"""
Overgrowth and ambience: vines with correct faces, moss, ground plants, hanging roots, glow lichen, cobwebs,
snow layers, nether roots / fungi / weeping vines. Everything is placed only in empty cells with a valid support.
"""
from . import blocks as B
from .build import DIRS, OPP, AIR, is_solid_full, is_structural

def _empty(b, x, y, z, allow_void=True):
	if not b.t.inside(x, y, z):
		return False
	s = b.t.get(x, y, z)
	if s is None:
		return allow_void and y > b.ground
	return s.startswith(AIR)


def _in(region, x, y, z):
	if region is None:
		return True
	(x0, y0, z0), (x1, y1, z1) = region
	return x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1


def vines(b, density=0.25, max_len=7, region=None, scale=0.12, y_min=None, exposed_to_sky=False):
	"""Vines hanging from the top of wall faces, longer where the noise is high."""
	n = b.noise
	count = 0
	sx, sy, sz = b.size
	cells = b.t.cells
	for (x, y, z), state in list(cells.items()):
		if not is_solid_full(state) or not _in(region, x, y, z):
			continue
		if y_min is not None and y < y_min:
			continue
		for d, (dx, dz) in DIRS.items():
			vx, vz = x + dx, z + dz  # the vine cell is in front of the face `d` of this wall block
			wall_side = OPP[d]  # vine property: the side of the vine cell its support is on
			if not _empty(b, vx, y, vz) or (vx, y, vz) in b.keep_clear:
				continue
			# start only at the top of a face (nothing climbable above)
			if is_solid_full(cells.get((x, y + 1, z))) and _empty(b, vx, y + 1, vz):
				continue
			if exposed_to_sky and not _sky(b, vx, y, vz):
				continue
			v = 0.5 + 0.5 * n.fbm3(x * scale, y * scale, z * scale, 2) * 1.6
			if v + (b.hrand(x, y, z, 11) - 0.5) * 0.3 < 1.0 - density:
				continue
			length = 1 + int((v - (1.0 - density)) / max(0.05, density) * max_len)
			length = min(length, max_len)
			for k in range(length):
				cy = y - k
				if cy <= b.ground or not _empty(b, vx, cy, vz) or (vx, cy, vz) in b.keep_clear:
					break
				if k > 0 and not is_solid_full(cells.get((x, cy, z))) and b.hrand(x, cy, z, 3) < 0.5:
					break
				existing = cells.get((vx, cy, vz))
				props = {wall_side: "true"}
				if existing is not None and existing.startswith("minecraft:vine"):
					props.update({k2: v2 for k2, v2 in B.parse(existing)[1].items() if v2 == "true"})
				b.t.set(vx, cy, vz, B.fmt("minecraft:vine", props))
				count += 1
	return count


def _sky(b, x, y, z):
	sy = b.size[1]
	for yy in range(y + 1, sy):
		s = b.t.get(x, yy, z)
		if s is not None and not s.startswith(AIR):
			return False
	return True


def floor_cover(b, choices, density=0.2, region=None, scale=0.15, need=None, avoid_clear=True, sky=None, salt=21):
	"""
	Places plants / carpets on top of solid floors. choices: [(state, weight)]; need(state_below) -> bool filter;
	sky: True = only under the open sky, False = only under a roof, None = both.
	"""
	n = b.noise
	total = sum(w for _, w in choices)
	count = 0
	for (x, y, z), state in list(b.t.cells.items()):
		if not _in(region, x, y + 1, z):
			continue
		if not is_structural(state) or state.startswith(AIR):
			continue
		if need is not None and not need(state):
			continue
		if not _empty(b, x, y + 1, z) or (avoid_clear and (x, y + 1, z) in b.keep_clear and not _flat(choices)):
			continue
		if sky is not None and _sky(b, x, y + 1, z) != sky:
			continue
		v = 0.5 + 0.5 * n.fbm3(x * scale + 40, y * scale, z * scale - 40, 2) * 1.6
		if v + (b.hrand(x, y, z, salt) - 0.5) * 0.4 < 1.0 - density:
			continue
		r = b.hrand(x, y, z, salt + 1) * total
		acc = 0
		for choice, w in choices:
			acc += w
			if r < acc:
				b.t.set(x, y + 1, z, choice)
				count += 1
				break
	return count


def _flat(choices):
	return all(("carpet" in s or "snow" in s or "petals" in s or "leaf_litter" in s) for s, _ in choices)


