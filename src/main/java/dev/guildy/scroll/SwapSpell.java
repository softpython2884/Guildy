package dev.guildy.scroll;

import dev.guildy.core.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Permutation: the caster and the creature under their crosshair (a monster, or an ally of the guild to rescue; in sight,
 * within range) trade places in a flash. Both spots open a shrinking violet / teal portal, two streams of light cross between
 * them along a twisting double helix. A swapped monster is stunned and loses its target; a swapped ally is shielded
 * (Resistance II). Never into a block, lava, fire or a harmful block, never above the void ({@link SwapRules}): a floating
 * target sends the caster to the ground right under it, or the swap is refused and the scroll kept. The ruin guardians may
 * be swapped: their arena leash brings them back as usual.
 */
final class SwapSpell extends SpellEffect {
	private static final int TICKS = 26;
	/** Scan depth under a floating target (one more than the allowed drop, to tell "too high" from "nothing"). */
	private static final int SCAN = SwapRules.MAX_DROP + 1;

	private final Vec3 from;
	private final Vec3 to;
	private final double fromHeight;
	private final double toHeight;

	private SwapSpell(SpellContext ctx, Vec3 from, Vec3 to, double fromHeight, double toHeight) {
		super(ctx);
		this.from = from;
		this.to = to;
		this.fromHeight = fromHeight;
		this.toHeight = toHeight;
	}

	/** Where each one lands, and whether it is safe. */
	record Plan(Vec3 casterTo, Vec3 targetTo, SwapRules.Verdict verdict) {
	}

	/** The creature the caster would swap with (under the crosshair, in sight, within range), or null. */
	static @Nullable LivingEntity target(SpellContext ctx) {
		return SpellTargets.aimedSwappable(ctx, ctx.p("range"));
	}

	/** Lang key of the reason the swap cannot happen now (scroll kept), or null. */
	static @Nullable String check(SpellContext ctx) {
		LivingEntity target = target(ctx);
		if (target == null) {
			return "scroll.fail.no_swap_target";
		}
		return SwapRules.failureKey(plan(ctx.level, ctx.caster, target).verdict());
	}

