package dev.guildy.unique;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Msg;
import dev.guildy.core.Scheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Powers of the armor set bonuses ({@link SetPower}, given by {@link SetBonuses}); monsters only, nothing ever changes a
 * block:
 * <ul>
 * <li>Sang-froid (Hiver éternel, 2 pieces): the wearer never freezes (powder snow) nor takes freezing damage;</li>
 * <li>Cœur de l'hiver (4 pieces): slowness is shaken off at once; after a blow that leaves the wearer under a health
 * threshold, a frost nova (ice shell, spikes, freezing wave) freezes and throws back the monsters around and the wearer
 * gets resistance and absorption (cooldown shown on the pieces);</li>
 * <li>Embuscade du jaguar (Guerrier jaguar, 4 pieces): crouched without attacking for a moment (shorter in foliage), the
 * wearer is camouflaged (invisibility, the monsters around lose his track, bosses excepted); his next melee blow on a
 * monster, then or shortly after standing up, is an ambush: he springs at it, extra damage, the jaguar's claws rake the
 * monsters around (cooldown shown on the pieces);</li>
 * <li>Bûcher des âmes (Brasier des âmes, 4 pieces): every pulse, an aura drains the souls of the nearest monsters (magic
 * damage of the wearer) and heals him a part of it; running leaves a trail of soul fire (particles only) that sets the
 * monsters crossing it on fire.</li>
 * </ul>
 */
final class SetPowers {
	/** Bosses never lose the track of a camouflaged player (ruin guardians, horde bosses, the wither, the warden). */
	private static final String RUIN_BOSS_TAG = "guildy_ruin_boss";
	private static final String WAVE_TAG = "guildy_wave";
	private static final int TRAIL_MAX = 40;

	/** Stalking state of a jaguar warrior. */
	private static final class Stalk {
		int crouched;
		boolean camouflaged;
		long strikeUntil;
		long readyAt;
	}

	/** Soul pyre state: next pulse, live points of the trail, last burn of each monster. */
	private static final class Pyre {
		long nextPulse;
		final Deque<TrailPoint> trail = new ArrayDeque<>();
		final Map<Integer, Long> burnt = new HashMap<>();
	}

	private record TrailPoint(ServerLevel level, Vec3 pos, long until) {
	}

	private static final Map<UUID, Stalk> STALKS = new HashMap<>();
	private static final Map<UUID, Pyre> PYRES = new HashMap<>();

	private SetPowers() {
	}

	// ================================================================== wiring

