package dev.guildy.ruin;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.data.Guild;
import dev.guildy.gui.GuiUtil;
import dev.guildy.ruin.GuildRuinData.Research;
import dev.guildy.ruin.RuinConfig.RuinDef;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Menu of the "Atelier du cartographe": start a research (Overworld or Nether), follow its progress (refreshed every
 * second), collect the map / compass, and the list of the ruins known by the guild.
 */
final class AtelierMenu {
	private static final int KNOWN_PER_PAGE = 45;

	private AtelierMenu() {
	}

	static void open(ServerPlayer player, Guild guild) {
		if (!guild.isMember(player.getUUID())) {
			Msg.send(player, "structure.sign.foreign", guild.name);
			return;
		}
		SimpleGui gui = new SimpleGui(GuiUtil.rows(4), player, false) {
			private int ticks;

			@Override
			public void onTick() {
				if (++ticks % 20 == 0) {
					Safe.run("ruin.atelier.refresh", () -> fill(this, player, guild));
				}
			}
		};
		gui.setTitle(Lang.get("ruin.menu.title"));
		fill(gui, player, guild);
		gui.open();
	}

	private static void fill(SimpleGui gui, ServerPlayer player, Guild guild) {
		for (int i = 0; i < gui.getSize(); i++) {
			gui.clearSlot(i);
		}
		Research research = Cartography.research(guild);
		gui.setSlot(4, info(guild, research));
		if (research == null) {
			gui.setSlot(11, researchButton(player, guild, false));
			gui.setSlot(15, researchButton(player, guild, true));
		} else if (research.status == Research.Status.RUNNING) {
			gui.setSlot(13, progress(guild, research));
			gui.setSlot(22, GuiUtil.item(Items.BARRIER, Lang.get("ruin.menu.cancel"), GuiUtil.lines("ruin.menu.cancel.lore"))
					.setCallback((i, t, a, g) -> GuiUtil.confirm(player, Lang.get("ruin.menu.cancel"), GuiUtil.lines("ruin.menu.cancel.lore"),
							() -> {
								result(player, Cartography.cancel(player, guild));
								open(player, guild);
							}, () -> open(player, guild))));
		} else {
			boolean ready = research.status == Research.Status.READY;
			Item icon = ready ? (research.nether ? Items.COMPASS : Items.FILLED_MAP) : Items.PAPER;
			List<Component> claimLore = new ArrayList<>(GuiUtil.lines(ready ? "ruin.menu.claim.lore" : "ruin.menu.refund.lore"));
			int incantations = Cartography.settings().incantationsPerResearch;
			if (ready && incantations > 0) {
				claimLore.add(Lang.get("ruin.menu.incantation", incantations));
			}
			GuiElementBuilder claim = GuiUtil.item(icon, Lang.get(ready ? "ruin.menu.claim" : "ruin.menu.refund"), claimLore);
			if (ready) {
				claim.glow();
			}
			gui.setSlot(13, claim.setCallback((i, t, a, g) -> {
				result(player, Cartography.claim(player, guild));
				open(player, guild);
			}));
		}
		GuildRuinData data = RuinIndex.peekGuild(guild.id);
		int known = data == null ? 0 : data.known.size();
		gui.setSlot(30, GuiUtil.item(Items.BOOK, Lang.get("ruin.menu.known", known), GuiUtil.lines("ruin.menu.known.lore")).setCallback((i, t, a, g) -> {
			GuiUtil.clickSound(player);
			openKnown(player, guild, 0);
		}));
		gui.setSlot(32, GuiUtil.close());
		GuiUtil.fill(gui);
	}

	private static GuiElementBuilder info(Guild guild, Research research) {
		List<Component> lore = new ArrayList<>();
		lore.addAll(GuiUtil.lines("ruin.menu.info.lore"));
		lore.add(Component.empty());
		lore.add(Lang.get("ruin.menu.info.cartographers", Cartography.cartographers(guild), Cartography.working(guild)));
		if (Cartography.cartographers(guild) == 0) {
			lore.addAll(GuiUtil.lines("ruin.menu.info.no_cartographer"));
		}
		return GuiUtil.item(Items.CARTOGRAPHY_TABLE, Lang.get("ruin.menu.info"), lore);
	}

