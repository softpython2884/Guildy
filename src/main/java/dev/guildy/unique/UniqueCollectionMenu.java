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
 * "Collection d'armes uniques" (6 rows): every unique of uniques.json5, four rows of seven per page. Weapons the guild
 * obtained (held now or ever) show their item and their current holders; the others are "???" with their rarity and where
 * they can drop. The armor sets come after the weapons, one row each (starting on a new page unless they all fit): the set
 * (its pieces obtained and its bonuses), then its pieces. Counter X / N in the title and on the main menu button.
 */
final class UniqueCollectionMenu extends GuildGui {
	private static final int ROWS = 4;
	private static final int COLUMNS = 7;
	/** Column of the first piece in a set row (the set itself is in the first column). */
	private static final int SET_PIECES_FROM = 2;
	private static final int MAX_HOLDERS = 3;

	/** One cell of the grid: a unique, the header of a set, or nothing. */
	private record Cell(UniqueConfig.@Nullable UniqueDef def, UniqueConfig.@Nullable SetDef set) {
	}

	private int page;

	private UniqueCollectionMenu(ServerPlayer player, Guild guild, int page) {
		super(player, 6, guild);
		this.page = page;
	}

	static void open(ServerPlayer player, Guild guild) {
		new UniqueCollectionMenu(player, guild, 0).show();
	}

	/** Rows of the grid: the uniques outside any set seven by seven, then one row per set (more when it has many pieces). */
	static List<Cell[]> grid(UniqueConfig cfg) {
		List<Cell[]> rows = new ArrayList<>();
		Cell[] row = null;
		int filled = 0;
		for (UniqueConfig.UniqueDef def : cfg.usable()) {
			if (cfg.set(def.set) != null) {
				continue;
			}
			if (row == null || filled == COLUMNS) {
				row = new Cell[COLUMNS];
				rows.add(row);
				filled = 0;
			}
			row[filled++] = new Cell(def, null);
		}
		List<Cell[]> setRows = new ArrayList<>();
		int perRow = COLUMNS - SET_PIECES_FROM;
		for (UniqueConfig.SetDef set : cfg.usableSets()) {
			List<String> pieces = cfg.piecesOf(set.id);
			for (int i = 0; i < pieces.size(); i += perRow) {
				Cell[] line = new Cell[COLUMNS];
				if (i == 0) {
					line[0] = new Cell(null, set);
				}
				for (int j = 0; j < perRow && i + j < pieces.size(); j++) {
					line[SET_PIECES_FROM + j] = new Cell(cfg.item(pieces.get(i + j)), null);
				}
				setRows.add(line);
			}
		}
		int free = rows.size() % ROWS == 0 ? 0 : ROWS - rows.size() % ROWS;
		if (!setRows.isEmpty() && free > 0 && free < setRows.size()) {
			// The sets start on a page of their own.
			for (int i = 0; i < free; i++) {
				rows.add(new Cell[COLUMNS]);
			}
		}
		rows.addAll(setRows);
		return rows;
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
		return Math.max(1, (grid(UniqueModule.config()).size() + ROWS - 1) / ROWS);
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
		List<Cell[]> grid = grid(cfg);
		int pages = Math.max(1, (grid.size() + ROWS - 1) / ROWS);
		page = Math.max(0, Math.min(page, pages - 1));
		Set<String> obtained = ledger.obtainedBy(guild.id);
		Map<String, List<UniqueLedger.Copy>> held = new LinkedHashMap<>();
		for (UniqueLedger.Copy copy : ledger.heldCopies(guild.id)) {
			held.computeIfAbsent(copy.id, id -> new ArrayList<>()).add(copy);
		}
		setSlot(4, header(cfg, obtainedCount(guild), cfg.usable().size(), held.size()));
		for (int r = 0; r < ROWS; r++) {
			int index = page * ROWS + r;
			if (index >= grid.size()) {
				break;
			}
			Cell[] row = grid.get(index);
			for (int c = 0; c < COLUMNS; c++) {
				Cell cell = row[c];
				int slot = (r + 1) * 9 + c + 1;
				if (cell == null) {
					continue;
				}
				if (cell.set() != null) {
					setSlot(slot, setIcon(cfg, cell.set(), obtained));
				} else if (cell.def() != null) {
					UniqueConfig.UniqueDef def = cell.def();
					setSlot(slot, obtained.contains(def.id) ? known(cfg, def, held.getOrDefault(def.id, List.of())) : unknown(cfg, def));
				}
			}
		}
		if (cfg.usable().isEmpty()) {
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

	/** A set: its pieces obtained by the guild (the others "???"), its bonuses; glowing when complete. */
	private static GuiElementBuilder setIcon(UniqueConfig cfg, UniqueConfig.SetDef set, Set<String> obtained) {
		List<String> pieces = cfg.piecesOf(set.id);
		int count = 0;
		List<Component> lore = new ArrayList<>();
		for (String piece : pieces) {
			if (obtained.contains(piece)) {
				count++;
			}
		}
		lore.add(Lang.get("unique.collection.set.count", count, pieces.size()));
		for (String piece : pieces) {
			UniqueConfig.UniqueDef def = cfg.item(piece);
			lore.add(obtained.contains(piece) && def != null ? Lang.get("unique.collection.set.obtained", Lang.strip(Lang.colorize(def.name)))
					: Lang.get("unique.collection.set.missing"));
		}
		for (UniqueConfig.SetBonus bonus : set.bonuses()) {
			lore.add(Component.empty());
			lore.add(Lang.get("unique.set.bonus.neutral", bonus.pieces, bonus.name));
			for (String line : UniqueItems.bonusLines(bonus)) {
				lore.add(Lang.get("unique.set.line.neutral", line));
			}
		}
		lore.add(Component.empty());
		lore.addAll(GuiUtil.lines("unique.collection.set.hint"));
		GuiElementBuilder icon = GuiUtil.item(Items.ARMOR_STAND, Component.literal(Lang.colorize(set.name)), lore);
		if (!pieces.isEmpty() && count >= pieces.size()) {
			icon.glow();
		}
		return icon;
	}

	/** "✧ Panoplie de l'Hiver éternel" under a piece of a set (nothing for the other uniques). */
	private static void setLine(UniqueConfig cfg, UniqueConfig.UniqueDef def, List<Component> lore) {
		UniqueConfig.SetDef set = cfg.set(def.set);
		if (set != null) {
			lore.add(Lang.get("unique.collection.set", Lang.colorize(set.name)));
		}
	}

	/** An obtained weapon: its look, rarity, power and who holds it now. */
	private static GuiElementBuilder known(UniqueConfig cfg, UniqueConfig.UniqueDef def, List<UniqueLedger.Copy> copies) {
		ItemStack display = UniqueItems.display(def, GuildyServer.registryAccess());
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("unique.item.header", UniqueItems.rarityName(def.rarity())));
		UniquePower power = def.power();
		if (power != null) {
			lore.add(Lang.get("unique.item.power", Lang.raw(power.langKey() + ".name")));
		}
		setLine(cfg, def, lore);
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
		setLine(cfg, def, lore);
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
