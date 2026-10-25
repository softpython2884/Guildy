package dev.guildy.builds;

import com.mojang.math.Transformation;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What one player sees of a schematic being placed (or of a site, "Afficher le contour"), sent to that player only:
 * the edges of the box in particles (green: the placement is valid, red: it is not, yellow: an existing site) pulsed
 * every half second, and ghost blocks: block display entities that exist only on that client (never in the world),
 * slightly shrunk and full bright. Large schematics show only their shell (cells touching air or the outside), sampled
 * to {@code preview.maxGhostBlocks}. Everything disappears when the preview ends (confirm, cancel, timeout, logout).
 */
public final class BuildPreview {
	private static final int INTERVAL = 10;
	private static final int MAX_PARTICLES = 600;
	private static final int PACKETS_PER_BUNDLE = 2000;
	private static final DustParticleOptions VALID = new DustParticleOptions(0x55FF55, 1.4F);
	private static final DustParticleOptions INVALID = new DustParticleOptions(0xFF5555, 1.4F);
	private static final DustParticleOptions SITE = new DustParticleOptions(0xFFD700, 1.4F);
	private static final DustParticleOptions ORIGIN = new DustParticleOptions(0x55FFFF, 1.8F);

	/** A placement in progress, or a site shown ({@code projectId} >= 0). */
	static final class Session {
		final UUID guildId;
		final String entryKey;
		final String display;
		final BuildPlan plan;
		PlacementTransform transform;
		final int projectId;
		long until;
		int[] ghostIds = new int[0];
		/** Local cells shown as ghosts (null: chosen at the first draw). */
		int @Nullable [] ghostCells;
		boolean redraw = true;
		@Nullable String error;
		Object[] errorArgs = new Object[0];

		Session(UUID guildId, String entryKey, String display, BuildPlan plan, PlacementTransform transform, int projectId) {
			this.guildId = guildId;
			this.entryKey = entryKey;
			this.display = display;
			this.plan = plan;
			this.transform = transform;
			this.projectId = projectId;
		}

		boolean site() {
			return projectId >= 0;
		}

		int[] box() {
			return transform.box(plan.data.sizeX, plan.data.sizeY, plan.data.sizeZ);
		}
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	private BuildPreview() {
	}

	static void init() {
		Scheduler.every("build.preview", INTERVAL, 3, BuildPreview::tick);
	}

	static @Nullable Session session(ServerPlayer player) {
		Session session = SESSIONS.get(player.getUUID());
		return session == null || session.site() ? null : session;
	}

	/** Starts (or replaces) the player's placement preview. */
	static Session start(ServerPlayer player, UUID guildId, String entryKey, String display, BuildPlan plan, PlacementTransform transform) {
		end(player);
		Session session = new Session(guildId, entryKey, display, plan, transform, -1);
		session.until = Scheduler.ticks() + BuildModule.config().preview.seconds * 20L;
		SESSIONS.put(player.getUUID(), session);
		return session;
	}

	/** Shows an existing site for a while (its outline and the blocks still to place). */
	static void showSite(ServerPlayer player, BuildSite site) {
		BuildPlan plan = site.plan();
		if (plan == null) {
			return;
		}
		end(player);
		Session session = new Session(site.guild.id, "", site.project.name, plan, site.project.transform(), site.project.id);
		session.until = Scheduler.ticks() + BuildModule.config().preview.outlineSeconds * 20L;
		List<Integer> cells = new ArrayList<>();
		int max = BuildModule.config().preview.maxGhostBlocks;
		int boxCells = plan.data.cells.length;
		for (int cell = 0; cell < boxCells && cells.size() < max; cell++) {
			byte state = site.state(cell);
			if ((state == BuildSite.PLACE || state == BuildSite.NEED || state == BuildSite.WAIT || state == BuildSite.BLOCKED) && site.target(cell) != null) {
				cells.add(cell);
			}
		}
		session.ghostCells = cells.stream().mapToInt(Integer::intValue).toArray();
		SESSIONS.put(player.getUUID(), session);
		Safe.run("build.preview", () -> draw(player, session));
	}

	/** The transform changed (adjust menu, new anchor): validated again and redrawn. */
	static void changed(ServerPlayer player, Session session) {
		session.until = Scheduler.ticks() + BuildModule.config().preview.seconds * 20L;
		session.redraw = true;
		Safe.run("build.preview", () -> draw(player, session));
	}

	/** Ends the player's preview and removes its ghosts. */
	static void end(ServerPlayer player) {
		Session session = SESSIONS.remove(player.getUUID());
		if (session != null) {
			removeGhosts(player, session);
		}
	}

