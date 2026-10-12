package dev.guildy.villager;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Expedition destinations (pure logic, unit tested): the default destination of each kind is the
 * {@code expeditions.exploration} / {@code expeditions.nether} section of villagers.json5 ("Forêt et plaines" /
 * "Terres du Nether": the historical tables, unchanged), the others come from {@code expeditions.destinations}
 * ({@link ExpeditionDestination}). Lookup with fallback to the default of the kind, guild level locks, durations, wishlist
 * weights, unique weapon attempts, validation and the default list (added once to files written before it).
 */
public final class ExpeditionDestinations {
	public static final String EXPLORATION = "exploration";
	public static final String NETHER = "nether";
	/** Id of the default exploration destination ({@code expeditions.exploration}). */
	public static final String PLAINS = "plains";
	/** Id of the default Nether destination ({@code expeditions.nether}). */
	public static final String NETHER_WASTES = "nether_wastes";
	/** villagers.json5 version that introduced the destinations: older files receive the default list once. */
	public static final int CONFIG_VERSION = 6;
	public static final int MAX_SECONDS = 86_400;
	public static final double MAX_UNIQUE_MULTIPLIER = 20;
	public static final double MAX_WISHLIST_MULTIPLIER = 20;
	/** A loot entry is "rare" when one roll picks it at most this often. */
	public static final double RARE_SHARE = 0.05;
	private static final int MAX_WEIGHT = 1_000_000;
	private static final int MAX_ID_LENGTH = 48;
	private static final String DEFAULT_ICON_EXPLORATION = "minecraft:oak_sapling";
	private static final String DEFAULT_ICON_NETHER = "minecraft:netherrack";
	private static final Logger LOGGER = LoggerFactory.getLogger("Guildy");

	private ExpeditionDestinations() {
	}

	/**
	 * A resolved destination. {@code name} is empty for the default destinations (their name is a lang text,
	 * "villager.destination.&lt;id&gt;"); {@code durationSeconds} is resolved (multipliers applied), before upgrades.
	 */
	public record Destination(String id, String name, String icon, boolean nether, int minLevel, int durationSeconds,
			double lossChance, int rolls, double treasureMapChance, double uniqueChanceMultiplier, List<VillagerConfig.LootEntry> loot) {

		public boolean isDefault() {
			return id.equals(defaultId(nether));
		}

		public boolean unlocked(int guildLevel) {
			return guildLevel >= minLevel;
		}
	}

	// ================================================================ lookup

	public static String defaultId(boolean nether) {
		return nether ? NETHER_WASTES : PLAINS;
	}

	public static boolean isReserved(String id) {
		return PLAINS.equals(id) || NETHER_WASTES.equals(id);
	}

	/** The default destination of a kind: its section of villagers.json5. */
	public static Destination defaultDestination(VillagerConfig.Expeditions expeditions, boolean nether) {
		VillagerConfig.Expedition base = base(expeditions, nether);
		return new Destination(defaultId(nether), "", nether ? DEFAULT_ICON_NETHER : DEFAULT_ICON_EXPLORATION, nether, 1,
				Math.max(1, base.durationSeconds), base.lossChance, base.rolls, base.treasureMapChance, 1.0,
				base.loot == null ? List.of() : base.loot);
	}

	/** Every destination of a kind, the default one first, then the list in config order. */
	public static List<Destination> list(VillagerConfig.Expeditions expeditions, boolean nether) {
		List<Destination> list = new ArrayList<>();
		list.add(defaultDestination(expeditions, nether));
		if (expeditions.destinations != null) {
			for (ExpeditionDestination destination : expeditions.destinations) {
				if (destination != null && destination.isNether() == nether && !isReserved(destination.id)) {
					list.add(resolve(expeditions, destination));
				}
			}
		}
		return list;
	}

