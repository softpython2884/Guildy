package dev.guildy.villager;

import dev.guildy.config.Comment;
import dev.guildy.config.Json5;
import dev.guildy.config.Validatable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * villagers.json5 - guild villagers balance. Must stay free of Minecraft classes (used by unit tests).
 */
@Comment({
		"Guildy - villageois de guilde : arrivees, taches, zones de travail, parcours de garde, stockage.",
		"Les villageois arrivent au bureau d'accueil (construction 'reception'), puis travaillent pour la guilde.",
		"Rechargement a chaud : /guild admin reload"
})
public final class VillagerConfig implements Validatable {
	/**
	 * Current file format; older files get their untouched old defaults migrated in {@link #validate()} (each step:
	 * {@code if (configVersion < N) {...}}, only values still equal to the old default are changed).
	 */
	public static final int CURRENT_VERSION = 6;

	@Comment("Version du fichier (geree automatiquement, ne pas modifier)")
	public int configVersion = 0;

	@Comment("Arrivees de nouveaux villageois")
	public Arrivals arrivals = new Arrivals();

	@Comment("Reglages communs a toutes les taches")
	public Work work = new Work();

	@Comment("Tache 'Ranger la base' : vide les coffres de depot dans les coffres par categorie")
	public Sorter sorter = new Sorter();

	@Comment({
			"Zones de travail : chaque zone est marquee par un fanion (menu Villageois -> Zones et parcours).",
			"Types : mine, coupe de bois, champs, patrouille. Une zone peut sortir du territoire, jamais entrer dans celui",
			"d'une autre guilde. Rayon en chunks autour du chunk du fanion : 0 = son chunk, 1 = 3x3 chunks, 2 = 5x5..."
	})
	public Zones zones = new Zones();

	@Comment("Parcours de garde : suite de points (clic droit) que les gardes parcourent en boucle ou en aller-retour")
	public Routes routes = new Routes();

	@Comment({
			"Tache 'Mineur', mode 'Simule' : minage simule au fanion de sa zone, le butin depend de l'objectif de la zone et",
			"de la profondeur (Y) du fanion. Sans zone : la mine la plus proche, sinon la pierre naturelle a decouvert la plus proche."
	})
	public Miner miner = new Miner();

	@Comment({
			"Tache 'Mineur', mode 'Reel' (zones de mine) : le mineur creuse vraiment. Depuis le fanion, un escalier en colimacon",
			"(1 bloc de large, 3 de haut) descend jusqu'a la profondeur cible, puis il creuse des galeries : une galerie",
			"principale avec des galeries laterales tous les 3 blocs (objectif 'Pierre' : des salles de carriere), toujours dans",
			"le carre de la zone. Il ne casse que des blocs naturels (listes ci-dessous) : jamais un bloc pose par un joueur, un",
			"bloc a donnees (coffre...), un bloc d'une autre guilde, ni un bloc naturel qui porte un bloc non naturel. Il colmate",
			"l'eau et la lave avec un bloc de son sac (sinon il abandonne la galerie) et comble les trous du sol."
	})
	public RealMining realMining = new RealMining();

	@Comment("Tache 'Bucheron' : coupe reelle des arbres naturels de sa zone (sans zone : les plus proches de lui)")
	public Lumberjack lumberjack = new Lumberjack();

	@Comment("Tache 'Fermier' : recolte les cultures mures de sa zone (sans zone : les plus proches de lui) et les replante")
	public Farmer farmer = new Farmer();

	@Comment("Tache 'Garde' : patrouille (zone ou parcours) ou defend le coeur ; pendant les vagues, defend toujours le coeur")
	public Guard guard = new Guard();

	@Comment("Stockage de la guilde (coffres de depot et coffres par categorie)")
	public Storage storage = new Storage();

	@Comment({
			"Expeditions : le villageois quitte la base (il disparait), puis revient apres la duree indiquee avec du butin.",
			"Il part par le bureau d'accueil (ou le coeur) ; pour le Nether, par un portail du Nether DANS le territoire.",
			"lossChance = risque (0 a 1) qu'il ne revienne jamais ; rolls = nombre de tirages dans la table de butin."
	})
	public Expeditions expeditions = new Expeditions();

	@Comment("Chance (0 a 1) que chaque villageois quitte la guilde apres une vague perdue")
	public double defeatLeaveChance = 0.2;

	@Comment("Prenoms donnes aux villageois qui se presentent")
	public List<String> names = new ArrayList<>(List.of(
			"Albert", "Amélie", "Antoine", "Arthur", "Baptiste", "Bernard", "Camille", "Céleste", "Charles", "Claire",
			"Colette", "Denis", "Édith", "Élise", "Émile", "Étienne", "Fernand", "Firmin", "Gaston", "Germaine",
			"Gisèle", "Hector", "Henri", "Hortense", "Irène", "Jacques", "Jeanne", "Joséphine", "Jules", "Léa",
			"Léon", "Louise", "Lucien", "Madeleine", "Marcel", "Margot", "Marius", "Mathilde", "Maurice", "Noémie",
			"Odette", "Paulette", "Philibert", "Raymond", "René", "Rose", "Simone", "Suzanne", "Théo", "Victor",
			"Yvette", "Zoé"));

	@Json5.Section
	public static final class Arrivals {
		@Comment("Nombre maximum de villageois au niveau 1 de la guilde")
		public int baseMaxVillagers = 2;
		@Comment("Villageois supplementaires autorises par niveau de guilde")
		public int maxVillagersPerLevel = 1;
		@Comment("Chance (0 a 1) qu'un villageois se presente chaque matin (si les conditions sont reunies)")
		public double arrivalChance = 0.6;
		@Comment("Delai (secondes) avant le premier candidat apres la construction du bureau d'accueil")
		public int firstArrivalDelaySeconds = 60;
		@Comment("Duree (secondes) pendant laquelle un candidat attend une reponse avant de repartir")
		public int candidateTimeoutSeconds = 600;
		@Comment("Exiger au moins un lit libre dans le territoire pour qu'un villageois se presente")
		public boolean requireFreeBed = true;
	}

