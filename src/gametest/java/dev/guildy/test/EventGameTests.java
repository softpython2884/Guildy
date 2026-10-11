package dev.guildy.test;

import dev.guildy.core.GuildyItems;
import dev.guildy.core.ItemUtil;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildRole;
import dev.guildy.event.EventConfig;
import dev.guildy.event.EventModule;
import dev.guildy.gui.GuildGui;
import dev.guildy.structure.BuiltStructure;
import dev.guildy.structure.StructureManager;
import dev.guildy.trade.GuildTradeData;
import dev.guildy.trade.Merchants;
import dev.guildy.trade.TradeConfig;
import dev.guildy.trade.TradeEntry;
import dev.guildy.trade.TradeHub;
import dev.guildy.trade.TradeModule;
import dev.guildy.trade.TradeOffers;
import dev.guildy.wave.WaveApi;
import dev.guildy.wave.WaveFormula;
import dev.guildy.wave.WaveManager;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Events and trade hub scenarios: a forced merchant visit at a freshly built trade hub (Guildy offers paid in gold,
 * tier upgrade), a blood night making a horde bigger, a pillager raid by day (event wave of illagers).
 */
public class EventGameTests {

	@GameTest(maxTicks = 400)
	public void tradeHubMerchantVisit(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TestSupport.console(level, "time set 1000"); // visits are daytime affairs
		BlockPos ground = TestSupport.region(level, 10);
		TestSupport.forceChunks(level, ground, 2);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Trade");
		GuildManager.setLevel(guild, 2);

		// Every default offer resolves in the game registries (items, enchantments).
		TradeConfig cfg = TradeModule.config();
		EventConfig events = EventModule.config();
		List<TradeEntry> all = new ArrayList<>();
		for (TradeConfig.Tier tier : cfg.tiers) {
			helper.assertTrue(tier.usable().size() == tier.trades.size(), "tier '" + tier.name + "': every default trade is valid");
			all.addAll(tier.usable());
		}
		helper.assertTrue(events.caravan.usable().size() == events.caravan.trades.size(), "every caravan trade is valid");
		helper.assertTrue(events.bookMerchant.usable().size() == events.bookMerchant.books.size(), "every book is valid");
		all.addAll(events.caravan.usable());
		all.addAll(events.bookMerchant.usable());
		for (TradeEntry entry : all) {
			helper.assertTrue(TradeOffers.offer(level.registryAccess(), entry) != null, "merchant offer for " + entry);
		}

		// Trade hub "WEW / BTB" at z+4, front facing north (sign on the emerald block at z+3).
		TestSupport.console(level, "guild admin structure unlock " + guild.name + " trade_hub");
		BlockState wool = Blocks.WOOL.pick(DyeColor.WHITE).defaultBlockState();
		BlockState barrel = Blocks.BARREL.defaultBlockState();
		wall(level, ground.offset(0, 0, 4), new BlockState[][]{
				{wool, Blocks.EMERALD_BLOCK.defaultBlockState(), wool},
				{barrel, Blocks.CARTOGRAPHY_TABLE.defaultBlockState(), barrel}});
		validate(leader, level, ground.offset(0, 1, 4), Direction.NORTH, TradeHub.STRUCTURE);
		helper.assertTrue(StructureManager.isBuilt(guild, TradeHub.STRUCTURE), "trade hub validated");

		// Merchants only arrive in entity-ticking chunks: the forced chunks are ticking a moment later.
		helper.runAfterDelay(20, () -> {
			try {
				visitScenario(helper, level, ground, leader, guild, cfg);
			} catch (ReflectiveOperationException e) {
				helper.fail("reflection failed: " + e);
			}
		});
	}

