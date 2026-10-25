package dev.guildy.villager;

import dev.guildy.builds.Bags;
import dev.guildy.builds.BuildConfig;
import dev.guildy.builds.BuildModule;
import dev.guildy.builds.BuildPasses;
import dev.guildy.builds.BuildProject;
import dev.guildy.builds.BuildSite;
import dev.guildy.builds.BuildSites;
import dev.guildy.core.ItemUtil;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Task "Bâtisseur": builds the guild's construction sites ({@code dev.guildy.builds}) or fetches what they miss.
 * <p>
 * Each step the builder takes its preferred role (manual, or the guild's distribution, {@link Builders}) and falls
 * back to the other one when its own is impossible: BUILD needs a site with work it can do now (an active site, by
 * priority, the chosen one if any: "passer à la suivante" when one has nothing possible), GATHER needs wanted materials
 * ({@link Procurement}) and a {@link GatherWork} that can fetch them. A builder without any site but with wanted
 * materials gathers.
 * <p>
 * BUILD: leases a parcel of the site ({@link BuildSite#claim}), takes from the site's sources only what the parcel
 * needs ({@link BuildStock}), walks within reach and works its cells nearest first at {@code builder.blocksPerSecond}
 * (work speed upgrade included): the block shown in hand, arm swing, sounds and particles. Debris goes to the bag, then
 * to a deposit chest (materials of the site stay in the bag); when the storage is full the debris is dropped
 * ({@code terraform.discardWhenStorageFull}). A builder standing in the cell steps aside first.
 */
final class BuilderTask extends TaskHandler {
	private static final long SKIP_TICKS = 30 * 20L;
	private static final long CELL_SKIP_TICKS = 8 * 20L;
	private static final int MAX_ACTIONS_PER_STEP = 10;

	private @Nullable BuildSite site;
	private BuildSite.@Nullable Job job;
	private @Nullable GatherWork gather;
	private BuilderRoles.@Nullable Role working;
	private @Nullable BlockPos fetchTarget;
	private final Map<BlockPos, Long> emptySources = new HashMap<>();
	private final Map<BuildSite.Job, Long> skipped = new HashMap<>();
	private final Map<Integer, Long> skippedCells = new HashMap<>();
	private final DepositFlow deposit = new DepositFlow();
	private boolean depositing;
	private double credit;

	@Override
	void tick(WorkContext ctx) {
		BuilderData data = Builders.data(ctx.record);
		boolean manual = BuilderRoles.Role.parse(data.role) != null;
		BuilderRoles.Role preferred = Builders.role(ctx.guild, ctx.record);
		expire(ctx.now);

		if (depositing && !depositStep(ctx)) {
			note(ctx, preferred, manual, working);
			return;
		}

		BuildSite chosen = chooseSite(ctx, data);
		String gatherReason = gatherReason(ctx);
		BuilderRoles.Role role = preferred;
		if (role == BuilderRoles.Role.BUILD && chosen == null && gatherReason == null) {
			role = BuilderRoles.Role.GATHER;
		} else if (role == BuilderRoles.Role.GATHER && gatherReason != null && chosen != null) {
			role = BuilderRoles.Role.BUILD;
		}
		if (working != role) {
			if (working == BuilderRoles.Role.GATHER) {
				stopGather(ctx);
			} else if (working == BuilderRoles.Role.BUILD) {
				releaseSite(ctx);
			}
			working = role;
		}
		if (role == BuilderRoles.Role.GATHER) {
			if (gatherReason != null) {
				idle(ctx, data, gatherReason);
				note(ctx, preferred, manual, null);
				return;
			}
			if (gather == null) {
				gather = new GatherWork();
			}
			gather.tick(ctx);
			if (gather.finished()) {
				// The trip is over: the role is chosen again at the next step.
				gather.stop(ctx);
				gather = null;
			}
			note(ctx, preferred, manual, BuilderRoles.Role.GATHER);
			return;
		}
		if (chosen == null) {
			idle(ctx, data, gatherReason);
			note(ctx, preferred, manual, null);
			return;
		}
		buildStep(ctx, data, chosen);
		note(ctx, preferred, manual, BuilderRoles.Role.BUILD);
	}

	@Override
	void stop(WorkContext ctx) {
		releaseSite(ctx);
		stopGather(ctx);
		Villager villager = ctx.villager;
		if (!villager.getMainHandItem().isEmpty()) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
		Builders.leave(ctx.guild, ctx.record.uuid);
	}

	private void note(WorkContext ctx, BuilderRoles.Role preferred, boolean manual, BuilderRoles.@Nullable Role role) {
		int project = role == BuilderRoles.Role.BUILD && site != null ? site.project.id : -1;
		Builders.note(ctx.guild, ctx.record, preferred, manual, role, project);
	}

	private void expire(long now) {
		emptySources.values().removeIf(until -> until < now);
		skipped.values().removeIf(until -> until < now);
		skippedCells.values().removeIf(until -> until < now);
	}

	// ================================================================ choices

	/** The site this builder works on now: its chosen one, else the highest-priority site with work it can do. */
	private @Nullable BuildSite chooseSite(WorkContext ctx, BuilderData data) {
		Map<Item, Integer> bag = Bags.contents(ctx.record.carried);
		if (data.project >= 0) {
			BuildProject project = ctx.guild.builds.get(data.project);
			if (project != null && !project.finished) {
				BuildSite chosen = BuildSites.site(ctx.guild, project);
				return chosen != null && workable(chosen, bag) ? chosen : null;
			}
			// Its site is gone or finished: back to automatic.
			data.project = -1;
			GuildManager.markDirty();
		}
		if (site != null && job != null && workable(site, bag) && BuildSites.peek(ctx.guild, site.project) == site) {
			return site;
		}
		for (BuildSite candidate : BuildSites.sites(ctx.guild)) {
			if (workable(candidate, bag)) {
				return candidate;
			}
		}
		return null;
	}

	private static boolean workable(BuildSite site, Map<Item, Integer> bag) {
		if (!site.scanned() || !site.project.active()) {
			return false;
		}
		return site.actionable() > 0 || (!bag.isEmpty() && site.counts().need() > 0);
	}

	/** The site shown in the status when nothing can be done: the chosen one, else the first unfinished one. */
	private static @Nullable BuildSite topSite(WorkContext ctx, BuilderData data) {
		if (data.project >= 0) {
			BuildProject project = ctx.guild.builds.get(data.project);
			if (project != null) {
				return BuildSites.site(ctx.guild, project);
			}
		}
		List<BuildSite> sites = BuildSites.sites(ctx.guild);
		for (BuildSite candidate : sites) {
			if (candidate.project.active()) {
				return candidate;
			}
		}
		return sites.isEmpty() ? null : sites.getFirst();
	}

	/** Null when gathering is possible now, else the lang key of the reason. */
	private static @Nullable String gatherReason(WorkContext ctx) {
		if (Procurement.wanted(ctx.guild).isEmpty()) {
			return "villager.builder.gather.nothing";
		}
		return GatherWork.blocked(ctx);
	}

	/** Nothing to build: debris back to the storage, then a status telling why. */
	private void idle(WorkContext ctx, BuilderData data, @Nullable String gatherReason) {
		releaseSite(ctx);
		BuildSite top = topSite(ctx, data);
		if (!ctx.record.carried.isEmpty() && hasJunk(ctx, top)) {
			depositing = true;
			depositStep(ctx);
			return;
		}
		String gather = gatherReason == null ? "" : dev.guildy.core.Lang.raw(gatherReason);
		if (top == null) {
			ctx.status("villager.status.builder.no_site", gather);
			return;
		}
		String name = top.project.name;
		if (top.error() != null) {
			ctx.status("villager.status.builder.error", name, dev.guildy.core.Lang.raw(top.error(), top.errorArgs()));
		} else if (top.project.paused) {
			ctx.status("villager.status.builder.paused", name);
		} else if (!top.scanned()) {
			ctx.status("villager.status.builder.loading", name);
		} else if (top.counts().need() > 0) {
			Map<Item, Integer> missing = top.missing(ctx.now);
			Item most = null;
			int count = 0;
			for (Map.Entry<Item, Integer> entry : missing.entrySet()) {
				if (entry.getValue() > count) {
					most = entry.getKey();
					count = entry.getValue();
				}
			}
			if (most == null) {
				ctx.status("villager.status.builder.waiting_stock", name);
			} else {
				ctx.status(gatherReason == null ? "villager.status.builder.waiting_materials" : "villager.status.builder.waiting_materials_no_gather",
						count, ItemUtil.name(most).getString(), gather);
			}
		} else if (top.counts().blocked() > 0) {
			ctx.status("villager.status.builder.blocked", name, top.counts().blocked());
		} else if (top.counts().unknown() > 0) {
			ctx.status("villager.status.builder.unloaded", name);
		} else {
			ctx.status("villager.status.builder.nothing", name);
		}
	}

	// ================================================================ building

	private void buildStep(WorkContext ctx, BuilderData data, BuildSite chosen) {
		if (site != chosen) {
			releaseSite(ctx);
			site = chosen;
		}
		BuildSite current = chosen;
		BuildConfig.Builder cfg = BuildModule.config().builder;
		List<ItemStack> carried = ctx.record.carried;
		UUID me = ctx.record.uuid;
		current.reportBag(me, carried, ctx.now);
		if (Bags.freeStacks(carried, cfg.carryStacks) == 0 && hasJunk(ctx, current)) {
			depositing = true;
			depositStep(ctx);
			return;
		}
		Villager villager = ctx.villager;
		if (job == null) {
			job = current.claim(me, villager.blockPosition(), Bags.contents(carried), ctx.now, skipped::containsKey);
			fetchTarget = null;
			if (job == null) {
				ctx.status("villager.status.builder.all_taken", current.project.name);
				return;
			}
		}
		BuildSite.Job parcel = job;
		current.renew(parcel, me, ctx.now);
		Map<Item, Integer> bag = Bags.contents(carried);
		Map<Item, Integer> needs = current.needs(parcel, bag);
		if (!needs.isEmpty() && fetch(ctx, data, current, needs)) {
			return;
		}
		int cell = current.next(parcel, villager.blockPosition(), bag, skippedCells::containsKey);
		if (cell < 0) {
			// Parcel done (or what is left waits for materials nobody has, or was tried a moment ago): the next one.
			if (!needs.isEmpty() || current.next(parcel, villager.blockPosition(), bag, c -> false) >= 0) {
				skipped.put(parcel, ctx.now + SKIP_TICKS);
			}
			current.release(me);
			job = null;
			ctx.status("villager.status.builder.next_parcel", current.project.name);
			return;
		}
		BlockPos pos = current.pos(cell);
		double reach = cfg.reach;
		if (!Movement.near(ctx, pos, reach, reach)) {
			switch (Movement.moveTo(ctx, pos, Math.max(1.5, reach - 1.0), ctx.config.work.walkTimeoutSeconds)) {
				case MOVING -> {
					ctx.status("villager.status.builder.going", current.project.name, layerText(current, parcel));
					return;
				}
				case UNREACHABLE -> {
					skipped.put(parcel, ctx.now + SKIP_TICKS);
					current.release(me);
					job = null;
					ctx.status("villager.status.builder.unloaded", current.project.name);
					return;
				}
				default -> {
				}
			}
		}
		Movement.hold(ctx, pos);
		credit = Math.min(credit + cfg.blocksPerSecond * Upgrades.workRate(ctx.guild), MAX_ACTIONS_PER_STEP);
		int actions = 0;
		while (credit >= 1 && actions < MAX_ACTIONS_PER_STEP && cell >= 0) {
			credit -= 1;
			actions++;
			BuildPasses.Pass pass = current.pass(cell);
			showInHand(villager, current, cell);
			BuildSite.WorkResult result = current.work(ctx.level, cell, villager, carried);
			boolean stop = handle(ctx, data, current, cell, pass, result);
			if (stop) {
				break;
			}
			cell = current.next(parcel, villager.blockPosition(), Bags.contents(carried), skippedCells::containsKey);
			if (cell < 0 || !Movement.near(ctx, current.pos(cell), reach, reach)) {
				break;
			}
		}
		if (credit < 1 && actions == 0) {
			ctx.status(statusKey(current.pass(cell)), layerText(current, parcel), current.project.name);
		}
	}

	/** What a work result means for the builder; true stops this step's work. */
	private boolean handle(WorkContext ctx, BuilderData data, BuildSite current, int cell, BuildPasses.@Nullable Pass pass, BuildSite.WorkResult result) {
		Villager villager = ctx.villager;
		BlockPos pos = current.pos(cell);
		switch (result.outcome()) {
			case PLACED, FIXED -> {
				WorldUtil.swing(villager);
				if (result.outcome() == BuildSite.Outcome.PLACED) {
					data.placed++;
				}
				keepDrops(ctx, pos, result.drops());
				ctx.status(statusKey(pass), layerText(current, job), current.project.name);
				GuildManager.markDirty();
				return false;
			}
			case CLEARED, FILLED, PLUGGED -> {
				WorldUtil.swing(villager);
				if (result.outcome() == BuildSite.Outcome.CLEARED) {
					data.cleared++;
				}
				keepDrops(ctx, pos, result.drops());
				ctx.status("villager.status.builder.clear", layerText(current, job), current.project.name);
				GuildManager.markDirty();
				return false;
			}
			case ALREADY -> {
				return false;
			}
			case WAIT, BLOCKED, UNLOADED -> {
				// Not now (an entity in the cell, a support missing...): other cells first.
				skippedCells.put(cell, ctx.now + CELL_SKIP_TICKS);
				return false;
			}
			case NEED_ITEMS -> {
				current.invalidateStock();
				return true;
			}
			case SELF_INSIDE -> {
				stepAside(ctx, pos);
				return true;
			}
		}
		return false;
	}

	/** Debris of a dug cell: into the bag, what does not fit dropped on the ground (never lost). */
	private static void keepDrops(WorkContext ctx, BlockPos pos, List<ItemStack> drops) {
		int capacity = BuildModule.config().builder.carryStacks;
		for (ItemStack drop : drops) {
			ItemStack rest = Bags.add(ctx.record.carried, drop, capacity);
			if (!rest.isEmpty()) {
				Block.popResource(ctx.level, pos, rest);
			}
		}
	}

	/** Shows the block being placed (a shovel while digging); never dropped. */
	private static void showInHand(Villager villager, BuildSite site, int cell) {
		BlockState target = site.target(cell);
		Item item = target == null || target.isAir() ? Items.IRON_SHOVEL : target.getBlock().asItem();
		if (item == Items.AIR) {
			item = Items.BRICKS;
		}
		if (!villager.getMainHandItem().is(item)) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
			villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		}
	}

	/** Steps out of a cell it must fill (a safe spot around it, teleported with particles; else waits). */
	private static void stepAside(WorkContext ctx, BlockPos cell) {
		BlockPos spot = WorldUtil.findSafeSpot(ctx.level, cell, 3, true,
				p -> !p.equals(cell) && !p.equals(cell.above()) && !p.equals(cell.below()));
		ctx.status("villager.status.builder.step_aside");
		if (spot == null) {
			return;
		}
		Villager villager = ctx.villager;
		Movement.stop(ctx);
		WorldUtil.portal(ctx.level, villager.getX(), villager.getY(), villager.getZ());
		villager.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
	}

	private static String statusKey(BuildPasses.@Nullable Pass pass) {
		if (pass == null) {
			return "villager.status.builder.solid";
		}
		return switch (pass) {
			case CLEAR -> "villager.status.builder.clear";
			case SOLID -> "villager.status.builder.solid";
			case ATTACHED -> "villager.status.builder.attached";
			case FLUID -> "villager.status.builder.fluid";
		};
	}

	/** "7/24" (the layer of the parcel), "fondations -3" below the schematic. */
	private static String layerText(BuildSite site, BuildSite.@Nullable Job parcel) {
		if (parcel == null) {
			return "?";
		}
		int height = site.heightOf(parcel.layer());
		if (height < 0) {
			return dev.guildy.core.Lang.raw("villager.status.builder.foundation_layer", -height);
		}
		return (height + 1) + "/" + site.sizeY();
	}

	// ================================================================ materials

	/**
	 * Fetches what the parcel needs from the site's sources. True while busy with it (walking, taking); false when nothing
	 * more can be fetched (work goes on with what the bag holds).
	 */
	private boolean fetch(WorkContext ctx, BuilderData data, BuildSite current, Map<Item, Integer> needs) {
		Map<Item, Integer> stock = current.stock();
		Map<Item, Integer> available = new HashMap<>();
		needs.forEach((item, count) -> {
			int there = stock.getOrDefault(item, 0);
			if (there > 0) {
				available.put(item, Math.min(count, there));
			}
		});
		if (available.isEmpty()) {
			fetchTarget = null;
			return false;
		}
		int capacity = BuildModule.config().builder.carryStacks;
		boolean room = false;
		for (Item item : available.keySet()) {
			if (Bags.room(ctx.record.carried, item, capacity) > 0) {
				room = true;
				break;
			}
		}
		if (!room) {
			if (hasJunk(ctx, current)) {
				depositing = true;
				depositStep(ctx);
				return true;
			}
			fetchTarget = null;
			return false;
		}
		BuildProject.Sources sources = current.project.sourcesMode();
		if (fetchTarget == null) {
			fetchTarget = BuildStock.nearestWith(ctx, sources, available.keySet(), emptySources.keySet());
			if (fetchTarget == null) {
				current.invalidateStock();
				return false;
			}
		}
		BlockPos target = fetchTarget;
		Map.Entry<Item, Integer> first = available.entrySet().iterator().next();
		switch (Movement.moveTo(ctx, target, 2.5, ctx.config.work.walkTimeoutSeconds)) {
			case MOVING -> {
				ctx.status("villager.status.builder.fetch", first.getValue(), ItemUtil.name(first.getKey()).getString(), current.project.name);
				return true;
			}
			case UNREACHABLE -> {
				emptySources.put(target, ctx.now + SKIP_TICKS);
				fetchTarget = null;
				return true;
			}
			default -> {
			}
		}
		int moved = BuildStock.fetch(ctx, target, sources, available, capacity);
		fetchTarget = null;
		if (moved <= 0) {
			emptySources.put(target, ctx.now + SKIP_TICKS);
			current.invalidateStock();
			return true;
		}
		data.fetched += moved;
		Movement.hold(ctx, target);
		WorldUtil.swing(ctx.villager);
		WorldUtil.playSound(ctx.level, ctx.villager, SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 1.0F);
		current.reportBag(ctx.record.uuid, ctx.record.carried, ctx.now);
		current.invalidateStock();
		ctx.status("villager.status.builder.fetched", moved, current.project.name);
		GuildManager.markDirty();
		return true;
	}

	/** Something in the bag the site does not need (debris, leftovers of a cancelled site). */
	private static boolean hasJunk(WorkContext ctx, @Nullable BuildSite current) {
		Map<Item, Integer> remaining = current == null || !current.ready() ? Map.of() : current.remainingMaterials();
		for (ItemStack stack : ctx.record.carried) {
			if (!Bags.plain(stack) || !remaining.containsKey(stack.getItem())) {
				return true;
			}
		}
		return false;
	}

	/** Deposit trip of the debris. @return true when done (or given up), false while busy */
	private boolean depositStep(WorkContext ctx) {
		BuildSite current = site;
		Map<Item, Integer> remaining = current == null || !current.ready() || !current.project.active() ? Map.of() : current.remainingMaterials();
		Set<Item> keep = remaining.keySet();
		DepositFlow.State state = deposit.tick(ctx, stack -> Bags.plain(stack) && keep.contains(stack.getItem()));
		switch (state) {
			case DONE -> {
				depositing = false;
				return true;
			}
			case WORKING -> {
				ctx.status("villager.status.builder.deposit");
				return false;
			}
			default -> {
				if (BuildModule.config().terraform.discardWhenStorageFull) {
					Iterator<ItemStack> it = ctx.record.carried.iterator();
					int dropped = 0;
					while (it.hasNext()) {
						ItemStack stack = it.next();
						if (!(Bags.plain(stack) && keep.contains(stack.getItem())) && !keepAlways(stack)) {
							dropped += stack.getCount();
							it.remove();
						}
					}
					GuildManager.markDirty();
					depositing = false;
					ctx.status("villager.status.builder.discarded", dropped);
					return true;
				}
				ctx.status("villager.status.builder.bag_full");
				return false;
			}
		}
	}

	/** Never thrown away: anything with components (named, enchanted...), buckets. */
	private static boolean keepAlways(ItemStack stack) {
		return !Bags.plain(stack) || stack.is(Items.BUCKET) || stack.is(Items.WATER_BUCKET) || stack.is(Items.LAVA_BUCKET);
	}

	// ================================================================ release

	private void releaseSite(WorkContext ctx) {
		if (site != null) {
			site.release(ctx.record.uuid);
		}
		site = null;
		job = null;
		fetchTarget = null;
		credit = 0;
	}

	private void stopGather(WorkContext ctx) {
		if (gather != null) {
			gather.stop(ctx);
			gather = null;
		}
	}

	// ================================================================ chunk loading

	/** Site of a working builder for the chunk loader: {x, y, z, radius in chunks}, null when none. */
	static int @Nullable [] siteAnchor(Guild guild, VillagerRecord record, int maxRadius) {
		Builders.Note note = Builders.noteOf(guild, record.uuid);
		if (note == null || note.project() < 0) {
			return null;
		}
		BuildProject project = guild.builds.get(note.project());
		if (project == null || project.finished) {
			return null;
		}
		int[] box = project.box();
		int cx = (box[0] + box[3]) / 2;
		int cz = (box[2] + box[5]) / 2;
		int half = Math.max(box[3] - box[0], box[5] - box[2]) / 2;
		int radius = Math.min(maxRadius, (half + 15) / 16);
		return new int[] {cx, (box[1] + box[4]) / 2, cz, radius};
	}

}
