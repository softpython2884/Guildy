package dev.guildy.wave;

import dev.guildy.config.Comment;
import dev.guildy.config.Json5;
import dev.guildy.config.Validatable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * waves.json5 - night hordes balance. Pure data (no Minecraft classes) so it can be used in unit tests.
 */
@Comment({
		"Guildy - hordes nocturnes : chaque nuit, une horde attaque les bases des guildes presentes.",
		"Heure du jour = horloge de l'Overworld modulo 24000 (13000 = tombee de la nuit, 23000 = aube).",
		"Victoire : survivre jusqu'a l'aube avec un coeur intact. Defaite : integrite du coeur a 0.",
		"Rechargement a chaud : /guild admin reload"
})
public final class WaveConfig implements Validatable {
	/** Current file format; older files get their untouched old defaults migrated in {@link #validate()}. */
	public static final int CURRENT_VERSION = 2;

	@Comment("Version du fichier (geree automatiquement, ne pas modifier)")
	public int configVersion = 0;

	@Comment("Activer les hordes nocturnes")
	public boolean enabled = true;

	@Comment("Horaires et conditions de declenchement")
	public Timing timing = new Timing();

	@Comment({
			"Difficulte D = base + perNight * nuit + perLevel * (niveau - 1) + perExtraPlayer * (membres presents - 1),",
			"bornee a [base, max]. 'nuit' = palier de la guilde (+1 par victoire, recule apres une defaite)."
	})
	public DifficultySettings difficulty = new DifficultySettings();

	@Comment("Apparition des monstres (par groupes, en bordure du territoire)")
	public Spawning spawning = new Spawning();

	@Comment("Renforcement et comportement des monstres de la horde")
	public MobSettings mobs = new MobSettings();

	@Comment("Coeur de la base : points d'integrite et degats subis chaque seconde")
	public HeartSettings heart = new HeartSettings();

	@Comment({
			"Recompenses de victoire, multipliees par un facteur selon la proportion de monstres tues :",
			"facteur = minRewardFactor + (1 - minRewardFactor) * (tues / total)"
	})
	public Rewards rewards = new Rewards();

	@Comment({
			"Consequences d'une defaite (integrite du coeur a 0) : la horde est dispersee immediatement,",
			"une partie du tresor est perdue et le palier de nuit recule (la difficulte baisse : anti-spirale).",
			"Le depart eventuel de villageois est gere par le module villageois. L'equipement des joueurs n'est jamais touche."
	})
	public Defeat defeat = new Defeat();

	@Comment("Butin supplementaire lache par les monstres de la horde a leur mort")
	public KillLoot killLoot = new KillLoot();

	@Comment("Vagues de test lancees par /guild admin wave start <guilde> (meme de jour)")
	public TestSettings test = new TestSettings();

	@Comment({
			"Themes de horde : un theme est tire au sort chaque nuit (pondere par 'weight') parmi ceux dont minNight <= nuit",
			"(et nuit <= maxNight si maxNight >= 0). Champs d'un theme :",
			"  id, name (nom affiche, codes &), weight, minNight, maxNight (-1 = sans limite), barColor (PINK, BLUE, RED, GREEN,",
			"  YELLOW, PURPLE, WHITE), eliteBonus (chance d'elite ajoutee), mobCountMultiplier (multiplie le nombre de monstres),",
			"  forceBoss (true = toujours un boss), bosses (boss possibles), mobs (monstres possibles).",
			"Monstre : entity (id), weight, minDifficulty, maxCount (0 = illimite), gear (peut recevoir armure/arme),",
			"  alwaysHostile (attaque toujours le joueur le plus proche, ex. endermen), spawnHeight (blocs au-dessus du sol, volants).",
			"Boss : entity, name, weight, healthMultiplier, damageMultiplier, gear, alwaysHostile, spawnHeight.",
			"Les nuits de boss (voir difficulty.bossMinNight / bossEveryNights) choisissent un theme ayant des boss."
	})
	public List<Theme> themes = defaultThemes();

	// ------------------------------------------------------------------ sections

	@Json5.Section
	public static final class Timing {
		@Comment("Debut de la nuit (heure du jour en ticks)")
		public int nightStart = 13000;
		@Comment("Aube : fin de la nuit et de la horde (victoire si le coeur tient)")
		public int nightEnd = 23000;
		@Comment("Si personne n'est present a la tombee de la nuit, nouvelle tentative jusqu'a cette heure ; jamais apres")
		public int lateStartUntil = 18000;
		@Comment("Intervalle entre deux tentatives de demarrage (secondes)")
		public int startRetrySeconds = 5;
		@Comment("Distance (blocs) au coeur a laquelle un membre en ligne doit se trouver pour declencher la horde")
		public int activationDistance = 96;
		@Comment("Annuler la horde (sans recompense ni penalite) si aucun membre n'est present pendant ce temps (secondes)")
		public int absentCancelSeconds = 120;
		@Comment("Un saut d'horloge superieur a ce nombre de ticks en une seconde (sommeil, /time set) annule la horde")
		public int maxClockJumpTicks = 600;
		@Comment("Terminer la horde par une victoire des que tous ses monstres ont ete tues (sinon attendre l'aube)")
		public boolean endWhenHordeDefeated = true;
		@Comment("Securite : duree maximale d'une horde (minutes) ; au-dela elle se termine comme a l'aube")
		public int maxWaveMinutes = 20;
	}

