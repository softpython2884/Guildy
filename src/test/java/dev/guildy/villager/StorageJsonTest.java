package dev.guildy.villager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** guilds.json form of the registered containers: one compact line each, default values left out, older objects read. */
class StorageJsonTest {
	/** The chest list as it sits in the guild data. */
	static final class Holder {
		List<StorageChest> chests = new ArrayList<>();
	}

	/** Same settings as the guild data (pretty printing, positions as [x, y, z]). */
	private static Gson gson() {
		return new GsonBuilder()
				.setPrettyPrinting()
				.disableHtmlEscaping()
				.registerTypeHierarchyAdapter(BlockPos.class, (JsonSerializer<BlockPos>) (pos, type, ctx) -> {
					JsonArray array = new JsonArray();
					array.add(pos.getX());
					array.add(pos.getY());
					array.add(pos.getZ());
					return array;
				})
				.registerTypeHierarchyAdapter(BlockPos.class, (JsonDeserializer<BlockPos>) (json, type, ctx) -> json.isJsonArray()
						? new BlockPos(json.getAsJsonArray().get(0).getAsInt(), json.getAsJsonArray().get(1).getAsInt(), json.getAsJsonArray().get(2).getAsInt())
						: new BlockPos(json.getAsJsonObject().get("x").getAsInt(), json.getAsJsonObject().get("y").getAsInt(),
						json.getAsJsonObject().get("z").getAsInt()))
				.create();
	}

	@Test
	void oneCompactLinePerContainer() {
		Holder holder = new Holder();
		StorageChest deposit = new StorageChest(new BlockPos(1, 64, -2), StorageChest.Role.DEPOSIT, "");
		deposit.label = "Dépôt";
		StorageChest auto = new StorageChest(new BlockPos(5, 65, 7), StorageChest.Role.CATEGORY, "Livres enchantés");
		auto.auto = true;
		auto.room = 3;
		auto.label = "Livres enchantés";
		StorageChest filter = StorageChest.filtered(new BlockPos(9, 70, 9), ChestFilter.items(List.of("minecraft:enchanted_book")));
		holder.chests.addAll(List.of(deposit, auto, filter));
		String json = gson().toJson(holder);
		assertTrue(json.contains("{\"pos\":[1,64,-2],\"role\":\"DEPOSIT\",\"label\":\"Dépôt\"}"), json);
		assertTrue(json.contains("{\"pos\":[5,65,7],\"role\":\"CATEGORY\",\"category\":\"Livres enchantés\",\"label\":\"Livres enchantés\",\"auto\":true,\"room\":3}"), json);
		assertEquals(7, json.split("\n").length, "one line per container: " + json);
		Holder read = gson().fromJson(json, Holder.class);
		assertEquals(3, read.chests.size());
		assertEquals(StorageChest.Role.DEPOSIT, read.chests.get(0).role);
		assertEquals("", read.chests.get(0).category);
		assertEquals("Livres enchantés", read.chests.get(1).category);
		assertTrue(read.chests.get(1).auto);
		assertEquals(3, read.chests.get(1).room);
		assertEquals(new BlockPos(5, 65, 7), read.chests.get(1).pos);
		assertTrue(read.chests.get(2).isFilter());
		assertTrue(read.chests.get(2).filter.accepts("minecraft:enchanted_book"));
		assertNull(read.chests.get(0).filter);
	}

	@Test
	void olderFullObjectsAreRead() {
		String old = "{ \"chests\": [ { \"pos\": { \"x\": 1, \"y\": 2, \"z\": 3 }, \"role\": \"CATEGORY\", \"category\": \"Pierre\", \"label\": \"\","
				+ " \"auto\": false, \"room\": 0 } ] }";
		Holder read = gson().fromJson(old, Holder.class);
		assertEquals(new BlockPos(1, 2, 3), read.chests.getFirst().pos);
		assertEquals("Pierre", read.chests.getFirst().category);
		assertEquals("", read.chests.getFirst().label);
	}

	/** Thousands of containers: about 100 bytes each (the pretty form took about 260). */
	@Test
	void thousandsOfContainersStaySmall() {
		Holder holder = new Holder();
		for (int i = 0; i < 3000; i++) {
			StorageChest chest = new StorageChest(new BlockPos(1000 + i % 60, 64 + i / 600, -2000 + (i / 60) % 10), StorageChest.Role.CATEGORY,
					"Minerais et lingots");
			chest.label = chest.category;
			chest.auto = true;
			chest.room = 1;
			holder.chests.add(chest);
		}
		int bytes = gson().toJson(holder).getBytes(StandardCharsets.UTF_8).length;
		System.out.printf("[guilds.json] 3000 containers: %d KB%n", bytes / 1024);
		assertTrue(bytes < 3000 * 140, bytes + " bytes");
	}
}
