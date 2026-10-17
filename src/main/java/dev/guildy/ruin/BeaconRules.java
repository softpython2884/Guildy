package dev.guildy.ruin;

/**
 * Pure rules (no Minecraft classes, unit-tested) of the beacon sanctuaries of the ruins: the levels of a beacon pyramid
 * (same scan as vanilla {@code BeaconBlockEntity.updateBase}: level k is the square of side 2k + 1 centred k blocks under
 * the beacon, a level counts only when it is complete and every level above it too) and the blocks they protect.
 */
public final class BeaconRules {
	/** Vanilla beacons count at most four levels. */
	public static final int MAX_LEVELS = 4;

	/** Whether the block at a position counts as beacon base (tag {@code minecraft:beacon_base_blocks}). */
	@FunctionalInterface
	public interface Base {
		boolean test(int x, int y, int z);
	}

	private BeaconRules() {
	}

	/** Complete levels (0 to 4) of the pyramid under a beacon at (x, y, z); nothing below {@code minY} counts. */
	public static int levels(int x, int y, int z, int minY, Base base) {
		int levels = 0;
		for (int step = 1; step <= MAX_LEVELS; step++) {
			int ly = y - step;
			if (ly < minY) {
				break;
			}
			for (int lx = x - step; lx <= x + step; lx++) {
				for (int lz = z - step; lz <= z + step; lz++) {
					if (!base.test(lx, ly, lz)) {
						return levels;
					}
				}
			}
			levels = step;
		}
		return levels;
	}

	/** The beacon at (bx, by, bz) with {@code levels} complete levels protects (x, y, z): itself or a block of a level. */
	public static boolean protects(int bx, int by, int bz, int levels, int x, int y, int z) {
		if (x == bx && y == by && z == bz) {
			return true;
		}
		int step = by - y;
		return step >= 1 && step <= Math.min(MAX_LEVELS, levels) && Math.abs(x - bx) <= step && Math.abs(z - bz) <= step;
	}

	/** Blocks of a pyramid of {@code levels} complete levels (9, 34, 83, 164). */
	public static int baseBlocks(int levels) {
		int total = 0;
		for (int k = 1; k <= Math.min(MAX_LEVELS, levels); k++) {
			total += (2 * k + 1) * (2 * k + 1);
		}
		return total;
	}
}
