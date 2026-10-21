package dev.guildy.villager;

import dev.guildy.alchemy.BrewBook;
import dev.guildy.data.Guild;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Roles of the alchemists of a guild: the balanced distribution ({@link AlchemyRoles}, among the jobs the guild can do
 * right now: tables, stands, books, paper, a missing potion with its reagents...) recomputed every
 * {@code alchemist.roleIntervalSeconds} or when the alchemists change, the manual roles of the menus, and what the menus
 * show (role, job worked at, why the preferred one is impossible).
 */
public final class Alchemists {
	private Alchemists() {
	}

	/** Preferred job of an alchemist without a manual role (the guild's distribution). */
	static AlchemyJob assigned(WorkContext ctx, AlchemyGuild shared) {
		AlchemistConfig cfg = ctx.config.alchemist;
		List<AlchemyJob> order = cfg.order();
		AlchemyRoles.Distribution mode = AlchemyRoles.Distribution.parse(cfg.distribution);
		List<VillagerRecord> alchemists = AlchemyGuild.alchemists(ctx.guild);
		Set<UUID> ids = new java.util.HashSet<>();
		for (VillagerRecord record : alchemists) {
			ids.add(record.uuid);
		}
		ids.add(ctx.record.uuid);
		if (shared.rolesDue(ctx.now, WorkContext.ticks(cfg.roleIntervalSeconds), ids) || !shared.roles.containsKey(ctx.record.uuid)) {
			distribute(ctx.level, ctx.guild, shared, alchemists, ids, mode, ctx.now);
		}
		AlchemyJob role = shared.roles.get(ctx.record.uuid);
		return role == null ? order.getFirst() : role;
	}

	private static void distribute(ServerLevel level, Guild guild, AlchemyGuild shared, List<VillagerRecord> alchemists, Set<UUID> ids,
			AlchemyRoles.Distribution mode, long now) {
		AlchemistConfig cfg = VillagerModule.config().alchemist;
		List<String> order = new ArrayList<>();
		Map<String, AlchemyJob> manual = new HashMap<>();
		Map<String, AlchemyJob> previous = new HashMap<>();
		Map<String, UUID> back = new HashMap<>();
		for (VillagerRecord record : alchemists) {
			String id = record.uuid.toString();
			order.add(id);
			back.put(id, record.uuid);
			AlchemyJob own = record.craft == null ? null : AlchemyJob.parse(record.craft.role);
			if (own != null) {
				manual.put(id, own);
			}
			AlchemyJob before = shared.roles.get(record.uuid);
			if (before != null) {
				previous.put(id, before);
			}
		}
		Set<AlchemyJob> possible = mode == AlchemyRoles.Distribution.BALANCED ? possible(level, guild, shared, alchemists) : EnumSet.allOf(AlchemyJob.class);
		Map<String, AlchemyJob> result = AlchemyRoles.assign(order, manual, previous, cfg.order(), AlchemyRoles.weights(cfg.roleWeights), possible, mode);
		Map<UUID, AlchemyJob> roles = new HashMap<>();
		for (Map.Entry<String, AlchemyJob> entry : result.entrySet()) {
			roles.put(back.get(entry.getKey()), entry.getValue());
		}
		shared.setRoles(roles, ids, now);
	}

