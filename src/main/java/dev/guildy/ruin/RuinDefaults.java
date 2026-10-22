package dev.guildy.ruin;

import dev.guildy.ruin.RuinConfig.AbilityDef;
import dev.guildy.ruin.RuinConfig.BossDef;
import dev.guildy.ruin.RuinConfig.DreadAbilityDef;
import dev.guildy.ruin.RuinConfig.EffectDef;
import dev.guildy.ruin.RuinConfig.ItemDrop;
import dev.guildy.ruin.RuinConfig.MinionDef;
import dev.guildy.ruin.RuinConfig.RewardDef;
import dev.guildy.ruin.RuinConfig.RuinDef;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default guardians of the eight ruins (tier 1 to 5) and the generic tier-based guardian of unknown ruins.
 * Balance: gold worth the fight and the incantation (about 20 ingots at tier 1 up to 75 for the celestial jaguar, plus one
 * per escort mob killed; never gold blocks), vanilla rare items through the ruin loot tables, a rare unique weapon and
 * spell scroll; the escort grows with the number of players. Each ruin has its room guards (themed names, types): the
 * crypt's skeletons and zombies, the mage tower's illagers, the ziggurat's husks and parched, the frost bastion's strays,
 * the infernal altar's piglin brutes, the soul citadel's wither skeletons, and the undead of the two Mayan temples (bogged,
 * parched, zombies on guard, cave spiders: never a raider), under jade-green or golden helmets.
 */
final class RuinDefaults {
	/** Gold of a guardian by tier (1 to 5): the generic guardian and the default ruins of each tier (Guildy 1.10). */
	static final int[] GOLD_MIN = {15, 20, 28, 36, 50};
	static final int[] GOLD_MAX = {25, 32, 42, 56, 75};
	/** The celestial jaguar, above the soul citadel. */
	static final int JAGUAR_GOLD_MIN = 60;
	static final int JAGUAR_GOLD_MAX = 90;

	private RuinDefaults() {
	}

	static Map<String, RuinDef> ruins() {
		Map<String, RuinDef> map = new LinkedHashMap<>();
		map.put("crypte_oubliee", crypt());
		map.put("tour_du_mage", mageTower());
		map.put("ziggourat_des_sables", ziggurat());
		map.put("bastion_de_givre", frostBastion());
		map.put("autel_infernal", infernalAltar());
		map.put("citadelle_des_ames", soulCitadel());
		map.put("temple_maya", featheredSerpentTemple());
		map.put("grand_temple_maya", celestialJaguarTemple());
		return map;
	}

	private static RuinDef crypt() {
		RuinDef def = ruin("Crypte oubliée", 1, "PURPLE", "minecraft:red_x");
		def.boss = boss("minecraft:wither_skeleton", "&5&lSeigneur des Cryptes", 220, 9, 8, 2, 0, 0.6, 1.5);
		def.boss.equipment.put("mainhand", "minecraft:iron_sword");
		def.boss.equipment.put("chest", "minecraft:chainmail_chestplate");
		def.minions = minions(3, 2, 8, 12, 1.4, 1.1);
		def.minions.types.put("minecraft:skeleton", 3);
		def.minions.types.put("minecraft:zombie", 3);
		def.minions.equipment.put("head", "minecraft:chainmail_helmet");
		def.abilities = new ArrayList<>(List.of(
				ability("ground_slam", 12, 6, 5, 1, "", 1.0),
				ability("summon", 22, 0, 4, 2, "", 0.8),
				ability("life_drain", 18, 3, 10, 1, "", 0.6),
				enrage(0.3, 0)));
		def.dreadAbilities = cryptDread();
		def.rewards = rewards(GOLD_MIN[0], GOLD_MAX[0], 80, "guildy:chests/ruin/rare", 1);
		def.rewards.items.add(new ItemDrop("minecraft:experience_bottle", 4, 8, 1.0));
		def.rewards.items.add(new ItemDrop("minecraft:wither_skeleton_skull", 1, 1, 0.15));
		def.guards.name = "&7Gardien des cryptes";
		return def;
	}

