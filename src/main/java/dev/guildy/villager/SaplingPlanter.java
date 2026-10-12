package dev.guildy.villager;

import dev.guildy.core.ItemUtil;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lumberjack planting: new saplings on free natural ground of its forest zone (automatic mode only when
 * {@code lumberjack.plantInAutoMode}), so the forest keeps growing between cuts. A spot needs natural soil (grass,
 * dirt, podzol, moss, mud: never farmland nor a path), room above for a tree, light, only natural blocks around (no
 * building), no other guild's land within {@link PlantingRules#FOREIGN_MARGIN} blocks, no log nor sapling within
 * {@code plantSpacing} blocks, and its chunk must hold fewer than {@code plantMaxPerChunk} trees. Dark oak and pale oak
 * only grow as 2×2 squares: they are planted by four (squares on level ground), never alone. Species: the zone's,
 * then those of the wanted items, then whatever the bag or the storage holds (any-tree zones only).
 */
final class SaplingPlanter extends Planter {
	/** Saplings planted per step at most (when several spots are within reach). */
	private static final int PER_STEP = 2;
	private static final int MAX_TRUNK = 40;
	private final Map<Item, Boolean> saplings = new HashMap<>();

	@Override
	boolean enabled(WorkContext ctx, @Nullable WorkZone zone) {
		VillagerConfig.Lumberjack config = ctx.config.lumberjack;
		return config.plantSaplings && (zone != null ? zone.type == ZoneType.FOREST : config.plantInAutoMode);
	}

	@Override
	Settings settings(VillagerConfig config) {
		VillagerConfig.Lumberjack lumberjack = config.lumberjack;
		return new Settings(lumberjack.plantPerRound, PER_STEP, lumberjack.plantFromStorage, lumberjack.plantFetchAmount, lumberjack.plantPauseSeconds);
	}

	@Override
	boolean plantable(Item item) {
		return saplings.computeIfAbsent(item, candidate -> candidate instanceof BlockItem block && new ItemStack(candidate).is(ItemTags.SAPLINGS)
				&& block.getBlock().defaultBlockState().is(BlockTags.SAPLINGS));
	}

	@Override
	int minCount(Item item) {
		return PlantingRules.needsSquare(ItemUtil.id(item)) ? 4 : 1;
	}

	@Override
	List<String> preferences(WorkContext ctx, @Nullable WorkZone zone) {
		String objective = zone != null && zone.type == ZoneType.FOREST ? zone.objective.block() : "";
		return PlantingRules.saplingPreferences(objective, wishIds(ctx.record, zone));
	}

	@Override
	boolean anyAllowed(@Nullable WorkZone zone) {
		return zone == null || zone.objective.isAny();
	}

	@Override
	@Nullable Item extraItem(WorkContext ctx) {
		return ctx.config.lumberjack.useBoneMeal ? Items.BONE_MEAL : null;
	}

	@Override
	void onStep(WorkContext ctx) {
		if (ctx.config.lumberjack.pickUpSaplings) {
			Planting.pickUp(ctx, 3.0, Planting::isSapling);
		}
	}

	// ------------------------------------------------------------------ spots

	@Override
	void collect(WorkContext ctx, WorkArea area, LevelChunk chunk, Item item, List<Spot> pending, int limit) {
		VillagerConfig.Lumberjack config = ctx.config.lumberjack;
		int spacing = config.plantSpacing;
		int pad = spacing;
		int size = minCount(item) >= 4 ? 2 : 1;
		int x0 = chunk.getPos().getMinBlockX();
		int z0 = chunk.getPos().getMinBlockZ();
		Planting.Chunks chunks = new Planting.Chunks(ctx.level);
		int width = 16 + 2 * pad;
		boolean[][] occupied = new boolean[width][width];
		int existing = 0;
		for (int lx = -pad; lx < 16 + pad; lx++) {
			for (int lz = -pad; lz < 16 + pad; lz++) {
				int x = x0 + lx;
				int z = z0 + lz;
				int top = chunks.top(x, z);
				if (top == Integer.MIN_VALUE) {
					// Not loaded: unknown, so nothing is planted near it.
					occupied[lx + pad][lz + pad] = true;
					continue;
				}
				BlockState topState = chunks.state(x, top, z);
				boolean sapling = isSapling(topState) || isSapling(chunks.state(x, top + 1, z));
				boolean log = topState != null && topState.is(BlockTags.LOGS);
				if (sapling || log || isPending(pending, x, z)) {
					occupied[lx + pad][lz + pad] = true;
				}
				if (lx >= 0 && lz >= 0 && lx < 16 && lz < 16 && (sapling || (log && trunkOnSoil(chunks, x, top, z, topState)))) {
					existing++;
				}
			}
		}
		int refX = ctx.villager.getBlockX() - x0;
		int refZ = ctx.villager.getBlockZ() - z0;
		List<int[]> spots = PlantingRules.pickSpots(occupied, pad, spacing, size, existing, config.plantMaxPerChunk, limit, refX, refZ,
				(lx, lz) -> spotOk(ctx, chunks, area, x0 + lx, z0 + lz, config.plantHeadroom));
		for (int[] spot : spots) {
			int x = x0 + spot[0];
			int z = z0 + spot[1];
			pending.add(new Spot(new BlockPos(x, chunks.top(x, z) + 1, z), size, false));
		}
	}

