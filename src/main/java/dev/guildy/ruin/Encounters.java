package dev.guildy.ruin;

import dev.guildy.Guildy;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.ruin.RuinConfig.AbilityDef;
import dev.guildy.ruin.RuinConfig.RuinDef;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.ConversionType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Ruin fights: awakening (at the climax of an incantation ritual, see {@link Incantations}, or in the "proximity" mode when
 * a survival player approaches the guardian), boss bar, arena leash, abilities, reset when nobody is around, cancellation
 * when the guardian is unloaded, rewards on its death. The ruin's threat level ("Menace") makes the guardian stronger at
 * each return; a guardian killed too fast refuses to die once and rises again, enraged ({@link Resurrection}). Runs once
 * per second (the animations, the resurrection and the aura of a risen guardian every tick); every loop is bounded by the
 * players online and the running fights.
 */
public final class Encounters {
	private static final Map<String, Encounter> ACTIVE = new LinkedHashMap<>();
	private static final Map<UUID, Encounter> BY_MOB = new HashMap<>();
	private static final Map<UUID, Long> HINTS = new HashMap<>();
	/** Player → last dormant ruin they were seen in ("scroll" mode hint shown when they enter it). */
	private static final Map<UUID, Presence> PRESENCE = new HashMap<>();
	private static final SplittableRandom RANDOM = new SplittableRandom();
	private static final int HINT_INTERVAL_TICKS = 60 * 20;
	/** A player who left a dormant ruin for this long sees its hint again when coming back. */
	private static final int PRESENCE_FORGET_TICKS = 2 * 60 * 20;
	/** Smallest particle budget of one resurrection per tick when several run at once. */
	private static final int MIN_RISING_SHARE = 150;
	private static long seconds;

	private record Presence(String ruinKey, long seen) {
	}

	private Encounters() {
	}