	private static void visitScenario(GameTestHelper helper, ServerLevel level, BlockPos ground, ServerPlayer leader, Guild guild,
			TradeConfig cfg) throws ReflectiveOperationException {
		BuiltStructure hub = TradeHub.hub(guild);
		// Forced visit: a tagged wandering trader with Guildy offers (tier 1 table, paid in gold).
		TestSupport.console(level, "guild admin event start merchant " + guild.name);
		GuildTradeData.Visitor visitor = guild.trade.visitor(TradeHub.KIND);
		helper.assertTrue(visitor != null, "a merchant arrived");
		Entity entity = level.getEntity(visitor.trader);
		helper.assertTrue(entity instanceof WanderingTrader, "the merchant is a wandering trader");
		WanderingTrader trader = (WanderingTrader) entity;
		helper.assertTrue(trader.entityTags().contains(Merchants.TAG), "merchant tagged");
		helper.assertTrue(trader.getOffers().size() >= cfg.tier(1).offersMin, "offers rolled: " + trader.getOffers().size());
		Set<String> tierOne = cfg.tier(1).usable().stream().map(e -> e.item).collect(Collectors.toSet());
		for (MerchantOffer offer : trader.getOffers()) {
			ItemStack cost = offer.getCostA();
			helper.assertTrue(cost.is(Items.GOLD_INGOT) || cost.is(Items.GOLD_BLOCK), "paid in gold, got " + cost);
			helper.assertTrue(tierOne.contains(ItemUtil.id(offer.getResult().getItem())), "tier 1 offer, got " + offer.getResult());
		}

		// Menus render with a hub and a merchant: hub menu (command) and the preview of every tier.
		leader.closeContainer();
		level.getServer().getCommands().performPrefixedCommand(leader.createCommandSourceStack(), "guild trade");
		helper.assertTrue(leader.containerMenu != leader.inventoryMenu, "trade hub menu open");
		Constructor<?> preview = Class.forName("dev.guildy.trade.TradePreviewMenu")
				.getDeclaredConstructor(ServerPlayer.class, Guild.class, int.class, int.class);
		preview.setAccessible(true);
		for (int t = 1; t <= cfg.maxTier(); t++) {
			leader.closeContainer();
			((GuildGui) preview.newInstance(leader, guild, t, 0)).show();
			helper.assertTrue(leader.containerMenu != leader.inventoryMenu, "preview of tier " + t + " open");
		}
		leader.closeContainer();

		// Tier upgrade: 150 treasury gold + 16 emeralds of the (survival) officer who clicks, atomic.
		GuildManager.setLevel(guild, 3);
		ServerPlayer officer = TestSupport.survivor(helper, ground.east(2));
		GuildManager.addMember(guild, officer.getUUID(), officer.getName().getString());
		GuildManager.setRole(guild, officer.getUUID(), GuildRole.OFFICER);
		GuildManager.addTreasury(guild, 150);
		long treasury = guild.treasury;
		helper.assertTrue(TradeHub.upgrade(officer, guild) != null, "refused without emeralds");
		helper.assertTrue(guild.trade.tier == 1 && guild.treasury == treasury, "nothing taken on refusal");
		officer.getInventory().add(new ItemStack(Items.EMERALD, 16));
		helper.assertTrue(TradeHub.upgrade(officer, guild) == null, "hub upgraded");
		helper.assertTrue(guild.trade.tier == 2, "tier 2, got " + guild.trade.tier);
		helper.assertTrue(guild.treasury == treasury - 150 && ItemUtil.count(officer, Items.EMERALD) == 0, "150 gold and 16 emeralds paid");

		// Then a natural visit: won in the morning (scheduled), it arrives through the pending tick of the merchants.
		boolean[] scheduled = {false};
		helper.succeedWhen(() -> {
			if (!scheduled[0]) {
				SignBlockEntity sign = (SignBlockEntity) level.getBlockEntity(hub.signs.getFirst());
				String text = sign.getText(SignTextSlot.FRONT).getMessages(false).stream().map(Component::getString)
						.collect(Collectors.joining("|"));
				helper.assertTrue(text.contains("II"), "waiting for the sign to show tier II: " + text);
				TestSupport.console(level, "guild admin event stop merchant " + guild.name);
				helper.assertTrue(guild.trade.visitor(TradeHub.KIND) == null && trader.isRemoved(), "merchant sent away");
				Merchants.schedule(guild, TradeHub.KIND, WaveFormula.dayIndex(level.getOverworldClockTime()));
				scheduled[0] = true;
			}
			GuildTradeData.Visitor arrived = guild.trade.visitor(TradeHub.KIND);
			helper.assertTrue(arrived != null, "waiting for the scheduled merchant to arrive");
			helper.assertTrue(!guild.trade.pending.containsKey(TradeHub.KIND), "pending visit consumed");
			helper.assertTrue(arrived.name.contains("II"), "merchant named after the hub tier: " + arrived.name);
			TestSupport.console(level, "guild admin event stop merchant " + guild.name);
			GuildManager.disband(guild);
		});
	}

