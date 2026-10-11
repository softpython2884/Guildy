package dev.guildy.gui;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Hooks;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildEvents;
import dev.guildy.data.GuildManager;
import eu.pb4.sgui.api.SguiUtils;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Main guild menus (main menu, members, territory, treasury, banner editor...).
 */
public final class GuildMenus {
	private GuildMenus() {
	}

	public static void init() {
		MenuLang.register();
		Hooks.openMainMenu = GuildMenus::open;
		// Menus of players who are no longer members would act on a stale guild: close them.
		GuildEvents.DISBANDED.add(guild -> guild.memberIds().forEach(GuildMenus::closeGuildMenu));
		GuildEvents.MEMBER_LEFT.add((guild, player) -> closeGuildMenu(player));
	}

	/** Main menu, or the "no guild" menu. */
	public static void open(ServerPlayer player) {
		Safe.run("menu.open", () -> {
			Guild guild = GuildManager.of(player);
			if (guild == null) {
				new NoGuildMenu(player).show();
			} else {
				new MainMenu(player, guild).show();
			}
		});
	}

	public static void openMembers(ServerPlayer player) {
		withGuild(player, guild -> new MembersMenu(player, guild, 0).show());
	}

	public static void openTerritory(ServerPlayer player) {
		withGuild(player, guild -> new TerritoryMenu(player, guild).show());
	}

	public static void openTreasury(ServerPlayer player) {
		withGuild(player, guild -> new TreasuryMenu(player, guild).show());
	}

	/** Banner editor on a copy of the current guild banner. */
	public static void openBanner(ServerPlayer player) {
		withGuild(player, guild -> new BannerMenu(player, guild, guild.banner.copy()).show());
	}

	private static void withGuild(ServerPlayer player, Consumer<Guild> action) {
		Safe.run("menu.open", () -> {
			Guild guild = GuildManager.of(player);
			if (guild == null) {
				Msg.send(player, "error.no_guild");
			} else {
				action.accept(guild);
			}
		});
	}

	/** Closes the guild menu the player is looking at, if any. */
	static void closeGuildMenu(UUID playerId) {
		ServerPlayer player = GuildyServer.player(playerId);
		if (player != null && SguiUtils.getCurrentGui(player) instanceof GuildGui menu) {
			Safe.run("menu.close", menu::close);
		}
	}
}
