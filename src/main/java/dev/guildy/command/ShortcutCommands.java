package dev.guildy.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import dev.guildy.Guildy;
import dev.guildy.command.ShortcutRules.Decision;
import dev.guildy.command.ShortcutRules.Outcome;
import dev.guildy.config.GuildyConfig;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Top-level shortcuts of /guild subcommands ({@code /home} = {@code /guild home}), configured in
 * {@code config.json5 > shortcuts}. Each shortcut is a new root literal that copies the target node: its command,
 * its children (same nodes: arguments and suggestions behave exactly like under /guild) and the requirements of every
 * node on the path (admin gates stay). Registered in a late phase of the command registration event, so that vanilla
 * and the other mods have registered theirs first: an existing command is never replaced.
 */
public final class ShortcutCommands {
	/** Command registration phase that runs after the default one. */
	public static final Identifier PHASE = Identifier.fromNamespaceAndPath(Guildy.MOD_ID, "shortcuts");

	/** What was registered on each dispatcher (a new one is built at startup and on every /reload). */
	private static final Map<CommandDispatcher<CommandSourceStack>, Applied> APPLIED = Collections.synchronizedMap(new WeakHashMap<>());
	/** Log lines already written (one warning per skipped shortcut, not one per reload). */
	private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
	private static Field @Nullable [] nodeMaps;

	/** One registered shortcut: its root node, plus what it was given (to notice a later merge by another mod). */
	private record Registered(LiteralCommandNode<CommandSourceStack> node, @Nullable Command<CommandSourceStack> command, int children) {
		boolean live(RootCommandNode<CommandSourceStack> root) {
			return root.getChild(node.getName()) == node && node.getCommand() == command && node.getChildren().size() == children;
		}
	}

	private record Applied(boolean enabled, List<Decision> decisions, Map<String, Registered> nodes) {
	}

	/** A resolved target: its node and the requirement of every node from /guild down to it. */
	private record Target(CommandNode<CommandSourceStack> node, List<Predicate<CommandSourceStack>> requirements) {
	}

	private ShortcutCommands() {
	}

