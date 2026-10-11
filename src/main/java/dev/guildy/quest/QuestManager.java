package dev.guildy.quest;

import dev.guildy.config.ConfigHandle;
import dev.guildy.core.GuildyItems;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildEvents;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import dev.guildy.shop.PlainItems;
import dev.guildy.structure.StructureManager;
import dev.guildy.structure.TextUtil;
import dev.guildy.upgrade.UpgradeEffects;
import dev.guildy.upgrade.UpgradeMath;
import dev.guildy.upgrade.Upgrades;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Quest lifecycle: generation on the quest board, progress (block breaks, kills, deliveries, wave victories,
 * exploration), completion, rewards, expiry.
 */
public final class QuestManager {
	public static final String BOARD = "quest_board";
	/** GATHER anti-abuse: a block position counts once per quest (memory only, bounded). */
	private static final int MAX_COUNTED_POSITIONS = 16384;
	private static final int TICK_SLICES = 5;

	private static ConfigHandle<QuestConfig> config;
	private static final Random RANDOM = new Random();
	private static final Map<String, LongOpenHashSet> COUNTED = new HashMap<>();

	private QuestManager() {
	}

	static void init(ConfigHandle<QuestConfig> handle) {
		config = handle;
		PlayerBlockBreakEvents.AFTER.register(QuestManager::onBlockBroken);
		ServerLivingEntityEvents.AFTER_DEATH.register(QuestManager::onDeath);
		GuildEvents.WAVE_ENDED.add(QuestManager::onWaveEnded);
		GuildEvents.DISBANDED.add(guild -> {
			for (QuestInstance quest : guild.quests.active) {
				COUNTED.remove(quest.id);
			}
		});
		Lifecycle.STARTED.add(server -> {
			for (Guild guild : GuildManager.all()) {
				guild.quests.normalize();
			}
		});
		Lifecycle.STOPPING.add(server -> COUNTED.clear());
		// Generation, expiry and exploration: every second, each guild handled once every 5 seconds.
		Scheduler.every("quest.tick", 20, 17, QuestManager::tick);
	}

	public static QuestConfig config() {
		return config.get();
	}

	/** Overworld game time (monotonic, used for quest timers). */
	public static long now() {
		MinecraftServer server = GuildyServer.server();
		return server == null ? 0L : server.overworld().getGameTime();
	}

	public static boolean hasBoard(Guild guild) {
		return StructureManager.isBuilt(guild, BOARD);
	}

	private static long refreshDelayTicks() {
		return config.get().refreshDelaySeconds * 20L;
	}

	// ================================================================ periodic work

	private static void tick() {
		long slice = (Scheduler.ticks() / 20) % TICK_SLICES;
		for (Guild guild : List.copyOf(GuildManager.all())) {
			if (Math.floorMod(guild.id.hashCode(), TICK_SLICES) != slice) {
				continue;
			}
			Safe.run("quest.tick", () -> {
				expire(guild);
				checkExploration(guild);
				fillSlots(guild, false, true);
			});
		}
	}

	private static void expire(Guild guild) {
		long now = now();
		for (QuestInstance quest : List.copyOf(guild.quests.active)) {
			if (quest.isActive() && quest.expiresAt > 0 && now >= quest.expiresAt) {
				guild.quests.active.remove(quest);
				guild.quests.setReadyAt(quest.slot, now + refreshDelayTicks());
				guild.quests.expiredTotal++;
				COUNTED.remove(quest.id);
				GuildManager.markDirty();
				StructureManager.requestSignRefresh(guild);
				Msg.guild(guild, "quest.expired", quest.name);
			}
		}
	}