	@Json5.Section
	public static final class Zones {
		@Comment("Nombre maximum de zones de travail par guilde (1 a 27)")
		public int maxZones = 12;
		@Comment("Rayon maximum d'une zone, en chunks (0 a 5)")
		public int maxRadiusChunks = 3;
		@Comment("Rayon d'une nouvelle zone, en chunks")
		public int defaultRadiusChunks = 1;
		@Comment("Distance maximum (blocs) entre le coeur et un fanion de zone ou un point de parcours")
		public int maxZoneDistance = 192;
		@Comment({
				"Mode automatique (aucune zone choisie) : rayon (blocs) de recherche autour du villageois",
				"(arbres, cultures, pierre), toujours a moins de maxZoneDistance du coeur"
		})
		public int autoSearchRadius = 48;
		@Comment("Duree (secondes) d'affichage des limites d'une zone ou d'un parcours (bouton 'Afficher')")
		public int showSeconds = 20;
	}

	@Json5.Section
	public static final class Routes {
		@Comment("Nombre maximum de parcours de garde par guilde (0 a 9, 0 = desactive)")
		public int maxRoutes = 6;
		@Comment("Nombre maximum de points par parcours")
		public int maxWaypoints = 16;
		@Comment("Pause (secondes) du garde a chaque point (parcours et zones de patrouille)")
		public int pauseSeconds = 4;
		@Comment("Distance maximum (blocs) entre le parcours et un monstre que le garde poursuit")
		public int leash = 16;
		@Comment("Creation d'un parcours : annulee apres ce delai (secondes) sans nouveau point")
		public int selectionTimeoutSeconds = 120;
	}

	@Json5.Section
	public static final class Work {
		@Comment("Nombre de piles d'objets qu'un travailleur (mineur, bucheron, fermier) transporte avant d'aller deposer")
		public int carryStacks = 9;
		@Comment("Temps maximum (secondes) pour atteindre une destination ; ensuite le villageois est teleporte a cote")
		public int walkTimeoutSeconds = 40;
		@Comment("Temps (secondes) sans progresser avant de considerer le villageois bloque (meme solution)")
		public int stuckSeconds = 12;
		@Comment("Intervalle minimum (secondes) entre deux recherches d'arbres / de cultures")
		public int scanIntervalSeconds = 10;
		@Comment("Vitesse de marche des villageois au travail (multiplicateur, vanilla = 0.5)")
		public double walkSpeed = 0.6;
	}

	@Json5.Section
	public static final class Sorter {
		@Comment("Intervalle (secondes) entre deux verifications des coffres de depot")
		public int intervalSeconds = 10;
		@Comment("Nombre de piles d'objets emportees a chaque voyage")
		public int stacksPerTrip = 6;
		@Comment("Temps maximum (secondes) pour atteindre un coffre avant teleportation")
		public int walkTimeoutSeconds = 30;
	}

	@Json5.Section
	public static final class Miner {
		@Comment("Duree (secondes) d'une expedition de minage a la mine")
		public int tripSeconds = 120;
		@Comment("Intervalle (secondes) entre deux animations de minage")
		public int effectIntervalSeconds = 3;
		@Comment("Objectif 'Tout venant' : nombre de tirages dans la table de butin a la fin de chaque expedition")
		public int rollsPerTrip = 5;
		@Comment({
				"Objectif 'Tout venant' (pierre + minerais) : tables de butin par profondeur. La premiere table dont",
				"maxY >= Y du fanion est utilisee (au-dessus de toutes : la derniere).",
				"entries : item, min, max (quantite), weight (poids du tirage)."
		})
		public List<DepthTable> depthTables = defaultDepthTables();
		@Comment("Objectif 'Minerais' : nombre de tirages par expedition (que des minerais, donc moins de tirages)")
		public int oreRollsPerTrip = 3;
		@Comment("Objectif 'Minerais' : tables de butin par profondeur (meme format)")
		public List<DepthTable> oreTables = defaultOreTables();
		@Comment("Objectif 'Pierre et variantes' : nombre de tirages par expedition")
		public int stoneRollsPerTrip = 6;
		@Comment("Objectif 'Pierre et variantes' : tables de butin par profondeur (meme format)")
		public List<DepthTable> stoneTables = defaultStoneTables();

		/** Loot tables of a mine objective. */
		public List<DepthTable> tables(ZoneObjective objective) {
			return switch (objective) {
				case ORES -> oreTables;
				case STONE -> stoneTables;
				default -> depthTables;
			};
		}

		public int rolls(ZoneObjective objective) {
			return switch (objective) {
				case ORES -> oreRollsPerTrip;
				case STONE -> stoneRollsPerTrip;
				default -> rollsPerTrip;
			};
		}
	}

