"""
Fluid flow on a template, faithful to the vanilla rules of Minecraft 26.3 (FlowingFluid, WaterFluid, LavaFluid,
SimpleWaterloggedBlock, the minecraft:washed_away_by_fluids block tag): what the water (or lava) of a ruin becomes
once the placed structure has ticked for a while.

  - sources: water / lava blocks of the template and waterlogged blocks (the template's own fluid states);
  - a fluid falls through every cell that can hold it (air, plants, torches, carpets...: the washed-away tag), spreads
    sideways on whatever stops its fall, losing one level per block for water (up to 7 blocks from a source; lava: 2
    per block in the Overworld, 1 in the Nether), toward the nearest hole within the slope distance (water 4) when
    there is one, and forms a new source between two sources standing on a solid block;
  - flowing water never enters a waterloggable block (stairs, slabs, walls, fences, chests...): only a new source does
    (it waterlogs them); faces are compared with the vanilla collision shapes (slabs and stairs at half-block
    resolution), so a full side of a stair or a slab stops the flow and a full block (or leaves, a copper grate) never
    lets it through;
  - structure_void cells (unknown terrain) and the outside of the template are solid; guildy DATA markers are air (the
    ruin module empties them).

The ticks are scheduled like the server does (delay 5 for water, deduplicated per cell and fluid type, run in
scheduling order), until nothing changes any more. The result: every wet cell with its fluid state.
"""
from collections import deque

from . import blocks as B

NORTH, EAST, SOUTH, WEST, DOWN, UP = "north", "east", "south", "west", "down", "up"
HORIZONTAL = (NORTH, EAST, SOUTH, WEST)  # Direction.Plane.HORIZONTAL order
STEP = {NORTH: (0, 0, -1), SOUTH: (0, 0, 1), WEST: (-1, 0, 0), EAST: (1, 0, 0), DOWN: (0, -1, 0), UP: (0, 1, 0)}
OPP = {NORTH: SOUTH, SOUTH: NORTH, WEST: EAST, EAST: WEST, DOWN: UP, UP: DOWN}
FULL = 0b1111

# minecraft:washed_away_by_fluids (26.3), tags expanded: blocks a flowing fluid replaces.
WASHED = set("""
acacia_button acacia_sapling activator_rail air allium attached_melon_stem attached_pumpkin_stem azalea azure_bluet
bamboo_button bamboo_sapling beetroots big_dripleaf big_dripleaf_stem birch_button birch_sapling black_candle
black_carpet blue_candle blue_carpet blue_orchid brain_coral brain_coral_fan brain_coral_wall_fan brown_candle
brown_carpet brown_mushroom bubble_coral bubble_coral_fan bubble_coral_wall_fan bush cactus_flower candle carrots
cave_air cave_vines cave_vines_plant cherry_button cherry_sapling chorus_flower chorus_plant closed_eyeblossom cobweb
cocoa comparator copper_golem_statue copper_torch copper_wall_torch cornflower creeper_head creeper_wall_head
crimson_button crimson_fungus crimson_roots cyan_candle cyan_carpet dandelion dark_oak_button dark_oak_sapling
dead_bush detector_rail dragon_head dragon_wall_head end_rod exposed_copper_golem_statue fern fire fire_coral
fire_coral_fan fire_coral_wall_fan firefly_bush flower_pot flowering_azalea frogspawn glow_lichen golden_dandelion
gray_candle gray_carpet green_candle green_carpet hanging_roots heavy_core horn_coral horn_coral_fan
horn_coral_wall_fan jungle_button jungle_sapling kelp kelp_plant large_fern lava leaf_litter lever light
light_blue_candle light_blue_carpet light_gray_candle light_gray_carpet lilac lily_of_the_valley lily_pad lime_candle
lime_carpet magenta_candle magenta_carpet mangrove_button mangrove_propagule melon_stem moss_carpet nether_sprouts
nether_wart oak_button oak_sapling open_eyeblossom orange_candle orange_carpet orange_tulip oxeye_daisy
oxidized_copper_golem_statue pale_hanging_moss pale_moss_carpet pale_oak_button pale_oak_sapling peony piglin_head
piglin_wall_head pink_candle pink_carpet pink_petals pink_tulip pitcher_crop pitcher_plant player_head
player_wall_head polished_blackstone_button poplar_button poplar_sapling poppy potatoes powder_snow powered_rail
pumpkin_stem purple_candle purple_carpet rail red_candle red_carpet red_mushroom red_shrub red_tulip redstone_torch
redstone_wall_torch redstone_wire repeater resin_clump rose_bush scaffolding sea_pickle seagrass shelf_mushroom
short_dry_grass short_grass skeleton_skull skeleton_wall_skull small_dripleaf snow soul_fire soul_torch
soul_wall_torch spore_blossom spruce_button spruce_sapling stone_button straw_bed sunflower sweet_berry_bush
tall_dry_grass tall_grass tall_seagrass torch torchflower torchflower_crop tripwire tripwire_hook tube_coral
tube_coral_fan tube_coral_wall_fan twisting_vines twisting_vines_plant vine void_air wall_torch warped_button
warped_fungus warped_roots water waxed_copper_golem_statue waxed_exposed_copper_golem_statue
waxed_oxidized_copper_golem_statue waxed_weathered_copper_golem_statue weathered_copper_golem_statue weeping_vines
weeping_vines_plant wheat white_candle white_carpet white_tulip wildflowers wither_rose wither_skeleton_skull
wither_skeleton_wall_skull yellow_candle yellow_carpet zombie_head zombie_wall_head
""".split())
# LiquidBlockContainers without a waterlogged property: they never accept a fluid.
SEALED_CONTAINERS = {"kelp", "kelp_plant", "seagrass", "tall_seagrass"}
# Waterloggable blocks whose collision shape is the full cube (Shapes.block()): no fluid ever passes their faces.
FULL_SHAPE = ("_leaves", "copper_grate", "mangrove_roots")
# Small attached things that are not "solid" (BlockState.isSolid) for the source-conversion rule.
NOT_SOLID = ("candle", "sea_pickle", "lantern", "glow_lichen", "hanging_roots", "amethyst_cluster", "_amethyst_bud",
	"pointed_dripstone", "lightning_rod", "_sign", "torch", "button", "_carpet", "rail", "lever", "tripwire")


