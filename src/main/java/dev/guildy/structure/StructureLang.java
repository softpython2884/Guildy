package dev.guildy.structure;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults for the structure texts (keys "structure.*").
 */
final class StructureLang {
	private StructureLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// validation with the plan
		m.put("structure.validate.unknown_plan", "&cCe plan ne correspond à aucune construction connue.");
		m.put("structure.validate.locked", "&cVotre guilde n'a pas encore débloqué &e{0}&c. &7(/guild build)");
		m.put("structure.validate.level", "&cIl faut une guilde de niveau &e{0}&c pour construire &e{1}&c.");
		m.put("structure.validate.max", "&cVotre guilde possède déjà le maximum autorisé ({0}) de &e{1}&c.");
		m.put("structure.validate.outside", "&cLes constructions doivent être bâties dans le territoire de votre guilde.");
		m.put("structure.validate.no_match", "&cLe bloc cliqué ne fait partie d'aucun(e) &e{0}&c conforme au plan.");
		m.put("structure.validate.wrong_block", "&cBloc incorrect en &e{0} {1} {2}&c : attendu &f{3}&c, trouvé &f{4}&c.");
		m.put("structure.validate.more_errors", "&7(+{0} autre(s) bloc(s) incorrect(s))");
		m.put("structure.validate.hint_front", "&7Astuce : faites le clic droit sur la &eface avant&7 du mur, là où seront posés les panneaux.");
		m.put("structure.validate.hint_plan", "&7Consultez la disposition dans la description du plan (vue de face).");
		m.put("structure.validate.overlap", "&cLe bloc en &e{0} {1} {2}&c fait déjà partie d'une autre construction.");
		m.put("structure.validate.territory", "&cLe bloc en &e{0} {1} {2}&c est hors du territoire de votre guilde.");
		m.put("structure.validate.sign_blocked", "&cL'emplacement du panneau en &e{0} {1} {2}&c doit être libre (trouvé : &f{3}&c).");
		m.put("structure.validate.unloaded_block", "zone non chargée");
		m.put("structure.validate.unloaded","&cLa zone en &e{0} {1} {2}&c n'est pas chargée : rapprochez-vous de la construction.");
		m.put("structure.validate.not_sturdy","&cLe bloc en &e{0} {1} {2}&c ne peut pas porter de panneau (bloc plein requis).");
		m.put("structure.built", "&a&l{0}&a construit(e) en &e{1} {2} {3}&a !");
		m.put("structure.built.guild", "&e{0}&a a validé la construction &e{1}&a.");
		// removal
		m.put("structure.removed.broken", "&cLa construction &e{0}&c a été détruite : &e{1}&c a cassé le bloc en &e{2} {3} {4}&c.");
		m.put("structure.removed.invalid", "&cLa construction &e{0}&c ne correspond plus au plan et a été retirée.");
		m.put("structure.removed.territory", "&cLa construction &e{0}&c n'est plus dans le territoire et a été retirée.");
		m.put("structure.removed.dismantled", "&e{0}&7 a démonté la construction &e{1}&7.");
		m.put("structure.removed.admin", "&7La construction &e{0}&7 a été retirée par un administrateur.");
		m.put("structure.dismantle.gone", "&cCette construction n'existe plus.");
		// unlock & plans
		m.put("structure.unlock.already", "&cCette construction est déjà débloquée.");
		m.put("structure.unlock.level", "&cIl faut une guilde de niveau &e{0}&c pour débloquer cette construction.");
		m.put("structure.unlock.done", "&e{0}&a a débloqué la construction &e{1}&a pour &e{2} or&a (trésor).");
		m.put("structure.unlock.admin", "&aUn administrateur a débloqué la construction &e{0}&a pour votre guilde.");
		m.put("structure.plan.locked", "&cDébloquez d'abord cette construction.");
		m.put("structure.plan.already", "&cVous avez déjà ce plan dans votre inventaire.");
		m.put("structure.plan.received", "&aVous avez reçu le plan : &e{0}&a. Bâtissez le mur puis faites un clic droit dessus avec le plan.");
		m.put("structure.plan.lore.layout", "&7Disposition &8(vue de face, de haut en bas)&7 :");
		m.put("structure.plan.lore.row", "&f{0}");
		m.put("structure.plan.lore.key", "&e{0} &8= &f{1}");
		m.put("structure.plan.lore.any", "&e. &8= &7n'importe quel bloc");
		m.put("structure.plan.lore.signs", "&7Panneaux posés devant : &f{0}");
		m.put("structure.plan.lore.usage", "&7Bâtissez le mur dans votre territoire, puis\n&7faites un &eclic droit&7 sur un de ses blocs\n&7avec ce plan en main (face avant).\n&8Le plan n'est pas consommé.");
		// signs
		m.put("structure.sign.default.name", "&1{0}");
		m.put("structure.sign.default.hint", "&8Clic droit");
		m.put("structure.sign.protected", "&cCe panneau fait partie d'une construction. Démontez-la depuis le menu &e/guild build&c.");
		m.put("structure.sign.foreign", "&cCe panneau appartient à la guilde &e{0}&c.");
		// menus
		m.put("structure.gui.title", "Constructions de la guilde");
		m.put("structure.gui.none", "&cAucune construction n'est configurée.");
		m.put("structure.gui.help.name", "&e&lConstructions");
		m.put("structure.gui.help.lore", "&7Débloquez une construction avec l'or du trésor,\n&7récupérez son plan, bâtissez le mur décrit\n&7puis validez-le avec un clic droit (plan en main).\n\n&7Trésor de la guilde : &e{0} or");
		m.put("structure.gui.cost", "&7Coût de déblocage : &e{0} or &7(trésor)");
		m.put("structure.gui.click_detail", "&eClic : voir le plan et les actions");
		m.put("structure.gui.status.built", "&aConstruite en {0} {1} {2}");
		m.put("structure.gui.status.more", "&a (+{0})");
		m.put("structure.gui.status.unlocked", "&eDébloquée &7- à construire");
		m.put("structure.gui.status.locked", "&cVerrouillée");
		m.put("structure.gui.level.ok", "&7Niveau requis : &a{0} &8(guilde : {1})");
		m.put("structure.gui.level.missing", "&7Niveau requis : &c{0} &8(guilde : {1})");
		m.put("structure.gui.detail.title", "Plan : {0}");
		m.put("structure.gui.unlock.name", "&6&lDébloquer");
		m.put("structure.gui.unlock.lore", "&7Coût : &e{0} or &7(payé par le trésor)\n&7Trésor actuel : &e{1} or");
		m.put("structure.gui.unlock.confirm_title", "Débloquer la construction ?");
		m.put("structure.gui.unlock.confirm", "&7Débloquer &e{0}&7 pour &e{1} or&7 du trésor ?");
		m.put("structure.gui.need_permission", "&cVotre rôle ne permet pas cette action.");
		m.put("structure.gui.plan.name", "&b&lRecevoir le plan");
		m.put("structure.gui.plan.lore", "&7Donne le plan de construction (gratuit).\n&7Clic droit sur le mur bâti, plan en main,\n&7pour valider la construction.");
		m.put("structure.gui.dismantle.name", "&c&lDémonter");
		m.put("structure.gui.dismantle.at", "&7Construite en &f{0} {1} {2}");
		m.put("structure.gui.dismantle.lore", "&7Retire les panneaux et désactive la construction.\n&7Les blocs du mur restent en place.");
		m.put("structure.gui.dismantle.confirm_title", "Démonter la construction ?");
		m.put("structure.gui.dismantle.confirm", "&7Démonter &e{0}&7 (en {1} {2} {3}) ?");
		m.put("structure.gui.instances.title", "Démonter : {0}");
		m.put("structure.gui.instances.click", "&eClic : démonter celle-ci");
		m.put("structure.gui.cell.any", "&7N'importe quel bloc");
		m.put("structure.gui.cell.name", "&f{0}");
		m.put("structure.gui.cell.tag", "&7N'importe quel bloc de &f{0}");
		m.put("structure.gui.cell.sign", "&eUn panneau (n°{0}) sera posé devant");
		m.put("structure.gui.size", "&7Taille : &f{0} x {1} &8- &7panneaux : &f{2}");
		m.put("structure.gui.max", "&7Maximum par guilde : &f{0}");
		m.put("structure.gui.howto", "&8Le plan ci-dessus est vu de face :\n&8les panneaux se posent sur la face avant.");
		// admin
		m.put("structure.admin.unknown", "&cConstruction inconnue : &e{0}");
		m.put("structure.admin.unlocked", "&aConstruction &e{0}&a débloquée pour la guilde &e{1}&a.");
		m.put("structure.admin.already", "&7La construction &e{0}&7 était déjà débloquée pour &e{1}&7.");
		m.put("structure.admin.checked", "&aGuilde &e{0}&a : {1} construction(s) valide(s), {2} retirée(s).");
		Lang.addDefaults(m);
	}
}
