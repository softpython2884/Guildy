package dev.guildy.trade;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Hooks;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import dev.guildy.gui.GuiUtil;
import dev.guildy.gui.GuildGui;
import dev.guildy.structure.BuiltStructure;
import dev.guildy.structure.TextUtil;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Trade hub menu (5 rows): hub card, current tier, upgrade, merchants at the base, rules, one preview button per tier.
 * Refreshed every 2 seconds while open (remaining stay times).
 */
final class TradeHubMenu extends GuildGui {
	private int ticks;

	TradeHubMenu(ServerPlayer player, Guild guild) {
		super(player, 5, guild);
	}

	static void open(ServerPlayer player, Guild guild) {
		new TradeHubMenu(player, guild).show();
	}

	@Override
	protected Component title(@Nullable Guild guild) {
		return Lang.get("trade.gui.title");
	}

	@Override
	public void onTick() {
		if (++ticks % 40 == 0 && isOpen()) {
			render();
		}
	}

	@Override
	protected void build(@Nullable Guild guild) {
		if (guild == null) {
			return;
		}
		TradeConfig cfg = TradeModule.config();
		int tier = TradeHub.tier(guild);
		BuiltStructure hub = TradeHub.hub(guild);
		setSlot(4, hubCard(guild, cfg, tier, hub));
		setSlot(20, tierCard(cfg, tier));
		setSlot(22, upgradeButton(guild, cfg, tier));
		setSlot(24, visitorsCard(guild, hub));
		int tiers = Math.min(4, cfg.maxTier());
		int first = 31 - (tiers - 1);
		for (int t = 1; t <= tiers; t++) {
			int shown = t;
			GuiElementBuilder icon = GuiUtil.item(t <= tier ? Items.EMERALD : Items.COAL,
					Lang.get("trade.gui.preview.name", TradeHub.roman(t), Lang.colorize(cfg.tier(t).name)),
					GuiUtil.lines("trade.gui.preview.lore", cfg.tier(t).usable().size(), cfg.tier(t).offersMin, cfg.tier(t).offersMax));
			if (t == tier) {
				icon.glow();
			}
			button(first + 2 * (t - 1), icon, null, () -> new TradePreviewMenu(player, guild, shown, 0).show());
		}
		bottomBar(() -> Hooks.openMainMenu.accept(player));
		setSlot(44, GuiUtil.item(Items.BOOK, Lang.get("trade.gui.help.name"),
				GuiUtil.lines("trade.gui.help.lore", cfg.visits.staySeconds / 60)));
	}

	// ---------------------------------------------------------------- items

	private GuiElementBuilder hubCard(Guild guild, TradeConfig cfg, int tier, @Nullable BuiltStructure hub) {
		List<Component> lore = new ArrayList<>();
		if (hub == null) {
			lore.addAll(GuiUtil.lines("trade.gui.hub.missing"));
		} else {
			lore.add(Lang.get("trade.gui.hub.at", hub.origin.getX(), hub.origin.getY(), hub.origin.getZ()));
		}
		lore.add(Lang.get("trade.gui.hub.tier", TradeHub.roman(tier), cfg.maxTier(), Lang.colorize(cfg.tier(tier).name)));
		lore.add(Lang.get("trade.gui.hub.chance", percent(TradeHub.visitChance(guild))));
		lore.add(Lang.get("trade.gui.hub.visits", guild.trade.visits));
		GuiElementBuilder icon = GuiUtil.item(Items.EMERALD_BLOCK, Lang.get("trade.gui.hub.name"), lore);
		if (hub != null) {
			icon.glow();
		}
		return icon;
	}

	private GuiElementBuilder tierCard(TradeConfig cfg, int tier) {
		TradeConfig.Tier def = cfg.tier(tier);
		List<Component> lore = GuiUtil.lines("trade.gui.tier.lore", def.offersMin, def.offersMax);
		for (int t = 1; t <= tier; t++) {
			double share = tierShare(cfg, tier, t);
			lore.add(Lang.get("trade.gui.tier.source", TradeHub.roman(t), Lang.colorize(cfg.tier(t).name), percent(share)));
		}
		return GuiUtil.item(Items.CARTOGRAPHY_TABLE, Lang.get("trade.gui.tier.name", TradeHub.roman(tier), Lang.colorize(def.name)), lore);
	}