class Fluid:
	"""Parameters of one fluid kind (WaterFluid / LavaFluid; fast lava = ultraWarm dimensions)."""

	def __init__(self, name, drop_off, slope, delay, convert):
		self.name, self.drop_off, self.slope, self.delay, self.convert = name, drop_off, slope, delay, convert


WATER = Fluid("water", 1, 4, 5, True)
LAVA = Fluid("lava", 2, 2, 30, False)
LAVA_NETHER = Fluid("lava", 1, 4, 10, False)


def _name(state):
	return state.split("[", 1)[0].split(":", 1)[1]


# ---------------------------------------------------------------------------------------------- block shapes

def _voxel_masks(voxels):
	"""
	Face masks (2 x 2 quadrants per face, the voxels touching that face) of a shape made of half-block voxels
	(i, j, k) = (x, y, z) halves.
	"""
	out = {d: 0 for d in STEP}
	for (i, j, k) in voxels:
		if j == 0:
			out[DOWN] |= 1 << (i * 2 + k)
		if j == 1:
			out[UP] |= 1 << (i * 2 + k)
		if k == 0:
			out[NORTH] |= 1 << (i * 2 + j)
		if k == 1:
			out[SOUTH] |= 1 << (i * 2 + j)
		if i == 0:
			out[WEST] |= 1 << (j * 2 + k)
		if i == 1:
			out[EAST] |= 1 << (j * 2 + k)
	return out


_HALF = {NORTH: lambda i, k: k == 0, SOUTH: lambda i, k: k == 1, WEST: lambda i, k: i == 0, EAST: lambda i, k: i == 1}
_CW = {NORTH: EAST, EAST: SOUTH, SOUTH: WEST, WEST: NORTH}
_CCW = {v: k for k, v in _CW.items()}


def stair_voxels(facing, half, shape):
	"""StairBlock shapes: the slab half plus the step (back half; outer = one quarter, inner = three quarters)."""
	low, high = (0, 1) if half == "bottom" else (1, 0)
	vox = {(i, low, k) for i in (0, 1) for k in (0, 1)}
	back = _HALF[facing]
	if shape == "straight":
		top = lambda i, k: back(i, k)
	elif shape == "outer_left":
		top = lambda i, k: back(i, k) and _HALF[_CCW[facing]](i, k)
	elif shape == "outer_right":
		top = lambda i, k: back(i, k) and _HALF[_CW[facing]](i, k)
	elif shape == "inner_left":
		top = lambda i, k: back(i, k) or _HALF[_CCW[facing]](i, k)
	else:  # inner_right
		top = lambda i, k: back(i, k) or _HALF[_CW[facing]](i, k)
	vox |= {(i, high, k) for i in (0, 1) for k in (0, 1) if top(i, k)}
	return vox


