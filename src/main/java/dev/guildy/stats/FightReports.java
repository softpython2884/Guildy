package dev.guildy.stats;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Delivery of the fight summaries: the chat version (titles with the best players, the reader's own line, hover text with
 * every number of a player, a clickable "[Voir le détail]" opening {@link FightStatsMenu}) and the last summary of each
 * player, kept {@code stats.keepMinutes} for {@code /guild bilan} (bounded, pruned every minute).
 */
public final class FightReports {
	/** Command run by the "[Voir le détail]" button. */
	public static final String DETAIL_COMMAND = "/guild bilan detail";
	private static final int MAX_STORED = 1024;
	/** Delay between the end of the fight and the chat summary (the victory titles and messages come first). */
	static final int CHAT_DELAY_TICKS = 30;
	/** Names shown at most on a shared title ("+N" beyond). */
	private static final int MAX_HOLDERS = 3;

	private record Stored(FightReport report, long expires) {
	}

	private static final Map<UUID, Stored> LAST = new HashMap<>();

	private FightReports() {
	}

	/**
	 * Keeps the summary for each of its recipients and sends it to the online ones in chat (shortly after: the victory
	 * messages first). The short form on a failure.
	 */
	public static void deliver(FightReport report, FightStatsSettings cfg) {
		long expires = Scheduler.ticks() + cfg.keepMinutes * 60L * 20L;
		for (UUID player : report.recipients()) {
			if (LAST.size() >= MAX_STORED && !LAST.containsKey(player)) {
				prune();
				if (LAST.size() >= MAX_STORED) {
					break;
				}
			}
			LAST.put(player, new Stored(report, expires));
		}
		Scheduler.later("fight.report", CHAT_DELAY_TICKS, () -> {
			for (UUID uuid : report.recipients()) {
				ServerPlayer player = GuildyServer.player(uuid);
				if (player != null) {
					Safe.run("fight.report.send", () -> send(player, report, FightModule.settings()));
				}
			}
		});
	}

	/** The last summary of a player still kept, or null. */
	public static @Nullable FightReport last(UUID player) {
		Stored stored = LAST.get(player);
		if (stored == null) {
			return null;
		}
		if (stored.expires() < Scheduler.ticks()) {
			LAST.remove(player);
			return null;
		}
		return stored.report();
	}

	public static void send(ServerPlayer player, FightReport report, FightStatsSettings cfg) {
		List<Component> lines = chat(report, player.getUUID(), cfg);
		for (int i = 0; i < lines.size(); i++) {
			player.sendSystemMessage(i == 0 ? Msg.prefixed(lines.get(i)) : lines.get(i));
		}
		if (report.header().outcome() == FightReport.Outcome.VICTORY) {
			Msg.sound(player, SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.0F);
		}
	}

	/** The chat lines of a summary as {@code viewer} reads it (the first one gets the chat prefix). */
	public static List<Component> chat(FightReport report, UUID viewer, FightStatsSettings cfg) {
		FightReport.Header h = report.header();
		boolean victory = h.outcome() == FightReport.Outcome.VICTORY;
		List<Component> lines = new ArrayList<>();
		lines.add(Lang.get(victory ? "fight.report.header" : "fight.report.header.failure", h.place()));
		lines.add(Lang.get("fight.report.info", h.boss(), threat(h), FightRules.clock(h.fightTicks()), report.entries().size()));
		if (!victory && h.bossHealthLeft() >= 0) {
			lines.add(Lang.get("fight.report.failure.health", FightRules.percent(h.bossHealthLeft())));
		}
		if (victory && (h.guardsPosted() > 0 || h.escortKilled() > 0)) {
			lines.add(Lang.get("fight.report.kills", h.guardsKilled(), h.guardsPosted(), h.escortKilled()));
		}
		List<FightReport.Title> titles = report.titles(cfg.categoryList());
		// The short form (failure) keeps the first title only.
		int shown = 0;
		for (FightReport.Title title : titles) {
			if (!victory && shown >= 1) {
				break;
			}
			lines.add(titleLine(title, h));
			shown++;
		}
		lines.add(ownLine(report, viewer, cfg));
		MutableComponent detail = Lang.get("fight.report.detail").withStyle(style -> style
				.withClickEvent(new ClickEvent.RunCommand(DETAIL_COMMAND))
				.withHoverEvent(new HoverEvent.ShowText(Lang.get("fight.report.detail.hover"))));
		lines.add(Lang.get("fight.report.indent").append(detail));
		return lines;
	}

