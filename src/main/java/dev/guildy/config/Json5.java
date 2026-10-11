package dev.guildy.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Map;

/**
 * Minimal JSON5-ish support: files are read with a lenient Gson reader (accepts // and /* *\/ comments) and
 * written from the Java object, with {@link Comment} annotations turned into comment lines.
 * Missing keys keep the defaults set by the field initializers.
 */
public final class Json5 {
	public static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.setStrictness(Strictness.LENIENT)
			.create();

	private Json5() {
	}

	public static <T> T read(Path file, Class<T> type) throws IOException {
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonReader jsonReader = new JsonReader(reader);
			jsonReader.setStrictness(Strictness.LENIENT);
			return GSON.fromJson(jsonReader, type);
		}
	}

	public static <T> T fromString(String text, Class<T> type) {
		JsonReader jsonReader = new JsonReader(new StringReader(text));
		jsonReader.setStrictness(Strictness.LENIENT);
		return GSON.fromJson(jsonReader, type);
	}

	/** Writes atomically (temp file + move). */
	public static void write(Path file, Object value) throws IOException {
		Files.createDirectories(file.getParent());
		StringBuilder sb = new StringBuilder();
		Comment header = value.getClass().getAnnotation(Comment.class);
		if (header != null) {
			for (String line : header.value()) {
				sb.append("// ").append(line).append('\n');
			}
		}
		writeObject(sb, value, 0);
		sb.append('\n');
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	private static void writeObject(StringBuilder sb, Object obj, int indent) {
		sb.append("{\n");
		Field[] fields = obj.getClass().getDeclaredFields();
		boolean first = true;
		for (Field field : fields) {
			int mod = field.getModifiers();
			if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || field.isSynthetic()) {
				continue;
			}
			field.setAccessible(true);
			Object fieldValue;
			try {
				fieldValue = field.get(obj);
			} catch (IllegalAccessException e) {
				continue;
			}
			if (!first) {
				sb.append(",\n");
			}
			first = false;
			Comment comment = field.getAnnotation(Comment.class);
			if (comment != null) {
				sb.append('\n');
				for (String line : comment.value()) {
					indent(sb, indent + 1).append("// ").append(line).append('\n');
				}
			}
			indent(sb, indent + 1).append(GSON.toJson(field.getName())).append(": ");
			writeValue(sb, fieldValue, indent + 1);
		}
		sb.append('\n');
		indent(sb, indent).append('}');
	}

	private static void writeValue(StringBuilder sb, Object value, int indent) {
		if (value != null && isSection(value.getClass())) {
			writeObject(sb, value, indent);
			return;
		}
		JsonElement tree = GSON.toJsonTree(value);
		String json = GSON.toJson(tree);
		// Re-indent nested pretty-printed JSON
		String pad = "\t".repeat(indent);
		sb.append(json.replace("\n", "\n" + pad).replace("  ", "\t"));
	}

	/** A "section" is a plain config POJO (not a JDK type, collection, map, enum or record). */
	private static boolean isSection(Class<?> type) {
		if (type.isPrimitive() || type.isEnum() || type.isArray() || type.isRecord()) {
			return false;
		}
		if (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
			return false;
		}
		String name = type.getName();
		return !name.startsWith("java.") && type.isAnnotationPresent(Section.class);
	}

	private static StringBuilder indent(StringBuilder sb, int level) {
		for (int i = 0; i < level; i++) {
			sb.append('\t');
		}
		return sb;
	}

	/** Marks a nested config class whose fields should be written one by one (with their comments). */
	@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
	@java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE)
	public @interface Section {
	}
}