class Cell:
	"""What the flow rules need to know about one block state."""
	__slots__ = ("kind", "masks", "block", "solid", "waterlogged", "fluid")

	def __init__(self, kind, masks=None, block=False, solid=True, waterlogged=False, fluid=None):
		self.kind = kind  # "open" (air, washed away), "container" (waterloggable), "sealed" (holds no fluid), "liquid"
		self.masks = masks or {}
		self.block = block  # collision shape is Shapes.block()
		self.solid = solid  # BlockState.isSolid()
		self.waterlogged = waterlogged
		self.fluid = fluid  # (fluid name, amount, falling, source) of a liquid block / waterlogged block


_CACHE = {}


def classify(state):
	"""Cell of a block state string (None = structure void / outside: solid unknown terrain)."""
	if state is None:
		return _SEALED
	cell = _CACHE.get(state)
	if cell is not None:
		return cell
	block_id, props = B.parse(state)
	name = block_id.split(":", 1)[1]
	if name in ("water", "lava"):
		level = int(props.get("level", "0"))
		if level == 0:
			fl = (name, 8, False, True)
		else:
			fl = (name, 8 if level >= 8 else 8 - level, level >= 8, False)
		cell = Cell("liquid", solid=False, fluid=fl)
	elif name == "structure_block":
		cell = Cell("open", solid=False)  # the DATA markers are emptied at runtime
	elif "waterlogged" in props or "waterlogged" in B.properties(block_id):
		wl = props.get("waterlogged") == "true"
		masks, block = _shape(name, props)
		solid = not any(p in name for p in NOT_SOLID)
		cell = Cell("container", masks, block, solid, wl, ("water", 8, False, True) if wl else None)
	elif name in SEALED_CONTAINERS:
		cell = Cell("sealed", block=True)
	elif name in WASHED:
		cell = Cell("open", solid=name in ("scaffolding", "chorus_plant", "chorus_flower", "heavy_core"))
	else:
		cell = Cell("sealed", block=True)
	_CACHE[state] = cell
	return cell


_SEALED = Cell("sealed", block=True)


def _shape(name, props):
	"""(face masks, is the full cube) of a waterloggable block's collision shape."""
	if any(p in name for p in FULL_SHAPE):
		return {}, True
	if name.endswith("_slab"):
		kind = props.get("type", "bottom")
		if kind == "double":
			return {}, True
		j = 0 if kind == "bottom" else 1
		return _voxel_masks({(i, j, k) for i in (0, 1) for k in (0, 1)}), False
	if name.endswith("_stairs"):
		return _voxel_masks(stair_voxels(props.get("facing", "north"), props.get("half", "bottom"),
			props.get("shape", "straight"))), False
	if name.endswith("campfire"):
		return {DOWN: FULL}, False
	if name == "ladder" or name.endswith("_trapdoor") and props.get("open") == "true":
		return {OPP[props.get("facing", "north")]: FULL}, False
	if name.endswith("_trapdoor"):
		return {DOWN if props.get("half") == "bottom" else UP: FULL}, False
	return {}, False  # posts, panes, bars, chains, chests, pots, lanterns, signs...: no full quadrant on any face


# ---------------------------------------------------------------------------------------------- simulation

