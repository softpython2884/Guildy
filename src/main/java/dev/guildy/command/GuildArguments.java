package dev.guildy.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Safe;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildRole;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Suggestion providers and name resolution for /guild arguments.
 */
final class GuildArguments {
	private GuildArguments() {
	}

	/** All guild names, raw (for greedy string arguments). */
	static final SuggestionProvider<CommandSourceStack> ALL_GUILDS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Safe.get("suggest.guilds", GuildArguments::guildNames, List.of()), builder);

	/** All guild names, quoted when needed (for {@link StringArgumentType#string()} arguments followed by others). */
	static final SuggestionProvider<CommandSourceStack> ALL_GUILDS_QUOTED = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Safe.get("suggest.guilds", () -> guildNames().stream()
					.map(StringArgumentType::escapeIfRequired).toList(), List.of()), builder);

	/** Guilds that invited the command source. */
	static final SuggestionProvider<CommandSourceStack> INVITING_GUILDS = (ctx, builder) -> {
		ServerPlayer player = ctx.getSource().getPlayer();
		List<String> names = new ArrayList<>();
		if (player != null) {
			Safe.run("suggest.invites", () -> GuildManager.invitesOf(player.getUUID()).forEach(g -> names.add(g.name)));
		}
		return SharedSuggestionProvider.suggest(names, builder);
	};

	/** Online players without a guild (except the source). */
	static final SuggestionProvider<CommandSourceStack> INVITABLE_PLAYERS = (ctx, builder) -> {
		ServerPlayer source = ctx.getSource().getPlayer();
		List<String> names = new ArrayList<>();
		for (ServerPlayer player : GuildyServer.players()) {
			if (player != source && GuildManager.of(player) == null) {
				names.add(player.getName().getString());
			}
		}
		return SharedSuggestionProvider.suggest(names, builder);
	};

	/** Members of the source's guild accepted by {@code filter(sourceRole, memberRole)} (never the source). */
	static SuggestionProvider<CommandSourceStack> members(BiPredicate<GuildRole, GuildRole> filter) {
		return (ctx, builder) -> {
			ServerPlayer source = ctx.getSource().getPlayer();
			List<String> names = new ArrayList<>();
			if (source != null) {
				Safe.run("suggest.members", () -> {
					Guild guild = GuildManager.of(source);
					GuildRole sourceRole = guild == null ? null : guild.role(source.getUUID());
					if (sourceRole == null) {
						return;
					}
					for (UUID id : guild.memberIds()) {
						GuildRole role = guild.role(id);
						if (!id.equals(source.getUUID()) && role != null && filter.test(sourceRole, role)) {
							names.add(GuildActions.memberName(guild, id));
						}
					}
				});
			}
			return SharedSuggestionProvider.suggest(names, builder);
		};
	}

	static List<String> guildNames() {
		List<String> names = new ArrayList<>();
		for (Guild guild : GuildManager.all()) {
			names.add(guild.name);
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	/** Guild by exact (case-insensitive) name, tolerating surrounding quotes and extra spaces. */
	static @Nullable Guild guild(String input) {
		String name = input.trim();
		if (name.length() >= 2 && (name.startsWith("\"") && name.endsWith("\"") || name.startsWith("'") && name.endsWith("'"))) {
			name = name.substring(1, name.length() - 1);
		}
		Guild guild = GuildManager.byName(name.trim());
		return guild != null ? guild : GuildManager.byName(GuildRules.normalizeName(name));
	}
}
