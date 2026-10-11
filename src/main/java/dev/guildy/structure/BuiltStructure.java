package dev.guildy.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * A validated multiblock owned by a guild. Shared data contract between the structure system and the
 * features built on it (quest board, shop, reception...).
 */
public class BuiltStructure {
	/** Structure definition id (e.g. "quest_board"). */
	public String type;
	/** Bottom-left block as seen by a player standing in front of the structure. */
	public BlockPos origin;
	/** Direction the front face points to (managed signs are at block.relative(facing)). */
	public Direction facing;
	public int width;
	public int height;
	/** Every block of the pattern. */
	public List<BlockPos> blocks = new ArrayList<>();
	/** Managed sign positions; the index is the sign slot (quest board: slot = quest index). */
	public List<BlockPos> signs = new ArrayList<>();
	public long builtAt;

	public boolean containsBlock(BlockPos pos) {
		return blocks.contains(pos);
	}

	public int signIndex(BlockPos pos) {
		return signs.indexOf(pos);
	}
}
