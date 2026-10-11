package dev.guildy.villager;

import dev.guildy.Guildy;
import dev.guildy.core.GuildyItems;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.UniqueDrops;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.structure.BuiltStructure;
import dev.guildy.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulated expeditions. Leaving = the villager entity is saved (SNBT in its record) and removed from the world; while
 * away the record shows a countdown; on return the very same villager (UUID, profession, trades...) is recreated at its
 * departure point with loot in its bag — unless it was lost on the way.
 */
final class Expeditions {
	private static final String RECEPTION = "reception";

	private Expeditions() {
	}

	static VillagerConfig.Expedition config(Task type) {
		VillagerConfig.Expeditions expeditions = VillagerModule.config().expeditions;
		return type == Task.NETHER ? expeditions.nether : expeditions.exploration;
	}

	/** Reception sign / heart for the wilds; nearest nether portal inside the territory for the Nether. */
	static @Nullable BlockPos departurePoint(ServerLevel level, Guild guild, Task type, BlockPos from) {
		if (type == Task.NETHER) {
			return nearestPortal(level, guild, from);
		}
		BuiltStructure reception = guild.structures.find(RECEPTION);
		if (reception != null && !reception.signs.isEmpty()) {
			return reception.signs.getFirst();
		}
		return guild.heart;
	}

	static @Nullable BlockPos nearestPortal(ServerLevel level, Guild guild, BlockPos from) {
		if (guild.heart == null) {
			return null;
		}
		int radius = Math.max(16, Math.min(guild.territoryRadiusBlocks(), 256));
		return level.getPoiManager()
				.getInRange(holder -> holder.is(PoiTypes.NETHER_PORTAL), guild.heart, radius, PoiManager.Occupancy.ANY)
				.map(PoiRecord::getPos)
				.filter(pos -> GuildManager.at(level, pos) == guild)
				.min(Comparator.comparingDouble(pos -> pos.distSqr(from)))
				.orElse(null);
	}

	/** The villager leaves: saved into its record, then removed from the world. */
	static void depart(WorkContext ctx, Task type, BlockPos departure) {
		Villager villager = ctx.villager;
		VillagerRecord record = ctx.record;
		CompoundTag saved = save(villager);
		if (saved == null) {
			ctx.status("villager.status.departure_failed");
			return;
		}
		VillagerConfig.Expedition config = config(type);
		int seconds = Upgrades.workSeconds(ctx.guild, config.durationSeconds);
		record.awayEntity = saved.toString();
		record.awayTask = type;
		record.returnAt = ctx.level.getGameTime() + WorkContext.ticks(seconds);
		record.returnPos = departure.immutable();
		GuildManager.markDirty();

		Movement.stop(ctx);
		if (type == Task.NETHER) {
			WorldUtil.portal(ctx.level, villager.getX(), villager.getY() + 1.0, villager.getZ());
			WorldUtil.playSound(ctx.level, villager, SoundEvents.PORTAL_TRIGGER, 0.4f, 1.4f);
		} else {
			WorldUtil.poof(ctx.level, villager);
			WorldUtil.playSound(ctx.level, villager, SoundEvents.VILLAGER_CELEBRATE, 1.0f, 1.0f);
		}
		TaskEngine.forget(record.uuid);
		villager.discard();
		Msg.guild(ctx.guild, "villager.expedition.departed", record.name, Lang.raw(type.langKey()), Math.max(1, (seconds + 59) / 60));
	}

