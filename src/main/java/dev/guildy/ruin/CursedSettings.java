package dev.guildy.ruin;

import dev.guildy.config.Comment;
import dev.guildy.config.Json5;

import java.util.List;
import java.util.function.Predicate;

/**
 * Section {@code cursed} of ruins.json5: the cursed incantation scroll, its ritual, the cursed guardian (multipliers on top
 * of the threat level), its three phases, its attacks, its rewards and where the scroll can be found. Free of Minecraft
 * classes (unit tests); {@link #fix} clamps every value.
 */
@Json5.Section
public final class CursedSettings {
	/** Base item of the cursed scroll (a banner pattern without crafting recipe, never bought by villagers). */
	public static final String DEFAULT_ITEM = "minecraft:piglin_banner_pattern";

	@Comment("Gardiens maudits actifs (false = le parchemin d'incantation maudit est refuse, il n'apparait plus a la vente)")
	public boolean enabled = true;

	@Comment("Le parchemin d'incantation maudit et son rituel")
	public RitualSettings ritual = new RitualSettings();

	@Comment({
			"Le gardien maudit : le gardien de la ruine, nomme \"Maudit\", barre violette. Ces multiplicateurs s'ajoutent a ceux",
			"de la menace (et de la taille du groupe) : vie = vie de la ruine x menace x healthMultiplier"
	})
	public GuardianSettings guardian = new GuardianSettings();

	@Comment("Les trois phases : sous phase2Below puis phase3Below (part de la vie), une transition ou il est invulnerable")
	public PhaseSettings phases = new PhaseSettings();

	@Comment({
			"Attaques maudites (en plus de ses capacites normales) : phase 2 = pentagrammes (drainent la vie des joueurs qui",
			"restent dedans et soignent le gardien), chaines d'ames (attirent un joueur), meteores noirs ; phase 3 = en plus",
			"ondes de tenebres (effet Obscurite), cages de foudre (foudre visuelle, degats si on reste dans le cercle), nova de",
			"sang (draine tous les joueurs proches), ombres (copies faibles et temporaires), aura maudite permanente.",
			"Chaque attaque est annoncee (cercle d'alerte) avant de frapper. Recharges en secondes, degats en points de vie"
	})
	public AttackSettings attacks = new AttackSettings();

	@Comment("Recompenses d'une victoire sur un gardien maudit (en plus des bonus de la menace)")
	public RewardSettings rewards = new RewardSettings();

	@Comment("Ou trouver le parchemin maudit (en plus de la commande admin)")
	public SourceSettings sources = new SourceSettings();

	// ================================================================== sections

	@Json5.Section
	public static final class RitualSettings {
		@Comment("Objet vanilla de base du parchemin maudit (son apparence sans pack de ressources)")
		public String item = DEFAULT_ITEM;
		@Comment("Taille maximale d'une pile de parchemins maudits (1 a 64)")
		public int maxStack = 4;
		@Comment("Duree (secondes) du clic droit maintenu avant le debut du rituel maudit (0.25 a 10)")
		public double channelSeconds = 2.5;
		@Comment("Duree (secondes) du rituel maudit avant l'apparition du gardien (2 a 30)")
		public double ritualSeconds = 10.0;
		@Comment("Points de vie draines a chaque participant pendant le rituel maudit (6 = trois coeurs, 0 = aucun)")
		public double drainHealth = 6.0;
		@Comment("Le drain ne fait jamais descendre un participant sous ces points de vie (1 au minimum)")
		public double minHealth = 4.0;
	}

	@Json5.Section
	public static final class GuardianSettings {
		@Comment("Vie du gardien maudit (multiplicateur, 1 a 20)")
		public double healthMultiplier = 3.0;
		@Comment("Degats du gardien maudit : attaque et capacites (multiplicateur, 1 a 10)")
		public double damageMultiplier = 2.0;
		@Comment("Points d'armure en plus (armure vanilla plafonnee a 30)")
		public double armorBonus = 8.0;
		@Comment("Robustesse d'armure en plus")
		public double toughnessBonus = 4.0;
		@Comment("Taille du gardien maudit (multiplicateur, 1 a 2 ; limitee par la hauteur libre de l'arene)")
		public double scale = 1.3;
		@Comment("Vitesse de deplacement en plus (0.15 = +15 %)")
		public double speedBonus = 0.15;
		@Comment("Resistance au recul minimale du gardien maudit (0 a 1)")
		public double knockbackResistance = 0.9;
		@Comment("Recharges de ses capacites normales (facteur, 0.6 = -40 % ; en plus de la menace et de la rage)")
		public double cooldownFactor = 0.6;
		@Comment("Escorte maudite : nombre de sbires multiplie par escortMultiplier, plus escortBonus")
		public double escortMultiplier = 1.5;
		public int escortBonus = 2;
		@Comment("Sbires vivants en plus au maximum (plafond de l'escorte)")
		public int extraAlive = 6;
		@Comment("Vie et degats des sbires maudits (multiplicateurs, en plus de ceux de la ruine)")
		public double minionHealthMultiplier = 1.5;
		public double minionDamageMultiplier = 1.3;
	}

