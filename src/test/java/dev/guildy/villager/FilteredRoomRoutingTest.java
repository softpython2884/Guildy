package dev.guildy.villager;

import dev.guildy.villager.StorageRouting.Target;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Routing to the chests of a filtered room: a chest receives the kinds it already holds (several kinds, changing with its
 * contents), never anything while empty; it ranks with the filter chests listing the item (nearest first), before the
 * material and type families, the categories, the fallback and the deposits.
 */
class FilteredRoomRoutingTest {
	private static final List<String> STONE = List.of("Pierre");
	private static final List<String> FOOD = List.of("Nourriture");
	private static final List<String> WOOD = List.of("Bois");

	/** Routing index whose filtered-room targets are the chests of {@code contents} (chest id = target index). */
	private static StorageRouting.Index index(List<Target> targets, ContentIndex contents) {
		int[] xs = new int[targets.size()];
		int[] ys = new int[targets.size()];
		int[] zs = new int[targets.size()];
		for (int i = 0; i < targets.size(); i++) {
			xs[i] = i * 3;
		}
		return new StorageRouting.Index(targets, xs, ys, zs, key -> {
			long[] holders = contents.holders(key);
			int[] members = new int[holders.length];
			for (int i = 0; i < holders.length; i++) {
				members[i] = (int) holders[i];
			}
			return members;
		});
	}

	private static List<Integer> walk(StorageRouting.Cursor cursor) {
		List<Integer> order = new ArrayList<>();
		for (int i = cursor.next(); i >= 0; i = cursor.next()) {
			order.add(i);
		}
		return order;
	}

	@Test
	void aChestReceivesTheKindsItHoldsAndAnEmptyOneNothing() {
		ContentIndex contents = new ContentIndex();
		contents.update(0, List.of("minecraft:diorite", "minecraft:granite"));
		contents.update(1, List.of("minecraft:wheat"));
		contents.update(2, List.of());
		Target diorite = Target.content(contents.kinds(0));
		Target wheat = Target.content(contents.kinds(1));
		Target empty = Target.content(contents.kinds(2));
		assertEquals(StorageRouting.CONTENT_TIER, StorageRouting.tier(diorite, "minecraft:diorite", STONE));
		assertEquals(StorageRouting.CONTENT_TIER, StorageRouting.tier(diorite, "minecraft:granite", STONE), "several kinds per chest");
		assertEquals(StorageRouting.NONE, StorageRouting.tier(diorite, "minecraft:polished_diorite", STONE), "no family: exactly what it holds");
		assertEquals(StorageRouting.CONTENT_TIER, StorageRouting.tier(wheat, "minecraft:wheat", FOOD));
		assertEquals(StorageRouting.NONE, StorageRouting.tier(empty, "minecraft:diorite", STONE), "an empty chest receives nothing");
		assertEquals(StorageRouting.NONE, StorageRouting.tier(empty, "minecraft:oak_log", WOOD));
		// The kind of a potion, not its item id, is what the chest must hold.
		contents.update(2, List.of("minecraft:potion[minecraft:healing]"));
		assertEquals(StorageRouting.CONTENT_TIER, StorageRouting.tier(empty, "minecraft:potion", "minecraft:potion[minecraft:healing]", List.of()));
		assertEquals(StorageRouting.NONE, StorageRouting.tier(empty, "minecraft:potion", "minecraft:potion[minecraft:poison]", List.of()));
	}

	@Test
	void filteredRoomChestsRankWithExactFiltersBeforeFamiliesAndCategories() {
		ContentIndex contents = new ContentIndex();
		// 0 Pierre (nearest), 1 type "slab" filter, 2 material "diorite" filter, 3 exact filter (far), 4 filtered-room chest
		// holding diorite slabs, 5 empty filtered-room chest, 6 fallback, 7 deposit.
		List<Target> targets = List.of(Target.category("Pierre"), Target.filter(ChestFilter.family(ChestFilter.Mode.TYPE, "slab", "")),
				Target.filter(ChestFilter.family(ChestFilter.Mode.MATERIAL, "diorite", "")),
				Target.filter(ChestFilter.items(List.of("minecraft:diorite_slab"))), Target.content(contents.kinds(4)),
				Target.content(contents.kinds(5)), Target.fallback(), Target.deposit());
		contents.update(4, List.of("minecraft:diorite_slab", "minecraft:bone"));
		contents.update(5, List.of());
		double[] dist = {0, 1, 2, 900, 30, 0, 5, 6};
		// Exact filter and filtered-room chest share the first tier: nearest first; the empty chest never.
		assertEquals(List.of(4, 3, 2, 1, 0, 6, 7), StorageRouting.candidates(targets, dist, "minecraft:diorite_slab", "minecraft:diorite_slab", STONE,
				StorageRouting.DEPOSIT_TIER));
		// Bones: only the chest that holds some, then the fallback (no deposit while sorting a deposit).
		assertEquals(List.of(4, 6), StorageRouting.candidates(targets, dist, "minecraft:bone", "minecraft:bone", List.of("Butin"),
				StorageRouting.FALLBACK_TIER));
		// The same order from the index (the filtered-room chests asked to the live contents).
		StorageRouting.Index index = index(targets, contents);
		assertEquals(List.of(4, 3, 2, 1, 0, 6, 7), walk(index.cursor("minecraft:diorite_slab", "minecraft:diorite_slab", STONE,
				StorageRouting.DEPOSIT_TIER, 100, 0, 0)));
	}

