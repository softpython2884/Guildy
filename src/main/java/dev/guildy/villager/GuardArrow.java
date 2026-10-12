package dev.guildy.villager;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Arrow shot by an archer guard: a real vanilla arrow for clients (same entity type), owned by the villager, that can
 * never be picked up. Server side it flies through everything but monsters (players, villagers, pets and animals in
 * the line of fire are not even touched) and lands exactly the configured damage (+ Power of the bow) on impact.
 * A Flame bow sets the monster it hits on fire, but the arrow itself never burns (no TNT or campfire lit in the base).
 * <p>
 * Saved and reloaded as a plain arrow (a chunk unloaded mid-flight): its {@link Guards#TAG_ARROW} tag stays, and
 * {@link FriendlyFire} then cancels any damage on something else than a monster.
 */
final class GuardArrow extends Arrow {
	/** Initial speed of a guard shot (blocks per tick, like a skeleton). */
	static final float SPEED = 1.6f;
	private static final float FLAME_SECONDS = 5.0f;

	private final double wantedDamage;
	private final boolean flame;

	GuardArrow(Level level, LivingEntity owner, ItemStack bow, double damage, boolean flame) {
		super(level, owner, new ItemStack(Items.ARROW), bow);
		this.wantedDamage = Math.max(0.0, damage);
		this.flame = flame;
		this.pickup = AbstractArrow.Pickup.DISALLOWED;
		this.setBaseDamage(GuardRules.arrowBaseDamage(wantedDamage, SPEED));
		this.addTag(Guards.TAG_ARROW);
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return super.canHitEntity(entity) && Guards.arrowMayHit(entity);
	}

	@Override
	protected void onHitEntity(EntityHitResult hitResult) {
		// Vanilla damage = impact speed x base damage: keep the configured damage whatever the distance flown.
		this.setBaseDamage(GuardRules.arrowBaseDamage(wantedDamage, this.getDeltaMovement().length()));
		Entity entity = hitResult.getEntity();
		if (flame && Guards.arrowMayHit(entity) && !entity.fireImmune()) {
			entity.igniteForSeconds(FLAME_SECONDS);
		}
		super.onHitEntity(hitResult);
	}
}
