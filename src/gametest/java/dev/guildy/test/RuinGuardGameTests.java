package dev.guildy.test;

import dev.guildy.ruin.Encounters;
import dev.guildy.ruin.IncantationItems;
import dev.guildy.ruin.Incantations;
import dev.guildy.ruin.RoomGuards;
import dev.guildy.ruin.RuinConfig;
import dev.guildy.ruin.RuinIndex;
import dev.guildy.ruin.RuinModule;
import dev.guildy.ruin.RuinRecord;
import dev.guildy.ruin.RuinState;
import dev.guildy.ruin.Sanctuaries;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Room guards, group scaling and beacon sanctuaries, on the test ruin of {@link RuinGameTests} (the celestial jaguar: see
 * {@link RuinJaguarGameTests}). The
 * guard scenarios give their ruin its own configured definition (the generic test ruin has no guards, see
 * {@link GuildyTestInit}): approaching posts the guards (fallback posts: escort markers, entrance; or the guard markers),
 * the incantation is refused while they live and accepted once they are dead, killed guards stay dead, the survivors leave
 * when nobody is around and fresh ones come at the next approach; two players make the guardian stronger and share its
 * gold; a beacon and its pyramid resist players, explosions and pistons while the other blocks break.
 */
@SuppressWarnings("removal")
public class RuinGuardGameTests {
	private static final AtomicInteger IDS = new AtomicInteger();

	/** A placed, indexed test ruin with its boss marker, at threat level I, under a configured definition. */
	private static RuinRecord ruin(GameTestHelper helper, RuinConfig.RuinDef def) {
		ServerLevel level = helper.getLevel();
		BlockPos center = TestSupport.region(level, 0);
		TestSupport.forceChunks(level, center, 3);
		StructureStart start = RuinGameTests.generate(level, center);
		RuinGameTests.place(level, start);
		RuinIndex.processPending(level.getServer());
		RuinRecord ruin = RuinIndex.register(level, start);
		helper.assertTrue(ruin != null && ruin.boss != null, "test ruin indexed with its boss marker");
		helper.assertTrue(ruin.state == RuinState.DORMANT, "dormant ruin");
		Encounters.setThreat(ruin, 1);
		if (def != null) {
			String id = "guard_test_" + IDS.incrementAndGet();
			RuinModule.config().ruins.put(id, def);
			ruin.ruinId = id;
		}
		return ruin;
	}

	/** The generic guardian with {@code count} zombie guards, quick to reset, never rising again. */
	private static RuinConfig.RuinDef guarded(int count) {
		RuinConfig.RuinDef def = RuinModule.config().def("test_ruin", false).copy();
		def.resetSeconds = 3;
		def.respawnDays = 7;
		def.fastKillSeconds = 0;
		def.guards.count = count;
		def.guards.name = "&cGarde de test";
		def.guards.types.clear();
		def.guards.types.put("minecraft:zombie", 1);
		return def;
	}

