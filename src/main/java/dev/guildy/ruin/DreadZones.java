package dev.guildy.ruin;

import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.ruin.DreadAttacks.Context;
import dev.guildy.ruin.DreadAttacks.Running;
import dev.guildy.ruin.RuinConfig.DreadAbilityDef;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dread attacks on the ground: graves that root (grasping_graves), a bone prison closing on a player (bone_prison), wells
 * that pull toward their center (gravity_well, quicksand), a curse that explodes on the players around its victim
 * (mummy_curse), a storm that spares only its eye (sandstorm, blizzard_dome), lingering or erupting zones (frozen_ground,
 * lava_geysers, soul_pillars), chains that punish the players straying from the guardian (soul_chains) and an aura that
 * punishes those staying close (wither_aura).
 */
final class DreadZones {
	private DreadZones() {
	}

	// ================================================================== grasping graves

	/** Graves burst under players (cracks and a red circle), then bony hands grasp whoever still stands there: rooted. */
	static final class Graves extends Running {
		private final List<Vec3> centers = new ArrayList<>();
		private final List<Double> spins = new ArrayList<>();
		private final Set<UUID> grasped = new HashSet<>();

		private Graves(DreadAbilityDef def, String style, int telegraph) {
			super(def, style, telegraph);
		}

		static @Nullable Graves cast(DreadAbilityDef def, Context ctx, int telegraph, String style) {
			List<ServerPlayer> victims = DreadAttacks.pick(ctx, Math.max(1, def.count));
			if (victims.isEmpty()) {
				return null;
			}
			Graves graves = new Graves(def, style, telegraph);
			for (ServerPlayer player : victims) {
				graves.centers.add(DreadAttacks.floorUnder(ctx.level, player));
				graves.spins.add(ctx.random.nextDouble() * Math.PI);
			}
			// A few more graves around the arena when there are fewer players than graves.
			for (int i = victims.size(); i < def.count; i++) {
				graves.centers.add(DreadAttacks.arenaSpot(ctx, 2.5, DreadAttacks.reach(ctx)));
				graves.spins.add(ctx.random.nextDouble() * Math.PI);
			}
			DreadAttacks.warn(victims, "grasping_graves");
			DreadAttacks.sound(ctx.level, ctx.boss.position(), SoundEvents.WITHER_SKELETON_AMBIENT, 2.0F, 0.5F);
			for (Vec3 c : graves.centers) {
				DreadAttacks.sound(ctx.level, c, SoundEvents.WARDEN_DIG, 1.5F, 0.7F);
			}
			return graves;
		}

		@Override
		boolean tick(Context ctx) {
			double radius = def.radius;
			int root = Math.max(20, durationTicks());
			for (int i = 0; i < centers.size(); i++) {
				Vec3 c = centers.get(i).add(0, 0.07, 0);
				double spin = spins.get(i);
				if (age < telegraph) {
					if (age % 2 == 0) {
						double k = (double) age / telegraph;
						CursedFx.warning(ctx.fx, c, radius, k, spin + age * 0.05);
						grave(ctx, c, spin, k);
					}
					if (age % 4 == 0) {
						ctx.fx.burst(theme.debris(), c.x, c.y + 0.1, c.z, 4, radius * 0.4, 0.05, radius * 0.4, 0.15);
					}
					continue;
				}
				int t = age - telegraph;
				if (t == 0) {
					DreadAttacks.sound(ctx.level, c, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 1.4F, 0.5F);
					DreadAttacks.sound(ctx.level, c, SoundEvents.BONE_BLOCK_BREAK, 2.0F, 0.6F);
					ctx.fx.burst(theme.debris(), c.x, c.y + 0.2, c.z, 30, radius * 0.5, 0.2, radius * 0.5, 0.3);
					for (int h = 0; h < 6; h++) {
						double a = spin + Math.PI * 2 * h / 6;
						double d = radius * (0.35 + 0.5 * ctx.random.nextDouble());
						hand(ctx, new Vec3(c.x + Math.cos(a) * d, c.y, c.z + Math.sin(a) * d), 1.6);
					}
					for (ServerPlayer player : ctx.targets) {
						if (DreadAttacks.inside(player, centers.get(i), radius) && grasped.add(player.getUUID())) {
							DreadAttacks.hurt(ctx, player, ctx.damage(def.damage), ctx.physical());
							Roots.root(player, root, ctx.now);
							DreadAttacks.effect(ctx, player, MobEffects.WITHER, 60, def.amplifier - 1);
							Msg.actionBar(player, "ruin.dread.rooted");
						}
					}
				} else if (t < 10 && t % 2 == 0) {
					// The hands rise from the open graves.
					double a = spin + t * 0.7;
					hand(ctx, new Vec3(c.x + Math.cos(a) * radius * 0.5, c.y, c.z + Math.sin(a) * radius * 0.5), 1.2 + t * 0.06);
				}
			}
			if (age >= telegraph && (age - telegraph) % 3 == 0) {
				// Hands keep their grip on the rooted players.
				for (ServerPlayer player : ctx.targets) {
					if (grasped.contains(player.getUUID()) && Roots.rooted(player.getUUID())) {
						Vec3 feet = player.position();
						for (int h = 0; h < 2; h++) {
							double a = (age + h * 3) * 0.6;
							hand(ctx, feet.add(Math.cos(a) * 0.45, 0, Math.sin(a) * 0.45), 1.0);
						}
					}
				}
			}
			return age < telegraph + root + 4;
		}

