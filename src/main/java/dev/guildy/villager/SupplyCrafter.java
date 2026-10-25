package dev.guildy.villager;

import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Crafts and smelts one plan of {@link SupplyPlanner}: fetches its inputs from the storage chests (really taken, see
 * {@link CraftStock}), plans again from what the bag really holds (the storage may have changed), then does the steps in
 * order: crafts by hand for the 2×2 recipes, at the nearest crafting table of the territory otherwise
 * ({@code crafting.ticksPerCraft} per craft), smelting simulated at a furnace of the territory
 * ({@code smelting.ticksPerItem} per item, fuel from the bag at its vanilla burn time). The furnace's real inventory is
 * never touched: it is only lit for the show (when it is idle and empty) and put out afterwards.
 */
final class SupplyCrafter {
	enum Result {
		WORKING,
		DONE,
		FAILED
	}

	private enum Phase {
		FETCH,
		PLAN,
		STEPS
	}

	private final SupplyGuild.Job job;
	private SupplyPlanner.Plan plan;
	private Phase phase = Phase.FETCH;
	private final Map<Item, Integer> toFetch = new LinkedHashMap<>();
	private final Set<BlockPos> excluded = new HashSet<>();
	private @Nullable BlockPos fetchFrom;
	private @Nullable Item fetching;
	private int stepIndex;
	private int doneInStep;
	private long progress;
	private @Nullable BlockPos station;
	private @Nullable BlockPos litFurnace;
	private double fuelCredit;
	private String failure = "";

	SupplyCrafter(SupplyGuild.Job job) {
		this.job = job;
		this.plan = job.plan();
		this.toFetch.putAll(SupplyGuild.items(job.plan()));
	}

	/** Why the job was given up (lang text). */
	String failure() {
		return failure;
	}

	Result tick(WorkContext ctx, String jobText) {
		return switch (phase) {
			case FETCH -> fetchStep(ctx, jobText);
			case PLAN -> planStep(ctx);
			case STEPS -> steps(ctx, jobText);
		};
	}

	/** Puts out the furnace lit for the show. */
	void stop(WorkContext ctx) {
		putOut(ctx);
	}

	// ------------------------------------------------------------------ fetch

	private Result fetchStep(WorkContext ctx, String jobText) {
		while (true) {
			Map.Entry<Item, Integer> next = null;
			for (Map.Entry<Item, Integer> entry : toFetch.entrySet()) {
				if (CraftStock.countInBag(ctx, entry.getKey()) < entry.getValue()) {
					next = entry;
					break;
				}
			}
			if (next == null) {
				phase = Phase.PLAN;
				return planStep(ctx);
			}
			Item item = next.getKey();
			int missing = next.getValue() - CraftStock.countInBag(ctx, item);
			if (fetching != item || fetchFrom == null) {
				fetching = item;
				fetchFrom = CraftStock.nearestWith(ctx, item, excluded);
				if (fetchFrom == null) {
					// Gone from the storage since the plan: what the bag holds is planned again.
					toFetch.remove(item);
					SupplyGuild.of(ctx.guild).invalidateStock();
					continue;
				}
			}
			BlockPos from = fetchFrom;
			switch (Movement.moveTo(ctx, from, 2.5, ctx.config.work.walkTimeoutSeconds)) {
				case UNREACHABLE -> {
					excluded.add(from);
					fetchFrom = null;
				}
				case MOVING -> ctx.status("villager.gather.job", jobText, Lang.raw("supply.action.fetch", SupplyLang.amount(missing, item)));
				case ARRIVED -> {
					int moved = CraftStock.fetch(ctx, from, item, missing);
					if (moved > 0) {
						Movement.hold(ctx, from);
						WorldUtil.swing(ctx.villager);
						WorldUtil.playSound(ctx.level, ctx.villager, SoundEvents.ITEM_PICKUP, 0.6f, 0.9f);
						SupplyGuild.of(ctx.guild).invalidateStock();
					} else {
						excluded.add(from);
					}
					fetchFrom = null;
					ctx.status("villager.gather.job", jobText, Lang.raw("supply.action.fetch", SupplyLang.amount(missing, item)));
				}
			}
			return Result.WORKING;
		}
	}

