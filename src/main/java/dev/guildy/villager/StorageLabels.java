package dev.guildy.villager;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.TagValueInput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Vanilla custom names of the registered containers ("Dépôt", "Minerais et lingots"), shown as the title when a player
 * opens them. Choosing a role names the container (both halves of a double chest); unregistering it removes the name
 * while it is still Guildy's. A periodic pass renames the loaded containers whose label changed (category renamed or
 * removed, lang edited, chest replaced) without ever overwriting a name given by a player. Breaking a registered
 * container clears its name first (the dropped item stays a plain chest) and moves the registration to the other half of
 * a double chest (else unregisters it).
 */
final class StorageLabels {
	private static final int SYNC_TICKS = 100;

	private StorageLabels() {
	}

	static void init() {
		Scheduler.every("villager.storage.labels", SYNC_TICKS, 37, StorageLabels::sync);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			Safe.run("villager.storage.break", () -> beforeBreak(level, pos));
			return true;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) ->
				Safe.run("villager.storage.broken", () -> afterBreak(level, player instanceof ServerPlayer p ? p : null, pos, state)));
	}

	/** Name a chest should bear: "Dépôt" or its (effective) category; "" when naming is disabled. */
	static String desired(StorageChest chest) {
		if (!VillagerModule.config().storage.labelChests) {
			return "";
		}
		if (chest.isFree()) {
			return Lang.raw("villager.storage.label.reserve");
		}
		return chest.isDeposit() ? Lang.raw("villager.storage.label.deposit") : Lang.raw("villager.storage.label.category", Storage.roleName(chest));
	}

	/** A player chose the role: names the container (any previous name is replaced). */
	static void apply(ServerLevel level, StorageChest chest) {
		String label = desired(chest);
		for (BlockPos pos : halves(level, chest.pos)) {
			String current = name(level, pos);
			if (!label.isEmpty()) {
				setName(level, pos, label);
			} else if (current != null && current.equals(chest.label)) {
				setName(level, pos, null);
			}
		}
		chest.label = label;
		GuildManager.markDirty();
	}

	/** The container leaves the storage: its name is removed if it is still Guildy's. */
	static void clear(ServerLevel level, StorageChest chest) {
		if (chest.label == null || chest.label.isEmpty() || chest.pos == null || !level.isLoaded(chest.pos)) {
			return;
		}
		for (BlockPos pos : halves(level, chest.pos)) {
			if (chest.label.equals(name(level, pos))) {
				setName(level, pos, null);
			}
		}
		chest.label = "";
	}

	/** Periodic pass over the loaded containers of every guild. */
	static void sync() {
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			return;
		}
		for (Guild guild : GuildManager.all()) {
			for (StorageChest chest : guild.villagers.chests) {
				sync(level, guild, chest);
			}
		}
	}

	/**
	 * Gives the current label to a loaded container still named by Guildy (or not named at all); a container left
	 * outside the territory loses it.
	 */
	static void sync(ServerLevel level, Guild guild, StorageChest chest) {
		if (chest.pos == null || !level.isLoaded(chest.pos) || !Storage.isStorageBlock(level.getBlockEntity(chest.pos))) {
			return;
		}
		if (chest.label == null) {
			chest.label = "";
		}
		String label = GuildManager.at(level, chest.pos) == guild ? desired(chest) : "";
		for (BlockPos pos : halves(level, chest.pos)) {
			String current = name(level, pos);
			if (Objects.equals(current, label.isEmpty() ? null : label)) {
				continue;
			}
			// A name given by a player is never replaced.
			if (current == null || current.equals(chest.label)) {
				setName(level, pos, label.isEmpty() ? null : label);
			}
		}
		if (!chest.label.equals(label)) {
			chest.label = label;
			GuildManager.markDirty();
		}
	}

	/** The guild is disbanded: its loaded containers lose their Guildy names. */
	static void onDisband(Guild guild) {
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			return;
		}
		for (StorageChest chest : guild.villagers.chests) {
			clear(level, chest);
		}
	}

	// ------------------------------------------------------------------ breaking

	private static void beforeBreak(Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel serverLevel) || !Storage.isStorageBlock(level.getBlockEntity(pos))) {
			return;
		}
		Guild guild = GuildManager.at(serverLevel, pos);
		StorageChest chest = guild == null ? null : Storage.registered(serverLevel, guild, pos);
		if (chest != null && chest.label != null && !chest.label.isEmpty() && chest.label.equals(name(serverLevel, pos))) {
			// Cancelled by another mod: the next sync gives the name back.
			setName(serverLevel, pos, null);
		}
	}

	private static void afterBreak(Level level, @Nullable ServerPlayer player, BlockPos pos, BlockState state) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		Guild guild = GuildManager.at(serverLevel, pos);
		StorageChest chest = guild == null ? null : guild.villagers.chestAt(pos);
		if (chest == null) {
			return;
		}
		BlockPos other = null;
		if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			other = ChestBlock.getConnectedBlockPos(pos, state);
		}
		if (other != null && Storage.isStorageBlock(serverLevel.getBlockEntity(other)) && guild.villagers.chestAt(other) == null) {
			// The other half of the double chest stays registered (and keeps its name).
			chest.pos = other.immutable();
		} else {
			guild.villagers.chests.remove(chest);
			if (player != null) {
				Msg.actionBar(player, "villager.storage.broken", Storage.roleName(chest));
			}
		}
		GuildManager.markDirty();
	}

	// ------------------------------------------------------------------ block names

	/** The container and, for a double chest, its other half. */
	private static List<BlockPos> halves(ServerLevel level, BlockPos pos) {
		List<BlockPos> halves = new ArrayList<>(2);
		halves.add(pos);
		BlockPos other = Storage.otherHalf(level, pos);
		if (other != null) {
			halves.add(other);
		}
		return halves;
	}

	/** Plain text of the custom name of the container at {@code pos}, null if it has none. */
	static @Nullable String name(ServerLevel level, BlockPos pos) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity instanceof BaseContainerBlockEntity container && container.getCustomName() != null) {
			return container.getCustomName().getString();
		}
		return null;
	}

	/**
	 * Sets (or removes, {@code name} null) the custom name of a container through its saved data, like the /data command
	 * does (there is no setter): contents and loot table are saved and loaded back unchanged.
	 */
	private static void setName(ServerLevel level, BlockPos pos, @Nullable String name) {
		if (!(level.getBlockEntity(pos) instanceof BaseContainerBlockEntity container)) {
			return;
		}
		HolderLookup.Provider registries = level.registryAccess();
		CompoundTag tag = container.saveCustomOnly(registries);
		if (name == null) {
			tag.remove("CustomName");
		} else {
			tag.store("CustomName", ComponentSerialization.CODEC, registries.createSerializationContext(NbtOps.INSTANCE), Component.literal(name));
		}
		container.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
		container.setChanged();
	}
}
