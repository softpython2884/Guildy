package dev.guildy.ruin;

import dev.guildy.core.GuildyServer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Players rooted by a dread attack (grasping hands, a bone prison, a stunning roar): transient attribute modifiers (never
 * saved: a restart or a reconnection frees them) that zero their walking speed and their jump, removed when the root ends,
 * when the fight ends and at shutdown. A root never stacks: the longest one wins.
 */
final class Roots {
	private static final Identifier SPEED = Identifier.fromNamespaceAndPath("guildy", "ruin_dread_root_speed");
	private static final Identifier JUMP = Identifier.fromNamespaceAndPath("guildy", "ruin_dread_root_jump");
	/** Rooted player → game tick at which they are freed. */
	private static final Map<UUID, Long> ROOTED = new HashMap<>();

	private Roots() {
	}

	/** Roots a player for {@code ticks} (survival / adventure only). */
	static void root(ServerPlayer player, int ticks, long now) {
		if (ticks <= 0 || player.isCreative() || player.isSpectator()) {
			return;
		}
		ROOTED.merge(player.getUUID(), now + ticks, Math::max);
		apply(player);
		Vec3 motion = player.getDeltaMovement();
		player.setDeltaMovement(0, Math.min(0, motion.y), 0);
		player.syncVelocity = true;
	}

	static boolean rooted(UUID player) {
		return ROOTED.containsKey(player);
	}

	private static void apply(ServerPlayer player) {
		modifier(player.getAttribute(Attributes.MOVEMENT_SPEED), SPEED);
		modifier(player.getAttribute(Attributes.JUMP_STRENGTH), JUMP);
	}

	private static void modifier(AttributeInstance instance, Identifier id) {
		if (instance != null && !instance.hasModifier(id)) {
			instance.addTransientModifier(new AttributeModifier(id, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	private static void free(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SPEED);
		}
		AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
		if (jump != null) {
			jump.removeModifier(JUMP);
		}
	}

	/** Every tick: the roots that ended are lifted (the current entity of each player: a respawn makes a new one). */
	static void tick(long now) {
		if (ROOTED.isEmpty()) {
			return;
		}
		MinecraftServer server = GuildyServer.server();
		Iterator<Map.Entry<UUID, Long>> it = ROOTED.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Long> entry = it.next();
			ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				// Offline: the transient modifiers are gone with the entity.
				it.remove();
				continue;
			}
			if (now >= entry.getValue() || !player.isAlive() || player.isCreative() || player.isSpectator()) {
				free(player);
				it.remove();
			} else {
				// A respawned / reloaded entity gets them again.
				apply(player);
			}
		}
	}

	/** Frees everyone (end of every fight at shutdown, tests). */
	static void clear() {
		MinecraftServer server = GuildyServer.server();
		for (UUID uuid : ROOTED.keySet()) {
			ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(uuid);
			if (player != null) {
				free(player);
			}
		}
		ROOTED.clear();
	}

	/** Frees the given players (a fight ended). */
	static void release(Iterable<UUID> players) {
		MinecraftServer server = GuildyServer.server();
		for (UUID uuid : players) {
			if (ROOTED.remove(uuid) != null) {
				ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(uuid);
				if (player != null) {
					free(player);
				}
			}
		}
	}
}
