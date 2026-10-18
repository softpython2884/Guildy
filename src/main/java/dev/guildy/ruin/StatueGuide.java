package dev.guildy.ruin;

import dev.guildy.core.GuildyItems;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.ruin.StatueRecord.Route;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Guidance of the jaguar statues: a player (not a spectator) who comes within {@code guideRadius} blocks of a placed statue
 * sees its eyes glow, a golden and jade stream of particles flowing from its head toward the next statue (or the temple)
 * for {@code guideSeconds}, hears a low grinding of stone, and gets the hint in the chat (the direction, or the light of
 * the temple at the last statue). Once per statue and player every {@code guideCooldownSeconds}.
 */
public final class StatueGuide {
	private static final int CHECK_TICKS = 10;
	/** Animations last at most as long as {@link RuinFx} lets them run. */
	private static final int MAX_TICKS = 190;
	private static final ParticleOptions GOLD = new DustParticleOptions(0xF2C14E, 1.3F);
	private static final ParticleOptions JADE = new DustParticleOptions(0x3FD18A, 1.3F);
	private static final ParticleOptions EYE = new DustParticleOptions(0x9CFFB8, 1.1F);
	/** Player → statue id → tick of the last guidance. */
	private static final Map<UUID, Map<Integer, Long>> LAST = new HashMap<>();
	/** Statue id → tick its stream ends. */
	private static final Map<Integer, Long> ACTIVE = new HashMap<>();

	private StatueGuide() {
	}

	static void init() {
		Scheduler.every("ruin.statue.guide", CHECK_TICKS, 7, () -> {
			MinecraftServer server = GuildyServer.server();
			if (server != null) {
				tick(server);
			}
		});
		Lifecycle.STOPPING.add(server -> clear());
	}

	static void clear() {
		LAST.clear();
		ACTIVE.clear();
	}

	// ================================================================== observable state (GameTests, admin)

	/** Tick of the last guidance of a statue for a player, -1 if never. */
	public static long lastGuided(UUID player, int statue) {
		Map<Integer, Long> mine = LAST.get(player);
		Long tick = mine == null ? null : mine.get(statue);
		return tick == null ? -1 : tick;
	}

	/** The stream of a statue is flowing now. */
	public static boolean isActive(int statue) {
		Long end = ACTIVE.get(statue);
		return end != null && Scheduler.ticks() <= end;
	}

	// ================================================================== proximity

