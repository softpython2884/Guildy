package dev.guildy.structure;

import dev.guildy.core.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * Lang formatting that keeps {@link Component} arguments as real components, so translatable item/entity names
 * are rendered client-side in the player's language ({@link Lang#get} flattens them to server-side strings).
 * Legacy '&' colors active before a placeholder are applied to the inserted component and carried over to the
 * text that follows it.
 */
public final class TextUtil {
	private TextUtil() {
	}

	/** Formats the lang key; Component args are inserted as components, other args as text. */
	public static MutableComponent lang(String key, Object... args) {
		return format(Lang.raw(key), args);
	}

	/** Same as {@link #lang} for an already colorized pattern ('§' codes, {n} placeholders). */
	public static MutableComponent format(String pattern, Object... args) {
		MutableComponent out = Component.empty();
		LegacyState state = new LegacyState();
		StringBuilder segment = new StringBuilder();
		int i = 0;
		while (i < pattern.length()) {
			char c = pattern.charAt(i);
			if (c == '§' && i + 1 < pattern.length()) {
				state.apply(pattern.charAt(i + 1));
				segment.append(c).append(pattern.charAt(i + 1));
				i += 2;
				continue;
			}
			if (c == '{') {
				int end = pattern.indexOf('}', i);
				if (end > i + 1) {
					Integer index = parseIndex(pattern.substring(i + 1, end));
					if (index != null && args != null && index < args.length) {
						Object arg = args[index];
						if (arg instanceof Component component) {
							flush(out, segment);
							out.append(Component.empty().withStyle(state.style()).append(component));
							// Text after a component starts from the root style: re-apply the active codes.
							segment.append(state.codes());
						} else {
							// Same as Lang.get: '&' codes in text arguments are colorized too.
							String text = Lang.colorize(String.valueOf(arg));
							for (int k = 0; k < text.length(); k++) {
								char t = text.charAt(k);
								if (t == '§' && k + 1 < text.length()) {
									state.apply(text.charAt(k + 1));
								}
								segment.append(t);
							}
						}
						i = end + 1;
						continue;
					}
				}
			}
			segment.append(c);
			i++;
		}
		flush(out, segment);
		return out;
	}

	/** Splits a lang value on '\n' into lines (each formatted with {@link #format}). */
	public static List<Component> lines(String key, Object... args) {
		List<Component> lines = new ArrayList<>();
		for (String part : Lang.raw(key).split("\n")) {
			lines.add(format(part, args));
		}
		return lines;
	}

	/** Colorizes a config string ('&' codes) into a literal component. */
	public static MutableComponent colored(String text) {
		return Component.literal(Lang.colorize(text == null ? "" : text));
	}

	private static void flush(MutableComponent out, StringBuilder segment) {
		// Segments made only of formatting codes render nothing: skip them.
		if (!segment.isEmpty() && !Lang.strip(segment.toString()).isEmpty()) {
			out.append(Component.literal(segment.toString()));
		}
		segment.setLength(0);
	}

	private static Integer parseIndex(String text) {
		try {
			int n = Integer.parseInt(text);
			return n >= 0 ? n : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Tracks legacy formatting codes while scanning a string. */
	private static final class LegacyState {
		private ChatFormatting color;
		private final List<ChatFormatting> formats = new ArrayList<>();

		void apply(char code) {
			ChatFormatting formatting = ChatFormatting.getByCode(code);
			if (formatting == null) {
				return;
			}
			if (formatting == ChatFormatting.RESET) {
				color = null;
				formats.clear();
			} else if (formatting.ordinal() <= ChatFormatting.WHITE.ordinal()) {
				// colors (0-9, a-f) reset the formats, like vanilla legacy rendering
				color = formatting;
				formats.clear();
			} else if (!formats.contains(formatting)) {
				formats.add(formatting);
			}
		}

		Style style() {
			Style style = Style.EMPTY;
			if (color != null) {
				style = style.withColor(color);
			}
			for (ChatFormatting formatting : formats) {
				style = style.applyFormat(formatting);
			}
			return style;
		}

		String codes() {
			StringBuilder sb = new StringBuilder();
			if (color != null) {
				sb.append(color);
			}
			for (ChatFormatting formatting : formats) {
				sb.append(formatting);
			}
			return sb.toString();
		}
	}
}
