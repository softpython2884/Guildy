package dev.guildy.builds;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Where a schematic goes in the world (pure, unit tested): the world position of its local cell (0, 0, 0), then a
 * mirror and a rotation around that cell, with the formulas of vanilla structures ({@code StructureTemplate.transform},
 * pivot 0): the mirror first (LEFT_RIGHT: z → −z, FRONT_BACK: x → −x), then the rotation (CLOCKWISE_90: (x, z) →
 * (−z, x), COUNTERCLOCKWISE_90: (x, z) → (z, −x), CLOCKWISE_180: (x, z) → (−x, −z)). Block states follow with
 * {@code state.mirror(mirror).rotate(rotation)} (server side).
 *
 * @param ox origin (world position of the local cell 0, 0, 0)
 */
public record PlacementTransform(int ox, int oy, int oz, Turn turn, Flip flip) {
	/** Rotation (same names and order as {@code Rotation}). */
	public enum Turn {
		NONE,
		CLOCKWISE_90,
		CLOCKWISE_180,
		COUNTERCLOCKWISE_90;

		public Turn clockwise() {
			return values()[(ordinal() + 1) % 4];
		}

		public Turn counterClockwise() {
			return values()[(ordinal() + 3) % 4];
		}

		public Turn inverse() {
			return switch (this) {
				case CLOCKWISE_90 -> COUNTERCLOCKWISE_90;
				case COUNTERCLOCKWISE_90 -> CLOCKWISE_90;
				default -> this;
			};
		}

		/** Quarter turns clockwise (0 to 3). */
		public int quarters() {
			return ordinal();
		}

		public static Turn parse(@Nullable String text) {
			if (text != null) {
				for (Turn turn : values()) {
					if (turn.name().equalsIgnoreCase(text.trim())) {
						return turn;
					}
				}
			}
			return NONE;
		}

		/**
		 * The rotation that points the local +Z axis (the schematic's "depth") along the player's horizontal look, from
		 * the yaw (0 = south, 90 = west, 180 = north, 270 = east, like the entity yaw).
		 */
		public static Turn facing(float yaw) {
			int quadrant = Math.floorMod(Math.round(yaw / 90.0F), 4);
			return switch (quadrant) {
				case 1 -> CLOCKWISE_90; // west
				case 2 -> CLOCKWISE_180; // north
				case 3 -> COUNTERCLOCKWISE_90; // east
				default -> NONE; // south
			};
		}
	}

	/** Mirror (same names as {@code Mirror}). */
	public enum Flip {
		NONE,
		LEFT_RIGHT,
		FRONT_BACK;

		public Flip next() {
			return values()[(ordinal() + 1) % 3];
		}

		public static Flip parse(@Nullable String text) {
			if (text != null) {
				for (Flip flip : values()) {
					if (flip.name().equalsIgnoreCase(text.trim())) {
						return flip;
					}
				}
			}
			return NONE;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** World x of a local cell. */
	public int worldX(int x, int z) {
		int mx = flip == Flip.FRONT_BACK ? -x : x;
		int mz = flip == Flip.LEFT_RIGHT ? -z : z;
		return ox + switch (turn) {
			case CLOCKWISE_90 -> -mz;
			case COUNTERCLOCKWISE_90 -> mz;
			case CLOCKWISE_180 -> -mx;
			case NONE -> mx;
		};
	}

	/** World z of a local cell. */
	public int worldZ(int x, int z) {
		int mx = flip == Flip.FRONT_BACK ? -x : x;
		int mz = flip == Flip.LEFT_RIGHT ? -z : z;
		return oz + switch (turn) {
			case CLOCKWISE_90 -> mx;
			case COUNTERCLOCKWISE_90 -> -mx;
			case CLOCKWISE_180 -> -mz;
			case NONE -> mz;
		};
	}

	public int worldY(int y) {
		return oy + y;
	}

	/** Local {x, z} of a world column (inverse of {@link #worldX} / {@link #worldZ}). */
	public int[] local(int wx, int wz) {
		int dx = wx - ox;
		int dz = wz - oz;
		int mx;
		int mz;
		switch (turn) {
			case CLOCKWISE_90 -> {
				mx = dz;
				mz = -dx;
			}
			case COUNTERCLOCKWISE_90 -> {
				mx = -dz;
				mz = dx;
			}
			case CLOCKWISE_180 -> {
				mx = -dx;
				mz = -dz;
			}
			default -> {
				mx = dx;
				mz = dz;
			}
		}
		int x = flip == Flip.FRONT_BACK ? -mx : mx;
		int z = flip == Flip.LEFT_RIGHT ? -mz : mz;
		return new int[] {x, z};
	}

	/**
	 * World box of a schematic of that size: {minX, minY, minZ, maxX, maxY, maxZ} (inclusive).
	 */
	public int[] box(int sizeX, int sizeY, int sizeZ) {
		int ax = worldX(0, 0);
		int az = worldZ(0, 0);
		int bx = worldX(sizeX - 1, sizeZ - 1);
		int bz = worldZ(sizeX - 1, sizeZ - 1);
		return new int[] {Math.min(ax, bx), oy, Math.min(az, bz), Math.max(ax, bx), oy + sizeY - 1, Math.max(az, bz)};
	}

	public PlacementTransform withOrigin(int x, int y, int z) {
		return new PlacementTransform(x, y, z, turn, flip);
	}

	public PlacementTransform moved(int dx, int dy, int dz) {
		return new PlacementTransform(ox + dx, oy + dy, oz + dz, turn, flip);
	}

	/** Same box corner (minimum x, z), another rotation / mirror: what the adjust menu does. */
	public PlacementTransform reoriented(Turn newTurn, Flip newFlip, int sizeX, int sizeY, int sizeZ) {
		int[] before = box(sizeX, sizeY, sizeZ);
		PlacementTransform next = new PlacementTransform(0, oy, 0, newTurn, newFlip);
		int[] after = next.box(sizeX, sizeY, sizeZ);
		return new PlacementTransform(before[0] - after[0], oy, before[2] - after[2], newTurn, newFlip);
	}

	/**
	 * Default placement in front of a player: the middle of the schematic's front edge (local z = 0) on {@code anchor}
	 * (the block above the clicked one), the schematic extending in the look direction.
	 */
	public static PlacementTransform inFront(int ax, int ay, int az, float yaw, int sizeX) {
		Turn turn = Turn.facing(yaw);
		PlacementTransform zero = new PlacementTransform(0, ay, 0, turn, Flip.NONE);
		int mid = sizeX / 2;
		return new PlacementTransform(ax - zero.worldX(mid, 0), ay, az - zero.worldZ(mid, 0), turn, Flip.NONE);
	}
}
