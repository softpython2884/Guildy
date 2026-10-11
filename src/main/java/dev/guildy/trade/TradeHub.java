package dev.guildy.trade;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import dev.guildy.shop.PlainItems;
import dev.guildy.structure.BuiltStructure;
import dev.guildy.structure.StructureBehavior;
import dev.guildy.structure.StructureManager;
import dev.guildy.structure.TextUtil;
import dev.guildy.wave.WaveFormula;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * The trade hub ("Carrefour commercial"): structure behavior (sign = tier + merchant status, click = hub menu), the
 * morning visit roll of the "Marchand de passage" and the tier upgrades (treasury gold + plain items of the player,
 * atomic payment).
 */
public final class TradeHub {
	/** Structure id (structures.json5). */
	public static final String STRUCTURE = "trade_hub";
	/** Visitor kind of the hub merchant. */
	public static final String KIND = "merchant";
	private static final SplittableRandom RANDOM = new SplittableRandom();

	private record Taken(Item item, int count) {
	}

	private TradeHub() {
	}

	static void init() {
		StructureBehavior.register(STRUCTURE, new Behavior());
		Merchants.register(new HubMerchant());
		Scheduler.every("trade.hub", 20, 14, TradeHub::tick);
	}

	// ================================================================== queries

	/** The guild's standing trade hub, or null. */
	public static @Nullable BuiltStructure hub(Guild guild) {
		return guild.structures.find(STRUCTURE);
	}

	/** Current tier (1..max, clamped to the configured tiers). */
	public static int tier(Guild guild) {
		return Math.max(1, Math.min(guild.trade.tier, TradeModule.config().maxTier()));
	}

	/** Chance (0..1) of a merchant visit tomorrow morning. */
	public static double visitChance(Guild guild) {
		TradeConfig.Visits visits = TradeModule.config().visits;
		return TradeRolls.visitChance(visits.chanceBase, visits.chancePerTier, tier(guild));
	}

	/** Where visitors arrive at the hub: in front of its sign, at floor level. Null without a hub. */
	public static @Nullable BlockPos arrivalPoint(Guild guild) {
		BuiltStructure hub = hub(guild);
		if (hub == null || hub.facing == null || hub.signs == null || hub.signs.isEmpty()) {
			return null;
		}
		return hub.signs.getFirst().relative(hub.facing).below();
	}

	/** Arrival point of event merchants: the trade hub if built, else next to the heart. */
	public static @Nullable BlockPos basePoint(Guild guild) {
		BlockPos hub = arrivalPoint(guild);
		if (hub != null) {
			return hub;
		}
		return guild.heart == null ? null : guild.heart.offset(3, 0, 2);
	}

	public static String roman(int n) {
		String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		return n >= 1 && n <= numerals.length ? numerals[n - 1] : String.valueOf(n);
	}

	// ================================================================== morning visits

	private static void tick() {
		TradeConfig cfg = TradeModule.config();
		ServerLevel level = GuildyServer.claimLevel();
		if (!cfg.visits.enabled || level == null) {
			return;
		}
		long clock = level.getOverworldClockTime();
		long timeOfDay = WaveFormula.timeOfDay(clock);
		if (timeOfDay < cfg.visits.morningTime || timeOfDay >= cfg.visits.morningTime + cfg.visits.rollWindowTicks) {
			return;
		}
		long day = WaveFormula.dayIndex(clock);
		for (Guild guild : List.copyOf(GuildManager.all())) {
			if (guild.trade.lastVisitRollDay != day && hub(guild) != null) {
				Safe.run("trade.visit_roll", () -> rollVisit(guild, day, RANDOM));
			}
		}
	}

	static void rollVisit(Guild guild, long day, RandomGenerator random) {
		guild.trade.lastVisitRollDay = day;
		GuildManager.markDirty();
		if (random.nextDouble() < visitChance(guild)) {
			Merchants.schedule(guild, KIND, day);
		}
	}

	// ================================================================== tier upgrades

	/** Plain items of this id carried by the player (main inventory + off hand). */
	public static int owned(ServerPlayer player, String itemId) {
		Item item = ItemUtil.item(itemId);
		return item == null ? 0 : PlainItems.count(player, stack -> stack.is(item));
	}

