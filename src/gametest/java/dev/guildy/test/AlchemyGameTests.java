package dev.guildy.test;

import dev.guildy.alchemy.AlchemyModule;
import dev.guildy.alchemy.Brews;
import dev.guildy.alchemy.Elixirs;
import dev.guildy.alchemy.MagicBoosts;
import dev.guildy.core.GuildyItems;
import dev.guildy.core.Hooks;
import dev.guildy.core.Lang;
import dev.guildy.core.MagicBoost;
import dev.guildy.core.ScrollDrops;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.scroll.ScrollConfig;
import dev.guildy.scroll.ScrollItems;
import dev.guildy.scroll.ScrollModule;
import dev.guildy.villager.AlchemistConfig;
import dev.guildy.villager.Alchemists;
import dev.guildy.villager.AlchemyJob;
import dev.guildy.villager.CraftData;
import dev.guildy.villager.ItemClassifier;
import dev.guildy.villager.StorageChest;
import dev.guildy.villager.Task;
import dev.guildy.villager.VillagerManager;
import dev.guildy.villager.VillagerModule;
import dev.guildy.villager.VillagerRecord;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.containerwrappers.SlotBasedWrapperMenu;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Alchemists brewing potions and elixirs: an alchemist brews healing potions at a brewing stand from the water bottles,
 * nether wart, glistering melon and blaze powder of the storage and stores them in the potion chest (or from glass bottles
 * filled at a water cauldron); its menu sets its role and the guild's potion stocks; an alchemist whose
 * role is books but who has no book falls back to potions (reason in its menu), then goes back to books when some arrive;
 * three alchemists of a guild that can do every job get one role each; drinking an Élixir arcanique makes a spell hit
 * harder, a clairvoyance boost shortens the incantation and the cooldown, satiety halves the grimoire's food cost.
 */
public class AlchemyGameTests {
	private static final String DAY = "guildy-test:day";

	/** A guild with alchemists, a deposit chest (supplies) and a potion chest, a brewing stand and maybe a table. */
	private record Lab(ServerLevel level, Guild guild, ServerPlayer leader, List<VillagerRecord> alchemists, Container deposit, Container potions,
			BlockPos stand) {
	}

