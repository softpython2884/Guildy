package dev.guildy.villager;

import dev.guildy.villager.StorageRouting.Kind;
import dev.guildy.villager.StorageRouting.Target;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Destination priority of the sorters: filter chests (exact, material, type), category, fallback, deposit; nearest first. */
class StorageRoutingTest {
	private static final String STONE = "Pierre";

	private static ChestFilter material(String family) {
		return ChestFilter.family(ChestFilter.Mode.MATERIAL, family, "");
	}

	@Test
	void filterChestComesBeforeTheCategoryChest() {
		// Index: 0 Pierre (nearest), 1 diorite filter (far), 2 fallback, 3 deposit, 4 granite filter (refuses diorite).
		List<Target> targets = List.of(Target.category(STONE), Target.filter(material("diorite")), Target.fallback(), Target.deposit(),
				Target.filter(material("granite")));
		double[] dist = {1, 400, 4, 2, 1};
		assertEquals(List.of(1, 0, 2, 3), StorageRouting.candidates(targets, dist, "minecraft:polished_diorite", STONE, StorageRouting.DEPOSIT_TIER));
		// Without deposits (the sorter emptying a deposit): never back to a deposit.
		assertEquals(List.of(1, 0, 2), StorageRouting.candidates(targets, dist, "minecraft:diorite_slab", STONE, StorageRouting.FALLBACK_TIER));
		// Cobblestone: no filter takes it, its category chest first.
		assertEquals(List.of(0, 2, 3), StorageRouting.candidates(targets, dist, "minecraft:cobblestone", STONE, StorageRouting.DEPOSIT_TIER));
		// Another category: fallback, then deposit.
		assertEquals(List.of(2, 3), StorageRouting.candidates(targets, dist, "minecraft:oak_log", "Bois", StorageRouting.DEPOSIT_TIER));
	}

	@Test
	void exactItemsBeforeMaterialBeforeTypeThenNearest() {
		List<Target> targets = List.of(
				Target.filter(ChestFilter.family(ChestFilter.Mode.TYPE, "slab", "")),
				Target.filter(material("diorite")),
				Target.filter(ChestFilter.items(List.of("minecraft:diorite_slab", "minecraft:granite"))),
				Target.filter(material("diorite")),
				Target.category(STONE));
		double[] dist = {1, 50, 900, 10, 0};
		assertEquals(List.of(2, 3, 1, 0, 4), StorageRouting.candidates(targets, dist, "minecraft:diorite_slab", STONE, StorageRouting.DEPOSIT_TIER));
		assertEquals(0, StorageRouting.tier(targets.get(2), "minecraft:granite", STONE));
		assertEquals(StorageRouting.NONE, StorageRouting.tier(targets.get(2), "minecraft:polished_granite", STONE));
	}

	@Test
	void reserveAndOtherCategoriesAreNoDestination() {
		Target reserve = new Target(Kind.RESERVE, null, "");
		assertEquals(StorageRouting.NONE, StorageRouting.tier(reserve, "minecraft:stone", STONE));
		assertEquals(StorageRouting.NONE, StorageRouting.tier(Target.category("Bois"), "minecraft:stone", STONE));
		assertEquals(StorageRouting.CATEGORY_TIER, StorageRouting.tier(Target.category("pierre"), "minecraft:stone", STONE), "case ignored");
		assertEquals(StorageRouting.FALLBACK_TIER, StorageRouting.tier(Target.fallback(), "minecraft:stone", STONE));
		assertEquals(StorageRouting.DEPOSIT_TIER, StorageRouting.tier(Target.deposit(), "minecraft:stone", STONE));
		assertEquals(StorageRouting.NONE, StorageRouting.tier(new Target(Kind.FILTER, null, ""), "minecraft:stone", STONE));
	}

	// ------------------------------------------------------------------ routing chains

	private static final String BOOKS = "Livres et enchantements";
	private static final String ENCHANTED = "Livres enchantés";
	private static final List<String> ENCHANTED_BOOK = List.of(ENCHANTED, BOOKS);
	private static final List<String> PAPER = List.of("Livres et papier", BOOKS);

	@Test
	void mostSpecificCategoryOfTheChainFirst() {
		assertEquals(StorageRouting.CATEGORY_TIER, StorageRouting.tier(Target.category(ENCHANTED), "minecraft:enchanted_book", ENCHANTED_BOOK));
		assertEquals(StorageRouting.CATEGORY_TIER + 1, StorageRouting.tier(Target.category(BOOKS), "minecraft:enchanted_book", ENCHANTED_BOOK));
		assertEquals(StorageRouting.NONE, StorageRouting.tier(Target.category(ENCHANTED), "minecraft:paper", PAPER), "paper never goes to enchanted books");
		assertTrue(StorageRouting.CATEGORY_TIER + StorageRouting.MAX_CHAIN - 1 < StorageRouting.FALLBACK_TIER);
		assertTrue(StorageRouting.FALLBACK_TIER < StorageRouting.DEPOSIT_TIER);
		// Filters still come first.
		assertTrue(StorageRouting.tier(Target.filter(ChestFilter.items(List.of("minecraft:enchanted_book"))), "minecraft:enchanted_book", ENCHANTED_BOOK)
				< StorageRouting.CATEGORY_TIER);
	}

