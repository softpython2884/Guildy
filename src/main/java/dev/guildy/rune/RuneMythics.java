package dev.guildy.rune;

import dev.guildy.Guildy;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The two mythic runes (netherite chestplates only):
 * <ul>
 * <li>Rune du Phénix: a lethal blow (not /kill nor the void) raises the wearer from the ashes instead (health given back,
 * harmful effects removed, fire resistance and regeneration), in a burst of flames that knocks back, burns and ignites the
 * monsters around, with flaming wings; then it recharges for a long time (game time stored on the chestplate: it survives
 * restarts and follows the piece). Before a totem of undying.</li>
 * <li>Rune du Dragon: the chestplate glides like an elytra (glider component added at the inscription, it wears while
 * gliding), with spectral wings while gliding; sneak + jump breathes dragon fire in a cone (monsters only).</li>
 * </ul>
 */
final class RuneMythics {
	private static final ResourceKey<DamageType> BRASIER = ResourceKey.create(Registries.DAMAGE_TYPE,
			Identifier.fromNamespaceAndPath(Guildy.MOD_ID, "brasier"));

	private RuneMythics() {
	}

	static long gameTime(ServerLevel level) {
		return level.getServer().overworld().getGameTime();
	}

	// ================================================================== Phénix

	/** ALLOW_DEATH of a player: true when the Phoenix rune raised the wearer (death cancelled). */
	static boolean rise(ServerPlayer player, DamageSource source) {
		RuneConfig config = RuneModule.config();
		if (!config.enabled || player.isSpectator() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (RuneItems.powerOf(chest) != RunePower.PHOENIX) {
			return false;
		}
		ServerLevel level = player.level();
		long now = gameTime(level);
		if (RuneItems.readyAt(chest) > now) {
			return false;
		}
		RuneConfig.Phoenix cfg = config.powers.phoenix;
		player.setHealth((float) Math.max(1.0, player.getMaxHealth() * cfg.healthRatio));
		player.clearFire();
		for (MobEffectInstance effect : List.copyOf(player.getActiveEffects())) {
			if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				player.removeEffect(effect.getEffect());
			}
		}
		if (cfg.fireResistanceSeconds > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, cfg.fireResistanceSeconds * 20, 0));
		}
		if (cfg.regenSeconds > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, cfg.regenSeconds * 20, cfg.regenLevel - 1));
		}
		player.resetFallDistance();
		RuneItems.setReadyAt(chest, now + cfg.cooldownMinutes * 1200L);
		rebirth(player);
		Msg.title(player, Lang.get("rune.phoenix.title"), Lang.get("rune.phoenix.subtitle"), 5, 50, 20);
		Msg.send(player, "rune.phoenix.rise", cfg.cooldownMinutes);
		return true;
	}

	/** Ashes, a rising double helix of flames, a nova of fire and flaming wings. */
	private static void rebirth(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 origin = player.position();
		UUID owner = player.getUUID();
		double radius = RuneModule.config().powers.phoenix.radius;
		RuneFx.burst(level, ParticleTypes.LARGE_SMOKE, origin.add(0, 1.0, 0), 30, 0.5, 0.05);
		RuneFx.burst(level, ParticleTypes.WHITE_ASH, origin.add(0, 1.0, 0), 60, 0.8, 0.02);
		RuneFx.burst(level, RuneFx.flash(RuneFx.FLAME), origin.add(0, 1.0, 0), 1, 0, 0);
		level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.BLAZE_DEATH, SoundSource.PLAYERS, 1.0F, 1.6F);
		level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
		RuneFx.play(level, 40, (lvl, age) -> {
			ServerPlayer wearer = GuildyServer.player(owner);
			boolean here = wearer != null && wearer.level() == lvl;
			Vec3 at = here ? wearer.position() : origin;
			if (age < 12) {
				for (int strand = 0; strand < 2; strand++) {
					double angle = age * 0.6 + Math.PI * strand;
					double y = age / 12.0 * 2.4;
					RuneFx.dot(lvl, ParticleTypes.FLAME, at.add(Math.cos(angle) * 0.7, y, Math.sin(angle) * 0.7));
					RuneFx.dot(lvl, RuneFx.fade(RuneFx.GOLD, RuneFx.EMBER, 1.2F), at.add(Math.cos(angle) * 0.55, y + 0.1, Math.sin(angle) * 0.55));
				}
			}
			if (age == 6) {
				nova(lvl, owner, origin);
				lvl.playSound(null, origin.x, origin.y, origin.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2F, 0.8F);
				lvl.playSound(null, origin.x, origin.y, origin.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0F, 0.7F);
			}
			if (age >= 6 && age <= 14) {
				double r = 0.5 + (radius - 0.5) * (age - 6) / 8.0;
				RuneFx.ring(lvl, ParticleTypes.FLAME, origin.add(0, 0.2, 0), r, (int) Math.min(72, 10 + r * 5), age * 0.3);
				RuneFx.ring(lvl, RuneFx.fade(RuneFx.FLAME, RuneFx.ASH, 1.4F), origin.add(0, 0.5, 0), r * 0.85, (int) Math.min(48, 8 + r * 3), -age * 0.3);
			}
			if (here && age >= 4 && age < 36 && age % 2 == 0) {
				wings(lvl, wearer, age, RuneFx.fade(RuneFx.GOLD, RuneFx.EMBER, 1.1F), ParticleTypes.FLAME);
			}
			if (age % 4 == 0) {
				RuneFx.burst(lvl, ParticleTypes.WHITE_ASH, at.add(0, 2.6, 0), 6, 0.9, 0.01);
			}
			if (age == 30) {
				lvl.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.4F);
			}
			return age < 39;
		});
	}

	/** The nova: monsters around are knocked back, burnt (guildy:brasier, credited to the wearer) and set on fire. */
	static int nova(ServerLevel level, UUID owner, Vec3 center) {
		ServerPlayer wearer = GuildyServer.player(owner);
		if (wearer == null) {
			return 0;
		}
		RuneConfig.Phoenix cfg = RuneModule.config().powers.phoenix;
		Holder<DamageType> type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(BRASIER);
		List<LivingEntity> targets = RunePowers.hostilesAround(level, center, cfg.radius, 32, null);
		for (LivingEntity target : targets) {
			double distance = Math.sqrt(target.distanceToSqr(center));
			double falloff = Math.max(0.35, 1.0 - distance / (cfg.radius + 1));
			Vec3 away = RuneFx.horizontal(target.position().subtract(center)).scale(cfg.push * falloff);
			RunePowers.push(target, new Vec3(away.x, 0.35 + 0.2 * falloff, away.z));
			if (cfg.fireSeconds > 0 && !target.fireImmune()) {
				target.igniteForSeconds(cfg.fireSeconds);
			}
			RunePowers.hurt(level, target, new DamageSource(type, wearer), cfg.damage);
			RuneFx.burst(level, ParticleTypes.FLAME, target.position().add(0, target.getBbHeight() * 0.5, 0), 12, 0.3, 0.05);
		}
		return targets.size();
	}

	/** While the Phoenix rune is worn (every second): it smoulders when ready, and tells the wearer once it is ready again. */
	static void phoenixNotice(ServerPlayer player) {
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (RuneItems.powerOf(chest) != RunePower.PHOENIX) {
			return;
		}
		ServerLevel level = player.level();
		long now = gameTime(level);
		Vec3 heart = player.position().add(0, 1.1, 0);
		if (RuneItems.takeReadyNotice(chest, now)) {
			Msg.actionBar(player, "rune.phoenix.ready");
			level.playSound(null, heart.x, heart.y, heart.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.5F, 1.4F);
			RuneFx.burst(level, ParticleTypes.FLAME, heart, 12, 0.3, 0.03);
			return;
		}
		if (!player.isInvisible()) {
			boolean ready = RuneItems.readyAt(chest) <= now;
			level.sendParticles(ready ? ParticleTypes.SMALL_FLAME : ParticleTypes.SMOKE, heart.x, heart.y, heart.z, 1, 0.2, 0.15, 0.2, 0.005);
		}
	}

	// ================================================================== Dragon

	/** Every tick while the Dragon rune is worn: wings while gliding, sneak + jump breathes fire. */
	static void dragonTick(ServerPlayer player, RunePowers.Worn worn, long now) {
		if (player.isFallFlying() && now % 2 == 0) {
			wings(player.level(), player, (int) now, RuneFx.fade(RuneFx.DRAGON, RuneFx.DRAGON_DARK, 1.1F), ParticleTypes.REVERSE_PORTAL);
			if (now % 6 == 0) {
				Vec3 tail = player.position().add(0, 0.3, 0).subtract(player.getLookAngle().scale(0.9));
				RuneFx.dot(player.level(), breathParticle(), tail);
			}
		}
		Input input = player.getLastClientInput();
		if (!input.jump() || !input.shift()) {
			worn.breathHeld = false;
			return;
		}
		if (worn.breathHeld) {
			return;
		}
		worn.breathHeld = true;
		RuneConfig.Dragon cfg = RuneModule.config().powers.dragon;
		if (!RunePowers.ready(player, RunePower.DRAGON, cfg.breathCooldownSeconds)) {
			Msg.actionBar(player, "rune.cooldown", Lang.raw("rune.power.dragon.breath"),
					RuneItems.number(Math.ceil(RunePowers.remaining(player, RunePower.DRAGON))));
			return;
		}
		breath(player);
	}

	/** The breath: a cone of dragon fire; the monsters in it (in sight) are burnt, set on fire and pushed. @return monsters hit */
	static int breath(ServerPlayer player) {
		RuneConfig.Dragon cfg = RuneModule.config().powers.dragon;
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle().normalize();
		double cos = Math.cos(Math.toRadians(cfg.breathAngle));
		List<LivingEntity> targets = new ArrayList<>();
		for (LivingEntity target : RunePowers.hostilesAround(level, eye, cfg.breathRange, 32, null)) {
			Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(eye);
			double distance = to.length();
			if (distance < 1.0E-3 || to.scale(1 / distance).dot(look) < cos || !player.hasLineOfSight(target)) {
				continue;
			}
			targets.add(target);
		}
		for (LivingEntity target : targets) {
			RunePowers.magic(target, player, cfg.breathDamage);
			if (cfg.breathFireSeconds > 0 && !target.fireImmune()) {
				target.igniteForSeconds(cfg.breathFireSeconds);
			}
			RunePowers.push(target, look.scale(0.6).add(0, 0.2, 0));
			RuneFx.burst(level, breathParticle(), target.position().add(0, target.getBbHeight() * 0.5, 0), 10, 0.3, 0.02);
		}
		level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.8F, 1.3F);
		level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.PLAYERS, 1.0F, 0.9F);
		UUID owner = player.getUUID();
		double range = cfg.breathRange;
		double spread = Math.tan(Math.toRadians(cfg.breathAngle)) * 0.8;
		RuneFx.play(level, 12, (lvl, age) -> {
			ServerPlayer wearer = GuildyServer.player(owner);
			if (wearer == null || wearer.level() != lvl) {
				return false;
			}
			Vec3 from = wearer.getEyePosition().subtract(0, 0.2, 0);
			Vec3 dir = wearer.getLookAngle().normalize();
			Vec3 side = RuneFx.horizontal(new Vec3(-dir.z, 0, dir.x));
			Vec3 up = side.cross(dir).normalize();
			for (int i = 0; i < 6; i++) {
				double a = lvl.getRandom().nextDouble() * Math.PI * 2;
				double r = lvl.getRandom().nextDouble() * spread;
				Vec3 velocity = dir.add(side.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r)).normalize().scale(0.55 * range / 9.0);
				RuneFx.move(lvl, i % 2 == 0 ? breathParticle() : RuneFx.dust(RuneFx.DRAGON, 1.4F), from.add(dir.scale(0.6)), velocity);
				if (i % 3 == 0) {
					RuneFx.move(lvl, ParticleTypes.FLAME, from.add(dir.scale(0.6)), velocity.scale(1.2));
				}
			}
			return age < 9;
		});
		return targets.size();
	}

	private static ParticleOptions breathParticle() {
		return PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F);
	}

	// ================================================================== shared

	/**
	 * Two wings of particles spreading from the wearer's back (standing: behind the shoulders; gliding: along the
	 * horizontal body), flapping with {@code age}.
	 */
	static void wings(ServerLevel level, ServerPlayer wearer, int age, ParticleOptions membrane, ParticleOptions tips) {
		boolean gliding = wearer.isFallFlying();
		Vec3 look = wearer.getLookAngle().normalize();
		Vec3 forward = gliding ? look : RuneFx.horizontal(look);
		Vec3 side = RuneFx.horizontal(new Vec3(-forward.z, 0, forward.x));
		Vec3 up = gliding ? side.cross(forward).normalize() : new Vec3(0, 1, 0);
		Vec3 base = gliding ? wearer.position().add(0, wearer.getBbHeight() * 0.5, 0)
				: wearer.position().add(0, 1.35, 0).subtract(forward.scale(0.3));
		double flap = Math.sin(age * 0.4);
		for (int s = -1; s <= 1; s += 2) {
			for (int i = 1; i <= 7; i++) {
				double d = i * 0.3;
				Vec3 pos = base.add(side.scale(s * d)).add(up.scale(0.07 * i + flap * 0.09 * i)).subtract(forward.scale(0.05 * i));
				RuneFx.dot(level, i == 7 ? tips : membrane, pos);
				if (i >= 3 && i <= 5) {
					RuneFx.dot(level, membrane, pos.subtract(up.scale(0.25)).subtract(forward.scale(0.1)));
				}
			}
		}
	}
}
