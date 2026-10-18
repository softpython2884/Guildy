package dev.guildy.test;

import dev.guildy.core.GuildyItems;
import dev.guildy.core.ItemUtil;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildRole;
import dev.guildy.ruin.Cartography;
import dev.guildy.ruin.GuildRuinData;
import dev.guildy.ruin.RuinIndex;
import dev.guildy.ruin.RuinItems;
import dev.guildy.ruin.RuinRecord;
import dev.guildy.ruin.Sanctuaries;
import dev.guildy.ruin.StatueGuide;
import dev.guildy.ruin.StatueRecord;
import dev.guildy.ruin.StatueSettings;
import dev.guildy.ruin.StatueTrail;
import dev.guildy.ruin.Statues;
import dev.guildy.structure.StructureManager;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The legend of the celestial jaguar: the research leads to a stubbed grand temple (the GameTest world generates no
 * structure; a generated, unplaced test ruin start stands for it) through a trail of jaguar statues: the statue map shows
 * the statues and never the temple, the surface research no longer finds the grand temple, a pending statue is placed
 * when its chunks are loaded on natural ground and never in a claimed chunk, and a player who comes near a placed statue
 * gets its guidance (stream of particles, hint in the chat).
 */
@SuppressWarnings("removal")
public class RuinStatueGameTests {
	/** Temples returned by the stubbed temple search, per guild. */
	private static final Map<UUID, RuinRecord> TEMPLES = new ConcurrentHashMap<>();
	private static boolean stubbed;

	// ================================================================== research and map

	@GameTest(maxTicks = 200)
	public void legendResearchGivesAStatueMap(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		installLocator();
		BlockPos ground = TestSupport.region(level, 12);
		TestSupport.forceChunks(level, ground, 1);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Statue");
		GuildManager.setLevel(guild, 5);
		TestSupport.console(level, "guild admin structure unlock " + guild.name + " cartographer");
		BlockState bookshelf = Blocks.BOOKSHELF.defaultBlockState();
		BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
		wall(level, ground.offset(0, 0, 4), new BlockState[][]{
				{bookshelf, Blocks.LECTERN.defaultBlockState(), bookshelf},
				{planks, Blocks.CARTOGRAPHY_TABLE.defaultBlockState(), planks}});
		validate(leader, level, ground.offset(0, 1, 4), Direction.NORTH, "cartographer");
		helper.assertTrue(StructureManager.isBuilt(guild, Cartography.STRUCTURE_ID), "atelier validated");

		RuinRecord temple = RuinIndex.register(level, RuinGameTests.generate(level, ground.offset(1500, 0, 160)));
		helper.assertTrue(temple != null, "stand-in temple registered");
		TEMPLES.put(guild.id, temple);

		// The surface research no longer looks for the grand temple.
		HolderSet<Structure> surface = Cartography.surfaceStructures(level);
		helper.assertTrue(surface != null && surface.size() > 0, "surface ruins to look for");
		for (Holder<Structure> holder : surface) {
			String id = holder.unwrapKey().map(k -> k.identifier().toString()).orElse("");
			helper.assertFalse(id.equals(StatueSettings.DEFAULT_TEMPLE), "the grand temple is left to the legend");
		}

		ServerPlayer member = TestSupport.survivor(helper, ground.offset(2, 0, 0));
		GuildManager.addMember(guild, member.getUUID(), member.getName().getString());
		GuildManager.setRole(guild, member.getUUID(), GuildRole.OFFICER); // the gold comes from the treasury
		StatueSettings cfg = Statues.settings();
		for (Map.Entry<String, Integer> cost : cfg.cost.entrySet()) {
			ItemUtil.giveOrDrop(member, ItemUtil.item(cost.getKey()), cost.getValue());
		}
		GuildManager.addTreasury(guild, cfg.goldCost);
		Component refused = Cartography.startLegend(member, guild);
		helper.assertTrue(refused != null && refused.getString().contains(String.valueOf(cfg.minGuildLevel)), "guild level too low: refused");
		GuildManager.setLevel(guild, cfg.minGuildLevel);
		long treasury = guild.treasury;
		Component error = Cartography.startLegend(member, guild);
		helper.assertTrue(error == null, "legend research started: " + (error == null ? "" : error.getString()));
		helper.assertTrue(ItemUtil.count(member, Items.PAPER) == 0 && ItemUtil.count(member, Items.EMERALD) == 0, "cost paid");
		helper.assertTrue(guild.treasury == treasury - cfg.goldCost, "gold taken from the treasury");
		GuildRuinData.Research research = Cartography.research(guild);
		helper.assertTrue(research != null && research.legend, "a legend research");

		Cartography.addProgress(level.getServer(), guild, 1_000_000);
		helper.assertTrue(research.status == GuildRuinData.Research.Status.READY, "temple found, trail planned");
		StatueTrail trail = Statues.trail(research.trailId);
		helper.assertTrue(trail != null && trail.temple.equals(temple.key), "trail to the temple");
		List<StatueRecord> statues = Statues.statuesOf(trail);
		helper.assertTrue(statues.size() == cfg.count, "default number of statues: " + statues.size());

		helper.assertTrue(Cartography.claim(member, guild) == null, "statue map collected");
		ItemStack map = find(member, RuinItems.KIND_STATUE_MAP);
		helper.assertTrue(map.is(Items.FILLED_MAP) && map.get(DataComponents.MAP_ID) != null, "a filled map");
		helper.assertTrue(GuildyItems.data(map).getIntOr("trail", 0) == trail.id, "map bound to the trail");
		MapDecorations decorations = map.get(DataComponents.MAP_DECORATIONS);
		helper.assertTrue(decorations != null && decorations.decorations().size() == statues.size(),
				"one decoration per statue: " + (decorations == null ? 0 : decorations.decorations().size()));
		BlockPos aim = temple.target();
		for (Map.Entry<String, MapDecorations.Entry> entry : decorations.decorations().entrySet()) {
			MapDecorations.Entry deco = entry.getValue();
			helper.assertTrue(entry.getKey().startsWith("guildy_statue_"), "decoration of a statue: " + entry.getKey());
			double toTemple = Math.hypot(deco.x() - aim.getX(), deco.z() - aim.getZ());
			helper.assertTrue(toTemple >= cfg.lastDistanceMin - 40, "no decoration on the temple (" + Math.round(toTemple) + " blocks away)");
			boolean onStatue = statues.stream().anyMatch(s -> s.posX() == (int) deco.x() && s.posZ() == (int) deco.z());
			helper.assertTrue(onStatue, "the decoration marks a statue");
		}
		ItemLore lore = map.get(DataComponents.LORE);
		helper.assertTrue(lore != null && !lore.lines().isEmpty(), "hints in the lore");
		for (Component line : lore.lines()) {
			helper.assertFalse(line.getString().contains(String.valueOf(aim.getX())) || line.getString().contains(String.valueOf(aim.getZ())),
					"no temple coordinates in the lore: " + line.getString());
		}
		helper.assertFalse(RuinIndex.guild(guild.id).known.contains(temple.key), "the temple stays unknown to the guild");
		helper.assertTrue(Cartography.research(guild) == null, "research cleared");
		helper.assertTrue(statues.stream().allMatch(s -> s.status == StatueRecord.Status.PENDING), "statues wait for their chunks");
		helper.assertTrue(statues.getLast().routes.getFirst().next == -1, "the last statue leads to the temple");
		for (int i = 0; i + 1 < statues.size(); i++) {
			helper.assertTrue(statues.get(i).route(temple.key).next == statues.get(i + 1).id, "each statue leads to the next one");
		}
		TEMPLES.remove(guild.id);
		GuildManager.disband(guild);
		helper.succeed();
	}