	/** The books building: enchanted books go to its chests, the parent's chests take them only without one, misc never. */
	@Test
	void booksBuilding() {
		// 0 books (parent) chest, nearest; 1 fallback; 2 and 3 enchanted books chests (the building, far); 4 deposit.
		List<Target> targets = List.of(Target.category(BOOKS), Target.fallback(), Target.category(ENCHANTED), Target.category(ENCHANTED),
				Target.deposit());
		double[] dist = {1, 2, 300, 200, 0};
		assertEquals(List.of(3, 2, 0, 1, 4), StorageRouting.candidates(targets, dist, "minecraft:enchanted_book", ENCHANTED_BOOK, StorageRouting.DEPOSIT_TIER));
		assertEquals(List.of(0, 1, 4), StorageRouting.candidates(targets, dist, "minecraft:paper", PAPER, StorageRouting.DEPOSIT_TIER));
		assertEquals(List.of(1, 4), StorageRouting.candidates(targets, dist, "minecraft:cobblestone", List.of("Pierre"), StorageRouting.DEPOSIT_TIER),
				"a misc item never reaches the books building");
		// Without the building: the books chest, then the fallback.
		List<Target> before = List.of(Target.category(BOOKS), Target.fallback(), Target.deposit());
		assertEquals(List.of(0, 1, 2), StorageRouting.candidates(before, new double[]{5, 1, 0}, "minecraft:enchanted_book", ENCHANTED_BOOK,
				StorageRouting.DEPOSIT_TIER));
		// Only a fallback: there.
		assertEquals(List.of(0), StorageRouting.candidates(List.of(Target.fallback()), new double[]{1}, "minecraft:enchanted_book", ENCHANTED_BOOK,
				StorageRouting.FALLBACK_TIER));
	}

	// ------------------------------------------------------------------ index

	private static StorageRouting.Index index(List<Target> targets, int[][] positions) {
		int[] xs = new int[targets.size()];
		int[] ys = new int[targets.size()];
		int[] zs = new int[targets.size()];
		for (int i = 0; i < targets.size(); i++) {
			xs[i] = positions[i][0];
			ys[i] = positions[i][1];
			zs[i] = positions[i][2];
		}
		return new StorageRouting.Index(targets, xs, ys, zs);
	}

	private static List<Integer> walk(StorageRouting.Cursor cursor) {
		List<Integer> order = new ArrayList<>();
		for (int i = cursor.next(); i >= 0; i = cursor.next()) {
			order.add(i);
		}
		return order;
	}

	private static final List<String> CATEGORIES = List.of("Pierre", "Bois", BOOKS, ENCHANTED, "Armes", "Équipement enchanté");

