package dev.guildy.upgrade;

import dev.guildy.core.Hooks;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import dev.guildy.gui.GuildGui;
import dev.guildy.gui.GuiUtil;
import dev.guildy.structure.TextUtil;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Arbre d'evolution" (6 rows): info card, one row per branch (branch icon, then its tiers left to right linked by
 * panes that turn green once the previous node is unlocked), back / close / help. Clicking an available node asks for
 * a confirmation, then {@link UpgradeService#unlock} re-checks everything and pays.
 */
final class UpgradeMenu extends GuildGui {
	private static final int BRANCHES_PER_PAGE = 4;
	/** Up to 4 tiers: nodes on every other column with connectors between them; beyond, one node per column. */
	private static final int SPACED_MAX_TIER = 4;

	private int page;

	UpgradeMenu(ServerPlayer player, Guild guild, int page) {
		super(player, 6, guild);
		this.page = page;
	}

	static void open(ServerPlayer player, Guild guild) {
		new UpgradeMenu(player, guild, 0).show();
	}

	private static int pages(UpgradeTree tree) {
		return GuiUtil.pages(tree.branches().size(), BRANCHES_PER_PAGE);
	}

	@Override
	protected Component title(@Nullable Guild guild) {
		int pages = pages(Upgrades.tree());
		return pages > 1 ? Lang.get("upgrade.gui.title_paged", page + 1, pages) : Lang.get("upgrade.gui.title");
	}

	@Override
	protected void build(@Nullable Guild guild) {
		if (guild == null) {
			return;
		}
		UpgradeTree tree = Upgrades.tree();
		int pages = pages(tree);
		page = Math.max(0, Math.min(page, pages - 1));
		setSlot(4, infoCard(guild, tree));
		List<UpgradeTree.Branch> branches = tree.branches();
		boolean spaced = tree.maxTier() <= SPACED_MAX_TIER;
		for (int row = 0; row < BRANCHES_PER_PAGE; row++) {
			int index = page * BRANCHES_PER_PAGE + row;
			if (index >= branches.size()) {
				break;
			}
			branchRow(guild, tree, branches.get(index), 9 * (row + 1), spaced);
		}
		if (branches.isEmpty()) {
			setSlot(22, GuiUtil.item(Items.BARRIER, Lang.get("upgrade.gui.none"), List.of()));
		}
		bottomBar(() -> Hooks.openMainMenu.accept(player));
		pager(page, pages, target -> {
			page = target;
			render();
		});
		setSlot(53, GuiUtil.item(Items.BOOK, Lang.get("upgrade.gui.help.name"), GuiUtil.lines("upgrade.gui.help.lore")));
	}

	// ---------------------------------------------------------------- items

	private GuiElementBuilder infoCard(Guild guild, UpgradeTree tree) {
		List<Component> lore = new ArrayList<>(GuiUtil.lines("upgrade.gui.info.lore", guild.treasury, guild.level,
				tree.unlockedCount(guild.upgrades.unlocked), tree.nodes().size()));
		lore.add(Component.empty());
		lore.add(Lang.get("upgrade.gui.info.bonuses"));
		boolean any = false;
		for (String key : UpgradeEffects.ALL) {
			double value = UpgradeEffects.effective(key, tree.value(guild.upgrades.unlocked, key));
			if (value > 0) {
				lore.add(Lang.get("upgrade.gui.info.bonus", Upgrades.describe(key, value)));
				any = true;
			}
		}
		if (!any) {
			lore.add(Lang.get("upgrade.gui.info.none"));
		}
		return GuiUtil.item(Items.ENCHANTING_TABLE, Lang.get("upgrade.gui.info.name"), lore);
	}

	private void branchRow(Guild guild, UpgradeTree tree, UpgradeTree.Branch branch, int first, boolean spaced) {
		Set<String> unlocked = guild.upgrades.unlocked;
		List<UpgradeTree.Node> nodes = tree.nodesOf(branch.id());
		int done = 0;
		for (UpgradeTree.Node node : nodes) {
			if (unlocked.contains(node.id())) {
				done++;
			}
		}
		List<Component> lore = List.of(nodes.isEmpty() ? Lang.get("upgrade.gui.branch.empty") : Lang.get("upgrade.gui.branch.lore", done, nodes.size()));
		GuiElementBuilder icon = GuiUtil.item(item(branch.icon(), Items.BOOK), Lang.get("upgrade.gui.branch.name", branch.name()), lore);
		if (!nodes.isEmpty() && done == nodes.size()) {
			icon.glow();
		}
		setSlot(first, icon);
		boolean previousUnlocked = true; // the branch itself
		int maxTier = spaced ? SPACED_MAX_TIER : UpgradeTree.MAX_TIER;
		for (int tier = 1; tier <= maxTier; tier++) {
			UpgradeTree.Node node = tree.nodeAt(branch.id(), tier);
			if (node == null) {
				continue;
			}
			int slot = spaced ? first + 2 * tier : first + tier;
			if (spaced) {
				DyeColor color = previousUnlocked ? DyeColor.LIME : DyeColor.WHITE;
				setSlot(slot - 1, new GuiElementBuilder(Items.STAINED_GLASS_PANE.pick(color)).setName(Component.literal(" ")).hideTooltip());
			}
			setSlot(slot, nodeIcon(guild, tree, branch, node));
			previousUnlocked = unlocked.contains(node.id());
		}
	}

	private GuiElementBuilder nodeIcon(Guild guild, UpgradeTree tree, UpgradeTree.Branch branch, UpgradeTree.Node node) {
		Set<String> unlocked = guild.upgrades.unlocked;
		UpgradeRules.State state = UpgradeRules.state(node, unlocked, guild.level);
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("upgrade.gui.node.header", branch.name(), node.tier()));
		for (String line : node.description()) {
			lore.add(TextUtil.colored(line));
		}
		lore.add(Component.empty());
		lore.add(Lang.get("upgrade.gui.node.effects"));
		for (Map.Entry<String, Double> effect : node.effects().entrySet()) {
			lore.add(Lang.get("upgrade.gui.node.effect", Upgrades.describe(effect.getKey(), effect.getValue())));
		}
		if (node.effects().isEmpty()) {
			lore.add(Lang.get("upgrade.gui.node.no_effect"));
		}
		boolean affordable = true;
		if (state != UpgradeRules.State.UNLOCKED) {
			lore.add(Component.empty());
			lore.add(Lang.get("upgrade.gui.node.cost"));
			if (node.costGold() > 0) {
				boolean enough = guild.treasury >= node.costGold();
				affordable = enough;
				lore.add(Lang.get(enough ? "upgrade.gui.node.cost.gold" : "upgrade.gui.node.cost.gold.missing", node.costGold(), guild.treasury));
			}
			for (UpgradeTree.Cost cost : node.costItems()) {
				Item item = ItemUtil.item(cost.item());
				if (item == null) {
					continue;
				}
				int have = UpgradeService.owned(player, cost.item());
				affordable &= have >= cost.count();
				lore.add(TextUtil.lang(have >= cost.count() ? "upgrade.gui.node.cost.item" : "upgrade.gui.node.cost.item.missing",
						cost.count(), ItemUtil.name(item), have));
			}
			if (node.costGold() <= 0 && node.costItems().isEmpty()) {
				lore.add(Lang.get("upgrade.gui.node.cost.free"));
			}
			if (node.minLevel() > 1 || !node.requires().isEmpty()) {
				lore.add(Component.empty());
				lore.add(Lang.get("upgrade.gui.node.conditions"));
				if (node.minLevel() > 1) {
					lore.add(Lang.get(guild.level >= node.minLevel() ? "upgrade.gui.node.level" : "upgrade.gui.node.level.missing",
							node.minLevel(), guild.level));
				}
				for (String requirement : node.requires()) {
					UpgradeTree.Node required = tree.node(requirement);
					String name = required != null ? required.name() : requirement;
					lore.add(Lang.get(unlocked.contains(requirement) ? "upgrade.gui.node.require" : "upgrade.gui.node.require.missing", name));
				}
			}
		}
		lore.add(Component.empty());
		String nameKey;
		switch (state) {
			case UNLOCKED -> {
				nameKey = "upgrade.gui.node.name.unlocked";
				lore.add(Lang.get("upgrade.gui.node.state.unlocked"));
			}
			case AVAILABLE -> {
				nameKey = "upgrade.gui.node.name.available";
				if (!guild.can(player.getUUID(), GuildPermission.TREASURY)) {
					lore.addAll(GuiUtil.lines("upgrade.gui.node.state.permission"));
				} else if (!affordable) {
					lore.add(Lang.get("upgrade.gui.node.state.unaffordable"));
				} else {
					lore.add(Lang.get("upgrade.gui.node.state.available"));
				}
			}
			default -> {
				nameKey = "upgrade.gui.node.name.locked";
				lore.add(Lang.get("upgrade.gui.node.state.locked"));
			}
		}
		GuiElementBuilder icon = GuiUtil.item(item(node.icon(), Items.PAPER), Lang.get(nameKey, node.name()), lore);
		if (state == UpgradeRules.State.UNLOCKED) {
			icon.glow();
		}
		String id = node.id();
		return icon.setCallback(onClick("upgrade.node", (current, type) -> clickNode(current, id)));
	}

	private static Item item(String id, Item fallback) {
		Item item = ItemUtil.item(id);
		return item != null ? item : fallback;
	}

	// ---------------------------------------------------------------- actions

	private void clickNode(@Nullable Guild guild, String id) {
		if (guild == null) {
			return;
		}
		UpgradeTree.Node node = Upgrades.tree().node(id);
		if (node != null && guild.upgrades.unlocked.contains(id)) {
			GuiUtil.clickSound(player);
			Msg.actionBar(player, Lang.get("upgrade.error.already", node.name()));
			return;
		}
		Component problem = node == null ? Lang.get("upgrade.error.unknown", id) : UpgradeService.problem(player, guild, node);
		if (problem != null) {
			GuiUtil.errorSound(player);
			Msg.sendRaw(player, problem);
			if (isOpen()) {
				render();
			}
			return;
		}
		GuiUtil.clickSound(player);
		confirm(guild, node);
	}

	private void confirm(Guild guild, UpgradeTree.Node node) {
		List<Component> question = new ArrayList<>();
		question.add(Lang.get("upgrade.confirm.question", node.name()));
		if (node.costGold() > 0) {
			question.add(Lang.get("upgrade.confirm.gold", node.costGold()));
		}
		for (UpgradeTree.Cost cost : node.costItems()) {
			Item item = ItemUtil.item(cost.item());
			if (item != null) {
				question.add(TextUtil.lang("upgrade.confirm.item", cost.count(), ItemUtil.name(item)));
			}
		}
		question.add(Component.empty());
		for (Map.Entry<String, Double> effect : node.effects().entrySet()) {
			question.add(Lang.get("upgrade.gui.node.effect", Upgrades.describe(effect.getKey(), effect.getValue())));
		}
		int keep = page;
		String id = node.id();
		GuiUtil.confirm(player, Lang.get("upgrade.confirm.title"), question,
				() -> {
					Component error = UpgradeService.unlock(player, guild, id);
					if (error != null) {
						Msg.sendRaw(player, error);
						GuiUtil.errorSound(player);
					}
					reopen(guild, keep);
				},
				() -> reopen(guild, keep));
	}

	private void reopen(Guild guild, int page) {
		if (GuildManager.of(player) == guild) {
			new UpgradeMenu(player, guild, page).show();
		} else {
			Hooks.openMainMenu.accept(player);
		}
	}
}
