package dev.guildy.villager;

import net.minecraft.core.BlockPos;

/**
 * A container assigned to the guild storage system (persisted).
 */
public class StorageChest {
	public enum Role {
		/** Workers drop their loot here; the sorter empties it. */
		DEPOSIT,
		/** Receives the items of {@link #category} (the fallback category name = "misc" chest). */
		CATEGORY
	}

	public BlockPos pos;
	public Role role = Role.DEPOSIT;
	/** Category name when role = CATEGORY. */
	public String category = "";
	/**
	 * Custom name Guildy last gave to the container ("Dépôt", "Minerais et lingots"), "" if none: it is only replaced or
	 * removed while the container still bears it (see {@link StorageLabels}).
	 */
	public String label = "";
	/**
	 * Automatic chest of a chest room ({@link ChestRoom}): its category is chosen by the room planning
	 * ({@link ChestRooms}), "" = free (reserve, receives nothing). A role chosen by a player makes it manual.
	 */
	public boolean auto = false;
	/** Chest room holding this container (0 = none). */
	public int room = 0;

	public StorageChest() {
	}

	public StorageChest(BlockPos pos, Role role, String category) {
		this.pos = pos;
		this.role = role;
		this.category = category == null ? "" : category;
	}

	public boolean isDeposit() {
		return role == Role.DEPOSIT;
	}

	/** Automatic chest without category yet (reserve of a chest room): no group, receives nothing. */
	public boolean isFree() {
		return auto && role == Role.CATEGORY && (category == null || category.isBlank());
	}

	void sanitize() {
		if (role == null) {
			role = Role.DEPOSIT;
		}
		if (category == null) {
			category = "";
		}
		if (label == null) {
			label = "";
		}
		if (room < 0) {
			room = 0;
		}
		if (auto) {
			role = Role.CATEGORY;
		}
	}
}
