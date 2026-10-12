package dev.guildy.villager;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.gui.GuiUtil;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Menus of the work zones and patrol routes: the list, zone creation (fanion or "here"), zone settings (radius,
 * objective, name, display, villagers, move, delete), route settings, and the zone picker of a villager.
 */
public final class ZoneGui {
	/** Zone / route names (also shown on the fanion banner). */
	static final int MAX_NAME = 24;
	private static final int[] OBJECTIVE_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

	private ZoneGui() {
	}

	private static VillagerConfig config() {
		return VillagerModule.config();
	}

	// ================================================================ list

	public static void openZones(ServerPlayer player, Guild guild) {
		if (!guild.isMember(player.getUUID())) {
			Msg.send(player, "error.no_guild");
			return;
		}
		ServerLevel level = GuildyServer.claimLevel();
		GuildVillagerData data = guild.villagers;
		VillagerConfig config = config();
		SimpleGui gui = VillagerGui.chest(player, 6, Lang.get("villager.gui.zones.title", data.zones.size(), config.zones.maxZones,
				data.routes.size(), config.routes.maxRoutes));
		int slot = 0;
		for (WorkZone zone : data.zones) {
			if (slot >= 27) {
				break;
			}
			int id = zone.id;
			gui.setSlot(slot++, zoneIcon(level, guild, zone, true).setCallback((s, type, action, g) -> {
				GuiUtil.clickSound(player);
				openZone(player, guild, id);
			}));
		}
		slot = 27;
		for (PatrolRoute route : data.routes) {
			if (slot >= 36) {
				break;
			}
			int id = route.id;
			gui.setSlot(slot++, routeIcon(guild, route, true).setCallback((s, type, action, g) -> {
				GuiUtil.clickSound(player);
				openRoute(player, guild, id);
			}));
		}
		if (data.zones.isEmpty() && data.routes.isEmpty()) {
			gui.setSlot(13, GuiUtil.item(Items.MAP, Lang.get("villager.gui.zones.empty"), GuiUtil.lines("villager.gui.zones.empty.lore")));
		}
		gui.setSlot(38, GuiUtil.item(ZoneFlags.icon(ZoneType.FOREST), Lang.get("villager.gui.zones.create"),
				GuiUtil.lines("villager.gui.zones.create.lore", data.zones.size(), config.zones.maxZones)).glow().setCallback((s, type, action, g) -> {
			if (!VillagerGui.checkPermission(player, guild)) {
				return;
			}
			GuiUtil.clickSound(player);
			openTypePicker(player, guild);
		}));
		gui.setSlot(40, GuiUtil.item(Items.LEAD, Lang.get("villager.gui.zones.new_route"), GuiUtil.lines("villager.gui.zones.new_route.lore",
				data.routes.size(), config.routes.maxRoutes, config.routes.maxWaypoints)).setCallback((s, type, action, g) -> {
			if (!VillagerGui.checkPermission(player, guild)) {
				return;
			}
			String error = Zones.canCreateRoute(guild);
			if (error != null) {
				Msg.send(player, error, config.routes.maxRoutes);
				GuiUtil.errorSound(player);
				return;
			}
			g.close();
			RouteSelection.start(player, guild, null);
		}));
		gui.setSlot(42, GuiUtil.item(Items.BOOK, Lang.get("villager.gui.help"),
				GuiUtil.lines("villager.work.zones.help.lore", config.zones.maxZoneDistance, config.zones.autoSearchRadius)));
		gui.setSlot(45, GuiUtil.back(() -> VillagerGui.openList(player, guild)));
		gui.setSlot(53, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	/** Zone summary: type, objective, position, size / depth, villagers, fanion state. */
	static GuiElementBuilder zoneIcon(@Nullable ServerLevel level, Guild guild, WorkZone zone, boolean manageHint) {
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("villager.gui.zone.lore.type", Lang.raw(zone.type.langKey())));
		if (zone.type != ZoneType.PATROL) {
			lore.add(Lang.get("villager.gui.zone.lore.objective", Lang.raw(zone.objective.langKey())));
		}
		BlockPos pos = zone.pos;
		boolean outside = level != null && GuildManager.at(level, pos) != guild;
		lore.add(Lang.get(outside ? "villager.gui.zone.lore.pos_outside" : "villager.gui.zone.lore.pos", pos.getX(), pos.getY(), pos.getZ()));
		if (zone.type == ZoneType.MINE) {
			lore.add(Lang.get("villager.gui.zone.lore.mode", Lang.raw(zone.mine().mode.langKey())));
		}
		if (zone.usesRadius()) {
			lore.add(Lang.get("villager.gui.zone.lore.size", zone.sizeChunks(), zone.effectiveRadius(), zone.sizeChunks() * 16));
		} else {
			lore.add(Lang.get("villager.gui.zone.lore.depth", pos.getY()));
		}
		if (zone.isRealMine()) {
			lore.add(Lang.get("villager.gui.zone.lore.target", Mines.shownTarget(level, zone)));
		}
		lore.add(Lang.get("villager.gui.zone.lore.villagers", Zones.assignedCount(guild, zone)));
		Component wishes = WishlistMenu.summary(zone);
		if (wishes != null) {
			lore.add(wishes);
		}
		lore.add(Lang.get(switch (Zones.fanionState(level, zone)) {
			case PRESENT -> "villager.gui.zone.lore.fanion.present";
			case ABSENT -> "villager.gui.zone.lore.fanion.absent";
			case UNLOADED -> "villager.gui.zone.lore.fanion.unloaded";
			case NONE -> "villager.gui.zone.lore.fanion.none";
		}));
		if (manageHint) {
			lore.add(Lang.get("villager.gui.lore.manage"));
		}
		return GuiUtil.item(ZoneFlags.icon(zone.type), Lang.get("villager.gui.zone.name", zone.name), lore);
	}

	static GuiElementBuilder routeIcon(Guild guild, PatrolRoute route, boolean manageHint) {
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("villager.gui.route.lore.points", route.points.size(), route.length()));
		lore.add(Lang.get("villager.gui.route.lore.mode", Lang.raw(route.pingPong ? "villager.route.mode.pingpong" : "villager.route.mode.loop")));
		if (!route.points.isEmpty()) {
			BlockPos first = route.points.getFirst();
			lore.add(Lang.get("villager.gui.route.lore.start", first.getX(), first.getY(), first.getZ()));
		}
		lore.add(Lang.get("villager.gui.route.lore.guards", Zones.assignedCount(guild, route)));
		if (manageHint) {
			lore.add(Lang.get("villager.gui.lore.manage"));
		}
		return GuiUtil.item(Items.FILLED_MAP, Lang.get("villager.gui.route.name", route.name), lore);
	}

