package dev.guildy.builds;

import dev.guildy.Guildy;
import dev.guildy.config.ConfigManager;
import dev.guildy.core.GuildyServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * The schematics players may pick (never a free path: a list of the files found in two places) and their loading:
 * <ul>
 * <li>{@code config/guildy/schematics/} (sub-folders allowed): .schem, .schematic, .litematic, .nbt;</li>
 * <li>the structures saved in game with a structure block ({@code <world>/generated/<namespace>/structure/**.nbt}): a
 * survival-friendly way to make a schematic.</li>
 * </ul>
 * Files are read and resolved on one worker thread (size limits checked first), results handed back to the server
 * thread and cached by path, size and modification time. The file of a validated site is copied into
 * {@code <world>/guildy/builds/}: editing or deleting the source later changes nothing.
 */
public final class SchematicLibrary {
	private static final int MAX_ENTRIES = 1024;
	private static final int MAX_DEPTH = 6;

	/** A file of the list: key ("config:dir/file.schem", "world:namespace:path"), display name and file. */
	public record Entry(String key, boolean world, String display, Path path, long size, long modified) {
		String cacheKey() {
			return path.toAbsolutePath().normalize() + "|" + size + "|" + modified;
		}
	}

	/** A load result: a plan, or an error (lang key + arguments). */
	public record Result(@Nullable BuildPlan plan, @Nullable String error, Object[] args) {
		static Result ok(BuildPlan plan) {
			return new Result(plan, null, new Object[0]);
		}

		static Result failed(String key, Object... args) {
			return new Result(null, key, args);
		}

		public boolean ok() {
			return plan != null;
		}
	}

	private static final Map<String, Result> CACHE = new LinkedHashMap<>(16, 0.75f, true);
	private static final Map<String, List<Consumer<Result>>> PENDING = new HashMap<>();
	private static @Nullable ExecutorService worker;

	private SchematicLibrary() {
	}

	// ================================================================ folders

	public static Path configDir() {
		return ConfigManager.dir().resolve("schematics");
	}

	static Path buildsDir(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("guildy").resolve("builds");
	}

	/** Creates the schematics folder and its README (never overwrites an existing one). */
	static void ensureFolder() {
		Path dir = configDir();
		try {
			Files.createDirectories(dir);
			Path readme = dir.resolve("README.txt");
			if (!Files.exists(readme)) {
				Files.writeString(readme, README, StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			Guildy.LOGGER.warn("Could not create {}: {}", dir, e.toString());
		}
	}

	// ================================================================ listing

	/** Every schematic file players may pick, config folder first, then the structures of the world (sorted by name). */
	public static List<Entry> list() {
		List<Entry> entries = new ArrayList<>();
		Path config = configDir();
		collect(config, config, false, "", entries);
		MinecraftServer server = GuildyServer.server();
		if (server != null && BuildModule.config().schematics.listWorldStructures) {
			Path generated = server.getWorldPath(LevelResource.GENERATED_DIR);
			try (Stream<Path> namespaces = Files.isDirectory(generated) ? Files.list(generated) : Stream.empty()) {
				for (Path ns : namespaces.sorted().toList()) {
					String namespace = ns.getFileName().toString();
					for (String folder : List.of("structure", "structures")) {
						Path root = ns.resolve(folder);
						if (Files.isDirectory(root)) {
							collect(root, root, true, namespace + ":", entries);
						}
					}
				}
			} catch (IOException e) {
				Guildy.LOGGER.warn("Could not list {}: {}", generated, e.toString());
			}
		}
		Set<String> seen = new HashSet<>();
		entries.removeIf(entry -> !seen.add(entry.key()));
		entries.sort(Comparator.comparing(Entry::world).thenComparing(entry -> entry.display().toLowerCase(Locale.ROOT)));
		return entries.size() > MAX_ENTRIES ? List.copyOf(entries.subList(0, MAX_ENTRIES)) : entries;
	}

	private static void collect(Path root, Path dir, boolean world, String prefix, List<Entry> out) {
		if (!Files.isDirectory(dir) || out.size() >= MAX_ENTRIES) {
			return;
		}
		Path realRoot;
		try {
			realRoot = root.toRealPath();
		} catch (IOException e) {
			return;
		}
		try (Stream<Path> files = Files.walk(dir, MAX_DEPTH)) {
			for (Path file : files.sorted().toList()) {
				if (out.size() >= MAX_ENTRIES) {
					break;
				}
				String name = file.getFileName().toString();
				String format = SchematicReader.formatOf(name);
				if (format == null || (world && !format.equals("nbt")) || !Files.isRegularFile(file)) {
					continue;
				}
				Path real;
				try {
					real = file.toRealPath();
				} catch (IOException e) {
					continue;
				}
				if (!real.startsWith(realRoot)) {
					// A link leading out of the folder: never read.
					continue;
				}
				String relative = root.relativize(file).toString().replace('\\', '/');
				String key;
				String display;
				if (world) {
					String id = relative.substring(0, relative.length() - ".nbt".length());
					key = "world:" + prefix + id;
					display = prefix + id;
				} else {
					key = "config:" + relative;
					display = relative;
				}
				out.add(new Entry(key, world, display, real, Files.size(real), Files.getLastModifiedTime(real).toMillis()));
			}
		} catch (IOException | RuntimeException e) {
			Guildy.LOGGER.warn("Could not list schematics in {}: {}", dir, e.toString());
		}
	}

	/** The entry of a key (listed again: a removed file is no longer found). */
	public static @Nullable Entry find(String key) {
		for (Entry entry : list()) {
			if (entry.key().equals(key)) {
				return entry;
			}
		}
		return null;
	}

	// ================================================================ loading

	/** Cached result of an entry, or null while it is not loaded (the load starts, {@code done} runs on the server thread). */
	public static @Nullable Result get(Entry entry, @Nullable Consumer<Result> done) {
		String key = entry.cacheKey();
		Result cached = CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		load(key, entry.path(), entry.path().getFileName().toString(), done);
		return null;
	}

	/** Loads a site's copy ({@code <world>/guildy/builds/<file>}); {@code done} runs on the server thread. */
	static void loadCopy(MinecraftServer server, String file, Consumer<Result> done) {
		Path path = buildsDir(server).resolve(file).normalize();
		if (!path.startsWith(buildsDir(server).normalize()) || !Files.isRegularFile(path)) {
			done.accept(Result.failed("build.error.missing_copy", file));
			return;
		}
		try {
			String key = path.toAbsolutePath() + "|" + Files.size(path) + "|" + Files.getLastModifiedTime(path).toMillis();
			Result cached = CACHE.get(key);
			if (cached != null) {
				done.accept(cached);
				return;
			}
			load(key, path, file, done);
		} catch (IOException e) {
			done.accept(Result.failed("build.error.missing_copy", file));
		}
	}

	private static void load(String key, Path path, String name, @Nullable Consumer<Result> done) {
		List<Consumer<Result>> waiting = PENDING.get(key);
		if (waiting != null) {
			if (done != null) {
				waiting.add(done);
			}
			return;
		}
		waiting = new ArrayList<>();
		if (done != null) {
			waiting.add(done);
		}
		PENDING.put(key, waiting);
		BuildConfig config = BuildModule.config();
		SchematicReader.Limits limits = config.limits();
		long maxFile = (long) config.schematics.maxFileMegabytes * 1024 * 1024;
		boolean upgrade = config.schematics.upgradeOldFiles;
		boolean waterFree = config.materials.waterFree;
		MinecraftServer server = GuildyServer.server();
		executor().execute(() -> {
			Result result;
			try {
				long size = Files.size(path);
				if (size > maxFile) {
					throw new SchematicException("build.error.file_too_big", size / (1024 * 1024) + 1, maxFile / (1024 * 1024));
				}
				byte[] bytes = Files.readAllBytes(path);
				SchematicData data = SchematicReader.read(name, bytes, limits);
				result = Result.ok(BuildPlan.of(data, upgrade, waterFree));
			} catch (SchematicException e) {
				result = Result.failed(e.key(), e.args());
			} catch (IOException e) {
				result = Result.failed("build.error.read", e.getClass().getSimpleName());
			} catch (RuntimeException | OutOfMemoryError | StackOverflowError e) {
				Guildy.LOGGER.warn("Could not load schematic {}", path, e);
				result = Result.failed("build.error.corrupt", e.getClass().getSimpleName());
			}
			Result finished = result;
			if (server == null) {
				return;
			}
			server.execute(() -> deliver(key, finished));
		});
	}

	private static void deliver(String key, Result result) {
		CACHE.put(key, result);
		int max = BuildModule.config().schematics.cacheSize;
		while (CACHE.size() > max) {
			String eldest = CACHE.keySet().iterator().next();
			CACHE.remove(eldest);
		}
		List<Consumer<Result>> waiting = PENDING.remove(key);
		if (waiting != null) {
			for (Consumer<Result> consumer : waiting) {
				dev.guildy.core.Safe.run("build.schematic.loaded", () -> consumer.accept(result));
			}
		}
	}

	/** Name of the copy of a site's file: unique over the guilds ("1a2b3c4d-3-maison.schem"). */
	static String copyName(java.util.UUID guild, int projectId, Entry entry) {
		String slug = entry.path().getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
		return guild.toString().substring(0, 8) + "-" + projectId + "-" + slug;
	}

	/** Copies a picked file for a new site as {@code file}; {@code done} runs on the server thread with it (null on failure). */
	static void copyForSite(MinecraftServer server, Entry entry, String file, Consumer<@Nullable String> done) {
		Path dir = buildsDir(server);
		executor().execute(() -> {
			String copied = null;
			try {
				Files.createDirectories(dir);
				Path target = dir.resolve(file);
				Path tmp = dir.resolve(file + ".tmp");
				Files.copy(entry.path(), tmp, StandardCopyOption.REPLACE_EXISTING);
				try {
					Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				} catch (java.nio.file.AtomicMoveNotSupportedException e) {
					Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
				}
				copied = file;
			} catch (IOException | RuntimeException e) {
				Guildy.LOGGER.warn("Could not copy schematic {} to {}", entry.path(), dir, e);
			}
			String result = copied;
			server.execute(() -> done.accept(result));
		});
	}

	/** Deletes a removed site's copy (worker thread). */
	static void deleteCopy(MinecraftServer server, String file) {
		if (file == null || file.isBlank()) {
			return;
		}
		Path dir = buildsDir(server).normalize();
		Path path = dir.resolve(file).normalize();
		if (!path.startsWith(dir)) {
			return;
		}
		executor().execute(() -> {
			try {
				Files.deleteIfExists(path);
			} catch (IOException e) {
				Guildy.LOGGER.warn("Could not delete {}", path, e);
			}
		});
	}

	static synchronized ExecutorService executor() {
		if (worker == null || worker.isShutdown()) {
			worker = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
				Thread thread = new Thread(runnable, "Guildy schematics");
				thread.setDaemon(true);
				thread.setPriority(Thread.NORM_PRIORITY - 1);
				return thread;
			});
			((ThreadPoolExecutor) worker).allowCoreThreadTimeOut(true);
		}
		return worker;
	}

	/** Server stop: cached plans and pending callbacks are dropped. */
	static void clear() {
		CACHE.clear();
		PENDING.clear();
	}

	private static final String README = """
			Guildy - schematiques des chantiers
			===================================

			Deposez ici les fichiers que les villageois batisseurs pourront construire (sous-dossiers permis) :
			  - .schem / .schematic : WorldEdit, FAWE (format Sponge versions 1, 2 et 3) ;
			  - .litematic          : Litematica (toutes les regions sont fusionnees en une seule boite) ;
			  - .nbt                : structure vanilla (bloc de structure).

			Les structures sauvegardees en jeu avec un bloc de structure (dossier <monde>/generated/<namespace>/structure/)
			sont aussi proposees, dans un groupe a part : un moyen de creer des schematiques en survie.

			Dans le jeu : menu des villageois -> Chantiers -> Nouveau chantier (ou /guild chantier). Choisissez une
			schematique, recevez le "Plan de chantier", faites clic droit sur un bloc pour poser l'apercu, ajustez
			(rotation, miroir, deplacement) puis validez.

			Les fichiers trop anciens sont mis a jour avec le DataFixer de Minecraft (d'apres leur DataVersion) ; les blocs
			inconnus sont ignores et signales. Limites (taille, volume, nombre de blocs) : config/guildy/builds.json5.
			Un fichier valide pour un chantier est copie dans <monde>/guildy/builds/ : le modifier ou le supprimer ici ne
			change plus le chantier.
			""";
}
