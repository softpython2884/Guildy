package dev.guildy.villager;

import dev.guildy.claim.ClaimService;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * Zone fanions: typed banners (custom data, free from the menu) that register a work zone where they are planted.
 * The banner is placed here (vanilla placement skipped, like the guild flags) and named after the zone. Breaking a
 * fanion deletes its zone (VILLAGERS permission, no drop); anyone else is refused.
 */
final class ZoneFlags {
	private ZoneFlags() {
	}

	// ================================================================ items

	/**
	 * A fanion of the type; with {@code moving}, planting it moves that zone instead of creating one.
	 */
	static ItemStack create(Guild guild, ZoneType type, @Nullable WorkZone moving) {
		ItemStack stack = design(type).createBanner(GuildyServer.registryAccess());
		CompoundTag extra = new CompoundTag();
		extra.putString("type", type.name());
		extra.putString("guild", guild.id.toString());
		if (moving != null) {
			extra.putInt("zone", moving.id);
		}
		GuildyItems.tag(stack, GuildyItems.KIND_ZONE_FLAG, extra);
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		String typeName = Lang.raw(type.langKey());
		List<Component> lore = new ArrayList<>();
		if (moving != null) {
			lore.addAll(GuiUtil.lines("villager.fanion.move.lore", moving.name));
			return ItemUtil.named(stack, Lang.get("villager.fanion.move.name", moving.name), lore);
		}
		lore.addAll(GuiUtil.lines(type.descKey()));
		lore.addAll(GuiUtil.lines("villager.fanion.lore", VillagerModule.config().zones.maxZoneDistance));
		return ItemUtil.named(stack, Lang.get("villager.fanion.name", typeName), lore);
	}

	/** Plain banner with the look of the type's fanion (menu icons). */
	static ItemStack icon(ZoneType type) {
		return design(type).createBanner(GuildyServer.registryAccess());
	}

	/** Banner look of each type (base color of {@link ZoneType#color()} + motif + border). */
	private static BannerDesign design(ZoneType type) {
		BannerDesign design = new BannerDesign();
		design.base = type.color();
		switch (type) {
			case MINE -> {
				design.layers.add(new BannerDesign.Layer("minecraft:bricks", "black"));
				design.layers.add(new BannerDesign.Layer("minecraft:border", "black"));
			}
			case FOREST -> {
				design.layers.add(new BannerDesign.Layer("minecraft:flower", "lime"));
				design.layers.add(new BannerDesign.Layer("minecraft:border", "brown"));
			}
			case FARM -> {
				design.layers.add(new BannerDesign.Layer("minecraft:stripe_bottom", "brown"));
				design.layers.add(new BannerDesign.Layer("minecraft:border", "brown"));
			}
			case PATROL -> {
				design.layers.add(new BannerDesign.Layer("minecraft:cross", "white"));
				design.layers.add(new BannerDesign.Layer("minecraft:border", "black"));
			}
		}
		return design;
	}

	static boolean isFanion(ItemStack stack) {
		return GuildyItems.is(stack, GuildyItems.KIND_ZONE_FLAG);
	}

	static @Nullable ZoneType typeOf(ItemStack stack) {
		String value = GuildyItems.data(stack).getStringOr("type", "");
		for (ZoneType type : ZoneType.values()) {
			if (type.name().equals(value)) {
				return type;
			}
		}
		return null;
	}

	/** Zone moved by this fanion, -1 for a new zone. */
	static int movedZoneId(ItemStack stack) {
		return GuildyItems.data(stack).getIntOr("zone", -1);
	}

