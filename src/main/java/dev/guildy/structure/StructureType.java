package dev.guildy.structure;

import dev.guildy.core.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A validated structure definition with its parsed pattern and resolved block keys (runtime view).
 */
public record StructureType(StructureDefinition def, StructurePattern pattern, Map<Character, BlockKey> keys) {

	public String id() {
		return def.id;
	}

	/** Colorized display name. */
	public Component name() {
		return Component.literal(Lang.colorize(def.name));
	}

	public String plainName() {
		return Lang.strip(Lang.colorize(def.name));
	}

	public int width() {
		return pattern.width();
	}

	public int height() {
		return pattern.height();
	}

	public BlockKey key(char c) {
		return keys.get(c);
	}

	/** World position of cell (col, row) for a structure whose bottom-left block is {@code origin}. */
	public BlockPos cellPos(BlockPos origin, Direction facing, int col, int row) {
		int[] offset = Orientation.cellOffset(col, row, pattern.height(), facing.getStepX(), facing.getStepZ());
		return origin.offset(offset[0], offset[1], offset[2]);
	}

	/** Positions of every non-wildcard cell (reading order). */
	public List<BlockPos> blockPositions(BlockPos origin, Direction facing) {
		List<BlockPos> list = new ArrayList<>(pattern.blockCount());
		for (int row = 0; row < pattern.height(); row++) {
			for (int col = 0; col < pattern.width(); col++) {
				if (!pattern.isWildcard(col, row)) {
					list.add(cellPos(origin, facing, col, row));
				}
			}
		}
		return list;
	}

	/** Managed sign positions (in front of the sign cells), in sign index order. */
	public List<BlockPos> signPositions(BlockPos origin, Direction facing) {
		List<BlockPos> list = new ArrayList<>(def.signCount());
		for (int[] cell : def.signCells) {
			list.add(cellPos(origin, facing, cell[0], cell[1]).relative(facing));
		}
		return list;
	}

	/** Block cell carrying sign {@code index}. */
	public BlockPos signSupport(BlockPos origin, Direction facing, int index) {
		int[] cell = def.signCells.get(index);
		return cellPos(origin, facing, cell[0], cell[1]);
	}
}
