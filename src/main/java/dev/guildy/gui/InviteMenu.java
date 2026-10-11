package dev.guildy.gui;

import dev.guildy.command.GuildActions;
import dev.guildy.command.GuildRules;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Online players without a guild; clicking one sends an invitation.
 */
final class InviteMenu extends GuildGui {
	private int page;

	InviteMenu(ServerPlayer player, Guild guild, int page) {
		super(player, 6, guild);
		this.page = page;
	}

	@Override
	protected Component title(@Nullable Guild guild) {
		return Lang.get("members.invite_menu.title");
	}

	private List<ServerPlayer> candidates() {
		List<ServerPlayer> list = new ArrayList<>();
		for (ServerPlayer online : GuildyServer.players()) {
			if (online != player && GuildManager.of(online) == null) {
				list.add(online);
			}
		}
		list.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getName().getString(), b.getName().getString()));
		return list;
	}

	@Override
	protected void build(@Nullable Guild guild) {
		if (guild == null) {
			return;
		}
		List<ServerPlayer> candidates = candidates();
		setSlot(4, icon(Items.WRITABLE_BOOK, "members.invite_menu.info.name",
				GuiUtil.lines("members.invite_menu.info.lore", GuildyServer.config().guilds.inviteExpireSeconds)));
		if (candidates.isEmpty()) {
			setSlot(22, icon(Items.PAPER, "members.invite_menu.none.name", GuiUtil.lines("members.invite_menu.none.lore")));
		}
		int pages = GuildRules.pageCount(candidates.size(), MembersMenu.PER_PAGE);
		page = GuildRules.clampPage(page, pages);
		int slot = 9;
		for (ServerPlayer candidate : GuildRules.page(candidates, page, MembersMenu.PER_PAGE)) {
			UUID id = candidate.getUUID();
			String name = candidate.getName().getString();
			List<Component> lore = new ArrayList<>();
			if (GuildManager.hasInvite(id, guild)) {
				lore.add(Lang.get("members.invite_menu.player.invited"));
			}
			lore.add(Lang.get("members.invite_menu.player.lore"));
			action(slot++, head(id, Lang.get("members.invite_menu.player.name", name), lore),
					() -> GuildActions.inviteDenial(player), () -> {
						ServerPlayer target = GuildyServer.player(id);
						if (target == null) {
							Msg.send(player, "guild.player.unknown", name);
							return false;
						}
						return GuildActions.invite(player, target);
					});
		}
		bottomBar(() -> new MembersMenu(player, guild, 0).show());
		pager(page, pages, target -> {
			page = target;
			render();
		});
	}
}
