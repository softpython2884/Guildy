package dev.guildy.villager;

import dev.guildy.data.Guild;
import dev.guildy.data.GuildManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * World side of real mining: what a block is for the rules ({@link MineBlock}, lists of villagers.json5), which blocks
 * are collected on purpose (ores, "objectifs prioritaires": {@link Wanted}), breaking a natural block like a player
 * with an iron pickaxe (real drops), filling holes / sealing fluids with blocks of the bag, wall torches. Reads only
 * loaded chunks.
 */
final class MineWorld implements MineRules.Grid, MineRules.Targets {
	private static final ItemStack PICKAXE = new ItemStack(Items.IRON_PICKAXE);
	private static final byte OTHER = 0;
	private static final byte NATURAL = 1;
	private static final byte ORE = 2;
	/** Items a block was seen to drop (iron pickaxe), per block. */
	private static final Map<Block, Set<Item>> DROPS = new IdentityHashMap<>();

	/** Compiled block / item lists of a config instance (tags resolved per block, cached). */
	private record Lists(Set<Block> natural, List<TagKey<Block>> naturalTags, Set<Block> ores, List<TagKey<Block>> oreTags,
			Set<Item> fillers, Map<Block, Byte> kinds, Set<Item> keep, List<TagKey<Item>> keepTags, Set<Block> clear,
			List<TagKey<Block>> clearTags, Map<Block, Boolean> clearable) {
	}

	private static @Nullable Lists lists;
	private static @Nullable VillagerConfig listsFor;

	private final ServerLevel level;
	private final Guild guild;
	private final @Nullable Wanted wanted;
	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

	MineWorld(ServerLevel level, Guild guild) {
		this(level, guild, null);
	}

	MineWorld(ServerLevel level, Guild guild, @Nullable Wanted wanted) {
		this.level = level;
		this.guild = guild;
		this.wanted = wanted;
	}

	/**
	 * Wanted items of one miner ("objectifs prioritaires", {@link Wishlist}) and the natural blocks dropping them (cached
	 * per block). The bulk rock (stone, deepslate) is never followed as a vein even when its drop is wanted: the tunnels
	 * bring plenty of it.
	 */
	static final class Wanted {
		private static final Set<Block> BULK = Set.of(Blocks.STONE, Blocks.DEEPSLATE);
		final Set<Item> items;
		private final Map<Block, Boolean> blocks = new IdentityHashMap<>();

		Wanted(Set<Item> items) {
			this.items = Set.copyOf(items);
		}

		boolean isEmpty() {
			return items.isEmpty();
		}

		boolean item(ItemStack stack) {
			return !stack.isEmpty() && items.contains(stack.getItem());
		}

		private boolean block(ServerLevel level, BlockPos pos, BlockState state) {
			if (items.isEmpty()) {
				return false;
			}
			Block block = state.getBlock();
			Boolean known = blocks.get(block);
			if (known == null) {
				known = !BULK.contains(block) && (items.contains(block.asItem()) || drops(level, pos, state).stream().anyMatch(items::contains));
				blocks.put(block, known);
			}
			return known;
		}
	}

	/** Items the block drops when mined (sampled once per block with an iron pickaxe). */
	private static Set<Item> drops(ServerLevel level, BlockPos pos, BlockState state) {
		Block block = state.getBlock();
		Set<Item> items = DROPS.get(block);
		if (items == null) {
			items = new HashSet<>();
			for (ItemStack drop : Block.getDrops(state, level, pos.immutable(), null, null, PICKAXE)) {
				if (!drop.isEmpty()) {
					items.add(drop.getItem());
				}
			}
			DROPS.put(block, items);
		}
		return items;
	}

	// ------------------------------------------------------------------ targets

