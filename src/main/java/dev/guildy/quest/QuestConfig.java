package dev.guildy.quest;

import dev.guildy.Guildy;
import dev.guildy.config.Comment;
import dev.guildy.config.Json5;
import dev.guildy.config.Validatable;
import dev.guildy.core.ItemUtil;
import dev.guildy.level.LevelCurve;
import dev.guildy.level.LevelScaling;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Comment({
		"Guildy - quetes de guilde (tableau de quetes : 6 panneaux = 6 quetes communes a toute la guilde).",
		"Seules les actions des membres comptent, jamais en mode creatif. Types (target = id ou #tag) :",
		"  GATHER casser des blocs (bloc), KILL tuer (entite), DELIVER livrer depuis le menu (objet), SURVIVE gagner des",
		"  vagues nocturnes, EXPLORE atteindre un lieu tire au hasard loin du coeur,",
		"  CRAFT fabriquer (objet), SMELT cuire/fondre au four, au fumoir ou au haut-fourneau (objet obtenu),",
		"  FISH pecher (objet, vide = toute prise), BREED faire naitre des animaux (entite, vide = tous), TAME apprivoiser",
		"  (entite, vide = tous), TRADE echanger avec des villageois ou des marchands (objet obtenu, vide = tout echange),",
		"  ENCHANT enchanter a la table (objet, vide = tous), ELITE tuer des elites de horde, BOSS vaincre des boss de horde,",
		"  NIGHT_HUNT tuer des monstres la nuit (entite, vide = tout monstre), BIOME entrer dans un biome (biome ou #tag),",
		"  COLLECTION livrer un assortiment : collection = {min, max, items: [{item, name, amountMin, amountMax, amountPerLevel}]}",
		"  (entre min et max objets differents tires parmi items, chacun avec sa propre progression).",
		"Valeurs a l'echelle du niveau de guilde : valeur = aleatoire(min..max) + perLevel x (niveau - 1) ; au-dela du niveau 10,",
		"le niveau de calcul monte plus lentement (voir levelScalingAbove10 / maxScalingLevel).",
		"rewardItems : [{item, count, chance (0..1)}] ; flagChance / treasureMapChance : chance (0..1) d'obtenir",
		"un drapeau d'extension / une carte au tresor. durationDays : duree en jours de jeu (1 jour = 20 minutes).",
		"weight : poids relatif du tirage ; minLevel / maxLevel (0 = sans maximum) : niveaux de guilde concernes ;",
		"legendary : peut devenir un contrat legendaire (jamais EXPLORE / BIOME).",
		"Economie : l'or des quetes DELIVER / COLLECTION doit rester sous le prix boutique des objets demandes (et n'est",
		"jamais augmente par l'arbre d'evolution) ; pour CRAFT, choisissez des objets qu'on ne peut pas re-decomposer."
})
public final class QuestConfig implements Validatable {
	/** Default templates that existed before {@link #knownTemplates}: never re-added (an admin may have removed them). */
	static final Set<String> ORIGINAL_DEFAULTS = Set.of("bois", "pierre", "moisson", "charbon", "fer", "diamant", "zombies",
			"squelettes", "araignees", "creepers", "endermen", "sorcieres", "pain", "forge", "tanneur", "tisserand", "combustible",
			"nuit", "rempart", "expedition");

	@Comment("Delai (secondes) avant qu'une nouvelle quete apparaisse sur un panneau libere")
	public int refreshDelaySeconds = 120;

	@Comment("Annoncer aux membres connectes chaque nouvelle quete affichee au tableau")
	public boolean announceNewQuests = true;

	@Comment("Quetes EXPLORE : distance minimale / maximale (blocs) entre le coeur de la guilde et le lieu a atteindre")
	public int exploreMinDistance = 300;
	public int exploreMaxDistance = 800;

	@Comment("Quetes EXPLORE : un membre doit passer a moins de ce nombre de blocs du lieu")
	public int exploreRadius = 24;

	@Comment({"Chance (0..1) qu'une quete ordinaire rapporte aussi une arme unique, tiree au moment de recuperer la recompense",
			"(module des armes uniques ; rien si aucune n'est disponible)"})
	public double uniqueChance = 0.03;

