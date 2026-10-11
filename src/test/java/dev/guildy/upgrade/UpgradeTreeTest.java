package dev.guildy.upgrade;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpgradeTreeTest {
	private static final Predicate<String> ANY_ITEM = id -> true;
	private static final Predicate<String> KNOWN_ITEMS = Set.of("minecraft:iron_ingot", "minecraft:stone", "minecraft:paper",
			"minecraft:book")::contains;

	private static UpgradeTree defaults() {
		List<String> warnings = new ArrayList<>();
		UpgradeTree tree = UpgradeTree.build(UpgradeConfig.defaultBranches(), UpgradeConfig.defaultNodes(), ANY_ITEM, warnings);
		assertTrue(warnings.isEmpty(), warnings.toString());
		return tree;
	}

	private static UpgradeNode node(String id, String branch, int tier, Object... effects) {
		UpgradeNode node = new UpgradeNode();
		node.id = id;
		node.branch = branch;
		node.tier = tier;
		for (int i = 0; i + 1 < effects.length; i += 2) {
			node.effects.put((String) effects[i], ((Number) effects[i + 1]).doubleValue());
		}
		return node;
	}

	private static boolean mentions(List<String> warnings, String text) {
		return warnings.stream().anyMatch(w -> w.contains(text));
	}

	@Test
	void defaultTreeHasThreeBranchesOfFourChainedTiers() {
		UpgradeTree tree = defaults();
		assertEquals(3, tree.branches().size());
		assertEquals(12, tree.nodes().size());
		assertEquals(4, tree.maxTier());
		for (UpgradeTree.Branch branch : tree.branches()) {
			List<UpgradeTree.Node> nodes = tree.nodesOf(branch.id());
			assertEquals(4, nodes.size(), branch.id());
			for (int i = 0; i < nodes.size(); i++) {
				UpgradeTree.Node node = nodes.get(i);
				assertEquals(i + 1, node.tier(), node.id());
				assertFalse(node.effects().isEmpty(), node.id());
				assertTrue(node.costGold() > 0, node.id());
				assertEquals(i == 0 ? List.of() : List.of(nodes.get(i - 1).id()), node.requires(), "each tier requires the previous one: " + node.id());
				assertTrue(i == 0 || node.minLevel() >= nodes.get(i - 1).minLevel(), "levels never go down along a branch: " + node.id());
			}
		}
		assertNotNull(tree.nodeAt("expansion", 1));
		assertNull(tree.nodeAt("expansion", 5));
	}

	@Test
	void valueSumsTheUnlockedNodesStillInTheConfig() {
		UpgradeTree tree = defaults();
		assertEquals(0, tree.value(Set.of(), UpgradeEffects.HEART_INTEGRITY_PCT));
		assertEquals(25, tree.value(Set.of("coeur_renforce"), UpgradeEffects.HEART_INTEGRITY_PCT));
		assertEquals(75, tree.value(Set.of("coeur_renforce", "bastion"), UpgradeEffects.HEART_INTEGRITY_PCT));
		assertEquals(12, tree.value(Set.of("frontieres_elargies", "grand_domaine"), UpgradeEffects.EXTRA_CHUNKS));
		assertEquals(40, tree.value(Set.of("primes_quetes", "age_or", "gone"), UpgradeEffects.QUEST_REWARD_PCT), "unknown ids are ignored");
		assertEquals(0, tree.value(Set.of("coeur_renforce"), UpgradeEffects.EXTRA_CHUNKS));
		assertEquals(2, tree.unlockedCount(Set.of("coeur_renforce", "bastion", "gone")));

		List<UpgradeNode> nodes = UpgradeConfig.defaultNodes();
		nodes.removeIf(node -> node.id.equals("bastion"));
		UpgradeTree reduced = UpgradeTree.build(UpgradeConfig.defaultBranches(), nodes, ANY_ITEM, new ArrayList<>());
		assertEquals(25, reduced.value(Set.of("coeur_renforce", "bastion"), UpgradeEffects.HEART_INTEGRITY_PCT), "a removed node stops counting");
	}

	@Test
	void branchesAreValidated() {
		List<UpgradeBranch> branches = new ArrayList<>(List.of(
				new UpgradeBranch(" Main ", "", "minecraft:nope"),
				new UpgradeBranch("bad id!", "Bad", "minecraft:book"),
				new UpgradeBranch("main", "Duplicate", "minecraft:book")));
		List<String> warnings = new ArrayList<>();
		UpgradeTree tree = UpgradeTree.build(branches, List.of(), KNOWN_ITEMS, warnings);
		assertEquals(1, tree.branches().size());
		UpgradeTree.Branch main = tree.branches().getFirst();
		assertEquals("main", main.id(), "ids are trimmed and lower-cased");
		assertEquals("main", main.name(), "blank name falls back to the id");
		assertEquals(UpgradeTree.DEFAULT_BRANCH_ICON, main.icon(), "unknown icon replaced");
		assertTrue(mentions(warnings, "unknown icon"));
		assertTrue(mentions(warnings, "invalid id"));
		assertTrue(mentions(warnings, "duplicate id"));
	}

	@Test
	void nodeFieldsAreCleanedAndBadNodesSkipped() {
		List<UpgradeBranch> branches = List.of(new UpgradeBranch("main", "Main", "minecraft:book"));
		UpgradeNode a = node("a", "main", 1, "extra_chunks", 2, "teleportation", 5, "extra_villagers", -1);
		a.costGold = -5;
		a.minLevel = 0;
		a.icon = "stone";
		a.costItems.add(new UpgradeNode.ItemCost("Iron_Ingot", 3));
		a.costItems.add(new UpgradeNode.ItemCost("minecraft:iron_ingot", 2));
		a.costItems.add(new UpgradeNode.ItemCost("minecraft:unobtainium", 1));
		a.costItems.add(new UpgradeNode.ItemCost("minecraft:paper", 0));
		List<UpgradeNode> nodes = new ArrayList<>(List.of(a,
				node("a", "main", 2, "extra_chunks", 1),
				node("orphan", "nowhere", 2, "extra_chunks", 1),
				node("zero", "main", 0, "extra_chunks", 1),
				node("nine", "main", UpgradeTree.MAX_TIER + 1, "extra_chunks", 1),
				node("same_tier", "main", 1, "extra_chunks", 1),
				node("no_effect", "main", 3)));
		List<String> warnings = new ArrayList<>();
		UpgradeTree tree = UpgradeTree.build(branches, nodes, KNOWN_ITEMS, warnings);

		assertEquals(Set.of("a", "no_effect"), Set.copyOf(tree.nodes().stream().map(UpgradeTree.Node::id).toList()));
		UpgradeTree.Node node = tree.node("a");
		assertNotNull(node);
		assertEquals(0, node.costGold(), "negative gold -> 0");
		assertEquals(1, node.minLevel());
		assertEquals("minecraft:stone", node.icon(), "item ids are normalized");
		assertEquals(List.of(new UpgradeTree.Cost("minecraft:iron_ingot", 5)), node.costItems(), "same item merged, unknown / empty ignored");
		assertEquals(1, node.effects().size(), "unknown and negative effects ignored");
		assertEquals(2, node.effect(UpgradeEffects.EXTRA_CHUNKS));
		assertTrue(tree.node("no_effect").effects().isEmpty());
		for (String expected : List.of("negative costGold", "unknown cost item", "count >= 1", "unknown effect", "number >= 0",
				"duplicate id", "unknown branch", "outside 1..", "already has a tier 1", "no valid effect")) {
			assertTrue(mentions(warnings, expected), "warning '" + expected + "' in " + warnings);
		}
	}

	@Test
	void unknownAndSelfRequirementsAreDropped() {
		List<UpgradeBranch> branches = List.of(new UpgradeBranch("main", "Main", "minecraft:book"));
		UpgradeNode a = node("a", "main", 1, "extra_chunks", 1);
		UpgradeNode b = node("b", "main", 2, "extra_chunks", 1);
		b.requires = new ArrayList<>(List.of(" A ", "ghost", "b", "a"));
		List<String> warnings = new ArrayList<>();
		UpgradeTree tree = UpgradeTree.build(branches, List.of(a, b), ANY_ITEM, warnings);
		assertEquals(List.of("a"), tree.node("b").requires());
		assertTrue(mentions(warnings, "unknown requirement 'ghost'"));
		assertTrue(mentions(warnings, "cannot require itself"));
	}

	@Test
	void cyclesAreBroken() {
		List<UpgradeBranch> branches = List.of(new UpgradeBranch("main", "Main", "minecraft:book"));
		UpgradeNode x = node("x", "main", 1, "extra_chunks", 1);
		UpgradeNode y = node("y", "main", 2, "extra_chunks", 1);
		UpgradeNode z = node("z", "main", 3, "extra_chunks", 1);
		x.requires = new ArrayList<>(List.of("z"));
		y.requires = new ArrayList<>(List.of("x"));
		z.requires = new ArrayList<>(List.of("y"));
		List<String> warnings = new ArrayList<>();
		UpgradeTree tree = UpgradeTree.build(branches, List.of(x, y, z), ANY_ITEM, warnings);
		assertTrue(mentions(warnings, "cycle"), warnings.toString());
		assertEquals(1, warnings.size(), "a single edge is enough to break the cycle");
		assertEquals(List.of("y", "z", "x"), List.copyOf(tree.withRequirements("x")), "closure terminates, requirements first");
		// The tree is acyclic: no node is (transitively) its own requirement.
		for (UpgradeTree.Node node : tree.nodes()) {
			for (String requirement : node.requires()) {
				assertFalse(tree.withRequirements(requirement).contains(node.id()), node.id());
			}
		}
	}

	@Test
	void requirementAndDependentClosures() {
		UpgradeTree tree = defaults();
		assertEquals(List.of("coeur_renforce", "gardes_aguerris", "banniere_ralliement", "bastion"), List.copyOf(tree.withRequirements("bastion")));
		assertEquals(List.of("frontieres_elargies"), List.copyOf(tree.withRequirements("frontieres_elargies")));
		assertTrue(tree.withRequirements("ghost").isEmpty());

		Set<String> unlocked = Set.of("coeur_renforce", "gardes_aguerris", "banniere_ralliement", "primes_quetes");
		assertEquals(Set.of("coeur_renforce", "gardes_aguerris", "banniere_ralliement"), tree.withDependents("coeur_renforce", unlocked),
				"only unlocked dependents, never other branches");
		assertEquals(Set.of("banniere_ralliement"), tree.withDependents("banniere_ralliement", unlocked));
		assertTrue(tree.withDependents("ghost", unlocked).isEmpty());
	}
}