	/** "Menace III", "Menace III ☠ Maudit", "Menace III — Enragé". */
	static String threat(FightReport.Header h) {
		String label = h.threat();
		if (h.cursed()) {
			label = Lang.raw("fight.report.cursed", label);
		} else if (h.risen()) {
			label = Lang.raw("fight.report.risen", label);
		}
		return label;
	}

	private static Component titleLine(FightReport.Title title, FightReport.Header h) {
		MutableComponent line = Lang.get("fight.report.title", Lang.raw("fight.title." + title.category().id));
		List<FightReport.Entry> holders = title.holders();
		for (int i = 0; i < holders.size() && i < MAX_HOLDERS; i++) {
			if (i > 0) {
				line.append(Lang.get("fight.report.and"));
			}
			line.append(name(holders.get(i)));
		}
		if (holders.size() > MAX_HOLDERS) {
			line.append(Lang.get("fight.report.more", holders.size() - MAX_HOLDERS));
		}
		if (title.category() == FightCategory.FINAL_BLOW) {
			line.append(Lang.get("fight.value.final_blow", h.boss()));
		} else {
			line.append(Lang.get("fight.value." + title.category().id, FightRules.number(title.value())));
		}
		return line;
	}

	/** A player's name with every number of their fight in its hover text. */
	static MutableComponent name(FightReport.Entry entry) {
		Component hover = hover(entry);
		return Component.literal(Lang.raw("fight.report.name", entry.name())).withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(hover)));
	}

	private static Component hover(FightReport.Entry entry) {
		MutableComponent text = Lang.get("fight.stat.header", entry.name());
		for (Component line : statLines(entry.tally())) {
			text.append(Component.literal("\n")).append(line);
		}
		return text;
	}

	private static Component ownLine(FightReport report, UUID viewer, FightStatsSettings cfg) {
		FightReport.Entry own = report.entry(viewer);
		if (own == null) {
			return Lang.get("fight.report.you.none", FightRules.number(cfg.minDamage));
		}
		FightTally t = own.tally();
		MutableComponent line = Lang.get("fight.report.you", report.rank(viewer), report.entries().size(), FightRules.number(t.damage(FightKind.BOSS)),
				FightRules.number(t.damage(FightKind.GUARD) + t.damage(FightKind.ESCORT)), FightRules.number(t.healing() + t.shielding()),
				FightRules.number(t.taken()));
		Component hover = hover(own);
		return line.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(hover)));
	}

	/** Every statistic of a player, one line each (chat hover and menu lore). */
	public static List<Component> statLines(FightTally t) {
		List<Component> lines = new ArrayList<>();
		lines.add(Lang.get("fight.stat.boss", FightRules.number(t.damage(FightKind.BOSS))));
		lines.add(Lang.get("fight.stat.guards", FightRules.number(t.damage(FightKind.GUARD)), t.kills(FightKind.GUARD)));
		lines.add(Lang.get("fight.stat.escort", FightRules.number(t.damage(FightKind.ESCORT)), t.kills(FightKind.ESCORT)));
		if (t.finalBlow()) {
			lines.add(Lang.get("fight.stat.final_blow"));
		}
		lines.add(Lang.get("fight.stat.taken", FightRules.number(t.taken()), t.deaths()));
		lines.add(Lang.get("fight.stat.healing", FightRules.number(t.healing()), FightRules.number(t.shielding())));
		lines.add(Lang.get("fight.stat.misc", t.spells(), FightRules.clock(t.seconds() * 20L)));
		return lines;
	}

	/** Every minute: the expired summaries are forgotten. */
	static void prune() {
		long now = Scheduler.ticks();
		Iterator<Stored> it = LAST.values().iterator();
		while (it.hasNext()) {
			if (it.next().expires() < now) {
				it.remove();
			}
		}
	}

	static void clear() {
		LAST.clear();
	}
}
