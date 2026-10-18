package dev.guildy.stats;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Where a tracked fight takes place (given by the module that opens the session, e.g. a ruin and its surroundings). */
public interface FightArena {
	/** The player stands in the arena (position only: also true for a player who just died there). */
	boolean contains(ServerPlayer player);

	/** Players taking part right now: in the arena, alive, neither spectators nor in creative. */
	List<ServerPlayer> present();
}