	static void forget(UUID player) {
		SESSIONS.remove(player);
	}

	static void clear() {
		SESSIONS.clear();
	}

	private static void tick() {
		if (SESSIONS.isEmpty()) {
			return;
		}
		long now = Scheduler.ticks();
		Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
		List<Runnable> later = new ArrayList<>();
		while (it.hasNext()) {
			Map.Entry<UUID, Session> entry = it.next();
			ServerPlayer player = GuildyServer.player(entry.getKey());
			Session session = entry.getValue();
			if (player == null) {
				it.remove();
				continue;
			}
			if (now > session.until || !GuildyServer.isClaimLevel(player.level())) {
				it.remove();
				later.add(() -> {
					removeGhosts(player, session);
					if (!session.site()) {
						dev.guildy.core.Msg.actionBar(player, "build.preview.expired");
					}
				});
				continue;
			}
			later.add(() -> outline(player, session));
		}
		for (Runnable runnable : later) {
			Safe.run("build.preview", runnable);
		}
	}

	// ================================================================ drawing

	private static void draw(ServerPlayer player, Session session) {
		if (!session.site()) {
			BuildPlacement.validate(player, session);
		}
		if (session.redraw) {
			session.redraw = false;
			removeGhosts(player, session);
			sendGhosts(player, session);
		}
		outline(player, session);
	}

	/** The 12 edges of the box (and the origin corner of a placement). */
	private static void outline(ServerPlayer player, Session session) {
		int[] b = session.box();
		DustParticleOptions color = session.site() ? SITE : session.error == null ? VALID : INVALID;
		double minX = b[0];
		double minY = b[1];
		double minZ = b[2];
		double maxX = b[3] + 1;
		double maxY = b[4] + 1;
		double maxZ = b[5] + 1;
		double edges = 4 * ((maxX - minX) + (maxY - minY) + (maxZ - minZ));
		double step = 0.5;
		while (step < 16 && edges / step > MAX_PARTICLES) {
			step *= 2;
		}
		List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
		int range = BuildModule.config().preview.range;
		for (double x = minX; x <= maxX; x += step) {
			add(packets, player, color, x, minY, minZ, range);
			add(packets, player, color, x, maxY, minZ, range);
			add(packets, player, color, x, minY, maxZ, range);
			add(packets, player, color, x, maxY, maxZ, range);
		}
		for (double y = minY; y <= maxY; y += step) {
			add(packets, player, color, minX, y, minZ, range);
			add(packets, player, color, maxX, y, minZ, range);
			add(packets, player, color, minX, y, maxZ, range);
			add(packets, player, color, maxX, y, maxZ, range);
		}
		for (double z = minZ; z <= maxZ; z += step) {
			add(packets, player, color, minX, minY, z, range);
			add(packets, player, color, maxX, minY, z, range);
			add(packets, player, color, minX, maxY, z, range);
			add(packets, player, color, maxX, maxY, z, range);
		}
		if (!session.site()) {
			PlacementTransform t = session.transform;
			double ox = t.ox() + 0.5;
			double oz = t.oz() + 0.5;
			for (double dy = 0; dy <= 2; dy += 0.5) {
				add(packets, player, ORIGIN, ox, t.oy() + dy, oz, range);
			}
		}
		send(player, packets);
	}

	private static void add(List<Packet<? super ClientGamePacketListener>> packets, ServerPlayer player, DustParticleOptions color,
			double x, double y, double z, int range) {
		if (packets.size() >= MAX_PARTICLES) {
			return;
		}
		double dx = x - player.getX();
		double dy = y - player.getY();
		double dz = z - player.getZ();
		if (dx * dx + dy * dy + dz * dz > (double) range * range) {
			return;
		}
		packets.add(new ClientboundLevelParticlesPacket(color, true, true, x, y, z, 0.0F, 0.0F, 0.0F, 0.0F, 1));
	}

	private static void send(ServerPlayer player, List<Packet<? super ClientGamePacketListener>> packets) {
		for (int start = 0; start < packets.size(); start += PACKETS_PER_BUNDLE) {
			List<Packet<? super ClientGamePacketListener>> part = packets.subList(start, Math.min(packets.size(), start + PACKETS_PER_BUNDLE));
			player.connection.send(new ClientboundBundlePacket(new ArrayList<>(part)));
		}
	}

	// ================================================================ ghosts