class Flow:
	"""
	The flow of one fluid kind on a template. cells: {(x, y, z): state}. After run(): fluid = {pos: (amount,
	falling, source)} for every wet cell, initial = the template's own fluid cells, waterlogged = containers a new
	source waterlogged, ticks = game ticks simulated.
	"""

	def __init__(self, template, fluid=WATER, max_ticks=24000, terrain_water_below=None):
		"""
		terrain_water_below: layer (exclusive) under which the unknown terrain (structure_void) touching the ruin is
		water (an aquifer, a flooded cave): those cells become sources, so leaks into the rooms show up.
		"""
		self.t = template
		self.f = fluid
		self.max_ticks = max_ticks
		self.cells = dict(template.cells)
		self.fluid = {}
		for pos, state in self.cells.items():
			c = classify(state)
			if c.fluid and c.fluid[0] == fluid.name:
				self.fluid[pos] = c.fluid[1:]
		self.terrain = terrain_contacts(template, terrain_water_below) if terrain_water_below is not None else set()
		if self.terrain:
			for n in self.terrain:
				self.cells[n] = f"minecraft:{fluid.name}[level=0]"
				self.fluid[n] = (8, False, True)
		self.initial = dict(self.fluid)
		self.waterlogged = set()
		self.replaced = {}  # pos -> state washed away by the fluid
		self.ticks = 0
		self.converged = False
		self._queue = {}
		self._scheduled = set()
		self._now = 0

	# ------------------------------------------------------------------------------------------ world access

	def state(self, pos):
		if not self.t.inside(*pos):
			return None
		return self.cells.get(pos)

	def cell(self, pos):
		return classify(self.state(pos))

	@staticmethod
	def rel(pos, d):
		dx, dy, dz = STEP[d]
		return pos[0] + dx, pos[1] + dy, pos[2] + dz

	def hold_any(self, c):
		return c.kind in ("open", "container", "liquid")

	def hold_specific(self, c, source):
		"""canHoldSpecificFluid: a waterloggable block takes only a water source; the rest takes anything."""
		if c.kind == "container":
			return source and self.f.name == "water"
		return c.kind != "sealed"

	@staticmethod
	def pass_wall(d, a, b):
		"""canPassThroughWall from cell a to its neighbour b in direction d."""
		if a.block or b.block:
			return False
		return (a.masks.get(d, 0) | b.masks.get(OPP[d], 0)) != FULL

	def can_maybe_pass(self, pos, c, d, tpos, tc):
		tf = self.fluid.get(tpos)
		return not (tf and tf[2]) and self.hold_any(tc) and self.pass_wall(d, c, tc)

	def is_hole(self, pos, c):
		bpos = self.rel(pos, DOWN)
		bc = self.cell(bpos)
		if not self.pass_wall(DOWN, c, bc):
			return False
		return bpos in self.fluid or (self.hold_any(bc) and self.hold_specific(bc, False))

	def new_liquid(self, pos, c):
		highest, sources = 0, 0
		for d in HORIZONTAL:
			n = self.rel(pos, d)
			nf = self.fluid.get(n)
			if nf and self.pass_wall(d, c, self.cell(n)):
				if nf[2]:
					sources += 1
				highest = max(highest, nf[0])
		if sources >= 2 and self.f.convert:
			b = self.rel(pos, DOWN)
			bf = self.fluid.get(b)
			if self.cell(b).solid or (bf and bf[2]):
				return (8, False, True)
		a = self.rel(pos, UP)
		if a in self.fluid and self.pass_wall(UP, c, self.cell(a)):
			return (8, True, False)
		amount = highest - self.f.drop_off
		return None if amount <= 0 else (amount, False, False)

	# ------------------------------------------------------------------------------------------ scheduling

	def schedule(self, pos, source):
		key = (pos, source)
		if key in self._scheduled:
			return
		self._scheduled.add(key)
		self._queue.setdefault(self._now + self.f.delay, []).append(key)

	def notify(self, pos):
		"""setBlockAndUpdate: the cell and its six neighbours with a fluid get a tick."""
		f = self.fluid.get(pos)
		if f:
			self.schedule(pos, f[2])
		for d in STEP:
			n = self.rel(pos, d)
			nf = self.fluid.get(n)
			if nf:
				self.schedule(n, nf[2])

	def set_fluid(self, pos, f):
		old = self.state(pos)
		c = classify(old)
		if f is None:
			self.fluid.pop(pos, None)
			self.cells[pos] = "minecraft:air"
		elif c.kind == "container":
			self.fluid[pos] = f
			self.cells[pos] = B.with_props(old, waterlogged="true")
			self.waterlogged.add(pos)
		else:
			if c.kind == "open" and not old.startswith(("minecraft:air", "minecraft:cave_air")):
				self.replaced.setdefault(pos, old)
			self.fluid[pos] = f
			level = 0 if f[2] else (8 if f[1] else 8 - f[0])
			self.cells[pos] = f"minecraft:{self.f.name}[level={level}]"
		self.notify(pos)

	def run(self):
		for pos in sorted(self.fluid, key=lambda p: (p[1], p[0], p[2])):
			self.schedule(pos, self.fluid[pos][2])
		while self._queue:
			now = min(self._queue)
			if now > self.max_ticks:
				break
			self._now = now
			batch = self._queue.pop(now)
			for key in batch:
				self._scheduled.discard(key)
			for pos, source in batch:
				f = self.fluid.get(pos)
				if f and f[2] == source:
					self.tick(pos, f)
		self.ticks = self._now
		self.converged = not self._queue
		return self

	# ------------------------------------------------------------------------------------------ FlowingFluid

	def tick(self, pos, f):
		c = self.cell(pos)
		if not f[2]:
			new = self.new_liquid(pos, c)
			if new is None:
				self.set_fluid(pos, None)
				return
			if new != f:
				self.set_fluid(pos, new)
				f = new
				c = self.cell(pos)
		self.spread(pos, c, f)

	def spread(self, pos, c, f):
		bpos = self.rel(pos, DOWN)
		bc = self.cell(bpos)
		if self.can_maybe_pass(pos, c, DOWN, bpos, bc):
			nb = self.new_liquid(bpos, bc)
			if bpos not in self.fluid and nb is not None and self.hold_specific(bc, nb[2]):
				self.set_fluid(bpos, nb)
				if self.source_neighbours(pos) >= 3:
					self.spread_to_sides(pos, c, f)
				return
		if f[2] or not self.is_hole(pos, c):
			self.spread_to_sides(pos, c, f)

	def source_neighbours(self, pos):
		n = 0
		for d in HORIZONTAL:
			nf = self.fluid.get(self.rel(pos, d))
			if nf and nf[2]:
				n += 1
		return n

	def spread_to_sides(self, pos, c, f):
		neighbour = 7 if f[1] else f[0] - self.f.drop_off
		if neighbour <= 0:
			return
		for d, nf in self.get_spread(pos, c):
			self.set_fluid(self.rel(pos, d), nf)

	def get_spread(self, pos, c):
		lowest = 1000
		result = []
		holes = {}
		for d in HORIZONTAL:
			tpos = self.rel(pos, d)
			tc = self.cell(tpos)
			if not self.can_maybe_pass(pos, c, d, tpos, tc):
				continue
			nf = self.new_liquid(tpos, tc)
			if nf is None or not self.hold_specific(tc, nf[2]):
				continue
			distance = 0 if self._hole(tpos, tc, holes) else self.slope_distance(tpos, tc, 1, OPP[d], holes)
			if distance < lowest:
				result = []
			if distance <= lowest:
				if tpos not in self.fluid:  # FluidState.canBeReplacedWith: only an empty cell takes the flow
					result.append((d, nf))
				lowest = distance
		return result

	def _hole(self, pos, c, holes):
		h = holes.get(pos)
		if h is None:
			h = holes[pos] = self.is_hole(pos, c)
		return h

	def slope_distance(self, pos, c, depth, came_from, holes):
		lowest = 1000
		for d in HORIZONTAL:
			if d == came_from:
				continue
			tpos = self.rel(pos, d)
			tc = self.cell(tpos)
			if self.can_maybe_pass(pos, c, d, tpos, tc) and self.hold_specific(tc, False):
				if self._hole(tpos, tc, holes):
					return depth
				if depth < self.f.slope:
					lowest = min(lowest, self.slope_distance(tpos, tc, depth + 1, OPP[d], holes))
		return lowest

	# ------------------------------------------------------------------------------------------ results

	def spilled(self):
		"""Wet cells that were not wet in the template (flowing fluid, new sources, waterlogged blocks)."""
		return {p: f for p, f in self.fluid.items() if p not in self.initial}

	def components(self, cells):
		"""Groups of 6-connected positions."""
		left = set(cells)
		out = []
		while left:
			start = left.pop()
			group = [start]
			queue = deque([start])
			while queue:
				p = queue.popleft()
				for d in STEP:
					q = self.rel(p, d)
					if q in left:
						left.remove(q)
						group.append(q)
						queue.append(q)
			out.append(sorted(group, key=lambda p: (p[1], p[0], p[2])))
		out.sort(key=len, reverse=True)
		return out


