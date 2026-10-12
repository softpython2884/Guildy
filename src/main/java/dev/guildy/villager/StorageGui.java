package dev.guildy.villager;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
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
import java.util.List;
import java.util.Map;

/**
 * Storage menus: the registered containers ("Stockage": left click = change the role, right click = remove), the
 * storage categories (icon, examples, chests; click = show them) and the role picker shown after selecting a container.
 * Every button re-checks the VILLAGERS permission.
 */
final class StorageGui {
	private static final int PER_PAGE = 45;
	/** Characters per lore line of the category descriptions. */
	private static final int WRAP = 36;

	private StorageGui() {
	}

	/** One choice of the role picker / categories menu: the deposit, a category or the fallback. */
	private record Option(StorageChest.Role role, String category, boolean fallback) {
	}

	// ================================================================ registered containers

	static void openStorage(ServerPlayer player, Guild guild, int page) {
		ServerLevel level = GuildyServer.claimLevel();
		if (level != null) {
			Storage.cleanup(level, guild);
		}
		List<StorageChest> chests = new ArrayList<>(guild.villagers.chests);
		int pages = GuiUtil.pages(chests.size(), PER_PAGE);
		int current = Math.max(0, Math.min(page, pages - 1));
		SimpleGui gui = VillagerGui.chest(player, 6, Lang.get("villager.gui.storage.title", chests.size()));
		for (int i = 0; i < PER_PAGE; i++) {
			int index = current * PER_PAGE + i;
			if (index >= chests.size()) {
				break;
			}
			StorageChest chest = chests.get(index);
			gui.setSlot(i, chestIcon(level, chest).setCallback((slot, type, action, g) -> {
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
		gui.setSlot(45, GuiUtil.back(() -> VillagerGui.openList(player, guild, 0)));
		if (current > 0) {
			gui.setSlot(46, GuiUtil.item(Items.ARROW, Lang.get("gui.page.prev"), List.of(Lang.get("gui.page", current + 1, pages)))
					.setCallback((slot, type, action, g) -> openStorage(player, guild, current - 1)));
		}
		StorageCategories categories = Storage.categories();
		gui.setSlot(47, GuiUtil.item(Items.BOOKSHELF, Lang.get("villager.gui.storage.categories.button"),
				GuiUtil.lines("villager.gui.storage.categories.button.lore", categories.names().size(), categories.fallback()))
				.setCallback((slot, type, action, g) -> {
					GuiUtil.clickSound(player);
					openCategories(player, guild, 0);
				}));
		gui.setSlot(48, GuiUtil.item(Items.SPYGLASS, Lang.get("villager.gui.storage.show"),
				GuiUtil.lines("villager.gui.storage.show.lore", VillagerModule.config().storage.showSeconds))
				.setCallback((slot, type, action, g) -> {
					if (StorageVisualizer.show(player, guild, null) > 0) {
						g.close();
						GuiUtil.successSound(player);
					} else {
						GuiUtil.errorSound(player);
					}
				}));
		gui.setSlot(49, GuiUtil.item(Items.CHEST, Lang.get("villager.gui.storage.add"), GuiUtil.lines("villager.gui.storage.add.lore",
				VillagerInteractions.SELECTION_SECONDS)).glow().setCallback((slot, type, action, g) -> {
			if (!VillagerGui.checkPermission(player, guild)) {
				return;
			}
			if (guild.villagers.chests.size() >= VillagerModule.config().storage.maxChests) {
				Msg.send(player, "villager.storage.limit", VillagerModule.config().storage.maxChests);
				GuiUtil.errorSound(player);
				return;
			}
			g.close();
			GuiUtil.clickSound(player);
			VillagerInteractions.startSelection(player, guild);
		}));
		gui.setSlot(50, heldIcon(player, guild).setCallback((slot, type, action, g) -> {
			GuiUtil.clickSound(player);
			openStorage(player, guild, current);
		}));
		gui.setSlot(51, GuiUtil.item(Items.PAPER, Lang.get("villager.gui.help"),
				GuiUtil.lines("villager.gui.storage.help.text", categories.fallback())));
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
						GuildManager.markDirty();
						Msg.send(player, "villager.storage.removed");
					}
					openStorage(player, guild, page);
				},
				() -> openStorage(player, guild, page));
	}

	private static GuiElementBuilder chestIcon(@Nullable ServerLevel level, StorageChest chest) {
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("villager.gui.storage.entry.lore.pos", chest.pos.getX(), chest.pos.getY(), chest.pos.getZ()));
		if (level == null || !level.isLoaded(chest.pos)) {
			lore.add(Lang.get("villager.gui.storage.entry.lore.unloaded"));
		} else {
			int[] fill = Storage.fill(level, chest.pos);
			lore.add(fill == null ? Lang.get("villager.gui.storage.entry.lore.missing")
					: Lang.get("villager.gui.storage.entry.lore.fill", fill[0], fill[1]));
		}
		if (!chest.isDeposit() && !chest.category.isBlank() && Storage.categories().canonical(chest.category) == null) {
			lore.addAll(GuiUtil.lines("villager.gui.storage.entry.lore.stale", chest.category, Storage.effectiveCategory(chest)));
		}
		lore.addAll(GuiUtil.lines("villager.gui.storage.entry.lore.click"));
		if (chest.isDeposit()) {
			return GuiUtil.item(Items.HOPPER, Lang.get("villager.gui.storage.entry.deposit"), lore);
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
		lore.add(Lang.get("villager.gui.storage.held.chests", counts(guild).getOrDefault(match.category().toLowerCase(java.util.Locale.ROOT), 0)));
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
		List<Option> options = options(false);
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
			int count = counts.getOrDefault(option.category().toLowerCase(java.util.Locale.ROOT), 0);
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
		if (current > 0) {
			gui.setSlot(46, GuiUtil.item(Items.ARROW, Lang.get("gui.page.prev"), List.of(Lang.get("gui.page", current + 1, pages)))
					.setCallback((slot, type, action, g) -> openCategories(player, guild, current - 1)));
		}
		if (current < pages - 1) {
			gui.setSlot(52, GuiUtil.item(Items.ARROW, Lang.get("gui.page.next"), List.of(Lang.get("gui.page", current + 1, pages)))
					.setCallback((slot, type, action, g) -> openCategories(player, guild, current + 1)));
		}
		gui.setSlot(53, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	// ================================================================ role picker

	/** Role choice after right-clicking a container in selection mode (or left-clicking it in the storage menu). */
	static void openRolePicker(ServerPlayer player, Guild guild, BlockPos pos, int page) {
		ServerLevel level = GuildyServer.claimLevel();
		StorageChest existing = level == null ? guild.villagers.chestAt(pos) : Storage.registered(level, guild, pos);
		List<Option> options = options(true);
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
			boolean isCurrent = existing != null && existing.role == option.role()
					&& (option.role() == StorageChest.Role.DEPOSIT || Storage.effectiveCategory(existing).equalsIgnoreCase(option.category()));
			List<Component> lore = new ArrayList<>(description(option));
			if (option.role() == StorageChest.Role.CATEGORY) {
				lore.add(Component.empty());
				lore.add(Lang.get("villager.gui.category.chests", counts.getOrDefault(option.category().toLowerCase(java.util.Locale.ROOT), 0)));
			}
			lore.add(Component.empty());
			lore.add(isCurrent ? Lang.get("villager.gui.role.current")
					: Lang.get(option.role() == StorageChest.Role.DEPOSIT ? "villager.gui.role.choose_deposit" : "villager.gui.role.choose"));
			Component name = option.role() == StorageChest.Role.DEPOSIT ? Lang.get("villager.gui.role.deposit")
					: Lang.get(option.fallback() ? "villager.gui.role.fallback" : "villager.gui.role.category", option.category());
			GuiElementBuilder button = GuiUtil.item(optionIcon(option), name, lore);
			if (isCurrent) {
				button.glow();
			}
			gui.setSlot(i, button.setCallback((slot, type, action, g) -> assign(player, guild, pos, option.role(), option.category())));
		}
		List<Component> info = existing == null ? GuiUtil.lines("villager.gui.role.info.new")
				: List.of(Lang.get("villager.gui.role.info.current", Storage.roleName(existing)));
		gui.setSlot(49, GuiUtil.item(Items.CHEST, Lang.get("villager.gui.role.info", pos.getX(), pos.getY(), pos.getZ()), info));
		gui.setSlot(45, GuiUtil.back(() -> openStorage(player, guild, 0)));
		if (current > 0) {
			gui.setSlot(46, GuiUtil.item(Items.ARROW, Lang.get("gui.page.prev"), List.of(Lang.get("gui.page", current + 1, pages)))
					.setCallback((slot, type, action, g) -> openRolePicker(player, guild, pos, current - 1)));
		}
		if (current < pages - 1) {
			gui.setSlot(52, GuiUtil.item(Items.ARROW, Lang.get("gui.page.next"), List.of(Lang.get("gui.page", current + 1, pages)))
					.setCallback((slot, type, action, g) -> openRolePicker(player, guild, pos, current + 1)));
		}
		gui.setSlot(53, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	private static void assign(ServerPlayer player, Guild guild, BlockPos pos, StorageChest.Role role, String category) {
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
			chest.role = role;
			chest.category = category;
			Msg.send(player, "villager.storage.updated", Storage.roleName(chest));
		} else {
			if (guild.villagers.chests.size() >= VillagerModule.config().storage.maxChests) {
				Msg.send(player, "villager.storage.limit", VillagerModule.config().storage.maxChests);
				player.closeContainer();
				return;
			}
			chest = new StorageChest(pos.immutable(), role, category);
			guild.villagers.chests.add(chest);
			Msg.send(player, "villager.storage.added", Storage.roleName(chest), pos.getX(), pos.getY(), pos.getZ());
		}
		StorageLabels.apply(level, chest);
		GuildManager.markDirty();
		GuiUtil.successSound(player);
		openStorage(player, guild, 0);
	}

	// ================================================================ helpers

	/** Deposit (role picker only), every category in config order, then the fallback. */
	private static List<Option> options(boolean withDeposit) {
		List<Option> options = new ArrayList<>();
		if (withDeposit) {
			options.add(new Option(StorageChest.Role.DEPOSIT, "", false));
		}
		StorageCategories categories = Storage.categories();
		for (String name : categories.names()) {
			options.add(new Option(StorageChest.Role.CATEGORY, name, false));
		}
		options.add(new Option(StorageChest.Role.CATEGORY, categories.fallback(), true));
		return options;
	}

	private static Item optionIcon(Option option) {
		if (option.role() == StorageChest.Role.DEPOSIT) {
			return Items.HOPPER;
		}
		return option.fallback() ? Items.BARREL : icon(option.category());
	}

	/** Lore lines of an option: the role text, or the category examples (word-wrapped). */
	private static List<Component> description(Option option) {
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
			String key = chest.isDeposit() ? "" : Storage.effectiveCategory(chest).toLowerCase(java.util.Locale.ROOT);
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
