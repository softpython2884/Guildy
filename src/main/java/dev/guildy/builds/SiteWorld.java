package dev.guildy.builds;

import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * World side of a construction site: what stands in a cell ({@link CellRules.World}), whether a block can go there now
 * ({@link Ready}), and the actions of the builders. Only loaded chunks are read.
 * <p>
 * Update flags: a target block is set with {@code UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE} (no neighbour update, no shape
 * update), like vanilla structures: the schematic's exact state (stairs, fences, panes, walls as saved) and the blocks
 * placed before it stay as they are; a natural block is replaced by the target in the same call (stone → planks, never
 * an intermediate air block, so nothing above falls). Digging a cell out uses the normal updates (neighbours react like
 * when a player mines) but only once the safety checks passed: no falling block above, no fluid next to it (outside
 * fluids are plugged first).
 */
public final class SiteWorld {
	private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	private static final ItemStack PICKAXE = new ItemStack(Items.IRON_PICKAXE);
	private static final ItemStack AXE = new ItemStack(Items.IRON_AXE);
	private static final ItemStack SHOVEL = new ItemStack(Items.IRON_SHOVEL);
	private static final ItemStack HOE = new ItemStack(Items.IRON_HOE);
	private static final Direction[] AROUND = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};

	/** Why a cell cannot be worked right now. */
	public enum Ready {
		OK,
		/** A falling target (sand...) with nothing under it yet. */
		FALLING,
		/** {@code canSurvive} is false (no wall for the torch yet...). */
		SUPPORT,
		/** A living entity stands in the cell (a target with collision). */
		OCCUPIED,
		/** The other half (door top, bed head) cannot go in its cell. */
		PARTNER,
		/** A falling block (sand, gravel) above a cell to dig out. */
		GRAVITY,
		/** Water or lava next to a cell to dig out (inside the site: waits; outside: plugged first). */
		FLUID
	}

	/** Compiled lists of a config instance. */
	private record Lists(Set<Block> natural, List<TagKey<Block>> naturalTags, Map<Block, Boolean> naturalCache, Set<String> ignored,
			Set<Item> plugItems, BlockState plugBlock, @Nullable BlockState foundation) {
	}

	private static @Nullable Lists lists;
	private static @Nullable BuildConfig listsFor;

	private SiteWorld() {
	}

	// ================================================================ classification

	/**
	 * What stands at {@code pos} for a cell whose target is {@code target} (null: a cell to dig out).
	 */
	public static CellRules.World classify(ServerLevel level, BuildSite site, BlockPos pos, @Nullable BlockState target) {
		if (!level.isLoaded(pos)) {
			return CellRules.World.UNLOADED;
		}
		if (site.foreign(level, pos)) {
			return CellRules.World.FOREIGN;
		}
		BlockState world = level.getBlockState(pos);
		if (target != null && matches(world, target)) {
			return CellRules.World.EXACT;
		}
		if (site.isProtected(level, pos)) {
			return CellRules.World.PROTECTED;
		}
		if (world.isAir()) {
			return CellRules.World.AIR;
		}
		if (world.getBlock() instanceof LiquidBlock) {
			return CellRules.World.FLUID;
		}
		if (target != null && world.is(target.getBlock())) {
			return CellRules.World.SAME_BLOCK;
		}
		if (world.hasBlockEntity()) {
			return CellRules.World.CONTAINER;
		}
		if (world.canBeReplaced() && world.getFluidState().isEmpty()) {
			return CellRules.World.REPLACEABLE;
		}
		if (natural(level, pos, world)) {
			return CellRules.World.NATURAL;
		}
		return CellRules.World.BUILT;
	}

	/** Same block and same properties, but the volatile ones (builds.json5 → materials.ignoredProperties). */
	public static boolean matches(BlockState world, BlockState target) {
		if (world == target) {
			return true;
		}
		if (!world.is(target.getBlock())) {
			return false;
		}
		if (target.getBlock() instanceof LiquidBlock) {
			// A source is wanted: flowing water of the same fluid is not done.
			return world.getFluidState().isSource();
		}
		Set<String> ignored = lists().ignored();
		for (Property<?> property : target.getProperties()) {
			if (ignored.contains(property.getName())) {
				continue;
			}
			if (!world.hasProperty(property) || !world.getValue(property).equals(target.getValue(property))) {
				return false;
			}
		}
		return true;
	}

	/** A natural block of the terrain: the configured list (natural leaves, logs of natural trees), or a plant. */
	static boolean natural(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.hasBlockEntity()) {
			return false;
		}
		Lists current = lists();
		Block block = state.getBlock();
		boolean listed = current.naturalCache().computeIfAbsent(block, b -> {
			BlockState base = b.defaultBlockState();
			return current.natural().contains(b) || current.naturalTags().stream().anyMatch(base::is);
		});
		if (listed) {
			if (state.is(BlockTags.LEAVES)) {
				return state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT);
			}
			if (state.is(BlockTags.LOGS)) {
				return naturalLog(level, pos, state);
			}
			return true;
		}
		// Plants: no collision and not a fixture players place (torches, rails, redstone...).
		return state.getCollisionShape(level, pos).isEmpty() && !isFixture(state) && state.getFluidState().isEmpty();
	}

	/** A log of a natural tree: an upright trunk whose top log has natural (non-persistent) leaves around it. */
	private static boolean naturalLog(ServerLevel level, BlockPos pos, BlockState state) {
		if (!state.hasProperty(RotatedPillarBlock.AXIS) || state.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y) {
			return false;
		}
		BlockPos.MutableBlockPos cursor = pos.mutable();
		for (int i = 0; i < 32; i++) {
			BlockPos up = cursor.above();
			if (!level.isLoaded(up) || !level.getBlockState(up).is(state.getBlock())) {
				break;
			}
			cursor.set(up);
		}
		BlockPos top = cursor.immutable();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = -1; dy <= 2; dy++) {
					at.set(top.getX() + dx, top.getY() + dy, top.getZ() + dz);
					if (!level.isLoaded(at)) {
						continue;
					}
					BlockState leaves = level.getBlockState(at);
					if (leaves.is(BlockTags.LEAVES) && leaves.hasProperty(LeavesBlock.PERSISTENT) && !leaves.getValue(LeavesBlock.PERSISTENT)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Things without collision that players place: never taken for plants. */
	private static boolean isFixture(BlockState state) {
		Block block = state.getBlock();
		return block instanceof BaseTorchBlock || block instanceof RedstoneWireBlock || block instanceof DiodeBlock
				|| block instanceof LeverBlock || block instanceof TripWireBlock || block instanceof TripWireHookBlock
				|| block instanceof LadderBlock || state.is(BlockTags.RAILS) || state.is(BlockTags.BUTTONS)
				|| state.is(BlockTags.PRESSURE_PLATES) || state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.FENCE_GATES)
				|| state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.BANNERS)
				|| state.is(BlockTags.ALL_SIGNS) || block == Blocks.STRUCTURE_VOID || block == Blocks.LIGHT;
	}

	// ================================================================ readiness

	/** Can the target go at {@code pos} now (its own cell already free of anything that must not be touched)? */
	static Ready readyToPlace(ServerLevel level, BuildSite site, BlockPos pos, BlockState target, @Nullable Entity self) {
		if (target.getBlock() instanceof FallingBlock && FallingBlock.isFree(level.getBlockState(pos.below()))) {
			return Ready.FALLING;
		}
		if (!target.canSurvive(level, pos)) {
			return Ready.SUPPORT;
		}
		if (!target.getCollisionShape(level, pos).isEmpty() && occupied(level, pos, self)) {
			return Ready.OCCUPIED;
		}
		BlockPos partner = partnerPos(pos, target);
		if (partner != null) {
			BlockState partnerTarget = partnerState(target);
			CellRules.World world = classify(level, site, partner, partnerTarget);
			boolean free = switch (world) {
				case EXACT, AIR, REPLACEABLE, FLUID, NATURAL, SAME_BLOCK -> true;
				case BUILT -> site.project.replaceBuilt;
				default -> false;
			};
			if (!free || occupied(level, partner, self)) {
				return Ready.PARTNER;
			}
		}
		// A natural block replaced by something that does not hold up a falling block above it.
		if (!target.isCollisionShapeFullBlock(level, pos) && level.getBlockState(pos.above()).getBlock() instanceof FallingBlock
				&& !level.getBlockState(pos).isAir()) {
			return Ready.GRAVITY;
		}
		return Ready.OK;
	}

	/**
	 * Can the cell be dug out now: no falling block above, no fluid next to it (N, S, E, W, up). A fluid outside the
	 * site may be plugged ({@link #plugTarget}): the caller plugs then retries.
	 */
	static Ready readyToClear(ServerLevel level, BuildSite site, BlockPos pos) {
		if (level.getBlockState(pos.above()).getBlock() instanceof FallingBlock) {
			return Ready.GRAVITY;
		}
		for (Direction direction : AROUND) {
			BlockPos side = pos.relative(direction);
			if (level.isLoaded(side) && !level.getFluidState(side).isEmpty()) {
				return Ready.FLUID;
			}
		}
		return Ready.OK;
	}

	/** True when the cell itself holds a fluid (a cell to dig out full of water is filled with dirt first if wet around). */
	static boolean wet(ServerLevel level, BlockPos pos) {
		return !level.getFluidState(pos).isEmpty();
	}

	/**
	 * The fluid next to a cell to dig out that a builder may plug: outside the site's box (or in a cell of the box that
	 * stays as terrain), loaded, never in another guild's land nor a protected block. Null when there is none.
	 */
	static @Nullable BlockPos plugTarget(ServerLevel level, BuildSite site, BlockPos pos) {
		for (Direction direction : AROUND) {
			BlockPos side = pos.relative(direction);
			if (!level.isLoaded(side) || level.getFluidState(side).isEmpty()) {
				continue;
			}
			if (site.worksAt(side) || site.foreign(level, side) || site.isProtected(level, side)) {
				continue;
			}
			BlockState state = level.getBlockState(side);
			if (state.getBlock() instanceof LiquidBlock || state.canBeReplaced()) {
				return side;
			}
		}
		return null;
	}

	/** A living entity stands in the cell (the builder itself included unless it is {@code self}). */
	static boolean occupied(ServerLevel level, BlockPos pos, @Nullable Entity self) {
		AABB box = new AABB(pos);
		return !level.getEntitiesOfClass(LivingEntity.class, box, entity -> entity != self && entity.isAlive() && !entity.isSpectator()
				&& !(entity instanceof ArmorStand)).isEmpty();
	}

	/** The builder itself stands in the cell (it steps aside first). */
	static boolean selfInside(BlockPos pos, Entity self) {
		return self.getBoundingBox().intersects(new AABB(pos));
	}

	/** Item frame, painting, armor stand... on or in the block: it belongs to a build, never broken. */
	static boolean decorated(ServerLevel level, BlockPos pos) {
		return !level.getEntitiesOfClass(Entity.class, new AABB(pos).inflate(0.6),
				entity -> entity instanceof BlockAttachedEntity || entity instanceof ArmorStand).isEmpty();
	}

	// ================================================================ partners

	/** Cell of the other half placed with this state (door / tall plant top, bed head), null for single blocks. */
	static @Nullable BlockPos partnerPos(BlockPos pos, BlockState state) {
		if (BuildPlan.isLowerHalf(state)) {
			return pos.above();
		}
		if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT) {
			return pos.relative(state.getValue(BedBlock.FACING));
		}
		return null;
	}

	static BlockState partnerState(BlockState state) {
		if (BuildPlan.isLowerHalf(state)) {
			return state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
		}
		return state.setValue(BedBlock.PART, BedPart.HEAD);
	}

	// ================================================================ actions

	/** What breaking the block gives (the tool its kind of block wants), without breaking it. */
	static List<ItemStack> drops(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity breaker) {
		ItemStack tool = state.is(BlockTags.MINEABLE_WITH_AXE) ? AXE : state.is(BlockTags.MINEABLE_WITH_SHOVEL) ? SHOVEL
				: state.is(BlockTags.MINEABLE_WITH_HOE) ? HOE : PICKAXE;
		BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
		List<ItemStack> list = new ArrayList<>();
		for (ItemStack drop : Block.getDrops(state, level, pos, blockEntity, breaker, tool)) {
			if (!drop.isEmpty()) {
				list.add(drop);
			}
		}
		return list;
	}

	/**
	 * Sets the target (and the other half of a door or a bed) with the exact state, replacing whatever stood there
	 * (natural block, plant, fluid) in the same call; cosmetic block entity data applied after.
	 *
	 * @return the drops of what was replaced (empty when nothing dropped)
	 */
	static List<ItemStack> place(ServerLevel level, BlockPos pos, BlockState target, @Nullable CompoundTag cosmetic, @Nullable Entity worker) {
		List<ItemStack> dropped = new ArrayList<>();
		BlockState old = level.getBlockState(pos);
		if (!old.isAir() && !(old.getBlock() instanceof LiquidBlock) && !matches(old, target)) {
			dropped.addAll(drops(level, pos, old, worker));
			level.levelEvent(null, LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(old));
		}
		BlockPos partner = partnerPos(pos, target);
		if (partner != null) {
			BlockState partnerOld = level.getBlockState(partner);
			BlockState partnerTarget = partnerState(target);
			if (!partnerOld.isAir() && !(partnerOld.getBlock() instanceof LiquidBlock) && !matches(partnerOld, partnerTarget)) {
				dropped.addAll(drops(level, partner, partnerOld, worker));
			}
			level.setBlock(pos, target, PLACE_FLAGS);
			level.setBlock(partner, partnerTarget, PLACE_FLAGS);
		} else {
			level.setBlock(pos, target, PLACE_FLAGS);
		}
		if (cosmetic != null) {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (blockEntity != null) {
				try {
					blockEntity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), cosmetic));
					blockEntity.setChanged();
					level.sendBlockUpdated(pos, target, target, Block.UPDATE_CLIENTS);
				} catch (RuntimeException e) {
					// Unreadable text or patterns: the block stays, without them.
				}
			}
		}
		level.playSound(null, pos, target.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.9F, 0.9F + level.getRandom().nextFloat() * 0.2F);
		if (!target.isAir() && target.getFluidState().isEmpty()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, target), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					6, 0.25, 0.25, 0.25, 0.05);
		}
		return dropped;
	}

	/** Digs a cell out (normal updates): its drops, or nothing for a fluid. */
	static List<ItemStack> clear(ServerLevel level, BlockPos pos, @Nullable Entity worker) {
		BlockState old = level.getBlockState(pos);
		List<ItemStack> dropped = new ArrayList<>();
		if (old.isAir()) {
			return dropped;
		}
		if (!(old.getBlock() instanceof LiquidBlock)) {
			dropped.addAll(drops(level, pos, old, worker));
			level.levelEvent(null, LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK, pos, Block.getId(old));
		}
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		return dropped;
	}

	/** Fills a wet cell to dig out with dirt (natural: dug out normally later, once no fluid touches it). */
	static void fillWet(ServerLevel level, BlockPos pos) {
		level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
		level.playSound(null, pos, Blocks.DIRT.defaultBlockState().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
	}

	/** Plugs a fluid next to the site with a block (of the bag, else the free plug block when allowed). */
	static void plug(ServerLevel level, BlockPos pos, BlockState block) {
		level.setBlock(pos, block, Block.UPDATE_ALL);
		level.playSound(null, pos, block.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8F, 0.9F);
	}

	/** A plug block from the bag (taken out of it), or the free one, or null. */
	static @Nullable BlockState takePlug(List<ItemStack> bag) {
		Lists current = lists();
		for (int i = 0; i < bag.size(); i++) {
			ItemStack stack = bag.get(i);
			if (stack.isEmpty() || !current.plugItems().contains(stack.getItem()) || !(stack.getItem() instanceof BlockItem item)) {
				continue;
			}
			BlockState state = item.getBlock().defaultBlockState();
			stack.shrink(1);
			if (stack.isEmpty()) {
				bag.remove(i);
			}
			return state;
		}
		return BuildModule.config().terraform.freePlugBlocks ? current.plugBlock() : null;
	}

	/** The configured block of the foundations (under a bottom block that is not a full cube). */
	static BlockState configuredFoundation() {
		BlockState state = lists().foundation();
		return state == null ? Blocks.COBBLESTONE.defaultBlockState() : state;
	}

	/** Arm swing + small particles on the cell (dig or place feedback). */
	static void workEffect(ServerLevel level, BlockPos pos) {
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0.0);
	}

	// ================================================================ lists

	private static Lists lists() {
		BuildConfig config = BuildModule.config();
		Lists current = lists;
		if (current == null || listsFor != config) {
			current = compile(config);
			lists = current;
			listsFor = config;
		}
		return current;
	}

	private static Lists compile(BuildConfig config) {
		Set<Block> natural = new HashSet<>();
		List<TagKey<Block>> tags = new ArrayList<>();
		for (String value : config.terraform.naturalBlocks) {
			boolean tag = value.startsWith("#");
			Identifier id = parse(tag ? value.substring(1) : value);
			if (id == null) {
				continue;
			}
			if (tag) {
				tags.add(TagKey.create(Registries.BLOCK, id));
			} else if (BuiltInRegistries.BLOCK.containsKey(id)) {
				natural.add(BuiltInRegistries.BLOCK.getValue(id));
			}
		}
		Set<Item> plugItems = new HashSet<>();
		for (String value : config.terraform.plugItems) {
			Identifier id = parse(value);
			if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
				Item item = BuiltInRegistries.ITEM.getValue(id);
				if (item instanceof BlockItem blockItem && !(blockItem.getBlock() instanceof FallingBlock)) {
					plugItems.add(item);
				}
			}
		}
		BlockState plug = blockOf(config.terraform.plugBlock, Blocks.COBBLESTONE.defaultBlockState());
		BlockState foundation = blockOf(config.foundations.block, Blocks.COBBLESTONE.defaultBlockState());
		return new Lists(natural, tags, new IdentityHashMap<>(), Set.copyOf(config.materials.ignoredProperties), plugItems, plug, foundation);
	}

	private static BlockState blockOf(String id, BlockState fallback) {
		Identifier identifier = parse(id);
		if (identifier == null || !BuiltInRegistries.BLOCK.containsKey(identifier)) {
			return fallback;
		}
		BlockState state = BuiltInRegistries.BLOCK.getValue(identifier).defaultBlockState();
		return state.isAir() ? fallback : state;
	}

	private static @Nullable Identifier parse(String id) {
		try {
			String value = id.trim();
			return value.contains(":") ? Identifier.parse(value) : Identifier.withDefaultNamespace(value);
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Item of the fluid in a cell (bucket checks), for the menus. */
	static boolean isFluidSource(FluidState fluid) {
		return !fluid.isEmpty() && fluid.isSource();
	}
}
