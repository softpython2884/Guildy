package dev.guildy.villager;

import dev.guildy.alchemy.AlchemyModule;
import dev.guildy.alchemy.BrewBook;
import dev.guildy.alchemy.Brews;
import dev.guildy.alchemy.PotionKey;
import dev.guildy.data.Guild;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What an alchemist can brew with what it carries and what the storage holds ({@link BrewBook#choose} on the
 * Minecraft side): the bottles it may start from (water bottles, glass bottles to fill when there is water near a
 * stand, bottles it carries, bottles the storage holds beyond their own stock target), the reagents of a chain and the
 * fuel of its brews.
 */
final class PotionPlanner {
	/** Supplies an alchemist (or the whole guild) can use: carried items plus what the storage gives. */
	record View(Map<Item, Integer> plain, Map<String, Integer> bottles, int fuelBrews, boolean fillable) {
		int plain(@Nullable Item item) {
			return item == null ? 0 : plain.getOrDefault(item, 0);
		}

		int bottles(String key) {
			return bottles.getOrDefault(key, 0);
		}
	}

	/** A brewable plan and the bottles it may take (capped by the batch size). */
	record Pick(BrewBook.Choice choice, int startBottles) {
	}

	private PotionPlanner() {
	}

	/**
	 * Supplies seen by an alchemist: its bag (or the bags of all the guild's alchemists) plus what the storage gives. Bottles
	 * of the storage only count beyond their own stock target (a kept potion is never brewed further).
	 */
	static View view(Guild guild, AlchemyGuild.Stock stock, List<List<ItemStack>> bags, int fuelLeft, boolean fillable) {
		AlchemistConfig cfg = VillagerModule.config().alchemist;
		Map<Item, Integer> plain = new HashMap<>(stock.plain);
		Map<String, Integer> bottles = new HashMap<>();
		for (Map.Entry<String, Integer> entry : stock.bottles.entrySet()) {
			int spare = entry.getValue() - AlchemyGuild.target(guild, entry.getKey());
			if (spare > 0) {
				bottles.put(entry.getKey(), spare);
			}
		}
		for (List<ItemStack> bag : bags) {
			for (ItemStack stack : bag) {
				String key = Brews.keyOf(stack);
				if (key != null) {
					bottles.merge(key, stack.getCount(), Integer::sum);
				} else if (stack.getComponentsPatch().isEmpty()) {
					plain.merge(stack.getItem(), stack.getCount(), Integer::sum);
				}
			}
		}
		Item fuel = Brews.item(cfg.brewFuel);
		int fuelBrews = Math.max(0, fuelLeft) + (fuel == null ? 0 : plain.getOrDefault(fuel, 0) * cfg.brewsPerFuel);
		return new View(plain, bottles, fuelBrews, fillable);
	}

	/** Bottles the chain may start from: its start product, plus glass bottles to fill for a water start. */
	static int startBottles(View view, String start) {
		int bottles = view.bottles(start);
		if (start.equals(PotionKey.WATER.text()) && view.fillable()) {
			bottles += view.plain(Items.GLASS_BOTTLE);
		}
		return bottles;
	}

	/** What to brew for the guild with these supplies ({@code except}: this alchemist's own batch is not counted held). */
	static BrewBook.Choice choose(ServerLevel level, Guild guild, AlchemyGuild shared, View view, @Nullable UUID except) {
		BrewBook book = Brews.book();
		Set<String> sources = new HashSet<>();
		sources.add(PotionKey.WATER.text());
		for (Map.Entry<String, Integer> entry : view.bottles().entrySet()) {
			if (entry.getValue() > 0) {
				sources.add(entry.getKey());
			}
		}
		Map<String, Integer> targets = AlchemyGuild.targets(guild);
		Map<String, Integer> held = shared.held(level, guild, except);
		return book.choose(AlchemyModule.config().menuProducts(), targets, held, sources, chain -> affordable(view, chain));
	}

	/** Bottles to start from, reagents of every brew and fuel for all of them. */
	static boolean affordable(View view, List<BrewBook.Step> chain) {
		if (chain.isEmpty() || chain.size() > view.fuelBrews()) {
			return false;
		}
		if (startBottles(view, chain.getFirst().from()) <= 0) {
			return false;
		}
		for (Map.Entry<String, Integer> entry : BrewBook.reagents(chain).entrySet()) {
			Item item = Brews.item(entry.getKey());
			if (item == null || view.plain(item) < entry.getValue()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Why nothing missing can be brewed (lang key + its argument): no bottle at all, no fuel, else the reagents of the most
	 * wanted product.
	 */
	static String[] missing(View view, @Nullable String wanted) {
		boolean bottles = view.bottles(PotionKey.WATER.text()) > 0 || (view.fillable() && view.plain(Items.GLASS_BOTTLE) > 0);
		if (!bottles) {
			for (Map.Entry<String, Integer> entry : view.bottles().entrySet()) {
				if (entry.getValue() > 0) {
					bottles = true;
					break;
				}
			}
		}
		if (!bottles) {
			return new String[]{"no_bottles", ""};
		}
		if (view.fuelBrews() <= 0) {
			return new String[]{"no_fuel", AlchemistLang.itemName(VillagerModule.config().alchemist.brewFuel)};
		}
		return new String[]{"no_reagents", wanted == null ? "?" : Brews.name(wanted)};
	}
}
