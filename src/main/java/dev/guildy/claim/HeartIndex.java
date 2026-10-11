package dev.guildy.claim;

import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Index of heart positions (claim dimension) so that the heart banner and the block holding it can be protected
 * cheaply from players, explosions, fire and pistons. Rebuilt lazily when territories change (and every 10 s as a
 * safety net, in case another module edits a heart directly).
 */
public final class HeartIndex {
	private static final int REFRESH_TICKS = 200;
	private static final Map<BlockPos, Guild> HEARTS = new HashMap<>();
	private static boolean dirty = true;
	private static long builtAt = Long.MIN_VALUE;

	private HeartIndex() {
	}

	public static void markDirty() {
		dirty = true;
	}

	private static void ensure() {
		long now = Scheduler.ticks();
		if (!dirty && now >= builtAt && now - builtAt < REFRESH_TICKS) {
			return;
		}
		HEARTS.clear();
		for (Guild guild : GuildManager.all()) {
			if (guild.heart != null) {
				HEARTS.put(guild.heart.immutable(), guild);
			}
		}
		dirty = false;
		builtAt = now;
	}

	/** Guild whose heart banner is at {@code pos}, or null. */
	public static @Nullable Guild heartAt(Level level, BlockPos pos) {
		if (!ClaimGuard.isClaimLevel(level)) {
			return null;
		}
		ensure();
		return HEARTS.isEmpty() ? null : HEARTS.get(pos);
	}

	/**
	 * Guild whose heart is at {@code pos} or is attached to the block at {@code pos} (block below a standing banner,
	 * wall behind a wall banner). Breaking / moving that block would pop the heart off.
	 */
	public static @Nullable Guild heartOrSupport(Level level, BlockPos pos) {
		if (!ClaimGuard.isClaimLevel(level)) {
			return null;
		}
		ensure();
		if (HEARTS.isEmpty()) {
			return null;
		}
		Guild guild = HEARTS.get(pos);
		if (guild != null) {
			return guild;
		}
		BlockPos above = pos.above();
		guild = HEARTS.get(above);
		if (guild != null && level.getBlockState(above).getBlock() instanceof BannerBlock) {
			return guild;
		}
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos side = pos.relative(direction);
			guild = HEARTS.get(side);
			if (guild != null) {
				BlockState state = level.getBlockState(side);
				if (state.getBlock() instanceof WallBannerBlock && state.getValue(WallBannerBlock.FACING) == direction) {
					return guild;
				}
			}
		}
		return null;
	}

	public static void clear() {
		HEARTS.clear();
		dirty = true;
	}
}
