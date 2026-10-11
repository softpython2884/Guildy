package dev.guildy.shop;

import dev.guildy.core.GoldUtil;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Hooks;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.gui.GuiUtil;
import dev.guildy.structure.BuiltStructure;
import dev.guildy.structure.StructureManager;
import dev.guildy.structure.TextUtil;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Shop menus: categories, then paginated articles. Left click = buy 1 lot, shift + left = 8 lots,
 * right click = sell 1 lot, shift + right = sell every lot. Menu items are virtual (SGUI).
 */
public final class ShopMenus {
	private static final int PER_PAGE = 45;
	private static final int BULK_LOTS = 8;

	private ShopMenus() {
	}

	/** Hook entry point (main menu...): only near the guild's counter when the config requires it. */
	public static void openFromHook(ServerPlayer player, Guild guild) {
		ShopConfig config = ShopModule.config();
		if (config.requireCounter) {
			List<BuiltStructure> counters = StructureManager.builtOf(guild, ShopModule.STRUCTURE);
			if (counters.isEmpty()) {
				Msg.send(player, "shop.counter.none");
				GuiUtil.errorSound(player);
				return;
			}
			if (!nearCounter(player, counters, config.counterDistance)) {
				BlockPos origin = counters.get(0).origin;
				Msg.send(player, "shop.counter.far", origin.getX(), origin.getY(), origin.getZ());
				GuiUtil.errorSound(player);
				return;
			}
		}
		open(player, guild);
	}

	private static boolean nearCounter(ServerPlayer player, List<BuiltStructure> counters, int distance) {
		if (player.level() != GuildyServer.claimLevel()) {
			return false;
		}
		double max = (double) distance * distance;
		for (BuiltStructure counter : counters) {
			for (BlockPos sign : counter.signs) {
				if (player.distanceToSqr(sign.getX() + 0.5, sign.getY() + 0.5, sign.getZ() + 0.5) <= max) {
					return true;
				}
			}
		}
		return false;
	}

