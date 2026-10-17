package dev.guildy.villager;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.gui.GuiUtil;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Storage menus: the registered containers ("Stockage": left click = change the role, right click = remove; tool row:
 * categories, chest groups, chest rooms, storage order, add a chest, show, held item, help), the storage categories
 * (icon, examples, chests; click = show them), the chest groups (state, fill, "Réorganiser maintenant", the chests of a
 * group in reading order), the chest rooms (add / show / rescan / remove) and the role picker shown after selecting a
 * container. Every button re-checks the VILLAGERS permission.
 */
final class StorageGui {
	private static final int PER_PAGE = 45;
	/** Containers per page of the storage menu (its fifth row holds the tools). */
	private static final int CHESTS_PER_PAGE = 36;
	/** Characters per lore line of the category descriptions. */
	private static final int WRAP = 36;

	private StorageGui() {
	}

	/** One choice of the role picker / categories menu: the deposit, a category, the fallback, or automatic (rooms). */
	private record Option(StorageChest.Role role, String category, boolean fallback, boolean auto) {
		Option(StorageChest.Role role, String category, boolean fallback) {
			this(role, category, fallback, false);
		}
	}

	// ================================================================ registered containers

	static void openStorage(ServerPlayer player, Guild guild, int page) {
		ServerLevel level = GuildyServer.claimLevel();
		if (level != null) {
			Storage.cleanup(level, guild);
		}
		List<StorageChest> chests = new ArrayList<>(guild.villagers.chests);
		int pages = GuiUtil.pages(chests.size(), CHESTS_PER_PAGE);
		int current = Math.max(0, Math.min(page, pages - 1));
		SimpleGui gui = VillagerGui.chest(player, 6, Lang.get("villager.gui.storage.title", chests.size()));
		for (int i = 0; i < CHESTS_PER_PAGE; i++) {
			int index = current * CHESTS_PER_PAGE + i;
			if (index >= chests.size()) {
				break;
			}
			StorageChest chest = chests.get(index);
			gui.setSlot(i, chestIcon(level, guild, chest).setCallback((slot, type, action, g) -> {
				if (!VillagerGui.checkPermission(player, guild)) {
					return;
				}
				if (!guild.villagers.chests.contains(chest)) {
					openStorage(player, guild, current);
					return;
				}
				GuiUtil.clickSound(player);
				if (type.isRight) {
					confirmRemove(player, guild, chest, current);
				} else {
					openRolePicker(player, guild, chest.pos, 0);
				}
			}));
		}
		StorageCategories categories = Storage.categories();
		VillagerConfig.Storage config = VillagerModule.config().storage;
		gui.setSlot(36, GuiUtil.item(Items.BOOKSHELF, Lang.get("villager.gui.storage.categories.button"),
				GuiUtil.lines("villager.gui.storage.categories.button.lore", categories.names().size(), categories.fallback()))
				.setCallback((slot, type, action, g) -> {
					GuiUtil.clickSound(player);
					openCategories(player, guild, 0);
				}));
		int groups = level == null ? 0 : StorageGroups.groups(level, guild).size();
		gui.setSlot(37, GuiUtil.item(Items.CHISELED_BOOKSHELF, Lang.get("villager.gui.storage.groups.button"),
				GuiUtil.lines("villager.gui.storage.groups.button.lore", groups)).setCallback((slot, type, action, g) -> {
			GuiUtil.clickSound(player);
			openGroups(player, guild, 0);
		}));
		gui.setSlot(38, GuiUtil.item(Items.BARREL, Lang.get("villager.gui.storage.rooms.button"),
				GuiUtil.lines("villager.gui.storage.rooms.button.lore", guild.villagers.rooms.size(), autoCount(guild)))
				.setCallback((slot, type, action, g) -> {
					GuiUtil.clickSound(player);
					openRooms(player, guild, 0);
				}));
		ChestOrder.Mode mode = StorageGroups.mode(guild);
		ChestOrder.Mode other = mode == ChestOrder.Mode.COLUMNS ? ChestOrder.Mode.ROWS : ChestOrder.Mode.COLUMNS;
		gui.setSlot(39, GuiUtil.item(mode == ChestOrder.Mode.COLUMNS ? Items.QUARTZ_PILLAR : Items.QUARTZ_BRICKS,
				Lang.get("villager.gui.storage.order", orderName(mode)), GuiUtil.lines("villager.gui.storage.order.lore", orderName(other)))
				.setCallback((slot, type, action, g) -> {
					if (!VillagerGui.checkPermission(player, guild)) {
						return;
					}
					guild.villagers.storageOrder = other.id();
					GuildManager.markDirty();
					ServerLevel world = GuildyServer.claimLevel();
					if (world != null) {
						ChestRooms.replanAll(world, guild);
					}
					Tidier.invalidate(guild);
					Msg.send(player, "villager.storage.order.changed", orderName(other));
					GuiUtil.successSound(player);
					openStorage(player, guild, current);
				}));
		gui.setSlot(40, GuiUtil.item(Items.CHEST, Lang.get("villager.gui.storage.add"), GuiUtil.lines("villager.gui.storage.add.lore",
				VillagerInteractions.SELECTION_SECONDS)).glow().setCallback((slot, type, action, g) -> {
			if (!VillagerGui.checkPermission(player, guild)) {
				return;
			}
			if (manualCount(guild) >= config.maxChests) {
				Msg.send(player, "villager.storage.limit", config.maxChests);
				GuiUtil.errorSound(player);
				return;
			}
			g.close();
			GuiUtil.clickSound(player);
			VillagerInteractions.startSelection(player, guild);
		}));
		gui.setSlot(41, GuiUtil.item(Items.SPYGLASS, Lang.get("villager.gui.storage.show"),
				GuiUtil.lines("villager.gui.storage.show.lore", config.showSeconds))
				.setCallback((slot, type, action, g) -> {
					if (StorageVisualizer.show(player, guild, null) > 0) {
						g.close();
						GuiUtil.successSound(player);
					} else {
						GuiUtil.errorSound(player);
					}
				}));
		gui.setSlot(42, heldIcon(player, guild).setCallback((slot, type, action, g) -> {
			GuiUtil.clickSound(player);
			openStorage(player, guild, current);
		}));
		gui.setSlot(43, GuiUtil.item(Items.PAPER, Lang.get("villager.gui.help"),
				GuiUtil.lines("villager.gui.storage.help.text", categories.fallback())));
		gui.setSlot(45, GuiUtil.back(() -> VillagerGui.openList(player, guild, 0)));
		if (current > 0) {
			gui.setSlot(46, GuiUtil.item(Items.ARROW, Lang.get("gui.page.prev"), List.of(Lang.get("gui.page", current + 1, pages)))
					.setCallback((slot, type, action, g) -> openStorage(player, guild, current - 1)));
		}
		if (current < pages - 1) {
			gui.setSlot(52, GuiUtil.item(Items.ARROW, Lang.get("gui.page.next"), List.of(Lang.get("gui.page", current + 1, pages)))
					.setCallback((slot, type, action, g) -> openStorage(player, guild, current + 1)));
		}
		gui.setSlot(53, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	private static void confirmRemove(ServerPlayer player, Guild guild, StorageChest chest, int page) {
		GuiUtil.confirm(player, Lang.get("villager.gui.storage.remove.confirm"),
				List.of(Lang.get("villager.gui.storage.entry.lore.pos", chest.pos.getX(), chest.pos.getY(), chest.pos.getZ())),
				() -> {
					if (VillagerGui.checkPermission(player, guild) && guild.villagers.chests.remove(chest)) {
						ServerLevel level = GuildyServer.claimLevel();
						if (level != null) {
							StorageLabels.clear(level, chest);
						}
						Tidier.invalidate(guild);
						GuildManager.markDirty();
						Msg.send(player, "villager.storage.removed");
					}
					openStorage(player, guild, page);
				},
				() -> openStorage(player, guild, page));
	}

	private static GuiElementBuilder chestIcon(@Nullable ServerLevel level, Guild guild, StorageChest chest) {
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("villager.gui.storage.entry.lore.pos", chest.pos.getX(), chest.pos.getY(), chest.pos.getZ()));
		if (level == null || !level.isLoaded(chest.pos)) {
			lore.add(Lang.get("villager.gui.storage.entry.lore.unloaded"));
		} else {
			int[] fill = Storage.fill(level, chest.pos);
			lore.add(fill == null ? Lang.get("villager.gui.storage.entry.lore.missing")
					: Lang.get("villager.gui.storage.entry.lore.fill", fill[0], fill[1]));
		}
		ChestRoom room = guild.villagers.room(chest.room);
		if (room != null) {
			lore.add(chest.auto ? Lang.get("villager.gui.storage.entry.lore.auto", room.name) : Lang.get("villager.gui.storage.entry.lore.room", room.name));
		}
		if (!chest.isDeposit() && !chest.isFree() && !chest.category.isBlank() && Storage.categories().canonical(chest.category) == null) {
			lore.addAll(GuiUtil.lines("villager.gui.storage.entry.lore.stale", chest.category, Storage.effectiveCategory(chest)));
		}
		lore.addAll(GuiUtil.lines("villager.gui.storage.entry.lore.click"));
		if (chest.isDeposit()) {
			return GuiUtil.item(Items.HOPPER, Lang.get("villager.gui.storage.entry.deposit"), lore);
		}
		if (chest.isFree()) {
			return GuiUtil.item(Items.CHEST, Lang.get("villager.gui.storage.entry.reserve"), lore);
		}
		String category = Storage.effectiveCategory(chest);
		if (Storage.isFallback(chest)) {
			return GuiUtil.item(Items.BARREL, Lang.get("villager.gui.storage.entry.fallback", category), lore);
		}
		return GuiUtil.item(icon(category), Lang.get("villager.gui.storage.entry.category", category), lore);
	}

	/** "Où ranger l'objet en main ?": category of the held item, the rule that chose it and its chests. */
	private static GuiElementBuilder heldIcon(ServerPlayer player, Guild guild) {
		ItemStack held = player.getMainHandItem();
		if (held.isEmpty()) {
			return GuiUtil.item(Items.COMPASS, Lang.get("villager.gui.storage.held"), GuiUtil.lines("villager.gui.storage.held.none"));
		}
		StorageCategories.Match match = ItemClassifier.match(held);
		List<Component> lore = new ArrayList<>();
		// The item name is translated by the client (French for French players).
		lore.add(Lang.get("villager.gui.storage.held.item").append(held.getHoverName()));
		lore.add(Lang.get("villager.gui.storage.held.category", match.category()));
		lore.add(Lang.get("villager.gui.storage.held.rule", ruleText(match)));
		lore.add(Lang.get("villager.gui.storage.held.chests", counts(guild).getOrDefault(match.category().toLowerCase(Locale.ROOT), 0)));
		return GuiUtil.item(held, Lang.get("villager.gui.storage.held"), lore);
	}

	/** "tag #minecraft:logs", "objet minecraft:leather"... */
	static String ruleText(StorageCategories.Match match) {
		return switch (match.kind()) {
			case ID -> Lang.raw("villager.storage.rule.id", match.rule());
			case TAG -> Lang.raw("villager.storage.rule.tag", match.rule());
			case PATTERN -> Lang.raw("villager.storage.rule.pattern", match.rule());
			case PROPERTY -> Lang.raw("villager.storage.rule.property", match.rule());
			case TAB -> Lang.raw("villager.storage.rule.tab", match.rule());
			case FALLBACK -> Lang.raw("villager.storage.rule.fallback");
		};
	}

	// ================================================================ categories

	/** The categories (and the fallback) with their icon, examples and number of chests; click = show these chests. */
	static void openCategories(ServerPlayer player, Guild guild, int page) {
		List<Option> options = options(false, false);
		int pages = GuiUtil.pages(options.size(), PER_PAGE);
		int current = Math.max(0, Math.min(page, pages - 1));
		Map<String, Integer> counts = counts(guild);
		String fallback = Storage.categories().fallback();
		SimpleGui gui = VillagerGui.chest(player, 6, Lang.get("villager.gui.categories.title"));
		for (int i = 0; i < PER_PAGE; i++) {
			int index = current * PER_PAGE + i;
			if (index >= options.size()) {
				break;
			}
			Option option = options.get(index);
			int count = counts.getOrDefault(option.category().toLowerCase(Locale.ROOT), 0);
			List<Component> lore = new ArrayList<>(description(option));
			lore.add(Component.empty());
			lore.add(count > 0 ? Lang.get("villager.gui.category.chests", count) : Lang.get("villager.gui.category.no_chest",
					option.fallback() ? Lang.raw("villager.storage.role.deposit") : fallback));
			if (count > 0) {
				lore.add(Lang.get("villager.gui.category.show"));
			}
			Component name = Lang.get(option.fallback() ? "villager.gui.category.fallback" : "villager.gui.category.name", option.category());
			gui.setSlot(i, GuiUtil.item(optionIcon(option), name, lore).setCallback((slot, type, action, g) -> {
				if (StorageVisualizer.show(player, guild, option.category()) > 0) {
					g.close();
					GuiUtil.successSound(player);
				} else {
					GuiUtil.errorSound(player);
				}
			}));
		}
		gui.setSlot(45, GuiUtil.back(() -> openStorage(player, guild, 0)));
		pagination(gui, current, pages, p -> openCategories(player, guild, p));
		gui.setSlot(53, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	// ================================================================ chest groups

	/** The chest groups (category + room): state, fill, worker; left click = its chests, right click = reorganise now. */
	static void openGroups(ServerPlayer player, Guild guild, int page) {
		ServerLevel level = GuildyServer.claimLevel();
		List<StorageGroups.Group> groups = level == null ? List.of() : StorageGroups.groups(level, guild);
		int pages = GuiUtil.pages(groups.size(), PER_PAGE);
		int current = Math.max(0, Math.min(page, pages - 1));
		SimpleGui gui = VillagerGui.chest(player, 6, Lang.get("villager.gui.groups.title", groups.size()));
		for (int i = 0; i < PER_PAGE; i++) {
			int index = current * PER_PAGE + i;
			if (index >= groups.size()) {
				break;
			}
			StorageGroups.Group group = groups.get(index);
			gui.setSlot(i, groupIcon(level, guild, group).setCallback((slot, type, action, g) -> {
				if (!VillagerGui.checkPermission(player, guild)) {
					return;
				}
				if (type.isRight) {
					reorganize(player, guild, group);
					openGroups(player, guild, current);
				} else {
					GuiUtil.clickSound(player);
					openGroup(player, guild, group.key(), 0);
				}
			}));
		}
		if (groups.isEmpty()) {
			gui.setSlot(22, GuiUtil.item(Items.STRUCTURE_VOID, Lang.get("villager.gui.groups.none"), List.of()));
		}
		gui.setSlot(45, GuiUtil.back(() -> openStorage(player, guild, 0)));
		pagination(gui, current, pages, p -> openGroups(player, guild, p));
		gui.setSlot(53, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	private static GuiElementBuilder groupIcon(@Nullable ServerLevel level, Guild guild, StorageGroups.Group group) {
		List<Component> lore = new ArrayList<>();
		ChestRoom room = guild.villagers.room(group.room());
		lore.add(room == null ? Lang.get("villager.gui.group.outside") : Lang.get("villager.gui.group.room", room.name));
		lore.add(Lang.get("villager.gui.group.chests", group.chests().size()));
		int[] fill = fill(level, group.chests());
		lore.add(Lang.get("villager.gui.group.fill", fill[1] == 0 ? 0 : Math.round(100.0f * fill[0] / fill[1]), fill[0], fill[1]));
		lore.addAll(stateLines(guild, group.key()));
		lore.add(Component.empty());
		lore.addAll(GuiUtil.lines("villager.gui.group.click"));
		boolean fallback = group.category().equalsIgnoreCase(Storage.categories().fallback());
		return GuiUtil.item(fallback ? Items.BARREL : icon(group.category()), Lang.get("villager.gui.group.name", group.category()), lore);
	}

	/** "Rangé" / "À ranger" / not checked yet, a busy chest, and the villager tidying it. */
	private static List<Component> stateLines(Guild guild, String key) {
		List<Component> lines = new ArrayList<>();
		Tidier.Status status = Tidier.status(guild, key);
		if (status == null) {
			lines.add(Lang.get("villager.gui.group.unknown"));
		} else {
			lines.add(Lang.get(status.tidy() ? "villager.gui.group.tidy" : "villager.gui.group.untidy"));
			if (status.busy()) {
				lines.add(Lang.get("villager.gui.group.busy"));
			}
		}
		UUID worker = Tidier.worker(guild, key, Scheduler.ticks());
		VillagerRecord record = worker == null ? null : guild.villagers.get(worker);
		if (record != null) {
			lines.add(Lang.get("villager.gui.group.worker", record.name));
		}
		return lines;
	}

	private static void reorganize(ServerPlayer player, Guild guild, StorageGroups.Group group) {
		Tidier.prioritize(guild, group.key(), Scheduler.ticks());
		Msg.send(player, "villager.storage.group.prioritized", group.category());
		boolean sorter = false;
		for (VillagerRecord record : guild.villagers.members.values()) {
			sorter |= record.task == Task.SORTER;
		}
		if (!sorter) {
			Msg.send(player, "villager.storage.group.no_sorter");
		}
		GuiUtil.successSound(player);
	}

	/** The chests of a group in reading order, "Réorganiser maintenant" and "Afficher". */
	static void openGroup(ServerPlayer player, Guild guild, String key, int page) {
		ServerLevel level = GuildyServer.claimLevel();
		StorageGroups.Group group = level == null ? null : StorageGroups.group(level, guild, key);
		if (group == null) {
			openGroups(player, guild, 0);
			return;
		}
		List<StorageChest> chests = group.chests();
		int pages = GuiUtil.pages(chests.size(), PER_PAGE);
		int current = Math.max(0, Math.min(page, pages - 1));
		SimpleGui gui = VillagerGui.chest(player, 6, Lang.get("villager.gui.group.title", group.category(), chests.size()));
		boolean fallback = group.category().equalsIgnoreCase(Storage.categories().fallback());
		Item item = fallback ? Items.BARREL : icon(group.category());
		for (int i = 0; i < PER_PAGE; i++) {
			int index = current * PER_PAGE + i;
			if (index >= chests.size()) {
				break;
			}
			StorageChest chest = chests.get(index);
			List<Component> lore = new ArrayList<>();
			lore.add(Lang.get("villager.gui.storage.entry.lore.pos", chest.pos.getX(), chest.pos.getY(), chest.pos.getZ()));
			int[] fill = level.isLoaded(chest.pos) ? Storage.fill(level, chest.pos) : null;
			lore.add(fill == null ? Lang.get("villager.gui.storage.entry.lore.unloaded") : Lang.get("villager.gui.storage.entry.lore.fill", fill[0], fill[1]));
			ChestRoom room = guild.villagers.room(chest.room);
			if (room != null && chest.auto) {
				lore.add(Lang.get("villager.gui.storage.entry.lore.auto", room.name));
			}
			gui.setSlot(i, GuiUtil.item(item, Lang.get("villager.gui.group.chest", index + 1, group.category()), lore));
		}
		List<Component> reorganizeLore = new ArrayList<>(GuiUtil.lines("villager.gui.group.reorganize.lore"));
		reorganizeLore.addAll(stateLines(guild, key));
		gui.setSlot(47, GuiUtil.item(Items.WRITABLE_BOOK, Lang.get("villager.gui.group.reorganize"), reorganizeLore)
				.setCallback((slot, type, action, g) -> {
					if (!VillagerGui.checkPermission(player, guild)) {
						return;
					}
					reorganize(player, guild, group);
					openGroup(player, guild, key, current);
				}));
		gui.setSlot(49, GuiUtil.item(Items.SPYGLASS, Lang.get("villager.gui.group.show"),
				GuiUtil.lines("villager.gui.group.show.lore", VillagerModule.config().storage.showSeconds))
				.setCallback((slot, type, action, g) -> {
					if (StorageVisualizer.show(player, guild, StorageVisualizer.GROUP + key) > 0) {
						g.close();
						GuiUtil.successSound(player);
					} else {
						GuiUtil.errorSound(player);
					}
				}));
		gui.setSlot(51, GuiUtil.item(Items.PAPER, Lang.get("villager.gui.help"),
				List.of(Lang.get("villager.gui.group.order.lore", orderName(StorageGroups.mode(guild))))));
		gui.setSlot(45, GuiUtil.back(() -> openGroups(player, guild, 0)));
		pagination(gui, current, pages, p -> openGroup(player, guild, key, p));
		gui.setSlot(53, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	// ================================================================ chest rooms

	/** The chest rooms: left click = show its chests, shift + click = add the new chests of its box, right click = remove. */
	static void openRooms(ServerPlayer player, Guild guild, int page) {
		ServerLevel level = GuildyServer.claimLevel();
		VillagerConfig.Storage config = VillagerModule.config().storage;
		List<ChestRoom> rooms = new ArrayList<>(guild.villagers.rooms);
		int pages = GuiUtil.pages(rooms.size(), PER_PAGE);
		int current = Math.max(0, Math.min(page, pages - 1));
		SimpleGui gui = VillagerGui.chest(player, 6, Lang.get("villager.gui.rooms.title", rooms.size()));
		for (int i = 0; i < PER_PAGE; i++) {
			int index = current * PER_PAGE + i;
			if (index >= rooms.size()) {
				break;
			}
			ChestRoom room = rooms.get(index);
			gui.setSlot(i, roomIcon(level, guild, room).setCallback((slot, type, action, g) -> {
				if (!VillagerGui.checkPermission(player, guild)) {
					return;
				}
				if (!guild.villagers.rooms.contains(room)) {
					openRooms(player, guild, current);
					return;
				}
				ServerLevel world = GuildyServer.claimLevel();
				if (type.isRight) {
					GuiUtil.clickSound(player);
					confirmRemoveRoom(player, guild, room, current);
				} else if (type.shift && world != null) {
					int added = ChestRooms.scan(world, guild, room);
					if (added < 0) {
						Msg.send(player, "villager.storage.room.unloaded");
						GuiUtil.errorSound(player);
						return;
					}
					ChestRooms.plan(world, guild, room, false, Scheduler.ticks());
					for (StorageChest chest : ChestRooms.chests(guild, room)) {
						StorageLabels.sync(world, guild, chest);
					}
					Tidier.invalidate(guild);
					Msg.send(player, "villager.storage.room.rescanned", added, room.name);
					GuiUtil.successSound(player);
					openRooms(player, guild, current);
				} else if (StorageVisualizer.show(player, guild, StorageVisualizer.ROOM + room.id) > 0) {
					g.close();
					GuiUtil.successSound(player);
				} else {
					GuiUtil.errorSound(player);
				}
			}));
		}
		if (rooms.isEmpty()) {
			gui.setSlot(22, GuiUtil.item(Items.STRUCTURE_VOID, Lang.get("villager.gui.rooms.none"), List.of()));
		}
		List<Component> addLore = config.maxRooms <= 0 ? List.of(Lang.get("villager.gui.rooms.disabled"))
				: GuiUtil.lines("villager.gui.rooms.add.lore", VillagerInteractions.ROOM_SELECTION_SECONDS, config.maxRoomSize,
				Storage.categories().fallback());
		gui.setSlot(49, GuiUtil.item(Items.CHEST, Lang.get("villager.gui.rooms.add"), addLore).glow()
				.setCallback((slot, type, action, g) -> {
					if (!VillagerGui.checkPermission(player, guild)) {
						return;
					}
					if (guild.villagers.rooms.size() >= config.maxRooms) {
						Msg.send(player, "villager.storage.room.limit", config.maxRooms);
						GuiUtil.errorSound(player);
						return;
					}
					if (ChestRooms.roomChestCount(guild) >= config.maxRoomChests) {
						Msg.send(player, "villager.storage.room.chest_limit", config.maxRoomChests);
						GuiUtil.errorSound(player);
						return;
					}
					g.close();
					GuiUtil.clickSound(player);
					VillagerInteractions.startRoomSelection(player, guild);
				}));
		gui.setSlot(51, GuiUtil.item(Items.PAPER, Lang.get("villager.gui.help"), GuiUtil.lines("villager.gui.rooms.help.text")));
		gui.setSlot(45, GuiUtil.back(() -> openStorage(player, guild, 0)));
		pagination(gui, current, pages, p -> openRooms(player, guild, p));
		gui.setSlot(53, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	private static GuiElementBuilder roomIcon(@Nullable ServerLevel level, Guild guild, ChestRoom room) {
		List<StorageChest> chests = ChestRooms.chests(guild, room);
		int autos = 0;
		int free = 0;
		Set<String> categories = new LinkedHashSet<>();
		for (StorageChest chest : chests) {
			if (chest.auto) {
				autos++;
			}
			if (chest.isFree()) {
				free++;
			} else if (!chest.isDeposit()) {
				categories.add(Storage.effectiveCategory(chest));
			}
		}
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("villager.gui.room.box", room.min.getX(), room.min.getY(), room.min.getZ(), room.max.getX(), room.max.getY(), room.max.getZ()));
		lore.add(Lang.get("villager.gui.room.chests", chests.size(), autos, free));
		if (level != null) {
			boolean columns = StorageGroups.mode(guild) == ChestOrder.Mode.COLUMNS;
			lore.add(Lang.get("villager.gui.room.lines", Lang.raw(columns ? "villager.gui.room.lines.columns" : "villager.gui.room.lines.rows"),
					ChestRooms.lines(level, guild, room)));
		}
		if (!categories.isEmpty()) {
			lore.addAll(wrap(Lang.raw("villager.gui.room.categories", String.join(", ", categories))));
		}
		List<String> unplaced = ChestRooms.unplaced(guild, room);
		if (!unplaced.isEmpty()) {
			lore.addAll(wrap(Lang.raw("villager.gui.room.unplaced", String.join(", ", unplaced))));
		}
		lore.add(Component.empty());
		lore.addAll(GuiUtil.lines("villager.gui.room.click"));
		return GuiUtil.item(Items.CHEST, Lang.get("villager.gui.room.name", room.name), lore);
	}

	private static void confirmRemoveRoom(ServerPlayer player, Guild guild, ChestRoom room, int page) {
		GuiUtil.confirm(player, Lang.get("villager.gui.room.remove.confirm"), GuiUtil.lines("villager.gui.room.remove.lore"),
				() -> {
					ServerLevel level = GuildyServer.claimLevel();
					if (VillagerGui.checkPermission(player, guild) && level != null && guild.villagers.rooms.contains(room)) {
						ChestRooms.remove(level, guild, room);
						Msg.send(player, "villager.storage.room.removed", room.name);
					}
					openRooms(player, guild, page);
				},
				() -> openRooms(player, guild, page));
	}

	// ================================================================ role picker

	/** Role choice after right-clicking a container in selection mode (or left-clicking it in the storage menu). */
	static void openRolePicker(ServerPlayer player, Guild guild, BlockPos pos, int page) {
		ServerLevel level = GuildyServer.claimLevel();
		StorageChest existing = level == null ? guild.villagers.chestAt(pos) : Storage.registered(level, guild, pos);
		List<Option> options = options(true, existing != null && existing.room > 0);
		int pages = GuiUtil.pages(options.size(), PER_PAGE);
		int current = Math.max(0, Math.min(page, pages - 1));
		Map<String, Integer> counts = counts(guild);
		SimpleGui gui = VillagerGui.chest(player, 6, Lang.get("villager.gui.role.title", pos.getX(), pos.getY(), pos.getZ()));
		for (int i = 0; i < PER_PAGE; i++) {
			int index = current * PER_PAGE + i;
			if (index >= options.size()) {
				break;
			}
			Option option = options.get(index);
			boolean isCurrent = existing != null && (option.auto() ? existing.auto : !existing.auto && existing.role == option.role()
					&& (option.role() == StorageChest.Role.DEPOSIT || Storage.effectiveCategory(existing).equalsIgnoreCase(option.category())));
			List<Component> lore = new ArrayList<>(description(option));
			if (option.role() == StorageChest.Role.CATEGORY && !option.auto()) {
				lore.add(Component.empty());
				lore.add(Lang.get("villager.gui.category.chests", counts.getOrDefault(option.category().toLowerCase(Locale.ROOT), 0)));
			}
			lore.add(Component.empty());
			lore.add(isCurrent ? Lang.get("villager.gui.role.current")
					: Lang.get(option.role() == StorageChest.Role.DEPOSIT ? "villager.gui.role.choose_deposit" : "villager.gui.role.choose"));
			Component name = option.auto() ? Lang.get("villager.gui.role.auto")
					: option.role() == StorageChest.Role.DEPOSIT ? Lang.get("villager.gui.role.deposit")
					: Lang.get(option.fallback() ? "villager.gui.role.fallback" : "villager.gui.role.category", option.category());
			GuiElementBuilder button = GuiUtil.item(optionIcon(option), name, lore);
			if (isCurrent) {
				button.glow();
			}
			gui.setSlot(i, button.setCallback((slot, type, action, g) -> assign(player, guild, pos, option)));
		}
		List<Component> info = existing == null ? GuiUtil.lines("villager.gui.role.info.new")
				: List.of(Lang.get("villager.gui.role.info.current", Storage.roleName(existing)));
		gui.setSlot(49, GuiUtil.item(Items.CHEST, Lang.get("villager.gui.role.info", pos.getX(), pos.getY(), pos.getZ()), info));
		gui.setSlot(45, GuiUtil.back(() -> openStorage(player, guild, 0)));
		pagination(gui, current, pages, p -> openRolePicker(player, guild, pos, p));
		gui.setSlot(53, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	private static void assign(ServerPlayer player, Guild guild, BlockPos pos, Option option) {
		if (!VillagerGui.checkPermission(player, guild)) {
			return;
		}
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null || !Storage.isStorageBlock(level.getBlockEntity(pos)) || GuildManager.at(level, pos) != guild) {
			Msg.send(player, "villager.storage.not_territory");
			GuiUtil.errorSound(player);
			player.closeContainer();
			return;
		}
		StorageChest chest = Storage.registered(level, guild, pos);
		if (chest != null) {
			if (option.auto()) {
				ChestRoom room = guild.villagers.room(chest.room);
				if (room == null) {
					player.closeContainer();
					return;
				}
				chest.auto = true;
				chest.role = StorageChest.Role.CATEGORY;
				chest.category = "";
				ChestRooms.plan(level, guild, room, false, Scheduler.ticks());
			} else {
				// A role chosen by a player: the chest is manual from now on (never reassigned by a room).
				chest.auto = false;
				chest.role = option.role();
				chest.category = option.category();
			}
			Msg.send(player, "villager.storage.updated", Storage.roleName(chest));
		} else {
			if (option.auto()) {
				player.closeContainer();
				return;
			}
			if (manualCount(guild) >= VillagerModule.config().storage.maxChests) {
				Msg.send(player, "villager.storage.limit", VillagerModule.config().storage.maxChests);
				player.closeContainer();
				return;
			}
			chest = new StorageChest(pos.immutable(), option.role(), option.category());
			guild.villagers.chests.add(chest);
			Msg.send(player, "villager.storage.added", Storage.roleName(chest), pos.getX(), pos.getY(), pos.getZ());
		}
		StorageLabels.apply(level, chest);
		Tidier.invalidate(guild);
		GuildManager.markDirty();
		GuiUtil.successSound(player);
		openStorage(player, guild, 0);
	}

	// ================================================================ helpers

	private interface PageOpener {
		void open(int page);
	}

	private static void pagination(SimpleGui gui, int current, int pages, PageOpener opener) {
		if (current > 0) {
			gui.setSlot(46, GuiUtil.item(Items.ARROW, Lang.get("gui.page.prev"), List.of(Lang.get("gui.page", current + 1, pages)))
					.setCallback((slot, type, action, g) -> opener.open(current - 1)));
		}
		if (current < pages - 1) {
			gui.setSlot(52, GuiUtil.item(Items.ARROW, Lang.get("gui.page.next"), List.of(Lang.get("gui.page", current + 1, pages)))
					.setCallback((slot, type, action, g) -> opener.open(current + 1)));
		}
	}

	/** "par colonnes" / "par rangées". */
	static String orderName(ChestOrder.Mode mode) {
		return Lang.raw(mode == ChestOrder.Mode.COLUMNS ? "villager.storage.order.columns" : "villager.storage.order.rows");
	}

	/** Used / total slots of the loaded chests. */
	private static int[] fill(@Nullable ServerLevel level, List<StorageChest> chests) {
		int used = 0;
		int total = 0;
		if (level != null) {
			for (StorageChest chest : chests) {
				int[] fill = level.isLoaded(chest.pos) ? Storage.fill(level, chest.pos) : null;
				if (fill != null) {
					used += fill[0];
					total += fill[1];
				}
			}
		}
		return new int[]{used, total};
	}

	/** Containers outside the chest rooms (their budget: storage.maxChests). */
	private static int manualCount(Guild guild) {
		int count = 0;
		for (StorageChest chest : guild.villagers.chests) {
			if (chest.room <= 0) {
				count++;
			}
		}
		return count;
	}

	private static int autoCount(Guild guild) {
		int count = 0;
		for (StorageChest chest : guild.villagers.chests) {
			if (chest.auto) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Deposit (role picker only), every category in config order, the fallback, then "Automatique" (role picker of a
	 * chest of a room).
	 */
	private static List<Option> options(boolean withDeposit, boolean withAuto) {
		List<Option> options = new ArrayList<>();
		if (withDeposit) {
			options.add(new Option(StorageChest.Role.DEPOSIT, "", false));
		}
		StorageCategories categories = Storage.categories();
		for (String name : categories.names()) {
			options.add(new Option(StorageChest.Role.CATEGORY, name, false));
		}
		options.add(new Option(StorageChest.Role.CATEGORY, categories.fallback(), true));
		if (withAuto) {
			options.add(new Option(StorageChest.Role.CATEGORY, "", false, true));
		}
		return options;
	}

	private static Item optionIcon(Option option) {
		if (option.auto()) {
			return Items.COMPARATOR;
		}
		if (option.role() == StorageChest.Role.DEPOSIT) {
			return Items.HOPPER;
		}
		return option.fallback() ? Items.BARREL : icon(option.category());
	}

	/** Lore lines of an option: the role text, or the category examples (word-wrapped). */
	private static List<Component> description(Option option) {
		if (option.auto()) {
			return GuiUtil.lines("villager.gui.role.auto.lore");
		}
		if (option.role() == StorageChest.Role.DEPOSIT) {
			return GuiUtil.lines("villager.gui.role.deposit.lore");
		}
		String text;
		if (option.fallback()) {
			text = Lang.raw("villager.storage.fallback.description");
		} else {
			StorageCategory category = VillagerModule.config().storage.categories.get(option.category());
			text = category == null ? "" : category.description;
		}
		return wrap(text);
	}

	/** Menu icon of a category: its configured icon, else its first item rule, else a chest. */
	static Item icon(String category) {
		StorageCategory def = VillagerModule.config().storage.categories.get(category);
		Item item = def == null || def.iconOrGuess().isEmpty() ? null : ItemUtil.item(def.iconOrGuess());
		return item == null ? Items.CHEST : item;
	}

	/** Number of registered CATEGORY chests per effective category (lower case), deposits under "". */
	private static Map<String, Integer> counts(Guild guild) {
		Map<String, Integer> counts = new HashMap<>();
		for (StorageChest chest : guild.villagers.chests) {
			if (chest.isFree()) {
				continue;
			}
			String key = chest.isDeposit() ? "" : Storage.effectiveCategory(chest).toLowerCase(Locale.ROOT);
			counts.merge(key, 1, Integer::sum);
		}
		return counts;
	}

	/** Word-wraps a description in gray lore lines ('&' colour codes kept). */
	static List<Component> wrap(String text) {
		List<Component> lines = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return lines;
		}
		StringBuilder line = new StringBuilder();
		for (String word : text.trim().split("\\s+")) {
			if (!line.isEmpty() && line.length() + 1 + Lang.strip(Lang.colorize(word)).length() > WRAP) {
				lines.add(Component.literal(Lang.colorize("&7" + line)));
				line.setLength(0);
			}
			if (!line.isEmpty()) {
				line.append(' ');
			}
			line.append(word);
		}
		if (!line.isEmpty()) {
			lines.add(Component.literal(Lang.colorize("&7" + line)));
		}
		return lines;
	}
}