	/** True when the player already carries an unplanted fanion of this type (same moved zone). */
	static boolean carries(ServerPlayer player, ZoneType type, int movedZoneId) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (isFanion(stack) && typeOf(stack) == type && movedZoneId(stack) == movedZoneId) {
				return true;
			}
		}
		return false;
	}

	/** Gives a fanion (one of each kind at a time: they are free). */
	static void give(ServerPlayer player, Guild guild, ZoneType type, @Nullable WorkZone moving) {
		int movedId = moving == null ? -1 : moving.id;
		if (carries(player, type, movedId)) {
			Msg.send(player, "villager.fanion.already");
			return;
		}
		if (moving == null && Zones.canCreateZone(guild) != null) {
			Msg.send(player, "villager.zone.error.limit", VillagerModule.config().zones.maxZones);
			return;
		}
		ItemUtil.giveOrDrop(player, create(guild, type, moving));
		if (moving == null) {
			Msg.send(player, "villager.fanion.given", Lang.raw(type.langKey()), VillagerModule.config().zones.maxZoneDistance);
		} else {
			Msg.send(player, "villager.fanion.move.given", moving.name);
		}
		Msg.sound(player, SoundEvents.ARMOR_EQUIP_LEATHER, 0.8f, 1.0f);
	}

	// ================================================================ planting

	/** Right-click on a block with a fanion: places the banner and registers (or moves) the zone. */
	static InteractionResult plant(ServerPlayer player, ServerLevel level, BlockHitResult hit, ItemStack stack) {
		ZoneType type = typeOf(stack);
		if (type == null) {
			return refuse(player, level, hit, "villager.fanion.invalid");
		}
		Guild guild = GuildManager.of(player);
		if (guild == null) {
			return refuse(player, level, hit, "error.no_guild");
		}
		if (!guild.can(player.getUUID(), GuildPermission.VILLAGERS)) {
			return refuse(player, level, hit, "error.no_permission");
		}
		UUID owner = GuildyItems.guildOf(stack);
		if (owner != null && !owner.equals(guild.id)) {
			return refuse(player, level, hit, "villager.fanion.not_yours");
		}
		if (!player.mayBuild()) {
			return refuse(player, level, hit, "villager.fanion.cannot_place");
		}
		BlockPos clicked = hit.getBlockPos();
		Direction face = hit.getDirection();
		boolean replaceClicked = level.getBlockState(clicked).canBeReplaced();
		if (face == Direction.DOWN && !replaceClicked) {
			return refuse(player, level, hit, "villager.fanion.place_floor");
		}
		BlockPos target = replaceClicked ? clicked : clicked.relative(face);
		BlockState banner = placementState(level, stack, target, face, replaceClicked, player.getYRot());
		if (banner == null || !canPlaceAt(player, level, target)) {
			return refuse(player, level, hit, "villager.fanion.cannot_place");
		}
		VillagerConfig config = VillagerModule.config();
		String error = Zones.checkPlacement(level, guild, target);
		if (error != null) {
			return refuse(player, level, hit, error, config.zones.maxZoneDistance);
		}
		WorkZone moving = guild.villagers.zone(movedZoneId(stack));
		if (moving != null && moving.type != type) {
			moving = null;
		}
		if (moving == null && Zones.canCreateZone(guild) != null) {
			return refuse(player, level, hit, "villager.zone.error.limit", config.zones.maxZones);
		}
		Zones.Located occupant = Zones.fanionAt(target);
		if (occupant != null && occupant.zone() != moving) {
			return refuse(player, level, hit, "villager.zone.error.occupied");
		}
		String name = moving != null ? moving.name : Zones.defaultZoneName(guild, type);
		if (!placeBanner(level, target, banner, stack, name)) {
			return refuse(player, level, hit, "villager.fanion.cannot_place");
		}
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		boolean outside = GuildManager.at(level, target) != guild;
		WorkZone zone;
		if (moving != null) {
			Zones.moveZone(level, guild, moving, target);
			zone = moving;
			Msg.send(player, "villager.zone.moved", zone.name, target.getX(), target.getY(), target.getZ());
		} else {
			zone = Zones.createZone(guild, type, target, name, true);
			Msg.guild(guild, "villager.zone.created", player.getName().getString(), zone.name, Lang.raw(type.langKey()),
					target.getX(), target.getY(), target.getZ());
		}
		if (outside) {
			Msg.send(player, "villager.zone.outside_hint");
		}
		Msg.sound(player, SoundEvents.UI_LOOM_TAKE_RESULT, 0.8f, 1.0f);
		ZoneGui.openZone(player, guild, zone.id);
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

	/** Places the banner with the fanion's patterns (looms may decorate fanions) and the zone name, never its custom data. */
	private static boolean placeBanner(ServerLevel level, BlockPos target, BlockState state, ItemStack fanion, String name) {
		if (!level.setBlock(target, state, Block.UPDATE_ALL_IMMEDIATE)) {
			return false;
		}
		if (level.getBlockEntity(target) instanceof BannerBlockEntity banner) {
			ItemStack carrier = new ItemStack(fanion.getItem());
			carrier.set(DataComponents.BANNER_PATTERNS, fanion.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY));
			carrier.set(DataComponents.CUSTOM_NAME, Component.literal(name));
			banner.applyComponentsFromItemStack(carrier);
			banner.setChanged();
			level.sendBlockUpdated(target, state, state, Block.UPDATE_CLIENTS);
		}
		level.playSound(null, target, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
		return true;
	}

	/** Renames a placed banner, keeping its patterns. */
	static void applyName(ServerLevel level, BlockPos pos, BannerBlockEntity banner, Component name) {
		ItemStack carrier = new ItemStack(Items.BANNER.pick(banner.getBaseColor()));
		carrier.set(DataComponents.BANNER_PATTERNS, banner.getPatterns());
		carrier.set(DataComponents.CUSTOM_NAME, name);
		banner.applyComponentsFromItemStack(carrier);
		banner.setChanged();
		BlockState state = level.getBlockState(pos);
		level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
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

	// ================================================================ breaking

	/**
	 * A player breaks a block: true lets it break. A fanion is deleted with its zone by a member allowed to manage
	 * the villagers (block removed here: no drop); anyone else is refused.
	 */
	static boolean onBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		if (!(state.getBlock() instanceof AbstractBannerBlock) || !GuildyServer.isClaimLevel(level) || player.isSpectator()) {
			return true;
		}
		Zones.Located located = Zones.fanionAt(pos);
		if (located == null) {
			return true;
		}
		Guild guild = located.guild();
		WorkZone zone = located.zone();
		boolean allowed = guild.can(player.getUUID(), GuildPermission.VILLAGERS) || ClaimService.hasBypass(player);
		if (!allowed) {
			Guild landlord = GuildManager.at(level, pos);
			if (landlord != null && landlord != guild && landlord.can(player.getUUID(), GuildPermission.BUILD)) {
				// Land claimed by another guild after the fanion was planted: its members may clear it (the zone stays).
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				Msg.guild(guild, "villager.fanion.removed_by_landlord", zone.name, landlord.name);
				return false;
			}
			Msg.actionBar(player, "villager.fanion.protected", zone.name, guild.name);
			resyncLater(player, level, pos);
			return false;
		}
		Zones.deleteZone(level, guild, zone);
		Msg.guild(guild, "villager.zone.deleted_by", player.getName().getString(), zone.name);
		Msg.sound(player, SoundEvents.BEACON_DEACTIVATE, 0.6f, 1.4f);
		return false;
	}

	/** After a cancelled break the client rebuilds the banner without its patterns / name: resend them next tick. */
	private static void resyncLater(ServerPlayer player, ServerLevel level, BlockPos pos) {
		BlockPos target = pos.immutable();
		Scheduler.later("villager.fanion.resync", 1, () -> {
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
