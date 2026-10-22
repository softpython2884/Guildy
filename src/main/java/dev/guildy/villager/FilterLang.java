package dev.guildy.villager;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the filter chests, the in-chest layout, the chat scan of the containers
 * ({@code /guild villagers storage ...}) and the family names of the filters.
 */
final class FilterLang {
	private FilterLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// roles, names
		m.put("villager.storage.role.filter", "Coffres filtrés");
		m.put("villager.storage.label.filter", "{0}");
		m.put("villager.storage.filter.name.and", " et ");
		m.put("villager.storage.filter.name.comma", ", ");
		m.put("villager.storage.filter.name.others", " et {0} autres");
		m.put("villager.storage.filter.mode.items", "liste exacte de {0} objet(s)");
		m.put("villager.storage.filter.mode.material", "famille de matière « {0} »");
		m.put("villager.storage.filter.mode.type", "famille de type « {0} »");
		// in-chest layout
		m.put("villager.storage.layout.columns", "par colonnes");
		m.put("villager.storage.layout.rows", "par lignes");
		m.put("villager.storage.layout.changed", "&aRangement dans les coffres : &e{0}&a. Les trieurs réorganisent les coffres.");
		m.put("villager.storage.layout.unknown", "&cRangement inconnu « {0} » : colonnes ou lignes.");
		m.put("villager.storage.layout.current", "&7Rangement dans les coffres : &e{0}&7. Changer : &f/guild villagers storage layout <colonnes|lignes>");
		m.put("villager.gui.storage.layout", "&b&lRangement dans le coffre : &e{0}");
		m.put("villager.gui.storage.layout.lore", "&7Par colonnes : chaque colonne de haut en\n&7bas, puis la suivante à droite ; chaque\n&7nouvel objet commence en haut d'une\n&7nouvelle colonne. Par lignes : les\n&7emplacements dans l'ordre de lecture.\n&eClic : passer {0}");
		// storage menu
		m.put("villager.gui.storage.entry.lore.filter", "&7Coffre filtré : {0}");
		m.put("villager.gui.storage.entry.lore.filter.examples", "&8Accepte par exemple : {0}");
		m.put("villager.gui.role.filter", "&d&lCoffre filtré (d'après son contenu)");
		m.put("villager.gui.role.filter.lore", "&7N'accepte que les objets qu'il contient\n&7déjà, ou leur famille (« diorite » : diorite,\n&7diorite polie, dalles, escaliers, murets...).\n&7Les trieurs y rangent ces objets avant\n&7leur coffre de catégorie et sortent les\n&7autres. Rangez-y d'abord quelques objets.");
		m.put("villager.gui.role.choose_filter", "&eClic : filtrer d'après le contenu actuel");
		m.put("villager.gui.storage.held.filters", "&dCoffres filtrés qui l'acceptent : &f{0} &8(prioritaires)");
		m.put("villager.storage.commands.help", "&6Stockage (chat) :\n&f/guild stockage scan <chunks> [remplacer]&7 : analyse les coffres autour de vous et propose des filtres\n&f/guild stockage filtre ici&7 | &fretirer&7 : filtre du coffre regardé, d'après son contenu\n&f/guild stockage rangement <colonnes|lignes>&7 : rangement dans les coffres");
		// filter commands
		m.put("villager.storage.filter.no_target", "&cRegardez un coffre, un tonneau ou une boîte de Shulker du territoire (à 6 blocs au plus).");
		m.put("villager.storage.filter.empty", "&cCe conteneur est vide : rangez-y d'abord les objets qu'il doit accepter.");
		m.put("villager.storage.filter.too_mixed", "&cTrop d'objets différents ({0}) sans famille commune pour un filtre ({1} au plus).");
		m.put("villager.storage.filter.set", "&aCoffre filtré : &d");
		m.put("villager.storage.filter.set.detail", "&7 ({0}). Les trieurs y rangent ces objets en premier et sortent les autres.");
		m.put("villager.storage.filter.limit", "&cLimite de {0} coffres filtrés atteinte.");
		m.put("villager.storage.filter.cleared", "&7Filtre retiré : le conteneur quitte le stockage (ses objets restent dedans).");
		m.put("villager.storage.filter.none", "&cCe conteneur n'est pas un coffre filtré.");
		m.put("villager.storage.filter.disabled", "&cCoffres filtrés désactivés sur ce serveur (storage.maxFilterChests = 0).");
		// chat scan
		m.put("villager.storage.scan.usage", "&7Usage : &f/guild villagers storage scan <chunks> [remplacer]&7 (rayon de 1 à {0} chunks autour de vous).");
		m.put("villager.storage.scan.wrong_level", "&cLe stockage n'existe que dans le monde du territoire.");
		m.put("villager.storage.scan.no_chunk", "&cAucun chunk de votre territoire dans ce rayon.");
		m.put("villager.storage.scan.started", "&7Analyse des conteneurs du territoire dans un rayon de &f{0}&7 chunk(s) ({1} chunk(s) de la guilde)...");
		m.put("villager.storage.scan.done", "&aAnalyse terminée : &f{0}&a conteneur(s) lu(s).");
		m.put("villager.storage.scan.counts", "&7Familles : &d{0}&7 · Listes d'objets : &d{1}&7 · Catégories : &e{2}&7 · Trop mélangés : &c{3}&7 · Vides : &8{4}&7 · Déjà enregistrés : &8{5}");
		m.put("villager.storage.scan.unloaded", "&8{0} chunk(s) non chargé(s) ignoré(s).");
		m.put("villager.storage.scan.truncated", "&6Limite de {0} conteneurs lus atteinte : réduisez le rayon pour le reste.");
		m.put("villager.storage.scan.list.family", "&7Familles : ");
		m.put("villager.storage.scan.list.items", "&7Listes : ");
		m.put("villager.storage.scan.list.category", "&7Catégories : ");
		m.put("villager.storage.scan.list.mixed", "&7Mélangés (laissés tels quels) : ");
		m.put("villager.storage.scan.list.more", "&8 ... et {0} autre(s)");
		m.put("villager.storage.scan.hover.pos", "&7Conteneur en &f{0} {1} {2}");
		m.put("villager.storage.scan.hover.double", "&8(coffre double)");
		m.put("villager.storage.scan.hover.filter", "&7Coffre filtré : &d");
		m.put("villager.storage.scan.hover.accepts.items", "&7Accepte exactement ces {0} objet(s)");
		m.put("villager.storage.scan.hover.accepts.material", "&7Accepte tous les objets en « {0} »\n&7(blocs, polis, dalles, escaliers, murets...)");
		m.put("villager.storage.scan.hover.accepts.type", "&7Accepte tous les objets « {0} »");
		m.put("villager.storage.scan.hover.category", "&7Coffre de catégorie : &e{0}");
		m.put("villager.storage.scan.hover.mixed", "&7Trop d'objets différents sans catégorie\n&7principale : laissé tel quel.");
		m.put("villager.storage.scan.hover.empty", "&7Vide : laissé tel quel.");
		m.put("villager.storage.scan.hover.items", "&7Objets vus :");
		m.put("villager.storage.scan.hover.item", "&8- ");
		m.put("villager.storage.scan.hover.item.count", "&8 ×{0}");
		m.put("villager.storage.scan.hover.more_items", "&8... et {0} autre(s)");
		m.put("villager.storage.scan.hover.replaces", "&6Remplace le rôle actuel : {0}");
		m.put("villager.storage.scan.hover.click", "&eClic : afficher ce conteneur");
		m.put("villager.storage.scan.button.apply", "&a&l[Appliquer]");
		m.put("villager.storage.scan.button.apply.hover", "&7Enregistrer d'un coup ces {0} coffre(s)\n&7(filtres, catégories et noms des coffres).");
		m.put("villager.storage.scan.button.cancel", "&c&l[Annuler]");
		m.put("villager.storage.scan.button.cancel.hover", "&7Ne rien enregistrer.");
		m.put("villager.storage.scan.button.show", "&b&l[Afficher]");
		m.put("villager.storage.scan.button.show.hover", "&7Particules au-dessus des conteneurs\n&7analysés (violet : filtre, jaune :\n&7catégorie, rouge : mélangé, gris : vide).");
		m.put("villager.storage.scan.valid", "&8 (valable {0} s)");
		m.put("villager.storage.scan.nothing", "&7Aucun coffre à enregistrer.");
		m.put("villager.storage.scan.running", "&7Analyse en cours, patientez...");
		m.put("villager.storage.scan.none", "&cAucun aperçu en attente : lancez d'abord &f/guild villagers storage scan <chunks>&c.");
		m.put("villager.storage.scan.expired", "&cCet aperçu a expiré : relancez l'analyse.");
		m.put("villager.storage.scan.cancelled", "&7Aperçu annulé : rien n'a été enregistré.");
		m.put("villager.storage.scan.applied", "&a{0} coffre(s) filtré(s) et {1} coffre(s) de catégorie enregistré(s). Les trieurs les rangent maintenant.");
		m.put("villager.storage.scan.applied.skipped", "&7{0} conteneur(s) ignoré(s) (cassé(s), hors du territoire ou enregistré(s) entre-temps).");
		m.put("villager.storage.scan.applied.limit", "&6Limite atteinte : {0} conteneur(s) non enregistré(s) (maxFilterChests, maxChests ou maxRoomChests dans villagers.json5).");
		m.put("villager.storage.scan.shown", "&7Conteneur affiché pendant {0} s.");
		// family names (filters whose family has no item of that name: "minecraft:oak" does not exist)
		family(m, "oak", "Chêne");
		family(m, "spruce", "Sapin");
		family(m, "birch", "Bouleau");
		family(m, "jungle", "Acajou");
		family(m, "acacia", "Acacia");
		family(m, "dark_oak", "Chêne noir");
		family(m, "mangrove", "Palétuvier");
		family(m, "cherry", "Cerisier");
		family(m, "pale_oak", "Chêne pâle");
		family(m, "bamboo", "Bambou");
		family(m, "crimson", "Carmin");
		family(m, "warped", "Biscornu");
		family(m, "copper", "Cuivre");
		family(m, "iron", "Fer");
		family(m, "gold", "Or");
		family(m, "netherite", "Netherite");
		family(m, "lapis", "Lapis-lazuli");
		family(m, "amethyst", "Améthyste");
		family(m, "nether", "Briques du Nether");
		family(m, "red_nether", "Briques rouges du Nether");
		family(m, "purpur", "Purpur");
		family(m, "wheat", "Blé et graines");
		family(m, "beetroot", "Betteraves et graines");
		family(m, "melon", "Pastèque");
		family(m, "pumpkin", "Citrouille");
		family(m, "white", "Blanc");
		family(m, "orange", "Orange");
		family(m, "magenta", "Magenta");
		family(m, "light_blue", "Bleu clair");
		family(m, "yellow", "Jaune");
		family(m, "lime", "Vert clair");
		family(m, "pink", "Rose");
		family(m, "gray", "Gris");
		family(m, "light_gray", "Gris clair");
		family(m, "cyan", "Cyan");
		family(m, "purple", "Violet");
		family(m, "blue", "Bleu");
		family(m, "brown", "Marron");
		family(m, "green", "Vert");
		family(m, "red", "Rouge");
		family(m, "black", "Noir");
		family(m, "wool", "Laine");
		family(m, "carpet", "Tapis");
		family(m, "concrete", "Béton");
		family(m, "concrete_powder", "Béton en poudre");
		family(m, "terracotta", "Terre cuite");
		family(m, "glass", "Verre");
		family(m, "glass_pane", "Vitres");
		family(m, "bed", "Lits");
		family(m, "banner", "Bannières");
		family(m, "candle", "Bougies");
		family(m, "shulker_box", "Boîtes de Shulker");
		family(m, "dye", "Colorants");
		family(m, "seeds", "Graines");
		family(m, "sapling", "Pousses");
		family(m, "leaves", "Feuilles");
		family(m, "log", "Bûches");
		family(m, "wood", "Bois");
		family(m, "planks", "Planches");
		family(m, "slab", "Dalles");
		family(m, "stairs", "Escaliers");
		family(m, "wall", "Murets");
		family(m, "fence", "Barrières");
		family(m, "fence_gate", "Portillons");
		family(m, "door", "Portes");
		family(m, "trapdoor", "Trappes");
		family(m, "button", "Boutons");
		family(m, "pressure_plate", "Plaques de pression");
		family(m, "sign", "Panneaux");
		family(m, "hanging_sign", "Panneaux suspendus");
		family(m, "brick", "Briques");
		family(m, "ore", "Minerais");
		family(m, "ingot", "Lingots");
		family(m, "nugget", "Pépites");
		family(m, "sword", "Épées");
		family(m, "pickaxe", "Pioches");
		family(m, "axe", "Haches");
		family(m, "shovel", "Pelles");
		family(m, "hoe", "Houes");
		family(m, "spear", "Lances");
		family(m, "helmet", "Casques");
		family(m, "chestplate", "Plastrons");
		family(m, "leggings", "Jambières");
		family(m, "boots", "Bottes");
		family(m, "horse_armor", "Armures de cheval");
		family(m, "boat", "Bateaux");
		family(m, "chest_boat", "Bateaux avec coffre");
		family(m, "spawn_egg", "Œufs d'apparition");
		family(m, "egg", "Œufs");
		family(m, "music_disc", "Disques");
		family(m, "pottery_sherd", "Tessons de poterie");
		family(m, "smithing_template", "Modèles de forge");
		family(m, "banner_pattern", "Motifs de bannière");
		family(m, "coral", "Coraux");
		family(m, "tulip", "Tulipes");
		family(m, "lantern", "Lanternes");
		family(m, "torch", "Torches");
		family(m, "bundle", "Sacs");
		family(m, "harness", "Harnais");
		family(m, "bucket", "Seaux");
		family(m, "potion", "Potions");
		family(m, "arrow", "Flèches");
		filteredRooms(m);
		Lang.addDefaults(m);
	}

	/** Filtered rooms: each chest receives the kinds of items it already holds. */
	private static void filteredRooms(Map<String, String> m) {
		m.put("villager.storage.role.content", "Filtré (son contenu)");
		m.put("villager.storage.label.content", "Filtré");
		m.put("villager.storage.content.more", " +{0}");
		m.put("villager.storage.group.content", "Salle filtrée « {0} »");
		m.put("villager.gui.storage.entry.content", "&d&lCoffre de salle filtrée");
		m.put("villager.gui.storage.entry.lore.content", "&7Reçoit : ");
		m.put("villager.gui.storage.entry.lore.content.empty", "&cVide : ne reçoit rien");
		m.put("villager.gui.storage.held.content", "&7Coffres de salle filtrée qui en contiennent : &f{0}");
		m.put("villager.gui.rooms.help.text.v3", "&7Une salle des coffres range toute seule :\n&7chaque catégorie reçoit des colonnes\n&7entières à sa taille (plus une marge),\n&7les colonnes libres restent en réserve.\n&7Un coffre dont vous choisissez le rôle\n&7n'est jamais réattribué.\n&6Une salle peut être réservée à quelques\n&6catégories (menu de la salle).\n&dUne salle filtrée ne planifie rien :\n&dchaque coffre ne reçoit que les objets\n&dqu'il contient déjà ; vide, il ne reçoit\n&drien.");
		m.put("villager.gui.role.auto.lore.v2", "&7Le coffre suit le mode de sa salle : une\n&7catégorie choisie par les trieurs, ou,\n&7dans une salle filtrée, seulement les\n&7objets qu'il contient déjà.");
		m.put("villager.gui.room.filtered.state", "&dSalle filtrée : chaque coffre ne reçoit\n&dque ce qu'il contient déjà.\n&7Conteneurs : &f{0}&7 (filtrés : &f{1}&7, vides : &f{2}&7)");
		m.put("villager.gui.room.mode.auto", "&6&lMode : salle automatique");
		m.put("villager.gui.room.mode.filtered", "&d&lMode : salle filtrée");
		m.put("villager.gui.room.mode.lore.auto", "&7Les trieurs donnent une catégorie aux\n&7coffres, par colonnes entières.");
		m.put("villager.gui.room.mode.lore.filtered", "&7Chaque coffre ne reçoit que les sortes\n&7d'objets qu'il contient déjà (plusieurs\n&7possibles). Un coffre vide ne reçoit\n&7rien : mettez-y un objet à la main pour\n&7qu'il l'accepte. Les trieurs n'en sortent\n&7jamais rien ; ils fusionnent seulement\n&7les piles dans chaque coffre.");
		m.put("villager.gui.room.mode.to_filtered", "&eClic : en faire une salle filtrée");
		m.put("villager.gui.room.mode.to_auto", "&eClic : repasser en salle automatique");
		m.put("villager.gui.room.mode.disabled", "&cSalles filtrées désactivées sur ce serveur.");
		m.put("villager.gui.room.mode.confirm.filtered", "Rendre la salle filtrée ?");
		m.put("villager.gui.room.mode.confirm.auto", "Repasser en salle automatique ?");
		m.put("villager.gui.room.mode.confirm.filtered.lore", "&7Chaque coffre automatique ne recevra\n&7plus que ce qu'il contient maintenant.\n&7Rien n'est déplacé ; un coffre vide ne\n&7reçoit rien.");
		m.put("villager.gui.room.mode.confirm.auto.lore", "&7Les coffres reçoivent de nouveau une\n&7catégorie par colonnes et les trieurs\n&7déplacent leurs objets en conséquence.");
		m.put("villager.storage.room.filtered.on", "&aSalle « {0} » filtrée : &e{1}&a coffre(s) ne reçoivent plus que ce qu'ils contiennent déjà (un coffre vide ne reçoit rien).");
		m.put("villager.storage.room.filtered.off", "&aSalle « {0} » de nouveau automatique : &e{1}&a coffre(s) reçoivent une catégorie, les trieurs y rangent les objets.");
		m.put("villager.storage.room.filtered.disabled", "&cSalles filtrées désactivées sur ce serveur (storage.filteredRooms = false).");
		m.put("villager.gui.room.theme.lore.filtered", "&7Sans effet dans une salle filtrée\n&7(gardées pour un retour en salle\n&7automatique).");
		m.put("villager.storage.room.theme.filtered", "&cSalle filtrée : ses coffres reçoivent ce qu'ils contiennent déjà. Repassez-la en salle automatique pour choisir ses catégories.");
		m.put("villager.gui.room.chest_list", "&b&lCoffres de la salle");
		m.put("villager.gui.room.chest_list.lore", "&7Chaque coffre et ce qu'il reçoit.\n&eClic : ouvrir la liste");
		m.put("villager.gui.room.chest_list.lore.filtered", "&7Chaque coffre et son filtre (les sortes\n&7d'objets qu'il contient) ; un coffre\n&7vide ne reçoit rien.\n&eClic : ouvrir la liste");
		m.put("villager.gui.room.chest_list.title", "Coffres de « {0} » ({1})");
		m.put("villager.gui.room.chest_list.chest", "&6Coffre {0}");
		m.put("villager.gui.room.chest_list.role", "&7Rôle : &f{0}");
		m.put("villager.gui.room.chest_list.manual", "&8Rôle choisi par un joueur");
		m.put("villager.gui.room.chest_list.click", "&eClic : changer son rôle");
		m.put("villager.gui.room.chest_list.none", "&7Aucun coffre dans cette salle.");
	}

	private static void family(Map<String, String> m, String family, String name) {
		m.put(ChestFilters.FAMILY_LANG + family, name);
	}
}
