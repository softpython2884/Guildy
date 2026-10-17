package dev.guildy.ruin;

import dev.guildy.core.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Shadow clones of a cursed guardian (phase 3): weak, temporary copies (same mob, same equipment, never dropped) named
 * "Ombre de ...", tracked as escort mobs of the fight (tagged {@code guildy_ruin}: refused at load after a restart), wrapped
 * in ink and void dust, gone in smoke after {@code cloneSeconds}.
 */
final class CursedClones {
	private static final Identifier CLONE_SCALE = Identifier.fromNamespaceAndPath("guildy", "ruin_cursed_clone_scale");

	private CursedClones() {
	}

	/** A clone of the guardian at {@code pos} (null when the escort cap is reached or it cannot appear). */
	static @Nullable Mob spawn(CursedAttacks.Context ctx, BlockPos pos) {
		Encounter encounter = ctx.encounter;
		ServerLevel level = ctx.level;
		if (encounter.minionEntities(level).size() >= encounter.maxAlive()) {
			return null;
		}
		String entity = encounter.def.boss.entity;
		BlockPos spot = BossFactory.standable(level, pos, entity, 2);
		Mob clone = BossFactory.create(level, entity, spot != null ? spot : pos);
		if (clone == null) {
			return null;
		}
		for (EquipmentSlot slot : EquipmentSlot.VALUES) {
			ItemStack stack = ctx.boss.getItemBySlot(slot);
			clone.setItemSlot(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
			clone.setDropChance(slot, 0.0F);
		}
		CursedSettings.AttackSettings cfg = ctx.cfg();
		setBase(clone, Attributes.MAX_HEALTH, cfg.cloneHealth);
		AttributeInstance damage = clone.getAttribute(Attributes.ATTACK_DAMAGE);
		double bossDamage = ctx.boss.getAttributeValue(Attributes.ATTACK_DAMAGE);
		if (damage != null) {
			damage.setBaseValue(Math.max(1, bossDamage * cfg.cloneDamageRatio));
		}
		double scale = ctx.boss.getAttributeValue(Attributes.SCALE);
		AttributeInstance size = clone.getAttribute(Attributes.SCALE);
		if (size != null && scale > 0) {
			size.addOrReplacePermanentModifier(new AttributeModifier(CLONE_SCALE, Math.max(0.5, scale * 0.85) - 1.0, AttributeModifier.Operation.ADD_VALUE));
		}
		clone.setHealth(clone.getMaxHealth());
		clone.setCustomName(Lang.get("ruin.cursed.clone.name", Lang.colorize(encounter.def.boss.name)));
		clone.setCustomNameVisible(true);
		clone.setSilent(true);
		long until = level.getGameTime() + Math.round(cfg.cloneSeconds * 20);
		Mob added = BossFactory.add(level, clone, mob -> {
			Encounters.track(encounter, mob);
			return true;
		});
		if (added == null) {
			Encounters.untrack(encounter, clone.getUUID());
			return null;
		}
		ctx.cursed.clones.put(added.getUUID(), until);
		if (!ctx.targets.isEmpty()) {
			added.setTarget(ctx.targets.get(ctx.random.nextInt(ctx.targets.size())));
		}
		return added;
	}

	/** Every tick: the clones' shadows; the expired ones vanish in smoke. */
	static void tick(CursedAttacks.Context ctx) {
		Map<UUID, Long> clones = ctx.cursed.clones;
		if (clones.isEmpty()) {
			return;
		}
		long now = ctx.level.getGameTime();
		Iterator<Map.Entry<UUID, Long>> it = clones.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Long> entry = it.next();
			net.minecraft.world.entity.Entity entity = ctx.level.getEntity(entry.getKey());
			if (!(entity instanceof Mob clone) || !clone.isAlive()) {
				it.remove();
				Encounters.untrack(ctx.encounter, entry.getKey());
				continue;
			}
			Vec3 c = clone.position().add(0, clone.getBbHeight() * 0.5, 0);
			if (now >= entry.getValue()) {
				ctx.fx.burst(ParticleTypes.LARGE_SMOKE, c, 20, 0.4, 0.03);
				ctx.fx.burst(ParticleTypes.SQUID_INK, c, 12, 0.4, 0.05);
				clone.discard();
				it.remove();
				Encounters.untrack(ctx.encounter, entry.getKey());
				continue;
			}
			if ((now + entry.getKey().hashCode()) % 3 == 0) {
				ctx.fx.burst(RitualFx.dust(CursedFx.VOID, 1.2F), c.x, c.y, c.z, 2, clone.getBbWidth() * 0.4, clone.getBbHeight() * 0.3,
						clone.getBbWidth() * 0.4, 0.0);
				ctx.fx.move(ParticleTypes.SQUID_INK, c.x, clone.getY() + 0.1, c.z, 0, 0.03, 0);
			}
		}
	}

	private static void setBase(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}
}
