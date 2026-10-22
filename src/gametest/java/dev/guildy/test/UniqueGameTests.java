package dev.guildy.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.guildy.core.GuildyItems;
import dev.guildy.core.UniqueDrops;
import dev.guildy.trade.TradeConfig;
import dev.guildy.trade.TradeEntry;
import dev.guildy.trade.TradeModule;
import dev.guildy.trade.TradeOffers;
import dev.guildy.unique.UniqueConfig;
import dev.guildy.unique.UniqueItems;
import dev.guildy.unique.UniqueModule;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Unique weapons: the data-driven guildy enchantments are loaded (vanilla effect types only, out of the enchanting table,
 * trades and random loot), the unique items carry their components, the drop hook works, and the Java powers work
 * through the real game paths (right click, block break, mace smash attack).
 */
public class UniqueGameTests {
	private static final Map<String, String> ENCHANTMENTS = Map.of("foudroiement", "Foudroiement", "vampirisme", "Vampirisme",
			"givre", "Givre", "brasier", "Brasier", "echo", "Écho", "seisme", "Séisme", "apesanteur", "Apesanteur", "salve", "Salve",
			"moisson", "Moisson", "marque", "Marque du chasseur");
	private static final List<TagKey<Enchantment>> VANILLA_SOURCES = List.of(EnchantmentTags.IN_ENCHANTING_TABLE, EnchantmentTags.TRADEABLE,
			EnchantmentTags.ON_RANDOM_LOOT, EnchantmentTags.ON_MOB_SPAWN_EQUIPMENT, EnchantmentTags.ON_TRADED_EQUIPMENT,
			EnchantmentTags.TREASURE, EnchantmentTags.NON_TREASURE, EnchantmentTags.DOUBLE_TRADE_PRICE);
	/** nextDouble() == 0: every chance passes. */
	private static final RandomGenerator LUCKY = () -> 0L;

