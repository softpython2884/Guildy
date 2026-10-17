package dev.guildy.ruin;

import dev.guildy.ruin.RuinConfig.AbilityDef;
import dev.guildy.ruin.RuinConfig.BossDef;
import dev.guildy.ruin.RuinConfig.EffectDef;
import dev.guildy.ruin.RuinConfig.ItemDrop;
import dev.guildy.ruin.RuinConfig.MinionDef;
import dev.guildy.ruin.RuinConfig.RewardDef;
import dev.guildy.ruin.RuinConfig.RuinDef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default guardians of the seven ruins (tier 1 to 5) and the generic tier-based guardian of unknown ruins.
 * Balance: modest gold (never gold blocks), vanilla rare items through the ruin loot tables, a rare unique weapon and
 * spell scroll; the escort grows with the number of players.
 */
final class RuinDefaults {
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
		def.rewards = rewards(4, 8, 80, "guildy:chests/ruin/rare", 1);
		def.rewards.items.add(new ItemDrop("minecraft:experience_bottle", 4, 8, 1.0));
		def.rewards.items.add(new ItemDrop("minecraft:wither_skeleton_skull", 1, 1, 0.15));
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
		def.rewards = rewards(6, 10, 120, "guildy:chests/ruin/rare", 2);
		def.rewards.items.add(new ItemDrop("minecraft:experience_bottle", 6, 12, 1.0));
		def.rewards.items.add(new ItemDrop("minecraft:totem_of_undying", 1, 1, 0.25));
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
		def.minions.types.put("minecraft:husk", 1);
		def.abilities = new ArrayList<>(List.of(
				ability("ground_slam", 11, 7, 6, 1, "sand", 1.0),
				ability("summon", 18, 0, 4, 3, "", 1.0),
				ability("fire_ring", 16, 6, 7, 1, "fire", 0.75),
				enrage(0.3, 1)));
		def.rewards = rewards(6, 10, 120, "guildy:chests/ruin/rare", 2);
		def.rewards.items.add(new ItemDrop("minecraft:experience_bottle", 6, 12, 1.0));
		def.rewards.items.add(new ItemDrop("minecraft:golden_apple", 1, 2, 0.6));
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
		def.rewards = rewards(8, 14, 160, "guildy:chests/ruin/treasure", 1);
		def.rewards.items.add(new ItemDrop("minecraft:experience_bottle", 8, 16, 1.0));
		def.rewards.items.add(new ItemDrop("minecraft:diamond", 1, 3, 0.5));
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
		def.rewards = rewards(10, 18, 220, "guildy:chests/ruin/nether_treasure", 1);
		def.rewards.items.add(new ItemDrop("minecraft:experience_bottle", 8, 16, 1.0));
		def.rewards.items.add(new ItemDrop("minecraft:netherite_scrap", 1, 2, 0.5));
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
		def.rewards = rewards(14, 24, 300, "guildy:chests/ruin/nether_treasure", 2);
		def.rewards.items.add(new ItemDrop("minecraft:experience_bottle", 12, 24, 1.0));
		def.rewards.items.add(new ItemDrop("minecraft:netherite_scrap", 1, 3, 0.75));
		return def;
	}

	/**
	 * Jungle temple (structure guildy:ruin/temple_maya): a giant breeze whose natural attack is the wind charge (it deflects
	 * arrows and other projectiles, as in vanilla: a melee fight), jade wind rings that poison, wind blades and gusts;
	 * bogged skeletons (poison arrows) and cave spiders. Same rewards as the other tier-3 ruin, emeralds for the jungle.
	 */
	private static RuinDef featheredSerpentTemple() {
		RuinDef def = ruin("Temple du Serpent à plumes", 3, "GREEN", "minecraft:red_x");
		def.boss = boss("minecraft:breeze", "&2&lLe Serpent à plumes", 360, 10, 8, 3, 0, 0.8, 2.0);
		def.minions = minions(4, 2, 10, 14, 1.4, 1.1);
		def.minions.types.put("minecraft:bogged", 3);
		def.minions.types.put("minecraft:cave_spider", 2);
		def.abilities = new ArrayList<>(List.of(
				ability("volley", 9, 6, 4, 5, "wind", 1.0),
				ability("fire_ring", 13, 7, 7, 1, "jade", 1.0),
				ability("blink", 12, 8, 4, 1, "wind", 0.8),
				ability("summon", 22, 0, 4, 3, "", 0.65),
				enrage(0.3, 1)));
		def.rewards = rewards(8, 14, 160, "guildy:chests/ruin/treasure", 1);
		def.rewards.items.add(new ItemDrop("minecraft:experience_bottle", 8, 16, 1.0));
		def.rewards.items.add(new ItemDrop("minecraft:diamond", 1, 3, 0.5));
		def.rewards.items.add(new ItemDrop("minecraft:emerald", 3, 8, 0.6));
		return def;
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
		int[] goldMin = {4, 6, 8, 10, 14};
		int[] goldMax = {8, 10, 14, 18, 24};
		int[] xp = {80, 120, 160, 220, 300};
		String table = nether ? (t >= 4 ? "guildy:chests/ruin/nether_treasure" : "guildy:chests/ruin/nether_rare")
				: (t >= 3 ? "guildy:chests/ruin/treasure" : "guildy:chests/ruin/rare");
		def.rewards = rewards(goldMin[t - 1], goldMax[t - 1], xp[t - 1], table, t >= 4 ? 2 : 1);
		def.rewards.items.add(new ItemDrop("minecraft:experience_bottle", 2 + 2 * t, 4 + 4 * t, 1.0));
		return def;
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

	private static BossDef boss(String entity, String name, double health, double damage, double armor, double toughness, double speed,
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

	private static AbilityDef ability(String type, double cooldown, double damage, double radius, int count, String style, double healthBelow) {
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

	private static AbilityDef enrage(double healthBelow, int amplifier) {
		AbilityDef ability = ability("enrage", 1, 0, 1, 1, "", healthBelow);
		ability.amplifier = amplifier;
		return ability;
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
