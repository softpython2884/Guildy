package dev.guildy.villager;

/**
 * Builder settings and statistics of a guild villager (persisted in {@link VillagerRecord#builder}, created the first
 * time it becomes a builder; kept when the task changes).
 */
public class BuilderData {
	/** Role chosen in the menu ({@link BuilderRoles.Role} id), "" = automatic (the guild's distribution). */
	public String role = "";
	/** Construction site chosen in the menu (project id), -1 = automatic (the highest-priority site with work). */
	public int project = -1;
	/** Blocks placed, cells dug out and items fetched from the storage. */
	public long placed;
	public long cleared;
	public long fetched;

	void sanitize() {
		if (role == null || BuilderRoles.Role.parse(role) == null) {
			role = "";
		}
		project = Math.max(-1, project);
		placed = Math.max(0, placed);
		cleared = Math.max(0, cleared);
		fetched = Math.max(0, fetched);
	}
}
