package dev.guildy.villager;

import dev.guildy.core.GuildyServer;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Remembers the farmland of the field zones that turns back to dirt (trampled, dried out, covered), from the vanilla
 * conversion {@code FarmlandBlock.turnToBaseBlock} (mixin {@code FarmlandBlockMixin}): only in the claim dimension,
 * only in field zones whose fanion allows "Relabourer", never in another guild's land. The crop that grew there is
 * noted (its seed) so the farmer sows the same one again. Bounded per zone ({@code farmer.fieldWork.retillMemory}).
 */
public final class FieldMemory {
	private FieldMemory() {
	}

	/** Called by the mixin before the farmland at {@code pos} becomes dirt (the crop above is still there). */
	public static void onFarmlandReverted(Level level, BlockPos pos) {
		if (level.isClientSide() || !GuildyServer.isClaimLevel(level)) {
			return;
		}
		VillagerConfig config = VillagerModule.config();
		int max = config.farmer.fieldWork.retillMemory;
		if (!config.farmer.fieldWork.enabled || max <= 0) {
			return;
		}
		Guild owner = GuildManager.at(level, pos);
		String seed = null;
		for (Guild guild : GuildManager.all()) {
			if (owner != null && owner != guild) {
				continue;
			}
			for (WorkZone zone : guild.villagers.zones) {
				if (zone.type != ZoneType.FARM || !zone.contains(pos) || !ZonePermissions.allows(zone, ZoneOption.RETILL)) {
					continue;
				}
				if (seed == null) {
					seed = seedAbove(level, pos);
				}
				if (FieldState.remember(zone.field().trampled, pos, seed, max)) {
					GuildManager.markDirty();
				}
			}
		}
	}

	/** Seed item id of the crop standing on the farmland ("" when none). */
	private static String seedAbove(Level level, BlockPos pos) {
		BlockState above = level.getBlockState(pos.above());
		if (above.getBlock() instanceof CropBlock) {
			return PlantingRules.seedOfCrop(BuiltInRegistries.BLOCK.getKey(above.getBlock()).toString());
		}
		return "";
	}
}
