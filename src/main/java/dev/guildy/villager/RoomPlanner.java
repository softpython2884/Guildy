package dev.guildy.villager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pure planning of a chest room ("Salle des coffres", no Minecraft classes: unit tested): which category each automatic
 * chest receives. The room is cut into lines (columns or rows, in reading order, see {@link ChestOrder}); a category
 * gets whole lines, as one block when possible.
 * <ul>
 * <li>First plan (new room, or {@code fresh}): every category holding items gets a block sized to what it holds plus
 * headroom, in the category order of villagers.json5, the fallback category last, the remaining lines are free (the
 * reserve). Short of lines: the fallback and the biggest categories first.</li>
 * <li>Later plans change little (hysteresis): a category only grows (one line per plan) when fewer than
 * {@code growFreeSlots} slots are left in its lines, a category without line gets one once it holds
 * {@code newCategoryStacks} stacks (before that its items go to the fallback), and a line of a category that stayed
 * empty for long ({@code idleLines}) goes back to the reserve when its category does not need it. A growing category
 * takes the free line next to its block, else the blocks between it and the nearest free line move by one line when
 * they are at most {@code maxShift}, else it takes the nearest free line.</li>
 * <li>Categories to keep ({@code keep}: the fallback of an ordinary room, every category of a room reserved to a few
 * categories) always have a line, even empty; a room reserved to some categories has no fallback unless it is one of
 * them.</li>
 * </ul>
 */
public final class RoomPlanner {
	/** Category of a free (reserve) chest. */
	public static final String FREE = "";

	/** One line of automatic chests (reading order) and its number of slots. */
	public record Line(List<Integer> chests, int capacity) {
	}

	public record Settings(int headroomPercent, int growFreeSlots, int newCategoryStacks, int maxShift) {
	}

	/**
	 * New category per chest ({@link #FREE} = reserve), the chests whose category changed, and the categories that need a
	 * line but could not get one (room full).
	 */
	public record Result(Map<Integer, String> assignment, Set<Integer> changed, List<String> unplaced) {
	}

	/** A container of the room: position / facing / double chest ({@link ChestOrder.Unit}), slots, automatic or manual. */
	public record RoomChest(ChestOrder.Unit unit, int slots, boolean automatic) {
	}

	private RoomPlanner() {
	}

	/**
	 * Plans a room from its containers: lines = the columns or rows of {@link ChestOrder} ({@code mode}); manual
	 * containers take no part (never reassigned); a line is idle when all its automatic chests are in
	 * {@code idleChests}. Ids are the unit ids.
	 */
	public static Result planRoom(List<RoomChest> chests, ChestOrder.Mode mode, Map<Integer, String> current, List<String> categories,
			String fallback, Map<String, Integer> demand, Set<Integer> idleChests, Settings settings, boolean fresh) {
		return planRoom(chests, mode, current, categories, fallback, demand, idleChests, settings, fresh, keepFallback(fallback));
	}

	/** Same as the other {@code planRoom} with the categories always given a line ({@code keep}). */
	public static Result planRoom(List<RoomChest> chests, ChestOrder.Mode mode, Map<Integer, String> current, List<String> categories,
			String fallback, Map<String, Integer> demand, Set<Integer> idleChests, Settings settings, boolean fresh, Set<String> keep) {
		Map<Integer, RoomChest> byId = new HashMap<>();
		List<ChestOrder.Unit> units = new ArrayList<>();
		for (RoomChest chest : chests) {
			byId.put(chest.unit().id(), chest);
			units.add(chest.unit());
		}
		List<Line> lines = new ArrayList<>();
		Set<Integer> idle = new LinkedHashSet<>();
		Map<Integer, String> automatic = new HashMap<>();
		for (List<Integer> line : ChestOrder.order(units, mode).lines()) {
			List<Integer> autos = new ArrayList<>();
			int capacity = 0;
			boolean lineIdle = true;
			for (int id : line) {
				RoomChest chest = byId.get(id);
				if (chest == null || !chest.automatic()) {
					continue;
				}
				autos.add(id);
				capacity += Math.max(0, chest.slots());
				lineIdle &= idleChests.contains(id);
				String category = current.get(id);
				if (category != null) {
					automatic.put(id, category);
				}
			}
			if (!autos.isEmpty()) {
				if (lineIdle) {
					idle.add(lines.size());
				}
				lines.add(new Line(autos, capacity));
			}
		}
		return plan(lines, automatic, categories, fallback, demand, idle, settings, fresh, keep);
	}