		/** The outline of an open grave (a rectangle of bone dust, cracks across it). */
		private void grave(Context ctx, Vec3 c, double spin, double k) {
			double cos = Math.cos(spin);
			double sin = Math.sin(spin);
			double w = 0.6;
			double l = 1.1;
			Vec3[] corners = new Vec3[4];
			double[][] offsets = {{-w, -l}, {w, -l}, {w, l}, {-w, l}};
			for (int i = 0; i < 4; i++) {
				double x = offsets[i][0] * cos - offsets[i][1] * sin;
				double z = offsets[i][0] * sin + offsets[i][1] * cos;
				corners[i] = c.add(x, 0.02, z);
			}
			ParticleOptions bone = RitualFx.dust(theme.primary(), 0.8F);
			for (int i = 0; i < 4; i++) {
				ctx.fx.line(bone, corners[i], corners[(i + 1) % 4], 3);
			}
			if (k > 0.5) {
				ctx.fx.line(RitualFx.dust(theme.secondary(), 0.7F), corners[0], corners[2], 3);
			}
		}

		/** A bony hand: a short column of bone dust with spread fingers. */
		private void hand(Context ctx, Vec3 base, double height) {
			ParticleOptions bone = RitualFx.dust(theme.primary(), 1.0F);
			ctx.fx.line(bone, base, base.add(0, height, 0), 4);
			Vec3 top = base.add(0, height, 0);
			for (int f = 0; f < 3; f++) {
				double a = f * 2.1;
				ctx.fx.line(bone, top, top.add(Math.cos(a) * 0.25, 0.3, Math.sin(a) * 0.25), 6);
			}
			ctx.fx.burst(ParticleTypes.SOUL, top.x, top.y, top.z, 1, 0.1, 0.1, 0.1, 0.01);
		}
	}

	// ================================================================== bone prison

	/** A ring of bone pillars closes on a player: whoever has not left it when it snaps is imprisoned (rooted, drained). */
	static final class Prison extends Running {
		private static final double END_RADIUS = 1.4;
		private static final int PILLARS = 9;
		private final Vec3 center;
		private final int closing;
		private final Set<UUID> caught = new HashSet<>();

		private Prison(DreadAbilityDef def, String style, int telegraph, Vec3 center) {
			super(def, style, telegraph);
			this.center = center;
			// The ring closes over twice the warning: time to walk out of it.
			this.closing = Math.max(this.telegraph * 2, 40);
		}

		static @Nullable Prison cast(DreadAbilityDef def, Context ctx, int telegraph, String style) {
			List<ServerPlayer> victims = DreadAttacks.pick(ctx, 1);
			if (victims.isEmpty()) {
				return null;
			}
			ServerPlayer victim = victims.getFirst();
			Prison prison = new Prison(def, style, telegraph, DreadAttacks.floorUnder(ctx.level, victim));
			DreadAttacks.warn(victims, "bone_prison");
			DreadAttacks.sound(ctx.level, prison.center, SoundEvents.SKELETON_AMBIENT, 2.0F, 0.4F);
			return prison;
		}

		@Override
		boolean warning() {
			return age < closing;
		}

		@Override
		boolean tick(Context ctx) {
			double start = Math.max(END_RADIUS + 1, def.radius);
			int jail = Math.max(20, durationTicks());
			Vec3 c = center.add(0, 0.05, 0);
			if (age < closing) {
				double k = (double) age / closing;
				double radius = DreadRules.closing(start, END_RADIUS, k);
				if (age % 2 == 0) {
					ctx.fx.ring(RitualFx.dust(DreadFx.WARNING, 1.0F), c, radius, (int) Math.max(14, radius * 8), age * 0.04);
					ctx.fx.ring(RitualFx.fade(theme.secondary(), CursedFx.VOID, 0.8F), c, END_RADIUS, 12, -age * 0.08);
					for (int p = 0; p < PILLARS; p++) {
						double a = Math.PI * 2 * p / PILLARS + age * 0.02;
						Vec3 base = new Vec3(c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius);
						ctx.fx.line(RitualFx.dust(theme.primary(), 1.1F), base, base.add(0, 1.4 + 1.2 * k, 0), 3);
					}
				}
				if (age % 10 == 0) {
					DreadAttacks.sound(ctx.level, c, SoundEvents.SKELETON_HURT, 1.2F, 0.4F + (float) k * 0.6F);
				}
				return true;
			}
			int t = age - closing;
			if (t == 0) {
				DreadAttacks.sound(ctx.level, c, SoundEvents.ANVIL_LAND, 1.2F, 0.5F);
				DreadAttacks.sound(ctx.level, c, SoundEvents.BONE_BLOCK_PLACE, 2.0F, 0.5F);
				ctx.fx.burst(theme.debris(), c.x, c.y + 1, c.z, 30, END_RADIUS, 0.8, END_RADIUS, 0.2);
				for (ServerPlayer player : ctx.targets) {
					if (DreadAttacks.inside(player, center, END_RADIUS + 0.6)) {
						caught.add(player.getUUID());
						Roots.root(player, jail, ctx.now);
						DreadAttacks.effect(ctx, player, MobEffects.MINING_FATIGUE, jail, 1);
						Msg.actionBar(player, "ruin.dread.imprisoned");
					}
				}
			}
			if (t % 3 == 0) {
				// The cage: bars and a ring on top.
				for (int p = 0; p < PILLARS; p++) {
					double a = Math.PI * 2 * p / PILLARS;
					Vec3 base = new Vec3(c.x + Math.cos(a) * END_RADIUS, c.y, c.z + Math.sin(a) * END_RADIUS);
					ctx.fx.line(RitualFx.dust(theme.primary(), 1.2F), base, base.add(0, 2.8, 0), 3);
				}
				ctx.fx.ring(RitualFx.dust(theme.primary(), 1.2F), c.add(0, 2.8, 0), END_RADIUS, 14, 0);
			}
			if (t > 0 && t % 10 == 0) {
				for (ServerPlayer player : ctx.targets) {
					if (caught.contains(player.getUUID()) && DreadAttacks.inside(player, center, END_RADIUS + 1.2)) {
						DreadAttacks.hurt(ctx, player, ctx.damage(def.damage), ctx.magic());
						ctx.fx.burst(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 4, 0.3, 0.4, 0.3, 0.02);
					}
				}
			}
			if (t == jail) {
				DreadAttacks.sound(ctx.level, c, SoundEvents.BONE_BLOCK_BREAK, 2.0F, 0.8F);
				ctx.fx.burst(theme.debris(), c.x, c.y + 1, c.z, 24, END_RADIUS, 0.8, END_RADIUS, 0.2);
			}
			return t < jail;
		}

