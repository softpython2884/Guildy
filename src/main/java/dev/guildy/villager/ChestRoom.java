package dev.guildy.villager;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * A chest room ("Salle des coffres", persisted in the guild): a box of the territory whose storage blocks were
 * registered as automatic chests ({@link StorageChest#auto}); the sorters choose their categories by lines (see
 * {@link ChestRooms}, {@link RoomPlanner}). A room may be reserved to some categories ({@link #categories}): its automatic
 * chests then only receive those.
 */
public class ChestRoom {
	public int id;
	public String name = "";
	/** Corners of the box (inclusive). */
	public BlockPos min;
	public BlockPos max;
	/** Categories the room is reserved to (names as in villagers.json5, the fallback allowed); empty = every category. */
	public List<String> categories = new ArrayList<>();

	public ChestRoom() {
	}

	public ChestRoom(int id, String name, BlockPos a, BlockPos b) {
		this.id = id;
		this.name = name;
		this.min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
		this.max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
	}

	/** Reserved to some categories (else it takes every category). */
	public boolean themed() {
		return categories != null && !categories.isEmpty();
	}

	public boolean contains(BlockPos pos) {
		return pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getY() >= min.getY() && pos.getY() <= max.getY()
				&& pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
	}

	void sanitize() {
		if (name == null) {
			name = "";
		}
		if (categories == null) {
			categories = new ArrayList<>();
		}
		categories.removeIf(category -> category == null || category.isBlank());
		if (min != null && max != null) {
			BlockPos a = min;
			BlockPos b = max;
			min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
			max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
		}
	}
}
