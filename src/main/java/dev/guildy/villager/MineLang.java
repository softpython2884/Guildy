package dev.guildy.villager;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the real mines (levels, shared jobs, veins, "objectifs prioritaires", exploitation view). The
 * older mine keys stay in {@link VillagerLang}.
 */
final class MineLang {
	private MineLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// statuses of a miner
		m.put("villager.status.mine.widen", "&aÉlargit l'escalier · Y {0} ({1}/{2})");
		m.put("villager.status.mine.landing", "&aCreuse le palier du niveau Y {0} ({1}/{2})");
		m.put("villager.status.mine.gallery_main", "&aGalerie principale du niveau Y {0} ({1}/{2})");
		m.put("villager.status.mine.gallery", "&aGalerie niveau Y {0} ({1}/{2})");
		m.put("villager.status.mine.quarry", "&aCarrière niveau Y {0} ({1}/{2})");
		m.put("villager.status.mine.wanted_vein", "&dRécolte un objectif prioritaire ({0} bloc(s) restant(s)) · Y {1}");
		m.put("villager.status.mine.vein_outside", "&bSuit un filon hors zone ({0} bloc(s) restant(s)) · Y {1}");
		m.put("villager.status.mine.approach", "&bCreuse vers un filon · Y {0}");
		m.put("villager.status.mine.going_to", "&eSe rend à son chantier · Y {0} → {1}");
		m.put("villager.status.mine.no_work", "&7Attend du travail à « {0} » : l'escalier descend vers le prochain niveau");
		m.put("villager.status.mine.all_taken", "&7Attend du travail : les {0} chantier(s) ouvert(s) de « {1} » sont pris");
		m.put("villager.status.mine.full", "&7Mine « {0} » au complet ({1} mineurs) : attend une place");
		// messages
		m.put("villager.mine.deeper", "&aMine « {0} » : tous les niveaux jusqu'à Y {1} sont terminés, l'escalier descend vers Y {2}.");
		m.put("villager.mine.error.full", "&cLa mine « {0} » a déjà {1} mineur(s) : c'est le maximum ({2}).");
		// zone menu summary
		m.put("villager.mine.info.working", "&7État : &fen exploitation");
		m.put("villager.mine.info.levels_summary", "&7Niveaux : &f{0} ouvert(s)&7, &f{1} terminé(s) &7sur {2} &8(Y {3} → {4})");
		m.put("villager.mine.info.miners", "&7Mineurs au travail : &f{0}/{1}");
		m.put("villager.mine.info.wanted", "&7Objectifs prioritaires trouvés : &d{0}");
		m.put("villager.mine.info.veins", "&7Filons à suivre : &b{0}");
		m.put("villager.gui.mine.view.click", "&eClic : détails de l'exploitation");
		// exploitation view
		m.put("villager.gui.mine.view.title", "Exploitation : {0}");
		m.put("villager.gui.mine.view.summary", "&6&lMine « {0} »");
		m.put("villager.gui.mine.view.stairs", "&7Escalier : &f{0}/{1} marches &8(jusqu'à Y {2})");
		m.put("villager.gui.mine.view.stairs_blocked", "&7Escalier : &carrêté à Y {0}");
		m.put("villager.gui.mine.view.width", "&7Largeur de l'escalier : &f{0} bloc(s)");
		m.put("villager.gui.mine.view.widened", "&7Escalier élargi : &f{0}/{1} marches");
		m.put("villager.gui.mine.view.level", "&6&lNiveau Y {0}");
		m.put("villager.gui.mine.view.level.target", "&e(profondeur cible)");
		m.put("villager.gui.mine.view.level.closed", "&8Pas encore atteint par l'escalier");
		m.put("villager.gui.mine.view.level.open", "&aOuvert");
		m.put("villager.gui.mine.view.level.finished", "&7Terminé");
		m.put("villager.gui.mine.view.level.progress", "&7Tronçons creusés : &f{0}/{1}");
		m.put("villager.gui.mine.view.level.abandoned", "&7Tronçons abandonnés : &c{0}");
		m.put("villager.gui.mine.view.level.found", "&7Minerais : &f{0} &8· &7objectifs : &d{1}");
		m.put("villager.gui.mine.view.level.miners", "&7Mineurs ici : &f{0}");
		m.put("villager.gui.mine.view.more_levels", "&8… et {0} autre(s) niveau(x) plus bas");
		m.put("villager.gui.mine.view.no_level", "&7Aucun niveau pour l'instant");
		m.put("villager.gui.mine.view.miner", "&e{0}");
		m.put("villager.gui.mine.view.miner.level", "&7Niveau : &fY {0}");
		m.put("villager.gui.mine.view.miner.stairs", "&7Dans l'escalier ou en surface");
		m.put("villager.gui.mine.view.miner.away", "&8Pas au travail dans cette mine");
		m.put("villager.gui.mine.view.miner.wishlist", "&7Objectifs : &d{0}");
		m.put("villager.gui.mine.view.no_miner", "&7Aucun mineur affecté à cette mine");
		m.put("villager.gui.mine.view.others", "&e… et {0} autre(s) mineur(s)");
		m.put("villager.gui.mine.view.other", "&e{0} &8: &7{1}");
		m.put("villager.gui.mine.view.wishlist", "&d&lObjectifs prioritaires");
		m.put("villager.gui.mine.view.wishlist.lore", "&7Les blocs qui donnent ces objets sont minés\n&7en priorité (filons suivis même un peu hors\n&7de la zone), gardés dans le sac et jamais\n&7utilisés pour colmater.");
		m.put("villager.gui.mine.view.wishlist.zone", "&7Zone : &f{0}");
		m.put("villager.gui.mine.view.wishlist.villager", "&7{0} : &f{1}");
		m.put("villager.gui.mine.view.wishlist.none", "&8Aucun objectif prioritaire.");
		m.put("villager.gui.mine.view.reset", "&c&lRéinitialiser l'exploitation");
		m.put("villager.gui.mine.view.page", "&ePage {0}/{1}");
		Lang.addDefaults(m);
	}
}
