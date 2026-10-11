package dev.guildy.test;

import dev.guildy.core.GuildyServer;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildStorage;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * In-world integration tests (headless server). Run with `gradlew build` or `gradlew runGameTest`.
 */
public class GuildyGameTests {

	/** Core: guild creation, indexes, claims lookup, XP level-up, treasury and JSON persistence round-trip. */
	@GameTest
	public void coreGuildLifecycle(GameTestHelper helper) {
		ServerPlayer leader = helper.makeMockServerPlayerInLevel();
		String name = "TestCore" + (System.nanoTime() % 100000);
		Guild guild = GuildManager.create(name, leader);
		helper.assertTrue(GuildManager.of(leader) == guild, "player index");
		helper.assertTrue(GuildManager.byName(name.toUpperCase()) == guild, "name index is case-insensitive");

		BlockPos pos = helper.absolutePos(BlockPos.ZERO);
		long chunk = ChunkKey.ofBlock(pos.getX(), pos.getZ());
		GuildManager.claim(guild, chunk);
		helper.assertTrue(GuildManager.at(helper.getLevel(), pos) == guild, "claim lookup");

		GuildManager.addXp(guild, GuildyServer.config().xpForLevel(2));
		helper.assertTrue(guild.level == 2, "level up at threshold");

		GuildManager.addTreasury(guild, 50);
		helper.assertTrue(GuildManager.takeTreasury(guild, 20) && guild.treasury == 30, "treasury");
		helper.assertFalse(GuildManager.takeTreasury(guild, 31), "cannot overdraw treasury");

		try {
			GuildStorage.save(helper.getLevel().getServer(), GuildManager.all());
			List<Guild> loaded = GuildStorage.load(helper.getLevel().getServer());
			Guild copy = loaded.stream().filter(g -> g.id.equals(guild.id)).findFirst().orElse(null);
			helper.assertTrue(copy != null && copy.owns(chunk) && copy.treasury == 30 && copy.level == 2, "persistence round-trip");
		} catch (Exception e) {
			helper.fail("persistence failed: " + e);
		}

		GuildManager.disband(guild);
		helper.assertTrue(GuildManager.at(helper.getLevel(), pos) == null, "claims removed on disband");
		helper.assertTrue(GuildManager.of(leader) == null, "member index removed on disband");
		helper.succeed();
	}
}
