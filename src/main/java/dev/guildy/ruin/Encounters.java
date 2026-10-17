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
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
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
 * Ruin fights: awakening when a survival player approaches the guardian, boss bar, arena leash, abilities, reset when
 * nobody is around, cancellation when the guardian is unloaded, rewards on its death. Runs once per second (the
 * animations every tick in {@link RuinFx}); every loop is bounded by the players online and the running fights.
 */
public final class Encounters {
	private static final Map<String, Encounter> ACTIVE = new LinkedHashMap<>();
	private static final Map<UUID, Encounter> BY_MOB = new HashMap<>();
	private static final Map<UUID, Long> HINTS = new HashMap<>();
	private static final SplittableRandom RANDOM = new SplittableRandom();
	private static final int HINT_INTERVAL_TICKS = 60 * 20;
	private static long seconds;

	private Encounters() {
	}

	static void init() {
		Scheduler.every("ruin.encounters", 20, 13, Encounters::tickSecond);
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
		if (ruin.state != RuinState.DORMANT || ACTIVE.containsKey(ruin.key) || player.isCreative()) {
			return;
		}
		BlockPos spawn = ruin.boss != null ? ruin.boss : ruin.center();
		boolean inActivation = def.activationRadius <= 0
				|| player.distanceToSqr(Vec3.atBottomCenterOf(spawn)) <= (double) def.activationRadius * def.activationRadius;
		boolean enabled = cfg.bossesEnabled && level.getDifficulty() != Difficulty.PEACEFUL && ACTIVE.size() < cfg.encounter.maxActive;
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
	 * @param forced admin command: also re-arms a defeated guardian and ignores the fight limit
	 */
	public static @Nullable String awaken(ServerLevel level, RuinRecord ruin, @Nullable RuinDef forcedDef, boolean forced) {
		if (ACTIVE.containsKey(ruin.key)) {
			return "ruin.admin.already_awake";
		}
		if (!level.dimension().identifier().toString().equals(ruin.dimension)) {
			return "ruin.admin.wrong_dimension";
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
		if (!level.isPositionEntityTicking(spawn)) {
			return "ruin.admin.not_loaded";
		}
		ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(Lang.colorize(def.boss.name)), barColor(def.bossBarColor),
				BossEvent.BossBarOverlay.NOTCHED_10);
		bar.setDarkenScreen(cfg.encounter.darkenSky);
		Encounter encounter = new Encounter(ruin, def, level.dimension(), spawn, bar, Scheduler.ticks(), cfg.encounter.firstAbilitySeconds);
		Mob boss = BossFactory.spawnBoss(level, def.boss, spawn, mob -> {
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
		spawnMinions(level, encounter, RuinRules.minionCount(def.minions.base, def.minions.perExtraPlayer, def.minions.max, Math.max(1, fighters)));
		awakenEffects(level, boss);
		Component title = Component.literal(Lang.colorize(def.name));
		Component subtitle = Lang.get("ruin.awaken.subtitle", Lang.colorize(def.boss.name));
		for (ServerPlayer player : present) {
			Msg.title(player, title, subtitle, 10, 60, 20);
			Msg.send(player, "ruin.awaken.chat", Lang.colorize(def.boss.name), Lang.colorize(def.name));
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

	/** One escort mob near {@code pos} (cap: minions.maxAlive). */
	private static @Nullable Mob summon(ServerLevel level, Encounter encounter, BlockPos pos) {
		if (encounter.def.minions.types.isEmpty() || encounter.minionEntities(level).size() >= encounter.def.minions.maxAlive) {
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
		leash(level, encounter, boss);
		syncBar(level, encounter, boss, present);
		float ratio = boss.getHealth() / Math.max(1.0F, boss.getMaxHealth());
		if (encounter.enrage != null && !encounter.enraged && ratio <= encounter.enrage.healthBelow) {
			encounter.enraged = true;
			encounter.cooldownFactor = 0.5;
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
			boolean used = Safe.get("ruin.ability." + ability.type,
					() -> BossAbilities.use(level, encounter, boss, ability, targets, RANDOM, Encounters::summon), false);
			if (used) {
				encounter.readyAt[index] = now + RuinRules.cooldownTicks(ability.cooldownSeconds, encounter.cooldownFactor);
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
		if (boss == null) {
			return false;
		}
		if ("enrage".equals(ability.type)) {
			encounter.enraged = true;
			encounter.cooldownFactor = 0.5;
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
		return !targets.isEmpty() && BossAbilities.use(level, encounter, boss, ability, targets, RANDOM, Encounters::summon);
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
			encounter.bar.setName(Lang.get("ruin.bar", Lang.colorize(encounter.def.boss.name), (int) Math.ceil(health), (int) Math.ceil(max)));
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

	/** Admin reset: ends the fight and re-arms a defeated guardian. */
	public static void reset(RuinRecord ruin) {
		Encounter encounter = ACTIVE.get(ruin.key);
		if (encounter != null) {
			MinecraftServer server = GuildyServer.server();
			end(encounter, server == null ? null : server.getLevel(encounter.dimension), false);
		}
		ruin.state = RuinState.DORMANT;
		RuinIndex.markDirty();
	}

	/** Server stopping: every fight is cancelled (the guardians are removed before the chunks are saved). */
	static void stopAll() {
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
		for (ServerPlayer player : present) {
			Msg.title(player, Lang.get("ruin.victory.title"), Lang.get("ruin.victory.subtitle", bossName), 10, 70, 20);
			Msg.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
		}
		if (cfg.encounter.announce) {
			String names = encounter.names.isEmpty() ? Lang.raw("ruin.victory.someone") : String.join(", ", encounter.names.values());
			level.getServer().getPlayerList().broadcastSystemMessage(Msg.prefixed(Lang.get("ruin.victory.announce", names, bossName, ruinName)), false);
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
			converted.setHealth(Math.max(1.0F, ratio * converted.getMaxHealth()));
		} else if (encounter.minions.remove(previous.getUUID())) {
			encounter.minions.add(converted.getUUID());
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
}
