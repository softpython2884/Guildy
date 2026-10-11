package dev.guildy.test;

import dev.guildy.core.Hooks;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.gui.GuildMenus;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Smoke test: every menu opens (as a real server-side container) for a guildless player and a guild leader.
 */
public class MenuGameTests {

	@GameTest(maxTicks = 60)
	public void everyMenuOpens(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 4);
		ServerPlayer loner = TestSupport.survivor(helper, ground.east(2));
		open(helper, loner, "no-guild menu", Hooks.openMainMenu);

		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Menu");
		Map<String, Consumer<ServerPlayer>> menus = new LinkedHashMap<>();
		menus.put("main", Hooks.openMainMenu);
		menus.put("members", GuildMenus::openMembers);
		menus.put("territory", GuildMenus::openTerritory);
		menus.put("treasury", GuildMenus::openTreasury);
		menus.put("banner", GuildMenus::openBanner);
		menus.put("waves", p -> Hooks.openWaves.accept(p, guild));
		menus.put("structures", p -> Hooks.openStructures.accept(p, guild));
		menus.put("quests", p -> Hooks.openQuests.accept(p, guild));
		menus.put("villagers", p -> Hooks.openVillagers.accept(p, guild));
		menus.put("trade hub", p -> level.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(), "guild trade"));
		for (var entry : menus.entrySet()) {
			open(helper, leader, entry.getKey(), entry.getValue());
		}
		GuildManager.disband(guild);
		helper.succeed();
	}

	private static void open(GameTestHelper helper, ServerPlayer player, String name, Consumer<ServerPlayer> opener) {
		player.closeContainer();
		opener.accept(player);
		helper.assertTrue(player.containerMenu != player.inventoryMenu, "menu '" + name + "' should be open");
	}
}