	private static GuiElementBuilder researchButton(ServerPlayer player, Guild guild, boolean nether) {
		RuinConfig.CartographySettings cfg = Cartography.settings();
		List<Component> lore = new ArrayList<>(GuiUtil.lines(nether ? "ruin.menu.research.nether.lore" : "ruin.menu.research.lore"));
		if (cfg.incantationsPerResearch > 0) {
			lore.add(Lang.get("ruin.menu.incantation", cfg.incantationsPerResearch));
		}
		lore.add(Component.empty());
		lore.add(Lang.get("ruin.menu.research.cost"));
		for (Map.Entry<String, Integer> entry : (nether ? cfg.netherCost : cfg.overworldCost).entrySet()) {
			Item item = ItemUtil.item(entry.getKey());
			if (item != null) {
				boolean has = ItemUtil.count(player, item) >= entry.getValue();
				lore.add(Lang.get(has ? "ruin.menu.research.cost.ok" : "ruin.menu.research.cost.missing", entry.getValue(),
						ItemUtil.name(item).getString()));
			}
		}
		if (cfg.goldCost > 0) {
			lore.add(Lang.get("ruin.menu.research.cost.gold", cfg.goldCost));
		}
		lore.add(Lang.get("ruin.menu.research.duration", formatMinutes(cfg.researchMinutes)));
		lore.add(Component.empty());
		lore.add(Lang.get("ruin.menu.research.click"));
		Item icon = nether ? Items.COMPASS : Items.MAP;
		return GuiUtil.item(icon, Lang.get(nether ? "ruin.menu.research.nether" : "ruin.menu.research"), lore).setCallback((i, t, a, g) -> {
			result(player, Cartography.start(player, guild, nether));
			open(player, guild);
		});
	}

	private static GuiElementBuilder progress(Guild guild, Research research) {
		int working = Cartography.working(guild);
		List<Component> lore = new ArrayList<>();
		double ratio = research.required <= 0 ? 1 : research.progress / research.required;
		int filled = (int) Math.round(Math.max(0, Math.min(1, ratio)) * 20);
		lore.add(Lang.get("ruin.menu.progress.bar", "&a" + "|".repeat(filled) + "&8" + "|".repeat(20 - filled),
				RuinRules.percent(research.progress, research.required)));
		lore.add(Lang.get(research.nether ? "ruin.menu.progress.nether" : "ruin.menu.progress.overworld"));
		long left = RuinRules.secondsLeft(research.progress, research.required,
				RuinRules.researchWorkers(working, Cartography.settings().maxCartographers));
		lore.add(left < 0 ? Lang.get("ruin.menu.progress.paused") : Lang.get("ruin.menu.progress.left", formatSeconds(left)));
		lore.add(Lang.get("ruin.menu.progress.workers", working));
		lore.add(Lang.get("ruin.menu.progress.by", research.startedByName));
		return GuiUtil.item(Items.CLOCK, Lang.get("ruin.menu.progress"), lore);
	}

	private static void result(ServerPlayer player, Component error) {
		if (error == null) {
			GuiUtil.successSound(player);
		} else {
			Msg.sendRaw(player, error);
			GuiUtil.errorSound(player);
		}
	}

	// ================================================================== known ruins