	@Json5.Section
	public static final class DifficultySettings {
		@Comment("Difficulte de base (minimum)")
		public double base = 1.0;
		@Comment("Difficulte ajoutee par palier de nuit")
		public double perNight = 0.5;
		@Comment("Difficulte ajoutee par niveau de guilde au-dela du niveau 1")
		public double perLevel = 0.5;
		@Comment("Difficulte ajoutee par membre present supplementaire (au demarrage)")
		public double perExtraPlayer = 0.75;
		@Comment("Difficulte maximale")
		public double max = 20.0;
		@Comment("Nombre de monstres = arrondi(mobsBase + mobsPerDifficulty * D) borne a [minMobs, maxMobsPerWave]")
		public double mobsBase = 6;
		public double mobsPerDifficulty = 3.0;
		public int minMobs = 5;
		public int maxMobsPerWave = 80;
		@Comment("Chance qu'un monstre soit une elite = min(eliteMax, eliteBase + elitePerDifficulty * D) (+ bonus du theme)")
		public double eliteBase = 0.02;
		public double elitePerDifficulty = 0.015;
		public double eliteMax = 0.35;
		@Comment("Nuits de boss : a partir du palier bossMinNight, puis tous les bossEveryNights paliers (0 = jamais)")
		public int bossMinNight = 5;
		public int bossEveryNights = 5;
	}

	@Json5.Section
	public static final class Spawning {
		@Comment("Intervalle entre deux groupes de monstres (secondes)")
		public int spawnGroupIntervalSeconds = 12;
		@Comment("Delai avant le premier groupe (secondes)")
		public int firstGroupDelaySeconds = 6;
		@Comment("Duree visee pour faire apparaitre toute la horde (secondes) : determine la taille des groupes")
		public int spawnPhaseSeconds = 240;
		@Comment("Taille minimale / maximale d'un groupe")
		public int groupSizeMin = 2;
		public int groupSizeMax = 8;
		@Comment("Nombre maximum de monstres de horde vivants par base")
		public int maxAliveMobsPerBase = 30;
		@Comment("Nombre maximum de monstres de horde vivants sur tout le serveur")
		public int globalMaxAliveMobs = 150;
		@Comment("Essais de placement par base et par seconde (au-dela : nouvel essai la seconde suivante)")
		public int spawnAttemptsPerSecond = 16;
		@Comment("Distance minimale (blocs) entre un point d'apparition et un joueur")
		public int minPlayerDistance = 12;
		@Comment("Distance minimale (blocs) entre un point d'apparition et le coeur")
		public int minHeartDistance = 10;
		@Comment({
				"Decalage du point d'apparition par rapport au bord du territoire, en blocs : 1 = premier bloc a l'exterieur.",
				"Avec un minimum >= 1, les monstres apparaissent toujours dehors, jamais sur les remparts construits au bord.",
				"0 ou negatif = peut apparaitre a l'interieur du territoire."
		})
		public int edgeOffsetMin = 1;
		public int edgeOffsetMax = 6;
		@Comment("Si la zone n'est pas chargee/active, le point est rapproche du coeur par pas de N blocs")
		public int walkStep = 8;
		@Comment("Dispersion (blocs) des monstres d'un meme groupe autour du point d'apparition")
		public int groupSpread = 4;
		@Comment("Le boss apparait quand cette fraction de la horde est deja apparue (0 = au debut, 1 = a la fin)")
		public double bossSpawnProgress = 0.4;
		@Comment("Particules et sons a l'apparition")
		public boolean spawnEffects = true;
	}

