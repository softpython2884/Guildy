package dev.guildy.ruin;

import dev.guildy.claim.ClaimGuard;
import dev.guildy.command.GuildCommand;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Beacon sanctuaries of the ruins: a beacon inside a known ruin and the blocks of its pyramid (the complete levels under
 * it, counted like vanilla, see {@link BeaconRules}) cannot be broken by players (the admin bypass and creative players
 * with the admin permission excepted), destroyed by explosions nor moved by pistons (ruin mixins). Every other block of the
 * ruins stays breakable. The beacons of a ruin are found among the block entities of its loaded chunks, cached two seconds.
 */
public final class Sanctuaries {
	private static final long CACHE_TICKS = 40;
	private static final int CACHE_MAX = 512;
	private static final int MESSAGE_TICKS = 20;
	private static final Map<String, Cached> CACHE = new HashMap<>();
	private static final Map<UUID, Long> WARNED = new HashMap<>();

	/** A beacon of a ruin and the complete levels of its pyramid. */
	record Beacon(BlockPos pos, int levels) {
	}

	private record Cached(long tick, List<Beacon> beacons) {
	}

	private Sanctuaries() {
	}

	static void init() {
		AttackBlockCallback.EVENT.register(Sanctuaries::onAttack);
		PlayerBlockBreakEvents.BEFORE.register(Sanctuaries::beforeBreak);
		Lifecycle.STOPPING.add(server -> {
			CACHE.clear();
			WARNED.clear();
		});
	}

	// ================================================================== queries

	/** A beacon of a known ruin, or a block of its pyramid, or a block of a jaguar statue of the trails ({@link Statues}). */
	public static boolean isProtected(Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel server)) {
			return false;
		}
		if (Statues.protects(level, pos)) {
			return true;
		}
		RuinRecord ruin = RuinIndex.containing(level.dimension(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0);
		return ruin != null && protects(server, ruin, pos);
	}

	private static boolean protects(ServerLevel level, RuinRecord ruin, BlockPos pos) {
		for (Beacon beacon : beacons(level, ruin)) {
			BlockPos b = beacon.pos();
			if (BeaconRules.protects(b.getX(), b.getY(), b.getZ(), beacon.levels(), pos.getX(), pos.getY(), pos.getZ())) {
				return true;
			}
		}
		return false;
	}

	/** Beacons of a ruin (loaded chunks only) with their pyramid levels. */
	static List<Beacon> beacons(ServerLevel level, RuinRecord ruin) {
		long now = level.getGameTime();
		Cached cached = CACHE.get(ruin.key);
		if (cached != null && now >= cached.tick() && now - cached.tick() < CACHE_TICKS) {
			return cached.beacons();
		}
		List<Beacon> found = scan(level, ruin);
		if (CACHE.size() >= CACHE_MAX) {
			CACHE.clear();
		}
		CACHE.put(ruin.key, new Cached(now, found));
		return found;
	}

	private static List<Beacon> scan(ServerLevel level, RuinRecord ruin) {
		List<Beacon> found = new ArrayList<>();
		for (int cx = ruin.minX >> 4; cx <= ruin.maxX >> 4; cx++) {
			for (int cz = ruin.minZ >> 4; cz <= ruin.maxZ >> 4; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity entity : chunk.getBlockEntities().values()) {
					BlockPos pos = entity.getBlockPos();
					if (entity instanceof BeaconBlockEntity && ruin.contains(pos, 0)) {
						int levels = BeaconRules.levels(pos.getX(), pos.getY(), pos.getZ(), level.getMinY(), (x, y, z) -> {
							BlockPos base = new BlockPos(x, y, z);
							return level.isLoaded(base) && level.getBlockState(base).is(BlockTags.BEACON_BASE_BLOCKS);
						});
						found.add(new Beacon(pos.immutable(), levels));
					}
				}
			}
		}
		return found;
	}

	/** Forgets the cached beacons (a beacon placed or removed by a command, GameTests). */
	public static void invalidate() {
		CACHE.clear();
	}

	// ================================================================== explosions and pistons (ruin mixins)

	/** Removes the protected positions an explosion would destroy. */
	public static void filterExplosion(Level level, List<BlockPos> positions) {
		if (positions.isEmpty() || !(level instanceof ServerLevel server)) {
			return;
		}
		Statues.filterExplosion(level, positions);
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (BlockPos pos : positions) {
			minX = Math.min(minX, pos.getX());
			minY = Math.min(minY, pos.getY());
			minZ = Math.min(minZ, pos.getZ());
			maxX = Math.max(maxX, pos.getX());
			maxY = Math.max(maxY, pos.getY());
			maxZ = Math.max(maxZ, pos.getZ());
		}
		String dimension = level.dimension().identifier().toString();
		List<RuinRecord> ruins = new ArrayList<>();
		for (RuinRecord ruin : RuinIndex.all()) {
			if (dimension.equals(ruin.dimension) && ruin.minX <= maxX && ruin.maxX >= minX && ruin.minY <= maxY && ruin.maxY >= minY
					&& ruin.minZ <= maxZ && ruin.maxZ >= minZ && !beacons(server, ruin).isEmpty()) {
				ruins.add(ruin);
			}
		}
		if (ruins.isEmpty()) {
			return;
		}
		positions.removeIf(pos -> {
			for (RuinRecord ruin : ruins) {
				if (ruin.contains(pos, 0) && protects(server, ruin, pos)) {
					return true;
				}
			}
			return false;
		});
	}

	/** False when a piston would push, pull or break a protected block. */
	public static boolean pistonAllowed(Level level, List<BlockPos> toPush, List<BlockPos> toDestroy) {
		if (!(level instanceof ServerLevel)) {
			return true;
		}
		for (BlockPos pos : toPush) {
			if (isProtected(level, pos)) {
				return false;
			}
		}
		for (BlockPos pos : toDestroy) {
			if (isProtected(level, pos)) {
				return false;
			}
		}
		return true;
	}

	// ================================================================== players

	private static InteractionResult onAttack(Player player, Level level, InteractionHand hand, BlockPos pos, Direction direction) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		return Safe.get("ruin.sanctuary.attack", () -> mayBreak(serverPlayer, level, pos) ? InteractionResult.PASS : InteractionResult.FAIL,
				InteractionResult.PASS);
	}

	private static boolean beforeBreak(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return true;
		}
		return Safe.get("ruin.sanctuary.break", () -> mayBreak(serverPlayer, level, pos), true);
	}

	/** Players may break anything but a sanctuary (admins excepted). Sends the message when refused. */
	static boolean mayBreak(ServerPlayer player, Level level, BlockPos pos) {
		if (player.isSpectator() || !isProtected(level, pos) || exempt(player)) {
			return true;
		}
		long now = Scheduler.ticks();
		Long last = WARNED.get(player.getUUID());
		if (last == null || now < last || now - last >= MESSAGE_TICKS) {
			WARNED.put(player.getUUID(), now);
			Msg.actionBar(player, "ruin.sanctuary.protected");
			Msg.sound(player, SoundEvents.BEACON_POWER_SELECT, 0.6F, 0.6F);
		}
		if (WARNED.size() > 256) {
			WARNED.clear();
		}
		return false;
	}

	/** The admin bypass of the territories, or a creative player with the admin permission. */
	private static boolean exempt(ServerPlayer player) {
		return ClaimGuard.hasBypass(player) || player.isCreative()
				&& Permissions.check(player, GuildCommand.ADMIN_PERMISSION, PermissionLevel.byId(GuildyServer.config().general.adminOpLevel));
	}
}
