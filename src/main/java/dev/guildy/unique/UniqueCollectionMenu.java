package dev.guildy.unique;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Hooks;
import dev.guildy.core.Lang;
import dev.guildy.data.Guild;
import dev.guildy.gui.GuiUtil;
import dev.guildy.gui.GuildGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Collection d'armes uniques" (6 rows): every unique weapon of uniques.json5, 28 per page. Weapons the guild obtained
 * (held now or ever) show their item and their current holders; the others are "???" with their rarity and where they
 * can drop. Counter X / N in the title and on the main menu button.
 */
final class UniqueCollectionMenu extends GuildGui {
	private static final int[] SLOTS = slots();
	private static final int MAX_HOLDERS = 3;

	private int page;

	private UniqueCollectionMenu(ServerPlayer player, Guild guild, int page) {
		super(player, 6, guild);
		this.page = page;
	}

	static void open(ServerPlayer player, Guild guild) {
		new UniqueCollectionMenu(player, guild, 0).show();
	}

	private static int[] slots() {
		int[] slots = new int[28];
		int i = 0;
		for (int row = 1; row <= 4; row++) {
			for (int col = 1; col <= 7; col++) {
				slots[i++] = row * 9 + col;
			}
		}
		return slots;
	}

	/** Ids of the usable weapons the guild ever obtained. */
	static int obtainedCount(Guild guild) {
		Set<String> obtained = UniqueRegistry.ledger().obtainedBy(guild.id);
		int count = 0;
		for (UniqueConfig.UniqueDef def : UniqueModule.config().usable()) {
			if (obtained.contains(def.id)) {
				count++;
			}
		}
		return count;
	}

	/** Lore of the main menu button: description + counter. */
	static List<Component> summary(Guild guild) {
		List<Component> lore = new ArrayList<>(GuiUtil.lines("unique.menu.lore"));
		lore.add(Component.empty());
		lore.add(Lang.get("unique.menu.count", obtainedCount(guild), UniqueModule.config().usable().size()));
		return lore;
	}

	private static int pages() {
		return GuiUtil.pages(UniqueModule.config().usable().size(), SLOTS.length);
	}

	@Override
	protected Component title(@Nullable Guild guild) {
		int total = UniqueModule.config().usable().size();
		int count = guild == null ? 0 : obtainedCount(guild);
		int pages = pages();
		return pages > 1 ? Lang.get("unique.collection.title_paged", count, total, page + 1, pages)
				: Lang.get("unique.collection.title", count, total);
	}

	@Override
	protected void build(@Nullable Guild guild) {
		if (guild == null) {
			return;
		}
		UniqueConfig cfg = UniqueModule.config();
		UniqueLedger ledger = UniqueRegistry.ledger();
		List<UniqueConfig.UniqueDef> defs = new ArrayList<>(cfg.usable());
		int pages = pages();
		page = Math.max(0, Math.min(page, pages - 1));
		Set<String> obtained = ledger.obtainedBy(guild.id);
		Map<String, List<UniqueLedger.Copy>> held = new LinkedHashMap<>();
		for (UniqueLedger.Copy copy : ledger.heldCopies(guild.id)) {
			held.computeIfAbsent(copy.id, id -> new ArrayList<>()).add(copy);
		}
		setSlot(4, header(cfg, obtainedCount(guild), defs.size(), held.size()));
		for (int i = 0; i < SLOTS.length; i++) {
			int index = page * SLOTS.length + i;
			if (index >= defs.size()) {
				break;
			}
			UniqueConfig.UniqueDef def = defs.get(index);
			setSlot(SLOTS[i], obtained.contains(def.id) ? known(def, held.getOrDefault(def.id, List.of())) : unknown(cfg, def));
		}
		if (defs.isEmpty()) {
			setSlot(22, GuiUtil.item(Items.BARRIER, Lang.get("unique.collection.empty"), List.of()));
		}
		bottomBar(() -> Hooks.openMainMenu.accept(player));
		pager(page, pages, target -> {
			page = target;
			render();
		});
	}