	@Json5.Section
	public static final class MobSettings {
		@Comment("Multiplicateur de vie = min(maxHealthMultiplier, 1 + healthPerDifficulty * (D - base))")
		public double healthPerDifficulty = 0.08;
		public double maxHealthMultiplier = 3.0;
		@Comment("Multiplicateur de degats = min(maxDamageMultiplier, 1 + damagePerDifficulty * (D - base))")
		public double damagePerDifficulty = 0.05;
		public double maxDamageMultiplier = 2.5;
		@Comment("Distance de poursuite minimale des monstres (blocs)")
		public double followRange = 48;
		@Comment("Les monstres sans cible attaquent le joueur le plus proche DANS le territoire a moins de N blocs")
		public double targetRange = 16;
		@Comment("Portee d'agression des monstres 'alwaysHostile' (endermen...), dans ou hors du territoire")
		public double aggroRange = 24;
		@Comment("Intervalle de pilotage de chaque monstre (secondes) : cible, chemin vers le coeur, blocage")
		public int steeringIntervalSeconds = 2;
		@Comment("Distance des etapes intermediaires vers le coeur (blocs)")
		public int waypointDistance = 24;
		@Comment("Un monstre sans cible immobile (< 1 bloc) pendant ce temps est retire puis reapparait (secondes)")
		public int stuckTimeoutSeconds = 30;
		@Comment("Un monstre introuvable (zone dechargee) pendant ce temps est remplace (secondes)")
		public int lostTimeoutSeconds = 15;
		@Comment("Chance par piece d'armure = min(armorChanceMax, armorChancePerDifficulty * D) (monstres 'gear')")
		public double armorChancePerDifficulty = 0.04;
		public double armorChanceMax = 0.6;
		@Comment("Chance d'arme (main vide) = min(weaponChanceMax, weaponChancePerDifficulty * D)")
		public double weaponChancePerDifficulty = 0.05;
		public double weaponChanceMax = 0.7;
		@Comment("Chance d'equipement ajoutee pour les elites et les boss")
		public double eliteGearBonus = 0.3;
		@Comment("Materiaux d'armure selon la difficulte (le plus haut atteint ; 'material' + _helmet/_chestplate/_leggings/_boots)")
		public List<GearTier> armorTiers = new ArrayList<>(List.of(
				GearTier.of(0, "minecraft:leather"),
				GearTier.of(4, "minecraft:chainmail"),
				GearTier.of(8, "minecraft:iron"),
				GearTier.of(14, "minecraft:diamond")));
		@Comment("Armes selon la difficulte ('material' + _sword)")
		public List<GearTier> weaponTiers = new ArrayList<>(List.of(
				GearTier.of(0, "minecraft:wooden"),
				GearTier.of(3, "minecraft:stone"),
				GearTier.of(8, "minecraft:iron"),
				GearTier.of(15, "minecraft:diamond")));
		@Comment("Elites : multiplicateurs supplementaires de vie et de degats, contour lumineux")
		public double eliteHealthMultiplier = 2.0;
		public double eliteDamageMultiplier = 1.5;
		public boolean eliteGlowing = true;
		@Comment("Boss : taille (attribut scale), resistance au recul, contour lumineux")
		public double bossScale = 1.3;
		public double bossKnockbackResistance = 0.6;
		public boolean bossGlowing = true;
	}

	@Json5.Section
	public static final class HeartSettings {
		@Comment("Integrite du coeur = baseIntegrity + integrityPerLevel * (niveau - 1)")
		public int baseIntegrity = 150;
		public int integrityPerLevel = 30;
		@Comment("Les monstres a moins de 'radius' blocs (horizontalement) et 'verticalRange' blocs (verticalement) du coeur l'endommagent")
		public double radius = 3.0;
		public double verticalRange = 4.0;
		@Comment("Degats par seconde : monstre normal, elite, boss")
		public int damageNormal = 1;
		public int damageElite = 3;
		public int damageBoss = 8;
		@Comment("Alerte (barre d'action) aux membres quand le coeur est attaque, au plus toutes les N secondes")
		public int alertIntervalSeconds = 5;
	}

	@Json5.Section
	public static final class Rewards {
		@Comment("Or verse au tresor = arrondi((goldBase + goldPerDifficulty * D) * facteur)")
		public double goldBase = 4;
		public double goldPerDifficulty = 2.0;
		@Comment("XP de guilde = arrondi((xpBase + xpPerDifficulty * D) * facteur)")
		public double xpBase = 20;
		public double xpPerDifficulty = 10.0;
		@Comment("Facteur de recompense si aucun monstre n'a ete tue (1.0 si tous l'ont ete)")
		public double minRewardFactor = 0.25;
		@Comment({
				"Tirages d'objets pour chaque membre present pendant la horde =",
				"min(itemRollsMax, arrondi((itemRollsBase + itemRollsPerDifficulty * D) * tues / total))"
		})
		public double itemRollsBase = 1;
		public double itemRollsPerDifficulty = 0.25;
		public int itemRollsMax = 6;
		@Comment("Table d'objets (item, min, max, weight, minDifficulty)")
		public List<ItemReward> items = new ArrayList<>(List.of(
				ItemReward.of("minecraft:iron_ingot", 2, 6, 30, 0),
				ItemReward.of("minecraft:gold_ingot", 1, 4, 20, 0),
				ItemReward.of("minecraft:bread", 4, 8, 15, 0),
				ItemReward.of("minecraft:arrow", 8, 16, 15, 0),
				ItemReward.of("minecraft:emerald", 1, 4, 15, 2),
				ItemReward.of("minecraft:experience_bottle", 3, 8, 10, 3),
				ItemReward.of("minecraft:golden_apple", 1, 1, 8, 4),
				ItemReward.of("minecraft:ender_pearl", 1, 3, 6, 5),
				ItemReward.of("minecraft:name_tag", 1, 1, 3, 5),
				ItemReward.of("minecraft:diamond", 1, 2, 8, 6),
				ItemReward.of("minecraft:totem_of_undying", 1, 1, 2, 10),
				ItemReward.of("minecraft:netherite_scrap", 1, 1, 2, 12),
				ItemReward.of("minecraft:enchanted_golden_apple", 1, 1, 1, 14)));
	}

