package dev.guildy.builds;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * A construction site of a guild (persisted in {@link GuildBuildData}). Only what describes the site is saved: the
 * world is the source of truth for its progress (a cell is done when the world block is the target), recomputed by
 * {@link BuildSite} after a restart; {@link #done} / {@link #total} are the last known numbers, shown until then.
 */
public class BuildProject {
	/** Where the builders take the materials. */
	public enum Sources {
		/** The guild's registered storage (deposit chests, the chests of the item's category, "Divers"). */
		STORAGE,
		/** Every chest, trapped chest and barrel of the territory (loaded chunks), registered or not. */
		ALL;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public Sources next() {
			return this == STORAGE ? ALL : STORAGE;
		}

		public static Sources parse(@Nullable String text) {
			return text != null && text.trim().equalsIgnoreCase("all") ? ALL : STORAGE;
		}
	}

	public int id;
	public String name = "";
	/** File of the copy in {@code <world>/guildy/builds/} (later edits of the source file change nothing). */
	public String file = "";
	/** Where it was picked ("config:maisons/ferme.schem", "world:minecraft:maison"), display only. */
	public String source = "";
	public String format = "";
	/** Schematic size (local axes, before the rotation). */
	public int sizeX;
	public int sizeY;
	public int sizeZ;
	/** World position of the schematic's local cell (0, 0, 0). */
	public @Nullable BlockPos origin;
	public String rotation = PlacementTransform.Turn.NONE.name();
	public String mirror = PlacementTransform.Flip.NONE.name();

	// options (project menu)
	public String sources = Sources.STORAGE.id();
	public String interior = InteriorRules.Mode.HOLLOW.id();
	public boolean replaceBuilt;
	public boolean foundations;
	/** Higher first (automatic builders work on the highest-priority site that has work). */
	public int priority;
	public boolean paused;

	// history
	public long createdAt;
	public String createdBy = "";
	public boolean finished;
	public long finishedAt;
	/** Last known progress (cells done / cells of work). */
	public long done;
	public long total;
	/** Blocks placed and cells dug out by the builders. */
	public long placed;
	public long cleared;

	public BuildProject() {
	}

	public PlacementTransform transform() {
		BlockPos o = origin == null ? BlockPos.ZERO : origin;
		return new PlacementTransform(o.getX(), o.getY(), o.getZ(), PlacementTransform.Turn.parse(rotation), PlacementTransform.Flip.parse(mirror));
	}

	public void setTransform(PlacementTransform transform) {
		origin = new BlockPos(transform.ox(), transform.oy(), transform.oz());
		rotation = transform.turn().name();
		mirror = transform.flip().name();
	}

	/** World box {minX, minY, minZ, maxX, maxY, maxZ}. */
	public int[] box() {
		return transform().box(sizeX, sizeY, sizeZ);
	}

	public Sources sourcesMode() {
		return Sources.parse(sources);
	}

	public InteriorRules.Mode interiorMode() {
		return InteriorRules.Mode.parse(interior, InteriorRules.Mode.HOLLOW);
	}

	/** Not finished, not paused. */
	public boolean active() {
		return !finished && !paused;
	}

	/** Percentage of the last known progress (0 to 100). */
	public int percent() {
		if (finished) {
			return 100;
		}
		if (total <= 0) {
			return 0;
		}
		return (int) Math.max(0, Math.min(99, Math.floor(done * 100.0 / total)));
	}

	/** Same box as another site (overlap test). */
	public boolean overlaps(int[] other) {
		int[] box = box();
		return box[0] <= other[3] && box[3] >= other[0] && box[1] <= other[4] && box[4] >= other[1] && box[2] <= other[5] && box[5] >= other[2];
	}

	/** Contains the world position. */
	public boolean contains(int x, int y, int z) {
		int[] box = box();
		return x >= box[0] && x <= box[3] && y >= box[1] && y <= box[4] && z >= box[2] && z <= box[5];
	}

	void sanitize() {
		if (name == null || name.isBlank()) {
			name = "Chantier " + id;
		}
		if (file == null) {
			file = "";
		}
		if (source == null) {
			source = "";
		}
		if (format == null) {
			format = "";
		}
		rotation = PlacementTransform.Turn.parse(rotation).name();
		mirror = PlacementTransform.Flip.parse(mirror).name();
		sources = Sources.parse(sources).id();
		interior = InteriorRules.Mode.parse(interior, InteriorRules.Mode.HOLLOW).id();
		if (createdBy == null) {
			createdBy = "";
		}
		sizeX = Math.max(1, sizeX);
		sizeY = Math.max(1, sizeY);
		sizeZ = Math.max(1, sizeZ);
		priority = Math.max(-9, Math.min(9, priority));
		done = Math.max(0, done);
		total = Math.max(0, total);
		placed = Math.max(0, placed);
		cleared = Math.max(0, cleared);
	}
}
