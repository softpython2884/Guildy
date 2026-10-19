package dev.guildy.test;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import dev.guildy.command.GuildChat;
import dev.guildy.command.ShortcutCommands;
import dev.guildy.command.ShortcutRules.Decision;
import dev.guildy.command.ShortcutRules.Outcome;
import dev.guildy.core.GuildyServer;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Top-level shortcuts: /sethome and /home behave like /guild sethome and /guild home for a survival member; custom
 * shortcuts rebuilt in place (as /guild admin reload does) pass their arguments and suggestions through, keep the admin
 * gates, never replace an existing command (vanilla /tp) and disappear when removed from the config.
 */
public class ShortcutGameTests {

	@GameTest(maxTicks = 200)
	public void homeShortcutTeleportsLikeGuildHome(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		BlockPos ground = TestSupport.region(level, 4);
		ServerPlayer leader = TestSupport.survivor(helper, ground.east(3), "sc-home");
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Sch");

		Map<String, Outcome> outcomes = outcomes(server);
		helper.assertTrue(outcomes.get("home") == Outcome.ACTIVE && outcomes.get("sethome") == Outcome.ACTIVE && outcomes.get("g") == Outcome.ACTIVE,
				"default shortcuts registered: " + outcomes);
		boolean flyExists = server.getCommands().getDispatcher().getRoot().getChild("guild").getChild("fly") != null;
		helper.assertTrue(outcomes.get("fly") == (flyExists ? Outcome.ACTIVE : Outcome.MISSING_TARGET),
				"/fly follows /guild fly (exists: " + flyExists + "): " + outcomes.get("fly"));

		run(server, leader, "sethome");
		BlockPos home = leader.blockPosition();
		helper.assertTrue(home.equals(guild.home), "/sethome set the guild home: " + guild.home + " vs " + home);

		// Away from the home (still on the platform), then /home: same warm-up and teleport as /guild home.
		BlockPos away = ground.west(3).above();
		leader.teleportTo(level, away.getX() + 0.5, away.getY(), away.getZ() + 0.5, Set.of(), 0, 0, true);
		run(server, leader, "home");
		Vec3 destination = Vec3.atBottomCenterOf(home);
		helper.succeedWhen(() -> {
			helper.assertTrue(leader.position().distanceTo(destination) < 1.0, "waiting for /home: " + leader.position() + " -> " + destination);
			GuildManager.disband(guild);
		});
	}

	@GameTest(maxTicks = 100)
	public void customShortcutsKeepArgumentsGatesAndOtherCommands(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
		BlockPos ground = TestSupport.region(level, 2);
		ServerPlayer member = TestSupport.survivor(helper, ground, "sc-custom");
		Guild guild = GuildManager.create(TestSupport.uniqueName("Scc"), member);
		CommandNode<CommandSourceStack> vanillaTp = dispatcher.getRoot().getChild("tp");
		helper.assertTrue(vanillaTp != null, "vanilla /tp exists");

		Map<String, String> aliases = GuildyServer.config().shortcuts.aliases;
		Map<String, String> saved = new LinkedHashMap<>(aliases);
		try {
			aliases.put("gtdepot", "deposit");
			aliases.put("gtsave", "/guild admin save");
			aliases.put("tp", "home");
			aliases.put("gtnothing", "nothing here");
			helper.assertTrue(ShortcutCommands.rebuild(server), "shortcuts rebuilt in place");

			Map<String, Outcome> outcomes = outcomes(server);
			helper.assertTrue(outcomes.get("gtdepot") == Outcome.ACTIVE && outcomes.get("gtsave") == Outcome.ACTIVE
					&& outcomes.get("home") == Outcome.ACTIVE, "custom shortcuts registered: " + outcomes);
			helper.assertTrue(outcomes.get("tp") == Outcome.COLLISION, "an existing command is a collision: " + outcomes.get("tp"));
			helper.assertTrue(dispatcher.getRoot().getChild("tp") == vanillaTp, "vanilla /tp is untouched");
			helper.assertTrue(outcomes.get("gtnothing") == Outcome.MISSING_TARGET && dispatcher.getRoot().getChild("gtnothing") == null,
					"missing target skipped: " + outcomes.get("gtnothing"));
			TestSupport.console(level, "guild admin shortcuts"); // the admin report, in the test log

			// Arguments pass through: /gtdepot 3 = /guild deposit 3, with the same suggestions.
			member.getInventory().add(new ItemStack(Items.GOLD_INGOT, 5));
			run(server, member, "gtdepot 3");
			helper.assertTrue(guild.treasury == 3 && member.getInventory().countItem(Items.GOLD_INGOT) == 2,
					"3 gold deposited through the shortcut: treasury " + guild.treasury);
			CommandSourceStack player = member.createCommandSourceStack();
			Suggestions suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("gtdepot ", player)).join();
			helper.assertTrue(suggestions.getList().stream().anyMatch(s -> s.getText().equals("all")),
					"suggestions of /guild deposit: " + suggestions.getList());

			// Admin targets stay gated, directly or below /g.
			CommandSourceStack console = server.createCommandSourceStack();
			helper.assertTrue(!parses(dispatcher, "gtsave", player) && parses(dispatcher, "gtsave", console),
					"/gtsave is for admins only");
			helper.assertTrue(!parses(dispatcher, "g admin save", player) && parses(dispatcher, "g admin save", console),
					"/g admin is for admins only");
			helper.assertTrue(parses(dispatcher, "g info", player) && parses(dispatcher, "home", player), "player shortcuts usable");

			// /guild help lists the shortcuts this player may use.
			GuildChat.help(member);
			List<String> chat = TestSupport.chat(member);
			String line = chat.stream().filter(text -> text.contains("Raccourcis")).reduce((a, b) -> b).orElse("");
			helper.assertTrue(line.contains("[/home]") && line.contains("[/gtdepot]") && !line.contains("gtsave") && !line.contains("[/tp]"),
					"help line: " + line);
		} finally {
			aliases.clear();
			aliases.putAll(saved);
			ShortcutCommands.rebuild(server);
		}
		helper.assertTrue(dispatcher.getRoot().getChild("gtdepot") == null && dispatcher.getRoot().getChild("gtsave") == null,
				"removed shortcuts are gone after the rebuild");
		helper.assertTrue(outcomes(server).get("home") == Outcome.ACTIVE, "/home still registered");
		helper.assertTrue(dispatcher.getRoot().getChild("tp") == vanillaTp, "vanilla /tp still untouched");
		GuildManager.disband(guild);
		helper.succeed();
	}

	/** Runs a command typed by the player (like a chat command). */
	private static void run(MinecraftServer server, ServerPlayer player, String command) {
		server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
	}

	/** Whether the whole command parses to something executable for that source. */
	private static boolean parses(CommandDispatcher<CommandSourceStack> dispatcher, String command, CommandSourceStack source) {
		ParseResults<CommandSourceStack> parse = dispatcher.parse(command, source);
		return !parse.getReader().canRead() && parse.getExceptions().isEmpty() && parse.getContext().getCommand() != null;
	}

	private static Map<String, Outcome> outcomes(MinecraftServer server) {
		Map<String, Outcome> map = new HashMap<>();
		for (Decision decision : ShortcutCommands.decisions(server)) {
			map.putIfAbsent(decision.alias(), decision.outcome());
		}
		return map;
	}
}
