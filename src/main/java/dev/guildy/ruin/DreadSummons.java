package dev.guildy.ruin;

import dev.guildy.ruin.DreadAttacks.Context;
import dev.guildy.ruin.DreadAttacks.Running;
import dev.guildy.ruin.RuinConfig.DreadAbilityDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Dread attacks that bring mobs: mirror images of the guardian (mirror_clones: same look, same name, weak, short-lived; the
 * guardian swaps places with one of them) and the Nether rifts of the Avatar (nether_rifts: portals open in the arena and let
 * blazes and wither skeletons out). Every mob is tracked as an escort mob of the fight (tagged, refused at load without it).
 */
final class DreadSummons {
	private static final Identifier MIRROR_SCALE = Identifier.fromNamespaceAndPath("guildy", "ruin_dread_mirror_scale");
	private static final Identifier RIFT_HEALTH = Identifier.fromNamespaceAndPath("guildy", "ruin_avatar_rift_health");
	/** Health and share of the guardian's attack damage of a mirror image. */
	static final double MIRROR_HEALTH = 20;
	static final double MIRROR_DAMAGE_RATIO = 0.5;

	private DreadSummons() {
	}

	// ================================================================== mirror images

	/** Silhouettes of arcane light appear around the guardian, then mirror images stand there and it hides among them. */
	static final class Mirrors extends Running {
		private final List<Vec3> spots = new ArrayList<>();

		private Mirrors(DreadAbilityDef def, String style, int telegraph) {
			super(def, style, telegraph);
		}