	/** The destination of this id and kind; the default destination of the kind for an unknown / removed / blank id. */
	public static Destination find(VillagerConfig.Expeditions expeditions, boolean nether, @Nullable String id) {
		String key = id == null ? "" : normalizeId(id);
		if (!key.isEmpty() && !isReserved(key) && expeditions.destinations != null) {
			for (ExpeditionDestination destination : expeditions.destinations) {
				if (destination != null && key.equals(destination.id) && destination.isNether() == nether) {
					return resolve(expeditions, destination);
				}
			}
		}
		return defaultDestination(expeditions, nether);
	}

	/** Where a villager really goes: its choice ({@link #find}), or the default destination while it is locked. */
	public static Destination chosen(VillagerConfig.Expeditions expeditions, boolean nether, @Nullable String id, int guildLevel) {
		Destination destination = find(expeditions, nether, id);
		return destination.unlocked(guildLevel) ? destination : defaultDestination(expeditions, nether);
	}

	/** Trip duration of a listed destination: its own seconds, else multiplier x the default destination's duration. */
	public static int durationSeconds(ExpeditionDestination destination, int baseSeconds) {
		if (destination.durationSeconds > 0) {
			return Math.min(MAX_SECONDS, destination.durationSeconds);
		}
		double multiplier = destination.durationMultiplier > 0 ? destination.durationMultiplier : 1.0;
		return (int) Math.max(1, Math.min(MAX_SECONDS, Math.round(Math.max(1, baseSeconds) * multiplier)));
	}

	private static Destination resolve(VillagerConfig.Expeditions expeditions, ExpeditionDestination destination) {
		boolean nether = destination.isNether();
		int seconds = durationSeconds(destination, base(expeditions, nether).durationSeconds);
		return new Destination(destination.id, destination.name == null ? "" : destination.name,
				destination.icon == null ? "" : destination.icon, nether, Math.max(1, destination.minLevel), seconds,
				destination.lossChance, destination.rolls, destination.treasureMapChance, destination.uniqueChanceMultiplier,
				destination.loot == null ? List.of() : destination.loot);
	}

	private static VillagerConfig.Expedition base(VillagerConfig.Expeditions expeditions, boolean nether) {
		VillagerConfig.Expedition base = nether ? expeditions.nether : expeditions.exploration;
		if (base != null) {
			return base;
		}
		return nether ? VillagerConfig.Expedition.nether() : VillagerConfig.Expedition.exploration();
	}

	// ================================================================ loot

	/**
	 * Wishlist bonus: entries whose item is wanted get their weight multiplied by {@code factor} (the list is never
	 * modified: changed entries are copies). Returns {@code loot} itself when nothing changes.
	 */
	public static List<VillagerConfig.LootEntry> boosted(List<VillagerConfig.LootEntry> loot, @Nullable Collection<String> wanted, double factor) {
		if (loot == null) {
			return List.of();
		}
		if (wanted == null || wanted.isEmpty() || !(factor > 1)) {
			return loot;
		}
		Set<String> keys = normalizedItems(wanted);
		List<VillagerConfig.LootEntry> result = new ArrayList<>(loot.size());
		for (VillagerConfig.LootEntry entry : loot) {
			if (entry != null && entry.item != null && entry.weight > 0 && keys.contains(normalizeItem(entry.item))) {
				int weight = (int) Math.min(MAX_WEIGHT, Math.max(entry.weight, Math.round(entry.weight * Math.min(factor, MAX_WISHLIST_MULTIPLIER))));
				result.add(new VillagerConfig.LootEntry(entry.item, entry.min, entry.max, weight));
			} else {
				result.add(entry);
			}
		}
		return result;
	}

	/** Wanted items (wishlist order, normalized ids) that this loot table can give. */
	public static List<String> favoured(List<VillagerConfig.LootEntry> loot, @Nullable Collection<String> wanted) {
		List<String> result = new ArrayList<>();
		if (loot == null || wanted == null || wanted.isEmpty()) {
			return result;
		}
		Set<String> items = new HashSet<>();
		for (VillagerConfig.LootEntry entry : loot) {
			if (entry != null && entry.item != null && entry.weight > 0) {
				items.add(normalizeItem(entry.item));
			}
		}
		for (String id : normalizedItems(wanted)) {
			if (items.contains(id)) {
				result.add(id);
			}
		}
		return result;
	}

