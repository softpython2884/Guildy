package dev.guildy.builds;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What each block state of a schematic costs (a fake item registry: the usual items exist, the block-only ids do not). */
class MaterialRulesTest {
	private static final Set<String> NO_ITEM = Set.of("minecraft:wall_torch", "minecraft:oak_wall_sign", "minecraft:white_wall_banner",
			"minecraft:wheat", "minecraft:carrots", "minecraft:redstone_wire", "minecraft:tripwire", "minecraft:potted_poppy",
			"minecraft:potted_azalea_bush", "minecraft:cave_vines", "minecraft:cave_vines_plant", "minecraft:water", "minecraft:lava",
			"minecraft:piston_head", "minecraft:candle_cake", "minecraft:red_candle_cake", "minecraft:skeleton_wall_skull",
			"minecraft:dead_brain_coral_wall_fan", "minecraft:melon_stem", "minecraft:sweet_berry_bush", "minecraft:frosted_ice",
			"minecraft:powder_snow", "minecraft:tall_seagrass", "minecraft:modded_thing");
	private static final MaterialRules.ItemLookup ITEMS = id -> !NO_ITEM.contains(id) && !id.startsWith("othermod:");

	private static MaterialRules.Requirement of(String text) {
		return MaterialRules.of(BlockStateString.parse(text), ITEMS, true);
	}

	private static Map<String, Integer> items(String text) {
		MaterialRules.Requirement requirement = of(text);
		assertEquals(MaterialRules.Kind.BUILD, requirement.kind(), text);
		return requirement.items();
	}

	@Test
	void plainBlocksAndCounts() {
		assertEquals(Map.of("minecraft:stone", 1), items("stone"));
		assertEquals(Map.of("minecraft:oak_slab", 2), items("minecraft:oak_slab[type=double,waterlogged=false]"));
		assertEquals(Map.of("minecraft:oak_slab", 1), items("minecraft:oak_slab[type=top]"));
		assertEquals(Map.of("minecraft:candle", 3), items("minecraft:candle[candles=3,lit=true]"));
		assertEquals(Map.of("minecraft:red_candle", 4), items("minecraft:red_candle[candles=4]"));
		assertEquals(Map.of("minecraft:sea_pickle", 2), items("minecraft:sea_pickle[pickles=2,waterlogged=true]"));
		assertEquals(Map.of("minecraft:snow", 5), items("minecraft:snow[layers=5]"));
		assertEquals(Map.of("minecraft:turtle_egg", 3), items("minecraft:turtle_egg[eggs=3,hatch=0]"));
		assertEquals(Map.of("minecraft:pink_petals", 4), items("minecraft:pink_petals[facing=north,flower_amount=4]"));
		assertEquals(Map.of("minecraft:glow_lichen", 2), items("minecraft:glow_lichen[down=true,east=true,north=false]"));
	}

	@Test
	void halvesAndPairs() {
		assertEquals(Map.of("minecraft:oak_door", 1), items("minecraft:oak_door[facing=north,half=lower,hinge=left,open=false]"));
		assertEquals(MaterialRules.Kind.AUTO, of("minecraft:oak_door[facing=north,half=upper]").kind());
		assertEquals(MaterialRules.Kind.AUTO, of("minecraft:red_bed[part=head,facing=east]").kind());
		assertEquals(Map.of("minecraft:red_bed", 1), items("minecraft:red_bed[part=foot,facing=east]"));
		assertEquals(MaterialRules.Kind.AUTO, of("minecraft:tall_grass[half=upper]").kind());
		assertEquals(Map.of("minecraft:sunflower", 1), items("minecraft:sunflower[half=lower]"));
		// Stairs "half" is top / bottom: an ordinary block.
		assertEquals(Map.of("minecraft:oak_stairs", 1), items("minecraft:oak_stairs[half=top]"));
		assertEquals(Map.of("minecraft:seagrass", 1), items("minecraft:tall_seagrass[half=lower]"));
	}

