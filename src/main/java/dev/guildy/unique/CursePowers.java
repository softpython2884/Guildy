package dev.guildy.unique;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Scheduler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Lame du Pacte, "Sceau du pacte": each hit seals the monster (purple runes orbit above its head, one per seal). The last
 * seal opens an inverted pentagram under it, drawn stroke by stroke; when it closes, the pact bursts: magic damage of the
 * wielder, withering, and the curse spreads one seal to each monster nearby (a pact cannot start another one by itself:
 * only a blow of the blade does). Runtime state only (bounded), forgotten when the monster leaves or after a while.
 */
final class CursePowers {
	static final int VOID = 0x22062E;
	static final int PURPLE = 0x9B2FE6;
	static final int CRIMSON = 0xC8163E;
	static final int PALE = 0xF0C2FF;
	/** Ticks of the pentagram before the burst. */
	static final int TELEGRAPH_TICKS = 12;
	private static final int MAX_SEALED = 256;

	private static final class Seal {
		final ServerLevel level;
		int count;
		long lastSeal;

		Seal(ServerLevel level) {
			this.level = level;
		}
	}

	private static final Map<UUID, Seal> SEALS = new HashMap<>();

	private CursePowers() {
	}

	/** Seals on a monster (0 when none). */
	static int seals(Entity entity) {
		Seal seal = SEALS.get(entity.getUUID());
		return seal == null ? 0 : seal.count;
	}