	private static GuiElementBuilder header(UniqueConfig cfg, int obtained, int total, int heldNow) {
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("unique.collection.header.count", obtained, total));
		lore.add(Lang.get("unique.collection.header.held", heldNow));
		lore.add(Component.empty());
		lore.addAll(GuiUtil.lines("unique.collection.mode." + cfg.mode().id()));
		GuiElementBuilder icon = GuiUtil.item(Items.NETHER_STAR, Lang.get("unique.collection.header.name"), lore);
		if (total > 0 && obtained >= total) {
			icon.glow();
		}
		return icon;
	}

	/** An obtained weapon: its look, rarity, power and who holds it now. */
	private static GuiElementBuilder known(UniqueConfig.UniqueDef def, List<UniqueLedger.Copy> copies) {
		ItemStack display = UniqueItems.display(def, GuildyServer.registryAccess());
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("unique.item.header", UniqueItems.rarityName(def.rarity())));
		UniquePower power = def.power();
		if (power != null) {
			lore.add(Lang.get("unique.item.power", Lang.raw(power.langKey() + ".name")));
		}
		lore.add(Component.empty());
		if (copies.isEmpty()) {
			lore.add(Lang.get("unique.collection.gone"));
		} else {
			long now = System.currentTimeMillis();
			for (int i = 0; i < Math.min(MAX_HOLDERS, copies.size()); i++) {
				UniqueLedger.Copy copy = copies.get(i);
				String holder = copy.playerName == null || copy.playerName.isEmpty() ? Lang.raw("unique.collection.holder.guild") : copy.playerName;
				lore.add(Lang.get("unique.collection.held", holder, ago(now, copy.lastSeen)));
			}
			if (copies.size() > MAX_HOLDERS) {
				lore.add(Lang.get("unique.collection.held_more", copies.size() - MAX_HOLDERS));
			}
		}
		if (display.isEmpty()) {
			return GuiUtil.item(Items.BARRIER, UniqueItems.name(def), lore);
		}
		return GuiUtil.item(display, UniqueItems.name(def), lore);
	}

	/** A weapon never obtained: "???", its rarity and where it drops. */
	private static GuiElementBuilder unknown(UniqueConfig cfg, UniqueConfig.UniqueDef def) {
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("unique.item.header", UniqueItems.rarityName(def.rarity())));
		lore.add(Component.empty());
		lore.add(Lang.get("unique.collection.where"));
		boolean any = false;
		for (Map.Entry<String, UniqueConfig.DropTable> entry : cfg.drops.entrySet()) {
			double chance = UniqueRolls.probability(entry.getValue(), def.id, key -> cfg.item(key) != null);
			if (chance > 0) {
				any = true;
				lore.add(Lang.get("unique.collection.where_line", Lang.raw(sourceKey(entry.getKey())), UniqueCommands.percent(chance)));
			}
		}
		// Guardians that favour this weapon (their own, higher odds).
		for (Map.Entry<String, Map<String, Double>> entry : cfg.favorites.entrySet()) {
			if (entry.getValue().getOrDefault(def.id, 0.0) <= 0) {
				continue;
			}
			double chance = UniqueRolls.probability(cfg.effectiveTable(entry.getKey()), def.id, key -> cfg.item(key) != null);
			if (chance > 0) {
				any = true;
				lore.add(Lang.get("unique.collection.where_line", favoriteName(entry.getKey()), UniqueCommands.percent(chance)));
			}
		}
		if (!any) {
			lore.add(Lang.get("unique.collection.where_none"));
		}
		return GuiUtil.item(Items.FIREWORK_STAR, Lang.get("unique.collection.unknown", def.rarity().namePrefix()), lore);
	}

	/** "le gardien du Bastion de givre" for "ruin_boss@bastion_de_givre" (unknown context: source name + context id). */
	static String favoriteName(String key) {
		List<String> contexts = UniqueConfig.contexts(key);
		String context = contexts.isEmpty() ? "" : contexts.getFirst();
		String lang = "unique.favorite." + context;
		return Lang.has(lang) ? Lang.raw(lang) : Lang.raw("unique.favorite.unknown", Lang.raw(sourceKey(UniqueConfig.baseSource(key))), context);
	}

	static String sourceKey(String source) {
		String key = "unique.source." + source;
		return Lang.has(key) ? key : "unique.source.unknown";
	}

	/** "à l'instant", "il y a 5 min", "il y a 3 h", "il y a 2 j". */
	static String ago(long now, long then) {
		long seconds = Math.max(0, (now - then) / 1000);
		if (then <= 0) {
			return Lang.raw("unique.time.never");
		}
		if (seconds < 60) {
			return Lang.raw("unique.time.now");
		}
		if (seconds < 3600) {
			return Lang.raw("unique.time.minutes", seconds / 60);
		}
		if (seconds < 86400) {
			return Lang.raw("unique.time.hours", seconds / 3600);
		}
		return Lang.raw("unique.time.days", seconds / 86400);
	}
}
