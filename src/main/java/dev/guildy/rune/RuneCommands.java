package dev.guildy.rune;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.guildy.command.GuildCommand;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /guild admin rune list | inscribe <player> <id> | remove <player>} (on the item in the player's main hand; the
 * admin node is already permission-gated). An admin inscription ignores the tier weights but not the slot and mythic rules.
 */
final class RuneCommands {
	private static final SuggestionProvider<CommandSourceStack> IDS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Safe.get("suggest.runes", RuneCommands::ids, List.of()), builder);

	private RuneCommands() {
	}

	static void register() {
		GuildCommand.addAdminSubcommands((admin, context) -> admin.then(Commands.literal("rune")
				.then(Commands.literal("list").executes(RuneCommands::list))
				.then(Commands.literal("inscribe")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.argument("id", StringArgumentType.word()).suggests(IDS).executes(RuneCommands::inscribe))))
				.then(Commands.literal("remove")
						.then(Commands.argument("player", EntityArgument.player()).executes(RuneCommands::remove)))));
	}

	private static List<String> ids() {
		List<String> ids = new ArrayList<>();
		for (RuneConfig.RuneDef def : RuneModule.config().runes) {
			ids.add(def.id);
		}
		return ids;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		RuneConfig config = RuneModule.config();
		ctx.getSource().sendSuccess(() -> Lang.get("rune.admin.list.header", config.runes.size()), false);
		for (RuneConfig.RuneDef def : config.runes) {
			RuneTier tier = def.tier();
			String disabled = def.enabled ? "" : Lang.raw("rune.admin.list.disabled");
			ctx.getSource().sendSuccess(() -> Lang.get("rune.admin.list.line", def.id, tier.color + Lang.raw(tier.langKey()),
					String.join(",", def.slots), def.weight, def.glyph + " " + def.name + " " + disabled), false);
		}
		return config.runes.size();
	}

	private static int inscribe(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		String id = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
		RuneConfig config = RuneModule.config();
		RuneConfig.RuneDef def = config.rune(id);
		if (def == null) {
			ctx.getSource().sendFailure(Msg.prefixed(Lang.get("rune.admin.unknown", id)));
			return 0;
		}
		ItemStack stack = target.getMainHandItem();
		EquipmentSlot slot = RuneItems.armorSlot(stack);
		if (!RuneItems.canReceive(stack) || slot == null) {
			ctx.getSource().sendFailure(Msg.prefixed(Lang.get("rune.admin.no_item", target.getName().getString())));
			return 0;
		}
		if (!def.allows(slot.getSerializedName())
				|| def.tier() == RuneTier.MYTHIC && !config.mythicItems.contains(RuneItems.itemId(stack))) {
			ctx.getSource().sendFailure(Msg.prefixed(Lang.get("rune.admin.not_eligible", def.name)));
			return 0;
		}
		RuneItems.inscribe(stack, def);
		target.containerMenu.broadcastChanges();
		ctx.getSource().sendSuccess(() -> Lang.get("rune.admin.inscribed", def.tier().color + def.name, target.getName().getString()), true);
		return 1;
	}

	private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		ItemStack stack = target.getMainHandItem();
		if (!RuneItems.hasRuneTraces(stack)) {
			ctx.getSource().sendFailure(Msg.prefixed(Lang.get("rune.admin.no_rune", target.getName().getString())));
			return 0;
		}
		RuneItems.strip(stack);
		target.containerMenu.broadcastChanges();
		ctx.getSource().sendSuccess(() -> Lang.get("rune.admin.removed", target.getName().getString()), true);
		return 1;
	}
}
