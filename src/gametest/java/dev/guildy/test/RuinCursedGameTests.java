package dev.guildy.test;

import dev.guildy.core.GuildyItems;
import dev.guildy.core.ScrollDrops;
import dev.guildy.ruin.CursedFights;
import dev.guildy.ruin.CursedItems;
import dev.guildy.ruin.CursedSettings;
import dev.guildy.ruin.Encounters;
import dev.guildy.ruin.IncantationItems;
import dev.guildy.ruin.Incantations;
import dev.guildy.ruin.RuinConfig;
import dev.guildy.ruin.RuinIndex;
import dev.guildy.ruin.RuinModule;
import dev.guildy.ruin.RuinRecord;
import dev.guildy.ruin.RoomGuards;
import dev.guildy.ruin.RuinState;
import dev.guildy.ruin.SummonRules;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cursed guardians, on the test ruin of {@link RuinGameTests}: the cursed incantation scroll (item, loot table stacks
 * completed, admin command); read in a dormant ruin it starts the cursed ritual (heavier blood price) and its climax
 * awakens the guardian cursed (3x health, purple boss bar, "Maudit"); a killing blow in phase 1 starts the phase 2
 * transition (invulnerable) instead of a death or a resurrection; pentagrams appear on the floor, a player standing in one
 * is drained while the guardian heals; killed in phase 3 it always rises once more (its final form, enraged, nothing
 * dropped yet), and only its second death gives the cursed rewards (no risen bonus on top) and the threat rises by 2; when
 * everyone leaves, the cursed guardian vanishes and the ruin sleeps again; like the normal one, the cursed scroll is
 * refused while room guards hold the arena (and given back when its ritual breaks).
 */
@SuppressWarnings("removal")
public class RuinCursedGameTests {
	private static final Identifier CURSED_HEALTH = Identifier.fromNamespaceAndPath("guildy", "ruin_cursed_health");
	private static final Identifier BOOST = Identifier.fromNamespaceAndPath("guildy-test", "cursed_test_health");
	private static final Identifier RISEN_SCALE = Identifier.fromNamespaceAndPath("guildy", "ruin_risen_scale");
	private static final Identifier RISEN_SPEED = Identifier.fromNamespaceAndPath("guildy", "ruin_risen_speed");
	private static final AtomicInteger IDS = new AtomicInteger();

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

	/** A copy of the generic guardian: no escort, no normal ability, exact gold, a fast-kill window (never used when cursed). */
	private static RuinConfig.RuinDef guardian(int resetSeconds) {
		RuinConfig.RuinDef def = RuinModule.config().def("test_ruin", false).copy();
		def.resetSeconds = resetSeconds;
		def.respawnDays = 7;
		def.fastKillSeconds = 60;
		def.bossBarColor = "RED";
		def.abilities.clear();
		def.minions.types.clear();
		def.rewards.goldMin = 10;
		def.rewards.goldMax = 10;
		def.rewards.lootTable = "";
		def.rewards.items.clear();
		return def;
	}

