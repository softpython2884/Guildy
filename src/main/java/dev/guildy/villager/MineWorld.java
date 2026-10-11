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
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
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
 * World side of real mining: what a block is for the rules ({@link MineBlock}, lists of villagers.json5), breaking a
 * natural block like a player with an iron pickaxe (real drops), filling holes / sealing fluids with blocks of the bag,
 * wall torches. Reads only loaded chunks.
 */
final class MineWorld implements MineRules.Grid {
	private static final ItemStack PICKAXE = new ItemStack(Items.IRON_PICKAXE);
	private static final byte OTHER = 0;
	private static final byte NATURAL = 1;
	private static final byte ORE = 2;

	/** Compiled block / item lists of a config instance (tags resolved per block, cached). */
	private record Lists(Set<Block> natural, List<TagKey<Block>> naturalTags, Set<Block> ores, List<TagKey<Block>> oreTags,
			Set<Item> fillers, Map<Block, Byte> kinds) {
	}

	private static @Nullable Lists lists;
	private static @Nullable VillagerConfig listsFor;

	private final ServerLevel level;
	private final Guild guild;
	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

	MineWorld(ServerLevel level, Guild guild) {
		this.level = level;
		this.guild = guild;
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
		return new Lists(natural, naturalTags, ores, oreTags, fillers, new IdentityHashMap<>());
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

	/** Filler blocks in the bag. */
	static int fillerCount(VillagerRecord record) {
		int count = 0;
		for (ItemStack stack : record.carried) {
			if (isFiller(stack)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/** Takes one filler block out of the bag, null when there is none. */
	static @Nullable BlockState takeFiller(VillagerRecord record) {
		Iterator<ItemStack> it = record.carried.iterator();
		while (it.hasNext()) {
			ItemStack stack = it.next();
			if (!isFiller(stack) || !(stack.getItem() instanceof BlockItem blockItem)) {
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
