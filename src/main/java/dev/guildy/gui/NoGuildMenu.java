package dev.guildy.gui;

import dev.guildy.command.GuildActions;
import dev.guildy.command.GuildChat;
import dev.guildy.config.GuildyConfig;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Menu of a player without guild: create one (anvil name input), pending invitations, ranking, help.
 */
final class NoGuildMenu extends GuildGui {
	private static final int[] INVITE_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

	NoGuildMenu(ServerPlayer player) {
		super(player, 5, null);
	}

	@Override
	protected Component title(@Nullable Guild guild) {
		return Lang.get("menu.noguild.title");
	}

	@Override
	public boolean render() {
		if (GuildManager.of(player) != null) {
			// Joined a guild meanwhile (invitation accepted elsewhere, admin...).
			GuildMenus.open(player);
			return false;
		}
		return super.render();
	}

	@Override
	protected void build(@Nullable Guild ignored) {
		GuildyConfig config = GuildyServer.config();
		setSlot(4, icon(Items.BANNER.pick(DyeColor.WHITE), "menu.noguild.info.name", GuiUtil.lines("menu.noguild.info.lore")));
		button(11, icon(Items.NAME_TAG, "menu.noguild.create.name",
				GuiUtil.lines("menu.noguild.create.lore", config.guilds.nameMinLength, config.guilds.nameMaxLength)), null, () -> askName(""));
		button(13, icon(Items.LECTERN, "menu.noguild.list.name", GuiUtil.lines("menu.noguild.list.lore")), null, () -> {
			close();
			GuildChat.list(player::sendSystemMessage, 1);
		});
		button(15, icon(Items.BOOK, "menu.main.help.name", GuiUtil.lines("menu.main.help.lore")), null, () -> {
			close();
			GuildChat.help(player);
		});

		List<Guild> invites = GuildManager.invitesOf(player.getUUID());
		if (invites.isEmpty()) {
			setSlot(22, icon(Items.PAPER, "menu.noguild.invites.none.name", GuiUtil.lines("menu.noguild.invites.none.lore")));
		}
		for (int i = 0; i < invites.size() && i < INVITE_SLOTS.length; i++) {
			Guild guild = invites.get(i);
			setSlot(INVITE_SLOTS[i], icon(guild.banner.createBanner(GuildyServer.registryAccess()), Lang.get("menu.noguild.invite.name", guild.name),
					GuiUtil.lines("menu.noguild.invite.lore", guild.level, guild.size()))
					.setCallback(onClick("invite", (g, type) -> {
						boolean accept = !type.isRight;
						boolean done = accept ? GuildActions.join(player, guild) : GuildActions.deny(player, guild);
						if (done) {
							GuiUtil.successSound(player);
						} else {
							GuiUtil.errorSound(player);
						}
						if (accept && done) {
							GuildMenus.open(player);
						} else if (isOpen()) {
							render();
						}
					})));
		}
		setSlot(40, GuiUtil.close());
	}

	/** Anvil input for the guild name; invalid names re-open the input with the typed text. */
	private void askName(String initial) {
		GuiUtil.textInput(player, Lang.get("menu.noguild.create.input"), initial, name -> {
			if (name.isBlank()) {
				new NoGuildMenu(player).show();
			} else if (!GuildActions.create(player, name) && GuildManager.of(player) == null) {
				GuiUtil.errorSound(player);
				askName(name);
			}
			// On success the screen stays closed so the title and the explanation are visible.
		}, () -> {
		});
	}
}