	/** Damage is blocked; the mock player can be hit (its client "loaded"). */
	private static ServerPlayer fighter(GameTestHelper helper, RuinRecord ruin) {
		ServerPlayer player = TestSupport.survivor(helper, ruin.boss.offset(3, 0, 0));
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4, false, false));
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

	private static List<ItemEntity> drops(ServerLevel level, Mob boss) {
		return level.getEntitiesOfClass(ItemEntity.class, new AABB(boss.blockPosition()).inflate(10), e -> !e.getItem().isEmpty());
	}

	private static int cursedScrolls(ServerPlayer player) {
		int total = 0;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (CursedItems.is(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	// ================================================================== the item

	@GameTest(maxTicks = 80)
	public void cursedScrollItem(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CursedSettings cfg = RuinModule.config().cursed;
		ItemStack stack = CursedItems.create(2);
		helper.assertTrue(GuildyItems.is(stack, CursedItems.KIND), "custom_data guildy.kind=ruin_summon_cursed");
		helper.assertFalse(IncantationItems.is(stack), "not a normal incantation scroll");
		helper.assertFalse(CursedItems.is(IncantationItems.create(1)), "a normal scroll is not cursed");
		helper.assertTrue(stack.is(Items.PIGLIN_BANNER_PATTERN), "vanilla base item");
		helper.assertTrue(stack.getCount() == 2 && stack.getMaxStackSize() == cfg.ritual.maxStack, "stackable by " + cfg.ritual.maxStack);
		helper.assertTrue(stack.has(DataComponents.CONSUMABLE), "vanilla channel animation");
		helper.assertTrue(stack.getUseDuration(null) == Math.round(cfg.ritual.channelSeconds * 20), "channel time");
		String name = stack.get(DataComponents.ITEM_NAME).getString();
		helper.assertTrue(name.contains("Parchemin d'incantation") && name.contains("maudit"), "named: " + name);
		StringBuilder lore = new StringBuilder();
		for (Component line : stack.get(DataComponents.LORE).lines()) {
			lore.append(line.getString()).append('|');
		}
		helper.assertTrue(lore.indexOf("Malédiction") >= 0 && lore.indexOf("sang") >= 0 && lore.indexOf("Récompenses") >= 0
				&& lore.indexOf("dormante") >= 0, "lore: the curse, the blood price, the rewards: " + lore);
		helper.assertFalse(lore.indexOf("{") >= 0, "every placeholder replaced: " + lore);
		helper.assertFalse(stack.has(DataComponents.PROVIDES_BANNER_PATTERNS), "no loom pattern");

		// Loot table stacks (custom_data only, any base item) are completed in the inventory; the admin gives more.
		ServerPlayer player = TestSupport.survivor(helper, TestSupport.region(level, 2));
		ItemStack raw = new ItemStack(Items.PAPER, 3);
		GuildyItems.tag(raw, CursedItems.KIND, new CompoundTag());
		player.getInventory().add(raw);
		TestSupport.console(level, "execute as " + player.getStringUUID() + " run guild admin ruin incantation @s 2 cursed");
		helper.succeedWhen(() -> {
			Inventory inventory = player.getInventory();
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				ItemStack item = inventory.getItem(slot);
				if (GuildyItems.is(item, CursedItems.KIND)) {
					helper.assertTrue(item.is(Items.PIGLIN_BANNER_PATTERN) && item.has(DataComponents.ITEM_NAME), "rebuilt: " + item);
				}
			}
			helper.assertTrue(cursedScrolls(player) == 5, "every cursed scroll kept (3 + 2 given by the admin): " + cursedScrolls(player));
		});
	}

	// ================================================================== the cursed ritual

	@GameTest(maxTicks = 700)
	public void cursedRitualAwakensACursedGuardian(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper);
		CursedSettings cfg = RuinModule.config().cursed;
		ServerPlayer caster = TestSupport.survivor(helper, ruin.boss.offset(2, -1, 0));
		caster.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4, false, false));
		float before = caster.getHealth();
		ItemStack stack = CursedItems.create(2);
		caster.setItemInHand(InteractionHand.MAIN_HAND, stack);
		InteractionResult result = UseItemCallback.EVENT.invoker().interact(caster, level, InteractionHand.MAIN_HAND);
		helper.assertTrue(result instanceof InteractionResult.Success && caster.isUsingItem(), "cursed incantation started, got " + result);
		for (int i = 0; i < 400 && caster.isUsingItem(); i++) {
			caster.doTick();
		}
		ItemStack hand = caster.getMainHandItem();
		helper.assertTrue(CursedItems.is(hand) && hand.getCount() == 1, "exactly one cursed scroll consumed");
		helper.assertTrue(Incantations.ritualRunning(ruin) && Incantations.ritualCursed(ruin), "a cursed ritual started");
		long started = level.getGameTime();
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(Encounters.isActive(ruin), "waiting for the climax"))
				.thenExecute(() -> {
					long elapsed = level.getGameTime() - started;
					helper.assertTrue(elapsed >= SummonRules.ticks(cfg.ritual.ritualSeconds) - 2, "after the whole cursed ritual: " + elapsed + " ticks");
					helper.assertTrue(CursedFights.isCursed(ruin) && CursedFights.phase(ruin) == 1, "a cursed fight, phase I");
					Mob boss = Encounters.boss(ruin);
					helper.assertTrue(boss != null && boss.isAlive(), "guardian spawned");
					RuinConfig.RuinDef def = RuinModule.config().def(ruin.ruinId, false);
					helper.assertTrue(boss.getMaxHealth() >= def.boss.health * cfg.guardian.healthMultiplier - 0.5,
							"at least 3x health: " + boss.getMaxHealth() + " (ruin: " + def.boss.health + ")");
					AttributeModifier modifier = boss.getAttribute(Attributes.MAX_HEALTH).getModifier(CURSED_HEALTH);
					helper.assertTrue(modifier != null && Math.abs(modifier.amount() - (cfg.guardian.healthMultiplier - 1)) < 1.0E-6, "cursed health modifier");
					helper.assertTrue(boss.getHealth() >= boss.getMaxHealth() - 0.01F, "full health");
					helper.assertTrue(boss.getCustomName() != null && boss.getCustomName().getString().contains("Maudit"), "named: " + boss.getCustomName());
					helper.assertTrue(Encounters.barColor(ruin) == BossEvent.BossBarColor.PURPLE, "purple boss bar");
					helper.assertTrue(bar(ruin).contains("Maudit") && bar(ruin).contains("Phase I") && !bar(ruin).contains("Résurrection"),
							"boss bar: " + bar(ruin));
					float expected = (float) Math.max(cfg.ritual.minHealth, before - cfg.ritual.drainHealth);
					helper.assertTrue(Math.abs(caster.getHealth() - expected) < 0.01F, "the heavier blood price: " + caster.getHealth() + " (expected " + expected + ")");
					helper.assertTrue(cursedScrolls(caster) == 1, "no refund once the guardian is awake");
					away(level, caster, ruin);
					Encounters.reset(ruin);
					helper.assertFalse(Encounters.isActive(ruin), "fight stopped");
				})
				.thenSucceed();
	}

	// ================================================================== phases, pentagrams, rewards

	@GameTest(maxTicks = 1600)
	public void cursedGuardianPhasesPentagramsAndRewards(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper);
		RuinConfig.RuinDef def = guardian(60);
		CursedSettings cfg = RuinModule.config().cursed;
		ServerPlayer player = fighter(helper, ruin);
		Mob[] boss = new Mob[1];
		float[] value = new float[3];
		double[] healed = new double[1];
		helper.startSequence()
				.thenExecute(() -> helper.assertTrue(Encounters.awaken(level, ruin, def, false, true) == null, "cursed guardian awakened"))
				.thenWaitUntil(() -> {
					boss[0] = Encounters.boss(ruin);
					helper.assertTrue(boss[0] != null, "waiting for the cursed guardian");
				})
				.thenExecute(() -> {
					helper.assertTrue(CursedFights.isCursed(ruin) && CursedFights.phase(ruin) == 1, "phase I");
					helper.assertTrue(Encounters.barColor(ruin) == BossEvent.BossBarColor.PURPLE, "purple bar even for a red ruin");
					helper.assertTrue(Math.abs(boss[0].getMaxHealth() - def.boss.health * cfg.guardian.healthMultiplier) < 0.5,
							"3x health at Menace I: " + boss[0].getMaxHealth());
					// A killing blow in phase 1: no death, no resurrection, the phase 2 transition.
					boss[0].hurtServer(level, level.damageSources().playerAttack(player), 100_000F);
					helper.assertTrue(boss[0].isAlive() && Encounters.isActive(ruin) && ruin.state == RuinState.AWAKE && ruin.timesCleared == 0, "not dead");
					helper.assertFalse(Encounters.rising(ruin), "no resurrection for a cursed guardian");
					helper.assertTrue(CursedFights.transitioning(ruin), "the phase 2 transition starts");
					float ratio = boss[0].getHealth() / boss[0].getMaxHealth();
					helper.assertTrue(Math.abs(ratio - cfg.phases.phase2Below) < 0.01, "the hit stopped at the phase 2 threshold: " + ratio);
					helper.assertTrue(boss[0].isInvulnerable() && boss[0].isNoAi(), "invulnerable and frozen during the transition");
					float health = boss[0].getHealth();
					helper.assertFalse(boss[0].hurtServer(level, level.damageSources().playerAttack(player), 50F), "no damage during the transition");
					helper.assertTrue(boss[0].getHealth() >= health, "health untouched");
					helper.assertTrue(bar(ruin).contains("se transforme"), "boss bar: " + bar(ruin));
				})
				.thenIdle(20)
				.thenExecute(() -> {
					helper.assertTrue(CursedFights.transitioning(ruin) && boss[0].isInvulnerable() && boss[0].isNoAi(), "still transitioning after one second");
					helper.assertTrue(boss[0].getY() >= ruin.boss.getY() - 1.01, "it rises (or stays) above its floor: " + boss[0].getY());
				})
				.thenWaitUntil(() -> helper.assertFalse(CursedFights.transitioning(ruin), "waiting for the end of the transition"))
				.thenExecute(() -> {
					helper.assertTrue(CursedFights.phase(ruin) == 2, "phase II, got " + CursedFights.phase(ruin));
					helper.assertFalse(boss[0].isInvulnerable() || boss[0].isNoAi(), "it fights again");
					helper.assertTrue(bar(ruin).contains("Phase II"), "boss bar: " + bar(ruin));
				})
				.thenWaitUntil(() -> helper.assertFalse(CursedFights.pentagrams(ruin).isEmpty(), "waiting for the arena pentagrams"))
				.thenExecute(() -> {
					// Stand in a pentagram, unprotected (more health so that nothing can kill the mock player).
					player.removeEffect(MobEffects.RESISTANCE);
					player.getAttribute(Attributes.MAX_HEALTH).addOrReplacePermanentModifier(new AttributeModifier(BOOST, 200, AttributeModifier.Operation.ADD_VALUE));
					player.setHealth(player.getMaxHealth());
					boss[0].setNoAi(true);
					Vec3 center = CursedFights.pentagrams(ruin).getFirst();
					player.teleportTo(level, center.x, center.y, center.z, Set.of(), 0, 0, true);
					value[0] = player.getHealth();
					value[1] = boss[0].getHealth();
					healed[0] = CursedFights.healed(ruin);
				})
				.thenIdle(45)
				.thenExecute(() -> {
					helper.assertTrue(player.getHealth() < value[0], "drained in the pentagram: " + value[0] + " -> " + player.getHealth());
					helper.assertTrue(CursedFights.drained(ruin) > 0, "drain counted");
					helper.assertTrue(CursedFights.healed(ruin) > healed[0] && boss[0].getHealth() > value[1],
							"the guardian heals: " + value[1] + " -> " + boss[0].getHealth());
					player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4, false, false));
					boss[0].setNoAi(false);
					boss[0].hurtServer(level, level.damageSources().playerAttack(player), 100_000F);
					helper.assertTrue(boss[0].isAlive() && CursedFights.transitioning(ruin), "the phase 3 transition starts");
					float ratio = boss[0].getHealth() / boss[0].getMaxHealth();
					helper.assertTrue(Math.abs(ratio - cfg.phases.phase3Below) < 0.01, "stopped at the phase 3 threshold: " + ratio);
				})
				.thenWaitUntil(() -> helper.assertFalse(CursedFights.transitioning(ruin), "waiting for phase III"))
				.thenExecute(() -> {
					helper.assertTrue(CursedFights.phase(ruin) == 3, "phase III");
					helper.assertTrue(boss[0].hasEffect(MobEffects.STRENGTH), "stronger in phase III");
					// Killed at the end of phase III, long after any fast-kill window: it always rises once more.
					boss[0].hurtServer(level, level.damageSources().playerAttack(player), 100_000F);
					helper.assertTrue(boss[0].isAlive() && Encounters.isActive(ruin) && ruin.state == RuinState.AWAKE && ruin.timesCleared == 0,
							"the cursed guardian refuses to die");
					helper.assertTrue(Encounters.rising(ruin) && !Encounters.risen(ruin), "it rises into its final form");
					helper.assertTrue(boss[0].isInvulnerable() && boss[0].isNoAi(), "invulnerable and frozen while it rises");
					helper.assertTrue(Encounters.barColor(ruin) == BossEvent.BossBarColor.PURPLE, "still the cursed bar");
					helper.assertTrue(bar(ruin).contains("refuse de mourir"), "boss bar: " + bar(ruin));
					helper.assertFalse(boss[0].hurtServer(level, level.damageSources().playerAttack(player), 50F), "no damage while it rises");
				})
				.thenIdle(10)
				.thenExecute(() -> helper.assertTrue(drops(level, boss[0]).stream().noneMatch(e -> e.getItem().is(Items.GOLD_INGOT)),
						"no reward before its final death"))
				.thenWaitUntil(() -> helper.assertFalse(Encounters.rising(ruin), "waiting for the final form"))
				.thenExecute(() -> {
					helper.assertTrue(Encounters.risen(ruin) && boss[0].isAlive() && !boss[0].isNoAi(), "back, fighting");
					String name = boss[0].getCustomName() == null ? "" : boss[0].getCustomName().getString();
					helper.assertTrue(name.contains("Maudit") && name.contains("Enragé"), "enraged final form: " + name);
					helper.assertTrue(bar(ruin).contains("Forme finale"), "boss bar: " + bar(ruin));
					float ratio = boss[0].getHealth() / boss[0].getMaxHealth();
					helper.assertTrue(Math.abs(ratio - cfg.finalForm.healthRatio) < 0.02, "back with part of its health: " + ratio);
					helper.assertTrue(boss[0].getAttribute(Attributes.SCALE).getModifier(RISEN_SCALE) != null
							|| boss[0].getAttribute(Attributes.MOVEMENT_SPEED).getModifier(RISEN_SPEED) != null, "the risen modifiers");
					helper.assertTrue(ruin.state == RuinState.AWAKE && ruin.timesCleared == 0, "not cleared yet");
					boss[0].hurtServer(level, level.damageSources().playerAttack(player), 100_000F);
				})
				.thenWaitUntil(() -> helper.assertTrue(ruin.state == RuinState.CLEARED, "waiting for the victory"))
				.thenExecute(() -> {
					helper.assertFalse(Encounters.isActive(ruin), "fight over");
					helper.assertTrue(ruin.timesCleared == 1, "one victory");
					helper.assertTrue(ruin.threat == 1 + cfg.rewards.threatGain, "the threat rises by " + cfg.rewards.threatGain + ", got " + ruin.threat);
				})
				.thenWaitUntil(() -> {
					List<ItemEntity> items = drops(level, boss[0]);
					int gold = items.stream().filter(e -> e.getItem().is(Items.GOLD_INGOT)).mapToInt(e -> e.getItem().getCount()).sum();
					// The cursed rewards once, after the final death (no risen bonus on top: no double count).
					helper.assertTrue(gold == 30, "gold x3: 10 -> 30, got " + gold);
					boolean magic = items.stream().anyMatch(e -> ScrollDrops.isScroll(e.getItem()) || GuildyItems.is(e.getItem(), "grimoire"));
					helper.assertTrue(magic, "a spell scroll (or a grimoire) guaranteed");
				})
				.thenExecute(() -> away(level, player, ruin))
				.thenSucceed();
	}

	// ================================================================== room guards

	@GameTest(maxTicks = 900)
	public void cursedRitualRefusedWhileGuardsHoldTheArena(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper);
		RuinConfig.RuinDef def = RuinModule.config().def("test_ruin", false).copy();
		def.resetSeconds = 3;
		def.fastKillSeconds = 0;
		def.guards.count = 2;
		def.guards.types.clear();
		def.guards.types.put("minecraft:zombie", 1);
		String id = "cursed_guard_test_" + IDS.incrementAndGet();
		RuinModule.config().ruins.put(id, def);
		ruin.ruinId = id;
		ServerPlayer player = TestSupport.survivor(helper, ruin.boss.offset(2, -1, 0));
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4, false, false));
		List<Mob> guards = new ArrayList<>();
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(RoomGuards.guards(ruin).size() == 2, "waiting for the room guards"))
				.thenExecute(() -> {
					guards.addAll(RoomGuards.guards(ruin));
					guards.forEach(guard -> guard.setNoAi(true));
					helper.assertTrue(RoomGuards.blocking(level, ruin) == 2, "the guards hold the arena");
					player.setItemInHand(InteractionHand.MAIN_HAND, CursedItems.create(2));
					InteractionResult refused = UseItemCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND);
					helper.assertTrue(refused == InteractionResult.FAIL && !player.isUsingItem(), "cursed incantation refused, got " + refused);
					helper.assertTrue(cursedScrolls(player) == 2 && !Incantations.ritualRunning(ruin), "scroll kept, no ritual");
					guards.forEach(guard -> guard.hurtServer(level, level.damageSources().playerAttack(player), 1000F));
				})
				.thenIdle(5)
				.thenExecute(() -> {
					helper.assertTrue(RoomGuards.blocking(level, ruin) == 0, "the arena is free");
					InteractionResult started = UseItemCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND);
					helper.assertTrue(started instanceof InteractionResult.Success && player.isUsingItem(), "accepted once they are dead, got " + started);
					for (int i = 0; i < 400 && player.isUsingItem(); i++) {
						player.doTick();
					}
					helper.assertTrue(Incantations.ritualRunning(ruin) && Incantations.ritualCursed(ruin) && cursedScrolls(player) == 1,
							"the cursed ritual starts, one scroll consumed");
					away(level, player, ruin);
				})
				.thenWaitUntil(() -> helper.assertFalse(Incantations.ritualRunning(ruin), "waiting for the ritual to break"))
				.thenExecute(() -> {
					helper.assertFalse(Encounters.isActive(ruin), "no guardian");
					helper.assertTrue(cursedScrolls(player) == 2, "the cursed scroll is given back by the broken ritual: " + cursedScrolls(player));
				})
				.thenSucceed();
	}

	// ================================================================== failure

	@GameTest(maxTicks = 400)
	public void cursedGuardianVanishesWhenEveryoneLeaves(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper);
		RuinConfig.RuinDef def = guardian(2);
		ServerPlayer player = fighter(helper, ruin);
		Mob[] boss = new Mob[1];
		helper.startSequence()
				.thenExecute(() -> helper.assertTrue(Encounters.awaken(level, ruin, def, false, true) == null, "cursed guardian awakened"))
				.thenWaitUntil(() -> {
					boss[0] = Encounters.boss(ruin);
					helper.assertTrue(boss[0] != null, "waiting for the cursed guardian");
				})
				.thenExecute(() -> away(level, player, ruin))
				.thenWaitUntil(() -> helper.assertFalse(Encounters.isActive(ruin), "waiting for the cursed guardian to vanish"))
				.thenExecute(() -> {
					helper.assertTrue(ruin.state == RuinState.DORMANT, "the ruin sleeps again");
					helper.assertTrue(boss[0].isRemoved(), "the cursed guardian is gone");
					helper.assertFalse(CursedFights.isCursed(ruin), "no cursed fight any more");
				})
				.thenSucceed();
	}
}
