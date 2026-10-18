package dev.guildy.unique;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Scheduler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Players in the air because of a power: the jaguar pounce (Lance du Jaguar céleste) and the solar leap (Masse du Zénith).
 * The player's own client moves him (vanilla clients): the server only watches him leave the ground, then land (or stay on
 * the ground a short while: blocked leap, a lagging client), and calls the power's landing. No fall damage while a leap
 * lasts (the power is part of the move). Forgotten at logout, death, dimension change or after a maximum duration.
 */
final class Leaps {
	/** A leap that never left the ground lands after this delay (a wall, a low ceiling, a lagging client). */
	static final int GROUND_GRACE_TICKS = 10;

	enum Kind {
		/** Jaguar pounce: claws at the landing, or wherever the player is after 3 s. */
		POUNCE(60, true),
		/** Solar leap: the sun bursts at the landing (or at the first smash attack); nothing after 8 s in the air. */
		SOLAR(160, false);

		final int maxTicks;
		final boolean landsOnTimeout;

		Kind(int maxTicks, boolean landsOnTimeout) {
			this.maxTicks = maxTicks;
			this.landsOnTimeout = landsOnTimeout;
		}
	}

	static final class Leap {
		final Kind kind;
		final ServerLevel level;
		final long start;
		boolean airborne;
		/** The landing effect already happened (solar burst on a smash attack): the leap only protects from the fall. */
		boolean spent;
		Vec3 last;

		Leap(Kind kind, ServerLevel level, long start, Vec3 at) {
			this.kind = kind;
			this.level = level;
			this.start = start;
			this.last = at;
		}

		int age() {
			return (int) (Scheduler.ticks() - start);
		}
	}

	private static final Map<UUID, Leap> LEAPS = new HashMap<>();

	private Leaps() {
	}

	static void start(ServerPlayer player, Kind kind) {
		player.resetFallDistance();
		LEAPS.put(player.getUUID(), new Leap(kind, player.level(), Scheduler.ticks(), player.position()));
	}

	static @Nullable Leap get(ServerPlayer player) {
		return LEAPS.get(player.getUUID());
	}

	static boolean active(ServerPlayer player, Kind kind) {
		Leap leap = LEAPS.get(player.getUUID());
		return leap != null && leap.kind == kind;
	}

	/** The landing effect happened early (solar burst on a smash attack): the leap still protects from the fall until landing. */
	static void spend(ServerPlayer player) {
		Leap leap = LEAPS.get(player.getUUID());
		if (leap != null) {
			leap.spent = true;
		}
	}

	/** A player in a leap takes no fall damage. */
	static boolean cancelsFall(LivingEntity entity, DamageSource source) {
		return entity instanceof ServerPlayer && !LEAPS.isEmpty() && LEAPS.containsKey(entity.getUUID()) && source.is(DamageTypeTags.IS_FALL);
	}

	/** Every server tick: trails, landings, timeouts. */
	static void tick() {
		if (LEAPS.isEmpty()) {
			return;
		}
		List<Map.Entry<UUID, Leap>> entries = new ArrayList<>(LEAPS.entrySet());
		for (Map.Entry<UUID, Leap> entry : entries) {
			Leap leap = entry.getValue();
			ServerPlayer player = GuildyServer.player(entry.getKey());
			if (player == null || !player.isAlive() || player.level() != leap.level || player.isSpectator()) {
				LEAPS.remove(entry.getKey());
				continue;
			}
			int age = leap.age();
			boolean grounded = player.onGround() || player.isInWater() || player.onClimbable() || player.isPassenger();
			if (!grounded) {
				leap.airborne = true;
			}
			Vec3 now = player.position();
			Vec3 motion = now.subtract(leap.last);
			leap.last = now;
			if (leap.airborne && grounded || !leap.airborne && age >= GROUND_GRACE_TICKS) {
				LEAPS.remove(entry.getKey());
				player.resetFallDistance();
				land(player, leap);
				continue;
			}
			if (age >= leap.kind.maxTicks) {
				LEAPS.remove(entry.getKey());
				if (leap.kind.landsOnTimeout) {
					land(player, leap);
				}
				continue;
			}
			if (!leap.spent) {
				switch (leap.kind) {
					case POUNCE -> JaguarPowers.trail(player, age, motion);
					case SOLAR -> SolarPowers.trail(player, age, motion);
				}
			}
		}
	}

	private static void land(ServerPlayer player, Leap leap) {
		if (leap.spent) {
			return;
		}
		switch (leap.kind) {
			case POUNCE -> JaguarPowers.land(player);
			case SOLAR -> SolarPowers.burst(player, player.position());
		}
	}

	static void forget(UUID player) {
		LEAPS.remove(player);
	}

	static void clear() {
		LEAPS.clear();
	}
}
