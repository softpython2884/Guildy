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

	void sanitize() {
		if (role == null) {
			role = Role.DEPOSIT;
		}
		if (category == null) {
			category = "";
		}
	}
}
