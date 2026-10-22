package dev.guildy.alchemy;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Simple brewing (alchemy.json5 {@code brewing.mode: "simple"}, pure, unit tested): every product costs only blaze powder
 * and time, from its class (plain, long or strong potion, in a drinkable, splash or lingering bottle; elixir; magic
 * elixir) or from its own entry in {@code brewing.products}. One batch makes up to {@code alchemist.bottlesPerBrew} bottles
 * for one cost (a smaller batch pays its share, rounded up); the time does not depend on the number of bottles, like a
 * vanilla brewing stand. Chooses what to brew from the stock targets with the blaze powder at hand.
 */
public final class SimpleBrewing {
	/** How the alchemist brews. */
	public enum Mode {
		/** Blaze powder and time only (default). */
		SIMPLE("simple"),
		/** The brewing steps of {@code recipes} (bottles, nether wart, reagents, fuel), like a vanilla brewing stand. */
		RECIPES("recipes");

		private final String id;

		Mode(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		/** The mode of a config text, null when it is not one. */
		public static @Nullable Mode parse(@Nullable String text) {
			String value = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
			for (Mode mode : values()) {
				if (mode.id.equals(value)) {
					return mode;
				}
			}
			return null;
		}
	}

	/** Strength of a product, the first part of its class. */
	public enum Base {
		/** A potion without long_ / strong_ (healing, swiftness, awkward...). */
		PLAIN,
		/** A long_ potion (longer effect, redstone in vanilla). */
		LONG,
		/** A strong_ potion (level II, glowstone in vanilla). */
		STRONG,
		/** A Guildy elixir without magic boost. */
		ELIXIR,
		/** A Guildy elixir boosting the spells (arcanique, clairvoyance, satiete...). */
		MAGIC_ELIXIR
	}

	/** Class of a product: its strength and its bottle ({@link PotionKey.Form#ELIXIR} for the elixirs). */
	public record Kind(Base base, PotionKey.Form form) {
	}

	/** Cost of a batch: blaze powder used up and seconds of work. */
	public record Cost(int blaze, int seconds) {
		public Cost plus(Cost other) {
			return new Cost(blaze + other.blaze, seconds + other.seconds);
		}
	}

	/** What {@link #choose} found. */
	public enum Verdict {
		/** A product below its target, affordable. */
		BREW,
		/** Every product reached its target (or none has one). */
		STOCK_FULL,
		/** Products are missing but the blaze powder at hand pays for none of them. */
		NO_BLAZE,
		/** Only elixirs are missing and this alchemist's elixirs of the day are done. */
		ELIXIR_CAP
	}

	/**
	 * Result of {@link #choose}: the product (BREW; the most wanted one that the blaze powder does not pay for with
	 * NO_BLAZE; the most wanted elixir with ELIXIR_CAP), the bottles of the batch, the blaze powder it uses up and the
	 * cost of a full batch (its seconds are the time of the batch).
	 */
	public record Choice(Verdict verdict, @Nullable String product, int bottles, int blaze, Cost cost) {
		static final Choice FULL = new Choice(Verdict.STOCK_FULL, null, 0, 0, new Cost(0, 0));

		public int seconds() {
			return cost.seconds();
		}

		/** The same batch with fewer bottles (the blaze powder of its share). */
		public Choice resized(int newBottles, int fullBatch) {
			int count = Math.max(0, Math.min(bottles, newBottles));
			return new Choice(verdict, product, count, blazeFor(cost, count, fullBatch), cost);
		}
	}

	private SimpleBrewing() {
	}

	/**
	 * Class of a product ("splash:strong_healing" = STRONG in a splash bottle, "elixir:arcanique" = MAGIC_ELIXIR when
	 * {@code magic} says so), null when the text is not a product.
	 */
	public static @Nullable Kind classify(@Nullable String product, Predicate<String> magic) {
		PotionKey key = PotionKey.parse(product);
		if (key == null) {
			return null;
		}
		if (key.isElixir()) {
			return new Kind(magic.test(key.id()) ? Base.MAGIC_ELIXIR : Base.ELIXIR, PotionKey.Form.ELIXIR);
		}
		String id = key.id().contains(":") ? key.id().substring(key.id().indexOf(':') + 1) : key.id();
		Base base = id.startsWith("strong_") ? Base.STRONG : id.startsWith("long_") ? Base.LONG : Base.PLAIN;
		return new Kind(base, key.form());
	}