	@Comment({
			"Niveaux de guilde au-dela de 10 : quantites et recompenses (perLevel) calculees avec le niveau",
			"10 + (niveau - 10) x levelScalingAbove10, au plus maxScalingLevel. Par defaut : niveau de calcul 12 au niveau 20,",
			"18 au niveau 50, 28 au niveau 100, 30 au plus (les quetes restent faisables, l'or des livraisons reste sous le prix",
			"boutique). minLevel / maxLevel des modeles utilisent toujours le vrai niveau"
	})
	public double levelScalingAbove10 = 0.2;
	public int maxScalingLevel = 30;

	@Comment("Contrats legendaires : une quete plus dure et plus longue, recompensee par une arme unique en plus de l'or et de l'XP")
	public Legendary legendary = new Legendary();

	@Comment("Cartes au tresor (recompenses de quetes)")
	public Treasure treasure = new Treasure();

	@Comment("Modeles de quetes (voir l'explication en haut du fichier)")
	public List<QuestTemplate> templates = defaults();

	@Comment({
			"Modeles par defaut deja proposes. Un modele ajoute par une mise a jour du mod est ajoute automatiquement a",
			"'templates' une seule fois ; pour ne pas l'utiliser, retirez-le de 'templates' (pas d'ici) ou mettez weight a 0."
	})
	public List<String> knownTemplates = new ArrayList<>();

	@Json5.Section
	public static final class Legendary {
		@Comment("Activer les contrats legendaires")
		public boolean enabled = true;
		@Comment("Niveau de guilde minimum")
		public int minLevel = 3;
		@Comment("Chance (0..1) qu'une nouvelle quete du tableau soit un contrat legendaire (un seul a la fois)")
		public double chance = 0.08;
		@Comment("Delai minimum (jours de jeu) entre deux contrats legendaires d'une meme guilde")
		public double cooldownDays = 2.0;
		@Comment("Multiplicateurs appliques a la quete tiree : quantite (arrondie au-dessus), duree, or, XP (minimum 1).")
		public double amountMultiplier = 2.0;
		public double durationMultiplier = 2.0;
		public double goldMultiplier = 2.5;
		public double xpMultiplier = 3.0;
		@Comment("Or verse au tresor a la place de l'arme unique si aucune n'est disponible")
		public int fallbackGold = 60;

		public long cooldownTicks() {
			return Math.round(cooldownDays * QuestGenerator.DAY_TICKS);
		}

		public QuestGenerator.Legendary multipliers() {
			return new QuestGenerator.Legendary(amountMultiplier, durationMultiplier, goldMultiplier, xpMultiplier);
		}
	}

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