	private static RuinDef mageTower() {
		RuinDef def = ruin("Tour du Mage déchu", 2, "BLUE", "minecraft:red_x");
		def.boss = boss("minecraft:evoker", "&9&lArchimage déchu", 260, 8, 6, 2, 0, 0.7, 1.4);
		def.boss.effects.add(new EffectDef("minecraft:slow_falling", 0));
		def.minions = minions(3, 2, 9, 12, 1.4, 1.1);
		def.minions.types.put("minecraft:vindicator", 4);
		def.minions.types.put("minecraft:vex", 2);
		def.abilities = new ArrayList<>(List.of(
				ability("blink", 10, 7, 4, 1, "", 1.0),
				ability("volley", 9, 5, 4, 5, "spectral", 1.0),
				ability("fire_ring", 15, 6, 7, 1, "fire", 0.85),
				ability("summon", 24, 0, 4, 2, "", 0.7),
				enrage(0.3, 1)));
		def.dreadAbilities = mageDread();
		def.rewards = rewards(GOLD_MIN[1], GOLD_MAX[1], 120, "guildy:chests/ruin/rare", 2);
		def.rewards.items.add(new ItemDrop("minecraft:experience_bottle", 6, 12, 1.0));
		def.rewards.items.add(new ItemDrop("minecraft:totem_of_undying", 1, 1, 0.25));
		// No vex on guard duty: they fly through the walls.
		guards(def, "&9Garde de l'Archimage", "minecraft:vindicator", 1);
		return def;
	}

	private static RuinDef ziggurat() {
		RuinDef def = ruin("Ziggourat des sables", 2, "YELLOW", "minecraft:red_x");
		def.boss = boss("minecraft:husk", "&6&lPharaon des sables", 300, 10, 4, 2, 0.26, 0.7, 1.6);
		def.boss.equipment.put("mainhand", "minecraft:golden_sword");
		def.boss.equipment.put("head", "minecraft:golden_helmet");
		def.boss.equipment.put("chest", "minecraft:golden_chestplate");
		def.boss.equipment.put("legs", "minecraft:golden_leggings");
		def.boss.equipment.put("feet", "minecraft:golden_boots");
		def.minions = minions(4, 2, 10, 14, 1.4, 1.1);
		// Mummies and parched archers (weakness arrows) of the desert.
		def.minions.types.putAll(zigguratEscort());
		def.abilities = new ArrayList<>(List.of(
				ability("ground_slam", 11, 7, 6, 1, "sand", 1.0),
				ability("summon", 18, 0, 4, 3, "", 1.0),
				ability("fire_ring", 16, 6, 7, 1, "fire", 0.75),
				enrage(0.3, 1)));
		def.dreadAbilities = zigguratDread();
		def.rewards = rewards(GOLD_MIN[1], GOLD_MAX[1], 120, "guildy:chests/ruin/rare", 2);
		def.rewards.items.add(new ItemDrop("minecraft:experience_bottle", 6, 12, 1.0));
		def.rewards.items.add(new ItemDrop("minecraft:golden_apple", 1, 2, 0.6));
		def.guards.name = "&6Momie gardienne";
		return def;
	}

