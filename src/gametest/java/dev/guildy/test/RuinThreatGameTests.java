package dev.guildy.test;

import dev.guildy.ruin.Encounters;
import dev.guildy.ruin.RuinConfig;
import dev.guildy.ruin.RuinIndex;
import dev.guildy.ruin.RuinModule;
import dev.guildy.ruin.RuinRecord;
import dev.guildy.ruin.RuinState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/**
 * Threat levels and resurrections, on the test ruin of {@link RuinGameTests} with forced guardian definitions (copies:
 * the shared generic guardian is left alone): a guardian killed inside its fast-kill window refuses to die (no loot, no
 * victory), rises while invulnerable and frozen, comes back enraged at full health and pays a bonus when killed again; a
 * guardian killed after the window dies at once; each victory raises the ruin's threat and the next guardian is stronger;
 * the default guardian of the feathered serpent temple is a giant breeze with its jade and wind abilities.
 */
@SuppressWarnings("removal")
public class RuinThreatGameTests {

	/** A placed, indexed test ruin with its boss marker, at threat level I. */
	private static RuinRecord ruin(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos center = TestSupport.region(level, 0);
		TestSupport.forceChunks(level, center, 3);
		StructureStart start = RuinGameTests.generate(level, center);
		RuinGameTests.place(level, start);
		RuinIndex.processPending(level.getServer());
		RuinRecord ruin = RuinIndex.register(level, start);
		helper.assertTrue(ruin != null && ruin.boss != null, "test ruin indexed with its boss marker");
		helper.assertTrue(ruin.state == RuinState.DORMANT, "dormant ruin");
		Encounters.setThreat(ruin, 1);
		return ruin;
	}

	/** A copy of the generic guardian: exact gold, no loot table, the given fast-kill window (0 = never rises). */
	private static RuinConfig.RuinDef guardian(double fastKillSeconds) {
		RuinConfig.RuinDef def = RuinModule.config().def("test_ruin", false).copy();
		def.resetSeconds = 60;
		def.respawnDays = 7;
		def.fastKillSeconds = fastKillSeconds;
		def.rewards.goldMin = 10;
		def.rewards.goldMax = 10;
		def.rewards.lootTable = "";
		def.rewards.items.clear();
		return def;
	}

