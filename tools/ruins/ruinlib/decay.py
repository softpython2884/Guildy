"""
Ruin decay: noise-driven damage, gravity-aware stabilisation (no floating blocks, bounded overhangs), cleanup of
attached blocks that lost their support, and rubble piles under the breaches.
"""
import heapq
import math
import random

from . import blocks as B
from .build import DIRS, OPP, AIR, is_structural, is_solid_full

UP_ATTACH = ("hanging_roots", "weeping_vines", "cave_vines", "spore_blossom", "pale_hanging_moss")
DOWN_ATTACH = ("torch", "candle", "carpet", "pressure_plate", "flower_pot", "potted_", "snow", "short_grass",
	"tall_grass", "fern", "dead_bush", "sapling", "mushroom", "roots", "sprouts", "fungus", "campfire", "skull",
	"head", "flower", "tulip", "poppy", "dandelion", "allium", "orchid", "bluet", "daisy", "cornflower",
	"lily_of_the_valley", "rose", "lilac", "peony", "bush", "petals", "leaf_litter", "twisting_vines", "door",
	"brewing_stand", "lectern", "cauldron", "chest", "barrel", "bell", "rail", "wildflowers", "dry_grass",
	"decorated_pot", "eyeblossom", "anvil")


def _name(state):
	return state.split("[", 1)[0].split(":")[1]


def remove(b, pos, debris=True):
	"""Removes a cell: air above the ground floor, natural ground (void) at or below it."""
	x, y, z = pos
	state = b.t.get(x, y, z)
	if state is None:
		return
	if debris and is_structural(state):
		b.debris.append((pos, state))
	b.t.set(x, y, z, AIR if y > b.ground else None)


def damage(b, fn, region=None):
	"""Removes every structural, unprotected cell for which fn(x, y, z, state) is true."""
	removed = 0
	for pos, state in list(b.t.cells.items()):
		if pos in b.protected or not is_structural(state) or state.startswith(AIR):
			continue
		if region is not None and not _in(region, pos):
			continue
		if fn(pos[0], pos[1], pos[2], state):
			remove(b, pos)
			removed += 1
	return removed


def breach(b, cx, cy, cz, rx, ry, rz, roughness=0.35, region=None):
	"""Ellipsoidal hole with a noisy outline (a collapsed part of a wall / roof)."""
	n = b.noise

	def fn(x, y, z, state):
		d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2
		return d < 1.0 + roughness * n.n3(x * 0.31, y * 0.31, z * 0.31)
	return damage(b, fn, region)


def erode(b, amount, top_bias=1.0, scale=0.17, region=None, y0=None, y1=None, match=None):
	"""
	Scattered decay: noise field + height bias (upper parts lose more). amount ~ 0..1. Only cells with at least one
	exposed face are eroded (ruins wear from the outside in).
	"""
	n = b.noise
	sy = b.size[1]
	lo = b.ground + 1 if y0 is None else y0
	hi = sy - 1 if y1 is None else y1

	def exposed(x, y, z):
		for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, 0, 1), (0, 0, -1)):
			s = b.t.get(x + dx, y + dy, z + dz)
			if s is None or s.startswith(AIR):
				return True
		return False

	def fn(x, y, z, state):
		if y < lo or y > hi:
			return False
		if match is not None and not match(state):
			return False
		if not exposed(x, y, z):
			return False
		h = (y - lo) / max(1, hi - lo)
		v = 0.5 + 0.5 * n.fbm3(x * scale, y * scale * 1.4, z * scale, 3) * 1.5
		threshold = 1.0 - amount * (0.35 + top_bias * h)
		return v + (b.hrand(x, y, z, 99) - 0.5) * 0.25 > threshold
	return damage(b, fn, region)


def _in(region, pos):
	(x0, y0, z0), (x1, y1, z1) = region
	return x0 <= pos[0] <= x1 and y0 <= pos[1] <= y1 and z0 <= pos[2] <= z1


def span_limit(state):
	"""How far a block may reach horizontally from a supported column (cantilever, lintel, floor span)."""
	name = _name(state)
	if any(k in name for k in ("planks", "_slab", "stairs")) and not any(k in name for k in ("brick", "stone", "tile",
		"deepslate", "blackstone", "sandstone", "quartz", "basalt", "tuff", "prismarine", "purpur", "mud")):
		return 9
	if name.endswith("_log") or name.endswith("_wood") or name.endswith("_stem") or "hyphae" in name:
		return 10
	if "stairs" in name or "slab" in name:
		return 6
	if any(k in name for k in ("fence", "wall", "bars", "pane", "chain")):
		return 3
	if any(k in name for k in ("ice", "snow")):
		return 3
	return 4