	/** The most common entries (highest weights first, one per item), at most {@code max}. */
	public static List<VillagerConfig.LootEntry> typical(List<VillagerConfig.LootEntry> loot, int max) {
		List<VillagerConfig.LootEntry> sorted = usable(loot);
		sorted.sort(Comparator.comparingInt((VillagerConfig.LootEntry entry) -> entry.weight).reversed());
		return distinct(sorted, max, Set.of());
	}

	/**
	 * Rare finds: entries picked at most {@link #RARE_SHARE} of the rolls (rarest first, one per item), never one of
	 * {@code shown}, at most {@code max}.
	 */
	public static List<VillagerConfig.LootEntry> rare(List<VillagerConfig.LootEntry> loot, int max, Collection<VillagerConfig.LootEntry> shown) {
		List<VillagerConfig.LootEntry> entries = usable(loot);
		long total = 0;
		for (VillagerConfig.LootEntry entry : entries) {
			total += entry.weight;
		}
		long totalWeight = total;
		entries.removeIf(entry -> entry.weight > totalWeight * RARE_SHARE);
		entries.sort(Comparator.comparingInt(entry -> entry.weight));
		Set<String> skip = new HashSet<>();
		for (VillagerConfig.LootEntry entry : shown) {
			skip.add(normalizeItem(entry.item));
		}
		return distinct(entries, max, skip);
	}

	private static List<VillagerConfig.LootEntry> usable(List<VillagerConfig.LootEntry> loot) {
		List<VillagerConfig.LootEntry> entries = new ArrayList<>();
		if (loot != null) {
			for (VillagerConfig.LootEntry entry : loot) {
				if (entry != null && entry.item != null && !entry.item.isBlank() && entry.weight > 0) {
					entries.add(entry);
				}
			}
		}
		return entries;
	}

	private static List<VillagerConfig.LootEntry> distinct(List<VillagerConfig.LootEntry> entries, int max, Set<String> skip) {
		List<VillagerConfig.LootEntry> result = new ArrayList<>();
		Set<String> seen = new HashSet<>(skip);
		for (VillagerConfig.LootEntry entry : entries) {
			if (result.size() >= max) {
				break;
			}
			if (seen.add(normalizeItem(entry.item))) {
				result.add(entry);
			}
		}
		return result;
	}

	/**
	 * Number of "expedition" unique weapon rolls for a chance multiplier: floor(m), plus one more with probability
	 * frac(m) (m = 2.5 → 2 or 3 rolls, 0.5 → 0 or 1). One unique at most per trip: the rolls stop at the first success,
	 * so the chance is about m x the table chance while it stays small.
	 */
	public static int uniqueAttempts(double multiplier, RandomGenerator random) {
		if (!(multiplier > 0)) {
			return 0;
		}
		double m = Math.min(multiplier, MAX_UNIQUE_MULTIPLIER);
		int attempts = (int) Math.floor(m);
		double fraction = m - attempts;
		if (fraction > 1e-9 && random.nextDouble() < fraction) {
			attempts++;
		}
		return attempts;
	}

	/** Risk level of a loss chance: "low", "medium", "high" or "very_high" (lang suffix). */
	public static String risk(double lossChance) {
		if (lossChance <= 0.06) {
			return "low";
		}
		if (lossChance <= 0.12) {
			return "medium";
		}
		if (lossChance <= 0.2) {
			return "high";
		}
		return "very_high";
	}

	// ================================================================ validation

	/**
	 * Validates the destination settings of villagers.json5 and logs the problems fixed. {@code migrate}: the file was
	 * written before the destinations ({@code configVersion} &lt; {@link #CONFIG_VERSION}).
	 */
	static void validateAndLog(VillagerConfig.Expeditions expeditions, boolean migrate) {
		for (String warning : validate(expeditions, migrate)) {
			LOGGER.warn("[villagers.json5] {}", warning);
		}
	}

