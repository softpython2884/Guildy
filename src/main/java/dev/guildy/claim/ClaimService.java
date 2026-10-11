package dev.guildy.claim;

import dev.guildy.config.GuildyConfig;
import dev.guildy.core.GuildyItems;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Hooks;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import dev.guildy.data.GuildRole;
import dev.guildy.upgrade.UpgradeEffects;
import dev.guildy.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Territory operations used by commands and menus. Methods returning a String return null on success,
 * otherwise the lang key of the error (already-formatted messages are sent by the caller with Msg.send).
 * <p>
 * Error keys never contain placeholders. On success the service gives its own feedback (guild broadcast, item,
 * sound), so callers only need to refresh their menu.
 */
public final class ClaimService {
	/** Chunk owner lookup for {@link ClaimRules} (claim dimension). */
	static final ClaimRules.Owners OWNERS = key -> {
		Guild guild = GuildManager.atChunk(key);
		return guild == null ? null : guild.id;
	};

	private ClaimService() {
	}

	private static GuildyConfig.Territory territory() {
		return GuildyServer.config().territory;
	}

	// ================================================================ validation (shared with flag planting)

	static @Nullable String resultKey(ClaimRules.Result result) {
		return switch (result) {
			case OK -> null;
			case TAKEN -> "claim.error.taken";
			case ALREADY_OWNED -> "claim.error.already_owned";
			case MAX_CHUNKS -> "claim.error.max_chunks";
			case NOT_CONNECTED -> "claim.error.not_connected";
			case TOO_CLOSE -> "claim.error.too_close";
			case TOO_CLOSE_TO_SPAWN -> "claim.error.too_close_spawn";
		};
	}

	/** Founding rules for a heart planted in chunk {@code center}; fills {@code out} with the chunks to claim. */
	static @Nullable String validateFounding(ServerPlayer player, Guild guild, Level level, long center, List<Long> out) {
		UUID uuid = player.getUUID();
		if (!guild.isMember(uuid)) {
			return "error.no_guild";
		}
		if (!guild.can(uuid, GuildPermission.CLAIM)) {
			return "error.no_permission";
		}
		if (guild.isFounded()) {
			return "claim.error.already_founded";
		}
		if (!ClaimGuard.isClaimLevel(level)) {
			return "claim.error.wrong_dimension";
		}
		GuildyConfig.Territory cfg = territory();
		ClaimRules.Check check = ClaimRules.checkFounding(guild.id, ClaimRules.square(center, cfg.foundingRadius), OWNERS,
				cfg.minDistanceBetweenGuilds, ClaimGuard.spawnChunk(), cfg.minDistanceFromSpawn);
		if (!check.ok()) {
			return resultKey(check.result());
		}
		out.addAll(check.newChunks());
		return null;
	}

	/** Extension rules for the square (territory.extensionRadius) around {@code center}; fills {@code out}. */
	static @Nullable String validateExtension(ServerPlayer player, Guild guild, long center, List<Long> out) {
		UUID uuid = player.getUUID();
		if (!guild.isMember(uuid)) {
			return "error.no_guild";
		}
		if (!guild.can(uuid, GuildPermission.CLAIM)) {
			return "error.no_permission";
		}
		if (!guild.isFounded()) {
			return "claim.error.not_founded";
		}
		GuildyConfig.Territory cfg = territory();
		ClaimRules.Check check = ClaimRules.checkExtension(guild.id, guild.claims(), ClaimRules.square(center, cfg.extensionRadius),
				OWNERS, guild.maxChunks(), cfg.requireConnected, cfg.minDistanceBetweenGuilds, ClaimGuard.spawnChunk(),
				cfg.minDistanceFromSpawn);
		if (!check.ok()) {
			return resultKey(check.result());
		}
		out.addAll(check.newChunks());
		return null;
	}

	/** Claims validated chunks for an extension and tells the guild. */
	static void applyExtension(ServerPlayer player, Guild guild, List<Long> chunks) {
		for (long key : chunks) {
			GuildManager.claim(guild, key);
		}
		GuildManager.markDirty();
		Msg.guild(guild, "claim.extended", player.getName().getString(), chunks.size(), guild.claimCount(), guild.maxChunks());
		Msg.sound(player, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.4f);
		GuildManager.fireTerritoryChanged(guild);
	}

