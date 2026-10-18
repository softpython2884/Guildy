package dev.guildy.test;

import com.mojang.authlib.GameProfile;
import dev.guildy.core.Lang;
import dev.guildy.ruin.CursedItems;
import dev.guildy.ruin.Encounters;
import dev.guildy.ruin.IncantationItems;
import dev.guildy.ruin.Incantations;
import dev.guildy.ruin.RoomGuards;
import dev.guildy.ruin.RuinConfig;
import dev.guildy.ruin.RuinIndex;
import dev.guildy.ruin.RuinModule;
import dev.guildy.ruin.RuinRecord;
import dev.guildy.ruin.RuinState;
import dev.guildy.ruin.Seals;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Mysterious ruin hints and the sealed ritual, on the test ruin of {@link RuinGameTests}, with a player who records what
 * it is shown (chat, action bar, titles): room guards take their posts without any on-screen announce, the entrance hint
 * speaks of sentinels without counting them and no longer of the threat; an incantation read while they hold the arena
 * draws its circle, which then breaks with "Une présence scelle encore l'entité…" (scroll kept, normal and cursed, a new
 * attempt ignored while it plays, a real reading accepted once they are dead); a victory says nothing of the threat
 * (it stays on the boss bar).
 */
@SuppressWarnings("removal")
public class RuinSealGameTests {
	private static final Logger LOGGER = LoggerFactory.getLogger("GuildyTest");
	private static final AtomicInteger IDS = new AtomicInteger();
	/** Notes on the threat that the messages no longer give. */
	private static final List<String> THREAT_NOTES = List.of("plus fort", "menace grandit", "Niveau de menace", "menace maximale", "attise la menace");

	/** A connected mock survivor recording its chat, action bar and titles. */
	private record Viewer(ServerPlayer player, EmbeddedChannel channel, List<String> chat, List<String> bar, List<String> titles) {
		/** Titles and subtitles sent so far (read from the packets of its connection). */
		@Override
		public List<String> titles() {
			channel.flushOutbound();
			Object message;
			while ((message = channel.readOutbound()) != null) {
				if (message instanceof ClientboundSetTitleTextPacket title) {
					titles.add(title.text().getString());
				} else if (message instanceof ClientboundSetSubtitleTextPacket subtitle) {
					titles.add(subtitle.text().getString());
				}
			}
			return titles;
		}

		static boolean any(List<String> lines, Predicate<String> test) {
			return lines.stream().anyMatch(test);
		}
	}

