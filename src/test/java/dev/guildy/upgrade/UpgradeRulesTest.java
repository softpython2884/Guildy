package dev.guildy.upgrade;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpgradeRulesTest {
	private static final UpgradeTree TREE = UpgradeTree.build(UpgradeConfig.defaultBranches(), UpgradeConfig.defaultNodes(),
			id -> true, new ArrayList<>());
	private static final ToIntFunction<String> NOTHING = item -> 0;

	private static UpgradeTree.Node node(String id) {
		UpgradeTree.Node node = TREE.node(id);
		assertTrue(node != null, id);
		return node;
	}

	@Test
	void stateFollowsLevelAndRequirements() {
		UpgradeTree.Node heart = node("coeur_renforce");
		UpgradeTree.Node guards = node("gardes_aguerris");
		assertEquals(UpgradeRules.State.AVAILABLE, UpgradeRules.state(heart, Set.of(), 1));
		assertEquals(UpgradeRules.State.LOCKED, UpgradeRules.state(guards, Set.of(), 5), "previous tier missing");
		assertEquals(UpgradeRules.State.LOCKED, UpgradeRules.state(guards, Set.of("coeur_renforce"), 1), "level too low");
		assertEquals(UpgradeRules.State.AVAILABLE, UpgradeRules.state(guards, Set.of("coeur_renforce"), 2));
		assertEquals(UpgradeRules.State.UNLOCKED, UpgradeRules.state(heart, Set.of("coeur_renforce"), 1));
		assertEquals(List.of("coeur_renforce"), UpgradeRules.missingRequirements(guards, Set.of("primes_quetes")));
		assertTrue(UpgradeRules.missingRequirements(guards, Set.of("coeur_renforce")).isEmpty());
	}

	@Test
	void checkReportsTheFirstProblem() {
		UpgradeTree.Node heart = node("coeur_renforce"); // level 1, 30 gold + 8 iron ingots
		UpgradeTree.Node guards = node("gardes_aguerris"); // level 2, requires coeur_renforce
		ToIntFunction<String> eightIron = item -> item.equals("minecraft:iron_ingot") ? 8 : 0;
		ToIntFunction<String> sevenIron = item -> item.equals("minecraft:iron_ingot") ? 7 : 0;

		assertEquals(UpgradeRules.Problem.ALREADY_UNLOCKED, UpgradeRules.check(heart, Set.of("coeur_renforce"), 9, 999, eightIron));
		assertEquals(UpgradeRules.Problem.LEVEL, UpgradeRules.check(guards, Set.of(), 1, 999, NOTHING), "level before requirements");
		assertEquals(UpgradeRules.Problem.REQUIREMENTS, UpgradeRules.check(guards, Set.of(), 2, 999, NOTHING));
		assertEquals(UpgradeRules.Problem.GOLD, UpgradeRules.check(heart, Set.of(), 1, 29, eightIron));
		assertEquals(UpgradeRules.Problem.ITEMS, UpgradeRules.check(heart, Set.of(), 1, 30, sevenIron));
		assertEquals(UpgradeRules.Problem.NONE, UpgradeRules.check(heart, Set.of(), 1, 30, eightIron));
		assertEquals(UpgradeRules.Problem.NONE, UpgradeRules.check(node("frontieres_elargies"), Set.of(), 1, 30, NOTHING), "no item cost");
	}

	@Test
	void missingItemsListsWhatIsLacking() {
		UpgradeTree.Node heart = node("coeur_renforce");
		assertEquals(Map.of("minecraft:iron_ingot", 8), UpgradeRules.missingItems(heart, NOTHING));
		assertEquals(Map.of("minecraft:iron_ingot", 3), UpgradeRules.missingItems(heart, item -> 5));
		assertTrue(UpgradeRules.missingItems(heart, item -> 64).isEmpty());
		assertEquals(Map.of("minecraft:iron_ingot", 8), UpgradeRules.missingItems(heart, item -> -4), "negative counts are 0");
	}
}
