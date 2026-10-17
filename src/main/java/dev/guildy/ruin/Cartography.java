package dev.guildy.ruin;

import com.mojang.datafixers.util.Pair;
import dev.guildy.Guildy;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import dev.guildy.ruin.GuildRuinData.Research;
import dev.guildy.structure.BuiltStructure;
import dev.guildy.structure.Orientation;
import dev.guildy.structure.StructureManager;
import dev.guildy.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cartographer research: started from the atelier menu (cost paid by the player), advanced by the guild's cartographer
 * villagers working at the atelier (one second of work per cartographer and per second, capped, paused at night), then
 * a locate of the nearest ruin not yet referenced by a map (like vanilla explorer maps); "Récupérer" hands out a map
 * (Overworld) or a lodestone compass (Nether). Nothing found: the cost is refunded.
 */
public final class Cartography {
	/** Structure id of the "Atelier du cartographe". */
	public static final String STRUCTURE_ID = "cartographer";
	private static final long WORKING_WINDOW_TICKS = 60;

	/** Finds a ruin for a research (replaceable for tests): the vanilla structure search by default. */
	@FunctionalInterface
	public interface Locator {
		@Nullable RuinRecord locate(ServerLevel level, BlockPos origin, boolean nether, Guild guild);
	}

	public static Locator locator = Cartography::vanillaLocate;

	/** Guild id → villager id → last tick it worked at the atelier. */
	private static final Map<UUID, Map<UUID, Long>> WORKERS = new HashMap<>();
	/** Guild id → {second, work seconds added during that second}. */
	private static final Map<UUID, long[]> BUDGET = new HashMap<>();

	private Cartography() {
	}

	static void clear() {
		WORKERS.clear();
		BUDGET.clear();
	}

	static RuinConfig.CartographySettings settings() {
		return RuinModule.config().cartography;
	}

	// ================================================================== atelier

	public static @Nullable BuiltStructure atelier(Guild guild) {
		return guild.structures.find(STRUCTURE_ID);
	}

