package dev.guildy.gui;

import dev.guildy.command.GuildActions;
import dev.guildy.core.Lang;
import dev.guildy.data.Guild;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Actions on one member: promote, demote, kick (confirmation), transfer leadership (confirmation).
 */
final class MemberMenu extends GuildGui {
	private final UUID target;
	private final int returnPage;

	MemberMenu(ServerPlayer player, Guild guild, UUID target, int returnPage) {
		super(player, 3, guild);
		this.target = target;
		this.returnPage = returnPage;
	}

	@Override
	protected Component title(@Nullable Guild guild) {
		return Lang.get("members.manage.title", guild == null ? "?" : GuildActions.memberName(guild, target));
	}

	@Override
	public boolean render() {
		Guild guild = guild();
		if (guild != null && !guild.isMember(target)) {
			// The member left or was kicked meanwhile.
			new MembersMenu(player, guild, returnPage).show();
			return false;
		}
		return super.render();
	}

	@Override
	protected void build(@Nullable Guild guild) {
		if (guild == null) {
			return;
		}
		String name = GuildActions.memberName(guild, target);
		setSlot(4, head(target, MembersMenu.memberTitle(guild, target), MembersMenu.memberLore(guild, target)));

		action(10, icon(Items.EMERALD, "members.action.promote.name", GuiUtil.lines("members.action.promote.lore", name)),
				() -> GuildActions.promoteDenial(player, target), () -> GuildActions.promote(player, target));
		action(12, icon(Items.REDSTONE, "members.action.demote.name", GuiUtil.lines("members.action.demote.lore", name)),
				() -> GuildActions.demoteDenial(player, target), () -> GuildActions.demote(player, target));
		button(14, icon(Items.IRON_DOOR, "members.action.kick.name", GuiUtil.lines("members.action.kick.lore", name)),
				() -> GuildActions.kickDenial(player, target),
				() -> GuildActions.confirmKick(player, target, () -> new MembersMenu(player, guild, returnPage).show(), this::reopen));
		button(16, icon(Items.GOLDEN_HELMET, "members.action.transfer.name", GuiUtil.lines("members.action.transfer.lore", name)),
				() -> GuildActions.transferDenial(player, target),
				() -> GuildActions.confirmTransfer(player, target, () -> new MembersMenu(player, guild, returnPage).show(), this::reopen));

		bottomBar(() -> new MembersMenu(player, guild, returnPage).show());
	}

	private void reopen() {
		Guild guild = guild();
		if (guild != null) {
			new MemberMenu(player, guild, target, returnPage).show();
		} else {
			GuildMenus.open(player);
		}
	}
}
