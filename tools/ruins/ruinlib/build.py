"""
Builder: a Template + seeded randomness + noise + building primitives and architectural techniques
(materials with weathering gradients, walls with depth, buttresses, arches, vaults, roofs, windows, stairs...).

Coordinates: x = east, y = up, z = south. Directions are the vanilla names (north = -z, south = +z, west = -x,
east = +x). A stair "facing" d has its high (full) side toward d.
"""
import math
import random

from . import blocks as B
from .noise import Noise
from .template import Template, AIR

DIRS = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OPP = {"north": "south", "south": "north", "west": "east", "east": "west"}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}
CCW = {v: k for k, v in CW.items()}


def dir_of(dx, dz):
	for d, v in DIRS.items():
		if v == (dx, dz):
			return d
	raise ValueError((dx, dz))


# --------------------------------------------------------------------------------------------- block families

class Fam:
	"""A block family: full block + stairs / slab / wall variants (None when the variant does not exist)."""

	def __init__(self, full, stairs=None, slab=None, wall=None):
		self.full, self.stairs, self.slab, self.wall = full, stairs, slab, wall


def fam(name, full=None, stairs=True, slab=True, wall=True):
	"""fam('stone_brick') -> stone_bricks / stone_brick_stairs / stone_brick_slab / stone_brick_wall."""
	full = full or (name + "s")
	st = name + "_stairs" if stairs else None
	sl = name + "_slab" if slab else None
	wa = name + "_wall" if wall else None
	for block in (full, st, sl, wa):
		if block and not B.exists(block):
			raise B.BlockError(f"family {name}: unknown block {block}")
	return Fam(full, st, sl, wa)


F = {
	"stone_brick": fam("stone_brick"),
	"mossy_stone_brick": fam("mossy_stone_brick"),
	"cobblestone": fam("cobblestone", full="cobblestone"),
	"mossy_cobblestone": fam("mossy_cobblestone", full="mossy_cobblestone"),
	"stone": fam("stone", full="stone", wall=False),
	"smooth_stone": Fam("smooth_stone", None, "smooth_stone_slab", None),
	"andesite": fam("andesite", full="andesite"),
	"polished_andesite": fam("polished_andesite", full="polished_andesite", wall=False),
	"deepslate_brick": fam("deepslate_brick"),
	"deepslate_tile": fam("deepslate_tile"),
	"polished_deepslate": fam("polished_deepslate", full="polished_deepslate"),
	"cobbled_deepslate": fam("cobbled_deepslate", full="cobbled_deepslate"),
	"tuff": fam("tuff", full="tuff"),
	"tuff_brick": fam("tuff_brick"),
	"polished_tuff": fam("polished_tuff", full="polished_tuff"),
	"sandstone": fam("sandstone", full="sandstone"),
	"smooth_sandstone": fam("smooth_sandstone", full="smooth_sandstone", wall=False),
	"cut_sandstone": Fam("cut_sandstone", None, "cut_sandstone_slab", None),
	"red_sandstone": fam("red_sandstone", full="red_sandstone"),
	"smooth_red_sandstone": fam("smooth_red_sandstone", full="smooth_red_sandstone", wall=False),
	"cut_red_sandstone": Fam("cut_red_sandstone", None, "cut_red_sandstone_slab", None),
	"mud_brick": fam("mud_brick"),
	"brick": fam("brick", full="bricks"),
	"blackstone": fam("blackstone", full="blackstone"),
	"polished_blackstone": fam("polished_blackstone", full="polished_blackstone"),
	"polished_blackstone_brick": fam("polished_blackstone_brick"),
	"nether_brick": fam("nether_brick"),
	"red_nether_brick": fam("red_nether_brick"),
	"quartz": Fam("quartz_block", "quartz_stairs", "quartz_slab", None),
	"smooth_quartz": Fam("smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab", None),
	"prismarine_brick": Fam("prismarine_bricks", "prismarine_brick_stairs", "prismarine_brick_slab", None),
	"dark_prismarine": Fam("dark_prismarine", "dark_prismarine_stairs", "dark_prismarine_slab", None),
	"oak": Fam("oak_planks", "oak_stairs", "oak_slab", None),
	"spruce": Fam("spruce_planks", "spruce_stairs", "spruce_slab", None),
	"dark_oak": Fam("dark_oak_planks", "dark_oak_stairs", "dark_oak_slab", None),
	"birch": Fam("birch_planks", "birch_stairs", "birch_slab", None),
	"jungle": Fam("jungle_planks", "jungle_stairs", "jungle_slab", None),
	"crimson": Fam("crimson_planks", "crimson_stairs", "crimson_slab", None),
	"warped": Fam("warped_planks", "warped_stairs", "warped_slab", None),
	"purpur": Fam("purpur_block", "purpur_stairs", "purpur_slab", None),
	"granite": fam("granite", full="granite"),
	"polished_granite": fam("polished_granite", full="polished_granite", wall=False),
	"diorite": fam("diorite", full="diorite"),
	"polished_diorite": fam("polished_diorite", full="polished_diorite", wall=False),
	"end_stone_brick": fam("end_stone_brick"),
	"resin_brick": fam("resin_brick"),
}


