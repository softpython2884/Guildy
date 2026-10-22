package dev.guildy.test;

import dev.guildy.core.GuildyItems;
import dev.guildy.core.UniqueDrops;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.unique.UniqueConfig;
import dev.guildy.unique.UniqueItems;
import dev.guildy.unique.UniqueModule;
import dev.guildy.unique.UniqueRegistry;
import dev.guildy.villager.VillagerRecord;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * The seven uniques of the ruins: their components, the collection of 33, the favourite weapon of a guardian (context of a
 * ruin roll), and each power through the real game paths on a zombie, next to a survival player and a guild villager that
 * are never hurt (nor slowed, frozen, poisoned or pushed).
 */
public class UniqueRuinGameTests {

	/** A ground, the wielder (survival), a bystander player (survival, can be hit) and a guild villager standing still. */
	private record Arena(ServerLevel level, BlockPos ground, ServerPlayer wielder, ServerPlayer bystander, Villager villager,
			float villagerHealth) {
		/** Nobody but monsters was touched by the power. */
		void assertSpared(GameTestHelper helper) {
			helper.assertTrue(bystander.getHealth() >= bystander.getMaxHealth(), "the bystander player is never hurt: " + bystander.getHealth());
			helper.assertTrue(villager.isAlive() && villager.getHealth() >= villagerHealth, "the guild villager is never hurt: "
					+ villager.getHealth() + " / " + villagerHealth);
			for (var effect : List.of(MobEffects.SLOWNESS, MobEffects.POISON, MobEffects.WITHER)) {
				helper.assertFalse(bystander.hasEffect(effect) || villager.hasEffect(effect), "no harmful effect on the bystanders");
			}
			helper.assertTrue(bystander.getRemainingFireTicks() <= 0 && villager.getRemainingFireTicks() <= 0, "nobody else set on fire");
			helper.assertTrue(villager.getTicksFrozen() == 0 && bystander.getTicksFrozen() == 0, "nobody else frozen");
		}

		void cleanUp(Zombie... zombies) {
			for (Zombie zombie : zombies) {
				zombie.discard();
			}
			wielder.stopUsingItem();
		}
	}

