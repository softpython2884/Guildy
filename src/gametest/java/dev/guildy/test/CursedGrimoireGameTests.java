package dev.guildy.test;

import dev.guildy.core.GuildyItems;
import dev.guildy.ruin.CursedFights;
import dev.guildy.ruin.Encounters;
import dev.guildy.ruin.RuinConfig;
import dev.guildy.ruin.RuinIndex;
import dev.guildy.ruin.RuinModule;
import dev.guildy.ruin.RuinRecord;
import dev.guildy.ruin.RuinState;
import dev.guildy.scroll.CursedGrimoireRules;
import dev.guildy.scroll.GrimoireItems;
import dev.guildy.scroll.GrimoireRules;
import dev.guildy.scroll.ScrollConfig;
import dev.guildy.scroll.ScrollModule;
import dev.guildy.unique.UniqueVisuals;
import dev.guildy.villager.ItemClassifier;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The cursed grimoire through the real game paths: the item (custom data, its own cooldown group, model data, name, lore
 * with the blood price, stored with the enchanted books, never sold, admin command), a cursed Pluie de météores that rains
 * far more meteors than the same page of a grimoire and costs more food plus a little health but never below the floor (a
 * cast that would go under it is refused), free in creative, and its only source: the cursed guardian of the Tour du Mage
 * (forced chance 1.0) drops it, another cursed guardian never does.
 */
public class CursedGrimoireGameTests {
	private static final String ENV = "guildy-test:day";
	private static final String METEORS = "pluie_de_meteores";

	private static GrimoireRules.Pages pages(int selected, String... ids) {
		return new GrimoireRules.Pages(List.of(ids), selected);
	}

