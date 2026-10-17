package dev.guildy.villager;

import dev.guildy.config.Comment;
import dev.guildy.config.Json5;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * villagers.json5, section "alchemist": the "Alchimiste" task (turns plain books into enchanted books at the best
 * enchanting table of the territory, sometimes into a spell scroll). Free of Minecraft classes (unit tests).
 */
@Json5.Section
public final class AlchemistConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("Guildy");
	/** Scroll ids of scrolls.json5. */
	private static final Pattern SCROLL_ID = Pattern.compile("[a-z0-9_]{1,48}");

	@Comment("Niveau de guilde minimum pour donner cette tache a un villageois (1 a 100)")
	public int minGuildLevel = 5;
	@Comment({
			"Temps de travail (secondes) par niveau d'enchantement : un livre de niveau 30 prend 30 x cette valeur ; le bonus",
			"de vitesse de travail s'applique (1 a 120). Le niveau suit la 3e ligne de la table vanilla selon le nombre de",
			"bibliotheques (15 bibliotheques : niveau 30 ; table seule : niveau 1 a 8)"
	})
	public int secondsPerLevel = 6;
	@Comment("Livres enchantes (et parchemins) au plus par alchimiste et par jour de jeu (1 a 1000)")
	public int maxBooksPerDay = 8;
	@Comment({
			"Lapis-lazuli consomme par livre (0 a 3) ; 0 = seulement du temps et des livres. Pris dans son sac puis dans les",
			"coffres du stockage, comme les livres"
	})
	public int lapisPerBook = 0;
	@Comment("Livres pris dans les coffres du stockage en un voyage (1 a 64)")
	public int booksPerTrip = 4;
	@Comment("Intervalle minimum (secondes) entre deux recherches des tables d'enchantement du territoire (10 a 3600)")
	public int scanIntervalSeconds = 120;
	@Comment({
			"Chance (0 a 1) qu'un travail termine donne un parchemin de sort au lieu d'un livre enchante (0 = jamais). Le",
			"parchemin utilise le meme livre (et le lapis) plus scrollPaper feuilles de papier ; sans papier : un livre enchante.",
			"Il est range dans le coffre de sa categorie de stockage (sinon un coffre Divers, sinon un coffre de depot)"
	})
	public double scrollChance = 0.15;
	@Comment("Feuilles de papier par parchemin (0 a 64), prises dans son sac puis dans les coffres du stockage comme les livres")
	public int scrollPaper = 2;
	@Comment("Parchemins au plus par alchimiste et par jour de jeu, compris dans maxBooksPerDay (0 a maxBooksPerDay ; 0 = jamais)")
	public int maxScrollsPerDay = 2;
	@Comment({
			"Niveau d'enchantement minimum (1 a 30) pour ecrire un parchemin plus rare que RARE (EPIC, LEGENDARY, MYTHIC) ; en",
			"dessous, seuls les parchemins RARE sont possibles. Niveau 30 = table entouree de 15 bibliotheques"
	})
	public int epicScrollLevel = 30;
	@Comment({
			"Parchemins que l'alchimiste peut ecrire : {id: poids} (ids de scrolls.json5, tirage pondere parmi ceux permis au",
			"niveau du travail ; un id inconnu est ignore avec un avertissement dans les journaux du serveur)"
	})
	public Map<String, Double> scrolls = defaultScrolls();

	void validate() {
		minGuildLevel = clamp(minGuildLevel, 1, 100);
		secondsPerLevel = clamp(secondsPerLevel, 1, 120);
		maxBooksPerDay = clamp(maxBooksPerDay, 1, 1000);
		lapisPerBook = clamp(lapisPerBook, 0, 3);
		booksPerTrip = clamp(booksPerTrip, 1, 64);
		scanIntervalSeconds = clamp(scanIntervalSeconds, 10, 3600);
		scrollChance = Double.isNaN(scrollChance) ? 0 : Math.max(0, Math.min(1, scrollChance));
		scrollPaper = clamp(scrollPaper, 0, 64);
		maxScrollsPerDay = clamp(maxScrollsPerDay, 0, maxBooksPerDay);
		epicScrollLevel = clamp(epicScrollLevel, 1, AlchemyRules.MAX_LEVEL);
		for (String warning : cleanScrolls()) {
			LOGGER.warn("[villagers.json5] {}", warning);
		}
	}

	/**
	 * Normalizes the scroll table: ids trimmed and lower-cased, entries with an invalid id or a weight that is not positive
	 * removed (pure).
	 *
	 * @return the problems found
	 */
	List<String> cleanScrolls() {
		List<String> warnings = new ArrayList<>();
		if (scrolls == null) {
			scrolls = defaultScrolls();
			return warnings;
		}
		Map<String, Double> clean = new LinkedHashMap<>();
		for (Map.Entry<String, Double> entry : scrolls.entrySet()) {
			String id = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT);
			if (!SCROLL_ID.matcher(id).matches()) {
				warnings.add("alchemist.scrolls: invalid scroll id '" + entry.getKey() + "' (a-z, 0-9, _): ignored");
				continue;
			}
			Double weight = entry.getValue();
			if (weight == null || !Double.isFinite(weight) || weight <= 0) {
				warnings.add("alchemist.scrolls: scroll '" + id + "' needs a positive weight: ignored");
				continue;
			}
			clean.merge(id, Math.min(weight, 1_000_000.0), Double::sum);
		}
		scrolls = clean;
		return warnings;
	}

	/**
	 * Ids of the scroll table that scrolls.json5 does not define (ignored when the alchemist picks a scroll). Checked once
	 * every config file is loaded (scrolls.json5 is read after villagers.json5).
	 *
	 * @param rarity scroll id → rarity name, null for an unknown id
	 */
	List<String> unknownScrolls(Function<String, @Nullable String> rarity) {
		List<String> warnings = new ArrayList<>();
		if (scrolls == null) {
			return warnings;
		}
		for (String id : scrolls.keySet()) {
			if (rarity.apply(id) == null) {
				warnings.add("alchemist.scrolls: unknown scroll '" + id + "' (not defined in scrolls.json5): ignored");
			}
		}
		return warnings;
	}

	/** Mostly the basic scrolls (Rappel first), rarely an epic one (needs a level-30 table by default). */
	static Map<String, Double> defaultScrolls() {
		Map<String, Double> map = new LinkedHashMap<>();
		map.put("rappel", 12.0);
		map.put("benediction", 7.0);
		map.put("revelation", 6.0);
		map.put("blizzard", 1.0);
		map.put("seisme", 1.0);
		map.put("egide", 1.0);
		map.put("tempete_de_foudre", 1.0);
		return map;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
