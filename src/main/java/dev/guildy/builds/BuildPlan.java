package dev.guildy.builds;

import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A schematic ready to be built: its palette resolved to block states (old files upgraded with the vanilla DataFixer
 * from their data version), what each entry costs ({@link MaterialRules}), its pass ({@link BuildPasses}), the cosmetic
 * block entity data kept (sign texts, banner patterns: never container contents) and the cells each interior mode digs
 * out ({@link InteriorRules}). Built off the server thread (registries are frozen and read-only by then); immutable.
 * <p>
 * Unknown blocks (a mod's block, a newer version) are skipped and reported; an unknown property or value keeps the
 * block's default for it (reported too): a file never crashes the server.
 */
public final class BuildPlan {
	private static final Set<String> SIGN_KEYS = Set.of("front_text", "back_text", "is_waxed");
	private static final Set<String> BANNER_KEYS = Set.of("patterns", "CustomName");

	public final SchematicData data;
	/** Resolved state per palette entry (null: void or unknown block). */
	final @Nullable BlockState[] states;
	final MaterialRules.Kind[] kinds;
	final BuildPasses.Pass[] passes;
	/** Cost of one cell of the entry (item → count) and what it gives back (empty buckets). */
	final Map<Item, Integer>[] costs;
	final Map<Item, Integer>[] returns;
	final boolean[] falling;
	/** Why an entry is not buildable (code shown in the menus), null otherwise. */
	final @Nullable String[] reasons;
	/** Cells using each palette entry. */
	final int[] uses;
	/** Cosmetic block entity data by cell index (upgraded, filtered). */
	final Map<Integer, CompoundTag> cosmetic;
	/** Problems met while resolving (lang "key|arg" entries, see {@link SchematicData#warnings}), the file's ones included. */
	public final List<String> warnings;
	private final byte[] interiorKinds;
	private final List<InteriorRules.Door> doors;
	private final Map<InteriorRules.Mode, BitSet> clear = new EnumMap<>(InteriorRules.Mode.class);
	private final Map<Integer, List<GhostLod.Ghost>> ghosts = new HashMap<>();

	@SuppressWarnings("unchecked")
	private BuildPlan(SchematicData data, boolean upgrade, boolean waterFree) {
		this.data = data;
		int size = data.palette.size();
		this.states = new BlockState[size];
		this.kinds = new MaterialRules.Kind[size];
		this.passes = new BuildPasses.Pass[size];
		this.costs = new Map[size];
		this.returns = new Map[size];
		this.falling = new boolean[size];
		this.reasons = new String[size];
		this.uses = new int[size];
		Set<String> warnings = new LinkedHashSet<>(data.warnings);
		int current = SharedConstants.getCurrentVersion().dataVersion().version();
		boolean fix = upgrade && data.dataVersion > 0 && data.dataVersion < current;
		for (int i = 0; i < size; i++) {
			costs[i] = Map.of();
			returns[i] = Map.of();
			if (i == SchematicData.VOID) {
				kinds[i] = MaterialRules.Kind.VOID;
				continue;
			}
			String text = data.palette.get(i);
			if (fix) {
				text = upgradeState(text, data.dataVersion, current);
			}
			BlockState state = resolve(text, warnings);
			states[i] = state;
			if (state == null) {
				kinds[i] = MaterialRules.Kind.NOT_BUILDABLE;
				reasons[i] = "unknown";
				continue;
			}
			if (state.isAir()) {
				kinds[i] = MaterialRules.Kind.AIR;
				continue;
			}
			MaterialRules.Requirement requirement = MaterialRules.of(textOf(state), BuildPlan::itemExists, waterFree);
			kinds[i] = requirement.kind();
			if (requirement.kind() == MaterialRules.Kind.NOT_BUILDABLE || requirement.kind() == MaterialRules.Kind.AUTO) {
				reasons[i] = requirement.reason();
				continue;
			}
			Map<Item, Integer> cost = new LinkedHashMap<>();
			if (requirement.pick()) {
				Item item = state.getBlock().asItem();
				if (item == Items.AIR) {
					kinds[i] = MaterialRules.Kind.NOT_BUILDABLE;
					reasons[i] = "no_item";
					continue;
				}
				cost.put(item, 1);
			}
			requirement.items().forEach((id, count) -> {
				Item item = item(id);
				if (item != null) {
					cost.merge(item, count, Integer::sum);
				}
			});
			Map<Item, Integer> back = new LinkedHashMap<>();
			requirement.returns().forEach((id, count) -> {
				Item item = item(id);
				if (item != null) {
					back.merge(item, count, Integer::sum);
				}
			});
			costs[i] = Map.copyOf(cost);
			returns[i] = Map.copyOf(back);
			falling[i] = state.getBlock() instanceof FallingBlock;
			passes[i] = BuildPasses.passOf(requirement.kind(), false, requirement.kind() == MaterialRules.Kind.BUILD && SupportProbe.needsSupport(state));
		}
		for (int cell : data.cells) {
			if (cell >= 0 && cell < size) {
				uses[cell]++;
			}
		}
		for (int i = 0; i < size; i++) {
			if (uses[i] > 0 && kinds[i] == MaterialRules.Kind.NOT_BUILDABLE && "unknown".equals(reasons[i])) {
				warnings.add("build.warning.unknown_block|" + data.palette.get(i) + "|" + uses[i]);
			}
		}
		// Interior: void (and unknown) cells count as outside, air as air, everything else as a wall.
		byte[] interior = new byte[data.cells.length];
		List<InteriorRules.Door> doorList = new ArrayList<>();
		for (int index = 0; index < data.cells.length; index++) {
			int p = data.cells[index];
			BlockState state = p > 0 && p < size ? states[p] : null;
			if (state == null) {
				interior[index] = InteriorRules.VOID;
			} else if (state.isAir()) {
				interior[index] = InteriorRules.AIR;
			} else {
				interior[index] = InteriorRules.BLOCK;
				if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
					Direction facing = state.getValue(DoorBlock.FACING);
					doorList.add(new InteriorRules.Door(data.x(index), data.y(index), data.z(index), facing.getAxis() == Direction.Axis.X));
				}
			}
		}
		this.interiorKinds = interior;
		this.doors = List.copyOf(doorList);
		// Block entities: only the cosmetic data (texts of signs, patterns of banners), upgraded like the states.
		Map<Integer, CompoundTag> kept = new HashMap<>();
		data.blockEntities.forEach((index, tag) -> {
			CompoundTag nbt = fix ? upgradeBlockEntity(tag, data.dataVersion, current) : tag;
			CompoundTag filtered = cosmeticOf(nbt);
			if (filtered != null) {
				kept.put(index, filtered);
			}
		});
		this.cosmetic = Map.copyOf(kept);
		this.warnings = List.copyOf(warnings);
		clear.put(InteriorRules.Mode.HOLLOW, InteriorRules.clearCells(data.sizeX, data.sizeY, data.sizeZ, interior, doors, InteriorRules.Mode.HOLLOW));
	}

	/** Resolves a schematic (worker thread). */
	public static BuildPlan of(SchematicData data, boolean upgrade, boolean waterFree) {
		return new BuildPlan(data, upgrade, waterFree);
	}

	// ------------------------------------------------------------------ queries

	public int paletteSize() {
		return states.length;
	}

	public @Nullable BlockState state(int palette) {
		return states[palette];
	}

	public MaterialRules.Kind kind(int palette) {
		return kinds[palette];
	}

	public BuildPasses.@Nullable Pass pass(int palette) {
		return passes[palette];
	}

	public Map<Item, Integer> cost(int palette) {
		return costs[palette];
	}

	public Map<Item, Integer> returns(int palette) {
		return returns[palette];
	}

	public boolean falling(int palette) {
		return falling[palette];
	}

	public @Nullable String reason(int palette) {
		return reasons[palette];
	}

	public int uses(int palette) {
		return uses[palette];
	}

	/** Ghost blocks of the preview, at most {@code max} (shared, never modify; computed once per maximum). */
	public synchronized List<GhostLod.Ghost> ghosts(int max) {
		return ghosts.computeIfAbsent(max, m -> List.copyOf(GhostLod.select(data.sizeX, data.sizeY, data.sizeZ, data.cells,
				cell -> ghostable(data.cells[cell]), this::solid, m)));
	}

	/** Shown as a ghost: a block to build or a fluid. */
	public boolean ghostable(int palette) {
		return (kinds[palette] == MaterialRules.Kind.BUILD || kinds[palette] == MaterialRules.Kind.FLUID) && states[palette] != null;
	}

	private boolean solid(int palette) {
		BlockState state = states[palette];
		return state != null && state.isSolidRender();
	}

	/** Cells dug out in that mode (shared, never modify). */
	public synchronized BitSet clearCells(InteriorRules.Mode mode) {
		return clear.computeIfAbsent(mode, m -> InteriorRules.clearCells(data.sizeX, data.sizeY, data.sizeZ, interiorKinds, doors, m));
	}

	public @Nullable CompoundTag cosmetic(int cell) {
		return cosmetic.get(cell);
	}

	/** Everything the schematic costs (item → count). */
	public Map<Item, Integer> bill() {
		Map<Item, Integer> bill = new LinkedHashMap<>();
		for (int p = 0; p < states.length; p++) {
			if (uses[p] == 0) {
				continue;
			}
			int count = uses[p];
			costs[p].forEach((item, n) -> bill.merge(item, n * count, Integer::sum));
		}
		return bill;
	}

	/** Cells that cannot be built (unknown block, no item, forbidden block). */
	public int notBuildableCells() {
		int count = 0;
		for (int p = 0; p < states.length; p++) {
			if (kinds[p] == MaterialRules.Kind.NOT_BUILDABLE) {
				count += uses[p];
			}
		}
		return count;
	}

	/** Cells with a block to place (fluids included). */
	public int buildCells() {
		int count = 0;
		for (int p = 0; p < states.length; p++) {
			if (kinds[p] == MaterialRules.Kind.BUILD || kinds[p] == MaterialRules.Kind.FLUID) {
				count += uses[p];
			}
		}
		return count;
	}

	// ------------------------------------------------------------------ resolution

	/** The canonical text of a resolved state ({@link MaterialRules} reads it). */
	static BlockStateString textOf(BlockState state) {
		Map<String, String> props = new LinkedHashMap<>();
		for (Property<?> property : state.getProperties()) {
			props.put(property.getName(), valueName(state, property));
		}
		return new BlockStateString(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), props);
	}

	private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}

	/** Block state of a text, null for an unknown block (unknown properties / values reported, default kept). */
	static @Nullable BlockState resolve(String text, Set<String> warnings) {
		BlockStateString parsed = BlockStateString.parse(text);
		if (parsed == null) {
			return null;
		}
		Identifier id = Identifier.tryParse(parsed.id());
		if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
			return null;
		}
		Block block = BuiltInRegistries.BLOCK.getValue(id);
		BlockState state = block.defaultBlockState();
		for (Map.Entry<String, String> entry : parsed.props().entrySet()) {
			Property<?> property = block.getStateDefinition().getProperty(entry.getKey());
			if (property == null) {
				warnings.add("build.warning.unknown_property|" + parsed.id() + "|" + entry.getKey());
				continue;
			}
			BlockState next = with(state, property, entry.getValue());
			if (next == null) {
				warnings.add("build.warning.unknown_value|" + parsed.id() + "|" + entry.getKey() + "=" + entry.getValue());
			} else {
				state = next;
			}
		}
		return state;
	}

	private static <T extends Comparable<T>> @Nullable BlockState with(BlockState state, Property<T> property, String value) {
		Optional<T> parsed = property.getValue(value);
		return parsed.map(v -> state.setValue(property, v)).orElse(null);
	}

	/** One palette state through the vanilla DataFixer (block_state type), from the file's version to the current one. */
	static String upgradeState(String text, int from, int to) {
		BlockStateString parsed = BlockStateString.parse(text);
		if (parsed == null) {
			return text;
		}
		try {
			CompoundTag tag = new CompoundTag();
			tag.putString("Name", parsed.id());
			if (!parsed.props().isEmpty()) {
				CompoundTag props = new CompoundTag();
				parsed.props().forEach(props::putString);
				tag.put("Properties", props);
			}
			Tag fixed = DataFixers.getDataFixer().update(References.BLOCK_STATE, new Dynamic<>(NbtOps.INSTANCE, (Tag) tag), from, to).getValue();
			if (fixed instanceof CompoundTag compound) {
				return SchematicReader.stateText(compound);
			}
		} catch (RuntimeException e) {
			// An unexpected structure: the state stays as written.
		}
		return text;
	}

	private static CompoundTag upgradeBlockEntity(CompoundTag tag, int from, int to) {
		try {
			Tag fixed = DataFixers.getDataFixer().update(References.BLOCK_ENTITY, new Dynamic<>(NbtOps.INSTANCE, (Tag) tag.copy()), from, to).getValue();
			return fixed instanceof CompoundTag compound ? compound : tag;
		} catch (RuntimeException e) {
			return tag;
		}
	}

	/** Sign texts and banner patterns only (nothing else is ever copied: no container contents, no commands). */
	static @Nullable CompoundTag cosmeticOf(CompoundTag tag) {
		String id = tag.getStringOr("id", "");
		Set<String> keys;
		if (id.equals("minecraft:sign") || id.equals("minecraft:hanging_sign")) {
			keys = SIGN_KEYS;
		} else if (id.equals("minecraft:banner")) {
			keys = BANNER_KEYS;
		} else {
			return null;
		}
		CompoundTag kept = new CompoundTag();
		for (String key : keys) {
			Tag value = tag.get(key);
			if (value != null) {
				kept.put(key, value.copy());
			}
		}
		if (kept.isEmpty()) {
			return null;
		}
		kept.put("id", StringTag.valueOf(id));
		return kept;
	}

	private static boolean itemExists(String id) {
		Identifier identifier = Identifier.tryParse(id);
		return identifier != null && BuiltInRegistries.ITEM.containsKey(identifier);
	}

	private static @Nullable Item item(String id) {
		Identifier identifier = Identifier.tryParse(id);
		return identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier) ? null : BuiltInRegistries.ITEM.getValue(identifier);
	}

	/** True when the state is the lower half of a two-block-high block (door, tall flower...). */
	static boolean isLowerHalf(BlockState state) {
		return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
	}
}
