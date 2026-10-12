package dev.guildy.villager;

import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * No friendly fire on guild villagers, at any time: a member of the guild cannot hurt its villagers (melee hits are
 * cancelled before they land: no knockback, no sweep; sweeps, arrows, tridents and every other projectile or damage
 * whose source is the member are cancelled too, as well as the member's pets). A short action bar hint explains it;
 * a villager is dismissed from its menu. Archer guards' arrows only ever hurt monsters.
 */
final class FriendlyFire {
	private static final long HINT_INTERVAL_TICKS = 40;
	private static final Map<UUID, Long> LAST_HINT = new HashMap<>();

	private FriendlyFire() {
	}

	static void register() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) ->
				Safe.get("villager.friendly_fire.attack", () -> onAttack(player, entity), InteractionResult.PASS));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				Safe.get("villager.friendly_fire.damage", () -> allowDamage(entity, source), true));
	}

	static void clear() {
		LAST_HINT.clear();
	}

	private static InteractionResult onAttack(Player player, Entity entity) {
		if (!(player instanceof ServerPlayer serverPlayer) || player.isSpectator() || !(entity instanceof Villager villager)) {
			return InteractionResult.PASS;
		}
		VillagerRecord record = protectedFrom(villager, serverPlayer.getUUID());
		if (record == null) {
			return InteractionResult.PASS;
		}
		hint(serverPlayer, record);
		return InteractionResult.FAIL;
	}

	/** Cancels the damage of a member (or of a member's pet / projectile) on a villager of its guild, and stray guard arrows. */
	static boolean allowDamage(LivingEntity entity, DamageSource source) {
		Entity direct = source.getDirectEntity();
		if (direct != null && direct.entityTags().contains(Guards.TAG_ARROW) && !Guards.arrowMayHit(entity)) {
			return false;
		}
		if (!(entity instanceof Villager villager) || !villager.entityTags().contains(VillagerManager.TAG_VILLAGER)) {
			return true;
		}
		Entity attacker = source.getEntity();
		if (attacker instanceof ServerPlayer player) {
			VillagerRecord record = protectedFrom(villager, player.getUUID());
			if (record == null) {
				return true;
			}
			if (source.getDirectEntity() != player) {
				// Projectile (direct hits are stopped earlier, sweeps are cancelled silently).
				hint(player, record);
			}
			return false;
		}
		if (attacker instanceof OwnableEntity pet) {
			EntityReference<LivingEntity> owner = pet.getOwnerReference();
			return owner == null || protectedFrom(villager, owner.getUUID()) == null;
		}
		return true;
	}

	/** The villager's record when it belongs to the guild of {@code player}, else null. */
	private static @Nullable VillagerRecord protectedFrom(Villager villager, UUID player) {
		if (!villager.entityTags().contains(VillagerManager.TAG_VILLAGER)) {
			return null;
		}
		VillagerManager.Owner owner = VillagerManager.owner(villager.getUUID());
		return owner != null && owner.guild().isMember(player) ? owner.record() : null;
	}

	private static void hint(ServerPlayer player, VillagerRecord record) {
		long now = Scheduler.ticks();
		Long last = LAST_HINT.get(player.getUUID());
		if (last != null && now >= last && now - last < HINT_INTERVAL_TICKS) {
			return;
		}
		LAST_HINT.put(player.getUUID(), now);
		Msg.actionBar(player, "villager.guard.friendly_fire", record.name);
	}
}
