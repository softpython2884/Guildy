package dev.guildy.villager;

import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.GuildManager;
import dev.guildy.rune.RuneConfig;
import dev.guildy.rune.RuneForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "Forgeron": finds the armor stands of the territory holding damaged equipment (4 armor slots and both hands;
 * unbreakable items are left alone), walks to the anvil nearest to the stand (within {@code blacksmith.anvilRadius}
 * blocks, in the territory) and repairs the most damaged item step by step. The item never leaves the stand: its
 * damage value is lowered in place, so nothing can be duplicated or lost. Each step is one vanilla anvil use (wear
 * chance, anvil → chipped → damaged → destroyed, vanilla level events); a destroyed anvil is replaced by the next one.
 * Incremental search over the loaded chunks of the territory ({@link TerritoryScan}), repeated every
 * {@code blacksmith.scanIntervalSeconds}; two blacksmiths never work on the same stand (transient leases).
 * <p>
 * Runes ({@link BlacksmithRunes}): an armor piece repaired up to its full durability is one chance to inscribe a rune on
 * it; with nothing to repair, the blacksmith inspects the stands whose armor may receive a rune (once per
 * {@code inspectionMinutes} each) at their anvil, one more chance each, after fetching the rune material from the storage.
 */
final class BlacksmithTask extends TaskHandler {
	private enum Phase {
		SCAN,
		SELECT,
		FETCH,
		GO,
		WORK
	}

	/** A stand to repair (most damaged first), or to inspect for a rune (after every repair). */
	private record Candidate(UUID stand, BlockPos pos, double ratio, boolean inspect) {
	}

	private record Lease(UUID villager, long until) {
	}

	private static final int CHUNKS_PER_STEP = 4;
	private static final int SELECT_PER_STEP = 4;
	/** A lease not renewed for this long (game ticks) is free again. */
	private static final long LEASE_TICKS = 100;
	private static final long DEPOSIT_RETRY_TICKS = 60 * 20L;
	/** No rune material in the storage: inspections wait this long before looking again. */
	private static final long MATERIAL_RETRY_TICKS = 60 * 20L;
	/** Stand UUID → blacksmith working on it. */
	private static final Map<UUID, Lease> LEASES = new HashMap<>();

	private Phase phase = Phase.SCAN;
	private @Nullable TerritoryScan scan;
	private final List<Candidate> candidates = new ArrayList<>();
	private boolean anyWithoutAnvil;
	private long nextScan;
	private @Nullable String idleKey;
	private @Nullable UUID stand;
	private @Nullable BlockPos anvil;
	/** The current stand is inspected for a rune (not repaired). */
	private boolean inspecting;
	private long progress;
	private final DepositFlow deposit = new DepositFlow();
	private long depositRetryAt;
	/** Runes possible for the scan in progress (level, daily cap). */
	private boolean scanRunes;
	/** Inspections skipped because the storage has no rune material. */
	private boolean noMaterial;
	private long materialMissingUntil;
	private @Nullable BlockPos fetchFrom;
	private final Set<BlockPos> fetchSkip = new HashSet<>();

	@Override
	void tick(WorkContext ctx) {
		if (!CraftJobs.checkLevel(ctx)) {
			release(ctx);
			phase = Phase.SCAN;
			return;
		}
		if (phase != Phase.WORK && phase != Phase.FETCH && !ctx.record.carried.isEmpty() && ctx.now >= depositRetryAt) {
			// Items of a former task: brought to a deposit chest first (only the rune material stays in the bag).
			DepositFlow.State state = deposit.tick(ctx, BlacksmithRunes::isMaterial);
			if (state == DepositFlow.State.WORKING) {
				return;
			}
			if (state != DepositFlow.State.DONE) {
				depositRetryAt = ctx.now + DEPOSIT_RETRY_TICKS;
			}
		}
		switch (phase) {
			case SCAN -> scanStep(ctx);
			case SELECT -> selectStep(ctx);
			case FETCH -> fetchStep(ctx);
			case GO -> goStep(ctx);
			case WORK -> workStep(ctx);
		}
	}

	@Override
	void stop(WorkContext ctx) {
		release(ctx);
	}

