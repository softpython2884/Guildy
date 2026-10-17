package dev.guildy.test;

import dev.guildy.core.GuildyItems;
import dev.guildy.core.ItemUtil;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.ruin.Cartography;
import dev.guildy.ruin.Encounters;
import dev.guildy.ruin.GuildRuinData;
import dev.guildy.ruin.IncantationItems;
import dev.guildy.ruin.RuinConfig;
import dev.guildy.ruin.RuinIndex;
import dev.guildy.ruin.RuinItems;
import dev.guildy.ruin.RuinModule;
import dev.guildy.ruin.RuinRecord;
import dev.guildy.ruin.RuinState;
import dev.guildy.structure.StructureManager;
import dev.guildy.villager.Task;
import dev.guildy.villager.VillagerRecord;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ruins: the test ruin (guildy-test:ruin/test_ruin, a 13 x 7 x 13 arena with data markers) is placed like
 * {@code /place structure}; its markers are captured and turned into air, the ruin is indexed; the guardian is awakened
 * (unknown ruin id: generic guardian; the incantation ritual is tested in {@link RuinSummonGameTests}) next to a survival
 * player who kills it (loot, XP, CLEARED) or leaves (reset). The
 * cartographer researches with a stubbed locate (the GameTest world generates no structures).
 */
@SuppressWarnings("removal")
public class RuinGameTests {
	private static final ResourceKey<Structure> TEST_RUIN = ResourceKey.create(Registries.STRUCTURE,
			Identifier.fromNamespaceAndPath("guildy-test", "ruin/test_ruin"));
	/** Locate results per guild (absent = nothing found), used by the stubbed locator. */
	private static final Map<UUID, RuinRecord> LOCATED = new ConcurrentHashMap<>();
	private static boolean stubbed;

	// ================================================================== markers, fight and victory

