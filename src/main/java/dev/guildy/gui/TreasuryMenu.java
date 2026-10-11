package dev.guildy.gui;

import dev.guildy.command.GuildActions;
import dev.guildy.command.GuildActions.Denial;
import dev.guildy.core.GoldUtil;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildPermission;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Treasury: balance, deposits (anyone) and withdrawals (TREASURY permission). Gold moves only through
 * {@link GuildActions} (take before give, refund on failure); the icons are virtual items.
 */
final class TreasuryMenu extends GuildGui {
	private static final int[] AMOUNTS = {1, 10, 64};

	TreasuryMenu(ServerPlayer player, Guild guild) {
		super(player, 4, guild);
	}

	@Override
	protected Component title(@Nullable Guild guild) {
		return Lang.get("treasury.title");
	}

	/** Nuggets that "deposit all" would convert (0 when disabled in the config). */
	private int nuggets() {
		return GuildyServer.config().economy.depositNuggets ? ItemUtil.count(player, Items.GOLD_NUGGET) : 0;
	}

	private @Nullable Denial depositDenial(int amount) {
		return GoldUtil.count(player) < amount ? new Denial("error.not_enough_gold", amount) : null;
	}

	private @Nullable Denial depositAllDenial() {
		return GoldUtil.count(player) > 0 || nuggets() >= 9 ? null : new Denial("treasury.deposit.nothing");
	}

	@Override
	protected void build(@Nullable Guild guild) {
		if (guild == null) {
			return;
		}
		int carried = GoldUtil.count(player);
		List<Component> balanceLore = new ArrayList<>(GuiUtil.lines("treasury.balance.lore", carried));
		if (!guild.can(player.getUUID(), GuildPermission.TREASURY)) {
			balanceLore.addAll(GuiUtil.lines("treasury.balance.no_withdraw"));
		}
		setSlot(4, icon(Items.GOLD_BLOCK, "treasury.balance.name", balanceLore, guild.treasury));

		// Deposits on the left (row 2, columns 2-4), withdrawals on the right (columns 6-8).
		for (int i = 0; i < AMOUNTS.length; i++) {
			int amount = AMOUNTS[i];
			action(10 + i, icon(new ItemStack(Items.GOLD_INGOT, amount), Lang.get("treasury.deposit.name", amount),
							GuiUtil.lines("treasury.deposit.lore")),
					() -> depositDenial(amount), () -> GuildActions.deposit(player, amount));
			action(14 + i, icon(new ItemStack(Items.GOLD_INGOT, amount), Lang.get("treasury.withdraw.name", amount),
							GuiUtil.lines("treasury.withdraw.lore")),
					() -> GuildActions.withdrawDenial(player, amount), () -> GuildActions.withdraw(player, amount));
		}
		int nuggets = nuggets();
		List<Component> allLore = nuggets >= 9 ? GuiUtil.lines("treasury.deposit_all.lore_nuggets", carried, nuggets / 9)
				: GuiUtil.lines("treasury.deposit_all.lore", carried);
		action(20, icon(Items.CHEST, "treasury.deposit_all.name", allLore), this::depositAllDenial, () -> GuildActions.deposit(player, 0));
		bottomBar(() -> new MainMenu(player, guild).show());
	}
}
