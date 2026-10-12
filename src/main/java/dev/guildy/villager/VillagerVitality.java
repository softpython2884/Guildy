package dev.guildy.villager;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Lifecycle;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildEvents;
import dev.guildy.data.GuildManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Health of the guild villagers ({@code vitality} in villagers.json5): extra max health from the guild level (a
 * permanent attribute modifier with a fixed id, re-applied at each step, on level-up and after a reload; guards get more)
 * and the slow regeneration out of combat of every guild villager (guards: {@code guard.passiveRegenPerSecond}). The
 * retreat healing of the wounded guards stays in {@link GuardTask}.
 */
final class VillagerVitality {
	/** Id of the max health modifier (removed when the villager is released). */
	static final Identifier HEALTH_ID = Identifier.fromNamespaceAndPath("guildy", "guild_vitality");
	private static final double EPSILON = 1e-6;
	/** Last time each guild villager was hurt or fought (scheduler ticks; transient). */
	private static final Map<UUID, Long> LAST_COMBAT = new HashMap<>();

	private VillagerVitality() {
	}

	static void init() {
		VitalityLang.register();
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof Villager villager && villager.entityTags().contains(VillagerManager.TAG_VILLAGER)) {
				markCombat(villager.getUUID(), Scheduler.ticks());
			}
		});
		GuildEvents.LEVEL_UP.add((guild, level) -> refresh(guild, true));
		Lifecycle.RELOADED.add(() -> {
			for (Guild guild : GuildManager.all()) {
				Safe.run("villager.vitality.reload", () -> refresh(guild, false));
			}
		});
		Lifecycle.STARTED.add(server -> LAST_COMBAT.clear());
		Lifecycle.STOPPING.add(server -> LAST_COMBAT.clear());
	}

	/** Hurt or fighting now: the slow regeneration waits {@code vitality.outOfCombatSeconds}. */
	static void markCombat(UUID villager, long now) {
		LAST_COMBAT.put(villager, now);
	}

	/** Each processing step of a guild villager (loaded): max health of its guild level, then the slow regeneration. */
	static void step(WorkContext ctx) {
		apply(ctx.villager, ctx.guild, ctx.record, false);
		regenerate(ctx);
	}

	/** Applies the max health of the guild level to the loaded villagers of a guild (level-up: the gain is healed). */
	static void refresh(Guild guild, boolean levelUp) {
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			return;
		}
		for (VillagerRecord record : List.copyOf(guild.villagers.members.values())) {
			Villager villager = record.isAway() ? null : VillagerManager.entity(level, record.uuid);
			if (villager != null && villager.isAlive()) {
				apply(villager, guild, record, levelUp);
			}
		}
	}

	/**
	 * Sets the extra max health of the villager (idempotent). A villager at full health stays at full health; on a
	 * level-up ({@code healGain}) the gained health is added; a lower maximum (task changed, config) caps the health.
	 */
	static void apply(Villager villager, Guild guild, VillagerRecord record, boolean healGain) {
		AttributeInstance instance = villager.getAttribute(Attributes.MAX_HEALTH);
		if (instance == null) {
			return;
		}
		double bonus = VillagerModule.config().vitality.bonusHealth(guild.level, record.task == Task.GUARD);
		AttributeModifier current = instance.getModifier(HEALTH_ID);
		double currentAmount = current == null ? 0.0 : current.amount();
		boolean upToDate = current == null ? bonus <= EPSILON
				: Math.abs(currentAmount - bonus) <= EPSILON && current.operation() == AttributeModifier.Operation.ADD_VALUE;
		if (upToDate) {
			return;
		}
		float oldMax = villager.getMaxHealth();
		float health = villager.getHealth();
		if (bonus <= EPSILON) {
			instance.removeModifier(HEALTH_ID);
		} else {
			instance.addOrReplacePermanentModifier(new AttributeModifier(HEALTH_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
		}
		float newMax = villager.getMaxHealth();
		float wanted = (float) VitalityRules.healthAfterChange(health, oldMax, newMax, healGain);
		if (villager.isAlive() && Math.abs(wanted - villager.getHealth()) > EPSILON) {
			villager.setHealth(wanted);
		}
	}

	/** Released villager (guild gone): back to the vanilla max health. */
	static void reset(Villager villager) {
		AttributeInstance instance = villager.getAttribute(Attributes.MAX_HEALTH);
		if (instance != null && instance.getModifier(HEALTH_ID) != null) {
			instance.removeModifier(HEALTH_ID);
			if (villager.getHealth() > villager.getMaxHealth()) {
				villager.setHealth(villager.getMaxHealth());
			}
		}
		LAST_COMBAT.remove(villager.getUUID());
	}

	/** Slow regeneration once neither hurt nor fighting for {@code vitality.outOfCombatSeconds}. */
	private static void regenerate(WorkContext ctx) {
		Villager villager = ctx.villager;
		if (!villager.isAlive() || villager.getHealth() >= villager.getMaxHealth()) {
			return;
		}
		VillagerConfig config = ctx.config;
		double rate = ctx.record.task == Task.GUARD ? config.guard.passiveRegenPerSecond : config.vitality.regenPerSecond;
		if (rate <= 0) {
			return;
		}
		Long last = LAST_COMBAT.get(ctx.record.uuid);
		if (last != null && !VitalityRules.outOfCombat(ctx.now, last, WorkContext.ticks(config.vitality.outOfCombatSeconds))) {
			return;
		}
		villager.heal((float) rate);
	}

	/** Health line of a villager in the menus (unloaded: nothing). */
	static void addLore(List<Component> lore, @Nullable Villager villager) {
		if (villager != null) {
			lore.add(Lang.get("villager.gui.lore.health", VitalityRules.display(villager.getHealth()), VitalityRules.display(villager.getMaxHealth())));
		}
	}
}
