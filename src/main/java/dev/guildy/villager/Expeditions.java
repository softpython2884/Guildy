package dev.guildy.villager;

import dev.guildy.Guildy;
import dev.guildy.core.GuildyItems;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.ScrollDrops;
import dev.guildy.core.UniqueDrops;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.structure.BuiltStructure;
import dev.guildy.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulated expeditions. Leaving = the villager entity is saved (SNBT in its record) and removed from the world; while
 * away the record shows a countdown; on return the very same villager (UUID, profession, trades...) is recreated at its
 * departure point with loot in its bag — unless it was lost on the way. Duration, risk and loot come from the
 * destination chosen in the villager menu ({@link ExpeditionDestinations}), stored on the record for the whole trip.
 */
final class Expeditions {
	private static final String RECEPTION = "reception";
	private static final String UNIQUE_SOURCE = "expedition";
	private static final Set<String> WARNED = new HashSet<>();

	private Expeditions() {
	}

	static VillagerConfig.Expeditions settings() {
		return VillagerModule.config().expeditions;
	}

	/** Where this villager goes on its next trip of this kind: its choice, else the default destination of the kind. */
	static ExpeditionDestinations.Destination destination(Guild guild, VillagerRecord record, Task type) {
		return ExpeditionDestinations.chosen(settings(), type == Task.NETHER, record.expeditionDestination, guild.level);
	}

	/** Destination of the trip in progress (removed from the config since the departure: the default of the kind). */
	static ExpeditionDestinations.Destination awayDestination(VillagerRecord record) {
		Task type = record.awayTask == null ? Task.EXPLORER : record.awayTask;
		return ExpeditionDestinations.find(settings(), type == Task.NETHER, record.awayDestination);
	}

	/** Display name: the configured name, or the lang name of a default destination ("villager.destination.&lt;id&gt;"). */
	static String name(ExpeditionDestinations.Destination destination) {
		return destination.name().isEmpty() ? Lang.raw("villager.destination." + destination.id()) : Lang.colorize(destination.name());
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
		ExpeditionDestinations.Destination destination = destination(ctx.guild, record, type);
		int seconds = Upgrades.workSeconds(ctx.guild, destination.durationSeconds());
		record.awayEntity = saved.toString();
		record.awayTask = type;
		record.awayDestination = destination.id();
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
		Msg.guild(ctx.guild, type == Task.NETHER ? "villager.expedition.departed.nether" : "villager.expedition.departed.explorer",
				record.name, name(destination), Math.max(1, (seconds + 59) / 60));
	}

