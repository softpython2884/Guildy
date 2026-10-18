package dev.guildy.unique;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Scheduler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Powers of the worn and held defences, triggered when the wearer takes a blow (AFTER_DAMAGE of a player):
 * <ul>
 * <li>Couronne du Roi de givre, "Couronne de givre": the monster that struck is caught in a prison of ice spikes (slowness,
 * frozen, cold damage), a wave of cold slows the monsters around; the wearer never freezes (powder snow);</li>
 * <li>Égide des Âmes, "Rempart des âmes": a monster's blow blocked by the shield goes back to its author as souls (magic
 * damage of the wielder) and its soul comes back to heal the wielder;</li>
 * <li>Plastron du Serpent à plumes, "Souffle de jade": under a health threshold, a jade gale repels and poisons the
 * monsters around, the wearer regenerates and floats down like a feather.</li>
 * </ul>
 * Monsters only (the attacker must be a monster), passive cooldowns per player.
 */
final class DefensePowers {
	static final int ICE = 0xD6F6FF;
	static final int FROST = 0x7CC6EE;
	static final int DEEP = 0x2E6FB0;
	static final int SOUL = 0x5BE3F2;
	static final int SOUL_DARK = 0x0E5B6E;
	static final int JADE = 0x34C27A;
	static final int JADE_DARK = 0x136B44;
	static final int JADE_PALE = 0xC8F5D8;

	private DefensePowers() {
	}

	/** AFTER_DAMAGE of a player (not killed): {@code base} = damage before the shield, {@code blocked} = a shield took part of it. */
	static void onDamaged(ServerPlayer player, DamageSource source, float base, float taken, boolean blocked) {
		if (player.isSpectator() || !player.isAlive()) {
			return;
		}
		LivingEntity attacker = source.getEntity() instanceof LivingEntity living && living != player && PowerUtil.isHostile(living) ? living : null;
		if (blocked && attacker != null && aegis(player)) {
			soulAegis(player, attacker, base);
		}
		if (attacker != null && UniqueItems.powerOf(player.getItemBySlot(EquipmentSlot.HEAD)) == UniquePower.FROST_CROWN) {
			frostCrown(player, attacker, source.getDirectEntity() == attacker);
		}
		if (taken > 0 && UniqueItems.powerOf(player.getItemBySlot(EquipmentSlot.CHEST)) == UniquePower.JADE_GALE) {
			jadeGale(player);
		}
	}

	/** The shield that blocked is the Égide (in use, else held: an axe may have just disabled it). */
	private static boolean aegis(ServerPlayer player) {
		if (isAegis(player.getUseItem())) {
			return true;
		}
		for (InteractionHand hand : InteractionHand.values()) {
			if (isAegis(player.getItemInHand(hand))) {
				return true;
			}
		}
		return false;
	}

	private static boolean isAegis(ItemStack stack) {
		return !stack.isEmpty() && stack.has(DataComponents.BLOCKS_ATTACKS) && UniqueItems.powerOf(stack) == UniquePower.SOUL_AEGIS;
	}

	// ================================================================== Couronne du Roi de givre