	static void init() {
		Scheduler.every("ruin.encounters", 20, 13, Encounters::tickSecond);
		Scheduler.every("ruin.encounters.fast", 1, 0, Encounters::tickFast);
		// A guardian killed too fast refuses to die (its death is cancelled: no loot, no kill) and rises again.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
				BY_MOB.isEmpty() || Safe.get("ruin.resurrection", () -> allowDeath(entity, source), true));
		// Nothing hurts a guardian while it rises (creative players and /damage included, /kill excepted).
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				BY_MOB.isEmpty() || Safe.get("ruin.rising.damage", () -> allowDamage(entity, source), true));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> Safe.run("ruin.death", () -> onDeath(entity, source)));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!BY_MOB.isEmpty()) {
				Safe.run("ruin.damage", () -> onDamage(entity, source));
			}
		});
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, params) ->
				Safe.run("ruin.conversion", () -> onConversion(previous, converted, params)));
		// Restart / unload safety: ruin mobs are never kept without their fight; leftovers are refused at load.
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, fromDisk) -> Safe.get("ruin.load", () -> allowLoad(entity), true));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> Safe.run("ruin.load", () -> onEntityLoad(entity)));
	}

	// ================================================================== queries

	/** Running fight of a ruin, or null. */
	static @Nullable Encounter active(RuinRecord ruin) {
		return ACTIVE.get(ruin.key);
	}

	public static boolean isActive(RuinRecord ruin) {
		return ACTIVE.containsKey(ruin.key);
	}

	/** The guardian of a running fight (loaded), or null. */
	public static @Nullable Mob boss(RuinRecord ruin) {
		Encounter encounter = ACTIVE.get(ruin.key);
		MinecraftServer server = GuildyServer.server();
		if (encounter == null || server == null) {
			return null;
		}
		ServerLevel level = server.getLevel(encounter.dimension);
		return level == null ? null : encounter.bossEntity(level);
	}

	/** Loaded escort mobs of a running fight. */
	public static List<Mob> minions(RuinRecord ruin) {
		Encounter encounter = ACTIVE.get(ruin.key);
		MinecraftServer server = GuildyServer.server();
		if (encounter == null || server == null) {
			return List.of();
		}
		ServerLevel level = server.getLevel(encounter.dimension);
		return level == null ? List.of() : encounter.minionEntities(level);
	}

	/** Players seeing the boss bar of a running fight. */
	public static Collection<ServerPlayer> barViewers(RuinRecord ruin) {
		Encounter encounter = ACTIVE.get(ruin.key);
		return encounter == null ? List.of() : Collections.unmodifiableCollection(encounter.bar.getPlayers());
	}

	public static int count() {
		return ACTIVE.size();
	}

	/** The guardian of this ruin is rising from the dead right now. */
	public static boolean rising(RuinRecord ruin) {
		Encounter encounter = ACTIVE.get(ruin.key);
		return encounter != null && encounter.rising != null;
	}

	/** The guardian of this ruin came back from the dead in the running fight. */
	public static boolean risen(RuinRecord ruin) {
		Encounter encounter = ACTIVE.get(ruin.key);
		return encounter != null && encounter.risen;
	}

	/** Title of the boss bar of a running fight, or null. */
	public static @Nullable Component barTitle(RuinRecord ruin) {
		Encounter encounter = ACTIVE.get(ruin.key);
		return encounter == null ? null : encounter.bar.getName();
	}

	/** Colour of the boss bar of a running fight, or null. */
	public static BossEvent.@Nullable BossBarColor barColor(RuinRecord ruin) {
		Encounter encounter = ACTIVE.get(ruin.key);
		return encounter == null ? null : encounter.bar.getColor();
	}

	/** Threat level of the running fight, else the one the next guardian will have. */
	public static int threat(RuinRecord ruin) {
		Encounter encounter = ACTIVE.get(ruin.key);
		return encounter != null ? encounter.threat : threatLevel(ruin);
	}

	/** Threat level of the next guardian of a ruin (capped by the config, I when the escalation is disabled). */
	public static int threatLevel(RuinRecord ruin) {
		RuinConfig.EscalationSettings e = RuinModule.config().escalation;
		return ThreatRules.level(ruin.threat, ruin.timesCleared, e.maxLevel, e.enabled);
	}

	/** "Menace III", coloured by level. */
	public static String threatLabel(int level) {
		return Lang.raw("ruin.threat", ThreatRules.roman(level), ThreatRules.color(level, RuinModule.config().escalation.maxLevel));
	}

	/** Admin: sets the threat level of a ruin (applies from the next awakening). @return the level set */
	public static int setThreat(RuinRecord ruin, int level) {
		ruin.threat = Math.max(1, Math.min(RuinModule.config().escalation.maxLevel, level));
		RuinIndex.markDirty();
		return ruin.threat;
	}

	/** Fast-kill window (seconds) of a ruin's guardian at a threat level, 0 when it never rises. */
	public static double fastKillSeconds(RuinDef def, int threat) {
		RuinConfig.ResurrectionSettings r = RuinModule.config().resurrection;
		if (!r.enabled) {
			return 0;
		}
		return ThreatRules.fastKillSeconds(def.fastKillSeconds, r.fastKillSeconds, r.fastKillPerTier, r.fastKillPerThreat, def.tier, threat);
	}

	/**
	 * Seconds left in the fast-kill window of the running fight (0 = closed or already risen), -1 without a fight.
	 */
	public static long resurrectionSecondsLeft(RuinRecord ruin) {
		Encounter encounter = ACTIVE.get(ruin.key);
		if (encounter == null) {
			return -1;
		}
		return encounter.risen || encounter.rising != null ? 0 : ThreatRules.secondsLeft(Scheduler.ticks() - encounter.startedTick, encounter.fastKillTicks);
	}

	/** Current in-game day (overworld clock / 24000). */
	public static long day(MinecraftServer server) {
		return Math.floorDiv(server.overworld().getOverworldClockTime(), 24000L);
	}

	// ================================================================== loop

	private static void tickSecond() {
		MinecraftServer server = GuildyServer.server();
		if (server == null) {
			return;
		}
		seconds++;
		RuinConfig cfg = RuinModule.config();
		for (Encounter encounter : new ArrayList<>(ACTIVE.values())) {
			Safe.run("ruin.encounter", () -> update(server, encounter, cfg));
		}
		for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
			Safe.run("ruin.detect", () -> detect(server, player, cfg));
		}
		if (seconds % 30 == 0) {
			rearmAll(server);
		}
	}

	private static void detect(MinecraftServer server, ServerPlayer player, RuinConfig cfg) {
		if (player.isSpectator() || !player.isAlive()) {
			return;
		}
		ServerLevel level = player.level();
		RuinRecord ruin = RuinIndex.containing(level.dimension(), player.getX(), player.getY(), player.getZ(), cfg.encounter.activationMargin);
		if (ruin == null) {
			return;
		}
		RuinDef def = def(ruin);
		if (ruin.state == RuinState.CLEARED) {
			long now = day(server);
			if (RuinRules.rearmed(ruin.clearedDay, now, def.respawnDays)) {
				rearm(ruin);
			} else {
				hint(player, ruin, def, RuinRules.daysLeft(ruin.clearedDay, now, def.respawnDays));
				return;
			}
		}
		if (ruin.state != RuinState.DORMANT || ACTIVE.containsKey(ruin.key) || Incantations.ritualRunning(ruin)) {
			return;
		}
		if (!SummonRules.proximityAwakens(cfg.summon.mode)) {
			// "scroll" mode: the guardian sleeps until an incantation scroll is read near it.
			if (cfg.bossesEnabled) {
				presenceHint(player, ruin, def);
			}
			return;
		}
		if (player.isCreative()) {
			return;
		}
		BlockPos spawn = ruin.boss != null ? ruin.boss : ruin.center();
		boolean inActivation = def.activationRadius <= 0
				|| player.distanceToSqr(Vec3.atBottomCenterOf(spawn)) <= (double) def.activationRadius * def.activationRadius;
		boolean enabled = cfg.bossesEnabled && level.getDifficulty() != Difficulty.PEACEFUL
				&& ACTIVE.size() + Incantations.ritualCount() < cfg.encounter.maxActive;
		if (RuinRules.decide(RuinState.DORMANT, enabled, inActivation, 0, def.resetSeconds, false, false) == RuinRules.Action.AWAKEN) {
			awaken(level, ruin, def, false);
		}
	}

	private static void hint(ServerPlayer player, RuinRecord ruin, RuinDef def, long daysLeft) {
		long now = Scheduler.ticks();
		Long next = HINTS.get(player.getUUID());
		if (next != null && now < next && now >= next - HINT_INTERVAL_TICKS) {
			return;
		}
		HINTS.put(player.getUUID(), now + HINT_INTERVAL_TICKS);
		String name = Lang.colorize(def.name);
		Msg.actionBar(player, daysLeft < 0 ? Lang.get("ruin.cleared.never", name) : Lang.get("ruin.cleared.hint", name, Math.max(1, daysLeft)));
	}

	/** "Une présence dort ici…": title and chat when the player enters a dormant ruin (again after a while away). */
	private static void presenceHint(ServerPlayer player, RuinRecord ruin, RuinDef def) {
		long now = Scheduler.ticks();
		Presence last = PRESENCE.get(player.getUUID());
		PRESENCE.put(player.getUUID(), new Presence(ruin.key, now));
		if (last != null && last.ruinKey().equals(ruin.key) && now - last.seen() <= PRESENCE_FORGET_TICKS && now >= last.seen()) {
			return;
		}
		String name = Lang.colorize(def.name);
		Msg.title(player, Component.literal(name), Lang.get("ruin.summon.hint.subtitle"), 10, 60, 20);
		Msg.actionBar(player, "ruin.summon.hint.actionbar");
		Msg.send(player, "ruin.summon.hint.chat", name, Lang.colorize(def.boss.name));
		Msg.send(player, "ruin.summon.hint.threat", threatLabel(threatLevel(ruin)));
	}

	/** A defeated guardian due back is re-armed now. @return true when the ruin is dormant again */
	static boolean rearmIfDue(MinecraftServer server, RuinRecord ruin) {
		if (ruin.state == RuinState.CLEARED && RuinRules.rearmed(ruin.clearedDay, day(server), def(ruin).respawnDays)) {
			rearm(ruin);
			return true;
		}
		return false;
	}

	private static void rearmAll(MinecraftServer server) {
		long now = day(server);
		for (RuinRecord ruin : RuinIndex.all()) {
			if (ruin.state == RuinState.CLEARED && RuinRules.rearmed(ruin.clearedDay, now, def(ruin).respawnDays)) {
				rearm(ruin);
			}
		}
	}

	private static void rearm(RuinRecord ruin) {
		ruin.state = RuinState.DORMANT;
		RuinIndex.markDirty();
	}

	static RuinDef def(RuinRecord ruin) {
		return RuinModule.config().def(ruin.ruinId, ruin.isNether());
	}

	// ================================================================== awakening

	/**
	 * Spawns the guardian and its escort. @return null on success, else the lang key of the reason (admin command)
	 *
	 * @param forced admin command: also re-arms a defeated guardian, ignores the fight limit and cancels a running
	 *               incantation ritual (its scroll is refunded)
	 */
	public static @Nullable String awaken(ServerLevel level, RuinRecord ruin, @Nullable RuinDef forcedDef, boolean forced) {
		if (ACTIVE.containsKey(ruin.key)) {
			return "ruin.admin.already_awake";
		}
		if (!level.dimension().identifier().toString().equals(ruin.dimension)) {
			return "ruin.admin.wrong_dimension";
		}
		if (Incantations.ritualRunning(ruin)) {
			if (!forced) {
				return "ruin.summon.fail.ritual";
			}
			Incantations.cancelRitual(ruin);
		}
		if (forced && ruin.state == RuinState.CLEARED) {
			ruin.state = RuinState.DORMANT;
		}
		RuinDef def = forcedDef != null ? forcedDef : def(ruin);
		RuinConfig cfg = RuinModule.config();
		BlockPos spawn = spawnPoint(level, ruin, def);
		if (spawn == null) {
			return "ruin.admin.no_spawn";
		}
		if (!loaded(level, spawn)) {
			return "ruin.admin.not_loaded";
		}
		ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(Lang.colorize(def.boss.name)), barColor(def.bossBarColor),
				BossEvent.BossBarOverlay.NOTCHED_10);
		bar.setDarkenScreen(cfg.encounter.darkenSky);
		int threat = threatLevel(ruin);
		ThreatRules.Scaling scaling = ThreatRules.scaling(cfg.escalation, threat);
		long window = ThreatRules.windowTicks(fastKillSeconds(def, threat));
		Encounter encounter = new Encounter(ruin, def, level.dimension(), spawn, bar, Scheduler.ticks(), cfg.encounter.firstAbilitySeconds, scaling,
				cfg.escalation.enabled ? cfg.escalation.enragePerLevel : 0, cfg.escalation.maxEnrageThreshold, window);
		Mob boss = BossFactory.spawnBoss(level, def.boss, spawn, scaling, mob -> {
			encounter.boss = mob.getUUID();
			BY_MOB.put(mob.getUUID(), encounter);
			return true;
		});
		if (boss == null) {
			if (encounter.boss != null) {
				BY_MOB.remove(encounter.boss);
			}
			Guildy.LOGGER.warn("[ruins] could not spawn the guardian '{}' of {}", def.boss.entity, ruin.key);
			return "ruin.admin.spawn_failed";
		}
		ACTIVE.put(ruin.key, encounter);
		ruin.state = RuinState.AWAKE;
		List<ServerPlayer> present = present(level, ruin, cfg.encounter.presenceMargin);
		int fighters = 0;
		for (ServerPlayer player : present) {
			if (!player.isCreative()) {
				fighters++;
			}
		}
		spawnMinions(level, encounter, RuinRules.minionCount(def.minions.base, def.minions.perExtraPlayer, def.minions.max, Math.max(1, fighters))
				+ scaling.extraMinions());
		awakenEffects(level, boss);
		Component title = Component.literal(Lang.colorize(def.name));
		Component subtitle = Lang.get("ruin.awaken.subtitle", Lang.colorize(def.boss.name));
		String label = threatLabel(threat);
		for (ServerPlayer player : present) {
			Msg.title(player, title, subtitle, 10, 60, 20);
			Msg.send(player, "ruin.awaken.chat", Lang.colorize(def.boss.name), Lang.colorize(def.name));
			if (window > 0) {
				Msg.send(player, "ruin.awaken.threat.chrono", label, ThreatRules.clock(window / 20));
			} else {
				Msg.send(player, "ruin.awaken.threat", label);
			}
		}
		syncBar(level, encounter, boss, present);
		return null;
	}

	private static @Nullable BlockPos spawnPoint(ServerLevel level, RuinRecord ruin, RuinDef def) {
		if (ruin.boss != null) {
			return ruin.boss;
		}
		// No boss marker (template without one): a safe spot near the middle of the ruin.
		BlockPos center = ruin.center();
		if (!level.isLoaded(center)) {
			return null;
		}
		for (int dy = 0; dy <= Math.max(4, (ruin.maxY - ruin.minY) / 2 + 2); dy++) {
			for (int sign : new int[] {1, -1}) {
				BlockPos around = center.above(dy * sign);
				BlockPos spot = BossFactory.standable(level, around, def.boss.entity, 6);
				if (spot != null) {
					return spot;
				}
			}
		}
		return null;
	}

	private static void spawnMinions(ServerLevel level, Encounter encounter, int count) {
		List<BlockPos> markers = new ArrayList<>(encounter.ruin.minions);
		Collections.shuffle(markers, new java.util.Random(RANDOM.nextLong()));
		for (int i = 0; i < count; i++) {
			BlockPos base;
			if (!markers.isEmpty()) {
				base = markers.get(i % markers.size());
			} else {
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				double dist = 3 + RANDOM.nextDouble() * 4;
				base = BlockPos.containing(encounter.spawn.getX() + 0.5 + Math.cos(angle) * dist, encounter.spawn.getY(),
						encounter.spawn.getZ() + 0.5 + Math.sin(angle) * dist);
			}
			summon(level, encounter, base);
		}
	}

	/** One escort mob near {@code pos} (cap: the living escort cap, raised by the threat level). */
	private static @Nullable Mob summon(ServerLevel level, Encounter encounter, BlockPos pos) {
		if (encounter.def.minions.types.isEmpty() || encounter.minionEntities(level).size() >= encounter.maxAlive()) {
			return null;
		}
		String probe = encounter.def.minions.types.keySet().iterator().next();
		BlockPos spot = BossFactory.standable(level, pos, probe, 2);
		if (spot == null) {
			spot = pos;
		}
		Mob mob = BossFactory.spawnMinion(level, encounter.def.minions, spot, RANDOM, m -> {
			encounter.minions.add(m.getUUID());
			BY_MOB.put(m.getUUID(), encounter);
			return true;
		});
		if (mob == null) {
			cleanupMissing(encounter);
		}
		return mob;
	}

	/** Forgets escort UUIDs whose spawn failed (registered before the entity was refused). */
	private static void cleanupMissing(Encounter encounter) {
		MinecraftServer server = GuildyServer.server();
		ServerLevel level = server == null ? null : server.getLevel(encounter.dimension);
		if (level == null) {
			return;
		}
		encounter.minions.removeIf(uuid -> {
			Entity entity = level.getEntity(uuid);
			if (entity == null && level.isLoaded(encounter.spawn)) {
				BY_MOB.remove(uuid);
				return true;
			}
			return false;
		});
	}

	private static void awakenEffects(ServerLevel level, Mob boss) {
		RuinFx.sound(level, boss.position(), SoundEvents.WITHER_SPAWN, 0.7F, 0.7F);
		RuinFx.sound(level, boss.position(), SoundEvents.AMBIENT_CAVE, 2.0F, 0.8F);
		RuinFx.burst(level, ParticleTypes.LARGE_SMOKE, boss.position().add(0, 1, 0), 30, 0.6, 0.05);
		RuinFx.play(level, (lvl, age) -> {
			if (!boss.isAlive()) {
				return false;
			}
			Vec3 c = boss.position();
			RuinFx.helix(lvl, ParticleTypes.SOUL_FIRE_FLAME, c, 1.4, age, boss.getBbHeight() + 1.5, 20);
			RuinFx.helix(lvl, ParticleTypes.SOUL, c, 2.0, age + 10, boss.getBbHeight() + 1.5, 20);
			if (age % 10 == 0) {
				RuinFx.ring(lvl, ParticleTypes.SMOKE, c.add(0, 0.1, 0), 2.5, 16, 0.1);
			}
			return age < 40;
		});
	}

	// ================================================================== update

	private static void update(MinecraftServer server, Encounter encounter, RuinConfig cfg) {
		ServerLevel level = server.getLevel(encounter.dimension);
		if (level == null) {
			end(encounter, null, false);
			return;
		}
		Mob boss = encounter.bossEntity(level);
		List<ServerPlayer> present = present(level, encounter.ruin, cfg.encounter.presenceMargin);
		encounter.secondsAlone = present.isEmpty() ? encounter.secondsAlone + 1 : 0;
		RuinRules.Action action = RuinRules.decide(RuinState.AWAKE, true, false, encounter.secondsAlone, encounter.def.resetSeconds,
				boss != null, false);
		if (action == RuinRules.Action.CANCEL) {
			Guildy.LOGGER.debug("[ruins] fight of {} cancelled (guardian unloaded)", encounter.ruin.key);
			end(encounter, level, false);
			return;
		}
		if (action == RuinRules.Action.RESET) {
			end(encounter, level, true);
			return;
		}
		if (encounter.rising != null) {
			// Frozen while it rises (moved every tick by the resurrection): only the viewers of the bar change.
			syncBar(level, encounter, boss, present);
			return;
		}
		leash(level, encounter, boss);
		syncBar(level, encounter, boss, present);
		float ratio = boss.getHealth() / Math.max(1.0F, boss.getMaxHealth());
		if (encounter.enrage != null && !encounter.enraged && ratio <= encounter.enrageBelow) {
			encounter.enraged = true;
			BossAbilities.enrage(level, boss, encounter.enrage, lvl -> {
				Component message = Lang.get("ruin.enrage", Lang.colorize(encounter.def.boss.name));
				for (ServerPlayer player : present) {
					Msg.actionBar(player, message);
				}
			});
		}
		useAbility(level, encounter, boss, present, cfg, ratio);
	}

	private static void useAbility(ServerLevel level, Encounter encounter, Mob boss, List<ServerPlayer> present, RuinConfig cfg, float ratio) {
		long now = Scheduler.ticks();
		if (now < encounter.nextAbilityTick || encounter.abilities.isEmpty()) {
			return;
		}
		double range = cfg.encounter.abilityRange;
		List<ServerPlayer> targets = new ArrayList<>();
		for (ServerPlayer player : present) {
			if (BossAbilities.valid(player, level) && player.distanceToSqr(boss) <= range * range) {
				targets.add(player);
			}
		}
		if (targets.isEmpty()) {
			return;
		}
		List<Integer> ready = RuinRules.readyAbilities(now, encounter.readyAt, encounter.healthBelow, ratio, encounter.abilityOffset++);
		for (int index : ready) {
			AbilityDef ability = encounter.abilities.get(index);
			AbilityDef scaled = ability.scaled(encounter.damageFactor());
			boolean used = Safe.get("ruin.ability." + ability.type,
					() -> BossAbilities.use(level, encounter, boss, scaled, targets, RANDOM, Encounters::summon), false);
			if (used) {
				encounter.readyAt[index] = now + RuinRules.cooldownTicks(ability.cooldownSeconds, encounter.cooldownFactor());
				encounter.nextAbilityTick = now + cfg.encounter.abilityGlobalCooldownSeconds * 20L;
				return;
			}
		}
	}

	/**
	 * Uses an ability now in a running fight, ignoring its cooldown (admin preview, GameTests).
	 *
	 * @return false when there is no fight, no target in reach or the ability cannot be used right now
	 */
	public static boolean forceAbility(RuinRecord ruin, AbilityDef ability) {
		Encounter encounter = ACTIVE.get(ruin.key);
		MinecraftServer server = GuildyServer.server();
		ServerLevel level = encounter == null || server == null ? null : server.getLevel(encounter.dimension);
		Mob boss = level == null ? null : encounter.bossEntity(level);
		if (boss == null || encounter.rising != null) {
			return false;
		}
		if ("enrage".equals(ability.type)) {
			encounter.enraged = true;
			BossAbilities.enrage(level, boss, ability, lvl -> {
			});
			return true;
		}
		double range = RuinModule.config().encounter.abilityRange;
		List<ServerPlayer> targets = new ArrayList<>();
		for (ServerPlayer player : present(level, ruin, RuinModule.config().encounter.presenceMargin)) {
			if (BossAbilities.valid(player, level) && player.distanceToSqr(boss) <= range * range) {
				targets.add(player);
			}
		}
		return !targets.isEmpty() && BossAbilities.use(level, encounter, boss, ability.scaled(encounter.damageFactor()), targets, RANDOM,
				Encounters::summon);
	}

	/** Lured away from its arena: the guardian (and a straying escort mob) is brought back. */
	private static void leash(ServerLevel level, Encounter encounter, Mob boss) {
		Vec3 home = Vec3.atBottomCenterOf(encounter.spawn);
		double leash = encounter.def.leashRadius;
		if (boss.position().distanceToSqr(home) > leash * leash) {
			RuinFx.burst(level, ParticleTypes.REVERSE_PORTAL, boss.position().add(0, 1, 0), 40, 0.4, 0.2);
			boss.teleportTo(home.x, home.y, home.z);
			boss.getNavigation().stop();
			boss.setTarget(null);
			RuinFx.burst(level, ParticleTypes.PORTAL, home.add(0, 1, 0), 50, 0.5, 0.5);
			RuinFx.sound(level, home, SoundEvents.ENDERMAN_TELEPORT, 1.2F, 0.5F);
		}
		double minionLeash = leash * 1.5;
		for (Mob minion : encounter.minionEntities(level)) {
			if (minion.position().distanceToSqr(home) > minionLeash * minionLeash) {
				BlockPos spot = BossFactory.standable(level, encounter.spawn, encounter.def.minions.types.keySet().iterator().next(), 4);
				if (spot != null) {
					minion.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
					minion.getNavigation().stop();
				}
			}
		}
	}

	private static void syncBar(ServerLevel level, Encounter encounter, @Nullable Mob boss, List<ServerPlayer> present) {
		if (boss != null) {
			float health = boss.getHealth();
			float max = Math.max(1.0F, boss.getMaxHealth());
			encounter.bar.setName(barName(encounter, health, max));
			encounter.bar.setProgress(Mth.clamp(health / max, 0.0F, 1.0F));
		}
		for (ServerPlayer player : new ArrayList<>(encounter.bar.getPlayers())) {
			if (!present.contains(player)) {
				encounter.bar.removePlayer(player);
			}
		}
		for (ServerPlayer player : present) {
			encounter.bar.addPlayer(player);
		}
	}

	/**
	 * "Seigneur des Cryptes — Menace III — 220/220 ❤", then "| Résurrection possible : 0:32" while the fast-kill window is
	 * open; "Enragé" once risen; "revient d'entre les morts…" while it rises.
	 */
	private static Component barName(Encounter encounter, float health, float max) {
		String name = Lang.colorize(encounter.def.boss.name);
		String threat = threatLabel(encounter.threat);
		if (encounter.rising != null) {
			return Lang.get("ruin.bar.rising", name, threat);
		}
		String title = Lang.raw(encounter.risen ? "ruin.bar.name.risen" : "ruin.bar.name", name, threat);
		Component bar = Lang.get("ruin.bar", title, (int) Math.ceil(health), (int) Math.ceil(max));
		if (!encounter.risen) {
			long left = ThreatRules.secondsLeft(Scheduler.ticks() - encounter.startedTick, encounter.fastKillTicks);
			if (left > 0) {
				return Lang.get("ruin.bar.chrono", bar.getString(), ThreatRules.clock(left));
			}
		}
		return bar;
	}

	/** Non-spectator players of the ruin's dimension inside its box inflated by {@code margin}. */
	static List<ServerPlayer> present(ServerLevel level, RuinRecord ruin, int margin) {
		List<ServerPlayer> list = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.isAlive() && ruin.contains(player.getX(), player.getY(), player.getZ(), margin)) {
				list.add(player);
			}
		}
		return list;
	}

	// ================================================================== end of a fight

	/**
	 * Ends a fight without victory: the guardian and its escort vanish (loaded ones now, unloaded ones are refused at
	 * load), the ruin sleeps again.
	 *
	 * @param announce reset by absence: the players still close get a message
	 */
	private static void end(Encounter encounter, @Nullable ServerLevel level, boolean announce) {
		ACTIVE.remove(encounter.ruin.key);
		if (encounter.ruin.state == RuinState.AWAKE) {
			encounter.ruin.state = RuinState.DORMANT;
		}
		if (level != null) {
			Mob boss = encounter.bossEntity(level);
			if (boss != null) {
				RuinFx.burst(level, ParticleTypes.LARGE_SMOKE, boss.position().add(0, 1, 0), 30, 0.5, 0.05);
				RuinFx.burst(level, ParticleTypes.SOUL, boss.position().add(0, 1, 0), 15, 0.5, 0.05);
				boss.discard();
			}
			for (Mob minion : encounter.minionEntities(level)) {
				RuinFx.burst(level, ParticleTypes.POOF, minion.position().add(0, 0.5, 0), 6, 0.3, 0.02);
				minion.discard();
			}
			if (announce) {
				for (ServerPlayer player : present(level, encounter.ruin, RuinModule.config().encounter.presenceMargin * 2)) {
					Msg.send(player, "ruin.reset", Lang.colorize(encounter.def.boss.name));
				}
			}
		}
		forget(encounter);
	}

	private static void forget(Encounter encounter) {
		ACTIVE.remove(encounter.ruin.key, encounter);
		if (encounter.boss != null) {
			BY_MOB.remove(encounter.boss);
		}
		for (UUID uuid : encounter.minions) {
			BY_MOB.remove(uuid);
		}
		encounter.bar.removeAllPlayers();
	}

	/** Admin reset: ends the fight (or cancels the ritual, scroll refunded) and re-arms a defeated guardian. */
	public static void reset(RuinRecord ruin) {
		Incantations.cancelRitual(ruin);
		Encounter encounter = ACTIVE.get(ruin.key);
		if (encounter != null) {
			MinecraftServer server = GuildyServer.server();
			end(encounter, server == null ? null : server.getLevel(encounter.dimension), false);
		}
		ruin.state = RuinState.DORMANT;
		RuinIndex.markDirty();
	}

	/**
	 * Server stopping: every ritual is cancelled (scrolls refunded) and every fight too (the guardians are removed before
	 * the chunks are saved).
	 */
	static void stopAll() {
		Safe.run("ruin.ritual.stop", Incantations::stopAll);
		PRESENCE.clear();
		MinecraftServer server = GuildyServer.server();
		for (Encounter encounter : new ArrayList<>(ACTIVE.values())) {
			Safe.run("ruin.stop", () -> end(encounter, server == null ? null : server.getLevel(encounter.dimension), false));
		}
		ACTIVE.clear();
		BY_MOB.clear();
		HINTS.clear();
		RuinFx.clear();
	}

	// ================================================================== events

	private static void onDeath(LivingEntity entity, DamageSource source) {
		Encounter encounter = BY_MOB.get(entity.getUUID());
		if (encounter == null || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		if (entity.getUUID().equals(encounter.boss) && entity instanceof Mob boss) {
			victory(level, encounter, boss, source);
		} else if (encounter.minions.remove(entity.getUUID())) {
			BY_MOB.remove(entity.getUUID());
			encounter.kills++;
			encounter.addFallen(entity.position());
		}
	}

	private static void onDamage(LivingEntity entity, DamageSource source) {
		Encounter encounter = BY_MOB.get(entity.getUUID());
		if (encounter != null && entity.getUUID().equals(encounter.boss) && source.getEntity() instanceof ServerPlayer player) {
			encounter.addParticipant(player);
		}
	}

	private static void victory(ServerLevel level, Encounter encounter, Mob boss, DamageSource source) {
		RuinConfig cfg = RuinModule.config();
		if (source.getEntity() instanceof ServerPlayer killer) {
			encounter.addParticipant(killer);
		}
		List<ServerPlayer> present = present(level, encounter.ruin, cfg.encounter.presenceMargin);
		for (ServerPlayer player : present) {
			if (!player.isCreative()) {
				encounter.addParticipant(player);
			}
		}
		ACTIVE.remove(encounter.ruin.key);
		RuinRecord ruin = encounter.ruin;
		ruin.state = RuinState.CLEARED;
		ruin.clearedDay = day(level.getServer());
		// Each victory raises the ruin's threat: its guardian comes back stronger.
		// (Escalation disabled: the level already reached is kept as it is.)
		int previous = ThreatRules.stored(ruin.threat, ruin.timesCleared);
		int stored = Math.min(previous, Math.max(1, cfg.escalation.maxLevel));
		int threat = cfg.escalation.enabled ? ThreatRules.next(stored, cfg.escalation.maxLevel) : previous;
		ruin.threat = threat;
		ruin.timesCleared++;
		ruin.lastVictors = String.join(", ", encounter.names.values());
		RuinIndex.markDirty();

		Safe.run("ruin.loot", () -> RuinLoot.drop(level, encounter, boss, RANDOM));
		Safe.run("ruin.xp", () -> RuinLoot.giveXp(encounter, cfg.encounter.minXpPerGuild));
		for (Mob minion : encounter.minionEntities(level)) {
			RuinFx.burst(level, ParticleTypes.SOUL, minion.position().add(0, 0.8, 0), 8, 0.3, 0.05);
			minion.discard();
		}
		victoryEffects(level, boss);
		String bossName = Lang.colorize(encounter.def.boss.name);
		String ruinName = Lang.colorize(encounter.def.name);
		boolean raised = threat > stored;
		for (ServerPlayer player : present) {
			if (encounter.risen) {
				Msg.title(player, Lang.get("ruin.risen.victory.title"), Lang.get("ruin.risen.victory.subtitle", bossName), 10, 80, 20);
				Msg.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.8F);
			} else {
				Msg.title(player, Lang.get("ruin.victory.title"), Lang.get("ruin.victory.subtitle", bossName), 10, 70, 20);
			}
			Msg.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
			if (cfg.escalation.enabled) {
				Msg.send(player, raised ? "ruin.victory.threat" : "ruin.victory.threat.max", ruinName, threatLabel(threat));
			}
		}
		if (cfg.encounter.announce) {
			String names = encounter.names.isEmpty() ? Lang.raw("ruin.victory.someone") : String.join(", ", encounter.names.values());
			level.getServer().getPlayerList().broadcastSystemMessage(Msg.prefixed(Lang.get(encounter.risen ? "ruin.risen.victory.announce"
					: "ruin.victory.announce", names, bossName, ruinName)), false);
		}
		forget(encounter);
	}

	private static void victoryEffects(ServerLevel level, Mob boss) {
		Vec3 at = boss.position();
		RuinFx.sound(level, at, SoundEvents.WITHER_DEATH, 0.6F, 1.2F);
		RuinFx.burst(level, ParticleTypes.TOTEM_OF_UNDYING, at.add(0, 1, 0), 60, 0.6, 0.4);
		RuinFx.play(level, (lvl, age) -> {
			RuinFx.helix(lvl, ParticleTypes.END_ROD, at, 1.0 + age * 0.04, age, 4.0, 30);
			return age < 30;
		});
	}

	private static void onConversion(Mob previous, Mob converted, ConversionParams params) {
		Encounter encounter = BY_MOB.get(previous.getUUID());
		if (encounter == null) {
			return;
		}
		if (params.type() != ConversionType.SINGLE) {
			// Split children (magma cubes) are not part of the fight: refused at load (tagged, untracked).
			return;
		}
		// The converted mob is added right after this event: track it first so that the load check accepts it.
		BY_MOB.remove(previous.getUUID());
		BY_MOB.put(converted.getUUID(), encounter);
		converted.addTag(BossFactory.TAG);
		converted.setPersistenceRequired();
		if (previous.getUUID().equals(encounter.boss)) {
			encounter.boss = converted.getUUID();
			converted.addTag(BossFactory.TAG_BOSS);
			float ratio = previous.getHealth() / Math.max(1.0F, previous.getMaxHealth());
			BossFactory.applyBoss(converted, encounter.def.boss);
			BossFactory.applyThreat(converted, encounter.scaling);
			if (encounter.risen) {
				BossFactory.applyRisen(converted, encounter.risenScale, encounter.risenSpeed, encounter.risenDamage);
			}
			converted.setHealth(Math.max(1.0F, ratio * converted.getMaxHealth()));
		} else if (encounter.minions.remove(previous.getUUID())) {
			encounter.minions.add(converted.getUUID());
		}
	}

	// ================================================================== resurrection

	/**
	 * ALLOW_DEATH: a guardian killed less than its fast-kill window after it appeared, and not risen yet, refuses to die
	 * (nothing dropped, no kill counted) and starts to rise. /kill and the void still kill it.
	 */
	private static boolean allowDeath(LivingEntity entity, DamageSource source) {
		Encounter encounter = BY_MOB.get(entity.getUUID());
		if (encounter == null || !entity.getUUID().equals(encounter.boss) || !(entity instanceof Mob boss)
				|| !(entity.level() instanceof ServerLevel level)) {
			return true;
		}
		if (encounter.rising != null && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			boss.setHealth(1.0F);
			return false;
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		RuinConfig cfg = RuinModule.config();
		long alive = Scheduler.ticks() - encounter.startedTick;
		if (!ThreatRules.resurrects(cfg.resurrection.enabled && cfg.bossesEnabled, encounter.risen, alive, encounter.fastKillTicks)) {
			return true;
		}
		boss.setHealth(1.0F);
		startRising(level, encounter, boss, source, alive, cfg);
		return false;
	}

	/** ALLOW_DAMAGE: a rising guardian takes no damage at all (/kill excepted). */
	private static boolean allowDamage(LivingEntity entity, DamageSource source) {
		Encounter encounter = BY_MOB.get(entity.getUUID());
		return encounter == null || encounter.rising == null || !entity.getUUID().equals(encounter.boss)
				|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	private static void startRising(ServerLevel level, Encounter encounter, Mob boss, DamageSource source, long alive, RuinConfig cfg) {
		if (source.getEntity() instanceof ServerPlayer killer) {
			encounter.addParticipant(killer);
		}
		Resurrection rising = new Resurrection(level, boss, encounter, cfg.resurrection);
		encounter.rising = rising;
		RitualFx fx = new RitualFx(level, rising.anchor, Math.max(400, cfg.resurrection.particleBudget), cfg.summon.viewDistance);
		Safe.run("ruin.rising.begin", () -> rising.begin(level, boss, fx));
		fx.flush();
		encounter.bar.setColor(BossEvent.BossBarColor.RED);
		encounter.bar.setName(barName(encounter, boss.getHealth(), boss.getMaxHealth()));
		encounter.bar.setProgress(0.0F);
		String bossName = Lang.colorize(encounter.def.boss.name);
		for (ServerPlayer player : present(level, encounter.ruin, cfg.encounter.presenceMargin)) {
			Msg.title(player, Lang.get("ruin.risen.title"), Lang.get("ruin.risen.subtitle", bossName), 5, 80, 15);
			Msg.send(player, "ruin.risen.chat", bossName, ThreatRules.clock((alive + 19) / 20), ThreatRules.clock(encounter.fastKillTicks / 20));
		}
		Guildy.LOGGER.debug("[ruins] the guardian of {} was killed in {} ticks: it rises again", encounter.ruin.key, alive);
	}

	/** Every tick: the resurrections running and the aura of the risen guardians. */
	private static void tickFast() {
		if (ACTIVE.isEmpty()) {
			return;
		}
		MinecraftServer server = GuildyServer.server();
		if (server == null) {
			return;
		}
		int rising = 0;
		for (Encounter encounter : ACTIVE.values()) {
			if (encounter.rising != null) {
				rising++;
			}
		}
		// One particle budget shared by every resurrection running (like the rituals).
		int share = rising == 0 ? 0 : Math.max(MIN_RISING_SHARE, RuinModule.config().resurrection.particleBudget / rising);
		for (Encounter encounter : new ArrayList<>(ACTIVE.values())) {
			if (encounter.rising != null || encounter.risen) {
				Safe.run("ruin.rising", () -> fast(server, encounter, share));
			}
		}
	}

	private static void fast(MinecraftServer server, Encounter encounter, int share) {
		ServerLevel level = server.getLevel(encounter.dimension);
		Mob boss = level == null ? null : encounter.bossEntity(level);
		if (boss == null) {
			// Unloaded or gone: the next second of update() cancels the fight.
			return;
		}
		Resurrection rising = encounter.rising;
		if (rising == null) {
			aura(level, boss);
			return;
		}
		RuinConfig cfg = RuinModule.config();
		boolean done = rising.age >= rising.duration + Resurrection.GRACE_TICKS;
		if (!done) {
			RitualFx fx = new RitualFx(level, rising.anchor, share, cfg.summon.viewDistance);
			try {
				done = rising.tick(level, boss, fx, cfg.resurrection);
			} finally {
				fx.flush();
			}
			encounter.bar.setProgress(Mth.clamp(boss.getHealth() / Math.max(1.0F, boss.getMaxHealth()), 0.0F, 1.0F));
		}
		if (done) {
			finishRising(level, encounter, boss, rising, cfg);
		}
	}

	/** Back from the dead: enraged, bigger, faster, stronger, a fresh escort, flash and shockwave. */
	private static void finishRising(ServerLevel level, Encounter encounter, Mob boss, Resurrection rising, RuinConfig cfg) {
		RuinConfig.ResurrectionSettings r = cfg.resurrection;
		encounter.rising = null;
		encounter.risen = true;
		encounter.enraged = true;
		encounter.risenCooldown = r.cooldownFactor;
		encounter.risenDamage = r.damageBonus;
		encounter.risenScale = rising.scale;
		encounter.risenSpeed = r.speedBonus;
		rising.release(boss);
		BossFactory.applyRisen(boss, encounter.risenScale, encounter.risenSpeed, encounter.risenDamage);
		boss.setHealth((float) Math.max(1.0, boss.getMaxHealth() * r.healthRatio));
		int amplifier = Math.min(4, (encounter.enrage == null ? 0 : encounter.enrage.amplifier) + 1);
		boss.addEffect(new MobEffectInstance(MobEffects.STRENGTH, MobEffectInstance.INFINITE_DURATION, amplifier, false, true));
		boss.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 160, 1, false, true));
		boss.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		String bossName = Lang.colorize(encounter.def.boss.name);
		boss.setCustomName(Lang.get("ruin.risen.name", bossName));
		boss.setCustomNameVisible(true);
		long now = Scheduler.ticks();
		for (int i = 0; i < encounter.readyAt.length; i++) {
			encounter.readyAt[i] = Math.min(encounter.readyAt[i], now + 40 + i * 20L);
		}
		encounter.nextAbilityTick = now + 30;
		encounter.bar.setColor(BossEvent.BossBarColor.RED);
		encounter.bar.setName(barName(encounter, boss.getHealth(), boss.getMaxHealth()));
		encounter.bar.setProgress(Mth.clamp(boss.getHealth() / Math.max(1.0F, boss.getMaxHealth()), 0.0F, 1.0F));
		RitualFx fx = new RitualFx(level, rising.anchor, Math.max(800, r.particleBudget), cfg.summon.viewDistance);
		Safe.run("ruin.rising.land", () -> rising.land(level, boss, fx, r));
		fx.flush();
		Safe.run("ruin.rising.minions", () -> risenEscort(level, encounter, boss, r.minions));
		List<ServerPlayer> present = present(level, encounter.ruin, cfg.encounter.presenceMargin);
		ServerPlayer nearest = null;
		for (ServerPlayer player : present) {
			Msg.title(player, Lang.get("ruin.risen.back.title"), Lang.get("ruin.risen.back.subtitle", bossName), 0, 60, 20);
			Msg.send(player, "ruin.risen.back.chat", bossName);
			if (BossAbilities.valid(player, level) && (nearest == null || player.distanceToSqr(boss) < nearest.distanceToSqr(boss))) {
				nearest = player;
			}
		}
		if (nearest != null) {
			boss.setTarget(nearest);
		}
	}

	/** A fresh wave of escort mobs around the risen guardian, each in a burst of soul fire. */
	private static void risenEscort(ServerLevel level, Encounter encounter, Mob boss, int count) {
		for (int i = 0; i < count; i++) {
			double angle = Math.PI * 2 * i / Math.max(1, count) + RANDOM.nextDouble() * 0.5;
			double dist = 3 + RANDOM.nextDouble() * 3;
			BlockPos around = BlockPos.containing(boss.getX() + Math.cos(angle) * dist, boss.getY(), boss.getZ() + Math.sin(angle) * dist);
			Mob mob = summon(level, encounter, around);
			if (mob == null) {
				continue;
			}
			RuinFx.burst(level, ParticleTypes.SOUL_FIRE_FLAME, mob.position().add(0, 0.3, 0), 16, 0.4, 0.04);
			RuinFx.burst(level, ParticleTypes.LARGE_SMOKE, mob.position().add(0, 0.8, 0), 8, 0.3, 0.02);
		}
	}

	/** Soul fire and embers around a risen guardian; it burns (fire resistance: the flames are only for show). */
	private static void aura(ServerLevel level, Mob boss) {
		long now = Scheduler.ticks();
		if (now % 20 == 0 && !boss.fireImmune()) {
			boss.setRemainingFireTicks(Math.max(boss.getRemainingFireTicks(), 60));
		}
		if (now % 2 != 0) {
			return;
		}
		Vec3 feet = boss.position();
		double width = Math.max(0.6, boss.getBbWidth());
		double height = Math.max(0.6, boss.getBbHeight());
		int age = (int) (now / 2 % 1000);
		RuinFx.helix(level, ParticleTypes.SOUL_FIRE_FLAME, feet, width * 0.9 + 0.3, age, height + 0.5, 16);
		RuinFx.helix(level, ParticleTypes.FLAME, feet, width * 0.9 + 0.3, age + 8, height + 0.5, 16);
		level.sendParticles(RitualFx.fade(Resurrection.RED, Resurrection.CRIMSON, 1.2F), feet.x, feet.y + height * 0.5, feet.z, 2, width * 0.5,
				height * 0.3, width * 0.5, 0.0);
		if (age % 3 == 0) {
			RuinFx.ring(level, ParticleTypes.SOUL_FIRE_FLAME, feet.add(0, 0.1, 0), width + 0.6, 10, 0.05);
		}
		if (age % 5 == 0) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, feet.x, feet.y + height, feet.z, 1, 0.2, 0.1, 0.2, 0.01);
		}
	}

	private static boolean allowLoad(Entity entity) {
		return !entity.entityTags().contains(BossFactory.TAG) || BY_MOB.containsKey(entity.getUUID());
	}

	/** A leftover loaded anyway (should have been refused): removed on the next tick. */
	private static void onEntityLoad(Entity entity) {
		if (!entity.entityTags().contains(BossFactory.TAG) || BY_MOB.containsKey(entity.getUUID())) {
			return;
		}
		Scheduler.later("ruin.cleanup", 1, () -> {
			if (!entity.isRemoved() && !BY_MOB.containsKey(entity.getUUID())) {
				entity.discard();
			}
		});
	}

	private static BossEvent.BossBarColor barColor(String name) {
		try {
			return BossEvent.BossBarColor.valueOf(name);
		} catch (IllegalArgumentException e) {
			return BossEvent.BossBarColor.RED;
		}
	}

	/**
	 * The guardian may appear at {@code pos}: its chunk is loaded (a chunk forced or reached by a player this very tick
	 * is not "entity ticking" before the next ticket update, the guardian simply starts to act one tick later).
	 */
	static boolean loaded(ServerLevel level, BlockPos pos) {
		return level.isPositionEntityTicking(pos) || level.isLoaded(pos);
	}
}