# ---------------------------------------------------------------------------------------------- audit

def is_open(state):
	"""A cell a fluid can be in or pass through (air, plants, waterloggable blocks, fluids); void is terrain."""
	return state is not None and classify(state).kind != "sealed"


def rel(pos, d):
	"""The neighbour of pos in direction d."""
	dx, dy, dz = STEP[d]
	return pos[0] + dx, pos[1] + dy, pos[2] + dz


def terrain_contacts(t, below):
	"""Void cells (structure_void: unknown terrain) under layer `below` that touch an open cell of the template."""
	out = set()
	for pos, state in t.cells.items():
		if not is_open(state):
			continue
		for d in STEP:
			n = rel(pos, d)
			if n[1] < below and t.inside(*n) and n not in t.cells:
				out.add(n)
	return out


def holes(t, below):
	"""
	Void cells in the middle of a room under layer `below`: at least three open horizontal neighbours and an open
	cell above or under. In the world they keep whatever the terrain had there: a floating block of dirt or stone, or a
	water (lava) source hanging in the room when the ground is flooded.
	"""
	out = []
	for v in terrain_contacts(t, below):
		side = sum(1 for d in HORIZONTAL if is_open(t.get(*rel(v, d))))
		if side >= 3 and (is_open(t.get(*rel(v, UP))) or is_open(t.get(*rel(v, DOWN)))):
			out.append(v)
	return sorted(out)


