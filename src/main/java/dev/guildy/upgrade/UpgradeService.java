package dev.guildy.upgrade;

import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import dev.guildy.shop.PlainItems;
import dev.guildy.structure.TextUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Unlocking upgrades (menu and admin commands). Everything is re-checked on the server thread at each call and a payment
 * is atomic: the items are taken first, then the treasury gold; the items are given back if the gold cannot be taken.
 */
public final class UpgradeService {
	private record Taken(Item item, int count) {
	}

	private UpgradeService() {
	}

	/** Plain items of this id carried by the player (main inventory + off hand, never special or modified items). */
	public static int owned(ServerPlayer player, String itemId) {
		Item item = ItemUtil.item(itemId);
		return item == null ? 0 : PlainItems.count(player, stack -> stack.is(item));
	}

	/** Why the player cannot unlock the node right now, or null when everything is fine. */
	public static @Nullable Component problem(ServerPlayer player, Guild guild, UpgradeTree.Node node) {
		if (GuildManager.get(guild.id) != guild || GuildManager.of(player) != guild) {
			return Lang.get("error.no_guild");
		}
		if (!guild.can(player.getUUID(), GuildPermission.TREASURY)) {
			return Lang.get("error.no_permission");
		}
		Set<String> unlocked = guild.upgrades.unlocked;
		UpgradeRules.Problem problem = UpgradeRules.check(node, unlocked, guild.level, guild.treasury, item -> owned(player, item));
		boolean costsLeft = problem == UpgradeRules.Problem.NONE || problem == UpgradeRules.Problem.GOLD || problem == UpgradeRules.Problem.ITEMS;
		if (costsLeft && player.isCreative() && !node.costItems().isEmpty()) {
			// Creative players could spawn the materials (same rule as quest deliveries and shop sales).
			return Lang.get("upgrade.error.creative");
		}
		return switch (problem) {
			case NONE -> null;
			case ALREADY_UNLOCKED -> Lang.get("upgrade.error.already", node.name());
			case LEVEL -> Lang.get("upgrade.error.level", node.minLevel(), guild.level);
			case REQUIREMENTS -> Lang.get("upgrade.error.requires", names(UpgradeRules.missingRequirements(node, unlocked)));
			case GOLD -> Lang.get("upgrade.error.gold", node.costGold(), guild.treasury);
			case ITEMS -> TextUtil.lang("upgrade.error.items", itemList(UpgradeRules.missingItems(node, item -> owned(player, item))));
		};
	}

	/**
	 * Unlocks a node for the guild, paid with the treasury and the player's inventory (TREASURY permission).
	 *
	 * @return the error, or null on success (the guild is told)
	 */
	public static @Nullable Component unlock(ServerPlayer player, Guild guild, String nodeId) {
		UpgradeTree.Node node = Upgrades.tree().node(nodeId);
		if (node == null) {
			return Lang.get("upgrade.error.unknown", nodeId);
		}
		Component problem = problem(player, guild, node);
		if (problem != null) {
			return problem;
		}
		List<Taken> taken = new ArrayList<>();
		for (UpgradeTree.Cost cost : node.costItems()) {
			Item item = ItemUtil.item(cost.item());
			if (item == null || !PlainItems.remove(player, stack -> stack.is(item), cost.count())) {
				refund(player, taken);
				return Lang.get("upgrade.error.payment");
			}
			taken.add(new Taken(item, cost.count()));
		}
		if (!GuildManager.takeTreasury(guild, node.costGold())) {
			refund(player, taken);
			return Lang.get("upgrade.error.gold", node.costGold(), guild.treasury);
		}
		guild.upgrades.unlocked.add(node.id());
		GuildManager.markDirty();
		Msg.guild(guild, "upgrade.unlocked", player.getName().getString(), node.name(), effectsSummary(node));
		Msg.guildTitle(guild, Lang.get("upgrade.unlocked.title"), Lang.get("upgrade.unlocked.subtitle", node.name()));
		Msg.guildSound(guild, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
		return null;
	}

	/** Admin: unlocks the node and its missing requirements, for free. @return names of the nodes newly unlocked */
	public static List<String> adminUnlock(Guild guild, String nodeId) {
		UpgradeTree tree = Upgrades.tree();
		List<String> added = new ArrayList<>();
		for (String id : tree.withRequirements(nodeId)) {
			UpgradeTree.Node node = tree.node(id);
			if (node != null && guild.upgrades.unlocked.add(id)) {
				added.add(node.name());
			}
		}
		if (!added.isEmpty()) {
			GuildManager.markDirty();
			Msg.guild(guild, "upgrade.admin.notify.unlocked", String.join(", ", added));
		}
		return added;
	}

	/** Admin: removes the node and every unlocked node depending on it. @return names of the removed nodes */
	public static List<String> adminLock(Guild guild, String nodeId) {
		UpgradeTree tree = Upgrades.tree();
		Set<String> ids = tree.node(nodeId) != null ? tree.withDependents(nodeId, guild.upgrades.unlocked) : Set.of(nodeId);
		List<String> removed = new ArrayList<>();
		for (String id : ids) {
			if (guild.upgrades.unlocked.remove(id)) {
				UpgradeTree.Node node = tree.node(id);
				removed.add(node != null ? node.name() : id);
			}
		}
		if (!removed.isEmpty()) {
			GuildManager.markDirty();
			Msg.guild(guild, "upgrade.admin.notify.locked", String.join(", ", removed));
		}
		return removed;
	}

	/** "+25 % d'intégrité du cœur, ..." */
	static String effectsSummary(UpgradeTree.Node node) {
		return node.effects().entrySet().stream()
				.map(entry -> Upgrades.describe(entry.getKey(), entry.getValue()))
				.collect(Collectors.joining("&7, &f"));
	}

	/** Display names of node ids, comma separated. */
	static String names(List<String> ids) {
		List<String> names = new ArrayList<>();
		for (String id : ids) {
			UpgradeTree.Node node = Upgrades.tree().node(id);
			names.add(node != null ? node.name() : id);
		}
		return String.join(", ", names);
	}

	/** "3 x Iron Ingot, 2 x Diamond" with item names translated client-side. */
	static Component itemList(Map<String, Integer> counts) {
		MutableComponent out = Component.empty();
		boolean first = true;
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			Item item = ItemUtil.item(entry.getKey());
			if (!first) {
				out.append(Component.literal(", "));
			}
			first = false;
			out.append(TextUtil.lang("upgrade.error.items.entry", entry.getValue(), item == null ? entry.getKey() : ItemUtil.name(item)));
		}
		return out;
	}

	private static void refund(ServerPlayer player, List<Taken> taken) {
		for (Taken entry : taken) {
			ItemUtil.giveOrDrop(player, entry.item(), entry.count());
		}
	}
}
