package dev.guildy.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeaconBeamBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * The ruin structures (data/guildy/structure/ruin, worldgen/structure/ruin): every template loads and honours
 * the ruin contract (size budget, exactly one guildy:boss DATA marker, minions, room guards, loot tables on every
 * container), and every worldgen structure generates and places without error (like /place structure) in its
 * dimension. The grand temple carries a working beacon: a full iron base, nothing opaque above it.
 * Natural generation is kept out of the GameTest world by the test-only world preset override
 * (data/minecraft/worldgen/world_preset/flat_all_dimensions.json in the test resources).
 */
public class RuinTemplateGameTests {
	static final String[] OVERWORLD = {"crypte_oubliee", "tour_du_mage", "ziggourat_des_sables", "bastion_de_givre", "temple_maya",
		"grand_temple_maya"};
	static final String[] NETHER = {"autel_infernal", "citadelle_des_ames"};
	/** The landmarks allowed to reach 64 x 64. */
	static final List<String> BIG = List.of("bastion_de_givre", "temple_maya", "citadelle_des_ames");
	/** The grand temple: up to 116 x 116 x 128 (its start piece stays within the 8 chunks a structure start reaches). */
	static final String GRAND = "grand_temple_maya";
	/** Loot table of the decorated pots of the ruins (one item each, broken or not). */
	static final Identifier POT_LOOT = Identifier.fromNamespaceAndPath("guildy", "pots/ruin");
	/** Random per run (the test world is reused): far from the other scenarios (x > 40000, z = 8). */
	private static final int BASE_X = -120000 - (int) (Math.random() * 400) * 2048;
	private static final AtomicInteger SLOTS = new AtomicInteger();

	/**
	 * Template ids of a ruin grouped by variant: the procedural one (ruin/<id> or ruin/<id>/surface + below) and the
	 * variants converted from schematics (ruin/<id>/<variant> or ruin/<id>/<variant>/surface + below).
	 */
	static Map<String, List<Identifier>> variants(StructureTemplateManager manager, String id) {
		String root = "ruin/" + id;
		Map<String, List<Identifier>> out = new TreeMap<>();
		manager.listTemplates()
			.filter(t -> t.getNamespace().equals("guildy") && (t.getPath().equals(root) || t.getPath().startsWith(root + "/")))
			.forEach(t -> {
				String path = t.getPath();
				String key = path.endsWith("/surface") || path.endsWith("/below") ? path.substring(0, path.lastIndexOf('/')) : path;
				out.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
			});
		return out;
	}

	@GameTest(maxTicks = 100)
	public void ruinTemplatesHonourTheContract(GameTestHelper helper) {
		StructureTemplateManager manager = helper.getLevel().getStructureTemplateManager();
		List<String> all = new ArrayList<>(List.of(OVERWORLD));
		all.addAll(List.of(NETHER));
		for (String id : all) {
			boolean nether = List.of(NETHER).contains(id);
			Map<String, List<Identifier>> variants = variants(manager, id);
			helper.assertTrue(variants.containsKey("ruin/" + id), "ruin " + id + " has its templates, found " + variants.keySet());
			for (Map.Entry<String, List<Identifier>> variant : variants.entrySet()) {
				int footprint = id.equals(GRAND) ? 116 : BIG.contains(id) ? 64 : 48;
				checkVariant(helper, manager, variant.getKey(), variant.getValue(), footprint, id.equals(GRAND) ? 128 : 64, nether);
			}
		}
		helper.succeed();
	}

