package dev.guildy.quest;

import dev.guildy.core.Safe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Entry points of the quest mixins ({@code dev.guildy.quest.mixin}): vanilla actions without a Fabric API event. Each
 * call returns at once unless the player is a member (not in creative / spectator mode) whose guild has an active quest
 * of that type; the work is isolated by {@link Safe}.
 */
public final class QuestHooks {
	private QuestHooks() {
	}

	/** Items taken out of a crafting result slot (crafting table or inventory grid): {@code count} items crafted. */
	public static void crafted(Player player, ItemStack result, int count) {
		if (count > 0 && player instanceof ServerPlayer serverPlayer && QuestManager.wants(serverPlayer, QuestType.CRAFT) && !result.isEmpty()) {
			Safe.run("quest.craft", () -> QuestManager.onAction(serverPlayer, QuestType.CRAFT,
					(quest, target) -> target != null && target.matches(result) ? count : 0));
		}
	}

	/** Items taken out of the result slot of a furnace, smoker or blast furnace. */
	public static void smelted(Player player, ItemStack result, int count) {
		if (count > 0 && player instanceof ServerPlayer serverPlayer && QuestManager.wants(serverPlayer, QuestType.SMELT) && !result.isEmpty()) {
			Safe.run("quest.smelt", () -> QuestManager.onAction(serverPlayer, QuestType.SMELT,
					(quest, target) -> target != null && target.matches(result) ? count : 0));
		}
	}

	/** A fishing rod reeled in a catch (an empty list = an entity was hooked: not a catch). */
	public static void fished(ServerPlayer player, @Nullable Collection<ItemStack> items) {
		if (items == null || items.isEmpty() || !QuestManager.wants(player, QuestType.FISH)) {
			return;
		}
		List<ItemStack> catches = new ArrayList<>(items);
		Safe.run("quest.fish", () -> QuestManager.onAction(player, QuestType.FISH, (quest, target) -> {
			if (target == null) {
				return 1; // any catch
			}
			int count = 0;
			for (ItemStack stack : catches) {
				if (stack != null && target.matches(stack)) {
					count += stack.getCount();
				}
			}
			return count;
		}));
	}

	/** Two animals bred by the player (turtles lay eggs: no child yet, the parent's type counts). */
	public static void bred(ServerPlayer player, Animal parent, @Nullable AgeableMob child) {
		if (!QuestManager.wants(player, QuestType.BREED)) {
			return;
		}
		Entity baby = child != null ? child : parent;
		Safe.run("quest.breed", () -> QuestManager.onAction(player, QuestType.BREED,
				(quest, target) -> target == null || target.matches(baby) ? 1 : 0));
	}

	/** An animal tamed (wolves, cats, parrots...) or a horse-like mount broken in by the player. */
	public static void tamed(ServerPlayer player, Animal animal) {
		if (QuestManager.wants(player, QuestType.TAME)) {
			Safe.run("quest.tame", () -> QuestManager.onAction(player, QuestType.TAME,
					(quest, target) -> target == null || target.matches(animal) ? 1 : 0));
		}
	}

	/** One trade with a villager or a wandering trader ({@code result} = the item the player received). */
	public static void traded(ServerPlayer player, @Nullable ItemStack result) {
		if (QuestManager.wants(player, QuestType.TRADE)) {
			ItemStack received = result == null ? ItemStack.EMPTY : result;
			Safe.run("quest.trade", () -> QuestManager.onAction(player, QuestType.TRADE,
					(quest, target) -> target == null || target.matches(received) ? 1 : 0));
		}
	}

	/** One item enchanted at an enchanting table ({@code item} = the enchanted result, books become enchanted books). */
	public static void enchanted(ServerPlayer player, @Nullable ItemStack item) {
		if (QuestManager.wants(player, QuestType.ENCHANT)) {
			ItemStack enchanted = item == null ? ItemStack.EMPTY : item;
			Safe.run("quest.enchant", () -> QuestManager.onAction(player, QuestType.ENCHANT,
					(quest, target) -> target == null || target.matches(enchanted) ? 1 : 0));
		}
	}
}
