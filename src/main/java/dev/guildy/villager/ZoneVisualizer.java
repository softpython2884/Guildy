package dev.guildy.villager;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Particles sent to one player only: the border of a zone and its fanion ("Afficher"), the path of a patrol route,
 * and the route being drawn. Pulsed every {@link #INTERVAL} ticks, edges within {@link #RANGE} blocks of the viewer,
 * at most {@link #MAX_PARTICLES} particles per pulse.
 */
final class ZoneVisualizer {
	private static final int INTERVAL = 10;
	private static final int RANGE = 64;
	private static final int MAX_PARTICLES = 300;
	private static final DustParticleOptions ROUTE = new DustParticleOptions(0x55FFFF, 1.2F);
	private static final DustParticleOptions WAYPOINT = new DustParticleOptions(0xFFFF55, 1.8F);
	private static final DustParticleOptions FIRST_WAYPOINT = new DustParticleOptions(0x55FF55, 2.0F);
	private static final DustParticleOptions DRAFT = new DustParticleOptions(0xFF55FF, 1.2F);

	/** What a player looks at: a zone or a route (the other id is -1). */
	private record Session(UUID guildId, int zoneId, int routeId, long until) {
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	private ZoneVisualizer() {
	}

	static void register() {
		Scheduler.every("villager.zones.show", INTERVAL, 5, ZoneVisualizer::tick);
	}

	static void showZone(ServerPlayer player, Guild guild, WorkZone zone) {
		SESSIONS.put(player.getUUID(), new Session(guild.id, zone.id, -1, until()));
		Safe.run("villager.zones.show", () -> drawZone(player, zone));
	}

	static void showRoute(ServerPlayer player, Guild guild, PatrolRoute route) {
		SESSIONS.put(player.getUUID(), new Session(guild.id, -1, route.id, until()));
		Safe.run("villager.zones.show", () -> drawPath(player, route.points, route.loops(), ROUTE));
	}

	/** Redraws the player's route draft right away (after a click). */
	static void previewNow(ServerPlayer player) {
		List<BlockPos> points = RouteSelection.points(player.getUUID());
		if (points != null) {
			Safe.run("villager.zones.preview", () -> drawPath(player, points, false, DRAFT));
		}
	}

	private static long until() {
		return Scheduler.ticks() + WorkContext.ticks(VillagerModule.config().zones.showSeconds);
	}

	private static void tick() {
		if (!SESSIONS.isEmpty()) {
			long now = Scheduler.ticks();
			Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<UUID, Session> entry = it.next();
				Session session = entry.getValue();
				ServerPlayer player = GuildyServer.player(entry.getKey());
				Guild guild = GuildManager.get(session.guildId());
				WorkZone zone = guild == null ? null : guild.villagers.zone(session.zoneId());
				PatrolRoute route = guild == null ? null : guild.villagers.route(session.routeId());
				if (player == null || now > session.until() || (zone == null && route == null)) {
					it.remove();
					continue;
				}
				if (zone != null) {
					Safe.run("villager.zones.show", () -> drawZone(player, zone));
				} else {
					Safe.run("villager.zones.show", () -> drawPath(player, route.points, route.loops(), ROUTE));
				}
			}
		}
		for (Map.Entry<UUID, List<BlockPos>> entry : RouteSelection.drafts().entrySet()) {
			ServerPlayer player = GuildyServer.player(entry.getKey());
			if (player != null) {
				Safe.run("villager.zones.preview", () -> drawPath(player, entry.getValue(), false, DRAFT));
			}
		}
	}

	private static DustParticleOptions zoneColor(ZoneType type) {
		int color = switch (type) {
			case MINE -> 0xAAAAAA;
			case FOREST -> 0x55FF55;
			case FARM -> 0xFFFF55;
			case PATROL -> 0xFF5555;
		};
		return new DustParticleOptions(color, 1.5F);
	}

	/** The four edges of the zone at the viewer's height, plus a column of particles on the fanion. */
	private static void drawZone(ServerPlayer player, WorkZone zone) {
		if (!GuildyServer.isClaimLevel(player.level())) {
			return;
		}
		DustParticleOptions color = zoneColor(zone.type);
		int[] bounds = zone.bounds();
		int minX = bounds[0];
		int minZ = bounds[1];
		int maxX = bounds[2] + 1;
		int maxZ = bounds[3] + 1;
		double y = player.getY() + 1.0;
		int perimeter = 2 * ((maxX - minX) + (maxZ - minZ));
		int step = 1;
		while (step < 16 && perimeter / step > MAX_PARTICLES * 2) {
			step *= 2;
		}
		List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
		for (int x = minX; x <= maxX; x += step) {
			add(packets, player, color, x, y, minZ);
			add(packets, player, color, x, y, maxZ);
		}
		for (int z = minZ; z <= maxZ; z += step) {
			add(packets, player, color, minX, y, z);
			add(packets, player, color, maxX, y, z);
		}
		BlockPos pos = zone.pos;
		for (double dy = 0; dy <= 3.0; dy += 0.5) {
			add(packets, player, color, pos.getX() + 0.5, pos.getY() + dy, pos.getZ() + 0.5);
		}
		send(player, packets);
	}

	/** Segments between waypoints (closing one for a loop) and a marker on each waypoint. */
	private static void drawPath(ServerPlayer player, List<BlockPos> points, boolean loop, DustParticleOptions color) {
		if (points.isEmpty() || !GuildyServer.isClaimLevel(player.level())) {
			return;
		}
		List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
		for (int i = 0; i < points.size(); i++) {
			BlockPos point = points.get(i);
			DustParticleOptions marker = i == 0 ? FIRST_WAYPOINT : WAYPOINT;
			for (double dy = 0.3; dy <= 1.8; dy += 0.5) {
				add(packets, player, marker, point.getX() + 0.5, point.getY() + dy, point.getZ() + 0.5);
			}
		}
		for (int i = 0; i + 1 < points.size(); i++) {
			segment(packets, player, color, points.get(i), points.get(i + 1));
		}
		if (loop && points.size() > 2) {
			segment(packets, player, color, points.getLast(), points.getFirst());
		}
		send(player, packets);
	}

	private static void segment(List<Packet<? super ClientGamePacketListener>> packets, ServerPlayer player, DustParticleOptions color,
			BlockPos from, BlockPos to) {
		double ax = from.getX() + 0.5;
		double ay = from.getY() + 0.5;
		double az = from.getZ() + 0.5;
		double dx = to.getX() + 0.5 - ax;
		double dy = to.getY() + 0.5 - ay;
		double dz = to.getZ() + 0.5 - az;
		int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz)));
		for (int s = 1; s < steps; s++) {
			double t = (double) s / steps;
			add(packets, player, color, ax + dx * t, ay + dy * t, az + dz * t);
		}
	}

	private static void add(List<Packet<? super ClientGamePacketListener>> packets, ServerPlayer player, DustParticleOptions color,
			double x, double y, double z) {
		if (packets.size() >= MAX_PARTICLES) {
			return;
		}
		double dx = x - player.getX();
		double dz = z - player.getZ();
		if (dx * dx + dz * dz > (double) RANGE * RANGE) {
			return;
		}
		packets.add(new ClientboundLevelParticlesPacket(color, true, true, x, y, z, 0.0F, 0.0F, 0.0F, 0.0F, 1));
	}

	private static void send(ServerPlayer player, List<Packet<? super ClientGamePacketListener>> packets) {
		if (!packets.isEmpty()) {
			player.connection.send(new ClientboundBundlePacket(packets));
		}
	}

	static void forget(UUID player) {
		SESSIONS.remove(player);
	}

	static void clear() {
		SESSIONS.clear();
	}
}