	private static Set<String> keepFallback(String fallback) {
		return fallback == null || fallback.isBlank() ? Set.of() : Set.of(fallback);
	}

	/**
	 * @param lines      lines of automatic chests in reading order
	 * @param current    chest → current category (missing / unknown names = free)
	 * @param categories configured categories in config order (without the fallback)
	 * @param fallback   fallback category ("Divers")
	 * @param demand     stacks held per category (fallback included; missing = 0)
	 * @param idleLines  indices of the lines empty for a long time
	 * @param fresh      plan from scratch, ignoring the current assignment
	 */
	public static Result plan(List<Line> lines, Map<Integer, String> current, List<String> categories, String fallback,
			Map<String, Integer> demand, Set<Integer> idleLines, Settings settings, boolean fresh) {
		return plan(lines, current, categories, fallback, demand, idleLines, settings, fresh, keepFallback(fallback));
	}

	/**
	 * Same as the other {@code plan}; {@code fallback} may be blank (no fallback line: a room reserved to some
	 * categories), {@code keep} = categories that always get one line.
	 */
	public static Result plan(List<Line> lines, Map<Integer, String> current, List<String> categories, String fallback,
			Map<String, Integer> demand, Set<Integer> idleLines, Settings settings, boolean fresh, Set<String> keep) {
		boolean hasFallback = fallback != null && !fallback.isBlank();
		fallback = hasFallback ? fallback : FREE;
		List<String> order = new ArrayList<>();
		for (String category : categories) {
			if (category != null && !category.isBlank() && !category.equalsIgnoreCase(fallback) && index(order, category) < 0) {
				order.add(category);
			}
		}
		if (hasFallback) {
			order.add(fallback);
		}
		Set<String> kept = new LinkedHashSet<>();
		for (String category : keep) {
			String name = canonical(order, category);
			if (!name.equals(FREE)) {
				kept.add(name);
			}
		}
		Map<String, Integer> held = new HashMap<>();
		for (Map.Entry<String, Integer> entry : demand.entrySet()) {
			String name = canonical(order, entry.getKey());
			if (!name.equals(FREE) && entry.getValue() != null) {
				held.merge(name, Math.max(0, entry.getValue()), Integer::sum);
			}
		}
		int n = lines.size();
		String[] cat = new String[n];
		boolean anyAssigned = false;
		for (int i = 0; i < n; i++) {
			cat[i] = fresh ? FREE : majority(lines.get(i), current, order);
			anyAssigned |= !cat[i].equals(FREE);
		}
		List<String> unplaced = new ArrayList<>();
		if (!anyAssigned) {
			initial(lines, cat, order, fallback, kept, held, settings, unplaced);
		} else {
			shrink(lines, cat, fallback, kept, held, idleLines, settings);
			grow(lines, cat, order, fallback, kept, held, settings, unplaced);
		}
		Map<Integer, String> assignment = new LinkedHashMap<>();
		Set<Integer> changed = new TreeSet<>();
		for (int i = 0; i < n; i++) {
			for (int chest : lines.get(i).chests()) {
				assignment.put(chest, cat[i]);
				// Compared with the name actually saved: a category the room no longer plans (removed, or outside the
				// categories a room is reserved to) changes too, even into the reserve.
				String before = current.get(chest);
				boolean same = before == null || before.isBlank() ? cat[i].equals(FREE) : before.trim().equalsIgnoreCase(cat[i]);
				if (!same) {
					changed.add(chest);
				}
			}
		}
		return new Result(assignment, changed, unplaced);
	}

	/** Slots a category should have: what it holds plus headroom (0 when it holds nothing). */
	static int need(int held, Settings settings) {
		if (held <= 0) {
			return 0;
		}
		long withHeadroom = (long) Math.ceil(held * (100.0 + Math.max(0, settings.headroomPercent())) / 100.0);
		return (int) Math.min(Integer.MAX_VALUE, Math.max(held + (long) Math.max(1, settings.growFreeSlots()), withHeadroom));
	}

	// ------------------------------------------------------------------ first plan