	/**
	 * Cleans {@code expeditions.destinations} (ids, kinds, bounds, loot) and bounds the wishlist multiplier. With
	 * {@code migrate}, the default destinations missing from the list are added (files written before the destinations:
	 * once, the version is then raised); entries already there, customised loot tables included, are never touched.
	 *
	 * @return the problems found (fixed or ignored entries)
	 */
	public static List<String> validate(VillagerConfig.Expeditions expeditions, boolean migrate) {
		List<String> warnings = new ArrayList<>();
		expeditions.wishlistWeightMultiplier = clamp(expeditions.wishlistWeightMultiplier, 1.0, MAX_WISHLIST_MULTIPLIER);
		if (expeditions.destinations == null) {
			expeditions.destinations = defaults();
			return warnings;
		}
		List<ExpeditionDestination> cleaned = new ArrayList<>();
		Set<String> ids = new HashSet<>(Set.of(PLAINS, NETHER_WASTES));
		for (ExpeditionDestination destination : expeditions.destinations) {
			if (destination == null) {
				continue;
			}
			String id = normalizeId(destination.id == null || destination.id.isBlank() ? destination.name : destination.id);
			if (id.isEmpty()) {
				warnings.add("expedition destination without id (ignored)");
				continue;
			}
			if (!ids.add(id)) {
				warnings.add("expedition destination '" + id + "': id already used" + (isReserved(id) ? " by a default destination" : "") + " (ignored)");
				continue;
			}
			destination.id = id;
			clean(destination, warnings);
			cleaned.add(destination);
		}
		if (migrate) {
			mergeDefaults(cleaned);
		}
		expeditions.destinations = cleaned;
		return warnings;
	}

	private static void clean(ExpeditionDestination destination, List<String> warnings) {
		String id = destination.id;
		destination.kind = normalizeKind(destination.kind, id, warnings);
		destination.name = destination.name == null || destination.name.isBlank() ? id : destination.name.trim();
		destination.icon = destination.icon == null || destination.icon.isBlank()
				? (destination.isNether() ? DEFAULT_ICON_NETHER : "minecraft:compass")
				: normalizeItem(destination.icon);
		destination.minLevel = clamp(destination.minLevel, 1, 1000);
		destination.durationSeconds = clamp(destination.durationSeconds, 0, MAX_SECONDS);
		destination.durationMultiplier = clamp(destination.durationMultiplier, 0.05, 20.0);
		destination.lossChance = clamp(destination.lossChance, 0.0, 1.0);
		destination.rolls = clamp(destination.rolls, 0, 64);
		destination.treasureMapChance = clamp(destination.treasureMapChance, 0.0, 1.0);
		destination.uniqueChanceMultiplier = clamp(destination.uniqueChanceMultiplier, 0.0, MAX_UNIQUE_MULTIPLIER);
		destination.loot = cleanLoot(destination.loot);
		if (destination.loot.isEmpty() && destination.rolls > 0) {
			warnings.add("expedition destination '" + id + "': empty loot table");
		}
	}

	/** Copy without unusable entries; namespaced item ids, 1 <= min <= max <= 64. */
	static List<VillagerConfig.LootEntry> cleanLoot(@Nullable List<VillagerConfig.LootEntry> loot) {
		List<VillagerConfig.LootEntry> cleaned = new ArrayList<>();
		if (loot == null) {
			return cleaned;
		}
		for (VillagerConfig.LootEntry entry : loot) {
			if (entry == null || entry.item == null || entry.item.isBlank() || entry.weight <= 0) {
				continue;
			}
			entry.item = normalizeItem(entry.item);
			entry.min = clamp(entry.min, 1, 64);
			entry.max = clamp(entry.max, entry.min, 64);
			cleaned.add(entry);
		}
		return cleaned;
	}

	/**
	 * Appends the default destinations whose id is not in {@code list} yet (never replaces an entry).
	 *
	 * @return number of destinations added
	 */
	static int mergeDefaults(List<ExpeditionDestination> list) {
		Set<String> ids = new HashSet<>();
		for (ExpeditionDestination destination : list) {
			if (destination != null && destination.id != null) {
				ids.add(normalizeId(destination.id));
			}
		}
		int added = 0;
		for (ExpeditionDestination destination : defaults()) {
			if (ids.add(destination.id)) {
				list.add(destination);
				added++;
			}
		}
		return added;
	}

