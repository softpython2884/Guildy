package dev.guildy.villager;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import dev.guildy.gui.GuiUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Appeler ici": brings a guild villager next to the player, wherever it is. A villager whose chunk is not loaded is
 * looked for at its last known position: the chunks around it are loaded for a moment (a vanilla ticket that is not
 * saved and expires by itself two seconds after its last refresh), the villager is teleported as soon as its entity is
 * loaded, then the ticket is removed. Not there: it may have walked through a Nether portal (before portals were closed
 * to guild villagers), so the Nether around the matching coordinates (÷ 8) is loaded too; once loaded there it is
 * brought back by {@link Repatriation}. Rules: the player stands in the territory or in one of the guild's zones; the
 * villager is not trading nor away on an expedition.
 */
final class VillagerCalls {
	enum Result {
		/** The villager is next to the player. */
		DONE,
		/** Its chunk is loading: it comes in a moment (messages follow). */
		PENDING,
		FAILED
	}

	/** Ticket radius: the chunk of the last position and its 8 neighbours are loaded (entities included). */
	private static final int RADIUS = 1;
	/** Not loaded / not found after this delay: the call fails. */
	private static final long MAX_WAIT_TICKS = 10 * 20L;
	private static final int MAX_CALLS = 16;
	/** Ticket radius of the Nether search (portal exits land near the overworld coordinates divided by 8). */
	private static final int NETHER_RADIUS = 2;

	/** {@code nether}: second phase, {@code chunk} is a Nether chunk. */
	private record Call(UUID player, UUID guild, UUID villager, ChunkPos chunk, BlockPos lastPos, long startedAt, boolean nether) {
	}

	/** Villager UUID → call in progress. */
	private static final Map<UUID, Call> CALLS = new LinkedHashMap<>();

	private VillagerCalls() {
	}

	static void init() {
		Scheduler.every("villager.calls", 5, 3, VillagerCalls::tick);
		Lifecycle.STARTED.add(server -> CALLS.clear());
		Lifecycle.STOPPING.add(server -> releaseAll());
	}

	/** Calls in progress (tests). */
	static int pending() {
		return CALLS.size();
	}

