package dev.guildy.scroll;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Outlines of the figures a show draws in the sky around a burst (pure Java, unit-tested): points (u, v) in [-1, 1], u to
 * the audience's right, v up, spread evenly along the strokes of the figure.
 */
public final class ShowFigures {
	/** Points of every outline. */
	public static final int POINTS = 56;
	private static final Map<ShowPlan.Figure, List<double[]>> CACHE = Collections.synchronizedMap(new EnumMap<>(ShowPlan.Figure.class));

	private ShowFigures() {
	}

	/** The outline of a figure ({@link #POINTS} points; none for NONE and WILLOW, which have no outline). */
	public static List<double[]> outline(ShowPlan.Figure figure) {
		return CACHE.computeIfAbsent(figure, f -> Collections.unmodifiableList(sample(strokes(f), POINTS)));
	}

	/** The strokes (polylines) of a figure. */
	static List<List<double[]>> strokes(ShowPlan.Figure figure) {
		List<List<double[]>> strokes = new ArrayList<>();
		switch (figure) {
			case HEART -> {
				List<double[]> heart = new ArrayList<>();
				for (int i = 0; i <= 96; i++) {
					double t = Math.PI * 2 * i / 96;
					double x = 16 * Math.pow(Math.sin(t), 3);
					double y = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);
					heart.add(new double[] {x / 17.0, (y + 2.5) / 15.0});
				}
				strokes.add(heart);
			}
			case STAR -> strokes.add(star(5, 1.0, 0.42, Math.PI / 2));
			case RING -> strokes.add(circle(1.0, 64));
			case SPIRAL -> {
				for (int arm = 0; arm < 2; arm++) {
					List<double[]> line = new ArrayList<>();
					for (int i = 0; i <= 48; i++) {
						double r = 0.12 + 0.88 * i / 48.0;
						double a = arm * Math.PI + r * Math.PI * 2.2;
						line.add(new double[] {Math.cos(a) * r, Math.sin(a) * r});
					}
					strokes.add(line);
				}
			}
			case RUNE -> {
				strokes.add(circle(1.0, 64));
				strokes.add(circle(0.55, 40));
				// Hexagram: two triangles.
				for (int tri = 0; tri < 2; tri++) {
					List<double[]> line = new ArrayList<>();
					for (int i = 0; i <= 3; i++) {
						double a = Math.PI / 2 + tri * Math.PI + i * Math.PI * 2 / 3;
						line.add(new double[] {Math.cos(a) * 0.8, Math.sin(a) * 0.8});
					}
					strokes.add(line);
				}
			}
			case CROWN -> {
				strokes.add(List.of(new double[] {-0.8, -0.55}, new double[] {-0.85, 0.45}, new double[] {-0.42, -0.02}, new double[] {0.0, 0.7},
						new double[] {0.42, -0.02}, new double[] {0.85, 0.45}, new double[] {0.8, -0.55}, new double[] {-0.8, -0.55}));
				strokes.add(List.of(new double[] {-0.75, -0.3}, new double[] {0.75, -0.3}));
				// Jewels on the three tips.
				strokes.add(circle(0.1, 10, -0.85, 0.58));
				strokes.add(circle(0.1, 10, 0.0, 0.84));
				strokes.add(circle(0.1, 10, 0.85, 0.58));
			}
			case PENTAGRAM -> {
				strokes.add(circle(1.0, 64));
				// Inverted: one point down.
				List<double[]> star = new ArrayList<>();
				for (int i = 0; i <= 5; i++) {
					double a = -Math.PI / 2 + i * Math.PI * 4 / 5;
					star.add(new double[] {Math.cos(a) * 0.95, Math.sin(a) * 0.95});
				}
				strokes.add(star);
			}
			default -> {
			}
		}
		return strokes;
	}

	private static List<double[]> circle(double radius, int segments) {
		return circle(radius, segments, 0, 0);
	}

	private static List<double[]> circle(double radius, int segments, double cx, double cy) {
		List<double[]> line = new ArrayList<>();
		for (int i = 0; i <= segments; i++) {
			double a = Math.PI * 2 * i / segments;
			line.add(new double[] {cx + Math.cos(a) * radius, cy + Math.sin(a) * radius});
		}
		return line;
	}

	private static List<double[]> star(int tips, double outer, double inner, double phase) {
		List<double[]> line = new ArrayList<>();
		for (int i = 0; i <= tips * 2; i++) {
			double r = i % 2 == 0 ? outer : inner;
			double a = phase + Math.PI * i / tips;
			line.add(new double[] {Math.cos(a) * r, Math.sin(a) * r});
		}
		return line;
	}

	/** {@code count} points spread evenly along the strokes (by length), each stroke getting its share. */
	static List<double[]> sample(List<List<double[]>> strokes, int count) {
		List<double[]> out = new ArrayList<>();
		double total = 0;
		for (List<double[]> stroke : strokes) {
			total += length(stroke);
		}
		if (total <= 0 || count <= 0) {
			return out;
		}
		double step = total / count;
		double carry = step / 2;
		for (List<double[]> stroke : strokes) {
			for (int i = 1; i < stroke.size(); i++) {
				double[] a = stroke.get(i - 1);
				double[] b = stroke.get(i);
				double segment = Math.hypot(b[0] - a[0], b[1] - a[1]);
				double at = carry;
				while (at <= segment && out.size() < count) {
					double k = segment == 0 ? 0 : at / segment;
					out.add(new double[] {a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k});
					at += step;
				}
				carry = at - segment;
			}
		}
		while (out.size() < count) {
			List<double[]> last = strokes.getLast();
			out.add(last.getLast().clone());
		}
		return out;
	}

	private static double length(List<double[]> stroke) {
		double length = 0;
		for (int i = 1; i < stroke.size(); i++) {
			length += Math.hypot(stroke.get(i)[0] - stroke.get(i - 1)[0], stroke.get(i)[1] - stroke.get(i - 1)[1]);
		}
		return length;
	}
}
