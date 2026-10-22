package dev.guildy.villager;

import dev.guildy.alchemy.Elixirs;
import dev.guildy.core.GuildyServer;
import dev.guildy.core.Lang;
import dev.guildy.core.Scheduler;
import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Contents of the chests of the filtered rooms ({@link ChestRoom#filtered()}): the kind of a stack ({@link ContentKeys}
 * with the rules of villagers.json5), and the guild's {@link ContentIndex} kept up to date:
 * <ul>
 * <li>a chest is read again whenever the sorter considers it as a destination ({@link Storage#route}: a chest that no
 * longer holds the item is skipped and its kinds corrected at once);</li>
 * <li>a background pass reads {@code storage.filteredRoomReadsPerTick} loaded filtered-room chests per tick, in turn
 * over every guild (a kind added or removed by hand is seen within a few seconds, even with thousands of chests);</li>
 * <li>a room turned into a filtered room is read at once.</li>
 * </ul>
 */
final class RoomContents {
	/** Item ids, computed once per item. */
	private static final Map<Item, String> IDS = new IdentityHashMap<>();
	private static ContentKeys.@Nullable Rules cachedRules;
	private static @Nullable VillagerConfig rulesFor;
	/** Round robin of the background pass. */
	private static int guildCursor;
	private static int chestCursor;

	private RoomContents() {
	}

	static void init() {
		Scheduler.every("villager.storage.contents", 1, 0, RoomContents::readStep);
	}

	static void clear() {
		guildCursor = 0;
		chestCursor = 0;
		cachedRules = null;
		rulesFor = null;
	}

	// ================================================================ kinds

	/** Identity rules of villagers.json5 (potions, enchanted books). */
	static ContentKeys.Rules rules() {
		VillagerConfig config = VillagerModule.config();
		if (cachedRules == null || rulesFor != config) {
			ContentKeys.PotionMatch potions = ContentKeys.PotionMatch.parse(config.storage.filteredRoomPotions);
			ContentKeys.BookMatch books = ContentKeys.BookMatch.parse(config.storage.filteredRoomBooks);
			cachedRules = new ContentKeys.Rules(potions == null ? ContentKeys.Rules.DEFAULT.potions() : potions,
					books == null ? ContentKeys.Rules.DEFAULT.books() : books);
			rulesFor = config;
		}
		return cachedRules;
	}

	/** Kind of a stack for the filtered rooms ("minecraft:diorite", "minecraft:potion[minecraft:healing]"...). */
	static String key(ItemStack stack) {
		if (stack.isEmpty()) {
			return "";
		}
		Item item = stack.getItem();
		String id = IDS.computeIfAbsent(item, i -> BuiltInRegistries.ITEM.getKey(i).toString());
		PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
		if (potion != null) {
			return ContentKeys.key(id, potionOf(stack, potion), null, rules());
		}
		if (item == Items.ENCHANTED_BOOK && rules().books() == ContentKeys.BookMatch.ENCHANTMENTS) {
			ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
			List<String> enchantments = new ArrayList<>();
			if (stored != null) {
				for (Holder<Enchantment> enchantment : stored.keySet()) {
					enchantment.unwrapKey().ifPresent(key -> enchantments.add(key.identifier().toString()));
				}
			}
			return ContentKeys.key(id, null, enchantments, rules());
		}
		return id;
	}

	/** Potion part of a potion item: its base potion, a Guildy elixir ("elixir:<id>"), else "custom". */
	private static String potionOf(ItemStack stack, PotionContents contents) {
		String base = contents.potion().flatMap(Holder::unwrapKey).map(key -> key.identifier().toString()).orElse(null);
		if (base != null) {
			return base;
		}
		String elixir = Elixirs.idOf(stack);
		return elixir != null ? "elixir:" + elixir : ContentKeys.CUSTOM_POTION;
	}

	/** Kinds of items a container holds. */
	static Set<String> kinds(Container container) {
		Set<String> kinds = new HashSet<>();
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty()) {
				kinds.add(key(stack));
			}
		}
		return kinds;
	}

	/** One stack of each kind the container holds, in slot order (menus). */
	static List<ItemStack> samples(Container container) {
		Map<String, ItemStack> samples = new LinkedHashMap<>();
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty()) {
				samples.putIfAbsent(key(stack), stack);
			}
		}
		return new ArrayList<>(samples.values());
	}

	/**
	 * "Diorite, Diorite polie +2": the names of the first {@code shown} kinds (translated by the client) and how many
	 * more; "" for none.
	 */
	static MutableComponent summary(List<ItemStack> samples, int shown) {
		MutableComponent text = Component.empty();
		for (int i = 0; i < samples.size() && i < shown; i++) {
			if (i > 0) {
				text.append(Component.literal(", "));
			}
			text.append(samples.get(i).getItemName());
		}
		if (samples.size() > shown) {
			text.append(Component.literal(Lang.raw("villager.storage.content.more", samples.size() - shown)));
		}
		return text;
	}

	// ================================================================ index

	/**
	 * Reads the container of a filtered-room chest into the guild's index.
	 *
	 * @return the kinds it holds now (live read-only view)
	 */
	static Set<String> refresh(Guild guild, StorageChest chest, Container container) {
		ContentIndex index = guild.villagers.contents();
		long id = chest.pos.asLong();
		index.update(id, kinds(container));
		return index.kinds(id);
	}

	/** Reads a filtered-room chest if it is loaded (nothing otherwise). */
	static void refresh(ServerLevel level, Guild guild, StorageChest chest) {
		if (!chest.isContent() || !Storage.usable(level, guild, chest)) {
			return;
		}
		Container container = Storage.container(level, chest.pos);
		if (container != null) {
			refresh(guild, chest, container);
		}
	}

	/** Reads every loaded chest of a room (a room just turned into a filtered room). */
	static void refreshRoom(ServerLevel level, Guild guild, ChestRoom room) {
		for (StorageChest chest : ChestRooms.chests(guild, room)) {
			refresh(level, guild, chest);
		}
	}

	/** Background pass: a few loaded filtered-room chests per tick, every guild in turn. */
	private static void readStep() {
		ServerLevel level = GuildyServer.claimLevel();
		if (level == null) {
			return;
		}
		List<Guild> guilds = new ArrayList<>();
		for (Guild guild : GuildManager.all()) {
			if (hasFilteredRoom(guild)) {
				guilds.add(guild);
			}
		}
		if (guilds.isEmpty()) {
			return;
		}
		int budget = VillagerModule.config().storage.filteredRoomReadsPerTick;
		// Unloaded chests cost no read but are skipped within a bounded number of steps.
		int steps = budget * 16;
		for (int visited = 0; budget > 0 && visited <= guilds.size() && steps-- > 0; ) {
			if (guildCursor >= guilds.size()) {
				guildCursor = 0;
			}
			Guild guild = guilds.get(guildCursor);
			List<StorageChest> chests = Storage.index(guild).contentChests();
			if (chestCursor >= chests.size()) {
				guildCursor++;
				chestCursor = 0;
				visited++;
				continue;
			}
			StorageChest chest = chests.get(chestCursor++);
			if (chest.pos != null && level.isLoaded(chest.pos)) {
				refresh(level, guild, chest);
				budget--;
			}
		}
	}

	private static boolean hasFilteredRoom(Guild guild) {
		for (ChestRoom room : guild.villagers.rooms) {
			if (room.filtered()) {
				return true;
			}
		}
		return false;
	}
}
