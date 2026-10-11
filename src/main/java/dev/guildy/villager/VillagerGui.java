package dev.guildy.villager;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Hooks;
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
 * Villager menus: list (+ candidate), villager detail (task, work zone, dismiss), bulk task assignment, storage chests
 * and the role picker used after selecting a container.
 */
public final class VillagerGui {
	private static final int PER_PAGE = 36;
	private static final int CHESTS_PER_PAGE = 45;
	private static final int CARRIED_LINES = 8;

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
			gui.setSlot(i, villagerIcon(level, record, true).setCallback((slot, type, action, g) -> {
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
		gui.setSlot(49, GuiUtil.item(Items.CHEST, Lang.get("villager.gui.storage"), GuiUtil.lines("villager.gui.storage.lore", guild.villagers.chests.size()))
				.setCallback((slot, type, action, g) -> {
					GuiUtil.clickSound(player);
					openStorage(player, guild, 0);
				}));
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
		VillagerManager.Candidate candidate = VillagerManager.candidate(guild);
		if (candidate != null) {
			gui.setSlot(40, GuiUtil.item(Items.VILLAGER_SPAWN_EGG, Lang.get("villager.gui.candidate.name", candidate.name()),
					GuiUtil.lines("villager.gui.candidate.lore", VillagerManager.remainingSeconds(candidate))).glow());
			gui.setSlot(39, GuiUtil.item(Items.CONCRETE.pick(DyeColor.LIME), Lang.get("villager.gui.candidate.accept", candidate.name()), List.of())
					.setCallback((slot, type, action, g) -> {
						GuiUtil.clickSound(player);
						VillagerManager.accept(player);
						openList(player, guild, 0);
					}));
			gui.setSlot(41, GuiUtil.item(Items.CONCRETE.pick(DyeColor.RED), Lang.get("villager.gui.candidate.refuse", candidate.name()), List.of())
					.setCallback((slot, type, action, g) -> {
						GuiUtil.clickSound(player);
						VillagerManager.refuse(player);
						openList(player, guild, 0);
					}));
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

	/** Icon of a villager with its task, status, position, zone, load and stats. */
	private static GuiElementBuilder villagerIcon(@Nullable ServerLevel level, VillagerRecord record, boolean manageHint) {
		Villager villager = level == null ? null : VillagerManager.entity(level, record.uuid);
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("villager.gui.lore.task", Lang.raw(record.task.langKey())));
		lore.add(Lang.get("villager.gui.lore.status", statusText(record, villager)));
		if (villager != null) {
			String profession = villager.getVillagerData().profession().unwrapKey()
					.map(key -> Lang.raw("villager.profession." + key.identifier().getPath())).orElse("?");
			lore.add(Lang.get("villager.gui.lore.profession", profession));
			BlockPos pos = villager.blockPosition();
			lore.add(Lang.get("villager.gui.lore.position", pos.getX(), pos.getY(), pos.getZ()));
		} else if (record.lastPos != null) {
			lore.add(Lang.get("villager.gui.lore.last_position", record.lastPos.getX(), record.lastPos.getY(), record.lastPos.getZ()));
		}
		if (record.workPos != null) {
			lore.add(Lang.get("villager.gui.lore.zone", record.workPos.getX(), record.workPos.getY(), record.workPos.getZ()));
		} else if (record.task.needsWorkPos()) {
			lore.add(Lang.get("villager.gui.lore.zone.none"));
		}
		if (!record.carried.isEmpty()) {
			lore.add(Lang.get("villager.gui.lore.carried", record.carriedCount()));
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
		return GuiUtil.item(record.task.icon(), Lang.get("villager.gui.villager.name", record.name), lore);
	}

	private static String statusText(VillagerRecord record, @Nullable Villager villager) {
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

	static void openDetail(ServerPlayer player, Guild guild, UUID uuid) {
		VillagerRecord record = guild.villagers.members.get(uuid);
		if (record == null) {
			Msg.send(player, "villager.error.unknown");
			openList(player, guild, 0);
			return;
		}
		ServerLevel level = GuildyServer.claimLevel();
		SimpleGui gui = chest(player, 5, Lang.get("villager.gui.detail.title", record.name));
		gui.setSlot(4, villagerIcon(level, record, false));

		Task[] tasks = Task.values();
		for (int i = 0; i < tasks.length; i++) {
			Task task = tasks[i];
			boolean currentTask = record.task == task;
			List<Component> lore = new ArrayList<>(GuiUtil.lines(task.descKey()));
			lore.add(Component.empty());
			lore.add(Lang.get(currentTask ? "villager.gui.task.current" : "villager.gui.task.choose"));
			GuiElementBuilder button = GuiUtil.item(task.icon(), Lang.get("villager.gui.task.button", Lang.raw(task.langKey())), lore);
			if (currentTask) {
				button.glow();
			}
			gui.setSlot(19 + i, button.setCallback((slot, type, action, g) -> {
				if (!checkPermission(player, guild) || !stillMember(player, guild, record)) {
					return;
				}
				if (record.task != task) {
					VillagerManager.setTask(guild, record, task, player);
					Msg.send(player, "villager.task.set", record.name, Lang.raw(task.langKey()));
					if (task.needsWorkPos() && record.workPos == null) {
						Msg.send(player, "villager.task.need_workpos");
					}
					GuiUtil.successSound(player);
				}
				openDetail(player, guild, uuid);
			}));
		}

		gui.setSlot(29, carriedIcon(record));
		List<Component> zoneLore = new ArrayList<>();
		if (record.workPos != null) {
			zoneLore.add(Lang.get("villager.gui.lore.zone", record.workPos.getX(), record.workPos.getY(), record.workPos.getZ()));
		} else {
			zoneLore.add(Lang.get("villager.gui.lore.zone.none"));
		}
		zoneLore.addAll(GuiUtil.lines("villager.gui.workpos.lore"));
		gui.setSlot(31, GuiUtil.item(Items.COMPASS, Lang.get("villager.gui.workpos"), zoneLore).setCallback((slot, type, action, g) -> {
			if (!checkPermission(player, guild) || !stillMember(player, guild, record)) {
				return;
			}
			BlockPos pos = player.blockPosition();
			if (!GuildyServer.isClaimLevel(player.level()) || GuildManager.at(player.level(), pos) != guild) {
				Msg.send(player, "villager.workpos.outside");
				GuiUtil.errorSound(player);
				return;
			}
			record.workPos = pos.immutable();
			GuildManager.markDirty();
			TaskEngine.resetTask(record.uuid);
			Msg.send(player, "villager.workpos.set", record.name, pos.getX(), pos.getY(), pos.getZ());
			GuiUtil.successSound(player);
			openDetail(player, guild, uuid);
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
							if (task.needsWorkPos()) {
								Msg.send(player, "villager.task.need_workpos");
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

	static void openStorage(ServerPlayer player, Guild guild, int page) {
		ServerLevel level = GuildyServer.claimLevel();
		if (level != null) {
			Storage.cleanup(level, guild);
		}
		List<StorageChest> chests = new ArrayList<>(guild.villagers.chests);
		int pages = GuiUtil.pages(chests.size(), CHESTS_PER_PAGE);
		int current = Math.max(0, Math.min(page, pages - 1));
		SimpleGui gui = chest(player, 6, Lang.get("villager.gui.storage.title", chests.size()));
		for (int i = 0; i < CHESTS_PER_PAGE; i++) {
			int index = current * CHESTS_PER_PAGE + i;
			if (index >= chests.size()) {
				break;
			}
			StorageChest chest = chests.get(index);
			gui.setSlot(i, chestIcon(level, chest).setCallback((slot, type, action, g) -> {
				if (!checkPermission(player, guild)) {
					return;
				}
				GuiUtil.confirm(player, Lang.get("villager.gui.storage.remove.confirm"),
						List.of(Lang.get("villager.gui.storage.entry.lore.pos", chest.pos.getX(), chest.pos.getY(), chest.pos.getZ())),
						() -> {
							if (guild.villagers.chests.remove(chest)) {
								GuildManager.markDirty();
								Msg.send(player, "villager.storage.removed");
							}
							openStorage(player, guild, current);
						},
						() -> openStorage(player, guild, current));
			}));
		}
		gui.setSlot(45, GuiUtil.back(() -> openList(player, guild, 0)));
		if (current > 0) {
			gui.setSlot(46, GuiUtil.item(Items.ARROW, Lang.get("gui.page.prev"), List.of(Lang.get("gui.page", current + 1, pages)))
					.setCallback((slot, type, action, g) -> openStorage(player, guild, current - 1)));
		}
		gui.setSlot(47, categoriesIcon());
		gui.setSlot(49, GuiUtil.item(Items.CHEST, Lang.get("villager.gui.storage.add"), GuiUtil.lines("villager.gui.storage.add.lore",
				VillagerInteractions.SELECTION_SECONDS)).glow().setCallback((slot, type, action, g) -> {
			if (!checkPermission(player, guild)) {
				return;
			}
			if (guild.villagers.chests.size() >= VillagerModule.config().storage.maxChests) {
				Msg.send(player, "villager.storage.limit", VillagerModule.config().storage.maxChests);
				GuiUtil.errorSound(player);
				return;
			}
			g.close();
			GuiUtil.clickSound(player);
			VillagerInteractions.startSelection(player, guild);
		}));
		gui.setSlot(51, GuiUtil.item(Items.PAPER, Lang.get("villager.gui.help"),
				GuiUtil.lines("villager.gui.storage.help.lore", Storage.categories().fallback())));
		if (current < pages - 1) {
			gui.setSlot(52, GuiUtil.item(Items.ARROW, Lang.get("gui.page.next"), List.of(Lang.get("gui.page", current + 1, pages)))
					.setCallback((slot, type, action, g) -> openStorage(player, guild, current + 1)));
		}
		gui.setSlot(53, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	private static GuiElementBuilder chestIcon(@Nullable ServerLevel level, StorageChest chest) {
		Item icon = chest.isDeposit() ? Items.HOPPER : Storage.isFallback(chest) ? Items.BARREL : Items.CHEST;
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("villager.gui.storage.entry.lore.pos", chest.pos.getX(), chest.pos.getY(), chest.pos.getZ()));
		if (level == null || !level.isLoaded(chest.pos)) {
			lore.add(Lang.get("villager.gui.storage.entry.lore.unloaded"));
		} else {
			int[] fill = Storage.fill(level, chest.pos);
			if (fill == null) {
				lore.add(Lang.get("villager.gui.storage.entry.lore.missing"));
			} else {
				lore.add(Lang.get("villager.gui.storage.entry.lore.fill", fill[0], fill[1]));
			}
		}
		lore.add(Lang.get("villager.gui.storage.entry.lore.remove"));
		Component name = chest.isDeposit() ? Lang.get("villager.gui.storage.entry.deposit") : Lang.get("villager.gui.storage.entry.category", chest.category);
		return GuiUtil.item(icon, name, lore);
	}

	private static GuiElementBuilder categoriesIcon() {
		List<Component> lore = new ArrayList<>();
		StorageCategories categories = Storage.categories();
		for (String name : categories.names()) {
			lore.add(Lang.get("villager.gui.storage.categories.line", name));
		}
		lore.add(Lang.get("villager.gui.storage.categories.line", categories.fallback()));
		lore.addAll(GuiUtil.lines("villager.gui.storage.categories.lore"));
		return GuiUtil.item(Items.BOOKSHELF, Lang.get("villager.gui.storage.categories"), lore);
	}

	/** Role choice after right-clicking a container in selection mode. */
	static void openRolePicker(ServerPlayer player, Guild guild, BlockPos pos) {
		StorageCategories categories = Storage.categories();
		List<String> names = new ArrayList<>(categories.names());
		int options = Math.min(45, names.size() + 2);
		int rows = Math.min(6, (options + 8) / 9 + 1);
		SimpleGui gui = chest(player, rows, Lang.get("villager.gui.role.title", pos.getX(), pos.getY(), pos.getZ()));
		int slot = 0;
		gui.setSlot(slot++, GuiUtil.item(Items.HOPPER, Lang.get("villager.gui.role.deposit"), GuiUtil.lines("villager.gui.role.deposit.lore"))
				.setCallback((s, type, action, g) -> assign(player, guild, pos, StorageChest.Role.DEPOSIT, "")));
		for (String name : names) {
			if (slot >= options - 1) {
				break;
			}
			gui.setSlot(slot++, GuiUtil.item(Items.CHEST, Lang.get("villager.gui.role.category", name), GuiUtil.lines("villager.gui.role.category.lore"))
					.setCallback((s, type, action, g) -> assign(player, guild, pos, StorageChest.Role.CATEGORY, name)));
		}
		String fallback = categories.fallback();
		gui.setSlot(slot, GuiUtil.item(Items.BARREL, Lang.get("villager.gui.role.category", fallback), GuiUtil.lines("villager.gui.role.fallback.lore"))
				.setCallback((s, type, action, g) -> assign(player, guild, pos, StorageChest.Role.CATEGORY, fallback)));
		gui.setSlot((rows - 1) * 9, GuiUtil.back(() -> openStorage(player, guild, 0)));
		gui.setSlot(rows * 9 - 1, GuiUtil.close());
		GuiUtil.fill(gui);
		gui.open();
	}

	private static void assign(ServerPlayer player, Guild guild, BlockPos pos, StorageChest.Role role, String category) {
		if (!checkPermission(player, guild)) {
			return;
		}
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null || !Storage.isStorageBlock(level.getBlockEntity(pos)) || GuildManager.at(level, pos) != guild) {
			Msg.send(player, "villager.storage.not_territory");
			GuiUtil.errorSound(player);
			player.closeContainer();
			return;
		}
		StorageChest existing = Storage.registered(level, guild, pos);
		if (existing != null) {
			existing.role = role;
			existing.category = category;
			Msg.send(player, "villager.storage.updated", Storage.roleName(existing));
		} else {
			if (guild.villagers.chests.size() >= VillagerModule.config().storage.maxChests) {
				Msg.send(player, "villager.storage.limit", VillagerModule.config().storage.maxChests);
				player.closeContainer();
				return;
			}
			StorageChest chest = new StorageChest(pos.immutable(), role, category);
			guild.villagers.chests.add(chest);
			Msg.send(player, "villager.storage.added", Storage.roleName(chest), pos.getX(), pos.getY(), pos.getZ());
		}
		GuildManager.markDirty();
		GuiUtil.successSound(player);
		openStorage(player, guild, 0);
	}

	// ================================================================ helpers

	/** Chest menu whose clicks are isolated by {@link Safe} (a failing button never breaks the menu). */
	private static SimpleGui chest(ServerPlayer player, int rows, Component title) {
		SimpleGui gui = new SimpleGui(GuiUtil.rows(rows), player, false) {
			@Override
			public boolean click(int index, ClickType type, ContainerInput action) {
				return Safe.get("villager.gui", () -> super.click(index, type, action), false);
			}
		};
		gui.setTitle(title);
		return gui;
	}

	private static boolean checkPermission(ServerPlayer player, Guild guild) {
		if (GuildManager.of(player) != guild || !guild.can(player.getUUID(), GuildPermission.VILLAGERS)) {
			Msg.send(player, "error.no_permission");
			GuiUtil.errorSound(player);
			return false;
		}
		return true;
	}

	private static boolean stillMember(ServerPlayer player, Guild guild, VillagerRecord record) {
		if (guild.villagers.members.get(record.uuid) != record) {
			Msg.send(player, "villager.error.unknown");
			player.closeContainer();
			return false;
		}
		return true;
	}
}