	private static RuinDef frostBastion() {
		RuinDef def = ruin("Bastion de givre", 3, "WHITE", "minecraft:red_x");
		def.boss = boss("minecraft:stray", "&b&lRoi de givre", 380, 11, 10, 3, 0.27, 0.8, 1.7);
		def.boss.equipment.put("mainhand", "minecraft:bow");
		def.boss.equipment.put("head", "minecraft:diamond_helmet");
		def.boss.equipment.put("chest", "minecraft:diamond_chestplate");
		def.minions = minions(4, 2, 10, 14, 1.5, 1.2);
		def.minions.types.put("minecraft:stray", 1);
		def.minions.equipment.put("head", "minecraft:iron_helmet");
		def.abilities = new ArrayList<>(List.of(
				ability("fire_ring", 12, 7, 7, 1, "frost", 1.0),
				ability("volley", 10, 6, 4, 6, "arrow", 1.0),
				ability("blink", 14, 8, 4, 1, "", 0.7),
				ability("summon", 22, 0, 4, 3, "", 0.6),
				enrage(0.3, 1)));
		def.dreadAbilities = frostDread();
		def.rewards = rewards(GOLD_MIN[2], GOLD_MAX[2], 160, "guildy:chests/ruin/treasure", 1);
		def.rewards.items.add(new ItemDrop("minecraft:experience_bottle", 8, 16, 1.0));
		def.rewards.items.add(new ItemDrop("minecraft:diamond", 1, 3, 0.5));
		def.guards.name = "&bSentinelle de givre";
		return def;
	}

	private static RuinDef infernalAltar() {
		RuinDef def = ruin("Autel infernal", 4, "RED", "minecraft:red_x");
		def.boss = boss("minecraft:piglin_brute", "&c&lSeigneur de guerre", 480, 16, 14, 5, 0.38, 1.0, 1.6);
		def.boss.equipment.put("mainhand", "minecraft:netherite_axe");
		def.boss.equipment.put("chest", "minecraft:netherite_chestplate");
		def.boss.effects.add(new EffectDef("minecraft:fire_resistance", 0));
		def.minions = minions(3, 2, 9, 12, 1.4, 1.1);
		def.minions.types.put("minecraft:piglin_brute", 3);
		def.minions.types.put("minecraft:magma_cube", 2);
		def.abilities = new ArrayList<>(List.of(
				ability("ground_slam", 10, 10, 6, 1, "", 1.0),
				ability("fire_ring", 14, 8, 8, 1, "fire", 1.0),
				ability("volley", 12, 5, 4, 4, "fireball", 0.8),
				ability("summon", 22, 0, 4, 2, "", 0.7),
				enrage(0.3, 1)));
		def.dreadAbilities = infernalDread();
		def.rewards = rewards(GOLD_MIN[3], GOLD_MAX[3], 220, "guildy:chests/ruin/nether_treasure", 1);
		def.rewards.items.add(new ItemDrop("minecraft:experience_bottle", 8, 16, 1.0));
		def.rewards.items.add(new ItemDrop("minecraft:netherite_scrap", 1, 2, 0.5));
		// No magma cube on guard duty (their split children would not be guards).
		guards(def, "&cGarde infernal", "minecraft:piglin_brute", 1);
		return def;
	}

	private static RuinDef soulCitadel() {
		RuinDef def = ruin("Citadelle des âmes", 5, "BLUE", "minecraft:red_x");
		def.boss = boss("minecraft:wither_skeleton", "&3&lChampion des âmes", 650, 20, 18, 8, 0.3, 1.0, 2.0);
		def.boss.equipment.put("mainhand", "minecraft:netherite_sword");
		def.boss.equipment.put("head", "minecraft:netherite_helmet");
		def.boss.equipment.put("chest", "minecraft:netherite_chestplate");
		def.minions = minions(4, 2, 10, 14, 1.5, 1.2);
		def.minions.types.put("minecraft:wither_skeleton", 3);
		def.minions.types.put("minecraft:blaze", 2);
		def.abilities = new ArrayList<>(List.of(
				ability("life_drain", 14, 5, 12, 1, "soul", 1.0),
				ability("fire_ring", 12, 9, 8, 1, "soul", 1.0),
				ability("blink", 11, 12, 4, 1, "", 1.0),
				ability("ground_slam", 13, 12, 7, 1, "", 0.8),
				ability("volley", 12, 5, 4, 5, "fireball", 0.6),
				ability("summon", 24, 0, 4, 3, "", 0.6),
				enrage(0.3, 2)));
		def.dreadAbilities = soulDread();
		def.rewards = rewards(GOLD_MIN[4], GOLD_MAX[4], 300, "guildy:chests/ruin/nether_treasure", 2);
		def.rewards.items.add(new ItemDrop("minecraft:experience_bottle", 12, 24, 1.0));
		def.rewards.items.add(new ItemDrop("minecraft:netherite_scrap", 1, 3, 0.75));
		// No blaze on guard duty: they fly off their post.
		guards(def, "&3Gardien des âmes", "minecraft:wither_skeleton", 1);
		return def;
	}

