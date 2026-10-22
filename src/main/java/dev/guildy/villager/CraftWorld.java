package dev.guildy.villager;

import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * World reads of the blacksmith and alchemist (loaded chunks only, never loads one): armor stands and their damaged
 * equipment, anvils near a point, enchanting tables of a chunk and their bookshelves, brewing stands and the water near
 * them.
 */
final class CraftWorld {
	/** Equipment slots of an armor stand, in repair order on equal damage. */
	static final List<EquipmentSlot> STAND_SLOTS = List.of(EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.HEAD,
			EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);
	private static final Predicate<BlockState> ANVIL = state -> state.is(BlockTags.ANVIL);
	private static final Predicate<BlockState> TABLE = state -> state.is(Blocks.ENCHANTING_TABLE);
	private static final Predicate<BlockState> STAND = state -> state.is(Blocks.BREWING_STAND);

	private CraftWorld() {
	}

	// ------------------------------------------------------------------ armor stands

	/** A repairable item: it can break (unbreakable items, like Guildy's unique weapons, are left alone) and is damaged. */
	static boolean repairable(ItemStack stack) {
		return !stack.isEmpty() && stack.isDamageableItem() && stack.getDamageValue() > 0 && stack.getMaxDamage() > 0;
	}

	/** Slot of the item to repair first on this stand (lowest durability left), or null. */
	static @Nullable EquipmentSlot mostDamaged(ArmorStand stand) {
		int[] damage = new int[STAND_SLOTS.size()];
		int[] max = new int[STAND_SLOTS.size()];
		for (int i = 0; i < STAND_SLOTS.size(); i++) {
			ItemStack stack = stand.getItemBySlot(STAND_SLOTS.get(i));
			if (repairable(stack)) {
				damage[i] = stack.getDamageValue();
				max[i] = stack.getMaxDamage();
			}
		}
		int index = ForgeRules.mostDamaged(damage, max);
		return index < 0 ? null : STAND_SLOTS.get(index);
	}

	/** Lowest durability ratio of the stand's repairable items (1 when nothing needs a repair). */
	static double worstRatio(ArmorStand stand) {
		double worst = 1.0;
		for (EquipmentSlot slot : STAND_SLOTS) {
			ItemStack stack = stand.getItemBySlot(slot);
			if (repairable(stack)) {
				worst = Math.min(worst, ForgeRules.durabilityRatio(stack.getDamageValue(), stack.getMaxDamage()));
			}
		}
		return worst;
	}

	/** Armor stands of the chunk holding at least one repairable item. */
	static List<ArmorStand> damagedStands(ServerLevel level, LevelChunk chunk) {
		int x0 = chunk.getPos().getMinBlockX();
		int z0 = chunk.getPos().getMinBlockZ();
		AABB box = new AABB(x0, level.getMinY(), z0, x0 + 16, level.getMaxY() + 1, z0 + 16);
		return level.getEntitiesOfClass(ArmorStand.class, box, stand -> stand.isAlive() && mostDamaged(stand) != null);
	}

	/** Live armor stands of the chunk (repairs and rune inspections). */
	static List<ArmorStand> stands(ServerLevel level, LevelChunk chunk) {
		int x0 = chunk.getPos().getMinBlockX();
		int z0 = chunk.getPos().getMinBlockZ();
		AABB box = new AABB(x0, level.getMinY(), z0, x0 + 16, level.getMaxY() + 1, z0 + 16);
		return level.getEntitiesOfClass(ArmorStand.class, box, ArmorStand::isAlive);
	}

	// ------------------------------------------------------------------ anvils

	/** Height of the slot's item on a standing armor stand (particles). */
	static double slotHeight(ArmorStand stand, EquipmentSlot slot) {
		double h = stand.getBbHeight();
		return switch (slot) {
			case HEAD -> h * 0.9;
			case CHEST, MAINHAND, OFFHAND -> h * 0.65;
			case LEGS -> h * 0.4;
			default -> h * 0.1;
		};
	}

