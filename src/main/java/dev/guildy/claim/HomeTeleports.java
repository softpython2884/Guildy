package dev.guildy.claim;

import dev.guildy.config.GuildyConfig;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * /guild home: per-player cooldown, warm-up with an action bar countdown, cancelled when the player moves more than
 * one block. Destination: the guild home, or a free spot at the heart.
 */
final class HomeTeleports {
	private static final int CHECK_INTERVAL = 5;
	private static final Map<UUID, Long> LAST_TELEPORT = new HashMap<>();
	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	private static final class Pending {
		final UUID guildId;
		final Vec3 start;
		final long startTick;
		final int warmupTicks;
		int shownSeconds = -1;

		Pending(UUID guildId, Vec3 start, long startTick, int warmupTicks) {
			this.guildId = guildId;
			this.start = start;
			this.startTick = startTick;
			this.warmupTicks = warmupTicks;
		}
	}

	private HomeTeleports() {
	}

	static void register() {
		Scheduler.every("home.warmup", CHECK_INTERVAL, 1, HomeTeleports::tick);
	}

	static void request(ServerPlayer player, Guild guild) {
		GuildyConfig.Guilds cfg = GuildyServer.config().guilds;
		if (!cfg.homeEnabled) {
			Msg.send(player, "home.disabled");
			return;
		}
		if (!guild.isMember(player.getUUID())) {
			Msg.send(player, "error.no_guild");
			return;
		}
		if (guild.home == null && guild.heart == null) {
			Msg.send(player, "home.not_set");
			return;
		}
		if (PENDING.containsKey(player.getUUID())) {
			Msg.send(player, "home.pending");
			return;
		}
		long now = Scheduler.ticks();
		Long last = LAST_TELEPORT.get(player.getUUID());
		long cooldownTicks = cfg.homeCooldownSeconds * 20L;
		if (last != null && now >= last && now - last < cooldownTicks) {
			long remaining = (cooldownTicks - (now - last) + 19) / 20;
			Msg.send(player, "home.cooldown", remaining);
			return;
		}
		int warmupTicks = cfg.homeWarmupSeconds * 20;
		if (warmupTicks <= 0) {
			teleport(player, guild);
			return;
		}
		Pending pending = new Pending(guild.id, player.position(), now, warmupTicks);
		PENDING.put(player.getUUID(), pending);
		showCountdown(player, pending, now);
	}

	private static void tick() {
		if (PENDING.isEmpty()) {
			return;
		}
		long now = Scheduler.ticks();
		Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Pending> entry = it.next();
			Pending pending = entry.getValue();
			ServerPlayer player = GuildyServer.player(entry.getKey());
			if (player == null || !player.isAlive()) {
				it.remove();
				continue;
			}
			if (player.position().distanceToSqr(pending.start) > 1.0) {
				it.remove();
				Msg.actionBar(player, "home.cancelled");
				Msg.sound(player, SoundEvents.VILLAGER_NO, 0.6f, 1.0f);
				continue;
			}
			if (now - pending.startTick >= pending.warmupTicks) {
				it.remove();
				Guild guild = GuildManager.get(pending.guildId);
				if (guild != null && guild.isMember(player.getUUID())) {
					Safe.run("home.teleport", () -> teleport(player, guild));
				}
				continue;
			}
			showCountdown(player, pending, now);
		}
	}

	private static void showCountdown(ServerPlayer player, Pending pending, long now) {
		int seconds = (int) Math.max(1, (pending.warmupTicks - (now - pending.startTick) + 19) / 20);
		if (seconds != pending.shownSeconds) {
			pending.shownSeconds = seconds;
			Msg.actionBar(player, "home.warmup", seconds);
		}
	}

	private static void teleport(ServerPlayer player, Guild guild) {
		ServerLevel level = GuildyServer.claimLevel();
		Vec3 target = level == null ? null : destination(level, guild);
		if (level == null || target == null) {
			Msg.send(player, "home.unavailable");
			return;
		}
		float yaw = guild.home != null ? guild.homeYaw : player.getYRot();
		moveTo(player, level, target, yaw);
		LAST_TELEPORT.put(player.getUUID(), Scheduler.ticks());
		Msg.actionBar(player, "home.teleported");
	}

	/** Teleport helper (also used by the admin "tp" command). */
	static void moveTo(ServerPlayer player, ServerLevel level, Vec3 target, float yaw) {
		if (player.isPassenger()) {
			player.stopRiding();
		}
		player.teleportTo(level, target.x, target.y, target.z, Set.of(), yaw, player.getXRot(), true);
		player.resetFallDistance();
		Msg.sound(player, SoundEvents.ENDERMAN_TELEPORT, 0.7f, 1.0f);
	}

	/** Home position (standing on top of a partial block if needed), else a free spot at the heart. */
	static @Nullable Vec3 destination(ServerLevel level, Guild guild) {
		if (guild.home != null) {
			BlockPos home = guild.home;
			return new Vec3(home.getX() + 0.5, home.getY() + top(level, home), home.getZ() + 0.5);
		}
		return guild.heart == null ? null : nearHeart(level, guild.heart);
	}

	/** First spot from the heart upwards with room for a player and something to stand on. */
	static Vec3 nearHeart(ServerLevel level, BlockPos heart) {
		for (int dy = 0; dy <= 8; dy++) {
			BlockPos feet = heart.above(dy);
			if (free(level, feet) && free(level, feet.above()) && !free(level, feet.below())) {
				return new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
			}
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, heart.getX(), heart.getZ());
		return new Vec3(heart.getX() + 0.5, y, heart.getZ() + 0.5);
	}

	private static boolean free(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	private static double top(ServerLevel level, BlockPos pos) {
		VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
		return shape.isEmpty() ? 0.0 : shape.max(Direction.Axis.Y);
	}

	static void forget(UUID player) {
		PENDING.remove(player);
	}

	static void clear() {
		PENDING.clear();
		LAST_TELEPORT.clear();
	}
}
