package dev.guildy.villager;

import dev.guildy.config.Comment;
import dev.guildy.config.Json5;
import dev.guildy.config.Validatable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * villagers.json5 - guild villagers balance. Must stay free of Minecraft classes (used by unit tests).
 */
@Comment({
		"Guildy - villageois de guilde : arrivees, taches, stockage.",
		"Les villageois arrivent au bureau d'accueil (construction 'reception'), puis travaillent pour la guilde.",
		"Rechargement a chaud : /guild admin reload"
})
public final class VillagerConfig implements Validatable {

	@Comment("Arrivees de nouveaux villageois")
	public Arrivals arrivals = new Arrivals();

	@Comment("Reglages communs a toutes les taches")
	public Work work = new Work();

	@Comment("Tache 'Ranger la base' : vide les coffres de depot dans les coffres par categorie")
	public Sorter sorter = new Sorter();

	@Comment("Tache 'Mineur' : minage simule, les minerais dependent de la profondeur (Y) de la zone de travail")
	public Miner miner = new Miner();

	@Comment("Tache 'Bucheron' : coupe reelle des arbres naturels du territoire autour de la zone de travail")
	public Lumberjack lumberjack = new Lumberjack();

	@Comment("Tache 'Fermier' : recolte les cultures mures autour de la zone de travail et les replante")
	public Farmer farmer = new Farmer();

	@Comment("Tache 'Garde' : attaque les monstres du territoire, defend le coeur pendant les vagues")
	public Guard guard = new Guard();

	@Comment("Stockage de la guilde (coffres de depot et coffres par categorie)")
	public Storage storage = new Storage();

	@Comment("Chance (0 a 1) que chaque villageois quitte la guilde apres une vague perdue")
	public double defeatLeaveChance = 0.2;

	@Comment("Prenoms donnes aux villageois qui se presentent")
	public List<String> names = new ArrayList<>(List.of(
			"Albert", "Amélie", "Antoine", "Arthur", "Baptiste", "Bernard", "Camille", "Céleste", "Charles", "Claire",
			"Colette", "Denis", "Édith", "Élise", "Émile", "Étienne", "Fernand", "Firmin", "Gaston", "Germaine",
			"Gisèle", "Hector", "Henri", "Hortense", "Irène", "Jacques", "Jeanne", "Joséphine", "Jules", "Léa",
			"Léon", "Louise", "Lucien", "Madeleine", "Marcel", "Margot", "Marius", "Mathilde", "Maurice", "Noémie",
			"Odette", "Paulette", "Philibert", "Raymond", "René", "Rose", "Simone", "Suzanne", "Théo", "Victor",
			"Yvette", "Zoé"));

	@Json5.Section
	public static final class Arrivals {
		@Comment("Nombre maximum de villageois au niveau 1 de la guilde")
		public int baseMaxVillagers = 2;
		@Comment("Villageois supplementaires autorises par niveau de guilde")
		public int maxVillagersPerLevel = 1;
		@Comment("Chance (0 a 1) qu'un villageois se presente chaque matin (si les conditions sont reunies)")
		public double arrivalChance = 0.6;
		@Comment("Delai (secondes) avant le premier candidat apres la construction du bureau d'accueil")
		public int firstArrivalDelaySeconds = 60;
		@Comment("Duree (secondes) pendant laquelle un candidat attend une reponse avant de repartir")
		public int candidateTimeoutSeconds = 600;
		@Comment("Exiger au moins un lit libre dans le territoire pour qu'un villageois se presente")
		public boolean requireFreeBed = true;
	}

	@Json5.Section
	public static final class Work {
		@Comment("Nombre de piles d'objets qu'un travailleur (mineur, bucheron, fermier) transporte avant d'aller deposer")
		public int carryStacks = 9;
		@Comment("Temps maximum (secondes) pour atteindre une destination ; ensuite le villageois est teleporte a cote")
		public int walkTimeoutSeconds = 40;
		@Comment("Temps (secondes) sans progresser avant de considerer le villageois bloque (meme solution)")
		public int stuckSeconds = 12;
		@Comment("Intervalle minimum (secondes) entre deux recherches d'arbres / de cultures")
		public int scanIntervalSeconds = 10;
		@Comment("Vitesse de marche des villageois au travail (multiplicateur, vanilla = 0.5)")
		public double walkSpeed = 0.6;
	}

