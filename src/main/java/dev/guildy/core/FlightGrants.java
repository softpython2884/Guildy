package dev.guildy.core;

import dev.guildy.core.FlightOwnership.Owner;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who gave a survival player's {@code abilities.mayfly} (rules: {@link FlightOwnership}). Every Guildy feature granting
 * flight goes through here, so that one never takes back a flight another one still needs, and none touches a flight it
 * did not give. Creative / spectator abilities are never changed. Each grant is marked with the owner's entity tag (saved
 * with the player): a grant left over by a crash is taken back at login; logging out lets go of every grant.
 */
public final class FlightGrants {
	private static final Map<UUID, EnumSet<Owner>> HOLDERS = new HashMap<>();

	private FlightGrants() {
	}

	public static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Safe.run("flight.join", () -> onJoin(handler.getPlayer())));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Safe.run("flight.leave", () -> releaseAll(handler.getPlayer())));
		Lifecycle.STOPPING.add(server -> {
			for (ServerPlayer player : List.copyOf(GuildyServer.players())) {
				Safe.run("flight.stop", () -> releaseAll(player));
			}
			HOLDERS.clear();
		});
	}

	/** Survival / adventure: the only modes whose flight Guildy may change. */
	public static boolean manageable(ServerPlayer player) {
		return player.gameMode.isSurvival() && !player.isSpectator();
	}

	public static Set<Owner> holders(ServerPlayer player) {
		EnumSet<Owner> holders = HOLDERS.get(player.getUUID());
		return holders == null ? Set.of() : holders;
	}

	public static boolean holds(ServerPlayer player, Owner owner) {
		return holders(player).contains(owner);
	}

	/** A higher-ranked owner holds the grant: {@code owner} must step aside. */
	public static boolean outranked(ServerPlayer player, Owner owner) {
		return FlightOwnership.outranked(owner, holders(player));
	}

	/**
	 * Takes (or keeps) the grant for {@code owner}: mayfly on, tag set. Also gives mayfly back to a holder that lost it
	 * (respawn, another mod). Refused in creative / spectator and when mayfly comes from outside Guildy.
	 *
	 * @return true when {@code owner} holds the grant afterwards
	 */
	public static boolean grant(ServerPlayer player, Owner owner) {
		if (!manageable(player)) {
			return false;
		}
		Set<Owner> current = holders(player);
		Abilities abilities = player.getAbilities();
		if (!current.contains(owner) && !FlightOwnership.mayGrant(abilities.mayfly, current)) {
			return false;
		}
		HOLDERS.computeIfAbsent(player.getUUID(), id -> EnumSet.noneOf(Owner.class)).add(owner);
		player.addTag(owner.tag);
		if (!abilities.mayfly) {
			abilities.mayfly = true;
			player.onUpdateAbilities();
		}
		return true;
	}

	/**
	 * Lets go of {@code owner}'s grant: mayfly is taken back only when no other owner still holds it, flying stops when the
	 * last owner allowing real flight lets go. Creative / spectator abilities stay untouched.
	 *
	 * @return true when {@code owner} held the grant
	 */
	public static boolean release(ServerPlayer player, Owner owner) {
		player.removeTag(owner.tag);
		EnumSet<Owner> holders = HOLDERS.get(player.getUUID());
		if (holders == null || !holders.remove(owner)) {
			return false;
		}
		if (holders.isEmpty()) {
			HOLDERS.remove(player.getUUID());
		}
		if (!manageable(player)) {
			return true;
		}
		Abilities abilities = player.getAbilities();
		boolean changed = false;
		if (FlightOwnership.revokeMayfly(holders) && abilities.mayfly) {
			abilities.mayfly = false;
			changed = true;
		}
		if (FlightOwnership.stopFlying(owner, holders) && abilities.flying) {
			abilities.flying = false;
			changed = true;
		}
		if (changed) {
			player.onUpdateAbilities();
		}
		return true;
	}

	/** Drops {@code owner}'s hold without touching the abilities (the player switched to creative / spectator). */
	public static void forget(ServerPlayer player, Owner owner) {
		player.removeTag(owner.tag);
		EnumSet<Owner> holders = HOLDERS.get(player.getUUID());
		if (holders != null && holders.remove(owner) && holders.isEmpty()) {
			HOLDERS.remove(player.getUUID());
		}
	}

	/** Logout / server stop: every grant is taken back, so that the saved player never keeps a Guildy flight. */
	public static void releaseAll(ServerPlayer player) {
		for (Owner owner : Owner.values()) {
			release(player, owner);
		}
		HOLDERS.remove(player.getUUID());
	}

	/** Login: a grant tag left by a crash means mayfly was ours; it is taken back (the features grant it again if allowed). */
	public static void onJoin(ServerPlayer player) {
		HOLDERS.remove(player.getUUID());
		boolean leftover = false;
		for (Owner owner : Owner.values()) {
			leftover |= player.removeTag(owner.tag);
		}
		if (leftover && manageable(player)) {
			Abilities abilities = player.getAbilities();
			if (abilities.mayfly || abilities.flying) {
				abilities.mayfly = false;
				abilities.flying = false;
				player.onUpdateAbilities();
			}
		}
	}
}
