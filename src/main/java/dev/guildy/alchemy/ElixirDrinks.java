package dev.guildy.alchemy;

import dev.guildy.core.Safe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * A player finished drinking (mixin on {@code Consumable.onConsume}, before the item is used up): an enabled magical elixir
 * starts its boost. The vanilla effects of the bottle are applied by the game itself.
 */
public final class ElixirDrinks {
	private ElixirDrinks() {
	}

	public static void drunk(ServerPlayer player, ItemStack stack) {
		String id = Elixirs.idOf(stack);
		if (id == null) {
			return;
		}
		Safe.run("alchemy.elixir.drink", () -> {
			AlchemyConfig.ElixirDef def = AlchemyModule.config().elixir(id);
			if (def != null) {
				MagicBoosts.drink(player, def);
			}
		});
	}
}
