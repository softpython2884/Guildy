package dev.guildy.scroll;

import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What a player does with a grimoire: right click casts the selected page (through {@link ScrollCasting}: same targeting
 * and animation as the scroll, short channel, food instead of the scroll), sneak + right click turns to the next written
 * page, right-clicking a scroll onto the grimoire in a container (or the grimoire onto a scroll, like a bundle) fuses it,
 * sneak + swap hands opens the pages menu. An empty grimoire opens its menu on right click.
 */
public final class Grimoires {
	private Grimoires() {
	}

	// ================================================================== right click

	/** Right click with a grimoire in {@code hand} (UseItemCallback, server side). */
	static InteractionResult use(ServerPlayer player, InteractionHand hand) {
		ItemStack stack = refreshedInHand(player, hand);
		if (player.isUsingItem()) {
			return InteractionResult.PASS;
		}
		GrimoireRules.Pages pages = GrimoireItems.pages(stack);
		if (pages.filled() == 0) {
			ScrollCasting.syncUsing(player);
			Msg.actionBar(player, "grimoire.empty");
			openMenuLater(player, stack);
			return InteractionResult.FAIL;
		}
		if (player.isSecondaryUseActive()) {
			turnPage(player, stack);
			ScrollCasting.syncUsing(player);
			return InteractionResult.SUCCESS_SERVER;
		}
		ScrollConfig cfg = ScrollModule.config();
		if (!cfg.grimoire.enabled) {
			return ScrollCasting.refuse(player, "grimoire.fail.disabled");
		}
		ScrollConfig.ScrollDef def = GrimoireItems.selectedDef(pages);
		if (def == null) {
			return ScrollCasting.refuse(player, "grimoire.fail.unreadable", pages.selectedSpell());
		}
		return ScrollCasting.begin(player, hand, stack, def, true);
	}

	/** The grimoire in hand, completed / brought up to date first (replaced in hand when its base item changed). */
	private static ItemStack refreshedInHand(ServerPlayer player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		ItemStack fresh = GrimoireItems.refreshed(stack);
		if (fresh != null && fresh != stack) {
			player.setItemInHand(hand, fresh);
			return fresh;
		}
		return stack;
	}

