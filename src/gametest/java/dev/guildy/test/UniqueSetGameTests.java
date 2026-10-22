package dev.guildy.test;

import dev.guildy.core.GuildyItems;
import dev.guildy.core.UniqueDrops;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.unique.UniqueConfig;
import dev.guildy.unique.UniqueItems;
import dev.guildy.unique.UniqueModule;
import dev.guildy.unique.UniqueRegistry;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.Equippable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * The armor sets (uniques.json5 format 5): bonuses following the pieces worn (two pieces, four pieces, a piece taken off),
 * the lore ticking the worn pieces, each four-piece power through the real game paths on a zombie next to a survival
 * player that is never touched, a set piece from its guardian's table (never one the guild holds, a scroll when it holds
 * them all) and the worn look of every piece with the resource pack.
 */
public class UniqueSetGameTests {
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final List<String> FROST = List.of("heaume_hiver_eternel", "cuirasse_hiver_eternel", "jambieres_hiver_eternel",
			"solerets_hiver_eternel");
	private static final List<String> JAGUAR = List.of("masque_guerrier_jaguar", "pectoral_guerrier_jaguar", "jambieres_guerrier_jaguar",
			"sandales_guerrier_jaguar");
	private static final List<String> SOUL = List.of("heaume_brasier_des_ames", "cuirasse_brasier_des_ames", "greves_brasier_des_ames",
			"bottes_brasier_des_ames");

	/** A mock player whose client "loaded": it can take damage. */
	private static ServerPlayer hittable(GameTestHelper helper, ServerPlayer player) {
		for (int i = 0; i < 200 && !player.connection.hasClientLoaded(); i++) {
			player.connection.tickClientLoadTimeout();
		}
		helper.assertTrue(player.connection.hasClientLoaded(), "mock player can be hit");
		return player;
	}

	private static void place(ServerPlayer player, BlockPos ground) {
		player.teleportTo(player.level(), ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
		player.setOnGround(true);
	}

	/** A zombie without AI (a chainmail helmet: no sunburn), with a lot of health. */
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

	/** Puts the pieces on, head to feet (null or a missing piece = an empty slot). */
	private static void wear(ServerPlayer player, List<String> pieces) {
		for (int i = 0; i < SLOTS.length; i++) {
			String id = i < pieces.size() ? pieces.get(i) : null;
			player.setItemSlot(SLOTS[i], id == null ? ItemStack.EMPTY : UniqueDrops.create(id));
		}
	}

	private static boolean hasModifier(ServerPlayer player, Holder<Attribute> attribute, String path) {
		AttributeInstance instance = player.getAttribute(attribute);
		return instance != null && instance.hasModifier(Identifier.fromNamespaceAndPath("guildy", path));
	}

	private static double modifier(ServerPlayer player, Holder<Attribute> attribute, String path) {
		AttributeInstance instance = player.getAttribute(attribute);
		AttributeModifier modifier = instance == null ? null : instance.getModifier(Identifier.fromNamespaceAndPath("guildy", path));
		return modifier == null ? Double.NaN : modifier.amount();
	}

	private static String lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		StringBuilder text = new StringBuilder();
		if (lore != null) {
			lore.lines().forEach(line -> text.append(ChatFormatting.stripFormatting(line.getString())).append('\n'));
		}
		return text.toString();
	}

	/** The bystander was never hurt, slowed, frozen, poisoned or set on fire. */
	private static void assertSpared(GameTestHelper helper, ServerPlayer bystander) {
		helper.assertTrue(bystander.getHealth() >= bystander.getMaxHealth(), "the bystander player is never hurt: " + bystander.getHealth());
		helper.assertFalse(bystander.hasEffect(MobEffects.SLOWNESS) || bystander.hasEffect(MobEffects.POISON), "no effect on the bystander");
		helper.assertTrue(bystander.getRemainingFireTicks() <= 0 && bystander.getTicksFrozen() == 0, "the bystander neither burns nor freezes");
	}

	/** nextDouble() returns these values in turn (then the last one). */
	private static RandomGenerator sequence(double... values) {
		int[] next = {0};
		return () -> ((long) (values[Math.min(next[0]++, values.length - 1)] * (1L << 53))) << 11;
	}

	// ================================================================== bonuses