def replace_surface(b, mapping, density=0.3, region=None, scale=0.18, top_only=True, salt=5):
	"""Turns some exposed blocks into others (moss blocks on stone, podzol on grass...)."""
	n = b.noise
	count = 0
	for (x, y, z), state in list(b.t.cells.items()):
		block_id = state.split("[", 1)[0]
		if block_id not in mapping or not _in(region, x, y, z):
			continue
		if top_only and not _empty(b, x, y + 1, z):
			continue
		v = 0.5 + 0.5 * n.fbm3(x * scale - 11, y * scale, z * scale + 7, 2) * 1.6
		if v + (b.hrand(x, y, z, salt) - 0.5) * 0.3 > 1.0 - density:
			b.t.set(x, y, z, mapping[block_id])
			count += 1
	return count


def hanging(b, state, density=0.1, region=None, max_len=1, plant=None, salt=31):
	"""Hanging plants under solid ceilings (hanging roots, weeping vines with their *_plant body...)."""
	count = 0
	for (x, y, z), s in list(b.t.cells.items()):
		if not is_solid_full(s) or not _in(region, x, y - 1, z):
			continue
		if not _empty(b, x, y - 1, z, allow_void=False) or (x, y - 1, z) in b.keep_clear:
			continue
		if b.hrand(x, y, z, salt) > density:
			continue
		length = 1 + int(b.hrand(x, y, z, salt + 1) * max_len)
		cells = []
		for k in range(1, length + 1):
			if not _empty(b, x, y - k, z, allow_void=False) or (x, y - k, z) in b.keep_clear or not _empty(b, x, y - k - 1, z, False):
				break
			cells.append((x, y - k, z))
		if not cells:
			continue
		for i, c in enumerate(cells):
			last = i == len(cells) - 1
			b.t.set(*c, state if (last or plant is None) else plant)
			count += 1
	return count


def lichen(b, density=0.06, region=None, salt=41, block="minecraft:glow_lichen"):
	"""Glow lichen patches on walls / ceilings of dark interiors."""
	count = 0
	n = b.noise
	for (x, y, z), s in list(b.t.cells.items()):
		if not _in(region, x, y, z) or not _empty(b, x, y, z, allow_void=False) or (x, y, z) in b.keep_clear:
			continue
		faces = {}
		for d, (dx, dy, dz) in (("north", (0, 0, -1)), ("south", (0, 0, 1)), ("east", (1, 0, 0)), ("west", (-1, 0, 0)),
			("up", (0, 1, 0)), ("down", (0, -1, 0))):
			if is_solid_full(b.t.get(x + dx, y + dy, z + dz)):
				faces[d] = "true"
		if not faces:
			continue
		v = 0.5 + 0.5 * n.fbm3(x * 0.2 + 99, y * 0.2, z * 0.2, 2) * 1.6
		if v + (b.hrand(x, y, z, salt) - 0.5) * 0.3 < 1.0 - density:
			continue
		keep = dict(list(faces.items())[:2])
		b.t.set(x, y, z, B.fmt(block, keep))
		count += 1
	return count


def cobwebs(b, density=0.15, region=None, salt=51, min_solid=3):
	"""Cobwebs in interior corners (empty cells with several solid neighbours, including the ceiling)."""
	count = 0
	sx, sy, sz = b.size
	for x in range(sx):
		for z in range(sz):
			for y in range(b.ground + 1, sy):
				if not _in(region, x, y, z):
					continue
				s = b.t.get(x, y, z)
				if s is None or not s.startswith(AIR) or (x, y, z) in b.keep_clear:
					continue
				solid = 0
				for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0)):
					if is_structural(b.t.get(x + dx, y + dy, z + dz)) and not b.t.get(x + dx, y + dy, z + dz).startswith(AIR):
						solid += 1
				if solid >= min_solid and is_structural(b.t.get(x, y + 1, z) or "minecraft:air") and b.hrand(x, y, z, salt) < density:
					b.t.set(x, y, z, "minecraft:cobweb")
					count += 1
	return count


def snow(b, density=1.0, region=None, max_layers=2, salt=61):
	"""Snow layers on every top surface open to the sky (and some inside under breaches)."""
	count = 0
	for (x, y, z), s in list(b.t.cells.items()):
		if not _in(region, x, y + 1, z):
			continue
		if not is_solid_full(s) and not _top_slab(s):
			continue
		if not _empty(b, x, y + 1, z) or (x, y + 1, z) in b.keep_clear and max_layers > 1:
			continue
		if not _sky(b, x, y + 1, z):
			continue
		if b.hrand(x, y, z, salt) > density:
			continue
		layers = 1 + int(b.hrand(x, y, z, salt + 1) * max_layers)
		b.t.set(x, y + 1, z, f"minecraft:snow[layers={layers}]")
		count += 1
	return count


def _top_slab(s):
	return s is not None and "_slab" in s and ("type=top" in s or "type=double" in s)
