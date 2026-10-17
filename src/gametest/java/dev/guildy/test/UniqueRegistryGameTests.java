package dev.guildy.test;

import dev.guildy.core.Hooks;
import dev.guildy.core.UniqueDrops;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.unique.UniqueConfig;
import dev.guildy.unique.UniqueItems;
import dev.guildy.unique.UniqueLedger;
import dev.guildy.unique.UniqueModule;
import dev.guildy.unique.UniqueRegistry;
import dev.guildy.unique.UniquenessMode;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Uniqueness of the unique weapons through the real registry: a guild never rolls a weapon it holds (another guild can,
 * the holder follows the weapon), "server" mode keeps one copy, destroyed or broken copies free their id, unregistered
 * stacks (legacy items, bought offers) get a serial, duplicates are reported, the collection menu opens.
 */
public class UniqueRegistryGameTests {
	/** nextDouble() == 0: every chance passes, the first entry of a table is picked. */
	private static final RandomGenerator LUCKY = () -> 0L;

	private static UniqueConfig.DropTable table(String... ids) {
		UniqueConfig.DropTable table = new UniqueConfig.DropTable();
		table.chance = 1;
		for (String id : ids) {
			table.items.put(id, 1.0);
		}
		return table;
	}

	private static ItemStack roll(UniqueConfig cfg, UniqueConfig.DropTable table, String source, UniqueDrops.Recipient recipient,
			UniquenessMode mode, boolean fallback) {
		return UniqueModule.rollWith(cfg, table, source, LUCKY, recipient, mode, fallback);
	}

	private static UniqueLedger ledger() {
		return UniqueRegistry.ledger();
	}

	private static UniqueLedger.@Nullable Copy copy(ItemStack stack) {
		long serial = UniqueItems.serialOf(stack);
		return serial <= 0 ? null : ledger().copy(serial);
	}

