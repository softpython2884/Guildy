package dev.guildy.builds;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three schematic formats read from NBT built in memory: Sponge v1 / v2 / v3 (var-int block data), Litematica
 * (tightly packed long arrays, an entry spanning two longs, negative sizes, regions merged), vanilla structures (void
 * cells), gzip or not, and the limits / corrupted files.
 */
class SchematicReaderTest {
	private static final SchematicReader.Limits LIMITS = new SchematicReader.Limits(64, 100_000, 50_000, 64L * 1024 * 1024);

	// ------------------------------------------------------------------ helpers

	private static byte[] gzip(CompoundTag root) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		NbtIo.writeCompressed(root, out);
		return out.toByteArray();
	}

	private static byte[] plain(CompoundTag root) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		NbtIo.write(root, new DataOutputStream(out));
		return out.toByteArray();
	}

	private static byte[] varints(int[] values) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (int value : values) {
			int v = value;
			while ((v & ~0x7F) != 0) {
				out.write((v & 0x7F) | 0x80);
				v >>>= 7;
			}
			out.write(v);
		}
		return out.toByteArray();
	}

	/** Litematica packing: entry i at bit i * bits, spanning two longs when needed. */
	static long[] pack(int[] values, int bits) {
		long[] packed = new long[(int) ((values.length * (long) bits + 63) / 64)];
		for (int i = 0; i < values.length; i++) {
			long start = (long) i * bits;
			int index = (int) (start >>> 6);
			int offset = (int) (start & 63);
			packed[index] |= ((long) values[i]) << offset;
			if (offset + bits > 64) {
				packed[index + 1] |= ((long) values[i]) >>> (64 - offset);
			}
		}
		return packed;
	}

	private static CompoundTag state(String name, String... props) {
		CompoundTag tag = new CompoundTag();
		tag.putString("Name", name);
		if (props.length > 0) {
			CompoundTag properties = new CompoundTag();
			for (int i = 0; i + 1 < props.length; i += 2) {
				properties.putString(props[i], props[i + 1]);
			}
			tag.put("Properties", properties);
		}
		return tag;
	}

	private static CompoundTag xyz(int x, int y, int z) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("x", x);
		tag.putInt("y", y);
		tag.putInt("z", z);
		return tag;
	}

	/** Sponge palette + data for a 2 × 2 × 2 box: stone floor, air top, one oak stairs. */
	private static CompoundTag spongeBlocks(CompoundTag into, boolean v3) {
		CompoundTag palette = new CompoundTag();
		palette.putInt("minecraft:air", 0);
		palette.putInt("minecraft:stone", 1);
		palette.putInt("minecraft:oak_stairs[half=bottom,facing=north]", 2);
		// index = (y * length + z) * width + x
		int[] data = {1, 1, 1, 1, 0, 2, 0, 0};
		if (v3) {
			CompoundTag blocks = new CompoundTag();
			blocks.put("Palette", palette);
			blocks.putByteArray("Data", varints(data));
			ListTag entities = new ListTag();
			CompoundTag sign = new CompoundTag();
			sign.put("Pos", new IntArrayTag(new int[] {0, 1, 1}));
			sign.putString("Id", "minecraft:sign");
			CompoundTag signData = new CompoundTag();
			signData.putBoolean("is_waxed", true);
			sign.put("Data", signData);
			entities.add(sign);
			blocks.put("BlockEntities", entities);
			into.put("Blocks", blocks);
		} else {
			into.put("Palette", palette);
			into.putInt("PaletteMax", 3);
			into.putByteArray("BlockData", varints(data));
		}
		into.putShort("Width", (short) 2);
		into.putShort("Height", (short) 2);
		into.putShort("Length", (short) 2);
		return into;
	}

	// ------------------------------------------------------------------ Sponge

	@Test
	void spongeV2() throws Exception {
		CompoundTag root = spongeBlocks(new CompoundTag(), false);
		root.putInt("Version", 2);
		root.putInt("DataVersion", 3700);
		SchematicData data = SchematicReader.read("maison.schem", gzip(root), LIMITS);
		assertEquals("schem", data.format);
		assertEquals(2, data.sizeX);
		assertEquals(2, data.sizeY);
		assertEquals(2, data.sizeZ);
		assertEquals(3700, data.dataVersion);
		assertEquals("minecraft:stone", data.stateAt(1, 0, 1));
		assertEquals("minecraft:air", data.stateAt(0, 1, 0));
		// Properties sorted in the canonical text.
		assertEquals("minecraft:oak_stairs[facing=north,half=bottom]", data.stateAt(1, 1, 0));
		assertEquals(5, data.nonAir);
	}

	@Test
	void spongeV3NestedWithBlockEntityData() throws Exception {
		CompoundTag inner = spongeBlocks(new CompoundTag(), true);
		inner.putInt("Version", 3);
		inner.putInt("DataVersion", 3955);
		CompoundTag root = new CompoundTag();
		root.put("Schematic", inner);
		SchematicData data = SchematicReader.read("tour.schem", plain(root), LIMITS);
		assertEquals("minecraft:oak_stairs[facing=north,half=bottom]", data.stateAt(1, 1, 0));
		assertEquals(3955, data.dataVersion);
		CompoundTag sign = data.blockEntities.get(data.index(0, 1, 1));
		assertTrue(sign != null && sign.getStringOr("id", "").equals("minecraft:sign") && sign.getBooleanOr("is_waxed", false),
				"v3 block entity: Data + id");
	}

	@Test
	void spongeV1HasNoDataVersion() throws Exception {
		CompoundTag root = spongeBlocks(new CompoundTag(), false);
		root.putInt("Version", 1);
		ListTag tiles = new ListTag();
		CompoundTag chest = new CompoundTag();
		chest.put("Pos", new IntArrayTag(new int[] {1, 0, 0}));
		chest.putString("Id", "minecraft:chest");
		tiles.add(chest);
		root.put("TileEntities", tiles);
		SchematicData data = SchematicReader.read("vieux.schematic", gzip(root), LIMITS);
		assertEquals(SchematicReader.SPONGE_V1_DATA_VERSION, data.dataVersion);
		assertEquals("minecraft:chest", data.blockEntities.get(data.index(1, 0, 0)).getStringOr("id", ""));
	}

	@Test
	void spongeLargeVarIntPalette() throws Exception {
		// 200 palette entries: indexes above 127 take two var-int bytes.
		CompoundTag root = new CompoundTag();
		CompoundTag palette = new CompoundTag();
		int[] data = new int[200];
		for (int i = 0; i < 200; i++) {
			palette.putInt("testmod:block_" + i, i);
			data[i] = 199 - i;
		}
		root.put("Palette", palette);
		root.putByteArray("BlockData", varints(data));
		root.putShort("Width", (short) 10);
		root.putShort("Height", (short) 2);
		root.putShort("Length", (short) 10);
		root.putInt("Version", 2);
		SchematicData read = SchematicReader.read("blocs.schem", gzip(root), LIMITS);
		assertEquals(200, read.nonAir);
		assertEquals("testmod:block_199", read.palette.get(read.cells[0]));
		assertEquals("testmod:block_0", read.palette.get(read.cells[199]));
		assertEquals("testmod:block_72", read.palette.get(read.cells[127]));
	}

	@Test
	void spongeTruncatedDataIsAClearError() throws Exception {
		CompoundTag root = spongeBlocks(new CompoundTag(), false);
		root.putByteArray("BlockData", varints(new int[] {1, 1, 1}));
		SchematicException e = assertThrows(SchematicException.class, () -> SchematicReader.read("x.schem", gzip(root), LIMITS));
		assertEquals("build.error.corrupt", e.key());
	}

	@Test
	void spongeUnknownPaletteIndex() throws Exception {
		CompoundTag root = spongeBlocks(new CompoundTag(), false);
		root.putByteArray("BlockData", varints(new int[] {1, 1, 1, 1, 0, 9, 0, 0}));
		assertEquals("build.error.corrupt", assertThrows(SchematicException.class, () -> SchematicReader.read("x.schem", gzip(root), LIMITS)).key());
	}

	// ------------------------------------------------------------------ Litematica

	@Test
	void litematicPackedArraySpanningLongs() {
		// 5 bits per entry (palette of 17): entries cross long boundaries at 12, 25, 38...
		int[] values = new int[100];
		for (int i = 0; i < values.length; i++) {
			values[i] = (i * 7) % 17;
		}
		long[] packed = pack(values, 5);
		for (int i = 0; i < values.length; i++) {
			assertEquals(values[i], SchematicReader.packedValue(packed, i, 5, 31), "entry " + i);
		}
	}

	@Test
	void litematicNegativeSizesAndMergedRegions() throws Exception {
		CompoundTag regions = new CompoundTag();
		// Region A: position (0,0,0), size (2,1,2) → cells x 0..1, z 0..1.
		CompoundTag a = new CompoundTag();
		a.put("Position", xyz(0, 0, 0));
		a.put("Size", xyz(2, 1, 2));
		ListTag paletteA = new ListTag();
		paletteA.add(state("minecraft:air"));
		paletteA.add(state("minecraft:stone"));
		a.put("BlockStatePalette", paletteA);
		a.putLongArray("BlockStates", pack(new int[] {1, 1, 1, 1}, 2));
		regions.put("A", a);
		// Region B: position (3,1,1), size (-2,-2,1) → x 2..3, y 0..1, z 1.
		CompoundTag b = new CompoundTag();
		b.put("Position", xyz(3, 1, 1));
		b.put("Size", xyz(-2, -2, 1));
		ListTag paletteB = new ListTag();
		paletteB.add(state("minecraft:air"));
		paletteB.add(state("minecraft:oak_planks"));
		paletteB.add(state("minecraft:oak_log", "axis", "x"));
		b.put("BlockStatePalette", paletteB);
		// order (y, z, x) relative to B's min corner (2, 0, 1): y0: x2=planks, x3=log; y1: x2=air, x3=planks
		b.putLongArray("BlockStates", pack(new int[] {1, 2, 0, 1}, 2));
		ListTag tiles = new ListTag();
		CompoundTag sign = new CompoundTag();
		sign.putInt("x", 1);
		sign.putInt("y", 1);
		sign.putInt("z", 0);
		sign.putString("id", "minecraft:sign");
		tiles.add(sign);
		b.put("TileEntities", tiles);
		regions.put("B", b);
		CompoundTag root = new CompoundTag();
		root.put("Regions", regions);
		root.putInt("MinecraftDataVersion", 4325);
		root.putInt("Version", 6);
		SchematicData data = SchematicReader.read("ferme.litematic", gzip(root), LIMITS);
		assertEquals(4, data.sizeX);
		assertEquals(2, data.sizeY);
		assertEquals(2, data.sizeZ);
		assertEquals(4325, data.dataVersion);
		assertEquals("minecraft:stone", data.stateAt(0, 0, 0));
		assertEquals("minecraft:stone", data.stateAt(1, 0, 1));
		assertEquals("minecraft:oak_planks", data.stateAt(2, 0, 1));
		assertEquals("minecraft:oak_log[axis=x]", data.stateAt(3, 0, 1));
		assertEquals("minecraft:air", data.stateAt(2, 1, 1));
		assertEquals("minecraft:oak_planks", data.stateAt(3, 1, 1));
		// Not covered by any region: void.
		assertEquals(BlockStateString.STRUCTURE_VOID, data.stateAt(2, 0, 0));
		assertTrue(data.blockEntities.containsKey(data.index(3, 1, 1)), "tile entity at its region-relative position");
		assertEquals(7, data.nonAir);
	}

	@Test
	void litematicShortBlockStatesIsCorrupt() throws Exception {
		CompoundTag region = new CompoundTag();
		region.put("Position", xyz(0, 0, 0));
		region.put("Size", xyz(8, 8, 8));
		ListTag palette = new ListTag();
		palette.add(state("minecraft:air"));
		palette.add(state("minecraft:stone"));
		region.put("BlockStatePalette", palette);
		region.putLongArray("BlockStates", new long[2]);
		CompoundTag regions = new CompoundTag();
		regions.put("R", region);
		CompoundTag root = new CompoundTag();
		root.put("Regions", regions);
		assertEquals("build.error.corrupt", assertThrows(SchematicException.class, () -> SchematicReader.read("x.litematic", gzip(root), LIMITS)).key());
	}

	// ------------------------------------------------------------------ vanilla structure

	@Test
	void vanillaStructureListsOnlyItsBlocks() throws Exception {
		CompoundTag root = new CompoundTag();
		ListTag size = new ListTag();
		size.add(IntTag.valueOf(3));
		size.add(IntTag.valueOf(2));
		size.add(IntTag.valueOf(1));
		root.put("size", size);
		ListTag palette = new ListTag();
		palette.add(state("minecraft:cobblestone"));
		palette.add(state("minecraft:air"));
		palette.add(state("minecraft:wall_torch", "facing", "east"));
		root.put("palette", palette);
		ListTag blocks = new ListTag();
		blocks.add(block(0, 0, 0, 0, null));
		blocks.add(block(1, 0, 0, 0, null));
		blocks.add(block(1, 1, 0, 1, null));
		CompoundTag nbt = new CompoundTag();
		nbt.putString("id", "minecraft:sign");
		blocks.add(block(2, 1, 0, 2, nbt));
		root.put("blocks", blocks);
		root.putInt("DataVersion", 3465);
		SchematicData data = SchematicReader.read("moulin.nbt", gzip(root), LIMITS);
		assertEquals("nbt", data.format);
		assertEquals("minecraft:cobblestone", data.stateAt(0, 0, 0));
		assertEquals("minecraft:air", data.stateAt(1, 1, 0));
		assertEquals("minecraft:wall_torch[facing=east]", data.stateAt(2, 1, 0));
		// (2,0,0) and (0,1,0) are not listed: nothing to do there.
		assertEquals(BlockStateString.STRUCTURE_VOID, data.stateAt(2, 0, 0));
		assertEquals(BlockStateString.STRUCTURE_VOID, data.stateAt(0, 1, 0));
		assertEquals(3, data.nonAir);
		assertTrue(data.blockEntities.containsKey(data.index(2, 1, 0)));
		List<Map.Entry<String, Integer>> top = data.topBlocks(1);
		assertEquals("minecraft:cobblestone", top.getFirst().getKey());
		assertEquals(2, top.getFirst().getValue());
	}

	private static CompoundTag block(int x, int y, int z, int state, CompoundTag nbt) {
		CompoundTag tag = new CompoundTag();
		ListTag pos = new ListTag();
		pos.add(IntTag.valueOf(x));
		pos.add(IntTag.valueOf(y));
		pos.add(IntTag.valueOf(z));
		tag.put("pos", pos);
		tag.putInt("state", state);
		if (nbt != null) {
			tag.put("nbt", nbt);
		}
		return tag;
	}

	// ------------------------------------------------------------------ limits

	@Test
	void limitsAreCheckedBeforeAllocating() throws Exception {
		CompoundTag root = new CompoundTag();
		root.putShort("Width", (short) 200);
		root.putShort("Height", (short) 2);
		root.putShort("Length", (short) 2);
		CompoundTag palette = new CompoundTag();
		palette.putInt("minecraft:stone", 0);
		root.put("Palette", palette);
		root.putByteArray("BlockData", new byte[0]);
		assertEquals("build.error.too_big", assertThrows(SchematicException.class, () -> SchematicReader.read("x.schem", gzip(root), LIMITS)).key());
		SchematicReader.Limits small = new SchematicReader.Limits(64, 100_000, 3, 1 << 20);
		CompoundTag ok = spongeBlocks(new CompoundTag(), false);
		assertEquals("build.error.too_many_blocks", assertThrows(SchematicException.class, () -> SchematicReader.read("x.schem", gzip(ok), small)).key());
	}

	@Test
	void garbageIsCorruptNeverACrash() {
		byte[] garbage = new byte[] {0x1F, (byte) 0x8B, 1, 2, 3, 4, 5};
		assertEquals("build.error.corrupt", assertThrows(SchematicException.class, () -> SchematicReader.read("x.schem", garbage, LIMITS)).key());
		assertEquals("build.error.format", assertThrows(SchematicException.class, () -> SchematicReader.read("x.txt", new byte[0], LIMITS)).key());
	}

	@Test
	void legacyMcEditFilesAreRefusedWithAReason() throws Exception {
		CompoundTag root = new CompoundTag();
		root.putShort("Width", (short) 1);
		root.putShort("Height", (short) 1);
		root.putShort("Length", (short) 1);
		root.putString("Materials", "Alpha");
		root.putByteArray("Blocks", new byte[] {1});
		root.putByteArray("Data", new byte[] {0});
		assertEquals("build.error.legacy", assertThrows(SchematicException.class, () -> SchematicReader.read("old.schematic", gzip(root), LIMITS)).key());
	}

	@Test
	void emptySchematicIsRefused() throws Exception {
		CompoundTag root = new CompoundTag();
		CompoundTag palette = new CompoundTag();
		palette.putInt("minecraft:air", 0);
		root.put("Palette", palette);
		root.putByteArray("BlockData", varints(new int[] {0}));
		root.putShort("Width", (short) 1);
		root.putShort("Height", (short) 1);
		root.putShort("Length", (short) 1);
		assertEquals("build.error.empty", assertThrows(SchematicException.class, () -> SchematicReader.read("x.schem", gzip(root), LIMITS)).key());
	}

	@Test
	void formatsByExtension() {
		assertEquals("schem", SchematicReader.formatOf("A.SCHEM"));
		assertEquals("schem", SchematicReader.formatOf("old.schematic"));
		assertEquals("litematic", SchematicReader.formatOf("x.litematic"));
		assertEquals("nbt", SchematicReader.formatOf("x.nbt"));
		assertEquals(null, SchematicReader.formatOf("x.json"));
	}
}
