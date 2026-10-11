package dev.guildy.command;

import dev.guildy.config.GuildyConfig;
import dev.guildy.core.GoldUtil;
import dev.guildy.core.GuildyItems;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.BannerDesign;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildMember;
import dev.guildy.data.GuildPermission;
import dev.guildy.data.GuildRole;
import dev.guildy.gui.GuiUtil;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Guild management operations shared by the /guild subcommands and the menus.
 * <p>
 * Each operation re-validates everything at call time (membership, role, config limits), sends the feedback to the
 * actor, notifies the guild and returns true on success. {@code xxxDenial} methods expose the same rules without
 * acting, so menus can show why a button is unavailable.
 */
public final class GuildActions {
	private GuildActions() {
	}

	/** Why an action is refused: a lang key and its arguments. */
	public record Denial(String key, Object... args) {
		public void send(ServerPlayer player) {
			Msg.send(player, key, args);
		}

		public MutableComponent text() {
			return Lang.get(key, args);
		}
	}

	// ================================================================ helpers

	static GuildyConfig config() {
		return GuildyServer.config();
	}

	public static String name(ServerPlayer player) {
		return player.getName().getString();
	}

	/** Display name of a member: current name when online, else the last known name. */
	public static String memberName(Guild guild, UUID id) {
		ServerPlayer online = GuildyServer.player(id);
		if (online != null) {
			return name(online);
		}
		GuildMember member = guild.member(id);
		if (member != null && member.name != null && !member.name.isEmpty()) {
			return member.name;
		}
		return id.toString().substring(0, 8);
	}

	/** Name of any player (online name, else a short UUID). */
	static String nameOf(UUID id) {
		ServerPlayer online = GuildyServer.player(id);
		return online != null ? name(online) : id.toString().substring(0, 8);
	}

	/** Member whose current or last known name matches (case-insensitive). */
	public static @Nullable UUID findMember(Guild guild, String name) {
		for (UUID id : guild.memberIds()) {
			if (memberName(guild, id).equalsIgnoreCase(name)) {
				return id;
			}
		}
		return null;
	}

	/** Prefixed lang message to the online members, optionally skipping one player. */
	public static void notifyGuild(Guild guild, @Nullable UUID except, String key, Object... args) {
		Component message = Msg.prefixed(Lang.get(key, args));
		for (ServerPlayer member : guild.onlineMembers()) {
			if (!member.getUUID().equals(except)) {
				member.sendSystemMessage(message);
			}
		}
	}

	/** Same as {@link #notifyGuild}, restricted to members whose role grants {@code permission}. */
	public static void notifyPermitted(Guild guild, GuildPermission permission, @Nullable UUID except, String key, Object... args) {
		Component message = Msg.prefixed(Lang.get(key, args));
		for (ServerPlayer member : guild.onlineMembers()) {
			if (!member.getUUID().equals(except) && guild.can(member.getUUID(), permission)) {
				member.sendSystemMessage(message);
			}
		}
	}

	/** Multi-line lang text ('\n' separated): the first line gets the chat prefix. */
	public static void sendLines(ServerPlayer player, String key, Object... args) {
		String[] lines = Lang.raw(key, args).split("\n");
		for (int i = 0; i < lines.length; i++) {
			Component line = Component.literal(lines[i]);
			player.sendSystemMessage(i == 0 ? Msg.prefixed(line) : line);
		}
	}