	private static @Nullable ItemStack find(ServerPlayer player, String id) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (id.equals(UniqueItems.idOf(stack))) {
				return stack;
			}
		}
		return null;
	}

	/** Guild mode: the Dragons hold Foudrelame, so their rolls give Croc-de-Givre; the Wolves can roll Foudrelame. */
	@GameTest(maxTicks = 200)
	public void guildModeNeverRollsAHeldWeapon(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 3);
		ServerPlayer steve = TestSupport.survivor(helper, ground);
		ServerPlayer alex = TestSupport.survivor(helper, ground.east(2));
		Guild dragons = GuildManager.create(TestSupport.uniqueName("UqDragons"), steve);
		Guild wolves = GuildManager.create(TestSupport.uniqueName("UqWolves"), alex);
		UniqueDrops.Recipient forDragons = UniqueDrops.Recipient.of(steve, dragons);
		UniqueDrops.Recipient forWolves = UniqueDrops.Recipient.of(alex, wolves);
		UniqueConfig cfg = UniqueModule.config();
		UniqueConfig.DropTable pair = table("foudrelame", "croc_de_givre");

		ItemStack blade = roll(cfg, pair, "boss", forDragons, UniquenessMode.GUILD, false);
		helper.assertTrue("foudrelame".equals(UniqueItems.idOf(blade)), "first roll: Foudrelame, got " + blade);
		UniqueLedger.Copy bladeCopy = copy(blade);
		helper.assertTrue(bladeCopy != null && bladeCopy.active() && dragons.id.equals(bladeCopy.guild), "registered for the Dragons");
		steve.getInventory().add(blade);

		ItemStack croc = roll(cfg, pair, "boss", forDragons, UniquenessMode.GUILD, false);
		helper.assertTrue("croc_de_givre".equals(UniqueItems.idOf(croc)), "held Foudrelame skipped, got " + croc);
		helper.assertTrue(roll(cfg, pair, "boss", forDragons, UniquenessMode.GUILD, false).isEmpty(), "both held, no fallback: nothing");
		ItemStack scroll = roll(cfg, pair, "boss", forDragons, UniquenessMode.GUILD, true);
		helper.assertTrue(!scroll.isEmpty() && !UniqueDrops.isUnique(scroll), "both held: a spell scroll instead, got " + scroll);
		ItemStack unlimited = roll(cfg, pair, "boss", forDragons, UniquenessMode.NONE, false);
		helper.assertTrue("foudrelame".equals(UniqueItems.idOf(unlimited)), "none mode: no limit, got " + unlimited);
		ledger().markLost(UniqueItems.serialOf(unlimited), "admin", System.currentTimeMillis());

		ItemStack other = roll(cfg, pair, "boss", forWolves, UniquenessMode.GUILD, false);
		helper.assertTrue("foudrelame".equals(UniqueItems.idOf(other)), "another guild can roll Foudrelame, got " + other);

		// A merchant visiting the Dragons never offers a weapon they hold (default uniqueness: guild).
		helper.assertTrue(UniqueDrops.offer("trade", "croc_de_givre", forDragons).isEmpty(), "held weapon not offered");
		helper.assertTrue(UniqueItems.isUnique(UniqueDrops.offer("trade", "croc_de_givre", forWolves)), "offered to the Wolves");

		// Steve gives his Foudrelame to Alex: the Wolves hold it now, the Dragons may roll it again.
		ItemStack given = find(steve, "foudrelame");
		helper.assertTrue(given != null, "Steve has Foudrelame");
		long serial = UniqueItems.serialOf(given);
		steve.getInventory().removeItem(given);
		alex.getInventory().add(given);
		helper.succeedWhen(() -> {
			UniqueLedger.Copy moved = ledger().copy(serial);
			helper.assertTrue(moved != null && wolves.id.equals(moved.guild) && alex.getUUID().equals(moved.player),
					"waiting for the scan to move the holder");
			helper.assertTrue(!ledger().heldBy(dragons.id, null).contains("foudrelame"), "the Dragons no longer hold it");
			ItemStack again = roll(cfg, pair, "boss", forDragons, UniquenessMode.GUILD, false);
			helper.assertTrue("foudrelame".equals(UniqueItems.idOf(again)), "the Dragons may roll it again, got " + again);
			helper.assertTrue(ledger().obtainedBy(dragons.id).containsAll(List.of("foudrelame", "croc_de_givre")), "collection");

			steve.closeContainer();
			Hooks.openUniques.accept(steve, dragons);
			helper.assertTrue(steve.containerMenu != steve.inventoryMenu, "collection menu open");
			boolean knownShown = false;
			boolean unknownShown = false;
			for (int slot = 0; slot < 54; slot++) {
				ItemStack shown = steve.containerMenu.getSlot(slot).getItem();
				knownShown |= shown.is(Items.NETHERITE_SWORD) && shown.getHoverName().getString().contains("Foudrelame");
				unknownShown |= shown.is(Items.FIREWORK_STAR) && shown.getHoverName().getString().contains("???");
				helper.assertTrue(UniqueItems.idOf(shown) == null, "menu icons are never real copies");
			}
			helper.assertTrue(knownShown && unknownShown, "obtained weapons shown, the others as ???");
			steve.closeContainer();
			GuildManager.disband(dragons);
			GuildManager.disband(wolves);
		});
	}

	/** Server mode: one copy of each weapon on the whole server, available again once freed. */
	@GameTest(maxTicks = 60)
	public void serverModeKeepsOneCopy(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 2);
		ServerPlayer steve = TestSupport.survivor(helper, ground);
		ServerPlayer alex = TestSupport.survivor(helper, ground.east(2));
		Guild dragons = GuildManager.create(TestSupport.uniqueName("UqSrvA"), steve);
		Guild wolves = GuildManager.create(TestSupport.uniqueName("UqSrvB"), alex);
		// Weapons of this test only (the other tests never make them active).
		UniqueConfig cfg = testConfig("essai_alpha", "essai_beta");
		UniqueConfig.DropTable pair = table("essai_alpha", "essai_beta");
		UniqueDrops.Recipient forDragons = UniqueDrops.Recipient.of(steve, dragons);
		UniqueDrops.Recipient forWolves = UniqueDrops.Recipient.of(alex, wolves);

		ItemStack alpha = roll(cfg, pair, "essai", forDragons, UniquenessMode.SERVER, false);
		helper.assertTrue("essai_alpha".equals(UniqueItems.idOf(alpha)), "first copy of alpha, got " + alpha);
		ItemStack beta = roll(cfg, pair, "essai", forWolves, UniquenessMode.SERVER, false);
		helper.assertTrue("essai_beta".equals(UniqueItems.idOf(beta)), "alpha exists (held by another guild): beta, got " + beta);
		helper.assertTrue(roll(cfg, pair, "essai", forWolves, UniquenessMode.SERVER, false).isEmpty(), "both exist: nothing left");
		helper.assertTrue("essai_alpha".equals(UniqueItems.idOf(roll(cfg, pair, "essai", forWolves, UniquenessMode.GUILD, false))),
				"guild mode would give alpha to the Wolves");

		TestSupport.console(level, "guild admin unique free essai_alpha");
		helper.assertTrue(!ledger().activeIds().contains("essai_alpha"), "alpha freed by the admin command");
		ItemStack again = roll(cfg, pair, "essai", forWolves, UniquenessMode.SERVER, false);
		helper.assertTrue("essai_alpha".equals(UniqueItems.idOf(again)), "alpha available again, got " + again);
		TestSupport.console(level, "guild admin unique registry essai_alpha");
		ledger().free("essai_alpha", null, "admin", System.currentTimeMillis());
		ledger().free("essai_beta", null, "admin", System.currentTimeMillis());
		GuildManager.disband(dragons);
		GuildManager.disband(wolves);
		helper.succeed();
	}

	private static UniqueConfig testConfig(String... ids) {
		UniqueConfig cfg = new UniqueConfig();
		cfg.items = new ArrayList<>();
		for (String id : ids) {
			UniqueConfig.UniqueDef def = new UniqueConfig.UniqueDef();
			def.id = id;
			def.item = "minecraft:iron_sword";
			def.name = "Essai " + id;
			def.lore.add("Arme d'essai des GameTests.");
			cfg.items.add(def);
		}
		cfg.drops = new LinkedHashMap<>();
		cfg.rebuild(id -> true);
		return cfg;
	}

	/** A unique weapon burnt in lava: its copy is lost, the guild may roll it again. */
	@GameTest(maxTicks = 300)
	public void itemBurntInLavaFreesTheId(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 4);
		TestSupport.forceChunks(level, ground, 1);
		ServerPlayer steve = TestSupport.survivor(helper, ground.west(3));
		Guild dragons = GuildManager.create(TestSupport.uniqueName("UqLava"), steve);
		BlockPos pit = ground.east(3);
		level.setBlockAndUpdate(pit, Blocks.LAVA.defaultBlockState());
		// Croc-de-Givre: a diamond sword (netherite items float on lava).
		ItemStack sword = roll(UniqueModule.config(), table("croc_de_givre"), "boss", UniqueDrops.Recipient.of(steve, dragons),
				UniquenessMode.GUILD, false);
		long serial = UniqueItems.serialOf(sword);
		helper.assertTrue(serial > 0 && ledger().heldBy(dragons.id, null).contains("croc_de_givre"), "held by the Dragons");
		ItemEntity entity = new ItemEntity(level, pit.getX() + 0.5, pit.getY() + 1.2, pit.getZ() + 0.5, sword, 0, 0, 0);
		level.addFreshEntity(entity);
		helper.succeedWhen(() -> {
			helper.assertTrue(entity.isRemoved(), "waiting for the sword to burn");
			UniqueLedger.Copy copy = ledger().copy(serial);
			helper.assertTrue(copy != null && copy.state == UniqueLedger.State.LOST && "destroyed".equals(copy.lostReason),
					"waiting for the loss to be confirmed");
			helper.assertTrue(!ledger().heldBy(dragons.id, null).contains("croc_de_givre"), "the id is free again");
			level.setBlockAndUpdate(pit, Blocks.STONE.defaultBlockState());
			GuildManager.disband(dragons);
		});
	}

	/** A breakable unique weapon that breaks frees its id at once. */
	@GameTest(maxTicks = 40)
	public void brokenWeaponFreesTheId(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 2);
		ServerPlayer steve = TestSupport.survivor(helper, ground);
		Guild dragons = GuildManager.create(TestSupport.uniqueName("UqBreak"), steve);
		ItemStack pick = roll(UniqueModule.config(), table("pic_du_filon"), "boss", UniqueDrops.Recipient.of(steve, dragons),
				UniquenessMode.GUILD, false);
		long serial = UniqueItems.serialOf(pick);
		helper.assertTrue(serial > 0 && pick.isDamageableItem(), "a breakable registered pickaxe");
		pick.setDamageValue(pick.getMaxDamage() - 1);
		// Unbreaking may spare a hit: hit until it breaks.
		for (int i = 0; i < 200 && !pick.isEmpty(); i++) {
			pick.hurtAndBreak(1, level, steve, item -> {
			});
		}
		helper.assertTrue(pick.isEmpty(), "the pickaxe broke");
		UniqueLedger.Copy copy = ledger().copy(serial);
		helper.assertTrue(copy != null && copy.state == UniqueLedger.State.LOST && "broken".equals(copy.lostReason), "lost when broken");
		GuildManager.disband(dragons);
		helper.succeed();
	}

	/**
	 * Unregistered stacks get a serial from the inventory scan: a legacy weapon (held by the carrier's guild) and a bought
	 * merchant offer (source kept). The same serial in two inventories is reported as a duplicate.
	 */
	@GameTest(maxTicks = 200)
	public void legacyStacksGetASerial(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 2);
		ServerPlayer steve = TestSupport.survivor(helper, ground);
		ServerPlayer alex = TestSupport.survivor(helper, ground.east(2));
		Guild dragons = GuildManager.create(TestSupport.uniqueName("UqLegacy"), steve);
		ItemStack legacy = UniqueDrops.create("pic_du_filon");
		helper.assertTrue(UniqueItems.isUnique(legacy) && UniqueItems.serialOf(legacy) == 0, "an unregistered unique");
		steve.getInventory().add(legacy);
		ItemStack offered = UniqueDrops.offer("trade", "bottes_du_coursier", UniqueDrops.Recipient.guild(dragons));
		helper.assertTrue(UniqueItems.isUnique(offered) && UniqueItems.serialOf(offered) == 0, "an offer is not registered");
		steve.getInventory().add(offered);
		long[] serial = new long[1];
		helper.succeedWhen(() -> {
			if (serial[0] == 0) {
				ItemStack found = find(steve, "pic_du_filon");
				helper.assertTrue(found != null && UniqueItems.serialOf(found) > 0, "waiting for the scan to give a serial");
				UniqueLedger.Copy copy = ledger().copy(UniqueItems.serialOf(found));
				helper.assertTrue(copy != null && copy.active() && dragons.id.equals(copy.guild) && steve.getUUID().equals(copy.player)
						&& UniqueLedger.LEGACY.equals(copy.source), "legacy copy registered for its carrier's guild");
				ItemStack boots = find(steve, "bottes_du_coursier");
				UniqueLedger.Copy bought = boots == null ? null : copy(boots);
				helper.assertTrue(bought != null && "trade".equals(bought.source) && UniqueItems.pendingSource(boots).isEmpty(),
						"the bought offer keeps its source");
				// A creative-style copy of the registered pickaxe in Alex's inventory.
				serial[0] = copy.serial;
				alex.getInventory().add(found.copy());
			}
			helper.assertTrue(ledger().duplicates().stream().anyMatch(d -> d.serial == serial[0]), "waiting for the duplicate report");
			helper.assertTrue(find(alex, "pic_du_filon") != null && find(steve, "pic_du_filon") != null, "duplicates are never deleted");
			TestSupport.console(level, "guild admin unique registry");
			alex.getInventory().clearContent();
			ledger().clearDuplicates(serial[0]);
			GuildManager.disband(dragons);
		});
	}
}