		Vec3 center() {
			return center;
		}
	}

	// ================================================================== gravity well / quicksand

	/**
	 * A well opens under a player (red circle, a spiral drawn inward): for a few seconds it drags the players around toward its
	 * center, which hurts; the gravity well then implodes (a blow lifting the players near it), the quicksand swallows.
	 */
	static final class Well extends Running {
		private final Vec3 center;

		private Well(DreadAbilityDef def, String style, int telegraph, Vec3 center) {
			super(def, style, telegraph);
			this.center = center;
		}

		static @Nullable Well cast(DreadAbilityDef def, Context ctx, int telegraph, String style) {
			List<ServerPlayer> victims = DreadAttacks.pick(ctx, 1);
			if (victims.isEmpty()) {
				return null;
			}
			Well well = new Well(def, style, telegraph, DreadAttacks.floorUnder(ctx.level, victims.getFirst()));
			List<ServerPlayer> around = new ArrayList<>();
			for (ServerPlayer player : ctx.targets) {
				if (DreadAttacks.horizontal(player.position(), well.center) <= def.radius + 3) {
					around.add(player);
				}
			}
			boolean sand = "sand".equals(style);
			DreadAttacks.warn(around, sand ? "quicksand" : "gravity_well");
			DreadAttacks.sound(ctx.level, well.center, sand ? SoundEvents.SAND_FALL : SoundEvents.PORTAL_TRIGGER, sand ? 2.0F : 0.6F, 0.6F);
			DreadAttacks.sound(ctx.level, ctx.boss.position(), sand ? SoundEvents.HUSK_AMBIENT : SoundEvents.EVOKER_PREPARE_ATTACK, 2.0F, 0.5F);
			return well;
		}

		@Override
		boolean tick(Context ctx) {
			double radius = def.radius;
			boolean sand = "sand".equals(style);
			Vec3 c = center.add(0, 0.06, 0);
			if (age < telegraph) {
				if (age % 2 == 0) {
					CursedFx.warning(ctx.fx, c, radius, (double) age / telegraph, age * 0.08);
					spiral(ctx, c, radius, age, 0.6F);
				}
				return true;
			}
			int t = age - telegraph;
			int duration = Math.max(20, durationTicks());
			if (t < duration) {
				if (age % 2 == 0) {
					spiral(ctx, c, radius, age, 1.0F);
					ctx.fx.ring(RitualFx.dust(theme.primary(), 1.0F), c, radius, (int) Math.max(14, radius * 7), -age * 0.1);
				}
				if (sand && age % 3 == 0) {
					ctx.fx.burst(theme.particle(), c.x, c.y + 0.2, c.z, 6, radius * 0.5, 0.05, radius * 0.5, 0.05);
				}
				if (!sand && age % 4 == 0) {
					CursedFx.implode(ctx.fx, c.add(0, 1.0, 0), 1.6, (age % 20) / 20.0, theme.primary(), 16, age * 0.2);
				}
				if (t % 20 == 0) {
					DreadAttacks.sound(ctx.level, c, sand ? SoundEvents.SAND_STEP : SoundEvents.PORTAL_AMBIENT, 1.5F, sand ? 0.5F : 0.8F);
				}
				for (ServerPlayer player : ctx.targets) {
					if (Math.abs(player.getY() - c.y) > 3) {
						continue;
					}
					double dist = DreadAttacks.horizontal(player.position(), c);
					double pull = DreadRules.pull(dist, radius, sand ? 0.075 : 0.11);
					if (pull > 0) {
						Vec3 dir = DreadAttacks.away(player.position(), c);
						DreadAttacks.push(player, dir.x * pull, 0, dir.z * pull);
						if (sand && t % 10 == 0) {
							DreadAttacks.effect(ctx, player, MobEffects.SLOWNESS, 25, 1);
						}
					}
					if (dist <= 1.6 && t % 10 == 0) {
						DreadAttacks.hurt(ctx, player, ctx.damage(def.damage), ctx.magic());
						ctx.fx.burst(theme.particle(), player.getX(), player.getY() + 0.5, player.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
					}
				}
				return true;
			}
			if (t == duration) {
				// The end: the well implodes / the sand swallows the ones at its heart.
				ctx.fx.dot(RitualFx.flash(theme.primary()), c.add(0, 1, 0));
				ctx.fx.sphereBurst(RitualFx.fade(theme.primary(), theme.secondary(), 1.4F), c.add(0, 1, 0), 0.3, 50, 0.4);
				DreadAttacks.sound(ctx.level, c, sand ? SoundEvents.SAND_BREAK : SoundEvents.BEACON_DEACTIVATE, 2.0F, 0.6F);
				for (ServerPlayer player : ctx.targets) {
					if (DreadAttacks.inside(player, center, 2.5)) {
						DreadAttacks.hurt(ctx, player, ctx.damage(def.damage * 2), ctx.magic());
						if (!sand) {
							DreadAttacks.push(player, 0, 0.8, 0);
						} else {
							DreadAttacks.effect(ctx, player, MobEffects.BLINDNESS, 30, 0);
						}
					}
				}
			}
			return false;
		}

		/** Arms of the spiral turning toward the center. */
		private void spiral(Context ctx, Vec3 c, double radius, int age, float size) {
			ParticleOptions dust = RitualFx.dust("sand".equals(style) ? theme.secondary() : theme.primary(), size);
			for (int arm = 0; arm < 3; arm++) {
				for (int i = 0; i < 6; i++) {
					double r = radius * (1 - i / 6.0);
					double a = age * 0.12 + arm * Math.PI * 2 / 3 + (1 - r / radius) * 2.4;
					if (!ctx.fx.dot(dust, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r)) {
						return;
					}
				}
			}
		}

		Vec3 center() {
			return center;
		}
	}

