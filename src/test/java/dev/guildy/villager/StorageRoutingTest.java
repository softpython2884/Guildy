package dev.guildy.villager;

import dev.guildy.villager.StorageRouting.Kind;
import dev.guildy.villager.StorageRouting.Target;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
