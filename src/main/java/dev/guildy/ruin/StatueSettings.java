package dev.guildy.ruin;

import dev.guildy.config.Comment;
import dev.guildy.config.Json5;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Section {@code cartography.statues} of ruins.json5: the legend research ("La legende du Jaguar celeste") that leads to the
 * grand temple through a trail of jaguar statues, the placement of the statues and their guidance. Free of Minecraft
 * classes (unit tests); {@link #fix} clamps every value.
 */
@Json5.Section
public final class StatueSettings {
	public static final String DEFAULT_TEMPLE = "guildy:ruin/grand_temple_maya";
	public static final String DEFAULT_TEMPLATE = "guildy:ruin/statue_jaguar";

	@Comment({
			"Recherche 'La legende du Jaguar celeste' : elle localise le grand temple le plus proche et donne une carte des statues",
			"de jaguar qui menent au temple. Le grand temple n'est alors plus propose par la recherche de surface normale",
			"(false = plus de legende, le grand temple redevient une ruine de surface comme les autres pour les recherches)"
	})
	public boolean enabled = true;
	@Comment("Structure du grand temple que la legende localise")
	public String templeStructure = DEFAULT_TEMPLE;
	@Comment("Gabarit des statues (pose par le mod au plus pres des points du chemin, face vers la statue suivante)")
	public String template = DEFAULT_TEMPLATE;
	@Comment("Couche du gabarit posee au niveau du sol (les couches en dessous sont des fondations enterrees)")
	public int groundLayer = 1;
	@Comment("Niveau de guilde minimal pour lancer la recherche de la legende")
	public int minGuildLevel = 7;
	@Comment("Cout de la recherche : objets pris dans l'inventaire du joueur {objet: nombre} (refuse en creatif)")
	public Map<String, Integer> cost = cost("minecraft:paper", 16, "minecraft:compass", 1, "minecraft:emerald", 8);
	@Comment("Or du tresor de guilde ajoute au cout (0 = aucun ; permission Tresor requise sinon)")
	public int goldCost = 40;
	@Comment("Minutes de travail d'un cartographe pour la recherche de la legende")
	public double researchMinutes = 16.0;
	@Comment("Rayon de recherche du temple (en cases de la grille de placement du grand temple, comme searchRadius)")
	public int searchRadius = 64;
	@Comment("Nombre de statues du chemin (au minimum : davantage si l'ecart depasse maxSpacing, moins si le temple est proche)")
	public int count = 4;
	@Comment("Nombre maximal de statues d'un chemin")
	public int maxCount = 8;
	@Comment("Ecart minimal entre deux statues (blocs)")
	public int minSpacing = 64;
	@Comment("Ecart maximal entre deux statues (blocs, 0 = pas de limite : toujours 'count' statues)")
	public int maxSpacing = 800;
	@Comment("La derniere statue se dresse entre lastDistanceMin et lastDistanceMax blocs du temple (en vue de sa balise)")
	public int lastDistanceMin = 150;
	public int lastDistanceMax = 250;
	@Comment("Decalage lateral maximal des statues (blocs, jamais la derniere) : le chemin n'est pas une ligne droite")
	public int lateralJitter = 32;
	@Comment({
			"Rayon (blocs) autour du point prevu ou chercher un emplacement assez plat de blocs naturels, hors de l'eau et hors de",
			"tout territoire de guilde (sinon une deuxieme recherche deux fois plus loin, puis la statue est abandonnee)"
	})
	public int siteRadius = 20;
	@Comment("Denivele maximal (blocs) du sol sous la statue (les fondations comblent le reste)")
	public int maxSlope = 3;
	@Comment("Une statue existante a moins de ce rayon (blocs) d'un point prevu est reutilisee par le nouveau chemin")
	public int reuseRadius = 96;
	@Comment("Blocs naturels sur lesquels une statue peut etre posee (id ou #tag) : jamais sur une construction")
	public List<String> groundBlocks = new ArrayList<>(List.of("#minecraft:substrate_overworld", "#minecraft:base_stone_overworld",
			"#minecraft:sand", "#minecraft:terracotta", "minecraft:gravel", "minecraft:clay", "minecraft:sandstone", "minecraft:red_sandstone",
			"minecraft:calcite", "minecraft:snow_block", "minecraft:packed_ice"));
	@Comment("Bloc des fondations posees sous la statue jusqu'au sol")
	public String foundationBlock = "minecraft:mossy_cobblestone";
	@Comment("Statues protegees comme les sanctuaires (ni cassees, ni explosees, ni deplacees au piston)")
	public boolean protect = true;
	@Comment("Rayon (blocs) autour d'une statue : un joueur qui s'en approche voit son regard (particules, son) et recoit l'indice")
	public int guideRadius = 12;
	@Comment("Duree (secondes, 1 a 9) de la trainee de particules vers la statue suivante (ou le temple)")
	public double guideSeconds = 6.0;
	@Comment("Delai (secondes) avant qu'une statue guide de nouveau le meme joueur")
	public int guideCooldownSeconds = 45;
	@Comment("Longueur (blocs) de la trainee de particules")
	public int trailLength = 40;
	@Comment("Zoom des cartes des statues : le plus petit qui montre l'atelier et toutes les statues, entre mapMinZoom et mapMaxZoom (0 a 4)")
	public int mapMinZoom = 2;
	public int mapMaxZoom = 4;
	@Comment("Icone des statues sur la carte (type de decoration vanilla)")
	public String mapDecoration = "minecraft:banner_lime";
	@Comment("Icone de la derniere statue (celle qui voit le temple)")
	public String lastMapDecoration = "minecraft:banner_yellow";

	public StatueSettings() {
	}

	/** Clamps every value (pure; unknown items of the cost are dropped and reported). */
	public void fix(Predicate<String> itemExists, List<String> warnings) {
		templeStructure = id(templeStructure, DEFAULT_TEMPLE);
		template = id(template, DEFAULT_TEMPLATE);
		groundLayer = clamp(groundLayer, 0, 16);
		minGuildLevel = clamp(minGuildLevel, 1, 1000);
		Map<String, Integer> fixed = new LinkedHashMap<>();
		if (cost != null) {
			for (Map.Entry<String, Integer> entry : cost.entrySet()) {
				String item = RuinConfig.normalize(entry.getKey());
				int n = entry.getValue() == null ? 0 : entry.getValue();
				if (!itemExists.test(item)) {
					warnings.add("cartography.statues.cost: unknown item '" + entry.getKey() + "': ignored");
				} else if (n > 0) {
					fixed.merge(item, Math.min(n, 64 * 36), Integer::sum);
				}
			}
		}
		cost = fixed;
		goldCost = clamp(goldCost, 0, 100_000);
		researchMinutes = clamp(researchMinutes, 0.05, 480);
		searchRadius = clamp(searchRadius, 4, 200);
		count = clamp(count, 1, 16);
		maxCount = clamp(maxCount, count, 32);
		minSpacing = clamp(minSpacing, 16, 4096);
		maxSpacing = maxSpacing <= 0 ? 0 : clamp(maxSpacing, minSpacing, 8192);
		lastDistanceMin = clamp(lastDistanceMin, 16, 1024);
		lastDistanceMax = clamp(lastDistanceMax, lastDistanceMin, 2048);
		lateralJitter = clamp(lateralJitter, 0, 256);
		siteRadius = clamp(siteRadius, 0, 48);
		maxSlope = clamp(maxSlope, 0, 8);
		reuseRadius = clamp(reuseRadius, 0, 512);
		List<String> blocks = new ArrayList<>();
		if (groundBlocks != null) {
			for (String block : groundBlocks) {
				if (block == null || block.isBlank()) {
					continue;
				}
				String value = block.trim().toLowerCase(Locale.ROOT);
				blocks.add(value.startsWith("#") ? "#" + RuinConfig.normalize(value.substring(1)) : RuinConfig.normalize(value));
			}
		}
		groundBlocks = blocks.isEmpty() ? new StatueSettings().groundBlocks : blocks;
		foundationBlock = id(foundationBlock, "minecraft:mossy_cobblestone");
		guideRadius = clamp(guideRadius, 2, 64);
		guideSeconds = clamp(guideSeconds, 1, 9);
		guideCooldownSeconds = clamp(guideCooldownSeconds, 1, 3600);
		trailLength = clamp(trailLength, 8, 128);
		mapMinZoom = clamp(mapMinZoom, 0, 4);
		mapMaxZoom = clamp(mapMaxZoom, mapMinZoom, 4);
		mapDecoration = id(mapDecoration, "minecraft:banner_lime");
		lastMapDecoration = id(lastMapDecoration, "minecraft:banner_yellow");
	}

	/** The spacing rules of {@link StatueRules#waypoints}. */
	public StatueRules.Spacing spacing() {
		return new StatueRules.Spacing(count, maxCount, minSpacing, maxSpacing, lastDistanceMin, lastDistanceMax, lateralJitter);
	}

	private static String id(@Nullable String value, String fallback) {
		String normalized = RuinConfig.normalize(value);
		return normalized.isEmpty() ? fallback : normalized;
	}

	private static Map<String, Integer> cost(Object... itemCount) {
		Map<String, Integer> map = new LinkedHashMap<>();
		for (int i = 0; i + 1 < itemCount.length; i += 2) {
			map.put((String) itemCount[i], (Integer) itemCount[i + 1]);
		}
		return map;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp(double value, double min, double max) {
		return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
	}
}