	@Test
	void theIndexFollowsTheContentsOfTheChests() {
		ContentIndex contents = new ContentIndex();
		List<Target> targets = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			targets.add(Target.content(contents.kinds(i)));
		}
		targets.add(Target.fallback());
		contents.update(0, List.of("minecraft:diorite", "minecraft:granite"));
		contents.update(1, List.of("minecraft:wheat"));
		contents.update(2, List.of());
		StorageRouting.Index index = index(targets, contents);
		assertEquals(List.of(0, 3), walk(index.cursor("minecraft:diorite", "minecraft:diorite", STONE, StorageRouting.FALLBACK_TIER, 0, 0, 0)));
		assertEquals(List.of(1, 3), walk(index.cursor("minecraft:wheat", "minecraft:wheat", FOOD, StorageRouting.FALLBACK_TIER, 0, 0, 0)));
		assertEquals(List.of(3), walk(index.cursor("minecraft:oak_log", "minecraft:oak_log", WOOD, StorageRouting.FALLBACK_TIER, 0, 0, 0)),
				"an unrelated item: the fallback, never the empty chest");
		// A player puts cobblestone into the empty chest: from now on it receives cobblestone.
		contents.update(2, List.of("minecraft:cobblestone"));
		assertEquals(List.of(2, 3), walk(index.cursor("minecraft:cobblestone", "minecraft:cobblestone", STONE, StorageRouting.FALLBACK_TIER, 0, 0, 0)));
		assertEquals(StorageRouting.CONTENT_TIER, StorageRouting.tier(targets.get(2), "minecraft:cobblestone", STONE));
		// The last granite leaves chest 0: it no longer receives granite, still diorite.
		contents.update(0, List.of("minecraft:diorite"));
		assertEquals(List.of(3), walk(index.cursor("minecraft:granite", "minecraft:granite", STONE, StorageRouting.FALLBACK_TIER, 0, 0, 0)));
		assertEquals(List.of(0, 3), walk(index.cursor("minecraft:diorite", "minecraft:diorite", STONE, StorageRouting.FALLBACK_TIER, 0, 0, 0)));
		assertEquals(StorageRouting.CONTENT_TIER, index.bestTier("minecraft:diorite", "minecraft:diorite", STONE, StorageRouting.FALLBACK_TIER, i -> true));
		assertEquals(StorageRouting.FALLBACK_TIER, index.bestTier("minecraft:diorite", "minecraft:diorite", STONE, StorageRouting.FALLBACK_TIER,
				i -> i != 0), "outside chest 0, only the fallback");
		// A provider naming a target that is no filtered-room chest is ignored.
		StorageRouting.Index wrong = new StorageRouting.Index(targets, new int[4], new int[4], new int[4], key -> new int[]{3, 3, 0});
		assertEquals(List.of(0, 3), walk(wrong.cursor("minecraft:diorite", "minecraft:diorite", STONE, StorageRouting.FALLBACK_TIER, 0, 0, 0)));
	}

	@Test
	void indexAndCandidatesAgreeOnRandomStorages() {
		Random random = new Random(72);
		List<String> kinds = List.of("minecraft:diorite", "minecraft:granite", "minecraft:wheat", "minecraft:diorite_slab", "minecraft:bone",
				"minecraft:oak_log");
		List<List<String>> chains = List.of(STONE, STONE, FOOD, STONE, List.of("Butin"), WOOD);
		for (int round = 0; round < 50; round++) {
			ContentIndex contents = new ContentIndex();
			List<Target> targets = new ArrayList<>();
			int count = 10 + random.nextInt(40);
			int[] xs = new int[count];
			int[] ys = new int[count];
			int[] zs = new int[count];
			for (int i = 0; i < count; i++) {
				switch (random.nextInt(6)) {
					case 0, 1 -> {
						List<String> held = new ArrayList<>();
						for (String kind : kinds) {
							if (random.nextInt(4) == 0) {
								held.add(kind);
							}
						}
						contents.update(i, held);
						targets.add(Target.content(contents.kinds(i)));
					}
					case 2 -> targets.add(Target.filter(ChestFilter.items(List.of(kinds.get(random.nextInt(kinds.size()))))));
					case 3 -> targets.add(Target.category(random.nextBoolean() ? "Pierre" : "Nourriture"));
					case 4 -> targets.add(Target.fallback());
					default -> targets.add(Target.deposit());
				}
				xs[i] = random.nextInt(100);
				ys[i] = random.nextInt(10);
				zs[i] = random.nextInt(100);
			}
			StorageRouting.Index index = new StorageRouting.Index(targets, xs, ys, zs, key -> {
				long[] holders = contents.holders(key);
				int[] members = new int[holders.length];
				for (int h = 0; h < holders.length; h++) {
					members[h] = (int) holders[h];
				}
				return members;
			});
			for (int k = 0; k < kinds.size(); k++) {
				String kind = kinds.get(k);
				double x = random.nextInt(100);
				double z = random.nextInt(100);
				double[] dist = new double[count];
				for (int i = 0; i < count; i++) {
					double dx = xs[i] + 0.5 - x;
					double dy = ys[i];
					double dz = zs[i] + 0.5 - z;
					dist[i] = (float) (dx * dx + dy * dy + dz * dz);
				}
				for (int maxTier : new int[]{StorageRouting.FALLBACK_TIER, StorageRouting.DEPOSIT_TIER}) {
					List<Integer> expected = StorageRouting.candidates(targets, dist, kind, kind, chains.get(k), maxTier);
					List<Integer> actual = walk(index.cursor(kind, kind, chains.get(k), maxTier, x, 0, z));
					assertEquals(expected, actual, "round " + round + ", " + kind);
					for (int target : actual) {
						if (targets.get(target).kind() == StorageRouting.Kind.CONTENT) {
							assertTrue(contents.holds(target, kind), "a filtered-room chest receives only what it holds");
						}
					}
				}
			}
		}
	}
}
