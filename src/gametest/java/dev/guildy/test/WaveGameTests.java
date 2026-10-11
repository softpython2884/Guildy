package dev.guildy.test;

import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.wave.WaveManager;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Wave scenario: a forced (admin) wave spawns tagged horde mobs at the territory border, and stopping it removes
 * them all.
 */
public class WaveGameTests {

	@GameTest(maxTicks = 900)
	public void forcedWaveSpawnsAndCleansUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 56);
		TestSupport.forceChunks(level, ground, 4);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Wave");

		TestSupport.console(level, "guild admin wave start " + guild.name);
		helper.assertTrue(WaveManager.isActive(guild), "wave should be active after /guild admin wave start");

		helper.succeedWhen(() -> {
			List<Mob> horde = hordeAround(level, guild.heart);
			helper.assertTrue(!horde.isEmpty(), "waiting for horde mobs to spawn");
			TestSupport.console(level, "guild admin wave stop " + guild.name);
			helper.assertFalse(WaveManager.isActive(guild), "wave stopped");
			helper.assertTrue(hordeAround(level, guild.heart).stream().allMatch(Mob::isRemoved), "horde removed on stop");
			GuildManager.disband(guild);
		});
	}

	/**
	 * Real nightfall (environment "dusk": clock 12900, time advancing): with a member at the heart, the horde starts
	 * by itself once the night window opens (13000).
	 */
	@GameTest(environment = "guildy-test:dusk", maxTicks = 400)
	public void nightfallStartsTheHorde(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 24);
		TestSupport.forceChunks(level, ground, 3);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Dusk");
		helper.assertFalse(WaveManager.isActive(guild), "no horde before nightfall");

		helper.succeedWhen(() -> {
			helper.assertTrue(WaveManager.isActive(guild), "waiting for nightfall to start the horde");
			TestSupport.console(level, "guild admin wave stop " + guild.name);
			GuildManager.disband(guild);
		});
	}

	private static List<Mob> hordeAround(ServerLevel level, BlockPos heart) {
		AABB box = new AABB(heart).inflate(96, 128, 96);
		return level.getEntities(EntityTypeTest.forClass(Mob.class), box, mob -> mob.entityTags().contains("guildy_wave"));
	}
}