	@Json5.Section
	public static final class RealMining {
		@Comment("Mode des nouvelles zones de mine (et des anciennes sans reglage) : true = reel, false = simule")
		public boolean defaultReal = true;
		@Comment("Temps (ticks, 20 = 1 seconde) pour casser un bloc ; le bonus de vitesse de travail s'applique (4 blocs/s max)")
		public int ticksPerBlock = 20;
		@Comment("Objectif 'Minerais' : profondeur cible par defaut (Y du sol des galeries ; -54 = couche des diamants)")
		public int oresTargetY = -54;
		@Comment("Objectif 'Tout venant' : profondeur cible par defaut (Y)")
		public int mixedTargetY = 16;
		@Comment("Objectif 'Pierre et variantes' : profondeur par defaut des galeries sous le fanion (blocs)")
		public int stoneDepth = 12;
		@Comment("Profondeur minimum des galeries sous le fanion (blocs) : jamais de tranchee a la surface")
		public int minDepthBelowFanion = 4;
		@Comment("Pas des boutons de profondeur (menu de la zone) et ecart entre deux niveaux de galeries (blocs)")
		public int levelSpacing = 8;
		@Comment("Niveau termine : ouvrir automatiquement le niveau suivant (8 blocs plus bas, sinon plus haut)")
		public boolean autoNextLevel = true;
		@Comment("Cote (blocs) du carre de l'escalier en colimacon (3 a 6) : une marche = 1 bloc plus bas")
		public int stairSide = 4;
		@Comment("Longueur maximum de la galerie principale (blocs ; coupee au bord de la zone)")
		public int mainTunnelLength = 32;
		@Comment("Ecart entre deux galeries laterales (blocs, 2 minimum)")
		public int branchSpacing = 3;
		@Comment("Longueur de chaque galerie laterale (blocs ; coupee au bord de la zone)")
		public int branchLength = 12;
		@Comment("Objectif 'Pierre' : largeur des salles de carriere (blocs, impair)")
		public int roomWidth = 5;
		@Comment("Objectif 'Pierre' : longueur des salles de carriere (blocs)")
		public int roomLength = 5;
		@Comment("Objectif 'Pierre' : hauteur des salles de carriere (blocs)")
		public int roomHeight = 3;
		@Comment("Objectif 'Pierre' : couloir entre deux salles (blocs)")
		public int roomGap = 2;
		@Comment("Objectifs 'Minerais' et 'Tout venant' : blocs de minerai suivis au plus par filon visible depuis la galerie")
		public int maxVeinBlocks = 16;
		@Comment({
				"Blocs de remplissage (pierre taillee...) gardes dans le sac pour colmater et combler : ils restent dans le sac au",
				"coffre de depot (sauf fin de mission). Objectif 'Minerais' : le reste des gravats est laisse sur place."
		})
		public int fillerReserve = 32;
		@Comment("Une torche toutes les N marches / N blocs de galerie (0 = pas de torche)")
		public int torchInterval = 8;
		@Comment("Les torches utilisent le charbon du sac quand il y en a (1 charbon = 4 torches, + 1 baton s'il y en a)")
		public boolean torchesUseCoal = true;
		@Comment("Sans charbon dans le sac : torche gratuite (true) ou pas de torche (false)")
		public boolean freeTorches = true;
		@Comment("Trou dans le sol et aucun bloc de remplissage dans le sac : pierre taillee gratuite (true) ou galerie abandonnee")
		public boolean freeFloorBlocks = true;
		@Comment("Heure (ticks du jour, 0 a 24000) a partir de laquelle le mineur remonte avant la nuit (-1 = jamais)")
		public int surfaceAtTime = 10500;
		@Comment("Blocs naturels que le mineur peut casser (id ou #tag ; les blocs a donnees ne sont jamais casses)")
		public List<String> naturalBlocks = new ArrayList<>(List.of(
				"#minecraft:base_stone_overworld", "#minecraft:substrate_overworld", "#minecraft:sand", "minecraft:gravel",
				"minecraft:clay", "minecraft:sandstone", "minecraft:red_sandstone", "minecraft:calcite", "minecraft:dripstone_block",
				"minecraft:pointed_dripstone", "minecraft:smooth_basalt", "minecraft:magma_block", "minecraft:snow_block",
				"minecraft:infested_stone", "minecraft:infested_deepslate"));
		@Comment("Minerais (cassables, suivis en filon ; seuls gardes avec l'objectif 'Minerais')")
		public List<String> oreBlocks = new ArrayList<>(List.of(
				"#minecraft:ores", "#minecraft:gold_ores", "minecraft:raw_iron_block", "minecraft:raw_copper_block"));
		@Comment("Objets du sac utilisables pour colmater l'eau et la lave et combler les trous (blocs pleins, jamais de sable)")
		public List<String> fillerItems = new ArrayList<>(List.of(
				"minecraft:cobblestone", "minecraft:cobbled_deepslate", "minecraft:dirt", "minecraft:stone", "minecraft:deepslate",
				"minecraft:andesite", "minecraft:diorite", "minecraft:granite", "minecraft:tuff", "minecraft:calcite",
				"minecraft:netherrack"));
	}

	@Json5.Section
	public static final class Lumberjack {
		@Comment("Nombre maximum de buches d'un arbre (les arbres plus grands sont ignores)")
		public int maxLogsPerTree = 80;
		@Comment("Replanter une pousse a la base de l'arbre coupe (si le sol le permet)")
		public boolean replant = true;
	}

	@Json5.Section
	public static final class Farmer {
		@Comment({
				"Cultures sous abri : hauteur (blocs) au-dessus et en dessous du fanion (sans zone : du villageois)",
				"ou les chercher en plus de la surface"
		})
		public int verticalRange = 3;
		@Comment("Nombre maximum de cultures recoltees par seconde")
		public int harvestPerSecond = 3;
	}

