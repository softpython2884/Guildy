package dev.guildy.wave;

import dev.guildy.core.GuildyItems;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.UniqueDrops;
import dev.guildy.data.Guild;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Extra drops of wave mobs and item rewards of victories.
 */
final class WaveLoot {
	private WaveLoot() {
	}

	/**
	 * Extra drops at the death position: nuggets, elite gold + elite item, boss table + flag / treasure map chances, and
	 * the rare unique weapon of a boss.
	 */
	static void dropKillLoot(ServerLevel level, Mob mob, WavePlanner.PlannedMob planned, Guild guild, BlockPos heart, double difficulty,
			RandomGenerator random) {
		WaveConfig.KillLoot cfg = WaveModule.config().killLoot;
		List<ItemStack> drops = new ArrayList<>();
		if (random.nextDouble() < cfg.nuggetChance) {
			int max = WaveFormula.nuggetMax(cfg, difficulty);
			int count = cfg.nuggetMin + (max > cfg.nuggetMin ? random.nextInt(max - cfg.nuggetMin + 1) : 0);
			if (count > 0) {
				drops.add(new ItemStack(Items.GOLD_NUGGET, count));
			}
		}
		if (planned.isElite()) {
			int gold = cfg.eliteGoldMin + (cfg.eliteGoldMax > cfg.eliteGoldMin ? random.nextInt(cfg.eliteGoldMax - cfg.eliteGoldMin + 1) : 0);
			if (gold > 0) {
				drops.add(new ItemStack(Items.GOLD_INGOT, gold));
			}
			addRoll(drops, cfg.eliteItems, difficulty, random);
		}
		if (planned.isBoss()) {
			for (int i = 0; i < cfg.bossItemRolls; i++) {
				addRoll(drops, cfg.bossItems, difficulty, random);
			}
			if (random.nextDouble() < cfg.bossFlagChance) {
				drops.add(GuildyItems.extensionFlag(null));
			}
			if (random.nextDouble() < cfg.bossMapChance) {
				double angle = random.nextDouble() * Math.PI * 2;
				double distance = cfg.mapMinDistance + random.nextDouble() * (cfg.mapMaxDistance - cfg.mapMinDistance);
				int x = (int) Math.round(heart.getX() + Math.cos(angle) * distance);
				int z = (int) Math.round(heart.getZ() + Math.sin(angle) * distance);
				drops.add(GuildyItems.treasureMap(x, z, WaveFormula.mapTier(cfg, difficulty), guild.id));
			}
		}
		for (ItemStack stack : drops) {
			for (ItemStack part : split(stack)) {
				mob.spawnAtLocation(level, part);
			}
		}
		if (planned.isBoss()) {
			dropUnique(level, mob, guild, random);
		}
	}

	/**
	 * Unique weapon of a boss (uniques.json5: table "blood_boss" during a blood night, else "boss"). The dropped item never
	 * despawns and resists fire, lava and explosions, and glows so that it is found after the fight.
	 */
	private static void dropUnique(ServerLevel level, Mob mob, Guild guild, RandomGenerator random) {
		ItemStack unique = UniqueDrops.roll(isBloodNight(guild) ? "blood_boss" : "boss", random);
		if (unique.isEmpty()) {
			return;
		}
		ItemEntity entity = mob.spawnAtLocation(level, unique);
		if (entity != null) {
			entity.setUnlimitedLifetime();
			entity.setPermanentlyInvulnerable(true);
			entity.setGlowingTag(true);
		}
	}

	/** A night (or test) wave strengthened by event night modifiers: the blood night (event waves are excluded). */
	static boolean isBloodNight(Guild guild) {
		WaveSession session = WaveManager.session(guild);
		return session != null && !session.isEvent() && !NightModifiers.NONE.equals(session.modifiers());
	}

	/** Splits an oversized stack (configurable counts) into valid stacks. */
	static List<ItemStack> split(ItemStack stack) {
		List<ItemStack> parts = new ArrayList<>();
		int max = Math.max(1, stack.getMaxStackSize());
		int remaining = stack.getCount();
		while (remaining > 0) {
			int n = Math.min(max, remaining);
			parts.add(stack.copyWithCount(n));
			remaining -= n;
		}
		return parts;
	}

	/** Rolls {@code rolls} times in the victory table (filtered by difficulty). */
	static List<ItemStack> rollVictoryItems(int rolls, double difficulty, RandomGenerator random) {
		List<ItemStack> items = new ArrayList<>();
		List<WaveConfig.ItemReward> table = WaveModule.config().rewards.items;
		for (int i = 0; i < rolls; i++) {
			addRoll(items, table, difficulty, random);
		}
		return items;
	}

	private static void addRoll(List<ItemStack> out, List<WaveConfig.ItemReward> table, double difficulty, RandomGenerator random) {
		ItemStack stack = roll(table, difficulty, random);
		if (stack != null) {
			out.add(stack);
		}
	}

	static @Nullable ItemStack roll(List<WaveConfig.ItemReward> table, double difficulty, RandomGenerator random) {
		List<WaveConfig.ItemReward> eligible = new ArrayList<>();
		for (WaveConfig.ItemReward reward : table) {
			if (reward.minDifficulty <= difficulty && ItemUtil.item(reward.item) != null) {
				eligible.add(reward);
			}
		}
		WaveConfig.ItemReward reward = WeightedPicker.pick(eligible, r -> r.weight, random);
		if (reward == null) {
			return null;
		}
		Item item = ItemUtil.item(reward.item);
		if (item == null) {
			return null;
		}
		int count = reward.min + (reward.max > reward.min ? random.nextInt(reward.max - reward.min + 1) : 0);
		return new ItemStack(item, Math.max(1, count));
	}
}