	@Json5.Section
	public static final class PhaseSettings {
		@Comment("Part de la vie sous laquelle commence la phase 2 (0.66 = 66 %)")
		public double phase2Below = 0.66;
		@Comment("Part de la vie sous laquelle commence la phase 3 (inferieure a phase2Below)")
		public double phase3Below = 0.33;
		@Comment({
				"Duree (secondes) d'une transition de phase (2 a 10) : le gardien est invulnerable et s'eleve, un pentagramme geant",
				"s'embrase, des chaines d'ames lient les joueurs, une onde de choc les repousse (sans degats), des sbires surgissent"
		})
		public double transitionSeconds = 4.0;
		@Comment("Degats en plus a chaque phase au-dela de la premiere (0.25 = +25 % en phase 2, +50 % en phase 3)")
		public double damageBonus = 0.25;
		@Comment("Recharges multipliees a chaque phase au-dela de la premiere (0.85 = -15 % par phase)")
		public double cooldownFactor = 0.85;
		@Comment("Vitesse en plus a chaque phase au-dela de la premiere (0.1 = +10 %)")
		public double speedBonus = 0.1;
		@Comment("Sbires qui surgissent du sol a chaque transition (dans la limite de l'escorte vivante)")
		public int minions = 3;
		@Comment("Rayon (blocs) et force de l'onde de choc d'une transition (repousse les joueurs, sans degats)")
		public double shockwaveRadius = 9.0;
		public double shockwaveStrength = 1.4;
		@Comment("Duree (secondes) de l'effet Obscurite donne aux joueurs quand l'arene s'assombrit (0 = aucun)")
		public double darknessSeconds = 4.0;
	}

	@Json5.Section
	public static final class AttackSettings {
		@Comment("Delai minimal (secondes) entre deux attaques maudites")
		public double globalCooldownSeconds = 3.0;
		@Comment("Pentagrammes (phase 2) : recharge, nombre (+1 en phase 3), rayon, duree, degats par pulsation (toutes les 0,5 s)")
		public double pentagramCooldownSeconds = 15.0;
		public int pentagramCount = 3;
		public double pentagramRadius = 2.5;
		public double pentagramSeconds = 8.0;
		public double pentagramDamage = 1.5;
		@Comment("Le gardien se soigne de pentagramHeal fois la vie drainee par les pentagrammes")
		public double pentagramHeal = 2.0;
		@Comment("Chaines d'ames (phase 2) : recharge, joueurs vises, force de l'attraction (0 a 2), degats quand elles se brisent")
		public double chainCooldownSeconds = 12.0;
		public int chainTargets = 2;
		public double chainPull = 0.9;
		public double chainDamage = 4.0;
		@Comment("Meteores noirs (phase 2) : recharge, nombre, degats au centre, rayon de l'impact")
		public double meteorCooldownSeconds = 14.0;
		public int meteorCount = 6;
		public double meteorDamage = 8.0;
		public double meteorRadius = 2.5;
		@Comment("Ondes de tenebres (phase 3) : recharge, duree de l'Obscurite (secondes), degats")
		public double darknessCooldownSeconds = 18.0;
		public double darknessSeconds = 6.0;
		public double darknessDamage = 2.0;
		@Comment("Cages de foudre (phase 3) : recharge, joueurs vises, rayon, duree (secondes), degats par pulsation si on reste dedans")
		public double cageCooldownSeconds = 15.0;
		public int cageTargets = 2;
		public double cageRadius = 2.5;
		public double cageSeconds = 3.0;
		public double cageDamage = 3.0;
		@Comment("Nova de sang (phase 3) : recharge, rayon, degats, soin du gardien (fois la vie drainee)")
		public double novaCooldownSeconds = 22.0;
		public double novaRadius = 14.0;
		public double novaDamage = 6.0;
		public double novaHeal = 1.5;
		@Comment("Ombres (phase 3) : recharge, nombre, duree de vie (secondes), vie, part des degats du gardien")
		public double cloneCooldownSeconds = 25.0;
		public int cloneCount = 2;
		public double cloneSeconds = 20.0;
		public double cloneHealth = 30.0;
		public double cloneDamageRatio = 0.4;
		@Comment("Aura maudite (phase 3) : rayon (blocs) et duree (secondes) de l'effet Wither donne aux joueurs trop proches (0 = aucun)")
		public double auraRadius = 3.5;
		public double auraWitherSeconds = 2.0;
		@Comment("Particules envoyees au maximum par tick pour tous les rituels et combats maudits (au-dela, l'animation est allegee)")
		public int particleBudget = 1200;
	}