	/** Same guild, same difficulty: the blood night horde has more mobs than the normal one. */
	@GameTest(maxTicks = 200)
	public void bloodNightMakesBiggerHordes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 24);
		TestSupport.forceChunks(level, ground, 3);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Blood");

		TestSupport.console(level, "guild admin wave start " + guild.name);
		WaveApi.Info normal = WaveApi.info(guild);
		helper.assertTrue(normal != null && !normal.modifiers().hasLabel(), "normal test wave started");
		TestSupport.console(level, "guild admin wave stop " + guild.name);

		TestSupport.console(level, "guild admin event start blood_night");
		TestSupport.console(level, "guild admin wave start " + guild.name);
		WaveApi.Info blood = WaveApi.info(guild);
		TestSupport.console(level, "guild admin wave stop " + guild.name);
		TestSupport.console(level, "guild admin event stop blood_night");
		helper.assertTrue(blood != null && blood.modifiers().hasLabel(), "blood night modifiers applied to the wave");
		helper.assertTrue(blood.total() > normal.total(), "blood night horde " + blood.total() + " > normal " + normal.total());
		helper.assertTrue(blood.modifiers().goldMultiplier() > 1, "rewards multiplied");
		GuildManager.disband(guild);
		helper.succeed();
	}

	/** A pillager raid forced by day with a defender at the heart: an event wave made of illagers. */
	@GameTest(maxTicks = 900)
	public void pillagerRaidByDay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TestSupport.console(level, "time set 1000");
		BlockPos ground = TestSupport.region(level, 56);
		TestSupport.forceChunks(level, ground, 4);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Raid");
		GuildManager.setLevel(guild, 2);

		TestSupport.console(level, "guild admin event start pillager_raid " + guild.name);
		WaveApi.Info info = WaveApi.info(guild);
		helper.assertTrue(info != null && "pillager_raid".equals(info.eventId()), "raid wave started");
		helper.assertTrue(guild.waves.lastWaveDay == -1, "a raid never blocks the night horde");

		helper.succeedWhen(() -> {
			List<Mob> horde = hordeAround(level, guild.heart);
			helper.assertTrue(!horde.isEmpty(), "waiting for the pillagers to spawn");
			List<String> others = new ArrayList<>(new HashSet<>(horde.stream().filter(mob -> !(mob instanceof Raider))
					.map(mob -> mob.getType().toShortString()).toList()));
			helper.assertTrue(others.isEmpty(), "only illagers in the raid, got " + others);
			TestSupport.console(level, "guild admin event stop pillager_raid " + guild.name);
			helper.assertFalse(WaveManager.isActive(guild), "raid stopped");
			GuildManager.disband(guild);
		});
	}

	// ---------------------------------------------------------------- helpers

	private static List<Mob> hordeAround(ServerLevel level, BlockPos heart) {
		AABB box = new AABB(heart).inflate(96, 128, 96);
		return level.getEntities(EntityTypeTest.forClass(Mob.class), box, mob -> mob.entityTags().contains("guildy_wave"));
	}

	/** Places rows (top to bottom, left to right as seen from the north side) along x, standing on {@code base}. */
	private static void wall(ServerLevel level, BlockPos base, BlockState[][] rows) {
		int height = rows.length;
		for (int r = 0; r < height; r++) {
			for (int c = 0; c < rows[r].length; c++) {
				level.setBlockAndUpdate(base.offset(c - 1, height - r, 0), rows[r][c]);
			}
		}
	}

	private static void validate(ServerPlayer player, ServerLevel level, BlockPos clicked, Direction face, String structure) {
		player.setItemInHand(InteractionHand.MAIN_HAND, GuildyItems.plan(structure, Component.literal(structure), List.of()));
		Vec3 hitPos = Vec3.atCenterOf(clicked).relative(face, 0.5);
		UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, new BlockHitResult(hitPos, face, clicked, false));
	}
}
