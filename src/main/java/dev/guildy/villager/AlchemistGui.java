package dev.guildy.villager;

import dev.guildy.alchemy.AlchemyConfig;
import dev.guildy.alchemy.AlchemyModule;
import dev.guildy.alchemy.BrewBook;
import dev.guildy.alchemy.Brews;
import dev.guildy.alchemy.PotionKey;
import dev.guildy.alchemy.SimpleBrewing;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildEvents;
import dev.guildy.data.GuildManager;
import dev.guildy.gui.GuiUtil;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Alchemist parts of the villager menus: role and fallback lines of its icon, the role button (Auto / Livres / Parchemins
 * / Potions) and the guild's potion stock menu (how many bottles of each product the alchemists keep in the storage).
 */
final class AlchemistGui {
	private static final int PER_PAGE = 45;

	private AlchemistGui() {
	}

	static void init() {
		AlchemistLang.register();
		Lifecycle.STOPPING.add(server -> AlchemyGuild.clear());
		GuildEvents.DISBANDED.add(guild -> AlchemyGuild.forget(guild.id));
	}

	// ------------------------------------------------------------------ villager icon

	/** Role (manual or distributed) and, when it works at another job, why its own is impossible. */
	static void addLore(List<Component> lore, Guild guild, VillagerRecord record) {
		if (record.task != Task.ALCHEMIST) {
			return;
		}
		boolean manual = record.craft != null && AlchemyJob.parse(record.craft.role) != null;
		AlchemyJob role = Alchemists.role(guild, record.uuid);
		String name = role == null ? Lang.raw("villager.alchemy.role.auto") : Lang.raw(role.langKey());
		lore.add(Lang.get("villager.gui.lore.alchemy.role", name, Lang.raw(manual ? "villager.alchemy.role.manual" : "villager.alchemy.role.distributed")));
		AlchemyJob working = Alchemists.working(guild, record.uuid);
		String reason = Alchemists.fallbackReason(guild, record.uuid);
		if (role != null && working != null && working != role && !reason.isEmpty()) {
			lore.add(Lang.get("villager.gui.lore.alchemy.fallback", AlchemistLang.reason(role, reason), Lang.raw(working.langKey())));
		}
	}

	// ------------------------------------------------------------------ villager detail buttons

	/** Role button: left click = next role (Auto, Livres, Parchemins, Potions), right click = previous. */
	static GuiElementBuilder roleButton(ServerPlayer player, Guild guild, VillagerRecord record, Runnable reopen) {
		AlchemyJob manual = record.craft == null ? null : AlchemyJob.parse(record.craft.role);
		AlchemyJob role = Alchemists.role(guild, record.uuid);
		String title = manual == null ? Lang.raw("villager.alchemy.role.auto") + (role == null ? "" : " (" + Lang.raw(role.langKey()) + ")")
				: Lang.raw(manual.langKey());
		List<String> order = new ArrayList<>();
		for (AlchemyJob job : VillagerModule.config().alchemist.order()) {
			order.add(Lang.raw(job.langKey()));
		}
		List<Component> lore = new ArrayList<>(GuiUtil.lines("villager.gui.alchemy.role.lore", String.join(" > ", order)));
		AlchemyJob working = Alchemists.working(guild, record.uuid);
		if (working != null) {
			lore.add(Lang.get("villager.gui.alchemy.role.now", Lang.raw(working.langKey())));
		}
		lore.add(Component.empty());
		lore.add(Lang.get("villager.gui.alchemy.role.click"));
		ItemStack icon = new ItemStack(manual == null ? Items.COMPARATOR : switch (manual) {
			case BOOKS -> Items.ENCHANTED_BOOK;
			case SCROLLS -> Items.PAPER;
			case POTIONS -> Items.BREWING_STAND;
		});
		return GuiUtil.item(icon, Lang.get("villager.gui.alchemy.role", title), lore).setCallback((slot, type, action, g) -> {
			if (!VillagerGui.checkPermission(player, guild) || !VillagerGui.stillMember(player, guild, record)) {
				return;
			}
			AlchemyJob[] jobs = AlchemyJob.values();
			int index = manual == null ? -1 : manual.ordinal();
			int next = type.isRight ? index - 1 : index + 1;
			if (next < -1) {
				next = jobs.length - 1;
			}
			if (next >= jobs.length) {
				next = -1;
			}
			AlchemyJob chosen = next < 0 ? null : jobs[next];
			Alchemists.setRole(guild, record, chosen);
			Msg.send(player, "villager.gui.alchemy.role.set", record.name,
					Lang.raw(chosen == null ? "villager.alchemy.role.auto" : chosen.langKey()));
			GuiUtil.clickSound(player);
			reopen.run();
		});
	}

