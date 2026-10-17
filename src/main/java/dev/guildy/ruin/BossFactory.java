package dev.guildy.ruin;

import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.ruin.RuinConfig.BossDef;
import dev.guildy.ruin.RuinConfig.EffectDef;
import dev.guildy.ruin.RuinConfig.MinionDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * Creation of the ruin guardians and their escorts: vanilla mobs with buffed attributes, equipment that never drops,
 * permanent effects, the {@value #TAG} entity tag (refused at load without a running fight) and no despawning.
 */
final class BossFactory {
	/** Tag of every ruin mob (guardian and escort). */
	static final String TAG = "guildy_ruin";
	/** Extra tag of the guardian. */
	static final String TAG_BOSS = "guildy_ruin_boss";
	private static final Identifier MINION_HEALTH = Identifier.fromNamespaceAndPath("guildy", "ruin_minion_health");
	private static final Identifier MINION_DAMAGE = Identifier.fromNamespaceAndPath("guildy", "ruin_minion_damage");
	private static final Identifier THREAT_HEALTH = Identifier.fromNamespaceAndPath("guildy", "ruin_threat_health");
	private static final Identifier THREAT_DAMAGE = Identifier.fromNamespaceAndPath("guildy", "ruin_threat_damage");
	private static final Identifier THREAT_ARMOR = Identifier.fromNamespaceAndPath("guildy", "ruin_threat_armor");
	private static final Identifier RISEN_SCALE = Identifier.fromNamespaceAndPath("guildy", "ruin_risen_scale");
	private static final Identifier RISEN_SPEED = Identifier.fromNamespaceAndPath("guildy", "ruin_risen_speed");
	private static final Identifier RISEN_DAMAGE = Identifier.fromNamespaceAndPath("guildy", "ruin_risen_damage");

	private BossFactory() {
	}

	/** Spawns the guardian (tracked by {@code track} BEFORE it enters the level, so that the load check accepts it). */
	static @Nullable Mob spawnBoss(ServerLevel level, BossDef def, BlockPos pos, Predicate<Mob> track) {
		return spawnBoss(level, def, pos, ThreatRules.Scaling.NONE, track);
	}

	/** Spawns the guardian, made stronger by the threat level of its ruin. */
	static @Nullable Mob spawnBoss(ServerLevel level, BossDef def, BlockPos pos, ThreatRules.Scaling scaling, Predicate<Mob> track) {
		Mob mob = create(level, def.entity, pos);
		if (mob == null) {
			return null;
		}
		applyBoss(mob, def);
		applyThreat(mob, scaling);
		equip(mob, def.equipment);
		mob.addTag(TAG_BOSS);
		mob.setCustomName(Component.literal(Lang.colorize(def.name)));
		mob.setCustomNameVisible(true);
		mob.setHealth(mob.getMaxHealth());
		return add(level, mob, track);
	}

	/** Spawns one escort mob of a random type. */
	static @Nullable Mob spawnMinion(ServerLevel level, MinionDef def, BlockPos pos, RandomGenerator random, Predicate<Mob> track) {
		String entity = pickType(def.types, random);
		if (entity == null) {
			return null;
		}
		Mob mob = create(level, entity, pos);
		if (mob == null) {
			return null;
		}
		multiply(mob, Attributes.MAX_HEALTH, MINION_HEALTH, def.healthMultiplier);
		multiply(mob, Attributes.ATTACK_DAMAGE, MINION_DAMAGE, def.damageMultiplier);
		applyEffects(mob, def.effects);
		equip(mob, def.equipment);
		mob.setHealth(mob.getMaxHealth());
		return add(level, mob, track);
	}

	private static @Nullable Mob create(ServerLevel level, String entityId, BlockPos pos) {
		EntityType<?> type = RuinRegistries.entity(entityId);
		if (type == null) {
			return null;
		}
		Entity entity = type.create(level, EntitySpawnReason.EVENT);
		if (!(entity instanceof Mob mob)) {
			if (entity != null) {
				entity.discard();
			}
			return null;
		}
		float yaw = level.getRandom().nextFloat() * 360.0F;
		mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
		// No babies and no chicken jockeys (an extra untracked entity).
		SpawnGroupData group = mob instanceof Zombie ? new Zombie.ZombieGroupData(false, false) : null;
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, group);
		if (mob.isVehicle()) {
			mob.ejectPassengers();
		}
		if (mob.isPassenger()) {
			mob.stopRiding();
		}
		mob.addTag(TAG);
		mob.setPersistenceRequired();
		mob.setCanPickUpLoot(false);
		if (mob instanceof AbstractPiglin piglin) {
			piglin.setImmuneToZombification(true);
		}
		if (mob instanceof Hoglin hoglin) {
			hoglin.setImmuneToZombification(true);
		}
		if (mob instanceof Enderman) {
			mob.removeAllGoals(goal -> {
				String name = goal.getClass().getSimpleName();
				return name.contains("TakeBlock") || name.contains("LeaveBlock");
			});
		}
		AttributeInstance reinforcements = mob.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
		if (reinforcements != null) {
			reinforcements.removeModifiers();
			reinforcements.setBaseValue(0);
		}
		for (EquipmentSlot slot : EquipmentSlot.VALUES) {
			// Vanilla gear from finalizeSpawn included: the guardians never drop their equipment.
			mob.setDropChance(slot, 0.0F);
		}
		return mob;
	}

	private static @Nullable Mob add(ServerLevel level, Mob mob, Predicate<Mob> track) {
		sunProof(mob);
		if (!track.test(mob)) {
			mob.discard();
			return null;
		}
		boolean added = level.addFreshEntity(mob);
		return added ? mob : null;
	}

	/**
	 * Some arenas are open to the sky (the frost bastion's courtyard): equipment never wears out (a helmet protects an
	 * undead from the sun only while it lasts) and an undead without a helmet gets fire resistance.
	 */
	private static void sunProof(Mob mob) {
		for (EquipmentSlot slot : EquipmentSlot.VALUES) {
			ItemStack stack = mob.getItemBySlot(slot);
			if (!stack.isEmpty() && stack.isDamageableItem()) {
				stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
			}
		}
		if (mob.is(EntityTypeTags.BURN_IN_DAYLIGHT) && mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
			mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		}
	}

	/**
	 * Guardian statistics (base values, idempotent: re-applied after a conversion such as a husk drowning, which does not
	 * keep attributes) and permanent effects.
	 */
	static void applyBoss(Mob mob, BossDef def) {
		setBase(mob, Attributes.MAX_HEALTH, def.health);
		setBase(mob, Attributes.ATTACK_DAMAGE, def.damage);
		setBase(mob, Attributes.ARMOR, def.armor);
		setBase(mob, Attributes.ARMOR_TOUGHNESS, def.armorToughness);
		if (def.speed > 0) {
			setBase(mob, Attributes.MOVEMENT_SPEED, def.speed);
		}
		setBase(mob, Attributes.KNOCKBACK_RESISTANCE, def.knockbackResistance);
		setBase(mob, Attributes.SCALE, def.scale);
		AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null && follow.getBaseValue() < def.followRange) {
			follow.setBaseValue(def.followRange);
		}
		applyEffects(mob, def.effects);
	}

	/**
	 * Threat level of the ruin: more health and damage (multiplied, on top of the configured values) and armor points
	 * (vanilla cap 30). Permanent modifiers, idempotent (re-applied after a conversion).
	 */
	static void applyThreat(Mob mob, ThreatRules.Scaling scaling) {
		multiply(mob, Attributes.MAX_HEALTH, THREAT_HEALTH, scaling.health());
		multiply(mob, Attributes.ATTACK_DAMAGE, THREAT_DAMAGE, scaling.damage());
		AttributeInstance armor = mob.getAttribute(Attributes.ARMOR);
		if (armor != null && scaling.armor() > 1.0E-4) {
			armor.addOrReplacePermanentModifier(new AttributeModifier(THREAT_ARMOR, scaling.armor(), AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** The risen guardian: bigger, faster, stronger (permanent modifiers, idempotent). */
	static void applyRisen(Mob mob, double scale, double speedBonus, double damageBonus) {
		multiply(mob, Attributes.SCALE, RISEN_SCALE, scale);
		multiply(mob, Attributes.MOVEMENT_SPEED, RISEN_SPEED, 1.0 + speedBonus);
		multiply(mob, Attributes.ATTACK_DAMAGE, RISEN_DAMAGE, 1.0 + damageBonus);
	}

	private static void applyEffects(Mob mob, java.util.List<EffectDef> effects) {
		for (EffectDef effect : effects) {
			Holder<MobEffect> holder = RuinRegistries.effect(effect.effect);
			if (holder != null) {
				mob.addEffect(new MobEffectInstance(holder, MobEffectInstance.INFINITE_DURATION, effect.amplifier, false, false));
			}
		}
	}

	private static void equip(Mob mob, Map<String, String> equipment) {
		for (Map.Entry<String, String> entry : equipment.entrySet()) {
			Item item = ItemUtil.item(entry.getValue());
			EquipmentSlot slot = slot(entry.getKey());
			if (item != null && slot != null) {
				mob.setItemSlot(slot, new ItemStack(item));
				mob.setDropChance(slot, 0.0F);
			}
		}
	}

	private static @Nullable EquipmentSlot slot(String name) {
		return switch (name) {
			case "mainhand" -> EquipmentSlot.MAINHAND;
			case "offhand" -> EquipmentSlot.OFFHAND;
			case "head" -> EquipmentSlot.HEAD;
			case "chest" -> EquipmentSlot.CHEST;
			case "legs" -> EquipmentSlot.LEGS;
			case "feet" -> EquipmentSlot.FEET;
			default -> null;
		};
	}

	private static void setBase(Mob mob, Holder<Attribute> attribute, double value) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	private static void multiply(Mob mob, Holder<Attribute> attribute, Identifier id, double multiplier) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance == null || Math.abs(multiplier - 1.0) < 1.0E-4) {
			return;
		}
		instance.addOrReplacePermanentModifier(new AttributeModifier(id, multiplier - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	private static @Nullable String pickType(Map<String, Integer> types, RandomGenerator random) {
		int total = 0;
		for (int weight : types.values()) {
			total += Math.max(0, weight);
		}
		if (total <= 0) {
			return null;
		}
		int roll = random.nextInt(total);
		for (Map.Entry<String, Integer> entry : types.entrySet()) {
			roll -= Math.max(0, entry.getValue());
			if (roll < 0) {
				return entry.getKey();
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ positions

	/** A spot where a mob of {@code entityId} fits, standing on a sturdy block, within {@code radius} of {@code around}. */
	static @Nullable BlockPos standable(ServerLevel level, BlockPos around, String entityId, int radius) {
		EntityType<?> type = RuinRegistries.entity(entityId);
		if (type == null) {
			return null;
		}
		for (int r = 0; r <= radius; r++) {
			for (int dy : new int[] {0, 1, -1, 2, -2}) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
							continue;
						}
						BlockPos pos = around.offset(dx, dy, dz);
						if (fits(level, type, pos)) {
							return pos;
						}
					}
				}
			}
		}
		return null;
	}

	static boolean fits(ServerLevel level, EntityType<?> type, BlockPos pos) {
		if (!level.isLoaded(pos) || !level.isInsideBuildHeight(pos)) {
			return false;
		}
		BlockPos below = pos.below();
		BlockState floor = level.getBlockState(below);
		if (!floor.isFaceSturdy(level, below, Direction.UP) || !floor.getFluidState().isEmpty() || type.isBlockDangerous(floor)) {
			return false;
		}
		AABB box = type.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		return level.noCollision(box) && !level.containsAnyLiquid(box);
	}
}
