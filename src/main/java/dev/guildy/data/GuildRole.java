package dev.guildy.data;

public enum GuildRole {
	MEMBER,
	OFFICER,
	LEADER;

	public boolean atLeast(GuildRole other) {
		return this.ordinal() >= other.ordinal();
	}

	/** Lang key of the display name. */
	public String langKey() {
		return "role." + name().toLowerCase();
	}

	public static GuildRole parse(String value, GuildRole fallback) {
		if (value == null) {
			return fallback;
		}
		try {
			return GuildRole.valueOf(value.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}
}
