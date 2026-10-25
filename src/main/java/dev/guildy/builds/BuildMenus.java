package dev.guildy.builds;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Hooks;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import dev.guildy.gui.GuiUtil;
import dev.guildy.villager.Builders;
import dev.guildy.villager.Procurement;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Menus of the construction sites: the list ("Chantiers"), the schematic picker, the adjust menu of a placement, the
 * detail of a site (progress by pass, fronts, builders, options), its materials and its obstacles. Every action checks
 * the VILLAGERS permission again.
 */
public final class BuildMenus {
	private static final int PER_PAGE = 45;
	private static final Map<UUID, SimpleGui> PICKERS = new HashMap<>();

	private BuildMenus() {
	}

	// ================================================================ helpers

	private static SimpleGui chest(ServerPlayer player, int rows, Component title) {
		SimpleGui gui = new SimpleGui(GuiUtil.rows(rows), player, false) {
			@Override
			public boolean click(int index, ClickType type, ContainerInput action) {
				if (!(type.isLeft || type.isRight) || type.isDragging) {
					return false;
				}
				return Safe.get("build.gui", () -> super.click(index, type, action), false);
			}
		};
		gui.setTitle(title);
		return gui;
	}

	static boolean allowed(ServerPlayer player, Guild guild) {
		if (GuildManager.of(player) != guild || !guild.can(player.getUUID(), GuildPermission.VILLAGERS)) {
			Msg.send(player, "error.no_permission");
			GuiUtil.errorSound(player);
			return false;
		}
		return true;
	}

	/** Status of a site (lang key, see {@link #statusArgs}). */
	public static String statusKey(@Nullable BuildSite site) {
		if (site == null) {
			return "build.status.finished";
		}
		BuildProject project = site.project;
		if (project.finished) {
			return "build.status.finished";
		}
		if (site.error() != null) {
			return "build.status.error";
		}
		if (!site.ready()) {
			return "build.status.loading";
		}
		if (project.paused) {
			return "build.status.paused";
		}
		if (!site.scanned()) {
			return "build.status.scanning";
		}
		BuildSite.Counts counts = site.counts();
		if (site.actionable() > 0) {
			return "build.status.working";
		}
		if (counts.need() > 0) {
			return "build.status.materials";
		}
		if (counts.blocked() > 0) {
			return "build.status.blocked";
		}
		if (counts.unknown() > 0) {
			return "build.status.unloaded";
		}
		return "build.status.waiting";
	}

	public static Object[] statusArgs(@Nullable BuildSite site) {
		if (site == null) {
			return new Object[0];
		}
		if (site.error() != null) {
			return new Object[] {Lang.raw(site.error(), site.errorArgs())};
		}
		BuildSite.Counts counts = site.counts();
		long now = Scheduler.ticks();
		return new Object[] {site.workers(now), counts.need(), counts.blocked(), counts.unknown(), counts.waiting() + counts.hold()};
	}

	private static String status(@Nullable BuildSite site) {
		return Lang.raw(statusKey(site), statusArgs(site));
	}