	/** Level used for the amounts and rewards of the quests of a guild level (see {@code levelScalingAbove10}). */
	public int scalingLevel(int guildLevel) {
		return LevelScaling.softLevel(guildLevel, levelScalingAbove10, maxScalingLevel);
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
		levelScalingAbove10 = Double.isFinite(levelScalingAbove10) ? Math.max(0, Math.min(1, levelScalingAbove10)) : 0.2;
		maxScalingLevel = Math.max(LevelScaling.CLASSIC_LEVELS, Math.min(LevelCurve.ABSOLUTE_MAX_LEVEL, maxScalingLevel));
		uniqueChance = clamp01(uniqueChance);
		if (legendary == null) {
			legendary = new Legendary();
		}
		legendary.minLevel = Math.max(1, legendary.minLevel);
		legendary.chance = clamp01(legendary.chance);
		legendary.cooldownDays = Double.isFinite(legendary.cooldownDays) ? Math.max(0, Math.min(60, legendary.cooldownDays)) : 2.0;
		legendary.amountMultiplier = multiplier(legendary.amountMultiplier);
		legendary.durationMultiplier = multiplier(legendary.durationMultiplier);
		legendary.goldMultiplier = multiplier(legendary.goldMultiplier);
		legendary.xpMultiplier = multiplier(legendary.xpMultiplier);
		legendary.fallbackGold = Math.max(0, legendary.fallbackGold);
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
		if (knownTemplates == null) {
			knownTemplates = new ArrayList<>();
		}
		for (String added : mergeNewDefaults(templates, knownTemplates)) {
			Guildy.LOGGER.info("[quests.json5] new default quest '{}' added", added);
		}
		Map<String, QuestTemplate> result = new LinkedHashMap<>();
		for (QuestTemplate template : templates) {
			List<String> errors = template.check();
			if (errors.isEmpty() && template.type.hasTarget() && !template.target.isBlank()
					&& QuestTarget.parse(template.type, template.target) == null) {
				errors.add("unknown " + template.type.targetKind().name().toLowerCase(java.util.Locale.ROOT) + " or invalid tag '"
						+ template.target + "'");
			}
			if (errors.isEmpty() && template.collection != null) {
				checkCollectionItems(template, errors);
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

	/** COLLECTION items: unknown ids are skipped (kept in the file); too few usable items make the template unusable. */
	private static void checkCollectionItems(QuestTemplate template, List<String> errors) {
		QuestTemplate.Assortment collection = template.collection;
		List<QuestTemplate.Part> usableParts = new ArrayList<>();
		for (QuestTemplate.Part part : collection.items) {
			if (QuestTarget.parse(QuestType.TargetKind.ITEM, part.item) != null) {
				usableParts.add(part);
			} else {
				Guildy.LOGGER.warn("[quests.json5] quest '{}': unknown collection item '{}' (ignored)", template.id, part.item);
			}
		}
		collection.usable = usableParts;
		if (usableParts.size() < collection.min) {
			errors.add("collection: only " + usableParts.size() + " valid items, " + collection.min + " needed");
		}
	}

	/**
	 * BIOME targets can only be checked once the server (and its data-driven biome registry) runs: templates naming an
	 * unknown biome are withdrawn from the usable ones (tags are accepted as they are).
	 */
	public void checkBiomes(RegistryAccess registries) {
		var biomes = registries.lookupOrThrow(Registries.BIOME);
		Iterator<QuestTemplate> it = usable.values().iterator();
		while (it.hasNext()) {
			QuestTemplate template = it.next();
			if (template.type != QuestType.BIOME) {
				continue;
			}
			QuestTarget target = QuestTarget.parse(QuestType.BIOME, template.target);
			Identifier id = target == null ? null : target.biomeId();
			if (id != null && !biomes.containsKey(id)) {
				Guildy.LOGGER.error("[quests.json5] quest template '{}' ignored: unknown biome '{}'", template.id, template.target);
				it.remove();
			}
		}
	}

	/**
	 * Existing files keep their own 'templates' list: default templates added by a mod update (not yet in {@code known})
	 * are appended once, then remembered in {@code known} (pure, unit-tested).
	 *
	 * @return ids appended to {@code templates}
	 */
	static List<String> mergeNewDefaults(List<QuestTemplate> templates, List<String> known) {
		List<String> added = new ArrayList<>();
		Set<String> present = new HashSet<>();
		for (QuestTemplate template : templates) {
			present.add(template.id);
		}
		for (QuestTemplate template : defaults()) {
			if (known.contains(template.id)) {
				continue;
			}
			known.add(template.id);
			if (!present.contains(template.id) && !ORIGINAL_DEFAULTS.contains(template.id)) {
				templates.add(template);
				added.add(template.id);
			}
		}
		return added;
	}

	private static double clamp01(double value) {
		return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
	}

	private static double multiplier(double value) {
		return Double.isFinite(value) ? Math.max(1.0, Math.min(10.0, value)) : 1.0;
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
		list.add(icon(t("moisson", QuestType.GATHER, "Moisson", "Récoltez du blé arrivé à maturité.",
				"minecraft:wheat", "Blé mûr", 24, 40, 6, 8, 14, 2, 25, 5, 0.05, 0.05, 8, 1, 3,
				List.of(new QuestTemplate.RewardItem("minecraft:bread", 6, 0.5))), "minecraft:wheat"));
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
		list.add(icon(t("zombies", QuestType.KILL, "Chasse aux zombies", "Éliminez des zombies et leurs semblables.",
				"#minecraft:zombies", "Zombies", 10, 16, 2, 10, 16, 2, 30, 5, 0.08, 0.05, 9, 1, 3,
				List.of()), "minecraft:zombie_spawn_egg"));
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
		list.add(icon(t("pillards", QuestType.KILL, "Chasse aux pillards",
				"Pillards, vindicateurs et évocateurs rôdent autour de la base : chassez-les avant le prochain raid.",
				"#minecraft:illager", "Illageois", 5, 8, 1, 18, 26, 3, 70, 10, 0.15, 0.10, 5, 3, 4,
				List.of(new QuestTemplate.RewardItem("minecraft:emerald", 2, 0.4))), "minecraft:crossbow"));
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
		// ---- CRAFT (results that cannot be turned back into their ingredients; gold below the shop price of the result)
		list.add(maxLevel(t("torches", QuestType.CRAFT, "Que la lumière soit",
				"Fabriquez des torches : une base bien éclairée est une base où les monstres n'apparaissent pas.",
				"minecraft:torch", "", 32, 48, 8, 3, 4, 1, 30, 5, 0.04, 0.03, 8, 1, 2,
				List.of()), 5));
		list.add(t("boulanger", QuestType.CRAFT, "La fournée du boulanger",
				"Pétrissez le blé de la guilde : fabriquez du pain pour nourrir les défenseurs.",
				"minecraft:bread", "", 16, 24, 4, 4, 6, 1, 35, 6, 0.05, 0.03, 8, 1, 3,
				List.of()));
		list.add(t("rails", QuestType.CRAFT, "Le chemin de fer",
				"Fabriquez des rails pour relier la base à la mine et aux champs.",
				"minecraft:rail", "", 32, 48, 8, 10, 15, 2, 55, 8, 0.08, 0.05, 6, 2, 3,
				List.of(new QuestTemplate.RewardItem("minecraft:minecart", 1, 0.5))));
		list.add(t("gateau", QuestType.CRAFT, "Le gâteau de la victoire",
				"Lait, sucre, œufs et blé : fabriquez des gâteaux pour fêter les victoires de la guilde.",
				"minecraft:cake", "", 2, 3, 0.5, 10, 14, 2, 60, 8, 0.06, 0.05, 5, 2, 3,
				List.of(new QuestTemplate.RewardItem("minecraft:cookie", 8, 0.5))));
		list.add(t("lanternes", QuestType.CRAFT, "Les lanternes du rempart",
				"Fabriquez des lanternes : les remparts doivent rester éclairés pendant les nuits de horde.",
				"minecraft:lantern", "", 8, 12, 1, 16, 22, 2, 75, 10, 0.10, 0.10, 5, 3, 4,
				List.of(new QuestTemplate.RewardItem("minecraft:experience_bottle", 2, 0.4))));
		// ---- SMELT (furnace, smoker or blast furnace; counted when a member takes the result out)
		list.add(t("fonderie", QuestType.SMELT, "La fonderie",
				"Faites fondre du minerai de fer au fourneau ou au haut-fourneau, puis récupérez les lingots.",
				"minecraft:iron_ingot", "", 12, 20, 3, 6, 10, 1.5, 45, 6, 0.06, 0.04, 7, 1, 3,
				List.of()));
		list.add(icon(t("banquet", QuestType.SMELT, "Le grand banquet",
				"Faites cuire de la viande au fourneau ou au fumoir : ce soir, la guilde festoie !",
				"#minecraft:meat", "Viande cuite", 16, 24, 4, 5, 8, 1, 40, 6, 0.05, 0.03, 7, 1, 3,
				List.of()), "minecraft:cooked_beef"));
		list.add(t("verrier", QuestType.SMELT, "Le souffleur de verre",
				"Faites fondre du sable pour obtenir du verre : la guilde veut des fenêtres !",
				"minecraft:glass", "", 32, 48, 8, 3, 5, 1, 35, 5, 0.04, 0.03, 6, 2, 2,
				List.of()));
		// ---- FISH
		list.add(icon(t("peche", QuestType.FISH, "Jour de pêche", "Sortez la canne à pêche et ramenez du poisson frais.",
				"#minecraft:fishes", "Poissons", 8, 12, 2, 6, 10, 1.5, 40, 6, 0.05, 0.08, 7, 1, 3,
				List.of()), "minecraft:cod"));
		list.add(t("peche_miraculeuse", QuestType.FISH, "Pêche miraculeuse",
				"Poissons, trésors ou vieilles bottes : chaque prise compte. La patience est une vertu !",
				"", "", 24, 32, 4, 14, 20, 2, 70, 10, 0.10, 0.25, 5, 3, 4,
				List.of(new QuestTemplate.RewardItem("minecraft:name_tag", 1, 0.15))));
		// ---- BREED
		list.add(t("elevage", QuestType.BREED, "Le petit élevage", "Nourrissez les animaux de la ferme pour qu'ils aient des petits.",
				"", "", 4, 6, 1, 6, 10, 1.5, 40, 6, 0.05, 0.03, 7, 1, 3,
				List.of()));
		list.add(t("vaches", QuestType.BREED, "Le troupeau", "Agrandissez le troupeau : faites naître des veaux avec du blé.",
				"minecraft:cow", "Vaches", 5, 8, 1, 8, 12, 1.5, 50, 8, 0.06, 0.04, 5, 2, 3,
				List.of(new QuestTemplate.RewardItem("minecraft:leather", 4, 0.4))));
		// ---- TAME
		list.add(t("compagnon", QuestType.TAME, "Le meilleur ami",
				"Apprivoisez des loups avec des os : de fidèles compagnons pour les nuits de horde.",
				"minecraft:wolf", "Loups", 1, 2, 0.25, 8, 12, 1.5, 45, 6, 0.05, 0.05, 5, 1, 4,
				List.of(new QuestTemplate.RewardItem("minecraft:bone", 8, 0.5))));
		list.add(t("cavalerie", QuestType.TAME, "La cavalerie", "Domptez des chevaux sauvages : la guilde a besoin de montures rapides.",
				"minecraft:horse", "Chevaux", 2, 3, 0.5, 14, 20, 2, 65, 8, 0.10, 0.05, 4, 3, 4,
				List.of(new QuestTemplate.RewardItem("minecraft:saddle", 1, 0.35), new QuestTemplate.RewardItem("minecraft:lead", 2, 0.5))));
		// ---- TRADE
		list.add(t("negoce", QuestType.TRADE, "Jour de marché",
				"Faites des affaires : échangez avec des villageois ou des marchands de passage.",
				"", "", 6, 10, 1, 8, 12, 1.5, 50, 8, 0.05, 0.05, 5, 2, 3,
				List.of()));
		list.add(t("emeraudes", QuestType.TRADE, "La bourse aux émeraudes",
				"Vendez vos marchandises aux villageois : seuls les échanges qui rapportent des émeraudes comptent.",
				"minecraft:emerald", "Émeraudes", 8, 12, 2, 14, 20, 2, 75, 10, 0.10, 0.08, 4, 4, 4,
				List.of()));
		// ---- ENCHANT
		list.add(t("enchanteur", QuestType.ENCHANT, "L'apprenti enchanteur", "Enchantez des objets à la table d'enchantement.",
				"", "", 2, 3, 0.5, 12, 16, 2, 65, 8, 0.06, 0.05, 5, 3, 4,
				List.of(new QuestTemplate.RewardItem("minecraft:lapis_lazuli", 8, 0.6))));
		list.add(t("arcanes", QuestType.ENCHANT, "Les arcanes interdites",
				"Maîtrisez la magie : enchantez de nombreux objets à la table d'enchantement.",
				"", "", 5, 7, 1, 22, 30, 3, 120, 12, 0.15, 0.20, 4, 5, 5,
				List.of(new QuestTemplate.RewardItem("minecraft:experience_bottle", 4, 0.5))));
		// ---- ELITE / BOSS (horde mobs)
		list.add(t("elites", QuestType.ELITE, "Chasseurs d'élite",
				"Les élites de la horde brillent dans la nuit : abattez-les avant qu'elles n'atteignent le cœur.",
				"", "", 1, 2, 0.5, 16, 22, 2, 70, 10, 0.15, 0.10, 5, 2, 5,
				List.of()));
		list.add(t("champions", QuestType.ELITE, "Le tableau de chasse", "Accumulez les trophées : abattez de nombreuses élites de horde.",
				"", "", 5, 7, 1, 30, 40, 3, 140, 12, 0.30, 0.15, 4, 5, 6,
				List.of(new QuestTemplate.RewardItem("minecraft:golden_apple", 1, 0.4))));
		list.add(t("boss", QuestType.BOSS, "La tête du chef de guerre",
				"Un boss mène parfois la horde : terrassez-le pour l'honneur de la guilde.",
				"", "", 1, 1, 0, 40, 55, 4, 180, 15, 0.60, 0.30, 3, 3, 8,
				List.of(new QuestTemplate.RewardItem("minecraft:golden_apple", 1, 0.5))));
		// ---- NIGHT_HUNT
		list.add(t("nuit_blanche", QuestType.NIGHT_HUNT, "Nuit blanche",
				"Quand la lune se lève, partez à la chasse : seuls les monstres tués de nuit comptent.",
				"", "", 15, 20, 3, 10, 14, 2, 45, 6, 0.06, 0.05, 7, 1, 3,
				List.of(new QuestTemplate.RewardItem("minecraft:arrow", 16, 0.4))));
		list.add(t("veilleurs", QuestType.NIGHT_HUNT, "Les veilleurs de minuit",
				"Montez la garde nuit après nuit et éliminez tout ce qui rôde dans l'obscurité.",
				"", "", 40, 50, 5, 22, 30, 3, 100, 10, 0.20, 0.10, 4, 4, 5,
				List.of()));
		// ---- BIOME (one visit; far biomes pay more)
		list.add(icon(t("desert", QuestType.BIOME, "Mirage", "Rejoignez un désert, admirez les dunes... et revenez raconter.",
				"minecraft:desert", "Désert", 1, 1, 0, 10, 14, 2, 45, 6, 0.05, 0.20, 4, 1, 4,
				List.of()), "minecraft:cactus"));
		list.add(icon(t("jungle", QuestType.BIOME, "Au cœur de la jungle",
				"Trouvez une jungle et entrez-y : perroquets, ocelots et temples oubliés vous attendent.",
				"#minecraft:is_jungle", "Jungle", 1, 1, 0, 14, 20, 2, 60, 8, 0.10, 0.25, 4, 2, 5,
				List.of()), "minecraft:jungle_sapling"));
		list.add(icon(t("champignons", QuestType.BIOME, "L'île aux champignons",
				"Une île couverte de mycélium où aucun monstre n'apparaît... encore faut-il la trouver.",
				"minecraft:mushroom_fields", "Champs de champignons", 1, 1, 0, 35, 45, 3, 130, 10, 0.30, 0.30, 2, 4, 7,
				List.of()), "minecraft:red_mushroom"));
		list.add(icon(t("abysses", QuestType.BIOME, "Les abysses",
				"Descendez dans les profondeurs sombres... et surtout, ne réveillez pas le gardien.",
				"minecraft:deep_dark", "Abîmes", 1, 1, 0, 45, 60, 4, 170, 12, 0.40, 0.20, 2, 6, 6,
				List.of()), "minecraft:sculk"));
		// ---- COLLECTION (assortments; like DELIVER, gold below the shop price of the cheapest buyable assortment)
		list.add(collection(t("provisions", QuestType.COLLECTION, "Provisions pour l'hiver",
				"Remplissez le garde-manger de la guilde avec un assortiment de vivres.",
				"", "", 1, 1, 0, 3, 4, 0.5, 70, 8, 0.05, 0.05, 6, 1, 3,
				List.of()), 2, 3,
				new QuestTemplate.Part("minecraft:bread", "", 8, 12, 2),
				new QuestTemplate.Part("minecraft:carrot", "", 12, 16, 2),
				new QuestTemplate.Part("minecraft:potato", "", 12, 16, 2),
				new QuestTemplate.Part("minecraft:apple", "", 4, 6, 1),
				new QuestTemplate.Part("minecraft:cooked_beef", "", 4, 6, 1),
				new QuestTemplate.Part("minecraft:pumpkin_pie", "", 3, 5, 1)));
		list.add(collection(t("trophees", QuestType.COLLECTION, "Trophées de chasse",
				"Rapportez les dépouilles des monstres : le cabinet des curiosités de la guilde s'agrandit.",
				"", "", 1, 1, 0, 3, 4, 0.5, 75, 8, 0.08, 0.05, 6, 2, 3,
				List.of()), 3, 3,
				new QuestTemplate.Part("minecraft:bone", "", 8, 12, 1),
				new QuestTemplate.Part("minecraft:string", "", 8, 12, 1),
				new QuestTemplate.Part("minecraft:rotten_flesh", "", 16, 24, 2),
				new QuestTemplate.Part("minecraft:gunpowder", "", 3, 5, 1),
				new QuestTemplate.Part("minecraft:spider_eye", "", 3, 5, 1),
				new QuestTemplate.Part("minecraft:arrow", "", 16, 24, 2),
				new QuestTemplate.Part("minecraft:feather", "", 8, 12, 1)));
		list.add(collection(t("alchimiste", QuestType.COLLECTION, "Le cabinet de l'alchimiste",
				"L'alchimiste de la guilde prépare ses potions : réunissez les ingrédients qu'il réclame.",
				"", "", 1, 1, 0, 2, 3, 0.5, 95, 10, 0.10, 0.10, 5, 3, 4,
				List.of(new QuestTemplate.RewardItem("minecraft:experience_bottle", 3, 0.5))), 2, 3,
				new QuestTemplate.Part("minecraft:spider_eye", "", 3, 5, 1),
				new QuestTemplate.Part("minecraft:gunpowder", "", 3, 5, 1),
				new QuestTemplate.Part("minecraft:redstone", "", 10, 16, 2),
				new QuestTemplate.Part("minecraft:glowstone_dust", "", 8, 12, 2),
				new QuestTemplate.Part("minecraft:sugar", "", 8, 12, 2),
				new QuestTemplate.Part("minecraft:nether_wart", "", 6, 10, 1),
				new QuestTemplate.Part("minecraft:blaze_powder", "", 2, 4, 0.5)));
		list.add(collection(t("tresor_royal", QuestType.COLLECTION, "Le trésor de la couronne",
				"Pierres précieuses et curiosités rares : constituez un trésor digne d'une grande guilde.",
				"", "", 1, 1, 0, 12, 18, 1, 220, 15, 0.50, 0.40, 3, 6, 6,
				List.of()), 3, 4,
				new QuestTemplate.Part("minecraft:emerald", "", 6, 10, 1),
				new QuestTemplate.Part("minecraft:diamond", "", 2, 3, 0.5),
				new QuestTemplate.Part("minecraft:amethyst_shard", "", 12, 16, 2),
				new QuestTemplate.Part("minecraft:lapis_lazuli", "", 16, 24, 2),
				new QuestTemplate.Part("minecraft:quartz", "", 16, 24, 2),
				new QuestTemplate.Part("minecraft:ender_pearl", "", 2, 4, 0.5)));
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

	/** Icon where the automatic choice is poor (crop block item = seeds, first mob of a tag, biomes...). */
	private static QuestTemplate icon(QuestTemplate template, String icon) {
		template.icon = icon;
		return template;
	}

	private static QuestTemplate maxLevel(QuestTemplate template, int maxLevel) {
		template.maxLevel = maxLevel;
		return template;
	}

	private static QuestTemplate collection(QuestTemplate template, int min, int max, QuestTemplate.Part... parts) {
		template.collection = new QuestTemplate.Assortment(min, max, new ArrayList<>(List.of(parts)));
		return template;
	}
}