	@Json5.Section
	public static final class Defeat {
		@Comment("Pourcentage du tresor perdu en cas de defaite")
		public int treasuryLossPercent = 25;
		@Comment("Le palier de nuit recule de N (minimum 0)")
		public int nightRollback = 2;
	}

	@Json5.Section
	public static final class KillLoot {
		@Comment("Pepites d'or : chance par monstre ; quantite entre nuggetMin et nuggetMaxBase + nuggetsPerDifficulty * D")
		public double nuggetChance = 0.6;
		public int nuggetMin = 1;
		public int nuggetMaxBase = 2;
		public double nuggetsPerDifficulty = 0.25;
		@Comment("Elites : lingots d'or (min-max) + un objet de la table eliteItems")
		public int eliteGoldMin = 1;
		public int eliteGoldMax = 3;
		public List<ItemReward> eliteItems = new ArrayList<>(List.of(
				ItemReward.of("minecraft:iron_ingot", 1, 3, 20, 0),
				ItemReward.of("minecraft:emerald", 1, 2, 15, 0),
				ItemReward.of("minecraft:experience_bottle", 1, 3, 15, 0),
				ItemReward.of("minecraft:golden_carrot", 2, 4, 10, 0),
				ItemReward.of("minecraft:golden_apple", 1, 1, 5, 3),
				ItemReward.of("minecraft:ender_pearl", 1, 2, 6, 4),
				ItemReward.of("minecraft:diamond", 1, 1, 6, 5)));
		@Comment("Boss : nombre de tirages dans bossItems")
		public int bossItemRolls = 3;
		public List<ItemReward> bossItems = new ArrayList<>(List.of(
				ItemReward.of("minecraft:diamond", 2, 4, 20, 0),
				ItemReward.of("minecraft:emerald", 4, 10, 20, 0),
				ItemReward.of("minecraft:gold_block", 1, 3, 15, 0),
				ItemReward.of("minecraft:golden_apple", 2, 4, 15, 0),
				ItemReward.of("minecraft:experience_bottle", 8, 16, 15, 0),
				ItemReward.of("minecraft:totem_of_undying", 1, 1, 6, 6),
				ItemReward.of("minecraft:netherite_scrap", 1, 2, 6, 10),
				ItemReward.of("minecraft:enchanted_golden_apple", 1, 1, 3, 12)));
		@Comment("Boss : chance de drapeau d'extension et de carte au tresor")
		public double bossFlagChance = 0.35;
		public double bossMapChance = 0.5;
		@Comment("Carte au tresor : point aleatoire entre mapMinDistance et mapMaxDistance blocs du coeur")
		public int mapMinDistance = 200;
		public int mapMaxDistance = 600;
		@Comment("Rang de la carte = 1 + D / mapTierEveryDifficulty, borne a mapMaxTier")
		public double mapTierEveryDifficulty = 7;
		public int mapMaxTier = 3;
	}

	@Json5.Section
	public static final class TestSettings {
		@Comment("Duree d'une vague de test (secondes) ; elle se termine aussi quand tous ses monstres sont morts")
		public int durationSeconds = 300;
		@Comment("Appliquer les resultats (recompenses / penalites / statistiques) des vagues de test ; false = toujours annulee")
		public boolean applyResults = true;
	}

	// ------------------------------------------------------------------ list entries

	public static final class GearTier {
		public double minDifficulty;
		public String material = "minecraft:iron";

		public static GearTier of(double minDifficulty, String material) {
			GearTier tier = new GearTier();
			tier.minDifficulty = minDifficulty;
			tier.material = material;
			return tier;
		}
	}

	public static final class ItemReward {
		public String item = "minecraft:iron_ingot";
		public int min = 1;
		public int max = 1;
		public double weight = 10;
		public double minDifficulty;

		public static ItemReward of(String item, int min, int max, double weight, double minDifficulty) {
			ItemReward reward = new ItemReward();
			reward.item = item;
			reward.min = min;
			reward.max = max;
			reward.weight = weight;
			reward.minDifficulty = minDifficulty;
			return reward;
		}
	}

	public static final class MobEntry {
		public String entity = "minecraft:zombie";
		public double weight = 10;
		public double minDifficulty;
		public int maxCount;
		public boolean gear;
		public boolean alwaysHostile;
		public int spawnHeight;

		public static MobEntry of(String entity, double weight, double minDifficulty, int maxCount) {
			MobEntry entry = new MobEntry();
			entry.entity = entity;
			entry.weight = weight;
			entry.minDifficulty = minDifficulty;
			entry.maxCount = maxCount;
			return entry;
		}

		public MobEntry gear() {
			this.gear = true;
			return this;
		}

		public MobEntry hostile() {
			this.alwaysHostile = true;
			return this;
		}

		public MobEntry height(int blocks) {
			this.spawnHeight = blocks;
			return this;
		}
	}

	public static final class BossDef {
		public String entity = "minecraft:ravager";
		public String name = "&4&lBoss";
		public double weight = 10;
		public double healthMultiplier = 4.0;
		public double damageMultiplier = 1.5;
		public boolean gear;
		public boolean alwaysHostile;
		public int spawnHeight;

