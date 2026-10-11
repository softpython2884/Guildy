package dev.guildy.shop;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** shop.json5 survives the Json5 writer + lenient reader unchanged. */
class ShopConfigRoundTripTest {

	@Test
	void roundTrip(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("shop.json5");
		ShopConfig written = new ShopConfig();
		Json5.write(file, written);
		ShopConfig read = Json5.read(file, ShopConfig.class);
		assertEquals(written.requireCounter, read.requireCounter);
		assertEquals(written.counterDistance, read.counterDistance);
		assertEquals(written.categories.size(), read.categories.size());
		for (int i = 0; i < written.categories.size(); i++) {
			ShopCategory a = written.categories.get(i);
			ShopCategory b = read.categories.get(i);
			assertEquals(a.id, b.id);
			assertEquals(a.name, b.name);
			assertEquals(a.icon, b.icon);
			assertEquals(a.entries.size(), b.entries.size());
			for (int e = 0; e < a.entries.size(); e++) {
				assertEquals(a.entries.get(e).item, b.entries.get(e).item);
				assertEquals(a.entries.get(e).amount, b.entries.get(e).amount);
				assertEquals(a.entries.get(e).buy, b.entries.get(e).buy);
				assertEquals(a.entries.get(e).sell, b.entries.get(e).sell);
				assertEquals(a.entries.get(e).minLevel, b.entries.get(e).minLevel);
			}
		}
	}
}
