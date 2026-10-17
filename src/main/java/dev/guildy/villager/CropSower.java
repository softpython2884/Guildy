package dev.guildy.villager;

import dev.guildy.core.ItemUtil;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import java.util.Set;

/**
 * Farmer sowing: empty farmland of its field zone (autorisation "Semer" of the fanion; or, with
 * {@code farmer.sowInAutoMode}, around the territory) gets wheat, carrots, potatoes or beetroots: the wanted crops
 * first, then the zone's crop, then whatever seeds the bag or the storage holds (any-crop zones only). The soil is
 * tilled by the field works of the zone ({@link FieldWorks}), not here. Melons, pumpkins and sugar cane are not sown
 * (their stem / base stays at harvest, and a stem needs a free block next to it for its fruit).
 */
final class CropSower extends Planter {
	private final Map<Item, Boolean> seeds = new HashMap<>();

	@Override
	boolean enabled(WorkContext ctx, @Nullable WorkZone zone) {
		VillagerConfig.Farmer config = ctx.config.farmer;
		return config.sow && (zone != null ? zone.type == ZoneType.FARM && ZonePermissions.allows(zone, ZoneOption.SOW) : config.sowInAutoMode);
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

	// ------------------------------------------------------------------ spots

	@Override
	void collect(WorkContext ctx, WorkArea area, LevelChunk chunk, Item item, List<Spot> pending, int limit) {
		int vertical = ctx.config.farmer.verticalRange;
		int x0 = chunk.getPos().getMinBlockX();
		int z0 = chunk.getPos().getMinBlockZ();
		Planting.Chunks chunks = new Planting.Chunks(ctx.level);
		int room = limit;
		for (int index = 0; index < 256 && room > 0; index++) {
			int x = x0 + (index >> 4);
			int z = z0 + (index & 15);
			if (!area.contains(x, z) || isPending(pending, x, z)) {
				continue;
			}
			int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			boolean found = false;
			if (sowable(chunks, x, top, z)) {
				pending.add(new Spot(new BlockPos(x, top + 1, z), 1, false));
				found = true;
			}
			for (int y = area.refY - vertical; y <= area.refY + vertical && !found; y++) {
				if (y != top && sowable(chunks, x, y, z)) {
					pending.add(new Spot(new BlockPos(x, y + 1, z), 1, false));
					found = true;
				}
			}
			if (found) {
				room--;
			}
		}
	}

	/** Farmland with air above. */
	private static boolean sowable(Planting.Chunks chunks, int x, int y, int z) {
		BlockState ground = chunks.state(x, y, z);
		BlockState above = ground == null ? null : chunks.state(x, y + 1, z);
		return ground != null && above != null && ground.is(Blocks.FARMLAND) && above.isAir();
	}

	// ------------------------------------------------------------------ retilled spots

	/**
	 * Seed of the bag to sow a retilled spot: {@code preferred} (the crop that grew there) when the bag holds it, then the
	 * usual preferences; null when the bag holds none.
	 */
	@Nullable Item seedInBag(WorkContext ctx, @Nullable WorkZone zone, String preferred) {
		List<String> order = new ArrayList<>();
		if (!preferred.isEmpty()) {
			order.add(preferred);
		}
		order.addAll(preferences(ctx, zone));
		PlantingRules.Choice choice = PlantingRules.choose(order, anyAllowed(zone), PlantStock.bag(ctx, this::plantable), List.of(),
				this::plantableId, id -> 1);
		return choice == null ? null : ItemUtil.item(choice.item());
	}

	/** Seeds to sow exist: in the bag, or in a storage chest the farmer may take from. */
	boolean hasSeeds(WorkContext ctx, @Nullable WorkZone zone) {
		List<Map<String, Integer>> chests = new ArrayList<>();
		if (ctx.config.farmer.sowFromStorage) {
			for (PlantStock.ChestView chest : PlantStock.chests(ctx, this::plantable, Set.of())) {
				chests.add(chest.items());
			}
		}
		return PlantingRules.choose(preferences(ctx, zone), anyAllowed(zone), PlantStock.bag(ctx, this::plantable), chests, this::plantableId,
				id -> 1) != null;
	}

	/** Sows {@code seed} of the bag at {@code pos} (above farmland). @return true when sown */
	boolean sow(WorkContext ctx, BlockPos pos, Item seed) {
		return plant(ctx, new Spot(pos, 1, false), seed);
	}

	private boolean plantableId(String id) {
		Item candidate = ItemUtil.item(id);
		return candidate != null && plantable(candidate);
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
		BlockState plant = crop.getStateForAge(0);
		if (!level.getBlockState(ground).is(Blocks.FARMLAND) || !plant.canSurvive(level, pos) || !PlantStock.takeFromBag(ctx, item, 1)) {
			return false;
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
		ctx.status("villager.plant.going_sow", Planting.plantName(item, false), done, round);
	}

	@Override
	void working(WorkContext ctx, Spot spot, Item item, int done, int round) {
		ctx.status("villager.plant.sowing", Planting.plantName(item, false), done, round);
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
