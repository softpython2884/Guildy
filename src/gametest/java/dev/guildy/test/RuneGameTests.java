package dev.guildy.test;

import dev.guildy.core.UniqueDrops;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.rune.RuneConfig;
import dev.guildy.rune.RuneForge;
import dev.guildy.rune.RuneItems;
import dev.guildy.rune.RuneModule;
import dev.guildy.rune.RuneTier;
import dev.guildy.shop.PlainItems;
import dev.guildy.villager.BlacksmithConfig;
import dev.guildy.villager.CraftData;
import dev.guildy.villager.Task;
import dev.guildy.villager.VillagerModule;
import dev.guildy.villager.VillagerRecord;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.Set;

/**
 * Runes: a blacksmith inscribes a rune on an armor piece of an armor stand (chance forced to 1, material fetched from the
 * storage); a common rune's bonus applies only while worn; the grindstone removes only the rune (enchantments kept, no
 * experience); a mythic rune is never drawn for anything but a netherite chestplate; the hit powers, the Phoenix rebirth
 * and the Dragon's glider and breath through the real game paths (monsters only).
 */
public class RuneGameTests {

	// ================================================================== blacksmith

	@GameTest(environment = "guildy-test:day", maxTicks = 2400)
	public void blacksmithInscribesARuneOnAStand(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		BlacksmithConfig smith = VillagerModule.config().blacksmith;
		RuneConfig runes = RuneModule.config();
		double chance = runes.inscription.chance;
		int seconds = runes.inscription.secondsPerInspection;
		int rare = runes.tiers.rare;
		int mythic = runes.tiers.mythic;
		runes.inscription.chance = 1.0;
		runes.inscription.secondsPerInspection = 1;
		// Common runes only: they all give an attribute bonus.
		runes.tiers.rare = 0;
		runes.tiers.mythic = 0;
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Runes");
		Guild guild = recruit.guild();
		guild.level = Math.max(guild.level, Math.max(smith.minGuildLevel, runes.inscription.minGuildLevel));
		BlockPos ground = recruit.ground();
		// The material of the runes, in the deposit chest.
		Container deposit = (Container) level.getBlockEntity(recruit.deposit());
		deposit.setItem(0, new ItemStack(Items.AMETHYST_SHARD, 5));

		BlockPos anvil = ground.offset(-6, 1, 6);
		level.setBlockAndUpdate(anvil, Blocks.ANVIL.defaultBlockState());
		ArmorStand stand = new ArmorStand(level, ground.getX() - 8 + 0.5, ground.getY() + 1, ground.getZ() + 6 + 0.5);
		// An intact chestplate: nothing to repair, the blacksmith inspects it.
		stand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		// A unique armor never receives a rune.
		ItemStack unique = UniqueDrops.create("couronne_du_roi_de_givre");
		stand.setItemSlot(EquipmentSlot.HEAD, unique.copy());
		level.addFreshEntity(stand);

		VillagerRecord record = recruit.record();
		ZoneGameTests.setTask(recruit, Task.BLACKSMITH);
		helper.succeedWhen(() -> {
			ItemStack piece = stand.getItemBySlot(EquipmentSlot.CHEST);
			helper.assertTrue(stand.isAlive() && piece.is(Items.IRON_CHESTPLATE), "the chestplate stays on the stand");
			helper.assertTrue(RuneItems.has(piece), "waiting for a rune on the chestplate");
			RuneConfig.RuneDef def = runes.rune(RuneItems.idOf(piece));
			helper.assertTrue(def != null && def.tier() == RuneTier.COMMON && def.allows("chest"), "a common chest rune: " + RuneItems.idOf(piece));
			boolean modifier = false;
			for (ItemAttributeModifiers.Entry entry : piece.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
				if (entry.modifier().id().equals(Identifier.fromNamespaceAndPath("guildy", "rune_" + def.id + "_chest"))) {
					modifier = true;
				}
			}
			helper.assertTrue(modifier, "the rune's attribute modifier guildy:rune_" + def.id + "_chest");
			helper.assertTrue(hasRuneLore(piece, def.name), "the rune's lore line");
			helper.assertTrue(Boolean.TRUE.equals(piece.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), "the rune shines");
			helper.assertTrue(!PlainItems.isPlain(piece), "a runed piece is not a plain item for the shop");
			helper.assertTrue(!RuneItems.has(stand.getItemBySlot(EquipmentSlot.HEAD)), "never a rune on a unique");
			CraftData data = record.craft;
			helper.assertTrue(data != null && data.runes == 1 && data.lastRune.equals(def.id), "rune statistics counted");
			int shards = count(deposit, Items.AMETHYST_SHARD) + carried(record, Items.AMETHYST_SHARD);
			helper.assertTrue(shards == 4, "one amethyst shard used: " + shards + " left");
			helper.assertTrue(TestSupport.chat(recruit.leader()).stream().anyMatch(line -> line.contains("a gravé une rune")),
					"the guild is told");
			runes.inscription.chance = chance;
			runes.inscription.secondsPerInspection = seconds;
			runes.tiers.rare = rare;
			runes.tiers.mythic = mythic;
			stand.discard();
			GuildManager.disband(guild);
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 100)
	public void mythicRunesOnlyForNetheriteChestplates(GameTestHelper helper) {
		RuneConfig runes = RuneModule.config();
		int common = runes.tiers.common;
		int rare = runes.tiers.rare;
		int mythic = runes.tiers.mythic;
		try {
			// Mythic runes weigh almost everything now.
			runes.tiers.common = 1;
			runes.tiers.rare = 1;
			runes.tiers.mythic = 10000;
			RandomSource random = RandomSource.create(42);
			ItemStack diamond = new ItemStack(Items.DIAMOND_CHESTPLATE);
			ItemStack netheriteLegs = new ItemStack(Items.NETHERITE_LEGGINGS);
			ItemStack netherite = new ItemStack(Items.NETHERITE_CHESTPLATE);
			int mythics = 0;
			for (int i = 0; i < 500; i++) {
				RuneConfig.RuneDef onDiamond = RuneForge.roll(diamond, random);
				helper.assertTrue(onDiamond != null && onDiamond.tier() != RuneTier.MYTHIC, "never a mythic rune on a diamond chestplate");
				RuneConfig.RuneDef onLegs = RuneForge.roll(netheriteLegs, random);
				helper.assertTrue(onLegs != null && onLegs.tier() != RuneTier.MYTHIC, "never a mythic rune on netherite leggings");
				RuneConfig.RuneDef onChest = RuneForge.roll(netherite, random);
				if (onChest != null && onChest.tier() == RuneTier.MYTHIC) {
					mythics++;
				}
			}
			helper.assertTrue(mythics > 400, "the netherite chestplate draws the mythic runes: " + mythics);
			helper.assertTrue(!RuneForge.canReceive(UniqueDrops.create("plastron_du_serpent")), "no rune on a unique armor");
			helper.assertTrue(!RuneForge.canReceive(new ItemStack(Items.CARVED_PUMPKIN)), "no rune on a pumpkin");
			helper.assertTrue(!RuneForge.canReceive(new ItemStack(Items.IRON_SWORD)), "no rune on a weapon");
			ItemStack runed = new ItemStack(Items.IRON_BOOTS);
			RuneItems.inscribe(runed, runes.rune("celerite"));
			helper.assertTrue(!RuneForge.canReceive(runed), "one rune per piece");
		} finally {
			runes.tiers.common = common;
			runes.tiers.rare = rare;
			runes.tiers.mythic = mythic;
		}
		helper.succeed();
	}

	// ================================================================== common rune worn

	@GameTest(environment = "guildy-test:day", maxTicks = 100)
	public void commonRuneBonusAppliesWhenWorn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 4);
		TestSupport.forceChunks(level, ground, 1);
		ServerPlayer player = TestSupport.survivor(helper, ground, TestSupport.uniqueName("Rune"));
		RuneConfig runes = RuneModule.config();
		ItemStack helmet = new ItemStack(Items.IRON_HELMET);
		RuneItems.inscribe(helmet, runes.rune("rempart"));
		ItemStack boots = new ItemStack(Items.IRON_BOOTS);
		RuneItems.inscribe(boots, runes.rune("rempart"));
		ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
		RuneItems.inscribe(chest, runes.rune("vitalite"));
		double plainHelmet = armor(new ItemStack(Items.IRON_HELMET), EquipmentSlot.HEAD);
		double plainBoots = armor(new ItemStack(Items.IRON_BOOTS), EquipmentSlot.FEET);
		double plainChest = armor(new ItemStack(Items.IRON_CHESTPLATE), EquipmentSlot.CHEST);
		helper.assertTrue(armor(helmet, EquipmentSlot.HEAD) == plainHelmet + 1, "the helmet gives one more armor point");
		helper.assertTrue(armor(chest, EquipmentSlot.CHEST) == plainChest, "the chestplate keeps its own armor");
		player.doTick();
		double bare = player.getAttributeValue(Attributes.ARMOR);
		player.setItemSlot(EquipmentSlot.HEAD, helmet);
		player.setItemSlot(EquipmentSlot.FEET, boots);
		player.setItemSlot(EquipmentSlot.CHEST, chest);
		player.doTick();
		double expected = bare + plainHelmet + 1 + plainBoots + 1 + plainChest;
		AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
		helper.assertTrue(armor.getModifier(Identifier.fromNamespaceAndPath("guildy", "rune_rempart_head")) != null
				&& armor.getModifier(Identifier.fromNamespaceAndPath("guildy", "rune_rempart_feet")) != null,
				"the same rune on two pieces: two modifiers, one per slot");
		helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ARMOR) - expected) < 1e-6,
				"armor with the runes: " + player.getAttributeValue(Attributes.ARMOR) + " (expected " + expected + ")");
		helper.assertTrue(player.getMaxHealth() == 22.0F, "Rune de vitalité: +2 max health, got " + player.getMaxHealth());
		player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
		player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		player.doTick();
		helper.assertTrue(player.getAttributeValue(Attributes.ARMOR) == bare && player.getMaxHealth() == 20.0F, "nothing left once taken off");
		helper.succeed();
	}

	// ================================================================== grindstone

	@GameTest(environment = "guildy-test:day", maxTicks = 100)
	public void grindstoneRemovesOnlyTheRune(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 4);
		TestSupport.forceChunks(level, ground, 1);
		ServerPlayer player = TestSupport.survivor(helper, ground, TestSupport.uniqueName("Meule"));
		BlockPos grindstone = ground.offset(2, 1, 0);
		level.setBlockAndUpdate(grindstone, Blocks.GRINDSTONE.defaultBlockState());
		Holder<Enchantment> protection = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
		ItemStack piece = new ItemStack(Items.DIAMOND_CHESTPLATE);
		piece.enchant(protection, 4);
		piece.setDamageValue(37);
		piece.set(DataComponents.CUSTOM_NAME, Component.literal("Cuirasse"));
		ItemStack reference = piece.copy();
		RuneItems.inscribe(piece, RuneModule.config().rune("vitalite"));
		helper.assertTrue(RuneItems.has(piece) && hasRuneLore(piece, "Rune de vitalité"), "the piece carries the rune");

		GrindstoneMenu menu = new GrindstoneMenu(1, player.getInventory(), ContainerLevelAccess.create(level, grindstone));
		player.containerMenu = menu;
		menu.getSlot(GrindstoneMenu.INPUT_SLOT).set(piece);
		ItemStack result = menu.getSlot(GrindstoneMenu.RESULT_SLOT).getItem();
		helper.assertTrue(!result.isEmpty(), "the grindstone offers the piece without its rune");
		helper.assertTrue(!RuneItems.hasRuneTraces(result), "no rune data, modifier or lore left");
		helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(protection, result) == 4, "Protection IV kept");
		helper.assertTrue(result.getDamageValue() == 37 && "Cuirasse".equals(result.getHoverName().getString()), "durability and name kept");
		helper.assertTrue(ItemStack.isSameItemSameComponents(result, reference), "exactly the piece as it was before the rune");
		// Taking it: no experience (nothing was disenchanted), the rune fades.
		menu.clicked(GrindstoneMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
		helper.assertTrue(ItemStack.isSameItemSameComponents(menu.getCarried(), reference), "the player holds the piece");
		helper.assertTrue(menu.getSlot(GrindstoneMenu.INPUT_SLOT).getItem().isEmpty(), "the runed piece was used up");
		helper.assertTrue(level.getEntitiesOfClass(ExperienceOrb.class, new AABB(grindstone).inflate(3)).isEmpty(), "no experience for a rune");
		helper.assertTrue(TestSupport.chat(player).stream().anyMatch(line -> line.contains("s'efface sous la meule")), "the rune fades");
		// A second pass removes the enchantments, as in vanilla.
		ItemStack again = menu.getCarried().copy();
		menu.setCarried(ItemStack.EMPTY);
		menu.getSlot(GrindstoneMenu.INPUT_SLOT).set(again);
		ItemStack disenchanted = menu.getSlot(GrindstoneMenu.RESULT_SLOT).getItem();
		helper.assertTrue(!disenchanted.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(protection, disenchanted) == 0,
				"then the vanilla grindstone removes the enchantments");
		menu.getSlot(GrindstoneMenu.INPUT_SLOT).set(ItemStack.EMPTY);
		// Two runed pieces merged (vanilla repair): the result never keeps a rune.
		ItemStack first = new ItemStack(Items.IRON_BOOTS);
		first.setDamageValue(100);
		RuneItems.inscribe(first, RuneModule.config().rune("plume"));
		ItemStack second = new ItemStack(Items.IRON_BOOTS);
		second.setDamageValue(100);
		RuneItems.inscribe(second, RuneModule.config().rune("celerite"));
		menu.getSlot(GrindstoneMenu.INPUT_SLOT).set(first);
		menu.getSlot(GrindstoneMenu.ADDITIONAL_SLOT).set(second);
		ItemStack merged = menu.getSlot(GrindstoneMenu.RESULT_SLOT).getItem();
		helper.assertTrue(merged.is(Items.IRON_BOOTS) && !RuneItems.hasRuneTraces(merged), "a merged piece has no rune");
		player.containerMenu = player.inventoryMenu;
		helper.succeed();
	}

	// ================================================================== powers

	@GameTest(environment = "guildy-test:day", maxTicks = 120)
	public void rareRunesSparkBurnAndReveal(GameTestHelper helper) {
		Arena arena = arena(helper, "Etin");
		BlockPos c = arena.ground.offset(2, 0, 2);
		place(arena.wearer, c);
		Zombie zombie = zombie(arena.level, c.south(1));
		Zombie far = zombie(arena.level, c.north(6));
		RuneConfig runes = RuneModule.config();
		double emberChance = runes.powers.ember.chance;
		runes.powers.ember.chance = 1.0;
		ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
		RuneItems.inscribe(chest, runes.rune("etincelles"));
		ItemStack helmet = new ItemStack(Items.IRON_HELMET);
		RuneItems.inscribe(helmet, runes.rune("lueur"));
		arena.wearer.setItemSlot(EquipmentSlot.CHEST, chest);
		arena.wearer.setItemSlot(EquipmentSlot.HEAD, helmet);
		float before = zombie.getHealth();
		arena.wearer.hurtServer(arena.level, arena.level.damageSources().mobAttack(zombie), 2.0F);
		helper.assertTrue(zombie.getHealth() < before, "Rune d'étincelles: sparks on the attacker (" + zombie.getHealth() + ")");
		helper.assertTrue(zombie.hasEffect(MobEffects.GLOWING) && far.hasEffect(MobEffects.GLOWING), "Rune de lueur: the monsters around glow");
		helper.assertTrue(!arena.villager.hasEffect(MobEffects.GLOWING) && !arena.bystander.hasEffect(MobEffects.GLOWING),
				"never a villager or a player");
		// Braise (chance forced to 1): another chestplate.
		ItemStack ember = new ItemStack(Items.IRON_CHESTPLATE);
		RuneItems.inscribe(ember, runes.rune("braise"));
		arena.wearer.setItemSlot(EquipmentSlot.CHEST, ember);
		arena.wearer.damageCooldownTime = 0;
		arena.wearer.hurtServer(arena.level, arena.level.damageSources().mobAttack(zombie), 1.0F);
		helper.assertTrue(zombie.getRemainingFireTicks() > 0, "Rune de braise: the attacker burns");
		runes.powers.ember.chance = emberChance;
		helper.runAfterDelay(2, () -> {
			arena.assertSpared(helper);
			zombie.discard();
			far.discard();
			arena.cleanUp();
			helper.succeed();
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 120)
	public void phoenixRuneRaisesTheWearer(GameTestHelper helper) {
		Arena arena = arena(helper, "Phenix");
		BlockPos c = arena.ground.offset(2, 0, 2);
		place(arena.wearer, c);
		Zombie zombie = zombie(arena.level, c.south(2));
		ItemStack chest = new ItemStack(Items.NETHERITE_CHESTPLATE);
		RuneItems.inscribe(chest, RuneModule.config().rune("phenix"));
		arena.wearer.setItemSlot(EquipmentSlot.CHEST, chest);
		arena.wearer.setHealth(2.0F);
		long now = arena.level.getServer().overworld().getGameTime();
		arena.wearer.hurtServer(arena.level, arena.level.damageSources().mobAttack(zombie), 60.0F);
		helper.assertTrue(arena.wearer.isAlive() && arena.wearer.getHealth() >= 9.0F, "risen from the ashes: " + arena.wearer.getHealth());
		helper.assertTrue(arena.wearer.hasEffect(MobEffects.FIRE_RESISTANCE) && arena.wearer.hasEffect(MobEffects.REGENERATION),
				"fire resistance and regeneration");
		ItemStack worn = arena.wearer.getItemBySlot(EquipmentSlot.CHEST);
		helper.assertTrue(RuneItems.readyAt(worn) >= now + 20 * 1200L, "the rune recharges for 20 minutes (on the chestplate)");
		// On cooldown, or for /kill, death is not prevented any more.
		helper.assertTrue(ServerLivingEntityEvents.ALLOW_DEATH.invoker().allowDeath(arena.wearer, arena.level.damageSources().mobAttack(zombie), 60),
				"no second rebirth while the rune recharges");
		float zombieHealth = zombie.getHealth();
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(zombie.getHealth() < zombieHealth, "the nova of flames burns the zombie");
			helper.assertTrue(zombie.getRemainingFireTicks() > 0, "and sets it on fire");
			helper.assertTrue(zombie.getDeltaMovement().z > 0.2, "and throws it back: " + zombie.getDeltaMovement());
			arena.assertSpared(helper);
			zombie.discard();
			arena.cleanUp();
			helper.succeed();
		});
	}

	@GameTest(environment = "guildy-test:day", maxTicks = 160)
	public void dragonRuneGlidesAndBreathesFire(GameTestHelper helper) {
		Arena arena = arena(helper, "Dragon");
		BlockPos c = arena.ground.offset(2, 0, 2);
		place(arena.wearer, c);
		Zombie zombie = zombie(arena.level, c.south(3));
		ItemStack chest = new ItemStack(Items.NETHERITE_CHESTPLATE);
		RuneItems.inscribe(chest, RuneModule.config().rune("dragon"));
		helper.assertTrue(chest.has(DataComponents.GLIDER) && LivingEntity.canGlideUsing(chest, EquipmentSlot.CHEST), "it glides like an elytra");
		ItemStack ground = RuneItems.strip(chest.copy());
		helper.assertTrue(!ground.has(DataComponents.GLIDER) && !RuneItems.hasRuneTraces(ground), "without the rune it no longer glides");
		arena.wearer.setItemSlot(EquipmentSlot.CHEST, chest);
		float before = zombie.getHealth();
		// Sneak + jump: the breath (once the worn runes are read, at most 10 ticks).
		arena.wearer.setLastClientInput(new Input(false, false, false, false, true, true, false));
		helper.succeedWhen(() -> {
			helper.assertTrue(zombie.getHealth() < before, "the dragon's breath burns the zombie in front");
			helper.assertTrue(zombie.getRemainingFireTicks() > 0, "and sets it on fire");
			arena.assertSpared(helper);
			arena.wearer.setLastClientInput(Input.EMPTY);
			zombie.discard();
			arena.cleanUp();
		});
	}

	// ================================================================== helpers

	/** A ground, the wearer (survival, can be hit), a bystander player and a villager standing still. */
	private record Arena(ServerLevel level, BlockPos ground, ServerPlayer wearer, ServerPlayer bystander, Villager villager, float villagerHealth) {
		void assertSpared(GameTestHelper helper) {
			helper.assertTrue(bystander.getHealth() >= bystander.getMaxHealth(), "the bystander player is never hurt: " + bystander.getHealth());
			helper.assertTrue(villager.isAlive() && villager.getHealth() >= villagerHealth, "the villager is never hurt");
			helper.assertTrue(bystander.getRemainingFireTicks() <= 0 && villager.getRemainingFireTicks() <= 0, "nobody else set on fire");
		}

		void cleanUp() {
			villager.discard();
		}
	}

	private static Arena arena(GameTestHelper helper, String prefix) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 10);
		TestSupport.forceChunks(level, ground, 1);
		ServerPlayer wearer = hittable(helper, TestSupport.survivor(helper, ground, TestSupport.uniqueName(prefix + "W")));
		ServerPlayer bystander = hittable(helper, TestSupport.survivor(helper, ground.offset(-4, 0, 0), TestSupport.uniqueName(prefix + "B")));
		Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
		villager.snapTo(ground.getX() + 4.5, ground.getY() + 1, ground.getZ() + 3.5, 0.0F, 0.0F);
		villager.setNoAi(true);
		level.addFreshEntity(villager);
		return new Arena(level, ground, wearer, bystander, villager, villager.getHealth());
	}

	/** A mock player whose client "loaded": it can take damage. */
	private static ServerPlayer hittable(GameTestHelper helper, ServerPlayer player) {
		for (int i = 0; i < 200 && !player.connection.hasClientLoaded(); i++) {
			player.connection.tickClientLoadTimeout();
		}
		helper.assertTrue(player.connection.hasClientLoaded(), "mock player can be hit");
		return player;
	}

	/** Standing on {@code ground}, looking south (yaw 0). */
	private static void place(ServerPlayer player, BlockPos ground) {
		player.teleportTo(player.level(), ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
		player.setYHeadRot(0.0F);
		player.setOnGround(true);
	}

	/** A zombie without AI (a helmet: no sunburn) with a lot of health. */
	private static Zombie zombie(ServerLevel level, BlockPos ground) {
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		zombie.snapTo(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, 0.0F, 0.0F);
		zombie.setNoAi(true);
		zombie.setPersistenceRequired();
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		zombie.setHealth(200);
		level.addFreshEntity(zombie);
		return zombie;
	}

	private static double armor(ItemStack stack, EquipmentSlot slot) {
		return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).compute(Attributes.ARMOR, 0, slot);
	}

	private static boolean hasRuneLore(ItemStack stack, String name) {
		for (Component line : stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) {
			if (RuneItems.MARK.equals(line.getStyle().getInsertion()) && line.getString().contains(name)) {
				return true;
			}
		}
		return false;
	}

	private static int count(Container container, Item item) {
		int total = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (stack.is(item)) {
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
}
