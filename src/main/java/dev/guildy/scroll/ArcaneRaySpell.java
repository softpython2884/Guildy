package dev.guildy.scroll;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Rayon arcanique: a continuous beam of arcane light pours from the caster's hand for a few seconds and follows their aim:
 * a white core wrapped in a double helix fading from magenta to cyan, glyphs gathering at the hand, sparks and flashes where
 * it hits a wall. Every monster crossing it is hurt several times per second and pushed along.
 */
final class ArcaneRaySpell extends SpellEffect {
	private static final int HIT_INTERVAL = 4;

	private final double length;
	private final int duration;
	private final double damage;
	private final double width;
	private Vec3 from;
	private Vec3 to;

	ArcaneRaySpell(SpellContext ctx) {
		super(ctx);
		this.length = ctx.p("length");
		this.duration = ctx.ticks("duration");
		this.damage = ctx.p("damage");
		this.width = ctx.p("width");
		this.from = ctx.caster.getEyePosition();
		this.to = from;
	}

	@Override
	Vec3 anchor() {
		return from;
	}

	@Override
	void tick(Fx fx) {
		ServerPlayer caster = caster();
		if (caster == null || !caster.isAlive() || caster.level() != level || age >= duration) {
			if (age > 0) {
				fx.burst(ParticleTypes.ENCHANT, from, 30, 0.4, 0.6);
			}
			finish();
			return;
		}
		Spell spell = ctx.spell;
		Vec3 look = caster.getViewVector(1.0F);
		from = caster.getEyePosition().add(look.scale(0.7)).add(0, -0.25, 0);
		Vec3 end = from.add(look.scale(length));
		HitResult hit = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		to = hit.getLocation();
		if (age == 0) {
			sound(from, SoundEvents.GUARDIAN_ATTACK, 2.0F, 1.6F);
			sound(from, SoundEvents.BEACON_ACTIVATE, 2.0F, 2.0F);
		}
		if (age % 10 == 5) {
			sound(from, SoundEvents.BEACON_AMBIENT, 1.5F, 2.0F);
		}
		drawBeam(fx, spell, look);
		if (hit.getType() != HitResult.Type.MISS) {
			fx.burst(ParticleTypes.ELECTRIC_SPARK, to, 6, 0.15, 0.25);
			if (age % HIT_INTERVAL == 0) {
				fx.dot(Fx.flash(spell.color), to);
				fx.burst(ParticleTypes.CRIT, to, 8, 0.2, 0.4);
			}
		}
		if (age % HIT_INTERVAL == 0) {
			sear(fx, look);
		}
	}

	private void drawBeam(Fx fx, Spell spell, Vec3 look) {
		Vec3 axis = to.subtract(from);
		double len = axis.length();
		if (len < 0.1) {
			return;
		}
		Vec3 dir = axis.scale(1 / len);
		// Two vectors perpendicular to the beam for the helix.
		Vec3 up = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 u = dir.cross(up).normalize();
		Vec3 v = dir.cross(u).normalize();
		fx.line(ParticleTypes.END_ROD, from, to, 1.2);
		int steps = (int) Math.min(120, len * 3);
		double phase = age * 0.55;
		for (int i = 0; i <= steps; i++) {
			double s = (double) i / steps;
			Vec3 p = from.add(axis.scale(s));
			double a = phase + s * len * 1.4;
			double r = width * 0.4;
			int color = Fx.mix(spell.color, spell.accent, s);
			fx.dot(Fx.dust(color, 0.9F), p.add(u.scale(Math.cos(a) * r)).add(v.scale(Math.sin(a) * r)));
			fx.dot(Fx.dust(Fx.mix(spell.accent, spell.color, s), 0.9F), p.add(u.scale(-Math.cos(a) * r)).add(v.scale(-Math.sin(a) * r)));
		}
		// Glyphs gathering at the hand.
		for (int i = 0; i < 3; i++) {
			Vec3 offset = new Vec3((random.nextDouble() - 0.5) * 2, random.nextDouble() * 1.2, (random.nextDouble() - 0.5) * 2);
			fx.move(ParticleTypes.ENCHANT, from, offset);
		}
	}

	private void sear(Fx fx, Vec3 look) {
		AABB area = new AABB(from, to).inflate(width + 1);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, e -> SpellTargets.harmable(e, ctx.caster, ctx.guild))) {
			if (target.getBoundingBox().inflate(width * 0.5).clip(from, to).isEmpty() && !target.getBoundingBox().inflate(width * 0.5).contains(from)) {
				continue;
			}
			SpellTargets.hurt(ctx, target, SpellTargets.Element.ARCANE, damage);
			SpellTargets.push(target, new Vec3(look.x * 0.15, 0.05, look.z * 0.15));
			fx.burst(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY(0.5), target.getZ(), 8, 0.3, 0.4, 0.3, 0.15);
			fx.burst(Fx.fade(ctx.spell.color, ctx.spell.accent, 1.2F), target.getX(), target.getY(0.5), target.getZ(), 6, 0.3, 0.4, 0.3, 0.0);
		}
	}
}
