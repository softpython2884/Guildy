package dev.guildy.stats;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Scheduler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Who is healing right now: a small stack of "healer" contexts set around the code that heals on a player's behalf (the
 * tick of a Guildy spell effect: its caster; an instant health effect: the player who threw the potion; a regeneration tick:
 * the player who gave that regeneration). {@code LivingEntity.heal} and {@code addEffect} read it (see
 * {@code stats.mixin}). Regeneration given by a player is remembered per target until it expires or another regeneration
 * replaces it (bounded). Server thread only; the stack is reset every tick (a context left open by an error never leaks).
 */
public final class FightHealing {
	private static final int MAX_DEPTH = 8;
	private static final int MAX_REGEN = 512;
	/** Longest regeneration remembered (an infinite one is not followed beyond this). */
	private static final long MAX_REGEN_TICKS = 10 * 60 * 20L;

	private record Regen(UUID healer, long until) {
	}

	private static final ServerPlayer[] STACK = new ServerPlayer[MAX_DEPTH];
	private static final Map<UUID, Regen> REGEN = new HashMap<>();
	private static int depth;
	private static @Nullable ServerPlayer healer;

	private FightHealing() {
	}

	/** The player healing right now, or null. */
	public static @Nullable ServerPlayer healer() {
		return healer;
	}

	/** Opens a context: {@code player} heals until the matching {@link #pop()}. */
	public static void push(@Nullable ServerPlayer player) {
		if (depth < MAX_DEPTH) {
			STACK[depth] = healer;
		}
		depth++;
		healer = player;
	}

	public static void pop() {
		if (depth <= 0) {
			healer = null;
			return;
		}
		depth--;
		healer = depth < MAX_DEPTH ? STACK[depth] : null;
		if (depth < MAX_DEPTH) {
			STACK[depth] = null;
		}
	}

	/** An instant health effect (potion, lingering cloud): its owner heals. */
	public static void pushEffect(@Nullable Entity source, @Nullable Entity owner) {
		push(FightTracker.tracking() ? FightTracker.player(owner != null ? owner : source) : null);
	}

	/** A regeneration tick of {@code target}: the player who gave that regeneration heals (nobody when it is its own). */
	public static void pushRegeneration(LivingEntity target) {
		ServerPlayer giver = null;
		if (!REGEN.isEmpty() && FightTracker.tracking()) {
			Regen regen = REGEN.get(target.getUUID());
			if (regen != null && regen.until() >= Scheduler.ticks()) {
				giver = GuildyServer.player(regen.healer());
			}
		}
		push(giver);
	}

	/** {@code healer} gave {@code target} a regeneration lasting {@code duration} ticks (-1 = infinite). */
	static void regeneration(UUID target, UUID healer, int duration) {
		if (REGEN.size() >= MAX_REGEN && !REGEN.containsKey(target)) {
			prune();
			if (REGEN.size() >= MAX_REGEN) {
				return;
			}
		}
		long ticks = duration < 0 ? MAX_REGEN_TICKS : Math.min(MAX_REGEN_TICKS, duration);
		REGEN.put(target, new Regen(healer, Scheduler.ticks() + ticks));
	}

	/** The target's regeneration now comes from elsewhere (its own potion, food, a beacon...). */
	static void forget(UUID target) {
		REGEN.remove(target);
	}

	static void prune() {
		long now = Scheduler.ticks();
		Iterator<Regen> it = REGEN.values().iterator();
		while (it.hasNext()) {
			if (it.next().until() < now) {
				it.remove();
			}
		}
	}

	/** Every tick: no context survives the tick that opened it. */
	static void reset() {
		if (depth != 0 || healer != null) {
			for (int i = 0; i < MAX_DEPTH; i++) {
				STACK[i] = null;
			}
			depth = 0;
			healer = null;
		}
	}

	static void clear() {
		reset();
		REGEN.clear();
	}
}
