package dev.guildy.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * World rules of the field works (loaded chunks only, through {@link Planting.Chunks}): which soil is tilled, levelled
 * or cleared, the exact vanilla hydration test, field-friendly surroundings (no player building around, farm blocks
 * allowed) and the safety of a water hole.
 */
final class FieldWorld {
	private FieldWorld() {
	}

	/** Soil a hoe turns into farmland: grass block and dirt; coarse and rooted dirt first become dirt (vanilla hoe). */
	static boolean tillSoil(@Nullable BlockState state) {
		return state != null && (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT));
	}

	/** Natural soil a field is levelled with (pit floors filled on, bumps shaved): never farmland nor a path. */
	static boolean levelSoil(@Nullable BlockState state) {
		return tillSoil(state) || (state != null && (state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)));
	}

	/**
	 * A small plant on the soil that "Dégager le sol" removes: grass, ferns, flowers, dead bushes, dry grass, leaf
	 * litter...; never a sapling, a crop, a stem, sugar cane, berries, a mushroom, leaves, vines or a fluid.
	 */
	static boolean clearable(@Nullable BlockState state) {
		if (state == null || state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.is(BlockTags.LEAVES)
				|| state.is(BlockTags.SAPLINGS) || state.is(BlockTags.CROPS) || state.is(Blocks.VINE) || state.is(Blocks.GLOW_LICHEN)
				|| state.is(Blocks.HANGING_ROOTS)) {
			return false;
		}
		return state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.SMALL_FLOWERS) || state.is(Blocks.PINK_PETALS)
				|| state.is(Blocks.WILDFLOWERS) || state.is(Blocks.RED_SHRUB);
	}

	/** Room above a block to work: air, or a plant that may be cleared. */
	static boolean free(@Nullable BlockState above, boolean clear) {
		return above != null && (above.isAir() || (clear && clearable(above)));
	}

	static boolean isWater(@Nullable BlockState state) {
		return state != null && state.getFluidState().is(FluidTags.WATER);
	}

	/** A crop or a stem still growing (bone meal target). */
	static boolean growing(@Nullable BlockState state) {
		if (state == null) {
			return false;
		}
		if (state.getBlock() instanceof CropBlock crop) {
			return !crop.isMaxAge(state);
		}
		return state.getBlock() instanceof StemBlock;
	}

	/** Water in the vanilla box of FarmlandBlock.isNearWater: 4 blocks around, same level or one above (unloaded = dry). */
	static boolean hydrated(Planting.Chunks chunks, BlockPos ground) {
		int range = PlantingRules.HYDRATION_RANGE;
		for (int dy = 0; dy <= 1; dy++) {
			for (int dx = -range; dx <= range; dx++) {
				for (int dz = -range; dz <= range; dz++) {
					if (isWater(chunks.state(ground.getX() + dx, ground.getY() + dy, ground.getZ() + dz))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Nothing built around: the 8 blocks around at the ground level and above are natural, or parts of a field
	 * (farmland, water, crops, fences, gates, torches, lanterns, paths, composters... see {@link Planting#fieldNeighbour}).
	 */
	static boolean friendly(Planting.Chunks chunks, int x, int y, int z) {
		for (int dy = 0; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) {
						continue;
					}
					BlockState state = chunks.state(x + dx, y + dy, z + dz);
					if (state == null || !Planting.fieldNeighbour(state)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/**
	 * A water hole may replace the block at {@code pos}: two full blocks under it (no cave below), its 4 sides hold
	 * water (full blocks, farmland or water; never lava), nothing built around and no other guild's land within
	 * {@link PlantingRules#FOREIGN_MARGIN} blocks.
	 */
	static boolean holeSafe(WorkContext ctx, Planting.Chunks chunks, BlockPos pos) {
		for (int dy = 1; dy <= 2; dy++) {
			BlockPos below = pos.below(dy);
			BlockState state = chunks.state(below);
			if (state == null || !state.getFluidState().isEmpty() || state.hasBlockEntity() || !state.isCollisionShapeFullBlock(ctx.level, below)) {
				return false;
			}
		}
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos side = pos.relative(direction);
			if (!holdsWater(ctx, side, chunks.state(side))) {
				return false;
			}
		}
		return friendly(chunks, pos.getX(), pos.getY(), pos.getZ()) && !foreignNear(ctx, pos.getX(), pos.getZ());
	}

	/** Water does not flow there: a full block or farmland, or water already; never lava. */
	private static boolean holdsWater(WorkContext ctx, BlockPos pos, @Nullable BlockState state) {
		if (state == null || state.getFluidState().is(FluidTags.LAVA)) {
			return false;
		}
		if (isWater(state) || state.is(Blocks.FARMLAND)) {
			return true;
		}
		return state.getFluidState().isEmpty() && !state.is(BlockTags.WASHED_AWAY_BY_FLUIDS) && state.isCollisionShapeFullBlock(ctx.level, pos);
	}

	/** Another guild's land at the block or within the margin (the corners of the square cover every chunk it touches). */
	static boolean foreignNear(WorkContext ctx, int x, int z) {
		int m = PlantingRules.FOREIGN_MARGIN;
		return ctx.foreign(new BlockPos(x, 0, z)) || ctx.foreign(new BlockPos(x - m, 0, z - m)) || ctx.foreign(new BlockPos(x + m, 0, z - m))
				|| ctx.foreign(new BlockPos(x - m, 0, z + m)) || ctx.foreign(new BlockPos(x + m, 0, z + m));
	}

	/** The block may be dug for a water hole (or refilled): soil, empty farmland, air, or water that is not a source. */
	static boolean diggable(@Nullable BlockState state, @Nullable BlockState above) {
		if (state == null || above == null || state.hasBlockEntity()) {
			return false;
		}
		if (state.is(Blocks.FARMLAND)) {
			return above.isAir() || clearable(above);
		}
		if (isWater(state)) {
			return !state.getFluidState().isSource();
		}
		return state.isAir() || levelSoil(state);
	}
}
