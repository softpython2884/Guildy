package dev.guildy.unique;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bottes de Zéphyr: double jump for vanilla clients. While the boots are worn (survival / adventure) the player may
 * "fly" (abilities.mayfly) when standing on the ground; a double tap on jump makes the client start flying, which the
 * server turns into an upward boost and takes the flight back until the next landing. Players allowed to fly never take
 * fall damage (vanilla rule), which is part of the power. The grant is marked with an entity tag (saved with the player)
 * so that a crash never leaves a survival player able to fly: the tag is cleaned up at the next login.
 */
final class DoubleJump {
	static final String TAG = "guildy_double_jump";
	private static final Set<UUID> GRANTED = new HashSet<>();
	private static final Map<UUID, Long> LAST_JUMP = new HashMap<>();

	private DoubleJump() {
	}

	/** Every server tick: grants, jumps, revokes. Cheap (one slot read per online player). */
	static void tick() {
		for (ServerPlayer player : List.copyOf(GuildyServer.players())) {
			boolean eligible = player.gameMode.isSurvival() && !player.isSpectator()
					&& UniqueItems.powerOf(player.getItemBySlot(EquipmentSlot.FEET)) == UniquePower.DOUBLE_JUMP;
			boolean managed = GRANTED.contains(player.getUUID());
			if (!eligible) {
				if (managed) {
					revoke(player);
				}
				continue;
			}
			Abilities abilities = player.getAbilities();
			if (managed && abilities.flying) {
				// Double tap on jump: the client asked to fly.
				abilities.flying = false;
				abilities.mayfly = false;
				player.onUpdateAbilities();
				jump(player);
				continue;
			}
			if ((player.onGround() || player.isInWater() || player.onClimbable()) && (!managed || !abilities.mayfly)) {
				grant(player);
			}
		}
	}

	private static void grant(ServerPlayer player) {
		GRANTED.add(player.getUUID());
		player.addTag(TAG);
		Abilities abilities = player.getAbilities();
		if (!abilities.mayfly) {
			abilities.mayfly = true;
			player.onUpdateAbilities();
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

	/** Takes back the flight we granted (never touches creative / spectator flight). */
	static void revoke(ServerPlayer player) {
		GRANTED.remove(player.getUUID());
		LAST_JUMP.remove(player.getUUID());
		player.removeTag(TAG);
		if (player.gameMode.isSurvival()) {
			Abilities abilities = player.getAbilities();
			if (abilities.mayfly || abilities.flying) {
				abilities.mayfly = false;
				abilities.flying = false;
				player.onUpdateAbilities();
			}
		}
	}

	/** Login: a leftover grant (crash, logout with the boots on) is taken back; the next tick grants it again if worn. */
	static void onJoin(ServerPlayer player) {
		if (player.entityTags().contains(TAG)) {
			revoke(player);
		}
	}

	static void onLeave(ServerPlayer player) {
		if (GRANTED.contains(player.getUUID())) {
			revoke(player);
		}
	}

	static void clear() {
		GRANTED.clear();
		LAST_JUMP.clear();
	}
}
