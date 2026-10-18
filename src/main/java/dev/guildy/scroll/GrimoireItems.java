package dev.guildy.scroll;

import dev.guildy.Guildy;
import dev.guildy.core.GuildyItems;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.unique.UniqueVisuals;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.item.component.UseEffects;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The grimoire is a vanilla item with components: custom_data {"guildy": {"kind": "grimoire", "spells": [4 scroll ids, ""
 * = blank page], "selected": page, "sig": ...}}, item name with the selected spell ("Grimoire — Blizzard (2/4)"), lore (the
 * four pages, the selected spell with its values, channel, cooldown, food cost, how to use it), glint, max stack size 1, a
 * consumable component (the vanilla channel animation; the cast is decided by the mod and nothing is eaten), use effects,
 * a use_cooldown of its own group (the client shows it), custom_model_data strings[0] = the selected scroll id ("empty"
 * without a spell) and, only when a resource pack is guaranteed, item_model guildy:grimoire.
 *
 * <p>A stack carrying only the custom data (a loot table entry) or built from an older config is completed in place as soon
 * as it is seen in a player's inventory (rebuilt when its base item changed).
 */
public final class GrimoireItems {
	public static final String KIND = "grimoire";
	/** Cooldown group of every grimoire (independent of the scroll groups). */
	public static final Identifier COOLDOWN_GROUP = Identifier.fromNamespaceAndPath(Guildy.MOD_ID, "grimoire");
	private static final String SPELLS = "spells";
	private static final String SELECTED = "selected";
	private static final String SIGNATURE = "sig";
	private static final Holder<SoundEvent> SILENT = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY);
	/** Signature of the shared settings and texts (cached until the next reload, 0 = to compute). */
	private static int baseSignature;

	private GrimoireItems() {
	}

	// ================================================================== queries

	public static boolean isGrimoire(ItemStack stack) {
		return !stack.isEmpty() && stack.has(DataComponents.CUSTOM_DATA) && GuildyItems.is(stack, KIND);
	}

	/** The pages of a grimoire (empty pages for other items; a blank selected page moves to a written one). */
	public static GrimoireRules.Pages pages(ItemStack stack) {
		if (!isGrimoire(stack)) {
			return GrimoireRules.Pages.EMPTY;
		}
		CompoundTag data = GuildyItems.data(stack);
		ListTag list = data.getListOrEmpty(SPELLS);
		List<String> spells = new ArrayList<>();
		for (int i = 0; i < Math.min(list.size(), GrimoireRules.PAGES); i++) {
			spells.add(list.getStringOr(i, ""));
		}
		return GrimoireRules.normalize(new GrimoireRules.Pages(spells, data.getIntOr(SELECTED, 0)));
	}

	/** Definition of the selected page, or null (blank page, scroll removed from the config). */
	public static ScrollConfig.@Nullable ScrollDef selectedDef(GrimoireRules.Pages pages) {
		String id = pages.selectedSpell();
		return id.isEmpty() ? null : ScrollModule.config().scroll(id);
	}

	/** Channel time in ticks of the selected page (the grimoire's minimum without a readable spell), as on the item. */
	public static int channelTicks(GrimoireRules.Pages pages) {
		return ScrollModule.config().grimoire.channelTicks(selectedDef(pages));
	}

	/** Channel key of a grimoire: "grimoire/" + selected scroll id (null for other items). */
	static @Nullable String channelKey(ItemStack stack) {
		return isGrimoire(stack) ? "grimoire/" + pages(stack).selectedSpell() : null;
	}

	/** Food points the spell costs from a grimoire. */
	public static int foodCost(ScrollConfig.ScrollDef def) {
		return ScrollModule.config().grimoire.foodCost(def.rarity());
	}

	// ================================================================== creation

	/** A grimoire with these pages (empty when the base item is unknown). */
	public static ItemStack create(GrimoireRules.Pages pages) {
		Item item = ItemUtil.item(ScrollModule.config().grimoire.item);
		if (item == null) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = new ItemStack(item);
		apply(stack, pages);
		return stack;
	}

	/** Writes the pages and every component on the stack, in place (other components such as a custom name are kept). */
	public static void apply(ItemStack stack, GrimoireRules.Pages pages) {
		ScrollConfig cfg = ScrollModule.config();
		ScrollConfig.Grimoire g = cfg.grimoire;
		CompoundTag extra = new CompoundTag();
		ListTag spells = new ListTag();
		for (String id : pages.spells()) {
			spells.add(StringTag.valueOf(id));
		}
		extra.put(SPELLS, spells);
		extra.putInt(SELECTED, pages.selected());
		extra.putInt(SIGNATURE, signature(pages));
		GuildyItems.tag(stack, KIND, extra);
		stack.set(DataComponents.MAX_STACK_SIZE, 1);
		stack.set(DataComponents.ITEM_NAME, name(pages));
		stack.set(DataComponents.RARITY, Rarity.EPIC);
		stack.set(DataComponents.LORE, new ItemLore(lore(pages)));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, g.glint);
		stack.set(DataComponents.CONSUMABLE, Consumable.builder()
				// The selected spell's channel: the client animation and the server completion follow it.
				.consumeSeconds((float) g.channelSeconds(selectedDef(pages)))
				.animation(animation(g.animation))
				.sound(SILENT)
				.hasConsumeParticles(false)
				.build());
		stack.set(DataComponents.USE_EFFECTS, new UseEffects(false, false, (float) cfg.casting.moveSpeed));
		stack.set(DataComponents.USE_COOLDOWN, new UseCooldown((float) g.cooldownSeconds, Optional.of(COOLDOWN_GROUP)));
		stack.set(DataComponents.CUSTOM_MODEL_DATA,
				new CustomModelData(List.of(), List.of(), List.of(GrimoireRules.modelValue(pages)), List.of()));
		if (stack.getPrototype().has(DataComponents.PROVIDES_BANNER_PATTERNS)) {
			stack.remove(DataComponents.PROVIDES_BANNER_PATTERNS);
		}
		if (stack.getPrototype().has(DataComponents.ENCHANTABLE)) {
			// A plain book base: never enchanted at a table (it would become an enchanted book).
			stack.remove(DataComponents.ENCHANTABLE);
		}
		if (!g.model.isEmpty() && UniqueVisuals.enabled()) {
			stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(Guildy.MOD_ID, g.model));
		} else {
			// Back to the base item's own model (setting the prototype value removes the patch entry).
			stack.set(DataComponents.ITEM_MODEL, stack.getPrototype().get(DataComponents.ITEM_MODEL));
		}
	}

	private static ItemUseAnimation animation(String name) {
		try {
			return ItemUseAnimation.valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return ItemUseAnimation.TOOT_HORN;
		}
	}

	/** Colored spell name of a scroll definition ("§6§lPluie de météores"). */
	static String spellName(ScrollConfig.ScrollDef def) {
		return Lang.colorize(def.name.startsWith("&") ? def.name : def.rarity().namePrefix() + def.name);
	}

	/** "Grimoire — Blizzard (2/4)", "Grimoire (vierge)" without a spell. */
	public static Component name(GrimoireRules.Pages pages) {
		String id = pages.selectedSpell();
		ScrollConfig.ScrollDef def = selectedDef(pages);
		String text = id.isEmpty() ? Lang.raw("grimoire.item.name_empty")
				: Lang.raw("grimoire.item.name", def == null ? "§c" + id : spellName(def), pages.selected() + 1, GrimoireRules.PAGES);
		return Component.literal(text).withStyle(style -> style.withItalic(false));
	}

	/** One page line of the lore / menu: number, spell, food cost. */
	static Component pageLine(GrimoireRules.Pages pages, int page) {
		String id = pages.spell(page);
		if (id.isEmpty()) {
			return Lang.get("grimoire.item.page_blank", page + 1);
		}
		ScrollConfig.ScrollDef def = ScrollModule.config().scroll(id);
		if (def == null) {
			return Lang.get("grimoire.item.page_unknown", page + 1, id);
		}
		String key = page == pages.selected() ? "grimoire.item.page_selected" : "grimoire.item.page";
		return Lang.get(key, page + 1, spellName(def), foodCost(def));
	}

	/** Header, the four pages, the selected spell with the grimoire's values, channel, cooldown, cost and usage. */
	static List<Component> lore(GrimoireRules.Pages pages) {
		ScrollConfig.Grimoire g = ScrollModule.config().grimoire;
		List<Component> lines = new ArrayList<>();
		lines.add(Lang.get("grimoire.item.header", pages.filled(), GrimoireRules.PAGES));
		lines.add(Component.empty());
		for (int page = 0; page < GrimoireRules.PAGES; page++) {
			lines.add(pageLine(pages, page));
		}
		ScrollConfig.ScrollDef def = selectedDef(pages);
		lines.add(Component.empty());
		if (def != null) {
			lines.add(Lang.get("scroll.item.spell", Lang.raw(def.spell().langKey() + ".name")));
			for (String line : ScrollItems.spellText(def, g.powerMultiplier).split("\n")) {
				lines.add(Lang.get("scroll.item.spell_line", line));
			}
			lines.add(Component.empty());
			lines.add(Lang.get("grimoire.item.cost", foodCost(def)));
		} else {
			for (String line : Lang.raw("grimoire.item.no_spell").split("\n")) {
				lines.add(Component.literal(line));
			}
			lines.add(Component.empty());
		}
		lines.add(Lang.get("grimoire.item.cast", Spell.number(g.channelSeconds(def)), Spell.number(g.cooldownSeconds)));
		if (g.powerMultiplier != 1.0) {
			lines.add(Lang.get("grimoire.item.power", Spell.number(g.powerMultiplier)));
		}
		for (String line : Lang.raw("grimoire.item.usage").split("\n")) {
			lines.add(Component.literal(line));
		}
		List<Component> out = new ArrayList<>(lines.size());
		for (Component line : lines) {
			out.add(Component.empty().append(line).withStyle(style -> style.withItalic(false)));
		}
		return out;
	}

	// ================================================================== refresh

	/** What a grimoire with these pages records: the pages, their scroll definitions, the shared settings and texts. */
	static int signature(GrimoireRules.Pages pages) {
		ScrollConfig cfg = ScrollModule.config();
		int base = baseSignature;
		if (base == 0) {
			base = Objects.hash(cfg.grimoire.fingerprint(), UniqueVisuals.enabled(), cfg.casting.moveSpeed,
					String.join("|", Lang.raw("grimoire.item.name", "", "", ""), Lang.raw("grimoire.item.name_empty"),
							Lang.raw("grimoire.item.header", "", ""), Lang.raw("grimoire.item.page", "", "", ""),
							Lang.raw("grimoire.item.page_selected", "", "", ""), Lang.raw("grimoire.item.page_blank", ""),
							Lang.raw("grimoire.item.cost", ""), Lang.raw("grimoire.item.cast", "", ""), Lang.raw("grimoire.item.usage"),
							Lang.raw("grimoire.item.no_spell")));
			base = base == 0 ? 1 : base;
			baseSignature = base;
		}
		int hash = Objects.hash(base, pages);
		for (String id : pages.spells()) {
			ScrollConfig.ScrollDef def = id.isEmpty() ? null : cfg.scroll(id);
			hash = 31 * hash + (def == null ? 0 : ScrollItems.signature(def));
		}
		return hash;
	}

	/** Config or lang reloaded. */
	static void clearSignature() {
		baseSignature = 0;
	}

	/**
	 * Completes an incomplete or outdated grimoire: {@code stack} itself (updated in place) when its base item is right, a new
	 * stack when the base item changed, null when it is up to date (or no grimoire).
	 */
	static @Nullable ItemStack refreshed(ItemStack stack) {
		if (!isGrimoire(stack)) {
			return null;
		}
		GrimoireRules.Pages pages = pages(stack);
		Item item = ItemUtil.item(ScrollModule.config().grimoire.item);
		boolean sameItem = item == null || stack.is(item);
		if (sameItem && GuildyItems.data(stack).getIntOr(SIGNATURE, 0) == signature(pages)) {
			return null;
		}
		if (sameItem) {
			apply(stack, pages);
			return stack;
		}
		ItemStack fresh = create(pages);
		if (fresh.isEmpty()) {
			return null;
		}
		fresh.setCount(Math.max(1, Math.min(stack.getCount(), fresh.getMaxStackSize())));
		return fresh;
	}
}
