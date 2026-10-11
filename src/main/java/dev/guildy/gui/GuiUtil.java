package dev.guildy.gui;

import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shared look & feel for all Guildy menus (chest GUIs via SGUI): named items with non-italic lore,
 * glass-pane background, consistent back/close buttons, confirmation and text-input dialogs.
 */
public final class GuiUtil {
	private GuiUtil() {
	}

	/** Chest menu type for a row count (1..6). */
	public static MenuType<?> rows(int rows) {
		return switch (Math.max(1, Math.min(6, rows))) {
			case 1 -> MenuType.GENERIC_9x1;
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
	}

	public static SimpleGui chest(ServerPlayer player, int rows, Component title) {
		SimpleGui gui = new SimpleGui(rows(rows), player, false);
		gui.setTitle(title);
		return gui;
	}

	/** Item button with a name and lore (lore lines may contain '\n' to split). */
	public static GuiElementBuilder item(Item item, Component name, List<? extends Component> lore) {
		GuiElementBuilder builder = new GuiElementBuilder(item).setName(noItalic(name)).hideDefaultTooltip();
		for (Component line : lore) {
			builder.addLoreLine(noItalic(line));
		}
		return builder;
	}

	public static GuiElementBuilder item(ItemStack stack, Component name, List<? extends Component> lore) {
		GuiElementBuilder builder = GuiElementBuilder.from(stack.copy()).setName(noItalic(name)).hideDefaultTooltip();
		for (Component line : lore) {
			builder.addLoreLine(noItalic(line));
		}
		return builder;
	}

	/** Lang lines: splits the translated text on '\n' (use in lang values for multi-line lores). */
	public static List<Component> lines(String key, Object... args) {
		List<Component> list = new ArrayList<>();
		for (String part : Lang.raw(key, args).split("\n")) {
			list.add(Component.literal(part));
		}
		return list;
	}

	public static Component noItalic(Component component) {
		return Component.empty().append(component).withStyle(s -> s.withItalic(false));
	}

	/** Fills empty slots with a nameless gray pane. */
	public static void fill(SimpleGui gui) {
		for (int i = 0; i < gui.getSize(); i++) {
			if (gui.getGuiElement(i) == null) {
				gui.setSlot(i, new GuiElementBuilder(Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY)).setName(Component.literal(" ")).hideTooltip());
			}
		}
	}

	public static GuiElementBuilder back(Runnable action) {
		return item(Items.ARROW, Lang.get("gui.back"), List.of()).setCallback((index, type, action2, gui) -> {
			clickSound(gui.getPlayer());
			Safe.run("gui.back", action);
		});
	}

	public static GuiElementBuilder close() {
		return item(Items.BARRIER, Lang.get("gui.close"), List.of()).setCallback((index, type, action, gui) -> gui.close());
	}

	public static void clickSound(ServerPlayer player) {
		Msg.sound(player, SoundEvents.UI_BUTTON_CLICK, 0.5f, 1.0f);
	}

	public static void errorSound(ServerPlayer player) {
		Msg.sound(player, SoundEvents.VILLAGER_NO, 0.6f, 1.0f);
	}

	public static void successSound(ServerPlayer player) {
		Msg.sound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
	}

	/** Yes/No dialog (3 rows). */
	public static void confirm(ServerPlayer player, Component title, List<Component> question, Runnable onYes, Runnable onNo) {
		SimpleGui gui = chest(player, 3, title);
		gui.setSlot(13, item(Items.PAPER, title, question));
		gui.setSlot(11, item(Items.CONCRETE.pick(DyeColor.LIME), Lang.get("gui.confirm.yes"), List.of()).setCallback((i, t, a, g) -> {
			g.close();
			clickSound(player);
			Safe.run("gui.confirm", onYes);
		}));
		gui.setSlot(15, item(Items.CONCRETE.pick(DyeColor.RED), Lang.get("gui.confirm.no"), List.of()).setCallback((i, t, a, g) -> {
			g.close();
			clickSound(player);
			Safe.run("gui.confirm", onNo);
		}));
		fill(gui);
		gui.open();
	}

	/**
	 * Text input via an anvil: the player types in the name field and clicks the result item to validate.
	 */
	public static void textInput(ServerPlayer player, Component title, String defaultText, Consumer<String> onSubmit, Runnable onCancel) {
		AnvilInputGui gui = new AnvilInputGui(player, false) {
			@Override
			public void onManualClose() {
				Safe.run("gui.input.cancel", onCancel);
			}
		};
		gui.setTitle(title);
		gui.setDefaultInputValue(defaultText);
		gui.setSlot(2, item(Items.DYE.pick(DyeColor.LIME), Lang.get("gui.input.confirm"), List.of()).setCallback((i, t, a, g) -> {
			String input = ((AnvilInputGui) g).getInput();
			g.close();
			clickSound(player);
			Safe.run("gui.input", () -> onSubmit.accept(input == null ? "" : input.trim()));
		}));
		gui.open();
	}

	/** Paging helper: number of pages for {@code total} entries with {@code perPage} per page. */
	public static int pages(int total, int perPage) {
		return Math.max(1, (total + perPage - 1) / perPage);
	}
}