	// ================================================================== mummy curse

	/**
	 * A player is marked (bandages of sand spinning around them, the red circle of the blast following them, a countdown):
	 * when it ends, the curse bursts on every player inside that circle, the marked one included. Spread out!
	 */
	static final class Mark extends Running {
		private final Map<UUID, Vec3> marked = new HashMap<>();
		private final int fuse;

		private Mark(DreadAbilityDef def, String style, int telegraph) {
			super(def, style, telegraph);
			this.fuse = Math.max(this.telegraph * 2, (int) Math.round(def.durationSeconds * 20));
		}

		static @Nullable Mark cast(DreadAbilityDef def, Context ctx, int telegraph, String style) {
			List<ServerPlayer> victims = DreadAttacks.pick(ctx, Math.max(1, def.count));
			if (victims.isEmpty()) {
				return null;
			}
			Mark mark = new Mark(def, style, telegraph);
			for (ServerPlayer player : victims) {
				mark.marked.put(player.getUUID(), player.position());
				Msg.title(player, Lang.get("ruin.dread.mummy.title"), Lang.get("ruin.dread.mummy.subtitle"), 0, 40, 10);
				Msg.actionBar(player, "ruin.dread.warn.mummy_curse");
			}
			for (ServerPlayer player : ctx.targets) {
				if (!mark.marked.containsKey(player.getUUID())) {
					Msg.actionBar(player, "ruin.dread.warn.mummy_curse.others", victims.getFirst().getName().getString());
				}
			}
			DreadAttacks.sound(ctx.level, ctx.boss.position(), SoundEvents.HUSK_CONVERTED_TO_ZOMBIE, 2.0F, 0.5F);
			DreadAttacks.sound(ctx.level, ctx.boss.position(), SoundEvents.ELDER_GUARDIAN_CURSE, 0.8F, 1.4F);
			return mark;
		}

		@Override
		boolean warning() {
			return age < fuse;
		}

		@Override
		boolean tick(Context ctx) {
			double radius = def.radius;
			for (Map.Entry<UUID, Vec3> entry : marked.entrySet()) {
				ServerPlayer player = ctx.level.getServer().getPlayerList().getPlayer(entry.getKey());
				if (player != null && BossAbilities.valid(player, ctx.level)) {
					entry.setValue(player.position());
				}
				Vec3 at = entry.getValue();
				Vec3 ground = DreadAttacks.floor(ctx.level, at.x, at.y, at.z).add(0, 0.07, 0);
				if (age < fuse) {
					double k = (double) age / fuse;
					if (age % 2 == 0) {
						CursedFx.warning(ctx.fx, ground, radius, k, age * 0.05);
						// Bandages of sand spinning around the cursed one.
						for (int s = 0; s < 2; s++) {
							double a = age * 0.35 + s * Math.PI;
							double h = (age * 0.08 + s * 0.9) % 1.9;
							ctx.fx.dot(RitualFx.dust(theme.primary(), 0.9F), at.x + Math.cos(a) * 0.6, at.y + 0.1 + h, at.z + Math.sin(a) * 0.6);
						}
					}
					int beat = (int) Math.max(5, Math.round(20 - 14 * k));
					if (age % beat == 0) {
						DreadAttacks.sound(ctx.level, at, SoundEvents.NOTE_BLOCK_BASEDRUM, 1.5F, 0.6F + (float) k);
					}
					continue;
				}
				// The curse bursts.
				ctx.fx.burst(theme.particle(), ground.x, ground.y + 0.6, ground.z, 40, radius * 0.5, 0.4, radius * 0.5, 0.2);
				ctx.fx.burst(ParticleTypes.LARGE_SMOKE, ground.x, ground.y + 0.8, ground.z, 16, radius * 0.4, 0.3, radius * 0.4, 0.03);
				ctx.fx.ring(RitualFx.fade(theme.primary(), theme.secondary(), 1.4F), ground.add(0, 0.2, 0), radius, (int) (radius * 8), 0);
				DreadAttacks.sound(ctx.level, ground, SoundEvents.GENERIC_EXPLODE, 1.4F, 0.7F);
				DreadAttacks.sound(ctx.level, ground, SoundEvents.HUSK_AMBIENT, 2.0F, 0.4F);
				for (ServerPlayer victim : ctx.targets) {
					if (DreadAttacks.horizontal(victim.position(), at) <= radius && Math.abs(victim.getY() - at.y) <= 2.5) {
						DreadAttacks.hurt(ctx, victim, ctx.damage(def.damage), ctx.magic());
						DreadAttacks.effect(ctx, victim, MobEffects.HUNGER, 100, 1);
						DreadAttacks.effect(ctx, victim, MobEffects.WEAKNESS, 100, 0);
						DreadAttacks.effect(ctx, victim, MobEffects.SLOWNESS, 50, 0);
					}
				}
			}
			return age < fuse;
		}
	}