	// ------------------------------------------------------------------ search

	private void scanStep(WorkContext ctx) {
		BlacksmithConfig cfg = ctx.config.blacksmith;
		TerritoryScan running = scan;
		if (running == null) {
			if (ctx.now < nextScan) {
				idle(ctx);
				return;
			}
			if (ctx.guild.claims().isEmpty()) {
				ctx.status("villager.status.no_territory");
				nextScan = ctx.now + WorkContext.ticks(cfg.scanIntervalSeconds);
				idleKey = "villager.status.no_territory";
				return;
			}
			running = new TerritoryScan(ctx.guild, ctx.villager.blockPosition());
			scan = running;
			candidates.clear();
			anyWithoutAnvil = false;
			noMaterial = false;
			fetchSkip.clear();
			scanRunes = BlacksmithRunes.canInscribe(ctx);
		}
		for (int i = 0; i < CHUNKS_PER_STEP; i++) {
			LevelChunk chunk = running.next(ctx);
			if (chunk == null) {
				break;
			}
			for (ArmorStand found : CraftWorld.stands(ctx.level, chunk)) {
				if (!ctx.inTerritory(found.blockPosition())) {
					continue;
				}
				if (CraftWorld.mostDamaged(found) != null) {
					candidates.add(new Candidate(found.getUUID(), found.blockPosition(), CraftWorld.worstRatio(found), false));
				} else if (scanRunes && BlacksmithRunes.inspectable(ctx, found)) {
					candidates.add(new Candidate(found.getUUID(), found.blockPosition(), 1.0, true));
				}
			}
		}
		if (!running.done()) {
			ctx.status("villager.status.smith.scanning", running.position(), running.total());
			return;
		}
		scan = null;
		nextScan = ctx.now + WorkContext.ticks(cfg.scanIntervalSeconds);
		if (running.readChunks() == 0 && running.unloadedChunks() > 0) {
			idleKey = "villager.status.craft.unloaded";
			idle(ctx);
			return;
		}
		// The repairs first (most damaged items first, then the nearest stands), then the inspections (nearest first).
		candidates.sort(Comparator.comparing(Candidate::inspect).thenComparingDouble(Candidate::ratio)
				.thenComparingDouble(c -> ctx.distSq(c.pos())));
		phase = Phase.SELECT;
		selectStep(ctx);
	}

	private void selectStep(WorkContext ctx) {
		int radius = ctx.config.blacksmith.anvilRadius;
		for (int i = 0; i < SELECT_PER_STEP; i++) {
			if (candidates.isEmpty()) {
				idleKey = anyWithoutAnvil ? "villager.status.smith.no_anvil"
						: noMaterial ? "villager.status.smith.no_material" : "villager.status.smith.nothing";
				phase = Phase.SCAN;
				idle(ctx);
				return;
			}
			Candidate candidate = candidates.removeFirst();
			ArmorStand found = stand(ctx, candidate.stand());
			if (found == null || leasedByOther(ctx, candidate.stand())) {
				continue;
			}
			if (candidate.inspect()) {
				if (!BlacksmithRunes.canInscribe(ctx) || !BlacksmithRunes.inspectable(ctx, found)) {
					continue;
				}
				if (!BlacksmithRunes.hasMaterial(ctx)) {
					BlockPos chest = ctx.now < materialMissingUntil ? null : materialChest(ctx);
					if (chest == null) {
						noMaterial = true;
						continue;
					}
					// Material first, then this stand.
					candidates.addFirst(candidate);
					fetchFrom = chest;
					phase = Phase.FETCH;
					fetchStep(ctx);
					return;
				}
			} else if (CraftWorld.mostDamaged(found) == null) {
				continue;
			}
			BlockPos nearest = CraftWorld.nearestAnvil(ctx.level, ctx.guild, found.blockPosition(), radius);
			if (nearest == null) {
				anyWithoutAnvil = true;
				continue;
			}
			stand = candidate.stand();
			anvil = nearest;
			inspecting = candidate.inspect();
			progress = 0;
			lease(ctx);
			phase = Phase.GO;
			goStep(ctx);
			return;
		}
	}

