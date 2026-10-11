package dev.guildy.gui;

import dev.guildy.command.GuildActions.Denial;
import dev.guildy.command.GuildRules;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Base of the guild menus: a chest GUI bound to the viewer's guild, re-rendered in place after every change.
 * Clicks are isolated by {@link Safe}, only plain left/right clicks are accepted, and every click re-validates
 * the viewer's membership and the action's rules before running it.
 */
public abstract class GuildGui extends SimpleGui {
	private static final int DENIAL_WIDTH = 34;

	/** Guild this menu belongs to (null for the "no guild" menu). */
	protected final @Nullable UUID guildId;
	protected final int rows;

	protected GuildGui(ServerPlayer player, int rows, @Nullable Guild guild) {
		super(GuiUtil.rows(rows), player, false);
		this.rows = rows;
		this.guildId = guild == null ? null : guild.id;
	}

	@FunctionalInterface
	protected interface ClickHandler {
		void click(@Nullable Guild guild, ClickType type);
	}

	protected abstract Component title(@Nullable Guild guild);

	/** Places the items; the background is filled afterwards. */
	protected abstract void build(@Nullable Guild guild);

	/** The viewer's guild if it is still the one this menu was opened for. */
	protected @Nullable Guild guild() {
		if (guildId == null) {
			return null;
		}
		Guild guild = GuildManager.of(player);
		return guild != null && guild.id.equals(guildId) ? guild : null;
	}

	/**
	 * Redraws the whole menu. When the viewer no longer belongs to the guild, opens the right top menu instead.
	 *
	 * @return false if another menu was opened instead
	 */
	public boolean render() {
		Guild guild = guild();
		if (guildId != null && guild == null) {
			GuildMenus.open(player);
			return false;
		}
		for (int i = 0; i < getVirtualSize(); i++) {
			clearSlot(i);
		}
		build(guild);
		GuiUtil.fill(this);
		// After the items: a title change re-sends the whole screen with the new content.
		Component title = title(guild);
		if (getTitle() == null || !getTitle().equals(title)) {
			setTitle(title);
		}
		return true;
	}

	/** Builds and opens (or refreshes) the menu. */
	public void show() {
		Safe.run("menu.show", () -> {
			if (render() && !isOpen()) {
				open();
			}
		});
	}

	// ---------------------------------------------------------------- click plumbing

	/** Click callback: plain clicks only, guild re-validated, errors isolated (logged + reported). */
	protected GuiElement.ClickCallback onClick(String context, ClickHandler handler) {
		return (index, type, action, gui) -> {
			if (!(type.isLeft || type.isRight) || type.isDragging) {
				return;
			}
			try {
				Guild guild = guild();
				if (guildId != null && guild == null) {
					GuildMenus.open(player);
					return;
				}
				handler.click(guild, type);
			} catch (Throwable t) {
				Safe.log("menu." + context, t);
				Msg.send(player, "error.internal");
			}
		};
	}

	/** Adds the reason of a refusal (red, wrapped) at the end of the lore. */
	protected static GuiElementBuilder denied(GuiElementBuilder icon, @Nullable Denial denial) {
		if (denial != null) {
			icon.addLoreLine(Component.empty());
			for (String line : GuildRules.wrapLegacy(denial.text().getString(), DENIAL_WIDTH)) {
				icon.addLoreLine(GuiUtil.noItalic(Component.literal(line)));
			}
		}
		return icon;
	}

	/**
	 * Button whose rules are checked at render time (reason shown in the lore) and again at click time.
	 * Plays a click sound, runs {@code onClick}, then re-renders if this menu is still open.
	 */
	protected void button(int slot, GuiElementBuilder icon, @Nullable Supplier<@Nullable Denial> check, Runnable onClick) {
		setSlot(slot, denied(icon, check == null ? null : check.get()).setCallback(onClick("button", (guild, type) -> {
			Denial denial = check == null ? null : check.get();
			if (denial != null) {
				GuiUtil.errorSound(player);
				denial.send(player);
			} else {
				GuiUtil.clickSound(player);
				onClick.run();
			}
			if (isOpen()) {
				render();
			}
		})));
	}

	/** Like {@link #button} for state-changing actions: success / error sound from the result, then re-render. */
	protected void action(int slot, GuiElementBuilder icon, @Nullable Supplier<@Nullable Denial> check, BooleanSupplier action) {
		setSlot(slot, denied(icon, check == null ? null : check.get()).setCallback(onClick("action", (guild, type) -> {
			Denial denial = check == null ? null : check.get();
			if (denial != null) {
				GuiUtil.errorSound(player);
				denial.send(player);
			} else if (action.getAsBoolean()) {
				GuiUtil.successSound(player);
			} else {
				GuiUtil.errorSound(player);
			}
			if (isOpen()) {
				render();
			}
		})));
	}

	/** Back button at the bottom-left and close button at the bottom-center. */
	protected void bottomBar(@Nullable Runnable back) {
		int first = rows * 9 - 9;
		if (back != null) {
			setSlot(first, GuiUtil.back(back));
		}
		setSlot(first + 4, GuiUtil.close());
	}

	/** Previous / next page arrows around the close button. */
	protected void pager(int page, int pages, IntConsumer goTo) {
		int first = rows * 9 - 9;
		if (page > 0) {
			setSlot(first + 3, GuiUtil.item(Items.ARROW, Lang.get("gui.page.prev"), List.of(Lang.get("gui.page", page, pages)))
					.setCallback(onClick("page", (guild, type) -> {
						GuiUtil.clickSound(player);
						goTo.accept(page - 1);
					})));
		}
		if (page < pages - 1) {
			setSlot(first + 5, GuiUtil.item(Items.ARROW, Lang.get("gui.page.next"), List.of(Lang.get("gui.page", page + 2, pages)))
					.setCallback(onClick("page", (guild, type) -> {
						GuiUtil.clickSound(player);
						goTo.accept(page + 1);
					})));
		}
	}

	// ---------------------------------------------------------------- item helpers

	protected static GuiElementBuilder icon(Item item, String nameKey, List<Component> lore, Object... nameArgs) {
		return GuiUtil.item(item, Lang.get(nameKey, nameArgs), lore);
	}

	protected static GuiElementBuilder icon(Item item, Component name, List<Component> lore) {
		return GuiUtil.item(item, name, lore);
	}

	protected static GuiElementBuilder icon(ItemStack stack, Component name, List<Component> lore) {
		return GuiUtil.item(stack, name, lore);
	}

	/** Player head with the skin of {@code id} (resolved profile when online). */
	protected static GuiElementBuilder head(UUID id, Component name, List<Component> lore) {
		GuiElementBuilder builder = GuiUtil.item(Items.PLAYER_HEAD, name, lore);
		ServerPlayer online = GuildyServer.player(id);
		builder.setProfile(online != null ? ResolvableProfile.createResolved(online.getGameProfile()) : ResolvableProfile.createUnresolved(id));
		return builder;
	}
}
