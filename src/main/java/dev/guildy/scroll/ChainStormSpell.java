package dev.guildy.scroll;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Tempête de foudre: a dark storm cloud swirls over the caster and lightning leaps from their hand from monster to monster
 * (jagged arcs, sparks, colored flashes, a few visual-only bolts: no fire, no block change), stunning every one it hits.
 */
final class ChainStormSpell extends SpellEffect {
	private static final int STEP = 3;
	private static final int MAX_BOLTS = 4;
	private static final int LINGER = 24;

	private final Vec3 cloud;
	private final int count;
	private final double damage;
	private final double chainRadius;
	private final int stunTicks;
	private final Set<UUID> struck = new HashSet<>();
	private @Nullable LivingEntity next;
	private Vec3 previous;
	private int jumps;
	private int bolts;
	private int lastJump;

	ChainStormSpell(SpellContext ctx, LivingEntity first) {
		super(ctx);
		this.cloud = ctx.caster.position().add(0, 6, 0);
		this.count = ctx.i("count");
		this.damage = ctx.p("damage");
		this.chainRadius = ctx.p("chainRadius");
		this.stunTicks = ctx.ticks("stunSeconds");
		this.next = first;
		this.previous = hand(ctx.caster);
	}

	/** First target: the aimed monster, else the nearest one in range (null when none). */
	static @Nullable LivingEntity firstTarget(SpellContext ctx) {
		double range = ctx.p("range");
		LivingEntity aimed = SpellTargets.aimedHostile(ctx, range);
		if (aimed != null) {
			return aimed;
		}
		for (LivingEntity candidate : SpellTargets.hostiles(ctx, ctx.level, ctx.caster.position(), range, 8)) {
			if (ctx.caster.hasLineOfSight(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private static Vec3 hand(ServerPlayer player) {
		return player.getEyePosition().add(player.getLookAngle().scale(0.6)).add(0, -0.35, 0);
	}

	@Override
	Vec3 anchor() {
		return previous;
	}

	@Override
	void tick(Fx fx) {
		drawCloud(fx);
		if (age == 0) {
			sound(cloud, SoundEvents.TRIDENT_THUNDER, 2.5F, 0.8F);
		}
		if (next != null && age % STEP == 0) {
			strike(fx, next);
			next = jumps < count ? findNext(next.position()) : null;
			lastJump = age;
		}
		if (next == null && age - lastJump >= LINGER) {
			finish();
		}
	}

	private void drawCloud(Fx fx) {
		double phase = age * 0.18;
		for (int k = 0; k < 18; k++) {
			double angle = phase + k * Math.PI * 2 / 18;
			double r = 1.2 + (k % 3) * 0.9;
			fx.dot(Fx.dust(k % 2 == 0 ? 0x30354A : 0x4A5068, 2.6F), cloud.x + Math.cos(angle) * r, cloud.y + Math.sin(angle * 3) * 0.15,
					cloud.z + Math.sin(angle) * r);
		}
		if (age % 2 == 0) {
			fx.burst(ParticleTypes.CLOUD, cloud.x, cloud.y, cloud.z, 4, 1.6, 0.2, 1.6, 0.01);
		}
		if (age % 4 == 0) {
			fx.burst(ParticleTypes.ELECTRIC_SPARK, cloud.x, cloud.y - 0.2, cloud.z, 8, 1.8, 0.3, 1.8, 0.2);
		}
		if (age % 7 == 0) {
			// Rain of sparks under the cloud.
			fx.burst(ParticleTypes.FALLING_WATER, cloud.x, cloud.y - 0.4, cloud.z, 10, 2.0, 0.1, 2.0, 0.0);
		}
	}

	private void strike(Fx fx, LivingEntity target) {
		Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);
		fx.bolt(Fx.dust(0xE8FBFF, 0.9F), previous, to, 7, 0.55, random);
		fx.bolt(ParticleTypes.ELECTRIC_SPARK, previous, to, 5, 0.35, random);
		fx.burst(ParticleTypes.ELECTRIC_SPARK, to, 24, 0.4, 0.35);
		fx.dot(Fx.flash(ctx.spell.color), to);
		fx.ring(Fx.dust(ctx.spell.color, 1.2F), target.position().add(0, 0.1, 0), 1.2, 12, age * 0.4);
		if (bolts < MAX_BOLTS) {
			bolts++;
			visualBolt(level, target.position());
		} else {
			sound(to, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.6F, 1.2F + random.nextFloat() * 0.3F);
		}
		struck.add(target.getUUID());
		jumps++;
		SpellTargets.hurt(ctx, target, SpellTargets.Element.LIGHTNING, damage);
		if (stunTicks > 0 && target.isAlive()) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, stunTicks, 5, false, true));
			target.addEffect(new MobEffectInstance(MobEffects.GLOWING, stunTicks, 0, false, false));
		}
		previous = to;
	}

	private @Nullable LivingEntity findNext(Vec3 from) {
		List<LivingEntity> around = SpellTargets.hostiles(ctx, level, from, chainRadius, 24);
		for (LivingEntity candidate : around) {
			if (!struck.contains(candidate.getUUID())) {
				return candidate;
			}
		}
		return null;
	}

	/** A visual-only lightning bolt (never above copper or a lightning rod: the strike would change them), else sparks. */
	static void visualBolt(ServerLevel level, Vec3 pos) {
		BlockPos strike = BlockPos.containing(pos.x, pos.y - 1.0E-6, pos.z);
		if (level.isLoaded(strike) && safeStrike(level.getBlockState(strike)) && safeStrike(level.getBlockState(strike.below()))) {
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
			if (bolt != null) {
				bolt.setVisualOnly(true);
				bolt.snapTo(pos.x, pos.y, pos.z);
				if (level.addFreshEntity(bolt)) {
					return;
				}
			}
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, pos.x, pos.y + 1, pos.z, 30, 0.2, 1.2, 0.2, 0.3);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_IMPACT, net.minecraft.sounds.SoundSource.WEATHER, 1.5F, 1.0F);
	}

	static boolean safeStrike(BlockState state) {
		Block block = state.getBlock();
		return !(block instanceof WeatheringCopper) && !(block instanceof LightningRodBlock) && HoneycombItem.WAX_OFF_BY_BLOCK.get().get(block) == null;
	}
}
