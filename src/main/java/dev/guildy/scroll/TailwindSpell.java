package dev.guildy.scroll;

import dev.guildy.core.Scheduler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hâte du vent: a whirlwind rises around the caster (three spiralling ribbons of wind, gusts, mint and white streaks) and
 * blows outward in two gust rings; every ally around (the caster, guild members, guild villagers) gets Speed and Jump Boost
 * for the duration and, unless disabled, no fall damage ({@link #allowDamage}). The blessed allies leave wind streaks behind
 * them for a few seconds.
 */
final class TailwindSpell extends SpellEffect {
	private static final int GUST = 24;
	/** The wind trails of the blessed allies are drawn this long (the effects last the whole duration). */
	private static final int TRAILS = 6 * 20;
	/** Allies protected from falls at most (bounded map). */
	private static final int MAX_GRACE = 512;
	/** Ally -> server tick until which falls do not hurt them. */
	private static final Map<UUID, Long> GRACE = new HashMap<>();

	private final Vec3 center;
	private final double radius;
	private final int duration;
	private final List<UUID> blessed = new ArrayList<>();

	TailwindSpell(SpellContext ctx) {
		super(ctx);
		this.center = ctx.caster.position();
		this.radius = ctx.p("radius");
		this.duration = ctx.ticks("duration");
	}

	static void init() {
		Scheduler.every("scroll.tailwind.prune", 20, 13, TailwindSpell::prune);
	}

	@Override
	Vec3 anchor() {
		return center;
	}

	@Override
	void tick(Fx fx) {
		Spell spell = ctx.spell;
		if (age == 0) {
			bless(fx);
			sound(center, SoundEvents.BREEZE_WIND_CHARGE_BURST, 2.0F, 1.2F);
			sound(center, SoundEvents.WIND_CHARGE_BURST, 2.0F, 0.8F);
			sound(center, SoundEvents.PHANTOM_FLAP, 1.5F, 1.5F);
			fx.dot(Fx.flash(spell.color), center.x, center.y + 1, center.z);
			fx.burst(ParticleTypes.GUST, center.x, center.y + 0.5, center.z, 1, 0, 0, 0, 0);
		}
		if (age < GUST) {
			whirlwind(fx, spell);
		}
		if (age == 6 || age == 14) {
			fx.burst(ParticleTypes.SMALL_GUST, center.x, center.y + 0.6, center.z, 8, radius * 0.4, 0.3, radius * 0.4, 0.0);
			sound(center, SoundEvents.BREEZE_IDLE_GROUND, 1.5F, 1.4F);
		}
		trails(fx, spell);
		if (age >= Math.min(duration, TRAILS)) {
			finish();
		}
	}

	/** Three ribbons spiralling up and widening, and two gust rings blowing outward. */
	private void whirlwind(Fx fx, Spell spell) {
		double t = age / (double) GUST;
		for (int k = 0; k < 3; k++) {
			for (int j = 0; j < 10; j++) {
				double angle = age * 0.45 + k * Math.PI * 2 / 3 + j * 0.35;
				double h = j * 0.32 + t * 1.2;
				double r = 0.6 + j * 0.12 + t * 0.8;
				double cos = Math.cos(angle);
				double sin = Math.sin(angle);
				fx.move(Fx.fade(j % 3 == 0 ? 0xFFFFFF : spell.color, spell.accent, 1.0F), center.x + cos * r, center.y + h, center.z + sin * r,
						-sin * 0.12, 0.04, cos * 0.12);
			}
		}
		for (int wave = 0; wave < 2; wave++) {
			int start = wave * 8;
			int local = age - start;
			if (local < 0 || local > 14) {
				continue;
			}
			double r = radius * easeOut(local / 14.0);
			int points = (int) Math.max(14, r * 4);
			fx.arc(Fx.dust(wave == 0 ? spell.color : 0xFFFFFF, 1.1F), center.add(0, 0.3 + wave * 0.3, 0), r, age * 0.2, age * 0.2 + Math.PI * 2, points,
					true);
		}
		if (age % 3 == 0) {
			fx.burst(ParticleTypes.CLOUD, center.x, center.y + 0.2, center.z, 4, radius * 0.3, 0.05, radius * 0.3, 0.05);
		}
	}

	/** Every few ticks, a streak of wind behind each blessed ally on the move. */
	private void trails(Fx fx, Spell spell) {
		if (age % 3 != 0) {
			return;
		}
		for (UUID uuid : blessed) {
			if (!(level.getEntity(uuid) instanceof LivingEntity ally) || !ally.isAlive()) {
				continue;
			}
			Vec3 motion = ally.getDeltaMovement();
			double speed = motion.horizontalDistance();
			if (speed < 0.05 && age > GUST) {
				continue;
			}
			Vec3 back = speed < 1.0E-3 ? Vec3.ZERO : new Vec3(-motion.x, 0, -motion.z).normalize();
			for (int i = 0; i < 3; i++) {
				Vec3 p = ally.position().add(back.scale(0.4 + i * 0.35)).add(0, 0.3 + i * 0.25, 0);
				fx.move(Fx.dust(i == 1 ? spell.accent : spell.color, 0.8F), p, back.scale(0.08));
			}
		}
	}

	/** Speed, Jump Boost and the fall protection to the allies around the caster. */
	private void bless(Fx fx) {
		int ticks = duration;
		int speed = ctx.i("speedLevel");
		int jump = ctx.i("jumpLevel");
		boolean noFall = ctx.i("noFall") > 0;
		long until = Scheduler.ticks() + ticks;
		for (LivingEntity ally : SpellTargets.allies(ctx, level, center, radius)) {
			ally.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, speed - 1, false, true));
			ally.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, ticks, jump - 1, false, true));
			if (noFall) {
				protect(ally.getUUID(), until);
			}
			blessed.add(ally.getUUID());
			fx.burst(ParticleTypes.SMALL_GUST, ally.getX(), ally.getY(0.4), ally.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
			fx.burst(Fx.fade(ctx.spell.color, 0xFFFFFF, 0.9F), ally.getX(), ally.getY(0.5), ally.getZ(), 10, 0.4, 0.6, 0.4, 0.05);
		}
	}

	// ================================================================== fall protection

	private static void protect(UUID ally, long until) {
		Long old = GRACE.get(ally);
		if (old == null && GRACE.size() >= MAX_GRACE) {
			prune();
			if (GRACE.size() >= MAX_GRACE) {
				return;
			}
		}
		GRACE.put(ally, Math.max(until, old == null ? 0 : old));
	}

	/** Under the wind's protection right now (tests). */
	static boolean protectedFromFalls(UUID ally) {
		Long until = GRACE.get(ally);
		return until != null && until >= Scheduler.ticks();
	}

	/** ALLOW_DAMAGE: a fall does not hurt an ally under the wind's protection (a puff of wind cushions it). */
	static boolean allowDamage(LivingEntity entity, DamageSource source) {
		if (GRACE.isEmpty() || !source.is(DamageTypeTags.IS_FALL) || !protectedFromFalls(entity.getUUID())) {
			return true;
		}
		if (entity.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.SMALL_GUST, true, false, entity.getX(), entity.getY() + 0.2, entity.getZ(), 3, 0.3, 0.05, 0.3, 0.0);
			level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.5F, 1.6F);
		}
		return false;
	}

	private static void prune() {
		long now = Scheduler.ticks();
		Iterator<Long> it = GRACE.values().iterator();
		while (it.hasNext()) {
			if (it.next() < now) {
				it.remove();
			}
		}
	}

	/** Server stop. */
	static void clearAll() {
		GRACE.clear();
	}
}
