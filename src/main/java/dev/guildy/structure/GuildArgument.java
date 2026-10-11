package dev.guildy.structure;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import dev.guildy.core.Lang;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Guild name argument for admin commands (quoted when it contains spaces), with suggestions.
 */
public final class GuildArgument {
	private GuildArgument() {
	}

	public static RequiredArgumentBuilder<CommandSourceStack, String> guild(String name) {
		return Commands.argument(name, StringArgumentType.string()).suggests((ctx, builder) -> {
			List<String> names = new ArrayList<>();
			for (Guild guild : GuildManager.all()) {
				names.add(guild.name.matches("[A-Za-z0-9_.+-]+") ? guild.name : "\"" + guild.name.replace("\"", "") + "\"");
			}
			return SharedSuggestionProvider.suggest(names, builder);
		});
	}

	/** Resolves the argument; sends an error and returns null when unknown. */
	public static @Nullable Guild resolve(CommandContext<CommandSourceStack> ctx, String name) {
		String value = StringArgumentType.getString(ctx, name);
		Guild guild = GuildManager.byName(value);
		if (guild == null) {
			ctx.getSource().sendFailure(Msg.prefixed(Lang.get("error.unknown_guild", value)));
		}
		return guild;
	}
}