def stairs(block, facing, half="bottom", shape="straight"):
	return f"{block}[facing={facing},half={half},shape={shape}]"


def slab(block, kind="bottom"):
	return f"{block}[type={kind}]"


# ------------------------------------------------------------------------------------------------ materials

class Mat:
	"""
	A weathered material: base family + optional mossy / cracked / extra variants. The variant of each cell comes
	from clustered noise compared with height-dependent ratios (moss and cracks mostly low, cleaner high).
	"""

	def __init__(self, base, mossy=None, cracked=None, extra=(), moss=0.0, crack=0.0, moss_top=None,
		crack_top=None, y_low=0, y_high=20, scale=0.21, seed=1, alt=None):
		self.base = F[base] if isinstance(base, str) else base
		self.alt = F[alt] if isinstance(alt, str) else alt  # family used for the shapes the base lacks
		self.mossy = F[mossy] if isinstance(mossy, str) else mossy
		self.cracked = cracked  # full block id
		self.extra = list(extra)  # [(full state, probability)] sprinkled at random
		self.moss_low, self.moss_high = moss, moss if moss_top is None else moss_top
		self.crack_low, self.crack_high = crack, crack if crack_top is None else crack_top
		self.y_low, self.y_high = y_low, y_high
		self.scale = scale
		self.noise = Noise(seed * 7919 + 17)
		self.noise2 = Noise(seed * 104729 + 3)
		self.rng = random.Random(seed)

	def _t(self, y):
		if self.y_high == self.y_low:
			return 0.0
		return max(0.0, min(1.0, (y - self.y_low) / (self.y_high - self.y_low)))

	def _hash(self, x, y, z, salt):
		h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ (salt * 2654435761)
		return random.Random(h & 0xffffffff).random()

	def kind(self, x, y, z):
		t = self._t(y)
		moss = self.moss_low + (self.moss_high - self.moss_low) * t
		crack = self.crack_low + (self.crack_high - self.crack_low) * t
		s = self.scale
		n = 0.5 + 0.5 * self.noise.fbm3(x * s, y * s, z * s, 2) * 1.6 + (self._hash(x, y, z, 1) - 0.5) * 0.35
		if self.mossy is not None and n < moss:
			return "mossy"
		n2 = 0.5 + 0.5 * self.noise2.fbm3(x * s * 1.3, y * s * 1.3, z * s * 1.3, 2) * 1.6 + (self._hash(x, y, z, 2) - 0.5) * 0.45
		if n2 < crack:
			return "cracked"
		return "base"

	def full(self, x, y, z):
		for state, p in self.extra:
			if self._hash(x, y, z, 7 + len(state)) < p:
				return state
		k = self.kind(x, y, z)
		if k == "mossy":
			return self.mossy.full
		if k == "cracked" and self.cracked:
			return self.cracked
		return self.base.full

	def _fam(self, x, y, z, part):
		k = self.kind(x, y, z)
		if k == "mossy" and getattr(self.mossy, part):
			return self.mossy
		if getattr(self.base, part) is None:
			if self.alt is None or getattr(self.alt, part) is None:
				raise B.BlockError(f"{self.base.full} has no {part} variant (give the material an alt family)")
			return self.alt
		return self.base

	def stairs(self, x, y, z, facing, half="bottom", shape="straight"):
		f = self._fam(x, y, z, "stairs")
		return stairs(f.stairs, facing, half, shape)

	def slab(self, x, y, z, kind="bottom"):
		f = self._fam(x, y, z, "slab")
		return slab(f.slab, kind)

	def wall(self, x, y, z):
		f = self._fam(x, y, z, "wall")
		return f.wall or f.full