	public static void init() {
		registerLang();
		CommandRegistrationCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, PHASE);
		CommandRegistrationCallback.EVENT.register(PHASE, (dispatcher, context, selection) ->
				Safe.run("shortcuts.register", () -> apply(dispatcher)));
		// /reload builds a new dispatcher (with the shortcuts above) that vanilla does not send to the players.
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) ->
				Safe.run("shortcuts.resend", () -> resend(server)));
		Lifecycle.RELOADED.add(() -> {
			MinecraftServer server = GuildyServer.server();
			if (server != null) {
				rebuild(server);
			}
		});
		GuildCommand.addAdminSubcommands((admin, context) -> admin.then(Commands.literal("shortcuts").executes(ctx -> {
			Safe.run("shortcuts.report", () -> report(ctx.getSource()));
			return 1;
		})));
	}

	/**
	 * Rebuilds the shortcuts of the running server from the current config (after {@code /guild admin reload}): removes
	 * the ones registered before (unless another mod changed them), registers the new ones and resends the command tree.
	 *
	 * @return false when an old shortcut could not be removed (the previous ones stay until a restart or /reload)
	 */
	public static boolean rebuild(MinecraftServer server) {
		CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
		RootCommandNode<CommandSourceStack> root = dispatcher.getRoot();
		Applied previous = APPLIED.get(dispatcher);
		if (previous != null) {
			for (Registered registered : previous.nodes().values()) {
				// A node another mod merged into (same name registered later) is theirs too: left in place.
				if (registered.live(root) && !remove(root, registered.node())) {
					logOnce("remove", () -> Guildy.LOGGER.warn("Cannot remove the shortcut /{} from the command tree: the shortcuts change "
							+ "after a restart or /reload", registered.node().getName()));
					return false;
				}
			}
		}
		apply(dispatcher);
		resend(server);
		return true;
	}

	/** The decisions on the server's current command tree; a registered shortcut taken over later reads OVERRIDDEN. */
	public static List<Decision> decisions(MinecraftServer server) {
		CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
		Applied applied = APPLIED.get(dispatcher);
		if (applied == null) {
			return List.of();
		}
		List<Decision> out = new ArrayList<>(applied.decisions().size());
		for (Decision decision : applied.decisions()) {
			Registered registered = applied.nodes().get(decision.alias());
			if (decision.outcome().active() && (registered == null || !registered.live(dispatcher.getRoot()))) {
				decision = new Decision(decision.alias(), decision.target(), decision.path(), Outcome.OVERRIDDEN);
			}
			out.add(decision);
		}
		return out;
	}

	/** Whether shortcuts were enabled when the current command tree was built. */
	public static boolean enabled(MinecraftServer server) {
		Applied applied = APPLIED.get(server.getCommands().getDispatcher());
		return applied != null && applied.enabled();
	}

	/** "Raccourcis : /g /home ..." with the shortcuts this player may use (null when none). */
	public static @Nullable Component helpLine(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		RootCommandNode<CommandSourceStack> root = server.getCommands().getDispatcher().getRoot();
		CommandSourceStack source = player.createCommandSourceStack();
		MutableComponent line = Lang.get("guild.help.shortcuts");
		boolean any = false;
		for (Decision decision : decisions(server)) {
			CommandNode<CommandSourceStack> node = root.getChild(decision.alias());
			if (!decision.outcome().active() || node == null || !node.canUse(source)) {
				continue;
			}
			String command = "/" + decision.alias();
			Component label = Lang.get("guild.help.shortcut", command);
			Component hover = Lang.get("guild.help.shortcut.hover", command, decision.targetCommand());
			line.append(Msg.suggest(label, node.getCommand() != null ? command : command + " ", hover)).append(Component.literal(" "));
			any = true;
		}
		return any ? line : null;
	}

	/** {@code /guild admin shortcuts}: every configured shortcut, active or skipped with the reason. */
	static void report(CommandSourceStack source) {
		MinecraftServer server = source.getServer();
		List<Decision> decisions = decisions(server);
		if (!enabled(server)) {
			source.sendSystemMessage(Msg.prefixed(Lang.get("cmd.admin.shortcuts.disabled")));
			return;
		}
		if (decisions.isEmpty()) {
			source.sendSystemMessage(Msg.prefixed(Lang.get("cmd.admin.shortcuts.none")));
			return;
		}
		long active = decisions.stream().filter(d -> d.outcome().active()).count();
		source.sendSystemMessage(Lang.get("cmd.admin.shortcuts.header", active, decisions.size()));
		for (Decision decision : decisions) {
			if (decision.outcome().active()) {
				source.sendSystemMessage(Lang.get("cmd.admin.shortcuts.active", decision.alias(), decision.targetCommand()));
			} else {
				source.sendSystemMessage(Lang.get("cmd.admin.shortcuts.skipped", decision.alias(), decision.targetCommand(),
						Lang.raw("cmd.admin.shortcuts.reason." + decision.outcome().name().toLowerCase(Locale.ROOT))));
			}
		}
		source.sendSystemMessage(Lang.get("cmd.admin.shortcuts.footer"));
	}

	/** Registers the configured shortcuts on a dispatcher whose /guild tree is complete. */
	private static void apply(CommandDispatcher<CommandSourceStack> dispatcher) {
		GuildyConfig.Shortcuts config = GuildyServer.config().shortcuts;
		RootCommandNode<CommandSourceStack> root = dispatcher.getRoot();
		CommandNode<CommandSourceStack> guild = root.getChild("guild");
		if (config == null || !config.enabled || guild == null) {
			APPLIED.put(dispatcher, new Applied(false, List.of(), Map.of()));
			return;
		}
		List<Decision> decisions = ShortcutRules.plan(config.aliases, path -> resolve(guild, path) != null, name -> root.getChild(name) != null);
		Map<String, Registered> nodes = new LinkedHashMap<>();
		Map<String, String> defaults = GuildyConfig.defaultShortcuts();
		List<String> active = new ArrayList<>();
		for (Decision decision : decisions) {
			if (!decision.outcome().active()) {
				log(decision, defaults);
				continue;
			}
			Target target = resolve(guild, decision.path());
			if (target == null) {
				continue;
			}
			LiteralCommandNode<CommandSourceStack> node = dispatcher.register(copy(decision.alias(), target));
			nodes.put(decision.alias(), new Registered(node, node.getCommand(), node.getChildren().size()));
			active.add("/" + decision.alias());
		}
		APPLIED.put(dispatcher, new Applied(true, decisions, nodes));
		int skipped = decisions.size() - active.size();
		String summary = "Shortcuts: " + (active.isEmpty() ? "none" : String.join(" ", active))
				+ (skipped > 0 ? " (" + skipped + " skipped, see /guild admin shortcuts)" : "");
		logOnce(summary, () -> Guildy.LOGGER.info(summary));
	}

	/** Walks /guild along literal children; null when a word is not a subcommand or the node does nothing. */
	private static @Nullable Target resolve(CommandNode<CommandSourceStack> guild, @Nullable List<String> path) {
		if (path == null) {
			return null;
		}
		List<Predicate<CommandSourceStack>> requirements = new ArrayList<>();
		requirements.add(guild.getRequirement());
		CommandNode<CommandSourceStack> node = guild;
		for (String word : path) {
			CommandNode<CommandSourceStack> child = node.getChild(word);
			if (!(child instanceof LiteralCommandNode)) {
				return null;
			}
			requirements.add(child.getRequirement());
			node = child;
		}
		if (node.getCommand() == null && node.getChildren().isEmpty() && node.getRedirect() == null) {
			return null;
		}
		return new Target(node, requirements);
	}

	/** New root literal with the target's command, children (shared nodes) or redirect, and all the path's requirements. */
	private static LiteralArgumentBuilder<CommandSourceStack> copy(String alias, Target target) {
		LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(alias);
		builder.requires(combine(target.requirements(), builder.getRequirement()));
		CommandNode<CommandSourceStack> node = target.node();
		if (node.getCommand() != null) {
			builder.executes(node.getCommand());
		}
		if (node.getRedirect() != null) {
			builder.forward(node.getRedirect(), node.getRedirectModifier(), node.isFork());
		} else {
			for (CommandNode<CommandSourceStack> child : node.getChildren()) {
				builder.then(child);
			}
		}
		return builder;
	}

	/** All the gates of the path; the default "always" requirement is dropped, a single gate is kept as it is. */
	static <S> Predicate<S> combine(List<Predicate<S>> requirements, Predicate<S> always) {
		List<Predicate<S>> gates = requirements.stream().filter(requirement -> requirement != always).distinct().toList();
		if (gates.isEmpty()) {
			return always;
		}
		if (gates.size() == 1) {
			return gates.getFirst();
		}
		return source -> {
			for (Predicate<S> gate : gates) {
				if (!gate.test(source)) {
					return false;
				}
			}
			return true;
		};
	}

	/**
	 * Removes one of our root literals. Brigadier has no removal API: its three child maps are edited by reflection
	 * (unnamed module, private fields with stable names).
	 */
	private static boolean remove(RootCommandNode<CommandSourceStack> root, CommandNode<CommandSourceStack> node) {
		try {
			if (nodeMaps == null) {
				Field[] fields = new Field[3];
				String[] names = {"children", "literals", "arguments"};
				for (int i = 0; i < names.length; i++) {
					fields[i] = CommandNode.class.getDeclaredField(names[i]);
					fields[i].setAccessible(true);
				}
				nodeMaps = fields;
			}
			for (Field field : nodeMaps) {
				((Map<?, ?>) field.get(root)).remove(node.getName(), node);
			}
			return root.getChild(node.getName()) == null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			Safe.log("shortcuts.remove", e);
			return false;
		}
	}

	/** Sends the command tree again to every player (new or removed shortcuts in their tab completion). */
	private static void resend(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			server.getCommands().sendCommands(player);
		}
	}

	private static void log(Decision decision, Map<String, String> defaults) {
		String alias = decision.alias();
		String target = decision.targetCommand();
		String message = switch (decision.outcome()) {
			case INVALID_ALIAS -> "Shortcut '" + alias + "' skipped: use lowercase letters, digits, '_' or '-' (at most "
					+ ShortcutRules.MAX_ALIAS_LENGTH + " characters)";
			case RESERVED -> "Shortcut /" + alias + " skipped: reserved name";
			case DUPLICATE -> "Shortcut /" + alias + " skipped: listed twice (the first one is kept)";
			case INVALID_TARGET -> "Shortcut /" + alias + " skipped: invalid target '" + decision.target()
					+ "' (subcommand names only, no arguments)";
			case MISSING_TARGET -> "Shortcut /" + alias + " skipped: " + target + " does not exist on this server";
			case COLLISION -> "Shortcut /" + alias + " skipped: a /" + alias + " command already exists (Minecraft or another mod); "
					+ "rename or remove it in config.json5 > shortcuts";
			default -> null;
		};
		if (message == null) {
			return;
		}
		boolean warn = ShortcutRules.worthWarning(decision, defaults);
		logOnce(message, () -> {
			if (warn) {
				Guildy.LOGGER.warn(message);
			} else {
				Guildy.LOGGER.debug(message);
			}
		});
	}

	private static void logOnce(String key, Runnable log) {
		if (LOGGED.add(key)) {
			log.run();
		}
	}

	private static void registerLang() {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("guild.help.shortcuts", "&7Raccourcis : ");
		m.put("guild.help.shortcut", "&e[{0}]");
		m.put("guild.help.shortcut.hover", "&e{0} &7= &f{1}");
		m.put("cmd.admin.shortcuts.header", "&6Raccourcis de commandes &7({0} actif(s) sur {1}) :");
		m.put("cmd.admin.shortcuts.active", "&a✔ &e/{0} &7→ &f{1}");
		m.put("cmd.admin.shortcuts.skipped", "&c✘ &e/{0} &7→ &f{1} &8: &7{2}");
		m.put("cmd.admin.shortcuts.reason.invalid_alias", "nom invalide (minuscules, chiffres, _ et -, 32 caractères au plus)");
		m.put("cmd.admin.shortcuts.reason.reserved", "nom réservé (guild, gld)");
		m.put("cmd.admin.shortcuts.reason.duplicate", "en double (le premier est gardé)");
		m.put("cmd.admin.shortcuts.reason.invalid_target", "cible invalide (noms de sous-commandes seulement, sans argument)");
		m.put("cmd.admin.shortcuts.reason.missing_target", "cette commande n'existe pas sur ce serveur (fonction absente ou désactivée)");
		m.put("cmd.admin.shortcuts.reason.collision", "nom déjà pris par Minecraft ou un autre mod (jamais remplacé)");
		m.put("cmd.admin.shortcuts.reason.overridden", "remplacé ensuite par la commande d'un autre mod");
		m.put("cmd.admin.shortcuts.disabled", "&7Les raccourcis sont désactivés (&fconfig.json5 > shortcuts > enabled&7).");
		m.put("cmd.admin.shortcuts.none", "&7Aucun raccourci configuré (&fconfig.json5 > shortcuts > aliases&7).");
		m.put("cmd.admin.shortcuts.footer", "&7Modifiez &fconfig.json5 > shortcuts&7 puis &e/guild admin reload&7.");
		Lang.addDefaults(m);
	}
}
