package dev.guildy.villager;

import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.gui.GuiUtil;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * "Autorisations du fanion" of a field or forest zone: one toggle per {@link ZoneOption} (description, state, server
 * switch), the presets of field zones ("Entretenir un champ", "Créer des champs") and "Réglages par défaut". Anyone may
 * look; changing needs the right to manage the villagers. Villagers of the zone start their work over after a change.
 */
final class ZoneOptionsMenu {
	private static final int[] OPTION_SLOTS = {10, 11, 12, 13, 14, 19, 20, 21, 22, 23, 28, 29, 30, 31, 32};
	private static final int[] PRESET_SLOTS = {16, 25};
	private static final int DEFAULTS_SLOT = 34;

	private ZoneOptionsMenu() {
	}

	/** Button of the zone menu (only for zone types with toggles). */
	static GuiElementBuilder button(ServerPlayer player, Guild guild, WorkZone zone) {
		List<Component> lore = new ArrayList<>(GuiUtil.lines("villager.field.button.lore", ZonePermissions.enabledCount(zone),
				ZonePermissions.forType(zone.type).size()));
		ZonePermissions.Preset preset = ZonePermissions.matching(zone);
		lore.add(preset != null ? Lang.get("villager.field.button.preset", Lang.raw(preset.langKey())) : Lang.get("villager.field.button.custom"));
		lore.add(Component.empty());
		lore.add(Lang.get("villager.field.button.click"));
		int zoneId = zone.id;
		Item icon = zone.type == ZoneType.FARM ? Items.GOLDEN_HOE : Items.GOLDEN_AXE;
		return GuiUtil.item(icon, Lang.get("villager.field.button"), lore).setCallback((slot, type, action, g) -> {
			GuiUtil.clickSound(player);
			open(player, guild, zoneId);
		});
	}

	static void open(ServerPlayer player, Guild guild, int zoneId) {
		WorkZone zone = guild.villagers.zone(zoneId);
		if (zone == null || !ZonePermissions.supports(zone.type)) {
			Msg.send(player, "villager.zone.error.unknown");
			ZoneGui.openZones(player, guild);
			return;
		}
		VillagerConfig config = VillagerModule.config();
		SimpleGui gui = VillagerGui.chest(player, 5, Lang.get("villager.field.title", zone.name));
		gui.setSlot(4, info(zone));
		List<ZoneOption> options = ZonePermissions.forType(zone.type);
		for (int i = 0; i < options.size() && i < OPTION_SLOTS.length; i++) {
			ZoneOption option = options.get(i);
			gui.setSlot(OPTION_SLOTS[i], toggle(player, guild, zone, option, config));
		}
		List<ZonePermissions.Preset> presets = ZonePermissions.presets(zone.type);
		for (int i = 0; i < presets.size() && i < PRESET_SLOTS.length; i++) {
			gui.setSlot(PRESET_SLOTS[i], preset(player, guild, zone, presets.get(i)));
		}
		gui.setSlot(DEFAULTS_SLOT, preset(player, guild, zone, ZonePermissions.Preset.DEFAULTS));
		gui.setSlot(36, GuiUtil.back(() -> ZoneGui.openZone(player, guild, zone.id)));
		gui.setSlot(44, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	private static GuiElementBuilder info(WorkZone zone) {
		List<Component> lore = new ArrayList<>(GuiUtil.lines("villager.field.info.lore", zone.name));
		if (zone.type == ZoneType.FARM) {
			lore.addAll(GuiUtil.lines("villager.field.info.farm"));
			FieldState state = zone.field;
			lore.addAll(GuiUtil.lines("villager.field.info.state", state == null ? 0 : state.holes.size(), state == null ? 0 : state.trampled.size()));
		}
		return GuiUtil.item(Items.BOOK, Lang.get("villager.field.info"), lore);
	}

	private static GuiElementBuilder toggle(ServerPlayer player, Guild guild, WorkZone zone, ZoneOption option, VillagerConfig config) {
		boolean on = ZonePermissions.allows(zone, option);
		List<Component> lore = new ArrayList<>(GuiUtil.lines(option.descKey()));
		if (option == ZoneOption.IRRIGATE) {
			int cost = config.farmer.irrigation.waterBucketCost;
			lore.addAll(cost > 0 ? GuiUtil.lines("villager.field.irrigate.cost", cost) : GuiUtil.lines("villager.field.irrigate.free"));
		}
		lore.add(Component.empty());
		lore.add(Lang.get(on ? "villager.field.state.on" : "villager.field.state.off"));
		if (!FieldWorks.serverAllows(config, option)) {
			lore.add(Lang.get("villager.field.state.server_off"));
		}
		lore.add(Lang.get(on ? "villager.field.toggle.on" : "villager.field.toggle.off"));
		Component name = Lang.get(on ? "villager.field.option.name.on" : "villager.field.option.name.off", Lang.raw(option.langKey()));
		GuiElementBuilder button = GuiUtil.item(icon(option.icon()), name, lore);
		if (on) {
			button.glow();
		}
		return button.setCallback((slot, type, action, g) -> {
			if (!VillagerGui.checkPermission(player, guild) || !stillZone(player, guild, zone)) {
				return;
			}
			boolean now = ZonePermissions.toggle(zone, option);
			GuildManager.markDirty();
			Msg.send(player, now ? "villager.field.toggled.on" : "villager.field.toggled.off", zone.name, Lang.raw(option.langKey()));
			GuiUtil.clickSound(player);
			open(player, guild, zone.id);
		});
	}

	private static GuiElementBuilder preset(ServerPlayer player, Guild guild, WorkZone zone, ZonePermissions.Preset preset) {
		boolean current = ZonePermissions.matching(zone) == preset;
		List<Component> lore = new ArrayList<>(GuiUtil.lines(preset.descKey()));
		lore.add(Component.empty());
		lore.add(Lang.get(current ? "villager.field.preset.current" : "villager.field.preset.click"));
		GuiElementBuilder button = GuiUtil.item(icon(preset.icon()), Lang.get("villager.field.preset.name", Lang.raw(preset.langKey())), lore);
		if (current) {
			button.glow();
		}
		return button.setCallback((slot, type, action, g) -> {
			if (!VillagerGui.checkPermission(player, guild) || !stillZone(player, guild, zone)) {
				return;
			}
			ZonePermissions.apply(zone, preset);
			GuildManager.markDirty();
			Msg.send(player, "villager.field.preset.applied", zone.name, Lang.raw(preset.langKey()));
			GuiUtil.successSound(player);
			open(player, guild, zone.id);
		});
	}

	private static Item icon(String id) {
		Item item = ItemUtil.item(id);
		return item != null ? item : Items.BARRIER;
	}

	private static boolean stillZone(ServerPlayer player, Guild guild, WorkZone zone) {
		if (!guild.villagers.zones.contains(zone)) {
			Msg.send(player, "villager.zone.error.unknown");
			player.closeContainer();
			return false;
		}
		return true;
	}
}