		public static BossDef of(String entity, String name, double weight, double healthMultiplier, double damageMultiplier) {
			BossDef boss = new BossDef();
			boss.entity = entity;
			boss.name = name;
			boss.weight = weight;
			boss.healthMultiplier = healthMultiplier;
			boss.damageMultiplier = damageMultiplier;
			return boss;
		}

		public BossDef gear() {
			this.gear = true;
			return this;
		}

		public BossDef hostile() {
			this.alwaysHostile = true;
			return this;
		}
	}

	public static final class Theme {
		public String id = "";
		public String name = "";
		public double weight = 10;
		public int minNight;
		public int maxNight = -1;
		public String barColor = "RED";
		public double eliteBonus;
		public double mobCountMultiplier = 1.0;
		public boolean forceBoss;
		public List<BossDef> bosses = new ArrayList<>();
		public List<MobEntry> mobs = new ArrayList<>();

		public static Theme of(String id, String name, double weight, int minNight, String barColor) {
			Theme theme = new Theme();
			theme.id = id;
			theme.name = name;
			theme.weight = weight;
			theme.minNight = minNight;
			theme.barColor = barColor;
			return theme;
		}

		public boolean hasBosses() {
			return bosses != null && !bosses.isEmpty();
		}

		/** True when this theme may be drawn at the given night step. */
		public boolean availableAt(int night) {
			return weight > 0 && night >= minNight && (maxNight < 0 || night <= maxNight) && mobs != null && !mobs.isEmpty();
		}
	}

	// ------------------------------------------------------------------ defaults

	public static List<Theme> defaultThemes() {
		List<Theme> list = new ArrayList<>();

		Theme undead = Theme.of("undead", "&2Mort-vivants", 30, 0, "GREEN");
		undead.mobs.add(MobEntry.of("minecraft:zombie", 40, 0, 0).gear());
		undead.mobs.add(MobEntry.of("minecraft:skeleton", 25, 0, 0).gear());
		undead.mobs.add(MobEntry.of("minecraft:zombie_villager", 10, 0, 0).gear());
		undead.mobs.add(MobEntry.of("minecraft:husk", 15, 3, 0).gear());
		undead.mobs.add(MobEntry.of("minecraft:stray", 12, 4, 0).gear());
		undead.bosses.add(BossDef.of("minecraft:zombie", "&2&lSeigneur des morts", 10, 12, 1.8).gear());
		undead.bosses.add(BossDef.of("minecraft:husk", "&6&lMomie ancestrale", 6, 12, 1.8).gear());
		list.add(undead);

		Theme spiders = Theme.of("arachnids", "&8Arachnides", 20, 1, "YELLOW");
		spiders.mobs.add(MobEntry.of("minecraft:spider", 45, 0, 0));
		spiders.mobs.add(MobEntry.of("minecraft:cave_spider", 30, 2, 0));
		spiders.mobs.add(MobEntry.of("minecraft:skeleton", 15, 0, 0).gear());
		spiders.bosses.add(BossDef.of("minecraft:spider", "&8&lReine des araignées", 10, 14, 1.8));
		list.add(spiders);

		Theme illagers = Theme.of("illagers", "&7Illageois", 15, 3, "WHITE");
		illagers.mobs.add(MobEntry.of("minecraft:pillager", 40, 0, 0));
		illagers.mobs.add(MobEntry.of("minecraft:vindicator", 30, 2, 0));
		illagers.mobs.add(MobEntry.of("minecraft:witch", 8, 4, 3));
		illagers.mobs.add(MobEntry.of("minecraft:evoker", 5, 6, 2));
		illagers.bosses.add(BossDef.of("minecraft:ravager", "&4&lRavageur de guerre", 10, 3, 1.0));
		list.add(illagers);

		Theme nether = Theme.of("nether", "&cCréatures du Nether", 12, 5, "RED");
		nether.mobs.add(MobEntry.of("minecraft:blaze", 20, 0, 0));
		nether.mobs.add(MobEntry.of("minecraft:wither_skeleton", 20, 0, 0).gear());
		nether.mobs.add(MobEntry.of("minecraft:magma_cube", 18, 0, 0));
		nether.mobs.add(MobEntry.of("minecraft:piglin_brute", 15, 2, 0).gear());
		nether.mobs.add(MobEntry.of("minecraft:hoglin", 15, 2, 0));
		nether.mobs.add(MobEntry.of("minecraft:ghast", 3, 8, 1).height(8));
		nether.bosses.add(BossDef.of("minecraft:wither_skeleton", "&4&lSeigneur du Nether", 10, 10, 1.6).gear());
		list.add(nether);

		Theme end = Theme.of("end", "&5Créatures de l'End", 10, 7, "PURPLE");
		end.mobs.add(MobEntry.of("minecraft:enderman", 35, 0, 0).hostile());
		end.mobs.add(MobEntry.of("minecraft:endermite", 30, 0, 0));
		end.mobs.add(MobEntry.of("minecraft:phantom", 20, 4, 0).height(12));
		end.bosses.add(BossDef.of("minecraft:enderman", "&5&lGardien du Vide", 10, 6, 1.5).hostile());
		list.add(end);

		Theme elite = Theme.of("elite_mix", "&6Horde d'élite mixte", 6, 10, "YELLOW");
		elite.eliteBonus = 0.25;
		elite.mobCountMultiplier = 0.8;
		elite.mobs.add(MobEntry.of("minecraft:zombie", 20, 0, 0).gear());
		elite.mobs.add(MobEntry.of("minecraft:skeleton", 18, 0, 0).gear());
		elite.mobs.add(MobEntry.of("minecraft:spider", 12, 0, 0));
		elite.mobs.add(MobEntry.of("minecraft:vindicator", 12, 0, 0));
		elite.mobs.add(MobEntry.of("minecraft:wither_skeleton", 10, 0, 0).gear());
		elite.mobs.add(MobEntry.of("minecraft:blaze", 8, 0, 0));
		elite.mobs.add(MobEntry.of("minecraft:enderman", 6, 0, 0).hostile());
		elite.bosses.add(BossDef.of("minecraft:vindicator", "&6&lChampion de la horde", 10, 10, 1.4));
		list.add(elite);

		Theme boss = Theme.of("boss_night", "&4Nuit du boss", 4, 8, "PURPLE");
		boss.forceBoss = true;
		boss.mobCountMultiplier = 0.5;
		boss.mobs.add(MobEntry.of("minecraft:zombie", 30, 0, 0).gear());
		boss.mobs.add(MobEntry.of("minecraft:skeleton", 25, 0, 0).gear());
		boss.mobs.add(MobEntry.of("minecraft:vindicator", 15, 5, 0));
		boss.mobs.add(MobEntry.of("minecraft:wither_skeleton", 10, 8, 0).gear());
		boss.bosses.add(BossDef.of("minecraft:ravager", "&4&lRavageur colossal", 10, 4, 1.2));
		boss.bosses.add(BossDef.of("minecraft:wither_skeleton", "&8&lSeigneur squelette", 8, 12, 1.6).gear());
		boss.bosses.add(BossDef.of("minecraft:zombie", "&2&lTitan putride", 8, 15, 1.8).gear());
		list.add(boss);

		return list;
	}

