package dev.guildy.villager;

import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.data.GuildManager;
import dev.guildy.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Field works of a farmer in its field zone, allowed zone by zone by the "Autorisations du fanion"
 * ({@link ZonePermissions}) and run between harvests, in bounded rounds:
 * <ol>
 *   <li>{@link Mode#RETILL}: the farmland that turned back to dirt (remembered by {@link FieldMemory}) is tilled again
 *   and sown again at once with a seed of the bag (the crop that grew there first), else by the sowing round that
 *   follows. A spot is only retilled when it is hydrated or seeds exist (dry bare farmland would dry out again).</li>
 *   <li>{@link Mode#TERRAIN}: a survey of the zone (a few loaded chunks per second, {@link FieldPlanner.Grid}), then the
 *   jobs in this order: refill the water holes that disappeared, dig the new ones (lattice of {@link FieldPlanner}),
 *   level 1-block pits and bumps, till the hydrated soil (clearing the plants on it), bone meal on growing crops.
 *   Each job is checked again in the world before acting; the farmer walks to it and works on the jobs within reach
 *   ({@code fieldWork.actionsPerSecond} per second). A round stops after {@code fieldWork.perRound} actions (back to the
 *   harvest), the jobs left are done by the next rounds; a new survey is made once they are all done (or after
 *   {@link #SURVEY_TTL}).</li>
 * </ol>
 * Inside the zone only, loaded chunks only, never in another guild's land (nor within 4 blocks of it for the water
 * holes), never a block with data, never next to a player's building (farm blocks aside).
 */
final class FieldWorks {
	enum Mode {
		RETILL,
		TERRAIN
	}

	/** Terrain jobs, in priority order. */
	enum Kind {
		REFILL,
		HOLE,
		FILL,
		SHAVE,
		TILL,
		BONEMEAL
	}

	/** What a zone allows, the server switches of villagers.json5 applied. */
	record Allowed(boolean till, boolean irrigate, boolean clear, boolean level, boolean bonemeal, boolean retill) {
		boolean terrain() {
			return till || irrigate || level || bonemeal;
		}
	}

	private static final double REACH = 2.5;
	/** Other jobs done in the same step when this close (blocks, squared). */
	private static final double WORK_REACH_SQ = 3.2 * 3.2;
	/** Jobs checked per step at most before giving up for this second. */
	private static final int MAX_CHECKS = 24;
	/** Jobs of one kind kept from a survey at most (the rest is found by the next survey). */
	private static final int MAX_JOBS = 4096;
	private static final int MAX_CROPS = 256;
	/** A survey older than this is done again before the next round. */
	static final long SURVEY_TTL = 5 * 60 * 20L;
	private static final long UNLOADED_PAUSE_TICKS = 5 * 20L;
	private static final double PICKUP_RADIUS = 3.0;
	private static final ItemStack SHOVEL = new ItemStack(Items.IRON_SHOVEL);

	private boolean active;
	private Mode mode = Mode.TERRAIN;
	/** Actions of the current round. */
	private int done;
	private @Nullable Survey survey;
	private final EnumMap<Kind, List<BlockPos>> jobs = new EnumMap<>(Kind.class);
	private final int[] totals = new int[Kind.values().length];
	private final int[] dones = new int[Kind.values().length];
	private long plannedAt = Long.MIN_VALUE;
	private @Nullable Kind targetKind;
	private @Nullable BlockPos target;
	private @Nullable BlockPos fetchChest;
	private @Nullable Item fetchItem;
	private int fetchCount;
	private final Set<BlockPos> failedChests = new HashSet<>();
	/** Kinds whose resource (water buckets, dirt, bone meal) is missing until the next survey. */
	private final EnumSet<Kind> missing = EnumSet.noneOf(Kind.class);
	/** Coarse / rooted dirt turned into dirt this step: tilled again later (added after the job loop). */
	private final List<BlockPos> again = new ArrayList<>();
	private @Nullable List<BlockPos> retill;
	private int retillTotal;
	private int retillDone;
	private boolean seedsAvailable;
	private boolean retilledUnsown;
	/** Farmland made by the terrain works since the sowing round was last woken up. */
	private boolean tilledSinceWake;
	private long nextTerrainAt;
	private long nextRetillAt;
	private @Nullable String note;

	/** A survey in progress: the zone's chunks, nearest first, read into the grid. */
	private static final class Survey {
		final WorkArea area;
		final WorkArea.Scan scan;
		final FieldPlanner.Grid grid;
		final int ox;
		final int oz;
		final boolean[] reserved;
		final List<BlockPos> crops = new ArrayList<>();
		final List<BlockPos> refills = new ArrayList<>();

		Survey(WorkArea area, WorkArea.Scan scan, FieldPlanner.Grid grid, int ox, int oz) {
			this.area = area;
			this.scan = scan;
			this.grid = grid;
			this.ox = ox;
			this.oz = oz;
			this.reserved = new boolean[grid.size()];
		}

		int cell(int x, int z) {
			int gx = x - ox;
			int gz = z - oz;
			return grid.inside(gx, gz) ? grid.index(gx, gz) : -1;
		}
	}

	FieldWorks() {
		for (Kind kind : Kind.values()) {
			jobs.put(kind, new ArrayList<>());
		}
	}

	// ================================================================ permissions

	/** What the zone allows, with the server switches (null zone, other types: nothing). */
	static Allowed allowed(VillagerConfig config, @Nullable WorkZone zone) {
		if (zone == null || zone.type != ZoneType.FARM || !config.farmer.fieldWork.enabled) {
			return new Allowed(false, false, false, false, false, false);
		}
		return new Allowed(config.farmer.tillSoil && ZonePermissions.allows(zone, ZoneOption.TILL), ZonePermissions.allows(zone, ZoneOption.IRRIGATE),
				ZonePermissions.allows(zone, ZoneOption.CLEAR), ZonePermissions.allows(zone, ZoneOption.LEVEL),
				ZonePermissions.allows(zone, ZoneOption.BONEMEAL),
				config.farmer.fieldWork.retillMemory > 0 && ZonePermissions.allows(zone, ZoneOption.RETILL));
	}

	/** The server lets zones use this option (villagers.json5 switches); the zone's own toggle comes on top. */
	static boolean serverAllows(VillagerConfig config, ZoneOption option) {
		boolean works = config.farmer.fieldWork.enabled;
		return switch (option) {
			case HARVEST, REPLANT, CUT -> true;
			case SOW -> config.farmer.sow;
			case TILL -> works && config.farmer.tillSoil;
			case RETILL -> works && config.farmer.fieldWork.retillMemory > 0;
			case IRRIGATE, CLEAR, LEVEL, BONEMEAL -> works;
			case TREE_REPLANT -> config.lumberjack.replant;
			case TREE_PLANT -> config.lumberjack.plantSaplings;
		};
	}

	// ================================================================ state

	/**
	 * May a round of this mode run now (a round already started goes on)? When it may and none is running, it starts.
	 */
	boolean start(WorkContext ctx, @Nullable WorkZone zone, Mode wanted) {
		Allowed allowed = allowed(ctx.config, zone);
		if (zone == null) {
			return false;
		}
		if (active) {
			return mode == wanted && (wanted == Mode.RETILL ? allowed.retill() : allowed.terrain());
		}
		boolean ready = switch (wanted) {
			case RETILL -> allowed.retill() && ctx.now >= nextRetillAt && !zone.field().trampled.isEmpty();
			case TERRAIN -> allowed.terrain() && ctx.now >= nextTerrainAt;
		};
		if (ready) {
			active = true;
			mode = wanted;
			done = 0;
			target = null;
			targetKind = null;
			if (wanted == Mode.TERRAIN) {
				note = null;
			}
		}
		return ready;
	}

	/** The zone or its settings changed: everything starts over. */
	void reset() {
		active = false;
		done = 0;
		survey = null;
		clearJobs();
		plannedAt = Long.MIN_VALUE;
		retill = null;
		retilledUnsown = false;
		nextTerrainAt = 0;
		nextRetillAt = 0;
		note = null;
	}

	/** Note for the idle status ("champ terminé", missing water buckets...), null when none. */
	@Nullable String note() {
		return note;
	}

	/**
	 * Stacks staying in the bag at the deposit chest: one stack of dirt (levelling), one of bone meal and a few water
	 * buckets (when water costs buckets), never the item of a quota mission, nothing with a tiny bag.
	 */
	Predicate<ItemStack> keep(WorkContext ctx, @Nullable WorkZone zone) {
		Allowed allowed = allowed(ctx.config, zone);
		int stacks = ctx.config.work.carryStacks;
		if (!allowed.terrain() || ctx.record.goalReached || stacks < 6) {
			return stack -> false;
		}
		String quota = PlantStock.reserved(ctx.record);
		Set<ItemStack> kept = Collections.newSetFromMap(new IdentityHashMap<>());
		if (allowed.level() && !"minecraft:dirt".equals(quota)) {
			ItemStack dirt = PlantStock.bagStack(ctx, Items.DIRT);
			if (dirt != null) {
				kept.add(dirt);
			}
		}
		if (allowed.bonemeal() && !"minecraft:bone_meal".equals(quota)) {
			ItemStack meal = PlantStock.bagStack(ctx, Items.BONE_MEAL);
			if (meal != null) {
				kept.add(meal);
			}
		}
		if (allowed.irrigate() && ctx.config.farmer.irrigation.waterBucketCost > 0 && !"minecraft:water_bucket".equals(quota)) {
			int left = maxBuckets(ctx);
			for (ItemStack stack : ctx.record.carried) {
				if (left <= 0) {
					break;
				}
				if (Planting.plain(stack) && stack.is(Items.WATER_BUCKET)) {
					kept.add(stack);
					left--;
				}
			}
		}
		if (kept.isEmpty()) {
			return stack -> false;
		}
		return kept::contains;
	}

	/** Water buckets carried at most (the bag keeps room for the harvest). */
	private static int maxBuckets(WorkContext ctx) {
		return Math.max(1, Math.min(ctx.config.farmer.irrigation.bucketFetchAmount, ctx.config.work.carryStacks - 5));
	}

	private void clearJobs() {
		for (List<BlockPos> list : jobs.values()) {
			list.clear();
		}
		Arrays.fill(totals, 0);
		Arrays.fill(dones, 0);
		again.clear();
		missing.clear();
		failedChests.clear();
		target = null;
		targetKind = null;
		fetchChest = null;
		fetchItem = null;
	}

	private boolean jobsEmpty() {
		for (List<BlockPos> list : jobs.values()) {
			if (!list.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	// ================================================================ round

	/** One step of the current round. */
	Planter.Result tick(WorkContext ctx, @Nullable WorkZone zone, CropSower sower) {
		if (zone == null || !active) {
			active = false;
			return Planter.Result.DONE;
		}
		Allowed allowed = allowed(ctx.config, zone);
		if (mode == Mode.RETILL) {
			return retillStep(ctx, zone, sower, allowed);
		}
		Planter.Result result = terrainStep(ctx, zone, allowed);
		if (result == Planter.Result.DONE && tilledSinceWake) {
			// New farmland: the sowing round may come right away.
			tilledSinceWake = false;
			sower.wake();
		}
		return result;
	}

	private void finishTerrain(WorkContext ctx, long pauseTicks) {
		active = false;
		target = null;
		targetKind = null;
		nextTerrainAt = ctx.now + pauseTicks;
	}

	// ---------------------------------------------------------------- terrain

	private Planter.Result terrainStep(WorkContext ctx, WorkZone zone, Allowed allowed) {
		FieldWorkConfig config = ctx.config.farmer.fieldWork;
		if (!allowed.terrain()) {
			finishTerrain(ctx, 0);
			return Planter.Result.DONE;
		}
		if (survey == null && (jobsEmpty() || ctx.now - plannedAt > SURVEY_TTL)) {
			startSurvey(ctx, zone);
		}
		Survey current = survey;
		if (current != null) {
			return surveyStep(ctx, zone, allowed, current, config);
		}
		if (fetchChest != null) {
			return fetchStep(ctx);
		}
		return workStep(ctx, zone, allowed, config);
	}

	private void startSurvey(WorkContext ctx, WorkZone zone) {
		clearJobs();
		WorkArea area = WorkArea.of(zone);
		int[] bounds = zone.bounds();
		int range = FieldPlanner.RANGE;
		FieldPlanner.Grid grid = new FieldPlanner.Grid(bounds[2] - bounds[0] + 1 + 2 * range, bounds[3] - bounds[1] + 1 + 2 * range);
		survey = new Survey(area, new WorkArea.Scan(area, ctx.villager.blockPosition()), grid, bounds[0] - range, bounds[1] - range);
	}

	private Planter.Result surveyStep(WorkContext ctx, WorkZone zone, Allowed allowed, Survey current, FieldWorkConfig config) {
		Planting.Chunks chunks = new Planting.Chunks(ctx.level);
		for (int i = 0; i < config.surveyChunksPerStep; i++) {
			LevelChunk chunk = current.scan.next(ctx);
			if (chunk == null) {
				break;
			}
			readChunk(ctx, current, chunk, chunks, allowed);
		}
		if (!current.scan.done()) {
			ctx.status("villager.field.status.survey", zone.name, current.scan.position(), current.scan.total());
			return Planter.Result.WORKING;
		}
		survey = null;
		if (current.scan.readChunks() == 0) {
			finishTerrain(ctx, UNLOADED_PAUSE_TICKS);
			return Planter.Result.DONE;
		}
		readMargin(current, chunks);
		if (allowed.irrigate()) {
			readHoles(ctx, zone, current, chunks);
		}
		plan(ctx, zone, allowed, current);
		plannedAt = ctx.now;
		if (jobsEmpty()) {
			note = "villager.field.note.done";
			finishTerrain(ctx, WorkContext.ticks(config.pauseSeconds));
			return Planter.Result.DONE;
		}
		ctx.status("villager.field.status.survey", zone.name, current.scan.total(), current.scan.total());
		return Planter.Result.WORKING;
	}

	/** Columns of one loaded chunk of the zone. */
	private static void readChunk(WorkContext ctx, Survey survey, LevelChunk chunk, Planting.Chunks chunks, Allowed allowed) {
		FieldPlanner.Grid grid = survey.grid;
		int x0 = chunk.getPos().getMinBlockX();
		int z0 = chunk.getPos().getMinBlockZ();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int index = 0; index < 256; index++) {
			int x = x0 + (index >> 4);
			int z = z0 + (index & 15);
			int cell = survey.cell(x, z);
			if (cell < 0 || !survey.area.contains(x, z)) {
				continue;
			}
			grid.zone[cell] = true;
			int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockState ground = chunk.getBlockState(cursor.set(x, top, z));
			BlockState above = chunk.getBlockState(cursor.set(x, top + 1, z));
			grid.ground[cell] = top;
			if (FieldWorld.isWater(ground)) {
				grid.water[cell] = top;
				continue;
			}
			if (ground.is(Blocks.FARMLAND)) {
				grid.farmland[cell] = true;
				if (allowed.bonemeal() && FieldWorld.growing(above) && survey.crops.size() < MAX_CROPS) {
					survey.crops.add(new BlockPos(x, top + 1, z));
				}
				if (allowed.irrigate() && FieldWorld.free(above, allowed.clear()) && FieldWorld.holeSafe(ctx, chunks, new BlockPos(x, top, z))) {
					grid.hole[cell] = true;
				}
				continue;
			}
			if (!FieldWorld.levelSoil(ground) || !FieldWorld.free(above, allowed.clear()) || !FieldWorld.friendly(chunks, x, top, z)) {
				continue;
			}
			grid.level[cell] = true;
			grid.soil[cell] = FieldWorld.tillSoil(ground);
			if (allowed.irrigate() && FieldWorld.holeSafe(ctx, chunks, new BlockPos(x, top, z))) {
				grid.hole[cell] = true;
			}
		}
	}

	/** Columns around the zone (and in its skipped chunks): only their height and water, which hydrates the zone. */
	private static void readMargin(Survey survey, Planting.Chunks chunks) {
		FieldPlanner.Grid grid = survey.grid;
		for (int gz = 0; gz < grid.depth; gz++) {
			for (int gx = 0; gx < grid.width; gx++) {
				int cell = grid.index(gx, gz);
				if (grid.zone[cell]) {
					continue;
				}
				int x = survey.ox + gx;
				int z = survey.oz + gz;
				int top = chunks.top(x, z);
				if (top == Integer.MIN_VALUE) {
					continue;
				}
				grid.ground[cell] = top;
				if (FieldWorld.isWater(chunks.state(x, top, z))) {
					grid.water[cell] = top;
				}
			}
		}
	}

	/**
	 * Water holes dug before: present ones count as water (wherever the column top is), missing ones are refilled
	 * (counted as water too), the ones a player built on are forgotten.
	 */
	private static void readHoles(WorkContext ctx, WorkZone zone, Survey survey, Planting.Chunks chunks) {
		FieldPlanner.Grid grid = survey.grid;
		Iterator<BlockPos> it = zone.field().holes.iterator();
		while (it.hasNext()) {
			BlockPos hole = it.next();
			int cell = survey.cell(hole.getX(), hole.getZ());
			if (cell < 0 || !grid.zone[cell]) {
				if (!zone.contains(hole)) {
					it.remove();
					GuildManager.markDirty();
				}
				continue;
			}
			BlockState state = chunks.state(hole);
			BlockState above = chunks.state(hole.above());
			if (state == null) {
				continue;
			}
			boolean source = FieldWorld.isWater(state) && state.getFluidState().isSource();
			if (!source) {
				if (!FieldWorld.diggable(state, above) || !FieldWorld.free(above, true) || !FieldWorld.holeSafe(ctx, chunks, hole)) {
					it.remove();
					GuildManager.markDirty();
					continue;
				}
				survey.refills.add(hole);
			}
			survey.reserved[cell] = true;
			grid.water[cell] = hole.getY();
			grid.ground[cell] = hole.getY();
			grid.soil[cell] = false;
			grid.level[cell] = false;
			grid.hole[cell] = false;
			grid.farmland[cell] = false;
		}
	}

	/** Job lists from the grid: refills, holes, levelling, tilling, bone meal. */
	private void plan(WorkContext ctx, WorkZone zone, Allowed allowed, Survey survey) {
		FieldPlanner.Grid grid = survey.grid;
		int size = grid.size();
		boolean[] need = new boolean[size];
		for (int i = 0; i < size; i++) {
			need[i] = grid.zone[i] && ((allowed.till() && grid.soil[i]) || (allowed.irrigate() && grid.farmland[i]));
		}
		int room = allowed.irrigate() ? Math.max(0, ctx.config.farmer.irrigation.maxHolesPerZone - zone.field().holes.size()) : 0;
		FieldPlanner.Plan plan = FieldPlanner.irrigation(grid, need, allowed.irrigate(), room);
		boolean[] reserved = survey.reserved;
		if (allowed.irrigate()) {
			jobs.get(Kind.REFILL).addAll(survey.refills);
			for (int[] hole : plan.holes()) {
				int cell = grid.index(hole[0], hole[1]);
				reserved[cell] = true;
				jobs.get(Kind.HOLE).add(new BlockPos(survey.ox + hole[0], grid.ground[cell], survey.oz + hole[1]));
			}
		}
		boolean[] levelled = new boolean[size];
		if (allowed.level()) {
			for (FieldPlanner.LevelJob job : FieldPlanner.levelJobs(grid, reserved, MAX_JOBS)) {
				int cell = grid.index(job.x(), job.z());
				levelled[cell] = true;
				int y = grid.ground[cell];
				BlockPos pos = new BlockPos(survey.ox + job.x(), job.fill() ? y + 1 : y, survey.oz + job.z());
				jobs.get(job.fill() ? Kind.FILL : Kind.SHAVE).add(pos);
			}
		}
		if (allowed.till()) {
			List<BlockPos> till = jobs.get(Kind.TILL);
			for (int gz = 0; gz < grid.depth && till.size() < MAX_JOBS; gz++) {
				for (int gx = 0; gx < grid.width && till.size() < MAX_JOBS; gx++) {
					int cell = grid.index(gx, gz);
					if (grid.zone[cell] && grid.soil[cell] && plan.covered()[cell] && !reserved[cell] && !levelled[cell]) {
						till.add(new BlockPos(survey.ox + gx, grid.ground[cell], survey.oz + gz));
					}
				}
			}
		}
		if (allowed.bonemeal()) {
			List<BlockPos> crops = jobs.get(Kind.BONEMEAL);
			crops.addAll(survey.crops);
			while (crops.size() > ctx.config.farmer.fieldWork.boneMealPerRound) {
				crops.removeLast();
			}
		}
		for (Kind kind : Kind.values()) {
			totals[kind.ordinal()] = jobs.get(kind).size();
			dones[kind.ordinal()] = 0;
		}
	}

	private Planter.Result workStep(WorkContext ctx, WorkZone zone, Allowed allowed, FieldWorkConfig config) {
		Planting.Chunks chunks = new Planting.Chunks(ctx.level);
		int checks = 0;
		while (true) {
			if (target == null || targetKind == null || !jobs.get(targetKind).contains(target)) {
				pickTarget(ctx);
			}
			BlockPos pos = target;
			Kind kind = targetKind;
			if (pos == null || kind == null) {
				if (done == 0) {
					note = missingNote();
					if (note == null) {
						note = "villager.field.note.done";
					}
					finishTerrain(ctx, WorkContext.ticks(config.pauseSeconds));
				} else {
					note = missingNote();
					finishTerrain(ctx, 0);
				}
				return Planter.Result.DONE;
			}
			if (valid(ctx, zone, chunks, kind, pos, allowed)) {
				break;
			}
			jobs.get(kind).remove(pos);
			target = null;
			if (++checks >= MAX_CHECKS) {
				ctx.status("villager.field.status.checking", zone.name);
				return Planter.Result.WORKING;
			}
		}
		Kind kind = targetKind;
		BlockPos pos = target;
		Item resource = resource(ctx, kind);
		if (resource != null && PlantStock.countInBag(ctx, resource) < amount(ctx, kind)) {
			if (!startFetch(ctx, kind, resource)) {
				missing.add(kind);
				target = null;
				return Planter.Result.WORKING;
			}
			return fetchStep(ctx);
		}
		switch (Movement.moveTo(ctx, pos.above(), REACH, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> {
				jobs.get(kind).remove(pos);
				target = null;
				return Planter.Result.WORKING;
			}
			case MOVING -> {
				ctx.status("villager.field.status.going." + group(kind), progress(kind)[0], progress(kind)[1]);
				return Planter.Result.WORKING;
			}
			default -> {
			}
		}
		int budget = (int) Math.max(1, Math.round(config.actionsPerSecond * Upgrades.workRate(ctx.guild)));
		int acted = 0;
		List<BlockPos> list = jobs.get(kind);
		list.remove(pos);
		target = null;
		if (act(ctx, zone, chunks, kind, pos, allowed)) {
			acted++;
		}
		Iterator<BlockPos> it = list.iterator();
		while (it.hasNext() && acted < budget && !ctx.mustReturn()) {
			BlockPos other = it.next();
			if (ctx.villager.position().distanceToSqr(other.getX() + 0.5, other.getY() + 0.5, other.getZ() + 0.5) > WORK_REACH_SQ) {
				continue;
			}
			if (resource != null && PlantStock.countInBag(ctx, resource) < amount(ctx, kind)) {
				break;
			}
			it.remove();
			if (act(ctx, zone, chunks, kind, other, allowed)) {
				acted++;
			}
		}
		// Coarse / rooted dirt that became dirt: tilled by a later action.
		list.addAll(again);
		again.clear();
		if (acted > 0) {
			done += acted;
			Movement.hold(ctx, pos);
			WorldUtil.swing(ctx.villager);
			ctx.status("villager.field.status.work." + group(kind), progress(kind)[0], progress(kind)[1]);
		}
		if (done >= config.perRound || ctx.mustReturn()) {
			note = missingNote();
			finishTerrain(ctx, 0);
			return Planter.Result.DONE;
		}
		return Planter.Result.WORKING;
	}

	/** Nearest job of the first kind (priority order) that has jobs and its resource. */
	private void pickTarget(WorkContext ctx) {
		target = null;
		targetKind = null;
		for (Kind kind : Kind.values()) {
			List<BlockPos> list = jobs.get(kind);
			if (list.isEmpty() || missing.contains(kind)) {
				continue;
			}
			BlockPos best = null;
			double bestDist = Double.MAX_VALUE;
			for (BlockPos pos : list) {
				double dist = ctx.distSq(pos);
				if (dist < bestDist) {
					bestDist = dist;
					best = pos;
				}
			}
			target = best;
			targetKind = kind;
			return;
		}
	}

	/** Lang group of a kind (statuses). */
	private static String group(Kind kind) {
		return switch (kind) {
			case REFILL -> "refill";
			case HOLE -> "hole";
			case FILL, SHAVE -> "level";
			case TILL -> "till";
			case BONEMEAL -> "bonemeal";
		};
	}

	/** {done, total} of a kind (filling and shaving together). */
	private int[] progress(Kind kind) {
		if (kind == Kind.FILL || kind == Kind.SHAVE) {
			return new int[]{dones[Kind.FILL.ordinal()] + dones[Kind.SHAVE.ordinal()], totals[Kind.FILL.ordinal()] + totals[Kind.SHAVE.ordinal()]};
		}
		return new int[]{dones[kind.ordinal()], totals[kind.ordinal()]};
	}

	private @Nullable String missingNote() {
		if (missing.contains(Kind.HOLE) || missing.contains(Kind.REFILL)) {
			return "villager.field.note.no_buckets";
		}
		if (missing.contains(Kind.FILL)) {
			return "villager.field.note.no_dirt";
		}
		if (missing.contains(Kind.BONEMEAL)) {
			return "villager.field.note.no_bone_meal";
		}
		return null;
	}

	// ---------------------------------------------------------------- resources

	/** Item a job uses (null = none). */
	private static @Nullable Item resource(WorkContext ctx, Kind kind) {
		return switch (kind) {
			case REFILL, HOLE -> ctx.config.farmer.irrigation.waterBucketCost > 0 ? Items.WATER_BUCKET : null;
			case FILL -> Items.DIRT;
			case BONEMEAL -> Items.BONE_MEAL;
			default -> null;
		};
	}

	private static int amount(WorkContext ctx, Kind kind) {
		return kind == Kind.REFILL || kind == Kind.HOLE ? ctx.config.farmer.irrigation.waterBucketCost : 1;
	}

	/** Looks for a storage chest holding the item. @return false when none (or the storage may not be used) */
	private boolean startFetch(WorkContext ctx, Kind kind, Item item) {
		boolean buckets = item == Items.WATER_BUCKET;
		if (!buckets && !ctx.config.farmer.fieldWork.fromStorage) {
			return false;
		}
		List<PlantStock.ChestView> chests = PlantStock.chests(ctx, candidate -> candidate == item, failedChests);
		if (chests.isEmpty()) {
			return false;
		}
		fetchChest = chests.getFirst().pos();
		fetchItem = item;
		fetchCount = buckets ? Math.max(amount(ctx, kind), maxBuckets(ctx)) : ctx.config.farmer.fieldWork.fetchAmount;
		return true;
	}

	private Planter.Result fetchStep(WorkContext ctx) {
		BlockPos pos = fetchChest;
		Item item = fetchItem;
		if (pos == null || item == null) {
			fetchChest = null;
			fetchItem = null;
			return Planter.Result.WORKING;
		}
		switch (Movement.moveTo(ctx, pos, REACH, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> {
				failedChests.add(pos);
				fetchChest = null;
				fetchItem = null;
				return Planter.Result.WORKING;
			}
			case MOVING -> {
				ctx.status(fetchStatus(item));
				return Planter.Result.WORKING;
			}
			default -> {
			}
		}
		int moved = PlantStock.fetch(ctx, pos, item, fetchCount, false);
		fetchChest = null;
		fetchItem = null;
		if (moved <= 0) {
			failedChests.add(pos);
		} else {
			Movement.hold(ctx, pos);
			WorldUtil.swing(ctx.villager);
			WorldUtil.playSound(ctx.level, ctx.villager, SoundEvents.BUNDLE_REMOVE_ONE, 0.8f, 1.0f);
			ctx.status(fetchStatus(item));
		}
		return Planter.Result.WORKING;
	}

	private static String fetchStatus(Item item) {
		if (item == Items.WATER_BUCKET) {
			return "villager.field.status.fetch.water_bucket";
		}
		return item == Items.DIRT ? "villager.field.status.fetch.dirt" : "villager.field.status.fetch.bone_meal";
	}

	// ---------------------------------------------------------------- jobs

	/** The job still makes sense in the world (checked before walking there and again before acting). */
	private static boolean valid(WorkContext ctx, WorkZone zone, Planting.Chunks chunks, Kind kind, BlockPos pos, Allowed allowed) {
		if (!ctx.loaded(pos) || ctx.foreign(pos) || !zone.contains(pos)) {
			return false;
		}
		BlockState state = chunks.state(pos);
		BlockState above = chunks.state(pos.above());
		if (state == null || above == null) {
			return false;
		}
		return switch (kind) {
			case REFILL -> !(FieldWorld.isWater(state) && state.getFluidState().isSource()) && FieldWorld.diggable(state, above)
					&& FieldWorld.free(above, true) && FieldWorld.holeSafe(ctx, chunks, pos);
			case HOLE -> !FieldWorld.isWater(state) && FieldWorld.diggable(state, above) && !state.isAir() && FieldWorld.free(above, allowed.clear())
					&& FieldWorld.holeSafe(ctx, chunks, pos);
			case FILL -> FieldWorld.free(state, allowed.clear()) && FieldWorld.levelSoil(chunks.state(pos.below())) && sides(chunks, pos, true) >= 3
					&& FieldWorld.friendly(chunks, pos.getX(), pos.getY() - 1, pos.getZ());
			case SHAVE -> FieldWorld.levelSoil(state) && FieldWorld.free(above, allowed.clear()) && sides(chunks, pos, false) >= 3
					&& FieldWorld.friendly(chunks, pos.getX(), pos.getY(), pos.getZ());
			case TILL -> FieldWorld.tillSoil(state) && FieldWorld.free(above, allowed.clear()) && FieldWorld.friendly(chunks, pos.getX(), pos.getY(), pos.getZ())
					&& FieldWorld.hydrated(chunks, pos);
			case BONEMEAL -> FieldWorld.growing(state);
		};
	}

	/**
	 * Sides of a pit ({@code pit}: soil or farmland at the level of the block to fill) or of a bump (free above the soil
	 * one block lower) still around {@code pos}.
	 */
	private static int sides(Planting.Chunks chunks, BlockPos pos, boolean pit) {
		int count = 0;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos side = pos.relative(direction);
			if (pit) {
				BlockState state = chunks.state(side);
				if (FieldWorld.levelSoil(state) || (state != null && state.is(Blocks.FARMLAND))) {
					count++;
				}
			} else {
				BlockState state = chunks.state(side);
				BlockState below = chunks.state(side.below());
				if (state != null && (state.isAir() || FieldWorld.clearable(state)) && (FieldWorld.levelSoil(below) || (below != null && below.is(Blocks.FARMLAND)))) {
					count++;
				}
			}
		}
		return count;
	}

	/** Does the job (checked again). @return true when something was done */
	private boolean act(WorkContext ctx, WorkZone zone, Planting.Chunks chunks, Kind kind, BlockPos pos, Allowed allowed) {
		if (!valid(ctx, zone, chunks, kind, pos, allowed)) {
			return false;
		}
		boolean acted = switch (kind) {
			case REFILL, HOLE -> water(ctx, zone, pos, kind == Kind.REFILL || allowed.clear());
			case FILL -> fill(ctx, pos);
			case SHAVE -> shave(ctx, pos, allowed.clear());
			case TILL -> till(ctx, pos, allowed.clear());
			case BONEMEAL -> boneMeal(ctx, pos);
		};
		if (acted) {
			dones[kind.ordinal()]++;
			GuildManager.markDirty();
		}
		return acted;
	}

	/** Digs (or refills) a water hole: the soil goes to the bag, a water source takes its place. */
	private boolean water(WorkContext ctx, WorkZone zone, BlockPos pos, boolean clear) {
		ServerLevel level = ctx.level;
		int cost = ctx.config.farmer.irrigation.waterBucketCost;
		if (cost > 0) {
			if (!PlantStock.takeFromBag(ctx, Items.WATER_BUCKET, cost)) {
				return false;
			}
			PlantStock.addToBag(ctx, Items.BUCKET, cost);
		}
		clearAbove(ctx, pos, clear);
		BlockState ground = level.getBlockState(pos);
		if (!ground.isAir() && ground.getFluidState().isEmpty()) {
			carry(ctx, pos, Block.getDrops(ground, level, pos, null, ctx.villager, SHOVEL));
			WorldUtil.breakEffect(level, pos, ground);
		}
		BlockState water = Blocks.WATER.defaultBlockState();
		level.setBlockAndUpdate(pos, water);
		level.gameEvent(GameEvent.FLUID_PLACE, pos, GameEvent.Context.of(ctx.villager, water));
		level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
		FieldState.addHole(zone.field().holes, pos, ctx.config.farmer.irrigation.maxHolesPerZone);
		ctx.record.stats.waterHoles++;
		return true;
	}

	/** Fills a 1-block pit with dirt of the bag. */
	private static boolean fill(WorkContext ctx, BlockPos pos) {
		ServerLevel level = ctx.level;
		BlockState inside = level.getBlockState(pos);
		if (!PlantStock.takeFromBag(ctx, Items.DIRT, 1)) {
			return false;
		}
		if (FieldWorld.clearable(inside)) {
			carry(ctx, pos, Block.getDrops(inside, level, pos, null, ctx.villager, ItemStack.EMPTY));
			level.destroyBlock(pos, false, ctx.villager);
		}
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		level.setBlockAndUpdate(pos, dirt);
		level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(ctx.villager, dirt));
		level.playSound(null, pos, dirt.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0f, 0.8f);
		return true;
	}

	/** Shaves a 1-block bump: its block goes to the bag (dirt to fill pits with). */
	private static boolean shave(WorkContext ctx, BlockPos pos, boolean clear) {
		ServerLevel level = ctx.level;
		clearAbove(ctx, pos, clear);
		BlockState state = level.getBlockState(pos);
		carry(ctx, pos, Block.getDrops(state, level, pos, null, ctx.villager, SHOVEL));
		level.destroyBlock(pos, false, ctx.villager);
		return true;
	}

	/**
	 * Tills like the vanilla hoe: grass block and dirt become farmland; coarse dirt first becomes dirt, rooted dirt
	 * becomes dirt and gives hanging roots (both tilled by a later action, the job goes back to the list).
	 */
	private boolean till(WorkContext ctx, BlockPos pos, boolean clear) {
		ServerLevel level = ctx.level;
		clearAbove(ctx, pos, clear);
		BlockState state = level.getBlockState(pos);
		BlockState result;
		boolean twice = false;
		if (state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT)) {
			if (state.is(Blocks.ROOTED_DIRT)) {
				carry(ctx, pos, List.of(new ItemStack(Items.HANGING_ROOTS)));
			}
			result = Blocks.DIRT.defaultBlockState();
			twice = true;
		} else {
			result = Blocks.FARMLAND.defaultBlockState();
		}
		level.setBlockAndUpdate(pos, result);
		level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(ctx.villager, result));
		level.playSound(null, pos, SoundEvents.HOE_TILL.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
		if (twice) {
			this.again.add(pos.immutable());
			totals[Kind.TILL.ordinal()]++;
		} else {
			ctx.record.stats.tilled++;
			tilledSinceWake = true;
		}
		return true;
	}

	private static boolean boneMeal(WorkContext ctx, BlockPos pos) {
		ItemStack meal = PlantStock.bagStack(ctx, Items.BONE_MEAL);
		if (meal == null || !BoneMealItem.growCrop(meal, ctx.level, pos)) {
			return false;
		}
		ctx.level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 15);
		ctx.record.carried.removeIf(ItemStack::isEmpty);
		return true;
	}

	/** Removes the small plant on top of {@code pos} (its drops go to the bag). */
	private static void clearAbove(WorkContext ctx, BlockPos pos, boolean clear) {
		BlockPos above = pos.above();
		BlockState state = ctx.level.getBlockState(above);
		if (clear && FieldWorld.clearable(state)) {
			carry(ctx, above, Block.getDrops(state, ctx.level, above, null, ctx.villager, ItemStack.EMPTY));
			ctx.level.destroyBlock(above, false, ctx.villager);
		}
	}

	/** Into the bag; what does not fit falls on the spot. */
	private static void carry(WorkContext ctx, BlockPos pos, List<ItemStack> drops) {
		for (ItemStack drop : drops) {
			if (drop.isEmpty()) {
				continue;
			}
			ItemStack leftover = ctx.addCarried(drop);
			if (!leftover.isEmpty()) {
				ItemEntity entity = new ItemEntity(ctx.level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, leftover);
				entity.setDefaultPickUpDelay();
				ctx.level.addFreshEntity(entity);
			}
		}
	}

	// ---------------------------------------------------------------- retill

	private Planter.Result retillStep(WorkContext ctx, WorkZone zone, CropSower sower, Allowed allowed) {
		FieldWorkConfig config = ctx.config.farmer.fieldWork;
		if (!allowed.retill()) {
			finishRetill(ctx, sower, 0, config);
			return Planter.Result.DONE;
		}
		List<BlockPos> list = retill;
		if (list == null) {
			list = new ArrayList<>();
			for (FieldState.Trampled entry : zone.field().trampled) {
				if (zone.contains(entry.pos)) {
					list.add(entry.pos);
				}
			}
			retill = list;
			retillTotal = list.size();
			retillDone = 0;
			seedsAvailable = ctx.config.farmer.sow && ZonePermissions.allows(zone, ZoneOption.SOW) && sower.hasSeeds(ctx, zone);
		}
		Planting.Chunks chunks = new Planting.Chunks(ctx.level);
		BlockPos pos = null;
		int checks = 0;
		while (pos == null) {
			BlockPos candidate = nearest(ctx, list);
			if (candidate == null) {
				finishRetill(ctx, sower, done, config);
				return Planter.Result.DONE;
			}
			switch (checkRetill(ctx, zone, chunks, candidate, allowed)) {
				case OK -> pos = candidate;
				case FORGET -> {
					list.remove(candidate);
					if (FieldState.forget(zone.field().trampled, candidate)) {
						GuildManager.markDirty();
					}
				}
				case SKIP -> list.remove(candidate);
			}
			if (pos == null && ++checks >= MAX_CHECKS) {
				ctx.status("villager.field.status.checking", zone.name);
				return Planter.Result.WORKING;
			}
		}
		switch (Movement.moveTo(ctx, pos.above(), REACH, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> {
				list.remove(pos);
				return Planter.Result.WORKING;
			}
			case MOVING -> {
				ctx.status("villager.field.status.going.retill", retillDone, retillTotal);
				return Planter.Result.WORKING;
			}
			default -> {
			}
		}
		// Seeds and crops the trampled plant dropped around.
		Planting.pickUp(ctx, PICKUP_RADIUS, FieldWorks::cropItem);
		int budget = (int) Math.max(1, Math.round(config.actionsPerSecond * Upgrades.workRate(ctx.guild)));
		int acted = 0;
		list.remove(pos);
		if (retillOne(ctx, zone, sower, pos, allowed)) {
			acted++;
		}
		Iterator<BlockPos> it = list.iterator();
		while (it.hasNext() && acted < budget) {
			BlockPos other = it.next();
			if (ctx.villager.position().distanceToSqr(other.getX() + 0.5, other.getY() + 0.5, other.getZ() + 0.5) > WORK_REACH_SQ) {
				continue;
			}
			if (checkRetill(ctx, zone, chunks, other, allowed) != Check.OK) {
				continue;
			}
			it.remove();
			if (retillOne(ctx, zone, sower, other, allowed)) {
				acted++;
			}
		}
		if (acted > 0) {
			done += acted;
			retillDone += acted;
			Movement.hold(ctx, pos);
			WorldUtil.swing(ctx.villager);
			ctx.status("villager.field.status.work.retill", retillDone, retillTotal);
		}
		if (done >= config.perRound || ctx.mustReturn()) {
			finishRetill(ctx, sower, done, config);
			return Planter.Result.DONE;
		}
		return Planter.Result.WORKING;
	}

	private enum Check {
		OK,
		/** Left out of this round, kept in memory (not loaded, no seed and dry, plant on it). */
		SKIP,
		/** Forgotten (farmland again, built on, out of the zone). */
		FORGET
	}

	private Check checkRetill(WorkContext ctx, WorkZone zone, Planting.Chunks chunks, BlockPos pos, Allowed allowed) {
		if (!zone.contains(pos) || ctx.foreign(pos)) {
			return Check.FORGET;
		}
		if (!ctx.loaded(pos) || !ctx.ticking(pos)) {
			return Check.SKIP;
		}
		BlockState state = chunks.state(pos);
		BlockState above = chunks.state(pos.above());
		if (state == null || above == null) {
			return Check.SKIP;
		}
		if (!state.is(Blocks.DIRT) && !state.is(Blocks.GRASS_BLOCK)) {
			return Check.FORGET;
		}
		if (!FieldWorld.free(above, allowed.clear())) {
			return FieldWorld.clearable(above) ? Check.SKIP : Check.FORGET;
		}
		return seedsAvailable || FieldWorld.hydrated(chunks, pos) ? Check.OK : Check.SKIP;
	}

	/** Tills the spot again and sows it with a seed of the bag (the crop that grew there first, when the bag has it). */
	private boolean retillOne(WorkContext ctx, WorkZone zone, CropSower sower, BlockPos pos, Allowed allowed) {
		ServerLevel level = ctx.level;
		clearAbove(ctx, pos, allowed.clear());
		BlockState farmland = Blocks.FARMLAND.defaultBlockState();
		level.setBlockAndUpdate(pos, farmland);
		level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(ctx.villager, farmland));
		level.playSound(null, pos, SoundEvents.HOE_TILL.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
		ctx.record.stats.tilled++;
		String seed = "";
		for (FieldState.Trampled entry : zone.field().trampled) {
			if (entry.pos.equals(pos)) {
				seed = entry.seed;
				break;
			}
		}
		FieldState.forget(zone.field().trampled, pos);
		Item item = ZonePermissions.allows(zone, ZoneOption.SOW) && ctx.config.farmer.sow ? sower.seedInBag(ctx, zone, seed) : null;
		if (item == null || !sower.sow(ctx, pos.above(), item)) {
			retilledUnsown = true;
		}
		GuildManager.markDirty();
		return true;
	}

	private void finishRetill(WorkContext ctx, CropSower sower, int acted, FieldWorkConfig config) {
		active = false;
		retill = null;
		nextRetillAt = ctx.now + (acted > 0 ? 0L : WorkContext.ticks(config.pauseSeconds));
		if (retilledUnsown) {
			sower.wake();
		}
		retilledUnsown = false;
	}

	private static @Nullable BlockPos nearest(WorkContext ctx, List<BlockPos> list) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos pos : list) {
			double dist = ctx.distSq(pos);
			if (dist < bestDist) {
				bestDist = dist;
				best = pos;
			}
		}
		return best;
	}

	/** Seeds and harvests of the sown crops (picked up around a retilled spot). */
	private static boolean cropItem(ItemStack stack) {
		String id = ItemUtil.id(stack.getItem());
		return PlantingRules.SEEDS.contains(id) || id.equals("minecraft:wheat") || id.equals("minecraft:beetroot");
	}

	// ================================================================ statuses

	/** Idle status of a farmer, with the notes of the sowing ("plus de graines...") and of the field works. */
	static void idleStatus(WorkContext ctx, Planter sower, FieldWorks fields, String key, Object... args) {
		String first = sower.note();
		String second = fields.note();
		if (first == null && second == null) {
			ctx.status(key, args);
		} else if (first == null || second == null) {
			ctx.status("villager.plant.idle", Lang.raw(key, args), Lang.raw(first != null ? first : second));
		} else {
			ctx.status("villager.field.idle", Lang.raw(key, args), Lang.raw(first), Lang.raw(second));
		}
	}
}
