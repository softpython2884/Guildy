package dev.guildy.scroll;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Rappel: after a long incantation, the caster and the guild members standing around them are drawn into a column of
 * light and brought back to the guild home (or the heart). Departure: rising teal spirals, portal motes, a flash per
 * traveller; arrival: rings of light falling around the travellers.
 */
final class RecallSpell extends SpellEffect {
	private static final int DEPARTURE_TICKS = 20;
	private static final int ARRIVAL_TICKS = 30;

	private final Vec3 where;
	private final List<Vec3> points;
	private final boolean arrival;

	private RecallSpell(SpellContext ctx, ServerLevel level, Vec3 where, List<Vec3> points, boolean arrival) {
		super(ctx, level);
		this.where = where;
		this.points = points;
		this.arrival = arrival;
	}

	/** Lang key of the reason the recall cannot happen, or null. */
	static @Nullable String check(SpellContext ctx) {
		Guild guild = ctx.guild;
		if (guild == null) {
			return "scroll.fail.no_guild";
		}
		ServerLevel home = GuildyServer.claimLevel();
		if (home == null || guild.home == null && guild.heart == null) {
			return "scroll.fail.no_home";
		}
		return null;
	}

	/** Teleports the caster and the members around them. @return lang key of a failure, or null */
	static @Nullable String cast(SpellContext ctx) {
		String problem = check(ctx);
		if (problem != null) {
			return problem;
		}
		Guild guild = ctx.guild;
		ServerLevel home = GuildyServer.claimLevel();
		Vec3 destination = destination(home, guild);
		if (destination == null) {
			return "scroll.fail.no_home";
		}
		List<ServerPlayer> travellers = new ArrayList<>();
		travellers.add(ctx.caster);
		int max = ctx.i("maxAllies");
		double radius = ctx.p("radius");
		if (radius > 0 && max > 0) {
			List<ServerPlayer> around = ctx.level.getEntitiesOfClass(ServerPlayer.class, ctx.caster.getBoundingBox().inflate(radius),
					p -> p.distanceToSqr(ctx.caster) <= radius * radius && TargetRules.mayRecall(SpellTargets.facts(p, ctx.caster, guild))
							&& !p.isSleeping());
			around.sort(java.util.Comparator.comparingDouble(p -> p.distanceToSqr(ctx.caster)));
			for (ServerPlayer companion : around) {
				if (travellers.size() - 1 >= max) {
					break;
				}
				travellers.add(companion);
			}
		}
		List<Vec3> departures = new ArrayList<>();
		for (ServerPlayer traveller : travellers) {
			departures.add(traveller.position());
		}
		EffectRunner.start(new RecallSpell(ctx, ctx.level, ctx.caster.position(), departures, false));
		List<Vec3> arrivals = new ArrayList<>();
		float yaw = guild.home != null ? guild.homeYaw : ctx.caster.getYRot();
		for (int i = 0; i < travellers.size(); i++) {
			ServerPlayer traveller = travellers.get(i);
			Vec3 spot = i == 0 ? destination : beside(home, destination, i);
			if (traveller.isPassenger()) {
				traveller.stopRiding();
			}
			traveller.teleportTo(home, spot.x, spot.y, spot.z, Set.of(), yaw, traveller.getXRot(), true);
			traveller.resetFallDistance();
			arrivals.add(spot);
			if (traveller != ctx.caster) {
				Msg.actionBar(traveller, "scroll.recall.pulled", ctx.caster.getName().getString());
			}
		}
		EffectRunner.start(new RecallSpell(ctx, home, destination, arrivals, true));
		return null;
	}

	/** Home position (on top of a partial block), else a free spot at the heart. */
	static @Nullable Vec3 destination(ServerLevel level, Guild guild) {
		if (guild.home != null) {
			BlockPos home = guild.home;
			VoxelShape shape = level.getBlockState(home).getCollisionShape(level, home);
			return new Vec3(home.getX() + 0.5, home.getY() + (shape.isEmpty() ? 0.0 : shape.max(Direction.Axis.Y)), home.getZ() + 0.5);
		}
		BlockPos heart = guild.heart;
		if (heart == null) {
			return null;
		}
		for (int dy = 0; dy <= 8; dy++) {
			BlockPos feet = heart.above(dy);
			if (free(level, feet) && free(level, feet.above()) && !free(level, feet.below())) {
				return new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
			}
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, heart.getX(), heart.getZ());
		return new Vec3(heart.getX() + 0.5, y, heart.getZ() + 0.5);
	}

	/** A free spot next to the destination for the i-th companion (the destination itself when none is free). */
	private static Vec3 beside(ServerLevel level, Vec3 destination, int i) {
		int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
		for (int k = 0; k < offsets.length; k++) {
			int[] o = offsets[(i - 1 + k) % offsets.length];
			BlockPos feet = BlockPos.containing(destination.x + o[0], destination.y, destination.z + o[1]);
			if (free(level, feet) && free(level, feet.above()) && !free(level, feet.below())) {
				return new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
			}
		}
		return destination;
	}

	private static boolean free(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	@Override
	Vec3 anchor() {
		return where;
	}

	/** The departure animation stays where it started (its owner is gone to another place). */
	@Override
	boolean bound() {
		return arrival;
	}

	@Override
	void tick(Fx fx) {
		Spell spell = ctx.spell;
		if (age == 0) {
			sound(where, arrival ? SoundEvents.PLAYER_TELEPORT : SoundEvents.ENDERMAN_TELEPORT, 2.0F, arrival ? 1.2F : 0.6F);
			sound(where, SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, arrival ? 1.4F : 0.7F);
		}
		int total = arrival ? ARRIVAL_TICKS : DEPARTURE_TICKS;
		double t = (double) age / total;
		for (Vec3 p : points) {
			if (age == 0) {
				fx.dot(Fx.flash(spell.color), p.x, p.y + 1, p.z);
				fx.burst(ParticleTypes.TOTEM_OF_UNDYING, p.x, p.y + 1, p.z, 20, 0.3, 0.6, 0.3, 0.25);
			}
			if (arrival) {
				// Rings of light falling around the traveller and closing in.
				double h = 3.0 * (1 - t);
				fx.ring(Fx.fade(spell.accent, spell.color, 1.1F), p.add(0, h, 0), 1.2 * (1 - t) + 0.4, 16, age * 0.5);
				fx.burst(ParticleTypes.END_ROD, p.x, p.y + 0.2, p.z, 2, 0.4, 0.1, 0.4, 0.02);
			} else {
				// Column of light with two rising spirals.
				for (int s = 0; s < 2; s++) {
					for (int k = 0; k < 6; k++) {
						double angle = age * 0.6 + s * Math.PI + k * 0.5;
						double y = (age * 0.15 + k * 0.4) % 3.0;
						fx.move(Fx.fade(spell.color, spell.accent, 1.0F), p.x + Math.cos(angle) * 0.8, p.y + y, p.z + Math.sin(angle) * 0.8, 0, 0.05, 0);
					}
				}
				fx.move(ParticleTypes.END_ROD, p.x, p.y + 0.1, p.z, 0, 0.35, 0);
				fx.move(ParticleTypes.REVERSE_PORTAL, p.x, p.y + 1, p.z, 0, 0.2, 0);
			}
		}
		if (age >= total) {
			finish();
		}
	}
}
