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
 * <p>The cursed grimoire is the same item with {"cursed": true} in its custom data: same pages and mechanics, its own
 * settings (section cursedGrimoire: base item, much stronger spells, food and blood price, cooldown group and length,
 * item_model guildy:grimoire_maudit) and texts.
 *
 * <p>A stack carrying only the custom data (a loot table entry) or built from an older config is completed in place as soon
 * as it is seen in a player's inventory (rebuilt when its base item changed).
 */
public final class GrimoireItems {
	public static final String KIND = "grimoire";
	/** Cooldown group of every grimoire (independent of the scroll groups). */
	public static final Identifier COOLDOWN_GROUP = Identifier.fromNamespaceAndPath(Guildy.MOD_ID, "grimoire");
	/** Cooldown group of the cursed grimoires (their own, longer cooldown). */
	public static final Identifier CURSED_COOLDOWN_GROUP = Identifier.fromNamespaceAndPath(Guildy.MOD_ID, "grimoire_maudit");
	/** Custom data flag of a cursed grimoire. */
	public static final String CURSED = "cursed";
	private static final String SPELLS = "spells";
	private static final String SELECTED = "selected";
	private static final String SIGNATURE = "sig";
	private static final Holder<SoundEvent> SILENT = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY);
	/** Signatures of the shared settings and texts, grimoire and cursed grimoire (cached until the next reload, 0 = to compute). */
	private static int baseSignature;
	private static int cursedSignature;

	private GrimoireItems() {
	}

	// ================================================================== queries

	public static boolean isGrimoire(ItemStack stack) {
		return !stack.isEmpty() && stack.has(DataComponents.CUSTOM_DATA) && GuildyItems.is(stack, KIND);
	}

	/** A cursed grimoire (a grimoire flagged cursed). */
	public static boolean isCursed(ItemStack stack) {
		return isGrimoire(stack) && GuildyItems.data(stack).getBooleanOr(CURSED, false);
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

	/** Channel key of a grimoire: "grimoire/" (or "grimoire_maudit/") + selected scroll id (null for other items). */
	static @Nullable String channelKey(ItemStack stack) {
		if (!isGrimoire(stack)) {
			return null;
		}
		return (isCursed(stack) ? "grimoire_maudit/" : "grimoire/") + pages(stack).selectedSpell();
	}

	/** A channel key of a grimoire (scroll ids never contain a '/'). */
	static boolean isGrimoireKey(String key) {
		return key.indexOf('/') >= 0;
	}

	/** Food points the spell costs from a grimoire. */
	public static int foodCost(ScrollConfig.ScrollDef def) {
		return ScrollModule.config().grimoire.foodCost(def.rarity());
	}

	/** Food points the spell costs from a grimoire, or from a cursed grimoire. */
	public static int foodCost(ScrollConfig.ScrollDef def, boolean cursed) {
		return cursed ? ScrollModule.config().cursedGrimoire.foodCost(def.rarity()) : foodCost(def);
	}

	/** Health points the spell costs from a cursed grimoire (0 from a grimoire). */
	public static int bloodCost(ScrollConfig.ScrollDef def, boolean cursed) {
		return cursed ? ScrollModule.config().cursedGrimoire.bloodCost(def.rarity()) : 0;
	}

	/** Power of a cast of this spell from a grimoire or a cursed grimoire. */
	public static SpellPower power(ScrollConfig.ScrollDef def, boolean cursed) {
		ScrollConfig cfg = ScrollModule.config();
		return cursed ? cfg.cursedGrimoire.power(def.spell()) : SpellPower.of(cfg.grimoire.powerMultiplier);
	}

	/** Cooldown (seconds) after a cast. */
	public static double cooldownSeconds(boolean cursed) {
		ScrollConfig cfg = ScrollModule.config();
		return cursed ? cfg.cursedGrimoire.cooldownSeconds : cfg.grimoire.cooldownSeconds;
	}

	public static Identifier cooldownGroup(boolean cursed) {
		return cursed ? CURSED_COOLDOWN_GROUP : COOLDOWN_GROUP;
	}

	/** Enabled in the config (the grimoire, or the cursed grimoire). */
	public static boolean enabled(boolean cursed) {
		ScrollConfig cfg = ScrollModule.config();
		return cursed ? cfg.cursedGrimoire.enabled : cfg.grimoire.enabled;
	}

	private static String baseItem(boolean cursed) {
		ScrollConfig cfg = ScrollModule.config();
		return cursed ? cfg.cursedGrimoire.item : cfg.grimoire.item;
	}

	/** The cursed grimoire's own text for this key when it has one ("grimoire.x" -> "grimoire.cursed.x"). */
	static String key(String key, boolean cursed) {
		if (cursed && key.startsWith("grimoire.")) {
			String own = "grimoire.cursed." + key.substring("grimoire.".length());
			if (Lang.has(own)) {
				return own;
			}
		}
		return key;
	}

	/** "0,5", "1" (hearts of a health cost). */
	static String hearts(int health) {
		return Spell.number(health / 2.0);
	}

	// ================================================================== creation

	/** A grimoire with these pages (empty when the base item is unknown). */
	public static ItemStack create(GrimoireRules.Pages pages) {
		return create(pages, false);
	}

	/** A grimoire, or a cursed grimoire, with these pages (empty when the base item is unknown). */
	public static ItemStack create(GrimoireRules.Pages pages, boolean cursed) {
		Item item = ItemUtil.item(baseItem(cursed));
		if (item == null) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = new ItemStack(item);
		apply(stack, pages, cursed);
		return stack;
	}

	/** Writes the pages and every component on the stack, in place (other components such as a custom name are kept). */
	public static void apply(ItemStack stack, GrimoireRules.Pages pages) {
		apply(stack, pages, isCursed(stack));
	}

	private static void apply(ItemStack stack, GrimoireRules.Pages pages, boolean cursed) {
		ScrollConfig cfg = ScrollModule.config();
		ScrollConfig.Grimoire g = cfg.grimoire;
		CompoundTag extra = new CompoundTag();
		ListTag spells = new ListTag();
		for (String id : pages.spells()) {
			spells.add(StringTag.valueOf(id));
		}
		extra.put(SPELLS, spells);
		extra.putInt(SELECTED, pages.selected());
		if (cursed) {
			extra.putBoolean(CURSED, true);
		}
		extra.putInt(SIGNATURE, signature(pages, cursed));
		GuildyItems.tag(stack, KIND, extra);
		stack.set(DataComponents.MAX_STACK_SIZE, 1);
		stack.set(DataComponents.ITEM_NAME, name(pages, cursed));
		stack.set(DataComponents.RARITY, Rarity.EPIC);
		stack.set(DataComponents.LORE, new ItemLore(lore(pages, cursed)));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, cursed ? cfg.cursedGrimoire.glint : g.glint);
		stack.set(DataComponents.CONSUMABLE, Consumable.builder()
				// The selected spell's channel: the client animation and the server completion follow it.
				.consumeSeconds((float) g.channelSeconds(selectedDef(pages)))
				.animation(animation(g.animation))
				.sound(SILENT)
				.hasConsumeParticles(false)
				.build());
		stack.set(DataComponents.USE_EFFECTS, new UseEffects(false, false, (float) cfg.casting.moveSpeed));
		stack.set(DataComponents.USE_COOLDOWN, new UseCooldown((float) cooldownSeconds(cursed), Optional.of(cooldownGroup(cursed))));
		stack.set(DataComponents.CUSTOM_MODEL_DATA,
				new CustomModelData(List.of(), List.of(), List.of(GrimoireRules.modelValue(pages)), List.of()));
		if (stack.getPrototype().has(DataComponents.PROVIDES_BANNER_PATTERNS)) {
			stack.remove(DataComponents.PROVIDES_BANNER_PATTERNS);
		}
		if (stack.getPrototype().has(DataComponents.ENCHANTABLE)) {
			// A plain book base: never enchanted at a table (it would become an enchanted book).
			stack.remove(DataComponents.ENCHANTABLE);
		}
		String model = cursed ? cfg.cursedGrimoire.model : g.model;
		if (!model.isEmpty() && UniqueVisuals.enabled()) {
			stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(Guildy.MOD_ID, model));
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
		return name(pages, false);
	}

	/** "Grimoire — Blizzard (2/4)" / "Grimoire maudit — Blizzard (2/4)", "(vierge)" without a spell. */
	public static Component name(GrimoireRules.Pages pages, boolean cursed) {
		String id = pages.selectedSpell();
		ScrollConfig.ScrollDef def = selectedDef(pages);
		String text = id.isEmpty() ? Lang.raw(key("grimoire.item.name_empty", cursed))
				: Lang.raw(key("grimoire.item.name", cursed), def == null ? "§c" + id : spellName(def), pages.selected() + 1, GrimoireRules.PAGES);
		return Component.literal(text).withStyle(style -> style.withItalic(false));
	}

	/** One page line of the lore / menu: number, spell, food cost (and blood price of a cursed grimoire). */
	static Component pageLine(GrimoireRules.Pages pages, int page, boolean cursed) {
		String id = pages.spell(page);
		if (id.isEmpty()) {
			return Lang.get("grimoire.item.page_blank", page + 1);
		}
		ScrollConfig.ScrollDef def = ScrollModule.config().scroll(id);
		if (def == null) {
			return Lang.get("grimoire.item.page_unknown", page + 1, id);
		}
		String line = page == pages.selected() ? "grimoire.item.page_selected" : "grimoire.item.page";
		return Lang.get(key(line, cursed), page + 1, spellName(def), foodCost(def, cursed), hearts(bloodCost(def, cursed)));
	}

	/** The cost line of a spell: food (and blood for a cursed grimoire). */
	static Component costLine(ScrollConfig.ScrollDef def, boolean cursed) {
		return Lang.get(key("grimoire.item.cost", cursed), foodCost(def, cursed), hearts(bloodCost(def, cursed)),
				hearts((int) Math.ceil(ScrollModule.config().cursedGrimoire.minHealth)));
	}

	/** Header, the four pages, the selected spell with the grimoire's values, channel, cooldown, cost and usage. */
	static List<Component> lore(GrimoireRules.Pages pages, boolean cursed) {
		ScrollConfig.Grimoire g = ScrollModule.config().grimoire;
		List<Component> lines = new ArrayList<>();
		lines.add(Lang.get(key("grimoire.item.header", cursed), pages.filled(), GrimoireRules.PAGES));
		lines.add(Component.empty());
		for (int page = 0; page < GrimoireRules.PAGES; page++) {
			lines.add(pageLine(pages, page, cursed));
		}
		ScrollConfig.ScrollDef def = selectedDef(pages);
		lines.add(Component.empty());
		if (def != null) {
			lines.add(Lang.get("scroll.item.spell", Lang.raw(def.spell().langKey() + ".name")));
			for (String line : ScrollItems.spellText(def, power(def, cursed)).split("\n")) {
				lines.add(Lang.get("scroll.item.spell_line", line));
			}
			lines.add(Component.empty());
			lines.add(costLine(def, cursed));
		} else {
			for (String line : Lang.raw("grimoire.item.no_spell").split("\n")) {
				lines.add(Component.literal(line));
			}
			lines.add(Component.empty());
		}
		lines.add(Lang.get("grimoire.item.cast", Spell.number(g.channelSeconds(def)), Spell.number(cooldownSeconds(cursed))));
		if (cursed) {
			for (String line : Lang.raw("grimoire.cursed.item.curse").split("\n")) {
				lines.add(Component.literal(line));
			}
		} else if (g.powerMultiplier != 1.0) {
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
	static int signature(GrimoireRules.Pages pages, boolean cursed) {
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
		if (cursed) {
			int own = cursedSignature;
			if (own == 0) {
				own = Objects.hash(base, cfg.cursedGrimoire.fingerprint(), String.join("|", Lang.raw("grimoire.cursed.item.name", "", "", ""),
						Lang.raw("grimoire.cursed.item.name_empty"), Lang.raw("grimoire.cursed.item.header", "", ""),
						Lang.raw("grimoire.cursed.item.page", "", "", "", ""), Lang.raw("grimoire.cursed.item.page_selected", "", "", "", ""),
						Lang.raw("grimoire.cursed.item.cost", "", "", ""), Lang.raw("grimoire.cursed.item.curse")));
				own = own == 0 ? 1 : own;
				cursedSignature = own;
			}
			base = own;
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
		cursedSignature = 0;
	}

	/**
	 * Completes an incomplete or outdated grimoire: {@code stack} itself (updated in place) when its base item is right, a new
	 * stack when the base item changed, null when it is up to date (or no grimoire).
	 */
	static @Nullable ItemStack refreshed(ItemStack stack) {
		if (!isGrimoire(stack)) {
			return null;
		}
		boolean cursed = isCursed(stack);
		GrimoireRules.Pages pages = pages(stack);
		Item item = ItemUtil.item(baseItem(cursed));
		boolean sameItem = item == null || stack.is(item);
		if (sameItem && GuildyItems.data(stack).getIntOr(SIGNATURE, 0) == signature(pages, cursed)) {
			return null;
		}
		if (sameItem) {
			apply(stack, pages, cursed);
			return stack;
		}
		ItemStack fresh = create(pages, cursed);
		if (fresh.isEmpty()) {
			return null;
		}
		fresh.setCount(Math.max(1, Math.min(stack.getCount(), fresh.getMaxStackSize())));
		return fresh;
	}
}
