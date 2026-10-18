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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * The "Parchemin d'incantation maudit": a vanilla item with components (custom_data {"guildy": {"kind": "ruin_summon_cursed",
 * "sig": ...}}, item name in purple and dark red, lore: the curse, the blood price, the rewards; glint, max stack size, a
 * silent consumable for the vanilla channel animation) and, only when a resource pack is guaranteed, the item model
 * {@code guildy:parchemin_incantation_maudit}. Read like the normal incantation scroll ({@link Incantations}), it starts a
 * {@link CursedRitual} that awakens the ruin's guardian cursed ({@link CursedEncounter}).
 *
 * <p>A stack carrying only the custom data (a loot table entry, an old scroll after a config change) is rebuilt as soon as
 * it is seen in a player's inventory. Sources: the trade hub merchant (tier III), enraged guardians, legendary contracts and
 * {@code /guild admin ruin incantation <player> [count] cursed}.
 */
public final class CursedItems {
	public static final String KIND = "ruin_summon_cursed";
	/** Item model of the texture pack (assets/guildy/items/parchemin_incantation_maudit.json). */
	public static final Identifier MODEL = Identifier.fromNamespaceAndPath(Guildy.MOD_ID, "parchemin_incantation_maudit");
	private static final String SIGNATURE = "sig";
	private static final Holder<SoundEvent> SILENT = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY);
	private static @Nullable Integer signature;

	private CursedItems() {
	}

	// ================================================================== creation

	/** {@code count} cursed scrolls (capped by the max stack size). */
	public static ItemStack create(int count) {
		CursedSettings.RitualSettings cfg = RuinModule.config().cursed.ritual;
		ItemStack stack = new ItemStack(baseItem());
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
		stack.set(DataComponents.USE_EFFECTS, new UseEffects(false, false, 0.25F));
		if (stack.getPrototype().has(DataComponents.PROVIDES_BANNER_PATTERNS)) {
			// Base banner pattern: no pattern line in the tooltip, not usable in a loom.
			stack.remove(DataComponents.PROVIDES_BANNER_PATTERNS);
		}
		if (UniqueVisuals.enabled()) {
			stack.set(DataComponents.ITEM_MODEL, MODEL);
		}
		return stack;
	}

	/** {@code count} cursed scrolls split into full stacks. */
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

	/** Gives {@code count} cursed scrolls (whatever does not fit is dropped at the player's feet). */
	public static void give(ServerPlayer player, int count) {
		for (ItemStack stack : stacks(count)) {
			ItemUtil.giveOrDrop(player, stack);
		}
	}

	private static Item baseItem() {
		Item item = ItemUtil.item(RuinModule.config().cursed.ritual.item);
		return item != null ? item : Items.PIGLIN_BANNER_PATTERN;
	}

	public static Component name() {
		return Component.literal(Lang.raw("ruin.cursed.item.name")).withStyle(style -> style.withItalic(false));
	}

	/** The curse, the blood price, the rewards, how to read it. */
	static List<Component> lore() {
		CursedSettings c = RuinModule.config().cursed;
		RuinConfig.SummonSettings summon = RuinModule.config().summon;
		List<Component> lines = new ArrayList<>();
		lines.add(Lang.get("ruin.cursed.item.header"));
		lines.add(Component.empty());
		for (String line : Lang.raw("ruin.cursed.item.story").split("\n")) {
			lines.add(Component.literal(line));
		}
		lines.add(Component.empty());
		lines.add(Lang.get("ruin.cursed.item.curse", IncantationItems.number(c.guardian.healthMultiplier), IncantationItems.number(c.guardian.damageMultiplier)));
		lines.add(Lang.get("ruin.cursed.item.curse.attacks"));
		if (c.finalForm.enabled) {
			lines.add(Lang.get("ruin.cursed.item.final"));
		}
		if (c.ritual.drainHealth > 0) {
			lines.add(Lang.get("ruin.cursed.item.price", IncantationItems.hearts(c.ritual.drainHealth)));
			lines.add(Lang.get("ruin.cursed.item.price.min", IncantationItems.hearts(c.ritual.minHealth)));
		} else {
			lines.add(Lang.get("ruin.cursed.item.price.none"));
		}
		lines.add(Lang.get("ruin.cursed.item.rewards", IncantationItems.number(c.rewards.goldMultiplier)));
		lines.add(Lang.get("ruin.cursed.item.rewards.more", c.rewards.threatGain));
		lines.add(Component.empty());
		lines.add(Lang.get("ruin.cursed.item.use", IncantationItems.number(c.ritual.channelSeconds), summon.arenaRadius));
		lines.add(Lang.get("ruin.cursed.item.ritual", IncantationItems.number(c.ritual.ritualSeconds)));
		lines.add(Lang.get("ruin.cursed.item.dormant"));
		lines.add(Lang.get("ruin.cursed.item.single_use"));
		List<Component> out = new ArrayList<>(lines.size());
		for (Component line : lines) {
			out.add(Component.empty().append(line).withStyle(style -> style.withItalic(false)));
		}
		return out;
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
			CursedSettings c = RuinModule.config().cursed;
			CursedSettings.RitualSettings r = c.ritual;
			StringBuilder texts = new StringBuilder();
			for (String key : List.of("ruin.cursed.item.name", "ruin.cursed.item.header", "ruin.cursed.item.story", "ruin.cursed.item.curse",
					"ruin.cursed.item.curse.attacks", "ruin.cursed.item.final", "ruin.cursed.item.price", "ruin.cursed.item.price.min", "ruin.cursed.item.price.none",
					"ruin.cursed.item.rewards", "ruin.cursed.item.rewards.more", "ruin.cursed.item.use", "ruin.cursed.item.ritual",
					"ruin.cursed.item.dormant", "ruin.cursed.item.single_use")) {
				texts.append(Lang.raw(key)).append('|');
			}
			sig = Objects.hash(r.item, r.maxStack, r.channelSeconds, r.ritualSeconds, r.drainHealth, r.minHealth, c.guardian.healthMultiplier,
					c.guardian.damageMultiplier, c.rewards.goldMultiplier, c.rewards.threatGain, c.finalForm.enabled, RuinModule.config().summon.arenaRadius,
					UniqueVisuals.enabled(), texts.toString());
			signature = sig;
		}
		return sig;
	}

	/** Config or lang reloaded: stacks are compared to a fresh signature. */
	static void clearSignature() {
		signature = null;
	}

	/**
	 * The rebuilt stack (same count, capped by the max stack size) when {@code stack} is a cursed scroll built from other
	 * settings or another base item (custom_data only from a loot table, config changed), else null.
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

	/** Rebuilds the incomplete / outdated cursed scrolls of a player's inventory (never the one being used). */
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
				give(player, extra);
			}
			changed = true;
		}
		if (changed) {
			inventory.setChanged();
		}
	}

	// ================================================================== sources

	/**
	 * Sometimes a cursed scroll at the trade hub merchant (from {@code cursed.sources.tradeTier}, chance {@code tradeChance},
	 * price {@code tradePrice} gold): a single one per visit.
	 */
	public static void addTradeOffer(MerchantOffers offers, int hubTier) {
		CursedSettings c = RuinModule.config().cursed;
		CursedSettings.SourceSettings s = c.sources;
		if (!c.enabled || s.tradeTier <= 0 || hubTier < s.tradeTier || ThreadLocalRandom.current().nextDouble() >= s.tradeChance) {
			return;
		}
		ItemStack stack = create(1);
		int[] split = TradeRolls.priceSplit(s.tradePrice);
		ItemCost costA = split[0] > 0 ? new ItemCost(Items.GOLD_BLOCK, split[0]) : new ItemCost(Items.GOLD_INGOT, split[1]);
		Optional<ItemCost> costB = split[0] > 0 && split[1] > 0 ? Optional.of(new ItemCost(Items.GOLD_INGOT, split[1])) : Optional.empty();
		offers.add(new MerchantOffer(costA, costB, stack, 1, 1, 0.0F));
	}

	/** A cursed scroll for a finished legendary contract ({@code sources.legendaryQuestChance}), else empty. */
	public static ItemStack rollLegendaryContract() {
		CursedSettings c = RuinModule.config().cursed;
		return roll(c.enabled ? c.sources.legendaryQuestChance : 0, ThreadLocalRandom.current());
	}

	/** A cursed scroll for a guardian defeated in its risen ("Enragé") form ({@code sources.risenChance}), else empty. */
	static ItemStack rollRisen(RandomGenerator random) {
		CursedSettings c = RuinModule.config().cursed;
		return roll(c.enabled ? c.sources.risenChance : 0, random);
	}

	private static ItemStack roll(double chance, RandomGenerator random) {
		if (chance <= 0 || chance < 1 && random.nextDouble() >= chance) {
			return ItemStack.EMPTY;
		}
		return create(1);
	}
}