	@Json5.Section
	public static final class RewardSettings {
		@Comment("Or et XP de guilde multiplies (en plus de la menace)")
		public double goldMultiplier = 3.0;
		public double xpMultiplier = 3.0;
		@Comment("Tirages en plus dans la table de butin de la ruine")
		public int extraLootRolls = 4;
		@Comment({
				"Tentatives d'arme unique en plus (chacune avec la chance de la source de la ruine dans uniques.json5, regles",
				"d'unicite respectees) et nombre maximal d'armes uniques par victoire"
		})
		public int extraUniqueRolls = 5;
		public int maxUniques = 2;
		@Comment("Tirages de parchemin de sort en plus (chance de la source dans scrolls.json5)")
		public int extraScrollRolls = 3;
		@Comment("Au moins un parchemin de sort garanti (si aucun tirage n'en donne)")
		public boolean guaranteedScroll = true;
		@Comment("Chance (0 a 1) d'un grimoire vierge en plus")
		public double grimoireChance = 0.35;
		@Comment("Niveaux de menace gagnes par la ruine apres une victoire sur son gardien maudit (au lieu de 1)")
		public int threatGain = 2;
		@Comment("Annonce a tout le serveur (\"La malediction est brisee...\")")
		public boolean announce = true;
	}

	@Json5.Section
	public static final class SourceSettings {
		@Comment("Palier minimal du Carrefour commercial dont le marchand propose parfois le parchemin maudit (0 = jamais)")
		public int tradeTier = 3;
		@Comment("Chance (0 a 1) que le marchand du Carrefour le propose a une visite")
		public double tradeChance = 0.25;
		@Comment("Prix (lingots d'or) du parchemin maudit chez le marchand du Carrefour")
		public int tradePrice = 250;
		@Comment("Chance (0 a 1) qu'un gardien vaincu sous sa forme ressuscitee (Enrage) laisse un parchemin maudit")
		public double risenChance = 0.15;
		@Comment("Chance (0 a 1) qu'un contrat legendaire termine donne un parchemin maudit")
		public double legendaryQuestChance = 0.1;
	}

	// ================================================================== validation

