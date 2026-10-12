package dev.guildy.villager;

import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.gui.GuiUtil;
import dev.guildy.structure.TextUtil;
import dev.guildy.upgrade.Upgrades;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Expedition destinations in the villager menus: the "Destination" button of an explorer / Nether villager (in place of
 * the work zone button, which expeditions do not use), the destination picker (duration, risk, typical and rare loot,
 * wishlist favourites, required level; locked destinations greyed with the reason) and the line of the villager list.
 */
final class ExpeditionMenu {
	private static final int PER_PAGE = 45;
	private static final int TYPICAL_LINES = 4;
	private static final int RARE_ITEMS = 3;

	private ExpeditionMenu() {
	}

	/** "Destination : X" line of a villager icon (trip in progress, or the next one), null for other tasks. */
	static @Nullable Component listLine(Guild guild, VillagerRecord record) {
		if (record.isAway()) {
			return Lang.get("villager.gui.lore.destination", Expeditions.name(Expeditions.awayDestination(record)));
		}
		if (!record.task.isExpedition()) {
			return null;
		}
		return Lang.get("villager.gui.lore.destination", Expeditions.name(Expeditions.destination(guild, record, record.task)));
	}

	/** "Destination" button of the villager detail menu (expedition tasks only). */
	static GuiElementBuilder button(ServerPlayer player, Guild guild, VillagerRecord record) {
		ExpeditionDestinations.Destination destination = Expeditions.destination(guild, record, record.task);
		List<Component> lore = new ArrayList<>();
		describe(lore, guild, record, destination);
		if (record.isAway()) {
			lore.add(Lang.get("villager.gui.destination.away", Expeditions.name(Expeditions.awayDestination(record))));
		}
		lore.add(Component.empty());
		lore.add(Lang.get("villager.gui.destination.click"));
		UUID uuid = record.uuid;
		return GuiUtil.item(icon(destination), Lang.get("villager.gui.destination", Expeditions.name(destination)), lore)
				.setCallback((slot, type, action, g) -> {
					if (!VillagerGui.checkPermission(player, guild) || !VillagerGui.stillMember(player, guild, record)) {
						return;
					}
					GuiUtil.clickSound(player);
					open(player, guild, uuid, 0);
				});
	}

