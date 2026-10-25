package dev.guildy.villager;

import dev.guildy.config.Comment;
import dev.guildy.config.Json5;
import dev.guildy.config.Validatable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * supply.json5 - how the builders fetch the materials their construction sites lack (role "Chercher les materiaux"):
 * crafting, smelting and gathering. Free of Minecraft classes (unit tests).
 */
@Comment({
		"Guildy - approvisionnement des chantiers : les batisseurs en role 'Chercher les materiaux' fabriquent, cuisent ou",
		"recoltent ce qui manque aux chantiers de la guilde, puis le deposent au stockage. Pour chaque materiau manquant, par",
		"ordre de preference : fabrication a l'etabli avec ce que contient deja le stockage (recettes vanilla, sur plusieurs",
		"niveaux : buches -> planches -> escaliers...), cuisson simulee a un four du territoire (sable -> verre...), puis",
		"recolte de blocs naturels : dans les zones de mine reelles, dans les zones de coupe de bois (arbres entiers, replantes)",
		"ou hors du territoire (fosses propres creusees niveau par niveau avec une rampe de sortie, arbres naturels).",
		"Ce qu'aucune de ces voies ne peut obtenir reste a deposer par les joueurs (raison affichee dans le menu).",
		"Rechargement a chaud : /guild admin reload"
})
public final class SupplyConfig implements Validatable {
	@Comment("Version du fichier (geree automatiquement, ne pas modifier)")
	public int configVersion = 0;

	@Comment("Les batisseurs vont chercher les materiaux (false = jamais : seuls les joueurs approvisionnent les chantiers)")
	public boolean enabled = true;

	@Comment("Duree (secondes) pendant laquelle l'analyse des besoins de la guilde est reutilisee avant d'etre refaite (1 a 60)")
	public int decisionSeconds = 5;

	@Comment("Materiaux differents examines au plus a chaque analyse (les plus gros manques d'abord, 1 a 512)")
	public int maxWantedItems = 96;

	@Comment("Fabrication a l'etabli (recettes vanilla du serveur, avec ou sans forme ; les recettes speciales sont ignorees)")
	public Crafting crafting = new Crafting();

	@Comment("Cuisson au four (recettes de four vanilla), simulee : le contenu reel des fours n'est jamais touche")
	public Smelting smelting = new Smelting();

	@Comment({
			"Transformations faites dans le monde (pas des recettes) : simulees avec du temps, a cote de l'atelier ou du stockage",
			"(le bloc n'est jamais pose) : buches et bois ecorces (hache), chemin de terre (pelle), terre sterile -> terre (houe),",
			"beton en poudre -> beton et terre -> boue (eau : une source d'eau du territoire, sinon simule si simulateWater)"
	})
	public Transforms transforms = new Transforms();

	@Comment("Recolte de blocs naturels (vraie, comme un joueur avec un outil en fer non enchante)")
	public Gathering gathering = new Gathering();

	@Comment({
			"Sources forcees : objet -> blocs naturels qui le donnent (sinon deduites du butin vanilla des blocs naturels de",
			"villagers.json5 -> realMining). 'tree:<buche>' = arbre naturel de cette buche ; liste vide = jamais recolte.",
			"Exemple : \"minecraft:flint\": [\"minecraft:gravel\"]"
	})
	public Map<String, List<String>> sources = new LinkedHashMap<>();

	@Comment("Objets jamais recoltes ni fabriques par les batisseurs (a deposer par les joueurs), ids ou #tags")
	public List<String> neverFetch = new ArrayList<>();

