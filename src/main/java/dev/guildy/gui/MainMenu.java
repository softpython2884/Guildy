package dev.guildy.gui;

import dev.guildy.command.GuildActions;
import dev.guildy.command.GuildChat;
import dev.guildy.command.GuildRules;
import dev.guildy.config.GuildyConfig;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Hooks;
import dev.guildy.core.Lang;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildRole;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Main guild menu (6 rows): guild card, XP bar, feature buttons, leave/disband, help.
 */
final class MainMenu extends GuildGui {
	MainMenu(ServerPlayer player, Guild guild) {
		super(player, 6, guild);
	}

	@Override
	protected Component title(@Nullable Guild guild) {
		return Lang.get("menu.main.title", guild == null ? "" : guild.name);
	}

	@Override
	protected void build(@Nullable Guild guild) {
		if (guild == null) {
			return;
		}
		setSlot(4, infoCard(guild));
		xpBar(guild);

		GuiElementBuilder members = icon(Items.PLAYER_HEAD, "menu.main.members.name", GuiUtil.lines("menu.main.members.lore", guild.size()));
		members.setProfile(ResolvableProfile.createResolved(player.getGameProfile()));
		button(19, members, null, () -> new MembersMenu(player, guild, 0).show());
		button(21, icon(Items.MAP, "menu.main.territory.name", GuiUtil.lines("menu.main.territory.lore", guild.claimCount(), guild.maxChunks())),
				null, () -> new TerritoryMenu(player, guild).show());
		button(23, icon(Items.GOLD_BLOCK, "menu.main.treasury.name", GuiUtil.lines("menu.main.treasury.lore", guild.treasury)),
				null, () -> new TreasuryMenu(player, guild).show());
		button(25, icon(Items.LOOM, "menu.main.banner.name", GuiUtil.lines("menu.main.banner.lore")),
				null, () -> GuildMenus.openBanner(player));

		button(28, icon(Items.BRICKS, "menu.main.structures.name", GuiUtil.lines("menu.main.structures.lore")),
				null, () -> Hooks.openStructures.accept(player, guild));
		button(30, icon(Items.WRITABLE_BOOK, "menu.main.quests.name", GuiUtil.lines("menu.main.quests.lore")),
				null, () -> Hooks.openQuests.accept(player, guild));
		button(32, icon(Items.VILLAGER_SPAWN_EGG, "menu.main.villagers.name", GuiUtil.lines("menu.main.villagers.lore")),
				null, () -> Hooks.openVillagers.accept(player, guild));
		List<Component> waveLore = new ArrayList<>(GuiUtil.lines("menu.main.waves.lore"));
		List<Component> status = Hooks.waveStatusLore.apply(guild);
		if (status != null && !status.isEmpty()) {
			waveLore.add(Component.empty());
			waveLore.addAll(status);
		}
		GuiElementBuilder waves = icon(Items.ZOMBIE_HEAD, "menu.main.waves.name", waveLore);
		if (Hooks.isWaveActive.test(guild)) {
			waves.glow();
		}
		button(34, waves, null, () -> Hooks.openWaves.accept(player, guild));

		if (guild.role(player.getUUID()) == GuildRole.LEADER) {
			button(45, icon(Items.TNT, "menu.main.disband.name", GuiUtil.lines("menu.main.disband.lore")),
					() -> GuildActions.disbandDenial(player),
					() -> GuildActions.confirmDisband(player, () -> new MainMenu(player, guild).show()));
		} else {
			button(45, icon(Items.OAK_DOOR, "menu.main.leave.name", GuiUtil.lines("menu.main.leave.lore")),
					() -> GuildActions.leaveDenial(player),
					() -> GuildActions.confirmLeave(player, () -> {
					}, () -> new MainMenu(player, guild).show()));
		}
		setSlot(49, GuiUtil.close());
		button(53, icon(Items.BOOK, "menu.main.help.name", GuiUtil.lines("menu.main.help.lore")), null, () -> {
			close();
			GuildChat.help(player);
		});
	}

	private GuiElementBuilder infoCard(Guild guild) {
		GuildyConfig config = GuildyServer.config();
		List<Component> lore = new ArrayList<>();
		GuildRole role = guild.role(player.getUUID());
		if (role != null) {
			lore.add(Lang.get("menu.main.info.role", Lang.raw(role.langKey())));
		}
		lore.add(Lang.get("menu.main.info.level", guild.level));
		boolean max = guild.level >= config.maxLevel();
		if (max) {
			lore.add(Lang.get("menu.main.info.xp_max", guild.xp));
		} else {
			long next = config.xpForLevel(guild.level + 1);
			long[] progress = GuildRules.levelProgress(guild.xp, config.xpForLevel(guild.level), next, false);
			lore.add(Lang.get("menu.main.info.xp", guild.xp, next));
			lore.add(Lang.get("menu.main.info.bar", GuildRules.progressBar(progress[0], progress[1], 20, "&a", "&8", '|'),
					GuildRules.percent(progress[0], progress[1])));
		}
		lore.add(Lang.get("menu.main.info.members", guild.onlineMembers().size(), guild.size()));
		lore.add(Lang.get("menu.main.info.chunks", guild.claimCount(), guild.maxChunks()));
		lore.add(Lang.get("menu.main.info.treasury", guild.treasury));
		if (guild.heart != null) {
			lore.add(Lang.get("menu.main.info.heart", guild.heart.getX(), guild.heart.getY(), guild.heart.getZ()));
		} else {
			lore.add(Lang.get("menu.main.info.heart_none"));
		}
		lore.add(Lang.get("menu.main.info.night", guild.waves.night));
		lore.add(Lang.get("menu.main.info.record", guild.waves.victories, guild.waves.defeats));
		if (Hooks.isWaveActive.test(guild)) {
			lore.add(Component.empty());
			lore.add(Lang.get("menu.main.info.wave_active"));
		}
		return icon(guild.banner.createBanner(GuildyServer.registryAccess()), Lang.get("menu.main.info.name", guild.name), lore);
	}

	/** Row 2: XP progress as 9 glass panes. */
	private void xpBar(Guild guild) {
		GuildyConfig config = GuildyServer.config();
		boolean max = guild.level >= config.maxLevel();
		long next = config.xpForLevel(guild.level + 1);
		long[] progress = GuildRules.levelProgress(guild.xp, config.xpForLevel(guild.level), next, max);
		int filled = max ? 9 : GuildRules.filledSegments(progress[0], progress[1], 9);
		Component name = max ? Lang.get("menu.main.xpbar.max", guild.level)
				: Lang.get("menu.main.xpbar", guild.level + 1, guild.xp, next, GuildRules.percent(progress[0], progress[1]));
		for (int i = 0; i < 9; i++) {
			DyeColor color = max ? DyeColor.YELLOW : i < filled ? DyeColor.LIME : DyeColor.WHITE;
			setSlot(9 + i, GuiUtil.item(Items.STAINED_GLASS_PANE.pick(color), name, List.of()));
		}
	}
}