	/** Destination picker of a villager: the destinations of its task's kind, default first. */
	static void open(ServerPlayer player, Guild guild, UUID uuid, int page) {
		VillagerRecord record = guild.villagers.members.get(uuid);
		if (record == null) {
			Msg.send(player, "villager.error.unknown");
			VillagerGui.openList(player, guild);
			return;
		}
		if (!record.task.isExpedition()) {
			Msg.send(player, "villager.destination.no_expedition", record.name);
			VillagerGui.openDetail(player, guild, uuid);
			return;
		}
		boolean nether = record.task == Task.NETHER;
		List<ExpeditionDestinations.Destination> destinations = ExpeditionDestinations.list(Expeditions.settings(), nether);
		String current = Expeditions.destination(guild, record, record.task).id();
		int pages = GuiUtil.pages(destinations.size(), PER_PAGE);
		int shown = Math.max(0, Math.min(page, pages - 1));
		SimpleGui gui = VillagerGui.chest(player, 6,
				Lang.get(nether ? "villager.gui.destinations.title.nether" : "villager.gui.destinations.title", record.name));
		for (int i = 0; i < PER_PAGE; i++) {
			int index = shown * PER_PAGE + i;
			if (index >= destinations.size()) {
				break;
			}
			ExpeditionDestinations.Destination destination = destinations.get(index);
			gui.setSlot(i, entry(player, guild, record, destination, destination.id().equals(current)));
		}
		gui.setSlot(45, GuiUtil.back(() -> VillagerGui.openDetail(player, guild, uuid)));
		if (shown > 0) {
			gui.setSlot(46, GuiUtil.item(Items.ARROW, Lang.get("gui.page.prev"), List.of(Lang.get("gui.page", shown + 1, pages)))
					.setCallback((slot, type, action, g) -> open(player, guild, uuid, shown - 1)));
		}
		List<Component> help = new ArrayList<>(GuiUtil.lines("villager.gui.destinations.help.lore", record.name,
				decimal(Expeditions.settings().wishlistWeightMultiplier)));
		if (nether) {
			help.addAll(GuiUtil.lines("villager.gui.destinations.help.nether"));
		}
		gui.setSlot(49, GuiUtil.item(Items.BOOK, Lang.get("villager.gui.help"), help));
		if (shown < pages - 1) {
			gui.setSlot(52, GuiUtil.item(Items.ARROW, Lang.get("gui.page.next"), List.of(Lang.get("gui.page", shown + 1, pages)))
					.setCallback((slot, type, action, g) -> open(player, guild, uuid, shown + 1)));
		}
		gui.setSlot(53, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	private static GuiElementBuilder entry(ServerPlayer player, Guild guild, VillagerRecord record,
			ExpeditionDestinations.Destination destination, boolean current) {
		boolean unlocked = destination.unlocked(guild.level);
		String name = Expeditions.name(destination);
		List<Component> lore = new ArrayList<>();
		describe(lore, guild, record, destination);
		lore.add(Component.empty());
		if (!unlocked) {
			lore.add(Lang.get("villager.gui.destination.locked", destination.minLevel(), guild.level));
		} else {
			lore.add(Lang.get(current ? "villager.gui.destination.current" : "villager.gui.destination.choose"));
		}
		GuiElementBuilder icon;
		if (unlocked) {
			icon = GuiUtil.item(icon(destination), Lang.get(current ? "villager.gui.destination.name.current" : "villager.gui.destination.name", name), lore);
			if (current) {
				icon.glow();
			}
		} else {
			icon = GuiUtil.item(Items.DYE.pick(DyeColor.GRAY), Lang.get("villager.gui.destination.name.locked", Lang.strip(name)), lore);
		}
		String id = destination.id();
		return icon.setCallback((slot, type, action, g) -> choose(player, guild, record, id));
	}

	private static void choose(ServerPlayer player, Guild guild, VillagerRecord record, String id) {
		if (!VillagerGui.checkPermission(player, guild) || !VillagerGui.stillMember(player, guild, record)) {
			return;
		}
		if (!record.task.isExpedition()) {
			Msg.send(player, "villager.destination.no_expedition", record.name);
			GuiUtil.errorSound(player);
			VillagerGui.openDetail(player, guild, record.uuid);
			return;
		}
		boolean nether = record.task == Task.NETHER;
		ExpeditionDestinations.Destination destination = ExpeditionDestinations.find(Expeditions.settings(), nether, id);
		if (!destination.id().equals(id)) {
			// Removed by a config reload while the menu was open.
			Msg.send(player, "villager.destination.unknown");
			GuiUtil.errorSound(player);
			open(player, guild, record.uuid, 0);
			return;
		}
		String name = Expeditions.name(destination);
		if (!destination.unlocked(guild.level)) {
			Msg.send(player, "villager.destination.locked", Lang.strip(name), destination.minLevel());
			GuiUtil.errorSound(player);
			return;
		}
		record.expeditionDestination = destination.id();
		GuildManager.markDirty();
		String message = record.isAway() ? "villager.destination.set_next" : nether ? "villager.destination.set.nether" : "villager.destination.set";
		Msg.send(player, message, record.name, name);
		GuiUtil.successSound(player);
		VillagerGui.openDetail(player, guild, record.uuid);
	}

	/** Duration (with the work speed upgrade), risk, level, typical and rare loot, maps, uniques, wishlist favourites. */
	private static void describe(List<Component> lore, Guild guild, VillagerRecord record, ExpeditionDestinations.Destination destination) {
		int seconds = Upgrades.workSeconds(guild, destination.durationSeconds());
		lore.add(Lang.get("villager.gui.destination.duration", Math.max(1, (seconds + 59) / 60)));
		lore.add(Lang.get("villager.gui.destination.risk", Lang.raw("villager.destination.risk." + ExpeditionDestinations.risk(destination.lossChance())),
				decimal(destination.lossChance() * 100)));
		if (destination.minLevel() > 1 && destination.unlocked(guild.level)) {
			// Locked destinations end with the reason instead (see entry).
			lore.add(Lang.get("villager.gui.destination.level", destination.minLevel()));
		}
		List<VillagerConfig.LootEntry> typical = ExpeditionDestinations.typical(destination.loot(), TYPICAL_LINES);
		if (!typical.isEmpty()) {
			lore.add(Lang.get("villager.gui.destination.typical"));
			for (VillagerConfig.LootEntry entry : typical) {
				Item item = ItemUtil.item(entry.item);
				if (item != null) {
					lore.add(entry.min == entry.max
							? TextUtil.lang("villager.gui.destination.loot_line.fixed", ItemUtil.name(item), entry.min)
							: TextUtil.lang("villager.gui.destination.loot_line", ItemUtil.name(item), entry.min, entry.max));
				}
			}
		}
		List<String> rareIds = new ArrayList<>();
		for (VillagerConfig.LootEntry entry : ExpeditionDestinations.rare(destination.loot(), RARE_ITEMS, typical)) {
			rareIds.add(entry.item);
		}
		Component rare = names(rareIds);
		if (rare != null) {
			lore.add(TextUtil.lang("villager.gui.destination.rare", rare));
		}
		if (destination.treasureMapChance() > 0) {
			lore.add(Lang.get("villager.gui.destination.map", decimal(destination.treasureMapChance() * 100)));
		}
		if (destination.uniqueChanceMultiplier() != 1.0) {
			lore.add(Lang.get("villager.gui.destination.unique", decimal(destination.uniqueChanceMultiplier())));
		}
		double boost = Expeditions.settings().wishlistWeightMultiplier;
		Component favoured = boost > 1 ? names(ExpeditionDestinations.favoured(destination.loot(), record.wishlist)) : null;
		if (favoured != null) {
			lore.add(TextUtil.lang("villager.gui.destination.favoured", favoured, decimal(boost)));
		}
	}

	/** "Sable, Cactus, Os" with the item names translated by the client, null when no item is known. */
	private static @Nullable Component names(List<String> itemIds) {
		MutableComponent out = null;
		for (String id : itemIds) {
			Item item = ItemUtil.item(id);
			if (item == null) {
				continue;
			}
			if (out == null) {
				out = Component.empty();
			} else {
				out.append(", ");
			}
			out.append(ItemUtil.name(item));
		}
		return out;
	}

	private static Item icon(ExpeditionDestinations.Destination destination) {
		Item item = ItemUtil.item(destination.icon());
		return item != null ? item : destination.nether() ? Items.NETHERRACK : Items.COMPASS;
	}

	/** 1.5 → "1,5", 12.0 → "12" (French decimal comma, two decimals at most). */
	static String decimal(double value) {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			return "0";
		}
		return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',');
	}
}