	/** Claims the founding square, sets the heart (and home if missing) and celebrates. */
	static void applyFounding(ServerPlayer player, Guild guild, BlockPos heart, List<Long> chunks) {
		for (long key : chunks) {
			GuildManager.claim(guild, key);
		}
		guild.heart = heart.immutable();
		if (guild.home == null) {
			guild.home = player.blockPosition();
			guild.homeYaw = player.getYRot();
		}
		GuildManager.markDirty();
		Msg.guildTitle(guild, Lang.get("claim.founded.title"), Lang.get("claim.founded.subtitle", guild.name));
		Msg.guildSound(guild, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
		Msg.guild(guild, "claim.founded.broadcast", player.getName().getString(), heart.getX(), heart.getY(), heart.getZ());
		GuildManager.fireTerritoryChanged(guild);
	}

	// ================================================================ public API

	/** Dry run of {@link #claimWithFlagFromInventory} (rules only, no flag needed): null when the chunk can be claimed. */
	public static @Nullable String checkClaim(ServerPlayer player, Guild guild, long chunkKey) {
		return validateExtension(player, guild, chunkKey, new ArrayList<>());
	}

	/** Claims {@code chunkKey} for the guild, consuming one extension flag from the player's inventory. */
	public static @Nullable String claimWithFlagFromInventory(ServerPlayer player, Guild guild, long chunkKey) {
		List<Long> chunks = new ArrayList<>();
		String error = validateExtension(player, guild, chunkKey, chunks);
		if (error != null) {
			return error;
		}
		Inventory inventory = player.getInventory();
		int slot = -1;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (GuildyItems.is(inventory.getItem(i), GuildyItems.KIND_EXTENSION_FLAG)) {
				slot = i;
				break;
			}
		}
		if (slot < 0) {
			return "claim.error.no_flag";
		}
		if (!player.hasInfiniteMaterials()) {
			ItemStack stack = inventory.getItem(slot);
			stack.shrink(1);
			if (stack.isEmpty()) {
				inventory.setItem(slot, ItemStack.EMPTY);
			}
			inventory.setChanged();
			player.containerMenu.broadcastChanges();
		}
		applyExtension(player, guild, chunks);
		return null;
	}

	/** Dry run of {@link #unclaim}: null when the chunk can be released. */
	public static @Nullable String checkUnclaim(ServerPlayer player, Guild guild, long chunkKey) {
		UUID uuid = player.getUUID();
		if (!guild.isMember(uuid)) {
			return "error.no_guild";
		}
		if (!guild.can(uuid, GuildPermission.CLAIM)) {
			return "error.no_permission";
		}
		if (!guild.owns(chunkKey)) {
			return "claim.error.not_owned";
		}
		if (guild.isFounded()) {
			long heartChunk = guild.heartChunk();
			if (chunkKey == heartChunk) {
				return "claim.error.heart_chunk";
			}
			if (!ClaimRules.staysConnected(guild.claims(), heartChunk, chunkKey)) {
				return "claim.error.disconnects";
			}
		}
		return null;
	}

	/** Unclaims a chunk (never the heart chunk; keeps the territory connected) and refunds an extension flag. */
	public static @Nullable String unclaim(ServerPlayer player, Guild guild, long chunkKey) {
		String error = checkUnclaim(player, guild, chunkKey);
		if (error != null) {
			return error;
		}
		GuildManager.unclaim(guild, chunkKey);
		ItemUtil.giveOrDrop(player, GuildyItems.extensionFlag(guild));
		Msg.guild(guild, "claim.unclaimed", player.getName().getString(), ChunkKey.x(chunkKey), ChunkKey.z(chunkKey));
		GuildManager.fireTerritoryChanged(guild);
		return null;
	}

