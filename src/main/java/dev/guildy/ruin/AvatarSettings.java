package dev.guildy.ruin;

import dev.guildy.config.Comment;
import dev.guildy.config.Json5;
import dev.guildy.ruin.RuinConfig.DreadAbilityDef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Section {@code cursed.avatar} of ruins.json5: the Avatar, the super form of a cursed guardian summoned with a Nether star
 * lying in the ritual circle (see {@link AvatarRules}). It fights at least as a dread guardian of {@code minThreat}, with
 * its own health and damage on top of the curse, its theme's dread abilities amplified, Nether and soul attacks unlocked
 * phase after phase, a red sky and huge rewards. Free of Minecraft classes except the predicates given to {@link #fix}.
 */
@Json5.Section
public final class AvatarSettings {
	public static final String DEFAULT_ITEM = "minecraft:nether_star";

	@Comment("Avatar actif (false = une etoile du Nether dans le cercle ne change rien au rituel maudit)")
	public boolean enabled = true;
	@Comment("Objet a poser au sol dans le cercle du rituel maudit pour appeler l'Avatar (consomme a l'apparition)")
	public String item = DEFAULT_ITEM;
	@Comment("L'Avatar se bat au moins comme un gardien de ce niveau de menace (statistiques, forme redoutable), 1 a 50")
	public int minThreat = 10;

	@Comment({
			"Vie : multiplicateur en plus de la malediction (et de la menace, du groupe, de la forme redoutable), puis bornee par",
			"joueur compte a l'apparition : au moins healthMinPerPlayer, au plus healthCapPerPlayer (vie effective)"
	})
	public double healthMultiplier = 2.0;
	public double healthMinPerPlayer = 10000;
	public double healthCapPerPlayer = 16000;
	@Comment("Degats aux joueurs (apres armure) multiplies, en plus de ceux de la forme redoutable (et de la malediction)")
	public double damageMultiplier = 1.2;
	@Comment("Ce bonus ne fait jamais depasser a un coup cette part de la vie maximale du joueur (0 = sans limite)")
	public double maxHitHealthRatio = 0.9;
	@Comment("Vie des sbires de l'Avatar (escorte et creatures des failles du Nether), multiplicateur")
	public double escortHealthMultiplier = 2.0;
	@Comment("Ses capacites redoutables (celles de sa ruine) : degats multiplies et une repetition de plus (cercles, lignes...)")
	public double themeDamageMultiplier = 1.25;
	public int themeCountBonus = 1;
	@Comment("Attaques de l'Avatar : delai minimal entre deux, nombre au plus en meme temps, recharges de sa forme finale (facteur)")
	public double globalCooldownSeconds = 3.0;
	public int maxRunning = 3;
	public double finalFormCooldownFactor = 0.75;

	@Comment({
			"Attaques du Nether et des ames (type, phase a partir de laquelle il les utilise, recharge, degats, rayon, nombre,",
			"duree) : soul_pillars (piliers de feu des ames), wither_skulls (tetes de wither a tete chercheuse), nether_rifts",
			"(failles qui liberent blazes et wither squelettes), ghast_rain (pluie de boules de feu), soul_chains (chaines d'ames :",
			"rester pres de lui), collapsing_ring (anneau de feu des ames qui se referme : se placer dans une breche)"
	})
	public List<DreadAbilityDef> attacks = defaultAttacks();
	@Comment("Creatures des failles du Nether {entite: poids} et nombre de ces creatures vivantes au plus")
	public Map<String, Integer> riftMobs = defaultRiftMobs();
	public int riftMaxAlive = 6;

	@Comment({
			"Mise en scene : ciel rouge sombre (teinte rouge de l'ecran des joueurs de l'arene, comme pres de la bordure du monde),",
			"musique de boss, annonce a tout le serveur a son apparition et a sa chute"
	})
	public boolean redSky = true;
	public boolean bossMusic = true;
	public boolean announce = true;

	@Comment("Recompenses de sa chute (en plus de celles d'un gardien maudit)")
	public RewardSettings rewards = new RewardSettings();

	@Comment("Particules envoyees au maximum par tick pour un combat contre l'Avatar (aura, attaques)")
	public int particleBudget = 1400;

	@Json5.Section
	public static final class RewardSettings {
		@Comment("Or et XP de guilde multiplies (en plus de la malediction, de la menace et de la forme redoutable)")
		public double goldMultiplier = 1.5;
		public double xpMultiplier = 2.0;
		@Comment("Tirages en plus dans la table de butin")
		public int extraLootRolls = 2;
		@Comment("Tentatives d'arme unique en plus et nombre maximal d'armes uniques (remplace celui de la malediction)")
		public int extraUniqueRolls = 3;
		public int maxUniques = 3;
		@Comment("Tirages de parchemin de sort en plus (un parchemin reste garanti)")
		public int extraScrollRolls = 2;
		@Comment("Chance (0 a 1) d'un grimoire vierge en plus")
		public double grimoireChance = 1.0;
		@Comment("Trophee \"Coeur de l'Avatar\" (un objet de collection a l'image de l'etoile, sans autre usage) donne au vainqueur")
		public boolean trophy = true;
	}

	static List<DreadAbilityDef> defaultAttacks() {
		return new ArrayList<>(List.of(
				RuinDefaults.avatar("soul_pillars", 1, 14, 6, 2.2, 4, 3),
				RuinDefaults.avatar("wither_skulls", 1, 12, 8, 1.6, 8, 0),
				RuinDefaults.avatar("nether_rifts", 2, 30, 0, 2.5, 2, 0),
				RuinDefaults.avatar("ghast_rain", 2, 22, 12, 3, 12, 5),
				RuinDefaults.avatar("soul_chains", 3, 20, 8, 7, 3, 6),
				RuinDefaults.avatar("collapsing_ring", 3, 24, 16, 18, 3, 0)));
	}

	static Map<String, Integer> defaultRiftMobs() {
		Map<String, Integer> map = new LinkedHashMap<>();
		map.put("minecraft:blaze", 2);
		map.put("minecraft:wither_skeleton", 2);
		return map;
	}

	/** Clamps every value (pure); unknown items / entities fall back to the defaults, unknown attack types are dropped. */
	public void fix(Predicate<String> itemExists, Predicate<String> entityExists, List<String> warnings) {
		item = RuinConfig.normalize(item);
		if (!itemExists.test(item)) {
			warnings.add("cursed.avatar.item: unknown item '" + item + "', " + DEFAULT_ITEM + " used");
			item = DEFAULT_ITEM;
		}
		minThreat = Math.max(1, Math.min(ThreatRules.MAX_LEVEL, minThreat));
		healthMultiplier = DreadSettings.clamp(healthMultiplier, 1, 50);
		healthMinPerPlayer = DreadSettings.clamp(healthMinPerPlayer, 0, 1_000_000);
		healthCapPerPlayer = DreadSettings.clamp(healthCapPerPlayer, 0, 1_000_000);
		if (healthCapPerPlayer > 0 && healthCapPerPlayer < healthMinPerPlayer) {
			healthCapPerPlayer = healthMinPerPlayer;
		}
		damageMultiplier = DreadSettings.clamp(damageMultiplier, 1, 10);
		maxHitHealthRatio = DreadSettings.clamp(maxHitHealthRatio, 0, 5);
		escortHealthMultiplier = DreadSettings.clamp(escortHealthMultiplier, 1, 10);
		themeDamageMultiplier = DreadSettings.clamp(themeDamageMultiplier, 0.5, 5);
		themeCountBonus = Math.max(0, Math.min(8, themeCountBonus));
		globalCooldownSeconds = DreadSettings.clamp(globalCooldownSeconds, 0.5, 60);
		maxRunning = Math.max(1, Math.min(8, maxRunning));
		finalFormCooldownFactor = DreadSettings.clamp(finalFormCooldownFactor, 0.2, 1);
		attacks = RuinConfig.fixDread(attacks, "cursed.avatar.attacks", warnings);
		Map<String, Integer> mobs = new LinkedHashMap<>();
		if (riftMobs != null) {
			for (Map.Entry<String, Integer> entry : riftMobs.entrySet()) {
				String entity = RuinConfig.normalize(entry.getKey());
				int weight = entry.getValue() == null ? 0 : entry.getValue();
				if (!entityExists.test(entity)) {
					warnings.add("cursed.avatar.riftMobs: unknown entity '" + entry.getKey() + "': ignored");
				} else if (weight > 0) {
					mobs.put(entity.toLowerCase(Locale.ROOT), Math.min(weight, 1000));
				}
			}
		}
		riftMobs = mobs.isEmpty() ? defaultRiftMobs() : mobs;
		riftMaxAlive = Math.max(0, Math.min(24, riftMaxAlive));
		if (rewards == null) {
			rewards = new RewardSettings();
		}
		RewardSettings r = rewards;
		r.goldMultiplier = DreadSettings.clamp(r.goldMultiplier, 1, 20);
		r.xpMultiplier = DreadSettings.clamp(r.xpMultiplier, 1, 20);
		r.extraLootRolls = Math.max(0, Math.min(10, r.extraLootRolls));
		r.extraUniqueRolls = Math.max(0, Math.min(10, r.extraUniqueRolls));
		r.maxUniques = Math.max(1, Math.min(6, r.maxUniques));
		r.extraScrollRolls = Math.max(0, Math.min(10, r.extraScrollRolls));
		r.grimoireChance = DreadSettings.clamp(r.grimoireChance, 0, 1);
		particleBudget = Math.max(100, Math.min(10_000, particleBudget));
	}
}
