package dev.guildy.scroll;

import dev.guildy.Guildy;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.stats.FightHealing;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Ticks the running spells (every server tick, each one isolated by {@link Safe}): a bounded number of effects (per player
 * and server-wide, checked before a cast), a shared particle budget split between them (rotating order so a crowded tick
 * never starves the same effect), clean ends on logout / dimension change / server stop.
 */
final class EffectRunner {
	/** Minimum particle share of an effect in a crowded tick. */
	private static final int MIN_SHARE = 40;
	private static final List<SpellEffect> ACTIVE = new ArrayList<>();
	/** Effects started while the runner ticks (spells spawning sub-effects): added at the next tick. */
	private static final List<SpellEffect> ADDED = new ArrayList<>();
	private static boolean ticking;
	private static int rotation;

	private EffectRunner() {
	}

	static void init() {
		Scheduler.every("scroll.effects", 1, 0, EffectRunner::tick);
	}

	static void start(SpellEffect effect) {
		if (ticking) {
			ADDED.add(effect);
		} else {
			ACTIVE.add(effect);
		}
	}

	/** Running effects of a player (bound or not). */
	static int count(UUID owner) {
		int n = 0;
		for (SpellEffect effect : ACTIVE) {
			if (effect.owner.equals(owner) && !effect.done()) {
				n++;
			}
		}
		for (SpellEffect effect : ADDED) {
			if (effect.owner.equals(owner)) {
				n++;
			}
		}
		return n;
	}

	static int count() {
		return ACTIVE.size() + ADDED.size();
	}

	/** Running effects of a type (tests, guardians count). */
	static <T extends SpellEffect> List<T> all(Class<T> type) {
		List<T> out = new ArrayList<>();
		for (SpellEffect effect : ACTIVE) {
			if (type.isInstance(effect) && !effect.done()) {
				out.add(type.cast(effect));
			}
		}
		for (SpellEffect effect : ADDED) {
			if (type.isInstance(effect)) {
				out.add(type.cast(effect));
			}
		}
		return out;
	}

	private static void tick() {
		if (!ADDED.isEmpty()) {
			ACTIVE.addAll(ADDED);
			ADDED.clear();
		}
		if (ACTIVE.isEmpty()) {
			return;
		}
		ScrollConfig.Limits limits = ScrollModule.config().limits;
		int size = ACTIVE.size();
		int share = Math.max(MIN_SHARE, limits.particleBudget / size);
		int left = limits.particleBudget;
		rotation = (rotation + 1) % size;
		List<SpellEffect> order = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			order.add(ACTIVE.get((i + rotation) % size));
		}
		ticking = true;
		try {
			for (SpellEffect effect : order) {
				if (effect.done()) {
					continue;
				}
				if (effect.level.getServer().getLevel(effect.level.dimension()) != effect.level) {
					effect.finish();
					continue;
				}
				Fx fx = new Fx(effect.level, effect.anchor(), Math.min(share, Math.max(0, left)), limits.viewDistance);
				// Whatever the spell heals or shields counts for its caster in the fight statistics.
				FightHealing.push(effect.ctx.caster);
				boolean ok;
				try {
					ok = Safe.get("scroll.effect", () -> {
						effect.tick(fx);
						return true;
					}, false);
				} finally {
					FightHealing.pop();
				}
				if (!ok) {
					Guildy.LOGGER.warn("[scrolls] spell effect {} stopped after an error", effect.getClass().getSimpleName());
					effect.finish();
				} else if (effect.ctx.cursed()) {
					// Cast from a cursed grimoire: its dark layer over the spell's own animation (same particle share).
					Safe.run("scroll.effect.cursed", () -> CursedSpellFx.overlay(effect, fx));
				}
				effect.age++;
				left -= fx.used();
				Safe.run("scroll.effect.send", fx::flush);
			}
		} finally {
			ticking = false;
		}
		Iterator<SpellEffect> it = ACTIVE.iterator();
		while (it.hasNext()) {
			SpellEffect effect = it.next();
			if (effect.done()) {
				it.remove();
				Safe.run("scroll.effect.end", () -> effect.end(false));
			}
		}
	}

	/** Ends the effects of a player who logged out or changed dimension. */
	static void endFor(UUID owner) {
		List<SpellEffect> ended = new ArrayList<>();
		for (List<SpellEffect> list : List.of(ACTIVE, ADDED)) {
			Iterator<SpellEffect> it = list.iterator();
			while (it.hasNext()) {
				SpellEffect effect = it.next();
				if (effect.owner.equals(owner) && effect.bound()) {
					it.remove();
					ended.add(effect);
				}
			}
		}
		for (SpellEffect effect : ended) {
			effect.finish();
			Safe.run("scroll.effect.end", () -> effect.end(true));
		}
	}

	/** Server stop: ends everything (summoned guardians disappear before the final save). */
	static void clear() {
		List<SpellEffect> ended = new ArrayList<>(ACTIVE);
		ended.addAll(ADDED);
		ACTIVE.clear();
		ADDED.clear();
		for (SpellEffect effect : ended) {
			effect.finish();
			Safe.run("scroll.effect.end", () -> effect.end(true));
		}
	}
}