	/** Two pieces: the two-piece bonus; four: both; a piece off: the four-piece bonus goes, the other stays; all off: nothing. */
	@GameTest(environment = "guildy-test:day", maxTicks = 300)
	public void setBonusesFollowTheWornPieces(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 3);
		ServerPlayer player = TestSupport.survivor(helper, ground, TestSupport.uniqueName("Set"));
		wear(player, List.of("heaume_hiver_eternel", "cuirasse_hiver_eternel"));
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(hasModifier(player, Attributes.ARMOR_TOUGHNESS, "set_hiver_eternel_2_0"),
						"waiting for the two-piece bonus (+2 toughness)"))
				.thenExecute(() -> {
					helper.assertFalse(hasModifier(player, Attributes.MAX_HEALTH, "set_hiver_eternel_4_0"), "no four-piece bonus with two pieces");
					helper.assertTrue(lore(player.getItemBySlot(EquipmentSlot.HEAD)).contains("(2/4 portées)"), "the lore counts the pieces worn: "
							+ lore(player.getItemBySlot(EquipmentSlot.HEAD)));
					wear(player, FROST);
				})
				.thenWaitUntil(() -> helper.assertTrue(hasModifier(player, Attributes.MAX_HEALTH, "set_hiver_eternel_4_0"),
						"waiting for the four-piece bonus (+4 health)"))
				.thenExecute(() -> {
					helper.assertTrue(hasModifier(player, Attributes.ARMOR_TOUGHNESS, "set_hiver_eternel_2_0"), "the tiers add up");
					helper.assertTrue(player.getAttribute(Attributes.ARMOR_TOUGHNESS).getModifiers().stream()
							.filter(modifier -> modifier.id().getPath().startsWith("set_")).count() == 1, "never stacked twice");
					helper.assertTrue(modifier(player, Attributes.ARMOR_TOUGHNESS, "set_hiver_eternel_2_0") == 2.0, "+2 toughness");
					helper.assertTrue(modifier(player, Attributes.MAX_HEALTH, "set_hiver_eternel_4_0") == 4.0, "+4 health");
					String lore = lore(player.getItemBySlot(EquipmentSlot.FEET));
					helper.assertTrue(lore.contains("(4/4 portées)") && lore.contains("✔ Heaume de l'Hiver éternel"),
							"the worn pieces are ticked: " + lore);
					player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
				})
				.thenWaitUntil(() -> helper.assertFalse(hasModifier(player, Attributes.MAX_HEALTH, "set_hiver_eternel_4_0"),
						"waiting for the four-piece bonus to go with the boots"))
				.thenExecute(() -> {
					helper.assertTrue(hasModifier(player, Attributes.ARMOR_TOUGHNESS, "set_hiver_eternel_2_0"), "three pieces: the two-piece bonus stays");
					helper.assertTrue(player.getHealth() <= player.getMaxHealth(), "health never above the maximum");
					wear(player, List.of());
				})
				.thenWaitUntil(() -> helper.assertFalse(hasModifier(player, Attributes.ARMOR_TOUGHNESS, "set_hiver_eternel_2_0"),
						"waiting for every bonus to go"))
				.thenExecute(() -> {
					helper.assertTrue(player.getAttribute(Attributes.MAX_HEALTH).getModifiers().stream()
							.noneMatch(modifier -> modifier.id().getPath().startsWith("set_")), "no set modifier left on the health");
					// Pieces of two different sets: two pieces of neither, no bonus.
					wear(player, List.of("heaume_hiver_eternel", "pectoral_guerrier_jaguar"));
				})
				.thenIdle(25)
				.thenExecute(() -> {
					helper.assertFalse(hasModifier(player, Attributes.ARMOR_TOUGHNESS, "set_hiver_eternel_2_0")
							|| hasModifier(player, Attributes.MOVEMENT_SPEED, "set_guerrier_jaguar_2_0"), "one piece of each set: nothing");
					wear(player, List.of());
				})
				.thenSucceed();
	}

	// ================================================================== powers

	/** Hiver éternel: no freezing, no slowness; under 30 % health after a blow, the frost nova freezes the zombie. */
	@GameTest(environment = "guildy-test:day", maxTicks = 300)
	public void winterHeartFreezesTheZombie(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 8);
		// Entity-ticking chunks: the zombies are found by the powers (entities of a merely loaded chunk are hidden).
		TestSupport.forceChunks(level, ground, 1);
		ServerPlayer wearer = hittable(helper, TestSupport.survivor(helper, ground, TestSupport.uniqueName("Hiver")));
		ServerPlayer bystander = hittable(helper, TestSupport.survivor(helper, ground, TestSupport.uniqueName("HivB")));
		place(wearer, ground);
		place(bystander, ground.west(3));
		Zombie zombie = zombie(level, ground.south(2));
		wear(wearer, FROST);
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(hasModifier(wearer, Attributes.MAX_HEALTH, "set_hiver_eternel_4_0"), "waiting for the set"))
				.thenExecute(() -> {
					wearer.setTicksFrozen(100);
					wearer.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 2));
					helper.assertFalse(wearer.hurtServer(level, level.damageSources().freeze(), 2.0F), "Sang-froid: no freezing damage");
				})
				.thenIdle(2)
				.thenExecute(() -> {
					helper.assertTrue(wearer.getTicksFrozen() == 0, "the wearer never freezes");
					helper.assertFalse(wearer.hasEffect(MobEffects.SLOWNESS), "Cœur de l'hiver: slowness shaken off");
					// Above the threshold: nothing.
					wearer.hurtServer(level, level.damageSources().mobAttack(zombie), 1.0F);
					helper.assertTrue(zombie.getHealth() >= 200, "no nova above 30 % health");
					wearer.setHealth(6);
					wearer.damageCooldownTime = 0;
					float before = zombie.getHealth();
					boolean hit = wearer.hurtServer(level, level.damageSources().mobAttack(zombie), 1.0F);
					helper.assertTrue(zombie.getHealth() < before, "the nova's cold hurts the zombie: " + zombie.getHealth() + " (hit " + hit
							+ ", health " + wearer.getHealth() + " / " + wearer.getMaxHealth() + ", zombie at " + zombie.distanceTo(wearer) + ")");
					MobEffectInstance slowness = zombie.getEffect(MobEffects.SLOWNESS);
					helper.assertTrue(slowness != null && slowness.getAmplifier() == 3, "Slowness IV on the zombie");
					helper.assertTrue(zombie.getTicksFrozen() > 0, "the zombie is frozen");
					helper.assertTrue(wearer.hasEffect(MobEffects.RESISTANCE) && wearer.hasEffect(MobEffects.ABSORPTION),
							"the wearer is shielded by the ice");
					helper.assertTrue(wearer.getCooldowns().isOnCooldown(wearer.getItemBySlot(EquipmentSlot.CHEST)),
							"the recharge shows on the pieces");
					float after = zombie.getHealth();
					wearer.damageCooldownTime = 0;
					wearer.hurtServer(level, level.damageSources().mobAttack(zombie), 1.0F);
					helper.assertTrue(zombie.getHealth() >= after, "recharging: no second nova");
				})
				.thenIdle(3)
				.thenExecute(() -> {
					assertSpared(helper, bystander);
					zombie.discard();
					wear(wearer, List.of());
				})
				.thenSucceed();
	}

	/** Guerrier jaguar: crouched, the wearer is camouflaged; his next blow is an ambush that also claws the zombie beside. */
	@GameTest(environment = "guildy-test:day", maxTicks = 300)
	public void jaguarAmbushFromTheCamouflage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 8);
		// Entity-ticking chunks: the zombies are found by the powers (entities of a merely loaded chunk are hidden).
		TestSupport.forceChunks(level, ground, 1);
		ServerPlayer wearer = hittable(helper, TestSupport.survivor(helper, ground, TestSupport.uniqueName("Jag")));
		ServerPlayer bystander = hittable(helper, TestSupport.survivor(helper, ground, TestSupport.uniqueName("JagB")));
		place(wearer, ground);
		place(bystander, ground.west(4));
		Zombie prey = zombie(level, ground.south(2));
		Zombie neighbour = zombie(level, ground.south(2).east(2));
		Zombie far = zombie(level, ground.north(6));
		wear(wearer, JAGUAR);
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(hasModifier(wearer, Attributes.ATTACK_DAMAGE, "set_guerrier_jaguar_4_0")
						&& hasModifier(wearer, Attributes.MOVEMENT_SPEED, "set_guerrier_jaguar_2_0"), "waiting for the set"))
				.thenExecute(() -> wearer.setShiftKeyDown(true))
				.thenWaitUntil(() -> helper.assertTrue(wearer.hasEffect(MobEffects.INVISIBILITY), "waiting for the camouflage"))
				.thenExecute(() -> {
					prey.damageCooldownTime = 0;
					wearer.attack(prey);
					// The plain blow of a mock player is weak (no attack strength): the ambush adds 5 hearts.
					helper.assertTrue(prey.getHealth() <= 190, "the ambush strikes the prey: " + prey.getHealth());
					helper.assertTrue(prey.hasEffect(MobEffects.SLOWNESS), "the prey is slowed");
					helper.assertTrue(neighbour.getHealth() <= 195 && neighbour.hasEffect(MobEffects.SLOWNESS), "the claws rake the zombie beside: "
							+ neighbour.getHealth());
					helper.assertTrue(far.getHealth() >= 200, "the far zombie is out of reach");
					helper.assertFalse(wearer.hasEffect(MobEffects.INVISIBILITY), "the camouflage ends with the strike");
					helper.assertTrue(wearer.getCooldowns().isOnCooldown(wearer.getItemBySlot(EquipmentSlot.HEAD)), "the recharge shows on the pieces");
					float after = prey.getHealth();
					prey.damageCooldownTime = 0;
					wearer.attack(prey);
					helper.assertTrue(prey.getHealth() > after - 5, "recharging: a plain blow, no second ambush");
					wearer.setShiftKeyDown(false);
				})
				.thenIdle(3)
				.thenExecute(() -> {
					assertSpared(helper, bystander);
					prey.discard();
					neighbour.discard();
					far.discard();
					wear(wearer, List.of());
				})
				.thenSucceed();
	}

	/** Brasier des âmes: the aura drains the zombie's soul and heals the wearer. */
	@GameTest(environment = "guildy-test:day", maxTicks = 300)
	public void soulPyreDrainsTheZombie(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 8);
		// Entity-ticking chunks: the zombies are found by the powers (entities of a merely loaded chunk are hidden).
		TestSupport.forceChunks(level, ground, 1);
		ServerPlayer wearer = hittable(helper, TestSupport.survivor(helper, ground, TestSupport.uniqueName("Ame")));
		ServerPlayer bystander = hittable(helper, TestSupport.survivor(helper, ground, TestSupport.uniqueName("AmeB")));
		place(wearer, ground);
		place(bystander, ground.west(3));
		Zombie zombie = zombie(level, ground.south(2));
		Zombie far = zombie(level, ground.north(7));
		wearer.setHealth(10);
		wear(wearer, SOUL);
		helper.startSequence()
				.thenWaitUntil(() -> {
					helper.assertTrue(zombie.getHealth() <= 198, "waiting for the aura to drain the zombie: " + zombie.getHealth());
					helper.assertTrue(wearer.getHealth() > 10, "waiting for the souls to heal the wearer: " + wearer.getHealth());
				})
				.thenExecute(() -> {
					helper.assertTrue(far.getHealth() >= 200, "the far zombie is out of the aura");
					helper.assertTrue(hasModifier(wearer, Attributes.MAX_HEALTH, "set_brasier_des_ames_2_0"), "Flamme d'âme: +4 health");
					assertSpared(helper, bystander);
					zombie.discard();
					far.discard();
					wear(wearer, List.of());
				})
				.thenSucceed();
	}

	// ================================================================== drops and looks

	/**
	 * The guardian of the Bastion de givre favours the frost set: the end of its table is the set; a piece the guild holds
	 * never drops again; when the guild holds everything of the table, a spell scroll instead.
	 */
	@GameTest(environment = "guildy-test:day", maxTicks = 200)
	public void bastionGuardianDropsTheFrostSet(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 3);
		ServerPlayer steve = TestSupport.survivor(helper, ground, TestSupport.uniqueName("SetDrop"));
		Guild guild = GuildManager.create(TestSupport.uniqueName("UqSet"), steve);
		UniqueDrops.Recipient recipient = UniqueDrops.Recipient.of(steve, guild);
		UniqueConfig cfg = UniqueModule.config();
		UniqueConfig.DropTable table = cfg.effectiveTable("ruin_boss@bastion_de_givre");
		helper.assertTrue(table.items.keySet().containsAll(FROST), "the frost set in the guardian's table");
		helper.assertFalse(cfg.table("ruin_boss").items.containsKey("heaume_hiver_eternel"), "not in the plain ruin table");

		ItemStack first = UniqueDrops.recipientRoller.roll("ruin_boss@bastion_de_givre", sequence(0.0, 0.9999), recipient);
		helper.assertTrue("solerets_hiver_eternel".equals(UniqueItems.idOf(first)), "a piece of the frost set, got " + first);
		helper.assertTrue("ruin_boss".equals(GuildyItems.data(first).getStringOr("announce", "")), "announced under its plain source");
		helper.assertTrue("ruin_boss".equals(UniqueRegistry.ledger().copy(UniqueItems.serialOf(first)).source), "registered (serial)");
		ItemStack second = UniqueDrops.recipientRoller.roll("ruin_boss@bastion_de_givre", sequence(0.0, 0.9999), recipient);
		helper.assertTrue("jambieres_hiver_eternel".equals(UniqueItems.idOf(second)), "never the boots again: the next piece, got " + second);

		// Everything of the table held by the guild: a spell scroll instead.
		for (String id : table.items.keySet()) {
			if (!UniqueRegistry.ledger().obtainedBy(guild.id).contains(id)) {
				TestSupport.console(level, "execute as " + steve.getStringUUID() + " run guild admin unique give @s " + id);
			}
		}
		helper.assertTrue(UniqueRegistry.ledger().obtainedBy(guild.id).containsAll(table.items.keySet()), "the guild holds the whole table");
		ItemStack scroll = UniqueDrops.recipientRoller.roll("ruin_boss@bastion_de_givre", sequence(0.0, 0.5), recipient);
		helper.assertTrue(!scroll.isEmpty() && !UniqueDrops.isUnique(scroll), "all held: a spell scroll instead, got " + scroll);
		steve.getInventory().clearContent();
		GuildManager.disband(guild);
		helper.succeed();
	}

	/** Armor, toughness and knockback resistance a stack gives in a slot (additive modifiers of its component). */
	private static double total(ItemStack stack, Holder<Attribute> attribute, EquipmentSlot slot) {
		double sum = 0;
		for (ItemAttributeModifiers.Entry entry : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
			if (entry.attribute().equals(attribute) && entry.slot().test(slot) && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
				sum += entry.modifier().amount();
			}
		}
		return sum;
	}

	private static void assertNetherite(GameTestHelper helper, ItemStack stack, EquipmentSlot slot, double armor, String what) {
		double a = total(stack, Attributes.ARMOR, slot);
		double t = total(stack, Attributes.ARMOR_TOUGHNESS, slot);
		double k = total(stack, Attributes.KNOCKBACK_RESISTANCE, slot);
		helper.assertTrue(a >= armor - 1e-6 && t >= 3 - 1e-6 && k >= 0.1 - 1e-6, what + ": at least netherite (" + armor + " / 3 / 0.1), got "
				+ a + " / " + t + " / " + k);
	}

	/** The old copy of a unique armor, made before the floor: the floor modifiers taken off. */
	private static ItemStack withoutFloor(ItemStack stack) {
		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(modifiers.modifiers().stream()
				.filter(entry -> !entry.modifier().id().getPath().startsWith("unique_floor_")).toList()));
		return stack;
	}

	/**
	 * Protection floor: a new Bottes du Coursier (leather) protects like netherite boots, and so does an old copy made
	 * without the floor once the inventory scan refreshed it (worn, and carried in the inventory); the other unique armors
	 * too (a diamond crown gets its toughness), a netherite piece gets nothing.
	 */
	@GameTest(environment = "guildy-test:day", maxTicks = 200)
	public void uniqueArmorsProtectAtLeastLikeNetherite(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 2);
		ServerPlayer player = TestSupport.survivor(helper, ground, TestSupport.uniqueName("Floor"));
		ItemStack fresh = UniqueDrops.create("bottes_du_coursier");
		helper.assertTrue(fresh.is(Items.LEATHER_BOOTS), "leather boots");
		assertNetherite(helper, fresh, EquipmentSlot.FEET, 3, "new Bottes du Coursier");
		assertNetherite(helper, UniqueDrops.create("couronne_du_roi_de_givre"), EquipmentSlot.HEAD, 3, "Couronne du Roi de givre");
		assertNetherite(helper, UniqueDrops.create("plastron_du_serpent"), EquipmentSlot.CHEST, 8, "Plastron du Serpent à plumes");
		assertNetherite(helper, UniqueDrops.create("jambieres_guerrier_jaguar"), EquipmentSlot.LEGS, 6, "golden leggings of the jaguar warrior");
		ItemStack zephyr = UniqueDrops.create("bottes_de_zephyr");
		helper.assertTrue(zephyr.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
				.noneMatch(entry -> entry.modifier().id().getPath().startsWith("unique_floor_")), "netherite boots: no floor modifier");
		helper.assertTrue(fresh.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
				.filter(entry -> entry.modifier().id().getPath().startsWith("unique_floor_feet_")).count() == 3, "three floor modifiers for the feet");

		ItemStack worn = withoutFloor(UniqueDrops.create("bottes_du_coursier"));
		ItemStack carried = withoutFloor(UniqueDrops.create("couronne_du_roi_de_givre"));
		helper.assertTrue(total(worn, Attributes.ARMOR, EquipmentSlot.FEET) < 3, "an old copy: below the floor");
		player.setItemSlot(EquipmentSlot.FEET, worn);
		player.getInventory().add(carried);
		helper.startSequence()
				.thenWaitUntil(() -> {
					assertNetherite(helper, player.getItemBySlot(EquipmentSlot.FEET), EquipmentSlot.FEET, 3, "worn old copy, refreshed by the scan");
					ItemStack crown = ItemStack.EMPTY;
					for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
						if ("couronne_du_roi_de_givre".equals(UniqueItems.idOf(player.getInventory().getItem(i)))) {
							crown = player.getInventory().getItem(i);
						}
					}
					helper.assertFalse(crown.isEmpty(), "the crown is carried");
					assertNetherite(helper, crown, EquipmentSlot.HEAD, 3, "carried old copy, refreshed by the scan");
				})
				.thenExecute(() -> {
					ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
					helper.assertFalse(UniqueItems.syncArmorFloor(boots), "stable once refreshed");
					player.getInventory().clearContent();
					player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
				})
				.thenSucceed();
	}

	/** With the resource pack guaranteed, every set piece is drawn with its own equipment asset and item model. */
	@GameTest(maxTicks = 40)
	public void setPiecesWornLookWithThePack(GameTestHelper helper) {
		UniqueConfig cfg = UniqueModule.config();
		String previous = cfg.customModels;
		// Switched and restored within this call: no other test runs in between.
		try {
			cfg.customModels = "on";
			for (String id : UniqueConfig.SET_UNIQUES) {
				ItemStack stack = UniqueDrops.create(id);
				Equippable base = stack.getPrototype().get(DataComponents.EQUIPPABLE);
				Equippable worn = stack.get(DataComponents.EQUIPPABLE);
				helper.assertTrue(base != null && worn != null, id + ": an armor piece");
				helper.assertTrue(worn.assetId().equals(Optional.of(UniqueItems.equipmentAsset(id))), id + ": asset_id guildy:" + id + ", got " + worn);
				helper.assertTrue(worn.equals(UniqueItems.withAsset(base, UniqueItems.equipmentAsset(id))), id + ": slot and sounds of the base item");
				helper.assertTrue(("guildy:" + id).equals(String.valueOf(stack.get(DataComponents.ITEM_MODEL))), id + ": its item model");
				helper.assertTrue(stack.get(DataComponents.USE_COOLDOWN) != null && stack.get(DataComponents.USE_COOLDOWN).cooldownGroup()
						.equals(Optional.of(UniqueItems.setCooldownGroup(cfg.item(id).set))), id + ": the cooldown group of its set");
			}
			cfg.customModels = "off";
			ItemStack plain = UniqueDrops.create("greves_brasier_des_ames");
			helper.assertTrue(plain.get(DataComponents.EQUIPPABLE).equals(new ItemStack(Items.NETHERITE_LEGGINGS).get(DataComponents.EQUIPPABLE)),
					"no pack: the vanilla look of the base item");
		} finally {
			cfg.customModels = previous;
		}
		helper.succeed();
	}
}
