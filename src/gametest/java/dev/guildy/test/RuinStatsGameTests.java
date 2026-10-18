package dev.guildy.test;

import dev.guildy.core.Lang;
import dev.guildy.core.ScrollDrops;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.ruin.Encounters;
import dev.guildy.ruin.RoomGuards;
import dev.guildy.ruin.RuinConfig;
import dev.guildy.ruin.RuinIndex;
import dev.guildy.ruin.RuinModule;
import dev.guildy.ruin.RuinRecord;
import dev.guildy.ruin.RuinState;
import dev.guildy.stats.FightCategory;
import dev.guildy.stats.FightKind;
import dev.guildy.stats.FightReport;
import dev.guildy.stats.FightReports;
import dev.guildy.stats.FightTally;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * "Bilan du combat" on the test ruin of {@link RuinGameTests}: two players fight the room guards, the escort and the guardian
 * with different amounts, one heals the other with a Bénédiction scroll; at the victory both get the summary (chat and
 * stored report) with the right numbers and titles, and the detail menu opens. A fight reset by an admin sends the short
 * summary.
 */
@SuppressWarnings("removal")
public class RuinStatsGameTests {
	private static final AtomicInteger IDS = new AtomicInteger();
	private static final double EPS = 0.01;

	/** A placed, indexed test ruin at threat level I, under its own definition (guards: {@code guards}). */
	private static RuinRecord ruin(GameTestHelper helper, RuinConfig.RuinDef def) {
		ServerLevel level = helper.getLevel();
		BlockPos center = TestSupport.region(level, 0);
		TestSupport.forceChunks(level, center, 3);
		StructureStart start = RuinGameTests.generate(level, center);
		RuinGameTests.place(level, start);
		RuinIndex.processPending(level.getServer());
		RuinRecord ruin = RuinIndex.register(level, start);
		helper.assertTrue(ruin != null && ruin.boss != null, "test ruin indexed with its boss marker");
		Encounters.setThreat(ruin, 1);
		String id = "stats_test_" + IDS.incrementAndGet();
		RuinModule.config().ruins.put(id, def);
		ruin.ruinId = id;
		return ruin;
	}

	/** The generic guardian without abilities (no stray damage), never rising, with {@code guards} zombie room guards. */
	private static RuinConfig.RuinDef guardian(int guards) {
		RuinConfig.RuinDef def = RuinModule.config().def("test_ruin", false).copy();
		def.resetSeconds = 60;
		def.respawnDays = 7;
		def.fastKillSeconds = 0;
		def.abilities.clear();
		def.rewards.lootTable = "";
		def.rewards.items.clear();
		def.guards.count = guards;
		def.guards.types.clear();
		def.guards.types.put("minecraft:zombie", 1);
		return def;
	}

	/** Mock players only take damage once their client "loaded" (60 ticks after joining otherwise). */
	private static void loaded(ServerPlayer player) {
		for (int i = 0; i < 80; i++) {
			player.connection.tickClientLoadTimeout();
		}
	}

	/** Hits {@code target} as {@code source} (invulnerability frames cleared first); returns the health it lost. */
	private static float hit(ServerLevel level, LivingEntity target, DamageSource source, float amount) {
		float before = target.getHealth() + target.getAbsorptionAmount();
		target.damageCooldownTime = 0;
		target.hurtServer(level, source, amount);
		return before - (target.isAlive() ? target.getHealth() + target.getAbsorptionAmount() : 0);
	}

	private static String plain(String text) {
		return Lang.strip(text);
	}

	/** A chat line of the player containing every fragment (formatting stripped), or null. */
	private static String line(ServerPlayer player, String... fragments) {
		for (String raw : TestSupport.chat(player)) {
			String text = plain(raw);
			boolean all = true;
			for (String fragment : fragments) {
				all &= text.contains(fragment);
			}
			if (all) {
				return text;
			}
		}
		return null;
	}

	private static List<String> holders(FightReport report, FightCategory category) {
		for (FightReport.Title title : report.titles(List.of(category))) {
			return title.holders().stream().map(FightReport.Entry::name).toList();
		}
		return List.of();
	}

