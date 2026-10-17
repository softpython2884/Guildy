package dev.guildy.villager;

import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.data.ChunkKey;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * World helpers shared by the sapling planter and the crop sower: natural blocks (no player building around a spot),
 * loaded-chunk reads that never load anything, saplings picked up from the ground, statuses and stats lines.
 */
final class Planting {
	/** Ground items picked up per step at most. */
	private static final int MAX_PICKUPS = 4;

	private Planting() {
	}

	/**
	 * Natural terrain, vegetation or natural tree: a spot next to anything else (planks, bricks, glass, chests, paths,
	 * farmland, persistent leaves...) is a player's work and is left alone.
	 */
	static boolean natural(BlockState state) {
		if (state.isAir()) {
			return true;
		}
		if (state.is(BlockTags.LEAVES)) {
			return !state.hasProperty(LeavesBlock.PERSISTENT) || !state.getValue(LeavesBlock.PERSISTENT);
		}
		return state.is(BlockTags.SUBSTRATE_OVERWORLD) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.SAND)
				|| state.is(BlockTags.LOGS) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.REPLACEABLE_BY_TREES)
				|| state.is(BlockTags.FLOWERS) || state.is(BlockTags.SNOW) || state.is(BlockTags.ICE) || state.is(BlockTags.BADLANDS_TERRACOTTA)
				|| state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)
				|| state.is(Blocks.SNOW) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM)
				|| state.is(Blocks.SUGAR_CANE) || state.is(Blocks.CACTUS) || state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON)
				|| state.is(Blocks.MOSS_CARPET) || state.is(Blocks.MANGROVE_ROOTS) || state.is(Blocks.BAMBOO) || state.is(Blocks.WATER);
	}

	/**
	 * Around a block to till or a water hole: natural blocks, or the usual parts of a field (farmland, water, crops,
	 * fences, gates, torches, lanterns, paths, composters, hay bales, cauldrons).
	 */
	static boolean fieldNeighbour(BlockState state) {
		return natural(state) || state.is(Blocks.FARMLAND) || state.is(BlockTags.CROPS) || state.is(BlockTags.MAINTAINS_FARMLAND)
				|| state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES) || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
				|| state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH) || state.is(Blocks.COPPER_TORCH)
				|| state.is(Blocks.COPPER_WALL_TORCH) || state.is(BlockTags.LANTERNS) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.COMPOSTER)
				|| state.is(Blocks.HAY_BLOCK) || state.is(BlockTags.CAULDRONS);
	}

	/** A small plant, a thin snow layer or air a sapling may replace; never a fluid, leaves or the half of a two-block plant. */
	static boolean freeForPlant(BlockState state) {
		if (state.isAir() || (state.is(Blocks.SNOW) && state.getValue(SnowLayerBlock.LAYERS) == 1)) {
			return true;
		}
		return state.is(BlockTags.REPLACEABLE_BY_TREES) && state.getFluidState().isEmpty() && !state.is(BlockTags.LEAVES)
				&& !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
	}

	/** Free room above a sapling: air, small plants or leaves (a growing tree replaces them). */
	static boolean freeForTree(BlockState state) {
		return state.isAir() || (state.is(BlockTags.REPLACEABLE_BY_TREES) && state.getFluidState().isEmpty());
	}

	/** Ground classes of the pure rules. */
	static PlantingRules.Ground ground(BlockState state) {
		if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)) {
			return PlantingRules.Ground.TILLABLE;
		}
		if (state.is(Blocks.FARMLAND)) {
			return PlantingRules.Ground.FARMLAND;
		}
		if (state.is(Blocks.DIRT_PATH)) {
			return PlantingRules.Ground.PATH;
		}
		return state.is(BlockTags.SUBSTRATE_OVERWORLD) ? PlantingRules.Ground.SOIL : PlantingRules.Ground.OTHER;
	}

	/** Plain stack (no name, enchantment, damage...): only those are planted or taken from a chest. */
	static boolean plain(ItemStack stack) {
		return !stack.isEmpty() && stack.getComponentsPatch().isEmpty();
	}

	/** Some carried stack must go to the deposit chest (stacks matching {@code keep} stay in the bag). */
	static boolean hasDepositable(WorkContext ctx, Predicate<ItemStack> keep) {
		for (ItemStack stack : ctx.record.carried) {
			if (!keep.test(stack)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Picks up the ground items matching {@code filter} around the villager (a lumberjack collects the saplings of the
	 * decaying foliage): never in another guild's land, never an item thrown by someone still around.
	 *
	 * @return items taken
	 */
	static int pickUp(WorkContext ctx, double radius, Predicate<ItemStack> filter) {
		if (ctx.carriedFull()) {
			return 0;
		}
		AABB box = ctx.villager.getBoundingBox().inflate(radius, 2.0, radius);
		List<ItemEntity> items = ctx.level.getEntitiesOfClass(ItemEntity.class, box,
				entity -> entity.isAlive() && !entity.hasPickUpDelay() && entity.getOwner() == null && plain(entity.getItem())
						&& filter.test(entity.getItem()) && !ctx.foreign(entity.blockPosition()));
		int taken = 0;
		int done = 0;
		for (ItemEntity entity : items) {
			if (done++ >= MAX_PICKUPS) {
				break;
			}
			ItemStack stack = entity.getItem();
			int count = stack.getCount();
			ItemStack leftover = ctx.addCarried(stack);
			int moved = count - leftover.getCount();
			if (moved <= 0) {
				continue;
			}
			ctx.villager.take(entity, moved);
			if (leftover.isEmpty()) {
				entity.discard();
			} else {
				entity.setItem(leftover);
			}
			taken += moved;
		}
		return taken;
	}

	/** Saplings lying around, for {@link #pickUp}. */
	static boolean isSapling(ItemStack stack) {
		return stack.is(ItemTags.SAPLINGS);
	}

	/** French name of a planted item for the statuses ("des pousses de chêne", "du blé"), generic when unknown. */
	static String plantName(@Nullable Item item, boolean sapling) {
		if (item != null) {
			String key = "villager.plant.name." + WishMatch.path(ItemUtil.id(item));
			if (Lang.has(key)) {
				return Lang.raw(key);
			}
		}
		return Lang.raw(sapling ? "villager.plant.name.saplings" : "villager.plant.name.seeds");
	}

	/** Idle status of a lumberjack / farmer, with the planter's note ("plus de pousses...") when it has one. */
	static void idleStatus(WorkContext ctx, Planter planter, String key, Object... args) {
		String note = planter.note();
		if (note == null) {
			ctx.status(key, args);
		} else {
			ctx.status("villager.plant.idle", Lang.raw(key, args), Lang.raw(note));
		}
	}

	/** Planting lines of the villager's stats. */
	static void statLore(List<Component> lore, VillagerRecord.Stats stats) {
		if (stats.planted > 0) {
			lore.add(Lang.get("villager.gui.lore.stat.planted", stats.planted));
		}
		if (stats.sown > 0) {
			lore.add(Lang.get("villager.gui.lore.stat.sown", stats.sown));
		}
		if (stats.tilled > 0) {
			lore.add(Lang.get("villager.gui.lore.stat.tilled", stats.tilled));
		}
		if (stats.waterHoles > 0) {
			lore.add(Lang.get("villager.gui.lore.stat.water_holes", stats.waterHoles));
		}
	}

	/**
	 * Reads of loaded chunks only (never loads one), cached for one scan step: columns outside loaded chunks are
	 * "unknown" (null states, {@link Integer#MIN_VALUE} heights).
	 */
	static final class Chunks {
		private final ServerLevel level;
		private final Map<Long, LevelChunk> cache = new HashMap<>();
		private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		Chunks(ServerLevel level) {
			this.level = level;
		}

		@Nullable LevelChunk at(int x, int z) {
			long key = ChunkKey.ofBlock(x, z);
			if (cache.containsKey(key)) {
				return cache.get(key);
			}
			LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
			cache.put(key, chunk);
			return chunk;
		}

		/** Top motion-blocking block (leaves ignored) of the column, {@link Integer#MIN_VALUE} when not loaded. */
		int top(int x, int z) {
			LevelChunk chunk = at(x, z);
			return chunk == null ? Integer.MIN_VALUE : chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		}

		@Nullable BlockState state(int x, int y, int z) {
			if (level.isOutsideBuildHeight(y)) {
				return null;
			}
			LevelChunk chunk = at(x, z);
			return chunk == null ? null : chunk.getBlockState(cursor.set(x, y, z));
		}

		@Nullable BlockState state(BlockPos pos) {
			return state(pos.getX(), pos.getY(), pos.getZ());
		}
	}
}