		static @Nullable Mirrors cast(DreadAbilityDef def, Context ctx, int telegraph, String style) {
			if (ctx.targets.isEmpty() || ctx.encounter.minionEntities(ctx.level).size() >= ctx.encounter.maxAlive()) {
				return null;
			}
			Mirrors mirrors = new Mirrors(def, style, telegraph);
			String entity = ctx.encounter.def.boss.entity;
			int count = Math.max(1, def.count);
			for (int i = 0; i < count * 3 && mirrors.spots.size() < count; i++) {
				double a = ctx.random.nextDouble() * Math.PI * 2;
				double d = 3 + ctx.random.nextDouble() * 3;
				BlockPos around = BlockPos.containing(ctx.boss.getX() + Math.cos(a) * d, ctx.boss.getY(), ctx.boss.getZ() + Math.sin(a) * d);
				BlockPos spot = BossFactory.standable(ctx.level, around, entity, 1);
				if (spot != null) {
					mirrors.spots.add(Vec3.atBottomCenterOf(spot));
				}
			}
			if (mirrors.spots.isEmpty()) {
				return null;
			}
			DreadAttacks.warn(ctx.targets, "mirror_clones");
			DreadAttacks.sound(ctx.level, ctx.boss.position(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, 2.0F, 0.7F);
			return mirrors;
		}

		@Override
		boolean tick(Context ctx) {
			double height = Math.max(1.0, ctx.boss.getBbHeight());
			if (age < telegraph) {
				if (age % 2 == 0) {
					for (Vec3 at : spots) {
						CursedFx.helix(ctx.fx, RitualFx.dust(theme.primary(), 1.1F), at, 0.5, age, height, telegraph);
						ctx.fx.dot(RitualFx.trail(at.add(0, height * 0.5, 0), theme.secondary(), 10), ctx.core());
					}
					ctx.fx.burst(ParticleTypes.ENCHANT, ctx.core().x, ctx.core().y, ctx.core().z, 6, 0.5, 0.6, 0.5, 0.6);
				}
				return true;
			}
			// The guardian hides among them: it takes the place of one, an image appears where it stood.
			Vec3 old = ctx.boss.position();
			Vec3 swap = spots.get(ctx.random.nextInt(spots.size()));
			List<Vec3> places = new ArrayList<>(spots);
			places.remove(swap);
			places.add(old);
			ctx.fx.burst(ParticleTypes.REVERSE_PORTAL, old.x, old.y + height * 0.5, old.z, 30, 0.4, height * 0.3, 0.4, 0.2);
			ctx.boss.teleportTo(swap.x, swap.y, swap.z);
			ctx.boss.getNavigation().stop();
			long until = ctx.level.getGameTime() + Math.max(60, Math.round(def.durationSeconds * 20));
			for (Vec3 at : places) {
				Mob image = mirror(ctx, at);
				Vec3 c = at.add(0, height * 0.5, 0);
				ctx.fx.burst(RitualFx.dust(theme.primary(), 1.4F), c, 20, 0.5, 0.0);
				ctx.fx.burst(ParticleTypes.ENCHANT, c, 20, 0.5, 0.5);
				if (image != null && ctx.encounter.dreadFight != null) {
					ctx.encounter.dreadFight.fading.put(image.getUUID(), until);
				}
			}
			DreadAttacks.sound(ctx.level, ctx.boss.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 2.0F, 0.7F);
			return false;
		}

		/** A mirror image of the guardian at {@code at} (null when the escort cap is reached). */
		private static @Nullable Mob mirror(Context ctx, Vec3 at) {
			Encounter encounter = ctx.encounter;
			if (encounter.minionEntities(ctx.level).size() >= encounter.maxAlive()) {
				return null;
			}
			Mob image = BossFactory.create(ctx.level, encounter.def.boss.entity, BlockPos.containing(at), encounter.def.boss.variant);
			if (image == null) {
				return null;
			}
			for (EquipmentSlot slot : EquipmentSlot.VALUES) {
				ItemStack stack = ctx.boss.getItemBySlot(slot);
				image.setItemSlot(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
				image.setDropChance(slot, 0.0F);
			}
			setBase(image, Attributes.MAX_HEALTH, MIRROR_HEALTH);
			setBase(image, Attributes.ATTACK_DAMAGE, Math.max(1, ctx.boss.getAttributeValue(Attributes.ATTACK_DAMAGE) * MIRROR_DAMAGE_RATIO));
			AttributeInstance size = image.getAttribute(Attributes.SCALE);
			double scale = ctx.boss.getAttributeValue(Attributes.SCALE);
			if (size != null && scale > 0) {
				size.addOrReplacePermanentModifier(new AttributeModifier(MIRROR_SCALE, scale - size.getBaseValue(), AttributeModifier.Operation.ADD_VALUE));
			}
			image.setHealth(image.getMaxHealth());
			Component name = ctx.boss.getCustomName();
			if (name != null) {
				// The same name: which one is the real guardian?
				image.setCustomName(name.copy());
				image.setCustomNameVisible(true);
			}
			// No summoned vexes nor sheep conversions from the images: only the fangs of an evoker remain.
			image.removeAllGoals(goal -> {
				String type = goal.getClass().getSimpleName();
				return type.contains("Summon") || type.contains("Wololo");
			});
			Mob added = BossFactory.add(ctx.level, image, mob -> {
				Encounters.track(encounter, mob);
				return true;
			});
			if (added == null) {
				Encounters.untrack(encounter, image.getUUID());
				return null;
			}
			if (!ctx.targets.isEmpty()) {
				added.setTarget(ctx.targets.get(ctx.random.nextInt(ctx.targets.size())));
			}
			return added;
		}
	}

	// ================================================================== Nether rifts

	/** Rifts to the Nether tear the arena open (swirls of portal light, crimson spores), then Nether mobs step out. */
	static final class Rifts extends Running {
		private final List<Vec3> rifts = new ArrayList<>();

		private Rifts(DreadAbilityDef def, String style, int telegraph) {
			super(def, style, telegraph * 2);
		}

		static @Nullable Rifts cast(DreadAbilityDef def, Context ctx, int telegraph, String style) {
			AvatarSettings avatar = RuinModule.config().cursed.avatar;
			DreadEncounter fight = ctx.encounter.dreadFight;
			int room = Math.min(ctx.encounter.maxAlive() - ctx.encounter.minionEntities(ctx.level).size(),
					avatar.riftMaxAlive - (fight == null ? 0 : fight.riftAlive(ctx.level)));
			if (ctx.targets.isEmpty() || room <= 0 || avatar.riftMobs.isEmpty()) {
				return null;
			}
			Rifts rifts = new Rifts(def, style, telegraph);
			String probe = avatar.riftMobs.keySet().iterator().next();
			for (int i = 0; i < Math.min(room, Math.max(1, def.count)) * 3 && rifts.rifts.size() < Math.min(room, Math.max(1, def.count)); i++) {
				Vec3 spot = DreadAttacks.arenaSpot(ctx, 3, DreadAttacks.reach(ctx));
				BlockPos fit = BossFactory.standable(ctx.level, BlockPos.containing(spot), probe, 1);
				if (fit != null) {
					rifts.rifts.add(Vec3.atBottomCenterOf(fit));
				}
			}
			if (rifts.rifts.isEmpty()) {
				return null;
			}
			DreadAttacks.warn(ctx.targets, "nether_rifts");
			for (Vec3 at : rifts.rifts) {
				DreadAttacks.sound(ctx.level, at, SoundEvents.PORTAL_TRIGGER, 0.8F, 0.6F);
			}
			DreadAttacks.sound(ctx.level, ctx.boss.position(), SoundEvents.RESPAWN_ANCHOR_CHARGE, 2.0F, 0.5F);
			return rifts;
		}

		@Override
		boolean tick(Context ctx) {
			if (age < telegraph) {
				double k = (double) age / telegraph;
				for (Vec3 at : rifts) {
					if (age % 2 == 0) {
						rift(ctx, at, k);
						CursedFx.warning(ctx.fx, at.add(0, 0.08, 0), 1.6, k, age * 0.1);
					}
				}
				return true;
			}
			int t = age - telegraph;
			if (t == 0) {
				for (Vec3 at : rifts) {
					Mob mob = spawn(ctx, at);
					Vec3 c = at.add(0, 1.2, 0);
					ctx.fx.dot(RitualFx.flash(theme.primary()), c);
					ctx.fx.burst(ParticleTypes.REVERSE_PORTAL, c, 40, 0.5, 0.3);
					ctx.fx.burst(ParticleTypes.CRIMSON_SPORE, c, 20, 0.8, 0.1);
					DreadAttacks.sound(ctx.level, at, SoundEvents.PORTAL_TRAVEL, 0.4F, 1.4F);
					if (mob != null) {
						ctx.fx.burst(ParticleTypes.FLAME, mob.position().add(0, 0.5, 0), 16, 0.4, 0.05);
					}
				}
			}
			if (t < 20 && t % 2 == 0) {
				for (Vec3 at : rifts) {
					rift(ctx, at, 1.0 - t / 20.0);
				}
			}
			return t < 20;
		}

		/** A vertical ellipse of portal light, {@code k} = its size (0..1). */
		private void rift(Context ctx, Vec3 at, double k) {
			double h = 2.6 * Math.max(0.15, k);
			double w = 0.9 * Math.max(0.15, k);
			int points = 18;
			for (int i = 0; i < points; i++) {
				double a = Math.PI * 2 * i / points + age * 0.05;
				double x = Math.cos(a) * w;
				double y = 1.3 + Math.sin(a) * h * 0.5;
				if (!ctx.fx.dot(i % 3 == 0 ? RitualFx.dust(theme.secondary(), 1.2F) : RitualFx.dust(theme.primary(), 1.0F), at.x + x, at.y + y, at.z)) {
					return;
				}
			}
			ctx.fx.burst(ParticleTypes.PORTAL, at.x, at.y + 1.3, at.z, 4, w * 0.6, h * 0.3, 0.1, 0.4);
			ctx.fx.burst(ParticleTypes.CRIMSON_SPORE, at.x, at.y + 1.3, at.z, 2, w, h * 0.4, 0.4, 0.0);
		}

		private static @Nullable Mob spawn(Context ctx, Vec3 at) {
			Encounter encounter = ctx.encounter;
			AvatarSettings avatar = RuinModule.config().cursed.avatar;
			if (encounter.minionEntities(ctx.level).size() >= encounter.maxAlive()) {
				return null;
			}
			String type = pickType(avatar.riftMobs, ctx.random);
			if (type == null) {
				return null;
			}
			Mob mob = BossFactory.create(ctx.level, type, BlockPos.containing(at));
			if (mob == null) {
				return null;
			}
			AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
			if (health != null && avatar.escortHealthMultiplier > 1) {
				health.addOrReplacePermanentModifier(new AttributeModifier(RIFT_HEALTH, avatar.escortHealthMultiplier - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
			if (encounter.cursed != null) {
				encounter.cursed.curseMinion(mob);
			}
			mob.setHealth(mob.getMaxHealth());
			Mob added = BossFactory.add(ctx.level, mob, m -> {
				Encounters.track(encounter, m);
				return true;
			});
			if (added == null) {
				Encounters.untrack(encounter, mob.getUUID());
				return null;
			}
			if (encounter.dreadFight != null) {
				encounter.dreadFight.riftMobs.add(added.getUUID());
			}
			if (!ctx.targets.isEmpty()) {
				added.setTarget(ctx.targets.get(ctx.random.nextInt(ctx.targets.size())));
			}
			return added;
		}
	}

	// ================================================================== helpers

	/** Every tick: the mirror images whose time is up vanish in arcane light. */
	static void tickFading(ServerLevel level, Encounter encounter, Map<UUID, Long> fading, RitualFx fx) {
		if (fading.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		Iterator<Map.Entry<UUID, Long>> it = fading.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Long> entry = it.next();
			Entity entity = level.getEntity(entry.getKey());
			if (!(entity instanceof Mob image) || !image.isAlive()) {
				it.remove();
				Encounters.untrack(encounter, entry.getKey());
				continue;
			}
			if (now >= entry.getValue()) {
				Vec3 c = image.position().add(0, image.getBbHeight() * 0.5, 0);
				fx.burst(ParticleTypes.ENCHANT, c, 24, 0.4, 0.5);
				fx.burst(ParticleTypes.POOF, c, 8, 0.4, 0.03);
				image.discard();
				it.remove();
				Encounters.untrack(encounter, entry.getKey());
			}
		}
	}

	private static @Nullable String pickType(Map<String, Integer> types, SplittableRandom random) {
		int total = 0;
		for (int weight : types.values()) {
			total += Math.max(0, weight);
		}
		if (total <= 0) {
			return null;
		}
		int roll = random.nextInt(total);
		for (Map.Entry<String, Integer> entry : types.entrySet()) {
			roll -= Math.max(0, entry.getValue());
			if (roll < 0) {
				return entry.getKey();
			}
		}
		return null;
	}

	private static void setBase(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}
}
