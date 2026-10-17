package dev.guildy.villager;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Field zones (persisted in {@link WorkZone#field}): the water holes dug by the farmers (refilled when they disappear)
 * and the farmland that turned back to dirt (trampled or dried out), to be tilled and sown again. Both lists are
 * bounded, oldest entries dropped first. Only plain data and pure list rules (unit tested).
 */
public class FieldState {
	/** Water holes dug by the farmers of the zone (the water block). */
	public List<BlockPos> holes = new ArrayList<>();
	/** Farmland turned back to dirt, oldest first. */
	public List<Trampled> trampled = new ArrayList<>();

	/** A dirt block that was farmland, and the seed of the crop it carried ("" = none / unknown). */
	public static class Trampled {
		public BlockPos pos;
		public String seed = "";

		public Trampled() {
		}

		public Trampled(BlockPos pos, String seed) {
			this.pos = pos;
			this.seed = seed == null ? "" : seed;
		}
	}

	/**
	 * Remembers a spot: an entry at the same position is replaced (and becomes the newest), the oldest entries go
	 * when the list holds more than {@code max} ({@code max} 0 = nothing remembered).
	 *
	 * @return true when the list changed
	 */
	public static boolean remember(List<Trampled> list, BlockPos pos, String seed, int max) {
		if (max <= 0) {
			boolean had = !list.isEmpty();
			list.clear();
			return had;
		}
		BlockPos key = pos.immutable();
		list.removeIf(entry -> entry.pos.equals(key));
		list.add(new Trampled(key, seed));
		trim(list, max);
		return true;
	}

	/** Forgets the spot. @return true when it was remembered */
	public static boolean forget(List<Trampled> list, BlockPos pos) {
		return list.removeIf(entry -> entry.pos.equals(pos));
	}

	/** Adds a hole position once (oldest dropped beyond {@code max}). @return true when added */
	public static boolean addHole(List<BlockPos> holes, BlockPos pos, int max) {
		BlockPos key = pos.immutable();
		if (max <= 0 || holes.contains(key)) {
			return false;
		}
		holes.add(key);
		while (holes.size() > max) {
			holes.removeFirst();
		}
		return true;
	}

	/** Drops the oldest entries beyond {@code max}. */
	public static <T> void trim(List<T> list, int max) {
		while (list.size() > Math.max(0, max)) {
			list.removeFirst();
		}
	}

	/** Fixes nulls and duplicates after loading. */
	void sanitize() {
		if (holes == null) {
			holes = new ArrayList<>();
		}
		Set<BlockPos> seen = new HashSet<>();
		holes.removeIf(pos -> pos == null || !seen.add(pos));
		if (trampled == null) {
			trampled = new ArrayList<>();
		}
		Set<BlockPos> spots = new HashSet<>();
		Iterator<Trampled> it = trampled.iterator();
		while (it.hasNext()) {
			Trampled entry = it.next();
			if (entry == null || entry.pos == null || !spots.add(entry.pos)) {
				it.remove();
				continue;
			}
			if (entry.seed == null) {
				entry.seed = "";
			}
		}
	}
}