	private static Lab lab(GameTestHelper helper, String prefix, int count, boolean table) throws Exception {
		ServerLevel level = helper.getLevel();
		TestSupport.console(level, "time set 1000");
		BlockPos ground = TestSupport.region(level, 16);
		TestSupport.forceChunks(level, ground, 2);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, prefix);
		guild.level = Math.max(guild.level, VillagerModule.config().alchemist.minGuildLevel);
		while (VillagerManager.maxVillagers(guild) < count && guild.level < 500) {
			guild.level++;
		}
		for (int i = 0; i < count; i++) {
			TestSupport.console(level, "guild admin villager spawn " + guild.name);
			level.getServer().getCommands().performPrefixedCommand(leader.createCommandSourceStack(), "guild villagers accept");
		}
		List<VillagerRecord> alchemists = new ArrayList<>(guild.villagers.members.values());
		helper.assertTrue(alchemists.size() == count, "candidates accepted as guild villagers: " + alchemists.size());
		BlockPos depositPos = ground.offset(4, 1, 0);
		level.setBlockAndUpdate(depositPos, Blocks.CHEST.defaultBlockState());
		guild.villagers.chests.add(new StorageChest(depositPos, StorageChest.Role.DEPOSIT, ""));
		BlockPos potionPos = ground.offset(4, 1, 4);
		level.setBlockAndUpdate(potionPos, Blocks.CHEST.defaultBlockState());
		String potions = ItemClassifier.categoryOf(new ItemStack(Items.POTION));
		guild.villagers.chests.add(new StorageChest(potionPos, StorageChest.Role.CATEGORY, potions));
		BlockPos stand = ground.offset(-5, 1, 5);
		level.setBlockAndUpdate(stand, Blocks.BREWING_STAND.defaultBlockState());
		if (table) {
			level.setBlockAndUpdate(ground.offset(-6, 1, -6), Blocks.ENCHANTING_TABLE.defaultBlockState());
		}
		// Only healing potions are wanted here (deterministic): every other product at 0.
		for (String product : AlchemyModule.config().menuProducts()) {
			guild.villagers.potionTargets.put(product, 0);
		}
		guild.villagers.potionTargets.put("healing", 3);
		return new Lab(level, guild, leader, alchemists, (Container) level.getBlockEntity(depositPos), (Container) level.getBlockEntity(potionPos), stand);
	}

	/** Supplies of one batch of healing potions (two brews, three bottles). */
	private static void healingSupplies(Container deposit) {
		add(deposit, new ItemStack(Items.NETHER_WART, 2));
		add(deposit, new ItemStack(Items.GLISTERING_MELON_SLICE, 2));
		add(deposit, new ItemStack(Items.BLAZE_POWDER, 1));
		for (int i = 0; i < 3; i++) {
			add(deposit, Brews.water());
		}
	}

	private static void setTask(Guild guild, VillagerRecord record, Task task, ServerPlayer player) throws ReflectiveOperationException {
		Method setTask = Class.forName("dev.guildy.villager.VillagerManager")
				.getDeclaredMethod("setTask", Guild.class, VillagerRecord.class, Task.class, ServerPlayer.class);
		setTask.setAccessible(true);
		setTask.invoke(null, guild, record, task, player);
	}

	@GameTest(environment = DAY, maxTicks = 3600)
	public void alchemistBrewsHealingPotionsIntoTheStorage(GameTestHelper helper) throws Exception {
		AlchemistConfig cfg = VillagerModule.config().alchemist;
		int seconds = cfg.secondsPerBrew;
		cfg.secondsPerBrew = 1;
		Lab lab = lab(helper, "Alambic", 1, false);
		healingSupplies(lab.deposit());
		VillagerRecord record = lab.alchemists().getFirst();
		setTask(lab.guild(), record, Task.ALCHEMIST, lab.leader());
		helper.succeedWhen(() -> {
			int bottles = countBrews(lab.deposit()) + countBrews(lab.potions()) + carriedBrews(record);
			if (bottles != 3) {
				helper.fail("bottles lost or duplicated: " + bottles);
			}
			int healing = countKey(lab.potions(), "healing");
			helper.assertTrue(healing == 3, "waiting for 3 healing potions in the potion chest (" + healing + ", status "
					+ Lang.strip(status(lab.level(), record)) + ")");
			CraftData data = record.craft;
			helper.assertTrue(data != null && data.potions == 3 && "healing".equals(data.lastPotion), "potion statistics counted");
			helper.assertTrue(count(lab.deposit(), Items.NETHER_WART) == 1 && count(lab.deposit(), Items.GLISTERING_MELON_SLICE) == 1,
					"one nether wart and one melon slice used for the three bottles");
			helper.assertTrue(count(lab.deposit(), Items.BLAZE_POWDER) == 0 && data.brewFuel == cfg.brewsPerFuel - 2,
					"one blaze powder burnt for two brews (fuel left " + data.brewFuel + ")");
			for (int i = 0; i < lab.potions().getContainerSize(); i++) {
				ItemStack stack = lab.potions().getItem(i);
				if (!stack.isEmpty()) {
					PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
					helper.assertTrue(stack.is(Items.POTION) && contents != null && contents.isPotionWithoutCustomEffects(),
							"a vanilla healing potion, exactly like a brewing stand makes it");
				}
			}
			cfg.secondsPerBrew = seconds;
			GuildManager.disband(lab.guild());
		});
	}

	/** Glass bottles of the storage are filled at a water cauldron next to the stand (one level per bottle, like vanilla). */
	@GameTest(environment = DAY, maxTicks = 3600)
	public void alchemistFillsGlassBottlesAtACauldron(GameTestHelper helper) throws Exception {
		AlchemistConfig cfg = VillagerModule.config().alchemist;
		int seconds = cfg.secondsPerBrew;
		cfg.secondsPerBrew = 1;
		Lab lab = lab(helper, "Chaudron", 1, false);
		BlockPos cauldron = lab.stand().offset(2, 0, 0);
		lab.level().setBlockAndUpdate(cauldron, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
		add(lab.deposit(), new ItemStack(Items.NETHER_WART, 1));
		add(lab.deposit(), new ItemStack(Items.GLISTERING_MELON_SLICE, 1));
		add(lab.deposit(), new ItemStack(Items.BLAZE_POWDER, 1));
		add(lab.deposit(), new ItemStack(Items.GLASS_BOTTLE, 3));
		VillagerRecord record = lab.alchemists().getFirst();
		setTask(lab.guild(), record, Task.ALCHEMIST, lab.leader());
		helper.succeedWhen(() -> {
			int bottles = countBrews(lab.deposit()) + countBrews(lab.potions()) + carriedBrews(record) + count(lab.deposit(), Items.GLASS_BOTTLE)
					+ carried(record, Items.GLASS_BOTTLE);
			if (bottles != 3) {
				helper.fail("bottles lost or duplicated: " + bottles);
			}
			helper.assertTrue(countKey(lab.potions(), "healing") == 3, "waiting for 3 healing potions from glass bottles (status "
					+ Lang.strip(status(lab.level(), record)) + ")");
			helper.assertTrue(lab.level().getBlockState(cauldron).is(Blocks.CAULDRON), "three bottles filled: the cauldron is empty");
			cfg.secondsPerBrew = seconds;
			GuildManager.disband(lab.guild());
		});
	}

	@GameTest(environment = DAY, maxTicks = 4800)
	public void alchemistWithoutBooksFallsBackToPotions(GameTestHelper helper) throws Exception {
		AlchemistConfig cfg = VillagerModule.config().alchemist;
		int brew = cfg.secondsPerBrew;
		int level = cfg.secondsPerLevel;
		cfg.secondsPerBrew = 1;
		cfg.secondsPerLevel = 1;
		Lab lab = lab(helper, "Repli", 1, true);
		healingSupplies(lab.deposit());
		VillagerRecord record = lab.alchemists().getFirst();
		setTask(lab.guild(), record, Task.ALCHEMIST, lab.leader());
		// Its role: books, but the storage has none.
		Alchemists.setRole(lab.guild(), record, AlchemyJob.BOOKS);
		String[] menu = {null};
		boolean[] booksAdded = {false};
		helper.succeedWhen(() -> {
			Guild guild = lab.guild();
			if (menu[0] == null && Alchemists.working(guild, record.uuid) == AlchemyJob.POTIONS) {
				helper.assertTrue(Alchemists.fallbackReason(guild, record.uuid).contains("pas de livres"),
						"the reason of the fallback: " + Alchemists.fallbackReason(guild, record.uuid));
				Hooks.openVillagers.accept(lab.leader(), guild);
				menu[0] = Lang.strip(lore(lab.leader(), 0));
				lab.leader().closeContainer();
			}
			helper.assertTrue(menu[0] != null, "waiting for the fallback to potions");
			helper.assertTrue(menu[0].contains("Rôle : Livres (choisi)") && menu[0].contains("pas de livres") && menu[0].contains("→ Potions"),
					"role and fallback shown in the villager menu: " + menu[0]);
			CraftData data = record.craft;
			if (!booksAdded[0]) {
				helper.assertTrue(data != null && data.potions == 3, "waiting for the healing potions brewed instead");
				helper.assertTrue(data.books == 0, "no book without books");
				add(lab.deposit(), new ItemStack(Items.BOOK, 2));
				booksAdded[0] = true;
				helper.fail("books added");
			}
			helper.assertTrue(data.books >= 1, "waiting for an enchanted book: back to its own job");
			helper.assertTrue(Alchemists.role(guild, record.uuid) == AlchemyJob.BOOKS, "role kept");
			cfg.secondsPerBrew = brew;
			cfg.secondsPerLevel = level;
			GuildManager.disband(guild);
		});
	}

	@GameTest(environment = DAY, maxTicks = 2400)
	public void threeAlchemistsShareTheJobs(GameTestHelper helper) throws Exception {
		Lab lab = lab(helper, "Roles", 3, true);
		healingSupplies(lab.deposit());
		add(lab.deposit(), new ItemStack(Items.BOOK, 6));
		add(lab.deposit(), new ItemStack(Items.PAPER, 12));
		for (VillagerRecord record : lab.alchemists()) {
			setTask(lab.guild(), record, Task.ALCHEMIST, lab.leader());
		}
		helper.succeedWhen(() -> {
			Set<AlchemyJob> roles = EnumSet.noneOf(AlchemyJob.class);
			for (VillagerRecord record : lab.alchemists()) {
				AlchemyJob role = Alchemists.role(lab.guild(), record.uuid);
				helper.assertTrue(role != null, "waiting for the roles");
				roles.add(role);
			}
			helper.assertTrue(roles.equals(EnumSet.allOf(AlchemyJob.class)), "one alchemist per job (balanced): " + roles);
			GuildManager.disband(lab.guild());
		});
	}

	/**
	 * The alchemist's menu: the role button cycles Auto -> Livres -> Parchemins (right click back), the stock menu changes
	 * the guild's targets (a target back at its default is forgotten) and resets them.
	 */
	@GameTest(maxTicks = 200)
	public void alchemistMenuSetsTheRoleAndTheStocks(GameTestHelper helper) throws Exception {
		Lab lab = lab(helper, "Menu", 1, false);
		Guild guild = lab.guild();
		ServerPlayer leader = lab.leader();
		VillagerRecord record = lab.alchemists().getFirst();
		setTask(guild, record, Task.ALCHEMIST, leader);
		guild.villagers.potionTargets.clear();
		openDetail(helper, leader, guild);
		helper.assertTrue(Lang.strip(lore(leader, 19)).contains("Rôle : Auto"), "automatic role at first: " + lore(leader, 19));
		click(helper, leader, 19, false);
		helper.assertTrue(Alchemists.role(guild, record.uuid) == AlchemyJob.BOOKS && "books".equals(record.craft.role), "role: books");
		click(helper, leader, 19, false);
		helper.assertTrue(Alchemists.role(guild, record.uuid) == AlchemyJob.SCROLLS, "role: scrolls");
		click(helper, leader, 19, true);
		click(helper, leader, 19, true);
		helper.assertTrue(record.craft.role.isEmpty(), "right clicks: back to automatic");

		click(helper, leader, 20, false);
		String first = AlchemyModule.config().menuProducts().getFirst();
		int base = AlchemyModule.config().defaultTarget(first);
		helper.assertTrue(Lang.strip(lore(leader, 0)).contains("Stock voulu : " + base), "default target shown: " + lore(leader, 0));
		click(helper, leader, 0, false);
		helper.assertTrue(guild.villagers.potionTargets.get(first) == base + 1, "left click: +1");
		click(helper, leader, 0, true);
		helper.assertTrue(!guild.villagers.potionTargets.containsKey(first), "back at the default: forgotten");
		click(helper, leader, 0, true);
		helper.assertTrue(guild.villagers.potionTargets.get(first) == Math.max(0, base - 1), "right click: -1");
		click(helper, leader, 49, false);
		helper.assertTrue(guild.villagers.potionTargets.isEmpty(), "reset to the defaults");
		leader.closeContainer();
		GuildManager.disband(guild);
		helper.succeed();
	}

	private static void openDetail(GameTestHelper helper, ServerPlayer player, Guild guild) {
		Hooks.openVillagers.accept(player, guild);
		click(helper, player, 0, false);
	}

	private static void click(GameTestHelper helper, ServerPlayer player, int slot, boolean right) {
		helper.assertTrue(player.containerMenu instanceof SlotBasedWrapperMenu, "a menu is open");
		((SlotBasedWrapperMenu) player.containerMenu).getBackingGui().click(slot, right ? ClickType.MOUSE_RIGHT : ClickType.MOUSE_LEFT,
				ContainerInput.PICKUP);
	}

	/**
	 * Drinking an Élixir arcanique (the vanilla drink completes, the magic boost starts at its end): the same solar nova
	 * hurts a zombie 25 % more. Clairvoyance shortens the incantation and the cooldown, satiety halves the food cost.
	 */
	@GameTest(environment = DAY, maxTicks = 400)
	public void arcaneElixirMakesSpellsStronger(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 10);
		TestSupport.forceChunks(level, ground, 1);
		ServerPlayer caster = TestSupport.survivor(helper, ground.west(8));
		Guild guild = TestSupport.foundGuild(helper, caster, ground.west(8), "Arcane");
		caster.teleportTo(level, ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, Set.of(), -90.0F, 0.0F, true);

		ItemStack elixir = Elixirs.create("arcanique");
		helper.assertTrue("arcanique".equals(Elixirs.idOf(elixir)) && GuildyItems.is(elixir, Elixirs.KIND), "custom_data guildy kind elixir + id");
		helper.assertTrue(elixir.is(Items.POTION) && elixir.getHoverName().getString().contains("Élixir arcanique"), "a named potion");
		helper.assertTrue("elixir:arcanique".equals(Brews.keyOf(elixir)), "an elixir product");
		ItemLore lore = elixir.get(DataComponents.LORE);
		StringBuilder text = new StringBuilder();
		for (Component line : lore.lines()) {
			text.append(line.getString()).append('|');
		}
		helper.assertTrue(text.toString().contains("Sorts +25 %"), "the boost in the lore: " + text);
		ItemStack colosse = Elixirs.create("colosse");
		PotionContents contents = colosse.get(DataComponents.POTION_CONTENTS);
		helper.assertTrue(contents != null && contents.customEffects().size() == 2 && contents.customColor().isPresent(),
				"Élixir du colosse: strength II and resistance, its own colour");

		ScrollConfig.ScrollDef nova = ScrollModule.config().scroll("nova_solaire");
		Zombie first = zombie(level, ground.east(3));
		castNova(helper, caster);
		double[] dealt = new double[2];
		Zombie[] second = new Zombie[1];
		helper.startSequence()
				// The sphere expands for 14 ticks and hits each monster once.
				.thenIdle(40)
				.thenExecute(() -> {
					dealt[0] = first.getMaxHealth() - first.getHealth();
					helper.assertTrue(dealt[0] > 0, "the nova hurts the zombie");
					first.discard();
					caster.getCooldowns().removeCooldown(ScrollItems.cooldownGroup(nova));
					// The vanilla drink: use, 32 ticks of drinking, the bottle comes back.
					caster.setItemInHand(InteractionHand.MAIN_HAND, elixir);
					caster.gameMode.useItem(caster, level, elixir, InteractionHand.MAIN_HAND);
					helper.assertTrue(caster.isUsingItem(), "drinking the elixir");
					for (int i = 0; i < 100 && caster.isUsingItem(); i++) {
						caster.doTick();
					}
					helper.assertTrue(caster.getMainHandItem().is(Items.GLASS_BOTTLE), "drunk: the empty bottle is left, got " + caster.getMainHandItem());
					helper.assertTrue(Math.abs(MagicBoost.powerMultiplier(caster) - 1.25) < 1.0E-6, "spells +25 %: " + MagicBoost.powerMultiplier(caster));
					helper.assertTrue(MagicBoosts.remaining(caster).getOrDefault("arcanique", 0) > 5000, "five minutes of boost");
					second[0] = zombie(level, ground.east(3));
					castNova(helper, caster);
				})
				.thenIdle(40)
				.thenExecute(() -> {
					dealt[1] = second[0].getMaxHealth() - second[0].getHealth();
					second[0].discard();
					caster.sendSystemMessage(Component.literal("solar nova: " + dealt[0] + " then " + dealt[1] + " with the arcane elixir"));
					helper.assertTrue(Math.abs(dealt[1] - dealt[0] * 1.25) < 0.01, "the nova hurts 25 % more: " + dealt[0] + " -> " + dealt[1]);

					// Clairvoyance: shorter incantation and cooldown; satiety: half the food of a grimoire.
					caster.getCooldowns().removeCooldown(ScrollItems.cooldownGroup(nova));
					helper.assertTrue(MagicBoosts.give(caster, "clairvoyance", 0) && MagicBoosts.give(caster, "satiete", 0), "boosts given");
					helper.assertTrue(Math.abs(MagicBoost.cooldownMultiplier(caster) - 0.7) < 1.0E-6, "cooldowns -30 %");
					helper.assertTrue(MagicBoost.foodCost(caster, 4) == 2 && MagicBoost.foodCost(caster, 3) == 2, "grimoire food -50 % (rounded up)");
					Zombie third = zombie(level, ground.east(3));
					caster.setItemInHand(InteractionHand.MAIN_HAND, ScrollDrops.create("nova_solaire", 1));
					InteractionResult result = UseItemCallback.EVENT.invoker().interact(caster, level, InteractionHand.MAIN_HAND);
					helper.assertTrue(result instanceof InteractionResult.Success && caster.isUsingItem(), "incantation started");
					int expected = Math.round(nova.channelTicks() * 0.7F);
					helper.assertTrue(Math.abs(caster.getUseItemRemainingTicks() - expected) <= 1,
							"incantation -30 %: " + caster.getUseItemRemainingTicks() + " ticks instead of " + nova.channelTicks());
					for (int i = 0; i < 400 && caster.isUsingItem(); i++) {
						caster.doTick();
					}
					helper.assertTrue(caster.getMainHandItem().isEmpty(), "cast at the end of the shorter incantation");
					third.discard();
					MagicBoosts.clear(caster);
					helper.assertTrue(MagicBoost.powerMultiplier(caster) == 1.0, "boosts removed");
					GuildManager.disband(guild);
				})
				.thenSucceed();
	}

	/** Starts and completes the incantation of a solar nova from a fresh scroll (the spell then runs on its own). */
	private static void castNova(GameTestHelper helper, ServerPlayer caster) {
		ItemStack scroll = ScrollDrops.create("nova_solaire", 1);
		caster.setItemInHand(InteractionHand.MAIN_HAND, scroll);
		InteractionResult result = UseItemCallback.EVENT.invoker().interact(caster, caster.level(), InteractionHand.MAIN_HAND);
		helper.assertTrue(result instanceof InteractionResult.Success && caster.isUsingItem(), "nova incantation started, got " + result);
		for (int i = 0; i < 400 && caster.isUsingItem(); i++) {
			caster.doTick();
		}
		helper.assertTrue(!caster.isUsingItem() && caster.getMainHandItem().isEmpty(), "nova cast (scroll consumed)");
	}

	private static Zombie zombie(ServerLevel level, BlockPos ground) {
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		zombie.snapTo(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, 90.0F, 0.0F);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
		zombie.setHealth(1000);
		// Only the spell counts: no burning.
		zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		zombie.setNoAi(true);
		zombie.setPersistenceRequired();
		level.addFreshEntity(zombie);
		return zombie;
	}

	// ------------------------------------------------------------------ helpers

	private static int count(Container container, Item item) {
		int total = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (stack.is(item) && stack.getComponentsPatch().isEmpty()) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static int carried(VillagerRecord record, Item item) {
		int total = 0;
		for (ItemStack stack : record.carried) {
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static int countKey(Container container, String key) {
		int total = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (key.equals(Brews.keyOf(stack))) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static int countBrews(Container container) {
		int total = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (Brews.isBrew(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static int carriedBrews(VillagerRecord record) {
		int total = 0;
		for (ItemStack stack : record.carried) {
			if (Brews.isBrew(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static void add(Container container, ItemStack stack) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).isEmpty()) {
				container.setItem(i, stack);
				return;
			}
		}
		throw new IllegalStateException("chest full");
	}

	private static String lore(ServerPlayer player, int slot) {
		ItemStack stack = player.containerMenu.getSlot(slot).getItem();
		StringBuilder text = new StringBuilder(stack.getHoverName().getString());
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				text.append(" | ").append(line.getString());
			}
		}
		return text.toString();
	}

	private static String status(ServerLevel level, VillagerRecord record) {
		try {
			Class<?> gui = Class.forName("dev.guildy.villager.VillagerGui");
			Method statusText = gui.getDeclaredMethod("statusText", VillagerRecord.class, net.minecraft.world.entity.npc.villager.Villager.class);
			statusText.setAccessible(true);
			net.minecraft.world.entity.Entity entity = level.getEntity(record.uuid);
			return String.valueOf(statusText.invoke(null, record,
					entity instanceof net.minecraft.world.entity.npc.villager.Villager villager ? villager : null));
		} catch (ReflectiveOperationException e) {
			return "?";
		}
	}
}
