package dev.guildy.trade;

import dev.guildy.Guildy;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildEvents;
import dev.guildy.data.GuildManager;
import dev.guildy.ruin.IncantationItems;
import dev.guildy.structure.StructureManager;
import dev.guildy.wave.WaveApi;
import dev.guildy.wave.WaveFormula;
import dev.guildy.wave.WaveModule;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.equine.TraderLlama;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Merchants visiting the guild bases: tagged vanilla wandering traders (+ trader llamas) spawned with Guildy offers (set
 * before any vanilla trade generation), recorded in {@link GuildTradeData#visitors}, sent away when their stay ends (at
 * nightfall at the latest) and discarded when found without record (restart safety). Visits won in the morning wait in
 * {@link GuildTradeData#pending} until the arrival area is loaded (entity-ticking), and are dropped at nightfall.
 * Work is bounded: a few spawns per second, only loaded chunks.
 */
public final class Merchants {
	/** Tag carried by every visiting trader and llama. */
	public static final String TAG = "guildy_merchant";
	private static final int MAX_SPAWNS_PER_SECOND = 2;
	/** Vanilla despawn delay kept above the remaining stay: the visit always ends through {@link #leave}. */
	private static final int DESPAWN_MARGIN_TICKS = 1400;
	/** A merchant whose stay is over waits up to this long for the trade in progress to end. */
	private static final int TRADING_GRACE_TICKS = 1200;
	private static final SplittableRandom RANDOM = new SplittableRandom();
	private static final Map<String, Kind> KINDS = new LinkedHashMap<>();
	/** trader / llama uuid -> guild id. */
	private static final Map<UUID, UUID> INDEX = new HashMap<>();
	private static @Nullable Field offersField;
	private static boolean offersFieldFailed;

	/** A kind of visitor: the trade hub merchant (trade module), the caravan and the scholar (event module). */
	public interface Kind {
		String id();

		/** Trader name ('&' codes). */
		String name(Guild guild);

		/** Offers of one visit. */
		List<TradeEntry> offers(Guild guild, RandomGenerator random);

		/** Arrival point (trade hub, heart...), null when there is none. */
		@Nullable BlockPos target(Guild guild);

		int staySeconds();

		int llamas();

		/** Lang keys of the arrival: {prefix}.title, {prefix}.subtitle ({0} = minutes), {prefix}.chat ({0}..{2} = x y z, {3} = minutes). */
		String langPrefix();

		/** False when a pending visit must be dropped (e.g. the trade hub was destroyed). */
		default boolean stillWanted(Guild guild) {
			return true;
		}
	}

	public enum Result {
		SPAWNED, UNKNOWN_KIND, NO_TARGET, NOT_LOADED, NO_SPACE, NO_OFFERS, FAILED
	}

	private Merchants() {
	}

	static void init() {
		Scheduler.every("trade.merchants", 20, 9, Merchants::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> Safe.run("trade.load", () -> onEntityLoad(entity)));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> Safe.run("trade.unload", () -> onEntityUnload(entity)));
		Lifecycle.STARTED.add(server -> rebuildIndex());
		Lifecycle.STOPPING.add(server -> INDEX.clear());
		GuildEvents.DISBANDED.add(guild -> {
			ServerLevel level = GuildyServer.claimLevel();
			for (GuildTradeData.Visitor visitor : List.copyOf(guild.trade.visitors)) {
				leave(level, guild, visitor, false);
			}
			guild.trade.pending.clear();
		});
	}

	public static void register(Kind kind) {
		KINDS.put(kind.id(), kind);
	}

	public static @Nullable Kind kind(String id) {
		return KINDS.get(id);
	}

	// ================================================================== queries

	public static List<GuildTradeData.Visitor> visitors(Guild guild) {
		return List.copyOf(guild.trade.visitors);
	}

	public static GuildTradeData.@Nullable Visitor visitor(Guild guild, String kind) {
		return guild.trade.visitor(kind);
	}

	public static boolean isPending(Guild guild, String kind) {
		return guild.trade.pending.containsKey(kind);
	}

	/** Seconds before the visitor leaves (0 when no server). */
	public static long remainingSeconds(GuildTradeData.Visitor visitor) {
		ServerLevel level = GuildyServer.claimLevel();
		return level == null ? 0 : Math.max(0, (visitor.until - level.getGameTime()) / 20);
	}

	// ================================================================== scheduling & arrival

	/** A visit won for today: the merchant arrives as soon as its arrival area is loaded (dropped at nightfall). */
	public static void schedule(Guild guild, String kind, long day) {
		guild.trade.pending.put(kind, day);
		GuildManager.markDirty();
		StructureManager.requestSignRefresh(guild);
	}

	/**
	 * Makes a merchant arrive now.
	 *
	 * @param replace true (admin): a visitor of the same kind already there is sent away first
	 */
	public static Result spawnNow(Guild guild, String kindId, boolean replace) {
		Kind kind = KINDS.get(kindId);
		ServerLevel level = GuildyServer.claimLevel();
		if (kind == null || level == null) {
			return Result.UNKNOWN_KIND;
		}
		return spawn(level, guild, kind, replace);
	}

	private static Result spawn(ServerLevel level, Guild guild, Kind kind, boolean replace) {
		BlockPos target = kind.target(guild);
		if (target == null) {
			return Result.NO_TARGET;
		}
		if (!isTicking(level, target)) {
			return Result.NOT_LOADED;
		}
		BlockPos spot = findSpot(level, guild, target, EntityTypes.WANDERING_TRADER);
		if (spot == null) {
			return Result.NO_SPACE;
		}
		List<TradeEntry> entries = kind.offers(guild, RANDOM);
		MerchantOffers offers = TradeOffers.build(level.registryAccess(), entries);
		if (TradeHub.KIND.equals(kind.id())) {
			TradeOffers.addScrollOffer(offers, TradeHub.tier(guild));
			Safe.run("trade.incantation", () -> IncantationItems.addTradeOffer(offers, TradeHub.tier(guild)));
		}
		if (offers.isEmpty()) {
			return Result.NO_OFFERS;
		}
		GuildTradeData.Visitor previous = guild.trade.visitor(kind.id());
		if (previous != null) {
			if (!replace) {
				return Result.FAILED;
			}
			leave(level, guild, previous, false);
		}
		WanderingTrader trader = EntityTypes.WANDERING_TRADER.create(level, EntitySpawnReason.EVENT);
		if (trader == null) {
			return Result.FAILED;
		}
		long now = level.getGameTime();
		long until = now + kind.staySeconds() * 20L;
		TradeConfig.Visits cfg = TradeModule.config().visits;
		long clock = level.getOverworldClockTime();
		long timeOfDay = WaveFormula.timeOfDay(clock);
		int nightStart = WaveModule.config().timing.nightStart;
		if (cfg.leaveAtNight && timeOfDay < nightStart) {
			until = Math.min(until, now + (nightStart - timeOfDay));
		}
		until = Math.max(now + 20 * 30, until);
		String name = kind.name(guild);

		float yaw = RANDOM.nextFloat() * 360.0F;
		trader.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0.0F);
		trader.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
		setOffers(trader, offers);
		trader.setCustomName(Component.literal(Lang.colorize(name)));
		trader.setCustomNameVisible(true);
		trader.setDespawnDelay((int) Math.min(Integer.MAX_VALUE, until - now + DESPAWN_MARGIN_TICKS));
		trader.setWanderTarget(spot);
		trader.setHomeTo(spot, cfg.homeRadius);
		tag(trader, guild);

		GuildTradeData.Visitor visitor = new GuildTradeData.Visitor();
		visitor.kind = kind.id();
		visitor.trader = trader.getUUID();
		visitor.pos = spot.immutable();
		visitor.arrivedAt = now;
		visitor.until = until;
		visitor.name = name;
		// Recorded before being added: the load event must find it.
		INDEX.put(trader.getUUID(), guild.id);
		guild.trade.visitors.add(visitor);
		if (!level.addFreshEntity(trader)) {
			INDEX.remove(trader.getUUID());
			guild.trade.visitors.remove(visitor);
			return Result.FAILED;
		}
		long stayTicks = until - now;
		for (int i = 0; i < kind.llamas(); i++) {
			Safe.run("trade.llama", () -> spawnLlama(level, guild, visitor, trader, stayTicks));
		}
		guild.trade.visits++;
		GuildManager.markDirty();
		StructureManager.requestSignRefresh(guild);
		announceArrival(level, guild, kind, visitor);
		return Result.SPAWNED;
	}

	private static void spawnLlama(ServerLevel level, Guild guild, GuildTradeData.Visitor visitor, WanderingTrader trader, long stayTicks) {
		TraderLlama llama = EntityTypes.TRADER_LLAMA.create(level, EntitySpawnReason.EVENT);
		if (llama == null) {
			return;
		}
		BlockPos spot = findSpot(level, guild, visitor.pos, EntityTypes.TRADER_LLAMA);
		if (spot == null) {
			spot = visitor.pos;
		}
		llama.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, RANDOM.nextFloat() * 360.0F, 0.0F);
		llama.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
		llama.setDespawnDelay((int) Math.min(Integer.MAX_VALUE, stayTicks + DESPAWN_MARGIN_TICKS));
		tag(llama, guild);
		INDEX.put(llama.getUUID(), guild.id);
		visitor.llamas.add(llama.getUUID());
		if (level.addFreshEntity(llama)) {
			llama.setLeashedTo(trader, true);
		} else {
			INDEX.remove(llama.getUUID());
			visitor.llamas.remove(llama.getUUID());
		}
	}

	private static void tag(Entity entity, Guild guild) {
		entity.addTag(TAG);
		entity.addTag(TAG + "_" + guild.id);
	}

	private static void announceArrival(ServerLevel level, Guild guild, Kind kind, GuildTradeData.Visitor visitor) {
		long minutes = Math.max(1, Math.round((visitor.until - visitor.arrivedAt) / 1200.0));
		String prefix = kind.langPrefix();
		Component title = Lang.get(prefix + ".title");
		Component subtitle = Lang.get(prefix + ".subtitle", minutes);
		for (ServerPlayer member : guild.onlineMembers()) {
			Msg.title(member, title, subtitle, 10, 60, 20);
			Msg.sound(member, SoundEvents.BELL_BLOCK, 0.8F, 1.2F);
			Msg.send(member, prefix + ".chat", visitor.pos.getX(), visitor.pos.getY(), visitor.pos.getZ(), minutes);
		}
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, visitor.pos.getX() + 0.5, visitor.pos.getY() + 1.0, visitor.pos.getZ() + 0.5,
				15, 0.6, 0.8, 0.6, 0.0);
	}

	// ================================================================== departure

	/** Sends a visitor away (trader and llamas removed when loaded, otherwise when their chunk loads). */
	public static void leave(@Nullable ServerLevel level, Guild guild, GuildTradeData.Visitor visitor, boolean announce) {
		guild.trade.visitors.remove(visitor);
		INDEX.remove(visitor.trader);
		for (UUID llama : visitor.llamas) {
			INDEX.remove(llama);
		}
		GuildManager.markDirty();
		StructureManager.requestSignRefresh(guild);
		if (level != null) {
			for (UUID id : visitor.llamas) {
				if (level.getEntity(id) instanceof TraderLlama llama && !llama.isRemoved()) {
					llama.removeLeash();
					poof(level, llama);
					llama.discard();
				}
			}
			Entity trader = level.getEntity(visitor.trader);
			if (trader != null && !trader.isRemoved()) {
				poof(level, trader);
				trader.discard();
			}
		}
		if (announce) {
			Msg.guild(guild, "trade.visitor.leave", Lang.colorize(visitor.name));
		}
	}

	/** Sends away the visitor of this kind (admin). @return false when there is none */
	public static boolean dismiss(Guild guild, String kind) {
		GuildTradeData.Visitor visitor = guild.trade.visitor(kind);
		boolean pending = guild.trade.pending.remove(kind) != null;
		if (pending) {
			GuildManager.markDirty();
		}
		if (visitor == null) {
			return pending;
		}
		leave(GuildyServer.claimLevel(), guild, visitor, true);
		return true;
	}

	private static void poof(ServerLevel level, Entity entity) {
		level.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
	}

	// ================================================================== tick

	private static void tick() {
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			return;
		}
		long gameTime = level.getGameTime();
		long clock = level.getOverworldClockTime();
		long day = WaveFormula.dayIndex(clock);
		long timeOfDay = WaveFormula.timeOfDay(clock);
		int nightStart = WaveModule.config().timing.nightStart;
		int budget = MAX_SPAWNS_PER_SECOND;
		for (Guild guild : List.copyOf(GuildManager.all())) {
			if (!guild.trade.visitors.isEmpty()) {
				Safe.run("trade.visitors", () -> tickVisitors(level, guild, gameTime));
			}
			if (!guild.trade.pending.isEmpty()) {
				int left = budget;
				budget -= Safe.get("trade.pending", () -> tickPending(level, guild, day, timeOfDay, nightStart, left), 0);
			}
		}
	}

	private static void tickVisitors(ServerLevel level, Guild guild, long gameTime) {
		for (GuildTradeData.Visitor visitor : List.copyOf(guild.trade.visitors)) {
			WanderingTrader trader = level.getEntity(visitor.trader) instanceof WanderingTrader found && !found.isRemoved() ? found : null;
			if (gameTime >= visitor.until) {
				// A trade in progress may finish (one more minute at most).
				if (trader == null || !trader.isTrading() || gameTime >= visitor.until + TRADING_GRACE_TICKS) {
					leave(level, guild, visitor, true);
				}
				continue;
			}
			if (trader != null) {
				// The vanilla countdown only runs while the trader ticks: keep it behind the real end of the stay.
				trader.setDespawnDelay((int) Math.min(Integer.MAX_VALUE, visitor.until - gameTime + DESPAWN_MARGIN_TICKS));
			}
		}
	}

	/** @return spawn attempts made */
	private static int tickPending(ServerLevel level, Guild guild, long day, long timeOfDay, int nightStart, int budget) {
		int attempts = 0;
		for (Map.Entry<String, Long> entry : List.copyOf(guild.trade.pending.entrySet())) {
			Kind kind = KINDS.get(entry.getKey());
			boolean expired = entry.getValue() != day || timeOfDay >= nightStart;
			if (kind == null || expired || !kind.stillWanted(guild) || guild.trade.visitor(kind.id()) != null) {
				guild.trade.pending.remove(entry.getKey());
				GuildManager.markDirty();
				continue;
			}
			// Nobody to welcome it, or the base is under attack: it waits (until nightfall).
			if (attempts >= budget || guild.onlineMembers().isEmpty() || WaveApi.isActive(guild)) {
				continue;
			}
			attempts++;
			Result result = spawn(level, guild, kind, false);
			switch (result) {
				case SPAWNED -> {
					guild.trade.pending.remove(kind.id());
					GuildManager.markDirty();
				}
				case NOT_LOADED, NO_SPACE -> {
					// retried next second (until nightfall)
				}
				default -> {
					Guildy.LOGGER.warn("[trade] visit '{}' of guild {} dropped: {}", kind.id(), guild.name, result);
					guild.trade.pending.remove(kind.id());
					GuildManager.markDirty();
				}
			}
		}
		return attempts;
	}

	// ================================================================== entity events

	private static void rebuildIndex() {
		INDEX.clear();
		for (Guild guild : GuildManager.all()) {
			for (GuildTradeData.Visitor visitor : guild.trade.visitors) {
				INDEX.put(visitor.trader, guild.id);
				for (UUID llama : visitor.llamas) {
					INDEX.put(llama, guild.id);
				}
			}
		}
	}

	/** Visitor (re)loaded: home area restored; a tagged entity without record (stay over, restart...) is removed. */
	private static void onEntityLoad(Entity entity) {
		if (!entity.entityTags().contains(TAG)) {
			return;
		}
		UUID guildId = INDEX.get(entity.getUUID());
		Guild guild = guildId == null ? null : GuildManager.get(guildId);
		GuildTradeData.Visitor visitor = guild == null ? null : byTrader(guild, entity.getUUID());
		if (visitor != null && entity instanceof WanderingTrader trader) {
			trader.setHomeTo(visitor.pos, TradeModule.config().visits.homeRadius);
			return;
		}
		if (guild != null) {
			return; // one of its llamas
		}
		Scheduler.later("trade.cleanup", 1, () -> {
			if (!entity.isRemoved() && !INDEX.containsKey(entity.getUUID())) {
				if (entity instanceof TraderLlama llama) {
					llama.removeLeash();
				}
				entity.discard();
			}
		});
	}

	/** A visiting trader killed (or removed by something else): the visit ends; a dead llama is forgotten. */
	private static void onEntityUnload(Entity entity) {
		if (!entity.entityTags().contains(TAG)) {
			return;
		}
		Entity.RemovalReason reason = entity.getRemovalReason();
		if (reason == null || !reason.shouldDestroy()) {
			return; // chunk unloaded: the visit goes on
		}
		UUID guildId = INDEX.remove(entity.getUUID());
		Guild guild = guildId == null ? null : GuildManager.get(guildId);
		if (guild == null) {
			return;
		}
		GuildTradeData.Visitor visitor = byTrader(guild, entity.getUUID());
		if (visitor == null) {
			for (GuildTradeData.Visitor other : guild.trade.visitors) {
				if (other.llamas.remove(entity.getUUID())) {
					GuildManager.markDirty();
					break;
				}
			}
			return;
		}
		// Never remove other entities from inside an entity removal callback: the llamas go one tick later.
		guild.trade.visitors.remove(visitor);
		for (UUID llama : visitor.llamas) {
			INDEX.remove(llama);
		}
		GuildManager.markDirty();
		List<UUID> llamas = List.copyOf(visitor.llamas);
		Scheduler.later("trade.llamas", 1, () -> {
			ServerLevel level = GuildyServer.claimLevel();
			for (UUID id : llamas) {
				if (level != null && level.getEntity(id) instanceof TraderLlama llama && !llama.isRemoved()) {
					llama.removeLeash();
					llama.discard();
				}
			}
		});
		Msg.guild(guild, reason == Entity.RemovalReason.KILLED ? "trade.visitor.killed" : "trade.visitor.leave", Lang.colorize(visitor.name));
	}

	private static GuildTradeData.@Nullable Visitor byTrader(Guild guild, UUID trader) {
		for (GuildTradeData.Visitor visitor : guild.trade.visitors) {
			if (trader.equals(visitor.trader)) {
				return visitor;
			}
		}
		return null;
	}

	// ================================================================== helpers

	/**
	 * Sets the offers before any vanilla trade generation (AbstractVillager has no setter: its field is set directly).
	 * Fallback if the field is not accessible: vanilla generation, then replaced.
	 */
	static void setOffers(AbstractVillager trader, MerchantOffers offers) {
		if (!offersFieldFailed) {
			try {
				Field field = offersField;
				if (field == null) {
					field = AbstractVillager.class.getDeclaredField("offers");
					field.setAccessible(true);
					offersField = field;
				}
				field.set(trader, offers);
				return;
			} catch (ReflectiveOperationException | RuntimeException e) {
				offersFieldFailed = true;
				Guildy.LOGGER.warn("[trade] cannot set merchant offers directly, using the vanilla list instead", e);
			}
		}
		MerchantOffers current = trader.getOffers();
		current.clear();
		current.addAll(offers);
	}

	/** Loaded (never triggers a chunk load) and entity-ticking. */
	static boolean isTicking(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null && level.isPositionEntityTicking(pos)
				&& level.areEntitiesLoaded(ChunkKey.ofBlock(pos.getX(), pos.getZ()));
	}

	/** A free standing spot near {@code target} (radius 4), inside the guild territory, in ticking chunks. */
	static @Nullable BlockPos findSpot(ServerLevel level, Guild guild, BlockPos target, EntityType<?> type) {
		for (int attempt = 0; attempt < 20; attempt++) {
			int dx = attempt == 0 ? 0 : RANDOM.nextInt(9) - 4;
			int dz = attempt == 0 ? 0 : RANDOM.nextInt(9) - 4;
			BlockPos column = target.offset(dx, 0, dz);
			if (GuildManager.at(level, column) != guild || !isTicking(level, column)) {
				continue;
			}
			BlockPos spot = standable(level, type, column);
			if (spot != null) {
				return spot;
			}
		}
		return null;
	}

	/** Closest height (within 3 blocks of the column's y) with a sturdy dry floor and room for the entity. */
	private static @Nullable BlockPos standable(ServerLevel level, EntityType<?> type, BlockPos column) {
		for (int dy : new int[] {0, 1, -1, 2, -2, 3, -3}) {
			BlockPos pos = column.above(dy);
			BlockPos floor = pos.below();
			BlockState floorState = level.getBlockState(floor);
			if (!floorState.getFluidState().isEmpty() || !floorState.isFaceSturdy(level, floor, Direction.UP)) {
				continue;
			}
			AABB box = type.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			if (level.getWorldBorder().isWithinBounds(box) && level.noCollision(box) && !level.containsAnyLiquid(box)) {
				return pos;
			}
		}
		return null;
	}
}
