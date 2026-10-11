package dev.guildy.quest;

import dev.guildy.Guildy;
import dev.guildy.config.Comment;
import dev.guildy.config.Json5;
import dev.guildy.config.Validatable;
import dev.guildy.core.ItemUtil;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Comment({
		"Guildy - quetes de guilde (tableau de quetes : 6 panneaux = 6 quetes communes a toute la guilde).",
		"Types : GATHER (casser des blocs : target = bloc ou #tag), KILL (tuer : target = entite ou #tag),",
		"DELIVER (livrer depuis le menu : target = objet ou #tag), SURVIVE (gagner des vagues nocturnes),",
		"EXPLORE (atteindre un lieu tire au hasard loin du coeur).",
		"Valeurs a l'echelle du niveau de guilde : valeur = aleatoire(min..max) + perLevel x (niveau - 1).",
		"rewardItems : [{item, count, chance (0..1)}] ; flagChance / treasureMapChance : chance (0..1) d'obtenir",
		"un drapeau d'extension / une carte au tresor. durationDays : duree en jours de jeu (1 jour = 20 minutes).",
		"weight : poids relatif du tirage ; minLevel / maxLevel (0 = sans maximum) : niveaux de guilde concernes."
})
public final class QuestConfig implements Validatable {

	@Comment("Delai (secondes) avant qu'une nouvelle quete apparaisse sur un panneau libere")
	public int refreshDelaySeconds = 120;

	@Comment("Annoncer aux membres connectes chaque nouvelle quete affichee au tableau")
	public boolean announceNewQuests = true;

	@Comment("Quetes EXPLORE : distance minimale / maximale (blocs) entre le coeur de la guilde et le lieu a atteindre")
	public int exploreMinDistance = 300;
	public int exploreMaxDistance = 800;

	@Comment("Quetes EXPLORE : un membre doit passer a moins de ce nombre de blocs du lieu")
	public int exploreRadius = 24;

	@Comment("Cartes au tresor (recompenses de quetes)")
	public Treasure treasure = new Treasure();

	@Comment("Modeles de quetes (voir l'explication en haut du fichier)")
	public List<QuestTemplate> templates = defaults();

	@Json5.Section
	public static final class Treasure {
		@Comment("Distance minimale / maximale (blocs) entre le coeur de la guilde et le tresor")
		public int minDistance = 150;
		public int maxDistance = 450;
		@Comment("Distance horizontale (blocs) a laquelle le tresor est deterre (carte en main)")
		public int digRadius = 4;
		@Comment("En dessous de cette distance (blocs), la distance restante s'affiche (carte en main)")
		public int hintRadius = 64;
		@Comment({"Rangs de tresor (le premier element = rang 1). Rang = 1 + (niveau de guilde - 1) / 3.",
				"lootTable : table de butin vanilla du coffre ; goldMin / goldMax : lingots d'or ajoutes ;",
				"items : objets bonus [{item, count, chance}]"})
		public List<TreasureTier> tiers = defaultTiers();
	}

	/** One treasure tier. */
	public static class TreasureTier {
		public String lootTable = "";
		public int goldMin = 0;
		public int goldMax = 0;
		public List<QuestTemplate.RewardItem> items = new ArrayList<>();

		public TreasureTier() {
		}

		TreasureTier(String lootTable, int goldMin, int goldMax, List<QuestTemplate.RewardItem> items) {
			this.lootTable = lootTable;
			this.goldMin = goldMin;
			this.goldMax = goldMax;
			this.items = items;
		}
	}

	/** Usable templates (validated), by id. */
	private transient Map<String, QuestTemplate> usable = new LinkedHashMap<>();

	public Map<String, QuestTemplate> usable() {
		return usable;
	}

