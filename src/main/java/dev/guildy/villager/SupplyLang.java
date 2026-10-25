package dev.guildy.villager;

import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * French defaults of the builders' supply work (prefixes {@code villager.gather.} for the statuses and reasons,
 * {@code supply.} for the actions, places and item names) and the plain-text names of the materials: statuses are plain
 * text and the server only knows the English names, so the usual building materials have a French name here
 * ({@code supply.item.<id>}), the others are composed from a shape and a material ("Escalier en chêne"), else the
 * blacksmith's names ({@link CraftNames}), else the vanilla name.
 */
final class SupplyLang {
	/** Shapes composed with a material: suffix of the item id → lang key of the pattern. */
	private static final List<String> SHAPES = List.of("pressure_plate", "fence_gate", "hanging_sign", "stairs", "slab", "wall", "fence",
			"door", "trapdoor", "button", "sign", "planks", "log", "wood");

	/** Dyed families composed with a colour, longest suffix first ("Terre cuite émaillée rouge"). */
	private static final List<String> COLORED = List.of("stained_glass_pane", "glazed_terracotta", "concrete_powder", "stained_glass",
			"shulker_box", "terracotta", "concrete", "carpet", "candle", "banner", "wool", "bed", "dye");
	private static final java.util.Set<String> FEMININE = java.util.Set.of("stained_glass_pane", "glazed_terracotta", "terracotta", "candle",
			"banner", "wool", "shulker_box");