def stabilize(b, anchors=None, max_span=None, keep=None):
	"""
	Removes structural blocks that are not held by a column standing on the ground (or on an anchor), allowing
	horizontal spans up to span_limit(material). Removed blocks feed the rubble. Returns the number removed.
	"""
	cells = {p: s for p, s in b.t.cells.items() if is_structural(s) and not s.startswith(AIR)}
	dist = {}
	heap = []
	for p, s in cells.items():
		x, y, z = p
		below = b.t.get(x, y - 1, z)
		grounded = y <= b.ground or y == 0 or (below is None and y - 1 <= b.ground)
		if grounded or p in b.protected or (anchors is not None and p in anchors):
			dist[p] = 0
			heapq.heappush(heap, (0, p))
	while heap:
		d, p = heapq.heappop(heap)
		if dist.get(p, 1 << 30) < d:
			continue
		x, y, z = p
		up = (x, y + 1, z)
		if up in cells and dist.get(up, 1 << 30) > d:
			dist[up] = d
			heapq.heappush(heap, (d, up))
		for dx, dz in DIRS.values():
			q = (x + dx, y, z + dz)
			if q in cells:
				nd = d + 1
				limit = (max_span or span_limit)(cells[q])
				if nd <= limit and dist.get(q, 1 << 30) > nd:
					dist[q] = nd
					heapq.heappush(heap, (nd, q))
		down = (x, y - 1, z)
		if down in cells:
			nd = d + 2
			if nd <= 2 and dist.get(down, 1 << 30) > nd:
				dist[down] = nd
				heapq.heappush(heap, (nd, down))
	removed = 0
	for p in cells:
		if p not in dist and (keep is None or p not in keep):
			remove(b, p)
			removed += 1
	return removed


def _needs(state):
	"""Support requirement of a non-structural block: list of (dx, dy, dz) of which ONE must be solid."""
	name = _name(state)
	props = B.parse(state)[1]
	if name.endswith("lantern"):
		return [(0, 1, 0)] if props.get("hanging") == "true" else [(0, -1, 0)]
	if "wall_torch" in name or name.endswith("_wall_sign") or name.endswith("wall_skull") or \
		name.endswith("wall_head") or name.endswith("wall_banner") or name == "ladder":
		f = props.get("facing", "north")
		dx, dz = DIRS[OPP[f]]
		return [(dx, 0, dz)]
	if name == "vine":
		faces = [(DIRS[d][0], 0, DIRS[d][1]) for d in ("north", "south", "east", "west") if props.get(d) == "true"]
		if props.get("up") == "true":
			faces.append((0, 1, 0))
		return faces or [(0, 1, 0)]
	if name in ("glow_lichen", "sculk_vein", "resin_clump"):
		faces = []
		for d, v in (("north", (0, 0, -1)), ("south", (0, 0, 1)), ("east", (1, 0, 0)), ("west", (-1, 0, 0)),
			("up", (0, 1, 0)), ("down", (0, -1, 0))):
			if props.get(d) == "true":
				faces.append(v)
		return faces
	if name.endswith("chain"):
		return [(0, 1, 0), (0, -1, 0)]
	if name == "cobweb":
		return [(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)]
	for k in UP_ATTACH:
		if k in name:
			return [(0, 1, 0)]
	for k in DOWN_ATTACH:
		if k in name:
			return [(0, -1, 0)]
	return None


def _holds(b, state, pos, offset):
	x, y, z = pos
	dx, dy, dz = offset
	other = b.t.get(x + dx, y + dy, z + dz)
	name = _name(state)
	if other is None:
		# void = natural terrain: assumed solid below the ground floor only
		return y + dy <= b.ground
	if other.startswith(AIR):
		return False
	on = _name(other)
	if name.endswith("chain"):
		# hangs from a chain / block above, or stands on a block
		return is_structural(other) or (offset == (0, 1, 0) and on.endswith("chain"))
	if name.endswith("lantern"):
		return is_structural(other) or on.endswith("chain")  # iron_chain, copper chains (26.x names)
	if offset == (0, 1, 0) and any(k in name for k in UP_ATTACH):
		return is_solid_full(other) or on == name or on == name.replace("_plant", "") or on + "_plant" == name or \
			on == name + "_plant"
	if name == "twisting_vines" or name == "twisting_vines_plant":
		return is_structural(other) or "twisting" in on
	if name == "cobweb":
		return is_structural(other)
	if offset == (0, -1, 0):
		return is_structural(other)
	return is_solid_full(other)


