package dev.guildy.villager;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import dev.guildy.upgrade.UpgradeEffects;
import dev.guildy.upgrade.Upgrades;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Guards: roles (épéiste / archer, balanced in "Auto"), equipment taken from the guild storage, retreat to heal, the
 * fine-grained combat ticker and the protection of guild villagers against their own guild. Entry point of the
 * guard features ({@link #init()} from {@link VillagerModule}) and the hooks called by the villager lifecycle.
 */
final class Guards {
	/** Entity tag of the arrows shot by guards (kept when an arrow is saved / reloaded). */
	static final String TAG_ARROW = "guildy_guard_arrow";

	private Guards() {
	}

	static void init() {
		GuardLang.register();
		FriendlyFire.register();
		GuardCombat.register();
		Lifecycle.STOPPING.add(server -> FriendlyFire.clear());
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!blocked && entity instanceof Villager villager && villager.entityTags().contains(VillagerManager.TAG_VILLAGER)) {
				Safe.run("villager.guard.wear", () -> {
					if (VillagerModule.config().guard.armorWear) {
						GuardEquipment.wear(villager, source, taken);
					}
				});
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> Safe.run("villager.guard.kill", () -> onKill(source)));
	}

	// ================================================================ roles

	static GuardData data(VillagerRecord record) {
		if (record.guard == null) {
			record.guard = new GuardData();
		}
		return record.guard;
	}

	/** Effective role of a guard, resolved (and saved) the first time it is needed. */
	static GuardRole role(Guild guild, VillagerRecord record) {
		GuardData data = data(record);
		if (data.role == null) {
			data.role = data.autoRole ? balancedRole(guild, record) : GuardRole.SWORD;
			GuildManager.markDirty();
		}
		return data.role;
	}

	/** Role keeping the guild's guards balanced (archerShare), {@code self} not counted. */
	static GuardRole balancedRole(Guild guild, VillagerRecord self) {
		int[] counts = counts(guild, self);
		return GuardRules.pickRole(counts[0], counts[1], VillagerModule.config().guard.archerShare);
	}

	/** {swords, bows} among the guild's guards whose role is known ({@code except} not counted). */
	static int[] counts(Guild guild, @Nullable VillagerRecord except) {
		int swords = 0;
		int bows = 0;
		for (VillagerRecord other : guild.villagers.members.values()) {
			if (other == except || other.task != Task.GUARD || other.guard == null || other.guard.role == null) {
				continue;
			}
			if (other.guard.role == GuardRole.BOW) {
				bows++;
			} else {
				swords++;
			}
		}
		return new int[]{swords, bows};
	}

	/** Menu: Auto → Épéiste → Archer → Auto. A real weapon of the old role goes to the player. */
	static void cycleRole(ServerPlayer actor, Guild guild, VillagerRecord record) {
		GuardData data = data(record);
		GuardRules.Setting next = GuardRules.nextSetting(data.autoRole, data.role);
		data.autoRole = next.auto();
		data.role = next.forced() != null ? next.forced() : balancedRole(guild, record);
		GuildManager.markDirty();
		Villager villager = loaded(record);
		if (villager != null) {
			ItemStack weapon = villager.getItemBySlot(GuardEquipment.WEAPON);
			if (!weapon.isEmpty() && !GuardEquipment.fitsRole(weapon, data.role)) {
				villager.setItemSlot(GuardEquipment.WEAPON, ItemStack.EMPTY);
				Msg.send(actor, "villager.guard.weapon_returned", record.name);
				ItemUtil.giveOrDrop(actor, weapon.copy());
			}
			GuardEquipment.applyDisplay(villager, data.role);
		}
		Msg.send(actor, "villager.guard.role.set", record.name, roleName(record));
	}

	/** "Archer", "Épéiste (auto)"... */
	static String roleName(VillagerRecord record) {
		GuardData data = record.guard;
		GuardRole role = data == null || data.role == null ? GuardRole.SWORD : data.role;
		String name = Lang.raw(role.langKey());
		return data == null || data.autoRole ? Lang.raw("villager.guard.role.auto", name) : name;
	}

	// ================================================================ combat helpers

	/** Damage of a guard hit (sword) before the weapon bonus: config + guild level, boosted by the upgrade tree. */
	static double baseDamage(Guild guild) {
		return Upgrades.boost(guild, UpgradeEffects.GUARD_DAMAGE_PCT, VillagerModule.config().guardDamage(guild.level));
	}

	/** Guard arrows only touch living monsters (not ignored by archers): never players, villagers, pets or animals. */
	static boolean arrowMayHit(Entity entity) {
		return entity instanceof Enemy && entity instanceof LivingEntity living && living.isAlive() && !ignoredByArchers(entity);
	}

	static boolean ignoredByArchers(Entity entity) {
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
		return VillagerModule.config().guard.bowIgnoredMobs.contains(id);
	}

	/** Kill statistic of guards (melee hits and arrows: the damage source's entity is the villager). */
	private static void onKill(DamageSource source) {
		if (!(source.getEntity() instanceof Villager villager) || !villager.entityTags().contains(VillagerManager.TAG_VILLAGER)) {
			return;
		}
		VillagerManager.Owner owner = VillagerManager.owner(villager.getUUID());
		if (owner != null) {
			owner.record().stats.kills++;
			GuildManager.markDirty();
		}
	}

	// ================================================================ lifecycle hooks

	static @Nullable Villager loaded(VillagerRecord record) {
		ServerLevel level = GuildyServer.claimLevel();
		return level == null ? null : VillagerManager.entity(level, record.uuid);
	}

	/**
	 * Task changed from the menus: a new guard gets its role now (balanced among the guards); a guard taking another
	 * task brings its equipment back to the storage (what does not fit goes to {@code actor}). An unloaded villager
	 * does it when its chunk loads ({@link #checkNotGuard}).
	 */
	static void onTaskChange(Guild guild, VillagerRecord record, Task previous, Task task, @Nullable ServerPlayer actor) {
		if (task == Task.GUARD) {
			role(guild, record);
			return;
		}
		if (previous != Task.GUARD) {
			return;
		}
		GuardData data = record.guard;
		if (data != null && data.autoRole) {
			// Balanced again the next time it becomes a guard.
			data.role = null;
		}
		ServerLevel level = GuildyServer.claimLevel();
		Villager villager = loaded(record);
		if (level == null || villager == null || !GuardEquipment.hasEquipment(villager)) {
			return;
		}
		GuardEquipment.Returned returned = GuardEquipment.returnAll(level, guild, villager, actor);
		if (actor != null && returned.total() > 0) {
			Msg.send(actor, returned.given() > 0 ? "villager.guard.equipment.given" : "villager.guard.equipment.stored",
					record.name, returned.given());
		}
	}

	/** Dismissal: the equipment goes to the player who dismissed the villager. */
	static void onDismiss(ServerPlayer player, VillagerRecord record) {
		Villager villager = loaded(record);
		if (villager != null && GuardEquipment.hasEquipment(villager)) {
			int given = GuardEquipment.giveAll(player, villager);
			Msg.send(player, "villager.guard.equipment.given", record.name, given);
		}
	}

	/** The villager leaves the guild (defeat, dismissed while unloaded...): equipment back to the storage, else dropped. */
	static void onLeave(ServerLevel level, Guild guild, Villager villager) {
		if (GuardEquipment.hasEquipment(villager)) {
			GuardEquipment.returnAll(level, guild, villager, null);
		}
	}

	/** Released (guild disbanded): the equipment falls at its feet, the display weapon disappears. */
	static void onRelease(ServerLevel level, Villager villager) {
		GuardEquipment.dropAll(level, villager);
	}

	/**
	 * Every step of a villager that is not a guard (task changed while unloaded): equipment still on it goes back to
	 * the storage (else to the ground), with a message to the guild.
	 */
	static void checkNotGuard(ServerLevel level, Guild guild, VillagerRecord record, Villager villager) {
		if (!GuardEquipment.hasEquipment(villager)) {
			return;
		}
		GuardEquipment.Returned returned = GuardEquipment.returnAll(level, guild, villager, null);
		if (returned.dropped() > 0) {
			Msg.guild(guild, "villager.guard.equipment.dropped", record.name);
		} else if (returned.stored() > 0) {
			Msg.guild(guild, "villager.guard.equipment.stored", record.name);
		}
	}

	/** Item shown in the hand of the role (menus). */
	static ItemStack roleIcon(GuardRole role) {
		return GuardEquipment.displayFor(role, ItemStack.EMPTY);
	}

	/** Armor slot names used in the menus. */
	static String slotKey(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> "villager.gui.guard.slot.head";
			case CHEST -> "villager.gui.guard.slot.chest";
			case LEGS -> "villager.gui.guard.slot.legs";
			case FEET -> "villager.gui.guard.slot.feet";
			default -> "villager.gui.guard.slot.weapon";
		};
	}
}
