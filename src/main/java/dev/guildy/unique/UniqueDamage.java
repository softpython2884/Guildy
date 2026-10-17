package dev.guildy.unique;

import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Damage dealt by players to the bosses that drop unique weapons (ruin guardians, horde bosses): the unique weapon of a
 * ruin guardian goes to the guild of the player who dealt it the most damage ({@code UniqueDrops.Recipient.topDamager}).
 * Bounded runtime map, forgotten when the boss leaves the world or after 10 minutes without a hit.
 */
final class UniqueDamage {
	/** Ruin guardians (ruin.BossFactory.TAG_BOSS) and horde mobs (horde bosses have a visible name). */
	private static final String RUIN_BOSS_TAG = "guildy_ruin_boss";
	private static final String WAVE_TAG = "guildy_wave";
	private static final int MAX_TRACKED = 256;
	private static final long FORGET_TICKS = 10 * 60 * 20L;

	private static final class Tally {
		final Map<UUID, Float> damage = new HashMap<>();
		long lastHit;
	}

	private static final Map<UUID, Tally> TALLIES = new HashMap<>();

	private UniqueDamage() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0 && source.getEntity() instanceof ServerPlayer player && tracked(entity)) {
				Safe.run("unique.damage", () -> add(entity.getUUID(), player.getUUID(), taken));
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (!TALLIES.isEmpty() && entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy()) {
				TALLIES.remove(entity.getUUID());
			}
		});
		Scheduler.every("unique.damage.prune", 1200, 37, UniqueDamage::prune);
	}

	private static boolean tracked(LivingEntity entity) {
		Set<String> tags = entity.entityTags();
		return tags.contains(RUIN_BOSS_TAG) || tags.contains(WAVE_TAG) && entity.isCustomNameVisible();
	}

	static void add(UUID victim, UUID player, float amount) {
		Tally tally = TALLIES.get(victim);
		if (tally == null) {
			if (TALLIES.size() >= MAX_TRACKED) {
				prune();
				if (TALLIES.size() >= MAX_TRACKED) {
					return;
				}
			}
			tally = new Tally();
			TALLIES.put(victim, tally);
		}
		tally.damage.merge(player, amount, Float::sum);
		tally.lastHit = Scheduler.ticks();
	}

	/** Player who dealt the most damage to this entity (the killing blow is not counted: the killer is the fallback), or null. */
	static @Nullable UUID leader(LivingEntity victim) {
		Tally tally = TALLIES.get(victim.getUUID());
		if (tally == null) {
			return null;
		}
		UUID best = null;
		float most = 0;
		for (Map.Entry<UUID, Float> entry : tally.damage.entrySet()) {
			if (entry.getValue() > most) {
				most = entry.getValue();
				best = entry.getKey();
			}
		}
		return best;
	}

	private static void prune() {
		long now = Scheduler.ticks();
		Iterator<Tally> it = TALLIES.values().iterator();
		while (it.hasNext()) {
			if (now - it.next().lastHit > FORGET_TICKS) {
				it.remove();
			}
		}
	}

	static void clear() {
		TALLIES.clear();
	}
}