	// ================================================================== sandstorm / blizzard

	/**
	 * A storm sweeps the arena: only its eye (a green circle drawn somewhere in the arena while the wind rises) is safe;
	 * outside, every half second, it hurts and blinds (sand) or freezes (blizzard).
	 */
	static final class Storm extends Running {
		private final List<Vec3> eyes = new ArrayList<>();
		private final int gather;

		private Storm(DreadAbilityDef def, String style, int telegraph) {
			super(def, style, telegraph);
			// Time to reach the eye.
			this.gather = this.telegraph * 2 + 10;
		}

		static @Nullable Storm cast(DreadAbilityDef def, Context ctx, int telegraph, String style) {
			if (ctx.targets.isEmpty()) {
				return null;
			}
			Storm storm = new Storm(def, style, telegraph);
			for (int i = 0; i < Math.max(1, def.count); i++) {
				Vec3 spot = null;
				for (int attempt = 0; attempt < 12 && spot == null; attempt++) {
					// A few steps from a player (reachable during the warning), away from the guardian, on a clear floor.
					ServerPlayer near = ctx.targets.get(ctx.random.nextInt(ctx.targets.size()));
					double a = ctx.random.nextDouble() * Math.PI * 2;
					double d = 3 + ctx.random.nextDouble() * 4;
					Vec3 candidate = DreadAttacks.floor(ctx.level, near.getX() + Math.cos(a) * d, near.getY() + 1, near.getZ() + Math.sin(a) * d);
					if (DreadAttacks.horizontal(candidate, ctx.boss.position()) >= 3.5 && Math.abs(candidate.y - near.getY()) <= 2
							&& !DreadAttacks.solid(ctx.level, candidate.add(0, 0.5, 0)) && !DreadAttacks.solid(ctx.level, candidate.add(0, 1.5, 0))) {
						spot = candidate;
					}
				}
				storm.eyes.add(spot != null ? spot : DreadAttacks.floor(ctx.level, ctx.encounter.spawn.getX() + 0.5, ctx.encounter.spawn.getY() + 0.5,
						ctx.encounter.spawn.getZ() + 0.5));
			}
			boolean sand = "sand".equals(style);
			DreadAttacks.warn(ctx.targets, sand ? "sandstorm" : "blizzard_dome");
			DreadAttacks.sound(ctx.level, ctx.boss.position(), sand ? SoundEvents.HUSK_AMBIENT : SoundEvents.PLAYER_HURT_FREEZE, 2.5F, 0.4F);
			DreadAttacks.sound(ctx.level, ctx.boss.position(), SoundEvents.ELYTRA_FLYING, 1.5F, 0.6F);
			return storm;
		}

		@Override
		boolean warning() {
			return age < gather;
		}

