package dev.guildy.test;

import dev.guildy.core.GoldUtil;
import dev.guildy.core.GuildyItems;
import dev.guildy.core.ItemUtil;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.quest.GuildQuestData;
import dev.guildy.shop.ShopCategory;
import dev.guildy.shop.ShopEntry;
import dev.guildy.shop.ShopModule;
import dev.guildy.structure.BuiltStructure;
import dev.guildy.structure.StructureManager;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Structures scenario: build the quest board and the shop counter, validate them with their plans
 * (6 managed signs + 6 quests), then buy and sell at the shop with a survival player.
 */
public class StructureGameTests {

	@GameTest(maxTicks = 400)
	public void questBoardAndShop(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 10);
		TestSupport.forceChunks(level, ground, 2);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Struct");

		// Quest board "LBL / LPL" at z+4, front facing north (signs at z+3).
		BlockState log = Blocks.OAK_LOG.defaultBlockState();
		wall(level, ground.offset(0, 0, 4), new BlockState[][]{
				{log, Blocks.BOOKSHELF.defaultBlockState(), log},
				{log, Blocks.OAK_PLANKS.defaultBlockState(), log}});
		validate(leader, level, ground.offset(0, 1, 4), Direction.NORTH, "quest_board");
		helper.assertTrue(StructureManager.isBuilt(guild, "quest_board"), "quest board validated");
		BuiltStructure board = StructureManager.builtOf(guild, "quest_board").getFirst();
		helper.assertTrue(board.signs.size() == 6, "6 managed signs, got " + board.signs.size());
		for (BlockPos sign : board.signs) {
			helper.assertTrue(level.getBlockEntity(sign) instanceof SignBlockEntity, "sign placed at " + sign.toShortString());
		}
		helper.assertTrue(guild.quests.active.size() == GuildQuestData.SLOTS, "6 quests generated, got " + guild.quests.active.size());

		// Next plan click a few ticks later (structure clicks are debounced per player, like a real player).
		helper.runAfterDelay(10, () -> {
			try {
				shopScenario(helper, level, ground, leader, guild);
			} catch (ReflectiveOperationException e) {
				helper.fail("reflection failed: " + e);
			}
			helper.succeedWhen(() -> {
				SignBlockEntity sign = (SignBlockEntity) level.getBlockEntity(board.signs.getFirst());
				Component line = sign.getText(SignTextSlot.FRONT).getMessages(false).getFirst();
				helper.assertFalse(line.getString().isBlank(), "waiting for the quest board signs to show quests");
				GuildManager.disband(guild);
			});
		});
	}

	private static void shopScenario(GameTestHelper helper, ServerLevel level, BlockPos ground, ServerPlayer leader, Guild guild)
			throws ReflectiveOperationException {
		// Shop "PGP / BBB" at z-4, front facing south (sign on the gold block at z-3).
		TestSupport.console(level, "guild admin structure unlock " + guild.name + " shop");
		BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
		BlockState barrel = Blocks.BARREL.defaultBlockState();
		wall(level, ground.offset(0, 0, -4), new BlockState[][]{
				{planks, Blocks.GOLD_BLOCK.defaultBlockState(), planks},
				{barrel, barrel, barrel}});
		validate(leader, level, ground.offset(0, 1, -4), Direction.SOUTH, "shop");
		helper.assertTrue(StructureManager.isBuilt(guild, "shop"), "shop validated");

		// Buy then sell one lot with a survival player; creative players cannot sell.
		ShopEntry entry = tradableEntry();
		Item item = ItemUtil.item(entry.item);
		ServerPlayer buyer = TestSupport.survivor(helper, ground.offset(0, 0, -2));
		ItemUtil.giveOrDrop(buyer, Items.GOLD_INGOT, 64);
		Class<?> tx = Class.forName("dev.guildy.shop.ShopTransactions");
		Method buy = tx.getDeclaredMethod("buy", ServerPlayer.class, Guild.class, ShopEntry.class, int.class);
		Method sell = tx.getDeclaredMethod("sell", ServerPlayer.class, Guild.class, ShopEntry.class, boolean.class);
		buy.setAccessible(true);
		sell.setAccessible(true);
		helper.assertTrue(success(buy.invoke(null, buyer, guild, entry, 1)), "buy succeeds");
		helper.assertTrue(ItemUtil.count(buyer, item) == entry.amount, "bought lot received");
		helper.assertTrue(GoldUtil.count(buyer) == 64 - entry.buy, "paid " + entry.buy + " gold");
		helper.assertTrue(success(sell.invoke(null, buyer, guild, entry, false)), "sell succeeds");
		helper.assertTrue(ItemUtil.count(buyer, item) == 0, "sold lot removed");
		helper.assertTrue(GoldUtil.count(buyer) == 64 - entry.buy + entry.sell, "received " + entry.sell + " gold");
		leader.getInventory().add(new ItemStack(item, entry.amount));
		helper.assertFalse(success(sell.invoke(null, leader, guild, entry, false)), "creative players cannot sell");
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

	private static ShopEntry tradableEntry() {
		for (ShopCategory category : ShopModule.config().categories) {
			for (ShopEntry entry : category.entries) {
				if (entry.buy > 0 && entry.sell > 0 && entry.minLevel <= 1 && ItemUtil.item(entry.item) != null) {
					return entry;
				}
			}
		}
		throw new IllegalStateException("no entry both buyable and sellable at level 1");
	}

	private static boolean success(Object result) throws ReflectiveOperationException {
		Method accessor = result.getClass().getDeclaredMethod("success");
		accessor.setAccessible(true);
		return (boolean) accessor.invoke(result);
	}
}
