package dev.guildy.core;

import dev.guildy.data.Guild;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.UUID;

/**
 * Player feedback helpers: prefixed chat, action bar, titles, sounds, clickable buttons, guild broadcasts.
 */
public final class Msg {
	private Msg() {
	}

	/** Prefix + lang text in chat. */
	public static void send(ServerPlayer player, String key, Object... args) {
		player.sendSystemMessage(prefixed(Lang.get(key, args)));
	}

	/** Prefix + arbitrary component in chat. */
	public static void sendRaw(ServerPlayer player, Component message) {
		player.sendSystemMessage(prefixed(message));
	}

	public static MutableComponent prefixed(Component message) {
		return Lang.get("prefix").append(message);
	}

	public static void actionBar(ServerPlayer player, Component message) {
		player.sendSystemMessage(message, true);
	}

	public static void actionBar(ServerPlayer player, String key, Object... args) {
		actionBar(player, Lang.get(key, args));
	}

	/** Title + subtitle (ticks). Either part may be null. */
	public static void title(ServerPlayer player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
		if (subtitle != null) {
			player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		}
		player.connection.send(new ClientboundSetTitleTextPacket(title != null ? title : Component.empty()));
	}

	public static void title(ServerPlayer player, Component title, Component subtitle) {
		title(player, title, subtitle, 10, 50, 15);
	}

	/** Plays a sound only for this player, at their position. */
	public static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		sound(player, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), volume, pitch);
	}

	public static void sound(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
		player.connection.send(new ClientboundSoundPacket(sound, SoundSource.MASTER,
				player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
	}

	/** Chat text with a clickable [button] running a command. */
	public static MutableComponent button(String labelKey, String command, String hoverKey) {
		MutableComponent label = Lang.get(labelKey);
		return label.withStyle(style -> style
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Lang.get(hoverKey))));
	}

	/** Clickable text suggesting a command in the chat box. */
	public static MutableComponent suggest(Component label, String command, Component hover) {
		return label.copy().withStyle(style -> style
				.withClickEvent(new ClickEvent.SuggestCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(hover)));
	}

	/** Sends a prefixed lang message to every online member of the guild. */
	public static void guild(Guild guild, String key, Object... args) {
		Component message = prefixed(Lang.get(key, args));
		for (UUID uuid : guild.memberIds()) {
			ServerPlayer player = GuildyServer.player(uuid);
			if (player != null) {
				player.sendSystemMessage(message);
			}
		}
	}

	public static void guildRaw(Guild guild, Component message) {
		Component full = prefixed(message);
		for (UUID uuid : guild.memberIds()) {
			ServerPlayer player = GuildyServer.player(uuid);
			if (player != null) {
				player.sendSystemMessage(full);
			}
		}
	}

	public static void guildTitle(Guild guild, Component title, Component subtitle) {
		for (UUID uuid : guild.memberIds()) {
			ServerPlayer player = GuildyServer.player(uuid);
			if (player != null) {
				title(player, title, subtitle);
			}
		}
	}

	public static void guildSound(Guild guild, Holder<SoundEvent> sound, float volume, float pitch) {
		for (UUID uuid : guild.memberIds()) {
			ServerPlayer player = GuildyServer.player(uuid);
			if (player != null) {
				sound(player, sound, volume, pitch);
			}
		}
	}

	public static void guildSound(Guild guild, SoundEvent sound, float volume, float pitch) {
		guildSound(guild, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), volume, pitch);
	}
}