	// ------------------------------------------------------------------ validation

	@Override
	public void validate() {
		if (timing == null) {
			timing = new Timing();
		}
		if (difficulty == null) {
			difficulty = new DifficultySettings();
		}
		if (spawning == null) {
			spawning = new Spawning();
		}
		// Migrations: only values still equal to the old default change (admin edits are kept).
		if (configVersion < 2 && spawning.edgeOffsetMin == -3) {
			// v2: hordes spawn just outside the territory (never on top of border walls).
			spawning.edgeOffsetMin = 1;
		}
		configVersion = CURRENT_VERSION;
		if (mobs == null) {
			mobs = new MobSettings();
		}
		if (heart == null) {
			heart = new HeartSettings();
		}
		if (rewards == null) {
			rewards = new Rewards();
		}
		if (defeat == null) {
			defeat = new Defeat();
		}
		if (killLoot == null) {
			killLoot = new KillLoot();
		}
		if (test == null) {
			test = new TestSettings();
		}

		Timing t = timing;
		t.nightStart = clamp(t.nightStart, 0, 23999);
		t.nightEnd = clamp(t.nightEnd, t.nightStart + 1, 24000);
		t.lateStartUntil = clamp(t.lateStartUntil, t.nightStart + 1, t.nightEnd);
		t.startRetrySeconds = clamp(t.startRetrySeconds, 1, 60);
		t.activationDistance = clamp(t.activationDistance, 8, 512);
		t.absentCancelSeconds = clamp(t.absentCancelSeconds, 10, 3600);
		t.maxClockJumpTicks = clamp(t.maxClockJumpTicks, 40, 24000);
		t.maxWaveMinutes = clamp(t.maxWaveMinutes, 1, 120);

		DifficultySettings d = difficulty;
		d.base = clamp(d.base, 0, 1000);
		d.max = clamp(d.max, d.base, 1000);
		d.perNight = clamp(d.perNight, 0, 100);
		d.perLevel = clamp(d.perLevel, 0, 100);
		d.perExtraPlayer = clamp(d.perExtraPlayer, 0, 100);
		d.mobsBase = clamp(d.mobsBase, 0, 1000);
		d.mobsPerDifficulty = clamp(d.mobsPerDifficulty, 0, 1000);
		d.maxMobsPerWave = clamp(d.maxMobsPerWave, 1, 500);
		d.minMobs = clamp(d.minMobs, 1, d.maxMobsPerWave);
		d.eliteBase = clamp(d.eliteBase, 0, 1);
		d.elitePerDifficulty = clamp(d.elitePerDifficulty, 0, 1);
		d.eliteMax = clamp(d.eliteMax, 0, 1);
		d.bossMinNight = Math.max(0, d.bossMinNight);
		d.bossEveryNights = Math.max(0, d.bossEveryNights);

		Spawning s = spawning;
		s.spawnGroupIntervalSeconds = clamp(s.spawnGroupIntervalSeconds, 1, 300);
		s.firstGroupDelaySeconds = clamp(s.firstGroupDelaySeconds, 0, 300);
		s.spawnPhaseSeconds = clamp(s.spawnPhaseSeconds, 1, 3600);
		s.groupSizeMin = clamp(s.groupSizeMin, 1, 64);
		s.groupSizeMax = clamp(s.groupSizeMax, s.groupSizeMin, 64);
		s.maxAliveMobsPerBase = clamp(s.maxAliveMobsPerBase, 1, 500);
		s.globalMaxAliveMobs = clamp(s.globalMaxAliveMobs, 1, 5000);
		s.spawnAttemptsPerSecond = clamp(s.spawnAttemptsPerSecond, 1, 200);
		s.minPlayerDistance = clamp(s.minPlayerDistance, 0, 64);
		s.minHeartDistance = clamp(s.minHeartDistance, 0, 64);
		s.edgeOffsetMin = clamp(s.edgeOffsetMin, -16, 32);
		s.edgeOffsetMax = clamp(s.edgeOffsetMax, s.edgeOffsetMin, 32);
		s.walkStep = clamp(s.walkStep, 1, 32);
		s.groupSpread = clamp(s.groupSpread, 0, 16);
		s.bossSpawnProgress = clamp(s.bossSpawnProgress, 0, 1);

		MobSettings m = mobs;
		m.healthPerDifficulty = clamp(m.healthPerDifficulty, 0, 10);
		m.maxHealthMultiplier = clamp(m.maxHealthMultiplier, 1, 50);
		m.damagePerDifficulty = clamp(m.damagePerDifficulty, 0, 10);
		m.maxDamageMultiplier = clamp(m.maxDamageMultiplier, 1, 50);
		m.followRange = clamp(m.followRange, 8, 128);
		m.targetRange = clamp(m.targetRange, 0, 64);
		m.aggroRange = clamp(m.aggroRange, 0, 64);
		m.steeringIntervalSeconds = clamp(m.steeringIntervalSeconds, 1, 10);
		m.waypointDistance = clamp(m.waypointDistance, 6, 64);
		m.stuckTimeoutSeconds = clamp(m.stuckTimeoutSeconds, 5, 600);
		m.lostTimeoutSeconds = clamp(m.lostTimeoutSeconds, 2, 600);
		m.armorChancePerDifficulty = clamp(m.armorChancePerDifficulty, 0, 1);
		m.armorChanceMax = clamp(m.armorChanceMax, 0, 1);
		m.weaponChancePerDifficulty = clamp(m.weaponChancePerDifficulty, 0, 1);
		m.weaponChanceMax = clamp(m.weaponChanceMax, 0, 1);
		m.eliteGearBonus = clamp(m.eliteGearBonus, 0, 1);
		m.armorTiers = cleanTiers(m.armorTiers, new MobSettings().armorTiers);
		m.weaponTiers = cleanTiers(m.weaponTiers, new MobSettings().weaponTiers);
		m.eliteHealthMultiplier = clamp(m.eliteHealthMultiplier, 1, 50);
		m.eliteDamageMultiplier = clamp(m.eliteDamageMultiplier, 1, 50);
		m.bossScale = clamp(m.bossScale, 0.5, 4);
		m.bossKnockbackResistance = clamp(m.bossKnockbackResistance, 0, 1);

		HeartSettings h = heart;
		h.baseIntegrity = clamp(h.baseIntegrity, 1, 1_000_000);
		h.integrityPerLevel = clamp(h.integrityPerLevel, 0, 1_000_000);
		h.radius = clamp(h.radius, 0.5, 16);
		h.verticalRange = clamp(h.verticalRange, 0.5, 16);
		h.damageNormal = clamp(h.damageNormal, 0, 1_000_000);
		h.damageElite = clamp(h.damageElite, 0, 1_000_000);
		h.damageBoss = clamp(h.damageBoss, 0, 1_000_000);
		h.alertIntervalSeconds = clamp(h.alertIntervalSeconds, 1, 600);

		Rewards r = rewards;
		r.goldBase = clamp(r.goldBase, 0, 1_000_000);
		r.goldPerDifficulty = clamp(r.goldPerDifficulty, 0, 1_000_000);
		r.xpBase = clamp(r.xpBase, 0, 1_000_000);
		r.xpPerDifficulty = clamp(r.xpPerDifficulty, 0, 1_000_000);
		r.minRewardFactor = clamp(r.minRewardFactor, 0, 1);
		r.itemRollsBase = clamp(r.itemRollsBase, 0, 100);
		r.itemRollsPerDifficulty = clamp(r.itemRollsPerDifficulty, 0, 100);
		r.itemRollsMax = clamp(r.itemRollsMax, 0, 100);
		r.items = cleanRewards(r.items);

		defeat.treasuryLossPercent = clamp(defeat.treasuryLossPercent, 0, 100);
		defeat.nightRollback = clamp(defeat.nightRollback, 0, 1000);

		KillLoot k = killLoot;
		k.nuggetChance = clamp(k.nuggetChance, 0, 1);
		k.nuggetMin = clamp(k.nuggetMin, 0, 64);
		k.nuggetMaxBase = clamp(k.nuggetMaxBase, k.nuggetMin, 64);
		k.nuggetsPerDifficulty = clamp(k.nuggetsPerDifficulty, 0, 64);
		k.eliteGoldMin = clamp(k.eliteGoldMin, 0, 64);
		k.eliteGoldMax = clamp(k.eliteGoldMax, k.eliteGoldMin, 64);
		k.eliteItems = cleanRewards(k.eliteItems);
		k.bossItemRolls = clamp(k.bossItemRolls, 0, 32);
		k.bossItems = cleanRewards(k.bossItems);
		k.bossFlagChance = clamp(k.bossFlagChance, 0, 1);
		k.bossMapChance = clamp(k.bossMapChance, 0, 1);
		k.mapMinDistance = clamp(k.mapMinDistance, 0, 100_000);
		k.mapMaxDistance = clamp(k.mapMaxDistance, k.mapMinDistance, 100_000);
		k.mapTierEveryDifficulty = clamp(k.mapTierEveryDifficulty, 0.1, 1000);
		k.mapMaxTier = clamp(k.mapMaxTier, 1, 10);

		test.durationSeconds = clamp(test.durationSeconds, 10, 3600);

		themes = cleanThemes(themes);
	}

