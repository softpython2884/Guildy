"""
Isometric preview renderer for templates (PIL only). Blocks are drawn as simplified shapes (full cubes, slabs,
stairs, posts, panes, carpets, plants, small objects) with their average texture colour and face shading.
Camera: from the south-east, above (visible faces: top, east, south); other angles = rotated templates.
"""
import json
import os
import random

from PIL import Image, ImageDraw, ImageFont

from . import blocks as B
from .build import is_structural

with open(os.path.join(B.DATA, "colors.json"), encoding="utf-8") as _f:
	COLORS = json.load(_f)["colors"]

SHADE_TOP, SHADE_EAST, SHADE_SOUTH = 1.0, 0.78, 0.62
# boss red, minions orange, room guards blue, entrance green
MARKER_COLORS = {"guildy:boss": (230, 30, 30), "guildy:minion": (245, 140, 20), "guildy:guard": (40, 110, 240),
	"guildy:entrance": (40, 200, 60)}
UNKNOWN = (255, 0, 255)

_PLANT = ("sapling", "short_grass", "tall_grass", "fern", "flower", "tulip", "orchid", "allium", "bluet", "daisy",
	"poppy", "dandelion", "cornflower", "lily_of_the_valley", "dead_bush", "sweet_berry_bush", "roots", "sprouts",
	"fungus", "mushroom", "weeping_vines", "twisting_vines", "cave_vines", "hanging_roots", "sugar_cane", "bamboo",
	"rose_bush", "lilac", "peony", "sunflower", "pitcher", "torchflower", "eyeblossom", "cobweb", "wither_rose",
	"bush", "firefly", "cactus_flower", "short_dry_grass", "tall_dry_grass", "leaf_litter", "wildflowers",
	"pink_petals", "glow_berries", "nether_wart", "wheat", "carrots", "potatoes", "beetroots", "kelp", "seagrass",
	"spore_blossom", "pointed_dripstone", "big_dripleaf", "small_dripleaf", "pale_hanging_moss", "crimson_roots",
	"warped_roots", "nether_sprouts", "fire", "soul_fire")
_SMALL = ("torch", "lantern", "candle", "skull", "head", "flower_pot", "potted_", "button", "lever", "end_rod",
	"amethyst_cluster", "_bud", "sea_pickle", "turtle_egg", "frogspawn", "tripwire_hook", "brewing_stand", "bell",
	"decorated_pot", "lightning_rod", "redstone_torch", "chain", "cocoa")
_POST = ("fence", "_wall", "bars", "pane", "chain")


def color(block_id):
	c = COLORS.get(block_id)
	if c is None:
		return UNKNOWN, UNKNOWN, 1.0
	return tuple(c[0:3]), tuple(c[3:6]), c[6]


