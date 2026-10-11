package dev.guildy.claim;

import dev.guildy.core.GuildyItems;
import dev.guildy.data.BannerDesign;
import dev.guildy.core.Msg;
import dev.guildy.data.ChunkKey;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Planting Guildy flags (right-click on a block, server side). The banner is placed here (vanilla placement is
 * skipped): founding flag = heart of the base + founding square, extension flag = decoration + claimed chunk(s).
 */
final class FlagHandler {
	private FlagHandler() {
	}

	static InteractionResult plant(ServerPlayer player, ServerLevel level, BlockHitResult hit, ItemStack stack, String kind) {
		BlockPos clicked = hit.getBlockPos();
		Direction face = hit.getDirection();
		if (!player.mayBuild()) {
			return refuse(player, level, hit, "claim.error.cannot_place");
		}
		boolean replaceClicked = level.getBlockState(clicked).canBeReplaced();
		if (face == Direction.DOWN && !replaceClicked) {
			return refuse(player, level, hit, "claim.error.place_floor");
		}
		BlockPos target = replaceClicked ? clicked : clicked.relative(face);
		BlockState banner = placementState(level, stack, target, face, replaceClicked, player.getYRot());
		if (banner == null || !canPlaceAt(player, level, target)) {
			return refuse(player, level, hit, "claim.error.cannot_place");
		}

		Guild guild = GuildManager.of(player);
		if (guild == null) {
			return refuse(player, level, hit, "error.no_guild");
		}
		long center = ChunkKey.ofBlock(target.getX(), target.getZ());
		List<Long> chunks = new ArrayList<>();
		if (GuildyItems.KIND_FOUNDING_FLAG.equals(kind)) {
			UUID flagGuild = GuildyItems.guildOf(stack);
			if (flagGuild == null || !flagGuild.equals(guild.id)) {
				return refuse(player, level, hit, "claim.flag.not_your_flag");
			}
			String error = ClaimService.validateFounding(player, guild, level, center, chunks);
			if (error != null) {
				return refuse(player, level, hit, error);
			}
			// The founding flag may have been woven at a loom: its design becomes the guild banner.
			BannerDesign woven = BannerDesign.fromStack(stack);
			if (woven != null) {
				guild.banner = woven;
			}
			if (!placeBanner(level, target, banner, stack, guild)) {
				return refuse(player, level, hit, "claim.error.cannot_place");
			}
			consume(player, stack);
			ClaimService.applyFounding(player, guild, target, chunks);
		} else {
			if (!ClaimGuard.isClaimLevel(level)) {
				return refuse(player, level, hit, "claim.error.wrong_dimension");
			}
			String error = ClaimService.validateExtension(player, guild, center, chunks);
			if (error != null) {
				return refuse(player, level, hit, error);
			}
			if (!placeBanner(level, target, banner, stack, guild)) {
				return refuse(player, level, hit, "claim.error.cannot_place");
			}
			consume(player, stack);
			ClaimService.applyExtension(player, guild, chunks);
		}
		return InteractionResult.SUCCESS;
	}

	/** Standing banner on top of a block (rotation from the player's yaw, like vanilla), wall banner on a side. */
	private static @Nullable BlockState placementState(ServerLevel level, ItemStack stack, BlockPos target, Direction face,
			boolean replaceClicked, float yaw) {
		DyeColor color = stack.getItem() instanceof BannerItem bannerItem ? bannerItem.getColor() : DyeColor.WHITE;
		BlockState standing = Blocks.BANNER.pick(color).defaultBlockState()
				.setValue(BannerBlock.ROTATION, RotationSegment.convertToSegment(yaw + 180.0F));
		List<BlockState> candidates = new ArrayList<>(2);
		if (face == Direction.UP) {
			candidates.add(standing);
		} else if (face.getAxis().isHorizontal()) {
			BlockState wall = Blocks.WALL_BANNER.pick(color).defaultBlockState().setValue(WallBannerBlock.FACING, face);
			if (replaceClicked) {
				candidates.add(standing);
			}
			candidates.add(wall);
		} else if (replaceClicked) {
			candidates.add(standing);
		}
		for (BlockState candidate : candidates) {
			if (candidate.canSurvive(level, target)) {
				return candidate;
			}
		}
		return null;
	}

	private static boolean canPlaceAt(ServerPlayer player, ServerLevel level, BlockPos target) {
		return level.isInWorldBounds(target)
				&& level.getWorldBorder().isWithinBounds(target)
				&& !level.getServer().isUnderSpawnProtection(level, target, player)
				&& level.getBlockState(target).canBeReplaced();
	}

	/**
	 * Places the banner with the flag's patterns and the guild name (shown on maps), never the flag's custom data:
	 * a broken flag is a plain banner.
	 */
	private static boolean placeBanner(ServerLevel level, BlockPos target, BlockState state, ItemStack flag, Guild guild) {
		if (!level.setBlock(target, state, Block.UPDATE_ALL_IMMEDIATE)) {
			return false;
		}
		if (level.getBlockEntity(target) instanceof BannerBlockEntity banner) {
			ItemStack patterns = new ItemStack(flag.getItem());
			patterns.set(DataComponents.BANNER_PATTERNS, flag.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY));
			patterns.set(DataComponents.CUSTOM_NAME, Component.literal(guild.name));
			banner.applyComponentsFromItemStack(patterns);
			banner.setChanged();
			level.sendBlockUpdated(target, state, state, Block.UPDATE_CLIENTS);
		}
		level.playSound(null, target, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
		return true;
	}

	private static void consume(ServerPlayer player, ItemStack stack) {
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
	}

	/** Error message, then undo the client's predicted placement (blocks + inventory). */
	private static InteractionResult refuse(ServerPlayer player, ServerLevel level, BlockHitResult hit, String key) {
		Msg.send(player, key);
		Msg.sound(player, SoundEvents.VILLAGER_NO, 0.6f, 1.0f);
		BlockPos clicked = hit.getBlockPos();
		player.connection.send(new ClientboundBlockUpdatePacket(level, clicked));
		player.connection.send(new ClientboundBlockUpdatePacket(level, clicked.relative(hit.getDirection())));
		player.containerMenu.sendAllDataToRemote();
		return InteractionResult.FAIL;
	}
}
