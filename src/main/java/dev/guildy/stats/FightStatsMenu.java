package dev.guildy.stats;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.gui.GuiUtil;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * "Bilan du combat" menu: the fight on top (place, guardian, threat, outcome, durations, room guards and escort killed,
 * totems of undying used), then one player head per listed participant, best damage to the guardian first, with the titles they hold and all their
 * numbers in the lore. Read-only (SGUI: nothing can be taken).
 */
public final class FightStatsMenu {
	/** Heads shown at most (five rows under the header row). */
	static final int MAX_HEADS = 45;

	private FightStatsMenu() {
	}

	public static void open(ServerPlayer player, FightReport report) {
		FightReport.Header h = report.header();
		List<FightReport.Entry> entries = report.entries();
		int heads = Math.min(MAX_HEADS, entries.size());
		int rows = 1 + Math.max(1, (heads + 8) / 9);
		SimpleGui gui = GuiUtil.chest(player, rows, Lang.get("fight.menu.title", Lang.strip(h.place())));
		gui.setSlot(4, GuiUtil.item(h.outcome() == FightReport.Outcome.VICTORY ? Items.NETHER_STAR : Items.SKELETON_SKULL,
				Lang.get(h.outcome() == FightReport.Outcome.VICTORY ? "fight.menu.header" : "fight.menu.header.failure", h.place()), header(report)));
		gui.setSlot(8, GuiUtil.close());
		List<FightCategory> categories = FightModule.settings().categoryList();
		List<FightCategory> all = List.of(FightCategory.values());
		for (int i = 0; i < heads; i++) {
			FightReport.Entry entry = entries.get(i);
			gui.setSlot(9 + i, head(report, entry, i + 1, all, categories, entry.player().equals(player.getUUID())));
		}
		if (entries.isEmpty()) {
			gui.setSlot(13, GuiUtil.item(Items.BARRIER, Lang.get("fight.menu.empty"), List.of()));
		}
		GuiUtil.fill(gui);
		gui.open();
	}

	private static List<Component> header(FightReport report) {
		FightReport.Header h = report.header();
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("fight.menu.boss", h.boss()));
		lore.add(Lang.get("fight.menu.threat", FightReports.threat(h)));
		lore.add(Lang.get("fight.menu.duration", FightRules.clock(h.fightTicks()), FightRules.clock(h.totalTicks())));
		if (h.outcome() == FightReport.Outcome.FAILURE && h.bossHealthLeft() >= 0) {
			lore.add(Lang.get("fight.report.failure.health", FightRules.percent(h.bossHealthLeft())));
		}
		lore.add(Lang.get("fight.menu.kills", h.guardsKilled(), h.guardsPosted(), h.escortKilled()));
		if (report.totems() > 0) {
			lore.add(Lang.get("fight.stat.totems", report.totems()));
		}
		lore.add(Lang.get("fight.menu.players", report.entries().size()));
		if (report.entries().size() > MAX_HEADS) {
			lore.add(Lang.get("fight.menu.more", report.entries().size() - MAX_HEADS));
		}
		return lore;
	}

	private static GuiElementBuilder head(FightReport report, FightReport.Entry entry, int rank, List<FightCategory> all, List<FightCategory> shown,
			boolean self) {
		List<Component> lore = new ArrayList<>();
		// Every title they hold (the ones the chat shows first), then the numbers.
		List<FightCategory> held = report.titlesOf(entry.player(), all);
		for (FightCategory category : shown) {
			if (held.contains(category)) {
				lore.add(Lang.get("fight.menu.title.held", Lang.raw("fight.title." + category.id)));
			}
		}
		for (FightCategory category : held) {
			if (!shown.contains(category)) {
				lore.add(Lang.get("fight.menu.title.held", Lang.raw("fight.title." + category.id)));
			}
		}
		if (!held.isEmpty()) {
			lore.add(Component.empty());
		}
		lore.addAll(FightReports.statLines(entry.tally()));
		GuiElementBuilder builder = GuiUtil.item(Items.PLAYER_HEAD, Lang.get(self ? "fight.menu.head.self" : "fight.menu.head", rank, entry.name()), lore);
		ServerPlayer online = GuildyServer.player(entry.player());
		builder.setProfile(online != null ? ResolvableProfile.createResolved(online.getGameProfile()) : ResolvableProfile.createUnresolved(entry.player()));
		if (rank == 1 && entry.tally().damage(FightKind.BOSS) > 0) {
			builder.glow();
		}
		return builder;
	}
}