	/** Every server tick. */
	static void tick() {
		long now = Scheduler.ticks();
		for (ServerPlayer player : SetBonuses.wearers(SetPower.FROST_BLOOD)) {
			if (player.getTicksFrozen() > 0) {
				player.setTicksFrozen(0);
			}
		}
		for (ServerPlayer player : SetBonuses.wearers(SetPower.WINTER_HEART)) {
			if (player.hasEffect(MobEffects.SLOWNESS)) {
				player.removeEffect(MobEffects.SLOWNESS);
			}
			if (now % 40 == 0 && !player.isSpectator() && !player.isInvisible()) {
				Vec3 at = player.position().add(0, 1.2, 0);
				player.level().sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 3, 0.35, 0.5, 0.35, 0.005);
			}
		}
		for (ServerPlayer player : SetBonuses.wearers(SetPower.JAGUAR_AMBUSH)) {
			stalk(player, now);
		}
		for (ServerPlayer player : SetBonuses.wearers(SetPower.SOUL_PYRE)) {
			pyre(player, now);
		}
	}

	/** The powers of a player changed: the states of the lost ones are dropped (camouflage ended, trail put out). */
	static void onPowersChanged(ServerPlayer player, Set<SetPower> before, Set<SetPower> after) {
		if (before.contains(SetPower.JAGUAR_AMBUSH) && !after.contains(SetPower.JAGUAR_AMBUSH)) {
			Stalk stalk = STALKS.remove(player.getUUID());
			if (stalk != null && stalk.camouflaged) {
				uncloak(player);
			}
		}
		if (before.contains(SetPower.SOUL_PYRE) && !after.contains(SetPower.SOUL_PYRE)) {
			PYRES.remove(player.getUUID());
		}
	}

	/** ALLOW_DAMAGE: no freezing damage for the Sang-froid. */
	static boolean allowDamage(LivingEntity entity, DamageSource source) {
		return !(entity instanceof ServerPlayer player && source.is(DamageTypes.FREEZE) && SetBonuses.has(player, SetPower.FROST_BLOOD));
	}

	/** AFTER_DAMAGE of a player (not killed). */
	static void onDamaged(ServerPlayer player, DamageSource source, float taken) {
		if (taken > 0 && player.isAlive() && !player.isSpectator() && SetBonuses.has(player, SetPower.WINTER_HEART)) {
			winterHeart(player);
		}
	}

	/** A melee blow of the player on a living entity (killed or not). */
	static void onMelee(ServerPlayer player, LivingEntity victim, DamageSource source) {
		if (!SetBonuses.has(player, SetPower.JAGUAR_AMBUSH) || !source.is(DamageTypes.PLAYER_ATTACK)) {
			return;
		}
		Stalk stalk = STALKS.computeIfAbsent(player.getUUID(), uuid -> new Stalk());
		boolean ambush = stalk.camouflaged || Scheduler.ticks() <= stalk.strikeUntil;
		stalk.crouched = 0;
		if (ambush && PowerUtil.isHostileType(victim)) {
			ambush(player, victim, stalk);
		} else if (stalk.camouflaged) {
			// A blow on something else breaks the camouflage.
			stalk.camouflaged = false;
			uncloak(player);
		}
	}

	/** Starts the vanilla cooldown overlay of a set power on the pieces of its set. */
	private static void showCooldown(ServerPlayer player, SetPower power, double seconds) {
		String set = SetBonuses.setOf(player, power);
		if (set != null && seconds > 0) {
			player.getCooldowns().addCooldown(UniqueItems.setCooldownGroup(set), (int) Math.round(seconds * 20));
		}
	}

	// ================================================================== Cœur de l'hiver

	static void winterHeart(ServerPlayer player) {
		UniqueConfig.WinterHeart cfg = PowerUtil.cfg().winterHeart;
		if (player.getHealth() > player.getMaxHealth() * cfg.healthThreshold || !PowerUtil.ready(player, SetPower.WINTER_HEART, cfg.cooldownSeconds)) {
			return;
		}
		showCooldown(player, SetPower.WINTER_HEART, cfg.cooldownSeconds);
		ServerLevel level = player.level();
		Vec3 center = player.position();
		if (cfg.resistanceSeconds > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, cfg.resistanceSeconds * 20, cfg.resistanceLevel - 1));
			if (cfg.absorptionLevel > 0) {
				player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, cfg.resistanceSeconds * 20, cfg.absorptionLevel - 1));
			}
		}
		player.setTicksFrozen(0);
		for (LivingEntity target : PowerUtil.hostilesAround(level, center, cfg.radius, 24, null)) {
			if (cfg.freezeSeconds > 0) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, cfg.freezeSeconds * 20, cfg.slowLevel - 1));
				if (target.canFreeze()) {
					target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + cfg.freezeSeconds * 40));
				}
			}
			PowerUtil.damage(target, PowerUtil.GIVRE, player, cfg.damage);
			double distance = Math.sqrt(target.distanceToSqr(center));
			double falloff = Math.max(0.35, 1.0 - distance / (cfg.radius + 1));
			Vec3 away = PowerUtil.horizontal(target.position().subtract(center)).scale(cfg.push * falloff);
			PowerUtil.push(target, new Vec3(away.x, 0.25 + 0.2 * falloff, away.z));
			UniqueFx.burst(level, ParticleTypes.SNOWFLAKE, target.position().add(0, target.getBbHeight() * 0.6, 0), 10, 0.3, 0.03);
		}
		Msg.actionBar(player, "unique.setpower.winter_heart.fired");
		level.playSound(null, center.x, center.y, center.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2F, 0.5F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.2F, 0.7F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0F, 0.6F);
		double radius = cfg.radius;
		UUID owner = player.getUUID();
		UniqueFx.play(level, 16, (lvl, age) -> {
			ServerPlayer wearer = GuildyServer.player(owner);
			Vec3 at = wearer != null && wearer.level() == lvl ? wearer.position() : center;
			if (age < 6) {
				// The ice shell: a sphere of frost closing around the wearer, then shattering.
				double r = 1.4 - age * 0.12;
				for (int i = 0; i < 14; i++) {
					double theta = Math.PI * 2 * i / 14 + age * 0.5;
					double phi = Math.acos(1 - 2 * ((i * 0.618 + age * 0.13) % 1.0));
					Vec3 p = at.add(Math.sin(phi) * Math.cos(theta) * r, 1.0 + Math.cos(phi) * r, Math.sin(phi) * Math.sin(theta) * r);
					UniqueFx.dot(lvl, UniqueFx.fade(DefensePowers.ICE, DefensePowers.FROST, 1.1F), p);
				}
			}
			if (age == 6) {
				UniqueFx.burst(lvl, ParticleTypes.ITEM_SNOWBALL, at.add(0, 1, 0), 30, 0.6, 0.25);
				UniqueFx.burst(lvl, ParticleTypes.END_ROD, at.add(0, 1, 0), 10, 0.4, 0.08);
			}
			if (age >= 4 && age <= 12) {
				// The freezing wave races outward, spikes of ice rise on its front.
				double r = 0.6 + (radius - 0.6) * (age - 4) / 8.0;
				UniqueFx.ring(lvl, UniqueFx.fade(DefensePowers.ICE, DefensePowers.DEEP, 1.3F), center.add(0, 0.2, 0), r, (int) (12 + r * 6), age * 0.3);
				UniqueFx.ring(lvl, ParticleTypes.SNOWFLAKE, center.add(0, 0.4, 0), r * 0.9, (int) (6 + r * 2), -age * 0.3);
				if (age % 2 == 0) {
					for (int spike = 0; spike < 8; spike++) {
						double angle = Math.PI * 2 * spike / 8 + age * 0.2;
						Vec3 foot = center.add(Math.cos(angle) * r, 0.1, Math.sin(angle) * r);
						UniqueFx.line(lvl, UniqueFx.fade(DefensePowers.ICE, DefensePowers.FROST, 0.8F), foot, foot.add(0, 0.9, 0), 5);
					}
				}
			}
			return age < 15;
		});
	}

	// ================================================================== Embuscade du jaguar

	private static void stalk(ServerPlayer player, long now) {
		UniqueConfig.JaguarAmbush cfg = PowerUtil.cfg().jaguarAmbush;
		Stalk stalk = STALKS.computeIfAbsent(player.getUUID(), uuid -> new Stalk());
		if (stalk.readyAt > 0 && now >= stalk.readyAt) {
			stalk.readyAt = 0;
			Msg.actionBar(player, "unique.setpower.jaguar_ambush.ready");
		}
		boolean crouched = player.isShiftKeyDown() && !player.isPassenger() && !player.isSpectator() && !player.isSprinting()
				&& !player.getAbilities().flying && player.isAlive();
		if (!crouched || !PowerUtil.isReady(player, SetPower.JAGUAR_AMBUSH)) {
			stalk.crouched = 0;
			if (stalk.camouflaged) {
				stalk.camouflaged = false;
				stalk.strikeUntil = now + Math.round(cfg.strikeWindowSeconds * 20);
				uncloak(player);
			}
			return;
		}
		stalk.crouched++;
		double needed = (inFoliage(player) ? cfg.foliageSeconds : cfg.camouflageSeconds) * 20;
		ServerLevel level = player.level();
		if (!stalk.camouflaged && stalk.crouched >= needed) {
			stalk.camouflaged = true;
			Msg.actionBar(player, "unique.setpower.jaguar_ambush.camouflaged");
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AZALEA_LEAVES_STEP, SoundSource.PLAYERS, 0.8F, 0.7F);
			ColorParticleOption leaf = ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, 0xFF000000 | DefensePowers.JADE);
			UniqueFx.burst(level, leaf, player.position().add(0, 1, 0), 10, 0.5, 0.0);
			UniqueFx.burst(level, UniqueFx.dust(JaguarPowers.SPOT, 1.0F), player.position().add(0, 0.8, 0), 8, 0.35, 0.0);
		}
		if (stalk.camouflaged) {
			if (now % 10 == 0) {
				player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, true, false, true));
				loseTrack(player, cfg.loseTrackRange);
			}
			if (now % 15 == 0) {
				UniqueFx.burst(level, UniqueFx.dust(JaguarPowers.GOLD, 0.6F), player.position().add(0, 0.4, 0), 2, 0.4, 0.0);
			}
		}
	}

	/** Leaves, tall grass, vines... at the feet or around the head of the player. */
	private static boolean inFoliage(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos feet = player.blockPosition();
		BlockPos head = BlockPos.containing(player.getEyePosition());
		for (BlockPos pos : List.of(feet, head, head.north(), head.south(), head.east(), head.west())) {
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.LEAVES) || state.is(BlockTags.REPLACEABLE_BY_TREES)) {
				return true;
			}
		}
		return false;
	}

	/** The monsters around that hunt the player forget him (never the bosses). */
	private static void loseTrack(ServerPlayer player, double range) {
		if (range <= 0) {
			return;
		}
		AABB box = player.getBoundingBox().inflate(range);
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class, box, mob -> PowerUtil.isHostileType(mob) && !isBoss(mob))) {
			if (mob.getTargetUnchecked() == player) {
				mob.setTarget(null);
			}
			Optional<LivingEntity> memory = mob.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET);
			if (memory != null && memory.isPresent() && memory.get() == player) {
				mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
			}
		}
	}

	private static boolean isBoss(Mob mob) {
		Set<String> tags = mob.entityTags();
		return tags.contains(RUIN_BOSS_TAG) || tags.contains(WAVE_TAG) && mob.isCustomNameVisible() || mob.getType() == EntityTypes.WITHER
				|| mob.getType() == EntityTypes.WARDEN || mob.getType() == EntityTypes.ELDER_GUARDIAN;
	}

	/** Our short invisibility goes (a potion's longer one stays). */
	private static void uncloak(ServerPlayer player) {
		MobEffectInstance invisibility = player.getEffect(MobEffects.INVISIBILITY);
		if (invisibility != null && invisibility.isAmbient() && invisibility.getDuration() <= 30) {
			player.removeEffect(MobEffects.INVISIBILITY);
		}
	}

	private static void ambush(ServerPlayer player, LivingEntity victim, Stalk stalk) {
		UniqueConfig.JaguarAmbush cfg = PowerUtil.cfg().jaguarAmbush;
		if (!PowerUtil.ready(player, SetPower.JAGUAR_AMBUSH, cfg.cooldownSeconds)) {
			return;
		}
		showCooldown(player, SetPower.JAGUAR_AMBUSH, cfg.cooldownSeconds);
		stalk.camouflaged = false;
		stalk.strikeUntil = 0;
		stalk.readyAt = Scheduler.ticks() + Math.round(cfg.cooldownSeconds * 20);
		uncloak(player);
		ServerLevel level = player.level();
		Vec3 from = player.position();
		Vec3 facing = PowerUtil.horizontal(victim.position().subtract(from));
		// The spring: a short leap onto the prey.
		player.setDeltaMovement(facing.x * 0.45, 0.22, facing.z * 0.45);
		player.connection.send(new ClientboundSetEntityMotionPacket(player));
		if (victim.isAlive()) {
			PowerUtil.magic(victim, player, cfg.damage);
			if (cfg.slowSeconds > 0) {
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, cfg.slowSeconds * 20, cfg.slowLevel - 1));
			}
		}
		JaguarPowers.claws(level, victim, facing);
		Vec3 center = victim.position();
		for (LivingEntity target : PowerUtil.hostilesAround(level, center, cfg.radius, cfg.maxTargets, victim)) {
			PowerUtil.melee(target, player, cfg.clawDamage);
			if (cfg.slowSeconds > 0) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, cfg.slowSeconds * 20, cfg.slowLevel - 1));
			}
			JaguarPowers.claws(level, target, PowerUtil.horizontal(target.position().subtract(center)));
		}
		// Golden streak of the leap, spots and jade feathers, the roar.
		Vec3 chest = from.add(0, 1.0, 0);
		Vec3 hit = center.add(0, victim.getBbHeight() * 0.6, 0);
		UniqueFx.line(level, UniqueFx.fade(JaguarPowers.PALE, JaguarPowers.AMBER, 1.2F), chest, hit, 5);
		UniqueFx.burst(level, UniqueFx.dust(JaguarPowers.SPOT, 1.2F), hit, 8, 0.4, 0.0);
		UniqueFx.burst(level, ParticleTypes.CRIT, hit, 16, 0.4, 0.3);
		double radius = Math.max(1, cfg.radius);
		UniqueFx.play(level, 6, (lvl, age) -> {
			double r = 0.5 + (radius - 0.5) * age / 5.0;
			UniqueFx.ring(lvl, UniqueFx.fade(JaguarPowers.PALE, JaguarPowers.GOLD, 1.0F), center.add(0, 0.2, 0), r, (int) (10 + r * 5), age * 0.4);
			UniqueFx.ring(lvl, UniqueFx.dust(DefensePowers.JADE, 0.8F), center.add(0, 0.35, 0), r * 0.8, 6, -age * 0.4);
			return age < 5;
		});
		Msg.actionBar(player, "unique.setpower.jaguar_ambush.strike");
		level.playSound(null, center.x, center.y, center.z, SoundEvents.POLAR_BEAR_WARNING, SoundSource.PLAYERS, 1.0F, 1.3F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.RAVAGER_ATTACK, SoundSource.PLAYERS, 0.9F, 1.5F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	// ================================================================== Bûcher des âmes

	private static void pyre(ServerPlayer player, long now) {
		UniqueConfig.SoulPyre cfg = PowerUtil.cfg().soulPyre;
		Pyre pyre = PYRES.computeIfAbsent(player.getUUID(), uuid -> new Pyre());
		ServerLevel level = player.level();
		if (now >= pyre.nextPulse && player.isAlive() && !player.isSpectator()) {
			pyre.nextPulse = now + Math.max(10, Math.round(cfg.pulseSeconds * 20));
			pulse(player, cfg);
		}
		// The trail: a soul flame every few ticks while running.
		if (cfg.trailSeconds > 0 && player.isSprinting() && !player.isSpectator() && now % 3 == 0) {
			Vec3 feet = player.position().add(0, 0.1, 0);
			TrailPoint last = pyre.trail.peekLast();
			if (last == null || last.level() != level || last.pos().distanceToSqr(feet) > 0.6) {
				pyre.trail.addLast(new TrailPoint(level, feet, now + Math.round(cfg.trailSeconds * 20)));
				while (pyre.trail.size() > TRAIL_MAX) {
					pyre.trail.removeFirst();
				}
			}
		}
		if (pyre.trail.isEmpty()) {
			return;
		}
		boolean hurt = now % 5 == 0;
		Iterator<TrailPoint> points = pyre.trail.iterator();
		while (points.hasNext()) {
			TrailPoint point = points.next();
			if (now >= point.until() || point.level() != level) {
				points.remove();
				continue;
			}
			if (now % 2 == 0) {
				point.level().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, point.pos().x, point.pos().y + 0.1, point.pos().z, 1, 0.15, 0.05, 0.15, 0.01);
			}
			if (hurt) {
				burnAround(player, pyre, point, cfg, now);
			}
		}
		pyre.burnt.values().removeIf(until -> until <= now);
	}

	/** Monsters standing in a flame of the trail catch fire (once a second each). */
	private static void burnAround(ServerPlayer player, Pyre pyre, TrailPoint point, UniqueConfig.SoulPyre cfg, long now) {
		AABB box = AABB.ofSize(point.pos().add(0, 0.75, 0), 1.6, 1.5, 1.6);
		for (LivingEntity target : point.level().getEntitiesOfClass(LivingEntity.class, box, PowerUtil::isHostile)) {
			if (pyre.burnt.containsKey(target.getId())) {
				continue;
			}
			pyre.burnt.put(target.getId(), now + 20);
			if (cfg.fireSeconds > 0 && !target.fireImmune()) {
				target.igniteForSeconds(cfg.fireSeconds);
			}
			PowerUtil.damage(target, PowerUtil.BRASIER, player, cfg.trailDamage);
			UniqueFx.burst(point.level(), ParticleTypes.SOUL_FIRE_FLAME, target.position().add(0, target.getBbHeight() * 0.5, 0), 6, 0.25, 0.03);
		}
	}

	/** One pulse of the aura: souls torn from the nearest monsters fly to the wearer and heal him. */
	static void pulse(ServerPlayer player, UniqueConfig.SoulPyre cfg) {
		ServerLevel level = player.level();
		Vec3 feet = player.position();
		List<LivingEntity> targets = PowerUtil.hostilesAround(level, feet, cfg.radius, cfg.maxTargets, null);
		if (targets.isEmpty()) {
			if (!player.isInvisible()) {
				UniqueFx.ring(level, ParticleTypes.SOUL_FIRE_FLAME, feet.add(0, 0.15, 0), 0.7, 5, Scheduler.ticks() * 0.3);
			}
			return;
		}
		double drained = 0;
		Vec3 chest = feet.add(0, 1.1, 0);
		for (LivingEntity target : targets) {
			if (PowerUtil.magic(target, player, cfg.damage)) {
				drained += cfg.damage;
			}
			Vec3 at = target.position().add(0, target.getBbHeight() * 0.6, 0);
			int travel = (int) Math.max(3, Math.min(16, at.distanceTo(chest) / 0.6));
			for (int i = 0; i < 4; i++) {
				UniqueFx.dot(level, UniqueFx.trail(chest, i % 2 == 0 ? DefensePowers.SOUL : DefensePowers.SOUL_DARK, travel),
						at.add((i - 1.5) * 0.12, (i % 2) * 0.15, 0));
			}
			UniqueFx.burst(level, ParticleTypes.SCULK_SOUL, at, 3, 0.2, 0.02);
		}
		double heal = Math.min(cfg.maxHeal, drained * cfg.healRatio);
		if (heal > 0 && player.isAlive()) {
			player.heal((float) heal);
		}
		UniqueFx.ring(level, UniqueFx.fade(DefensePowers.SOUL, DefensePowers.SOUL_DARK, 1.0F), feet.add(0, 0.15, 0), 1.0, 12, 0);
		UniqueFx.ring(level, ParticleTypes.SOUL_FIRE_FLAME, feet.add(0, 0.2, 0), Math.min(cfg.radius, 2.5), 10, Scheduler.ticks() * 0.2);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.8F, 0.7F);
	}

	// ================================================================== cleanup

	static void forget(UUID player) {
		STALKS.remove(player);
		PYRES.remove(player);
	}

	static void clear() {
		STALKS.clear();
		PYRES.clear();
	}
}