	@Json5.Section
	public static final class Crafting {
		@Comment("Fabrication activee")
		public boolean enabled = true;
		@Comment({
				"Niveaux de recettes enchaines au plus (1 a 8) : 3 = buches -> planches -> batons -> torches ; 5 = boules d'argile ->",
				"bloc d'argile -> terre cuite -> terre cuite teinte (colorant de fleur) -> terre cuite emaillee"
		})
		public int maxDepth = 5;
		@Comment("Temps (ticks, 20 = 1 seconde) par fabrication (une fabrication = un passage de la recette) ; bonus de vitesse de travail applique")
		public int ticksPerCraft = 20;
		@Comment("Recettes qui tiennent en 2x2 (planches, batons, etabli...) faites a la main, sans etabli")
		public boolean handCrafting = true;
		@Comment({
				"Recettes du tailleur de pierre (escaliers, dalles, murets, variantes taillees : 1 bloc -> 1 escalier) quand le",
				"territoire a un tailleur de pierre ; sinon l'etabli"
		})
		public boolean stonecutter = true;
		@Comment("Temps (ticks) par bloc taille au tailleur de pierre (1 a 400)")
		public int ticksPerCut = 10;
		@Comment("Fabrications au plus par voyage (1 a 512)")
		public int maxCraftsPerTrip = 128;
		@Comment("Intervalle (secondes) entre deux recherches des etablis et des fours du territoire (10 a 3600)")
		public int stationScanSeconds = 60;
		@Comment("Recettes jamais utilisees (ids de recette, ex. \"minecraft:oak_planks\")")
		public List<String> ignoredRecipes = new ArrayList<>();

		void validate() {
			maxDepth = clamp(maxDepth, 1, 8);
			ticksPerCut = clamp(ticksPerCut, 1, 400);
			ticksPerCraft = clamp(ticksPerCraft, 1, 1200);
			maxCraftsPerTrip = clamp(maxCraftsPerTrip, 1, 512);
			stationScanSeconds = clamp(stationScanSeconds, 10, 3600);
			ignoredRecipes = clean(ignoredRecipes);
		}
	}

	@Json5.Section
	public static final class Smelting {
		@Comment("Cuisson activee")
		public boolean enabled = true;
		@Comment("Temps (ticks) par objet cuit (vanilla : 200) ; bonus de vitesse de travail applique (1 a 400)")
		public int ticksPerItem = 20;
		@Comment({
				"Combustibles utilises, par ordre de preference (ids ou #tags ; duree de combustion vanilla). Jamais un objet",
				"demande par un chantier"
		})
		public List<String> fuels = new ArrayList<>(List.of("minecraft:coal", "minecraft:charcoal", "minecraft:coal_block",
				"minecraft:dried_kelp_block", "minecraft:blaze_rod", "#minecraft:logs_that_burn", "#minecraft:planks"));
		@Comment("Le four s'allume (visuel seulement) pendant la cuisson, puis s'eteint ; un four deja utilise n'est jamais touche")
		public boolean lightFurnace = true;
		@Comment("Recettes de four jamais utilisees (ids de recette)")
		public List<String> ignoredRecipes = new ArrayList<>();

		void validate() {
			ticksPerItem = clamp(ticksPerItem, 1, 400);
			fuels = clean(fuels);
			ignoredRecipes = clean(ignoredRecipes);
		}
	}

	@Json5.Section
	public static final class Transforms {
		@Comment("Transformations activees")
		public boolean enabled = true;
		@Comment("Temps (ticks) par objet transforme ; bonus de vitesse de travail applique (1 a 400)")
		public int ticksPerItem = 10;
		@Comment("Buches, tiges et bois ecorces (hache)")
		public boolean stripping = true;
		@Comment("Chemin de terre depuis la terre, l'herbe, le podzol... (pelle)")
		public boolean dirtPaths = true;
		@Comment("Terre sterile -> terre (houe)")
		public boolean coarseDirt = true;
		@Comment("Beton en poudre -> beton (eau)")
		public boolean concrete = true;
		@Comment("Terre -> boue (eau, comme une fiole d'eau versee)")
		public boolean mud = true;
		@Comment("Sans source d'eau dans le territoire : beton et boue simules quand meme (false = une source d'eau est necessaire)")
		public boolean simulateWater = true;

		void validate() {
			ticksPerItem = clamp(ticksPerItem, 1, 400);
		}
	}

	@Json5.Section
	public static final class Gathering {
		@Comment("Recolte activee")
		public boolean enabled = true;
		@Comment("Temps (ticks) pour casser un bloc ; bonus de vitesse de travail applique (2 a 200)")
		public int ticksPerBlock = 10;
		@Comment("Buches coupees par seconde (1 a 8)")
		public int logsPerSecond = 2;

		@Comment("Zones de mine REELLES de la guilde : fosses dans le carre de la zone, loin de l'escalier et des galeries")
		public boolean mineZones = true;
		@Comment("Distance minimum (blocs) entre une fosse et l'escalier ou les galeries d'une mine reelle (1 a 16)")
		public int mineMargin = 3;
		@Comment("Zones de coupe de bois de la guilde (autorisation 'Couper les arbres' du fanion respectee)")
		public boolean forestZones = true;