	private static String normalizeKind(@Nullable String kind, String id, List<String> warnings) {
		String value = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
		switch (value) {
			case NETHER:
				return NETHER;
			case EXPLORATION, "explorer", "overworld":
				return EXPLORATION;
			default:
				warnings.add("expedition destination '" + id + "': unknown kind '" + kind + "' (exploration used)");
				return EXPLORATION;
		}
	}

	/** "Temple du désert" → "temple_du_desert": lowercase ASCII letters, digits and single '_'. */
	public static String normalizeId(@Nullable String value) {
		if (value == null) {
			return "";
		}
		String ascii = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		StringBuilder sb = new StringBuilder();
		for (char c : ascii.toCharArray()) {
			boolean valid = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
			if (valid) {
				sb.append(c);
			} else if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '_') {
				sb.append('_');
			}
		}
		while (!sb.isEmpty() && sb.charAt(sb.length() - 1) == '_') {
			sb.setLength(sb.length() - 1);
		}
		return sb.length() > MAX_ID_LENGTH ? sb.substring(0, MAX_ID_LENGTH) : sb.toString();
	}

	/** "Sand" → "minecraft:sand" (lowercase, default namespace). */
	public static String normalizeItem(@Nullable String id) {
		if (id == null) {
			return "";
		}
		String value = id.trim().toLowerCase(Locale.ROOT);
		return value.isEmpty() || value.contains(":") ? value : "minecraft:" + value;
	}

	private static Set<String> normalizedItems(Collection<String> ids) {
		Set<String> keys = new LinkedHashSet<>();
		for (String id : ids) {
			if (id != null && !id.isBlank()) {
				keys.add(normalizeItem(id));
			}
		}
		return keys;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp(double value, double min, double max) {
		if (Double.isNaN(value)) {
			return min;
		}
		return Math.max(min, Math.min(max, value));
	}

	// ================================================================ defaults

	/**
	 * Default destinations (besides "Forêt et plaines" and "Terres du Nether"). Durations are multipliers of the default
	 * destination of the kind (8 min exploration, 12 min Nether). Structures are riskier, longer and need a guild level;
	 * gold stays modest (never a cheap source of gold compared with the shop: see ExpeditionEconomyTest).
	 */
	public static List<ExpeditionDestination> defaults() {
		List<ExpeditionDestination> list = new ArrayList<>();
		// ---- exploration: biomes
		list.add(exploration("desert", "Désert", "cactus").level(1).duration(1.0).risk(0.06).rolls(6).map(0.06).unique(1.0)
				.loot("sand", 8, 24, 10).loot("sandstone", 4, 12, 8).loot("cactus", 1, 4, 7).loot("bone", 1, 4, 6)
				.loot("dead_bush", 1, 3, 5).loot("rabbit_hide", 1, 3, 5).loot("red_sand", 4, 12, 3).loot("gold_nugget", 1, 4, 3)
				.loot("rabbit_foot", 1, 1, 1).build());
		list.add(exploration("jungle", "Jungle", "jungle_sapling").level(1).duration(1.1).risk(0.07).rolls(6).map(0.05).unique(1.0)
				.loot("bamboo", 4, 12, 9).loot("cocoa_beans", 2, 6, 7).loot("melon_slice", 3, 8, 7).loot("jungle_sapling", 1, 4, 6)
				.loot("vine", 2, 8, 6).loot("jungle_log", 4, 10, 5).loot("feather", 1, 3, 2).build());
		list.add(exploration("taiga", "Taïga et neige", "spruce_sapling").level(1).duration(1.1).risk(0.06).rolls(6).map(0.05).unique(1.0)
				.loot("spruce_sapling", 1, 4, 7).loot("sweet_berries", 3, 8, 7).loot("snowball", 4, 16, 6).loot("ice", 2, 6, 5)
				.loot("rabbit", 1, 3, 5).loot("spruce_log", 4, 10, 5).loot("packed_ice", 1, 4, 3).loot("rabbit_hide", 1, 3, 3)
				.loot("fern", 1, 3, 2).build());
		list.add(exploration("ocean", "Océan et plage", "kelp").level(1).duration(1.1).risk(0.07).rolls(6).map(0.10).unique(1.0)
				.loot("sand", 6, 16, 8).loot("kelp", 4, 12, 8).loot("cod", 2, 5, 7).loot("sea_pickle", 1, 4, 5)
				.loot("salmon", 1, 4, 5).loot("prismarine_shard", 1, 4, 4).loot("ink_sac", 1, 3, 4).loot("nautilus_shell", 1, 1, 1).build());
		list.add(exploration("swamp", "Marais et mangrove", "lily_pad").level(1).duration(1.1).risk(0.07).rolls(6).map(0.04).unique(1.0)
				.loot("clay_ball", 4, 12, 8).loot("mud", 4, 12, 7).loot("mangrove_propagule", 1, 4, 6).loot("lily_pad", 1, 4, 6)
				.loot("mangrove_log", 4, 10, 5).loot("slime_ball", 1, 3, 4).loot("vine", 2, 6, 3).loot("blue_orchid", 1, 3, 3).build());
		list.add(exploration("mountains", "Montagnes", "goat_horn").level(2).duration(1.2).risk(0.08).rolls(6).map(0.04).unique(1.0)
				.loot("stone", 8, 24, 10).loot("coal", 2, 6, 8).loot("raw_iron", 1, 4, 7).loot("raw_copper", 2, 6, 5)
				.loot("calcite", 2, 6, 3).loot("snowball", 2, 8, 3).loot("emerald", 1, 2, 2).loot("goat_horn", 1, 1, 1).build());
		list.add(exploration("cherry_grove", "Cerisiers", "cherry_sapling").level(2).duration(1.1).risk(0.05).rolls(6).map(0.04).unique(1.0)
				.loot("pink_petals", 2, 8, 8).loot("cherry_sapling", 1, 3, 7).loot("cherry_log", 4, 10, 6).loot("honeycomb", 1, 3, 4)
				.loot("pink_tulip", 1, 3, 3).loot("honey_bottle", 1, 2, 2).build());
		// ---- exploration: structures (riskier, longer, better loot)
		list.add(exploration("village", "Village", "bell").level(2).duration(1.25).risk(0.06).rolls(6).map(0.06).unique(1.0)
				.loot("bread", 2, 5, 8).loot("wheat", 4, 12, 7).loot("carrot", 2, 8, 6).loot("potato", 2, 8, 6)
				.loot("iron_ingot", 1, 3, 4).loot("emerald", 1, 3, 3).loot("apple", 1, 4, 3).loot("book", 1, 2, 2)
				.loot("saddle", 1, 1, 1).build());
		list.add(exploration("mineshaft", "Puits de mine abandonné", "rail").level(3).duration(1.25).risk(0.10).rolls(6).map(0.04).unique(1.25)
				.loot("coal", 3, 8, 9).loot("rail", 2, 8, 8).loot("raw_iron", 1, 4, 7).loot("glow_berries", 2, 6, 6)
				.loot("string", 2, 6, 5).loot("oak_planks", 4, 12, 5).loot("redstone", 2, 6, 4).loot("lapis_lazuli", 2, 6, 3)
				.loot("torch", 2, 6, 3).loot("name_tag", 1, 1, 1).build());
		list.add(exploration("shipwreck", "Épave et ruines", "map").level(3).duration(1.25).risk(0.08).rolls(6).map(0.25).unique(1.25)
				.loot("paper", 2, 8, 9).loot("coal", 2, 6, 7).loot("iron_nugget", 2, 8, 6).loot("kelp", 3, 8, 5)
				.loot("emerald", 1, 3, 4).loot("gold_nugget", 2, 6, 4).loot("wheat", 2, 6, 4).loot("carrot", 2, 6, 3)
				.loot("prismarine_shard", 1, 3, 3).build());
		list.add(exploration("desert_temple", "Temple du désert", "chiseled_sandstone").level(4).duration(1.5).risk(0.12).rolls(6).map(0.05).unique(1.5)
				.loot("bone", 2, 6, 10).loot("rotten_flesh", 2, 7, 10).loot("sand", 4, 12, 6).loot("gunpowder", 1, 4, 5)
				.loot("emerald", 1, 3, 4).loot("gold_ingot", 1, 3, 3).loot("tnt", 1, 2, 3).loot("spider_eye", 1, 3, 3)
				.loot("enchanted_book", 1, 1, 2).loot("diamond", 1, 1, 1).build());
		list.add(exploration("jungle_temple", "Temple de la jungle", "mossy_cobblestone").level(4).duration(1.5).risk(0.12).rolls(6).map(0.05).unique(1.5)
				.loot("bamboo", 4, 12, 8).loot("arrow", 4, 12, 8).loot("bone", 2, 6, 6).loot("rotten_flesh", 2, 6, 6)
				.loot("mossy_cobblestone", 4, 10, 5).loot("emerald", 1, 3, 4).loot("iron_ingot", 1, 3, 3).loot("gold_ingot", 1, 2, 2)
				.loot("enchanted_book", 1, 1, 1).loot("diamond", 1, 1, 1).build());
		list.add(exploration("pillager_outpost", "Avant-poste de pillards", "crossbow").level(5).duration(1.5).risk(0.18).rolls(7).map(0.04).unique(2.0)
				.loot("arrow", 6, 16, 10).loot("dark_oak_log", 4, 12, 8).loot("string", 2, 6, 6).loot("emerald", 1, 3, 5)
				.loot("wheat", 3, 8, 5).loot("iron_ingot", 1, 3, 3).loot("crossbow", 1, 1, 2).loot("experience_bottle", 1, 3, 2)
				.loot("goat_horn", 1, 1, 1).build());
		list.add(exploration("ocean_monument", "Monument océanique", "prismarine_bricks").level(6).duration(1.75).risk(0.20).rolls(7).map(0.0).unique(2.0)
				.loot("prismarine_shard", 4, 12, 10).loot("prismarine_crystals", 3, 10, 9).loot("prismarine", 4, 10, 6).loot("cod", 2, 6, 5)
				.loot("sea_lantern", 1, 3, 4).loot("dark_prismarine", 2, 6, 3).loot("gold_ingot", 1, 2, 2).loot("sponge", 1, 1, 2).build());
		list.add(exploration("ancient_city", "Ville ancienne", "sculk_shrieker").level(7).duration(2.0).risk(0.25).rolls(7).map(0.0).unique(3.0)
				.loot("sculk", 4, 12, 10).loot("candle", 1, 4, 7).loot("bone", 2, 6, 6).loot("amethyst_shard", 2, 6, 5)
				.loot("coal", 2, 6, 5).loot("soul_torch", 2, 6, 4).loot("experience_bottle", 1, 3, 3).loot("sculk_sensor", 1, 1, 2)
				.loot("enchanted_book", 1, 1, 2).loot("echo_shard", 1, 2, 2).loot("disc_fragment_5", 1, 2, 2).build());
		// ---- Nether (needs a nether portal in the territory)
		list.add(nether("crimson_forest", "Forêt carmin", "crimson_fungus").level(2).duration(1.0).risk(0.12).rolls(7).unique(1.0)
				.loot("crimson_stem", 4, 12, 10).loot("crimson_fungus", 1, 4, 8).loot("nether_wart_block", 2, 6, 7).loot("weeping_vines", 2, 6, 6)
				.loot("shroomlight", 1, 3, 5).loot("crimson_roots", 1, 4, 4).loot("porkchop", 1, 3, 4).loot("leather", 1, 3, 3)
				.loot("gold_nugget", 2, 6, 2).build());
		list.add(nether("warped_forest", "Forêt biscornue", "warped_fungus").level(2).duration(1.0).risk(0.10).rolls(7).unique(1.0)
				.loot("warped_stem", 4, 12, 10).loot("warped_fungus", 1, 4, 8).loot("warped_wart_block", 2, 6, 7).loot("twisting_vines", 2, 6, 6)
				.loot("shroomlight", 1, 3, 4).loot("warped_roots", 1, 4, 4).loot("nether_sprouts", 2, 6, 3).loot("ender_pearl", 1, 2, 2).build());
		list.add(nether("soul_sand_valley", "Vallée des âmes", "soul_sand").level(3).duration(1.1).risk(0.15).rolls(7).unique(1.25)
				.loot("soul_sand", 6, 16, 10).loot("bone", 2, 8, 9).loot("soul_soil", 6, 16, 8).loot("arrow", 2, 8, 5)
				.loot("bone_block", 1, 3, 3).loot("basalt", 2, 6, 3).loot("gunpowder", 1, 3, 3).loot("ghast_tear", 1, 1, 2).build());
		list.add(nether("basalt_deltas", "Deltas de basalte", "basalt").level(3).duration(1.1).risk(0.15).rolls(7).unique(1.25)
				.loot("basalt", 6, 16, 10).loot("blackstone", 6, 16, 9).loot("magma_cream", 1, 4, 7).loot("magma_block", 2, 6, 5)
				.loot("quartz", 2, 6, 4).build());
		list.add(nether("nether_fortress", "Forteresse du Nether", "nether_bricks").level(4).duration(1.25).risk(0.16).rolls(7).unique(1.5)
				.loot("nether_wart", 2, 6, 16).loot("nether_bricks", 4, 12, 16).loot("blaze_rod", 1, 3, 14).loot("coal", 2, 6, 12)
				.loot("bone", 2, 6, 12).loot("magma_cream", 1, 3, 10).loot("gold_nugget", 2, 6, 8).loot("iron_ingot", 1, 3, 6)
				.loot("obsidian", 1, 2, 4).loot("wither_skeleton_skull", 1, 1, 1).build());
		list.add(nether("bastion", "Bastion", "gilded_blackstone").level(6).duration(1.5).risk(0.22).rolls(7).unique(2.5)
				.loot("gold_nugget", 4, 12, 16).loot("blackstone", 6, 16, 16).loot("polished_blackstone_bricks", 4, 10, 10)
				.loot("magma_cream", 1, 3, 8).loot("arrow", 4, 12, 8).loot("porkchop", 2, 5, 8).loot("gilded_blackstone", 1, 2, 6)
				.loot("crying_obsidian", 1, 3, 6).loot("obsidian", 1, 3, 6).loot("gold_ingot", 1, 2, 4).loot("ancient_debris", 1, 1, 2)
				.loot("piglin_banner_pattern", 1, 1, 1).loot("netherite_scrap", 1, 1, 1).build());
		return list;
	}

	private static Builder exploration(String id, String name, String icon) {
		return new Builder(id, name, icon, EXPLORATION);
	}

	private static Builder nether(String id, String name, String icon) {
		return new Builder(id, name, icon, NETHER);
	}

	/** Compact construction of the default destinations. */
	private static final class Builder {
		private final ExpeditionDestination destination;

		Builder(String id, String name, String icon, String kind) {
			destination = new ExpeditionDestination(id, name, "minecraft:" + icon, kind);
		}

		Builder level(int level) {
			destination.minLevel = level;
			return this;
		}

		/** Duration multiplier of the default destination of the kind. */
		Builder duration(double multiplier) {
			destination.durationSeconds = 0;
			destination.durationMultiplier = multiplier;
			return this;
		}

		Builder risk(double lossChance) {
			destination.lossChance = lossChance;
			return this;
		}

		Builder rolls(int rolls) {
			destination.rolls = rolls;
			return this;
		}

		Builder map(double chance) {
			destination.treasureMapChance = chance;
			return this;
		}

		Builder unique(double multiplier) {
			destination.uniqueChanceMultiplier = multiplier;
			return this;
		}

		Builder loot(String item, int min, int max, int weight) {
			destination.loot.add(new VillagerConfig.LootEntry("minecraft:" + item, min, max, weight));
			return this;
		}

		ExpeditionDestination build() {
			return destination;
		}
	}
}
