package dev.guildy.villager;

import dev.guildy.core.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Role "fetch the materials" of the builders: crafts, smelts or gathers what {@link Procurement} lists for the guild and
 * brings it to the storage. One instance per builder while it has this role; one trip at a time:
 * <ol>
 * <li>its bag is emptied into the storage if it holds anything (a former task, an interrupted trip);</li>
 * <li>it picks a job ({@link SupplyGuild#choose}): the cheapest route of the most wanted item that the other gatherers
 * do not already cover (crafted from the storage, else gathered itself, else an ingredient gathered first), reserved
 * with {@link Procurement#fetching};</li>
 * <li>it does it: {@link SupplyCrafter} (fetch the inputs, craft / cut / smelt / transform), or gathering: a pit
 * ({@link SupplyPits} survey then {@link SupplyDigger}) or a tree ({@link SupplyTrees});</li>
 * <li>it brings everything to the storage, each stack to the chest of its category (else a fallback, else a deposit
 * chest), and reports the wanted items delivered ({@link Procurement#delivered}).</li>
 * </ol>
 * The trip is then {@link #finished()}: the builder chooses its role again (a new trip starts if it ticks this instance
 * again). Status line: "↪ Matériaux : 64 Verre → cuit le Sable (12/64)".
 */
final class GatherWork {
	private enum Phase {
		DECIDE,
		STOW,
		CRAFT,
		SURVEY,
		DIG,
		TREE,
		DELIVER
	}

	/** Pits tried in one trip before it gives up (a pit that turns out to hold nothing more). */
	private static final int MAX_SURVEYS = 3;
	private static final long RETRY_TICKS = 10 * 20L;
	private static final int MAX_DELIVERY_FAILURES = 3;

	private Phase phase = Phase.DECIDE;
	private boolean finished;
	private SupplyGuild.@Nullable Job job;
	private String jobText = "";
	private @Nullable SupplyCrafter crafter;
	private SupplyPits.@Nullable Survey survey;
	private int surveys;
	private @Nullable SupplyDigger digger;
	private @Nullable SupplyTrees trees;
	/** Stow phase: what to do once the bag is empty (DECIDE, or end of the trip). */
	private boolean stowThenFinish;
	private @Nullable BlockPos deliverTo;
	private final Set<BlockPos> full = new HashSet<>();
	private int deliveryFailures;
	private long deliveryRetryAt;
	private long retryAt;
	/** Shown while waiting before a new decision after a trip given up. */
	private String waitStatus = "";
	private int delivered;
	private String endStatus = "";

	/**
	 * Null when this villager can fetch something useful now, otherwise the reason (shown in its menu): a lang key or a text
	 * already formatted (both show with {@code Lang.raw} / {@code Lang.get}, an unknown key is returned as it is).
	 */
	static @Nullable String blocked(WorkContext ctx) {
		if (!Supply.config().enabled) {
			return "villager.gather.disabled";
		}
		if (ctx.guild.claims().isEmpty()) {
			return "villager.gather.no_territory";
		}
		if (ctx.guild.villagers.chests.isEmpty()) {
			// Nowhere to take the ingredients from nor to bring the materials to.
			return "villager.gather.no_storage";
		}
		String[] reason = {null};
		SupplyGuild.Job job = SupplyGuild.of(ctx.guild).choose(ctx, false, reason);
		return job != null ? null : reason[0] == null ? "villager.gather.unavailable" : reason[0];
	}

	/** One step, at most once per second. */
	void tick(WorkContext ctx) {
		if (finished) {
			// Ticked again after the end of a trip: the next one starts.
			reset(ctx);
		}
		SupplyGuild.Job current = job;
		if (current != null) {
			Procurement.fetching(ctx.guild, ctx.record.uuid, current.target(), current.amount());
		}
		switch (phase) {
			case DECIDE -> decide(ctx);
			case STOW, DELIVER -> deliver(ctx);
			case CRAFT -> craft(ctx);
			case SURVEY -> survey(ctx);
			case DIG -> dig(ctx);
			case TREE -> tree(ctx);
		}
	}

	/** True when the current trip is over (nothing more to fetch, or everything delivered): the villager may switch role. */
	boolean finished() {
		return finished;
	}

	/** Releases what it holds (reservations...) when the villager changes role or task. */
	void stop(WorkContext ctx) {
		SupplyCrafter running = crafter;
		if (running != null) {
			running.stop(ctx);
		}
		SupplyGuild.of(ctx.guild).leave(ctx);
		Item hand = Task.BUILDER.handItem();
		ctx.villager.setItemSlot(EquipmentSlot.MAINHAND, hand == null ? ItemStack.EMPTY : new ItemStack(hand));
	}

	private void reset(WorkContext ctx) {
		SupplyGuild.of(ctx.guild).leave(ctx);
		phase = Phase.DECIDE;
		finished = false;
		job = null;
		jobText = "";
		crafter = null;
		survey = null;
		surveys = 0;
		digger = null;
		trees = null;
		stowThenFinish = false;
		deliverTo = null;
		full.clear();
		deliveryFailures = 0;
		deliveryRetryAt = 0;
		delivered = 0;
		endStatus = "";
	}

	/** Ends the trip: reservations freed, the builder chooses its role again. */
	private void end(WorkContext ctx, String status) {
		SupplyCrafter running = crafter;
		if (running != null) {
			running.stop(ctx);
		}
		SupplyGuild.of(ctx.guild).leave(ctx);
		job = null;
		finished = true;
		if (!status.isEmpty()) {
			ctx.status(status);
		}
	}

	// ------------------------------------------------------------------ decision

	private void decide(WorkContext ctx) {
		if (!ctx.record.carried.isEmpty()) {
			// Something in the bag (a former task, an interrupted trip): to the storage first.
			stowThenFinish = false;
			phase = Phase.STOW;
			deliver(ctx);
			return;
		}
		if (ctx.now < retryAt) {
			ctx.status(waitStatus.isEmpty() ? "villager.gather.unavailable" : waitStatus);
			finished = true;
			return;
		}
		SupplyGuild shared = SupplyGuild.of(ctx.guild);
		shared.scanStations(ctx);
		if (!shared.stationsKnown()) {
			// The workstations of the territory first: never a trip for a furnace that does not exist.
			int[] scan = shared.scanProgress();
			ctx.status("villager.gather.scanning", scan == null ? 0 : scan[0], scan == null ? 1 : scan[1]);
			return;
		}
		String[] reason = {null};
		SupplyGuild.Job chosen = SupplyGuild.of(ctx.guild).choose(ctx, true, reason);
		if (chosen == null) {
			end(ctx, reason[0] == null ? "villager.gather.unavailable" : reason[0]);
			return;
		}
		job = chosen;
		jobText = SupplyLang.amount(chosen.amount(), chosen.target());
		if (chosen.crafts()) {
			crafter = new SupplyCrafter(chosen);
			phase = Phase.CRAFT;
			craft(ctx);
			return;
		}
		Item gather = chosen.gather();
		if (gather == null) {
			end(ctx, "villager.gather.unavailable");
			return;
		}
		if (chosen.method() == SupplySources.Method.TREE && SupplyTrees.natural(gather)) {
			trees = new SupplyTrees(gather, chosen.gatherAmount());
			phase = Phase.TREE;
			tree(ctx);
		} else {
			startSurvey(ctx, gather);
		}
	}

	private void startSurvey(WorkContext ctx, Item gather) {
		SupplyGuild.Job current = job;
		Set<String> blocks = SupplyBook.sources(ctx.level).blocks(dev.guildy.core.ItemUtil.id(gather), SupplySources.Method.DIG);
		survey = new SupplyPits.Survey(SupplyPits.blocks(blocks), current == null ? 64 : current.gatherAmount());
		surveys++;
		phase = Phase.SURVEY;
		survey(ctx);
	}

	// ------------------------------------------------------------------ crafting

	private void craft(WorkContext ctx) {
		SupplyCrafter running = crafter;
		if (running == null) {
			phase = Phase.DELIVER;
			return;
		}
		switch (running.tick(ctx, jobText)) {
			case WORKING -> {
			}
			case DONE -> {
				phase = Phase.DELIVER;
				stowThenFinish = true;
			}
			case FAILED -> {
				endStatus = Lang.raw("villager.gather.failed", jobText, running.failure());
				waitStatus = endStatus;
				retryAt = ctx.now + RETRY_TICKS;
				SupplyGuild.of(ctx.guild).invalidateStock();
				phase = Phase.DELIVER;
				stowThenFinish = true;
			}
		}
	}

	// ------------------------------------------------------------------ gathering

	private void survey(WorkContext ctx) {
		SupplyPits.Survey running = survey;
		SupplyGuild.Job current = job;
		if (running == null || current == null || current.gather() == null) {
			phase = Phase.DELIVER;
			stowThenFinish = true;
			return;
		}
		Optional<SupplyPits.Found> result = running.step(ctx);
		if (result == null) {
			ctx.status("villager.gather.job", jobText, Lang.raw("supply.action.survey", SupplyLang.name(current.gather()), running.seen()));
			return;
		}
		survey = null;
		if (result.isEmpty()) {
			SupplyGuild shared = SupplyGuild.of(ctx.guild);
			if (CraftStock.countInBag(ctx, current.gather()) <= 0) {
				shared.noSite(current.gather(), SupplySources.Method.DIG, ctx.now);
				endStatus = shared.noSiteText(current.gather());
			}
			phase = Phase.DELIVER;
			stowThenFinish = true;
			deliver(ctx);
			return;
		}
		SupplyPits.Found found = result.get();
		digger = new SupplyDigger(found.pit(), found.world(), current.gather(), current.gatherAmount());
		phase = Phase.DIG;
		dig(ctx);
	}

	private void dig(WorkContext ctx) {
		SupplyDigger running = digger;
		SupplyGuild.Job current = job;
		if (running == null || current == null) {
			phase = Phase.DELIVER;
			stowThenFinish = true;
			return;
		}
		switch (running.tick(ctx, jobText)) {
			case WORKING -> {
			}
			case DONE -> {
				phase = Phase.DELIVER;
				stowThenFinish = true;
			}
			case EXHAUSTED -> {
				digger = null;
				if (running.progress(ctx) <= 0 && surveys < MAX_SURVEYS && current.gather() != null) {
					startSurvey(ctx, current.gather());
				} else {
					phase = Phase.DELIVER;
					stowThenFinish = true;
				}
			}
		}
	}

	private void tree(WorkContext ctx) {
		SupplyTrees running = trees;
		SupplyGuild.Job current = job;
		if (running == null || current == null || current.gather() == null) {
			phase = Phase.DELIVER;
			stowThenFinish = true;
			return;
		}
		switch (running.tick(ctx, jobText)) {
			case WORKING -> {
			}
			case DONE -> {
				phase = Phase.DELIVER;
				stowThenFinish = true;
			}
			case NONE -> {
				SupplyGuild shared = SupplyGuild.of(ctx.guild);
				shared.noSite(current.gather(), SupplySources.Method.TREE, ctx.now);
				endStatus = shared.noSiteText(current.gather());
				phase = Phase.DELIVER;
				stowThenFinish = true;
				deliver(ctx);
			}
		}
	}

	// ------------------------------------------------------------------ delivery

	/**
	 * Brings the bag to the storage, each stack to the chest of its category (else a fallback, else a deposit chest), and
	 * reports the wanted items delivered. Then a new decision (stow before a trip) or the end of the trip.
	 */
	private void deliver(WorkContext ctx) {
		if (ctx.record.carried.isEmpty()) {
			deliverTo = null;
			finishDelivery(ctx);
			return;
		}
		if (ctx.now < deliveryRetryAt) {
			ctx.status(endStatus);
			return;
		}
		ItemStack first = null;
		if (deliverTo == null || ctx.guild.villagers.chestAt(deliverTo) == null) {
			StorageChest chest = null;
			for (ItemStack stack : ctx.record.carried) {
				chest = Storage.route(ctx, stack, full, true);
				if (chest != null) {
					first = stack;
					break;
				}
			}
			if (chest == null) {
				deliveryFailures++;
				full.clear();
				deliveryRetryAt = ctx.now + RETRY_TICKS;
				endStatus = ctx.guild.villagers.chests.isEmpty() ? Lang.raw("villager.gather.no_storage")
						: Lang.raw("villager.gather.storage_full", SupplyLang.name(ctx.record.carried.getFirst().getItem()));
				ctx.status(endStatus);
				if (deliveryFailures >= MAX_DELIVERY_FAILURES) {
					// The items stay in the bag; the next trip tries again.
					end(ctx, endStatus);
				}
				return;
			}
			deliverTo = chest.pos;
		}
		BlockPos target = deliverTo;
		String what = first != null ? SupplyLang.amount(first.getCount(), first.getItem()) : SupplyLang.name(ctx.record.carried.getFirst().getItem());
		String action = Lang.raw(phase == Phase.STOW ? "supply.action.stow" : "supply.action.deliver", what);
		switch (Movement.moveTo(ctx, target, 2.5, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> {
				full.add(target);
				deliverTo = null;
			}
			case MOVING -> ctx.status("villager.gather.job", jobText.isEmpty() ? Lang.raw("villager.task.builder") : jobText, action);
			case ARRIVED -> {
				Container container = Storage.container(ctx.level, target);
				StorageChest chest = Storage.registered(ctx.level, ctx.guild, target);
				int moved = 0;
				if (container != null && chest != null) {
					moved = insertRouted(ctx, container, target);
				}
				if (moved > 0) {
					Movement.hold(ctx, target);
					WorldUtil.swing(ctx.villager);
					WorldUtil.playSound(ctx.level, ctx.villager, SoundEvents.BUNDLE_INSERT, 0.8f, 1.0f);
					SupplyGuild.of(ctx.guild).invalidateStock();
					ctx.status("villager.gather.job", jobText.isEmpty() ? Lang.raw("villager.task.builder") : jobText, action);
				} else {
					full.add(target);
				}
				deliverTo = null;
			}
		}
	}

	/**
	 * Inserts every carried stack whose destination is this chest (the routing's choice for it), counting the wanted
	 * items delivered.
	 */
	private int insertRouted(WorkContext ctx, Container container, BlockPos target) {
		Map<Item, Integer> wanted = Procurement.wanted(ctx.guild);
		int moved = 0;
		java.util.List<ItemStack> leftovers = new java.util.ArrayList<>();
		java.util.Iterator<ItemStack> it = ctx.record.carried.iterator();
		while (it.hasNext()) {
			ItemStack stack = it.next();
			StorageChest route = Storage.route(ctx, stack, full, true);
			if (route == null || !route.pos.equals(target)) {
				continue;
			}
			ItemStack leftover = Storage.insert(container, stack);
			int count = stack.getCount() - leftover.getCount();
			if (count > 0) {
				moved += count;
				Integer want = wanted.get(stack.getItem());
				if (want != null && want > 0) {
					Procurement.delivered(ctx.guild, stack.getItem(), Math.min(want, count));
					wanted.put(stack.getItem(), want - Math.min(want, count));
					SupplyGuild.of(ctx.guild).invalidateAnalysis();
					SupplyGuild.Job current = job;
					if (current != null && current.target() == stack.getItem()) {
						delivered += count;
					}
				}
			}
			it.remove();
			if (!leftover.isEmpty()) {
				leftovers.add(leftover);
			}
		}
		ctx.record.carried.addAll(leftovers);
		if (moved > 0) {
			dev.guildy.data.GuildManager.markDirty();
		}
		return moved;
	}

	private void finishDelivery(WorkContext ctx) {
		SupplyGuild.Job current = job;
		if (phase == Phase.STOW && !stowThenFinish) {
			phase = Phase.DECIDE;
			decide(ctx);
			return;
		}
		String status = endStatus;
		if (status.isEmpty() && current != null) {
			status = delivered > 0 ? Lang.raw("villager.gather.delivered", SupplyLang.amount(delivered, current.target()))
					: Lang.raw("villager.gather.delivered", jobText);
		}
		end(ctx, status);
	}
}