	/**
	 * Jungle temple (structure guildy:ruin/temple_maya): a giant breeze whose natural attack is the wind charge (it deflects
	 * arrows and other projectiles, as in vanilla: a melee fight), jade wind rings that poison, wind blades and gusts;
	 * the temple's undead: bogged (poison arrows) and parched (weakness arrows) skeletons, cave spiders; zombies too on guard,
	 * under jade-green helmets (turtle shells). Same rewards as the other tier-3 ruin, emeralds for the jungle.
	 */
	private static RuinDef featheredSerpentTemple() {
		RuinDef def = ruin("Temple du Serpent à plumes", 3, "GREEN", "minecraft:red_x");
		def.boss = boss("minecraft:breeze", "&2&lLe Serpent à plumes", 360, 10, 8, 3, 0, 0.8, 2.0);
		def.minions = minions(4, 2, 10, 14, 1.4, 1.1);
		def.minions.types.putAll(serpentEscort());
		def.abilities = new ArrayList<>(List.of(
				ability("volley", 9, 6, 4, 5, "wind", 1.0),
				ability("fire_ring", 13, 7, 7, 1, "jade", 1.0),
				ability("blink", 12, 8, 4, 1, "wind", 0.8),
				ability("summon", 22, 0, 4, 3, "", 0.65),
				enrage(0.3, 1)));
		def.dreadAbilities = serpentDread();
		def.rewards = rewards(GOLD_MIN[2], GOLD_MAX[2], 160, "guildy:chests/ruin/treasure", 1);
		def.rewards.items.add(new ItemDrop("minecraft:experience_bottle", 8, 16, 1.0));
		def.rewards.items.add(new ItemDrop("minecraft:diamond", 1, 3, 0.5));
		def.rewards.items.add(new ItemDrop("minecraft:emerald", 3, 8, 0.6));
		def.guards.name = "&2Gardien du temple";
		def.guards.types.putAll(serpentGuards());
		def.guards.equipment.put("head", JADE_HELMET);
		return def;
	}

	/**
	 * Second jungle temple, the huge one (structure guildy:ruin/grand_temple_maya, indoor arena under a beacon): the
	 * celestial jaguar, a giant spotted ocelot (x5: 3 blocks wide, 3.5 high, like the old giant ravager) turned into a
	 * relentless hunter ({@link Felines}: it sprints after the players, never flees, cannot be fed, tamed or leashed), fast
	 * but on foot (step height 1, never flies nor teleports), no projectile; its jaguar attacks ({@link JaguarAbilities}):
	 * pounce, claw sweep, stalk, roar, spirit jaguars, plus the sun ring. Its cursed form is a giant black cat, "Le Jaguar
	 * d'obsidienne". The temple's undead on guard and as escort: bogged, parched, zombies on guard, cave spiders, under
	 * golden helmets; rewards above the soul citadel.
	 */
	private static RuinDef celestialJaguarTemple() {
		RuinDef def = ruin("Grand Temple du Jaguar céleste", 5, "YELLOW", "minecraft:red_x");
		def.activationRadius = 24;
		def.leashRadius = 22;
		def.boss = jaguar();
		def.minions = minions(4, 2, 10, 14, 1.5, 1.2);
		def.minions.types.putAll(jaguarEscort());
		def.minions.equipment.put("head", GOLD_HELMET);
		def.abilities = jaguarAbilities();
		def.dreadAbilities = jaguarDread();
		def.rewards = rewards(JAGUAR_GOLD_MIN, JAGUAR_GOLD_MAX, 380, "guildy:chests/ruin/treasure", 3);
		def.rewards.items.add(new ItemDrop("minecraft:experience_bottle", 16, 28, 1.0));
		def.rewards.items.add(new ItemDrop("minecraft:diamond", 2, 4, 0.75));
		def.rewards.items.add(new ItemDrop("minecraft:emerald", 6, 12, 0.75));
		def.rewards.items.add(new ItemDrop("minecraft:totem_of_undying", 1, 1, 0.3));
		def.rewards.items.add(new ItemDrop("minecraft:enchanted_golden_apple", 1, 1, 0.05));
		def.guards.name = "&6Guerrier jaguar";
		def.guards.types.putAll(jaguarGuards());
		def.guards.equipment.put("head", GOLD_HELMET);
		def.guards.healthMultiplier = 1.8;
		def.guards.damageMultiplier = 1.3;
		return def;
	}