	@Json5.Section
	public static final class Guard {
		@Comment("Rayon (blocs) de surveillance du garde (autour du coeur sans zone, autour de lui en patrouille)")
		public int radius = 16;
		@Comment("Zone de patrouille : marge (blocs) autour de la zone ou le garde attaque encore les monstres")
		public int zoneMargin = 8;
		@Comment("Degats par coup au niveau 1 de la guilde")
		public double damage = 4.0;
		@Comment("Degats supplementaires par niveau de guilde")
		public double damagePerLevel = 0.5;
		@Comment("Delai (ticks, 20 = 1 seconde) entre deux coups")
		public int attackCooldownTicks = 20;
		@Comment("Distance (blocs) a laquelle le garde peut frapper")
		public double attackReach = 2.5;
		@Comment("Abandonner une cible qu'on n'arrive pas a atteindre apres ce delai (secondes)")
		public int chaseTimeoutSeconds = 20;
		@Comment("Monstres ignores par les gardes")
		public List<String> ignoredMobs = new ArrayList<>(List.of("minecraft:creeper", "minecraft:phantom", "minecraft:ghast", "minecraft:enderman"));
	}

	@Json5.Section
	public static final class Storage {
		@Comment({
				"Categories de rangement, dans l'ordre de priorite (la premiere qui correspond l'emporte).",
				"Chaque valeur est un id d'objet (minecraft:diamond) ou un tag d'objet precede de # (#minecraft:logs)."
		})
		public LinkedHashMap<String, List<String>> categories = defaultCategories();
		@Comment("Categorie implicite des objets qui ne correspondent a aucune categorie (ou sans coffre dedie)")
		public String fallbackCategory = "Divers";
		@Comment("Nombre maximum de conteneurs enregistres par guilde")
		public int maxChests = 48;
	}

	@Json5.Section
	public static final class Expeditions {
		@Comment({
				"Expedition 'Exploration' (foret, plaines, grottes de surface) = destination par defaut des explorations",
				"(id plains, nom dans lang.json : villager.destination.plains)"
		})
		public Expedition exploration = Expedition.exploration();
		@Comment({
				"Expedition 'Nether' (necessite un portail du Nether dans le territoire) = destination par defaut des",
				"expeditions au Nether (id nether_wastes, nom dans lang.json : villager.destination.nether_wastes)"
		})
		public Expedition nether = Expedition.nether();
		@Comment("Objets des 'objectifs prioritaires' d'un villageois : leur poids dans la table de la destination est multiplie (1 a 20)")
		public double wishlistWeightMultiplier = 3.0;
		@Comment({
				"Autres destinations, choisies dans le menu du villageois (bouton 'Destination') : biomes, constructions, Nether.",
				"id (unique : lettres minuscules, chiffres, _), name (nom affiche), icon (objet), kind (exploration, ou nether :",
				"portail du Nether requis), minLevel (niveau de guilde), durationSeconds (0 = durationMultiplier x la duree de la",
				"destination par defaut du meme type), lossChance, rolls, treasureMapChance, uniqueChanceMultiplier (x la chance",
				"'expedition' d'uniques.json5 ; 0 = jamais), loot (meme format ; minecraft:enchanted_book = livre a enchantement",
				"aleatoire). Destination inconnue ou retiree : le villageois part vers la destination par defaut de son type."
		})
		public List<ExpeditionDestination> destinations = ExpeditionDestinations.defaults();
	}

	/** One kind of expedition: duration, risk and loot. */
	public static final class Expedition {
		/** Duration of the trip in seconds (shortened by the "work speed" upgrade). */
		public int durationSeconds = 480;
		/** Chance (0..1) that the villager never comes back. */
		public double lossChance = 0.05;
		/** Weighted rolls in the loot table per trip. */
		public int rolls = 6;
		/** Chance (0..1) to also bring back a treasure map. */
		public double treasureMapChance = 0.0;
		public List<LootEntry> loot = new ArrayList<>();

		public Expedition() {
		}

		static Expedition exploration() {
			Expedition e = new Expedition();
			e.durationSeconds = 480;
			e.lossChance = 0.05;
			e.rolls = 6;
			e.treasureMapChance = 0.08;
			e.loot = new ArrayList<>(List.of(
					new LootEntry("minecraft:oak_sapling", 1, 4, 6), new LootEntry("minecraft:birch_sapling", 1, 3, 4),
					new LootEntry("minecraft:spruce_sapling", 1, 3, 4), new LootEntry("minecraft:wheat_seeds", 2, 8, 6),
					new LootEntry("minecraft:pumpkin_seeds", 1, 3, 3), new LootEntry("minecraft:melon_seeds", 1, 3, 3),
					new LootEntry("minecraft:sweet_berries", 2, 6, 5), new LootEntry("minecraft:apple", 1, 4, 5),
					new LootEntry("minecraft:sugar_cane", 1, 4, 4), new LootEntry("minecraft:cocoa_beans", 1, 3, 2),
					new LootEntry("minecraft:leather", 1, 4, 5), new LootEntry("minecraft:feather", 2, 6, 4),
					new LootEntry("minecraft:string", 1, 4, 4), new LootEntry("minecraft:bone", 1, 4, 4),
					new LootEntry("minecraft:flint", 1, 4, 3), new LootEntry("minecraft:clay_ball", 2, 8, 3),
					new LootEntry("minecraft:emerald", 1, 2, 2), new LootEntry("minecraft:name_tag", 1, 1, 1),
					new LootEntry("minecraft:saddle", 1, 1, 1)));
			return e;
		}