	@Json5.Section
	public static final class Sorter {
		@Comment("Intervalle (secondes) entre deux verifications des coffres de depot")
		public int intervalSeconds = 10;
		@Comment("Nombre de piles d'objets emportees a chaque voyage")
		public int stacksPerTrip = 6;
		@Comment("Temps maximum (secondes) pour atteindre un coffre avant teleportation")
		public int walkTimeoutSeconds = 30;
	}

	@Json5.Section
	public static final class Miner {
		@Comment("Duree (secondes) d'une expedition de minage dans la zone de travail")
		public int tripSeconds = 120;
		@Comment("Nombre de tirages dans la table de butin a la fin de chaque expedition")
		public int rollsPerTrip = 5;
		@Comment("Intervalle (secondes) entre deux animations de minage")
		public int effectIntervalSeconds = 3;
		@Comment({
				"Tables de butin par profondeur : la premiere table dont maxY >= Y de la zone de travail est utilisee",
				"(si Y est au-dessus de toutes, la derniere). entries : item, min, max (quantite), weight (poids du tirage)."
		})
		public List<DepthTable> depthTables = defaultDepthTables();
	}

	@Json5.Section
	public static final class Lumberjack {
		@Comment("Rayon (blocs) de recherche des arbres autour de la zone de travail")
		public int radius = 16;
		@Comment("Nombre maximum de buches d'un arbre (les arbres plus grands sont ignores)")
		public int maxLogsPerTree = 80;
		@Comment("Replanter une pousse a la base de l'arbre coupe (si le sol le permet)")
		public boolean replant = true;
	}

	@Json5.Section
	public static final class Farmer {
		@Comment("Rayon (blocs) de recolte autour de la zone de travail")
		public int radius = 8;
		@Comment("Hauteur (blocs) au-dessus et en dessous de la zone de travail ou chercher des cultures")
		public int verticalRange = 3;
		@Comment("Nombre maximum de cultures recoltees par seconde")
		public int harvestPerSecond = 3;
	}

	@Json5.Section
	public static final class Guard {
		@Comment("Rayon (blocs) de surveillance autour du point de garde (zone de travail, sinon le coeur)")
		public int radius = 16;
		@Comment("Degats par coup au niveau 1 de la guilde")
		public double damage = 4.0;
		@Comment("Degats supplementaires par niveau de guilde")
		public double damagePerLevel = 0.5;
		@Comment("Delai (ticks, 20 = 1 seconde) entre deux coups")
		public int attackCooldownTicks = 20;
		@Comment("Distance (blocs) a laquelle le garde peut frapper")
		public double attackReach = 2.5;
		@Comment("Abandonner une cible qu'on n'arrive pas a atteindre apres ce delai (secondes)")
		public int chaseTimeoutSeconds = 20;
		@Comment("Monstres ignores par les gardes")
		public List<String> ignoredMobs = new ArrayList<>(List.of("minecraft:creeper", "minecraft:phantom", "minecraft:ghast", "minecraft:enderman"));
	}

	@Json5.Section
	public static final class Storage {
		@Comment({
				"Categories de rangement, dans l'ordre de priorite (la premiere qui correspond l'emporte).",
				"Chaque valeur est un id d'objet (minecraft:diamond) ou un tag d'objet precede de # (#minecraft:logs)."
		})
		public LinkedHashMap<String, List<String>> categories = defaultCategories();
		@Comment("Categorie implicite des objets qui ne correspondent a aucune categorie (ou sans coffre dedie)")
		public String fallbackCategory = "Divers";
		@Comment("Nombre maximum de conteneurs enregistres par guilde")
		public int maxChests = 48;
	}

	/** A loot table used when the miner's work position is at or below {@code maxY}. */
	public static final class DepthTable {
		public int maxY;
		public List<LootEntry> entries = new ArrayList<>();

		public DepthTable() {
		}

		public DepthTable(int maxY, List<LootEntry> entries) {
			this.maxY = maxY;
			this.entries = new ArrayList<>(entries);
		}
	}