	/** Once per second for a villager away: countdown, then return (or loss). */
	static void tickAway(ServerLevel level, Guild guild, VillagerRecord record) {
		VillagerRuntime runtime = TaskEngine.runtime(record.uuid);
		Task type = record.awayTask == null ? Task.EXPLORER : record.awayTask;
		ExpeditionDestinations.Destination destination = awayDestination(record);
		String where = name(destination);
		long left = record.returnAt - level.getGameTime();
		if (left > 0) {
			runtime.status("villager.status.away", where, Math.max(1, (left / 20 + 59) / 60));
			return;
		}
		BlockPos home = record.returnPos != null ? record.returnPos : guild.heart;
		if (home == null || !level.isLoaded(home) || !level.isPositionEntityTicking(home)) {
			runtime.status("villager.status.away_waiting");
			return;
		}
		if (ThreadLocalRandom.current().nextDouble() < destination.lossChance()) {
			record.awayEntity = null;
			record.awayDestination = null;
			VillagerManager.removeRecord(guild, record);
			Msg.guild(guild, "villager.expedition.lost", record.name, where);
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
		record.awayDestination = null;
		record.returnPos = null;
		Entity restored = restore(level, saved, spot);
		if (restored == null) {
			Guildy.LOGGER.error("Could not restore villager {} ({}) back from an expedition", record.name, record.uuid);
			VillagerManager.removeRecord(guild, record);
			Msg.guild(guild, "villager.expedition.lost", record.name, where);
			return;
		}
		int found = bringLoot(level, restored, guild, record, destination);
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
		Msg.guild(guild, "villager.expedition.returned", record.name, where, found);
	}

	/**
	 * Rolls the loot of the trip into the villager's bag: the destination's table, where the items of the villager's
	 * wishlist weigh more. @return number of items brought back
	 */
	private static int bringLoot(ServerLevel level, Entity villager, Guild guild, VillagerRecord record, ExpeditionDestinations.Destination destination) {
		VillagerConfig config = VillagerModule.config();
		int carryStacks = config.work.carryStacks;
		int total = 0;
		// Rare unique weapon, rolled first so a full bag cannot swallow it (dropped at its feet otherwise).
		ItemStack unique = rollUnique(guild, destination.uniqueChanceMultiplier());
		if (!unique.isEmpty()) {
			if (addToBag(record, unique, carryStacks) == 0) {
				villager.spawnAtLocation(level, unique);
			}
			total++;
		}
		// Spell scrolls (scrolls.json5, "expedition" table, same destination multiplier), also before the common loot.
		ItemStack scroll = rollScroll(destination.uniqueChanceMultiplier());
		if (!scroll.isEmpty()) {
			int count = scroll.getCount();
			int bagged = addToBag(record, scroll, carryStacks);
			if (bagged < count) {
				villager.spawnAtLocation(level, scroll.copyWithCount(count - bagged));
			}
			total += count;
		}
		List<VillagerConfig.LootEntry> loot = ExpeditionDestinations.boosted(destination.loot(), record.wishlist, config.expeditions.wishlistWeightMultiplier);
		for (DepthLoot.Drop drop : DepthLoot.roll(new VillagerConfig.DepthTable(0, loot), destination.rolls(), ThreadLocalRandom.current())) {
			Item item = ItemUtil.item(drop.item());
			if (item == null) {
				if (WARNED.add(drop.item())) {
					Guildy.LOGGER.warn("[villagers.json5] unknown item '{}' in the loot of expedition destination '{}' (skipped)", drop.item(), destination.id());
				}
				continue;
			}
			for (ItemStack stack : stacks(level, item, drop.count())) {
				total += addToBag(record, stack, carryStacks);
			}
		}
		if (destination.treasureMapChance() > 0 && ThreadLocalRandom.current().nextDouble() < destination.treasureMapChance() && guild.heart != null) {
			double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
			int distance = 200 + ThreadLocalRandom.current().nextInt(400);
			int x = guild.heart.getX() + (int) (Math.cos(angle) * distance);
			int z = guild.heart.getZ() + (int) (Math.sin(angle) * distance);
			total += addToBag(record, GuildyItems.treasureMap(x, z, 1, guild.id), carryStacks);
		}
		return total;
	}

	/** A unique weapon of the "expedition" table, its chance scaled by the destination (one at most), or empty. */
	private static ItemStack rollUnique(Guild guild, double multiplier) {
		int attempts = ExpeditionDestinations.uniqueAttempts(multiplier, ThreadLocalRandom.current());
		for (int i = 0; i < attempts; i++) {
			ItemStack unique = UniqueDrops.roll(UNIQUE_SOURCE, UniqueDrops.Recipient.guild(guild));
			if (!unique.isEmpty()) {
				return unique;
			}
		}
		return ItemStack.EMPTY;
	}

	/** Spell scrolls of the "expedition" table, the chance scaled by the destination like the unique weapons, or empty. */
	private static ItemStack rollScroll(double multiplier) {
		int attempts = ExpeditionDestinations.uniqueAttempts(multiplier, ThreadLocalRandom.current());
		for (int i = 0; i < attempts; i++) {
			ItemStack scroll = ScrollDrops.roll(UNIQUE_SOURCE);
			if (!scroll.isEmpty()) {
				return scroll;
			}
		}
		return ItemStack.EMPTY;
	}

	/** The stacks of a loot drop: enchanted books are separate books, each with its own random enchantment. */
	private static List<ItemStack> stacks(ServerLevel level, Item item, int count) {
		if (item != Items.ENCHANTED_BOOK) {
			return List.of(new ItemStack(item, count));
		}
		List<ItemStack> books = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			books.add(randomBook(level));
		}
		return books;
	}

	/** Enchanted book with one random enchantment of #minecraft:on_random_loot, like the books of vanilla chests. */
	static ItemStack randomBook(ServerLevel level) {
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		RandomSource random = level.getRandom();
		level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.ON_RANDOM_LOOT)
				.flatMap(enchantments -> enchantments.getRandomElement(random))
				.ifPresent(enchantment -> book.enchant(enchantment,
						Mth.nextInt(random, enchantment.value().getMinLevel(), enchantment.value().getMaxLevel())));
		return book;
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