def cleanup(b, max_passes=6):
	"""Removes attached blocks (torches, lanterns, vines, carpets, plants...) whose support disappeared."""
	total = 0
	for _ in range(max_passes):
		changed = 0
		for pos, state in list(b.t.cells.items()):
			if state.startswith(AIR) or is_structural(state):
				continue
			needs = _needs(state)
			if not needs:
				continue
			name = _name(state)
			if name in ("vine", "glow_lichen"):
				props = B.parse(state)[1]
				keys = [k for k in ("north", "south", "east", "west", "up", "down") if props.get(k) == "true"]
				kept = {}
				for k in keys:
					v = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0),
						"down": (0, -1, 0)}[k]
					if _holds(b, state, pos, v):
						kept[k] = "true"
					elif name == "vine" and k != "up":
						above = b.t.get(pos[0], pos[1] + 1, pos[2])
						if above is not None and _name(above) == "vine" and B.parse(above)[1].get(k) == "true":
							kept[k] = "true"  # a hanging vine continues the one above it
				if len(kept) != len(keys):
					changed += 1
					if kept:
						new = {k: "false" for k in keys}
						new.update(kept)
						b.t.set(*pos, B.with_props(state, **new))
					else:
						b.t.set(*pos, AIR if pos[1] > b.ground else None)
				continue
			if not any(_holds(b, state, pos, off) for off in needs):
				b.t.set(*pos, AIR if pos[1] > b.ground else None)
				changed += 1
		total += changed
		if not changed:
			break
	return total


def rubble(b, mat, keep=0.4, scatter=1.6, top=None, region=None, max_height=4, seed=0, source=None):
	"""
	Turns part of the removed blocks into piles under the place they fell from: falling columns with a little
	horizontal scatter, then slope relaxation (angle of repose) and a softened top (slabs / stairs). Rubble never
	lands in keep_clear cells. mat: Mat / Mix for full blocks; top: Mat for slabs and stairs (defaults to mat).
	source(state) -> None (use mat), False (the block leaves no rubble) or a state (e.g. a fallen roof tile slab).
	"""
	rnd = random.Random(b.seed * 31 + seed)
	sx, sy, sz = b.size

	def floor_at(x, y, z):
		"""First free cell (y) going down from y, or None."""
		while y > 0:
			below = b.t.get(x, y - 1, z)
			if below is None:
				if y - 1 <= b.ground:
					return y
				y -= 1
				continue
			if below.startswith(AIR):
				y -= 1
				continue
			if not is_structural(below):
				y -= 1
				continue
			return y
		return None

	def free(x, y, z):
		if not b.t.inside(x, y, z) or (x, y, z) in b.keep_clear or (x, y, z) in b.protected:
			return False
		s = b.t.get(x, y, z)
		if s is None:
			return y > b.ground
		return s.startswith(AIR) or not is_structural(s)

	placed = []
	for (x, y, z), state in b.debris:
		if rnd.random() > keep:
			continue
		if region is not None and not _in(region, (x, y, z)):
			continue
		a = rnd.random() * math.tau
		r = min(scatter * 2.5, rnd.random() ** 1.5 * scatter * (1 + (y - b.ground) / 12.0))
		px, pz = int(round(x + math.cos(a) * r)), int(round(z + math.sin(a) * r))
		if not (0 <= px < sx and 0 <= pz < sz):
			continue
		fy = floor_at(px, y, pz)
		if fy is None or not free(px, fy, pz):
			continue
		base = b.ground + 1
		if fy - base >= max_height:
			continue
		special = source(state) if source is not None else None
		if special is False:
			continue
		if special:
			b.t.set(px, fy, pz, special)
			continue
		b.t.set(px, fy, pz, mat.full(px, fy, pz))
		placed.append((px, fy, pz))
	# relax slopes: a block with an empty lower neighbour slides down
	for _ in range(3):
		moved = 0
		for (x, y, z) in list(placed):
			s = b.t.get(x, y, z)
			if s is None or s.startswith(AIR):
				continue
			if not b.empty(x, y + 1, z) and b.t.get(x, y + 1, z) is not None:
				continue
			options = []
			for dx, dz in DIRS.values():
				nx, nz = x + dx, z + dz
				if free(nx, y, nz) and free(nx, y - 1, nz) and y - 1 > b.ground:
					options.append((nx, nz))
			if options:
				nx, nz = rnd.choice(options)
				fy = floor_at(nx, y, nz)
				if fy is not None and free(nx, fy, nz):
					b.t.set(x, y, z, AIR)
					b.t.set(nx, fy, nz, s)
					placed.remove((x, y, z))
					placed.append((nx, fy, nz))
					moved += 1
		if not moved:
			break
	# soften the top of the piles
	top = top or mat
	for (x, y, z) in placed:
		if not b.empty(x, y + 1, z):
			continue
		s = b.t.get(x, y, z)
		if s is None or s.startswith(AIR):
			continue
		lower = [d for d, (dx, dz) in DIRS.items() if b.empty(x + dx, y, z + dz) and free(x + dx, y, z + dz)]
		r = rnd.random()
		if hasattr(top, "stairs") and lower and r < 0.45:
			high = [d for d in DIRS if d not in lower]
			facing = rnd.choice(high) if high else OPP[rnd.choice(lower)]
			b.t.set(x, y, z, top.stairs(x, y, z, facing, "bottom"))
		elif hasattr(top, "slab") and r < 0.7 and len(lower) >= 2:
			b.t.set(x, y, z, top.slab(x, y, z, "bottom"))
	return len(placed)
