package dev.guildy.test;

import dev.guildy.ruin.CursedFights;
import dev.guildy.ruin.Encounters;
import dev.guildy.ruin.RuinConfig;
import dev.guildy.ruin.RuinIndex;
import dev.guildy.ruin.RuinModule;
import dev.guildy.ruin.RuinRecord;
import dev.guildy.ruin.RuinState;
import dev.guildy.scroll.GrimoireItems;
import dev.guildy.scroll.GrimoireRules;
import dev.guildy.scroll.ScrollConfig;
import dev.guildy.scroll.ScrollModule;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What one cast does to one target, through the real game paths (grimoire and cursed grimoire pages cast by a survival
 * player, vanilla channel ticked to its end): one meteor shower on a tough ordinary monster (no cap, diminishing returns
 * only), on a group of horde-like monsters (all crushed), and four cursed meteor showers on the cursed Archimage déchu at
 * threat X (each one takes about the cursed grimoire's boss cap, it stays well alive in its first phase).
 */
public class SpellBalanceGameTests {
	private static final Logger LOGGER = LoggerFactory.getLogger("GuildyTest");
	private static final String ENV = "guildy-test:day";
	private static final String METEORS = "pluie_de_meteores";

	/** Lets the vanilla use of the item run to its end (the mock player is not ticked by a connection). */
	private static void channel(ServerPlayer player) {
		for (int i = 0; i < 400 && player.isUsingItem(); i++) {
			player.doTick();
		}
	}

	/** The player turned towards the middle of {@code target} (aimed through the crosshair). */
	private static void lookAt(ServerPlayer player, LivingEntity target) {
		Vec3 eye = player.getEyePosition();
		Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
		double dx = to.x - eye.x;
		double dy = to.y - eye.y;
		double dz = to.z - eye.z;
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		player.teleportTo((ServerLevel) player.level(), player.getX(), player.getY(), player.getZ(), Set.of(), yaw, pitch, true);
	}

	/** Casts the meteor page of the (cursed) grimoire in hand at {@code target}: cooldown cleared, fed and healed first. */
	private static void castMeteors(GameTestHelper helper, ServerPlayer caster, ItemStack grimoire, boolean cursed, LivingEntity target) {
		caster.setItemInHand(InteractionHand.MAIN_HAND, grimoire);
		caster.getCooldowns().removeCooldown(cursed ? GrimoireItems.CURSED_COOLDOWN_GROUP : GrimoireItems.COOLDOWN_GROUP);
		caster.getFoodData().setFoodLevel(20);
		caster.getFoodData().setSaturation(20.0F);
		caster.setHealth(caster.getMaxHealth());
		lookAt(caster, target);
		InteractionResult result = UseItemCallback.EVENT.invoker().interact(caster, caster.level(), InteractionHand.MAIN_HAND);
		helper.assertTrue(result instanceof InteractionResult.Success && caster.isUsingItem(), "incantation started, got " + result);
		channel(caster);
		helper.assertTrue(caster.getCooldowns().isOnCooldown(grimoire), "cast (grimoire cooldown)");
	}

	/** Meteors of one cast of the scroll from this grimoire. */
	private static int meteors(boolean cursed) {
		ScrollConfig cfg = ScrollModule.config();
		ScrollConfig.ScrollDef def = cfg.scroll(METEORS);
		return cursed ? (int) Math.round(cfg.cursedGrimoire.power(def.spell()).apply(def.spell().param("count"), def.param("count")))
				: def.intParam("count");
	}

	/** A husk without AI, immune to fire (the burn is not the spell), with {@code health} points, standing on {@code ground}. */
	private static Husk dummy(ServerLevel level, BlockPos ground, double health) {
		Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
		husk.snapTo(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, 90.0F, 0.0F);
		husk.setNoAi(true);
		husk.setPersistenceRequired();
		husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		husk.setHealth((float) health);
		husk.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		level.addFreshEntity(husk);
		return husk;
	}

	// ================================================================== one tough monster

	@GameTest(environment = ENV, maxTicks = 500)
	public void meteorShowerOnOneToughMonster(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 14);
		TestSupport.forceChunks(level, ground, 3);
		ServerPlayer normal = TestSupport.survivor(helper, ground.north(10), TestSupport.uniqueName("met"));
		ServerPlayer cursed = TestSupport.survivor(helper, ground.south(10), TestSupport.uniqueName("mal"));
		Husk normalTarget = dummy(level, ground.north(10).east(12), 1024);
		Husk cursedTarget = dummy(level, ground.south(10).east(12), 1024);
		int normalBefore = ScrollModule.meteorImpacts(normal.getUUID());
		int cursedBefore = ScrollModule.meteorImpacts(cursed.getUUID());
		castMeteors(helper, normal, GrimoireItems.create(new GrimoireRules.Pages(List.of(METEORS), 0)), false, normalTarget);
		castMeteors(helper, cursed, GrimoireItems.create(new GrimoireRules.Pages(List.of(METEORS), 0), true), true, cursedTarget);
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(ScrollModule.meteorImpacts(normal.getUUID()) - normalBefore >= meteors(false)
						&& ScrollModule.meteorImpacts(cursed.getUUID()) - cursedBefore >= meteors(true), "waiting for every meteor"))
				.thenIdle(5)
				.thenExecute(() -> {
					double normalLost = 1024 - normalTarget.getHealth();
					double cursedLost = 1024 - cursedTarget.getHealth();
					LOGGER.info("[GuildyTest] [balance] one meteor shower on one 1024 HP monster: grimoire {} HP ({} meteors), cursed {} HP ({} meteors)",
							Math.round(normalLost), meteors(false), Math.round(cursedLost), meteors(true));
					ScrollConfig cfg = ScrollModule.config();
					// Not a boss: no cap, only the diminishing returns (the first meteors hit whole, the next ones less).
					helper.assertTrue(cursedLost > 2 * cfg.cursedGrimoire.bossCastCap * 1024, "an ordinary monster is not capped like a boss: " + cursedLost);
					helper.assertTrue(normalLost > 2 * cfg.limits.bossCastCap * 1024, "nor by a grimoire: " + normalLost);
					helper.assertTrue(cursedLost < 600, "but each further meteor on it hurts less (about 900 before): " + cursedLost);
					helper.assertTrue(cursedLost > normalLost, "the cursed grimoire stays stronger");
					normalTarget.discard();
					cursedTarget.discard();
				})
				.thenSucceed();
	}

	// ================================================================== a group of ordinary monsters

	@GameTest(environment = ENV, maxTicks = 400)
	public void cursedMeteorShowerStillClearsAGroup(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos ground = TestSupport.region(level, 14);
		TestSupport.forceChunks(level, ground, 3);
		ServerPlayer caster = TestSupport.survivor(helper, ground.west(12), TestSupport.uniqueName("horde"));
		// Ten monsters of 40 health points (a horde's), spread around the aimed one.
		List<Husk> group = new ArrayList<>();
		group.add(dummy(level, ground, 40));
		int[][] spots = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {2, 2}, {-2, -2}, {2, -2}, {-2, 2}, {5, 1}};
		for (int[] spot : spots) {
			group.add(dummy(level, ground.offset(spot[0], 0, spot[1]), 40));
		}
		int before = ScrollModule.meteorImpacts(caster.getUUID());
		castMeteors(helper, caster, GrimoireItems.create(new GrimoireRules.Pages(List.of(METEORS), 0), true), true, group.getFirst());
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(ScrollModule.meteorImpacts(caster.getUUID()) - before >= meteors(true), "waiting for every meteor"))
				.thenIdle(5)
				.thenExecute(() -> {
					long alive = group.stream().filter(LivingEntity::isAlive).count();
					LOGGER.info("[GuildyTest] [balance] one cursed meteor shower on 10 monsters of 40 HP: {} left alive", alive);
					helper.assertTrue(alive == 0, "the whole group is crushed: " + alive + " left");
					group.forEach(Husk::discard);
				})
				.thenSucceed();
	}

	// ================================================================== a cursed guardian at threat X

	/** A placed, indexed test ruin with its boss marker. */
	private static RuinRecord ruin(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos center = TestSupport.region(level, 0);
		TestSupport.forceChunks(level, center, 3);
		StructureStart start = RuinGameTests.generate(level, center);
		RuinGameTests.place(level, start);
		RuinIndex.processPending(level.getServer());
		RuinRecord ruin = RuinIndex.register(level, start);
		helper.assertTrue(ruin != null && ruin.boss != null, "test ruin indexed with its boss marker");
		helper.assertTrue(ruin.state == RuinState.DORMANT, "dormant ruin");
		return ruin;
	}

	/** The Archimage déchu of the Tour du Mage, alone (no escort, no ability: only the spells hurt it). */
	private static RuinConfig.RuinDef archmage() {
		RuinConfig.RuinDef def = RuinModule.config().def("tour_du_mage", false).copy();
		def.resetSeconds = 120;
		def.abilities.clear();
		def.minions.types.clear();
		def.rewards.lootTable = "";
		def.rewards.items.clear();
		return def;
	}

	@GameTest(maxTicks = 1400)
	public void cursedMeteorShowersOnACursedGuardianAtThreatX(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper);
		Encounters.setThreat(ruin, 10);
		RuinConfig.RuinDef def = archmage();
		ServerPlayer caster = TestSupport.survivor(helper, ruin.boss.offset(5, -1, 0), TestSupport.uniqueName("arch"));
		caster.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4, false, false));
		ItemStack tome = GrimoireItems.create(new GrimoireRules.Pages(List.of(METEORS), 0), true);
		Mob[] boss = new Mob[1];
		float[] start = new float[1];
		float[] before = new float[1];
		int[] casts = new int[1];
		int[] impacts = new int[1];
		double cap = ScrollModule.config().cursedGrimoire.bossCastCap;
		var sequence = helper.startSequence()
				.thenExecute(() -> helper.assertTrue(Encounters.awaken(level, ruin, def, false, true) == null, "cursed guardian awakened"))
				.thenWaitUntil(() -> {
					boss[0] = Encounters.boss(ruin);
					helper.assertTrue(boss[0] != null, "waiting for the cursed guardian");
				})
				.thenExecute(() -> {
					boss[0].setNoAi(true);
					start[0] = boss[0].getHealth();
					LOGGER.info("[GuildyTest] [balance] cursed Archimage at threat X: max health {} (ruin {} x threat x cursed), phase {}",
							boss[0].getMaxHealth(), def.boss.health, CursedFights.phase(ruin));
				});
		for (int k = 0; k < 4; k++) {
			sequence = sequence
					.thenWaitUntil(() -> helper.assertFalse(CursedFights.transitioning(ruin), "waiting for the end of a phase transition"))
					.thenExecute(() -> {
						Mob current = Encounters.boss(ruin);
						helper.assertTrue(current != null && current.isAlive(), "the guardian is still there");
						boss[0] = current;
						boss[0].setNoAi(true);
						before[0] = boss[0].getHealth();
						impacts[0] = ScrollModule.meteorImpacts(caster.getUUID());
						castMeteors(helper, caster, tome, true, boss[0]);
						casts[0]++;
					})
					.thenWaitUntil(() -> helper.assertTrue(ScrollModule.meteorImpacts(caster.getUUID()) - impacts[0] >= meteors(true),
							"waiting for every meteor"))
					.thenIdle(3)
					.thenExecute(() -> {
						Mob current = Encounters.boss(ruin);
						LOGGER.info("[GuildyTest] [balance] after cursed meteor shower {}: health {} / {} ({} %), phase {}, transitioning {}, active {}",
								casts[0], current == null ? 0 : current.getHealth(), current == null ? 0 : current.getMaxHealth(),
								current == null ? 0 : Math.round(100 * current.getHealth() / current.getMaxHealth()), CursedFights.phase(ruin),
								CursedFights.transitioning(ruin), Encounters.isActive(ruin));
						helper.assertTrue(current != null && current.isAlive(), "the guardian survives the shower");
						double lost = (before[0] - current.getHealth()) / current.getMaxHealth();
						// The cap of the cursed grimoire, plus a little burn (the meteors set it on fire).
						helper.assertTrue(lost > cap * 0.5 && lost <= cap + 0.02, "one cursed shower takes about " + cap + " of its health: " + lost);
					});
		}
		sequence.thenExecute(() -> {
			Mob current = Encounters.boss(ruin);
			helper.assertTrue(current != null && current.isAlive() && Encounters.isActive(ruin), "4 cursed showers: still alive");
			double left = current.getHealth() / current.getMaxHealth();
			LOGGER.info("[GuildyTest] [balance] 4 cursed meteor showers on the cursed Archimage at threat X: {} % of its health left",
					Math.round(100 * left));
			helper.assertTrue(CursedFights.phase(ruin) == 1 && !CursedFights.transitioning(ruin), "still in its first phase");
			helper.assertTrue(left > 0.6, "well alive: " + left);
			caster.teleportTo(level, ruin.boss.getX() + 300.5, ruin.boss.getY() + 1, ruin.boss.getZ() + 0.5, Set.of(), 0, 0, true);
			Encounters.reset(ruin);
		}).thenSucceed();
	}
}
