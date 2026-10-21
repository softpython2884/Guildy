package dev.guildy.alchemy;

import com.mojang.serialization.Codec;
import dev.guildy.Guildy;
import dev.guildy.alchemy.mixin.LivingEntityUseAccessor;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.MagicBoost;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Magic boosts of the players (elixirs): per player, elixir id -> ticks left, saved with the player (Fabric data
 * attachment, not kept on death, like potion effects) and counted down while they are online. Implements the core
 * {@link MagicBoost} hook read by the scrolls and grimoires; shown as a violet boss bar (or the action bar).
 */
public final class MagicBoosts {
	private static final AttachmentType<Map<String, Integer>> BOOSTS = AttachmentRegistry.createPersistent(
			Identifier.fromNamespaceAndPath(Guildy.MOD_ID, "magic_boosts"), Codec.unboundedMap(Codec.STRING, Codec.INT));
	private static final int STEP = 20;
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

	private MagicBoosts() {
	}

	static void init() {
		MagicBoost.provider = new MagicBoost.Provider() {
			@Override
			public double power(ServerPlayer player) {
				return MagicBoostRules.powerMultiplier(active(player), caps());
			}

			@Override
			public double cooldown(ServerPlayer player) {
				return MagicBoostRules.cooldownMultiplier(active(player), caps());
			}

			@Override
			public int food(ServerPlayer player, int cost) {
				return MagicBoostRules.foodCost(cost, MagicBoostRules.foodMultiplier(active(player), caps()));
			}

			@Override
			public void shortenUse(ServerPlayer player) {
				shorten(player);
			}
		};
		Scheduler.every("alchemy.boosts", STEP, 7, MagicBoosts::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Safe.run("alchemy.boosts.leave", () -> hideBar(handler.getPlayer().getUUID())));
	}

	private static MagicBoostRules.Caps caps() {
		return AlchemyModule.config().magic.caps();
	}

	/** Elixir id -> ticks left (a copy; empty without boost). */
	public static Map<String, Integer> remaining(ServerPlayer player) {
		Map<String, Integer> map = player.getAttached(BOOSTS);
		return map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
	}

