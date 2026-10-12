package dev.guildy.villager;

import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.gui.GuiUtil;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * "Objectifs prioritaires" menus: one row of up to {@link Wishlist#MAX} wanted item types, for a villager (its own
 * entries; those of its zone are added to them) or for a work zone (shared by its villagers). An item is added by
 * clicking an empty slot with it on the cursor, or by shift-clicking it in the inventory: only its type is recorded, the
 * player's item is never taken nor moved. A click on an entry removes it; "Vider" empties the list. Editing needs the
 * villager management permission (checked at every click).
 */
public final class WishlistMenu {
	private static final int ROWS = 2;
	private static final int SLOT_BACK = 9;
	private static final int SLOT_INFO = 11;
	private static final int SLOT_ZONE = 13;
	private static final int SLOT_CLEAR = 15;
	private static final int SLOT_CLOSE = 17;
	/** Item names shown by a summary line before "+N". */
	private static final int SUMMARY_ITEMS = 3;

	private WishlistMenu() {
	}

	/** Tasks whose work follows a wishlist (the miner's is handled by its own code). */
	public static boolean supports(Task task) {
		return task == Task.MINER || task == Task.LUMBERJACK || task == Task.FARMER;
	}

	public static boolean supports(ZoneType type) {
		return type == ZoneType.MINE || type == ZoneType.FOREST || type == ZoneType.FARM;
	}

	// ================================================================ buttons and summaries for the other menus

	/** "Objectifs prioritaires" button of the villager menu: its entries and its zone's. */
	static GuiElementBuilder villagerButton(ServerPlayer player, Guild guild, VillagerRecord record) {
		WorkZone zone = Zones.assignedZone(guild, record);
		int margin = VillagerModule.config().zones.wishlistMargin;
		List<Component> lore = new ArrayList<>(GuiUtil.lines("villager.wishlist.button.lore", record.name, margin));
		lore.add(Lang.get("villager.wishlist.button.own").append(record.wishlist.isEmpty() ? Lang.get("villager.wishlist.button.none") : Component.empty()));
		lore.addAll(entryLines(record.wishlist));
		if (zone != null && !zone.wishlist.isEmpty()) {
			lore.add(Lang.get("villager.wishlist.button.zone", zone.name));
			lore.addAll(entryLines(zone.wishlist));
		}
		lore.add(Component.empty());
		lore.add(Lang.get("villager.wishlist.button.click"));
		GuiElementBuilder button = GuiUtil.item(Items.WRITABLE_BOOK, Lang.get("villager.wishlist.button"), lore);
		if (!record.wishlist.isEmpty() || (zone != null && !zone.wishlist.isEmpty())) {
			button.glow();
		}
		return button.setCallback((slot, type, action, g) -> {
			GuiUtil.clickSound(player);
			openVillager(player, guild, record.uuid);
		});
	}

	/** "Objectifs prioritaires" button of the zone menu. */
	static GuiElementBuilder zoneButton(ServerPlayer player, Guild guild, WorkZone zone) {
		List<Component> lore = new ArrayList<>(GuiUtil.lines("villager.wishlist.button.zone_lore", VillagerModule.config().zones.wishlistMargin));
		if (zone.wishlist.isEmpty()) {
			lore.add(Lang.get("villager.wishlist.button.none"));
		}
		lore.addAll(entryLines(zone.wishlist));
		lore.add(Component.empty());
		lore.add(Lang.get("villager.wishlist.button.click"));
		GuiElementBuilder button = GuiUtil.item(Items.WRITABLE_BOOK, Lang.get("villager.wishlist.button"), lore);
		if (!zone.wishlist.isEmpty()) {
			button.glow();
		}
		int zoneId = zone.id;
		return button.setCallback((slot, type, action, g) -> {
			GuiUtil.clickSound(player);
			openZone(player, guild, zoneId, null);
		});
	}

	/** "Objectifs : Diorite, Granite, Tuf +2" for a villager (its entries, then its zone's), null when it has none. */
	static @Nullable Component summary(Guild guild, VillagerRecord record) {
		if (!supports(record.task)) {
			return null;
		}
		WorkZone zone = Zones.assignedZone(guild, record);
		return summary(record.wishlist, zone == null ? List.of() : zone.wishlist);
	}

	/** Summary line of a zone's wishlist, null when empty. */
	static @Nullable Component summary(WorkZone zone) {
		return supports(zone.type) ? summary(zone.wishlist, List.of()) : null;
	}

