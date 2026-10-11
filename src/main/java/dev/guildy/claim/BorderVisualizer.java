package dev.guildy.claim;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
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
 * /guild show: particles along the outer edges of the guild's claimed chunks, sent to the viewer only,
 * every {@link #INTERVAL} ticks for a few seconds. Only edges within {@link #RANGE} blocks, at the player's height,
 * capped at {@link #MAX_PARTICLES} particles per pulse.
 */
final class BorderVisualizer {
	private static final int INTERVAL = 10;
	private static final int RANGE = 48;
	private static final int CHUNK_RANGE = RANGE / 16 + 1;
	private static final int MAX_PARTICLES = 300;
	private static final DustParticleOptions OWN_BORDER = new DustParticleOptions(0xFFAA00, 1.5F);
	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	private record Session(UUID guildId, long untilTick) {
	}

	/** 16-block chunk edge starting at (x, z): along +x when {@code alongX}, else along +z. */
	private record Edge(int x, int z, boolean alongX) {
	}

	private BorderVisualizer() {
	}

	static void register() {
		Scheduler.every("territory.borders", INTERVAL, 3, BorderVisualizer::tick);
	}

	static void show(ServerPlayer player, Guild guild, int seconds) {
		SESSIONS.put(player.getUUID(), new Session(guild.id, Scheduler.ticks() + seconds * 20L));
		Safe.run("territory.borders.draw", () -> draw(player, guild));
	}

	private static void tick() {
		if (SESSIONS.isEmpty()) {
			return;
		}
		long now = Scheduler.ticks();
		Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Session> entry = it.next();
			ServerPlayer player = GuildyServer.player(entry.getKey());
			Guild guild = GuildManager.get(entry.getValue().guildId());
			if (player == null || guild == null || now > entry.getValue().untilTick()) {
				it.remove();
				continue;
			}
			Safe.run("territory.borders.draw", () -> draw(player, guild));
		}
	}

	private static void draw(ServerPlayer player, Guild guild) {
		if (!ClaimGuard.isClaimLevel(player.level()) || guild.claims().isEmpty()) {
			return;
		}
		int pcx = player.getBlockX() >> 4;
		int pcz = player.getBlockZ() >> 4;
		List<Edge> edges = new ArrayList<>();
		for (int cx = pcx - CHUNK_RANGE; cx <= pcx + CHUNK_RANGE; cx++) {
			for (int cz = pcz - CHUNK_RANGE; cz <= pcz + CHUNK_RANGE; cz++) {
				long key = ChunkKey.of(cx, cz);
				if (!guild.owns(key)) {
					continue;
				}
				int x0 = cx << 4;
				int z0 = cz << 4;
				if (!guild.owns(ChunkKey.of(cx, cz - 1))) {
					edges.add(new Edge(x0, z0, true));
				}
				if (!guild.owns(ChunkKey.of(cx, cz + 1))) {
					edges.add(new Edge(x0, z0 + 16, true));
				}
				if (!guild.owns(ChunkKey.of(cx - 1, cz))) {
					edges.add(new Edge(x0, z0, false));
				}
				if (!guild.owns(ChunkKey.of(cx + 1, cz))) {
					edges.add(new Edge(x0 + 16, z0, false));
				}
			}
		}
		if (edges.isEmpty()) {
			return;
		}
		double px = player.getX();
		double pz = player.getZ();
		double y = player.getY() + 1.0;
		double rangeSq = (double) RANGE * RANGE;
		// spacing between particles so that the visible edges fit in the budget
		int step = 1;
		while (step < 16 && edges.size() * (16 / step + 1) > MAX_PARTICLES * 2) {
			step *= 2;
		}
		List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
		for (Edge edge : edges) {
			for (int t = 0; t <= 16 && packets.size() < MAX_PARTICLES; t += step) {
				double x = edge.alongX() ? edge.x() + t : edge.x();
				double z = edge.alongX() ? edge.z() : edge.z() + t;
				double dx = x - px;
				double dz = z - pz;
				if (dx * dx + dz * dz > rangeSq) {
					continue;
				}
				packets.add(new ClientboundLevelParticlesPacket(OWN_BORDER, true, true, x, y, z, 0.0F, 0.0F, 0.0F, 0.0F, 1));
			}
			if (packets.size() >= MAX_PARTICLES) {
				break;
			}
		}
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
