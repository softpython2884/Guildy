package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Pure layout of the items of a chest group (no Minecraft classes: unit tested). Items are abstract stacks: a
 * {@code key} (equal keys = the same item with the same components, interchangeable; lower keys come first in the
 * canonical order) and a count.
 * <ul>
 * <li>{@link #layout}: the perfect layout of a group: every key merged into full stacks followed by one partial stack,
 * keys in ascending order, laid out in reading order across the containers (container 0 slot 0, 1, 2... then container
 * 1...). A stack a container refuses (a shulker box in a shulker box) takes the next slot that accepts it.</li>
 * <li>{@link #arrange}: what a part of the group (a section within reach of one standing spot) becomes with only its own
 * items: each target slot gets what the section has of its key, extra items of a key top its stacks up, and items the
 * section must give away (other keys, extra counts) go into the last free slots. Same items before and after.</li>
 * <li>{@link #transfers}: what must be carried between sections so that each holds exactly the items of its target.</li>
 * </ul>
 */
public final class StorageLayout {
	/** A stack: interchangeable items ({@code key}) and their number (&gt; 0). */
	public record Stack(int key, int count) {
	}

	/** Stack sizes and container restrictions of the items of a group. */
	public interface Rules {
		/** Largest stack of this key in the group's containers (&gt;= 1). */
		int maxStack(int key);

		/** True when container {@code container} may hold this key. */
		default boolean accepts(int container, int key) {
			return true;
		}
	}

	/** Items to carry: {@code count} items of {@code key} from section {@code from} to section {@code to}. */
	public record Transfer(int from, int to, int key, long count) {
	}

	private StorageLayout() {
	}

	/** Total items per key of the given containers (all when {@code containers} is null), keys ascending. */
	public static SortedMap<Integer, Long> totals(@Nullable Stack[][] contents, int @Nullable [] containers) {
		SortedMap<Integer, Long> totals = new TreeMap<>();
		if (containers == null) {
			for (Stack[] container : contents) {
				add(totals, container);
			}
		} else {
			for (int c : containers) {
				add(totals, contents[c]);
			}
		}
		return totals;
	}

	private static void add(SortedMap<Integer, Long> totals, @Nullable Stack[] container) {
		for (Stack stack : container) {
			if (stack != null && stack.count() > 0) {
				totals.merge(stack.key(), (long) stack.count(), Long::sum);
			}
		}
	}

	/**
	 * Perfect layout of {@code totals} in containers of {@code sizes} slots (reading order), or null when it does not
	 * fit.
	 */
	public static @Nullable Stack @Nullable [][] layout(int[] sizes, SortedMap<Integer, Long> totals, Rules rules) {
		Stack[][] result = empty(sizes);
		int cursorContainer = 0;
		int cursorSlot = 0;
		for (Map.Entry<Integer, Long> entry : totals.entrySet()) {
			int key = entry.getKey();
			long left = entry.getValue();
			int max = Math.max(1, rules.maxStack(key));
			while (left > 0) {
				int count = (int) Math.min(max, left);
				// Next free slot from the cursor (reading order) whose container accepts the key.
				int[] slot = nextFree(result, cursorContainer, cursorSlot, key, rules);
				if (slot == null) {
					return null;
				}
				result[slot[0]][slot[1]] = new Stack(key, count);
				left -= count;
				if (slot[0] == cursorContainer && slot[1] == cursorSlot) {
					// Advance the cursor past every used slot.
					int[] next = advance(result, cursorContainer, cursorSlot);
					cursorContainer = next[0];
					cursorSlot = next[1];
				}
			}
		}
		return result;
	}

	private static int @Nullable [] nextFree(Stack[][] result, int fromContainer, int fromSlot, int key, Rules rules) {
		for (int c = fromContainer; c < result.length; c++) {
			if (!rules.accepts(c, key)) {
				continue;
			}
			for (int s = c == fromContainer ? fromSlot : 0; s < result[c].length; s++) {
				if (result[c][s] == null) {
					return new int[]{c, s};
				}
			}
		}
		return null;
	}

	/** First empty slot at or after (container, slot); past the end when none. */
	private static int[] advance(Stack[][] result, int container, int slot) {
		for (int c = container; c < result.length; c++) {
			for (int s = c == container ? slot : 0; s < result[c].length; s++) {
				if (result[c][s] == null) {
					return new int[]{c, s};
				}
			}
		}
		return new int[]{result.length, 0};
	}

	/** Slot-by-slot equality (same key and count, or both empty). */
	public static boolean sameLayout(@Nullable Stack[][] a, @Nullable Stack[][] b) {
		if (a.length != b.length) {
			return false;
		}
		for (int c = 0; c < a.length; c++) {
			if (!sameContainer(a[c], b[c])) {
				return false;
			}
		}
		return true;
	}

	public static boolean sameContainer(@Nullable Stack[] a, @Nullable Stack[] b) {
		if (a.length != b.length) {
			return false;
		}
		for (int s = 0; s < a.length; s++) {
			if (!Objects.equals(a[s], b[s])) {
				return false;
			}
		}
		return true;
	}

	/**
	 * New contents of a section (containers {@code containers} of the group, in reading order) holding exactly the items
	 * it holds now, placed as close to the group's {@code target} as these items allow; null when they do not fit (the
	 * section then stays as it is).
	 *
	 * @return one array per section container (same order as {@code containers})
	 */
	public static @Nullable Stack @Nullable [][] arrange(int[] containers, @Nullable Stack[][] target, @Nullable Stack[][] current, Rules rules) {
		SortedMap<Integer, Long> available = totals(current, containers);
		Stack[][] result = new Stack[containers.length][];
		for (int i = 0; i < containers.length; i++) {
			result[i] = new Stack[current[containers[i]].length];
		}
		// 1. Each target slot receives what the section has of its key.
		for (int i = 0; i < containers.length; i++) {
			Stack[] wanted = target[containers[i]];
			for (int s = 0; s < wanted.length && s < result[i].length; s++) {
				Stack want = wanted[s];
				if (want == null || !rules.accepts(containers[i], want.key())) {
					continue;
				}
				long have = available.getOrDefault(want.key(), 0L);
				int put = (int) Math.min(have, want.count());
				if (put > 0) {
					result[i][s] = new Stack(want.key(), put);
					available.put(want.key(), have - put);
				}
			}
		}
		// 2. Extra items of a key top its stacks up (kept merged).
		for (int i = 0; i < containers.length; i++) {
			for (int s = 0; s < result[i].length; s++) {
				Stack stack = result[i][s];
				if (stack == null) {
					continue;
				}
				long have = available.getOrDefault(stack.key(), 0L);
				int room = Math.max(1, rules.maxStack(stack.key())) - stack.count();
				if (have > 0 && room > 0) {
					int put = (int) Math.min(have, room);
					result[i][s] = new Stack(stack.key(), stack.count() + put);
					available.put(stack.key(), have - put);
				}
			}
		}
		// 3. The rest (items to give away) fills the last free slots, still in canonical order.
		List<Stack> rest = new ArrayList<>();
		for (Map.Entry<Integer, Long> entry : available.entrySet()) {
			long left = entry.getValue();
			int max = Math.max(1, rules.maxStack(entry.getKey()));
			while (left > 0) {
				int count = (int) Math.min(max, left);
				rest.add(new Stack(entry.getKey(), count));
				left -= count;
			}
		}
		for (int r = rest.size() - 1; r >= 0; r--) {
			Stack stack = rest.get(r);
			boolean placed = false;
			for (int i = containers.length - 1; i >= 0 && !placed; i--) {
				if (!rules.accepts(containers[i], stack.key())) {
					continue;
				}
				for (int s = result[i].length - 1; s >= 0; s--) {
					if (result[i][s] == null) {
						result[i][s] = stack;
						placed = true;
						break;
					}
				}
			}
			if (!placed) {
				return null;
			}
		}
		return result;
	}

	/**
	 * Items to carry between sections (lists of container indices) so that each section holds the items of its part of
	 * {@code target}; keys absent from the target (items to take out of the group) are ignored. Surplus goes to the
	 * nearest section in need first.
	 */
	public static List<Transfer> transfers(List<int[]> sections, @Nullable Stack[][] target, @Nullable Stack[][] current) {
		List<SortedMap<Integer, Long>> wanted = new ArrayList<>();
		List<SortedMap<Integer, Long>> held = new ArrayList<>();
		SortedMap<Integer, Long> keys = totals(target, null);
		for (int[] section : sections) {
			wanted.add(totals(target, section));
			held.add(totals(current, section));
		}
		List<Transfer> transfers = new ArrayList<>();
		for (int key : keys.keySet()) {
			Map<Integer, Long> surplus = new LinkedHashMap<>();
			Map<Integer, Long> deficit = new LinkedHashMap<>();
			for (int i = 0; i < sections.size(); i++) {
				long diff = held.get(i).getOrDefault(key, 0L) - wanted.get(i).getOrDefault(key, 0L);
				if (diff > 0) {
					surplus.put(i, diff);
				} else if (diff < 0) {
					deficit.put(i, -diff);
				}
			}
			for (Map.Entry<Integer, Long> from : surplus.entrySet()) {
				long left = from.getValue();
				while (left > 0 && !deficit.isEmpty()) {
					int best = -1;
					for (int to : deficit.keySet()) {
						if (best < 0 || Math.abs(to - from.getKey()) < Math.abs(best - from.getKey())) {
							best = to;
						}
					}
					long need = deficit.get(best);
					long move = Math.min(left, need);
					transfers.add(new Transfer(from.getKey(), best, key, move));
					left -= move;
					if (need == move) {
						deficit.remove(best);
					} else {
						deficit.put(best, need - move);
					}
				}
			}
		}
		return transfers;
	}

	/** Number of non-empty slots. */
	public static int used(@Nullable Stack[] container) {
		int used = 0;
		for (Stack stack : container) {
			if (stack != null) {
				used++;
			}
		}
		return used;
	}

	private static Stack[][] empty(int[] sizes) {
		Stack[][] result = new Stack[sizes.length][];
		for (int i = 0; i < sizes.length; i++) {
			result[i] = new Stack[sizes[i]];
		}
		return result;
	}
}
