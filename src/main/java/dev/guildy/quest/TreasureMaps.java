package dev.guildy.quest;

import dev.guildy.core.GuildyItems;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Treasure maps: a player holding one near its (x, z) unearths a chest filled with tier-scaled loot.
 */
final class TreasureMaps {
	private static final Random RANDOM = new Random();
	private static final Map<UUID, Long> LAST_WARNING = new HashMap<>();

	private TreasureMaps() {
	}

	static void init() {
		Scheduler.every("quest.treasure", 20, 9, TreasureMaps::tick);
	}

	/** Gives a treasure map for a random unclaimed spot around the guild base (reward of a quest). */
	static void giveMap(ServerPlayer player, Guild guild) {
		QuestConfig.Treasure config = QuestManager.config().treasure;
		int tier = QuestGenerator.mapTier(guild.level, config.tiers.size());
		int[] point = QuestManager.randomPoint(guild, config.minDistance, config.maxDistance, true);
		if (point == null) {
			// No valid spot (tiny world border...): never lose the reward, give the treasure's gold instead.
			int gold = config.tiers.isEmpty() ? 0 : config.tiers.get(Math.min(tier, config.tiers.size()) - 1).goldMax;
			GuildManager.addTreasury(guild, gold);
			Msg.send(player, "quest.treasure.none", gold);
			return;
		}
		ItemUtil.giveOrDrop(player, GuildyItems.treasureMap(point[0], point[1], tier, guild.id));
		Msg.send(player, "quest.treasure.received", tier);
	}

	private static void tick() {
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			return;
		}
		for (ServerPlayer player : List.copyOf(level.players())) {
			if (player.isSpectator()) {
				continue;
			}
			for (InteractionHand hand : InteractionHand.values()) {
				ItemStack stack = player.getItemInHand(hand);
				if (GuildyItems.is(stack, GuildyItems.KIND_TREASURE_MAP)) {
					Safe.run("quest.treasure", () -> check(player, level, stack));
					break;
				}
			}
		}
	}

	private static void check(ServerPlayer player, ServerLevel level, ItemStack stack) {
		QuestConfig.Treasure config = QuestManager.config().treasure;
		CompoundTag data = GuildyItems.data(stack);
		int x = data.getIntOr("x", 0);
		int z = data.getIntOr("z", 0);
		int tier = Math.max(1, data.getIntOr("tier", 1));
		double dx = player.getX() - (x + 0.5);
		double dz = player.getZ() - (z + 0.5);
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance > config.digRadius) {
			if (distance <= config.hintRadius) {
				Msg.actionBar(player, "quest.treasure.near", (int) Math.ceil(distance));
			}
			return;
		}
		if (!level.isLoaded(new BlockPos(x, level.getMinY(), z))) {
			return;
		}
		BlockPos pos = surface(level, x, z);
		if (pos == null) {
			warn(player, "quest.treasure.blocked");
			return;
		}
		Guild owner = GuildManager.at(level, pos);
		if (owner != null && owner != GuildManager.of(player)) {
			warn(player, "quest.treasure.foreign");
			return;
		}
		// Consume the map before creating the chest (one map = one chest).
		stack.shrink(1);
		player.getInventory().setChanged();
		BlockState chest = Blocks.CHEST.defaultBlockState()
				.setValue(ChestBlock.FACING, player.getDirection().getOpposite())
				.setValue(ChestBlock.WATERLOGGED, level.getFluidState(pos).isSourceOfType(Fluids.WATER));
		level.setBlock(pos, chest, Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof ChestBlockEntity container) {
			fill(player, container, tier, config);
		}
		Msg.title(player, Lang.get("quest.treasure.title"), Lang.get("quest.treasure.subtitle"));
		Msg.send(player, "quest.treasure.found", pos.getX(), pos.getY(), pos.getZ());
		Msg.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
		level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0f, 0.8f);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 40, 0.4, 0.6, 0.4, 0.3);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 15, 0.6, 0.4, 0.6, 0.0);
	}

	private static void fill(ServerPlayer player, ChestBlockEntity container, int tierNumber, QuestConfig.Treasure config) {
		if (config.tiers.isEmpty()) {
			return;
		}
		QuestConfig.TreasureTier tier = config.tiers.get(Math.min(tierNumber, config.tiers.size()) - 1);
		if (!tier.lootTable.isBlank()) {
			Identifier id = Identifier.tryParse(tier.lootTable);
			if (id != null) {
				ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, id);
				container.setLootTable(key, RANDOM.nextLong());
				container.unpackLootTable(player);
			}
		}
		List<ItemStack> extras = new ArrayList<>();
		int gold = tier.goldMax > tier.goldMin ? tier.goldMin + RANDOM.nextInt(tier.goldMax - tier.goldMin + 1) : tier.goldMin;
		while (gold > 0) {
			int n = Math.min(64, gold);
			extras.add(new ItemStack(Items.GOLD_INGOT, n));
			gold -= n;
		}
		for (QuestTemplate.RewardItem reward : tier.items) {
			Item item = reward == null ? null : ItemUtil.item(reward.item);
			if (item != null && RANDOM.nextDouble() < reward.chance) {
				int count = reward.count;
				int max = new ItemStack(item).getMaxStackSize();
				while (count > 0) {
					int n = Math.min(max, count);
					extras.add(new ItemStack(item, n));
					count -= n;
				}
			}
		}
		for (ItemStack extra : extras) {
			int slot = emptySlot(container);
			if (slot < 0) {
				ItemUtil.giveOrDrop(player, extra);
			} else {
				container.setItem(slot, extra);
			}
		}
		container.setChanged();
	}

	private static int emptySlot(ChestBlockEntity container) {
		List<Integer> empty = new ArrayList<>();
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).isEmpty()) {
				empty.add(i);
			}
		}
		return empty.isEmpty() ? -1 : empty.get(RANDOM.nextInt(empty.size()));
	}

	/** First replaceable spot at the surface of the column (null if none within a few blocks). */
	private static @Nullable BlockPos surface(ServerLevel level, int x, int z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos pos = new BlockPos(x, Math.max(level.getMinY(), y), z);
		for (int i = 0; i < 6 && level.isInsideBuildHeight(pos); i++) {
			BlockState state = level.getBlockState(pos);
			if (state.canBeReplaced() && !state.hasBlockEntity()) {
				return pos;
			}
			pos = pos.above();
		}
		return null;
	}

	/** Chat warning at most every 10 seconds per player. */
	private static void warn(ServerPlayer player, String key) {
		long now = Scheduler.ticks();
		Long last = LAST_WARNING.get(player.getUUID());
		if (last == null || now - last >= 200 || now < last) {
			LAST_WARNING.put(player.getUUID(), now);
			Msg.send(player, key);
		}
	}
}
