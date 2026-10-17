package dev.guildy.villager;

import dev.guildy.core.Lifecycle;
import dev.guildy.core.Scheduler;
import dev.guildy.core.ScrollDrops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spell scrolls written by the alchemist ({@code alchemist.scroll*} in villagers.json5), through the core
 * {@link ScrollDrops} hook only: rarity and name of an id, creation (an empty stack, e.g. scrolls disabled, means an
 * enchanted book instead), the warnings for ids scrolls.json5 does not define, and the look of a finished scroll.
 */
final class AlchemistScrolls {
	private static final Logger LOGGER = LoggerFactory.getLogger("Guildy");
	/** Ticks between the runes drawn into the table and the flash. */
	private static final int FLASH_DELAY = 10;
	private static final int RUNES = 24;
	private static final int RING = 16;
	/** Violet of the flash (enchanting glyphs). */
	private static final int FLASH_COLOR = 0xFFB070FF;

	private AlchemistScrolls() {
	}

	static void init() {
		// scrolls.json5 is loaded after villagers.json5: the ids are checked once every file is loaded.
		Lifecycle.STARTED.add(server -> warnUnknown());
		Lifecycle.RELOADED.add(AlchemistScrolls::warnUnknown);
	}

	private static void warnUnknown() {
		for (String warning : VillagerModule.config().alchemist.unknownScrolls(AlchemistScrolls::definedRarity)) {
			LOGGER.warn("[villagers.json5] {}", warning);
		}
	}

	/** Rarity of a scroll id defined in scrolls.json5, else null. */
	private static @Nullable String definedRarity(String id) {
		ScrollDrops.Info info = ScrollDrops.info(id);
		return info == null ? null : info.rarity();
	}

	/** Rarity of a scroll the alchemist may write now (defined and obtainable), else null. */
	static @Nullable String rarity(String id) {
		ScrollDrops.Info info = ScrollDrops.info(id);
		return info == null || !info.obtainable() ? null : info.rarity();
	}

	/** Plain name of a scroll id ("Rappel"), the id when scrolls.json5 no longer defines it. */
	static String name(String id) {
		ScrollDrops.Info info = ScrollDrops.info(id);
		return info == null || info.name().isBlank() ? id : info.name();
	}

	/** One scroll of that id, or empty (unknown id, scrolls disabled, module absent). */
	static ItemStack create(String id) {
		return ScrollDrops.create(id, 1);
	}

	static boolean isScroll(ItemStack stack) {
		return ScrollDrops.isScroll(stack);
	}

	/**
	 * A finished scroll: a ring of enchanting runes drawn into the book on the table, then (half a second later) a short
	 * runic flash (violet flash, runes and sparks bursting out, a ring of light) with a spell sound instead of the
	 * enchanting table's.
	 */
	static void effects(ServerLevel level, BlockPos table) {
		RandomSource random = level.getRandom();
		double x = table.getX() + 0.5;
		double y = table.getY() + 1.25;
		double z = table.getZ() + 0.5;
		for (int i = 0; i < RUNES; i++) {
			double angle = Math.PI * 2 * i / RUNES;
			// Count 0: the "distances" are where each rune starts, flying into (x, y, z) like EnchantingTableBlock.animateTick.
			level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 0, Math.cos(angle) * 1.8, 0.4 + random.nextFloat() * 0.8,
					Math.sin(angle) * 1.8, 1.0);
		}
		level.playSound(null, table, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0f, 0.6f);
		level.playSound(null, table, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8f, 1.4f);
		Scheduler.later("villager.alchemist.scroll_flash", FLASH_DELAY, () -> flash(level, table));
	}

	private static void flash(ServerLevel level, BlockPos table) {
		if (!level.isLoaded(table)) {
			return;
		}
		double x = table.getX() + 0.5;
		double y = table.getY() + 1.3;
		double z = table.getZ() + 0.5;
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, FLASH_COLOR), x, y, z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ENCHANT, x, y + 0.2, z, 40, 0.6, 0.5, 0.6, 1.5);
		level.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 18, 0.35, 0.3, 0.35, 0.4);
		for (int i = 0; i < RING; i++) {
			double angle = Math.PI * 2 * i / RING;
			// Count 0: (dx, dy, dz) × speed is the motion, a ring of light spreading out.
			level.sendParticles(ParticleTypes.END_ROD, x, y - 0.2, z, 0, Math.cos(angle), 0.02, Math.sin(angle), 0.12);
		}
		level.playSound(null, table, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.BLOCKS, 0.9f, 1.3f);
		level.playSound(null, table, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2f, 1.6f);
	}
}