	// ------------------------------------------------------------------ themed escorts and guards (1.8.2)

	/** Jade-green helmet of the feathered serpent's undead (a turtle shell: no jade item in the game). */
	static final String JADE_HELMET = "minecraft:turtle_helmet";
	static final String GOLD_HELMET = "minecraft:golden_helmet";

	static Map<String, Integer> zigguratEscort() {
		return weights("minecraft:husk", 3, "minecraft:parched", 2);
	}

	static Map<String, Integer> serpentEscort() {
		return weights("minecraft:bogged", 3, "minecraft:parched", 2, "minecraft:cave_spider", 2);
	}

	static Map<String, Integer> serpentGuards() {
		return weights("minecraft:bogged", 3, "minecraft:parched", 2, "minecraft:zombie", 2, "minecraft:cave_spider", 1);
	}

	static Map<String, Integer> jaguarEscort() {
		return weights("minecraft:bogged", 3, "minecraft:parched", 2, "minecraft:cave_spider", 2);
	}

	static Map<String, Integer> jaguarGuards() {
		return weights("minecraft:bogged", 3, "minecraft:parched", 3, "minecraft:zombie", 2, "minecraft:cave_spider", 1);
	}

	/** The celestial jaguar: a giant spotted ocelot, a giant black cat when cursed. */
	static BossDef jaguar() {
		BossDef boss = boss("minecraft:ocelot", "&6&lLe Jaguar céleste", 760, 18, 16, 6, JAGUAR_SPEED, 1.0, JAGUAR_SCALE);
		boss.effects.add(new EffectDef("minecraft:fire_resistance", 0));
		boss.cursedForm = jaguarCursedForm();
		return boss;
	}

	static final double JAGUAR_SCALE = 5.0;
	/** x1.33 when it sprints: a little slower than a sprinting player, much faster once enraged. */
	static final double JAGUAR_SPEED = 0.26;

	static RuinConfig.CursedFormDef jaguarCursedForm() {
		return new RuinConfig.CursedFormDef("minecraft:cat", "minecraft:all_black", "&5&lLe Jaguar d'obsidienne");
	}

	/** Claws, pounce, roar, stalk and spirits; the sun ring and the temple's undead when it weakens. */
	static List<AbilityDef> jaguarAbilities() {
		return new ArrayList<>(List.of(
				ability("claw_sweep", 7, 14, 5, 3, "jade", 1.0),
				ability("pounce", 12, 15, 5, 1, "gold", 1.0),
				ability("roar", 16, 4, 10, 1, "solar", 0.9),
				ability("stalk", 20, 20, 3, 1, "gold", 0.85),
				ability("spirit_jaguars", 26, 7, 5, 3, "jade", 0.7),
				ability("fire_ring", 16, 9, 8, 1, "solar", 0.6),
				ability("summon", 26, 0, 5, 3, "", 0.5),
				enrage(0.3, 2)));
	}

