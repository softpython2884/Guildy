package dev.guildy.ruin;

import dev.guildy.core.GuildyServer;
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
import net.minecraft.server.level.ServerPlayer;
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
 * scroll (rare, protected drops), guild XP split between the guilds of the participants. The ruin's threat level adds gold,
 * XP and a chance of extra unique / scroll rolls; a guardian defeated in its risen form gives bonus rolls on top. A group
 * fight (see {@link GroupScaling}) gives more gold and XP, the gold being shared: one pile per participant, that only they
 * can pick up.
 */
final class RuinLoot {
	static final String SOURCE_OVERWORLD = "ruin_boss";
	static final String SOURCE_NETHER = "nether_ruin_boss";

	private RuinLoot() {
	}

	/** Drops everything at the guardian's position. @return the number of stacks dropped (unique / scroll included) */
	static int drop(ServerLevel level, Encounter encounter, Mob boss, RandomGenerator random) {
		RewardDef rewards = encounter.def.rewards;
		ThreatRules.Rewards bonus = bonus(encounter);
		List<ItemStack> drops = new ArrayList<>();
		int rolled = rewards.goldMin + (rewards.goldMax > rewards.goldMin ? random.nextInt(rewards.goldMax - rewards.goldMin + 1) : 0);
		int gold = ThreatRules.scaled(rolled, GroupScaling.combined(bonus.gold(), encounter.party.gold()));
		int dropped = gold > 0 ? dropGold(level, encounter, boss, gold) : 0;
		int lootRolls = rewards.lootRolls + bonus.extraLootRolls();
		if (!rewards.lootTable.isBlank() && lootRolls > 0) {
			Identifier id = Identifier.tryParse(rewards.lootTable);
			if (id != null) {
				LootTable table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
				LootParams params = new LootParams.Builder(level)
						.withParameter(LootContextParams.ORIGIN, boss.position())
						.withOptionalParameter(LootContextParams.THIS_ENTITY, boss)
						.create(LootContextParamSets.CHEST);
				for (int i = 0; i < lootRolls; i++) {
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
		int uniqueRolls = rolls(bonus.extraUniqueChance(), bonus.bonusUniqueRolls(), random);
		// A cursed guardian gets many attempts, but never more than maxUniques weapons.
		int uniqueCap = encounter.cursed != null ? RuinModule.config().cursed.rewards.maxUniques : Integer.MAX_VALUE;
		int uniques = 0;
		for (int i = 0; i < uniqueRolls && uniques < uniqueCap; i++) {
			int n = protectedDrop(level, boss, UniqueDrops.roll(source, random, UniqueDrops.Recipient.topDamager(boss)));
			uniques += n;
			dropped += n;
		}
		String scrollSource = source(encounter.ruin, rewards.scrollSource);
		int scrollRolls = rolls(bonus.extraScrollChance(), bonus.bonusScrollRolls(), random);
		int scrolls = 0;
		for (int i = 0; i < scrollRolls; i++) {
			int n = protectedDrop(level, boss, ScrollDrops.roll(scrollSource, random));
			scrolls += n;
			dropped += n;
		}
		dropped += CursedEncounter.bonusDrops(level, encounter, boss, random, scrollSource, scrolls > 0);
		return dropped;
	}

	/**
	 * The gold, one share per participant still online in the level (only they can pick it up), else a single pile.
	 *
	 * @return the number of stacks dropped
	 */
	private static int dropGold(ServerLevel level, Encounter encounter, Mob boss, int gold) {
		List<UUID> recipients = new ArrayList<>();
		if (RuinModule.config().scaling.shareGold) {
			for (UUID uuid : encounter.participants) {
				ServerPlayer player = GuildyServer.player(uuid);
				if (player != null && player.level() == level) {
					recipients.add(uuid);
				}
			}
		}
		int[] shares = GroupScaling.shares(gold, recipients.size());
		int dropped = 0;
		for (int i = 0; i < shares.length; i++) {
			UUID owner = recipients.size() > 1 ? recipients.get(i) : null;
			for (ItemStack part : split(new ItemStack(Items.GOLD_INGOT, shares[i]))) {
				ItemEntity entity = boss.spawnAtLocation(level, part);
				if (entity != null) {
					entity.setUnlimitedLifetime();
					if (owner != null) {
						entity.setTarget(owner);
					}
					dropped++;
				}
			}
		}
		return dropped;
	}

	/** Reward multipliers of the fight: threat level of the ruin, risen guardian, cursed guardian (the group is apart). */
	static ThreatRules.Rewards bonus(Encounter encounter) {
		RuinConfig cfg = RuinModule.config();
		ThreatRules.Rewards rewards = ThreatRules.rewards(cfg.escalation, cfg.resurrection, encounter.threat, encounter.risen);
		return encounter.cursed != null ? CursedRules.rewards(rewards, cfg.cursed.rewards) : rewards;
	}

	/** One roll, one more with {@code extraChance}, plus the guaranteed bonus rolls. */
	private static int rolls(double extraChance, int bonusRolls, RandomGenerator random) {
		return 1 + (extraChance > 0 && random.nextDouble() < extraChance ? 1 : 0) + Math.max(0, bonusRolls);
	}

	/** Configured source, or ruin_boss / nether_ruin_boss by dimension. */
	static String source(RuinRecord ruin, String configured) {
		if (configured != null && !configured.isBlank()) {
			return configured;
		}
		return ruin.isNether() ? SOURCE_NETHER : SOURCE_OVERWORLD;
	}

	/** Rare drop: never despawns, resists fire / lava / explosions and glows so that it is found after the fight. */
	static int protectedDrop(ServerLevel level, Mob boss, ItemStack stack) {
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
		long total = ThreatRules.scaled(encounter.def.rewards.xp, GroupScaling.combined(bonus(encounter).xp(), encounter.party.xp()));
		Map<UUID, Long> split = RuinRules.splitXp(perGuild, total, minPerGuild);
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