	private void idle(WorkContext ctx) {
		String key = idleKey == null ? "villager.status.smith.nothing" : idleKey;
		if (key.equals("villager.status.smith.no_anvil")) {
			ctx.status(key, ctx.config.blacksmith.anvilRadius);
		} else if (key.equals("villager.status.smith.no_material")) {
			Item material = BlacksmithRunes.material();
			ctx.status(key, material == null ? "?" : CraftNames.item(new ItemStack(material)));
		} else {
			ctx.status(key);
		}
	}

	// ------------------------------------------------------------------ rune material

	/** Nearest storage chest giving the rune material, or null (then no new look for a minute). */
	private @Nullable BlockPos materialChest(WorkContext ctx) {
		Item material = BlacksmithRunes.material();
		BlockPos chest = material == null ? null : CraftStock.nearestWith(ctx, material, fetchSkip);
		if (chest == null) {
			materialMissingUntil = ctx.now + MATERIAL_RETRY_TICKS;
		}
		return chest;
	}

	private void fetchStep(WorkContext ctx) {
		BlockPos from = fetchFrom;
		Item material = BlacksmithRunes.material();
		if (from == null || material == null) {
			fetchFrom = null;
			phase = Phase.SELECT;
			return;
		}
		switch (Movement.moveTo(ctx, from, 2.5, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> {
				fetchSkip.add(from);
				fetchFrom = null;
				phase = Phase.SELECT;
			}
			case MOVING -> ctx.status("villager.status.smith.fetch_material", CraftNames.item(new ItemStack(material)));
			case ARRIVED -> {
				int moved = CraftStock.fetch(ctx, from, material, BlacksmithRunes.wanted(ctx));
				if (moved > 0) {
					Movement.hold(ctx, from);
					WorldUtil.swing(ctx.villager);
					WorldUtil.playSound(ctx.level, ctx.villager, SoundEvents.ITEM_PICKUP, 0.6f, 0.9f);
				} else {
					fetchSkip.add(from);
				}
				fetchFrom = null;
				phase = Phase.SELECT;
			}
		}
	}

	// ------------------------------------------------------------------ work

	private void goStep(WorkContext ctx) {
		ArmorStand target = validTarget(ctx);
		if (target == null) {
			return;
		}
		BlockPos at = anvil;
		switch (Movement.moveTo(ctx, at, 2.5, ctx.config.work.walkTimeoutSeconds)) {
			case UNREACHABLE -> drop(ctx);
			case MOVING -> {
				lease(ctx);
				if (inspecting) {
					ctx.status("villager.status.smith.going_inspect");
				} else {
					EquipmentSlot slot = CraftWorld.mostDamaged(target);
					ctx.status("villager.status.smith.going", slot == null ? "?" : CraftNames.item(target.getItemBySlot(slot)));
				}
			}
			case ARRIVED -> {
				phase = Phase.WORK;
				workStep(ctx);
			}
		}
	}

	private void workStep(WorkContext ctx) {
		ArmorStand target = validTarget(ctx);
		if (target == null) {
			return;
		}
		BlockPos at = anvil;
		if (phase != Phase.WORK || !Movement.near(ctx, at, 3.5, 3.0)) {
			phase = Phase.GO;
			goStep(ctx);
			return;
		}
		EquipmentSlot slot = inspecting ? null : CraftWorld.mostDamaged(target);
		if (!inspecting && slot == null) {
			finished(ctx);
			return;
		}
		lease(ctx);
		Movement.hold(ctx, at);
		ServerLevel level = ctx.level;
		WorldUtil.swing(ctx.villager);
		level.sendParticles(ParticleTypes.CRIT, at.getX() + 0.5, at.getY() + 1.05, at.getZ() + 0.5, 4, 0.18, 0.05, 0.18, 0.15);
		if (ctx.now % 40 < 20) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.getX() + 0.5, at.getY() + 1.1, at.getZ() + 0.5, 3, 0.15, 0.05, 0.15, 0.2);
		}
		progress += 20;
		if (inspecting) {
			// Glyphs drift from the stand to the anvil while the blacksmith studies the armor.
			level.sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 6, 0.3, 0.4, 0.3, 0.6);
			long needed = ctx.workTicks(RuneForge.settings().secondsPerInspection);
			if (progress >= needed) {
				progress = 0;
				inspect(ctx, target);
				return;
			}
			ctx.status("villager.status.smith.inspecting", Math.min(99, progress * 100 / Math.max(1, needed)), anvilName(ctx));
			return;
		}
		ItemStack item = target.getItemBySlot(slot);
		if (progress >= ctx.workTicks(ctx.config.blacksmith.secondsPerRepair)) {
			progress = 0;
			repair(ctx, target, slot, item);
			return;
		}
		status(ctx, item);
	}

	/** One repair step: durability given back in place on the stand, then one vanilla anvil use. */
	private void repair(WorkContext ctx, ArmorStand target, EquipmentSlot slot, ItemStack item) {
		BlacksmithConfig cfg = ctx.config.blacksmith;
		CraftData data = CraftJobs.data(ctx.record);
		int before = item.getDamageValue();
		int after = ForgeRules.damageAfterRepair(before, item.getMaxDamage(), cfg.repairPercent);
		item.setDamageValue(after);
		data.durability += before - after;
		data.repairSteps++;
		ServerLevel level = ctx.level;
		double y = target.getY() + CraftWorld.slotHeight(target, slot);
		level.sendParticles(ParticleTypes.WAX_ON, target.getX(), y, target.getZ(), 6, 0.25, 0.2, 0.25, 0.0);
		boolean done = after == 0;
		if (done) {
			data.repaired++;
			WorldUtil.happy(level, target);
		}
		GuildManager.markDirty();
		useAnvil(ctx);
		if (done) {
			ctx.status("villager.status.smith.repaired", CraftNames.item(item));
			if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
				// A piece repaired up to the end: one chance for a rune.
				inscribedStatus(ctx, target, BlacksmithRunes.attempt(ctx, target, slot));
			}
		} else if (anvil != null) {
			status(ctx, item);
		}
	}

	/** End of an inspection: one chance for a rune on a piece of the stand. */
	private void inspect(WorkContext ctx, ArmorStand target) {
		BlacksmithRunes.Inscribed inscribed = BlacksmithRunes.attempt(ctx, target, null);
		if (inscribed == null) {
			ctx.status("villager.status.smith.no_rune");
		}
		inscribedStatus(ctx, target, inscribed);
		finished(ctx);
	}

	private static void inscribedStatus(WorkContext ctx, ArmorStand target, BlacksmithRunes.@Nullable Inscribed inscribed) {
		if (inscribed != null) {
			RuneConfig.RuneDef rune = inscribed.rune();
			ctx.status("villager.status.smith.inscribed", rune.tier().color + rune.name, CraftNames.item(target.getItemBySlot(inscribed.slot())));
		}
	}

	/** Vanilla anvil use (AnvilMenu.onTake): wear chance, chipped → damaged → destroyed, with the vanilla sounds. */
	private void useAnvil(WorkContext ctx) {
		BlockPos at = anvil;
		if (at == null) {
			return;
		}
		ServerLevel level = ctx.level;
		if (!CraftWorld.isAnvil(level, at)) {
			anvil = null;
			return;
		}
		BlockState state = level.getBlockState(at);
		if (!ForgeRules.wears(level.getRandom().nextFloat(), ctx.config.blacksmith.anvilDamageChance)) {
			level.levelEvent(LevelEvent.SOUND_ANVIL_USED, at, 0);
			return;
		}
		BlockState worn = AnvilBlock.damage(state);
		if (worn != null) {
			level.setBlock(at, worn, Block.UPDATE_CLIENTS);
			level.levelEvent(LevelEvent.SOUND_ANVIL_USED, at, 0);
			return;
		}
		level.removeBlock(at, false);
		level.levelEvent(LevelEvent.SOUND_ANVIL_BROKEN, at, 0);
		CraftData data = CraftJobs.data(ctx.record);
		data.anvilsWorn++;
		GuildManager.markDirty();
		Msg.guild(ctx.guild, "villager.smith.anvil_destroyed", at.getX(), at.getY(), at.getZ(), ctx.record.name);
		// Next anvil for the same stand, else the next stand.
		ArmorStand target = stand == null ? null : stand(ctx, stand);
		BlockPos next = target == null ? null : CraftWorld.nearestAnvil(level, ctx.guild, target.blockPosition(), ctx.config.blacksmith.anvilRadius);
		ctx.status("villager.status.smith.anvil_broken");
		if (next == null) {
			anyWithoutAnvil = true;
			anvil = null;
			release(ctx);
			stand = null;
			phase = Phase.SELECT;
		} else {
			anvil = next;
			phase = Phase.GO;
		}
	}

	private String anvilName(WorkContext ctx) {
		BlockPos at = anvil;
		return at == null ? "?" : Lang.raw("villager.smith.anvil." + switch (CraftWorld.stage(ctx.level.getBlockState(at))) {
			case INTACT -> "intact";
			case CHIPPED -> "chipped";
			default -> "damaged";
		});
	}

	private void status(WorkContext ctx, ItemStack item) {
		ctx.status("villager.status.smith.repairing", CraftNames.item(item),
				ForgeRules.durabilityPercent(item.getDamageValue(), item.getMaxDamage()), anvilName(ctx));
	}

	/** Stand finished: the next candidate (or a new search once they are all done). */
	private void finished(WorkContext ctx) {
		release(ctx);
		stand = null;
		anvil = null;
		inspecting = false;
		progress = 0;
		phase = Phase.SELECT;
		if (candidates.isEmpty()) {
			// Something else may have been damaged meanwhile: look again soon.
			nextScan = Math.min(nextScan, ctx.now + WorkContext.ticks(5));
		}
	}

	/** The current stand (alive, loaded, in the territory, still with work to do) and anvil, or null (target dropped). */
	private @Nullable ArmorStand validTarget(WorkContext ctx) {
		UUID id = stand;
		BlockPos at = anvil;
		ArmorStand target = id == null ? null : stand(ctx, id);
		boolean work = target != null && (inspecting
				? BlacksmithRunes.canInscribe(ctx) && BlacksmithRunes.inspectable(ctx, target)
				: CraftWorld.mostDamaged(target) != null);
		if (!work) {
			if (target != null) {
				finished(ctx);
			} else {
				drop(ctx);
			}
			return null;
		}
		int radius = ctx.config.blacksmith.anvilRadius;
		if (at == null || !CraftWorld.isAnvil(ctx.level, at) || !ctx.inTerritory(at) || at.distSqr(target.blockPosition()) > (double) radius * radius) {
			BlockPos next = CraftWorld.nearestAnvil(ctx.level, ctx.guild, target.blockPosition(), radius);
			if (next == null) {
				anyWithoutAnvil = true;
				drop(ctx);
				return null;
			}
			anvil = next;
			phase = Phase.GO;
		}
		return target;
	}

	private void drop(WorkContext ctx) {
		release(ctx);
		stand = null;
		anvil = null;
		inspecting = false;
		progress = 0;
		phase = Phase.SELECT;
	}

	private static @Nullable ArmorStand stand(WorkContext ctx, UUID id) {
		Entity entity = ctx.level.getEntity(id);
		if (!(entity instanceof ArmorStand found) || !found.isAlive() || !ctx.ticking(found.blockPosition()) || !ctx.inTerritory(found.blockPosition())) {
			return null;
		}
		return found;
	}

	// ------------------------------------------------------------------ leases

	private boolean leasedByOther(WorkContext ctx, UUID id) {
		Lease lease = LEASES.get(id);
		return lease != null && !lease.villager().equals(ctx.record.uuid) && lease.until() > ctx.level.getGameTime();
	}

	private void lease(WorkContext ctx) {
		if (stand != null) {
			LEASES.put(stand, new Lease(ctx.record.uuid, ctx.level.getGameTime() + LEASE_TICKS));
		}
		if (LEASES.size() > 256) {
			long now = ctx.level.getGameTime();
			LEASES.values().removeIf(lease -> lease.until() <= now);
		}
	}

	private void release(WorkContext ctx) {
		if (stand != null) {
			Lease lease = LEASES.get(stand);
			if (lease != null && lease.villager().equals(ctx.record.uuid)) {
				LEASES.remove(stand);
			}
		}
	}
}