	private static List<GearTier> cleanTiers(List<GearTier> tiers, List<GearTier> fallback) {
		List<GearTier> out = new ArrayList<>();
		if (tiers != null) {
			for (GearTier tier : tiers) {
				if (tier != null && tier.material != null && !tier.material.isBlank()) {
					tier.material = tier.material.trim().toLowerCase(Locale.ROOT);
					tier.minDifficulty = Math.max(0, tier.minDifficulty);
					out.add(tier);
				}
			}
		}
		if (out.isEmpty()) {
			out.addAll(fallback);
		}
		out.sort((a, b) -> Double.compare(a.minDifficulty, b.minDifficulty));
		return out;
	}

	private static List<ItemReward> cleanRewards(List<ItemReward> rewards) {
		List<ItemReward> out = new ArrayList<>();
		if (rewards == null) {
			return out;
		}
		for (ItemReward reward : rewards) {
			if (reward == null || reward.item == null || reward.item.isBlank()) {
				continue;
			}
			reward.item = reward.item.trim().toLowerCase(Locale.ROOT);
			reward.min = clamp(reward.min, 1, 6400);
			reward.max = clamp(reward.max, reward.min, 6400);
			reward.weight = Math.max(0, reward.weight);
			reward.minDifficulty = Math.max(0, reward.minDifficulty);
			out.add(reward);
		}
		return out;
	}

