package dev.guildy.villager;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Hooks;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import dev.guildy.gui.GuiUtil;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Villager menus: list (+ candidate), villager detail (task, work zone, mission, carried items, call, dismiss), bulk
 * task assignment, storage chests and the role picker used after selecting a container. Zone and route menus are in
 * {@link ZoneGui}.
 */
public final class VillagerGui {
	private static final int PER_PAGE = 36;
	private static final int CARRIED_LINES = 8;
	/** Carried stacks listed in the villager list (the detail menu shows more). */
	private static final int LIST_CARRIED_LINES = 3;

	private VillagerGui() {
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
		ServerLevel level = GuildyServer.claimLevel();
		List<VillagerRecord> records = new ArrayList<>(guild.villagers.members.values());
		int max = VillagerManager.maxVillagers(guild);
		int pages = GuiUtil.pages(records.size(), PER_PAGE);
		int current = Math.max(0, Math.min(page, pages - 1));
		SimpleGui gui = chest(player, 6, Lang.get("villager.gui.list.title", records.size(), max));
		for (int i = 0; i < PER_PAGE; i++) {
			int index = current * PER_PAGE + i;
			if (index >= records.size()) {
				break;
			}
			VillagerRecord record = records.get(index);
			gui.setSlot(i, villagerIcon(level, guild, record, true).setCallback((slot, type, action, g) -> {
				GuiUtil.clickSound(player);
				openDetail(player, guild, record.uuid);
			}));
		}
		if (records.isEmpty()) {
			gui.setSlot(13, GuiUtil.item(Items.VILLAGER_SPAWN_EGG, Lang.get("villager.gui.list.empty"), GuiUtil.lines("villager.gui.list.empty.lore")));
		}
		candidateSection(gui, player, guild, level);

		gui.setSlot(45, GuiUtil.back(() -> Hooks.openMainMenu.accept(player)));
		if (current > 0) {
			gui.setSlot(46, GuiUtil.item(Items.ARROW, Lang.get("gui.page.prev"), List.of(Lang.get("gui.page", current + 1, pages)))
					.setCallback((slot, type, action, g) -> openList(player, guild, current - 1)));
		}
		int idle = 0;
		for (VillagerRecord record : records) {
			if (record.task == Task.IDLE) {
				idle++;
			}
		}
		gui.setSlot(47, GuiUtil.item(Items.WRITABLE_BOOK, Lang.get("villager.gui.assign_idle"), GuiUtil.lines("villager.gui.assign_idle.lore", idle))
				.setCallback((slot, type, action, g) -> {
					if (!checkPermission(player, guild)) {
						return;
					}
					GuiUtil.clickSound(player);
					openAssignIdle(player, guild);
				}));
		gui.setSlot(48, GuiUtil.item(ZoneFlags.icon(ZoneType.FOREST), Lang.get("villager.gui.zones"),
				GuiUtil.lines("villager.gui.zones.lore", guild.villagers.zones.size(), guild.villagers.routes.size())).setCallback((slot, type, action, g) -> {
			GuiUtil.clickSound(player);
			ZoneGui.openZones(player, guild);
		}));
		gui.setSlot(49, GuiUtil.item(Items.CHEST, Lang.get("villager.gui.storage"), GuiUtil.lines("villager.gui.storage.lore", guild.villagers.chests.size()))
				.setCallback((slot, type, action, g) -> {
					GuiUtil.clickSound(player);
					openStorage(player, guild, 0);
				}));
		gui.setSlot(50, WorkChunkLoader.menuItem(guild));
		gui.setSlot(51, GuiUtil.item(Items.BOOK, Lang.get("villager.gui.help"), GuiUtil.lines("villager.gui.help.lore")));
		if (current < pages - 1) {
			gui.setSlot(52, GuiUtil.item(Items.ARROW, Lang.get("gui.page.next"), List.of(Lang.get("gui.page", current + 1, pages)))
					.setCallback((slot, type, action, g) -> openList(player, guild, current + 1)));
		}
		gui.setSlot(53, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	private static void candidateSection(SimpleGui gui, ServerPlayer player, Guild guild, @Nullable ServerLevel level) {
		List<VillagerManager.Candidate> candidates = VillagerManager.candidates(guild);
		if (candidates.size() == 1) {
			VillagerManager.Candidate candidate = candidates.getFirst();
			gui.setSlot(40, GuiUtil.item(Items.VILLAGER_SPAWN_EGG, Lang.get("villager.gui.candidate.name", candidate.name()),
					GuiUtil.lines("villager.gui.candidate.lore", VillagerManager.remainingSeconds(candidate))).glow());
			gui.setSlot(39, GuiUtil.item(Items.CONCRETE.pick(DyeColor.LIME), Lang.get("villager.gui.candidate.accept", candidate.name()), List.of())
					.setCallback((slot, type, action, g) -> {
						GuiUtil.clickSound(player);
						VillagerManager.accept(player, candidate.name());
						openList(player, guild, 0);
					}));
			gui.setSlot(41, GuiUtil.item(Items.CONCRETE.pick(DyeColor.RED), Lang.get("villager.gui.candidate.refuse", candidate.name()), List.of())
					.setCallback((slot, type, action, g) -> {
						GuiUtil.clickSound(player);
						VillagerManager.refuse(player, candidate.name());
						openList(player, guild, 0);
					}));
			return;
		}
		if (candidates.size() > 1) {
			// Several candidates: one icon each, left click = accept, right click = refuse.
			int shown = Math.min(candidates.size(), 5);
			int first = 40 - (shown - 1);
			for (int i = 0; i < shown; i++) {
				VillagerManager.Candidate candidate = candidates.get(i);
				List<Component> lore = GuiUtil.lines("villager.gui.candidate.lore", VillagerManager.remainingSeconds(candidate));
				lore.addAll(GuiUtil.lines("villager.gui.candidate.click"));
				gui.setSlot(first + 2 * i, GuiUtil.item(Items.VILLAGER_SPAWN_EGG, Lang.get("villager.gui.candidate.name", candidate.name()), lore)
						.glow().setCallback((slot, type, action, g) -> {
							GuiUtil.clickSound(player);
							if (type.isRight) {
								VillagerManager.refuse(player, candidate.name());
							} else if (type.isLeft) {
								VillagerManager.accept(player, candidate.name());
							} else {
								return;
							}
							openList(player, guild, 0);
						}));
			}
			return;
		}
		boolean reception = guild.structures.find(VillagerManager.RECEPTION) != null;
		int count = guild.villagers.members.size();
		int max = VillagerManager.maxVillagers(guild);
		String beds;
		if (!VillagerModule.config().arrivals.requireFreeBed) {
			beds = Lang.raw("villager.gui.beds.not_required");
		} else if (level == null) {
			beds = "?";
		} else {
			int free = VillagerManager.freeBeds(level, guild);
			beds = free > 0 ? Lang.raw("villager.gui.yes") + " (" + free + ")" : Lang.raw("villager.gui.no");
		}
		gui.setSlot(40, GuiUtil.item(Items.CLOCK, Lang.get("villager.gui.candidate.none"), GuiUtil.lines("villager.gui.candidate.none.lore",
				Lang.raw(reception ? "villager.gui.yes" : "villager.gui.no"), count, max, beds,
				Math.round(VillagerModule.config().arrivals.arrivalChance * 100))));
	}

	/** Icon of a villager: task, zone / route, status, profession, position, carried items and stats. */
	private static GuiElementBuilder villagerIcon(@Nullable ServerLevel level, Guild guild, VillagerRecord record, boolean manageHint) {
		Villager villager = level == null ? null : VillagerManager.entity(level, record.uuid);
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("villager.gui.lore.task", Lang.raw(record.task.langKey())));
		String assignment = Zones.assignmentText(guild, record);
		if (assignment != null) {
			lore.add(Lang.get("villager.gui.lore.assignment", assignment));
		}
		Component destination = ExpeditionMenu.listLine(guild, record);
		if (destination != null) {
			lore.add(destination);
		}
		Component wishes = WishlistMenu.summary(guild, record);
		if (wishes != null) {
			lore.add(wishes);
		}
		lore.add(Lang.get("villager.gui.lore.status", statusText(record, villager)));
		WorkChunkLoader.addLore(lore, record);
		GuardGui.addLore(lore, record, villager);
		if (villager != null) {
			String profession = villager.getVillagerData().profession().unwrapKey()
					.map(key -> Lang.raw("villager.profession." + key.identifier().getPath())).orElse("?");
			lore.add(Lang.get("villager.gui.lore.profession", profession));
			BlockPos pos = villager.blockPosition();
			lore.add(Lang.get("villager.gui.lore.position", pos.getX(), pos.getY(), pos.getZ()));
		} else if (record.lastPos != null) {
			lore.add(Lang.get("villager.gui.lore.last_position", record.lastPos.getX(), record.lastPos.getY(), record.lastPos.getZ()));
		}
		if (!record.carried.isEmpty()) {
			lore.add(Lang.get("villager.gui.lore.carried", record.carriedCount()));
			int shown = 0;
			for (ItemStack stack : record.carried) {
				if (shown++ >= LIST_CARRIED_LINES) {
					lore.add(Lang.get("villager.gui.carried.more", record.carried.size() - LIST_CARRIED_LINES));
					break;
				}
				lore.add(Lang.get("villager.gui.carried.line", stack.getCount()).append(stack.getHoverName()));
			}
		}
		VillagerRecord.Stats stats = record.stats;
		if (stats.sorted > 0) {
			lore.add(Lang.get("villager.gui.lore.stat.sorted", stats.sorted));
		}
		if (stats.mined > 0) {
			lore.add(Lang.get("villager.gui.lore.stat.mined", stats.mined));
		}
		if (stats.logs > 0) {
			lore.add(Lang.get("villager.gui.lore.stat.logs", stats.logs));
		}
		if (stats.crops > 0) {
			lore.add(Lang.get("villager.gui.lore.stat.crops", stats.crops));
		}
		if (stats.kills > 0) {
			lore.add(Lang.get("villager.gui.lore.stat.kills", stats.kills));
		}
		if (manageHint) {
			lore.add(Lang.get("villager.gui.lore.manage"));
		}
		return GuiUtil.item(GuardGui.icon(record), Lang.get("villager.gui.villager.name", record.name), lore);
	}

	static String statusText(VillagerRecord record, @Nullable Villager villager) {
		if (record.isAway()) {
			VillagerRuntime rt = TaskEngine.peek(record.uuid);
			return rt == null ? Lang.raw("villager.status.unloaded") : Lang.raw(rt.statusKey, rt.statusArgs);
		}
		if (villager == null) {
			return Lang.raw("villager.status.unloaded");
		}
		VillagerRuntime rt = TaskEngine.peek(record.uuid);
		if (rt == null) {
			return Lang.raw(record.task == Task.IDLE ? "villager.status.idle" : "villager.status.starting");
		}
		return Lang.raw(rt.statusKey, rt.statusArgs);
	}

	// ================================================================ detail

	/**
	 * Detail of a villager. Row 2: tasks; row 3: work zone, mission (goal, amount, item), carried items; row 4: call
	 * it here, dismiss.
	 */
	static void openDetail(ServerPlayer player, Guild guild, UUID uuid) {
		VillagerRecord record = guild.villagers.members.get(uuid);
		if (record == null) {
			Msg.send(player, "villager.error.unknown");
			openList(player, guild, 0);
			return;
		}
		ServerLevel level = GuildyServer.claimLevel();
		SimpleGui gui = chest(player, 5, Lang.get("villager.gui.detail.title", record.name));
		gui.setSlot(4, villagerIcon(level, guild, record, false));
		GuardGui.addButtons(gui, player, guild, record);

		Task[] tasks = Task.values();
		for (int i = 0; i < tasks.length && i < 9; i++) {
			Task task = tasks[i];
			boolean currentTask = record.task == task;
			List<Component> lore = new ArrayList<>(GuiUtil.lines(task.descKey()));
			lore.add(Component.empty());
			lore.add(Lang.get(currentTask ? "villager.gui.task.current" : "villager.gui.task.choose"));
			GuiElementBuilder button = GuiUtil.item(task.icon(), Lang.get("villager.gui.task.button", Lang.raw(task.langKey())), lore);
			if (currentTask) {
				button.glow();
			}
			gui.setSlot(9 + i, button.setCallback((slot, type, action, g) -> {
				if (!checkPermission(player, guild) || !stillMember(player, guild, record)) {
					return;
				}
				if (record.task != task) {
					VillagerManager.setTask(guild, record, task, player);
					Msg.send(player, "villager.task.set", record.name, Lang.raw(task.langKey()));
					if (task.zoneType() != null && Zones.assignedZone(guild, record) == null && Zones.assignedRoute(guild, record) == null) {
						Msg.send(player, "villager.task.auto_hint", record.name);
					}
					GuiUtil.successSound(player);
				}
				openDetail(player, guild, uuid);
			}));
		}

		gui.setSlot(19, zoneButton(level, guild, record).setCallback((slot, type, action, g) -> {
			if (!checkPermission(player, guild) || !stillMember(player, guild, record)) {
				return;
			}
			if (record.task.zoneType() == null) {
				Msg.send(player, "villager.zone.no_zone_task", Lang.raw(record.task.langKey()));
				GuiUtil.errorSound(player);
				return;
			}
			GuiUtil.clickSound(player);
			ZoneGui.openPicker(player, guild, uuid);
		}));
		if (record.task.isExpedition()) {
			// Expeditions use no work zone: their destination takes the button's place.
			gui.setSlot(19, ExpeditionMenu.button(player, guild, record));
		}
		if (WishlistMenu.supports(record.task)) {
			gui.setSlot(20, WishlistMenu.villagerButton(player, guild, record));
		}
		missionControls(gui, player, guild, record);
		gui.setSlot(25, carriedIcon(record));

		gui.setSlot(29, GuiUtil.item(Items.ENDER_PEARL, Lang.get("villager.gui.call"), GuiUtil.lines("villager.work.call.lore", record.name))
				.setCallback((slot, type, action, g) -> {
					if (!checkPermission(player, guild) || !stillMember(player, guild, record)) {
						return;
					}
					switch (VillagerManager.summon(player, guild, record)) {
						case DONE -> {
							GuiUtil.successSound(player);
							openDetail(player, guild, uuid);
						}
						case PENDING -> {
							// Its chunk is loading: it arrives in a moment (watch it come).
							GuiUtil.clickSound(player);
							g.close();
						}
						case FAILED -> GuiUtil.errorSound(player);
					}
				}));
		gui.setSlot(33, GuiUtil.item(Items.OAK_DOOR, Lang.get("villager.gui.dismiss", record.name), GuiUtil.lines("villager.gui.dismiss.lore"))
				.setCallback((slot, type, action, g) -> {
					if (!checkPermission(player, guild) || !stillMember(player, guild, record)) {
						return;
					}
					GuiUtil.confirm(player, Lang.get("villager.gui.dismiss.confirm", record.name), GuiUtil.lines("villager.gui.dismiss.lore"),
							() -> {
								VillagerManager.dismiss(player, guild, record);
								openList(player, guild, 0);
							},
							() -> openDetail(player, guild, uuid));
				}));
		gui.setSlot(36, GuiUtil.back(() -> openList(player, guild, 0)));
		gui.setSlot(44, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	/** "Zone de travail": the assigned zone / route, the automatic mode, or nothing for tasks without zones. */
	private static GuiElementBuilder zoneButton(@Nullable ServerLevel level, Guild guild, VillagerRecord record) {
		ZoneType type = record.task.zoneType();
		if (type == null) {
			return GuiUtil.item(Items.DYE.pick(DyeColor.GRAY), Lang.get("villager.gui.zonebtn.none"), GuiUtil.lines("villager.gui.zonebtn.none.lore",
					Lang.raw(record.task.langKey())));
		}
		Component name = Lang.get("villager.gui.zonebtn", Zones.assignmentText(guild, record));
		PatrolRoute route = Zones.assignedRoute(guild, record);
		WorkZone zone = Zones.assignedZone(guild, record);
		GuiElementBuilder button;
		if (route != null) {
			button = ZoneGui.routeIcon(guild, route, false);
		} else if (zone != null) {
			button = ZoneGui.zoneIcon(level, guild, zone, false);
		} else {
			button = GuiUtil.item(Items.COMPASS, name, GuiUtil.lines(WorkLang.autoLoreKey(type),
					VillagerModule.config().zones.autoSearchRadius));
		}
		button.setName(GuiUtil.noItalic(name));
		button.addLoreLine(GuiUtil.noItalic(Component.empty()));
		button.addLoreLine(GuiUtil.noItalic(Lang.get("villager.gui.zonebtn.click")));
		return button;
	}

	/** Mission goal (row 3): mode, amount and target item, for gathering tasks and expeditions. */
	private static void missionControls(SimpleGui gui, ServerPlayer player, Guild guild, VillagerRecord record) {
		UUID uuid = record.uuid;
		List<Component> goalLore = new ArrayList<>(GuiUtil.lines("villager.gui.goal.lore"));
		goalLore.add(Component.empty());
		if (record.goal == Goal.QUOTA) {
			goalLore.add(Lang.get("villager.gui.goal.progress", record.goalProgress, record.goalAmount, Missions.goalItemName(record)));
		}
		if (!record.task.hasGoal()) {
			goalLore.add(Lang.get("villager.gui.goal.no_effect"));
		}
		goalLore.add(Lang.get("villager.gui.goal.click"));
		gui.setSlot(21, GuiUtil.item(Items.TARGET, Lang.get("villager.gui.goal", Lang.raw(record.goal.langKey())), goalLore)
				.setCallback((slot, type, action, g) -> {
					if (!checkPermission(player, guild) || !stillMember(player, guild, record)) {
						return;
					}
					record.goal = record.goal.next();
					Missions.reset(record);
					GuildManager.markDirty();
					GuiUtil.clickSound(player);
					openDetail(player, guild, uuid);
				}));
		if (record.goal == Goal.QUOTA) {
			gui.setSlot(22, GuiUtil.item(Items.HOPPER, Lang.get("villager.gui.goal.amount", record.goalAmount),
					GuiUtil.lines("villager.gui.goal.amount.lore")).setCallback((slot, type, action, g) -> {
				if (!checkPermission(player, guild) || !stillMember(player, guild, record)) {
					return;
				}
				int step = type.shift ? 64 : 16;
				int amount = record.goalAmount + (type.isRight ? -step : step);
				record.goalAmount = Math.max(1, Math.min(10_000, amount));
				record.goalReached = record.goalProgress >= record.goalAmount;
				GuildManager.markDirty();
				GuiUtil.clickSound(player);
				openDetail(player, guild, uuid);
			}));
			Item target = record.goalItem.isEmpty() ? null : ItemUtil.item(record.goalItem);
			GuiElementBuilder itemButton = target == null
					? GuiUtil.item(Items.CHEST, Lang.get("villager.gui.goal.item", Lang.raw("villager.goal.any_item")), GuiUtil.lines("villager.gui.goal.item.lore"))
					: GuiUtil.item(target, Lang.get("villager.gui.goal.item", Missions.goalItemName(record)), GuiUtil.lines("villager.gui.goal.item.lore"));
			gui.setSlot(23, itemButton.setCallback((slot, type, action, g) -> {
				if (!checkPermission(player, guild) || !stillMember(player, guild, record)) {
					return;
				}
				ItemStack held = player.getMainHandItem();
				if (type.shift || held.isEmpty()) {
					record.goalItem = "";
				} else {
					record.goalItem = ItemUtil.id(held.getItem());
				}
				Missions.reset(record);
				GuildManager.markDirty();
				Msg.send(player, "villager.goal.item.set", record.name, Missions.goalItemName(record));
				GuiUtil.clickSound(player);
				openDetail(player, guild, uuid);
			}));
		}
	}

	private static GuiElementBuilder carriedIcon(VillagerRecord record) {
		List<Component> lore = new ArrayList<>();
		if (record.carried.isEmpty()) {
			lore.add(Lang.get("villager.gui.carried.empty"));
		} else {
			int shown = 0;
			for (ItemStack stack : record.carried) {
				if (shown++ >= CARRIED_LINES) {
					lore.add(Lang.get("villager.gui.carried.more", record.carried.size() - CARRIED_LINES));
					break;
				}
				lore.add(Lang.get("villager.gui.carried.line", stack.getCount()).append(stack.getHoverName()));
			}
		}
		return GuiUtil.item(Items.BUNDLE, Lang.get("villager.gui.carried"), lore);
	}

	// ================================================================ assign idle

	static void openAssignIdle(ServerPlayer player, Guild guild) {
		SimpleGui gui = chest(player, 3, Lang.get("villager.gui.assign.title"));
		int slot = 11;
		for (Task task : Task.values()) {
			if (task == Task.IDLE) {
				continue;
			}
			List<Component> lore = new ArrayList<>(GuiUtil.lines(task.descKey()));
			lore.add(Component.empty());
			lore.add(Lang.get("villager.gui.task.choose"));
			gui.setSlot(slot++, GuiUtil.item(task.icon(), Lang.get("villager.gui.task.button", Lang.raw(task.langKey())), lore)
					.setCallback((s, type, action, g) -> {
						if (!checkPermission(player, guild)) {
							return;
						}
						int count = 0;
						for (VillagerRecord record : guild.villagers.members.values()) {
							if (record.task == Task.IDLE) {
								VillagerManager.setTask(guild, record, task, player);
								count++;
							}
						}
						if (count == 0) {
							Msg.send(player, "villager.task.none_idle");
						} else {
							Msg.send(player, "villager.task.set_all", count, Lang.raw(task.langKey()));
							if (task.zoneType() != null) {
								Msg.send(player, "villager.task.auto_hint_all");
							}
							GuiUtil.successSound(player);
						}
						openList(player, guild, 0);
					}));
		}
		gui.setSlot(18, GuiUtil.back(() -> openList(player, guild, 0)));
		gui.setSlot(26, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	// ================================================================ storage

	/** Storage menu (see {@link StorageGui}). */
	static void openStorage(ServerPlayer player, Guild guild, int page) {
		StorageGui.openStorage(player, guild, page);
	}

	/** Role choice after right-clicking a container in selection mode (see {@link StorageGui}). */
	static void openRolePicker(ServerPlayer player, Guild guild, BlockPos pos) {
		StorageGui.openRolePicker(player, guild, pos, 0);
	}

	// ================================================================ helpers

	/**
	 * Chest menu whose clicks are isolated by {@link Safe} (a failing button never breaks the menu); only plain
	 * left / right clicks reach the buttons (no double toggle on double clicks, drags or number keys).
	 */
	static SimpleGui chest(ServerPlayer player, int rows, Component title) {
		SimpleGui gui = new SimpleGui(GuiUtil.rows(rows), player, false) {
			@Override
			public boolean click(int index, ClickType type, ContainerInput action) {
				if (!(type.isLeft || type.isRight) || type.isDragging) {
					return false;
				}
				return Safe.get("villager.gui", () -> super.click(index, type, action), false);
			}
		};
		gui.setTitle(title);
		return gui;
	}

	static boolean checkPermission(ServerPlayer player, Guild guild) {
		if (GuildManager.of(player) != guild || !guild.can(player.getUUID(), GuildPermission.VILLAGERS)) {
			Msg.send(player, "error.no_permission");
			GuiUtil.errorSound(player);
			return false;
		}
		return true;
	}

	static boolean stillMember(ServerPlayer player, Guild guild, VillagerRecord record) {
		if (guild.villagers.members.get(record.uuid) != record) {
			Msg.send(player, "villager.error.unknown");
			player.closeContainer();
			return false;
		}
		return true;
	}
}