	/** Category list. */
	public static void open(ServerPlayer player, Guild guild) {
		List<ShopCategory> categories = ShopModule.config().usable();
		int contentRows = Math.max(1, (categories.size() + 6) / 7);
		int rows = Math.min(6, contentRows + 2);
		SimpleGui gui = GuiUtil.chest(player, rows, Lang.get("shop.gui.title"));
		int max = Math.min(categories.size(), 7 * (rows - 2));
		for (int i = 0; i < max; i++) {
			ShopCategory category = categories.get(i);
			Item icon = ItemUtil.item(category.icon);
			gui.setSlot(9 * (1 + i / 7) + 1 + i % 7, GuiUtil.item(icon == null ? Items.CHEST : icon,
					TextUtil.colored("&e&l" + category.name), GuiUtil.lines("shop.gui.category.lore", category.entries.size()))
					.setCallback((index, type, action, g) -> Safe.run("shop.gui", () -> {
						GuiUtil.clickSound(player);
						new CategoryGui(player, guild, category).open();
					})));
		}
		if (categories.isEmpty()) {
			gui.setSlot(13, GuiUtil.item(Items.BARRIER, Lang.get("shop.gui.empty"), List.of()));
		}
		int bottom = (rows - 1) * 9;
		gui.setSlot(bottom, GuiUtil.back(() -> Hooks.openMainMenu.accept(player)));
		gui.setSlot(bottom + 4, goldIcon(player));
		gui.setSlot(bottom + 8, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	private static GuiElementBuilder goldIcon(ServerPlayer player) {
		return GuiUtil.item(Items.GOLD_INGOT, Lang.get("shop.gui.gold", GoldUtil.count(player)), GuiUtil.lines("shop.gui.help"));
	}

	/** Articles of a category, re-rendered in place after each transaction. */
	private static final class CategoryGui extends SimpleGui {
		private final ServerPlayer viewer;
		private final Guild guild;
		private final ShopCategory category;
		private int page;

		CategoryGui(ServerPlayer player, Guild guild, ShopCategory category) {
			super(GuiUtil.rows(6), player, false);
			this.viewer = player;
			this.guild = guild;
			this.category = category;
			setTitle(TextUtil.colored(Lang.strip(Lang.colorize(category.name))));
			render();
		}

		void render() {
			for (int i = 0; i < getSize(); i++) {
				clearSlot(i);
			}
			int pages = GuiUtil.pages(category.entries.size(), PER_PAGE);
			page = Math.max(0, Math.min(page, pages - 1));
			int start = page * PER_PAGE;
			for (int i = 0; i < PER_PAGE && start + i < category.entries.size(); i++) {
				ShopEntry entry = category.entries.get(start + i);
				GuiElementBuilder icon = entryIcon(entry);
				if (icon != null) {
					setSlot(i, icon.setCallback((index, type, action, g) -> Safe.run("shop.click", () -> click(entry, type))));
				}
			}
			setSlot(45, GuiUtil.back(() -> ShopMenus.open(viewer, guild)));
			if (page > 0) {
				setSlot(48, GuiUtil.item(Items.ARROW, Lang.get("gui.page.prev"), List.of()).setCallback((i, t, a, g) -> Safe.run("shop.page", () -> {
					page--;
					render();
				})));
			}
			setSlot(49, GuiUtil.item(Items.PAPER, Lang.get("gui.page", page + 1, pages), List.of()));
			if (page < pages - 1) {
				setSlot(50, GuiUtil.item(Items.ARROW, Lang.get("gui.page.next"), List.of()).setCallback((i, t, a, g) -> Safe.run("shop.page", () -> {
					page++;
					render();
				})));
			}
			setSlot(53, goldIcon(viewer));
			GuiUtil.fill(this);
		}

		private GuiElementBuilder entryIcon(ShopEntry entry) {
			Item item = ItemUtil.item(entry.item);
			if (item == null) {
				return null;
			}
			boolean locked = guild.level < entry.minLevel;
			int owned = PlainItems.count(viewer, ShopTransactions.matcher(item));
			List<Component> lore = new ArrayList<>();
			lore.add(Lang.get("shop.gui.entry.lot", entry.amount));
			lore.add(entry.buy > 0 ? Lang.get("shop.gui.entry.buy", entry.buy, entry.buy * BULK_LOTS) : Lang.get("shop.gui.entry.no_buy"));
			lore.add(entry.sell > 0 ? Lang.get("shop.gui.entry.sell", entry.sell) : Lang.get("shop.gui.entry.no_sell"));
			lore.add(Lang.get("shop.gui.entry.owned", owned));
			if (locked) {
				lore.add(Component.empty());
				lore.add(Lang.get("shop.gui.entry.locked", entry.minLevel, guild.level));
			} else {
				lore.add(Component.empty());
				if (entry.buy > 0) {
					lore.add(Lang.get("shop.gui.entry.hint_buy", BULK_LOTS));
				}
				if (entry.sell > 0) {
					lore.add(Lang.get("shop.gui.entry.hint_sell"));
				}
			}
			ItemStack display = new ItemStack(item, Math.max(1, Math.min(entry.amount, new ItemStack(item).getMaxStackSize())));
			return GuiUtil.item(display, TextUtil.lang(locked ? "shop.gui.entry.name_locked" : "shop.gui.entry.name", ItemUtil.name(item)), lore);
		}

		private void click(ShopEntry entry, ClickType type) {
			if (GuildManager.of(viewer) != guild) {
				// Left the guild while the menu was open.
				close();
				return;
			}
			ShopTransactions.Result result;
			if (type == ClickType.MOUSE_LEFT) {
				result = ShopTransactions.buy(viewer, guild, entry, 1);
			} else if (type == ClickType.MOUSE_LEFT_SHIFT) {
				result = ShopTransactions.buy(viewer, guild, entry, BULK_LOTS);
			} else if (type == ClickType.MOUSE_RIGHT) {
				result = ShopTransactions.sell(viewer, guild, entry, false);
			} else if (type == ClickType.MOUSE_RIGHT_SHIFT) {
				result = ShopTransactions.sell(viewer, guild, entry, true);
			} else {
				return;
			}
			if (result.success()) {
				Msg.actionBar(viewer, result.message());
				Msg.sound(viewer, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, type.isRight ? 0.9f : 1.3f);
			} else {
				Msg.sendRaw(viewer, result.message());
				GuiUtil.errorSound(viewer);
			}
			render();
		}
	}
}