	private static void initial(List<Line> lines, String[] cat, List<String> order, String fallback, Set<String> keep,
			Map<String, Integer> held, Settings settings, List<String> unplaced) {
		int n = lines.size();
		List<String> wanted = new ArrayList<>();
		for (String category : order) {
			if (keep.contains(category) || held.getOrDefault(category, 0) > 0) {
				wanted.add(category);
			}
		}
		if (wanted.size() > n) {
			// Short of lines: the categories to keep (the fallback first), then the categories holding the most.
			List<String> byDemand = new ArrayList<>(wanted);
			byDemand.removeAll(keep);
			byDemand.sort((a, b) -> Integer.compare(held.getOrDefault(b, 0), held.getOrDefault(a, 0)));
			Set<String> kept = new LinkedHashSet<>();
			if (n > 0 && keep.contains(fallback)) {
				kept.add(fallback);
			}
			for (String category : wanted) {
				if (keep.contains(category) && kept.size() < n) {
					kept.add(category);
				} else if (keep.contains(category) && !kept.contains(category)) {
					unplaced.add(category);
				}
			}
			for (String category : byDemand) {
				if (kept.size() >= n) {
					unplaced.add(category);
				} else {
					kept.add(category);
				}
			}
			wanted.removeIf(category -> !kept.contains(category));
		}
		int pos = 0;
		for (int w = 0; w < wanted.size(); w++) {
			String category = wanted.get(w);
			int maxLines = n - pos - (wanted.size() - w - 1);
			int holds = held.getOrDefault(category, 0);
			if (category.equals(fallback)) {
				// The fallback also receives the items of the categories left without line.
				for (String other : unplaced) {
					holds += held.getOrDefault(other, 0);
				}
			}
			int need = Math.max(1, need(holds, settings));
			int taken = 0;
			int capacity = 0;
			while (taken < maxLines && (taken == 0 || capacity < need)) {
				capacity += lines.get(pos + taken).capacity();
				taken++;
			}
			for (int i = 0; i < taken; i++) {
				cat[pos + i] = category;
			}
			pos += taken;
		}
	}

	// ------------------------------------------------------------------ later plans

	/** Long-empty lines go back to the reserve when their category does not need them (one fallback line is kept). */
	private static void shrink(List<Line> lines, String[] cat, String fallback, Set<String> keep, Map<String, Integer> held,
			Set<Integer> idleLines, Settings settings) {
		List<Integer> idle = new ArrayList<>(new TreeSet<>(idleLines));
		for (int k = idle.size() - 1; k >= 0; k--) {
			int i = idle.get(k);
			if (i < 0 || i >= cat.length || cat[i].equals(FREE)) {
				continue;
			}
			String category = cat[i];
			int count = 0;
			int capacity = 0;
			for (int j = 0; j < cat.length; j++) {
				if (cat[j].equals(category)) {
					count++;
					capacity += lines.get(j).capacity();
				}
			}
			if (keep.contains(category) && count <= 1) {
				continue;
			}
			int holds = holds(cat, category, fallback, held);
			if (holds == 0 || capacity - lines.get(i).capacity() >= need(holds, settings)) {
				cat[i] = FREE;
			}
		}
	}

	/** One more line for each category running out of room (or holding enough items to deserve its first line). */
	private static void grow(List<Line> lines, String[] cat, List<String> order, String fallback, Set<String> keep,
			Map<String, Integer> held, Settings settings, List<String> unplaced) {
		for (String category : order) {
			int holds = holds(cat, category, fallback, held);
			int capacity = 0;
			int first = -1;
			int last = -1;
			for (int i = 0; i < cat.length; i++) {
				if (cat[i].equals(category)) {
					capacity += lines.get(i).capacity();
					first = first < 0 ? i : first;
					last = i;
				}
			}
			boolean wants;
			if (first < 0) {
				// The fallback of an ordinary room once it holds something; a category of a reserved room at once.
				wants = keep.contains(category) ? holds > 0 || !category.equals(fallback) : holds >= Math.max(1, settings.newCategoryStacks());
			} else {
				wants = holds > 0 && capacity - holds < Math.max(1, settings.growFreeSlots());
			}
			if (!wants) {
				continue;
			}
			if (!take(cat, category, order, first, last, settings.maxShift())) {
				unplaced.add(category);
			}
		}
	}

