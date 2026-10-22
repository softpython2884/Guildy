package dev.guildy.rune;

/**
 * Implemented by the grindstone menu (rune.mixin.GrindstoneMenuMixin): its current result is a rune removal (no
 * experience is given for it: the enchantments stay on the piece).
 */
public interface RuneGrindstone {
	boolean guildy$runeRemoval();
}
