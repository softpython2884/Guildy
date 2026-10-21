package dev.guildy.test;

import dev.guildy.core.ScrollDrops;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.scroll.GrimoireItems;
import dev.guildy.scroll.GrimoireRules;
import dev.guildy.scroll.ScrollItems;
import dev.guildy.scroll.ScrollModule;
import dev.guildy.scroll.Vulnerability;
import dev.guildy.villager.VillagerManager;
import dev.guildy.villager.VillagerRecord;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The tactical spell scrolls of Guildy 1.12, cast through the real game paths (right click, vanilla channel ticked to its
 * end) by a survival player of a guild, next to a villager of their guild, a player of no guild and monsters: the war banner
 * buffs allies only, the scourge makes monsters take more damage (bosses less), the permutation swaps places and refuses an
 * unsafe landing, the rally teleports only the member who clicked [Rejoindre], the spectral chains hold monsters in place,
 * the tailwind hastens allies and cushions their falls, and every new spell works from a grimoire and a cursed grimoire page.
 */
public class TacticalScrollGameTests {
	private static final String ENV = "guildy-test:day";

	/** A caster in a guild, one villager of that guild, a player of no guild, monsters. */
	private record Scene(ServerLevel level, BlockPos ground, ServerPlayer caster, Guild guild, Villager villager, ServerPlayer stranger,
			List<Husk> husks) {
	}

