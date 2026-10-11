package dev.guildy.upgrade;

import dev.guildy.Guildy;
import dev.guildy.config.Comment;
import dev.guildy.config.Json5;
import dev.guildy.config.Validatable;
import dev.guildy.core.ItemUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * upgrades.json5 - the guild upgrade tree ("Arbre d'evolution"). Free of Minecraft classes except in {@link #validate()}.
 */
@Comment({
		"Guildy - arbre d'evolution : ameliorations permanentes de la guilde (menu /guild upgrades).",
		"branches : [{id, name, icon}] = une ligne du menu chacune (4 par page).",
		"nodes : [{id, branch, tier (palier 1 a 8, un seul noeud par palier dans une branche), name, description (lignes,",
		"codes couleur &), icon, minLevel (niveau de guilde requis), requires (ids des ameliorations a debloquer avant),",
		"costGold (or du tresor), costItems [{item, count}] (pris dans l'inventaire du joueur qui debloque : objets simples,",
		"ni renommes, ni enchantes, ni abimes), effects {effet: valeur}}. Les valeurs d'un meme effet s'additionnent ;",
		"une amelioration retiree du fichier ne compte plus (elle revient si on la remet avec le meme id).",
		"Effets : heart_integrity_pct (+% d'integrite du coeur pendant les hordes), guard_damage_pct (+% de degats des gardes),",
		"wave_regen (niveau de Regeneration des membres dans le territoire pendant une horde), defeat_loss_reduction_pct",
		"(-% d'or perdu en cas de defaite), villager_loyalty_pct (-% de chance de depart des villageois apres une defaite),",
		"quest_reward_pct (+% d'or et d'XP des quetes ; l'or des quetes de livraison n'est jamais augmente : pas de boucle",
		"d'or avec la boutique), wave_reward_pct (+% d'or et d'XP des victoires), flag_discount_pct (-% sur le prix des",
		"drapeaux d'extension), extra_chunks (+chunks maximum), extra_villagers (+villageois maximum), work_speed_pct",
		"(-% de duree des taches des villageois, 75 maximum), territory_haste (niveau de Celerite des membres dans le territoire).",
		"Les objets, effets et prerequis inconnus sont ignores (voir la console), les prerequis circulaires sont refuses."
})
public final class UpgradeConfig implements Validatable {

	@Comment("Effets de territoire (Celerite : territory_haste, Regeneration : wave_regen)")
	public Buffs buffs = new Buffs();

	@Comment("Branches de l'arbre (voir l'explication en haut du fichier)")
	public List<UpgradeBranch> branches = defaultBranches();

	@Comment("Ameliorations (voir l'explication en haut du fichier)")
	public List<UpgradeNode> nodes = defaultNodes();

	private transient UpgradeTree tree = UpgradeTree.EMPTY;

	@Json5.Section
	public static final class Buffs {
		@Comment("Duree (secondes, 3 a 60) des effets, renouveles toutes les 2 secondes tant que le membre reste dans le territoire")
		public int durationSeconds = 5;
	}

	/** Usable tree (built by {@link #validate()}). */
	public UpgradeTree tree() {
		return tree;
	}

	@Override
	public void validate() {
		for (String warning : rebuild(id -> ItemUtil.item(id) != null)) {
			Guildy.LOGGER.warn("[upgrades.json5] {}", warning);
		}
	}

	/** Clamps the settings and rebuilds the usable tree (pure). @return the problems found */
	public List<String> rebuild(Predicate<String> itemExists) {
		if (buffs == null) {
			buffs = new Buffs();
		}
		buffs.durationSeconds = Math.max(3, Math.min(60, buffs.durationSeconds));
		if (branches == null) {
			branches = defaultBranches();
		}
		if (nodes == null) {
			nodes = defaultNodes();
		}
		List<String> warnings = new ArrayList<>();
		tree = UpgradeTree.build(branches, nodes, itemExists, warnings);
		return warnings;
	}

	// ------------------------------------------------------------------ defaults

	public static List<UpgradeBranch> defaultBranches() {
		List<UpgradeBranch> list = new ArrayList<>();
		list.add(new UpgradeBranch("defense", "Défense", "minecraft:shield"));
		list.add(new UpgradeBranch("economie", "Économie", "minecraft:gold_ingot"));
		list.add(new UpgradeBranch("expansion", "Expansion", "minecraft:map"));
		return list;
	}

	public static List<UpgradeNode> defaultNodes() {
		List<UpgradeNode> list = new ArrayList<>();
		// ---- Défense
		list.add(node("coeur_renforce", "defense", 1, "Cœur renforcé", "minecraft:heart_of_the_sea", 1, 30, null,
				lines("&7Des renforts de fer consolident", "&7le cœur de la base."),
				effects(UpgradeEffects.HEART_INTEGRITY_PCT, 25),
				cost("minecraft:iron_ingot", 8)));
		list.add(node("gardes_aguerris", "defense", 2, "Gardes aguerris", "minecraft:iron_sword", 2, 60, "coeur_renforce",
				lines("&7Les gardes villageois s'entraînent", "&7au maniement des armes."),
				effects(UpgradeEffects.GUARD_DAMAGE_PCT, 50),
				cost("minecraft:iron_block", 2)));
		list.add(node("banniere_ralliement", "defense", 3, "Bannière de ralliement", "minecraft:red_banner", 3, 100, "gardes_aguerris",
				lines("&7La bannière galvanise les membres", "&7qui défendent le territoire la nuit."),
				effects(UpgradeEffects.WAVE_REGEN, 1),
				cost("minecraft:golden_apple", 2)));
		list.add(node("bastion", "defense", 4, "Bastion", "minecraft:totem_of_undying", 5, 200, "banniere_ralliement",
				lines("&7La base devient une forteresse :", "&7même vaincue, elle ne plie pas."),
				effects(UpgradeEffects.HEART_INTEGRITY_PCT, 50, UpgradeEffects.DEFEAT_LOSS_REDUCTION_PCT, 50,
						UpgradeEffects.VILLAGER_LOYALTY_PCT, 100),
				cost("minecraft:diamond", 4)));
		// ---- Économie
		list.add(node("primes_quetes", "economie", 1, "Primes de quêtes", "minecraft:writable_book", 1, 25, null,
				lines("&7Les commanditaires paient mieux", "&7les guildes de confiance."),
				effects(UpgradeEffects.QUEST_REWARD_PCT, 20)));
		list.add(node("butin_guerre", "economie", 2, "Butin de guerre", "minecraft:chest", 2, 50, "primes_quetes",
				lines("&7Les hordes repoussées laissent", "&7davantage de richesses."),
				effects(UpgradeEffects.WAVE_REWARD_PCT, 25),
				cost("minecraft:emerald", 8)));
		list.add(node("cartographes", "economie", 3, "Cartographes", "minecraft:cartography_table", 3, 80, "butin_guerre",
				lines("&7Des cartographes négocient le prix", "&7des drapeaux d'extension."),
				effects(UpgradeEffects.FLAG_DISCOUNT_PCT, 30),
				cost("minecraft:paper", 16)));
		list.add(node("age_or", "economie", 4, "Âge d'or", "minecraft:gold_block", 5, 200, "cartographes",
				lines("&7La renommée de la guilde attire", "&7l'or de tout le royaume."),
				effects(UpgradeEffects.QUEST_REWARD_PCT, 20, UpgradeEffects.WAVE_REWARD_PCT, 25),
				cost("minecraft:diamond_block", 1)));
		// ---- Expansion
		list.add(node("frontieres_elargies", "expansion", 1, "Frontières élargies", "minecraft:oak_fence", 1, 30, null,
				lines("&7Les frontières de la guilde", "&7repoussent l'horizon."),
				effects(UpgradeEffects.EXTRA_CHUNKS, 4)));
		list.add(node("logements", "expansion", 2, "Logements", "minecraft:red_bed", 2, 60, "frontieres_elargies",
				lines("&7De nouveaux logis pour accueillir", "&7plus de villageois."),
				effects(UpgradeEffects.EXTRA_VILLAGERS, 2),
				cost("minecraft:white_bed", 3)));
		list.add(node("artisans_efficaces", "expansion", 3, "Artisans efficaces", "minecraft:iron_pickaxe", 3, 100, "logements",
				lines("&7Outils affûtés et méthodes éprouvées :", "&7les villageois travaillent plus vite."),
				effects(UpgradeEffects.WORK_SPEED_PCT, 30),
				cost("minecraft:iron_ingot", 16)));
		list.add(node("grand_domaine", "expansion", 4, "Grand domaine", "minecraft:beacon", 5, 200, "artisans_efficaces",
				lines("&7Un domaine immense où chacun", "&7œuvre avec ardeur."),
				effects(UpgradeEffects.EXTRA_CHUNKS, 8, UpgradeEffects.EXTRA_VILLAGERS, 2, UpgradeEffects.TERRITORY_HASTE, 1),
				cost("minecraft:diamond", 4)));
		return list;
	}

	private static UpgradeNode node(String id, String branch, int tier, String name, String icon, int minLevel, int gold,
			String requires, List<String> description, Map<String, Double> effects, UpgradeNode.ItemCost... costs) {
		UpgradeNode node = new UpgradeNode();
		node.id = id;
		node.branch = branch;
		node.tier = tier;
		node.name = name;
		node.icon = icon;
		node.minLevel = minLevel;
		node.costGold = gold;
		node.requires = requires == null ? new ArrayList<>() : new ArrayList<>(List.of(requires));
		node.description = description;
		node.effects = effects;
		node.costItems = new ArrayList<>(List.of(costs));
		return node;
	}

	private static List<String> lines(String... lines) {
		return new ArrayList<>(List.of(lines));
	}

	private static Map<String, Double> effects(Object... keyValues) {
		Map<String, Double> map = new LinkedHashMap<>();
		for (int i = 0; i + 1 < keyValues.length; i += 2) {
			map.put((String) keyValues[i], ((Number) keyValues[i + 1]).doubleValue());
		}
		return map;
	}

	private static UpgradeNode.ItemCost cost(String item, int count) {
		return new UpgradeNode.ItemCost(item, count);
	}
}