	private static void shield(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4, false, false));
	}

	private static void at(ServerLevel level, ServerPlayer player, BlockPos pos) {
		player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), 0, 0, true);
	}

	private static void away(ServerLevel level, ServerPlayer player, RuinRecord ruin) {
		player.teleportTo(level, ruin.boss.getX() + 300.5, ruin.boss.getY() + 1, ruin.boss.getZ() + 0.5, Set.of(), 0, 0, true);
	}

	/** Right click with incantation scrolls; returns the stack in hand. */
	private static ItemStack rightClick(GameTestHelper helper, ServerPlayer player, int count, boolean expectStart) {
		player.setItemInHand(InteractionHand.MAIN_HAND, IncantationItems.create(count));
		InteractionResult result = UseItemCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND);
		if (expectStart) {
			helper.assertTrue(result instanceof InteractionResult.Success && player.isUsingItem(), "incantation started, got " + result);
		} else {
			helper.assertTrue(result == InteractionResult.FAIL && !player.isUsingItem(), "incantation refused, got " + result);
		}
		return player.getMainHandItem();
	}

	private static void channel(ServerPlayer player) {
		for (int i = 0; i < 400 && player.isUsingItem(); i++) {
			player.doTick();
		}
	}

	private static int incantations(ServerPlayer player) {
		int total = 0;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (IncantationItems.is(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static String bar(RuinRecord ruin) {
		Component title = Encounters.barTitle(ruin);
		return title == null ? "" : title.getString();
	}

	// ================================================================== guards, fallback posts, ritual

	@GameTest(maxTicks = 1200)
	public void guardsHoldTheRoomUntilTheyFall(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper, guarded(3));
		helper.assertTrue(ruin.guards.isEmpty(), "the test template has no guard marker: fallback posts");
		ServerPlayer player = TestSupport.survivor(helper, ruin.boss.offset(80, -1, 0));
		shield(player);
		List<Mob> first = new ArrayList<>();
		helper.startSequence()
				.thenIdle(30)
				.thenExecute(() -> {
					helper.assertFalse(RoomGuards.posted(ruin), "nobody near: no guard");
					at(level, player, ruin.boss.offset(2, 0, 0));
				})
				.thenWaitUntil(() -> helper.assertTrue(RoomGuards.guards(ruin).size() == 3, "waiting for the guards, got " + RoomGuards.guards(ruin).size()))
				.thenExecute(() -> {
					first.addAll(RoomGuards.guards(ruin));
					for (Mob guard : first) {
						guard.setNoAi(true);
						helper.assertTrue(guard.getType() == EntityTypes.ZOMBIE, "guards.types: " + guard.getType());
						helper.assertTrue(guard.entityTags().contains(RoomGuards.TAG) && !guard.entityTags().contains("guildy_ruin"),
								"guard tag only (not a fight mob): " + guard.entityTags());
						helper.assertTrue(guard.isPersistenceRequired(), "never despawns");
						helper.assertTrue(guard.getCustomName() != null && guard.getCustomName().getString().contains("Garde de test"), "named");
						BlockPos post = RoomGuards.postOf(guard);
						helper.assertTrue(post != null && guard.blockPosition().distManhattan(post) <= 2, "at its post " + post + ": " + guard.blockPosition());
						helper.assertTrue(ruin.contains(guard.blockPosition(), 1), "inside the ruin");
					}
					List<BlockPos> posts = RoomGuards.posts(ruin);
					for (BlockPos minion : ruin.minions) {
						helper.assertTrue(posts.stream().anyMatch(p -> p.distManhattan(minion) <= 3), "a guard at the escort marker " + minion + ": " + posts);
					}
					helper.assertTrue(RoomGuards.blocking(level, ruin) == 3, "the three hold the arena");
					helper.assertTrue(rightClick(helper, player, 2, false).getCount() == 2, "incantation refused while they live, scroll kept");
					helper.assertFalse(Incantations.ritualRunning(ruin), "no ritual");
					first.get(0).hurtServer(level, level.damageSources().playerAttack(player), 1000F);
					first.get(1).hurtServer(level, level.damageSources().playerAttack(player), 1000F);
					helper.assertTrue(RoomGuards.alive(ruin) == 1 && RoomGuards.blocking(level, ruin) == 1, "one left");
					helper.assertTrue(rightClick(helper, player, 2, false).getCount() == 2, "still refused (one guard)");
					first.get(2).hurtServer(level, level.damageSources().playerAttack(player), 1000F);
				})
				.thenIdle(30)
				.thenExecute(() -> {
					helper.assertTrue(RoomGuards.posted(ruin) && RoomGuards.alive(ruin) == 0 && RoomGuards.killed(ruin) == 3,
							"killed guards stay dead: alive " + RoomGuards.alive(ruin) + ", killed " + RoomGuards.killed(ruin));
					helper.assertTrue(RoomGuards.blocking(level, ruin) == 0, "the arena is free");
					ItemStack hand = rightClick(helper, player, 2, true);
					channel(player);
					helper.assertTrue(hand.getCount() == 1 && Incantations.ritualRunning(ruin), "the incantation is accepted once they are dead");
					away(level, player, ruin);
				})
				.thenWaitUntil(() -> helper.assertFalse(Incantations.ritualRunning(ruin), "waiting for the ritual to break"))
				.thenWaitUntil(() -> helper.assertFalse(RoomGuards.posted(ruin), "waiting for the reset of the guards (nobody around)"))
				.thenExecute(() -> {
					helper.assertTrue(incantations(player) == 2, "scroll given back by the broken ritual");
					at(level, player, ruin.boss.offset(2, 0, 0));
				})
				.thenWaitUntil(() -> helper.assertTrue(RoomGuards.guards(ruin).size() == 3, "fresh guards at the next approach"))
				.thenExecute(() -> {
					first.clear();
					first.addAll(RoomGuards.guards(ruin));
					first.forEach(g -> g.setNoAi(true));
					away(level, player, ruin);
				})
				.thenWaitUntil(() -> helper.assertFalse(RoomGuards.posted(ruin), "waiting for the living guards to leave"))
				.thenExecute(() -> {
					for (Mob guard : first) {
						helper.assertTrue(guard.isRemoved(), "the guard left with the reset");
					}
					helper.assertTrue(level.getEntitiesOfClass(Mob.class, AABB.of(ruin.box()).inflate(4), m -> m.entityTags().contains(RoomGuards.TAG)).isEmpty(),
							"no guard left in the ruin");
				})
				.thenSucceed();
	}

	// ================================================================== guard markers, more players

	@GameTest(maxTicks = 600)
	public void guardsTakeTheirMarkedPosts(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper, guarded(2));
		// Guard markers (as the templates give them): two posts in the room.
		BlockPos north = ruin.boss.offset(4, 0, -4);
		BlockPos south = ruin.boss.offset(-4, 0, 4);
		ruin.guards.add(north);
		ruin.guards.add(south);
		helper.assertTrue(level.getBlockState(north).isAir() && level.getBlockState(south).isAir(), "free posts");
		ServerPlayer a = TestSupport.survivor(helper, ruin.boss.offset(80, -1, 0));
		ServerPlayer b = TestSupport.survivor(helper, ruin.boss.offset(80, -1, 2));
		shield(a);
		shield(b);
		helper.startSequence()
				.thenExecute(() -> {
					// Two intruders at once: one more guard than the ruin's two.
					at(level, a, ruin.boss.offset(0, 0, -2));
					at(level, b, ruin.boss.offset(0, 0, 2));
				})
				.thenWaitUntil(() -> helper.assertTrue(RoomGuards.guards(ruin).size() == 3, "waiting for 2 + 1 guards, got " + RoomGuards.guards(ruin).size()))
				.thenExecute(() -> {
					int[] perPost = new int[2];
					for (Mob guard : RoomGuards.guards(ruin)) {
						guard.setNoAi(true);
						BlockPos post = RoomGuards.postOf(guard);
						helper.assertTrue(ruin.guards.contains(post), "posted at a guard marker: " + post);
						helper.assertTrue(guard.blockPosition().distManhattan(post) <= 1, "standing on it: " + guard.blockPosition());
						perPost[post.equals(north) ? 0 : 1]++;
					}
					helper.assertTrue(perPost[0] >= 1 && perPost[1] >= 1, "every post gets a guard before a second one");
					// Lured away: brought back to its post.
					Mob straying = RoomGuards.guards(ruin).getFirst();
					BlockPos post = RoomGuards.postOf(straying);
					straying.teleportTo(post.getX() + 0.5, post.getY() + 30, post.getZ() + 0.5);
				})
				.thenWaitUntil(() -> {
					for (Mob guard : RoomGuards.guards(ruin)) {
						helper.assertTrue(guard.blockPosition().distManhattan(RoomGuards.postOf(guard)) <= 2, "waiting for the leash");
					}
				})
				.thenExecute(() -> {
					away(level, a, ruin);
					away(level, b, ruin);
				})
				.thenWaitUntil(() -> helper.assertFalse(RoomGuards.posted(ruin), "waiting for the guards to leave"))
				.thenSucceed();
	}

	// ================================================================== group scaling

	@GameTest(maxTicks = 600)
	public void twoPlayersMakeTheGuardianStronger(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper, null);
		Encounters.setThreat(ruin, 3);
		RuinConfig.RuinDef def = RuinModule.config().def("test_ruin", false).copy();
		def.resetSeconds = 60;
		def.fastKillSeconds = 0;
		def.rewards.goldMin = 10;
		def.rewards.goldMax = 10;
		def.rewards.goldPerEscort = 0;
		def.rewards.lootTable = "";
		def.rewards.items.clear();
		ServerPlayer a = TestSupport.survivor(helper, ruin.boss.offset(3, -1, 0));
		ServerPlayer b = TestSupport.survivor(helper, ruin.boss.offset(-3, -1, 0));
		shield(a);
		shield(b);
		Mob[] boss = new Mob[1];
		helper.startSequence()
				.thenExecute(() -> helper.assertTrue(Encounters.awaken(level, ruin, def, false) == null, "guardian awakened"))
				.thenWaitUntil(() -> {
					boss[0] = Encounters.boss(ruin);
					helper.assertTrue(boss[0] != null, "waiting for the guardian");
				})
				.thenExecute(() -> {
					helper.assertTrue(Encounters.partySize(ruin) == 2, "two players counted, got " + Encounters.partySize(ruin));
					// Menace III (+40 %) x 2 players (+50 %): multiplied.
					double expected = def.boss.health * 1.4 * 1.5;
					helper.assertTrue(Math.abs(boss[0].getMaxHealth() - expected) < 0.5, "health x1.4 x1.5 = " + expected + ", got " + boss[0].getMaxHealth());
					helper.assertTrue(boss[0].getHealth() >= boss[0].getMaxHealth() - 0.01F, "at full health");
					helper.assertTrue(bar(ruin).contains("2 joueurs") && bar(ruin).contains("Menace III"), "boss bar: " + bar(ruin));
					// 3 + 2 for the second player, +1 for Menace III.
					helper.assertTrue(Encounters.minions(ruin).size() == 6, "escort of 6, got " + Encounters.minions(ruin).size());
					Encounters.minions(ruin).forEach(m -> m.setNoAi(true));
					boss[0].hurtServer(level, level.damageSources().playerAttack(a), 100_000F);
				})
				.thenWaitUntil(() -> helper.assertTrue(ruin.state == RuinState.CLEARED, "waiting for the victory"))
				.thenWaitUntil(() -> {
					List<ItemEntity> gold = level.getEntitiesOfClass(ItemEntity.class, new AABB(boss[0].blockPosition()).inflate(8),
							e -> e.getItem().is(Items.GOLD_INGOT));
					int total = gold.stream().mapToInt(e -> e.getItem().getCount()).sum();
					// 10 x 1.3 (Menace III) x 1.5 (2 players) = 19.5 -> 20, one share each.
					helper.assertTrue(total == 20, "group gold, got " + total);
					helper.assertTrue(gold.size() == 2 && gold.get(0).getItem().getCount() == 10 && gold.get(1).getItem().getCount() == 10,
							"two shares of 10: " + gold.stream().map(e -> e.getItem().getCount()).toList());
				})
				.thenSucceed();
	}

	// ================================================================== beacon sanctuary

	@GameTest(maxTicks = 200)
	public void beaconSanctuaryResists(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper, null);
		// A two-level pyramid in a corner of the ruin, a beacon and its glass on top, a loose iron block aside.
		BlockPos base = new BlockPos(ruin.minX + 2, ruin.minY + 1, ruin.maxZ - 2);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				level.setBlockAndUpdate(base.offset(dx, 0, dz), Blocks.IRON_BLOCK.defaultBlockState());
				if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
					level.setBlockAndUpdate(base.offset(dx, 1, dz), Blocks.GOLD_BLOCK.defaultBlockState());
				}
			}
		}
		BlockPos beacon = base.above(2);
		level.setBlockAndUpdate(beacon, Blocks.BEACON.defaultBlockState());
		level.setBlockAndUpdate(beacon.above(), Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.YELLOW).defaultBlockState());
		BlockPos loose = base.offset(0, 0, -5);
		level.setBlockAndUpdate(loose, Blocks.IRON_BLOCK.defaultBlockState());
		helper.assertTrue(ruin.contains(loose, 0) && ruin.contains(beacon, 0), "inside the ruin");
		Sanctuaries.invalidate();

		ServerPlayer player = TestSupport.survivor(helper, base.offset(4, 0, 0));
		helper.assertTrue(Sanctuaries.isProtected(level, beacon), "the beacon");
		helper.assertTrue(Sanctuaries.isProtected(level, base.offset(1, 1, -1)) && Sanctuaries.isProtected(level, base.offset(-2, 0, 2)),
				"both levels of its pyramid");
		helper.assertFalse(Sanctuaries.isProtected(level, beacon.above()), "not the glass");
		helper.assertFalse(Sanctuaries.isProtected(level, loose), "not a loose iron block");

		helper.assertFalse(player.gameMode.destroyBlock(beacon), "players cannot break the beacon");
		helper.assertFalse(player.gameMode.destroyBlock(base.offset(1, 1, -1)), "nor the first level");
		helper.assertFalse(player.gameMode.destroyBlock(base.offset(-2, 0, 2)), "nor the second level");
		helper.assertTrue(level.getBlockState(beacon).is(Blocks.BEACON) && level.getBlockState(base.offset(-2, 0, 2)).is(Blocks.IRON_BLOCK), "still there");

		// Pistons: the loose block moves, the pyramid does not.
		helper.assertTrue(new PistonStructureResolver(level, loose.north(), Direction.SOUTH, true).resolve(), "a piston pushes the loose block");
		helper.assertFalse(new PistonStructureResolver(level, base.offset(2, 1, 0), Direction.WEST, true).resolve(), "but not the pyramid");

		helper.assertTrue(player.gameMode.destroyBlock(loose) && level.getBlockState(loose).isAir(), "the loose iron block breaks");
		BlockPos floor = ruin.boss.below();
		helper.assertTrue(player.gameMode.destroyBlock(floor) && level.getBlockState(floor).isAir(), "an ordinary block of the ruin breaks");

		// Explosions: the blocks around go, the sanctuary stays.
		away(level, player, ruin);
		BlockPos stone = base.offset(3, 0, 0);
		level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
		level.explode(null, base.getX() + 3.5, base.getY() + 1.5, base.getZ() + 0.5, 4.0F, Level.ExplosionInteraction.TNT);
		helper.assertTrue(level.getBlockState(stone).isAir(), "the explosion destroys an ordinary block next to the pyramid");
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				helper.assertTrue(level.getBlockState(base.offset(dx, 0, dz)).is(Blocks.IRON_BLOCK), "level 2 intact at " + dx + "," + dz);
			}
		}
		helper.assertTrue(level.getBlockState(base.offset(1, 1, 0)).is(Blocks.GOLD_BLOCK) && level.getBlockState(beacon).is(Blocks.BEACON), "beacon intact");
		helper.succeed();
	}
}
