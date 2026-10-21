package dev.guildy.scroll;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Fléau: a cursed miasma settles on the aimed area to the elder guardian's curse: a violet heptagram and a venom-green ring
 * on the ground, sickly fumes rising, a dark cloud overhead dripping venom, wandering souls. Every half second, the monsters
 * inside glow, are weakened and slowed, and take more damage from every source ({@link Vulnerability}; bosses only get
 * their share); each newly cursed monster is struck by a tendril from the cloud and carries a brand under its feet. The
 * curse lingers {@link #LINGER} ticks after the monster leaves the area or the miasma lifts.
 */
final class ScourgeSpell extends SpellEffect {
	private static final int PULSE = 10;
	static final int LINGER = 60;
	private static final double CLOUD = 4.6;
	private static final int DARK = 0x1E0628;

	private final Vec3 center;
	private final double radius;
	private final int duration;
	private final double vulnerability;
	private final double bossShare;
	private final int weakness;
	private final int slowness;
	private final Set<UUID> cursed = new HashSet<>();

	ScourgeSpell(SpellContext ctx, Vec3 center) {
		super(ctx);
		this.center = center;
		this.radius = ctx.p("radius");
		this.duration = ctx.ticks("duration");
		this.vulnerability = ctx.p("vulnerability");
		this.bossShare = ctx.p("bossShare");
		this.weakness = ctx.i("weaknessLevel");
		this.slowness = ctx.i("slowLevel");
	}

	@Override
	Vec3 anchor() {
		return center;
	}

	@Override
	void tick(Fx fx) {
		Spell spell = ctx.spell;
		if (age == 0) {
			sound(center, SoundEvents.ELDER_GUARDIAN_CURSE, 2.0F, 0.8F);
			sound(center, SoundEvents.WITCH_CELEBRATE, 1.6F, 0.6F);
			sound(center, SoundEvents.SCULK_CATALYST_BLOOM, 2.0F, 0.7F);
			fx.dot(Fx.flash(spell.color), center.x, center.y + 1.2, center.z);
			fx.sphereBurst(Fx.fade(spell.color, spell.accent, 1.5F), center.add(0, 1.2, 0), 0.5, 48, 0.3);
		}
		double phase = age * 0.06;
		double fadeIn = Math.min(1, (age + 1) / 12.0);
		double r = radius * (0.3 + 0.7 * easeOut(fadeIn));
		// Ground: venom ring, counter-rotating violet heptagram.
		if (age % 2 == 0) {
			fx.ring(Fx.fade(spell.color, spell.accent, 1.3F), center.add(0, 0.15, 0), r, (int) Math.max(16, r * 6), phase);
		}
		if (age % 3 == 0) {
			fx.polygram(Fx.dust(spell.accent, 1.1F), center.add(0, 0.12, 0), r * 0.85, 7, 3, -phase * 0.8, fadeIn, 2);
		}
		// Fumes rising from the cursed ground.
		for (int i = 0; i < 6; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double d = Math.sqrt(random.nextDouble()) * r;
			fx.move(Fx.fade(spell.color, spell.accent, 1.2F), center.x + Math.cos(angle) * d, center.y + 0.1 + random.nextDouble() * 0.4,
					center.z + Math.sin(angle) * d, 0, 0.03 + random.nextDouble() * 0.03, 0);
		}
		// The dark cloud overhead and the venom dripping from it.
		if (age % 2 == 0) {
			fx.burst(Fx.dust(DARK, 2.0F), center.x, center.y + CLOUD, center.z, 8, r * 0.45, 0.2, r * 0.45, 0.0);
			fx.burst(ParticleTypes.LARGE_SMOKE, center.x, center.y + CLOUD + 0.2, center.z, 2, r * 0.4, 0.1, r * 0.4, 0.005);
		}
		for (int i = 0; i < 4; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double d = Math.sqrt(random.nextDouble()) * r * 0.9;
			fx.move(Fx.dust(spell.color, 0.9F), center.x + Math.cos(angle) * d, center.y + CLOUD - 0.2, center.z + Math.sin(angle) * d, 0, -0.3, 0);
		}
		if (age % 6 == 0) {
			fx.burst(ParticleTypes.SOUL, center.x, center.y + 0.4, center.z, 2, r * 0.4, 0.2, r * 0.4, 0.02);
		}
		if (age % PULSE == 0) {
			afflict(fx);
		}
		brands(fx);
		if (age % 40 == 20) {
			sound(center, SoundEvents.WITCH_AMBIENT, 1.2F, 0.5F);
		}
		if (age >= duration) {
			fx.sphereBurst(Fx.fade(spell.accent, DARK, 1.4F), center.add(0, 1, 0), radius * 0.4, 40, 0.1);
			fx.burst(ParticleTypes.LARGE_SMOKE, center.x, center.y + 1, center.z, 12, radius * 0.4, 0.4, radius * 0.4, 0.02);
			sound(center, SoundEvents.SOUL_ESCAPE, 2.0F, 0.6F);
			finish();
		}
	}

	/** Every monster inside: glowing, weakness, slowness and the vulnerability mark, for {@link #LINGER} ticks. */
	private void afflict(Fx fx) {
		for (LivingEntity target : SpellTargets.hostiles(ctx, level, center, radius + 6, 60)) {
			double dx = target.getX() - center.x;
			double dz = target.getZ() - center.z;
			if (dx * dx + dz * dz > radius * radius || Math.abs(target.getY() - center.y) > 6) {
				continue;
			}
			boolean boss = SpellTargets.isBoss(target);
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, LINGER, weakness - 1, false, true));
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, LINGER, slowness - 1, false, true));
			target.addEffect(new MobEffectInstance(MobEffects.GLOWING, LINGER, 0, false, false));
			Vulnerability.mark(target, DebuffRules.vulnerability(vulnerability, boss, bossShare), LINGER);
			if (cursed.add(target.getUUID())) {
				Vec3 head = target.position().add(0, target.getBbHeight() + 0.2, 0);
				fx.line(Fx.fade(ctx.spell.color, ctx.spell.accent, 1.0F), center.add(0, CLOUD - 0.3, 0), head, 3);
				fx.burst(ParticleTypes.WITCH, target.getX(), target.getY(0.6), target.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
				fx.burst(ParticleTypes.SCULK_SOUL, target.getX(), target.getY(0.5), target.getZ(), 3, 0.2, 0.3, 0.2, 0.02);
				curse(fx, target.position(), 1.0);
				sound(target.position(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, 0.8F, 1.4F);
			}
		}
	}

	/** The brand of the cursed monsters still alive: a violet ring under their feet and venom dripping from them. */
	private void brands(Fx fx) {
		if (age % 4 != 0) {
			return;
		}
		for (UUID uuid : cursed) {
			if (!(level.getEntity(uuid) instanceof LivingEntity target) || !target.isAlive() || Vulnerability.percent(target) <= 0) {
				continue;
			}
			fx.ring(Fx.dust(ctx.spell.accent, 0.9F), target.position().add(0, 0.1, 0), Math.max(0.6, target.getBbWidth() * 0.8), 10, age * 0.25);
			fx.move(Fx.dust(ctx.spell.color, 0.8F), target.getX(), target.getY() + target.getBbHeight() * 0.8, target.getZ(), 0, -0.12, 0);
		}
	}
}