	/** Leader only: removes all claims and the heart, gives the founding flag back. Caller asks for confirmation. */
	public static @Nullable String abandonTerritory(ServerPlayer player, Guild guild) {
		if (guild.role(player.getUUID()) != GuildRole.LEADER) {
			return "claim.error.leader_only";
		}
		if (!guild.isFounded() && guild.claims().isEmpty()) {
			return "claim.error.not_founded";
		}
		if (Hooks.isWaveActive.test(guild)) {
			return "claim.error.wave_active";
		}
		BlockPos heart = guild.heart;
		ServerLevel level = GuildyServer.claimLevel();
		if (heart != null && level != null && level.getBlockState(heart).getBlock() instanceof AbstractBannerBlock) {
			// no drops: the founding flag is given back below
			level.setBlock(heart, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		GuildManager.unclaimAll(guild);
		guild.heart = null;
		guild.home = null;
		GuildManager.markDirty();
		ItemUtil.giveOrDrop(player, GuildyItems.foundingFlag(guild));
		Msg.guild(guild, "claim.abandoned", player.getName().getString());
		Msg.guildSound(guild, SoundEvents.BEACON_DEACTIVATE, 1.0f, 1.0f);
		GuildManager.fireTerritoryChanged(guild);
		return null;
	}

	/** Shows the territory borders with particles for a few seconds. */
	public static void showBorders(ServerPlayer player, Guild guild) {
		if (guild.claims().isEmpty()) {
			Msg.send(player, "claim.error.no_territory");
			return;
		}
		if (!ClaimGuard.isClaimLevel(player.level())) {
			Msg.send(player, "territory.show.wrong_dimension");
			return;
		}
		int seconds = territory().borderDisplaySeconds;
		BorderVisualizer.show(player, guild, seconds);
		Msg.send(player, "territory.show", seconds);
	}

	public static @Nullable String setHome(ServerPlayer player, Guild guild) {
		UUID uuid = player.getUUID();
		if (!guild.isMember(uuid)) {
			return "error.no_guild";
		}
		if (!guild.can(uuid, GuildPermission.SET_HOME)) {
			return "error.no_permission";
		}
		if (ClaimGuard.ownerAt(player.level(), player.blockPosition()) != guild) {
			return "home.not_in_territory";
		}
		guild.home = player.blockPosition();
		guild.homeYaw = player.getYRot();
		GuildManager.markDirty();
		Msg.send(player, "home.set");
		Msg.sound(player, SoundEvents.NOTE_BLOCK_PLING, 0.6f, 1.5f);
		return null;
	}

	/** Teleports to the guild home (cooldown + warm-up handled here). */
	public static void teleportHome(ServerPlayer player, Guild guild) {
		HomeTeleports.request(player, guild);
	}

	/** Gives the founding flag again if the guild has no heart yet and the player doesn't already carry one. */
	public static @Nullable String giveFoundingFlag(ServerPlayer player, Guild guild) {
		UUID uuid = player.getUUID();
		if (!guild.isMember(uuid)) {
			return "error.no_guild";
		}
		if (!guild.can(uuid, GuildPermission.CLAIM)) {
			return "error.no_permission";
		}
		if (guild.isFounded()) {
			return "claim.error.already_founded";
		}
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (GuildyItems.is(stack, GuildyItems.KIND_FOUNDING_FLAG) && guild.id.equals(GuildyItems.guildOf(stack))) {
				return "claim.error.already_has_flag";
			}
		}
		ItemUtil.giveOrDrop(player, GuildyItems.foundingFlag(guild));
		Msg.send(player, "claim.flag.given");
		Msg.sound(player, SoundEvents.ARMOR_EQUIP_LEATHER, 0.8f, 1.0f);
		return null;
	}

	/** Current price (treasury gold) of the next extension flag, after the guild's upgrade discount. */
	public static int extensionFlagPrice(Guild guild) {
		return Upgrades.discount(guild, UpgradeEffects.FLAG_DISCOUNT_PCT, GuildyServer.config().extensionFlagPrice(guild.extensionFlagsBought));
	}

	/** Buys an extension flag with treasury gold (TREASURY permission) and gives it to the player. */
	public static @Nullable String buyExtensionFlag(ServerPlayer player, Guild guild) {
		UUID uuid = player.getUUID();
		if (!guild.isMember(uuid)) {
			return "error.no_guild";
		}
		if (!guild.can(uuid, GuildPermission.TREASURY)) {
			return "error.no_permission";
		}
		int price = extensionFlagPrice(guild);
		if (!GuildManager.takeTreasury(guild, price)) {
			return "claim.error.treasury";
		}
		guild.extensionFlagsBought++;
		GuildManager.markDirty();
		ItemUtil.giveOrDrop(player, GuildyItems.extensionFlag(guild));
		Msg.send(player, "claim.flag.bought", price);
		Msg.sound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
		return null;
	}

	/** True when the player is an admin in protection-bypass mode. */
	public static boolean hasBypass(ServerPlayer player) {
		return ClaimGuard.hasBypass(player);
	}

	/** Repaints the heart banner with the current guild design and name (done later if its chunk is not loaded). */
	public static void refreshHeartBanner(Guild guild) {
		HeartBanners.sync(guild);
	}
}
