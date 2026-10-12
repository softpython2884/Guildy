package dev.guildy.villager;

import dev.guildy.core.ItemUtil;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.TorchflowerCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Farmer sowing: empty farmland of its field zone (or, with {@code farmer.sowInAutoMode}, around the territory) gets
 * wheat, carrots, potatoes or beetroots: the wanted crops first, then the zone's crop, then whatever seeds the bag or
 * the storage holds (any-crop zones only). In field zones with {@code farmer.tillSoil}, grass blocks and dirt in the
 * open with water within the vanilla hydration range (4 blocks) are tilled then sown, never a path, never next to a
 * player's block other than farmland, water, fences or torches. Melons, pumpkins and sugar cane are not sown (their
 * stem / base stays at harvest).
 */
final class CropSower extends Planter {
	private final Map<Item, Boolean> seeds = new HashMap<>();

	@Override
	boolean enabled(WorkContext ctx, @Nullable WorkZone zone) {
		VillagerConfig.Farmer config = ctx.config.farmer;
		return config.sow && (zone != null ? zone.type == ZoneType.FARM : config.sowInAutoMode);
	}

	@Override
	Settings settings(VillagerConfig config) {
		VillagerConfig.Farmer farmer = config.farmer;
		return new Settings(farmer.sowPerRound, farmer.harvestPerSecond, farmer.sowFromStorage, farmer.sowFetchAmount, farmer.sowPauseSeconds);
	}

	@Override
	boolean plantable(Item item) {
		return seeds.computeIfAbsent(item, candidate -> PlantingRules.SEEDS.contains(ItemUtil.id(candidate)) && candidate instanceof BlockItem block
				&& block.getBlock() instanceof CropBlock && !(block.getBlock() instanceof TorchflowerCropBlock));
	}

	@Override
	List<String> preferences(WorkContext ctx, @Nullable WorkZone zone) {
		String objective = zone != null && zone.type == ZoneType.FARM ? zone.objective.block() : "";
		return PlantingRules.seedPreferences(objective, wishIds(ctx.record, zone));
	}

	@Override
	boolean anyAllowed(@Nullable WorkZone zone) {
		return zone == null || zone.objective.isAny();
	}

	/** Tilling only in field zones. */
	private boolean tilling(WorkContext ctx) {
		WorkZone current = zone;
		return ctx.config.farmer.tillSoil && current != null && current.type == ZoneType.FARM;
	}

	// ------------------------------------------------------------------ spots

	@Override
	void collect(WorkContext ctx, WorkArea area, LevelChunk chunk, Item item, List<Spot> pending, int limit) {
		int vertical = ctx.config.farmer.verticalRange;
		boolean till = tilling(ctx);
		int x0 = chunk.getPos().getMinBlockX();
		int z0 = chunk.getPos().getMinBlockZ();
		Planting.Chunks chunks = new Planting.Chunks(ctx.level);
		List<Spot> sow = new ArrayList<>();
		List<BlockPos> tillable = new ArrayList<>();
		for (int index = 0; index < 256; index++) {
			int x = x0 + (index >> 4);
			int z = z0 + (index & 15);
			if (!area.contains(x, z) || isPending(pending, x, z)) {
				continue;
			}
			int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			boolean found = false;
			if (sowable(chunks, x, top, z)) {
				sow.add(new Spot(new BlockPos(x, top + 1, z), 1, false));
				found = true;
			}
			for (int y = area.refY - vertical; y <= area.refY + vertical && !found; y++) {
				if (y != top && sowable(chunks, x, y, z)) {
					sow.add(new Spot(new BlockPos(x, y + 1, z), 1, false));
					found = true;
				}
			}
			if (!found && till) {
				BlockState ground = chunks.state(x, top, z);
				BlockState above = chunks.state(x, top + 1, z);
				if (ground != null && above != null && above.isAir() && Planting.ground(ground) == PlantingRules.Ground.TILLABLE) {
					tillable.add(new BlockPos(x, top, z));
				}
			}
		}
		for (Spot spot : sow) {
			if (limit <= 0) {
				return;
			}
			pending.add(spot);
			limit--;
		}
		if (tillable.isEmpty() || limit <= 0) {
			return;
		}
		boolean[][] water = waterColumns(chunks, x0, z0);
		for (BlockPos ground : tillable) {
			if (limit <= 0) {
				return;
			}
			if (nearWater(water, ground.getX() - x0, ground.getZ() - z0) && canTill(ctx, chunks, ground)) {
				pending.add(new Spot(ground.above(), 1, true));
				limit--;
			}
		}
	}

	/** Farmland with air above. */
	private static boolean sowable(Planting.Chunks chunks, int x, int y, int z) {
		BlockState ground = chunks.state(x, y, z);
		BlockState above = ground == null ? null : chunks.state(x, y + 1, z);
		return ground != null && above != null && ground.is(Blocks.FARMLAND) && above.isAir();
	}

