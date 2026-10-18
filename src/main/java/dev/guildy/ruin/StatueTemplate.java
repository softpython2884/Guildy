package dev.guildy.ruin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Geometry of a statue template (read once from its structure template): its cells, the columns it stands on, its
 * glowing eyes, for each of the four rotations, as offsets from its anchor (the centre column at the ground layer, the
 * rotation pivot: the anchor stays in place whatever the rotation).
 */
final class StatueTemplate {
	/** Light level from which a block of the template counts as an eye (froglights 15; three lit candles 9). */
	private static final int EYE_LIGHT = 12;

	final Identifier id;
	final StructureTemplate template;
	final Vec3i size;
	final int ground;
	/** Rotation pivot (template coordinates): the centre column, layer 0. */
	final BlockPos pivot;
	private final List<BlockPos> solid = new ArrayList<>();
	private final List<BlockPos> lights = new ArrayList<>();
	private final Shape[] shapes = new Shape[4];

	/**
	 * One rotation: every offset relative to the anchor. {@code columns} = (dx, dz, lowest dy) of every column holding a cell,
	 * {@code top} = the highest dy, the box of the offsets.
	 */
	record Shape(List<int[]> columns, Set<Long> cells, List<int[]> eyes, int top, int minDx, int maxDx, int minDz, int maxDz) {
		/** Largest horizontal reach from the anchor. */
		int span() {
			return Math.max(Math.max(-minDx, maxDx), Math.max(-minDz, maxDz));
		}

		boolean hasCell(int dx, int dy, int dz) {
			return cells.contains(BlockPos.asLong(dx, dy, dz));
		}
	}

	StatueTemplate(Identifier id, StructureTemplate template, int ground) {
		this.id = id;
		this.template = template;
		this.size = template.getSize();
		this.ground = Math.max(0, Math.min(size.getY() - 1, ground));
		this.pivot = new BlockPos(size.getX() / 2, 0, size.getZ() / 2);
		CompoundTag tag = template.save(new CompoundTag());
		ListTag palette = tag.getListOrEmpty("palette");
		if (palette.isEmpty()) {
			palette = tag.getListOrEmpty("palettes").getListOrEmpty(0);
		}
		List<BlockState> states = new ArrayList<>();
		for (int i = 0; i < palette.size(); i++) {
			states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK, palette.getCompoundOrEmpty(i)));
		}
		ListTag blocks = tag.getListOrEmpty("blocks");
		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag block = blocks.getCompoundOrEmpty(i);
			ListTag pos = block.getListOrEmpty("pos");
			int index = block.getIntOr("state", -1);
			if (pos.size() < 3 || index < 0 || index >= states.size()) {
				continue;
			}
			BlockState state = states.get(index);
			if (state.isAir()) {
				continue;
			}
			BlockPos at = new BlockPos(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0));
			solid.add(at);
			if (state.getLightEmission() >= EYE_LIGHT && at.getY() > this.ground) {
				lights.add(at);
			}
		}
	}

	/** Number of non-air cells. */
	int blockCount() {
		return solid.size();
	}

	static Rotation rotation(int quarterTurns) {
		return switch (Math.floorMod(quarterTurns, 4)) {
			case 1 -> Rotation.CLOCKWISE_90;
			case 2 -> Rotation.CLOCKWISE_180;
			case 3 -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	/** Template origin that puts the anchor at {@code anchor} (the pivot keeps the anchor in place). */
	BlockPos origin(BlockPos anchor) {
		return anchor.offset(-pivot.getX(), -ground, -pivot.getZ());
	}

	Shape shape(int quarterTurns) {
		int turns = Math.floorMod(quarterTurns, 4);
		Shape shape = shapes[turns];
		if (shape == null) {
			shape = build(rotation(turns));
			shapes[turns] = shape;
		}
		return shape;
	}

	private Shape build(Rotation rotation) {
		Map<Long, int[]> columns = new HashMap<>();
		Set<Long> cells = new HashSet<>();
		int top = Integer.MIN_VALUE;
		int minDx = Integer.MAX_VALUE;
		int maxDx = Integer.MIN_VALUE;
		int minDz = Integer.MAX_VALUE;
		int maxDz = Integer.MIN_VALUE;
		for (BlockPos cell : solid) {
			int[] o = offset(cell, rotation);
			cells.add(BlockPos.asLong(o[0], o[1], o[2]));
			long column = BlockPos.asLong(o[0], 0, o[2]);
			int[] existing = columns.get(column);
			if (existing == null) {
				columns.put(column, new int[] {o[0], o[2], o[1]});
			} else {
				existing[2] = Math.min(existing[2], o[1]);
			}
			top = Math.max(top, o[1]);
			minDx = Math.min(minDx, o[0]);
			maxDx = Math.max(maxDx, o[0]);
			minDz = Math.min(minDz, o[2]);
			maxDz = Math.max(maxDz, o[2]);
		}
		List<int[]> eyes = new ArrayList<>();
		for (BlockPos light : lights) {
			eyes.add(offset(light, rotation));
		}
		if (solid.isEmpty()) {
			top = 0;
			minDx = maxDx = minDz = maxDz = 0;
		}
		return new Shape(new ArrayList<>(columns.values()), cells, eyes, top, minDx, maxDx, minDz, maxDz);
	}

	/** Offset of a template cell from the anchor once rotated around the pivot. */
	private int[] offset(BlockPos cell, Rotation rotation) {
		BlockPos rotated = StructureTemplate.transform(cell, Mirror.NONE, rotation, pivot);
		return new int[] {rotated.getX() - pivot.getX(), rotated.getY() - ground, rotated.getZ() - pivot.getZ()};
	}
}
