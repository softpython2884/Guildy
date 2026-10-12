package dev.guildy.villager;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import dev.guildy.gui.GuiUtil;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Guard part of the villager menus: role button (Auto → Épéiste → Archer) and equipment display in the detail menu
 * (vanilla clients do not draw armor on villagers), one summary line in the villager list.
 */
final class GuardGui {
	/** Detail menu slots (top row, around the villager icon). */
	static final int ROLE_SLOT = 2;
	static final int EQUIPMENT_SLOT = 6;

	private GuardGui() {
	}

	/** Detail menu of a guard: role and equipment buttons (nothing for other tasks). */
	static void addButtons(SimpleGui gui, ServerPlayer player, Guild guild, VillagerRecord record) {
		if (record.task != Task.GUARD) {
			return;
		}
		VillagerConfig.Guard cfg = VillagerModule.config().guard;
		GuardRole role = Guards.role(guild, record);
		ServerLevel level = GuildyServer.claimLevel();
		Villager villager = level == null ? null : VillagerManager.entity(level, record.uuid);
		gui.setSlot(ROLE_SLOT, roleButton(guild, record, villager, role, cfg).setCallback((slot, type, action, g) -> {
			if (!VillagerGui.checkPermission(player, guild) || !VillagerGui.stillMember(player, guild, record)) {
				return;
			}
			if (record.task == Task.GUARD) {
				Guards.cycleRole(player, guild, record);
				GuiUtil.clickSound(player);
			}
			VillagerGui.openDetail(player, guild, record.uuid);
		}));
		gui.setSlot(EQUIPMENT_SLOT, equipmentIcon(villager, role, cfg).setCallback((slot, type, action, g) -> {
			if (!type.shift || !VillagerGui.checkPermission(player, guild) || !VillagerGui.stillMember(player, guild, record)) {
				return;
			}
			Villager current = Guards.loaded(record);
			if (current == null) {
				Msg.send(player, "villager.call.unloaded", record.name);
				GuiUtil.errorSound(player);
				return;
			}
			if (!GuardEquipment.hasEquipment(current)) {
				Msg.send(player, "villager.guard.equipment.none", record.name);
				GuiUtil.errorSound(player);
				return;
			}
			int given = GuardEquipment.giveAll(player, current);
			GuardEquipment.applyDisplay(current, Guards.role(guild, record));
			Msg.send(player, "villager.guard.equipment.given", record.name, given);
			GuiUtil.successSound(player);
			VillagerGui.openDetail(player, guild, record.uuid);
		}));
	}

	/** Role button, showing the weapon the guard fights with. */
	private static GuiElementBuilder roleButton(Guild guild, VillagerRecord record, @Nullable Villager villager, GuardRole role,
			VillagerConfig.Guard cfg) {
		List<Component> lore = new ArrayList<>(GuiUtil.lines("villager.gui.guard.role.lore", cfg.bowRange, Math.round(cfg.archerShare * 100)));
		lore.add(Component.empty());
		int[] counts = Guards.counts(guild, null);
		lore.add(Lang.get("villager.gui.guard.role.count", counts[0], counts[1]));
		GuardData data = Guards.data(record);
		lore.add(Lang.get(data.autoRole ? "villager.gui.guard.role.setting.auto" : "villager.gui.guard.role.setting.forced"));
		lore.add(Lang.get("villager.gui.guard.role.click"));
		ItemStack icon = GuardEquipment.displayFor(role, villager == null ? ItemStack.EMPTY : GuardEquipment.weapon(villager, role));
		return GuiUtil.item(icon, Lang.get("villager.gui.guard.role", Guards.roleName(record)), lore);
	}

	private static GuiElementBuilder equipmentIcon(@Nullable Villager villager, GuardRole role, VillagerConfig.Guard cfg) {
		List<Component> lore = new ArrayList<>();
		ItemStack icon = new ItemStack(Items.IRON_CHESTPLATE);
		if (villager == null) {
			lore.add(Lang.get("villager.gui.guard.unloaded"));
		} else {
			lore.add(Lang.get("villager.gui.guard.health", GuardRules.displayHealth(villager.getHealth()),
					GuardRules.displayHealth(villager.getMaxHealth())));
			lore.add(Lang.get("villager.gui.guard.armor", villager.getArmorValue(), Math.round(villager.getAttributeValue(Attributes.ARMOR_TOUGHNESS))));
			for (EquipmentSlot slot : GuardEquipment.ARMOR) {
				lore.add(Lang.get(Guards.slotKey(slot)).append(describe(villager.getItemBySlot(slot))));
			}
			ItemStack weapon = GuardEquipment.weapon(villager, role);
			MutableComponent weaponText = weapon.isEmpty()
					? itemName(Guards.roleIcon(role)).append(Lang.get("villager.gui.guard.slot.default"))
					: describe(weapon);
			lore.add(Lang.get(Guards.slotKey(GuardEquipment.WEAPON)).append(weaponText));
			ItemStack chest = villager.getItemBySlot(EquipmentSlot.CHEST);
			if (!chest.isEmpty()) {
				icon = chest.copy();
			}
		}
		lore.add(Component.empty());
		lore.addAll(GuiUtil.lines("villager.gui.guard.equipment.lore"));
		if (cfg.weaponsFromChests) {
			lore.addAll(GuiUtil.lines("villager.gui.guard.equipment.weapons"));
		}
		if (!cfg.equipmentFromChests && !cfg.weaponsFromChests) {
			lore.add(Lang.get("villager.gui.guard.equipment.off"));
		}
		lore.addAll(GuiUtil.lines("villager.gui.guard.equipment.retreat", Math.round(cfg.retreatHealthRatio * 100),
				Math.round(cfg.resumeHealthRatio * 100)));
		lore.add(Component.empty());
		lore.add(Lang.get("villager.gui.guard.equipment.take"));
		return GuiUtil.item(icon, Lang.get("villager.gui.guard.equipment"), lore);
	}

	/** "Casque en fer (85 %)" (item name translated by the client), "—" for an empty slot. */
	private static MutableComponent describe(ItemStack stack) {
		if (stack.isEmpty()) {
			return Lang.get("villager.gui.guard.slot.empty");
		}
		MutableComponent text = itemName(stack);
		if (stack.isDamageableItem()) {
			int percent = (int) Math.round(100.0 * (stack.getMaxDamage() - stack.getDamageValue()) / Math.max(1, stack.getMaxDamage()));
			text.append(Lang.get("villager.gui.guard.slot.worn", percent));
		}
		return text;
	}

	private static MutableComponent itemName(ItemStack stack) {
		return Component.empty().append(stack.getHoverName().copy().withStyle(ChatFormatting.WHITE));
	}

	/** Icon of a villager in the menus: the task icon, a bow for archers. */
	static Item icon(VillagerRecord record) {
		GuardData data = record.guard;
		return record.task == Task.GUARD && data != null && data.role == GuardRole.BOW ? Items.BOW : record.task.icon();
	}

	/** One line of the villager list for a guard: role, armor, health. */
	static void addLore(List<Component> lore, VillagerRecord record, @Nullable Villager villager) {
		if (record.task != Task.GUARD) {
			return;
		}
		String role = Guards.roleName(record);
		if (villager == null) {
			lore.add(Lang.get("villager.gui.lore.guard_unloaded", role));
		} else {
			lore.add(Lang.get("villager.gui.lore.guard", role, villager.getArmorValue(), GuardRules.displayHealth(villager.getHealth()),
					GuardRules.displayHealth(villager.getMaxHealth())));
		}
	}
}