	private static List<Theme> cleanThemes(List<Theme> list) {
		List<Theme> out = new ArrayList<>();
		if (list != null) {
			for (Theme theme : list) {
				if (theme == null) {
					continue;
				}
				if (theme.id == null || theme.id.isBlank()) {
					theme.id = "theme" + out.size();
				}
				if (theme.name == null || theme.name.isBlank()) {
					theme.name = theme.id;
				}
				theme.weight = Math.max(0, theme.weight);
				theme.minNight = Math.max(0, theme.minNight);
				theme.maxNight = Math.max(-1, theme.maxNight);
				theme.barColor = theme.barColor == null ? "RED" : theme.barColor.trim().toUpperCase(Locale.ROOT);
				theme.eliteBonus = clamp(theme.eliteBonus, 0, 1);
				theme.mobCountMultiplier = clamp(theme.mobCountMultiplier, 0, 10);
				List<MobEntry> entries = new ArrayList<>();
				if (theme.mobs != null) {
					for (MobEntry entry : theme.mobs) {
						if (entry == null || entry.entity == null || entry.entity.isBlank()) {
							continue;
						}
						entry.entity = entry.entity.trim().toLowerCase(Locale.ROOT);
						entry.weight = Math.max(0, entry.weight);
						entry.minDifficulty = Math.max(0, entry.minDifficulty);
						entry.maxCount = Math.max(0, entry.maxCount);
						entry.spawnHeight = clamp(entry.spawnHeight, 0, 64);
						entries.add(entry);
					}
				}
				theme.mobs = entries;
				List<BossDef> bosses = new ArrayList<>();
				if (theme.bosses != null) {
					for (BossDef boss : theme.bosses) {
						if (boss == null || boss.entity == null || boss.entity.isBlank()) {
							continue;
						}
						boss.entity = boss.entity.trim().toLowerCase(Locale.ROOT);
						if (boss.name == null || boss.name.isBlank()) {
							boss.name = "&4&lBoss";
						}
						boss.weight = Math.max(0, boss.weight);
						boss.healthMultiplier = clamp(boss.healthMultiplier, 0.1, 100);
						boss.damageMultiplier = clamp(boss.damageMultiplier, 0.1, 100);
						boss.spawnHeight = clamp(boss.spawnHeight, 0, 64);
						bosses.add(boss);
					}
				}
				theme.bosses = bosses;
				out.add(theme);
			}
		}
		if (out.isEmpty()) {
			out.addAll(defaultThemes());
		}
		return out;
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
