package dev.guildy.villager;

import dev.guildy.core.Lang;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Logs for the construction sites: a natural tree of the wanted wood is cut whole, top-down, and replanted, like the
 * lumberjack does (natural leaves, soil under the trunk, at most {@code lumberjack.maxLogsPerTree} logs, never a log in
 * another guild's land). Where: the guild's forest zones first (option "Couper les arbres" of the fanion respected,
 * "Replanter" too), then natural trees outside the territory within {@code gathering.radius} blocks of the heart (never
 * in a claimed chunk, never near the spawn). Chunks are read a few per second, nearest first, loaded entity-ticking
 * chunks only.
 */
final class SupplyTrees {
	enum Result {
		WORKING,
		DONE,
		/** No tree of that wood in reach. */
		NONE
	}

	private static final int CHUNKS_PER_STEP = 4;
	private static final int MAX_TRUNK_HEIGHT = 40;
	private static final int MAX_SPREAD = 6;
	private static final int MIN_NATURAL_LEAVES = 3;
	private static final ItemStack AXE = new ItemStack(Items.IRON_AXE);

	/** One area to search: a forest zone, or the surroundings of the territory. */
	private record Area(@Nullable WorkZone zone, long[] chunks) {
	}

	private record Tree(BlockPos base, Block log, ArrayDeque<BlockPos> logs, List<BlockPos> plantSpots, @Nullable Block sapling,
			@Nullable WorkZone zone) {
	}

	private final Item item;
	private final Block log;
	private final int amount;
	private final List<Area> areas = new ArrayList<>();
	private int areaIndex;
	private int chunkIndex;
	private int chunksRead;
	private int chunksTotal;
	private final ArrayDeque<BlockPos> bases = new ArrayDeque<>();
	private final Set<Long> rejected = new HashSet<>();
	private @Nullable Tree tree;
	private boolean prepared;
	private double budget;

	SupplyTrees(Item item, int amount) {
		this.item = item;
		this.log = Block.byItem(item);
		this.amount = amount;
	}

	static boolean hasForestZone(WorkContext ctx) {
		for (WorkZone zone : ctx.guild.villagers.zones) {
			if (zone.type == ZoneType.FOREST && zone.pos != null && ZonePermissions.allows(zone, ZoneOption.CUT)) {
				return true;
			}
		}
		return false;
	}

	int progress(WorkContext ctx) {
		return CraftStock.countInBag(ctx, item);
	}

	Result tick(WorkContext ctx, String job) {
		if (progress(ctx) >= amount || CraftStock.roomFor(ctx, new ItemStack(item)) <= 0) {
			return Result.DONE;
		}
		if (!prepared) {
			prepare(ctx);
			prepared = true;
		}
		Tree current = tree;
		if (current == null) {
			current = search(ctx);
			if (current == null) {
				if (areaIndex >= areas.size() && bases.isEmpty()) {
					return progress(ctx) > 0 ? Result.DONE : Result.NONE;
				}
				ctx.status("villager.gather.job", job, Lang.raw("supply.action.tree_search", SupplyLang.name(item), chunksRead, Math.max(1, chunksTotal)));
				return Result.WORKING;
			}
			tree = current;
		}
		String place = current.zone() != null ? Lang.raw("supply.place.zone", current.zone().name) : Lang.raw("supply.place.outside");
		if (!Movement.near(ctx, current.base(), 5.0, 6.0)) {
			switch (Movement.moveTo(ctx, current.base(), 2.5, ctx.config.work.walkTimeoutSeconds)) {
				case UNREACHABLE -> {
					tree = null;
					return Result.WORKING;
				}
				case MOVING -> {
					ctx.status("villager.gather.job", job, Lang.raw("supply.action.tree_go", place));
					return Result.WORKING;
				}
				default -> {
				}
			}
		}
		budget += Supply.config().gathering.logsPerSecond * Upgrades.workRate(ctx.guild);
		while (budget >= 1) {
			BlockPos next = nextLog(ctx, current);
			if (next == null) {
				finishTree(ctx, current);
				tree = null;
				break;
			}
			budget -= 1;
			cut(ctx, next);
			if (progress(ctx) >= amount || CraftStock.roomFor(ctx, new ItemStack(item)) <= 0) {
				if (current.logs().isEmpty() || nextLogPeek(ctx, current) == null) {
					finishTree(ctx, current);
					tree = null;
				}
				return Result.DONE;
			}
		}
		budget = Math.min(budget, 4);
		ctx.status("villager.gather.job", job, Lang.raw("supply.action.tree_cut", place, progress(ctx), amount));
		return Result.WORKING;
	}

	private void cut(WorkContext ctx, BlockPos pos) {
		ServerLevel level = ctx.level;
		BlockState state = level.getBlockState(pos);
		List<ItemStack> drops = Block.getDrops(state, level, pos, null, ctx.villager, AXE);
		if (!ctx.villager.getMainHandItem().is(Items.IRON_AXE)) {
			ctx.villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
			ctx.villager.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
		}
		Movement.hold(ctx, pos);
		WorldUtil.swing(ctx.villager);
		level.destroyBlock(pos, false, ctx.villager);
		for (ItemStack drop : drops) {
			if (drop.isEmpty()) {
				continue;
			}
			int room = CraftStock.roomFor(ctx, drop);
			if (room > 0) {
				CraftStock.addStack(ctx, drop.copyWithCount(Math.min(room, drop.getCount())));
			}
			if (room < drop.getCount()) {
				net.minecraft.world.entity.item.ItemEntity entity = new net.minecraft.world.entity.item.ItemEntity(level, ctx.villager.getX(),
						ctx.villager.getY() + 0.5, ctx.villager.getZ(), drop.copyWithCount(drop.getCount() - Math.max(0, room)));
				entity.setDefaultPickUpDelay();
				level.addFreshEntity(entity);
			}
		}
		ctx.record.stats.logs++;
		GuildManager.markDirty();
	}

	private @Nullable BlockPos nextLog(WorkContext ctx, Tree current) {
		while (!current.logs().isEmpty()) {
			BlockPos pos = current.logs().poll();
			if (ctx.loaded(pos) && !ctx.foreign(pos) && ctx.level.getBlockState(pos).is(current.log())) {
				return pos;
			}
		}
		return null;
	}

	private @Nullable BlockPos nextLogPeek(WorkContext ctx, Tree current) {
		for (BlockPos pos : current.logs()) {
			if (ctx.loaded(pos) && ctx.level.getBlockState(pos).is(current.log())) {
				return pos;
			}
		}
		return null;
	}

	/** Saplings replanted at the trunk base (free, like the lumberjack's replanting). */
	private static void finishTree(WorkContext ctx, Tree current) {
		WorkZone zone = current.zone();
		boolean allowed = zone == null || ZonePermissions.allows(zone, ZoneOption.TREE_REPLANT);
		if (!Supply.config().gathering.replant || !allowed || current.sapling() == null) {
			return;
		}
		BlockState sapling = current.sapling().defaultBlockState();
		for (BlockPos spot : current.plantSpots()) {
			if (ctx.loaded(spot) && !ctx.foreign(spot) && ctx.level.getBlockState(spot).isAir() && sapling.canSurvive(ctx.level, spot)) {
				ctx.level.setBlockAndUpdate(spot, sapling);
				ctx.level.playSound(null, spot, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.8f, 1.0f);
			}
		}
	}

	// ------------------------------------------------------------------ search

	private void prepare(WorkContext ctx) {
		SupplyConfig.Gathering cfg = Supply.config().gathering;
		if (cfg.forestZones) {
			for (WorkZone zone : ctx.guild.villagers.zones) {
				if (zone.type == ZoneType.FOREST && zone.pos != null && ZonePermissions.allows(zone, ZoneOption.CUT)) {
					int[] b = zone.bounds();
					areas.add(new Area(zone, ZoneMath.chunksNearestFirst(b[0], b[1], b[2], b[3], zone.pos.getX(), zone.pos.getZ())));
				}
			}
		}
		BlockPos heart = ctx.guild.heart;
		if (cfg.outside && cfg.outsideTrees && heart != null) {
			int r = cfg.radius;
			areas.add(new Area(null, ZoneMath.chunksNearestFirst(heart.getX() - r, heart.getZ() - r, heart.getX() + r, heart.getZ() + r, heart.getX(),
					heart.getZ())));
		}
		for (Area area : areas) {
			chunksTotal += area.chunks().length;
		}
	}

	private @Nullable Tree search(WorkContext ctx) {
		for (int validations = 0; validations < 3 && !bases.isEmpty(); validations++) {
			Tree found = validate(ctx, bases.poll());
			if (found != null) {
				return found;
			}
		}
		int read = 0;
		while (read < CHUNKS_PER_STEP && areaIndex < areas.size() && bases.isEmpty()) {
			Area area = areas.get(areaIndex);
			if (chunkIndex >= area.chunks().length) {
				areaIndex++;
				chunkIndex = 0;
				continue;
			}
			long key = area.chunks()[chunkIndex++];
			chunksRead++;
			read++;
			collect(ctx, area, key);
		}
		return null;
	}

	private void collect(WorkContext ctx, Area area, long key) {
		int cx = ChunkKey.x(key);
		int cz = ChunkKey.z(key);
		Guild owner = GuildManager.atChunk(key);
		if (area.zone() == null ? owner != null : owner != null && owner != ctx.guild) {
			return;
		}
		LevelChunk chunk = ctx.level.getChunkSource().getChunkNow(cx, cz);
		if (chunk == null || !ctx.ticking(new BlockPos((cx << 4) + 8, ctx.villager.getBlockY(), (cz << 4) + 8))) {
			return;
		}
		if (area.zone() == null && SupplyPits.nearSpawn(ctx.level, (cx << 4) + 8, (cz << 4) + 8)) {
			return;
		}
		BlockPos heart = ctx.guild.heart;
		int r = Supply.config().gathering.radius;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int index = 0; index < 256; index++) {
			int x = (cx << 4) + (index >> 4);
			int z = (cz << 4) + (index & 15);
			if (area.zone() != null ? !area.zone().contains(x, z)
					: heart != null && (long) (x - heart.getX()) * (x - heart.getX()) + (long) (z - heart.getZ()) * (z - heart.getZ()) > (long) r * r) {
				continue;
			}
			if (rejected.contains(ChunkKey.of(x, z))) {
				continue;
			}
			int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (!chunk.getBlockState(pos.set(x, top, z)).is(log)) {
				continue;
			}
			int y = top;
			int steps = 0;
			while (steps < MAX_TRUNK_HEIGHT && chunk.getBlockState(pos.set(x, y - 1, z)).is(log)) {
				y--;
				steps++;
			}
			if (LumberjackTask.isSoil(chunk.getBlockState(pos.set(x, y - 1, z)))) {
				bases.add(new BlockPos(x, y, z));
			}
		}
	}

	/** The connected logs of a natural tree (bounded); null when it is not a tree we may cut. */
	private @Nullable Tree validate(WorkContext ctx, BlockPos base) {
		ServerLevel level = ctx.level;
		if (!ctx.loaded(base) || !level.getBlockState(base).is(log)) {
			return null;
		}
		WorkZone zone = zoneAt(ctx, base);
		int maxLogs = ctx.config.lumberjack.maxLogsPerTree;
		Set<BlockPos> logs = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(base);
		logs.add(base);
		int leaves = 0;
		boolean valid = true;
		while (!queue.isEmpty()) {
			BlockPos current = queue.poll();
			Guild owner = GuildManager.at(level, current);
			if (owner != null && (owner != ctx.guild || zone == null)) {
				// Never another guild's tree; outside the zones, never a tree of a territory.
				valid = false;
			}
			for (Direction direction : Direction.values()) {
				BlockPos side = current.relative(direction);
				if (ctx.loaded(side) && LumberjackTask.isNaturalLeaves(level.getBlockState(side))) {
					leaves++;
				}
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = 0; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos next = current.offset(dx, dy, dz);
						if (logs.contains(next) || Math.abs(next.getX() - base.getX()) > MAX_SPREAD || Math.abs(next.getZ() - base.getZ()) > MAX_SPREAD
								|| next.getY() - base.getY() > MAX_TRUNK_HEIGHT || !ctx.loaded(next) || !level.getBlockState(next).is(log)) {
							continue;
						}
						if (logs.size() >= maxLogs) {
							reject(logs);
							return null;
						}
						logs.add(next);
						queue.add(next);
					}
				}
			}
		}
		if (!valid || leaves < MIN_NATURAL_LEAVES) {
			reject(logs);
			return null;
		}
		List<BlockPos> sorted = new ArrayList<>(logs);
		sorted.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
		List<BlockPos> plantSpots = new ArrayList<>();
		for (BlockPos pos : logs) {
			if (pos.getY() == base.getY() && LumberjackTask.isSoil(level.getBlockState(pos.below()))) {
				plantSpots.add(pos);
			}
		}
		reject(logs);
		return new Tree(base, log, new ArrayDeque<>(sorted), plantSpots, LumberjackTask.saplingFor(log), zone);
	}

	private void reject(Set<BlockPos> logs) {
		for (BlockPos pos : logs) {
			rejected.add(ChunkKey.of(pos.getX(), pos.getZ()));
		}
	}

	private static @Nullable WorkZone zoneAt(WorkContext ctx, BlockPos pos) {
		for (WorkZone zone : ctx.guild.villagers.zones) {
			if (zone.type == ZoneType.FOREST && zone.pos != null && zone.contains(pos) && ZonePermissions.allows(zone, ZoneOption.CUT)) {
				return zone;
			}
		}
		return null;
	}

	/** The wood of a log item, or null when it is not a natural tree log. */
	static boolean natural(Item item) {
		Block block = Block.byItem(item);
		return block.defaultBlockState().is(BlockTags.OVERWORLD_NATURAL_LOGS) && LumberjackTask.saplingFor(block) != null;
	}
}
