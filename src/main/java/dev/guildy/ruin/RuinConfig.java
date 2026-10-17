package dev.guildy.ruin;

import dev.guildy.Guildy;
import dev.guildy.config.Comment;
import dev.guildy.config.Json5;
import dev.guildy.config.Validatable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * ruins.json5 - ruin guardians (one definition per ruin id), encounter settings and the cartographer's research.
 * Free of Minecraft classes except in {@link #validate()} (unit tests use {@link #rebuild}).
 */
@Comment({
		"Guildy - ruines : des ruines generees dans l'Overworld et le Nether, chacune gardee par un boss.",
		"Le boss dort jusqu'a la lecture d'un parchemin d'incantation pres de son point d'apparition (rituel qui draine le sang",
		"des joueurs presents, voir 'summon') ; vaincu, il revient apres 'respawnDays' jours.",
		"Le villageois Cartographe (construction 'Atelier du cartographe') localise les ruines : carte (Overworld) ou",
		"boussole (Nether). Commandes admin : /guild admin ruin list | info | awaken | reset | locate <id> | map <joueur> <id>",
		"| incantation <joueur> [nombre] | threat <niveau> (menace de la ruine la plus proche)."
})
public final class RuinConfig implements Validatable {
	public static final int CURRENT_VERSION = 1;
	public static final Set<String> ABILITIES = Set.of("summon", "ground_slam", "fire_ring", "blink", "volley", "life_drain", "enrage");
	public static final Set<String> STYLES = Set.of("", "fire", "soul", "frost", "sand", "arrow", "spectral", "fireball", "jade", "wind");
	public static final Set<String> BAR_COLORS = Set.of("PINK", "BLUE", "RED", "GREEN", "YELLOW", "PURPLE", "WHITE");
	public static final Set<String> SLOTS = Set.of("mainhand", "offhand", "head", "chest", "legs", "feet");
	/** Base item of the incantation scroll (a banner pattern: no crafting recipe, never bought by villagers). */
	public static final String DEFAULT_INCANTATION_ITEM = "minecraft:skull_banner_pattern";
	private static final String FALLBACK_ENTITY = "minecraft:zombie";

	@Comment("Version du fichier (geree automatiquement, ne pas modifier)")
	public int configVersion = 0;

	@Comment("Boss des ruines actifs (false = plus aucun boss ne se reveille ; les cartes restent possibles)")
	public boolean bossesEnabled = true;

	@Comment("Reglages communs des combats")
	public EncounterSettings encounter = new EncounterSettings();

	@Comment("Reveil des boss : parchemin d'incantation et rituel")
	public SummonSettings summon = new SummonSettings();

	@Comment({
			"Menace (niveaux I, II, III... propres a chaque ruine) : chaque victoire sur un gardien eleve la menace de sa ruine ;",
			"a son retour (respawnDays), le gardien est plus fort (vie, degats, armure, escorte, recharges, rage) et rapporte plus"
	})
	public EscalationSettings escalation = new EscalationSettings();

	@Comment({
			"Resurrection : un gardien abattu trop vite (moins de fastKillSeconds apres son apparition) refuse de mourir. Il",
			"revient d'entre les morts une seule fois par combat, enrage ; le vaincre sous cette forme donne un butin bonus"
	})
	public ResurrectionSettings resurrection = new ResurrectionSettings();

	@Comment("Recherches de l'Atelier du cartographe")
	public CartographySettings cartography = new CartographySettings();

	@Comment("Boss generique des ruines inconnues de ce fichier (ajoutees par un autre pack de donnees)")
	public GenericSettings generic = new GenericSettings();

	@Comment({
			"Ruines par id (le chemin du gabarit apres 'ruin/', ex. guildy:ruin/crypte_oubliee -> crypte_oubliee) :",
			"name, tier (1 a 5), activationRadius (mode proximity : blocs autour du point d'apparition du boss pour le reveiller,",
			"0 = toute la ruine), resetSeconds (sans joueur proche pendant ce temps, le boss disparait et reviendra a pleine vie),",
			"respawnDays (jours de jeu avant le retour d'un boss vaincu, 0 = jamais), leashRadius (au-dela, le boss est ramene a son",
			"arene), bossBarColor (PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE ; aussi la couleur du rituel d'incantation),",
			"mapDecoration (icone de la carte, vide = celle de 'cartography'), fastKillSeconds (resurrection : -1 = formule de",
			"'resurrection', 0 = ce gardien ne ressuscite jamais, sinon la fenetre en secondes).",
			"boss : entity, name (codes couleur &), health, damage, armor, armorToughness, speed (0 = vitesse vanilla),",
			"knockbackResistance (0 a 1), scale (taille, 1 = normale), followRange, equipment {emplacement: objet} (mainhand,",
			"offhand, head, chest, legs, feet), effects [{effect, amplifier}] (permanents).",
			"minions (escorte) : types {entite: poids}, base + perExtraPlayer par joueur present au-dela du premier, max ;",
			"maxAlive (plafond avec les invocations), healthMultiplier, damageMultiplier, equipment, effects.",
			"abilities [{type, cooldownSeconds, damage, radius, count, style, healthBelow (0 a 1 : seulement sous ce ratio de vie),",
			"amplifier}] : summon (invoque count sbires), ground_slam (onde de choc, style sand pour du sable), fire_ring (anneau :",
			"style fire, soul, frost ou jade = poison et souffle), blink (se teleporte derriere un joueur et frappe ; style wind =",
			"bourrasque), volley (salve : style arrow, spectral, fireball ou wind = lames de vent), life_drain (draine la vie des",
			"joueurs proches), enrage (une fois sous healthBelow : Force + Vitesse de niveau amplifier, recharges reduites de moitie).",
			"rewards : goldMin/goldMax (lingots), xp (XP de guilde partagee entre les guildes des participants), lootTable +",
			"lootRolls (table de butin vanilla tiree au cadavre), items [{item, min, max, chance}], uniqueSource / scrollSource",
			"(vide = ruin_boss dans l'Overworld, nether_ruin_boss dans le Nether ; sources de uniques.json5 et des parchemins)."
	})
	public Map<String, RuinDef> ruins = RuinDefaults.ruins();

	@Comment({
			"Ruines par defaut deja proposees. Une ruine ajoutee par une mise a jour du mod est ajoutee automatiquement a",
			"'ruins' une seule fois ; pour ne plus l'utiliser, retirez-la de 'ruins' (pas d'ici)."
	})
	public List<String> knownDefaults = new ArrayList<>();

	/** Generic guardians built on demand (cleared by {@link #rebuild}). */
	private transient Map<String, RuinDef> genericCache = new LinkedHashMap<>();

	// ================================================================== sections

	@Json5.Section
	public static final class EncounterSettings {
		@Comment("Marge (blocs) autour de la boite de la ruine dans laquelle un joueur compte comme 'dans la ruine'")
		public int activationMargin = 2;
		@Comment("Marge (blocs) autour de la ruine ou les joueurs entretiennent le combat et voient la barre du boss")
		public int presenceMargin = 24;
		@Comment("Combats simultanes au maximum sur le serveur")
		public int maxActive = 8;
		@Comment("Delai (secondes) avant la premiere capacite du boss apres son reveil")
		public int firstAbilitySeconds = 4;
		@Comment("Delai minimal (secondes) entre deux capacites du boss")
		public int abilityGlobalCooldownSeconds = 3;
		@Comment("Portee (blocs) des capacites : seuls les joueurs plus proches sont vises")
		public int abilityRange = 24;
		@Comment("Assombrir le ciel des joueurs qui voient la barre du boss")
		public boolean darkenSky = true;
		@Comment("Annonce a tout le serveur quand un boss de ruine est vaincu")
		public boolean announce = true;
		@Comment("XP de guilde minimale par guilde participante (la recompense 'xp' est partagee selon le nombre de participants)")
		public int minXpPerGuild = 10;
	}

	@Json5.Section
	public static final class SummonSettings {
		@Comment({
				"Reveil des boss : \"scroll\" (il faut lire un parchemin d'incantation pres du point d'apparition du boss ; entrer",
				"dans la ruine ne le reveille plus) ou \"proximity\" (ancien comportement : un joueur en survie qui approche le reveille)"
		})
		public String mode = SummonRules.MODE_SCROLL;
		@Comment("Objet vanilla de base du parchemin d'incantation (son apparence sans pack de ressources)")
		public String item = DEFAULT_INCANTATION_ITEM;
		@Comment("Taille maximale d'une pile de parchemins d'incantation (1 a 64)")
		public int maxStack = 16;
		@Comment("Duree (secondes) du clic droit maintenu avant le debut du rituel (0.25 a 10)")
		public double channelSeconds = 1.5;
		@Comment({
				"Distance maximale (blocs) au point d'apparition du boss pour lancer le rituel ; tous les joueurs a cette",
				"distance y participent (et sont draines)"
		})
		public int arenaRadius = 16;
		@Comment("Duree (secondes) du rituel avant l'apparition du boss (2 a 30)")
		public double ritualSeconds = 7.0;
		@Comment("Points de vie draines a chaque participant pendant le rituel (2 = un coeur, 0 = aucun)")
		public double drainHealth = 4.0;
		@Comment("Le drain ne fait jamais descendre un participant sous ces points de vie (1 au minimum : le rituel ne tue jamais)")
		public double minHealth = 4.0;
		@Comment("Ralentir les participants pendant le drain")
		public boolean slowParticipants = true;
		@Comment("Particules envoyees au maximum par tick pour tous les rituels (au-dela, l'animation est allegee)")
		public int particleBudget = 900;
		@Comment("Distance (blocs) a laquelle les joueurs voient le rituel (particules longue distance)")
		public int viewDistance = 128;
		@Comment("Palier minimal du Carrefour commercial dont le marchand propose parfois un parchemin d'incantation (0 = jamais)")
		public int tradeTier = 2;
		@Comment("Chance (0 a 1) que le marchand du Carrefour propose un parchemin d'incantation")
		public double tradeChance = 0.5;
		@Comment("Prix (lingots d'or) d'un parchemin d'incantation chez le marchand du Carrefour")
		public int tradePrice = 24;
	}

	@Json5.Section
	public static final class EscalationSettings {
		@Comment("Menace active (false = tous les gardiens restent au niveau I ; les niveaux deja atteints sont conserves)")
		public boolean enabled = true;
		@Comment("Niveau de menace maximal d'une ruine (1 a 50)")
		public int maxLevel = 10;
		@Comment("Vie du gardien en plus par niveau au-dela de I (0.2 = +20 %)")
		public double healthPerLevel = 0.20;
		@Comment("Degats du gardien (attaque et capacites) en plus par niveau au-dela de I (0.1 = +10 %)")
		public double damagePerLevel = 0.10;
		@Comment("Points d'armure du gardien en plus par niveau au-dela de I (armure vanilla plafonnee a 30)")
		public double armorPerLevel = 1.0;
		@Comment("Un sbire de plus (escorte et plafond des invocations) tous les N niveaux (0 = jamais)")
		public int minionsEveryLevels = 2;
		@Comment("Recharges des capacites reduites par niveau au-dela de I (0.05 = -5 %)")
		public double cooldownPerLevel = 0.05;
		@Comment("Facteur minimal des recharges dues a la menace (0.6 = jamais plus de -40 %)")
		public double minCooldownFactor = 0.6;
		@Comment("Seuil de rage (ratio de vie) releve par niveau au-dela de I : la rage arrive plus tot")
		public double enragePerLevel = 0.02;
		@Comment("Seuil de rage maximal atteint par la menace (0 a 1)")
		public double maxEnrageThreshold = 0.5;
		@Comment("Or du butin en plus par niveau au-dela de I (0.15 = +15 %)")
		public double goldPerLevel = 0.15;
		@Comment("XP de guilde en plus par niveau au-dela de I (0.15 = +15 %)")
		public double xpPerLevel = 0.15;
		@Comment("Chance d'un tirage d'arme unique supplementaire par niveau au-dela de I (0.05 = 5 % par niveau, 100 % au plus)")
		public double uniqueChancePerLevel = 0.05;
		@Comment("Chance d'un tirage de parchemin de sort supplementaire par niveau au-dela de I")
		public double scrollChancePerLevel = 0.08;
	}

	@Json5.Section
	public static final class ResurrectionSettings {
		@Comment("Resurrection des gardiens abattus trop vite")
		public boolean enabled = true;
		@Comment({
				"Fenetre (secondes apres l'apparition du gardien) pendant laquelle sa mort declenche la resurrection :",
				"fastKillSeconds + fastKillPerTier par palier au-dela de 1 + fastKillPerThreat par niveau de menace au-dela de I",
				"(une ruine peut fixer la sienne : 'fastKillSeconds' de sa definition)"
		})
		public double fastKillSeconds = 45;
		public double fastKillPerTier = 15;
		public double fastKillPerThreat = 5;
		@Comment("Duree (secondes) de l'animation de resurrection, pendant laquelle le gardien est invulnerable et immobile (2 a 15)")
		public double durationSeconds = 5.0;
		@Comment("Part de la vie maximale rendue au gardien ressuscite (0.1 a 1)")
		public double healthRatio = 1.0;
		@Comment("Taille du gardien ressuscite (multiplicateur, 1 a 2 ; limitee par la hauteur libre au-dessus de lui)")
		public double scale = 1.25;
		@Comment("Vitesse de deplacement en plus du gardien ressuscite (0.25 = +25 %)")
		public double speedBonus = 0.25;
		@Comment("Degats en plus du gardien ressuscite (attaque et capacites, 0.4 = +40 %)")
		public double damageBonus = 0.4;
		@Comment("Facteur des recharges de ses capacites une fois ressuscite (0.7 = -30 %, en plus de la rage)")
		public double cooldownFactor = 0.7;
		@Comment("Sbires invoques a son retour (dans la limite de l'escorte vivante)")
		public int minions = 4;
		@Comment("Rayon (blocs) de l'onde de choc de son retour : repousse les joueurs, sans degats (0 = aucune)")
		public double shockwaveRadius = 8;
		@Comment("Force de la poussee de l'onde de choc (0 a 4)")
		public double shockwaveStrength = 1.2;
		@Comment("Particules envoyees au maximum par tick pour l'animation d'une resurrection (100 a 5000)")
		public int particleBudget = 700;
		@Comment("Tirages en plus dans la table de butin quand le gardien est vaincu sous sa forme ressuscitee")
		public int bonusLootRolls = 2;
		@Comment("Or du butin en plus sous la forme ressuscitee (0.5 = +50 %)")
		public double bonusGold = 0.5;
		@Comment("XP de guilde en plus sous la forme ressuscitee (0.5 = +50 %)")
		public double bonusXp = 0.5;
		@Comment("Tirages d'arme unique en plus sous la forme ressuscitee")
		public int bonusUniqueRolls = 1;
		@Comment("Tirages de parchemin de sort en plus sous la forme ressuscitee")
		public int bonusScrollRolls = 1;
	}

	@Json5.Section
	public static final class CartographySettings {
		@Comment("Minutes de travail d'un cartographe pour une recherche (plusieurs cartographes vont plus vite, pause la nuit)")
		public double researchMinutes = 8.0;
		@Comment("Cartographes qui comptent au maximum en meme temps (chacun ajoute une seconde de travail par seconde)")
		public int maxCartographers = 3;
		@Comment("Cout d'une recherche dans l'Overworld : objets pris dans l'inventaire du joueur {objet: nombre} (refuse en creatif)")
		public Map<String, Integer> overworldCost = cost("minecraft:paper", 8, "minecraft:compass", 1);
		@Comment("Cout d'une recherche dans le Nether")
		public Map<String, Integer> netherCost = cost("minecraft:paper", 8, "minecraft:compass", 1, "minecraft:quartz", 4);
		@Comment("Or du tresor de guilde ajoute au cout (0 = aucun ; permission Tresor requise sinon)")
		public int goldCost = 0;
		@Comment("Rayon de recherche (en cases de la grille de placement des ruines, comme les cartes d'explorateur vanilla)")
		public int searchRadius = 64;
		@Comment("Zoom des cartes (0 a 4, 2 = comme les cartes d'explorateur)")
		public int mapZoom = 2;
		@Comment("Icone de la cible sur les cartes (type de decoration vanilla, ex. minecraft:red_x, minecraft:target_x)")
		public String mapDecoration = "minecraft:red_x";
		@Comment("Parchemins d'incantation donnes avec chaque carte / boussole d'une recherche reussie (0 = aucun)")
		public int incantationsPerResearch = 1;
	}

	@Json5.Section
	public static final class GenericSettings {
		@Comment("Palier du boss generique d'une ruine inconnue de l'Overworld (1 a 5)")
		public int overworldTier = 2;
		@Comment("Palier du boss generique d'une ruine inconnue du Nether (1 a 5)")
		public int netherTier = 4;
	}

	/** One ruin: its guardian, escort, abilities, timings and rewards. */
	public static final class RuinDef {
		public String name = "";
		public int tier = 1;
		public int activationRadius = 20;
		public int resetSeconds = 60;
		public int respawnDays = 7;
		public int leashRadius = 18;
		public String bossBarColor = "RED";
		public String mapDecoration = "";
		/** Fast-kill window of this ruin (seconds): -1 = the formula of {@code resurrection}, 0 = never rises. */
		public double fastKillSeconds = -1;
		public BossDef boss = new BossDef();
		public MinionDef minions = new MinionDef();
		public List<AbilityDef> abilities = new ArrayList<>();
		public RewardDef rewards = new RewardDef();

		public RuinDef() {
		}

		/** Deep copy (forced definitions of admin previews and GameTests). */
		public RuinDef copy() {
			com.google.gson.Gson gson = new com.google.gson.Gson();
			return gson.fromJson(gson.toJson(this), RuinDef.class);
		}
	}

	public static final class BossDef {
		public String entity = FALLBACK_ENTITY;
		public String name = "";
		public double health = 200;
		public double damage = 8;
		public double armor = 6;
		public double armorToughness = 2;
		public double speed = 0;
		public double knockbackResistance = 0.6;
		public double scale = 1.5;
		public double followRange = 32;
		public Map<String, String> equipment = new LinkedHashMap<>();
		public List<EffectDef> effects = new ArrayList<>();

		public BossDef() {
		}
	}

	public static final class MinionDef {
		public Map<String, Integer> types = new LinkedHashMap<>();
		public int base = 3;
		public int perExtraPlayer = 2;
		public int max = 8;
		public int maxAlive = 12;
		public double healthMultiplier = 1.5;
		public double damageMultiplier = 1.2;
		public Map<String, String> equipment = new LinkedHashMap<>();
		public List<EffectDef> effects = new ArrayList<>();

		public MinionDef() {
		}
	}

	public static final class EffectDef {
		public String effect = "";
		public int amplifier = 0;

		public EffectDef() {
		}

		public EffectDef(String effect, int amplifier) {
			this.effect = effect;
			this.amplifier = amplifier;
		}
	}

	public static final class AbilityDef {
		public String type = "";
		public double cooldownSeconds = 12;
		public double damage = 6;
		public double radius = 6;
		public int count = 3;
		public String style = "";
		public double healthBelow = 1.0;
		public int amplifier = 1;

		public AbilityDef() {
		}

		/** Same ability with its damage multiplied (threat level, risen guardian); itself when the factor is 1. */
		public AbilityDef scaled(double damageFactor) {
			if (Math.abs(damageFactor - 1.0) < 1.0E-6) {
				return this;
			}
			AbilityDef copy = new AbilityDef();
			copy.type = type;
			copy.cooldownSeconds = cooldownSeconds;
			copy.damage = damage * Math.max(0, damageFactor);
			copy.radius = radius;
			copy.count = count;
			copy.style = style;
			copy.healthBelow = healthBelow;
			copy.amplifier = amplifier;
			return copy;
		}
	}

	public static final class RewardDef {
		public int goldMin = 4;
		public int goldMax = 8;
		public int xp = 80;
		public String lootTable = "";
		public int lootRolls = 1;
		public List<ItemDrop> items = new ArrayList<>();
		public String uniqueSource = "";
		public String scrollSource = "";

		public RewardDef() {
		}
	}

	public static final class ItemDrop {
		public String item = "";
		public int min = 1;
		public int max = 1;
		public double chance = 1.0;

		public ItemDrop() {
		}

		public ItemDrop(String item, int min, int max, double chance) {
			this.item = item;
			this.min = min;
			this.max = max;
			this.chance = chance;
		}
	}

	// ================================================================== queries

	/** Definition of a ruin id, or the generic tier-based guardian for an unknown id. */
	public RuinDef def(@Nullable String ruinId, boolean nether) {
		RuinDef def = ruinId == null || ruins == null ? null : ruins.get(ruinId);
		if (def != null) {
			return def;
		}
		int tier = nether ? generic.netherTier : generic.overworldTier;
		return genericCache.computeIfAbsent((nether ? "nether:" : "overworld:") + tier, k -> RuinDefaults.generic(tier, nether));
	}

	/** True when the id has its own definition in the file. */
	public boolean isKnown(@Nullable String ruinId) {
		return ruinId != null && ruins != null && ruins.containsKey(ruinId);
	}

	private static Map<String, Integer> cost(Object... itemCount) {
		Map<String, Integer> map = new LinkedHashMap<>();
		for (int i = 0; i + 1 < itemCount.length; i += 2) {
			map.put((String) itemCount[i], (Integer) itemCount[i + 1]);
		}
		return map;
	}

	// ================================================================== validation

	@Override
	public void validate() {
		for (String warning : rebuild(RuinRegistries::entityExists, RuinRegistries::itemExists, RuinRegistries::effectExists)) {
			Guildy.LOGGER.warn("[ruins.json5] {}", warning);
		}
	}

	/** Clamps every value and fixes broken entries (pure). @return the problems found */
	public List<String> rebuild(Predicate<String> entityExists, Predicate<String> itemExists, Predicate<String> effectExists) {
		List<String> warnings = new ArrayList<>();
		configVersion = CURRENT_VERSION;
		genericCache = new LinkedHashMap<>();
		if (encounter == null) {
			encounter = new EncounterSettings();
		}
		EncounterSettings e = encounter;
		e.activationMargin = clamp(e.activationMargin, 0, 64);
		e.presenceMargin = clamp(e.presenceMargin, 4, 128);
		e.maxActive = clamp(e.maxActive, 1, 64);
		e.firstAbilitySeconds = clamp(e.firstAbilitySeconds, 0, 120);
		e.abilityGlobalCooldownSeconds = clamp(e.abilityGlobalCooldownSeconds, 1, 120);
		e.abilityRange = clamp(e.abilityRange, 4, 64);
		e.minXpPerGuild = clamp(e.minXpPerGuild, 0, 100_000);
		if (summon == null) {
			summon = new SummonSettings();
		}
		SummonSettings s = summon;
		String mode = SummonRules.mode(s.mode);
		if (mode == null) {
			warnings.add("summon.mode: unknown mode '" + s.mode + "' (" + SummonRules.MODE_SCROLL + ", " + SummonRules.MODE_PROXIMITY + "), "
					+ SummonRules.MODE_SCROLL + " used");
			mode = SummonRules.MODE_SCROLL;
		}
		s.mode = mode;
		s.item = normalize(s.item);
		if (!itemExists.test(s.item)) {
			warnings.add("summon.item: unknown item '" + s.item + "', " + DEFAULT_INCANTATION_ITEM + " used");
			s.item = DEFAULT_INCANTATION_ITEM;
		}
		s.maxStack = clamp(s.maxStack, 1, 64);
		s.channelSeconds = clamp(s.channelSeconds, 0.25, 10);
		s.arenaRadius = clamp(s.arenaRadius, 4, 64);
		s.ritualSeconds = clamp(s.ritualSeconds, 2, 30);
		s.drainHealth = clamp(s.drainHealth, 0, 40);
		s.minHealth = clamp(s.minHealth, 1, 40);
		s.particleBudget = clamp(s.particleBudget, 100, 10_000);
		s.viewDistance = clamp(s.viewDistance, 16, 256);
		s.tradeTier = clamp(s.tradeTier, 0, 3);
		s.tradeChance = clamp(s.tradeChance, 0, 1);
		s.tradePrice = clamp(s.tradePrice, 1, 2304);
		if (escalation == null) {
			escalation = new EscalationSettings();
		}
		EscalationSettings t = escalation;
		t.maxLevel = clamp(t.maxLevel, 1, ThreatRules.MAX_LEVEL);
		t.healthPerLevel = clamp(t.healthPerLevel, 0, 5);
		t.damagePerLevel = clamp(t.damagePerLevel, 0, 5);
		t.armorPerLevel = clamp(t.armorPerLevel, 0, 10);
		t.minionsEveryLevels = clamp(t.minionsEveryLevels, 0, ThreatRules.MAX_LEVEL);
		t.cooldownPerLevel = clamp(t.cooldownPerLevel, 0, 0.5);
		t.minCooldownFactor = clamp(t.minCooldownFactor, 0.1, 1);
		t.enragePerLevel = clamp(t.enragePerLevel, 0, 0.2);
		t.maxEnrageThreshold = clamp(t.maxEnrageThreshold, 0, 1);
		t.goldPerLevel = clamp(t.goldPerLevel, 0, 5);
		t.xpPerLevel = clamp(t.xpPerLevel, 0, 5);
		t.uniqueChancePerLevel = clamp(t.uniqueChancePerLevel, 0, 1);
		t.scrollChancePerLevel = clamp(t.scrollChancePerLevel, 0, 1);
		if (resurrection == null) {
			resurrection = new ResurrectionSettings();
		}
		ResurrectionSettings r = resurrection;
		r.fastKillSeconds = clamp(r.fastKillSeconds, 0, 3600);
		r.fastKillPerTier = clamp(r.fastKillPerTier, 0, 600);
		r.fastKillPerThreat = clamp(r.fastKillPerThreat, 0, 600);
		r.durationSeconds = clamp(r.durationSeconds, 2, 15);
		r.healthRatio = clamp(r.healthRatio, 0.1, 1);
		r.scale = clamp(r.scale, 1, 2);
		r.speedBonus = clamp(r.speedBonus, 0, 2);
		r.damageBonus = clamp(r.damageBonus, 0, 5);
		r.cooldownFactor = clamp(r.cooldownFactor, 0.1, 1);
		r.minions = clamp(r.minions, 0, 16);
		r.shockwaveRadius = clamp(r.shockwaveRadius, 0, 32);
		r.shockwaveStrength = clamp(r.shockwaveStrength, 0, 4);
		r.particleBudget = clamp(r.particleBudget, 100, 5000);
		r.bonusLootRolls = clamp(r.bonusLootRolls, 0, 10);
		r.bonusGold = clamp(r.bonusGold, 0, 5);
		r.bonusXp = clamp(r.bonusXp, 0, 5);
		r.bonusUniqueRolls = clamp(r.bonusUniqueRolls, 0, 5);
		r.bonusScrollRolls = clamp(r.bonusScrollRolls, 0, 5);
		if (cartography == null) {
			cartography = new CartographySettings();
		}
		CartographySettings c = cartography;
		c.researchMinutes = clamp(c.researchMinutes, 0.05, 240);
		c.maxCartographers = clamp(c.maxCartographers, 1, 16);
		c.overworldCost = fixCost(c.overworldCost, itemExists, "cartography.overworldCost", warnings);
		c.netherCost = fixCost(c.netherCost, itemExists, "cartography.netherCost", warnings);
		c.goldCost = clamp(c.goldCost, 0, 100_000);
		c.searchRadius = clamp(c.searchRadius, 4, 200);
		c.mapZoom = clamp(c.mapZoom, 0, 4);
		c.mapDecoration = c.mapDecoration == null || c.mapDecoration.isBlank() ? "minecraft:red_x" : c.mapDecoration.trim().toLowerCase(Locale.ROOT);
		c.incantationsPerResearch = clamp(c.incantationsPerResearch, 0, 16);
		if (generic == null) {
			generic = new GenericSettings();
		}
		generic.overworldTier = clamp(generic.overworldTier, 1, 5);
		generic.netherTier = clamp(generic.netherTier, 1, 5);
		if (ruins == null) {
			ruins = RuinDefaults.ruins();
		}
		if (knownDefaults == null) {
			knownDefaults = new ArrayList<>();
		}
		mergeNewDefaults(ruins, knownDefaults);
		Map<String, RuinDef> fixed = new LinkedHashMap<>();
		for (Map.Entry<String, RuinDef> entry : ruins.entrySet()) {
			String id = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT);
			if (id.isEmpty() || entry.getValue() == null) {
				continue;
			}
			fixDef(id, entry.getValue(), entityExists, itemExists, effectExists, warnings);
			fixed.put(id, entry.getValue());
		}
		ruins = fixed;
		return warnings;
	}

	/** Appends the default ruins not yet known (once), like the structures' knownDefaults. @return ids added */
	static List<String> mergeNewDefaults(Map<String, RuinDef> ruins, List<String> known) {
		List<String> added = new ArrayList<>();
		for (Map.Entry<String, RuinDef> entry : RuinDefaults.ruins().entrySet()) {
			if (known.contains(entry.getKey())) {
				continue;
			}
			known.add(entry.getKey());
			if (!ruins.containsKey(entry.getKey())) {
				ruins.put(entry.getKey(), entry.getValue());
				added.add(entry.getKey());
			}
		}
		return added;
	}

	static void fixDef(String id, RuinDef def, Predicate<String> entityExists, Predicate<String> itemExists, Predicate<String> effectExists,
			List<String> warnings) {
		String where = "ruins." + id;
		if (def.name == null || def.name.isBlank()) {
			def.name = id;
		}
		def.tier = clamp(def.tier, 1, 5);
		def.activationRadius = clamp(def.activationRadius, 0, 128);
		def.resetSeconds = clamp(def.resetSeconds, 2, 3600);
		def.respawnDays = clamp(def.respawnDays, 0, 365);
		def.leashRadius = clamp(def.leashRadius, 4, 128);
		def.bossBarColor = def.bossBarColor == null ? "RED" : def.bossBarColor.trim().toUpperCase(Locale.ROOT);
		if (!BAR_COLORS.contains(def.bossBarColor)) {
			warnings.add(where + ": bossBarColor '" + def.bossBarColor + "' unknown, RED used");
			def.bossBarColor = "RED";
		}
		def.mapDecoration = def.mapDecoration == null ? "" : def.mapDecoration.trim().toLowerCase(Locale.ROOT);
		def.fastKillSeconds = Double.isNaN(def.fastKillSeconds) || def.fastKillSeconds < 0 ? -1 : Math.min(3600, def.fastKillSeconds);

		if (def.boss == null) {
			def.boss = new BossDef();
		}
		BossDef b = def.boss;
		b.entity = normalize(b.entity);
		if (!entityExists.test(b.entity)) {
			warnings.add(where + ".boss: unknown entity '" + b.entity + "', " + FALLBACK_ENTITY + " used");
			b.entity = FALLBACK_ENTITY;
		}
		if (b.name == null || b.name.isBlank()) {
			b.name = def.name;
		}
		b.health = clamp(b.health, 1, 10_000);
		b.damage = clamp(b.damage, 0, 1000);
		b.armor = clamp(b.armor, 0, 30);
		b.armorToughness = clamp(b.armorToughness, 0, 20);
		b.speed = clamp(b.speed, 0, 2);
		b.knockbackResistance = clamp(b.knockbackResistance, 0, 1);
		b.scale = clamp(b.scale, 0.5, 4);
		b.followRange = clamp(b.followRange, 8, 64);
		b.equipment = fixEquipment(b.equipment, itemExists, where + ".boss", warnings);
		b.effects = fixEffects(b.effects, effectExists, where + ".boss", warnings);

		if (def.minions == null) {
			def.minions = new MinionDef();
		}
		MinionDef m = def.minions;
		Map<String, Integer> types = new LinkedHashMap<>();
		if (m.types != null) {
			for (Map.Entry<String, Integer> type : m.types.entrySet()) {
				String entity = normalize(type.getKey());
				int weight = type.getValue() == null ? 0 : type.getValue();
				if (!entityExists.test(entity)) {
					warnings.add(where + ".minions: unknown entity '" + type.getKey() + "': ignored");
				} else if (weight > 0) {
					types.put(entity, Math.min(weight, 1000));
				}
			}
		}
		m.types = types;
		m.base = clamp(m.base, 0, 32);
		m.perExtraPlayer = clamp(m.perExtraPlayer, 0, 16);
		m.max = clamp(m.max, 0, 32);
		m.maxAlive = clamp(m.maxAlive, 0, 48);
		m.healthMultiplier = clamp(m.healthMultiplier, 0.1, 20);
		m.damageMultiplier = clamp(m.damageMultiplier, 0.1, 20);
		m.equipment = fixEquipment(m.equipment, itemExists, where + ".minions", warnings);
		m.effects = fixEffects(m.effects, effectExists, where + ".minions", warnings);

		List<AbilityDef> abilities = new ArrayList<>();
		if (def.abilities != null) {
			for (AbilityDef a : def.abilities) {
				if (a == null) {
					continue;
				}
				a.type = a.type == null ? "" : a.type.trim().toLowerCase(Locale.ROOT);
				if (!ABILITIES.contains(a.type)) {
					warnings.add(where + ".abilities: unknown type '" + a.type + "' (" + String.join(", ", ABILITIES) + "): ignored");
					continue;
				}
				a.style = a.style == null ? "" : a.style.trim().toLowerCase(Locale.ROOT);
				if (!STYLES.contains(a.style)) {
					warnings.add(where + ".abilities." + a.type + ": unknown style '" + a.style + "': default used");
					a.style = "";
				}
				a.cooldownSeconds = clamp(a.cooldownSeconds, 1, 600);
				a.damage = clamp(a.damage, 0, 200);
				a.radius = clamp(a.radius, 1, 32);
				a.count = clamp(a.count, 1, 16);
				a.healthBelow = clamp(a.healthBelow, 0, 1);
				a.amplifier = clamp(a.amplifier, 0, 9);
				abilities.add(a);
			}
		}
		def.abilities = abilities;

		if (def.rewards == null) {
			def.rewards = new RewardDef();
		}
		RewardDef r = def.rewards;
		r.goldMin = clamp(r.goldMin, 0, 576);
		r.goldMax = clamp(r.goldMax, r.goldMin, 576);
		r.xp = clamp(r.xp, 0, 100_000);
		r.lootTable = r.lootTable == null ? "" : r.lootTable.trim().toLowerCase(Locale.ROOT);
		r.lootRolls = clamp(r.lootRolls, 0, 10);
		r.uniqueSource = r.uniqueSource == null ? "" : r.uniqueSource.trim().toLowerCase(Locale.ROOT);
		r.scrollSource = r.scrollSource == null ? "" : r.scrollSource.trim().toLowerCase(Locale.ROOT);
		List<ItemDrop> items = new ArrayList<>();
		if (r.items != null) {
			for (ItemDrop drop : r.items) {
				if (drop == null) {
					continue;
				}
				drop.item = normalize(drop.item);
				if (!itemExists.test(drop.item)) {
					warnings.add(where + ".rewards: unknown item '" + drop.item + "': ignored");
					continue;
				}
				drop.min = clamp(drop.min, 1, 64);
				drop.max = clamp(drop.max, drop.min, 64);
				drop.chance = clamp(drop.chance, 0, 1);
				items.add(drop);
			}
		}
		r.items = items;
	}

	private static Map<String, Integer> fixCost(@Nullable Map<String, Integer> cost, Predicate<String> itemExists, String where, List<String> warnings) {
		Map<String, Integer> fixed = new LinkedHashMap<>();
		if (cost == null) {
			return fixed;
		}
		for (Map.Entry<String, Integer> entry : cost.entrySet()) {
			String item = normalize(entry.getKey());
			int count = entry.getValue() == null ? 0 : entry.getValue();
			if (!itemExists.test(item)) {
				warnings.add(where + ": unknown item '" + entry.getKey() + "': ignored");
			} else if (count > 0) {
				fixed.merge(item, Math.min(count, 64 * 36), Integer::sum);
			}
		}
		return fixed;
	}

	private static Map<String, String> fixEquipment(@Nullable Map<String, String> equipment, Predicate<String> itemExists, String where,
			List<String> warnings) {
		Map<String, String> fixed = new LinkedHashMap<>();
		if (equipment == null) {
			return fixed;
		}
		for (Map.Entry<String, String> entry : equipment.entrySet()) {
			String slot = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT);
			String item = normalize(entry.getValue());
			if (!SLOTS.contains(slot)) {
				warnings.add(where + ".equipment: unknown slot '" + entry.getKey() + "' (" + String.join(", ", SLOTS) + "): ignored");
			} else if (!itemExists.test(item)) {
				warnings.add(where + ".equipment: unknown item '" + entry.getValue() + "': ignored");
			} else {
				fixed.put(slot, item);
			}
		}
		return fixed;
	}

	private static List<EffectDef> fixEffects(@Nullable List<EffectDef> effects, Predicate<String> effectExists, String where, List<String> warnings) {
		List<EffectDef> fixed = new ArrayList<>();
		if (effects == null) {
			return fixed;
		}
		Set<String> seen = new HashSet<>();
		for (EffectDef effect : effects) {
			if (effect == null) {
				continue;
			}
			effect.effect = normalize(effect.effect);
			if (!effectExists.test(effect.effect)) {
				warnings.add(where + ".effects: unknown effect '" + effect.effect + "': ignored");
				continue;
			}
			if (seen.add(effect.effect)) {
				effect.amplifier = clamp(effect.amplifier, 0, 9);
				fixed.add(effect);
			}
		}
		return fixed;
	}

	/** "zombie" → "minecraft:zombie", trimmed and lower case ("" stays ""). */
	static String normalize(@Nullable String id) {
		if (id == null) {
			return "";
		}
		String value = id.trim().toLowerCase(Locale.ROOT);
		return value.isEmpty() || value.contains(":") ? value : "minecraft:" + value;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp(double value, double min, double max) {
		return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
	}
}
