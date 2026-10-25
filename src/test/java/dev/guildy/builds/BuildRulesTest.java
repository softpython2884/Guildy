package dev.guildy.builds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cells, passes and fronts, block state texts, the saved data of the sites and builds.json5. */
class BuildRulesTest {
	// ------------------------------------------------------------------ cells

	@Test
	void cellDecisionsWhereABlockGoes() {
		assertEquals(CellRules.Action.DONE, CellRules.decide(false, CellRules.World.EXACT, false));
		assertEquals(CellRules.Action.PLACE, CellRules.decide(false, CellRules.World.AIR, false));
		assertEquals(CellRules.Action.PLACE, CellRules.decide(false, CellRules.World.REPLACEABLE, false));
		assertEquals(CellRules.Action.PLACE, CellRules.decide(false, CellRules.World.FLUID, false));
		assertEquals(CellRules.Action.REPLACE, CellRules.decide(false, CellRules.World.NATURAL, false));
		assertEquals(CellRules.Action.FIX, CellRules.decide(false, CellRules.World.SAME_BLOCK, false));
		assertEquals(CellRules.Action.BLOCKED, CellRules.decide(false, CellRules.World.BUILT, false));
		assertEquals(CellRules.Action.BREAK_AND_PLACE, CellRules.decide(false, CellRules.World.BUILT, true));
		for (CellRules.World never : new CellRules.World[] {CellRules.World.CONTAINER, CellRules.World.PROTECTED, CellRules.World.FOREIGN}) {
			assertEquals(CellRules.Action.BLOCKED, CellRules.decide(false, never, true), never.name());
			assertEquals(CellRules.Action.LEFT, CellRules.decide(true, never, true), never.name());
		}
		assertEquals(CellRules.Action.UNKNOWN, CellRules.decide(false, CellRules.World.UNLOADED, true));
	}

	@Test
	void cellDecisionsWhereTheInteriorIsDug() {
		assertEquals(CellRules.Action.DONE, CellRules.decide(true, CellRules.World.AIR, false));
		assertEquals(CellRules.Action.CLEAR, CellRules.decide(true, CellRules.World.NATURAL, false));
		assertEquals(CellRules.Action.CLEAR, CellRules.decide(true, CellRules.World.REPLACEABLE, false));
		assertEquals(CellRules.Action.CLEAR, CellRules.decide(true, CellRules.World.FLUID, false));
		assertEquals(CellRules.Action.LEFT, CellRules.decide(true, CellRules.World.BUILT, false));
		assertEquals(CellRules.Action.CLEAR_BUILT, CellRules.decide(true, CellRules.World.BUILT, true));
		assertTrue(CellRules.isWork(CellRules.Action.CLEAR));
		assertFalse(CellRules.isWork(CellRules.Action.LEFT));
		assertTrue(CellRules.needsMaterials(CellRules.Action.REPLACE));
		assertFalse(CellRules.needsMaterials(CellRules.Action.FIX));
		assertFalse(CellRules.needsMaterials(CellRules.Action.CLEAR));
	}

	// ------------------------------------------------------------------ passes

	@Test
	void frontsOrderThePasses() {
		int[] solid = {0, 4, 6, 2, 0};
		int[] attached = {1, 0, 3, 0, 1};
		int[] clear = {0, 0, 2, 5, 0};
		BuildPasses.Fronts fronts = BuildPasses.fronts(solid, attached, clear, 2);
		assertEquals(1, fronts.solidLow());
		assertEquals(3, fronts.clearTop());
		// Solid: bottom-up, two layers open.
		assertFalse(fronts.allows(BuildPasses.Pass.SOLID, 0));
		assertTrue(fronts.allows(BuildPasses.Pass.SOLID, 1));
		assertTrue(fronts.allows(BuildPasses.Pass.SOLID, 2));
		assertFalse(fronts.allows(BuildPasses.Pass.SOLID, 3));
		// Clear: top-down.
		assertTrue(fronts.allows(BuildPasses.Pass.CLEAR, 3));
		assertTrue(fronts.allows(BuildPasses.Pass.CLEAR, 2));
		assertFalse(fronts.allows(BuildPasses.Pass.CLEAR, 1));
		// Attached: only below the solid front.
		assertTrue(fronts.allows(BuildPasses.Pass.ATTACHED, 0));
		assertFalse(fronts.allows(BuildPasses.Pass.ATTACHED, 2));
		// Fluids: last.
		assertFalse(fronts.allows(BuildPasses.Pass.FLUID, 0));
	}

