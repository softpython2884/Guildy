package dev.guildy.builds;

/**
 * Order of the work on a site (pure, unit tested). Four passes run together, each on a front of layers:
 * <ul>
 * <li>{@link Pass#CLEAR} (terrassement): the cells to dig out, from the top layer down (a dug cell is never under
 * terrain still to dig, and a falling block above has been removed first);</li>
 * <li>{@link Pass#SOLID} (gros œuvre): the blocks that stand by themselves, bottom-up (builders stand on the lower
 * layers; the falling ones wait for their support);</li>
 * <li>{@link Pass#ATTACHED} (finitions): torches, doors, carpets, ladders, plants, signs... on the layers the solid front
 * has passed, each one only once {@code canSurvive} (else deferred);</li>
 * <li>{@link Pass#FLUID}: water and lava sources, last (no flooding of a site still dug or built).</li>
 * </ul>
 * A front only counts the actionable cells (materials available, not blocked, not deferred): a missing block or an
 * obstacle never holds back the rest of the site. {@code window} layers are open at once on the solid and clear fronts
 * (more builders side by side).
 */
public final class BuildPasses {
	public enum Pass {
		CLEAR,
		SOLID,
		ATTACHED,
		FLUID;

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/**
	 * The open layers: solid work on [{@code solidLow}, solidLow + window - 1], attached work below {@code solidLow},
	 * clear work on [clearTop - window + 1, clearTop], fluids once nothing else is actionable.
	 *
	 * @param solidLow lowest layer with actionable solid work ({@code sizeY} when none)
	 * @param clearTop highest layer with actionable clear work (-1 when none)
	 */
	public record Fronts(int solidLow, int clearTop, boolean anyAttached, int window) {
		public boolean allows(Pass pass, int y) {
			return switch (pass) {
				case SOLID -> y >= solidLow && y < solidLow + window;
				case CLEAR -> y <= clearTop && y > clearTop - window;
				case ATTACHED -> y < solidLow;
				case FLUID -> solidLow == Integer.MAX_VALUE && clearTop < 0 && !anyAttached;
			};
		}

		public boolean solidDone() {
			return solidLow == Integer.MAX_VALUE;
		}

		public boolean clearDone() {
			return clearTop < 0;
		}
	}

	private BuildPasses() {
	}

	/**
	 * Fronts from the actionable cells of each layer ({@code solid[y]}, {@code attached[y]}, {@code clear[y]}).
	 */
	public static Fronts fronts(int[] solid, int[] attached, int[] clear, int window) {
		int solidLow = Integer.MAX_VALUE;
		for (int y = 0; y < solid.length; y++) {
			if (solid[y] > 0) {
				solidLow = y;
				break;
			}
		}
		int clearTop = -1;
		for (int y = clear.length - 1; y >= 0; y--) {
			if (clear[y] > 0) {
				clearTop = y;
				break;
			}
		}
		boolean anyAttached = false;
		for (int count : attached) {
			if (count > 0) {
				anyAttached = true;
				break;
			}
		}
		return new Fronts(solidLow, clearTop, anyAttached, Math.max(1, window));
	}

	/** Pass of a cell: fluids, clear cells, then blocks by whether they need a support (attached) or not (solid). */
	public static Pass passOf(MaterialRules.Kind kind, boolean clearCell, boolean needsSupport) {
		if (clearCell) {
			return Pass.CLEAR;
		}
		if (kind == MaterialRules.Kind.FLUID) {
			return Pass.FLUID;
		}
		return needsSupport ? Pass.ATTACHED : Pass.SOLID;
	}
}