	/** Plans again from the bag: the largest amount (up to the job's) its contents make. */
	private Result planStep(WorkContext ctx) {
		Map<String, Integer> bag = new HashMap<>();
		for (ItemStack stack : ctx.record.carried) {
			if (!stack.isEmpty() && stack.getComponentsPatch().isEmpty()) {
				bag.merge(ItemUtil.id(stack.getItem()), stack.getCount(), Integer::sum);
			}
		}
		SupplyPlanner.Context base = SupplyGuild.of(ctx.guild).planning(ctx, Map.of(), ctx.record.uuid, true).context();
		SupplyPlanner.Context fromBag = new SupplyPlanner.Context(base.book(), bag, Map.of(), Set.of(), base.fuels(), base.settings());
		SupplyPlanner.Plan replanned = SupplyPlanner.bestFromStock(fromBag, ItemUtil.id(job.target()), job.amount(), ctx.config.work.carryStacks + 4);
		if (replanned == null) {
			failure = Lang.raw("supply.fail.stock");
			return Result.FAILED;
		}
		plan = replanned;
		stepIndex = 0;
		doneInStep = 0;
		progress = 0;
		phase = Phase.STEPS;
		return Result.WORKING;
	}

	// ------------------------------------------------------------------ steps

	private Result steps(WorkContext ctx, String jobText) {
		List<SupplyPlanner.Step> steps = plan.steps();
		if (stepIndex >= steps.size()) {
			putOut(ctx);
			return Result.DONE;
		}
		SupplyPlanner.Step step = steps.get(stepIndex);
		SupplyPlanner.Recipe recipe = step.recipe();
		SupplyGuild shared = SupplyGuild.of(ctx.guild);
		SupplyPlanner.Kind kind = recipe.kind();
		SupplyPlanner.Station need = recipe.station();
		BlockPos at = null;
		if (need != SupplyPlanner.Station.HAND) {
			at = station;
			boolean valid = at != null && ctx.loaded(at) && SupplyGuild.test(need).test(ctx.level.getBlockState(at)) && ctx.inTerritory(at);
			if (!valid) {
				putOut(ctx);
				shared.scanStations(ctx);
				at = shared.station(ctx, need);
				station = at;
				if (at == null) {
					int[] scan = shared.scanProgress();
					if (scan != null || !shared.stationsKnown()) {
						ctx.status("villager.gather.scanning", scan == null ? 0 : scan[0], scan == null ? 1 : scan[1]);
						return Result.WORKING;
					}
					if (need != SupplyPlanner.Station.WATER || !Supply.config().transforms.simulateWater) {
						shared.invalidateStations();
						failure = Lang.raw("supply.fail.station");
						return Result.FAILED;
					}
					// No water in the territory: simulated where it stands (supply.json5, transforms.simulateWater).
				}
			}
			if (at != null && !Movement.near(ctx, at, 3.0, 3.0)) {
				switch (Movement.moveTo(ctx, at, 2.5, ctx.config.work.walkTimeoutSeconds)) {
					case UNREACHABLE -> {
						station = null;
						shared.invalidateStations();
						return Result.WORKING;
					}
					case MOVING -> {
						ctx.status("villager.gather.job", jobText, Lang.raw("supply.action.go." + need.name().toLowerCase(java.util.Locale.ROOT)));
						return Result.WORKING;
					}
					default -> {
					}
				}
			}
			Movement.hold(ctx, at);
			if (kind == SupplyPlanner.Kind.SMELT && at != null) {
				light(ctx, at);
				furnaceEffects(ctx, at);
			}
		} else {
			Movement.hold(ctx, null);
		}
		String tool = SupplyBook.transformTool(recipe);
		if (kind == SupplyPlanner.Kind.TRANSFORM) {
			holdTool(ctx, tool);
		}
		SupplyConfig cfg = Supply.config();
		int ticks = switch (kind) {
			case SMELT -> cfg.smelting.ticksPerItem;
			case CUT -> cfg.crafting.ticksPerCut;
			case TRANSFORM -> cfg.transforms.ticksPerItem;
			case CRAFT -> cfg.crafting.ticksPerCraft;
		};
		long per = Math.max(1, Math.round(ticks / Upgrades.workRate(ctx.guild)));
		progress += 20;
		while (progress >= per && doneInStep < step.times()) {
			progress -= per;
			if (kind == SupplyPlanner.Kind.SMELT && !fuel(ctx)) {
				putOut(ctx);
				failure = Lang.raw("villager.gather.missing", Lang.raw("villager.gather.missing.fuel"), "");
				return Result.FAILED;
			}
			if (!craftOnce(ctx, step)) {
				// The bag changed (the storage had less than planned): planned again from what it holds.
				putOut(ctx);
				phase = Phase.PLAN;
				return Result.WORKING;
			}
			doneInStep++;
			effects(ctx, at, recipe, tool);
		}
		Item output = ItemUtil.item(recipe.output());
		String name = output == null ? recipe.output() : SupplyLang.name(output);
		String action = switch (kind) {
			case SMELT -> Lang.raw("supply.action.smelt", inputName(recipe), doneInStep, step.times());
			case CUT -> Lang.raw("supply.action.cut", name, doneInStep, step.times());
			case TRANSFORM -> Lang.raw("supply.action.transform." + tool, inputName(recipe), name, doneInStep, step.times());
			case CRAFT -> Lang.raw(need == SupplyPlanner.Station.TABLE ? "supply.action.craft" : "supply.action.hand_craft", name, doneInStep,
					step.times());
		};
		ctx.status("villager.gather.job", jobText, action);
		if (doneInStep >= step.times()) {
			stepIndex++;
			doneInStep = 0;
			progress = 0;
			if (stepIndex < steps.size() && steps.get(stepIndex).recipe().station() != need) {
				putOut(ctx);
				station = null;
			}
		}
		return Result.WORKING;
	}