		@Override
		boolean tick(Context ctx) {
			double radius = def.radius;
			boolean sand = "sand".equals(style);
			int duration = Math.max(40, durationTicks());
			if (age % 3 == 0) {
				double k = Math.min(1, (double) age / gather);
				for (Vec3 eye : eyes) {
					DreadFx.safeCircle(ctx.fx, eye.add(0, 0.08, 0), radius, age * 0.03, k);
					if (age < gather) {
						ctx.fx.dot(ParticleTypes.HAPPY_VILLAGER, eye.x, eye.y + 1.2, eye.z);
					}
				}
			}
			// The wind around every player (rising during the warning).
			double strength = age < gather ? (double) age / gather : 1.0;
			for (ServerPlayer player : ctx.targets) {
				int n = (int) Math.round(2 + 6 * strength);
				ctx.fx.burst(theme.particle(), player.getX(), player.getY() + 1.2, player.getZ(), n, 3.0, 1.2, 3.0, 0.08);
				if (age % 2 == 0) {
					ctx.fx.burst(RitualFx.dust(sand ? theme.primary() : 0xFFFFFF, 1.6F), player.getX(), player.getY() + 1.0, player.getZ(), (int) (1 + 3 * strength),
							2.5, 1.0, 2.5, 0.0);
				}
			}
			if (age < gather) {
				return true;
			}
			int t = age - gather;
			if (t == 0) {
				DreadAttacks.sound(ctx.level, ctx.boss.position(), SoundEvents.BREEZE_WIND_CHARGE_BURST, 2.5F, 0.5F);
				for (ServerPlayer player : ctx.targets) {
					Msg.actionBar(player, sand ? "ruin.dread.storm.sand" : "ruin.dread.storm.ice");
				}
			}
			if (t % 20 == 0) {
				DreadAttacks.sound(ctx.level, ctx.boss.position(), SoundEvents.ELYTRA_FLYING, 1.2F, sand ? 0.5F : 0.8F);
			}
			if (t % 10 == 0) {
				for (ServerPlayer player : ctx.targets) {
					boolean safe = false;
					for (Vec3 eye : eyes) {
						if (DreadAttacks.inside(player, eye, radius + 0.3)) {
							safe = true;
							break;
						}
					}
					if (safe) {
						continue;
					}
					DreadAttacks.hurt(ctx, player, ctx.damage(def.damage), ctx.magic());
					if (sand) {
						DreadAttacks.effect(ctx, player, MobEffects.BLINDNESS, 40, 0);
						DreadAttacks.effect(ctx, player, MobEffects.SLOWNESS, 30, 0);
					} else {
						DreadAttacks.effect(ctx, player, MobEffects.SLOWNESS, 30, Math.max(0, def.amplifier));
						player.setTicksFrozen(Math.min(player.getTicksRequiredToFreeze() + 60, player.getTicksFrozen() + 60));
					}
				}
			}
			return t < duration;
		}

		List<Vec3> eyes() {
			return eyes;
		}
	}

	// ================================================================== zones on the ground

	/**
	 * Zones under and around the players, announced by red circles: frozen ground and soul pillars linger (every half second:
	 * damage, slowness and frost / soul fire), lava geysers erupt once (thrown in the air, set on fire).
	 */
	static final class Patches extends Running {
		private final List<Vec3> centers = new ArrayList<>();
		private final boolean linger;
		private final Set<UUID> erupted = new HashSet<>();

		private Patches(DreadAbilityDef def, String style, int telegraph, boolean linger) {
			super(def, style, telegraph);
			this.linger = linger;
		}

		static @Nullable Patches cast(DreadAbilityDef def, Context ctx, int telegraph, String style, boolean linger) {
			if (ctx.targets.isEmpty()) {
				return null;
			}
			Patches patches = new Patches(def, style, telegraph, linger);
			int count = Math.max(1, def.count);
			List<ServerPlayer> victims = DreadAttacks.pick(ctx, count);
			for (ServerPlayer player : victims) {
				patches.centers.add(DreadAttacks.floorUnder(ctx.level, player));
			}
			for (int i = victims.size(); i < count; i++) {
				ServerPlayer near = ctx.targets.get(ctx.random.nextInt(ctx.targets.size()));
				double a = ctx.random.nextDouble() * Math.PI * 2;
				double d = def.radius * 2 + ctx.random.nextDouble() * 3;
				patches.centers.add(DreadAttacks.floor(ctx.level, near.getX() + Math.cos(a) * d, near.getY(), near.getZ() + Math.sin(a) * d));
			}
			DreadAttacks.warn(victims, "ice".equals(style) ? "frozen_ground" : "lava".equals(style) ? "lava_geysers" : "soul_pillars");
			for (Vec3 c : patches.centers) {
				switch (style) {
					case "ice" -> DreadAttacks.sound(ctx.level, c, SoundEvents.POWDER_SNOW_STEP, 1.5F, 0.5F);
					case "lava" -> DreadAttacks.sound(ctx.level, c, SoundEvents.LAVA_AMBIENT, 2.0F, 0.6F);
					default -> DreadAttacks.sound(ctx.level, c, SoundEvents.SOUL_ESCAPE, 1.5F, 0.5F);
				}
			}
			return patches;
		}

		@Override
		boolean tick(Context ctx) {
			double radius = def.radius;
			int duration = linger ? Math.max(20, durationTicks()) : 14;
			for (Vec3 center : centers) {
				Vec3 c = center.add(0, 0.07, 0);
				if (age < telegraph) {
					if (age % 2 == 0) {
						CursedFx.warning(ctx.fx, c, radius, (double) age / telegraph, age * 0.06);
					}
					if (age % 3 == 0) {
						ctx.fx.burst(theme.particle(), c.x, c.y + 0.1, c.z, 3, radius * 0.4, 0.05, radius * 0.4, 0.02);
					}
					continue;
				}
				int t = age - telegraph;
				if (t == 0) {
					erupt(ctx, c, radius);
				}
				if (t >= duration) {
					continue;
				}
				draw(ctx, c, radius, t);
				if (!linger || t % 10 != 0) {
					continue;
				}
				for (ServerPlayer player : ctx.targets) {
					if (DreadAttacks.inside(player, center, radius)) {
						DreadAttacks.hurt(ctx, player, ctx.damage(def.damage), ctx.magic());
						if ("ice".equals(style)) {
							DreadAttacks.effect(ctx, player, MobEffects.SLOWNESS, 30, Math.max(0, def.amplifier));
							player.setTicksFrozen(Math.min(player.getTicksRequiredToFreeze() + 40, player.getTicksFrozen() + 40));
						} else {
							DreadAttacks.effect(ctx, player, MobEffects.WITHER, 40, 0);
							player.igniteForSeconds(2.0F);
						}
					}
				}
			}
			return age < telegraph + duration;
		}

