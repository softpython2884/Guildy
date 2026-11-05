package dev.guildy.anchor;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the anchor flags (anchor. keys, the territory menu button and the /guild help line).
 */
final class AnchorLang {
	private AnchorLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// Item and banner.
		m.put("anchor.item.name", "&b&lDrapeau d'ancrage");
		m.put("anchor.item.lore", "&7Guilde : &f{0}\n&7Plantez-le (clic droit sur un bloc) dans le\n&7territoire : les {1}×{1} chunks autour restent\n"
				+ "&7actifs comme si un joueur était là (cultures,\n&7fours, redstone, golems de fer, animaux).\n"
				+ "&7Le casser vous le rend.");
		m.put("anchor.item.lore.mobs", "&7Les monstres y apparaissent aussi : fermes à\n&7monstres, cages à monstres à 16 blocs.");
		m.put("anchor.banner_name", "Ancrage de {0}");

		// Taking, planting, removing.
		m.put("anchor.given", "&aDrapeau d'ancrage reçu : plantez-le là où les chunks doivent rester actifs (dans le territoire).");
		m.put("anchor.given.paid", "&aDrapeau d'ancrage reçu (&6{0} or &apayés par le trésor) : plantez-le dans le territoire.");
		m.put("anchor.planted", "&b⚓ {0} &7a planté un drapeau d'ancrage en &f{1} {2} {3}&7 : {4} chunks restent actifs.");
		m.put("anchor.planted.inactive", "&eCe drapeau d'ancrage est planté mais inactif pour l'instant : {0}.");
		m.put("anchor.removed", "&7⚓ {0} a retiré le drapeau d'ancrage de &f{1} {2} {3}&7.");
		m.put("anchor.removed_by_landlord", "&c⚓ Le drapeau d'ancrage de {0} {1} {2} a été retiré par la guilde {3}, qui possède maintenant ce terrain.");
		m.put("anchor.lost", "&c⚓ Le drapeau d'ancrage de {0} {1} {2} a disparu (explosion, eau, piston...) : ses chunks ne sont plus chargés. "
				+ "Reprenez-en un dans le menu Territoire.");
		m.put("anchor.protected", "&cDrapeau d'ancrage de la guilde {0} : protégé.");

		// Errors.
		m.put("anchor.error.disabled", "&cLes drapeaux d'ancrage sont désactivés sur ce serveur.");
		m.put("anchor.error.level", "&cDrapeau d'ancrage : niveau de guilde {0} requis.");
		m.put("anchor.error.limit", "&cVotre guilde a déjà tous ses drapeaux d'ancrage ({0}) : retirez-en un d'abord.");
		m.put("anchor.error.carrying", "&7Vous avez déjà un drapeau d'ancrage dans votre inventaire : plantez-le d'abord.");
		m.put("anchor.error.not_yours", "&cCe drapeau d'ancrage appartient à une autre guilde.");
		m.put("anchor.error.cannot_place", "&cImpossible de planter le drapeau d'ancrage ici.");
		m.put("anchor.error.place_floor", "&cPlantez le drapeau d'ancrage sur le sol ou contre un mur.");
		m.put("anchor.error.foreign", "&cCe terrain appartient à la guilde {0}.");
		m.put("anchor.error.outside", "&cLe drapeau d'ancrage se plante dans le territoire de votre guilde.");
		m.put("anchor.error.gone", "&cCe drapeau d'ancrage n'existe plus.");

		// States.
		m.put("anchor.state.active", "actif");
		m.put("anchor.state.disabled", "désactivé sur le serveur");
		m.put("anchor.state.dimension", "dimension introuvable");
		m.put("anchor.state.level", "niveau de guilde trop bas");
		m.put("anchor.state.limit", "au-delà du nombre de drapeaux permis");
		m.put("anchor.state.foreign", "sur le terrain d'une autre guilde");
		m.put("anchor.state.outside", "hors du territoire");
		m.put("anchor.state.offline", "aucun membre connecté");

