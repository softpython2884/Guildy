package dev.guildy.core;

import dev.guildy.data.Guild;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Cross-module entry points, assigned by each module during init. Lets modules open each other's menus
 * without compile-time coupling. Defaults are safe no-ops.
 */
public final class Hooks {
	private static final BiConsumer<ServerPlayer, Guild> UNAVAILABLE = (player, guild) -> Msg.send(player, "error.unavailable");

	/** Main guild menu (or the "no guild" menu). Set by the guild GUI module. */
	public static Consumer<ServerPlayer> openMainMenu = player -> Msg.send(player, "error.unavailable");
	/** Wave statistics / info page. */
	public static BiConsumer<ServerPlayer, Guild> openWaves = UNAVAILABLE;
	/** Structures list (plans, unlocks). */
	public static BiConsumer<ServerPlayer, Guild> openStructures = UNAVAILABLE;
	/** Quest list. */
	public static BiConsumer<ServerPlayer, Guild> openQuests = UNAVAILABLE;
	/** Shop (normally opened from the shop structure). */
	public static BiConsumer<ServerPlayer, Guild> openShop = UNAVAILABLE;
	/** Villager management. */
	public static BiConsumer<ServerPlayer, Guild> openVillagers = UNAVAILABLE;
	/** Upgrade tree ("Arbre d'evolution"). */
	public static BiConsumer<ServerPlayer, Guild> openUpgrades = UNAVAILABLE;
	/** Construction sites ("Chantiers"). Set by the build module. */
	public static BiConsumer<ServerPlayer, Guild> openBuilds = UNAVAILABLE;
	/** Collection of the unique weapons of the guild. Set by the unique items module. */
	public static BiConsumer<ServerPlayer, Guild> openUniques = UNAVAILABLE;
	/** Lore lines of the main menu "unique weapons" button (collection counter). */
	public static Function<Guild, List<Component>> uniqueCollectionLore = guild -> List.of();

	/** True while a night wave is running for this guild. Set by the wave module. */
	public static Predicate<Guild> isWaveActive = guild -> false;
	/** Short status lines shown in the main menu "waves" button lore. */
	public static Function<Guild, List<Component>> waveStatusLore = guild -> List.of();

	private Hooks() {
	}
}