		private void erupt(Context ctx, Vec3 c, double radius) {
			switch (style) {
				case "ice" -> {
					DreadAttacks.sound(ctx.level, c, SoundEvents.GLASS_BREAK, 1.6F, 0.6F);
					DreadAttacks.sound(ctx.level, c, SoundEvents.PLAYER_HURT_FREEZE, 1.2F, 0.6F);
					ctx.fx.burst(theme.debris(), c.x, c.y + 0.2, c.z, 24, radius * 0.5, 0.1, radius * 0.5, 0.2);
				}
				case "lava" -> {
					DreadAttacks.sound(ctx.level, c, SoundEvents.LAVA_POP, 2.0F, 0.6F);
					DreadAttacks.sound(ctx.level, c, SoundEvents.FIRECHARGE_USE, 1.6F, 0.6F);
					ctx.fx.burst(ParticleTypes.LAVA, c.x, c.y + 0.3, c.z, 20, radius * 0.4, 0.2, radius * 0.4, 0.3);
					for (ServerPlayer player : ctx.targets) {
						if (DreadAttacks.inside(player, c, radius) && erupted.add(player.getUUID())) {
							DreadAttacks.hurt(ctx, player, ctx.damage(def.damage), ctx.blast());
							DreadAttacks.push(player, 0, 1.05, 0);
							player.igniteForSeconds(5.0F);
						}
					}
				}
				default -> {
					DreadAttacks.sound(ctx.level, c, SoundEvents.BLAZE_SHOOT, 1.6F, 0.4F);
					DreadAttacks.sound(ctx.level, c, SoundEvents.SOUL_ESCAPE, 2.0F, 0.6F);
					ctx.fx.burst(ParticleTypes.SOUL, c.x, c.y + 0.5, c.z, 16, radius * 0.4, 0.4, radius * 0.4, 0.06);
				}
			}
		}

		private void draw(Context ctx, Vec3 c, double radius, int t) {
			switch (style) {
				case "ice" -> {
					if (t % 2 == 0) {
						ctx.fx.ring(RitualFx.dust(theme.primary(), 1.0F), c, radius, (int) Math.max(12, radius * 7), t * 0.02);
						ctx.fx.burst(RitualFx.dust(0xFFFFFF, 0.8F), c.x, c.y + 0.05, c.z, 6, radius * 0.45, 0.02, radius * 0.45, 0.0);
					}
					if (t % 4 == 0) {
						ctx.fx.burst(ParticleTypes.SNOWFLAKE, c.x, c.y + 0.4, c.z, 4, radius * 0.4, 0.3, radius * 0.4, 0.01);
					}
				}
				case "lava" -> {
					// A geyser: a column of lava and flames, a few ticks long.
					double height = 5.0 * (1 - t / 14.0) + 0.5;
					DreadFx.column(ctx.fx, ParticleTypes.FLAME, c, height, radius * 0.25, 3);
					if (t % 2 == 0) {
						DreadFx.column(ctx.fx, ParticleTypes.LAVA, c, height * 0.8, radius * 0.2, 1);
						ctx.fx.burst(ParticleTypes.LARGE_SMOKE, c.x, c.y + height, c.z, 3, 0.4, 0.3, 0.4, 0.02);
					}
				}
				default -> {
					// A pillar of soul fire.
					if (t % 2 == 0) {
						DreadFx.column(ctx.fx, ParticleTypes.SOUL_FIRE_FLAME, c, 6.0, radius * 0.35, 2);
						ctx.fx.ring(RitualFx.dust(theme.primary(), 1.1F), c, radius, (int) Math.max(12, radius * 7), t * 0.05);
					}
					if (t % 5 == 0) {
						ctx.fx.burst(ParticleTypes.SOUL, c.x, c.y + 1.5, c.z, 3, radius * 0.3, 1.0, radius * 0.3, 0.03);
					}
				}
			}
		}

		List<Vec3> centers() {
			return centers;
		}
	}

	// ================================================================== soul chains

	/** Chains of souls bind a few players to the guardian: past {@code radius} blocks from it, the chain bites and drags them back. */
	static final class Tether extends Running {
		private final List<UUID> bound = new ArrayList<>();

		private Tether(DreadAbilityDef def, String style, int telegraph) {
			super(def, style, telegraph);
		}

		static @Nullable Tether cast(DreadAbilityDef def, Context ctx, int telegraph, String style) {
			List<ServerPlayer> victims = DreadAttacks.pick(ctx, Math.max(1, def.count));
			if (victims.isEmpty()) {
				return null;
			}
			Tether tether = new Tether(def, style, telegraph);
			for (ServerPlayer player : victims) {
				tether.bound.add(player.getUUID());
			}
			DreadAttacks.warn(victims, "soul_chains", (int) Math.round(def.radius));
			DreadAttacks.sound(ctx.level, ctx.boss.position(), SoundEvents.CHAIN_PLACE, 2.0F, 0.5F);
			DreadAttacks.sound(ctx.level, ctx.boss.position(), SoundEvents.SOUL_ESCAPE, 2.0F, 0.5F);
			return tether;
		}