		static Expedition nether() {
			Expedition e = new Expedition();
			e.durationSeconds = 720;
			e.lossChance = 0.12;
			e.rolls = 7;
			e.loot = new ArrayList<>(List.of(
					new LootEntry("minecraft:quartz", 4, 12, 8), new LootEntry("minecraft:gold_nugget", 4, 12, 6),
					new LootEntry("minecraft:glowstone_dust", 2, 8, 5), new LootEntry("minecraft:nether_wart", 1, 4, 4),
					new LootEntry("minecraft:soul_sand", 4, 12, 4), new LootEntry("minecraft:blackstone", 8, 24, 4),
					new LootEntry("minecraft:crimson_stem", 2, 8, 3), new LootEntry("minecraft:warped_stem", 2, 8, 3),
					new LootEntry("minecraft:magma_cream", 1, 3, 3), new LootEntry("minecraft:blaze_rod", 1, 2, 2),
					new LootEntry("minecraft:obsidian", 1, 3, 2), new LootEntry("minecraft:crying_obsidian", 1, 2, 1),
					new LootEntry("minecraft:ghast_tear", 1, 1, 1), new LootEntry("minecraft:ancient_debris", 1, 1, 1)));
			return e;
		}

		/** Loot table usable by {@link DepthLoot#roll}. */
		DepthTable table() {
			return new DepthTable(0, loot);
		}

		void validate() {
			durationSeconds = clamp(durationSeconds, 1, 86_400);
			lossChance = clamp(lossChance, 0.0, 1.0);
			rolls = clamp(rolls, 0, 64);
			treasureMapChance = clamp(treasureMapChance, 0.0, 1.0);
			if (loot == null) {
				loot = new ArrayList<>();
			}
			loot.removeIf(entry -> entry == null || entry.item == null || entry.weight <= 0);
		}
	}

	/** A loot table used when the miner's work position is at or below {@code maxY}. */
	public static final class DepthTable {
		public int maxY;
		public List<LootEntry> entries = new ArrayList<>();

		public DepthTable() {
		}

		public DepthTable(int maxY, List<LootEntry> entries) {
			this.maxY = maxY;
			this.entries = new ArrayList<>(entries);
		}
	}

	public static final class LootEntry {
		public String item = "minecraft:cobblestone";
		public int min = 1;
		public int max = 1;
		public int weight = 1;

		public LootEntry() {
		}

		public LootEntry(String item, int min, int max, int weight) {
			this.item = item;
			this.min = min;
			this.max = max;
			this.weight = weight;
		}
	}

	// ------------------------------------------------------------------ defaults

	public static List<DepthTable> defaultDepthTables() {
		List<DepthTable> tables = new ArrayList<>();
		// Deep slate layer: best ores, diamonds only here (rare).
		tables.add(new DepthTable(-24, List.of(
				new LootEntry("minecraft:cobbled_deepslate", 2, 6, 34),
				new LootEntry("minecraft:raw_iron", 1, 3, 16),
				new LootEntry("minecraft:raw_gold", 1, 2, 10),
				new LootEntry("minecraft:redstone", 2, 5, 12),
				new LootEntry("minecraft:lapis_lazuli", 2, 5, 9),
				new LootEntry("minecraft:coal", 1, 3, 6),
				new LootEntry("minecraft:tuff", 1, 3, 8),
				new LootEntry("minecraft:diamond", 1, 1, 3),
				new LootEntry("minecraft:emerald", 1, 1, 2))));
		tables.add(new DepthTable(16, List.of(
				new LootEntry("minecraft:cobblestone", 2, 6, 34),
				new LootEntry("minecraft:cobbled_deepslate", 1, 4, 8),
				new LootEntry("minecraft:coal", 1, 4, 16),
				new LootEntry("minecraft:raw_iron", 1, 3, 16),
				new LootEntry("minecraft:raw_copper", 2, 5, 12),
				new LootEntry("minecraft:raw_gold", 1, 2, 6),
				new LootEntry("minecraft:redstone", 1, 4, 5),
				new LootEntry("minecraft:lapis_lazuli", 1, 3, 4),
				new LootEntry("minecraft:gravel", 1, 3, 6))));
		tables.add(new DepthTable(64, List.of(
				new LootEntry("minecraft:cobblestone", 2, 6, 44),
				new LootEntry("minecraft:coal", 1, 3, 20),
				new LootEntry("minecraft:raw_copper", 1, 4, 14),
				new LootEntry("minecraft:raw_iron", 1, 2, 10),
				new LootEntry("minecraft:gravel", 1, 3, 8),
				new LootEntry("minecraft:flint", 1, 2, 4))));
		// Surface and mountains.
		tables.add(new DepthTable(320, List.of(
				new LootEntry("minecraft:cobblestone", 2, 6, 50),
				new LootEntry("minecraft:coal", 1, 3, 18),
				new LootEntry("minecraft:raw_iron", 1, 2, 10),
				new LootEntry("minecraft:raw_copper", 1, 3, 10),
				new LootEntry("minecraft:flint", 1, 2, 6),
				new LootEntry("minecraft:emerald", 1, 1, 1))));
		return tables;
	}

	/** "Minerais": ores only (fewer rolls than the mixed tables), diamonds only deep. */
	public static List<DepthTable> defaultOreTables() {
		List<DepthTable> tables = new ArrayList<>();
		tables.add(new DepthTable(-24, List.of(
				new LootEntry("minecraft:raw_iron", 1, 3, 22),
				new LootEntry("minecraft:raw_gold", 1, 3, 14),
				new LootEntry("minecraft:redstone", 2, 6, 18),
				new LootEntry("minecraft:lapis_lazuli", 2, 6, 12),
				new LootEntry("minecraft:coal", 1, 3, 8),
				new LootEntry("minecraft:diamond", 1, 1, 5),
				new LootEntry("minecraft:emerald", 1, 1, 2))));
		tables.add(new DepthTable(16, List.of(
				new LootEntry("minecraft:coal", 1, 4, 22),
				new LootEntry("minecraft:raw_iron", 1, 3, 24),
				new LootEntry("minecraft:raw_copper", 2, 5, 20),
				new LootEntry("minecraft:raw_gold", 1, 2, 10),
				new LootEntry("minecraft:redstone", 1, 4, 10),
				new LootEntry("minecraft:lapis_lazuli", 1, 3, 8))));
		tables.add(new DepthTable(64, List.of(
				new LootEntry("minecraft:coal", 1, 4, 34),
				new LootEntry("minecraft:raw_copper", 2, 5, 28),
				new LootEntry("minecraft:raw_iron", 1, 3, 26),
				new LootEntry("minecraft:raw_gold", 1, 1, 4),
				new LootEntry("minecraft:lapis_lazuli", 1, 2, 4))));
		tables.add(new DepthTable(320, List.of(
				new LootEntry("minecraft:coal", 1, 4, 40),
				new LootEntry("minecraft:raw_iron", 1, 3, 26),
				new LootEntry("minecraft:raw_copper", 1, 4, 20),
				new LootEntry("minecraft:emerald", 1, 1, 4))));
		return tables;
	}

