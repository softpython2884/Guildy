package dev.guildy.scroll;

import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Msg;
import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import dev.guildy.scroll.mixin.LivingEntityAccessor;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Casting a scroll, or the selected page of a grimoire. Right click starts the vanilla channel animation of the item's
 * consumable component (after the checks: config, dimension, cooldown, effect limits, spell conditions, food for a
 * grimoire; a refused start also stops the animation the client predicted). While channeling, a runic circle draws itself
 * under the caster. When the vanilla use completes, {@code ItemStack.finishUsingItem} is intercepted (mixin) and
 * {@link #finish} casts the spell: on success exactly one scroll is consumed (never in creative) and the cooldown of its
 * group starts, or the grimoire costs its food and starts its own cooldown; on failure nothing is spent.
 *
 * <p>When the server completes a use, the vanilla client completes it too and predicts the consumable's effects (one item
 * eaten, the item's use_cooldown): whenever the server does not consume the item, the hand and the cooldowns are sent back.
 */
public final class ScrollCasting {
	private static final Map<UUID, Channel> CHANNELS = new HashMap<>();

	/** A started incantation: what is cast (scroll id or "grimoire/" + id), hand, start, length. */
	private record Channel(String key, InteractionHand hand, long start, int ticks) {
	}

	private ScrollCasting() {
	}

	static void register() {
		UseItemCallback.EVENT.register(ScrollCasting::onUse);
		Scheduler.every("scroll.channel", 1, 0, ScrollCasting::tickChannels);
	}

	// ================================================================== start

	private static InteractionResult onUse(Player player, Level level, InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (ScrollItems.isScroll(stack)) {
			return Safe.get("scroll.use", () -> start(serverPlayer, hand), InteractionResult.FAIL);
		}
		if (GrimoireItems.isGrimoire(stack)) {
			return Safe.get("scroll.grimoire.use", () -> Grimoires.use(serverPlayer, hand), InteractionResult.FAIL);
		}
		return InteractionResult.PASS;
	}

	/** Starts the incantation of the scroll in {@code hand} (or explains why not). */
	static InteractionResult start(ServerPlayer player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		ScrollConfig.ScrollDef def = ScrollItems.defOf(stack);
		if (def == null) {
			return refuse(player, "scroll.fail.unknown");
		}
		ItemStack fresh = ScrollItems.refreshed(stack);
		if (fresh != null) {
			// Incomplete (loot table) or outdated scroll: rebuilt before use.
			player.setItemInHand(hand, fresh);
			stack = fresh;
		}
		return begin(player, hand, stack, def, false);
	}

	/**
	 * Starts the incantation of {@code def} with the (up to date) item in {@code hand}: a scroll, or a grimoire whose selected
	 * page holds {@code def}.
	 */
	static InteractionResult begin(ServerPlayer player, InteractionHand hand, ItemStack stack, ScrollConfig.ScrollDef def, boolean grimoire) {
		if (player.isUsingItem()) {
			return InteractionResult.PASS;
		}
		ScrollConfig cfg = ScrollModule.config();
		if (player.getCooldowns().isOnCooldown(stack)) {
			double total = grimoire ? cfg.grimoire.cooldownSeconds : def.cooldownSeconds;
			double left = player.getCooldowns().getCooldownPercent(stack, 0.0F) * total;
			return refuse(player, grimoire ? "grimoire.fail.cooldown" : "scroll.fail.cooldown",
					Spell.number(Math.max(0.1, Math.ceil(left * 10) / 10)));
		}
		String problem = precheck(player, def);
		if (problem != null) {
			return refuse(player, grimoire ? grimoireKey(problem) : problem);
		}
		if (grimoire && !affords(player, def)) {
			return refuse(player, "grimoire.fail.hungry", GrimoireItems.foodCost(def), player.getFoodData().getFoodLevel());
		}
		player.startUsingItem(hand);
		if (!player.isUsingItem()) {
			return refuse(player, "scroll.fail.unknown");
		}
		String key = grimoire ? GrimoireItems.channelKey(stack) : def.id;
		int ticks = grimoire ? cfg.grimoire.channelTicks() : def.channelTicks();
		CHANNELS.put(player.getUUID(), new Channel(key == null ? def.id : key, hand, Scheduler.ticks(), ticks));
		Vec3 pos = player.position();
		player.level().playSound(null, pos.x, pos.y, pos.z, def.spell().offensive ? SoundEvents.EVOKER_PREPARE_ATTACK : SoundEvents.EVOKER_PREPARE_WOLOLO,
				SoundSource.PLAYERS, grimoire ? 0.7F : 1.0F, grimoire ? 1.3F : 1.0F);
		player.level().playSound(null, pos.x, pos.y, pos.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
		return InteractionResult.CONSUME;
	}

	/** Conditions shared by the start and the completion. @return lang key of the problem, or null */
	private static @Nullable String precheck(ServerPlayer player, ScrollConfig.ScrollDef def) {
		ScrollConfig cfg = ScrollModule.config();
		if (!cfg.enabled) {
			return "scroll.fail.disabled";
		}
		if (cfg.dimensionDisabled(player.level().dimension().identifier().toString())) {
			return "scroll.fail.dimension";
		}
		if (EffectRunner.count(player.getUUID()) >= cfg.limits.maxEffectsPerPlayer) {
			return "scroll.fail.limit_player";
		}
		if (EffectRunner.count() >= cfg.limits.maxEffectsServer) {
			return "scroll.fail.limit_server";
		}
		return Spells.check(new SpellContext(player, def));
	}

	/** The grimoire's own wording of a scroll message when it has one ("scroll.fail.x" -> "grimoire.fail.x"). */
	private static String grimoireKey(String key) {
		if (key.startsWith("scroll.")) {
			String own = "grimoire." + key.substring("scroll.".length());
			if (Lang.has(own)) {
				return own;
			}
		}
		return key;
	}

	/** Enough food for a grimoire cast of this spell (always in creative). */
	private static boolean affords(ServerPlayer player, ScrollConfig.ScrollDef def) {
		return player.hasInfiniteMaterials() || GrimoireRules.canAfford(player.getFoodData().getFoodLevel(), GrimoireItems.foodCost(def));
	}

	/**
	 * A refused start: action bar message, sound, and the "not using" state sent back to the client (it already started the
	 * channel animation of the consumable on its side).
	 */
	static InteractionResult refuse(ServerPlayer player, String key, Object... args) {
		Msg.actionBar(player, key, args);
		Msg.sound(player, SoundEvents.VILLAGER_NO, 0.6F, 1.2F);
		syncUsing(player);
		return InteractionResult.FAIL;
	}

	/** Sends the player's real "using an item" flags to their client (stops a predicted use animation). */
	static void syncUsing(ServerPlayer player) {
		EntityDataAccessor<Byte> flags = LivingEntityAccessor.guildy$livingFlags();
		List<SynchedEntityData.DataValue<?>> values = new ArrayList<>();
		values.add(SynchedEntityData.DataValue.create(flags, player.getEntityData().get(flags)));
		player.connection.send(new ClientboundSetEntityDataPacket(player.getId(), values));
	}

	// ================================================================== completion

	/**
	 * The vanilla use of a scroll or grimoire completed ({@code ItemStack.finishUsingItem}, server side): casts the spell.
	 * Never throws.
	 *
	 * @return the stack left in hand (one scroll less after a successful cast, outside creative; the grimoire unchanged)
	 */
	public static ItemStack finish(ItemStack stack, Level level, LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel)) {
			return stack;
		}
		return Safe.get("scroll.cast", () -> cast(player, stack), stack);
	}

	private static ItemStack cast(ServerPlayer player, ItemStack stack) {
		Channel channel = CHANNELS.remove(player.getUUID());
		boolean grimoire = GrimoireItems.isGrimoire(stack);
		ScrollConfig.ScrollDef def = grimoire ? GrimoireItems.selectedDef(GrimoireItems.pages(stack)) : ScrollItems.defOf(stack);
		String key = grimoire ? GrimoireItems.channelKey(stack) : ScrollItems.idOf(stack);
		if (def == null || channel == null || !channel.key().equals(key)) {
			Msg.actionBar(player, grimoire ? "grimoire.fail.interrupted" : "scroll.fail.interrupted");
			undoPrediction(player, stack, true);
			return stack;
		}
		ScrollConfig cfg = ScrollModule.config();
		String problem = precheck(player, def);
		if (problem == null && grimoire && !cfg.grimoire.enabled) {
			problem = "grimoire.fail.disabled";
		}
		if (problem == null && grimoire && !affords(player, def)) {
			problem = "grimoire.fail.hungry_now";
		}
		if (problem == null) {
			problem = Spells.cast(new SpellContext(player, def, grimoire ? cfg.grimoire.powerMultiplier : 1.0));
		}
		if (problem != null) {
			Msg.actionBar(player, grimoire ? grimoireKey(problem) : problem);
			Msg.sound(player, SoundEvents.VILLAGER_NO, 0.6F, 1.2F);
			fizzle(player);
			undoPrediction(player, stack, true);
			return stack;
		}
		Identifier group = grimoire ? GrimoireItems.COOLDOWN_GROUP : ScrollItems.cooldownGroup(def);
		if (!player.hasInfiniteMaterials() || cfg.casting.creativeCooldown) {
			double seconds = grimoire ? cfg.grimoire.cooldownSeconds : def.cooldownSeconds;
			player.getCooldowns().addCooldown(group, (int) Math.max(1, Math.round(seconds * 20)));
		} else {
			undoPrediction(player, stack, false);
		}
		player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
		castFlash(player, def.spell());
		if (grimoire) {
			int cost = player.hasInfiniteMaterials() ? 0 : GrimoireItems.foodCost(def);
			eat(player, cost);
			Msg.actionBar(player, cost > 0 ? "grimoire.cast" : "scroll.cast", GrimoireItems.spellName(def), cost);
			// The grimoire is never consumed: the client, which predicted it eaten, gets its hand back.
			undoPrediction(player, stack, true);
			return stack;
		}
		Msg.actionBar(player, "scroll.cast", ScrollItems.name(def).getString());
		// Exactly one scroll, never in creative.
		stack.consume(1, player);
		return stack;
	}

	/** Spends the food of a grimoire cast: saturation first, then food points (a fraction left as exhaustion). */
	private static void eat(ServerPlayer player, int cost) {
		if (cost <= 0) {
			return;
		}
		FoodData food = player.getFoodData();
		GrimoireRules.Meal meal = GrimoireRules.spend(food.getFoodLevel(), food.getSaturationLevel(), cost);
		food.setFoodLevel(meal.food());
		food.setSaturation(meal.saturation());
		if (meal.exhaustion() > 0) {
			food.addExhaustion(meal.exhaustion());
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.4F, 0.6F);
	}

	/**
	 * Undoes what the vanilla client predicted when the use completed without consuming the item: the item eaten (the
	 * inventory is sent again when {@code inventory}) and the item's use_cooldown (cleared when the server runs none for it).
	 */
	private static void undoPrediction(ServerPlayer player, ItemStack stack, boolean inventory) {
		if (inventory) {
			player.inventoryMenu.sendAllDataToRemote();
		}
		if (!player.getCooldowns().isOnCooldown(stack)) {
			player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(stack));
		}
	}

	// ================================================================== channel visuals

	private static void tickChannels() {
		if (CHANNELS.isEmpty()) {
			return;
		}
		ScrollConfig cfg = ScrollModule.config();
		long now = Scheduler.ticks();
		List<UUID> ended = new ArrayList<>();
		for (Map.Entry<UUID, Channel> entry : CHANNELS.entrySet()) {
			ServerPlayer player = GuildyServer.player(entry.getKey());
			Channel channel = entry.getValue();
			if (player == null || !player.isAlive() || !player.isUsingItem()) {
				ended.add(entry.getKey());
				if (player != null && player.isAlive()) {
					Msg.actionBar(player, channel.key().startsWith("grimoire/") ? "grimoire.fail.interrupted" : "scroll.fail.interrupted");
					fizzle(player);
				}
				continue;
			}
			ItemStack using = player.getUseItem();
			boolean grimoire = GrimoireItems.isGrimoire(using);
			String key = grimoire ? GrimoireItems.channelKey(using) : ScrollItems.idOf(using);
			if (!channel.key().equals(key)) {
				// Swapped to another scroll mid-channel: the incantation is lost (no shortcut through a short channel).
				ended.add(entry.getKey());
				player.stopUsingItem();
				syncUsing(player);
				Msg.actionBar(player, channel.key().startsWith("grimoire/") ? "grimoire.fail.interrupted" : "scroll.fail.interrupted");
				continue;
			}
			ScrollConfig.ScrollDef def = grimoire ? GrimoireItems.selectedDef(GrimoireItems.pages(using)) : cfg.scroll(key);
			if (def == null) {
				continue;
			}
			long elapsed = now - channel.start();
			Safe.run("scroll.channel.fx", () -> drawChannel(player, def.spell(), elapsed, channel.ticks()));
		}
		for (UUID uuid : ended) {
			CHANNELS.remove(uuid);
		}
	}

	/** Runic circle drawing itself under the caster, glyphs flowing into their hands, a rising light at the end. */
	private static void drawChannel(ServerPlayer player, Spell spell, long elapsed, int total) {
		ScrollConfig.Limits limits = ScrollModule.config().limits;
		double progress = Math.min(1, (elapsed + 1) / (double) total);
		Fx fx = new Fx(player.level(), player.position(), 220, limits.viewDistance);
		Vec3 feet = player.position().add(0, 0.08, 0);
		fx.runeCircle(feet, 1.7, spell.color, spell.accent, progress, elapsed * 0.08);
		Vec3 hands = player.position().add(0, 1.1, 0);
		for (int i = 0; i < 2; i++) {
			double angle = elapsed * 0.7 + i * Math.PI;
			Vec3 offset = new Vec3(Math.cos(angle) * 1.7, -1.0, Math.sin(angle) * 1.7);
			fx.move(ParticleTypes.ENCHANT, hands, offset);
		}
		if (elapsed % 4 == 0) {
			fx.burst(Fx.dust(spell.accent, 0.6F), hands.x, hands.y, hands.z, 3, 0.35, 0.3, 0.35, 0.0);
		}
		if (progress > 0.75) {
			double angle = elapsed * 0.9;
			fx.move(ParticleTypes.END_ROD, feet.x + Math.cos(angle) * 1.7, feet.y, feet.z + Math.sin(angle) * 1.7, 0, 0.18, 0);
		}
		fx.flush();
		if (elapsed % 10 == 5) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F,
					0.6F + (float) progress);
		}
	}

	/** Flash and sparks of a successful cast around the caster. */
	private static void castFlash(ServerPlayer player, Spell spell) {
		Fx fx = new Fx(player.level(), player.position(), 200, ScrollModule.config().limits.viewDistance);
		Vec3 hands = player.position().add(0, 1.2, 0);
		fx.dot(Fx.flash(spell.color), hands);
		fx.sphereBurst(Fx.fade(spell.color, spell.accent, 1.2F), hands, 0.3, 40, 0.25);
		fx.burst(ParticleTypes.ENCHANT, hands, 40, 0.5, 1.0);
		fx.arc(Fx.dust(spell.color, 1.0F), player.position().add(0, 0.1, 0), 1.7, 0, Math.PI * 2, 30, true);
		fx.flush();
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				spell.offensive ? SoundEvents.EVOKER_CAST_SPELL : SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.5F, 1.0F);
	}

	/** A failed or interrupted incantation: smoke and a dull sound. */
	private static void fizzle(ServerPlayer player) {
		Fx fx = new Fx(player.level(), player.position(), 40, 48);
		fx.burst(ParticleTypes.SMOKE, player.getX(), player.getY(0.6), player.getZ(), 16, 0.4, 0.4, 0.4, 0.02);
		fx.flush();
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 0.6F, 1.4F);
	}

	// ================================================================== cleanup

	static void forget(UUID player) {
		CHANNELS.remove(player);
	}

	static void clear() {
		CHANNELS.clear();
	}
}
