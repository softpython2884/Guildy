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
 * slightly shrunk and full bright. Beyond {@code preview.maxGhostBlocks}, only the shell is shown, then the schematic
 * seen through coarser cubes ({@link GhostLod}), so that a large building keeps its shape. Everything disappears when
 * the preview ends (confirm, cancel, timeout, logout).
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
		/** Ghosts shown (null: chosen at the first draw). */
		@Nullable List<GhostLod.Ghost> ghosts;
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

		/** Distance (blocks) up to which the outline and the ghosts show: the whole box can be seen from its far side. */
		int range() {
			double diagonal = Math.sqrt((double) plan.data.sizeX * plan.data.sizeX + (double) plan.data.sizeY * plan.data.sizeY
					+ (double) plan.data.sizeZ * plan.data.sizeZ);
			return (int) Math.min(512, Math.max(BuildModule.config().preview.range, diagonal + 32));
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
		SchematicData data = plan.data;
		session.ghosts = GhostLod.select(data.sizeX, data.sizeY, data.sizeZ, data.cells, cell -> {
			byte state = site.state(cell);
			return (state == BuildSite.PLACE || state == BuildSite.NEED || state == BuildSite.WAIT || state == BuildSite.BLOCKED)
					&& site.target(cell) != null && plan.ghostable(data.cells[cell]);
		}, palette -> {
			BlockState state = plan.state(palette);
			return state != null && state.isSolidRender();
		}, BuildModule.config().preview.maxGhostBlocks);
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
		int range = session.range();
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

	private static void sendGhosts(ServerPlayer player, Session session) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (session.ghosts == null) {
			session.ghosts = session.plan.ghosts(BuildModule.config().preview.maxGhostBlocks);
		}
		PlacementTransform t = session.transform;
		net.minecraft.world.level.block.Rotation rotation = BuildSite.rotation(t.turn());
		net.minecraft.world.level.block.Mirror mirror = BuildSite.mirror(t.flip());
		float viewRange = Math.max(1.5F, session.range() / 64.0F);
		List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
		int[] ids = new int[session.ghosts.size()];
		int count = 0;
		for (GhostLod.Ghost ghost : session.ghosts) {
			BlockState state = session.plan.state(ghost.palette());
			if (state == null) {
				continue;
			}
			BlockState rotated = state.mirror(mirror).rotate(rotation);
			// World box of the ghost: its two corners turned and mirrored like the schematic.
			int ax = t.worldX(ghost.x(), ghost.z());
			int az = t.worldZ(ghost.x(), ghost.z());
			int bx = t.worldX(ghost.x() + ghost.sizeX() - 1, ghost.z() + ghost.sizeZ() - 1);
			int bz = t.worldZ(ghost.x() + ghost.sizeX() - 1, ghost.z() + ghost.sizeZ() - 1);
			double wx = Math.min(ax, bx);
			double wy = t.worldY(ghost.y());
			double wz = Math.min(az, bz);
			Vector3f scale = new Vector3f(Math.abs(bx - ax) + 0.9F, ghost.sizeY() - 0.1F, Math.abs(bz - az) + 0.9F);
			Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
			display.setPos(wx, wy, wz);
			display.setBlockState(rotated);
			display.setTransformation(new Transformation(new Vector3f(0.05F, 0.05F, 0.05F), null, scale, null));
			display.setBrightnessOverride(Brightness.FULL_BRIGHT);
			display.setViewRange(viewRange);
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

	/** Ghost world corners of a preview (tests). */
	static List<BlockPos> ghostPositions(Session session) {
		List<BlockPos> list = new ArrayList<>();
		if (session.ghosts == null) {
			return list;
		}
		for (GhostLod.Ghost ghost : session.ghosts) {
			list.add(new BlockPos(session.transform.worldX(ghost.x(), ghost.z()), session.transform.worldY(ghost.y()),
					session.transform.worldZ(ghost.x(), ghost.z())));
		}
		return list;
	}
}
