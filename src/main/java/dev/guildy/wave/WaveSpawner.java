package dev.guildy.wave;

import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Safe;
import dev.guildy.data.ChunkKey;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Spawn position search (border of the territory, loaded + entity-ticking chunks only, never loads chunks) and
 * creation of buffed wave mobs.
 */
final class WaveSpawner {
	/** Tag carried by every wave mob (restart cleanup). */
	static final String TAG = "guildy_wave";
	private static final Identifier HEALTH_ID = Identifier.fromNamespaceAndPath("guildy", "wave_health");
	private static final Identifier DAMAGE_ID = Identifier.fromNamespaceAndPath("guildy", "wave_damage");
	private static final Identifier FOLLOW_ID = Identifier.fromNamespaceAndPath("guildy", "wave_follow_range");
	private static final Identifier SCALE_ID = Identifier.fromNamespaceAndPath("guildy", "wave_boss_scale");
	private static final Identifier KNOCKBACK_ID = Identifier.fromNamespaceAndPath("guildy", "wave_boss_knockback");
	private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private WaveSpawner() {
	}

	static String guildTag(UUID guildId) {
		return TAG + "_" + guildId;
	}

	// ------------------------------------------------------------------ entity types

	static @Nullable EntityType<?> entityType(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		Identifier identifier = Identifier.tryParse(id.trim().toLowerCase(Locale.ROOT));
		if (identifier == null) {
			return null;
		}
		return BuiltInRegistries.ENTITY_TYPE.getOptional(identifier).orElse(null);
	}

	/** Living, summonable, non-misc entity types only (validated again at spawn: must create a Mob). */
	static boolean isValidEntity(String id) {
		EntityType<?> type = entityType(id);
		return type != null && type.canSummon() && type.getCategory() != MobCategory.MISC;
	}

	// ------------------------------------------------------------------ positions

	/** A spawn point near the outer edge of a random border chunk, walked toward the heart until loaded and ticking. */
	static @Nullable BlockPos findBorderPosition(ServerLevel level, WaveSession session, EntityType<?> type, int spawnHeight, RandomGenerator random) {
		long[] border = session.borderChunks();
		if (border.length == 0) {
			return null;
		}
		WaveConfig.Spawning cfg = WaveModule.config().spawning;
		long key = border[random.nextInt(border.length)];
		int[] sides = new int[4];
		int count = 0;
		if (!session.guild().owns(ChunkKey.offset(key, 1, 0))) {
			sides[count++] = 0;
		}
		if (!session.guild().owns(ChunkKey.offset(key, -1, 0))) {
			sides[count++] = 1;
		}
		if (!session.guild().owns(ChunkKey.offset(key, 0, 1))) {
			sides[count++] = 2;
		}
		if (!session.guild().owns(ChunkKey.offset(key, 0, -1))) {
			sides[count++] = 3;
		}
		if (count == 0) {
			return null;
		}
		int side = sides[random.nextInt(count)];
		int offset = cfg.edgeOffsetMin + random.nextInt(cfg.edgeOffsetMax - cfg.edgeOffsetMin + 1);
		int along = random.nextInt(16);
		int minX = ChunkKey.x(key) << 4;
		int minZ = ChunkKey.z(key) << 4;
		int x;
		int z;
		switch (side) {
			case 0 -> {
				x = minX + 15 + offset;
				z = minZ + along;
			}
			case 1 -> {
				x = minX - offset;
				z = minZ + along;
			}
			case 2 -> {
				x = minX + along;
				z = minZ + 15 + offset;
			}
			default -> {
				x = minX + along;
				z = minZ - offset;
			}
		}
		return walkToTickingSurface(level, session, type, spawnHeight, x, z, cfg.walkStep);
	}