	private static Item icon(BuildProject project, @Nullable BuildSite site) {
		if (project.finished) {
			return Items.EMERALD_BLOCK;
		}
		BuildPlan plan = site == null ? null : site.plan();
		if (plan != null) {
			for (Map.Entry<String, Integer> entry : plan.data.topBlocks(1)) {
				net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(entry.getKey());
				if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
					Item item = BuiltInRegistries.ITEM.getValue(id);
					if (item != Items.AIR) {
						return item;
					}
				}
			}
		}
		return Items.BRICKS;
	}

	/** Sort key of an item (its id: the server only knows the English names). */
	private static String itemName(Item item) {
		return ItemUtil.id(item);
	}

	/** A lang line followed by a name the client translates (item, block), in that colour. */
	private static Component withName(Component line, Component name, ChatFormatting color) {
		return Component.empty().append(line).append(name.copy().withStyle(color));
	}

	// ================================================================ list

	public static void openList(ServerPlayer player, Guild guild) {
		openList(player, guild, 0);
	}

	static void openList(ServerPlayer player, Guild guild, int page) {
		if (!guild.isMember(player.getUUID())) {
			Msg.send(player, "error.no_guild");
			return;
		}
		List<BuildProject> projects = guild.builds.ordered();
		int pages = GuiUtil.pages(projects.size(), PER_PAGE);
		int current = Math.max(0, Math.min(page, pages - 1));
		BuildConfig config = BuildModule.config();
		SimpleGui gui = chest(player, 6, Lang.get("build.gui.list.title", guild.builds.activeCount(), config.maxActive(guild.level)));
		for (int i = 0; i < PER_PAGE; i++) {
			int index = current * PER_PAGE + i;
			if (index >= projects.size()) {
				break;
			}
			BuildProject project = projects.get(index);
			gui.setSlot(i, projectIcon(guild, project, true).setCallback((slot, type, action, g) -> {
				GuiUtil.clickSound(player);
				openDetail(player, guild, project.id);
			}));
		}
		if (projects.isEmpty()) {
			gui.setSlot(22, GuiUtil.item(Items.BRICKS, Lang.get("build.gui.list.empty"), GuiUtil.lines("build.gui.list.empty.lore")));
		}
		gui.setSlot(45, GuiUtil.back(() -> Hooks.openVillagers.accept(player, guild)));
		if (current > 0) {
			gui.setSlot(46, GuiUtil.item(Items.ARROW, Lang.get("gui.page.prev"), List.of(Lang.get("gui.page", current + 1, pages)))
					.setCallback((slot, type, action, g) -> openList(player, guild, current - 1)));
		}
		boolean full = guild.builds.activeCount() >= config.maxActive(guild.level);
		List<Component> newLore = new ArrayList<>(GuiUtil.lines("build.gui.new.lore"));
		if (full) {
			newLore.add(Lang.get("build.gui.new.full", config.maxActive(guild.level)));
		}
		gui.setSlot(48, GuiUtil.item(Items.WRITABLE_BOOK, Lang.get("build.gui.new"), newLore).setCallback((slot, type, action, g) -> {
			if (!allowed(player, guild)) {
				return;
			}
			if (guild.builds.activeCount() >= config.maxActive(guild.level)) {
				Msg.send(player, "build.error.limit", config.maxActive(guild.level));
				GuiUtil.errorSound(player);
				return;
			}
			GuiUtil.clickSound(player);
			openPicker(player, guild, 0);
		}));
		if (SyncmaticaLink.available()) {
			gui.setSlot(47, litematicaButton(player, guild));
		}
		gui.setSlot(50, GuiUtil.item(Items.BOOK, Lang.get("build.gui.help"), GuiUtil.lines("build.gui.help.lore")));
		if (current < pages - 1) {
			gui.setSlot(52, GuiUtil.item(Items.ARROW, Lang.get("gui.page.next"), List.of(Lang.get("gui.page", current + 1, pages)))
					.setCallback((slot, type, action, g) -> openList(player, guild, current + 1)));
		}
		gui.setSlot(53, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	private static GuiElementBuilder projectIcon(Guild guild, BuildProject project, boolean clickHint) {
		BuildSite site = BuildSites.site(guild, project);
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("build.gui.lore.status", status(site)));
		int percent = project.finished ? 100 : site != null && site.scanned() ? site.counts().percent() : project.percent();
		lore.add(Lang.get("build.gui.lore.progress", progressBar(percent), percent));
		lore.add(Lang.get("build.gui.lore.size", project.sizeX, project.sizeY, project.sizeZ));
		if (project.origin != null) {
			int[] box = project.box();
			lore.add(Lang.get("build.gui.lore.position", box[0], box[1], box[2]));
		}
		if (!project.finished) {
			long now = Scheduler.ticks();
			int workers = site == null ? 0 : site.workers(now);
			lore.add(Lang.get("build.gui.lore.builders", workers, Builders.assigned(guild, project)));
			if (project.priority != 0) {
				lore.add(Lang.get("build.gui.lore.priority", signed(project.priority)));
			}
		} else {
			lore.add(Lang.get("build.gui.lore.finished", project.placed, project.cleared));
		}
		if (clickHint) {
			lore.add(Component.empty());
			lore.add(Lang.get("build.gui.lore.click"));
		}
		GuiElementBuilder builder = GuiUtil.item(icon(project, site), Lang.get("build.gui.project.name", project.name), lore);
		if (site != null && site.actionable() > 0 && !project.paused) {
			builder.glow();
		}
		return builder;
	}

	static String progressBar(int percent) {
		int filled = Math.max(0, Math.min(20, percent / 5));
		return "§a" + "|".repeat(filled) + "§8" + "|".repeat(20 - filled);
	}

	private static String signed(int value) {
		return value > 0 ? "+" + value : String.valueOf(value);
	}

	// ================================================================ picker

	public static void openPicker(ServerPlayer player, Guild guild, int page) {
		List<SchematicLibrary.Entry> entries = SchematicLibrary.list();
		int pages = GuiUtil.pages(entries.size(), PER_PAGE);
		int current = Math.max(0, Math.min(page, pages - 1));
		SimpleGui gui = chest(player, 6, Lang.get("build.gui.picker.title", entries.size()));
		boolean pending = false;
		for (int i = 0; i < PER_PAGE; i++) {
			int index = current * PER_PAGE + i;
			if (index >= entries.size()) {
				break;
			}
			SchematicLibrary.Entry entry = entries.get(index);
			SchematicLibrary.Result result = SchematicLibrary.get(entry, loaded -> refreshPicker(player, guild, current));
			pending |= result == null;
			gui.setSlot(i, entryIcon(entry, result).setCallback((slot, type, action, g) -> {
				if (!allowed(player, guild)) {
					return;
				}
				SchematicLibrary.Result now = SchematicLibrary.get(entry, null);
				if (now == null) {
					Msg.actionBar(player, "build.preview.loading");
					GuiUtil.clickSound(player);
					return;
				}
				if (!now.ok()) {
					Msg.send(player, "build.error.unusable", Lang.raw(now.error(), now.args()));
					GuiUtil.errorSound(player);
					return;
				}
				g.close();
				BuildPlacement.give(player, guild, entry, now.plan());
			}));
		}
		if (entries.isEmpty()) {
			gui.setSlot(22, GuiUtil.item(Items.PAPER, Lang.get("build.gui.picker.empty"), GuiUtil.lines("build.gui.picker.empty.lore",
					"config/guildy/schematics")));
		}
		gui.setSlot(45, GuiUtil.back(() -> openList(player, guild, 0)));
		if (current > 0) {
			gui.setSlot(46, GuiUtil.item(Items.ARROW, Lang.get("gui.page.prev"), List.of(Lang.get("gui.page", current + 1, pages)))
					.setCallback((slot, type, action, g) -> openPicker(player, guild, current - 1)));
		}
		if (SyncmaticaLink.available()) {
			gui.setSlot(47, litematicaButton(player, guild));
		}
		gui.setSlot(49, GuiUtil.item(Items.BOOK, Lang.get("build.gui.picker.help"), GuiUtil.lines("build.gui.picker.help.lore")));
		if (current < pages - 1) {
			gui.setSlot(52, GuiUtil.item(Items.ARROW, Lang.get("gui.page.next"), List.of(Lang.get("gui.page", current + 1, pages)))
					.setCallback((slot, type, action, g) -> openPicker(player, guild, current + 1)));
		}
		gui.setSlot(53, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
		if (pending) {
			PICKERS.put(player.getUUID(), gui);
		}
	}

	/** A schematic of the page was analysed: the picker (if still open) shows it. */
	private static void refreshPicker(ServerPlayer player, Guild guild, int page) {
		SimpleGui open = PICKERS.get(player.getUUID());
		if (open == null || !open.isOpen() || player.hasDisconnected()) {
			return;
		}
		// One refresh for the files analysed within half a second (no flicker while a page loads).
		PICKERS.remove(player.getUUID());
		Scheduler.later("build.picker.refresh", 10, () -> {
			if (open.isOpen()) {
				openPicker(player, guild, page);
			}
		});
	}

	private static GuiElementBuilder entryIcon(SchematicLibrary.Entry entry, SchematicLibrary.@Nullable Result result) {
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get(entry.world() ? "build.gui.entry.world" : "build.gui.entry.config", entry.display()));
		lore.add(Lang.get("build.gui.entry.file_size", Math.max(1, entry.size() / 1024)));
		Item item = entry.world() ? Items.STRUCTURE_BLOCK : entry.display().toLowerCase(java.util.Locale.ROOT).endsWith(".litematic") ? Items.FILLED_MAP : Items.BRICKS;
		if (result == null) {
			lore.add(Lang.get("build.gui.entry.loading"));
			return GuiUtil.item(Items.CLOCK, Lang.get("build.gui.entry.name", BuildPlacement.displayName(entry)), lore);
		}
		if (!result.ok()) {
			lore.addAll(GuiUtil.lines("build.gui.entry.error", Lang.raw(result.error(), result.args())));
			return GuiUtil.item(Items.BARRIER, Lang.get("build.gui.entry.name", BuildPlacement.displayName(entry)), lore);
		}
		BuildPlan plan = result.plan();
		SchematicData data = plan.data;
		lore.add(Lang.get("build.gui.entry.format", Lang.raw("build.format." + data.format)));
		lore.add(Lang.get("build.gui.entry.size", data.sizeX, data.sizeY, data.sizeZ));
		lore.add(Lang.get("build.gui.entry.blocks", data.nonAir));
		List<Map.Entry<String, Integer>> top = data.topBlocks(4);
		if (!top.isEmpty()) {
			lore.add(Lang.get("build.gui.entry.top"));
			for (Map.Entry<String, Integer> block : top) {
				lore.add(withName(Lang.get("build.gui.entry.top.line", block.getValue()), blockName(block.getKey()), ChatFormatting.GRAY));
			}
		}
		int notBuildable = plan.notBuildableCells();
		if (notBuildable > 0) {
			lore.add(Lang.get("build.gui.entry.not_buildable", notBuildable));
		}
		if (!plan.warnings.isEmpty()) {
			lore.add(Lang.get("build.gui.entry.warnings", plan.warnings.size()));
			int shown = 0;
			for (String warning : plan.warnings) {
				if (shown++ >= 3) {
					break;
				}
				lore.add(Lang.get("build.gui.entry.warning", warning(warning)));
			}
		}
		lore.add(Component.empty());
		lore.add(Lang.get("build.gui.entry.click"));
		return GuiUtil.item(item, Lang.get("build.gui.entry.name", BuildPlacement.displayName(entry)), lore);
	}

	/** "key|arg|arg" warning as text. */
	static String warning(String warning) {
		String[] parts = warning.split("\\|");
		Object[] args = new Object[parts.length - 1];
		System.arraycopy(parts, 1, args, 0, args.length);
		return Lang.raw(parts[0], args);
	}

	private static Component blockName(String id) {
		net.minecraft.resources.Identifier identifier = net.minecraft.resources.Identifier.tryParse(id);
		if (identifier != null && BuiltInRegistries.BLOCK.containsKey(identifier)) {
			return BuiltInRegistries.BLOCK.getValue(identifier).getName();
		}
		return Component.literal(id);
	}

	// ================================================================ Litematica

	private static GuiElementBuilder litematicaButton(ServerPlayer player, Guild guild) {
		return GuiUtil.item(Items.FILLED_MAP, Lang.get("build.gui.litematica"), GuiUtil.lines("build.gui.litematica.lore"))
				.setCallback((slot, type, action, g) -> {
					GuiUtil.clickSound(player);
					openShared(player, guild, 0);
				});
	}

	/** The placements shared with Syncmatica in the guild's territory (or by its members). */
	public static void openShared(ServerPlayer player, Guild guild, int page) {
		if (!SyncmaticaLink.available()) {
			Msg.send(player, "build.litematica.unavailable");
			return;
		}
		List<SyncmaticaLink.Shared> shared = SyncmaticaLink.forGuild(guild);
		int pages = GuiUtil.pages(shared.size(), PER_PAGE);
		int current = Math.max(0, Math.min(page, pages - 1));
		SimpleGui gui = chest(player, 6, Lang.get("build.gui.shared.title", shared.size()));
		for (int i = 0; i < PER_PAGE; i++) {
			int index = current * PER_PAGE + i;
			if (index >= shared.size()) {
				break;
			}
			SyncmaticaLink.Shared one = shared.get(index);
			gui.setSlot(i, sharedIcon(guild, one).setCallback((slot, type, action, g) -> pickShared(player, guild, one, gui)));
		}
		if (shared.isEmpty()) {
			gui.setSlot(22, GuiUtil.item(Items.MAP, Lang.get("build.gui.shared.empty"), GuiUtil.lines("build.gui.shared.empty.lore")));
		}
		gui.setSlot(45, GuiUtil.back(() -> openList(player, guild, 0)));
		if (current > 0) {
			gui.setSlot(46, GuiUtil.item(Items.ARROW, Lang.get("gui.page.prev"), List.of(Lang.get("gui.page", current + 1, pages)))
					.setCallback((slot, type, action, g) -> openShared(player, guild, current - 1)));
		}
		gui.setSlot(49, GuiUtil.item(Items.BOOK, Lang.get("build.gui.shared.help"), GuiUtil.lines("build.gui.shared.help.lore")));
		if (current < pages - 1) {
			gui.setSlot(52, GuiUtil.item(Items.ARROW, Lang.get("gui.page.next"), List.of(Lang.get("gui.page", current + 1, pages)))
					.setCallback((slot, type, action, g) -> openShared(player, guild, current + 1)));
		}
		gui.setSlot(53, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	private static GuiElementBuilder sharedIcon(Guild guild, SyncmaticaLink.Shared shared) {
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("build.gui.shared.owner", shared.owner()));
		lore.add(Lang.get("build.gui.shared.origin", shared.x(), shared.y(), shared.z()));
		lore.add(Lang.get("build.gui.adjust.rotation", Lang.raw("build.rotation." + shared.turn().name().toLowerCase(java.util.Locale.ROOT)),
				Lang.raw("build.mirror." + shared.flip().id())));
		boolean here = GuildManager.atChunk(dev.guildy.data.ChunkKey.of(shared.x() >> 4, shared.z() >> 4)) == guild;
		lore.add(Lang.get(here ? "build.gui.shared.here" : "build.gui.shared.elsewhere"));
		if (shared.subregions()) {
			lore.addAll(GuiUtil.lines("build.gui.shared.subregions"));
		}
		java.nio.file.Path file = SyncmaticaLink.file(shared);
		boolean present = file != null && java.nio.file.Files.isRegularFile(file);
		if (!present) {
			lore.add(Lang.get("build.gui.shared.missing"));
		}
		lore.add(Component.empty());
		lore.add(Lang.get("build.gui.shared.click"));
		return GuiUtil.item(present ? Items.FILLED_MAP : Items.BARRIER, Lang.get("build.gui.entry.name", shared.name()), lore);
	}

	private static void pickShared(ServerPlayer player, Guild guild, SyncmaticaLink.Shared shared, SimpleGui gui) {
		if (!allowed(player, guild)) {
			return;
		}
		BuildConfig config = BuildModule.config();
		if (guild.builds.activeCount() >= config.maxActive(guild.level)) {
			Msg.send(player, "build.error.limit", config.maxActive(guild.level));
			GuiUtil.errorSound(player);
			return;
		}
		GuiUtil.clickSound(player);
		gui.close();
		BuildPlacement.startShared(player, guild, shared);
	}

	// ================================================================ adjust

	static void openAdjust(ServerPlayer player, Guild guild) {
		BuildPreview.Session session = BuildPreview.session(player);
		if (session == null) {
			Msg.send(player, "build.preview.none");
			return;
		}
		BuildPlacement.validate(player, session);
		SchematicData data = session.plan.data;
		PlacementTransform t = session.transform;
		SimpleGui gui = chest(player, 5, Lang.get("build.gui.adjust.title", session.display));
		List<Component> info = new ArrayList<>();
		info.add(Lang.get("build.gui.adjust.size", data.sizeX, data.sizeY, data.sizeZ, data.nonAir));
		int[] box = session.box();
		info.add(Lang.get("build.gui.adjust.box", box[0], box[1], box[2], box[3], box[4], box[5]));
		info.add(Lang.get("build.gui.adjust.rotation", Lang.raw("build.rotation." + t.turn().name().toLowerCase(java.util.Locale.ROOT)),
				Lang.raw("build.mirror." + t.flip().id())));
		info.add(Component.empty());
		if (session.error == null) {
			info.add(Lang.get("build.gui.adjust.valid"));
		} else {
			info.addAll(GuiUtil.lines("build.gui.adjust.invalid", Lang.raw(session.error, session.errorArgs)));
		}
		GuiElementBuilder card = GuiUtil.item(session.error == null ? Items.FILLED_MAP : Items.BARRIER,
				Lang.get("build.gui.adjust.info", session.display), info);
		gui.setSlot(4, card);
		gui.setSlot(19, GuiUtil.item(Items.COMPASS, Lang.get("build.gui.adjust.rotate_left"), GuiUtil.lines("build.gui.adjust.rotate.lore"))
				.setCallback((slot, type, action, g) -> adjust(player, guild, session,
						session.transform.reoriented(session.transform.turn().counterClockwise(), session.transform.flip(), data.sizeX, data.sizeY, data.sizeZ))));
		gui.setSlot(20, GuiUtil.item(Items.RECOVERY_COMPASS, Lang.get("build.gui.adjust.rotate_right"), GuiUtil.lines("build.gui.adjust.rotate.lore"))
				.setCallback((slot, type, action, g) -> adjust(player, guild, session,
						session.transform.reoriented(session.transform.turn().clockwise(), session.transform.flip(), data.sizeX, data.sizeY, data.sizeZ))));
		gui.setSlot(21, GuiUtil.item(Items.GLASS_PANE, Lang.get("build.gui.adjust.mirror", Lang.raw("build.mirror." + t.flip().id())),
				GuiUtil.lines("build.gui.adjust.mirror.lore")).setCallback((slot, type, action, g) -> adjust(player, guild, session,
				session.transform.reoriented(session.transform.turn(), session.transform.flip().next(), data.sizeX, data.sizeY, data.sizeZ))));
		gui.setSlot(23, moveButton(player, guild, session, Items.DYE.pick(DyeColor.RED), "x", 1, 0, 0));
		gui.setSlot(24, moveButton(player, guild, session, Items.DYE.pick(DyeColor.BLUE), "z", 0, 0, 1));
		gui.setSlot(25, moveButton(player, guild, session, Items.DYE.pick(DyeColor.LIME), "y", 0, 1, 0));
		if (session.shared) {
			gui.setSlot(29, GuiUtil.item(Items.FILLED_MAP, Lang.get("build.gui.adjust.litematica"), GuiUtil.lines("build.gui.adjust.litematica.lore"))
					.setCallback((slot, type, action, g) -> {
						SyncmaticaLink.Shared shared = SyncmaticaLink.byKey(session.entryKey);
						if (shared == null) {
							Msg.send(player, "build.litematica.gone");
							GuiUtil.errorSound(player);
							return;
						}
						adjust(player, guild, session, SyncmaticaLink.transform(shared, data));
					}));
		} else {
			gui.setSlot(29, GuiUtil.item(Items.ENDER_EYE, Lang.get("build.gui.adjust.anchor"), GuiUtil.lines("build.gui.adjust.anchor.lore"))
					.setCallback((slot, type, action, g) -> {
						GuiUtil.clickSound(player);
						g.close();
						Msg.actionBar(player, "build.plan.hint");
					}));
		}
		GuiElementBuilder confirm = GuiUtil.item(Items.CONCRETE.pick(session.error == null ? DyeColor.LIME : DyeColor.GRAY), Lang.get("build.gui.adjust.confirm"),
				session.error == null ? GuiUtil.lines("build.gui.adjust.confirm.lore") : GuiUtil.lines("build.gui.adjust.invalid", Lang.raw(session.error, session.errorArgs)));
		if (session.error == null) {
			confirm.glow();
		}
		gui.setSlot(31, confirm.setCallback((slot, type, action, g) -> {
			if (!allowed(player, guild)) {
				return;
			}
			BuildProject project = BuildPlacement.confirm(player, guild);
			if (project == null) {
				GuiUtil.errorSound(player);
				openAdjust(player, guild);
				return;
			}
			GuiUtil.successSound(player);
			openDetail(player, guild, project.id);
		}));
		gui.setSlot(33, GuiUtil.item(Items.CONCRETE.pick(DyeColor.RED), Lang.get("build.gui.adjust.cancel"), GuiUtil.lines("build.gui.adjust.cancel.lore"))
				.setCallback((slot, type, action, g) -> {
					GuiUtil.clickSound(player);
					BuildPreview.end(player);
					BuildPlacement.removePlans(player);
					Msg.send(player, "build.preview.cancelled");
					g.close();
				}));
		gui.setSlot(36, GuiUtil.back(() -> openList(player, guild, 0)));
		gui.setSlot(44, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	private static GuiElementBuilder moveButton(ServerPlayer player, Guild guild, BuildPreview.Session session, Item icon, String axis, int dx, int dy, int dz) {
		PlacementTransform t = session.transform;
		int value = axis.equals("x") ? t.ox() : axis.equals("y") ? t.oy() : t.oz();
		return GuiUtil.item(icon, Lang.get("build.gui.adjust.move." + axis, value), GuiUtil.lines("build.gui.adjust.move.lore." + axis))
				.setCallback((slot, type, action, g) -> {
					int step = (type.shift ? 5 : 1) * (type.isRight ? -1 : 1);
					adjust(player, guild, session, session.transform.moved(dx * step, dy * step, dz * step));
				});
	}

	private static void adjust(ServerPlayer player, Guild guild, BuildPreview.Session session, PlacementTransform next) {
		if (!allowed(player, guild) || BuildPreview.session(player) != session) {
			return;
		}
		session.transform = next;
		BuildPreview.changed(player, session);
		GuiUtil.clickSound(player);
		openAdjust(player, guild);
	}

	// ================================================================ detail

	public static void openDetail(ServerPlayer player, Guild guild, int projectId) {
		BuildProject project = guild.builds.get(projectId);
		if (project == null) {
			Msg.send(player, "build.error.unknown_site");
			openList(player, guild, 0);
			return;
		}
		BuildSite site = BuildSites.site(guild, project);
		SimpleGui gui = chest(player, 6, Lang.get("build.gui.detail.title", project.name));
		gui.setSlot(4, infoCard(guild, project, site));
		if (site != null && site.ready()) {
			passIcons(gui, site);
		}
		if (!project.finished) {
			options(gui, player, guild, project);
		}
		gui.setSlot(28, GuiUtil.item(Items.CHEST, Lang.get("build.gui.materials"), materialsSummary(site)).setCallback((slot, type, action, g) -> {
			GuiUtil.clickSound(player);
			openMaterials(player, guild, projectId, 0);
		}));
		BuildSite.Counts counts = site == null ? BuildSite.Counts.EMPTY : site.counts();
		gui.setSlot(29, GuiUtil.item(counts.blocked() > 0 ? Items.TNT : Items.IRON_BARS, Lang.get("build.gui.obstacles"),
				GuiUtil.lines("build.gui.obstacles.lore", counts.blocked(), counts.left(), counts.hold(), counts.waiting()))
				.setCallback((slot, type, action, g) -> {
					GuiUtil.clickSound(player);
					openObstacles(player, guild, projectId);
				}));
		if (site != null && site.plan() != null) {
			gui.setSlot(30, GuiUtil.item(Items.SPYGLASS, Lang.get("build.gui.outline"), GuiUtil.lines("build.gui.outline.lore",
					BuildModule.config().preview.outlineSeconds)).setCallback((slot, type, action, g) -> {
				GuiUtil.clickSound(player);
				g.close();
				BuildPreview.showSite(player, site);
				Msg.actionBar(player, "build.outline.shown", project.name);
			}));
		}
		gui.setSlot(32, GuiUtil.item(Items.VILLAGER_SPAWN_EGG, Lang.get("build.gui.builders"), Builders.describe(guild, project)));
		if (!project.finished) {
			gui.setSlot(53, GuiUtil.item(Items.BARRIER, Lang.get("build.gui.cancel"), GuiUtil.lines("build.gui.cancel.lore"))
					.setCallback((slot, type, action, g) -> {
						if (!allowed(player, guild)) {
							return;
						}
						GuiUtil.confirm(player, Lang.get("build.gui.cancel.confirm", project.name), GuiUtil.lines("build.gui.cancel.lore"), () -> {
							if (allowed(player, guild)) {
								BuildActions.cancel(guild, project, player);
								openList(player, guild, 0);
							}
						}, () -> openDetail(player, guild, projectId));
					}));
		} else {
			gui.setSlot(53, GuiUtil.item(Items.LAVA_BUCKET, Lang.get("build.gui.remove"), GuiUtil.lines("build.gui.remove.lore"))
					.setCallback((slot, type, action, g) -> {
						if (!allowed(player, guild)) {
							return;
						}
						BuildActions.removeFinished(guild, project);
						GuiUtil.clickSound(player);
						openList(player, guild, 0);
					}));
		}
		gui.setSlot(45, GuiUtil.back(() -> openList(player, guild, 0)));
		gui.setSlot(49, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	private static GuiElementBuilder infoCard(Guild guild, BuildProject project, @Nullable BuildSite site) {
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("build.gui.lore.status", status(site)));
		BuildSite.Counts counts = site == null ? BuildSite.Counts.EMPTY : site.counts();
		int percent = project.finished ? 100 : site != null && site.scanned() ? counts.percent() : project.percent();
		lore.add(Lang.get("build.gui.lore.progress", progressBar(percent), percent));
		if (site != null && site.scanned()) {
			lore.add(Lang.get("build.gui.detail.cells", counts.done(), counts.total()));
		}
		lore.add(Lang.get("build.gui.detail.source", project.source));
		lore.add(Lang.get("build.gui.lore.size", project.sizeX, project.sizeY, project.sizeZ));
		int[] box = project.box();
		lore.add(Lang.get("build.gui.detail.box", box[0], box[1], box[2], box[3], box[4], box[5]));
		lore.add(Lang.get("build.gui.adjust.rotation", Lang.raw("build.rotation." + project.rotation.toLowerCase(java.util.Locale.ROOT)),
				Lang.raw("build.mirror." + project.mirror.toLowerCase(java.util.Locale.ROOT))));
		lore.add(Lang.get("build.gui.detail.created", project.createdBy));
		lore.add(Lang.get("build.gui.detail.stats", project.placed, project.cleared));
		if (site != null && site.scanned() && !project.finished) {
			long now = Scheduler.ticks();
			int workers = site.workers(now);
			int left = counts.place() + counts.free();
			if (workers > 0 && left > 0) {
				double perSecond = workers * BuildModule.config().builder.blocksPerSecond;
				long seconds = (long) Math.ceil(left / Math.max(0.1, perSecond));
				lore.add(Lang.get("build.gui.detail.eta", duration(seconds)));
			}
		}
		return GuiUtil.item(icon(project, site), Lang.get("build.gui.project.name", project.name), lore);
	}

	static String duration(long seconds) {
		if (seconds < 60) {
			return Lang.raw("build.time.seconds", seconds);
		}
		if (seconds < 3600) {
			return Lang.raw("build.time.minutes", (seconds + 59) / 60);
		}
		return Lang.raw("build.time.hours", seconds / 3600, (seconds % 3600) / 60);
	}

	private static void passIcons(SimpleGui gui, BuildSite site) {
		BuildSite.Counts counts = site.counts();
		BuildPasses.Fronts fronts = site.fronts();
		Item[] icons = {Items.IRON_SHOVEL, Items.BRICKS, Items.LANTERN, Items.WATER_BUCKET};
		BuildPasses.Pass[] passes = BuildPasses.Pass.values();
		for (int i = 0; i < passes.length; i++) {
			BuildPasses.Pass pass = passes[i];
			int done = counts.passDone()[i];
			int total = counts.passTotal()[i];
			List<Component> lore = new ArrayList<>(GuiUtil.lines("build.pass." + pass.id() + ".desc"));
			lore.add(Component.empty());
			int percent = total <= 0 ? 100 : (int) Math.floor(done * 100.0 / total);
			lore.add(Lang.get("build.gui.lore.progress", progressBar(percent), percent));
			lore.add(Lang.get("build.gui.pass.cells", done, total));
			String front = switch (pass) {
				case SOLID -> fronts.solidDone() ? Lang.raw("build.gui.pass.front.done") : Lang.raw("build.gui.pass.front.layer",
						site.heightOf(fronts.solidLow()) + 1, site.sizeY());
				case CLEAR -> fronts.clearDone() ? Lang.raw("build.gui.pass.front.done") : Lang.raw("build.gui.pass.front.layer",
						site.heightOf(fronts.clearTop()) + 1, site.sizeY());
				case ATTACHED -> fronts.solidDone() ? Lang.raw("build.gui.pass.front.all") : Lang.raw("build.gui.pass.front.below",
						site.heightOf(fronts.solidLow()) + 1);
				case FLUID -> fronts.allows(BuildPasses.Pass.FLUID, 0) ? Lang.raw("build.gui.pass.front.all") : Lang.raw("build.gui.pass.front.last");
			};
			lore.add(Lang.get("build.gui.pass.front", front));
			GuiElementBuilder icon = GuiUtil.item(icons[i], Lang.get("build.pass." + pass.id()), lore);
			if (total > 0 && done >= total) {
				icon.glow();
			}
			gui.setSlot(10 + i * 2, icon);
		}
	}

	private static void options(SimpleGui gui, ServerPlayer player, Guild guild, BuildProject project) {
		int id = project.id;
		BuildProject.Sources sources = project.sourcesMode();
		gui.setSlot(19, GuiUtil.item(sources == BuildProject.Sources.ALL ? Items.BARREL : Items.CHEST,
				Lang.get("build.gui.option.sources", Lang.raw("build.sources." + sources.id())), GuiUtil.lines("build.gui.option.sources.lore"))
				.setCallback((slot, type, action, g) -> option(player, guild, id, () -> BuildActions.setSources(guild, project, sources.next()))));
		InteriorRules.Mode mode = project.interiorMode();
		gui.setSlot(20, GuiUtil.item(switch (mode) {
					case KEEP -> Items.GRASS_BLOCK;
					case HOLLOW -> Items.IRON_PICKAXE;
					case BOX -> Items.TNT;
				}, Lang.get("build.gui.option.interior", Lang.raw("build.interior." + mode.id())), GuiUtil.lines("build.gui.option.interior.lore"))
				.setCallback((slot, type, action, g) -> option(player, guild, id, () -> BuildActions.setInterior(guild, project, mode.next()))));
		gui.setSlot(21, toggle(Items.STONE_BRICKS, "build.gui.option.replace", project.replaceBuilt)
				.setCallback((slot, type, action, g) -> option(player, guild, id, () -> BuildActions.setReplaceBuilt(guild, project, !project.replaceBuilt))));
		gui.setSlot(22, toggle(Items.COBBLESTONE_WALL, "build.gui.option.foundations", project.foundations, BuildModule.config().foundations.maxDepth)
				.setCallback((slot, type, action, g) -> option(player, guild, id, () -> BuildActions.setFoundations(guild, project, !project.foundations))));
		gui.setSlot(23, GuiUtil.item(Items.BELL, Lang.get("build.gui.option.priority", signed(project.priority)), GuiUtil.lines("build.gui.option.priority.lore"))
				.setCallback((slot, type, action, g) -> option(player, guild, id, () -> BuildActions.setPriority(guild, project,
						project.priority + (type.isRight ? -1 : 1)))));
		gui.setSlot(24, GuiUtil.item(project.paused ? Items.DYE.pick(DyeColor.LIME) : Items.DYE.pick(DyeColor.GRAY),
				Lang.get(project.paused ? "build.gui.option.resume" : "build.gui.option.pause"), GuiUtil.lines(project.paused
						? "build.gui.option.resume.lore" : "build.gui.option.pause.lore"))
				.setCallback((slot, type, action, g) -> option(player, guild, id, () -> BuildActions.setPaused(guild, project, !project.paused))));
		gui.setSlot(25, GuiUtil.item(Items.NAME_TAG, Lang.get("build.gui.option.rename"), GuiUtil.lines("build.gui.option.rename.lore"))
				.setCallback((slot, type, action, g) -> {
					if (!allowed(player, guild)) {
						return;
					}
					GuiUtil.textInput(player, Lang.get("build.gui.option.rename"), project.name, text -> {
						if (allowed(player, guild)) {
							BuildActions.rename(player, guild, project, text);
						}
						openDetail(player, guild, id);
					}, () -> openDetail(player, guild, id));
				}));
	}

	private static GuiElementBuilder toggle(Item item, String key, boolean on, Object... args) {
		List<Component> lore = new ArrayList<>(GuiUtil.lines(key + ".lore", args));
		lore.add(Component.empty());
		lore.add(Lang.get(on ? "build.gui.option.on" : "build.gui.option.off"));
		GuiElementBuilder builder = GuiUtil.item(item, Lang.get(key), lore);
		if (on) {
			builder.glow();
		}
		return builder;
	}

	private static void option(ServerPlayer player, Guild guild, int projectId, Runnable change) {
		if (!allowed(player, guild)) {
			return;
		}
		change.run();
		GuildManager.markDirty();
		GuiUtil.clickSound(player);
		openDetail(player, guild, projectId);
	}

	private static List<Component> materialsSummary(@Nullable BuildSite site) {
		List<Component> lore = new ArrayList<>();
		if (site == null || !site.scanned()) {
			lore.add(Lang.get("build.gui.materials.scanning"));
			return lore;
		}
		long now = Scheduler.ticks();
		Map<Item, Integer> remaining = site.remainingMaterials();
		Map<Item, Integer> missing = site.missing(now);
		int remainingCount = remaining.values().stream().mapToInt(Integer::intValue).sum();
		int missingCount = missing.values().stream().mapToInt(Integer::intValue).sum();
		lore.add(Lang.get("build.gui.materials.remaining", remainingCount, remaining.size()));
		lore.add(missingCount > 0 ? Lang.get("build.gui.materials.missing", missingCount, missing.size()) : Lang.get("build.gui.materials.all_here"));
		List<Map.Entry<Item, Integer>> top = new ArrayList<>(missing.entrySet());
		top.sort((a, b) -> b.getValue() - a.getValue());
		for (int i = 0; i < Math.min(5, top.size()); i++) {
			lore.add(withName(Lang.get("build.gui.materials.missing.line", top.get(i).getValue()), ItemUtil.name(top.get(i).getKey()),
					ChatFormatting.GRAY));
		}
		lore.add(Component.empty());
		lore.add(Lang.get("build.gui.materials.click"));
		return lore;
	}

	// ================================================================ materials

	public static void openMaterials(ServerPlayer player, Guild guild, int projectId, int page) {
		BuildProject project = guild.builds.get(projectId);
		if (project == null) {
			openList(player, guild, 0);
			return;
		}
		BuildSite site = BuildSites.site(guild, project);
		SimpleGui gui = chest(player, 6, Lang.get("build.gui.materials.title", project.name));
		if (site == null || !site.scanned()) {
			gui.setSlot(22, GuiUtil.item(Items.CLOCK, Lang.get("build.gui.materials.scanning"), List.of()));
		} else {
			long now = Scheduler.ticks();
			Map<Item, Integer> bill = site.billOfMaterials();
			Map<Item, Integer> remaining = site.remainingMaterials();
			Map<Item, Integer> stock = site.stock();
			Map<Item, Integer> carried = site.carried(now);
			Map<Item, Integer> missing = site.missing(now);
			Map<Item, Integer> fetching = Procurement.fetching(site.guild);
			Map<Item, Integer> all = new LinkedHashMap<>(bill);
			remaining.forEach((item, count) -> all.putIfAbsent(item, count));
			List<Item> items = new ArrayList<>(all.keySet());
			items.sort(Comparator.comparingInt((Item item) -> -missing.getOrDefault(item, 0))
					.thenComparingInt(item -> -remaining.getOrDefault(item, 0))
					.thenComparing(BuildMenus::itemName));
			int pages = GuiUtil.pages(items.size() + 1, PER_PAGE);
			int current = Math.max(0, Math.min(page, pages - 1));
			for (int i = 0; i < PER_PAGE; i++) {
				int index = current * PER_PAGE + i;
				if (index >= items.size()) {
					break;
				}
				Item item = items.get(index);
				int needed = bill.getOrDefault(item, remaining.getOrDefault(item, 0));
				int left = remaining.getOrDefault(item, 0);
				int lack = missing.getOrDefault(item, 0);
				List<Component> lore = new ArrayList<>();
				lore.add(Lang.get("build.gui.material.needed", needed));
				lore.add(Lang.get("build.gui.material.placed", Math.max(0, needed - left)));
				lore.add(Lang.get("build.gui.material.remaining", left));
				lore.add(Lang.get("build.gui.material.available", stock.getOrDefault(item, 0), carried.getOrDefault(item, 0)));
				lore.add(lack > 0 ? Lang.get("build.gui.material.missing", lack) : Lang.get("build.gui.material.ok"));
				int fetched = lack > 0 ? fetching.getOrDefault(item, 0) : 0;
				if (fetched > 0) {
					lore.add(Lang.get("build.gui.material.fetching", fetched));
				}
				ItemStack icon = new ItemStack(item, Math.max(1, Math.min(item.getDefaultMaxStackSize(), Math.max(1, lack > 0 ? lack : left))));
				GuiElementBuilder button = GuiUtil.item(icon, withName(Component.empty(), ItemUtil.name(item),
						lack > 0 ? ChatFormatting.RED : ChatFormatting.WHITE), lore);
				gui.setSlot(i, button);
			}
			BuildPlan plan = site.plan();
			if (plan != null && plan.notBuildableCells() > 0) {
				List<Component> lore = new ArrayList<>(GuiUtil.lines("build.gui.not_buildable.lore", plan.notBuildableCells()));
				int shown = 0;
				for (int p = 1; p < plan.paletteSize() && shown < 8; p++) {
					if (plan.kind(p) == MaterialRules.Kind.NOT_BUILDABLE && plan.uses(p) > 0) {
						lore.add(Lang.get("build.gui.not_buildable.line", plan.uses(p), plan.data.palette.get(p),
								Lang.raw("build.reason." + reasonKey(plan.reason(p)))));
						shown++;
					}
				}
				gui.setSlot(47, GuiUtil.item(Items.STRUCTURE_VOID, Lang.get("build.gui.not_buildable"), lore));
			}
			if (current > 0) {
				gui.setSlot(46, GuiUtil.item(Items.ARROW, Lang.get("gui.page.prev"), List.of(Lang.get("gui.page", current + 1, pages)))
						.setCallback((slot, type, action, g) -> openMaterials(player, guild, projectId, current - 1)));
			}
			if (current < pages - 1) {
				gui.setSlot(52, GuiUtil.item(Items.ARROW, Lang.get("gui.page.next"), List.of(Lang.get("gui.page", current + 1, pages)))
						.setCallback((slot, type, action, g) -> openMaterials(player, guild, projectId, current + 1)));
			}
			gui.setSlot(49, GuiUtil.item(project.sourcesMode() == BuildProject.Sources.ALL ? Items.BARREL : Items.CHEST,
					Lang.get("build.gui.materials.sources", Lang.raw("build.sources." + project.sourcesMode().id())),
					GuiUtil.lines("build.gui.materials.sources.lore")));
		}
		gui.setSlot(45, GuiUtil.back(() -> openDetail(player, guild, projectId)));
		gui.setSlot(53, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	private static String reasonKey(@Nullable String reason) {
		return "unknown".equals(reason) || "no_item".equals(reason) ? reason : "forbidden";
	}

	// ================================================================ obstacles

	public static void openObstacles(ServerPlayer player, Guild guild, int projectId) {
		BuildProject project = guild.builds.get(projectId);
		if (project == null) {
			openList(player, guild, 0);
			return;
		}
		BuildSite site = BuildSites.site(guild, project);
		SimpleGui gui = chest(player, 6, Lang.get("build.gui.obstacles.title", project.name));
		ServerLevel level = GuildyServer.claimLevel();
		int slot = 0;
		if (site != null && site.scanned()) {
			BuildSite.Counts counts = site.counts();
			slot = obstacleSection(gui, level, slot, counts.blockedAt(), counts.blocked(), "build.gui.obstacle.blocked");
			slot = obstacleSection(gui, level, slot, counts.leftAt(), counts.left(), "build.gui.obstacle.left");
			slot = obstacleSection(gui, level, slot, counts.holdAt(), counts.hold(), "build.gui.obstacle.hold");
			obstacleSection(gui, level, slot, counts.waitAt(), counts.waiting(), "build.gui.obstacle.wait");
		}
		if (gui.getGuiElement(0) == null) {
			gui.setSlot(22, GuiUtil.item(Items.DYE.pick(DyeColor.LIME), Lang.get("build.gui.obstacles.none"), GuiUtil.lines("build.gui.obstacles.none.lore")));
		}
		gui.setSlot(45, GuiUtil.back(() -> openDetail(player, guild, projectId)));
		gui.setSlot(49, GuiUtil.item(Items.BOOK, Lang.get("build.gui.obstacles.help"), GuiUtil.lines("build.gui.obstacles.help.lore")));
		gui.setSlot(53, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	private static int obstacleSection(SimpleGui gui, @Nullable ServerLevel level, int slot, List<BlockPos> positions, int total, String key) {
		int shown = 0;
		for (BlockPos pos : positions) {
			if (slot >= PER_PAGE) {
				return slot;
			}
			BlockState state = level == null || !level.isLoaded(pos) ? null : level.getBlockState(pos);
			Item item = state == null ? Items.PAPER : state.getBlock().asItem();
			if (item == Items.AIR) {
				item = Items.PAPER;
			}
			Component name = state == null ? Component.literal("?") : state.getBlock().getName();
			List<Component> lore = new ArrayList<>(GuiUtil.lines(key + ".lore"));
			lore.add(Lang.get("build.gui.obstacle.at", pos.getX(), pos.getY(), pos.getZ()));
			if (shown == positions.size() - 1 && total > positions.size()) {
				lore.add(Lang.get("build.gui.obstacle.more", total - positions.size()));
			}
			gui.setSlot(slot++, GuiUtil.item(item, withName(Lang.get(key), name, ChatFormatting.WHITE), lore));
			shown++;
		}
		return slot;
	}
}
