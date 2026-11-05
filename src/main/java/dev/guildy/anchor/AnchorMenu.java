package dev.guildy.anchor;

import dev.guildy.command.GuildActions.Denial;
import dev.guildy.config.GuildyConfig;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildPermission;
import dev.guildy.gui.GuiUtil;
import dev.guildy.gui.GuildGui;
import dev.guildy.gui.GuildMenus;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Anchor flags of the guild (territory menu → "Drapeaux d'ancrage", {@code /guild ancrage}): what they do, the planted
 * ones with their state, a button to take a flag; clicking a planted anchor takes it down (with confirmation) and gives
 * the flag back.
 */
public final class AnchorMenu extends GuildGui {
	/** Two centred rows of five. */
	private static final int[] ANCHOR_SLOTS = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33};

	private AnchorMenu(ServerPlayer player, Guild guild) {
		super(player, 5, guild);
	}

	public static void open(ServerPlayer player, Guild guild) {
		new AnchorMenu(player, guild).show();
	}

	@Override
	protected Component title(@Nullable Guild guild) {
		return Lang.get("anchor.menu.title");
	}

	@Override
	protected void build(@Nullable Guild guild) {
		if (guild == null) {
			return;
		}
		GuildyConfig.Anchors config = GuildyServer.config().anchors;
		setSlot(4, icon(AnchorFlags.icon(), Lang.get("anchor.menu.info.name"), infoLore(guild, config)));
		int price = config.price;
		action(13, icon(AnchorFlags.icon(), Lang.get("anchor.menu.take.name"),
						GuiUtil.lines(price > 0 ? "anchor.menu.take.lore.paid" : "anchor.menu.take.lore", price, guild.treasury)).glow(),
				() -> AnchorFlags.takeDenial(player, guild), () -> AnchorFlags.take(player, guild));
		List<ChunkAnchor> anchors = guild.anchors.flags;
		boolean canRemove = guild.can(player.getUUID(), GuildPermission.CLAIM);
		for (int i = 0; i < anchors.size() && i < ANCHOR_SLOTS.length; i++) {
			ChunkAnchor anchor = anchors.get(i);
			GuiElementBuilder icon = anchorIcon(guild, anchor, canRemove);
			int anchorId = anchor.id;
			button(ANCHOR_SLOTS[i], icon, () -> canRemove ? null : new Denial("error.no_permission"), () -> confirmTakeDown(guild, anchorId));
		}
		if (anchors.isEmpty()) {
			setSlot(22, icon(Items.BARRIER, Lang.get("anchor.menu.none.name"), GuiUtil.lines("anchor.menu.none.lore")));
		}
		bottomBar(() -> GuildMenus.openTerritory(player));
	}

	private List<Component> infoLore(Guild guild, GuildyConfig.Anchors config) {
		List<Component> lore = new ArrayList<>(GuiUtil.lines("anchor.menu.info.lore"));
		int side = 2 * config.radius + 1;
		lore.add(Component.empty());
		lore.add(Lang.get("anchor.menu.info.count", guild.anchors.flags.size(), AnchorTickets.allowed(guild)));
		int next = AnchorRules.nextLevel(guild.level, config.baseCount, config.extraEveryLevels, config.maxCount, config.minGuildLevel);
		if (next > 0) {
			lore.add(Lang.get("anchor.menu.info.next", next));
		}
		lore.add(Lang.get("anchor.menu.info.area", side, side * side));
		lore.add(Lang.get(config.mobSpawning ? "anchor.menu.info.mobs.on" : "anchor.menu.info.mobs.off"));
		if (config.onlyWhenMemberOnline) {
			lore.add(Lang.get("anchor.menu.info.online_only"));
		}
		if (!config.enabled) {
			lore.add(Lang.get("anchor.menu.info.disabled"));
		}
		return lore;
	}

	private static GuiElementBuilder anchorIcon(Guild guild, ChunkAnchor anchor, boolean canRemove) {
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("anchor.menu.anchor.pos", anchor.pos.getX(), anchor.pos.getY(), anchor.pos.getZ()));
		if (!anchor.dimension.equals("minecraft:overworld")) {
			lore.add(Lang.get("anchor.menu.anchor.dimension", anchor.dimension));
		}
		if (!anchor.placedBy.isEmpty()) {
			lore.add(Lang.get("anchor.menu.anchor.by", anchor.placedBy));
		}
		AnchorTickets.State state = AnchorTickets.state(guild, anchor);
		if (state == AnchorTickets.State.ACTIVE) {
			lore.add(Lang.get("anchor.menu.anchor.active", AnchorTickets.chunksPerAnchor()));
		} else {
			lore.add(Lang.get("anchor.menu.anchor.inactive", Lang.raw(state.langKey())));
		}
		if (canRemove) {
			lore.add(Component.empty());
			lore.addAll(GuiUtil.lines("anchor.menu.anchor.click"));
		}
		GuiElementBuilder icon = icon(AnchorFlags.icon(), Lang.get("anchor.menu.anchor.name", anchor.id), lore);
		return state == AnchorTickets.State.ACTIVE ? icon.glow() : icon;
	}

	private void confirmTakeDown(Guild guild, int anchorId) {
		ChunkAnchor anchor = guild.anchors.get(anchorId);
		if (anchor == null) {
			show();
			return;
		}
		GuiUtil.confirm(player, Lang.get("anchor.menu.remove.title"),
				GuiUtil.lines("anchor.menu.remove.lore", anchor.pos.getX(), anchor.pos.getY(), anchor.pos.getZ()), () -> {
					Guild current = guild();
					if (current != null && AnchorFlags.takeDown(player, current, anchorId)) {
						GuiUtil.successSound(player);
					} else {
						GuiUtil.errorSound(player);
					}
					reopen();
				}, this::reopen);
	}

	private void reopen() {
		Guild guild = guild();
		if (guild != null) {
			open(player, guild);
		} else {
			GuildMenus.open(player);
		}
	}
}