	/** Clickable chat text running a command. */
	public static MutableComponent clickable(Component label, String command, Component hover) {
		return label.copy().withStyle(style -> style
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(hover)));
	}

	private static @Nullable Guild requireGuild(ServerPlayer player) {
		Guild guild = GuildManager.of(player);
		if (guild == null) {
			Msg.send(player, "error.no_guild");
		}
		return guild;
	}

	private static boolean refuse(ServerPlayer player, @Nullable Denial denial) {
		if (denial != null) {
			denial.send(player);
			return true;
		}
		return false;
	}

	/**
	 * Yes/No dialog bound to the guild the actor belongs to now: if they changed guild before confirming,
	 * the action is cancelled.
	 */
	private static void confirm(ServerPlayer player, Guild guild, Component title, List<Component> question, Runnable action, Runnable onCancel) {
		GuiUtil.confirm(player, title, question, () -> {
			if (GuildManager.of(player) != guild) {
				Msg.send(player, "guild.cancelled");
				return;
			}
			action.run();
		}, onCancel);
	}

	// ================================================================ creation

	public static boolean create(ServerPlayer player, String rawName) {
		if (GuildManager.of(player) != null) {
			Msg.send(player, "error.already_in_guild");
			return false;
		}
		String name = GuildRules.normalizeName(rawName);
		String error = GuildManager.validateName(name);
		if (error != null) {
			Msg.send(player, error);
			return false;
		}
		Guild guild = GuildManager.create(name, player);
		ItemUtil.giveOrDrop(player, GuildyItems.foundingFlag(guild));
		Msg.title(player, Lang.get("guild.created.title", guild.name), Lang.get("guild.created.subtitle"));
		Msg.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
		sendLines(player, "guild.created.explain", guild.name);
		String broadcast = Lang.raw("guild.created.broadcast", guild.name, name(player));
		if (!Lang.strip(broadcast).isBlank()) {
			Component message = Msg.prefixed(Component.literal(broadcast));
			for (ServerPlayer other : GuildyServer.players()) {
				if (other != player) {
					other.sendSystemMessage(message);
				}
			}
		}
		return true;
	}

	// ================================================================ invitations

	/** Whether the actor may invite someone right now (role + member limit). */
	public static @Nullable Denial inviteDenial(ServerPlayer actor) {
		Guild guild = GuildManager.of(actor);
		if (guild == null) {
			return new Denial("error.no_guild");
		}
		if (!guild.can(actor.getUUID(), GuildPermission.INVITE)) {
			return new Denial("error.no_permission");
		}
		int max = config().guilds.maxMembers;
		if (GuildRules.isFull(guild.size(), max)) {
			return new Denial("guild.full", max);
		}
		return null;
	}

	public static boolean invite(ServerPlayer actor, ServerPlayer target) {
		if (refuse(actor, inviteDenial(actor))) {
			return false;
		}
		Guild guild = GuildManager.of(actor);
		if (target.getUUID().equals(actor.getUUID())) {
			Msg.send(actor, "guild.invite.self");
			return false;
		}
		Guild other = GuildManager.of(target);
		if (other == guild) {
			Msg.send(actor, "guild.invite.already_member", name(target));
			return false;
		}
		if (other != null) {
			Msg.send(actor, "guild.invite.target_in_guild", name(target));
			return false;
		}
		GuildManager.invite(target.getUUID(), guild);
		int seconds = config().guilds.inviteExpireSeconds;
		Msg.send(actor, "guild.invite.sent", name(target), seconds);
		notifyGuild(guild, actor.getUUID(), "guild.invite.notify", name(actor), name(target));
		Msg.send(target, "guild.invite.received", guild.name, name(actor), seconds);
		target.sendSystemMessage(Msg.prefixed(inviteButtons(guild)));
		Msg.sound(target, SoundEvents.NOTE_BLOCK_PLING, 0.8f, 1.2f);
		return true;
	}

	/** "[Rejoindre] [Refuser]" chat buttons for an invitation. */
	public static Component inviteButtons(Guild guild) {
		return Component.empty()
				.append(Msg.button("guild.invite.accept", "/guild join " + guild.name, "guild.invite.accept.hover"))
				.append(Component.literal("  "))
				.append(Msg.button("guild.invite.deny", "/guild deny " + guild.name, "guild.invite.deny.hover"));
	}

	private static void sendInviteChoices(ServerPlayer player, List<Guild> invites, String subcommand) {
		Msg.send(player, "guild.join.choose");
		MutableComponent line = Component.empty();
		for (Guild guild : invites) {
			line.append(clickable(Lang.get("guild.join.option", guild.name), "/guild " + subcommand + " " + guild.name,
					Lang.get(subcommand.equals("deny") ? "guild.invite.deny.hover" : "guild.invite.accept.hover")));
			line.append(Component.literal(" "));
		}
		player.sendSystemMessage(Msg.prefixed(line));
	}

	/** Accepts an invitation (the only one when {@code requested} is null). */
	public static boolean join(ServerPlayer player, @Nullable Guild requested) {
		if (GuildManager.of(player) != null) {
			Msg.send(player, "error.already_in_guild");
			return false;
		}
		List<Guild> invites = GuildManager.invitesOf(player.getUUID());
		Guild guild = requested;
		if (guild == null) {
			if (invites.isEmpty()) {
				Msg.send(player, "guild.join.no_invite");
				return false;
			}
			if (invites.size() > 1) {
				sendInviteChoices(player, invites, "join");
				return false;
			}
			guild = invites.getFirst();
		} else if (!invites.contains(guild)) {
			Msg.send(player, "guild.join.not_invited", guild.name);
			return false;
		}
		int max = config().guilds.maxMembers;
		if (GuildRules.isFull(guild.size(), max)) {
			Msg.send(player, "guild.full", max);
			return false;
		}
		GuildManager.addMember(guild, player.getUUID(), name(player));
		Msg.send(player, "guild.join.success", guild.name);
		Msg.sound(player, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.2f);
		notifyGuild(guild, player.getUUID(), "guild.join.notify", name(player));
		return true;
	}

	/** Declines an invitation (the only one when {@code requested} is null). */
	public static boolean deny(ServerPlayer player, @Nullable Guild requested) {
		List<Guild> invites = GuildManager.invitesOf(player.getUUID());
		Guild guild = requested;
		if (guild == null) {
			if (invites.isEmpty()) {
				Msg.send(player, "guild.join.no_invite");
				return false;
			}
			if (invites.size() > 1) {
				sendInviteChoices(player, invites, "deny");
				return false;
			}
			guild = invites.getFirst();
		} else if (!invites.contains(guild)) {
			Msg.send(player, "guild.join.not_invited", guild.name);
			return false;
		}
		GuildManager.removeInvite(player.getUUID(), guild);
		Msg.send(player, "guild.deny.success", guild.name);
		notifyPermitted(guild, GuildPermission.INVITE, null, "guild.deny.notify", name(player));
		return true;
	}

	// ================================================================ leaving

	/** A leader cannot leave while other members remain (transfer first); a lone leader leaving disbands. */
	public static @Nullable Denial leaveDenial(ServerPlayer player) {
		Guild guild = GuildManager.of(player);
		if (guild == null) {
			return new Denial("error.no_guild");
		}
		if (guild.role(player.getUUID()) == GuildRole.LEADER && guild.size() > 1) {
			return new Denial("guild.leave.leader");
		}
		return null;
	}

	/** True when leaving would disband the guild (lone leader). */
	public static boolean leaveDisbands(ServerPlayer player) {
		Guild guild = GuildManager.of(player);
		return guild != null && guild.role(player.getUUID()) == GuildRole.LEADER;
	}

	/** Leaves the guild without confirmation (a lone leader disbands it). */
	public static boolean leave(ServerPlayer player) {
		if (refuse(player, leaveDenial(player))) {
			return false;
		}
		Guild guild = GuildManager.of(player);
		if (guild.role(player.getUUID()) == GuildRole.LEADER) {
			disbandNow(guild, "guild.disband.notify", guild.name, name(player));
			return true;
		}
		GuildManager.removeMember(guild, player.getUUID());
		Msg.send(player, "guild.leave.success", guild.name);
		notifyGuild(guild, null, "guild.leave.notify", name(player));
		return true;
	}

	public static void confirmLeave(ServerPlayer player, Runnable onDone, Runnable onCancel) {
		if (refuse(player, leaveDenial(player))) {
			return;
		}
		Guild guild = GuildManager.of(player);
		boolean alone = leaveDisbands(player);
		Component title = Lang.get(alone ? "guild.leave.alone.title" : "guild.leave.confirm.title");
		List<Component> question = GuiUtil.lines(alone ? "guild.leave.alone.lore" : "guild.leave.confirm.lore", guild.name, guild.treasury);
		confirm(player, guild, title, question, () -> {
			leave(player);
			onDone.run();
		}, onCancel);
	}

	// ================================================================ members

	public static @Nullable Denial kickDenial(ServerPlayer actor, UUID target) {
		Guild guild = GuildManager.of(actor);
		if (guild == null) {
			return new Denial("error.no_guild");
		}
		if (!guild.can(actor.getUUID(), GuildPermission.KICK)) {
			return new Denial("error.no_permission");
		}
		if (actor.getUUID().equals(target)) {
			return new Denial("guild.kick.self");
		}
		GuildRole targetRole = guild.role(target);
		if (targetRole == null) {
			return new Denial("guild.member.unknown", nameOf(target));
		}
		GuildRole actorRole = guild.role(actor.getUUID());
		if (actorRole == null || !GuildRules.canKick(actorRole, targetRole)) {
			return new Denial("guild.kick.higher");
		}
		return null;
	}

	public static boolean kick(ServerPlayer actor, UUID target) {
		if (refuse(actor, kickDenial(actor, target))) {
			return false;
		}
		Guild guild = GuildManager.of(actor);
		String targetName = memberName(guild, target);
		GuildManager.removeMember(guild, target);
		notifyGuild(guild, null, "guild.kick.notify", targetName, name(actor));
		ServerPlayer online = GuildyServer.player(target);
		if (online != null) {
			Msg.send(online, "guild.kick.target", guild.name, name(actor));
			Msg.sound(online, SoundEvents.VILLAGER_NO, 0.8f, 1.0f);
		}
		return true;
	}

	public static void confirmKick(ServerPlayer actor, UUID target, Runnable onDone, Runnable onCancel) {
		if (refuse(actor, kickDenial(actor, target))) {
			return;
		}
		Guild guild = GuildManager.of(actor);
		String targetName = memberName(guild, target);
		confirm(actor, guild, Lang.get("guild.kick.confirm.title", targetName), GuiUtil.lines("guild.kick.confirm.lore", targetName), () -> {
			kick(actor, target);
			onDone.run();
		}, onCancel);
	}

	private static @Nullable Denial leaderActionDenial(ServerPlayer actor, UUID target, String selfKey) {
		Guild guild = GuildManager.of(actor);
		if (guild == null) {
			return new Denial("error.no_guild");
		}
		if (guild.role(actor.getUUID()) != GuildRole.LEADER) {
			return new Denial("guild.leader_only");
		}
		if (actor.getUUID().equals(target)) {
			return new Denial(selfKey);
		}
		if (!guild.isMember(target)) {
			return new Denial("guild.member.unknown", nameOf(target));
		}
		return null;
	}

	public static @Nullable Denial promoteDenial(ServerPlayer actor, UUID target) {
		Denial denial = leaderActionDenial(actor, target, "guild.role.self");
		if (denial != null) {
			return denial;
		}
		Guild guild = GuildManager.of(actor);
		GuildRole role = guild.role(target);
		if (role == null || GuildRules.promoted(role) == null) {
			return new Denial("guild.promote.max", memberName(guild, target));
		}
		return null;
	}

	public static @Nullable Denial demoteDenial(ServerPlayer actor, UUID target) {
		Denial denial = leaderActionDenial(actor, target, "guild.role.self");
		if (denial != null) {
			return denial;
		}
		Guild guild = GuildManager.of(actor);
		GuildRole role = guild.role(target);
		if (role == null || GuildRules.demoted(role) == null) {
			return new Denial("guild.demote.min", memberName(guild, target));
		}
		return null;
	}

	public static boolean promote(ServerPlayer actor, UUID target) {
		if (refuse(actor, promoteDenial(actor, target))) {
			return false;
		}
		Guild guild = GuildManager.of(actor);
		GuildRole role = GuildRules.promoted(guild.role(target));
		GuildManager.setRole(guild, target, role);
		notifyGuild(guild, null, "guild.promote.notify", memberName(guild, target), Lang.raw(role.langKey()), name(actor));
		return true;
	}

	public static boolean demote(ServerPlayer actor, UUID target) {
		if (refuse(actor, demoteDenial(actor, target))) {
			return false;
		}
		Guild guild = GuildManager.of(actor);
		GuildRole role = GuildRules.demoted(guild.role(target));
		GuildManager.setRole(guild, target, role);
		notifyGuild(guild, null, "guild.demote.notify", memberName(guild, target), Lang.raw(role.langKey()), name(actor));
		return true;
	}

	public static @Nullable Denial transferDenial(ServerPlayer actor, UUID target) {
		return leaderActionDenial(actor, target, "guild.transfer.self");
	}

	/** Makes {@code target} the leader; the old leader becomes an officer. */
	public static boolean transfer(ServerPlayer actor, UUID target) {
		if (refuse(actor, transferDenial(actor, target))) {
			return false;
		}
		Guild guild = GuildManager.of(actor);
		GuildManager.setRole(guild, target, GuildRole.LEADER);
		GuildManager.setRole(guild, actor.getUUID(), GuildRole.OFFICER);
		notifyGuild(guild, null, "guild.transfer.notify", name(actor), memberName(guild, target));
		Msg.guildSound(guild, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.2f);
		return true;
	}

	public static void confirmTransfer(ServerPlayer actor, UUID target, Runnable onDone, Runnable onCancel) {
		if (refuse(actor, transferDenial(actor, target))) {
			return;
		}
		Guild guild = GuildManager.of(actor);
		confirm(actor, guild, Lang.get("guild.transfer.confirm.title"),
				GuiUtil.lines("guild.transfer.confirm.lore", memberName(guild, target)), () -> {
					transfer(actor, target);
					onDone.run();
				}, onCancel);
	}

	// ================================================================ disband

	public static @Nullable Denial disbandDenial(ServerPlayer actor) {
		Guild guild = GuildManager.of(actor);
		if (guild == null) {
			return new Denial("error.no_guild");
		}
		if (guild.role(actor.getUUID()) != GuildRole.LEADER) {
			return new Denial("guild.leader_only");
		}
		return null;
	}

	public static boolean disband(ServerPlayer actor) {
		if (refuse(actor, disbandDenial(actor))) {
			return false;
		}
		Guild guild = GuildManager.of(actor);
		disbandNow(guild, "guild.disband.notify", guild.name, name(actor));
		return true;
	}

	public static void confirmDisband(ServerPlayer actor, Runnable onCancel) {
		if (refuse(actor, disbandDenial(actor))) {
			return;
		}
		Guild guild = GuildManager.of(actor);
		confirm(actor, guild, Lang.get("guild.disband.confirm.title"),
				GuiUtil.lines("guild.disband.confirm.lore", guild.name, guild.treasury), () -> disband(actor), onCancel);
	}

	/** Notifies the online members, then disbands (fires DISBANDED for module cleanup). */
	public static void disbandNow(Guild guild, String notifyKey, Object... args) {
		notifyGuild(guild, null, notifyKey, args);
		Msg.guildSound(guild, SoundEvents.BEACON_DEACTIVATE, 1.0f, 0.8f);
		GuildManager.disband(guild);
	}

	// ================================================================ treasury

	/** Moves gold from the inventory to the treasury; {@code amount <= 0} deposits all of it. */
	public static boolean deposit(ServerPlayer player, int amount) {
		Guild guild = requireGuild(player);
		if (guild == null) {
			return false;
		}
		int taken;
		if (amount <= 0) {
			taken = GoldUtil.takeAll(player, config().economy.depositNuggets);
			if (taken <= 0) {
				Msg.send(player, "treasury.deposit.nothing");
				return false;
			}
		} else {
			if (!GoldUtil.take(player, amount)) {
				Msg.send(player, "error.not_enough_gold", amount);
				return false;
			}
			taken = amount;
		}
		GuildManager.addTreasury(guild, taken);
		Msg.send(player, "treasury.deposit.success", taken, guild.treasury);
		Msg.sound(player, SoundEvents.ARMOR_EQUIP_GOLD, 0.8f, 1.0f);
		return true;
	}

	public static @Nullable Denial withdrawDenial(ServerPlayer player, long amount) {
		Guild guild = GuildManager.of(player);
		if (guild == null) {
			return new Denial("error.no_guild");
		}
		if (!guild.can(player.getUUID(), GuildPermission.TREASURY)) {
			return new Denial("error.no_permission");
		}
		if (guild.treasury < amount) {
			return new Denial("error.not_enough_treasury", amount);
		}
		return null;
	}

	/** Treasury → inventory (TREASURY permission). Takes from the treasury first, refunds if giving fails. */
	public static boolean withdraw(ServerPlayer player, int amount) {
		if (amount <= 0 || refuse(player, withdrawDenial(player, amount))) {
			return false;
		}
		Guild guild = GuildManager.of(player);
		if (!GuildManager.takeTreasury(guild, amount)) {
			Msg.send(player, "error.not_enough_treasury", amount);
			return false;
		}
		try {
			GoldUtil.give(player, amount);
		} catch (RuntimeException e) {
			GuildManager.addTreasury(guild, amount);
			throw e;
		}
		Msg.send(player, "treasury.withdraw.success", amount, guild.treasury);
		Msg.sound(player, SoundEvents.ARMOR_EQUIP_GOLD, 0.8f, 0.8f);
		notifyGuild(guild, player.getUUID(), "treasury.withdraw.notify", name(player), amount);
		return true;
	}

	// ================================================================ banner

	public static @Nullable Denial saveBannerDenial(ServerPlayer player) {
		Guild guild = GuildManager.of(player);
		if (guild == null) {
			return new Denial("error.no_guild");
		}
		if (!guild.can(player.getUUID(), GuildPermission.BANNER)) {
			return new Denial("error.no_permission");
		}
		return null;
	}

	public static boolean saveBanner(ServerPlayer player, BannerDesign design) {
		if (refuse(player, saveBannerDenial(player))) {
			return false;
		}
		if (design.layers.size() > BannerDesign.MAX_LAYERS) {
			Msg.send(player, "banner.max_layers", BannerDesign.MAX_LAYERS);
			return false;
		}
		Guild guild = GuildManager.of(player);
		guild.banner = design.copy();
		GuildManager.markDirty();
		Msg.send(player, "banner.saved");
		Msg.sound(player, SoundEvents.UI_LOOM_TAKE_RESULT, 1.0f, 1.0f);
		notifyGuild(guild, player.getUUID(), "banner.saved.notify", name(player));
		return true;
	}

	public static @Nullable Denial decorativeBannerDenial(ServerPlayer player) {
		Guild guild = GuildManager.of(player);
		if (guild == null) {
			return new Denial("error.no_guild");
		}
		if (!guild.can(player.getUUID(), GuildPermission.TREASURY)) {
			return new Denial("error.no_permission");
		}
		int price = config().economy.decorativeBannerPrice;
		if (guild.treasury < price) {
			return new Denial("error.not_enough_treasury", price);
		}
		return null;
	}

	/** Buys a decorative banner (saved guild design) with treasury gold. */
	public static boolean buyDecorativeBanner(ServerPlayer player) {
		if (refuse(player, decorativeBannerDenial(player))) {
			return false;
		}
		Guild guild = GuildManager.of(player);
		int price = config().economy.decorativeBannerPrice;
		if (!GuildManager.takeTreasury(guild, price)) {
			Msg.send(player, "error.not_enough_treasury", price);
			return false;
		}
		try {
			ItemUtil.giveOrDrop(player, GuildyItems.decorativeBanner(guild));
		} catch (RuntimeException e) {
			GuildManager.addTreasury(guild, price);
			throw e;
		}
		Msg.send(player, "banner.decorative.bought", price);
		Msg.sound(player, SoundEvents.UI_LOOM_TAKE_RESULT, 1.0f, 1.2f);
		return true;
	}
}
