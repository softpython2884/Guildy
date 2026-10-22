package dev.guildy.stats;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Live statistics of the tracked fights. A module opens a {@link FightSession} with its {@link FightArena} and registers a
 * {@link Classifier} telling which monsters belong to which session; this class then counts, for every survival / adventure
 * player:
 * <ul>
 * <li>damage dealt to the monsters of the fight (health and absorption actually lost, measured around
 * {@code LivingEntity.actuallyHurt}: armour, resistance and overkill already taken into account), credited to the player
 * behind the hit (projectiles, Guildy spell damage, pets and summoned guardians count for their owner) and their kills;</li>
 * <li>damage taken in the arena (except from players), deaths there and the totems of undying that saved them there;</li>
 * <li>health given back to other players while a healer context is open ({@link FightHealing}: Guildy spells, instant health
 * potions thrown, regeneration given), never above their missing health, and absorption given (Égide, potions);</li>
 * <li>spells cast and seconds spent in the arena.</li>
 * </ul>
 * Creative and spectator players are never counted. Server thread only; every hook is isolated by {@link Safe}.
 */
public final class FightTracker {
	/**
	 * A monster of a session and what it is there; {@code scale} turns the health it really lost into the damage counted (a
	 * guardian whose effective health is beyond the vanilla cap loses less health per hit: 1 for every other monster).
	 */
	public record Hit(FightSession session, FightKind kind, double scale) {
		public Hit(FightSession session, FightKind kind) {
			this(session, kind, 1.0);
		}
	}

	/** Tells which monsters belong to which open session (opening one lazily if it wants). */
	public interface Classifier {
		/** Cheap first check (entity tags...): could this entity belong to a fight of this module? */
		boolean interested(LivingEntity entity);

		@Nullable Hit classify(LivingEntity entity);
	}

	/** The player who owns an entity that is neither a pet nor a projectile (e.g. a guardian summoned by a spell). */
	@FunctionalInterface
	public interface OwnerResolver {
		@Nullable UUID owner(Entity entity);
	}

	private static final Map<String, FightSession> SESSIONS = new LinkedHashMap<>();
	private static final List<Classifier> CLASSIFIERS = new CopyOnWriteArrayList<>();
	private static final List<OwnerResolver> RESOLVERS = new CopyOnWriteArrayList<>();

	private FightTracker() {
	}

	public static void addClassifier(Classifier classifier) {
		CLASSIFIERS.add(classifier);
	}

	public static void addOwnerResolver(OwnerResolver resolver) {
		RESOLVERS.add(resolver);
	}

	// ================================================================== sessions

	/** At least one session is open (the hooks do nothing otherwise). */
	public static boolean tracking() {
		return !SESSIONS.isEmpty();
	}

	public static @Nullable FightSession get(String key) {
		return SESSIONS.get(key);
	}

	/** The open session of {@code key}, else a new one. */
	public static FightSession open(String key, FightArena arena, int maxPlayers) {
		FightSession session = SESSIONS.get(key);
		if (session == null) {
			session = new FightSession(key, arena, Scheduler.ticks(), maxPlayers);
			SESSIONS.put(key, session);
		}
		return session;
	}

	/** Closes and returns the session of {@code key} (null when none was open). */
	public static @Nullable FightSession close(String key) {
		return SESSIONS.remove(key);
	}

	public static List<FightSession> sessions() {
		return new ArrayList<>(SESSIONS.values());
	}

	static void clear() {
		SESSIONS.clear();
		FightHealing.clear();
	}

	/** The open session whose arena the player stands in (the first one), or null. */
	public static @Nullable FightSession sessionOf(ServerPlayer player) {
		for (FightSession session : SESSIONS.values()) {
			if (session.arena.contains(player)) {
				return session;
			}
		}
		return null;
	}

	// ================================================================== owners

	/** Counted in the statistics: neither creative nor spectator. */
	public static boolean countable(@Nullable ServerPlayer player) {
		return player != null && !player.isCreative() && !player.isSpectator();
	}

