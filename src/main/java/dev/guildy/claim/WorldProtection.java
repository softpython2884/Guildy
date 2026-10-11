package dev.guildy.claim;

import dev.guildy.config.GuildyConfig;
import dev.guildy.core.GuildyServer;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildPermission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Decisions used by the world mixins (explosions, fire, pistons, fluids, projectiles). Every method is cheap and
 * returns the vanilla behaviour outside the claim dimension. Callers wrap them in try/catch.
 */
public final class WorldProtection {
	private WorldProtection() {
	}

	private static GuildyConfig.Protection config() {
		return GuildyServer.config().protection;
	}

	/** True when a heart banner stands at this position. */
	public static boolean isHeart(Level level, BlockPos pos) {
		return HeartIndex.heartAt(level, pos) != null;
	}

	/** Removes the positions an explosion must not destroy (claimed chunks, hearts and their supports). */
	public static void filterExplosion(Level level, List<BlockPos> positions) {
		if (positions.isEmpty() || !ClaimGuard.isClaimLevel(level)) {
			return;
		}
		boolean protectClaims = config().explosions;
		positions.removeIf(pos -> (protectClaims && ClaimGuard.ownerAt(level, pos) != null)
				|| HeartIndex.heartOrSupport(level, pos) != null);
	}

	/** True when fire must not burn / spread at this position. */
	public static boolean fireProtected(Level level, BlockPos pos) {
		if (!ClaimGuard.isClaimLevel(level)) {
			return false;
		}
		return (config().fire && ClaimGuard.ownerAt(level, pos) != null) || HeartIndex.heartOrSupport(level, pos) != null;
	}

	/**
	 * False when a piston move must fail: a pushed / destroyed block or its destination lies in a claim owned by
	 * another guild than the piston's chunk, or touches a heart.
	 *
	 * @param head position the extending piston head will occupy (null when retracting)
	 */
	public static boolean pistonAllowed(Level level, BlockPos pistonPos, Direction pushDirection, @Nullable BlockPos head,
			List<BlockPos> toPush, List<BlockPos> toDestroy) {
		if (!ClaimGuard.isClaimLevel(level)) {
			return true;
		}
		boolean checkClaims = config().pistons;
		Guild pistonOwner = ClaimGuard.ownerAt(level, pistonPos);
		if (head != null && blocked(level, head, pistonOwner, checkClaims)) {
			return false;
		}
		for (BlockPos pos : toPush) {
			if (blocked(level, pos, pistonOwner, checkClaims) || blocked(level, pos.relative(pushDirection), pistonOwner, checkClaims)) {
				return false;
			}
		}
		for (BlockPos pos : toDestroy) {
			if (blocked(level, pos, pistonOwner, checkClaims)) {
				return false;
			}
		}
		return true;
	}

	private static boolean blocked(Level level, BlockPos pos, @Nullable Guild pistonOwner, boolean checkClaims) {
		if (checkClaims) {
			Guild owner = ClaimGuard.ownerAt(level, pos);
			if (owner != null && owner != pistonOwner) {
				return true;
			}
		}
		return HeartIndex.heartOrSupport(level, pos) != null;
	}

	/** False when a fluid must not flow into {@code pos} (coming from {@code pos - direction}). */
	public static boolean fluidAllowed(Level level, BlockPos pos, Direction direction) {
		if (!config().fluids || !ClaimGuard.isClaimLevel(level)) {
			return true;
		}
		Guild target = ClaimGuard.ownerAt(level, pos);
		if (target == null) {
			return true;
		}
		return ClaimGuard.ownerAt(level, pos.relative(direction.getOpposite())) == target;
	}

	/** False when a projectile shot by a player must not affect the block (TNT, pots, dripstone, campfires...). */
	public static boolean projectileMayInteract(@Nullable Entity owner, ServerLevel level, BlockPos pos) {
		if (!(owner instanceof ServerPlayer player) || !ClaimGuard.isClaimLevel(level) || ClaimGuard.hasBypass(player)) {
			return true;
		}
		return ClaimGuard.allowedAt(player, level, pos, GuildPermission.BUILD) && HeartIndex.heartOrSupport(level, pos) == null;
	}
}