	/** "Pierre et variantes": building stone in volume (cobbled deepslate and tuff deep down). */
	public static List<DepthTable> defaultStoneTables() {
		List<DepthTable> tables = new ArrayList<>();
		tables.add(new DepthTable(0, List.of(
				new LootEntry("minecraft:cobbled_deepslate", 3, 8, 50),
				new LootEntry("minecraft:tuff", 2, 6, 20),
				new LootEntry("minecraft:cobblestone", 1, 4, 10),
				new LootEntry("minecraft:gravel", 1, 4, 8),
				new LootEntry("minecraft:calcite", 1, 3, 6),
				new LootEntry("minecraft:dripstone_block", 1, 3, 6))));
		tables.add(new DepthTable(48, List.of(
				new LootEntry("minecraft:cobblestone", 3, 8, 44),
				new LootEntry("minecraft:andesite", 2, 6, 12),
				new LootEntry("minecraft:diorite", 2, 6, 12),
				new LootEntry("minecraft:granite", 2, 6, 12),
				new LootEntry("minecraft:gravel", 1, 4, 8),
				new LootEntry("minecraft:tuff", 1, 4, 6),
				new LootEntry("minecraft:clay_ball", 2, 6, 3),
				new LootEntry("minecraft:dripstone_block", 1, 2, 3))));
		tables.add(new DepthTable(320, List.of(
				new LootEntry("minecraft:cobblestone", 3, 8, 46),
				new LootEntry("minecraft:andesite", 2, 6, 12),
				new LootEntry("minecraft:diorite", 2, 6, 10),
				new LootEntry("minecraft:granite", 2, 6, 10),
				new LootEntry("minecraft:gravel", 1, 4, 8),
				new LootEntry("minecraft:dirt", 1, 4, 6),
				new LootEntry("minecraft:calcite", 1, 3, 4),
				new LootEntry("minecraft:flint", 1, 2, 4))));
		return tables;
	}

	public static LinkedHashMap<String, List<String>> defaultCategories() {
		LinkedHashMap<String, List<String>> map = new LinkedHashMap<>();
		map.put("Bois", new ArrayList<>(List.of(
				"#minecraft:logs", "#minecraft:planks", "#minecraft:wooden_slabs", "#minecraft:wooden_stairs",
				"#minecraft:wooden_fences", "#minecraft:saplings", "#minecraft:leaves", "minecraft:stick")));
		map.put("Pierre & terre", new ArrayList<>(List.of(
				"#minecraft:stone_crafting_materials", "#minecraft:stone_tool_materials", "#minecraft:dirt", "#minecraft:grass_blocks",
				"#minecraft:mud", "#minecraft:moss_blocks", "#minecraft:sand",
				"#minecraft:stone_bricks", "minecraft:cobblestone", "minecraft:stone", "minecraft:cobbled_deepslate",
				"minecraft:deepslate", "minecraft:granite", "minecraft:diorite", "minecraft:andesite", "minecraft:tuff",
				"minecraft:calcite", "minecraft:gravel", "minecraft:clay_ball", "minecraft:netherrack")));
		map.put("Minerais & lingots", new ArrayList<>(List.of(
				"#minecraft:coals", "#minecraft:iron_ores", "#minecraft:gold_ores", "#minecraft:copper_ores",
				"#minecraft:coal_ores", "#minecraft:diamond_ores", "#minecraft:emerald_ores", "#minecraft:lapis_ores",
				"#minecraft:redstone_ores", "minecraft:raw_iron", "minecraft:raw_gold", "minecraft:raw_copper",
				"minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:copper_ingot", "minecraft:iron_nugget",
				"minecraft:gold_nugget", "minecraft:diamond", "minecraft:emerald", "minecraft:lapis_lazuli",
				"minecraft:redstone", "minecraft:quartz", "minecraft:amethyst_shard", "minecraft:flint")));
		map.put("Cultures & nourriture", new ArrayList<>(List.of(
				"#minecraft:villager_plantable_seeds", "#minecraft:meat", "#minecraft:fishes", "minecraft:wheat",
				"minecraft:carrot", "minecraft:potato", "minecraft:beetroot", "minecraft:bread", "minecraft:apple",
				"minecraft:melon_slice", "minecraft:pumpkin", "minecraft:sugar_cane", "minecraft:egg")));
		map.put("Butin de monstres", new ArrayList<>(List.of(
				"minecraft:rotten_flesh", "minecraft:bone", "minecraft:bone_meal", "minecraft:string",
				"minecraft:spider_eye", "minecraft:gunpowder", "minecraft:ender_pearl", "minecraft:slime_ball",
				"minecraft:phantom_membrane", "minecraft:blaze_rod", "minecraft:ghast_tear", "minecraft:magma_cream")));
		map.put("Outils & armes", new ArrayList<>(List.of(
				"#minecraft:swords", "#minecraft:axes", "#minecraft:pickaxes", "#minecraft:shovels", "#minecraft:hoes",
				"#minecraft:spears", "#minecraft:head_armor", "#minecraft:chest_armor", "#minecraft:leg_armor",
				"#minecraft:foot_armor", "#minecraft:arrows", "minecraft:bow", "minecraft:crossbow", "minecraft:shield",
				"minecraft:trident", "minecraft:mace", "minecraft:fishing_rod", "minecraft:shears",
				"minecraft:flint_and_steel")));
		return map;
	}

