package dev.guildy.upgrade;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Validated, immutable upgrade tree built from upgrades.json5 (pure: no Minecraft classes, unit-tested).
 * Invalid entries stay in the file (so the admin can fix them) but are left out of the tree; every problem is reported.
 * Requirements always form a DAG: unknown requirements and those closing a cycle are dropped.
 */
public final class UpgradeTree {
	public static final int MAX_TIER = 8;
	/** A full inventory (36 slots of 64). */
	public static final int MAX_COST_COUNT = 64 * 36;
	public static final String DEFAULT_BRANCH_ICON = "minecraft:book";
	public static final String DEFAULT_NODE_ICON = "minecraft:paper";
	public static final UpgradeTree EMPTY = new UpgradeTree(List.of(), new LinkedHashMap<>());
	private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");

	public record Branch(String id, String name, String icon) {
	}

	public record Cost(String item, int count) {
	}

	public record Node(String id, String branch, int tier, String name, List<String> description, String icon, int minLevel,
			List<String> requires, int costGold, List<Cost> costItems, Map<String, Double> effects) {
		public double effect(String key) {
			Double value = effects.get(key);
			return value == null ? 0 : value;
		}
	}

	private final List<Branch> branches;
	private final Map<String, Branch> branchById = new HashMap<>();
	private final Map<String, Node> nodes;
	private final Map<String, List<Node>> byBranch = new HashMap<>();
	private final int maxTier;

	private UpgradeTree(List<Branch> branches, Map<String, Node> nodes) {
		this.branches = List.copyOf(branches);
		this.nodes = Collections.unmodifiableMap(nodes);
		int max = 0;
		for (Branch branch : branches) {
			branchById.put(branch.id(), branch);
		}
		for (Node node : nodes.values()) {
			byBranch.computeIfAbsent(node.branch(), k -> new ArrayList<>()).add(node);
			max = Math.max(max, node.tier());
		}
		for (List<Node> list : byBranch.values()) {
			list.sort(Comparator.comparingInt(Node::tier));
		}
		this.maxTier = max;
	}

	// ------------------------------------------------------------------ queries

	public List<Branch> branches() {
		return branches;
	}

	public @Nullable Branch branch(String id) {
		return branchById.get(id);
	}

	public Collection<Node> nodes() {
		return nodes.values();
	}

	public @Nullable Node node(@Nullable String id) {
		return id == null ? null : nodes.get(id);
	}

	/** Nodes of a branch, by tier. */
	public List<Node> nodesOf(String branch) {
		return Collections.unmodifiableList(byBranch.getOrDefault(branch, List.of()));
	}

	public @Nullable Node nodeAt(String branch, int tier) {
		for (Node node : byBranch.getOrDefault(branch, List.of())) {
			if (node.tier() == tier) {
				return node;
			}
		}
		return null;
	}

	/** Highest tier of any node (0 when empty). */
	public int maxTier() {
		return maxTier;
	}

	/** Sum of an effect over the unlocked nodes that exist in this tree (unknown ids are ignored). */
	public double value(Collection<String> unlocked, String effectKey) {
		double total = 0;
		for (String id : unlocked) {
			Node node = nodes.get(id);
			if (node != null) {
				total += node.effect(effectKey);
			}
		}
		return total;
	}

	/** Unlocked ids that exist in this tree. */
	public int unlockedCount(Collection<String> unlocked) {
		int count = 0;
		for (String id : unlocked) {
			if (nodes.containsKey(id)) {
				count++;
			}
		}
		return count;
	}

	/** The node and everything it needs (transitively), requirements first. Empty for an unknown id. */
	public Set<String> withRequirements(String id) {
		Set<String> out = new LinkedHashSet<>();
		collectRequirements(id, out);
		return out;
	}

	private void collectRequirements(String id, Set<String> out) {
		Node node = nodes.get(id);
		if (node == null || out.contains(id)) {
			return;
		}
		for (String requirement : node.requires()) {
			collectRequirements(requirement, out);
		}
		out.add(id);
	}