	/**
	 * The guild heart 10 blocks west, the caster on {@code ground} facing east, the villager and the other player at their
	 * offsets, monsters (no AI, 100 health points) at the given distances east of the caster.
	 */
	private static Scene scene(GameTestHelper helper, String prefix, BlockPos villagerOffset, BlockPos strangerOffset, int... huskEast) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 13);
		TestSupport.forceChunks(level, ground, 2);
		ServerPlayer caster = TestSupport.survivor(helper, ground.west(10));
		Guild guild = TestSupport.foundGuild(helper, caster, ground.west(10), prefix);
		face(caster, level, ground);
		Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
		BlockPos v = ground.offset(villagerOffset);
		villager.snapTo(v.getX() + 0.5, v.getY() + 1, v.getZ() + 0.5, 0.0F, 0.0F);
		villager.setNoAi(true);
		villager.setPersistenceRequired();
		villager.addTag(VillagerManager.TAG_VILLAGER);
		guild.villagers.members.put(villager.getUUID(), new VillagerRecord(villager.getUUID(), "Testeur"));
		level.addFreshEntity(villager);
		ServerPlayer stranger = TestSupport.survivor(helper, ground.offset(strangerOffset));
		List<Husk> husks = new ArrayList<>();
		for (int east : huskEast) {
			husks.add(husk(level, ground.east(east)));
		}
		return new Scene(level, ground, caster, guild, villager, stranger, husks);
	}

	/** Player standing on {@code ground}, looking east, horizontally. */
	private static void face(ServerPlayer player, ServerLevel level, BlockPos ground) {
		player.teleportTo(level, ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, Set.of(), -90.0F, 0.0F, true);
	}

	/** A husk without AI and with 100 health points standing on {@code ground}. */
	private static Husk husk(ServerLevel level, BlockPos ground) {
		Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
		husk.snapTo(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, 90.0F, 0.0F);
		husk.setNoAi(true);
		husk.setPersistenceRequired();
		husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
		husk.setHealth(100);
		level.addFreshEntity(husk);
		return husk;
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

	/** Casts {@code id} (2 in hand): exactly one consumed, the cooldown started. */
	private static void cast(GameTestHelper helper, ServerPlayer caster, String id) {
		ItemStack stack = rightClick(helper, caster, id, 2, true);
		channel(caster);
		helper.assertFalse(caster.isUsingItem(), id + ": channel completed");
		ItemStack hand = caster.getMainHandItem();
		helper.assertTrue(hand == stack && hand.getCount() == 1, id + ": exactly one scroll consumed, left " + hand.getCount());
		helper.assertTrue(caster.getCooldowns().isOnCooldown(hand), id + ": cooldown started");
	}

	/** Health points lost to {@code amount} of magic damage (armor ignored), the invulnerability frames cleared first. */
	private static float magicHit(ServerLevel level, LivingEntity target, float amount) {
		target.damageCooldownTime = 0;
		float before = target.getHealth();
		target.hurtServer(level, level.damageSources().magic(), amount);
		return before - target.getHealth();
	}

	private static boolean hurt(LivingEntity entity) {
		return entity.isRemoved() || entity.isDeadOrDying() || entity.getHealth() < entity.getMaxHealth();
	}

	private static void cleanup(Scene scene) {
		for (Husk husk : scene.husks()) {
			husk.discard();
		}
		scene.villager().discard();
		scene.guild().villagers.members.remove(scene.villager().getUUID());
		GuildManager.disband(scene.guild());
	}

	// ================================================================== Bannière de guerre

	@GameTest(environment = ENV, maxTicks = 120)
	public void warBannerBuffsAlliesOnly(GameTestHelper helper) {
		Scene scene = scene(helper, "Banniere", new BlockPos(2, 0, 1), new BlockPos(-2, 0, 0), 3);
		cast(helper, scene.caster(), "banniere_de_guerre");
		Husk husk = scene.husks().getFirst();
		helper.succeedWhen(() -> {
			MobEffectInstance strength = scene.caster().getEffect(MobEffects.STRENGTH);
			helper.assertTrue(strength != null && strength.getAmplifier() == 1, "the caster fights with Strength II: " + strength);
			helper.assertTrue(scene.caster().hasEffect(MobEffects.HASTE), "and Haste (faster attacks)");
			helper.assertTrue(scene.villager().hasEffect(MobEffects.STRENGTH) && scene.villager().hasEffect(MobEffects.HASTE),
					"the guild villager is rallied");
			helper.assertTrue(scene.stranger().getActiveEffects().isEmpty(), "a player of no guild is not: " + scene.stranger().getActiveEffects());
			helper.assertFalse(husk.hasEffect(MobEffects.STRENGTH) || husk.hasEffect(MobEffects.HASTE), "monsters never");
			cleanup(scene);
		});
	}

	// ================================================================== Fléau

	@GameTest(environment = ENV, maxTicks = 160)
	public void scourgeMakesMonstersTakeMoreDamage(GameTestHelper helper) {
		// The aimed husk (8 east), a boss next to it, a husk far outside the miasma.
		Scene scene = scene(helper, "Fleau", new BlockPos(3, 0, 1), new BlockPos(-2, 0, 0), 8);
		Husk cursed = scene.husks().getFirst();
		Husk boss = husk(scene.level(), scene.ground().east(9).north(2));
		boss.addTag("guildy_ruin_boss");
		Husk outside = husk(scene.level(), scene.ground().east(8).south(11));
		scene.husks().add(boss);
		scene.husks().add(outside);
		cast(helper, scene.caster(), "fleau");
		helper.startSequence()
				.thenWaitUntil(() -> {
					helper.assertTrue(cursed.hasEffect(MobEffects.GLOWING) && cursed.hasEffect(MobEffects.WEAKNESS)
							&& cursed.hasEffect(MobEffects.SLOWNESS), "the monster in the miasma is cursed");
					helper.assertTrue(Vulnerability.percent(boss) > 0, "the boss too");
				})
				.thenExecute(() -> {
					helper.assertTrue(Math.abs(Vulnerability.percent(cursed) - 25) < 1.0E-6, "+25 %: " + Vulnerability.percent(cursed));
					helper.assertTrue(Math.abs(Vulnerability.percent(boss) - 12.5) < 1.0E-6, "a boss gets half: " + Vulnerability.percent(boss));
					helper.assertTrue(Vulnerability.percent(outside) == 0 && !outside.hasEffect(MobEffects.GLOWING), "outside the area: untouched");
					float control = magicHit(scene.level(), outside, 8.0F);
					float more = magicHit(scene.level(), cursed, 8.0F);
					float bossLost = magicHit(scene.level(), boss, 8.0F);
					helper.assertTrue(Math.abs(control - 8.0F) < 0.01F, "a normal monster loses 8, lost " + control);
					helper.assertTrue(Math.abs(more - 10.0F) < 0.01F, "the cursed monster loses 10 (+25 %), lost " + more);
					helper.assertTrue(Math.abs(bossLost - 9.0F) < 0.01F, "the boss loses 9 (+12.5 %), lost " + bossLost);
					helper.assertTrue(scene.villager().getActiveEffects().isEmpty() && Vulnerability.percent(scene.villager()) == 0,
							"the guild villager is spared");
					helper.assertTrue(scene.stranger().getActiveEffects().isEmpty() && Vulnerability.percent(scene.stranger()) == 0,
							"players are spared");
					helper.assertTrue(magicHit(scene.level(), scene.villager(), 2.0F) <= 2.0F, "no extra damage on the villager");
					cleanup(scene);
				})
				.thenSucceed();
	}

	// ================================================================== Permutation

	@GameTest(environment = ENV, maxTicks = 160)
	public void permutationSwapsAndRefusesUnsafeSpots(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 13);
		TestSupport.forceChunks(level, ground, 2);
		ServerPlayer caster = TestSupport.survivor(helper, ground.west(10));
		Guild guild = TestSupport.foundGuild(helper, caster, ground.west(10), "Permutation");
		face(caster, level, ground);

		// A monster standing on magma: never there, refused at the start, the scroll is kept.
		level.setBlockAndUpdate(ground.east(6), Blocks.MAGMA_BLOCK.defaultBlockState());
		Husk hot = husk(level, ground.east(6));
		ItemStack kept = rightClick(helper, caster, "permutation", 2, false);
		helper.assertTrue(kept.getCount() == 2 && !caster.getCooldowns().isOnCooldown(kept), "unsafe: scroll kept, no cooldown");
		hot.discard();
		level.setBlockAndUpdate(ground.east(6), Blocks.STONE.defaultBlockState());
		// A player of no guild in sight is no target.
		ServerPlayer stranger = TestSupport.survivor(helper, ground.east(4));
		ItemStack again = rightClick(helper, caster, "permutation", 2, false);
		helper.assertTrue(again.getCount() == 2, "a stranger cannot be swapped");
		stranger.teleportTo(level, ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 6.5, Set.of(), 0.0F, 0.0F, true);

		// A monster 8 blocks east: both trade places, the monster is stunned.
		Husk husk = husk(level, ground.east(8));
		Vec3 casterBefore = caster.position();
		Vec3 huskBefore = husk.position();
		Vec3 strangerBefore = stranger.position();
		cast(helper, caster, "permutation");
		helper.assertTrue(caster.position().distanceTo(huskBefore) < 0.3, "the caster stands where the monster was: " + caster.position());
		helper.assertTrue(husk.position().distanceTo(casterBefore) < 0.3, "the monster stands where the caster was: " + husk.position());
		helper.assertTrue(husk.hasEffect(MobEffects.SLOWNESS), "the swapped monster is stunned");
		helper.assertTrue(stranger.position().distanceTo(strangerBefore) < 0.1, "nobody else moved");

		// A guild member in danger, between the caster and the monster: rescued, shielded.
		ServerPlayer member = TestSupport.survivor(helper, BlockPos.containing(caster.position()).below().west(4));
		GuildManager.addMember(guild, member.getUUID(), "membre");
		caster.getCooldowns().removeCooldown(ScrollItems.cooldownGroup(ScrollModule.config().scroll("permutation")));
		Vec3 casterSpot = caster.position();
		Vec3 memberSpot = member.position();
		cast(helper, caster, "permutation");
		helper.assertTrue(caster.position().distanceTo(memberSpot) < 0.3, "the caster took the member's place: " + caster.position());
		helper.assertTrue(member.position().distanceTo(casterSpot) < 0.3, "the member is safe at the caster's spot: " + member.position());
		helper.assertTrue(member.hasEffect(MobEffects.RESISTANCE), "and shielded");
		helper.runAfterDelay(30, () -> {
			husk.discard();
			GuildManager.disband(guild);
			helper.succeed();
		});
	}

	// ================================================================== Ralliement

	@GameTest(environment = ENV, maxTicks = 300)
	public void rallyTeleportsOnlyWhoClicked(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 13);
		TestSupport.forceChunks(level, ground, 2);
		ServerPlayer caster = TestSupport.survivor(helper, ground.west(10));
		Guild guild = TestSupport.foundGuild(helper, caster, ground.west(10), "Ralliement");
		face(caster, level, ground);
		ServerPlayer clicker = TestSupport.survivor(helper, ground.east(12));
		ServerPlayer waiting = TestSupport.survivor(helper, ground.north(12));
		ServerPlayer stranger = TestSupport.survivor(helper, ground.south(12));
		GuildManager.addMember(guild, clicker.getUUID(), "membre");
		GuildManager.addMember(guild, waiting.getUUID(), "membre");
		Vec3 clickerBefore = clicker.position();
		Vec3 waitingBefore = waiting.position();
		Vec3 strangerBefore = stranger.position();

		cast(helper, caster, "ralliement");
		String command = ScrollModule.pendingRally(clicker.getUUID());
		helper.assertTrue(command != null, "the member received [Rejoindre]");
		helper.assertTrue(ScrollModule.pendingRally(waiting.getUUID()) != null, "every member around was called");
		helper.assertTrue(ScrollModule.pendingRally(stranger.getUUID()) == null, "a player of no guild is not");
		helper.assertTrue(clicker.position().distanceTo(clickerBefore) < 0.1 && waiting.position().distanceTo(waitingBefore) < 0.1,
				"nobody moved without clicking");
		// The stranger tries the command: nothing happens.
		TestSupport.console(level, "execute as " + stranger.getStringUUID() + " run " + command);
		helper.assertTrue(stranger.position().distanceTo(strangerBefore) < 0.1, "not called: not teleported");
		// One member clicks [Rejoindre].
		TestSupport.console(level, "execute as " + clicker.getStringUUID() + " run " + command);
		helper.assertTrue(clicker.position().distanceTo(caster.position()) < 3.5, "the member who clicked joined the caster: " + clicker.position());
		helper.assertTrue(waiting.position().distanceTo(waitingBefore) < 0.1, "the other member stays until they click");
		helper.assertTrue(ScrollModule.pendingRally(clicker.getUUID()) == null, "joined: nothing pending any more");
		helper.assertTrue(ScrollModule.pendingRally(waiting.getUUID()) != null, "the call stays open for the others");
		// A second click of the same member does nothing.
		Vec3 joined = clicker.position();
		TestSupport.console(level, "execute as " + clicker.getStringUUID() + " run " + command);
		helper.assertTrue(clicker.position().distanceTo(joined) < 0.1, "already joined: no second teleport");
		helper.runAfterDelay(10, () -> {
			GuildManager.disband(guild);
			helper.succeed();
		});
	}

	// ================================================================== Chaînes spectrales

	@GameTest(environment = ENV, maxTicks = 240)
	public void spectralChainsHoldThenShatter(GameTestHelper helper) {
		// Two husks close together (8 and 10 east), one far away.
		Scene scene = scene(helper, "Chaines", new BlockPos(3, 0, 2), new BlockPos(-2, 0, 0), 8, 10);
		Husk far = husk(scene.level(), scene.ground().east(8).south(10));
		scene.husks().add(far);
		Husk aimed = scene.husks().get(0);
		Husk neighbour = scene.husks().get(1);
		Vec3 aimedSpot = aimed.position();
		Vec3 neighbourSpot = neighbour.position();
		Vec3 farSpot = far.position();
		cast(helper, scene.caster(), "chaines_spectrales");
		helper.startSequence()
				.thenIdle(15)
				.thenExecute(() -> {
					// Pushed away: the chains pull them back; the far monster is free.
					aimed.teleportTo(aimedSpot.x + 3, aimedSpot.y, aimedSpot.z);
					neighbour.teleportTo(neighbourSpot.x, neighbourSpot.y, neighbourSpot.z + 3);
					far.teleportTo(farSpot.x + 3, farSpot.y, farSpot.z);
				})
				.thenIdle(3)
				.thenExecute(() -> {
					helper.assertTrue(aimed.position().distanceTo(aimedSpot) < 0.3, "the aimed monster is held: " + aimed.position());
					helper.assertTrue(neighbour.position().distanceTo(neighbourSpot) < 0.3, "its neighbour too: " + neighbour.position());
					helper.assertTrue(far.position().distanceTo(farSpot) > 2.5, "a monster out of reach is free");
					helper.assertFalse(hurt(aimed) || hurt(neighbour), "no damage while they are held");
				})
				.thenWaitUntil(() -> {
					helper.assertTrue(hurt(aimed) && hurt(neighbour), "the chains shatter on them");
				})
				.thenExecute(() -> {
					helper.assertFalse(hurt(far), "the far monster is untouched");
					helper.assertTrue(scene.villager().getActiveEffects().isEmpty() && !hurt(scene.villager()), "the guild villager is spared");
					helper.assertTrue(scene.stranger().getActiveEffects().isEmpty(), "players are spared");
					cleanup(scene);
				})
				.thenSucceed();
	}

	// ================================================================== Hâte du vent

	@GameTest(environment = ENV, maxTicks = 120)
	public void tailwindHastensAlliesAndCushionsFalls(GameTestHelper helper) {
		Scene scene = scene(helper, "Vent", new BlockPos(2, 0, 1), new BlockPos(-2, 0, 0), 3);
		cast(helper, scene.caster(), "hate_du_vent");
		Husk husk = scene.husks().getFirst();
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(scene.caster().hasEffect(MobEffects.SPEED), "the caster is hastened"))
				.thenExecute(() -> {
					helper.assertTrue(scene.caster().hasEffect(MobEffects.JUMP_BOOST), "and jumps higher");
					helper.assertTrue(scene.villager().hasEffect(MobEffects.SPEED), "the guild villager too");
					helper.assertTrue(scene.stranger().getActiveEffects().isEmpty(), "not a player of no guild");
					helper.assertFalse(husk.hasEffect(MobEffects.SPEED), "never a monster");
					// (Mock players ignore every damage until their client has loaded: the falls are checked on mobs.)
					float villagerBefore = scene.villager().getHealth();
					scene.villager().hurtServer(scene.level(), scene.level().damageSources().fall(), 6.0F);
					helper.assertTrue(scene.villager().getHealth() == villagerBefore, "the wind cushions the fall of a blessed ally");
					float huskBefore = husk.getHealth();
					scene.villager().damageCooldownTime = 0;
					scene.villager().hurtServer(scene.level(), scene.level().damageSources().generic(), 2.0F);
					helper.assertTrue(scene.villager().getHealth() < villagerBefore, "only falls: other damage still hurts");
					husk.hurtServer(scene.level(), scene.level().damageSources().fall(), 6.0F);
					helper.assertTrue(husk.getHealth() < huskBefore, "a monster's fall is not cushioned");
					cleanup(scene);
				})
				.thenSucceed();
	}

	// ================================================================== from a grimoire

	private static void feed(ServerPlayer player) {
		player.getFoodData().setFoodLevel(20);
		player.getFoodData().setSaturation(0.0F);
	}

	/** Casts the selected page of the grimoire in hand (cooldown cleared, fed first). */
	private static void castPage(GameTestHelper helper, ServerPlayer caster, ItemStack grimoire, int page, boolean cursed) {
		GrimoireItems.apply(grimoire, GrimoireItems.pages(grimoire).select(page));
		caster.setItemInHand(InteractionHand.MAIN_HAND, grimoire);
		caster.getCooldowns().removeCooldown(cursed ? GrimoireItems.CURSED_COOLDOWN_GROUP : GrimoireItems.COOLDOWN_GROUP);
		feed(caster);
		caster.setHealth(caster.getMaxHealth());
		String spell = GrimoireItems.pages(grimoire).selectedSpell();
		InteractionResult result = UseItemCallback.EVENT.invoker().interact(caster, caster.level(), InteractionHand.MAIN_HAND);
		helper.assertTrue(result instanceof InteractionResult.Success && caster.isUsingItem(), spell + ": incantation started, got " + result);
		channel(caster);
		helper.assertTrue(caster.getMainHandItem() == grimoire, spell + ": the grimoire stays");
		helper.assertTrue(caster.getCooldowns().isOnCooldown(grimoire), spell + ": cast (grimoire cooldown)");
	}

	@GameTest(environment = ENV, maxTicks = 240)
	public void newSpellsFromAGrimoire(GameTestHelper helper) {
		Scene scene = scene(helper, "Grimoire neuf", new BlockPos(2, 0, 2), new BlockPos(-2, 0, 0), 8);
		Husk husk = scene.husks().getFirst();
		Vec3 huskSpot = husk.position();
		ItemStack grimoire = GrimoireItems.create(new GrimoireRules.Pages(List.of("banniere_de_guerre", "hate_du_vent", "chaines_spectrales"), 0));
		castPage(helper, scene.caster(), grimoire, 0, false);
		castPage(helper, scene.caster(), grimoire, 1, false);
		castPage(helper, scene.caster(), grimoire, 2, false);
		helper.startSequence()
				.thenIdle(10)
				.thenExecute(() -> {
					helper.assertTrue(scene.caster().hasEffect(MobEffects.STRENGTH), "page 1: the war banner");
					helper.assertTrue(scene.caster().hasEffect(MobEffects.SPEED), "page 2: the tailwind");
					husk.teleportTo(huskSpot.x + 3, huskSpot.y, huskSpot.z);
				})
				.thenIdle(3)
				.thenExecute(() -> helper.assertTrue(husk.position().distanceTo(huskSpot) < 0.3, "page 3: the chains hold the monster"))
				.thenWaitUntil(() -> helper.assertTrue(hurt(husk), "page 3: the chains shatter"))
				.thenExecute(() -> {
					helper.assertTrue(scene.stranger().getActiveEffects().isEmpty(), "players of no guild are spared");
					cleanup(scene);
				})
				.thenSucceed();
	}

	@GameTest(environment = ENV, maxTicks = 300)
	public void newSpellsFromACursedGrimoire(GameTestHelper helper) {
		Scene scene = scene(helper, "Grimoire maudit", new BlockPos(2, 0, 3), new BlockPos(-2, 0, 3), 8);
		Husk husk = scene.husks().getFirst();
		ServerPlayer member = TestSupport.survivor(helper, scene.ground().north(12));
		GuildManager.addMember(scene.guild(), member.getUUID(), "membre");
		ItemStack grimoire = GrimoireItems.create(new GrimoireRules.Pages(List.of("fleau", "permutation", "ralliement"), 0), true);
		helper.assertTrue(GrimoireItems.isCursed(grimoire), "a cursed grimoire");
		// Page 1: the scourge, amplified (+35 % instead of +25 %).
		castPage(helper, scene.caster(), grimoire, 0, true);
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(Vulnerability.percent(husk) > 0, "page 1: the monster is cursed"))
				.thenExecute(() -> {
					helper.assertTrue(Math.abs(Vulnerability.percent(husk) - 35) < 1.0E-6, "cursed: +35 %, got " + Vulnerability.percent(husk));
					float lost = magicHit(scene.level(), husk, 8.0F);
					helper.assertTrue(Math.abs(lost - 10.8F) < 0.01F, "8 magic damage -> 10.8, lost " + lost);
					// Page 2: the permutation with the cursed monster.
					Vec3 casterBefore = scene.caster().position();
					Vec3 huskBefore = husk.position();
					castPage(helper, scene.caster(), grimoire, 1, true);
					helper.assertTrue(scene.caster().position().distanceTo(huskBefore) < 0.3, "page 2: swapped with the monster");
					helper.assertTrue(husk.position().distanceTo(casterBefore) < 0.3, "page 2: the monster took the caster's spot");
					// Page 3: the rally calls the member far away.
					castPage(helper, scene.caster(), grimoire, 2, true);
					helper.assertTrue(ScrollModule.pendingRally(member.getUUID()) != null, "page 3: the member received [Rejoindre]");
					helper.assertTrue(ScrollModule.pendingRally(scene.stranger().getUUID()) == null, "not the stranger");
					cleanup(scene);
				})
				.thenSucceed();
	}
}
