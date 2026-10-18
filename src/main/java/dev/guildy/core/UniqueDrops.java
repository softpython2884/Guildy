package dev.guildy.core;

import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.random.RandomGenerator;

/**
 * Entry point for unique (legendary) weapon drops, implemented by the unique-items module. Other modules (hordes,
 * quests, merchants, expeditions, ruins) call {@link #roll} with their source name and, when known, the {@link Recipient}
 * (the uniqueness rules of uniques.json5 never give a guild a weapon it already holds); the default is a safe no-op.
 */
public final class UniqueDrops {
	/** Custom data kind of the unique items. */
	public static final String KIND = "unique";

	/** Rolls a unique item for a source ("boss", "blood_boss", "quest", "legendary_quest", "expedition", "trade"). */
	@FunctionalInterface
	public interface Roller {
		ItemStack roll(String source, RandomGenerator random);
	}

	/** Rolls a unique item for a source, for a recipient (uniqueness rules). */
	@FunctionalInterface
	public interface RecipientRoller {
		ItemStack roll(String source, RandomGenerator random, Recipient recipient);
	}

	/**
	 * A unique item offered for sale (not registered until someone carries it): {@code id} null / "random" = drawn from the
	 * source table, else that id; empty when the recipient may not get it.
	 */
	@FunctionalInterface
	public interface Offerer {
		ItemStack offer(String source, @Nullable String id, RandomGenerator random, Recipient recipient);
	}

	/** Creates a unique item by id (empty stack for an unknown id). */
	@FunctionalInterface
	public interface Factory {
		ItemStack create(String id);
	}

	/**
	 * Who receives a drop: a player and / or a guild (both may be null). The guild decides the uniqueness check
	 * ("guild" mode), the pair becomes the first holder of the registered copy.
	 */
	public record Recipient(@Nullable UUID player, @Nullable UUID guild) {
		public static final Recipient NONE = new Recipient(null, null);

		/** The player and his current guild. */
		public static Recipient of(@Nullable ServerPlayer player) {
			if (player == null) {
				return NONE;
			}
			Guild guild = GuildManager.of(player);
			return new Recipient(player.getUUID(), guild == null ? null : guild.id);
		}

		/** The player for this guild (quest rewards: the guild that completed it). */
		public static Recipient of(@Nullable ServerPlayer player, @Nullable Guild guild) {
			if (guild == null) {
				return of(player);
			}
			return new Recipient(player == null ? null : player.getUUID(), guild.id);
		}

		/** A guild without a player (expeditions, merchants). */
		public static Recipient guild(@Nullable Guild guild) {
			return guild == null ? NONE : new Recipient(null, guild.id);
		}

		/** Horde boss: the player who killed it, else the member of the attacked guild nearest to it, else the guild. */
		public static Recipient killerOrNearest(LivingEntity victim, @Nullable Guild guild) {
			if (victim.getKillCredit() instanceof ServerPlayer killer) {
				return of(killer);
			}
			if (guild == null) {
				return NONE;
			}
			ServerPlayer nearest = null;
			double best = Double.MAX_VALUE;
			for (ServerPlayer member : guild.onlineMembers()) {
				if (member.level() == victim.level()) {
					double distance = member.distanceToSqr(victim);
					if (distance < best) {
						best = distance;
						nearest = member;
					}
				}
			}
			return nearest == null ? guild(guild) : of(nearest, guild);
		}

		/** Ruin boss: the player who dealt it the most damage, else the killer. */
		public static Recipient topDamager(LivingEntity victim) {
			UUID top = Safe.get("unique.damage", () -> damageLeader.apply(victim), null);
			if (top != null) {
				ServerPlayer online = GuildyServer.player(top);
				if (online != null) {
					return of(online);
				}
				Guild guild = GuildManager.of(top);
				return new Recipient(top, guild == null ? null : guild.id);
			}
			return victim.getKillCredit() instanceof ServerPlayer killer ? of(killer) : NONE;
		}
	}

