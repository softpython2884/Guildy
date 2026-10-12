package dev.guildy.villager;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.gui.GuiUtil;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Exploitation" of a real mine: the staircase and its width, every level (open, finished, not reached yet, progress,
 * ores and wanted items found, miners working there), the miners of the zone with their status, the "objectifs
 * prioritaires" of the zone and of its miners, and the reset.
 */
final class MineGui {
	private static final int LEVELS_PER_PAGE = 18;
	private static final int FIRST_MINER_SLOT = 27;
	private static final int MINER_SLOTS = 18;

	private MineGui() {
	}

	static void open(ServerPlayer player, Guild guild, int zoneId, int page) {
		WorkZone zone = guild.villagers.zone(zoneId);
		if (zone == null || zone.type != ZoneType.MINE) {
			Msg.send(player, "villager.zone.error.unknown");
			ZoneGui.openZones(player, guild);
			return;
		}
		if (!zone.isRealMine()) {
			ZoneGui.openZone(player, guild, zone.id);
			return;
		}
		ServerLevel level = GuildyServer.claimLevel();
		long now = Scheduler.ticks();
		Mines.View view = Mines.view(level, guild, zone);
		SimpleGui gui = VillagerGui.chest(player, 6, Lang.get("villager.gui.mine.view.title", zone.name));
		gui.setSlot(4, summary(level, guild, zone, view, now));
		gui.setSlot(6, wishlist(guild, zone));
		Map<UUID, Integer> active = Mines.activeMiners(guild, zone, now);

		List<Integer> levels = view.levels();
		int pages = Math.max(1, GuiUtil.pages(levels.size(), LEVELS_PER_PAGE));
		int shown = Math.max(0, Math.min(page, pages - 1));
		int target = level == null ? Integer.MIN_VALUE : Mines.targetLevel(level, zone, VillagerModule.config().realMining);
		if (levels.isEmpty()) {
			gui.setSlot(13, GuiUtil.item(Items.GRAVEL, Lang.get("villager.gui.mine.view.no_level"), List.of()));
		}
		for (int i = 0; i < LEVELS_PER_PAGE; i++) {
			int index = shown * LEVELS_PER_PAGE + i;
			if (index >= levels.size()) {
				break;
			}
			gui.setSlot(9 + i, levelIcon(guild, view, levels.get(index), target, active));
		}

		List<VillagerRecord> miners = new ArrayList<>();
		for (VillagerRecord record : guild.villagers.members.values()) {
			if (Zones.assignedZone(guild, record) == zone || active.containsKey(record.uuid)) {
				miners.add(record);
			}
		}
		for (int i = 0; i < miners.size() && i < MINER_SLOTS; i++) {
			boolean overflow = i == MINER_SLOTS - 1 && miners.size() > MINER_SLOTS;
			gui.setSlot(FIRST_MINER_SLOT + i, overflow ? others(level, miners.subList(i, miners.size()))
					: minerIcon(level, miners.get(i), active));
		}
		if (miners.isEmpty()) {
			gui.setSlot(31, GuiUtil.item(Items.VILLAGER_SPAWN_EGG, Lang.get("villager.gui.mine.view.no_miner"), List.of()));
		}

		gui.setSlot(45, GuiUtil.back(() -> ZoneGui.openZone(player, guild, zone.id)));
		if (shown > 0) {
			gui.setSlot(47, GuiUtil.item(Items.ARROW, Lang.get("villager.gui.mine.view.page", shown, pages), List.of())
					.setCallback((s, type, action, g) -> {
						GuiUtil.clickSound(player);
						open(player, guild, zone.id, shown - 1);
					}));
		}
		gui.setSlot(49, GuiUtil.item(Items.TNT, Lang.get("villager.gui.mine.view.reset"), GuiUtil.lines("villager.gui.mine.reset.lore"))
				.setCallback((s, type, action, g) -> {
					if (!VillagerGui.checkPermission(player, guild) || !guild.villagers.zones.contains(zone)) {
						return;
					}
					GuiUtil.confirm(player, Lang.get("villager.gui.mine.reset.confirm", zone.name), GuiUtil.lines("villager.gui.mine.reset.lore"),
							() -> {
								if (VillagerGui.checkPermission(player, guild) && guild.villagers.zones.contains(zone)) {
									Zones.resetMine(guild, zone);
									Msg.guild(guild, "villager.mine.reset", player.getName().getString(), zone.name);
								}
								ZoneGui.openZone(player, guild, zone.id);
							},
							() -> open(player, guild, zone.id, shown));
				}));
		if (shown + 1 < pages) {
			gui.setSlot(51, GuiUtil.item(Items.ARROW, Lang.get("villager.gui.mine.view.page", shown + 2, pages), List.of())
					.setCallback((s, type, action, g) -> {
						GuiUtil.clickSound(player);
						open(player, guild, zone.id, shown + 1);
					}));
		}
		gui.setSlot(53, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	private static GuiElementBuilder summary(@Nullable ServerLevel level, Guild guild, WorkZone zone, Mines.View view, long now) {
		List<Component> lore = new ArrayList<>(Mines.describe(level, guild, zone, now));
		MineLayout layout = view.layout();
		MineState state = zone.mine();
		if (layout != null) {
			lore.add(Component.empty());
			if (state.stairsBlockedAt >= 0) {
				lore.add(Lang.get("villager.gui.mine.view.stairs_blocked", zone.pos.getY() - state.stairsBlockedAt));
			} else {
				lore.add(Lang.get("villager.gui.mine.view.stairs", Math.min(state.stairsDone, view.stairsNeeded()), view.stairsNeeded(),
						view.levels().isEmpty() ? zone.pos.getY() : view.levels().getLast()));
			}
			lore.add(Lang.get("villager.gui.mine.view.width", MineLayout.wide(layout.width()) ? 2 : 1));
			if (MineLayout.wide(layout.width())) {
				int dug = Math.min(state.stairsDone, layout.stairs().size());
				int widened = 0;
				for (int k = 0; k < dug; k++) {
					if (state.widened(k)) {
						widened++;
					}
				}
				lore.add(Lang.get("villager.gui.mine.view.widened", widened, dug));
			}
		}
		return GuiUtil.item(Items.IRON_PICKAXE, Lang.get("villager.gui.mine.view.summary", zone.name), lore);
	}

	private static GuiElementBuilder levelIcon(Guild guild, Mines.View view, int y, int target, Map<UUID, Integer> active) {
		MineLayout layout = view.layout();
		MineState state = view.state();
		MineState.Level progress = state.level(y);
		boolean finished = progress != null && progress.finished;
		boolean reached = layout != null && MineJobs.reached(layout, state, y);
		List<Component> lore = new ArrayList<>();
		if (y == target) {
			lore.add(Lang.get("villager.gui.mine.view.level.target"));
		}
		lore.add(Lang.get(finished ? "villager.gui.mine.view.level.finished" : reached ? "villager.gui.mine.view.level.open"
				: "villager.gui.mine.view.level.closed"));
		MineLayout.Level galleries = layout == null ? null : layout.level(y);
		if (galleries != null) {
			int total = galleries.segments().size();
			int done = progress == null ? 0 : Math.min(total, progress.done.size());
			lore.add(Lang.get("villager.gui.mine.view.level.progress", done, total));
			if (progress != null && !progress.abandoned.isEmpty()) {
				lore.add(Lang.get("villager.gui.mine.view.level.abandoned", progress.abandoned.size()));
			}
		}
		lore.add(Lang.get("villager.gui.mine.view.level.found", progress == null ? 0 : progress.ores, progress == null ? 0 : progress.wanted));
		List<String> names = new ArrayList<>();
		for (Map.Entry<UUID, Integer> entry : active.entrySet()) {
			VillagerRecord record = guild.villagers.members.get(entry.getKey());
			if (record != null && entry.getValue() == y) {
				names.add(record.name);
			}
		}
		if (!names.isEmpty()) {
			lore.add(Lang.get("villager.gui.mine.view.level.miners", String.join(", ", names)));
		}
		Item icon = finished ? Items.MOSSY_COBBLESTONE : !reached ? Items.GRAVEL : y < 0 ? Items.DEEPSLATE : Items.STONE;
		GuiElementBuilder builder = GuiUtil.item(icon, Lang.get("villager.gui.mine.view.level", y), lore);
		if (!names.isEmpty()) {
			builder.glow();
		}
		return builder;
	}

	private static GuiElementBuilder minerIcon(@Nullable ServerLevel level, VillagerRecord record, Map<UUID, Integer> active) {
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("villager.gui.lore.status", VillagerGui.statusText(record, level == null ? null : VillagerManager.entity(level, record.uuid))));
		Integer at = active.get(record.uuid);
		if (at == null) {
			lore.add(Lang.get("villager.gui.mine.view.miner.away"));
		} else if (at == MineLayout.STAIRS_LEVEL) {
			lore.add(Lang.get("villager.gui.mine.view.miner.stairs"));
		} else {
			lore.add(Lang.get("villager.gui.mine.view.miner.level", at));
		}
		List<String> wishes = names(record.wishlist);
		if (!wishes.isEmpty()) {
			lore.add(Lang.get("villager.gui.mine.view.miner.wishlist", String.join(", ", wishes)));
		}
		return GuiUtil.item(record.task.icon(), Lang.get("villager.gui.mine.view.miner", record.name), lore);
	}

	/** The miners that do not fit in the view, with their status. */
	private static GuiElementBuilder others(@Nullable ServerLevel level, List<VillagerRecord> records) {
		List<Component> lore = new ArrayList<>();
		for (VillagerRecord record : records) {
			lore.add(Lang.get("villager.gui.mine.view.other", record.name,
					VillagerGui.statusText(record, level == null ? null : VillagerManager.entity(level, record.uuid))));
		}
		return GuiUtil.item(Items.VILLAGER_SPAWN_EGG, Lang.get("villager.gui.mine.view.others", records.size()), lore);
	}

	private static GuiElementBuilder wishlist(Guild guild, WorkZone zone) {
		List<Component> lore = new ArrayList<>(GuiUtil.lines("villager.gui.mine.view.wishlist.lore"));
		lore.add(Component.empty());
		boolean any = false;
		List<String> zoneItems = names(zone.wishlist);
		if (!zoneItems.isEmpty()) {
			lore.add(Lang.get("villager.gui.mine.view.wishlist.zone", String.join(", ", zoneItems)));
			any = true;
		}
		for (VillagerRecord record : guild.villagers.members.values()) {
			if (Zones.assignedZone(guild, record) != zone) {
				continue;
			}
			List<String> items = names(record.wishlist);
			if (!items.isEmpty()) {
				lore.add(Lang.get("villager.gui.mine.view.wishlist.villager", record.name, String.join(", ", items)));
				any = true;
			}
		}
		if (!any) {
			lore.add(Lang.get("villager.gui.mine.view.wishlist.none"));
		}
		return GuiUtil.item(Items.WRITABLE_BOOK, Lang.get("villager.gui.mine.view.wishlist"), lore);
	}

	private static List<String> names(List<String> ids) {
		List<String> names = new ArrayList<>(ids.size());
		for (String id : ids) {
			Item item = ItemUtil.item(id);
			if (item != null) {
				names.add(ItemUtil.name(item).getString());
			}
		}
		return names;
	}
}
