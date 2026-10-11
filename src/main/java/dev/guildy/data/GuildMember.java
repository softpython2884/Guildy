package dev.guildy.data;

public final class GuildMember {
	public GuildRole role = GuildRole.MEMBER;
	/** Last known player name (for offline display). */
	public String name = "";
	public long joinedAt;

	public GuildMember() {
	}

	public GuildMember(GuildRole role, String name, long joinedAt) {
		this.role = role;
		this.name = name;
		this.joinedAt = joinedAt;
	}
}