	@Test
	void frontsWhenPassesAreDone() {
		BuildPasses.Fronts done = BuildPasses.fronts(new int[3], new int[] {0, 0, 2}, new int[3], 1);
		assertTrue(done.solidDone());
		assertTrue(done.clearDone());
		assertTrue(done.allows(BuildPasses.Pass.ATTACHED, 2), "no solid work left: every layer gets its finishing");
		assertFalse(done.allows(BuildPasses.Pass.FLUID, 0), "attached work left");
		BuildPasses.Fronts last = BuildPasses.fronts(new int[3], new int[3], new int[3], 1);
		assertTrue(last.allows(BuildPasses.Pass.FLUID, 1));
		assertEquals(BuildPasses.Pass.CLEAR, BuildPasses.passOf(MaterialRules.Kind.AIR, true, false));
		assertEquals(BuildPasses.Pass.FLUID, BuildPasses.passOf(MaterialRules.Kind.FLUID, false, false));
		assertEquals(BuildPasses.Pass.ATTACHED, BuildPasses.passOf(MaterialRules.Kind.BUILD, false, true));
		assertEquals(BuildPasses.Pass.SOLID, BuildPasses.passOf(MaterialRules.Kind.BUILD, false, false));
	}

	// ------------------------------------------------------------------ texts

	@Test
	void blockStateTexts() {
		BlockStateString a = BlockStateString.parse(" Oak_Stairs[ half = bottom , facing=north ] ");
		BlockStateString b = BlockStateString.parse("minecraft:oak_stairs[facing=north,half=bottom]");
		assertEquals(a, b);
		assertEquals("minecraft:oak_stairs[facing=north,half=bottom]", a.toString());
		assertEquals("oak_stairs", a.path());
		assertEquals("north", a.prop("facing"));
		assertEquals("minecraft:stone", BlockStateString.parse("stone").toString());
		assertNull(BlockStateString.parse("minecraft:stone[facing"));
		assertNull(BlockStateString.parse("bad id"));
		assertNull(BlockStateString.parse(""));
		assertTrue(BlockStateString.parse("minecraft:void_air").isAir());
		assertTrue(BlockStateString.parse("structure_void").isStructureVoid());
		assertEquals("minecraft:candle[candles=2]", BlockStateString.parse("candle[candles=1]").with("candles", "2").toString());
	}

	// ------------------------------------------------------------------ saved data

	private static Gson gson() {
		return new GsonBuilder()
				.registerTypeHierarchyAdapter(BlockPos.class, (JsonSerializer<BlockPos>) (pos, type, ctx) -> {
					JsonArray array = new JsonArray();
					array.add(pos.getX());
					array.add(pos.getY());
					array.add(pos.getZ());
					return array;
				})
				.registerTypeHierarchyAdapter(BlockPos.class, (JsonDeserializer<BlockPos>) (json, type, ctx) -> {
					JsonArray array = json.getAsJsonArray();
					return new BlockPos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
				})
				.create();
	}