	/** A random guild storage: categories, filters, fallbacks, deposits and reserve chests at random positions. */
	private static List<Target> randomTargets(Random random, int count) {
		List<ChestFilter> filters = List.of(material("diorite"), material("granite"), ChestFilter.family(ChestFilter.Mode.TYPE, "slab", ""),
				ChestFilter.items(List.of("minecraft:enchanted_book", "minecraft:diorite_slab")));
		List<Target> targets = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			int kind = random.nextInt(20);
			targets.add(kind < 13 ? Target.category(CATEGORIES.get(random.nextInt(CATEGORIES.size())))
					: kind < 16 ? Target.filter(filters.get(random.nextInt(filters.size())))
					: kind < 17 ? Target.fallback() : kind < 18 ? Target.deposit() : Target.reserve());
		}
		return targets;
	}

	@Test
	void indexGivesTheSameOrderAsTheFullSort() {
		Random random = new Random(12);
		List<Target> targets = randomTargets(random, 400);
		int[][] positions = new int[targets.size()][];
		for (int i = 0; i < positions.length; i++) {
			positions[i] = new int[]{random.nextInt(64), 60 + random.nextInt(8), random.nextInt(64)};
		}
		StorageRouting.Index index = index(targets, positions);
		List<String> ids = List.of("minecraft:diorite_slab", "minecraft:polished_granite", "minecraft:enchanted_book", "minecraft:oak_log",
				"minecraft:diamond_sword", "minecraft:apple");
		List<List<String>> chains = List.of(List.of("Pierre"), List.of("Pierre"), ENCHANTED_BOOK, List.of("Bois"),
				List.of("Équipement enchanté", "Armes"), List.of());
		for (int t = 0; t < ids.size(); t++) {
			double fx = 31.25;
			double fy = 64;
			double fz = 10.5;
			double[] dist = new double[targets.size()];
			for (int i = 0; i < dist.length; i++) {
				double dx = positions[i][0] + 0.5 - fx;
				double dy = positions[i][1] - fy;
				double dz = positions[i][2] + 0.5 - fz;
				dist[i] = dx * dx + dy * dy + dz * dz;
			}
			for (int maxTier : new int[]{StorageRouting.FALLBACK_TIER, StorageRouting.DEPOSIT_TIER}) {
				assertEquals(StorageRouting.candidates(targets, dist, ids.get(t), chains.get(t), maxTier),
						walk(index.cursor(ids.get(t), chains.get(t), maxTier, fx, fy, fz)), ids.get(t) + " up to tier " + maxTier);
			}
		}
		// Every chest that may give an enchanted book: deposits, fallbacks, accepting filters, both book categories.
		int givers = 0;
		for (StorageRouting.Bucket bucket : index.buckets("minecraft:enchanted_book", ENCHANTED_BOOK, StorageRouting.DEPOSIT_TIER)) {
			givers += bucket.members().length;
		}
		int expected = 0;
		for (Target target : targets) {
			expected += StorageRouting.tier(target, "minecraft:enchanted_book", ENCHANTED_BOOK) <= StorageRouting.DEPOSIT_TIER ? 1 : 0;
		}
		assertEquals(expected, givers);
	}

	@Test
	void bestTierOutsideAGroup() {
		List<Target> targets = List.of(Target.category(ENCHANTED), Target.category(ENCHANTED), Target.category(BOOKS), Target.fallback());
		StorageRouting.Index index = index(targets, new int[][]{{0, 0, 0}, {1, 0, 0}, {2, 0, 0}, {3, 0, 0}});
		// The group of chests 0 and 1 (enchanted books): the next better place outside is the books chest.
		assertEquals(StorageRouting.CATEGORY_TIER + 1, index.bestTier("minecraft:enchanted_book", ENCHANTED_BOOK, StorageRouting.FALLBACK_TIER,
				i -> i > 1));
		assertEquals(StorageRouting.CATEGORY_TIER, index.bestTier("minecraft:enchanted_book", ENCHANTED_BOOK, StorageRouting.FALLBACK_TIER, i -> i != 0));
		assertEquals(StorageRouting.NONE, index.bestTier("minecraft:enchanted_book", ENCHANTED_BOOK, StorageRouting.CATEGORY_TIER, i -> i > 1));
	}

	/**
	 * Scale: 5 000 chests of 20 categories (a big chest room), 20 000 lookups of a destination. Only the chests of the
	 * item's tiers are sorted (about 250 here), so the whole run stays far below the budget; the time is printed for the
	 * notes of the release.
	 */
	@Test
	void thousandsOfChestsStayCheap() {
		Random random = new Random(3);
		List<String> categories = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			categories.add("Catégorie " + i);
		}
		List<Target> targets = new ArrayList<>();
		int[][] positions = new int[5000][];
		for (int i = 0; i < 5000; i++) {
			targets.add(i % 100 == 0 ? Target.fallback() : i % 250 == 1 ? Target.deposit() : Target.category(categories.get(i % 20)));
			positions[i] = new int[]{random.nextInt(200), 60 + random.nextInt(20), random.nextInt(200)};
		}
		long buildStart = System.nanoTime();
		StorageRouting.Index index = index(targets, positions);
		long build = System.nanoTime() - buildStart;
		long start = System.nanoTime();
		int found = 0;
		for (int i = 0; i < 20_000; i++) {
			List<String> chain = List.of(categories.get(i % 20));
			StorageRouting.Cursor cursor = index.cursor("minecraft:item_" + (i % 50), chain, StorageRouting.DEPOSIT_TIER, random.nextInt(200), 64,
					random.nextInt(200));
			// The sorter usually stops at one of the first chests of the first tier.
			for (int k = 0; k < 3 && cursor.next() >= 0; k++) {
				found++;
			}
		}
		long elapsed = System.nanoTime() - start;
		// The 1.11 way for comparison: every chest ranked and the whole list sorted at each lookup.
		long fullStart = System.nanoTime();
		for (int i = 0; i < 200; i++) {
			double fx = random.nextInt(200);
			double fz = random.nextInt(200);
			double[] dist = new double[targets.size()];
			for (int k = 0; k < dist.length; k++) {
				double dx = positions[k][0] + 0.5 - fx;
				double dy = positions[k][1] - 64;
				double dz = positions[k][2] + 0.5 - fz;
				dist[k] = dx * dx + dy * dy + dz * dz;
			}
			StorageRouting.candidates(targets, dist, "minecraft:item_" + (i % 50), List.of(categories.get(i % 20)), StorageRouting.DEPOSIT_TIER);
		}
		long full = System.nanoTime() - fullStart;
		System.out.printf("[routing index] 5000 chests: build %.2f ms, 20000 lookups %.1f ms (%.1f us each); full sort of every chest: %.1f us each%n",
				build / 1e6, elapsed / 1e6, elapsed / 1e3 / 20_000, full / 1e3 / 200);
		assertEquals(60_000, found);
		assertTrue(elapsed < 5_000_000_000L, "20 000 lookups among 5 000 chests in less than 5 s (typically well under 1 s)");
	}
}
