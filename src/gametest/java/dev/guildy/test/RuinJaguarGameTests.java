package dev.guildy.test;

import dev.guildy.ruin.CursedFights;
import dev.guildy.ruin.CursedItems;
import dev.guildy.ruin.Encounters;
import dev.guildy.ruin.IncantationItems;
import dev.guildy.ruin.RoomGuards;
import dev.guildy.ruin.RuinConfig;
import dev.guildy.ruin.RuinIndex;
import dev.guildy.ruin.RuinModule;
import dev.guildy.ruin.RuinRecord;
import dev.guildy.ruin.RuinState;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.CatVariants;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/**
 * The celestial jaguar and the themed dungeons, on the test ruin of {@link RuinGameTests}: the grand temple's guardian is a
 * hostile giant ocelot at its configured scale that hunts a survival player, with its jaguar attacks (claw sweep, roar,
 * pounce, stalk, spirit jaguars); its cursed form is a giant all-black cat that cannot be tamed; the Mayan temples post
 * their undead (bogged, parched, zombies, cave spiders) under jade or golden helmets, never a raider; the admin scroll
 * command gives the incantation scrolls.
 */
@SuppressWarnings("removal")
public class RuinJaguarGameTests {
	private static final Identifier STALK_SPEED = Identifier.fromNamespaceAndPath("guildy", "ruin_stalk_speed");
	private static final Set<EntityType<?>> UNDEAD = Set.of(EntityTypes.BOGGED, EntityTypes.PARCHED, EntityTypes.ZOMBIE, EntityTypes.CAVE_SPIDER);

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

	/** The grand temple's guardian (a copy): it never resets nor rises during the test, its bite does not kill the player. */
	private static RuinConfig.RuinDef jaguar(GameTestHelper helper) {
		RuinConfig.RuinDef template = RuinModule.config().ruins.get("grand_temple_maya");
		helper.assertTrue(template != null, "grand_temple_maya in ruins.json5");
		RuinConfig.RuinDef def = template.copy();
		def.resetSeconds = 60;
		def.fastKillSeconds = 0;
		def.boss.damage = 0.5;
		def.abilities.clear();
		return def;
	}

	/** A shielded survival player the guardian can hit (the mock client "loaded"). */
	private static ServerPlayer prey(GameTestHelper helper, RuinRecord ruin) {
		ServerPlayer player = TestSupport.survivor(helper, ruin.boss.offset(4, -1, 0));
		shield(player);
		for (int i = 0; i < 200 && !player.connection.hasClientLoaded(); i++) {
			player.connection.tickClientLoadTimeout();
		}
		helper.assertTrue(player.connection.hasClientLoaded(), "mock player can be hit");
		return player;
	}

