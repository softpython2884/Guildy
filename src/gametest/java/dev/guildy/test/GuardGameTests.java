package dev.guildy.test;

import dev.guildy.data.GuildManager;
import dev.guildy.villager.GuardData;
import dev.guildy.villager.GuardRole;
import dev.guildy.villager.Task;
import dev.guildy.villager.VillagerRecord;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Guards: an archer kills a monster at range without hurting the player and the villager standing in its line of fire;
 * a guard puts on iron armor (and a better sword) from a registered storage chest, then brings it back when its task
 * changes; a wounded guard runs to a bed and heals; guild members cannot hurt their own villagers.
 */
public class GuardGameTests {
	/** Entity tag of the guards' arrows (Guards.TAG_ARROW). */
	private static final String GUARD_ARROW_TAG = "guildy_guard_arrow";

	@GameTest(maxTicks = 1600)
	public void archerKillsAtRangeWithoutFriendlyFire(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Archer");
		VillagerRecord record = recruit.record();
		record.guard = forced(GuardRole.BOW);
		BlockPos ground = recruit.ground();
		Villager guard = (Villager) level.getEntity(record.uuid);
		helper.assertTrue(guard != null, "guard loaded");
		guard.teleportTo(ground.getX() + 2.5, ground.getY() + 1, ground.getZ() + 0.5);

		// Bystanders in the line of fire (the arrows fly about 1.5 to 2 blocks above the ground): a villager right in
		// front of the archer, a survival player half-way. A husk (no sunburn) without AI, 10 blocks away.
		Villager bystander = EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
		helper.assertTrue(bystander != null, "bystander villager");
		bystander.snapTo(ground.getX() + 4.0, ground.getY() + 1, ground.getZ() + 0.5, 0.0f, 0.0f);
		bystander.setNoAi(true);
		level.addFreshEntity(bystander);
		ServerPlayer player = TestSupport.survivor(helper, ground.offset(7, 0, 0));
		Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.EVENT);
		helper.assertTrue(husk != null, "husk");
		husk.snapTo(ground.getX() + 12.5, ground.getY() + 1, ground.getZ() + 0.5, 90.0f, 0.0f);
		husk.setNoAi(true);
		husk.setPersistenceRequired();
		level.addFreshEntity(husk);
		float playerHealth = player.getHealth();
		float bystanderHealth = bystander.getHealth();

