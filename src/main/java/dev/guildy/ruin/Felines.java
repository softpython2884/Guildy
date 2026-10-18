package dev.guildy.ruin;

import dev.guildy.core.Lang;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.core.GuildyServer;
import dev.guildy.ruin.mixin.MobTargetSelectorAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.CatVariant;
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The great cats of the ruins (ocelots and cats: the celestial jaguar, its black cursed form, the spirit jaguars, any feline
 * an admin puts in ruins.json5). Vanilla felines are shy: their goals are replaced by a hunter's (chase the nearest survival
 * / adventure player at a sprint, strike in melee, retaliate against players only; no fleeing, no tempting, no trusting, no
 * breeding, no sitting), they climb a full block (step height 1, like a ravager) and their variant can be forced (the
 * all-black cat). The ruin mobs cannot be fed, tamed, leashed or renamed (the interactions are refused in
 * {@link Encounters}). The jaguar abilities hold the hunt for a moment ({@link #hold}); the spirit jaguars live a few
 * seconds ({@link #addSpirit}, ticked here).
 */
final class Felines {
	/** Sprint speed of the vanilla felines (their sprinting pose is shown at exactly this speed modifier). */
	static final double SPRINT = Ocelot.SPRINT_SPEED_MOD;
	private static final double STEP_HEIGHT = 1.0;
	private static final Map<UUID, Long> HOLD = new HashMap<>();
	private static final Map<UUID, Spirit> SPIRITS = new LinkedHashMap<>();

	/** A temporary spirit jaguar of a fight. */
	record Spirit(UUID id, ResourceKey<Level> dimension, Encounter encounter, long until, int color) {
	}

	private Felines() {
	}

	static void init() {
		Scheduler.every("ruin.spirits", 1, 0, () -> {
			if (!SPIRITS.isEmpty()) {
				Safe.run("ruin.spirits", Felines::tickSpirits);
			}
		});
	}

	/** An ocelot or a cat. */
	static boolean isFeline(@Nullable Entity entity) {
		return entity instanceof Ocelot || entity instanceof Cat;
	}

	// ================================================================== the hunter

	/** A shy vanilla feline becomes a relentless hunter of players (no effect on other mobs). */
	static void hunt(Mob mob) {
		if (!isFeline(mob) || !(mob instanceof PathfinderMob hunter)) {
			return;
		}
		if (mob instanceof AgeableMob ageable && ageable.isBaby()) {
			ageable.setBaby(false);
		}
		if (mob instanceof Cat cat) {
			cat.setOrderedToSit(false);
			cat.setInSittingPose(false);
			cat.setLying(false);
		}
		GoalSelector goals = mob.getGoalSelector();
		GoalSelector targets = ((MobTargetSelectorAccessor) mob).guildy$targetSelector();
		goals.removeAllGoals(goal -> true);
		targets.removeAllGoals(goal -> true);
		goals.addGoal(0, new FloatGoal(mob));
		goals.addGoal(2, new HuntGoal(hunter));
		goals.addGoal(7, new WaterAvoidingRandomStrollGoal(hunter, 0.8));
		goals.addGoal(8, new LookAtPlayerGoal(mob, Player.class, 16.0F));
		targets.addGoal(1, new PlayerRetaliation(hunter));
		targets.addGoal(2, new NearestAttackableTargetGoal<>(mob, Player.class, true));
		AttributeInstance step = mob.getAttribute(Attributes.STEP_HEIGHT);
		if (step != null && step.getBaseValue() < STEP_HEIGHT) {
			step.setBaseValue(STEP_HEIGHT);
		}
	}

	/**
	 * Forces a vanilla variant ("minecraft:all_black" for a cat). @return false when the mob has no such variant (unknown id,
	 * not a cat)
	 */
	static boolean applyVariant(Mob mob, @Nullable String variant) {
		if (variant == null || variant.isBlank() || !(mob instanceof Cat cat)) {
			return false;
		}
		Identifier id = Identifier.tryParse(variant);
		if (id == null) {
			return false;
		}
		Optional<Holder.Reference<CatVariant>> holder = mob.registryAccess().lookupOrThrow(Registries.CAT_VARIANT).get(id);
		if (holder.isEmpty()) {
			return false;
		}
		cat.setComponent(DataComponents.CAT_VARIANT, holder.get());
		return true;
	}

	/** The guardian itself: silent (its meows would not fit a jaguar), growls when hurt ({@link #growl}). */
	static void styleGuardian(Mob mob) {
		if (isFeline(mob)) {
			mob.setSilent(true);
		}
	}

	/** A deep growl instead of the meow of a hurt guardian. */
	static void growl(ServerLevel level, Mob boss) {
		Vec3 at = boss.position();
		RuinFx.sound(level, at, SoundEvents.OCELOT_HURT, 1.6F, 0.45F + level.getRandom().nextFloat() * 0.1F);
		RuinFx.sound(level, at, SoundEvents.POLAR_BEAR_WARNING, 0.6F, 1.5F);
	}

	/** The death cry of a feline guardian. */
	static void deathCry(ServerLevel level, Mob boss) {
		Vec3 at = boss.position();
		RuinFx.sound(level, at, SoundEvents.OCELOT_DEATH, 2.5F, 0.4F);
		RuinFx.sound(level, at, SoundEvents.RAVAGER_DEATH, 1.0F, 1.3F);
	}

	// ================================================================== holding the hunt

	/** The mob stands still for {@code ticks} (an ability winds up): no chase, no melee. */
	static void hold(Mob mob, int ticks) {
		HOLD.put(mob.getUUID(), mob.level().getGameTime() + Math.max(0, ticks));
		mob.getNavigation().stop();
		if (HOLD.size() > 256) {
			long now = mob.level().getGameTime();
			HOLD.values().removeIf(until -> until < now);
		}
	}

	/** The hunt resumes now. */
	static void release(Mob mob) {
		HOLD.remove(mob.getUUID());
	}

	static boolean held(Mob mob) {
		Long until = HOLD.get(mob.getUUID());
		if (until == null) {
			return false;
		}
		if (mob.level().getGameTime() >= until) {
			HOLD.remove(mob.getUUID());
			return false;
		}
		return true;
	}

	// ================================================================== spirit jaguars

	/** Tracks a spirit jaguar until {@code until} (game time); it then vanishes. */
	static void addSpirit(Mob spirit, Encounter encounter, long until, int color) {
		SPIRITS.put(spirit.getUUID(), new Spirit(spirit.getUUID(), spirit.level().dimension(), encounter, until, color));
	}

	/** Living spirit jaguars of a fight (tests). */
	static int spirits(Encounter encounter) {
		int n = 0;
		for (Spirit spirit : SPIRITS.values()) {
			if (spirit.encounter() == encounter) {
				n++;
			}
		}
		return n;
	}

	static void clear() {
		SPIRITS.clear();
		HOLD.clear();
	}

	private static void tickSpirits() {
		MinecraftServer server = GuildyServer.server();
		if (server == null) {
			SPIRITS.clear();
			return;
		}
		Iterator<Spirit> it = SPIRITS.values().iterator();
		while (it.hasNext()) {
			Spirit spirit = it.next();
			ServerLevel level = server.getLevel(spirit.dimension());
			Entity entity = level == null ? null : level.getEntity(spirit.id());
			if (!(entity instanceof Mob mob) || !mob.isAlive() || level == null) {
				it.remove();
				Encounters.untrack(spirit.encounter(), spirit.id());
				continue;
			}
			long now = level.getGameTime();
			Vec3 c = mob.position().add(0, mob.getBbHeight() * 0.5, 0);
			if (now >= spirit.until() || !Encounters.isActive(spirit.encounter().ruin)) {
				// Back to the spirit world: a puff of light.
				RuinFx.burst(level, RitualFx.dust(spirit.color(), 1.3F), c, 16, 0.4, 0.0);
				RuinFx.burst(level, ParticleTypes.END_ROD, c, 6, 0.3, 0.05);
				RuinFx.sound(level, c, SoundEvents.ALLAY_DEATH, 0.8F, 1.4F);
				mob.discard();
				it.remove();
				Encounters.untrack(spirit.encounter(), spirit.id());
				continue;
			}
			if ((now + spirit.id().hashCode()) % 3 == 0) {
				double w = mob.getBbWidth() * 0.5;
				level.sendParticles(RitualFx.dust(spirit.color(), 0.9F), c.x, c.y, c.z, 2, w, mob.getBbHeight() * 0.3, w, 0.0);
			}
			if ((now + spirit.id().hashCode()) % 7 == 0) {
				level.sendParticles(ParticleTypes.END_ROD, c.x, mob.getY() + 0.1, c.z, 1, 0.15, 0.0, 0.15, 0.01);
			}
		}
	}

	/** Name of a spirit jaguar (normal or cursed form). */
	static String spiritName(boolean cursed) {
		return Lang.raw(cursed ? "ruin.jaguar.spirit.cursed" : "ruin.jaguar.spirit.name");
	}

	// ================================================================== goals

	/**
	 * Chases its target at a sprint (straight at it when no path is found: a big cat never gives up) and strikes in melee
	 * once per second; stands still while an ability holds it.
	 */
	static final class HuntGoal extends Goal {
		private final PathfinderMob mob;
		private int repath;
		private int cooldown;

		HuntGoal(PathfinderMob mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		private @Nullable LivingEntity prey() {
			LivingEntity target = mob.getTarget();
			return target != null && target.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target) ? target : null;
		}

		@Override
		public boolean canUse() {
			return prey() != null;
		}

		@Override
		public boolean canContinueToUse() {
			return prey() != null;
		}

		@Override
		public void start() {
			mob.setAggressive(true);
			repath = 0;
			cooldown = 0;
		}

		@Override
		public void stop() {
			LivingEntity target = mob.getTarget();
			if (target != null && !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target)) {
				mob.setTarget(null);
			}
			mob.setAggressive(false);
			mob.getNavigation().stop();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = prey();
			if (target == null) {
				return;
			}
			mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
			cooldown = Math.max(0, cooldown - 1);
			if (held(mob)) {
				mob.getNavigation().stop();
				return;
			}
			if (--repath <= 0) {
				double dist = mob.distanceToSqr(target);
				repath = 4 + mob.getRandom().nextInt(7) + (dist > 256.0 ? 5 : 0);
				if (!mob.getNavigation().moveTo(target, SPRINT)) {
					repath += 10;
				}
			}
			if (mob.getNavigation().isDone() && !mob.isWithinMeleeAttackRange(target) && mob.getSensing().hasLineOfSight(target)) {
				// No path (a big body, a ledge): straight at the prey.
				mob.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), SPRINT);
			}
			if (cooldown <= 0 && mob.isWithinMeleeAttackRange(target) && mob.getSensing().hasLineOfSight(target)
					&& mob.level() instanceof ServerLevel level) {
				cooldown = adjustedTickDelay(20);
				if (mob.doHurtTarget(level, target) && mob.getBbWidth() > 1.5) {
					RuinFx.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.6F);
					level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 1, 0, 0, 0, 0);
				}
			}
		}
	}

	/** Retaliation against the players who hurt it (never against a stray arrow of its own escort). */
	static final class PlayerRetaliation extends HurtByTargetGoal {
		PlayerRetaliation(PathfinderMob mob) {
			super(mob);
		}

		@Override
		public boolean canUse() {
			return mob.getLastHurtByMob() instanceof Player && super.canUse();
		}
	}
}
