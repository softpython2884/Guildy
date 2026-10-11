package dev.guildy.quest;

import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolved target of a quest: a block (GATHER), an entity type (KILL) or an item (DELIVER), by id or by tag.
 * Instances are cached per (type, raw target).
 */
public final class QuestTarget {
	private static final Map<String, QuestTarget> CACHE = new HashMap<>();

	private final QuestType type;
	private final String raw;
	private final @Nullable Block block;
	private final @Nullable TagKey<Block> blockTag;
	private final @Nullable EntityType<?> entity;
	private final @Nullable TagKey<EntityType<?>> entityTag;
	private final @Nullable Item item;
	private final @Nullable TagKey<Item> itemTag;

	private QuestTarget(QuestType type, String raw, @Nullable Block block, @Nullable TagKey<Block> blockTag,
			@Nullable EntityType<?> entity, @Nullable TagKey<EntityType<?>> entityTag, @Nullable Item item, @Nullable TagKey<Item> itemTag) {
		this.type = type;
		this.raw = raw;
		this.block = block;
		this.blockTag = blockTag;
		this.entity = entity;
		this.entityTag = entityTag;
		this.item = item;
		this.itemTag = itemTag;
	}

	/** Cached lookup; null when the target is invalid for this quest type. */
	public static @Nullable QuestTarget get(QuestType type, String raw) {
		if (type == null || raw == null || !type.hasTarget()) {
			return null;
		}
		String key = type.name() + "|" + raw;
		if (CACHE.containsKey(key)) {
			return CACHE.get(key);
		}
		QuestTarget target = parse(type, raw);
		CACHE.put(key, target);
		return target;
	}

	public static void clearCache() {
		CACHE.clear();
	}

	/** Parses without caching (config validation). */
	public static @Nullable QuestTarget parse(QuestType type, String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String text = raw.trim();
		boolean tag = text.startsWith("#");
		Identifier id = Identifier.tryParse(tag ? text.substring(1) : (text.contains(":") ? text : "minecraft:" + text));
		if (id == null) {
			return null;
		}
		return switch (type) {
			case GATHER -> tag
					? new QuestTarget(type, text, null, TagKey.create(Registries.BLOCK, id), null, null, null, null)
					: BuiltInRegistries.BLOCK.containsKey(id)
					? new QuestTarget(type, text, BuiltInRegistries.BLOCK.getValue(id), null, null, null, null, null)
					: null;
			case KILL -> tag
					? new QuestTarget(type, text, null, null, null, TagKey.create(Registries.ENTITY_TYPE, id), null, null)
					: BuiltInRegistries.ENTITY_TYPE.containsKey(id)
					? new QuestTarget(type, text, null, null, BuiltInRegistries.ENTITY_TYPE.getValue(id), null, null, null)
					: null;
			case DELIVER -> tag
					? new QuestTarget(type, text, null, null, null, null, null, TagKey.create(Registries.ITEM, id))
					: ItemUtil.item(id.toString()) != null
					? new QuestTarget(type, text, null, null, null, null, ItemUtil.item(id.toString()), null)
					: null;
			default -> null;
		};
	}

	/** GATHER: block matches; crops only count when fully grown. */
	public boolean matches(BlockState state) {
		boolean match = blockTag != null ? state.is(blockTag) : block != null && state.is(block);
		if (match && state.getBlock() instanceof CropBlock crop) {
			return crop.isMaxAge(state);
		}
		return match;
	}

	public boolean matches(Entity entity) {
		if (entityTag != null) {
			return entity.is(entityTag);
		}
		return this.entity != null && entity.getType() == this.entity;
	}

	public boolean matches(ItemStack stack) {
		if (itemTag != null) {
			return stack.is(itemTag);
		}
		return item != null && stack.is(item);
	}

	/** Display name: the configured name, else the game's translated name (client-side), else the raw tag. */
	public Component name(@Nullable String configured) {
		if (configured != null && !configured.isBlank()) {
			return Component.literal(Lang.colorize(configured));
		}
		if (block != null) {
			return block.getName();
		}
		if (entity != null) {
			return entity.getDescription();
		}
		if (item != null) {
			return ItemUtil.name(item);
		}
		return Component.literal(raw);
	}

	/** Menu icon for this target. */
	public Item icon() {
		switch (type) {
			case GATHER -> {
				if (block != null && block.asItem() != Items.AIR) {
					return block.asItem();
				}
				if (blockTag != null) {
					for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(blockTag)) {
						if (holder.value().asItem() != Items.AIR) {
							return holder.value().asItem();
						}
					}
				}
				return Items.IRON_PICKAXE;
			}
			case KILL -> {
				if (entity != null) {
					return SpawnEggItem.byId(entity).map(Holder::value).orElse(Items.IRON_SWORD);
				}
				if (entityTag != null) {
					for (Holder<EntityType<?>> holder : BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(entityTag)) {
						Item egg = SpawnEggItem.byId(holder.value()).map(Holder::value).orElse(null);
						if (egg != null) {
							return egg;
						}
					}
				}
				return Items.IRON_SWORD;
			}
			case DELIVER -> {
				if (item != null) {
					return item;
				}
				if (itemTag != null) {
					for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(itemTag)) {
						return holder.value();
					}
				}
				return Items.CHEST;
			}
			default -> {
				return Items.PAPER;
			}
		}
	}
}
