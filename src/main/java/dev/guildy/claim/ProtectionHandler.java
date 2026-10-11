package dev.guildy.claim;

import dev.guildy.core.GuildyItems;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildPermission;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Territory protection through Fabric events: block break / use / placement, buckets, entities.
 * Rules: in a claimed chunk, breaking and placing need BUILD, other block / entity interactions need INTERACT
 * (doors optionally open to visitors), hostile mobs ({@link Enemy}) can be attacked by anyone.
 * Admins can toggle a bypass. Flag planting is dispatched to {@link FlagHandler} first.
 */
final class ProtectionHandler {
	private static final int DENY_MESSAGE_INTERVAL = 20;
	private static final Map<UUID, Long> LAST_DENY = new HashMap<>();

	private ProtectionHandler() {
	}

	static void register() {
		UseBlockCallback.EVENT.register(ProtectionHandler::onUseBlock);
		AttackBlockCallback.EVENT.register(ProtectionHandler::onAttackBlock);
		PlayerBlockBreakEvents.BEFORE.register(ProtectionHandler::onBreak);
		UseItemCallback.EVENT.register(ProtectionHandler::onUseItem);
		UseEntityCallback.EVENT.register(ProtectionHandler::onUseEntity);
		AttackEntityCallback.EVENT.register(ProtectionHandler::onAttackEntity);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ProtectionHandler::allowDamage);
	}

	static void forget(UUID player) {
		LAST_DENY.remove(player);
	}

	static void clear() {
		LAST_DENY.clear();
	}

	// ================================================================ blocks

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.PASS;
		}
		return Safe.get("protect.use_block", () -> useBlock(serverPlayer, serverLevel, hand, hit), InteractionResult.PASS);
	}

	private static InteractionResult useBlock(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit) {
		if (player.isSpectator()) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		String kind = GuildyItems.kind(stack);
		if (GuildyItems.KIND_FOUNDING_FLAG.equals(kind) || GuildyItems.KIND_EXTENSION_FLAG.equals(kind)) {
			return FlagHandler.plant(player, level, hit, stack, kind);
		}
		if (!ClaimGuard.isClaimLevel(level) || ClaimGuard.hasBypass(player)) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockPos placePos = pos.relative(hit.getDirection());
		Guild owner = ClaimGuard.ownerAt(level, pos);
		Guild placeOwner = ClaimGuard.ownerAt(level, placePos);
		if (owner == null && placeOwner == null) {
			return InteractionResult.PASS;
		}
		BlockState state = level.getBlockState(pos);
		if (owner != null && hand == InteractionHand.MAIN_HAND && isDoorUse(player, state)) {
			if (GuildyServer.config().protection.visitorsCanUseDoors || ClaimGuard.allowed(player, owner, GuildPermission.INTERACT)) {
				return InteractionResult.PASS;
			}
			return deny(player, owner, level, pos, placePos, true);
		}
		if (isBuildItem(stack)) {
			if (!ClaimGuard.allowed(player, owner, GuildPermission.BUILD)) {
				return deny(player, owner, level, pos, placePos, true);
			}
			if (!ClaimGuard.allowed(player, placeOwner, GuildPermission.BUILD)) {
				return deny(player, placeOwner, level, pos, placePos, true);
			}
			return InteractionResult.PASS;
		}
		if (!ClaimGuard.allowed(player, owner, GuildPermission.INTERACT)) {
			// silent for items used "in the air" (food, bows...) aimed at an inert block
			return deny(player, owner, level, pos, placePos, stack.isEmpty() || isInteractive(level, pos, state));
		}
		return InteractionResult.PASS;
	}

	/** Blocks with a right-click action (containers, redstone controls, cauldrons...): worth a denial message. */
	private static boolean isInteractive(ServerLevel level, BlockPos pos, BlockState state) {
		Block block = state.getBlock();
		return state.hasBlockEntity()
				|| state.getMenuProvider(level, pos) != null
				|| state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.BUTTONS)
				|| block instanceof LeverBlock || block instanceof DiodeBlock || block instanceof NoteBlock || block instanceof CakeBlock
				|| block instanceof AbstractCauldronBlock || block instanceof ComposterBlock || block instanceof FlowerPotBlock
				|| block instanceof RespawnAnchorBlock || block instanceof DragonEggBlock || block instanceof SweetBerryBushBlock;
	}

	/** A right-click that only opens/closes a door, trapdoor or fence gate (no item use). */
	private static boolean isDoorUse(ServerPlayer player, BlockState state) {
		boolean handsBusy = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
		if (player.isSecondaryUseActive() && handsBusy) {
			return false;
		}
		if (state.getBlock() instanceof DoorBlock door) {
			return door.type().canOpenByHand();
		}
		if (state.is(BlockTags.TRAPDOORS)) {
			return !state.is(Blocks.IRON_TRAPDOOR);
		}
		return state.is(BlockTags.FENCE_GATES);
	}

	/** Items whose use on a block places or alters blocks / entities (needs BUILD). */
	private static boolean isBuildItem(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		Item item = stack.getItem();
		return item instanceof BlockItem
				|| item instanceof BucketItem
				|| item instanceof HangingEntityItem
				|| item instanceof ArmorStandItem
				|| item instanceof MinecartItem
				|| item instanceof BoatItem
				|| item instanceof EndCrystalItem
				|| item instanceof SpawnEggItem
				|| item instanceof FlintAndSteelItem
				|| item instanceof FireChargeItem
				|| item instanceof BoneMealItem
				|| item instanceof HoneycombItem
				|| item instanceof ShearsItem
				|| item instanceof BrushItem
				|| stack.has(DataComponents.BLOCK_TRANSFORMER)
				|| stack.has(DataComponents.TOOL);
	}

	private static InteractionResult onAttackBlock(Player player, Level level, InteractionHand hand, BlockPos pos, Direction direction) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		return Safe.get("protect.attack_block", () -> mayBreak(serverPlayer, level, pos) ? InteractionResult.PASS : InteractionResult.FAIL,
				InteractionResult.PASS);
	}

	private static boolean onBreak(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return true;
		}
		return Safe.get("protect.break", () -> {
			if (mayBreak(serverPlayer, level, pos)) {
				return true;
			}
			resyncLater(serverPlayer, level, pos);
			return false;
		}, true);
	}

	/** Heart (and its support) unbreakable for everyone; otherwise BUILD in claimed chunks. Sends the denial message. */
	private static boolean mayBreak(ServerPlayer player, Level level, BlockPos pos) {
		if (player.isSpectator() || ClaimGuard.hasBypass(player) || !ClaimGuard.isClaimLevel(level)) {
			return true;
		}
		if (HeartIndex.heartOrSupport(level, pos) != null) {
			if (throttle(player)) {
				Msg.actionBar(player, "protect.heart");
			}
			return false;
		}
		Guild owner = ClaimGuard.ownerAt(level, pos);
		if (ClaimGuard.allowed(player, owner, GuildPermission.BUILD)) {
			return true;
		}
		notifyDenied(player, owner);
		return false;
	}

	/**
	 * After a cancelled break the client rebuilds the block without its block entity data (banner patterns, sign
	 * text): resend it next tick, after vanilla's block update.
	 */
	private static void resyncLater(ServerPlayer player, Level level, BlockPos pos) {
		BlockPos target = pos.immutable();
		Scheduler.later("protect.resync", 1, () -> {
			if (player.hasDisconnected() || player.level() != level) {
				return;
			}
			player.connection.send(new ClientboundBlockUpdatePacket(level, target.above()));
			player.connection.send(new ClientboundBlockUpdatePacket(level, target.below()));
			BlockEntity blockEntity = level.getBlockEntity(target);
			if (blockEntity != null) {
				Packet<?> packet = blockEntity.getUpdatePacket();
				if (packet != null) {
					player.connection.send(packet);
				}
			}
		});
	}

	// ================================================================ items (buckets & co)

	private static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.PASS;
		}
		return Safe.get("protect.use_item", () -> useItem(serverPlayer, serverLevel, hand), InteractionResult.PASS);
	}

	private static InteractionResult useItem(ServerPlayer player, ServerLevel level, InteractionHand hand) {
		if (player.isSpectator() || !ClaimGuard.isClaimLevel(level) || ClaimGuard.hasBypass(player)) {
			return InteractionResult.PASS;
		}
		Item item = player.getItemInHand(hand).getItem();
		ClipContext.Fluid fluid;
		if (item instanceof BucketItem bucket) {
			fluid = bucket.getFluidContext();
		} else if (item instanceof PlaceOnWaterBlockItem) {
			fluid = ClipContext.Fluid.SOURCE_ONLY;
		} else {
			return InteractionResult.PASS;
		}
		Vec3 from = player.getEyePosition();
		Vec3 to = from.add(player.getViewVector(1.0F).scale(player.blockInteractionRange()));
		BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, fluid, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockPos placePos = pos.relative(hit.getDirection());
		Guild owner = ClaimGuard.ownerAt(level, pos);
		if (!ClaimGuard.allowed(player, owner, GuildPermission.BUILD)) {
			return deny(player, owner, level, pos, placePos, true);
		}
		Guild placeOwner = ClaimGuard.ownerAt(level, placePos);
		if (!ClaimGuard.allowed(player, placeOwner, GuildPermission.BUILD)) {
			return deny(player, placeOwner, level, pos, placePos, true);
		}
		return InteractionResult.PASS;
	}

	// ================================================================ entities

	/** Entities protected in claims: everything except hostile mobs and players. */
	private static boolean isProtected(Entity entity) {
		return !(entity instanceof Enemy) && !(entity instanceof Player);
	}

	/** Hurting animals / villagers needs INTERACT; breaking decorations and vehicles needs BUILD. */
	private static GuildPermission attackPermission(Entity entity) {
		return entity instanceof LivingEntity && !(entity instanceof ArmorStand) ? GuildPermission.INTERACT : GuildPermission.BUILD;
	}

	private static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		return Safe.get("protect.use_entity", () -> {
			if (entityAllowed(serverPlayer, entity, GuildPermission.INTERACT)) {
				return InteractionResult.PASS;
			}
			serverPlayer.containerMenu.sendAllDataToRemote();
			return InteractionResult.FAIL;
		}, InteractionResult.PASS);
	}

	private static InteractionResult onAttackEntity(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		return Safe.get("protect.attack_entity", () -> attackAllowed(serverPlayer, entity) ? InteractionResult.PASS : InteractionResult.FAIL,
				InteractionResult.PASS);
	}

	/** Projectiles, sweeping attacks, fire aspect... on protected living entities (incl. armor stands). */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(source.getEntity() instanceof ServerPlayer attacker) || attacker == entity) {
			return true;
		}
		return Safe.get("protect.damage", () -> attackAllowed(attacker, entity), true);
	}

	/** Self-defence: a neutral mob (wolf, bee, golem...) currently targeting the player can always be fought back. */
	private static boolean attackAllowed(ServerPlayer player, Entity entity) {
		if (entity instanceof Mob mob && mob.getTarget() == player) {
			return true;
		}
		return entityAllowed(player, entity, attackPermission(entity));
	}

	/** Checks a protected entity in a claim; sends the (throttled) denial message when refused. */
	private static boolean entityAllowed(ServerPlayer player, Entity entity, GuildPermission permission) {
		if (!GuildyServer.config().protection.entities || player.isSpectator() || !isProtected(entity)) {
			return true;
		}
		Guild owner = ClaimGuard.ownerAt(entity.level(), entity.blockPosition());
		if (ClaimGuard.allowed(player, owner, permission)) {
			return true;
		}
		notifyDenied(player, owner);
		return false;
	}

	// ================================================================ feedback

	private static InteractionResult deny(ServerPlayer player, @Nullable Guild owner, ServerLevel level, BlockPos pos, BlockPos placePos,
			boolean notify) {
		if (notify) {
			notifyDenied(player, owner);
		}
		player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
		player.connection.send(new ClientboundBlockUpdatePacket(level, placePos));
		player.containerMenu.sendAllDataToRemote();
		return InteractionResult.FAIL;
	}

	/** Action bar "this territory belongs to X" (or "your role cannot"), at most once per second. */
	static void notifyDenied(ServerPlayer player, @Nullable Guild owner) {
		if (!throttle(player)) {
			return;
		}
		if (owner != null && owner.isMember(player.getUUID())) {
			Msg.actionBar(player, "protect.denied_role");
		} else {
			Msg.actionBar(player, "protect.denied", owner == null ? "?" : owner.name);
		}
	}

	private static boolean throttle(ServerPlayer player) {
		long now = Scheduler.ticks();
		Long last = LAST_DENY.get(player.getUUID());
		if (last != null && now >= last && now - last < DENY_MESSAGE_INTERVAL) {
			return false;
		}
		LAST_DENY.put(player.getUUID(), now);
		return true;
	}
}