	/** {@link MineRules#ORE_KEY} for ores, a key per kind of wanted natural block, else 0. */
	@Override
	public int key(int x, int y, int z) {
		cursor.set(x, y, z);
		if (!level.isLoaded(cursor)) {
			return 0;
		}
		BlockState state = level.getBlockState(cursor);
		MineBlock kind = classify(state);
		if (kind == MineBlock.ORE) {
			return MineRules.ORE_KEY;
		}
		if ((kind == MineBlock.NATURAL || kind == MineBlock.FALLING) && wanted != null && wanted.block(level, cursor, state)) {
			return 2 + BuiltInRegistries.BLOCK.getId(state.getBlock());
		}
		return 0;
	}

	/** The block at {@code pos} is an ore, or drops a wanted item. */
	boolean target(BlockPos pos) {
		return key(pos.getX(), pos.getY(), pos.getZ()) != 0;
	}

	/** The block at {@code pos} is a wanted (non-ore) block. */
	boolean wantedBlock(BlockPos pos) {
		return key(pos.getX(), pos.getY(), pos.getZ()) > MineRules.ORE_KEY;
	}

	// ------------------------------------------------------------------ grid

	@Override
	public MineBlock at(int x, int y, int z) {
		cursor.set(x, y, z);
		if (!level.isLoaded(cursor)) {
			return MineBlock.UNLOADED;
		}
		return classify(level.getBlockState(cursor));
	}

	@Override
	public boolean foreign(int x, int y, int z) {
		Guild owner = GuildManager.at(level, cursor.set(x, y, z));
		return owner != null && owner != guild;
	}

	@Override
	public boolean sturdy(int x, int y, int z) {
		cursor.set(x, y, z);
		if (!level.isLoaded(cursor)) {
			return false;
		}
		BlockState state = level.getBlockState(cursor);
		return state.isFaceSturdy(level, cursor, Direction.UP) && !state.is(Blocks.MAGMA_BLOCK) && !state.is(BlockTags.CAMPFIRES);
	}

	/** Rule category of a block state (see {@link MineBlock}). */
	MineBlock classify(BlockState state) {
		FluidState fluid = state.getFluidState();
		if (!fluid.isEmpty()) {
			if (!state.canBeReplaced()) {
				return MineBlock.WET;
			}
			return fluid.is(FluidTags.LAVA) ? MineBlock.LAVA : MineBlock.WATER;
		}
		if (state.isAir()) {
			return MineBlock.AIR;
		}
		if (state.hasBlockEntity()) {
			return MineBlock.PROTECTED;
		}
		if (state.getCollisionShape(level, cursor).isEmpty()) {
			return isFixture(state) ? MineBlock.FIXTURE : MineBlock.AIR;
		}
		return switch (kind(state.getBlock())) {
			case ORE -> MineBlock.ORE;
			case NATURAL -> state.getBlock() instanceof FallingBlock ? MineBlock.FALLING : MineBlock.NATURAL;
			default -> MineBlock.PROTECTED;
		};
	}

