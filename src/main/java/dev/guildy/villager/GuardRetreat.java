package dev.guildy.villager;

import dev.guildy.data.Guild;
import dev.guildy.structure.BuiltStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.AbstractBedBlock;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Where a wounded guard goes to heal: its own bed (villager HOME memory), else the nearest bed of the territory, else
 * the reception area or the heart (during a horde the reception first: the heart is where the monsters go).
 */
final class GuardRetreat {
	/** Bed search radius around the guard (blocks): close enough to only touch loaded chunks. */
	private static final int BED_SEARCH_RADIUS = 32;

	private GuardRetreat() {
	}

	/** A retreat spot; {@code bed} = heals faster there. */
	record Spot(BlockPos pos, boolean bed) {
	}

	static @Nullable Spot find(WorkContext ctx, boolean wave) {
		BlockPos home = home(ctx);
		if (home != null) {
			return new Spot(home, true);
		}
		BlockPos bed = nearestBed(ctx);
		if (bed != null) {
			return new Spot(bed, true);
		}
		BlockPos reception = reception(ctx.guild);
		BlockPos heart = ctx.guild.heart;
		BlockPos first = wave ? reception : heart;
		BlockPos second = wave ? heart : reception;
		if (first != null && ctx.loaded(first)) {
			return new Spot(first, false);
		}
		if (second != null && ctx.loaded(second)) {
			return new Spot(second, false);
		}
		return null;
	}

	/** The guard's own bed (vanilla HOME memory) when it still is a loaded bed outside other guilds' land. */
	static @Nullable BlockPos home(WorkContext ctx) {
		Optional<GlobalPos> memory = ctx.villager.getBrain().getMemory(MemoryModuleType.HOME);
		if (memory.isEmpty() || !memory.get().dimension().equals(ctx.level.dimension())) {
			return null;
		}
		BlockPos pos = memory.get().pos();
		return isBed(ctx.level, pos) && !ctx.foreign(pos) ? pos : null;
	}

	/** Nearest bed (point of interest "home") of the territory around the guard: a free one first, else any bed. */
	static @Nullable BlockPos nearestBed(WorkContext ctx) {
		BlockPos free = nearestBed(ctx, PoiManager.Occupancy.HAS_SPACE);
		return free != null ? free : nearestBed(ctx, PoiManager.Occupancy.ANY);
	}

	private static @Nullable BlockPos nearestBed(WorkContext ctx, PoiManager.Occupancy occupancy) {
		return ctx.level.getPoiManager().findClosest(type -> type.is(PoiTypes.HOME),
				pos -> ctx.inTerritory(pos) && isBed(ctx.level, pos), ctx.villager.blockPosition(), BED_SEARCH_RADIUS, occupancy).orElse(null);
	}

	static boolean isBed(ServerLevel level, BlockPos pos) {
		return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof AbstractBedBlock;
	}

	/** Reception sign (else structure origin), like the villagers' arrival point. */
	static @Nullable BlockPos reception(Guild guild) {
		BuiltStructure reception = guild.structures.find(VillagerManager.RECEPTION);
		if (reception == null) {
			return null;
		}
		if (reception.signs != null && !reception.signs.isEmpty() && reception.signs.getFirst() != null) {
			return reception.signs.getFirst();
		}
		return reception.origin;
	}
}
