package dev.guildy.villager;

import dev.guildy.core.Lifecycle;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hits and shots of the guards that have a target, every {@link #INTERVAL} ticks (the once-per-second
 * {@link GuardTask} step only picks the target and moves): swordsmen strike within reach, archers shoot real arrows
 * ({@link GuardArrow}) within range and sight, each with its cooldown. Only guards in a fight are visited.
 */
final class GuardCombat {
	static final int INTERVAL = 4;
	/** Angle (degrees) of the side bolts of a Multishot crossbow. */
	private static final double MULTISHOT_ANGLE = 10.0;
	private static final Map<UUID, Fighter> FIGHTERS = new HashMap<>();

	private GuardCombat() {
	}

	/** Combat state of one guard, owned by its {@link GuardTask} and refreshed at each of its steps. */
	static final class Fighter {
		@Nullable Villager villager;
		@Nullable ServerLevel level;
		@Nullable Guild guild;
		@Nullable VillagerRecord record;
		@Nullable Mob target;
		GuardRole role = GuardRole.SWORD;
		/** What it fights with (storage or base weapon, see {@link GuardGear}), refreshed at each step of the guard. */
		ItemStack weapon = ItemStack.EMPTY;
		/** Delay between two hits / shots with that weapon. */
		int cooldownTicks = 20;
		/** Retreating: only strikes (or shoots) what is within arm's reach, never chases. */
		boolean defensive;
		long lastAttack = -10_000;
	}

	static void register() {
		Scheduler.every("villager.guard.combat", INTERVAL, 1, GuardCombat::tick);
		Lifecycle.STARTED.add(server -> FIGHTERS.clear());
		Lifecycle.STOPPING.add(server -> FIGHTERS.clear());
	}

	static void engage(Fighter fighter, WorkContext ctx, Mob target, GuardRole role, boolean defensive) {
		fighter.villager = ctx.villager;
		fighter.level = ctx.level;
		fighter.guild = ctx.guild;
		fighter.record = ctx.record;
		fighter.target = target;
		fighter.role = role;
		fighter.defensive = defensive;
		fighter.weapon = GuardGear.weapon(ctx.level, ctx.guild, ctx.villager, role);
		fighter.cooldownTicks = GuardGear.cooldownTicks(ctx.level, fighter.weapon, role, ctx.config);
		FIGHTERS.put(ctx.record.uuid, fighter);
	}

	static void disengage(Fighter fighter) {
		fighter.target = null;
		VillagerRecord record = fighter.record;
		if (record != null) {
			FIGHTERS.remove(record.uuid, fighter);
		}
	}

	private static void tick() {
		if (FIGHTERS.isEmpty()) {
			return;
		}
		long now = Scheduler.ticks();
		for (Fighter fighter : List.copyOf(FIGHTERS.values())) {
			if (!Safe.get("villager.guard.combat", () -> step(fighter, now), false)) {
				disengage(fighter);
			}
		}
	}

	/** @return false when the fight is over (guard or target gone, not a guard anymore) */
	private static boolean step(Fighter fighter, long now) {
		Villager villager = fighter.villager;
		Mob target = fighter.target;
		ServerLevel level = fighter.level;
		Guild guild = fighter.guild;
		VillagerRecord record = fighter.record;
		if (villager == null || target == null || level == null || guild == null || record == null) {
			return false;
		}
		if (villager.isRemoved() || !villager.isAlive() || target.isRemoved() || !target.isAlive() || villager.level() != level
				|| target.level() != level || guild.villagers.members.get(record.uuid) != record || record.task != Task.GUARD) {
			return false;
		}
		if (villager.isTrading() || villager.isPassenger()) {
			return true;
		}
		VillagerConfig.Guard cfg = VillagerModule.config().guard;
		double distSq = villager.distanceToSqr(target);
		if (fighter.role == GuardRole.BOW) {
			double range = fighter.defensive ? cfg.attackReach + 1.5 : cfg.bowRange;
			if (now - fighter.lastAttack < fighter.cooldownTicks || distSq > range * range || !villager.hasLineOfSight(target)) {
				return true;
			}
			shoot(level, guild, villager, target, cfg, fighter.weapon);
		} else {
			double reach = cfg.attackReach;
			if (now - fighter.lastAttack < fighter.cooldownTicks || distSq > reach * reach
					|| Math.abs(villager.getY() - target.getY()) > 2.0 || !villager.hasLineOfSight(target)) {
				return true;
			}
			strike(level, guild, villager, target, fighter.weapon);
		}
		fighter.lastAttack = now;
		return true;
	}