	private static Map<String, Integer> weights(Object... entityWeight) {
		Map<String, Integer> map = new LinkedHashMap<>();
		for (int i = 0; i + 1 < entityWeight.length; i += 2) {
			map.put((String) entityWeight[i], (Integer) entityWeight[i + 1]);
		}
		return map;
	}

	/**
	 * Guardian of a ruin id unknown to ruins.json5 (a ruin added by another data pack): a vindicator (Overworld) or a
	 * wither skeleton (Nether) whose statistics and rewards scale with the tier.
	 */
	static RuinDef generic(int tier, boolean nether) {
		int t = Math.max(1, Math.min(5, tier));
		RuinDef def = ruin(nether ? "Ruine infernale" : "Ruine oubliée", t, nether ? "RED" : "PURPLE", "");
		def.boss = boss(nether ? "minecraft:wither_skeleton" : "minecraft:vindicator", nether ? "&c&lGardien infernal" : "&5&lGardien des ruines",
				150 + 90 * (t - 1), 7 + 2.5 * (t - 1), 4 + 3 * (t - 1), 1 + (t - 1), 0, 0.6 + 0.1 * (t - 1), 1.4 + 0.1 * (t - 1));
		def.boss.equipment.put("mainhand", t >= 4 ? "minecraft:netherite_axe" : "minecraft:iron_axe");
		if (nether) {
			def.boss.effects.add(new EffectDef("minecraft:fire_resistance", 0));
		}
		def.minions = minions(3, 2, 8, 12, 1.3 + 0.1 * t, 1.1);
		if (nether) {
			def.minions.types.put("minecraft:wither_skeleton", 2);
			def.minions.types.put("minecraft:blaze", 1);
		} else {
			def.minions.types.put("minecraft:zombie", 2);
			def.minions.types.put("minecraft:skeleton", 2);
			def.minions.equipment.put("head", "minecraft:iron_helmet");
		}
		def.abilities = new ArrayList<>(List.of(
				ability("ground_slam", 12, 5 + 1.5 * t, 5, 1, "", 1.0),
				ability(nether ? "fire_ring" : "volley", 14, 4 + t, 6, 4, nether ? "fire" : "arrow", 1.0),
				ability("summon", 22, 0, 4, 2, "", 0.7),
				enrage(0.3, t >= 4 ? 1 : 0)));
		def.dreadAbilities = genericDread(t, nether);
		int[] xp = {80, 120, 160, 220, 300};
		String table = nether ? (t >= 4 ? "guildy:chests/ruin/nether_treasure" : "guildy:chests/ruin/nether_rare")
				: (t >= 3 ? "guildy:chests/ruin/treasure" : "guildy:chests/ruin/rare");
		def.rewards = rewards(GOLD_MIN[t - 1], GOLD_MAX[t - 1], xp[t - 1], table, t >= 4 ? 2 : 1);
		def.rewards.items.add(new ItemDrop("minecraft:experience_bottle", 2 + 2 * t, 4 + 4 * t, 1.0));
		return def;
	}

	// ------------------------------------------------------------------ dread abilities (Guildy 1.14)

	/** Dread abilities of a default ruin id (empty for an unknown id). */
	static List<DreadAbilityDef> dreadOf(String ruinId) {
		return switch (ruinId) {
			case "crypte_oubliee" -> cryptDread();
			case "tour_du_mage" -> mageDread();
			case "ziggourat_des_sables" -> zigguratDread();
			case "bastion_de_givre" -> frostDread();
			case "autel_infernal" -> infernalDread();
			case "citadelle_des_ames" -> soulDread();
			case "temple_maya" -> serpentDread();
			case "grand_temple_maya" -> jaguarDread();
			default -> new ArrayList<>();
		};
	}

	/** The default settings of a dread ability or Avatar attack type (from the guardian that uses it), or null. */
	static @Nullable DreadAbilityDef defaultDread(String type) {
		List<DreadAbilityDef> all = new ArrayList<>();
		for (String id : ruins().keySet()) {
			all.addAll(dreadOf(id));
		}
		all.addAll(AvatarSettings.defaultAttacks());
		for (DreadAbilityDef def : all) {
			if (def.type.equals(type)) {
				return def;
			}
		}
		return null;
	}