		@Comment("Hors du territoire : fosses et arbres naturels, jamais dans un territoire (le sien compris)")
		public boolean outside = true;
		@Comment("Hors du territoire : distance maximum (blocs) au coeur de la guilde (16 a 512)")
		public int radius = 96;
		@Comment("Hors du territoire : arbres naturels coupes (entiers) et replantes")
		public boolean outsideTrees = true;

		@Comment("Cote (blocs) d'une fosse (4 a 12)")
		public int pitSide = 6;
		@Comment("Profondeur maximum (blocs) d'une fosse (2 a 4 x (cote - 1) - 1 : la rampe longe les murs)")
		public int pitMaxDepth = 10;
		@Comment("Blocs laisses entre deux fosses (1 a 8)")
		public int pitGap = 2;
		@Comment("Denivele maximum (blocs) du sol d'une fosse (0 a 2)")
		public int pitMaxUneven = 1;
		@Comment("Blocs voulus au minimum dans une nouvelle fosse (sinon le site suivant est cherche, 1 a 256)")
		public int minSiteBlocks = 6;
		@Comment("Fosses gardees en memoire par guilde (les plus anciennes terminees sont oubliees, 8 a 512)")
		public int maxPits = 64;
		@Comment("Sites de fosse examines au plus par seconde (1 a 64)")
		public int sitesPerStep = 6;
		@Comment("Aucun site trouve pour un objet : nouvel essai apres ce delai (secondes, 30 a 3600)")
		public int noSiteRetrySeconds = 300;

		@Comment("Autres blocs casses dans la fosse (terre au-dessus de la pierre...) : rapportes au stockage (true) ou laisses (false)")
		public boolean keepByproducts = false;
		@Comment("Blocs de remplissage gardes dans le sac pour colmater l'eau et la lave (0 a 64)")
		public int fillerReserve = 16;
		@Comment("Arbres coupes : pousse replantee a la base (comme le bucheron)")
		public boolean replant = true;

		void validate() {
			ticksPerBlock = clamp(ticksPerBlock, 2, 200);
			logsPerSecond = clamp(logsPerSecond, 1, 8);
			mineMargin = clamp(mineMargin, 1, 16);
			radius = clamp(radius, 16, 512);
			pitSide = clamp(pitSide, 4, 12);
			pitMaxDepth = clamp(pitMaxDepth, 2, 4 * (pitSide - 1) - 1);
			pitGap = clamp(pitGap, 1, 8);
			pitMaxUneven = clamp(pitMaxUneven, 0, 2);
			minSiteBlocks = clamp(minSiteBlocks, 1, 256);
			maxPits = clamp(maxPits, 8, 512);
			sitesPerStep = clamp(sitesPerStep, 1, 64);
			noSiteRetrySeconds = clamp(noSiteRetrySeconds, 30, 3600);
			fillerReserve = clamp(fillerReserve, 0, 64);
		}
	}

	@Override
	public void validate() {
		decisionSeconds = clamp(decisionSeconds, 1, 60);
		maxWantedItems = clamp(maxWantedItems, 1, 512);
		if (crafting == null) {
			crafting = new Crafting();
		}
		if (smelting == null) {
			smelting = new Smelting();
		}
		if (gathering == null) {
			gathering = new Gathering();
		}
		if (transforms == null) {
			transforms = new Transforms();
		}
		transforms.validate();
		crafting.validate();
		smelting.validate();
		gathering.validate();
		Map<String, List<String>> cleaned = new LinkedHashMap<>();
		if (sources != null) {
			sources.forEach((item, blocks) -> {
				if (item != null && !item.isBlank()) {
					cleaned.put(item.trim(), blocks == null ? new ArrayList<>() : clean(blocks));
				}
			});
		}
		sources = cleaned;
		neverFetch = clean(neverFetch);
		configVersion = 1;
	}

	private static List<String> clean(List<String> values) {
		List<String> out = new ArrayList<>();
		if (values != null) {
			for (String value : values) {
				if (value != null && !value.isBlank() && !out.contains(value.trim())) {
					out.add(value.trim());
				}
			}
		}
		return out;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
