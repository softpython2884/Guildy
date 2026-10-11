package dev.guildy.trade;

import dev.guildy.config.ConfigHandle;
import dev.guildy.config.ConfigManager;

/**
 * Trade: the trade hub structure ("Carrefour commercial") with its tiers and visiting merchants, and the merchant
 * spawner shared with the event module (caravans, book merchant). Config: config/guildy/trade.json5.
 */
public final class TradeModule {
	private static final TradeConfig DEFAULTS = new TradeConfig();
	private static ConfigHandle<TradeConfig> config;

	private TradeModule() {
	}

	public static void init() {
		config = ConfigManager.register("trade.json5", TradeConfig.class, TradeConfig::new);
		TradeLang.register();
		Merchants.init();
		TradeHub.init();
		TradeCommands.register();
	}

	public static TradeConfig config() {
		return config != null ? config.get() : DEFAULTS;
	}
}
