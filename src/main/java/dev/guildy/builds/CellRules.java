package dev.guildy.builds;

/**
 * What a builder does with one cell of a construction site (pure, unit tested): the world is the source of truth, a
 * cell is done when the block there already is the target (placed by a builder, by a player helping by hand, or there
 * from the start). Everything else depends on what stands there now ({@link World}, classified server side by
 * {@code SiteWorld}):
 * <ul>
 * <li>air, a replaceable plant or snow layer, a fluid: the block is placed (a cell to clear is simply cleared);</li>
 * <li>a natural block (terrain, natural leaves...): dug out (drops to the bag), then the target placed in the same
 * step: the terrain around stays intact;</li>
 * <li>the same block with other properties (stairs turned the wrong way by a helper): set to the exact state, free;</li>
 * <li>a block built by a player: an obstacle listed in the menu, unless "replace the built blocks" is on (a cell to
 * clear then leaves it, without blocking the end of the site);</li>
 * <li>a container or a block entity, the heart and its support, a flag or fanion, a managed sign, anything in another
 * guild's territory: never touched.</li>
 * </ul>
 */
public final class CellRules {
	/** What stands in the cell now. */
	public enum World {
		AIR,
		/** Already the target state (doneness ignores volatile properties: age, power, distance...). */
		EXACT,
		/** The target's block with other properties. */
		SAME_BLOCK,
		/** Replaceable without drops: short grass, flowers, snow layer, dead bush... */
		REPLACEABLE,
		/** A natural block of the terrain (configured list), natural leaves, a fluid-free plant with collision. */
		NATURAL,
		/** Water or lava (source or flowing). */
		FLUID,
		/** Any other block: placed by a player. */
		BUILT,
		/** A block entity (chest, furnace, sign...): never broken. */
		CONTAINER,
		/** The heart, its support, a flag or fanion, a managed sign. */
		PROTECTED,
		/** In another guild's territory. */
		FOREIGN,
		/** Chunk not loaded. */
		UNLOADED
	}

	/** What to do with the cell. */
	public enum Action {
		DONE,
		/** Place the target (the cell holds air, a replaceable plant or a fluid). */
		PLACE,
		/** Dig the natural block (drops kept), then place the target. */
		REPLACE,
		/** Same block, other properties: set the exact state (free). */
		FIX,
		/** Break the player's block (replace option on; drops kept), then place the target. */
		BREAK_AND_PLACE,
		/** Dig the natural block out (cell to clear). */
		CLEAR,
		/** Break the player's block out (cell to clear, replace option on). */
		CLEAR_BUILT,
		/** Something that must not be touched stands where a block goes: the site cannot finish until it is removed. */
		BLOCKED,
		/** Something that must not be touched stands in a cell to clear: left there (not counted against the end). */
		LEFT,
		/** Not loaded: unknown. */
		UNKNOWN
	}

	private CellRules() {
	}

	/**
	 * Decision for a cell.
	 *
	 * @param clearCell    the target is air and the interior mode digs it out
	 * @param replaceBuilt option "replace the built blocks"
	 */
	public static Action decide(boolean clearCell, World world, boolean replaceBuilt) {
		if (world == World.UNLOADED) {
			return Action.UNKNOWN;
		}
		if (clearCell) {
			return switch (world) {
				case AIR, EXACT -> Action.DONE;
				case REPLACEABLE, NATURAL, FLUID -> Action.CLEAR;
				case BUILT, SAME_BLOCK -> replaceBuilt ? Action.CLEAR_BUILT : Action.LEFT;
				default -> Action.LEFT;
			};
		}
		return switch (world) {
			case EXACT -> Action.DONE;
			case AIR, REPLACEABLE, FLUID -> Action.PLACE;
			case NATURAL -> Action.REPLACE;
			case SAME_BLOCK -> Action.FIX;
			case BUILT -> replaceBuilt ? Action.BREAK_AND_PLACE : Action.BLOCKED;
			default -> Action.BLOCKED;
		};
	}

	/** The action changes the world (a builder has work there). */
	public static boolean isWork(Action action) {
		return switch (action) {
			case PLACE, REPLACE, FIX, BREAK_AND_PLACE, CLEAR, CLEAR_BUILT -> true;
			default -> false;
		};
	}

	/** The action uses the cell's materials (the free fix and the clearing do not). */
	public static boolean needsMaterials(Action action) {
		return action == Action.PLACE || action == Action.REPLACE || action == Action.BREAK_AND_PLACE;
	}
}