	/**
	 * Melee hit: guard damage (config + guild level + upgrades) + what the sword deals more than a wooden one, and its
	 * enchantments (storage sword, else the base sword of the guild level).
	 */
	static void strike(ServerLevel level, Guild guild, Villager villager, Mob target, ItemStack weapon) {
		DamageSource source = level.damageSources().mobAttack(villager);
		float damage = (float) Guards.baseDamage(guild);
		if (!weapon.isEmpty()) {
			damage += (float) GuardRules.swordBonus(GuardEquipment.attackDamage(weapon), GuardEquipment.BASELINE_SWORD_DAMAGE);
			damage = EnchantmentHelper.modifyDamage(level, weapon, target, source, damage);
		}
		villager.getLookControl().setLookAt(target, 30.0f, 30.0f);
		WorldUtil.swing(villager);
		if (target.hurtServer(level, source, damage)) {
			if (!weapon.isEmpty()) {
				EnchantmentHelper.doPostAttackEffectsWithItemSource(level, target, source, weapon);
			}
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
			WorldUtil.playSound(level, villager, SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1.1f);
		}
	}

	/**
	 * Bow or crossbow shot like a skeleton's / pillager's (aims a bit above, real arrow owned by the villager, flies
	 * through non-monsters). The weapon's enchantments act on impact (Power, Punch; Flame sets the monster on fire;
	 * Piercing goes through several monsters): the "projectile spawned" effects are not applied, a burning arrow could
	 * light TNT or campfires in the base. A crossbow hits harder ({@code guardGear.crossbowDamageFactor}) and Multishot
	 * fires three bolts.
	 */
	static void shoot(ServerLevel level, Guild guild, Villager villager, Mob target, VillagerConfig.Guard cfg, ItemStack weapon) {
		ItemStack launcher = weapon.isEmpty() ? new ItemStack(Items.BOW) : weapon.copy();
		boolean crossbow = launcher.is(Items.CROSSBOW);
		boolean flame = !crossbow && GuardEquipment.hasFlame(level, launcher);
		double damage = Guards.baseDamage(guild) * cfg.bowDamageFactor * (crossbow ? VillagerModule.config().guardGear.crossbowDamageFactor : 1.0);
		int arrows = crossbow && GuardEquipment.enchantmentLevel(level, Enchantments.MULTISHOT, launcher) > 0 ? 3 : 1;
		double xd = target.getX() - villager.getX();
		double yd = target.getY(1.0 / 3.0) - (villager.getEyeY() - 0.1);
		double zd = target.getZ() - villager.getZ();
		double horizontal = Math.sqrt(xd * xd + zd * zd);
		villager.getLookControl().setLookAt(target, 30.0f, 30.0f);
		for (int i = 0; i < arrows; i++) {
			// Multishot: the side bolts leave 10 degrees to the left and to the right.
			double angle = i == 0 ? 0.0 : Math.toRadians(i == 1 ? -MULTISHOT_ANGLE : MULTISHOT_ANGLE);
			double x = xd * Math.cos(angle) - zd * Math.sin(angle);
			double z = xd * Math.sin(angle) + zd * Math.cos(angle);
			GuardArrow arrow = new GuardArrow(level, villager, launcher, damage, flame);
			arrow.shoot(x, yd + horizontal * 0.2, z, GuardArrow.SPEED, (float) cfg.bowInaccuracy);
			level.addFreshEntity(arrow);
		}
		level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), crossbow ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.ARROW_SHOOT,
				SoundSource.NEUTRAL, 1.0f, 1.0f / (villager.getRandom().nextFloat() * 0.4f + 0.8f));
		WorldUtil.swing(villager);
	}
}
