package dev.guildy.scroll;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Pure rules of the grimoire (unit-tested): four pages holding scroll ids and the selected page, fusing a scroll (first
 * blank page, never twice the same spell), erasing a page, turning to the next filled page, the custom_model_data value of
 * the item, and the food a cast costs (saturation first, then food points; a fraction of a point left becomes exhaustion,
 * like vanilla).
 */
public final class GrimoireRules {
	/** Number of pages (spells) of a grimoire. */
	public static final int PAGES = 4;
	/** custom_model_data string of a grimoire without a spell (the resource pack shows the bare grimoire). */
	public static final String NO_SPELL_MODEL = "empty";
	/** Exhaustion worth one food point (vanilla FoodData). */
	public static final float EXHAUSTION_PER_POINT = 4.0F;

	private GrimoireRules() {
	}

	/** The pages ("" = blank page, ids in lower case) and the selected page (0 to PAGES - 1). */
	public record Pages(List<String> spells, int selected) {
		public static final Pages EMPTY = new Pages(List.of(), 0);

		public Pages {
			List<String> fixed = new ArrayList<>(PAGES);
			for (int i = 0; i < PAGES; i++) {
				String id = spells != null && i < spells.size() ? spells.get(i) : null;
				fixed.add(id == null ? "" : id.trim().toLowerCase(Locale.ROOT));
			}
			spells = List.copyOf(fixed);
			selected = selected < 0 || selected >= PAGES ? 0 : selected;
		}

		/** Spell id of a page ("" when blank or out of range). */
		public String spell(int page) {
			return page < 0 || page >= PAGES ? "" : spells.get(page);
		}

		public String selectedSpell() {
			return spells.get(selected);
		}

		public int filled() {
			int n = 0;
			for (String id : spells) {
				if (!id.isEmpty()) {
					n++;
				}
			}
			return n;
		}

		public boolean contains(String id) {
			return !id.isEmpty() && spells.contains(id);
		}

		/** First blank page, or -1 when the four pages are written. */
		public int firstBlank() {
			return spells.indexOf("");
		}

		Pages with(int page, String id) {
			List<String> copy = new ArrayList<>(spells);
			copy.set(page, id);
			return new Pages(copy, selected);
		}

		public Pages select(int page) {
			return new Pages(spells, page);
		}
	}

	/** Outcome of a fusion. */
	public enum Fuse {
		/** The spell is written on a blank page (the scroll is consumed). */
		ADDED,
		/** The grimoire already holds this spell. */
		DUPLICATE,
		/** The four pages are written. */
		FULL,
		/** No scroll id. */
		INVALID
	}

	/** Result of a fusion: the new pages (unchanged unless ADDED) and the page written (-1 unless ADDED). */
	public record FuseResult(Fuse outcome, Pages pages, int page) {
	}

	/**
	 * Fuses a scroll: its spell goes on the first blank page; a grimoire whose selected page is blank selects it. Refused when
	 * the spell is already there or when every page is written.
	 */
	public static FuseResult fuse(Pages pages, @Nullable String id) {
		String key = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
		if (key.isEmpty()) {
			return new FuseResult(Fuse.INVALID, pages, -1);
		}
		if (pages.contains(key)) {
			return new FuseResult(Fuse.DUPLICATE, pages, -1);
		}
		int page = pages.firstBlank();
		if (page < 0) {
			return new FuseResult(Fuse.FULL, pages, -1);
		}
		Pages written = pages.with(page, key);
		if (pages.selectedSpell().isEmpty()) {
			written = written.select(page);
		}
		return new FuseResult(Fuse.ADDED, written, page);
	}

	/**
	 * Erases a page (the spell is lost). When it was the selected page, the next written page is selected (the erased one
	 * stays selected when none is left). Out of range or blank pages: unchanged.
	 */
	public static Pages erase(Pages pages, int page) {
		if (pages.spell(page).isEmpty()) {
			return pages;
		}
		Pages erased = pages.with(page, "");
		if (pages.selected() != page) {
			return erased;
		}
		int next = next(erased, id -> true);
		return next < 0 ? erased : erased.select(next);
	}

	/**
	 * The next written (and usable) page after the selected one, cyclic, the selected page itself last; -1 when there is
	 * none.
	 */
	public static int next(Pages pages, Predicate<String> usable) {
		for (int step = 1; step <= PAGES; step++) {
			int page = (pages.selected() + step) % PAGES;
			String id = pages.spell(page);
			if (!id.isEmpty() && usable.test(id)) {
				return page;
			}
		}
		return -1;
	}

	/** A blank selected page while other pages are written (hand-made data): the next written page is selected. */
	public static Pages normalize(Pages pages) {
		if (!pages.selectedSpell().isEmpty() || pages.filled() == 0) {
			return pages;
		}
		return pages.select(next(pages, id -> true));
	}

	/** custom_model_data string of the item: the selected scroll id, {@link #NO_SPELL_MODEL} when the page is blank. */
	public static String modelValue(Pages pages) {
		String id = pages.selectedSpell();
		return id.isEmpty() ? NO_SPELL_MODEL : id;
	}

	/** Food points (0 to 20) a spell of this rarity costs (map by rarity name, 0 when missing). */
	public static int foodCost(@Nullable Map<String, Integer> costs, @Nullable String rarity) {
		if (costs == null || rarity == null) {
			return 0;
		}
		Integer cost = costs.get(rarity.trim().toUpperCase(Locale.ROOT));
		return cost == null ? 0 : Math.max(0, Math.min(20, cost));
	}

	/** A cast is possible when the food bar holds at least the cost (saturation does not count). */
	public static boolean canAfford(int food, int cost) {
		return cost <= 0 || food >= cost;
	}

	/** Food data after a cast: food level, saturation, and the exhaustion to add (a fraction of a point). */
	public record Meal(int food, float saturation, float exhaustion) {
	}

	/** Spends {@code cost} food points: saturation first, then whole food points, the fraction left as exhaustion. */
	public static Meal spend(int food, float saturation, int cost) {
		float sat = Float.isFinite(saturation) ? Math.max(0.0F, saturation) : 0.0F;
		if (cost <= 0) {
			return new Meal(food, sat, 0.0F);
		}
		float fromSaturation = Math.min(sat, cost);
		float rest = cost - fromSaturation;
		int whole = (int) Math.floor(rest + 1.0E-4F);
		float fraction = rest - whole;
		if (fraction < 1.0E-4F) {
			fraction = 0.0F;
		}
		return new Meal(Math.max(0, food - whole), sat - fromSaturation, fraction * EXHAUSTION_PER_POINT);
	}
}