	static void openKnown(ServerPlayer player, Guild guild, int page) {
		GuildRuinData data = RuinIndex.peekGuild(guild.id);
		List<RuinRecord> ruins = new ArrayList<>();
		if (data != null) {
			for (String key : data.known) {
				RuinRecord ruin = RuinIndex.get(key);
				if (ruin != null) {
					ruins.add(ruin);
				}
			}
		}
		int pages = GuiUtil.pages(ruins.size(), KNOWN_PER_PAGE);
		int current = Math.max(0, Math.min(page, pages - 1));
		SimpleGui gui = GuiUtil.chest(player, 6, Lang.get("ruin.known.title", ruins.size()));
		MinecraftServer server = GuildyServer.server();
		long today = server == null ? 0 : Encounters.day(server);
		for (int i = 0; i < KNOWN_PER_PAGE; i++) {
			int index = current * KNOWN_PER_PAGE + i;
			if (index >= ruins.size()) {
				break;
			}
			RuinRecord ruin = ruins.get(index);
			RuinDef def = Encounters.def(ruin);
			BlockPos target = ruin.target();
			List<Component> lore = new ArrayList<>();
			lore.add(Lang.get(ruin.isNether() ? "ruin.item.lore.nether" : "ruin.item.lore.overworld"));
			lore.add(Lang.get("ruin.item.lore.coords", target.getX(), target.getY(), target.getZ()));
			lore.add(Lang.get("ruin.item.lore.tier", def.tier));
			lore.add(Lang.get("ruin.item.lore.threat", Encounters.threatLabel(Encounters.threat(ruin))));
			lore.add(Lang.get("ruin.item.lore.boss", Lang.colorize(def.boss.name)));
			lore.add(stateLine(ruin, def, today));
			if (ruin.timesCleared > 0) {
				lore.add(Lang.get("ruin.known.victories", ruin.timesCleared, ruin.lastVictors));
			}
			lore.add(Component.empty());
			lore.add(Lang.get("ruin.known.click"));
			Item icon = ruin.isNether() ? Items.COMPASS : Items.FILLED_MAP;
			gui.setSlot(i, GuiUtil.item(icon, Component.literal(Lang.colorize(def.name)), lore).setCallback((slot, t, a, g) -> {
				GuiUtil.clickSound(player);
				Msg.send(player, "ruin.known.coords", Lang.colorize(def.name), target.getX(), target.getY(), target.getZ(),
						Lang.raw(ruin.isNether() ? "ruin.dimension.nether" : "ruin.dimension.overworld"));
			}));
		}
		if (current > 0) {
			gui.setSlot(45, GuiUtil.item(Items.ARROW, Lang.get("gui.page.prev"), List.of()).setCallback((s, t, a, g) -> openKnown(player, guild, current - 1)));
		}
		if (current + 1 < pages) {
			gui.setSlot(53, GuiUtil.item(Items.ARROW, Lang.get("gui.page.next"), List.of()).setCallback((s, t, a, g) -> openKnown(player, guild, current + 1)));
		}
		gui.setSlot(49, GuiUtil.back(() -> open(player, guild)));
		GuiUtil.fill(gui);
		gui.open();
	}

	/** "dormante" / "réveillée" / "vaincue — de retour dans N jours". */
	static Component stateLine(RuinRecord ruin, RuinDef def, long today) {
		if (ruin.state == RuinState.CLEARED) {
			long left = RuinRules.daysLeft(ruin.clearedDay, today, def.respawnDays);
			return left < 0 ? Lang.get("ruin.known.state.cleared_forever") : Lang.get("ruin.known.state.cleared", Math.max(1, left));
		}
		if (Encounters.isActive(ruin)) {
			return Lang.get("ruin.known.state.awake");
		}
		if (Incantations.ritualRunning(ruin)) {
			return Lang.get("ruin.known.state.ritual");
		}
		// Watched or not, never how many (the admins see the count in /guild admin ruin info).
		int guards = RoomGuards.alive(ruin);
		return guards > 0 ? Lang.get("ruin.known.state.watched", guards) : Lang.get("ruin.known.state.dormant");
	}

	private static String formatMinutes(double minutes) {
		return formatSeconds(Math.round(minutes * 60));
	}

	static String formatSeconds(long seconds) {
		long s = Math.max(0, seconds);
		return s >= 60 ? (s / 60) + " min " + String.format("%02d", s % 60) + " s" : s + " s";
	}
}