	/** The player behind an entity: itself, the shooter of a projectile, the owner of a pet or summon (see {@link FightRules#owner}). */
	public static @Nullable ServerPlayer player(@Nullable Entity entity) {
		Entity owner = FightRules.owner(entity, FightTracker::parent, e -> e instanceof ServerPlayer, FightRules.MAX_OWNER_DEPTH);
		return owner instanceof ServerPlayer player ? player : null;
	}

	private static @Nullable Entity parent(Entity entity) {
		if (entity instanceof OwnableEntity ownable) {
			LivingEntity owner = ownable.getOwner();
			if (owner != null) {
				return owner;
			}
		}
		if (entity instanceof TraceableEntity traceable) {
			Entity owner = traceable.getOwner();
			if (owner != null) {
				return owner;
			}
		}
		for (OwnerResolver resolver : RESOLVERS) {
			UUID uuid = resolver.owner(entity);
			ServerPlayer player = uuid == null ? null : GuildyServer.player(uuid);
			if (player != null) {
				return player;
			}
		}
		return null;
	}

	/** The player responsible for a damage source (its causing entity, else its direct one). */
	public static @Nullable ServerPlayer attacker(DamageSource source) {
		Entity entity = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
		return player(entity);
	}

	private static @Nullable FightTally tally(FightSession session, ServerPlayer player) {
		return countable(player) ? session.tally(player.getUUID(), player.getName().getString()) : null;
	}

	// ================================================================== hooks (stats.mixin, events)

	/** {@code victim} just lost {@code amount} health + absorption to {@code source} (LivingEntity.hurtServer). */
	public static void hurt(LivingEntity victim, DamageSource source, float amount) {
		if (!(amount > 0) || CLASSIFIERS.isEmpty() || !(victim.level() instanceof ServerLevel)) {
			return;
		}
		try {
			if (victim instanceof ServerPlayer player) {
				if (!SESSIONS.isEmpty()) {
					taken(player, source, amount);
				}
				return;
			}
			for (Classifier classifier : CLASSIFIERS) {
				if (classifier.interested(victim)) {
					Hit hit = classifier.classify(victim);
					if (hit != null) {
						dealt(hit, victim, source, amount);
						return;
					}
				}
			}
		} catch (Throwable t) {
			Safe.log("fight.hurt", t);
		}
	}

	private static void dealt(Hit hit, LivingEntity victim, DamageSource source, float amount) {
		hit.session().kind(victim.getUUID(), hit.kind());
		ServerPlayer attacker = attacker(source);
		FightTally tally = attacker == null ? null : tally(hit.session(), attacker);
		if (tally != null) {
			tally.addDamage(hit.kind(), amount * Math.max(1.0, hit.scale()));
		}
	}

	private static void taken(ServerPlayer player, DamageSource source, float amount) {
		if (!countable(player)) {
			return;
		}
		// Friendly fire, one's own arrows and explosions are not the monsters' doing.
		if (attacker(source) != null) {
			return;
		}
		FightSession session = sessionOf(player);
		FightTally tally = session == null ? null : tally(session, player);
		if (tally != null) {
			tally.addTaken(amount);
		}
	}

	/** ServerLivingEntityEvents.AFTER_DEATH: a player died in an arena, or a monster of a fight was killed (the guardian excepted). */
	static void death(LivingEntity victim, DamageSource source) {
		if (SESSIONS.isEmpty()) {
			return;
		}
		if (victim instanceof ServerPlayer player) {
			FightSession session = countable(player) ? sessionOf(player) : null;
			FightTally tally = session == null ? null : tally(session, player);
			if (tally != null) {
				tally.addDeath();
			}
			return;
		}
		for (FightSession session : SESSIONS.values()) {
			FightKind kind = session.kindOf(victim.getUUID());
			if (kind == null) {
				continue;
			}
			// The guardian's death is the end of the fight: its module credits the final blow (see creditFinalBlow).
			if (kind != FightKind.BOSS) {
				session.killed(kind);
				ServerPlayer killer = attacker(source);
				FightTally tally = killer == null ? null : tally(session, killer);
				if (tally != null) {
					tally.addKill(kind);
				}
			}
			return;
		}
	}

