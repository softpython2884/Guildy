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
 * {@code /guild admin scroll list | give <player> <id> [count]} (the admin node is already permission-gated, like the unique
 * weapon commands).
 */
final class ScrollCommands {
	private static final SuggestionProvider<CommandSourceStack> IDS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Safe.get("suggest.scrolls", ScrollCommands::ids, List.of()), builder);

	private ScrollCommands() {
	}

	static void register() {
		GuildCommand.addAdminSubcommands((admin, context) -> admin.then(Commands.literal("scroll")
				.then(Commands.literal("list").executes(ScrollCommands::list))
				.then(Commands.literal("give")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("id", StringArgumentType.word()).suggests(IDS)
										.executes(ctx -> give(ctx, 1))
										.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
												.executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "count")))))))));
	}

	private static List<String> ids() {
		List<String> ids = new ArrayList<>();
		for (ScrollConfig.ScrollDef def : ScrollModule.config().usable()) {
			ids.add(def.id);
		}
		return ids;
	}

	private static int give(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		String id = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
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
		List<String> tables = new ArrayList<>();
		for (Map.Entry<String, ScrollConfig.DropTable> entry : cfg.drops.entrySet()) {
			tables.add(entry.getKey() + " " + percent(entry.getValue().chance) + " %");
		}
		source.sendSuccess(() -> Lang.get("scroll.admin.list.tables", String.join(", ", tables)), false);
		source.sendSuccess(() -> Lang.get("scroll.admin.list.effects", EffectRunner.count()), false);
		return cfg.usable().size();
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
