package dev.guildy.test;

import dev.guildy.ruin.CursedFights;
import dev.guildy.ruin.CursedItems;
import dev.guildy.ruin.Encounters;
import dev.guildy.ruin.IncantationItems;
import dev.guildy.ruin.Incantations;
import dev.guildy.ruin.RuinConfig;
import dev.guildy.ruin.RuinIndex;
import dev.guildy.ruin.RuinModule;
import dev.guildy.ruin.RuinRecord;
import dev.guildy.ruin.RuinState;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/**
 * The dread form of the guardians and the Avatar, on the test ruin of {@link RuinGameTests}: a guardian whose intended
 * health (3000) is beyond the vanilla cap of 1024 dies after exactly 3000 damage; at Menace VII the guardian takes its dread
 * form (effective health x2.5 shown on the bar, +50 % damage on the players after their armour, its telegraphed dread
 * abilities: grasping graves warn, then root and hurt), at Menace VI nothing of it; a cursed ritual with a Nether star in the
 * circle consumes it and awakens the Avatar (red bar, red sky, its health floor), without the star the cursed guardian, and a
 * normal ritual leaves the star alone; the Avatar's Nether rifts let Nether mobs out; every one of the 29 dread attacks
 * starts telegraphed and runs to its end without an error.
 */
@SuppressWarnings("removal")
public class RuinDreadGameTests {
	private static final Identifier ROOT_SPEED = Identifier.fromNamespaceAndPath("guildy", "ruin_dread_root_speed");
	private static final Identifier BOOST = Identifier.fromNamespaceAndPath("guildy-test", "dread_test_health");

	/** A placed, indexed test ruin with its boss marker, at threat {@code threat}. */
	private static RuinRecord ruin(GameTestHelper helper, int threat) {
		ServerLevel level = helper.getLevel();
		BlockPos center = TestSupport.region(level, 0);
		TestSupport.forceChunks(level, center, 3);
		StructureStart start = RuinGameTests.generate(level, center);
		RuinGameTests.place(level, start);
		RuinIndex.processPending(level.getServer());
		RuinRecord ruin = RuinIndex.register(level, start);
		helper.assertTrue(ruin != null && ruin.boss != null, "test ruin indexed with its boss marker");
		helper.assertTrue(ruin.state == RuinState.DORMANT, "dormant ruin");
		Encounters.setThreat(ruin, threat);
		return ruin;
	}

	/** A copy of the generic guardian: no escort, no normal ability (its dread abilities kept), never rises. */
	private static RuinConfig.RuinDef guardian() {
		RuinConfig.RuinDef def = RuinModule.config().def("test_ruin", false).copy();
		def.resetSeconds = 60;
		def.respawnDays = 7;
		def.fastKillSeconds = 0;
		def.abilities.clear();
		def.minions.types.clear();
		def.rewards.goldMin = 10;
		def.rewards.goldMax = 10;
		def.rewards.goldPerEscort = 0;
		def.rewards.lootTable = "";
		def.rewards.items.clear();
		return def;
	}

