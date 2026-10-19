package dev.guildy.command;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Pure rules of the top-level shortcuts ({@code /home} = {@code /guild home}): alias names, target paths under
 * {@code /guild} and the decision for each configured entry (no Minecraft types: unit tested).
 */
public final class ShortcutRules {
	public static final int MAX_ALIAS_LENGTH = 32;
	/** Names that always stay the guild root command and its alias. */
	public static final Set<String> RESERVED = Set.of("guild", "gld");
	private static final Pattern NAME = Pattern.compile("[a-z0-9_-]+");

	/** Why a configured shortcut is (not) registered. */
	public enum Outcome {
		/** Registered. */
		ACTIVE,
		/** Not lowercase letters, digits, '_' or '-' (or too long). */
		INVALID_ALIAS,
		/** {@code guild} or {@code gld}. */
		RESERVED,
		/** Same name as an earlier entry (case-insensitive): the first one wins. */
		DUPLICATE,
		/** The target is not a list of subcommand names. */
		INVALID_TARGET,
		/** No such subcommand under /guild (feature absent or disabled). */
		MISSING_TARGET,
		/** A command with this name already exists (Minecraft or another mod): never replaced. */
		COLLISION,
		/** Registered, then taken over by a command of the same name registered later by another mod (never decided by {@link #plan}). */
		OVERRIDDEN;

		public boolean active() {
			return this == ACTIVE;
		}
	}

	/**
	 * Decision for one configured entry.
	 *
	 * @param alias  normalized alias (lowercase, no leading slash)
	 * @param target target as written in the config
	 * @param path   parsed target path under /guild (empty = /guild itself), null when malformed
	 */
	public record Decision(String alias, String target, @Nullable List<String> path, Outcome outcome) {
		/** "/guild home" (or the raw target when it could not be parsed). */
		public String targetCommand() {
			return path == null ? target : command(path);
		}
	}

	private ShortcutRules() {
	}

	/** Lowercase, trimmed, without a leading slash. */
	public static String normalizeAlias(@Nullable String raw) {
		if (raw == null) {
			return "";
		}
		String alias = raw.trim().toLowerCase(Locale.ROOT);
		while (alias.startsWith("/")) {
			alias = alias.substring(1);
		}
		return alias;
	}

	/** Lowercase ASCII letters, digits, '_' and '-', 1 to {@link #MAX_ALIAS_LENGTH} characters. */
	public static boolean validAlias(String alias) {
		return alias.length() <= MAX_ALIAS_LENGTH && NAME.matcher(alias).matches();
	}

	public static boolean reserved(String alias) {
		return RESERVED.contains(alias);
	}

	/**
	 * Target path under /guild: {@code "home"} → [home], {@code "/guild villagers storage"} → [villagers, storage];
	 * {@code ""}, {@code "guild"} or {@code "/gld"} → [] (the /guild root itself); null when a word is not a plain
	 * subcommand name (arguments like {@code <amount>} cannot be part of a target).
	 */
	public static @Nullable List<String> parseTarget(@Nullable String raw) {
		if (raw == null) {
			return null;
		}
		String text = raw.trim().toLowerCase(Locale.ROOT);
		while (text.startsWith("/")) {
			text = text.substring(1).trim();
		}
		List<String> path = new ArrayList<>();
		if (!text.isEmpty()) {
			for (String word : text.split("\\s+")) {
				if (!NAME.matcher(word).matches()) {
					return null;
				}
				path.add(word);
			}
		}
		if (!path.isEmpty() && RESERVED.contains(path.getFirst())) {
			path.removeFirst();
		}
		return path;
	}

	/** "/guild" + the path. */
	public static String command(List<String> path) {
		return path.isEmpty() ? "/guild" : "/guild " + String.join(" ", path);
	}

	/**
	 * Decides every configured entry, in the config order.
	 *
	 * @param aliases      alias → target, as configured
	 * @param targetExists whether a target path names an existing /guild (sub)command
	 * @param rootTaken    whether a top-level command with that name already exists (not counting the entries before)
	 */
	public static List<Decision> plan(Map<String, String> aliases, Predicate<List<String>> targetExists, Predicate<String> rootTaken) {
		List<Decision> decisions = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (Map.Entry<String, String> entry : aliases.entrySet()) {
			String alias = normalizeAlias(entry.getKey());
			String target = entry.getValue() == null ? "" : entry.getValue();
			List<String> path = parseTarget(target);
			Outcome outcome;
			if (!validAlias(alias)) {
				outcome = Outcome.INVALID_ALIAS;
			} else if (reserved(alias)) {
				outcome = Outcome.RESERVED;
			} else if (!seen.add(alias)) {
				outcome = Outcome.DUPLICATE;
			} else if (path == null) {
				outcome = Outcome.INVALID_TARGET;
			} else if (!targetExists.test(path)) {
				outcome = Outcome.MISSING_TARGET;
			} else if (rootTaken.test(alias)) {
				outcome = Outcome.COLLISION;
			} else {
				outcome = Outcome.ACTIVE;
			}
			decisions.add(new Decision(alias.isEmpty() ? String.valueOf(entry.getKey()) : alias, target, path, outcome));
		}
		return decisions;
	}

	/**
	 * Whether a skipped entry deserves a warning in the log: a default entry whose target is absent (a feature that is
	 * disabled or not in this version, e.g. {@code fly}) is expected and only logged at debug level.
	 */
	public static boolean worthWarning(Decision decision, Map<String, String> defaults) {
		if (decision.outcome().active()) {
			return false;
		}
		if (decision.outcome() == Outcome.MISSING_TARGET) {
			String defaultTarget = defaults.get(decision.alias());
			return defaultTarget == null || !defaultTarget.equals(decision.target());
		}
		return true;
	}
}