class Mix:
	"""Plain weighted random mix of states (optionally clustered by noise)."""

	def __init__(self, entries, seed=1, scale=0.0):
		self.entries = [(B.canon(s) if "[" in s or ":" in s else s, w) for s, w in entries]
		self.total = sum(w for _, w in entries)
		self.noise = Noise(seed * 31 + 5)
		self.scale = scale

	def full(self, x, y, z):
		if self.scale:
			r = max(0.0, min(0.999, 0.5 + 0.5 * self.noise.fbm3(x * self.scale, y * self.scale, z * self.scale, 2) * 1.5))
		else:
			r = random.Random(((x * 73856093) ^ (y * 19349663) ^ (z * 83492791)) & 0xffffffff).random()
		acc = 0.0
		for state, w in self.entries:
			acc += w / self.total
			if r < acc:
				return state
		return self.entries[-1][0]


def resolve(mat, x, y, z):
	if mat is None or isinstance(mat, str):
		return mat
	return mat.full(x, y, z)


# -------------------------------------------------------------------------------------------------- builder

NON_STRUCTURAL = {"air", "cave_air", "void_air", "water", "lava", "fire", "soul_fire", "cobweb", "vine",
	"glow_lichen", "sculk_vein", "resin_clump", "moss_carpet", "pale_moss_carpet", "short_grass", "tall_grass", "fern",
	"large_fern", "dead_bush", "bush", "firefly_bush", "short_dry_grass", "tall_dry_grass", "hanging_roots", "ladder",
	"lever", "bell", "spawner", "structure_block", "jigsaw", "snow", "lectern", "brewing_stand", "cauldron",
	"water_cauldron", "lava_cauldron", "powder_snow_cauldron", "flower_pot", "decorated_pot", "leaf_litter",
	"pink_petals", "wildflowers", "spore_blossom", "pale_hanging_moss", "sugar_cane", "sweet_berry_bush",
	"nether_sprouts", "crimson_roots", "warped_roots", "crimson_fungus", "warped_fungus", "brown_mushroom",
	"red_mushroom", "weeping_vines", "weeping_vines_plant", "twisting_vines", "twisting_vines_plant", "cave_vines",
	"cave_vines_plant", "torch", "wall_torch", "soul_torch", "soul_wall_torch", "redstone_torch", "redstone_wall_torch",
	"candle", "campfire", "soul_campfire", "chest", "trapped_chest", "barrel", "ender_chest", "anvil", "chipped_anvil",
	"damaged_anvil", "rail", "poppy", "dandelion", "allium", "azure_bluet", "blue_orchid", "oxeye_daisy", "cornflower",
	"lily_of_the_valley", "wither_rose", "torchflower", "open_eyeblossom", "closed_eyeblossom", "rose_bush", "lilac",
	"peony", "sunflower", "pitcher_plant", "nether_wart", "seagrass", "kelp", "kelp_plant", "big_dripleaf",
	"small_dripleaf", "big_dripleaf_stem", "pointed_dripstone", "amethyst_cluster", "sea_pickle", "lily_pad",
	"cactus_flower", "tripwire", "tripwire_hook", "end_rod", "lightning_rod", "conduit", "frogspawn", "turtle_egg",
	"sniffer_egg", "glow_berries", "bamboo", "cocoa"}
NON_STRUCTURAL_SUFFIX = ("_chain", "_lantern", "lantern", "_candle", "_bed", "_sign", "_banner", "_skull", "_head",
	"_button", "_pressure_plate", "_carpet", "_rail", "_tulip", "_sapling", "_door", "_trapdoor", "_campfire",
	"_coral", "_coral_fan", "_coral_wall_fan", "_bud", "_torch", "_fungus", "_roots", "_mushroom", "_propagule")


def is_structural(state):
	"""True for blocks that build things (walls, floors, roofs...), false for attached / decorative ones."""
	if state is None:
		return False
	name = state.split("[", 1)[0].split(":")[1]
	if name in NON_STRUCTURAL or name.startswith("potted_"):
		return False
	return not name.endswith(NON_STRUCTURAL_SUFFIX)


def is_solid_full(state):
	"""Full-face block a vine / torch / lantern can hang on."""
	if state is None or not is_structural(state):
		return False
	name = state.split("[", 1)[0].split(":")[1]
	for part in ("stairs", "slab", "wall", "fence", "pane", "bars", "door", "trapdoor", "leaves", "glass", "rod",
		"chain", "plate", "carpet", "snow"):
		if part in name:
			return False
	return True


