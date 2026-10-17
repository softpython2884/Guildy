package dev.guildy.ruin;

import dev.guildy.core.GuildyItems;
import dev.guildy.core.ItemUtil;
import dev.guildy.core.Lang;
import dev.guildy.ruin.RuinConfig.RuinDef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Items leading to a ruin: an explorer-style filled map (Overworld: biome preview + target mark) or a lodestone-tracked
 * compass (Nether: points to the ruin while held in the Nether), both tagged {@code custom_data} guildy kind
 * {@value #KIND_MAP} / {@value #KIND_COMPASS}.
 */
public final class RuinItems {
	public static final String KIND_MAP = "ruin_map";
	public static final String KIND_COMPASS = "ruin_compass";

	private RuinItems() {
	}

	/** Map (ruin in a dimension with a level) or compass (Nether ruin); empty when its dimension is missing. */
	public static ItemStack forRuin(MinecraftServer server, RuinRecord ruin) {
		ResourceKey<Level> dimension = dimension(ruin);
		ServerLevel level = dimension == null ? null : server.getLevel(dimension);
		if (level == null) {
			return ItemStack.EMPTY;
		}
		return ruin.isNether() ? compass(level.dimension(), ruin) : map(level, ruin);
	}

	public static ItemStack map(ServerLevel level, RuinRecord ruin) {
		RuinDef def = Encounters.def(ruin);
		RuinConfig.CartographySettings cfg = RuinModule.config().cartography;
		BlockPos target = ruin.target();
		ItemStack stack = MapItem.create(level, target.getX(), target.getZ(), (byte) cfg.mapZoom, true, true);
		MapItem.renderBiomePreviewMap(level, stack);
		MapItemSavedData.addTargetDecoration(stack, target, "+", decoration(def.mapDecoration.isEmpty() ? cfg.mapDecoration : def.mapDecoration));
		tag(stack, KIND_MAP, ruin, target);
		return ItemUtil.named(stack, Lang.get("ruin.item.map.name", Lang.colorize(def.name)), lore(ruin, def, target));
	}

	public static ItemStack compass(ResourceKey<Level> dimension, RuinRecord ruin) {
		RuinDef def = Encounters.def(ruin);
		BlockPos target = ruin.target();
		ItemStack stack = new ItemStack(Items.COMPASS);
		// Not tracked: the compass keeps pointing to the ruin (no lodestone needed there).
		stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(dimension, target)), false));
		tag(stack, KIND_COMPASS, ruin, target);
		return ItemUtil.named(stack, Lang.get("ruin.item.compass.name", Lang.colorize(def.name)), lore(ruin, def, target));
	}

	private static void tag(ItemStack stack, String kind, RuinRecord ruin, BlockPos target) {
		CompoundTag extra = new CompoundTag();
		extra.putString("ruin", ruin.key);
		extra.putString("id", ruin.ruinId);
		extra.putString("dimension", ruin.dimension);
		extra.putInt("x", target.getX());
		extra.putInt("y", target.getY());
		extra.putInt("z", target.getZ());
		GuildyItems.tag(stack, kind, extra);
	}

	private static List<Component> lore(RuinRecord ruin, RuinDef def, BlockPos target) {
		List<Component> lore = new ArrayList<>();
		lore.add(Lang.get(ruin.isNether() ? "ruin.item.lore.nether" : "ruin.item.lore.overworld"));
		lore.add(Lang.get("ruin.item.lore.coords", target.getX(), target.getY(), target.getZ()));
		lore.add(Lang.get("ruin.item.lore.tier", def.tier));
		lore.add(Lang.get("ruin.item.lore.threat", Encounters.threatLabel(Encounters.threatLevel(ruin))));
		lore.add(Lang.get("ruin.item.lore.boss", Lang.colorize(def.boss.name)));
		lore.add(Lang.get(ruin.isNether() ? "ruin.item.lore.hint.nether" : "ruin.item.lore.hint"));
		return lore;
	}

	private static Holder<MapDecorationType> decoration(String id) {
		Identifier identifier = Identifier.tryParse(id);
		if (identifier == null) {
			return MapDecorationTypes.RED_X;
		}
		return BuiltInRegistries.MAP_DECORATION_TYPE.get(identifier).<Holder<MapDecorationType>>map(h -> h).orElse(MapDecorationTypes.RED_X);
	}

	static ResourceKey<Level> dimension(RuinRecord ruin) {
		Identifier id = Identifier.tryParse(ruin.dimension);
		return id == null ? null : ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id);
	}
}