	@Test
	void sitesSurviveASaveAndBadData() {
		GuildBuildData data = new GuildBuildData();
		BuildProject project = new BuildProject();
		project.id = 3;
		project.name = "Ferme";
		project.file = "3-ferme.schem";
		project.sizeX = 5;
		project.sizeY = 4;
		project.sizeZ = 6;
		project.setTransform(new PlacementTransform(10, 64, -20, PlacementTransform.Turn.CLOCKWISE_90, PlacementTransform.Flip.FRONT_BACK));
		project.interior = "box";
		project.sources = "all";
		project.priority = 2;
		project.done = 40;
		project.total = 90;
		data.projects.add(project);
		data.nextId = 4;
		GuildBuildData read = gson().fromJson(gson().toJson(data), GuildBuildData.class);
		read.sanitize();
		BuildProject back = read.get(3);
		assertEquals("Ferme", back.name);
		assertEquals(project.transform(), back.transform());
		assertEquals(InteriorRules.Mode.BOX, back.interiorMode());
		assertEquals(BuildProject.Sources.ALL, back.sourcesMode());
		assertEquals(44, back.percent());
		assertEquals(4, read.nextId);
		assertTrue(back.contains(10, 64, -20));

		// Broken entries: no position or file → dropped; duplicated ids renumbered; bad enums back to defaults.
		String json = "{\"projects\":[{\"id\":3,\"file\":\"a.schem\",\"origin\":[0,0,0],\"rotation\":\"SIDEWAYS\",\"interior\":\"?\"},"
				+ "{\"id\":3,\"file\":\"b.schem\",\"origin\":[1,0,0]},{\"id\":5,\"origin\":[2,0,0]},{\"id\":6,\"file\":\"c.nbt\"}],\"nextId\":1}";
		GuildBuildData broken = gson().fromJson(json, GuildBuildData.class);
		broken.sanitize();
		assertEquals(2, broken.projects.size());
		assertEquals(PlacementTransform.Turn.NONE.name(), broken.projects.get(0).rotation);
		assertEquals("hollow", broken.projects.get(0).interior);
		assertTrue(broken.projects.get(0).id != broken.projects.get(1).id);
		assertTrue(broken.nextId > Math.max(broken.projects.get(0).id, broken.projects.get(1).id));
		assertFalse(broken.projects.get(0).name.isBlank());
	}

	@Test
	void orderAndFinishedTrim() {
		GuildBuildData data = new GuildBuildData();
		for (int i = 1; i <= 5; i++) {
			BuildProject project = new BuildProject();
			project.id = i;
			project.file = i + ".schem";
			project.origin = BlockPos.ZERO;
			project.createdAt = i;
			project.finished = i <= 3;
			project.finishedAt = i * 100L;
			project.priority = i == 5 ? 3 : 0;
			data.projects.add(project);
		}
		List<BuildProject> ordered = data.ordered();
		assertEquals(5, ordered.get(0).id, "highest priority first");
		assertEquals(4, ordered.get(1).id);
		assertEquals(3, ordered.get(2).id, "then the finished ones, newest first");
		assertEquals(2, data.activeCount());
		List<BuildProject> removed = data.trimFinished(2);
		assertEquals(1, removed.size());
		assertEquals(1, removed.getFirst().id);
		assertEquals(4, data.projects.size());
	}

	@Test
	void overlapOfBoxes() {
		BuildProject project = new BuildProject();
		project.sizeX = 4;
		project.sizeY = 3;
		project.sizeZ = 4;
		project.origin = new BlockPos(0, 60, 0);
		assertTrue(project.overlaps(new int[] {3, 62, 3, 10, 70, 10}));
		assertFalse(project.overlaps(new int[] {4, 60, 0, 10, 70, 10}));
		assertFalse(project.overlaps(new int[] {0, 63, 0, 3, 70, 3}));
	}

	@Test
	void configDefaultsAndLimits() {
		BuildConfig config = new BuildConfig();
		config.validate();
		assertEquals(BuildConfig.CURRENT_VERSION, config.configVersion);
		assertEquals(2, config.maxActive(1));
		assertEquals(2, config.maxActive(5));
		assertEquals(3, config.maxActive(6));
		assertEquals(8, config.maxActive(100), "capped");
		config.builder.blocksPerSecond = 500;
		config.builder.maxGatherShare = -1;
		config.projects.defaultInterior = "weird";
		config.terraform.naturalBlocks = null;
		config.validate();
		assertEquals(10.0, config.builder.blocksPerSecond);
		assertEquals(0.0, config.builder.maxGatherShare);
		assertEquals("hollow", config.projects.defaultInterior);
		assertFalse(config.terraform.naturalBlocks.isEmpty());
	}
}