	/** Tool held for a transformation (visual only; the task's own item comes back at the next step). */
	private static void holdTool(WorkContext ctx, String tool) {
		Item item = switch (tool) {
			case "axe" -> net.minecraft.world.item.Items.IRON_AXE;
			case "shovel" -> net.minecraft.world.item.Items.IRON_SHOVEL;
			case "hoe" -> net.minecraft.world.item.Items.IRON_HOE;
			case "water" -> net.minecraft.world.item.Items.WATER_BUCKET;
			default -> null;
		};
		if (item != null && !ctx.villager.getMainHandItem().is(item)) {
			ctx.villager.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(item));
			ctx.villager.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0f);
		}
	}

	private static String inputName(SupplyPlanner.Recipe recipe) {
		if (recipe.inputs().isEmpty() || recipe.inputs().getFirst().options().isEmpty()) {
			return "?";
		}
		Item input = ItemUtil.item(recipe.inputs().getFirst().options().getFirst());
		return input == null ? "?" : SupplyLang.name(input);
	}

	/** One craft: its inputs out of the bag (options mixed, keeping what later steps need), its output in. */
	private boolean craftOnce(WorkContext ctx, SupplyPlanner.Step step) {
		SupplyPlanner.Recipe recipe = step.recipe();
		Map<Item, Integer> later = laterNeeds();
		Map<Item, Integer> take = new LinkedHashMap<>();
		for (SupplyPlanner.Input input : recipe.inputs()) {
			int need = input.count();
			// Options with spare units (beyond what later steps need) first, then any.
			for (int pass = 0; pass < 2 && need > 0; pass++) {
				for (String option : input.options()) {
					Item item = ItemUtil.item(option);
					if (item == null || need <= 0) {
						continue;
					}
					int inBag = CraftStock.countInBag(ctx, item) - take.getOrDefault(item, 0);
					int spare = pass == 0 ? inBag - later.getOrDefault(item, 0) : inBag;
					int use = Math.min(need, Math.max(0, spare));
					if (use > 0) {
						take.merge(item, use, Integer::sum);
						need -= use;
					}
				}
			}
			if (need > 0) {
				return false;
			}
		}
		for (Map.Entry<Item, Integer> entry : take.entrySet()) {
			if (!CraftStock.takeFromBag(ctx, entry.getKey(), entry.getValue())) {
				return false;
			}
		}
		Item output = ItemUtil.item(recipe.output());
		if (output != null) {
			CraftStock.addStack(ctx, new ItemStack(output, recipe.count()));
		}
		return true;
	}

	/** Units of single-option inputs still needed by the crafts after the current one. */
	private Map<Item, Integer> laterNeeds() {
		Map<Item, Integer> needs = new HashMap<>();
		List<SupplyPlanner.Step> steps = plan.steps();
		for (int i = stepIndex; i < steps.size(); i++) {
			SupplyPlanner.Step step = steps.get(i);
			int crafts = i == stepIndex ? step.times() - doneInStep - 1 : step.times();
			if (crafts <= 0) {
				continue;
			}
			for (SupplyPlanner.Input input : step.recipe().inputs()) {
				if (input.options().size() == 1) {
					Item item = ItemUtil.item(input.options().getFirst());
					if (item != null) {
						needs.merge(item, input.count() * crafts, Integer::sum);
					}
				}
			}
		}
		return needs;
	}

	/** Pays one smelted item with the fuel credit, burning a fuel item of the bag when it runs out. */
	private boolean fuel(WorkContext ctx) {
		if (fuelCredit >= 1.0 - 1e-9) {
			fuelCredit -= 1.0;
			return true;
		}
		SupplyBook book = SupplyBook.of(ctx.level);
		Map<Item, Integer> later = laterNeeds();
		for (String id : plan.fuel().isEmpty() ? List.<String>of() : List.copyOf(plan.fuel().keySet())) {
			if (burn(ctx, book, id, later)) {
				return fuel(ctx);
			}
		}
		for (String id : SupplyBook.expandItems(Supply.config().smelting.fuels)) {
			if (burn(ctx, book, id, later)) {
				return fuel(ctx);
			}
		}
		return false;
	}

	private boolean burn(WorkContext ctx, SupplyBook book, String id, Map<Item, Integer> later) {
		Item item = ItemUtil.item(id);
		double per = book.smeltsPerFuel(id);
		if (item == null || per <= 0 || CraftStock.countInBag(ctx, item) - later.getOrDefault(item, 0) <= 0) {
			return false;
		}
		if (!CraftStock.takeFromBag(ctx, item, 1)) {
			return false;
		}
		fuelCredit += per;
		return true;
	}

	// ------------------------------------------------------------------ visuals

	private static void effects(WorkContext ctx, @Nullable BlockPos at, SupplyPlanner.Recipe recipe, String tool) {
		ServerLevel level = ctx.level;
		WorldUtil.swing(ctx.villager);
		Item output = ItemUtil.item(recipe.output());
		double x = at != null ? at.getX() + 0.5 : ctx.villager.getX();
		double y = at != null ? at.getY() + 1.1 : ctx.villager.getY() + 1.2;
		double z = at != null ? at.getZ() + 0.5 : ctx.villager.getZ();
		if (output != null) {
			level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, output), x, y, z, 6, 0.2, 0.1, 0.2, 0.05);
		}
		float pitch = 0.9f + level.getRandom().nextFloat() * 0.2f;
		switch (recipe.kind()) {
			case CRAFT -> level.playSound(null, x, y, z, SoundEvents.CRAFTER_CRAFT, SoundSource.NEUTRAL, 0.5f, pitch);
			case CUT -> level.playSound(null, x, y, z, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 0.7f, pitch);
			case SMELT -> level.playSound(null, x, y, z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.7f, 1.0f);
			case TRANSFORM -> {
				switch (tool) {
					case "axe" -> level.playSound(null, x, y, z, SoundEvents.AXE_STRIP, SoundSource.NEUTRAL, 0.7f, pitch);
					case "shovel" -> level.playSound(null, x, y, z, SoundEvents.SHOVEL_FLATTEN, SoundSource.NEUTRAL, 0.7f, pitch);
					case "hoe" -> level.playSound(null, x, y, z, SoundEvents.HOE_TILL, SoundSource.NEUTRAL, 0.7f, pitch);
					default -> {
						level.playSound(null, x, y, z, SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 0.6f, pitch);
						level.sendParticles(ParticleTypes.SPLASH, x, y - 0.4, z, 8, 0.3, 0.1, 0.3, 0.1);
					}
				}
			}
		}
	}

	private static void furnaceEffects(WorkContext ctx, BlockPos at) {
		ServerLevel level = ctx.level;
		BlockState state = level.getBlockState(at);
		Direction facing = state.hasProperty(AbstractFurnaceBlock.FACING) ? state.getValue(AbstractFurnaceBlock.FACING) : Direction.NORTH;
		RandomSource random = level.getRandom();
		double x = at.getX() + 0.5 + facing.getStepX() * 0.52;
		double y = at.getY() + 0.4;
		double z = at.getZ() + 0.5 + facing.getStepZ() * 0.52;
		level.sendParticles(ParticleTypes.FLAME, x, y, z, 2, 0.15, 0.1, 0.15, 0.0);
		level.sendParticles(ParticleTypes.SMOKE, x, y + 0.2, z, 2, 0.15, 0.1, 0.15, 0.0);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.getX() + 0.5, at.getY() + 1.1, at.getZ() + 0.5, 1, 0.1, 0.0, 0.1, 0.01);
		if (random.nextInt(3) == 0) {
			level.playSound(null, at, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.8f, 1.0f);
		}
	}

	/** Lights an idle, empty furnace for the show (its inventory is never touched). */
	private void light(WorkContext ctx, BlockPos at) {
		if (!Supply.config().smelting.lightFurnace || at.equals(litFurnace)) {
			return;
		}
		putOut(ctx);
		BlockState state = ctx.level.getBlockState(at);
		if (state.hasProperty(AbstractFurnaceBlock.LIT) && !state.getValue(AbstractFurnaceBlock.LIT) && idle(ctx, at)) {
			ctx.level.setBlockAndUpdate(at, state.setValue(AbstractFurnaceBlock.LIT, true));
			litFurnace = at;
		}
	}

	/** Puts out the furnace it lit (only when it is still lit and still idle and empty). */
	private void putOut(WorkContext ctx) {
		BlockPos at = litFurnace;
		litFurnace = null;
		if (at == null || !ctx.loaded(at)) {
			return;
		}
		BlockState state = ctx.level.getBlockState(at);
		if (state.hasProperty(AbstractFurnaceBlock.LIT) && state.getValue(AbstractFurnaceBlock.LIT) && idle(ctx, at)) {
			ctx.level.setBlockAndUpdate(at, state.setValue(AbstractFurnaceBlock.LIT, false));
		}
	}

	/** No input and no fuel in the furnace: nobody is using it. */
	private static boolean idle(WorkContext ctx, BlockPos at) {
		return ctx.level.getBlockEntity(at) instanceof AbstractFurnaceBlockEntity furnace && furnace.getItem(0).isEmpty() && furnace.getItem(1).isEmpty();
	}
}
