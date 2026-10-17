package dev.guildy.villager;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the "Autorisations du fanion" (zone toggles, presets, menu) and of the field works of the farmers
 * (statuses, idle notes, stats lines).
 */
final class FieldLang {
	private FieldLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// toggles of field zones
		m.put("villager.field.option.farm.harvest", "Récolter");
		m.put("villager.field.option.farm.harvest.desc", "&7Récolte les cultures mûres de la zone (celles\n&7de son objectif ; melons, citrouilles et canne\n"
				+ "&7à sucre avec « Toutes les cultures »).");
		m.put("villager.field.option.farm.replant", "Replanter");
		m.put("villager.field.option.farm.replant.desc", "&7Replante chaque culture récoltée avec une de\n&7ses graines (sinon la terre reste vide).");
		m.put("villager.field.option.farm.sow", "Semer");
		m.put("villager.field.option.farm.sow.desc", "&7Sème les terres labourées vides avec les\n&7graines de son sac ou des coffres du stockage :\n"
				+ "&7objectifs prioritaires, puis la culture de la\n&7zone, puis ce qu'il trouve (blé, carottes,\n&7pommes de terre, betteraves).");
		m.put("villager.field.option.farm.retill", "Relabourer la terre piétinée");
		m.put("villager.field.option.farm.retill.desc", "&7Retient où une terre labourée est redevenue de\n&7la terre (piétinée, desséchée), la relaboure et\n"
				+ "&7la ressème, avec la même culture si possible.");
		m.put("villager.field.option.farm.till", "Labourer");
		m.put("villager.field.option.farm.till.desc", "&7Laboure l'herbe, la terre, la terre stérile et\n&7la terre racinée &fhydratées&7 (eau à 4 blocs ou\n"
				+ "&7moins, comme la houe vanilla), jamais un chemin\n&7ni contre vos constructions.\n"
				+ "&7Avec « Créer des points d'eau », tout le sol\n&7de la zone finit hydraté puis labouré.");
		m.put("villager.field.option.farm.irrigate", "Créer des points d'eau");
		m.put("villager.field.option.farm.irrigate.desc", "&7Creuse des trous d'eau d'un bloc en quadrillage\n&7(tous les 9 blocs) pour que tout le sol de la\n"
				+ "&7zone soit hydraté, et les remplit s'ils\n&7disparaissent. Seulement sur un sol sûr : plein\n"
				+ "&7dessous et autour, loin des constructions et\n&7des autres guildes.");
		m.put("villager.field.option.farm.clear", "Dégager le sol");
		m.put("villager.field.option.farm.clear.desc", "&7Arrache herbes, fougères, fleurs et buissons\n&7morts sur le sol à labourer (ce qu'ils donnent\n"
				+ "&7va au coffre de dépôt). Jamais les pousses\n&7ni les cultures.");
		m.put("villager.field.option.farm.level", "Aplanir");
		m.put("villager.field.option.farm.level.desc", "&7Comble les trous d'un bloc et rabote les bosses\n&7d'un bloc de terre naturelle au niveau du champ\n"
				+ "&7(terre du sac, du stockage ou des bosses).");
		m.put("villager.field.option.farm.bonemeal", "Poudre d'os");
		m.put("villager.field.option.farm.bonemeal.desc", "&7Met de la poudre d'os (sac, sinon stockage) sur\n&7les cultures qui poussent.");
		// toggles of forest zones
		m.put("villager.field.option.forest.cut", "Couper les arbres");
		m.put("villager.field.option.forest.cut.desc", "&7Coupe les arbres naturels de la zone (ceux\n&7de son objectif).");
		m.put("villager.field.option.forest.replant", "Replanter");
		m.put("villager.field.option.forest.replant.desc", "&7Replante une pousse à la base de chaque arbre\n&7coupé.");
		m.put("villager.field.option.forest.plant", "Planter de nouvelles pousses");
		m.put("villager.field.option.forest.plant.desc", "&7Quand il n'a plus rien à couper, plante de\n&7nouvelles pousses sur le sol naturel libre de\n&7la zone.");
		// presets
		m.put("villager.field.preset.defaults", "Réglages par défaut");
		m.put("villager.field.preset.defaults.desc", "&7Comme les zones d'avant : récolte, replante,\n&7sème, relaboure la terre piétinée et laboure\n"
				+ "&7le sol déjà hydraté.");
		m.put("villager.field.preset.maintain", "Entretenir un champ");
		m.put("villager.field.preset.maintain.desc", "&7Pour un champ déjà fait : récolte, replante,\n&7sème les terres vides et relaboure la terre\n"
				+ "&7piétinée. Ne laboure rien de nouveau.");
		m.put("villager.field.preset.create", "Créer des champs");
		m.put("villager.field.preset.create.desc", "&7Transforme toute la zone en champs : dégage\n&7le sol, creuse des points d'eau, laboure tout\n"
				+ "&7le sol hydraté, sème, récolte et entretient.\n&8(sans aplanir ni poudre d'os)");
		// menu
		m.put("villager.field.button", "&6&lAutorisations du fanion");
		m.put("villager.field.button.lore", "&7Ce que les villageois de la zone ont\n&7le droit d'y faire : &f{0}/{1}&7 activées.");
		m.put("villager.field.button.preset", "&7Réglage : &f{0}");
		m.put("villager.field.button.custom", "&7Réglage : &fpersonnalisé");
		m.put("villager.field.button.click", "&eClic : modifier");
		m.put("villager.field.title", "Autorisations de « {0} »");
		m.put("villager.field.info", "&e&lAutorisations du fanion");
		m.put("villager.field.info.lore", "&7Choisissez ce que les villageois de « {0} »\n&7ont le droit d'y faire. Ils restent dans la zone,\n"
				+ "&7jamais chez une autre guilde ni contre vos\n&7constructions, et seulement quand elle est chargée.");
		m.put("villager.field.info.farm", "&7Ordre : récolte d'abord, puis relabour et\n&7semis, puis les travaux du sol (points d'eau,\n"
				+ "&7aplanir, labourer) entre deux récoltes.");
		m.put("villager.field.info.state", "&7Points d'eau creusés : &f{0}\n&7Terres piétinées à relabourer : &f{1}");
		m.put("villager.field.option.name.on", "&a{0}");
		m.put("villager.field.option.name.off", "&7{0}");
		m.put("villager.field.state.on", "&a✔ Activé");
		m.put("villager.field.state.off", "&c✘ Désactivé");
		m.put("villager.field.state.server_off", "&8(désactivé sur ce serveur : villagers.json5)");
		m.put("villager.field.toggle.on", "&eClic : désactiver");
		m.put("villager.field.toggle.off", "&eClic : activer");
		m.put("villager.field.irrigate.free", "&8Eau gratuite.");
		m.put("villager.field.irrigate.cost", "&8Coûte {0} seau(x) d'eau du stockage par trou\n&8(les seaux vides reviennent au coffre de dépôt).");
		m.put("villager.field.preset.name", "&b{0}");
		m.put("villager.field.preset.current", "&a✔ Réglage actuel");
		m.put("villager.field.preset.click", "&eClic : appliquer");
		m.put("villager.field.preset.applied", "&7Autorisations de « {0} » : &f{1}");
		m.put("villager.field.toggled.on", "&7« {0} » : &f{1} &aactivé");
		m.put("villager.field.toggled.off", "&7« {0} » : &f{1} &cdésactivé");
		// statuses
		m.put("villager.field.status.survey", "&7Inspecte le champ « {0} » &8({1}/{2})");
		m.put("villager.field.status.checking", "&7Vérifie le champ « {0} »");
		m.put("villager.field.status.going.refill", "&eVa remplir un point d'eau &8({0}/{1})");
		m.put("villager.field.status.going.hole", "&eVa creuser un point d'eau &8({0}/{1})");
		m.put("villager.field.status.going.level", "&eVa aplanir le champ &8({0}/{1})");
		m.put("villager.field.status.going.till", "&eVa labourer &8({0}/{1})");
		m.put("villager.field.status.going.bonemeal", "&eVa mettre de la poudre d'os &8({0}/{1})");
		m.put("villager.field.status.going.retill", "&eVa relabourer la terre piétinée &8({0}/{1})");
		m.put("villager.field.status.work.refill", "&aRemplit un point d'eau ({0}/{1})");
		m.put("villager.field.status.work.hole", "&aCreuse un point d'eau ({0}/{1})");
		m.put("villager.field.status.work.level", "&aAplanit le champ ({0}/{1})");
		m.put("villager.field.status.work.till", "&aLaboure ({0}/{1})");
		m.put("villager.field.status.work.bonemeal", "&aMet de la poudre d'os ({0}/{1})");
		m.put("villager.field.status.work.retill", "&aRelaboure la terre piétinée ({0}/{1})");
		m.put("villager.field.status.fetch.water_bucket", "&eVa chercher des seaux d'eau au stockage");
		m.put("villager.field.status.fetch.dirt", "&eVa chercher de la terre au stockage");
		m.put("villager.field.status.fetch.bone_meal", "&eVa chercher de la poudre d'os au stockage");
		m.put("villager.field.status.harvest_off", "&7Récolte désactivée dans « {0} » &8(autorisations du fanion)");
		m.put("villager.field.status.cut_off", "&7Coupe désactivée dans « {0} » &8(autorisations du fanion)");
		// idle notes (after the task's own idle status)
		m.put("villager.field.idle", "{0} &8· {1} · {2}");
		m.put("villager.field.note.done", "champ terminé");
		m.put("villager.field.note.no_buckets", "plus de seaux d'eau au stockage");
		m.put("villager.field.note.no_dirt", "plus de terre pour aplanir");
		m.put("villager.field.note.no_bone_meal", "plus de poudre d'os");
		// stats
		m.put("villager.gui.lore.stat.tilled", "&8Terres labourées : {0}");
		m.put("villager.gui.lore.stat.water_holes", "&8Points d'eau creusés : {0}");
		Lang.addDefaults(m);
	}
}
