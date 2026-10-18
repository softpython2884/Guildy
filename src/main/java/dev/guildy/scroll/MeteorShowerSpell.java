package dev.guildy.scroll;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pluie de météores: a red runic circle marks the area around the aimed point, then burning meteors fall one after the other
 * from behind the caster (fire trail, smoke, color-fading embers), each one announced by a shrinking warning ring; every
 * impact bursts (no real explosion, no block touched) and hurts, ignites and throws back the monsters around it.
 */
final class MeteorShowerSpell extends SpellEffect {
	private static final int FLIGHT = 16;
	private static final int INTERVAL = 4;
	private static final int EMBERS = 24;
	/** Meteors landed per caster since the server started (statistics, tests). */
	private static final Map<UUID, Integer> IMPACTS = new HashMap<>();

	private final Vec3 center;
	private final Vec3 from;
	private final int count;
	private final double radius;
	private final double damage;
	private final double impactRadius;
	private final float fireSeconds;
	private final List<Meteor> meteors = new ArrayList<>();
	private final List<Crater> craters = new ArrayList<>();
	private int launched;

	private static final class Meteor {
		final Vec3 start;
		final Vec3 impact;
		Vec3 last;
		int age;

		Meteor(Vec3 start, Vec3 impact) {
			this.start = start;
			this.impact = impact;
			this.last = start;
		}
	}

	private static final class Crater {
		final Vec3 pos;
		int left = EMBERS;

		Crater(Vec3 pos) {
			this.pos = pos;
		}
	}

	MeteorShowerSpell(SpellContext ctx, Vec3 center) {
		super(ctx);
		this.center = center;
		Vec3 look = ctx.caster.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
		// The meteors come from behind the caster, high in the sky.
		this.from = new Vec3(-flat.x * 12, 28, -flat.z * 12);
		this.count = ctx.i("count");
		this.radius = ctx.p("radius");
		this.damage = ctx.p("damage");
		this.impactRadius = ctx.p("impactRadius");
		this.fireSeconds = (float) ctx.p("fireSeconds");
	}

	/** Meteors landed for this caster since the server started. */
	static int impacts(UUID caster) {
		return IMPACTS.getOrDefault(caster, 0);
	}

	/** Server stop. */
	static void clearImpacts() {
		IMPACTS.clear();
	}

	@Override
	Vec3 anchor() {
		return center;
	}

	@Override
	void tick(Fx fx) {
		Spell spell = ctx.spell;
		if (age == 0) {
			sound(center, SoundEvents.BLAZE_SHOOT, 2.5F, 0.5F);
			sound(center, SoundEvents.EVOKER_CAST_SPELL, 2.0F, 0.7F);
		}
		// Area mark: red runic circle, redrawn every few ticks while meteors fall.
		if (launched < count && age % 3 == 0) {
			fx.runeCircle(center.add(0, 0.15, 0), radius, spell.color, spell.accent, Math.min(1, (age + 6) / 18.0), age * 0.04);
		}
		if (launched < count && age >= 6 && (age - 6) % INTERVAL == 0) {
			launch();
		}
		Iterator<Meteor> it = meteors.iterator();
		while (it.hasNext()) {
			Meteor meteor = it.next();
			meteor.age++;
			double t = Math.min(1, meteor.age / (double) FLIGHT);
			// Accelerating fall.
			Vec3 pos = meteor.start.add(meteor.impact.subtract(meteor.start).scale(t * t * 0.6 + t * 0.4));
			drawMeteor(fx, meteor, pos, t);
			meteor.last = pos;
			if (meteor.age >= FLIGHT) {
				impact(fx, meteor.impact);
				it.remove();
			}
		}
		Iterator<Crater> cit = craters.iterator();
		while (cit.hasNext()) {
			Crater crater = cit.next();
			if (crater.left % 3 == 0) {
				fx.burst(ParticleTypes.FLAME, crater.pos.x, crater.pos.y + 0.2, crater.pos.z, 3, impactRadius * 0.4, 0.1, impactRadius * 0.4, 0.01);
				fx.burst(ParticleTypes.SMOKE, crater.pos.x, crater.pos.y + 0.4, crater.pos.z, 2, impactRadius * 0.3, 0.2, impactRadius * 0.3, 0.02);
			}
			if (--crater.left <= 0) {
				cit.remove();
			}
		}
		if (launched >= count && meteors.isEmpty() && craters.isEmpty()) {
			finish();
		}
	}

