package dev.guildy.structure;

import dev.guildy.core.Lang;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A key of a structure pattern: a single block ("minecraft:bookshelf") or a block tag ("#minecraft:logs").
 */
public final class BlockKey {
	private final String raw;
	private final @Nullable Block block;
	private final @Nullable TagKey<Block> tag;

	private BlockKey(String raw, @Nullable Block block, @Nullable TagKey<Block> tag) {
		this.raw = raw;
		this.block = block;
		this.tag = tag;
	}

	/** Parses a key value; null when the block id is unknown or the syntax invalid. */
	public static @Nullable BlockKey parse(@Nullable String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String text = value.trim();
		if (text.startsWith("#")) {
			Identifier id = Identifier.tryParse(text.substring(1));
			return id == null ? null : new BlockKey(text, null, TagKey.create(Registries.BLOCK, id));
		}
		Identifier id = Identifier.tryParse(text.contains(":") ? text : "minecraft:" + text);
		if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
			return null;
		}
		return new BlockKey(text, BuiltInRegistries.BLOCK.getValue(id), null);
	}

	public boolean test(BlockState state) {
		return tag != null ? state.is(tag) : state.is(block);
	}

	public boolean isTag() {
		return tag != null;
	}

	public String raw() {
		return raw;
	}

	public @Nullable Block block() {
		return block;
	}

	/** True when a wall sign can surely hang on this block (single full solid block). Tags are checked in world. */
	@SuppressWarnings("deprecation") // isSolid() is what WallSignBlock.canSurvive checks
	public boolean canHoldSign() {
		if (block == null) {
			return true;
		}
		BlockState state = block.defaultBlockState();
		return state.isSolid() && state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	/** Item shown for this key in menus (first item of the tag for tags, barrier if none). */
	public Item displayItem() {
		if (block != null) {
			Item item = block.asItem();
			return item == Items.AIR ? Items.BARRIER : item;
		}
		if (tag != null) {
			for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
				Item item = holder.value().asItem();
				if (item != Items.AIR) {
					return item;
				}
			}
		}
		return Items.BARRIER;
	}

	/** Display name: the configured label, else the block name (translated client-side), else the raw tag. */
	public Component displayName(@Nullable String label) {
		if (label != null && !label.isBlank()) {
			return Component.literal(Lang.colorize(label));
		}
		if (block != null) {
			return block.getName();
		}
		return Component.literal(raw);
	}
}