	// ================================================================ creation

	static void openTypePicker(ServerPlayer player, Guild guild) {
		SimpleGui gui = VillagerGui.chest(player, 3, Lang.get("villager.gui.zone_type.title"));
		int[] slots = {10, 12, 14, 16};
		ZoneType[] types = ZoneType.values();
		for (int i = 0; i < types.length && i < slots.length; i++) {
			ZoneType type = types[i];
			List<Component> lore = new ArrayList<>(GuiUtil.lines(type.descKey()));
			lore.add(Component.empty());
			lore.addAll(GuiUtil.lines("villager.gui.zone_type.click"));
			gui.setSlot(slots[i], GuiUtil.item(ZoneFlags.icon(type), Lang.get("villager.gui.zone_type.name", Lang.raw(type.langKey())), lore)
					.setCallback((s, click, action, g) -> {
						if (!VillagerGui.checkPermission(player, guild)) {
							return;
						}
						if (click.isRight) {
							createHere(player, guild, type);
						} else {
							g.close();
							ZoneFlags.give(player, guild, type, null);
						}
					}));
		}
		gui.setSlot(18, GuiUtil.back(() -> openZones(player, guild)));
		gui.setSlot(26, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	/** "Créer la zone ici": a zone at the player's feet, without a fanion (same rules). */
	private static void createHere(ServerPlayer player, Guild guild, ZoneType type) {
		BlockPos pos = player.blockPosition();
		String error = Zones.canCreateZone(guild);
		Object arg = config().zones.maxZones;
		if (error == null) {
			error = Zones.checkPlacement(player.level(), guild, pos);
			arg = config().zones.maxZoneDistance;
		}
		if (error == null && Zones.fanionAt(pos) != null) {
			error = "villager.zone.error.occupied";
		}
		if (error != null) {
			Msg.send(player, error, arg);
			GuiUtil.errorSound(player);
			return;
		}
		WorkZone zone = Zones.createZone(guild, type, pos, Zones.defaultZoneName(guild, type), false);
		Msg.guild(guild, "villager.zone.created", player.getName().getString(), zone.name, Lang.raw(type.langKey()), pos.getX(), pos.getY(), pos.getZ());
		GuiUtil.successSound(player);
		openZone(player, guild, zone.id);
	}

	// ================================================================ zone

	public static void openZone(ServerPlayer player, Guild guild, int zoneId) {
		WorkZone zone = guild.villagers.zone(zoneId);
		if (zone == null) {
			Msg.send(player, "villager.zone.error.unknown");
			openZones(player, guild);
			return;
		}
		ServerLevel level = GuildyServer.claimLevel();
		VillagerConfig config = config();
		boolean mine = zone.type == ZoneType.MINE;
		int rows = mine ? 5 : 4;
		SimpleGui gui = VillagerGui.chest(player, rows, Lang.get("villager.gui.zone.title", zone.name));
		gui.setSlot(4, zoneIcon(level, guild, zone, false));

		if (zone.usesRadius()) {
			gui.setSlot(10, GuiUtil.item(Items.CONCRETE.pick(DyeColor.RED), Lang.get("villager.gui.zone.radius.minus"),
					GuiUtil.lines("villager.gui.zone.radius.lore")).setCallback((s, type, action, g) -> changeRadius(player, guild, zone, -1)));
			gui.setSlot(11, GuiUtil.item(Items.MAP, Lang.get("villager.gui.zone.radius", zone.effectiveRadius(), zone.sizeChunks()),
					GuiUtil.lines("villager.gui.zone.radius.info", zone.sizeChunks() * 16, config.zones.maxRadiusChunks)));
			gui.setSlot(12, GuiUtil.item(Items.CONCRETE.pick(DyeColor.LIME), Lang.get("villager.gui.zone.radius.plus"),
					GuiUtil.lines("villager.gui.zone.radius.lore")).setCallback((s, type, action, g) -> changeRadius(player, guild, zone, 1)));
		} else {
			gui.setSlot(11, GuiUtil.item(Items.DEEPSLATE, Lang.get("villager.gui.zone.depth", zone.pos.getY()), GuiUtil.lines("villager.gui.zone.depth.lore")));
		}

		if (zone.type == ZoneType.PATROL) {
			gui.setSlot(14, GuiUtil.item(Items.IRON_SWORD, Lang.get("villager.gui.zone.patrol"),
					GuiUtil.lines("villager.gui.zone.patrol.lore", config.guard.zoneMargin, config.routes.pauseSeconds)));
		} else {
			List<Component> lore = new ArrayList<>(GuiUtil.lines(zone.objective.descKey()));
			lore.add(Component.empty());
			lore.add(Lang.get("villager.gui.zone.objective.click"));
			gui.setSlot(14, GuiUtil.item(icon(zone.objective), Lang.get("villager.gui.zone.objective", Lang.raw(zone.objective.langKey())), lore)
					.setCallback((s, type, action, g) -> {
						if (!VillagerGui.checkPermission(player, guild) || !stillZone(player, guild, zone)) {
							return;
						}
						GuiUtil.clickSound(player);
						openObjectives(player, guild, zone.id);
					}));
		}

		if (WishlistMenu.supports(zone.type)) {
			gui.setSlot(15, WishlistMenu.zoneButton(player, guild, zone));
		}
		gui.setSlot(16, GuiUtil.item(Items.NAME_TAG, Lang.get("villager.gui.zone.rename"), GuiUtil.lines("villager.gui.zone.rename.lore"))
				.setCallback((s, type, action, g) -> {
					if (!VillagerGui.checkPermission(player, guild) || !stillZone(player, guild, zone)) {
						return;
					}
					GuiUtil.textInput(player, Lang.get("villager.gui.zone.rename.title"), zone.name, input -> {
						if (!VillagerGui.checkPermission(player, guild) || !stillZone(player, guild, zone)) {
							return;
						}
						String name = ZoneMath.cleanName(input, MAX_NAME);
						if (name.isEmpty()) {
							Msg.send(player, "villager.zone.error.name", MAX_NAME);
							GuiUtil.errorSound(player);
						} else {
							Zones.rename(GuildyServer.claimLevel(), zone, name);
							Msg.send(player, "villager.zone.renamed", name);
							GuiUtil.successSound(player);
						}
						openZone(player, guild, zone.id);
					}, () -> openZone(player, guild, zone.id));
				}));

		gui.setSlot(19, GuiUtil.item(Items.ENDER_EYE, Lang.get("villager.gui.zone.show"), GuiUtil.lines("villager.gui.zone.show.lore", config.zones.showSeconds))
				.setCallback((s, type, action, g) -> {
					if (!stillZone(player, guild, zone)) {
						return;
					}
					g.close();
					ZoneVisualizer.showZone(player, guild, zone);
					Msg.send(player, "villager.zone.shown", zone.name, config.zones.showSeconds);
					GuiUtil.clickSound(player);
				}));
		Task task = zone.type.task();
		gui.setSlot(21, GuiUtil.item(Items.VILLAGER_SPAWN_EGG, Lang.get("villager.gui.zone.villagers", Zones.assignedCount(guild, zone)),
				GuiUtil.lines("villager.gui.zone.villagers.lore", Lang.raw(task.langKey()))).setCallback((s, type, action, g) -> {
			if (!stillZone(player, guild, zone)) {
				return;
			}
			GuiUtil.clickSound(player);
			openAssignees(player, guild, Lang.get("villager.gui.assignees.title", zone.name), task, zone.name,
					record -> Zones.assignedZone(guild, record) == zone,
					record -> Zones.assign(record, zone),
					record -> Zones.assign(record, null),
					() -> stillZone(player, guild, zone),
					() -> openZone(player, guild, zone.id),
					record -> Mines.refuseAssign(player, guild, zone, record));
		}));
		gui.setSlot(23, GuiUtil.item(ZoneFlags.icon(zone.type), Lang.get(zone.fanion ? "villager.gui.zone.move" : "villager.gui.zone.plant"),
				GuiUtil.lines(zone.fanion ? "villager.gui.zone.move.lore" : "villager.gui.zone.plant.lore"))
				.setCallback((s, type, action, g) -> {
					if (!VillagerGui.checkPermission(player, guild) || !stillZone(player, guild, zone)) {
						return;
					}
					g.close();
					ZoneFlags.give(player, guild, zone.type, zone);
				}));
		gui.setSlot(25, GuiUtil.item(Items.TNT, Lang.get("villager.gui.zone.delete"), GuiUtil.lines("villager.gui.zone.delete.lore"))
				.setCallback((s, type, action, g) -> {
					if (!VillagerGui.checkPermission(player, guild) || !stillZone(player, guild, zone)) {
						return;
					}
					GuiUtil.confirm(player, Lang.get("villager.gui.zone.delete.confirm", zone.name), GuiUtil.lines("villager.gui.zone.delete.lore"),
							() -> {
								if (VillagerGui.checkPermission(player, guild) && guild.villagers.zones.contains(zone)) {
									Zones.deleteZone(GuildyServer.claimLevel(), guild, zone);
									Msg.guild(guild, "villager.zone.deleted", player.getName().getString(), zone.name);
								}
								openZones(player, guild);
							},
							() -> openZone(player, guild, zone.id));
				}));
		if (mine) {
			mineControls(gui, player, guild, zone, level);
		}
		gui.setSlot(rows * 9 - 9, GuiUtil.back(() -> openZones(player, guild)));
		gui.setSlot(rows * 9 - 1, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	/** Mines (row 4): mode, then for real mines the target depth −/+ and the excavation (progress, reset). */
	private static void mineControls(SimpleGui gui, ServerPlayer player, Guild guild, WorkZone zone, @Nullable ServerLevel level) {
		MineState state = zone.mine();
		boolean real = state.mode == MineMode.REAL;
		List<Component> modeLore = new ArrayList<>(GuiUtil.lines(real ? "villager.gui.mine.mode.real.lore" : "villager.gui.mine.mode.simulated.lore"));
		modeLore.add(Component.empty());
		modeLore.add(Lang.get("villager.gui.mine.mode.click"));
		gui.setSlot(28, GuiUtil.item(real ? Items.DIAMOND_PICKAXE : Items.STONE_PICKAXE, Lang.get("villager.gui.mine.mode", Lang.raw(state.mode.langKey())),
				modeLore).setCallback((s, type, action, g) -> {
			if (!VillagerGui.checkPermission(player, guild) || !stillZone(player, guild, zone)) {
				return;
			}
			MineMode mode = zone.mine().mode.next();
			Zones.setMineMode(guild, zone, mode);
			Msg.send(player, "villager.mine.mode_set", zone.name, Lang.raw(mode.langKey()));
			GuiUtil.clickSound(player);
			openZone(player, guild, zone.id);
		}));
		if (!real) {
			return;
		}
		VillagerConfig.RealMining settings = config().realMining;
		int step = Mines.spacing(zone, settings);
		gui.setSlot(30, GuiUtil.item(Items.CONCRETE.pick(DyeColor.RED), Lang.get("villager.gui.mine.deeper", step),
				GuiUtil.lines("villager.gui.mine.depth.lore")).setCallback((s, type, action, g) -> changeTarget(player, guild, zone, -step)));
		int target = Mines.shownTarget(level, zone);
		List<Component> targetLore = new ArrayList<>(level == null || state.targetY == null ? GuiUtil.lines("villager.gui.mine.target.default")
				: GuiUtil.lines("villager.gui.mine.target.custom",
				MineRules.clampLevel(Integer.MIN_VALUE / 2, zone.pos.getY(), level.getMinY(), settings.minDepthBelowFanion),
				MineRules.clampLevel(Integer.MAX_VALUE / 2, zone.pos.getY(), level.getMinY(), settings.minDepthBelowFanion)));
		if (settings.autoNextLevel) {
			targetLore.addAll(GuiUtil.lines("villager.gui.mine.target.next", step));
		}
		gui.setSlot(31, GuiUtil.item(Items.DEEPSLATE, Lang.get("villager.gui.mine.target", target), targetLore));
		gui.setSlot(32, GuiUtil.item(Items.CONCRETE.pick(DyeColor.LIME), Lang.get("villager.gui.mine.higher", step),
				GuiUtil.lines("villager.gui.mine.depth.lore")).setCallback((s, type, action, g) -> changeTarget(player, guild, zone, step)));
		List<Component> info = new ArrayList<>(Mines.describe(level, guild, zone, Scheduler.ticks()));
		info.add(Component.empty());
		info.addAll(GuiUtil.lines("villager.gui.mine.view.click"));
		gui.setSlot(34, GuiUtil.item(Items.IRON_PICKAXE, Lang.get("villager.gui.mine.info"), info).setCallback((s, type, action, g) -> {
			if (!stillZone(player, guild, zone)) {
				return;
			}
			GuiUtil.clickSound(player);
			MineGui.open(player, guild, zone.id, 0);
		}));
	}

	/** Target depth of a real mine ± one level, kept in the allowed range. */
	private static void changeTarget(ServerPlayer player, Guild guild, WorkZone zone, int delta) {
		if (!VillagerGui.checkPermission(player, guild) || !stillZone(player, guild, zone)) {
			return;
		}
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			GuiUtil.errorSound(player);
			return;
		}
		VillagerConfig.RealMining settings = config().realMining;
		int current = Mines.targetLevel(level, zone, settings);
		int wanted = MineRules.clampLevel(current + delta, zone.pos.getY(), level.getMinY(), settings.minDepthBelowFanion);
		if (wanted == current) {
			GuiUtil.errorSound(player);
			return;
		}
		Zones.setMineTarget(guild, zone, wanted);
		Msg.send(player, "villager.mine.target_set", zone.name, wanted);
		GuiUtil.clickSound(player);
		openZone(player, guild, zone.id);
	}

	private static void changeRadius(ServerPlayer player, Guild guild, WorkZone zone, int delta) {
		if (!VillagerGui.checkPermission(player, guild) || !stillZone(player, guild, zone)) {
			return;
		}
		int max = config().zones.maxRadiusChunks;
		// "−" always shrinks into the allowed range (the maximum may have been lowered since).
		int radius = delta < 0 ? Math.min(zone.radius - 1, max) : zone.radius + 1;
		if (radius < 0 || radius > max || radius == zone.radius) {
			GuiUtil.errorSound(player);
			return;
		}
		Zones.setRadius(guild, zone, radius);
		GuiUtil.clickSound(player);
		openZone(player, guild, zone.id);
	}

	static void openObjectives(ServerPlayer player, Guild guild, int zoneId) {
		WorkZone zone = guild.villagers.zone(zoneId);
		if (zone == null) {
			Msg.send(player, "villager.zone.error.unknown");
			openZones(player, guild);
			return;
		}
		SimpleGui gui = VillagerGui.chest(player, 4, Lang.get("villager.gui.objective.title", zone.name));
		List<ZoneObjective> objectives = ZoneObjective.forType(zone.type);
		for (int i = 0; i < objectives.size() && i < OBJECTIVE_SLOTS.length; i++) {
			ZoneObjective objective = objectives.get(i);
			boolean current = zone.objective == objective;
			List<Component> lore = new ArrayList<>(GuiUtil.lines(objective.descKey()));
			lore.add(Component.empty());
			lore.add(Lang.get(current ? "villager.gui.objective.current" : "villager.gui.objective.choose"));
			GuiElementBuilder button = GuiUtil.item(icon(objective), Lang.get("villager.gui.objective.name", Lang.raw(objective.langKey())), lore);
			if (current) {
				button.glow();
			}
			gui.setSlot(OBJECTIVE_SLOTS[i], button.setCallback((s, type, action, g) -> {
				if (!VillagerGui.checkPermission(player, guild) || !stillZone(player, guild, zone)) {
					return;
				}
				if (zone.objective != objective) {
					Zones.setObjective(guild, zone, objective);
					Msg.send(player, "villager.zone.objective_set", zone.name, Lang.raw(objective.langKey()));
				}
				GuiUtil.successSound(player);
				openZone(player, guild, zone.id);
			}));
		}
		gui.setSlot(27, GuiUtil.back(() -> openZone(player, guild, zone.id)));
		gui.setSlot(35, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	private static Item icon(ZoneObjective objective) {
		Item item = ItemUtil.item(objective.icon());
		return item != null ? item : Items.BARRIER;
	}

	private static boolean stillZone(ServerPlayer player, Guild guild, WorkZone zone) {
		if (!guild.villagers.zones.contains(zone)) {
			Msg.send(player, "villager.zone.error.unknown");
			player.closeContainer();
			return false;
		}
		return true;
	}

	// ================================================================ route

	public static void openRoute(ServerPlayer player, Guild guild, int routeId) {
		PatrolRoute route = guild.villagers.route(routeId);
		if (route == null) {
			Msg.send(player, "villager.route.error.unknown");
			openZones(player, guild);
			return;
		}
		VillagerConfig config = config();
		SimpleGui gui = VillagerGui.chest(player, 4, Lang.get("villager.gui.route.title", route.name));
		gui.setSlot(4, routeIcon(guild, route, false));
		gui.setSlot(10, GuiUtil.item(Items.ENDER_EYE, Lang.get("villager.gui.route.show"), GuiUtil.lines("villager.gui.route.show.lore", config.zones.showSeconds))
				.setCallback((s, type, action, g) -> {
					if (!stillRoute(player, guild, route)) {
						return;
					}
					g.close();
					ZoneVisualizer.showRoute(player, guild, route);
					Msg.send(player, "villager.zone.shown", route.name, config.zones.showSeconds);
					GuiUtil.clickSound(player);
				}));
		String mode = Lang.raw(route.pingPong ? "villager.route.mode.pingpong" : "villager.route.mode.loop");
		gui.setSlot(12, GuiUtil.item(Items.REPEATER, Lang.get("villager.gui.route.mode", mode), GuiUtil.lines("villager.gui.route.mode.lore"))
				.setCallback((s, type, action, g) -> {
					if (!VillagerGui.checkPermission(player, guild) || !stillRoute(player, guild, route)) {
						return;
					}
					Zones.setPingPong(guild, route, !route.pingPong);
					GuiUtil.clickSound(player);
					openRoute(player, guild, route.id);
				}));
		gui.setSlot(14, GuiUtil.item(Items.NAME_TAG, Lang.get("villager.gui.route.rename"), GuiUtil.lines("villager.gui.route.rename.lore"))
				.setCallback((s, type, action, g) -> {
					if (!VillagerGui.checkPermission(player, guild) || !stillRoute(player, guild, route)) {
						return;
					}
					GuiUtil.textInput(player, Lang.get("villager.gui.route.rename.title"), route.name, input -> {
						if (!VillagerGui.checkPermission(player, guild) || !stillRoute(player, guild, route)) {
							return;
						}
						String name = ZoneMath.cleanName(input, MAX_NAME);
						if (name.isEmpty()) {
							Msg.send(player, "villager.zone.error.name", MAX_NAME);
							GuiUtil.errorSound(player);
						} else {
							route.name = name;
							GuildManager.markDirty();
							Msg.send(player, "villager.route.renamed", name);
							GuiUtil.successSound(player);
						}
						openRoute(player, guild, route.id);
					}, () -> openRoute(player, guild, route.id));
				}));
		gui.setSlot(16, GuiUtil.item(Items.IRON_SWORD, Lang.get("villager.gui.route.guards", Zones.assignedCount(guild, route)),
				GuiUtil.lines("villager.gui.route.guards.lore")).setCallback((s, type, action, g) -> {
			if (!stillRoute(player, guild, route)) {
				return;
			}
			GuiUtil.clickSound(player);
			openAssignees(player, guild, Lang.get("villager.gui.assignees.title", route.name), Task.GUARD, route.name,
					record -> Zones.assignedRoute(guild, record) == route,
					record -> Zones.assignRoute(record, route),
					record -> Zones.assignRoute(record, null),
					() -> stillRoute(player, guild, route),
					() -> openRoute(player, guild, route.id));
		}));
		gui.setSlot(21, GuiUtil.item(Items.FEATHER, Lang.get("villager.gui.route.redraw"), GuiUtil.lines("villager.gui.route.redraw.lore",
				config.routes.maxWaypoints)).setCallback((s, type, action, g) -> {
			if (!VillagerGui.checkPermission(player, guild) || !stillRoute(player, guild, route)) {
				return;
			}
			g.close();
			RouteSelection.start(player, guild, route);
		}));
		gui.setSlot(25, GuiUtil.item(Items.TNT, Lang.get("villager.gui.route.delete"), GuiUtil.lines("villager.gui.route.delete.lore"))
				.setCallback((s, type, action, g) -> {
					if (!VillagerGui.checkPermission(player, guild) || !stillRoute(player, guild, route)) {
						return;
					}
					GuiUtil.confirm(player, Lang.get("villager.gui.route.delete.confirm", route.name), GuiUtil.lines("villager.gui.route.delete.lore"),
							() -> {
								if (VillagerGui.checkPermission(player, guild) && guild.villagers.routes.contains(route)) {
									Zones.deleteRoute(guild, route);
									Msg.guild(guild, "villager.route.deleted", player.getName().getString(), route.name);
								}
								openZones(player, guild);
							},
							() -> openRoute(player, guild, route.id));
				}));
		gui.setSlot(27, GuiUtil.back(() -> openZones(player, guild)));
		gui.setSlot(35, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	private static boolean stillRoute(ServerPlayer player, Guild guild, PatrolRoute route) {
		if (!guild.villagers.routes.contains(route)) {
			Msg.send(player, "villager.route.error.unknown");
			player.closeContainer();
			return false;
		}
		return true;
	}

	// ================================================================ villagers of a zone / route

	/**
	 * Every guild villager, assigned ones glowing; a click assigns (a villager of another task takes {@code task}) or
	 * removes (back to the automatic mode).
	 */
	private static void openAssignees(ServerPlayer player, Guild guild, Component title, Task task, String targetName,
			Predicate<VillagerRecord> assigned, Consumer<VillagerRecord> assign, Consumer<VillagerRecord> unassign,
			java.util.function.BooleanSupplier stillThere, Runnable back) {
		openAssignees(player, guild, title, task, targetName, assigned, assign, unassign, stillThere, back, record -> false);
	}

	/** Same, {@code refuse}: true when the villager may not be added (the predicate tells the player why). */
	private static void openAssignees(ServerPlayer player, Guild guild, Component title, Task task, String targetName,
			Predicate<VillagerRecord> assigned, Consumer<VillagerRecord> assign, Consumer<VillagerRecord> unassign,
			java.util.function.BooleanSupplier stillThere, Runnable back, Predicate<VillagerRecord> refuse) {
		ServerLevel level = GuildyServer.claimLevel();
		SimpleGui gui = VillagerGui.chest(player, 6, title);
		int slot = 0;
		for (VillagerRecord record : guild.villagers.members.values()) {
			if (slot >= 45) {
				break;
			}
			boolean here = assigned.test(record);
			List<Component> lore = new ArrayList<>();
			lore.add(Lang.get("villager.gui.lore.task", Lang.raw(record.task.langKey())));
			String assignment = Zones.assignmentText(guild, record);
			if (assignment != null) {
				lore.add(Lang.get("villager.gui.lore.assignment", assignment));
			}
			lore.add(Lang.get("villager.gui.lore.status", VillagerGui.statusText(record, level == null ? null : VillagerManager.entity(level, record.uuid))));
			lore.add(Component.empty());
			if (record.isAway()) {
				lore.add(Lang.get("villager.gui.assignees.away"));
			} else if (here) {
				lore.add(Lang.get("villager.gui.assignees.remove"));
			} else if (record.task == task) {
				lore.add(Lang.get("villager.gui.assignees.add"));
			} else {
				lore.add(Lang.get("villager.gui.assignees.add_task", Lang.raw(task.langKey())));
			}
			GuiElementBuilder icon = GuiUtil.item(record.task.icon(), Lang.get("villager.gui.villager.name", record.name), lore);
			if (here) {
				icon.glow();
			}
			gui.setSlot(slot++, icon.setCallback((s, type, action, g) -> {
				if (!VillagerGui.checkPermission(player, guild) || !VillagerGui.stillMember(player, guild, record) || !stillThere.getAsBoolean()) {
					return;
				}
				if (record.isAway()) {
					Msg.send(player, "villager.zone.assign.away", record.name);
					GuiUtil.errorSound(player);
					return;
				}
				if (assigned.test(record)) {
					unassign.accept(record);
					Msg.send(player, "villager.assign.auto_done", record.name);
				} else {
					if (refuse.test(record)) {
						return;
					}
					if (record.task != task) {
						VillagerManager.setTask(guild, record, task, player);
						Msg.send(player, "villager.task.set", record.name, Lang.raw(task.langKey()));
					}
					assign.accept(record);
					Msg.send(player, "villager.assign.done", record.name, targetName);
				}
				GuiUtil.successSound(player);
				openAssignees(player, guild, title, task, targetName, assigned, assign, unassign, stillThere, back, refuse);
			}));
		}
		if (guild.villagers.members.isEmpty()) {
			gui.setSlot(22, GuiUtil.item(Items.VILLAGER_SPAWN_EGG, Lang.get("villager.gui.list.empty"), GuiUtil.lines("villager.gui.list.empty.lore")));
		}
		gui.setSlot(45, GuiUtil.back(back));
		gui.setSlot(53, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	// ================================================================ zone picker of a villager

	/** "Zone de travail" of one villager: automatic, a zone of its task's type, or (guards) a route. */
	public static void openPicker(ServerPlayer player, Guild guild, UUID uuid) {
		VillagerRecord record = guild.villagers.members.get(uuid);
		if (record == null) {
			Msg.send(player, "villager.error.unknown");
			VillagerGui.openList(player, guild);
			return;
		}
		ZoneType zoneType = record.task.zoneType();
		if (zoneType == null) {
			Msg.send(player, "villager.zone.no_zone_task", Lang.raw(record.task.langKey()));
			VillagerGui.openDetail(player, guild, uuid);
			return;
		}
		ServerLevel level = GuildyServer.claimLevel();
		SimpleGui gui = VillagerGui.chest(player, 6, Lang.get("villager.gui.picker.title", record.name));
		WorkZone currentZone = Zones.assignedZone(guild, record);
		PatrolRoute currentRoute = Zones.assignedRoute(guild, record);
		boolean auto = currentZone == null && currentRoute == null;

		List<Component> autoLore = new ArrayList<>(GuiUtil.lines(WorkLang.autoLoreKey(zoneType), config().zones.autoSearchRadius));
		autoLore.add(Component.empty());
		autoLore.add(Lang.get(auto ? "villager.gui.picker.current" : "villager.gui.picker.choose"));
		GuiElementBuilder autoIcon = GuiUtil.item(Items.COMPASS, Lang.get("villager.gui.picker.auto"), autoLore);
		if (auto) {
			autoIcon.glow();
		}
		gui.setSlot(0, autoIcon.setCallback((s, type, action, g) -> {
			if (!VillagerGui.checkPermission(player, guild) || !VillagerGui.stillMember(player, guild, record)) {
				return;
			}
			Zones.assign(record, null);
			Msg.send(player, "villager.assign.auto_done", record.name);
			GuiUtil.successSound(player);
			VillagerGui.openDetail(player, guild, uuid);
		}));
		int slot = 1;
		for (WorkZone zone : guild.villagers.zones) {
			if (zone.type != zoneType || slot >= 45) {
				continue;
			}
			boolean current = zone == currentZone;
			GuiElementBuilder icon = zoneIcon(level, guild, zone, false);
			icon.addLoreLine(GuiUtil.noItalic(Component.empty()));
			icon.addLoreLine(GuiUtil.noItalic(Lang.get(current ? "villager.gui.picker.current" : "villager.gui.picker.choose")));
			if (current) {
				icon.glow();
			}
			gui.setSlot(slot++, icon.setCallback((s, type, action, g) -> {
				if (!VillagerGui.checkPermission(player, guild) || !VillagerGui.stillMember(player, guild, record) || !stillZone(player, guild, zone)
						|| Mines.refuseAssign(player, guild, zone, record)) {
					return;
				}
				Zones.assign(record, zone);
				Msg.send(player, "villager.assign.done", record.name, zone.name);
				GuiUtil.successSound(player);
				VillagerGui.openDetail(player, guild, uuid);
			}));
		}
		if (zoneType == ZoneType.PATROL) {
			for (PatrolRoute route : guild.villagers.routes) {
				if (slot >= 45) {
					break;
				}
				boolean current = route == currentRoute;
				GuiElementBuilder icon = routeIcon(guild, route, false);
				icon.addLoreLine(GuiUtil.noItalic(Component.empty()));
				icon.addLoreLine(GuiUtil.noItalic(Lang.get(current ? "villager.gui.picker.current" : "villager.gui.picker.choose")));
				if (current) {
					icon.glow();
				}
				gui.setSlot(slot++, icon.setCallback((s, type, action, g) -> {
					if (!VillagerGui.checkPermission(player, guild) || !VillagerGui.stillMember(player, guild, record) || !stillRoute(player, guild, route)) {
						return;
					}
					Zones.assignRoute(record, route);
					Msg.send(player, "villager.assign.done", record.name, route.name);
					GuiUtil.successSound(player);
					VillagerGui.openDetail(player, guild, uuid);
				}));
			}
		}
		if (slot == 1) {
			gui.setSlot(22, GuiUtil.item(Items.MAP, Lang.get("villager.gui.picker.none", Lang.raw(zoneType.langKey())),
					GuiUtil.lines("villager.gui.picker.none.lore")));
		}
		gui.setSlot(45, GuiUtil.back(() -> VillagerGui.openDetail(player, guild, uuid)));
		gui.setSlot(49, GuiUtil.item(ZoneFlags.icon(zoneType), Lang.get("villager.gui.picker.manage"), List.of()).setCallback((s, type, action, g) -> {
			GuiUtil.clickSound(player);
			openZones(player, guild);
		}));
		gui.setSlot(53, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}
}