	/**
	 * A totem of undying (or any other death protection item, either hand) just saved {@code player} from death
	 * ({@code UsedTotemTrigger.trigger}, called by {@code LivingEntity.checkTotemDeathProtection} only when one is consumed).
	 */
	public static void totemUsed(ServerPlayer player) {
		if (SESSIONS.isEmpty()) {
			return;
		}
		Safe.run("fight.totem", () -> {
			FightSession session = countable(player) ? sessionOf(player) : null;
			FightTally tally = session == null ? null : tally(session, player);
			if (tally != null) {
				tally.addTotem();
			}
		});
	}

	/** The guardian of {@code session} was killed by {@code source}: kill and final blow for the player behind it. */
	public static void creditFinalBlow(FightSession session, DamageSource source) {
		session.killed(FightKind.BOSS);
		ServerPlayer killer = attacker(source);
		FightTally tally = killer == null ? null : tally(session, killer);
		if (tally != null) {
			tally.addKill(FightKind.BOSS);
			tally.setFinalBlow();
		}
	}

	/** {@code target} is healed by {@code amount} (LivingEntity.heal, health / max health before it) inside a healer context. */
	public static void healed(LivingEntity target, float health, float maxHealth, float amount) {
		ServerPlayer giver = FightHealing.healer();
		if (giver == null || giver == target || SESSIONS.isEmpty() || !(target instanceof ServerPlayer player)) {
			return;
		}
		try {
			double effective = FightRules.effectiveHeal(health, maxHealth, amount);
			if (effective <= 0 || !countable(giver) || !countable(player)) {
				return;
			}
			FightSession session = sessionOf(player);
			if (session == null) {
				session = sessionOf(giver);
			}
			FightTally tally = session == null ? null : tally(session, giver);
			if (tally != null) {
				tally.addHealing(effective);
			}
		} catch (Throwable t) {
			Safe.log("fight.heal", t);
		}
	}

	/**
	 * {@code target} received {@code effect} (LivingEntity.addEffect returned {@code changed}; absorption before it): absorption
	 * given by another player is counted, a regeneration given by another player is remembered (its ticks heal for them).
	 */
	public static void effectAdded(LivingEntity target, MobEffectInstance effect, @Nullable Entity source, float absorptionBefore, boolean changed) {
		if (SESSIONS.isEmpty() || !(target instanceof ServerPlayer player)) {
			return;
		}
		try {
			boolean absorption = effect.is(MobEffects.ABSORPTION);
			boolean regeneration = effect.is(MobEffects.REGENERATION);
			if (!absorption && !regeneration) {
				return;
			}
			ServerPlayer giver = FightHealing.healer();
			if (giver == null) {
				giver = player(source);
			}
			boolean other = giver != null && giver != player && countable(giver);
			if (regeneration) {
				if (changed) {
					if (other) {
						FightHealing.regeneration(player.getUUID(), giver.getUUID(), effect.isInfiniteDuration() ? -1 : effect.getDuration());
					} else {
						FightHealing.forget(player.getUUID());
					}
				}
				return;
			}
			double gained = FightRules.gained(absorptionBefore, player.getAbsorptionAmount());
			if (!other || gained <= 0 || !countable(player)) {
				return;
			}
			FightSession session = sessionOf(player);
			if (session == null) {
				session = sessionOf(giver);
			}
			FightTally tally = session == null ? null : tally(session, giver);
			if (tally != null) {
				tally.addShield(gained);
			}
		} catch (Throwable t) {
			Safe.log("fight.effect", t);
		}
	}

	/** A spell was cast (Guildy scroll or grimoire). */
	public static void spellCast(ServerPlayer caster) {
		if (SESSIONS.isEmpty()) {
			return;
		}
		Safe.run("fight.spell", () -> {
			FightSession session = sessionOf(caster);
			FightTally tally = session == null ? null : tally(session, caster);
			if (tally != null) {
				tally.addSpell();
			}
		});
	}

	/** Every second: one second in the arena for each player taking part. */
	static void tickSecond() {
		for (FightSession session : new ArrayList<>(SESSIONS.values())) {
			Safe.run("fight.presence", () -> {
				for (ServerPlayer player : session.arena.present()) {
					FightTally tally = tally(session, player);
					if (tally != null) {
						tally.addSecond();
					}
				}
			});
		}
	}
}
