package dev.guildy.builds;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * Reads the three schematic formats into a {@link SchematicData} (pure: NBT classes only, unit tested on files built in
 * memory). Every size and index is checked against the {@link Limits} before anything is allocated: a corrupted or huge
 * file gives a {@link SchematicException} with a clear reason, never an out-of-memory error or a crash.
 * <ul>
 * <li>Sponge {@code .schem} (WorldEdit / FAWE) versions 1, 2 and 3: palette "id[props]" → index, block data as
 * var-ints in x, z, y order (the cell order of {@link SchematicData}); block entities with {@code Pos}.</li>
 * <li>Litematica {@code .litematic}: every region (signed sizes: a negative size extends towards lower coordinates) is
 * merged into one enclosing box; block states are tightly packed in a long array (an entry may span two longs, at least
 * 2 bits per entry); later regions overwrite earlier ones where they overlap (air never overwrites a block).</li>
 * <li>Vanilla structure {@code .nbt} (structure blocks): listed blocks only, the other cells stay void ("nothing to do",
 * like a structure void); the first palette of a multi-palette structure.</li>
 * </ul>
 */
public final class SchematicReader {
	/** Sponge v1 files carry no data version: they were written by 1.13.2. */
	static final int SPONGE_V1_DATA_VERSION = 1631;

	/** Size limits (builds.json5 → schematics). */
	public record Limits(int maxAxis, int maxVolume, int maxBlocks, long maxNbtBytes) {
		public static final Limits DEFAULT = new Limits(256, 2_097_152, 500_000, 256L * 1024 * 1024);
	}

	private SchematicReader() {
	}

	/** The format of a file name: "schem", "litematic", "nbt", or null when not a schematic. */
	public static @Nullable String formatOf(String fileName) {
		String name = fileName.toLowerCase(Locale.ROOT);
		if (name.endsWith(".schem") || name.endsWith(".schematic")) {
			return "schem";
		}
		if (name.endsWith(".litematic")) {
			return "litematic";
		}
		if (name.endsWith(".nbt")) {
			return "nbt";
		}
		return null;
	}

	/** Reads a file's bytes (gzip or not) in the format of its name. */
	public static SchematicData read(String fileName, byte[] bytes, Limits limits) throws SchematicException {
		String format = formatOf(fileName);
		if (format == null) {
			throw new SchematicException("build.error.format", fileName);
		}
		CompoundTag root = readNbt(bytes, limits);
		return switch (format) {
			case "schem" -> sponge(root, limits);
			case "litematic" -> litematic(root, limits);
			default -> structure(root, limits);
		};
	}

