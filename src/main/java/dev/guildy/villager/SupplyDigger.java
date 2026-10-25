package dev.guildy.villager;

import dev.guildy.data.GuildManager;
import dev.guildy.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Digs one gathering pit for a wanted item, like a careful player: the ground above the pit's top first, then level after
 * level in the order of {@link PitPlan#level} (the ramp steps stay), at {@code gathering.ticksPerBlock} per block (work
 * speed upgrade applied). Every block is checked by the real miners' rules ({@link MineRules#dig}: only natural blocks,
 * never under a non-natural block, never under a fluid, never in another guild's land) and the fluids beside or below it
 * are sealed first with a filler block of the bag; a column that cannot be dug is given up (it stays as a pillar), a ramp
 * step that cannot be dug ends the pit at that level. Drops: the vanilla loot of the block with an iron tool of its kind;
 * the wanted item, items wanted by a construction site and a small reserve of filler blocks go into the bag, the rest is
 * left (or brought back with {@code gathering.keepByproducts}). A level about to open into a cave or a fluid ends the pit.
 */
final class SupplyDigger {
	enum Result {
		WORKING,
		/** Bag full or amount reached. */
		DONE,
		/** The pit holds no more of the item (or is finished). */
		EXHAUSTED
	}

	private final SupplyPit pit;
	private final SupplyPits.World world;
	private final PitPlan plan;
	private final Item item;
	private final int amount;
	private double budget;
	private int sinceCheck;

	SupplyDigger(SupplyPit pit, SupplyPits.World world, Item item, int amount) {
		this.pit = pit;
		this.world = world;
		this.plan = pit.plan();
		this.item = item;
		this.amount = amount;
	}

	SupplyPit pit() {
		return pit;
	}

	String place() {
		return world.place();
	}

	/** Units of the item in the bag (the trip's progress). */
	int progress(WorkContext ctx) {
		return CraftStock.countInBag(ctx, item);
	}

	Result tick(WorkContext ctx, String job) {
		SupplyGuild.of(ctx.guild).leasePit(pit.id, ctx.record.uuid, ctx.now);
		pit.usedAt = System.currentTimeMillis();
		if (progress(ctx) >= amount || CraftStock.roomFor(ctx, new ItemStack(item)) <= 0) {
			return Result.DONE;
		}
		if (pit.finished) {
			return Result.EXHAUSTED;
		}
		BlockPos center = new BlockPos(plan.x0() + plan.side() / 2, plan.top(), plan.z0() + plan.side() / 2);
		if (!world.ticking(center.getX(), center.getZ())) {
			ctx.status("villager.gather.job", job, dev.guildy.core.Lang.raw("supply.fail.unloaded"));
			return Result.WORKING;
		}
		SupplyConfig.Gathering cfg = Supply.config().gathering;
		budget += 20.0 * Upgrades.workRate(ctx.guild) / cfg.ticksPerBlock;
		int dug = 0;
		boolean moving = false;
		while (budget >= 1 && !moving) {
			Target target = next(ctx);
			if (target == null) {
				break;
			}
			if (target.exhausted) {
				return Result.EXHAUSTED;
			}
			BlockPos pos = target.pos;
			if (!reach(ctx, pos)) {
				moving = true;
				break;
			}
			Step step = dig(ctx, target);
			if (step == Step.WAIT) {
				break;
			}
			if (step == Step.DUG) {
				budget -= 1;
				dug++;
				if (progress(ctx) >= amount || CraftStock.roomFor(ctx, new ItemStack(item)) <= 0) {
					status(ctx, job);
					return Result.DONE;
				}
			}
		}
		budget = Math.min(budget, 4);
		if (moving) {
			ctx.status("villager.gather.job", job, dev.guildy.core.Lang.raw("supply.action.pit_go", place()));
		} else {
			status(ctx, job);
		}
		if (dug > 0) {
			GuildManager.markDirty();
		}
		return Result.WORKING;
	}

