package dev.guildy.test;

import dev.guildy.core.ScrollDrops;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.scroll.GrimoireItems;
import dev.guildy.scroll.GrimoireRules;
import dev.guildy.scroll.ScrollItems;
import dev.guildy.scroll.ScrollModule;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The Grand spectacle fireworks scroll through the real game paths (right click, vanilla channel to its end): the stage of
 * rune circles behind the caster, real rockets launched and burst over time, an audience standing on the launch circles (a
 * player, a villager, an armor stand) never hurt, not even by a show rocket exploding the vanilla way right on them (an
 * ordinary rocket does hurt), no block changed, the show over after its duration and no rocket left; one show at a time,
 * refused under a roof (scroll kept); from a grimoire page and, bigger and longer, from a cursed grimoire page.
 */
public class ShowGameTests {
	private static final String ENV = "guildy-test:day";
	private static final String TAG = "guildy_firework";
	private static final int RADIUS = 14;

	/** Player standing on {@code ground}, looking east, horizontally. */
	private static void face(ServerPlayer player, ServerLevel level, BlockPos ground) {
		player.teleportTo(level, ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, Set.of(), -90.0F, 0.0F, true);
	}

	/** Right click with {@code count} scrolls of {@code id} in the main hand; returns the stack in hand. */
	private static ItemStack rightClick(GameTestHelper helper, ServerPlayer player, String id, int count, boolean expectStart) {
		ItemStack stack = ScrollDrops.create(id, count);
		helper.assertTrue(ScrollItems.isScroll(stack) && stack.getCount() == count, "scroll " + id + " created");
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		InteractionResult result = UseItemCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND);
		if (expectStart) {
			helper.assertTrue(result instanceof InteractionResult.Success && player.isUsingItem(), id + ": incantation started, got " + result);
		} else {
			helper.assertTrue(result == InteractionResult.FAIL && !player.isUsingItem(), id + ": refused, got " + result);
		}
		return player.getMainHandItem();
	}

	/** Lets the vanilla use of the item run to its end (the mock player is not ticked by a connection). */
	private static void channel(ServerPlayer player) {
		for (int i = 0; i < 400 && player.isUsingItem(); i++) {
			player.doTick();
		}
	}

	/** Every block of the platform area, from just under it to 8 blocks above. */
	private static List<BlockState> blocks(ServerLevel level, BlockPos ground) {
		List<BlockState> states = new ArrayList<>();
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				for (int dy = -1; dy <= 8; dy++) {
					states.add(level.getBlockState(ground.offset(dx, dy, dz)));
				}
			}
		}
		return states;
	}

	/** The show's rockets around the stage. */
	private static List<FireworkRocketEntity> rockets(ServerLevel level, BlockPos ground) {
		AABB box = new AABB(ground).inflate(40);
		return level.getEntitiesOfClass(FireworkRocketEntity.class, box, rocket -> rocket.entityTags().contains(TAG));
	}

	/**
	 * A firework rocket with a large ball that hangs where it is put (shot "at an angle" with no speed: no lift) and explodes
	 * there by itself at the end of its vanilla lifetime (half a second to a second), hurting what stands within 5 blocks.
	 */
	private static FireworkRocketEntity rocket(ServerLevel level, Vec3 at, boolean show) {
		ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET);
		stack.set(DataComponents.FIREWORKS, new Fireworks(0, List.of(new FireworkExplosion(FireworkExplosion.Shape.LARGE_BALL,
				IntList.of(0xFF0000), IntList.of(), false, false))));
		FireworkRocketEntity rocket = new FireworkRocketEntity(level, stack, at.x, at.y, at.z, true);
		rocket.setDeltaMovement(Vec3.ZERO);
		if (show) {
			rocket.addTag(TAG);
		}
		level.addFreshEntity(rocket);
		return rocket;
	}

	private static Villager villager(ServerLevel level, Vec3 at) {
		Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
		villager.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		villager.setNoAi(true);
		villager.setPersistenceRequired();
		level.addFreshEntity(villager);
		return villager;
	}

	// ================================================================== the show from a scroll

	@GameTest(environment = ENV, maxTicks = 1300)
	public void grandSpectacleIsHarmlessAndEndsOnTime(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, RADIUS);
		TestSupport.forceChunks(level, ground, 2);
		ServerPlayer caster = TestSupport.survivor(helper, ground.east(10));
		Guild guild = TestSupport.foundGuild(helper, caster, ground.east(10), "Spectacle");
		face(caster, level, ground);

		// The stage: nine circles in a line north-south, three blocks behind (west of) the caster.
		List<Vec3> spots = ScrollModule.showStage(caster);
		helper.assertTrue(spots.size() == 9, "nine launch circles under the open sky, got " + spots.size());
		// The audience stands east, looking west: their left hand is south (+z).
		helper.assertTrue(spots.getFirst().z > spots.getLast().z, "left to right as the audience sees them: south to north");
		for (Vec3 spot : spots) {
			helper.assertTrue(spot.x < caster.getX() - 0.5 && Math.abs(spot.y - (ground.getY() + 1)) < 1.0E-6, "behind the caster, on the ground: " + spot);
		}
		helper.assertTrue(spots.getFirst().z - spots.getLast().z > 17.9, "18 blocks wide");

		// The audience on the circles: a player, a villager, an armor stand.
		ServerPlayer watcher = TestSupport.survivor(helper, ground, "Spectateur");
		Vec3 first = spots.getFirst();
		watcher.teleportTo(level, first.x, first.y, first.z, Set.of(), 90.0F, 0.0F, true);
		Villager villager = villager(level, spots.get(4));
		ArmorStand stand = new ArmorStand(level, spots.getLast().x, spots.getLast().y, spots.getLast().z);
		level.addFreshEntity(stand);
		// Far from them, a monster for an ordinary rocket.
		Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
		husk.snapTo(ground.getX() + 8.5, ground.getY() + 1, ground.getZ() + 8.5, 0.0F, 0.0F);
		husk.setNoAi(true);
		husk.setPersistenceRequired();
		husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
		husk.setHealth(100);
		level.addFreshEntity(husk);
		List<BlockState> before = blocks(level, ground);

		ItemStack stack = rightClick(helper, caster, "grand_spectacle", 2, true);
		channel(caster);
		helper.assertFalse(caster.isUsingItem(), "channel completed");
		helper.assertTrue(caster.getMainHandItem() == stack && stack.getCount() == 1, "exactly one scroll consumed, left " + stack.getCount());
		helper.assertTrue(caster.getCooldowns().isOnCooldown(stack), "cooldown started");
		long start = level.getGameTime();
		ScrollModule.ShowStatus status = ScrollModule.show(caster.getUUID());
		helper.assertTrue(status != null && status.points() == 9 && status.ticks() == 800 && !status.cursed(), "a 40 s show on 9 circles: " + status);
		helper.assertTrue(status.planned() >= 160, "a real show: " + status.planned() + " rockets planned");
		// One show at a time: a second scroll is refused at once (its group is on cooldown anyway).
		ItemStack again = rightClick(helper, caster, "grand_spectacle", 1, false);
		helper.assertTrue(again.getCount() == 1, "second scroll kept");

		// A show rocket exploding the vanilla way right on the audience, an ordinary one on the monster.
		rocket(level, villager.position().add(0, 0.5, 0), true);
		rocket(level, watcher.position().add(0, 0.5, 0), true);
		rocket(level, husk.position().add(0, 0.5, 0), false);
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(husk.getHealth() < 100, "an ordinary rocket hurts: " + husk.getHealth()))
				.thenWaitUntil(() -> {
					ScrollModule.ShowStatus now = ScrollModule.show(caster.getUUID());
					helper.assertTrue(now != null && now.launched() >= 10 && now.exploded() >= 5, "rockets launched and burst: " + now);
					List<FireworkRocketEntity> flying = rockets(level, ground);
					helper.assertFalse(flying.isEmpty(), "rockets in the sky");
					for (FireworkRocketEntity rocket : flying) {
						helper.assertTrue(rocket.noPhysics, "a show rocket crosses anything without hitting it");
					}
				})
				.thenExecute(() -> {
					helper.assertTrue(watcher.getHealth() == watcher.getMaxHealth(), "the player on a circle is not hurt: " + watcher.getHealth());
					helper.assertTrue(villager.getHealth() == villager.getMaxHealth(), "the villager is not hurt: " + villager.getHealth());
				})
				.thenWaitUntil(() -> helper.assertTrue(ScrollModule.show(caster.getUUID()) == null, "the show is over"))
				.thenExecute(() -> {
					long elapsed = level.getGameTime() - start;
					helper.assertTrue(elapsed >= 795 && elapsed <= 815, "over after its 40 s, took " + elapsed + " ticks");
					helper.assertTrue(rockets(level, ground).isEmpty(), "no rocket left");
					helper.assertTrue(watcher.isAlive() && watcher.getHealth() == watcher.getMaxHealth(), "the player was never hurt");
					helper.assertTrue(villager.isAlive() && villager.getHealth() == villager.getMaxHealth(), "the villager was never hurt");
					helper.assertTrue(!stand.isRemoved() && stand.getHealth() == stand.getMaxHealth(), "the armor stand stands");
					helper.assertTrue(before.equals(blocks(level, ground)), "no block changed");
					villager.discard();
					stand.discard();
					husk.discard();
					GuildManager.disband(guild);
				})
				.thenSucceed();
	}

	// ================================================================== the sky is needed

	@GameTest(environment = ENV, maxTicks = 200)
	public void grandSpectacleNeedsTheSky(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, RADIUS);
		TestSupport.forceChunks(level, ground, 2);
		ServerPlayer caster = TestSupport.survivor(helper, ground);
		face(caster, level, ground);
		// Indoors: a stone ceiling four blocks above the floor.
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				level.setBlockAndUpdate(ground.offset(dx, 5, dz), Blocks.STONE.defaultBlockState());
			}
		}
		helper.assertTrue(ScrollModule.showStage(caster).isEmpty(), "no launch point under a roof");
		ItemStack kept = rightClick(helper, caster, "grand_spectacle", 1, false);
		helper.assertTrue(kept.getCount() == 1 && !caster.getCooldowns().isOnCooldown(kept), "refused: scroll kept, no cooldown");
		// Half the roof gone: the circles under the sky only.
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = 0; dz <= RADIUS; dz++) {
				level.setBlockAndUpdate(ground.offset(dx, 5, dz), Blocks.AIR.defaultBlockState());
			}
		}
		List<Vec3> spots = ScrollModule.showStage(caster);
		helper.assertTrue(spots.size() >= 3 && spots.size() < 9, "only the circles under the sky: " + spots.size());
		for (Vec3 spot : spots) {
			helper.assertTrue(spot.z >= ground.getZ(), "under the open half: " + spot);
		}
		rightClick(helper, caster, "grand_spectacle", 1, true);
		channel(caster);
		ScrollModule.ShowStatus status = ScrollModule.show(caster.getUUID());
		helper.assertTrue(status != null && status.points() == spots.size(), "a show on the open circles: " + status);
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(ScrollModule.show(caster.getUUID()).launched() > 0, "it starts"))
				.thenExecute(() -> {
					ScrollModule.endShow(caster.getUUID());
					helper.assertTrue(ScrollModule.show(caster.getUUID()) == null, "ended");
				})
				.thenIdle(2)
				.thenExecute(() -> helper.assertTrue(rockets(level, ground).isEmpty(), "its rockets are gone"))
				.thenSucceed();
	}

	// ================================================================== grimoires

	/** Casts the selected page of the grimoire in hand (cooldown cleared, fed and healed first). */
	private static InteractionResult castPage(GameTestHelper helper, ServerPlayer caster, ItemStack grimoire, boolean cursed, boolean expectStart) {
		caster.setItemInHand(InteractionHand.MAIN_HAND, grimoire);
		caster.getCooldowns().removeCooldown(cursed ? GrimoireItems.CURSED_COOLDOWN_GROUP : GrimoireItems.COOLDOWN_GROUP);
		caster.getFoodData().setFoodLevel(20);
		caster.getFoodData().setSaturation(0.0F);
		caster.setHealth(caster.getMaxHealth());
		InteractionResult result = UseItemCallback.EVENT.invoker().interact(caster, caster.level(), InteractionHand.MAIN_HAND);
		if (expectStart) {
			helper.assertTrue(result instanceof InteractionResult.Success && caster.isUsingItem(), "incantation started, got " + result);
			channel(caster);
			helper.assertTrue(caster.getMainHandItem() == grimoire, "the grimoire stays");
			helper.assertTrue(caster.getCooldowns().isOnCooldown(grimoire), "cast (grimoire cooldown)");
		} else {
			helper.assertTrue(result == InteractionResult.FAIL && !caster.isUsingItem(), "refused, got " + result);
		}
		return result;
	}

	@GameTest(environment = ENV, maxTicks = 300)
	public void grandSpectacleFromGrimoires(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, RADIUS);
		TestSupport.forceChunks(level, ground, 2);
		ServerPlayer caster = TestSupport.survivor(helper, ground);
		face(caster, level, ground);
		ItemStack grimoire = GrimoireItems.create(new GrimoireRules.Pages(List.of("grand_spectacle"), 0));
		castPage(helper, caster, grimoire, false, true);
		ScrollModule.ShowStatus status = ScrollModule.show(caster.getUUID());
		helper.assertTrue(status != null && status.points() == 9 && status.ticks() == 800 && !status.cursed(), "the grimoire's show: " + status);
		int food = caster.getFoodData().getFoodLevel();
		helper.assertTrue(food < 20, "it cost food: " + food);
		// While it plays, the page is refused (nothing spent).
		castPage(helper, caster, grimoire, false, false);
		helper.assertTrue(caster.getFoodData().getFoodLevel() == 20, "a refused page costs nothing");

		ItemStack cursed = GrimoireItems.create(new GrimoireRules.Pages(List.of("grand_spectacle"), 0), true);
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(ScrollModule.show(caster.getUUID()).launched() > 0, "the grimoire's show starts"))
				.thenExecute(() -> {
					ScrollModule.endShow(caster.getUUID());
					// The cursed grimoire: a bigger, longer show.
					castPage(helper, caster, cursed, true, true);
					ScrollModule.ShowStatus dark = ScrollModule.show(caster.getUUID());
					helper.assertTrue(dark != null && dark.cursed(), "the cursed show: " + dark);
					helper.assertTrue(dark.ticks() == 1400, "70 s (40 s x1.75), got " + dark.ticks());
					helper.assertTrue(dark.points() == 14, "14 circles (9 x1.5) over a wider stage, got " + dark.points());
					helper.assertTrue(caster.getHealth() < caster.getMaxHealth(), "paid in blood");
				})
				.thenWaitUntil(() -> helper.assertTrue(ScrollModule.show(caster.getUUID()).exploded() > 0, "the cursed show bursts"))
				.thenExecute(() -> ScrollModule.endShow(caster.getUUID()))
				.thenIdle(2)
				.thenExecute(() -> helper.assertTrue(rockets(level, ground).isEmpty(), "no rocket left"))
				.thenSucceed();
	}
}
