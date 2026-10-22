package dev.guildy.scroll;

import java.util.Arrays;
import java.util.List;

/**
 * The refusal of a spell as the spell checks return it (pure Java, unit-tested): a lang key, optionally followed by the
 * arguments of its message ("scroll.fail.rally.near" + 2 + 8), packed in one string so that every check keeps returning a
 * plain lang key.
 */
public final class SpellFailure {
	private static final char SEPARATOR = '\u001F';

	private SpellFailure() {
	}

	/** A refusal with the arguments of its message (the key alone when there are none). */
	public static String of(String key, Object... args) {
		if (args == null || args.length == 0) {
			return key;
		}
		StringBuilder sb = new StringBuilder(key);
		for (Object arg : args) {
			sb.append(SEPARATOR).append(arg == null ? "" : String.valueOf(arg).replace(SEPARATOR, ' '));
		}
		return sb.toString();
	}

	/** A refusal with the arguments of its message. */
	public static String of(String key, List<?> args) {
		return of(key, args == null ? new Object[0] : args.toArray());
	}

	/** The lang key of a refusal. */
	public static String key(String failure) {
		int cut = failure.indexOf(SEPARATOR);
		return cut < 0 ? failure : failure.substring(0, cut);
	}

	/** The arguments of a refusal's message (none for a plain key). */
	public static Object[] args(String failure) {
		int cut = failure.indexOf(SEPARATOR);
		if (cut < 0) {
			return new Object[0];
		}
		String[] parts = failure.substring(cut + 1).split(String.valueOf(SEPARATOR), -1);
		return Arrays.copyOf(parts, parts.length, Object[].class);
	}

	/** The same refusal under another key (the grimoire's own wording), arguments kept. */
	public static String rekey(String failure, String key) {
		int cut = failure.indexOf(SEPARATOR);
		return cut < 0 ? key : key + failure.substring(cut);
	}
}