	@GameTest(maxTicks = 600)
	public void ruinMarkersAwakeningAndVictory(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos center = TestSupport.region(level, 0);
		TestSupport.forceChunks(level, center, 3);
		testGuardian();
		StructureStart start = generate(level, center);
		BoundingBox box = start.getBoundingBox();
		// The marker cells keep the block that was there (vanilla skips structure blocks): put stone everywhere at their
		// height first; the template's air replaces it, only the runtime can clear the marker cells.
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				level.setBlockAndUpdate(new BlockPos(x, box.minY() + 1, z), Blocks.STONE.defaultBlockState());
			}
		}
		place(level, start);
		RuinIndex.processPending(level.getServer());
		RuinRecord ruin = RuinIndex.register(level, start);
		helper.assertTrue(ruin != null, "test ruin indexed");
		helper.assertTrue("test_ruin".equals(ruin.ruinId), "ruin id from the template path, got " + ruin.ruinId);
		helper.assertFalse(RuinModule.config().isKnown("test_ruin"), "unknown id: generic guardian");
		helper.assertTrue(ruin.boss != null && ruin.minions.size() == 2 && ruin.entrance != null,
				"markers captured: boss " + ruin.boss + ", minions " + ruin.minions + ", entrance " + ruin.entrance);
		helper.assertTrue(ruin.box().isInside(ruin.boss), "boss marker inside the ruin box");
		for (BlockPos marker : List.of(ruin.boss, ruin.minions.getFirst(), ruin.minions.getLast(), ruin.entrance)) {
			helper.assertTrue(level.getBlockState(marker).isAir(), "marker cell turned into air at " + marker.toShortString()
					+ ", got " + level.getBlockState(marker));
		}
		helper.assertTrue(ruin.target().equals(ruin.entrance), "maps point to the entrance");
		helper.assertTrue(chestWithRuinLoot(level, box), "chest with the guildy:chests/ruin/common loot table");
		helper.assertTrue(ruin.state == RuinState.DORMANT, "dormant before anyone comes");

		ServerPlayer player = TestSupport.survivor(helper, ruin.boss.offset(2, -1, 0));
		Guild guild = GuildManager.create(TestSupport.uniqueName("Ruin"), player);
		long xpBefore = guild.xp;
		Mob[] boss = new Mob[1];
		helper.startSequence()
				.thenExecute(() -> awaken(helper, ruin))
				.thenWaitUntil(() -> {
					helper.assertTrue(Encounters.isActive(ruin), "waiting for the guardian to awaken");
					boss[0] = Encounters.boss(ruin);
					helper.assertTrue(boss[0] != null, "guardian spawned");
				})
				.thenExecute(() -> {
					helper.assertTrue(ruin.state == RuinState.AWAKE, "ruin awake");
					helper.assertTrue(boss[0].entityTags().contains("guildy_ruin"), "guardian tagged");
					helper.assertTrue(boss[0].isPersistenceRequired(), "guardian never despawns");
					helper.assertTrue(boss[0].getMaxHealth() > 100, "buffed guardian, max health " + boss[0].getMaxHealth());
					helper.assertTrue(boss[0].getCustomName() != null && boss[0].isCustomNameVisible(), "named guardian");
					helper.assertTrue(Encounters.minions(ruin).size() == 3, "escort of 3 for one player, got " + Encounters.minions(ruin).size());
					helper.assertTrue(Encounters.barViewers(ruin).contains(player), "boss bar shown to the player");
					boss[0].hurtServer(level, level.damageSources().playerAttack(player), 100_000F);
				})
				.thenWaitUntil(() -> helper.assertTrue(ruin.state == RuinState.CLEARED, "waiting for the victory"))
				.thenExecute(() -> {
					helper.assertFalse(Encounters.isActive(ruin), "fight over");
					helper.assertTrue(ruin.clearedDay >= 0 && ruin.timesCleared == 1, "victory recorded");
					helper.assertTrue(guild.xp > xpBefore, "guild XP for the participant's guild");
					helper.assertTrue(RuinIndex.guild(guild.id).known.contains(ruin.key), "the ruin is known by the guild");
					List<ItemEntity> gold = level.getEntitiesOfClass(ItemEntity.class, new AABB(boss[0].blockPosition()).inflate(6),
							e -> e.getItem().is(Items.GOLD_INGOT));
					int ingots = gold.stream().mapToInt(e -> e.getItem().getCount()).sum();
					helper.assertTrue(ingots >= 3, "gold dropped by the guardian, got " + ingots);
					helper.assertTrue(level.getEntitiesOfClass(Mob.class, AABB.of(box).inflate(8), m -> m.entityTags().contains("guildy_ruin")
							&& m.isAlive()).isEmpty(), "escort removed with the guardian");
					GuildManager.disband(guild);
				})
				.thenSucceed();
	}

	// ================================================================== reset

	@GameTest(maxTicks = 600)
	public void ruinResetsWhenNobodyIsAround(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos center = TestSupport.region(level, 0);
		TestSupport.forceChunks(level, center, 3);
		testGuardian();
		StructureStart start = generate(level, center);
		place(level, start);
		RuinIndex.processPending(level.getServer());
		RuinRecord ruin = RuinIndex.register(level, start);
		helper.assertTrue(ruin != null && ruin.boss != null, "test ruin indexed with its boss marker");
		ServerPlayer player = TestSupport.survivor(helper, ruin.boss.offset(-2, -1, 0));
		Mob[] boss = new Mob[1];
		helper.startSequence()
				.thenExecute(() -> awaken(helper, ruin))
				.thenWaitUntil(() -> {
					boss[0] = Encounters.boss(ruin);
					helper.assertTrue(boss[0] != null, "waiting for the guardian to awaken");
				})
				.thenExecute(() -> {
					// Wounded guardian, then the player leaves the area.
					boss[0].setHealth(boss[0].getMaxHealth() / 2);
					player.teleportTo(level, ruin.boss.getX() + 300.5, ruin.boss.getY() + 1, ruin.boss.getZ() + 0.5, Set.of(), 0, 0, true);
				})
				.thenWaitUntil(() -> helper.assertFalse(Encounters.isActive(ruin), "waiting for the reset (nobody around)"))
				.thenExecute(() -> {
					helper.assertTrue(ruin.state == RuinState.DORMANT, "ruin dormant again, got " + ruin.state);
					helper.assertTrue(boss[0].isRemoved(), "guardian removed");
					helper.assertTrue(Encounters.minions(ruin).isEmpty(), "escort removed");
					// Back again: a fresh guardian at full health.
					player.teleportTo(level, ruin.boss.getX() + 2.5, ruin.boss.getY(), ruin.boss.getZ() + 0.5, Set.of(), 0, 0, true);
					awaken(helper, ruin);
				})
				.thenWaitUntil(() -> {
					Mob fresh = Encounters.boss(ruin);
					helper.assertTrue(fresh != null && fresh != boss[0], "waiting for a new guardian");
					helper.assertTrue(fresh.getHealth() >= fresh.getMaxHealth() - 0.01F, "full health after a reset");
				})
				.thenExecute(() -> {
					player.teleportTo(level, ruin.boss.getX() + 300.5, ruin.boss.getY() + 1, ruin.boss.getZ() + 0.5, Set.of(), 0, 0, true);
					Encounters.reset(ruin);
					helper.assertFalse(Encounters.isActive(ruin), "admin reset ends the fight");
					helper.assertTrue(ruin.state == RuinState.DORMANT, "dormant after the admin reset");
				})
				.thenSucceed();
	}

	// ================================================================== abilities

	@GameTest(maxTicks = 900)
	public void ruinGuardianAbilities(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos center = TestSupport.region(level, 0);
		TestSupport.forceChunks(level, center, 3);
		testGuardian();
		StructureStart start = generate(level, center);
		place(level, start);
		RuinIndex.processPending(level.getServer());
		RuinRecord ruin = RuinIndex.register(level, start);
		helper.assertTrue(ruin != null && ruin.boss != null, "test ruin indexed with its boss marker");
		ServerPlayer player = TestSupport.survivor(helper, ruin.boss.offset(4, -1, 0));
		// Damage is blocked (the effects of the abilities are what this test checks).
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4, false, false));
		Mob[] boss = new Mob[1];
		int[] minions = new int[1];
		GameTestSequence sequence = helper.startSequence()
				.thenExecute(() -> awaken(helper, ruin))
				.thenWaitUntil(() -> {
					boss[0] = Encounters.boss(ruin);
					helper.assertTrue(boss[0] != null, "waiting for the guardian to awaken");
				})
				.thenWaitUntil(() -> helper.assertTrue(boss[0].onGround(), "waiting for the guardian to land"))
				.thenExecute(() -> {
					// Motionless mobs: the abilities move the guardian themselves.
					boss[0].setNoAi(true);
					Encounters.minions(ruin).forEach(m -> m.setNoAi(true));
					boss[0].setHealth(boss[0].getMaxHealth() / 2);
				});
		ability(helper, sequence, ruin, player, "ground_slam", "", () -> {
		});
		ability(helper, sequence, ruin, player, "fire_ring", "fire", () -> helper.assertTrue(player.getRemainingFireTicks() > 0, "player set on fire"));
		ability(helper, sequence, ruin, player, "fire_ring", "soul", () -> helper.assertTrue(player.hasEffect(MobEffects.WITHER), "soul fire withers"));
		ability(helper, sequence, ruin, player, "fire_ring", "frost", () -> helper.assertTrue(player.hasEffect(MobEffects.SLOWNESS), "frost slows"));
		ability(helper, sequence, ruin, player, "volley", "arrow", () -> {
		});
		ability(helper, sequence, ruin, player, "volley", "spectral", () -> {
		});
		ability(helper, sequence, ruin, player, "volley", "fireball", () -> {
		});
		ability(helper, sequence, ruin, player, "life_drain", "", () -> {
		});
		sequence.thenExecute(() -> minions[0] = Encounters.minions(ruin).size());
		ability(helper, sequence, ruin, player, "summon", "", () -> helper.assertTrue(Encounters.minions(ruin).size() > minions[0],
				"escort summoned: " + minions[0] + " -> " + Encounters.minions(ruin).size()));
		ability(helper, sequence, ruin, player, "blink", "", () -> helper.assertTrue(boss[0].distanceTo(player) < 4.5,
				"guardian blinked next to the player, distance " + boss[0].distanceTo(player)));
		ability(helper, sequence, ruin, player, "enrage", "", () -> helper.assertTrue(boss[0].hasEffect(MobEffects.STRENGTH), "enraged guardian"));
		sequence.thenExecute(() -> {
			helper.assertTrue(boss[0].isAlive() && Encounters.isActive(ruin), "fight still running");
			player.teleportTo(level, ruin.boss.getX() + 300.5, ruin.boss.getY() + 1, ruin.boss.getZ() + 0.5, Set.of(), 0, 0, true);
			Encounters.reset(ruin);
		}).thenSucceed();
	}

	/** Uses an ability now, lets its animation run, then checks its effect. */
	private static void ability(GameTestHelper helper, GameTestSequence sequence, RuinRecord ruin, ServerPlayer player, String type,
			String style, Runnable check) {
		RuinConfig.AbilityDef ability = new RuinConfig.AbilityDef();
		ability.type = type;
		ability.style = style;
		ability.damage = 4;
		ability.radius = 7;
		ability.count = 2;
		sequence.thenExecute(() -> {
			// Same spot before each ability: 4 blocks from the guardian's spawn cell.
			player.teleportTo(helper.getLevel(), ruin.boss.getX() + 4.5, ruin.boss.getY(), ruin.boss.getZ() + 0.5, Set.of(), 0, 0, true);
			helper.assertTrue(Encounters.forceAbility(ruin, ability), "ability " + type + " " + style + " used");
		})
				.thenIdle(40)
				.thenExecute(check);
	}

	// ================================================================== loot tables

	@GameTest(maxTicks = 20)
	public void ruinLootTablesRoll(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (String name : List.of("common", "rare", "treasure", "nether_common", "nether_rare", "nether_treasure")) {
			ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("guildy", "chests/ruin/" + name));
			LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
			helper.assertTrue(table != LootTable.EMPTY, "loot table " + name + " loaded");
			LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
			int total = 0;
			for (int i = 0; i < 5; i++) {
				for (ItemStack stack : table.getRandomItems(params)) {
					helper.assertFalse(stack.is(Items.GOLD_BLOCK), name + " never gives gold blocks");
					total += stack.getCount();
				}
			}
			helper.assertTrue(total > 0, name + " gives items");
		}
		helper.succeed();
	}

	// ================================================================== cartographer

	@GameTest(environment = "guildy-test:day", maxTicks = 1600)
	public void cartographerResearchGivesMapAndCompass(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		installLocator();
		BlockPos ground = TestSupport.region(level, 12);
		TestSupport.forceChunks(level, ground, 2);
		ServerPlayer leader = TestSupport.player(helper, ground);
		Guild guild = TestSupport.foundGuild(helper, leader, ground, "Carto");
		GuildManager.setLevel(guild, 5);
		TestSupport.console(level, "guild admin structure unlock " + guild.name + " cartographer");
		BlockState bookshelf = Blocks.BOOKSHELF.defaultBlockState();
		BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
		wall(level, ground.offset(0, 0, 4), new BlockState[][]{
				{bookshelf, Blocks.LECTERN.defaultBlockState(), bookshelf},
				{planks, Blocks.CARTOGRAPHY_TABLE.defaultBlockState(), planks}});
		validate(leader, level, ground.offset(0, 1, 4), Direction.NORTH, "cartographer");
		helper.assertTrue(StructureManager.isBuilt(guild, Cartography.STRUCTURE_ID), "atelier validated");

		TestSupport.console(level, "guild admin villager spawn " + guild.name);
		level.getServer().getCommands().performPrefixedCommand(leader.createCommandSourceStack(), "guild villagers accept");
		helper.assertTrue(guild.villagers.members.size() == 1, "candidate accepted");
		VillagerRecord record = guild.villagers.members.values().iterator().next();
		Method setTask = Class.forName("dev.guildy.villager.VillagerManager")
				.getDeclaredMethod("setTask", Guild.class, VillagerRecord.class, Task.class, ServerPlayer.class);
		setTask.setAccessible(true);
		setTask.invoke(null, guild, record, Task.CARTOGRAPHER, leader);
		helper.assertTrue(Cartography.cartographers(guild) == 1, "one cartographer");

		// Ruins found by the stubbed search (structure starts generated, not placed, in both dimensions).
		RuinRecord surface = RuinIndex.register(level, generate(level, ground.offset(600, 0, 0)));
		ServerLevel nether = level.getServer().getLevel(Level.NETHER);
		helper.assertTrue(surface != null && nether != null, "surface ruin registered, Nether present");
		RuinRecord netherRuin = RuinIndex.register(nether, generate(nether, new BlockPos(ground.getX() / 8 + 200, 64, ground.getZ() / 8)));
		helper.assertTrue(netherRuin != null && netherRuin.isNether(), "Nether ruin registered");

		RuinModule.config().cartography.researchMinutes = 0.05; // 3 seconds of work
		ServerPlayer member = TestSupport.survivor(helper, ground.offset(2, 0, 0));
		GuildManager.addMember(guild, member.getUUID(), member.getName().getString());
		helper.assertTrue(Cartography.start(member, guild, false) != null, "no paper / compass: refused");
		ItemUtil.giveOrDrop(member, Items.PAPER, 8);
		ItemUtil.giveOrDrop(member, Items.COMPASS, 1);
		helper.assertTrue(Cartography.start(leader, guild, false) != null, "creative players cannot pay");
		LOCATED.put(guild.id, surface);
		Component error = Cartography.start(member, guild, false);
		helper.assertTrue(error == null, "research started: " + (error == null ? "" : error.getString()));
		helper.assertTrue(ItemUtil.count(member, Items.PAPER) == 0 && ItemUtil.count(member, Items.COMPASS) == 0, "cost paid");

		helper.startSequence()
				.thenWaitUntil(() -> {
					GuildRuinData.Research research = Cartography.research(guild);
					helper.assertTrue(research != null && research.status == GuildRuinData.Research.Status.READY,
							"waiting for the cartographer to finish (" + (research == null ? "-" : research.progress + "/" + research.required) + ")");
				})
				.thenExecute(() -> {
					helper.assertTrue(Cartography.claim(member, guild) == null, "map collected");
					ItemStack map = find(member, RuinItems.KIND_MAP);
					helper.assertTrue(map.is(Items.FILLED_MAP), "explorer map received");
					helper.assertTrue(map.get(DataComponents.MAP_ID) != null, "map with map data");
					helper.assertTrue(map.get(DataComponents.MAP_DECORATIONS) != null && !map.get(DataComponents.MAP_DECORATIONS).decorations().isEmpty(),
							"target marked on the map");
					helper.assertTrue(surface.key.equals(GuildyItems.data(map).getStringOr("ruin", "")), "map bound to the ruin");
					int incantations = RuinModule.config().cartography.incantationsPerResearch;
					helper.assertTrue(find(member, IncantationItems.KIND).getCount() == incantations,
							"incantation scroll(s) given with the map: expected " + incantations);
					helper.assertTrue(RuinIndex.guild(guild.id).known.contains(surface.key), "ruin known by the guild");
					helper.assertTrue(Cartography.research(guild) == null, "research cleared");

					// Nether research, finished by the admin command: a lodestone compass.
					ItemUtil.giveOrDrop(member, Items.PAPER, 8);
					ItemUtil.giveOrDrop(member, Items.COMPASS, 1);
					ItemUtil.giveOrDrop(member, Items.QUARTZ, 4);
					LOCATED.put(guild.id, netherRuin);
					helper.assertTrue(Cartography.start(member, guild, true) == null, "Nether research started");
					TestSupport.console(level, "guild admin ruin research " + guild.name + " finish");
					helper.assertTrue(Cartography.claim(member, guild) == null, "compass collected");
					ItemStack compass = find(member, RuinItems.KIND_COMPASS);
					LodestoneTracker tracker = compass.get(DataComponents.LODESTONE_TRACKER);
					helper.assertTrue(compass.is(Items.COMPASS) && tracker != null && tracker.target().isPresent()
							&& tracker.target().get().dimension() == Level.NETHER && !tracker.tracked(), "compass pointing into the Nether");

					// Nothing found: the cost comes back.
					ItemUtil.giveOrDrop(member, Items.PAPER, 8);
					ItemUtil.giveOrDrop(member, Items.COMPASS, 1);
					LOCATED.remove(guild.id);
					helper.assertTrue(Cartography.start(member, guild, false) == null, "third research started");
					Cartography.addProgress(level.getServer(), guild, 10_000);
					helper.assertTrue(Cartography.research(guild).status == GuildRuinData.Research.Status.FAILED, "nothing found");
					helper.assertTrue(Cartography.claim(member, guild) == null, "refund collected");
					helper.assertTrue(ItemUtil.count(member, Items.PAPER) == 8 && ItemUtil.count(member, Items.COMPASS) >= 1, "cost refunded");
					GuildManager.disband(guild);
				})
				.thenSucceed();
	}

	// ================================================================== helpers

	/**
	 * Awakens the guardian directly (the admin path): in the default "scroll" mode walking in no longer does it, and the
	 * incantation ritual has its own tests ({@link RuinSummonGameTests}).
	 */
	private static void awaken(GameTestHelper helper, RuinRecord ruin) {
		String error = Encounters.awaken(helper.getLevel(), ruin, null, false);
		helper.assertTrue(error == null, "guardian awakened, got " + error);
	}

	/** The generic guardian of unknown ruins, made quick to test (both fight tests set the same values). */
	private static void testGuardian() {
		RuinConfig.RuinDef def = RuinModule.config().def("test_ruin", false);
		def.resetSeconds = 2;
		def.activationRadius = 12;
		def.rewards.goldMin = 3;
		def.rewards.goldMax = 5;
		def.respawnDays = 7;
	}

	/** Generates the test ruin start at the chunk of {@code at} (like /place structure, the pieces are not placed). */
	static StructureStart generate(ServerLevel level, BlockPos at) {
		Holder.Reference<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(TEST_RUIN);
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState randomState = level.getChunkSource().randomState();
		StructureStart start = holder.value().generate(holder, level.dimension(), level.registryAccess(), generator, generator.getBiomeSource(),
				randomState.createClimateSampler(SamplerContext.EMPTY_UNCACHED), randomState, level.getStructureTemplateManager(), level.getSeed(),
				ChunkPos.containing(at), 0, level, biome -> true);
		if (!start.isValid()) {
			throw new IllegalStateException("test ruin could not be generated at " + at);
		}
		return start;
	}

	/** Places every chunk of the start (chunks must be loaded), as /place structure does. */
	static void place(ServerLevel level, StructureStart start) {
		BoundingBox box = start.getBoundingBox();
		ChunkPos min = ChunkPos.containing(new BlockPos(box.minX(), 0, box.minZ()));
		ChunkPos max = ChunkPos.containing(new BlockPos(box.maxX(), 0, box.maxZ()));
		for (int cx = min.x(); cx <= max.x(); cx++) {
			for (int cz = min.z(); cz <= max.z(); cz++) {
				ChunkPos chunk = new ChunkPos(cx, cz);
				level.getChunk(cx, cz);
				start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(),
						new BoundingBox(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxY() + 1,
								chunk.getMaxBlockZ()), chunk);
			}
		}
	}

	private static boolean chestWithRuinLoot(ServerLevel level, BoundingBox box) {
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int y = box.minY(); y <= box.maxY(); y++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					BlockEntity entity = level.getBlockEntity(new BlockPos(x, y, z));
					if (entity instanceof ChestBlockEntity chest && chest.getLootTable() != null
							&& chest.getLootTable().identifier().toString().equals("guildy:chests/ruin/common")) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Stubbed ruin search: the record registered for the guild by the test (absent = nothing found). */
	private static synchronized void installLocator() {
		if (stubbed) {
			return;
		}
		stubbed = true;
		Cartography.Locator vanilla = Cartography.locator;
		Cartography.locator = (level, origin, nether, guild) -> {
			if (!LOCATED.containsKey(guild.id) && !guild.name.startsWith("Carto")) {
				return vanilla.locate(level, origin, nether, guild);
			}
			return LOCATED.get(guild.id);
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