	/**
	 * Jobs the guild can do right now, daily caps aside (all of them before the first scan): a table and books (scrolls: and
	 * paper, a scroll possible at the best table), a stand and a missing potion with its bottles, reagents and fuel.
	 */
	static Set<AlchemyJob> possible(ServerLevel level, Guild guild, AlchemyGuild shared, List<VillagerRecord> alchemists) {
		if (!shared.workshopsKnown()) {
			return EnumSet.allOf(AlchemyJob.class);
		}
		AlchemistConfig cfg = VillagerModule.config().alchemist;
		Set<AlchemyJob> possible = EnumSet.noneOf(AlchemyJob.class);
		AlchemyGuild.Stock stock = shared.stock(level, guild);
		List<List<ItemStack>> bags = new ArrayList<>();
		int fuel = 0;
		for (VillagerRecord record : alchemists) {
			bags.add(record.carried);
			fuel += record.craft == null ? 0 : record.craft.brewFuel;
		}
		int books = stock.plain(Items.BOOK) + carried(bags, Items.BOOK);
		int lapis = stock.plain(Items.LAPIS_LAZULI) + carried(bags, Items.LAPIS_LAZULI);
		int paper = stock.plain(Items.PAPER) + carried(bags, Items.PAPER);
		int bestShelves = -1;
		for (BlockPos pos : shared.tables) {
			if (CraftWorld.isTable(level, pos)) {
				bestShelves = Math.max(bestShelves, CraftWorld.bookshelves(level, pos));
			}
		}
		boolean lapisOk = cfg.lapisPerBook <= 0 || lapis >= cfg.lapisPerBook;
		if (bestShelves >= 0 && books > 0 && lapisOk) {
			possible.add(AlchemyJob.BOOKS);
			if (cfg.maxScrollsPerDay > 0 && paper >= cfg.scrollPaper
					&& AlchemyRules.anyScroll(cfg.scrolls, AlchemistScrolls::rarity, AlchemyRules.maxLevel(bestShelves), cfg.epicScrollLevel)) {
				possible.add(AlchemyJob.SCROLLS);
			}
		}
		boolean stand = false;
		for (BlockPos pos : shared.stands) {
			stand |= CraftWorld.isStand(level, pos);
		}
		if (stand && cfg.maxPotionsPerDay > 0) {
			boolean fillable = cfg.fillBottles && shared.anyWater(level, cfg.waterRadius);
			PotionPlanner.View view = PotionPlanner.view(guild, stock, bags, fuel, fillable);
			if (PotionPlanner.choose(level, guild, shared, view, null).verdict() == BrewBook.Verdict.BREW) {
				possible.add(AlchemyJob.POTIONS);
			}
		}
		return possible;
	}

	private static int carried(List<List<ItemStack>> bags, net.minecraft.world.item.Item item) {
		int count = 0;
		for (List<ItemStack> bag : bags) {
			for (ItemStack stack : bag) {
				if (CraftStock.plain(stack, item)) {
					count += stack.getCount();
				}
			}
		}
		return count;
	}

	// ------------------------------------------------------------------ menus and tests

	/** Role of an alchemist: its manual role, else its share of the distribution (null before its first decision). */
	public static @Nullable AlchemyJob role(Guild guild, UUID alchemist) {
		VillagerRecord record = guild.villagers.members.get(alchemist);
		AlchemyJob manual = record == null || record.craft == null ? null : AlchemyJob.parse(record.craft.role);
		if (manual != null) {
			return manual;
		}
		AlchemyGuild shared = AlchemyGuild.peek(guild);
		return shared == null ? null : shared.roles.get(alchemist);
	}

	/** Job the alchemist worked at on its last decision (null: nothing possible, or no decision yet). */
	public static @Nullable AlchemyJob working(Guild guild, UUID alchemist) {
		AlchemyGuild shared = AlchemyGuild.peek(guild);
		AlchemyGuild.Note note = shared == null ? null : shared.notes.get(alchemist);
		return note == null ? null : note.working();
	}

	/** Why its preferred job was impossible on its last decision ("" when it works at it). */
	public static String fallbackReason(Guild guild, UUID alchemist) {
		AlchemyGuild shared = AlchemyGuild.peek(guild);
		AlchemyGuild.Note note = shared == null ? null : shared.notes.get(alchemist);
		if (note == null || note.working() == note.preferred()) {
			return "";
		}
		String reason = note.reasons().get(note.preferred());
		return reason == null ? "" : reason;
	}

	/** Sets the manual role of an alchemist (null = automatic) and redistributes at the next decision. */
	public static void setRole(Guild guild, VillagerRecord record, @Nullable AlchemyJob role) {
		CraftJobs.data(record).role = role == null ? "" : role.id();
		AlchemyGuild shared = AlchemyGuild.peek(guild);
		if (shared != null) {
			shared.rolesAt = -1;
		}
		dev.guildy.data.GuildManager.markDirty();
	}
}
