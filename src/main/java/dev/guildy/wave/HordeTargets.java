package dev.guildy.wave;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.AbstractVillager;

/**
 * Horde mobs leave the villagers alone ({@code mobs.targetVillagers} false in waves.json5). Vanilla zombies, husks,
 * drowned, pillagers, vindicators, evokers, illusioners and ravagers pick a villager they see with a
 * {@code NearestAttackableTargetGoal<AbstractVillager>}, then zombies, pillagers and evokers keep hunting it out of sight
 * (through doors they break on Hard) across the follow range the hordes raise to 48 blocks: the villagers of the base
 * were hunted down, the workers on their way home first. That goal now skips the villagers
 * (guild villagers, candidates, merchants) for mobs tagged {@code guildy_wave}, except a villager that just hurt the mob:
 * a guard fighting a monster may be targeted back (the vanilla retaliation goal is untouched). Applied by
 * {@code dev.guildy.wave.mixin.NearestAttackableTargetGoalMixin}; brain-driven mobs (piglins, hoglins) never target
 * villagers anyway.
 */
public final class HordeTargets {
	private HordeTargets() {
	}

	/** True when this mob is a horde mob that must not pick villagers as targets. Cheap: called by target searches. */
	public static boolean restricted(Mob mob) {
		return !WaveModule.config().mobs.targetVillagers && mob.entityTags().contains(WaveSpawner.TAG);
	}

	/** A villager the (restricted) horde mob ignores: any villager, unless it is the last one that hurt the mob. */
	public static boolean spared(Mob mob, Entity candidate) {
		return candidate instanceof AbstractVillager villager && mob.getLastHurtByMob() != villager;
	}
}
