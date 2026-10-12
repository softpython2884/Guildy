package dev.guildy.test;

import dev.guildy.core.GuildyServer;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.villager.GuardData;
import dev.guildy.villager.GuardGearConfig;
import dev.guildy.villager.GuardRole;
import dev.guildy.villager.Task;
import dev.guildy.villager.VillagerModule;
import dev.guildy.villager.VillagerRecord;
import dev.guildy.villager.VitalityConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Villagers surviving the nights: health from the guild level, horde mobs leaving the villagers alone (unless hurt by
 * one), a worker left outside at nightfall brought next to its bed, and the base equipment of a high level guard.
 */
public class VitalityGameTests {
	/** Tag of the horde mobs (WaveSpawner.TAG). */
	private static final String WAVE_TAG = "guildy_wave";

	@GameTest(maxTicks = 400)
	public void guildLevelGivesVillagersMoreHealth(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit low = ZoneGameTests.recruit(helper, "Vie1");
		ZoneGameTests.Recruit high = ZoneGameTests.recruit(helper, "Vie5");
		GuildManager.setLevel(high.guild(), 5);
		VitalityConfig vitality = VillagerModule.config().vitality;
		float base = 20.0f;
		float lowMax = base + (float) vitality.bonusHealth(1, false);
		float highMax = base + (float) vitality.bonusHealth(5, false);
		float guardMax = base + (float) vitality.bonusHealth(5, true);
		int[] phase = {0};
		helper.succeedWhen(() -> {
			Villager lowVillager = (Villager) level.getEntity(low.record().uuid);
			Villager highVillager = (Villager) level.getEntity(high.record().uuid);
			helper.assertTrue(lowVillager != null && highVillager != null, "villagers loaded");
			if (phase[0] == 0) {
				helper.assertTrue(highVillager.getMaxHealth() == highMax, "waiting for the level 5 health (" + highVillager.getMaxHealth() + "/" + highMax + ")");
				helper.assertTrue(lowVillager.getMaxHealth() == lowMax, "level 1 villager keeps " + lowMax + " (" + lowVillager.getMaxHealth() + ")");
				helper.assertTrue(highVillager.getMaxHealth() > lowVillager.getMaxHealth(), "a level 5 villager is tougher than a level 1 one");
				helper.assertTrue(highVillager.getHealth() == highMax, "a healthy villager stays at full health (" + highVillager.getHealth() + ")");
				// Level-up: applied at once, the gain is healed.
				lowVillager.setHealth(10.0f);
				GuildManager.addXp(low.guild(), GuildyServer.config().xpForLevel(2) - low.guild().xp);
				float levelTwo = base + (float) vitality.bonusHealth(2, false);
				helper.assertTrue(low.guild().level == 2, "guild level 2");
				helper.assertTrue(lowVillager.getMaxHealth() == levelTwo, "level-up applied at once (" + lowVillager.getMaxHealth() + ")");
				helper.assertTrue(lowVillager.getHealth() == 10.0f + (levelTwo - lowMax), "the gained health is healed (" + lowVillager.getHealth() + ")");
				phase[0] = 1;
				try {
					setTask(high, Task.GUARD);
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			}
			if (phase[0] == 1) {
				helper.assertTrue(highVillager.getMaxHealth() == guardMax, "waiting for the guard health (" + highVillager.getMaxHealth() + "/" + guardMax + ")");
				helper.assertTrue(highVillager.getHealth() == guardMax, "still at full health as a guard");
				GuildManager.disband(low.guild());
				GuildManager.disband(high.guild());
				phase[0] = 2;
			}
			helper.assertTrue(highVillager.getMaxHealth() == base, "released villager back to " + base + " (" + highVillager.getMaxHealth() + ")");
		});
	}

	@GameTest(maxTicks = 800)
	public void hordeMobsLeaveWorkersAlone(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Paix");
		BlockPos ground = recruit.ground();
		Villager worker = (Villager) level.getEntity(recruit.record().uuid);
		helper.assertTrue(worker != null, "worker loaded");
		// The worker stands still (no AI: it would flee) in plain sight of a horde husk two blocks away: a zombie needs to
		// see a villager to pick it, then hunts it even out of sight.
		BlockPos spot = ground.offset(4, 1, 4);
		worker.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		worker.setNoAi(true);
		setTask(recruit, Task.FARMER);
		Husk horde = husk(helper, level, spot.getX() + 2.5, spot.getY(), spot.getZ() + 0.5);
		horde.addTag(WAVE_TAG);
		// Control: a plain husk next to a plain villager, far from the worker, picks it as vanilla does (both removed as
		// soon as it does, before any harm).
		Villager plain = EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
		helper.assertTrue(plain != null, "plain villager");
		plain.snapTo(ground.getX() - 9.5, ground.getY() + 1, ground.getZ() - 9.5, 0.0f, 0.0f);
		plain.setNoAi(true);
		level.addFreshEntity(plain);
		Husk control = husk(helper, level, ground.getX() - 9.5, ground.getY() + 1, ground.getZ() - 7.5);

		boolean[] targeted = {false};
		boolean[] hurt = {false};
		boolean[] controlTargeted = {false};
		boolean[] retaliated = {false};
		long start = helper.getTick();
		helper.onEachTick(() -> {
			if (!retaliated[0] && horde.getTarget() instanceof AbstractVillager) {
				targeted[0] = true;
			}
			if (!retaliated[0] && (!worker.isAlive() || worker.getHealth() < worker.getMaxHealth())) {
				hurt[0] = true;
			}
			if (!controlTargeted[0] && control.getTarget() == plain) {
				controlTargeted[0] = true;
				control.discard();
				plain.discard();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertFalse(targeted[0], "the horde husk targeted a villager");
			helper.assertFalse(hurt[0], "the worker was attacked");
			helper.assertTrue(controlTargeted[0], "waiting for the plain husk to pick its villager (vanilla behaviour)");
			helper.assertTrue(helper.getTick() - start >= 200, "watching the horde husk for 10 s");
			if (!retaliated[0]) {
				// Hit by the villager (a guard fighting it): the horde mob may strike back.
				retaliated[0] = true;
				horde.hurtServer(level, level.damageSources().mobAttack(worker), 1.0f);
			}
			helper.assertTrue(horde.getTarget() == worker, "waiting for the horde husk to target the villager that hurt it");
			horde.discard();
			worker.setNoAi(false);
			GuildManager.disband(recruit.guild());
		});
	}

	/**
	 * Nightfall (environment "night": clock 12600, frozen): a worker shut in a glass cell far from the only bed of the
	 * territory gets that bed and is brought next to it.
	 */
	@GameTest(environment = "guildy-test:night", maxTicks = 400)
	public void workerOutsideAtNightfallIsBroughtToItsBed(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 12);
		TestSupport.forceChunks(level, ground, 3);
		var leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Nuit");
		TestSupport.console(level, "guild admin villager spawn " + guild.name);
		ZoneGameTests.command(level, leader, "guild villagers accept");
		helper.assertTrue(guild.villagers.members.size() == 1, "candidate accepted as guild villager");
		VillagerRecord record = guild.villagers.members.values().iterator().next();
		ZoneGameTests.Recruit recruit = new ZoneGameTests.Recruit(guild, record, leader, ground, ground.offset(4, 1, 0));

		BlockPos foot = ground.offset(0, 1, 8);
		BlockPos head = foot.relative(Direction.SOUTH);
		BlockState bed = Blocks.BED.pick(DyeColor.BLUE).defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
		level.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), Block.UPDATE_ALL);
		level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_ALL);
		Villager worker = (Villager) level.getEntity(record.uuid);
		helper.assertTrue(worker != null, "worker loaded");
		BlockPos cell = ground.offset(-8, 1, -6);
		cage(level, cell);
		worker.teleportTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
		setTask(recruit, Task.FARMER);

		helper.succeedWhen(() -> {
			double dist = Math.min(worker.distanceToSqr(Vec3.atBottomCenterOf(foot)), worker.distanceToSqr(Vec3.atBottomCenterOf(head)));
			helper.assertTrue(dist <= 3.0 * 3.0, "waiting for the worker to be brought next to the bed (at " + worker.blockPosition() + ")");
			Optional<GlobalPos> home = worker.getBrain().getMemory(MemoryModuleType.HOME);
			helper.assertTrue(home.isPresent() && (home.get().pos().equals(head) || home.get().pos().equals(foot)), "the free bed became its bed");
			helper.assertTrue(worker.isAlive(), "alive");
			GuildManager.disband(guild);
		});
	}

	@GameTest(maxTicks = 600)
	public void highLevelGuardGetsTheBaseGear(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ZoneGameTests.Recruit recruit = ZoneGameTests.recruit(helper, "Arsenal");
		VillagerRecord record = recruit.record();
		GuildManager.setLevel(recruit.guild(), 10);
		GuardData data = new GuardData();
		data.autoRole = false;
		data.role = GuardRole.SWORD;
		record.guard = data;
		setTask(recruit, Task.GUARD);
		GuardGearConfig.GearLevel step = VillagerModule.config().guardGear.forLevel(10);
		helper.assertTrue(step != null && !step.armor.isEmpty(), "the default table gives armor at level 10");
		Item sword = item(step.sword);
		Item ranged = item(step.ranged);
		double armor = 0.0;
		double toughness = 0.0;
		for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
			String suffix = switch (slot) {
				case HEAD -> "_helmet";
				case CHEST -> "_chestplate";
				case LEGS -> "_leggings";
				default -> "_boots";
			};
			ItemAttributeModifiers modifiers = new ItemStack(item(step.armor + suffix)).getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
					ItemAttributeModifiers.EMPTY);
			armor += modifiers.compute(Attributes.ARMOR, 0.0, slot);
			toughness += modifiers.compute(Attributes.ARMOR_TOUGHNESS, 0.0, slot);
		}
		double expectedArmor = armor;
		double expectedToughness = toughness;
		float guardMax = 20.0f + (float) VillagerModule.config().vitality.bonusHealth(10, true);
		int[] phase = {0};
		helper.succeedWhen(() -> {
			Villager guard = (Villager) level.getEntity(record.uuid);
			helper.assertTrue(guard != null, "guard loaded");
			if (phase[0] == 0) {
				helper.assertTrue(guard.getMainHandItem().is(sword), "waiting for the swordsman to hold the level 10 sword (" + guard.getMainHandItem() + ")");
				helper.assertTrue(step.sharpness <= 0 || Boolean.TRUE.equals(guard.getMainHandItem().get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)),
						"the shown sword glints: the base sword carries its Sharpness");
				helper.assertTrue(guard.getArmorValue() == (int) expectedArmor, "base armor " + expectedArmor + " (" + guard.getArmorValue() + ")");
				helper.assertTrue(Math.abs(guard.getAttributeValue(Attributes.ARMOR_TOUGHNESS) - expectedToughness) < 1e-6, "base armor toughness");
				for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND)) {
					helper.assertTrue(guard.getItemBySlot(slot).isEmpty(), "the base equipment is not an item that could be taken: " + slot);
				}
				helper.assertTrue(guard.getMaxHealth() == guardMax, "level 10 guard health " + guardMax + " (" + guard.getMaxHealth() + ")");
				data.role = GuardRole.BOW;
				phase[0] = 1;
			}
			if (phase[0] == 1) {
				helper.assertTrue(guard.getMainHandItem().is(ranged), "waiting for the archer to hold the level 10 ranged weapon (" + guard.getMainHandItem() + ")");
				// Back to level 1 (no base armor in the table), then another task: nothing left.
				GuildManager.setLevel(recruit.guild(), 1);
				phase[0] = 2;
			}
			if (phase[0] == 2) {
				helper.assertTrue(guard.getArmorValue() == 0, "waiting for the level 1 base armor (none) (" + guard.getArmorValue() + ")");
				try {
					setTask(recruit, Task.IDLE);
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
				phase[0] = 3;
			}
			helper.assertTrue(guard.getMaxHealth() == 20.0f, "waiting for the worker health (" + guard.getMaxHealth() + ")");
			helper.assertTrue(!guard.getMainHandItem().is(sword) && !guard.getMainHandItem().is(ranged), "no guard weapon shown anymore");
			GuildManager.disband(recruit.guild());
		});
	}

	// ------------------------------------------------------------------ helpers

	private static void setTask(ZoneGameTests.Recruit recruit, Task task) throws ReflectiveOperationException {
		ZoneGameTests.setTask(recruit, task);
	}

	/** Glass walls and roof around a one-block cell (feet at {@code cell}). */
	private static void cage(ServerLevel level, BlockPos cell) {
		BlockState glass = Blocks.GLASS.defaultBlockState();
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			level.setBlockAndUpdate(cell.relative(direction), glass);
			level.setBlockAndUpdate(cell.relative(direction).above(), glass);
		}
		level.setBlockAndUpdate(cell.above(2), glass);
		level.setBlockAndUpdate(cell, Blocks.AIR.defaultBlockState());
		level.setBlockAndUpdate(cell.above(), Blocks.AIR.defaultBlockState());
	}

	/** A husk (no sunburn) with its AI, kept loaded. */
	private static Husk husk(GameTestHelper helper, ServerLevel level, double x, double y, double z) {
		Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.EVENT);
		helper.assertTrue(husk != null, "husk");
		husk.snapTo(x, y, z, 0.0f, 0.0f);
		husk.setPersistenceRequired();
		level.addFreshEntity(husk);
		return husk;
	}

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
	}
}