	@GameTest(maxTicks = 40)
	public void guildyEnchantmentsAreDataDriven(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Registry<Enchantment> registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
		for (Map.Entry<String, String> entry : ENCHANTMENTS.entrySet()) {
			ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath("guildy", entry.getKey()));
			Holder.Reference<Enchantment> holder = registry.get(key).orElse(null);
			helper.assertTrue(holder != null, "enchantment guildy:" + entry.getKey() + " loaded");
			Enchantment enchantment = holder.value();
			helper.assertTrue(!(enchantment.description().getContents() instanceof TranslatableContents),
					entry.getKey() + ": literal description (no client lang file needed)");
			helper.assertTrue(entry.getValue().equals(enchantment.description().getString()), "French name of " + entry.getKey());
			helper.assertTrue(enchantment.getMaxLevel() >= 1 && enchantment.getMaxLevel() <= 3, entry.getKey() + ": reasonable max level");
			for (TagKey<Enchantment> tag : VANILLA_SOURCES) {
				helper.assertFalse(holder.is(tag), entry.getKey() + " must stay out of " + tag.location());
			}
			// What a vanilla client receives (synchronized registry): only vanilla effect / condition / provider types.
			JsonElement json = Enchantment.DIRECT_CODEC.encodeStart(ops, enchantment).getOrThrow();
			List<String> custom = new ArrayList<>();
			collectTypes(json, custom);
			JsonObject effects = json.getAsJsonObject().getAsJsonObject("effects");
			if (effects != null) {
				for (String component : effects.keySet()) {
					if (!component.startsWith("minecraft:")) {
						custom.add(component);
					}
				}
			}
			helper.assertTrue(custom.isEmpty(), entry.getKey() + " uses non-vanilla types: " + custom);
		}
		for (String type : List.of("foudre", "brasier", "givre")) {
			helper.assertTrue(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
					.get(Identifier.fromNamespaceAndPath("guildy", type)).isPresent(), "damage type guildy:" + type);
		}
		Holder<Enchantment> foudre = registry.getOrThrow(key("foudroiement"));
		Holder<Enchantment> givre = registry.getOrThrow(key("givre"));
		helper.assertFalse(Enchantment.areCompatible(foudre, givre), "elemental enchantments are exclusive");
		helper.succeed();
	}

	private static ResourceKey<Enchantment> key(String path) {
		return ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath("guildy", path));
	}

	/** Every "type" value of the encoded tree that is not in the minecraft namespace. */
	private static void collectTypes(JsonElement element, List<String> custom) {
		if (element.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
				if (entry.getKey().equals("type") && entry.getValue().isJsonPrimitive()) {
					String type = entry.getValue().getAsString();
					// Position sources of spawn_particles use plain enum names ("in_bounding_box").
					if (type.contains(":") && !type.startsWith("minecraft:")) {
						custom.add(type);
					}
				}
				collectTypes(entry.getValue(), custom);
			}
		} else if (element.isJsonArray()) {
			for (JsonElement child : (JsonArray) element) {
				collectTypes(child, custom);
			}
		}
	}

	@GameTest(maxTicks = 120)
	public void uniqueItemsCarryTheirComponents(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack sword = UniqueDrops.create("foudrelame");
		helper.assertTrue(sword.is(Items.NETHERITE_SWORD), "Foudrelame is a netherite sword, got " + sword);
		helper.assertTrue("foudrelame".equals(UniqueItems.idOf(sword)), "custom_data guildy.kind=unique, id=foudrelame");
		helper.assertTrue(GuildyItems.is(sword, UniqueItems.KIND), "Guildy special item (never sold to the shop)");
		helper.assertTrue(sword.get(DataComponents.ITEM_NAME).getString().contains("Foudrelame"), "item name");
		helper.assertTrue(sword.get(DataComponents.LORE).lines().size() >= 6, "rarity, story and power in the lore");
		Registry<Enchantment> enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(key("foudroiement")), sword) == 3, "Foudroiement III");
		helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(
				ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("sharpness"))), sword) == 6, "Sharpness VI (beyond vanilla)");
		helper.assertTrue(sword.has(DataComponents.UNBREAKABLE), "unbreakable");
		helper.assertTrue(Boolean.TRUE.equals(sword.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), "glint");
		helper.assertTrue(sword.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
				.anyMatch(e -> e.modifier().id().getNamespace().equals("guildy")), "extra attribute modifier");
		UseCooldown cooldown = sword.get(DataComponents.USE_COOLDOWN);
		helper.assertTrue(cooldown != null && cooldown.cooldownGroup().map(UniqueItems.cooldownGroup("foudrelame")::equals).orElse(false),
				"own cooldown group for the right-click power");
		helper.assertTrue(Identifier.withDefaultNamespace("netherite_sword").equals(sword.get(DataComponents.ITEM_MODEL)),
				"no pack guaranteed (no Polymer, customModels auto): vanilla model");

		// Every default unique is created with all its enchantments (every id resolves in the registries).
		UniqueConfig cfg = UniqueModule.config();
		for (UniqueConfig.UniqueDef def : cfg.usable()) {
			ItemStack stack = UniqueDrops.create(def.id);
			helper.assertFalse(stack.isEmpty(), "unique " + def.id + " created");
			helper.assertTrue(stack.getEnchantments().size() == def.enchantments.size(), def.id + ": every enchantment applied");
		}
		ItemStack boots = UniqueDrops.create("bottes_du_coursier");
		helper.assertTrue(boots.has(DataComponents.DYED_COLOR), "dyed leather");

		// Drop hook: a lucky boss roll gives a unique marked for the server announcement, a trade roll does not.
		ItemStack boss = UniqueDrops.roll("boss", LUCKY);
		helper.assertTrue(UniqueItems.isUnique(boss), "lucky boss roll gives a unique");
		helper.assertTrue("boss".equals(GuildyItems.data(boss).getStringOr("announce", "")), "boss drop marked for the announcement");
		ItemStack trade = UniqueDrops.roll("trade", LUCKY);
		helper.assertTrue(UniqueItems.isUnique(trade) && GuildyItems.data(trade).getStringOr("announce", "").isEmpty(), "trade roll");
		helper.assertTrue(UniqueDrops.roll("no_such_source", LUCKY).isEmpty(), "unknown source: nothing");
		helper.assertTrue(UniqueDrops.create("no_such_unique").isEmpty(), "unknown id: nothing");

		// Trade hub: unique weapons are never sold by default; an offer added by an admin still works (gold blocks).
		TradeConfig trades = TradeModule.config();
		helper.assertTrue(trades.tiers.stream().flatMap(t -> t.usable().stream()).noneMatch(TradeEntry::isUnique), "no unique offer by default");
		TradeEntry entry = TradeEntry.unique(TradeEntry.RANDOM_UNIQUE, 360, 3);
		helper.assertTrue(dev.guildy.trade.TradeValidator.check(entry, id -> true, java.util.Map.of(), java.util.Map.of(), new java.util.ArrayList<>()) == null,
				"an admin's unique offer is valid");
		MerchantOffer offer = TradeOffers.offer(level.registryAccess(), entry);
		helper.assertTrue(offer != null && UniqueItems.isUnique(offer.getResult()), "the offer sells a unique weapon");
		helper.assertTrue(offer.getCostA().is(Items.GOLD_BLOCK), "paid in gold blocks");

		// Admin command, then the announcement scan removes the marker of a dropped unique once a player has it.
		BlockPos ground = TestSupport.region(level, 2);
		ServerPlayer player = TestSupport.survivor(helper, ground);
		// Mock players share their name: target this one through its UUID (@s of an execute as).
		TestSupport.console(level, "execute as " + player.getStringUUID() + " run guild admin unique give @s faux_des_ames");
		helper.assertTrue(countUniques(player, "faux_des_ames") == 1, "admin give");
		TestSupport.console(level, "guild admin unique list");
		player.getInventory().add(boss);
		helper.succeedWhen(() -> {
			boolean pending = false;
			Inventory inventory = player.getInventory();
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (UniqueItems.isUnique(stack) && !GuildyItems.data(stack).getStringOr("announce", "").isEmpty()) {
					pending = true;
				}
			}
			helper.assertFalse(pending, "waiting for the announcement scan");
		});
	}

	private static int countUniques(ServerPlayer player, String id) {
		int count = 0;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (id.equals(UniqueItems.idOf(inventory.getItem(slot)))) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Worn look of the unique armors: with the resource pack guaranteed (customModels forced on), a new unique armor is
	 * drawn with its own equipment asset (same slot, sounds and other fields as its base item); without it the vanilla
	 * equippable is untouched; copies in circulation follow the setting (the inventory scan calls syncVisuals).
	 */
	@GameTest(maxTicks = 40)
	public void uniqueArmorsWornLookFollowsThePack(GameTestHelper helper) {
		UniqueConfig cfg = UniqueModule.config();
		String previous = cfg.customModels;
		// Switched and restored within this call: no other test runs in between.
		try {
			cfg.customModels = "off";
			ItemStack plain = UniqueDrops.create("couronne_du_roi_de_givre");
			Equippable vanilla = new ItemStack(Items.DIAMOND_HELMET).get(DataComponents.EQUIPPABLE);
			helper.assertTrue(vanilla != null && vanilla.equals(plain.get(DataComponents.EQUIPPABLE)), "no pack: the vanilla equippable");
			helper.assertFalse(plain.getComponentsPatch().split().added().has(DataComponents.EQUIPPABLE), "no pack: equippable untouched");
			helper.assertTrue(UniqueDrops.create("plastron_du_serpent").get(DataComponents.EQUIPPABLE)
					.equals(new ItemStack(Items.LEATHER_CHESTPLATE).get(DataComponents.EQUIPPABLE)), "no pack: vanilla leather look");

			cfg.customModels = "on";
			Map<String, Item> armors = Map.of("couronne_du_roi_de_givre", Items.DIAMOND_HELMET, "plastron_du_serpent", Items.LEATHER_CHESTPLATE,
					"bottes_de_zephyr", Items.NETHERITE_BOOTS, "bottes_du_coursier", Items.LEATHER_BOOTS);
			for (Map.Entry<String, Item> entry : armors.entrySet()) {
				String id = entry.getKey();
				ItemStack stack = UniqueDrops.create(id);
				helper.assertTrue(stack.is(entry.getValue()), id + ": base item " + entry.getValue());
				Equippable base = new ItemStack(entry.getValue()).get(DataComponents.EQUIPPABLE);
				Equippable worn = stack.get(DataComponents.EQUIPPABLE);
				ResourceKey<EquipmentAsset> asset = UniqueItems.equipmentAsset(id);
				helper.assertTrue(worn != null && worn.assetId().equals(Optional.of(asset)), id + ": asset_id guildy:" + id + ", got " + worn);
				helper.assertTrue(asset.identifier().equals(Identifier.fromNamespaceAndPath("guildy", id)), id + ": guildy namespace");
				helper.assertTrue(worn.slot() == base.slot(), id + ": same slot as " + entry.getValue());
				helper.assertTrue(worn.equals(UniqueItems.withAsset(base, asset)), id + ": sounds and other fields of the base item");
				helper.assertTrue(("guildy:" + id).equals(String.valueOf(stack.get(DataComponents.ITEM_MODEL))), id + ": its item model too");
			}
			helper.assertFalse(UniqueDrops.create("foudrelame").has(DataComponents.EQUIPPABLE), "a weapon gets no equippable");
			ItemStack aegis = UniqueDrops.create("egide_des_ames");
			helper.assertTrue(aegis.get(DataComponents.EQUIPPABLE).equals(new ItemStack(Items.SHIELD).get(DataComponents.EQUIPPABLE)),
					"the shield (offhand) keeps its equippable");

			// A copy made without the pack is refreshed by the inventory scan, then reverted when the pack is no longer sure.
			UniqueConfig.UniqueDef crown = cfg.item("couronne_du_roi_de_givre");
			helper.assertTrue(UniqueItems.syncVisuals(plain, crown), "refreshed once the pack is guaranteed");
			helper.assertTrue(plain.get(DataComponents.EQUIPPABLE).assetId().equals(Optional.of(UniqueItems.equipmentAsset("couronne_du_roi_de_givre"))),
					"the copy in circulation is drawn with the crown");
			helper.assertFalse(UniqueItems.syncVisuals(plain, crown), "stable once refreshed");
			cfg.customModels = "off";
			helper.assertTrue(UniqueItems.syncVisuals(plain, crown), "reverted without the pack");
			helper.assertTrue(vanilla.equals(plain.get(DataComponents.EQUIPPABLE))
					&& !plain.getComponentsPatch().split().added().has(DataComponents.EQUIPPABLE), "back to the base item's own equippable");
			helper.assertTrue(Identifier.withDefaultNamespace("diamond_helmet").equals(plain.get(DataComponents.ITEM_MODEL)), "and its model");
		} finally {
			cfg.customModels = previous;
		}
		helper.succeed();
	}

	/** Foudrelame, right click on an aimed monster: a visual-only bolt strikes it (damage, no fire), cooldown starts. */
	@GameTest(maxTicks = 100)
	public void stormCallStrikesTheAimedMonster(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 10);
		TestSupport.forceChunks(level, ground, 1);
		ServerPlayer player = TestSupport.survivor(helper, ground);
		player.teleportTo(level, ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, Set.of(), -90.0F, 0.0F, true);
		Husk husk = monster(level, ground.east(6));
		ItemStack sword = UniqueDrops.create("foudrelame");
		player.setItemInHand(InteractionHand.MAIN_HAND, sword);
		float before = husk.getHealth();

		InteractionResult result = UseItemCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND);
		helper.assertTrue(result instanceof InteractionResult.Success, "power fired, got " + result);
		helper.assertTrue(husk.getHealth() < before, "the husk was struck: " + husk.getHealth() + " / " + before);
		helper.assertTrue(player.getCooldowns().isOnCooldown(sword), "cooldown started");
		List<LightningBolt> bolts = level.getEntitiesOfClass(LightningBolt.class, new AABB(husk.blockPosition()).inflate(3));
		helper.assertTrue(!bolts.isEmpty(), "a lightning bolt struck the husk");
		helper.assertTrue(husk.getRemainingFireTicks() <= 0, "visual bolt: the husk is not set on fire");
		helper.assertTrue(UseItemCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND) == InteractionResult.PASS,
				"on cooldown: nothing happens");
		helper.runAfterDelay(10, () -> {
			BlockPos top = husk.blockPosition();
			helper.assertTrue(!level.getBlockState(top).is(Blocks.FIRE) && !level.getBlockState(top.below()).is(Blocks.FIRE), "no fire placed");
			husk.discard();
			helper.succeed();
		});
	}

	/** Hache de l'Ancien Forestier: breaking the bottom log of a natural tree fells it; a log pillar is left alone. */
	@GameTest(maxTicks = 100)
	public void treeFellerFellsNaturalTreesOnly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 8);
		TestSupport.forceChunks(level, ground, 1);
		ServerPlayer player = TestSupport.survivor(helper, ground.west(2));
		player.setItemInHand(InteractionHand.MAIN_HAND, UniqueDrops.create("hache_sylvestre"));
		BlockState log = Blocks.OAK_LOG.defaultBlockState();
		BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
		BlockPos tree = ground.east(2);
		for (int y = 1; y <= 5; y++) {
			level.setBlockAndUpdate(tree.above(y), log);
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) {
					level.setBlockAndUpdate(tree.offset(dx, 5, dz), leaves);
				}
			}
		}
		BlockPos pillar = ground.west(5);
		for (int y = 1; y <= 4; y++) {
			level.setBlockAndUpdate(pillar.above(y), log);
		}

		helper.assertTrue(player.gameMode.destroyBlock(tree.above()), "bottom log broken");
		helper.assertTrue(player.gameMode.destroyBlock(pillar.above()), "pillar bottom broken");
		helper.succeedWhen(() -> {
			for (int y = 1; y <= 5; y++) {
				helper.assertTrue(level.getBlockState(tree.above(y)).isAir(), "waiting for the tree to fall (log " + y + ")");
			}
			for (int y = 2; y <= 4; y++) {
				helper.assertTrue(level.getBlockState(pillar.above(y)).is(Blocks.OAK_LOG), "a build of logs (no leaves) is not a tree");
			}
			helper.assertTrue(UniqueItems.isUnique(player.getMainHandItem()), "the axe is still there");
		});
	}

	/** Marteau du Séisme: a real smash attack on one husk sends a shockwave into the other one. */
	@GameTest(maxTicks = 60)
	public void maceSmashSendsAShockwave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 8);
		TestSupport.forceChunks(level, ground, 1);
		ServerPlayer player = TestSupport.survivor(helper, ground);
		player.setItemInHand(InteractionHand.MAIN_HAND, UniqueDrops.create("marteau_du_seisme"));
		Husk target = monster(level, ground.east(2));
		Husk other = monster(level, ground.east(4));
		float before = other.getHealth();
		player.fallDistance = 3.0;
		player.attack(target);
		helper.assertTrue(other.getHealth() < before || other.isDeadOrDying(), "the shockwave hit the other husk: " + other.getHealth());
		target.discard();
		other.discard();
		helper.succeed();
	}

	private static Husk monster(ServerLevel level, BlockPos ground) {
		Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
		husk.snapTo(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, 90.0F, 0.0F);
		husk.setNoAi(true);
		husk.setPersistenceRequired();
		level.addFreshEntity(husk);
		return husk;
	}
}
