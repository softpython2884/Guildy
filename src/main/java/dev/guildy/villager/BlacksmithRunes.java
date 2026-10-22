package dev.guildy.villager;

import dev.guildy.data.GuildManager;
import dev.guildy.rune.RuneConfig;
import dev.guildy.rune.RuneForge;
import dev.guildy.rune.RuneRules;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The blacksmith's runes (runes.json5, section inscription): each armor piece repaired up to its full durability, and each
 * inspection of an armor stand whose armor needs no repair, is one chance to inscribe a rune on a piece of that stand (in
 * place, like the repairs). Guild level, daily cap per blacksmith ({@link CraftData#runesToday}), one material per rune
 * carried from the storage; a stand is inspected (or rolled after a repair) at most once per {@code inspectionMinutes}
 * (transient memory, forgotten on restart).
 */
final class BlacksmithRunes {
	/** Stand UUID → game time of its last inspection or rune roll. */
	private static final Map<UUID, Long> INSPECTED = new HashMap<>();

	/** A rune inscribed on the piece in {@code slot} of the stand. */
	record Inscribed(RuneConfig.RuneDef rune, EquipmentSlot slot) {
	}

	private BlacksmithRunes() {
	}

	/** May this blacksmith inscribe runes now: runes on, guild level, daily cap not reached. */
	static boolean canInscribe(WorkContext ctx) {
		if (!RuneForge.active()) {
			return false;
		}
		RuneConfig.Inscription settings = RuneForge.settings();
		if (ctx.guild.level < settings.minGuildLevel) {
			return false;
		}
		CraftData data = ctx.record.craft;
		return data == null || RuneRules.underCap(data.runeDay, data.runesToday, RuneForge.day(ctx.level), settings.maxPerDay);
	}

	/** A stand worth an inspection: a piece that may receive a rune, not inspected recently. */
	static boolean inspectable(WorkContext ctx, ArmorStand stand) {
		return due(ctx, stand.getUUID()) && !RuneForge.receivers(stand).isEmpty();
	}

	private static boolean due(WorkContext ctx, UUID stand) {
		Long last = INSPECTED.get(stand);
		return RuneRules.inspectionDue(last == null ? -1 : last, ctx.level.getGameTime(), intervalTicks());
	}

	private static long intervalTicks() {
		return RuneForge.settings().inspectionMinutes * 1200L;
	}

	static void markInspected(WorkContext ctx, UUID stand) {
		long now = ctx.level.getGameTime();
		INSPECTED.put(stand, now);
		if (INSPECTED.size() > 1024) {
			long interval = intervalTicks();
			INSPECTED.values().removeIf(last -> now - last >= interval);
		}
	}

	/** The material of a rune (null = free). */
	static @Nullable Item material() {
		return RuneForge.material();
	}

	/** The bag holds the material of one rune (always true when runes are free). */
	static boolean hasMaterial(WorkContext ctx) {
		Item material = material();
		return material == null || CraftStock.countInBag(ctx, material) >= RuneForge.materialCount();
	}

	/** The rune material stays in the bag (never brought back to a deposit chest). */
	static boolean isMaterial(ItemStack stack) {
		Item material = material();
		return material != null && CraftStock.plain(stack, material);
	}

	/** Materials worth fetching: those of the runes the blacksmith may still inscribe today. */
	static int wanted(WorkContext ctx) {
		RuneConfig.Inscription settings = RuneForge.settings();
		CraftData data = ctx.record.craft;
		int made = data == null ? 0 : data.runesToday(RuneForge.day(ctx.level));
		return Math.max(1, settings.maxPerDay - made) * Math.max(1, RuneForge.materialCount());
	}

	/**
	 * One chance to inscribe a rune on the stand: on the piece in {@code slot} (just repaired), or on a random piece that may
	 * receive one (inspection). The stand counts as inspected either way.
	 *
	 * @return the rune inscribed and its piece, or null
	 */
	static @Nullable Inscribed attempt(WorkContext ctx, ArmorStand stand, @Nullable EquipmentSlot slot) {
		markInspected(ctx, stand.getUUID());
		if (!canInscribe(ctx) || !hasMaterial(ctx)) {
			return null;
		}
		List<EquipmentSlot> receivers = RuneForge.receivers(stand);
		RandomSource random = ctx.level.getRandom();
		EquipmentSlot chosen;
		if (slot != null) {
			chosen = receivers.contains(slot) ? slot : null;
		} else {
			chosen = receivers.isEmpty() ? null : receivers.get(random.nextInt(receivers.size()));
		}
		if (chosen == null || !RuneRules.succeeds(random.nextDouble(), RuneForge.settings().chance)) {
			return null;
		}
		RuneConfig.RuneDef def = RuneForge.roll(stand.getItemBySlot(chosen), random);
		if (def == null) {
			return null;
		}
		Item material = material();
		if (material != null && !CraftStock.takeFromBag(ctx, material, RuneForge.materialCount())) {
			return null;
		}
		if (!RuneForge.inscribe(ctx.level, ctx.guild, stand, chosen, def, ctx.record.name)) {
			return null;
		}
		CraftJobs.data(ctx.record).countRune(RuneForge.day(ctx.level), def.id);
		GuildManager.markDirty();
		return new Inscribed(def, chosen);
	}

	/** Forgets the inspections (tests). */
	static void clear() {
		INSPECTED.clear();
	}
}