	private void status(WorkContext ctx, String job) {
		ctx.status("villager.gather.job", job,
				dev.guildy.core.Lang.raw("supply.action.dig", place(), progress(ctx), amount, Math.max(1, pit.levels + (pit.levelled ? 1 : 0))));
	}

	// ------------------------------------------------------------------ order

	private record Target(BlockPos pos, int cell, int level, boolean exhausted) {
	}

	/** Next block to dig (skipping the columns given up and the blocks already gone), or null to wait this step. */
	private @Nullable Target next(WorkContext ctx) {
		for (int guard = 0; guard < 4; guard++) {
			if (!pit.levelled) {
				Target hill = hill(ctx);
				if (hill != null) {
					return hill;
				}
				pit.levelled = true;
			}
			int k = pit.levels + 1;
			if (k > pit.depth) {
				finish();
				return new Target(BlockPos.ZERO, -1, k, true);
			}
			List<BlockPos> blocks = plan.level(k);
			BlockPos underSelf = null;
			for (BlockPos pos : blocks) {
				int cell = plan.cell(pos.getX(), pos.getZ());
				if (pit.stoppedAt(cell) <= k || world.at(pos.getX(), pos.getY(), pos.getZ()).passable()) {
					continue;
				}
				if (standsOver(ctx, pos)) {
					underSelf = pos;
					continue;
				}
				if (othersOver(ctx, pos)) {
					continue;
				}
				return new Target(pos, cell, k, false);
			}
			if (underSelf != null) {
				// Only the block under its feet is left: it steps aside onto a dug column first.
				stepAside(ctx, underSelf);
				return null;
			}
			// Level finished.
			pit.levels = k;
			GuildManager.markDirty();
			if (!nextLevelSafe(k + 1)) {
				pit.depth = k;
			}
			if (++sinceCheck >= 1 && PitRules.count(world, plan, pit.top, pit.levels) <= 0) {
				sinceCheck = 0;
				if (pit.levels >= pit.depth) {
					finish();
				}
				return new Target(BlockPos.ZERO, -1, k, true);
			}
		}
		return null;
	}

	/** Ground above the pit's top (an uneven footprint), highest blocks first. */
	private @Nullable Target hill(WorkContext ctx) {
		for (int y = pit.highest; y > plan.top(); y--) {
			for (int cell = 0; cell < plan.cells(); cell++) {
				int x = plan.cellX(cell);
				int z = plan.cellZ(cell);
				if (pit.stoppedAt(cell) <= 0 || world.at(x, y, z).passable()) {
					continue;
				}
				BlockPos pos = new BlockPos(x, y, z);
				if (standsOver(ctx, pos) || othersOver(ctx, pos)) {
					continue;
				}
				return new Target(pos, cell, 0, false);
			}
		}
		return null;
	}

	/** Level k may be dug: no cave (air) nor fluid among its blocks (a pit never opens into a cave). */
	private boolean nextLevelSafe(int k) {
		if (k > plan.depth()) {
			return false;
		}
		int y = plan.levelY(k);
		for (int cell = 0; cell < plan.cells(); cell++) {
			if (plan.limit(cell) < k || pit.stoppedAt(cell) <= k) {
				continue;
			}
			MineBlock block = world.at(plan.cellX(cell), y, plan.cellZ(cell));
			if (block == MineBlock.AIR || block == MineBlock.FIXTURE || block.liquid() || block == MineBlock.WET) {
				return false;
			}
		}
		return true;
	}

	private void finish() {
		if (!pit.finished) {
			pit.finished = true;
			GuildManager.markDirty();
		}
	}

	// ------------------------------------------------------------------ digging

	private enum Step {
		DUG,
		SKIPPED,
		WAIT
	}

