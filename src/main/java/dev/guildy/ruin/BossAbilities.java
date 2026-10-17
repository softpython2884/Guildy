package dev.guildy.ruin;

import dev.guildy.ruin.RuinConfig.AbilityDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

/**
 * The guardians' abilities (data-driven list in ruins.json5): animated particle effects and sounds over several ticks
 * ({@link RuinFx}), damage only to survival / adventure players, everything bounded (radius, count, duration).
 */
final class BossAbilities {
	private static final int SLAM_RISE_TICKS = 8;
	private static final int SLAM_WAVE_TICKS = 10;
	private static final int RING_CHARGE_TICKS = 10;
	private static final int RING_WAVE_TICKS = 16;
	private static final int DRAIN_TICKS = 24;
	private static final int SUMMON_TICKS = 14;

	private BossAbilities() {
	}

	/**
	 * Uses an ability. @return false when it could not be used now (no target in reach...): the caller tries another.
	 *
	 * @param targets survival / adventure players in reach of the guardian (never empty)
	 * @param summon  spawns one escort mob near a position (null when the cap is reached)
	 */
	static boolean use(ServerLevel level, Encounter encounter, Mob boss, AbilityDef ability, List<ServerPlayer> targets, RandomGenerator random,
			Summoner summon) {
		return switch (ability.type) {
			case "ground_slam" -> groundSlam(level, boss, ability, targets);
			case "fire_ring" -> ring(level, boss, ability, targets);
			case "blink" -> blink(level, boss, ability, targets, random);
			case "volley" -> volley(level, boss, ability, targets, random);
			case "summon" -> summon(level, encounter, boss, ability, random, summon);
			case "life_drain" -> lifeDrain(level, boss, ability, targets);
			default -> false;
		};
	}

	/** Spawns an escort mob near {@code pos} (respecting the alive cap): implemented by the encounter manager. */
	@FunctionalInterface
	interface Summoner {
		@Nullable Mob summon(ServerLevel level, Encounter encounter, BlockPos pos);
	}

	// ================================================================== ground slam

	/** Leaps, lands with an explosion and sends a shockwave along the ground (damage + knockback). */
	private static boolean groundSlam(ServerLevel level, Mob boss, AbilityDef ability, List<ServerPlayer> targets) {
		if (!boss.onGround() || nearest(boss, targets, ability.radius + 4) == null) {
			return false;
		}
		boss.setDeltaMovement(boss.getDeltaMovement().x * 0.2, 0.85, boss.getDeltaMovement().z * 0.2);
		boss.needsSync = true;
		RuinFx.sound(level, boss.position(), SoundEvents.RAVAGER_ROAR, 1.6F, 0.7F);
		RuinFx.burst(level, ParticleTypes.CLOUD, boss.position(), 20, 0.6, 0.05);
		boolean sand = "sand".equals(ability.style);
		Set<UUID> hit = new HashSet<>();
		int[] impact = {-1};
		Vec3[] center = {boss.position()};
		RuinFx.play(level, (lvl, age) -> {
			if (!boss.isAlive()) {
				return false;
			}
			if (impact[0] < 0) {
				lvl.sendParticles(ParticleTypes.CLOUD, boss.getX(), boss.getY(), boss.getZ(), 2, 0.3, 0.1, 0.3, 0.0);
				if (age >= SLAM_RISE_TICKS && (boss.onGround() || age >= SLAM_RISE_TICKS * 3)) {
					impact[0] = age;
					center[0] = boss.position();
					RuinFx.sound(lvl, center[0], SoundEvents.GENERIC_EXPLODE, 1.4F, 0.6F);
					RuinFx.sound(lvl, center[0], SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.6F, 0.8F);
					RuinFx.burst(lvl, ParticleTypes.EXPLOSION_EMITTER, center[0], 1, 0.0, 0.0);
				}
				return true;
			}
			int t = age - impact[0];
			double radius = ability.radius * Math.min(1.0, (t + 1) / (double) SLAM_WAVE_TICKS);
			Vec3 c = center[0];
			BlockState ground = sand ? Blocks.SAND.defaultBlockState() : lvl.getBlockState(BlockPos.containing(c.x, c.y - 0.5, c.z));
			if (ground.isAir()) {
				ground = Blocks.STONE.defaultBlockState();
			}
			ParticleOptions dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
			int points = (int) Math.max(10, radius * 7);
			RuinFx.ring(lvl, dust, c.add(0, 0.15, 0), radius, points, 0.15);
			RuinFx.ring(lvl, ParticleTypes.CRIT, c.add(0, 0.4, 0), radius, points / 2, 0.1);
			if (t % 3 == 0) {
				RuinFx.ring(lvl, ParticleTypes.CLOUD, c.add(0, 0.3, 0), radius, points / 3, 0.2);
			}
			for (ServerPlayer player : targets) {
				if (!valid(player, lvl) || hit.contains(player.getUUID())) {
					continue;
				}
				double dist = horizontal(player.position(), c);
				if (dist <= radius + 0.6 && dist >= radius - 1.6 && Math.abs(player.getY() - c.y) <= 2.5) {
					hit.add(player.getUUID());
					DamageSource source = lvl.damageSources().mobAttack(boss);
					player.hurtServer(lvl, source, (float) ability.damage);
					Vec3 away = horizontalDirection(c, player.position());
					player.push(away.x * 0.9, 0.55, away.z * 0.9);
					player.syncVelocity = true;
				}
			}
			return t < SLAM_WAVE_TICKS;
		});
		return true;
	}

