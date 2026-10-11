package dev.guildy.claim;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the territory module (keys claim., protect., territory., home.).
 * Keys returned by {@link ClaimService} as errors never use placeholders (callers send them without arguments).
 */
final class ClaimLang {
	private ClaimLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		// claim errors (no placeholders: returned by ClaimService)
		m.put("claim.error.already_founded", "&cVotre guilde possède déjà une base. Agrandissez-la avec des drapeaux d'extension.");
		m.put("claim.error.not_founded", "&cVotre guilde n'a pas encore de base : plantez d'abord le drapeau fondateur.");
		m.put("claim.error.wrong_dimension", "&cLes territoires ne peuvent pas être créés dans cette dimension.");
		m.put("claim.error.taken", "&cCette zone empiète sur le territoire d'une autre guilde.");
		m.put("claim.error.too_close", "&cTrop près du territoire d'une autre guilde.");
		m.put("claim.error.too_close_spawn", "&cTrop près du point d'apparition du monde.");
		m.put("claim.error.max_chunks", "&cVotre territoire a atteint la taille maximale pour le niveau de la guilde.");
		m.put("claim.error.not_connected", "&cLe chunk doit toucher votre territoire par un côté.");
		m.put("claim.error.already_owned", "&cCe chunk appartient déjà à votre guilde.");
		m.put("claim.error.cannot_place", "&cImpossible de planter le drapeau ici.");
		m.put("claim.error.place_floor", "&cPlantez le drapeau sur le sol ou contre un mur.");
		m.put("claim.error.no_flag", "&cVous n'avez aucun drapeau d'extension dans votre inventaire.");
		m.put("claim.error.not_owned", "&cCe chunk n'appartient pas à votre guilde.");
		m.put("claim.error.heart_chunk", "&cLe chunk du cœur de la base ne peut pas être abandonné.");
		m.put("claim.error.disconnects", "&cCe retrait couperait votre territoire en plusieurs morceaux.");
		m.put("claim.error.leader_only", "&cSeul le chef de la guilde peut abandonner le territoire.");
		m.put("claim.error.wave_active", "&cImpossible pendant une vague !");
		m.put("claim.error.already_has_flag", "&cVous avez déjà le drapeau fondateur de votre guilde.");
		m.put("claim.error.treasury", "&cLe trésor de la guilde ne contient pas assez d'or.");
		m.put("claim.error.no_territory", "&cVotre guilde ne possède aucun territoire.");
		m.put("claim.flag.not_your_flag", "&cCe drapeau fondateur appartient à une autre guilde.");
		// claim success
		m.put("claim.founded.title", "&6&lBase fondée !");
		m.put("claim.founded.subtitle", "&eLe cœur de {0} bat désormais ici");
		m.put("claim.founded.broadcast", "&a{0} a planté le drapeau fondateur en &e{1} {2} {3}&a : la base est fondée ! Protégez son cœur.");
		m.put("claim.extended", "&a{0} a agrandi le territoire (+{1} chunk(s), &e{2}/{3}&a).");
		m.put("claim.unclaimed", "&e{0} a libéré le chunk &6[{1}, {2}]&e. Un drapeau d'extension a été rendu.");
		m.put("claim.abandoned", "&c{0} a abandonné le territoire de la guilde. Le drapeau fondateur a été rendu.");
		m.put("claim.flag.given", "&aVoici le drapeau fondateur de votre guilde : plantez-le pour fonder votre base !");
		m.put("claim.flag.bought", "&aDrapeau d'extension acheté pour &e{0} or&a. Plantez-le à côté de votre territoire.");
		m.put("claim.flag.info", "&7Prochain drapeau d'extension : &e{0} or &7(trésor : &e{1} or&7). ");
		m.put("claim.flag.buy", "&a&l[Acheter]");
		m.put("claim.flag.buy.hover", "&7Acheter un drapeau d'extension avec l'or du trésor");
		m.put("claim.info.usage", "&7Plantez un drapeau d'extension (ou &e/guild claim&7) dans un chunk voisin pour agrandir le territoire.");
		// admin
		m.put("claim.admin.gave_extension", "&a{0} drapeau(x) d'extension donné(s) à {1}.");
		m.put("claim.admin.gave_founding", "&aDrapeau fondateur de {0} donné à {1}.");
		m.put("claim.admin.tp", "&aTéléportation au cœur de {0}.");
		m.put("claim.admin.no_heart", "&c{0} n'a pas encore de base.");
		// protection
		m.put("protect.denied", "&cCe territoire appartient à la guilde &6{0}&c.");
		m.put("protect.denied_role", "&cVotre rôle ne vous permet pas de faire cela ici.");
		m.put("protect.heart", "&cLe cœur de la base est indestructible. Le chef peut utiliser « Abandonner le territoire » dans le menu de guilde.");
		m.put("protect.bypass.on", "&aContournement des protections activé.");
		m.put("protect.bypass.off", "&eContournement des protections désactivé.");
		// territory display
		m.put("territory.enter.title", "&6{0}");
		m.put("territory.enter.subtitle", "&7Niveau {0} - territoire protégé");
		m.put("territory.own.subtitle", "&aVotre territoire");
		m.put("territory.enter.actionbar", "&6{0} &8- &7Niveau {1} - territoire protégé");
		m.put("territory.own.actionbar", "&6{0} &8- &aVotre territoire");
		m.put("territory.wild", "&7Zone sauvage");
		m.put("territory.show", "&aLimites du territoire affichées pendant {0} s.");
		m.put("territory.show.wrong_dimension", "&cLes limites ne sont visibles que dans la dimension des territoires.");
		// home
		m.put("home.disabled", "&cLa téléportation au QG est désactivée.");
		m.put("home.not_set", "&cVotre guilde n'a pas encore de QG.");
		m.put("home.cooldown", "&cPatientez encore &e{0} s&c avant de retourner au QG.");
		m.put("home.warmup", "&eTéléportation au QG dans &6{0} s&e... ne bougez pas !");
		m.put("home.cancelled", "&cTéléportation annulée : vous avez bougé.");
		m.put("home.pending", "&eUne téléportation est déjà en cours.");
		m.put("home.teleported", "&aBienvenue au QG de la guilde !");
		m.put("home.set", "&aLe QG de la guilde est maintenant ici.");
		m.put("home.not_in_territory", "&cVous devez vous trouver dans le territoire de votre guilde.");
		m.put("home.unavailable", "&cLe QG est inaccessible pour le moment.");
		Lang.addDefaults(m);
	}
}
