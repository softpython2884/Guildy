package dev.guildy.scroll;

import net.minecraft.core.particles.ParticleOptions;
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
 * Bannière de guerre: a war banner rises where the caster stands (a dark pole with a golden finial, a red flag with a golden
 * emblem flapping in the wind) to the call of a war horn. While it stands, war drums beat (golden shock rings sweep the
 * ground to the edge of the area), embers rise from its red border, and the allies inside (the caster, guild members, guild
 * villagers) fight with Strength and Haste (faster attacks), refreshed every half second; a burst of sparks marks each ally
 * the first time the banner rallies them. When it ends, the flag burns away.
 */
final class WarBannerSpell extends SpellEffect {
	private static final int RISE = 12;
	private static final int REFRESH = 10;
	private static final int BEAT = 20;
	private static final double POLE = 4.2;
	private static final double FLAG_LENGTH = 2.1;
	private static final double FLAG_HEIGHT = 1.4;
	private static final int FLAG_COLUMNS = 8;
	private static final int FLAG_ROWS = 5;
	private static final int WOOD = 0x4A2C14;
	private static final int GOLD = 0xFFD86A;

	private final Vec3 base;
	private final double radius;
	private final int duration;
	private final int strength;
	private final int haste;
	/** Direction the flag flies (to the caster's side, so they see it broadside) and the direction it flaps toward. */
	private final Vec3 along;
	private final Vec3 across;
	private final Set<UUID> rallied = new HashSet<>();

	WarBannerSpell(SpellContext ctx) {
		super(ctx);
		this.base = ctx.caster.position();
		this.radius = ctx.p("radius");
		this.duration = ctx.ticks("duration");
		this.strength = ctx.i("strengthLevel");
		this.haste = ctx.i("hasteLevel");
		Vec3 look = ctx.caster.getLookAngle();
		Vec3 forward = new Vec3(look.x, 0, look.z);
		forward = forward.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : forward.normalize();
		this.across = forward;
		this.along = new Vec3(-forward.z, 0, forward.x);
	}

	@Override
	Vec3 anchor() {
		return base;
	}

	@Override
	void tick(Fx fx) {
		Spell spell = ctx.spell;
		if (age == 0) {
			sound(base, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(5), 4.0F, 0.9F);
			sound(base, SoundEvents.ANVIL_LAND, 1.2F, 0.6F);
			sound(base, SoundEvents.ARMOR_EQUIP_IRON, 2.0F, 0.8F);
			fx.dot(Fx.flash(spell.color), base.x, base.y + 1, base.z);
			fx.burst(ParticleTypes.CAMPFIRE_COSY_SMOKE, base.x, base.y + 0.1, base.z, 6, 0.4, 0.05, 0.4, 0.01);
			fx.arc(Fx.dust(spell.accent, 1.3F), base.add(0, 0.1, 0), 0.8, 0, Math.PI * 2, 24, true);
		}
		double rise = easeOut(Math.min(1, (age + 1) / (double) RISE));
		drawBanner(fx, spell, rise);
		drawGround(fx, spell);
		if (age % REFRESH == 0) {
			rally(fx);
		}
		if (age >= duration) {
			burnAway(fx);
			finish();
		}
	}

	private void drawBanner(Fx fx, Spell spell, double rise) {
		double top = POLE * rise;
		if (age % 2 == 0 || age < RISE) {
			fx.line(Fx.dust(WOOD, 1.0F), base.add(0, 0.05, 0), base.add(0, top, 0), 4);
		}
		Vec3 tip = base.add(0, top + 0.2, 0);
		fx.dot(Fx.dust(GOLD, 1.4F), tip);
		if (age % 10 == 0) {
			fx.dot(Fx.flash(spell.accent), tip);
		}
		// Crossbar under the finial, then the flag hanging from it and flapping (stronger toward its free end).
		Vec3 bar = base.add(0, top - 0.05, 0);
		if (age % 2 == 1 || age < RISE) {
			fx.line(Fx.dust(GOLD, 0.8F), bar, bar.add(along.scale(FLAG_LENGTH * rise)), 4);
		}
		if (age % 2 == 0) {
			for (int c = 1; c <= FLAG_COLUMNS; c++) {
				double u = c / (double) FLAG_COLUMNS;
				double wave = Math.sin(age * 0.35 - c * 0.9) * 0.22 * u;
				for (int r = 0; r < FLAG_ROWS; r++) {
					if (c == FLAG_COLUMNS && r == FLAG_ROWS / 2) {
						// Swallowtail notch.
						continue;
					}
					double v = (r + 0.5) / FLAG_ROWS;
					Vec3 point = bar.add(along.scale(FLAG_LENGTH * u * rise)).add(across.scale(wave)).add(0, -FLAG_HEIGHT * v * rise, 0);
					boolean emblem = Math.abs(c - FLAG_COLUMNS / 2) + Math.abs(r - FLAG_ROWS / 2) <= 1;
					boolean trim = r == FLAG_ROWS - 1;
					int color = emblem ? GOLD : trim ? Fx.mix(spell.color, 0x000000, 0.35) : spell.color;
					fx.dot(Fx.dust(color, emblem ? 1.2F : 1.1F), point);
				}
			}
		}
	}

