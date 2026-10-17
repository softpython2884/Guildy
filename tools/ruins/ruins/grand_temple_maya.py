"""
Grand Temple du Jaguar céleste (tier 5, Overworld: jungle, bamboo jungle, sparse jungle). The landmark of the
jungles: rarer than every other ruin (its own structure set).

A ten-terrace pyramid climbing out of the canopy (69 x 69 at the base, 50 high), every terrace with a sloped talud,
an overhanging tablero of glyph panels and a projecting apron, inset corners, the upper courses still red with old
paint. The grand stairway (11 steps wide) climbs the south face between two feathered-serpent balustrades: the light
and shadow serpent zigzags down the west flank to the giant heads at its foot. On the summit platform stands the
temple of the Celestial Jaguar (portico with the chacmool, cella with the jaguar throne and the well of the world
tree) under a huge stepped roof comb carrying the jaguar sun mask. At its very top a real beacon (hidden iron base
inside the crown) sends a golden beam into the sky, seen from far across the jungle.

Around the plaza: the sacbe lined with stelae from the south edge to the stairway, two jaguar thrones, the platform
of Venus and the round observatory on its two platforms (south-west), the ball court with its rings, the temple of
the jaguars on its east wall, the hall of columns and the skull rack (south-east), the small temple of the moon
(north-west), the sacred cenote (north-east), giant ceibas (one grew on the north-east terraces and tore the corner
apart), bamboo, a fallen tree.

Inside, three ways lead to the Hall of the Celestial Jaguar (29 x 29, 15 high, eight jaguar-warrior pillars, the
altar and the throne, the treasure), dug under the heart of the pyramid:
  - the hidden way: down the gullet of the west serpent head, the gallery of glyphs (glowing walls), a curtain of
    glowing vines over the short stair down (or the long way: the gallery of waters, the hall of offerings and the
    stair of the nine lords), the antechamber of the jaguars and the south door of the hall; a trapped corridor of
    false doors leads to the hidden treasury;
  - the way of the king: up the stairway into the temple, down the spiral stair of the world tree well to the tomb
    of the king (carved sarcophagus lid, the nine lords of the night, the psychoduct), the descent of the soul (two
    flights) and the antechamber of the west door;
  - the way of the waters: the tunnel from the east door to the sacred cenote, a path cut into its wall up to the
    rim (the second way out).
Light falls into the hall by four shafts from the high terraces.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from ruinlib import build as K, decay, veg, deco, shapes  # noqa: E402
from ruinlib import blocks as B  # noqa: E402
from ruinlib import nbt as N  # noqa: E402
from ruinlib.build import Mat, Mix, AIR, DIRS, OPP, CW  # noqa: E402
from ruinlib.ruin import Ruin, split_pieces, rules  # noqa: E402
from ruins.temple_maya import leaves, blob, log_line, prune, pot, unprotect, double_plants  # noqa: E402

ID = "grand_temple_maya"
SEED = 8808
SX, SY, SZ = 112, 104, 116
G = 18  # ground floor layer
PX, PZ = 55, 35  # pyramid centre
TH = 5  # terrace height: two layers of talud, two of tablero, the apron
HALVES = [34, 31, 28, 25, 22, 20, 18, 16, 14, 12]  # outer half width of each terrace (steeper near the top)
TERRACES = len(HALVES)
TOPY = G + TH * TERRACES  # top layer of the last terrace = floor of the summit platform (68)
TOPH = HALVES[-1]
STAIR_FOOT = PZ + TOPH + (TOPY - G - 1)  # z of the first step of the grand stairway (block at G + 1): 96
STAIR_W = 5  # half width of the flight of steps (11 steps)
FEET = G - 12  # walking level of the hall of the Celestial Jaguar (6)
HALL = 14  # interior half width of the hall (29 x 29)
HALL_WALL = FEET + 8  # last layer of its vertical walls; corbel vault above, capstone at FEET + 15
CAP = FEET + 15
L1 = G - 6  # walking level of the galleries of the hidden way (12)
TOMB_FEET = G + 6  # walking level of the tomb of the king (24)
SANCT = (PX - 8, PZ - 12, PX + 8, PZ + 6)  # summit temple outline x0, z0, x1, z1
CELLA_Z = PZ + 1  # wall between the cella (north) and the portico (south)
WELL = (PX, PZ - 4)  # axis of the spiral stair of the world tree (under the cella)
COMB_BASE = TOPY + 13  # first layer of the roof comb (on the temple roof)
BEACON_Y = COMB_BASE + 20  # the beacon on the crown of the roof comb
BEACON = (PX, BEACON_Y, PZ - 4)
CEN = (100.5, float(PZ))  # sacred cenote (on the axis of the east door of the hall)
WATER = FEET - 1  # its water surface layer
HEADS = (PX - 10, PX + 4)  # west x of the two serpent heads (7 wide)
TOMB = (PX - 20, PZ - 8, PX - 8, PZ + 2)  # interior x0, z0, x1, z1 of the tomb of the king


def build():
	b = K.new(SX, SY, SZ, SEED, ground=G, name=ID)
	m = materials()
	foundations(b, m)
	pyramid(b, m)
	stairway(b, m)
	for x0 in HEADS:
		serpent_head(b, m, x0)
	temple(b, m)
	roof_comb(b, m)
	hall(b, m)
	well_stair(b, m)
	tomb(b, m)
	descent(b, m)
	hidden_way(b, m)
	cenote(b, m)
	plaza(b, m)
	observatory(b, m)
	ball_court(b, m)
	small_temple(b, m)
	b.envelop(m["deep"], thickness=1)
	ruin_it(b, m)
	life(b, m)
	b.carve_dome(PX, PZ + 20, 66.0, G + 1, 14, rough=3.0)
	b.open_clear_zones()
	shapes.fix(b.t)
	pieces = split_pieces(ID, b.t, G, jigsaw_xz=(0, SZ - 1))
	ruin = Ruin(ID, "Grand Temple du Jaguar céleste", "overworld", 5,
		["minecraft:jungle", "minecraft:bamboo_jungle", "minecraft:sparse_jungle"],
		"beard_thin", pieces, G, 0, terrain="minecraft:grass_block", processors=[rules(
			("minecraft:stone_bricks", "minecraft:cracked_stone_bricks", 0.04),
			("minecraft:cobblestone", "minecraft:mossy_cobblestone", 0.2),
			("minecraft:polished_andesite", "minecraft:mossy_stone_bricks", 0.05))])
	ruin.grand = True
	ruin.structure_set = "ruins_landmark"
	ruin.plan_levels = [(G + 1, "Plan : place, pyramide, observatoire, jeu de balle, cénote"),
		(L1, "Plan : galeries de la voie cachée"), (FEET, "Plan : salle du Jaguar céleste (arène)"),
		(TOMB_FEET, "Plan : tombeau du roi"), (TOPY + 1, "Plan : temple du sommet")]
	ruin.cut_level = G + 3
	ruin.under_cut = FEET + 4
	ruin.sections = [("x", PX, "Coupe nord-sud dans l'axe : escalier, puits de l'arbre du monde, salle, tête du serpent"),
		("z", PZ, "Coupe est-ouest par le centre : tombeau du roi, descente de l'âme, salle, tunnel du cénote"),
		("z", PZ + HALL + 23, "Coupe est-ouest au sud : galerie des glyphes, couloir piégé, galerie des eaux")]
	return ruin


def materials():
	return {
		"body": Mat("stone_brick", mossy="mossy_stone_brick", cracked="minecraft:cracked_stone_bricks", moss=0.6,
			moss_top=0.08, crack=0.2, crack_top=0.08, y_low=G + 1, y_high=TOPY, seed=SEED,
			extra=[("minecraft:andesite", 0.03), ("minecraft:tuff", 0.03), ("minecraft:cobblestone", 0.02)]),
		"talud": Mat("cobblestone", mossy="mossy_cobblestone", moss=0.66, moss_top=0.18, y_low=G + 1, y_high=TOPY,
			seed=SEED + 1, extra=[("minecraft:stone_bricks", 0.2)]),
		"apron": Mat("polished_andesite", mossy="mossy_stone_brick", moss=0.32, moss_top=0.04, y_low=G + 1,
			y_high=TOPY, seed=SEED + 2, extra=[("minecraft:stone_bricks", 0.1), ("minecraft:smooth_stone", 0.05)]),
		"ledge": Mix([("minecraft:stone_bricks", 4), ("minecraft:mossy_stone_bricks", 4), ("minecraft:moss_block", 2),
			("minecraft:cracked_stone_bricks", 1), ("minecraft:cobblestone", 1), ("minecraft:mossy_cobblestone", 2)],
			seed=SEED + 3, scale=0.22),
		"tuff": Mat("tuff_brick", seed=SEED + 4, extra=[("minecraft:polished_tuff", 0.2),
			("minecraft:chiseled_tuff_bricks", 0.03)]),
		"ptuff": Mat("polished_tuff", seed=SEED + 5, extra=[("minecraft:tuff_bricks", 0.15)]),
		"steps": Mat("stone_brick", mossy="mossy_stone_brick", cracked="minecraft:cracked_stone_bricks", moss=0.4,
			moss_top=0.06, crack=0.14, y_low=G + 1, y_high=TOPY, seed=SEED + 6),
		"core": Mix([("minecraft:cobblestone", 4), ("minecraft:stone", 3), ("minecraft:andesite", 2), ("minecraft:tuff", 2),
			("minecraft:mossy_cobblestone", 1), ("minecraft:coarse_dirt", 1)], seed=SEED + 7),
		"pave": Mix([("minecraft:stone_bricks", 4), ("minecraft:mossy_stone_bricks", 4), ("minecraft:cracked_stone_bricks", 2),
			("minecraft:cobblestone", 2), ("minecraft:mossy_cobblestone", 3), ("minecraft:moss_block", 2),
			("minecraft:coarse_dirt", 1), ("minecraft:polished_andesite", 1)], seed=SEED + 8, scale=0.2),
		"lime": Mat("polished_diorite", seed=SEED + 9, alt="diorite", extra=[("minecraft:calcite", 0.22),
			("minecraft:diorite", 0.12), ("minecraft:mossy_stone_bricks", 0.05)]),
		"jade": Mat(K.Fam("minecraft:oxidized_cut_copper", "minecraft:oxidized_cut_copper_stairs",
			"minecraft:oxidized_cut_copper_slab", None), seed=SEED + 10,
			extra=[("minecraft:oxidized_chiseled_copper", 0.25), ("minecraft:oxidized_copper", 0.15)]),
		"red": Mix([("minecraft:red_terracotta", 5), ("minecraft:terracotta", 2), ("minecraft:granite", 1)], seed=SEED + 11),
		"rubble": Mix([("minecraft:cobblestone", 3), ("minecraft:mossy_cobblestone", 4), ("minecraft:stone_bricks", 2),
			("minecraft:mossy_stone_bricks", 2), ("minecraft:cracked_stone_bricks", 2), ("minecraft:andesite", 1),
			("minecraft:tuff", 1), ("minecraft:moss_block", 1), ("minecraft:coarse_dirt", 1)], seed=SEED + 12),
		"rock": Mix([("minecraft:stone", 5), ("minecraft:andesite", 3), ("minecraft:tuff", 2), ("minecraft:diorite", 1),
			("minecraft:mossy_cobblestone", 2), ("minecraft:cobblestone", 1), ("minecraft:dripstone_block", 1)],
			seed=SEED + 13, scale=0.2),
		"soil": Mix([("minecraft:dirt", 4), ("minecraft:rooted_dirt", 3), ("minecraft:coarse_dirt", 2), ("minecraft:mud", 1)],
			seed=SEED + 14),
		"hfloor": Mix([("minecraft:polished_tuff", 4), ("minecraft:tuff_bricks", 3), ("minecraft:stone_bricks", 2),
			("minecraft:cracked_stone_bricks", 1), ("minecraft:mossy_stone_bricks", 1)], seed=SEED + 15),
		# the painted limestone of the summit temple and the plaster of the upper courses
		"plaster": Mat("polished_diorite", seed=SEED + 16, alt="diorite", extra=[("minecraft:calcite", 0.3),
			("minecraft:smooth_stone", 0.06)]),
		"redpaint": Mix([("minecraft:red_terracotta", 8), ("minecraft:terracotta", 1), ("minecraft:granite", 1),
			("minecraft:polished_diorite", 1), ("minecraft:stone_bricks", 1)], seed=SEED + 18, scale=0.25),
		"deep": Mat("tuff_brick", seed=SEED + 17, extra=[("minecraft:polished_tuff", 0.25), ("minecraft:tuff", 0.1),
			("minecraft:chiseled_tuff_bricks", 0.02)]),
	}


# ------------------------------------------------------------------------------------------------ geometry

def ring(x, z, cx=PX, cz=PZ):
	return max(abs(x - cx), abs(z - cz))


def terrace_of(y):
	k = (y - G - 1) // TH
	return k if y > G and 0 <= k < TERRACES else None


def half_at(y):
	k = terrace_of(y)
	return None if k is None else HALVES[k]


def outward(x, z, cx=PX, cz=PZ):
	dx, dz = x - cx, z - cz
	if abs(dx) >= abs(dz):
		return "east" if dx > 0 else "west"
	return "south" if dz > 0 else "north"


def along(x, z, cx=PX, cz=PZ):
	"""Coordinate of a ring cell parallel to its face, from the centre line."""
	return z - cz if outward(x, z, cx, cz) in ("east", "west") else x - cx


def dist(x, z, c):
	return math.hypot(x - c[0], z - c[1])


def step_y(z):
	"""Layer of the stair block of the grand stairway at z (TOPY at the platform edge)."""
	return TOPY - (z - (PZ + TOPH))


def room(b, m, x0, z0, x1, z1, feet, height, floor=None, wall=None, ceil=None):
	"""Box room: walls (only where nothing open is), floor, air, flat ceiling."""
	wall = wall or m["deep"]
	for x in range(x0 - 1, x1 + 2):
		for z in range(z0 - 1, z1 + 2):
			if x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1):
				for y in range(feet - 1, feet + height + 1):
					if not b.is_air(x, y, z):
						b.set(x, y, z, wall.full(x, y, z))
	for x in range(x0, x1 + 1):
		for z in range(z0, z1 + 1):
			b.set(x, feet - 1, z, floor.full(x, feet - 1, z) if hasattr(floor, "full") else
				floor or ("minecraft:polished_tuff" if (x + z) % 2 else "minecraft:tuff_bricks"))
			for y in range(feet, feet + height):
				b.set(x, y, z, AIR)
			b.set(x, feet + height, z, (ceil or m["ptuff"]).full(x, feet + height, z))


def corbel(b, m, x0, z0, x1, z1, feet, wall_h, axis, mat=None, cap=None):
	"""
	Corbel-vaulted gallery / room: vertical walls `wall_h` high, then the two long walls step inwards one block per
	layer (upside-down stairs) until they meet under a capstone. axis 'x': the gallery runs along x (the vault closes
	across z). Returns the capstone layer.
	"""
	mat = mat or m["ptuff"]
	room(b, m, x0, z0, x1, z1, feet, wall_h, ceil=m["deep"])
	lo, hi = (z0, z1) if axis == "x" else (x0, x1)
	a0, a1 = (x0, x1) if axis == "x" else (z0, z1)
	y = feet + wall_h
	k = 0
	while lo + k <= hi - k:
		for a in range(a0, a1 + 1):
			for c in range(lo + k, hi - k + 1):
				x, z = (a, c) if axis == "x" else (c, a)
				b.set(x, y, z, AIR)
			if lo + k < hi - k:
				c1, c2 = lo + k, hi - k
				if axis == "x":
					b.set(a, y, c1 - 1, mat.stairs(a, y, c1 - 1, "north", "top") if k else m["deep"].full(a, y, c1 - 1))
					b.set(a, y, c2 + 1, mat.stairs(a, y, c2 + 1, "south", "top") if k else m["deep"].full(a, y, c2 + 1))
				else:
					b.set(c1 - 1, y, a, mat.stairs(c1 - 1, y, a, "west", "top") if k else m["deep"].full(c1 - 1, y, a))
					b.set(c2 + 1, y, a, mat.stairs(c2 + 1, y, a, "east", "top") if k else m["deep"].full(c2 + 1, y, a))
		for a in range(a0 - 1, a1 + 2):
			for c in (lo + k - 1, hi - k + 1):
				x, z = (a, c) if axis == "x" else (c, a)
				if not b.is_air(x, y, z) and b.get(x, y, z) is None:
					b.set(x, y, z, m["deep"].full(x, y, z))
		y += 1
		k += 1
	for a in range(a0 - 1, a1 + 2):
		for c in range(lo - 1, hi + 2):
			x, z = (a, c) if axis == "x" else (c, a)
			if b.get(x, y, z) is None or not b.is_air(x, y, z):
				b.set(x, y, z, (cap or m["ptuff"]).full(x, y, z))
	return y


def hang(b, x, y, z, chain=1, soul=False):
	"""A copper lantern hanging from `chain` links of copper chain under the ceiling cell above them."""
	for k in range(1, chain + 1):
		b.t.set(x, y + k, z, "minecraft:oxidized_copper_chain[axis=y,waterlogged=false]")
	b.t.set(x, y, z, "minecraft:soul_lantern[hanging=true,waterlogged=false]" if soul else
		"minecraft:oxidized_copper_lantern[hanging=true,waterlogged=false]")


def door(b, x0, z0, x1, z1, feet, height=3):
	for x in range(min(x0, x1), max(x0, x1) + 1):
		for z in range(min(z0, z1), max(z0, z1) + 1):
			for y in range(feet, feet + height):
				b.set(x, y, z, AIR)
	b.clear_zone(x0, feet, z0, x1, feet + 1, z1)


def flight(b, m, x, z, go, steps, feet, width, mat=None, head=3, wall=True, light_every=0):
	"""
	Straight stair going DOWN toward `go`, `width` cells to the right of the walking direction, from walking level
	`feet` (the first step is one below). Carves the headroom, walls the sides (and the ceiling) where nothing is
	open. Returns (x, z, feet) of the cell after the last step.
	"""
	mat = mat or m["steps"]
	gx, gz = DIRS[go]
	rx, rz = DIRS[CW[go]]
	for s in range(steps):
		y = feet - 1 - s
		for w in range(-1, width + 1):
			cx, cz = x + gx * s + rx * w, z + gz * s + rz * w
			if 0 <= w < width:
				b.set(cx, y - 1, cz, m["deep"].full(cx, y - 1, cz))
				b.set(cx, y, cz, mat.stairs(cx, y, cz, OPP[go], "bottom"))
				for h in range(1, head + 1):
					b.set(cx, y + h, cz, AIR)
				b.set(cx, y + head + 1, cz, m["ptuff"].full(cx, y + head + 1, cz))
				b.clear_zone(cx, y + 1, cz, cx, y + 2, cz)
			elif wall:
				for h in range(-1, head + 2):
					if not b.is_air(cx, y + h, cz):
						b.set(cx, y + h, cz, m["deep"].full(cx, y + h, cz))
		if light_every and s % light_every == light_every // 2:
			lx, lz = x + gx * s - rx, z + gz * s - rz
			if not b.is_air(lx, y + 2, lz):
				b.set(lx, y + 2, lz, "minecraft:verdant_froglight[axis=y]")
	return x + gx * steps, z + gz * steps, feet - steps


# --------------------------------------------------------------------------------------------- foundations

def foundations(b, m):
	"""Solid core under the pyramid and the stairway (the rooms are carved in it later) and the basal platform."""
	core = m["core"]
	for x in range(PX - HALVES[0] - 3, PX + HALVES[0] + 4):
		for z in range(PZ - HALVES[0] - 3, PZ + HALVES[0] + 4):
			d = ring(x, z)
			if d <= HALVES[0]:
				for y in range(G - 4, G + 1):
					b.set(x, y, z, core)
			elif d <= HALVES[0] + 2:
				for y in range(G - 2, G):
					b.set(x, y, z, core)
				b.set(x, G, z, m["pave"])
			elif d == HALVES[0] + 3 and b.hrand(x, 0, z, 3) < 0.75:
				b.set(x, G, z, m["apron"].full(x, G, z))


# ------------------------------------------------------------------------------------------------- pyramid

GLYPHS = [
	("CPC", "PRP"),
	("RCR", "CPC"),
	("PJP", "RCR"),
	("CRC", "RPR"),
	("PCP", "CJC"),
	("RRC", "CPP"),
	("JCJ", "PRP"),
]


def panel_block(b, m, x, y, z, k, l, cx=PX, cz=PZ, paint=True):
	"""
	The carved glyph panels of the tablero (seen between the frame posts): windows of 3 x 2, each a glyph of chiseled
	tuff, plain tuff, jade inlays and (on the painted courses) red, worn in places.
	"""
	a = along(x, z, cx, cz)
	window = (a + 2) // 4
	col = (a + 2) % 4 - 1
	g = GLYPHS[int(b.hrand(window, k, outward(x, z, cx, cz) == "north", 5) * len(GLYPHS))]
	ch = g[0 if l == 3 else 1][max(0, min(2, col))]
	if b.hrand(x, y, z, 19) < 0.15:
		ch = "W"
	return {
		"C": "minecraft:chiseled_tuff_bricks",
		"P": "minecraft:polished_tuff",
		"R": m["red"].full(x, y, z) if paint else "minecraft:tuff_bricks",
		"J": "minecraft:oxidized_chiseled_copper",
		"W": "minecraft:tuff",
	}[ch]


def band(k):
	"""Courses of the pyramid: 0 = the three lower terraces (mossy, the jungle climbs them), 1 = the middle ones,
	2 = the three upper ones, still white and red with the old plaster and paint."""
	return 0 if k <= 2 else (1 if k <= 6 else 2)


def terrace_cell(b, m, k, h, x, y, z, l, last):
	"""
	One cell of terrace k (outer half h) at layer l (0..4): talud (two layers of sloped stairs, the upper one set back),
	tablero (frame posts every 4 cells in front of the recessed glyph panels, projecting over the talud), apron. Lower
	courses mossy, upper ones plastered white (talud, posts) with a red apron.
	"""
	body, apron, ledge, core = m["body"], m["apron"], m["ledge"], m["core"]
	bd = band(k)
	talud = (m["talud"], m["steps"], m["plaster"])[bd]
	post_mat = (body, apron, m["plaster"])[bd]
	d = ring(x, z)
	e = h - d  # depth from the outer face
	out = outward(x, z)
	inward = OPP[out]
	a = abs(along(x, z))
	corner = abs(x - PX) >= h - 1 and abs(z - PZ) >= h - 1  # inset corners
	top_mat = m["redpaint"] if bd == 2 else apron
	if last and l == TH - 1:
		if e >= 2:
			return "minecraft:polished_tuff" if (x + z) % 2 else "minecraft:tuff_bricks"
		return top_mat.full(x, y, z)
	if e >= 3:
		return core.full(x, y, z) if l < TH - 1 else ledge.full(x, y, z)
	if corner:
		# the corners step back one block: a vertical re-entrant edge, apron on top
		if abs(x - PX) == h and abs(z - PZ) == h:
			return AIR if l < TH - 1 else None
		if e == 0:
			return AIR if l < TH - 1 else top_mat.full(x, y, z)
		return top_mat.full(x, y, z) if l == TH - 1 else post_mat.full(x, y, z)
	if l == 0:
		if e == 0:
			return talud.stairs(x, y, z, inward, "bottom")
		return body.full(x, y, z)
	if l == 1:
		if e == 0:
			return AIR
		if e == 1:
			return talud.stairs(x, y, z, inward, "bottom")
		return body.full(x, y, z)
	if l in (2, 3):
		post = a % 4 == 2 or a >= h - 2
		if e == 0:
			if post:
				return post_mat.full(x, y, z)
			return AIR
		if e == 1:
			return panel_block(b, m, x, y, z, k, l, paint=bd == 2) if not post else post_mat.full(x, y, z)
		return body.full(x, y, z)
	# apron
	if e <= 1:
		return top_mat.full(x, y, z)
	return ledge.full(x, y, z)


def pyramid(b, m):
	apron = m["apron"]
	for k, h in enumerate(HALVES):
		y0 = G + 1 + TH * k
		last = k == TERRACES - 1
		for x in range(PX - h, PX + h + 1):
			for z in range(PZ - h, PZ + h + 1):
				for l in range(TH):
					s = terrace_cell(b, m, k, h, x, y0 + l, z, l, last)
					if s is None:
						continue
					if s == AIR:
						if b.get(x, y0 + l, z) is None:
							continue
					b.set(x, y0 + l, z, s)
				# the apron overhangs the walkway below
				d = ring(x, z)
				corner = abs(x - PX) >= h - 1 and abs(z - PZ) >= h - 1
				if d == h and not corner:
					out = outward(x, z)
					dx, dz = DIRS[out]
					ox, oz = x + dx, z + dz
					yy = y0 + TH - 1
					if b.empty(ox, yy, oz):
						b.set(ox, yy, oz, B.fmt("minecraft:polished_granite_stairs", {"facing": OPP[out], "half": "top",
							"shape": "straight", "waterlogged": "false"}) if band(k) == 2 and b.hrand(ox, yy, oz, 35) < 0.8
							else apron.stairs(ox, yy, oz, OPP[out], "top"))
	# the inside of the pyramid stays out of the decay (only its skin wears, except where it collapses)
	for x in range(PX - HALVES[0], PX + HALVES[0] + 1):
		for z in range(PZ - HALVES[0], PZ + HALVES[0] + 1):
			d = ring(x, z)
			for y in range(G - 4, TOPY + 1):
				h = half_at(y)
				if y <= G or (h is not None and d <= h - 3):
					b.protected.add((x, y, z))


# ----------------------------------------------------------------------------------------- grand stairway

def stairway(b, m):
	"""
	The grand stairway: 11 steps wide between two balustrades (3 wide, a sloped parapet scaled with jade feathers),
	from the summit platform down to the plaza. The projecting part stands on its own flanks: the west flank carries
	the serpent of light and shadow (seven triangles of light on dark stone, the body of the serpent whose head lies
	at the foot of the balustrade).
	"""
	steps, body, tuff, jade, core = m["steps"], m["body"], m["tuff"], m["jade"], m["core"]
	xw, xe = PX - STAIR_W - 3, PX + STAIR_W + 3
	for z in range(PZ + TOPH + 1, STAIR_FOOT + 1):
		y = step_y(z)
		beyond = z > PZ + HALVES[0]
		for x in range(xw, xe + 1):
			flank = x in (xw, xe)
			for yy in range(G, y):
				if flank and beyond:
					b.set(x, yy, z, flank_block(b, m, x, yy, z))
				elif beyond or b.get(x, yy, z) is None or b.is_air(x, yy, z) or flank or ring(x, z) > (half_at(yy) or 0) - 3:
					b.set(x, yy, z, core.full(x, yy, z) if not flank else body.full(x, yy, z))
			if abs(x - PX) <= STAIR_W:
				b.set(x, y, z, steps.stairs(x, y, z, "north", "bottom"))
				for h in range(1, 5):
					b.set(x, y + h, z, AIR)
				b.clear_zone(x, y + 1, z, x, y + 2, z)
			else:
				# balustrade: a sloped parapet one block above the steps, scaled with jade feathers
				b.set(x, y, z, tuff.full(x, y, z))
				feather = (z - STAIR_FOOT) % 3 == 0
				b.set(x, y + 1, z, jade.stairs(x, y + 1, z, "north", "bottom") if feather and flank else
					tuff.stairs(x, y + 1, z, "north", "bottom"))
				for h in range(2, 4):
					b.set(x, y + h, z, AIR)
		if beyond:
			# plinth along the flanks of the projecting stairway
			for x, f in ((xw - 1, "east"), (xe + 1, "west")):
				b.set(x, G + 1, z, m["talud"].stairs(x, G + 1, z, f, "bottom"))
			# the serpent of light stays bare (no vines over it)
			b.clear_zone(xw - 1, G + 2, z, xw - 1, step_y(z), z)
	# the platform edge: pedestals with braziers at the top of the balustrades
	for x in (xw, xw + 1, xe - 1, xe):
		b.set(x, TOPY + 1, PZ + TOPH, tuff.full(x, TOPY + 1, PZ + TOPH))
		b.set(x, TOPY + 2, PZ + TOPH, "minecraft:chiseled_tuff_bricks" if x in (xw, xe) else
			tuff.slab(x, TOPY + 2, PZ + TOPH, "bottom"))
	for x in (xw, xe):
		b.t.set(x, TOPY + 3, PZ + TOPH, "minecraft:campfire[facing=south,lit=false,signal_fire=false]",
			N.Compound(id=N.String("minecraft:campfire")))
	b.protect(PX - STAIR_W, G, PZ + TOPH + 1, PX + STAIR_W, TOPY, STAIR_FOOT)


def flank_block(b, m, x, y, z):
	"""
	The flanks of the projecting stairway. West flank: the serpent of light and shadow, a band under the balustrade
	where triangles of light (pale limestone) alternate with the shadow (dark tuff), the body of the serpent whose
	head lies at the foot; east flank: the terrace bands and glyph panels continued along the stairway.
	"""
	top = step_y(z)  # the balustrade parapet stands on top + 1
	below = top - y  # depth under the parapet
	lvl = (y - G - 1) % TH
	if x > PX:
		if lvl == TH - 1:
			return m["apron"].full(x, y, z)
		if lvl in (2, 3) and below > 1:
			return panel_block(b, m, x, y, z, 0, lvl, x - 100, 0, paint=False) if z % 4 != 3 else m["apron"].full(x, y, z)
		return m["body"].full(x, y, z)
	n_tri = 5
	z0, z1 = PZ + HALVES[0] + 1, STAIR_FOOT
	frac = (z - z0) / max(1.0, (z1 - z0 + 1)) * n_tri
	t = frac - int(frac)
	depth = 8.5 * (1.0 - abs(2.0 * t - 1.0))  # triangle of light, apex pointing down
	if below <= 9:
		if below < depth:
			return "minecraft:calcite" if (y + z) % 4 else "minecraft:polished_diorite"
		return "minecraft:polished_tuff" if (y + z) % 2 else "minecraft:tuff_bricks"
	return m["apron"].full(x, y, z) if lvl == TH - 1 else m["body"].full(x, y, z)


def serpent_head(b, m, x0):
	"""
	Giant feathered-serpent head (7 wide, 11 long, columns x0..x0+6) at the foot of a balustrade, facing south: the
	jaws gape (a 5 x 3 mouth) with hanging and rising fangs, the forked red tongue runs out over the lower jaw, the
	snout curls up, glowing eyes under heavy brows on the sides, a collar of jade feathers behind. The gullet of the
	west head goes down into the earth (the hidden way).
	"""
	z0 = STAIR_FOOT + 1
	tuff, jade, ptuff = m["tuff"], m["jade"], m["ptuff"]
	fang_down = "minecraft:pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]"
	fang_up = "minecraft:pointed_dripstone[thickness=tip,vertical_direction=up,waterlogged=false]"
	zf = z0 + 10  # tip of the lower jaw
	for x in range(x0 - 1, x0 + 8):
		for z in range(z0 - 1, zf + 2):
			b.set(x, G, z, "minecraft:polished_tuff" if (x + z) % 3 else "minecraft:tuff_bricks")
	for x in range(x0, x0 + 7):
		i = x - x0
		side = i in (0, 6)
		for z in range(z0, zf + 1):
			k = z - z0  # 0 at the neck, 10 at the tip of the jaw
			# lower jaw (G + 1): the full width, narrowing at the tip
			if k <= 9 or 1 <= i <= 5:
				b.set(x, G + 1, z, tuff.full(x, G + 1, z) if (side or k < 2) else ptuff.full(x, G + 1, z))
			# the mouth (G + 2 .. G + 4) between the cheeks
			for y in (G + 2, G + 3, G + 4):
				if k < 2:
					s_ = tuff.full(x, y, z)
				elif side:
					s_ = tuff.full(x, y, z) if k <= 7 else AIR
				else:
					s_ = AIR
				b.set(x, y, z, s_)
			# upper jaw (G + 5) and the snout curling up at its tip (G + 6 .. G + 7)
			if k <= 8:
				b.set(x, G + 5, z, tuff.full(x, G + 5, z))
			elif k == 9 and 1 <= i <= 5:
				b.set(x, G + 5, z, tuff.stairs(x, G + 5, z, "north", "top"))
			if k <= 6:
				b.set(x, G + 6, z, tuff.full(x, G + 6, z) if (k < 5 or 1 <= i <= 5) else AIR)
			elif k == 7:
				b.set(x, G + 6, z, tuff.stairs(x, G + 6, z, "north", "bottom") if 1 <= i <= 5 else AIR)
			elif k == 8 and 2 <= i <= 4:
				b.set(x, G + 6, z, jade.stairs(x, G + 6, z, "north", "bottom") if i != 3 else "minecraft:oxidized_chiseled_copper")
			if k <= 4:
				b.set(x, G + 7, z, tuff.full(x, G + 7, z) if k < 4 or not side else tuff.stairs(x, G + 7, z, "north", "bottom"))
			elif k == 5 and not side:
				b.set(x, G + 7, z, tuff.stairs(x, G + 7, z, "north", "bottom"))
		# the eyes under their brows, the scales of the crown of the head
		if side:
			b.set(x, G + 6, z0 + 4, "minecraft:ochre_froglight[axis=y]")
			b.set(x, G + 6, z0 + 5, "minecraft:ochre_froglight[axis=y]")
			b.set(x, G + 7, z0 + 4, "minecraft:chiseled_tuff_bricks")
		else:
			b.set(x, G + 7, z0 + 2, "minecraft:chiseled_tuff_bricks" if i % 2 else tuff.full(x, G + 7, z0 + 2))
		# collar of jade feathers behind the head
		for z, y in ((z0, G + 8), (z0 + 1, G + 8), (z0, G + 9)):
			if y == G + 9 and side:
				continue
			b.set(x, y, z, jade.stairs(x, y, z, "north", "bottom") if (z == z0 + 1 or y == G + 9) else
				"minecraft:oxidized_chiseled_copper")
	# fangs: hanging from the upper jaw at the front corners of the mouth, rising from the lower jaw
	for x in (x0 + 1, x0 + 5):
		b.set(x, G + 4, z0 + 8, fang_down)
		b.set(x, G + 4, z0 + 7, fang_down)
		b.set(x, G + 2, z0 + 9, fang_up)
	for x in (x0 + 2, x0 + 4):
		b.set(x, G + 4, z0 + 8, fang_down)
	# the forked tongue running out of the mouth
	b.set(x0 + 3, G + 1, zf, "minecraft:red_terracotta")
	for z in range(z0 + 2, zf):
		b.set(x0 + 3, G + 1, z, "minecraft:red_terracotta")
	b.set(x0 + 2, G + 1, zf + 1, "minecraft:granite_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]")
	b.set(x0 + 4, G + 1, zf + 1, "minecraft:granite_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]")
	# feathered ears: plumes fanning out on both sides of the collar
	for x, f in ((x0 - 1, "east"), (x0 + 7, "west")):
		for z in (z0, z0 + 1, z0 + 2):
			b.set(x, G + 1, z, tuff.full(x, G + 1, z))
		b.set(x, G + 6, z0 + 1, jade.stairs(x, G + 6, z0 + 1, f, "top"))
		b.set(x, G + 7, z0, jade.stairs(x, G + 7, z0, f, "bottom"))
		b.set(x, G + 8, z0, jade.slab(x, G + 8, z0, "bottom"))
		for y in range(G + 2, G + 6):
			b.set(x, y, z0, tuff.full(x, y, z0) if y != G + 4 else "minecraft:oxidized_chiseled_copper")
	b.clear_zone(x0 + 1, G + 2, z0 + 2, x0 + 5, G + 3, zf)
	b.protect(x0 - 1, G, z0 - 1, x0 + 7, G + 9, zf + 1)


# ----------------------------------------------------------------------------------------------- the temple

def _side(x, z, x0, x1, z0, z1):
	if x == x0:
		return "west"
	if x == x1:
		return "east"
	if z == z0:
		return "north"
	return "south"


def temple(b, m):
	"""
	The temple of the Celestial Jaguar on the summit platform: limestone walls painted red, a frieze of glyphs and
	jade masks, a portico (three doors, the chacmool) and the cella (the jaguar throne, the altar, the well of the
	world tree going down into the pyramid), both under corbel vaults.
	"""
	tuff, ptuff, jade, plaster = m["tuff"], m["ptuff"], m["jade"], m["plaster"]
	x0, z0, x1, z1 = SANCT
	T = TOPY
	for x in range(x0, x1 + 1):
		for z in range(z0, z1 + 1):
			edge = x in (x0, x1) or z in (z0, z1)
			for y in range(T + 1, T + 13):
				if y <= T + 6:
					s = (plaster.full(x, y, z) if y > T + 1 else ptuff.full(x, y, z)) if (edge or z == CELLA_Z) else AIR
				else:
					s = tuff.full(x, y, z) if edge else ptuff.full(x, y, z)
				b.set(x, y, z, s)
	# corbel vaults: portico (z CELLA_Z+1 .. z1-1) and cella (z0+1 .. CELLA_Z-1)
	for (za, zb) in ((CELLA_Z + 1, z1 - 1), (z0 + 1, CELLA_Z - 1)):
		for k in range(0, 5):
			y = T + 7 + k
			lo, hi = za + k, zb - k
			if lo > hi:
				break
			for x in range(x0 + 1, x1):
				for z in range(lo, hi + 1):
					b.set(x, y, z, AIR)
				if lo < hi:
					b.set(x, y, lo, ptuff.stairs(x, y, lo, "north", "top"))
					b.set(x, y, hi, ptuff.stairs(x, y, hi, "south", "top"))
	for z in (CELLA_Z + 2, PZ - 3, PZ - 7):
		for x in range(x0 + 1, x1):
			b.set(x, T + 6, z, "minecraft:stripped_jungle_log[axis=x]")
	# base molding, pilasters and piers of the front
	for x in range(x0 - 1, x1 + 2):
		for z in range(z0 - 1, z1 + 2):
			if not (x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)):
				continue
			if x in (x0 - 1, x1 + 1) and z in (z0 - 1, z1 + 1):
				continue
			f = OPP[_side(x, z, x0 - 1, x1 + 1, z0 - 1, z1 + 1)]
			b.set(x, T + 1, z, tuff.stairs(x, T + 1, z, f, "bottom"))
			b.set(x, T + 7, z, tuff.stairs(x, T + 7, z, f, "top"))
			b.set(x, T + 12, z, ptuff.stairs(x, T + 12, z, f, "top"))
	for (x, z) in ((x0, z1 + 1), (x1, z1 + 1), (PX - 3, z1 + 1), (PX + 3, z1 + 1), (x0, z0 - 1), (x1, z0 - 1),
		(x0 - 1, z0), (x1 + 1, z0), (x0 - 1, z1), (x1 + 1, z1), (x0 - 1, CELLA_Z), (x1 + 1, CELLA_Z)):
		for y in range(T + 1, T + 7):
			b.set(x, y, z, ptuff.full(x, y, z) if y != T + 5 else "minecraft:chiseled_tuff_bricks")
	# front: three doors with wooden lintels
	for (xa, xb) in ((PX - 1, PX + 1), (PX - 5, PX - 4), (PX + 4, PX + 5)):
		for x in range(xa, xb + 1):
			for y in range(T + 1, T + 5):
				b.set(x, y, z1, AIR)
			b.set(x, T + 5, z1, "minecraft:stripped_jungle_log[axis=x]")
		b.clear_zone(xa, T + 1, z1 - 1, xb, T + 2, z1 + 1)
	for x in range(PX - 1, PX + 2):
		for y in range(T + 1, T + 5):
			b.set(x, y, CELLA_Z, AIR)
		b.set(x, T + 5, CELLA_Z, "minecraft:stripped_jungle_log[axis=x]")
	b.clear_zone(PX - 1, T + 1, CELLA_Z - 1, PX + 1, T + 2, CELLA_Z + 1)
	# upper facade: a frieze painted red, glyph stones, jade masks
	for x in range(x0, x1 + 1):
		for z in range(z0, z1 + 1):
			if not (x in (x0, x1) or z in (z0, z1)):
				continue
			a = x - PX if z in (z0, z1) else z - PZ
			for y in (T + 8, T + 9, T + 10, T + 11):
				if y == T + 9:
					s = "minecraft:oxidized_chiseled_copper" if a % 4 == 0 else ("minecraft:chiseled_tuff_bricks" if a % 2 == 0
						else "minecraft:polished_tuff")
				elif y in (T + 8, T + 10):
					s = m["redpaint"].full(x, y, z)
				else:
					s = tuff.full(x, y, z)
				b.set(x, y, z, s)
	for x in range(x0, x1 + 1):
		for z in range(z0, z1 + 1):
			b.set(x, T + 12, z, ptuff.full(x, T + 12, z))
	# rain-god masks at the front corners, the jaguar mask over the main door, side masks
	for x in (x0, x1):
		b.set(x, T + 9, z1 + 1, "minecraft:oxidized_chiseled_copper")
		b.set(x, T + 8, z1 + 1, jade.stairs(x, T + 8, z1 + 1, "north", "top"))
		b.set(x, T + 10, z1 + 1, tuff.stairs(x, T + 10, z1 + 1, "north", "bottom"))
		b.set(x, T + 9, z1 + 2, "minecraft:oxidized_cut_copper_stairs[facing=north,half=top]")
	for x in range(PX - 2, PX + 3):
		b.set(x, T + 9, z1 + 1, "minecraft:verdant_froglight[axis=y]" if abs(x - PX) == 1 else
			"minecraft:oxidized_chiseled_copper" if x == PX else "minecraft:chiseled_tuff_bricks")
		b.set(x, T + 8, z1 + 1, jade.stairs(x, T + 8, z1 + 1, "north", "top") if abs(x - PX) <= 1 else
			tuff.stairs(x, T + 8, z1 + 1, "north", "top"))
		b.set(x, T + 10, z1 + 1, jade.stairs(x, T + 10, z1 + 1, "north", "bottom") if x == PX else
			tuff.slab(x, T + 10, z1 + 1, "bottom"))
	for x, f in ((x0 - 1, "east"), (x1 + 1, "west")):
		for z in (PZ - 5, PZ):
			b.set(x, T + 9, z, "minecraft:oxidized_chiseled_copper")
			b.set(x, T + 8, z, jade.stairs(x, T + 8, z, f, "top"))
			b.set(x, T + 10, z, tuff.stairs(x, T + 10, z, f, "bottom"))
	# floors: red-painted plaster in the cella, polished tuff and jade in the portico
	for x in range(x0 + 1, x1):
		for z in range(z0 + 1, z1):
			if z == CELLA_Z:
				continue
			b.set(x, T, z, ("minecraft:polished_tuff" if (x * 3 + z) % 4 else "minecraft:red_terracotta") if z < CELLA_Z
				else ("minecraft:oxidized_cut_copper" if (x - PX) % 4 == 0 else "minecraft:polished_tuff"))
	cella_props(b, m)
	b.protect(x0, T, z0, x1, T + 5, z1)


def cella_props(b, m):
	"""Jaguar throne against the back wall, offerings, the chacmool in the portico, lanterns."""
	tuff, ptuff = m["tuff"], m["ptuff"]
	x0, z0, x1, z1 = SANCT
	T = TOPY
	jaguar_throne(b, m, PX, T + 1, z0 + 1, "south")
	for x in (PX - 3, PX + 3):
		b.set(x, T + 1, z0 + 1, "minecraft:polished_tuff")
		pot(b, x, T + 2, z0 + 1, cracked=x < PX)
	deco.candles(b, PX - 4, T + 1, z0 + 1, 3, color="red")
	deco.candles(b, PX + 4, T + 1, z0 + 1, 4, color="green")
	deco.chest(b, x0 + 1, T + 1, CELLA_Z - 1, "east", "common", kind="barrel")
	pot(b, x1 - 1, T + 1, CELLA_Z - 1, cracked=True, facing="west")
	for (x, z) in ((PX - 4, PZ - 3), (PX + 4, PZ - 3), (PX, CELLA_Z + 2)):
		b.t.set(x, T + 5, z, "minecraft:oxidized_copper_lantern[hanging=true,waterlogged=false]")
	# the chacmool reclining at the west end of the portico, the offering bowl on its belly
	cz = CELLA_Z + 2
	b.set(x0 + 1, T + 1, cz, "minecraft:polished_tuff")
	b.set(x0 + 1, T + 2, cz, "minecraft:chiseled_tuff")
	b.set(x0 + 2, T + 1, cz, tuff.stairs(x0 + 2, T + 1, cz, "west", "bottom"))
	pot(b, x0 + 2, T + 2, cz, facing="east")
	b.set(x0 + 3, T + 1, cz, tuff.full(x0 + 3, T + 1, cz))
	b.set(x0 + 4, T + 1, cz, tuff.slab(x0 + 4, T + 1, cz, "bottom"))
	# a second offering table at the east end
	b.set(x1 - 1, T + 1, cz, ptuff.stairs(x1 - 1, T + 1, cz, "east", "top"))
	b.set(x1 - 2, T + 1, cz, ptuff.stairs(x1 - 2, T + 1, cz, "west", "top"))
	deco.candles(b, x1 - 1, T + 2, cz, 2, color="green")
	deco.skull(b, x1 - 2, T + 2, cz, "skeleton_skull")


def jaguar_throne(b, m, x, y, z, facing):
	"""
	The red jaguar throne (Chichen Itza): a crouching jaguar painted red with jade spots, its head toward `facing`,
	the seat on its back. 3 wide x 3 long, centred on (x, z), z = its back end.
	"""
	dx, dz = DIRS[facing]
	sx, sz = DIRS[CW[facing]]
	spots = "minecraft:oxidized_chiseled_copper"
	for i in range(3):  # back to front
		for j in (-1, 0, 1):
			cx, cz = x + dx * i + sx * j, z + dz * i + sz * j
			if i < 2:
				b.set(cx, y, cz, spots if (i + j) % 2 == 0 and j else "minecraft:red_terracotta")
			else:
				# the forepaws
				b.set(cx, y, cz, B.fmt("minecraft:granite_stairs", {"facing": OPP[facing], "half": "bottom",
					"shape": "straight", "waterlogged": "false"}) if j else "minecraft:red_terracotta")
	# the seat (a slab on the back) and the head with its jade eyes
	b.set(x + dx * 0, y + 1, z + dz * 0, "minecraft:granite_slab[type=bottom,waterlogged=false]")
	b.set(x + sx, y + 1, z + sz, "minecraft:red_terracotta")
	b.set(x - sx, y + 1, z - sz, "minecraft:red_terracotta")
	hx, hz = x + dx * 2, z + dz * 2
	b.set(x + dx, y + 1, z + dz, "minecraft:red_terracotta")
	b.set(hx, y + 1, hz, "minecraft:red_terracotta")
	b.set(hx + sx, y + 1, hz + sz, "minecraft:oxidized_cut_copper_slab[type=bottom,waterlogged=false]")
	b.set(hx - sx, y + 1, hz - sz, "minecraft:oxidized_cut_copper_slab[type=bottom,waterlogged=false]")
	b.set(hx, y + 2, hz, B.fmt("minecraft:granite_stairs", {"facing": OPP[facing], "half": "bottom", "shape": "straight",
		"waterlogged": "false"}))


# -------------------------------------------------------------------------------------- roof comb, beacon

def roof_comb(b, m):
	"""
	The huge stepped roof comb over the cella: a hollow perforated body in two stages (15 then 13 wide, 11 deep)
	carrying the mask of the jaguar sun on its front, a solid red-banded stage, then the crown, four steps (11, 9, 7,
	5) hiding the iron base of the beacon, and the beacon itself in its jade setting under a jewel of golden glass,
	open to the sky.
	"""
	tuff, ptuff, plaster = m["tuff"], m["ptuff"], m["plaster"]
	bx, by, bz = BEACON
	base = COMB_BASE
	za, zb = bz - 5, bz + 5  # 11 deep
	# hollow body: lower stage 15 wide (base .. base + 5), upper stage 13 wide (base + 6 .. base + 11)
	for y in range(base, base + 12):
		w = 7 if y < base + 6 else 6
		for x in range(PX - w, PX + w + 1):
			for z in range(za, zb + 1):
				edge = abs(x - PX) == w or z in (za, zb)
				if edge or y in (base, base + 11):
					s = tuff.full(x, y, z) if (y - base) % 6 in (0, 5) else plaster.full(x, y, z)
					if y in (base + 4, base + 10) and edge:
						s = m["redpaint"].full(x, y, z)
					b.set(x, y, z, s)
				else:
					b.set(x, y, z, AIR)
	# mouldings: upside-down stairs under the step of the upper stage, bottom stairs on the ledge
	for x in range(PX - 7, PX + 8):
		for z in range(za - 1, zb + 2):
			r_x = abs(x - PX)
			on_rim = (r_x == 7 and za <= z <= zb) or (z in (za - 1, zb + 1) and r_x <= 7)
			if not on_rim:
				continue
			if z == zb + 1:
				continue  # the mask side
			f = "west" if x > PX and r_x == 7 else "east" if x < PX and r_x == 7 else ("south" if z < za else "north")
			if r_x == 7 and za <= z <= zb:
				b.set(x, base + 6, z, tuff.stairs(x, base + 6, z, OPP[f], "bottom"))
			elif z == za - 1:
				b.set(x, base + 5, z, tuff.stairs(x, base + 5, z, OPP[f], "top"))
				b.set(x, base + 11, z, tuff.stairs(x, base + 11, z, OPP[f], "top") if r_x <= 6 else AIR)
	# the perforations (Tikal-style lattice) on the sides and the back
	for y in (base + 2, base + 3, base + 7, base + 8):
		w = 7 if y < base + 6 else 6
		for z in range(za + 1, zb):
			if (z - bz) % 2 == 0:
				for x in (PX - w, PX + w):
					b.set(x, y, z, AIR)
		for x in range(PX - w + 1, PX + w):
			if (x - PX) % 2 == 1:
				b.set(x, y, za, AIR)
	# the solid stage, red band
	for y in range(base + 12, base + 16):
		for x in range(PX - 5, PX + 6):
			for z in range(za, zb + 1):
				edge = abs(x - PX) == 5 or z in (za, zb)
				s = plaster.full(x, y, z)
				if y == base + 13 and edge:
					s = "minecraft:red_terracotta"
				elif y == base + 14 and edge:
					s = "minecraft:chiseled_tuff_bricks" if (x + z) % 2 else "minecraft:oxidized_cut_copper"
				b.set(x, y, z, s)
	for x in range(PX - 6, PX + 7):
		for z in range(za - 1, zb + 2):
			if abs(x - PX) <= 5 and za <= z <= zb:
				continue
			if abs(x - PX) == 6 and z in (za - 1, zb + 1):
				continue
			f = "east" if x < PX - 5 else "west" if x > PX + 5 else ("south" if z < za else "north")
			b.set(x, base + 12, z, tuff.stairs(x, base + 12, z, f, "bottom"))
	# the crown: steps of 11, 9, 7, 5 around the iron base of the beacon (hidden in the stone)
	for i, half in enumerate((5, 4, 3, 2)):
		y = by - 4 + i
		iron = 4 - i  # half width of the iron level under the beacon (9 x 9, 7 x 7, 5 x 5, 3 x 3)
		for x in range(bx - half, bx + half + 1):
			for z in range(bz - half, bz + half + 1):
				r = max(abs(x - bx), abs(z - bz))
				if r <= iron:
					b.set(x, y, z, "minecraft:iron_block")
				elif i < 3 and (abs(x - bx) == half) != (abs(z - bz) == half):
					f = "west" if x - bx == half else "east" if bx - x == half else ("north" if z > bz else "south")
					b.set(x, y, z, ptuff.stairs(x, y, z, OPP[f], "bottom") if i == 2 else
						("minecraft:chiseled_tuff_bricks" if (x + z) % 2 else "minecraft:polished_tuff"))
				else:
					b.set(x, y, z, "minecraft:chiseled_tuff_bricks" if (x + z) % 2 else "minecraft:polished_tuff")
	# the beacon in its jade setting, the golden jewel above it
	b.t.set(bx, by, bz, "minecraft:beacon", N.Compound(id=N.String("minecraft:beacon")))
	for d, (dx, dz) in DIRS.items():
		b.set(bx + dx, by, bz + dz, B.fmt("minecraft:oxidized_cut_copper_stairs", {"facing": d, "half": "bottom",
			"shape": "straight", "waterlogged": "false"}))
	for dx in (-1, 1):
		for dz in (-1, 1):
			b.set(bx + dx, by, bz + dz, "minecraft:oxidized_chiseled_copper")
			b.set(bx + dx, by + 1, bz + dz, "minecraft:oxidized_cut_copper_slab[type=bottom,waterlogged=false]")
	b.set(bx, by + 1, bz, "minecraft:yellow_stained_glass")
	# the mask of the jaguar sun on the front of the comb (square eyes of ochre light, red mouth, white fangs)
	zf = zb
	rows = [
		".JJJJJJJJJJJ.",
		"JRCRCRCRCRCRJ",
		"S.SSSSSSSSS.S",
		"SSEEESNSEEESS",
		"SSEEESNSEEESS",
		"SSSSSNNNSSSSS",
		"RSSSMMMMMSSSR",
		"RS.FMTTTMF.SR",
		".S.SMMMMMS.S.",
		"..SSSSSSSSS..",
		".....JJJ.....",
	]
	for i, row in enumerate(rows):
		y = base + 11 - i
		for j, ch in enumerate(row):
			x = PX - 6 + j
			s = {
				"J": "minecraft:oxidized_chiseled_copper",
				"C": "minecraft:chiseled_tuff_bricks",
				"R": "minecraft:red_terracotta",
				"S": tuff.full(x, y, zf),
				"E": "minecraft:ochre_froglight[axis=y]",
				"N": "minecraft:chiseled_tuff",
				"M": "minecraft:red_terracotta",
				"T": "minecraft:calcite",
			}.get(ch)
			if ch == "F":
				b.set(x, y, zf + 1, tuff.stairs(x, y, zf + 1, "north", "top"))
				b.set(x, y, zf, "minecraft:red_terracotta")
			elif s is not None:
				b.set(x, y, zf, s)
	# ear flares and the brow projecting from the face
	for x, f in ((PX - 7, "east"), (PX + 7, "west")):
		b.set(x, base + 8, zf, "minecraft:oxidized_chiseled_copper")
		b.set(x, base + 8, zf + 1, B.fmt("minecraft:oxidized_cut_copper_stairs", {"facing": "north", "half": "top",
			"shape": "straight", "waterlogged": "false"}))
	for x in range(PX - 5, PX + 6):
		b.set(x, base + 9, zf + 1, tuff.slab(x, base + 9, zf + 1, "top") if abs(x - PX) != 0 else
			"minecraft:oxidized_cut_copper_slab[type=top,waterlogged=false]")
	b.protect(PX - 8, base, za - 1, PX + 8, by + 1, zb + 1)
	b.keep_clear.update({(bx, y, bz) for y in range(by + 1, SY)})


# ---------------------------------------------------------------------------- hall of the Celestial Jaguar

def vault_half(y):
	"""Interior half width of the hall at layer y (corbel vault above the walls), None above the vault."""
	if y <= HALL_WALL:
		return HALL
	k = y - HALL_WALL
	return HALL - k if HALL - k >= 8 else None


PILLARS = [(PX + dx, PZ + dz) for dx in (-11, -4, 4, 11) for dz in (-8, 8)]


def hall(b, m):
	"""
	The arena, under the heart of the pyramid: 29 x 29, walls 9 high and a corbel vault (15 high at the centre), eight
	jaguar-warrior pillars carrying a frame of beams, walls of glowing glyph panels between pilasters, the dais with the
	jaguar throne, the jade mask of the sun and the treasure at the north end. Doors west (the way of the king), south
	(the hidden way) and east (the tunnel of the cenote); four light shafts fall from the high terraces.
	"""
	ptuff, red, deep = m["ptuff"], m["red"], m["deep"]
	for x in range(PX - HALL - 2, PX + HALL + 3):
		for z in range(PZ - HALL - 2, PZ + HALL + 3):
			d = ring(x, z)
			for y in range(FEET - 4, CAP + 1):
				hv = vault_half(y)
				if y >= FEET and hv is not None and d <= hv:
					b.set(x, y, z, AIR)
				elif y == FEET - 1 and d <= HALL:
					b.set(x, y, z, m["hfloor"].full(x, y, z))
				elif hv is not None and d == hv + 1 and y > HALL_WALL:
					b.set(x, y, z, ptuff.stairs(x, y, z, outward(x, z), "top"))
				elif y == CAP and d <= 8:
					b.set(x, y, z, "minecraft:polished_tuff" if (x + z) % 2 else "minecraft:chiseled_tuff_bricks")
				else:
					b.set(x, y, z, deep.full(x, y, z))
	# walls: plinth, glyph panels lit from within between pilasters, a carved and painted frieze, niches
	for x in range(PX - HALL, PX + HALL + 1):
		for z in range(PZ - HALL, PZ + HALL + 1):
			if ring(x, z) != HALL:
				continue
			out = outward(x, z)
			dx, dz = DIRS[out]
			wx, wz = x + dx, z + dz
			a = along(x, z)
			if abs(a) == HALL:
				continue
			b.set(x, FEET, z, ptuff.stairs(x, FEET, z, out, "bottom"))
			b.set(wx, FEET + 7, wz, "minecraft:chiseled_tuff_bricks" if a % 2 == 0 else red.full(wx, FEET + 7, wz))
			b.set(wx, FEET + 8, wz, "minecraft:red_terracotta" if a % 4 == 1 else "minecraft:polished_tuff")
			if a % 5 == 0:
				# pilaster with a copper torch
				for y in range(FEET, HALL_WALL + 1):
					b.set(x, y, z, ptuff.full(x, y, z) if y not in (FEET + 3, FEET + 7) else "minecraft:chiseled_tuff_bricks")
				ix, iz = x - dx, z - dz
				b.set(ix, FEET, iz, ptuff.stairs(ix, FEET, iz, out, "bottom"))
				b.set(ix, FEET + 5, iz, f"minecraft:copper_wall_torch[facing={OPP[out]}]")
			elif a % 5 in (2, 3) and abs(a) < HALL - 1:
				# glyph panel: a glowing jade glyph in a frame of polished tuff
				g = (a + (3 if out in ("east", "west") else 0)) % 4
				for y in range(FEET + 2, FEET + 6):
					rel = y - FEET - 2
					glow = (rel in (1, 2) and (g + rel + (a % 5)) % 3 == 0)
					b.set(wx, y, wz, "minecraft:verdant_froglight[axis=y]" if glow else
						("minecraft:chiseled_tuff" if (rel + a) % 3 == 0 else "minecraft:polished_tuff"))
			elif a % 5 == 4 and out in ("west", "east") and abs(a) < HALL - 2:
				b.set(wx, FEET + 1, wz, AIR)
				b.set(wx, FEET + 2, wz, ptuff.stairs(wx, FEET + 2, wz, out, "top"))
				if b.hrand(x, 0, z, 23) < 0.5:
					deco.skull(b, wx, FEET + 1, wz, "skeleton_skull", rotation={"west": 4, "east": 12}[out])
				else:
					deco.candles(b, wx, FEET + 1, wz, 3, color="red")
	# floor: polished tuff with a cross of jade toward the doors and the sun disc around the guardian
	for x in range(PX - HALL, PX + HALL + 1):
		for z in range(PZ - HALL, PZ + HALL + 1):
			dx, dz = x - PX, z - PZ
			r = math.hypot(dx, dz)
			if (dx == 0 or dz == 0) and r > 4:
				b.set(x, FEET - 1, z, "minecraft:tuff_bricks" if (dx + dz) % 2 else "minecraft:polished_tuff")
			if 3.5 <= r < 4.5:
				b.set(x, FEET - 1, z, "minecraft:oxidized_cut_copper" if (x + z) % 2 else "minecraft:red_terracotta")
			elif r < 1.5:
				b.set(x, FEET - 1, z, "minecraft:oxidized_chiseled_copper" if r < 0.5 else "minecraft:ochre_froglight[axis=y]")
			elif 5.5 <= r < 6.3 and abs(dx) == abs(dz):
				b.set(x, FEET - 1, z, "minecraft:verdant_froglight[axis=y]")
	# the eight jaguar warriors
	for (px, pz) in PILLARS:
		jaguar_pillar(b, m, px, pz)
	# beams of jungle wood between the pillar capitals and the walls, lanterns
	for z in (PZ - 8, PZ + 8):
		for x in range(PX - HALL, PX + HALL + 1):
			if b.is_air(x, HALL_WALL - 1, z):
				b.set(x, HALL_WALL - 1, z, "minecraft:stripped_jungle_log[axis=x]")
	for x in (PX - 4, PX + 4):
		for z in range(PZ - 7, PZ + 8):
			if b.is_air(x, HALL_WALL - 1, z):
				b.set(x, HALL_WALL - 1, z, "minecraft:stripped_jungle_log[axis=z]")
	for (x, z) in ((PX - 8, PZ - 8), (PX + 8, PZ - 8), (PX - 8, PZ + 8), (PX + 8, PZ + 8), (PX - 4, PZ), (PX + 4, PZ)):
		hang(b, x, HALL_WALL - 3, z, 1)
	for (x, z) in ((PX, PZ - 4), (PX, PZ + 4), (PX - 6, PZ), (PX + 6, PZ)):
		for y in range(FEET + 9, CAP):
			if b.is_air(x, y, z):
				b.set(x, y, z, "minecraft:oxidized_copper_chain[axis=y,waterlogged=false]")
		b.set(x, FEET + 8, z, "minecraft:oxidized_copper_lantern[hanging=true,waterlogged=false]")
	dais(b, m)
	# doors: west (way of the king), south (hidden way), east (tunnel of the cenote)
	for (x0, z0, x1, z1) in ((PX - HALL - 2, PZ - 1, PX - HALL - 1, PZ + 1), (PX - 1, PZ + HALL + 1, PX + 1, PZ + HALL + 2),
		(PX + HALL + 1, PZ - 1, PX + HALL + 2, PZ + 1)):
		for x in range(x0, x1 + 1):
			for z in range(z0, z1 + 1):
				for y in range(FEET, FEET + 4):
					b.set(x, y, z, AIR)
				b.set(x, FEET - 1, z, "minecraft:polished_tuff")
				b.set(x, FEET + 4, z, "minecraft:stripped_jungle_log[axis=x]" if z0 == z1 - 1 else
					"minecraft:stripped_jungle_log[axis=z]")
	# the light shafts from the terraces of the ninth level, closed by copper grates
	for sx in (-1, 1):
		for sz in (-1, 1):
			x, z = PX + 13 * sx, PZ + 13 * sz
			top = G + TH * 9 - 1 + 1  # walkway of terrace 8 (its top layer)
			for y in range(HALL_WALL + 1, top):
				b.set(x, y, z, AIR)
			b.set(x, top - 1 + 1, z, "minecraft:oxidized_copper_grate[waterlogged=false]")
			b.protect(x, HALL_WALL, z, x, top, z)
	# markers: the guardian on the sun disc, its escort between the pillars, two guards before the dais
	deco.marker(b, PX, FEET, PZ, deco.BOSS)
	for (x, z) in ((PX - 7, PZ - 4), (PX + 7, PZ - 4), (PX - 7, PZ + 4), (PX + 7, PZ + 4), (PX - 2, PZ + 11),
		(PX + 2, PZ + 11)):
		deco.marker(b, x, FEET, z, deco.MINION)
	deco.guard(b, PX - 9, FEET, PZ - 10)
	deco.guard(b, PX + 9, FEET, PZ - 10)
	b.clear_zone(PX - HALL, FEET, PZ - HALL + 5, PX + HALL, FEET + 3, PZ + HALL)
	b.protect(PX - HALL - 2, FEET - 4, PZ - HALL - 2, PX + HALL + 2, CAP + 1, PZ + HALL + 2)


def jaguar_pillar(b, m, px, pz):
	"""
	A 3 x 3 pillar carved as a jaguar warrior on its four faces: base, red legs with jade anklets, the belt, the
	jaguar pelt (ochre with black spots), the jade collar, the face, the jaguar helmet with its jaws, a plume of jade;
	above, a plain shaft up to the vault and a capital.
	"""
	tuff, ptuff = m["tuff"], m["ptuff"]
	top = FEET
	while top < CAP and all(b.is_air(px + dx, top, pz + dz) or b.get(px + dx, top, pz + dz) is None
		or (px + dx, top, pz + dz) in ((px, top, pz),) for dx in (-1, 0, 1) for dz in (-1, 0, 1)) and vault_half(top) is not None \
		and ring(px, pz) + 1 <= vault_half(top):
		top += 1
	for y in range(FEET, top):
		lvl = y - FEET
		for dx in (-1, 0, 1):
			for dz in (-1, 0, 1):
				x, z = px + dx, pz + dz
				corner = dx and dz
				centre = not dx and not dz
				face = not corner and not centre
				s = tuff.full(x, y, z)
				if lvl == 0:
					s = "minecraft:polished_tuff"
				elif lvl in (1, 2):
					s = ("minecraft:oxidized_cut_copper" if lvl == 1 and corner else "minecraft:red_terracotta") if not centre \
						else "minecraft:tuff_bricks"
				elif lvl == 3:
					s = "minecraft:oxidized_chiseled_copper" if face else "minecraft:chiseled_tuff_bricks"
				elif lvl in (4, 5):
					s = "minecraft:black_terracotta" if ((x * 7 + z * 3 + y) % 4 == 0) else "minecraft:yellow_terracotta"
				elif lvl == 6:
					s = "minecraft:oxidized_cut_copper"
				elif lvl == 7:
					s = "minecraft:chiseled_tuff" if face else "minecraft:polished_tuff"
				elif lvl == 8:
					s = "minecraft:red_terracotta" if face else ("minecraft:black_terracotta" if corner else "minecraft:yellow_terracotta")
				elif lvl == 9:
					s = "minecraft:yellow_terracotta" if not corner else "minecraft:black_terracotta"
				elif y == top - 1:
					s = "minecraft:chiseled_tuff_bricks"
				else:
					s = tuff.full(x, y, z) if not face or (y + x + z) % 5 else "minecraft:chiseled_tuff_bricks"
				b.set(x, y, z, s)
	# plinth, the jade plume above the helmet, the capital
	for d, (dx, dz) in DIRS.items():
		for w in (-1, 0, 1):
			x = px + dx * 2 + (w if dz else 0)
			z = pz + dz * 2 + (w if dx else 0)
			if b.is_air(x, FEET, z):
				b.set(x, FEET, z, ptuff.stairs(x, FEET, z, OPP[d], "bottom"))
			if w == 0:
				if b.is_air(x, FEET + 10, z):
					b.set(x, FEET + 10, z, B.fmt("minecraft:oxidized_cut_copper_stairs", {"facing": OPP[d], "half": "bottom",
						"shape": "straight", "waterlogged": "false"}))
				if top - 1 > FEET + 11 and b.is_air(x, top - 1, z):
					b.set(x, top - 1, z, ptuff.stairs(x, top - 1, z, OPP[d], "top"))
	b.set(px, FEET + 8, pz, "minecraft:ochre_froglight[axis=y]")


def dais(b, m):
	"""North end: a two-step dais, the jaguar throne of the guardian, the altar, the jade mask of the sun, the treasure."""
	ptuff = m["ptuff"]
	z0 = PZ - HALL
	for x in range(PX - 8, PX + 9):
		for z in range(z0, z0 + 5):
			lvl = 2 if z <= z0 + 2 and abs(x - PX) <= 6 else 1
			if z == z0 + 4 or abs(x - PX) == 8:
				b.set(x, FEET, z, ptuff.stairs(x, FEET, z, "south" if z == z0 + 4 else ("west" if x < PX else "east"),
					"bottom"))
				continue
			b.set(x, FEET, z, "minecraft:polished_tuff" if (x + z) % 2 else "minecraft:tuff_bricks")
			if lvl == 2:
				if z == z0 + 2 or abs(x - PX) == 6:
					b.set(x, FEET + 1, z, ptuff.stairs(x, FEET + 1, z, "south" if z == z0 + 2 else ("west" if x < PX else "east"),
						"bottom"))
				else:
					b.set(x, FEET + 1, z, "minecraft:polished_tuff")
	jaguar_throne(b, m, PX, FEET + 2, z0, "south")
	deco.chest(b, PX - 3, FEET + 2, z0, "south", "treasure")
	deco.candles(b, PX - 4, FEET + 2, z0, 4, color="red")
	deco.candles(b, PX + 3, FEET + 2, z0, 3, color="green")
	pot(b, PX + 4, FEET + 2, z0)
	pot(b, PX - 5, FEET + 2, z0 + 1, cracked=True)
	# the altar in front of the dais: a stone table with an offering bowl
	for x in (PX - 1, PX, PX + 1):
		b.set(x, FEET, z0 + 6, ptuff.stairs(x, FEET, z0 + 6, "north", "top") if x != PX else "minecraft:chiseled_tuff_bricks")
	pot(b, PX, FEET + 1, z0 + 6)
	# the jade mask of the sun on the north wall (glowing ochre eyes)
	wz = z0 - 1
	rows = [
		"..JJJJJ..",
		".JCRCRCJ.",
		"J.SSSSS.J",
		"RSEESEESR",
		"RS.SNS.SR",
		".SMMMMMS.",
		"..F.M.F..",
	]
	for i, row in enumerate(rows):
		y = FEET + 10 - i
		for j, ch in enumerate(row):
			x = PX - 4 + j
			s = {
				"J": "minecraft:oxidized_chiseled_copper",
				"C": "minecraft:chiseled_tuff_bricks",
				"R": "minecraft:oxidized_cut_copper",
				"S": "minecraft:polished_tuff",
				"E": "minecraft:ochre_froglight[axis=y]",
				"N": "minecraft:chiseled_tuff",
				"M": "minecraft:red_terracotta",
			}.get(ch)
			if ch == "F":
				b.set(x, y, wz + 1, ptuff.stairs(x, y, wz + 1, "north", "top"))
			elif s is not None:
				b.set(x, y, wz, s)


# ------------------------------------------------------------------------------------------ way of the king

def spiral_path(cx, cz, feet_top, feet_bottom, start="south"):
	"""
	Cells of a square spiral stair (2 wide) around a 3 x 3 pillar centred on (cx, cz), going down clockwise from the
	middle of the `start` side: [(cells, kind, feet)] per position, kind 'step' (down one) or 'land' (corner).
	"""
	order = ["south", "west", "north", "east"]  # clockwise seen from above: south side walked toward the west
	i = order.index(start)
	feet = feet_top
	out = []
	first = True
	while True:
		side = order[i % 4]
		# the three positions along the side (2 deep: distances 2 and 3 from the axis), then the corner landing
		for t in (-1, 0, 1) if not first else (0, 1):
			if feet <= feet_bottom:
				return out
			cells = _side_cells(cx, cz, side, t)
			out.append((cells, "step", feet, _walk_dir(side)))
			feet -= 1
		first = False
		out.append((_corner_cells(cx, cz, side), "land", feet, _walk_dir(side)))
		i += 1


def _walk_dir(side):
	return {"south": "west", "west": "north", "north": "east", "east": "south"}[side]


def _side_cells(cx, cz, side, t):
	"""The two cells (distances 2 and 3 from the axis) of position t (-1, 0, 1 along the walking direction)."""
	w = _walk_dir(side)
	wx, wz = DIRS[w]
	ox, oz = DIRS[side]
	return [(cx + ox * r + wx * t, cz + oz * r + wz * t) for r in (2, 3)]


def _corner_cells(cx, cz, side):
	w = _walk_dir(side)
	wx, wz = DIRS[w]
	ox, oz = DIRS[side]
	return [(cx + ox * r + wx * q, cz + oz * r + wz * q) for r in (2, 3) for q in (2, 3)]


def well_stair(b, m):
	"""
	The well of the world tree: a square spiral stair around a carved pillar, from the cella floor down to the level of
	the tomb, lit by glowing glyphs set in the pillar. The pillar rises through the cella floor as an altar.
	"""
	wx, wz = WELL
	steps, deep = m["steps"], m["deep"]
	feet_top = TOPY + 1
	path = spiral_path(wx, wz, feet_top, TOMB_FEET, start="south")
	# the well: stone everywhere (the core), carved along the path
	for x in range(wx - 4, wx + 5):
		for z in range(wz - 4, wz + 5):
			for y in range(TOMB_FEET - 2, TOPY):
				if ring(x, z, wx, wz) <= 4 and not (ring(x, z, wx, wz) <= 3 and y >= TOPY - 1):
					b.set(x, y, z, deep.full(x, y, z))
	for cells, kind, feet, go in path:
		for (x, z) in cells:
			if kind == "step":
				b.set(x, feet - 1, z, steps.stairs(x, feet - 1, z, OPP[go], "bottom"))
			else:
				b.set(x, feet - 1, z, "minecraft:polished_tuff" if (x + z) % 2 else "minecraft:tuff_bricks")
			b.set(x, feet - 2, z, deep.full(x, feet - 2, z))
			for h in range(0, 4):
				b.set(x, feet + h, z, AIR)
			b.clear_zone(x, feet, z, x, feet + 1, z)
	# the bottom: a landing ring at the level of the tomb (around the last steps), the passage west to it
	lowest = {}
	for cells, kind, feet, go in path:
		for c in cells:
			lowest[c] = min(lowest.get(c, feet), feet)
	for x in range(wx - 3, wx + 4):
		for z in range(wz - 3, wz + 4):
			if ring(x, z, wx, wz) >= 2 and lowest.get((x, z), TOMB_FEET) > TOMB_FEET + 4:
				b.set(x, TOMB_FEET - 1, z, "minecraft:polished_tuff" if (x + z) % 2 else "minecraft:tuff_bricks")
				for y in range(TOMB_FEET, TOMB_FEET + 4):
					b.set(x, y, z, AIR)
				b.clear_zone(x, TOMB_FEET, z, x, TOMB_FEET + 1, z)
	# the pillar: glyph stones and glowing jade glyphs, the world tree altar in the cella
	for y in range(TOMB_FEET - 1, TOPY + 3):
		for dx in (-1, 0, 1):
			for dz in (-1, 0, 1):
				x, z = wx + dx, wz + dz
				corner = dx and dz
				if y >= TOPY + 1:
					if y == TOPY + 1:
						s = "minecraft:chiseled_tuff_bricks" if corner else "minecraft:oxidized_cut_copper"
					elif y == TOPY + 2:
						s = None if corner else ("minecraft:oxidized_chiseled_copper" if not dx and not dz else
							B.fmt("minecraft:oxidized_cut_copper_stairs", {"facing": "south" if dz < 0 else "north" if dz > 0 else
							"east" if dx < 0 else "west", "half": "bottom", "shape": "straight", "waterlogged": "false"}))
					if s:
						b.set(x, y, z, s)
					continue
				glow = not corner and (dx or dz) and (y - TOMB_FEET) % 6 == 2
				b.set(x, y, z, "minecraft:verdant_froglight[axis=y]" if glow else (
					"minecraft:chiseled_tuff" if (y % 4 == 0 and not corner) else "minecraft:polished_tuff"))
	# the opening of the well in the cella floor: a jade kerb around the stairs that come up to it
	for x in range(wx - 4, wx + 5):
		for z in range(wz - 4, wz + 5):
			r = ring(x, z, wx, wz)
			if r == 4 and b.is_air(x, TOPY + 1, z) and b.get(x, TOPY, z) is not None and not b.is_air(x, TOPY, z):
				b.set(x, TOPY + 1, z, "minecraft:oxidized_cut_copper_slab[type=bottom,waterlogged=false]")
	b.protect(wx - 4, TOMB_FEET - 2, wz - 4, wx + 4, TOPY + 2, wz + 4)


def tomb(b, m):
	"""
	The tomb of the king: a long corbel-vaulted chamber, the monolithic sarcophagus with its carved lid (the world
	tree in jade on chiseled stone), the nine lords of the night carved on the walls, offerings, the psychoduct (a
	copper duct from the sarcophagus up the wall toward the temple above).
	"""
	x0, z0, x1, z1 = TOMB
	ptuff = m["ptuff"]
	cap = corbel(b, m, x0, z0, x1, z1, TOMB_FEET, 5, "x")
	cz = (z0 + z1) // 2
	# the passage from the bottom of the well
	wx, wz = WELL
	for x in range(x1 + 1, wx - 1):
		for z in (wz - 1, wz, wz + 1):
			b.set(x, TOMB_FEET - 1, z, "minecraft:polished_tuff")
			for y in range(TOMB_FEET, TOMB_FEET + 3):
				b.set(x, y, z, AIR)
			b.set(x, TOMB_FEET + 3, z, ptuff.full(x, TOMB_FEET + 3, z))
	b.clear_zone(x1 + 1, TOMB_FEET, wz - 1, wx - 2, TOMB_FEET + 1, wz + 1)
	# the nine lords of the night: figures in relief between the doors (stucco white, red, jade)
	lords = [(x, z0 - 1, "south") for x in range(x0 + 1, x1, 3)] + [(x, z1 + 1, "north") for x in range(x0 + 1, x1, 3)]
	for i, (x, z, f) in enumerate(lords[:9]):
		for y, s in ((TOMB_FEET, "minecraft:polished_tuff"), (TOMB_FEET + 1, "minecraft:red_terracotta"),
			(TOMB_FEET + 2, "minecraft:calcite"), (TOMB_FEET + 3, "minecraft:chiseled_tuff"),
			(TOMB_FEET + 4, "minecraft:oxidized_chiseled_copper")):
			b.set(x, y, z, s)
		dz = DIRS[f][1]
		b.set(x, TOMB_FEET + 4, z + dz, B.fmt("minecraft:oxidized_cut_copper_stairs", {"facing": OPP[f], "half": "top",
			"shape": "straight", "waterlogged": "false"}))
	# the sarcophagus: a monolith, its carved lid
	sx0, sx1 = (x0 + x1) // 2 - 2, (x0 + x1) // 2 + 2
	for x in range(sx0 - 1, sx1 + 2):
		for z in range(cz - 2, cz + 3):
			b.set(x, TOMB_FEET, z, ptuff.full(x, TOMB_FEET, z) if (x in (sx0 - 1, sx1 + 1) or abs(z - cz) == 2) else
				"minecraft:polished_tuff")
	for x in range(sx0, sx1 + 1):
		for z in range(cz - 1, cz + 2):
			b.set(x, TOMB_FEET + 1, z, "minecraft:polished_deepslate" if abs(z - cz) == 1 else "minecraft:red_terracotta")
			lid = "minecraft:oxidized_chiseled_copper" if (z == cz and x % 2 == 0) or (x == (sx0 + sx1) // 2) else \
				"minecraft:chiseled_tuff_bricks"
			b.set(x, TOMB_FEET + 2, z, lid)
	for x in (sx0 - 1, sx1 + 1):
		for z in range(cz - 1, cz + 2):
			b.set(x, TOMB_FEET + 1, z, ptuff.stairs(x, TOMB_FEET + 1, z, "east" if x < sx0 else "west", "bottom"))
	deco.skull(b, sx0 - 1, TOMB_FEET + 2, cz, "skeleton_skull", rotation=4)
	# the psychoduct: a copper duct from the head of the sarcophagus to the wall, then up toward the temple
	for x in range(x0, sx0 - 1):
		b.set(x, TOMB_FEET - 1, cz, "minecraft:oxidized_copper_grate[waterlogged=false]")
	for y in range(TOMB_FEET, cap):
		b.set(x0 - 1, y, cz, "minecraft:oxidized_copper_grate[waterlogged=false]")
	# offerings
	deco.chest(b, x1, TOMB_FEET, z0, "west", "rare")
	pot(b, x1, TOMB_FEET, z1, cracked=True, facing="west")
	pot(b, x0, TOMB_FEET, z0)
	deco.candles(b, x0, TOMB_FEET, z1, 4, color="red")
	deco.candles(b, sx1 + 1, TOMB_FEET + 2, cz - 1, 3, color="green")
	deco.candles(b, sx1 + 1, TOMB_FEET + 2, cz + 1, 2, color="green")
	for x in range(x0 + 2, x1 - 1, 4):
		hang(b, x, cap - 2, cz, 1)
	deco.guard(b, x1 - 1, TOMB_FEET, cz)
	b.clear_zone(x0, TOMB_FEET, cz - 4, x1, TOMB_FEET + 1, cz - 3)
	b.protect(x0 - 2, TOMB_FEET - 2, z0 - 2, x1 + 2, cap + 1, z1 + 2)


def descent(b, m):
	"""The descent of the soul: two flights from the tomb down to the antechamber of the west door of the hall."""
	x0, z0, x1, z1 = TOMB
	door(b, x0 - 1, z0, x0 - 1, z0 + 2, TOMB_FEET)
	x, z, feet = flight(b, m, x0 - 2, z0 + 2, "west", 9, TOMB_FEET, 3, light_every=4)
	# landing
	lx0 = x - 3
	room(b, m, lx0, z0, x, PZ + 1, feet, 4)
	hang(b, lx0 + 1, feet + 2, (z0 + PZ) // 2, 1)
	x2, z2, feet2 = flight(b, m, x + 1, PZ - 1, "east", feet - FEET, feet, 3, light_every=4)
	# antechamber of the west door
	ax0, ax1 = x2, PX - HALL - 3
	corbel(b, m, ax0, PZ - 2, ax1, PZ + 2, FEET, 4, "x")
	for xx in range(ax0, ax1 + 1, 2):
		for zz, f in ((PZ - 3, "south"), (PZ + 3, "north")):
			b.set(xx, FEET + 2, zz, "minecraft:verdant_froglight[axis=y]" if xx % 4 == 0 else "minecraft:chiseled_tuff_bricks")
	deco.guard(b, ax0 + 1, FEET, PZ + 1)
	b.clear_zone(ax0, FEET, PZ - 1, PX - HALL - 1, FEET + 1, PZ + 1)
	b.protect(lx0 - 1, FEET - 2, z0 - 2, PX - HALL - 1, TOMB_FEET + 5, PZ + 4)


# ----------------------------------------------------------------------------------------------- hidden way

def gullet(b, m):
	"""The gullet of the west serpent head: stairs going down from its open jaws into the earth, then north."""
	x0 = HEADS[0]
	steps = m["steps"]
	zt = STAIR_FOOT + 8  # inside the mouth, behind the fangs
	xs = range(x0 + 2, x0 + 5)
	feet = G + 2
	for s in range(G + 2 - L1):
		z = zt - s
		y = feet - 1 - s
		for x in xs:
			b.set(x, y, z, "minecraft:granite_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"
				if y >= G else steps.stairs(x, y, z, "south", "bottom"))
			b.set(x, y - 1, z, m["deep"].full(x, y - 1, z))
			for h in range(1, 4):
				if y + h < G + 2:
					b.set(x, y + h, z, AIR)
			if y + 4 < G + 2:
				b.set(x, y + 4, z, m["ptuff"].full(x, y + 4, z))
			b.clear_zone(x, y + 1, z, x, y + 2, z)
		for x in (x0 + 1, x0 + 5):
			for h in range(-1, 5):
				if y + h < G + 2 and not b.is_air(x, y + h, z):
					b.set(x, y + h, z, m["deep"].full(x, y + h, z))
	zend = zt - (G + 2 - L1)
	return zend


def hidden_way(b, m):
	"""
	The galleries under the south of the pyramid: the gullet, the gallery of glyphs, the curtain of glowing vines
	over the short stair, the antechamber of the jaguars before the south door of the hall; the long way by the
	gallery of waters, the hall of offerings and the stair of the nine lords; the corridor of false doors (trapped)
	and the hidden treasury.
	"""
	ptuff, deep = m["ptuff"], m["deep"]
	x0 = HEADS[0]
	zend = gullet(b, m)
	gx0, gx1 = x0 + 2, x0 + 4  # the gullet columns
	# --- gallery of glyphs (antechamber), feet L1
	ax0, az0, ax1, az1 = PX - 14, PZ + HALL + 19, PX, PZ + HALL + 28
	# corridor from the gullet to it
	corbel(b, m, gx0, az1 + 1, gx1, zend, L1, 3, "z")
	for z in range(az1 + 2, zend + 1, 4):
		for x in (gx0 - 1, gx1 + 1):
			b.set(x, L1 + 1, z, "minecraft:verdant_froglight[axis=y]")
	corbel(b, m, ax0, az0, ax1, az1, L1, 4, "x")
	door(b, gx0, az1 + 1, gx1, az1 + 1, L1)
	for x in range(ax0, ax1 + 1):
		for z, f in ((az0 - 1, "south"), (az1 + 1, "north")):
			if b.is_air(x, L1, z):
				continue
			for y in range(L1, L1 + 4):
				rel = y - L1
				glow = (x * 3 + rel) % 5 == 0 and rel in (1, 2)
				b.set(x, y, z, "minecraft:verdant_froglight[axis=y]" if glow else
					("minecraft:chiseled_tuff_bricks" if (x + rel) % 3 == 0 else "minecraft:polished_tuff"))
	for z in range(az0, az1 + 1):
		for x in (ax0 - 1, ax1 + 1):
			for y in range(L1 + 1, L1 + 3):
				if not b.is_air(x, y, z) and (z + y) % 4 == 0:
					b.set(x, y, z, "minecraft:ochre_froglight[axis=y]")
	deco.guard(b, ax0 + 2, L1, az0 + 3)
	deco.guard(b, ax1 - 2, L1, az0 + 3)
	pot(b, ax0, L1, az1)
	pot(b, ax1, L1, az1, cracked=True)
	deco.candles(b, ax0, L1, az0, 3, color="green")
	# --- the short way: north door, curtain of glowing vines, stair down to the antechamber of the jaguars
	jz0, jz1 = PZ + HALL + 3, PZ + HALL + 9  # antechamber of the jaguars
	cx = gx0
	door(b, cx, az0 - 1, cx + 2, az0 - 1, L1)
	for x in range(cx, cx + 3):
		b.set(x, L1 + 2, az0 - 1, "minecraft:cave_vines_plant[berries=true]")
		b.set(x, L1 + 1, az0 - 1, "minecraft:cave_vines_plant[berries=false]")
		b.set(x, L1, az0 - 1, "minecraft:cave_vines[age=25,berries=true]")
		b.set(x, L1 + 3, az0 - 1, ptuff.full(x, L1 + 3, az0 - 1))
	x, z, feet = flight(b, m, cx, az0 - 2, "north", L1 - FEET, L1, 3, light_every=3)
	# --- antechamber of the jaguars, feet FEET, before the south door of the hall
	corbel(b, m, PX - 8, jz0, PX + 8, jz1, FEET, 4, "x")
	for xx in range(cx, cx + 3):
		for zz in range(jz1 + 1, z + 1):
			b.set(xx, FEET - 1, zz, "minecraft:polished_tuff")
			for y in range(FEET, FEET + 3):
				b.set(xx, y, zz, AIR)
	for (xx, f) in ((PX - 6, "south"), (PX + 6, "south"), (PX - 3, "south"), (PX + 3, "south")):
		jaguar_statue(b, m, xx, FEET, jz1, "north")
	deco.guard(b, PX - 2, FEET, jz0 + 3)
	deco.guard(b, PX + 2, FEET, jz0 + 3)
	b.clear_zone(PX - 1, FEET, PZ + HALL + 1, PX + 1, FEET + 1, jz0 + 2)
	# --- the long way: gallery of waters (east), hall of offerings, stair of the nine lords
	wz = az0 + 4
	wx1 = PX + 24
	corbel(b, m, ax1 + 1, wz - 1, wx1, wz + 1, L1, 3, "x")
	door(b, ax1 + 1, wz - 1, ax1 + 1, wz + 1, L1)
	for xx in range(ax1 + 2, wx1):
		b.set(xx, L1 - 1, wz, "minecraft:water")
		b.set(xx, L1 - 2, wz, "minecraft:polished_tuff")
		if xx % 3 == 0:
			b.set(xx, L1 - 1, wz, "minecraft:sea_pickle[pickles=3,waterlogged=true]")
			b.set(xx, L1 - 2, wz, "minecraft:polished_tuff")
		for zz in (wz - 1, wz + 1):
			b.clear_zone(xx, L1, zz, xx, L1 + 1, zz)
	for xx in (PX + 11, PX + 19):
		for y in range(L1 + 4, G + 1):
			b.set(xx, y, wz, AIR)
		b.set(xx, G, wz, "minecraft:oxidized_copper_grate[waterlogged=false]")
	# turn north to the hall of offerings
	ox0, oz0, ox1, oz1 = PX + 21, PZ + HALL + 3, PX + 31, PZ + HALL + 13
	corbel(b, m, wx1 - 2, oz1 + 1, wx1, wz + 1, L1, 3, "z")
	corbel(b, m, ox0, oz0, ox1, oz1, L1, 4, "x")
	door(b, wx1 - 2, oz1, wx1, oz1 + 1, L1)
	offerings(b, m, ox0, oz0, ox1, oz1)
	# the stair of the nine lords: down west to the antechamber of the jaguars
	door(b, ox0 - 1, jz0 + 2, ox0 - 1, jz0 + 4, L1)
	x, z, feet = flight(b, m, ox0 - 2, jz0 + 2, "west", L1 - FEET, L1, 3, light_every=2)
	for xx in range(PX + 9, x + 1):
		for zz in range(jz0 + 2, jz0 + 5):
			b.set(xx, FEET - 1, zz, "minecraft:polished_tuff")
			for y in range(FEET, FEET + 3):
				b.set(xx, y, zz, AIR)
			b.set(xx, FEET + 3, zz, ptuff.full(xx, FEET + 3, zz))
		for zz in (jz0 + 1, jz0 + 5):
			for y in range(FEET - 1, FEET + 4):
				if not b.is_air(xx, y, zz):
					b.set(xx, y, zz, deep.full(xx, y, zz))
	b.clear_zone(PX + 9, FEET, jz0 + 2, x, FEET + 1, jz0 + 4)
	# --- the corridor of false doors (west): pressure plates on the sides fire the arrow dispensers in the walls
	tx0 = PX - 30
	corbel(b, m, tx0, wz - 1, ax0 - 1, wz + 1, L1, 3, "x")
	door(b, ax0 - 1, wz - 1, ax0 - 1, wz + 1, L1)
	for xx in range(tx0, ax0 - 1):
		b.set(xx, L1 - 1, wz, "minecraft:oxidized_cut_copper" if xx % 2 else "minecraft:polished_tuff")
		b.clear_zone(xx, L1, wz, xx, L1 + 1, wz)
		k = xx - tx0
		if k % 4 == 1 and k > 0:
			for zz, f in ((wz - 1, "south"), (wz + 1, "north")):
				b.set(xx, L1, zz, "minecraft:stone_pressure_plate[powered=false]")
				wzz = zz + (-1 if f == "south" else 1)
				dispenser(b, xx, L1, wzz, f)
		elif k % 4 == 3:
			# false doors: niches framed like doors, closed by stone
			for zz, f in ((wz - 2, "south"), (wz + 2, "north")):
				b.set(xx, L1 + 2, zz, "minecraft:stripped_jungle_log[axis=x]")
				b.set(xx, L1, zz, "minecraft:chiseled_tuff")
				b.set(xx, L1 + 1, zz, "minecraft:red_terracotta")
	for xx in range(tx0 + 2, ax0 - 1, 4):
		hang(b, xx, L1 + 3, wz, 1, soul=True)
	# the hidden treasury at its end
	room(b, m, tx0 - 6, wz - 3, tx0 - 1, wz + 3, L1, 4)
	door(b, tx0 - 1, wz - 1, tx0 - 1, wz + 1, L1)
	deco.chest(b, tx0 - 6, L1, wz, "east", "rare")
	deco.spawner(b, tx0 - 3, L1, wz - 3, "minecraft:cave_spider", count=2, max_nearby=4)
	pot(b, tx0 - 6, L1, wz - 2, cracked=True)
	pot(b, tx0 - 6, L1, wz + 2)
	deco.candles(b, tx0 - 5, L1, wz + 3, 4, color="red")
	deco.skull(b, tx0 - 2, L1, wz + 3, "skeleton_skull")
	b.protect(tx0 - 8, FEET - 2, PZ + HALL + 1, wx1 + 8, G - 1, STAIR_FOOT)


def dispenser(b, x, y, z, facing):
	b.t.set(x, y, z, f"minecraft:dispenser[facing={facing},triggered=false]", N.Compound(
		id=N.String("minecraft:dispenser"),
		Items=N.List([N.Compound(Slot=N.Byte(0), id=N.String("minecraft:arrow"), count=N.Int(6))], N.COMPOUND)))
	b.protected.add((x, y, z))


def jaguar_statue(b, m, x, y, z, facing):
	"""A seated stone jaguar (2 high) on a plinth, facing `facing`, ochre eyes."""
	b.set(x, y, z, "minecraft:polished_tuff")
	b.set(x, y + 1, z, "minecraft:yellow_terracotta")
	b.set(x, y + 2, z, B.fmt("minecraft:tuff_brick_stairs", {"facing": OPP[facing], "half": "bottom", "shape": "straight",
		"waterlogged": "false"}))
	dx, dz = DIRS[facing]
	b.set(x + dx, y, z + dz, B.fmt("minecraft:polished_tuff_stairs", {"facing": OPP[facing], "half": "bottom",
		"shape": "straight", "waterlogged": "false"}))


def offerings(b, m, x0, z0, x1, z1):
	"""The hall of offerings: niches of pots and skulls along the walls, an offering platform, a store of jade."""
	ptuff = m["ptuff"]
	cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
	for x in range(cx - 2, cx + 3):
		for z in range(cz - 2, cz + 3):
			b.set(x, L1, z, ptuff.stairs(x, L1, z, outward(x, z, cx, cz), "bottom") if ring(x, z, cx, cz) == 2 else
				"minecraft:polished_tuff")
	b.set(cx, L1 + 1, cz, "minecraft:oxidized_chiseled_copper")
	pot(b, cx, L1 + 2, cz)
	for (x, z) in ((cx - 1, cz - 1), (cx + 1, cz + 1)):
		deco.candles(b, x, L1 + 1, z, 3, color="red")
	for x in range(x0, x1 + 1, 2):
		for z, f in ((z0 - 1, "south"), (z1 + 1, "north")):
			if b.is_air(x, L1, z):
				continue
			b.set(x, L1 + 1, z, AIR)
			b.set(x, L1 + 2, z, ptuff.stairs(x, L1 + 2, z, OPP[f], "top"))
			if (x // 2) % 3 == 0:
				deco.skull(b, x, L1 + 1, z, "skeleton_skull", rotation=0 if f == "south" else 8)
			elif (x // 2) % 3 == 1:
				pot(b, x, L1 + 1, z, cracked=x % 4 == 0, facing=f)
			else:
				deco.candles(b, x, L1 + 1, z, 2, color="green")
	deco.chest(b, x1, L1, cz, "west", "common", kind="barrel")
	hang(b, cx - 3, L1 + 3, cz, 1)
	hang(b, cx + 3, L1 + 3, cz, 1)


# ---------------------------------------------------------------------------------------------- the cenote

def cenote_radius(y, x, z):
	"""Radius of the sinkhole at layer y (bell shaped: wider below), with a lobed outline."""
	base = 6.0 + 0.1 * max(0, G - y)
	a = math.atan2(z - CEN[1], x - CEN[0])
	return base + 0.5 * math.sin(3 * a + 0.7) + 0.3 * math.sin(5 * a + 2.0)


def cenote(b, m):
	"""The sacred cenote north-east of the pyramid: the tunnel from the east door of the hall, a path cut into the
	wall spiralling up to the south rim, an offering alcove, hanging roots and vines."""
	rock, soil = m["rock"], m["soil"]
	cx, cz = CEN
	bottom = WATER - 2
	path = {}
	t0 = -(FEET - (WATER + 1)) / float(G + 1 - FEET)
	n = 80
	a0 = math.atan2(PZ - cz, PX + HALL + 6 - cx)  # where the tunnel arrives (west)
	for i in range(n + 1):
		t = t0 + (1.0 - t0) * i / n
		a = a0 + t * 1.6 * math.pi
		level = FEET + int(round(t * (G + 1 - FEET)))
		for rr in (0.7, 1.6):
			px, pz = cx + math.cos(a) * 7.0, cz + math.sin(a) * 7.0
			r = cenote_radius(level, px, pz) + rr
			x = int(round(cx + math.cos(a) * r))
			z = int(round(cz + math.sin(a) * r))
			if (x, z) not in path or path[(x, z)] > level:
				path[(x, z)] = level
	b.cenote_path = path
	for x in range(int(cx) - 12, int(cx) + 13):
		for z in range(int(cz) - 12, int(cz) + 13):
			if not (0 <= x < SX and 0 <= z < SZ):
				continue
			dd = dist(x, z, CEN)
			walk = path.get((x, z))
			for y in range(bottom - 1, G + 1):
				r = cenote_radius(y, x, z)
				if dd <= r:
					if y < bottom:
						b.set(x, y, z, rock.full(x, y, z))
					elif y == bottom:
						b.set(x, y, z, "minecraft:clay" if b.hrand(x, y, z, 1) < 0.5 else "minecraft:gravel")
					elif y <= WATER:
						b.set(x, y, z, "minecraft:water")
					else:
						b.set(x, y, z, AIR)
				elif walk is not None and walk - 1 <= y <= walk + 2:
					if y == walk - 1:
						b.set(x, y, z, rock.full(x, y, z) if y < G - 2 else "minecraft:mossy_cobblestone")
					else:
						b.set(x, y, z, AIR)
						b.clear_zone(x, y, z, x, y, z)
				elif dd <= r + 2.6:
					if y < G:
						b.set(x, y, z, soil.full(x, y, z) if y >= G - 2 else rock.full(x, y, z))
					else:
						b.set(x, y, z, m["pave"].full(x, y, z) if dd <= r + 1.2 else "minecraft:moss_block")
	# rim: a broken kerb of carved stones
	for x in range(int(cx) - 10, int(cx) + 11):
		for z in range(int(cz) - 10, int(cz) + 11):
			dd = dist(x, z, CEN)
			r = cenote_radius(G, x, z)
			if r + 0.4 < dd <= r + 1.4 and (x, z) not in path and b.hrand(x, 0, z, 71) < 0.55 and 0 <= x < SX:
				b.set(x, G + 1, z, m["body"].slab(x, G + 1, z, "bottom") if b.hrand(x, 1, z, 72) < 0.5 else
					m["body"].full(x, G + 1, z))
	# an offering alcove half way up
	best = min(path.items(), key=lambda kv: abs(math.atan2(kv[0][1] - cz, kv[0][0] - cx) + math.pi / 2) +
		abs(kv[1] - (G - 4)) * 0.3)
	(ax, az), alev = best
	out = outward(ax, az, cx, cz)
	dx, dz = DIRS[out]
	ox, oz = ax + dx, az + dz
	while (ox, oz) in path:
		ox, oz = ox + dx, oz + dz
	b.set(ox, alev - 1, oz, rock.full(ox, alev - 1, oz))
	deco.chest(b, ox, alev, oz, OPP[out], "common", kind="barrel")
	b.set(ox, alev + 1, oz, AIR)
	deco.candles(b, ox, alev + 1, oz, 2, color="green")
	b.set(ox, alev + 2, oz, rock.full(ox, alev + 2, oz))
	# the tunnel from the east door of the hall
	tx0 = PX + HALL + 3
	for x in range(tx0, int(cx)):
		if dist(x, PZ, CEN) <= cenote_radius(FEET, x, PZ):
			break
		for z in range(PZ - 2, PZ + 3):
			for y in range(FEET - 2, FEET + 5):
				edge = abs(z - PZ) == 2 or y in (FEET - 2, FEET + 4)
				if edge:
					if not b.is_air(x, y, z):
						b.set(x, y, z, rock.full(x, y, z) if x > PX + HALVES[0] else m["deep"].full(x, y, z))
				elif y == FEET - 1:
					b.set(x, y, z, "minecraft:water" if z == PZ else m["hfloor"].full(x, y, z))
				else:
					b.set(x, y, z, AIR)
		b.set(x, FEET + 3, PZ - 1, m["ptuff"].stairs(x, FEET + 3, PZ - 1, "north", "top"))
		b.set(x, FEET + 3, PZ + 1, m["ptuff"].stairs(x, FEET + 3, PZ + 1, "south", "top"))
		b.set(x, FEET - 2, PZ, "minecraft:polished_tuff")
		b.clear_zone(x, FEET, PZ - 1, x, FEET + 1, PZ + 1)
		if (x - tx0) % 5 == 2:
			b.set(x, FEET + 2, PZ - 2, "minecraft:verdant_froglight[axis=y]")
	b.protect(int(cx) - 12, bottom - 1, int(cz) - 12, int(cx) + 12, G + 1, int(cz) + 12)
	b.protect(tx0, FEET - 2, PZ - 2, int(cx), FEET + 4, PZ + 2)


# ----------------------------------------------------------------------------------------------- the plaza

OBS = (19, 92)  # centre of the round tower of the observatory
COURT = (88, 76, 111)  # ball court: axis x, z0, z1 of the playing alley


def plaza(b, m):
	"""Paving of the great plaza (ragged toward the jungle), the sacbe with its stelae, thrones and altars."""
	pave, lime = m["pave"], m["lime"]
	n = b.noise
	for x in range(1, SX - 1):
		for z in range(1, SZ - 1):
			if b.get(x, G, z) is not None:
				continue
			edge = min(x - 1, SX - 2 - x, z - 1, SZ - 2 - z)
			v = n.fbm2(x * 0.09 + 4, z * 0.09 - 2, 3) + 0.08 * edge - 0.25
			if v > 0 and b.hrand(x, G, z, 61) < 0.93:
				b.set(x, G, z, pave)
	# the sacbe: a raised white causeway from the foot of the stairway to the south edge
	for z in range(STAIR_FOOT + 1, SZ):
		for x in range(PX - 3, PX + 4):
			b.set(x, G, z, m["core"].full(x, G, z))
			if abs(x - PX) <= 2:
				b.set(x, G + 1, z, lime.full(x, G + 1, z))
			else:
				b.set(x, G + 1, z, lime.stairs(x, G + 1, z, "east" if x > PX else "west", "bottom"))
			b.clear_zone(x, G + 2, z, x, G + 3, z)
	deco.marker(b, PX, G + 2, SZ - 6, deco.ENTRANCE)
	for (x, z, fallen) in ((PX - 6, STAIR_FOOT + 12, False), (PX + 6, STAIR_FOOT + 12, False),
		(PX - 6, STAIR_FOOT + 17, True), (PX + 6, STAIR_FOOT + 17, False)):
		stele(b, m, x, z, fallen)
	# two jaguar thrones facing the causeway beside the heads
	for x, f in ((PX - 14, "east"), (PX + 14, "west")):
		for xx in range(x - 2, x + 3):
			for zz in range(STAIR_FOOT + 2, STAIR_FOOT + 7):
				b.set(xx, G, zz, "minecraft:polished_tuff" if (xx + zz) % 2 else "minecraft:tuff_bricks")
		jaguar_throne(b, m, x - DIRS[f][0], G + 1, STAIR_FOOT + 4, f)
	venus_platform(b, m, 37, 110)


def stele(b, m, x, z, fallen):
	"""A carved stone slab (1 x 2 x 7) facing the causeway, its round altar in front."""
	lime = m["lime"]
	fx = 1 if x < PX else -1
	if fallen:
		for i in range(7):
			xx = x - fx * i
			for zz in (z, z + 1):
				b.set(xx, G + 1, zz, lime.full(xx, G + 1, zz) if i % 2 else "minecraft:chiseled_stone_bricks")
		b.set(x - fx * 7, G + 1, z + 1, lime.slab(x - fx * 7, G + 1, z + 1, "bottom"))
		b.set(x, G, z, "minecraft:polished_andesite")
		b.set(x + fx, G + 1, z, "minecraft:smooth_stone")
		return
	for zz in (z, z + 1):
		b.set(x, G, zz, "minecraft:polished_andesite")
		for y in range(G + 1, G + 8):
			s = lime.full(x, y, zz)
			if y in (G + 2, G + 6):
				s = "minecraft:chiseled_stone_bricks"
			if y in (G + 4, G + 5):
				s = "minecraft:chiseled_tuff" if (zz == z) == (y == G + 4) else "minecraft:red_terracotta"
			b.set(x, y, zz, s)
		b.set(x, G + 8, zz, lime.stairs(x, G + 8, zz, "north" if zz == z else "south", "bottom"))
	b.set(x + fx, G + 1, z, "minecraft:smooth_stone")
	b.set(x + fx, G + 1, z + 1, "minecraft:smooth_stone")
	b.set(x + fx, G + 2, z, "minecraft:smooth_stone_slab[type=bottom,waterlogged=false]")
	b.set(x + fx, G + 2, z + 1, "minecraft:smooth_stone_slab[type=bottom,waterlogged=false]")
	deco.candles(b, x + fx, G + 3, z, 2, color="red")
	pot(b, x + fx, G + 3, z + 1, cracked=b.hrand(x, 0, z, 5) < 0.5, facing="east" if fx > 0 else "west")
	b.protect(x, G, z, x + fx, G + 8, z + 1)


def talud_platform(b, m, x0, z0, x1, z1, y0, height, stairs=(), mat=None):
	"""
	Rectangular platform: a sloped talud at the bottom, a panelled body, an apron on top; `stairs` = [(side, a0, a1)]
	stairways (from a0 to a1 along the side) climbing to its top. Returns the walking level on top.
	"""
	body, talud, apron = m["body"], m["talud"], m["apron"]
	top = y0 + height - 1
	for x in range(x0, x1 + 1):
		for z in range(z0, z1 + 1):
			edge = x in (x0, x1) or z in (z0, z1)
			corner = x in (x0, x1) and z in (z0, z1)
			f = OPP[_side(x, z, x0, x1, z0, z1)] if edge else None
			b.set(x, y0 - 1, z, m["core"].full(x, y0 - 1, z))
			for y in range(y0, top + 1):
				if edge and not corner and y == y0:
					s = talud.stairs(x, y, z, f, "bottom")
				elif y == top:
					s = apron.full(x, y, z) if edge else ("minecraft:polished_andesite" if (x + z) % 3 else "minecraft:stone_bricks")
				elif edge and (along_rect(x, z, x0, x1, z0, z1) % 4 == 2) and y < top:
					s = panel_block(b, m, x, y, z, 2, 3 if y == top - 1 else 2, (x0 + x1) // 2, (z0 + z1) // 2) \
						if not corner and height > 3 else body.full(x, y, z)
				else:
					s = body.full(x, y, z)
				b.set(x, y, z, s)
	for side, a0, a1 in stairs:
		dx, dz = DIRS[side]
		for k in range(height):
			for a in range(a0, a1 + 1):
				if side in ("north", "south"):
					x, z = a, (z0 - 1 - (height - 1 - k) if side == "north" else z1 + 1 + (height - 1 - k))
				else:
					x, z = (x0 - 1 - (height - 1 - k) if side == "west" else x1 + 1 + (height - 1 - k)), a
				y = y0 + k
				for yy in range(y0 - 1, y):
					b.set(x, yy, z, m["core"].full(x, yy, z))
				b.set(x, y, z, m["steps"].stairs(x, y, z, OPP[side], "bottom"))
				b.clear_zone(x, y + 1, z, x, y + 2, z)
	b.protect(x0, y0, z0, x1, top, z1)
	return top + 1


def along_rect(x, z, x0, x1, z0, z1):
	return z - z0 if x in (x0, x1) else x - x0


def observatory(b, m):
	"""
	The observatory (El Caracol): a round tower on two platforms, its drum cut by narrow windows toward the risings of
	Venus, a gallery around the inner core, the dome half fallen.
	"""
	cx, cz = OBS
	lime, plaster = m["lime"], m["plaster"]
	t1 = talud_platform(b, m, cx - 14, cz - 11, cx + 14, cz + 11, G + 1, 4, stairs=[("east", cz - 3, cz + 3)])
	t2 = talud_platform(b, m, cx - 9, cz - 8, cx + 9, cz + 8, t1, 3, stairs=[("east", cz - 2, cz + 2)])
	r_out, r_in, r_core = 6.6, 4.6, 2.2
	top = t2 + 13
	for x in range(cx - 8, cx + 9):
		for z in range(cz - 8, cz + 9):
			d = math.hypot(x - cx, z - cz)
			for y in range(t2, top + 1):
				if d <= r_out + 0.3:
					if d > r_in or d <= r_core:
						s = lime.full(x, y, z) if (y - t2) % 5 not in (0, 4) else plaster.full(x, y, z)
						if y in (t2 + 4, t2 + 9) and d > r_in:
							s = "minecraft:chiseled_stone_bricks" if (x + z) % 2 else "minecraft:red_terracotta"
						b.set(x, y, z, s)
					else:
						b.set(x, y, z, AIR if y not in (t2 + 5, top) else plaster.full(x, y, z))
	b.set(cx, t2 + 5, cz, plaster.full(cx, t2 + 5, cz))
	# the dome: rings stepping in, then the fallen half
	for k in range(5):
		y = top + 1 + k
		rk = r_out - 1.2 * k
		for x in range(cx - 8, cx + 9):
			for z in range(cz - 8, cz + 9):
				d = math.hypot(x - cx, z - cz)
				if rk - 2.4 < d <= rk + 0.3 or (k == 4 and d <= rk + 0.3):
					b.set(x, y, z, lime.full(x, y, z) if k < 4 else "minecraft:chiseled_stone_bricks")
	# doors to the inner gallery (east, on both levels), narrow windows
	for y0 in (t2, t2 + 6):
		for z in (cz - 1, cz, cz + 1):
			for x in range(cx + 3, cx + 8):
				for y in range(y0, y0 + 3):
					if math.hypot(x - cx, z - cz) > r_in - 0.5:
						b.set(x, y, z, AIR)
		b.clear_zone(cx + 3, y0, cz - 1, cx + 7, y0 + 1, cz + 1)
	for ang in (200, 245, 290, 120, 160):
		a = math.radians(ang)
		for rr in (5.0, 5.8, 6.6):
			x, z = int(round(cx + math.cos(a) * rr)), int(round(cz + math.sin(a) * rr))
			for y in (t2 + 7, t2 + 8, t2 + 11):
				b.set(x, y, z, AIR)
	deco.chest(b, cx - 4, t2, cz, "east", "common", kind="barrel")
	pot(b, cx, t2 + 6, cz - 3)
	deco.candles(b, cx - 3, t2 + 6, cz + 2, 3, color="red")
	b.protect(cx - 8, t2, cz - 8, cx + 8, top + 5, cz + 8)
	return t2


def ball_court(b, m):
	"""
	The great ball court: two long walls with sloped benches, a stone ring high on each inner face, a small temple at
	each end and the temple of the jaguars on the east wall; a skull rack (tzompantli) beside it.
	"""
	ax, z0, z1 = COURT
	body, apron, talud, lime = m["body"], m["apron"], m["talud"], m["lime"]
	H = 7
	for side in (-1, 1):
		# bench cells at ax +- 5, +- 6; wall from ax +- 7 to +- 11
		for z in range(z0, z1 + 1):
			for x in range(ax + side * 5, ax + side * 12, side):
				dd = abs(x - ax)
				b.set(x, G, z, m["core"].full(x, G, z))
				for y in range(G + 1, G + H + 1):
					if dd == 5:
						s = talud.stairs(x, y, z, "east" if side < 0 else "west", "bottom") if y == G + 1 else None
					elif dd == 6:
						s = (body.full(x, y, z) if y == G + 1 else
							(talud.stairs(x, y, z, "east" if side < 0 else "west", "bottom") if y == G + 2 else None))
					elif y == G + H:
						s = apron.full(x, y, z)
					elif dd == 11 and y == G + 1:
						s = talud.stairs(x, y, z, "west" if side < 0 else "east", "bottom")
					else:
						s = lime.full(x, y, z) if dd == 7 else body.full(x, y, z)
						if dd == 7 and y == G + 5 and (z - z0) % 6 == 3:
							s = "minecraft:chiseled_stone_bricks"
					if s is not None:
						b.set(x, y, z, s)
		# the ring, high on the inner face in the middle of the court
		rz = (z0 + z1) // 2
		rx = ax + side * 6
		for dy in (-1, 0, 1):
			for dz in (-1, 0, 1):
				if dy == 0 and dz == 0:
					b.set(rx, G + 6, rz, AIR)
					continue
				b.set(rx, G + 6 + dy, rz + dz, "minecraft:chiseled_tuff_bricks" if (dy + dz) % 2 else "minecraft:polished_tuff")
		b.set(rx + side, G + 6, rz, "minecraft:chiseled_tuff")
	# playing field: packed earth and worn stone
	for x in range(ax - 4, ax + 5):
		for z in range(z0, z1 + 1):
			b.set(x, G, z, "minecraft:packed_mud" if b.hrand(x, 0, z, 81) < 0.6 else m["pave"].full(x, G, z))
			b.clear_zone(x, G + 1, z, x, G + 2, z)
	# the temple of the jaguars on the east wall (south part), facing the court
	tx0, tx1, tz0, tz1 = ax + 8, ax + 11, z1 - 13, z1 - 5
	for x in range(tx0, tx1 + 1):
		for z in range(tz0, tz1 + 1):
			edge = x in (tx0, tx1) or z in (tz0, tz1)
			for y in range(G + H + 1, G + H + 6):
				if y == G + H + 5:
					s = "minecraft:polished_tuff"
				elif edge and not (x == tx0 and tz0 + 2 <= z <= tz1 - 2 and y < G + H + 4):
					s = m["plaster"].full(x, y, z) if y != G + H + 4 else "minecraft:red_terracotta"
				else:
					s = AIR
				b.set(x, y, z, s)
	for z in (tz0 + 2, tz1 - 2):
		# serpent columns at the door: heads on the ground, rattles up
		b.set(tx0 - 1, G + H + 1, z, "minecraft:oxidized_chiseled_copper")
		for y in range(G + H + 2, G + H + 5):
			b.set(tx0 - 1, y, z, "minecraft:chiseled_tuff_bricks" if y < G + H + 4 else "minecraft:oxidized_cut_copper")
	jaguar_statue(b, m, tx1 - 1, G + H + 1, (tz0 + tz1) // 2, "west")
	deco.chest(b, tx1 - 1, G + H + 1, tz0 + 1, "west", "common")
	# the end temples: a low platform with a colonnade
	for zz, f in ((z0 - 5, "south"), (z1 + 1, "north")):
		for x in range(ax - 6, ax + 7):
			for z in range(zz, zz + 4):
				if 0 <= z < SZ:
					b.set(x, G, z, m["core"].full(x, G, z))
					b.set(x, G + 1, z, "minecraft:polished_andesite" if (x + z) % 2 else "minecraft:stone_bricks")
		for x in range(ax - 5, ax + 6, 2):
			zc = zz + (1 if f == "south" else 2)
			if not 0 <= zc < SZ:
				continue
			hgt = 5 if b.hrand(x, 3, zc, 7) < 0.6 else 2
			for y in range(G + 2, G + 2 + hgt):
				b.set(x, y, zc, "minecraft:chiseled_stone_bricks" if (y - G) % 3 == 0 else lime.full(x, y, zc))
	b.protect(ax - 12, G, z0, ax + 12, G + H + 6, z1)
	tzompantli(b, m, ax - 20, z1 - 6)
	colonnade(b, m, ax - 21, z0 - 2, ax - 14, z0 + 19)


def colonnade(b, m, x0, z0, x1, z1):
	"""The hall of columns beside the ball court: a low stylobate, two rows of columns, most of them broken."""
	body, apron, talud = m["body"], m["apron"], m["talud"]
	for x in range(x0, x1 + 1):
		for z in range(z0, z1 + 1):
			edge = x in (x0, x1) or z in (z0, z1)
			b.set(x, G, z, m["core"].full(x, G, z))
			if edge:
				f = OPP[_side(x, z, x0, x1, z0, z1)]
				corner = x in (x0, x1) and z in (z0, z1)
				b.set(x, G + 1, z, body.full(x, G + 1, z) if corner else talud.stairs(x, G + 1, z, f, "bottom"))
			else:
				b.set(x, G + 1, z, "minecraft:polished_andesite" if (x + z) % 3 else "minecraft:stone_bricks")
	for x in (x0 + 2, x1 - 2):
		for z in range(z0 + 2, z1 - 1, 3):
			r = b.hrand(x, 0, z, 77)
			height = 7 if r < 0.35 else (1 + int(r * 5))
			for y in range(G + 2, G + 2 + height):
				s = "minecraft:chiseled_stone_bricks" if (y - G) % 3 == 0 else (
					"minecraft:polished_andesite" if (y + x) % 2 else body.full(x, y, z))
				if (y - G) == 5:
					s = "minecraft:red_terracotta"
				b.set(x, y, z, s)
			if height == 7:
				top = G + 9
				b.set(x, top, z, "minecraft:chiseled_tuff_bricks")
				for d, (dx, dz) in DIRS.items():
					b.set(x + dx, top, z + dz, apron.stairs(x + dx, top, z + dz, OPP[d], "top"))
				b.set(x, top + 1, z, apron.slab(x, top + 1, z, "bottom"))
			elif b.hrand(x, 1, z, 78) < 0.7:
				d = ("north", "south", "east", "west")[int(b.hrand(x, 2, z, 79) * 4)]
				dx, dz = DIRS[d]
				for k in range(2, 2 + max(1, 6 - height)):
					fx, fz = x + dx * k, z + dz * k
					if b.empty(fx, G + 2, fz) and b.get(fx, G + 1, fz) is not None and K.is_structural(b.get(fx, G + 1, fz)):
						b.set(fx, G + 2, fz, "minecraft:polished_andesite" if k % 2 else "minecraft:chiseled_stone_bricks")
	b.protect(x0, G, z0, x1, G + 10, z1)


def tzompantli(b, m, x0, z0):
	"""The skull rack: a low T-shaped platform whose walls are carved with rows of skulls."""
	body, apron = m["body"], m["apron"]
	x1, z1 = x0 + 8, z0 + 6
	for x in range(x0, x1 + 1):
		for z in range(z0, z1 + 1):
			edge = x in (x0, x1) or z in (z0, z1)
			b.set(x, G, z, m["core"].full(x, G, z))
			for y in range(G + 1, G + 4):
				if y == G + 3:
					s = apron.full(x, y, z)
				elif edge:
					s = "minecraft:bone_block[axis=y]" if (x + z + y) % 2 else "minecraft:polished_diorite"
				else:
					s = body.full(x, y, z)
				b.set(x, y, z, s)
	for x in range(x0 + 1, x1, 2):
		deco.skull(b, x, G + 4, z0 + 1, "skeleton_skull", rotation=8)
		deco.skull(b, x, G + 4, z1 - 1, "skeleton_skull", rotation=0)
	for (x, z) in ((x0 + 2, z0 + 3), (x1 - 2, z0 + 3)):
		b.set(x, G + 4, z, "minecraft:bone_block[axis=y]")
		deco.skull(b, x, G + 5, z, "skeleton_skull")
	b.protect(x0, G, z0, x1, G + 5, z1)


def small_temple(b, m):
	"""The temple of the moon (north-west): a four-terrace pyramid, its stair facing the plaza, a shrine on top."""
	cx, cz, h0 = 10, 44, 8
	body, talud, apron, tuff = m["body"], m["talud"], m["apron"], m["tuff"]
	for k in range(4):
		h = h0 - 2 * k
		y0 = G + 1 + 3 * k
		for x in range(cx - h, cx + h + 1):
			for z in range(cz - h, cz + h + 1):
				d = ring(x, z, cx, cz)
				out = outward(x, z, cx, cz)
				for l in range(3):
					y = y0 + l
					if d == h:
						s = talud.stairs(x, y, z, OPP[out], "bottom") if l == 0 else (apron.full(x, y, z) if l == 2 else
							panel_block(b, m, x, y, z, k, 2, cx, cz) if abs(along(x, z, cx, cz)) % 3 == 1 else body.full(x, y, z))
					else:
						s = m["core"].full(x, y, z) if l < 2 else m["ledge"].full(x, y, z)
					b.set(x, y, z, s)
	top = G + 13  # walking level on the last terrace
	# stair on the south face: 12 steps up to the top
	for k in range(12):
		z = cz + 14 - k
		y = G + 1 + k
		for x in range(cx - 1, cx + 2):
			b.set(x, y, z, m["steps"].stairs(x, y, z, "north", "bottom"))
			for yy in range(G, y):
				if b.empty(x, yy, z) or ring(x, z, cx, cz) > h0:
					b.set(x, yy, z, m["core"].full(x, yy, z))
			for yy in range(y + 1, y + 4):
				b.set(x, yy, z, AIR)
			b.clear_zone(x, y + 1, z, x, y + 2, z)
		for x in (cx - 2, cx + 2):
			for yy in range(G, y):
				if b.empty(x, yy, z) or ring(x, z, cx, cz) > h0:
					b.set(x, yy, z, m["core"].full(x, yy, z))
			b.set(x, y, z, tuff.full(x, y, z))
			b.set(x, y + 1, z, tuff.stairs(x, y + 1, z, "north", "bottom"))
	# the shrine
	sx0, sx1, sz0, sz1 = cx - 2, cx + 2, cz - 2, cz + 2
	for x in range(sx0, sx1 + 1):
		for z in range(sz0, sz1 + 1):
			edge = x in (sx0, sx1) or z in (sz0, sz1)
			for y in range(top, top + 5):
				if y == top + 4:
					s = m["plaster"].full(x, y, z)
				elif edge and not (z == sz1 and x == cx and y < top + 3):
					s = m["plaster"].full(x, y, z) if y != top + 3 else "minecraft:red_terracotta"
				else:
					s = AIR
				b.set(x, y, z, s)
	b.set(cx, top + 5, cz, "minecraft:chiseled_tuff_bricks")
	pot(b, cx, top, cz - 1)
	deco.candles(b, cx - 1, top, cz - 1, 3, color="green")
	b.protect(cx - h0, G, cz - h0, cx + h0, top + 5, cz + h0)


def venus_platform(b, m, cx, cz):
	"""Low square platform with a stair on each side and a sacrificial altar on top (platform of Venus)."""
	body, talud, apron, tuff = m["body"], m["talud"], m["apron"], m["tuff"]
	h = 4
	for x in range(cx - h, cx + h + 1):
		for z in range(cz - h, cz + h + 1):
			d = ring(x, z, cx, cz)
			b.set(x, G, z, m["core"].full(x, G, z))
			out = outward(x, z, cx, cz)
			if d == h:
				b.set(x, G + 1, z, talud.stairs(x, G + 1, z, OPP[out], "bottom"))
				b.set(x, G + 2, z, apron.full(x, G + 2, z))
			else:
				b.set(x, G + 1, z, body.full(x, G + 1, z))
				b.set(x, G + 2, z, panel_block(b, m, x, G + 2, z, 1, 2, cx, cz) if d == h - 1 and abs(along(x, z, cx, cz)) % 2
					else ("minecraft:polished_andesite" if (x + z) % 2 else "minecraft:stone_bricks"))
	for d in ("north", "south", "east", "west"):
		dx, dz = DIRS[d]
		for i in (-1, 0, 1):
			x = cx + dx * (h + 1) + (i if dz else 0)
			z = cz + dz * (h + 1) + (i if dx else 0)
			b.set(x, G, z, m["core"].full(x, G, z))
			b.set(x, G + 1, z, body.stairs(x, G + 1, z, OPP[d], "bottom"))
			xi, zi = x - dx, z - dz
			b.set(xi, G + 1, zi, body.full(xi, G + 1, zi))
			b.set(xi, G + 2, zi, body.stairs(xi, G + 2, zi, OPP[d], "bottom"))
	b.set(cx, G + 3, cz, "minecraft:oxidized_chiseled_copper")
	b.set(cx - 1, G + 3, cz, tuff.stairs(cx - 1, G + 3, cz, "east", "bottom"))
	b.set(cx + 1, G + 3, cz, tuff.stairs(cx + 1, G + 3, cz, "west", "bottom"))
	b.set(cx, G + 4, cz, "minecraft:polished_tuff_slab[type=bottom,waterlogged=false]")
	deco.skull(b, cx + 2, G + 3, cz + 2, "skeleton_skull")
	deco.candles(b, cx - 2, G + 3, cz - 2, 3, color="red")
	b.protect(cx - h, G, cz - h, cx + h, G + 4, cz + h)


# ------------------------------------------------------------------------------------------------- decay

CEIBAS = [(7, 10, 34), (104, 58, 30)]  # (x, z, height) of the giant ceibas on the plaza
PYR_TREE = (82, 8)  # the ceiba that took root on the north-east terraces of the pyramid


def ruin_it(b, m):
	n = b.noise
	# the ceiba of the north-east terraces tore the corner apart: a slump down four terraces
	ne = (PX + 27, G + 16, PZ - 27)

	def in_slump(x, y, z):
		return ((x - ne[0]) / 8.0) ** 2 + ((y - ne[1]) / 10.0) ** 2 + ((z - ne[2]) / 8.0) ** 2 < 1.0 + 0.3 * n.n3(
			x * 0.3, y * 0.3, z * 0.3)
	unprotect(b, lambda x, y, z: in_slump(x, y, z) and y > G and ring(x, z) >= (half_at(y) or 0) - 6)
	decay.damage(b, lambda x, y, z, s: in_slump(x, y, z) and y > G and ring(x, z) >= (half_at(y) or 0) - 5)
	# an old landslide on the west face, a breach in the south-east corner, smaller scars
	decay.breach(b, PX - 31, G + 9, PZ - 6, 3.5, 5.0, 4.5, roughness=0.4)
	decay.breach(b, PX + 30, G + 6, PZ + 30, 4.0, 4.5, 4.0, roughness=0.4)
	decay.breach(b, PX - 18, G + 27, PZ + 17, 2.5, 3.0, 2.5)
	decay.breach(b, PX + 20, G + 22, PZ - 5, 2.2, 3.0, 3.0)
	decay.breach(b, PX - 12, G + 38, PZ - 13, 2.0, 2.5, 2.0)
	# the portico lost its east vault
	unprotect(b, lambda x, y, z: x > PX + 3 and y > TOPY and CELLA_Z < z <= SANCT[3] and y < COMB_BASE)
	decay.breach(b, PX + 7, TOPY + 10, SANCT[3] - 2, 3.0, 3.5, 2.5, roughness=0.3)
	# missing steps and a broken balustrade on the grand stairway
	unprotect(b, lambda x, y, z: PX - STAIR_W <= x <= PX + STAIR_W and PZ + 22 <= z <= PZ + 50 and y >= step_y(z) - 2)
	decay.breach(b, PX - 3, step_y(PZ + 40) + 1, PZ + 40, 1.8, 1.4, 2.0, roughness=0.5)
	decay.breach(b, PX + 2, step_y(PZ + 28) + 1, PZ + 28, 1.5, 1.2, 1.6, roughness=0.5)
	decay.breach(b, PX + STAIR_W + 2, step_y(PZ + 33) + 2, PZ + 33, 1.4, 2.2, 2.5, roughness=0.3)
	# the dome of the observatory fell on its south-west side, the ball court lost the north end of its west wall
	ox, oz = OBS
	unprotect(b, lambda x, y, z: math.hypot(x - ox, z - oz) < 9 and y > G + 18)
	decay.damage(b, lambda x, y, z, s: y > G + 18 and math.hypot(x - ox, z - oz) < 9 and (ox - x) + (z - oz) * 0.6 >
		-1.5 + 1.5 * n.n3(x * 0.3, y * 0.3, z * 0.3) + (G + 28 - y) * 0.8)
	ax, z0, z1 = COURT
	unprotect(b, lambda x, y, z: ax - 12 <= x < ax and z0 <= z < z0 + 9 and y > G)
	decay.breach(b, ax - 9, G + 6, z0 + 3, 3.0, 3.5, 4.5, roughness=0.4)
	# general wear: aprons and talud crumble, upper courses lose more
	masonry = lambda s: any(k in s for k in ("brick", "cobble", "andesite", "stone", "tuff", "diorite", "calcite"))
	decay.erode(b, 0.17, top_bias=0.8, y0=G + 2, y1=TOPY, match=masonry)
	decay.erode(b, 0.22, top_bias=0.5, y0=TOPY + 1, y1=COMB_BASE - 1, match=masonry)
	decay.stabilize(b)
	decay.cleanup(b)

	def source(state):
		if "copper" in state:
			return "minecraft:oxidized_cut_copper_slab[type=bottom,waterlogged=false]" if b.rng.random() < 0.25 else False
		if "log" in state or "terracotta" in state:
			return False
		return None
	decay.rubble(b, m["rubble"], keep=0.4, scatter=2.2, top=m["body"], max_height=4, source=source)
	decay.cleanup(b)


# ------------------------------------------------------------------------------------------------ the jungle

def ceiba(b, tx, tz, gy, height, crown=1.0, salt=0, roots_down=False):
	"""
	A giant ceiba: 2 x 2 trunk standing on layer gy, buttress roots, an umbrella crown of branches and leaves, cocoa
	pods. roots_down: long roots crawl down from the trunk over the terraces below.
	"""
	top = gy + height
	cxf, czf = tx + 0.5, tz + 0.5
	for x in (tx, tx + 1):
		for z in (tz, tz + 1):
			b.set(x, gy, z, "minecraft:rooted_dirt")
			for y in range(gy + 1, top + 1):
				b.set(x, y, z, "minecraft:jungle_log[axis=y]")
	for i, ang in enumerate((0.3, 1.1, 1.75, 2.6, 3.4, 4.3, 5.1, 5.8)):
		length = 4 + (i + salt) % 3
		for k in range(1, length + 1):
			x = int(round(cxf + math.cos(ang) * (k + 0.6)))
			z = int(round(czf + math.sin(ang) * (k + 0.6)))
			hgt = max(0, int((length - k) * 1.4))
			for y in range(gy + 1, gy + 1 + hgt):
				if b.empty(x, y, z):
					b.set(x, y, z, "minecraft:jungle_wood[axis=y]")
			if b.empty(x, gy, z):
				b.set(x, gy, z, "minecraft:rooted_dirt")
	if roots_down:
		for ang in (0.2, 1.3, 2.4, 4.0, 5.2):
			x, z, y = cxf + math.cos(ang) * 2, czf + math.sin(ang) * 2, gy
			for k in range(28):
				x += math.cos(ang) * 0.8
				z += math.sin(ang) * 0.8
				xi, zi = int(round(x)), int(round(z))
				while y > G + 1 and b.empty(xi, y - 1, zi):
					y -= 1
				if not b.t.inside(xi, y, zi) or y <= G:
					break
				if b.empty(xi, y, zi) or "leaves" in (b.get(xi, y, zi) or ""):
					b.set(xi, y, zi, "minecraft:jungle_wood[axis=y]" if k % 3 else "minecraft:rooted_dirt")
	r = crown
	branches = [((tx, top - 3, tz + 1), (tx - 9 * r, top + 1, tz + 3)), ((tx + 1, top - 2, tz + 1), (tx + 6 * r, top + 1, tz + 8 * r)),
		((tx, top - 4, tz + 1), (tx - 5 * r, top, tz + 10 * r)), ((tx + 1, top - 1, tz), (tx + 4 * r, top + 3, tz - 6 * r)),
		((tx, top - 6, tz + 1), (tx - 10 * r, top - 3, tz - 6 * r)), ((tx + 1, top - 5, tz), (tx + 10 * r, top - 2, tz - 2))]
	for a, c in branches:
		c = (int(round(c[0])), int(round(c[1])), int(round(c[2])))
		log_line(b, a, c)
		blob(b, c[0], c[1] + 1, c[2], 4.4, 1.5, 4.4, leaves(), salt=c[0] * 7 + c[2] + salt)
		mx, mz = (c[0] * 2 + tx) / 3.0, (c[2] * 2 + tz) / 3.0
		blob(b, mx, c[1] + 2.2, mz, 2.8, 1.2, 2.8, leaves(), salt=c[0] * 11 + c[2] + salt)
	blob(b, tx + 1, top + 2, tz + 1, 5.0, 1.9, 5.0, leaves(), salt=3 + salt)
	for (x, y, z, f) in ((tx - 1, gy + 6, tz, "east"), (tx + 2, gy + 9, tz + 1, "west"), (tx, gy + 12, tz + 2, "north"),
		(tx + 1, gy + 7, tz - 1, "south"), (tx + 2, gy + 14, tz, "west")):
		if b.t.inside(x, y, z) and b.empty(x, y, z):
			b.set(x, y, z, f"minecraft:cocoa[age={1 + (y % 2)},facing={f}]")


def droop_and_vines(b, y_min):
	"""Leaves drooping under the crowns and vines hanging from them."""
	for (x, y, z), s in list(b.t.cells.items()):
		if "jungle_leaves" in s and y > y_min and b.empty(x, y - 1, z) and b.hrand(x, y, z, 93) < 0.2:
			b.set(x, y - 1, z, s)
	for (x, y, z), s in list(b.t.cells.items()):
		if "jungle_leaves" in s and y > y_min and b.hrand(x, y, z, 91) < 0.16:
			for d, (dx, dz) in DIRS.items():
				vx, vz = x + dx, z + dz
				if b.empty(vx, y, vz) and b.t.inside(vx, y, vz):
					length = 2 + int(b.hrand(x, y, z, 92) * 9)
					for k in range(length):
						if not b.empty(vx, y - k, vz) or y - k <= G or (vx, y - k, vz) in b.keep_clear:
							break
						b.set(vx, y - k, vz, B.fmt("minecraft:vine", {OPP[d]: "true"}))
					break


def fallen_tree(b, x0, x1, z0):
	"""A giant tree fell across the plaza: upturned root plate, trunk, crushed crown."""
	for x in range(x0, x1 + 1):
		for z in (z0, z0 + 1):
			for y in (G + 1, G + 2):
				b.set(x, y, z, "minecraft:jungle_log[axis=x]")
	for z in range(z0 - 3, z0 + 5):
		for y in range(G + 1, G + 7):
			d = math.hypot((z - z0 - 0.5) / 3.8, (y - G - 2.5) / 3.2)
			if d < 1.0:
				r = b.hrand(x0 - 1, y, z, 41)
				b.set(x0 - 1, y, z, "minecraft:jungle_wood[axis=x]" if d < 0.45 else
					("minecraft:rooted_dirt" if r < 0.45 else "minecraft:jungle_wood[axis=z]" if r < 0.7 else "minecraft:dirt"))
	log_line(b, (x0 + 6, G + 3, z0), (x0 + 7, G + 5, z0 - 1))
	log_line(b, (x0 + 10, G + 3, z0 + 1), (x0 + 12, G + 4, z0 + 4))
	blob(b, x1 + 3, G + 2, z0 + 1, 3.5, 2.0, 3.8, leaves(), salt=13)
	blob(b, x0 + 12, G + 4, z0 + 4, 2.2, 1.4, 2.2, leaves(), salt=14)
	for x in range(x0, x1 + 1, 3):
		if b.hrand(x, 1, z0, 44) < 0.5:
			b.set(x, G + 3, z0, "minecraft:moss_carpet")
		for z, f in ((z0 - 1, "south"), (z0 + 2, "north")):
			if b.hrand(x, 0, z, 43) < 0.4 and b.empty(x, G + 1, z):
				b.set(x, G + 1, z, f"minecraft:cocoa[age=2,facing={f}]")


def bamboo(b, region, density, salt):
	(x0, z0), (x1, z1) = region
	for x in range(x0, x1 + 1):
		for z in range(z0, z1 + 1):
			if not (0 <= x < SX and 0 <= z < SZ) or b.hrand(x, 0, z, salt) > density:
				continue
			if b.get(x, G, z) is not None and not any(k in b.get(x, G, z) for k in ("dirt", "moss", "podzol", "grass")):
				continue
			if not all(b.empty(x, y, z) for y in range(G + 1, G + 6)):
				continue
			h = 6 + int(b.hrand(x, 1, z, salt) * 10)
			b.set(x, G, z, "minecraft:podzol" if b.get(x, G, z) is None else b.get(x, G, z))
			for k in range(h):
				y = G + 1 + k
				if not b.empty(x, y, z):
					break
				lv = "large" if k >= h - 2 else ("small" if k == h - 3 else "none")
				b.set(x, y, z, f"minecraft:bamboo[age=1,leaves={lv},stage=0]")


def jungle_bushes(b, count=40):
	"""Bushes of jungle leaves around a stub of log on the natural ground at the edges of the site."""
	rnd = b.rng
	placed = 0
	tries = 0
	while placed < count and tries < 1500:
		tries += 1
		x, z = rnd.randrange(1, SX - 1), rnd.randrange(1, SZ - 1)
		if any(b.get(x + dx, G, z + dz) is not None for dx in (-2, 0, 2) for dz in (-2, 0, 2)):
			continue
		if not all(b.empty(x, y, z) for y in range(G + 1, G + 4)):
			continue
		b.set(x, G + 1, z, "minecraft:jungle_log[axis=y]")
		blob(b, x, G + 1.5, z, 1.2 + 0.9 * rnd.random(), 1.2, 1.2 + 0.9 * rnd.random(), leaves(), salt=x * 31 + z)
		placed += 1


def cenote_vines(b, density=0.35):
	"""Long vines hanging from the rim of the cenote down its walls."""
	cx, cz = CEN
	for x in range(int(cx) - 11, int(cx) + 12):
		for z in range(int(cz) - 11, int(cz) + 12):
			if not b.is_air(x, G - 1, z) or (x, G - 1, z) in b.keep_clear:
				continue
			for d, (dx, dz) in DIRS.items():
				wall = b.get(x + dx, G - 1, z + dz)
				if not K.is_solid_full(wall) or b.hrand(x, 0, z, 97 + len(d)) > density:
					continue
				length = 3 + int(b.hrand(x, 1, z, 98) * 10)
				for k in range(length):
					y = G - 1 - k
					if y <= WATER or not b.is_air(x, y, z) or (x, y, z) in b.keep_clear:
						break
					b.set(x, y, z, B.fmt("minecraft:vine", {d: "true"}))
				break


def life(b, m):
	for (x, z, h) in CEIBAS:
		ceiba(b, x, z, G, h, crown=1.0, salt=x)
	# the ceiba of the north-east corner stands in the slump it caused, on the fallen masonry
	tx, tz = PYR_TREE
	gy = max(y for y in range(G, TOPY) for x in (tx, tx + 1) for z in (tz, tz + 1)
		if K.is_structural(b.get(x, y, z)) and not b.is_air(x, y, z))
	for x in range(tx - 1, tx + 3):
		for z in range(tz - 1, tz + 3):
			y = gy
			while y > G and b.empty(x, y, z):
				b.set(x, y, z, "minecraft:rooted_dirt")
				y -= 1
	ceiba(b, tx, tz, gy, 24, crown=0.9, salt=7, roots_down=True)
	fallen_tree(b, 3, 16, 70)
	everything = ((0, G + 1, 0), (SX - 1, SY - 1, SZ - 1))
	veg.replace_surface(b, {"minecraft:stone_bricks": "minecraft:moss_block", "minecraft:mossy_stone_bricks": "minecraft:moss_block",
		"minecraft:cobblestone": "minecraft:mossy_cobblestone", "minecraft:polished_andesite": "minecraft:mossy_stone_bricks"},
		density=0.3, region=((0, G, 0), (SX - 1, G + TH * 6, SZ - 1)))
	veg.vines(b, density=0.32, max_len=8, region=((0, G + 2, 0), (SX - 1, TOPY, SZ - 1)))
	veg.vines(b, density=0.25, max_len=4, region=((0, TOPY + 1, 0), (SX - 1, COMB_BASE + 6, SZ - 1)))
	veg.floor_cover(b, [("minecraft:moss_carpet", 4), ("minecraft:fern", 4), ("minecraft:short_grass", 2),
		(leaves(), 3), (leaves("minecraft:azalea_leaves"), 1), ("minecraft:bush", 2)], density=0.3,
		region=everything, sky=True, need=lambda s: any(k in s for k in ("moss", "cobble", "brick", "dirt", "andesite",
			"podzol", "mud")))
	bamboo(b, ((0, 0), (14, 6)), 0.4, 81)
	bamboo(b, ((100, 98), (111, 115)), 0.3, 82)
	bamboo(b, ((0, 60), (4, 80)), 0.35, 83)
	bamboo(b, ((106, 0), (111, 18)), 0.35, 84)
	jungle_bushes(b)
	droop_and_vines(b, G + 12)
	cx, cz = CEN
	cenote_vines(b)
	cen = ((int(cx) - 11, WATER, int(cz) - 11), (int(cx) + 11, G, int(cz) + 11))
	veg.hanging(b, "minecraft:hanging_roots", density=0.12, region=cen, salt=33)
	veg.hanging(b, "minecraft:cave_vines[age=25,berries=true]", density=0.08, region=cen, max_len=4,
		plant="minecraft:cave_vines_plant[berries=false]", salt=34)
	veg.lichen(b, density=0.06, region=cen)
	for (x, y, z), s in list(b.t.cells.items()):
		if s.startswith("minecraft:water") and y == WATER and b.empty(x, y + 1, z) and dist(x, z, CEN) < 10:
			r = b.hrand(x, y, z, 51)
			if r < 0.12:
				b.set(x, y + 1, z, "minecraft:lily_pad")
			elif r < 0.16 and b.get(x, y - 1, z) == "minecraft:water":
				b.set(x, y - 1, z, "minecraft:big_dripleaf_stem[facing=north,waterlogged=true]")
				b.set(x, y, z, "minecraft:big_dripleaf_stem[facing=north,waterlogged=true]")
				b.set(x, y + 1, z, "minecraft:big_dripleaf[facing=north,tilt=none,waterlogged=false]")
	# inside: cobwebs in the galleries, roots and moss creeping into the hall, lichen
	veg.cobwebs(b, density=0.1, region=((PX - 35, L1, PZ + HALL + 1), (PX + 35, G - 1, STAIR_FOOT)))
	veg.cobwebs(b, density=0.08, region=((PX - 22, TOMB_FEET, PZ - 10), (PX - 6, TOMB_FEET + 8, PZ + 4)))
	veg.hanging(b, "minecraft:hanging_roots", density=0.04, region=((PX - HALL, FEET, PZ - HALL), (PX + HALL, CAP, PZ + HALL)))
	veg.lichen(b, density=0.03, region=((PX - 35, FEET, PZ - HALL - 3), (PX + 35, TOPY, STAIR_FOOT)))
	veg.floor_cover(b, [("minecraft:moss_carpet", 1)], density=0.15, region=((PX + 3, FEET, PZ - 6), (PX + HALL, FEET, PZ + 8)),
		sky=False)
	decay.cleanup(b)
	double_plants(b, ((0, G, 0), (SX - 1, TOPY, SZ - 1)), 0.05, 61)
	prune(b, lambda s: any(k in s for k in ("leaves", "_log", "_wood", "rooted_dirt")))
	# cocoa pods only on the jungle logs they face
	for (x, y, z), s in list(b.t.cells.items()):
		if s.startswith("minecraft:cocoa"):
			dx, dz = DIRS[B.prop(s, "facing", "north")]
			if "jungle_log" not in (b.get(x + dx, y, z + dz) or "") and "jungle_wood" not in (b.get(x + dx, y, z + dz) or ""):
				b.set(x, y, z, AIR)
	decay.cleanup(b)


if __name__ == "__main__":
	import render
	print(render.render_ruin(build()))
