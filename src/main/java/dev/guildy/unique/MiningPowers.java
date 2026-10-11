package dev.guildy.unique;

import dev.guildy.core.Safe;
import dev.guildy.core.Scheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Block powers: tree felling, 3x3 excavation, vein mining. Every extra block goes through the vanilla break pipeline of
 * the player ({@code ServerPlayerGameMode.destroyBlock}: Fabric break events and territory protection, tool damage,
 * drops, stats) and is checked first: never another guild's territory, never a heart, only the configured natural
 * blocks. Big jobs are spread over several ticks; they stop when the player switches item or leaves.
 */
final class MiningPowers {
	private static final List<TagKey<Block>> ORE_FAMILIES = List.of(tag("coal_ores"), tag("iron_ores"), tag("copper_ores"),
			tag("gold_ores"), tag("redstone_ores"), tag("lapis_ores"), tag("diamond_ores"), tag("emerald_ores"));
	/** True while a power breaks blocks: the break events of these blocks are not powers themselves. */
	private static boolean breaking;
	/** Players with a job spread over several ticks: one at a time (the power's natural cooldown). */
	private static final Set<UUID> JOBS = new HashSet<>();
	private static @Nullable List<String> excavateSource;
	private static Matcher excavateMatcher = Matcher.NONE;
	private static @Nullable List<String> veinSource;
	private static Matcher veinMatcher = Matcher.NONE;

	private MiningPowers() {
	}

	static boolean busy() {
		return breaking;
	}

