package dev.guildy.villager;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Afficher les coffres": coloured particle columns above the registered containers of the guild, sent to one player
 * only for a few seconds (one colour per role, legend in the chat). Pulsed every {@link #INTERVAL} ticks, nearest
 * containers first, at most {@link #MAX_PARTICLES} particles per pulse.
 */
final class StorageVisualizer {
	private static final int INTERVAL = 10;
	private static final int RANGE = 48;
	private static final int MAX_PARTICLES = 400;
	/** Filter of the deposit containers (category filters are category names). */
	static final String DEPOSIT = "@deposit";
	/** Filter prefix of the containers of a chest room ("@room:3"). */
	static final String ROOM = "@room:";
	/** Filter prefix of the chests of one group ("@group:" + {@link StorageGroups#key}). */
	static final String GROUP = "@group:";
	/** Filter of the filter chests. */
	static final String FILTERS = "@filters";
	private static final char RESERVE_CODE = '8';
	private static final char FILTER_CODE = 'd';
	private static final char DEPOSIT_CODE = 'b';
	private static final char FALLBACK_CODE = '7';
	/** Legacy colour codes used for the categories (in config order, cycling), matching {@link #rgb(char)}. */
	private static final char[] PALETTE = {'a', 'e', '6', 'c', 'd', '9', '2', '5', '3', 'f', '4', '1', '8'};

	/** What a player looks at: every container of the guild ({@code filter} null) or one role. */
	private record Session(UUID guildId, @Nullable String filter, long until) {
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	/** Unregistered containers shown to a player (scan preview), with their colour codes. */
	private record Spots(List<BlockPos> positions, List<Character> codes, long until) {
	}

	private static final Map<UUID, Spots> SPOTS = new HashMap<>();

	private StorageVisualizer() {
	}

	static void register() {
		Scheduler.every("villager.storage.show", INTERVAL, 7, StorageVisualizer::tick);
	}

	/**
	 * Shows the containers of the guild ({@code filter}: null = all, {@link #DEPOSIT}, or a category name) and prints
	 * the colour legend.
	 *
	 * @return number of containers shown
	 */
	static int show(ServerPlayer player, Guild guild, @Nullable String filter) {
		int seconds = VillagerModule.config().storage.showSeconds;
		SESSIONS.put(player.getUUID(), new Session(guild.id, filter, Scheduler.ticks() + seconds * 20L));
		Map<String, Integer> counts = new LinkedHashMap<>();
		Map<String, Character> colours = new HashMap<>();
		for (StorageChest chest : guild.villagers.chests) {
			if (matches(chest, filter)) {
				// Filter chests share one legend entry (a room may hold a hundred different filters).
				String role = chest.isFilter() ? Lang.raw("villager.storage.role.filter") : Storage.roleName(chest);
				counts.merge(role, 1, Integer::sum);
				colours.put(role, code(chest));
			}
		}
		int total = counts.values().stream().mapToInt(Integer::intValue).sum();
		if (total == 0) {
			Msg.send(player, "villager.storage.show.none");
			return 0;
		}
		MutableComponent legend = Component.empty();
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			legend.append(Lang.get("villager.storage.show.entry", "&" + colours.get(entry.getKey()), entry.getKey(), entry.getValue()));
		}
		Msg.send(player, "villager.storage.show.done", total, seconds);
		player.sendSystemMessage(legend);
		Safe.run("villager.storage.show", () -> draw(player, guild, filter));
		return total;
	}

	/**
	 * Shows some containers that are not registered (yet): one colour per legend code ({@code codes}, same order as
	 * {@code positions}), for {@code storage.showSeconds}.
	 */
	static void showSpots(ServerPlayer player, List<BlockPos> positions, List<Character> codes) {
		int seconds = VillagerModule.config().storage.showSeconds;
		SPOTS.put(player.getUUID(), new Spots(List.copyOf(positions), List.copyOf(codes), Scheduler.ticks() + seconds * 20L));
		Safe.run("villager.storage.show", () -> drawSpots(player, SPOTS.get(player.getUUID())));
	}

	private static void tick() {
		long now = Scheduler.ticks();
		if (!SPOTS.isEmpty()) {
			Iterator<Map.Entry<UUID, Spots>> spots = SPOTS.entrySet().iterator();
			while (spots.hasNext()) {
				Map.Entry<UUID, Spots> entry = spots.next();
				ServerPlayer player = GuildyServer.player(entry.getKey());
				if (player == null || now > entry.getValue().until()) {
					spots.remove();
					continue;
				}
				Safe.run("villager.storage.show", () -> drawSpots(player, entry.getValue()));
			}
		}
		if (SESSIONS.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Session> entry = it.next();
			Session session = entry.getValue();
			ServerPlayer player = GuildyServer.player(entry.getKey());
			Guild guild = GuildManager.get(session.guildId());
			if (player == null || guild == null || now > session.until()) {
				it.remove();
				continue;
			}
			Safe.run("villager.storage.show", () -> draw(player, guild, session.filter()));
		}
	}

	private static boolean matches(StorageChest chest, @Nullable String filter) {
		if (filter == null) {
			return true;
		}
		if (filter.equals(DEPOSIT)) {
			return chest.isDeposit();
		}
		if (filter.startsWith(ROOM)) {
			return chest.room > 0 && filter.equals(ROOM + chest.room);
		}
		if (filter.startsWith(GROUP)) {
			// A part of a big group shows the whole group.
			return !chest.isDeposit() && !chest.isFree() && StorageGroups.baseKey(filter.substring(GROUP.length())).equals(StorageGroups.keyOf(chest));
		}
		if (filter.equals(FILTERS)) {
			return chest.isFilter();
		}
		return !chest.isDeposit() && !chest.isFree() && !chest.isFilter() && Storage.effectiveCategory(chest).equalsIgnoreCase(filter);
	}

	/** Particle columns (and the top corners) above the nearest matching containers. */
	private static void draw(ServerPlayer player, Guild guild, @Nullable String filter) {
		if (!GuildyServer.isClaimLevel(player.level()) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		List<StorageChest> chests = new ArrayList<>();
		for (StorageChest chest : guild.villagers.chests) {
			if (chest.pos != null && matches(chest, filter) && level.isLoaded(chest.pos) && horizontalDistSq(player, chest.pos) <= (double) RANGE * RANGE) {
				chests.add(chest);
			}
		}
		chests.sort(Comparator.comparingDouble(chest -> horizontalDistSq(player, chest.pos)));
		List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
		for (StorageChest chest : chests) {
			column(packets, level, chest.pos, code(chest));
			if (packets.size() >= MAX_PARTICLES) {
				break;
			}
		}
		if (!packets.isEmpty()) {
			player.connection.send(new ClientboundBundlePacket(packets));
		}
	}

	private static void drawSpots(ServerPlayer player, @Nullable Spots spots) {
		if (spots == null || !GuildyServer.isClaimLevel(player.level()) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < spots.positions().size(); i++) {
			BlockPos pos = spots.positions().get(i);
			if (level.isLoaded(pos) && horizontalDistSq(player, pos) <= (double) RANGE * RANGE) {
				order.add(i);
			}
		}
		order.sort(Comparator.comparingDouble(i -> horizontalDistSq(player, spots.positions().get(i))));
		List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
		for (int i : order) {
			column(packets, level, spots.positions().get(i), spots.codes().get(i));
			if (packets.size() >= MAX_PARTICLES) {
				break;
			}
		}
		if (!packets.isEmpty()) {
			player.connection.send(new ClientboundBundlePacket(packets));
		}
	}

	/** A particle column and the top corners above a container (both halves of a double chest). */
	private static void column(List<Packet<? super ClientGamePacketListener>> packets, ServerLevel level, BlockPos at, char code) {
		DustParticleOptions dust = new DustParticleOptions(rgb(code), 1.6F);
		List<BlockPos> blocks = new ArrayList<>(List.of(at));
		BlockPos other = Storage.otherHalf(level, at);
		if (other != null) {
			blocks.add(other);
		}
		for (BlockPos pos : blocks) {
			double x = pos.getX() + 0.5;
			double z = pos.getZ() + 0.5;
			for (double dy = 1.2; dy <= 2.8; dy += 0.4) {
				add(packets, dust, x, pos.getY() + dy, z);
			}
			for (int corner = 0; corner < 4; corner++) {
				add(packets, dust, pos.getX() + (corner % 2 == 0 ? 0.05 : 0.95), pos.getY() + 1.05, pos.getZ() + (corner < 2 ? 0.05 : 0.95));
			}
		}
	}

	private static void add(List<Packet<? super ClientGamePacketListener>> packets, DustParticleOptions dust, double x, double y, double z) {
		if (packets.size() < MAX_PARTICLES) {
			packets.add(new ClientboundLevelParticlesPacket(dust, true, true, x, y, z, 0.0F, 0.0F, 0.0F, 0.0F, 1));
		}
	}

	private static double horizontalDistSq(ServerPlayer player, BlockPos pos) {
		double dx = pos.getX() + 0.5 - player.getX();
		double dz = pos.getZ() + 0.5 - player.getZ();
		return dx * dx + dz * dz;
	}

	/** Legacy colour code of a container: aqua for deposits, gray for the fallback, else the category's palette colour. */
	static char code(StorageChest chest) {
		if (chest.isDeposit()) {
			return DEPOSIT_CODE;
		}
		if (chest.isFree()) {
			return RESERVE_CODE;
		}
		if (chest.role == StorageChest.Role.FILTER) {
			return FILTER_CODE;
		}
		int index = Storage.categories().indexOf(Storage.effectiveCategory(chest));
		return index < 0 ? FALLBACK_CODE : PALETTE[index % PALETTE.length];
	}

	/** RGB of a legacy colour code. */
	static int rgb(char code) {
		return switch (code) {
			case '0' -> 0x000000;
			case '1' -> 0x0000AA;
			case '2' -> 0x00AA00;
			case '3' -> 0x00AAAA;
			case '4' -> 0xAA0000;
			case '5' -> 0xAA00AA;
			case '6' -> 0xFFAA00;
			case '7' -> 0xAAAAAA;
			case '8' -> 0x555555;
			case '9' -> 0x5555FF;
			case 'a' -> 0x55FF55;
			case 'b' -> 0x55FFFF;
			case 'c' -> 0xFF5555;
			case 'd' -> 0xFF55FF;
			case 'e' -> 0xFFFF55;
			default -> 0xFFFFFF;
		};
	}

	static void forget(UUID player) {
		SESSIONS.remove(player);
		SPOTS.remove(player);
	}

	static void clear() {
		SESSIONS.clear();
		SPOTS.clear();
	}
}
