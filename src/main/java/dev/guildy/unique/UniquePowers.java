package dev.guildy.unique;

import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Event wiring of the Java powers (each handler isolated by {@link Safe}): right clicks (active powers, vanilla cooldown
 * overlay on the item), hits and kills (passive powers with per-player cooldowns), block breaks (mining powers), the
 * double jump tick, and the damage rule of the guildy proc damage types.
 */
final class UniquePowers {
	private UniquePowers() {
	}

	static void register() {
		UseItemCallback.EVENT.register(UniquePowers::onUse);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				Safe.get("unique.allow_damage", () -> allowDamage(entity, source), true));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			Safe.run("unique.hit", () -> onHit(entity, source, false));
			if (entity instanceof ServerPlayer player) {
				Safe.run("unique.defense", () -> DefensePowers.onDamaged(player, source, base, taken, blocked));
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) ->
				Safe.run("unique.kill", () -> onHit(entity, source, true)));
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) ->
				Safe.run("unique.break", () -> afterBreak(level, player, pos, state, blockEntity)));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Safe.run("unique.double_jump", DoubleJump::tick);
			Safe.run("unique.leaps", Leaps::tick);
			Safe.run("unique.seals", CursePowers::tick);
			Safe.run("unique.crown", DefensePowers::tick);
			Safe.run("unique.fx", UniqueFx::tick);
		});
	}

	// ================================================================== right click

	private static InteractionResult onUse(Player player, Level level, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		return Safe.get("unique.use", () -> use(serverPlayer), InteractionResult.PASS);
	}

	private static InteractionResult use(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();
		UniquePower power = UniqueItems.powerOf(stack);
		if (power == null || !power.active) {
			return InteractionResult.PASS;
		}
		double cooldown = UniqueModule.config().cooldownSeconds(power);
		if (player.getCooldowns().isOnCooldown(stack)) {
			double left = Math.max(0.1, player.getCooldowns().getCooldownPercent(stack, 0.0F) * cooldown);
			Msg.actionBar(player, "unique.cooldown", stack.getHoverName().getString(), UniqueItems.number(Math.ceil(left * 10) / 10));
			return InteractionResult.PASS;
		}
		boolean fired = switch (power) {
			case STORM_CALL -> LightningPowers.stormCall(player);
			case FROST_NOVA -> CombatPowers.frostNova(player);
			case BLINK_STRIKE -> CombatPowers.blinkStrike(player);
			case JAGUAR_POUNCE -> JaguarPowers.pounce(player);
			case SOLAR_LEAP -> SolarPowers.leap(player);
			case INFERNAL_RIFT -> InfernalPowers.rift(player);
			default -> false;
		};
		if (!fired) {
			return InteractionResult.PASS;
		}
		if (cooldown > 0) {
			player.getCooldowns().addCooldown(stack, (int) Math.round(cooldown * 20));
		}
		KineticWeapon kinetic = stack.get(DataComponents.KINETIC_WEAPON);
		if (kinetic != null) {
			// A spear: the pounce is the start of its charge, which the vanilla use would now refuse (cooldown just set).
			if (!player.isUsingItem()) {
				player.startUsingItem(InteractionHand.MAIN_HAND);
				kinetic.makeSound(player);
			}
			return InteractionResult.CONSUME;
		}
		return InteractionResult.SUCCESS;
	}

	// ================================================================== damage

	/** guildy proc damage: lands even right after the hit that triggered it; fire / cold immune mobs are spared. */
	static boolean allowDamage(LivingEntity entity, DamageSource source) {
		Holder<DamageType> type = source.typeHolder();
		if (type.is(PowerUtil.BRASIER) && entity.fireImmune()) {
			return false;
		}
		if (type.is(PowerUtil.GIVRE) && !entity.canFreeze()) {
			return false;
		}
		if (type.is(PowerUtil.FOUDRE) || type.is(PowerUtil.BRASIER) || type.is(PowerUtil.GIVRE)) {
			entity.damageCooldownTime = 0;
		}
		// No fall damage during a pounce or a solar leap.
		return !Leaps.cancelsFall(entity, source);
	}

	/** A hit by a player (killed or not): shockwave (mace smash), phoenix arrows, tide call; kills: soul harvest. */
	private static void onHit(LivingEntity victim, DamageSource source, boolean killed) {
		if (PowerUtil.dealing() || !(source.getEntity() instanceof ServerPlayer player) || victim == player) {
			return;
		}
		Entity direct = source.getDirectEntity();
		if (direct == player) {
			ItemStack weapon = player.getMainHandItem();
			UniquePower power = UniqueItems.powerOf(weapon);
			if (power == UniquePower.SHOCKWAVE && source.is(DamageTypes.MACE_SMASH)) {
				CombatPowers.shockwave(player, victim);
			}
			if (power == UniquePower.SOLAR_LEAP && source.is(DamageTypes.MACE_SMASH)) {
				SolarPowers.smash(player, victim);
			}
			if (!killed && power == UniquePower.PACT_SEAL && source.is(DamageTypes.PLAYER_ATTACK)) {
				CursePowers.hit(player, victim);
			}
			if (killed && power == UniquePower.SOUL_HARVEST && PowerUtil.isHostileType(victim)) {
				CombatPowers.soulHarvest(player, victim);
			}
			return;
		}
		if (direct instanceof ThrownTrident trident) {
			if (UniqueItems.powerOf(trident.getWeaponItem()) == UniquePower.TIDE_CALL) {
				LightningPowers.tideCall(player, victim);
			}
			return;
		}
		if (direct instanceof AbstractArrow arrow) {
			ItemStack bow = arrow.getWeaponItem();
			if (bow != null && UniqueItems.powerOf(bow) == UniquePower.PHOENIX_ARROWS) {
				CombatPowers.phoenixBurst(player, victim);
			}
		}
	}

	// ================================================================== blocks

	private static void afterBreak(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		if (MiningPowers.busy() || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		ItemStack tool = serverPlayer.getMainHandItem();
		String id = UniqueItems.idOf(tool);
		UniquePower power = id == null ? null : UniqueItems.powerOf(tool);
		if (power == null) {
			return;
		}
		switch (power) {
			case TREE_FELLER -> MiningPowers.fellTree(serverPlayer, serverLevel, pos, state, id);
			case EXCAVATE -> MiningPowers.excavate(serverPlayer, serverLevel, pos, state, id);
			case VEIN_MINER -> MiningPowers.veinMine(serverPlayer, serverLevel, pos, state, id);
			default -> {
			}
		}
	}
}