	/** Sneak + right click: the next written page is selected (name, action bar, page sound, a flourish of its colors). */
	static void turnPage(ServerPlayer player, ItemStack stack) {
		ScrollConfig cfg = ScrollModule.config();
		GrimoireRules.Pages pages = GrimoireItems.pages(stack);
		int next = GrimoireRules.next(pages, id -> cfg.scroll(id) != null);
		if (next < 0) {
			Msg.actionBar(player, "grimoire.page.none");
			Msg.sound(player, SoundEvents.VILLAGER_NO, 0.6F, 1.2F);
			return;
		}
		GrimoireRules.Pages turned = pages.select(next);
		GrimoireItems.apply(stack, turned);
		ScrollConfig.ScrollDef def = GrimoireItems.selectedDef(turned);
		if (def == null) {
			return;
		}
		Msg.actionBar(player, Lang.get("grimoire.page.turned", GrimoireItems.name(turned).getString(), GrimoireItems.foodCost(def)));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F,
				0.9F + 0.1F * next);
		flourish(player, def.spell());
	}

	/** A small swirl of the spell's colors around the book. */
	private static void flourish(ServerPlayer player, Spell spell) {
		Fx fx = new Fx(player.level(), player.position(), 60, 32);
		Vec3 look = player.getLookAngle();
		Vec3 book = player.position().add(look.x * 0.6, 1.25, look.z * 0.6);
		for (int i = 0; i < 10; i++) {
			double angle = i * Math.PI / 5;
			Vec3 offset = new Vec3(Math.cos(angle) * 0.35, Math.sin(angle) * 0.25 + 0.1, Math.sin(angle) * 0.35);
			fx.dot(Fx.dust(i % 2 == 0 ? spell.color : spell.accent, 0.7F), book.add(offset));
		}
		fx.burst(ParticleTypes.ENCHANT, book, 12, 0.3, 0.6);
		fx.flush();
	}

	// ================================================================== menu

	/** Opens the pages menu at the next tick (never from inside a click or a use being processed). */
	static void openMenuLater(ServerPlayer player, ItemStack stack) {
		Scheduler.later("scroll.grimoire.menu", 1, () -> Safe.run("scroll.grimoire.menu", () -> {
			if (!player.isRemoved() && player.isAlive()) {
				GrimoireMenu.open(player, stack);
			}
		}));
	}

	/**
	 * Swap hands key pressed: while sneaking with a grimoire in a hand, opens its menu instead of swapping.
	 *
	 * @return true when the swap must not happen
	 */
	public static boolean onSwapHands(ServerPlayer player) {
		if (!player.isShiftKeyDown() || player.isSpectator()) {
			return false;
		}
		ItemStack main = player.getMainHandItem();
		ItemStack stack = GrimoireItems.isGrimoire(main) ? main : player.getOffhandItem();
		if (!GrimoireItems.isGrimoire(stack)) {
			return false;
		}
		GrimoireMenu.open(player, stack);
		return true;
	}

	/** The grimoire held in the main hand, else the off hand, or null. */
	static @Nullable ItemStack held(Player player) {
		if (GrimoireItems.isGrimoire(player.getMainHandItem())) {
			return player.getMainHandItem();
		}
		return GrimoireItems.isGrimoire(player.getOffhandItem()) ? player.getOffhandItem() : null;
	}

	/** True while {@code stack} (this very stack) is in the player's inventory (hands and armor included). */
	static boolean carries(Player player, ItemStack stack) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (inventory.getItem(slot) == stack) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== fusion

	/**
	 * A scroll carried by the cursor right-clicked onto a grimoire in a slot ({@code ItemStack.overrideOtherStackedOnMe}).
	 *
	 * @return true when handled (the vanilla click is skipped)
	 */
	public static boolean scrollOntoGrimoire(ItemStack grimoire, ItemStack scroll, Slot slot, ClickAction action, Player player, SlotAccess carried) {
		if (action != ClickAction.SECONDARY || !(player instanceof ServerPlayer serverPlayer) || !GrimoireItems.isGrimoire(grimoire)
				|| !ScrollItems.isScroll(scroll) || !slot.allowModification(player)) {
			return false;
		}
		String id = ScrollItems.idOf(scroll);
		boolean added = fuse(serverPlayer, grimoire, id);
		if (added) {
			scroll.shrink(1);
			if (scroll.isEmpty()) {
				carried.set(ItemStack.EMPTY);
			}
		}
		return true;
	}

	/**
	 * A grimoire carried by the cursor right-clicked onto a scroll in a slot ({@code ItemStack.overrideStackedOnOther}): one
	 * scroll of the slot is fused.
	 *
	 * @return true when handled (the vanilla click is skipped)
	 */
	public static boolean grimoireOntoScroll(ItemStack grimoire, Slot slot, ClickAction action, Player player) {
		ItemStack scroll = slot.getItem();
		if (action != ClickAction.SECONDARY || !(player instanceof ServerPlayer serverPlayer) || !GrimoireItems.isGrimoire(grimoire)
				|| !ScrollItems.isScroll(scroll) || !slot.allowModification(player)) {
			return false;
		}
		String id = ScrollItems.idOf(scroll);
		GrimoireRules.FuseResult preview = GrimoireRules.fuse(GrimoireItems.pages(grimoire), id);
		if (preview.outcome() == GrimoireRules.Fuse.ADDED && slot.safeTake(1, 1, player).isEmpty()) {
			return true;
		}
		fuse(serverPlayer, grimoire, id);
		return true;
	}

	/**
	 * Fuses a scroll id into a grimoire (in place): message, sounds and particles. The caller consumes one scroll when this
	 * returns true.
	 */
	static boolean fuse(ServerPlayer player, ItemStack grimoire, @Nullable String id) {
		ScrollConfig.ScrollDef def = id == null ? null : ScrollModule.config().scroll(id);
		if (def == null) {
			Msg.send(player, "grimoire.fuse.unknown");
			failSound(player);
			return false;
		}
		GrimoireRules.FuseResult result = GrimoireRules.fuse(GrimoireItems.pages(grimoire), def.id);
		switch (result.outcome()) {
			case ADDED -> {
				GrimoireItems.apply(grimoire, result.pages());
				Msg.send(player, "grimoire.fuse.added", GrimoireItems.spellName(def), result.page() + 1, GrimoireRules.PAGES);
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8F,
						1.2F);
				flourish(player, def.spell());
				return true;
			}
			case DUPLICATE -> Msg.send(player, "grimoire.fuse.duplicate", GrimoireItems.spellName(def));
			case FULL -> Msg.send(player, "grimoire.fuse.full", GrimoireRules.PAGES);
			default -> Msg.send(player, "grimoire.fuse.unknown");
		}
		failSound(player);
		return false;
	}

	private static void failSound(ServerPlayer player) {
		Msg.sound(player, SoundEvents.VILLAGER_NO, 0.6F, 1.2F);
	}
}
