package dev.guildy.builds;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Construction sites of a guild (persisted inside the guild, {@code Guild.builds}).
 * <p>
 * Format {@link #CURRENT_FORMAT} (1): the first one. A later format migrates in {@link #sanitize} (each step: {@code if
 * (format < N) {...}}).
 */
public class GuildBuildData {
	public static final int CURRENT_FORMAT = 1;

	public int format = CURRENT_FORMAT;
	public List<BuildProject> projects = new ArrayList<>();
	/** Next project id (never reused: a stale reference never points to a newer site). */
	public int nextId = 1;

	public @Nullable BuildProject get(int id) {
		for (BuildProject project : projects) {
			if (project.id == id) {
				return project;
			}
		}
		return null;
	}

	/** Unfinished sites (paused ones included). */
	public int activeCount() {
		int count = 0;
		for (BuildProject project : projects) {
			if (!project.finished) {
				count++;
			}
		}
		return count;
	}

	/** Unfinished sites first, by priority (highest first), then by age; finished ones last, newest first. */
	public List<BuildProject> ordered() {
		List<BuildProject> list = new ArrayList<>(projects);
		list.sort(Comparator.comparing((BuildProject p) -> p.finished)
				.thenComparing((BuildProject p) -> p.finished ? 0 : -p.priority)
				.thenComparingLong(p -> p.finished ? -p.finishedAt : p.createdAt)
				.thenComparingInt(p -> p.id));
		return list;
	}

	/** Names in use (a new site gets "Maison 2" when "Maison" exists). */
	public Set<String> names() {
		Set<String> names = new HashSet<>();
		for (BuildProject project : projects) {
			names.add(project.name.toLowerCase(java.util.Locale.ROOT));
		}
		return names;
	}

	/** Keeps the {@code keep} newest finished sites. @return the removed ones */
	public List<BuildProject> trimFinished(int keep) {
		List<BuildProject> finished = new ArrayList<>();
		for (BuildProject project : projects) {
			if (project.finished) {
				finished.add(project);
			}
		}
		finished.sort(Comparator.comparingLong((BuildProject p) -> p.finishedAt).reversed());
		List<BuildProject> removed = new ArrayList<>();
		for (int i = keep; i < finished.size(); i++) {
			removed.add(finished.get(i));
		}
		projects.removeAll(removed);
		return removed;
	}

	public void sanitize() {
		if (projects == null) {
			projects = new ArrayList<>();
		}
		projects.removeIf(project -> project == null || project.origin == null || project.file == null || project.file.isBlank());
		int max = 0;
		for (BuildProject project : projects) {
			max = Math.max(max, project.id);
		}
		nextId = Math.max(Math.max(1, nextId), max + 1);
		Set<Integer> ids = new HashSet<>();
		for (BuildProject project : projects) {
			if (project.id <= 0 || !ids.add(project.id)) {
				project.id = nextId++;
				ids.add(project.id);
			}
			project.sanitize();
		}
		format = CURRENT_FORMAT;
	}
}
