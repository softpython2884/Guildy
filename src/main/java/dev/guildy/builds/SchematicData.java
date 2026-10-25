package dev.guildy.builds;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A schematic read from a file, before its block states are resolved (pure: block states are canonical texts, see
 * {@link BlockStateString}). One box of {@code sizeX × sizeY × sizeZ} cells; cell index = (y · sizeZ + z) · sizeX + x
 * (x fastest, the Sponge order). Each cell holds a palette index; index {@link #VOID} (0) is "nothing to do" (a cell a
 * vanilla structure does not list, a structure void), every other entry is a block state text (air included: an
 * explicit air cell may be cleared).
 */
public final class SchematicData {
	public static final int VOID = 0;

	public final String format;
	public final int sizeX;
	public final int sizeY;
	public final int sizeZ;
	/** Palette: entry 0 = {@link BlockStateString#STRUCTURE_VOID}, then the states in order of first use. */
	public final List<String> palette;
	public final int[] cells;
	/** Block entity data by cell index (as saved, before any upgrade). */
	public final Map<Integer, CompoundTag> blockEntities;
	/** Minecraft data version of the file (0 = unknown, then no upgrade is done). */
	public final int dataVersion;
	/** Problems that did not prevent reading (lang key + arguments, already formatted as "key|arg|arg"). */
	public final List<String> warnings;
	/** Cells that are neither void nor air. */
	public final int nonAir;

	public SchematicData(String format, int sizeX, int sizeY, int sizeZ, List<String> palette, int[] cells,
			Map<Integer, CompoundTag> blockEntities, int dataVersion, List<String> warnings) {
		this.format = format;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.palette = List.copyOf(palette);
		this.cells = cells;
		this.blockEntities = Collections.unmodifiableMap(new HashMap<>(blockEntities));
		this.dataVersion = dataVersion;
		this.warnings = List.copyOf(warnings);
		boolean[] air = new boolean[this.palette.size()];
		for (int i = 0; i < air.length; i++) {
			BlockStateString state = BlockStateString.parse(this.palette.get(i));
			air[i] = i == VOID || state == null || state.isAir() || state.isStructureVoid();
		}
		int count = 0;
		for (int cell : cells) {
			if (cell > 0 && cell < air.length && !air[cell]) {
				count++;
			}
		}
		this.nonAir = count;
	}

	public int volume() {
		return sizeX * sizeY * sizeZ;
	}

	public int index(int x, int y, int z) {
		return (y * sizeZ + z) * sizeX + x;
	}

	public int x(int index) {
		return index % sizeX;
	}

	public int z(int index) {
		return (index / sizeX) % sizeZ;
	}

	public int y(int index) {
		return index / (sizeX * sizeZ);
	}

	/** Palette text of the cell at (x, y, z). */
	public String stateAt(int x, int y, int z) {
		return palette.get(cells[index(x, y, z)]);
	}

	/** Non-air block counts by block id, most used first (menu lore: main materials). */
	public List<Map.Entry<String, Integer>> topBlocks(int limit) {
		int[] uses = new int[palette.size()];
		for (int cell : cells) {
			if (cell > 0 && cell < uses.length) {
				uses[cell]++;
			}
		}
		Map<String, Integer> byId = new LinkedHashMap<>();
		for (int i = 1; i < uses.length; i++) {
			BlockStateString state = BlockStateString.parse(palette.get(i));
			if (uses[i] == 0 || state == null || state.isAir() || state.isStructureVoid()) {
				continue;
			}
			byId.merge(state.id(), uses[i], Integer::sum);
		}
		List<Map.Entry<String, Integer>> list = new ArrayList<>(byId.entrySet());
		list.sort((a, b) -> b.getValue() - a.getValue());
		return list.size() > limit ? List.copyOf(list.subList(0, limit)) : List.copyOf(list);
	}

	/** Builds the palette while reading: deduplicates by canonical text. */
	static final class PaletteBuilder {
		private final List<String> entries = new ArrayList<>();
		private final Map<String, Integer> index = new HashMap<>();

		PaletteBuilder() {
			entries.add(BlockStateString.STRUCTURE_VOID);
			index.put(BlockStateString.STRUCTURE_VOID, VOID);
		}

		/** Palette index of a state text; unreadable text and structure voids give {@link #VOID}. */
		int add(String text) {
			BlockStateString state = BlockStateString.parse(text);
			if (state == null || state.isStructureVoid()) {
				return VOID;
			}
			String key = state.isAir() ? BlockStateString.AIR : state.toString();
			Integer known = index.get(key);
			if (known != null) {
				return known;
			}
			int id = entries.size();
			entries.add(key);
			index.put(key, id);
			return id;
		}

		List<String> entries() {
			return entries;
		}
	}
}
