package dev.guildy.test;

import dev.guildy.core.GuildyItems;
import dev.guildy.ruin.Encounters;
import dev.guildy.ruin.IncantationItems;
import dev.guildy.ruin.Incantations;
import dev.guildy.ruin.RuinConfig;
import dev.guildy.ruin.RuinIndex;
import dev.guildy.ruin.RuinModule;
import dev.guildy.ruin.RuinRecord;
import dev.guildy.ruin.RuinState;
import dev.guildy.ruin.SummonRules;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.Set;

/**
 * Incantation scrolls and the summoning ritual, on the test ruin of {@link RuinGameTests}: in the default "scroll" mode,
 * walking into a dormant ruin no longer awakens its guardian; the scroll is refused (and kept) outside ruins, in a defeated
 * ruin and in an awake one; read in the arena it is consumed, the ritual drains the participants (never below the minimum
 * health) and its climax awakens the guardian with its boss bar; leaving the arena cancels the ritual and gives the scroll
 * back. Right clicks go through the real use event, the vanilla channel is completed by ticking the mock player.
 */
@SuppressWarnings("removal")
public class RuinSummonGameTests {
	/** A placed, indexed test ruin with its boss marker. */
	private static RuinRecord ruin(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos center = TestSupport.region(level, 0);
		TestSupport.forceChunks(level, center, 3);
		RuinConfig.RuinDef def = RuinModule.config().def("test_ruin", false);
		def.resetSeconds = 2;
		def.respawnDays = 7;
		StructureStart start = RuinGameTests.generate(level, center);
		RuinGameTests.place(level, start);
		RuinIndex.processPending(level.getServer());
		RuinRecord ruin = RuinIndex.register(level, start);
		helper.assertTrue(ruin != null && ruin.boss != null, "test ruin indexed with its boss marker");
		helper.assertTrue(ruin.state == RuinState.DORMANT, "dormant ruin");
		return ruin;
	}

