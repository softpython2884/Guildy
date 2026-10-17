package dev.guildy.villager;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.guildy.data.JsonAdapters;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZonePermissionsTest {
	private static WorkZone zone(ZoneType type) {
		return new WorkZone(1, type, "Zone", new BlockPos(100, 64, 200), 1, 0L);
	}

	@Test
	void defaultsReproduceTheOlderZones() {
		WorkZone farm = zone(ZoneType.FARM);
		for (ZoneOption option : List.of(ZoneOption.HARVEST, ZoneOption.REPLANT, ZoneOption.SOW, ZoneOption.RETILL, ZoneOption.TILL)) {
			assertTrue(ZonePermissions.allows(farm, option), option + " on by default");
		}
		for (ZoneOption option : List.of(ZoneOption.IRRIGATE, ZoneOption.CLEAR, ZoneOption.LEVEL, ZoneOption.BONEMEAL)) {
			assertFalse(ZonePermissions.allows(farm, option), option + " off by default");
		}
		WorkZone forest = zone(ZoneType.FOREST);
		for (ZoneOption option : List.of(ZoneOption.CUT, ZoneOption.TREE_REPLANT, ZoneOption.TREE_PLANT)) {
			assertTrue(ZonePermissions.allows(forest, option), option + " on by default");
		}
		assertFalse(ZonePermissions.allows(forest, ZoneOption.HARVEST), "options of another zone type never apply");
		assertFalse(ZonePermissions.allows(farm, ZoneOption.CUT));
		assertEquals(ZonePermissions.Preset.DEFAULTS, ZonePermissions.matching(farm));
		assertTrue(farm.options.isEmpty(), "nothing stored until a player changes something");
	}

	@Test
	void minesAndPatrolsHaveNoToggles() {
		assertFalse(ZonePermissions.supports(ZoneType.MINE));
		assertFalse(ZonePermissions.supports(ZoneType.PATROL));
		assertTrue(ZonePermissions.supports(ZoneType.FARM));
		assertTrue(ZonePermissions.supports(ZoneType.FOREST));
		WorkZone mine = zone(ZoneType.MINE);
		ZonePermissions.set(mine, ZoneOption.TILL, true);
		assertTrue(mine.options.isEmpty(), "an option of another type is never stored");
		assertNull(ZonePermissions.matching(mine));
		assertEquals("", ZonePermissions.key(mine));
	}

	@Test
	void idsAreUniqueWithinAType() {
		for (ZoneType type : ZoneType.values()) {
			Set<String> ids = new HashSet<>();
			for (ZoneOption option : ZonePermissions.forType(type)) {
				assertTrue(ids.add(option.id()), "duplicate id " + option.id() + " for " + type);
			}
		}
		assertEquals(9, ZonePermissions.forType(ZoneType.FARM).size());
		assertEquals(3, ZonePermissions.forType(ZoneType.FOREST).size());
	}

	@Test
	void togglesAreStoredByNameAndSurviveSaving() {
		WorkZone farm = zone(ZoneType.FARM);
		assertTrue(ZonePermissions.toggle(farm, ZoneOption.IRRIGATE));
		assertFalse(ZonePermissions.toggle(farm, ZoneOption.TILL));
		assertEquals(Map.of("irrigate", true, "till", false), farm.options);
		Gson gson = JsonAdapters.createGson();
		JsonObject json = gson.toJsonTree(farm).getAsJsonObject();
		assertTrue(json.getAsJsonObject("options").get("irrigate").getAsBoolean(), "persisted by name: " + json);
		WorkZone loaded = gson.fromJson(json, WorkZone.class);
		loaded.sanitize(3);
		assertTrue(ZonePermissions.allows(loaded, ZoneOption.IRRIGATE));
		assertFalse(ZonePermissions.allows(loaded, ZoneOption.TILL));
		assertTrue(ZonePermissions.allows(loaded, ZoneOption.HARVEST), "untouched options keep their default");
		assertEquals(ZonePermissions.key(farm), ZonePermissions.key(loaded));
	}

	@Test
	void olderDataWithoutTogglesTakesTheDefaults() {
		Gson gson = JsonAdapters.createGson();
		WorkZone loaded = gson.fromJson("{\"id\":3,\"type\":\"FARM\",\"name\":\"Champs 1\",\"pos\":[1,64,2],\"radius\":1,"
				+ "\"objective\":\"WHEAT\",\"fanion\":true}", WorkZone.class);
		loaded.sanitize(3);
		assertNotNull(loaded.options);
		assertTrue(loaded.options.isEmpty());
		assertNull(loaded.field, "no field state until the farmers need one");
		assertEquals(ZonePermissions.Preset.DEFAULTS, ZonePermissions.matching(loaded));
		WorkZone nulls = gson.fromJson("{\"id\":4,\"type\":\"FOREST\",\"name\":\"Bois\",\"pos\":[1,64,2],\"options\":null}", WorkZone.class);
		nulls.sanitize(3);
		assertNotNull(nulls.options);
		assertTrue(ZonePermissions.allows(nulls, ZoneOption.CUT));
	}

	@Test
	void sanitizeKeepsOnlyTheZoneTypeIds() {
		WorkZone farm = zone(ZoneType.FARM);
		Map<String, Boolean> options = new LinkedHashMap<>();
		options.put("till", false);
		options.put("cut", false);
		options.put("bogus", true);
		options.put("level", null);
		farm.options = options;
		farm.sanitize(3);
		assertEquals(Map.of("till", false), farm.options);
		farm.type = ZoneType.MINE;
		farm.sanitize(3);
		assertTrue(farm.options.isEmpty(), "a mine keeps no toggle");
	}

	@Test
	void presetsSetEveryOption() {
		WorkZone farm = zone(ZoneType.FARM);
		ZonePermissions.apply(farm, ZonePermissions.Preset.MAINTAIN);
		assertEquals(ZonePermissions.Preset.MAINTAIN, ZonePermissions.matching(farm));
		assertEquals(9, farm.options.size(), "every option stored explicitly");
		for (ZoneOption option : ZonePermissions.forType(ZoneType.FARM)) {
			boolean on = option == ZoneOption.HARVEST || option == ZoneOption.REPLANT || option == ZoneOption.SOW || option == ZoneOption.RETILL;
			assertEquals(on, ZonePermissions.allows(farm, option), "maintain: " + option);
		}
		ZonePermissions.apply(farm, ZonePermissions.Preset.CREATE);
		assertEquals(ZonePermissions.Preset.CREATE, ZonePermissions.matching(farm));
		for (ZoneOption option : ZonePermissions.forType(ZoneType.FARM)) {
			boolean on = option != ZoneOption.LEVEL && option != ZoneOption.BONEMEAL;
			assertEquals(on, ZonePermissions.allows(farm, option), "create: " + option);
		}
		ZonePermissions.toggle(farm, ZoneOption.LEVEL);
		assertNull(ZonePermissions.matching(farm), "custom settings");
		ZonePermissions.apply(farm, ZonePermissions.Preset.DEFAULTS);
		assertTrue(farm.options.isEmpty());
		assertEquals(ZonePermissions.Preset.DEFAULTS, ZonePermissions.matching(farm));
		assertEquals(List.of(ZonePermissions.Preset.MAINTAIN, ZonePermissions.Preset.CREATE), ZonePermissions.presets(ZoneType.FARM));
		assertTrue(ZonePermissions.presets(ZoneType.FOREST).isEmpty());
	}

	@Test
	void togglesChangeTheSettingsKey() {
		WorkZone farm = zone(ZoneType.FARM);
		String before = farm.settingsKey();
		ZonePermissions.toggle(farm, ZoneOption.CLEAR);
		assertNotEquals(before, farm.settingsKey(), "the zone's villagers start over after a change");
		assertEquals(5, ZonePermissions.enabledCount(zone(ZoneType.FARM)));
	}
}