	static void tick(MinecraftServer server) {
		if (Statues.placedCount() == 0) {
			return;
		}
		StatueSettings cfg = Statues.settings();
		long now = Scheduler.ticks();
		long cooldown = cfg.guideCooldownSeconds * 20L;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			ServerLevel level = player.level();
			StatueRecord statue = Statues.nearbyPlaced(level.dimension(), player.getX(), player.getY(), player.getZ(), cfg.guideRadius);
			if (statue == null || statue.routes.isEmpty()) {
				continue;
			}
			Map<Integer, Long> mine = LAST.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
			Long last = mine.get(statue.id);
			if (!StatueRules.guideReady(now, last == null ? -1 : last, cooldown)) {
				continue;
			}
			mine.put(statue.id, now);
			Safe.run("ruin.statue.guide", () -> guide(level, statue, player));
		}
		if (LAST.size() > 512) {
			LAST.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		}
		ACTIVE.values().removeIf(end -> end < now - 20 || end > now + 100_000);
	}

	/** Plays the guidance of a statue for a player (the stream once per statue while it flows; the hint every time). */
	public static void guide(ServerLevel level, StatueRecord statue, ServerPlayer player) {
		Route route = routeFor(statue, player);
		if (route == null) {
			return;
		}
		double[] target = Statues.target(level.getServer(), statue, route);
		boolean last = Statues.leadsToTemple(statue, route);
		double[] eye = statue.aim();
		double dx = target[0] - eye[0];
		double dz = target[2] - eye[2];
		long now = Scheduler.ticks();
		if (!isActive(statue.id)) {
			int ticks = (int) Math.min(MAX_TICKS, Math.round(Statues.settings().guideSeconds * 20));
			ACTIVE.put(statue.id, now + ticks);
			RuinFx.play(level, stream(statue, eye, dx, dz, ticks, Statues.settings().trailLength, last));
			level.playSound(null, eye[0], eye[1], eye[2], SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1.4F, 0.5F);
			level.playSound(null, eye[0], eye[1] - 4, eye[2], SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.2F, 0.5F);
			level.playSound(null, eye[0], eye[1], eye[2], SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.9F, 0.6F);
			if (last) {
				level.playSound(null, eye[0], eye[1], eye[2], SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 0.7F);
			}
		}
		String direction = Lang.raw("ruin.direction." + StatueRules.compass(dx, dz));
		if (last) {
			Msg.send(player, "ruin.statue.hint.last", direction);
		} else {
			Msg.send(player, "ruin.statue.hint", direction);
		}
		Msg.actionBar(player, last ? "ruin.statue.actionbar.last" : "ruin.statue.actionbar", direction);
		statue.timesGuided++;
		RuinIndex.markDirty();
	}

	/** The route a player follows: the one of a statue map in their inventory, else the first one of the statue. */
	static @Nullable Route routeFor(StatueRecord statue, ServerPlayer player) {
		if (statue.routes.isEmpty()) {
			return null;
		}
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!GuildyItems.is(stack, RuinItems.KIND_STATUE_MAP)) {
				continue;
			}
			StatueTrail trail = Statues.trail(GuildyItems.data(stack).getIntOr("trail", 0));
			Route route = trail == null ? null : statue.route(trail.temple);
			if (route != null) {
				return route;
			}
		}
		return statue.routes.getFirst();
	}

	/**
	 * The animation: the eyes glow (light dust, glow sparks) the whole time; a stream of gold and jade dust leaves the head,
	 * runs {@code length} blocks toward the target while sinking a little, then keeps flowing along that path.
	 */
	private static RuinFx.Animation stream(StatueRecord statue, double[] eye, double dx, double dz, int ticks, int length, boolean last) {
		double norm = Math.max(1e-6, Math.hypot(dx, dz));
		double ux = dx / norm;
		double uz = dz / norm;
		double speed = length / (ticks * 0.55);
		BlockPos[] eyes = statue.eyes.toArray(new BlockPos[0]);
		return (level, age) -> {
			if (age > ticks) {
				return false;
			}
			if (age % 3 == 0) {
				for (BlockPos e : eyes) {
					double ex = e.getX() + 0.5 + ux * 0.6;
					double ez = e.getZ() + 0.5 + uz * 0.6;
					level.sendParticles(EYE, true, true, ex, e.getY() + 0.5, ez, 3, 0.15, 0.15, 0.15, 0.0);
					level.sendParticles(ParticleTypes.GLOW, true, false, ex, e.getY() + 0.5, ez, 1, 0.2, 0.2, 0.2, 0.02);
				}
			}
			if (age == 0) {
				level.sendParticles(last ? ParticleTypes.END_ROD : ParticleTypes.HAPPY_VILLAGER, true, true, eye[0], eye[1], eye[2], 16, 1.2, 0.8, 1.2, 0.02);
			}
			double head = age * speed;
			double span = length + 12;
			double front = head <= length ? head : (head % span);
			for (int k = 0; k < 16; k++) {
				double t = front - k * 0.75;
				if (t < 0.5 || t > length) {
					continue;
				}
				double sink = 4.0 * Math.min(1.0, t / 12.0);
				double wave = Math.sin(t * 0.55 + age * 0.35) * 0.3;
				double x = eye[0] + ux * t - uz * wave;
				double z = eye[2] + uz * t + ux * wave;
				double y = eye[1] - sink + Math.cos(t * 0.4 + age * 0.25) * 0.2;
				level.sendParticles(k % 2 == 0 ? GOLD : JADE, true, false, x, y, z, 1, 0.05, 0.05, 0.05, 0.0);
			}
			if (age % 4 == 0 && head > 2) {
				double t = Math.min(length, front);
				level.sendParticles(ParticleTypes.GLOW, true, false, eye[0] + ux * t, eye[1] - 4.0 * Math.min(1.0, t / 12.0), eye[2] + uz * t, 2, 0.2,
						0.2, 0.2, 0.01);
			}
			return true;
		};
	}
}
