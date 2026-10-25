package dev.guildy.builds;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Msg;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Changes of a site (menus and commands): options, pause, priority, renaming, cancelling. Cancelling removes nothing
 * from the world: what is built stays, the builders bring their bags back to the storage and go to the next site.
 */
public final class BuildActions {
	private BuildActions() {
	}

	static void cancel(Guild guild, BuildProject project, @Nullable ServerPlayer actor) {
		guild.builds.projects.remove(project);
		BuildSites.forget(guild, project);
		MinecraftServer server = GuildyServer.server();
		if (server != null) {
			SchematicLibrary.deleteCopy(server, project.file);
		}
		GuildManager.markDirty();
		Msg.guild(guild, "build.cancelled", actor == null ? "?" : actor.getName().getString(), project.name);
	}

	static void removeFinished(Guild guild, BuildProject project) {
		if (!project.finished) {
			return;
		}
		guild.builds.projects.remove(project);
		MinecraftServer server = GuildyServer.server();
		if (server != null) {
			SchematicLibrary.deleteCopy(server, project.file);
		}
		GuildManager.markDirty();
	}

	static void setSources(Guild guild, BuildProject project, BuildProject.Sources sources) {
		project.sources = sources.id();
		BuildSite site = BuildSites.peek(guild, project);
		if (site != null) {
			site.invalidateStock();
		}
	}

	static void setInterior(Guild guild, BuildProject project, InteriorRules.Mode mode) {
		project.interior = mode.id();
		BuildSites.replan(guild, project);
	}

	static void setReplaceBuilt(Guild guild, BuildProject project, boolean value) {
		// The scan classifies the cells again with the new option (no restart of the site).
		project.replaceBuilt = value;
	}

	static void setFoundations(Guild guild, BuildProject project, boolean value) {
		project.foundations = value;
		BuildSites.replan(guild, project);
	}

	static void setPriority(Guild guild, BuildProject project, int value) {
		project.priority = Math.max(-9, Math.min(9, value));
	}

	static void setPaused(Guild guild, BuildProject project, boolean value) {
		project.paused = value;
		Msg.guild(guild, value ? "build.paused" : "build.resumed", project.name);
	}

	static void rename(ServerPlayer player, Guild guild, BuildProject project, String text) {
		String name = text == null ? "" : text.trim();
		if (name.isEmpty() || name.length() > 32) {
			Msg.send(player, "build.error.name");
			return;
		}
		for (BuildProject other : guild.builds.projects) {
			if (other != project && other.name.equalsIgnoreCase(name)) {
				Msg.send(player, "build.error.name_taken", name);
				return;
			}
		}
		project.name = name;
		GuildManager.markDirty();
		Msg.send(player, "build.renamed", name);
	}
}