	/** Why the player cannot upgrade the hub now, or null. */
	public static @Nullable Component upgradeProblem(ServerPlayer player, Guild guild) {
		if (GuildManager.get(guild.id) != guild || GuildManager.of(player) != guild) {
			return Lang.get("error.no_guild");
		}
		if (!guild.can(player.getUUID(), GuildPermission.TREASURY)) {
			return Lang.get("error.no_permission");
		}
		if (hub(guild) == null) {
			return Lang.get("trade.upgrade.no_hub");
		}
		TradeConfig cfg = TradeModule.config();
		int tier = tier(guild);
		if (tier >= cfg.maxTier()) {
			return Lang.get("trade.upgrade.max");
		}
		TradeConfig.Tier next = cfg.tier(tier + 1);
		if (guild.level < next.minLevel) {
			return Lang.get("trade.upgrade.level", next.minLevel, guild.level);
		}
		if (player.isCreative() && !next.costItems.isEmpty()) {
			return Lang.get("trade.upgrade.creative");
		}
		MutableComponent missing = Component.empty();
		boolean any = false;
		for (TradeConfig.Cost cost : next.costItems) {
			int have = owned(player, cost.item);
			if (have < cost.count) {
				Item item = ItemUtil.item(cost.item);
				if (any) {
					missing.append(Component.literal(", "));
				}
				missing.append(TextUtil.lang("trade.upgrade.items.entry", cost.count - have, item == null ? cost.item : ItemUtil.name(item)));
				any = true;
			}
		}
		if (any) {
			return TextUtil.lang("trade.upgrade.items", missing);
		}
		if (guild.treasury < next.costGold) {
			return Lang.get("error.not_enough_treasury", next.costGold);
		}
		return null;
	}

	/** Upgrades the hub one tier: items taken first, then the treasury gold (items refunded if that fails). @return the error or null */
	public static @Nullable Component upgrade(ServerPlayer player, Guild guild) {
		Component problem = upgradeProblem(player, guild);
		if (problem != null) {
			return problem;
		}
		TradeConfig.Tier next = TradeModule.config().tier(tier(guild) + 1);
		List<Taken> taken = new ArrayList<>();
		for (TradeConfig.Cost cost : next.costItems) {
			Item item = ItemUtil.item(cost.item);
			if (item == null || !PlainItems.remove(player, stack -> stack.is(item), cost.count)) {
				refund(player, taken);
				return Lang.get("trade.upgrade.payment");
			}
			taken.add(new Taken(item, cost.count));
		}
		if (!GuildManager.takeTreasury(guild, next.costGold)) {
			refund(player, taken);
			return Lang.get("error.not_enough_treasury", next.costGold);
		}
		guild.trade.tier = tier(guild) + 1;
		GuildManager.markDirty();
		int tier = guild.trade.tier;
		Msg.guild(guild, "trade.upgrade.done", player.getName().getString(), roman(tier), Lang.colorize(next.name));
		Msg.guildTitle(guild, Lang.get("trade.upgrade.title"), Lang.get("trade.upgrade.subtitle", roman(tier), Lang.colorize(next.name)));
		Msg.guildSound(guild, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.2F);
		StructureManager.requestSignRefresh(guild);
		return null;
	}

	/** Admin: sets the tier directly. @return the tier applied */
	public static int setTier(Guild guild, int tier) {
		guild.trade.tier = Math.max(1, Math.min(tier, TradeModule.config().maxTier()));
		GuildManager.markDirty();
		StructureManager.requestSignRefresh(guild);
		return guild.trade.tier;
	}

	private static void refund(ServerPlayer player, List<Taken> taken) {
		for (Taken entry : taken) {
			ItemUtil.giveOrDrop(player, entry.item(), entry.count());
		}
	}

	// ================================================================== structure & visitor kind

	private static final class Behavior implements StructureBehavior {
		@Override
		public void onUse(ServerPlayer player, Guild guild, BuiltStructure structure, int signIndex) {
			TradeHubMenu.open(player, guild);
		}

		@Override
		public List<Component> signText(Guild guild, BuiltStructure structure, int signIndex) {
			String status;
			if (guild.trade.visitor(KIND) != null) {
				status = "trade.sign.present";
			} else if (Merchants.isPending(guild, KIND)) {
				status = "trade.sign.pending";
			} else {
				status = "trade.sign.none";
			}
			return List.of(Lang.get("trade.sign.title"), Lang.get("trade.sign.tier", roman(tier(guild))), Lang.get(status),
					Lang.get("trade.sign.hint"));
		}
	}

	/** The "Marchand de passage" of the trade hub. */
	private static final class HubMerchant implements Merchants.Kind {
		@Override
		public String id() {
			return KIND;
		}

		@Override
		public String name(Guild guild) {
			return Lang.raw("trade.merchant.name", roman(tier(guild)));
		}

		@Override
		public List<TradeEntry> offers(Guild guild, RandomGenerator random) {
			TradeConfig cfg = TradeModule.config();
			int tier = tier(guild);
			TradeConfig.Tier def = cfg.tier(tier);
			int count = TradeRolls.count(def.offersMin, def.offersMax, random);
			return TradeRolls.rollTiered(cfg.tables(), tier, count, cfg.visits.ownTierWeight, cfg.visits.lowerTierWeight, random);
		}

		@Override
		public @Nullable BlockPos target(Guild guild) {
			return arrivalPoint(guild);
		}

		@Override
		public int staySeconds() {
			return TradeModule.config().visits.staySeconds;
		}

		@Override
		public int llamas() {
			return 0;
		}

		@Override
		public String langPrefix() {
			return "trade.merchant.arrive";
		}

		@Override
		public boolean stillWanted(Guild guild) {
			return hub(guild) != null;
		}
	}
}
