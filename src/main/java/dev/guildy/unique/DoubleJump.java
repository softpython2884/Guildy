package dev.guildy.unique;

import dev.guildy.core.FlightGrants;
import dev.guildy.core.FlightOwnership;
import dev.guildy.core.FlightOwnership.Owner;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Scheduler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bottes de Zéphyr: double jump for vanilla clients. While the boots are worn (survival / adventure) the player may
 * "fly" (abilities.mayfly) when standing on the ground; a double tap on jump makes the client start flying, which the
 * server turns into an upward boost and takes the flight back until the next landing. Players allowed to fly never take
 * fall damage (vanilla rule), which is part of the power. The grant goes through {@link FlightGrants} (owner
 * {@link Owner#DOUBLE_JUMP}, marked with an entity tag taken back at login after a crash): while guild flight holds it,
 * the double jump steps aside (real flight, no boost, no revoke) and takes over again from the next landing.
 */
final class DoubleJump {
	private static final Map<UUID, Long> LAST_JUMP = new HashMap<>();

	private DoubleJump() {
	}

	/** Every server tick: grants, jumps, revokes. Cheap (one slot read per online player). */
	static void tick() {
		for (ServerPlayer player : List.copyOf(GuildyServer.players())) {
			boolean eligible = player.gameMode.isSurvival() && !player.isSpectator()
					&& UniqueItems.powerOf(player.getItemBySlot(EquipmentSlot.FEET)) == UniquePower.DOUBLE_JUMP;
			boolean managed = FlightGrants.holds(player, Owner.DOUBLE_JUMP);
			if (!eligible) {
				if (managed) {
					revoke(player);
				}
				continue;
			}
			if (FlightGrants.outranked(player, Owner.DOUBLE_JUMP)) {
				// Guild flight holds the grant: the player really flies; mayfly stays with the higher owner.
				if (managed) {
					FlightGrants.release(player, Owner.DOUBLE_JUMP);
				}
				continue;
			}
			Abilities abilities = player.getAbilities();
			if (managed && abilities.flying && FlightOwnership.flightIsBoost(FlightGrants.holders(player))) {
				// Double tap on jump: the client asked to fly (flight and mayfly are taken back until the next landing).
				FlightGrants.release(player, Owner.DOUBLE_JUMP);
				jump(player);
				continue;
			}
			if ((player.onGround() || player.isInWater() || player.onClimbable()) && (!managed || !abilities.mayfly)) {
				FlightGrants.grant(player, Owner.DOUBLE_JUMP);
			}
		}
	}

	private static void jump(ServerPlayer player) {
		UniqueConfig.DoubleJump cfg = PowerUtil.cfg().doubleJump;
		long now = Scheduler.ticks();
		Long last = LAST_JUMP.get(player.getUUID());
		if (last != null && now - last < Math.round(cfg.cooldownSeconds * 20)) {
			return;
		}
		LAST_JUMP.put(player.getUUID(), now);
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0E-6 ? Vec3.ZERO : flat.normalize().scale(cfg.forward);
		player.setDeltaMovement(flat.x, cfg.upward, flat.z);
		player.connection.send(new ClientboundSetEntityMotionPacket(player));
		player.resetFallDistance();
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 10, 0.3, 0.05, 0.3, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.7F, 1.3F);
	}

	/** Takes back the flight we granted (never touches creative / spectator flight, nor a flight still held by guild fly). */
	static void revoke(ServerPlayer player) {
		LAST_JUMP.remove(player.getUUID());
		FlightGrants.release(player, Owner.DOUBLE_JUMP);
	}

	/** Logout: the grant itself is taken back by {@link FlightGrants}; only the jump cooldown is forgotten here. */
	static void onLeave(ServerPlayer player) {
		LAST_JUMP.remove(player.getUUID());
	}

	static void clear() {
		LAST_JUMP.clear();
	}
}