	/** Root compound of a gzip-compressed (or plain) NBT file. */
	static CompoundTag readNbt(byte[] bytes, Limits limits) throws SchematicException {
		try {
			InputStream in = new ByteArrayInputStream(bytes);
			if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0x1F && (bytes[1] & 0xFF) == 0x8B) {
				in = new GZIPInputStream(in);
			}
			try (DataInputStream data = new DataInputStream(new BufferedInputStream(in))) {
				return NbtIo.read(data, NbtAccounter.create(limits.maxNbtBytes()));
			}
		} catch (IOException | RuntimeException e) {
			String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			if (reason.length() > 120) {
				reason = reason.substring(0, 120) + "...";
			}
			throw new SchematicException("build.error.corrupt", reason);
		}
	}

	// ================================================================ Sponge

	static SchematicData sponge(CompoundTag file, Limits limits) throws SchematicException {
		CompoundTag root = file.getCompound("Schematic").orElse(file);
		int version = root.getIntOr("Version", root.contains("Blocks") ? 3 : root.contains("BlockData") ? 2 : 0);
		int width = root.getShortOr("Width", (short) 0) & 0xFFFF;
		int height = root.getShortOr("Height", (short) 0) & 0xFFFF;
		int length = root.getShortOr("Length", (short) 0) & 0xFFFF;
		checkSize(width, height, length, limits);
		CompoundTag paletteTag;
		byte[] data;
		ListTag entities;
		if (version >= 3) {
			CompoundTag blocks = root.getCompoundOrEmpty("Blocks");
			paletteTag = blocks.getCompoundOrEmpty("Palette");
			data = blocks.getByteArray("Data").orElse(null);
			entities = blocks.getListOrEmpty("BlockEntities");
		} else {
			paletteTag = root.getCompoundOrEmpty("Palette");
			data = root.getByteArray("BlockData").orElse(null);
			entities = root.contains("BlockEntities") ? root.getListOrEmpty("BlockEntities") : root.getListOrEmpty("TileEntities");
		}
		if (data == null || paletteTag.isEmpty()) {
			throw new SchematicException("build.error.no_blocks");
		}
		int dataVersion = root.getIntOr("DataVersion", version <= 1 ? SPONGE_V1_DATA_VERSION : 0);
		List<String> warnings = new ArrayList<>();
		SchematicData.PaletteBuilder palette = new SchematicData.PaletteBuilder();
		// File palette index → our palette index.
		Map<Integer, Integer> remap = new HashMap<>();
		int maxIndex = -1;
		for (String key : paletteTag.keySet()) {
			int index = paletteTag.getIntOr(key, -1);
			if (index < 0 || index > 1_000_000) {
				throw new SchematicException("build.error.corrupt", "palette index " + index);
			}
			int ours = palette.add(key);
			if (ours == SchematicData.VOID && BlockStateString.parse(key) == null) {
				warnings.add("build.warning.bad_state|" + key);
			}
			remap.put(index, ours);
			maxIndex = Math.max(maxIndex, index);
		}
		int[] lookup = new int[maxIndex + 1];
		java.util.Arrays.fill(lookup, -1);
		remap.forEach((file1, ours) -> lookup[file1] = ours);
		int volume = width * height * length;
		int[] cells = new int[volume];
		int pos = 0;
		int cell = 0;
		while (cell < volume) {
			if (pos >= data.length) {
				throw new SchematicException("build.error.corrupt", "block data too short (" + cell + "/" + volume + ")");
			}
			int value = 0;
			int shift = 0;
			while (true) {
				if (pos >= data.length) {
					throw new SchematicException("build.error.corrupt", "truncated var-int");
				}
				byte b = data[pos++];
				value |= (b & 0x7F) << shift;
				if ((b & 0x80) == 0) {
					break;
				}
				shift += 7;
				if (shift > 28) {
					throw new SchematicException("build.error.corrupt", "var-int too long");
				}
			}
			if (value < 0 || value >= lookup.length || lookup[value] < 0) {
				throw new SchematicException("build.error.corrupt", "unknown palette index " + value);
			}
			cells[cell++] = lookup[value];
		}
		Map<Integer, CompoundTag> blockEntities = new HashMap<>();
		for (Tag tag : entities) {
			if (!(tag instanceof CompoundTag entry)) {
				continue;
			}
			int[] p = entry.getIntArray("Pos").orElse(null);
			if (p == null || p.length < 3 || !inside(p[0], p[1], p[2], width, height, length)) {
				continue;
			}
			CompoundTag nbt;
			if (version >= 3) {
				nbt = entry.getCompoundOrEmpty("Data").copy();
			} else {
				nbt = entry.copy();
				nbt.remove("Pos");
				nbt.remove("Id");
				nbt.remove("ContentVersion");
			}
			String id = entry.getStringOr("Id", nbt.getStringOr("id", ""));
			if (!id.isEmpty()) {
				nbt.putString("id", id);
			}
			blockEntities.put((p[1] * length + p[2]) * width + p[0], nbt);
		}
		return finish("schem", width, height, length, palette, cells, blockEntities, dataVersion, warnings, limits);
	}

	// ================================================================ Litematica

	private record Region(String name, int minX, int minY, int minZ, int sx, int sy, int sz, CompoundTag tag) {
	}

	static SchematicData litematic(CompoundTag root, Limits limits) throws SchematicException {
		CompoundTag regionsTag = root.getCompoundOrEmpty("Regions");
		if (regionsTag.isEmpty()) {
			throw new SchematicException("build.error.no_blocks");
		}
		List<Region> regions = new ArrayList<>();
		long minX = Long.MAX_VALUE;
		long minY = Long.MAX_VALUE;
		long minZ = Long.MAX_VALUE;
		long maxX = Long.MIN_VALUE;
		long maxY = Long.MIN_VALUE;
		long maxZ = Long.MIN_VALUE;
		for (String name : new TreeMap<>(asMap(regionsTag)).keySet()) {
			CompoundTag region = regionsTag.getCompoundOrEmpty(name);
			CompoundTag position = region.getCompoundOrEmpty("Position");
			CompoundTag size = region.getCompoundOrEmpty("Size");
			int px = position.getIntOr("x", 0);
			int py = position.getIntOr("y", 0);
			int pz = position.getIntOr("z", 0);
			int sx = size.getIntOr("x", 0);
			int sy = size.getIntOr("y", 0);
			int sz = size.getIntOr("z", 0);
			int ax = Math.abs(sx);
			int ay = Math.abs(sy);
			int az = Math.abs(sz);
			checkSize(ax, ay, az, limits);
			// Litematica: end = position + size - 1 (positive) or + size + 1 (negative); min corner = min(position, end).
			int rx = sx < 0 ? px + sx + 1 : px;
			int ry = sy < 0 ? py + sy + 1 : py;
			int rz = sz < 0 ? pz + sz + 1 : pz;
			regions.add(new Region(name, rx, ry, rz, ax, ay, az, region));
			minX = Math.min(minX, rx);
			minY = Math.min(minY, ry);
			minZ = Math.min(minZ, rz);
			maxX = Math.max(maxX, (long) rx + ax - 1);
			maxY = Math.max(maxY, (long) ry + ay - 1);
			maxZ = Math.max(maxZ, (long) rz + az - 1);
		}
		long width = maxX - minX + 1;
		long height = maxY - minY + 1;
		long length = maxZ - minZ + 1;
		if (width > limits.maxAxis() || height > limits.maxAxis() || length > limits.maxAxis()) {
			throw new SchematicException("build.error.too_big", width + "×" + height + "×" + length, limits.maxAxis());
		}
		checkSize((int) width, (int) height, (int) length, limits);
		int w = (int) width;
		int h = (int) height;
		int l = (int) length;
		int[] cells = new int[w * h * l];
		SchematicData.PaletteBuilder palette = new SchematicData.PaletteBuilder();
		List<String> warnings = new ArrayList<>();
		Map<Integer, CompoundTag> blockEntities = new HashMap<>();
		for (Region region : regions) {
			ListTag paletteList = region.tag().getListOrEmpty("BlockStatePalette");
			if (paletteList.isEmpty()) {
				warnings.add("build.warning.empty_region|" + region.name());
				continue;
			}
			int[] lookup = new int[paletteList.size()];
			boolean[] air = new boolean[paletteList.size()];
			for (int i = 0; i < paletteList.size(); i++) {
				String text = stateText(paletteList.getCompoundOrEmpty(i));
				lookup[i] = palette.add(text);
				BlockStateString state = BlockStateString.parse(text);
				air[i] = state == null || state.isAir();
				if (state == null) {
					warnings.add("build.warning.bad_state|" + text);
				}
			}
			long[] packed = region.tag().getLongArray("BlockStates").orElse(new long[0]);
			int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, paletteList.size() - 1)));
			long volume = (long) region.sx() * region.sy() * region.sz();
			if ((long) packed.length * 64 < volume * bits) {
				throw new SchematicException("build.error.corrupt", "region " + region.name() + ": block states too short");
			}
			long mask = (1L << bits) - 1;
			int ox = (int) (region.minX() - minX);
			int oy = (int) (region.minY() - minY);
			int oz = (int) (region.minZ() - minZ);
			for (int y = 0; y < region.sy(); y++) {
				for (int z = 0; z < region.sz(); z++) {
					for (int x = 0; x < region.sx(); x++) {
						long index = ((long) y * region.sz() + z) * region.sx() + x;
						int value = packedValue(packed, index, bits, mask);
						if (value < 0 || value >= lookup.length) {
							throw new SchematicException("build.error.corrupt", "region " + region.name() + ": palette index " + value);
						}
						int target = ((oy + y) * l + (oz + z)) * w + (ox + x);
						if (air[value] && cells[target] != SchematicData.VOID) {
							continue;
						}
						cells[target] = lookup[value];
					}
				}
			}
			for (Tag tag : region.tag().getListOrEmpty("TileEntities")) {
				if (!(tag instanceof CompoundTag entry)) {
					continue;
				}
				int x = entry.getIntOr("x", -1);
				int y = entry.getIntOr("y", -1);
				int z = entry.getIntOr("z", -1);
				if (!inside(x, y, z, region.sx(), region.sy(), region.sz())) {
					continue;
				}
				CompoundTag nbt = entry.copy();
				nbt.remove("x");
				nbt.remove("y");
				nbt.remove("z");
				blockEntities.put(((oy + y) * l + (oz + z)) * w + (ox + x), nbt);
			}
		}
		int dataVersion = root.getIntOr("MinecraftDataVersion", 0);
		return finish("litematic", w, h, l, palette, cells, blockEntities, dataVersion, warnings, limits);
	}

	/** Entry {@code index} of a Litematica bit array (entries are tightly packed: one may span two longs). */
	static int packedValue(long[] packed, long index, int bits, long mask) {
		long start = index * bits;
		int startLong = (int) (start >>> 6);
		int endLong = (int) ((start + bits - 1) >>> 6);
		int offset = (int) (start & 63);
		if (startLong == endLong) {
			return (int) ((packed[startLong] >>> offset) & mask);
		}
		return (int) (((packed[startLong] >>> offset) | (packed[endLong] << (64 - offset))) & mask);
	}

	// ================================================================ vanilla structure

	static SchematicData structure(CompoundTag root, Limits limits) throws SchematicException {
		ListTag size = root.getListOrEmpty("size");
		if (size.size() < 3) {
			throw new SchematicException("build.error.no_blocks");
		}
		int w = size.getIntOr(0, 0);
		int h = size.getIntOr(1, 0);
		int l = size.getIntOr(2, 0);
		checkSize(w, h, l, limits);
		ListTag paletteList = root.getListOrEmpty("palette");
		if (paletteList.isEmpty()) {
			ListTag palettes = root.getListOrEmpty("palettes");
			if (!palettes.isEmpty()) {
				paletteList = palettes.getListOrEmpty(0);
			}
		}
		if (paletteList.isEmpty()) {
			throw new SchematicException("build.error.no_blocks");
		}
		SchematicData.PaletteBuilder palette = new SchematicData.PaletteBuilder();
		List<String> warnings = new ArrayList<>();
		int[] lookup = new int[paletteList.size()];
		for (int i = 0; i < paletteList.size(); i++) {
			String text = stateText(paletteList.getCompoundOrEmpty(i));
			lookup[i] = palette.add(text);
			if (BlockStateString.parse(text) == null) {
				warnings.add("build.warning.bad_state|" + text);
			}
		}
		int[] cells = new int[w * h * l];
		Map<Integer, CompoundTag> blockEntities = new HashMap<>();
		for (Tag tag : root.getListOrEmpty("blocks")) {
			if (!(tag instanceof CompoundTag block)) {
				continue;
			}
			ListTag pos = block.getListOrEmpty("pos");
			int x = pos.getIntOr(0, -1);
			int y = pos.getIntOr(1, -1);
			int z = pos.getIntOr(2, -1);
			int state = block.getIntOr("state", -1);
			if (pos.size() < 3 || !inside(x, y, z, w, h, l) || state < 0 || state >= lookup.length) {
				continue;
			}
			int index = (y * l + z) * w + x;
			cells[index] = lookup[state];
			CompoundTag nbt = block.getCompound("nbt").orElse(null);
			if (nbt != null) {
				blockEntities.put(index, nbt.copy());
			}
		}
		int dataVersion = root.getIntOr("DataVersion", 0);
		return finish("nbt", w, h, l, palette, cells, blockEntities, dataVersion, warnings, limits);
	}

	// ================================================================ helpers

	/** "minecraft:oak_stairs[facing=north,...]" of a {Name, Properties} compound. */
	static String stateText(CompoundTag tag) {
		String name = tag.getStringOr("Name", "");
		CompoundTag props = tag.getCompoundOrEmpty("Properties");
		if (props.isEmpty()) {
			return name;
		}
		StringBuilder sb = new StringBuilder(name).append('[');
		boolean first = true;
		for (String key : new TreeMap<>(asMap(props)).keySet()) {
			if (!first) {
				sb.append(',');
			}
			sb.append(key).append('=').append(props.getStringOr(key, ""));
			first = false;
		}
		return sb.append(']').toString();
	}

	private static Map<String, Tag> asMap(CompoundTag tag) {
		Map<String, Tag> map = new HashMap<>();
		for (String key : tag.keySet()) {
			map.put(key, tag.get(key));
		}
		return map;
	}

	private static void checkSize(int x, int y, int z, Limits limits) throws SchematicException {
		if (x <= 0 || y <= 0 || z <= 0) {
			throw new SchematicException("build.error.empty");
		}
		if (x > limits.maxAxis() || y > limits.maxAxis() || z > limits.maxAxis()) {
			throw new SchematicException("build.error.too_big", x + "×" + y + "×" + z, limits.maxAxis());
		}
		long volume = (long) x * y * z;
		if (volume > limits.maxVolume()) {
			throw new SchematicException("build.error.too_large", volume, limits.maxVolume());
		}
	}

	private static boolean inside(int x, int y, int z, int w, int h, int l) {
		return x >= 0 && y >= 0 && z >= 0 && x < w && y < h && z < l;
	}

	private static SchematicData finish(String format, int w, int h, int l, SchematicData.PaletteBuilder palette, int[] cells,
			Map<Integer, CompoundTag> blockEntities, int dataVersion, List<String> warnings, Limits limits) throws SchematicException {
		SchematicData data = new SchematicData(format, w, h, l, palette.entries(), cells, blockEntities, dataVersion, warnings);
		if (data.nonAir == 0) {
			throw new SchematicException("build.error.empty");
		}
		if (data.nonAir > limits.maxBlocks()) {
			throw new SchematicException("build.error.too_many_blocks", data.nonAir, limits.maxBlocks());
		}
		return data;
	}
}