	static void frostCrown(ServerPlayer player, LivingEntity attacker, boolean melee) {
		UniqueConfig.FrostCrown cfg = PowerUtil.cfg().frostCrown;
		if (!attacker.isAlive() || attacker.distanceToSqr(player) > cfg.range * cfg.range
				|| !PowerUtil.ready(player, UniquePower.FROST_CROWN, cfg.cooldownSeconds)) {
			return;
		}
		ServerLevel level = player.level();
		attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, cfg.freezeSeconds * 20, cfg.slowLevel - 1));
		if (attacker.canFreeze()) {
			attacker.setTicksFrozen(Math.max(attacker.getTicksFrozen(), attacker.getTicksRequiredToFreeze() + cfg.freezeSeconds * 40));
		}
		PowerUtil.damage(attacker, PowerUtil.GIVRE, player, cfg.damage);
		for (LivingEntity other : PowerUtil.hostilesAround(level, player.position(), cfg.chillRadius, 12, attacker)) {
			other.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0));
			UniqueFx.burst(level, ParticleTypes.SNOWFLAKE, other.position().add(0, other.getBbHeight() * 0.6, 0), 4, 0.25, 0.01);
		}
		// The crown flares, a wave of cold leaves the wearer.
		Vec3 crown = player.position().add(0, player.getBbHeight() + 0.25, 0);
		UniqueFx.burst(level, ParticleTypes.END_ROD, crown, 5, 0.2, 0.02);
		UniqueFx.ring(level, UniqueFx.fade(ICE, FROST, 1.0F), crown.subtract(0, 0.05, 0), 0.4, 8, 0);
		Vec3 feet = player.position().add(0, 0.2, 0);
		double chill = Math.max(1, cfg.chillRadius);
		UniqueFx.play(level, 5, (lvl, age) -> {
			double r = 0.6 + (chill - 0.6) * age / 4.0;
			UniqueFx.ring(lvl, ParticleTypes.SNOWFLAKE, feet, r, (int) (8 + r * 5), age * 0.5);
			return age < 4;
		});
		if (!melee) {
			// A ranged attacker: shards of ice fly back to it.
			Vec3 from = player.getEyePosition();
			Vec3 to = attacker.position().add(0, attacker.getBbHeight() * 0.6, 0);
			int ticks = (int) Math.max(3, Math.min(20, from.distanceTo(to) / 1.5));
			for (int i = 0; i < 5; i++) {
				UniqueFx.dot(level, UniqueFx.trail(to, i % 2 == 0 ? ICE : FROST, ticks), from.add((i - 2) * 0.12, (i % 3 - 1) * 0.1, 0));
			}
		}
		icePrison(level, attacker);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.6F);
	}

	/** Six spikes of ice rising around the monster, then shattering. */
	private static void icePrison(ServerLevel level, LivingEntity target) {
		double radius = Math.max(0.5, target.getBbWidth() * 0.8);
		double height = Math.max(1.0, target.getBbHeight() * 0.9);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0F, 0.8F);
		UniqueFx.play(level, 9, (lvl, age) -> {
			Vec3 base = target.position();
			if (age < 6) {
				double h = height * (age + 1) / 6.0;
				for (int spike = 0; spike < 6; spike++) {
					double angle = Math.PI * 2 * spike / 6 + 0.3;
					Vec3 foot = base.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
					Vec3 tip = base.add(Math.cos(angle) * radius * 0.35, h, Math.sin(angle) * radius * 0.35);
					UniqueFx.line(lvl, UniqueFx.fade(ICE, DEEP, 0.8F), foot, tip, 4);
				}
				return true;
			}
			if (age == 6) {
				Vec3 heart = base.add(0, height * 0.5, 0);
				UniqueFx.burst(lvl, ParticleTypes.ITEM_SNOWBALL, heart, 24, radius * 0.6, 0.15);
				UniqueFx.burst(lvl, ParticleTypes.SNOWFLAKE, heart, 16, radius * 0.6, 0.05);
				lvl.playSound(null, base.x, base.y, base.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 1.3F);
			}
			return age < 8;
		});
	}

	// ================================================================== Égide des Âmes

	static void soulAegis(ServerPlayer player, LivingEntity attacker, float blockedDamage) {
		UniqueConfig.SoulAegis cfg = PowerUtil.cfg().soulAegis;
		if (!attacker.isAlive() || attacker.distanceToSqr(player) > cfg.range * cfg.range
				|| !PowerUtil.ready(player, UniquePower.SOUL_AEGIS, cfg.cooldownSeconds)) {
			return;
		}
		ServerLevel level = player.level();
		double amount = Math.max(cfg.reflectMin, Math.min(cfg.reflectMax, blockedDamage * cfg.reflectRatio));
		Vec3 shield = player.getEyePosition().add(PowerUtil.horizontal(player.getLookAngle()).scale(0.7)).subtract(0, 0.4, 0);
		Vec3 target = attacker.position().add(0, attacker.getBbHeight() * 0.6, 0);
		int travel = (int) Math.max(2, Math.min(12, shield.distanceTo(target) / 1.2));
		// A curved wall of soul fire in front of the shield, then the souls fly to the attacker.
		Vec3 facing = PowerUtil.horizontal(player.getLookAngle());
		double yaw = Math.atan2(facing.z, facing.x);
		for (int row = 0; row < 3; row++) {
			UniqueFx.arc(level, ParticleTypes.SOUL_FIRE_FLAME, player.position().add(0, 0.5 + row * 0.5, 0), 1.0, yaw - 0.8, yaw + 0.8, 7);
		}
		for (int i = 0; i < 6; i++) {
			UniqueFx.dot(level, UniqueFx.trail(target, i % 2 == 0 ? SOUL : 0xBFF8FF, travel), shield.add((i - 2.5) * 0.1, (i % 3 - 1) * 0.12, 0));
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.2F, 0.8F);
		java.util.UUID owner = player.getUUID();
		UniqueFx.play(level, travel + 12, (lvl, age) -> {
			if (age == travel) {
				// The blow lands on its author, its soul is torn out.
				ServerPlayer wielder = GuildyServer.player(owner);
				if (wielder != null && wielder.level() == lvl && attacker.isAlive()) {
					PowerUtil.magic(attacker, wielder, amount);
				}
				Vec3 at = attacker.position().add(0, attacker.getBbHeight() * 0.6, 0);
				UniqueFx.burst(lvl, ParticleTypes.SOUL_FIRE_FLAME, at, 18, 0.3, 0.06);
				UniqueFx.burst(lvl, ParticleTypes.SCULK_SOUL, at, 6, 0.3, 0.04);
				lvl.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 1.4F);
				if (wielder != null) {
					Vec3 chest = wielder.position().add(0, 1.1, 0);
					int back = (int) Math.max(3, Math.min(16, at.distanceTo(chest) / 1.0));
					for (int i = 0; i < 5; i++) {
						UniqueFx.dot(lvl, UniqueFx.trail(chest, SOUL_DARK, back), at.add((i - 2) * 0.1, i % 2 * 0.15, 0));
					}
				}
				return true;
			}
			if (age == travel + 10) {
				ServerPlayer wielder = GuildyServer.player(owner);
				if (wielder != null && wielder.isAlive() && cfg.heal > 0) {
					wielder.heal((float) cfg.heal);
					UniqueFx.burst(lvl, ParticleTypes.SOUL, wielder.position().add(0, 1, 0), 8, 0.4, 0.03);
					UniqueFx.ring(lvl, UniqueFx.fade(SOUL, SOUL_DARK, 1.0F), wielder.position().add(0, 0.2, 0), 0.8, 12, 0);
				}
				return false;
			}
			return true;
		});
	}

	// ================================================================== Plastron du Serpent à plumes

	static void jadeGale(ServerPlayer player) {
		UniqueConfig.JadeGale cfg = PowerUtil.cfg().jadeGale;
		if (player.getHealth() > player.getMaxHealth() * cfg.healthThreshold
				|| !PowerUtil.ready(player, UniquePower.JADE_GALE, cfg.cooldownSeconds)) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 center = player.position();
		if (cfg.regenSeconds > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, cfg.regenSeconds * 20, cfg.regenLevel - 1));
		}
		if (cfg.slowFallingSeconds > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, cfg.slowFallingSeconds * 20, 0));
		}
		List<LivingEntity> targets = PowerUtil.hostilesAround(level, center, cfg.radius, 24, null);
		for (LivingEntity target : targets) {
			double distance = Math.sqrt(target.distanceToSqr(center));
			double falloff = Math.max(0.35, 1.0 - distance / (cfg.radius + 1));
			Vec3 away = PowerUtil.horizontal(target.position().subtract(center)).scale(cfg.push * falloff);
			PowerUtil.push(target, new Vec3(away.x, 0.3 + 0.2 * falloff, away.z));
			if (cfg.poisonSeconds > 0) {
				target.addEffect(new MobEffectInstance(MobEffects.POISON, cfg.poisonSeconds * 20, cfg.poisonLevel - 1));
			}
			UniqueFx.burst(level, UniqueFx.dust(JADE, 1.0F), target.position().add(0, target.getBbHeight() * 0.5, 0), 6, 0.3, 0.0);
		}
		level.playSound(null, center.x, center.y, center.z, SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0F, 0.9F);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.8F, 1.5F);
		double radius = cfg.radius;
		java.util.UUID owner = player.getUUID();
		ColorParticleOption feather = ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, 0xFF000000 | JADE);
		UniqueFx.play(level, 24, (lvl, age) -> {
			ServerPlayer wearer = GuildyServer.player(owner);
			Vec3 at = wearer != null && wearer.level() == lvl ? wearer.position() : center;
			if (age == 0) {
				UniqueFx.burst(lvl, ParticleTypes.GUST, at.add(0, 1, 0), 1, 0, 0);
				UniqueFx.burst(lvl, ParticleTypes.SMALL_GUST, at.add(0, 0.6, 0), 8, radius / 3, 0.0);
			}
			if (age <= 6) {
				double r = 0.6 + (radius - 0.6) * age / 6.0;
				UniqueFx.ring(lvl, UniqueFx.fade(JADE_PALE, JADE, 1.2F), center.add(0, 0.3, 0), r, (int) (10 + r * 5), age * 0.35);
				UniqueFx.ring(lvl, ParticleTypes.HAPPY_VILLAGER, center.add(0, 0.5, 0), r * 0.8, 4, -age * 0.35);
			}
			// Three jade strands rising around the wearer, feathers drifting down.
			for (int strand = 0; strand < 3; strand++) {
				double angle = age * 0.45 + Math.PI * 2 * strand / 3;
				double y = (age % 12) / 12.0 * 2.4;
				UniqueFx.dot(lvl, UniqueFx.fade(JADE_PALE, JADE_DARK, 1.0F), at.add(Math.cos(angle) * 0.9, y, Math.sin(angle) * 0.9));
			}
			if (age % 3 == 0) {
				UniqueFx.burst(lvl, feather, at.add(0, 2.4, 0), 2, 0.8, 0.0);
			}
			return age < 23;
		});
	}

	// ================================================================== tick

	/** Every server tick: the crown keeps its wearer from freezing and sheds a few snowflakes. */
	static void tick() {
		boolean ambient = Scheduler.ticks() % 40 == 0;
		for (ServerPlayer player : List.copyOf(GuildyServer.players())) {
			if (UniqueItems.powerOf(player.getItemBySlot(EquipmentSlot.HEAD)) != UniquePower.FROST_CROWN) {
				continue;
			}
			if (player.getTicksFrozen() > 0) {
				player.setTicksFrozen(0);
			}
			if (ambient && !player.isSpectator() && !player.isInvisible()) {
				Vec3 crown = player.position().add(0, player.getBbHeight() + 0.3, 0);
				player.level().sendParticles(ParticleTypes.SNOWFLAKE, crown.x, crown.y, crown.z, 2, 0.25, 0.05, 0.25, 0.005);
			}
		}
	}
}
