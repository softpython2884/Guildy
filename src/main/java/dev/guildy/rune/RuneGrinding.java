package dev.guildy.rune;

import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The grindstone and the runes (rune.mixin.GrindstoneMenuMixin): a runed piece alone in the grindstone gives back the same
 * piece without its rune (enchantments, name and durability kept, no experience: nothing was disenchanted); a second use
 * then removes the enchantments as in vanilla. When two pieces are merged (vanilla repair), the result never keeps a rune.
 */
public final class RuneGrinding {
	private RuneGrinding() {
	}

	/**
	 * Result of the grindstone for these inputs when a rune is ground off: the single runed piece without its rune, or null
	 * (vanilla result).
	 */
	public static @Nullable ItemStack runeRemoval(ItemStack input, ItemStack additional) {
		boolean first = !input.isEmpty();
		boolean second = !additional.isEmpty();
		if (first == second) {
			return null;
		}
		ItemStack item = first ? input : additional;
		if (item.getCount() != 1 || !RuneItems.hasRuneTraces(item)) {
			return null;
		}
		return RuneItems.strip(item.copy());
	}

	/** A vanilla result (two pieces merged) never carries a rune. */
	public static ItemStack withoutRune(ItemStack result) {
		return !result.isEmpty() && RuneItems.hasRuneTraces(result) ? RuneItems.strip(result) : result;
	}

	/** The player took the piece without its rune: a word and a sound for the rune that fades. */
	public static void taken(Player player, ItemStack input, ItemStack additional) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		ItemStack item = !input.isEmpty() ? input : additional;
		RuneConfig.RuneDef def = RuneModule.config().rune(RuneItems.idOf(item));
		String name = def == null ? Lang.raw("rune.unknown") : def.tier().color + def.name;
		Msg.send(serverPlayer, "rune.ground", name);
		Msg.sound(serverPlayer, SoundEvents.AMETHYST_BLOCK_BREAK, 1.0F, 0.8F);
	}
}