	private void launch() {
		double angle = random.nextDouble() * Math.PI * 2;
		double distance = Math.sqrt(random.nextDouble()) * radius;
		Vec3 point = center.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
		// The ground near the area's height (never deep down a ravine nor on a far roof).
		Vec3 ground = SpellTargets.ground(level, point.add(0, 6, 0), 14);
		Vec3 impact = Math.abs(ground.y - center.y) <= 8 ? ground : point;
		Vec3 start = impact.add(from).add((random.nextDouble() - 0.5) * 6, random.nextDouble() * 4, (random.nextDouble() - 0.5) * 6);
		meteors.add(new Meteor(start, impact));
		launched++;
		sound(start, SoundEvents.FIRECHARGE_USE, 2.0F, 0.6F + random.nextFloat() * 0.3F);
	}

	private void drawMeteor(Fx fx, Meteor meteor, Vec3 pos, double t) {
		// Head: fire core, a big fading ember, a few lava sparks.
		fx.burst(ParticleTypes.FLAME, pos, 8, 0.3, 0.03);
		fx.dot(Fx.fade(0xFFE08A, 0xFF3A00, 3.5F), pos);
		fx.burst(ParticleTypes.LAVA, pos, 1, 0.15, 0.0);
		// Trail: embers fading to dark red along the last segment, heavy smoke behind.
		fx.line(Fx.fade(0xFF8A1E, 0x5A0A00, 1.8F), meteor.last, pos, 2.5);
		fx.line(ParticleTypes.LARGE_SMOKE, meteor.last, pos, 0.6);
		// Warning ring on the ground, closing in as the meteor comes.
		ParticleOptions warning = Fx.dust(0xFF2A00, 1.1F);
		fx.ring(warning, meteor.impact.add(0, 0.12, 0), impactRadius * (1.0 - 0.6 * t) + 0.3, 14, age * 0.3);
	}

	private void impact(Fx fx, Vec3 p) {
		fx.dot(Fx.flash(0xFF8A1E), p.x, p.y + 1, p.z);
		fx.burst(ParticleTypes.EXPLOSION, p.x, p.y + 0.6, p.z, 2, 0.6, 0.3, 0.6, 0.0);
		fx.burst(ParticleTypes.FLAME, p.x, p.y + 0.4, p.z, 36, 0.5, 0.3, 0.5, 0.18);
		fx.burst(ParticleTypes.LAVA, p.x, p.y + 0.3, p.z, 8, 0.6, 0.2, 0.6, 0.0);
		BlockPos below = BlockPos.containing(p.x, p.y - 0.5, p.z);
		BlockState ground = level.isLoaded(below) ? level.getBlockState(below) : null;
		if (ground != null && !ground.isAir()) {
			fx.burst(new BlockParticleOption(ParticleTypes.BLOCK, ground), p.x, p.y + 0.2, p.z, 24, 0.8, 0.2, 0.8, 0.3);
		}
		fx.arc(ParticleTypes.FLAME, p.add(0, 0.2, 0), impactRadius * 0.5, 0, Math.PI * 2, 20, true);
		fx.arc(Fx.fade(0xFF6A00, 0x2A0A00, 1.6F), p.add(0, 0.25, 0), impactRadius, 0, Math.PI * 2, 24, true);
		curse(fx, p, impactRadius);
		IMPACTS.merge(owner, 1, Integer::sum);
		sound(p, SoundEvents.GENERIC_EXPLODE, 2.2F, 0.75F + random.nextFloat() * 0.35F);
		sound(p, SoundEvents.FIRECHARGE_USE, 1.5F, 0.5F);
		for (LivingEntity target : SpellTargets.hostiles(ctx, level, p, impactRadius + 0.6, 16)) {
			double distance = Math.sqrt(target.distanceToSqr(p));
			double falloff = Math.max(0.5, 1.0 - 0.5 * distance / Math.max(1, impactRadius));
			SpellTargets.hurt(ctx, target, SpellTargets.Element.FIRE, damage * falloff);
			if (fireSeconds > 0) {
				target.igniteForSeconds(fireSeconds);
			}
			Vec3 away = target.position().subtract(p);
			away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : new Vec3(away.x, 0, away.z).normalize();
			SpellTargets.push(target, new Vec3(away.x * 0.6 * falloff, 0.35 * falloff, away.z * 0.6 * falloff));
		}
		craters.add(new Crater(p));
	}
}