	private static @Nullable Component summary(List<String> own, List<String> shared) {
		List<String> merged = WishMatch.merged(own, shared);
		if (merged.isEmpty()) {
			return null;
		}
		List<String> head = WishMatch.head(own, shared, SUMMARY_ITEMS);
		MutableComponent line = Lang.get("villager.wishlist.summary");
		for (int i = 0; i < head.size(); i++) {
			if (i > 0) {
				line.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
			}
			line.append(name(head.get(i)));
		}
		if (merged.size() > head.size()) {
			line.append(Lang.get("villager.wishlist.summary.more", merged.size() - head.size()));
		}
		return line;
	}

	private static List<Component> entryLines(List<String> ids) {
		List<Component> lines = new ArrayList<>();
		for (String id : ids) {
			lines.add(Lang.get("villager.wishlist.line").append(name(id)));
		}
		return lines;
	}

	/** Item name rendered in the player's language (translatable), white. */
	private static Component name(String id) {
		Item item = ItemUtil.item(id);
		return (item == null ? Component.literal(id) : ItemUtil.name(item).copy()).withStyle(ChatFormatting.WHITE);
	}

	// ================================================================ editors

	/** Wishlist of one villager (miner, lumberjack, farmer); the entries of its zone are shown in the info item. */
	public static void openVillager(ServerPlayer player, Guild guild, UUID uuid) {
		VillagerRecord record = guild.villagers.members.get(uuid);
		if (record == null) {
			Msg.send(player, "villager.error.unknown");
			VillagerGui.openList(player, guild);
			return;
		}
		List<Component> extra = new ArrayList<>(GuiUtil.lines("villager.wishlist.info.villager"));
		WorkZone zone = Zones.assignedZone(guild, record);
		if (zone != null && !zone.wishlist.isEmpty()) {
			extra.add(Lang.get("villager.wishlist.button.zone", zone.name));
			extra.addAll(entryLines(zone.wishlist));
		}
		Editor editor = new Editor(player, guild, Lang.get("villager.wishlist.title.villager", record.name),
				() -> guild.villagers.members.get(uuid) == record ? record.wishlist : null, usefulFor(record.task), extra,
				() -> VillagerGui.openDetail(player, guild, uuid));
		if (zone != null && supports(zone.type)) {
			int zoneId = zone.id;
			editor.gui.setSlot(SLOT_ZONE, GuiUtil.item(ZoneFlags.icon(zone.type), Lang.get("villager.wishlist.zone_shortcut", zone.name),
					GuiUtil.lines("villager.wishlist.zone_shortcut.lore")).setCallback((slot, type, action, g) -> {
				GuiUtil.clickSound(player);
				openZone(player, guild, zoneId, () -> openVillager(player, guild, uuid));
			}));
		}
		editor.open();
	}

	/** Wishlist of a work zone, shared by its villagers; {@code back} defaults to the zone menu. */
	public static void openZone(ServerPlayer player, Guild guild, int zoneId, @Nullable Runnable back) {
		WorkZone zone = guild.villagers.zone(zoneId);
		if (zone == null) {
			Msg.send(player, "villager.zone.error.unknown");
			ZoneGui.openZones(player, guild);
			return;
		}
		Editor editor = new Editor(player, guild, Lang.get("villager.wishlist.title.zone", zone.name),
				() -> guild.villagers.zones.contains(zone) ? zone.wishlist : null, usefulFor(zone.type.task()),
				GuiUtil.lines("villager.wishlist.info.zone"), back != null ? back : () -> ZoneGui.openZone(player, guild, zoneId));
		editor.open();
	}

	/** Items this task can bring back (entries it cannot are flagged), null = no hint (miner: handled elsewhere). */
	private static @Nullable Predicate<Item> usefulFor(Task task) {
		return switch (task) {
			case LUMBERJACK -> WorkWishes::lumberjackCanGet;
			case FARMER -> WorkWishes::farmerCanGet;
			default -> null;
		};
	}

	/** One open editor: the list is fetched again at every click (null once the villager / zone is gone). */
	private static final class Editor {
		private final ServerPlayer player;
		private final Guild guild;
		private final Supplier<@Nullable List<String>> list;
		private final @Nullable Predicate<Item> useful;
		private final SimpleGui gui;

