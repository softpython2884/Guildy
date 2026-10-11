package dev.guildy.shop;

/**
 * One shop line (pure data): a lot of {@code amount} items bought for {@code buy} gold / sold for {@code sell} gold.
 */
public class ShopEntry {
	public String item = "";
	/** Lot size. */
	public int amount = 1;
	/** Gold paid by the player for one lot (0 = not for sale). */
	public int buy = 0;
	/** Gold received by the player for one lot (0 = not bought back). */
	public int sell = 0;
	/** Guild level required. */
	public int minLevel = 1;

	public ShopEntry() {
	}

	public ShopEntry(String item, int amount, int buy, int sell, int minLevel) {
		this.item = item;
		this.amount = amount;
		this.buy = buy;
		this.sell = sell;
		this.minLevel = minLevel;
	}
}