		// Menu.
		m.put("anchor.menu.title", "Drapeaux d'ancrage");
		m.put("anchor.menu.info.name", "&b&lDrapeaux d'ancrage");
		m.put("anchor.menu.info.lore", "&7Un drapeau d'ancrage planté garde les\n&7chunks autour de lui actifs, comme si un\n"
				+ "&7joueur était là, même quand personne\n&7n'est à côté : fermes, cultures, fours,\n&7redstone, golems de fer, animaux.");
		m.put("anchor.menu.info.count", "&7Drapeaux plantés : &f{0}&7/&f{1}");
		m.put("anchor.menu.info.next", "&7Un drapeau de plus au niveau &f{0}");
		m.put("anchor.menu.info.area", "&7Zone par drapeau : &f{0}×{0} chunks &7({1} chunks)");
		m.put("anchor.menu.info.mobs.on", "&7Monstres : &aapparaissent comme près d'un joueur");
		m.put("anchor.menu.info.mobs.off", "&7Monstres : &cpas d'apparition &7(réglage du serveur)");
		m.put("anchor.menu.info.online_only", "&eActifs seulement quand un membre est connecté.");
		m.put("anchor.menu.info.disabled", "&cDésactivés sur ce serveur.");
		m.put("anchor.menu.take.name", "&b&lPrendre un drapeau d'ancrage");
		m.put("anchor.menu.take.lore", "&7Gratuit. Plantez-le dans le territoire ;\n&7cassé par la guilde, il vous est rendu.");
		m.put("anchor.menu.take.lore.paid", "&7Prix : &6{0} or &7(payé par le trésor)\n&7Trésor : &6{1} or\n&7Cassé par la guilde, il vous est rendu.");
		m.put("anchor.menu.none.name", "&7Aucun drapeau planté");
		m.put("anchor.menu.none.lore", "&7Prenez un drapeau ci-dessus et plantez-le\n&7près de vos fermes.");
		m.put("anchor.menu.anchor.name", "&b&lAncrage n°{0}");
		m.put("anchor.menu.anchor.pos", "&7Position : &f{0} {1} {2}");
		m.put("anchor.menu.anchor.dimension", "&7Dimension : &f{0}");
		m.put("anchor.menu.anchor.by", "&7Planté par &f{0}");
		m.put("anchor.menu.anchor.active", "&a● Actif &7: {0} chunks chargés");
		m.put("anchor.menu.anchor.inactive", "&c● Inactif &7: {0}");
		m.put("anchor.menu.anchor.click", "&eClic : retirer le drapeau\n&7(il vous est rendu)");
		m.put("anchor.menu.remove.title", "Retirer le drapeau d'ancrage ?");
		m.put("anchor.menu.remove.lore", "&7Le drapeau de &f{0} {1} {2} &7est retiré\n&7et vous est rendu ; ses chunks ne sont\n&7plus chargés.");

		// Territory menu button.
		m.put("menu.territory.anchors.name", "&b&lDrapeaux d'ancrage");
		m.put("menu.territory.anchors.lore", "&7Gardez les chunks de vos fermes actifs\n&7comme si un joueur était là.\n&7Plantés : &f{0}&7/&f{1}");

		// Admin.
		m.put("anchor.admin.header", "&7Drapeaux d'ancrage : &f{0} &7plantés, &a{1} &7actifs ({2} chunks). Ancrages : {3}, monstres : {4}.");
		m.put("anchor.admin.on", "&aactivés");
		m.put("anchor.admin.off", "&cdésactivés");
		m.put("anchor.admin.line", "&7- &f{0} &7: {1} {2} {3} ({4}) &7— {5} &8(planté par {6})");
		m.put("anchor.admin.none", "&7Aucun drapeau d'ancrage planté.");
		m.put("anchor.admin.pause", "&eLe serveur se met en pause après {0} s sans joueur (pause-when-empty-seconds) : les ancrages s'arrêtent alors. "
				+ "Mettez anchors.keepServerAwake à true ou pause-when-empty-seconds=0.");

		// /guild help
		m.put("guild.help.anchor", "&b⚓ /guild ancrage &7: drapeaux qui gardent les chunks de vos fermes actifs.");
		m.put("guild.help.anchor.hover", "&7Ouvrir le menu des drapeaux d'ancrage");
		Lang.addDefaults(m);
	}
}