	private static void checkExploration(Guild guild) {
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			return;
		}
		int radius = config.get().exploreRadius;
		long radiusSq = (long) radius * radius;
		for (QuestInstance quest : List.copyOf(guild.quests.active)) {
			if (quest.type != QuestType.EXPLORE || !quest.isActive()) {
				continue;
			}
			for (ServerPlayer player : guild.onlineMembers()) {
				if (player.level() != level || player.isSpectator()) {
					continue;
				}
				double dx = player.getX() - (quest.targetX + 0.5);
				double dz = player.getZ() - (quest.targetZ + 0.5);
				if (dx * dx + dz * dz <= radiusSq) {
					addProgress(guild, quest, 1, player);
					break;
				}
			}
		}
	}

	/**
	 * Fills the empty slots of a guild that has a quest board.
	 *
	 * @param immediate ignore the per-slot refresh delay (admin reroll)
	 * @param announce  tell online members about each new quest
	 * @return number of quests created
	 */
	public static int fillSlots(Guild guild, boolean immediate, boolean announce) {
		if (!hasBoard(guild)) {
			return 0;
		}
		long now = now();
		int created = 0;
		for (int slot = 0; slot < GuildQuestData.SLOTS; slot++) {
			if (guild.quests.bySlot(slot) != null || (!immediate && now < guild.quests.readyAt(slot))) {
				continue;
			}
			QuestInstance quest = generate(guild, slot, now);
			if (quest == null) {
				break;
			}
			created++;
			if (announce && config.get().announceNewQuests) {
				Msg.guild(guild, "quest.new", quest.name);
			}
		}
		if (created > 0) {
			GuildManager.markDirty();
			StructureManager.requestSignRefresh(guild);
		}
		return created;
	}

	private static @Nullable QuestInstance generate(Guild guild, int slot, long now) {
		Set<String> exclude = new HashSet<>();
		for (QuestInstance quest : guild.quests.active) {
			exclude.add(quest.template);
		}
		for (int attempt = 0; attempt < 3; attempt++) {
			QuestGenerator.Rolled rolled = QuestGenerator.generate(config.get().usable().values(), guild.level, exclude,
					guild.heart != null, RANDOM);
			if (rolled == null) {
				return null;
			}
			int[] point = null;
			if (rolled.template().type == QuestType.EXPLORE) {
				point = randomPoint(guild, config.get().exploreMinDistance, config.get().exploreMaxDistance, false);
				if (point == null) {
					// No reachable destination (tiny world border...): try another template.
					exclude.add(rolled.template().id);
					continue;
				}
			}
			return create(guild, slot, now, rolled, point);
		}
		return null;
	}

	private static QuestInstance create(Guild guild, int slot, long now, QuestGenerator.Rolled rolled, int @Nullable [] point) {
		QuestTemplate template = rolled.template();
		QuestInstance quest = new QuestInstance();
		quest.id = UUID.randomUUID().toString();
		quest.template = template.id;
		quest.slot = slot;
		quest.type = template.type;
		quest.name = template.name;
		quest.description = template.description;
		quest.target = template.target;
		quest.targetName = template.targetName;
		quest.icon = template.icon;
		quest.amount = rolled.amount();
		quest.createdAt = now;
		quest.expiresAt = now + rolled.durationTicks();
		quest.rewardGold = rolled.gold();
		quest.rewardXp = rolled.xp();
		quest.rewardItems.clear();
		for (RewardStack stack : rolled.items()) {
			if (ItemUtil.item(stack.item) != null) {
				quest.rewardItems.add(stack);
			}
		}
		quest.rewardFlags = rolled.flags();
		quest.rewardMap = rolled.treasureMap();
		if (point != null) {
			quest.targetX = point[0];
			quest.targetZ = point[1];
		}
		guild.quests.active.add(quest);
		return quest;
	}

	/**
	 * Random point at {@code min..max} blocks from the heart (or home), inside the world border.
	 *
	 * @param unclaimed also require a chunk claimed by no guild
	 */
	static int @Nullable [] randomPoint(Guild guild, int min, int max, boolean unclaimed) {
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			return null;
		}
		BlockPos center = guild.heart != null ? guild.heart : guild.home != null ? guild.home : level.getRespawnData().pos();
		int[] fallback = null;
		for (int attempt = 0; attempt < 16; attempt++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			double distance = min + RANDOM.nextDouble() * Math.max(0, max - min);
			int x = center.getX() + (int) Math.round(Math.cos(angle) * distance);
			int z = center.getZ() + (int) Math.round(Math.sin(angle) * distance);
			if (!level.getWorldBorder().isWithinBounds(x, z)) {
				continue;
			}
			if (fallback == null) {
				fallback = new int[] {x, z};
			}
			if (!unclaimed || !GuildManager.isClaimed(dev.guildy.data.ChunkKey.ofBlock(x, z))) {
				return new int[] {x, z};
			}
		}
		return fallback;
	}

	// ================================================================ progress

	/** Adds progress to an active quest (capped), completes it when the goal is reached. */
	public static void addProgress(Guild guild, QuestInstance quest, int amount, @Nullable ServerPlayer by) {
		if (!quest.isActive() || amount <= 0 || !guild.quests.active.contains(quest)) {
			return;
		}
		int added = Math.min(amount, quest.remaining());
		if (added <= 0) {
			return;
		}
		quest.progress += added;
		if (by != null) {
			quest.contributions.merge(by.getName().getString(), added, Integer::sum);
			Msg.actionBar(by, Lang.get("quest.progress.actionbar", quest.name, quest.progress, quest.amount));
		}
		GuildManager.markDirty();
		StructureManager.requestSignRefresh(guild);
		if (quest.progress >= quest.amount) {
			complete(guild, quest);
		}
	}

	static void complete(Guild guild, QuestInstance quest) {
		quest.progress = quest.amount;
		quest.state = QuestState.COMPLETED;
		quest.completedAt = now();
		COUNTED.remove(quest.id);
		GuildManager.markDirty();
		StructureManager.requestSignRefresh(guild);
		Msg.guild(guild, "quest.completed", quest.name);
		Msg.guildTitle(guild, Lang.get("quest.completed.title"), Lang.get("quest.completed.subtitle", quest.name));
		Msg.guildSound(guild, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.0f);
	}

	private static void onBlockBroken(Level level, Player p, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		if (level.isClientSide() || !(p instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) {
			return;
		}
		Safe.run("quest.gather", () -> {
			Guild guild = GuildManager.of(player);
			if (guild == null || guild.quests.active.isEmpty()) {
				return;
			}
			for (QuestInstance quest : List.copyOf(guild.quests.active)) {
				if (quest.type != QuestType.GATHER || !quest.isActive()) {
					continue;
				}
				QuestTarget target = QuestTarget.get(quest.type, quest.target);
				if (target != null && target.matches(state) && countOnce(quest, level, pos)) {
					addProgress(guild, quest, 1, player);
				}
			}
		});
	}

	/** A position counts once per quest: placing and breaking the same block again does not farm progress. */
	private static boolean countOnce(QuestInstance quest, Level level, BlockPos pos) {
		LongOpenHashSet set = COUNTED.computeIfAbsent(quest.id, k -> new LongOpenHashSet());
		if (set.size() >= MAX_COUNTED_POSITIONS) {
			set.clear();
		}
		return set.add(pos.asLong() * 31L + level.dimension().hashCode());
	}

	private static void onDeath(LivingEntity entity, DamageSource source) {
		if (entity.level().isClientSide()) {
			return;
		}
		Safe.run("quest.kill", () -> {
			ServerPlayer killer = killer(entity, source);
			if (killer == null || killer.isCreative() || killer == entity) {
				return;
			}
			Guild guild = GuildManager.of(killer);
			if (guild == null || guild.quests.active.isEmpty()) {
				return;
			}
			for (QuestInstance quest : List.copyOf(guild.quests.active)) {
				if (quest.type != QuestType.KILL || !quest.isActive()) {
					continue;
				}
				QuestTarget target = QuestTarget.get(quest.type, quest.target);
				if (target != null && target.matches(entity)) {
					addProgress(guild, quest, 1, killer);
				}
			}
		});
	}

	/** Player credited for a kill: direct attacker, projectile owner, or last player who hurt the mob. */
	private static @Nullable ServerPlayer killer(LivingEntity entity, DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer player) {
			return player;
		}
		if (source.getDirectEntity() instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) {
			return player;
		}
		if (entity.getLastHurtByPlayer() instanceof ServerPlayer player) {
			return player;
		}
		return null;
	}

	private static void onWaveEnded(GuildEvents.WaveOutcome outcome) {
		if (outcome.result() != GuildEvents.WaveOutcome.Result.VICTORY || outcome.guild() == null) {
			return;
		}
		Guild guild = outcome.guild();
		for (QuestInstance quest : List.copyOf(guild.quests.active)) {
			if (quest.type == QuestType.SURVIVE && quest.isActive()) {
				addProgress(guild, quest, 1, null);
			}
		}
	}

	// ================================================================ player actions (menus)

	/** Number of plain items matching a DELIVER quest in the player's inventory. */
	public static int deliverable(ServerPlayer player, QuestInstance quest) {
		QuestTarget target = QuestTarget.get(quest.type, quest.target);
		return target == null || quest.type != QuestType.DELIVER ? 0 : PlainItems.count(player, target::matches);
	}

	/** Delivers matching items (partial deliveries allowed). @return the error, or null on success. */
	public static @Nullable Component deliver(ServerPlayer player, Guild guild, QuestInstance quest) {
		if (player.isCreative()) {
			return Lang.get("quest.deliver.creative");
		}
		if (!guild.isMember(player.getUUID()) || !guild.quests.active.contains(quest) || !quest.isActive()
				|| quest.type != QuestType.DELIVER) {
			return Lang.get("quest.deliver.unavailable");
		}
		QuestTarget target = QuestTarget.get(quest.type, quest.target);
		if (target == null) {
			return Lang.get("quest.deliver.unavailable");
		}
		int count = Math.min(quest.remaining(), PlainItems.count(player, target::matches));
		if (count <= 0) {
			return TextUtil.lang("quest.deliver.none", target.name(quest.targetName));
		}
		// Take the items first; progress is only added for what was actually removed.
		if (!PlainItems.remove(player, target::matches, count)) {
			return Lang.get("quest.deliver.unavailable");
		}
		Msg.sendRaw(player, TextUtil.lang("quest.deliver.done", count, target.name(quest.targetName), quest.name));
		addProgress(guild, quest, count, player);
		return null;
	}

	/** Claims the reward of a completed quest. @return the error, or null on success. */
	public static @Nullable Component claim(ServerPlayer player, Guild guild, QuestInstance quest) {
		if (!guild.isMember(player.getUUID()) || !guild.quests.active.contains(quest)) {
			return Lang.get("quest.claim.gone");
		}
		if (!quest.isCompleted()) {
			return Lang.get("quest.claim.not_done");
		}
		// Take the quest off the board first: a second click (or another member) finds nothing left to claim.
		guild.quests.active.remove(quest);
		guild.quests.setReadyAt(quest.slot, now() + refreshDelayTicks());
		guild.quests.completedTotal++;
		GuildManager.markDirty();

		long gold = rewardGold(guild, quest);
		long xp = rewardXp(guild, quest);
		GuildManager.addTreasury(guild, gold);
		for (RewardStack stack : quest.rewardItems) {
			Item item = ItemUtil.item(stack.item);
			if (item != null) {
				ItemUtil.giveOrDrop(player, item, stack.count);
			}
		}
		for (int i = 0; i < quest.rewardFlags; i++) {
			ItemUtil.giveOrDrop(player, GuildyItems.extensionFlag(guild));
		}
		if (quest.rewardMap) {
			TreasureMaps.giveMap(player, guild);
		}
		Msg.guildRaw(guild, TextUtil.lang("quest.claimed", player.getName().getString(), quest.name, gold, xp));
		Msg.sound(player, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.2f);
		StructureManager.requestSignRefresh(guild);
		// Last: may trigger level-up announcements.
		GuildManager.addXp(guild, xp);
		return null;
	}

	/** Treasury gold paid by the quest, with the guild's upgrade bonus (never for deliveries: no shop gold loop). */
	public static long rewardGold(Guild guild, QuestInstance quest) {
		return UpgradeMath.questGold(quest.rewardGold, Upgrades.value(guild, UpgradeEffects.QUEST_REWARD_PCT), quest.type == QuestType.DELIVER);
	}

	/** Guild XP of the quest, with the guild's upgrade bonus. */
	public static long rewardXp(Guild guild, QuestInstance quest) {
		return Upgrades.boost(guild, UpgradeEffects.QUEST_REWARD_PCT, (long) quest.rewardXp);
	}

	/** Abandons an active quest (QUESTS permission); its slot gets a new quest after the refresh delay. */
	public static @Nullable Component abandon(ServerPlayer player, Guild guild, QuestInstance quest) {
		if (!guild.can(player.getUUID(), GuildPermission.QUESTS)) {
			return Lang.get("error.no_permission");
		}
		if (!guild.quests.active.contains(quest)) {
			return Lang.get("quest.claim.gone");
		}
		if (!quest.isActive()) {
			return Lang.get("quest.abandon.completed");
		}
		guild.quests.active.remove(quest);
		guild.quests.setReadyAt(quest.slot, now() + refreshDelayTicks());
		guild.quests.abandonedTotal++;
		COUNTED.remove(quest.id);
		GuildManager.markDirty();
		StructureManager.requestSignRefresh(guild);
		Msg.guild(guild, "quest.abandoned", player.getName().getString(), quest.name);
		return null;
	}

	// ================================================================ admin

	/** Replaces every active (not completed) quest. @return number of quests removed. */
	public static int reroll(Guild guild) {
		int removed = 0;
		for (QuestInstance quest : List.copyOf(guild.quests.active)) {
			if (quest.isActive()) {
				guild.quests.active.remove(quest);
				guild.quests.setReadyAt(quest.slot, 0L);
				COUNTED.remove(quest.id);
				removed++;
			}
		}
		GuildManager.markDirty();
		fillSlots(guild, true, false);
		StructureManager.requestSignRefresh(guild);
		return removed;
	}

	/** Completes the quest of a slot (testing). @return false when the slot has no active quest. */
	public static boolean forceComplete(Guild guild, int slot) {
		QuestInstance quest = guild.quests.bySlot(slot);
		if (quest == null || !quest.isActive()) {
			return false;
		}
		complete(guild, quest);
		return true;
	}
}