	/** Damage is blocked: the fights go on while the test checks them. */
	private static ServerPlayer fighter(GameTestHelper helper, RuinRecord ruin) {
		ServerPlayer player = TestSupport.survivor(helper, ruin.boss.offset(3, 0, 0));
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4, false, false));
		return player;
	}

	private static void awaken(GameTestHelper helper, RuinRecord ruin, RuinConfig.RuinDef def) {
		String error = Encounters.awaken(helper.getLevel(), ruin, def, false);
		helper.assertTrue(error == null, "guardian awakened, got " + error);
	}

	private static String bar(RuinRecord ruin) {
		Component title = Encounters.barTitle(ruin);
		return title == null ? "" : title.getString();
	}

	private static int gold(ServerLevel level, Mob boss) {
		List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(boss.blockPosition()).inflate(8), e -> e.getItem().is(Items.GOLD_INGOT));
		return items.stream().mapToInt(e -> e.getItem().getCount()).sum();
	}

	private static void away(ServerLevel level, ServerPlayer player, RuinRecord ruin) {
		player.teleportTo(level, ruin.boss.getX() + 300.5, ruin.boss.getY() + 1, ruin.boss.getZ() + 0.5, Set.of(), 0, 0, true);
	}

	// ================================================================== resurrection

	@GameTest(maxTicks = 900)
	public void guardianKilledTooFastRisesEnraged(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper);
		RuinConfig.RuinDef def = guardian(60);
		ServerPlayer player = fighter(helper, ruin);
		Mob[] boss = new Mob[1];
		float[] maxHealth = new float[1];
		double[] scale = new double[1];
		helper.startSequence()
				.thenExecute(() -> awaken(helper, ruin, def))
				.thenWaitUntil(() -> {
					boss[0] = Encounters.boss(ruin);
					helper.assertTrue(boss[0] != null, "waiting for the guardian");
				})
				.thenExecute(() -> {
					helper.assertTrue(bar(ruin).contains("Menace I") && bar(ruin).contains("Résurrection possible"),
							"the boss bar shows the threat and the chrono: " + bar(ruin));
					helper.assertTrue(Encounters.resurrectionSecondsLeft(ruin) > 50, "window of 60 s open");
					maxHealth[0] = boss[0].getMaxHealth();
					scale[0] = boss[0].getAttributeValue(Attributes.SCALE);
					boss[0].hurtServer(level, level.damageSources().playerAttack(player), 100_000F);
					helper.assertTrue(boss[0].isAlive() && !boss[0].isRemoved() && boss[0].getHealth() > 0, "death cancelled");
					helper.assertTrue(Encounters.rising(ruin), "the guardian rises");
					helper.assertTrue(ruin.state == RuinState.AWAKE && ruin.timesCleared == 0 && Encounters.isActive(ruin), "no victory");
					helper.assertTrue(boss[0].isInvulnerable() && boss[0].isNoAi(), "invulnerable and frozen while it rises");
					float health = boss[0].getHealth();
					helper.assertFalse(boss[0].hurtServer(level, level.damageSources().playerAttack(player), 50F), "no damage while it rises");
					helper.assertTrue(boss[0].getHealth() >= health, "health untouched");
					helper.assertTrue(gold(level, boss[0]) == 0, "nothing dropped");
					helper.assertTrue(bar(ruin).contains("revient d'entre les morts"), "boss bar: " + bar(ruin));
					helper.assertTrue(Encounters.barColor(ruin) == BossEvent.BossBarColor.RED, "red boss bar");
				})
				.thenIdle(20)
				.thenExecute(() -> {
					helper.assertTrue(Encounters.rising(ruin) && boss[0].isInvulnerable(), "still rising after one second");
					helper.assertFalse(boss[0].hurtServer(level, level.damageSources().playerAttack(player), 100_000F), "still invulnerable");
					helper.assertTrue(boss[0].isAlive(), "a second kill during the sequence does nothing");
				})
				.thenWaitUntil(() -> helper.assertTrue(Encounters.risen(ruin) && !Encounters.rising(ruin), "waiting for its return"))
				.thenExecute(() -> {
					helper.assertTrue(boss[0].isAlive() && Encounters.boss(ruin) == boss[0], "the same guardian is back");
					helper.assertTrue(boss[0].getMaxHealth() >= maxHealth[0] - 0.01F, "max health kept");
					helper.assertTrue(boss[0].getHealth() >= boss[0].getMaxHealth() - 0.01F, "full health, got " + boss[0].getHealth());
					helper.assertFalse(boss[0].isInvulnerable() || boss[0].isNoAi(), "it fights again");
					helper.assertTrue(boss[0].getCustomName() != null && boss[0].getCustomName().getString().contains("Enragé"),
							"enraged name: " + boss[0].getCustomName());
					helper.assertTrue(bar(ruin).contains("Enragé") && !bar(ruin).contains("Résurrection possible"), "boss bar: " + bar(ruin));
					helper.assertTrue(boss[0].hasEffect(MobEffects.STRENGTH), "stronger");
					helper.assertTrue(boss[0].getAttributeValue(Attributes.SCALE) >= scale[0] - 1.0E-6, "never smaller");
					helper.assertTrue(boss[0].getAttributeValue(Attributes.MOVEMENT_SPEED) > 0, "moves");
					helper.assertTrue(!Encounters.minions(ruin).isEmpty(), "a fresh escort");
					boss[0].hurtServer(level, level.damageSources().playerAttack(player), 100_000F);
				})
				.thenWaitUntil(() -> helper.assertTrue(ruin.state == RuinState.CLEARED, "waiting for the final victory"))
				.thenExecute(() -> {
					helper.assertFalse(Encounters.isActive(ruin), "fight over");
					helper.assertTrue(ruin.timesCleared == 1, "one victory, got " + ruin.timesCleared);
					helper.assertTrue(ruin.threat == 2, "Menace II next time, got " + ruin.threat);
					int gold = gold(level, boss[0]);
					helper.assertTrue(gold == 15, "risen bonus: 10 gold + 50 %, got " + gold);
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 400)
	public void guardianKilledAfterTheWindowDies(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper);
		RuinConfig.RuinDef def = guardian(1);
		ServerPlayer player = fighter(helper, ruin);
		Mob[] boss = new Mob[1];
		helper.startSequence()
				.thenExecute(() -> awaken(helper, ruin, def))
				.thenWaitUntil(() -> {
					boss[0] = Encounters.boss(ruin);
					helper.assertTrue(boss[0] != null, "waiting for the guardian");
				})
				.thenIdle(45)
				.thenExecute(() -> {
					helper.assertTrue(Encounters.resurrectionSecondsLeft(ruin) == 0, "window of 1 s closed");
					helper.assertFalse(bar(ruin).contains("Résurrection possible"), "no chrono any more: " + bar(ruin));
					boss[0].hurtServer(level, level.damageSources().playerAttack(player), 100_000F);
					helper.assertFalse(Encounters.rising(ruin), "no resurrection");
					helper.assertTrue(ruin.state == RuinState.CLEARED && !Encounters.isActive(ruin), "dies at once");
					helper.assertTrue(ruin.timesCleared == 1 && ruin.threat == 2, "victory and threat raised");
				})
				.thenWaitUntil(() -> helper.assertTrue(gold(level, boss[0]) == 10, "plain reward, got " + gold(level, boss[0])))
				.thenSucceed();
	}

	// ================================================================== escalation

	@GameTest(maxTicks = 600)
	public void victoriesRaiseTheThreat(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper);
		BlockPos at = ruin.boss;
		TestSupport.console(level, "execute in minecraft:overworld positioned " + at.getX() + " " + at.getY() + " " + at.getZ()
				+ " run guild admin ruin threat 4");
		helper.assertTrue(ruin.threat == 4 && Encounters.threatLevel(ruin) == 4, "admin command sets Menace IV, got " + ruin.threat);
		RuinConfig.RuinDef def = guardian(0);
		ServerPlayer player = fighter(helper, ruin);
		Mob[] boss = new Mob[2];
		double[] armor = new double[2];
		int[] escort = new int[2];
		helper.startSequence()
				.thenExecute(() -> awaken(helper, ruin, def))
				.thenWaitUntil(() -> {
					boss[0] = Encounters.boss(ruin);
					helper.assertTrue(boss[0] != null, "waiting for the first guardian");
				})
				.thenExecute(() -> {
					helper.assertTrue(bar(ruin).contains("Menace IV"), "boss bar: " + bar(ruin));
					helper.assertFalse(bar(ruin).contains("Résurrection"), "this guardian never rises: no chrono");
					double base = def.boss.health;
					helper.assertTrue(Math.abs(boss[0].getMaxHealth() - base * 1.6) < 0.5, "+20 % health per level: " + boss[0].getMaxHealth());
					armor[0] = boss[0].getAttributeValue(Attributes.ARMOR);
					escort[0] = Encounters.minions(ruin).size();
					boss[0].hurtServer(level, level.damageSources().playerAttack(player), 100_000F);
					helper.assertTrue(ruin.state == RuinState.CLEARED, "victory");
					helper.assertTrue(ruin.threat == 5, "the threat grows, got " + ruin.threat);
					Encounters.reset(ruin);
					helper.assertTrue(ruin.state == RuinState.DORMANT, "re-armed");
					awaken(helper, ruin, def);
				})
				.thenWaitUntil(() -> {
					boss[1] = Encounters.boss(ruin);
					helper.assertTrue(boss[1] != null && boss[1] != boss[0], "waiting for the next guardian");
				})
				.thenExecute(() -> {
					helper.assertTrue(bar(ruin).contains("Menace V"), "boss bar: " + bar(ruin));
					helper.assertTrue(boss[1].getMaxHealth() > boss[0].getMaxHealth(), "more health: " + boss[0].getMaxHealth() + " -> " + boss[1].getMaxHealth());
					armor[1] = boss[1].getAttributeValue(Attributes.ARMOR);
					helper.assertTrue(armor[1] > armor[0] || armor[1] >= 30, "more armor: " + armor[0] + " -> " + armor[1]);
					escort[1] = Encounters.minions(ruin).size();
					helper.assertTrue(escort[1] >= escort[0], "escort " + escort[0] + " -> " + escort[1]);
					away(level, player, ruin);
					Encounters.reset(ruin);
					helper.assertFalse(Encounters.isActive(ruin), "fight stopped");
				})
				.thenSucceed();
	}

	// ================================================================== the 7th ruin's guardian

	@GameTest(maxTicks = 700)
	public void featheredSerpentIsAGiantBreeze(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper);
		RuinConfig.RuinDef template = RuinModule.config().ruins.get("temple_maya");
		helper.assertTrue(template != null, "temple_maya in ruins.json5");
		RuinConfig.RuinDef def = template.copy();
		def.resetSeconds = 60;
		def.fastKillSeconds = 0;
		ServerPlayer player = fighter(helper, ruin);
		// A mock player stays invulnerable until its client "loaded" (no connection tick): end that delay, so that the
		// wind blades really hit (Resistance V still blocks the damage).
		for (int i = 0; i < 200 && !player.connection.hasClientLoaded(); i++) {
			player.connection.tickClientLoadTimeout();
		}
		helper.assertTrue(player.connection.hasClientLoaded(), "mock player can be hit");
		Mob[] boss = new Mob[1];
		GameTestSequence sequence = helper.startSequence()
				.thenExecute(() -> awaken(helper, ruin, def))
				.thenWaitUntil(() -> {
					boss[0] = Encounters.boss(ruin);
					helper.assertTrue(boss[0] != null, "waiting for the feathered serpent");
				})
				.thenExecute(() -> {
					helper.assertTrue(boss[0].getType() == EntityTypes.BREEZE, "a breeze, got " + boss[0].getType());
					helper.assertTrue(Math.abs(boss[0].getAttributeValue(Attributes.SCALE) - def.boss.scale) < 1.0E-6, "scaled up");
					helper.assertTrue(Math.abs(boss[0].getMaxHealth() - def.boss.health) < 0.5, "configured health " + boss[0].getMaxHealth());
					helper.assertTrue(boss[0].getCustomName() != null && boss[0].getCustomName().getString().contains("Serpent"), "named");
					helper.assertTrue(Encounters.barColor(ruin) == BossEvent.BossBarColor.GREEN, "green boss bar");
					helper.assertFalse(Encounters.minions(ruin).isEmpty(), "escort");
					for (Mob minion : Encounters.minions(ruin)) {
						helper.assertTrue(minion.getType() == EntityTypes.BOGGED || minion.getType() == EntityTypes.CAVE_SPIDER,
								"jungle escort, got " + minion.getType());
						minion.setNoAi(true);
					}
				})
				.thenWaitUntil(() -> helper.assertTrue(boss[0].onGround(), "waiting for the serpent to land"))
				.thenExecute(() -> boss[0].setNoAi(true));
		ability(helper, sequence, ruin, player, boss, "volley", "wind", () -> helper.assertTrue(player.getLastHurtByMob() == boss[0],
				"hit by a wind blade: player at " + player.position() + ", serpent at " + boss[0].position() + ", hurt by " + player.getLastHurtByMob()));
		ability(helper, sequence, ruin, player, boss, "fire_ring", "jade", () -> helper.assertTrue(player.hasEffect(MobEffects.POISON),
				"the jade ring poisons"));
		ability(helper, sequence, ruin, player, boss, "blink", "wind", () -> helper.assertTrue(boss[0].distanceTo(player) < 5.0,
				"the serpent rushed to the player, distance " + boss[0].distanceTo(player)));
		sequence.thenExecute(() -> {
			helper.assertTrue(boss[0].isAlive() && Encounters.isActive(ruin), "fight still running");
			away(level, player, ruin);
			Encounters.reset(ruin);
		}).thenSucceed();
	}

	/**
	 * Uses an ability now (cooldowns ignored), the serpent on its spawn cell and the player 4 blocks away, lets its
	 * animation run, then checks its effect.
	 */
	private static void ability(GameTestHelper helper, GameTestSequence sequence, RuinRecord ruin, ServerPlayer player, Mob[] boss, String type,
			String style, Runnable check) {
		RuinConfig.AbilityDef ability = new RuinConfig.AbilityDef();
		ability.type = type;
		ability.style = style;
		ability.damage = 4;
		ability.radius = 7;
		ability.count = 2;
		sequence.thenExecute(() -> {
			boss[0].teleportTo(ruin.boss.getX() + 0.5, ruin.boss.getY(), ruin.boss.getZ() + 0.5);
			boss[0].lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(ruin.boss.offset(4, 0, 0)));
			player.teleportTo(helper.getLevel(), ruin.boss.getX() + 4.5, ruin.boss.getY(), ruin.boss.getZ() + 0.5, Set.of(), 0, 0, true);
			player.setLastHurtByMob(null);
			helper.assertTrue(Encounters.forceAbility(ruin, ability), "ability " + type + " " + style + " used");
		})
				.thenIdle(40)
				.thenExecute(check);
	}
}
