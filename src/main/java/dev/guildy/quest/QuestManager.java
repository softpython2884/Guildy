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
import dev.guildy.core.ScrollDrops;
import dev.guildy.core.UniqueDrops;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildEvents;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import dev.guildy.ruin.CursedItems;
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
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntBiFunction;

/**
 * Quest lifecycle: generation on the quest board (with the occasional legendary contract), progress (block breaks, kills,
 * deliveries, wave victories, exploration, biomes, and the crafting / smelting / fishing / breeding / taming / trading /
 * enchanting hooks of {@link QuestHooks}), completion, rewards (unique weapons included), expiry.
 */
public final class QuestManager {
	public static final String BOARD = "quest_board";
	/** Unique item sources ({@link UniqueDrops#roll}). */
	public static final String UNIQUE_SOURCE = "quest";
	public static final String UNIQUE_SOURCE_LEGENDARY = "legendary_quest";
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
			checkBiomes(server);
		});
		Lifecycle.RELOADED.add(() -> {
			MinecraftServer server = GuildyServer.server();
			if (server != null) {
				checkBiomes(server);
			}
		});
		Lifecycle.STOPPING.add(server -> COUNTED.clear());
		// Generation, expiry, exploration and biomes: every second, each guild handled once every 5 seconds.
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

	private static void checkBiomes(MinecraftServer server) {
		Safe.run("quest.biomes", () -> config.get().checkBiomes(server.registryAccess()));
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
				checkBiomes(guild);
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

	/** Members (not in creative / spectator mode) able to make a periodic check progress. */
	private static boolean canProgress(ServerPlayer player) {
		return !player.isSpectator() && !player.isCreative() && player.isAlive();
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
				if (player.level() != level || !canProgress(player)) {
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

	/** BIOME quests: the biome at each online member's feet (cheap lookup in a loaded chunk, once every 5 seconds). */
	private static void checkBiomes(Guild guild) {
		List<QuestInstance> quests = null;
		for (QuestInstance quest : guild.quests.active) {
			if (quest.type == QuestType.BIOME && quest.isActive()) {
				if (quests == null) {
					quests = new ArrayList<>(2);
				}
				quests.add(quest);
			}
		}
		if (quests == null) {
			return;
		}
		for (ServerPlayer player : guild.onlineMembers()) {
			if (!canProgress(player)) {
				continue;
			}
			Holder<Biome> biome = player.level().getBiome(player.blockPosition());
			for (QuestInstance quest : quests) {
				QuestTarget target = QuestTarget.get(QuestType.BIOME, quest.target);
				if (quest.isActive() && target != null && target.matches(biome)) {
					addProgress(guild, quest, 1, player);
				}
			}
		}
	}

	/**
	 * Fills the empty slots of a guild that has a quest board.
	 *
	 * @param immediate ignore the per-slot refresh delay (admin reroll)
	 * @param announce  tell online members about each new quest (legendary contracts are always announced)
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
			QuestInstance quest = generate(guild, slot, now, false);
			if (quest == null) {
				break;
			}
			created++;
			if (quest.legendary) {
				announceLegendary(guild, quest);
			} else if (announce && config.get().announceNewQuests) {
				Msg.guild(guild, "quest.new", quest.name);
			}
		}
		if (created > 0) {
			GuildManager.markDirty();
			StructureManager.requestSignRefresh(guild);
		}
		return created;
	}

	private static void announceLegendary(Guild guild, QuestInstance quest) {
		Msg.guild(guild, "quest.new.legendary", quest.name);
		Msg.guildTitle(guild, Lang.get("quest.new.legendary.title"), Lang.get("quest.new.legendary.subtitle", quest.name));
		Msg.guildSound(guild, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.2f);
	}

	/**
	 * Draws a quest for a slot: sometimes a legendary contract (level, cooldown, one at a time), else a normal quest.
	 *
	 * @param forceLegendary admin: legendary contract regardless of level, chance and cooldown
	 */
	private static @Nullable QuestInstance generate(Guild guild, int slot, long now, boolean forceLegendary) {
		QuestConfig cfg = config.get();
		Set<String> exclude = new HashSet<>();
		for (QuestInstance quest : guild.quests.active) {
			exclude.add(quest.template);
		}
		boolean legendary = forceLegendary || QuestGenerator.legendaryTurn(cfg.legendary.enabled, guild.level, cfg.legendary.minLevel,
				guild.quests.hasLegendary(), now, guild.quests.lastLegendaryAt, cfg.legendary.cooldownTicks(), cfg.legendary.chance, RANDOM);
		for (int attempt = 0; attempt < 4; attempt++) {
			Collection<QuestTemplate> pool = legendary ? QuestGenerator.legendaryCandidates(cfg.usable().values()) : cfg.usable().values();
			QuestGenerator.Rolled rolled = QuestGenerator.generate(pool, guild.level, cfg.scalingLevel(guild.level), exclude, guild.heart != null, RANDOM);
			if (rolled == null) {
				if (legendary && !forceLegendary) {
					legendary = false; // no template can become legendary at this level: a normal quest instead
					continue;
				}
				return null;
			}
			if (legendary) {
				rolled = QuestGenerator.legendary(rolled, cfg.legendary.multipliers());
			}
			int[] point = null;
			if (rolled.template().type == QuestType.EXPLORE) {
				point = randomPoint(guild, cfg.exploreMinDistance, cfg.exploreMaxDistance, false);
				if (point == null) {
					// No reachable destination (tiny world border...): try another template.
					exclude.add(rolled.template().id);
					continue;
				}
			}
			QuestInstance quest = create(guild, slot, now, rolled, point);
			if (quest.legendary) {
				guild.quests.lastLegendaryAt = Math.max(1L, now);
			}
			return quest;
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
		quest.target = template.target == null ? "" : template.target.trim();
		quest.targetName = template.targetName;
		quest.icon = template.icon;
		quest.amount = rolled.amount();
		quest.legendary = rolled.legendary();
		quest.parts = new ArrayList<>();
		for (QuestPart part : rolled.parts()) {
			quest.parts.add(part.copy());
		}
		quest.syncParts();
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

	/** Adds progress to an active quest (capped), completes it when the goal is reached. @return the amount added */
	public static int addProgress(Guild guild, QuestInstance quest, int amount, @Nullable ServerPlayer by) {
		if (!quest.isActive() || amount <= 0 || !guild.quests.active.contains(quest)) {
			return 0;
		}
		int added = quest.addProgress(amount);
		if (added > 0) {
			afterProgress(guild, quest, added, by);
		}
		return added;
	}

	/** COLLECTION: adds progress to one item of the assortment (capped). @return the amount added */
	public static int addPartProgress(Guild guild, QuestInstance quest, int part, int amount, @Nullable ServerPlayer by) {
		if (!quest.isActive() || amount <= 0 || !guild.quests.active.contains(quest)) {
			return 0;
		}
		int added = quest.addPartProgress(part, amount);
		if (added > 0) {
			afterProgress(guild, quest, added, by);
		}
		return added;
	}

	private static void afterProgress(Guild guild, QuestInstance quest, int added, @Nullable ServerPlayer by) {
		if (by != null) {
			quest.contributions.merge(by.getName().getString(), added, Integer::sum);
			Msg.actionBar(by, Lang.get("quest.progress.actionbar", quest.name, quest.progress, quest.amount));
		}
		GuildManager.markDirty();
		StructureManager.requestSignRefresh(guild);
		if (quest.isGoalReached()) {
			complete(guild, quest);
		}
	}

	static void complete(Guild guild, QuestInstance quest) {
		quest.markCompleted(now());
		COUNTED.remove(quest.id);
		GuildManager.markDirty();
		StructureManager.requestSignRefresh(guild);
		if (quest.legendary) {
			Msg.guild(guild, "quest.completed.legendary", quest.name);
			Msg.guildTitle(guild, Lang.get("quest.completed.legendary.title"), Lang.get("quest.completed.subtitle", quest.name));
		} else {
			Msg.guild(guild, "quest.completed", quest.name);
			Msg.guildTitle(guild, Lang.get("quest.completed.title"), Lang.get("quest.completed.subtitle", quest.name));
		}
		Msg.guildSound(guild, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.0f);
	}

	/** Cheap pre-check of the hooks: a member (not in creative / spectator mode) whose guild has an active quest of this type. */
	static boolean wants(ServerPlayer player, QuestType type) {
		if (player.isCreative() || player.isSpectator()) {
			return false;
		}
		Guild guild = GuildManager.of(player);
		if (guild == null) {
			return false;
		}
		for (QuestInstance quest : guild.quests.active) {
			if (quest.type == type && quest.isActive()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Progress of the active quests of {@code type} for an action of a member (never in creative / spectator mode).
	 *
	 * @param amount (quest, its target or null when the quest has none) → progress to add (0 = no match)
	 */
	static void onAction(ServerPlayer player, QuestType type, ToIntBiFunction<QuestInstance, @Nullable QuestTarget> amount) {
		if (player.isCreative() || player.isSpectator()) {
			return;
		}
		Guild guild = GuildManager.of(player);
		if (guild == null || guild.quests.active.isEmpty()) {
			return;
		}
		for (QuestInstance quest : List.copyOf(guild.quests.active)) {
			if (quest.type != type || !quest.isActive()) {
				continue;
			}
			QuestTarget target = null;
			if (!quest.target.isBlank()) {
				target = QuestTarget.get(type, quest.target);
				if (target == null) {
					continue; // invalid target: never progresses (no accidental "anything")
				}
			}
			int value = amount.applyAsInt(quest, target);
			if (value > 0) {
				addProgress(guild, quest, value, player);
			}
		}
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

	/** Kills credited to a member: KILL, NIGHT_HUNT (monsters killed at night), ELITE / BOSS (horde elites / bosses). */
	private static void onDeath(LivingEntity entity, DamageSource source) {
		if (entity.level().isClientSide()) {
			return;
		}
		Safe.run("quest.kill", () -> {
			ServerPlayer killer = killer(entity, source);
			if (killer == null || killer.isCreative() || killer.isSpectator() || killer == entity) {
				return;
			}
			Guild guild = GuildManager.of(killer);
			if (guild == null || guild.quests.active.isEmpty()) {
				return;
			}
			HordeMobs.Rank rank = null;
			for (QuestInstance quest : List.copyOf(guild.quests.active)) {
				if (!quest.isActive()) {
					continue;
				}
				boolean counts = false;
				if (quest.type == QuestType.KILL) {
					QuestTarget target = QuestTarget.get(quest.type, quest.target);
					counts = target != null && target.matches(entity);
				} else if (quest.type == QuestType.NIGHT_HUNT) {
					counts = entity.level().isDarkOutside() && nightPrey(quest, entity);
				} else if (quest.type == QuestType.ELITE || quest.type == QuestType.BOSS) {
					if (rank == null) {
						rank = HordeMobs.rank(entity);
					}
					counts = rank == (quest.type == QuestType.ELITE ? HordeMobs.Rank.ELITE : HordeMobs.Rank.BOSS);
				}
				if (counts) {
					addProgress(guild, quest, 1, killer);
				}
			}
		});
	}

	/** NIGHT_HUNT: any monster without a target, else the target entity. */
	private static boolean nightPrey(QuestInstance quest, LivingEntity entity) {
		if (quest.target.isBlank()) {
			return entity instanceof Enemy;
		}
		QuestTarget target = QuestTarget.get(quest.type, quest.target);
		return target != null && target.matches(entity);
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

	/** Number of plain items matching one item of a COLLECTION quest in the player's inventory. */
	public static int deliverable(ServerPlayer player, QuestInstance quest, int part) {
		if (part < 0 || part >= quest.parts.size()) {
			return 0;
		}
		QuestTarget target = QuestTarget.get(QuestType.TargetKind.ITEM, quest.parts.get(part).target);
		return target == null ? 0 : PlainItems.count(player, target::matches);
	}

	/** Delivers matching items (partial deliveries allowed; every missing item of an assortment). @return the error, or null */
	public static @Nullable Component deliver(ServerPlayer player, Guild guild, QuestInstance quest) {
		return deliver(player, guild, quest, -1);
	}

	/**
	 * Delivers matching plain items from the player's inventory (partial deliveries allowed).
	 *
	 * @param part COLLECTION: index of the only item to deliver, -1 = every item still missing
	 * @return the error, or null on success
	 */
	public static @Nullable Component deliver(ServerPlayer player, Guild guild, QuestInstance quest, int part) {
		if (player.isCreative()) {
			return Lang.get("quest.deliver.creative");
		}
		if (!guild.isMember(player.getUUID()) || !guild.quests.active.contains(quest) || !quest.isActive() || !quest.type.isDelivery()) {
			return Lang.get("quest.deliver.unavailable");
		}
		if (quest.type == QuestType.COLLECTION) {
			return deliverCollection(player, guild, quest, part);
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

	private static @Nullable Component deliverCollection(ServerPlayer player, Guild guild, QuestInstance quest, int only) {
		if (!quest.hasParts() || only >= quest.parts.size()) {
			return Lang.get("quest.deliver.unavailable");
		}
		int delivered = 0;
		for (int i = 0; i < quest.parts.size() && quest.isActive(); i++) {
			if (only >= 0 && i != only) {
				continue;
			}
			QuestPart part = quest.parts.get(i);
			QuestTarget target = QuestTarget.get(QuestType.TargetKind.ITEM, part.target);
			if (part.isDone() || target == null) {
				continue;
			}
			int count = Math.min(part.remaining(), PlainItems.count(player, target::matches));
			if (count <= 0 || !PlainItems.remove(player, target::matches, count)) {
				continue;
			}
			Msg.sendRaw(player, TextUtil.lang("quest.deliver.done", count, target.name(part.targetName), quest.name));
			delivered += addPartProgress(guild, quest, i, count, player);
		}
		if (delivered > 0) {
			return null;
		}
		if (only >= 0) {
			QuestPart part = quest.parts.get(only);
			QuestTarget target = QuestTarget.get(QuestType.TargetKind.ITEM, part.target);
			return target == null ? Lang.get("quest.deliver.unavailable") : TextUtil.lang("quest.deliver.none", target.name(part.targetName));
		}
		return Lang.get("quest.deliver.none_collection");
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
		if (quest.legendary) {
			guild.quests.legendaryCompleted++;
		}
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
		giveUnique(player, guild, quest);
		StructureManager.requestSignRefresh(guild);
		// Last: may trigger level-up announcements.
		GuildManager.addXp(guild, xp);
		return null;
	}

	/**
	 * Unique weapon of the reward, drawn now (never at generation: abandoning quests cannot fish for one): always tried for a
	 * legendary contract (gold instead when none is available), with {@code uniqueChance} for the others.
	 */
	private static void giveUnique(ServerPlayer player, Guild guild, QuestInstance quest) {
		QuestConfig cfg = config.get();
		ItemStack unique = ItemStack.EMPTY;
		if (quest.legendary) {
			unique = UniqueDrops.roll(UNIQUE_SOURCE_LEGENDARY, UniqueDrops.Recipient.of(player, guild));
		} else if (cfg.uniqueChance > 0 && RANDOM.nextDouble() < cfg.uniqueChance) {
			unique = UniqueDrops.roll(UNIQUE_SOURCE, UniqueDrops.Recipient.of(player, guild));
		}
		if (!unique.isEmpty()) {
			Component name = unique.getDisplayName();
			ItemUtil.giveOrDrop(player, unique);
			Msg.guildRaw(guild, TextUtil.lang(UniqueDrops.isUnique(unique) ? "quest.claimed.unique" : "scroll.reward.quest", player.getName().getString(), name));
			Msg.guildSound(guild, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.4f);
		} else if (quest.legendary && cfg.legendary.fallbackGold > 0) {
			GuildManager.addTreasury(guild, cfg.legendary.fallbackGold);
			Msg.guild(guild, "quest.claimed.unique_none", cfg.legendary.fallbackGold);
		}
		// Spell scrolls (scrolls.json5, same "legendary_quest" / "quest" sources), also drawn at the reward.
		ItemStack scroll = ScrollDrops.roll(quest.legendary ? UNIQUE_SOURCE_LEGENDARY : UNIQUE_SOURCE);
		if (!scroll.isEmpty()) {
			Component scrollName = scroll.getDisplayName();
			ItemUtil.giveOrDrop(player, scroll);
			Msg.guildRaw(guild, TextUtil.lang("scroll.reward.quest", player.getName().getString(), scrollName));
		}
		// Legendary contracts: sometimes a cursed incantation scroll (ruins.json5, cursed.sources.legendaryQuestChance).
		ItemStack cursed = quest.legendary ? CursedItems.rollLegendaryContract() : ItemStack.EMPTY;
		if (!cursed.isEmpty()) {
			Component cursedName = cursed.getDisplayName();
			ItemUtil.giveOrDrop(player, cursed);
			Msg.guildRaw(guild, TextUtil.lang("ruin.cursed.reward.quest", player.getName().getString(), cursedName));
		}
	}

	/** Treasury gold paid by the quest, with the guild's upgrade bonus (never for deliveries: no shop gold loop). */
	public static long rewardGold(Guild guild, QuestInstance quest) {
		return UpgradeMath.questGold(quest.rewardGold, Upgrades.value(guild, UpgradeEffects.QUEST_REWARD_PCT), quest.type.isDelivery());
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

	/**
	 * Admin: puts a legendary contract on a slot now (replacing its quest), regardless of level, chance and cooldown.
	 *
	 * @return the new contract, or null when no template can become legendary (the slot is left unchanged)
	 */
	public static @Nullable QuestInstance forceLegendary(Guild guild, int slot) {
		QuestInstance previous = guild.quests.bySlot(slot);
		if (previous != null) {
			guild.quests.active.remove(previous);
		}
		QuestInstance quest = generate(guild, slot, now(), true);
		if (quest == null) {
			if (previous != null) {
				guild.quests.active.add(previous);
			}
			return null;
		}
		if (previous != null) {
			COUNTED.remove(previous.id);
		}
		GuildManager.markDirty();
		StructureManager.requestSignRefresh(guild);
		announceLegendary(guild, quest);
		return quest;
	}
}