	/** Things without collision that players place (never broken, never undermined); other ones are plants. */
	private static boolean isFixture(BlockState state) {
		Block block = state.getBlock();
		return block instanceof BaseTorchBlock || block instanceof RedstoneWireBlock || block instanceof DiodeBlock
				|| block instanceof LeverBlock || block instanceof TripWireBlock || block instanceof TripWireHookBlock
				|| block instanceof LadderBlock || state.is(BlockTags.RAILS) || state.is(BlockTags.BUTTONS)
				|| state.is(BlockTags.PRESSURE_PLATES) || state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.FENCE_GATES)
				|| state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS);
	}

	// ------------------------------------------------------------------ lists

	private static Lists lists() {
		VillagerConfig config = VillagerModule.config();
		Lists current = lists;
		if (current == null || listsFor != config) {
			current = compile(config.realMining);
			lists = current;
			listsFor = config;
		}
		return current;
	}

	private static Lists compile(VillagerConfig.RealMining real) {
		Set<Block> natural = new HashSet<>();
		List<TagKey<Block>> naturalTags = new ArrayList<>();
		readBlocks(real.naturalBlocks, natural, naturalTags);
		Set<Block> ores = new HashSet<>();
		List<TagKey<Block>> oreTags = new ArrayList<>();
		readBlocks(real.oreBlocks, ores, oreTags);
		Set<Item> fillers = new HashSet<>();
		for (String id : real.fillerItems) {
			Identifier identifier = parse(id);
			if (identifier != null && BuiltInRegistries.ITEM.containsKey(identifier)) {
				Item item = BuiltInRegistries.ITEM.getValue(identifier);
				if (item instanceof BlockItem blockItem && !(blockItem.getBlock() instanceof FallingBlock)) {
					fillers.add(item);
				}
			}
		}
		Set<Item> keep = new HashSet<>();
		List<TagKey<Item>> keepTags = new ArrayList<>();
		for (String value : real.quarryKeep) {
			boolean tag = value.startsWith("#");
			Identifier identifier = parse(tag ? value.substring(1) : value);
			if (identifier == null) {
				continue;
			}
			if (tag) {
				keepTags.add(TagKey.create(Registries.ITEM, identifier));
			} else if (BuiltInRegistries.ITEM.containsKey(identifier)) {
				keep.add(BuiltInRegistries.ITEM.getValue(identifier));
			}
		}
		Set<Block> clear = new HashSet<>();
		List<TagKey<Block>> clearTags = new ArrayList<>();
		readBlocks(real.quarrySurfaceClear, clear, clearTags);
		return new Lists(natural, naturalTags, ores, oreTags, fillers, new IdentityHashMap<>(), keep, keepTags, clear, clearTags,
				new IdentityHashMap<>());
	}

	private static void readBlocks(List<String> ids, Set<Block> blocks, List<TagKey<Block>> tags) {
		for (String value : ids) {
			boolean tag = value.startsWith("#");
			Identifier identifier = parse(tag ? value.substring(1) : value);
			if (identifier == null) {
				continue;
			}
			if (tag) {
				tags.add(TagKey.create(Registries.BLOCK, identifier));
			} else if (BuiltInRegistries.BLOCK.containsKey(identifier)) {
				blocks.add(BuiltInRegistries.BLOCK.getValue(identifier));
			}
		}
	}

	private static @Nullable Identifier parse(String id) {
		try {
			String value = id.trim();
			return value.contains(":") ? Identifier.parse(value) : Identifier.withDefaultNamespace(value);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static byte kind(Block block) {
		Lists current = lists();
		return current.kinds().computeIfAbsent(block, b -> {
			BlockState state = b.defaultBlockState();
			if (current.ores().contains(b) || current.oreTags().stream().anyMatch(state::is)) {
				return ORE;
			}
			if (current.natural().contains(b) || current.naturalTags().stream().anyMatch(state::is)) {
				return NATURAL;
			}
			return OTHER;
		});
	}

	static boolean isFiller(ItemStack stack) {
		return !stack.isEmpty() && lists().fillers().contains(stack.getItem());
	}

	/** Quarry: an item kept in the bag (realMining.quarryKeep). */
	static boolean quarryKeep(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		Lists current = lists();
		if (current.keep().contains(stack.getItem())) {
			return true;
		}
		for (TagKey<Item> tag : current.keepTags()) {
			if (stack.is(tag)) {
				return true;
			}
		}
		return false;
	}

	/** Quarry: extra vegetation cleared from the columns (realMining.quarrySurfaceClear). */
	private static boolean surfaceClear(BlockState state) {
		Lists current = lists();
		return current.clearable().computeIfAbsent(state.getBlock(), b -> {
			BlockState base = b.defaultBlockState();
			return current.clear().contains(b) || current.clearTags().stream().anyMatch(base::is);
		});
	}

	/** A natural block (realMining.naturalBlocks) that does not fall: a filler the next bench can dig again. */
	private static boolean naturalFiller(Block block) {
		return kind(block) == NATURAL && !(block instanceof FallingBlock);
	}

	/** Filler blocks in the bag. */
	static int fillerCount(VillagerRecord record) {
		return fillerCount(record, stack -> false);
	}

	/** Filler blocks in the bag, but the stacks {@code keep} protects (wanted items are never used as filler). */
	static int fillerCount(VillagerRecord record, Predicate<ItemStack> keep) {
		int count = 0;
		for (ItemStack stack : record.carried) {
			if (isFiller(stack) && !keep.test(stack)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/** Takes one filler block out of the bag, null when there is none. */
	static @Nullable BlockState takeFiller(VillagerRecord record) {
		return takeFiller(record, stack -> false);
	}

	/** Takes one filler block out of the bag (never a stack {@code keep} protects), null when there is none. */
	static @Nullable BlockState takeFiller(VillagerRecord record, Predicate<ItemStack> keep) {
		Iterator<ItemStack> it = record.carried.iterator();
		while (it.hasNext()) {
			ItemStack stack = it.next();
			if (!isFiller(stack) || keep.test(stack) || !(stack.getItem() instanceof BlockItem blockItem)) {
				continue;
			}
			BlockState state = blockItem.getBlock().defaultBlockState();
			stack.shrink(1);
			if (stack.isEmpty()) {
				it.remove();
			}
			GuildManager.markDirty();
			return state;
		}
		return null;
	}

	/**
	 * Quarry: a filler block for the quarry square, that the bench below can dig again: a natural one of the bag, else a
	 * free stone block when {@code free} (realMining.freeFloorBlocks), else any filler of the bag (that column will then
	 * stay as a support column). Never a stack {@code keep} protects. Null when there is none.
	 */
	static @Nullable BlockState takeQuarryFiller(VillagerRecord record, Predicate<ItemStack> keep, boolean free) {
		Iterator<ItemStack> it = record.carried.iterator();
		while (it.hasNext()) {
			ItemStack stack = it.next();
			if (!isFiller(stack) || keep.test(stack) || !(stack.getItem() instanceof BlockItem blockItem) || !naturalFiller(blockItem.getBlock())) {
				continue;
			}
			BlockState state = blockItem.getBlock().defaultBlockState();
			stack.shrink(1);
			if (stack.isEmpty()) {
				it.remove();
			}
			GuildManager.markDirty();
			return state;
		}
		if (free) {
			return Blocks.STONE.defaultBlockState();
		}
		return takeFiller(record, keep);
	}

	/** Takes one item of {@code item} out of the bag. */
	static boolean take(VillagerRecord record, Item item) {
		Iterator<ItemStack> it = record.carried.iterator();
		while (it.hasNext()) {
			ItemStack stack = it.next();
			if (stack.is(item)) {
				stack.shrink(1);
				if (stack.isEmpty()) {
					it.remove();
				}
				GuildManager.markDirty();
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ actions

	/** Item frame, painting, armor stand... hanging on or standing on the block: it belongs to a build, never broken. */
	boolean decorated(BlockPos pos) {
		return !level.getEntitiesOfClass(Entity.class, new AABB(pos).inflate(0.6),
				entity -> entity instanceof BlockAttachedEntity || entity instanceof ArmorStand || entity instanceof Mannequin).isEmpty();
	}

	/** Breaks a natural block like a player with an iron pickaxe: particles and sound, drops returned (not spawned). */
	List<ItemStack> breakBlock(BlockPos pos, Entity breaker) {
		BlockState state = level.getBlockState(pos);
		List<ItemStack> drops = Block.getDrops(state, level, pos, null, breaker, PICKAXE);
		level.destroyBlock(pos, false, breaker);
		return drops;
	}

	/** Places a block (fills a hole, seals a fluid). */
	void place(BlockPos pos, BlockState state) {
		level.setBlockAndUpdate(pos, state);
		level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8f, 0.9f);
	}

	/** Quarry: a standing torch on the bench floor at {@code pos} (air above a sturdy floor). */
	boolean placeFloorTorch(BlockPos pos) {
		if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) {
			return false;
		}
		BlockState torch = Blocks.TORCH.defaultBlockState();
		if (!torch.canSurvive(level, pos)) {
			return false;
		}
		place(pos, torch);
		return true;
	}

	/** Removes a plant without drops (grass, flowers... on a column being dug). */
	void removePlant(BlockPos pos) {
		level.destroyBlock(pos, false);
	}

	/**
	 * A living entity (miner, player, animal...) stands in the {@code height} blocks from {@code pos} up: the block under
	 * {@code pos} is its floor, never dug away. Null when there is none.
	 */
	@Nullable LivingEntity standing(BlockPos pos, int height, Entity self) {
		AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + height, pos.getZ() + 1);
		List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box, entity -> entity != self && entity.isAlive()
				&& !entity.isSpectator());
		return found.isEmpty() ? null : found.getFirst();
	}

	/** Quarry: what a block of a column is ({@link Quarry#stack}); {@code quarry}: its torch spots. */
	Quarry.SurfaceGrid surface(Quarry quarry) {
		return new Quarry.SurfaceGrid() {
			private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();

			@Override
			public Quarry.Surface surface(int x, int y, int z) {
				at.set(x, y, z);
				if (!level.isLoaded(at)) {
					return Quarry.Surface.UNLOADED;
				}
				BlockState state = level.getBlockState(at);
				if (state.isAir()) {
					return Quarry.Surface.AIR;
				}
				if (!state.getFluidState().isEmpty()) {
					return Quarry.Surface.FLUID;
				}
				Block block = state.getBlock();
				if (block == Blocks.TORCH || block == Blocks.WALL_TORCH) {
					return block == Blocks.TORCH && quarry.torchSpot(x, z) ? Quarry.Surface.TORCH : Quarry.Surface.LIGHT;
				}
				if (state.hasBlockEntity()) {
					return Quarry.Surface.OTHER;
				}
				if (surfaceClear(state)) {
					return Quarry.Surface.VEGETATION;
				}
				if (state.is(BlockTags.LEAVES)) {
					return naturalLeaves(state) ? Quarry.Surface.LEAVES : Quarry.Surface.OTHER;
				}
				if (state.is(BlockTags.LOGS)) {
					return state.hasProperty(RotatedPillarBlock.AXIS) && state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y
							? Quarry.Surface.LOG : Quarry.Surface.OTHER;
				}
				if (state.getCollisionShape(level, at).isEmpty()) {
					return isFixture(state) ? Quarry.Surface.OTHER : Quarry.Surface.PLANT;
				}
				return switch (kind(block)) {
					case ORE, NATURAL -> block instanceof FallingBlock ? Quarry.Surface.FALLING : Quarry.Surface.NATURAL;
					default -> Quarry.Surface.OTHER;
				};
			}

			@Override
			public boolean leavesNear(int x, int y, int z) {
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						for (int dy = -1; dy <= 2; dy++) {
							at.set(x + dx, y + dy, z + dz);
							if (level.isLoaded(at) && naturalLeaves(level.getBlockState(at))) {
								return true;
							}
						}
					}
				}
				return false;
			}
		};
	}

	private static boolean naturalLeaves(BlockState state) {
		return state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT);
	}

	/** Wall torch at {@code pos} (air), hung on a sturdy side accepted by {@code wall} (never a block still to dig). */
	boolean placeTorch(BlockPos pos, Predicate<BlockPos> wall) {
		if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) {
			return false;
		}
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			if (!wall.test(pos.relative(facing.getOpposite()))) {
				continue;
			}
			BlockState torch = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, facing);
			if (torch.canSurvive(level, pos)) {
				place(pos, torch);
				return true;
			}
		}
		return false;
	}
}
