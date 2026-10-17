package dev.guildy.ruin;

import dev.guildy.Guildy;
import dev.guildy.core.GuildyItems;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.trade.TradeRolls;
import dev.guildy.unique.UniqueVisuals;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The "Parchemin d'incantation": a vanilla item with components (custom_data {"guildy": {"kind": "ruin_summon", "sig": ...}},
 * item name, lore, glint, max stack size, a consumable component for the vanilla channel animation of the right click,
 * silent and never eaten: the ritual is decided by {@link Incantations}) and, only when a resource pack is guaranteed,
 * the item model {@code guildy:parchemin_incantation}.
 *
 * <p>A stack carrying only the custom data (a loot table entry, an old scroll after a config change) is rebuilt as soon as
 * it is seen in a player's inventory: {@code sig} records what the stack was built from.
 */
public final class IncantationItems {
	public static final String KIND = "ruin_summon";
	/** Item model of the texture pack (assets/guildy/items/parchemin_incantation.json). */
	public static final Identifier MODEL = Identifier.fromNamespaceAndPath(Guildy.MOD_ID, "parchemin_incantation");
	private static final String SIGNATURE = "sig";
	private static final Holder<SoundEvent> SILENT = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY);
	private static @Nullable Integer signature;

	private IncantationItems() {
	}

	// ================================================================== creation

	/** {@code count} incantation scrolls (capped by the max stack size). */
	public static ItemStack create(int count) {
		RuinConfig.SummonSettings cfg = RuinModule.config().summon;
		Item item = baseItem();
		ItemStack stack = new ItemStack(item);
		CompoundTag extra = new CompoundTag();
		extra.putInt(SIGNATURE, signature());
		GuildyItems.tag(stack, KIND, extra);
		int max = stack.has(DataComponents.MAX_DAMAGE) ? 1 : cfg.maxStack;
		stack.set(DataComponents.MAX_STACK_SIZE, max);
		stack.setCount(Math.max(1, Math.min(max, count)));
		stack.set(DataComponents.ITEM_NAME, name());
		stack.set(DataComponents.RARITY, Rarity.EPIC);
		stack.set(DataComponents.LORE, new ItemLore(lore()));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		stack.set(DataComponents.CONSUMABLE, Consumable.builder()
				.consumeSeconds((float) cfg.channelSeconds)
				.animation(ItemUseAnimation.TOOT_HORN)
				.sound(SILENT)
				.hasConsumeParticles(false)
				.build());
		stack.set(DataComponents.USE_EFFECTS, new UseEffects(false, false, 0.35F));
		if (stack.getPrototype().has(DataComponents.PROVIDES_BANNER_PATTERNS)) {
			// Base banner pattern: no pattern line in the tooltip, not usable in a loom.
			stack.remove(DataComponents.PROVIDES_BANNER_PATTERNS);
		}
		if (UniqueVisuals.enabled()) {
			stack.set(DataComponents.ITEM_MODEL, MODEL);
		}
		return stack;
	}

	/** {@code count} scrolls split into full stacks. */
	public static List<ItemStack> stacks(int count) {
		List<ItemStack> list = new ArrayList<>();
		int left = Math.max(0, count);
		while (left > 0) {
			ItemStack stack = create(left);
			if (stack.isEmpty()) {
				break;
			}
			list.add(stack);
			left -= stack.getCount();
		}
		return list;
	}

	/** Gives {@code count} scrolls (whatever does not fit is dropped at the player's feet). */
	public static void give(ServerPlayer player, int count) {
		for (ItemStack stack : stacks(count)) {
			ItemUtil.giveOrDrop(player, stack);
		}
	}

	private static Item baseItem() {
		Item item = ItemUtil.item(RuinModule.config().summon.item);
		return item != null ? item : Items.SKULL_BANNER_PATTERN;
	}

	public static Component name() {
		return Component.literal(Lang.raw("ruin.summon.item.name")).withStyle(style -> style.withItalic(false));
	}

	/** What it does, where it works, the blood price and how to read it. */
	static List<Component> lore() {
		RuinConfig.SummonSettings cfg = RuinModule.config().summon;
		List<Component> lines = new ArrayList<>();
		lines.add(Lang.get("ruin.summon.item.header"));
		lines.add(Component.empty());
		for (String line : Lang.raw("ruin.summon.item.story").split("\n")) {
			lines.add(Component.literal(line));
		}
		lines.add(Component.empty());
		if (cfg.drainHealth > 0) {
			lines.add(Lang.get("ruin.summon.item.price", hearts(cfg.drainHealth)));
			lines.add(Lang.get("ruin.summon.item.price.min", hearts(cfg.minHealth)));
		} else {
			lines.add(Lang.get("ruin.summon.item.price.none"));
		}
		lines.add(Component.empty());
		lines.add(Lang.get("ruin.summon.item.use", number(cfg.channelSeconds), cfg.arenaRadius));
		lines.add(Lang.get("ruin.summon.item.ritual", number(cfg.ritualSeconds)));
		lines.add(Lang.get("ruin.summon.item.dormant"));
		lines.add(Lang.get("ruin.summon.item.single_use"));
		List<Component> out = new ArrayList<>(lines.size());
		for (Component line : lines) {
			out.add(Component.empty().append(line).withStyle(style -> style.withItalic(false)));
		}
		return out;
	}

	/** Health points as hearts ("2", "1,5"). */
	static String hearts(double health) {
		return number(health / 2.0);
	}

	/** "7", "1,5" (French decimal comma, at most one decimal). */
	static String number(double value) {
		double rounded = Math.round(value * 10) / 10.0;
		if (rounded == Math.rint(rounded)) {
			return Long.toString((long) rounded);
		}
		return String.format(Locale.ROOT, "%.1f", rounded).replace('.', ',');
	}

	// ================================================================== queries

	public static boolean is(ItemStack stack) {
		return !stack.isEmpty() && stack.has(DataComponents.CUSTOM_DATA) && GuildyItems.is(stack, KIND);
	}

	// ================================================================== refresh

	/** What a stack records: the settings and texts it shows, the model mode (cached until the next reload). */
	static int signature() {
		Integer sig = signature;
		if (sig == null) {
			RuinConfig.SummonSettings cfg = RuinModule.config().summon;
			sig = Objects.hash(cfg.item, cfg.maxStack, cfg.channelSeconds, cfg.ritualSeconds, cfg.arenaRadius, cfg.drainHealth, cfg.minHealth,
					UniqueVisuals.enabled(), String.join("|", Lang.raw("ruin.summon.item.name"), Lang.raw("ruin.summon.item.header"),
							Lang.raw("ruin.summon.item.story"), Lang.raw("ruin.summon.item.price", ""), Lang.raw("ruin.summon.item.price.min", ""),
							Lang.raw("ruin.summon.item.price.none"), Lang.raw("ruin.summon.item.use", "", ""), Lang.raw("ruin.summon.item.ritual", ""),
							Lang.raw("ruin.summon.item.dormant"),
							Lang.raw("ruin.summon.item.single_use")));
			signature = sig;
		}
		return sig;
	}

	/** Config or lang reloaded: stacks are compared to a fresh signature. */
	static void clearSignature() {
		signature = null;
	}

	/**
	 * The rebuilt stack (same count, capped by the max stack size) when {@code stack} is an incantation scroll built from
	 * other settings or another base item (custom_data only from a loot table, config changed), else null.
	 */
	static @Nullable ItemStack refreshed(ItemStack stack) {
		if (!is(stack)) {
			return null;
		}
		int sig = GuildyItems.data(stack).getIntOr(SIGNATURE, 0);
		if (sig == signature() && stack.is(baseItem())) {
			return null;
		}
		ItemStack fresh = create(stack.getCount());
		return fresh.isEmpty() ? null : fresh;
	}

	/** Rebuilds the incomplete / outdated incantation scrolls of a player's inventory (never the one being used). */
	static void scan(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		ItemStack using = player.isUsingItem() ? player.getUseItem() : ItemStack.EMPTY;
		boolean changed = false;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || stack == using || !stack.has(DataComponents.CUSTOM_DATA)) {
				continue;
			}
			ItemStack fresh = refreshed(stack);
			if (fresh == null) {
				continue;
			}
			inventory.setItem(slot, fresh);
			int extra = stack.getCount() - fresh.getCount();
			if (extra > 0) {
				// More than the max stack size: the rest goes back to the player (or at their feet).
				give(player, extra);
			}
			changed = true;
		}
		if (changed) {
			inventory.setChanged();
		}
	}

	// ================================================================== trade

	/**
	 * Sometimes an incantation scroll at the trade hub merchant (from {@code summon.tradeTier}, chance
	 * {@code summon.tradeChance}, price {@code summon.tradePrice} gold).
	 */
	public static void addTradeOffer(MerchantOffers offers, int hubTier) {
		RuinConfig.SummonSettings cfg = RuinModule.config().summon;
		if (cfg.tradeTier <= 0 || hubTier < cfg.tradeTier || ThreadLocalRandom.current().nextDouble() >= cfg.tradeChance) {
			return;
		}
		ItemStack stack = create(1);
		int[] split = TradeRolls.priceSplit(cfg.tradePrice);
		ItemCost costA = split[0] > 0 ? new ItemCost(Items.GOLD_BLOCK, split[0]) : new ItemCost(Items.GOLD_INGOT, split[1]);
		Optional<ItemCost> costB = split[0] > 0 && split[1] > 0 ? Optional.of(new ItemCost(Items.GOLD_INGOT, split[1])) : Optional.empty();
		// A few uses: the merchant has a small stock, the price never changes with demand.
		offers.add(new MerchantOffer(costA, costB, stack, 3, 1, 0.0F));
	}
}
