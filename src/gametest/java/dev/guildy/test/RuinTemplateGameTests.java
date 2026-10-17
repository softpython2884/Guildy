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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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

/**
 * The six ruin structures (data/guildy/structure/ruin, worldgen/structure/ruin): every template loads and honours
 * the ruin contract (size budget, exactly one guildy:boss DATA marker, minions, loot tables on every container),
 * and every worldgen structure generates and places without error (like /place structure) in its dimension.
 * Natural generation is kept out of the GameTest world by the test-only world preset override
 * (data/minecraft/worldgen/world_preset/flat_all_dimensions.json in the test resources).
 */
public class RuinTemplateGameTests {
	static final String[] OVERWORLD = {"crypte_oubliee", "tour_du_mage", "ziggourat_des_sables", "bastion_de_givre"};
	static final String[] NETHER = {"autel_infernal", "citadelle_des_ames"};
	/** The two landmarks allowed to reach 64 x 64. */
	static final List<String> BIG = List.of("bastion_de_givre", "citadelle_des_ames");
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
				checkVariant(helper, manager, variant.getKey(), variant.getValue(), BIG.contains(id), nether);
			}
		}
		helper.succeed();
	}

	/** One variant: size budget, one boss marker, 2-8 minions, one treasure, loot tables on every container. */
	private static void checkVariant(GameTestHelper helper, StructureTemplateManager manager, String name, List<Identifier> ids,
		boolean big, boolean nether) {
		helper.assertTrue(ids.size() == 1 || ids.size() == 2, name + " has one template or a surface + below pair, got " + ids);
		int bosses = 0;
		int minions = 0;
		int height = 0;
		int treasure = 0;
		for (Identifier templateId : ids) {
			Optional<StructureTemplate> found = manager.get(templateId);
			helper.assertTrue(found.isPresent(), "template " + templateId + " loads");
			StructureTemplate template = found.get();
			Vec3i size = template.getSize();
			int limit = big ? 64 : 48;
			helper.assertTrue(size.getX() <= limit && size.getZ() <= limit, templateId + " footprint " + size + " within " + limit);
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
			helper.assertTrue(template.filterBlocks(BlockPos.ZERO, settings, Blocks.COMMAND_BLOCK).isEmpty()
				&& template.filterBlocks(BlockPos.ZERO, settings, Blocks.BARRIER).isEmpty(), templateId + " has no command block / barrier");
		}
		helper.assertTrue(height <= 64, name + " height " + height + " <= 64");
		helper.assertTrue(bosses == 1, name + " has exactly one guildy:boss marker, got " + bosses);
		helper.assertTrue(minions >= 2 && minions <= 8, name + " has 2-8 minion markers, got " + minions);
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
	public void autelInfernalGeneratesInTheNether(GameTestHelper helper) {
		place(helper, "autel_infernal", Level.NETHER);
	}

	@GameTest(maxTicks = 200)
	public void citadelleDesAmesGeneratesInTheNether(GameTestHelper helper) {
		place(helper, "citadelle_des_ames", Level.NETHER);
	}

	/**
	 * Generates the structure start in a far chunk and places it in every chunk it covers (PlaceCommand.placeStructure),
	 * then finds the boss marker through the pieces, as the ruin runtime does.
	 */
	private static void place(GameTestHelper helper, String id, ResourceKey<Level> dimension) {
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
		ChunkPos.rangeClosed(min, max).forEach(c -> start.placeInChunk(level, level.structureManager(), generator, level.getRandom(),
			new BoundingBox(c.getMinBlockX(), level.getMinY(), c.getMinBlockZ(), c.getMaxBlockX(), level.getMaxY() + 1, c.getMaxBlockZ()), c));
		helper.assertTrue(start.getPieces().size() == 1 || start.getPieces().size() == 2, id + " has 1 or 2 pieces, got " + start.getPieces().size());
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
		helper.assertTrue(bosses.size() == 1, id + " placed with one boss marker, got " + bosses.size());
		BlockPos boss = bosses.getFirst();
		helper.assertTrue(box.isInside(boss), id + " boss marker " + boss + " inside the structure " + box);
		helper.assertFalse(level.getBlockState(boss.below()).isAir(), id + " boss marker stands on a block");
		helper.assertTrue(level.getBlockState(boss.above(2)).isAir(), id + " free space above the boss marker");
		helper.succeed();
	}
}