	// ------------------------------------------------------------------ helpers

	/** Max villagers for a guild level (never negative). */
	public int maxVillagers(int guildLevel) {
		return Math.max(0, arrivals.baseMaxVillagers + Math.max(0, guildLevel - 1) * arrivals.maxVillagersPerLevel);
	}

	public double guardDamage(int guildLevel) {
		return guard.damage + Math.max(0, guildLevel - 1) * guard.damagePerLevel;
	}

	@Override
	public void validate() {
		if (expeditions == null) {
			expeditions = new Expeditions();
		}
		if (expeditions.exploration == null) {
			expeditions.exploration = Expedition.exploration();
		}
		if (expeditions.nether == null) {
			expeditions.nether = Expedition.nether();
		}
		expeditions.exploration.validate();
		expeditions.nether.validate();
		// Version 6 (expedition destinations): files written before receive the default destinations once.
		ExpeditionDestinations.validateAndLog(expeditions, configVersion < ExpeditionDestinations.CONFIG_VERSION);
		if (arrivals == null) {
			arrivals = new Arrivals();
		}
		if (work == null) {
			work = new Work();
		}
		if (sorter == null) {
			sorter = new Sorter();
		}
		if (miner == null) {
			miner = new Miner();
		}
		if (realMining == null) {
			realMining = new RealMining();
		}
		validateRealMining(realMining);
		if (lumberjack == null) {
			lumberjack = new Lumberjack();
		}
		if (farmer == null) {
			farmer = new Farmer();
		}
		if (guard == null) {
			guard = new Guard();
		}
		if (storage == null) {
			storage = new Storage();
		}
		if (zones == null) {
			zones = new Zones();
		}
		if (routes == null) {
			routes = new Routes();
		}
		zones.maxZones = clamp(zones.maxZones, 1, 27);
		zones.maxRadiusChunks = clamp(zones.maxRadiusChunks, 0, 5);
		zones.defaultRadiusChunks = clamp(zones.defaultRadiusChunks, 0, zones.maxRadiusChunks);
		zones.maxZoneDistance = clamp(zones.maxZoneDistance, 16, 1024);
		zones.autoSearchRadius = clamp(zones.autoSearchRadius, 8, 96);
		zones.showSeconds = clamp(zones.showSeconds, 3, 120);
		routes.maxRoutes = clamp(routes.maxRoutes, 0, 9);
		routes.maxWaypoints = clamp(routes.maxWaypoints, 2, 32);
		routes.pauseSeconds = clamp(routes.pauseSeconds, 0, 60);
		routes.leash = clamp(routes.leash, 4, 48);
		routes.selectionTimeoutSeconds = clamp(routes.selectionTimeoutSeconds, 20, 600);
		arrivals.baseMaxVillagers = clamp(arrivals.baseMaxVillagers, 0, 100);
		arrivals.maxVillagersPerLevel = clamp(arrivals.maxVillagersPerLevel, 0, 20);
		arrivals.arrivalChance = clamp(arrivals.arrivalChance, 0.0, 1.0);
		arrivals.firstArrivalDelaySeconds = clamp(arrivals.firstArrivalDelaySeconds, 1, 3600);
		arrivals.candidateTimeoutSeconds = clamp(arrivals.candidateTimeoutSeconds, 30, 24 * 3600);
		work.carryStacks = clamp(work.carryStacks, 1, 36);
		work.walkTimeoutSeconds = clamp(work.walkTimeoutSeconds, 5, 600);
		work.stuckSeconds = clamp(work.stuckSeconds, 3, 300);
		work.scanIntervalSeconds = clamp(work.scanIntervalSeconds, 10, 600);
		work.walkSpeed = clamp(work.walkSpeed, 0.2, 1.5);
		sorter.intervalSeconds = clamp(sorter.intervalSeconds, 1, 600);
		sorter.stacksPerTrip = clamp(sorter.stacksPerTrip, 1, 27);
		sorter.walkTimeoutSeconds = clamp(sorter.walkTimeoutSeconds, 5, 600);
		miner.tripSeconds = clamp(miner.tripSeconds, 5, 3600);
		miner.rollsPerTrip = clamp(miner.rollsPerTrip, 1, 64);
		miner.oreRollsPerTrip = clamp(miner.oreRollsPerTrip, 1, 64);
		miner.stoneRollsPerTrip = clamp(miner.stoneRollsPerTrip, 1, 64);
		miner.effectIntervalSeconds = clamp(miner.effectIntervalSeconds, 1, 60);
		miner.depthTables = cleanTables(miner.depthTables, VillagerConfig::defaultDepthTables);
		miner.oreTables = cleanTables(miner.oreTables, VillagerConfig::defaultOreTables);
		miner.stoneTables = cleanTables(miner.stoneTables, VillagerConfig::defaultStoneTables);
		lumberjack.maxLogsPerTree = clamp(lumberjack.maxLogsPerTree, 1, 256);
		farmer.verticalRange = clamp(farmer.verticalRange, 0, 8);
		farmer.harvestPerSecond = clamp(farmer.harvestPerSecond, 1, 16);
		guard.radius = clamp(guard.radius, 2, 48);
		guard.zoneMargin = clamp(guard.zoneMargin, 0, 32);
		guard.damage = clamp(guard.damage, 0.0, 100.0);
		guard.damagePerLevel = clamp(guard.damagePerLevel, 0.0, 20.0);
		guard.attackCooldownTicks = clamp(guard.attackCooldownTicks, 1, 200);
		guard.attackReach = clamp(guard.attackReach, 1.0, 6.0);
		guard.chaseTimeoutSeconds = clamp(guard.chaseTimeoutSeconds, 3, 300);
		guard.ignoredMobs = guard.ignoredMobs == null ? new ArrayList<>() : new ArrayList<>(guard.ignoredMobs);
		guard.ignoredMobs.removeIf(id -> id == null || id.isBlank());
		if (storage.categories == null) {
			storage.categories = defaultCategories();
		}
		LinkedHashMap<String, List<String>> categories = new LinkedHashMap<>();
		for (Map.Entry<String, List<String>> entry : storage.categories.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank()) {
				continue;
			}
			List<String> values = entry.getValue() == null ? new ArrayList<>() : new ArrayList<>(entry.getValue());
			values.removeIf(value -> value == null || value.isBlank());
			categories.put(entry.getKey().trim(), values);
		}
		storage.categories = categories;
		if (storage.fallbackCategory == null || storage.fallbackCategory.isBlank()) {
			storage.fallbackCategory = "Divers";
		}
		storage.fallbackCategory = storage.fallbackCategory.trim();
		String fallback = storage.fallbackCategory;
		storage.categories.keySet().removeIf(name -> name.equalsIgnoreCase(fallback));
		storage.maxChests = clamp(storage.maxChests, 1, 256);
		defeatLeaveChance = clamp(defeatLeaveChance, 0.0, 1.0);
		names = names == null ? new ArrayList<>() : new ArrayList<>(names);
		names.removeIf(name -> name == null || name.isBlank());
		if (names.isEmpty()) {
			names = new ArrayList<>(new VillagerConfig().names);
		}
		configVersion = CURRENT_VERSION;
	}

	private static void validateRealMining(RealMining real) {
		real.ticksPerBlock = clamp(real.ticksPerBlock, 5, 200);
		real.oresTargetY = clamp(real.oresTargetY, -2048, 2048);
		real.mixedTargetY = clamp(real.mixedTargetY, -2048, 2048);
		real.stoneDepth = clamp(real.stoneDepth, 1, 256);
		real.minDepthBelowFanion = clamp(real.minDepthBelowFanion, 3, 64);
		real.levelSpacing = clamp(real.levelSpacing, 4, 64);
		real.stairSide = clamp(real.stairSide, 3, 6);
		real.mainTunnelLength = clamp(real.mainTunnelLength, 0, 128);
		real.branchSpacing = clamp(real.branchSpacing, 2, 16);
		real.branchLength = clamp(real.branchLength, 0, 64);
		real.roomWidth = clamp(real.roomWidth, 1, 9);
		real.roomLength = clamp(real.roomLength, 1, 16);
		real.roomHeight = clamp(real.roomHeight, 2, 5);
		real.roomGap = clamp(real.roomGap, 1, 16);
		real.maxVeinBlocks = clamp(real.maxVeinBlocks, 0, 64);
		real.fillerReserve = clamp(real.fillerReserve, 0, 256);
		real.torchInterval = clamp(real.torchInterval, 0, 64);
		real.surfaceAtTime = real.surfaceAtTime < 0 ? -1 : clamp(real.surfaceAtTime, 0, 24000);
		real.naturalBlocks = cleanIds(real.naturalBlocks, new RealMining().naturalBlocks);
		real.oreBlocks = cleanIds(real.oreBlocks, new RealMining().oreBlocks);
		real.fillerItems = cleanIds(real.fillerItems, new RealMining().fillerItems);
	}

	/** Copy without blank entries (trimmed); the defaults when the list is missing. */
	private static List<String> cleanIds(List<String> ids, List<String> defaults) {
		if (ids == null) {
			return defaults;
		}
		List<String> cleaned = new ArrayList<>();
		for (String id : ids) {
			if (id != null && !id.isBlank()) {
				cleaned.add(id.trim());
			}
		}
		return cleaned;
	}

	/** Copy without empty / invalid entries, sorted by maxY; the defaults when nothing usable is left. */
	private static List<DepthTable> cleanTables(List<DepthTable> tables, Supplier<List<DepthTable>> defaults) {
		if (tables == null || tables.isEmpty()) {
			return defaults.get();
		}
		List<DepthTable> cleaned = new ArrayList<>(tables);
		cleaned.removeIf(table -> table == null);
		for (DepthTable table : cleaned) {
			table.entries = table.entries == null ? new ArrayList<>() : new ArrayList<>(table.entries);
			table.entries.removeIf(entry -> entry == null || entry.item == null || entry.item.isBlank() || entry.weight <= 0);
			for (LootEntry entry : table.entries) {
				entry.item = entry.item.trim();
				entry.min = clamp(entry.min, 1, 64);
				entry.max = clamp(entry.max, entry.min, 64);
			}
		}
		cleaned.removeIf(table -> table.entries.isEmpty());
		if (cleaned.isEmpty()) {
			return defaults.get();
		}
		cleaned.sort(Comparator.comparingInt(table -> table.maxY));
		return cleaned;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp(double value, double min, double max) {
		if (Double.isNaN(value)) {
			return min;
		}
		return Math.max(min, Math.min(max, value));
	}
}