	private static InteractionResult rightClick(ServerPlayer player) {
		return UseItemCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND);
	}

	/** Lets the vanilla use of the item run to its end (the mock player is not ticked by a connection). */
	private static void channel(ServerPlayer player) {
		for (int i = 0; i < 400 && player.isUsingItem(); i++) {
			player.doTick();
		}
	}

	/** Food below 18 and no saturation: no natural regeneration can blur the blood price. */
	private static void feed(ServerPlayer player, int food) {
		player.getFoodData().setFoodLevel(food);
		player.getFoodData().setSaturation(0.0F);
	}

	private static void face(ServerPlayer player, ServerLevel level, BlockPos ground) {
		player.teleportTo(level, ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, Set.of(), -90.0F, 0.0F, true);
	}

	private static ItemStack findCursed(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (GrimoireItems.isCursed(inventory.getItem(slot))) {
				return inventory.getItem(slot);
			}
		}
		return ItemStack.EMPTY;
	}

	// ================================================================== item

	@GameTest(maxTicks = 80)
	public void cursedGrimoireItem(GameTestHelper helper) {
		ScrollConfig cfg = ScrollModule.config();
		ItemStack blank = GrimoireItems.create(GrimoireRules.Pages.EMPTY, true);
		helper.assertTrue(GrimoireItems.isGrimoire(blank) && GrimoireItems.isCursed(blank) && GuildyItems.is(blank, GrimoireItems.KIND),
				"a grimoire (same kind) flagged cursed");
		helper.assertFalse(GrimoireItems.isCursed(GrimoireItems.create(GrimoireRules.Pages.EMPTY)), "the plain grimoire is not cursed");
		helper.assertTrue(blank.is(Items.ENCHANTED_BOOK) && blank.getMaxStackSize() == 1, "an enchanted book, one per slot");
		UseCooldown cooldown = blank.get(DataComponents.USE_COOLDOWN);
		helper.assertTrue(cooldown != null && cooldown.cooldownGroup().map(GrimoireItems.CURSED_COOLDOWN_GROUP::equals).orElse(false)
				&& Math.abs(cooldown.seconds() - cfg.cursedGrimoire.cooldownSeconds) < 1.0E-4, "its own, longer cooldown");
		helper.assertTrue(cfg.cursedGrimoire.cooldownSeconds > cfg.grimoire.cooldownSeconds, "longer than the grimoire's");
		CustomModelData model = blank.get(DataComponents.CUSTOM_MODEL_DATA);
		helper.assertTrue(model != null && List.of("empty").equals(model.strings()), "custom_model_data: empty: " + model);
		if (UniqueVisuals.enabled()) {
			helper.assertTrue(("guildy:" + cfg.cursedGrimoire.model).equals(String.valueOf(blank.get(DataComponents.ITEM_MODEL))), "its own model");
		} else {
			helper.assertTrue(blank.get(DataComponents.ITEM_MODEL).equals(blank.getPrototype().get(DataComponents.ITEM_MODEL)),
					"no resource pack: the vanilla look");
		}
		helper.assertTrue(blank.get(DataComponents.ITEM_NAME).getString().contains("Grimoire maudit"), "named");
		ItemStack written = GrimoireItems.create(pages(0, METEORS, "seisme"), true);
		helper.assertTrue(List.of(METEORS).equals(written.get(DataComponents.CUSTOM_MODEL_DATA).strings()), "model data: the selected scroll");
		StringBuilder lore = new StringBuilder();
		written.get(DataComponents.LORE).lines().forEach(line -> lore.append(line.getString()).append('|'));
		helper.assertTrue(lore.indexOf("25 météores") >= 0, "the lore shows the amplified meteors: " + lore);
		helper.assertTrue(lore.indexOf("sang") >= 0 && lore.indexOf("faim") >= 0 && lore.indexOf("{") < 0, "food and blood price: " + lore);
		helper.assertTrue(ScrollModule.tradePrice(blank, 9) == 0, "never sold at the trade hub");
		helper.assertTrue(ItemClassifier.categoryOf(written).equals(ItemClassifier.categoryOf(new ItemStack(Items.ENCHANTED_BOOK))),
				"stored with the enchanted books");
		// Pages, page turning and the menu are the grimoire's: fusing keeps the curse.
		GrimoireItems.apply(written, GrimoireItems.pages(written).select(1));
		helper.assertTrue(GrimoireItems.isCursed(written) && GrimoireItems.pages(written).selected() == 1, "still cursed after a page turn");

		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 2);
		ServerPlayer player = TestSupport.survivor(helper, ground);
		TestSupport.console(level, "execute as " + player.getStringUUID() + " run guild admin scroll grimoire @s cursed blizzard seisme");
		ItemStack given = findCursed(player);
		helper.assertTrue(GrimoireItems.pages(given).equals(pages(0, "blizzard", "seisme")), "admin cursed grimoire: " + GrimoireItems.pages(given));
		TestSupport.console(level, "guild admin scroll list");
		helper.succeed();
	}

	// ================================================================== casting

	@GameTest(environment = ENV, maxTicks = 400)
	public void cursedMeteorShowerRainsMoreAndCostsBlood(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ScrollConfig cfg = ScrollModule.config();
		ScrollConfig.ScrollDef def = cfg.scroll(METEORS);
		BlockPos ground = TestSupport.region(level, 8);
		TestSupport.forceChunks(level, ground, 3);
		ServerPlayer normal = TestSupport.survivor(helper, ground, TestSupport.uniqueName("grim"));
		ServerPlayer cursed = TestSupport.survivor(helper, ground.south(4), TestSupport.uniqueName("curs"));
		face(normal, level, ground);
		face(cursed, level, ground.south(4));
		ItemStack grimoire = GrimoireItems.create(pages(0, METEORS));
		ItemStack tome = GrimoireItems.create(pages(0, METEORS), true);
		normal.setItemInHand(InteractionHand.MAIN_HAND, grimoire);
		cursed.setItemInHand(InteractionHand.MAIN_HAND, tome);
		int normalBefore = ScrollModule.meteorImpacts(normal.getUUID());
		int cursedBefore = ScrollModule.meteorImpacts(cursed.getUUID());
		int food = GrimoireItems.foodCost(def, true);
		int blood = GrimoireItems.bloodCost(def, true);
		double floor = cfg.cursedGrimoire.minHealth;
		helper.assertTrue(food > GrimoireItems.foodCost(def) && blood >= 1, "more food than the grimoire, and blood: " + food + ", " + blood);

		// Not enough health: the blood price would go under the floor, refused, nothing spent.
		feed(cursed, 17);
		cursed.setHealth((float) (floor + blood - 0.5));
		helper.assertTrue(rightClick(cursed) == InteractionResult.FAIL && !cursed.isUsingItem(), "too weak: refused");
		helper.assertTrue(Math.abs(cursed.getHealth() - (floor + blood - 0.5)) < 0.01 && cursed.getFoodData().getFoodLevel() == 17,
				"nothing spent");
		helper.assertFalse(cursed.getCooldowns().isOnCooldown(tome), "no cooldown");

		// Exactly enough: cast, health down to the floor, never below.
		cursed.setHealth((float) (floor + blood));
		feed(normal, 17);
		helper.assertTrue(rightClick(normal) instanceof InteractionResult.Success && normal.isUsingItem(), "grimoire incantation started");
		helper.assertTrue(rightClick(cursed) instanceof InteractionResult.Success && cursed.isUsingItem(), "cursed incantation started");
		channel(normal);
		channel(cursed);
		helper.assertTrue(normal.getFoodData().getFoodLevel() == 17 - GrimoireItems.foodCost(def), "grimoire: its food cost");
		helper.assertTrue(cursed.getFoodData().getFoodLevel() == 17 - food, "cursed: its food cost, got " + cursed.getFoodData().getFoodLevel());
		helper.assertTrue(Math.abs(cursed.getHealth() - floor) < 0.01, "blood paid down to the floor, got " + cursed.getHealth());
		helper.assertTrue(cursed.getMainHandItem() == tome && GrimoireItems.isCursed(tome), "the cursed grimoire is never consumed");
		helper.assertTrue(cursed.getCooldowns().isOnCooldown(tome) && !cursed.getCooldowns().isOnCooldown(grimoire),
				"its own cooldown group");

		// Its cooldown over, at the floor: refused (it never takes the caster under it).
		cursed.getCooldowns().removeCooldown(GrimoireItems.CURSED_COOLDOWN_GROUP);
		feed(cursed, 17);
		helper.assertTrue(rightClick(cursed) == InteractionResult.FAIL && !cursed.isUsingItem(), "at the floor: refused");
		helper.assertTrue(Math.abs(cursed.getHealth() - floor) < 0.01, "never below the floor");

		int expectedNormal = (int) Math.round(def.param("count"));
		int expectedCursed = (int) Math.round(cfg.cursedGrimoire.power(def.spell()).apply(def.spell().param("count"), def.param("count")));
		helper.assertTrue(expectedCursed >= 2 * expectedNormal, "far more meteors: " + expectedNormal + " -> " + expectedCursed);
		helper.succeedWhen(() -> {
			int normalImpacts = ScrollModule.meteorImpacts(normal.getUUID()) - normalBefore;
			int cursedImpacts = ScrollModule.meteorImpacts(cursed.getUUID()) - cursedBefore;
			helper.assertTrue(normalImpacts == expectedNormal, "grimoire meteors: " + normalImpacts + "/" + expectedNormal);
			helper.assertTrue(cursedImpacts == expectedCursed, "cursed meteors: " + cursedImpacts + "/" + expectedCursed);
			helper.assertTrue(cursedImpacts >= 2 * normalImpacts, "clearly more impacts: " + cursedImpacts + " vs " + normalImpacts);
		});
	}

	@GameTest(environment = ENV, maxTicks = 100)
	public void creativeCastsTheCursedGrimoireForFree(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 6);
		ServerPlayer builder = TestSupport.player(helper, ground);
		ItemStack tome = GrimoireItems.create(pages(0, "benediction"), true);
		builder.setItemInHand(InteractionHand.MAIN_HAND, tome);
		feed(builder, 1);
		builder.setHealth(2.0F);
		helper.assertTrue(rightClick(builder) instanceof InteractionResult.Success && builder.isUsingItem(), "creative: no food, no blood needed");
		channel(builder);
		helper.assertTrue(builder.getMainHandItem() == tome && builder.getFoodData().getFoodLevel() == 1, "creative: free");
		helper.succeedWhen(() -> helper.assertTrue(builder.hasEffect(MobEffects.REGENERATION), "but cast (regeneration)"));
	}

	// ================================================================== drops

	/** A placed, indexed test ruin with its boss marker, at threat level I, its ruin id set to {@code id}. */
	private static RuinRecord ruin(GameTestHelper helper, String id) {
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
		ruin.ruinId = id;
		return ruin;
	}

	/** The generic guardian: no escort, no normal ability, exact gold, no loot table. */
	private static RuinConfig.RuinDef guardian() {
		RuinConfig.RuinDef def = RuinModule.config().def("test_ruin", false).copy();
		def.resetSeconds = 60;
		def.respawnDays = 7;
		def.fastKillSeconds = 60;
		def.abilities.clear();
		def.minions.types.clear();
		def.rewards.goldMin = 10;
		def.rewards.goldMax = 10;
		def.rewards.lootTable = "";
		def.rewards.items.clear();
		return def;
	}

	private static ServerPlayer fighter(GameTestHelper helper, RuinRecord ruin, String name) {
		ServerPlayer player = TestSupport.survivor(helper, ruin.boss.offset(3, 0, 0), TestSupport.uniqueName(name));
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4, false, false));
		for (int i = 0; i < 200 && !player.connection.hasClientLoaded(); i++) {
			player.connection.tickClientLoadTimeout();
		}
		helper.assertTrue(player.connection.hasClientLoaded(), "mock player can be hit");
		return player;
	}

	private static List<ItemEntity> drops(ServerLevel level, Mob boss) {
		return level.getEntitiesOfClass(ItemEntity.class, new AABB(boss.blockPosition()).inflate(10), e -> !e.getItem().isEmpty());
	}

	private static void hit(ServerLevel level, Mob boss, ServerPlayer player) {
		boss.hurtServer(level, level.damageSources().playerAttack(player), 100_000F);
	}

	@GameTest(maxTicks = 1600)
	public void onlyTheCursedArchmageDropsTheCursedGrimoire(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ScrollConfig.CursedGrimoire cfg = ScrollModule.config().cursedGrimoire;
		Map<String, Double> saved = new LinkedHashMap<>(cfg.ruinDrops);
		// Forced: the fallen archmage always drops it; another cursed guardian still never does.
		cfg.ruinDrops.put(CursedGrimoireRules.MAGE_TOWER, 1.0);
		RuinRecord mage = ruin(helper, CursedGrimoireRules.MAGE_TOWER);
		RuinRecord other = ruin(helper, "test_ruin");
		List<RuinRecord> ruins = List.of(mage, other);
		RuinConfig.RuinDef def = guardian();
		// Awakened at once, cursed (no room guard can be posted in a ruin that is awake).
		for (RuinRecord ruin : ruins) {
			helper.assertTrue(Encounters.awaken(level, ruin, def, true, true) == null, ruin.ruinId + ": cursed guardian awakened");
		}
		ServerPlayer mageFighter = fighter(helper, mage, "mage");
		ServerPlayer otherFighter = fighter(helper, other, "othr");
		Map<RuinRecord, ServerPlayer> fighters = Map.of(mage, mageFighter, other, otherFighter);
		Mob[] bosses = new Mob[2];
		helper.startSequence()
				.thenWaitUntil(() -> {
					bosses[0] = Encounters.boss(mage);
					bosses[1] = Encounters.boss(other);
					helper.assertTrue(bosses[0] != null && bosses[1] != null, "waiting for both cursed guardians");
				})
				.thenExecute(() -> {
					helper.assertTrue(CursedFights.isCursed(mage) && CursedFights.isCursed(other), "both cursed");
					hit(level, bosses[0], mageFighter);
					hit(level, bosses[1], otherFighter);
				})
				.thenWaitUntil(() -> helper.assertTrue(ruins.stream().noneMatch(CursedFights::transitioning)
						&& ruins.stream().allMatch(r -> CursedFights.phase(r) == 2), "waiting for phase II"))
				.thenExecute(() -> {
					hit(level, bosses[0], mageFighter);
					hit(level, bosses[1], otherFighter);
				})
				.thenWaitUntil(() -> helper.assertTrue(ruins.stream().noneMatch(CursedFights::transitioning)
						&& ruins.stream().allMatch(r -> CursedFights.phase(r) == 3), "waiting for phase III"))
				.thenExecute(() -> {
					hit(level, bosses[0], mageFighter);
					hit(level, bosses[1], otherFighter);
					helper.assertTrue(ruins.stream().allMatch(Encounters::rising), "both rise into their final form");
				})
				.thenWaitUntil(() -> helper.assertTrue(ruins.stream().noneMatch(Encounters::rising), "waiting for the final forms"))
				.thenExecute(() -> {
					hit(level, bosses[0], mageFighter);
					hit(level, bosses[1], otherFighter);
				})
				.thenWaitUntil(() -> helper.assertTrue(ruins.stream().allMatch(r -> r.state == RuinState.CLEARED), "waiting for both victories"))
				.thenWaitUntil(() -> {
					List<ItemEntity> mageDrops = drops(level, bosses[0]);
					List<ItemEntity> otherDrops = drops(level, bosses[1]);
					helper.assertTrue(otherDrops.stream().anyMatch(e -> e.getItem().is(Items.GOLD_INGOT)), "the other guardian's loot dropped");
					helper.assertTrue(mageDrops.stream().anyMatch(e -> GrimoireItems.isCursed(e.getItem())
							&& GrimoireItems.pages(e.getItem()).filled() == 0), "the cursed archmage drops a blank cursed grimoire");
					helper.assertTrue(otherDrops.stream().noneMatch(e -> GrimoireItems.isCursed(e.getItem())), "another cursed guardian never");
				})
				.thenExecute(() -> {
					cfg.ruinDrops.clear();
					cfg.ruinDrops.putAll(saved);
					for (Map.Entry<RuinRecord, ServerPlayer> entry : fighters.entrySet()) {
						BlockPos boss = entry.getKey().boss;
						entry.getValue().teleportTo(level, boss.getX() + 300.5, boss.getY() + 1, boss.getZ() + 0.5, Set.of(), 0, 0, true);
					}
				})
				.thenSucceed();
	}
}
