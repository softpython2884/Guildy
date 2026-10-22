package dev.guildy.scroll;

import dev.guildy.core.Lang;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * French defaults of the Grand spectacle fireworks scroll (Guildy 1.14): its spell description, its cooldown group, its
 * refusals (scroll and grimoire wordings) and the announcements of the show.
 */
final class ShowLang {
	private ShowLang() {
	}

	static void register() {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("scroll.group.spectacle", "Spectacle");
		m.put("scroll.spell.fireworks.name", "Grand spectacle");
		m.put("scroll.spell.fireworks.lore", "Tracez {points} cercles runiques sur {width}\nblocs derrière vous, face à votre public,\n"
				+ "puis offrez-lui un vrai feu d'artifice de\n{duration} s : salves, figures, gerbes jusqu'à\n"
				+ "{height} blocs et bouquet final. Ne blesse\npersonne, ne touche à aucun bloc.");
		// refusals (the scroll is kept)
		m.put("scroll.fail.show_no_sky", "&cIl faut un ciel dégagé derrière vous pour le spectacle ({0} emplacement(s) libre(s) sur {1}, "
				+ "il en faut {2}) : le parchemin est intact.");
		m.put("scroll.fail.show_running", "&7Votre spectacle n'est pas terminé : le parchemin est intact.");
		m.put("scroll.fail.show_nearby", "&7Un autre spectacle illumine déjà ce ciel : le parchemin est intact.");
		m.put("grimoire.fail.show_no_sky", "&cIl faut un ciel dégagé derrière vous pour le spectacle ({0} emplacement(s) libre(s) sur {1}, "
				+ "il en faut {2}).");
		m.put("grimoire.fail.show_running", "&7Votre spectacle n'est pas terminé.");
		m.put("grimoire.fail.show_nearby", "&7Un autre spectacle illumine déjà ce ciel.");
		// the show
		m.put("scroll.show.start", "&6✦ &e{0} &6présente : &f&lGrand spectacle &6✦");
		m.put("scroll.show.start_cursed", "&5✦ &d{0} &5présente : &d&lSpectacle maudit &5✦");
		m.put("scroll.show.finale", "&6✦ &e&lBouquet final &6✦");
		m.put("scroll.show.finale_cursed", "&5✦ &d&lBouquet maudit &5✦");
		Lang.addDefaults(m);
	}
}
