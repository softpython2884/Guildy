package dev.guildy.rune;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.ruin.RoomGuards;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Powers of the worn runes. Hit powers (Étincelles, Braise, Lueur) on AFTER_DAMAGE of a player, read from the worn pieces;
 * tick powers (Rebond, Renard, Écho, Dragon, Phénix notice) from a cache of the worn runes refreshed every 10 ticks;
 * the Phénix on ALLOW_DEATH. Players only, monsters only ({@link Enemy} or a ruin mob): never players, villagers,
 * animals or pets. Cooldowns per player and power (in memory).
 */
final class RunePowers {
	/** Armor slots of a player, in lookup order. */
	static final List<EquipmentSlot> ARMOR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

	/** Runes worn by a player (refreshed every 10 ticks) and the state of its tick powers. */
	static final class Worn {
		final EnumSet<RunePower> powers = EnumSet.noneOf(RunePower.class);
		/** Rebond: highest fall distance seen while airborne. */
		double maxFall;
		boolean airborne;
		/** Écho: ticks spent sneaking still; the ping fired for this sneak. */
		int stillTicks;
		boolean pinged;
		int stepTicks;
		/** Dragon: sneak + jump held (one breath per press). */
		boolean breathHeld;
		long noticeAt;
	}

	private static final Map<UUID, Worn> WORN = new HashMap<>();
	private static final Map<UUID, EnumMap<RunePower, Long>> COOLDOWNS = new HashMap<>();
	/** > 0 while a power deals damage: no chain reaction. */
	private static int dealing;

