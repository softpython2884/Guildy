package dev.guildy.gui;

import dev.guildy.command.GuildActions;
import dev.guildy.command.GuildChat;
import dev.guildy.command.GuildRules;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildMember;
import dev.guildy.data.GuildRole;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Member list (heads with role and online status). Clicking a member the viewer may manage opens {@link MemberMenu}.
 */
final class MembersMenu extends GuildGui {
	static final int PER_PAGE = 36;
	private int page;

	MembersMenu(ServerPlayer player, Guild guild, int page) {
		super(player, 6, guild);
		this.page = page;
	}

	@Override
	protected Component title(@Nullable Guild guild) {
		int max = GuildyServer.config().guilds.maxMembers;
		int size = guild == null ? 0 : guild.size();
		return max > 0 ? Lang.get("members.title", size, max) : Lang.get("members.title_unlimited", size);
	}

	/** Leader, officers, members; online first; then by name. */
	static List<UUID> sortedMembers(Guild guild) {
		List<UUID> ids = new ArrayList<>(guild.memberIds());
		ids.sort(Comparator.<UUID, Integer>comparing(id -> {
					GuildRole role = guild.role(id);
					return role == null ? 0 : -role.ordinal();
				})
				.thenComparing(id -> GuildyServer.player(id) == null)
				.thenComparing(id -> GuildActions.memberName(guild, id), String.CASE_INSENSITIVE_ORDER));
		return ids;
	}

	/** Name colored by role. */
	static Component memberTitle(Guild guild, UUID id) {
		GuildRole role = guild.role(id);
		String key = role == GuildRole.LEADER ? "members.head.name.leader" : role == GuildRole.OFFICER ? "members.head.name.officer" : "members.head.name.member";
		return Lang.get(key, GuildActions.memberName(guild, id));
	}

	/** Role, status and join date lines of a member head. */
	static List<Component> memberLore(Guild guild, UUID id) {
		List<Component> lore = new ArrayList<>();
		GuildRole role = guild.role(id);
		if (role != null) {
			lore.add(Lang.get("members.head.role", Lang.raw(role.langKey())));
		}
		lore.add(Lang.get(GuildyServer.player(id) != null ? "members.head.online" : "members.head.offline"));
		GuildMember member = guild.member(id);
		if (member != null && member.joinedAt > 0) {
			lore.add(Lang.get("members.head.joined", GuildChat.DATE.format(Instant.ofEpochMilli(member.joinedAt))));
		}
		return lore;
	}

	/** True when the viewer has at least one action available on this member. */
	static boolean canManage(ServerPlayer viewer, UUID target) {
		return GuildActions.kickDenial(viewer, target) == null || GuildActions.promoteDenial(viewer, target) == null
				|| GuildActions.demoteDenial(viewer, target) == null || GuildActions.transferDenial(viewer, target) == null;
	}

	@Override
	protected void build(@Nullable Guild guild) {
		if (guild == null) {
			return;
		}
		int max = GuildyServer.config().guilds.maxMembers;
		button(4, icon(Items.WRITABLE_BOOK, "members.invite.name", GuiUtil.lines("members.invite.lore", guild.size(), max > 0 ? max : "∞")),
				() -> GuildActions.inviteDenial(player), () -> new InviteMenu(player, guild, 0).show());

		List<UUID> ids = sortedMembers(guild);
		int pages = GuildRules.pageCount(ids.size(), PER_PAGE);
		page = GuildRules.clampPage(page, pages);
		int slot = 9;
		for (UUID id : GuildRules.page(ids, page, PER_PAGE)) {
			List<Component> lore = memberLore(guild, id);
			boolean self = id.equals(player.getUUID());
			boolean manageable = !self && canManage(player, id);
			if (self) {
				lore.add(Lang.get("members.head.you"));
			} else if (manageable) {
				lore.add(Component.empty());
				lore.add(Lang.get("members.head.manage"));
			}
			GuiElementBuilder head = head(id, memberTitle(guild, id), lore);
			if (manageable) {
				button(slot, head, null, () -> new MemberMenu(player, guild, id, page).show());
			} else {
				setSlot(slot, head);
			}
			slot++;
		}
		bottomBar(() -> new MainMenu(player, guild).show());
		pager(page, pages, target -> {
			page = target;
			render();
		});
	}
}