	/** A blow of the pact blade on a living monster: one more seal, and the pact when the seals are complete. */
	static void hit(ServerPlayer player, LivingEntity victim) {
		if (!victim.isAlive() || !PowerUtil.isHostile(victim)) {
			return;
		}
		UniqueConfig.PactSeal cfg = PowerUtil.cfg().pactSeal;
		Seal seal = add(victim, 1);
		if (seal == null) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 head = victim.position().add(0, victim.getBbHeight() + 0.3, 0);
		UniqueFx.burst(level, UniqueFx.dust(PURPLE, 0.9F), head, 6, 0.2, 0.0);
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6F,
				0.5F + 0.2F * seal.count);
		if (seal.count >= cfg.seals && PowerUtil.ready(player, UniquePower.PACT_SEAL, cfg.cooldownSeconds)) {
			SEALS.remove(victim.getUUID());
			pact(player, victim);
		}
	}

	private static @Nullable Seal add(LivingEntity victim, int n) {
		Seal seal = SEALS.get(victim.getUUID());
		if (seal == null) {
			if (SEALS.size() >= MAX_SEALED) {
				prune(Scheduler.ticks());
				if (SEALS.size() >= MAX_SEALED) {
					return null;
				}
			}
			seal = new Seal((ServerLevel) victim.level());
			SEALS.put(victim.getUUID(), seal);
		}
		seal.count = Math.min(PowerUtil.cfg().pactSeal.seals, seal.count + n);
		seal.lastSeal = Scheduler.ticks();
		return seal;
	}

	/** The pentagram opens under the monster, then bursts (the damage is the wielder's, even if he moved away). */
	private static void pact(ServerPlayer player, LivingEntity target) {
		UniqueConfig.PactSeal cfg = PowerUtil.cfg().pactSeal;
		ServerLevel level = player.level();
		UUID owner = player.getUUID();
		Vec3[] at = {target.position()};
		double spin = target.getRandom().nextDouble() * Math.PI * 2;
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 1.0F, 0.6F);
		UniqueFx.play(level, TELEGRAPH_TICKS + 5, (lvl, age) -> {
			if (target.isAlive()) {
				at[0] = target.position();
			}
			Vec3 floor = at[0].add(0, 0.1, 0);
			if (age < TELEGRAPH_TICKS) {
				double progress = (age + 1) / (double) TELEGRAPH_TICKS;
				double turn = spin + age * 0.05;
				UniqueFx.arc(lvl, UniqueFx.dust(PURPLE, 1.0F), floor, 1.75, turn, turn + Math.PI * 2 * Math.min(1, progress * 1.6), 22);
				// Inverted: the lone point away from the wielder's side, the two horns facing up the screen.
				UniqueFx.star(lvl, UniqueFx.dust(CRIMSON, 0.9F), floor, 1.6, 5, 2, turn + Math.PI / 2, progress, 3);
				if (age % 2 == 0) {
					double a = turn + age;
					UniqueFx.move(lvl, ParticleTypes.WITCH, floor.add(Math.cos(a) * 1.7, 0, Math.sin(a) * 1.7), new Vec3(0, 0.12, 0));
				}
				return true;
			}
			if (age == TELEGRAPH_TICKS) {
				burst(lvl, owner, target, floor, cfg);
				return true;
			}
			// The curse's shockwave, out to the spread radius.
			int k = age - TELEGRAPH_TICKS;
			double r = 1 + (Math.max(1, cfg.spreadRadius) - 1) * k / 4.0;
			UniqueFx.ring(lvl, UniqueFx.fade(PURPLE, VOID, 1.2F), floor, r, (int) (10 + r * 5), k * 0.4);
			return k < 4;
		});
	}

	private static void burst(ServerLevel level, UUID ownerId, LivingEntity target, Vec3 floor, UniqueConfig.PactSeal cfg) {
		ServerPlayer owner = GuildyServer.player(ownerId);
		Vec3 heart = floor.add(0, target.getBbHeight() * 0.5, 0);
		if (owner != null && owner.level() == level && target.isAlive()) {
			PowerUtil.magic(target, owner, cfg.damage);
			if (cfg.witherSeconds > 0 && target.isAlive()) {
				target.addEffect(new MobEffectInstance(MobEffects.WITHER, cfg.witherSeconds * 20, 1));
			}
		}
		UniqueFx.dot(level, UniqueFx.flash(PURPLE), heart);
		UniqueFx.burst(level, ParticleTypes.REVERSE_PORTAL, heart, 40, 0.4, 0.2);
		UniqueFx.burst(level, ParticleTypes.SQUID_INK, heart, 12, 0.3, 0.05);
		UniqueFx.burst(level, UniqueFx.fade(CRIMSON, VOID, 1.4F), heart, 24, 0.6, 0.0);
		UniqueFx.star(level, ParticleTypes.SOUL_FIRE_FLAME, floor, 1.6, 5, 2, 0, 1, 2);
		if (cfg.spreadRadius > 0) {
			for (LivingEntity next : PowerUtil.hostilesAround(level, floor, cfg.spreadRadius, 12, target)) {
				add(next, 1);
				UniqueFx.line(level, UniqueFx.dust(PALE, 0.7F), heart, next.position().add(0, next.getBbHeight() * 0.6, 0), 4);
			}
		}
		level.playSound(null, floor.x, floor.y, floor.z, SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.8F, 0.6F);
		level.playSound(null, floor.x, floor.y, floor.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE, SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	/** Every server tick (draws every 4 ticks): the runes over the sealed monsters; expired seals dropped. */
	static void tick() {
		if (SEALS.isEmpty()) {
			return;
		}
		long now = Scheduler.ticks();
		if (now % 4 != 0) {
			return;
		}
		int max = PowerUtil.cfg().pactSeal.seals;
		long expiry = PowerUtil.cfg().pactSeal.sealSeconds * 20L;
		Iterator<Map.Entry<UUID, Seal>> it = SEALS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Seal> entry = it.next();
			Seal seal = entry.getValue();
			Entity entity = seal.level.getEntity(entry.getKey());
			if (!(entity instanceof LivingEntity living) || !living.isAlive() || now - seal.lastSeal > expiry) {
				it.remove();
				continue;
			}
			Vec3 crown = living.position().add(0, living.getBbHeight() + 0.35, 0);
			double phase = now * 0.25;
			for (int i = 0; i < seal.count; i++) {
				double angle = phase + Math.PI * 2 * i / Math.max(1, seal.count);
				UniqueFx.dot(seal.level, UniqueFx.dust(seal.count >= max ? CRIMSON : PURPLE, 0.8F),
						crown.add(Math.cos(angle) * 0.45, 0, Math.sin(angle) * 0.45));
			}
			if (seal.count >= max) {
				UniqueFx.dot(seal.level, ParticleTypes.WITCH, crown.add(0, 0.2, 0));
			}
		}
	}

	private static void prune(long now) {
		long expiry = PowerUtil.cfg().pactSeal.sealSeconds * 20L;
		SEALS.values().removeIf(seal -> now - seal.lastSeal > expiry);
	}

	static void clear() {
		SEALS.clear();
	}
}