	private static @Nullable BlockPos walkToTickingSurface(ServerLevel level, WaveSession session, EntityType<?> type, int spawnHeight,
			int startX, int startZ, int step) {
		BlockPos heart = session.heart();
		double hx = heart.getX() + 0.5;
		double hz = heart.getZ() + 0.5;
		double px = startX + 0.5;
		double pz = startZ + 0.5;
		for (int i = 0; i < 64; i++) {
			double dx = hx - px;
			double dz = hz - pz;
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (dist < minHeartDistance()) {
				return null;
			}
			int bx = Mth.floor(px);
			int bz = Mth.floor(pz);
			if (isEntityTicking(level, bx, bz)) {
				return surface(level, type, spawnHeight, bx, bz);
			}
			px += dx / dist * step;
			pz += dz / dist * step;
		}
		return null;
	}

	/** A position close to the group anchor (ground position of the group's first mob): the group arrives together. */
	static @Nullable BlockPos findNear(ServerLevel level, WaveSession session, BlockPos anchor, EntityType<?> type, int spawnHeight, RandomGenerator random) {
		int spread = WaveModule.config().spawning.groupSpread;
		int bx = anchor.getX() + (spread == 0 ? 0 : random.nextInt(spread * 2 + 1) - spread);
		int bz = anchor.getZ() + (spread == 0 ? 0 : random.nextInt(spread * 2 + 1) - spread);
		BlockPos heart = session.heart();
		double dx = heart.getX() - bx;
		double dz = heart.getZ() - bz;
		double min = minHeartDistance();
		if (dx * dx + dz * dz < min * min || !isEntityTicking(level, bx, bz)) {
			return null;
		}
		BlockPos pos = surface(level, type, spawnHeight, bx, bz);
		// The anchor is a ground position: compare ground heights (avoids spawning on roofs / cliffs next to it).
		if (pos == null || Math.abs(pos.getY() - Math.max(0, spawnHeight) - anchor.getY()) > 8) {
			return null;
		}
		return pos;
	}

	/** Spawn points are never closer than this to the heart (blocks, at least 1). */
	private static double minHeartDistance() {
		return Math.max(1, WaveModule.config().spawning.minHeartDistance);
	}

	/** Loaded (never triggers a chunk load) AND entity-ticking. */
	static boolean isEntityTicking(ServerLevel level, int blockX, int blockZ) {
		LevelChunk chunk = level.getChunkSource().getChunkNow(blockX >> 4, blockZ >> 4);
		if (chunk == null) {
			return false;
		}
		BlockPos pos = new BlockPos(blockX, 64, blockZ);
		return level.isPositionEntityTicking(pos) && level.areEntitiesLoaded(ChunkKey.ofBlock(blockX, blockZ));
	}

	/** Surface position (heightmap) with a solid non-liquid floor and room for the mob's box, or null. */
	private static @Nullable BlockPos surface(ServerLevel level, EntityType<?> type, int spawnHeight, int x, int z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (y <= level.getMinY() + 1 || y + spawnHeight >= level.getMaxY() - 4) {
			return null;
		}
		BlockPos floor = new BlockPos(x, y - 1, z);
		BlockState floorState = level.getBlockState(floor);
		if (!floorState.getFluidState().isEmpty() || !floorState.isFaceSturdy(level, floor, Direction.UP) || type.isBlockDangerous(floorState)) {
			return null;
		}
		int spawnY = y + Math.max(0, spawnHeight);
		AABB box = type.getSpawnAABB(x + 0.5, spawnY, z + 0.5);
		if (!level.getWorldBorder().isWithinBounds(box) || !level.noCollision(box) || level.containsAnyLiquid(box)) {
			return null;
		}
		return new BlockPos(x, spawnY, z);
	}