	/** Gives one free line to {@code category} (block [first, last], -1 when none); false when no line is free. */
	private static boolean take(String[] cat, String category, List<String> order, int first, int last, int maxShift) {
		List<Integer> points = new ArrayList<>();
		if (first >= 0) {
			points.add(last + 1);
			points.add(first);
		} else {
			points.add(idealPosition(cat, category, order));
		}
		int bestFree = -1;
		int bestPoint = -1;
		int bestCost = Integer.MAX_VALUE;
		int bestDistance = Integer.MAX_VALUE;
		for (int f = 0; f < cat.length; f++) {
			if (!cat[f].equals(FREE)) {
				continue;
			}
			for (int p : points) {
				int cost = shiftCost(cat, p, f);
				int distance = f >= p ? f - p : p - 1 - f;
				if (cost < bestCost || (cost == bestCost && distance < bestDistance)) {
					bestCost = cost;
					bestDistance = distance;
					bestFree = f;
					bestPoint = p;
				}
			}
		}
		if (bestFree < 0) {
			return false;
		}
		if (bestCost > Math.max(0, maxShift)) {
			// Too many blocks to move: the nearest free line, even apart from the block.
			int nearest = -1;
			int nearestDistance = Integer.MAX_VALUE;
			for (int f = 0; f < cat.length; f++) {
				if (!cat[f].equals(FREE)) {
					continue;
				}
				for (int p : points) {
					int distance = f >= p ? f - p : p - 1 - f;
					if (distance < nearestDistance) {
						nearestDistance = distance;
						nearest = f;
					}
				}
			}
			cat[nearest] = category;
			return true;
		}
		if (bestFree >= bestPoint) {
			for (int i = bestFree; i > bestPoint; i--) {
				cat[i] = cat[i - 1];
			}
			cat[bestPoint] = category;
		} else {
			for (int i = bestFree; i < bestPoint - 1; i++) {
				cat[i] = cat[i + 1];
			}
			cat[bestPoint - 1] = category;
		}
		return true;
	}

	/** Number of category blocks that move when free line {@code free} is brought to insertion point {@code point}. */
	private static int shiftCost(String[] cat, int point, int free) {
		Set<String> moved = new LinkedHashSet<>();
		if (free >= point) {
			for (int i = point; i < free; i++) {
				moved.add(cat[i]);
			}
		} else {
			for (int i = free + 1; i < point; i++) {
				moved.add(cat[i]);
			}
		}
		moved.remove(FREE);
		return moved.size();
	}

	/** Where a category without line belongs: after the nearest preceding category placed, else before the next one. */
	private static int idealPosition(String[] cat, String category, List<String> order) {
		int index = order.indexOf(category);
		for (int k = index - 1; k >= 0; k--) {
			for (int i = cat.length - 1; i >= 0; i--) {
				if (cat[i].equals(order.get(k))) {
					return i + 1;
				}
			}
		}
		for (int k = index + 1; k < order.size(); k++) {
			for (int i = 0; i < cat.length; i++) {
				if (cat[i].equals(order.get(k))) {
					return i;
				}
			}
		}
		return 0;
	}

	// ------------------------------------------------------------------ helpers

	/** Stacks a category's lines must hold: its own, plus for the fallback those of the categories without line. */
	private static int holds(String[] cat, String category, String fallback, Map<String, Integer> held) {
		int holds = held.getOrDefault(category, 0);
		if (!category.equals(fallback)) {
			return holds;
		}
		Set<String> placed = new LinkedHashSet<>(List.of(cat));
		for (Map.Entry<String, Integer> entry : held.entrySet()) {
			if (!entry.getKey().equals(fallback) && !placed.contains(entry.getKey())) {
				holds += entry.getValue();
			}
		}
		return holds;
	}

	/** Most frequent known category of the line's chests (ties: config order), {@link #FREE} when none. */
	private static String majority(Line line, Map<Integer, String> current, List<String> order) {
		Map<String, Integer> votes = new HashMap<>();
		for (int chest : line.chests()) {
			String name = canonical(order, current.get(chest));
			if (!name.equals(FREE)) {
				votes.merge(name, 1, Integer::sum);
			}
		}
		String best = FREE;
		int bestVotes = 0;
		for (String category : order) {
			int count = votes.getOrDefault(category, 0);
			if (count > bestVotes) {
				best = category;
				bestVotes = count;
			}
		}
		return best;
	}

	/** Spelling of {@code name} in {@code order} (case ignored), {@link #FREE} when unknown or blank. */
	private static String canonical(List<String> order, String name) {
		if (name == null || name.isBlank()) {
			return FREE;
		}
		int index = index(order, name);
		return index < 0 ? FREE : order.get(index);
	}

	private static int index(List<String> order, String name) {
		for (int i = 0; i < order.size(); i++) {
			if (order.get(i).equalsIgnoreCase(name.trim())) {
				return i;
			}
		}
		return -1;
	}
}