class Builder:
	def __init__(self, template, seed, ground=0):
		self.t = template
		self.seed = seed
		self.rng = random.Random(seed)
		self.noise = Noise(seed)
		self.ground = ground  # template layer of the ground floor (replaces the natural top block)
		self.protected = set()  # cells immune to decay
		self.keep_clear = set()  # cells that must stay passable (paths, doors, arena)
		self.debris = []  # positions of removed structural blocks (rubble source)

	# ------------------------------------------------------------------------------------------- basic ops

	@property
	def size(self):
		return self.t.size

	def get(self, x, y, z):
		return self.t.get(x, y, z)

	def is_air(self, x, y, z):
		s = self.t.get(x, y, z)
		return s is not None and s.startswith(AIR)

	def empty(self, x, y, z):
		"""Void or air."""
		s = self.t.get(x, y, z)
		return s is None or s.startswith(AIR)

	def set(self, x, y, z, mat, nbt=None):
		state = resolve(mat, x, y, z)
		self.t.set(x, y, z, state, nbt)

	def fill(self, x0, y0, z0, x1, y1, z1, mat, only_empty=False):
		for x in range(min(x0, x1), max(x0, x1) + 1):
			for y in range(min(y0, y1), max(y0, y1) + 1):
				for z in range(min(z0, z1), max(z0, z1) + 1):
					if only_empty and not self.empty(x, y, z):
						continue
					self.set(x, y, z, mat)

	def air(self, x0, y0, z0, x1, y1, z1):
		self.fill(x0, y0, z0, x1, y1, z1, AIR)

	def void(self, x0, y0, z0, x1, y1, z1):
		self.fill(x0, y0, z0, x1, y1, z1, None)

	def ring(self, x0, z0, x1, z1, y0, y1, mat):
		"""Rectangular ring of walls (1 thick) between the corners."""
		for y in range(y0, y1 + 1):
			for x in range(x0, x1 + 1):
				self.set(x, y, z0, mat)
				self.set(x, y, z1, mat)
			for z in range(z0, z1 + 1):
				self.set(x0, y, z, mat)
				self.set(x1, y, z, mat)

	def protect(self, x0, y0, z0, x1, y1, z1):
		for x in range(min(x0, x1), max(x0, x1) + 1):
			for y in range(min(y0, y1), max(y0, y1) + 1):
				for z in range(min(z0, z1), max(z0, z1) + 1):
					self.protected.add((x, y, z))

	def clear_zone(self, x0, y0, z0, x1, y1, z1):
		for x in range(min(x0, x1), max(x0, x1) + 1):
			for y in range(min(y0, y1), max(y0, y1) + 1):
				for z in range(min(z0, z1), max(z0, z1) + 1):
					self.keep_clear.add((x, y, z))

	def open_clear_zones(self):
		"""Every keep_clear cell (paths, doors, arenas) still void becomes explicit air: never blocked by terrain."""
		for (x, y, z) in self.keep_clear:
			if y > self.ground and self.t.inside(x, y, z) and self.t.get(x, y, z) is None:
				self.t.set(x, y, z, AIR)

	def chance(self, p):
		return self.rng.random() < p

	def pick(self, seq):
		return self.rng.choice(seq)

	def hrand(self, x, y, z, salt=0):
		"""Position-hashed random in [0, 1) (stable whatever the build order)."""
		h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ ((self.seed + salt) * 2654435761)
		return random.Random(h & 0xffffffff).random()

	# ------------------------------------------------------------------------------------------ shapes

	def disc(self, cx, cz, r, y, mat, hollow=False, thickness=1.0):
		for x in range(int(math.floor(cx - r - 1)), int(math.ceil(cx + r + 1)) + 1):
			for z in range(int(math.floor(cz - r - 1)), int(math.ceil(cz + r + 1)) + 1):
				d = math.hypot(x - cx, z - cz)
				if d <= r + 0.5 and (not hollow or d > r + 0.5 - thickness):
					self.set(x, y, z, mat)

	def cylinder(self, cx, cz, r, y0, y1, mat, hollow=False, thickness=1.0):
		for y in range(y0, y1 + 1):
			self.disc(cx, cz, r, y, mat, hollow, thickness)

	def in_disc(self, x, z, cx, cz, r):
		return math.hypot(x - cx, z - cz) <= r + 0.5

	# ---------------------------------------------------------------------------------- vertical pieces

	def column(self, x, z, y0, y1, mat, base=None, capital=None):
		"""Pillar with an optional base / capital ring of stairs (mat must be a Mat with stairs)."""
		for y in range(y0, y1 + 1):
			self.set(x, y, z, mat)
		for d, (dx, dz) in DIRS.items():
			if base is not None and self.empty(x + dx, y0, z + dz):
				self.set(x + dx, y0, z + dz, base.stairs(x + dx, y0, z + dz, OPP[d], "bottom"))
			if capital is not None and self.empty(x + dx, y1, z + dz):
				self.set(x + dx, y1, z + dz, capital.stairs(x + dx, y1, z + dz, OPP[d], "top"))

	def buttress(self, x, z, out, y0, y1, mat, depth=2, cap=True):
		"""Stepped buttress against a wall cell (x, z) growing in direction `out`: deep at the bottom, sloped top."""
		dx, dz = DIRS[out]
		h = y1 - y0 + 1
		for k in range(1, depth + 1):
			top = y0 + int(h * (1.0 - (k - 1) / (depth + 0.6))) - 1
			for y in range(y0, top + 1):
				self.set(x + dx * k, y, z + dz * k, mat)
			if cap:
				self.set(x + dx * k, top + 1, z + dz * k, mat.stairs(x + dx * k, top + 1, z + dz * k, OPP[out], "bottom"))

	# ------------------------------------------------------------------------------------------- walls

	def wall_run(self, a, b, y0, y1, mat, out, thickness=1, pillars=4, pillar_mat=None, plinth=True,
		cornice=True, pillar_extra=0):
		"""
		Straight wall from cell a=(x, z) to b=(x, z) (axis aligned), outer face on the `out` side. Pillars every
		`pillars` cells protrude one block outwards (full height + pillar_extra), a plinth of stairs runs along the
		base between them and a cornice of upside-down stairs along the top: the wall never reads as a flat sheet.
		"""
		ax, az = a
		bx, bz = b
		ox, oz = DIRS[out]
		inward = OPP[out]
		length = max(abs(bx - ax), abs(bz - az))
		sx = (bx > ax) - (bx < ax)
		sz = (bz > az) - (bz < az)
		pmat = pillar_mat or mat
		for i in range(length + 1):
			x, z = ax + sx * i, az + sz * i
			for t in range(thickness):
				for y in range(y0, y1 + 1):
					self.set(x - ox * t, y, z - oz * t, mat)
			is_pillar = pillars and (i % pillars == 0 or i == length)
			px, pz = x + ox, z + oz
			if is_pillar:
				for y in range(y0, y1 + 1 + pillar_extra):
					self.set(px, y, pz, pmat)
				if pillar_extra:
					for t in range(thickness):
						for y in range(y1 + 1, y1 + 1 + pillar_extra):
							self.set(x - ox * t, y, z - oz * t, pmat)
			else:
				if plinth:
					self.set(px, y0, pz, mat.stairs(px, y0, pz, inward, "bottom"))
				if cornice:
					self.set(px, y1, pz, mat.stairs(px, y1, pz, inward, "top"))

	def crenellate(self, a, b, y, mat, out, every=2):
		"""Merlons on top of a wall run (y = first layer above the wall)."""
		ax, az = a
		bx, bz = b
		length = max(abs(bx - ax), abs(bz - az))
		sx = (bx > ax) - (bx < ax)
		sz = (bz > az) - (bz < az)
		for i in range(length + 1):
			x, z = ax + sx * i, az + sz * i
			if i % every == 0:
				self.set(x, y, z, mat)
			else:
				self.set(x, y, z, mat.slab(x, y, z, "bottom"))

	# ------------------------------------------------------------------------------------ openings

	def opening(self, x, z, along, y0, width, height, depth, out, mat, arch="round", frame=None, fill=None,
		sill=True):
		"""
		Door / window through a wall. (x, z) = outer-face cell of the left edge (seen from outside), `along` = the
		direction of the wall run, `depth` = wall thickness to carve (inwards), `out` = outward direction.
		arch: 'flat' | 'round' (stair corners) | 'pointed' (stairs stepping in). fill: state for the opening cells
		(glass pane, bars...) or None for air.
		"""
		ax, az = DIRS[along]
		ox, oz = DIRS[out]
		left_dir = OPP[along]
		right_dir = along
		cells = []
		for i in range(width):
			for h in range(height):
				for d in range(depth):
					cx = x + ax * i - ox * d
					cz = z + az * i - oz * d
					cells.append((cx, y0 + h, cz, i, h, d))
		for cx, cy, cz, i, h, d in cells:
			self.set(cx, cy, cz, fill if fill else AIR)
		top = y0 + height - 1
		if arch in ("round", "pointed") and width >= 2:
			for d in range(depth):
				lx, lz = x - ox * d, z - oz * d
				rx, rz = x + ax * (width - 1) - ox * d, z + az * (width - 1) - oz * d
				self.set(lx, top, lz, mat.stairs(lx, top, lz, left_dir, "top"))
				self.set(rx, top, rz, mat.stairs(rx, top, rz, right_dir, "top"))
				if arch == "pointed" and width >= 4:
					self.set(lx + ax, top, lz + az, mat.full(lx + ax, top, lz + az))
					self.set(rx - ax, top, rz - az, mat.full(rx - ax, top, rz - az))
		if arch == "pointed" and width == 1:
			pass
		if sill and fill is not None:
			for i in range(width):
				sx, sz = x + ax * i + ox, z + az * i + oz
				if self.empty(sx, y0 - 1, sz):
					self.set(sx, y0 - 1, sz, mat.stairs(sx, y0 - 1, sz, OPP[out], "top"))
		return cells

	# -------------------------------------------------------------------------------------- roofs

	def gable_roof(self, x0, z0, x1, z1, y, axis, mat, overhang=1, fill=None, ridge_slab=True, under=None):
		"""
		Gable roof over the box x0..x1 / z0..z1 starting at layer y. axis 'x': ridge runs east-west (slopes face
		north and south). Stairs on both slopes, `under` (planks, tiles...) right below every stair so the roof has
		a real thickness (and an underside seen from inside), optional `fill` for the gable end walls, slab ridge.
		Returns the layer above the ridge.
		"""
		if axis == "x":
			lo, hi = z0 - overhang, z1 + overhang
			a0, a1 = x0 - overhang, x1 + overhang
		else:
			lo, hi = x0 - overhang, x1 + overhang
			a0, a1 = z0 - overhang, z1 + overhang
		k = 0
		while lo + k <= hi - k:
			yy = y + k
			for a in range(a0, a1 + 1):
				if axis == "x":
					c1, c2 = (a, yy, lo + k), (a, yy, hi - k)
					u1, u2 = (a, yy - 1, lo + k), (a, yy - 1, hi - k)
					f1, f2 = "south", "north"
				else:
					c1, c2 = (lo + k, yy, a), (hi - k, yy, a)
					u1, u2 = (lo + k, yy - 1, a), (hi - k, yy - 1, a)
					f1, f2 = "east", "west"
				if lo + k == hi - k:
					self.set(*c1, mat.slab(*c1, "bottom") if ridge_slab else mat.full(*c1))
					if under is not None and k > 0:
						self.set(*u1, under)
				else:
					self.set(*c1, mat.stairs(*c1, f1, "bottom"))
					self.set(*c2, mat.stairs(*c2, f2, "bottom"))
					if under is not None and k > 0:
						self.set(*u1, under)
						self.set(*u2, under)
			# gable end walls under the slopes
			if fill is not None:
				for a in (x0, x1) if axis == "x" else (z0, z1):
					for c in range(lo + k + 1, hi - k):
						if axis == "x":
							self.set(a, yy, c, fill)
						else:
							self.set(c, yy, a, fill)
			k += 1
		return y + k

	def spire(self, cx, cz, half, y0, height, mat, core=None, round_=False, only=None):
		"""
		Solid tapering spire centred on (cx, cz) (cell centre, half-cells allowed: cx = 7.5 for an even width).
		half = half width at the base. Every layer whose next layer is narrower gets stairs on its rim.
		"""
		def half_at(i):
			return half * (1.0 - i / float(height))

		for i in range(height):
			h = half_at(i)
			h_next = half_at(i + 1)
			y = y0 + i
			for x in range(int(cx - half - 1), int(cx + half + 2)):
				for z in range(int(cz - half - 1), int(cz + half + 2)):
					if only is not None and not only(x, z):
						continue
					dx, dz = abs(x - cx), abs(z - cz)
					inside = (dx * dx + dz * dz) ** 0.5 <= h + 0.2 if round_ else max(dx, dz) <= h
					if not inside:
						continue
					inside_next = (dx * dx + dz * dz) ** 0.5 <= h_next + 0.2 if round_ else max(dx, dz) <= h_next
					if inside_next:
						self.set(x, y, z, core if (core is not None and max(dx, dz) < h - 1) else mat.full(x, y, z))
					else:
						# rim: stair facing the centre
						if dx >= dz:
							facing = "west" if x > cx else "east"
						else:
							facing = "north" if z > cz else "south"
						self.set(x, y, z, mat.stairs(x, y, z, facing, "bottom"))
		return y0 + height

	def pyramid_roof(self, x0, z0, x1, z1, y, mat, overhang=1, steep=1, top=None):
		"""Hip / pyramid roof of stairs over a rectangle; steep = layers per inward step."""
		x0 -= overhang
		z0 -= overhang
		x1 += overhang
		z1 += overhang
		yy = y
		while x0 <= x1 and z0 <= z1:
			for _ in range(steep):
				for x in range(x0, x1 + 1):
					self.set(x, yy, z0, mat.stairs(x, yy, z0, "south", "bottom", _corner(x, x0, x1, "south")))
					self.set(x, yy, z1, mat.stairs(x, yy, z1, "north", "bottom", _corner(x, x0, x1, "north")))
				for z in range(z0 + 1, z1):
					self.set(x0, yy, z, mat.stairs(x0, yy, z, "east", "bottom"))
					self.set(x1, yy, z, mat.stairs(x1, yy, z, "west", "bottom"))
				if steep > 1 and _ < steep - 1:
					yy += 1
			yy += 1
			x0 += 1
			z0 += 1
			x1 -= 1
			z1 -= 1
		if top is not None:
			cx, cz = (x0 + x1 + 2) // 2, (z0 + z1 + 2) // 2
			self.set(cx, yy - 1, cz, top)
		return yy

	def dome(self, cx, cz, y0, radius, mat, shell=1.0, only=None, rim_stairs=True):
		"""
		Hemispherical dome shell centred on (cx, cz), base layer y0. Cells whose next layer is narrower become
		stairs facing the centre (smooth curve), the others full blocks; hollow inside.
		"""
		import math as _m
		top = int(radius)
		for k in range(0, top + 1):
			rk = _m.sqrt(max(0.0, radius * radius - k * k))
			rn = _m.sqrt(max(0.0, radius * radius - (k + 1) * (k + 1))) if k < top else -1.0
			y = y0 + k
			for x in range(int(cx - radius - 1), int(cx + radius + 2)):
				for z in range(int(cz - radius - 1), int(cz + radius + 2)):
					if only is not None and not only(x, z):
						continue
					r = _m.hypot(x - cx, z - cz)
					if r > rk + 0.35:
						continue
					rim = r > rn + 0.35
					if not rim and r < rk + 0.35 - shell - (1.0 if k > radius * 0.6 else 0.0):
						continue
					if rim and rim_stairs and k > 0:
						dx, dz = x - cx, z - cz
						if abs(dx) >= abs(dz):
							facing = "west" if dx > 0 else "east"
						else:
							facing = "north" if dz > 0 else "south"
						self.set(x, y, z, mat.stairs(x, y, z, facing, "bottom"))
					else:
						self.set(x, y, z, mat.full(x, y, z))
		return y0 + top + 1

	def carve_dome(self, cx, cz, radius, y0, height, rough=2.0, scale=0.12):
		"""
		Explicit air in the void cells of a noisy half-ellipsoid over (cx, cz) from layer y0: the space the ruin
		needs is cleared even when the site is inside terrain (a cavern in the Nether, a hillside), with organic
		edges; cells already set by the build are untouched.
		"""
		n = self.noise
		sx, sy, sz = self.size
		for x in range(sx):
			for z in range(sz):
				d = math.hypot(x - cx, z - cz) + rough * n.n2(x * scale + 3.1, z * scale - 7.7)
				if d >= radius:
					continue
				h = height * math.sqrt(1.0 - (d / radius) ** 2) + rough * n.n2(x * scale * 1.7, z * scale * 1.7)
				for y in range(y0, min(sy, y0 + int(h) + 1)):
					if self.t.get(x, y, z) is None:
						self.t.set(x, y, z, AIR)

	def envelop(self, mat, thickness=1, below=None, upper=None, upper_from=None):
		"""
		Solid envelope around the carved (air) cells under ground (y < `below`, default the ground floor): void
		cells within `thickness` get `mat` (or `upper` from layer upper_from up), so caves, water and lava never
		break into underground rooms and the rooms read as built masonry.
		"""
		below = self.ground if below is None else below
		air = [p for p, s in self.t.cells.items() if s.startswith(AIR) and p[1] < below]
		for (x, y, z) in air:
			for dx in range(-thickness, thickness + 1):
				for dy in range(-thickness, thickness + 1):
					for dz in range(-thickness, thickness + 1):
						q = (x + dx, y + dy, z + dz)
						if q[1] >= below + 1 or self.t.get(*q) is not None or not self.t.inside(*q):
							continue
						use = upper if (upper is not None and upper_from is not None and q[1] >= upper_from) else mat
						self.set(*q, use)

	# ----------------------------------------------------------------------------------- stairs runs

	def stair_run(self, x, y, z, go, steps, width, mat, side=None, support=None, landing=0):
		"""
		Straight staircase climbing toward `go` from (x, y, z) (first step), `width` cells to the right of the
		walking direction, `steps` steps up. Cells above the steps are carved (3 high); `support` fills below.
		Returns the position after the last step (top landing cell).
		"""
		gx, gz = DIRS[go]
		rx, rz = DIRS[CW[go]]
		for s in range(steps):
			for w in range(width):
				cx, cz = x + gx * s + rx * w, z + gz * s + rz * w
				cy = y + s
				self.set(cx, cy, cz, mat.stairs(cx, cy, cz, go, "bottom"))
				for h in range(1, 4):
					self.set(cx, cy + h, cz, AIR)
				if support is not None:
					for yy in range(cy - 1, cy - 4, -1):
						if self.empty(cx, yy, cz):
							self.set(cx, yy, cz, support)
		return x + gx * steps, y + steps, z + gz * steps

	def stair_down(self, x, y, z, go, steps, width, mat, head=3, solid=None):
		"""Staircase going DOWN toward `go` from floor level y (first step top at y - 1); carves headroom."""
		gx, gz = DIRS[go]
		rx, rz = DIRS[CW[go]]
		for s in range(steps):
			for w in range(width):
				cx, cz = x + gx * s + rx * w, z + gz * s + rz * w
				cy = y - 1 - s
				self.set(cx, cy, cz, mat.stairs(cx, cy, cz, OPP[go], "bottom"))
				for h in range(1, head + 1):
					self.set(cx, cy + h, cz, AIR)
				if solid is not None:
					self.set(cx, cy - 1, cz, solid)
		return x + gx * steps, y - steps, z + gz * steps

	# --------------------------------------------------------------------------------------- vaults

	def vault_x(self, x0, x1, z0, z1, y_spring, mat, crown=None):
		"""Barrel vault for a corridor running along x, interior z0..z1, springing at y_spring."""
		width = z1 - z0 + 1
		for x in range(x0, x1 + 1):
			if width >= 3:
				self.set(x, y_spring, z0, mat.stairs(x, y_spring, z0, "north", "top"))
				self.set(x, y_spring, z1, mat.stairs(x, y_spring, z1, "south", "top"))
			for z in range(z0, z1 + 1):
				self.set(x, y_spring + 1, z, crown or mat.full(x, y_spring + 1, z))

	def vault_z(self, z0, z1, x0, x1, y_spring, mat, crown=None):
		width = x1 - x0 + 1
		for z in range(z0, z1 + 1):
			if width >= 3:
				self.set(x0, y_spring, z, mat.stairs(x0, y_spring, z, "west", "top"))
				self.set(x1, y_spring, z, mat.stairs(x1, y_spring, z, "east", "top"))
			for x in range(x0, x1 + 1):
				self.set(x, y_spring + 1, z, crown or mat.full(x, y_spring + 1, z))


def _corner(i, i0, i1, facing):
	if i == i0:
		return "outer_left" if facing in ("south",) else "outer_right"
	if i == i1:
		return "outer_right" if facing in ("south",) else "outer_left"
	return "straight"


def new(sx, sy, sz, seed, ground=0, name=""):
	return Builder(Template(sx, sy, sz, name), seed, ground)