	/**
	 * Nearest anvil (any wear stage) within {@code radius} blocks of {@code center}, inside the guild's territory, in
	 * loaded chunks; sections without an anvil are skipped through their palette.
	 */
	static @Nullable BlockPos nearestAnvil(ServerLevel level, Guild guild, BlockPos center, int radius) {
		BlockPos best = null;
		double bestDist = (double) radius * radius + 0.001;
		int minY = Math.max(level.getMinY(), center.getY() - radius);
		int maxY = Math.min(level.getMaxY(), center.getY() + radius);
		for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
			for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
					int index = chunk.getSectionIndexFromSectionY(sy);
					if (index < 0 || index >= chunk.getSectionsCount()) {
						continue;
					}
					LevelChunkSection section = chunk.getSection(index);
					if (section.hasOnlyAir() || !section.maybeHas(ANVIL)) {
						continue;
					}
					int x0 = Math.max(cx << 4, center.getX() - radius);
					int x1 = Math.min((cx << 4) + 15, center.getX() + radius);
					int z0 = Math.max(cz << 4, center.getZ() - radius);
					int z1 = Math.min((cz << 4) + 15, center.getZ() + radius);
					int y0 = Math.max(sy << 4, minY);
					int y1 = Math.min((sy << 4) + 15, maxY);
					for (int y = y0; y <= y1; y++) {
						for (int x = x0; x <= x1; x++) {
							for (int z = z0; z <= z1; z++) {
								if (!ANVIL.test(section.getBlockState(x & 15, y & 15, z & 15))) {
									continue;
								}
								double dx = x - center.getX();
								double dy = y - center.getY();
								double dz = z - center.getZ();
								double dist = dx * dx + dy * dy + dz * dz;
								BlockPos pos = new BlockPos(x, y, z);
								if (dist < bestDist && GuildManager.at(level, pos) == guild) {
									best = pos;
									bestDist = dist;
								}
							}
						}
					}
				}
			}
		}
		return best;
	}

	static boolean isAnvil(ServerLevel level, BlockPos pos) {
		return level.isLoaded(pos) && ANVIL.test(level.getBlockState(pos));
	}

	static ForgeRules.AnvilStage stage(BlockState state) {
		if (state.is(Blocks.ANVIL)) {
			return ForgeRules.AnvilStage.INTACT;
		}
		if (state.is(Blocks.CHIPPED_ANVIL)) {
			return ForgeRules.AnvilStage.CHIPPED;
		}
		return state.is(BlockTags.ANVIL) ? ForgeRules.AnvilStage.DAMAGED : ForgeRules.AnvilStage.BROKEN;
	}

	// ------------------------------------------------------------------ enchanting tables

	/** Enchanting tables of a chunk (sections without one are skipped through their palette). */
	static List<BlockPos> enchantingTables(LevelChunk chunk) {
		return blocks(chunk, TABLE);
	}

	/** Brewing stands of a chunk. */
	static List<BlockPos> brewingStands(LevelChunk chunk) {
		return blocks(chunk, STAND);
	}

	static boolean isStand(ServerLevel level, BlockPos pos) {
		return level.isLoaded(pos) && STAND.test(level.getBlockState(pos));
	}

	/**
	 * Nearest water to fill glass bottles from, within {@code radius} blocks of the stand (3 blocks up or down), in loaded
	 * chunks: a water source block or a water cauldron.
	 */
	static @Nullable BlockPos water(ServerLevel level, BlockPos stand, int radius) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dy = -3; dy <= 3; dy++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					pos.set(stand.getX() + dx, stand.getY() + dy, stand.getZ() + dz);
					double dist = dx * dx + dy * dy + dz * dz;
					if (dist >= bestDist || !level.isLoaded(pos) || !isWater(level, pos)) {
						continue;
					}
					best = pos.immutable();
					bestDist = dist;
				}
			}
		}
		return best;
	}

	/** A water source block or a water cauldron (with water left). */
	static boolean isWater(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.is(Blocks.WATER_CAULDRON)) {
			return true;
		}
		FluidState fluid = state.getFluidState();
		return fluid.is(FluidTags.WATER) && fluid.isSource();
	}

	/**
	 * Fills one glass bottle at that water: a source block gives it for free, a cauldron loses one level like in vanilla.
	 *
	 * @return false when there is no water there any more
	 */
	static boolean drawWater(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos) || !isWater(level, pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		if (state.is(Blocks.WATER_CAULDRON)) {
			LayeredCauldronBlock.lowerFillLevel(state, level, pos);
		}
		return true;
	}

	/** Blocks of a chunk matching {@code test} (sections without one are skipped through their palette). */
	private static List<BlockPos> blocks(LevelChunk chunk, Predicate<BlockState> test) {
		List<BlockPos> tables = new ArrayList<>();
		int x0 = chunk.getPos().getMinBlockX();
		int z0 = chunk.getPos().getMinBlockZ();
		LevelChunkSection[] sections = chunk.getSections();
		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection section = sections[i];
			if (section == null || section.hasOnlyAir() || !section.maybeHas(test)) {
				continue;
			}
			int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						if (test.test(section.getBlockState(x, y, z))) {
							tables.add(new BlockPos(x0 + x, baseY + y, z0 + z));
						}
					}
				}
			}
		}
		return tables;
	}

	static boolean isTable(ServerLevel level, BlockPos pos) {
		return level.isLoaded(pos) && TABLE.test(level.getBlockState(pos));
	}

	/** Valid bookshelves of a table (vanilla offsets and "transmitter" check), in loaded chunks only; not capped. */
	static int bookshelves(ServerLevel level, BlockPos table) {
		int count = 0;
		for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
			// The block between (offset / 2) is in the table's chunk or in the bookshelf's: both loaded then.
			if (level.isLoaded(table.offset(offset)) && EnchantingTableBlock.isValidBookShelf(level, table, offset)) {
				count++;
			}
		}
		return count;
	}

	/** Offsets of the valid bookshelves (particles). */
	static List<BlockPos> bookshelfOffsets(ServerLevel level, BlockPos table) {
		List<BlockPos> offsets = new ArrayList<>();
		for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
			if (level.isLoaded(table.offset(offset)) && EnchantingTableBlock.isValidBookShelf(level, table, offset)) {
				offsets.add(offset);
			}
		}
		return offsets;
	}
}
