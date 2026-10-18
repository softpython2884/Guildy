package dev.guildy.test;

import dev.guildy.core.GuildyItems;
import dev.guildy.core.ScrollDrops;
import dev.guildy.scroll.GrimoireItems;
import dev.guildy.scroll.GrimoireRules;
import dev.guildy.scroll.ScrollConfig;
import dev.guildy.scroll.ScrollItems;
import dev.guildy.scroll.ScrollModule;
import dev.guildy.scroll.Spell;
import dev.guildy.unique.UniqueVisuals;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.UseCooldown;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * The grimoire through the real game paths: the item and its drops, fusing scrolls by inventory clicks (scroll onto the
 * grimoire and grimoire onto a scroll, duplicates and a full grimoire refused), sneak + right click turning pages, sneak +
 * swap hands opening the menu, casting (right click, vanilla channel completed by ticking the player) that hurts nearby
 * monsters and costs food (saturation first) without consuming anything, refused without enough food, free in creative.
 */
public class GrimoireGameTests {
	private static final String ENV = "guildy-test:day";
	/** nextDouble() == 0: every chance passes. */
	private static final RandomGenerator LUCKY = () -> 0L;

	private static GrimoireRules.Pages pages(int selected, String... ids) {
		return new GrimoireRules.Pages(List.of(ids), selected);
	}

	private static Husk husk(ServerLevel level, BlockPos ground) {
		Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
		husk.snapTo(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, 90.0F, 0.0F);
		husk.setNoAi(true);
		husk.setPersistenceRequired();
		level.addFreshEntity(husk);
		return husk;
	}

	/** Caster standing on {@code ground}, looking east, horizontally. */
	private static void face(ServerPlayer player, ServerLevel level, BlockPos ground) {
		player.teleportTo(level, ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, Set.of(), -90.0F, 0.0F, true);
	}