	/** Clamps every value (pure); an unknown base item falls back to the default. */
	public void fix(Predicate<String> itemExists, List<String> warnings) {
		if (ritual == null) {
			ritual = new RitualSettings();
		}
		RitualSettings r = ritual;
		r.item = RuinConfig.normalize(r.item);
		if (!itemExists.test(r.item)) {
			warnings.add("cursed.ritual.item: unknown item '" + r.item + "', " + DEFAULT_ITEM + " used");
			r.item = DEFAULT_ITEM;
		}
		r.maxStack = clamp(r.maxStack, 1, 64);
		r.channelSeconds = clamp(r.channelSeconds, 0.25, 10);
		r.ritualSeconds = clamp(r.ritualSeconds, 2, 30);
		r.drainHealth = clamp(r.drainHealth, 0, 40);
		r.minHealth = clamp(r.minHealth, 1, 40);

		if (guardian == null) {
			guardian = new GuardianSettings();
		}
		GuardianSettings g = guardian;
		g.healthMultiplier = clamp(g.healthMultiplier, 1, 20);
		g.damageMultiplier = clamp(g.damageMultiplier, 1, 10);
		g.armorBonus = clamp(g.armorBonus, 0, 30);
		g.toughnessBonus = clamp(g.toughnessBonus, 0, 20);
		g.scale = clamp(g.scale, 1, 2);
		g.speedBonus = clamp(g.speedBonus, 0, 2);
		g.knockbackResistance = clamp(g.knockbackResistance, 0, 1);
		g.cooldownFactor = clamp(g.cooldownFactor, 0.1, 1);
		g.escortMultiplier = clamp(g.escortMultiplier, 0, 5);
		g.escortBonus = clamp(g.escortBonus, 0, 16);
		g.extraAlive = clamp(g.extraAlive, 0, 32);
		g.minionHealthMultiplier = clamp(g.minionHealthMultiplier, 0.1, 10);
		g.minionDamageMultiplier = clamp(g.minionDamageMultiplier, 0.1, 10);

		if (phases == null) {
			phases = new PhaseSettings();
		}
		PhaseSettings p = phases;
		p.phase2Below = clamp(p.phase2Below, 0.1, 0.95);
		p.phase3Below = clamp(p.phase3Below, 0.05, p.phase2Below - 0.05);
		p.transitionSeconds = clamp(p.transitionSeconds, 2, 10);
		p.damageBonus = clamp(p.damageBonus, 0, 2);
		p.cooldownFactor = clamp(p.cooldownFactor, 0.3, 1);
		p.speedBonus = clamp(p.speedBonus, 0, 1);
		p.minions = clamp(p.minions, 0, 12);
		p.shockwaveRadius = clamp(p.shockwaveRadius, 0, 32);
		p.shockwaveStrength = clamp(p.shockwaveStrength, 0, 4);
		p.darknessSeconds = clamp(p.darknessSeconds, 0, 20);

		if (attacks == null) {
			attacks = new AttackSettings();
		}
		AttackSettings a = attacks;
		a.globalCooldownSeconds = clamp(a.globalCooldownSeconds, 0.5, 60);
		a.pentagramCooldownSeconds = clamp(a.pentagramCooldownSeconds, 3, 600);
		a.pentagramCount = clamp(a.pentagramCount, 1, 8);
		a.pentagramRadius = clamp(a.pentagramRadius, 1, 6);
		a.pentagramSeconds = clamp(a.pentagramSeconds, 2, 30);
		a.pentagramDamage = clamp(a.pentagramDamage, 0, 40);
		a.pentagramHeal = clamp(a.pentagramHeal, 0, 20);
		a.chainCooldownSeconds = clamp(a.chainCooldownSeconds, 3, 600);
		a.chainTargets = clamp(a.chainTargets, 1, 8);
		a.chainPull = clamp(a.chainPull, 0, 2);
		a.chainDamage = clamp(a.chainDamage, 0, 40);
		a.meteorCooldownSeconds = clamp(a.meteorCooldownSeconds, 3, 600);
		a.meteorCount = clamp(a.meteorCount, 1, 16);
		a.meteorDamage = clamp(a.meteorDamage, 0, 60);
		a.meteorRadius = clamp(a.meteorRadius, 1, 6);
		a.darknessCooldownSeconds = clamp(a.darknessCooldownSeconds, 3, 600);
		a.darknessSeconds = clamp(a.darknessSeconds, 1, 30);
		a.darknessDamage = clamp(a.darknessDamage, 0, 40);
		a.cageCooldownSeconds = clamp(a.cageCooldownSeconds, 3, 600);
		a.cageTargets = clamp(a.cageTargets, 1, 8);
		a.cageRadius = clamp(a.cageRadius, 1.5, 6);
		a.cageSeconds = clamp(a.cageSeconds, 1, 10);
		a.cageDamage = clamp(a.cageDamage, 0, 40);
		a.novaCooldownSeconds = clamp(a.novaCooldownSeconds, 3, 600);
		a.novaRadius = clamp(a.novaRadius, 3, 24);
		a.novaDamage = clamp(a.novaDamage, 0, 60);
		a.novaHeal = clamp(a.novaHeal, 0, 20);
		a.cloneCooldownSeconds = clamp(a.cloneCooldownSeconds, 3, 600);
		a.cloneCount = clamp(a.cloneCount, 0, 6);
		a.cloneSeconds = clamp(a.cloneSeconds, 3, 120);
		a.cloneHealth = clamp(a.cloneHealth, 1, 500);
		a.cloneDamageRatio = clamp(a.cloneDamageRatio, 0, 2);
		a.auraRadius = clamp(a.auraRadius, 0, 10);
		a.auraWitherSeconds = clamp(a.auraWitherSeconds, 0, 10);
		a.particleBudget = clamp(a.particleBudget, 100, 10_000);

		if (rewards == null) {
			rewards = new RewardSettings();
		}
		RewardSettings w = rewards;
		w.goldMultiplier = clamp(w.goldMultiplier, 1, 20);
		w.xpMultiplier = clamp(w.xpMultiplier, 1, 20);
		w.extraLootRolls = clamp(w.extraLootRolls, 0, 10);
		w.extraUniqueRolls = clamp(w.extraUniqueRolls, 0, 10);
		w.maxUniques = clamp(w.maxUniques, 1, 5);
		w.extraScrollRolls = clamp(w.extraScrollRolls, 0, 10);
		w.grimoireChance = clamp(w.grimoireChance, 0, 1);
		w.threatGain = clamp(w.threatGain, 0, 5);

		if (sources == null) {
			sources = new SourceSettings();
		}
		SourceSettings s = sources;
		s.tradeTier = clamp(s.tradeTier, 0, 3);
		s.tradeChance = clamp(s.tradeChance, 0, 1);
		s.tradePrice = clamp(s.tradePrice, 1, 2304);
		s.risenChance = clamp(s.risenChance, 0, 1);
		s.legendaryQuestChance = clamp(s.legendaryQuestChance, 0, 1);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp(double value, double min, double max) {
		return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
	}
}