	// ================================================================== rings

	/** A charged ring of fire / soul fire / frost expanding from the guardian. */
	private static boolean ring(ServerLevel level, Mob boss, AbilityDef ability, List<ServerPlayer> targets) {
		if (nearest(boss, targets, ability.radius + 2) == null) {
			return false;
		}
		String style = ability.style.isEmpty() ? "fire" : ability.style;
		ParticleOptions main = switch (style) {
			case "soul" -> ParticleTypes.SOUL_FIRE_FLAME;
			case "frost" -> ParticleTypes.SNOWFLAKE;
			default -> ParticleTypes.FLAME;
		};
		ParticleOptions extra = switch (style) {
			case "soul" -> ParticleTypes.SOUL;
			case "frost" -> ParticleTypes.ITEM_SNOWBALL;
			default -> ParticleTypes.LAVA;
		};
		switch (style) {
			case "soul" -> RuinFx.sound(level, boss.position(), SoundEvents.SOUL_ESCAPE, 2.0F, 0.6F);
			case "frost" -> RuinFx.sound(level, boss.position(), SoundEvents.PLAYER_HURT_FREEZE, 1.5F, 0.5F);
			default -> RuinFx.sound(level, boss.position(), SoundEvents.BLAZE_SHOOT, 1.5F, 0.6F);
		}
		Set<UUID> hit = new HashSet<>();
		RuinFx.play(level, (lvl, age) -> {
			if (!boss.isAlive()) {
				return false;
			}
			Vec3 c = boss.position();
			if (age < RING_CHARGE_TICKS) {
				// Charge: a double helix rising around the guardian, shrinking.
				double r = 1.6 - age * 0.08;
				for (int k = 0; k < 3; k++) {
					RuinFx.helix(lvl, main, c, r, age * 3 + k, boss.getBbHeight() + 0.5, RING_CHARGE_TICKS * 3);
				}
				return true;
			}
			int t = age - RING_CHARGE_TICKS;
			if (t == 0) {
				switch (style) {
					case "soul" -> RuinFx.sound(lvl, c, SoundEvents.WITHER_SHOOT, 1.5F, 0.6F);
					case "frost" -> RuinFx.sound(lvl, c, SoundEvents.GLASS_BREAK, 1.8F, 0.5F);
					default -> RuinFx.sound(lvl, c, SoundEvents.FIRECHARGE_USE, 1.8F, 0.6F);
				}
				RuinFx.burst(lvl, extra, c.add(0, 1, 0), 25, 0.6, 0.1);
			}
			double radius = ability.radius * Math.min(1.0, (t + 1) / (double) RING_WAVE_TICKS);
			int points = (int) Math.max(12, radius * 8);
			RuinFx.ring(lvl, main, c.add(0, 0.2, 0), radius, points, 0.05);
			RuinFx.ring(lvl, main, c.add(0, 0.9, 0), radius, points / 2, 0.05);
			if (t % 2 == 0) {
				RuinFx.ring(lvl, extra, c.add(0, 0.5, 0), radius, Math.max(4, points / 4), 0.2);
			}
			for (ServerPlayer player : targets) {
				if (!valid(player, lvl) || hit.contains(player.getUUID())) {
					continue;
				}
				double dist = horizontal(player.position(), c);
				if (dist <= radius + 0.5 && dist >= radius - 1.5 && Math.abs(player.getY() - c.y) <= 3) {
					hit.add(player.getUUID());
					player.hurtServer(lvl, lvl.damageSources().mobAttack(boss), (float) ability.damage);
					switch (style) {
						case "soul" -> player.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1), boss);
						case "frost" -> {
							player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 2), boss);
							player.setTicksFrozen(Math.max(player.getTicksFrozen(), 200));
						}
						default -> player.igniteForSeconds(5.0F);
					}
				}
			}
			return t < RING_WAVE_TICKS;
		});
		return true;
	}

	// ================================================================== blink

	/** Vanishes in a burst of portal particles and reappears behind a player, striking at once. */
	private static boolean blink(ServerLevel level, Mob boss, AbilityDef ability, List<ServerPlayer> targets, RandomGenerator random) {
		List<ServerPlayer> reachable = new ArrayList<>();
		for (ServerPlayer player : targets) {
			if (valid(player, level) && player.distanceToSqr(boss) > 9.0 && player.distanceToSqr(boss) <= 24.0 * 24.0) {
				reachable.add(player);
			}
		}
		if (reachable.isEmpty()) {
			return false;
		}
		ServerPlayer target = reachable.get(random.nextInt(reachable.size()));
		Vec3 look = target.getLookAngle();
		Vec3 back = new Vec3(-look.x, 0, -look.z);
		if (back.lengthSqr() < 1.0E-4) {
			back = new Vec3(1, 0, 0);
		}
		back = back.normalize();
		Vec3 side = new Vec3(-back.z, 0, back.x);
		EntityType<?> type = boss.getType();
		BlockPos dest = null;
		for (Vec3 offset : new Vec3[] {back.scale(2.2), side.scale(2.2), side.scale(-2.2), back.scale(-2.2)}) {
			BlockPos base = BlockPos.containing(target.getX() + offset.x, target.getY(), target.getZ() + offset.z);
			for (int dy : new int[] {0, 1, -1}) {
				BlockPos candidate = base.above(dy);
				if (BossFactory.fits(level, type, candidate) && level.noCollision(boss, boss.getBoundingBox().move(
						candidate.getX() + 0.5 - boss.getX(), candidate.getY() - boss.getY(), candidate.getZ() + 0.5 - boss.getZ()))) {
					dest = candidate;
					break;
				}
			}
			if (dest != null) {
				break;
			}
		}
		if (dest == null) {
			return false;
		}
		Vec3 from = boss.position().add(0, boss.getBbHeight() / 2, 0);
		Vec3 to = Vec3.atBottomCenterOf(dest).add(0, boss.getBbHeight() / 2, 0);
		RuinFx.burst(level, ParticleTypes.REVERSE_PORTAL, from, 60, 0.5, 0.2);
		RuinFx.burst(level, ParticleTypes.LARGE_SMOKE, from, 15, 0.4, 0.02);
		RuinFx.line(level, ParticleTypes.WITCH, from, to, (int) Math.max(8, from.distanceTo(to) * 3));
		RuinFx.sound(level, from, SoundEvents.ENDERMAN_TELEPORT, 1.5F, 0.6F);
		boss.teleportTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5);
		boss.getNavigation().stop();
		double dx = target.getX() - boss.getX();
		double dz = target.getZ() - boss.getZ();
		float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
		boss.setYRot(yaw);
		boss.setYHeadRot(yaw);
		boss.setYBodyRot(yaw);
		boss.setTarget(target);
		RuinFx.burst(level, ParticleTypes.PORTAL, to, 60, 0.5, 0.6);
		RuinFx.sound(level, to, SoundEvents.ENDERMAN_TELEPORT, 1.5F, 0.8F);
		ServerPlayer victim = target;
		RuinFx.play(level, (lvl, age) -> {
			if (!boss.isAlive() || !valid(victim, lvl)) {
				return false;
			}
			if (age < 4) {
				// Short telegraph so that the player hears it coming.
				lvl.sendParticles(ParticleTypes.ENCHANTED_HIT, victim.getX(), victim.getY() + 1, victim.getZ(), 4, 0.3, 0.4, 0.3, 0.1);
				return true;
			}
			boss.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
			if (victim.distanceToSqr(boss) <= 6.5 * 6.5) {
				victim.hurtServer(lvl, lvl.damageSources().mobAttack(boss), (float) ability.damage);
				lvl.sendParticles(ParticleTypes.SWEEP_ATTACK, victim.getX(), victim.getY() + 1, victim.getZ(), 1, 0, 0, 0, 0);
				RuinFx.sound(lvl, victim.position(), SoundEvents.EVOKER_FANGS_ATTACK, 1.2F, 0.7F);
			}
			return false;
		});
		return true;
	}

	// ================================================================== volley

	/** A salvo of arrows, spectral arrows or fireballs fired one by one at the players around. */
	private static boolean volley(ServerLevel level, Mob boss, AbilityDef ability, List<ServerPlayer> targets, RandomGenerator random) {
		List<ServerPlayer> inSight = new ArrayList<>();
		for (ServerPlayer player : targets) {
			if (valid(player, level) && boss.hasLineOfSight(player) && player.distanceToSqr(boss) <= 28.0 * 28.0) {
				inSight.add(player);
			}
		}
		if (inSight.isEmpty()) {
			return false;
		}
		String style = ability.style.isEmpty() ? "arrow" : ability.style;
		boolean fireball = "fireball".equals(style);
		RuinFx.sound(level, boss.position(), fireball ? SoundEvents.BLAZE_AMBIENT : SoundEvents.EVOKER_PREPARE_ATTACK, 1.5F, 0.8F);
		int shots = Math.max(1, ability.count);
		RuinFx.play(level, (lvl, age) -> {
			if (!boss.isAlive()) {
				return false;
			}
			Vec3 hand = boss.getEyePosition().add(boss.getLookAngle().scale(0.6));
			if (age < 6) {
				RuinFx.burst(lvl, fireball ? ParticleTypes.FLAME : ParticleTypes.CRIT, hand, 4, 0.25, 0.05);
				return true;
			}
			int fired = (age - 6) / 3;
			if ((age - 6) % 3 != 0) {
				return fired < shots;
			}
			ServerPlayer target = inSight.get(fired % inSight.size());
			if (!valid(target, lvl)) {
				return fired + 1 < shots;
			}
			double spread = 0.08 * (fired - shots / 2.0);
			Vec3 aim = target.position().add(0, target.getBbHeight() * 0.45, 0).subtract(hand);
			Vec3 dir = aim.normalize().add(-aim.z * spread / Math.max(1, aim.length()), 0, aim.x * spread / Math.max(1, aim.length()));
			if (fireball) {
				SmallFireball ball = new SmallFireball(lvl, boss, dir.normalize());
				ball.setPos(hand.x, hand.y, hand.z);
				lvl.addFreshEntity(ball);
				RuinFx.sound(lvl, hand, SoundEvents.BLAZE_SHOOT, 1.0F, 0.9F + random.nextFloat() * 0.2F);
			} else {
				AbstractArrow arrow = "spectral".equals(style)
						? new SpectralArrow(lvl, boss, new ItemStack(Items.SPECTRAL_ARROW), null)
						: new Arrow(lvl, boss, new ItemStack(Items.ARROW), null);
				arrow.setPos(hand.x, hand.y, hand.z);
				arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
				// Arrow damage = base damage x speed (1.8): the configured damage is what lands.
				arrow.setBaseDamage(ability.damage / 1.8);
				double drop = aim.horizontalDistance() * 0.12;
				arrow.shoot(dir.x, dir.y + drop / Math.max(1, aim.length()), dir.z, 1.8F, 2.0F);
				lvl.addFreshEntity(arrow);
				RuinFx.sound(lvl, hand, SoundEvents.SKELETON_SHOOT, 1.0F, 0.8F + random.nextFloat() * 0.3F);
			}
			RuinFx.burst(lvl, fireball ? ParticleTypes.SMALL_FLAME : ParticleTypes.CRIT, hand, 6, 0.15, 0.05);
			return fired + 1 < shots;
		});
		return true;
	}

	// ================================================================== summon

	/** Soul columns rise at a few spots around the guardian, then escort mobs appear there. */
	private static boolean summon(ServerLevel level, Encounter encounter, Mob boss, AbilityDef ability, RandomGenerator random, Summoner summoner) {
		int alive = encounter.minionEntities(level).size();
		int count = RuinRules.summonCount(ability.count, alive, encounter.def.minions.maxAlive);
		if (count <= 0 || encounter.def.minions.types.isEmpty()) {
			return false;
		}
		List<BlockPos> spots = new ArrayList<>();
		String probe = encounter.def.minions.types.keySet().iterator().next();
		for (int i = 0; i < count * 3 && spots.size() < count; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double dist = 2.5 + random.nextDouble() * Math.max(1, ability.radius - 2.5);
			BlockPos around = BlockPos.containing(boss.getX() + Math.cos(angle) * dist, boss.getY(), boss.getZ() + Math.sin(angle) * dist);
			BlockPos spot = BossFactory.standable(level, around, probe, 1);
			if (spot != null && !spots.contains(spot)) {
				spots.add(spot);
			}
		}
		if (spots.isEmpty()) {
			return false;
		}
		RuinFx.sound(level, boss.position(), SoundEvents.EVOKER_PREPARE_SUMMON, 1.6F, 0.7F);
		boss.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
		RuinFx.play(level, (lvl, age) -> {
			if (!boss.isAlive()) {
				return false;
			}
			if (age < SUMMON_TICKS) {
				for (BlockPos spot : spots) {
					Vec3 base = Vec3.atBottomCenterOf(spot);
					RuinFx.helix(lvl, ParticleTypes.SOUL, base, 0.5, age, 2.0, SUMMON_TICKS);
					lvl.sendParticles(ParticleTypes.SMOKE, base.x, base.y + 0.1, base.z, 2, 0.3, 0.05, 0.3, 0.01);
				}
				RuinFx.helix(lvl, ParticleTypes.WITCH, boss.position(), 1.0, age, boss.getBbHeight(), SUMMON_TICKS);
				return true;
			}
			for (BlockPos spot : spots) {
				Mob mob = summoner.summon(lvl, encounter, spot);
				if (mob != null) {
					RuinFx.burst(lvl, ParticleTypes.POOF, mob.position().add(0, 0.6, 0), 12, 0.3, 0.05);
					RuinFx.burst(lvl, ParticleTypes.SOUL_FIRE_FLAME, mob.position().add(0, 0.2, 0), 8, 0.4, 0.02);
				}
			}
			RuinFx.sound(lvl, boss.position(), SoundEvents.EVOKER_CAST_SPELL, 1.5F, 0.8F);
			return false;
		});
		return true;
	}

	// ================================================================== life drain

	/** Red (or soul) threads flow from each nearby player into the guardian, then hurt them and heal it. */
	private static boolean lifeDrain(ServerLevel level, Mob boss, AbilityDef ability, List<ServerPlayer> targets) {
		List<ServerPlayer> victims = new ArrayList<>();
		for (ServerPlayer player : targets) {
			if (valid(player, level) && player.distanceToSqr(boss) <= ability.radius * ability.radius) {
				victims.add(player);
			}
		}
		if (victims.isEmpty() || boss.getHealth() >= boss.getMaxHealth() * 0.98F && victims.size() < 2) {
			return false;
		}
		boolean soul = "soul".equals(ability.style);
		ParticleOptions thread = soul ? ParticleTypes.SCULK_SOUL : new DustParticleOptions(0xB0101A, 1.3F);
		RuinFx.sound(level, boss.position(), SoundEvents.WARDEN_HEARTBEAT, 2.0F, 0.6F);
		RuinFx.play(level, (lvl, age) -> {
			if (!boss.isAlive()) {
				return false;
			}
			Vec3 core = boss.position().add(0, boss.getBbHeight() * 0.6, 0);
			double progress = (double) age / DRAIN_TICKS;
			for (ServerPlayer victim : victims) {
				if (!valid(victim, lvl)) {
					continue;
				}
				Vec3 from = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
				// A pulse travelling toward the guardian along the thread.
				for (int k = 0; k < 3; k++) {
					double t = Math.min(1.0, progress + k * 0.06);
					Vec3 p = from.add(core.subtract(from).scale(t));
					lvl.sendParticles(thread, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
				}
				if (age % 6 == 0) {
					RuinFx.line(lvl, ParticleTypes.SMOKE, from, core, 6);
				}
			}
			if (age < DRAIN_TICKS) {
				return true;
			}
			float drained = 0;
			for (ServerPlayer victim : victims) {
				if (valid(victim, lvl) && victim.distanceToSqr(boss) <= (ability.radius + 4) * (ability.radius + 4)) {
					if (victim.hurtServer(lvl, lvl.damageSources().indirectMagic(boss, boss), (float) ability.damage)) {
						drained += (float) ability.damage;
						lvl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, victim.getX(), victim.getY() + 1, victim.getZ(), 6, 0.3, 0.3, 0.3, 0.1);
					}
				}
			}
			if (drained > 0) {
				boss.heal(drained * 2.0F);
				RuinFx.burst(lvl, soul ? ParticleTypes.SOUL : ParticleTypes.HAPPY_VILLAGER, core, 20, 0.6, 0.05);
				RuinFx.sound(lvl, core, SoundEvents.SOUL_ESCAPE, 1.5F, 1.2F);
			}
			return false;
		});
		return true;
	}

	// ================================================================== enrage

	/** Once under its health threshold: strength + speed, faster abilities, a roar and a rising spiral of flames. */
	static void enrage(ServerLevel level, Mob boss, AbilityDef ability, Consumer<ServerLevel> announce) {
		int amplifier = Math.max(0, ability.amplifier);
		boss.addEffect(new MobEffectInstance(MobEffects.STRENGTH, MobEffectInstance.INFINITE_DURATION, amplifier, false, true));
		boss.addEffect(new MobEffectInstance(MobEffects.SPEED, MobEffectInstance.INFINITE_DURATION, Math.min(1, amplifier), false, true));
		boss.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 200, 1, false, true));
		RuinFx.sound(level, boss.position(), SoundEvents.RAVAGER_ROAR, 2.0F, 0.5F);
		RuinFx.sound(level, boss.position(), SoundEvents.ENDER_DRAGON_GROWL, 0.8F, 1.2F);
		RuinFx.burst(level, ParticleTypes.ANGRY_VILLAGER, boss.position().add(0, boss.getBbHeight(), 0), 12, 0.6, 0.1);
		announce.accept(level);
		RuinFx.play(level, (lvl, age) -> {
			if (!boss.isAlive()) {
				return false;
			}
			Vec3 c = boss.position();
			RuinFx.helix(lvl, ParticleTypes.FLAME, c, 1.2, age, boss.getBbHeight() + 1, 15);
			RuinFx.helix(lvl, ParticleTypes.SMALL_FLAME, c, 0.8, age + 7, boss.getBbHeight() + 1, 15);
			if (age % 5 == 0) {
				RuinFx.ring(lvl, ParticleTypes.LAVA, c.add(0, 0.2, 0), 1.5, 8, 0.1);
			}
			return age < 30;
		});
	}

	// ================================================================== helpers

	static boolean valid(ServerPlayer player, ServerLevel level) {
		return player.isAlive() && !player.isRemoved() && player.level() == level && !player.isSpectator() && !player.isCreative();
	}

	private static @Nullable ServerPlayer nearest(Mob boss, List<ServerPlayer> targets, double range) {
		ServerPlayer best = null;
		double bestDist = range * range;
		for (ServerPlayer player : targets) {
			double dist = player.distanceToSqr(boss);
			if (dist <= bestDist) {
				bestDist = dist;
				best = player;
			}
		}
		return best;
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static Vec3 horizontalDirection(Vec3 from, Vec3 to) {
		Vec3 d = new Vec3(to.x - from.x, 0, to.z - from.z);
		return d.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : d.normalize();
	}
}
