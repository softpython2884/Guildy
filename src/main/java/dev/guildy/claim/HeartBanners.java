package dev.guildy.claim;

import dev.guildy.config.GuildyConfig;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapBanner;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * The heart banner is the guild's identity in the world:
 * <ul>
 * <li>it always shows the current guild design and carries the guild name (re-synced lazily when its chunk is
 * loaded, so a banner adopted later or a heart planted by an older version gets updated);</li>
 * <li>its vanilla map banner marker (colored icon + guild name) is added automatically to the filled maps players
 * hold, as if they had clicked the banner with the map.</li>
 * </ul>
 */
final class HeartBanners {
	private HeartBanners() {
	}

	static void register() {
		Scheduler.every("heart.banners", 100, 37, HeartBanners::syncAll);
		Scheduler.every("heart.maps", 40, 13, HeartBanners::markHeldMaps);
	}

	static void syncAll() {
		for (Guild guild : GuildManager.all()) {
			sync(guild);
		}
	}

	/** Makes the heart banner match the guild design and name (only when its chunk is loaded). */
	static void sync(Guild guild) {
		BlockPos heart = guild.heart;
		ServerLevel level = GuildyServer.claimLevel();
		if (heart == null || level == null || !level.isLoaded(heart)) {
			return;
		}
		BlockState state = level.getBlockState(heart);
		if (!(state.getBlock() instanceof AbstractBannerBlock bannerBlock)) {
			return;
		}
		DyeColor color = guild.banner.baseColor();
		BannerPatternLayers patterns = guild.banner.toLayers(GuildyServer.registryAccess());
		Component name = Component.literal(guild.name);
		boolean sameColor = bannerBlock.getColor() == color;
		if (sameColor && level.getBlockEntity(heart) instanceof BannerBlockEntity banner
				&& banner.getPatterns().equals(patterns) && name.equals(banner.getCustomName())) {
			return;
		}
		if (!sameColor) {
			BlockState repainted = state.getBlock() instanceof WallBannerBlock
					? Blocks.WALL_BANNER.pick(color).defaultBlockState().setValue(WallBannerBlock.FACING, state.getValue(WallBannerBlock.FACING))
					: Blocks.BANNER.pick(color).defaultBlockState().setValue(BannerBlock.ROTATION, state.getValue(BannerBlock.ROTATION));
			level.setBlock(heart, repainted, Block.UPDATE_ALL);
		}
		if (level.getBlockEntity(heart) instanceof BannerBlockEntity banner) {
			ItemStack carrier = new ItemStack(Items.BANNER.pick(color));
			carrier.set(DataComponents.BANNER_PATTERNS, patterns);
			carrier.set(DataComponents.CUSTOM_NAME, name);
			banner.applyComponentsFromItemStack(carrier);
			banner.setChanged();
			BlockState now = level.getBlockState(heart);
			level.sendBlockUpdated(heart, now, now, Block.UPDATE_CLIENTS);
		}
	}

	/** Adds the hearts inside the area of every filled map held by a player (main or off hand). */
	static void markHeldMaps() {
		GuildyConfig.Banners config = GuildyServer.config().banners;
		ServerLevel level = GuildyServer.claimLevel();
		if (!config.heartOnMaps || level == null) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			for (InteractionHand hand : InteractionHand.values()) {
				ItemStack stack = player.getItemInHand(hand);
				if (!stack.is(Items.FILLED_MAP)) {
					continue;
				}
				MapItemSavedData data = MapItem.getSavedData(stack, level);
				if (data == null || !data.dimension.equals(level.dimension())) {
					continue;
				}
				Guild own = GuildManager.of(player);
				for (Guild guild : GuildManager.all()) {
					if (guild.heart != null && (config.allGuildsOnMaps || guild == own)) {
						mark(level, data, guild.heart);
					}
				}
			}
		}
	}

	private static void mark(ServerLevel level, MapItemSavedData data, BlockPos heart) {
		int scale = 1 << data.scale;
		double dx = (heart.getX() + 0.5 - data.centerX) / scale;
		double dz = (heart.getZ() + 0.5 - data.centerZ) / scale;
		if (Math.abs(dx) > 63 || Math.abs(dz) > 63 || !level.isLoaded(heart)) {
			return;
		}
		MapBanner current = MapBanner.fromWorld(level, heart);
		if (current != null && !data.getBanners().contains(current)) {
			// Adds the marker (or replaces a stale one at the same position: same key).
			data.toggleBanner(level, heart);
		}
	}
}
