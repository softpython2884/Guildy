package dev.guildy.scroll;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * The timeline of a Grand spectacle fireworks show (pure Java, unit-tested), built from a seed: the same settings always
 * give the same show. Times are ticks since the cast. A rocket leaves launch point {@code point} (0 = the leftmost circle
 * as the audience sees it) at {@code launch} and bursts {@code flight} ticks later, {@code height} blocks above its point,
 * {@code drift} blocks to the audience's right and {@code depth} blocks toward them; {@link #launchSpeed} and
 * {@link #sideSpeed} give the starting velocity of a vanilla rocket that gets there (upward acceleration 0.04 per tick,
 * horizontal speed x1.15 per tick).
 *
 * <p>The acts follow one another by burst time: the opening (a signal, a wave left to right and back, pincers, a chord),
 * one cycle or more (about one every 28 s) of synchronised symmetric salvos (outside in, inside out, volleys, faster and
 * faster), figures (hearts, stars, rings, spirals, a rune circle in the sky; pentagrams for the cursed show), crossfire
 * (fans, crossings above the centre, walls of rockets from one side then the other, a running light) and a calm part
 * (slow comets, golden willows), then a crescendo from the centre outward, and the grand finale: a dense layered bouquet
 * (low crackling bursts, high large balls, every circle at once now and then), one second of silence and the last burst of
 * every circle together, the crown (or the pentagram) above them. Each act has its palette (the guild's banner colours for
 * the opening, dark blood, void and soul colours for the cursed show).
 *
 * <p>Never more than {@link Settings#maxAlive} rockets in flight at once: a beat that would go over is dropped as a whole
 * (synchronised groups stay whole and symmetric); the final burst is planned first, so it always happens.
 */
public final class ShowPlan {
	/** Ticks of the ignition: the circles light up one after the other, the first rockets leave. */
	public static final int INTRO = 20;
	/** Ticks between the end of the ignition and the first bursts (the first flights). */
	public static final int LEAD = 26;
	/** Ticks after the final burst: the sparks fall, the circles fade out. */
	public static final int OUTRO = 40;
	/** Silence (no burst) before the final burst. */
	public static final int HUSH = 22;
	public static final int MIN_TICKS = 400;
	public static final int MAX_TICKS = 2400;
	public static final int MAX_POINTS = 15;
	public static final int MIN_FLIGHT = 12;
	/** Bounds of the starting vertical speed (blocks per tick): a rocket always climbs, never shoots like an arrow. */
	public static final double MIN_SPEED = 0.03;
	public static final double MAX_SPEED = 0.9;
	/** Vanilla rocket: vertical acceleration per tick, horizontal speed factor per tick. */
	public static final double LIFT = 0.04;
	public static final double SPREAD = 1.15;

	/** Vanilla firework shapes (FireworkExplosion.Shape). */
	public enum Shape {
		SMALL_BALL, LARGE_BALL, STAR, CREEPER, BURST
	}

	/** A shape drawn with particles around a burst (see {@link ShowFigures}); WILLOW = falling golden sparks. */
	public enum Figure {
		NONE, HEART, STAR, RING, SPIRAL, RUNE, CROWN, PENTAGRAM, WILLOW
	}

	public enum Act {
		OPENING, SALVOS, FIGURES, CROSSFIRE, CALM, CRESCENDO, FINALE
	}

	/** Colours (RGB) of an act and the colours its sparks fade to. */
	public record Palette(String name, List<Integer> colors, List<Integer> fades) {
		public Palette {
			colors = List.copyOf(colors);
			fades = List.copyOf(fades);
		}

		public int color(int index) {
			return colors.get(Math.floorMod(index, colors.size()));
		}
	}

	/** One vanilla explosion of a rocket (a rocket may carry several: layered bursts). */
	public record Explosion(Shape shape, List<Integer> colors, List<Integer> fades, boolean trail, boolean twinkle) {
		public Explosion {
			colors = List.copyOf(colors);
			fades = List.copyOf(fades);
		}
	}

	/** One rocket of the show (see the class for the coordinates). */
	public record Rocket(int launch, int flight, int point, double height, double drift, double depth, List<Explosion> explosions, Figure figure,
			Act act) {
		public Rocket {
			explosions = List.copyOf(explosions);
		}

		public int burst() {
			return launch + flight;
		}
	}

	/** An act: its bursts happen in [start, end). */
	public record Span(Act act, int start, int end, Palette palette, int cycle) {
		public boolean contains(int tick) {
			return tick >= start && tick < end;
		}
	}

	/**
	 * What the show is built from (values bounded): seed, length in ticks, launch points, stage width (blocks between the
	 * first and the last point), main burst height (blocks), cursed show, the guild's banner colours (RGB, may be empty),
	 * rockets in flight at most.
	 */
	public record Settings(long seed, int ticks, int points, double width, double height, boolean cursed, List<Integer> guildColors, int maxAlive) {
		public Settings {
			ticks = Math.max(MIN_TICKS, Math.min(MAX_TICKS, ticks));
			points = Math.max(1, Math.min(MAX_POINTS, points));
			width = Double.isFinite(width) ? Math.max(0, Math.min(64, width)) : 0;
			height = Double.isFinite(height) ? Math.max(12, Math.min(48, height)) : 26;
			guildColors = guildColors == null ? List.of() : List.copyOf(guildColors);
			maxAlive = Math.max(4, Math.min(96, maxAlive));
		}
	}

	// ================================================================== palettes

	static final Palette GOLD = new Palette("gold", List.of(0xFFD24A, 0xFFF1B0, 0xFFB000), List.of(0xFFFFFF, 0xFF8A00));
	static final Palette ROYAL = new Palette("royal", List.of(0x2F6BFF, 0xFFFFFF, 0xFFD24A), List.of(0x9EC8FF));
	static final Palette OCEAN = new Palette("ocean", List.of(0x1FD1C1, 0x2A8CFF, 0xBFFCFF), List.of(0x6BE7FF, 0xFFFFFF));
	static final Palette SPRING = new Palette("spring", List.of(0x6BFF7A, 0xFF7AC8, 0xFFF06B), List.of(0xFFFFFF));
	static final Palette ROSE = new Palette("rose", List.of(0xFF3B5C, 0xFF8FB1, 0xFFFFFF), List.of(0xFFC2D6));
	static final Palette FIRE = new Palette("fire", List.of(0xFF3B1F, 0xFF8A1A, 0xFFD24A), List.of(0xFFF1B0, 0x7A1A00));
	static final Palette SILVER = new Palette("silver", List.of(0xFFFFFF, 0xCFE6FF, 0xA8B8D0), List.of(0x6E86B0));
	static final Palette RAINBOW = new Palette("rainbow", List.of(0xFF3B3B, 0xFF9F1A, 0xFFF04A, 0x4AFF6B, 0x3BB0FF, 0xB05BFF), List.of(0xFFFFFF));
	static final Palette RUNE = new Palette("rune", List.of(0xFFD24A, 0xFF4FD8, 0xFFF1B0), List.of(0xB05BFF));
	// The cursed show: blood, void, soul fire, hellfire, ash, bone; its sparks die out almost black.
	static final Palette BLOOD = new Palette("blood", List.of(0xC8102E, 0x7A0A1E, 0xFF3B4F), List.of(0x2A0010, 0x5A0A3A));
	static final Palette VOID = new Palette("void", List.of(0x8E2BFF, 0x4B1A8C, 0xD08BFF), List.of(0x14001F, 0x2E0A4A));
	static final Palette SOUL = new Palette("soul", List.of(0x3FE6FF, 0x1AA8C0, 0xBFFCFF), List.of(0x0A2A3A, 0x4B1A8C));
	static final Palette HELLFIRE = new Palette("hellfire", List.of(0xFF4A1A, 0xC8102E, 0xFFB03B), List.of(0x1A0505, 0x5A0A14));
	static final Palette ASH = new Palette("ash", List.of(0xE8E0F0, 0x9A8CB0, 0x5A4A6E), List.of(0x1A1222));
	static final Palette BONE = new Palette("bone", List.of(0xF2EAD6, 0xC4B590), List.of(0x3A0A14));
	static final Palette NIGHTMARE = new Palette("nightmare", List.of(0x8E2BFF, 0xC8102E, 0x3FE6FF, 0x5AC81E, 0xFF4FD8), List.of(0x14001F));
	/** Every colour of the cursed palettes (tests: the cursed show never uses another one). */
	static final List<Palette> CURSED_PALETTES = List.of(BLOOD, VOID, SOUL, HELLFIRE, ASH, BONE, NIGHTMARE);

	private final Settings settings;
	private final List<Span> acts;
	private final List<Rocket> rockets;

	private ShowPlan(Settings settings, List<Span> acts, List<Rocket> rockets) {
		this.settings = settings;
		this.acts = List.copyOf(acts);
		this.rockets = List.copyOf(rockets);
	}

	public static ShowPlan build(Settings settings) {
		return new Builder(settings).build();
	}

	public Settings settings() {
		return settings;
	}

	/** The acts, in order, contiguous; the last one is the finale. */
	public List<Span> acts() {
		return acts;
	}

	/** Every rocket, by launch tick. */
	public List<Rocket> rockets() {
		return rockets;
	}

	/** Length of the show (ticks): it ends here, the circles faded out. */
	public int ticks() {
		return settings.ticks();
	}

	/** Tick of the final burst (every circle at once). */
	public int finalBurst() {
		return settings.ticks() - OUTRO;
	}

	public Span finale() {
		return acts.getLast();
	}

	/** The act playing at this tick (the opening before it, the finale after it). */
	public Span spanAt(int tick) {
		for (Span span : acts) {
			if (span.contains(tick)) {
				return span;
			}
		}
		return tick < acts.getFirst().start() ? acts.getFirst() : acts.getLast();
	}

	/** Rockets in flight at this tick (launched, not burst yet). */
	public int alive(int tick) {
		int n = 0;
		for (Rocket rocket : rockets) {
			if (rocket.launch() <= tick && tick < rocket.burst()) {
				n++;
			}
		}
		return n;
	}

	// ================================================================== vanilla rocket flight

	/** Starting vertical speed (blocks per tick) of a vanilla rocket that climbs {@code height} blocks in {@code flight} ticks. */
	public static double launchSpeed(double height, int flight) {
		int k = Math.max(1, flight);
		return height / k - LIFT / 2 * (k + 1);
	}

	/** Starting horizontal speed of a vanilla rocket that drifts {@code distance} blocks sideways in {@code flight} ticks. */
	public static double sideSpeed(double distance, int flight) {
		return distance / reach(flight);
	}

	/** Horizontal distance covered in {@code flight} ticks per unit of starting speed (1.15 + 1.15² + ...). */
	static double reach(int flight) {
		int k = Math.max(1, flight);
		return SPREAD * (Math.pow(SPREAD, k) - 1) / (SPREAD - 1);
	}

	/**
	 * Flight (ticks) of a rocket bursting {@code height} blocks up when it starts at about {@code speed} blocks per tick,
	 * within the bounds of {@link #MIN_SPEED} / {@link #MAX_SPEED} and never under {@link #MIN_FLIGHT}.
	 */
	public static int flightFor(double height, double speed) {
		double b = LIFT / 2 + speed;
		int k = (int) Math.round((-b + Math.sqrt(b * b + 2 * LIFT * height)) / LIFT);
		int min = MIN_FLIGHT;
		while (launchSpeed(height, min) > MAX_SPEED && min < 200) {
			min++;
		}
		int max = Math.max(min, k);
		while (launchSpeed(height, max) >= MIN_SPEED && max < 200) {
			max++;
		}
		max = Math.max(min, max - 1);
		return Math.max(min, Math.min(max, k));
	}

	// ================================================================== builder

	private static final class Builder {
		/** Natural starting speeds: lazy comets, ordinary rockets, snappy salvos. */
		private static final double LAZY = 0.12;
		private static final double NORMAL = 0.2;
		private static final double SNAPPY = 0.3;

		private final Settings s;
		private final Random random;
		private final int n;
		private final double h;
		private final int end;
		private final int[] alive;
		private final double spacing;
		/** Centre of the stage: the middle point (the left-middle one plus half a spacing for an even count). */
		private final int centre;
		private final double centreDrift;
		private final List<Span> spans = new ArrayList<>();
		private final List<Rocket> out = new ArrayList<>();

		Builder(Settings settings) {
			this.s = settings;
			this.random = new Random(settings.seed());
			this.n = settings.points();
			this.h = settings.height();
			this.end = settings.ticks() - OUTRO;
			this.alive = new int[settings.ticks() + 1];
			this.spacing = n > 1 ? settings.width() / (n - 1) : 0;
			this.centre = (n - 1) / 2;
			this.centreDrift = n % 2 == 0 ? spacing / 2 : 0;
		}

		ShowPlan build() {
			layout();
			finalBurst();
			for (Span span : spans) {
				switch (span.act()) {
					case OPENING -> opening(span);
					case SALVOS -> salvos(span);
					case FIGURES -> figures(span);
					case CROSSFIRE -> crossfire(span);
					case CALM -> calm(span);
					case CRESCENDO -> crescendo(span);
					case FINALE -> bouquet(span);
				}
			}
			out.sort(Comparator.comparingInt(Rocket::launch).thenComparingInt(Rocket::burst).thenComparingInt(Rocket::point));
			return new ShowPlan(s, spans, out);
		}

		// -------------------------------------------------------------- acts and palettes

		/** Act windows (by burst time), scaled to the length of the show. */
		private void layout() {
			int start = INTRO + LEAD;
			int usable = end - start;
			int opening = clamp((int) Math.round(0.13 * usable), 40, 160);
			int finale = clamp((int) Math.round(0.20 * usable), 70, 300);
			int crescendo = clamp((int) Math.round(0.10 * usable), 30, 160);
			int middle = usable - opening - finale - crescendo;
			int cycles = Math.max(1, (int) Math.round(middle / 560.0));
			spans.add(new Span(Act.OPENING, start, start + opening, palette(Act.OPENING, 0), 0));
			int t = start + opening;
			Act[] acts = {Act.SALVOS, Act.FIGURES, Act.CROSSFIRE, Act.CALM};
			double[] until = {0.30, 0.55, 0.80, 1.0};
			for (int c = 0; c < cycles; c++) {
				int from = t;
				int to = start + opening + (int) Math.round(middle * (c + 1.0) / cycles);
				for (int a = 0; a < acts.length; a++) {
					int stop = a == acts.length - 1 ? to : from + (int) Math.round((to - from) * until[a]);
					spans.add(new Span(acts[a], t, stop, palette(acts[a], c), c));
					t = stop;
				}
			}
			spans.add(new Span(Act.CRESCENDO, t, t + crescendo, palette(Act.CRESCENDO, 0), 0));
			t += crescendo;
			spans.add(new Span(Act.FINALE, t, end + 1, palette(Act.FINALE, 0), 0));
		}

		private Palette palette(Act act, int cycle) {
			if (s.cursed()) {
				return switch (act) {
					case OPENING -> BLOOD;
					case SALVOS -> pick(cycle, VOID, SOUL, BLOOD);
					case FIGURES -> pick(cycle, VOID, SOUL, VOID);
					case CROSSFIRE -> pick(cycle, HELLFIRE, BLOOD, HELLFIRE);
					case CALM -> ASH;
					case CRESCENDO -> NIGHTMARE;
					case FINALE -> VOID;
				};
			}
			return switch (act) {
				case OPENING -> guild();
				case SALVOS -> pick(cycle, ROYAL, OCEAN, SPRING);
				case FIGURES -> ROSE;
				case CROSSFIRE -> pick(cycle, FIRE, SPRING, FIRE);
				case CALM -> SILVER;
				case CRESCENDO -> RAINBOW;
				case FINALE -> GOLD;
			};
		}

		/** The guild's banner colours with a golden accent (gold without a guild). */
		private Palette guild() {
			if (s.guildColors().isEmpty()) {
				return GOLD;
			}
			List<Integer> colors = new ArrayList<>(s.guildColors());
			if (colors.size() < 3) {
				colors.add(0xFFD24A);
			}
			return new Palette("guild", colors, List.of(0xFFFFFF));
		}

		private static Palette pick(int cycle, Palette... palettes) {
			return palettes[Math.floorMod(cycle, palettes.length)];
		}

		// -------------------------------------------------------------- rockets and the flight cap

		private Rocket rocket(int burst, int point, double height, double drift, double depth, double speed, Act act, Figure figure,
				Explosion... explosions) {
			double top = Math.max(10, Math.min(h * 1.4, height));
			int flight = flightFor(top, speed);
			return new Rocket(burst - flight, flight, point, top, drift, depth, List.of(explosions), figure, act);
		}

		/** The rocket of point {@code i} and the mirrored one (drifting the other way): one rocket on the middle point. */
		private List<Rocket> pair(int burst, int i, double height, double drift, double speed, Act act, Explosion explosion) {
			int left = Math.min(i, n - 1 - i);
			int right = n - 1 - left;
			if (left == right) {
				return List.of(rocket(burst, left, height, 0, 0, speed, act, Figure.NONE, explosion));
			}
			return List.of(rocket(burst, left, height, -drift, 0, speed, act, Figure.NONE, explosion),
					rocket(burst, right, height, drift, 0, speed, act, Figure.NONE, explosion));
		}

		private Rocket middle(int burst, double height, double speed, Act act, Figure figure, Explosion... explosions) {
			return rocket(burst, centre, height, centreDrift, 0, speed, act, figure, explosions);
		}

		private boolean add(Rocket rocket) {
			return group(List.of(rocket));
		}

		/** Adds every rocket or none: inside the show, on a point of the stage, the flight cap kept. */
		private boolean group(List<Rocket> rockets) {
			for (Rocket rocket : rockets) {
				if (rocket.launch() < 0 || rocket.burst() > end || rocket.point() < 0 || rocket.point() >= n) {
					return false;
				}
			}
			for (Rocket rocket : rockets) {
				for (int t = rocket.launch(); t < rocket.burst(); t++) {
					alive[t]++;
				}
			}
			boolean fits = true;
			for (Rocket rocket : rockets) {
				for (int t = rocket.launch(); t < rocket.burst() && fits; t++) {
					fits = alive[t] <= s.maxAlive();
				}
			}
			if (!fits) {
				for (Rocket rocket : rockets) {
					for (int t = rocket.launch(); t < rocket.burst(); t++) {
						alive[t]--;
					}
				}
				return false;
			}
			out.addAll(rockets);
			return true;
		}

		/** An explosion in {@code count} colours of the palette (from a random one), fading or not. */
		private Explosion explosion(Shape shape, Palette palette, int count, boolean trail, boolean twinkle) {
			List<Integer> colors = new ArrayList<>();
			int offset = random.nextInt(palette.colors().size());
			for (int i = 0; i < Math.min(count, palette.colors().size()); i++) {
				colors.add(palette.color(offset + i));
			}
			List<Integer> fades = palette.fades().isEmpty() || random.nextInt(3) == 0 ? List.of()
					: List.of(palette.fades().get(random.nextInt(palette.fades().size())));
			return new Explosion(shape, colors, fades, trail, twinkle);
		}

		private static int clamp(int value, int min, int max) {
			return Math.max(min, Math.min(max, value));
		}

		// -------------------------------------------------------------- the opening

		/** A signal from the centre, a wave left to right, one back, pincers from both ends while time allows, a chord. */
		private void opening(Span span) {
			Palette p = span.palette();
			int t = span.start();
			int z = span.end();
			if (z - t >= 64) {
				List<Integer> signal = s.cursed() ? List.of(0xBFFCFF, 0xD08BFF) : List.of(0xFFFFFF, 0xFFD24A);
				add(middle(t, h * 1.15, NORMAL, Act.OPENING, Figure.NONE, new Explosion(Shape.STAR, signal, List.of(), false, true)));
				t += 16;
			}
			int step = clamp((z - t - 12) / (2 * n + 2), 2, 6);
			double rise = Math.max(1, n - 1);
			for (int i = 0; i < n && t + i * step < z; i++) {
				add(rocket(t + i * step, i, h * (0.72 + 0.28 * i / rise), 0, 0, NORMAL, Act.OPENING, Figure.NONE,
						new Explosion(Shape.SMALL_BALL, List.of(p.color(i), p.color(i + 1)), List.of(), true, false)));
			}
			t += n * step + 5;
			for (int i = 0; i < n && t + i * step < z; i++) {
				int point = n - 1 - i;
				add(rocket(t + i * step, point, h * (1.0 - 0.28 * i / rise), 0, 0, NORMAL, Act.OPENING, Figure.NONE,
						new Explosion(i % 2 == 0 ? Shape.STAR : Shape.BURST, List.of(p.color(point + 1)), p.fades(), false, false)));
			}
			t += n * step + 5;
			int half = (n + 1) / 2;
			for (int pincer = 0; pincer < 2 && t + half * step + 8 < z; pincer++) {
				Explosion e = explosion(Shape.SMALL_BALL, p, 2, false, pincer == 1);
				for (int i = 0; i < half; i++) {
					group(pair(t + i * step, i, h * (0.8 + 0.1 * pincer), 0.6, NORMAL, Act.OPENING, e));
				}
				t += half * step + 8;
			}
			if (t < z) {
				List<Rocket> chord = new ArrayList<>(pair(t, 0, h * 1.05, 1.0, NORMAL, Act.OPENING, explosion(Shape.LARGE_BALL, p, 2, false, true)));
				chord.add(middle(t, h * 1.1, NORMAL, Act.OPENING, Figure.NONE, explosion(Shape.LARGE_BALL, p, 3, false, false)));
				group(chord);
			}
		}

		// -------------------------------------------------------------- synchronised symmetric salvos

		/**
		 * Mirrored pairs outside in (each answered by a higher echo), inside out (rising), then volleys of every point; faster
		 * each phrase.
		 */
		private void salvos(Span span) {
			Palette p = span.palette();
			int t = span.start() + 2;
			int z = span.end();
			int half = (n + 1) / 2;
			int interval = 11;
			for (int phrase = 0; t < z - 2; phrase++) {
				switch (phrase % 3) {
					case 0 -> {
						for (int i = 0; i < half && t < z - 2; i++) {
							boolean outer = i == 0;
							group(pair(t, i, h * (0.95 - 0.2 * i / half), 0.4 + 0.25 * (half - i), NORMAL, Act.SALVOS,
									explosion(outer ? Shape.LARGE_BALL : Shape.SMALL_BALL, p, 2, false, outer)));
							if (t + 3 < z) {
								group(pair(t + 3, i, h * (1.2 - 0.2 * i / half), 0.8 + 0.3 * (half - i), SNAPPY, Act.SALVOS,
										explosion(Shape.STAR, p, 1, false, true)));
							}
							t += interval;
						}
					}
					case 1 -> {
						for (int i = half - 1; i >= 0 && t < z - 2; i--) {
							group(pair(t, i, h * (0.8 + 0.3 * (half - 1 - i) / half), 0.5, SNAPPY, Act.SALVOS,
									explosion(i % 2 == 0 ? Shape.STAR : Shape.SMALL_BALL, p, 2, false, true)));
							t += interval;
						}
					}
					default -> {
						List<Rocket> volley = new ArrayList<>();
						Explosion high = explosion(Shape.LARGE_BALL, p, 2, false, false);
						Explosion low = explosion(Shape.BURST, p, 2, false, true);
						for (int i = 0; i < half; i++) {
							volley.addAll(pair(t, i, h * (i % 2 == 0 ? 1.1 : 0.8), 0.3, NORMAL, Act.SALVOS, i % 2 == 0 ? high : low));
						}
						group(volley);
						t += interval * 2;
					}
				}
				interval = Math.max(5, interval - 2);
			}
		}

		// -------------------------------------------------------------- figures

		/** One shaped burst at a time above the centre, fountains on the outer circles in between. */
		private void figures(Span span) {
			List<Figure> figures = s.cursed() ? List.of(Figure.PENTAGRAM, Figure.RING, Figure.SPIRAL, Figure.RUNE)
					: List.of(Figure.HEART, Figure.STAR, Figure.RING, Figure.SPIRAL, Figure.RUNE);
			int t = span.start() + 4;
			int k = span.cycle() * 2;
			while (t < span.end() - 4) {
				Figure figure = figures.get(k % figures.size());
				Palette colors = figurePalette(figure, span.palette());
				Shape shape = figure == Figure.STAR ? Shape.STAR : Shape.SMALL_BALL;
				add(middle(t, h * 1.05, NORMAL, Act.FIGURES, figure,
						new Explosion(shape, List.of(colors.color(0), colors.color(1)), List.of(), false, figure == Figure.RUNE)));
				if (s.cursed() && k % 2 == 1 && n >= 3 && t + 6 < span.end()) {
					// Skulls: bone-white creeper faces from the outer circles.
					group(pair(t + 6, 0, h * 0.8, 0.5, NORMAL, Act.FIGURES, new Explosion(Shape.CREEPER, BONE.colors(), BONE.fades(), false, false)));
				}
				if (t + 14 < span.end() && n >= 3) {
					group(pair(t + 14, 0, h * 0.5, 0, SNAPPY, Act.FIGURES, explosion(Shape.BURST, span.palette(), 2, false, false)));
				}
				if (t + 22 < span.end() && n >= 5) {
					group(pair(t + 22, n / 4, h * 0.7, 0.6, NORMAL, Act.FIGURES, explosion(Shape.STAR, span.palette(), 2, false, true)));
				}
				t += 30 + random.nextInt(6);
				k++;
			}
		}

		private Palette figurePalette(Figure figure, Palette act) {
			return switch (figure) {
				case HEART -> ROSE;
				case STAR, CROWN, WILLOW -> GOLD;
				case RUNE -> s.cursed() ? NIGHTMARE : RUNE;
				case PENTAGRAM -> BLOOD;
				default -> act;
			};
		}

		// -------------------------------------------------------------- crossfire

		/** Fans from the centre, crossings above it, walls of rockets from one side then the other, a running light. */
		private void crossfire(Span span) {
			Palette p = span.palette();
			int t = span.start() + 2;
			int z = span.end();
			double fan = Math.max(1.5, s.width() / 8);
			for (int k = span.cycle(); t < z - 2; k++) {
				switch (k % 4) {
					case 0 -> {
						Explosion e = explosion(Shape.SMALL_BALL, p, 2, false, false);
						List<Rocket> rockets = new ArrayList<>();
						for (int j = -2; j <= 2; j++) {
							rockets.add(rocket(t, centre, h * (1.0 - 0.06 * Math.abs(j)), centreDrift + j * fan, 1.0, NORMAL, Act.CROSSFIRE,
									Figure.NONE, e));
						}
						group(rockets);
						t += 18;
					}
					case 1 -> {
						double across = s.width() * 0.42;
						group(pair(t, 0, h * 0.95, -across, NORMAL, Act.CROSSFIRE, explosion(Shape.STAR, p, 2, false, true)));
						if (n >= 6 && t + 6 < z) {
							group(pair(t + 6, 1, h * 1.1, -across * 0.8, NORMAL, Act.CROSSFIRE, explosion(Shape.SMALL_BALL, p, 2, false, false)));
						}
						t += 22;
					}
					case 2 -> {
						int side = Math.max(1, n / 2);
						for (int j = 0; j < 4 && t < z - 2; j++) {
							Explosion e = explosion(j % 2 == 0 ? Shape.SMALL_BALL : Shape.BURST, p, 2, false, false);
							List<Rocket> wall = new ArrayList<>();
							for (int i = 0; i < side; i++) {
								wall.add(rocket(t, j % 2 == 0 ? i : n - 1 - i, h * (0.7 + 0.1 * j), 0, 0, SNAPPY, Act.CROSSFIRE, Figure.NONE, e));
							}
							group(wall);
							t += 7;
						}
						t += 6;
					}
					default -> {
						for (int i = 0; i < 2 * n && t < z - 2; i++) {
							int point = i < n ? i : 2 * n - 1 - i;
							add(rocket(t, point, h * 0.55, 0, 0, SNAPPY, Act.CROSSFIRE, Figure.NONE,
									new Explosion(Shape.BURST, List.of(p.color(i)), List.of(), false, false)));
							t += 2;
						}
						t += 6;
					}
				}
			}
		}

		// -------------------------------------------------------------- calm

		/** Slow, high, twinkling comets one at a time (sometimes a mirrored pair), golden willows. */
		private void calm(Span span) {
			Palette p = span.palette();
			int t = span.start() + 8;
			for (int k = 0; t < span.end() - 4; k++) {
				if (k % 3 == 2 && n >= 3) {
					group(pair(t, n / 4, h * 1.2, 0.8, LAZY, Act.CALM, explosion(Shape.SMALL_BALL, p, 2, true, true)));
				} else if (k % 4 == 1) {
					Palette willow = s.cursed() ? VOID : GOLD;
					add(rocket(t, around(), h * 1.25, 0, 0, LAZY, Act.CALM, Figure.WILLOW,
							new Explosion(Shape.SMALL_BALL, List.of(willow.color(0), willow.color(1)), List.of(willow.fades().getLast()), true, false)));
				} else {
					add(rocket(t, around(), h * 1.25, 0, 0, LAZY, Act.CALM, Figure.NONE, explosion(Shape.LARGE_BALL, p, 2, false, true)));
				}
				t += 24 + random.nextInt(12);
			}
		}

		/** A point near the centre. */
		private int around() {
			return clamp(centre + (int) Math.round(random.nextGaussian() * n / 5.0), 0, n - 1);
		}

		// -------------------------------------------------------------- crescendo

		/** From the centre outward, faster and higher; pairs once it races. */
		private void crescendo(Span span) {
			Palette p = span.palette();
			List<Integer> order = new ArrayList<>();
			order.add(centre);
			for (int d = 1; order.size() < n; d++) {
				if (centre - d >= 0) {
					order.add(centre - d);
				}
				if (centre + d < n && order.size() < n) {
					order.add(centre + d);
				}
			}
			int t = span.start();
			double interval = 12;
			for (int k = 0; t < span.end() - 1; k++) {
				double progress = (t - span.start()) / (double) Math.max(1, span.end() - span.start());
				int point = order.get(k % n);
				Explosion e = explosion(k % 3 == 0 ? Shape.STAR : Shape.SMALL_BALL, p, 2, false, progress > 0.6);
				if (interval <= 5) {
					group(pair(t, point, h * (0.7 + 0.5 * progress), 0.4, SNAPPY, Act.CRESCENDO, e));
				} else {
					add(rocket(t, point, h * (0.7 + 0.5 * progress), 0, 0, SNAPPY, Act.CRESCENDO, Figure.NONE, e));
				}
				t += (int) Math.max(2, Math.round(interval));
				interval *= 0.85;
			}
		}

		// -------------------------------------------------------------- the grand finale

		/**
		 * The dense bouquet up to the silence before the final burst: a low crackling layer (more pairs on a wider stage), high
		 * large balls, golden willows, every circle at once now and then; faster and faster (the flight cap thins it out).
		 */
		private void bouquet(Span span) {
			Palette p = span.palette();
			Palette mix = s.cursed() ? pick(random.nextInt(3), BLOOD, SOUL, NIGHTMARE) : RAINBOW;
			int quiet = end - HUSH;
			int t = span.start();
			for (int beat = 0; t < quiet; beat++) {
				double progress = (t - span.start()) / (double) Math.max(1, quiet - span.start());
				int half = (n + 1) / 2;
				for (int low = 0; low < 1 + n / 8; low++) {
					group(pair(t, random.nextInt(half), h * (0.55 + 0.15 * random.nextDouble()), 0.5, SNAPPY, Act.FINALE,
							explosion(random.nextBoolean() ? Shape.SMALL_BALL : Shape.BURST, (beat + low) % 2 == 0 ? p : mix, 2, false, true)));
				}
				if (beat % 3 == 0) {
					group(pair(t, random.nextInt(half), h * (1.1 + 0.2 * random.nextDouble()), 1.0, NORMAL, Act.FINALE,
							explosion(Shape.LARGE_BALL, beat % 6 == 0 ? p : mix, 3, false, beat % 2 == 0)));
				}
				if (beat % 7 == 3 && progress > 0.3) {
					add(middle(t, h * 1.2, NORMAL, Act.FINALE, Figure.WILLOW, explosion(Shape.SMALL_BALL, s.cursed() ? VOID : GOLD, 2, true, false)));
				}
				if (beat % 12 == 11) {
					List<Rocket> tutti = new ArrayList<>();
					Explosion e = explosion(Shape.SMALL_BALL, mix, 3, false, true);
					for (int i = 0; i < half; i++) {
						tutti.addAll(pair(t, i, h * 0.9, 0.4, SNAPPY, Act.FINALE, e));
					}
					group(tutti);
				}
				t += progress < 0.5 ? 4 : progress < 0.8 ? 3 : 2;
			}
		}

		/** The last burst: every circle at once (large balls, a star layer) and the crown (or pentagram) above them. */
		private void finalBurst() {
			Palette main = s.cursed() ? BLOOD : guild();
			Palette second = s.cursed() ? SOUL : GOLD;
			List<Integer> big = new ArrayList<>(main.colors());
			big.add(s.cursed() ? 0x8E2BFF : 0xFFFFFF);
			Explosion large = new Explosion(Shape.LARGE_BALL, big, s.cursed() ? List.of(0x14001F) : List.of(0xFFF1B0), false, true);
			Explosion star = new Explosion(Shape.STAR, second.colors(), second.fades(), false, true);
			int half = (n + 1) / 2;
			List<Rocket> burst = new ArrayList<>();
			burst.add(middle(end, h * 1.35, NORMAL, Act.FINALE, s.cursed() ? Figure.PENTAGRAM : Figure.CROWN,
					new Explosion(Shape.LARGE_BALL, s.cursed() ? VOID.colors() : GOLD.colors(), List.of(), false, true),
					new Explosion(Shape.STAR, s.cursed() ? BLOOD.colors() : List.of(0xFFFFFF), List.of(), false, false)));
			for (int i = 0; i < half; i++) {
				burst.addAll(pair(end, i, h * 1.2, 0.8, NORMAL, Act.FINALE, large));
			}
			for (int i = 0; i < half; i += 2) {
				burst.addAll(pair(end, i, h * 0.85, 0.4, SNAPPY, Act.FINALE, star));
			}
			for (Rocket rocket : burst) {
				add(rocket);
			}
		}
	}
}