	private Step dig(WorkContext ctx, Target target) {
		BlockPos pos = target.pos;
		MineBlock block = world.at(pos.getX(), pos.getY(), pos.getZ());
		if (block.liquid()) {
			// A fluid inside the pit: filled with a block of the bag, dug at the next step.
			if (!seal(ctx, pos)) {
				giveUp(target);
				return Step.SKIPPED;
			}
			return Step.WAIT;
		}
		MineRules.Check check = MineRules.dig(world, pos.getX(), pos.getY(), pos.getZ());
		switch (check.verdict()) {
			case CLEAR -> {
				return Step.SKIPPED;
			}
			case WAIT -> {
				return Step.WAIT;
			}
			case DIG -> {
			}
			default -> {
				giveUp(target);
				return Step.SKIPPED;
			}
		}
		for (BlockPos fluid : check.seal()) {
			if (!seal(ctx, fluid)) {
				giveUp(target);
				return Step.SKIPPED;
			}
		}
		if (world.mine.decorated(pos)) {
			giveUp(target);
			return Step.SKIPPED;
		}
		BlockState state = ctx.level.getBlockState(pos);
		BlockEntity entity = ctx.level.getBlockEntity(pos);
		ItemStack tool = SupplyBook.toolFor(state);
		List<ItemStack> drops = Block.getDrops(state, ctx.level, pos, entity, ctx.villager, tool);
		BlockPos above = pos.above();
		BlockState aboveState = ctx.level.getBlockState(above);
		if (!aboveState.isAir() && aboveState.getCollisionShape(ctx.level, above).isEmpty() && aboveState.getFluidState().isEmpty()
				&& world.at(above.getX(), above.getY(), above.getZ()) == MineBlock.AIR) {
			// A plant on the block: removed without drops (no stray items around the pit).
			ctx.level.destroyBlock(above, false);
		}
		holdTool(ctx, tool);
		Movement.hold(ctx, pos);
		WorldUtil.swing(ctx.villager);
		ctx.level.destroyBlock(pos, false, ctx.villager);
		keep(ctx, drops);
		pit.dug++;
		ctx.record.stats.mined++;
		return Step.DUG;
	}

	/** A column that cannot be dug at this level: given up; a ramp step given up ends the pit at this level. */
	private void giveUp(Target target) {
		if (target.cell < 0) {
			return;
		}
		pit.stop(target.cell, Math.max(0, target.level));
		if (plan.step(target.cell) >= 0 && target.level >= 1) {
			pit.depth = Math.min(pit.depth, target.level);
		}
		GuildManager.markDirty();
	}

	/** Seals a fluid block with a filler of the bag (a filler not wanted by a site first). */
	private boolean seal(WorkContext ctx, BlockPos fluid) {
		if (ctx.foreign(fluid)) {
			return false;
		}
		Map<Item, Integer> wanted = Procurement.wanted(ctx.guild);
		BlockState filler = MineWorld.takeFiller(ctx.record, stack -> wanted.containsKey(stack.getItem()) && stack.getItem() != item);
		if (filler == null) {
			filler = MineWorld.takeFiller(ctx.record);
		}
		if (filler == null) {
			return false;
		}
		world.mine.place(fluid, filler);
		return true;
	}