	@Override
	public void validate() {
		refreshDelaySeconds = Math.max(0, Math.min(86400, refreshDelaySeconds));
		exploreMinDistance = Math.max(32, exploreMinDistance);
		exploreMaxDistance = Math.max(exploreMinDistance, exploreMaxDistance);
		exploreRadius = Math.max(4, Math.min(128, exploreRadius));
		if (treasure == null) {
			treasure = new Treasure();
		}
		treasure.minDistance = Math.max(16, treasure.minDistance);
		treasure.maxDistance = Math.max(treasure.minDistance, treasure.maxDistance);
		treasure.digRadius = Math.max(1, Math.min(16, treasure.digRadius));
		treasure.hintRadius = Math.max(treasure.digRadius, treasure.hintRadius);
		if (treasure.tiers == null || treasure.tiers.isEmpty()) {
			treasure.tiers = defaultTiers();
		}
		treasure.tiers.removeIf(t -> t == null);
		for (TreasureTier tier : treasure.tiers) {
			if (tier.lootTable == null) {
				tier.lootTable = "";
			}
			if (!tier.lootTable.isBlank() && Identifier.tryParse(tier.lootTable) == null) {
				Guildy.LOGGER.warn("[quests.json5] invalid treasure loot table '{}' (ignored)", tier.lootTable);
				tier.lootTable = "";
			}
			tier.goldMin = Math.max(0, tier.goldMin);
			tier.goldMax = Math.max(tier.goldMin, tier.goldMax);
			if (tier.items == null) {
				tier.items = new ArrayList<>();
			}
			for (QuestTemplate.RewardItem item : tier.items) {
				if (item != null) {
					item.count = Math.max(1, Math.min(64 * 9, item.count));
					item.chance = Double.isFinite(item.chance) ? Math.max(0, Math.min(1, item.chance)) : 0;
					if (ItemUtil.item(item.item) == null) {
						Guildy.LOGGER.warn("[quests.json5] unknown treasure item '{}' (ignored)", item.item);
					}
				}
			}
		}
		if (templates == null) {
			templates = defaults();
		}
		templates.removeIf(t -> t == null);
		Map<String, QuestTemplate> result = new LinkedHashMap<>();
		for (QuestTemplate template : templates) {
			List<String> errors = template.check();
			if (errors.isEmpty() && template.type.hasTarget() && QuestTarget.parse(template.type, template.target) == null) {
				errors.add("unknown " + (template.type == QuestType.KILL ? "entity" : template.type == QuestType.DELIVER ? "item" : "block")
						+ " or invalid tag '" + template.target + "'");
			}
			if (errors.isEmpty() && result.containsKey(template.id)) {
				errors.add("duplicate id");
			}
			if (template.icon != null && !template.icon.isBlank() && ItemUtil.item(template.icon) == null) {
				Guildy.LOGGER.warn("[quests.json5] quest '{}': unknown icon '{}' (automatic icon used)", template.id, template.icon);
				template.icon = "";
			}
			for (QuestTemplate.RewardItem reward : template.rewardItems) {
				if (ItemUtil.item(reward.item) == null) {
					Guildy.LOGGER.warn("[quests.json5] quest '{}': unknown reward item '{}' (ignored)", template.id, reward.item);
				}
			}
			if (!errors.isEmpty()) {
				// Kept in the file so the admin can fix it, but never offered.
				Guildy.LOGGER.error("[quests.json5] quest template '{}' ignored: {}", template.id, String.join("; ", errors));
				continue;
			}
			result.put(template.id, template);
		}
		usable = result;
		QuestTarget.clearCache();
	}

	// ------------------------------------------------------------------ defaults

	static List<TreasureTier> defaultTiers() {
		List<TreasureTier> tiers = new ArrayList<>();
		tiers.add(new TreasureTier("minecraft:chests/simple_dungeon", 4, 8,
				new ArrayList<>(List.of(new QuestTemplate.RewardItem("minecraft:emerald", 2, 0.5)))));
		tiers.add(new TreasureTier("minecraft:chests/buried_treasure", 8, 16,
				new ArrayList<>(List.of(new QuestTemplate.RewardItem("minecraft:diamond", 1, 0.5)))));
		tiers.add(new TreasureTier("minecraft:chests/woodland_mansion", 16, 28,
				new ArrayList<>(List.of(new QuestTemplate.RewardItem("minecraft:diamond", 2, 0.6),
						new QuestTemplate.RewardItem("minecraft:experience_bottle", 8, 0.5)))));
		return tiers;
	}