	/**
	 * Columns (the chunk and {@link PlantingRules#HYDRATION_RANGE} around) whose surface holds water: a quick filter
	 * before the exact vanilla hydration test.
	 */
	private static boolean[][] waterColumns(Planting.Chunks chunks, int x0, int z0) {
		int pad = PlantingRules.HYDRATION_RANGE;
		boolean[][] water = new boolean[16 + 2 * pad][16 + 2 * pad];
		for (int lx = -pad; lx < 16 + pad; lx++) {
			for (int lz = -pad; lz < 16 + pad; lz++) {
				int top = chunks.top(x0 + lx, z0 + lz);
				if (top == Integer.MIN_VALUE) {
					continue;
				}
				water[lx + pad][lz + pad] = isWater(chunks.state(x0 + lx, top, z0 + lz)) || isWater(chunks.state(x0 + lx, top - 1, z0 + lz));
			}
		}
		return water;
	}

	private static boolean nearWater(boolean[][] water, int lx, int lz) {
		int pad = PlantingRules.HYDRATION_RANGE;
		for (int dx = -pad; dx <= pad; dx++) {
			for (int dz = -pad; dz <= pad; dz++) {
				if (water[lx + dx + pad][lz + dz + pad]) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isWater(@Nullable BlockState state) {
		return state != null && state.getFluidState().is(FluidTags.WATER);
	}

	/** Exact rules: grass block / dirt, air above, vanilla hydration box, field-friendly neighbours, not another guild's. */
	private static boolean canTill(WorkContext ctx, Planting.Chunks chunks, BlockPos ground) {
		BlockState state = chunks.state(ground);
		BlockState above = chunks.state(ground.above());
		if (state == null || above == null || ctx.foreign(ground)) {
			return false;
		}
		return PlantingRules.tillable(Planting.ground(state), above.isAir(), hydrated(chunks, ground), neighboursOk(chunks, ground));
	}

	/** Water in the vanilla box of FarmlandBlock.isNearWater: 4 blocks around, same level or one above. */
	private static boolean hydrated(Planting.Chunks chunks, BlockPos ground) {
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

	/** The 8 blocks around, at the ground level and above: natural, or farmland, water, crops, fences, torches. */
	private static boolean neighboursOk(Planting.Chunks chunks, BlockPos ground) {
		for (int dy = 0; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) {
						continue;
					}
					BlockState state = chunks.state(ground.getX() + dx, ground.getY() + dy, ground.getZ() + dz);
					if (state == null || !Planting.fieldNeighbour(state)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ planting

	@Override
	boolean plant(WorkContext ctx, Spot spot, Item item) {
		if (!(item instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof CropBlock crop)) {
			return false;
		}
		ServerLevel level = ctx.level;
		BlockPos pos = spot.pos();
		BlockPos ground = pos.below();
		if (!ctx.loaded(pos) || ctx.foreign(pos) || !level.getBlockState(pos).isAir() || PlantStock.countInBag(ctx, item) < 1) {
			return false;
		}
		boolean tilled = false;
		if (spot.till()) {
			if (!canTill(ctx, new Planting.Chunks(level), ground)) {
				return false;
			}
			BlockState farmland = Blocks.FARMLAND.defaultBlockState();
			level.setBlockAndUpdate(ground, farmland);
			level.gameEvent(GameEvent.BLOCK_CHANGE, ground, GameEvent.Context.of(ctx.villager, farmland));
			level.playSound(null, ground, SoundEvents.HOE_TILL.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
			tilled = true;
		}
		BlockState plant = crop.getStateForAge(0);
		if (!level.getBlockState(ground).is(Blocks.FARMLAND) || !plant.canSurvive(level, pos) || !PlantStock.takeFromBag(ctx, item, 1)) {
			return tilled;
		}
		level.setBlockAndUpdate(pos, plant);
		level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(ctx.villager, plant));
		level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.8f, 1.0f);
		ctx.record.stats.sown++;
		GuildManager.markDirty();
		return true;
	}

	// ------------------------------------------------------------------ statuses

	@Override
	void searching(WorkContext ctx, Item item, int position, int total) {
		ctx.status("villager.plant.searching_field", position, total);
	}

	@Override
	void going(WorkContext ctx, Spot spot, Item item, int done, int round) {
		ctx.status(spot.till() ? "villager.plant.going_till" : "villager.plant.going_sow", Planting.plantName(item, false), done, round);
	}

	@Override
	void working(WorkContext ctx, Spot spot, Item item, int done, int round) {
		ctx.status(spot.till() ? "villager.plant.tilling" : "villager.plant.sowing", Planting.plantName(item, false), done, round);
	}

	@Override
	void fetching(WorkContext ctx, Item item) {
		ctx.status("villager.plant.fetch_seeds");
	}

	@Override
	String noSourceNote() {
		return "villager.plant.note.no_seeds";
	}

	@Override
	String noSpotNote() {
		return "villager.plant.note.no_room_field";
	}
}
