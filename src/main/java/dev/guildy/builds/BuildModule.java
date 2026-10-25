package dev.guildy.builds;

import dev.guildy.config.ConfigHandle;
import dev.guildy.config.ConfigManager;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Hooks;
import dev.guildy.data.Guild;
import dev.guildy.villager.Builders;
import dev.guildy.villager.Supply;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Construction sites ("chantiers"): schematics (.schem, .litematic, .nbt) picked from a list, placed with a preview and
 * built by the guild's builder villagers (villager task BUILDER), with smart terraforming and materials taken from the
 * guild's chests. builds.json5; lang prefix {@code build.}.
 */
public final class BuildModule {
	private static @Nullable ConfigHandle<BuildConfig> config;

	private BuildModule() {
	}

	public static void init() {
		config = ConfigManager.register("builds.json5", BuildConfig.class, BuildConfig::new);
		BuildLang.register();
		SchematicLibrary.ensureFolder();
		BuildSites.init();
		BuildPreview.init();
		BuildPlacement.init();
		BuildCommands.init();
		Builders.init();
		Hooks.openBuilds = BuildMenus::openList;
		// The builders' ingredients follow the sites' choice of sources: "all chests and barrels" on an active site.
		Predicate<Guild> server = Supply.allContainers;
		Supply.allContainers = guild -> server.test(guild) || guild.builds.projects.stream()
				.anyMatch(project -> project.active() && project.sourcesMode() == BuildProject.Sources.ALL);
	}

	public static BuildConfig config() {
		ConfigHandle<BuildConfig> handle = config;
		if (handle == null) {
			// Unit tests: no module, the defaults.
			BuildConfig defaults = new BuildConfig();
			defaults.validate();
			return defaults;
		}
		return handle.get();
	}

	/**
	 * Creates a site from a schematic of the list at a given origin (admin tools, tests): the file is loaded, the
	 * placement checked like a player's ({@code skipChecks}: no check), the copy made, the site started.
	 *
	 * @param done called on the server thread with the new project, or null and the reason (lang key) on failure
	 */
	public static void createSite(Guild guild, String entryKey, BlockPos origin, PlacementTransform.Turn turn, PlacementTransform.Flip flip,
			boolean skipChecks, Consumer<@Nullable BuildProject> done, Consumer<String> failed) {
		SchematicLibrary.Entry entry = SchematicLibrary.find(entryKey);
		MinecraftServer server = GuildyServer.server();
		if (entry == null || server == null) {
			failed.accept("build.error.schematic_gone");
			return;
		}
		Consumer<SchematicLibrary.Result> start = result -> {
			if (!result.ok()) {
				failed.accept(result.error());
				return;
			}
			PlacementTransform transform = new PlacementTransform(origin.getX(), origin.getY(), origin.getZ(), turn, flip);
			if (!skipChecks) {
				String[] error = {null};
				Object[][] args = {new Object[0]};
				net.minecraft.server.level.ServerLevel level = GuildyServer.claimLevel();
				if (level == null) {
					failed.accept("build.error.dimension");
					return;
				}
				BuildPlacement.check(level, guild, result.plan(), transform, -1, error, args);
				if (error[0] != null) {
					failed.accept(error[0]);
					return;
				}
			}
			BuildProject project = BuildPlacement.create(guild, entry, result.plan(), transform, "admin");
			BuildSite site = BuildSites.start(guild, project, result.plan());
			SchematicLibrary.copyForSite(server, entry, project.file, copied -> {
				if (copied == null) {
					site.fail("build.error.copy_failed");
				}
				done.accept(project);
			});
		};
		SchematicLibrary.Result cached = SchematicLibrary.get(entry, start);
		if (cached != null) {
			start.accept(cached);
		}
	}
}