	private static void shield(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MobEffectInstance.INFINITE_DURATION, 4, false, false));
	}

	private static void away(ServerLevel level, ServerPlayer player, RuinRecord ruin) {
		player.teleportTo(level, ruin.boss.getX() + 300.5, ruin.boss.getY() + 1, ruin.boss.getZ() + 0.5, Set.of(), 0, 0, true);
	}

	private static String bar(RuinRecord ruin) {
		Component title = Encounters.barTitle(ruin);
		return title == null ? "" : title.getString();
	}

	// ================================================================== the celestial jaguar

	@GameTest(maxTicks = 1400)
	public void celestialJaguarIsAGiantHuntingOcelot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper);
		RuinConfig.RuinDef def = jaguar(helper);
		ServerPlayer player = prey(helper, ruin);
		Mob[] boss = new Mob[1];
		GameTestSequence sequence = helper.startSequence()
				.thenExecute(() -> helper.assertTrue(Encounters.awaken(level, ruin, def, false) == null, "jaguar awakened"))
				.thenWaitUntil(() -> {
					boss[0] = Encounters.boss(ruin);
					helper.assertTrue(boss[0] != null, "waiting for the celestial jaguar");
				})
				.thenExecute(() -> {
					helper.assertTrue(boss[0].getType() == EntityTypes.OCELOT, "an ocelot, got " + boss[0].getType());
					helper.assertTrue(Math.abs(boss[0].getAttributeValue(Attributes.SCALE) - def.boss.scale) < 1.0E-6,
							"scaled x" + def.boss.scale + ": " + boss[0].getAttributeValue(Attributes.SCALE));
					helper.assertTrue(Math.abs(boss[0].getMaxHealth() - def.boss.health) < 0.5, "configured health " + boss[0].getMaxHealth());
					helper.assertTrue(boss[0].getAttributeValue(Attributes.STEP_HEIGHT) >= 1.0, "climbs a full block");
					helper.assertTrue(boss[0].isSilent(), "no meow: it growls");
					helper.assertTrue(boss[0].getCustomName() != null && boss[0].getCustomName().getString().contains("Jaguar"), "named");
					helper.assertTrue(Encounters.barColor(ruin) == BossEvent.BossBarColor.YELLOW, "yellow boss bar");
					helper.assertFalse(Encounters.minions(ruin).isEmpty(), "escort");
					for (Mob minion : Encounters.minions(ruin)) {
						helper.assertTrue(UNDEAD.contains(minion.getType()) && !(minion instanceof Raider), "the temple's undead, got " + minion.getType());
						if (minion.getType() != EntityTypes.CAVE_SPIDER) {
							helper.assertTrue(minion.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET), "golden helmet");
						}
						minion.setNoAi(true);
					}
					// Fed, tamed, leashed: refused.
					player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COD, 4));
					InteractionResult fed = UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, boss[0], null);
					helper.assertTrue(fed == InteractionResult.FAIL, "a ruin guardian cannot be fed, got " + fed);
					player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				})
				.thenWaitUntil(() -> helper.assertTrue(boss[0].getTarget() == player, "waiting for the jaguar to hunt the survival player, target "
						+ boss[0].getTarget()))
				// (Its box follows the scale attribute once it ticked.)
				.thenWaitUntil(() -> helper.assertTrue(boss[0].getBbWidth() > 2.5 && boss[0].getBbHeight() > 3.0, "waiting for a giant: "
						+ boss[0].getBbWidth() + " x " + boss[0].getBbHeight()))
				.thenWaitUntil(() -> helper.assertTrue(boss[0].onGround(), "waiting for the jaguar on the floor"));
		RuinConfig.AbilityDef claw = ability("claw_sweep", "jade", 4, 4, 3);
		step(helper, sequence, ruin, player, boss, claw, 4.5, true, () -> helper.assertTrue(player.hasEffect(MobEffects.POISON),
				"raked by the venomous claws"));
		RuinConfig.AbilityDef roar = ability("roar", "solar", 2, 8, 1);
		step(helper, sequence, ruin, player, boss, roar, 5.5, true, () -> helper.assertTrue(player.hasEffect(MobEffects.SLOWNESS)
				&& player.hasEffect(MobEffects.WEAKNESS), "the roar slows and weakens"));
		RuinConfig.AbilityDef pounce = ability("pounce", "gold", 4, 4, 1);
		step(helper, sequence, ruin, player, boss, pounce, 5.5, false, () -> helper.assertTrue(boss[0].distanceTo(player) < 4.5,
				"the jaguar pounced on the player, distance " + boss[0].distanceTo(player)));
		RuinConfig.AbilityDef spirits = ability("spirit_jaguars", "jade", 1, 4, 2);
		step(helper, sequence, ruin, player, boss, spirits, 5.5, true, () -> {
			List<Mob> spirit = Encounters.minions(ruin).stream().filter(m -> m.getType() == EntityTypes.OCELOT).toList();
			helper.assertTrue(!spirit.isEmpty(), "spirit jaguars appeared among the escort");
			for (Mob mob : spirit) {
				helper.assertTrue(mob.isCurrentlyGlowing() && mob.entityTags().contains("guildy_ruin"), "a glowing spirit of the fight");
				helper.assertTrue(mob.getTarget() == player, "it hunts the player too, target " + mob.getTarget());
				mob.discard();
			}
		});
		RuinConfig.AbilityDef stalk = ability("stalk", "gold", 2, 1.5, 1);
		sequence.thenExecute(() -> {
			boss[0].setNoAi(false);
			boss[0].teleportTo(ruin.boss.getX() + 0.5, ruin.boss.getY(), ruin.boss.getZ() + 0.5);
			player.teleportTo(level, ruin.boss.getX() + 5.5, ruin.boss.getY(), ruin.boss.getZ() + 0.5, Set.of(), 0, 0, true);
			helper.assertTrue(Encounters.forceAbility(ruin, stalk), "stalk used");
			helper.assertTrue(boss[0].hasEffect(MobEffects.INVISIBILITY), "the jaguar vanishes");
			helper.assertTrue(boss[0].getAttribute(Attributes.MOVEMENT_SPEED).getModifier(STALK_SPEED) != null, "faster while it stalks");
			helper.assertFalse(boss[0].isCustomNameVisible(), "its name hidden");
		})
				.thenWaitUntil(() -> helper.assertFalse(boss[0].hasEffect(MobEffects.INVISIBILITY), "waiting for the ambush"))
				.thenExecute(() -> {
					helper.assertTrue(boss[0].getAttribute(Attributes.MOVEMENT_SPEED).getModifier(STALK_SPEED) == null, "its normal speed again");
					helper.assertTrue(boss[0].isCustomNameVisible(), "its name shown again");
					helper.assertTrue(boss[0].isAlive() && Encounters.isActive(ruin), "fight still running");
					away(level, player, ruin);
					Encounters.reset(ruin);
				})
				.thenSucceed();
	}

	private static RuinConfig.AbilityDef ability(String type, String style, double damage, double radius, int count) {
		RuinConfig.AbilityDef ability = new RuinConfig.AbilityDef();
		ability.type = type;
		ability.style = style;
		ability.damage = damage;
		ability.radius = radius;
		ability.count = count;
		return ability;
	}

	/**
	 * Uses an ability now (cooldowns ignored), the jaguar on its spawn cell facing the player {@code distance} blocks away;
	 * {@code frozen}: without AI (it stays there), else free to move (a pounce flies).
	 */
	private static void step(GameTestHelper helper, GameTestSequence sequence, RuinRecord ruin, ServerPlayer player, Mob[] boss,
			RuinConfig.AbilityDef ability, double distance, boolean frozen, Runnable check) {
		sequence.thenExecute(() -> {
			boss[0].setNoAi(true);
			boss[0].teleportTo(ruin.boss.getX() + 0.5, ruin.boss.getY(), ruin.boss.getZ() + 0.5);
			boss[0].lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(ruin.boss.offset(5, 0, 0)));
			player.teleportTo(helper.getLevel(), ruin.boss.getX() + 0.5 + distance, ruin.boss.getY(), ruin.boss.getZ() + 0.5, Set.of(), 0, 0, true);
			player.removeAllEffects();
			shield(player);
			player.clearFire();
		})
				.thenIdle(3)
				.thenExecute(() -> {
					boss[0].setNoAi(frozen);
					helper.assertTrue(Encounters.forceAbility(ruin, ability), "ability " + ability.type + " " + ability.style + " used");
				})
				.thenIdle(40)
				.thenExecute(check);
	}

	// ================================================================== its cursed form

	@GameTest(maxTicks = 600)
	public void obsidianJaguarIsAGiantBlackCat(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper);
		RuinConfig.RuinDef def = jaguar(helper);
		ServerPlayer player = prey(helper, ruin);
		Mob[] boss = new Mob[1];
		helper.startSequence()
				.thenExecute(() -> helper.assertTrue(Encounters.awaken(level, ruin, def, false, true) == null, "cursed jaguar awakened"))
				.thenWaitUntil(() -> {
					boss[0] = Encounters.boss(ruin);
					helper.assertTrue(boss[0] != null, "waiting for the obsidian jaguar");
				})
				.thenExecute(() -> {
					helper.assertTrue(boss[0] instanceof Cat, "a cat, got " + boss[0].getType());
					Cat cat = (Cat) boss[0];
					helper.assertTrue(cat.getVariant().is(CatVariants.ALL_BLACK), "all black, got " + cat.getVariant());
					helper.assertTrue(cat.getAttributeValue(Attributes.SCALE) >= def.boss.scale - 1.0E-6, "giant: " + cat.getAttributeValue(Attributes.SCALE));
					String name = cat.getCustomName() == null ? "" : cat.getCustomName().getString();
					helper.assertTrue(name.contains("obsidienne") && name.contains("Maudit"), "named: " + name);
					helper.assertTrue(CursedFights.isCursed(ruin) && Encounters.barColor(ruin) == BossEvent.BossBarColor.PURPLE, "a cursed fight");
					helper.assertTrue(bar(ruin).contains("obsidienne"), "boss bar: " + bar(ruin));
					player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COD, 4));
					InteractionResult fed = UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, cat, null);
					helper.assertTrue(fed == InteractionResult.FAIL && !cat.isTame(), "never tamed, got " + fed);
					player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
					for (Mob minion : Encounters.minions(ruin)) {
						minion.setNoAi(true);
					}
				})
				.thenWaitUntil(() -> helper.assertTrue(boss[0].getTarget() == player, "waiting for the black cat to hunt the player, target "
						+ boss[0].getTarget()))
				.thenExecute(() -> {
					helper.assertFalse(((Cat) boss[0]).isInSittingPose(), "never sits");
					away(level, player, ruin);
					Encounters.reset(ruin);
					helper.assertFalse(Encounters.isActive(ruin), "fight stopped");
				})
				.thenSucceed();
	}

	// ================================================================== themed guards

	@GameTest(maxTicks = 300)
	public void mayanTemplesPostTheirUndead(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper);
		for (String id : List.of("temple_maya", "grand_temple_maya")) {
			ruin.ruinId = id;
			int posted = RoomGuards.postNow(level, ruin);
			helper.assertTrue(posted > 0, id + ": guards posted, got " + posted);
			List<Mob> guards = RoomGuards.guards(ruin);
			helper.assertTrue(guards.size() == posted, id + ": every guard loaded");
			boolean jade = id.equals("temple_maya");
			for (Mob guard : guards) {
				guard.setNoAi(true);
				helper.assertTrue(UNDEAD.contains(guard.getType()), id + ": undead guard, got " + guard.getType());
				helper.assertFalse(guard instanceof Raider, id + ": no raider in a Mayan temple: " + guard.getType());
				helper.assertTrue(guard.getItemBySlot(EquipmentSlot.HEAD).is(jade ? Items.TURTLE_HELMET : Items.GOLDEN_HELMET),
						id + ": " + (jade ? "jade" : "golden") + " helmet, got " + guard.getItemBySlot(EquipmentSlot.HEAD));
				helper.assertTrue(guard.getCustomName() != null, id + ": named guard");
			}
			helper.assertTrue(RoomGuards.reset(ruin), id + ": guards dismissed");
		}
		helper.succeed();
	}

	// ================================================================== admin scroll command

	@GameTest(maxTicks = 100)
	public void adminScrollGiveTakesTheIncantations(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = TestSupport.survivor(helper, TestSupport.region(level, 2));
		String as = "execute as " + player.getStringUUID() + " run ";
		TestSupport.console(level, as + "guild admin scroll give @s incantation 3");
		TestSupport.console(level, as + "guild admin scroll give @s incantation_maudit 2");
		TestSupport.console(level, "guild admin scroll list");
		helper.succeedWhen(() -> {
			int normal = 0;
			int cursed = 0;
			Inventory inventory = player.getInventory();
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (IncantationItems.is(stack)) {
					normal += stack.getCount();
				} else if (CursedItems.is(stack)) {
					cursed += stack.getCount();
				}
			}
			helper.assertTrue(normal == 3, "3 incantation scrolls given, got " + normal);
			helper.assertTrue(cursed == 2, "2 cursed incantation scrolls given, got " + cursed);
		});
	}
}