	/** Right click with what the main hand holds, through the real Fabric event. */
	private static InteractionResult rightClick(ServerPlayer player) {
		return UseItemCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND);
	}

	/** Lets the vanilla use of the item run to its end (the mock player is not ticked by a connection). */
	private static void channel(ServerPlayer player) {
		for (int i = 0; i < 400 && player.isUsingItem(); i++) {
			player.doTick();
		}
	}

	private static void feed(ServerPlayer player, int food, float saturation) {
		player.getFoodData().setFoodLevel(food);
		player.getFoodData().setSaturation(saturation);
	}

	private static int count(ServerPlayer player, String id) {
		int count = 0;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (id.equals(ScrollItems.idOf(stack))) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static ItemStack findGrimoire(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (GrimoireItems.isGrimoire(inventory.getItem(slot))) {
				return inventory.getItem(slot);
			}
		}
		return ItemStack.EMPTY;
	}

	// ================================================================== item and drops

	@GameTest(maxTicks = 80)
	public void grimoireItemAndDrops(GameTestHelper helper) {
		ScrollConfig.Grimoire cfg = ScrollModule.config().grimoire;
		ItemStack blank = GrimoireItems.create(GrimoireRules.Pages.EMPTY);
		helper.assertTrue(GrimoireItems.isGrimoire(blank) && GuildyItems.is(blank, GrimoireItems.KIND), "custom_data guildy.kind=grimoire");
		helper.assertTrue(blank.is(Items.ENCHANTED_BOOK) && blank.getMaxStackSize() == 1, "an enchanted book, one per slot");
		helper.assertTrue(blank.has(DataComponents.CONSUMABLE) && blank.getUseDuration(null) == cfg.channelTicks(),
				"vanilla channel of the grimoire's minimum length without a spell");
		UseCooldown cooldown = blank.get(DataComponents.USE_COOLDOWN);
		helper.assertTrue(cooldown != null && cooldown.cooldownGroup().map(GrimoireItems.COOLDOWN_GROUP::equals).orElse(false),
				"own cooldown group");
		CustomModelData model = blank.get(DataComponents.CUSTOM_MODEL_DATA);
		helper.assertTrue(model != null && List.of("empty").equals(model.strings()), "custom_model_data: empty: " + model);
		helper.assertTrue(blank.get(DataComponents.ITEM_NAME).getString().contains("Grimoire"), "named");
		helper.assertTrue(blank.get(DataComponents.LORE).lines().size() >= 8, "pages and usage in the lore");
		if (!UniqueVisuals.enabled()) {
			helper.assertTrue(blank.get(DataComponents.ITEM_MODEL).equals(blank.getPrototype().get(DataComponents.ITEM_MODEL)),
					"no resource pack: the vanilla look");
		}
		ItemStack written = GrimoireItems.create(pages(1, "blizzard", "seisme"));
		model = written.get(DataComponents.CUSTOM_MODEL_DATA);
		helper.assertTrue(model != null && List.of("seisme").equals(model.strings()), "custom_model_data: the selected scroll");
		String name = written.get(DataComponents.ITEM_NAME).getString();
		helper.assertTrue(name.contains("Séisme") && name.contains("(2/4)"), "name with the selected spell: " + name);
		StringBuilder lore = new StringBuilder();
		written.get(DataComponents.LORE).lines().forEach(line -> lore.append(line.getString()).append('|'));
		helper.assertTrue(lore.indexOf("Blizzard") >= 0 && lore.indexOf("faim") >= 0 && lore.indexOf("{") < 0, "pages and costs: " + lore);

		// Drops: the luckiest ruin boss roll gives the grimoire, horde bosses never do.
		helper.assertTrue(GrimoireItems.isGrimoire(ScrollDrops.roll("ruin_boss", LUCKY)), "ruin boss: grimoire");
		helper.assertTrue(GrimoireItems.isGrimoire(ScrollDrops.roll("nether_ruin_boss", LUCKY)), "nether ruin boss: grimoire");
		ItemStack boss = ScrollDrops.roll("boss", LUCKY);
		helper.assertTrue(ScrollItems.isScroll(boss) && !GrimoireItems.isGrimoire(boss), "horde boss: a scroll");
		helper.assertTrue(ScrollModule.tradePrice(blank, 2) == 0 && ScrollModule.tradePrice(blank, 3) == cfg.tradePrice, "sold from the tier III hub");
		for (int i = 0; i < 20; i++) {
			helper.assertFalse(GrimoireItems.isGrimoire(ScrollModule.tradeRoll(2)), "never at the tier II hub");
		}

		// Admin command (pages given by id), then the list.
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 2);
		ServerPlayer player = TestSupport.survivor(helper, ground);
		TestSupport.console(level, "execute as " + player.getStringUUID() + " run guild admin scroll grimoire @s blizzard seisme");
		ItemStack given = findGrimoire(player);
		helper.assertTrue(GrimoireItems.pages(given).equals(pages(0, "blizzard", "seisme")), "admin grimoire: " + GrimoireItems.pages(given));
		TestSupport.console(level, "guild admin scroll list");
		helper.succeed();
	}

	/** A loot table can hold a grimoire with only its custom data, on any item: it is completed in the inventory. */
	@GameTest(maxTicks = 80)
	public void customDataOnlyGrimoireIsCompleted(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 2);
		ServerPlayer player = TestSupport.survivor(helper, ground);
		ItemStack raw = new ItemStack(Items.BOOK);
		GuildyItems.tag(raw, GrimoireItems.KIND, new CompoundTag());
		player.getInventory().add(raw);
		helper.succeedWhen(() -> {
			ItemStack stack = findGrimoire(player);
			helper.assertTrue(stack.is(Items.ENCHANTED_BOOK), "rebuilt on the grimoire's base item: " + stack);
			helper.assertTrue(stack.has(DataComponents.CONSUMABLE) && stack.has(DataComponents.USE_COOLDOWN), "components added");
			helper.assertTrue(GrimoireItems.pages(stack).filled() == 0, "blank pages");
		});
	}

	// ================================================================== fusion

	@GameTest(maxTicks = 60)
	public void fusingByInventoryClicks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 2);
		ServerPlayer player = TestSupport.survivor(helper, ground);
		InventoryMenu menu = player.inventoryMenu;
		Inventory inventory = player.getInventory();
		// Main inventory slot 9 = menu slot 9.
		ItemStack grimoire = GrimoireItems.create(GrimoireRules.Pages.EMPTY);
		inventory.setItem(9, grimoire);

		// A scroll carried by the cursor, right-clicked onto the grimoire: one scroll becomes the first page.
		menu.setCarried(ScrollDrops.create("blizzard", 2));
		menu.clicked(9, 1, ContainerInput.PICKUP, player);
		helper.assertTrue(inventory.getItem(9) == grimoire, "the grimoire stays in its slot");
		helper.assertTrue(GrimoireItems.pages(grimoire).equals(pages(0, "blizzard")), "first page written: " + GrimoireItems.pages(grimoire));
		helper.assertTrue("blizzard".equals(ScrollItems.idOf(menu.getCarried())) && menu.getCarried().getCount() == 1, "one scroll consumed");
		helper.assertTrue(grimoire.get(DataComponents.ITEM_NAME).getString().contains("Blizzard"), "renamed with its spell");

		// The same spell again: refused, nothing consumed.
		menu.clicked(9, 1, ContainerInput.PICKUP, player);
		helper.assertTrue(menu.getCarried().getCount() == 1 && GrimoireItems.pages(grimoire).filled() == 1, "duplicate refused");

		// The grimoire carried onto a stack of scrolls: one of them is fused.
		menu.setCarried(ItemStack.EMPTY);
		inventory.setItem(9, ItemStack.EMPTY);
		inventory.setItem(10, ScrollDrops.create("seisme", 3));
		menu.setCarried(grimoire);
		menu.clicked(10, 1, ContainerInput.PICKUP, player);
		helper.assertTrue(menu.getCarried() == grimoire, "the grimoire stays on the cursor");
		helper.assertTrue(GrimoireItems.pages(grimoire).equals(pages(0, "blizzard", "seisme")), "second page: " + GrimoireItems.pages(grimoire));
		helper.assertTrue(inventory.getItem(10).getCount() == 2, "one scroll of the stack consumed");

		// Four pages at most.
		for (String id : List.of("rappel", "benediction")) {
			inventory.setItem(11, ScrollDrops.create(id, 1));
			menu.clicked(11, 1, ContainerInput.PICKUP, player);
			helper.assertTrue(inventory.getItem(11).isEmpty(), id + " fused");
		}
		inventory.setItem(11, ScrollDrops.create("egide", 1));
		menu.clicked(11, 1, ContainerInput.PICKUP, player);
		helper.assertTrue(inventory.getItem(11).getCount() == 1 && menu.getCarried() == grimoire, "full grimoire: refused, nothing moved");
		helper.assertTrue(GrimoireItems.pages(grimoire).equals(pages(0, "blizzard", "seisme", "rappel", "benediction")), "four pages");
		menu.setCarried(ItemStack.EMPTY);
		helper.succeed();
	}

	// ================================================================== pages

	@GameTest(maxTicks = 60)
	public void sneakUseTurnsPages(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 2);
		ServerPlayer player = TestSupport.survivor(helper, ground);
		ItemStack grimoire = GrimoireItems.create(pages(0, "blizzard", "seisme", "", "rappel"));
		player.setItemInHand(InteractionHand.MAIN_HAND, grimoire);
		player.setShiftKeyDown(true);
		try {
			InteractionResult result = rightClick(player);
			helper.assertTrue(result instanceof InteractionResult.Success && !player.isUsingItem(), "page turned, no incantation: " + result);
			helper.assertTrue(GrimoireItems.pages(grimoire).selected() == 1, "second page selected");
			String name = grimoire.get(DataComponents.ITEM_NAME).getString();
			helper.assertTrue(name.contains("Séisme") && name.contains("(2/4)"), "renamed: " + name);
			helper.assertTrue(List.of("seisme").equals(grimoire.get(DataComponents.CUSTOM_MODEL_DATA).strings()), "model follows the page");
			rightClick(player);
			helper.assertTrue(GrimoireItems.pages(grimoire).selected() == 3, "blank page skipped");
			rightClick(player);
			helper.assertTrue(GrimoireItems.pages(grimoire).selected() == 0, "back to the first page");
			helper.assertFalse(player.getCooldowns().isOnCooldown(grimoire), "turning pages costs no cooldown");

			// Sneak + swap hands: the pages menu opens, the hands are not swapped.
			player.connection.handlePlayerAction(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
					BlockPos.ZERO, Direction.DOWN));
			helper.assertTrue(player.getMainHandItem() == grimoire, "not swapped");
			helper.assertTrue(player.containerMenu != player.inventoryMenu, "pages menu open");
			player.closeContainer();
		} finally {
			player.setShiftKeyDown(false);
		}
		// An empty grimoire opens its menu on right click (next tick).
		player.setItemInHand(InteractionHand.MAIN_HAND, GrimoireItems.create(GrimoireRules.Pages.EMPTY));
		helper.assertTrue(rightClick(player) == InteractionResult.FAIL && !player.isUsingItem(), "nothing to cast");
		helper.succeedWhen(() -> helper.assertTrue(player.containerMenu != player.inventoryMenu, "menu of the empty grimoire"));
	}

	// ================================================================== casting

	@GameTest(environment = ENV, maxTicks = 200)
	public void castingCostsFood(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 8);
		TestSupport.forceChunks(level, ground, 1);
		ServerPlayer caster = TestSupport.survivor(helper, ground);
		face(caster, level, ground);
		List<Husk> husks = new ArrayList<>(List.of(husk(level, ground.east(3)), husk(level, ground.east(5))));
		ItemStack grimoire = GrimoireItems.create(pages(0, "seisme"));
		caster.setItemInHand(InteractionHand.MAIN_HAND, grimoire);
		caster.getInventory().setItem(20, ScrollDrops.create("seisme", 2));
		int cost = GrimoireItems.foodCost(ScrollModule.config().scroll("seisme"));
		helper.assertTrue(cost == 5, "an epic spell costs 5 food points: " + cost);

		// Not enough food: refused, nothing happens.
		feed(caster, cost - 1, 0.0F);
		helper.assertTrue(rightClick(caster) == InteractionResult.FAIL && !caster.isUsingItem(), "too hungry: refused");
		helper.assertTrue(caster.getFoodData().getFoodLevel() == cost - 1, "no food spent");
		helper.assertFalse(caster.getCooldowns().isOnCooldown(grimoire), "no cooldown");
		helper.runAfterDelay(10, () -> {
			for (Husk husk : husks) {
				helper.assertTrue(!husk.isRemoved() && husk.getHealth() >= husk.getMaxHealth(), "refused cast: monsters untouched");
			}
			// Enough food: cast, food spent, nothing consumed.
			feed(caster, 20, 0.0F);
			InteractionResult result = rightClick(caster);
			helper.assertTrue(result instanceof InteractionResult.Success && caster.isUsingItem(), "incantation started: " + result);
			channel(caster);
			helper.assertFalse(caster.isUsingItem(), "channel completed");
			helper.assertTrue(caster.getMainHandItem() == grimoire && grimoire.getCount() == 1, "the grimoire is never consumed");
			helper.assertTrue(count(caster, "seisme") == 2, "no scroll consumed");
			helper.assertTrue(caster.getFoodData().getFoodLevel() == 20 - cost, "food spent: " + caster.getFoodData().getFoodLevel());
			helper.assertTrue(caster.getCooldowns().isOnCooldown(grimoire), "grimoire cooldown started");
			InteractionResult again = rightClick(caster);
			helper.assertTrue(again == InteractionResult.FAIL && !caster.isUsingItem(), "on cooldown: refused");

			// Saturation goes first.
			caster.getCooldowns().removeCooldown(GrimoireItems.COOLDOWN_GROUP);
			feed(caster, 20, 3.0F);
			helper.assertTrue(rightClick(caster) instanceof InteractionResult.Success, "second cast started");
			channel(caster);
			helper.assertTrue(caster.getFoodData().getSaturationLevel() == 0.0F && caster.getFoodData().getFoodLevel() == 20 - (cost - 3),
					"3 from saturation, the rest from food: " + caster.getFoodData().getFoodLevel());
			helper.succeedWhen(() -> {
				for (Husk husk : husks) {
					helper.assertTrue(husk.isRemoved() || husk.isDeadOrDying() || husk.getHealth() < husk.getMaxHealth(),
							"husk at " + husk.blockPosition() + " hurt by the earthquake");
				}
				for (Husk husk : husks) {
					husk.discard();
				}
			});
		});
	}

	@GameTest(environment = ENV, maxTicks = 100)
	public void creativeCastsForFree(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 6);
		ServerPlayer builder = TestSupport.player(helper, ground);
		ItemStack grimoire = GrimoireItems.create(pages(0, "benediction"));
		builder.setItemInHand(InteractionHand.MAIN_HAND, grimoire);
		feed(builder, 1, 0.0F);
		helper.assertTrue(rightClick(builder) instanceof InteractionResult.Success && builder.isUsingItem(), "creative: no food needed");
		channel(builder);
		helper.assertTrue(builder.getMainHandItem() == grimoire && builder.getFoodData().getFoodLevel() == 1, "creative: free");
		helper.succeedWhen(() -> helper.assertTrue(builder.hasEffect(MobEffects.REGENERATION), "but cast (regeneration)"));
	}

	@GameTest(environment = ENV, maxTicks = 200)
	public void grimoireChannelFollowsTheSpell(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 6);
		ScrollConfig cfg = ScrollModule.config();
		ScrollConfig.Grimoire g = cfg.grimoire;
		// The item carries the selected spell's channel: long enough to see the circle form, never longer than the scroll.
		ItemStack pagesBook = GrimoireItems.create(pages(0, "benediction", "singularite", "revelation"));
		int[] lengths = new int[3];
		for (int page = 0; page < 3; page++) {
			GrimoireRules.Pages turned = GrimoireItems.pages(pagesBook).select(page);
			GrimoireItems.apply(pagesBook, turned);
			ScrollConfig.ScrollDef def = GrimoireItems.selectedDef(turned);
			int ticks = pagesBook.getUseDuration(null);
			lengths[page] = ticks;
			helper.assertTrue(def != null && ticks == g.channelTicks(def) && ticks == GrimoireItems.channelTicks(turned),
					"page " + page + ": the item's channel follows its spell, got " + ticks);
			helper.assertTrue(ticks >= 15 && ticks <= def.channelTicks(), def.id + ": at least 0.75 s, never longer than the scroll: " + ticks);
		}
		helper.assertTrue(lengths[0] == 20, "Bénédiction (1.25 s scroll): 1 s, got " + lengths[0]);
		helper.assertTrue(lengths[1] > lengths[0], "Singularité (2 s scroll) keeps a longer channel: " + lengths[1]);
		StringBuilder lore = new StringBuilder();
		pagesBook.get(DataComponents.LORE).lines().forEach(line -> lore.append(line.getString()).append('|'));
		helper.assertTrue(lore.indexOf("Incantation : " + seconds(g.channelSeconds(cfg.scroll("revelation")))) >= 0,
				"the lore shows the selected spell's channel: " + lore);

		// Cast for real: the vanilla use lasts exactly the new channel, then the spell fires.
		ServerPlayer caster = TestSupport.survivor(helper, ground);
		ItemStack grimoire = GrimoireItems.create(pages(0, "benediction"));
		caster.setItemInHand(InteractionHand.MAIN_HAND, grimoire);
		feed(caster, 20, 0.0F);
		helper.assertTrue(rightClick(caster) instanceof InteractionResult.Success && caster.isUsingItem(), "incantation started");
		int expected = g.channelTicks(cfg.scroll("benediction"));
		int ticks = 0;
		while (caster.isUsingItem() && ticks < 400) {
			caster.doTick();
			ticks++;
		}
		helper.assertTrue(ticks == expected && expected > 8, "a channel of " + expected + " ticks (0.4 s before), took " + ticks);
		helper.assertTrue(caster.getCooldowns().isOnCooldown(grimoire), "cast: the grimoire's cooldown started");
		helper.assertTrue(caster.getFoodData().getFoodLevel() == 20 - GrimoireItems.foodCost(cfg.scroll("benediction")), "food cost unchanged");
		helper.succeedWhen(() -> helper.assertTrue(caster.hasEffect(MobEffects.REGENERATION), "the blessing is cast"));
	}

	/** "0,75 s" as the lore writes it. */
	private static String seconds(double value) {
		return Spell.number(value) + " s";
	}
}