def shape(state):
	"""Shape category + parameters of a canonical state."""
	block_id, props = B.parse(state)
	name = block_id.split(":")[1]
	if name in ("air", "cave_air", "void_air", "structure_void", "barrier", "light", "jigsaw"):
		return ("none",)
	if name == "structure_block":
		return ("marker",)
	if name.endswith("_slab"):
		t = props.get("type", "bottom")
		return ("box", 0.0, 1.0) if t == "double" else (("box", 0.5, 1.0) if t == "top" else ("box", 0.0, 0.5))
	if name.endswith("_stairs"):
		return ("stairs", props.get("half", "bottom"), props.get("facing", "north"))
	if name.endswith("_carpet") or name in ("moss_carpet", "pale_moss_carpet", "snow") and props.get("layers", "1") in ("1", "2"):
		return ("box", 0.0, 0.1)
	if name == "snow":
		return ("box", 0.0, int(props.get("layers", "1")) / 8.0)
	if name.endswith("_trapdoor"):
		return ("box", 0.8, 1.0) if props.get("half") == "top" else ("box", 0.0, 0.2)
	if name.endswith("pressure_plate") or name in ("rail", "powered_rail", "detector_rail", "redstone_wire",
		"lily_pad", "leaf_litter", "pink_petals", "wildflowers", "glow_lichen", "sculk_vein", "resin_clump"):
		return ("box", 0.0, 0.06)
	if name in ("vine",):
		return ("vine", props)
	if name in ("water", "lava"):
		return ("liquid",)
	if "glass" in name and "pane" not in name:
		return ("glass",)
	if name.endswith("_leaves"):
		return ("leaves",)
	for p in _POST:
		if name.endswith(p) or (p == "chain" and name.endswith("chain")):
			return ("post", 0.25 if "pane" in name or "bars" in name or "chain" in name else 0.38)
	if name.endswith("_door"):
		return ("post", 0.3)
	for p in _PLANT:
		if p in name and not name.startswith("potted_") and not is_structural(state):
			return ("plant",)
	for p in _SMALL:
		if p in name:
			return ("small", 0.6 if "lantern" in name else 0.45)
	if name in ("cauldron", "water_cauldron", "lava_cauldron", "lectern", "enchanting_table", "anvil",
		"chipped_anvil", "damaged_anvil", "stonecutter", "grindstone", "composter", "hopper", "cake", "conduit"):
		return ("box", 0.0, 0.8)
	if name.endswith("chest") or name == "ender_chest":
		return ("inset", 0.06, 0.88)
	if name.endswith("_bed"):
		return ("box", 0.0, 0.56)
	if "campfire" in name:
		return ("box", 0.0, 0.45)
	if name.endswith("_sign") or name.endswith("_banner"):
		return ("small", 0.5)
	if name in ("ladder",):
		return ("box", 0.0, 1.0, "thin")
	return ("full",)


def opaque(state):
	if state is None:
		return False
	s = shape(state)
	if s[0] != "full":
		return False
	return color(state.split("[", 1)[0])[2] > 0.95


def _mul(c, k, jitter=0.0):
	return tuple(max(0, min(255, int(v * k * (1.0 + jitter)))) for v in c)