	private static TagKey<Block> tag(String path) {
		return TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace(path));
	}

	// ================================================================== tree feller

	/** A log was broken with the Hache de l'Ancien Forestier: fell the rest of the tree if it is a natural one. */
	static void fellTree(ServerPlayer player, ServerLevel level, BlockPos origin, BlockState state, String uniqueId) {
		UniqueConfig.TreeFeller cfg = PowerUtil.cfg().treeFeller;
		if (cfg.sneakDisables && player.isShiftKeyDown() || !state.is(BlockTags.LOGS)) {
			return;
		}
		List<BlockPos> logs = BlockSearch.connected(origin, cfg.maxLogs, origin.getY(),
				pos -> level.isLoaded(pos) && level.getBlockState(pos).is(BlockTags.LOGS));
		if (logs.isEmpty()) {
			return;
		}
		List<BlockPos> tree = new ArrayList<>(logs);
		tree.add(origin);
		if (BlockSearch.countAround(tree, Math.max(1, cfg.minLeaves), pos -> level.isLoaded(pos) && naturalLeaves(level.getBlockState(pos)))
				< cfg.minLeaves) {
			return; // no natural leaves around: a build made of logs, not a tree
		}
		breakAll(player, level, logs, uniqueId, cfg.logsPerTick, s -> s.is(BlockTags.LOGS));
	}

	static boolean naturalLeaves(BlockState state) {
		return state.getBlock() instanceof LeavesBlock && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT);
	}

	// ================================================================== excavation

	/** A block was broken with the Pioche du Titan: dig the 3x3 plane around it (natural blocks only). */
	static void excavate(ServerPlayer player, ServerLevel level, BlockPos origin, BlockState state, String uniqueId) {
		UniqueConfig.Excavate cfg = PowerUtil.cfg().excavate;
		if (cfg.sneakDisables && player.isShiftKeyDown()) {
			return;
		}
		Matcher matcher = excavateMatcher(cfg.blocks);
		if (!matcher.test(state)) {
			return;
		}
		Direction.Axis axis = BlockSearch.diggingAxis(player.getXRot(), player.getYRot());
		List<BlockPos> targets = new ArrayList<>();
		for (BlockPos pos : BlockSearch.plane(origin, axis)) {
			if (level.isLoaded(pos) && matcher.test(level.getBlockState(pos))) {
				targets.add(pos);
			}
		}
		breakAll(player, level, targets, uniqueId, 8, matcher);
	}

	// ================================================================== vein miner

	/** An ore was broken with the Pic du Filon: mine the rest of the vein (same ore family). */
	static void veinMine(ServerPlayer player, ServerLevel level, BlockPos origin, BlockState state, String uniqueId) {
		UniqueConfig.VeinMiner cfg = PowerUtil.cfg().veinMiner;
		if (cfg.sneakDisables && player.isShiftKeyDown() || !veinMatcher(cfg.blocks).test(state)) {
			return;
		}
		Predicate<BlockState> family = family(state);
		List<BlockPos> vein = BlockSearch.connected(origin, cfg.maxBlocks, level.getMinY(),
				pos -> level.isLoaded(pos) && family.test(level.getBlockState(pos)));
		breakAll(player, level, vein, uniqueId, 16, family);
	}

	/** Deepslate and stone variants of an ore belong to the same vein. */
	static Predicate<BlockState> family(BlockState origin) {
		for (TagKey<Block> tag : ORE_FAMILIES) {
			if (origin.is(tag)) {
				return state -> state.is(tag);
			}
		}
		Block block = origin.getBlock();
		return state -> state.is(block);
	}

	// ================================================================== breaking

	/**
	 * Breaks the positions as the player, {@code perTick} per tick (the first batch right now), while the player still
	 * holds the same unique item; each block is re-checked (still matching, allowed) just before.
	 */
	private static void breakAll(ServerPlayer player, ServerLevel level, List<BlockPos> positions, String uniqueId, int perTick,
			Predicate<BlockState> stillMatches) {
		UUID owner = player.getUUID();
		if (positions.isEmpty() || JOBS.contains(owner)) {
			return;
		}
		ArrayDeque<BlockPos> queue = new ArrayDeque<>(positions);
		JOBS.add(owner);
		Runnable step = new Runnable() {
			@Override
			public void run() {
				if (player.hasDisconnected() || player.isRemoved() || player.level() != level
						|| !uniqueId.equals(UniqueItems.idOf(player.getMainHandItem()))) {
					JOBS.remove(owner);
					return;
				}
				for (int n = 0; n < perTick && !queue.isEmpty(); n++) {
					BlockPos pos = queue.poll();
					Safe.run("unique.break", () -> breakOne(player, level, pos, stillMatches));
				}
				if (queue.isEmpty()) {
					JOBS.remove(owner);
				} else {
					Scheduler.later("unique.break", 1, this);
				}
			}
		};
		step.run();
	}

	/** Server stop: pending jobs are dropped with the scheduler. */
	static void clear() {
		JOBS.clear();
	}

	private static void breakOne(ServerPlayer player, ServerLevel level, BlockPos pos, Predicate<BlockState> stillMatches) {
		if (!level.isLoaded(pos)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || !stillMatches.test(state) || state.getDestroySpeed(level, pos) < 0 || !PowerUtil.mayAffectBlock(player, level, pos)) {
			return;
		}
		breaking = true;
		try {
			player.gameMode.destroyBlock(pos);
		} finally {
			breaking = false;
		}
	}

	// ================================================================== block lists

	private static Matcher excavateMatcher(List<String> ids) {
		if (ids != excavateSource) {
			excavateSource = ids;
			excavateMatcher = Matcher.of(ids);
		}
		return excavateMatcher;
	}

	private static Matcher veinMatcher(List<String> ids) {
		if (ids != veinSource) {
			veinSource = ids;
			veinMatcher = Matcher.of(ids);
		}
		return veinMatcher;
	}

	/** Blocks and block tags from the config ("minecraft:calcite", "#minecraft:ores"); unknown ids are ignored. */
	record Matcher(Set<Block> blocks, List<TagKey<Block>> tags) implements Predicate<BlockState> {
		static final Matcher NONE = new Matcher(Set.of(), List.of());

		static Matcher of(List<String> ids) {
			Set<Block> blocks = new HashSet<>();
			List<TagKey<Block>> tags = new ArrayList<>();
			for (String id : ids) {
				boolean isTag = id.startsWith("#");
				Identifier identifier = Identifier.tryParse(isTag ? id.substring(1) : id);
				if (identifier == null) {
					continue;
				}
				if (isTag) {
					tags.add(TagKey.create(Registries.BLOCK, identifier));
				} else {
					BuiltInRegistries.BLOCK.get(identifier).ifPresent(holder -> blocks.add(holder.value()));
				}
			}
			return new Matcher(Set.copyOf(blocks), List.copyOf(tags));
		}

		@Override
		public boolean test(BlockState state) {
			if (blocks.contains(state.getBlock())) {
				return true;
			}
			for (TagKey<Block> tag : tags) {
				if (state.is(tag)) {
					return true;
				}
			}
			return false;
		}
	}
}