	/** Natural soil column inside the area with a free, lit, natural spot and room for a tree, away from other guilds. */
	private static boolean spotOk(WorkContext ctx, Planting.Chunks chunks, WorkArea area, int x, int z, int headroom) {
		if (!area.contains(x, z)) {
			return false;
		}
		int top = chunks.top(x, z);
		BlockState groundState = top == Integer.MIN_VALUE ? null : chunks.state(x, top, z);
		BlockState spotState = groundState == null ? null : chunks.state(x, top + 1, z);
		if (groundState == null || spotState == null) {
			return false;
		}
		PlantingRules.Ground ground = Planting.ground(groundState);
		boolean free = Planting.freeForPlant(spotState);
		if ((ground != PlantingRules.Ground.TILLABLE && ground != PlantingRules.Ground.SOIL) || !free) {
			return false;
		}
		int room = 0;
		while (room < headroom) {
			BlockState above = chunks.state(x, top + 2 + room, z);
			if (above == null || !Planting.freeForTree(above)) {
				break;
			}
			room++;
		}
		int light = ctx.level.getRawBrightness(new BlockPos(x, top + 1, z), 0);
		PlantingRules.Column column = new PlantingRules.Column(ground, true, room, light, naturalAround(chunks, x, top, z), foreignNear(ctx, x, z));
		return PlantingRules.saplingSpot(column, headroom);
	}