class Scene:
	"""Draws cubes / boxes in back-to-front order on an image."""

	def __init__(self, sx, sy, sz, unit, margin=12):
		self.unit = unit
		self.w = int((sx + sz) * unit) + 2 * margin
		self.h = int((sx + sz) * unit / 2 + sy * unit) + 2 * margin
		self.ox = sz * unit + margin
		self.oy = sy * unit + margin
		self.img = Image.new("RGB", (self.w, self.h), (236, 233, 226))
		self.draw = ImageDraw.Draw(self.img)

	def project(self, x, y, z):
		u = self.unit
		return (self.ox + (x - z) * u, self.oy + (x + z) * u / 2 - y * u)

	def poly(self, pts, fill, outline=None):
		self.draw.polygon([self.project(*p) for p in pts], fill=fill, outline=outline)

	def box(self, x0, y0, z0, x1, y1, z1, top, east, south, faces="tes", outline=None):
		if "s" in faces:
			self.poly([(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], south, outline)
		if "e" in faces:
			self.poly([(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)], east, outline)
		if "t" in faces:
			self.poly([(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], top, outline)


def render_iso(template, unit=8, terrain=None, ground=0, hide=None, rotation=0, edges=True, light_bg=None, bright=1.0):
	"""
	Isometric view. rotation: quarter turns of the template before drawing. terrain: optional block id drawn under
	the void cells below `ground` (the natural ground layer), for context. hide(x, y, z) -> True skips a cell
	(cut-away views, in template coordinates).
	"""
	sx, sy, sz = template.size
	cells = {}
	marks = dict(template.markers())
	for (x, y, z), state in template.cells.items():
		if hide is not None and hide(x, y, z):
			continue
		if (x, y, z) in marks:
			state = "minecraft:structure_block[mode=data]|" + marks[(x, y, z)]
		cells[(x, y, z)] = state
	if terrain is not None:
		for x in range(sx):
			for z in range(sz):
				for y in range(max(0, ground - 1), ground + 1):
					if (x, y, z) not in cells and not (hide is not None and hide(x, y, z)):
						cells[(x, y, z)] = terrain
	# rotate coordinates (and states, for directional shapes)
	turns = rotation % 4
	if turns:
		rotated = {}
		for (x, y, z), state in cells.items():
			nx, nz, cx, cz = x, z, sx, sz
			for _ in range(turns):  # clockwise seen from above: north edge -> east edge
				nx, nz = cz - 1 - nz, nx
				cx, cz = cz, cx
			rotated[(nx, y, nz)] = B.rotate(state, turns) if "[" in state and "|" not in state else state
		cells = rotated
		if turns % 2:
			sx, sz = sz, sx
	scene = Scene(sx, sy, sz, unit)
	if light_bg:
		scene.img.paste(light_bg, (0, 0, scene.w, scene.h))
	order = sorted(cells.items(), key=lambda kv: (kv[0][0] + kv[0][1] + kv[0][2], kv[0][1]))
	opaque_cache = {}

	def is_opaque(pos):
		state = cells.get(pos)
		if state is None:
			return False
		r = opaque_cache.get(state)
		if r is None:
			r = opaque(state)
			opaque_cache[state] = r
		return r

	for (x, y, z), state in order:
		if "|" in state:
			meta = state.split("|", 1)[1]
			c = MARKER_COLORS.get(meta, (200, 0, 200))
			scene.box(x + 0.15, y, z + 0.15, x + 0.85, y + 1.6, z + 0.85, _mul(c, 1.0), _mul(c, 0.8), _mul(c, 0.65), "tes", (40, 0, 0))
			continue
		block_id = state.split("[", 1)[0]
		sh = shape(state)
		kind = sh[0]
		if kind == "none" or kind == "marker":
			continue
		top_c, side_c, alpha = color(block_id)
		rnd = random.Random(hash((x * 73856093) ^ (y * 19349663) ^ (z * 83492791)) & 0xffffffff)
		j = (rnd.random() - 0.5) * 0.10
		if bright != 1.0:
			top_c = tuple(min(255, int(c * bright + 18 * (bright - 1))) for c in top_c)
			side_c = tuple(min(255, int(c * bright + 18 * (bright - 1))) for c in side_c)
		top = _mul(top_c, SHADE_TOP, j)
		east = _mul(side_c, SHADE_EAST, j)
		south = _mul(side_c, SHADE_SOUTH, j)
		outline = _mul(side_c, 0.45) if edges and unit >= 6 else None
		if kind == "full" or kind == "glass" or kind == "leaves" or kind == "liquid":
			faces = ""
			if not is_opaque((x, y + 1, z)):
				faces += "t"
			if not is_opaque((x + 1, y, z)):
				faces += "e"
			if not is_opaque((x, y, z + 1)):
				faces += "s"
			if not faces:
				continue
			if kind == "glass":
				top, east, south = _mul(top_c, 1.05), _mul(side_c, 0.9), _mul(side_c, 0.8)
				outline = (140, 170, 180)
			if kind == "liquid":
				outline = None
				faces = "t" if not is_opaque((x, y + 1, z)) and cells.get((x, y + 1, z), "").split("[")[0] != block_id else ""
				if not faces:
					continue
				scene.box(x, y, z, x + 1, y + 0.88, z + 1, top, east, south, faces, None)
				continue
			scene.box(x, y, z, x + 1, y + 1, z + 1, top, east, south, faces, outline)
		elif kind == "box":
			scene.box(x, y + sh[1], z, x + 1, y + sh[2], z + 1, top, east, south, "tes", outline)
		elif kind == "inset":
			d = sh[1]
			scene.box(x + d, y, z + d, x + 1 - d, y + sh[2], z + 1 - d, top, east, south, "tes", outline)
		elif kind == "stairs":
			half, facing = sh[1], sh[2]
			if half == "bottom":
				scene.box(x, y, z, x + 1, y + 0.5, z + 1, top, east, south, "tes", outline)
				bx0, bz0, bx1, bz1 = _stair_back(x, z, facing)
				scene.box(bx0, y + 0.5, bz0, bx1, y + 1, bz1, top, east, south, "tes", outline)
			else:
				bx0, bz0, bx1, bz1 = _stair_back(x, z, facing)
				scene.box(bx0, y, bz0, bx1, y + 0.5, bz1, top, east, south, "tes", outline)
				scene.box(x, y + 0.5, z, x + 1, y + 1, z + 1, top, east, south, "tes", outline)
		elif kind == "post":
			r = sh[1]
			scene.box(x + 0.5 - r / 2, y, z + 0.5 - r / 2, x + 0.5 + r / 2, y + 1, z + 0.5 + r / 2, top, east, south,
				"tes", outline if unit >= 10 else None)
			props = B.parse(state)[1]
			for d, (dx, dz) in (("north", (0, -1)), ("south", (0, 1)), ("west", (-1, 0)), ("east", (1, 0))):
				v = props.get(d)
				if v in ("true", "low", "tall"):
					hy = 0.8 if v != "tall" else 1.0
					cx0 = x + 0.5 - r / 3 + min(0, dx) * 0.5
					cx1 = x + 0.5 + r / 3 + max(0, dx) * 0.5
					cz0 = z + 0.5 - r / 3 + min(0, dz) * 0.5
					cz1 = z + 0.5 + r / 3 + max(0, dz) * 0.5
					scene.box(cx0, y + 0.1, cz0, cx1, y + hy, cz1, top, east, south, "tes", None)
		elif kind == "plant":
			c = _mul(side_c, 1.0, j)
			p0 = scene.project(x + 0.15, y, z + 0.5)
			p1 = scene.project(x + 0.85, y, z + 0.5)
			p2 = scene.project(x + 0.85, y + 0.85, z + 0.5)
			p3 = scene.project(x + 0.15, y + 0.85, z + 0.5)
			scene.draw.polygon([p0, p1, p2, p3], fill=_mul(c, 0.85))
			q0 = scene.project(x + 0.5, y, z + 0.15)
			q1 = scene.project(x + 0.5, y, z + 0.85)
			q2 = scene.project(x + 0.5, y + 0.85, z + 0.85)
			q3 = scene.project(x + 0.5, y + 0.85, z + 0.15)
			scene.draw.polygon([q0, q1, q2, q3], fill=c)
		elif kind == "small":
			r = sh[1]
			name = block_id.split(":")[1]
			glow = any(k in name for k in ("torch", "lantern", "candle", "campfire", "end_rod"))
			if glow:
				top, east, south = (255, 214, 120), (240, 170, 80), (220, 150, 70)
				if "soul" in name:
					top, east, south = (150, 240, 250), (90, 200, 220), (70, 170, 200)
			yb = y + (0.35 if "lantern" in name and B.parse(state)[1].get("hanging") == "true" else 0.0)
			scene.box(x + 0.5 - r / 2, yb, z + 0.5 - r / 2, x + 0.5 + r / 2, yb + r, z + 0.5 + r / 2, top, east, south,
				"tes", None)
		elif kind == "vine":
			props = sh[1]
			c = _mul(side_c, 0.95, j)
			if props.get("north") == "true":
				scene.poly([(x, y, z + 0.05), (x + 1, y, z + 0.05), (x + 1, y + 1, z + 0.05), (x, y + 1, z + 0.05)], c)
			if props.get("west") == "true":
				scene.poly([(x + 0.05, y, z), (x + 0.05, y, z + 1), (x + 0.05, y + 1, z + 1), (x + 0.05, y + 1, z)], c)
			if props.get("south") == "true":
				scene.poly([(x, y, z + 0.95), (x + 1, y, z + 0.95), (x + 1, y + 1, z + 0.95), (x, y + 1, z + 0.95)], c)
			if props.get("east") == "true":
				scene.poly([(x + 0.95, y, z), (x + 0.95, y, z + 1), (x + 0.95, y + 1, z + 1), (x + 0.95, y + 1, z)], c)
			if props.get("up") == "true":
				scene.poly([(x, y + 0.95, z), (x + 1, y + 0.95, z), (x + 1, y + 0.95, z + 1), (x, y + 0.95, z + 1)], c)
	return scene.img


def _stair_back(x, z, facing):
	"""Footprint of the upper (back) half of a stair."""
	if facing == "north":
		return x, z, x + 1, z + 0.5
	if facing == "south":
		return x, z + 0.5, x + 1, z + 1
	if facing == "west":
		return x, z, x + 0.5, z + 1
	return x + 0.5, z, x + 1, z + 1


def render_plan(template, y, unit=6, below=6, bright=1.0, terrain=None, ground=None):
	"""Top-down slice at layer y: the cell at y, or (darker) the first non-air cell below, up to `below` layers."""
	sx, sy, sz = template.size
	img = Image.new("RGB", (sx * unit, sz * unit), (236, 233, 226))
	draw = ImageDraw.Draw(img)
	for x in range(sx):
		for z in range(sz):
			for d in range(0, below + 1):
				state = template.cells.get((x, y - d, z))
				if state is None and terrain is not None and ground is not None and y - d == ground:
					state = terrain
				if state is None or state.startswith("minecraft:air"):
					continue
				block_id = state.split("[", 1)[0]
				top, side, _ = color(block_id)
				if bright != 1.0:
					top = tuple(min(255, int(c * bright + 18 * (bright - 1))) for c in top)
					side = tuple(min(255, int(c * bright + 18 * (bright - 1))) for c in side)
				k = 1.0 - 0.11 * d
				if d == 0 and opaque(state):
					c = _mul(side, 0.85)  # wall cut at this level
				else:
					c = _mul(top, k)
				draw.rectangle([x * unit, z * unit, x * unit + unit - 1, z * unit + unit - 1], fill=c)
				if d == 0 and unit >= 5 and opaque(state):
					draw.rectangle([x * unit, z * unit, x * unit + unit - 1, z * unit + unit - 1], outline=_mul(side, 0.5))
				break
	# spawn markers standing on this level (feet cell within the slice)
	for (x, my, z), meta in template.markers():
		if y - below <= my <= y + 1:
			c = MARKER_COLORS.get(meta, (200, 0, 200))
			m = max(1, unit // 5)
			draw.rectangle([x * unit + m, z * unit + m, x * unit + unit - 1 - m, z * unit + unit - 1 - m], fill=c,
				outline=(20, 20, 20))
	return img


def font(size):
	for name in ("DejaVuSans.ttf", "arial.ttf", "segoeui.ttf"):
		try:
			return ImageFont.truetype(name, size)
		except OSError:
			continue
	return ImageFont.load_default()


def label(img, text, size=16, pos=(8, 6), fill=(40, 40, 40)):
	ImageDraw.Draw(img).text(pos, text, font=font(size), fill=fill)
	return img


def sheet(panels, columns, bg=(236, 233, 226), pad=8):
	"""Arranges (image, caption) panels in a grid."""
	cell_w = max(p.width for p, _ in panels)
	rows = []
	for i in range(0, len(panels), columns):
		rows.append(panels[i:i + columns])
	heights = [max(p.height for p, _ in row) + 22 for row in rows]
	out = Image.new("RGB", (columns * (cell_w + pad) + pad, sum(heights) + pad * (len(rows) + 1)), bg)
	y = pad
	for row, h in zip(rows, heights):
		x = pad
		for img, caption in row:
			out.paste(img, (x + (cell_w - img.width) // 2, y + 22))
			label(out, caption, 15, (x + 4, y + 2))
			x += cell_w + pad
		y += h + pad
	return out


def fit(img, max_w, max_h):
	k = min(max_w / img.width, max_h / img.height, 1.0)
	if k < 1.0:
		img = img.resize((max(1, int(img.width * k)), max(1, int(img.height * k))), Image.LANCZOS)
	return img


def save_png(img, path, colors=192):
	"""Palette-quantised PNG (small files for the repository)."""
	q = img.quantize(colors=colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
	q.save(path, optimize=True)