def fill_holes(t, below):
	"""Turns the holes (see holes()) into air: the cell of the room they were meant to be. Returns them."""
	found = holes(t, below)
	for v in found:
		t.set(*v, "minecraft:air")
	return found


def waterloggable(state):
	c = classify(state)
	return c.kind == "container" and not c.waterlogged


def flooded_ground(t, below):
	"""
	What an apply_waterlogging placement does in flooded ground (an aquifer around the basement), worst case: every
	waterloggable block of the template under layer `below` is placed into water and comes out waterlogged (the toFill
	pass of StructureTemplate.placeInWorld also spreads it to the waterloggable blocks touching them). Returns (the
	waterlogged positions, the Flow of the result).
	"""
	t2 = t.copy()
	wet = []
	for pos, state in t.cells.items():
		if pos[1] < below and waterloggable(state):
			t2.cells[pos] = B.with_props(state, waterlogged="true")
			wet.append(pos)
	return wet, Flow(t2).run()


def dry_channels(flow, min_len=3):
	"""
	Dry troughs: lines of open, dry cells sunk one block into a floor (solid floor under them, open above, solid floor
	blocks with open space above on both sides across the line), at least `min_len` long or touching the template's own
	water at the same layer: a canal that lost its water. Returns groups of positions.
	"""
	def solid(p):
		return flow.state(p) is not None and flow.cell(p).kind == "sealed"

	def floor_side(p):
		return solid(p) and is_open(flow.state(rel(p, UP)))

	cells = set()
	for pos, state in flow.cells.items():
		if pos in flow.fluid or not is_open(state) or classify(state).kind == "container":
			continue
		if not solid(rel(pos, DOWN)) or not is_open(flow.state(rel(pos, UP))):
			continue
		if (floor_side(rel(pos, NORTH)) and floor_side(rel(pos, SOUTH))) or \
			(floor_side(rel(pos, WEST)) and floor_side(rel(pos, EAST))):
			cells.add(pos)
	out = []
	for group in flow.components(cells):
		if len({p[1] for p in group}) != 1:
			continue
		wet_end = any(rel(p, d) in flow.initial for p in group for d in HORIZONTAL)
		line = min(len({p[0] for p in group}), len({p[2] for p in group})) == 1
		if wet_end or (line and len(group) >= min_len):
			out.append(group)
	return out


# block light emission of the sources bright enough to melt ice (more than 10 at the ice)
LIGHT = {"torch": 14, "wall_torch": 14, "copper_torch": 14, "copper_wall_torch": 14, "lantern": 15,
	"campfire": 15, "fire": 15, "glowstone": 15, "sea_lantern": 15, "shroomlight": 15, "jack_o_lantern": 15,
	"ochre_froglight": 15, "verdant_froglight": 15, "pearlescent_froglight": 15, "lava": 15, "beacon": 15,
	"end_rod": 14, "redstone_lamp": 15, "respawn_anchor": 15, "conduit": 15}


def light_of(state):
	if state is None:
		return 0
	block_id, props = B.parse(state)
	name = block_id.split(":", 1)[1]
	if name.endswith("copper_lantern"):
		return 15
	if name == "redstone_lamp" and props.get("lit") != "true":
		return 0
	if name.endswith("campfire") and props.get("lit") == "false":
		return 0
	if name in ("cave_vines", "cave_vines_plant"):
		return 14 if props.get("berries") == "true" else 0
	if name == "sea_pickle":
		return 3 + 3 * int(props.get("pickles", "1")) if props.get("waterlogged") == "true" else 0
	if name.startswith("soul_"):
		return 10
	return LIGHT.get(name, 0)