	/**
	 * One variant: size budget, one boss marker, 2-8 minions, 2-8 room guards, one treasure, loot tables on every container
	 * (chests, barrels, decorated pots).
	 */
	private static void checkVariant(GameTestHelper helper, StructureTemplateManager manager, String name, List<Identifier> ids,
		int footprint, int maxHeight, boolean nether) {
		helper.assertTrue(ids.size() == 1 || ids.size() == 2, name + " has one template or a surface + below pair, got " + ids);
		int bosses = 0;
		int minions = 0;
		int guards = 0;
		int height = 0;
		int treasure = 0;
		for (Identifier templateId : ids) {
			Optional<StructureTemplate> found = manager.get(templateId);
			helper.assertTrue(found.isPresent(), "template " + templateId + " loads");
			StructureTemplate template = found.get();
			Vec3i size = template.getSize();
			helper.assertTrue(size.getX() <= footprint && size.getZ() <= footprint, templateId + " footprint " + size + " within " + footprint);
			height += size.getY();
			StructurePlaceSettings settings = new StructurePlaceSettings();
			for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, settings, Blocks.STRUCTURE_BLOCK)) {
				CompoundTag nbt = info.nbt();
				helper.assertTrue(nbt != null && "DATA".equals(nbt.getStringOr("mode", "")), "structure blocks are DATA markers in " + templateId);
				String meta = nbt.getStringOr("metadata", "");
				if (meta.equals("guildy:boss")) {
					bosses++;
				} else if (meta.equals("guildy:minion")) {
					minions++;
				} else if (meta.equals("guildy:guard")) {
					guards++;
				}
			}
			for (Block container : new Block[]{Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL}) {
				for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, settings, container)) {
					String table = info.nbt() == null ? "" : info.nbt().getStringOr("LootTable", "");
					String prefix = nether ? "guildy:chests/ruin/nether_" : "guildy:chests/ruin/";
					helper.assertTrue(table.startsWith(prefix), templateId + " container at " + info.pos() + " has a ruin loot table, got '" + table + "'");
					if (table.endsWith("treasure")) {
						treasure++;
					}
				}
			}
			for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, settings, Blocks.DECORATED_POT)) {
				String table = info.nbt() == null ? "" : info.nbt().getStringOr("LootTable", "");
				helper.assertTrue(table.startsWith(POT_LOOT.toString()), templateId + " decorated pot at " + info.pos()
					+ " has a ruin pot loot table, got '" + table + "'");
			}
			helper.assertTrue(template.filterBlocks(BlockPos.ZERO, settings, Blocks.COMMAND_BLOCK).isEmpty()
				&& template.filterBlocks(BlockPos.ZERO, settings, Blocks.BARRIER).isEmpty(), templateId + " has no command block / barrier");
		}
		helper.assertTrue(height <= maxHeight, name + " height " + height + " <= " + maxHeight);
		helper.assertTrue(bosses == 1, name + " has exactly one guildy:boss marker, got " + bosses);
		helper.assertTrue(minions >= 2 && minions <= 8, name + " has 2-8 minion markers, got " + minions);
		helper.assertTrue(guards >= 2 && guards <= 8, name + " has 2-8 guard markers, got " + guards);
		helper.assertTrue(treasure == 1, name + " has one treasure container, got " + treasure);
	}

	@GameTest(maxTicks = 200)
	public void crypteOublieeGenerates(GameTestHelper helper) {
		place(helper, "crypte_oubliee", Level.OVERWORLD);
	}

	@GameTest(maxTicks = 200)
	public void tourDuMageGenerates(GameTestHelper helper) {
		place(helper, "tour_du_mage", Level.OVERWORLD);
	}

	@GameTest(maxTicks = 200)
	public void ziggouratDesSablesGenerates(GameTestHelper helper) {
		place(helper, "ziggourat_des_sables", Level.OVERWORLD);
	}

	@GameTest(maxTicks = 200)
	public void bastionDeGivreGenerates(GameTestHelper helper) {
		place(helper, "bastion_de_givre", Level.OVERWORLD);
	}

	@GameTest(maxTicks = 200)
	public void templeMayaGenerates(GameTestHelper helper) {
		place(helper, "temple_maya", Level.OVERWORLD);
	}

	/** The grand temple places like the others and its beacon works: full iron base, nothing opaque above. */
	@GameTest(maxTicks = 400)
	public void grandTempleMayaGeneratesWithItsBeacon(GameTestHelper helper) {
		StructureStart start = placeStart(helper, GRAND, Level.OVERWORLD);
		ServerLevel level = helper.getLevel().getServer().getLevel(Level.OVERWORLD);
		BoundingBox box = start.getBoundingBox();
		List<BlockPos> beacons = new ArrayList<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int y = box.minY(); y <= box.maxY(); y++) {
					if (level.getBlockState(pos.set(x, y, z)).is(Blocks.BEACON)) {
						beacons.add(pos.immutable());
					}
				}
			}
		}
		helper.assertTrue(beacons.size() == 1, "the grand temple has one beacon, got " + beacons);
		BlockPos beacon = beacons.getFirst();
		for (int step = 1; step <= 4; step++) {
			for (int x = -step; x <= step; x++) {
				for (int z = -step; z <= step; z++) {
					BlockPos base = beacon.offset(x, -step, z);
					helper.assertTrue(level.getBlockState(base).is(BlockTags.BEACON_BASE_BLOCKS),
						"beacon base level " + step + " complete at " + base + ", got " + level.getBlockState(base));
				}
			}
		}
		for (int y = beacon.getY() + 1; y <= level.getMaxY(); y++) {
			BlockState above = level.getBlockState(pos.set(beacon.getX(), y, beacon.getZ()));
			helper.assertTrue(above.getBlock() instanceof BeaconBeamBlock || above.getLightDampening() < 15,
				"open sky above the beacon, blocked at y " + y + " by " + above);
		}
		int top = start.getPieces().stream().mapToInt(piece -> piece.getBoundingBox().maxY()).max().orElseThrow();
		helper.assertTrue(beacon.getY() >= top - 3, "the beacon crowns the temple (" + beacon + ", top of the pieces " + top + ")");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void autelInfernalGeneratesInTheNether(GameTestHelper helper) {
		place(helper, "autel_infernal", Level.NETHER);
	}

	@GameTest(maxTicks = 200)
	public void citadelleDesAmesGeneratesInTheNether(GameTestHelper helper) {
		place(helper, "citadelle_des_ames", Level.NETHER);
	}

	/**
	 * The grand temple keeps the water where it was built. Its basement is dug 18 layers deep, under the water table of
	 * most jungles: it is placed here into flooded ground (water filling the whole volume of its "below" piece before
	 * the placement, like an aquifer) and the fluids tick. Nothing of that water may get in: no block waterlogged by the
	 * placement (the structure ignores the world's water), no gap in the masonry around the rooms, so no flowing water
	 * anywhere in the basement (the hall of the Celestial Jaguar, its stairs and galleries stay dry; the cenote, its
	 * canal and the gallery of waters are still water). Every decorated pot carries the ruin pot loot table.
	 */
	@GameTest(maxTicks = 600)
	public void grandTempleMayaKeepsTheWaterTableOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().getLevel(Level.OVERWORLD);
		BoundingBox[] basement = new BoundingBox[1];
		List<ChunkPos> forced = new ArrayList<>();
		StructureStart start = placeStart(helper, GRAND, Level.OVERWORLD, s -> {
			BoundingBox below = s.getPieces().stream().map(StructurePiece::getBoundingBox)
				.min(java.util.Comparator.comparingInt(BoundingBox::minY)).orElseThrow();
			basement[0] = below;
			BoundingBox box = s.getBoundingBox();
			ChunkPos.rangeClosed(ChunkPos.containing(new BlockPos(box.minX(), 0, box.minZ())),
				ChunkPos.containing(new BlockPos(box.maxX(), 0, box.maxZ()))).forEach(c -> {
					level.setChunkForced(c.x(), c.z(), true);
					forced.add(c);
				});
			BlockState water = Blocks.WATER.defaultBlockState();
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
			for (int x = below.minX(); x <= below.maxX(); x++) {
				for (int z = below.minZ(); z <= below.maxZ(); z++) {
					for (int y = below.minY(); y <= below.maxY(); y++) {
						level.setBlock(p.set(x, y, z), water, Block.UPDATE_CLIENTS);
					}
				}
			}
		});
		StructureTemplateManager manager = level.getStructureTemplateManager();
		int expectedPots = 0;
		for (Identifier templateId : variants(manager, GRAND).get("ruin/" + GRAND)) {
			expectedPots += manager.get(templateId).orElseThrow()
				.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.DECORATED_POT).size();
		}
		int pots = expectedPots;
		helper.runAfterDelay(160, () -> {
			BoundingBox below = basement[0];
			List<String> flowing = new ArrayList<>();
			List<String> waterlogged = new ArrayList<>();
			int sources = 0;
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
			for (int x = below.minX(); x <= below.maxX(); x++) {
				for (int z = below.minZ(); z <= below.maxZ(); z++) {
					for (int y = below.minY(); y <= below.maxY(); y++) {
						BlockState state = level.getBlockState(p.set(x, y, z));
						if (state.getFluidState().isEmpty()) {
							continue;
						}
						if (!state.getFluidState().isSource()) {
							flowing.add(p.toShortString());
						} else if (state.hasProperty(BlockStateProperties.WATERLOGGED) && !state.is(Blocks.SEA_PICKLE)) {
							waterlogged.add(p.toShortString() + " " + state.getBlock().getDescriptionId());
						} else {
							sources++;
						}
					}
				}
			}
			// the arena (29 x 29 around the guardian's marker): its floor and the layer above stay dry
			BlockPos boss = bossMarkers(level, start).getFirst();
			List<String> wetArena = new ArrayList<>();
			for (int dx = -14; dx <= 14; dx++) {
				for (int dz = -14; dz <= 14; dz++) {
					for (int dy = 0; dy <= 1; dy++) {
						if (!level.getFluidState(p.setWithOffset(boss, dx, dy, dz)).isEmpty()) {
							wetArena.add(p.toShortString());
						}
					}
				}
			}
			BoundingBox box = start.getBoundingBox();
			List<DecoratedPotBlockEntity> found = new ArrayList<>();
			for (ChunkPos c : forced) {
				for (BlockEntity entity : level.getChunk(c.x(), c.z()).getBlockEntities().values()) {
					if (entity instanceof DecoratedPotBlockEntity pot && box.isInside(pot.getBlockPos())) {
						found.add(pot);
					}
				}
			}
			forced.forEach(c -> level.setChunkForced(c.x(), c.z(), false));
			helper.assertTrue(waterlogged.isEmpty(), "no block of the basement waterlogged by the flooded ground: "
				+ waterlogged.size() + ", e.g. " + waterlogged.subList(0, Math.min(6, waterlogged.size())));
			helper.assertTrue(flowing.isEmpty(), "no water flows in the basement (" + sources + " still sources): " + flowing.size()
				+ " flowing cells, e.g. " + flowing.subList(0, Math.min(6, flowing.size())));
			helper.assertTrue(wetArena.isEmpty(), "the arena floor stays dry: " + wetArena.size() + " wet cells, e.g. "
				+ wetArena.subList(0, Math.min(6, wetArena.size())));
			helper.assertTrue(found.size() == pots, "every decorated pot of the grand temple placed: " + found.size() + " / " + pots);
			for (DecoratedPotBlockEntity pot : found) {
				helper.assertTrue(pot.getLootTable() != null && pot.getLootTable().identifier().equals(POT_LOOT),
					"pot at " + pot.getBlockPos().toShortString() + " has the ruin pot loot table, got " + pot.getLootTable());
			}
			// the table rolls one item into the pot (what breaking it spills)
			helper.assertFalse(found.getFirst().getTheItem().isEmpty(), "the ruin pot loot table gives an item");
			helper.succeed();
		});
	}

	private static void place(GameTestHelper helper, String id, ResourceKey<Level> dimension) {
		placeStart(helper, id, dimension);
		helper.succeed();
	}

	private static StructureStart placeStart(GameTestHelper helper, String id, ResourceKey<Level> dimension) {
		return placeStart(helper, id, dimension, s -> {
		});
	}

	/**
	 * Generates the structure start in a far chunk and places it in every chunk it covers (PlaceCommand.placeStructure),
	 * then finds the boss marker through the pieces, as the ruin runtime does. {@code beforePlacing} runs once the chunks
	 * are loaded, just before the pieces are placed.
	 */
	private static StructureStart placeStart(GameTestHelper helper, String id, ResourceKey<Level> dimension,
		Consumer<StructureStart> beforePlacing) {
		ServerLevel level = helper.getLevel().getServer().getLevel(dimension);
		helper.assertTrue(level != null, "dimension " + dimension.identifier() + " exists");
		ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("guildy", "ruin/" + id));
		Optional<Holder.Reference<Structure>> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(key);
		helper.assertTrue(holder.isPresent(), "structure guildy:ruin/" + id + " is registered");
		int slot = SLOTS.getAndIncrement();
		BlockPos origin = new BlockPos(BASE_X - slot * 1024, 64, 300000 + slot * 1024);
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState randomState = level.getChunkSource().randomState();
		StructureStart start = holder.get().value().generate(holder.get(), level.dimension(), level.registryAccess(), generator,
			generator.getBiomeSource(), randomState.createClimateSampler(SamplerContext.EMPTY_UNCACHED), randomState,
			level.getStructureTemplateManager(), level.getSeed(), ChunkPos.containing(origin), 0, level, b -> true);
		helper.assertTrue(start.isValid(), id + " start is valid");
		BoundingBox box = start.getBoundingBox();
		ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ()));
		ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ()));
		ChunkPos.rangeClosed(min, max).forEach(c -> level.getChunk(c.x(), c.z()));
		beforePlacing.accept(start);
		ChunkPos.rangeClosed(min, max).forEach(c -> start.placeInChunk(level, level.structureManager(), generator, level.getRandom(),
			new BoundingBox(c.getMinBlockX(), level.getMinY(), c.getMinBlockZ(), c.getMaxBlockX(), level.getMaxY() + 1, c.getMaxBlockZ()), c));
		helper.assertTrue(start.getPieces().size() == 1 || start.getPieces().size() == 2, id + " has 1 or 2 pieces, got " + start.getPieces().size());
		List<BlockPos> bosses = bossMarkers(level, start);
		helper.assertTrue(bosses.size() == 1, id + " placed with one boss marker, got " + bosses.size());
		BlockPos boss = bosses.getFirst();
		helper.assertTrue(box.isInside(boss), id + " boss marker " + boss + " inside the structure " + box);
		helper.assertFalse(level.getBlockState(boss.below()).isAir(), id + " boss marker stands on a block");
		helper.assertTrue(level.getBlockState(boss.above(2)).isAir(), id + " free space above the boss marker");
		return start;
	}

	/** The guildy:boss DATA markers of a placed start, found through its pieces as the ruin runtime does. */
	private static List<BlockPos> bossMarkers(ServerLevel level, StructureStart start) {
		List<BlockPos> bosses = new ArrayList<>();
		for (StructurePiece piece : start.getPieces()) {
			if (piece instanceof PoolElementStructurePiece pool && pool.getElement() instanceof SinglePoolElement single) {
				for (StructureTemplate.StructureBlockInfo marker : single.getDataMarkers(level.getStructureTemplateManager(), pool.getPosition(), pool.getRotation(), true)) {
					if (marker.nbt() != null && "guildy:boss".equals(marker.nbt().getStringOr("metadata", ""))) {
						bosses.add(marker.pos());
					}
				}
			}
		}
		return bosses;
	}
}