	/** The node and every unlocked node depending on it (transitively). Empty for an unknown id. */
	public Set<String> withDependents(String id, Collection<String> unlocked) {
		Set<String> out = new LinkedHashSet<>();
		if (!nodes.containsKey(id)) {
			return out;
		}
		out.add(id);
		boolean changed = true;
		while (changed) {
			changed = false;
			for (Node node : nodes.values()) {
				if (!out.contains(node.id()) && unlocked.contains(node.id()) && node.requires().stream().anyMatch(out::contains)) {
					out.add(node.id());
					changed = true;
				}
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ building / validation

	/**
	 * Validates the config entries (normalized in place: ids, item ids, clamped numbers) and builds the usable tree.
	 *
	 * @param itemExists tells whether a normalized item id ("minecraft:iron_ingot") exists
	 * @param warnings   receives one message per problem
	 */
	public static UpgradeTree build(@Nullable List<UpgradeBranch> branchList, @Nullable List<UpgradeNode> nodeList,
			Predicate<String> itemExists, List<String> warnings) {
		List<Branch> branches = new ArrayList<>();
		Set<String> branchIds = new HashSet<>();
		if (branchList != null) {
			for (UpgradeBranch branch : branchList) {
				if (branch == null) {
					continue;
				}
				branch.id = key(branch.id);
				String where = "branch '" + branch.id + "'";
				if (!ID.matcher(branch.id).matches()) {
					warnings.add(where + " ignored: invalid id (a-z, 0-9, _ ; 32 characters max)");
					continue;
				}
				if (!branchIds.add(branch.id)) {
					warnings.add(where + " ignored: duplicate id");
					continue;
				}
				if (branch.name == null || branch.name.isBlank()) {
					branch.name = branch.id;
				}
				branch.icon = icon(branch.icon, DEFAULT_BRANCH_ICON, itemExists, where, warnings);
				branches.add(new Branch(branch.id, branch.name, branch.icon));
			}
		}

		// Nodes: own fields first, requirements once every usable id is known.
		Map<String, UpgradeNode> usable = new LinkedHashMap<>();
		Map<String, List<Cost>> costs = new HashMap<>();
		Map<String, Map<String, Double>> effects = new HashMap<>();
		Set<String> slots = new HashSet<>();
		if (nodeList != null) {
			for (UpgradeNode node : nodeList) {
				if (node == null) {
					continue;
				}
				node.id = key(node.id);
				node.branch = key(node.branch);
				String where = "node '" + node.id + "'";
				if (!ID.matcher(node.id).matches()) {
					warnings.add(where + " ignored: invalid id (a-z, 0-9, _ ; 32 characters max)");
					continue;
				}
				if (usable.containsKey(node.id)) {
					warnings.add(where + " ignored: duplicate id");
					continue;
				}
				if (!branchIds.contains(node.branch)) {
					warnings.add(where + " ignored: unknown branch '" + node.branch + "'");
					continue;
				}
				if (node.tier < 1 || node.tier > MAX_TIER) {
					warnings.add(where + " ignored: tier " + node.tier + " outside 1.." + MAX_TIER);
					continue;
				}
				if (!slots.add(node.branch + "#" + node.tier)) {
					warnings.add(where + " ignored: branch '" + node.branch + "' already has a tier " + node.tier + " node");
					continue;
				}
				if (node.name == null || node.name.isBlank()) {
					node.name = node.id;
				}
				node.description = node.description == null ? new ArrayList<>() : new ArrayList<>(node.description);
				node.description.removeIf(Objects::isNull);
				node.icon = icon(node.icon, DEFAULT_NODE_ICON, itemExists, where, warnings);
				if (node.minLevel < 1) {
					node.minLevel = 1;
				}
				if (node.costGold < 0) {
					warnings.add(where + ": negative costGold -> 0");
					node.costGold = 0;
				}
				costs.put(node.id, costs(node, itemExists, where, warnings));
				effects.put(node.id, effects(node, where, warnings));
				usable.put(node.id, node);
			}
		}

		Map<String, List<String>> requires = new LinkedHashMap<>();
		for (UpgradeNode node : usable.values()) {
			List<String> resolved = new ArrayList<>();
			List<String> normalized = new ArrayList<>();
			if (node.requires != null) {
				for (String raw : node.requires) {
					if (raw == null || raw.isBlank()) {
						continue;
					}
					String requirement = key(raw);
					normalized.add(requirement);
					if (requirement.equals(node.id)) {
						warnings.add("node '" + node.id + "' cannot require itself (ignored)");
					} else if (!usable.containsKey(requirement)) {
						warnings.add("node '" + node.id + "': unknown requirement '" + requirement + "' (ignored)");
					} else if (!resolved.contains(requirement)) {
						resolved.add(requirement);
					}
				}
			}
			node.requires = normalized;
			requires.put(node.id, resolved);
		}
		breakCycles(requires, warnings);

		Map<String, Node> nodes = new LinkedHashMap<>();
		for (UpgradeNode node : usable.values()) {
			nodes.put(node.id, new Node(node.id, node.branch, node.tier, node.name, List.copyOf(node.description), node.icon,
					node.minLevel, List.copyOf(requires.get(node.id)), node.costGold, List.copyOf(costs.get(node.id)),
					Collections.unmodifiableMap(effects.get(node.id))));
		}
		return new UpgradeTree(branches, nodes);
	}

	private static List<Cost> costs(UpgradeNode node, Predicate<String> itemExists, String where, List<String> warnings) {
		if (node.costItems == null) {
			node.costItems = new ArrayList<>();
		}
		Map<String, Integer> merged = new LinkedHashMap<>();
		for (UpgradeNode.ItemCost cost : node.costItems) {
			if (cost == null) {
				continue;
			}
			cost.item = itemId(cost.item);
			if (cost.item.isEmpty() || !itemExists.test(cost.item)) {
				warnings.add(where + ": unknown cost item '" + cost.item + "' (ignored)");
				continue;
			}
			if (cost.count < 1) {
				warnings.add(where + ": cost item '" + cost.item + "' needs a count >= 1 (ignored)");
				continue;
			}
			if (cost.count > MAX_COST_COUNT) {
				warnings.add(where + ": cost item '" + cost.item + "' count " + cost.count + " -> " + MAX_COST_COUNT);
				cost.count = MAX_COST_COUNT;
			}
			merged.merge(cost.item, cost.count, (a, b) -> Math.min(MAX_COST_COUNT, a + b));
		}
		List<Cost> list = new ArrayList<>();
		merged.forEach((item, count) -> list.add(new Cost(item, count)));
		return list;
	}

	private static Map<String, Double> effects(UpgradeNode node, String where, List<String> warnings) {
		Map<String, Double> result = new LinkedHashMap<>();
		if (node.effects == null) {
			node.effects = new LinkedHashMap<>();
		}
		for (Map.Entry<String, Double> entry : node.effects.entrySet()) {
			String key = key(entry.getKey());
			Double value = entry.getValue();
			if (!UpgradeEffects.isKnown(key)) {
				warnings.add(where + ": unknown effect '" + key + "' (ignored)");
			} else if (value == null || !Double.isFinite(value) || value < 0) {
				warnings.add(where + ": effect '" + key + "' needs a number >= 0 (ignored)");
			} else {
				result.merge(key, value, Double::sum);
			}
		}
		if (result.isEmpty()) {
			warnings.add(where + ": no valid effect");
		}
		return result;
	}

	/** Depth-first search in config order: a requirement pointing back to a node being visited would close a cycle. */
	private static void breakCycles(Map<String, List<String>> requires, List<String> warnings) {
		Map<String, Integer> state = new HashMap<>();
		for (String id : requires.keySet()) {
			visit(id, requires, state, warnings);
		}
	}

	private static void visit(String id, Map<String, List<String>> requires, Map<String, Integer> state, List<String> warnings) {
		if (state.getOrDefault(id, 0) != 0) {
			return;
		}
		state.put(id, 1);
		Iterator<String> it = requires.get(id).iterator();
		while (it.hasNext()) {
			String requirement = it.next();
			int other = state.getOrDefault(requirement, 0);
			if (other == 1) {
				it.remove();
				warnings.add("node '" + id + "': requirement '" + requirement + "' would create a cycle (ignored)");
			} else if (other == 0) {
				visit(requirement, requires, state, warnings);
			}
		}
		state.put(id, 2);
	}

	private static String icon(@Nullable String raw, String fallback, Predicate<String> itemExists, String where, List<String> warnings) {
		String id = itemId(raw);
		if (!id.isEmpty() && itemExists.test(id)) {
			return id;
		}
		if (!id.isEmpty()) {
			warnings.add(where + ": unknown icon '" + id + "', using " + fallback);
		}
		return fallback;
	}

	private static String key(@Nullable String raw) {
		return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
	}

	/** "Iron_Ingot " -> "minecraft:iron_ingot". */
	static String itemId(@Nullable String raw) {
		String id = key(raw);
		return id.isEmpty() || id.contains(":") ? id : "minecraft:" + id;
	}
}
