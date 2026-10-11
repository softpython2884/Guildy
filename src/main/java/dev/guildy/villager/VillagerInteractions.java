package dev.guildy.villager;

import dev.guildy.claim.ClaimService;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.data.GuildPermission;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Player interactions: only guild members may trade with guild villagers (others are denied), sneak + right-click
 * opens the villager menu (VILLAGERS permission), and the storage "select a container" mode.
 */
final class VillagerInteractions {
	static final int SELECTION_SECONDS = 30;

	private record Selection(UUID guildId, long expiresAt) {
	}

	/** Player → pending container selection (not persisted). */
	private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();

	private VillagerInteractions() {
	}

	static void init() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) ->
				Safe.get("villager.use_entity", () -> onUseEntity(player, level, hand, entity), InteractionResult.PASS));
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) ->
				Safe.get("villager.use_block", () -> onUseBlock(player, level, hand, hitResult), InteractionResult.PASS));
	}

	// ================================================================ villagers

	private static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(entity instanceof Villager villager) || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		Set<String> tags = villager.entityTags();
		boolean member = tags.contains(VillagerManager.TAG_VILLAGER);
		boolean candidate = tags.contains(VillagerManager.TAG_CANDIDATE);
		if (!member && !candidate) {
			return InteractionResult.PASS;
		}
		VillagerManager.Owner owner = VillagerManager.owner(villager.getUUID());
		if (owner != null) {
			Guild guild = owner.guild();
			boolean isMember = guild.isMember(serverPlayer.getUUID());
			if (!isMember && !ClaimService.hasBypass(serverPlayer)) {
				if (hand == InteractionHand.MAIN_HAND) {
					Msg.actionBar(serverPlayer, "villager.error.not_member", owner.record().name, guild.name);
					WorldUtil.angry((ServerLevel) level, villager);
				}
				return InteractionResult.FAIL;
			}
			if (isMember && serverPlayer.isShiftKeyDown()) {
				if (hand == InteractionHand.MAIN_HAND) {
					if (guild.can(serverPlayer.getUUID(), GuildPermission.VILLAGERS)) {
						VillagerGui.openDetail(serverPlayer, guild, villager.getUUID());
					} else {
						Msg.send(serverPlayer, "error.no_permission");
					}
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		VillagerManager.Candidate pending = null;
		Guild candidateGuild = null;
		for (Guild guild : GuildManager.all()) {
			VillagerManager.Candidate c = VillagerManager.candidate(guild);
			if (c != null && c.entity().equals(villager.getUUID())) {
				pending = c;
				candidateGuild = guild;
				break;
			}
		}
		if (pending == null) {
			// Orphan marker (being released / discarded): leave vanilla behaviour.
			return InteractionResult.PASS;
		}
		if (hand == InteractionHand.MAIN_HAND) {
			if (candidateGuild.isMember(serverPlayer.getUUID())) {
				VillagerGui.openList(serverPlayer, candidateGuild);
			} else {
				Msg.actionBar(serverPlayer, "villager.error.candidate", pending.name(), candidateGuild.name);
			}
		}
		return InteractionResult.SUCCESS;
	}

	// ================================================================ container selection

	static void startSelection(ServerPlayer player, Guild guild) {
		SELECTIONS.put(player.getUUID(), new Selection(guild.id, Scheduler.ticks() + SELECTION_SECONDS * 20L));
		Msg.send(player, "villager.storage.select", SELECTION_SECONDS);
	}

	static void tickSelections() {
		if (SELECTIONS.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Selection>> it = SELECTIONS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Selection> entry = it.next();
			if (Scheduler.ticks() > entry.getValue().expiresAt()) {
				it.remove();
				ServerPlayer player = GuildyServer.player(entry.getKey());
				if (player != null) {
					Msg.send(player, "villager.storage.select_expired");
				}
			}
		}
	}

	static void cancelGuild(Guild guild) {
		SELECTIONS.values().removeIf(selection -> selection.guildId().equals(guild.id));
	}

	static void clear() {
		SELECTIONS.clear();
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.PASS;
		}
		Selection selection = SELECTIONS.get(serverPlayer.getUUID());
		if (selection == null) {
			return InteractionResult.PASS;
		}
		if (Scheduler.ticks() > selection.expiresAt()) {
			SELECTIONS.remove(serverPlayer.getUUID());
			return InteractionResult.PASS;
		}
		BlockPos pos = hitResult.getBlockPos();
		if (!Storage.isStorageBlock(serverLevel.getBlockEntity(pos))) {
			return InteractionResult.PASS;
		}
		Guild guild = GuildManager.get(selection.guildId());
		if (guild == null || GuildManager.of(serverPlayer) != guild) {
			SELECTIONS.remove(serverPlayer.getUUID());
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.FAIL;
		}
		if (GuildManager.at(serverLevel, pos) != guild) {
			Msg.send(serverPlayer, "villager.storage.not_territory");
			return InteractionResult.FAIL;
		}
		SELECTIONS.remove(serverPlayer.getUUID());
		if (!guild.can(serverPlayer.getUUID(), GuildPermission.VILLAGERS)) {
			Msg.send(serverPlayer, "error.no_permission");
			return InteractionResult.FAIL;
		}
		StorageChest existing = Storage.registered(serverLevel, guild, pos);
		BlockPos target = existing != null ? existing.pos : pos.immutable();
		VillagerGui.openRolePicker(serverPlayer, guild, target);
		return InteractionResult.SUCCESS;
	}
}
