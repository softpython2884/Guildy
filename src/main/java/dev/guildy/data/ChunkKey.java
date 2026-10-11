package dev.guildy.data;

/**
 * Packs chunk coordinates into a long (same layout as vanilla: x in the low 32 bits, z in the high 32 bits).
 */
public final class ChunkKey {
	private ChunkKey() {
	}

	public static long of(int chunkX, int chunkZ) {
		return (chunkX & 0xFFFFFFFFL) | ((chunkZ & 0xFFFFFFFFL) << 32);
	}

	public static long ofBlock(int blockX, int blockZ) {
		return of(blockX >> 4, blockZ >> 4);
	}

	public static int x(long key) {
		return (int) key;
	}

	public static int z(long key) {
		return (int) (key >>> 32);
	}

	public static long offset(long key, int dx, int dz) {
		return of(x(key) + dx, z(key) + dz);
	}

	/** Chebyshev distance in chunks. */
	public static int distance(long a, long b) {
		return Math.max(Math.abs(x(a) - x(b)), Math.abs(z(a) - z(b)));
	}

	public static String toString(long key) {
		return "[" + x(key) + ", " + z(key) + "]";
	}
}
