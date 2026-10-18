package dev.guildy.ruin;

import dev.guildy.Guildy;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.ruin.RuinConfig.RuinDef;
import dev.guildy.ruin.SummonRules.Refusal;
import dev.guildy.ruin.mixin.LivingEntityFlagsAccessor;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reading an incantation scroll and running the rituals. Right click inside a dormant ruin, near the boss marker, starts
 * the vanilla channel animation of the scroll's consumable component (after the checks of {@link SummonRules#check}; a
 * refused start also stops the animation the client predicted). When the vanilla use completes,
 * {@code ItemStack.finishUsingItem} is intercepted (ruin mixin) and {@link #finish} checks again, consumes exactly one
 * scroll (never in creative) and starts the {@link Ritual}. At its climax the fight starts ({@link Encounters#awaken});
 * a ritual nobody feeds any more (everyone left, died or disconnected), or stopped by the server, is cancelled and the
 * scroll goes back to its reader. A cursed incantation scroll ({@link CursedItems}) is read the same way and starts a
 * {@link CursedRitual} whose climax awakens the guardian cursed. While room guards still hold the arena, the reading is
 * sealed ({@link Seals}): the circle starts to form under the reader, then breaks; the scroll is kept.
 */
public final class Incantations {
	private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
	private static final Map<String, Ritual> RITUALS = new LinkedHashMap<>();
	private static final int MIN_SHARE = 80;

	/** A started incantation (channel animation running), of a cursed scroll or not. */
	private record Channel(String ruinKey, InteractionHand hand, long start, int ticks, boolean cursed) {
	}

	/** Where a player stands: the ruin (null outside ruins), its definition and why the incantation is refused. */
	record Target(@Nullable RuinRecord ruin, @Nullable RuinDef def, Refusal refusal, long daysLeft) {
	}

	private Incantations() {
	}

	static void init() {
		UseItemCallback.EVENT.register(Incantations::onUse);
		Scheduler.every("ruin.ritual", 1, 0, Incantations::tick);
		Scheduler.every("ruin.incantation.scan", 20, 7, Incantations::scanAll);
		Seals.init();
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Safe.run("ruin.incantation.join", () -> {
			IncantationItems.scan(handler.getPlayer());
			CursedItems.scan(handler.getPlayer());
		}));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Safe.run("ruin.incantation.leave", () -> {
			CHANNELS.remove(handler.getPlayer().getUUID());
			Seals.forget(handler.getPlayer().getUUID());
		}));
		Lifecycle.RELOADED.add(IncantationItems::clearSignature);
		Lifecycle.RELOADED.add(CursedItems::clearSignature);
	}

	/** An incantation scroll, normal or cursed. */
	static boolean isScroll(ItemStack stack) {
		return IncantationItems.is(stack) || CursedItems.is(stack);
	}

	// ================================================================== queries

	public static boolean ritualRunning(RuinRecord ruin) {
		return RITUALS.containsKey(ruin.key);
	}

	public static int ritualCount() {
		return RITUALS.size();
	}

	/** The ritual running in this ruin is a cursed one. */
	public static boolean ritualCursed(RuinRecord ruin) {
		Ritual ritual = RITUALS.get(ruin.key);
		return ritual != null && ritual.cursed();
	}

	/** Progress (0..1) of the ritual running in this ruin, -1 when none. */
	public static double ritualProgress(RuinRecord ruin) {
		Ritual ritual = RITUALS.get(ruin.key);
		return ritual == null ? -1 : ritual.progress();
	}

	/** Players taking part in the ritual running in this ruin (empty when none). */
	public static List<ServerPlayer> ritualParticipants(RuinRecord ruin) {
		Ritual ritual = RITUALS.get(ruin.key);
		return ritual == null ? List.of() : List.copyOf(ritual.participants);
	}

	/** Where the guardian appears: the boss marker, else the middle of the ruin. */
	static BlockPos marker(RuinRecord ruin) {
		return ruin.boss != null ? ruin.boss : ruin.center();
	}

	// ================================================================== start

	private static InteractionResult onUse(Player player, Level level, InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		if (!isScroll(player.getItemInHand(hand))) {
			return InteractionResult.PASS;
		}
		return Safe.get("ruin.incantation.use", () -> start(serverPlayer, hand), InteractionResult.FAIL);
	}

	/** Starts the incantation of the scroll in {@code hand} (or explains why not). */
	static InteractionResult start(ServerPlayer player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		boolean cursed = CursedItems.is(stack);
		ItemStack fresh = cursed ? CursedItems.refreshed(stack) : IncantationItems.refreshed(stack);
		if (fresh != null) {
			// Incomplete (loot table) or outdated scroll: rebuilt before use.
			int extra = stack.getCount() - fresh.getCount();
			player.setItemInHand(hand, fresh);
			if (extra > 0) {
				if (cursed) {
					CursedItems.give(player, extra);
				} else {
					IncantationItems.give(player, extra);
				}
			}
		}
		if (player.isUsingItem()) {
			return InteractionResult.PASS;
		}
		Target target = target(player, cursed);
		if (target.refusal() == Refusal.GUARDED) {
			return sealed(player, target, cursed, false);
		}
		if (target.refusal() != Refusal.NONE) {
			return refuse(player, target);
		}
		// A real reading: no broken circle left under the new one.
		Seals.stop(player.getUUID());
		player.startUsingItem(hand);
		if (!player.isUsingItem()) {
			Msg.actionBar(player, "ruin.summon.interrupted");
			syncUsing(player);
			return InteractionResult.FAIL;
		}
		RuinConfig cfg = RuinModule.config();
		int ticks = SummonRules.ticks(cursed ? cfg.cursed.ritual.channelSeconds : cfg.summon.channelSeconds);
		CHANNELS.put(player.getUUID(), new Channel(target.ruin().key, hand, Scheduler.ticks(), ticks, cursed));
		Vec3 pos = player.position();
		player.level().playSound(null, pos.x, pos.y, pos.z, cursed ? SoundEvents.EVOKER_PREPARE_WOLOLO : SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS,
				1.0F, cursed ? 0.5F : 0.7F);
		player.level().playSound(null, pos.x, pos.y, pos.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.6F);
		if (cursed) {
			player.level().playSound(null, pos.x, pos.y, pos.z, SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.5F, 0.5F);
		}
		return InteractionResult.CONSUME;
	}

	/** The ruin the player stands in and whether an incantation can be read there now. */
	static Target target(ServerPlayer player) {
		return target(player, false);
	}

	/** Same, for a cursed scroll when {@code cursed} (refused while the cursed guardians are disabled). */
	static Target target(ServerPlayer player, boolean cursed) {
		ServerLevel level = player.level();
		RuinConfig cfg = RuinModule.config();
		RuinRecord ruin = RuinIndex.containing(level.dimension(), player.getX(), player.getY(), player.getZ(), cfg.encounter.activationMargin);
		if (ruin == null) {
			return new Target(null, null, Refusal.NOT_IN_RUIN, 0);
		}
		MinecraftServer server = level.getServer();
		Encounters.rearmIfDue(server, ruin);
		RuinDef def = Encounters.def(ruin);
		long daysLeft = ruin.state == RuinState.CLEARED ? RuinRules.daysLeft(ruin.clearedDay, Encounters.day(server), def.respawnDays) : 0;
		BlockPos marker = marker(ruin);
		SummonRules.Situation situation = new SummonRules.Situation(cfg.bossesEnabled, level.getDifficulty() == Difficulty.PEACEFUL, true,
				level.dimension().identifier().toString().equals(ruin.dimension), ruin.state, daysLeft, Encounters.isActive(ruin),
				RITUALS.containsKey(ruin.key), player.position().distanceToSqr(Vec3.atBottomCenterOf(marker)), cfg.summon.arenaRadius,
				Encounters.count() + RITUALS.size(), cfg.encounter.maxActive, Encounters.loaded(level, marker));
		Refusal refusal = SummonRules.check(situation);
		if (refusal == Refusal.NONE && cursed && !cfg.cursed.enabled) {
			refusal = Refusal.CURSED_DISABLED;
		}
		if (refusal == Refusal.NONE && RoomGuards.blocking(level, ruin) > 0) {
			// The room guards first: they hold the arena (normal and cursed rituals alike).
			refusal = Refusal.GUARDED;
		}
		return new Target(ruin, def, refusal, daysLeft);
	}

	/** Refusal message: {0} = ruin, {1} = days left, {2} = guardian, {3} = arena radius, {4} = guards left in the arena. */
	private static void explain(ServerPlayer player, Target target) {
		RuinDef def = target.def();
		String ruinName = def == null ? "" : Lang.colorize(def.name);
		String bossName = def == null ? "" : Lang.colorize(def.boss.name);
		int guards = target.refusal() == Refusal.GUARDED && target.ruin() != null ? RoomGuards.blocking(player.level(), target.ruin()) : 0;
		Msg.actionBar(player, target.refusal().langKey(), ruinName, Math.max(1, target.daysLeft()), bossName, RuinModule.config().summon.arenaRadius,
				guards);
		Msg.sound(player, SoundEvents.VILLAGER_NO, 0.6F, 1.2F);
	}

	/** A refused start: the reason, and the "not using" state sent back to the client (it predicted the animation). */
	private static InteractionResult refuse(ServerPlayer player, Target target) {
		explain(player, target);
		syncUsing(player);
		return InteractionResult.FAIL;
	}

	/**
	 * The room guards still hold the arena: instead of a dry refusal, the incantation circle starts to form under the reader
	 * and breaks ({@link Seals}; a new attempt while it plays is ignored). The scroll is kept.
	 *
	 * @param drawn the channel already drew the circle (the guards came meanwhile): it cracks at once
	 */
	private static InteractionResult sealed(ServerPlayer player, Target target, boolean cursed, boolean drawn) {
		syncUsing(player);
		if (target.ruin() != null) {
			Seals.play(player, target.ruin(), cursed, drawn);
		}
		return InteractionResult.FAIL;
	}

	/** Sends the player's real "using an item" flags to their client (stops a predicted use animation). */
	private static void syncUsing(ServerPlayer player) {
		EntityDataAccessor<Byte> flags = LivingEntityFlagsAccessor.guildy$ruinLivingFlags();
		List<SynchedEntityData.DataValue<?>> values = new ArrayList<>();
		values.add(SynchedEntityData.DataValue.create(flags, player.getEntityData().get(flags)));
		player.connection.send(new ClientboundSetEntityDataPacket(player.getId(), values));
	}

	// ================================================================== completion

	/**
	 * The vanilla use of an incantation scroll completed ({@code ItemStack.finishUsingItem}, server side): the ritual
	 * starts. Never throws.
	 *
	 * @return the stack left in hand (one scroll less once the ritual started, outside creative)
	 */
	public static ItemStack finish(ItemStack stack, Level level, LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel)) {
			return stack;
		}
		return Safe.get("ruin.incantation.finish", () -> complete(player, stack), stack);
	}

	private static ItemStack complete(ServerPlayer player, ItemStack stack) {
		Channel channel = CHANNELS.remove(player.getUUID());
		boolean cursed = CursedItems.is(stack);
		if (channel == null || !isScroll(stack) || channel.cursed() != cursed) {
			Msg.actionBar(player, "ruin.summon.interrupted");
			return stack;
		}
		Target target = target(player, cursed);
		if (target.refusal() != Refusal.NONE) {
			// Nothing consumed: the client, which predicted the scroll eaten, gets its inventory back.
			player.inventoryMenu.sendAllDataToRemote();
			if (target.refusal() == Refusal.GUARDED) {
				sealed(player, target, cursed, true);
				return stack;
			}
			explain(player, target);
			fizzle(player);
			return stack;
		}
		if (!target.ruin().key.equals(channel.ruinKey())) {
			Msg.actionBar(player, "ruin.summon.interrupted");
			fizzle(player);
			return stack;
		}
		RuinConfig cfg = RuinModule.config();
		ServerLevel level = player.level();
		boolean consumed = !player.hasInfiniteMaterials();
		player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
		// Exactly one scroll, never in creative (nothing to give back then).
		stack.consume(1, player);
		Ritual ritual = cursed
				? new CursedRitual(level, target.ruin(), target.def(), marker(target.ruin()), player, consumed ? CursedItems.create(1) : ItemStack.EMPTY, cfg)
				: new Ritual(level, target.ruin(), target.def(), marker(target.ruin()), player, consumed ? IncantationItems.create(1) : ItemStack.EMPTY, cfg);
		RITUALS.put(target.ruin().key, ritual);
		ritual.gather(level, cfg.summon.arenaRadius);
		Safe.run("ruin.ritual.begin", () -> ritual.begin(level, cfg));
		return stack;
	}

	// ================================================================== loop

	private static void tick() {
		MinecraftServer server = GuildyServer.server();
		if (server == null) {
			return;
		}
		if (!CHANNELS.isEmpty()) {
			tickChannels();
		}
		if (RITUALS.isEmpty()) {
			return;
		}
		RuinConfig cfg = RuinModule.config();
		int share = Math.max(MIN_SHARE, cfg.summon.particleBudget / RITUALS.size());
		int cursedShare = Math.max(MIN_SHARE, cfg.cursed.attacks.particleBudget / RITUALS.size());
		for (Ritual ritual : new ArrayList<>(RITUALS.values())) {
			ServerLevel level = server.getLevel(ritual.dimension);
			boolean ok = Safe.get("ruin.ritual", () -> {
				step(level, ritual, cfg, ritual.cursed() ? cursedShare : share);
				return true;
			}, false);
			if (!ok && RITUALS.get(ritual.ruin.key) == ritual) {
				Guildy.LOGGER.warn("[ruins] ritual of {} stopped after an error", ritual.ruin.key);
				cancel(ritual, level, true);
			}
		}
	}

	private static void step(@Nullable ServerLevel level, Ritual ritual, RuinConfig cfg, int share) {
		if (level == null) {
			cancel(ritual, null, false);
			return;
		}
		ritual.age++;
		List<ServerPlayer> participants = ritual.gather(level, cfg.summon.arenaRadius);
		ritual.emptyTicks = participants.isEmpty() ? ritual.emptyTicks + 1 : 0;
		boolean dormant = ritual.ruin.state == RuinState.DORMANT && !Encounters.isActive(ritual.ruin);
		switch (SummonRules.step(ritual.age, ritual.duration, participants.size(), ritual.emptyTicks, SummonRules.GRACE_TICKS, dormant)) {
			case CANCEL -> cancel(ritual, level, true);
			case CLIMAX -> climax(level, ritual, cfg);
			case CONTINUE -> {
				RitualFx fx = new RitualFx(level, ritual.ground, share, cfg.summon.viewDistance);
				ritual.tick(level, fx, cfg);
				fx.flush();
			}
		}
	}

	/** The ritual is complete: full blood price, climax, then the guardian and its escort appear. */
	private static void climax(ServerLevel level, Ritual ritual, RuinConfig cfg) {
		RITUALS.remove(ritual.ruin.key, ritual);
		ritual.settle(level, cfg);
		ritual.close();
		RitualFx fx = new RitualFx(level, ritual.ground, Math.max(600, ritual.cursed() ? cfg.cursed.attacks.particleBudget : cfg.summon.particleBudget),
				cfg.summon.viewDistance);
		ritual.climaxFx(level, fx);
		String error = Encounters.awaken(level, ritual.ruin, null, false, ritual.cursed());
		if (error != null) {
			Guildy.LOGGER.warn("[ruins] the ritual of {} could not awaken its guardian: {}", ritual.ruin.key, error);
			fx.flush();
			refund(ritual, level);
			for (ServerPlayer player : ritual.participants) {
				Msg.send(player, "ruin.summon.failed", Lang.colorize(ritual.def.boss.name));
			}
			return;
		}
		Mob boss = Encounters.boss(ritual.ruin);
		if (boss != null) {
			ritual.materialize(level, fx, boss, Encounters.minions(ritual.ruin));
		}
		fx.flush();
	}

	/**
	 * Cancels a ritual: the scroll goes back to its reader (online: inventory, else dropped at the circle and kept there).
	 *
	 * @param announce the players around are told why
	 */
	private static void cancel(Ritual ritual, @Nullable ServerLevel level, boolean announce) {
		RITUALS.remove(ritual.ruin.key, ritual);
		ritual.close();
		refund(ritual, level);
		if (level == null) {
			return;
		}
		RitualFx fx = new RitualFx(level, ritual.ground, 200, RuinModule.config().summon.viewDistance);
		ritual.cancelFx(level, fx);
		fx.flush();
		if (announce) {
			for (ServerPlayer player : Encounters.present(level, ritual.ruin, RuinModule.config().encounter.presenceMargin * 2)) {
				Msg.send(player, "ruin.summon.cancelled", Lang.colorize(ritual.def.name), ritual.casterName);
			}
		}
	}

	private static void refund(Ritual ritual, @Nullable ServerLevel level) {
		if (ritual.refund.isEmpty()) {
			return;
		}
		ServerPlayer caster = GuildyServer.player(ritual.caster);
		if (caster != null) {
			ItemUtil.giveOrDrop(caster, ritual.refund.copy());
			Msg.send(caster, "ruin.summon.refunded");
		} else if (level != null) {
			ItemEntity item = new ItemEntity(level, ritual.ground.x, ritual.ground.y + 0.5, ritual.ground.z, ritual.refund.copy());
			item.setUnlimitedLifetime();
			level.addFreshEntity(item);
		} else {
			Guildy.LOGGER.warn("[ruins] incantation scroll of {} lost: the ritual's level is gone", ritual.casterName);
		}
	}

	/** Admin awakening / reset of a ruin with a ritual running: the ritual is cancelled (scroll refunded). */
	static void cancelRitual(RuinRecord ruin) {
		Ritual ritual = RITUALS.get(ruin.key);
		if (ritual != null) {
			MinecraftServer server = GuildyServer.server();
			cancel(ritual, server == null ? null : server.getLevel(ritual.dimension), false);
		}
	}

	/** Server stopping: every ritual is cancelled, scrolls refunded (players are still online, the chunks not saved yet). */
	static void stopAll() {
		MinecraftServer server = GuildyServer.server();
		for (Ritual ritual : new ArrayList<>(RITUALS.values())) {
			Safe.run("ruin.ritual.stop", () -> cancel(ritual, server == null ? null : server.getLevel(ritual.dimension), false));
		}
		RITUALS.clear();
		CHANNELS.clear();
		Seals.clear();
	}

	// ================================================================== channel visuals

	private static void tickChannels() {
		RuinConfig cfg = RuinModule.config();
		long now = Scheduler.ticks();
		List<UUID> ended = new ArrayList<>();
		for (Map.Entry<UUID, Channel> entry : CHANNELS.entrySet()) {
			ServerPlayer player = GuildyServer.player(entry.getKey());
			Channel channel = entry.getValue();
			if (player == null || !player.isAlive() || !player.isUsingItem() || !isScroll(player.getUseItem())) {
				ended.add(entry.getKey());
				if (player != null && player.isAlive()) {
					Msg.actionBar(player, "ruin.summon.interrupted");
					fizzle(player);
				}
				continue;
			}
			long elapsed = now - channel.start();
			RuinRecord ruin = RuinIndex.get(channel.ruinKey());
			int color = channel.cursed() ? CursedFx.PURPLE : ruin == null ? Ritual.BLOOD_BRIGHT : SummonRules.themeColor(Encounters.def(ruin).bossBarColor);
			Safe.run("ruin.incantation.fx", () -> drawChannel(player, elapsed, channel.ticks(), color, cfg, channel.cursed()));
		}
		for (UUID uuid : ended) {
			CHANNELS.remove(uuid);
		}
	}

	/**
	 * A rune circle drawing itself under the reader, blood rising from the floor into their hands (cursed: an inverted
	 * pentagram, void and souls).
	 */
	private static void drawChannel(ServerPlayer player, long elapsed, int total, int color, RuinConfig cfg, boolean cursed) {
		double progress = Math.min(1, (elapsed + 1) / (double) Math.max(1, total));
		RitualFx fx = new RitualFx(player.level(), player.position(), 160, cfg.summon.viewDistance);
		Vec3 feet = player.position().add(0, 0.08, 0);
		double spin = elapsed * 0.1;
		drawReaderCircle(fx, feet, progress, spin, color, cursed);
		if (cursed && elapsed % 2 == 0) {
			fx.burst(RitualFx.dust(CursedFx.VOID, 1.0F), feet.x, feet.y + 0.6, feet.z, 3, 0.6, 0.4, 0.6, 0.0);
			fx.move(ParticleTypes.SOUL, feet.x + Math.cos(spin * 3) * 1.6, feet.y + 0.1, feet.z + Math.sin(spin * 3) * 1.6, 0, 0.06, 0);
		}
		Vec3 hands = player.position().add(0, 1.1, 0);
		for (int i = 0; i < 2; i++) {
			double angle = elapsed * 0.6 + i * Math.PI;
			fx.move(ParticleTypes.ENCHANT, hands.x, hands.y, hands.z, Math.cos(angle) * 1.6, -1.0, Math.sin(angle) * 1.6);
		}
		if (elapsed % 3 == 0) {
			fx.burst(RitualFx.dust(Ritual.BLOOD_BRIGHT, 0.7F), hands.x, hands.y, hands.z, 3, 0.3, 0.3, 0.3, 0.0);
		}
		fx.flush();
		if (elapsed % 10 == 5) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 0.8F,
					0.6F + (float) progress * 0.6F);
		}
	}

	/**
	 * The circle under a reader, {@code progress} (0..1) of it drawn: two counter-rotating rings, then the pentagram from
	 * 30 % (inverted for a cursed reading). Also the sealed circle of {@link Seals}.
	 */
	static void drawReaderCircle(RitualFx fx, Vec3 feet, double progress, double spin, int color, boolean cursed) {
		double p = Math.max(0, Math.min(1, progress));
		fx.arc(RitualFx.dust(color, 0.9F), feet, Seals.OUTER, spin, spin + Math.PI * 2 * p, (int) Math.ceil(24 * p));
		fx.arc(RitualFx.fade(Ritual.BLOOD, color, 0.8F), feet, Seals.INNER, -spin, -spin - Math.PI * 2 * p, (int) Math.ceil(18 * p));
		if (p > 0.3) {
			fx.polygram(RitualFx.dust(Ritual.BLOOD_BRIGHT, 0.8F), feet, Seals.INNER, 5, 2, pentagramPhase(spin, cursed), (p - 0.3) / 0.7, 3.0);
		}
	}

	/** Rotation of the reader's pentagram (a cursed one points down). */
	static double pentagramPhase(double spin, boolean cursed) {
		return cursed ? Math.PI * 1.5 - spin * 0.5 : spin * 0.5;
	}

	/** A failed or interrupted incantation: smoke and a dull sound. */
	private static void fizzle(ServerPlayer player) {
		RitualFx fx = new RitualFx(player.level(), player.position(), 40, 48);
		fx.burst(ParticleTypes.SMOKE, player.getX(), player.getY(0.6), player.getZ(), 16, 0.4, 0.4, 0.4, 0.02);
		fx.flush();
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 0.6F, 1.4F);
	}

	private static void scanAll() {
		for (ServerPlayer player : List.copyOf(GuildyServer.players())) {
			Safe.run("ruin.incantation.scan", () -> {
				IncantationItems.scan(player);
				CursedItems.scan(player);
			});
		}
	}
}
