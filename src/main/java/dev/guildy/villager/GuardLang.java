package dev.guildy.villager;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the guard features (roles, equipment, retreat, friendly fire): keys "villager.guard.*",
 * "villager.gui.guard.*" and the guard statuses "villager.status.guard.*".
 */
final class GuardLang {
	private GuardLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// roles
		m.put("villager.guard.role.sword", "Épéiste");
		m.put("villager.guard.role.bow", "Archer");
		m.put("villager.guard.role.auto", "{0} (auto)");
		m.put("villager.guard.role.set", "&a{0} : rôle « {1} ».");
		m.put("villager.guard.weapon_returned", "&7{0} vous rend son arme : elle ne sert pas à son nouveau rôle.");
		// equipment
		m.put("villager.guard.equipment.given", "&7{0} vous remet son équipement ({1} objet(s)).");
		m.put("villager.guard.equipment.none", "&7{0} ne porte aucun équipement des coffres.");
		m.put("villager.guard.equipment.stored", "&7{0} range son équipement dans le stockage de la guilde.");
		m.put("villager.guard.equipment.dropped", "&e{0} : aucun coffre du stockage n'a de place, son équipement est posé à ses pieds.");
		// friendly fire
		m.put("villager.guard.friendly_fire", "&e{0} est un villageois de votre guilde : impossible de le blesser.");
		// statuses
		m.put("villager.status.guard.shooting", "&cTire sur un monstre !");
		m.put("villager.status.guard.backing_off", "&eRecule pour garder ses distances");
		m.put("villager.status.guard.retreating", "&6Se replie pour se soigner");
		m.put("villager.status.guard.healing", "&aSe soigne ({0}/{1} ❤)");
		m.put("villager.status.guard.healing_bed", "&aSe soigne au lit ({0}/{1} ❤)");
		m.put("villager.status.guard.equipping", "&eVa s'équiper au coffre ({0} {1} {2})");
		m.put("villager.status.guard.equipped", "&aS'est équipé ({0} objet(s))");
		// menus
		m.put("villager.gui.lore.guard", "&7Garde : &f{0} &8· &7armure &f{1} &8· &c{2}/{3} ❤");
		m.put("villager.gui.lore.guard_unloaded", "&7Garde : &f{0}");
		m.put("villager.gui.guard.role", "&6&lRôle : &e{0}");
		m.put("villager.gui.guard.role.lore", "&f&lÉpéiste&7 : combat au corps à corps.\n&f&lArcher&7 : tire de vraies flèches jusqu'à &f{0}&7 blocs\n&7et recule quand un monstre s'approche. Ses\n&7flèches ne touchent que les monstres.\n&f&lAuto&7 : le rôle qui équilibre les gardes de la\n&7guilde (&f{1} %&7 d'archers visés).");
		m.put("villager.gui.guard.role.count", "&7Gardes de la guilde : &f{0}&7 épéiste(s), &f{1}&7 archer(s)");
		m.put("villager.gui.guard.role.setting.auto", "&7Réglage : &fAuto");
		m.put("villager.gui.guard.role.setting.forced", "&7Réglage : &fimposé");
		m.put("villager.gui.guard.role.click", "&eClic : Auto → Épéiste → Archer");
		m.put("villager.gui.guard.equipment", "&6&lÉquipement");
		m.put("villager.gui.guard.health", "&7Vie : &c{0}/{1} ❤");
		m.put("villager.gui.guard.armor", "&7Armure : &f{0} &8(robustesse {1})");
		m.put("villager.gui.guard.slot.head", "&7Tête : ");
		m.put("villager.gui.guard.slot.chest", "&7Torse : ");
		m.put("villager.gui.guard.slot.legs", "&7Jambes : ");
		m.put("villager.gui.guard.slot.feet", "&7Pieds : ");
		m.put("villager.gui.guard.slot.weapon", "&7Arme : ");
		m.put("villager.gui.guard.slot.empty", "&8—");
		m.put("villager.gui.guard.slot.worn", " &8({0} %)");
		m.put("villager.gui.guard.slot.default", " &8(de base)");
		m.put("villager.gui.guard.unloaded", "&8Garde hors de portée : équipement inconnu.");
		m.put("villager.gui.guard.equipment.lore", "&7Hors combat, les gardes prennent dans les coffres\n&7du &bstockage&7 (dépôt et catégories) les pièces\n&7d'armure meilleures que les leurs et que leur\n&7équipement de base, et y rapportent l'ancienne.\n&7Une pièce cassée est remplacée plus tard.");
		m.put("villager.gui.guard.equipment.weapons", "&7Ils prennent aussi une meilleure épée, un\n&7meilleur arc ou une arbalète.");
		m.put("villager.gui.guard.equipment.off", "&8(prise d'équipement désactivée sur ce serveur)");
		m.put("villager.gui.guard.equipment.retreat", "&7Sous &f{0} %&7 de sa vie, il court se soigner à son\n&7lit, puis reprend son poste à &f{1} %&7.");
		m.put("villager.gui.guard.equipment.take", "&eMaj + clic : récupérer son équipement");
		Lang.addDefaults(m);
	}
}