	/** Only natural blocks in the 3×3 columns around, from the ground to two blocks above it. */
	private static boolean naturalAround(Planting.Chunks chunks, int x, int ground, int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = 0; dy <= 2; dy++) {
					BlockState state = chunks.state(x + dx, ground + dy, z + dz);
					if (state == null || !Planting.natural(state)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/** Another guild's land within the margin (the corners of the square cover every chunk it touches). */
	private static boolean foreignNear(WorkContext ctx, int x, int z) {
		int m = PlantingRules.FOREIGN_MARGIN;
		return ctx.foreign(new BlockPos(x - m, 0, z - m)) || ctx.foreign(new BlockPos(x + m, 0, z - m))
				|| ctx.foreign(new BlockPos(x - m, 0, z + m)) || ctx.foreign(new BlockPos(x + m, 0, z + m));
	}

	private static boolean isSapling(@Nullable BlockState state) {
		return state != null && state.is(BlockTags.SAPLINGS);
	}

	/** The log column is a trunk standing on soil (a tree base, counted for the density). */
	private static boolean trunkOnSoil(Planting.Chunks chunks, int x, int top, int z, BlockState log) {
		int y = top;
		for (int steps = 0; steps < MAX_TRUNK; steps++) {
			BlockState below = chunks.state(x, y - 1, z);
			if (below == null) {
				return false;
			}
			if (!below.is(log.getBlock())) {
				return below.is(BlockTags.SUBSTRATE_OVERWORLD) || below.is(BlockTags.NYLIUM) || below.is(Blocks.MANGROVE_ROOTS);
			}
			y--;
		}
		return false;
	}

	// ------------------------------------------------------------------ planting

	@Override
	boolean plant(WorkContext ctx, Spot spot, Item item) {
		if (!(item instanceof BlockItem blockItem)) {
			return false;
		}
		ServerLevel level = ctx.level;
		BlockState sapling = blockItem.getBlock().defaultBlockState();
		List<BlockPos> cells = footprint(spot);
		for (BlockPos cell : cells) {
			if (!ctx.loaded(cell) || ctx.foreign(cell)) {
				return false;
			}
			PlantingRules.Ground ground = Planting.ground(level.getBlockState(cell.below()));
			if ((ground != PlantingRules.Ground.TILLABLE && ground != PlantingRules.Ground.SOIL) || !Planting.freeForPlant(level.getBlockState(cell))
					|| !sapling.canSurvive(level, cell) || level.getRawBrightness(cell, 0) < PlantingRules.SAPLING_MIN_LIGHT) {
				return false;
			}
		}
		if (!spaced(new Planting.Chunks(level), spot, ctx.config.lumberjack.plantSpacing)
				|| !PlantStock.takeFromBag(ctx, item, cells.size())) {
			return false;
		}
		for (BlockPos cell : cells) {
			level.setBlockAndUpdate(cell, sapling);
			level.gameEvent(GameEvent.BLOCK_PLACE, cell, GameEvent.Context.of(ctx.villager, sapling));
		}
		level.playSound(null, spot.pos(), sapling.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0f, 0.8f);
		ctx.record.stats.planted += cells.size();
		GuildManager.markDirty();
		if (ctx.config.lumberjack.useBoneMeal) {
			boneMeal(ctx, spot.pos());
		}
		return true;
	}

	/** One sapling, or the four of a 2×2 square (the spot is its north-west corner). */
	private static List<BlockPos> footprint(Spot spot) {
		List<BlockPos> cells = new ArrayList<>();
		for (int dx = 0; dx < spot.size(); dx++) {
			for (int dz = 0; dz < spot.size(); dz++) {
				cells.add(spot.pos().offset(dx, 0, dz));
			}
		}
		return cells;
	}

	/** Still no log nor sapling within the spacing (another lumberjack may have planted nearby meanwhile). */
	private static boolean spaced(Planting.Chunks chunks, Spot spot, int spacing) {
		int x0 = spot.pos().getX();
		int z0 = spot.pos().getZ();
		for (int x = x0 - spacing; x <= x0 + spot.size() - 1 + spacing; x++) {
			for (int z = z0 - spacing; z <= z0 + spot.size() - 1 + spacing; z++) {
				if (x >= x0 && z >= z0 && x < x0 + spot.size() && z < z0 + spot.size()) {
					continue;
				}
				int top = chunks.top(x, z);
				if (top == Integer.MIN_VALUE) {
					return false;
				}
				BlockState state = chunks.state(x, top, z);
				if ((state != null && state.is(BlockTags.LOGS)) || isSapling(state) || isSapling(chunks.state(x, top + 1, z))) {
					return false;
				}
			}
		}
		return true;
	}

	/** Bone meal of the bag on the new sapling (config {@code lumberjack.useBoneMeal}). */
	private static void boneMeal(WorkContext ctx, BlockPos pos) {
		ItemStack meal = PlantStock.bagStack(ctx, Items.BONE_MEAL);
		if (meal != null && BoneMealItem.growCrop(meal, ctx.level, pos)) {
			ctx.level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 15);
			ctx.record.carried.removeIf(ItemStack::isEmpty);
			GuildManager.markDirty();
		}
	}

	// ------------------------------------------------------------------ statuses

	@Override
	void searching(WorkContext ctx, Item item, int position, int total) {
		ctx.status("villager.plant.searching_forest", Planting.plantName(item, true), position, total);
	}

	@Override
	void going(WorkContext ctx, Spot spot, Item item, int done, int round) {
		ctx.status("villager.plant.going", Planting.plantName(item, true), done, round);
	}

	@Override
	void working(WorkContext ctx, Spot spot, Item item, int done, int round) {
		ctx.status("villager.plant.planting", Planting.plantName(item, true), done, round);
	}

	@Override
	void fetching(WorkContext ctx, Item item) {
		ctx.status(item == Items.BONE_MEAL ? "villager.plant.fetch_bone_meal" : "villager.plant.fetch_saplings");
	}

	@Override
	String noSourceNote() {
		return "villager.plant.note.no_saplings";
	}

	@Override
	String noSpotNote() {
		return "villager.plant.note.no_room_forest";
	}
}