	/** "Stocks de potions" button. */
	static GuiElementBuilder stockButton(ServerPlayer player, Guild guild, Runnable back) {
		List<Component> lore = new ArrayList<>(GuiUtil.lines("villager.gui.alchemy.stock.lore"));
		AlchemyConfig.Brewing brewing = AlchemyModule.config().brewing;
		if (brewing.simple()) {
			// Simple brewing: what a batch needs.
			lore.add(Component.empty());
			lore.addAll(GuiUtil.lines("villager.gui.alchemy.stock.simple", VillagerModule.config().alchemist.bottlesPerBrew,
					AlchemistLang.itemName(brewing.item), brewing.maxElixirsPerDay));
		}
		lore.add(Component.empty());
		lore.add(Lang.get("villager.gui.alchemy.stock.click"));
		return GuiUtil.item(Items.BREWING_STAND, Lang.get("villager.gui.alchemy.stock"), lore).setCallback((slot, type, action, g) -> {
			GuiUtil.clickSound(player);
			openStock(player, guild, 0, back);
		});
	}

	// ------------------------------------------------------------------ stock menu

	static void openStock(ServerPlayer player, Guild guild, int page, Runnable back) {
		List<String> products = new ArrayList<>();
		for (String product : AlchemyModule.config().menuProducts()) {
			if (!Brews.create(product).isEmpty()) {
				products.add(product);
			}
		}
		int pages = GuiUtil.pages(products.size(), PER_PAGE);
		int current = Math.max(0, Math.min(page, pages - 1));
		SimpleGui gui = VillagerGui.chest(player, 6, Lang.get("villager.gui.stock.title"));
		ServerLevel level = GuildyServer.claimLevel();
		Map<String, Integer> held = level == null ? Map.of() : AlchemyGuild.of(guild).stock(level, guild).products;
		AlchemyConfig config = AlchemyModule.config();
		BrewBook book = Brews.book();
		for (int i = 0; i < PER_PAGE; i++) {
			int index = current * PER_PAGE + i;
			if (index >= products.size()) {
				break;
			}
			String product = products.get(index);
			int target = AlchemyGuild.target(guild, product);
			ItemStack stack = Brews.create(product);
			stack.setCount(Math.max(1, Math.min(stack.getMaxStackSize(), target)));
			GuiElementBuilder icon = GuiElementBuilder.from(stack);
			icon.addLoreLine(GuiUtil.noItalic(Component.empty()));
			icon.addLoreLine(GuiUtil.noItalic(Lang.get("villager.gui.stock.target", target, config.defaultTarget(product))));
			icon.addLoreLine(GuiUtil.noItalic(Lang.get("villager.gui.stock.held", held.getOrDefault(product, 0))));
			icon.addLoreLine(GuiUtil.noItalic(recipe(book, product)));
			icon.addLoreLine(GuiUtil.noItalic(Lang.get("villager.gui.stock.click")));
			if (target > 0) {
				icon.glow();
			}
			gui.setSlot(i, icon.setCallback((slot, type, action, g) -> {
				if (!VillagerGui.checkPermission(player, guild)) {
					return;
				}
				int step = type.shift ? 8 : 1;
				int value = AlchemyGuild.target(guild, product) + (type.isRight ? -step : step);
				value = Math.max(0, Math.min(config.maxStockTarget, value));
				if (value == config.defaultTarget(product)) {
					guild.villagers.potionTargets.remove(product);
				} else {
					guild.villagers.potionTargets.put(product, value);
				}
				GuildManager.markDirty();
				AlchemyGuild.of(guild).rolesAt = -1;
				GuiUtil.clickSound(player);
				openStock(player, guild, current, back);
			}));
		}
		gui.setSlot(45, GuiUtil.back(back));
		if (current > 0) {
			gui.setSlot(46, GuiUtil.item(Items.ARROW, Lang.get("gui.page.prev"), List.of(Lang.get("villager.gui.stock.page", current + 1, pages)))
					.setCallback((slot, type, action, g) -> openStock(player, guild, current - 1, back)));
		}
		gui.setSlot(49, GuiUtil.item(Items.BARRIER, Lang.get("villager.gui.stock.reset"), GuiUtil.lines("villager.gui.stock.reset.lore"))
				.setCallback((slot, type, action, g) -> {
					if (!VillagerGui.checkPermission(player, guild)) {
						return;
					}
					guild.villagers.potionTargets.clear();
					GuildManager.markDirty();
					Msg.send(player, "villager.gui.stock.reset.done");
					GuiUtil.successSound(player);
					openStock(player, guild, current, back);
				}));
		if (current < pages - 1) {
			gui.setSlot(52, GuiUtil.item(Items.ARROW, Lang.get("gui.page.next"), List.of(Lang.get("villager.gui.stock.page", current + 1, pages)))
					.setCallback((slot, type, action, g) -> openStock(player, guild, current + 1, back)));
		}
		gui.setSlot(53, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	/**
	 * Shortest recipe from a water bottle: "eau → verrue du Nether → poudre lumineuse"; in simple mode, the cost of a batch:
	 * "1 × poudre de blaze · 20 s par fournée".
	 */
	private static Component recipe(BrewBook book, String product) {
		AlchemyConfig config = AlchemyModule.config();
		if (config.brewing.simple()) {
			SimpleBrewing.Cost cost = SimpleBrewing.cost(product, config);
			return cost == null ? Lang.get("villager.gui.stock.no_recipe")
					: Lang.get("villager.gui.stock.cost", cost.blaze(), AlchemistLang.itemName(config.brewing.item), cost.seconds());
		}
		List<List<BrewBook.Step>> chains = book.chains(product, Set.of(PotionKey.WATER.text()), BrewBook.MAX_STEPS);
		if (chains.isEmpty()) {
			return Lang.get("villager.gui.stock.no_recipe");
		}
		List<String> parts = new ArrayList<>();
		parts.add(Brews.name(PotionKey.WATER.text()));
		for (BrewBook.Step step : chains.getFirst()) {
			parts.add(AlchemistLang.itemName(step.reagent()));
		}
		return Lang.get("villager.gui.stock.recipe", String.join(" → ", parts));
	}

	/** Statistics lines of the potions (villager menu). */
	static void statLore(List<Component> lore, @Nullable CraftData data, long today) {
		if (data == null || data.potions <= 0) {
			return;
		}
		lore.add(Lang.get("villager.gui.lore.stat.potions", data.potions, data.potionsToday(today), VillagerModule.config().alchemist.maxPotionsPerDay));
		AlchemyConfig.Brewing brewing = AlchemyModule.config().brewing;
		if (brewing.simple() && data.elixirsToday(today) > 0) {
			lore.add(Lang.get("villager.gui.lore.stat.elixirs", data.elixirsToday(today), brewing.maxElixirsPerDay));
		}
		if (!data.lastPotion.isEmpty()) {
			lore.add(Lang.get("villager.gui.lore.stat.last_potion", Brews.name(data.lastPotion)));
		}
	}
}
