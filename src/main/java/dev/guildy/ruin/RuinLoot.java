package dev.guildy.ruin;

import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.ScrollDrops;
import dev.guildy.core.UniqueDrops;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.ruin.RuinConfig.ItemDrop;
import dev.guildy.ruin.RuinConfig.RewardDef;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Rewards of a defeated guardian: gold ingots, rolls in a vanilla loot table, extra items, a unique weapon and a spell
 * scroll (rare, protected drops), guild XP split between the guilds of the participants.
 */
final class RuinLoot {
	static final String SOURCE_OVERWORLD = "ruin_boss";
	static final String SOURCE_NETHER = "nether_ruin_boss";

	private RuinLoot() {
	}

	/** Drops everything at the guardian's position. @return the number of stacks dropped (unique / scroll included) */
	static int drop(ServerLevel level, Encounter encounter, Mob boss, RandomGenerator random) {
		RewardDef rewards = encounter.def.rewards;
		List<ItemStack> drops = new ArrayList<>();
		int gold = rewards.goldMin + (rewards.goldMax > rewards.goldMin ? random.nextInt(rewards.goldMax - rewards.goldMin + 1) : 0);
		if (gold > 0) {
			drops.add(new ItemStack(Items.GOLD_INGOT, gold));
		}
		if (!rewards.lootTable.isBlank() && rewards.lootRolls > 0) {
			Identifier id = Identifier.tryParse(rewards.lootTable);
			if (id != null) {
				LootTable table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
				LootParams params = new LootParams.Builder(level)
						.withParameter(LootContextParams.ORIGIN, boss.position())
						.withOptionalParameter(LootContextParams.THIS_ENTITY, boss)
						.create(LootContextParamSets.CHEST);
				for (int i = 0; i < rewards.lootRolls; i++) {
					drops.addAll(table.getRandomItems(params));
				}
			}
		}
		for (ItemDrop extra : rewards.items) {
			Item item = ItemUtil.item(extra.item);
			if (item != null && random.nextDouble() < extra.chance) {
				int count = extra.min + (extra.max > extra.min ? random.nextInt(extra.max - extra.min + 1) : 0);
				drops.add(new ItemStack(item, count));
			}
		}
		int dropped = 0;
		for (ItemStack stack : drops) {
			for (ItemStack part : split(stack)) {
				ItemEntity entity = boss.spawnAtLocation(level, part);
				if (entity != null) {
					entity.setUnlimitedLifetime();
					dropped++;
				}
			}
		}
		String source = source(encounter.ruin, rewards.uniqueSource);
		dropped += protectedDrop(level, boss, UniqueDrops.roll(source, random));
		dropped += protectedDrop(level, boss, ScrollDrops.roll(source(encounter.ruin, rewards.scrollSource), random));
		return dropped;
	}

	/** Configured source, or ruin_boss / nether_ruin_boss by dimension. */
	static String source(RuinRecord ruin, String configured) {
		if (configured != null && !configured.isBlank()) {
			return configured;
		}
		return ruin.isNether() ? SOURCE_NETHER : SOURCE_OVERWORLD;
	}

	/** Rare drop: never despawns, resists fire / lava / explosions and glows so that it is found after the fight. */
	private static int protectedDrop(ServerLevel level, Mob boss, ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		ItemEntity entity = boss.spawnAtLocation(level, stack);
		if (entity == null) {
			return 0;
		}
		entity.setUnlimitedLifetime();
		entity.setPermanentlyInvulnerable(true);
		entity.setGlowingTag(true);
		return 1;
	}

	/** Guild XP split between the guilds of the participants (see {@link RuinRules#splitXp}). */
	static void giveXp(Encounter encounter, int minPerGuild) {
		Map<UUID, Integer> perGuild = new LinkedHashMap<>();
		for (UUID player : encounter.participants) {
			Guild guild = GuildManager.of(player);
			if (guild != null) {
				perGuild.merge(guild.id, 1, Integer::sum);
			}
		}
		Map<UUID, Long> split = RuinRules.splitXp(perGuild, encounter.def.rewards.xp, minPerGuild);
		String ruinName = Lang.colorize(encounter.def.name);
		for (Map.Entry<UUID, Long> entry : split.entrySet()) {
			Guild guild = GuildManager.get(entry.getKey());
			if (guild == null) {
				continue;
			}
			RuinIndex.guild(guild.id).addKnown(encounter.ruin.key);
			if (entry.getValue() > 0) {
				GuildManager.addXp(guild, entry.getValue());
				Msg.guild(guild, "ruin.victory.xp", entry.getValue(), ruinName);
			}
		}
		RuinIndex.markDirty();
	}

	private static List<ItemStack> split(ItemStack stack) {
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
}
