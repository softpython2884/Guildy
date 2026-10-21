package dev.guildy.villager;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the blacksmith and alchemist tasks: task names, level requirement, statuses, statistics, and the
 * French names of equipment and enchantments used in the statuses (the server only knows the English names).
 */
final class CraftLang {
	private CraftLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// tasks
		m.put("villager.task.blacksmith", "Forgeron");
		m.put("villager.task.blacksmith.desc", "&7Répare peu à peu les &farmures et objets&7 posés\n&7sur les &fporte-armures&7 du territoire, à l'enclume\n&7la plus proche du porte-armure. L'objet reste\n&7sur le porte-armure ; chaque réparation use\n&7l'enclume comme en vanilla.");
		m.put("villager.task.alchemist", "Alchimiste");
		m.put("villager.task.alchemist.desc", "&7Trois travaux, rangés au stockage :\n&d• Livres&7 : livres enchantés à la &dtable\n&7d'enchantement&7 (plus de bibliothèques = mieux).\n&d• Parchemins&7 : parchemins de sort (livre + papier).\n&d• Potions&7 : potions et &délixirs&7 à un &dalambic&7\n&7(fioles d'eau, ingrédients, poudre de blaze).\n&7Si son travail est impossible, il passe au suivant ;\n&7plusieurs alchimistes se répartissent les rôles.");
		// guild level requirement
		m.put("villager.craft.level.required", "&cNiveau de guilde {0} requis &8(actuel : {1})");
		m.put("villager.craft.level.ok", "&8Débloqué au niveau de guilde {0}");
		m.put("villager.craft.level.refused", "&cLa tâche {0} demande une guilde de niveau {1} (niveau actuel : {2}).");
		m.put("villager.status.craft.level", "&cNiveau de guilde {0} requis pour cette tâche");
		m.put("villager.status.craft.unloaded", "&8Territoire hors de portée (aucun joueur à proximité)");
		// blacksmith
		m.put("villager.status.smith.scanning", "&7Cherche des équipements abîmés sur les porte-armures &8({0}/{1})");
		m.put("villager.status.smith.nothing", "&7Aucun équipement abîmé sur les porte-armures");
		m.put("villager.status.smith.no_anvil", "&cAucune enclume à moins de {0} blocs d'un porte-armure");
		m.put("villager.status.smith.going", "&eVa à l'enclume pour réparer {0}");
		m.put("villager.status.smith.repairing", "&aRépare {0} ({1} %) — {2}");
		m.put("villager.status.smith.repaired", "&aA réparé {0} !");
		m.put("villager.status.smith.anvil_broken", "&cL'enclume s'est brisée : cherche une autre enclume");
		m.put("villager.smith.anvil.intact", "enclume");
		m.put("villager.smith.anvil.chipped", "enclume ébréchée");
		m.put("villager.smith.anvil.damaged", "enclume endommagée");
		m.put("villager.smith.anvil_destroyed", "&7L'enclume en &f{0} {1} {2}&7 s'est brisée sous les coups de &e{3}&7.");
		// alchemist
		m.put("villager.status.alchemy.scanning", "&7Cherche une table d'enchantement &8({0}/{1})");
		m.put("villager.status.alchemy.no_table", "&cAucune table d'enchantement dans le territoire");
		m.put("villager.status.alchemy.no_books", "&cPlus de livres dans le stockage");
		m.put("villager.status.alchemy.no_lapis", "&cPlus de lapis-lazuli dans le stockage ({0} par livre)");
		m.put("villager.status.alchemy.fetch_books", "&eVa chercher des livres au stockage");
		m.put("villager.status.alchemy.fetch_lapis", "&eVa chercher du lapis-lazuli au stockage");
		m.put("villager.status.alchemy.going", "&eVa à la table d'enchantement &8· &7table avec {0} bibliothèque(s) : niveau {1} au plus");
		m.put("villager.status.alchemy.working", "&dEnchante un livre (niveau {0}) {1} %");
		m.put("villager.status.alchemy.table_info", "&7Table avec {0} bibliothèque(s) : niveau {1} au plus");
		m.put("villager.status.alchemy.done", "&aA enchanté un livre : &d{0}");
		m.put("villager.status.alchemy.cap", "&7{0}/{1} livres et parchemins aujourd'hui : reprend demain");
		m.put("villager.status.alchemy.delivering", "&eRange {0} livre(s) enchanté(s) au stockage");
		m.put("villager.status.alchemy.no_room", "&cAucun coffre libre pour ranger les livres enchantés");
		m.put("villager.status.alchemy.bag_full", "&cSac plein : attend de pouvoir ranger");
		m.put("villager.status.alchemy.failed", "&cLa table n'a rien donné : nouvel essai");
		// alchemist: spell scrolls
		m.put("villager.status.alchemy.fetch_paper", "&eVa chercher du papier au stockage");
		m.put("villager.status.alchemy.scroll_done", "&aÉcrit un parchemin : &d{0}");
		m.put("villager.status.alchemy.no_paper", "&ePlus de papier : livre enchanté à la place &8· &d{0}");
		m.put("villager.status.alchemy.delivering_scrolls", "&eRange {0} parchemin(s) au stockage");
		m.put("villager.status.alchemy.no_room_scrolls", "&cAucun coffre libre pour ranger les parchemins");
		// statistics (villager menu)
		m.put("villager.gui.lore.stat.repaired", "&7Objets réparés : &f{0} &8(durabilité rendue : {1})");
		m.put("villager.gui.lore.stat.anvils", "&7Enclumes usées : &f{0}");
		m.put("villager.gui.lore.stat.books", "&7Livres enchantés : &f{0} &8(aujourd'hui : {1}/{2})");
		m.put("villager.gui.lore.stat.best_enchant", "&7Meilleur enchantement : &d{0}");
		m.put("villager.gui.lore.stat.last_book", "&7Dernier livre : &d{0}");
		m.put("villager.gui.lore.stat.scrolls", "&7Parchemins écrits : &f{0} &8(aujourd'hui : {1}/{2})");
		m.put("villager.gui.lore.stat.last_scroll", "&7Dernier parchemin : &d{0}");
		// equipment names: special items, then "<piece> <material>" (iron_chestplate → Plastron en fer)
		m.put("villager.craft.item.shield", "Bouclier");
		m.put("villager.craft.item.bow", "Arc");
		m.put("villager.craft.item.crossbow", "Arbalète");
		m.put("villager.craft.item.trident", "Trident");
		m.put("villager.craft.item.mace", "Masse");
		m.put("villager.craft.item.elytra", "Élytres");
		m.put("villager.craft.item.turtle_helmet", "Carapace de tortue");
		m.put("villager.craft.item.fishing_rod", "Canne à pêche");
		m.put("villager.craft.item.shears", "Cisailles");
		m.put("villager.craft.item.flint_and_steel", "Briquet");
		m.put("villager.craft.item.brush", "Pinceau");
		m.put("villager.craft.item.carrot_on_a_stick", "Carotte sur un bâton");
		m.put("villager.craft.item.warped_fungus_on_a_stick", "Champignon biscornu sur un bâton");
		m.put("villager.craft.item.wolf_armor", "Armure de loup");
		m.put("villager.craft.piece.helmet", "Casque");
		m.put("villager.craft.piece.chestplate", "Plastron");
		m.put("villager.craft.piece.leggings", "Jambières");
		m.put("villager.craft.piece.boots", "Bottes");
		m.put("villager.craft.piece.sword", "Épée");
		m.put("villager.craft.piece.pickaxe", "Pioche");
		m.put("villager.craft.piece.axe", "Hache");
		m.put("villager.craft.piece.shovel", "Pelle");
		m.put("villager.craft.piece.hoe", "Houe");
		m.put("villager.craft.piece.spear", "Lance");
		m.put("villager.craft.material.leather", "en cuir");
		m.put("villager.craft.material.chainmail", "en mailles");
		m.put("villager.craft.material.iron", "en fer");
		m.put("villager.craft.material.golden", "en or");
		m.put("villager.craft.material.diamond", "en diamant");
		m.put("villager.craft.material.netherite", "en netherite");
		m.put("villager.craft.material.copper", "en cuivre");
		m.put("villager.craft.material.wooden", "en bois");
		m.put("villager.craft.material.stone", "en pierre");
		// enchantments (vanilla French names)
		m.put("villager.craft.enchant.protection", "Protection");
		m.put("villager.craft.enchant.fire_protection", "Protection contre le feu");
		m.put("villager.craft.enchant.feather_falling", "Chute amortie");
		m.put("villager.craft.enchant.blast_protection", "Protection contre les explosions");
		m.put("villager.craft.enchant.projectile_protection", "Protection contre les projectiles");
		m.put("villager.craft.enchant.respiration", "Apnée");
		m.put("villager.craft.enchant.aqua_affinity", "Affinité aquatique");
		m.put("villager.craft.enchant.thorns", "Épines");
		m.put("villager.craft.enchant.depth_strider", "Agilité aquatique");
		m.put("villager.craft.enchant.frost_walker", "Semelles givrantes");
		m.put("villager.craft.enchant.binding_curse", "Malédiction du lien éternel");
		m.put("villager.craft.enchant.soul_speed", "Agilité des âmes");
		m.put("villager.craft.enchant.swift_sneak", "Furtivité rapide");
		m.put("villager.craft.enchant.sharpness", "Tranchant");
		m.put("villager.craft.enchant.smite", "Châtiment");
		m.put("villager.craft.enchant.bane_of_arthropods", "Fléau des arthropodes");
		m.put("villager.craft.enchant.knockback", "Recul");
		m.put("villager.craft.enchant.fire_aspect", "Aura de feu");
		m.put("villager.craft.enchant.looting", "Butin");
		m.put("villager.craft.enchant.sweeping_edge", "Affilage");
		m.put("villager.craft.enchant.efficiency", "Efficacité");
		m.put("villager.craft.enchant.silk_touch", "Toucher de soie");
		m.put("villager.craft.enchant.unbreaking", "Solidité");
		m.put("villager.craft.enchant.fortune", "Fortune");
		m.put("villager.craft.enchant.power", "Puissance");
		m.put("villager.craft.enchant.punch", "Frappe");
		m.put("villager.craft.enchant.flame", "Flamme");
		m.put("villager.craft.enchant.infinity", "Infinité");
		m.put("villager.craft.enchant.luck_of_the_sea", "Chance de la mer");
		m.put("villager.craft.enchant.lure", "Appât");
		m.put("villager.craft.enchant.loyalty", "Loyauté");
		m.put("villager.craft.enchant.impaling", "Empalement");
		m.put("villager.craft.enchant.riptide", "Impulsion");
		m.put("villager.craft.enchant.channeling", "Canalisation");
		m.put("villager.craft.enchant.multishot", "Tir multiple");
		m.put("villager.craft.enchant.quick_charge", "Charge rapide");
		m.put("villager.craft.enchant.piercing", "Perforation");
		m.put("villager.craft.enchant.mending", "Raccommodage");
		m.put("villager.craft.enchant.vanishing_curse", "Malédiction de disparition");
		m.put("villager.craft.enchant.density", "Densité");
		m.put("villager.craft.enchant.breach", "Brèche");
		m.put("villager.craft.enchant.wind_burst", "Rafale de vent");
		m.put("villager.craft.enchant.lunge", "Fente");
		Lang.addDefaults(m);
	}
}