	/** Cells shown as ghosts: every block when they fit, else the shell (touching air or the outside), sampled. */
	static int[] selectGhosts(BuildPlan plan, int max) {
		if (max <= 0) {
			return new int[0];
		}
		SchematicData data = plan.data;
		List<Integer> blocks = new ArrayList<>();
		for (int cell = 0; cell < data.cells.length; cell++) {
			if (ghostable(plan, data.cells[cell])) {
				blocks.add(cell);
			}
		}
		List<Integer> chosen = blocks;
		if (blocks.size() > max) {
			List<Integer> shell = new ArrayList<>();
			for (int cell : blocks) {
				if (exposed(plan, data, cell)) {
					shell.add(cell);
				}
			}
			chosen = shell;
		}
		if (chosen.size() > max) {
			List<Integer> sampled = new ArrayList<>(max);
			double step = chosen.size() / (double) max;
			for (int i = 0; i < max; i++) {
				sampled.add(chosen.get((int) Math.floor(i * step)));
			}
			chosen = sampled;
		}
		return chosen.stream().mapToInt(Integer::intValue).toArray();
	}

	private static boolean ghostable(BuildPlan plan, int palette) {
		MaterialRules.Kind kind = plan.kind(palette);
		return (kind == MaterialRules.Kind.BUILD || kind == MaterialRules.Kind.FLUID) && plan.state(palette) != null;
	}

	private static boolean exposed(BuildPlan plan, SchematicData data, int cell) {
		int x = data.x(cell);
		int y = data.y(cell);
		int z = data.z(cell);
		int[][] around = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		for (int[] d : around) {
			int nx = x + d[0];
			int ny = y + d[1];
			int nz = z + d[2];
			if (nx < 0 || ny < 0 || nz < 0 || nx >= data.sizeX || ny >= data.sizeY || nz >= data.sizeZ) {
				return true;
			}
			if (!ghostable(plan, data.cells[data.index(nx, ny, nz)])) {
				return true;
			}
		}
		return false;
	}

	private static void sendGhosts(ServerPlayer player, Session session) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (session.ghostCells == null) {
			session.ghostCells = selectGhosts(session.plan, BuildModule.config().preview.maxGhostBlocks);
		}
		SchematicData data = session.plan.data;
		PlacementTransform t = session.transform;
		net.minecraft.world.level.block.Rotation rotation = BuildSite.rotation(t.turn());
		net.minecraft.world.level.block.Mirror mirror = BuildSite.mirror(t.flip());
		List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
		int[] ids = new int[session.ghostCells.length];
		int count = 0;
		Transformation shrink = new Transformation(new Vector3f(0.05F, 0.05F, 0.05F), null, new Vector3f(0.9F, 0.9F, 0.9F), null);
		for (int cell : session.ghostCells) {
			BlockState state = session.plan.state(data.cells[cell]);
			if (state == null) {
				continue;
			}
			BlockState rotated = state.mirror(mirror).rotate(rotation);
			int x = data.x(cell);
			int z = data.z(cell);
			double wx = t.worldX(x, z);
			double wy = t.worldY(data.y(cell));
			double wz = t.worldZ(x, z);
			Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
			display.setPos(wx, wy, wz);
			display.setBlockState(rotated);
			display.setTransformation(shrink);
			display.setBrightnessOverride(Brightness.FULL_BRIGHT);
			display.setViewRange(1.5F);
			packets.add(new ClientboundAddEntityPacket(display.getId(), display.getUUID(), wx, wy, wz, 0.0F, 0.0F, EntityTypes.BLOCK_DISPLAY, 0,
					Vec3.ZERO, 0.0));
			List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> values = display.getEntityData().getNonDefaultValues();
			if (values != null) {
				packets.add(new ClientboundSetEntityDataPacket(display.getId(), values));
			}
			ids[count++] = display.getId();
		}
		session.ghostIds = java.util.Arrays.copyOf(ids, count);
		send(player, packets);
	}

	private static void removeGhosts(ServerPlayer player, Session session) {
		if (session.ghostIds.length > 0) {
			player.connection.send(new ClientboundRemoveEntitiesPacket(session.ghostIds));
			session.ghostIds = new int[0];
		}
	}

	/** Ghost world positions of a preview (tests). */
	static List<BlockPos> ghostPositions(Session session) {
		List<BlockPos> list = new ArrayList<>();
		if (session.ghostCells == null) {
			return list;
		}
		SchematicData data = session.plan.data;
		for (int cell : session.ghostCells) {
			int x = data.x(cell);
			int z = data.z(cell);
			list.add(new BlockPos(session.transform.worldX(x, z), session.transform.worldY(data.y(cell)), session.transform.worldZ(x, z)));
		}
		return list;
	}
}