	public static List<QuestTemplate> defaults() {
		List<QuestTemplate> list = new ArrayList<>();
		// ---- GATHER
		list.add(t("bois", QuestType.GATHER, "Coupe de bois", "Abattez des arbres pour remplir les réserves de bois.",
				"#minecraft:logs", "Bûches", 32, 48, 8, 8, 14, 2, 25, 5, 0.08, 0.05, 10, 1, 3,
				List.of()));
		list.add(t("pierre", QuestType.GATHER, "Carrière", "Extrayez de la roche : pierre, granite, diorite, andésite, tuf...",
				"#minecraft:base_stone_overworld", "Roche", 64, 96, 16, 8, 12, 2, 25, 5, 0.05, 0.05, 10, 1, 3,
				List.of()));
		list.add(t("moisson", QuestType.GATHER, "Moisson", "Récoltez du blé arrivé à maturité.",
				"minecraft:wheat", "Blé mûr", 24, 40, 6, 8, 14, 2, 25, 5, 0.05, 0.05, 8, 1, 3,
				List.of(new QuestTemplate.RewardItem("minecraft:bread", 6, 0.5))));
		list.add(t("charbon", QuestType.GATHER, "Filon de charbon", "Minez du minerai de charbon.",
				"#minecraft:coal_ores", "Minerai de charbon", 12, 20, 3, 10, 16, 2, 30, 5, 0.08, 0.05, 8, 1, 3,
				List.of(new QuestTemplate.RewardItem("minecraft:torch", 16, 0.4))));
		list.add(t("fer", QuestType.GATHER, "Filon de fer", "Minez du minerai de fer.",
				"#minecraft:iron_ores", "Minerai de fer", 8, 14, 2, 14, 22, 3, 40, 6, 0.10, 0.08, 7, 2, 4,
				List.of(new QuestTemplate.RewardItem("minecraft:iron_ingot", 4, 0.3))));
		list.add(t("diamant", QuestType.GATHER, "Éclats de diamant", "Minez du minerai de diamant dans les profondeurs.",
				"#minecraft:diamond_ores", "Minerai de diamant", 2, 4, 0.5, 30, 45, 4, 80, 10, 0.30, 0.25, 3, 4, 5,
				List.of()));
		// ---- KILL
		list.add(t("zombies", QuestType.KILL, "Chasse aux zombies", "Éliminez des zombies et leurs semblables.",
				"#minecraft:zombies", "Zombies", 10, 16, 2, 10, 16, 2, 30, 5, 0.08, 0.05, 9, 1, 3,
				List.of()));
		list.add(t("squelettes", QuestType.KILL, "Os brisés", "Éliminez des squelettes.",
				"#minecraft:skeletons", "Squelettes", 8, 14, 2, 12, 18, 2, 35, 5, 0.08, 0.05, 8, 1, 3,
				List.of(new QuestTemplate.RewardItem("minecraft:arrow", 16, 0.4))));
		list.add(t("araignees", QuestType.KILL, "Toiles et crocs", "Éliminez des araignées.",
				"minecraft:spider", "", 8, 12, 2, 10, 16, 2, 30, 5, 0.08, 0.05, 7, 1, 3,
				List.of(new QuestTemplate.RewardItem("minecraft:string", 8, 0.4))));
		list.add(t("creepers", QuestType.KILL, "Désamorçage", "Éliminez des creepers avant qu'ils n'explosent.",
				"minecraft:creeper", "", 5, 8, 1, 14, 20, 2, 40, 6, 0.10, 0.08, 6, 2, 3,
				List.of(new QuestTemplate.RewardItem("minecraft:gunpowder", 4, 0.4))));
		list.add(t("endermen", QuestType.KILL, "Regards perçants", "Éliminez des endermen.",
				"minecraft:enderman", "", 3, 5, 0.5, 25, 35, 3, 60, 8, 0.20, 0.15, 4, 3, 5,
				List.of(new QuestTemplate.RewardItem("minecraft:ender_pearl", 2, 0.5))));
		list.add(t("sorcieres", QuestType.KILL, "Chasse aux sorcières", "Éliminez des sorcières.",
				"minecraft:witch", "", 2, 4, 0.5, 25, 40, 3, 60, 8, 0.20, 0.20, 3, 4, 5,
				List.of(new QuestTemplate.RewardItem("minecraft:glowstone_dust", 8, 0.4))));
		// ---- DELIVER (gold reward kept below the shop price of the items: buying them never pays, XP is the prize)
		list.add(t("pain", QuestType.DELIVER, "Ravitaillement", "Livrez du pain pour nourrir la garnison.",
				"minecraft:bread", "", 16, 24, 4, 5, 7, 1, 45, 8, 0.05, 0.05, 8, 1, 3,
				List.of()));
		list.add(t("forge", QuestType.DELIVER, "Commande de la forge", "Livrez des lingots de fer au forgeron de la guilde.",
				"minecraft:iron_ingot", "", 8, 16, 2, 6, 9, 2, 70, 10, 0.15, 0.08, 6, 2, 4,
				List.of()));
		list.add(t("tanneur", QuestType.DELIVER, "Commande du tanneur", "Livrez du cuir.",
				"minecraft:leather", "", 8, 12, 2, 4, 5, 1, 50, 8, 0.08, 0.05, 6, 1, 3,
				List.of()));
		list.add(t("tisserand", QuestType.DELIVER, "Commande du tisserand", "Livrez de la laine, de n'importe quelle couleur.",
				"#minecraft:wool", "Laine", 16, 24, 4, 4, 5, 1, 45, 8, 0.08, 0.05, 6, 1, 3,
				List.of()));
		list.add(t("combustible", QuestType.DELIVER, "Réserve de combustible", "Livrez du charbon pour l'hiver.",
				"minecraft:coal", "", 24, 40, 8, 4, 5, 1, 40, 6, 0.05, 0.05, 5, 1, 3,
				List.of()));
		// ---- SURVIVE
		list.add(t("nuit", QuestType.SURVIVE, "Tenir la nuit", "Repoussez une vague nocturne avec succès.",
				"", "", 1, 1, 0, 20, 30, 4, 60, 10, 0.25, 0.15, 6, 1, 4,
				List.of()));
		list.add(t("rempart", QuestType.SURVIVE, "Rempart inébranlable", "Remportez plusieurs vagues nocturnes.",
				"", "", 2, 3, 0, 40, 60, 6, 120, 15, 0.50, 0.30, 3, 3, 7,
				List.of()));
		// ---- EXPLORE
		list.add(t("expedition", QuestType.EXPLORE, "Expédition lointaine", "Rendez-vous au lieu indiqué, loin de la base.",
				"", "", 1, 1, 0, 15, 25, 3, 50, 8, 0.10, 0.30, 5, 1, 3,
				List.of()));
		// Icons where the automatic choice is poor (crop block item = seeds, first mob of the tag = horse).
		for (QuestTemplate template : list) {
			switch (template.id) {
				case "moisson" -> template.icon = "minecraft:wheat";
				case "zombies" -> template.icon = "minecraft:zombie_spawn_egg";
				default -> {
				}
			}
		}
		return list;
	}

	private static QuestTemplate t(String id, QuestType type, String name, String description, String target, String targetName,
			int amountMin, int amountMax, double amountPerLevel, int goldMin, int goldMax, double goldPerLevel,
			int xp, double xpPerLevel, double flagChance, double mapChance, int weight, int minLevel, double days,
			List<QuestTemplate.RewardItem> items) {
		QuestTemplate template = new QuestTemplate();
		template.id = id;
		template.type = type;
		template.name = name;
		template.description = description;
		template.target = target;
		template.targetName = targetName;
		template.amountMin = amountMin;
		template.amountMax = amountMax;
		template.amountPerLevel = amountPerLevel;
		template.rewardGoldMin = goldMin;
		template.rewardGoldMax = goldMax;
		template.rewardGoldPerLevel = goldPerLevel;
		template.rewardXp = xp;
		template.rewardXpPerLevel = xpPerLevel;
		template.flagChance = flagChance;
		template.treasureMapChance = mapChance;
		template.weight = weight;
		template.minLevel = minLevel;
		template.durationDays = days;
		template.rewardItems = new ArrayList<>(items);
		return template;
	}
}
