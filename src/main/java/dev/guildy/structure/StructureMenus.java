package dev.guildy.structure;

import dev.guildy.core.Hooks;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import dev.guildy.gui.GuiUtil;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Structures menu: list of definitions with their status, detail page with the plan grid, unlock / plan /
 * dismantle buttons.
 */
public final class StructureMenus {
	private static final int PER_PAGE = 28;

	private StructureMenus() {
	}

	public static void openList(ServerPlayer player, Guild guild) {
		openList(player, guild, 0);
	}

	private static void openList(ServerPlayer player, Guild guild, int page) {
		List<StructureType> types = new ArrayList<>(StructureManager.types());
		int pages = GuiUtil.pages(types.size(), PER_PAGE);
		int current = Math.max(0, Math.min(page, pages - 1));
		int count = Math.min(PER_PAGE, types.size() - current * PER_PAGE);
		int contentRows = Math.max(1, (count + 6) / 7);
		int rows = Math.min(6, contentRows + 2);
		SimpleGui gui = GuiUtil.chest(player, rows, Lang.get("structure.gui.title"));
		for (int i = 0; i < count; i++) {
			StructureType type = types.get(current * PER_PAGE + i);
			int slot = 9 * (1 + i / 7) + 1 + i % 7;
			gui.setSlot(slot, listIcon(guild, type).setCallback((index, click, action, g) -> Safe.run("structure.gui", () -> {
				GuiUtil.clickSound(player);
				openDetail(player, guild, type);
			})));
		}
		if (types.isEmpty()) {
			gui.setSlot(13, GuiUtil.item(Items.BARRIER, Lang.get("structure.gui.none"), List.of()));
		}
		int bottom = (rows - 1) * 9;
		gui.setSlot(bottom, GuiUtil.back(() -> Hooks.openMainMenu.accept(player)));
		gui.setSlot(bottom + 4, GuiUtil.item(Items.BOOK, Lang.get("structure.gui.help.name"),
				GuiUtil.lines("structure.gui.help.lore", guild.treasury)));
		if (current > 0) {
			gui.setSlot(bottom + 3, GuiUtil.item(Items.ARROW, Lang.get("gui.page.prev"), List.of())
					.setCallback((i, c, a, g) -> Safe.run("structure.gui", () -> openList(player, guild, current - 1))));
		}
		if (current < pages - 1) {
			gui.setSlot(bottom + 5, GuiUtil.item(Items.ARROW, Lang.get("gui.page.next"), List.of())
					.setCallback((i, c, a, g) -> Safe.run("structure.gui", () -> openList(player, guild, current + 1))));
		}
		gui.setSlot(bottom + 8, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	private static GuiElementBuilder listIcon(Guild guild, StructureType type) {
		StructureDefinition def = type.def();
		List<Component> lore = new ArrayList<>();
		for (String line : def.description) {
			lore.add(TextUtil.colored(line));
		}
		lore.add(Component.empty());
		lore.add(status(guild, type));
		lore.add(levelLine(guild, def));
		if (!StructureManager.isUnlocked(guild, def)) {
			lore.add(Lang.get("structure.gui.cost", def.unlockCost));
		}
		lore.add(Component.empty());
		lore.add(Lang.get("structure.gui.click_detail"));
		GuiElementBuilder builder = GuiUtil.item(icon(def), type.name(), lore);
		if (StructureManager.isBuilt(guild, def.id)) {
			builder.glow();
		}
		return builder;
	}

	private static Item icon(StructureDefinition def) {
		Item item = ItemUtil.item(def.icon);
		return item == null ? Items.PAPER : item;
	}

	private static Component status(Guild guild, StructureType type) {
		List<BuiltStructure> built = StructureManager.builtOf(guild, type.id());
		if (!built.isEmpty()) {
			BlockPos origin = built.get(0).origin;
			Component line = Lang.get("structure.gui.status.built", origin.getX(), origin.getY(), origin.getZ());
			return built.size() > 1 ? Component.empty().append(line).append(Lang.get("structure.gui.status.more", built.size() - 1)) : line;
		}
		if (StructureManager.isUnlocked(guild, type.def())) {
			return Lang.get("structure.gui.status.unlocked");
		}
		return Lang.get("structure.gui.status.locked");
	}

	private static Component levelLine(Guild guild, StructureDefinition def) {
		return Lang.get(guild.level >= def.minLevel ? "structure.gui.level.ok" : "structure.gui.level.missing", def.minLevel, guild.level);
	}

	// ------------------------------------------------------------------ detail

	public static void openDetail(ServerPlayer player, Guild guild, StructureType type) {
		StructureDefinition def = type.def();
		StructurePattern pattern = type.pattern();
		SimpleGui gui = GuiUtil.chest(player, 6, Lang.get("structure.gui.detail.title", type.plainName()));

		// Plan grid, as seen from the front.
		int colOffset = (9 - pattern.width()) / 2;
		for (int row = 0; row < pattern.height(); row++) {
			for (int col = 0; col < pattern.width(); col++) {
				gui.setSlot(row * 9 + colOffset + col, cellIcon(type, col, row));
			}
		}

		boolean canManage = guild.can(player.getUUID(), GuildPermission.STRUCTURES);
		boolean unlocked = StructureManager.isUnlocked(guild, def);
		List<BuiltStructure> built = StructureManager.builtOf(guild, def.id);

		gui.setSlot(40, GuiUtil.item(icon(def), type.name(), infoLore(guild, type)));
		gui.setSlot(45, GuiUtil.back(() -> openList(player, guild)));

		if (!unlocked) {
			List<Component> lore = new ArrayList<>(GuiUtil.lines("structure.gui.unlock.lore", def.unlockCost, guild.treasury));
			lore.add(levelLine(guild, def));
			if (!canManage) {
				lore.add(Lang.get("structure.gui.need_permission"));
			}
			gui.setSlot(48, GuiUtil.item(Items.GOLD_INGOT, Lang.get("structure.gui.unlock.name"), lore)
					.setCallback((i, c, a, g) -> Safe.run("structure.gui.unlock", () -> {
						GuiUtil.clickSound(player);
						confirmUnlock(player, guild, type);
					})));
		} else {
			gui.setSlot(48, GuiUtil.item(Items.PAPER, Lang.get("structure.gui.plan.name"), GuiUtil.lines("structure.gui.plan.lore"))
					.glow()
					.setCallback((i, c, a, g) -> Safe.run("structure.gui.plan", () -> {
						Component error = StructureManager.givePlan(player, guild, type);
						if (error != null) {
							Msg.sendRaw(player, error);
							GuiUtil.errorSound(player);
						} else {
							Msg.sendRaw(player, TextUtil.lang("structure.plan.received", type.name()));
							GuiUtil.successSound(player);
							g.close();
						}
					})));
		}

		if (!built.isEmpty()) {
			List<Component> lore = new ArrayList<>();
			for (BuiltStructure structure : built) {
				lore.add(Lang.get("structure.gui.dismantle.at", structure.origin.getX(), structure.origin.getY(), structure.origin.getZ()));
			}
			lore.addAll(GuiUtil.lines("structure.gui.dismantle.lore"));
			if (!canManage) {
				lore.add(Lang.get("structure.gui.need_permission"));
			}
			gui.setSlot(50, GuiUtil.item(Items.TNT, Lang.get("structure.gui.dismantle.name"), lore)
					.setCallback((i, c, a, g) -> Safe.run("structure.gui.dismantle", () -> {
						GuiUtil.clickSound(player);
						if (built.size() == 1) {
							confirmDismantle(player, guild, type, built.get(0));
						} else {
							openInstances(player, guild, type);
						}
					})));
		}
		gui.setSlot(53, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	private static GuiElementBuilder cellIcon(StructureType type, int col, int row) {
		StructurePattern pattern = type.pattern();
		if (pattern.isWildcard(col, row)) {
			return GuiUtil.item(Items.STAINED_GLASS_PANE.pick(DyeColor.LIGHT_GRAY), Lang.get("structure.gui.cell.any"), List.of());
		}
		char c = pattern.at(col, row);
		BlockKey key = type.key(c);
		List<Component> lore = new ArrayList<>();
		if (key.isTag()) {
			lore.add(Lang.get("structure.gui.cell.tag", key.raw()));
		}
		int signIndex = signIndex(type.def(), col, row);
		if (signIndex >= 0) {
			lore.add(Lang.get("structure.gui.cell.sign", signIndex + 1));
		}
		GuiElementBuilder builder = GuiUtil.item(key.displayItem(),
				TextUtil.lang("structure.gui.cell.name", key.displayName(type.def().labelFor(c))), lore);
		if (signIndex >= 0) {
			builder.glow();
		}
		return builder;
	}

	private static int signIndex(StructureDefinition def, int col, int row) {
		for (int i = 0; i < def.signCells.size(); i++) {
			int[] cell = def.signCells.get(i);
			if (cell[0] == col && cell[1] == row) {
				return i;
			}
		}
		return -1;
	}

	private static List<Component> infoLore(Guild guild, StructureType type) {
		StructureDefinition def = type.def();
		List<Component> lore = new ArrayList<>();
		for (String line : def.description) {
			lore.add(TextUtil.colored(line));
		}
		lore.add(Component.empty());
		lore.add(status(guild, type));
		lore.add(levelLine(guild, def));
		if (def.unlockCost > 0) {
			lore.add(Lang.get("structure.gui.cost", def.unlockCost));
		}
		lore.add(Lang.get("structure.gui.size", type.width(), type.height(), def.signCount()));
		lore.add(Lang.get("structure.gui.max", def.maxPerGuild));
		lore.add(Component.empty());
		lore.addAll(GuiUtil.lines("structure.gui.howto"));
		return lore;
	}

	private static void confirmUnlock(ServerPlayer player, Guild guild, StructureType type) {
		GuiUtil.confirm(player, Lang.get("structure.gui.unlock.confirm_title"),
				List.of(TextUtil.lang("structure.gui.unlock.confirm", type.name(), type.def().unlockCost)),
				() -> {
					Component error = StructureManager.unlock(player, guild, type);
					if (error != null) {
						Msg.sendRaw(player, error);
						GuiUtil.errorSound(player);
					} else {
						GuiUtil.successSound(player);
					}
					reopen(player, guild, type);
				},
				() -> reopen(player, guild, type));
	}

	private static void confirmDismantle(ServerPlayer player, Guild guild, StructureType type, BuiltStructure structure) {
		GuiUtil.confirm(player, Lang.get("structure.gui.dismantle.confirm_title"),
				List.of(TextUtil.lang("structure.gui.dismantle.confirm", type.name(),
						structure.origin.getX(), structure.origin.getY(), structure.origin.getZ())),
				() -> {
					Component error = StructureManager.dismantle(player, guild, structure);
					if (error != null) {
						Msg.sendRaw(player, error);
						GuiUtil.errorSound(player);
					}
					reopen(player, guild, type);
				},
				() -> reopen(player, guild, type));
	}

	private static void openInstances(ServerPlayer player, Guild guild, StructureType type) {
		SimpleGui gui = GuiUtil.chest(player, 3, Lang.get("structure.gui.instances.title", type.plainName()));
		List<BuiltStructure> built = StructureManager.builtOf(guild, type.id());
		for (int i = 0; i < built.size() && i < 7; i++) {
			BuiltStructure structure = built.get(i);
			gui.setSlot(10 + i, GuiUtil.item(Items.TNT, Lang.get("structure.gui.dismantle.at",
					structure.origin.getX(), structure.origin.getY(), structure.origin.getZ()), List.of(Lang.get("structure.gui.instances.click")))
					.setCallback((index, c, a, g) -> Safe.run("structure.gui.instances", () -> confirmDismantle(player, guild, type, structure))));
		}
		gui.setSlot(18, GuiUtil.back(() -> openDetail(player, guild, type)));
		GuiUtil.fill(gui);
		gui.open();
	}

	/** Re-opens the detail page if the guild still exists. */
	private static void reopen(ServerPlayer player, Guild guild, StructureType type) {
		Guild current = GuildManager.of(player);
		if (current == guild) {
			StructureType fresh = StructureManager.type(type.id());
			if (fresh != null) {
				openDetail(player, guild, fresh);
			}
		}
	}
}