	static boolean farFromPlayers(ServerLevel level, BlockPos pos, double minDistance) {
		if (minDistance <= 0) {
			return true;
		}
		double max = minDistance * minDistance;
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) < max) {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ creation

	/**
	 * Creates, buffs, tags, tracks and adds the mob. Tracking is registered BEFORE adding it to the level so that the
	 * restart cleanup (ALLOW_LOAD) accepts it.
	 *
	 * @return the spawned mob, or null if the entity could not be created (invalid type, peaceful...)
	 */
	static @Nullable Mob spawn(ServerLevel level, WaveSession session, WavePlanner.PlannedMob planned, BlockPos pos, RandomGenerator random) {
		EntityType<?> type = entityType(planned.entity());
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
		WaveConfig cfg = WaveModule.config();
		float yaw = random.nextFloat() * 360.0F;
		mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
		// No chicken jockeys (they would add untracked entities); babies keep the vanilla odds.
		SpawnGroupData group = mob instanceof Zombie ? new Zombie.ZombieGroupData(Zombie.getSpawnAsBabyOdds(level.getRandom()), false) : null;
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, group);
		if (mob.isVehicle()) {
			mob.ejectPassengers(); // spider jockeys: the rider is never added to the level
		}
		if (mob.isPassenger()) {
			mob.stopRiding();
		}

		mob.addTag(TAG);
		mob.addTag(guildTag(session.guildId()));
		mob.setPersistenceRequired();
		mob.setCanPickUpLoot(false); // picked-up player items would vanish when the horde is removed
		if (mob instanceof AbstractPiglin piglin) {
			piglin.setImmuneToZombification(true);
		}
		if (mob instanceof Hoglin hoglin) {
			hoglin.setImmuneToZombification(true);
		}
		if (mob instanceof Enderman) {
			// No block stealing during the raid (the block would be lost when the horde is removed).
			mob.removeAllGoals(goal -> {
				String name = goal.getClass().getSimpleName();
				return name.contains("TakeBlock") || name.contains("LeaveBlock");
			});
		}
		AttributeInstance reinforcements = mob.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
		if (reinforcements != null) {
			reinforcements.removeModifiers();
			reinforcements.setBaseValue(0); // reinforcements would be untracked
		}

		applyBuffs(mob, planned, session.difficulty(), session.modifiers());
		if (planned.gear()) {
			equip(mob, cfg.mobs, session.difficulty(), planned.isElite() || planned.isBoss(), random);
		}
		if (planned.isElite()) {
			mob.setCustomName(Component.empty().append(Lang.get("wave.elite.prefix"))
					.append(type.getDescription().copy().withStyle(ChatFormatting.GOLD)));
		} else if (planned.isBoss() && planned.boss() != null) {
			mob.setCustomName(Component.literal(Lang.colorize(planned.boss().name)));
			mob.setCustomNameVisible(true);
		}
		if (session.isTimed() && level.isBrightOutside()) {
			// Admin test / event waves by day: undead would burn before reaching the base.
			mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, session.durationSeconds() * 20 + 200, 0, false, false));
		}
		mob.setHealth(mob.getMaxHealth());

		session.track(mob, planned);
		boolean added = false;
		try {
			added = level.addFreshEntity(mob);
		} finally {
			if (!added) {
				session.untrack(mob.getUUID());
			}
		}
		if (!added) {
			return null;
		}
		Safe.run("wave.spawn", () -> {
			attachSteering(mob, session);
			if (cfg.spawning.spawnEffects) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + 0.6, mob.getZ(), 10, 0.3, 0.6, 0.3, 0.02);
				level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + 0.6, mob.getZ(), 5, 0.3, 0.5, 0.3, 0.02);
			}
		});
		return mob;
	}

	/**
	 * Goal-based ground mobs get the move-to-heart goal; brain-based / flying / cube mobs are steered by the session.
	 * Idempotent: also called when a tracked mob is reloaded from its chunk (goals are not persisted).
	 */
	static void attachSteering(Mob mob, WaveSession session) {
		if (!(mob instanceof PathfinderMob pathfinder) || mob instanceof AbstractCubeMob || !mob.getBrain().isBrainDead()) {
			return;
		}
		for (WrappedGoal wrapped : pathfinder.getGoalSelector().getAvailableGoals()) {
			if (wrapped.getGoal() instanceof MoveToHeartGoal) {
				return;
			}
		}
		pathfinder.getGoalSelector().addGoal(MoveToHeartGoal.PRIORITY, new MoveToHeartGoal(pathfinder, session));
	}

	/**
	 * Difficulty / elite / boss / event attribute modifiers (+ glowing). Idempotent (fixed modifier ids): also re-applied to
	 * a mob created by a conversion (zombie drowning...), which does not inherit attribute modifiers.
	 */
	static void applyBuffs(Mob mob, WavePlanner.PlannedMob planned, double difficulty, NightModifiers modifiers) {
		WaveConfig cfg = WaveModule.config();
		double health = WaveFormula.healthMultiplier(cfg.mobs, cfg.difficulty.base, difficulty) * modifiers.healthMultiplier();
		double damage = WaveFormula.damageMultiplier(cfg.mobs, cfg.difficulty.base, difficulty) * modifiers.damageMultiplier();
		if (planned.isElite()) {
			health *= cfg.mobs.eliteHealthMultiplier;
			damage *= cfg.mobs.eliteDamageMultiplier;
			if (cfg.mobs.eliteGlowing) {
				mob.setGlowingTag(true);
			}
		}
		WaveConfig.BossDef boss = planned.boss();
		if (planned.isBoss() && boss != null) {
			health *= boss.healthMultiplier;
			damage *= boss.damageMultiplier;
			if (cfg.mobs.bossGlowing) {
				mob.setGlowingTag(true);
			}
			AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
			if (scale != null && cfg.mobs.bossScale != 1.0) {
				scale.addOrReplacePermanentModifier(new AttributeModifier(SCALE_ID, cfg.mobs.bossScale - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
			AttributeInstance knockback = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
			if (knockback != null && cfg.mobs.bossKnockbackResistance > 0) {
				knockback.addOrReplacePermanentModifier(new AttributeModifier(KNOCKBACK_ID, cfg.mobs.bossKnockbackResistance, AttributeModifier.Operation.ADD_VALUE));
			}
		}
		multiply(mob, Attributes.MAX_HEALTH, HEALTH_ID, health);
		multiply(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, damage);
		AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null) {
			follow.removeModifier(FOLLOW_ID);
			if (follow.getValue() < cfg.mobs.followRange) {
				follow.addPermanentModifier(new AttributeModifier(FOLLOW_ID, cfg.mobs.followRange - follow.getValue(), AttributeModifier.Operation.ADD_VALUE));
			}
		}
	}

	private static void multiply(Mob mob, Holder<Attribute> attribute, Identifier id, double multiplier) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance == null || Math.abs(multiplier - 1.0) < 1.0E-4) {
			return;
		}
		instance.addOrReplacePermanentModifier(new AttributeModifier(id, multiplier - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	private static void equip(Mob mob, WaveConfig.MobSettings cfg, double difficulty, boolean special, RandomGenerator random) {
		double armorChance = WaveFormula.armorChance(cfg, difficulty, special);
		int armorTier = WaveFormula.gearTierIndex(cfg.armorTiers, difficulty);
		if (armorTier >= 0) {
			for (EquipmentSlot slot : ARMOR_SLOTS) {
				if (!mob.getItemBySlot(slot).isEmpty() || random.nextDouble() >= armorChance) {
					continue;
				}
				Item item = ItemUtil.item(tierMaterial(cfg.armorTiers, armorTier, random) + armorSuffix(slot));
				if (item != null) {
					mob.setItemSlot(slot, new ItemStack(item));
					mob.setDropChance(slot, 0.0F);
				}
			}
		}
		int weaponTier = WaveFormula.gearTierIndex(cfg.weaponTiers, difficulty);
		if (weaponTier >= 0 && mob.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()
				&& random.nextDouble() < WaveFormula.weaponChance(cfg, difficulty, special)) {
			Item item = ItemUtil.item(tierMaterial(cfg.weaponTiers, weaponTier, random) + "_sword");
			if (item != null) {
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
				mob.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
			}
		}
	}

	/** Highest reached tier, with a 25 % chance of the tier below for variety. */
	private static String tierMaterial(List<WaveConfig.GearTier> tiers, int index, RandomGenerator random) {
		int i = index > 0 && random.nextDouble() < 0.25 ? index - 1 : index;
		return tiers.get(i).material;
	}

	private static String armorSuffix(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> "_helmet";
			case CHEST -> "_chestplate";
			case LEGS -> "_leggings";
			default -> "_boots";
		};
	}
}