	public static Roller roller = (source, random) -> ItemStack.EMPTY;
	public static Factory factory = id -> ItemStack.EMPTY;
	public static RecipientRoller recipientRoller = (source, random, recipient) -> roller.roll(source, random);
	public static Offerer offerer = (source, id, random, recipient) ->
			id == null || "random".equals(id) ? roller.roll(source, random) : factory.create(id);
	/** UUID of the player who dealt the most damage to a tracked boss, or null. */
	public static Function<LivingEntity, @Nullable UUID> damageLeader = victim -> null;
	/** Roller installed with the recipient-aware one: another value means a stub (tests), used for every roll. */
	private static @Nullable Roller installed;

	private UniqueDrops() {
	}

	/** Called by the unique module: the plain and the recipient-aware rollers, the offers and the factory. */
	public static void install(Roller plain, RecipientRoller withRecipient, Offerer offers, Factory create) {
		roller = plain;
		installed = plain;
		recipientRoller = withRecipient;
		offerer = offers;
		factory = create;
	}

	/** A unique item for this source, or {@link ItemStack#EMPTY} when the roll fails (or the module is absent). */
	public static ItemStack roll(String source) {
		return roll(source, ThreadLocalRandom.current());
	}

	public static ItemStack roll(String source, RandomGenerator random) {
		ItemStack stack = Safe.get("unique.roll", () -> roller.roll(source, random), ItemStack.EMPTY);
		return stack == null ? ItemStack.EMPTY : stack;
	}

	/**
	 * A unique item for this source and recipient: never one the recipient's guild already holds ("guild" mode) or that
	 * exists anywhere ("server" mode); when every item of the table is taken, maybe a spell scroll of the same source
	 * instead (fallbackToScroll). Empty when the roll fails.
	 */
	public static ItemStack roll(String source, @Nullable Recipient recipient) {
		return roll(source, ThreadLocalRandom.current(), recipient);
	}

	public static ItemStack roll(String source, RandomGenerator random, @Nullable Recipient recipient) {
		Roller plain = roller;
		Recipient target = recipient == null ? Recipient.NONE : recipient;
		ItemStack stack = Safe.get("unique.roll", () -> plain != installed ? plain.roll(source, random)
				: recipientRoller.roll(source, random, target), ItemStack.EMPTY);
		return stack == null ? ItemStack.EMPTY : stack;
	}

	/**
	 * A unique item for a merchant offer of this recipient ({@code id} null or "random" = drawn from the source table):
	 * never one the recipient may not receive. Registered only once someone carries it.
	 */
	public static ItemStack offer(String source, @Nullable String id, @Nullable Recipient recipient) {
		Recipient target = recipient == null ? Recipient.NONE : recipient;
		ItemStack stack = Safe.get("unique.offer", () -> offerer.offer(source, id, ThreadLocalRandom.current(), target), ItemStack.EMPTY);
		return stack == null ? ItemStack.EMPTY : stack;
	}

	public static ItemStack create(String id) {
		ItemStack stack = Safe.get("unique.create", () -> factory.create(id), ItemStack.EMPTY);
		return stack == null ? ItemStack.EMPTY : stack;
	}

	/**
	 * A source with a drop context: {@code withContext("ruin_boss", "grand_temple_maya", "cursed")} =
	 * "ruin_boss@grand_temple_maya,cursed". The unique module rolls in the table of the plain source, where the favourite
	 * weapons of each context (uniques.json5 "favorites") weigh more; blank contexts are skipped.
	 */
	public static String withContext(String source, String... contexts) {
		StringBuilder out = new StringBuilder(source);
		char separator = '@';
		for (String context : contexts) {
			if (context != null && !context.isBlank()) {
				out.append(separator).append(context.trim());
				separator = ',';
			}
		}
		return out.toString();
	}

	/** True for a unique item (a roll may also give a spell scroll instead). */
	public static boolean isUnique(ItemStack stack) {
		return !stack.isEmpty() && GuildyItems.is(stack, KIND);
	}
}
