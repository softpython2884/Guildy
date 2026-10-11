package dev.guildy.structure;

import dev.guildy.config.Json5;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** structures.json5 content survives the Json5 writer + lenient reader unchanged. */
class StructureConfigRoundTripTest {
	/** Same list field as StructureConfig (which cannot be built without the game bootstrap). */
	public static final class Wrapper {
		public List<StructureDefinition> definitions = StructureConfig.defaults();
	}

	@Test
	void definitionsRoundTrip(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("structures.json5");
		Wrapper written = new Wrapper();
		Json5.write(file, written);
		Wrapper read = Json5.read(file, Wrapper.class);
		assertEquals(written.definitions.size(), read.definitions.size());
		for (int i = 0; i < written.definitions.size(); i++) {
			StructureDefinition a = written.definitions.get(i);
			StructureDefinition b = read.definitions.get(i);
			assertEquals(a.id, b.id);
			assertEquals(a.name, b.name);
			assertEquals(a.description, b.description);
			assertEquals(a.tutorial, b.tutorial);
			assertEquals(a.rows, b.rows);
			assertEquals(a.key, b.key);
			assertEquals(a.keyLabels, b.keyLabels);
			assertEquals(a.minLevel, b.minLevel);
			assertEquals(a.unlockCost, b.unlockCost);
			assertEquals(a.signCells.size(), b.signCells.size());
			for (int s = 0; s < a.signCells.size(); s++) {
				assertArrayEquals(a.signCells.get(s), b.signCells.get(s));
			}
			assertTrue(b.check().isEmpty(), b.id);
		}
	}
}