		Editor(ServerPlayer player, Guild guild, Component title, Supplier<@Nullable List<String>> list, @Nullable Predicate<Item> useful,
				List<Component> infoExtra, Runnable back) {
			this.player = player;
			this.guild = guild;
			this.list = list;
			this.useful = useful;
			this.gui = new SimpleGui(GuiUtil.rows(ROWS), player, false) {
				@Override
				public boolean onAnyClick(int index, ClickType type, ContainerInput action) {
					if (index >= getSize() && type.shift) {
						// Shift-click in the inventory: the item type is added, the item itself is never moved.
						Safe.run("villager.wishlist", () -> addFromInventory(index));
						return false;
					}
					return super.onAnyClick(index, type, action);
				}

				@Override
				public boolean click(int index, ClickType type, ContainerInput action) {
					if (index < 0 || index >= getSize() || !(type.isLeft || type.isRight) || type.isDragging) {
						return false;
					}
					return Safe.get("villager.wishlist", () -> super.click(index, type, action), false);
				}
			};
			gui.setTitle(title);
			List<Component> info = new ArrayList<>(GuiUtil.lines("villager.wishlist.info.lore", VillagerModule.config().zones.wishlistMargin, Wishlist.MAX));
			info.addAll(infoExtra);
			gui.setSlot(SLOT_INFO, GuiUtil.item(Items.BOOK, Lang.get("villager.wishlist.info"), info));
			gui.setSlot(SLOT_CLEAR, GuiUtil.item(Items.BUCKET, Lang.get("villager.wishlist.clear"), GuiUtil.lines("villager.wishlist.clear.lore"))
					.setCallback((slot, type, action, g) -> clear()));
			gui.setSlot(SLOT_BACK, GuiUtil.back(back));
			gui.setSlot(SLOT_CLOSE, GuiUtil.close());
		}

		void open() {
			render();
			GuiUtil.fill(gui);
			gui.open();
		}

		/** Entries in the first row, then free slots. */
		private void render() {
			List<String> ids = list.get();
			if (ids == null) {
				gone();
				return;
			}
			for (int i = 0; i < Wishlist.MAX; i++) {
				if (i < ids.size()) {
					String id = ids.get(i);
					gui.setSlot(i, entry(id).setCallback((slot, type, action, g) -> remove(id)));
				} else {
					gui.setSlot(i, GuiUtil.item(Items.STAINED_GLASS_PANE.pick(DyeColor.LIGHT_GRAY), Lang.get("villager.wishlist.slot.empty"),
							GuiUtil.lines("villager.wishlist.slot.empty.lore")).setCallback((slot, type, action, g) -> addFromCursor()));
				}
			}
		}

		private GuiElementBuilder entry(String id) {
			Item item = ItemUtil.item(id);
			List<Component> lore = new ArrayList<>();
			if (item != null && useful != null && !useful.test(item)) {
				lore.add(Lang.get("villager.wishlist.entry.useless"));
			}
			lore.add(Lang.get("villager.wishlist.entry.lore"));
			return GuiUtil.item(item == null ? Items.BARRIER : item, name(id), lore);
		}

		private void addFromCursor() {
			ItemStack carried = player.containerMenu.getCarried();
			if (carried.isEmpty()) {
				Msg.send(player, "villager.wishlist.no_item");
				GuiUtil.errorSound(player);
				return;
			}
			add(carried.getItem());
		}

		private void addFromInventory(int index) {
			Slot slot = gui.getSlotRedirectOrPlayer(index);
			if (slot != null && slot.hasItem()) {
				add(slot.getItem().getItem());
			}
		}

		private void add(Item item) {
			if (!VillagerGui.checkPermission(player, guild)) {
				return;
			}
			List<String> ids = list.get();
			if (ids == null) {
				gone();
				return;
			}
			String id = Wishlist.id(item);
			if (ids.contains(id)) {
				Msg.send(player, "villager.wishlist.duplicate");
				GuiUtil.errorSound(player);
				return;
			}
			if (!WishMatch.add(ids, id, Wishlist.MAX)) {
				Msg.send(player, "villager.wishlist.full", Wishlist.MAX);
				GuiUtil.errorSound(player);
				return;
			}
			GuildManager.markDirty();
			GuiUtil.successSound(player);
			render();
		}

		private void remove(String id) {
			if (!VillagerGui.checkPermission(player, guild)) {
				return;
			}
			List<String> ids = list.get();
			if (ids == null) {
				gone();
				return;
			}
			if (WishMatch.remove(ids, id)) {
				GuildManager.markDirty();
				GuiUtil.clickSound(player);
			}
			render();
		}

		private void clear() {
			if (!VillagerGui.checkPermission(player, guild)) {
				return;
			}
			List<String> ids = list.get();
			if (ids == null) {
				gone();
				return;
			}
			if (!ids.isEmpty()) {
				ids.clear();
				GuildManager.markDirty();
				Msg.send(player, "villager.wishlist.cleared");
			}
			GuiUtil.clickSound(player);
			render();
		}

		private void gone() {
			Msg.send(player, "villager.wishlist.gone");
			player.closeContainer();
		}
	}
}
