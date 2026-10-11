package dev.guildy.villager;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the villager module (keys "villager.*").
 */
final class VillagerLang {
	private VillagerLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// tasks
		m.put("villager.task.idle", "Inactif");
		m.put("villager.task.sorter", "Ranger la base");
		m.put("villager.task.miner", "Mineur");
		m.put("villager.task.lumberjack", "Bûcheron");
		m.put("villager.task.farmer", "Fermier");
		m.put("villager.task.guard", "Garde");
		m.put("villager.task.idle.desc", "&7Comportement normal d'un villageois :\n&7commerce, promenade, sommeil.");
		m.put("villager.task.sorter.desc", "&7Vide les coffres de &bdépôt&7 et range les\n&7objets dans les coffres de leur catégorie.");
		m.put("villager.task.miner.desc", "&7Va miner dans sa zone de travail et rapporte\n&7des minerais selon la profondeur (Y) :\n&7plus c'est profond, meilleur c'est.");
		m.put("villager.task.lumberjack.desc", "&7Coupe les arbres naturels autour de sa zone\n&7de travail et replante des pousses.");
		m.put("villager.task.farmer.desc", "&7Récolte les cultures mûres autour de sa zone\n&7de travail et les replante.");
		m.put("villager.task.guard.desc", "&7Attaque les monstres du territoire autour de\n&7son poste (zone de travail, sinon le cœur).\n&7Défend le cœur pendant les vagues.");
		m.put("villager.task.explorer", "Exploration");
		m.put("villager.task.explorer.desc", "&7Part explorer les terres sauvages par le\n&7bureau d'accueil (ou le cœur) et revient\n&7quelques minutes plus tard avec du butin :\n&7graines, pousses, cuir, émeraudes, cartes au trésor...\n&cPetit risque de ne jamais revenir.");
		m.put("villager.task.nether", "Expédition au Nether");
		m.put("villager.task.nether.desc", "&7Passe par un &dportail du Nether&7 construit dans\n&7le territoire et revient avec du quartz, de l'or,\n&7de la poudre lumineuse, des bâtons de blaze...\n&cRisque plus élevé de ne jamais revenir.");
		// mission goals
		m.put("villager.goal.continuous", "Travail continu");
		m.put("villager.goal.fill", "Remplir son sac et rentrer");
		m.put("villager.goal.quota", "Rapporter une quantité");
		m.put("villager.goal.any_item", "objets");
		m.put("villager.gui.goal", "&6&lMission : &e{0}");
		m.put("villager.gui.goal.lore", "&7&fTravail continu&7 : travaille sans s'arrêter.\n&7&fRemplir son sac&7 : remplit son sac (ou fait une\n&7expédition), le vide au coffre de dépôt, puis s'arrête.\n&7&fRapporter une quantité&7 : continue jusqu'à avoir\n&7rapporté le nombre d'objets demandé, puis s'arrête.");
		m.put("villager.gui.goal.progress", "&7Progression : &a{0}&7/&a{1} &f{2}");
		m.put("villager.gui.goal.no_effect", "&8(sans effet pour la tâche actuelle)");
		m.put("villager.gui.goal.click", "&eClic : changer de mission");
		m.put("villager.gui.goal.amount", "&6&lQuantité : &e{0}");
		m.put("villager.gui.goal.amount.lore", "&7Clic gauche : +16  ·  clic droit : -16\n&7Maj + clic : ±64");
		m.put("villager.gui.goal.item", "&6&lObjet recherché : &e{0}");
		m.put("villager.gui.goal.item.lore", "&7Prenez en main l'objet voulu (ex. du fer brut,\n&7des bûches de chêne) et cliquez ici.\n&7Main vide ou Maj + clic : n'importe quel objet.");
		m.put("villager.goal.item.set", "&aMission de &e{0}&a : rapporter des &e{1}&a.");
		m.put("villager.mission.done.quota", "&e{0}&a a terminé sa mission ({1}) : &e{2} {3}&a rapportés au coffre !");
		m.put("villager.mission.done.fill", "&e{0}&a a rempli son sac ({1}) et l'a vidé au coffre : mission terminée !");
		// expeditions
		m.put("villager.expedition.departed", "&e{0}&7 part en expédition : &f{1}&7 (retour dans ~&f{2} min&7).");
		m.put("villager.expedition.returned", "&e{0}&a est de retour ({1}) avec &e{2}&a objets !");
		m.put("villager.expedition.lost", "&c{0} n'est jamais revenu ({1})... Le voyage était trop dangereux.");
		m.put("villager.status.no_portal", "&cAucun portail du Nether dans le territoire");
		m.put("villager.status.no_departure", "&cPas de point de départ (cœur ou bureau d'accueil)");
		m.put("villager.status.departure_unloaded", "&8Point de départ hors de portée");
		m.put("villager.status.going_departure", "&eSe prépare au départ...");
		m.put("villager.status.departure_failed", "&cDépart impossible (erreur, voir la console)");
		m.put("villager.status.away", "&dParti ({0}) : retour dans ~{1} min");
		m.put("villager.status.away_waiting", "&dDe retour d'expédition : attend que la base soit chargée");
		// statuses
		m.put("villager.status.unloaded", "&8Hors de portée (zone non chargée)");
		m.put("villager.status.starting", "&7Se prépare…");
		m.put("villager.status.idle", "&7Vaque à ses occupations");
		m.put("villager.status.trading", "&eEn pleine négociation");
		m.put("villager.status.riding", "&7Transporté");
		m.put("villager.status.wave", "&cÀ l'abri (vague en cours)");
		m.put("villager.status.sleeping", "&9Dort");
		m.put("villager.status.scared", "&cEffrayé !");
		m.put("villager.status.resting", "&9Repos");
		m.put("villager.status.no_workpos", "&cPas de zone de travail");
		m.put("villager.status.workpos_outside", "&cZone de travail hors du territoire");
		m.put("villager.status.workpos_unloaded", "&8Zone de travail non chargée");
		m.put("villager.status.no_territory", "&cLa guilde n'a pas de territoire");
		m.put("villager.status.going_work", "&eSe rend à sa zone de travail");
		m.put("villager.status.going_deposit", "&eVa déposer sa récolte");
		m.put("villager.status.deposited", "&aA déposé {0} objet(s)");
		m.put("villager.status.no_deposit", "&cPas de coffre de dépôt");
		m.put("villager.status.deposit_full", "&cCoffres de dépôt pleins");
		m.put("villager.status.sorter.waiting", "&7Attend des objets à ranger");
		m.put("villager.status.sorter.going_pickup", "&eVa chercher des objets à ranger");
		m.put("villager.status.sorter.delivering", "&eRange {0} pile(s) d'objets");
		m.put("villager.status.sorter.no_destination", "&cAucun coffre libre pour ranger ses objets");
		m.put("villager.status.miner.mining", "&aMine… ({0} s restantes)");
		m.put("villager.status.lumberjack.searching", "&7Cherche un arbre");
		m.put("villager.status.lumberjack.no_tree", "&7Aucun arbre à couper dans sa zone");
		m.put("villager.status.lumberjack.going", "&eSe rend à un arbre");
		m.put("villager.status.lumberjack.cutting", "&aCoupe un arbre ({0} bûche(s) restantes)");
		m.put("villager.status.farmer.waiting", "&7Attend que les cultures poussent");
		m.put("villager.status.farmer.going", "&eSe rend aux cultures");
		m.put("villager.status.farmer.harvesting", "&aRécolte");
		m.put("villager.status.guard.patrol", "&7Monte la garde");
		m.put("villager.status.guard.heart", "&6Défend le cœur");
		m.put("villager.status.guard.chasing", "&ePoursuit un monstre");
		m.put("villager.status.guard.fighting", "&cCombat un monstre !");
		m.put("villager.status.guard.returning", "&eRetourne à son poste");
		// professions
		m.put("villager.profession.none", "Sans emploi");
		m.put("villager.profession.nitwit", "Simplet");
		m.put("villager.profession.armorer", "Armurier");
		m.put("villager.profession.butcher", "Boucher");
		m.put("villager.profession.cartographer", "Cartographe");
		m.put("villager.profession.cleric", "Clerc");
		m.put("villager.profession.farmer", "Fermier");
		m.put("villager.profession.fisherman", "Pêcheur");
		m.put("villager.profession.fletcher", "Flécheur");
		m.put("villager.profession.leatherworker", "Tanneur");
		m.put("villager.profession.librarian", "Bibliothécaire");
		m.put("villager.profession.mason", "Maçon");
		m.put("villager.profession.shepherd", "Berger");
		m.put("villager.profession.toolsmith", "Forgeron d'outils");
		m.put("villager.profession.weaponsmith", "Forgeron d'armes");
		// arrivals / candidates
		m.put("villager.candidate.arrived", "&e{0}&7 est arrivé au bureau d'accueil et souhaite rejoindre la guilde ! ");
		m.put("villager.candidate.button.accept", "&a&l[Accepter]");
		m.put("villager.candidate.button.accept.hover", "&7Accueillir ce villageois dans la guilde");
		m.put("villager.candidate.button.refuse", "&c&l[Refuser]");
		m.put("villager.candidate.button.refuse.hover", "&7Renvoyer ce candidat");
		m.put("villager.candidate.none", "&cAucun villageois ne demande à rejoindre la guilde.");
		m.put("villager.candidate.accepted", "&a{0} a rejoint la guilde ! &7Donnez-lui une tâche : &e/guild villagers");
		m.put("villager.candidate.refused", "&7{0} a été refusé et repart sur les routes.");
		m.put("villager.candidate.timeout", "&7{0} n'a pas reçu de réponse et repart sur les routes.");
		m.put("villager.candidate.died", "&c{0}, le candidat, est mort avant d'avoir pu rejoindre la guilde.");
		m.put("villager.candidate.lost", "&7{0}, le candidat, a disparu.");
		m.put("villager.arrival.no_bed", "&eUn villageois voulait s'installer, mais il n'y a aucun lit libre dans le territoire !");
		m.put("villager.reception.built", "&aBureau d'accueil construit ! &7Un premier villageois devrait se présenter d'ici {0} secondes.");
		// members
		m.put("villager.death", "&c{0} est mort.");
		m.put("villager.death.night", "&c{0} est mort pendant la nuit…");
		m.put("villager.lost.converted", "&c{0} a été transformé en monstre !");
		m.put("villager.lost.vanished", "&c{0} a disparu…");
		m.put("villager.left.defeat", "&c{0} a perdu confiance après la défaite et quitte la guilde.");
		m.put("villager.dismissed", "&7{0} a été renvoyé et quitte la guilde.");
		m.put("villager.task.set", "&a{0} : nouvelle tâche « {1} ».");
		m.put("villager.task.set_all", "&a{0} villageois inactif(s) ont reçu la tâche « {1} ».");
		m.put("villager.task.none_idle", "&7Aucun villageois inactif.");
		m.put("villager.task.need_workpos", "&eN'oubliez pas de définir sa zone de travail (menu du villageois).");
		m.put("villager.task.items_returned", "&7{0} vous remet les objets qu'il transportait.");
		m.put("villager.workpos.set", "&aZone de travail de {0} : &e{1} {2} {3}&a.");
		m.put("villager.workpos.outside", "&cVous devez vous trouver dans le territoire de la guilde.");
		// errors
		m.put("villager.error.not_member", "&c{0} travaille pour la guilde &e{1}&c.");
		m.put("villager.error.candidate", "&7{0} attend la réponse de la guilde &e{1}&7.");
		m.put("villager.error.not_your_reception", "&cCe bureau d'accueil appartient à la guilde &e{0}&c.");
		m.put("villager.error.unknown", "&cCe villageois ne fait plus partie de la guilde.");
		m.put("villager.error.not_founded", "&cLa guilde n'a pas encore de base.");
		m.put("villager.error.already_candidate", "&cUn candidat attend déjà une réponse.");
		m.put("villager.error.no_reception", "&cLa guilde n'a pas de bureau d'accueil.");
		m.put("villager.error.not_loaded", "&cLe bureau d'accueil n'est pas chargé (aucun joueur à proximité).");
		m.put("villager.error.full", "&cLa guilde a déjà le nombre maximum de villageois.");
		m.put("villager.error.full_count", "&cLa guilde a déjà le nombre maximum de villageois ({0}).");
		m.put("villager.error.no_luck", "&7Aucun villageois ne s'est présenté.");
		m.put("villager.error.no_spot", "&cAucun emplacement libre près du bureau d'accueil.");
		m.put("villager.error.spawn_failed", "&cImpossible de faire apparaître le villageois.");
		m.put("villager.admin.spawned", "&aCandidat {0} envoyé à la guilde {1}.");
		// storage
		m.put("villager.storage.role.deposit", "Dépôt");
		m.put("villager.storage.select", "&eFaites un clic droit sur un coffre, un tonneau ou une boîte de Shulker du territoire (&6{0} s&e).");
		m.put("villager.storage.select_expired", "&7Sélection de coffre annulée.");
		m.put("villager.storage.not_territory", "&cCe conteneur n'est pas dans le territoire de votre guilde.");
		m.put("villager.storage.added", "&aConteneur enregistré : &e{0}&a ({1} {2} {3}).");
		m.put("villager.storage.updated", "&aRôle du conteneur modifié : &e{0}&a.");
		m.put("villager.storage.removed", "&7Conteneur retiré du stockage.");
		m.put("villager.storage.limit", "&cLimite de {0} conteneurs atteinte.");
		// reception sign (4 short lines)
		m.put("villager.sign.title", "&lAccueil");
		m.put("villager.sign.count", "Villageois {0}/{1}");
		m.put("villager.sign.candidate", "Candidat : {0}");
		m.put("villager.sign.click", "Clic : gérer");
		// menus
		m.put("villager.gui.yes", "&aoui");
		m.put("villager.gui.no", "&cnon");
		m.put("villager.gui.list.title", "Villageois ({0}/{1})");
		m.put("villager.gui.list.empty", "&7Aucun villageois");
		m.put("villager.gui.list.empty.lore", "&7Construisez un &ebureau d'accueil&7 : chaque\n&7matin, un villageois peut se présenter.");
		m.put("villager.gui.villager.name", "&e{0}");
		m.put("villager.gui.lore.task", "&7Tâche : &f{0}");
		m.put("villager.gui.lore.status", "&7Statut : {0}");
		m.put("villager.gui.lore.profession", "&7Métier : &f{0}");
		m.put("villager.gui.lore.position", "&7Position : &f{0} {1} {2}");
		m.put("villager.gui.lore.last_position", "&7Dernière position : &8{0} {1} {2}");
		m.put("villager.gui.lore.zone", "&7Zone de travail : &f{0} {1} {2}");
		m.put("villager.gui.lore.zone.none", "&7Zone de travail : &cnon définie");
		m.put("villager.gui.lore.carried", "&7Transporte : &f{0} objet(s)");
		m.put("villager.gui.lore.stat.sorted", "&8Objets rangés : {0}");
		m.put("villager.gui.lore.stat.mined", "&8Ressources minées : {0}");
		m.put("villager.gui.lore.stat.logs", "&8Bûches coupées : {0}");
		m.put("villager.gui.lore.stat.crops", "&8Récoltes : {0}");
		m.put("villager.gui.lore.stat.kills", "&8Monstres tués : {0}");
		m.put("villager.gui.lore.manage", "&eClic : gérer");
		m.put("villager.gui.candidate.name", "&eCandidat : {0}");
		m.put("villager.gui.candidate.lore", "&7Souhaite rejoindre la guilde.\n&7Repart dans &f{0} s&7 sans réponse.");
		m.put("villager.gui.candidate.accept", "&a&lAccepter {0}");
		m.put("villager.gui.candidate.refuse", "&c&lRefuser {0}");
		m.put("villager.gui.candidate.none", "&7Aucun candidat");
		m.put("villager.gui.candidate.none.lore", "&7Chaque matin, un villageois a &f{4} %&7 de chance\n&7de se présenter au bureau d'accueil si :\n&8- &7bureau d'accueil construit : {0}\n&8- &7place dans la guilde : &f{1}/{2}\n&8- &7lit libre dans le territoire : {3}");
		m.put("villager.gui.beds.not_required", "&7non requis");
		m.put("villager.gui.assign_idle", "&6Assigner une tâche aux inactifs");
		m.put("villager.gui.assign_idle.lore", "&7{0} villageois inactif(s)\n&eClic : choisir la tâche");
		m.put("villager.gui.storage", "&6Stockage de la guilde");
		m.put("villager.gui.storage.lore", "&7{0} conteneur(s) enregistré(s)\n&eClic : gérer les coffres");
		m.put("villager.gui.help", "&bComment ça marche ?");
		m.put("villager.gui.help.lore", "&7Chaque matin, un villageois peut se présenter\n&7au &ebureau d'accueil&7 : acceptez-le, puis\n&7donnez-lui une tâche et une zone de travail.\n&7Les travailleurs déposent leur récolte dans les\n&7coffres de &bdépôt&7 ; le trieur les range.\n&7Accroupi + clic droit sur un villageois de la\n&7guilde : ouvrir son menu.");
		m.put("villager.gui.detail.title", "Villageois : {0}");
		m.put("villager.gui.task.button", "&e{0}");
		m.put("villager.gui.task.current", "&a▶ Tâche actuelle");
		m.put("villager.gui.task.choose", "&eClic : choisir cette tâche");
		m.put("villager.gui.workpos", "&bDéfinir la zone de travail ici");
		m.put("villager.gui.workpos.lore", "&7Utilise votre position actuelle.\n&7Mineur : la profondeur (Y) fixe les minerais.\n&7Bûcheron, fermier : centre de la zone.\n&7Garde : son poste (sinon le cœur).");
		m.put("villager.gui.dismiss", "&cRenvoyer {0}");
		m.put("villager.gui.dismiss.lore", "&7Le villageois quitte définitivement la guilde.\n&7Ses objets transportés vous sont rendus.");
		m.put("villager.gui.dismiss.confirm", "Renvoyer {0} ?");
		m.put("villager.gui.carried", "&6Objets transportés");
		m.put("villager.gui.carried.empty", "&7Rien");
		m.put("villager.gui.carried.line", "&7- &f{0} × ");
		m.put("villager.gui.carried.more", "&7… et {0} autre(s) pile(s)");
		m.put("villager.gui.assign.title", "Tâche pour les inactifs");
		m.put("villager.gui.storage.title", "Stockage ({0})");
		m.put("villager.gui.storage.entry.deposit", "&b&lDépôt");
		m.put("villager.gui.storage.entry.category", "&e&l{0}");
		m.put("villager.gui.storage.entry.lore.pos", "&7Position : &f{0} {1} {2}");
		m.put("villager.gui.storage.entry.lore.fill", "&7Rempli : &f{0}/{1} emplacements");
		m.put("villager.gui.storage.entry.lore.unloaded", "&8Zone non chargée");
		m.put("villager.gui.storage.entry.lore.missing", "&cConteneur introuvable");
		m.put("villager.gui.storage.entry.lore.remove", "&cClic : retirer du stockage");
		m.put("villager.gui.storage.remove.confirm", "Retirer ce conteneur ?");
		m.put("villager.gui.storage.add", "&a&lAjouter un coffre");
		m.put("villager.gui.storage.add.lore", "&7Puis faites un clic droit sur un coffre,\n&7un tonneau ou une boîte de Shulker\n&7du territoire ({0} secondes).");
		m.put("villager.gui.storage.categories", "&bCatégories");
		m.put("villager.gui.storage.categories.line", "&8- &f{0}");
		m.put("villager.gui.storage.categories.lore", "&7(modifiables dans villagers.json5)");
		m.put("villager.gui.storage.help.lore", "&7Les travailleurs déposent leur récolte dans\n&7les coffres de &bdépôt&7. Le trieur (tâche\n&7« Ranger la base ») les range ensuite dans\n&7les coffres de catégorie, sinon « {0} ».");
		m.put("villager.gui.role.title", "Rôle du conteneur ({0} {1} {2})");
		m.put("villager.gui.role.deposit", "&b&lDépôt");
		m.put("villager.gui.role.deposit.lore", "&7Les travailleurs y déposent leur récolte ;\n&7le trieur le vide.");
		m.put("villager.gui.role.category", "&e&l{0}");
		m.put("villager.gui.role.category.lore", "&7Le trieur y range les objets de\n&7cette catégorie.");
		m.put("villager.gui.role.fallback.lore", "&7Objets sans catégorie, ou dont\n&7les coffres sont pleins.");
		Lang.addDefaults(m);
	}
}
