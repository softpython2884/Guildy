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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One running fight: the guardian, its escort, its boss bar, the participants, the ability cooldowns, the threat level
 * of the ruin and the resurrection of a guardian killed too fast. Runtime only: a fight never survives a restart or the
 * unloading of its guardian (its tagged mobs are then refused at load).
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
	/** Health ratio under which the guardian enrages (raised by the threat level). */
	double enrageBelow;
	boolean enraged;
	/** Threat level of this fight ("Menace", fixed at the awakening) and the statistics it gives. */
	final int threat;
	final ThreatRules.Scaling scaling;
	/** Group scaling: the players counted when the guardian appeared (fixed for the fight, see {@link GroupScaling}). */
	GroupScaling.Party party = GroupScaling.Party.NONE;
	/** Fast-kill window: a guardian killed less than this many ticks after it appeared rises again (0 = never). */
	final long fastKillTicks;
	/** The resurrection running (the guardian is invulnerable and frozen), null otherwise. */
	@Nullable Resurrection rising;
	/** The guardian came back from the dead (once per fight): enraged, bonus rewards. */
	boolean risen;
	/** The curse of a cursed fight (cursed incantation scroll): phases, attacks, rewards; null for a normal fight. */
	@Nullable CursedEncounter cursed;
	/** Its dread form (from {@code escalation.dreadThreshold}): NONE below the threshold. */
	DreadRules.Stats dread = DreadRules.Stats.NONE;
	/** Effective health beyond the vanilla cap of 1024: every hit it takes (and every heal) is divided by this (1 = none). */
	double vitality = 1.0;
	/** Damage factor on the players hit by the mobs of this fight (after their armour), and the share of their health it may reach. */
	double playerDamage = 1.0;
	double maxHitRatio;
	/** The dread abilities of its dread form (and the Avatar's attacks), null without any. */
	@Nullable DreadEncounter dreadFight;
	/** The Avatar (a cursed guardian summoned with a Nether star in the circle), null otherwise. */
	@Nullable AvatarEncounter avatar;
	/** Cooldown factor, extra damage, scale and speed of the risen guardian (re-applied after a conversion). */
	double risenCooldown = 1.0;
	double risenDamage;
	double risenScale = 1.0;
	double risenSpeed;
	/** Where escort mobs died, most recent last (souls drawn by a resurrection). */
	final Deque<Vec3> fallen = new ArrayDeque<>();
	long nextAbilityTick;
	int abilityOffset;
	int secondsAlone;
	int kills;
	/** Players currently seeing the boss bar. */
	final Set<ServerPlayer> viewers = new HashSet<>();

	Encounter(RuinRecord ruin, RuinDef def, ResourceKey<Level> dimension, BlockPos spawn, ServerBossEvent bar, long now, int firstAbilitySeconds,
			ThreatRules.Scaling scaling, double enragePerLevel, double maxEnrage, long fastKillTicks) {
		this.ruin = ruin;
		this.def = def;
		this.dimension = dimension;
		this.spawn = spawn;
		this.bar = bar;
		this.startedTick = now;
		this.scaling = scaling;
		this.threat = scaling.level();
		this.fastKillTicks = Math.max(0, fastKillTicks);
		for (AbilityDef ability : def.abilities) {
			if ("enrage".equals(ability.type)) {
				if (enrage == null) {
					enrage = ability;
				}
			} else {
				abilities.add(ability);
			}
		}
		enrageBelow = enrage == null ? -1 : ThreatRules.enrageThreshold(enrage.healthBelow, enragePerLevel, threat, maxEnrage);
		readyAt = new long[abilities.size()];
		healthBelow = new double[abilities.size()];
		for (int i = 0; i < abilities.size(); i++) {
			// Staggered: each ability waits its cooldown once before its first use (the first one is quicker).
			readyAt[i] = now + firstAbilitySeconds * 20L
					+ (i == 0 ? 0 : RuinRules.cooldownTicks(abilities.get(i).cooldownSeconds, 0.5 * scaling.cooldownFactor()));
			healthBelow[i] = abilities.get(i).healthBelow;
		}
		nextAbilityTick = now + firstAbilitySeconds * 20L;
	}

	/** Factor of the ability cooldowns: threat level, halved by the enrage, the risen guardian's factor, the curse, the dread form. */
	double cooldownFactor() {
		return scaling.cooldownFactor() * (enraged ? 0.5 : 1.0) * (risen ? risenCooldown : 1.0) * (cursed != null ? cursed.cooldownFactor() : 1.0)
				* (dread.active() ? dread.cooldown() : 1.0);
	}

	/** Factor of the ability damage: threat level, group, the risen guardian's bonus, the curse (and its phase). */
	double damageFactor() {
		return GroupScaling.combined(scaling.damage(), party.damage()) * (risen ? 1.0 + risenDamage : 1.0)
				* (cursed != null ? cursed.damageFactor() : 1.0);
	}

	/** Cap of the living escort (the threat level, the group and the curse add mobs). */
	int maxAlive() {
		return Math.min(48, def.minions.maxAlive + scaling.extraMinions() + party.extraMinions() + (cursed != null ? cursed.extraAlive() : 0)
				+ (avatar != null ? avatar.extraAlive() : 0));
	}

	/** Remembers where an escort mob died (bounded). */
	void addFallen(Vec3 pos) {
		if (fallen.size() >= 12) {
			fallen.removeFirst();
		}
		fallen.addLast(pos);
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