		// Guard arrows never hurt anything but monsters, even reloaded as plain arrows (tag only).
		Arrow tagged = new Arrow(level, guard, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
		tagged.addTag(GUARD_ARROW_TAG);
		helper.assertTrue(!player.hurtServer(level, level.damageSources().arrow(tagged, guard), 4.0f), "a guard arrow never hurts a player");
		helper.assertTrue(!bystander.hurtServer(level, level.damageSources().arrow(tagged, guard), 4.0f), "a guard arrow never hurts a villager");

		ZoneGameTests.setTask(recruit, Task.GUARD);
		double[] closest = {Double.MAX_VALUE};
		helper.onEachTick(() -> {
			Entity entity = level.getEntity(record.uuid);
			if (entity != null && husk.isAlive()) {
				closest[0] = Math.min(closest[0], entity.distanceTo(husk));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(guard.getMainHandItem().is(Items.BOW), "the archer shows a bow");
			helper.assertTrue(!husk.isAlive(), "waiting for the archer to kill the husk (" + husk.getHealth() + " HP)");
			helper.assertTrue(record.stats.kills >= 1, "kill counted for the guard");
			helper.assertTrue(closest[0] > 4.0, "shot from range (closest " + closest[0] + ")");
			helper.assertTrue(player.getHealth() == playerHealth && player.getArrowCount() == 0, "the player in the line of fire was not touched");
			helper.assertTrue(bystander.getHealth() == bystanderHealth && bystander.getArrowCount() == 0, "the villager in the line of fire was not touched");
			bystander.discard();
			GuildManager.disband(recruit.guild());
		});
	}

	@GameTest(maxTicks = 1600)
	public void guardTakesArmorFromStorageAndReturnsIt(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Armure");
		VillagerRecord record = recruit.record();
		record.guard = forced(GuardRole.SWORD);
		Container chest = (Container) level.getBlockEntity(recruit.deposit());
		List<Item> iron = List.of(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
		for (int i = 0; i < iron.size(); i++) {
			chest.setItem(i, new ItemStack(iron.get(i)));
		}
		chest.setItem(5, new ItemStack(Items.LEATHER_HELMET));
		chest.setItem(6, new ItemStack(Items.DIAMOND_SWORD));
		chest.setItem(7, new ItemStack(Items.STONE_SWORD));
		ZoneGameTests.setTask(recruit, Task.GUARD);

		boolean[] returned = {false};
		helper.succeedWhen(() -> {
			Villager guard = (Villager) level.getEntity(record.uuid);
			helper.assertTrue(guard != null, "guard loaded");
			if (!returned[0]) {
				helper.assertTrue(guard.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)
						&& guard.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)
						&& guard.getItemBySlot(EquipmentSlot.LEGS).is(Items.IRON_LEGGINGS)
						&& guard.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS), "waiting for the guard to put on the iron armor");
				helper.assertTrue(guard.getArmorValue() >= 15, "iron armor protects the guard (armor " + guard.getArmorValue() + ")");
				helper.assertTrue(guard.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.DIAMOND_SWORD), "the better sword is taken");
				for (Item item : iron) {
					helper.assertTrue(count(chest, item) == 0, "the armor left the chest: " + item);
				}
				helper.assertTrue(count(chest, Items.DIAMOND_SWORD) == 0, "the sword left the chest");
				helper.assertTrue(count(chest, Items.LEATHER_HELMET) == 1 && count(chest, Items.STONE_SWORD) == 1, "worse pieces stay in the chest");
				// The villager menu shows the role and the equipment (vanilla clients do not draw armor on villagers).
				ServerPlayer leader = recruit.leader();
				try {
					java.lang.reflect.Method openDetail = Class.forName("dev.guildy.villager.VillagerGui")
							.getDeclaredMethod("openDetail", ServerPlayer.class, dev.guildy.data.Guild.class, java.util.UUID.class);
					openDetail.setAccessible(true);
					leader.closeContainer();
					openDetail.invoke(null, leader, recruit.guild(), record.uuid);
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
				helper.assertTrue(leader.containerMenu != leader.inventoryMenu, "villager menu open");
				helper.assertTrue(leader.containerMenu.getSlot(2).getItem().is(Items.DIAMOND_SWORD), "role button shows the guard's sword");
				helper.assertTrue(leader.containerMenu.getSlot(6).getItem().is(Items.IRON_CHESTPLATE), "equipment button shows the chestplate");
				leader.closeContainer();
				// Another task: the equipment goes back to the storage.
				returned[0] = true;
				try {
					ZoneGameTests.setTask(recruit, Task.IDLE);
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			}
			for (Item item : iron) {
				helper.assertTrue(count(chest, item) == 1, "the armor is back in the chest: " + item);
			}
			helper.assertTrue(count(chest, Items.DIAMOND_SWORD) == 1, "the sword is back in the chest");
			helper.assertTrue(guard.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && guard.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty(),
					"nothing left on the villager (no duplication)");
			GuildManager.disband(recruit.guild());
		});
	}