	private static Arena arena(GameTestHelper helper, String prefix) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 12);
		TestSupport.forceChunks(level, ground, 1);
		BlockPos home = ground.offset(-8, 0, -8);
		ServerPlayer leader = TestSupport.player(helper, home);
		Guild guild = TestSupport.foundGuild(helper, leader, home, prefix);
		TestSupport.console(level, "guild admin villager spawn " + guild.name);
		ZoneGameTests.command(level, leader, "guild villagers accept");
		helper.assertTrue(guild.villagers.members.size() == 1, "candidate accepted as guild villager");
		VillagerRecord record = guild.villagers.members.values().iterator().next();
		Villager villager = (Villager) level.getEntity(record.uuid);
		helper.assertTrue(villager != null, "guild villager loaded");
		villager.setNoAi(true);
		ServerPlayer wielder = hittable(helper, TestSupport.survivor(helper, ground, TestSupport.uniqueName(prefix + "W")));
		ServerPlayer bystander = hittable(helper, TestSupport.survivor(helper, ground, TestSupport.uniqueName(prefix + "B")));
		// The wielder belongs to the villager's guild: his powers never touch his own village either.
		GuildManager.addMember(guild, wielder.getUUID(), wielder.getName().getString());
		return new Arena(level, ground, wielder, bystander, villager, villager.getHealth());
	}

	/** A mock player whose client "loaded": it can take damage (a power hitting it would show). */
	private static ServerPlayer hittable(GameTestHelper helper, ServerPlayer player) {
		for (int i = 0; i < 200 && !player.connection.hasClientLoaded(); i++) {
			player.connection.tickClientLoadTimeout();
		}
		helper.assertTrue(player.connection.hasClientLoaded(), "mock player can be hit");
		return player;
	}

	private static void place(ServerPlayer player, BlockPos ground, float yaw) {
		player.teleportTo(player.level(), ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, Set.of(), yaw, 0.0F, true);
		player.setYHeadRot(yaw);
		player.setOnGround(true);
	}

	private static void place(Villager villager, BlockPos ground) {
		villager.teleportTo(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5);
		villager.setDeltaMovement(0, 0, 0);
	}

	/** A zombie without AI (a chainmail helmet: no sunburn, and it can still freeze), with a lot of health to survive the power. */
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

	private static InteractionResult use(Arena arena) {
		return UseItemCallback.EVENT.invoker().interact(arena.wielder, arena.level, InteractionHand.MAIN_HAND);
	}

	/** nextDouble() returns these values in turn (then the last one). */
	private static RandomGenerator sequence(double... values) {
		int[] next = {0};
		return () -> ((long) (values[Math.min(next[0]++, values.length - 1)] * (1L << 53))) << 11;
	}

	// ================================================================== items, collection, drops

	@GameTest(environment = "guildy-test:day", maxTicks = 200)
	public void ruinUniquesCompleteACollectionOfThirtyThree(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		UniqueConfig cfg = UniqueModule.config();
		helper.assertTrue(cfg.usable().size() == 33, "33 uniques configured (12 set pieces), got " + cfg.usable().size());
		for (String id : UniqueConfig.RUIN_UNIQUES) {
			ItemStack stack = UniqueDrops.create(id);
			helper.assertTrue(id.equals(UniqueItems.idOf(stack)), id + " created");
			helper.assertTrue(stack.getEnchantments().size() == cfg.item(id).enchantments.size(), id + ": every enchantment applied");
			helper.assertTrue(stack.get(DataComponents.LORE).lines().size() >= 7, id + ": rarity, story, power and recharge in the lore");
		}
		ItemStack spear = UniqueDrops.create("lance_du_jaguar_celeste");
		helper.assertTrue(spear.is(Items.NETHERITE_SPEAR) && spear.has(DataComponents.KINETIC_WEAPON), "a real 26.3 spear (charge)");
		UseCooldown cooldown = spear.get(DataComponents.USE_COOLDOWN);
		helper.assertTrue(cooldown != null && cooldown.cooldownGroup().map(UniqueItems.cooldownGroup("lance_du_jaguar_celeste")::equals)
				.orElse(false), "own cooldown group: the recharge shows on the spear");
		ItemStack shield = UniqueDrops.create("egide_des_ames");
		BannerPatternLayers layers = shield.get(DataComponents.BANNER_PATTERNS);
		helper.assertTrue(shield.is(Items.SHIELD) && shield.get(DataComponents.BASE_COLOR) == DyeColor.BLACK && layers != null
				&& layers.layers().size() == 4, "the shield wears its banner (vanilla clients draw it without a pack)");
		helper.assertTrue(UniqueDrops.create("plastron_du_serpent").has(DataComponents.DYED_COLOR), "jade-dyed leather");
		helper.assertTrue("ruin_boss@grand_temple_maya,cursed".equals(UniqueDrops.withContext("ruin_boss", "grand_temple_maya", "", "cursed")),
				"source with its contexts");

		BlockPos ground = TestSupport.region(level, 3);
		ServerPlayer steve = TestSupport.survivor(helper, ground, TestSupport.uniqueName("Coll"));
		Guild guild = GuildManager.create(TestSupport.uniqueName("UqColl"), steve);
		UniqueDrops.Recipient recipient = UniqueDrops.Recipient.of(steve, guild);
		// The guardian of the Grand Temple favours the jaguar spear (and the jaguar warrior set, after it in its table): the
		// middle of its table is the spear. (The module's roller itself: another test may stub UniqueDrops.roller meanwhile.)
		ItemStack favourite = UniqueDrops.recipientRoller.roll("ruin_boss@grand_temple_maya", sequence(0.0, 0.45), recipient);
		helper.assertTrue("lance_du_jaguar_celeste".equals(UniqueItems.idOf(favourite)), "the grand temple's favourite, got " + favourite);
		helper.assertTrue("ruin_boss".equals(GuildyItems.data(favourite).getStringOr("announce", "")), "announced under its plain source");
		helper.assertTrue("ruin_boss".equals(UniqueRegistry.ledger().copy(UniqueItems.serialOf(favourite)).source), "registered under it");
		ItemStack plain = UniqueDrops.recipientRoller.roll("ruin_boss", sequence(0.0, 0.9), recipient);
		helper.assertTrue("plastron_du_serpent".equals(UniqueItems.idOf(plain)), "plain ruin table, got " + plain);

		// Every unique given to the guild: the collection of 33 is complete and announced.
		TestSupport.console(level, "guild admin unique list");
		for (UniqueConfig.UniqueDef def : cfg.usable()) {
			if (!UniqueRegistry.ledger().obtainedBy(guild.id).contains(def.id)) {
				TestSupport.console(level, "execute as " + steve.getStringUUID() + " run guild admin unique give @s " + def.id);
			}
		}
		helper.assertTrue(UniqueRegistry.ledger().obtainedBy(guild.id).size() == 33, "33 uniques in the collection, got "
				+ UniqueRegistry.ledger().obtainedBy(guild.id).size());
		helper.assertTrue(TestSupport.chat(steve).stream().anyMatch(line -> line.contains("33 armes uniques")),
				"collection announced: " + TestSupport.chat(steve));
		steve.getInventory().clearContent();
		GuildManager.disband(guild);
		helper.succeed();
	}

	// ================================================================== powers

	/** Lance du Jaguar céleste: the right click starts the charge with a pounce; at the landing, the claws rake the zombie. */
	@GameTest(environment = "guildy-test:day", maxTicks = 200)
	public void jaguarPounceClawsTheZombie(GameTestHelper helper) {
		Arena arena = arena(helper, "Jaguar");
		BlockPos c = arena.ground.offset(4, 0, 4);
		place(arena.wielder, c, 0.0F);
		place(arena.bystander, c.west(2), 0.0F);
		place(arena.villager, c.east(2));
		Zombie zombie = zombie(arena.level, c.south(2));
		ItemStack spear = UniqueDrops.create("lance_du_jaguar_celeste");
		arena.wielder.setItemInHand(InteractionHand.MAIN_HAND, spear);
		float before = zombie.getHealth();

		InteractionResult result = use(arena);
		helper.assertTrue(result instanceof InteractionResult.Success, "pounce, got " + result);
		helper.assertTrue(arena.wielder.isUsingItem(), "the spear's charge started with the pounce");
		helper.assertTrue(arena.wielder.getCooldowns().isOnCooldown(spear), "recharge shown on the spear");
		helper.assertTrue(arena.wielder.hurtServer(arena.level, arena.level.damageSources().fall(), 6.0F) == false,
				"no fall damage during the pounce");
		helper.succeedWhen(() -> {
			helper.assertTrue(zombie.getHealth() < before, "waiting for the claws at the landing: " + zombie.getHealth());
			helper.assertTrue(zombie.hasEffect(MobEffects.SLOWNESS), "the clawed zombie is slowed");
			arena.assertSpared(helper);
			arena.cleanUp(zombie);
		});
	}

	/** Couronne du Roi de givre: the zombie that hits the wearer is caught in the ice. */
	@GameTest(environment = "guildy-test:day", maxTicks = 120)
	public void frostCrownFreezesTheAttacker(GameTestHelper helper) {
		Arena arena = arena(helper, "Givre");
		BlockPos c = arena.ground.offset(4, 0, 4);
		place(arena.wielder, c, 0.0F);
		place(arena.bystander, c.west(2), 0.0F);
		place(arena.villager, c.east(2));
		Zombie zombie = zombie(arena.level, c.south(1));
		arena.wielder.setItemSlot(EquipmentSlot.HEAD, UniqueDrops.create("couronne_du_roi_de_givre"));
		arena.wielder.setTicksFrozen(100);
		float before = zombie.getHealth();

		arena.wielder.hurtServer(arena.level, arena.level.damageSources().mobAttack(zombie), 2.0F);
		helper.assertTrue(zombie.getHealth() < before, "cold damage to the attacker: " + zombie.getHealth());
		var slowness = zombie.getEffect(MobEffects.SLOWNESS);
		helper.assertTrue(slowness != null && slowness.getAmplifier() == 3, "Slowness IV on the attacker");
		helper.assertTrue(zombie.getTicksFrozen() > 0, "the attacker is frozen");
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(arena.wielder.getTicksFrozen() == 0, "the wearer never freezes");
			arena.assertSpared(helper);
			arena.cleanUp(zombie);
			helper.succeed();
		});
	}

	/** Égide des Âmes: a zombie's blow blocked by the shield goes back to the zombie and heals the wielder. */
	@GameTest(environment = "guildy-test:day", maxTicks = 200)
	public void soulAegisSendsTheBlowBack(GameTestHelper helper) {
		Arena arena = arena(helper, "Egide");
		BlockPos c = arena.ground.offset(4, 0, 4);
		place(arena.wielder, c, 0.0F);
		place(arena.bystander, c.west(2), 0.0F);
		place(arena.villager, c.east(2));
		Zombie zombie = zombie(arena.level, c.south(2));
		arena.wielder.setItemInHand(InteractionHand.OFF_HAND, UniqueDrops.create("egide_des_ames"));
		arena.wielder.startUsingItem(InteractionHand.OFF_HAND);
		for (int i = 0; i < 8; i++) {
			arena.wielder.doTick();
		}
		place(arena.wielder, c, 0.0F);
		helper.assertTrue(arena.wielder.isBlocking(), "raising the shield");
		arena.wielder.setHealth(12);
		float before = zombie.getHealth();

		arena.wielder.hurtServer(arena.level, arena.level.damageSources().mobAttack(zombie), 6.0F);
		helper.assertTrue(arena.wielder.getHealth() >= 12, "blocked: " + arena.wielder.getHealth());
		helper.succeedWhen(() -> {
			helper.assertTrue(zombie.getHealth() <= before - 5.9F, "waiting for the souls to strike the zombie: " + zombie.getHealth());
			helper.assertTrue(arena.wielder.getHealth() >= 15, "waiting for the drain to heal the wielder: " + arena.wielder.getHealth());
			arena.assertSpared(helper);
			arena.cleanUp(zombie);
		});
	}

	/** Masse du Zénith: the right click leaps (no fall damage), the landing bursts into a sun that burns the zombie. */
	@GameTest(environment = "guildy-test:day", maxTicks = 200)
	public void solarLeapBurnsTheZombie(GameTestHelper helper) {
		Arena arena = arena(helper, "Zenith");
		BlockPos c = arena.ground.offset(4, 0, 4);
		place(arena.wielder, c, 0.0F);
		place(arena.bystander, c.west(2), 0.0F);
		place(arena.villager, c.east(2));
		Zombie zombie = zombie(arena.level, c.south(3));
		ItemStack mace = UniqueDrops.create("masse_du_zenith");
		arena.wielder.setItemInHand(InteractionHand.MAIN_HAND, mace);
		float before = zombie.getHealth();

		InteractionResult result = use(arena);
		helper.assertTrue(result instanceof InteractionResult.Success, "leap, got " + result);
		helper.assertTrue(arena.wielder.getCooldowns().isOnCooldown(mace), "recharge shown on the mace");
		helper.assertTrue(!arena.wielder.hurtServer(arena.level, arena.level.damageSources().fall(), 6.0F), "no fall damage during the leap");
		helper.succeedWhen(() -> {
			// Undead: twice the burst's damage (16, a little less through the zombie's armor).
			helper.assertTrue(zombie.getHealth() <= before - 12F, "waiting for the sun to burst at the landing: " + zombie.getHealth());
			helper.assertTrue(zombie.getRemainingFireTicks() > 0, "the zombie burns");
			arena.assertSpared(helper);
			arena.cleanUp(zombie);
		});
	}

	/** Lame du Pacte: three blows seal the zombie, the pentagram opens and bursts (magic damage, withering). */
	@GameTest(environment = "guildy-test:day", maxTicks = 200)
	public void pactSealBurstsOnTheThirdBlow(GameTestHelper helper) {
		Arena arena = arena(helper, "Pacte");
		BlockPos c = arena.ground.offset(4, 0, 4);
		place(arena.wielder, c, 0.0F);
		place(arena.bystander, c.west(3), 0.0F);
		place(arena.villager, c.east(3));
		Zombie zombie = zombie(arena.level, c.south(2));
		Zombie neighbour = zombie(arena.level, c.south(4));
		arena.wielder.setItemInHand(InteractionHand.MAIN_HAND, UniqueDrops.create("lame_du_pacte"));
		for (int blow = 0; blow < 3; blow++) {
			zombie.damageCooldownTime = 0;
			arena.wielder.attack(zombie);
			helper.assertFalse(zombie.hasEffect(MobEffects.WITHER), "no pact before the burst (blow " + (blow + 1) + ")");
		}
		float afterBlows = zombie.getHealth();
		helper.assertTrue(afterBlows < 200, "the blows landed");
		helper.succeedWhen(() -> {
			helper.assertTrue(zombie.getHealth() <= afterBlows - 11.9F, "waiting for the pact to burst: " + zombie.getHealth());
			helper.assertTrue(zombie.hasEffect(MobEffects.WITHER), "withered by the pact");
			helper.assertTrue(neighbour.getHealth() >= 200, "the curse spreads a seal, not damage");
			arena.assertSpared(helper);
			arena.cleanUp(zombie, neighbour);
		});
	}

	/** Hache du Seigneur de guerre: the rift runs along the ground, burns the zombie in its way, passes under the others. */
	@GameTest(environment = "guildy-test:day", maxTicks = 200)
	public void infernalRiftBurnsTheZombieInItsWay(GameTestHelper helper) {
		Arena arena = arena(helper, "Faille");
		BlockPos c = arena.ground.offset(-6, 0, 4);
		// Facing east (+x): the zombie, then the bystander and the guild villager stand on the rift's line.
		place(arena.wielder, c, -90.0F);
		place(arena.bystander, c.east(7), 0.0F);
		place(arena.villager, c.east(9));
		Zombie zombie = zombie(arena.level, c.east(4));
		ItemStack axe = UniqueDrops.create("hache_du_seigneur_de_guerre");
		arena.wielder.setItemInHand(InteractionHand.MAIN_HAND, axe);
		float before = zombie.getHealth();

		InteractionResult result = use(arena);
		helper.assertTrue(result instanceof InteractionResult.Success, "rift, got " + result);
		helper.assertTrue(arena.wielder.getCooldowns().isOnCooldown(axe), "recharge shown on the axe");
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(zombie.getHealth() < before, "waiting for the rift: " + zombie.getHealth()))
				.thenExecute(() -> helper.assertTrue(zombie.getRemainingFireTicks() > 0, "the zombie burns"))
				.thenIdle(14)
				.thenExecute(() -> {
					arena.assertSpared(helper);
					arena.cleanUp(zombie);
				})
				.thenSucceed();
	}

	/** Plastron du Serpent à plumes: under 40 % health, the jade gale repels the zombie and heals the wearer. */
	@GameTest(environment = "guildy-test:day", maxTicks = 120)
	public void jadeGaleRepelsTheZombie(GameTestHelper helper) {
		Arena arena = arena(helper, "Jade");
		BlockPos c = arena.ground.offset(4, 0, 4);
		place(arena.wielder, c, 0.0F);
		place(arena.bystander, c.west(2), 0.0F);
		place(arena.villager, c.east(2));
		Zombie zombie = zombie(arena.level, c.south(2));
		arena.wielder.setItemSlot(EquipmentSlot.CHEST, UniqueDrops.create("plastron_du_serpent"));
		arena.wielder.setHealth(14);
		arena.wielder.hurtServer(arena.level, arena.level.damageSources().mobAttack(zombie), 1.0F);
		helper.assertFalse(arena.wielder.hasEffect(MobEffects.REGENERATION), "above the threshold: nothing yet");

		arena.wielder.setHealth(8);
		arena.wielder.damageCooldownTime = 0;
		arena.wielder.hurtServer(arena.level, arena.level.damageSources().mobAttack(zombie), 2.0F);
		helper.assertTrue(arena.wielder.hasEffect(MobEffects.REGENERATION) && arena.wielder.hasEffect(MobEffects.SLOW_FALLING),
				"the wearer regenerates and floats");
		helper.assertTrue(zombie.getDeltaMovement().z > 0.3, "the zombie is thrown back: " + zombie.getDeltaMovement());
		helper.assertTrue(arena.villager.getDeltaMovement().horizontalDistance() < 0.01, "the guild villager is not pushed");
		helper.runAfterDelay(2, () -> {
			arena.assertSpared(helper);
			arena.cleanUp(zombie);
			helper.succeed();
		});
	}

}
