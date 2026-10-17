package dev.guildy.villager;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Autorisations du fanion": the per-zone toggles of field and forest zones ({@link ZoneOption}), stored by id in
 * {@link WorkZone#options}. Only the options a player changed (or a preset set) are stored: an absent option takes its
 * default, so zones saved before the toggles existed keep their old behaviour. Mine and patrol zones have no toggle.
 * Pure rules (no Minecraft classes, unit tested); the server-wide switches of villagers.json5 are applied on top by the
 * tasks.
 */
public final class ZonePermissions {
	private ZonePermissions() {
	}

	/** Ready-made sets of toggles (field zones). */
	public enum Preset {
		/** Back to the defaults (nothing stored). */
		DEFAULTS("minecraft:compass"),
		/** "Entretenir un champ": an existing field is kept as it is (nothing new is tilled). */
		MAINTAIN("minecraft:hay_block"),
		/** "Créer des champs": the whole zone becomes fields (everything but levelling and bone meal). */
		CREATE("minecraft:diamond_hoe");

		private final String icon;

		Preset(String icon) {
			this.icon = icon;
		}

		public String icon() {
			return icon;
		}

		public String langKey() {
			return "villager.field.preset." + name().toLowerCase(Locale.ROOT);
		}

		public String descKey() {
			return langKey() + ".desc";
		}
	}

	/** The toggles of a zone type, in menu order (empty for mines and patrols). */
	public static List<ZoneOption> forType(ZoneType type) {
		List<ZoneOption> list = new ArrayList<>();
		for (ZoneOption option : ZoneOption.values()) {
			if (option.type() == type) {
				list.add(option);
			}
		}
		return list;
	}

	/** The zone type has toggles (field and forest zones). */
	public static boolean supports(ZoneType type) {
		return !forType(type).isEmpty();
	}

	/** Presets offered for a zone type besides {@link Preset#DEFAULTS}. */
	public static List<Preset> presets(ZoneType type) {
		return type == ZoneType.FARM ? List.of(Preset.MAINTAIN, Preset.CREATE) : List.of();
	}

	/** Value of an option in a preset. */
	public static boolean presetValue(Preset preset, ZoneOption option) {
		return switch (preset) {
			case DEFAULTS -> option.defaultOn();
			case MAINTAIN -> option.type() != ZoneType.FARM || switch (option) {
				case HARVEST, REPLANT, SOW, RETILL -> true;
				default -> false;
			};
			case CREATE -> option != ZoneOption.LEVEL && option != ZoneOption.BONEMEAL;
		};
	}

	/** Stored value, else the default. */
	public static boolean value(@Nullable Map<String, Boolean> options, ZoneOption option) {
		Boolean stored = options == null ? null : options.get(option.id());
		return stored != null ? stored : option.defaultOn();
	}

	/** The zone allows this (an option of another zone type is never allowed). */
	public static boolean allows(WorkZone zone, ZoneOption option) {
		return option.type() == zone.type && value(zone.options, option);
	}

	/** Sets one option (ignored when it does not belong to the zone type). */
	public static void set(WorkZone zone, ZoneOption option, boolean on) {
		if (option.type() != zone.type) {
			return;
		}
		if (zone.options == null) {
			zone.options = new LinkedHashMap<>();
		}
		zone.options.put(option.id(), on);
	}

	/** Flips one option. @return its new value */
	public static boolean toggle(WorkZone zone, ZoneOption option) {
		boolean on = !allows(zone, option);
		set(zone, option, on);
		return allows(zone, option);
	}

	/** Applies a preset: every option of the zone type is stored explicitly ({@link Preset#DEFAULTS}: none). */
	public static void apply(WorkZone zone, Preset preset) {
		zone.options = new LinkedHashMap<>();
		if (preset == Preset.DEFAULTS) {
			return;
		}
		for (ZoneOption option : forType(zone.type)) {
			zone.options.put(option.id(), presetValue(preset, option));
		}
	}

	/** The preset the current toggles match (the specific ones first), null when none. */
	public static @Nullable Preset matching(WorkZone zone) {
		List<ZoneOption> options = forType(zone.type);
		if (options.isEmpty()) {
			return null;
		}
		List<Preset> candidates = new ArrayList<>(presets(zone.type));
		candidates.add(Preset.DEFAULTS);
		for (Preset preset : candidates) {
			boolean same = true;
			for (ZoneOption option : options) {
				if (value(zone.options, option) != presetValue(preset, option)) {
					same = false;
					break;
				}
			}
			if (same) {
				return preset;
			}
		}
		return null;
	}

	/** Options of the zone that are on. */
	public static int enabledCount(WorkZone zone) {
		int count = 0;
		for (ZoneOption option : forType(zone.type)) {
			if (value(zone.options, option)) {
				count++;
			}
		}
		return count;
	}

	/** Identity of the toggles ("" without toggles): a change restarts the search of the zone's villagers. */
	public static String key(WorkZone zone) {
		StringBuilder key = new StringBuilder();
		for (ZoneOption option : forType(zone.type)) {
			key.append(value(zone.options, option) ? '1' : '0');
		}
		return key.toString();
	}

	/** After loading: no null map, no null entry, only ids of the zone type's options. */
	public static void sanitize(WorkZone zone) {
		Map<String, Boolean> cleaned = new LinkedHashMap<>();
		if (zone.options != null && zone.type != null) {
			for (ZoneOption option : forType(zone.type)) {
				Boolean stored = zone.options.get(option.id());
				if (stored != null) {
					cleaned.put(option.id(), stored);
				}
			}
		}
		zone.options = cleaned;
	}
}