	@Test
	void blocksWithAnotherItem() {
		assertEquals(Map.of("minecraft:wheat_seeds", 1), items("minecraft:wheat[age=7]"));
		assertEquals(Map.of("minecraft:carrot", 1), items("minecraft:carrots[age=2]"));
		assertEquals(Map.of("minecraft:melon_seeds", 1), items("minecraft:melon_stem[age=7]"));
		assertEquals(Map.of("minecraft:sweet_berries", 1), items("minecraft:sweet_berry_bush[age=3]"));
		assertEquals(Map.of("minecraft:redstone", 1), items("minecraft:redstone_wire[power=15,north=side]"));
		assertEquals(Map.of("minecraft:string", 1), items("minecraft:tripwire[attached=true]"));
		assertEquals(Map.of("minecraft:glow_berries", 1), items("minecraft:cave_vines[berries=true]"));
		assertEquals(Map.of("minecraft:glow_berries", 1), items("minecraft:cave_vines_plant"));
		assertEquals(Map.of("minecraft:torch", 1), items("minecraft:wall_torch[facing=east]"));
		assertEquals(Map.of("minecraft:oak_sign", 1), items("minecraft:oak_wall_sign[facing=east]"));
		assertEquals(Map.of("minecraft:white_banner", 1), items("minecraft:white_wall_banner[facing=south]"));
		assertEquals(Map.of("minecraft:skeleton_skull", 1), items("minecraft:skeleton_wall_skull[facing=south]"));
		assertEquals(Map.of("minecraft:dead_brain_coral_fan", 1), items("minecraft:dead_brain_coral_wall_fan[facing=south]"));
		assertEquals(Map.of("minecraft:flower_pot", 1, "minecraft:poppy", 1), items("minecraft:potted_poppy"));
		assertEquals(Map.of("minecraft:flower_pot", 1, "minecraft:azalea", 1), items("minecraft:potted_azalea_bush"));
		assertEquals(Map.of("minecraft:cake", 1, "minecraft:candle", 1), items("minecraft:candle_cake[lit=false]"));
		assertEquals(Map.of("minecraft:cake", 1, "minecraft:red_candle", 1), items("minecraft:red_candle_cake"));
		assertEquals(Map.of("minecraft:cobblestone_wall", 1), items("minecraft:cobblestone_wall[east=low]"));
	}

	@Test
	void fluidsAndWater() {
		MaterialRules.Requirement water = of("minecraft:water[level=0]");
		assertEquals(MaterialRules.Kind.FLUID, water.kind());
		assertTrue(water.items().isEmpty(), "water is free by default");
		MaterialRules.Requirement paid = MaterialRules.of(BlockStateString.parse("minecraft:water[level=0]"), ITEMS, false);
		assertEquals(Map.of("minecraft:water_bucket", 1), paid.items());
		assertEquals(Map.of("minecraft:bucket", 1), paid.returns());
		MaterialRules.Requirement lava = of("minecraft:lava[level=0]");
		assertEquals(Map.of("minecraft:lava_bucket", 1), lava.items());
		assertEquals(Map.of("minecraft:bucket", 1), lava.returns());
		assertEquals(MaterialRules.Kind.AUTO, of("minecraft:water[level=3]").kind(), "flowing water comes by itself");
		assertEquals(Map.of("minecraft:oak_stairs", 1), items("minecraft:oak_stairs[waterlogged=true]"), "waterlogged: free water");
		MaterialRules.Requirement logged = MaterialRules.of(BlockStateString.parse("minecraft:oak_stairs[waterlogged=true]"), ITEMS, false);
		assertEquals(Map.of("minecraft:oak_stairs", 1, "minecraft:water_bucket", 1), logged.items());
		assertEquals(Map.of("minecraft:powder_snow_bucket", 1), items("minecraft:powder_snow"));
		assertEquals(Map.of("minecraft:bucket", 1), of("minecraft:powder_snow").returns());
	}

	@Test
	void notBuildableAndAutomatic() {
		for (String id : new String[] {"barrier", "bedrock", "command_block", "structure_block", "jigsaw", "spawner", "trial_spawner",
				"vault", "nether_portal", "end_portal", "end_gateway", "fire", "soul_fire", "end_portal_frame", "reinforced_deepslate",
				"budding_amethyst", "light", "moving_piston", "bubble_column", "infested_stone", "infested_deepslate", "frosted_ice"}) {
			assertEquals(MaterialRules.Kind.NOT_BUILDABLE, of("minecraft:" + id).kind(), id);
		}
		assertEquals(MaterialRules.Kind.AUTO, of("minecraft:piston_head[facing=up]").kind());
		assertEquals(MaterialRules.Kind.AIR, of("minecraft:cave_air").kind());
		assertEquals(MaterialRules.Kind.VOID, of("minecraft:structure_void").kind());
	}

	@Test
	void unknownItemFallsBackToTheBlockItem() {
		MaterialRules.Requirement modded = of("othermod:machine[facing=north]");
		assertEquals(MaterialRules.Kind.BUILD, modded.kind());
		assertTrue(modded.pick(), "no item of the same id: the block's own item, server side");
		assertTrue(modded.items().isEmpty());
		assertFalse(of("minecraft:stone").pick());
		assertEquals("oak_sign", MaterialRules.withoutWall("oak_wall_sign"));
		assertEquals("torch", MaterialRules.withoutWall("wall_torch"));
		assertEquals("cobblestone_wall", MaterialRules.withoutWall("cobblestone_wall"));
	}
}
