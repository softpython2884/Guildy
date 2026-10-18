package dev.guildy.test;

import dev.guildy.claim.ClaimService;
import dev.guildy.config.GuildyConfig;
import dev.guildy.core.GuildyItems;
import dev.guildy.core.GuildyServer;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.villager.VillagerManager;
import dev.guildy.villager.VillagerModule;
import dev.guildy.wave.WaveConfig;
import dev.guildy.wave.WaveFormula;
import dev.guildy.wave.WaveModule;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Guild levels past 10: admin XP pushes a guild from level 1 to 20 (one announcement, milestone rewards, rank, free
 * flags, villager place), lowering and levelling again never pays twice, then level 100 (the default maximum) keeps the
 * horde, heart, territory and guard formulas bounded.
 */
public class LevelGameTests {

	@GameTest(maxTicks = 100)
	public void levelsPastTenRewardsAndBounds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 3);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Lvl");
		ServerPlayer outsider = TestSupport.survivor(helper, ground.east(3), "lvl-outsider");
		GuildyConfig config = GuildyServer.config();
		helper.assertTrue(config.maxLevel() == 100, "default maximum level 100, got " + config.maxLevel());
		int villagersAtTen = VillagerModule.config().maxVillagers(10);

		// 1 -> 20 in one go through the admin command: rewards of the levels 11..20 (two milestones: 15 and 20).
		long treasury = guild.treasury;
		TestSupport.console(level, "guild admin addxp " + guild.name + " " + config.xpForLevel(20));
		helper.assertTrue(guild.level == 20, "level 20 reached, got " + guild.level);
		helper.assertTrue(guild.rewardedLevel == 20, "levels rewarded up to 20");
		long gained = guild.treasury - treasury;
		helper.assertTrue(gained == 455, "gold of the levels 11..20 (255) + 2 milestones (200), got " + gained);
		helper.assertTrue(guild.freeFlags == 2, "two free extension flags, got " + guild.freeFlags);
		helper.assertTrue(VillagerManager.maxVillagers(guild) == villagersAtTen + 1, "one more villager place at level 20, got "
				+ VillagerManager.maxVillagers(guild));
		List<String> chat = TestSupport.chat(leader);
		helper.assertTrue(chat.stream().filter(line -> line.contains("passe au niveau")).count() == 1, "a single level-up announcement: " + chat);
		helper.assertTrue(chat.stream().anyMatch(line -> line.contains("gagne 19 niveaux")), "levels gained announced: " + chat);
		helper.assertTrue(chat.stream().anyMatch(line -> line.contains("Palier du niveau 20")), "milestone announced: " + chat);
		helper.assertTrue(chat.stream().anyMatch(line -> line.contains("Nouveau rang") && line.contains("Argent")), "rank announced: " + chat);
		helper.assertTrue(TestSupport.chat(outsider).stream().anyMatch(line -> line.contains(guild.name) && line.contains("niveau")),
				"milestone broadcast to the other players: " + TestSupport.chat(outsider));

		// A free flag: no gold, the price of the bought flags does not rise.
		int bought = guild.extensionFlagsBought;
		long before = guild.treasury;
		helper.assertTrue(ClaimService.extensionFlagPrice(guild) == 0, "free flag first");
		helper.assertTrue(ClaimService.buyExtensionFlag(leader, guild) == null, "free flag taken");
		helper.assertTrue(guild.freeFlags == 1 && guild.extensionFlagsBought == bought && guild.treasury == before, "nothing paid");
		boolean hasFlag = false;
		for (int i = 0; i < leader.getInventory().getContainerSize(); i++) {
			hasFlag |= GuildyItems.is(leader.getInventory().getItem(i), GuildyItems.KIND_EXTENSION_FLAG);
		}
		helper.assertTrue(hasFlag, "the extension flag is in the inventory");

		// Lowered by an admin then levelled up again: the rewards of the levels already rewarded are not paid twice.
		TestSupport.console(level, "guild admin setlevel " + guild.name + " 12");
		helper.assertTrue(guild.level == 12, "level set to 12, got " + guild.level);
		long lowered = guild.treasury;
		long rewardLines = TestSupport.chat(leader).stream().filter(line -> line.contains("Récompenses") || line.contains("Nouveau rang")).count();
		GuildManager.addXp(guild, config.xpForLevel(20) - guild.xp);
		helper.assertTrue(guild.level == 20 && guild.treasury == lowered && guild.freeFlags == 1, "no second payment: treasury "
				+ (guild.treasury - lowered) + ", flags " + guild.freeFlags);
		helper.assertTrue(TestSupport.chat(leader).stream().filter(line -> line.contains("Récompenses") || line.contains("Nouveau rang")).count()
				== rewardLines, "no reward nor rank announced again: " + TestSupport.chat(leader));

		// Level 100 = the default maximum; more XP never goes past it.
		GuildManager.addXp(guild, config.xpForLevel(100));
		helper.assertTrue(guild.level == 100, "level 100 reached, got " + guild.level);
		GuildManager.addXp(guild, 1_000_000_000L);
		helper.assertTrue(guild.level == 100, "never past the maximum level");
		helper.assertTrue(TestSupport.chat(leader).stream().anyMatch(line -> line.contains("Niveau maximum")), "maximum level announced");
		helper.assertTrue(guild.freeFlags == 1 + 16, "16 more milestones (25..100), got " + guild.freeFlags);

		// Bounded formulas at level 100.
		WaveConfig waves = WaveModule.config();
		double alone = WaveFormula.difficulty(waves.difficulty, 0, guild.level, 1);
		helper.assertTrue(Math.abs(alone - 10.0) < 1e-6, "night step 0 alone: D = 10, got " + alone);
		double later = WaveFormula.difficulty(waves.difficulty, 10, guild.level, 1);
		helper.assertTrue(later > alone && later < waves.difficulty.max, "the night step still matters: " + later);
		int mobs = WaveFormula.mobCount(waves.difficulty, alone, 1.0);
		helper.assertTrue(mobs == 36 && mobs <= waves.difficulty.maxMobsPerWave, "36 monsters, got " + mobs);
		helper.assertTrue(WaveFormula.heartIntegrity(waves.heart, guild.level) == 870, "heart 870, got "
				+ WaveFormula.heartIntegrity(waves.heart, guild.level));
		helper.assertTrue(guild.maxChunks() == 156, "156 chunks, got " + guild.maxChunks());
		helper.assertTrue(Math.abs(VillagerModule.config().guardDamage(guild.level) - 13.0) < 1e-6, "guard damage 13");
		helper.assertTrue(VillagerManager.maxVillagers(guild) == villagersAtTen + 9, "nine milestone villager places at level 100, got "
				+ VillagerManager.maxVillagers(guild));

		GuildManager.disband(guild);
		helper.succeed();
	}
}