	public static final class LootEntry {
		public String item = "minecraft:cobblestone";
		public int min = 1;
		public int max = 1;
		public int weight = 1;

		public LootEntry() {
		}

		public LootEntry(String item, int min, int max, int weight) {
			this.item = item;
			this.min = min;
			this.max = max;
			this.weight = weight;
		}
	}

	// ------------------------------------------------------------------ defaults

	public static List<DepthTable> defaultDepthTables() {
		List<DepthTable> tables = new ArrayList<>();
		// Deep slate layer: best ores, diamonds only here (rare).
		tables.add(new DepthTable(-24, List.of(
				new LootEntry("minecraft:cobbled_deepslate", 2, 6, 34),
				new LootEntry("minecraft:raw_iron", 1, 3, 16),
				new LootEntry("minecraft:raw_gold", 1, 2, 10),
				new LootEntry("minecraft:redstone", 2, 5, 12),
				new LootEntry("minecraft:lapis_lazuli", 2, 5, 9),
				new LootEntry("minecraft:coal", 1, 3, 6),
				new LootEntry("minecraft:tuff", 1, 3, 8),
				new LootEntry("minecraft:diamond", 1, 1, 3),
				new LootEntry("minecraft:emerald", 1, 1, 2))));
		tables.add(new DepthTable(16, List.of(
				new LootEntry("minecraft:cobblestone", 2, 6, 34),
				new LootEntry("minecraft:cobbled_deepslate", 1, 4, 8),
				new LootEntry("minecraft:coal", 1, 4, 16),
				new LootEntry("minecraft:raw_iron", 1, 3, 16),
				new LootEntry("minecraft:raw_copper", 2, 5, 12),
				new LootEntry("minecraft:raw_gold", 1, 2, 6),
				new LootEntry("minecraft:redstone", 1, 4, 5),
				new LootEntry("minecraft:lapis_lazuli", 1, 3, 4),
				new LootEntry("minecraft:gravel", 1, 3, 6))));
		tables.add(new DepthTable(64, List.of(
				new LootEntry("minecraft:cobblestone", 2, 6, 44),
				new LootEntry("minecraft:coal", 1, 3, 20),
				new LootEntry("minecraft:raw_copper", 1, 4, 14),
				new LootEntry("minecraft:raw_iron", 1, 2, 10),
				new LootEntry("minecraft:gravel", 1, 3, 8),
				new LootEntry("minecraft:flint", 1, 2, 4))));
		// Surface and mountains.
		tables.add(new DepthTable(320, List.of(
				new LootEntry("minecraft:cobblestone", 2, 6, 50),
				new LootEntry("minecraft:coal", 1, 3, 18),
				new LootEntry("minecraft:raw_iron", 1, 2, 10),
				new LootEntry("minecraft:raw_copper", 1, 3, 10),
				new LootEntry("minecraft:flint", 1, 2, 6),
				new LootEntry("minecraft:emerald", 1, 1, 1))));
		return tables;
	}

