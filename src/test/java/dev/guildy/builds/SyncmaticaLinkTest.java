package dev.guildy.builds;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Placements shared with Syncmatica: its file, and sites landing exactly where Litematica shows the schematic. */
class SyncmaticaLinkTest {
	private static final String JSON = """
			{
			  "placements": [
			    {
			      "id": "6f1c3b9a-0d2e-4f51-9a7c-3e8b2d4c5a61",
			      "file_name": "maison.litematic",
			      "display_name": "Maison du port",
			      "hash": "0b8f6a3e-1c2d-3e4f-8a9b-0c1d2e3f4a5b",
			      "origin": {"position": [120, 64, -340], "dimension": "minecraft:overworld"},
			      "rotation": "CLOCKWISE_90",
			      "mirror": "LEFT_RIGHT",
			      "owner": {"uuid": "11111111-2222-3333-4444-555555555555", "name": "nightFury"},
			      "subregionData": [{"name": "toit", "position": [0, 5, 0], "rotation": "NONE", "mirror": "NONE"}],
			      "litematicVersion": 7,
			      "dataVersion": 4325
			    },
			    {
			      "id": "7a2d4c0b-1e3f-4a62-8b8d-4f9c3e5d6b72",
			      "file_name": "C:\\\\Users\\\\x\\\\schematics\\\\tour.litematic",
			      "hash": "1c9a7b4f-2d3e-4f50-9bac-1d2e3f4a5b6c",
			      "origin": {"position": [0, -10, 5], "dimension": "minecraft:the_nether"},
			      "rotation": "NONE",
			      "mirror": "NONE",
			      "owner": {"uuid": "not-a-uuid", "name": "Bea"}
			    },
			    {"id": "8b3e5d1c-2f40-4b73-9c9e-5a0d4f6e7c83", "file_name": "broken.litematic"},
			    "not an object"
			  ]
			}
			""";

	@Test
	void readsTheSharedPlacementsAndSkipsBrokenEntries() {
		List<SyncmaticaLink.Shared> list = SyncmaticaLink.parse(JSON);
		assertEquals(2, list.size());
		SyncmaticaLink.Shared house = list.get(0);
		assertEquals(UUID.fromString("6f1c3b9a-0d2e-4f51-9a7c-3e8b2d4c5a61"), house.id());
		assertEquals("Maison du port", house.name());
		assertEquals(UUID.fromString("0b8f6a3e-1c2d-3e4f-8a9b-0c1d2e3f4a5b"), house.hash());
		assertEquals("minecraft:overworld", house.dimension());
		assertEquals(120, house.x());
		assertEquals(64, house.y());
		assertEquals(-340, house.z());
		assertEquals(PlacementTransform.Turn.CLOCKWISE_90, house.turn());
		assertEquals(PlacementTransform.Flip.LEFT_RIGHT, house.flip());
		assertEquals("nightFury", house.owner());
		assertEquals(UUID.fromString("11111111-2222-3333-4444-555555555555"), house.ownerId());
		assertTrue(house.subregions());
		assertEquals("syncmatica:6f1c3b9a-0d2e-4f51-9a7c-3e8b2d4c5a61", house.key());
		SyncmaticaLink.Shared tower = list.get(1);
		assertEquals("tour", tower.name(), "named after the file without its folder and extension");
		assertEquals("minecraft:the_nether", tower.dimension());
		assertNull(tower.ownerId());
		assertFalse(tower.subregions());
	}

	@Test
	void emptyOrForeignFilesGiveNoPlacement() {
		assertTrue(SyncmaticaLink.parse("{}").isEmpty());
		assertTrue(SyncmaticaLink.parse("{\"placements\": {}}").isEmpty());
		assertTrue(SyncmaticaLink.parse("[]").isEmpty());
	}

	/** Litematica's {@code PositionUtils.getTransformedBlockPos}: mirror, then rotation around (0, 0, 0). */
	private static int[] litematica(int x, int y, int z, PlacementTransform.Turn turn, PlacementTransform.Flip flip) {
		switch (flip) {
			case LEFT_RIGHT -> z = -z;
			case FRONT_BACK -> x = -x;
			default -> {
			}
		}
		return switch (turn) {
			case CLOCKWISE_90 -> new int[] {-z, y, x};
			case COUNTERCLOCKWISE_90 -> new int[] {z, y, -x};
			case CLOCKWISE_180 -> new int[] {-x, y, -z};
			case NONE -> new int[] {x, y, z};
		};
	}

	private static CompoundTag xyz(int x, int y, int z) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("x", x);
		tag.putInt("y", y);
		tag.putInt("z", z);
		return tag;
	}

	@Test
	void sitesLandWhereLitematicaPlacesEveryBlock() throws Exception {
		// One region saved away from the origin, with a negative size: blocks x 3..5, y -2..-1, z 7..8 from the origin.
		CompoundTag region = new CompoundTag();
		region.put("Position", xyz(5, -2, 7));
		region.put("Size", xyz(-3, 2, 2));
		ListTag palette = new ListTag();
		CompoundTag air = new CompoundTag();
		air.putString("Name", "minecraft:air");
		CompoundTag stone = new CompoundTag();
		stone.putString("Name", "minecraft:stone");
		palette.add(air);
		palette.add(stone);
		region.put("BlockStatePalette", palette);
		int[] values = new int[12];
		java.util.Arrays.fill(values, 1);
		region.putLongArray("BlockStates", SchematicReaderTest.pack(values, 2));
		CompoundTag regions = new CompoundTag();
		regions.put("Main", region);
		CompoundTag root = new CompoundTag();
		root.put("Regions", regions);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		NbtIo.writeCompressed(root, out);
		SchematicData data = SchematicReader.read("x.litematic", out.toByteArray(), SchematicReader.Limits.DEFAULT);
		assertEquals(3, data.originX);
		assertEquals(-2, data.originY);
		assertEquals(7, data.originZ);

		int ox = 100;
		int oy = 70;
		int oz = -50;
		for (PlacementTransform.Turn turn : PlacementTransform.Turn.values()) {
			for (PlacementTransform.Flip flip : PlacementTransform.Flip.values()) {
				PlacementTransform t = SyncmaticaLink.transform(ox, oy, oz, turn, flip, data.originX, data.originY, data.originZ);
				for (int ly = 0; ly < data.sizeY; ly++) {
					for (int lz = 0; lz < data.sizeZ; lz++) {
						for (int lx = 0; lx < data.sizeX; lx++) {
							// Litematica: origin + transform(position of the block from the file's origin).
							int[] rel = litematica(data.originX + lx, data.originY + ly, data.originZ + lz, turn, flip);
							String where = turn + "/" + flip + " local " + lx + "," + ly + "," + lz;
							assertEquals(ox + rel[0], t.worldX(lx, lz), where);
							assertEquals(oy + rel[1], t.worldY(ly), where);
							assertEquals(oz + rel[2], t.worldZ(lx, lz), where);
						}
					}
				}
			}
		}
	}
}