	/** Swaps the caster and the aimed creature. @return lang key of a failure (nothing happened), or null */
	static @Nullable String cast(SpellContext ctx) {
		LivingEntity target = target(ctx);
		if (target == null) {
			return "scroll.fail.no_swap_target";
		}
		ServerPlayer caster = ctx.caster;
		ServerLevel level = ctx.level;
		Plan plan = plan(level, caster, target);
		String failure = SwapRules.failureKey(plan.verdict());
		if (failure != null) {
			return failure;
		}
		Vec3 casterFrom = caster.position();
		Vec3 targetFrom = target.position();
		if (caster.isPassenger()) {
			caster.stopRiding();
		}
		if (target.isPassenger()) {
			target.stopRiding();
		}
		// The caster arrives facing the spot they left (where the swapped creature now stands).
		Vec3 back = casterFrom.subtract(plan.casterTo());
		float yaw = back.horizontalDistanceSqr() < 1.0E-4 ? caster.getYRot() : (float) (Math.toDegrees(Math.atan2(-back.x, back.z)));
		caster.teleportTo(level, plan.casterTo().x, plan.casterTo().y, plan.casterTo().z, Set.of(), yaw, caster.getXRot(), true);
		caster.resetFallDistance();
		Vec3 t = plan.targetTo();
		if (target instanceof ServerPlayer ally) {
			ally.teleportTo(level, t.x, t.y, t.z, Set.of(), ally.getYRot(), ally.getXRot(), true);
			Msg.actionBar(ally, "scroll.swap.swapped", caster.getName().getString());
		} else {
			target.teleportTo(t.x, t.y, t.z);
		}
		target.resetFallDistance();
		target.setDeltaMovement(Vec3.ZERO);
		if (SpellTargets.harmable(target, caster, ctx.guild)) {
			int stun = ctx.ticks("stunSeconds");
			if (target instanceof Mob mob) {
				mob.getNavigation().stop();
				mob.setTarget(null);
			}
			if (stun > 0) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, stun, 5, false, true));
				target.addEffect(new MobEffectInstance(MobEffects.GLOWING, stun, 0, false, false));
			}
		} else {
			if (target instanceof Mob mob) {
				mob.getNavigation().stop();
			}
			int shield = ctx.ticks("shieldSeconds");
			if (shield > 0) {
				target.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, shield, 1, false, true));
			}
		}
		EffectRunner.start(new SwapSpell(ctx, casterFrom, plan.casterTo(), caster.getBbHeight(), target.getBbHeight()));
		return null;
	}

	// ================================================================== safety

	/** The landing spots of both and the verdict of {@link SwapRules}. */
	static Plan plan(ServerLevel level, ServerPlayer caster, LivingEntity target) {
		Vec3 casterFrom = caster.position();
		Landing landing = landing(level, caster, target.position());
		AABB targetBox = target.getDimensions(target.getPose()).makeBoundingBox(casterFrom).deflate(1.0E-3);
		SwapRules.Spot forTarget = new SwapRules.Spot(level.isLoaded(BlockPos.containing(casterFrom)), level.noCollision(target, targetBox), false, false,
				0);
		return new Plan(landing.feet(), casterFrom, SwapRules.check(landing.spot(), forTarget));
	}

	/** Where a player sent to {@code feet} would stand (the ground under it when floating) and what that spot looks like. */
	record Landing(Vec3 feet, SwapRules.Spot spot) {
	}

	/**
	 * A player standing (upright) at {@code feet}: the ground under it when at most {@link SwapRules#MAX_DROP} blocks below,
	 * room, lava and hazards there ({@link SwapRules#forCaster} decides). Shared with the rally's arrivals.
	 */
	static Landing landing(ServerLevel level, ServerPlayer mover, Vec3 feet) {
		double drop = dropBelow(level, feet, SCAN);
		int blocks = drop < 0 ? -1 : (int) Math.ceil(drop - 1.0E-3);
		Vec3 spot = blocks >= 0 && blocks <= SwapRules.MAX_DROP ? new Vec3(feet.x, feet.y - drop, feet.z) : feet;
		AABB box = mover.getDimensions(Pose.STANDING).makeBoundingBox(spot).deflate(1.0E-3);
		return new Landing(spot, new SwapRules.Spot(level.isLoaded(BlockPos.containing(spot)), level.noCollision(mover, box), lava(level, box),
				hazard(level, box, spot), blocks));
	}

	/**
	 * Blocks of fall from {@code feet} down to a solid surface or water (0 when standing on it), or -1 when there is none
	 * within {@code depth} blocks.
	 */
	static double dropBelow(ServerLevel level, Vec3 feet, int depth) {
		int x = (int) Math.floor(feet.x);
		int z = (int) Math.floor(feet.z);
		int top = (int) Math.floor(feet.y);
		for (int y = top; y >= top - depth - 1 && y >= level.getMinY(); y--) {
			BlockPos pos = new BlockPos(x, y, z);
			if (!level.isLoaded(pos)) {
				return -1;
			}
			if (level.getFluidState(pos).is(FluidTags.WATER)) {
				// Water breaks the fall: the surface (or the feet when already in it).
				return Math.max(0, feet.y - (y + 1));
			}
			VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
			if (shape.isEmpty()) {
				continue;
			}
			double surface = y + shape.max(Direction.Axis.Y);
			if (surface <= feet.y + 1.0E-3) {
				return feet.y - surface > depth ? -1 : Math.max(0, feet.y - surface);
			}
			if (y == top) {
				// Feet sunk in a partial block (soul sand, a path): standing on it.
				return 0;
			}
		}
		return -1;
	}

	private static boolean lava(ServerLevel level, AABB box) {
		for (BlockPos pos : BlockPos.betweenClosed(box)) {
			if (level.getFluidState(pos).is(FluidTags.LAVA)) {
				return true;
			}
		}
		return false;
	}

	/** Fire, magma, a lit campfire, cactus, berry bush, wither rose, powder snow or dripstone in or right under the hitbox. */
	private static boolean hazard(ServerLevel level, AABB box, Vec3 feet) {
		for (BlockPos pos : BlockPos.betweenClosed(box)) {
			if (harmful(level.getBlockState(pos))) {
				return true;
			}
		}
		BlockPos below = BlockPos.containing(feet.x, feet.y - 0.05, feet.z);
		BlockState under = level.getBlockState(below);
		return harmful(under) || under.is(Blocks.POINTED_DRIPSTONE);
	}

	private static boolean harmful(BlockState state) {
		return NodeEvaluator.isBurningBlock(state) || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
				|| state.is(Blocks.POWDER_SNOW) || state.is(Blocks.POINTED_DRIPSTONE) || state.getFluidState().is(FluidTags.LAVA);
	}

	// ================================================================== animation

	@Override
	Vec3 anchor() {
		return from.add(to).scale(0.5);
	}

	@Override
	void tick(Fx fx) {
		Spell spell = ctx.spell;
		Vec3 a = from.add(0, fromHeight * 0.55, 0);
		Vec3 b = to.add(0, toHeight * 0.55, 0);
		if (age == 0) {
			for (Vec3 p : new Vec3[] {from, to}) {
				sound(p, SoundEvents.ENDERMAN_TELEPORT, 1.8F, p == from ? 0.8F : 1.2F);
				fx.burst(ParticleTypes.REVERSE_PORTAL, p.x, p.y + 1, p.z, 30, 0.3, 0.6, 0.3, 0.15);
				curse(fx, p, 1.2);
			}
			sound(anchor(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 2.0F, 1.0F);
			sound(anchor(), SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 1.4F);
			fx.dot(Fx.flash(spell.color), a);
			fx.dot(Fx.flash(spell.accent), b);
			// Two streams of light crossing: each spot sends its color to the other.
			for (int i = 0; i < 24; i++) {
				Vec3 jitter = new Vec3((random.nextDouble() - 0.5) * 0.8, (random.nextDouble() - 0.5) * 1.2, (random.nextDouble() - 0.5) * 0.8);
				fx.dot(Fx.trail(b.add(jitter), spell.color, 8 + random.nextInt(8)), a.add(jitter));
				fx.dot(Fx.trail(a.add(jitter), spell.accent, 8 + random.nextInt(8)), b.add(jitter));
			}
		}
		double t = age / (double) TICKS;
		// Shrinking upright portals at both spots, facing each other.
		portal(fx, from, fromHeight, Fx.dust(spell.accent, 1.1F), 1 - t);
		portal(fx, to, toHeight, Fx.dust(spell.color, 1.1F), 1 - t);
		if (age < 14) {
			helix(fx, a, b, spell);
		}
		if (age % 3 == 0) {
			fx.burst(ParticleTypes.REVERSE_PORTAL, to.x, to.y + 0.8, to.z, 3, 0.3, 0.5, 0.3, 0.05);
			fx.burst(ParticleTypes.PORTAL, from.x, from.y + 0.8, from.z, 3, 0.3, 0.5, 0.3, 0.3);
		}
		if (age >= TICKS) {
			fx.arc(Fx.dust(spell.color, 1.0F), to.add(0, 0.1, 0), 1.2, 0, Math.PI * 2, 20, true);
			fx.arc(Fx.dust(spell.accent, 1.0F), from.add(0, 0.1, 0), 1.2, 0, Math.PI * 2, 20, true);
			finish();
		}
	}

	/** An upright ring around a spot, perpendicular to the line between the two spots, scaled by {@code size}. */
	private void portal(Fx fx, Vec3 spot, double height, ParticleOptions particle, double size) {
		Vec3 line = to.subtract(from);
		Vec3 side = new Vec3(-line.z, 0, line.x);
		side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 center = spot.add(0, height * 0.55, 0);
		double rx = (0.3 + 0.8 * size);
		double ry = (0.3 + height * 0.6 * size);
		int points = 18;
		double phase = age * 0.3;
		for (int i = 0; i < points; i++) {
			double angle = phase + Math.PI * 2 * i / points;
			Vec3 p = center.add(side.scale(Math.cos(angle) * rx)).add(0, Math.sin(angle) * ry, 0);
			if (!fx.dot(particle, p)) {
				return;
			}
		}
	}

	/** A double helix twisting along the line between the spots, drawn from both ends toward the middle. */
	private void helix(Fx fx, Vec3 a, Vec3 b, Spell spell) {
		Vec3 line = b.subtract(a);
		double length = line.length();
		if (length < 0.5) {
			return;
		}
		Vec3 dir = line.scale(1 / length);
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 up = side.cross(dir).normalize();
		int steps = (int) Math.min(60, length * 3);
		double drawn = Math.min(1, (age + 1) / 8.0);
		for (int i = 0; i <= steps; i++) {
			double u = (double) i / steps;
			if (u > drawn * 0.5 && u < 1 - drawn * 0.5) {
				// Not drawn yet: both strands grow from the ends and meet in the middle.
				continue;
			}
			double angle = u * Math.PI * 6 + age * 0.5;
			Vec3 center = a.add(line.scale(u));
			Vec3 offset = side.scale(Math.cos(angle) * 0.35).add(up.scale(Math.sin(angle) * 0.35));
			if (!fx.dot(Fx.dust(spell.color, 0.8F), center.add(offset)) || !fx.dot(Fx.dust(spell.accent, 0.8F), center.subtract(offset))) {
				return;
			}
		}
	}
}
