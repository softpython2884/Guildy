package dev.guildy.quest;

import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolved target of a quest: a block (GATHER), an entity type (KILL, BREED, TAME, NIGHT_HUNT), an item (DELIVER, CRAFT,
 * SMELT, FISH, TRADE, ENCHANT, COLLECTION items) or a biome (BIOME), by id or by tag. Instances are cached per
 * (kind, raw target).
 */
public final class QuestTarget {
	private static final Map<String, QuestTarget> CACHE = new HashMap<>();

	private final QuestType.TargetKind kind;
	private final String raw;
	private final @Nullable Block block;
	private final @Nullable TagKey<Block> blockTag;
	private final @Nullable EntityType<?> entity;
	private final @Nullable TagKey<EntityType<?>> entityTag;
	private final @Nullable Item item;
	private final @Nullable TagKey<Item> itemTag;
	private final @Nullable ResourceKey<Biome> biome;
	private final @Nullable TagKey<Biome> biomeTag;

	private QuestTarget(QuestType.TargetKind kind, String raw, @Nullable Block block, @Nullable TagKey<Block> blockTag,
			@Nullable EntityType<?> entity, @Nullable TagKey<EntityType<?>> entityTag, @Nullable Item item, @Nullable TagKey<Item> itemTag,
			@Nullable ResourceKey<Biome> biome, @Nullable TagKey<Biome> biomeTag) {
		this.kind = kind;
		this.raw = raw;
		this.block = block;
		this.blockTag = blockTag;
		this.entity = entity;
		this.entityTag = entityTag;
		this.item = item;
		this.itemTag = itemTag;
		this.biome = biome;
		this.biomeTag = biomeTag;
	}

	/** Cached lookup; null when the target is blank or invalid for this quest type. */
	public static @Nullable QuestTarget get(QuestType type, String raw) {
		return type == null ? null : get(type.targetKind(), raw);
	}

	/** Cached lookup by target kind (COLLECTION items use {@link QuestType.TargetKind#ITEM}). */
	public static @Nullable QuestTarget get(QuestType.TargetKind kind, String raw) {
		if (kind == null || kind == QuestType.TargetKind.NONE || raw == null || raw.isBlank()) {
			return null;
		}
		String key = kind.name() + "|" + raw;
		if (CACHE.containsKey(key)) {
			return CACHE.get(key);
		}
		QuestTarget target = parse(kind, raw);
		CACHE.put(key, target);
		return target;
	}

	public static void clearCache() {
		CACHE.clear();
	}

	/** Parses without caching (config validation). */
	public static @Nullable QuestTarget parse(QuestType type, String raw) {
		return type == null ? null : parse(type.targetKind(), raw);
	}

	public static @Nullable QuestTarget parse(QuestType.TargetKind kind, String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String text = raw.trim();
		boolean tag = text.startsWith("#");
		Identifier id = Identifier.tryParse(tag ? text.substring(1) : (text.contains(":") ? text : "minecraft:" + text));
		if (id == null) {
			return null;
		}
		return switch (kind) {
			case BLOCK -> tag
					? new QuestTarget(kind, text, null, TagKey.create(Registries.BLOCK, id), null, null, null, null, null, null)
					: BuiltInRegistries.BLOCK.containsKey(id)
					? new QuestTarget(kind, text, BuiltInRegistries.BLOCK.getValue(id), null, null, null, null, null, null, null)
					: null;
			case ENTITY -> tag
					? new QuestTarget(kind, text, null, null, null, TagKey.create(Registries.ENTITY_TYPE, id), null, null, null, null)
					: BuiltInRegistries.ENTITY_TYPE.containsKey(id)
					? new QuestTarget(kind, text, null, null, BuiltInRegistries.ENTITY_TYPE.getValue(id), null, null, null, null, null)
					: null;
			case ITEM -> tag
					? new QuestTarget(kind, text, null, null, null, null, null, TagKey.create(Registries.ITEM, id), null, null)
					: ItemUtil.item(id.toString()) != null
					? new QuestTarget(kind, text, null, null, null, null, ItemUtil.item(id.toString()), null, null, null)
					: null;
			// Biomes are data-driven: ids are checked against the server registry once it runs (QuestConfig.checkBiomes).
			case BIOME -> tag
					? new QuestTarget(kind, text, null, null, null, null, null, null, null, TagKey.create(Registries.BIOME, id))
					: new QuestTarget(kind, text, null, null, null, null, null, null, ResourceKey.create(Registries.BIOME, id), null);
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
		if (stack.isEmpty()) {
			return false;
		}
		if (itemTag != null) {
			return stack.is(itemTag);
		}
		return item != null && stack.is(item);
	}

	public boolean matches(Holder<Biome> holder) {
		if (biomeTag != null) {
			return holder.is(biomeTag);
		}
		return biome != null && holder.is(biome);
	}

	/** Biome id (null for tags and other kinds). */
	public @Nullable Identifier biomeId() {
		return biome == null ? null : biome.identifier();
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
		if (biome != null) {
			Identifier id = biome.identifier();
			return Component.translatable("biome." + id.getNamespace() + "." + id.getPath().replace('/', '.'));
		}
		return Component.literal(raw);
	}

	/** Menu icon for this target (null when the target has no natural icon: biomes). */
	public @Nullable Item icon() {
		switch (kind) {
			case BLOCK -> {
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
			case ENTITY -> {
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
			case ITEM -> {
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
				return null;
			}
		}
	}
}
