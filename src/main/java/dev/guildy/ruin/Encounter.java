package dev.guildy.ruin;

import dev.guildy.ruin.RuinConfig.AbilityDef;
import dev.guildy.ruin.RuinConfig.RuinDef;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One running fight: the guardian, its escort, its boss bar, the participants and the ability cooldowns. Runtime
 * only: a fight never survives a restart or the unloading of its guardian (its tagged mobs are then refused at load).
 */
final class Encounter {
	final RuinRecord ruin;
	final RuinDef def;
	final ResourceKey<Level> dimension;
	final BlockPos spawn;
	final ServerBossEvent bar;
	final long startedTick;
	@Nullable UUID boss;
	final Set<UUID> minions = new LinkedHashSet<>();
	/** Players who damaged the guardian (+ players present at its death). */
	final Set<UUID> participants = new HashSet<>();
	/** Names of the participants (announcement). */
	final Map<UUID, String> names = new LinkedHashMap<>();
	/** Abilities usable by cooldown (enrage excluded), with their next ready tick. */
	final List<AbilityDef> abilities = new ArrayList<>();
	final long[] readyAt;
	final double[] healthBelow;
	@Nullable AbilityDef enrage;
	boolean enraged;
	double cooldownFactor = 1.0;
	long nextAbilityTick;
	int abilityOffset;
	int secondsAlone;
	int kills;
	/** Players currently seeing the boss bar. */
	final Set<ServerPlayer> viewers = new HashSet<>();

	Encounter(RuinRecord ruin, RuinDef def, ResourceKey<Level> dimension, BlockPos spawn, ServerBossEvent bar, long now, int firstAbilitySeconds) {
		this.ruin = ruin;
		this.def = def;
		this.dimension = dimension;
		this.spawn = spawn;
		this.bar = bar;
		this.startedTick = now;
		for (AbilityDef ability : def.abilities) {
			if ("enrage".equals(ability.type)) {
				if (enrage == null) {
					enrage = ability;
				}
			} else {
				abilities.add(ability);
			}
		}
		readyAt = new long[abilities.size()];
		healthBelow = new double[abilities.size()];
		for (int i = 0; i < abilities.size(); i++) {
			// Staggered: each ability waits its cooldown once before its first use (the first one is quicker).
			readyAt[i] = now + firstAbilitySeconds * 20L + (i == 0 ? 0 : RuinRules.cooldownTicks(abilities.get(i).cooldownSeconds, 0.5));
			healthBelow[i] = abilities.get(i).healthBelow;
		}
		nextAbilityTick = now + firstAbilitySeconds * 20L;
	}

	boolean tracks(UUID uuid) {
		return uuid.equals(boss) || minions.contains(uuid);
	}

	@Nullable Mob bossEntity(ServerLevel level) {
		if (boss == null) {
			return null;
		}
		Entity entity = level.getEntity(boss);
		return entity instanceof Mob mob && mob.isAlive() ? mob : null;
	}

	/** Loaded, living escort mobs. */
	List<Mob> minionEntities(ServerLevel level) {
		List<Mob> list = new ArrayList<>();
		for (UUID uuid : minions) {
			Entity entity = level.getEntity(uuid);
			if (entity instanceof Mob mob && mob.isAlive()) {
				list.add(mob);
			}
		}
		return list;
	}

	String bossName() {
		return def.boss.name;
	}

	void addParticipant(ServerPlayer player) {
		participants.add(player.getUUID());
		names.put(player.getUUID(), player.getName().getString());
	}
}