	public static LinkedHashMap<String, List<String>> defaultCategories() {
		LinkedHashMap<String, List<String>> map = new LinkedHashMap<>();
		map.put("Bois", new ArrayList<>(List.of(
				"#minecraft:logs", "#minecraft:planks", "#minecraft:wooden_slabs", "#minecraft:wooden_stairs",
				"#minecraft:wooden_fences", "#minecraft:saplings", "#minecraft:leaves", "minecraft:stick")));
		map.put("Pierre & terre", new ArrayList<>(List.of(
				"#minecraft:stone_crafting_materials", "#minecraft:stone_tool_materials", "#minecraft:dirt", "#minecraft:sand",
				"#minecraft:stone_bricks", "minecraft:cobblestone", "minecraft:stone", "minecraft:cobbled_deepslate",
				"minecraft:deepslate", "minecraft:granite", "minecraft:diorite", "minecraft:andesite", "minecraft:tuff",
				"minecraft:calcite", "minecraft:gravel", "minecraft:clay_ball", "minecraft:netherrack")));
		map.put("Minerais & lingots", new ArrayList<>(List.of(
				"#minecraft:coals", "#minecraft:iron_ores", "#minecraft:gold_ores", "#minecraft:copper_ores",
				"#minecraft:coal_ores", "#minecraft:diamond_ores", "#minecraft:emerald_ores", "#minecraft:lapis_ores",
				"#minecraft:redstone_ores", "minecraft:raw_iron", "minecraft:raw_gold", "minecraft:raw_copper",
				"minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:copper_ingot", "minecraft:iron_nugget",
				"minecraft:gold_nugget", "minecraft:diamond", "minecraft:emerald", "minecraft:lapis_lazuli",
				"minecraft:redstone", "minecraft:quartz", "minecraft:amethyst_shard", "minecraft:flint")));
		map.put("Cultures & nourriture", new ArrayList<>(List.of(
				"#minecraft:villager_plantable_seeds", "#minecraft:meat", "#minecraft:fishes", "minecraft:wheat",
				"minecraft:carrot", "minecraft:potato", "minecraft:beetroot", "minecraft:bread", "minecraft:apple",
				"minecraft:melon_slice", "minecraft:pumpkin", "minecraft:sugar_cane", "minecraft:egg")));
		map.put("Butin de monstres", new ArrayList<>(List.of(
				"minecraft:rotten_flesh", "minecraft:bone", "minecraft:bone_meal", "minecraft:string",
				"minecraft:spider_eye", "minecraft:gunpowder", "minecraft:ender_pearl", "minecraft:slime_ball",
				"minecraft:phantom_membrane", "minecraft:blaze_rod", "minecraft:ghast_tear", "minecraft:magma_cream")));
		map.put("Outils & armes", new ArrayList<>(List.of(
				"#minecraft:swords", "#minecraft:axes", "#minecraft:pickaxes", "#minecraft:shovels", "#minecraft:hoes",
				"#minecraft:spears", "#minecraft:head_armor", "#minecraft:chest_armor", "#minecraft:leg_armor",
				"#minecraft:foot_armor", "#minecraft:arrows", "minecraft:bow", "minecraft:crossbow", "minecraft:shield",
				"minecraft:trident", "minecraft:mace", "minecraft:fishing_rod", "minecraft:shears",
				"minecraft:flint_and_steel")));
		return map;
	}

	// ------------------------------------------------------------------ helpers

	/** Max villagers for a guild level (never negative). */
	public int maxVillagers(int guildLevel) {
		return Math.max(0, arrivals.baseMaxVillagers + Math.max(0, guildLevel - 1) * arrivals.maxVillagersPerLevel);
	}

	public double guardDamage(int guildLevel) {
		return guard.damage + Math.max(0, guildLevel - 1) * guard.damagePerLevel;
	}

