package dev.guildy.shop;

import java.util.ArrayList;
import java.util.List;

/**
 * A shop category (pure data).
 */
public class ShopCategory {
	public String id = "";
	public String name = "";
	public String icon = "minecraft:chest";
	public List<ShopEntry> entries = new ArrayList<>();

	public ShopCategory() {
	}

	public ShopCategory(String id, String name, String icon, List<ShopEntry> entries) {
		this.id = id;
		this.name = name;
		this.icon = icon;
		this.entries = entries;
	}
}