	private void drawGround(Fx fx, Spell spell) {
		double phase = age * 0.05;
		if (age % 4 == 0) {
			fx.ring(Fx.dust(spell.color, 1.2F), base.add(0, 0.15, 0), radius, (int) Math.max(16, radius * 6), phase);
		}
		if (age % 8 == 4) {
			fx.polygram(Fx.dust(spell.accent, 0.8F), base.add(0, 0.12, 0), Math.min(2.5, radius * 0.4), 8, 3, -phase, 1.0, 3);
		}
		// War drums: two beats, then a golden shock ring sweeping the area.
		int beat = age % BEAT;
		if (beat == 0 || beat == 5) {
			sound(base, SoundEvents.NOTE_BLOCK_BASEDRUM, 2.5F, beat == 0 ? 0.6F : 0.5F);
		}
		if (beat < 12) {
			double r = 0.6 + (radius - 0.6) * easeOut(beat / 12.0);
			fx.ring(Fx.fade(spell.accent, spell.color, 1.3F), base.add(0, 0.25, 0), r, (int) Math.max(12, r * 5), phase * 2);
		}
		// Embers rising from the border of the area.
		for (int i = 0; i < 3; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			ParticleOptions ember = i == 0 ? ParticleTypes.SMALL_FLAME : Fx.dust(i == 1 ? spell.accent : spell.color, 0.9F);
			fx.move(ember, base.x + Math.cos(angle) * radius, base.y + 0.2, base.z + Math.sin(angle) * radius, 0, 0.06 + random.nextDouble() * 0.04, 0);
		}
	}

	/** Strength and Haste to the allies inside (refreshed while they stay); sparks the first time. */
	private void rally(Fx fx) {
		Spell spell = ctx.spell;
		int ticks = REFRESH * 3;
		for (LivingEntity ally : SpellTargets.allies(ctx, level, base, radius)) {
			ally.addEffect(new MobEffectInstance(MobEffects.STRENGTH, ticks, strength - 1, false, true));
			ally.addEffect(new MobEffectInstance(MobEffects.HASTE, ticks, haste - 1, false, true));
			if (rallied.add(ally.getUUID())) {
				fx.burst(ParticleTypes.CRIT, ally.getX(), ally.getY(0.6), ally.getZ(), 18, 0.35, 0.5, 0.35, 0.3);
				fx.burst(ParticleTypes.ANGRY_VILLAGER, ally.getX(), ally.getY() + ally.getBbHeight() + 0.3, ally.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
				fx.dot(Fx.flash(spell.accent), ally.getX(), ally.getY(0.6), ally.getZ());
				sound(ally.position(), SoundEvents.ARMOR_EQUIP_GOLD, 1.0F, 1.2F);
			} else {
				fx.ring(Fx.dust(spell.color, 0.7F), ally.position().add(0, 0.1, 0), 0.6, 8, age * 0.3);
				fx.burst(Fx.dust(spell.accent, 0.7F), ally.getX(), ally.getY(0.7), ally.getZ(), 2, 0.3, 0.3, 0.3, 0.0);
			}
		}
	}

	private void burnAway(Fx fx) {
		Vec3 bar = base.add(0, POLE - 0.05, 0);
		for (int c = 1; c <= FLAG_COLUMNS; c += 2) {
			Vec3 point = bar.add(along.scale(FLAG_LENGTH * c / FLAG_COLUMNS)).add(0, -FLAG_HEIGHT / 2, 0);
			fx.burst(ParticleTypes.FLAME, point, 4, 0.2, 0.03);
			fx.burst(ParticleTypes.LARGE_SMOKE, point, 2, 0.2, 0.02);
		}
		fx.sphereBurst(Fx.dust(ctx.spell.color, 1.2F), base.add(0, 1, 0), radius * 0.3, 30, 0.12);
		sound(base, SoundEvents.FIRECHARGE_USE, 1.5F, 0.7F);
		sound(base, SoundEvents.GENERIC_EXTINGUISH_FIRE, 1.5F, 0.8F);
	}
}
