package dev.guildy.scroll;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.guildy.command.GuildCommand;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.ruin.CursedItems;
import dev.guildy.ruin.IncantationItems;
import dev.guildy.structure.TextUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /guild admin scroll list | give <player> <id> [count] | grimoire <player> [cursed] [ids]} (the admin node is already
 * permission-gated, like the unique weapon commands) and {@code /guild grimoire} (pages menu of the grimoire in hand).
 * {@code give} also takes the ruins' summoning scrolls: {@code incantation} and {@code incantation_maudit}.
 */
final class ScrollCommands {
	private static final SuggestionProvider<CommandSourceStack> IDS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Safe.get("suggest.scrolls", ScrollCommands::ids, List.of()), builder);
	private static final SuggestionProvider<CommandSourceStack> GIVE_IDS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Safe.get("suggest.scrolls", ScrollCommands::giveIds, List.of()), builder);

	private ScrollCommands() {
	}

	static void register() {
		GuildCommand.addAdminSubcommands((admin, context) -> admin.then(Commands.literal("scroll")
				.then(Commands.literal("list").executes(ScrollCommands::list))
				.then(Commands.literal("give")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("id", StringArgumentType.word()).suggests(GIVE_IDS)
										.executes(ctx -> give(ctx, 1))
										.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
												.executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "count")))))))
				.then(Commands.literal("grimoire")
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> giveGrimoire(ctx, "", false))
								.then(Commands.literal(CURSED)
										.executes(ctx -> giveGrimoire(ctx, "", true))
										.then(Commands.argument("spells", StringArgumentType.greedyString()).suggests(IDS)
												.executes(ctx -> giveGrimoire(ctx, StringArgumentType.getString(ctx, "spells"), true))))
								.then(Commands.argument("spells", StringArgumentType.greedyString()).suggests(IDS)
										.executes(ctx -> giveGrimoire(ctx, StringArgumentType.getString(ctx, "spells"), false)))))));
		GuildCommand.addSubcommands((root, context) -> root.then(Commands.literal("grimoire").executes(ctx -> {
			ServerPlayer player = GuildCommand.player(ctx);
			Safe.run("scroll.grimoire.command", () -> {
				ItemStack held = Grimoires.held(player);
				if (held == null) {
					Msg.send(player, "grimoire.menu.gone");
				} else {
					GrimoireMenu.open(player, held);
				}
			});
			return 1;
		})));
	}

	/**
	 * {@code grimoire <player> [cursed] [ids]}: a grimoire (or a cursed grimoire), with up to four pages already written (ids
	 * separated by spaces or commas).
	 */
	private static int giveGrimoire(CommandContext<CommandSourceStack> ctx, String spells, boolean cursed) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		GrimoireRules.Pages pages = GrimoireRules.Pages.EMPTY;
		for (String raw : spells.split("[\\s,]+")) {
			String id = raw.trim().toLowerCase(Locale.ROOT);
			if (id.isEmpty()) {
				continue;
			}
			if (ScrollModule.config().scroll(id) == null) {
				ctx.getSource().sendFailure(Msg.prefixed(Lang.get("grimoire.admin.unknown", id)));
				return 0;
			}
			pages = GrimoireRules.fuse(pages, id).pages();
		}
		GrimoireRules.Pages written = pages;
		ItemStack stack = Safe.get("scroll.admin.grimoire", () -> GrimoireItems.create(written, cursed), ItemStack.EMPTY);
		if (stack.isEmpty()) {
			ScrollConfig cfg = ScrollModule.config();
			ctx.getSource().sendFailure(Msg.prefixed(Lang.get("grimoire.admin.unknown", cursed ? cfg.cursedGrimoire.item : cfg.grimoire.item)));
			return 0;
		}
		ItemUtil.giveOrDrop(target, stack);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Lang.get(GrimoireItems.key("grimoire.admin.give", cursed), target.getName().getString())),
				true);
		return 1;
	}

	/** Literal of the admin command giving a cursed grimoire. */
	static final String CURSED = "cursed";

	private static List<String> ids() {
		List<String> ids = new ArrayList<>();
		for (ScrollConfig.ScrollDef def : ScrollModule.config().usable()) {
			ids.add(def.id);
		}
		return ids;
	}

	/** What {@code give} takes: the spell scrolls and the two incantation scrolls of the ruins. */
	static List<String> giveIds() {
		List<String> ids = ids();
		ids.add(INCANTATION);
		ids.add(INCANTATION_CURSED);
		return ids;
	}

	/** The summoning scrolls of the ruins (reserved ids, also given by {@code /guild admin ruin incantation}). */
	static final String INCANTATION = "incantation";
	static final String INCANTATION_CURSED = "incantation_maudit";

	/** An incantation scroll (normal or cursed) of the ruins, {@code count} of them in full stacks; empty for another id. */
	static List<ItemStack> incantations(String id, int count) {
		return switch (id) {
			case INCANTATION -> IncantationItems.stacks(count);
			case INCANTATION_CURSED -> CursedItems.stacks(count);
			default -> List.of();
		};
	}

	private static int give(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		String id = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
		if (id.equals(INCANTATION) || id.equals(INCANTATION_CURSED)) {
			List<ItemStack> stacks = Safe.get("scroll.admin.give.incantation", () -> incantations(id, count), List.of());
			if (stacks.isEmpty()) {
				ctx.getSource().sendFailure(Msg.prefixed(Lang.get("scroll.admin.unknown", id)));
				return 0;
			}
			Component name = id.equals(INCANTATION) ? IncantationItems.name() : CursedItems.name();
			for (ItemStack stack : stacks) {
				ItemUtil.giveOrDrop(target, stack);
			}
			ctx.getSource().sendSuccess(() -> Msg.prefixed(TextUtil.lang("scroll.admin.give", name, count, target.getName().getString())), true);
			return count;
		}
		ScrollConfig.ScrollDef def = ScrollModule.config().scroll(id);
		if (def == null) {
			ctx.getSource().sendFailure(Msg.prefixed(Lang.get("scroll.admin.unknown", id)));
			return 0;
		}
		Component name = ScrollItems.name(def);
		int left = count;
		while (left > 0) {
			int want = left;
			ItemStack stack = Safe.get("scroll.admin.give", () -> ScrollItems.create(def, want), ItemStack.EMPTY);
			if (stack.isEmpty()) {
				ctx.getSource().sendFailure(Msg.prefixed(Lang.get("scroll.admin.unknown", id)));
				return 0;
			}
			int given = Math.min(left, stack.getCount());
			stack.setCount(given);
			left -= given;
			ItemUtil.giveOrDrop(target, stack);
		}
		ctx.getSource().sendSuccess(() -> Msg.prefixed(TextUtil.lang("scroll.admin.give", name, count, target.getName().getString())), true);
		return count;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		return Safe.get("scroll.admin.list", () -> listScrolls(ctx), 0);
	}

	private static int listScrolls(CommandContext<CommandSourceStack> ctx) {
		ScrollConfig cfg = ScrollModule.config();
		CommandSourceStack source = ctx.getSource();
		source.sendSuccess(() -> Lang.get("scroll.admin.list.header", cfg.usable().size()), false);
		if (!cfg.enabled) {
			source.sendSuccess(() -> Lang.get("scroll.admin.list.disabled"), false);
		}
		if (!cfg.dropsEnabled) {
			source.sendSuccess(() -> Lang.get("scroll.admin.list.drops_disabled"), false);
		}
		for (ScrollConfig.ScrollDef def : cfg.usable()) {
			String spell = Lang.raw(def.spell().langKey() + ".name");
			Component hover = Lang.get("scroll.admin.list.hover", spell, Spell.number(def.channelSeconds), Spell.number(def.cooldownSeconds),
					ScrollItems.groupName(def.cooldownGroup), drops(cfg, def.id));
			MutableComponent name = ScrollItems.name(def).copy().withStyle(style -> style
					.withClickEvent(new ClickEvent.SuggestCommand("/guild admin scroll give " + playerName(source) + " " + def.id + " 1"))
					.withHoverEvent(new HoverEvent.ShowText(hover)));
			Component line = TextUtil.lang("scroll.admin.list.entry", name, def.id, ScrollItems.rarityName(def.rarity()), spell);
			source.sendSuccess(() -> line, false);
		}
		// The summoning scrolls of the ruins.
		for (String id : List.of(INCANTATION, INCANTATION_CURSED)) {
			boolean cursed = id.equals(INCANTATION_CURSED);
			Component hover = Lang.get(cursed ? "scroll.admin.list.incantation_cursed.hover" : "scroll.admin.list.incantation.hover");
			MutableComponent name = (cursed ? CursedItems.name() : IncantationItems.name()).copy().withStyle(style -> style
					.withClickEvent(new ClickEvent.SuggestCommand("/guild admin scroll give " + playerName(source) + " " + id + " 1"))
					.withHoverEvent(new HoverEvent.ShowText(hover)));
			Component line = TextUtil.lang("scroll.admin.list.incantation", name, id,
					Lang.raw(cursed ? "scroll.admin.list.incantation_cursed.kind" : "scroll.admin.list.incantation.kind"));
			source.sendSuccess(() -> line, false);
		}
		ScrollConfig.Grimoire g = cfg.grimoire;
		List<String> grimoireDrops = new ArrayList<>();
		for (Map.Entry<String, Double> entry : g.drops.entrySet()) {
			if (entry.getValue() > 0) {
				grimoireDrops.add(entry.getKey() + " " + percent(entry.getValue()) + " %");
			}
		}
		List<String> costs = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : g.foodCost.entrySet()) {
			costs.add(entry.getKey().toLowerCase(Locale.ROOT) + " " + entry.getValue());
		}
		Component grimoireHover = Lang.get("grimoire.admin.list.hover", String.join(", ", costs), Spell.number(g.powerMultiplier),
				grimoireDrops.isEmpty() ? Lang.raw("scroll.admin.list.no_drop") : String.join(", ", grimoireDrops), g.tradeMinTier, g.tradePrice);
		MutableComponent grimoireName = GrimoireItems.name(GrimoireRules.Pages.EMPTY).copy().withStyle(style -> style
				.withClickEvent(new ClickEvent.SuggestCommand("/guild admin scroll grimoire " + playerName(source)))
				.withHoverEvent(new HoverEvent.ShowText(grimoireHover)));
		// Channel range over the scrolls (it follows each spell, see grimoire.channelFactor).
		double shortest = g.channelSeconds;
		double longest = g.channelSeconds;
		boolean first = true;
		for (ScrollConfig.ScrollDef def : cfg.usable()) {
			double seconds = g.channelSeconds(def);
			shortest = first ? seconds : Math.min(shortest, seconds);
			longest = first ? seconds : Math.max(longest, seconds);
			first = false;
		}
		String channel = Spell.number(shortest) + (longest > shortest ? "-" + Spell.number(longest) : "");
		Component grimoireLine = TextUtil.lang("grimoire.admin.list", grimoireName, GrimoireRules.PAGES, channel, Spell.number(g.cooldownSeconds));
		source.sendSuccess(() -> grimoireLine, false);
		Component cursedLine = cursedGrimoireLine(cfg, source);
		source.sendSuccess(() -> cursedLine, false);
		List<String> tables = new ArrayList<>();
		for (Map.Entry<String, ScrollConfig.DropTable> entry : cfg.drops.entrySet()) {
			tables.add(entry.getKey() + " " + percent(entry.getValue().chance) + " %");
		}
		source.sendSuccess(() -> Lang.get("scroll.admin.list.tables", String.join(", ", tables)), false);
		source.sendSuccess(() -> Lang.get("scroll.admin.list.effects", EffectRunner.count()), false);
		return cfg.usable().size();
	}

	/** The cursed grimoire in the list: costs, power, ruins whose cursed guardian drops it; click = the give command. */
	private static Component cursedGrimoireLine(ScrollConfig cfg, CommandSourceStack source) {
		ScrollConfig.CursedGrimoire c = cfg.cursedGrimoire;
		List<String> food = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : c.foodCost.entrySet()) {
			food.add(entry.getKey().toLowerCase(Locale.ROOT) + " " + entry.getValue());
		}
		List<String> blood = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : c.bloodCost.entrySet()) {
			blood.add(entry.getKey().toLowerCase(Locale.ROOT) + " " + entry.getValue());
		}
		List<String> ruins = new ArrayList<>();
		for (Map.Entry<String, Double> entry : c.ruinDrops.entrySet()) {
			if (entry.getValue() > 0) {
				ruins.add(entry.getKey() + " " + percent(entry.getValue()) + " %");
			}
		}
		Component hover = Lang.get("grimoire.cursed.admin.list.hover", String.join(", ", food), String.join(", ", blood), Spell.number(c.minHealth),
				Spell.number(c.powerMultiplier), Spell.number(c.radiusMultiplier), Spell.number(c.countMultiplier), c.levelBonus,
				ruins.isEmpty() ? Lang.raw("scroll.admin.list.no_drop") : String.join(", ", ruins));
		MutableComponent name = GrimoireItems.name(GrimoireRules.Pages.EMPTY, true).copy().withStyle(style -> style
				.withClickEvent(new ClickEvent.SuggestCommand("/guild admin scroll grimoire " + playerName(source) + " " + CURSED))
				.withHoverEvent(new HoverEvent.ShowText(hover)));
		return TextUtil.lang("grimoire.cursed.admin.list", name, GrimoireRules.PAGES, Spell.number(c.cooldownSeconds));
	}

	/** "ruin_boss 4,3 %, boss 0,6 %" (chance that one roll of the source gives this scroll). */
	private static String drops(ScrollConfig cfg, String id) {
		List<String> parts = new ArrayList<>();
		for (Map.Entry<String, ScrollConfig.DropTable> entry : cfg.drops.entrySet()) {
			double chance = ScrollRolls.probability(entry.getValue(), id, key -> cfg.scroll(key) != null);
			if (chance > 0) {
				parts.add(entry.getKey() + " " + percent(chance) + " %");
			}
		}
		return parts.isEmpty() ? Lang.raw("scroll.admin.list.no_drop") : String.join(", ", parts);
	}

	private static String percent(double chance) {
		double pct = chance * 100;
		if (pct >= 10) {
			return String.valueOf(Math.round(pct));
		}
		return String.format(Locale.FRANCE, pct >= 1 ? "%.1f" : "%.2f", pct);
	}

	private static String playerName(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		return player == null ? "<joueur>" : player.getName().getString();
	}
}
