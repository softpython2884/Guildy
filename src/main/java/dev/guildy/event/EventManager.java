package dev.guildy.event;

import dev.guildy.Guildy;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.trade.GuildTradeData;
import dev.guildy.trade.Merchants;
import dev.guildy.trade.TradeEntry;
import dev.guildy.trade.TradeHub;
import dev.guildy.trade.TradeRolls;
import dev.guildy.wave.NightModifiers;
import dev.guildy.wave.WaveApi;
import dev.guildy.wave.WaveFormula;
import dev.guildy.wave.WaveModule;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * Random events runtime: once per day a dusk roll (blood night, server-wide) and a morning roll per guild (pillager raid,
 * caravan, book merchant), the pillager raid countdown, the blood night modifiers given to the wave module, the event
 * lines of the waves menus and the admin actions. Runs once per second; every action is isolated by {@link Safe}.
 */
public final class EventManager {
	private static final SplittableRandom RANDOM = new SplittableRandom();
	private static EventState state = new EventState();

	/** Result of an admin action (feedback for the command source). */
	public record AdminResult(boolean success, Component message) {
	}

	private EventManager() {
	}

	static void init() {
		Scheduler.every("event.tick", 20, 5, EventManager::tick);
		Lifecycle.STARTED.add(server -> state = EventState.load(server));
		Lifecycle.STOPPING.add(server -> state.save(server));
		Merchants.register(new CaravanKind());
		Merchants.register(new ScholarKind());
	}

	private static void save() {
		MinecraftServer server = GuildyServer.server();
		if (server != null) {
			state.save(server);
		}
	}

	private static EventConfig cfg() {
		return EventModule.config();
	}

	// ================================================================== tick

