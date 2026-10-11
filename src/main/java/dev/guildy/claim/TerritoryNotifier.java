package dev.guildy.claim;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Shows a title (or action bar) when a player enters a guild territory and "Zone sauvage" when leaving it.
 * Each player is checked every {@link #INTERVAL} ticks, players spread over ticks by entity id.
 */
final class TerritoryNotifier {
	private static final int INTERVAL = 10;
	private static final Map<UUID, State> STATES = new HashMap<>();

	private TerritoryNotifier() {
	}

	private static final class State {
		@Nullable ResourceKey<Level> dimension;
		long chunk;
		@Nullable UUID owner;
		/** False until the owner of the current chunk is known (login, dimension change). */
		boolean known;
	}

	static void register() {
		Scheduler.every("territory.notify", 1, 0, TerritoryNotifier::tick);
	}

	private static void tick() {
		int phase = (int) (Scheduler.ticks() % INTERVAL);
		for (ServerPlayer player : GuildyServer.players()) {
			if (Math.floorMod(player.getId(), INTERVAL) == phase) {
				Safe.run("territory.notify", () -> check(player, false, false));
			}
		}
	}

	/** Territories changed: update every state silently (no title for chunks claimed under someone's feet). */
	static void refreshSilently() {
		for (ServerPlayer player : GuildyServer.players()) {
			Safe.run("territory.refresh", () -> check(player, true, true));
		}
	}

	private static void check(ServerPlayer player, boolean force, boolean silent) {
		State state = STATES.computeIfAbsent(player.getUUID(), k -> new State());
		Level level = player.level();
		ResourceKey<Level> dimension = level.dimension();
		if (!ClaimGuard.isClaimLevel(level)) {
			state.dimension = dimension;
			state.owner = null;
			state.known = false;
			return;
		}
		long chunk = ChunkKey.ofBlock(player.getBlockX(), player.getBlockZ());
		boolean sameDimension = dimension.equals(state.dimension);
		if (!force && sameDimension && state.known && state.chunk == chunk) {
			return;
		}
		if (!sameDimension) {
			state.known = false;
		}
		state.dimension = dimension;
		state.chunk = chunk;
		Guild owner = GuildManager.atChunk(chunk);
		UUID ownerId = owner == null ? null : owner.id;
		if (state.known && Objects.equals(ownerId, state.owner)) {
			return;
		}
		boolean first = !state.known;
		state.owner = ownerId;
		state.known = true;
		if (silent) {
			return;
		}
		if (owner != null) {
			showEnter(player, owner);
		} else if (!first) {
			Msg.actionBar(player, "territory.wild");
		}
	}

	private static void showEnter(ServerPlayer player, Guild owner) {
		boolean own = owner.isMember(player.getUUID());
		if (GuildyServer.config().territory.enterTitle) {
			Component subtitle = own ? Lang.get("territory.own.subtitle") : Lang.get("territory.enter.subtitle", owner.level);
			Msg.title(player, Lang.get("territory.enter.title", owner.name), subtitle, 5, 40, 10);
		} else if (own) {
			Msg.actionBar(player, "territory.own.actionbar", owner.name);
		} else {
			Msg.actionBar(player, "territory.enter.actionbar", owner.name, owner.level);
		}
	}

	static void forget(UUID player) {
		STATES.remove(player);
	}

	static void clear() {
		STATES.clear();
	}
}