	/** The crypt: graves bursting with grasping hands, lines of bone spikes, a bone prison closing on a player. */
	static List<DreadAbilityDef> cryptDread() {
		return new ArrayList<>(List.of(
				dread("grasping_graves", 16, 6, 2.5, 2, 2.5, 1),
				dread("bone_spikes", 13, 8, 14, 3, 0, 1),
				dread("bone_prison", 22, 3, 5, 1, 3, 1)));
	}

	/** The fallen archmage: a barrage of arcane missiles, a gravity well, mirror images. */
	static List<DreadAbilityDef> mageDread() {
		return new ArrayList<>(List.of(
				dread("arcane_barrage", 12, 5, 1.2, 6, 0, 1),
				dread("gravity_well", 18, 4, 7, 1, 4, 1),
				dread("mirror_clones", 26, 0, 20, 3, 15, 1)));
	}

	/** The sand pharaoh: a quicksand pit, the mummy's curse, a blinding sandstorm (only its eye is safe). */
	static List<DreadAbilityDef> zigguratDread() {
		return new ArrayList<>(List.of(
				dread("quicksand", 16, 3, 4.5, 1, 4, 1),
				dread("mummy_curse", 20, 10, 4, 1, 4, 1),
				dread("sandstorm", 28, 2, 4, 1, 6, 1)));
	}

	/** The frost king: ice spike eruptions, frozen ground, a blizzard dome. */
	static List<DreadAbilityDef> frostDread() {
		return new ArrayList<>(List.of(
				dread("ice_spikes", 12, 9, 16, 4, 0, 1),
				dread("frozen_ground", 16, 2, 3, 3, 6, 2),
				dread("blizzard_dome", 30, 3, 4, 1, 6, 2)));
	}

	/** The feathered serpent: a storm of feathers, a dive bomb, a poison rain. */
	static List<DreadAbilityDef> serpentDread() {
		return new ArrayList<>(List.of(
				dread("feather_storm", 18, 4, 16, 5, 4, 1),
				dread("dive_bomb", 14, 12, 4, 1, 0, 1),
				dread("poison_rain", 20, 5, 2, 14, 4, 1)));
	}

	/** The celestial jaguar: a chain of pounces, a stunning roar, a solar beam. */
	static List<DreadAbilityDef> jaguarDread() {
		return new ArrayList<>(List.of(
				dread("pounce_chain", 18, 14, 4, 3, 0, 1),
				dread("stunning_roar", 20, 6, 12, 1, 2, 1),
				dread("solar_beam", 22, 4, 20, 1, 3, 1)));
	}

	/** The warlord of the infernal altar: lava geysers, a rain of magma meteors, a molten ring. */
	static List<DreadAbilityDef> infernalDread() {
		return new ArrayList<>(List.of(
				dread("lava_geysers", 13, 12, 2.5, 3, 0, 1),
				dread("magma_meteors", 20, 10, 3, 10, 4, 1),
				dread("molten_ring", 22, 14, 18, 3, 0, 1)));
	}

	/** The soul champion: soul chains (stay close), a wither aura (keep away), a soul eruption. */
	static List<DreadAbilityDef> soulDread() {
		return new ArrayList<>(List.of(
				dread("soul_chains", 18, 6, 7, 2, 6, 1),
				dread("wither_aura", 22, 4, 6, 1, 5, 1),
				dread("soul_eruption", 16, 14, 18, 3, 0, 1)));
	}