def melting_ice(t):
	"""
	Ice (minecraft:ice) that the block light of the ruin melts into water (IceBlock: block light above 11 - its light
	dampening of 1): the light of every source within 4 steps, one level lost per step through non-opaque cells.
	Returns [(pos, light)].
	"""
	out = []
	for p in (p for p, s in t.cells.items() if s.split("[", 1)[0] == "minecraft:ice"):
		best = 0
		seen = {p: 0}
		queue = deque([p])
		while queue:
			q = queue.popleft()
			d = seen[q]
			lv = light_of(t.get(*q))
			if lv:
				best = max(best, lv - d)
			if d >= 4:
				continue
			for dd in STEP:
				r = rel(q, dd)
				if r in seen or not t.inside(*r):
					continue
				s = t.get(*r)
				if s is not None and classify(s).kind == "sealed" and not light_of(s) and "glass" not in s \
					and "ice" not in s:
					continue  # opaque
				seen[r] = d + 1
				queue.append(r)
		if best > 10:
			out.append((p, best))
	return out


# ---------------------------------------------------------------------------------------------- self-test

def _selftest():
	"""The vanilla behaviours the audit relies on (python -m ruinlib.water, from tools/ruins)."""
	from .template import Template

	def world(n=31, h=12):
		t = Template(n, h, n)
		for x in range(n):
			for z in range(n):
				t.set(x, 0, z, "minecraft:stone")
				for y in range(1, h):
					t.set(x, y, z, "minecraft:air")
		return t

	def channel(t, x0=10, x1=20, z=15):
		for x in range(x0, x1 + 1):
			t.set(x, 1, z - 1, "minecraft:stone")
			t.set(x, 1, z + 1, "minecraft:stone")
		t.set(x0, 1, z, "minecraft:stone")
		t.set(x1, 1, z, "minecraft:stone")

	t = world()
	t.set(15, 1, 15, "minecraft:water")
	f = Flow(t).run()
	assert len(f.fluid) == 113, "a source on a floor wets the cells up to 7 blocks away"
	t = world()
	t.set(15, 6, 15, "minecraft:stone")
	t.set(15, 7, 15, "minecraft:water")
	f = Flow(t).run()
	assert {p[1] for p in f.fluid} == set(range(1, 8)) and (15, 6, 15) not in f.fluid, "water falls off a pillar"
	t = world()
	t.set(15, 1, 15, "minecraft:water")
	for x, z in ((14, 15), (16, 15), (15, 14), (15, 16)):
		t.set(x, 1, z, "minecraft:stone_brick_slab[type=bottom]")
	assert len(Flow(t).run().fluid) == 1, "flowing water never enters a waterloggable block"
	t = world()
	channel(t)
	t.set(12, 1, 15, "minecraft:water")
	t.set(14, 1, 15, "minecraft:water")
	f = Flow(t).run()
	assert f.fluid[(13, 1, 15)] == (8, False, True) and f.fluid[(15, 1, 15)] == (7, False, False), "two sources make one"
	t = world()
	channel(t)
	for x in range(11, 20):
		t.set(x, 1, 15, "minecraft:water")
	t.set(15, 1, 15, "minecraft:stone_brick_stairs[facing=north,half=bottom]")
	assert Flow(t).run().waterlogged == {(15, 1, 15)}, "a stair between two sources is waterlogged"
	t = world()
	for x in range(10, 21):
		for z in range(10, 21):
			t.set(x, 1, z, "minecraft:stone" if x in (10, 20) or z in (10, 20) else "minecraft:water")
	t.set(15, 8, 15, "minecraft:water")
	f = Flow(t).run()
	assert all(10 < p[0] < 20 and 10 < p[2] < 20 for p in f.spilled()), "a fall into a basin stays in it"
	t = world()
	t.set(15, 6, 15, "minecraft:polished_tuff_stairs[facing=north,half=top,waterlogged=true]")
	assert any(p[1] == 1 for p in Flow(t).run().fluid), "a waterlogged vault stair pours down to the floor"
	print("ruinlib.water: OK")


if __name__ == "__main__":
	_selftest()