	/** A player the attacks really hit (client "loaded"), sturdier than a plain one. */
	private static ServerPlayer target(GameTestHelper helper, RuinRecord ruin, boolean resistant) {
		ServerPlayer player = TestSupport.survivor(helper, ruin.boss.offset(3, 0, 0));
		if (resistant) {
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4, false, false));
		}
		for (int i = 0; i < 200 && !player.connection.hasClientLoaded(); i++) {
			player.connection.tickClientLoadTimeout();
		}
		helper.assertTrue(player.connection.hasClientLoaded(), "mock player can be hit");
		return player;
	}

	private static String bar(RuinRecord ruin) {
		Component title = Encounters.barTitle(ruin);
		return title == null ? "" : title.getString();
	}

	private static void away(ServerLevel level, ServerPlayer player, RuinRecord ruin) {
		player.teleportTo(level, ruin.boss.getX() + 300.5, ruin.boss.getY() + 1, ruin.boss.getZ() + 0.5, Set.of(), 0, 0, true);
	}

	/** Damage of a mob's hit on a player at the level's difficulty (vanilla scaling, before armour). */
	private static float scaled(ServerLevel level, float amount) {
		Difficulty difficulty = level.getDifficulty();
		return switch (difficulty) {
			case PEACEFUL -> 0;
			case EASY -> Math.min(amount / 2.0F + 1.0F, amount);
			case HARD -> amount * 1.5F;
			default -> amount;
		};
	}

	// ================================================================== intended health beyond the cap

	@GameTest(maxTicks = 300)
	public void guardianWithIntendedHealthOf3000DiesAfterExactly3000Damage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper, 1);
		RuinConfig.RuinDef def = guardian();
		def.boss.health = 3000;
		def.dreadAbilities.clear();
		ServerPlayer player = target(helper, ruin, true);
		Mob[] boss = new Mob[1];
		helper.startSequence()
				.thenExecute(() -> helper.assertTrue(Encounters.awaken(level, ruin, def, false) == null, "guardian awakened"))
				.thenWaitUntil(() -> {
					boss[0] = Encounters.boss(ruin);
					helper.assertTrue(boss[0] != null, "waiting for the guardian");
				})
				.thenExecute(() -> {
					Mob b = boss[0];
					b.setNoAi(true);
					for (EquipmentSlot slot : EquipmentSlot.VALUES) {
						b.setItemSlot(slot, ItemStack.EMPTY);
					}
					helper.assertTrue(Math.abs(b.getMaxHealth() - 1024) < 0.01, "the game caps the real max health at 1024: " + b.getMaxHealth());
					helper.assertTrue(Math.abs(Encounters.vitality(ruin) - 3000 / 1024.0) < 1.0E-6, "vitality 3000/1024: " + Encounters.vitality(ruin));
					double[] health = Encounters.effectiveHealth(ruin);
					helper.assertTrue(health != null && Math.abs(health[1] - 3000) < 0.01 && Math.abs(health[0] - 3000) < 0.01,
							"3000 effective health");
					helper.assertTrue(bar(ruin).contains("3000"), "the bar shows the intended health: " + bar(ruin));
					helper.assertFalse(Encounters.dreadActive(ruin), "Menace I: no dread form");
					// Magic damage: no armour, no enchantment in the way. 2929.6875 + 70.3125 = 3000.
					b.hurtServer(level, level.damageSources().magic(), 2929.6875F);
					helper.assertTrue(b.isAlive() && Encounters.isActive(ruin), "alive after 2929.6875 of 3000");
					helper.assertTrue(Math.abs(b.getHealth() - 24.0F) < 1.0E-3, "24 real health left (70.3125 effective): " + b.getHealth());
					double[] left = Encounters.effectiveHealth(ruin);
					helper.assertTrue(left != null && Math.abs(left[0] - 70.3125) < 0.01, "70.3125 effective left: " + (left == null ? -1 : left[0]));
					b.damageCooldownTime = 0;
					b.hurtServer(level, level.damageSources().magic(), 70.3125F);
					helper.assertTrue(b.isDeadOrDying(), "dead after exactly 3000 damage: " + b.getHealth());
					helper.assertTrue(ruin.state == RuinState.CLEARED, "victory");
				})
				.thenExecute(() -> away(level, player, ruin))
				.thenSucceed();
	}

	// ================================================================== Menace VII

	@GameTest(maxTicks = 400)
	public void threatSevenGuardianTakesItsDreadForm(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper, 7);
		RuinConfig.RuinDef def = guardian();
		helper.assertTrue(def.dreadAbilities.stream().anyMatch(a -> a.type.equals("grasping_graves")), "the generic guardian's dread abilities");
		ServerPlayer player = target(helper, ruin, false);
		player.getAttribute(Attributes.MAX_HEALTH).addOrReplacePermanentModifier(new AttributeModifier(BOOST, 200, AttributeModifier.Operation.ADD_VALUE));
		player.setHealth(player.getMaxHealth());
		Mob[] boss = new Mob[1];
		float[] health = new float[1];
		helper.startSequence()
				.thenExecute(() -> helper.assertTrue(Encounters.awaken(level, ruin, def, false) == null, "guardian awakened"))
				.thenWaitUntil(() -> {
					boss[0] = Encounters.boss(ruin);
					helper.assertTrue(boss[0] != null, "waiting for the guardian");
				})
				.thenExecute(() -> {
					Mob b = boss[0];
					b.setNoAi(true);
					helper.assertTrue(Encounters.dreadActive(ruin), "Menace VII: the dread form");
					// 240 (generic tier 2) x 2.2 (Menace VII) = 528 real, x2.5 dread = 1320 effective.
					double real = def.boss.health * 2.2;
					helper.assertTrue(Math.abs(b.getMaxHealth() - real) < 0.5, "real max health " + b.getMaxHealth());
					helper.assertTrue(Math.abs(Encounters.vitality(ruin) - 2.5) < 1.0E-4, "x2.5 health: " + Encounters.vitality(ruin));
					double[] effective = Encounters.effectiveHealth(ruin);
					helper.assertTrue(effective != null && Math.abs(effective[1] - real * 2.5) < 1, "effective max health " + (effective == null ? -1 : effective[1]));
					helper.assertTrue(Math.abs(Encounters.playerDamage(ruin) - 1.5) < 1.0E-6, "+50 % damage on the players");
					helper.assertTrue(bar(ruin).contains("Menace VII") && bar(ruin).contains("Forme redoutable") && bar(ruin).contains("1320"),
							"boss bar: " + bar(ruin));
					String name = b.getCustomName() == null ? "" : b.getCustomName().getString();
					helper.assertTrue(name.contains("Redoutable"), "named: " + name);
					// Its hit on a player (no armour): +50 %.
					float before = player.getHealth();
					player.hurtServer(level, level.damageSources().mobAttack(b), 4.0F);
					float lost = before - player.getHealth();
					float expected = scaled(level, 4.0F) * 1.5F;
					helper.assertTrue(Math.abs(lost - expected) < 0.01F, "a hit of 4 takes " + expected + ", took " + lost);
					player.setHealth(player.getMaxHealth());
					player.removeAllEffects();
					health[0] = player.getHealth();
					helper.assertTrue(Encounters.forceDread(ruin, "grasping_graves"), "the grasping graves");
					helper.assertTrue(Boolean.TRUE.equals(Encounters.dreadWarning(ruin, "grasping_graves")), "telegraphed first");
				})
				.thenIdle(10)
				.thenExecute(() -> {
					helper.assertTrue(Boolean.TRUE.equals(Encounters.dreadWarning(ruin, "grasping_graves")), "still a warning");
					helper.assertTrue(player.getHealth() >= health[0] - 0.01F, "nothing yet during the warning");
					helper.assertFalse(player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(ROOT_SPEED), "not rooted yet");
					helper.assertTrue(Encounters.dreadRunning(ruin).contains("grasping_graves"), "the graves are drawn");
				})
				.thenIdle(25)
				.thenExecute(() -> {
					helper.assertTrue(player.getHealth() < health[0], "the hands strike: " + health[0] + " -> " + player.getHealth());
					helper.assertTrue(player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(ROOT_SPEED), "rooted by the hands");
					helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) < 1.0E-6, "cannot walk");
				})
				.thenWaitUntil(() -> helper.assertFalse(player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(ROOT_SPEED), "waiting for the root to end"))
				.thenExecute(() -> {
					helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0, "free again");
					away(level, player, ruin);
					Encounters.reset(ruin);
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 300)
	public void threatSixGuardianHasNoDreadForm(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper, 6);
		RuinConfig.RuinDef def = guardian();
		ServerPlayer player = target(helper, ruin, false);
		player.getAttribute(Attributes.MAX_HEALTH).addOrReplacePermanentModifier(new AttributeModifier(BOOST, 200, AttributeModifier.Operation.ADD_VALUE));
		player.setHealth(player.getMaxHealth());
		Mob[] boss = new Mob[1];
		helper.startSequence()
				.thenExecute(() -> helper.assertTrue(Encounters.awaken(level, ruin, def, false) == null, "guardian awakened"))
				.thenWaitUntil(() -> {
					boss[0] = Encounters.boss(ruin);
					helper.assertTrue(boss[0] != null, "waiting for the guardian");
				})
				.thenExecute(() -> {
					Mob b = boss[0];
					b.setNoAi(true);
					helper.assertFalse(Encounters.dreadActive(ruin), "Menace VI: no dread form");
					helper.assertTrue(Encounters.dreadAbilities(ruin).isEmpty(), "no dread ability");
					helper.assertTrue(Math.abs(Encounters.vitality(ruin) - 1.0) < 1.0E-9, "its real health (480, under the cap)");
					helper.assertTrue(Math.abs(Encounters.playerDamage(ruin) - 1.0) < 1.0E-9, "no bonus on the players");
					helper.assertTrue(bar(ruin).contains("Menace VI") && !bar(ruin).contains("redoutable"), "boss bar: " + bar(ruin));
					String name = b.getCustomName() == null ? "" : b.getCustomName().getString();
					helper.assertFalse(name.contains("Redoutable"), "named: " + name);
					float before = player.getHealth();
					player.hurtServer(level, level.damageSources().mobAttack(b), 4.0F);
					float lost = before - player.getHealth();
					helper.assertTrue(Math.abs(lost - scaled(level, 4.0F)) < 0.01F, "a plain hit: " + lost);
					away(level, player, ruin);
					Encounters.reset(ruin);
				})
				.thenSucceed();
	}

	// ================================================================== every dread attack

	/** The 29 dread attacks, by groups of attacks that may run together. */
	private static final List<List<String>> GROUPS = List.of(
			List.of("grasping_graves", "bone_spikes", "arcane_barrage", "quicksand"),
			List.of("bone_prison", "gravity_well", "mummy_curse", "ice_spikes"),
			List.of("sandstorm", "frozen_ground", "feather_storm", "poison_rain"),
			List.of("dive_bomb", "stunning_roar", "lava_geysers", "molten_ring"),
			List.of("blizzard_dome", "solar_beam", "magma_meteors", "soul_chains"),
			List.of("pounce_chain", "wither_aura", "soul_eruption", "soul_pillars"),
			List.of("wither_skulls", "ghast_rain", "collapsing_ring", "nether_rifts"),
			List.of("mirror_clones"));

	@GameTest(maxTicks = 3200)
	public void everyDreadAttackIsTelegraphedAndRunsCleanly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper, 1);
		RuinConfig.RuinDef def = guardian();
		ServerPlayer player = target(helper, ruin, true);
		player.getAttribute(Attributes.MAX_HEALTH).addOrReplacePermanentModifier(new AttributeModifier(BOOST, 200, AttributeModifier.Operation.ADD_VALUE));
		player.setHealth(player.getMaxHealth());
		Set<String> types = new java.util.HashSet<>();
		GROUPS.forEach(types::addAll);
		helper.assertTrue(types.equals(RuinConfig.DREAD_TYPES), "every dread type is tried: " + types.size() + " of " + RuinConfig.DREAD_TYPES.size());
		Mob[] boss = new Mob[1];
		int[] errors = new int[1];
		Vec3 post = player.position();
		var sequence = helper.startSequence()
				.thenExecute(() -> helper.assertTrue(Encounters.awaken(level, ruin, def, false) == null, "guardian awakened"))
				.thenWaitUntil(() -> {
					boss[0] = Encounters.boss(ruin);
					helper.assertTrue(boss[0] != null, "waiting for the guardian");
				})
				.thenExecute(() -> {
					boss[0].setNoAi(true);
					errors[0] = Encounters.dreadErrors();
				});
		for (List<String> group : GROUPS) {
			sequence = sequence
					.thenExecute(() -> {
						player.teleportTo(level, post.x, post.y, post.z, Set.of(), 0, 0, true);
						player.setDeltaMovement(Vec3.ZERO);
						player.setHealth(player.getMaxHealth());
						for (String type : group) {
							helper.assertTrue(Encounters.forceDread(ruin, type), type + " can be used");
							helper.assertTrue(Boolean.TRUE.equals(Encounters.dreadWarning(ruin, type)), type + " is telegraphed first");
						}
					})
					.thenWaitUntil(() -> {
						List<String> running = Encounters.dreadRunning(ruin);
						helper.assertTrue(group.stream().noneMatch(running::contains), "waiting for the end of " + group + ", running " + running);
					})
					.thenExecute(() -> {
						helper.assertTrue(Encounters.isActive(ruin) && player.isAlive(), "the fight goes on");
						helper.assertTrue(Encounters.dreadErrors() == errors[0], "no error in " + group);
					});
		}
		sequence.thenExecute(() -> {
			away(level, player, ruin);
			Encounters.reset(ruin);
		}).thenSucceed();
	}

	// ================================================================== the Avatar

	/** Drops a Nether star (count 1) at {@code pos}: nobody picks it up during the test unless the ritual takes it. */
	private static ItemEntity star(ServerLevel level, BlockPos pos) {
		ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, new ItemStack(Items.NETHER_STAR));
		item.setDeltaMovement(Vec3.ZERO);
		item.setPickUpDelay(32000);
		level.addFreshEntity(item);
		return item;
	}

	/** Reads an incantation scroll (cursed or not) until its ritual starts. */
	private static void read(GameTestHelper helper, ServerLevel level, ServerPlayer caster, ItemStack scroll) {
		caster.setItemInHand(InteractionHand.MAIN_HAND, scroll);
		InteractionResult result = UseItemCallback.EVENT.invoker().interact(caster, level, InteractionHand.MAIN_HAND);
		helper.assertTrue(result instanceof InteractionResult.Success && caster.isUsingItem(), "incantation started, got " + result);
		for (int i = 0; i < 400 && caster.isUsingItem(); i++) {
			caster.doTick();
		}
	}

	@GameTest(maxTicks = 700)
	public void cursedRitualWithANetherStarAwakensTheAvatar(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper, 1);
		ServerPlayer caster = TestSupport.survivor(helper, ruin.boss.offset(2, -1, 0));
		caster.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4, false, false));
		ItemEntity star = star(level, ruin.boss.offset(-3, 0, 3));
		read(helper, level, caster, CursedItems.create(1));
		helper.assertTrue(Incantations.ritualRunning(ruin) && Incantations.ritualCursed(ruin), "a cursed ritual started");
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(Encounters.isActive(ruin), "waiting for the climax"))
				.thenExecute(() -> {
					helper.assertTrue(Encounters.isAvatar(ruin) && CursedFights.isCursed(ruin), "the Avatar, a cursed fight");
					helper.assertTrue(star.isRemoved() || star.getItem().isEmpty(), "the star is consumed");
					Mob boss = Encounters.boss(ruin);
					helper.assertTrue(boss != null && boss.isAlive(), "the Avatar stands");
					String name = boss.getCustomName() == null ? "" : boss.getCustomName().getString();
					helper.assertTrue(name.contains("Avatar"), "named: " + name);
					helper.assertTrue(Encounters.barColor(ruin) == BossEvent.BossBarColor.RED, "a red bar");
					helper.assertTrue(bar(ruin).contains("Avatar") && bar(ruin).contains("Menace X"), "boss bar: " + bar(ruin));
					helper.assertTrue(Encounters.threat(ruin) == 10, "at least Menace X");
					helper.assertTrue(Encounters.dreadActive(ruin), "its dread form included");
					double[] health = Encounters.effectiveHealth(ruin);
					double floor = RuinModule.config().cursed.avatar.healthMinPerPlayer;
					helper.assertTrue(health != null && health[1] >= floor - 1 && health[1] > 1024,
							"at least " + floor + " effective health, beyond the vanilla cap: " + (health == null ? -1 : health[1]));
					helper.assertTrue(Encounters.redSky(ruin, caster), "the sky turns red for the players of the arena");
					helper.assertFalse(Encounters.dreadAbilities(ruin).isEmpty(), "its attacks");
					away(level, caster, ruin);
					Encounters.reset(ruin);
					helper.assertFalse(Encounters.isActive(ruin), "fight stopped");
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 700)
	public void cursedRitualWithoutTheStarAwakensTheCursedGuardian(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper, 1);
		ServerPlayer caster = TestSupport.survivor(helper, ruin.boss.offset(2, -1, 0));
		caster.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4, false, false));
		read(helper, level, caster, CursedItems.create(1));
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(Encounters.isActive(ruin), "waiting for the climax"))
				.thenExecute(() -> {
					helper.assertTrue(CursedFights.isCursed(ruin) && !Encounters.isAvatar(ruin), "the cursed guardian, no Avatar");
					helper.assertTrue(Encounters.barColor(ruin) == BossEvent.BossBarColor.PURPLE, "the purple bar");
					helper.assertFalse(bar(ruin).contains("Avatar"), "boss bar: " + bar(ruin));
					helper.assertFalse(Encounters.redSky(ruin, caster), "no red sky");
					away(level, caster, ruin);
					Encounters.reset(ruin);
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 600)
	public void normalRitualLeavesTheNetherStarAlone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper, 1);
		ServerPlayer caster = TestSupport.survivor(helper, ruin.boss.offset(2, -1, 0));
		caster.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4, false, false));
		ItemEntity star = star(level, ruin.boss.offset(-3, 0, 3));
		read(helper, level, caster, IncantationItems.create(1));
		helper.assertTrue(Incantations.ritualRunning(ruin) && !Incantations.ritualCursed(ruin), "a normal ritual started");
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(Encounters.isActive(ruin), "waiting for the climax"))
				.thenExecute(() -> {
					helper.assertFalse(Encounters.isAvatar(ruin) || CursedFights.isCursed(ruin), "a normal guardian");
					helper.assertTrue(!star.isRemoved() && star.getItem().is(Items.NETHER_STAR) && star.getItem().getCount() == 1, "the star is untouched");
					away(level, caster, ruin);
					Encounters.reset(ruin);
					star.discard();
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 500)
	public void avatarOpensNetherRifts(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper, 1);
		RuinConfig.RuinDef def = guardian();
		ServerPlayer player = target(helper, ruin, true);
		Mob[] boss = new Mob[1];
		helper.startSequence()
				.thenExecute(() -> helper.assertTrue(Encounters.awaken(level, ruin, def, false, true, true) == null, "the Avatar awakened (admin)"))
				.thenWaitUntil(() -> {
					boss[0] = Encounters.boss(ruin);
					helper.assertTrue(boss[0] != null, "waiting for the Avatar");
				})
				.thenExecute(() -> {
					boss[0].setNoAi(true);
					helper.assertTrue(Encounters.isAvatar(ruin), "an Avatar fight");
					helper.assertTrue(Encounters.minions(ruin).isEmpty(), "no escort yet");
					helper.assertTrue(Encounters.forceDread(ruin, "nether_rifts"), "the rifts open");
					helper.assertTrue(Boolean.TRUE.equals(Encounters.dreadWarning(ruin, "nether_rifts")), "telegraphed first");
				})
				.thenIdle(20)
				.thenExecute(() -> helper.assertTrue(Encounters.minions(ruin).isEmpty(), "nothing out during the warning"))
				.thenWaitUntil(() -> helper.assertFalse(Encounters.minions(ruin).isEmpty(), "waiting for the Nether mobs"))
				.thenExecute(() -> {
					for (Mob mob : Encounters.minions(ruin)) {
						helper.assertTrue(mob.getType() == EntityTypes.BLAZE || mob.getType() == EntityTypes.WITHER_SKELETON, "a Nether mob, got " + mob.getType());
					}
					away(level, player, ruin);
					Encounters.reset(ruin);
				})
				.thenSucceed();
	}
}
