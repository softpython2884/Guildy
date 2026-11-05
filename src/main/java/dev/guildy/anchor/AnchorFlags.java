package dev.guildy.anchor;

import dev.guildy.claim.ClaimService;
import dev.guildy.command.GuildActions.Denial;
import dev.guildy.config.GuildyConfig;
import dev.guildy.core.GuildyItems;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Scheduler;
import dev.guildy.data.BannerDesign;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import dev.guildy.gui.GuiUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Anchor flags: typed banners (custom data kind {@value GuildyItems#KIND_ANCHOR_FLAG}, bound to a guild) taken from the
 * anchors menu. Planted (vanilla placement skipped, like the zone fanions) in the guild's territory, the banner keeps
 * the chunks around it active ({@link AnchorTickets}). A member allowed to claim breaks it to get the flag back; anyone
 * else is refused. The planted banner keeps only the patterns and a name, never the custom data.
 */
public final class AnchorFlags {
	private AnchorFlags() {
	}

	// ================================================================ items

	/** An anchor flag bound to the guild. */
	public static ItemStack create(Guild guild) {
		ItemStack stack = design().createBanner(GuildyServer.registryAccess());
		CompoundTag extra = new CompoundTag();
		extra.putString("guild", guild.id.toString());
		GuildyItems.tag(stack, GuildyItems.KIND_ANCHOR_FLAG, extra);
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		GuildyConfig.Anchors config = GuildyServer.config().anchors;
		int side = 2 * config.radius + 1;
		List<Component> lore = new ArrayList<>(GuiUtil.lines("anchor.item.lore", guild.name, side));
		if (config.mobSpawning) {
			lore.addAll(GuiUtil.lines("anchor.item.lore.mobs"));
		}
		return ItemUtil.named(stack, Lang.get("anchor.item.name"), lore);
	}

	/** Plain banner with the look of an anchor flag (menu icons). */
	public static ItemStack icon() {
		return design().createBanner(GuildyServer.registryAccess());
	}

	private static BannerDesign design() {
		BannerDesign design = new BannerDesign();
		design.base = "black";
		design.layers.add(new BannerDesign.Layer("minecraft:globe", "light_blue"));
		design.layers.add(new BannerDesign.Layer("minecraft:border", "cyan"));
		return design;
	}

	public static boolean isAnchorFlag(ItemStack stack) {
		return GuildyItems.is(stack, GuildyItems.KIND_ANCHOR_FLAG);
	}

	/** True when the player carries an unplanted anchor flag of the guild. */
	static boolean carries(ServerPlayer player, Guild guild) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (isAnchorFlag(stack) && guild.id.equals(GuildyItems.guildOf(stack))) {
				return true;
			}
		}
		return false;
	}

	/** Why the player may not take an anchor flag now, or null. */
	static @Nullable Denial takeDenial(ServerPlayer player, Guild guild) {
		GuildyConfig.Anchors config = GuildyServer.config().anchors;
		if (!config.enabled) {
			return new Denial("anchor.error.disabled");
		}
		if (!guild.can(player.getUUID(), GuildPermission.CLAIM)) {
			return new Denial("error.no_permission");
		}
		if (guild.level < config.minGuildLevel) {
			return new Denial("anchor.error.level", config.minGuildLevel);
		}
		int allowed = AnchorTickets.allowed(guild);
		if (guild.anchors.flags.size() >= allowed) {
			return new Denial("anchor.error.limit", allowed);
		}
		if (carries(player, guild)) {
			return new Denial("anchor.error.carrying");
		}
		if (config.price > 0) {
			if (!guild.can(player.getUUID(), GuildPermission.TREASURY)) {
				return new Denial("error.no_permission");
			}
			if (guild.treasury < config.price) {
				return new Denial("error.not_enough_treasury", config.price);
			}
		}
		return null;
	}

	/** Gives an anchor flag (paid by the treasury when anchors.price &gt; 0). */
	static boolean take(ServerPlayer player, Guild guild) {
		Denial denial = takeDenial(player, guild);
		if (denial != null) {
			denial.send(player);
			return false;
		}
		int price = GuildyServer.config().anchors.price;
		if (price > 0 && !GuildManager.takeTreasury(guild, price)) {
			Msg.send(player, "error.not_enough_treasury", price);
			return false;
		}
		ItemUtil.giveOrDrop(player, create(guild));
		Msg.send(player, price > 0 ? "anchor.given.paid" : "anchor.given", price);
		Msg.sound(player, SoundEvents.ARMOR_EQUIP_LEATHER, 0.8f, 1.0f);
		return true;
	}

	// ================================================================ planting

	/** Right-click on a block with an anchor flag: places the banner and starts the anchor. */
	static InteractionResult plant(ServerPlayer player, ServerLevel level, BlockHitResult hit, ItemStack stack) {
		GuildyConfig.Anchors config = GuildyServer.config().anchors;
		if (!config.enabled) {
			return refuse(player, level, hit, "anchor.error.disabled");
		}
		Guild guild = GuildManager.of(player);
		if (guild == null) {
			return refuse(player, level, hit, "error.no_guild");
		}
		UUID owner = GuildyItems.guildOf(stack);
		if (owner != null && !owner.equals(guild.id)) {
			return refuse(player, level, hit, "anchor.error.not_yours");
		}
		if (!guild.can(player.getUUID(), GuildPermission.CLAIM)) {
			return refuse(player, level, hit, "error.no_permission");
		}
		if (guild.level < config.minGuildLevel) {
			return refuse(player, level, hit, "anchor.error.level", config.minGuildLevel);
		}
		int allowed = AnchorTickets.allowed(guild);
		if (guild.anchors.flags.size() >= allowed) {
			return refuse(player, level, hit, "anchor.error.limit", allowed);
		}
		if (!player.mayBuild()) {
			return refuse(player, level, hit, "anchor.error.cannot_place");
		}
		BlockPos clicked = hit.getBlockPos();
		Direction face = hit.getDirection();
		boolean replaceClicked = level.getBlockState(clicked).canBeReplaced();
		if (face == Direction.DOWN && !replaceClicked) {
			return refuse(player, level, hit, "anchor.error.place_floor");
		}
		BlockPos target = replaceClicked ? clicked : clicked.relative(face);
		Guild landlord = GuildyServer.isClaimLevel(level) ? GuildManager.at(level, target) : null;
		if (landlord != null && landlord != guild) {
			return refuse(player, level, hit, "anchor.error.foreign", landlord.name);
		}
		if (landlord == null && !config.outsideTerritory) {
			return refuse(player, level, hit, "anchor.error.outside");
		}
		BlockState banner = placementState(level, stack, target, face, replaceClicked, player.getYRot());
		if (banner == null || !canPlaceAt(player, level, target)) {
			return refuse(player, level, hit, "anchor.error.cannot_place");
		}
		if (!placeBanner(level, target, banner, stack, Lang.raw("anchor.banner_name", guild.name))) {
			return refuse(player, level, hit, "anchor.error.cannot_place");
		}
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		ChunkAnchor anchor = guild.anchors.add(AnchorTickets.dimensionId(level), target, player.getName().getString());
		GuildManager.markDirty();
		AnchorTickets.refresh();
		Msg.guild(guild, "anchor.planted", player.getName().getString(), target.getX(), target.getY(), target.getZ(),
				AnchorTickets.chunksPerAnchor());
		AnchorTickets.State state = AnchorTickets.state(guild, anchor);
		if (state != AnchorTickets.State.ACTIVE) {
			Msg.send(player, "anchor.planted.inactive", Lang.raw(state.langKey()));
		}
		Msg.sound(player, SoundEvents.BEACON_ACTIVATE, 0.7f, 1.2f);
		return InteractionResult.SUCCESS;
	}

	/** Standing banner on top of a block (rotation from the player's yaw, like vanilla), wall banner on a side. */
	private static @Nullable BlockState placementState(ServerLevel level, ItemStack stack, BlockPos target, Direction face,
			boolean replaceClicked, float yaw) {
		DyeColor color = stack.getItem() instanceof BannerItem bannerItem ? bannerItem.getColor() : DyeColor.BLACK;
		BlockState standing = Blocks.BANNER.pick(color).defaultBlockState()
				.setValue(BannerBlock.ROTATION, RotationSegment.convertToSegment(yaw + 180.0F));
		List<BlockState> candidates = new ArrayList<>(2);
		if (face == Direction.UP) {
			candidates.add(standing);
		} else if (face.getAxis().isHorizontal()) {
			if (replaceClicked) {
				candidates.add(standing);
			}
			candidates.add(Blocks.WALL_BANNER.pick(color).defaultBlockState().setValue(WallBannerBlock.FACING, face));
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

	/** Places the banner with the flag's patterns (looms may decorate it) and a name, never its custom data. */
	private static boolean placeBanner(ServerLevel level, BlockPos target, BlockState state, ItemStack flag, String name) {
		if (!level.setBlock(target, state, Block.UPDATE_ALL_IMMEDIATE)) {
			return false;
		}
		if (level.getBlockEntity(target) instanceof BannerBlockEntity banner) {
			ItemStack carrier = new ItemStack(flag.getItem());
			carrier.set(DataComponents.BANNER_PATTERNS, flag.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY));
			carrier.set(DataComponents.CUSTOM_NAME, Component.literal(name));
			banner.applyComponentsFromItemStack(carrier);
			banner.setChanged();
			level.sendBlockUpdated(target, state, state, Block.UPDATE_CLIENTS);
		}
		level.playSound(null, target, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
		return true;
	}

	/** Error message, then undo the client's predicted placement (blocks + inventory). */
	private static InteractionResult refuse(ServerPlayer player, ServerLevel level, BlockHitResult hit, String key, Object... args) {
		Msg.send(player, key, args);
		Msg.sound(player, SoundEvents.VILLAGER_NO, 0.6f, 1.0f);
		BlockPos clicked = hit.getBlockPos();
		player.connection.send(new ClientboundBlockUpdatePacket(level, clicked));
		player.connection.send(new ClientboundBlockUpdatePacket(level, clicked.relative(hit.getDirection())));
		player.containerMenu.sendAllDataToRemote();
		return InteractionResult.FAIL;
	}

	// ================================================================ removing

	/** The guild and anchor whose banner stands at this position of the level, or null. */
	static @Nullable Located at(ServerLevel level, BlockPos pos) {
		String dimension = AnchorTickets.dimensionId(level);
		for (Guild guild : GuildManager.all()) {
			if (guild.anchors.flags.isEmpty()) {
				continue;
			}
			ChunkAnchor anchor = guild.anchors.at(dimension, pos);
			if (anchor != null) {
				return new Located(guild, anchor);
			}
		}
		return null;
	}

	record Located(Guild guild, ChunkAnchor anchor) {
	}

	/**
	 * A player breaks a block: true lets it break. An anchor banner is taken down by a member allowed to claim (the flag
	 * goes back to them) or an admin in bypass mode; anyone else is refused, except members of the guild that has since
	 * claimed the land (the anchor stops, the flag is lost).
	 */
	static boolean onBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		if (!(state.getBlock() instanceof AbstractBannerBlock) || player.isSpectator()) {
			return true;
		}
		Located located = at(level, pos);
		if (located == null) {
			return true;
		}
		Guild guild = located.guild();
		ChunkAnchor anchor = located.anchor();
		boolean member = guild.can(player.getUUID(), GuildPermission.CLAIM);
		if (!member && !ClaimService.hasBypass(player)) {
			Guild landlord = GuildyServer.isClaimLevel(level) ? GuildManager.at(level, pos) : null;
			if (landlord != null && landlord != guild && landlord.can(player.getUUID(), GuildPermission.BUILD)) {
				remove(level, guild, anchor);
				Msg.guild(guild, "anchor.removed_by_landlord", pos.getX(), pos.getY(), pos.getZ(), landlord.name);
				return false;
			}
			Msg.actionBar(player, "anchor.protected", guild.name);
			resyncLater(player, level, pos);
			return false;
		}
		remove(level, guild, anchor);
		if (member) {
			ItemUtil.giveOrDrop(player, create(guild));
		}
		Msg.guild(guild, "anchor.removed", player.getName().getString(), pos.getX(), pos.getY(), pos.getZ());
		Msg.sound(player, SoundEvents.BEACON_DEACTIVATE, 0.6f, 1.4f);
		return false;
	}

	/** Takes an anchor down from the menu: banner removed (its chunk loaded if needed), flag given back to the player. */
	static boolean takeDown(ServerPlayer player, Guild guild, int anchorId) {
		ChunkAnchor anchor = guild.anchors.get(anchorId);
		if (anchor == null) {
			Msg.send(player, "anchor.error.gone");
			return false;
		}
		if (!guild.can(player.getUUID(), GuildPermission.CLAIM)) {
			Msg.send(player, "error.no_permission");
			return false;
		}
		MinecraftServer server = player.level().getServer();
		ServerLevel level = AnchorTickets.level(server, anchor.dimension);
		remove(level, guild, anchor);
		ItemUtil.giveOrDrop(player, create(guild));
		Msg.guild(guild, "anchor.removed", player.getName().getString(), anchor.pos.getX(), anchor.pos.getY(), anchor.pos.getZ());
		return true;
	}

	/** Forgets the anchor, removes its banner without drop and releases its chunks. */
	static void remove(@Nullable ServerLevel level, Guild guild, ChunkAnchor anchor) {
		guild.anchors.flags.remove(anchor);
		GuildManager.markDirty();
		if (level != null) {
			// One chunk read at most (a rare click); the anchor usually keeps it loaded anyway.
			level.getChunk(anchor.chunkX(), anchor.chunkZ());
			if (level.getBlockState(anchor.pos).getBlock() instanceof AbstractBannerBlock) {
				level.setBlock(anchor.pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		AnchorTickets.refresh();
	}

	/** Disbanded guild: its anchor banners go (those of unloaded chunks are left as plain banners). */
	static void onDisband(Guild guild) {
		MinecraftServer server = GuildyServer.server();
		if (server == null) {
			return;
		}
		for (ChunkAnchor anchor : List.copyOf(guild.anchors.flags)) {
			ServerLevel level = AnchorTickets.level(server, anchor.dimension);
			if (level != null && level.isLoaded(anchor.pos) && level.getBlockState(anchor.pos).getBlock() instanceof AbstractBannerBlock) {
				level.setBlock(anchor.pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		guild.anchors.flags.clear();
	}

	/** After a cancelled break the client rebuilds the banner without its patterns / name: resend them next tick. */
	private static void resyncLater(ServerPlayer player, ServerLevel level, BlockPos pos) {
		BlockPos target = pos.immutable();
		Scheduler.later("anchor.resync", 1, () -> {
			if (player.hasDisconnected() || player.level() != level) {
				return;
			}
			player.connection.send(new ClientboundBlockUpdatePacket(level, target));
			BlockEntity blockEntity = level.getBlockEntity(target);
			if (blockEntity != null) {
				Packet<?> packet = blockEntity.getUpdatePacket();
				if (packet != null) {
					player.connection.send(packet);
				}
			}
		});
	}
}