	private GuiElementBuilder upgradeButton(Guild guild, TradeConfig cfg, int tier) {
		if (tier >= cfg.maxTier()) {
			return GuiUtil.item(Items.NETHER_STAR, Lang.get("trade.gui.upgrade.max"), GuiUtil.lines("trade.gui.upgrade.max.lore")).glow();
		}
		TradeConfig.Tier next = cfg.tier(tier + 1);
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("trade.gui.upgrade.offers", next.offersMin, next.offersMax,
				percent(TradeRolls.visitChance(cfg.visits.chanceBase, cfg.visits.chancePerTier, tier + 1))));
		lore.add(Component.empty());
		lore.add(Lang.get("trade.gui.upgrade.cost"));
		if (next.costGold > 0) {
			lore.add(Lang.get(guild.treasury >= next.costGold ? "trade.gui.upgrade.gold" : "trade.gui.upgrade.gold.missing",
					next.costGold, guild.treasury));
		}
		for (TradeConfig.Cost cost : next.costItems) {
			Item item = ItemUtil.item(cost.item);
			if (item == null) {
				continue;
			}
			int have = TradeHub.owned(player, cost.item);
			lore.add(TextUtil.lang(have >= cost.count ? "trade.gui.upgrade.item" : "trade.gui.upgrade.item.missing",
					cost.count, ItemUtil.name(item), have));
		}
		lore.add(Lang.get(guild.level >= next.minLevel ? "trade.gui.upgrade.level" : "trade.gui.upgrade.level.missing",
				next.minLevel, guild.level));
		lore.add(Component.empty());
		Component problem = TradeHub.upgradeProblem(player, guild);
		if (problem == null) {
			lore.add(Lang.get("trade.gui.upgrade.click"));
		} else {
			lore.add(Component.empty().append(problem));
		}
		if (!guild.can(player.getUUID(), GuildPermission.TREASURY)) {
			lore.add(Lang.get("trade.gui.upgrade.permission"));
		}
		GuiElementBuilder icon = GuiUtil.item(Items.ANVIL,
				Lang.get("trade.gui.upgrade.name", TradeHub.roman(tier + 1), Lang.colorize(next.name)), lore);
		return icon.setCallback(onClick("trade.upgrade", (current, type) -> clickUpgrade(current)));
	}

	private GuiElementBuilder visitorsCard(Guild guild, @Nullable BuiltStructure hub) {
		List<Component> lore = new ArrayList<>();
		List<GuildTradeData.Visitor> visitors = Merchants.visitors(guild);
		for (GuildTradeData.Visitor visitor : visitors) {
			BlockPos pos = visitor.pos;
			lore.add(Lang.get("trade.gui.visitors.entry", Lang.colorize(visitor.name), pos.getX(), pos.getY(), pos.getZ(),
					duration(Merchants.remainingSeconds(visitor))));
		}
		if (visitors.isEmpty()) {
			lore.add(Lang.get("trade.gui.visitors.none"));
		}
		lore.add(Component.empty());
		if (Merchants.isPending(guild, TradeHub.KIND)) {
			lore.addAll(GuiUtil.lines("trade.gui.visitors.pending"));
		} else if (hub != null) {
			lore.add(Lang.get("trade.gui.visitors.next", percent(TradeHub.visitChance(guild))));
		}
		GuiElementBuilder icon = GuiUtil.item(visitors.isEmpty() ? Items.LEAD : Items.EMERALD, Lang.get("trade.gui.visitors.name"), lore);
		if (!visitors.isEmpty()) {
			icon.glow();
		}
		return icon;
	}

	// ---------------------------------------------------------------- actions

	private void clickUpgrade(@Nullable Guild guild) {
		if (guild == null) {
			return;
		}
		Component problem = TradeHub.upgradeProblem(player, guild);
		if (problem != null) {
			GuiUtil.errorSound(player);
			Msg.sendRaw(player, problem);
			render();
			return;
		}
		GuiUtil.clickSound(player);
		TradeConfig cfg = TradeModule.config();
		int tier = TradeHub.tier(guild);
		TradeConfig.Tier next = cfg.tier(tier + 1);
		List<Component> question = new ArrayList<>();
		question.add(Lang.get("trade.confirm.question", TradeHub.roman(tier + 1), Lang.colorize(next.name)));
		if (next.costGold > 0) {
			question.add(Lang.get("trade.confirm.gold", next.costGold));
		}
		for (TradeConfig.Cost cost : next.costItems) {
			Item item = ItemUtil.item(cost.item);
			if (item != null) {
				question.add(TextUtil.lang("trade.confirm.item", cost.count, ItemUtil.name(item)));
			}
		}
		GuiUtil.confirm(player, Lang.get("trade.confirm.title"), question,
				() -> {
					Component error = TradeHub.upgrade(player, guild);
					if (error != null) {
						Msg.sendRaw(player, error);
						GuiUtil.errorSound(player);
					}
					reopen(guild);
				},
				() -> reopen(guild));
	}

	private void reopen(Guild guild) {
		if (GuildManager.of(player) == guild) {
			open(player, guild);
		} else {
			Hooks.openMainMenu.accept(player);
		}
	}

	// ---------------------------------------------------------------- helpers

	/** Share of the offers drawn from tier {@code source} for a hub of tier {@code tier}. */
	static double tierShare(TradeConfig cfg, int tier, int source) {
		double total = 0;
		for (int t = 1; t <= tier; t++) {
			if (!cfg.tier(t).usable().isEmpty()) {
				total += t == tier ? cfg.visits.ownTierWeight : cfg.visits.lowerTierWeight;
			}
		}
		if (total <= 0 || cfg.tier(source).usable().isEmpty()) {
			return 0;
		}
		return (source == tier ? cfg.visits.ownTierWeight : cfg.visits.lowerTierWeight) / total;
	}

	static String percent(double chance) {
		return String.valueOf(Math.round(chance * 100));
	}

	static String duration(long seconds) {
		long s = Math.max(0, seconds);
		return s < 60 ? Lang.raw("trade.time.seconds", s) : Lang.raw("trade.time.minutes", s / 60, s % 60);
	}

	/** Icon of an entry: the sold stack (enchanted books keep their enchantment). */
	static ItemStack icon(HolderLookup.Provider registries, TradeEntry entry) {
		ItemStack stack = TradeOffers.result(registries, entry);
		return stack == null ? new ItemStack(Items.BARRIER) : stack;
	}

	/** "12 or", or "2 blocs d'or + 3 lingots (21 or)". */
	static Component price(TradeEntry entry) {
		int[] split = TradeRolls.priceSplit(entry.price);
		if (split[0] == 0) {
			return Lang.get("trade.price.ingots", split[1]);
		}
		return split[1] == 0 ? Lang.get("trade.price.blocks", split[0], entry.price)
				: Lang.get("trade.price.mixed", split[0], split[1], entry.price);
	}

	/** Registries of the running server (menus are only opened in game). */
	static HolderLookup.Provider registries() {
		return GuildyServer.registryAccess();
	}
}
