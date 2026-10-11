package dev.guildy.wave;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildEvents;
import dev.guildy.data.GuildEvents.WaveOutcome.Result;
import dev.guildy.data.GuildManager;
import dev.guildy.upgrade.UpgradeEffects;
import dev.guildy.upgrade.Upgrades;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * One running wave for one guild (in memory only: never persisted, cancelled by a restart): a night horde, an admin
 * test wave or an event wave ({@link WaveManager.Kind}). Driven once per second by {@link WaveManager} (staggered by
 * {@link #slot()}).
 */
final class WaveSession {
	private final Guild guild;
	private final WaveConfig.Theme theme;
	private final double difficulty;
	private final int night;
	private final int nearbyAtStart;
	private final WaveManager.Kind kind;
	/** Duration of an event wave (seconds); test waves read waves.json5, night waves end at dawn. */
	private final int eventDuration;
	private final String eventId;
	private final NightModifiers modifiers;
	private final int slot;
	private final BlockPos heart;
	private final long heartChunk;
	private final long day;
	private final int maxIntegrity;
	private final boolean hasBoss;
	private final int groupSize;
	private final SplittableRandom random = new SplittableRandom();
	private final ArrayDeque<WavePlanner.PlannedMob> queue;
	private final Map<UUID, TrackedMob> mobs = new LinkedHashMap<>();
	private final Set<UUID> present = new HashSet<>();
	private final ServerBossEvent progressBar;
	private final ServerBossEvent heartBar;
	private @Nullable ServerBossEvent bossBar;
	private @Nullable UUID bossUuid;
	private String bossName = "";
	private long[] borderChunks = new long[0];
	private boolean borderDirty = true;
	private int total;
	private int integrity;
	private int kills;
	private int eliteKills;
	private int spawned;
	private boolean bossSpawned;
	private boolean bossKilled;
	private boolean bossDropped;
	private int seconds;
	private int absentSeconds;
	private int nextGroupIn;
	private int groupRemaining;
	private @Nullable BlockPos groupAnchor;
	private int lastAlertSecond = -100_000;
	private long lastClock;
	private int mobCounter;
	private boolean ended;

	WaveSession(Guild guild, WavePlanner.Plan plan, double difficulty, int night, int nearbyAtStart, WaveManager.Request request,
			NightModifiers modifiers, int slot, long clock, double spawnPhaseSeconds) {
		WaveConfig cfg = WaveModule.config();
		this.guild = guild;
		this.theme = plan.theme();
		this.difficulty = difficulty;
		this.night = night;
		this.nearbyAtStart = nearbyAtStart;
		this.kind = request.kind();
		this.eventDuration = request.durationSeconds();
		this.eventId = request.eventId() == null ? "" : request.eventId();
		this.modifiers = modifiers;
		this.slot = slot;
		this.heart = guild.heart == null ? BlockPos.ZERO : guild.heart.immutable();
		this.heartChunk = ChunkKey.ofBlock(heart.getX(), heart.getZ());
		this.day = WaveFormula.dayIndex(clock);
		this.lastClock = clock;
		this.queue = new ArrayDeque<>(plan.mobs());
		this.total = plan.mobs().size();
		this.hasBoss = plan.hasBoss();
		this.maxIntegrity = Upgrades.boost(guild, UpgradeEffects.HEART_INTEGRITY_PCT, WaveFormula.heartIntegrity(cfg.heart, guild.level));
		this.integrity = maxIntegrity;
		this.groupSize = WaveFormula.groupSize(cfg.spawning, total, spawnPhaseSeconds);
		this.nextGroupIn = Math.max(1, cfg.spawning.firstGroupDelaySeconds);
		this.progressBar = new ServerBossEvent(UUID.randomUUID(), Component.empty(), barColor(theme.barColor), BossEvent.BossBarOverlay.NOTCHED_10);
		this.heartBar = new ServerBossEvent(UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
	}

	// ================================================================== accessors

	Guild guild() {
		return guild;
	}

	UUID guildId() {
		return guild.id;
	}

	BlockPos heart() {
		return heart;
	}

	double difficulty() {
		return difficulty;
	}

	int night() {
		return night;
	}

	boolean isTest() {
		return kind == WaveManager.Kind.TEST;
	}

	/** Test and event waves ignore the day/night clock and end after {@link #durationSeconds()}. */
	boolean isTimed() {
		return kind != WaveManager.Kind.NIGHT;
	}

	boolean isEvent() {
		return kind == WaveManager.Kind.EVENT;
	}

	/** Event id of an event wave, "" otherwise. */
	String eventId() {
		return eventId;
	}

	NightModifiers modifiers() {
		return modifiers;
	}

	/** Lifetime of a timed wave (seconds). */
	int durationSeconds() {
		return isTest() ? WaveModule.config().test.durationSeconds : eventDuration;
	}

	/** Theme name, prefixed with the modifiers label (blood night...). */
	String displayName() {
		String name = Lang.colorize(theme.name);
		return modifiers.hasLabel() ? Lang.raw("wave.bar.label", Lang.colorize(modifiers.label()), name) : name;
	}

	int slot() {
		return slot;
	}

	boolean isRunning() {
		return !ended;
	}

	WaveConfig.Theme theme() {
		return theme;
	}

	int kills() {
		return kills;
	}

	int total() {
		return total;
	}

	int eliteKills() {
		return eliteKills;
	}

	int integrity() {
		return integrity;
	}

	int maxIntegrity() {
		return maxIntegrity;
	}

	int alive() {
		return mobs.size();
	}

	int queued() {
		return queue.size();
	}

	/** True when this wave has (or still plans) a boss. */
	boolean hasBoss() {
		return hasBoss && !bossDropped;
	}

	boolean bossSpawned() {
		return bossSpawned;
	}

	boolean bossKilled() {
		return bossKilled;
	}

	String bossName() {
		return bossName;
	}

	int heartPercent() {
		return maxIntegrity <= 0 ? 0 : (int) Math.ceil(100.0 * integrity / maxIntegrity);
	}

	Collection<TrackedMob> trackedMobs() {
		return mobs.values();
	}

	/** Remaining seconds before the wave ends by itself (dawn, test or event duration). */
	long remainingSeconds(long clock) {
		WaveConfig cfg = WaveModule.config();
		if (isTimed()) {
			return Math.max(0, durationSeconds() - seconds);
		}
		long tod = WaveFormula.timeOfDay(clock);
		return Math.max(0, (cfg.timing.nightEnd - tod) / 20);
	}

	/** Boss health (current, max) or null when no boss is alive/loaded. */
	float @Nullable [] bossHealth(ServerLevel level) {
		if (bossUuid == null) {
			return null;
		}
		TrackedMob tracked = mobs.get(bossUuid);
		Mob boss = tracked == null ? null : tracked.resolve(level);
		return boss == null ? null : new float[] {boss.getHealth(), boss.getMaxHealth()};
	}

	/** Border chunks of the territory (cached, refreshed after TERRITORY_CHANGED). */
	long[] borderChunks() {
		if (borderDirty) {
			LongArrayList list = new LongArrayList();
			for (long key : guild.claims()) {
				if (guild.isBorderChunk(key)) {
					list.add(key);
				}
			}
			borderChunks = list.toLongArray();
			borderDirty = false;
		}
		return borderChunks;
	}

	void markTerritoryChanged() {
		borderDirty = true;
	}

	// ================================================================== tracking

	TrackedMob track(Mob mob, WavePlanner.PlannedMob planned) {
		TrackedMob tracked = new TrackedMob(mob.getUUID(), planned, mobCounter++);
		tracked.bind(mob);
		mobs.put(tracked.uuid, tracked);
		WaveManager.indexMob(tracked.uuid, this);
		return tracked;
	}

	void untrack(UUID uuid) {
		mobs.remove(uuid);
		WaveManager.unindexMob(uuid);
	}

	@Nullable TrackedMob tracked(UUID uuid) {
		return mobs.get(uuid);
	}

	/**
	 * A tracked mob converted into another entity (called before the new entity is added to the level).
	 * Hostile result (zombie drowning...): tracking, buffs and steering move to the new entity.
	 * Non-hostile result (zombie villager cured by players): it leaves the horde for good and counts as defeated.
	 */
	void transfer(Mob previous, Mob converted) {
		TrackedMob old = mobs.get(previous.getUUID());
		if (old == null || ended) {
			return;
		}
		untrack(old.uuid);
		if (!(converted instanceof Enemy)) {
			converted.removeTag(WaveSpawner.TAG);
			converted.removeTag(WaveSpawner.guildTag(guild.id));
			converted.setGlowingTag(false);
			recordDefeat(old);
			return;
		}
		track(converted, old.planned);
		if (old.uuid.equals(bossUuid)) {
			bossUuid = converted.getUUID();
		}
		WaveSpawner.applyBuffs(converted, old.planned, difficulty, modifiers);
		converted.setHealth(converted.getMaxHealth());
		WaveSpawner.attachSteering(converted, this);
	}

	// ================================================================== lifecycle

	void start(ServerLevel level) {
		String themeName = Lang.colorize(theme.name);
		Component title = Lang.get("wave.start.title");
		Component subtitle = Lang.get("wave.start.subtitle", displayName());
		for (ServerPlayer player : guild.onlineMembers()) {
			Msg.title(player, title, subtitle, 10, 70, 20);
			Msg.sound(player, SoundEvents.RAID_HORN, 1.0F, 1.0F);
			Msg.send(player, "wave.start.chat", themeName, formatDifficulty(difficulty), total);
			player.sendSystemMessage(Lang.get(isEvent() ? "wave.start.goal.event" : "wave.start.goal"));
			if (modifiers.hasLabel()) {
				player.sendSystemMessage(Lang.get("wave.start.modifiers", Lang.colorize(modifiers.label()),
						formatMultiplier(modifiers.goldMultiplier())));
			}
			if (hasBoss) {
				player.sendSystemMessage(Lang.get("wave.start.boss"));
			}
			if (isTest()) {
				player.sendSystemMessage(Lang.get("wave.start.test"));
			}
		}
		refreshBars(level);
		syncBars(level);
	}

	/** Once per second. */
	void secondTick(ServerLevel level, long clock, boolean advanceTime) {
		if (ended) {
			return;
		}
		WaveConfig cfg = WaveModule.config();
		seconds++;

		// Territory still valid?
		if (GuildManager.get(guild.id) != guild || guild.heart == null || !guild.heart.equals(heart) || !guild.owns(heartChunk)) {
			finish(Result.CANCELLED, "wave.reason.territory");
			return;
		}
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			finish(Result.CANCELLED, "wave.reason.peaceful");
			return;
		}

		// Time: dawn (victory) or skipped night (cancelled); timed waves (test, event) end after their duration.
		if (isTimed()) {
			if (seconds >= durationSeconds()) {
				finish(Result.VICTORY, null);
				return;
			}
		} else {
			if (!advanceTime) {
				finish(Result.CANCELLED, "wave.reason.frozen");
				return;
			}
			long delta = clock - lastClock;
			lastClock = clock;
			boolean jumped = delta < 0 || delta > cfg.timing.maxClockJumpTicks;
			boolean nightOver = WaveFormula.dayIndex(clock) != day || !WaveFormula.inNight(cfg.timing, WaveFormula.timeOfDay(clock));
			if (nightOver) {
				if (jumped) {
					finish(Result.CANCELLED, "wave.reason.skipped");
				} else {
					finish(Result.VICTORY, null);
				}
				return;
			}
			if (seconds >= cfg.timing.maxWaveMinutes * 60) {
				finish(Result.VICTORY, null);
				return;
			}
		}

		// Defenders present?
		List<ServerPlayer> defenders = defenders(level, cfg);
		if (defenders.isEmpty()) {
			absentSeconds++;
			int warnAt = Math.max(1, cfg.timing.absentCancelSeconds - 60);
			if (absentSeconds == warnAt) {
				Msg.guild(guild, "wave.absent.warning", cfg.timing.absentCancelSeconds - warnAt);
			}
			if (absentSeconds >= cfg.timing.absentCancelSeconds) {
				finish(Result.CANCELLED, "wave.reason.absent");
				return;
			}
		} else {
			absentSeconds = 0;
			for (ServerPlayer player : defenders) {
				present.add(player.getUUID());
			}
		}

		// Mobs: lost/destroyed check, heart damage, steering.
		List<Candidate> candidates = candidates(level);
		List<TrackedMob> recycle = new ArrayList<>();
		int heartDamage = 0;
		for (TrackedMob tracked : new ArrayList<>(mobs.values())) {
			Mob mob = tracked.resolve(level);
			if (mob == null) {
				if (tracked.isDestroyed()) {
					recycle.add(tracked); // discarded by something else: back to the spawn budget
				} else if (++tracked.missingSeconds >= cfg.mobs.lostTimeoutSeconds) {
					recycle.add(tracked); // unloaded for too long
				}
				continue;
			}
			tracked.missingSeconds = 0;
			if (!mob.isAlive()) {
				continue; // dying: counted by the death event
			}
			if (inHeartZone(mob, cfg)) {
				heartDamage += switch (tracked.planned.kind()) {
					case BOSS -> cfg.heart.damageBoss;
					case ELITE -> cfg.heart.damageElite;
					default -> cfg.heart.damageNormal;
				};
			}
			if ((seconds + tracked.index) % cfg.mobs.steeringIntervalSeconds == 0) {
				steer(level, mob, tracked, candidates, recycle, cfg);
			}
		}
		for (TrackedMob tracked : recycle) {
			recycle(level, tracked);
		}

		if (heartDamage > 0) {
			hitHeart(level, heartDamage, cfg);
			if (integrity <= 0) {
				finish(Result.DEFEAT, null);
				return;
			}
		}

		spawnTick(level, cfg);

		if (queue.isEmpty() && mobs.isEmpty()) {
			if (spawned == 0) {
				finish(Result.CANCELLED, "wave.reason.empty");
				return;
			}
			if (isTimed() || cfg.timing.endWhenHordeDefeated) {
				finish(Result.VICTORY, null);
				return;
			}
		}

		refreshBars(level);
		syncBars(level);
	}

	// ================================================================== presence & targeting

	private List<ServerPlayer> defenders(ServerLevel level, WaveConfig cfg) {
		List<ServerPlayer> list = new ArrayList<>();
		double max = (double) cfg.timing.activationDistance * cfg.timing.activationDistance;
		for (ServerPlayer player : guild.onlineMembers()) {
			if (player.level() != level || player.isSpectator()) {
				continue;
			}
			double distance = player.distanceToSqr(heart.getX() + 0.5, heart.getY() + 0.5, heart.getZ() + 0.5);
			if (distance <= max || guild.owns(ChunkKey.ofBlock(player.getBlockX(), player.getBlockZ()))) {
				list.add(player);
			}
		}
		return list;
	}

	private record Candidate(ServerPlayer player, boolean inside) {
	}

	/** Attackable players around the base (bounded by the territory radius). */
	private List<Candidate> candidates(ServerLevel level) {
		List<Candidate> list = new ArrayList<>();
		double radius = guild.territoryRadiusBlocks() + 48.0;
		double max = radius * radius;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.isCreative() || !player.isAlive()) {
				continue;
			}
			double dx = player.getX() - heart.getX();
			double dz = player.getZ() - heart.getZ();
			if (dx * dx + dz * dz > max) {
				continue;
			}
			list.add(new Candidate(player, guild.owns(ChunkKey.ofBlock(player.getBlockX(), player.getBlockZ()))));
		}
		return list;
	}

	private void steer(ServerLevel level, Mob mob, TrackedMob tracked, List<Candidate> candidates, List<TrackedMob> recycle, WaveConfig cfg) {
		LivingEntity target = mob.getTarget();
		boolean brainDriven = !mob.getBrain().isBrainDead();
		if (target == null || !target.isAlive()) {
			ServerPlayer player = nearest(mob, tracked.planned.alwaysHostile(), candidates, cfg.mobs);
			if (player != null) {
				applyTarget(mob, player, brainDriven);
				target = player;
			}
		}
		boolean atHeart = inHeartZone(mob, cfg);
		if (target == null && brainDriven && !atHeart) {
			steerBrain(level, mob);
		}
		// Stuck detection (only when idle: a mob fighting or attacking the heart is never "stuck").
		if (target != null || atHeart) {
			tracked.stuckSeconds = 0;
			tracked.checkpoint(mob);
		} else if (mob.distanceToSqr(tracked.lastX, tracked.lastY, tracked.lastZ) < 1.0) {
			tracked.stuckSeconds += cfg.mobs.steeringIntervalSeconds;
			if (tracked.stuckSeconds >= cfg.mobs.stuckTimeoutSeconds) {
				recycle.add(tracked);
			}
		} else {
			tracked.stuckSeconds = 0;
			tracked.checkpoint(mob);
		}
	}

	private static @Nullable ServerPlayer nearest(Mob mob, boolean alwaysHostile, List<Candidate> candidates, WaveConfig.MobSettings cfg) {
		double range = alwaysHostile ? cfg.aggroRange : cfg.targetRange;
		if (range <= 0) {
			return null;
		}
		double best = range * range;
		ServerPlayer found = null;
		for (Candidate candidate : candidates) {
			if (!alwaysHostile && !candidate.inside()) {
				continue;
			}
			double distance = mob.distanceToSqr(candidate.player());
			if (distance <= best) {
				best = distance;
				found = candidate.player();
			}
		}
		return found;
	}

	private static void applyTarget(Mob mob, ServerPlayer player, boolean brainDriven) {
		Brain<?> brain = mob.getBrain();
		if (brainDriven && brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
			brain.setMemory(MemoryModuleType.ATTACK_TARGET, player);
		} else {
			mob.setTarget(player);
		}
		if (mob instanceof NeutralMob neutral) {
			neutral.setPersistentAngerTarget(EntityReference.<LivingEntity>of(player));
			neutral.startPersistentAngerTimer();
		}
	}

	private void steerBrain(ServerLevel level, Mob mob) {
		Brain<?> brain = mob.getBrain();
		if (brain.checkMemory(MemoryModuleType.HOME, MemoryStatus.REGISTERED)) {
			brain.setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), heart));
		}
		if (brain.checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
			brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(waypoint(mob, 1.0), 1.0F, 2));
		}
	}

	/** Next waypoint toward the heart (heart itself when close, else {@code waypointDistance * factor} blocks ahead). */
	Vec3 waypoint(Mob mob, double factor) {
		double hx = heart.getX() + 0.5;
		double hz = heart.getZ() + 0.5;
		double dx = hx - mob.getX();
		double dz = hz - mob.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		double step = Math.max(4, WaveModule.config().mobs.waypointDistance * factor);
		if (dist <= step) {
			return new Vec3(hx, heart.getY(), hz);
		}
		double x = mob.getX() + dx / dist * step;
		double z = mob.getZ() + dz / dist * step;
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		double y = mob.getY();
		if (mob.level() instanceof ServerLevel level && level.getChunkSource().getChunkNow(bx >> 4, bz >> 4) != null) {
			y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
		}
		return new Vec3(x, y, z);
	}

	/** Close enough to stop walking (inside the damage zone). */
	boolean isAtHeart(Mob mob) {
		WaveConfig.HeartSettings h = WaveModule.config().heart;
		double r = Math.max(1.0, h.radius - 1.0);
		double dx = mob.getX() - (heart.getX() + 0.5);
		double dz = mob.getZ() - (heart.getZ() + 0.5);
		return dx * dx + dz * dz <= r * r && Math.abs(mob.getY() - heart.getY()) <= h.verticalRange;
	}

	private boolean inHeartZone(Mob mob, WaveConfig cfg) {
		double r = cfg.heart.radius;
		double dx = mob.getX() - (heart.getX() + 0.5);
		double dz = mob.getZ() - (heart.getZ() + 0.5);
		return dx * dx + dz * dz <= r * r && Math.abs(mob.getY() - heart.getY()) <= cfg.heart.verticalRange;
	}

	private void recycle(ServerLevel level, TrackedMob tracked) {
		Mob mob = tracked.resolve(level);
		untrack(tracked.uuid);
		if (mob != null) {
			poof(level, mob);
			mob.discard();
		}
		queue.addFirst(tracked.planned);
		if (tracked.planned.isBoss()) {
			bossSpawned = false;
			bossUuid = null;
			removeBossBar();
		}
	}

	// ================================================================== heart

	private void hitHeart(ServerLevel level, int damage, WaveConfig cfg) {
		integrity = Math.max(0, integrity - damage);
		double x = heart.getX() + 0.5;
		double y = heart.getY() + 0.5;
		double z = heart.getZ() + 0.5;
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, x, y + 0.6, z, Math.min(12, 2 + damage), 0.4, 0.5, 0.4, 0.1);
		level.sendParticles(ParticleTypes.SMOKE, x, y, z, 8, 0.3, 0.5, 0.3, 0.02);
		level.playSound(null, x, y, z, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 0.7F, 0.8F + (float) random.nextDouble() * 0.4F);
		if (seconds - lastAlertSecond >= cfg.heart.alertIntervalSeconds) {
			lastAlertSecond = seconds;
			Component alert = Lang.get("wave.heart.alert", heartPercent());
			for (ServerPlayer player : guild.onlineMembers()) {
				Msg.actionBar(player, alert);
			}
		}
	}

	// ================================================================== spawning

	private void spawnTick(ServerLevel level, WaveConfig cfg) {
		WaveConfig.Spawning sc = cfg.spawning;
		if (queue.isEmpty()) {
			groupRemaining = 0;
			return;
		}
		if (groupRemaining <= 0) {
			if (--nextGroupIn > 0) {
				return;
			}
			nextGroupIn = sc.spawnGroupIntervalSeconds;
			groupRemaining = groupSize;
			groupAnchor = null;
		}
		int capacity = Math.min(sc.maxAliveMobsPerBase - mobs.size(), sc.globalMaxAliveMobs - WaveManager.totalAlive());
		int attempts = sc.spawnAttemptsPerSecond;
		BlockPos soundPos = null;
		while (groupRemaining > 0 && capacity > 0 && attempts > 0 && !queue.isEmpty()) {
			WavePlanner.PlannedMob next = queue.peekFirst();
			EntityType<?> type = WaveSpawner.entityType(next.entity());
			if (type == null) {
				dropFromPlan();
				continue;
			}
			BlockPos pos = null;
			if (groupAnchor != null && !next.isBoss()) {
				attempts--;
				pos = WaveSpawner.findNear(level, this, groupAnchor, type, next.spawnHeight(), random);
			}
			if (pos == null && attempts > 0) {
				attempts--;
				pos = WaveSpawner.findBorderPosition(level, this, type, next.spawnHeight(), random);
			}
			if (pos == null || !WaveSpawner.farFromPlayers(level, pos, sc.minPlayerDistance)) {
				continue;
			}
			BlockPos spawnPos = pos;
			Mob mob = Safe.get("wave.spawn", () -> WaveSpawner.spawn(level, this, next, spawnPos, random), null);
			if (mob == null) {
				dropFromPlan(); // cannot be created (invalid id, not a mob, error): leaves the plan
				continue;
			}
			queue.pollFirst();
			groupRemaining--;
			capacity--;
			spawned++;
			if (soundPos == null) {
				soundPos = pos;
			}
			if (next.isBoss()) {
				onBossSpawned(level, mob, next);
			} else if (groupAnchor == null) {
				groupAnchor = pos.below(Math.max(0, next.spawnHeight()));
			}
		}
		if (soundPos != null && sc.spawnEffects) {
			level.playSound(null, soundPos.getX() + 0.5, soundPos.getY(), soundPos.getZ() + 0.5, SoundEvents.EVOKER_PREPARE_SUMMON,
					SoundSource.HOSTILE, 1.5F, 0.6F);
		}
	}

	/** The head of the queue cannot be spawned at all (invalid entity): it leaves the plan. */
	private void dropFromPlan() {
		WavePlanner.PlannedMob dropped = queue.pollFirst();
		total = Math.max(kills + mobs.size() + queue.size(), total - 1);
		if (groupRemaining > 0) {
			groupRemaining--;
		}
		if (dropped != null) {
			WaveManager.warnInvalidEntity(dropped.entity());
			if (dropped.isBoss()) {
				bossDropped = true;
			}
		}
	}

	private void onBossSpawned(ServerLevel level, Mob mob, WavePlanner.PlannedMob planned) {
		bossSpawned = true;
		bossUuid = mob.getUUID();
		bossName = planned.boss() != null ? planned.boss().name : mob.getType().getDescriptionId();
		Component name = Component.literal(Lang.colorize(bossName));
		bossBar = new ServerBossEvent(UUID.randomUUID(), name, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_20);
		level.sendParticles(ParticleTypes.EXPLOSION, mob.getX(), mob.getY() + 1, mob.getZ(), 3, 0.6, 0.6, 0.6, 0.0);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, mob.getX(), mob.getY() + 1, mob.getZ(), 30, 0.8, 1.0, 0.8, 0.05);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0F, 1.0F);
		Component title = Lang.get("wave.boss.spawn.title");
		Component subtitle = Lang.get("wave.boss.spawn.subtitle", Lang.colorize(bossName));
		for (ServerPlayer player : guild.onlineMembers()) {
			Msg.title(player, title, subtitle, 10, 50, 15);
			Msg.send(player, "wave.boss.spawn.chat", Lang.colorize(bossName));
		}
	}

	private void removeBossBar() {
		if (bossBar != null) {
			bossBar.removeAllPlayers();
			bossBar = null;
		}
	}

	// ================================================================== deaths

	void onMobDeath(ServerLevel level, Mob mob, TrackedMob tracked) {
		untrack(tracked.uuid);
		if (ended) {
			return;
		}
		recordDefeat(tracked);
		WaveLoot.dropKillLoot(level, mob, tracked.planned, guild, heart, difficulty, random);
		refreshBars(level);
	}

	private void recordDefeat(TrackedMob tracked) {
		kills++;
		if (tracked.planned.isElite()) {
			eliteKills++;
		}
		if (tracked.planned.isBoss()) {
			bossKilled = true;
			bossUuid = null;
			removeBossBar();
			Msg.guild(guild, "wave.boss.killed", Lang.colorize(bossName));
			Msg.guildSound(guild, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
		}
	}

	// ================================================================== boss bars

	private void refreshBars(ServerLevel level) {
		int remaining = Math.max(0, total - kills);
		progressBar.setName(Lang.get("wave.bar.progress", displayName(), kills, total, remaining));
		progressBar.setProgress(total <= 0 ? 0.0F : Mth.clamp((float) kills / total, 0.0F, 1.0F));
		heartBar.setName(Lang.get("wave.bar.heart", integrity, maxIntegrity));
		heartBar.setProgress(Mth.clamp((float) integrity / Math.max(1, maxIntegrity), 0.0F, 1.0F));
		if (bossBar != null) {
			float[] health = bossHealth(level);
			if (health != null) {
				bossBar.setName(Lang.get("wave.bar.boss", Lang.colorize(bossName), (int) Math.ceil(health[0]), (int) Math.ceil(health[1])));
				bossBar.setProgress(Mth.clamp(health[0] / Math.max(1.0F, health[1]), 0.0F, 1.0F));
			}
		}
	}

	/** Viewers: online members (anywhere) + players standing inside the territory. */
	private void syncBars(ServerLevel level) {
		Set<ServerPlayer> viewers = new HashSet<>(guild.onlineMembers());
		for (ServerPlayer player : level.players()) {
			if (guild.owns(ChunkKey.ofBlock(player.getBlockX(), player.getBlockZ()))) {
				viewers.add(player);
			}
		}
		sync(progressBar, viewers);
		sync(heartBar, viewers);
		if (bossBar != null) {
			sync(bossBar, viewers);
		}
	}

	private static void sync(ServerBossEvent bar, Set<ServerPlayer> viewers) {
		for (ServerPlayer player : new ArrayList<>(bar.getPlayers())) {
			if (!viewers.contains(player)) {
				bar.removePlayer(player);
			}
		}
		for (ServerPlayer player : viewers) {
			bar.addPlayer(player);
		}
	}

	private void removeBars() {
		progressBar.removeAllPlayers();
		heartBar.removeAllPlayers();
		removeBossBar();
	}

	// ================================================================== end

	/**
	 * Ends the wave: rewards / penalties, stats, horde removal, feedback, WAVE_ENDED event. Idempotent.
	 *
	 * @param reasonKey lang key of the cancel reason (CANCELLED only)
	 */
	void finish(Result requested, @Nullable String reasonKey) {
		if (ended) {
			return;
		}
		ended = true;
		WaveManager.unregister(this);
		WaveConfig cfg = WaveModule.config();
		Result result = requested;
		String reason = reasonKey;
		if (result == Result.VICTORY && spawned == 0) {
			result = Result.CANCELLED;
			reason = "wave.reason.empty";
		}
		if (isTest() && !cfg.test.applyResults && result != Result.CANCELLED) {
			result = Result.CANCELLED;
			reason = "wave.reason.test";
		}

		ServerLevel level = GuildyServer.claimLevel();
		clearMobs(level);
		removeBars();

		GuildWaveData data = guild.waves;
		double ratio = WaveFormula.killRatio(kills, total);
		long gold = 0;
		long xp = 0;
		long loss = 0;
		int rolls = 0;
		int nightBefore = data.night;
		// Event waves (raids...) count as hordes for the statistics but never move the night step.
		boolean nightStep = !isEvent();
		switch (result) {
			case VICTORY -> {
				gold = modifiers.gold(Upgrades.boost(guild, UpgradeEffects.WAVE_REWARD_PCT, WaveFormula.goldReward(cfg.rewards, difficulty, ratio)));
				xp = modifiers.xp(Upgrades.boost(guild, UpgradeEffects.WAVE_REWARD_PCT, WaveFormula.xpReward(cfg.rewards, difficulty, ratio)));
				rolls = modifiers.itemRolls(WaveFormula.itemRolls(cfg.rewards, difficulty, ratio));
				GuildManager.addTreasury(guild, gold);
				if (nightStep) {
					data.night++;
				}
				data.victories++;
				data.streak++;
				data.bestStreak = Math.max(data.bestStreak, data.streak);
				data.lastResult = "wave.result.victory";
				data.lastGold = gold;
				data.lastXp = xp;
			}
			case DEFEAT -> {
				loss = Upgrades.reduce(guild, UpgradeEffects.DEFEAT_LOSS_REDUCTION_PCT, WaveFormula.treasuryLoss(cfg.defeat, guild.treasury));
				if (loss > 0) {
					GuildManager.takeTreasury(guild, loss);
				}
				if (nightStep) {
					data.night = WaveFormula.nightAfterDefeat(cfg.defeat, data.night);
				}
				data.defeats++;
				data.streak = 0;
				data.lastResult = "wave.result.defeat";
				data.lastGold = loss;
				data.lastXp = 0;
			}
			default -> {
				data.lastResult = "wave.result.cancelled";
				data.lastGold = 0;
				data.lastXp = 0;
			}
		}
		data.lastTheme = theme.name;
		data.lastKills = kills;
		data.lastTotal = total;
		data.lastAt = System.currentTimeMillis();
		data.totalKills += kills;
		if (bossKilled) {
			data.bossesKilled++;
		}
		GuildManager.markDirty();

		announceEnd(level, result, reason, gold, xp, rolls, loss, nightBefore, data.night, cfg);
		if (result == Result.VICTORY) {
			if (xp > 0) {
				GuildManager.addXp(guild, xp); // may announce a level-up: after the recap
			}
			if (rolls > 0) {
				giveItems(rolls);
			}
		}
		GuildEvents.fireWaveEnded(new GuildEvents.WaveOutcome(guild, result, night, difficulty, kills, total));
	}

	/** Last resort after repeated errors: removes bars and mobs without any other processing. */
	void emergencyCleanup(ServerLevel level) {
		ended = true;
		Safe.run("wave.abort", this::removeBars);
		Safe.run("wave.abort", () -> clearMobs(level));
	}

	private void clearMobs(@Nullable ServerLevel level) {
		for (TrackedMob tracked : new ArrayList<>(mobs.values())) {
			untrack(tracked.uuid);
			if (level != null) {
				Mob mob = tracked.resolve(level);
				if (mob != null) {
					poof(level, mob);
					mob.discard();
				}
			}
		}
		queue.clear();
	}

	private static void poof(ServerLevel level, Mob mob) {
		level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 12, 0.3, 0.4, 0.3, 0.03);
	}

	private void announceEnd(@Nullable ServerLevel level, Result result, @Nullable String reason, long gold, long xp, int rolls, long loss,
			int nightBefore, int nightAfter, WaveConfig cfg) {
		Component title;
		Component subtitle;
		switch (result) {
			case VICTORY -> {
				title = Lang.get("wave.end.victory.title");
				subtitle = Lang.get("wave.end.victory.subtitle");
				Msg.guildSound(guild, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
				if (level != null) {
					level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, heart.getX() + 0.5, heart.getY() + 1.5, heart.getZ() + 0.5, 60, 0.6, 1.0, 0.6, 0.3);
				}
			}
			case DEFEAT -> {
				title = Lang.get("wave.end.defeat.title");
				subtitle = Lang.get("wave.end.defeat.subtitle");
				Msg.guildSound(guild, SoundEvents.WITHER_DEATH, 0.7F, 1.0F);
				if (level != null) {
					level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, heart.getX() + 0.5, heart.getY() + 1, heart.getZ() + 0.5, 1, 0, 0, 0, 0);
					level.sendParticles(ParticleTypes.LARGE_SMOKE, heart.getX() + 0.5, heart.getY() + 1, heart.getZ() + 0.5, 40, 0.8, 1.0, 0.8, 0.05);
					level.playSound(null, heart.getX() + 0.5, heart.getY() + 0.5, heart.getZ() + 0.5, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5F, 0.8F);
				}
			}
			default -> {
				String reasonText = Lang.raw(reason == null ? "wave.reason.admin" : reason);
				title = Lang.get("wave.end.cancelled.title");
				subtitle = Lang.get("wave.end.cancelled.subtitle", reasonText);
			}
		}

		List<Component> lines = new ArrayList<>();
		lines.add(Lang.get("wave.recap.header"));
		lines.add(Lang.get("wave.recap.theme", displayName(), formatDifficulty(difficulty), night));
		String bossState = !hasBoss() ? Lang.raw("wave.recap.boss.none")
				: bossKilled ? Lang.raw("wave.recap.boss.killed") : Lang.raw("wave.recap.boss.alive");
		lines.add(Lang.get("wave.recap.kills", kills, total, eliteKills, bossState));
		switch (result) {
			case VICTORY -> {
				lines.add(Lang.get("wave.recap.victory", gold, xp));
				if (modifiers.hasLabel() && Math.abs(modifiers.goldMultiplier() - 1) > 1e-6) {
					lines.add(Lang.get("wave.recap.modifiers", Lang.colorize(modifiers.label()), formatMultiplier(modifiers.goldMultiplier())));
				}
				if (rolls > 0) {
					lines.add(Lang.get("wave.recap.items", rolls));
				}
				if (isEvent()) {
					lines.add(Lang.get("wave.recap.event", guild.waves.streak));
				} else {
					lines.add(Lang.get("wave.recap.next", nightAfter, guild.waves.streak));
				}
			}
			case DEFEAT -> {
				lines.add(Lang.get("wave.recap.defeat", loss, UpgradeEffects.format(
						Upgrades.reduce(guild, UpgradeEffects.DEFEAT_LOSS_REDUCTION_PCT, (double) cfg.defeat.treasuryLossPercent))));
				if (nightAfter != nightBefore) {
					lines.add(Lang.get("wave.recap.rollback", nightBefore, nightAfter));
				}
			}
			default -> lines.add(Lang.get("wave.recap.cancelled", Lang.raw(reason == null ? "wave.reason.admin" : reason)));
		}
		for (ServerPlayer player : guild.onlineMembers()) {
			Msg.title(player, title, subtitle, 10, 60, 20);
			for (Component line : lines) {
				player.sendSystemMessage(line);
			}
		}
	}

	/** Victory items for every member present during the wave (online now). */
	private void giveItems(int rolls) {
		for (UUID id : present) {
			ServerPlayer player = GuildyServer.player(id);
			if (player == null || !guild.isMember(id)) {
				continue;
			}
			int count = 0;
			for (ItemStack stack : WaveLoot.rollVictoryItems(rolls, difficulty, random)) {
				for (ItemStack part : WaveLoot.split(stack)) {
					count += part.getCount();
					ItemUtil.giveOrDrop(player, part);
				}
			}
			if (count > 0) {
				Msg.send(player, "wave.reward.received", count);
			}
		}
	}

	// ================================================================== helpers

	static String formatDifficulty(double d) {
		return String.format(java.util.Locale.FRANCE, "%.1f", d);
	}

	/** "2", "1,2"... */
	static String formatMultiplier(double m) {
		return Math.abs(m - Math.rint(m)) < 1e-6 ? String.valueOf((long) Math.rint(m)) : formatDifficulty(m);
	}

	private static BossEvent.BossBarColor barColor(String name) {
		try {
			return BossEvent.BossBarColor.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
		} catch (Exception e) {
			return BossEvent.BossBarColor.RED;
		}
	}

	/** One-line admin status. */
	Component statusLine(ServerLevel level) {
		String tag = isTest() ? " &e[test]" : isEvent() ? " &6[" + eventId + "]" : "";
		return Lang.get("wave.cmd.status.active", guild.name, displayName(), kills, total, mobs.size(), queue.size(),
				integrity, maxIntegrity, formatDifficulty(difficulty), tag);
	}

	int nearbyAtStart() {
		return nearbyAtStart;
	}
}