	/** Cost of a class: the cost of its strength, plus the extra of a splash or lingering bottle. */
	public static Cost cost(Kind kind, AlchemyConfig.Brewing brewing) {
		Cost base = switch (kind.base()) {
			case PLAIN -> brewing.plainPotion.cost();
			case LONG -> brewing.longPotion.cost();
			case STRONG -> brewing.strongPotion.cost();
			case ELIXIR -> brewing.elixir.cost();
			case MAGIC_ELIXIR -> brewing.magicElixir.cost();
		};
		return switch (kind.form()) {
			case SPLASH -> base.plus(brewing.splashExtra.cost());
			case LINGERING -> base.plus(brewing.lingeringExtra.cost());
			default -> base;
		};
	}

	/**
	 * Cost of a full batch of a product: its own entry of {@code brewing.products}, else the cost of its class (an elixir
	 * is magic when its definition boosts the spells). Null when the text is not a product.
	 */
	public static @Nullable Cost cost(@Nullable String product, AlchemyConfig config) {
		String key = PotionKey.canonical(product);
		if (key == null) {
			return null;
		}
		AlchemyConfig.Brewing brewing = config.brewing == null ? new AlchemyConfig.Brewing() : config.brewing;
		AlchemyConfig.CostDef own = brewing.products == null ? null : brewing.products.get(key);
		if (own != null) {
			return own.cost();
		}
		Kind kind = classify(key, id -> {
			AlchemyConfig.ElixirDef def = config.elixir(id);
			return def != null && def.magical();
		});
		return kind == null ? null : cost(kind, brewing);
	}

	/** Blaze powder of a batch of {@code bottles} out of {@code fullBatch}: its share of the cost, rounded up. */
	public static int blazeFor(Cost cost, int bottles, int fullBatch) {
		int full = Math.max(1, fullBatch);
		int count = Math.max(0, Math.min(bottles, full));
		if (count <= 0 || cost.blaze() <= 0) {
			return 0;
		}
		return (int) Math.ceil((double) cost.blaze() * count / full);
	}

	/**
	 * What to brew: among the {@code products} (menu order) below their target ({@code held} counts what the guild holds,
	 * batches in progress included) that {@code brewable} accepts, the furthest below it first (ties: menu order), the
	 * first one the {@code blaze} powder at hand pays for. A batch takes {@code limit} bottles at most (the daily cap left,
	 * at most {@code fullBatch}); an elixir at most {@code elixirsLeft} (none when it is 0). A product the blaze powder does
	 * not pay for is skipped for a cheaper one.
	 */
	public static Choice choose(List<String> products, Map<String, Integer> targets, Map<String, Integer> held, Predicate<String> brewable,
			Function<String, @Nullable Cost> costs, int blaze, int fullBatch, int limit, int elixirsLeft) {
		List<String> wanted = new ArrayList<>();
		for (String product : products) {
			if (BrewBook.shortage(targets.getOrDefault(product, 0), held.getOrDefault(product, 0)) > 0 && !wanted.contains(product)
					&& costs.apply(product) != null && brewable.test(product)) {
				wanted.add(product);
			}
		}
		if (wanted.isEmpty()) {
			return Choice.FULL;
		}
		// Stable sort: equal shortages keep the menu order.
		wanted.sort(Comparator.comparingDouble(p -> -BrewBook.shortage(targets.getOrDefault(p, 0), held.getOrDefault(p, 0))));
		int full = Math.max(1, fullBatch);
		int batch = Math.max(1, Math.min(limit, full));
		Choice unpaid = null;
		Choice capped = null;
		for (String product : wanted) {
			Cost cost = costs.apply(product);
			boolean elixir = product.startsWith(PotionKey.Form.ELIXIR.prefix());
			int bottles = elixir ? Math.min(batch, Math.max(0, elixirsLeft)) : batch;
			if (bottles <= 0) {
				if (capped == null) {
					capped = new Choice(Verdict.ELIXIR_CAP, product, 0, 0, cost);
				}
				continue;
			}
			int need = blazeFor(cost, bottles, full);
			if (need <= Math.max(0, blaze)) {
				return new Choice(Verdict.BREW, product, bottles, need, cost);
			}
			if (unpaid == null) {
				unpaid = new Choice(Verdict.NO_BLAZE, product, bottles, need, cost);
			}
		}
		return unpaid != null ? unpaid : capped;
	}
}