	/** The cartography table of the atelier (else the middle of its bottom row). */
	public static BlockPos table(ServerLevel level, BuiltStructure atelier) {
		for (BlockPos pos : atelier.blocks) {
			if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.CARTOGRAPHY_TABLE)) {
				return pos;
			}
		}
		int[] offset = Orientation.offset(atelier.width / 2, 0, atelier.facing.getStepX(), atelier.facing.getStepZ());
		return atelier.origin.offset(offset[0], offset[1], offset[2]);
	}

	/** Where a cartographer stands to work: in front of the table. */
	public static BlockPos workSpot(ServerLevel level, BuiltStructure atelier) {
		return table(level, atelier).relative(atelier.facing);
	}

	// ================================================================== research

	public static @Nullable Research research(Guild guild) {
		GuildRuinData data = RuinIndex.peekGuild(guild.id);
		return data == null ? null : data.research;
	}

	/** Starts a research (cost taken from the player and the treasury). @return the error, or null on success */
	public static @Nullable Component start(ServerPlayer player, Guild guild, boolean nether) {
		if (!guild.isMember(player.getUUID())) {
			return Lang.get("error.no_guild");
		}
		if (atelier(guild) == null) {
			return Lang.get("ruin.research.no_atelier");
		}
		GuildRuinData data = RuinIndex.guild(guild.id);
		if (data.research != null) {
			return Lang.get("ruin.research.busy");
		}
		RuinConfig.CartographySettings cfg = settings();
		Map<String, Integer> cost = nether ? cfg.netherCost : cfg.overworldCost;
		if (!cost.isEmpty() && player.isCreative()) {
			return Lang.get("ruin.research.creative");
		}
		if (cfg.goldCost > 0 && !guild.can(player.getUUID(), GuildPermission.TREASURY)) {
			return Lang.get("error.no_permission");
		}
		for (Map.Entry<String, Integer> entry : cost.entrySet()) {
			Item item = ItemUtil.item(entry.getKey());
			if (item != null && ItemUtil.count(player, item) < entry.getValue()) {
				return Lang.get("ruin.research.missing", entry.getValue(), ItemUtil.name(item).getString());
			}
		}
		if (cfg.goldCost > 0 && guild.treasury < cfg.goldCost) {
			return Lang.get("error.not_enough_treasury", cfg.goldCost);
		}
		// Pay: items first (all checked above), then the treasury (items given back if it fails).
		List<ItemStack> paid = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : cost.entrySet()) {
			Item item = ItemUtil.item(entry.getKey());
			if (item == null) {
				continue;
			}
			if (!ItemUtil.remove(player, item, entry.getValue())) {
				refund(player, paid);
				return Lang.get("ruin.research.missing", entry.getValue(), ItemUtil.name(item).getString());
			}
			paid.add(new ItemStack(item, entry.getValue()));
		}
		if (cfg.goldCost > 0 && !GuildManager.takeTreasury(guild, cfg.goldCost)) {
			refund(player, paid);
			return Lang.get("error.not_enough_treasury", cfg.goldCost);
		}
		Research research = new Research();
		research.nether = nether;
		research.required = Math.max(1, Upgrades.workSeconds(guild, (int) Math.round(cfg.researchMinutes * 60)));
		research.startedBy = player.getUUID();
		research.startedByName = player.getName().getString();
		research.startedAt = System.currentTimeMillis();
		research.paidItems = paid;
		research.paidGold = cfg.goldCost;
		data.research = research;
		RuinIndex.markDirty();
		StructureManager.requestSignRefresh(guild);
		Msg.guild(guild, nether ? "ruin.research.started.nether" : "ruin.research.started", player.getName().getString());
		if (cartographers(guild) == 0) {
			Msg.send(player, "ruin.research.no_cartographer");
		}
		return null;
	}

	/** Cancels a running research (cost refunded to the player who clicks). */
	public static @Nullable Component cancel(ServerPlayer player, Guild guild) {
		GuildRuinData data = RuinIndex.peekGuild(guild.id);
		Research research = data == null ? null : data.research;
		if (research == null || research.status != Research.Status.RUNNING) {
			return Lang.get("ruin.research.none");
		}
		data.research = null;
		refund(player, research.paidItems);
		if (research.paidGold > 0) {
			GuildManager.addTreasury(guild, research.paidGold);
		}
		RuinIndex.markDirty();
		StructureManager.requestSignRefresh(guild);
		Msg.guild(guild, "ruin.research.cancelled", player.getName().getString());
		return null;
	}

	/** "Récupérer": the map / compass (READY) or the refund (FAILED). @return the error, or null on success */
	public static @Nullable Component claim(ServerPlayer player, Guild guild) {
		GuildRuinData data = RuinIndex.peekGuild(guild.id);
		Research research = data == null ? null : data.research;
		if (research == null || research.status == Research.Status.RUNNING) {
			return Lang.get("ruin.research.not_ready");
		}
		MinecraftServer server = player.level().getServer();
		if (research.status == Research.Status.FAILED) {
			data.research = null;
			refund(player, research.paidItems);
			if (research.paidGold > 0) {
				GuildManager.addTreasury(guild, research.paidGold);
			}
			RuinIndex.markDirty();
			StructureManager.requestSignRefresh(guild);
			Msg.send(player, "ruin.research.refunded");
			return null;
		}
		RuinRecord ruin = RuinIndex.get(research.ruinKey);
		if (ruin == null) {
			research.status = Research.Status.FAILED;
			RuinIndex.markDirty();
			return Lang.get("ruin.research.lost");
		}
		ItemStack item = RuinItems.forRuin(server, ruin);
		if (item.isEmpty()) {
			return Lang.get("ruin.research.lost");
		}
		data.research = null;
		data.addKnown(ruin.key);
		RuinIndex.markDirty();
		StructureManager.requestSignRefresh(guild);
		ItemUtil.giveOrDrop(player, item);
		Msg.sound(player, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.0F, 1.0F);
		BlockPos target = ruin.target();
		Msg.guild(guild, ruin.isNether() ? "ruin.research.claimed.nether" : "ruin.research.claimed", player.getName().getString(),
				Lang.colorize(Encounters.def(ruin).name), target.getX(), target.getZ());
		return null;
	}

	/**
	 * One second of work by a cartographer standing at the atelier (called by its task). Adds progress (capped by the
	 * number of cartographers that count at once) and completes the research when it is done.
	 *
	 * @return false when there is nothing to work on
	 */
	public static boolean work(ServerLevel level, Guild guild, UUID villager) {
		GuildRuinData data = RuinIndex.peekGuild(guild.id);
		Research research = data == null ? null : data.research;
		if (research == null || research.status != Research.Status.RUNNING) {
			return false;
		}
		long now = Scheduler.ticks();
		WORKERS.computeIfAbsent(guild.id, k -> new HashMap<>()).put(villager, now);
		long second = now / 20;
		long[] budget = BUDGET.computeIfAbsent(guild.id, k -> new long[] {-1, 0});
		if (budget[0] != second) {
			budget[0] = second;
			budget[1] = 0;
		}
		if (budget[1] >= RuinRules.researchWorkers(Integer.MAX_VALUE, settings().maxCartographers)) {
			return true;
		}
		budget[1]++;
		research.progress = Math.min(research.required, research.progress + 1.0);
		RuinIndex.markDirty();
		if (research.progress >= research.required) {
			complete(level.getServer(), guild, research);
		}
		return true;
	}

	/** Adds work seconds directly (admin command / tests). */
	public static void addProgress(MinecraftServer server, Guild guild, double seconds) {
		Research research = research(guild);
		if (research == null || research.status != Research.Status.RUNNING) {
			return;
		}
		research.progress = Math.min(research.required, research.progress + Math.max(0, seconds));
		RuinIndex.markDirty();
		if (research.progress >= research.required) {
			complete(server, guild, research);
		}
	}

	/** Cartographers that worked at the atelier during the last few seconds. */
	public static int working(Guild guild) {
		Map<UUID, Long> workers = WORKERS.get(guild.id);
		if (workers == null) {
			return 0;
		}
		long now = Scheduler.ticks();
		workers.values().removeIf(t -> now - t > WORKING_WINDOW_TICKS || t > now);
		return workers.size();
	}

	/** Villagers of the guild with the cartographer task. */
	public static int cartographers(Guild guild) {
		int count = 0;
		for (var record : guild.villagers.members.values()) {
			if (record.task == dev.guildy.villager.Task.CARTOGRAPHER && !record.isAway()) {
				count++;
			}
		}
		return count;
	}

	/** Research done: locate now (server thread), READY with the ruin or FAILED (refund at "Récupérer"). */
	static void complete(MinecraftServer server, Guild guild, Research research) {
		research.progress = research.required;
		RuinRecord ruin = null;
		ServerLevel claimLevel = GuildyServer.claimLevel();
		BuiltStructure atelier = atelier(guild);
		BlockPos origin = atelier != null ? atelier.origin : guild.heart != null ? guild.heart : BlockPos.ZERO;
		ServerLevel level = research.nether ? server.getLevel(Level.NETHER) : claimLevel != null ? claimLevel : server.overworld();
		if (level != null) {
			BlockPos from = research.nether ? new BlockPos(origin.getX() / 8, 64, origin.getZ() / 8) : origin;
			try {
				ruin = locator.locate(level, from, research.nether, guild);
			} catch (Exception e) {
				Guildy.LOGGER.error("[ruins] ruin search failed for guild {}", guild.name, e);
			}
		}
		if (ruin == null) {
			research.status = Research.Status.FAILED;
			Msg.guild(guild, "ruin.research.failed");
		} else {
			research.status = Research.Status.READY;
			research.ruinKey = ruin.key;
			Msg.guild(guild, research.nether ? "ruin.research.ready.nether" : "ruin.research.ready");
			Msg.guildSound(guild, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 1.0F, 1.0F);
		}
		RuinIndex.markDirty();
		StructureManager.requestSignRefresh(guild);
	}

	/** Nearest ruin of the dimension's tag not yet referenced by a map (vanilla explorer-map search). */
	static @Nullable RuinRecord vanillaLocate(ServerLevel level, BlockPos origin, boolean nether, Guild guild) {
		TagKey<Structure> tag = nether ? RuinIndex.TAG_NETHER : RuinIndex.TAG_OVERWORLD;
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Optional<HolderSet.Named<Structure>> set = registry.get(tag);
		if (set.isEmpty() || set.get().size() == 0) {
			return null;
		}
		return locate(level, set.get(), origin, settings().searchRadius, true);
	}

	/**
	 * Structure search + registration of the found start in the ruin index.
	 *
	 * @param claim add a reference to the start (the next search skips it, like vanilla explorer maps)
	 */
	static @Nullable RuinRecord locate(ServerLevel level, HolderSet<Structure> structures, BlockPos origin, int radius, boolean claim) {
		Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(level, structures, origin, radius, claim);
		if (found == null) {
			return null;
		}
		ChunkPos chunk = ChunkPos.containing(found.getFirst());
		ChunkAccess access = level.getChunk(chunk.x(), chunk.z(), ChunkStatus.STRUCTURE_STARTS);
		StructureStart start = access.getStartForStructure(found.getSecond().value());
		if (start == null || !start.isValid()) {
			return null;
		}
		return RuinIndex.register(level, start);
	}

	private static void refund(ServerPlayer player, List<ItemStack> items) {
		for (ItemStack stack : items) {
			ItemUtil.giveOrDrop(player, stack.copy());
		}
	}

	static void onGuildRemoved(UUID guildId) {
		WORKERS.remove(guildId);
		BUDGET.remove(guildId);
		RuinIndex.removeGuild(guildId);
	}
}