	private static void tick() {
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			return;
		}
		EventConfig cfg = cfg();
		long clock = level.getOverworldClockTime();
		long day = WaveFormula.dayIndex(clock);
		long timeOfDay = WaveFormula.timeOfDay(clock);
		long gameTime = level.getGameTime();
		if (state.bloodNightDay >= 0 && EventRules.targetNight(clock, WaveModule.config().timing.nightEnd) > state.bloodNightDay) {
			state.bloodNightDay = -1; // dawn: the blood night is over
			save();
			for (ServerPlayer player : GuildyServer.players()) {
				Msg.send(player, "event.blood_night.over");
			}
		}
		if (state.lastDuskRollDay != day && EventRules.inWindow(timeOfDay, cfg.timing.duskTime, cfg.timing.rollWindowTicks)) {
			state.lastDuskRollDay = day;
			save();
			if (cfg.randomEvents) {
				Safe.run("event.dusk", () -> rollDusk(day));
			}
		}
		boolean morning = EventRules.inWindow(timeOfDay, cfg.timing.morningTime, cfg.timing.rollWindowTicks);
		for (Guild guild : List.copyOf(GuildManager.all())) {
			if (morning && guild.events.lastRollDay != day) {
				guild.events.lastRollDay = day;
				GuildManager.markDirty();
				if (cfg.randomEvents) {
					Safe.run("event.morning", () -> rollMorning(guild, day, RANDOM));
				}
			}
			if (guild.events.raidPending()) {
				Safe.run("event.raid", () -> tickRaid(guild, day, timeOfDay, gameTime));
			}
		}
	}

	// ================================================================== blood night

	private static void rollDusk(long day) {
		EventConfig.BloodNight b = cfg().bloodNight;
		if (!b.enabled || state.bloodNightDay == day) {
			return;
		}
		int highest = 0;
		for (Guild guild : GuildManager.all()) {
			if (guild.isFounded()) {
				highest = Math.max(highest, guild.waves.night);
			}
		}
		if (EventRules.bloodNightEligible(highest, b.minNightStep, day, state.lastBloodNightDay, b.minDaysBetween) && RANDOM.nextDouble() < b.chance) {
			startBloodNight(day);
		}
	}

	private static void startBloodNight(long night) {
		state.bloodNightDay = night;
		state.lastBloodNightDay = night;
		save();
		EventConfig.BloodNight b = cfg().bloodNight;
		Component title = Lang.get("event.blood_night.title");
		Component subtitle = Lang.get("event.blood_night.subtitle");
		String count = multiplier(b.mobCountMultiplier);
		String buff = String.valueOf(Math.round((Math.max(b.healthMultiplier, b.damageMultiplier) - 1) * 100));
		String rewards = multiplier(b.goldMultiplier);
		for (ServerPlayer player : GuildyServer.players()) {
			Msg.title(player, title, subtitle, 20, 80, 30);
			Msg.sound(player, SoundEvents.ELDER_GUARDIAN_CURSE, 0.6F, 0.6F);
			Msg.sound(player, SoundEvents.AMBIENT_CAVE, 1.0F, 0.7F);
			Msg.send(player, "event.blood_night.chat", count, buff, rewards);
		}
		Guildy.LOGGER.info("[events] Blood night scheduled for night {}", night);
	}

	/** True when the blood night concerns the night at this clock (tonight, or the running night). */
	public static boolean isBloodNight(long clock) {
		return state.bloodNightDay >= 0 && EventRules.targetNight(clock, WaveModule.config().timing.nightEnd) == state.bloodNightDay;
	}

	/** Hook for the wave module: modifiers of a night / test wave starting now. */
	static NightModifiers nightModifiers(Guild guild, long clock) {
		return isBloodNight(clock) ? cfg().bloodNightModifiers(Lang.raw("event.blood_night.label")) : NightModifiers.NONE;
	}

	// ================================================================== morning (per guild)

	static void rollMorning(Guild guild, long day, RandomGenerator random) {
		if (!guild.isFounded()) {
			return;
		}
		EventConfig cfg = cfg();
		GuildEventData data = guild.events;
		EventConfig.PillagerRaid raid = cfg.pillagerRaid;
		// No raid during a truce after repeated defeats (waves.json5 truce.blocksRaids).
		if (!data.raidPending() && !WaveApi.truceBlocksRaids(guild) && EventRules.guildEligible(raid.enabled, true, guild.level,
				raid.minGuildLevel, day, data.last(EventType.PILLAGER_RAID.id), raid.cooldownDays) && random.nextDouble() < raid.chance) {
			data.raidDay = day;
			data.raidAt = 0;
			data.lastDay.put(EventType.PILLAGER_RAID.id, day);
			GuildManager.markDirty();
			Msg.guild(guild, "event.pillager_raid.spotted");
		}
		EventConfig.Caravan caravan = cfg.caravan;
		if (EventRules.guildEligible(caravan.enabled, true, guild.level, caravan.minGuildLevel, day, data.last(EventType.CARAVAN.id),
				caravan.cooldownDays) && random.nextDouble() < caravan.chance) {
			data.lastDay.put(EventType.CARAVAN.id, day);
			Merchants.schedule(guild, EventType.CARAVAN.id, day);
		}
		EventConfig.BookMerchant books = cfg.bookMerchant;
		if (EventRules.guildEligible(books.enabled, true, guild.level, books.minGuildLevel, day, data.last(EventType.BOOK_MERCHANT.id),
				books.cooldownDays) && random.nextDouble() < books.chance) {
			data.lastDay.put(EventType.BOOK_MERCHANT.id, day);
			Merchants.schedule(guild, EventType.BOOK_MERCHANT.id, day);
		}
	}

	// ================================================================== pillager raid

	private static void tickRaid(Guild guild, long day, long timeOfDay, long gameTime) {
		GuildEventData data = guild.events;
		EventRules.RaidStep step = EventRules.raidStep(data.raidDay, day, timeOfDay, cfg().timing.duskTime, data.raidAt, gameTime,
				WaveApi.nearbyMembers(guild), WaveApi.isActive(guild));
		switch (step) {
			case DROP -> {
				boolean warned = data.raidAt > 0;
				data.clearRaid();
				GuildManager.markDirty();
				if (warned) {
					Msg.guild(guild, "event.pillager_raid.gone");
				}
			}
			case WARN -> {
				int seconds = cfg().pillagerRaid.warningSeconds;
				data.raidAt = gameTime + seconds * 20L;
				GuildManager.markDirty();
				warnRaid(guild, seconds);
			}
			case ATTACK -> {
				WaveApi.StartResult result = startRaid(guild);
				if (result != WaveApi.StartResult.STARTED && result != WaveApi.StartResult.ALREADY_ACTIVE) {
					Guildy.LOGGER.warn("[events] Pillager raid against {} dropped: {}", guild.name, result);
					data.clearRaid();
					GuildManager.markDirty();
				}
			}
			default -> {
			}
		}
	}

	private static void warnRaid(Guild guild, int seconds) {
		String delay = duration(seconds);
		Component title = Lang.get("event.pillager_raid.warn.title");
		Component subtitle = Lang.get("event.pillager_raid.warn.subtitle", delay);
		for (ServerPlayer member : guild.onlineMembers()) {
			Msg.title(member, title, subtitle, 10, 70, 20);
			Msg.sound(member, SoundEvents.RAID_HORN, 0.8F, 1.2F);
			Msg.send(member, "event.pillager_raid.warn.chat", delay);
		}
	}

	private static WaveApi.StartResult startRaid(Guild guild) {
		EventConfig.PillagerRaid raid = cfg().pillagerRaid;
		double reward = raid.rewardMultiplier;
		ServerLevel level = GuildyServer.claimLevel();
		long timeOfDay = level == null ? 0 : WaveFormula.timeOfDay(level.getOverworldClockTime());
		int duration = EventRules.raidDuration(raid.maxDurationSeconds, timeOfDay, WaveModule.config().timing.nightStart);
		WaveApi.EventWave wave = new WaveApi.EventWave(EventType.PILLAGER_RAID.id, raid.theme, duration,
				NightModifiers.rewards("", reward, reward, reward));
		WaveApi.StartResult result = WaveApi.startEventWave(guild, wave);
		if (result == WaveApi.StartResult.STARTED) {
			guild.events.clearRaid();
			guild.events.raids++;
			GuildManager.markDirty();
		}
		return result;
	}

	// ================================================================== visitors (caravan, scholar)

	/** "Caravane marchande": wandering trader + llamas with the caravan table. */
	private static final class CaravanKind implements Merchants.Kind {
		@Override
		public String id() {
			return EventType.CARAVAN.id;
		}

		@Override
		public String name(Guild guild) {
			return Lang.raw("event.caravan.trader");
		}

		@Override
		public List<TradeEntry> offers(Guild guild, RandomGenerator random) {
			EventConfig.Caravan c = cfg().caravan;
			return TradeRolls.roll(c.usable(), TradeRolls.count(c.offersMin, c.offersMax, random), random);
		}

		@Override
		public @Nullable BlockPos target(Guild guild) {
			return TradeHub.basePoint(guild);
		}

		@Override
		public int staySeconds() {
			return cfg().caravan.staySeconds;
		}

		@Override
		public int llamas() {
			return cfg().caravan.llamas;
		}

		@Override
		public String langPrefix() {
			return "event.caravan.arrive";
		}
	}

	/** "Érudit itinérant": enchanted books drawn from the weighted pool. */
	private static final class ScholarKind implements Merchants.Kind {
		@Override
		public String id() {
			return EventType.BOOK_MERCHANT.id;
		}

		@Override
		public String name(Guild guild) {
			return Lang.raw("event.book_merchant.trader");
		}

		@Override
		public List<TradeEntry> offers(Guild guild, RandomGenerator random) {
			EventConfig.BookMerchant m = cfg().bookMerchant;
			return TradeRolls.roll(m.usable(), TradeRolls.count(m.booksMin, m.booksMax, random), random);
		}

		@Override
		public @Nullable BlockPos target(Guild guild) {
			return TradeHub.basePoint(guild);
		}

		@Override
		public int staySeconds() {
			return cfg().bookMerchant.staySeconds;
		}

		@Override
		public int llamas() {
			return 0;
		}

		@Override
		public String langPrefix() {
			return "event.book_merchant.arrive";
		}
	}

	// ================================================================== admin

	public static AdminResult start(EventType type, @Nullable Guild guild) {
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			return fail("error.internal");
		}
		if (type == EventType.BLOOD_NIGHT) {
			long night = EventRules.targetNight(level.getOverworldClockTime(), WaveModule.config().timing.nightEnd);
			startBloodNight(night);
			return ok("event.admin.blood_night.started", night);
		}
		if (guild == null) {
			return fail("event.admin.need_guild", "start", type.id);
		}
		if (type == EventType.PILLAGER_RAID) {
			return startRaidAdmin(level, guild);
		}
		Merchants.Result result = Merchants.spawnNow(guild, type.id, true);
		String name = Lang.raw(type.nameKey());
		if (result == Merchants.Result.SPAWNED) {
			return ok("event.admin.visit.started", name, guild.name);
		}
		long clock = level.getOverworldClockTime();
		if (result == Merchants.Result.NOT_LOADED && WaveFormula.timeOfDay(clock) < WaveModule.config().timing.nightStart) {
			// Base not loaded: the visit waits for it (like a natural one), dropped at nightfall.
			Merchants.schedule(guild, type.id, WaveFormula.dayIndex(clock));
			return ok("event.admin.visit.pending", name, guild.name);
		}
		return fail("event.admin.visit.failed", name, guild.name, Lang.raw("event.admin.reason." + result.name().toLowerCase(java.util.Locale.ROOT)));
	}

	private static AdminResult startRaidAdmin(ServerLevel level, Guild guild) {
		if (!guild.isFounded()) {
			return fail("event.admin.raid.failed", guild.name, Lang.raw("event.admin.reason.not_founded"));
		}
		if (WaveApi.isActive(guild)) {
			return fail("event.admin.raid.failed", guild.name, Lang.raw("event.admin.reason.already_active"));
		}
		if (WaveApi.nearbyMembers(guild) > 0) {
			WaveApi.StartResult result = startRaid(guild);
			return result == WaveApi.StartResult.STARTED ? ok("event.admin.raid.started", guild.name)
					: fail("event.admin.raid.failed", guild.name, Lang.raw("event.admin.reason." + result.name().toLowerCase(java.util.Locale.ROOT)));
		}
		long clock = level.getOverworldClockTime();
		if (WaveFormula.timeOfDay(clock) >= cfg().timing.duskTime) {
			return fail("event.admin.raid.failed", guild.name, Lang.raw("event.admin.reason.no_defender"));
		}
		// Nobody home: already "warned", it attacks as soon as a member comes near the heart (dropped at dusk).
		guild.events.raidDay = WaveFormula.dayIndex(clock);
		guild.events.raidAt = Math.max(1, level.getGameTime());
		guild.events.lastDay.put(EventType.PILLAGER_RAID.id, guild.events.raidDay);
		GuildManager.markDirty();
		return ok("event.admin.raid.waiting", guild.name);
	}

	public static AdminResult stop(EventType type, @Nullable Guild guild) {
		if (type == EventType.BLOOD_NIGHT) {
			if (state.bloodNightDay < 0) {
				return fail("event.admin.blood_night.none");
			}
			state.bloodNightDay = -1;
			save();
			for (ServerPlayer player : GuildyServer.players()) {
				Msg.send(player, "event.blood_night.cancelled");
			}
			return ok("event.admin.blood_night.stopped");
		}
		if (guild == null) {
			return fail("event.admin.need_guild", "stop", type.id);
		}
		if (type == EventType.PILLAGER_RAID) {
			boolean pending = guild.events.raidPending();
			guild.events.clearRaid();
			GuildManager.markDirty();
			WaveApi.Info info = WaveApi.info(guild);
			boolean running = info != null && EventType.PILLAGER_RAID.id.equals(info.eventId()) && WaveApi.cancel(guild, "wave.reason.admin");
			return pending || running ? ok("event.admin.raid.stopped", guild.name) : fail("event.admin.raid.none", guild.name);
		}
		String name = Lang.raw(type.nameKey());
		return Merchants.dismiss(guild, type.id) ? ok("event.admin.visit.stopped", name, guild.name) : fail("event.admin.visit.none", guild.name);
	}

	/** Lines of /guild admin event list. */
	public static List<Component> list() {
		EventConfig cfg = cfg();
		List<Component> lines = new ArrayList<>();
		lines.add(Lang.get("event.admin.list.header"));
		if (!cfg.randomEvents) {
			lines.add(Lang.get("event.admin.list.random_off"));
		}
		EventConfig.BloodNight b = cfg.bloodNight;
		String scheduled = state.bloodNightDay >= 0 ? Lang.raw("event.admin.list.scheduled", state.bloodNightDay) : "";
		lines.add(entry(EventType.BLOOD_NIGHT, b.enabled,
				Lang.raw("event.admin.list.blood_night", percent(b.chance), b.minNightStep, b.minDaysBetween, scheduled)));
		lines.add(entry(EventType.PILLAGER_RAID, cfg.pillagerRaid.enabled, Lang.raw("event.admin.list.guild_event",
				percent(cfg.pillagerRaid.chance), cfg.pillagerRaid.minGuildLevel, cfg.pillagerRaid.cooldownDays)));
		lines.add(entry(EventType.CARAVAN, cfg.caravan.enabled, Lang.raw("event.admin.list.guild_event",
				percent(cfg.caravan.chance), cfg.caravan.minGuildLevel, cfg.caravan.cooldownDays)));
		lines.add(entry(EventType.BOOK_MERCHANT, cfg.bookMerchant.enabled, Lang.raw("event.admin.list.guild_event",
				percent(cfg.bookMerchant.chance), cfg.bookMerchant.minGuildLevel, cfg.bookMerchant.cooldownDays)));
		var visits = dev.guildy.trade.TradeModule.config().visits;
		lines.add(entry(EventType.MERCHANT, visits.enabled, Lang.raw("event.admin.list.merchant", percent(visits.chanceBase),
				percent(visits.chancePerTier))));
		int shown = 0;
		for (Guild guild : GuildManager.all()) {
			List<String> parts = new ArrayList<>();
			if (guild.events.raidPending()) {
				parts.add(Lang.raw("event.admin.list.raid"));
			}
			WaveApi.Info info = WaveApi.info(guild);
			if (info != null && info.isEvent()) {
				parts.add(Lang.raw("event.admin.list.wave", info.eventId(), info.kills(), info.total()));
			}
			for (GuildTradeData.Visitor visitor : guild.trade.visitors) {
				parts.add(Lang.raw("event.admin.list.visitor", visitor.kind, duration(Merchants.remainingSeconds(visitor))));
			}
			for (Map.Entry<String, Long> pending : guild.trade.pending.entrySet()) {
				parts.add(Lang.raw("event.admin.list.pending", pending.getKey()));
			}
			if (!parts.isEmpty() && shown < 15) {
				lines.add(Lang.get("event.admin.list.active", guild.name, String.join(Lang.raw("event.admin.list.separator"), parts)));
				shown++;
			}
		}
		if (shown == 0) {
			lines.add(Lang.get("event.admin.list.none"));
		}
		return lines;
	}

	private static Component entry(EventType type, boolean enabled, String details) {
		return Lang.get("event.admin.list.entry", Lang.raw(type.nameKey()), type.id, details + (enabled ? "" : " " + Lang.raw("event.admin.list.disabled")));
	}

	private static AdminResult ok(String key, Object... args) {
		return new AdminResult(true, Lang.get(key, args));
	}

	private static AdminResult fail(String key, Object... args) {
		return new AdminResult(false, Lang.get(key, args));
	}

	// ================================================================== menus

	/** Event lines of the waves menus: blood night, raid on its way, merchants at the base. */
	static List<Component> lore(Guild guild) {
		List<Component> lines = new ArrayList<>();
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			return lines;
		}
		long clock = level.getOverworldClockTime();
		if (isBloodNight(clock)) {
			boolean night = WaveFormula.inNight(WaveModule.config().timing, WaveFormula.timeOfDay(clock));
			lines.add(Lang.get(night ? "event.lore.blood_night.now" : "event.lore.blood_night.tonight", multiplier(cfg().bloodNight.goldMultiplier)));
		}
		GuildEventData data = guild.events;
		if (data.raidPending()) {
			lines.add(data.raidAt > 0
					? Lang.get("event.lore.raid.soon", duration(Math.max(0, (data.raidAt - level.getGameTime()) / 20)))
					: Lang.get("event.lore.raid.pending"));
		}
		for (GuildTradeData.Visitor visitor : guild.trade.visitors) {
			lines.add(Lang.get("event.lore.visitor", Lang.colorize(visitor.name), duration(Merchants.remainingSeconds(visitor))));
		}
		for (String kind : guild.trade.pending.keySet()) {
			EventType type = EventType.byId(kind);
			lines.add(Lang.get("event.lore.visitor.pending", type == null ? kind : Lang.raw(type.nameKey())));
		}
		return lines;
	}

	// ================================================================== helpers

	static String duration(long seconds) {
		long s = Math.max(0, seconds);
		if (s < 60) {
			return Lang.raw("event.time.seconds", s);
		}
		return s % 60 == 0 ? Lang.raw("event.time.minutes_exact", s / 60) : Lang.raw("event.time.minutes", s / 60, s % 60);
	}

	static String multiplier(double value) {
		return Math.abs(value - Math.rint(value)) < 1e-6 ? String.valueOf((long) Math.rint(value))
				: String.format(java.util.Locale.FRANCE, "%.1f", value);
	}

	static String percent(double chance) {
		double pct = chance * 100;
		return Math.abs(pct - Math.rint(pct)) < 1e-6 ? String.valueOf((long) Math.rint(pct)) : String.format(java.util.Locale.FRANCE, "%.1f", pct);
	}
}