	/** Damage is blocked (mobs of the fight): the health checks only see the blood price. */
	private static void shield(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4, false, false));
	}

	/** Right click with {@code count} incantation scrolls in the main hand; returns the stack in hand. */
	private static ItemStack rightClick(GameTestHelper helper, ServerPlayer player, int count, boolean expectStart) {
		ItemStack stack = IncantationItems.create(count);
		helper.assertTrue(IncantationItems.is(stack) && stack.getCount() == count, "incantation scrolls created");
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		InteractionResult result = UseItemCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND);
		if (expectStart) {
			helper.assertTrue(result instanceof InteractionResult.Success && player.isUsingItem(), "incantation started, got " + result);
		} else {
			helper.assertTrue(result == InteractionResult.FAIL && !player.isUsingItem(), "incantation refused, got " + result);
		}
		return player.getMainHandItem();
	}

	/** Lets the vanilla use of the item run to its end (the mock player is not ticked by a connection). */
	private static void channel(ServerPlayer player) {
		for (int i = 0; i < 400 && player.isUsingItem(); i++) {
			player.doTick();
		}
	}

	private static int incantations(ServerPlayer player) {
		int total = 0;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (IncantationItems.is(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static void away(ServerLevel level, ServerPlayer player, RuinRecord ruin) {
		player.teleportTo(level, ruin.boss.getX() + 300.5, ruin.boss.getY() + 1, ruin.boss.getZ() + 0.5, Set.of(), 0, 0, true);
	}

	// ================================================================== the item

	@GameTest(maxTicks = 80)
	public void incantationScrollItem(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinConfig.SummonSettings cfg = RuinModule.config().summon;
		ItemStack stack = IncantationItems.create(3);
		helper.assertTrue(GuildyItems.is(stack, IncantationItems.KIND), "custom_data guildy.kind=ruin_summon (never sold to the shop)");
		helper.assertTrue(stack.is(Items.SKULL_BANNER_PATTERN), "vanilla base item");
		helper.assertTrue(stack.getCount() == 3 && stack.getMaxStackSize() == cfg.maxStack, "stackable by " + cfg.maxStack);
		helper.assertTrue(stack.has(DataComponents.CONSUMABLE), "vanilla channel animation");
		helper.assertTrue(stack.getUseDuration(null) == Math.round(cfg.channelSeconds * 20), "channel time");
		helper.assertTrue(stack.get(DataComponents.ITEM_NAME).getString().contains("Parchemin d'incantation"), "named");
		StringBuilder lore = new StringBuilder();
		for (Component line : stack.get(DataComponents.LORE).lines()) {
			lore.append(line.getString()).append('|');
		}
		helper.assertTrue(lore.indexOf("ruine") >= 0 && lore.indexOf("sang") >= 0 && lore.indexOf("dormante") >= 0,
				"lore: what it does, the blood price, dormant ruins only: " + lore);
		helper.assertFalse(lore.indexOf("{") >= 0, "every placeholder replaced: " + lore);
		helper.assertFalse(stack.has(DataComponents.PROVIDES_BANNER_PATTERNS), "no loom pattern");

		// Loot table stacks (custom_data only, any base item) are completed in the inventory.
		ServerPlayer player = TestSupport.survivor(helper, TestSupport.region(level, 2));
		CompoundTag tag = new CompoundTag();
		ItemStack raw = new ItemStack(Items.SKULL_BANNER_PATTERN);
		GuildyItems.tag(raw, IncantationItems.KIND, tag);
		ItemStack old = new ItemStack(Items.PAPER, 20);
		GuildyItems.tag(old, IncantationItems.KIND, tag);
		player.getInventory().add(raw);
		player.getInventory().add(old);
		TestSupport.console(level, "execute as " + player.getStringUUID() + " run guild admin ruin incantation @s 2");
		helper.succeedWhen(() -> {
			int total = 0;
			Inventory inventory = player.getInventory();
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				ItemStack item = inventory.getItem(slot);
				if (!GuildyItems.is(item, IncantationItems.KIND)) {
					continue;
				}
				helper.assertTrue(item.is(Items.SKULL_BANNER_PATTERN), "rebuilt on the base item: " + item);
				helper.assertTrue(item.has(DataComponents.CONSUMABLE) && item.has(DataComponents.ITEM_NAME), "components added: " + item);
				helper.assertTrue(item.getCount() <= cfg.maxStack, "within the max stack size");
				total += item.getCount();
			}
			helper.assertTrue(total == 23, "every scroll kept (1 + 20 + 2 given by the admin): " + total);
		});
	}

	// ================================================================== scroll mode

	@GameTest(maxTicks = 200)
	public void enteringADormantRuinNoLongerAwakensIt(GameTestHelper helper) {
		helper.assertTrue("scroll".equals(RuinModule.config().summon.mode), "default mode: scroll");
		RuinRecord ruin = ruin(helper);
		ServerPlayer player = TestSupport.survivor(helper, ruin.boss.offset(2, -1, 0));
		helper.startSequence()
				.thenIdle(80)
				.thenExecute(() -> {
					helper.assertFalse(Encounters.isActive(ruin), "no guardian without an incantation");
					helper.assertTrue(Encounters.boss(ruin) == null, "no guardian spawned");
					helper.assertTrue(ruin.state == RuinState.DORMANT, "still dormant");
					helper.assertFalse(Incantations.ritualRunning(ruin), "no ritual");
					away(helper.getLevel(), player, ruin);
				})
				.thenSucceed();
	}

	// ================================================================== refusals keep the scroll

	@GameTest(maxTicks = 200)
	public void incantationRefusedKeepsTheScroll(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// Outside any ruin.
		ServerPlayer loner = TestSupport.survivor(helper, TestSupport.region(level, 2));
		ItemStack kept = rightClick(helper, loner, 2, false);
		helper.assertTrue(kept.getCount() == 2, "kept outside ruins");

		RuinRecord ruin = ruin(helper);
		ServerPlayer player = TestSupport.survivor(helper, ruin.boss.offset(2, -1, 0));
		// Defeated ruin: "de retour dans N jours".
		ruin.state = RuinState.CLEARED;
		ruin.clearedDay = Encounters.day(level.getServer());
		helper.assertTrue(rightClick(helper, player, 2, false).getCount() == 2, "kept in a defeated ruin");
		// Awake ruin.
		ruin.state = RuinState.DORMANT;
		helper.assertTrue(Encounters.awaken(level, ruin, null, false) == null, "guardian awakened by the admin path");
		helper.assertTrue(rightClick(helper, player, 2, false).getCount() == 2, "kept in an awake ruin");
		Encounters.reset(ruin);
		helper.assertTrue(ruin.state == RuinState.DORMANT && !Encounters.isActive(ruin), "dormant again");
		helper.assertTrue(incantations(player) == 2 && !Incantations.ritualRunning(ruin), "nothing consumed, no ritual");
		away(level, player, ruin);
		helper.succeed();
	}

	// ================================================================== the ritual

	@GameTest(maxTicks = 500)
	public void incantationRitualAwakensTheGuardian(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper);
		RuinConfig.SummonSettings cfg = RuinModule.config().summon;
		ServerPlayer caster = TestSupport.survivor(helper, ruin.boss.offset(2, -1, 0));
		ServerPlayer weak = TestSupport.survivor(helper, ruin.boss.offset(-2, -1, 1));
		shield(caster);
		shield(weak);
		weak.setHealth(5.0F);
		float casterBefore = caster.getHealth();
		long[] started = new long[1];
		ItemStack hand = rightClick(helper, caster, 2, true);
		channel(caster);
		helper.assertFalse(caster.isUsingItem(), "channel completed");
		helper.assertTrue(caster.getMainHandItem() == hand && hand.getCount() == 1, "exactly one scroll consumed, left " + hand.getCount());
		helper.assertTrue(Incantations.ritualRunning(ruin), "ritual started");
		helper.assertFalse(Encounters.isActive(ruin), "the guardian waits for the climax");
		helper.assertTrue(Incantations.ritualParticipants(ruin).size() == 2, "both players take part");
		started[0] = level.getGameTime();
		float[] lowest = {Float.MAX_VALUE};
		helper.startSequence()
				.thenWaitUntil(() -> {
					lowest[0] = Math.min(lowest[0], weak.getHealth());
					helper.assertTrue(caster.getHealth() < casterBefore, "waiting for the drain");
				})
				.thenWaitUntil(() -> {
					lowest[0] = Math.min(lowest[0], weak.getHealth());
					helper.assertTrue(Encounters.isActive(ruin), "waiting for the climax");
				})
				.thenExecute(() -> {
					long elapsed = level.getGameTime() - started[0];
					helper.assertTrue(elapsed >= SummonRules.ticks(cfg.ritualSeconds) - 2, "the guardian came after the ritual, " + elapsed + " ticks");
					helper.assertFalse(Incantations.ritualRunning(ruin), "ritual over");
					Mob boss = Encounters.boss(ruin);
					helper.assertTrue(boss != null && boss.isAlive(), "guardian spawned");
					helper.assertTrue(ruin.state == RuinState.AWAKE, "ruin awake");
					helper.assertTrue(Encounters.barViewers(ruin).contains(caster), "boss bar shown to the caster");
					helper.assertTrue(Encounters.minions(ruin).size() >= 3, "escort spawned: " + Encounters.minions(ruin).size());
					float expected = (float) Math.max(cfg.minHealth, casterBefore - cfg.drainHealth);
					helper.assertTrue(Math.abs(caster.getHealth() - expected) < 0.01F, "caster paid the blood price: " + caster.getHealth()
							+ " (expected " + expected + ")");
					helper.assertTrue(Math.abs(weak.getHealth() - cfg.minHealth) < 0.01F && lowest[0] >= cfg.minHealth - 0.01F,
							"the weak player stopped at the minimum health: " + weak.getHealth() + ", lowest " + lowest[0]);
					helper.assertTrue(incantations(caster) == 1, "no refund after a successful ritual");
					away(level, caster, ruin);
					away(level, weak, ruin);
					Encounters.reset(ruin);
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 300)
	public void leavingTheArenaCancelsTheRitual(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper);
		ServerPlayer caster = TestSupport.survivor(helper, ruin.boss.offset(2, -1, 0));
		shield(caster);
		ItemStack hand = rightClick(helper, caster, 2, true);
		channel(caster);
		helper.assertTrue(hand.getCount() == 1 && Incantations.ritualRunning(ruin), "ritual started, one scroll consumed");
		helper.startSequence()
				.thenIdle(10)
				.thenExecute(() -> away(level, caster, ruin))
				.thenWaitUntil(() -> helper.assertFalse(Incantations.ritualRunning(ruin), "waiting for the ritual to break"))
				.thenExecute(() -> {
					helper.assertFalse(Encounters.isActive(ruin), "no guardian");
					helper.assertTrue(Encounters.boss(ruin) == null, "nothing spawned");
					helper.assertTrue(ruin.state == RuinState.DORMANT, "still dormant");
					helper.assertTrue(incantations(caster) == 2, "scroll given back: " + incantations(caster));
				})
				.thenSucceed();
	}
}