	private RunePowers() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && dealing == 0) {
				Safe.run("rune.hit", () -> onDamaged(player, source));
			}
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
				!(entity instanceof ServerPlayer player) || !Safe.get("rune.phoenix", () -> RuneMythics.rise(player, source), false));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Safe.run("rune.tick", RunePowers::tick);
			Safe.run("rune.fx", RuneFx::tick);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer().getUUID()));
	}

	static void forget(UUID player) {
		WORN.remove(player);
		COOLDOWNS.remove(player);
	}

	static void clear() {
		WORN.clear();
		COOLDOWNS.clear();
		RuneFx.clear();
	}

	// ================================================================== worn runes

	/** Powers of the runes the player wears now (read from the armor slots). */
	static EnumSet<RunePower> wornPowers(ServerPlayer player) {
		EnumSet<RunePower> powers = EnumSet.noneOf(RunePower.class);
		for (EquipmentSlot slot : ARMOR) {
			RunePower power = RuneItems.powerOf(player.getItemBySlot(slot));
			if (power != null) {
				powers.add(power);
			}
		}
		return powers;
	}

	private static @Nullable Worn refresh(ServerPlayer player) {
		EnumSet<RunePower> powers = wornPowers(player);
		if (powers.isEmpty()) {
			WORN.remove(player.getUUID());
			return null;
		}
		Worn worn = WORN.computeIfAbsent(player.getUUID(), k -> new Worn());
		worn.powers.clear();
		worn.powers.addAll(powers);
		return worn;
	}

	// ================================================================== tick

	private static void tick() {
		if (!RuneModule.config().enabled) {
			if (!WORN.isEmpty()) {
				WORN.clear();
			}
			return;
		}
		long now = Scheduler.ticks();
		boolean refresh = now % 10 == 0;
		if (!refresh && WORN.isEmpty()) {
			return;
		}
		for (ServerPlayer player : List.copyOf(GuildyServer.players())) {
			Worn worn = refresh ? refresh(player) : WORN.get(player.getUUID());
			if (worn == null || !player.isAlive() || player.isSpectator()) {
				continue;
			}
			if (worn.powers.contains(RunePower.BOUNCE)) {
				bounceTick(player, worn);
			}
			if (worn.powers.contains(RunePower.FOX) && now % 10 == 3) {
				fox(player);
			}
			if (worn.powers.contains(RunePower.ECHO) && now % 5 == 1) {
				echoTick(player, worn);
			}
			if (worn.powers.contains(RunePower.DRAGON)) {
				RuneMythics.dragonTick(player, worn, now);
			}
			if (worn.powers.contains(RunePower.PHOENIX) && now % 20 == 7) {
				RuneMythics.phoenixNotice(player);
			}
		}
	}

	// ================================================================== hit powers

	static void onDamaged(ServerPlayer player, DamageSource source) {
		if (!RuneModule.config().enabled || player.isSpectator() || !player.isAlive()) {
			return;
		}
		LivingEntity attacker = source.getEntity() instanceof LivingEntity living && living != player && isHostile(living) ? living : null;
		if (attacker == null || attacker.distanceToSqr(player) > 32 * 32) {
			return;
		}
		EnumSet<RunePower> powers = wornPowers(player);
		if (powers.isEmpty()) {
			return;
		}
		if (powers.contains(RunePower.SPARKS) && source.getDirectEntity() == attacker) {
			sparks(player, attacker);
		}
		if (powers.contains(RunePower.EMBER)) {
			ember(player, attacker);
		}
		if (powers.contains(RunePower.GLOW)) {
			glow(player);
		}
	}

	/** Étincelles: a shower of sparks on the monster that struck in melee. */
	static void sparks(ServerPlayer player, LivingEntity attacker) {
		RuneConfig.Sparks cfg = RuneModule.config().powers.sparks;
		if (!ready(player, RunePower.SPARKS, cfg.cooldownSeconds)) {
			return;
		}
		ServerLevel level = player.level();
		magic(attacker, player, cfg.damage);
		Vec3 chest = player.position().add(0, 1.0, 0);
		Vec3 target = attacker.position().add(0, attacker.getBbHeight() * 0.6, 0);
		RuneFx.line(level, ParticleTypes.ELECTRIC_SPARK, chest, target, 4);
		RuneFx.burst(level, ParticleTypes.ELECTRIC_SPARK, target, 16, 0.3, 0.25);
		RuneFx.burst(level, ParticleTypes.CRIT, target, 8, 0.25, 0.3);
		RuneFx.burst(level, RuneFx.flash(0xFFF2A8), target, 1, 0, 0);
		level.playSound(null, target.x, target.y, target.z, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 0.7F, 1.6F);
		level.playSound(null, chest.x, chest.y, chest.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.8F);
	}

	/** Braise: the monster that hit may catch fire. */
	static void ember(ServerPlayer player, LivingEntity attacker) {
		RuneConfig.Ember cfg = RuneModule.config().powers.ember;
		if (attacker.fireImmune() || player.getRandom().nextDouble() >= cfg.chance || !ready(player, RunePower.EMBER, cfg.cooldownSeconds)) {
			return;
		}
		ServerLevel level = player.level();
		attacker.igniteForSeconds(cfg.fireSeconds);
		Vec3 at = attacker.position().add(0, attacker.getBbHeight() * 0.5, 0);
		RuneFx.burst(level, ParticleTypes.FLAME, at, 18, 0.3, 0.04);
		RuneFx.burst(level, ParticleTypes.LAVA, at, 3, 0.2, 0.0);
		RuneFx.burst(level, RuneFx.fade(RuneFx.FLAME, RuneFx.EMBER, 1.2F), player.position().add(0, 1.0, 0), 10, 0.35, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.3F);
	}

	/** Lueur: the monsters around the wearer glow for a few seconds. */
	static void glow(ServerPlayer player) {
		RuneConfig.Glow cfg = RuneModule.config().powers.glow;
		if (!ready(player, RunePower.GLOW, cfg.cooldownSeconds)) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 center = player.position();
		for (LivingEntity target : hostilesAround(level, center, cfg.radius, 48, null)) {
			target.addEffect(new MobEffectInstance(MobEffects.GLOWING, cfg.seconds * 20, 0, false, false));
			RuneFx.burst(level, ParticleTypes.GLOW, target.position().add(0, target.getBbHeight() * 0.7, 0), 6, 0.3, 0.02);
		}
		RuneFx.burst(level, ParticleTypes.GLOW, center.add(0, 1.2, 0), 14, 0.4, 0.05);
		double radius = cfg.radius;
		RuneFx.play(level, 10, (lvl, age) -> {
			double r = 0.6 + (radius - 0.6) * age / 9.0;
			RuneFx.ring(lvl, ParticleTypes.GLOW, center.add(0, 0.3, 0), r, (int) (10 + r * 3), age * 0.2);
			return age < 9;
		});
		level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.5F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.GLOW_INK_SAC_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
	}

	// ================================================================== tick powers

	/** Rebond: a landing after a high fall bounces the wearer (not sneaking, not in water, not gliding). */
	static void bounceTick(ServerPlayer player, Worn worn) {
		if (!player.onGround()) {
			if (!player.isInWater() && !player.isFallFlying() && !player.getAbilities().flying) {
				worn.maxFall = Math.max(worn.maxFall, player.fallDistance);
				worn.airborne = true;
			} else {
				worn.maxFall = 0;
			}
			return;
		}
		if (!worn.airborne) {
			return;
		}
		double fall = worn.maxFall;
		worn.airborne = false;
		worn.maxFall = 0;
		RuneConfig.Bounce cfg = RuneModule.config().powers.bounce;
		if (fall >= cfg.minFall && !player.isShiftKeyDown() && ready(player, RunePower.BOUNCE, cfg.cooldownSeconds)) {
			bounce(player);
		}
	}

	static void bounce(ServerPlayer player) {
		RuneConfig.Bounce cfg = RuneModule.config().powers.bounce;
		ServerLevel level = player.level();
		if (cfg.hop > 0) {
			Vec3 known = player.getKnownMovement();
			player.setDeltaMovement(known.x, cfg.hop, known.z);
			player.connection.send(new ClientboundSetEntityMotionPacket(player));
		}
		player.resetFallDistance();
		Vec3 feet = player.position().add(0, 0.1, 0);
		RuneFx.ring(level, ParticleTypes.ITEM_SLIME, feet, 0.7, 12, 0);
		RuneFx.ring(level, ParticleTypes.CLOUD, feet, 0.9, 10, 0.3);
		RuneFx.burst(level, RuneFx.dust(0x8CE06B, 1.0F), feet.add(0, 0.2, 0), 8, 0.4, 0.0);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 0.9F, 1.2F);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.SLIME_BLOCK_FALL, SoundSource.PLAYERS, 0.7F, 1.0F);
	}

	/** Renard: the dropped items nearby fly to the wearer (not while sneaking; never what someone just threw away). */
	static int fox(ServerPlayer player) {
		if (player.isShiftKeyDown()) {
			return 0;
		}
		RuneConfig.Fox cfg = RuneModule.config().powers.fox;
		ServerLevel level = player.level();
		Vec3 center = player.position().add(0, 0.6, 0);
		double r2 = cfg.radius * cfg.radius;
		List<ItemEntity> items = new ArrayList<>(level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(cfg.radius),
				item -> item.isAlive() && !item.hasPickUpDelay() && item.distanceToSqr(center) <= r2 && pullable(item, player)));
		items.sort(Comparator.comparingDouble(item -> item.distanceToSqr(center)));
		int pulled = 0;
		for (ItemEntity item : items) {
			if (pulled >= cfg.maxItems) {
				break;
			}
			Vec3 to = center.subtract(item.position());
			double distance = to.length();
			if (distance < 0.7) {
				continue;
			}
			Vec3 velocity = to.scale(1 / distance).scale(Math.min(0.55, 0.18 + distance * 0.06));
			item.setDeltaMovement(velocity.x, velocity.y + 0.06, velocity.z);
			item.needsSync = true;
			RuneFx.dot(level, RuneFx.fade(0xFF8A2B, 0xFFE0B0, 0.8F), item.position().add(0, 0.25, 0));
			pulled++;
		}
		return pulled;
	}

	/** Not an item someone else threw (or the wearer threw a moment ago). */
	private static boolean pullable(ItemEntity item, ServerPlayer player) {
		Entity owner = item.getOwner();
		return owner == null || owner == player && item.getAge() >= 200;
	}

	/** Écho: sneaking still for a moment sends a sonar ping; light footstep echoes while walking. */
	static void echoTick(ServerPlayer player, Worn worn) {
		RuneConfig.Echo cfg = RuneModule.config().powers.echo;
		ServerLevel level = player.level();
		boolean moving = player.getKnownMovement().horizontalDistanceSqr() > 1.0E-4;
		if (!player.isShiftKeyDown() || !player.onGround() || moving) {
			worn.stillTicks = 0;
			worn.pinged = false;
			if (moving && player.onGround() && !player.isInvisible() && (worn.stepTicks += 5) >= 15) {
				worn.stepTicks = 0;
				Vec3 feet = player.position();
				level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, feet.x, feet.y + 0.05, feet.z, 1, 0.15, 0, 0.15, 0.0);
			}
			return;
		}
		if (worn.pinged) {
			return;
		}
		worn.stillTicks += 5;
		Vec3 feet = player.position();
		level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, feet.x, feet.y + 0.1, feet.z, 2, 0.3, 0.05, 0.3, 0.0);
		if (worn.stillTicks < Math.round(cfg.sneakSeconds * 20)) {
			return;
		}
		worn.pinged = true;
		if (!ready(player, RunePower.ECHO, cfg.cooldownSeconds)) {
			Msg.actionBar(player, "rune.cooldown", Lang.raw("rune.power.echo.name"), RuneItems.number(Math.ceil(remaining(player, RunePower.ECHO))));
			return;
		}
		ping(player);
	}

	/** The sonar ping: an expanding ring, the monsters within the radius glow. @return monsters revealed */
	static int ping(ServerPlayer player) {
		RuneConfig.Echo cfg = RuneModule.config().powers.echo;
		ServerLevel level = player.level();
		Vec3 center = player.position();
		List<LivingEntity> targets = hostilesAround(level, center, cfg.radius, 64, null);
		for (LivingEntity target : targets) {
			target.addEffect(new MobEffectInstance(MobEffects.GLOWING, cfg.seconds * 20, 0, false, false));
		}
		RuneFx.burst(level, ParticleTypes.SONIC_BOOM, center.add(0, 1.0, 0), 1, 0, 0);
		double radius = cfg.radius;
		RuneFx.play(level, 16, (lvl, age) -> {
			double r = 0.8 + (radius - 0.8) * age / 15.0;
			RuneFx.ring(lvl, ParticleTypes.SCULK_CHARGE_POP, center.add(0, 0.2, 0), r, (int) Math.min(64, 8 + r * 2.5), age * 0.15);
			if (age == 15) {
				for (LivingEntity target : targets) {
					if (target.isAlive()) {
						RuneFx.burst(lvl, ParticleTypes.SCULK_SOUL, target.position().add(0, target.getBbHeight() + 0.2, 0), 2, 0.1, 0.02);
					}
				}
			}
			return age < 15;
		});
		level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.5F, 1.8F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 1.0F, 1.0F);
		Msg.actionBar(player, "rune.echo.ping", targets.size());
		return targets.size();
	}

	// ================================================================== helpers

	/** Monsters ({@link Enemy} or ruin mobs) alive around {@code center}, nearest first. */
	static List<LivingEntity> hostilesAround(ServerLevel level, Vec3 center, double radius, int max, @Nullable Entity excluded) {
		AABB box = AABB.ofSize(center, radius * 2, radius * 2, radius * 2);
		double r2 = radius * radius;
		List<LivingEntity> found = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e != excluded && isHostile(e) && e.distanceToSqr(center) <= r2));
		found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
		return found.size() > max ? new ArrayList<>(found.subList(0, Math.max(0, max))) : found;
	}

	/** A live monster: the powers never affect players, villagers, animals or pets. */
	static boolean isHostile(Entity entity) {
		return (entity instanceof Enemy || RoomGuards.isRuinMob(entity)) && entity instanceof LivingEntity living && living.isAlive()
				&& !living.isSpectator();
	}

	/** Magic damage of the player (kill credit) that lands even right after another hit. */
	static boolean magic(LivingEntity target, ServerPlayer player, double amount) {
		if (amount <= 0 || !target.isAlive()) {
			return false;
		}
		target.damageCooldownTime = 0;
		ServerLevel level = player.level();
		return hurt(level, target, level.damageSources().indirectMagic(player, player), amount);
	}

	static boolean hurt(ServerLevel level, LivingEntity target, DamageSource source, double amount) {
		dealing++;
		try {
			return target.hurtServer(level, source, (float) amount);
		} finally {
			dealing--;
		}
	}

	/** Pushes a monster (velocity added and synchronised). */
	static void push(LivingEntity target, Vec3 impulse) {
		target.setDeltaMovement(target.getDeltaMovement().add(impulse));
		target.needsSync = true;
	}

	/** True (and the cooldown starts) when the power is ready for this player. */
	static boolean ready(ServerPlayer player, RunePower power, double seconds) {
		long now = Scheduler.ticks();
		EnumMap<RunePower, Long> map = COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new EnumMap<>(RunePower.class));
		Long until = map.get(power);
		if (until != null && now < until) {
			return false;
		}
		map.put(power, now + Math.round(seconds * 20));
		return true;
	}

	/** Seconds left before the power is ready again (0 when ready). */
	static double remaining(ServerPlayer player, RunePower power) {
		EnumMap<RunePower, Long> map = COOLDOWNS.get(player.getUUID());
		Long until = map == null ? null : map.get(power);
		return until == null ? 0 : Math.max(0, (until - Scheduler.ticks()) / 20.0);
	}

	/** Forgets a player's cooldowns (tests, admin). */
	static void resetCooldowns(ServerPlayer player) {
		COOLDOWNS.remove(player.getUUID());
	}
}