	// ================================================================== lazy placement

	@GameTest(maxTicks = 900)
	public void pendingStatuesArePlacedOnNaturalGroundOutsideClaims(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = TestSupport.region(level, 0);
		RuinRecord temple = RuinIndex.register(level, RuinGameTests.generate(level, origin.offset(2000, 0, 0)));
		helper.assertTrue(temple != null, "stand-in temple registered");
		StatueTrail trail = Statues.createTrail(level, origin, temple, null);
		helper.assertTrue(trail != null && trail.statues.size() >= 2, "trail planned");
		List<StatueRecord> statues = Statues.statuesOf(trail);
		StatueRecord first = statues.get(0);
		StatueRecord second = statues.get(1);
		helper.assertTrue(first.status == StatueRecord.Status.PENDING && second.status == StatueRecord.Status.PENDING, "pending");

		// A guild settles right on the planned point of the second statue.
		BlockPos pad = TestSupport.pad(level, second.plannedX, second.plannedZ);
		ServerPlayer founder = TestSupport.player(helper, pad);
		Guild guild = TestSupport.foundGuild(helper, founder, pad, "Squat");
		helper.assertTrue(GuildManager.isClaimed(ChunkKey.ofBlock(second.plannedX, second.plannedZ)), "the planned point is claimed");
		TestSupport.forceChunks(level, new BlockPos(first.plannedX, 0, first.plannedZ), 3);
		TestSupport.forceChunks(level, pad, 4);
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(first.status == StatueRecord.Status.PLACED, "waiting for the first statue"))
				.thenExecute(() -> {
					helper.assertTrue(Math.hypot(first.x - first.plannedX, first.z - first.plannedZ) <= Statues.settings().siteRadius + 1,
							"placed near its planned point");
					helper.assertTrue(first.eyes.size() >= 2, "eyes recorded");
					for (BlockPos eye : first.eyes) {
						helper.assertTrue(level.getBlockState(eye).is(Blocks.VERDANT_FROGLIGHT), "glowing jade eye at " + eye.toShortString());
					}
					helper.assertTrue(Sanctuaries.isProtected(level, first.eyes.getFirst()), "the statue is protected");
					helper.assertFalse(Sanctuaries.isProtected(level, new BlockPos(first.x, first.maxY + 3, first.z)), "the air above is not");
					int built = 0;
					for (int x = first.minX; x <= first.maxX; x++) {
						for (int z = first.minZ; z <= first.maxZ; z++) {
							helper.assertFalse(GuildManager.isClaimed(ChunkKey.ofBlock(x, z)), "never in a claimed chunk");
							for (int y = first.minY; y <= first.maxY; y++) {
								if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
									built++;
								}
							}
							// standing on the ground: below the foundation layer, solid blocks
							BlockState under = level.getBlockState(new BlockPos(x, first.minY - 1, z));
							helper.assertFalse(under.isAir() && !level.getBlockState(new BlockPos(x, first.minY, z)).isAir(),
									"nothing floats at " + x + " " + z);
						}
					}
					helper.assertTrue(built > 500, "the statue stands there (" + built + " blocks)");
					StatueRecord.Route route = first.route(temple.key);
					helper.assertTrue(route != null && route.next == second.id, "the first statue leads to the second one");
				})
				.thenWaitUntil(() -> helper.assertTrue(second.status != StatueRecord.Status.PENDING, "waiting for the second statue"))
				.thenExecute(() -> {
					helper.assertTrue(second.status == StatueRecord.Status.PLACED, "placed farther, outside the territory: " + second.status);
					for (int cx = (second.minX - 1) >> 4; cx <= (second.maxX + 1) >> 4; cx++) {
						for (int cz = (second.minZ - 1) >> 4; cz <= (second.maxZ + 1) >> 4; cz++) {
							helper.assertFalse(GuildManager.isClaimed(ChunkKey.of(cx, cz)), "never in a claimed chunk");
						}
					}
					helper.assertTrue(second.attempts == 1, "found on the second, wider search");
					helper.assertTrue(guild.claims().stream().noneMatch(key -> GuildManager.atChunk(key) != guild), "the territory is intact");
					GuildManager.disband(guild);
				})
				.thenSucceed();
	}

	// ================================================================== guidance

	@GameTest(maxTicks = 600)
	public void approachingAStatueShowsTheWay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = TestSupport.region(level, 0);
		RuinRecord temple = RuinIndex.register(level, RuinGameTests.generate(level, origin.offset(0, 0, -1800)));
		helper.assertTrue(temple != null, "stand-in temple registered");
		StatueTrail trail = Statues.createTrail(level, origin, temple, null);
		helper.assertTrue(trail != null && !trail.statues.isEmpty(), "trail planned");
		StatueRecord statue = Statues.statuesOf(trail).getFirst();
		TestSupport.forceChunks(level, new BlockPos(statue.plannedX, 0, statue.plannedZ), 3);
		ServerPlayer[] player = new ServerPlayer[1];
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(statue.status == StatueRecord.Status.PLACED, "waiting for the statue"))
				.thenExecute(() -> {
					helper.assertTrue(statue.rotation == 0, "the trail goes north: the statue faces north, got " + statue.rotation);
					player[0] = TestSupport.survivor(helper, new BlockPos(statue.x + 9, statue.y, statue.z), "statue-walker");
				})
				.thenWaitUntil(() -> helper.assertTrue(StatueGuide.lastGuided(player[0].getUUID(), statue.id) >= 0, "waiting for the guidance"))
				.thenExecute(() -> {
					helper.assertTrue(StatueGuide.isActive(statue.id), "the stream of particles flows");
					helper.assertTrue(statue.timesGuided >= 1, "guidance counted");
					List<String> chat = TestSupport.chat(player[0]).stream().map(line -> line.replaceAll("§.", "")).toList();
					helper.assertTrue(chat.stream().anyMatch(line -> line.contains("Le regard du jaguar de pierre se tourne vers le nord")),
							"hint in the chat, got " + chat);
					long first = StatueGuide.lastGuided(player[0].getUUID(), statue.id);
					helper.assertTrue(first >= 0, "remembered");
				})
				.thenIdle(30)
				.thenExecute(() -> {
					long chats = TestSupport.chat(player[0]).stream().filter(line -> line.contains("Le regard du jaguar")).count();
					helper.assertTrue(chats == 1, "once per cooldown, got " + chats);
				})
				.thenSucceed();
	}

	// ================================================================== helpers

	/** Stubbed temple search: the record registered for the guild by the test (the vanilla search for any other guild). */
	private static synchronized void installLocator() {
		if (stubbed) {
			return;
		}
		stubbed = true;
		Cartography.TempleLocator vanilla = Cartography.templeLocator;
		Cartography.templeLocator = (level, origin, guild, claim) -> {
			RuinRecord temple = guild == null ? null : TEMPLES.get(guild.id);
			return temple != null ? temple : vanilla.locate(level, origin, guild, claim);
		};
	}

	private static ItemStack find(ServerPlayer player, String kind) {
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (GuildyItems.is(stack, kind)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
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