	/** Boosts of the elixirs the player is under (elixirs removed from the config no longer count). */
	public static List<MagicBoostRules.Boost> active(ServerPlayer player) {
		Map<String, Integer> map = player.getAttached(BOOSTS);
		if (map == null || map.isEmpty()) {
			return List.of();
		}
		List<MagicBoostRules.Boost> boosts = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : map.entrySet()) {
			AlchemyConfig.ElixirDef def = AlchemyModule.config().elixir(entry.getKey());
			if (def != null && def.enabled && entry.getValue() != null && entry.getValue() > 0) {
				boosts.add(def.boost());
			}
		}
		return boosts;
	}

	/** End of a drink of an elixir: its magic boost starts (or its time is refreshed). */
	static void drink(ServerPlayer player, AlchemyConfig.ElixirDef def) {
		if (!def.enabled || !def.magical()) {
			return;
		}
		Map<String, Integer> map = remaining(player);
		map.put(def.id, MagicBoostRules.refresh(map.getOrDefault(def.id, 0), def.magicSeconds * 20));
		player.setAttached(BOOSTS, map);
		Msg.send(player, "alchemy.boost.start", Lang.colorize(def.name), MagicBoostRules.clock(def.magicSeconds * 20));
		player.level().sendParticles(ParticleTypes.WITCH, player.getX(), player.getY(1.0), player.getZ(), 24, 0.4, 0.5, 0.4, 0.05);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
		show(player, map);
	}

	/** Removes every boost of the player (admin). */
	public static void clear(ServerPlayer player) {
		player.removeAttached(BOOSTS);
		hideBar(player.getUUID());
	}

	/** Gives a boost of an elixir directly (admin, tests). */
	public static boolean give(ServerPlayer player, String id, int ticks) {
		AlchemyConfig.ElixirDef def = AlchemyModule.config().elixir(id);
		if (def == null || !def.magical()) {
			return false;
		}
		Map<String, Integer> map = remaining(player);
		map.put(def.id, MagicBoostRules.refresh(map.getOrDefault(def.id, 0), ticks > 0 ? ticks : def.magicSeconds * 20));
		player.setAttached(BOOSTS, map);
		show(player, map);
		return true;
	}

	/** Shortens the incantation the player just started by the channel reduction of their elixirs. */
	private static void shorten(ServerPlayer player) {
		double multiplier = MagicBoostRules.channelMultiplier(active(player), caps());
		int left = player.getUseItemRemainingTicks();
		if (multiplier >= 1.0 || !player.isUsingItem() || left <= 0) {
			return;
		}
		int shorter = MagicBoostRules.channelTicks(left, multiplier);
		if (shorter < left) {
			((LivingEntityUseAccessor) player).guildy$setUseItemRemaining(shorter);
		}
	}

	// ------------------------------------------------------------------ count down and display

	private static void tick() {
		for (ServerPlayer player : GuildyServer.players()) {
			Safe.run("alchemy.boosts.tick", () -> tick(player));
		}
	}

	private static void tick(ServerPlayer player) {
		Map<String, Integer> map = player.getAttached(BOOSTS);
		if (map == null || map.isEmpty()) {
			hideBar(player.getUUID());
			return;
		}
		Map<String, Integer> next = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : map.entrySet()) {
			int left = entry.getValue() == null ? 0 : entry.getValue() - STEP;
			AlchemyConfig.ElixirDef def = AlchemyModule.config().elixir(entry.getKey());
			if (left > 0 && def != null) {
				next.put(entry.getKey(), left);
			} else if (def != null) {
				Msg.send(player, "alchemy.boost.end", Lang.colorize(def.name));
			}
		}
		if (next.isEmpty()) {
			player.removeAttached(BOOSTS);
			hideBar(player.getUUID());
			return;
		}
		player.setAttached(BOOSTS, next);
		show(player, next);
	}

	/** Boss bar (or action bar) of the boosts in progress: each elixir with its bonus and its time left. */
	private static void show(ServerPlayer player, Map<String, Integer> map) {
		String display = AlchemyModule.config().magic.display;
		if ("none".equals(display)) {
			hideBar(player.getUUID());
			return;
		}
		MutableComponent text = Component.empty();
		float progress = 1.0F;
		boolean first = true;
		for (Map.Entry<String, Integer> entry : map.entrySet()) {
			AlchemyConfig.ElixirDef def = AlchemyModule.config().elixir(entry.getKey());
			if (def == null) {
				continue;
			}
			if (!first) {
				text.append(Component.literal("   "));
			}
			first = false;
			text.append(Lang.get("alchemy.boost.bar", Lang.colorize(def.name), MagicBoostRules.clock(entry.getValue())));
			int total = Math.max(1, def.magicSeconds * 20);
			progress = Math.min(progress, Math.max(0, Math.min(1, entry.getValue() / (float) total)));
		}
		if (first) {
			hideBar(player.getUUID());
			return;
		}
		if ("actionbar".equals(display)) {
			hideBar(player.getUUID());
			Msg.actionBar(player, text);
			return;
		}
		ServerBossEvent bar = BARS.computeIfAbsent(player.getUUID(),
				id -> new ServerBossEvent(UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS));
		bar.setName(text);
		bar.setProgress(progress);
		if (!bar.getPlayers().contains(player)) {
			// A new player object (respawn, reconnection): the bar follows it.
			bar.removeAllPlayers();
			bar.addPlayer(player);
		}
	}

	private static void hideBar(UUID player) {
		ServerBossEvent bar = BARS.remove(player);
		if (bar != null) {
			bar.removeAllPlayers();
		}
	}

	/** Text of the boosts of a player for the admin command, null without boost. */
	static @Nullable String describe(ServerPlayer player) {
		Map<String, Integer> map = remaining(player);
		if (map.isEmpty()) {
			return null;
		}
		List<String> parts = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : map.entrySet()) {
			parts.add(entry.getKey() + " " + MagicBoostRules.clock(entry.getValue()));
		}
		return String.join(", ", parts) + " | power x" + String.format(java.util.Locale.ROOT, "%.2f", MagicBoost.powerMultiplier(player))
				+ ", cooldown x" + String.format(java.util.Locale.ROOT, "%.2f", MagicBoost.cooldownMultiplier(player));
	}
}
