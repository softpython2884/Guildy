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
 * villagers.json5, section "alchemist": the "Alchimiste" task: enchanted books and spell scrolls at the best enchanting
 * table of the territory, potions and elixirs at a brewing stand (what is brewed: alchemy.json5), the order of these jobs,
 * the distribution of the roles among the alchemists of a guild and the daily caps. Free of Minecraft classes (unit tests).
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
	@Comment("Livres enchantes au plus par alchimiste et par jour de jeu (1 a 1000) ; parchemins et potions ont leur propre plafond")
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
			"Travail 'Livres' : chance (0 a 1) qu'un livre termine devienne un parchemin de sort (0 = jamais). Le",
			"parchemin utilise le meme livre (et le lapis) plus scrollPaper feuilles de papier ; sans papier : un livre enchante.",
			"Il est range dans le coffre de sa categorie de stockage (sinon un coffre Divers, sinon un coffre de depot)"
	})
	public double scrollChance = 0.15;
	@Comment("Feuilles de papier par parchemin (0 a 64), prises dans son sac puis dans les coffres du stockage comme les livres")
	public int scrollPaper = 2;
	@Comment("Parchemins au plus par alchimiste et par jour de jeu, travail 'Parchemins' compris (0 a 1000 ; 0 = jamais)")
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
	@Comment({
			"Ordre des travaux : books = livres enchantes, scrolls = parchemins (un parchemin a chaque travail a la table, avec",
			"livre + papier), potions = potions et elixirs (alambic, voir alchemy.json5). L'alchimiste fait son travail prefere",
			"(son role) ; s'il est impossible (pas de table, de livres, de papier, d'alambic, d'ingredients, plafond du jour,",
			"stocks pleins...), il passe au premier travail possible dans cet ordre (raison affichee dans son menu)"
	})
	public List<String> jobOrder = new ArrayList<>(List.of("books", "scrolls", "potions"));
	@Comment({
			"Repartition des roles entre les alchimistes d'une guilde : balanced = selon roleWeights parmi les travaux possibles",
			"pour la guilde (5 alchimistes : 2 livres, 1 parchemins, 2 potions), priority = tous preferent le premier travail de",
			"jobOrder. Un role choisi dans le menu de l'alchimiste (Livres / Parchemins / Potions) remplace la repartition"
	})
	public String distribution = "balanced";
	@Comment("Parts de chaque travail dans la repartition balanced (0 = aucun alchimiste n'y est affecte d'office)")
	public Map<String, Double> roleWeights = defaultWeights();
	@Comment("Intervalle (secondes) entre deux repartitions des roles (10 a 3600)")
	public int roleIntervalSeconds = 60;
	@Comment("Un alchimiste au role choisi dans son menu passe lui aussi aux autres travaux quand le sien est impossible")
	public boolean manualRoleFallback = true;
	@Comment("Fioles de potions et d'elixirs au plus par alchimiste et par jour de jeu (0 a 1000 ; 0 = jamais de potions)")
	public int maxPotionsPerDay = 18;
	@Comment("Duree d'un brassage en secondes (20 = alambic vanilla) ; le bonus de vitesse de travail s'applique (1 a 120)")
	public int secondsPerBrew = 20;
	@Comment("Fioles par brassage (1 a 3, comme les 3 emplacements de l'alambic) : un ingredient pour toutes")
	public int bottlesPerBrew = 3;
	@Comment("Combustible des brassages, pris dans son sac puis au stockage")
	public String brewFuel = "minecraft:blaze_powder";
	@Comment("Brassages par unite de combustible (1 a 100 ; 20 = poudre de blaze vanilla)")
	public int brewsPerFuel = 20;
	@Comment("Remplir des fioles vides du stockage a une source d'eau ou un chaudron d'eau proche de l'alambic (sinon : fioles d'eau)")
	public boolean fillBottles = true;
	@Comment("Distance maximum (blocs) entre l'alambic et l'eau qui remplit les fioles vides (1 a 16)")
	public int waterRadius = 6;
	@Comment("Intervalle (secondes) entre deux inventaires des potions et des ingredients du stockage (5 a 300)")
	public int stockScanSeconds = 10;

	void validate() {
		minGuildLevel = clamp(minGuildLevel, 1, 100);
		secondsPerLevel = clamp(secondsPerLevel, 1, 120);
		maxBooksPerDay = clamp(maxBooksPerDay, 1, 1000);
		lapisPerBook = clamp(lapisPerBook, 0, 3);
		booksPerTrip = clamp(booksPerTrip, 1, 64);
		scanIntervalSeconds = clamp(scanIntervalSeconds, 10, 3600);
		scrollChance = Double.isNaN(scrollChance) ? 0 : Math.max(0, Math.min(1, scrollChance));
		scrollPaper = clamp(scrollPaper, 0, 64);
		maxScrollsPerDay = clamp(maxScrollsPerDay, 0, 1000);
		epicScrollLevel = clamp(epicScrollLevel, 1, AlchemyRules.MAX_LEVEL);
		for (String warning : cleanScrolls()) {
			LOGGER.warn("[villagers.json5] {}", warning);
		}
		for (String warning : cleanJobs()) {
			LOGGER.warn("[villagers.json5] {}", warning);
		}
	}

	/** Bounds of the job settings (order, distribution, weights, potions); pure. @return the problems found */
	List<String> cleanJobs() {
		List<String> warnings = new ArrayList<>();
		List<String> order = new ArrayList<>();
		for (AlchemyJob job : AlchemyRoles.parseOrder(jobOrder)) {
			order.add(job.id());
		}
		if (jobOrder != null) {
			for (String id : jobOrder) {
				if (AlchemyJob.parse(id) == null) {
					warnings.add("alchemist.jobOrder: unknown job '" + id + "' (books, scrolls, potions): ignored");
				}
			}
		}
		jobOrder = order;
		distribution = AlchemyRoles.Distribution.parse(distribution).id();
		Map<String, Double> weights = new LinkedHashMap<>();
		for (Map.Entry<AlchemyJob, Double> entry : AlchemyRoles.weights(roleWeights).entrySet()) {
			weights.put(entry.getKey().id(), Math.min(100.0, entry.getValue()));
		}
		for (AlchemyJob job : AlchemyJob.values()) {
			weights.putIfAbsent(job.id(), 0.0);
		}
		roleWeights = weights;
		roleIntervalSeconds = clamp(roleIntervalSeconds, 10, 3600);
		maxPotionsPerDay = clamp(maxPotionsPerDay, 0, 1000);
		secondsPerBrew = clamp(secondsPerBrew, 1, 120);
		bottlesPerBrew = clamp(bottlesPerBrew, 1, 3);
		brewsPerFuel = clamp(brewsPerFuel, 1, 100);
		String fuel = brewFuel == null ? "" : brewFuel.trim().toLowerCase(Locale.ROOT);
		if (!fuel.matches("([a-z0-9_.-]+:)?[a-z0-9_./-]+")) {
			warnings.add("alchemist.brewFuel: invalid item id '" + brewFuel + "': minecraft:blaze_powder");
			fuel = "minecraft:blaze_powder";
		}
		brewFuel = fuel.contains(":") ? fuel : "minecraft:" + fuel;
		waterRadius = clamp(waterRadius, 1, 16);
		stockScanSeconds = clamp(stockScanSeconds, 5, 300);
		return warnings;
	}

	/** Books and potions twice as often as scrolls (5 alchemists: 2 books, 1 scrolls, 2 potions). */
	static Map<String, Double> defaultWeights() {
		Map<String, Double> map = new LinkedHashMap<>();
		map.put("books", 2.0);
		map.put("scrolls", 1.0);
		map.put("potions", 2.0);
		return map;
	}

	/** The configured order of the jobs. */
	List<AlchemyJob> order() {
		return AlchemyRoles.parseOrder(jobOrder);
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
