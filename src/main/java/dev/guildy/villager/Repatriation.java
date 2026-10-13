package dev.guildy.villager;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.GuildManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A guild villager found in another dimension (it went through a portal before portals were closed to guild villagers)
 * is sent back to the claim level as soon as its entity loads: next to its last known position, else the reception or
 * the heart.
 */
final class Repatriation {
	private Repatriation() {
	}

	static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> Safe.run("villager.repatriate", () -> {
			if (entity instanceof Villager villager && villager.entityTags().contains(VillagerManager.TAG_VILLAGER)
					&& !GuildyServer.isClaimLevel(level)) {
				// Not during the load itself: the entity is teleported on the next tick.
				Scheduler.later("villager.repatriate", 1, () -> repatriate(villager));
			}
		}));
	}

	/** Brings a guild villager standing in another dimension back home. @return the entity in the claim level, or null */
	static @Nullable Villager repatriate(Villager villager) {
		ServerLevel home = GuildyServer.claimLevel();
		if (home == null || villager.isRemoved() || villager.level() == home) {
			return null;
		}
		VillagerManager.Owner owner = VillagerManager.owner(villager.getUUID());
		if (owner == null || owner.record().isAway()) {
			return null;
		}
		BlockPos spot = destination(home, owner);
		if (spot == null) {
			return null;
		}
		if (villager.isPassenger()) {
			villager.stopRiding();
		}
		if (villager.isSleeping()) {
			villager.stopSleeping();
		}
		Entity moved = villager.teleport(new TeleportTransition(home, Vec3.atBottomCenterOf(spot), Vec3.ZERO, villager.getYRot(), 0.0f,
				TeleportTransition.DO_NOTHING));
		if (!(moved instanceof Villager back)) {
			return null;
		}
		owner.record().lastPos = back.blockPosition();
		TaskEngine.resetTask(owner.record().uuid);
		GuildManager.markDirty();
		WorldUtil.portal(home, back.getX(), back.getY() + 1.0, back.getZ());
		Msg.guild(owner.guild(), "villager.repatriated", owner.record().name);
		return back;
	}

	private static @Nullable BlockPos destination(ServerLevel home, VillagerManager.Owner owner) {
		BlockPos anchor = owner.record().lastPos != null ? owner.record().lastPos : owner.guild().heart;
		if (anchor == null) {
			return null;
		}
		home.getChunk(anchor.getX() >> 4, anchor.getZ() >> 4); // load the destination for the safety check
		BlockPos spot = WorldUtil.findSafeSpot(home, anchor, 4, true, pos -> true);
		if (spot == null && owner.guild().heart != null) {
			spot = WorldUtil.findSafeSpot(home, owner.guild().heart, 4, true, pos -> true);
		}
		return spot != null ? spot : anchor.above();
	}
}