	/** Once per second for a villager away: countdown, then return (or loss). */
	static void tickAway(ServerLevel level, Guild guild, VillagerRecord record) {
		VillagerRuntime runtime = TaskEngine.runtime(record.uuid);
		Task type = record.awayTask == null ? Task.EXPLORER : record.awayTask;
		long left = record.returnAt - level.getGameTime();
		if (left > 0) {
			runtime.status("villager.status.away", Lang.raw(type.langKey()), Math.max(1, (left / 20 + 59) / 60));
			return;
		}
		BlockPos home = record.returnPos != null ? record.returnPos : guild.heart;
		if (home == null || !level.isLoaded(home) || !level.isPositionEntityTicking(home)) {
			runtime.status("villager.status.away_waiting");
			return;
		}
		VillagerConfig.Expedition config = config(type);
		if (ThreadLocalRandom.current().nextDouble() < config.lossChance) {
			record.awayEntity = null;
			VillagerManager.removeRecord(guild, record);
			Msg.guild(guild, "villager.expedition.lost", record.name, Lang.raw(type.langKey()));
			return;
		}
		BlockPos spot = WorldUtil.findSafeSpot(level, home, 4, true, pos -> GuildManager.at(level, pos) == guild);
		if (spot == null) {
			spot = home.above();
		}
		Entity stale = level.getEntity(record.uuid);
		if (stale != null) {
			// Old copy of the villager (chunk saved before the departure, then a crash): the saved one wins.
			stale.discard();
		}
		// Back home before the entity is added: the load event must see a member at home, not a stale copy.
		String saved = record.awayEntity;
		record.awayEntity = null;
		record.awayTask = null;
		record.returnPos = null;
		Entity restored = restore(level, saved, spot);
		if (restored == null) {
			Guildy.LOGGER.error("Could not restore villager {} ({}) back from an expedition", record.name, record.uuid);
			VillagerManager.removeRecord(guild, record);
			Msg.guild(guild, "villager.expedition.lost", record.name, Lang.raw(type.langKey()));
			return;
		}
		int found = bringLoot(level, restored, guild, record, config);
		record.stats.expeditions++;
		record.stats.found += found;
		if (record.goal == Goal.FILL) {
			record.goalReached = true;
		}
		GuildManager.markDirty();
		if (type == Task.NETHER) {
			WorldUtil.portal(level, restored.getX(), restored.getY() + 1.0, restored.getZ());
		}
		WorldUtil.happy(level, restored);
		WorldUtil.playSound(level, restored, SoundEvents.VILLAGER_CELEBRATE, 1.0f, 1.1f);
		Msg.guild(guild, "villager.expedition.returned", record.name, Lang.raw(type.langKey()), found);
	}

	/** Rolls the loot of the trip into the villager's bag. @return number of items brought back */
	private static int bringLoot(ServerLevel level, Entity villager, Guild guild, VillagerRecord record, VillagerConfig.Expedition config) {
		int carryStacks = VillagerModule.config().work.carryStacks;
		int total = 0;
		// Rare unique weapon, rolled first so a full bag cannot swallow it (dropped at its feet otherwise).
		ItemStack unique = UniqueDrops.roll("expedition");
		if (!unique.isEmpty()) {
			if (addToBag(record, unique, carryStacks) == 0) {
				villager.spawnAtLocation(level, unique);
			}
			total++;
		}
		for (DepthLoot.Drop drop : DepthLoot.roll(config.table(), config.rolls, ThreadLocalRandom.current())) {
			Item item = ItemUtil.item(drop.item());
			if (item != null) {
				total += addToBag(record, new ItemStack(item, drop.count()), carryStacks);
			}
		}
		if (config.treasureMapChance > 0 && ThreadLocalRandom.current().nextDouble() < config.treasureMapChance && guild.heart != null) {
			double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
			int distance = 200 + ThreadLocalRandom.current().nextInt(400);
			int x = guild.heart.getX() + (int) (Math.cos(angle) * distance);
			int z = guild.heart.getZ() + (int) (Math.sin(angle) * distance);
			total += addToBag(record, GuildyItems.treasureMap(x, z, 1, guild.id), carryStacks);
		}
		return total;
	}

	/** Merges into the bag (max {@code carryStacks} stacks, the rest is lost on the way). @return items added */
	private static int addToBag(VillagerRecord record, ItemStack input, int carryStacks) {
		ItemStack stack = input.copy();
		for (ItemStack carried : record.carried) {
			if (stack.isEmpty()) {
				break;
			}
			if (ItemStack.isSameItemSameComponents(carried, stack) && carried.getCount() < carried.getMaxStackSize()) {
				int move = Math.min(stack.getCount(), carried.getMaxStackSize() - carried.getCount());
				carried.grow(move);
				stack.shrink(move);
			}
		}
		while (!stack.isEmpty() && record.carried.size() < carryStacks) {
			record.carried.add(stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
		}
		int added = input.getCount() - stack.getCount();
		Missions.onCollected(record, input, added, record.carried.size() >= carryStacks);
		return added;
	}

	static @Nullable CompoundTag save(Villager villager) {
		try {
			TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, villager.level().registryAccess());
			if (!villager.save(output)) {
				return null;
			}
			return output.buildResult();
		} catch (Exception e) {
			Guildy.LOGGER.error("Could not save villager {} for an expedition", villager.getUUID(), e);
			return null;
		}
	}

	static @Nullable Entity restore(ServerLevel level, @Nullable String snbt, BlockPos spot) {
		if (snbt == null) {
			return null;
		}
		try {
			CompoundTag tag = TagParser.parseCompoundFully(snbt);
			Entity entity = EntityType.loadEntityRecursive(tag, level, new EntitySpawnRequest(EntitySpawnReason.LOAD, false), loaded -> {
				loaded.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, loaded.getYRot(), loaded.getXRot());
				return loaded;
			});
			if (entity == null || !level.addFreshEntity(entity)) {
				return null;
			}
			return entity;
		} catch (Exception e) {
			Guildy.LOGGER.error("Invalid saved villager data", e);
			return null;
		}
	}
}