	static Result call(ServerPlayer player, Guild guild, VillagerRecord record) {
		if (record.isAway()) {
			Msg.send(player, "villager.call.away", record.name);
			return Result.FAILED;
		}
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null || player.level() != level || !Zones.inGuildArea(level, guild, player.blockPosition())) {
			Msg.send(player, "villager.call.outside");
			return Result.FAILED;
		}
		Villager villager = VillagerManager.entity(level, record.uuid);
		if (villager == null) {
			villager = fromOtherDimension(level, record);
		}
		if (villager != null) {
			return bring(level, player, record, villager) ? Result.DONE : Result.FAILED;
		}
		if (CALLS.containsKey(record.uuid)) {
			Msg.send(player, "villager.call.already", record.name);
			return Result.PENDING;
		}
		BlockPos last = record.lastPos;
		if (last == null) {
			Msg.send(player, "villager.call.no_position", record.name);
			return Result.FAILED;
		}
		if (CALLS.size() >= MAX_CALLS) {
			Msg.send(player, "villager.call.too_many");
			return Result.FAILED;
		}
		ChunkPos chunk = ChunkPos.containing(last);
		level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, chunk, RADIUS);
		CALLS.put(record.uuid, new Call(player.getUUID(), guild.id, record.uuid, chunk, last, Scheduler.ticks(), false));
		Msg.send(player, "villager.call.loading", record.name, last.getX(), last.getY(), last.getZ());
		return Result.PENDING;
	}

	/** Every 5 ticks: refreshes the tickets, brings the villagers whose entity is loaded, gives up on the others. */
	private static void tick() {
		if (CALLS.isEmpty()) {
			return;
		}
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			CALLS.clear();
			return;
		}
		for (Call call : new ArrayList<>(CALLS.values())) {
			boolean over = Safe.get("villager.call", () -> step(level, call), true);
			if (over) {
				release(level, call);
			}
		}
	}

	/** @return true when the call is over (success or failure) */
	private static boolean step(ServerLevel level, Call call) {
		Guild guild = GuildManager.get(call.guild());
		VillagerRecord record = guild == null ? null : guild.villagers.members.get(call.villager());
		ServerPlayer player = GuildyServer.player(call.player());
		if (guild == null || record == null || record.isAway()) {
			return true;
		}
		Villager villager = VillagerManager.entity(level, call.villager());
		if (villager == null) {
			villager = fromOtherDimension(level, record);
		}
		if (villager != null) {
			// Released before the teleport: its old chunk may unload as soon as it is gone.
			release(level, call);
			if (player == null) {
				return true;
			}
			if (GuildManager.of(player) != guild || !guild.can(player.getUUID(), GuildPermission.VILLAGERS)) {
				Msg.send(player, "error.no_permission");
				return true;
			}
			if (player.level() != level || !Zones.inGuildArea(level, guild, player.blockPosition())) {
				Msg.send(player, "villager.call.outside");
				GuiUtil.errorSound(player);
				return true;
			}
			if (bring(level, player, record, villager)) {
				GuiUtil.successSound(player);
			} else {
				GuiUtil.errorSound(player);
			}
			return true;
		}
		ServerLevel searched = call.nether() ? level.getServer().getLevel(Level.NETHER) : level;
		int radius = call.nether() ? NETHER_RADIUS : RADIUS;
		if (searched == null || entitiesLoaded(searched, call.chunk(), radius) || Scheduler.ticks() - call.startedAt() > MAX_WAIT_TICKS) {
			ServerLevel nether = level.getServer().getLevel(Level.NETHER);
			if (!call.nether() && nether != null) {
				// Not near its last position: maybe through a Nether portal. Look around the matching Nether coordinates.
				release(level, call);
				BlockPos last = call.lastPos();
				ChunkPos netherChunk = new ChunkPos((last.getX() >> 3) >> 4, (last.getZ() >> 3) >> 4);
				nether.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, netherChunk, NETHER_RADIUS);
				CALLS.put(call.villager(), new Call(call.player(), call.guild(), call.villager(), netherChunk, last, Scheduler.ticks(), true));
				if (player != null) {
					Msg.send(player, "villager.call.nether", record.name);
				}
				return false;
			}
			if (player != null) {
				BlockPos last = call.lastPos();
				Msg.send(player, "villager.call.not_found", record.name, last.getX(), last.getY(), last.getZ());
				GuiUtil.errorSound(player);
			}
			return true;
		}
		// Still loading: keep the chunks loaded (the ticket expires 2 s after its last refresh).
		searched.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, call.chunk(), radius);
		return false;
	}

	/** The villager loaded in another dimension (Nether...): sent back home first. */
	private static @org.jspecify.annotations.Nullable Villager fromOtherDimension(ServerLevel home, VillagerRecord record) {
		for (ServerLevel other : home.getServer().getAllLevels()) {
			if (other != home && other.getEntity(record.uuid) instanceof Villager stray && !stray.isRemoved()) {
				return Repatriation.repatriate(stray);
			}
		}
		return null;
	}

	/** Every chunk of the loaded square has its entities: the villager is not there. */
	private static boolean entitiesLoaded(ServerLevel level, ChunkPos center, int radius) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (!level.areEntitiesLoaded(ChunkPos.pack(center.x() + dx, center.z() + dz))) {
					return false;
				}
			}
		}
		return true;
	}

	private static void release(ServerLevel level, Call call) {
		if (CALLS.remove(call.villager()) != null) {
			ServerLevel searched = call.nether() ? level.getServer().getLevel(Level.NETHER) : level;
			if (searched != null) {
				searched.getChunkSource().removeTicketWithRadius(TicketType.ENDER_PEARL, call.chunk(), call.nether() ? NETHER_RADIUS : RADIUS);
			}
		}
	}

	private static void releaseAll() {
		ServerLevel level = GuildyServer.claimLevel();
		if (level != null) {
			for (Call call : new ArrayList<>(CALLS.values())) {
				release(level, call);
			}
		}
		CALLS.clear();
	}

	/**
	 * Teleports the (loaded) villager next to the player; its task starts over from there (no walk back to an old
	 * target). @return true on success (messages sent either way)
	 */
	private static boolean bring(ServerLevel level, ServerPlayer player, VillagerRecord record, Villager villager) {
		if (villager.isTrading()) {
			Msg.send(player, "villager.call.busy", record.name);
			return false;
		}
		BlockPos spot = WorldUtil.findSafeSpot(level, player.blockPosition(), 2, true, pos -> true);
		if (spot == null) {
			Msg.send(player, "villager.call.no_spot");
			return false;
		}
		if (villager.isPassenger()) {
			villager.stopRiding();
		}
		if (villager.isSleeping()) {
			villager.stopSleeping();
		}
		WorldUtil.portal(level, villager.getX(), villager.getY(), villager.getZ());
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		villager.getBrain().eraseMemory(MemoryModuleType.PATH);
		villager.getNavigation().stop();
		villager.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		WorldUtil.portal(level, villager.getX(), villager.getY(), villager.getZ());
		WorldUtil.playSound(level, villager, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.6f, 1.2f);
		record.lastPos = villager.blockPosition();
		VillagerRuntime rt = TaskEngine.peek(record.uuid);
		if (rt != null) {
			rt.resetAllMovement();
		}
		TaskEngine.resetTask(record.uuid);
		GuildManager.markDirty();
		Msg.send(player, "villager.call.done", record.name);
		return true;
	}
}