	private SupplyLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// role status: "↪ Matériaux : 64 Verre → cuit le Sable (12/64)"
		m.put("villager.gather.job", "&6↪ Matériaux : &f{0} &7→ {1}");
		m.put("villager.gather.amount", "{0} {1}");
		// reasons (GatherWork.blocked and end of a trip)
		m.put("villager.gather.unavailable", "&7Rien à chercher pour les chantiers");
		m.put("villager.gather.nothing", "&7Aucun matériau ne manque aux chantiers");
		m.put("villager.gather.disabled", "&7Approvisionnement des chantiers désactivé sur ce serveur");
		m.put("villager.gather.taken", "&7Les autres bâtisseurs cherchent déjà tout ce qui manque");
		m.put("villager.gather.missing", "&cIl manque {0} : à déposer au stockage{1}");
		m.put("villager.gather.missing.more", "&8 (+{0} autre(s))");
		m.put("villager.gather.missing.because", "&8 (faute de {0})");
		m.put("villager.gather.missing.fuel", "combustible");
		m.put("villager.gather.no_table", "&cAucun établi dans le territoire pour fabriquer {0} : posez-en un");
		m.put("villager.gather.no_furnace", "&cAucun four dans le territoire pour cuire {0} : posez-en un");
		m.put("villager.gather.no_stonecutter", "&cAucun tailleur de pierre dans le territoire pour tailler {0} : posez-en un");
		m.put("villager.gather.no_water", "&cAucune source d'eau dans le territoire pour {0} : creusez-en une");
		m.put("villager.gather.no_site", "&cAucun gisement de {0} trouvé (zones de mine, {1} blocs autour du territoire) : à déposer au stockage");
		m.put("villager.gather.no_tree", "&cAucun arbre naturel de {0} à portée (zones de coupe, {1} blocs autour du territoire) : à déposer au stockage");
		m.put("villager.gather.no_territory", "&cLa guilde n'a pas de territoire");
		m.put("villager.gather.scanning", "&7Repère les établis et les fours du territoire &8({0}/{1})");
		m.put("villager.gather.storage_full", "&cStockage plein : impossible de ranger {0}");
		m.put("villager.gather.no_storage", "&cAucun coffre de stockage pour ranger les matériaux");
		m.put("villager.gather.delivered", "&a{0} livré(s) au stockage");
		m.put("villager.gather.failed", "&7Matériaux : {0} abandonné ({1})");
		// actions
		m.put("supply.action.stow", "range son sac au stockage");
		m.put("supply.action.fetch", "prend {0} au stockage");
		m.put("supply.action.go.table", "va à l'établi");
		m.put("supply.action.go.furnace", "va au four");
		m.put("supply.action.go.stonecutter", "va au tailleur de pierre");
		m.put("supply.action.go.water", "va au point d'eau");
		m.put("supply.action.craft", "fabrique {0} à l'établi &8({1}/{2})");
		m.put("supply.action.hand_craft", "fabrique {0} &8({1}/{2})");
		m.put("supply.action.smelt", "cuit {0} &8({1}/{2})");
		m.put("supply.action.cut", "taille {0} &8({1}/{2})");
		m.put("supply.action.transform.axe", "écorce {0} &8({2}/{3})");
		m.put("supply.action.transform.shovel", "aplanit {0} en {1} &8({2}/{3})");
		m.put("supply.action.transform.hoe", "laboure {0} &8({2}/{3})");
		m.put("supply.action.transform.water", "trempe {0} dans l'eau &8({2}/{3})");
		m.put("supply.action.survey", "cherche un gisement de {0} &8({1} site(s) vu(s))");
		m.put("supply.action.pit_go", "se rend à la fosse {0}");
		m.put("supply.action.dig", "creuse la fosse {0} &8({1}/{2}, niveau {3})");
		m.put("supply.action.seal", "colmate l'eau dans la fosse");
		m.put("supply.action.tree_search", "cherche un arbre de {0} &8({1}/{2})");
		m.put("supply.action.tree_go", "se rend à un arbre {0}");
		m.put("supply.action.tree_cut", "coupe un arbre {0} &8({1}/{2})");
		m.put("supply.action.deliver", "livre {0} au stockage");
		m.put("supply.action.wait", "attend {0}");
		m.put("supply.place.outside", "hors du territoire");
		m.put("supply.place.zone", "dans « {0} »");
		// reasons of a trip given up (shown in the log line of the status)
		m.put("supply.fail.no_site", "aucun gisement à portée");
		m.put("supply.fail.no_tree", "aucun arbre à portée");
		m.put("supply.fail.station", "atelier introuvable");
		m.put("supply.fail.stock", "le stockage n'en a plus");
		m.put("supply.fail.unloaded", "zone hors de portée (aucun joueur à proximité)");
		// placeholder task of this branch (the construction sites define the real one)
		if (!Lang.has("villager.task.builder")) {
			m.put("villager.task.builder", "Bâtisseur");
			m.put("villager.task.builder.desc", "&7Va chercher les matériaux qui manquent\n&7aux chantiers de la guilde : fabrique,\n&7cuit ou récolte, puis dépose au stockage.");
		}
		names(m);
		Lang.addDefaults(m);
	}

	// ------------------------------------------------------------------ names

	/** "64 Verre". */
	static String amount(int count, Item item) {
		return Lang.raw("villager.gather.amount", count, name(item));
	}

	/** Plain-text French name of a material. */
	static String name(Item item) {
		String id = ItemUtil.id(item);
		String path = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : null;
		if (path != null) {
			if (Lang.has("supply.item." + path)) {
				return Lang.raw("supply.item." + path);
			}
			for (String family : COLORED) {
				if (path.endsWith("_" + family)) {
					String color = path.substring(0, path.length() - family.length() - 1);
					boolean feminine = FEMININE.contains(family);
					String key = "supply.color." + (feminine ? "f." : "") + color;
					if (Lang.has(key)) {
						return Lang.raw("supply.family." + family, Lang.raw(key));
					}
				}
			}
			for (String shape : SHAPES) {
				if (path.endsWith("_" + shape)) {
					String base = path.substring(0, path.length() - shape.length() - 1);
					boolean stripped = base.startsWith("stripped_");
					if (stripped) {
						base = base.substring("stripped_".length());
					}
					if (Lang.has("supply.material." + base)) {
						String name = Lang.raw("supply.shape." + shape, Lang.raw("supply.material." + base));
						return stripped ? Lang.raw("supply.stripped", name) : name;
					}
				}
			}
		}
		return CraftNames.item(new ItemStack(item));
	}

	private static void names(Map<String, String> m) {
		String[][] colors = {
				{"white", "blanc", "blanche"}, {"orange", "orange", "orange"}, {"magenta", "magenta", "magenta"},
				{"light_blue", "bleu clair", "bleu clair"}, {"yellow", "jaune", "jaune"}, {"lime", "vert clair", "vert clair"},
				{"pink", "rose", "rose"}, {"gray", "gris", "grise"}, {"light_gray", "gris clair", "gris clair"}, {"cyan", "cyan", "cyan"},
				{"purple", "violet", "violette"}, {"blue", "bleu", "bleue"}, {"brown", "marron", "marron"}, {"green", "vert", "verte"},
				{"red", "rouge", "rouge"}, {"black", "noir", "noire"}};
		for (String[] color : colors) {
			m.put("supply.color." + color[0], color[1]);
			m.put("supply.color.f." + color[0], color[2]);
		}
		m.put("supply.family.concrete", "Béton {0}");
		m.put("supply.family.concrete_powder", "Béton en poudre {0}");
		m.put("supply.family.terracotta", "Terre cuite {0}");
		m.put("supply.family.glazed_terracotta", "Terre cuite émaillée {0}");
		m.put("supply.family.wool", "Laine {0}");
		m.put("supply.family.carpet", "Tapis {0}");
		m.put("supply.family.stained_glass", "Verre {0}");
		m.put("supply.family.stained_glass_pane", "Vitre {0}");
		m.put("supply.family.candle", "Bougie {0}");
		m.put("supply.family.bed", "Lit {0}");
		m.put("supply.family.banner", "Bannière {0}");
		m.put("supply.family.shulker_box", "Boîte de Shulker {0}");
		m.put("supply.family.dye", "Colorant {0}");
		m.put("supply.stripped", "{0} écorcé(e)");
		m.put("supply.shape.stairs", "Escalier en {0}");
		m.put("supply.shape.slab", "Dalle en {0}");
		m.put("supply.shape.wall", "Muret en {0}");
		m.put("supply.shape.fence", "Barrière en {0}");
		m.put("supply.shape.fence_gate", "Portillon en {0}");
		m.put("supply.shape.door", "Porte en {0}");
		m.put("supply.shape.trapdoor", "Trappe en {0}");
		m.put("supply.shape.button", "Bouton en {0}");
		m.put("supply.shape.pressure_plate", "Plaque de pression en {0}");
		m.put("supply.shape.sign", "Panneau en {0}");
		m.put("supply.shape.hanging_sign", "Panneau suspendu en {0}");
		m.put("supply.shape.planks", "Planches de {0}");
		m.put("supply.shape.log", "Bûche de {0}");
		m.put("supply.shape.wood", "Bois de {0}");
		String[][] materials = {
				{"oak", "chêne"}, {"spruce", "sapin"}, {"birch", "bouleau"}, {"jungle", "acajou"}, {"acacia", "acacia"},
				{"dark_oak", "chêne noir"}, {"mangrove", "palétuvier"}, {"cherry", "cerisier"}, {"pale_oak", "chêne pâle"},
				{"bamboo", "bambou"}, {"crimson", "carmin"}, {"warped", "biscornu"},
				{"stone", "roche"}, {"cobblestone", "pierre taillée"}, {"mossy_cobblestone", "pierre taillée moussue"},
				{"stone_brick", "briques de pierre"}, {"mossy_stone_brick", "briques de pierre moussues"}, {"smooth_stone", "roche lisse"},
				{"brick", "briques"}, {"mud_brick", "briques de boue"}, {"sandstone", "grès"}, {"smooth_sandstone", "grès lisse"},
				{"cut_sandstone", "grès taillé"}, {"red_sandstone", "grès rouge"}, {"smooth_red_sandstone", "grès rouge lisse"},
				{"cut_red_sandstone", "grès rouge taillé"}, {"granite", "granite"}, {"polished_granite", "granite poli"},
				{"diorite", "diorite"}, {"polished_diorite", "diorite polie"}, {"andesite", "andésite"},
				{"polished_andesite", "andésite polie"}, {"cobbled_deepslate", "ardoise des abîmes taillée"},
				{"polished_deepslate", "ardoise des abîmes polie"}, {"deepslate_brick", "briques d'ardoise des abîmes"},
				{"deepslate_tile", "carreaux d'ardoise des abîmes"}, {"tuff", "tuf"}, {"polished_tuff", "tuf poli"},
				{"tuff_brick", "briques de tuf"}, {"blackstone", "pierre noire"}, {"polished_blackstone", "pierre noire polie"},
				{"polished_blackstone_brick", "briques de pierre noire polie"}, {"nether_brick", "briques du Nether"},
				{"red_nether_brick", "briques rouges du Nether"}, {"quartz", "quartz"}, {"smooth_quartz", "quartz lisse"},
				{"prismarine", "prismarine"}, {"prismarine_brick", "briques de prismarine"}, {"dark_prismarine", "prismarine sombre"},
				{"purpur", "purpur"}, {"end_stone_brick", "briques de pierre de l'End"}, {"resin_brick", "briques de résine"},
				{"iron", "fer"}, {"copper", "cuivre"}};
		for (String[] material : materials) {
			m.put("supply.material." + material[0], material[1]);
		}
		String[][] items = {
				{"stone", "Roche"}, {"cobblestone", "Pierre taillée"}, {"mossy_cobblestone", "Pierre taillée moussue"},
				{"smooth_stone", "Roche lisse"}, {"stone_bricks", "Briques de pierre"}, {"mossy_stone_bricks", "Briques de pierre moussues"},
				{"cracked_stone_bricks", "Briques de pierre craquelées"}, {"chiseled_stone_bricks", "Briques de pierre sculptées"},
				{"granite", "Granite"}, {"polished_granite", "Granite poli"}, {"diorite", "Diorite"}, {"polished_diorite", "Diorite polie"},
				{"andesite", "Andésite"}, {"polished_andesite", "Andésite polie"}, {"deepslate", "Ardoise des abîmes"},
				{"cobbled_deepslate", "Ardoise des abîmes taillée"}, {"polished_deepslate", "Ardoise des abîmes polie"},
				{"deepslate_bricks", "Briques d'ardoise des abîmes"}, {"deepslate_tiles", "Carreaux d'ardoise des abîmes"},
				{"tuff", "Tuf"}, {"calcite", "Calcite"}, {"dripstone_block", "Bloc de spéléothème"}, {"smooth_basalt", "Basalte lisse"},
				{"sandstone", "Grès"}, {"cut_sandstone", "Grès taillé"}, {"smooth_sandstone", "Grès lisse"}, {"chiseled_sandstone", "Grès sculpté"},
				{"red_sandstone", "Grès rouge"}, {"sand", "Sable"}, {"red_sand", "Sable rouge"}, {"gravel", "Gravier"}, {"flint", "Silex"},
				{"dirt", "Terre"}, {"coarse_dirt", "Terre stérile"}, {"rooted_dirt", "Terre racinée"}, {"grass_block", "Bloc d'herbe"},
				{"podzol", "Podzol"}, {"mycelium", "Mycélium"}, {"moss_block", "Bloc de mousse"}, {"mud", "Boue"},
				{"packed_mud", "Boue compacte"}, {"mud_bricks", "Briques de boue"}, {"clay", "Bloc d'argile"}, {"clay_ball", "Boule d'argile"},
				{"brick", "Brique"}, {"bricks", "Briques"}, {"terracotta", "Terre cuite"}, {"glass", "Verre"}, {"glass_pane", "Vitre"},
				{"tinted_glass", "Verre teinté"}, {"snow_block", "Bloc de neige"}, {"snowball", "Boule de neige"}, {"ice", "Glace"},
				{"packed_ice", "Glace compacte"}, {"magma_block", "Bloc de magma"},
				{"stick", "Bâton"}, {"torch", "Torche"}, {"lantern", "Lanterne"}, {"crafting_table", "Établi"}, {"furnace", "Four"},
				{"chest", "Coffre"}, {"barrel", "Tonneau"}, {"ladder", "Échelle"}, {"bookshelf", "Bibliothèque"}, {"scaffolding", "Échafaudage"},
				{"coal", "Charbon"}, {"charcoal", "Charbon de bois"}, {"coal_block", "Bloc de charbon"}, {"coal_ore", "Minerai de charbon"},
				{"iron_ingot", "Lingot de fer"}, {"iron_nugget", "Pépite de fer"}, {"iron_block", "Bloc de fer"}, {"raw_iron", "Fer brut"},
				{"iron_ore", "Minerai de fer"}, {"iron_bars", "Barreaux de fer"}, {"chain", "Chaîne"}, {"gold_ingot", "Lingot d'or"},
				{"gold_block", "Bloc d'or"}, {"raw_gold", "Or brut"}, {"copper_ingot", "Lingot de cuivre"}, {"copper_block", "Bloc de cuivre"},
				{"raw_copper", "Cuivre brut"}, {"diamond", "Diamant"}, {"diamond_block", "Bloc de diamant"}, {"emerald", "Émeraude"},
				{"emerald_block", "Bloc d'émeraude"}, {"lapis_lazuli", "Lapis-lazuli"}, {"lapis_block", "Bloc de lapis-lazuli"},
				{"redstone", "Poudre de redstone"}, {"redstone_block", "Bloc de redstone"}, {"quartz", "Quartz du Nether"},
				{"quartz_block", "Bloc de quartz"}, {"amethyst_shard", "Éclat d'améthyste"}, {"netherrack", "Netherrack"},
				{"obsidian", "Obsidienne"}, {"white_wool", "Laine blanche"}, {"paper", "Papier"}, {"book", "Livre"}, {"string", "Ficelle"},
				{"wheat", "Blé"}, {"hay_block", "Botte de foin"}, {"oak_sapling", "Pousse de chêne"}, {"apple", "Pomme"},
				{"dirt_path", "Chemin de terre"}, {"honeycomb", "Rayon de miel"}, {"stonecutter", "Tailleur de pierre"},
				{"nether_bricks", "Briques du Nether"}, {"cracked_deepslate_bricks", "Briques d'ardoise des abîmes craquelées"},
				{"cracked_nether_bricks", "Briques du Nether craquelées"}, {"chiseled_deepslate", "Ardoise des abîmes sculptée"},
				{"bamboo_block", "Bloc de bambou"}, {"stripped_bamboo_block", "Bloc de bambou écorcé"}, {"bone_meal", "Poudre d'os"},
				{"dandelion", "Pissenlit"}, {"poppy", "Coquelicot"}};
		for (String[] item : items) {
			m.put("supply.item." + item[0], item[1]);
		}
	}
}