		@Override
		boolean tick(Context ctx) {
			boolean telegraphing = age < telegraph;
			int t = age - telegraph;
			int duration = Math.max(20, durationTicks());
			Vec3 core = ctx.core();
			boolean any = false;
			for (UUID uuid : bound) {
				ServerPlayer player = ctx.level.getServer().getPlayerList().getPlayer(uuid);
				if (player == null || !BossAbilities.valid(player, ctx.level)) {
					continue;
				}
				any = true;
				Vec3 chest = player.position().add(0, player.getBbHeight() * 0.55, 0);
				double dist = DreadAttacks.horizontal(player.position(), ctx.boss.position());
				boolean taut = dist > def.radius;
				if (age % (taut ? 1 : 2) == 0) {
					double sag = Math.min(2.5, chest.distanceTo(core) * 0.12) * (taut ? 0.15 : 1.0);
					CursedFx.chain(ctx.fx, core, chest, sag, age, !telegraphing);
				}
				if (telegraphing || t >= duration) {
					continue;
				}
				if (t % 10 == 0 && taut) {
					DreadAttacks.hurt(ctx, player, ctx.damage(def.damage), ctx.magic());
					Vec3 pull = DreadAttacks.away(player.position(), ctx.boss.position()).scale(0.9);
					DreadAttacks.push(player, pull.x, 0.15, pull.z);
					Msg.actionBar(player, "ruin.dread.chain.taut", (int) Math.round(def.radius));
					DreadAttacks.sound(ctx.level, chest, SoundEvents.CHAIN_HIT, 1.6F, 0.5F);
					ctx.fx.burst(ParticleTypes.SOUL_FIRE_FLAME, chest.x, chest.y, chest.z, 6, 0.3, 0.3, 0.3, 0.03);
				}
				if (t == duration - 1) {
					DreadAttacks.sound(ctx.level, chest, SoundEvents.CHAIN_BREAK, 1.6F, 0.6F);
					ctx.fx.burst(RitualFx.dust(CursedFx.SOUL, 1.2F), chest.x, chest.y, chest.z, 16, 0.4, 0.4, 0.4, 0.0);
				}
			}
			if (!telegraphing && t % 10 == 0) {
				// The maximum distance, drawn around the guardian.
				ctx.fx.ring(RitualFx.dust(CursedFx.SOUL, 0.8F), ctx.feet().add(0, 0.1, 0), def.radius, (int) Math.max(16, def.radius * 6), age * 0.05);
			}
			return any && age < telegraph + duration;
		}
	}

	// ================================================================== wither aura

	/** A withering aura rises around the guardian (dark rings growing to its radius): staying close withers. */
	static final class Aura extends Running {
		private Aura(DreadAbilityDef def, String style, int telegraph) {
			super(def, style, telegraph);
		}

		static @Nullable Aura cast(DreadAbilityDef def, Context ctx, int telegraph, String style) {
			if (ctx.targets.isEmpty()) {
				return null;
			}
			List<ServerPlayer> near = new ArrayList<>();
			for (ServerPlayer player : ctx.targets) {
				if (player.distanceToSqr(ctx.boss) <= (def.radius + 6) * (def.radius + 6)) {
					near.add(player);
				}
			}
			DreadAttacks.warn(near, "wither_aura");
			DreadAttacks.sound(ctx.level, ctx.boss.position(), SoundEvents.WITHER_AMBIENT, 2.0F, 0.5F);
			return new Aura(def, style, telegraph);
		}

		@Override
		boolean tick(Context ctx) {
			double radius = def.radius;
			Vec3 feet = ctx.feet().add(0, 0.08, 0);
			if (age < telegraph) {
				double k = (double) age / telegraph;
				if (age % 2 == 0) {
					CursedFx.warning(ctx.fx, feet, radius, k, age * 0.05);
					ctx.fx.ring(RitualFx.dust(theme.primary(), 1.4F), feet.add(0, 0.3, 0), radius * k, (int) Math.max(8, radius * k * 6), -age * 0.1);
				}
				return true;
			}
			int t = age - telegraph;
			int duration = Math.max(20, durationTicks());
			if (t == 0) {
				DreadAttacks.sound(ctx.level, feet, SoundEvents.WITHER_SHOOT, 2.0F, 0.5F);
			}
			if (age % 2 == 0) {
				ctx.fx.ring(RitualFx.dust(theme.primary(), 1.6F), feet.add(0, 0.2, 0), radius, (int) Math.max(18, radius * 7), age * 0.04);
				ctx.fx.ring(RitualFx.fade(theme.secondary(), theme.primary(), 1.2F), feet.add(0, 1.2, 0), radius * 0.95, (int) Math.max(10, radius * 4), -age * 0.04);
				ctx.fx.burst(ParticleTypes.LARGE_SMOKE, feet.x, feet.y + 0.6, feet.z, 3, radius * 0.45, 0.3, radius * 0.45, 0.01);
			}
			if (t % 10 == 0) {
				for (ServerPlayer player : ctx.targets) {
					if (DreadAttacks.horizontal(player.position(), feet) <= radius && Math.abs(player.getY() - feet.y) <= 3) {
						DreadAttacks.hurt(ctx, player, ctx.damage(def.damage), ctx.magic());
						DreadAttacks.effect(ctx, player, MobEffects.WITHER, 50, Math.max(0, def.amplifier));
					}
				}
			}
			return t < duration;
		}
	}
}
