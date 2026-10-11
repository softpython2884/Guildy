package dev.guildy.shop;

import dev.guildy.config.ConfigHandle;
import dev.guildy.config.ConfigManager;
import dev.guildy.core.Hooks;
import dev.guildy.core.Lang;
import dev.guildy.data.Guild;
import dev.guildy.structure.BuiltStructure;
import dev.guildy.structure.StructureBehavior;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Guild shop (buy / sell for gold), opened at the guild's trade counter structure.
 */
public final class ShopModule {
	/** Structure id of the trade counter. */
	public static final String STRUCTURE = "shop";

	private static ConfigHandle<ShopConfig> config;

	private ShopModule() {
	}

	public static void init() {
		config = ConfigManager.register("shop.json5", ShopConfig.class, ShopConfig::new);
		ShopLang.register();
		StructureBehavior.register(STRUCTURE, new StructureBehavior() {
			@Override
			public void onUse(ServerPlayer player, Guild guild, BuiltStructure structure, int signIndex) {
				ShopMenus.open(player, guild);
			}

			@Override
			public List<Component> signText(Guild guild, BuiltStructure structure, int signIndex) {
				return List.of(Component.empty(), Lang.get("shop.sign.line1"), Lang.get("shop.sign.line2"), Component.empty());
			}
		});
		Hooks.openShop = ShopMenus::openFromHook;
	}

	public static ShopConfig config() {
		return config.get();
	}
}