	/**
	 * Drops kept: the wanted item, items wanted by a construction site, filler blocks up to the reserve, and the rest only
	 * with {@code keepByproducts}. What does not fit stays on the ground.
	 */
	private void keep(WorkContext ctx, List<ItemStack> drops) {
		SupplyConfig.Gathering cfg = Supply.config().gathering;
		Map<Item, Integer> wanted = null;
		for (ItemStack drop : drops) {
			if (drop.isEmpty()) {
				continue;
			}
			boolean keep = drop.is(item);
			if (!keep) {
				if (wanted == null) {
					wanted = Procurement.wanted(ctx.guild);
				}
				keep = wanted.containsKey(drop.getItem()) || cfg.keepByproducts
						|| (MineWorld.isFiller(drop) && MineWorld.fillerCount(ctx.record) < cfg.fillerReserve);
			}
			if (!keep) {
				continue;
			}
			int room = CraftStock.roomFor(ctx, drop);
			if (room < drop.getCount() && drop.is(item)) {
				// The last units of the wanted item that do not fit fall at its feet (never lost).
				ItemStack rest = drop.copyWithCount(drop.getCount() - Math.max(0, room));
				net.minecraft.world.entity.item.ItemEntity entity = new net.minecraft.world.entity.item.ItemEntity(ctx.level, ctx.villager.getX(),
						ctx.villager.getY() + 0.5, ctx.villager.getZ(), rest);
				entity.setDefaultPickUpDelay();
				ctx.level.addFreshEntity(entity);
			}
			if (room <= 0) {
				continue;
			}
			ItemStack kept = drop.copyWithCount(Math.min(room, drop.getCount()));
			CraftStock.addStack(ctx, kept);
			String id = dev.guildy.core.ItemUtil.id(kept.getItem());
			pit.found.merge(id, (long) kept.getCount(), Long::sum);
		}
	}

	// ------------------------------------------------------------------ position

	/** Close enough to dig the block; else walks to the pit (or the block's column). */
	private boolean reach(WorkContext ctx, BlockPos pos) {
		double dx = ctx.villager.getX() - (pos.getX() + 0.5);
		double dy = ctx.villager.getEyeY() - (pos.getY() + 0.5);
		double dz = ctx.villager.getZ() - (pos.getZ() + 0.5);
		if (dx * dx + dy * dy + dz * dz <= 4.6 * 4.6 || pos.equals(ctx.runtime.remoteTarget)) {
			return true;
		}
		BlockPos goal = standFor(ctx, pos);
		return Movement.moveTo(ctx, goal, 1.5, ctx.config.work.walkTimeoutSeconds) == Movement.Result.ARRIVED;
	}

	/** Where to stand to dig a block: on the pit's floor or ramp next to it, never on the block itself. */
	private BlockPos standFor(WorkContext ctx, BlockPos pos) {
		BlockPos spot = WorldUtil.findSafeSpot(ctx.level, pos.above(), 3, true, candidate -> !candidate.below().equals(pos)
				&& (plan.contains(candidate.getX(), candidate.getZ()) || candidate.distManhattan(pos) <= 4));
		return spot != null ? spot : plan.entryOutside();
	}

	/** The villager stands on (or over) the block. */
	private static boolean standsOver(WorkContext ctx, BlockPos pos) {
		AABB space = new AABB(pos.getX(), pos.getY() + 1, pos.getZ(), pos.getX() + 1, pos.getY() + 2.5, pos.getZ() + 1);
		return ctx.villager.getBoundingBox().intersects(space);
	}

	/** Another living entity stands on the block: its floor is never dug away. */
	private boolean othersOver(WorkContext ctx, BlockPos pos) {
		return world.mine.standing(pos.above(), 2, ctx.villager) != null;
	}

	/** Moves off the last block of a level onto a column already dug (or the ramp). */
	private void stepAside(WorkContext ctx, BlockPos under) {
		BlockPos spot = WorldUtil.findSafeSpot(ctx.level, under, 3, true, candidate -> !candidate.below().equals(under)
				&& plan.contains(candidate.getX(), candidate.getZ()));
		if (spot == null) {
			spot = plan.entryOutside();
		}
		Movement.moveTo(ctx, spot, 0.5, 10);
	}

	/** Held tool while digging: a pickaxe, or a shovel for soft ground (visual only). */
	private static void holdTool(WorkContext ctx, ItemStack tool) {
		Item wanted = tool.isEmpty() || tool.is(Items.IRON_PICKAXE) ? Items.IRON_PICKAXE : tool.getItem();
		if (!ctx.villager.getMainHandItem().is(wanted)) {
			ctx.villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(wanted));
			ctx.villager.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
		}
	}
}