	@GameTest(maxTicks = 1500)
	public void victorySummaryNamesTheBestPlayers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinConfig.RuinDef def = guardian(2);
		RuinRecord ruin = ruin(helper, def);
		ServerPlayer alex = TestSupport.survivor(helper, ruin.boss.offset(3, -1, 0), "StatsAlex");
		ServerPlayer bea = TestSupport.survivor(helper, ruin.boss.offset(-3, -1, 0), "StatsBea");
		loaded(alex);
		loaded(bea);
		// No natural regeneration: Alex's health only changes through the test (and Bea's blessing). Bea takes no damage.
		alex.getFoodData().setFoodLevel(10);
		alex.getFoodData().setSaturation(0);
		bea.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4, false, false));
		Guild guild = GuildManager.create(TestSupport.uniqueName("Bilan"), alex);
		GuildManager.addMember(guild, bea.getUUID(), bea.getName().getString());
		// Expected numbers, measured on the entities.
		Map<String, Float> expected = new LinkedHashMap<>();
		List<Mob> guards = new ArrayList<>();
		Mob[] boss = new Mob[1];
		float[] alexHealth = new float[1];
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(RoomGuards.guards(ruin).size() >= 2, "waiting for the room guards, got " + RoomGuards.guards(ruin).size()))
				.thenExecute(() -> {
					guards.addAll(RoomGuards.guards(ruin));
					guards.forEach(g -> g.setNoAi(true));
					// Bea kills a guard, Alex only scratches another one.
					expected.put("beaGuard", hit(level, guards.get(0), level.damageSources().playerAttack(bea), 1000F));
					expected.put("alexGuard", hit(level, guards.get(1), level.damageSources().playerAttack(alex), 3F));
					helper.assertTrue(!guards.get(0).isAlive() && guards.get(1).isAlive(), "one guard down, one scratched");
					helper.assertTrue(Encounters.awaken(level, ruin, def, false) == null, "guardian awakened");
				})
				.thenWaitUntil(() -> {
					boss[0] = Encounters.boss(ruin);
					helper.assertTrue(boss[0] != null, "waiting for the guardian");
				})
				.thenExecute(() -> {
					boss[0].setNoAi(true);
					List<Mob> escort = Encounters.minions(ruin);
					escort.forEach(m -> m.setNoAi(true));
					helper.assertTrue(escort.size() >= 2, "an escort of two at least, got " + escort.size());
					// Different amounts on the guardian, the escort: Bea kills a minion, Alex hits another one.
					expected.put("alexBoss", hit(level, boss[0], level.damageSources().playerAttack(alex), 30F));
					expected.put("beaBoss", hit(level, boss[0], level.damageSources().playerAttack(bea), 8F));
					expected.put("beaEscort", hit(level, escort.get(0), level.damageSources().playerAttack(bea), 1000F));
					expected.put("alexEscort", hit(level, escort.get(1), level.damageSources().playerAttack(alex), 2F));
					helper.assertTrue(expected.get("alexBoss") > expected.get("beaBoss") && expected.get("beaBoss") > 0, "the guardian took both hits: " + expected);
					helper.assertTrue(expected.get("beaEscort") > expected.get("alexEscort") && expected.get("alexEscort") > 0, "escort hits: " + expected);
					// Alex takes a blow (not from a player); only 2 points are missing when Bea reads a Bénédiction (it heals more:
					// the overheal is not counted).
					expected.put("alexTaken", hit(level, alex, level.damageSources().magic(), 10F));
					helper.assertTrue(expected.get("alexTaken") >= 9.99F, "Alex is hurt: " + expected.get("alexTaken"));
					alex.setHealth(alex.getMaxHealth() - 2.0F);
					alexHealth[0] = alex.getHealth();
					ItemStack scroll = ScrollDrops.create("benediction", 2);
					helper.assertFalse(scroll.isEmpty(), "Bénédiction scroll created");
					bea.setItemInHand(InteractionHand.MAIN_HAND, scroll);
					InteractionResult result = UseItemCallback.EVENT.invoker().interact(bea, level, InteractionHand.MAIN_HAND);
					helper.assertTrue(result instanceof InteractionResult.Success && bea.isUsingItem(), "incantation started, got " + result);
					for (int i = 0; i < 400 && bea.isUsingItem(); i++) {
						bea.doTick();
					}
					helper.assertTrue(bea.getMainHandItem().getCount() == 1, "the scroll was read");
				})
				// The blessing lands on the first tick of its effect.
				.thenWaitUntil(() -> helper.assertTrue(alex.getHealth() > alexHealth[0], "waiting for the blessing"))
				.thenExecute(() -> expected.put("beaHealing", alex.getHealth() - alexHealth[0]))
				// More than a second in the arena (counted once per second).
				.thenIdle(25)
				.thenExecute(() -> expected.put("alexFinal", hit(level, boss[0], level.damageSources().playerAttack(alex), 100_000F)))
				.thenWaitUntil(() -> helper.assertTrue(ruin.state == RuinState.CLEARED, "waiting for the victory"))
				.thenWaitUntil(() -> {
					helper.assertTrue(line(alex, "Bilan du combat") != null, "waiting for Alex's summary");
					helper.assertTrue(line(bea, "Bilan du combat") != null, "waiting for Bea's summary");
				})
				.thenExecute(() -> {
					FightReport report = FightReports.last(alex.getUUID());
					helper.assertTrue(report != null && report == FightReports.last(bea.getUUID()), "the same summary is kept for both");
					helper.assertTrue(report.header().outcome() == FightReport.Outcome.VICTORY && !report.header().cursed(), "a victory");
					helper.assertTrue(report.header().guardsPosted() >= 2 && report.header().guardsKilled() == 1, "guards: "
							+ report.header().guardsKilled() + "/" + report.header().guardsPosted());
					helper.assertTrue(report.entries().size() == 2 && report.entries().get(0).player().equals(alex.getUUID()),
							"Alex first (most damage to the guardian): " + report.entries().stream().map(FightReport.Entry::name).toList());
					FightTally a = report.entry(alex.getUUID()).tally();
					FightTally b = report.entry(bea.getUUID()).tally();
					float alexBoss = expected.get("alexBoss") + expected.get("alexFinal");
					helper.assertTrue(Math.abs(a.damage(FightKind.BOSS) - alexBoss) < EPS, "Alex on the guardian " + a.damage(FightKind.BOSS) + " / " + alexBoss);
					helper.assertTrue(Math.abs(b.damage(FightKind.BOSS) - expected.get("beaBoss")) < EPS, "Bea on the guardian " + b.damage(FightKind.BOSS));
					helper.assertTrue(Math.abs(a.damage(FightKind.ESCORT) - expected.get("alexEscort")) < EPS, "Alex on the escort " + a.damage(FightKind.ESCORT));
					helper.assertTrue(Math.abs(b.damage(FightKind.ESCORT) - expected.get("beaEscort")) < EPS, "Bea on the escort " + b.damage(FightKind.ESCORT));
					helper.assertTrue(Math.abs(a.damage(FightKind.GUARD) - expected.get("alexGuard")) < EPS, "Alex on the guards " + a.damage(FightKind.GUARD));
					helper.assertTrue(Math.abs(b.damage(FightKind.GUARD) - expected.get("beaGuard")) < EPS, "Bea on the guards " + b.damage(FightKind.GUARD));
					helper.assertTrue(b.kills(FightKind.ESCORT) == 1 && b.kills(FightKind.GUARD) == 1 && a.kills(FightKind.ESCORT) == 0, "kills");
					helper.assertTrue(a.kills(FightKind.BOSS) == 1 && a.finalBlow() && !b.finalBlow(), "Alex dealt the final blow");
					helper.assertTrue(a.taken() >= expected.get("alexTaken") - EPS && b.taken() == 0, "damage taken " + a.taken() + " / " + b.taken());
					helper.assertTrue(Math.abs(b.healing() - 2.0) < EPS && Math.abs(expected.get("beaHealing") - 2.0) < EPS && a.healing() == 0,
							"Bea healed Alex " + b.healing() + " / " + expected.get("beaHealing") + " (no overheal, her own heal not counted)");
					helper.assertTrue(b.spells() == 1 && a.spells() == 0, "one spell cast by Bea");
					helper.assertTrue(a.seconds() > 0 && b.seconds() > 0, "time in the arena");
					// Titles.
					helper.assertTrue(holders(report, FightCategory.GUARDIAN).equals(List.of("StatsAlex")), "Fléau du gardien");
					helper.assertTrue(holders(report, FightCategory.GUARDS).equals(List.of("StatsBea")), "Briseur de gardes");
					helper.assertTrue(holders(report, FightCategory.ESCORT).equals(List.of("StatsBea")), "Chasseur de sbires");
					helper.assertTrue(holders(report, FightCategory.HEALING).equals(List.of("StatsBea")), "Ange gardien");
					helper.assertTrue(holders(report, FightCategory.TANK).equals(List.of("StatsAlex")), "Rempart");
					helper.assertTrue(holders(report, FightCategory.FINAL_BLOW).equals(List.of("StatsAlex")), "Coup fatal");
					// Chat.
					for (ServerPlayer player : List.of(alex, bea)) {
						helper.assertTrue(line(player, "Fléau du gardien", "StatsAlex") != null, "chat: Fléau du gardien " + TestSupport.chat(player));
						helper.assertTrue(line(player, "Briseur de gardes", "StatsBea") != null, "chat: Briseur de gardes");
						helper.assertTrue(line(player, "Ange gardien", "StatsBea") != null, "chat: Ange gardien");
						helper.assertTrue(line(player, "Coup fatal", "StatsAlex") != null, "chat: Coup fatal");
						helper.assertTrue(line(player, "Voir le détail") != null, "chat: detail button");
					}
					helper.assertTrue(line(alex, "Vous (#1/2)") != null, "Alex's own line: " + TestSupport.chat(alex));
					helper.assertTrue(line(bea, "Vous (#2/2)") != null, "Bea's own line");
					// The detail menu, from the chat button's command.
					level.getServer().getCommands().performPrefixedCommand(alex.createCommandSourceStack(), "guild bilan detail");
					helper.assertTrue(alex.containerMenu != alex.inventoryMenu, "the detail menu is open");
					ItemStack first = alex.containerMenu.getSlot(9).getItem();
					helper.assertTrue(first.is(Items.PLAYER_HEAD) && first.getHoverName().getString().contains("StatsAlex"),
							"Alex's head first: " + first.getHoverName().getString());
					ItemStack second = alex.containerMenu.getSlot(10).getItem();
					helper.assertTrue(second.is(Items.PLAYER_HEAD) && second.getHoverName().getString().contains("StatsBea"), "then Bea's");
					alex.closeContainer();
					GuildManager.disband(guild);
				})
				.thenSucceed();
	}

	@GameTest(maxTicks = 600)
	public void failedFightSendsTheShortSummary(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinConfig.RuinDef def = guardian(0);
		RuinRecord ruin = ruin(helper, def);
		ServerPlayer player = TestSupport.survivor(helper, ruin.boss.offset(3, -1, 0), "StatsLoner");
		loaded(player);
		Mob[] boss = new Mob[1];
		helper.startSequence()
				.thenExecute(() -> helper.assertTrue(Encounters.awaken(level, ruin, def, false) == null, "guardian awakened"))
				.thenWaitUntil(() -> {
					boss[0] = Encounters.boss(ruin);
					helper.assertTrue(boss[0] != null, "waiting for the guardian");
				})
				.thenExecute(() -> {
					boss[0].setNoAi(true);
					Encounters.minions(ruin).forEach(m -> m.setNoAi(true));
					helper.assertTrue(hit(level, boss[0], level.damageSources().playerAttack(player), 20F) > 0, "the guardian is hit");
					helper.assertTrue(hit(level, player, level.damageSources().magic(), 4F) > 0, "the player is hurt (Rempart too)");
					Encounters.reset(ruin);
					helper.assertFalse(Encounters.isActive(ruin), "fight over");
					FightReport report = FightReports.last(player.getUUID());
					helper.assertTrue(report != null && report.header().outcome() == FightReport.Outcome.FAILURE, "a failure summary is kept");
					helper.assertTrue(report.header().bossHealthLeft() > 0 && report.header().bossHealthLeft() < 1,
							"the guardian's health left: " + report.header().bossHealthLeft());
					helper.assertTrue(holders(report, FightCategory.TANK).equals(List.of("StatsLoner")), "he holds Rempart as well");
				})
				.thenWaitUntil(() -> helper.assertTrue(line(player, "Bilan du combat", "(échec)") != null, "waiting for the short summary"))
				.thenExecute(() -> {
					helper.assertTrue(line(player, "Le gardien avait encore") != null, "health left in the chat: " + TestSupport.chat(player));
					helper.assertTrue(line(player, "Fléau du gardien", "StatsLoner") != null, "the first title only");
					helper.assertTrue(line(player, "Rempart") == null, "the short form keeps one title");
				})
				.thenSucceed();
	}
}