	private static Viewer viewer(GameTestHelper helper, BlockPos pos) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		String name = "test-viewer";
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
		List<String> chat = new CopyOnWriteArrayList<>();
		List<String> bar = new CopyOnWriteArrayList<>();
		ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void sendSystemMessage(Component message, boolean overlay) {
				LOGGER.info("[{}{}] {}", name, overlay ? " bar" : "", message.getString());
				(overlay ? bar : chat).add(message.getString());
				super.sendSystemMessage(message, overlay);
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		EmbeddedChannel channel = new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		player.teleportTo(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, Set.of(), 0, 0, true);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MobEffectInstance.INFINITE_DURATION, 4, false, false));
		return new Viewer(player, channel, chat, bar, new ArrayList<>());
	}

	/** A placed, indexed test ruin with its boss marker, at threat level II, under {@code def} when given. */
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
		Encounters.setThreat(ruin, 2);
		if (def != null) {
			String id = "seal_test_" + IDS.incrementAndGet();
			RuinModule.config().ruins.put(id, def);
			ruin.ruinId = id;
		}
		return ruin;
	}

	private static void at(ServerLevel level, ServerPlayer player, BlockPos pos) {
		player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), 0, 0, true);
	}

	private static void away(ServerLevel level, ServerPlayer player, RuinRecord ruin) {
		player.teleportTo(level, ruin.boss.getX() + 300.5, ruin.boss.getY() + 1, ruin.boss.getZ() + 0.5, Set.of(), 0, 0, true);
	}

	/** Right click with the stack in the main hand, through the real use event. */
	private static InteractionResult read(ServerPlayer player) {
		return UseItemCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND);
	}

	private static int count(ServerPlayer player, Predicate<ItemStack> kind) {
		int total = 0;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (kind.test(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	// ================================================================== guards: hints, the sealed circle

	@GameTest(maxTicks = 1200)
	public void guardedReadingBreaksASealedCircle(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinConfig cfg = RuinModule.config();
		helper.assertFalse(cfg.guards.announceOnScreen, "no on-screen announce of the guards by default");
		RuinConfig.RuinDef def = RuinModule.config().def("test_ruin", false).copy();
		def.resetSeconds = 3;
		def.respawnDays = 7;
		def.fastKillSeconds = 0;
		def.guards.count = 2;
		def.guards.types.clear();
		def.guards.types.put("minecraft:zombie", 1);
		RuinRecord ruin = ruin(helper, def);
		Viewer viewer = viewer(helper, ruin.boss.offset(80, -1, 0));
		ServerPlayer player = viewer.player();
		String watch = Lang.raw("ruin.guards.watch");
		String sealed = Lang.raw("ruin.summon.sealed");
		String sealedCursed = Lang.raw("ruin.summon.sealed.cursed");
		List<Mob> guards = new ArrayList<>();
		int[] firstAge = new int[1];
		helper.startSequence()
				.thenIdle(5)
				.thenExecute(() -> at(level, player, ruin.boss.offset(2, 0, 0)))
				.thenWaitUntil(() -> helper.assertTrue(RoomGuards.guards(ruin).size() == 2, "waiting for the room guards"))
				.thenWaitUntil(() -> helper.assertTrue(Viewer.any(viewer.chat(), line -> line.contains(watch)), "waiting for the entrance hint"))
				.thenExecute(() -> {
					guards.addAll(RoomGuards.guards(ruin));
					guards.forEach(guard -> guard.setNoAi(true));
					helper.assertTrue(RoomGuards.blocking(level, ruin) > 0, "the guards hold the arena");
					// No title, subtitle or action bar about the guards; the hint counts nothing and says nothing of the threat.
					Predicate<String> aboutGuards = line -> line.toLowerCase(Locale.ROOT).contains("sentinelle")
							|| line.toLowerCase(Locale.ROOT).contains("gardiens");
					helper.assertFalse(Viewer.any(viewer.titles(), aboutGuards), "no guard title: " + viewer.titles());
					helper.assertFalse(Viewer.any(viewer.bar(), aboutGuards), "no guard announce on screen: " + viewer.bar());
					helper.assertFalse(Lang.strip(watch).matches(".*\\d.*"), "the hint never counts the guards: " + watch);
					helper.assertFalse(Viewer.any(viewer.chat(), line -> THREAT_NOTES.stream().anyMatch(line::contains)), "no threat note: " + viewer.chat());
					helper.assertTrue(Viewer.any(viewer.titles(), line -> line.contains(Lang.raw("ruin.summon.hint.subtitle"))), "the entrance title stays");

					// The guarded reading: refused, the scroll kept, the circle starts to form.
					player.setItemInHand(InteractionHand.MAIN_HAND, IncantationItems.create(2));
					InteractionResult result = read(player);
					helper.assertTrue(result == InteractionResult.FAIL && !player.isUsingItem(), "refused, got " + result);
					helper.assertTrue(Seals.playing(player) && Seals.cooling(player), "the sealed circle plays");
					helper.assertFalse(Incantations.ritualRunning(ruin), "no ritual");
					helper.assertTrue(count(player, IncantationItems::is) == 2, "scroll kept");
					firstAge[0] = Seals.age(player);
				})
				.thenIdle(8)
				.thenExecute(() -> {
					// Spamming changes nothing: the same circle goes on forming.
					helper.assertTrue(read(player) == InteractionResult.FAIL && !player.isUsingItem(), "refused again");
					helper.assertTrue(Seals.age(player) > firstAge[0] + 4, "the circle was not restarted: age " + Seals.age(player));
					helper.assertFalse(Viewer.any(viewer.bar(), line -> line.contains(sealed)), "still forming: no message yet");
				})
				.thenWaitUntil(() -> helper.assertTrue(Viewer.any(viewer.bar(), line -> line.contains(sealed)), "waiting for the circle to break"))
				.thenWaitUntil(() -> helper.assertFalse(Seals.playing(player), "waiting for the fragments to fade"))
				.thenExecute(() -> {
					helper.assertTrue(count(player, IncantationItems::is) == 2, "the scroll is never consumed");
					helper.assertFalse(Incantations.ritualRunning(ruin), "still no ritual");
				})
				.thenWaitUntil(() -> helper.assertFalse(Seals.cooling(player), "waiting for the end of the cooldown"))
				.thenExecute(() -> {
					// The cursed scroll is sealed the same way.
					player.setItemInHand(InteractionHand.MAIN_HAND, CursedItems.create(2));
					helper.assertTrue(read(player) == InteractionResult.FAIL && !player.isUsingItem(), "cursed reading refused");
					helper.assertTrue(Seals.playing(player), "a new sealed circle");
					helper.assertTrue(count(player, CursedItems::is) == 2, "cursed scroll kept");
				})
				.thenWaitUntil(() -> helper.assertTrue(Viewer.any(viewer.bar(), line -> line.contains(sealedCursed)), "waiting for the cursed circle to break"))
				.thenExecute(() -> {
					helper.assertTrue(count(player, CursedItems::is) == 2, "the cursed scroll is never consumed");
					guards.forEach(guard -> guard.hurtServer(level, level.damageSources().playerAttack(player), 1000F));
				})
				.thenIdle(5)
				.thenExecute(() -> {
					helper.assertTrue(RoomGuards.blocking(level, ruin) == 0, "the arena is free");
					// Once they are dead, a real reading starts at once (even while the last circle was cooling down).
					player.setItemInHand(InteractionHand.MAIN_HAND, IncantationItems.create(2));
					InteractionResult started = read(player);
					helper.assertTrue(started instanceof InteractionResult.Success && player.isUsingItem(), "accepted, got " + started);
					helper.assertFalse(Seals.playing(player), "no broken circle under the real one");
					player.stopUsingItem();
					away(level, player, ruin);
				})
				.thenWaitUntil(() -> helper.assertFalse(RoomGuards.posted(ruin), "waiting for the guards' watch to end"))
				.thenExecute(() -> helper.assertFalse(Incantations.ritualRunning(ruin), "no ritual left behind"))
				.thenSucceed();
	}

	// ================================================================== victory

	@GameTest(maxTicks = 600)
	public void victorySaysNothingOfTheThreat(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RuinRecord ruin = ruin(helper, null);
		RuinConfig.RuinDef def = RuinModule.config().def("test_ruin", false).copy();
		def.resetSeconds = 60;
		def.fastKillSeconds = 0;
		def.rewards.lootTable = "";
		def.rewards.items.clear();
		Viewer viewer = viewer(helper, ruin.boss.offset(3, -1, 0));
		ServerPlayer player = viewer.player();
		Mob[] boss = new Mob[1];
		helper.startSequence()
				.thenExecute(() -> helper.assertTrue(Encounters.awaken(level, ruin, def, false) == null, "guardian awakened"))
				.thenWaitUntil(() -> {
					boss[0] = Encounters.boss(ruin);
					helper.assertTrue(boss[0] != null, "waiting for the guardian");
				})
				.thenExecute(() -> {
					Component bar = Encounters.barTitle(ruin);
					helper.assertTrue(bar != null && bar.getString().contains("Menace II"), "the threat stays on the boss bar: " + bar);
					Encounters.minions(ruin).forEach(m -> m.setNoAi(true));
					boss[0].hurtServer(level, level.damageSources().playerAttack(player), 100_000F);
				})
				.thenWaitUntil(() -> helper.assertTrue(ruin.state == RuinState.CLEARED, "waiting for the victory"))
				.thenExecute(() -> {
					helper.assertTrue(ruin.threat == 3, "the threat still rises: " + ruin.threat);
					helper.assertTrue(Viewer.any(viewer.titles(), line -> line.contains(Lang.raw("ruin.victory.title"))), "victory title: " + viewer.titles());
					String bossName = Lang.colorize(def.boss.name);
					helper.assertTrue(Viewer.any(viewer.chat(), line -> line.contains(bossName)), "awakening and victory in the chat: " + viewer.chat());
					helper.assertFalse(Viewer.any(viewer.chat(), line -> THREAT_NOTES.stream().anyMatch(line::contains)), "no threat note: " + viewer.chat());
					away(level, player, ruin);
				})
				.thenSucceed();
	}
}