	@GameTest(maxTicks = 2000)
	public void woundedGuardRetreatsToBedAndHeals(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Repli");
		VillagerRecord record = recruit.record();
		BlockPos foot = recruit.ground().offset(0, 1, 8);
		BlockPos head = foot.relative(Direction.SOUTH);
		BlockState bed = Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
		level.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), Block.UPDATE_ALL);
		level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_ALL);
		ZoneGameTests.setTask(recruit, Task.GUARD);
		Villager guard = (Villager) level.getEntity(record.uuid);
		helper.assertTrue(guard != null, "guard loaded");
		guard.setHealth(5.0f);

		boolean[] nearBed = {false};
		helper.onEachTick(() -> {
			if (!guard.isRemoved()) {
				double dist = Math.min(guard.distanceToSqr(Vec3.atBottomCenterOf(foot)), guard.distanceToSqr(Vec3.atBottomCenterOf(head)));
				nearBed[0] |= dist <= 3.0 * 3.0;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(guard.isAlive(), "the guard is alive");
			helper.assertTrue(nearBed[0], "waiting for the wounded guard to reach the bed (health " + guard.getHealth() + ")");
			helper.assertTrue(guard.getHealth() >= 18.0f, "waiting for the guard to heal (" + guard.getHealth() + "/20)");
			GuildManager.disband(recruit.guild());
		});
	}

	@GameTest(maxTicks = 200)
	public void guildMembersCannotHurtTheirVillagers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Ami");
		Villager villager = (Villager) level.getEntity(recruit.record().uuid);
		helper.assertTrue(villager != null, "villager loaded");
		ServerPlayer member = TestSupport.survivor(helper, recruit.ground().offset(3, 0, 3));
		GuildManager.addMember(recruit.guild(), member.getUUID(), member.getName().getString());
		helper.assertTrue(recruit.guild().isMember(member.getUUID()), "member added");
		villager.teleportTo(member.getX() + 1.0, member.getY(), member.getZ());
		float before = villager.getHealth();

		member.attack(villager);
		helper.assertTrue(villager.getHealth() == before, "melee attack of a member cancelled");
		helper.assertTrue(!villager.hurtServer(level, level.damageSources().playerAttack(member), 6.0f), "member damage (sweep...) cancelled");
		Arrow arrow = new Arrow(level, member, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
		helper.assertTrue(!villager.hurtServer(level, level.damageSources().arrow(arrow, member), 6.0f), "member arrow cancelled");
		helper.assertTrue(villager.getHealth() == before && villager.isAlive(), "the villager was not hurt");

		// The rule only protects the guild's own villagers: a vanilla villager can still be hurt by the member.
		Villager stranger = EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
		helper.assertTrue(stranger != null, "vanilla villager");
		stranger.snapTo(recruit.ground().getX() + 0.5 + 60, recruit.ground().getY() + 1, recruit.ground().getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(stranger);
		helper.assertTrue(stranger.hurtServer(level, level.damageSources().playerAttack(member), 1.0f), "other villagers are not concerned");
		stranger.discard();
		GuildManager.disband(recruit.guild());
		helper.succeed();
	}

	@GameTest(maxTicks = 1200)
	public void swordsmanStrikesWithItsSword(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Epee");
		VillagerRecord record = recruit.record();
		record.guard = forced(GuardRole.SWORD);
		BlockPos ground = recruit.ground();
		Villager guard = (Villager) level.getEntity(record.uuid);
		helper.assertTrue(guard != null, "guard loaded");
		guard.teleportTo(ground.getX() + 2.5, ground.getY() + 1, ground.getZ() + 0.5);
		Container chest = (Container) level.getBlockEntity(recruit.deposit());
		chest.setItem(0, new ItemStack(Items.DIAMOND_SWORD));
		Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.EVENT);
		helper.assertTrue(husk != null, "husk");
		husk.snapTo(ground.getX() + 6.5, ground.getY() + 1, ground.getZ() + 2.5, 0.0f, 0.0f);
		husk.setNoAi(true);
		husk.setPersistenceRequired();
		level.addFreshEntity(husk);
		ZoneGameTests.setTask(recruit, Task.GUARD);
		helper.succeedWhen(() -> {
			helper.assertTrue(guard.getMainHandItem().is(Items.DIAMOND_SWORD), "the swordsman shows the better sword taken from the chest");
			helper.assertTrue(!husk.isAlive(), "waiting for the swordsman to kill the husk (" + husk.getHealth() + " HP)");
			helper.assertTrue(record.stats.kills >= 1, "kill counted for the guard");
			GuildManager.disband(recruit.guild());
		});
	}

	/** A monster too close: the archer steps back to keep its distance, and shoots. */
	@GameTest(maxTicks = 1200)
	public void archerStepsBackFromCloseMonsters(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Recul");
		VillagerRecord record = recruit.record();
		record.guard = forced(GuardRole.BOW);
		BlockPos ground = recruit.ground();
		Villager guard = (Villager) level.getEntity(record.uuid);
		helper.assertTrue(guard != null, "guard loaded");
		guard.teleportTo(ground.getX() + 2.5, ground.getY() + 1, ground.getZ() + 0.5);
		Husk husk = spawnHusk(helper, level, ground.getX() + 5.5, ground.getY() + 1, ground.getZ() + 0.5);
		Vec3 start = guard.position();
		ZoneGameTests.setTask(recruit, Task.GUARD);
		double[] farthest = {0.0};
		double[] moved = {0.0};
		helper.onEachTick(() -> {
			farthest[0] = Math.max(farthest[0], guard.distanceTo(husk));
			moved[0] = Math.max(moved[0], guard.position().distanceTo(start));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(moved[0] > 1.5 && farthest[0] > 4.5,
					"waiting for the archer to step back (moved " + moved[0] + ", distance " + farthest[0] + ")");
			helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "waiting for the archer to shoot");
			husk.discard();
			GuildManager.disband(recruit.guild());
		});
	}

	/** A wounded swordsman heading for its post to heal still strikes the monster next to it. */
	@GameTest(maxTicks = 600)
	public void retreatingGuardStrikesWithinReach(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Riposte");
		VillagerRecord record = recruit.record();
		record.guard = forced(GuardRole.SWORD);
		BlockPos ground = recruit.ground();
		Villager guard = (Villager) level.getEntity(record.uuid);
		helper.assertTrue(guard != null, "guard loaded");
		guard.teleportTo(ground.getX() + 2.5, ground.getY() + 1, ground.getZ() + 0.5);
		Husk husk = spawnHusk(helper, level, ground.getX() + 4.0, ground.getY() + 1, ground.getZ() + 0.5);
		ZoneGameTests.setTask(recruit, Task.GUARD);
		guard.setHealth(4.0f);
		// The husk has no AI: it follows the retreating guard like a chasing monster would, staying within arm's reach.
		helper.onEachTick(() -> {
			if (!husk.isRemoved() && husk.distanceToSqr(guard) > 2.25) {
				husk.teleportTo(guard.getX() + 1.2, guard.getY(), guard.getZ());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "waiting for the wounded guard to strike back");
			helper.assertTrue(guard.getHealth() < 18.0f, "the guard is still recovering (" + guard.getHealth() + ")");
			husk.discard();
			GuildManager.disband(recruit.guild());
		});
	}

	/** "Auto" guards share the roles (archerShare 0.5) around a forced archer: B (forced), S, S, B, S. */
	@GameTest(maxTicks = 200)
	public void autoRolesAreBalanced(GameTestHelper helper) throws Exception {
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Roles");
		List<VillagerRecord> records = new java.util.ArrayList<>();
		records.add(recruit.record());
		for (int i = 0; i < 4; i++) {
			VillagerRecord record = new VillagerRecord(java.util.UUID.randomUUID(), "Garde " + i);
			recruit.guild().villagers.members.put(record.uuid, record);
			records.add(record);
		}
		// One archer forced from the menu first: the auto guards balance around it.
		records.getFirst().guard = forced(GuardRole.BOW);
		for (VillagerRecord record : records) {
			setTask(recruit, record, Task.GUARD);
		}
		List<GuardRole> expected = List.of(GuardRole.BOW, GuardRole.SWORD, GuardRole.SWORD, GuardRole.BOW, GuardRole.SWORD);
		for (int i = 0; i < records.size(); i++) {
			GuardData data = records.get(i).guard;
			helper.assertTrue(data != null && data.role == expected.get(i),
					"guard " + i + ": expected " + expected.get(i) + ", got " + (data == null ? null : data.role));
		}
		// Back to another task: an auto guard is balanced again the next time it becomes a guard.
		setTask(recruit, records.get(3), Task.IDLE);
		helper.assertTrue(records.get(3).guard.role == null, "auto role released");
		helper.assertTrue(records.getFirst().guard.role == GuardRole.BOW, "a forced role stays");
		GuildManager.disband(recruit.guild());
		helper.succeed();
	}

	private static void setTask(ZoneGameTests.Recruit recruit, VillagerRecord record, Task task) throws ReflectiveOperationException {
		java.lang.reflect.Method method = Class.forName("dev.guildy.villager.VillagerManager")
				.getDeclaredMethod("setTask", dev.guildy.data.Guild.class, VillagerRecord.class, Task.class, ServerPlayer.class);
		method.setAccessible(true);
		method.invoke(null, recruit.guild(), record, task, recruit.leader());
	}

	/** A husk (no sunburn) without AI: it stays where it is put. */
	private static Husk spawnHusk(GameTestHelper helper, ServerLevel level, double x, double y, double z) {
		Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.EVENT);
		helper.assertTrue(husk != null, "husk");
		husk.snapTo(x, y, z, 0.0f, 0.0f);
		husk.setNoAi(true);
		husk.setPersistenceRequired();
		level.addFreshEntity(husk);
		return husk;
	}

	private static GuardData forced(GuardRole role) {
		GuardData data = new GuardData();
		data.autoRole = false;
		data.role = role;
		return data;
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
}
