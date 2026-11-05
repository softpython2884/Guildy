package dev.guildy.anchor;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Active anchors as seen by the mixins (server thread only), replaced as a whole by {@link AnchorTickets} at each
 * refresh. Points are the centres of the anchor banners ({x, y, z} triples), only for the anchors that spawn mobs
 * (anchors.mobSpawning); squares are their entity-ticking chunk squares ({chunkX, chunkZ, radius} triples).
 */
public final class AnchorPoints {
	private static Map<ServerLevel, double[]> spawnPoints = Map.of();
	private static Map<ServerLevel, int[]> spawnSquares = Map.of();
	private static boolean awake;

	private AnchorPoints() {
	}

	static void set(Map<ServerLevel, double[]> points, Map<ServerLevel, int[]> squares, boolean keepAwake) {
		spawnPoints = points.isEmpty() ? Map.of() : new IdentityHashMap<>(points);
		spawnSquares = squares.isEmpty() ? Map.of() : new IdentityHashMap<>(squares);
		awake = keepAwake;
	}

	static void clear() {
		set(Map.of(), Map.of(), false);
	}

	/** Points of the anchors of this level that count as a player for mob spawning, or null (most levels, most of the time). */
	public static double @Nullable [] spawnPoints(Level level) {
		Map<ServerLevel, double[]> map = spawnPoints;
		return map.isEmpty() ? null : map.get(level);
	}

	/** Entity-ticking chunk squares of these anchors ({chunkX, chunkZ, radius} triples), or null. */
	public static int @Nullable [] spawnSquares(Level level) {
		Map<ServerLevel, int[]> map = spawnSquares;
		return map.isEmpty() ? null : map.get(level);
	}

	/** Chunks these anchors add to the global mob caps of the level (a player's worth each). */
	public static int spawnChunkBonus(Level level) {
		double[] points = spawnPoints(level);
		return points == null ? 0 : points.length / 3 * AnchorRules.SPAWN_CHUNKS_PER_ANCHOR;
	}

	/** An anchor is active and anchors.keepServerAwake: the empty server must not pause. */
	public static boolean keepAwake() {
		return awake;
	}

	/**
	 * Stand-in returned to vanilla code that needs "the nearest player" when only an anchor is near (its distance is
	 * replaced by the anchor's): Fabric's shared fake player of the level, never added to the world.
	 */
	public static @Nullable Player standIn(Level level) {
		return level instanceof ServerLevel serverLevel ? FakePlayer.get(serverLevel) : null;
	}
}
