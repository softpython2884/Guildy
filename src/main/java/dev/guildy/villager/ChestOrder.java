package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure spatial ordering of storage containers, read like a wall by a person standing in front of it (no Minecraft
 * classes: unit tested).
 * <ul>
 * <li>A wall = the containers facing the same way in the same vertical plane (chest, barrel and shulker box FACING).
 * "Left to right" is seen from the front: for a chest facing north the viewer looks south, so left to right runs
 * towards the west.</li>
 * <li>{@link Mode#COLUMNS}: each column top to bottom, columns left to right; {@link Mode#ROWS}: each row left to right,
 * rows top to bottom. A double chest is one unit placed at its left half.</li>
 * <li>Containers without a horizontal facing (barrels facing up or down) join a wall whose plane they lie in, else they
 * are ordered after the walls by the fallback: along the main horizontal axis of their spread (the axis plays the role
 * of "left to right") and by height.</li>
 * <li>Several walls: ordered by facing (north, east, south, west) then by depth, so the order never depends on the
 * number of containers.</li>
 * </ul>
 * {@link #sections} cuts an ordered group into runs that one standing spot can reach.
 */
public final class ChestOrder {
	public enum Mode {
		COLUMNS,
		ROWS;

		/** Config / data id ("columns", "rows"). */
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** Case-insensitive, trimmed; null when unknown. */
		public static @Nullable Mode parse(@Nullable String value) {
			if (value == null) {
				return null;
			}
			String id = value.trim().toUpperCase(Locale.ROOT);
			for (Mode mode : values()) {
				if (mode.name().equals(id)) {
					return mode;
				}
			}
			return null;
		}
	}

	/** Horizontal direction the front of a container faces. */
	public enum Facing {
		NORTH(0, -1),
		EAST(1, 0),
		SOUTH(0, 1),
		WEST(-1, 0);

		final int dx;
		final int dz;

		Facing(int dx, int dz) {
			this.dx = dx;
			this.dz = dz;
		}

		/** Facing named like the Minecraft direction ("north"...), null for "up" / "down" / unknown. */
		public static @Nullable Facing of(@Nullable String name) {
			if (name == null) {
				return null;
			}
			for (Facing facing : values()) {
				if (facing.name().equalsIgnoreCase(name.trim())) {
					return facing;
				}
			}
			return null;
		}
	}

	/**
	 * One container: block position, facing (null = none), and the other half of a double chest when {@code pair}.
	 */
	public record Unit(int id, int x, int y, int z, @Nullable Facing facing, boolean pair, int pairX, int pairZ) {
		public static Unit single(int id, int x, int y, int z, @Nullable Facing facing) {
			return new Unit(id, x, y, z, facing, false, x, z);
		}

		public static Unit pair(int id, int x, int y, int z, int pairX, int pairZ, @Nullable Facing facing) {
			return new Unit(id, x, y, z, facing, true, pairX, pairZ);
		}

		/** Centre of the unit (both halves of a double chest). */
		double centerX() {
			return (pair ? (x + pairX) / 2.0 : x) + 0.5;
		}

		double centerZ() {
			return (pair ? (z + pairZ) / 2.0 : z) + 0.5;
		}
	}

	/**
	 * Ids in reading order, and the lines: the columns ({@link Mode#COLUMNS}) or rows ({@link Mode#ROWS}) in reading
	 * order, each listing its ids in reading order.
	 */
	public record Result(List<Integer> order, List<List<Integer>> lines) {
	}

	private record WallKey(Facing facing, int depth) {
	}

	private record Placed(Unit unit, int u, int secondary) {
	}

	private ChestOrder() {
	}

	public static Result order(Collection<Unit> input, Mode mode) {
		List<Unit> units = new ArrayList<>(input);
		units.sort(Comparator.comparingInt(Unit::id));
		Map<WallKey, List<Unit>> walls = new TreeMap<>(Comparator.comparingInt((WallKey key) -> key.facing().ordinal())
				.thenComparingInt(WallKey::depth));
		List<Unit> loose = new ArrayList<>();
		for (Unit unit : units) {
			if (unit.facing() == null) {
				loose.add(unit);
			} else {
				walls.computeIfAbsent(new WallKey(unit.facing(), depth(unit, unit.facing())), key -> new ArrayList<>()).add(unit);
			}
		}
		// Units without facing lying in the plane of a wall belong to it (the biggest one).
		Iterator<Unit> it = loose.iterator();
		while (it.hasNext()) {
			Unit unit = it.next();
			List<Unit> best = null;
			for (Map.Entry<WallKey, List<Unit>> wall : walls.entrySet()) {
				if (depth(unit, wall.getKey().facing()) == wall.getKey().depth() && (best == null || wall.getValue().size() > best.size())) {
					best = wall.getValue();
				}
			}
			if (best != null) {
				best.add(unit);
				it.remove();
			}
		}
		List<Integer> order = new ArrayList<>();
		List<List<Integer>> lines = new ArrayList<>();
		for (Map.Entry<WallKey, List<Unit>> wall : walls.entrySet()) {
			Facing facing = wall.getKey().facing();
			// Right hand of a viewer looking at the front (viewer faces -facing): (facing.dz, -facing.dx).
			List<Placed> placed = new ArrayList<>();
			for (Unit unit : wall.getValue()) {
				int u = unit.x() * facing.dz - unit.z() * facing.dx;
				if (unit.pair()) {
					u = Math.min(u, unit.pairX() * facing.dz - unit.pairZ() * facing.dx);
				}
				placed.add(new Placed(unit, u, 0));
			}
			append(placed, mode, order, lines);
		}
		if (!loose.isEmpty()) {
			int minX = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int minZ = Integer.MAX_VALUE;
			int maxZ = Integer.MIN_VALUE;
			for (Unit unit : loose) {
				minX = Math.min(minX, Math.min(unit.x(), unit.pairX()));
				maxX = Math.max(maxX, Math.max(unit.x(), unit.pairX()));
				minZ = Math.min(minZ, Math.min(unit.z(), unit.pairZ()));
				maxZ = Math.max(maxZ, Math.max(unit.z(), unit.pairZ()));
			}
			boolean alongX = maxX - minX >= maxZ - minZ;
			List<Placed> placed = new ArrayList<>();
			for (Unit unit : loose) {
				int u = alongX ? Math.min(unit.x(), unit.pairX()) : Math.min(unit.z(), unit.pairZ());
				placed.add(new Placed(unit, u, alongX ? unit.z() : unit.x()));
			}
			append(placed, mode, order, lines);
		}
		return new Result(List.copyOf(order), List.copyOf(lines));
	}

	/** Sorts one wall (or the loose units) and appends its ids and lines. */
	private static void append(List<Placed> placed, Mode mode, List<Integer> order, List<List<Integer>> lines) {
		Comparator<Placed> byId = Comparator.comparingInt(p -> p.unit().id());
		Comparator<Placed> comparator = mode == Mode.COLUMNS
				? Comparator.comparingInt(Placed::u).thenComparingInt(p -> -p.unit().y()).thenComparingInt(Placed::secondary).thenComparing(byId)
				: Comparator.comparingInt((Placed p) -> -p.unit().y()).thenComparingInt(Placed::u).thenComparingInt(Placed::secondary).thenComparing(byId);
		placed.sort(comparator);
		List<Integer> line = null;
		int lineKey = 0;
		for (Placed p : placed) {
			int key = mode == Mode.COLUMNS ? p.u() : p.unit().y();
			if (line == null || key != lineKey) {
				line = new ArrayList<>();
				lines.add(line);
				lineKey = key;
			}
			line.add(p.unit().id());
			order.add(p.unit().id());
		}
	}

	private static int depth(Unit unit, Facing facing) {
		return unit.x() * facing.dx + unit.z() * facing.dz;
	}

	// ------------------------------------------------------------------ sections

	/**
	 * Cuts units in reading order into runs of consecutive units that one standing spot can reach: every unit of a run
	 * is within {@code reach} blocks of the centre of the run's bounding box, and a run holds at most {@code maxUnits}.
	 * Units flagged in {@code skip} (busy, unloaded) belong to no run and end the current one.
	 *
	 * @return runs as lists of indices into {@code ordered}
	 */
	public static List<List<Integer>> sections(List<Unit> ordered, double reach, int maxUnits, boolean @Nullable [] skip) {
		List<List<Integer>> sections = new ArrayList<>();
		List<Integer> run = new ArrayList<>();
		for (int i = 0; i < ordered.size(); i++) {
			if (skip != null && skip[i]) {
				if (!run.isEmpty()) {
					sections.add(run);
					run = new ArrayList<>();
				}
				continue;
			}
			run.add(i);
			if (run.size() > Math.max(1, maxUnits) || !reachable(ordered, run, reach)) {
				run.removeLast();
				sections.add(run);
				run = new ArrayList<>();
				run.add(i);
			}
		}
		if (!run.isEmpty()) {
			sections.add(run);
		}
		return sections;
	}

	/** Centre of the bounding box of the units (x, y, z), for the standing spot of a section. */
	public static double[] center(List<Unit> units) {
		double minX = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		double minZ = Double.MAX_VALUE;
		double maxZ = -Double.MAX_VALUE;
		for (Unit unit : units) {
			minX = Math.min(minX, unit.centerX());
			maxX = Math.max(maxX, unit.centerX());
			minY = Math.min(minY, unit.y() + 0.5);
			maxY = Math.max(maxY, unit.y() + 0.5);
			minZ = Math.min(minZ, unit.centerZ());
			maxZ = Math.max(maxZ, unit.centerZ());
		}
		return new double[]{(minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2};
	}

	private static boolean reachable(List<Unit> ordered, List<Integer> run, double reach) {
		List<Unit> units = new ArrayList<>(run.size());
		for (int index : run) {
			units.add(ordered.get(index));
		}
		double[] c = center(units);
		for (Unit unit : units) {
			double dx = unit.centerX() - c[0];
			double dy = unit.y() + 0.5 - c[1];
			double dz = unit.centerZ() - c[2];
			if (dx * dx + dy * dy + dz * dz > reach * reach) {
				return false;
			}
		}
		return true;
	}
}
