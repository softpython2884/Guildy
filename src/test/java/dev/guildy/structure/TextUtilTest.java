package dev.guildy.structure;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextUtilTest {

	@Test
	void componentArgumentsKeepTheActiveColor() {
		MutableComponent out = TextUtil.format("§7Cible : {0}, reste {1}", Component.literal("Bûche"), 5);
		assertEquals("§7Cible : Bûche§7, reste 5", out.getString());
		// Siblings: "§7Cible : ", [gray]("Bûche"), "§7, reste 5"
		assertEquals(3, out.getSiblings().size());
		assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GRAY), out.getSiblings().get(1).getStyle().getColor());
	}

	@Test
	void formatsAndColorReset() {
		MutableComponent bold = TextUtil.format("§6§l{0}", Component.literal("X"));
		Component inserted = bold.getSiblings().get(0);
		assertTrue(inserted.getStyle().isBold());
		assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), inserted.getStyle().getColor());
		// A color code resets the formats, like the vanilla legacy renderer.
		MutableComponent reset = TextUtil.format("§l§a{0}", Component.literal("Y"));
		assertTrue(!reset.getSiblings().get(0).getStyle().isBold());
	}

	@Test
	void textArgumentsAreInlinedAndColorized() {
		MutableComponent out = TextUtil.format("§e{0} or ({1})", 12, "&cnon");
		assertEquals("§e12 or (§cnon)", out.getString());
		assertEquals(1, out.getSiblings().size());
		// Unknown placeholders stay as text.
		assertEquals("a {5} b", TextUtil.format("a {5} b", 1).getString());
	}
}
