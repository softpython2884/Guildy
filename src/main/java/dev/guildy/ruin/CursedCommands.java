package dev.guildy.ruin;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.guildy.command.GuildCommand;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.ruin.RuinConfig.RuinDef;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Admin commands of the cursed guardians, merged into {@code /guild admin ruin} (Brigadier merges nodes of the same name):
 * {@code incantation <player> [count] cursed}, {@code awaken cursed} (nearest ruin, no ritual), {@code cursed phase} (the
 * next phase now) and {@code cursed attack <attack>} (a cursed attack now, cooldowns ignored).
 */
final class CursedCommands {
	private CursedCommands() {
	}

	static void register() {
		GuildCommand.addAdminSubcommands((admin, context) -> admin.then(Commands.literal("ruin")
				.then(Commands.literal("incantation")
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.literal("cursed").executes(ctx -> give(ctx, 1)))
								.then(Commands.argument("count", IntegerArgumentType.integer(1, 256))
										.then(Commands.literal("cursed").executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "count")))))))
				.then(Commands.literal("awaken")
						.then(Commands.literal("cursed").executes(ctx -> Safe.get("ruin.admin.cursed.awaken", () -> awaken(ctx), 0))))
				.then(Commands.literal("cursed")
						.then(Commands.literal("phase").executes(ctx -> Safe.get("ruin.admin.cursed.phase", () -> phase(ctx), 0)))
						.then(Commands.literal("attack")
								.then(Commands.argument("attack", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(attackIds(), builder))
										.executes(ctx -> Safe.get("ruin.admin.cursed.attack", () -> attack(ctx), 0)))))));
	}

	private static List<String> attackIds() {
		List<String> ids = new ArrayList<>();
		for (CursedRules.Attack attack : CursedRules.Attack.values()) {
			ids.add(attack.id());
		}
		return ids;
	}

	private static @Nullable RuinRecord nearest(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		Vec3 pos = source.getPosition();
		RuinRecord ruin = RuinIndex.nearest(source.getLevel().dimension(), pos.x, pos.z, null);
		if (ruin == null) {
			source.sendFailure(Msg.prefixed(Lang.get("ruin.admin.none")));
		}
		return ruin;
	}

	/** Gives cursed incantation scrolls to a player. */
	private static int give(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		return Safe.get("ruin.admin.cursed.incantation", () -> {
			CursedItems.give(target, count);
			ctx.getSource().sendSuccess(() -> Msg.prefixed(Lang.get("ruin.cursed.admin.incantation", count, target.getName().getString())), true);
			return count;
		}, 0);
	}

	/** Awakens the guardian of the nearest ruin cursed, at once (no scroll, no ritual). */
	private static int awaken(CommandContext<CommandSourceStack> ctx) {
		RuinRecord ruin = nearest(ctx);
		if (ruin == null) {
			return 0;
		}
		String error = Encounters.awaken(ctx.getSource().getLevel(), ruin, null, true, true);
		if (error != null) {
			ctx.getSource().sendFailure(Msg.prefixed(Lang.get(error)));
			return 0;
		}
		RuinDef def = Encounters.def(ruin);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Lang.get("ruin.cursed.admin.awakened", Lang.colorize(def.boss.name), Lang.colorize(def.name))), true);
		return 1;
	}

	/** The cursed guardian of the nearest ruin moves to its next phase now. */
	private static int phase(CommandContext<CommandSourceStack> ctx) {
		RuinRecord ruin = nearest(ctx);
		if (ruin == null) {
			return 0;
		}
		if (!CursedFights.isCursed(ruin)) {
			ctx.getSource().sendFailure(Msg.prefixed(Lang.get("ruin.cursed.admin.not_cursed")));
			return 0;
		}
		int next = CursedFights.phase(ruin) + 1;
		if (!CursedFights.nextPhase(ruin)) {
			ctx.getSource().sendFailure(Msg.prefixed(Lang.get("ruin.cursed.admin.phase.failed")));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Lang.get("ruin.cursed.admin.phase", CursedRules.roman(next))), true);
		return next;
	}

	/** A cursed attack of the guardian of the nearest ruin, now. */
	private static int attack(CommandContext<CommandSourceStack> ctx) {
		RuinRecord ruin = nearest(ctx);
		if (ruin == null) {
			return 0;
		}
		String id = StringArgumentType.getString(ctx, "attack").toLowerCase(Locale.ROOT);
		CursedRules.Attack attack = CursedRules.Attack.byId(id);
		if (!CursedFights.isCursed(ruin)) {
			ctx.getSource().sendFailure(Msg.prefixed(Lang.get("ruin.cursed.admin.not_cursed")));
			return 0;
		}
		if (attack == null || !CursedFights.forceAttack(ruin, attack)) {
			ctx.getSource().sendFailure(Msg.prefixed(Lang.get("ruin.cursed.admin.attack.failed", id, String.join(", ", attackIds()))));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Lang.get("ruin.cursed.admin.attack", id)), false);
		return 1;
	}
}