	@Override
	public void validate() {
		if (arrivals == null) {
			arrivals = new Arrivals();
		}
		if (work == null) {
			work = new Work();
		}
		if (sorter == null) {
			sorter = new Sorter();
		}
		if (miner == null) {
			miner = new Miner();
		}
		if (lumberjack == null) {
			lumberjack = new Lumberjack();
		}
		if (farmer == null) {
			farmer = new Farmer();
		}
		if (guard == null) {
			guard = new Guard();
		}
		if (storage == null) {
			storage = new Storage();
		}
		arrivals.baseMaxVillagers = clamp(arrivals.baseMaxVillagers, 0, 100);
		arrivals.maxVillagersPerLevel = clamp(arrivals.maxVillagersPerLevel, 0, 20);
		arrivals.arrivalChance = clamp(arrivals.arrivalChance, 0.0, 1.0);
		arrivals.firstArrivalDelaySeconds = clamp(arrivals.firstArrivalDelaySeconds, 1, 3600);
		arrivals.candidateTimeoutSeconds = clamp(arrivals.candidateTimeoutSeconds, 30, 24 * 3600);
		work.carryStacks = clamp(work.carryStacks, 1, 36);
		work.walkTimeoutSeconds = clamp(work.walkTimeoutSeconds, 5, 600);
		work.stuckSeconds = clamp(work.stuckSeconds, 3, 300);
		work.scanIntervalSeconds = clamp(work.scanIntervalSeconds, 10, 600);
		work.walkSpeed = clamp(work.walkSpeed, 0.2, 1.5);
		sorter.intervalSeconds = clamp(sorter.intervalSeconds, 1, 600);
		sorter.stacksPerTrip = clamp(sorter.stacksPerTrip, 1, 27);
		sorter.walkTimeoutSeconds = clamp(sorter.walkTimeoutSeconds, 5, 600);
		miner.tripSeconds = clamp(miner.tripSeconds, 5, 3600);
		miner.rollsPerTrip = clamp(miner.rollsPerTrip, 1, 64);
		miner.effectIntervalSeconds = clamp(miner.effectIntervalSeconds, 1, 60);
		if (miner.depthTables == null || miner.depthTables.isEmpty()) {
			miner.depthTables = defaultDepthTables();
		}
		miner.depthTables = new ArrayList<>(miner.depthTables);
		miner.depthTables.removeIf(table -> table == null);
		for (DepthTable table : miner.depthTables) {
			table.entries = table.entries == null ? new ArrayList<>() : new ArrayList<>(table.entries);
			table.entries.removeIf(entry -> entry == null || entry.item == null || entry.item.isBlank() || entry.weight <= 0);
			for (LootEntry entry : table.entries) {
				entry.item = entry.item.trim();
				entry.min = clamp(entry.min, 1, 64);
				entry.max = clamp(entry.max, entry.min, 64);
			}
		}
		miner.depthTables.removeIf(table -> table.entries.isEmpty());
		if (miner.depthTables.isEmpty()) {
			miner.depthTables = defaultDepthTables();
		}
		miner.depthTables.sort(Comparator.comparingInt(table -> table.maxY));
		lumberjack.radius = clamp(lumberjack.radius, 2, 48);
		lumberjack.maxLogsPerTree = clamp(lumberjack.maxLogsPerTree, 1, 256);
		farmer.radius = clamp(farmer.radius, 1, 32);
		farmer.verticalRange = clamp(farmer.verticalRange, 0, 8);
		farmer.harvestPerSecond = clamp(farmer.harvestPerSecond, 1, 16);
		guard.radius = clamp(guard.radius, 2, 48);
		guard.damage = clamp(guard.damage, 0.0, 100.0);
		guard.damagePerLevel = clamp(guard.damagePerLevel, 0.0, 20.0);
		guard.attackCooldownTicks = clamp(guard.attackCooldownTicks, 1, 200);
		guard.attackReach = clamp(guard.attackReach, 1.0, 6.0);
		guard.chaseTimeoutSeconds = clamp(guard.chaseTimeoutSeconds, 3, 300);
		guard.ignoredMobs = guard.ignoredMobs == null ? new ArrayList<>() : new ArrayList<>(guard.ignoredMobs);
		guard.ignoredMobs.removeIf(id -> id == null || id.isBlank());
		if (storage.categories == null) {
			storage.categories = defaultCategories();
		}
		LinkedHashMap<String, List<String>> categories = new LinkedHashMap<>();
		for (Map.Entry<String, List<String>> entry : storage.categories.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank()) {
				continue;
			}
			List<String> values = entry.getValue() == null ? new ArrayList<>() : new ArrayList<>(entry.getValue());
			values.removeIf(value -> value == null || value.isBlank());
			categories.put(entry.getKey().trim(), values);
		}
		storage.categories = categories;
		if (storage.fallbackCategory == null || storage.fallbackCategory.isBlank()) {
			storage.fallbackCategory = "Divers";
		}
		storage.fallbackCategory = storage.fallbackCategory.trim();
		String fallback = storage.fallbackCategory;
		storage.categories.keySet().removeIf(name -> name.equalsIgnoreCase(fallback));
		storage.maxChests = clamp(storage.maxChests, 1, 256);
		defeatLeaveChance = clamp(defeatLeaveChance, 0.0, 1.0);
		names = names == null ? new ArrayList<>() : new ArrayList<>(names);
		names.removeIf(name -> name == null || name.isBlank());
		if (names.isEmpty()) {
			names = new ArrayList<>(new VillagerConfig().names);
		}
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
}
