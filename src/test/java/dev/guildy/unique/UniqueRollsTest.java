package dev.guildy.unique;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drop rolls: the table chance gates the roll, then the weights pick the item; unusable items are never picked.
 */
class UniqueRollsTest {
	/** nextDouble() == 0: every chance passes, the first pickable entry is picked. */
	private static final RandomGenerator LOWEST = () -> 0L;
	/** nextDouble() just below 1: every chance below 1 fails. */
	private static final RandomGenerator HIGHEST = () -> -1L;

	private static UniqueConfig.DropTable table(double chance, Object... idWeight) {
		UniqueConfig.DropTable table = new UniqueConfig.DropTable();
		table.chance = chance;
		for (int i = 0; i < idWeight.length; i += 2) {
			table.items.put((String) idWeight[i], ((Number) idWeight[i + 1]).doubleValue());
		}
		return table;
	}

	@Test
	void chanceGatesTheRoll() {
		UniqueConfig.DropTable table = table(0.3, "a", 1);
		assertEquals("a", UniqueRolls.roll(table, id -> true, LOWEST));
		assertNull(UniqueRolls.roll(table, id -> true, HIGHEST));
		assertNull(UniqueRolls.roll(table(0, "a", 1), id -> true, LOWEST), "chance 0: never");
		assertEquals("a", UniqueRolls.roll(table(1, "a", 1), id -> true, HIGHEST), "chance 1: always");
		assertNull(UniqueRolls.roll(null, id -> true, LOWEST));
		assertNull(UniqueRolls.roll(table(1), id -> true, LOWEST), "empty table");
	}

	@Test
	void weightsAndUsability() {
		UniqueConfig.DropTable table = table(1, "common", 3, "rare", 1, "zero", 0, "removed", 50);
		SplittableRandom random = new SplittableRandom(42);
		Map<String, Integer> counts = new HashMap<>();
		int rolls = 20_000;
		for (int i = 0; i < rolls; i++) {
			String id = UniqueRolls.roll(table, key -> !key.equals("removed"), random);
			assertNotNull(id);
			counts.merge(id, 1, Integer::sum);
		}
		assertNull(counts.get("zero"), "weight 0 never picked");
		assertNull(counts.get("removed"), "unusable items never picked");
		double common = counts.get("common") / (double) rolls;
		assertTrue(Math.abs(common - 0.75) < 0.02, "3:1 weights, got " + common);
		assertEquals(0.75, UniqueRolls.probability(table, "common", key -> !key.equals("removed")), 1e-9);
		assertEquals(0.0, UniqueRolls.probability(table, "removed", key -> !key.equals("removed")), 1e-9);
		assertEquals(0.25 * 0.5, UniqueRolls.probability(table(0.5, "a", 1, "b", 3), "a", key -> true), 1e-9);
	}

	@Test
	void bloodNightBossesGiveRarerItemsMoreOften() {
		UniqueConfig cfg = new UniqueConfig();
		cfg.rebuild(id -> true);
		double boss = expectedRank(cfg, "boss");
		double blood = expectedRank(cfg, "blood_boss");
		assertTrue(blood > boss, "blood night rarity " + blood + " > boss rarity " + boss);
		assertTrue(cfg.table("blood_boss").chance >= 4 * cfg.table("boss").chance);
		// Seeded rolls of the real default tables only give known items.
		SplittableRandom random = new SplittableRandom(7);
		for (String source : UniqueConfig.SOURCES) {
			for (int i = 0; i < 2_000; i++) {
				String id = UniqueRolls.roll(cfg.table(source), key -> cfg.item(key) != null, random);
				assertTrue(id == null || cfg.item(id) != null, source + " rolled " + id);
			}
		}
	}

	@Test
	void heldWeaponsAreNeverRolled() {
		UniqueConfig.DropTable table = table(1, "foudrelame", 1, "croc_de_givre", 1);
		// LOWEST picks the first entry: held, it is skipped, the other one comes out.
		assertEquals(new UniqueRolls.Outcome(UniqueRolls.Kind.ITEM, "foudrelame"), UniqueRolls.roll(table, id -> true, Set.of(), LOWEST));
		assertEquals(new UniqueRolls.Outcome(UniqueRolls.Kind.ITEM, "croc_de_givre"),
				UniqueRolls.roll(table, id -> true, Set.of("foudrelame"), LOWEST));
		SplittableRandom random = new SplittableRandom(3);
		for (int i = 0; i < 500; i++) {
			assertEquals("croc_de_givre", UniqueRolls.roll(table, id -> true, Set.of("foudrelame"), random).id());
		}
		// Everything held: exhausted (the module then falls back to a scroll), but only when the chance passed.
		assertEquals(UniqueRolls.Kind.EXHAUSTED, UniqueRolls.roll(table, id -> true, Set.of("foudrelame", "croc_de_givre"), LOWEST).kind());
		UniqueConfig.DropTable rare = table(0.3, "foudrelame", 1);
		assertEquals(UniqueRolls.Kind.NOTHING, UniqueRolls.roll(rare, id -> true, Set.of("foudrelame"), HIGHEST).kind(), "failed chance");
		assertEquals(UniqueRolls.Kind.EXHAUSTED, UniqueRolls.roll(rare, id -> true, Set.of("foudrelame"), LOWEST).kind());
		// Items that cannot be created or weigh nothing are not "taken": an empty table is nothing, not exhausted.
		assertEquals(UniqueRolls.Kind.NOTHING, UniqueRolls.roll(table(1, "zero", 0), id -> true, Set.of(), LOWEST).kind());
		assertEquals(UniqueRolls.Kind.NOTHING, UniqueRolls.roll(table, id -> false, Set.of(), LOWEST).kind());
		assertEquals(UniqueRolls.Kind.NOTHING, UniqueRolls.roll(null, id -> true, Set.of(), LOWEST).kind());
	}

	@Test
	void uniquenessModeOfTheConfig() {
		UniqueConfig cfg = new UniqueConfig();
		assertTrue(cfg.rebuild(id -> true).isEmpty());
		assertEquals(UniquenessMode.GUILD, cfg.mode(), "guild by default");
		assertTrue(cfg.fallbackToScroll);
		cfg.uniqueness = " Server ";
		assertTrue(cfg.rebuild(id -> true).isEmpty());
		assertEquals(UniquenessMode.SERVER, cfg.mode());
		assertEquals("server", cfg.uniqueness);
		cfg.uniqueness = "NONE";
		cfg.rebuild(id -> true);
		assertEquals(UniquenessMode.NONE, cfg.mode());
		cfg.uniqueness = "everyone";
		assertEquals(1, cfg.rebuild(id -> true).size(), "unknown mode reported");
		assertEquals(UniquenessMode.GUILD, cfg.mode());
		assertEquals("guild", cfg.uniqueness);
		cfg.uniqueness = null;
		cfg.rebuild(id -> true);
		assertEquals(UniquenessMode.GUILD, cfg.mode());
		assertEquals(null, UniquenessMode.parse("x"));
	}

	/** Expected rarity rank (RARE = 0 ... MYTHIC = 3) of an item rolled from the table. */
	private static double expectedRank(UniqueConfig cfg, String source) {
		UniqueConfig.DropTable table = cfg.table(source);
		double total = 0;
		double ranked = 0;
		for (Map.Entry<String, Double> entry : table.items.entrySet()) {
			total += entry.getValue();
			ranked += entry.getValue() * cfg.item(entry.getKey()).rarity().ordinal();
		}
		return ranked / total;
	}
}
