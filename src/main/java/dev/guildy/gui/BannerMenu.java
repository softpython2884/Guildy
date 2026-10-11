package dev.guildy.gui;

import dev.guildy.command.GuildActions;
import dev.guildy.command.GuildActions.Denial;
import dev.guildy.command.GuildRules;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.BannerDesign;
import dev.guildy.data.Guild;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Banner editor working on a copy of the guild banner: base color, up to {@link BannerDesign#MAX_LAYERS} pattern
 * layers (pattern picker → color picker), reset / revert, save (BANNER permission) and decorative banner purchase
 * (TREASURY permission). Nothing is applied to the guild until "Enregistrer".
 */
final class BannerMenu extends GuildGui {
	/** 9 + 7 symmetric color grid on rows 2 and 3. */
	static final int[] COLOR_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17, 19, 20, 21, 22, 23, 24, 25};
	private static final int[] LAYER_SLOTS = {28, 29, 30, 31, 32, 33};

	private final BannerDesign working;

	BannerMenu(ServerPlayer player, Guild guild, BannerDesign working) {
		super(player, 6, guild);
		this.working = working;
	}

	@Override
	protected Component title(@Nullable Guild guild) {
		return Lang.get("banner.title");
	}

	@Override
	protected void build(@Nullable Guild guild) {
		if (guild == null) {
			return;
		}
		setSlot(4, preview(working, guild));
		boolean freeEditor = GuildyServer.config().banners.freeEditor;
		action(freeEditor ? 43 : 22, adoptIcon(), () -> GuildActions.adoptBannerDenial(player), () -> {
			boolean adopted = GuildActions.adoptBanner(player);
			Guild current = guild();
			if (adopted && current != null) {
				working.base = current.banner.base;
				working.layers.clear();
				working.layers.addAll(current.banner.copy().layers);
			}
			return adopted;
		});
		if (!freeEditor) {
			// Loom mode: the banner is woven at a real loom with real dyes, then adopted.
			setSlot(20, GuiUtil.item(Items.LOOM, Lang.get("banner.loom.name"), GuiUtil.lines("banner.loom.lore")));
			action(24, icon(guild.banner.createBanner(GuildyServer.registryAccess()), Lang.get("banner.decorative.name"),
							GuiUtil.lines("banner.decorative.lore", GuildyServer.config().economy.decorativeBannerPrice)),
					() -> GuildActions.decorativeBannerDenial(player), () -> GuildActions.buyDecorativeBanner(player));
			bottomBar(() -> new MainMenu(player, guild).show());
			return;
		}

		DyeColor[] colors = DyeColor.values();
		for (int i = 0; i < colors.length && i < COLOR_SLOTS.length; i++) {
			DyeColor color = colors[i];
			boolean current = working.baseColor() == color;
			GuiElementBuilder dye = GuiUtil.item(Items.DYE.pick(color), colorName(color),
					GuiUtil.lines(current ? "banner.base.current" : "banner.base.pick"));
			if (current) {
				dye.glow();
			}
			button(COLOR_SLOTS[i], dye, null, () -> {
				working.base = color.getName();
				loomSound(player);
			});
		}

		for (int i = 0; i < LAYER_SLOTS.length; i++) {
			if (i < working.layers.size()) {
				int index = i;
				BannerDesign.Layer layer = working.layers.get(i);
				Holder<BannerPattern> pattern = pattern(layer.pattern);
				DyeColor color = layerColor(layer);
				ItemStack stack = pattern == null ? new ItemStack(Items.BANNER.pick(working.baseColor())) : single(working.baseColor(), pattern, color);
				Component name = pattern == null ? Component.literal(String.valueOf(layer.pattern)) : layerName(pattern, color);
				setSlot(LAYER_SLOTS[i], GuiUtil.item(stack, name, GuiUtil.lines("banner.layer.lore", i + 1))
						.setCallback(onClick("banner.layer", (g, type) -> {
							if (type.isRight && index < working.layers.size()) {
								working.layers.remove(index);
								loomSound(player);
								render();
							}
						})));
			} else {
				setSlot(LAYER_SLOTS[i], GuiUtil.item(Items.STAINED_GLASS_PANE.pick(DyeColor.LIGHT_GRAY),
						Lang.get("banner.layer.empty", i + 1), List.of()));
			}
		}
		button(34, icon(Items.LOOM, "banner.add.name", GuiUtil.lines("banner.add.lore", working.layers.size(), BannerDesign.MAX_LAYERS)),
				this::addDenial, () -> new PatternPicker(player, guild, working, 0).show());

		button(38, icon(Items.SHEARS, "banner.remove_last.name", GuiUtil.lines("banner.remove_last.lore")),
				() -> working.layers.isEmpty() ? new Denial("banner.remove_last.empty") : null, () -> {
					working.layers.removeLast();
					loomSound(player);
				});
		button(39, icon(Items.WATER_BUCKET, "banner.reset.name", GuiUtil.lines("banner.reset.lore")), null, () -> {
			working.base = DyeColor.WHITE.getName();
			working.layers.clear();
			loomSound(player);
		});
		button(40, icon(Items.CLOCK, "banner.revert.name", GuiUtil.lines("banner.revert.lore")),
				() -> {
					Guild current = guild();
					return current == null || sameDesign(working, current.banner) ? new Denial("banner.revert.nothing") : null;
				}, () -> {
					Guild current = guild();
					if (current != null) {
						working.base = current.banner.base;
						working.layers.clear();
						working.layers.addAll(current.banner.copy().layers);
					}
				});
		action(41, icon(Items.WRITABLE_BOOK, "banner.save.name", GuiUtil.lines("banner.save.lore")),
				() -> GuildActions.saveBannerDenial(player), () -> GuildActions.saveBanner(player, working));
		action(42, icon(guild.banner.createBanner(GuildyServer.registryAccess()), Lang.get("banner.decorative.name"),
						GuiUtil.lines("banner.decorative.lore", GuildyServer.config().economy.decorativeBannerPrice)),
				() -> GuildActions.decorativeBannerDenial(player), () -> GuildActions.buyDecorativeBanner(player));

		bottomBar(() -> new MainMenu(player, guild).show());
	}

	/** "Adopt the banner in hand" button, showing the held banner when there is one. */
	private GuiElementBuilder adoptIcon() {
		ItemStack held = player.getMainHandItem();
		BannerDesign design = BannerDesign.fromStack(held);
		ItemStack shown = design == null ? new ItemStack(Items.BANNER.pick(DyeColor.WHITE)) : design.createBanner(GuildyServer.registryAccess());
		return GuiUtil.item(shown, Lang.get("banner.adopt.name"),
				GuiUtil.lines(design == null ? "banner.adopt.lore.empty" : "banner.adopt.lore"));
	}

	private @Nullable Denial addDenial() {
		return working.layers.size() >= BannerDesign.MAX_LAYERS ? new Denial("banner.max_layers", BannerDesign.MAX_LAYERS) : null;
	}

	// ---------------------------------------------------------------- shared helpers

	static void loomSound(ServerPlayer player) {
		Msg.sound(player, SoundEvents.UI_LOOM_SELECT_PATTERN, 1.0f, 1.0f);
	}

	/** Localized color name, rendered by the client ("Rouge"...). */
	static MutableComponent colorName(DyeColor color) {
		return Component.translatable("color.minecraft." + color.getName());
	}

	static DyeColor layerColor(BannerDesign.Layer layer) {
		DyeColor color = DyeColor.byName(layer.color, DyeColor.BLACK);
		return color == null ? DyeColor.BLACK : color;
	}

	/** Vanilla layer name (e.g. "Pal rouge"), translated client-side. */
	static MutableComponent layerName(Holder<BannerPattern> pattern, DyeColor color) {
		String fallback = pattern.unwrapKey().map(k -> k.identifier().getPath()).orElse("?") + " " + color.getName();
		return Component.translatableWithFallback(pattern.value().translationKey() + "." + color.getName(), fallback);
	}

	/** All banner patterns of the registry, sorted by id. */
	static List<Holder.Reference<BannerPattern>> patterns() {
		return GuildyServer.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN).listElements()
				.sorted(Comparator.comparing(holder -> holder.key().identifier().toString()))
				.toList();
	}

	static @Nullable Holder<BannerPattern> pattern(@Nullable String id) {
		if (id == null) {
			return null;
		}
		try {
			ResourceKey<BannerPattern> key = ResourceKey.create(Registries.BANNER_PATTERN, Identifier.parse(id));
			return GuildyServer.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN).get(key).orElse(null);
		} catch (Exception e) {
			return null;
		}
	}

	/** Banner of {@code base} color with a single pattern layer. */
	static ItemStack single(DyeColor base, Holder<BannerPattern> pattern, DyeColor color) {
		ItemStack stack = new ItemStack(Items.BANNER.pick(base));
		stack.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder().add(pattern, color).build());
		return stack;
	}

	static boolean sameDesign(BannerDesign a, BannerDesign b) {
		if (a.baseColor() != b.baseColor() || a.layers.size() != b.layers.size()) {
			return false;
		}
		for (int i = 0; i < a.layers.size(); i++) {
			BannerDesign.Layer x = a.layers.get(i);
			BannerDesign.Layer y = b.layers.get(i);
			if (!Objects.equals(x.pattern, y.pattern) || !Objects.equals(x.color, y.color)) {
				return false;
			}
		}
		return true;
	}

	/** Live preview of the working copy. */
	static GuiElementBuilder preview(BannerDesign working, Guild guild) {
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get("banner.preview.base").append(colorName(working.baseColor()).withStyle(ChatFormatting.YELLOW)));
		lore.add(Lang.get("banner.preview.layers", working.layers.size(), BannerDesign.MAX_LAYERS));
		lore.add(Lang.get(sameDesign(working, guild.banner) ? "banner.preview.saved" : "banner.preview.unsaved"));
		return GuiUtil.item(working.createBanner(GuildyServer.registryAccess()), Lang.get("banner.preview.name"), lore);
	}

	// ---------------------------------------------------------------- pattern picker

	/** Every registered pattern, shown in black on a white banner (5 full rows: the 43 vanilla patterns fit on one page). */
	static final class PatternPicker extends GuildGui {
		private static final int PER_PAGE = 45;
		private final BannerDesign working;
		private int page;

		PatternPicker(ServerPlayer player, Guild guild, BannerDesign working, int page) {
			super(player, 6, guild);
			this.working = working;
			this.page = page;
		}

		@Override
		protected Component title(@Nullable Guild guild) {
			int pages = GuildRules.pageCount(patterns().size(), PER_PAGE);
			return pages > 1 ? Lang.get("banner.patterns.title_paged", GuildRules.clampPage(page, pages) + 1, pages)
					: Lang.get("banner.patterns.title");
		}

		@Override
		protected void build(@Nullable Guild guild) {
			if (guild == null) {
				return;
			}
			setSlot(53, preview(working, guild));
			List<Holder.Reference<BannerPattern>> patterns = patterns();
			int pages = GuildRules.pageCount(patterns.size(), PER_PAGE);
			page = GuildRules.clampPage(page, pages);
			int slot = 0;
			for (Holder.Reference<BannerPattern> pattern : GuildRules.page(patterns, page, PER_PAGE)) {
				String id = pattern.key().identifier().toString();
				button(slot++, GuiUtil.item(single(DyeColor.WHITE, pattern, DyeColor.BLACK), layerName(pattern, DyeColor.BLACK),
								GuiUtil.lines("banner.pattern.lore", id)),
						() -> working.layers.size() >= BannerDesign.MAX_LAYERS ? new Denial("banner.max_layers", BannerDesign.MAX_LAYERS) : null,
						() -> new ColorPicker(player, guild, working, id, page).show());
			}
			bottomBar(() -> new BannerMenu(player, guild, working).show());
			pager(page, pages, target -> {
				page = target;
				render();
			});
		}
	}

	// ---------------------------------------------------------------- color picker

	/** Color of the new layer; each choice previews the resulting banner. */
	static final class ColorPicker extends GuildGui {
		private final BannerDesign working;
		private final String patternId;
		private final int returnPage;

		ColorPicker(ServerPlayer player, Guild guild, BannerDesign working, String patternId, int returnPage) {
			super(player, 5, guild);
			this.working = working;
			this.patternId = patternId;
			this.returnPage = returnPage;
		}

		@Override
		protected Component title(@Nullable Guild guild) {
			return Lang.get("banner.colors.title");
		}

		@Override
		protected void build(@Nullable Guild guild) {
			if (guild == null) {
				return;
			}
			setSlot(4, preview(working, guild));
			Holder<BannerPattern> pattern = pattern(patternId);
			if (pattern != null) {
				DyeColor[] colors = DyeColor.values();
				for (int i = 0; i < colors.length && i < COLOR_SLOTS.length; i++) {
					DyeColor color = colors[i];
					BannerDesign result = working.copy();
					result.layers.add(new BannerDesign.Layer(patternId, color.getName()));
					button(COLOR_SLOTS[i], GuiUtil.item(result.createBanner(GuildyServer.registryAccess()), layerName(pattern, color),
									GuiUtil.lines("banner.color.lore")),
							() -> working.layers.size() >= BannerDesign.MAX_LAYERS ? new Denial("banner.max_layers", BannerDesign.MAX_LAYERS) : null,
							() -> {
								working.layers.add(new BannerDesign.Layer(patternId, color.getName()));
								Msg.sound(player, SoundEvents.UI_LOOM_TAKE_RESULT, 1.0f, 1.0f);
								new BannerMenu(player, guild, working).show();
							});
				}
			}
			bottomBar(() -> new PatternPicker(player, guild, working, returnPage).show());
		}
	}
}
