package dev.guildy.scroll;

import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.gui.GuiUtil;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Pages menu of a grimoire (this very stack, which must stay in the player's inventory): the grimoire itself, its four
 * pages (left click = select the page, right click = erase it after a confirmation, the scroll is lost), shift-click on a
 * scroll of the inventory = fuse it. Redrawn when the grimoire changes while the menu is open (a scroll right-clicked onto
 * it in the inventory part).
 */
final class GrimoireMenu {
	private static final int ROWS = 3;
	private static final int SLOT_BOOK = 4;
	private static final int[] SLOT_PAGES = {10, 12, 14, 16};
	private static final int SLOT_HELP = 18;
	private static final int SLOT_CLOSE = 22;

	private GrimoireMenu() {
	}

	/** Opens the menu of {@code stack} (refused when it is no grimoire of the player's inventory). */
	static void open(ServerPlayer player, ItemStack stack) {
		if (!GrimoireItems.isGrimoire(stack) || !Grimoires.carries(player, stack)) {
			Msg.send(player, "grimoire.menu.gone");
			return;
		}
		// Brought up to date in place (a grimoire to rebuild on another base item is replaced by the inventory scan).
		GrimoireItems.refreshed(stack);
		new View(player, stack).open();
	}

	private static final class View {
		private final ServerPlayer player;
		private final ItemStack stack;
		private final SimpleGui gui;
		private GrimoireRules.Pages shown = GrimoireRules.Pages.EMPTY;
		private int ticks;

		View(ServerPlayer player, ItemStack stack) {
			this.player = player;
			this.stack = stack;
			this.gui = new SimpleGui(GuiUtil.rows(ROWS), player, false) {
				@Override
				public boolean onAnyClick(int index, ClickType type, ContainerInput action) {
					if (index >= getSize() && type.shift) {
						// Shift-click in the inventory: a scroll is fused, nothing else moves.
						Safe.run("scroll.grimoire.menu.fuse", () -> fuseFromInventory(index));
						return false;
					}
					return super.onAnyClick(index, type, action);
				}

				@Override
				public void onTick() {
					if (++ticks % 10 == 0) {
						Safe.run("scroll.grimoire.menu.tick", View.this::redrawIfChanged);
					}
				}
			};
			gui.setTitle(Lang.get("grimoire.menu.title"));
		}

		void open() {
			render();
			gui.open();
		}

		private boolean present() {
			if (GrimoireItems.isGrimoire(stack) && Grimoires.carries(player, stack)) {
				return true;
			}
			Msg.send(player, "grimoire.menu.gone");
			gui.close();
			return false;
		}

		private void redrawIfChanged() {
			if (!present()) {
				return;
			}
			if (!GrimoireItems.pages(stack).equals(shown)) {
				render();
			}
		}

		private void render() {
			GrimoireRules.Pages pages = GrimoireItems.pages(stack);
			shown = pages;
			ItemStack book = GrimoireItems.create(pages);
			if (book.isEmpty()) {
				book = new ItemStack(Items.ENCHANTED_BOOK);
			}
			book.remove(DataComponents.CUSTOM_DATA);
			gui.setSlot(SLOT_BOOK, GuiElementBuilder.from(book));
			for (int page = 0; page < GrimoireRules.PAGES; page++) {
				gui.setSlot(SLOT_PAGES[page], page(pages, page));
			}
			gui.setSlot(SLOT_HELP, GuiUtil.item(Items.BOOK, Lang.get("grimoire.menu.help"), GuiUtil.lines("grimoire.menu.help.lore",
					GrimoireRules.PAGES)));
			gui.setSlot(SLOT_CLOSE, GuiUtil.close());
			GuiUtil.fill(gui);
		}

		private GuiElementBuilder page(GrimoireRules.Pages pages, int page) {
			String id = pages.spell(page);
			if (id.isEmpty()) {
				return GuiUtil.item(Items.PAPER, Lang.get("grimoire.menu.blank", page + 1), GuiUtil.lines("grimoire.menu.blank.lore"));
			}
			ScrollConfig.ScrollDef def = ScrollModule.config().scroll(id);
			if (def == null) {
				return GuiUtil.item(Items.PAPER, Lang.get("grimoire.menu.unknown", page + 1, id), GuiUtil.lines("grimoire.menu.unknown.lore"))
						.setCallback((index, type, action, g) -> {
							if (type.isRight) {
								confirmErase(page, id);
							}
						});
			}
			boolean selected = page == pages.selected();
			List<Component> lore = new ArrayList<>();
			lore.add(Lang.get("scroll.item.header", ScrollItems.rarityName(def.rarity())));
			lore.add(Component.empty());
			lore.add(Lang.get("scroll.item.spell", Lang.raw(def.spell().langKey() + ".name")));
			for (String line : ScrollItems.spellText(def, ScrollModule.config().grimoire.powerMultiplier).split("\n")) {
				lore.add(Lang.get("scroll.item.spell_line", line));
			}
			lore.add(Component.empty());
			lore.add(Lang.get("grimoire.item.cost", GrimoireItems.foodCost(def)));
			lore.add(Component.empty());
			lore.addAll(GuiUtil.lines(selected ? "grimoire.menu.page.selected" : "grimoire.menu.page.select"));
			lore.addAll(GuiUtil.lines("grimoire.menu.page.erase"));
			ItemStack display = ScrollItems.create(def, 1);
			if (display.isEmpty()) {
				display = new ItemStack(Items.PAPER);
			}
			display.remove(DataComponents.CUSTOM_DATA);
			GuiElementBuilder element = GuiElementBuilder.from(display)
					.setName(Lang.get(selected ? "grimoire.menu.page_selected" : "grimoire.menu.page", page + 1, GrimoireItems.spellName(def)))
					.setLore(lore)
					.glow(selected)
					.hideDefaultTooltip();
			return element.setCallback((index, type, action, g) -> {
				if (type.isLeft && !type.shift) {
					select(page);
				} else if (type.isRight && !type.shift) {
					confirmErase(page, def.id);
				}
			});
		}

		private void select(int page) {
			if (!present()) {
				return;
			}
			GrimoireRules.Pages pages = GrimoireItems.pages(stack);
			if (pages.spell(page).isEmpty() || pages.selected() == page) {
				return;
			}
			GrimoireItems.apply(stack, pages.select(page));
			Msg.sound(player, SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
			render();
		}

		private void confirmErase(int page, String id) {
			if (!present()) {
				return;
			}
			ScrollConfig.ScrollDef def = ScrollModule.config().scroll(id);
			String name = def == null ? id : GrimoireItems.spellName(def);
			GuiUtil.confirm(player, Lang.get("grimoire.menu.erase.title"), GuiUtil.lines("grimoire.menu.erase.question", page + 1, name),
					() -> erase(page, id), () -> GrimoireMenu.open(player, stack));
		}

		private void erase(int page, String id) {
			if (!GrimoireItems.isGrimoire(stack) || !Grimoires.carries(player, stack)) {
				Msg.send(player, "grimoire.menu.gone");
				return;
			}
			GrimoireRules.Pages pages = GrimoireItems.pages(stack);
			if (id.equals(pages.spell(page))) {
				GrimoireItems.apply(stack, GrimoireRules.erase(pages, page));
				Msg.send(player, "grimoire.menu.erased", page + 1);
				Msg.sound(player, SoundEvents.BOOK_PUT, 1.0F, 0.8F);
			}
			GrimoireMenu.open(player, stack);
		}

		private void fuseFromInventory(int index) {
			if (!present()) {
				return;
			}
			Slot slot = gui.getSlotRedirectOrPlayer(index);
			if (slot == null || !slot.hasItem()) {
				return;
			}
			ItemStack item = slot.getItem();
			if (!ScrollItems.isScroll(item)) {
				if (!GrimoireItems.isGrimoire(item)) {
					Msg.send(player, "grimoire.menu.not_scroll");
					GuiUtil.errorSound(player);
				}
				return;
			}
			if (Grimoires.fuse(player, stack, ScrollItems.idOf(item))) {
				item.shrink(1);
				slot.setChanged();
			}
			render();
		}
	}
}