	/** The generic guardian of an unknown ruin: three dread abilities of its dimension, damage by tier. */
	static List<DreadAbilityDef> genericDread(int tier, boolean nether) {
		double k = 1 + 0.25 * (Math.max(1, Math.min(5, tier)) - 1);
		if (nether) {
			return new ArrayList<>(List.of(
					dread("lava_geysers", 14, 8 * k, 2.5, 2, 0, 1),
					dread("soul_eruption", 18, 9 * k, 16, 3, 0, 1),
					dread("magma_meteors", 22, 7 * k, 3, 8, 4, 1)));
		}
		return new ArrayList<>(List.of(
				dread("bone_spikes", 13, 6 * k, 14, 3, 0, 1),
				dread("grasping_graves", 16, 4 * k, 2.5, 2, 2.5, 1),
				dread("arcane_barrage", 14, 4 * k, 1.2, 5, 0, 1)));
	}

	/** A dread ability, its effect level {@code amplifier}. */
	static DreadAbilityDef dread(String type, double cooldown, double damage, double radius, int count, double seconds, int amplifier) {
		DreadAbilityDef ability = new DreadAbilityDef();
		ability.type = type;
		ability.cooldownSeconds = cooldown;
		ability.damage = damage;
		ability.radius = radius;
		ability.count = count;
		ability.durationSeconds = seconds;
		ability.amplifier = amplifier;
		return ability;
	}

	/** An attack of the Avatar, used from {@code phase} of its cursed fight. */
	static DreadAbilityDef avatar(String type, int phase, double cooldown, double damage, double radius, int count, double seconds) {
		DreadAbilityDef ability = dread(type, cooldown, damage, radius, count, seconds, 1);
		ability.phase = phase;
		return ability;
	}

	// ------------------------------------------------------------------ builders

	private static RuinDef ruin(String name, int tier, String color, String decoration) {
		RuinDef def = new RuinDef();
		def.name = name;
		def.tier = tier;
		def.activationRadius = 20;
		def.resetSeconds = 60;
		def.respawnDays = 7;
		def.leashRadius = 18;
		def.bossBarColor = color;
		def.mapDecoration = decoration;
		return def;
	}

	static BossDef boss(String entity, String name, double health, double damage, double armor, double toughness, double speed,
			double knockback, double scale) {
		BossDef boss = new BossDef();
		boss.entity = entity;
		boss.name = name;
		boss.health = health;
		boss.damage = damage;
		boss.armor = armor;
		boss.armorToughness = toughness;
		boss.speed = speed;
		boss.knockbackResistance = Math.min(1.0, knockback);
		boss.scale = scale;
		boss.followRange = 32;
		return boss;
	}

	private static MinionDef minions(int base, int perExtraPlayer, int max, int maxAlive, double health, double damage) {
		MinionDef minions = new MinionDef();
		minions.base = base;
		minions.perExtraPlayer = perExtraPlayer;
		minions.max = max;
		minions.maxAlive = maxAlive;
		minions.healthMultiplier = health;
		minions.damageMultiplier = damage;
		return minions;
	}

	static AbilityDef ability(String type, double cooldown, double damage, double radius, int count, String style, double healthBelow) {
		AbilityDef ability = new AbilityDef();
		ability.type = type;
		ability.cooldownSeconds = cooldown;
		ability.damage = damage;
		ability.radius = radius;
		ability.count = count;
		ability.style = style;
		ability.healthBelow = healthBelow;
		return ability;
	}

	static AbilityDef enrage(double healthBelow, int amplifier) {
		AbilityDef ability = ability("enrage", 1, 0, 1, 1, "", healthBelow);
		ability.amplifier = amplifier;
		return ability;
	}

	/** Named guards of one type (more types can be added to {@code def.guards.types}). */
	private static void guards(RuinDef def, String name, String entity, int weight) {
		def.guards.name = name;
		def.guards.types.put(entity, weight);
	}

	private static RewardDef rewards(int goldMin, int goldMax, int xp, String lootTable, int lootRolls) {
		RewardDef rewards = new RewardDef();
		rewards.goldMin = goldMin;
		rewards.goldMax = goldMax;
		rewards.xp = xp;
		rewards.lootTable = lootTable;
		rewards.lootRolls = lootRolls;
		return rewards;
	}
}
