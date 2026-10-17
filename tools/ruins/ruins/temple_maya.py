"""
Temple du Serpent à plumes (tier 3, Overworld: jungle, bamboo jungle, sparse jungle).

A six-terrace stepped pyramid in the Maya style, taken back by the jungle. Every terrace has a sloped base
(talud), a recessed band of carved glyph panels (tablero) and a projecting apron. A monumental stairway climbs
the south face between two balustrades ending in feathered-serpent heads; a white causeway (sacbe) lined with
stelae leads to it across the sacred plaza (altar platform, a fallen giant tree). On the top platform stands the
sanctuary: a three-door portico, a cella with its altar and a tall perforated roof comb carrying the serpent's
mask. A giant ceiba, the world tree, grows by the cenote; its roots tore the north-east corner apart.

The way in: up the stairway, into the cella, then down the hidden stair inside the pyramid (Palenque style, two
offering niches on the way) to a landing under the stairway (a low door in its flank opens onto the plaza, a
store room further south), and two twin flights down into the Hall of the Feathered Serpent: a great
corbel-vaulted hall under the pyramid (21 x 21, 10 to 14 high, four pillars, a frame of beams, the jade mask and
the altar), the arena of a flying guardian. The second way in / out is the sacred cenote east of the pyramid: a
path cut into its wall spirals down to a tunnel that reaches the hall.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from ruinlib import build as K, decay, veg, deco, shapes  # noqa: E402
from ruinlib import blocks as B  # noqa: E402
from ruinlib.build import Mat, Mix, AIR, DIRS, OPP  # noqa: E402
from ruinlib.ruin import Ruin, split_pieces, rules  # noqa: E402

ID = "temple_maya"
SEED = 7707
SX, SY, SZ = 64, 62, 64
G = 14  # ground floor layer
PX, PZ = 29, 23  # pyramid centre
BASE = 18  # outer half width of the first terrace
TH = 4  # terrace height: talud, two layers of tablero, apron
TERRACES = 6
TOPY = G + TH * TERRACES  # top layer of the last terrace = floor of the top platform
TOP_HALF = BASE - 2 * (TERRACES - 1)  # 8
FEET = G - 8  # walking level of the great hall
HALL = 10  # interior half width of the hall
HALL_WALL = G + 1  # last layer of its vertical walls (corbel vault above, capstone at G + 6)
STAIR_FOOT = PZ + TOP_HALF + (TOPY - G - 1)  # z of the first step of the grand stairway (block at G + 1)
CEN = (55.5, 23.0)  # cenote centre
WATER = G - 11  # water surface layer of the cenote
F1 = PZ - 4  # z where the inner stair starts (on the cella floor), going down southward
LANDING = F1 + (TOPY - G)  # first z of the landing at its foot (walking G + 1), under the stairway
NICHE_Z = PZ + 5  # offering niches on both sides of the inner stair
SANCT = (PX - 6, PZ - 7, PX + 6, PZ + 5)  # sanctuary outline x0, z0, x1, z1
CEIBA = (53, 3)  # north-west corner of the 2 x 2 trunk of the giant tree (north of the cenote)


def build():
	b = K.new(SX, SY, SZ, SEED, ground=G, name=ID)
	m = materials()
	foundations(b, m)
	pyramid(b, m)
	stairway(b, m)
	sanctuary(b, m)
	roof_comb(b, m)
	hall(b, m)
	inner_stair(b, m)
	cenote(b, m)
	plaza(b, m)
	ruin_it(b, m)
	life(b, m)
	b.carve_dome(PX + 3, PZ + 9, 34.0, G + 1, SY - G - 2, rough=2.5)
	b.open_clear_zones()
	shapes.fix(b.t)
	pieces = split_pieces(ID, b.t, G, jigsaw_xz=(0, SZ - 1))
	ruin = Ruin(ID, "Temple du Serpent à plumes", "overworld", 3,
		["minecraft:jungle", "minecraft:bamboo_jungle", "minecraft:sparse_jungle"],
		"beard_thin", pieces, G, 0, terrain="minecraft:grass_block", processors=[rules(
			("minecraft:stone_bricks", "minecraft:cracked_stone_bricks", 0.05),
			("minecraft:cobblestone", "minecraft:mossy_cobblestone", 0.2),
			("minecraft:polished_andesite", "minecraft:mossy_stone_bricks", 0.06))])
	ruin.big = True
	ruin.set_weight = 2
	ruin.plan_levels = [(G + 1, "Plan : place sacrée, pyramide, cénote"),
		(FEET, "Plan : salle du Serpent à plumes (arène)"), (TOPY + 1, "Plan : sanctuaire")]
	ruin.cut_level = G + 3
	ruin.under_cut = FEET + 4
	return ruin


def materials():
	return {
		"body": Mat("stone_brick", mossy="mossy_stone_brick", cracked="minecraft:cracked_stone_bricks", moss=0.55,
			moss_top=0.12, crack=0.2, crack_top=0.1, y_low=G + 1, y_high=TOPY, seed=SEED,
			extra=[("minecraft:andesite", 0.03), ("minecraft:tuff", 0.03), ("minecraft:cobblestone", 0.03)]),
		"talud": Mat("cobblestone", mossy="mossy_cobblestone", moss=0.62, moss_top=0.25, y_low=G + 1, y_high=TOPY,
			seed=SEED + 1, extra=[("minecraft:stone_bricks", 0.2)]),
		"apron": Mat("polished_andesite", mossy="mossy_stone_brick", moss=0.3, moss_top=0.05, y_low=G + 1,
			y_high=TOPY, seed=SEED + 2, extra=[("minecraft:stone_bricks", 0.1), ("minecraft:smooth_stone", 0.05)]),
		"ledge": Mix([("minecraft:stone_bricks", 4), ("minecraft:mossy_stone_bricks", 4), ("minecraft:moss_block", 2),
			("minecraft:cracked_stone_bricks", 1), ("minecraft:cobblestone", 1), ("minecraft:mossy_cobblestone", 2)],
			seed=SEED + 3, scale=0.22),
		"tuff": Mat("tuff_brick", seed=SEED + 4, extra=[("minecraft:polished_tuff", 0.2),
			("minecraft:chiseled_tuff_bricks", 0.03)]),
		"ptuff": Mat("polished_tuff", seed=SEED + 5, extra=[("minecraft:tuff_bricks", 0.15)]),
		"steps": Mat("stone_brick", mossy="mossy_stone_brick", cracked="minecraft:cracked_stone_bricks", moss=0.45,
			moss_top=0.08, crack=0.15, y_low=G + 1, y_high=TOPY, seed=SEED + 6),
		"core": Mix([("minecraft:cobblestone", 4), ("minecraft:stone", 3), ("minecraft:andesite", 2), ("minecraft:tuff", 2),
			("minecraft:mossy_cobblestone", 1), ("minecraft:coarse_dirt", 1)], seed=SEED + 7),
		"pave": Mix([("minecraft:stone_bricks", 4), ("minecraft:mossy_stone_bricks", 4), ("minecraft:cracked_stone_bricks", 2),
			("minecraft:cobblestone", 2), ("minecraft:mossy_cobblestone", 3), ("minecraft:moss_block", 2),
			("minecraft:coarse_dirt", 1), ("minecraft:polished_andesite", 1)], seed=SEED + 8, scale=0.2),
		"lime": Mat("polished_diorite", seed=SEED + 9, alt="diorite", extra=[("minecraft:calcite", 0.22),
			("minecraft:diorite", 0.12), ("minecraft:mossy_stone_bricks", 0.06)]),
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
	}


# ------------------------------------------------------------------------------------------------ geometry

def ring(x, z, cx=PX, cz=PZ):
	return max(abs(x - cx), abs(z - cz))


def terrace_of(y):
	k = (y - G - 1) // TH
	return k if y > G and 0 <= k < TERRACES else None


def half_at(y):
	k = terrace_of(y)
	return None if k is None else BASE - 2 * k


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
	return TOPY - (z - (PZ + TOP_HALF))


def pot(b, x, y, z, cracked=False, facing="south"):
	b.t.set(x, y, z, f"minecraft:decorated_pot[cracked={'true' if cracked else 'false'},facing={facing},waterlogged=false]",
		deco.N.Compound(id=deco.N.String("minecraft:decorated_pot")))


# --------------------------------------------------------------------------------------------- foundations

def foundations(b, m):
	"""Solid core under the pyramid (the hall and the passages are carved in it later) and the basal platform."""
	core = m["core"]
	for x in range(PX - BASE - 3, PX + BASE + 4):
		for z in range(PZ - BASE - 3, PZ + BASE + 4):
			d = ring(x, z)
			if d <= BASE:
				for y in range(FEET - 3, G):
					b.set(x, y, z, core)
				b.set(x, G, z, core)
			elif d <= BASE + 2:
				for y in range(G - 3, G):
					b.set(x, y, z, core)
				b.set(x, G, z, m["pave"])
			elif d == BASE + 3 and b.hrand(x, 0, z, 3) < 0.75:
				b.set(x, G, z, m["apron"].full(x, G, z))


# ------------------------------------------------------------------------------------------------- pyramid

GLYPHS = [
	("CPC", "PRP"),
	("RCR", "CPC"),
	("PJP", "RCR"),
	("CRC", "RPR"),
	("PCP", "CJC"),
	("RRC", "CPP"),
]


def panel_block(b, m, x, y, z, k, l):
	"""
	The carved glyph panels of the tablero (seen through the recess): windows of 3 x 2 between the frames, each a
	glyph of chiseled tuff, plain tuff, jade inlays and traces of red paint, worn in places.
	"""
	a = along(x, z)
	window = (a + 2) // 4
	col = (a + 2) % 4 - 1  # 0..2 inside the window (-1 = behind a frame)
	g = GLYPHS[int(b.hrand(window, k, outward(x, z) == "north", 5) * len(GLYPHS))]
	ch = g[0 if l == 2 else 1][max(0, min(2, col))]
	if b.hrand(x, y, z, 19) < 0.18:
		ch = "W"  # worn
	return {
		"C": "minecraft:chiseled_tuff_bricks",
		"P": "minecraft:polished_tuff",
		"R": m["red"].full(x, y, z),
		"J": "minecraft:oxidized_chiseled_copper",
		"W": "minecraft:tuff",
	}[ch]


def pyramid(b, m):
	body, talud, apron, ledge, core = m["body"], m["talud"], m["apron"], m["ledge"], m["core"]
	for k in range(TERRACES):
		h = BASE - 2 * k
		y0 = G + 1 + TH * k
		last = k == TERRACES - 1
		for x in range(PX - h, PX + h + 1):
			for z in range(PZ - h, PZ + h + 1):
				d = ring(x, z)
				corner = abs(x - PX) == h and abs(z - PZ) == h
				out = outward(x, z)
				inward = OPP[out]
				a = abs(along(x, z))
				for l in range(TH):
					y = y0 + l
					top = l == TH - 1
					if last and top:
						s = apron.full(x, y, z) if d >= h - 1 else (
							"minecraft:polished_tuff" if (x + z) % 2 else "minecraft:tuff_bricks")
					elif d < h - 1:
						s = core.full(x, y, z)
					elif d == h - 1:
						s = ledge.full(x, y, z) if top else (panel_block(b, m, x, y, z, k, l) if l in (1, 2) else
							body.full(x, y, z))
					elif corner:
						s = apron.full(x, y, z) if top else body.full(x, y, z)
					elif l == 0:
						s = talud.stairs(x, y, z, inward, "bottom")
					elif top:
						s = apron.full(x, y, z)
					else:
						s = body.full(x, y, z) if (a % 4 == 2 or a >= h - 1) else AIR
					b.set(x, y, z, s)
				# the apron overhangs the ledge below
				if d == h and not corner:
					dx, dz = DIRS[out]
					ox, oz = x + dx, z + dz
					if b.empty(ox, y0 + TH - 1, oz):
						b.set(ox, y0 + TH - 1, oz, apron.stairs(ox, y0 + TH - 1, oz, inward, "top"))
	# the inside of the pyramid stays out of the decay (only its skin wears, except where it collapses)
	for x in range(PX - BASE, PX + BASE + 1):
		for z in range(PZ - BASE, PZ + BASE + 1):
			d = ring(x, z)
			for y in range(FEET - 3, TOPY + 1):
				h = half_at(y)
				if y <= G or (h is not None and d <= h - 2):
					b.protected.add((x, y, z))


# ----------------------------------------------------------------------------------------- grand stairway

def stairway(b, m):
	steps, body, tuff, jade, core, apron = m["steps"], m["body"], m["tuff"], m["jade"], m["core"], m["apron"]
	for z in range(PZ + TOP_HALF + 1, STAIR_FOOT + 1):
		y = step_y(z)
		beyond = z > PZ + BASE
		for x in range(PX - 5, PX + 6):
			flank = x in (PX - 5, PX + 5)
			for yy in range(G, y):
				if flank and beyond:
					band = (yy - G - 1) % TH == TH - 1
					b.set(x, yy, z, apron.full(x, yy, z) if band else body.full(x, yy, z))
				elif beyond or b.get(x, yy, z) is None or b.is_air(x, yy, z) or flank:
					b.set(x, yy, z, core.full(x, yy, z) if not flank else body.full(x, yy, z))
			if abs(x - PX) <= 3:
				b.set(x, y, z, steps.stairs(x, y, z, "north", "bottom"))
				for h in range(1, 5):
					b.set(x, y + h, z, AIR)
				b.clear_zone(x, y + 1, z, x, y + 2, z)
			else:
				# balustrades: a sloped parapet one block above the steps, scaled with jade feathers
				b.set(x, y, z, tuff.full(x, y, z))
				feather = (z - STAIR_FOOT) % 3 == 0
				b.set(x, y + 1, z, jade.stairs(x, y + 1, z, "north", "bottom") if feather and flank else
					tuff.stairs(x, y + 1, z, "north", "bottom"))
				for h in range(2, 4):
					b.set(x, y + h, z, AIR)
		if beyond:
			# plinth along the flanks of the projecting stairway
			for x, f in ((PX - 6, "east"), (PX + 6, "west")):
				b.set(x, G + 1, z, m["talud"].stairs(x, G + 1, z, f, "bottom"))
	# the platform edge: pedestals with braziers at the top of the balustrades
	for x in (PX - 5, PX - 4, PX + 4, PX + 5):
		b.set(x, TOPY + 1, PZ + TOP_HALF, tuff.full(x, TOPY + 1, PZ + TOP_HALF))
		b.set(x, TOPY + 2, PZ + TOP_HALF, "minecraft:chiseled_tuff_bricks" if x in (PX - 5, PX + 5) else
			tuff.slab(x, TOPY + 2, PZ + TOP_HALF, "bottom"))
	for x in (PX - 5, PX + 5):
		b.t.set(x, TOPY + 3, PZ + TOP_HALF, "minecraft:campfire[facing=south,lit=false,signal_fire=false]",
			deco.N.Compound(id=deco.N.String("minecraft:campfire")))
	serpent_head(b, m, PX - 6)
	serpent_head(b, m, PX + 3)
	b.protect(PX - 3, G, PZ + TOP_HALF + 1, PX + 3, TOPY, STAIR_FOOT)


def serpent_head(b, m, x0):
	"""
	Feathered-serpent head (4 wide, columns x0..x0+3) at the foot of a balustrade, facing south, jaws open: lower
	jaw with the red tongue, fangs, glowing eyes on the sides, a fan of jade feathers rising behind.
	"""
	z0 = STAIR_FOOT + 1
	tuff, jade, ptuff = m["tuff"], m["jade"], m["ptuff"]
	cols = range(x0, x0 + 4)
	for x in cols:
		side = x in (x0, x0 + 3)
		b.set(x, G, z0 - 1, "minecraft:polished_tuff")
		for z in range(z0, z0 + 6):
			b.set(x, G, z, "minecraft:polished_tuff")
		# lower jaw
		for z in range(z0, z0 + 5):
			b.set(x, G + 1, z, tuff.full(x, G + 1, z))
		b.set(x, G + 1, z0 + 5, tuff.stairs(x, G + 1, z0 + 5, "north", "bottom"))
		for z in range(z0, z0 + 5):
			b.set(x, G + 2, z, tuff.full(x, G + 2, z) if (side or z == z0) else "minecraft:red_terracotta")
		b.set(x, G + 2, z0 + 5, ptuff.slab(x, G + 2, z0 + 5, "bottom") if side else AIR)
		# open mouth, fangs at the front corners
		b.set(x, G + 3, z0, tuff.full(x, G + 3, z0))
		for z in range(z0 + 1, z0 + 5):
			b.set(x, G + 3, z, tuff.full(x, G + 3, z) if (side and z <= z0 + 2) else AIR)
		b.set(x, G + 3, z0 + 4, "minecraft:pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]"
			if side else AIR)
		# upper jaw and snout
		for z in range(z0, z0 + 5):
			s = tuff.full(x, G + 4, z)
			if side and z == z0 + 2:
				s = "minecraft:ochre_froglight[axis=y]"
			if not side and z == z0 + 4:
				s = "minecraft:chiseled_tuff_bricks"
			b.set(x, G + 4, z, s)
		b.set(x, G + 4, z0 + 5, tuff.stairs(x, G + 4, z0 + 5, "north", "top"))
		# brow and forehead sloping down to the snout
		b.set(x, G + 5, z0, tuff.full(x, G + 5, z0))
		b.set(x, G + 5, z0 + 1, tuff.full(x, G + 5, z0 + 1) if side else "minecraft:chiseled_tuff_bricks")
		b.set(x, G + 5, z0 + 2, tuff.stairs(x, G + 5, z0 + 2, "north", "bottom"))
		b.set(x, G + 5, z0 + 3, tuff.slab(x, G + 5, z0 + 3, "bottom") if not side else AIR)
		# crest of feathers swept back
		b.set(x, G + 6, z0, jade.stairs(x, G + 6, z0, "north", "bottom") if side else
			"minecraft:oxidized_chiseled_copper")
		if not side:
			b.set(x, G + 7, z0, jade.slab(x, G + 7, z0, "bottom"))
	# side plumes (feathered ears)
	for x, f in ((x0 - 1, "east"), (x0 + 4, "west")):
		b.set(x, G + 4, z0 + 1, jade.stairs(x, G + 4, z0 + 1, f, "top"))
		b.set(x, G + 5, z0, jade.stairs(x, G + 5, z0, f, "bottom"))
	b.protect(x0 - 1, G, z0 - 1, x0 + 4, G + 7, z0 + 5)


# ----------------------------------------------------------------------------------------------- sanctuary

def _side(x, z, x0, x1, z0, z1):
	if x == x0:
		return "west"
	if x == x1:
		return "east"
	if z == z0:
		return "north"
	return "south"


def sanctuary(b, m):
	tuff, ptuff, jade, red = m["tuff"], m["ptuff"], m["jade"], m["red"]
	x0, z0, x1, z1 = SANCT
	T = TOPY
	# walls and roof mass (vaults carved below)
	for x in range(x0, x1 + 1):
		for z in range(z0, z1 + 1):
			edge = x in (x0, x1) or z in (z0, z1)
			for y in range(T + 1, T + 11):
				if y <= T + 5:
					s = (tuff.full(x, y, z) if y > T + 1 else ptuff.full(x, y, z)) if (edge or z == PZ + 1) else AIR
				else:
					s = tuff.full(x, y, z) if edge else ptuff.full(x, y, z)
				b.set(x, y, z, s)
	# corbel vaults: portico (z PZ+2..PZ+4) and cella (z PZ-6..PZ)
	for (za, zb) in ((PZ + 2, PZ + 4), (PZ - 6, PZ)):
		for k in range(0, 6):
			y = T + 6 + k
			lo, hi = za + k, zb - k
			if lo > hi:
				break
			for x in range(x0 + 1, x1):
				for z in range(lo, hi + 1):
					b.set(x, y, z, AIR)
				if lo < hi:
					b.set(x, y, lo, ptuff.stairs(x, y, lo, "north", "top"))
					b.set(x, y, hi, ptuff.stairs(x, y, hi, "south", "top"))
	for z in (PZ + 3, PZ - 3):
		for x in range(x0 + 1, x1):
			b.set(x, T + 5, z, "minecraft:stripped_jungle_log[axis=x]")
	# base molding, corner pilasters and piers of the front
	for x in range(x0 - 1, x1 + 2):
		for z in range(z0 - 1, z1 + 2):
			if not (x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)):
				continue
			if x in (x0 - 1, x1 + 1) and z in (z0 - 1, z1 + 1):
				continue
			f = OPP[_side(x, z, x0 - 1, x1 + 1, z0 - 1, z1 + 1)]
			b.set(x, T + 1, z, tuff.stairs(x, T + 1, z, f, "bottom"))
			b.set(x, T + 6, z, tuff.stairs(x, T + 6, z, f, "top"))
			b.set(x, T + 10, z, ptuff.stairs(x, T + 10, z, f, "top"))
	for (x, z) in ((x0, z1 + 1), (x1, z1 + 1), (PX - 2, z1 + 1), (PX + 2, z1 + 1), (x0, z0 - 1), (x1, z0 - 1),
		(x0 - 1, z0), (x1 + 1, z0), (x0 - 1, z1), (x1 + 1, z1)):
		for y in range(T + 1, T + 6):
			b.set(x, y, z, ptuff.full(x, y, z) if y != T + 4 else "minecraft:chiseled_tuff_bricks")
	# front: three doors with wooden lintels
	for (xa, xb) in ((PX - 1, PX + 1), (PX - 4, PX - 3), (PX + 3, PX + 4)):
		for x in range(xa, xb + 1):
			for y in range(T + 1, T + 4):
				b.set(x, y, z1, AIR)
			b.set(x, T + 4, z1, "minecraft:stripped_jungle_log[axis=x]")
		b.clear_zone(xa, T + 1, z1 - 1, xb, T + 2, z1 + 1)
	for x in range(PX - 1, PX + 2):
		for y in range(T + 1, T + 4):
			b.set(x, y, PZ + 1, AIR)
		b.set(x, T + 4, PZ + 1, "minecraft:stripped_jungle_log[axis=x]")
	b.clear_zone(PX - 1, T + 1, PZ, PX + 1, T + 2, PZ + 2)
	# upper facade: a frieze painted red, carved stones, jade masks
	for x in range(x0, x1 + 1):
		for z in range(z0, z1 + 1):
			if not (x in (x0, x1) or z in (z0, z1)):
				continue
			a = x - PX if z in (z0, z1) else z - PZ
			for y in (T + 7, T + 8, T + 9):
				if y == T + 8:
					s = "minecraft:chiseled_tuff_bricks" if a % 3 == 0 else red.full(x, y, z)
				elif y == T + 7:
					s = red.full(x, y, z) if a % 2 else "minecraft:chiseled_tuff"
				else:
					s = tuff.full(x, y, z)
				b.set(x, y, z, s)
	for x in range(x0, x1 + 1):
		for z in range(z0, z1 + 1):
			b.set(x, T + 10, z, ptuff.full(x, T + 10, z))
			if x in (x0, x1) or z in (z0, z1):
				b.set(x, T + 11, z, ptuff.slab(x, T + 11, z, "bottom") if (x + z) % 2 else ptuff.full(x, T + 11, z))
	# long-nosed rain-god masks at the front corners, the serpent mask over the main door, side masks
	for x in (x0, x1):
		b.set(x, T + 8, z1 + 1, "minecraft:oxidized_chiseled_copper")
		b.set(x, T + 7, z1 + 1, jade.stairs(x, T + 7, z1 + 1, "north", "top"))
		b.set(x, T + 9, z1 + 1, tuff.stairs(x, T + 9, z1 + 1, "north", "bottom"))
		b.set(x, T + 8, z1 + 2, "minecraft:oxidized_cut_copper_stairs[facing=north,half=top]")
	b.set(PX, T + 8, z1 + 1, "minecraft:oxidized_chiseled_copper")
	for x in (PX - 1, PX + 1):
		b.set(x, T + 8, z1 + 1, "minecraft:verdant_froglight[axis=y]")
	b.set(PX, T + 7, z1 + 1, jade.stairs(PX, T + 7, z1 + 1, "north", "top"))
	b.set(PX, T + 9, z1 + 1, jade.stairs(PX, T + 9, z1 + 1, "north", "bottom"))
	for x, f in ((x0 - 1, "east"), (x1 + 1, "west")):
		b.set(x, T + 8, PZ - 1, "minecraft:oxidized_chiseled_copper")
		b.set(x, T + 7, PZ - 1, jade.stairs(x, T + 7, PZ - 1, f, "top"))
		b.set(x, T + 9, PZ - 1, tuff.stairs(x, T + 9, PZ - 1, f, "bottom"))
	# floor of red-painted plaster
	for x in range(x0 + 1, x1):
		for z in range(z0 + 1, z1):
			if z != PZ + 1:
				b.set(x, T, z, "minecraft:polished_tuff" if (x * 3 + z) % 4 else "minecraft:red_terracotta")
	# the cella: altar against the back wall, offerings, the rare chest
	for x in range(PX - 2, PX + 3):
		b.set(x, T + 1, z0 + 1, "minecraft:polished_tuff" if abs(x - PX) < 2 else ptuff.stairs(x, T + 1, z0 + 1,
			"east" if x < PX else "west", "bottom"))
	b.set(PX, T + 2, z0 + 1, "minecraft:oxidized_chiseled_copper")
	pot(b, PX, T + 3, z0 + 1)
	deco.candles(b, PX - 1, T + 2, z0 + 1, 3, color="red")
	deco.candles(b, PX + 1, T + 2, z0 + 1, 4, color="green")
	deco.chest(b, x1 - 1, T + 1, z0 + 1, "south", "rare")
	deco.chest(b, x0 + 1, T + 1, z1 - 1, "east", "common", kind="barrel")
	pot(b, x0 + 1, T + 1, z0 + 1, cracked=True)
	pot(b, x1 - 1, T + 1, PZ - 1, cracked=True, facing="west")
	deco.candles(b, x0 + 1, T + 1, PZ - 1, 2, color="red")
	deco.hanging_lantern(b, PX - 3, T + 4, PZ - 3, 1)
	deco.hanging_lantern(b, PX + 3, T + 4, PZ - 3, 1)
	deco.hanging_lantern(b, PX, T + 4, PZ + 3, 1)
	# a chacmool reclining at the west end of the portico, the offering bowl on its belly
	cz = PZ + 3
	b.set(x0 + 1, T + 1, cz, "minecraft:polished_tuff")
	b.set(x0 + 1, T + 2, cz, "minecraft:chiseled_tuff")
	b.set(x0 + 2, T + 1, cz, tuff.stairs(x0 + 2, T + 1, cz, "west", "bottom"))
	pot(b, x0 + 2, T + 2, cz, facing="east")
	b.set(x0 + 3, T + 1, cz, tuff.full(x0 + 3, T + 1, cz))
	b.set(x0 + 4, T + 1, cz, tuff.slab(x0 + 4, T + 1, cz, "bottom"))
	b.protect(x0, T, z0, x1, T + 4, z1)


def roof_comb(b, m):
	"""Tall perforated crest over the back of the sanctuary carrying the serpent's mask (Tikal style)."""
	tuff = m["tuff"]
	x0, z0, x1, z1 = SANCT
	base = TOPY + 11
	widths = [6, 6, 6, 5, 5, 5, 4, 4, 3, 3, 2, 1]
	depth = (z0 + 1, z0 + 2, z0 + 3)
	for i, w in enumerate(widths):
		y = base + i
		for x in range(PX - w, PX + w + 1):
			for z in depth:
				b.set(x, y, z, tuff.full(x, y, z))
		nxt = widths[i + 1] if i + 1 < len(widths) else 0
		if nxt < w:
			for x in (PX - w, PX + w):
				for z in depth:
					b.set(x, y, z, tuff.stairs(x, y, z, "east" if x < PX else "west", "bottom"))
	# the back face: horizontal mouldings
	for i in (2, 5, 8):
		y = base + i
		for x in range(PX - widths[i] + 1, PX + widths[i]):
			b.set(x, y, z0, tuff.stairs(x, y, z0, "south", "top"))
	# perforations (light through the crest)
	for (x, y) in ((PX - 5, base + 1), (PX + 5, base + 1), (PX - 4, base + 4), (PX + 4, base + 4),
		(PX - 3, base + 6), (PX + 3, base + 6), (PX - 2, base + 8), (PX + 2, base + 8)):
		for z in depth:
			b.set(x, y, z, AIR)
			b.set(x, y + 1, z, AIR)
	# the mask on the front face
	zf = z0 + 4
	rows = [
		".JJJJJ.",
		"JCRCRCJ",
		".SESES.",
		"RSSNSSR",
		"R.MMM.R",
		"..FMF..",
		"...J...",
	]
	for i, row in enumerate(rows):
		y = base + 8 - i
		for j, ch in enumerate(row):
			x = PX - 3 + j
			s = {
				"J": "minecraft:oxidized_chiseled_copper",
				"C": "minecraft:chiseled_tuff_bricks",
				"R": "minecraft:oxidized_cut_copper" if j in (0, 6) else "minecraft:red_terracotta",
				"S": tuff.full(x, y, zf),
				"E": "minecraft:verdant_froglight[axis=y]",
				"N": "minecraft:chiseled_tuff",
				"M": "minecraft:red_terracotta",
				"F": tuff.stairs(x, y, zf, "north", "top"),
			}.get(ch)
			if s is not None:
				b.set(x, y, zf, s)
	for z in depth:
		b.set(PX, base + 12, z, "minecraft:oxidized_cut_copper_slab[type=bottom]")


# --------------------------------------------------------------------------------------------- inner stair

def room(b, m, x0, z0, x1, z1, feet, height, floor=None):
	for x in range(x0 - 1, x1 + 2):
		for z in range(z0 - 1, z1 + 2):
			if x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1):
				for y in range(feet - 1, feet + height + 1):
					if not b.is_air(x, y, z):
						b.set(x, y, z, m["tuff"].full(x, y, z))
	for x in range(x0, x1 + 1):
		for z in range(z0, z1 + 1):
			b.set(x, feet - 1, z, floor or ("minecraft:polished_tuff" if (x + z) % 2 else "minecraft:tuff_bricks"))
			for y in range(feet, feet + height):
				b.set(x, y, z, AIR)
			b.set(x, feet + height, z, m["ptuff"].full(x, feet + height, z))


def door(b, x0, z0, x1, z1, feet, height=2):
	for x in range(x0, x1 + 1):
		for z in range(z0, z1 + 1):
			for y in range(feet, feet + height):
				b.set(x, y, z, AIR)
	b.clear_zone(x0, feet, z0, x1, feet + 1, z1)


def inner_stair(b, m):
	"""
	The hidden stair of the cella going down south under the stairway to a landing at ground level; two offering
	niches on the way, a low door in the flank of the stairway, a store room and twin flights down into the hall.
	"""
	tuff, ptuff, steps = m["tuff"], m["ptuff"], m["steps"]
	for z in range(F1 + 1, LANDING):
		w = TOPY + 1 - (z - F1)
		for x in range(PX - 2, PX + 3):
			for y in range(w - 2, w + 5):
				if not (y >= TOPY and z <= F1 + 3):
					b.set(x, y, z, tuff.full(x, y, z))
		for x in range(PX - 1, PX + 2):
			b.set(x, w - 1, z, steps.stairs(x, w - 1, z, "north", "bottom"))
			for y in range(w, w + 4):
				b.set(x, y, z, AIR)
			b.clear_zone(x, w, z, x, w + 1, z)
		if w + 3 < TOPY:
			b.set(PX - 1, w + 3, z, ptuff.stairs(PX - 1, w + 3, z, "west", "top"))
			b.set(PX + 1, w + 3, z, ptuff.stairs(PX + 1, w + 3, z, "east", "top"))
		if (z - F1) % 5 == 2:
			b.set(PX - 2, w + 1, z, "minecraft:chiseled_tuff_bricks")
			b.set(PX + 2, w + 1, z, "minecraft:chiseled_tuff_bricks")
			b.set(PX - 1, w + 1, z, "minecraft:copper_wall_torch[facing=east]")
	# the opening in the cella floor (the top of the stair), a jade kerb on both sides
	for z in range(F1 + 1, F1 + 4):
		for x in range(PX - 1, PX + 2):
			b.set(x, TOPY, z, AIR)
			for y in range(TOPY + 1, TOPY + 4):
				b.set(x, y, z, AIR)
		for x in (PX - 2, PX + 2):
			b.set(x, TOPY + 1, z, "minecraft:oxidized_cut_copper_slab[type=bottom]")
	# offering niches on both sides, half way down
	nw = TOPY + 1 - (NICHE_Z - F1)
	for sx in (-1, 1):
		xa, xb = (PX + 3, PX + 7) if sx > 0 else (PX - 7, PX - 3)
		room(b, m, xa, NICHE_Z - 1, xb, NICHE_Z + 1, nw, 3)
		door(b, PX + 2 * sx, NICHE_Z, PX + 2 * sx, NICHE_Z, nw, 2)
		far = xb if sx > 0 else xa
		for z in (NICHE_Z - 1, NICHE_Z + 1):
			b.set(far, nw, z, "minecraft:polished_tuff")
		b.set(far, nw, NICHE_Z, "minecraft:oxidized_chiseled_copper")
		deco.candles(b, far, nw + 1, NICHE_Z - 1, 3, color="green" if sx > 0 else "red")
		pot(b, far, nw + 1, NICHE_Z + 1, cracked=sx < 0)
		deco.skull(b, xa + 1 if sx > 0 else xb - 1, nw, NICHE_Z + 1, "skeleton_skull")
	deco.chest(b, PX + 7, nw + 1, NICHE_Z, "west", "rare")
	deco.spawner(b, PX - 5, nw, NICHE_Z, "minecraft:cave_spider", count=2, max_nearby=4)
	deco.chest(b, PX - 7, nw + 1, NICHE_Z, "east", "common")
	# the landing under the stairway
	lz0, lz1 = LANDING, LANDING + 1
	room(b, m, PX - 4, lz0, PX + 4, lz1, G + 1, 3)
	deco.hanging_lantern(b, PX, G + 3, lz0, 1)
	for x in (PX - 4, PX + 4):
		b.set(x, G + 1, lz1, "minecraft:chiseled_tuff_bricks")
		deco.candles(b, x, G + 2, lz1, 3, color="green")
	# the low door in the east flank of the stairway, onto the plaza
	door(b, PX + 5, lz0, PX + 5, lz1, G + 1, 2)
	for z in (lz0, lz1):
		b.set(PX + 5, G + 3, z, "minecraft:stripped_jungle_log[axis=z]")
		b.set(PX + 5, G, z, "minecraft:polished_tuff")
		b.set(PX + 6, G + 1, z, AIR)
		b.clear_zone(PX + 6, G + 1, z, PX + 7, G + 2, z)
	# store room under the lower steps, reached from the landing
	room(b, m, PX - 4, lz1 + 2, PX + 4, lz1 + 5, G + 1, 3)
	door(b, PX, lz1 + 1, PX, lz1 + 1, G + 1, 2)
	deco.chest(b, PX + 4, G + 1, lz1 + 5, "west", "common", kind="barrel")
	for (x, z, c) in ((PX - 4, lz1 + 5, False), (PX - 3, lz1 + 5, True), (PX - 4, lz1 + 4, False), (PX + 4, lz1 + 2, True)):
		pot(b, x, G + 1, z, cracked=c)
	deco.skull(b, PX - 2, G + 1, lz1 + 5, "skeleton_skull")
	# the twin flights going down north into the hall (doorways at floor level in its south wall)
	for sx in (-1, 1):
		xs = (PX + 3 * sx, PX + 4 * sx)
		for z in range(PZ + HALL + 2, LANDING):
			w = G + 1 - (LANDING - z)
			for x in (PX + 2 * sx, PX + 5 * sx):
				for y in range(w - 2, w + 4):
					b.set(x, y, z, tuff.full(x, y, z))
			for x in xs:
				b.set(x, w - 2, z, tuff.full(x, w - 2, z))
				b.set(x, w - 1, z, steps.stairs(x, w - 1, z, "south", "bottom"))
				for y in range(w, w + 3):
					b.set(x, y, z, AIR)
				b.set(x, w + 3, z, ptuff.full(x, w + 3, z))
				b.clear_zone(x, w, z, x, w + 1, z)
			if (LANDING - z) % 4 == 2:
				b.set(xs[0], w + 2, z, "minecraft:oxidized_copper_lantern[hanging=true,waterlogged=false]")
		for x in xs:
			door(b, x, PZ + HALL + 1, x, PZ + HALL + 1, FEET, 3)
			b.set(x, FEET - 1, PZ + HALL + 1, "minecraft:polished_tuff")
		b.set(xs[0], FEET + 3, PZ + HALL + 1, ptuff.stairs(xs[0], FEET + 3, PZ + HALL + 1, "east" if sx < 0 else "west", "top"))
		b.set(xs[1], FEET + 3, PZ + HALL + 1, ptuff.stairs(xs[1], FEET + 3, PZ + HALL + 1, "west" if sx < 0 else "east", "top"))
	b.protect(PX - 8, FEET - 1, PZ + HALL + 1, PX + 8, G + 4, lz1 + 6)
	b.protect(PX - 8, nw - 1, NICHE_Z - 2, PX + 8, nw + 4, NICHE_Z + 2)


# ---------------------------------------------------------------------------------------------- the hall

def vault_half(y):
	"""Interior half width of the hall at layer y (corbel vault above the walls), None above the capstone."""
	if y <= HALL_WALL:
		return HALL
	k = y - HALL_WALL
	return HALL - k if k <= 4 else None


def hall(b, m):
	tuff, ptuff, red = m["tuff"], m["ptuff"], m["red"]
	for x in range(PX - HALL - 2, PX + HALL + 3):
		for z in range(PZ - HALL - 2, PZ + HALL + 3):
			d = ring(x, z)
			for y in range(FEET - 2, G + 8):
				hv = vault_half(y)
				if y >= FEET and hv is not None and d <= hv:
					b.set(x, y, z, AIR)
				elif y == FEET - 1 and d <= HALL:
					b.set(x, y, z, m["hfloor"].full(x, y, z))
				elif hv is not None and d == hv + 1 and y > HALL_WALL:
					b.set(x, y, z, ptuff.stairs(x, y, z, outward(x, z), "top"))
				elif d <= HALL + 2:
					b.set(x, y, z, tuff.full(x, y, z))
	# walls: plinth, carved and painted frieze, pilasters with copper torches, niches with offerings
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
			b.set(wx, FEET + 5, wz, "minecraft:chiseled_tuff_bricks" if a % 2 == 0 else red.full(wx, FEET + 5, wz))
			b.set(wx, FEET + 6, wz, "minecraft:red_terracotta" if a % 4 == 1 else "minecraft:polished_tuff")
			b.set(x, FEET, z, ptuff.stairs(x, FEET, z, out, "bottom"))
			if a % 6 == 0 and abs(a) <= 6:
				for y in range(FEET, HALL_WALL + 1):
					b.set(x, y, z, ptuff.full(x, y, z) if y != FEET + 5 else "minecraft:chiseled_tuff")
				ix, iz = x - dx, z - dz
				b.set(ix, FEET, iz, ptuff.stairs(ix, FEET, iz, out, "bottom"))
				b.set(ix, FEET + 4, iz, f"minecraft:copper_wall_torch[facing={OPP[out]}]")
			elif a % 6 == 3 and out in ("west", "north"):
				b.set(wx, FEET + 1, wz, AIR)
				b.set(wx, FEET + 2, wz, ptuff.stairs(wx, FEET + 2, wz, out, "top"))
				if b.hrand(x, 0, z, 23) < 0.5:
					deco.skull(b, wx, FEET + 1, wz, "skeleton_skull", rotation={"west": 4, "north": 0}[out] * 2 % 16)
				else:
					deco.candles(b, wx, FEET + 1, wz, 3, color="red")
	# floor: polished tuff with a quincunx of jade and ochre light
	for x in range(PX - HALL, PX + HALL + 1):
		for z in range(PZ - HALL, PZ + HALL + 1):
			dx, dz = x - PX, z - PZ
			if (dx == 0 or dz == 0) and b.get(x, FEET - 1, z) is not None:
				b.set(x, FEET - 1, z, "minecraft:tuff_bricks" if (dx + dz) % 2 else "minecraft:polished_tuff")
			if abs(dx) == abs(dz) and 1 <= abs(dx) <= 3:
				b.set(x, FEET - 1, z, "minecraft:oxidized_cut_copper")
	for (dx, dz) in ((-4, -4), (4, -4), (-4, 4), (4, 4)):
		b.set(PX + dx, FEET - 1, PZ + dz, "minecraft:ochre_froglight[axis=y]")
	b.set(PX, FEET - 1, PZ, "minecraft:oxidized_chiseled_copper")
	# four great pillars (2 x 2) with bases and capitals, carrying a frame of jungle-wood beams
	for px in (PX - 7, PX + 6):
		for pz in (PZ - 7, PZ + 6):
			for x in (px, px + 1):
				for z in (pz, pz + 1):
					for y in range(FEET, G + 6):
						s = tuff.full(x, y, z)
						if y == FEET + 5:
							s = "minecraft:chiseled_tuff_bricks"
						elif y == FEET + 6:
							s = red.full(x, y, z)
						elif y == FEET + 2 and (x + z) % 2 == 0:
							s = "minecraft:oxidized_chiseled_copper"
						b.set(x, y, z, s)
			for x in range(px - 1, px + 3):
				for z in range(pz - 1, pz + 3):
					if (x in (px - 1, px + 2)) == (z in (pz - 1, pz + 2)):
						continue
					f = "east" if x == px - 1 else "west" if x == px + 2 else "south" if z == pz - 1 else "north"
					b.set(x, FEET, z, ptuff.stairs(x, FEET, z, f, "bottom"))
					if b.is_air(x, HALL_WALL - 1, z):
						b.set(x, HALL_WALL - 1, z, ptuff.stairs(x, HALL_WALL - 1, z, f, "top"))
	for z in (PZ - 7, PZ + 7):
		for x in range(PX - 5, PX + 6):
			b.set(x, HALL_WALL, z, "minecraft:stripped_jungle_log[axis=x]")
	for x in (PX - 7, PX + 7):
		for z in range(PZ - 5, PZ + 6):
			b.set(x, HALL_WALL, z, "minecraft:stripped_jungle_log[axis=z]")
	for (x, z) in ((PX - 3, PZ - 7), (PX + 3, PZ - 7), (PX - 7, PZ - 1), (PX + 7, PZ + 1), (PX - 3, PZ + 7), (PX + 3, PZ + 7)):
		deco.hanging_lantern(b, x, HALL_WALL - 2, z, 1)
	for (x, z) in ((PX, PZ - 3), (PX, PZ + 3), (PX - 3, PZ), (PX + 3, PZ)):
		for y in range(HALL_WALL + 1, G + 6):
			b.set(x, y, z, "minecraft:oxidized_copper_chain[axis=y,waterlogged=false]")
		b.set(x, HALL_WALL, z, "minecraft:oxidized_copper_lantern[hanging=true,waterlogged=false]")
	# the jade mask of the Feathered Serpent on the south wall, between the twin doors, and the altar below it
	wz = PZ + HALL + 1
	rows = [
		".JJJ.",
		"JCCCJ",
		"SENES",
		"R.n.R",
		".MMM.",
		".F.F.",
	]
	for i, row in enumerate(rows):
		y = FEET + 7 - i
		for j, ch in enumerate(row):
			x = PX - 2 + j
			if ch in ("n", "F"):
				b.set(x, y, wz - 1, ptuff.stairs(x, y, wz - 1, "south", "top"))
				continue
			s = {
				"J": "minecraft:oxidized_chiseled_copper",
				"C": "minecraft:chiseled_tuff_bricks",
				"S": "minecraft:polished_tuff",
				"E": "minecraft:verdant_froglight[axis=y]",
				"N": "minecraft:chiseled_tuff",
				"R": "minecraft:oxidized_cut_copper",
				"M": "minecraft:red_terracotta",
			}.get(ch)
			if s is not None:
				b.set(x, y, wz, s)
	for x in range(PX - 2, PX + 3):
		b.set(x, FEET, PZ + HALL, "minecraft:polished_tuff" if abs(x - PX) <= 1 else
			ptuff.stairs(x, FEET, PZ + HALL, "east" if x < PX else "west", "bottom"))
		b.set(x, FEET, PZ + HALL - 1, ptuff.stairs(x, FEET, PZ + HALL - 1, "south", "bottom"))
	deco.chest(b, PX, FEET + 1, PZ + HALL, "north", "treasure")
	deco.candles(b, PX - 1, FEET + 1, PZ + HALL, 4, color="red")
	deco.candles(b, PX + 1, FEET + 1, PZ + HALL, 3, color="green")
	# east door toward the cenote tunnel
	for z in range(PZ - 1, PZ + 2):
		for y in range(FEET, FEET + 3):
			for x in (PX + HALL + 1, PX + HALL + 2):
				b.set(x, y, z, AIR)
	# roots of the jungle broke through the vault in the north-west corner; stones fell under them
	for (x, y, z) in ((PX - 9, HALL_WALL + 1, PZ - 9), (PX - 8, HALL_WALL + 2, PZ - 9), (PX - 9, HALL_WALL + 2, PZ - 8),
		(PX - 10, HALL_WALL, PZ - 9), (PX - 9, HALL_WALL, PZ - 10), (PX - 10, HALL_WALL - 1, PZ - 10)):
		b.set(x, y, z, "minecraft:jungle_wood[axis=y]" if (x + y + z) % 2 else "minecraft:rooted_dirt")
	for (x, y, z) in ((PX - 9, HALL_WALL, PZ - 9), (PX - 8, HALL_WALL + 1, PZ - 9), (PX - 10, HALL_WALL - 2, PZ - 10),
		(PX - 9, HALL_WALL - 1, PZ - 10)):
		b.set(x, y, z, "minecraft:hanging_roots")
	for (x, z, s) in ((PX - 10, PZ - 10, "minecraft:mossy_cobblestone"), (PX - 9, PZ - 10, "minecraft:cobblestone_slab[type=bottom]"),
		(PX - 10, PZ - 9, "minecraft:tuff_brick_slab[type=bottom]"), (PX - 8, PZ - 10, "minecraft:rooted_dirt")):
		b.set(x, FEET, z, s)
	# markers: the guardian in the middle, its escort between the pillars
	deco.marker(b, PX, FEET, PZ, deco.BOSS)
	for (x, z) in ((PX - 4, PZ - 8), (PX + 4, PZ - 8), (PX - 8, PZ - 3), (PX + 8, PZ + 3), (PX - 4, PZ + 4), (PX + 4, PZ + 4)):
		deco.marker(b, x, FEET, z, deco.MINION)
	b.clear_zone(PX - HALL, FEET, PZ - HALL, PX + HALL, FEET + 3, PZ + HALL - 2)
	b.protect(PX - HALL - 2, FEET - 2, PZ - HALL - 2, PX + HALL + 2, G + 8, PZ + HALL + 2)


# --------------------------------------------------------------------------------------------- the cenote

def cenote_radius(y, x, z):
	"""Radius of the sinkhole at layer y (bell shaped: wider below), with a lobed outline."""
	base = 5.0 + 0.11 * max(0, G - y)
	a = math.atan2(z - CEN[1], x - CEN[0])
	return base + 0.45 * math.sin(3 * a + 0.7) + 0.3 * math.sin(5 * a + 2.0)


def cenote(b, m):
	rock, soil = m["rock"], m["soil"]
	cx, cz = CEN
	bottom = WATER - 2
	# a path cut into the wall: from the waterline (south-west) past the tunnel mouth (west, at the hall level)
	# around the north and east sides up to the south rim
	path = {}
	t0 = -(FEET - (WATER + 1)) / float(G + 1 - FEET)
	n = 60
	for i in range(n + 1):
		t = t0 + (1.0 - t0) * i / n
		a = math.pi + t * 1.5 * math.pi
		level = FEET + int(round(t * (G + 1 - FEET)))
		for rr in (0.7, 1.6):
			px, pz = cx + math.cos(a) * 6.5, cz + math.sin(a) * 6.5
			r = cenote_radius(level, px, pz) + rr
			x = int(round(cx + math.cos(a) * r))
			z = int(round(cz + math.sin(a) * r))
			if (x, z) not in path or path[(x, z)] > level:
				path[(x, z)] = level
	b.cenote_path = path
	for x in range(int(cx) - 11, int(cx) + 12):
		for z in range(int(cz) - 11, int(cz) + 12):
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
	# rim: a broken kerb of carved stones around the mouth
	for x in range(int(cx) - 9, int(cx) + 10):
		for z in range(int(cz) - 9, int(cz) + 10):
			dd = dist(x, z, CEN)
			r = cenote_radius(G, x, z)
			if r + 0.4 < dd <= r + 1.4 and (x, z) not in path and b.hrand(x, 0, z, 71) < 0.55 and 0 <= x < SX:
				b.set(x, G + 1, z, m["body"].slab(x, G + 1, z, "bottom") if b.hrand(x, 1, z, 72) < 0.5 else
					m["body"].full(x, G + 1, z))
	# an offering alcove in the wall half way up (north side): a barrel, candles
	best = min(path.items(), key=lambda kv: abs(math.atan2(kv[0][1] - cz, kv[0][0] - cx) + math.pi / 2) +
		abs(kv[1] - (G - 3)) * 0.3)
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
	# the tunnel from the hall's east door to the cenote
	tx0 = PX + HALL + 3
	for x in range(tx0, int(cx)):
		if dist(x, PZ, CEN) <= cenote_radius(FEET, x, PZ):
			break
		for z in range(PZ - 2, PZ + 3):
			for y in range(FEET - 2, FEET + 5):
				edge = abs(z - PZ) == 2 or y in (FEET - 2, FEET + 4)
				if edge:
					if not b.is_air(x, y, z):
						b.set(x, y, z, rock.full(x, y, z) if x > PX + BASE else m["tuff"].full(x, y, z))
				elif y == FEET - 1:
					b.set(x, y, z, "minecraft:water" if z == PZ else m["hfloor"].full(x, y, z))
				else:
					b.set(x, y, z, AIR)
		b.set(x, FEET + 3, PZ - 1, m["ptuff"].stairs(x, FEET + 3, PZ - 1, "north", "top"))
		b.set(x, FEET + 3, PZ + 1, m["ptuff"].stairs(x, FEET + 3, PZ + 1, "south", "top"))
		b.set(x, FEET - 2, PZ, "minecraft:polished_tuff")
		b.clear_zone(x, FEET, PZ - 1, x, FEET + 1, PZ + 1)
	for x in range(PX + HALL - 2, PX + HALL + 3):
		b.set(x, FEET - 1, PZ, "minecraft:water")
		b.set(x, FEET - 2, PZ, "minecraft:polished_tuff")
	b.protect(int(cx) - 10, bottom - 1, int(cz) - 10, int(cx) + 10, G + 1, int(cz) + 10)


# ----------------------------------------------------------------------------------------------- the plaza

def plaza(b, m):
	pave, lime = m["pave"], m["lime"]
	n = b.noise
	for x in range(2, SX - 2):
		for z in range(PZ + BASE + 3, SZ - 1):
			if b.get(x, G, z) is not None:
				continue
			edge = min(x - 2, SX - 3 - x, SZ - 2 - z)
			v = n.fbm2(x * 0.13 + 4, z * 0.13 - 2, 3) + 0.1 * edge - 0.3
			if v > 0 and b.hrand(x, G, z, 61) < 0.9:
				b.set(x, G, z, pave)
	# the sacbe: a raised white causeway from the foot of the stairway to the south edge
	for z in range(STAIR_FOOT + 1, SZ):
		for x in range(PX - 2, PX + 3):
			b.set(x, G, z, m["core"].full(x, G, z))
			if abs(x - PX) <= 1:
				b.set(x, G + 1, z, lime.full(x, G + 1, z))
			else:
				b.set(x, G + 1, z, lime.stairs(x, G + 1, z, "east" if x > PX else "west", "bottom"))
			b.clear_zone(x, G + 2, z, x, G + 3, z)
	deco.marker(b, PX, G + 2, STAIR_FOOT + 5, deco.ENTRANCE)
	for (x, z, fallen) in ((PX - 9, STAIR_FOOT + 3, False), (PX + 9, STAIR_FOOT + 3, False),
		(PX - 9, STAIR_FOOT + 7, True), (PX + 9, STAIR_FOOT + 7, False)):
		stele(b, m, x, z, fallen)
	altar_platform(b, m, PX - 18, PZ + BASE + 10)
	colonnade(b, m)


def colonnade(b, m):
	"""The hall of columns east of the stairway: a low stylobate, two rows of columns, most of them broken."""
	body, apron, talud = m["body"], m["apron"], m["talud"]
	x0, z0, x1, z1 = PX + 9, PZ + BASE + 4, PX + 24, PZ + BASE + 12
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
	n = 0
	for z in (z0 + 2, z1 - 2):
		for x in range(x0 + 2, x1 - 1, 3):
			n += 1
			r = b.hrand(x, 0, z, 77)
			height = 6 if r < 0.3 else (1 + int(r * 5))
			for y in range(G + 2, G + 2 + height):
				s = "minecraft:chiseled_stone_bricks" if (y - G) % 3 == 0 else (
					"minecraft:polished_andesite" if (y + x) % 2 else body.full(x, y, z))
				b.set(x, y, z, s)
			if height == 6:
				top = G + 7
				b.set(x, top, z, "minecraft:chiseled_tuff_bricks")
				for d, (dx, dz) in DIRS.items():
					b.set(x + dx, top, z + dz, apron.stairs(x + dx, top, z + dz, OPP[d], "top"))
				b.set(x, top + 1, z, apron.slab(x, top + 1, z, "bottom"))
			elif b.hrand(x, 1, z, 78) < 0.7:
				# the fallen drums lie beside the stump
				d = ("north", "south", "east", "west")[int(b.hrand(x, 2, z, 79) * 4)]
				dx, dz = DIRS[d]
				for k in range(2, 2 + max(1, 5 - height)):
					fx, fz = x + dx * k, z + dz * k
					if b.empty(fx, G + 2, fz) and b.get(fx, G + 1, fz) is not None:
						b.set(fx, G + 2, fz, "minecraft:polished_andesite" if k % 2 else "minecraft:chiseled_stone_bricks")
	b.protect(x0, G, z0, x1, G + 8, z1)


def stele(b, m, x, z, fallen):
	"""A carved stone slab (1 x 2 x 6) facing the causeway, its round altar in front."""
	lime = m["lime"]
	fx = 1 if x < PX else -1
	if fallen:
		for i in range(6):
			xx = x - fx * i
			for zz in (z, z + 1):
				b.set(xx, G + 1, zz, lime.full(xx, G + 1, zz) if i % 2 else "minecraft:chiseled_stone_bricks")
		b.set(x - fx * 6, G + 1, z + 1, lime.slab(x - fx * 6, G + 1, z + 1, "bottom"))
		b.set(x, G, z, "minecraft:polished_andesite")
		b.set(x + fx, G + 1, z, "minecraft:smooth_stone")
		return
	for zz in (z, z + 1):
		b.set(x, G, zz, "minecraft:polished_andesite")
		for y in range(G + 1, G + 7):
			s = lime.full(x, y, zz)
			if y in (G + 2, G + 5):
				s = "minecraft:chiseled_stone_bricks"
			if y == G + 4:
				s = "minecraft:chiseled_tuff" if zz == z else "minecraft:red_terracotta"
			b.set(x, y, zz, s)
		b.set(x, G + 7, zz, lime.stairs(x, G + 7, zz, "north" if zz == z else "south", "bottom"))
	b.set(x + fx, G + 1, z, "minecraft:smooth_stone")
	b.set(x + fx, G + 1, z + 1, "minecraft:smooth_stone")
	b.set(x + fx, G + 2, z, "minecraft:smooth_stone_slab[type=bottom]")
	b.set(x + fx, G + 2, z + 1, "minecraft:smooth_stone_slab[type=bottom]")
	deco.candles(b, x + fx, G + 3, z, 2, color="red")
	pot(b, x + fx, G + 3, z + 1, cracked=b.hrand(x, 0, z, 5) < 0.5, facing="east" if fx > 0 else "west")
	b.protect(x, G, z, x + fx, G + 7, z + 1)


def altar_platform(b, m, cx, cz):
	"""Low square platform with a stair on each side and a sacrificial altar on top (Venus platform)."""
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
				b.set(x, G + 2, z, panel_block(b, m, x, G + 2, z, 1, 2) if d == h - 1 and abs(along(x, z, cx, cz)) % 2
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
	b.set(cx, G + 4, cz, "minecraft:polished_tuff_slab[type=bottom]")
	deco.skull(b, cx + 2, G + 3, cz + 2, "skeleton_skull")
	deco.candles(b, cx - 2, G + 3, cz - 2, 3, color="red")
	deco.candles(b, cx + 2, G + 3, cz - 2, 2, color="green")
	deco.chest(b, cx - 2, G + 3, cz + 2, "east", "common", kind="barrel")
	b.protect(cx - h, G, cz - h, cx + h, G + 4, cz + h)


# ------------------------------------------------------------------------------------------------- decay

def unprotect(b, fn):
	b.protected = {p for p in b.protected if not fn(*p)}


def ruin_it(b, m):
	n = b.noise
	# the ceiba's roots tore the north-east corner of the pyramid apart: a slump down three terraces
	ne = (PX + 15, G + 9, PZ - 15)

	def in_slump(x, y, z):
		return ((x - ne[0]) / 7.0) ** 2 + ((y - ne[1]) / 9.0) ** 2 + ((z - ne[2]) / 7.0) ** 2 < 1.0 + 0.3 * n.n3(
			x * 0.3, y * 0.3, z * 0.3)
	unprotect(b, in_slump)
	decay.damage(b, lambda x, y, z, s: in_slump(x, y, z) and ring(x, z) >= (half_at(y) or 0) - 4)
	# the south-east corner terraces slid, an old breach in the east face
	se = (PX + 16, G + 6, PZ + 15)
	decay.breach(b, *se, 3.5, 4.5, 3.5, roughness=0.4)
	decay.breach(b, PX + 17, G + 13, PZ - 4, 2.5, 3.5, 3.0, roughness=0.4)
	decay.breach(b, PX - 12, G + 18, PZ + 10, 2.2, 3.0, 2.2)
	# the roof comb lost its eastern shoulder, the portico's east vault caved in
	comb = TOPY + 11
	decay.damage(b, lambda x, y, z, s: z <= SANCT[1] + 3 and x > PX + 1 and y > comb + 9 + 2 * n.n3(x * 0.4, y * 0.4, z * 0.4) -
		(x - PX) * 0.7)
	unprotect(b, lambda x, y, z: x > PX + 2 and y > TOPY and z > PZ + 1)
	decay.breach(b, PX + 6, TOPY + 9, PZ + 4, 3.0, 3.5, 2.6, roughness=0.3)
	# missing steps and a broken balustrade on the stairway
	unprotect(b, lambda x, y, z: PX - 3 <= x <= PX + 3 and PZ + 18 <= z <= PZ + 24)
	decay.breach(b, PX - 2, step_y(PZ + 21) + 1, PZ + 21, 1.8, 1.4, 2.2, roughness=0.5)
	decay.breach(b, PX + 5, step_y(PZ + 13) + 2, PZ + 13, 1.4, 2.2, 2.5, roughness=0.3)
	# general wear: aprons and talud crumble, upper courses lose more
	masonry = lambda s: any(k in s for k in ("brick", "cobble", "andesite", "stone", "tuff"))
	decay.erode(b, 0.2, top_bias=0.9, y0=G + 2, y1=TOPY, match=masonry)
	decay.erode(b, 0.3, top_bias=0.6, y0=TOPY + 1, match=masonry)
	decay.stabilize(b)
	decay.cleanup(b)

	def source(state):
		if "copper" in state:
			return "minecraft:oxidized_cut_copper_slab[type=bottom]" if b.rng.random() < 0.25 else False
		if "log" in state or "terracotta" in state:
			return False
		return None
	decay.rubble(b, m["rubble"], keep=0.4, scatter=2.0, top=m["body"], max_height=4, source=source)
	decay.cleanup(b)


# ------------------------------------------------------------------------------------------------ the jungle

def leaves(state="minecraft:jungle_leaves"):
	return state + "[distance=1,persistent=true,waterlogged=false]"


def blob(b, cx, cy, cz, rx, ry, rz, state, salt=0, only_empty=True):
	"""Noisy ellipsoid of leaves (only in empty cells)."""
	for x in range(int(cx - rx - 1), int(cx + rx + 2)):
		for y in range(int(cy - ry - 1), int(cy + ry + 2)):
			for z in range(int(cz - rz - 1), int(cz + rz + 2)):
				d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2
				if d > 1.0 + 0.35 * (b.hrand(x, y, z, salt) - 0.5):
					continue
				if only_empty and not b.empty(x, y, z):
					continue
				if not b.t.inside(x, y, z):
					continue
				b.set(x, y, z, state)


def log_line(b, a, c, log="minecraft:jungle_log"):
	"""Line of logs from a to c (3D, axis from the dominant direction), only through empty / leaf cells."""
	(x0, y0, z0), (x1, y1, z1) = a, c
	dx, dy, dz = x1 - x0, y1 - y0, z1 - z0
	nsteps = max(abs(dx), abs(dy), abs(dz), 1)
	axis = "x" if abs(dx) >= max(abs(dy), abs(dz)) else ("z" if abs(dz) >= abs(dy) else "y")
	for i in range(nsteps + 1):
		x = int(round(x0 + dx * i / nsteps))
		y = int(round(y0 + dy * i / nsteps))
		z = int(round(z0 + dz * i / nsteps))
		s = b.get(x, y, z)
		if s is None or s.startswith(AIR) or "leaves" in s or not K.is_structural(s):
			b.set(x, y, z, f"{log}[axis={axis}]")


def ceiba(b, m):
	"""The giant ceiba of the north-west corner: buttress roots, a tall trunk, an umbrella crown."""
	tx, tz = CEIBA
	top = G + 27
	cxf, czf = tx + 0.5, tz + 0.5
	for x in (tx, tx + 1):
		for z in (tz, tz + 1):
			b.set(x, G, z, "minecraft:rooted_dirt")
			for y in range(G + 1, top + 1):
				b.set(x, y, z, "minecraft:jungle_log[axis=y]")
	# buttress roots: fins of wood radiating from the trunk, lower and lower; long surface roots beyond
	for i, ang in enumerate((0.3, 1.1, 1.75, 2.6, 3.4, 4.3, 5.1, 5.8)):
		length = 4 + (i % 3)
		for k in range(1, length + 1):
			x = int(round(cxf + math.cos(ang) * (k + 0.6)))
			z = int(round(czf + math.sin(ang) * (k + 0.6)))
			hgt = max(0, int((length - k) * 1.3))
			for y in range(G + 1, G + 1 + hgt):
				if b.empty(x, y, z):
					b.set(x, y, z, "minecraft:jungle_wood[axis=y]")
			if b.get(x, G, z) is None:
				b.set(x, G, z, "minecraft:rooted_dirt")
	# roots crawling over the basal platform and up the broken terraces of the north-east corner
	for (a, c) in (((tx - 1, G + 1, tz + 1), (tx - 7, G + 1, tz + 3)), ((tx - 7, G + 1, tz + 3), (tx - 10, G + 5, tz + 6)),
		((tx - 1, G + 1, tz), (tx - 9, G + 2, tz - 1)), ((tx, G + 1, tz + 2), (tx - 3, G + 1, tz + 10)),
		((tx - 3, G + 1, tz + 10), (tx - 7, G + 3, tz + 12)), ((tx + 2, G + 1, tz + 1), (tx + 6, G + 1, tz + 4))):
		log_line(b, a, c, "minecraft:jungle_wood")
	# branches and crown
	branches = [((tx, top - 3, tz + 1), (tx - 8, top + 1, tz + 3)), ((tx + 1, top - 2, tz + 1), (tx + 5, top + 1, tz + 7)),
		((tx, top - 4, tz + 1), (tx - 5, top, tz + 9)), ((tx + 1, top - 1, tz), (tx + 3, top + 3, tz - 2)),
		((tx, top - 6, tz + 1), (tx - 11, top - 3, tz + 8))]
	for a, c in branches:
		log_line(b, a, c)
		blob(b, c[0], c[1] + 1, c[2], 4.0, 1.4, 4.0, leaves(), salt=c[0] * 7 + c[2])
		mx, mz = (c[0] * 2 + tx) / 3.0, (c[2] * 2 + tz) / 3.0
		blob(b, mx, c[1] + 2.2, mz, 2.6, 1.2, 2.6, leaves(), salt=c[0] * 11 + c[2])
	blob(b, tx + 1, top + 2, tz + 2, 4.6, 1.8, 4.6, leaves(), salt=3)
	# leaves drooping under the edges of the crown
	for (x, y, z), s in list(b.t.cells.items()):
		if "jungle_leaves" in s and y > G + 15 and b.empty(x, y - 1, z) and b.hrand(x, y, z, 93) < 0.22:
			b.set(x, y - 1, z, s)
	# cocoa pods on the trunk
	for (x, y, z, f) in ((tx - 1, G + 6, tz, "east"), (tx + 2, G + 9, tz + 1, "west"), (tx, G + 12, tz + 2, "north"),
		(tx + 1, G + 7, tz - 1, "south"), (tx + 2, G + 14, tz, "west")):
		if b.t.inside(x, y, z) and b.empty(x, y, z):
			b.set(x, y, z, f"minecraft:cocoa[age={1 + (y % 2)},facing={f}]")
	# hanging vines from the crown
	for (x, y, z), s in list(b.t.cells.items()):
		if "jungle_leaves" in s and y > G + 18 and b.hrand(x, y, z, 91) < 0.18:
			for d, (dx, dz) in DIRS.items():
				vx, vz = x + dx, z + dz
				if b.empty(vx, y, vz) and b.t.inside(vx, y, vz):
					length = 2 + int(b.hrand(x, y, z, 92) * 7)
					for k in range(length):
						if not b.empty(vx, y - k, vz) or y - k <= G:
							break
						b.set(vx, y - k, vz, B.fmt("minecraft:vine", {OPP[d]: "true"}))
					break


def fallen_tree(b, m):
	"""A giant tree fell across the west of the plaza: upturned root plate, trunk, crushed crown."""
	z0 = STAIR_FOOT + 5
	xa, xb = 4, 17
	for x in range(xa, xb + 1):
		for z in (z0, z0 + 1):
			for y in (G + 1, G + 2):
				b.set(x, y, z, "minecraft:jungle_log[axis=x]")
			if b.get(x, G, z) is None:
				b.set(x, G, z, "minecraft:coarse_dirt")
	# the root plate standing on edge at the west end
	for z in range(z0 - 3, z0 + 5):
		for y in range(G + 1, G + 7):
			d = math.hypot((z - z0 - 0.5) / 3.8, (y - G - 2.5) / 3.2)
			if d < 1.0:
				r = b.hrand(xa - 1, y, z, 41)
				b.set(xa - 1, y, z, "minecraft:jungle_wood[axis=x]" if d < 0.45 else
					("minecraft:rooted_dirt" if r < 0.45 else "minecraft:jungle_wood[axis=z]" if r < 0.7 else "minecraft:dirt"))
	for z in range(z0 - 2, z0 + 4):
		for y in range(G + 1, G + 5):
			if b.hrand(xa - 2, y, z, 42) < 0.35 and b.empty(xa - 2, y, z) and b.get(xa - 1, y, z) is not None:
				b.set(xa - 2, y, z, "minecraft:hanging_roots" if b.get(xa - 2, y + 1, z) else "minecraft:rooted_dirt")
	# branch stubs and the crown lying on the plaza
	log_line(b, (10, G + 3, z0), (11, G + 5, z0 - 1))
	log_line(b, (14, G + 3, z0 + 1), (16, G + 4, z0 + 4))
	blob(b, 20, G + 2, z0 + 1, 3.5, 2.0, 3.8, leaves(), salt=13)
	blob(b, 16, G + 4, z0 + 4, 2.2, 1.4, 2.2, leaves(), salt=14)
	for x in range(xa, xb + 1, 3):
		for z, f in ((z0 - 1, "south"), (z0 + 2, "north")):
			if b.hrand(x, 0, z, 43) < 0.4 and b.empty(x, G + 1, z):
				b.set(x, G + 1, z, f"minecraft:cocoa[age=2,facing={f}]")
		if b.hrand(x, 1, z0, 44) < 0.5:
			b.set(x, G + 3, z0, "minecraft:moss_carpet")
		if b.hrand(x, 2, z0, 45) < 0.3:
			b.set(x + 1, G + 3, z0 + 1, "minecraft:brown_mushroom" if x % 2 else "minecraft:red_mushroom")


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
			h = 5 + int(b.hrand(x, 1, z, salt) * 9)
			b.set(x, G, z, "minecraft:podzol" if b.get(x, G, z) is None else b.get(x, G, z))
			for k in range(h):
				y = G + 1 + k
				if not b.empty(x, y, z):
					break
				lv = "large" if k >= h - 2 else ("small" if k == h - 3 else "none")
				b.set(x, y, z, f"minecraft:bamboo[age=1,leaves={lv},stage=0]")


def jungle_bushes(b, count=26):
	"""Bushes of jungle leaves around a stub of log on the natural ground at the edges of the site."""
	rnd = b.rng
	placed = 0
	tries = 0
	while placed < count and tries < 600:
		tries += 1
		x, z = rnd.randrange(1, SX - 1), rnd.randrange(1, SZ - 1)
		if ring(x, z, SX // 2, SZ // 2) < 24 and rnd.random() < 0.8:
			continue
		if any(b.get(x + dx, G, z + dz) is not None for dx in (-2, 0, 2) for dz in (-2, 0, 2)):
			continue
		if not all(b.empty(x, y, z) for y in range(G + 1, G + 4)):
			continue
		b.set(x, G + 1, z, "minecraft:jungle_log[axis=y]")
		blob(b, x, G + 1.5, z, 1.1 + 0.8 * rnd.random(), 1.1, 1.1 + 0.8 * rnd.random(), leaves(), salt=x * 31 + z)
		placed += 1


def cenote_vines(b, density=0.35):
	"""Long vines hanging from the rim of the cenote down its walls (they go on hanging where the wall recedes)."""
	cx, cz = CEN
	for x in range(int(cx) - 10, int(cx) + 11):
		for z in range(int(cz) - 10, int(cz) + 11):
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


def double_plants(b, region, density, salt):
	"""Large ferns (two blocks) on soil, moss or old paving under the open sky."""
	(x0, y0, z0), (x1, y1, z1) = region
	for (x, y, z), s in list(b.t.cells.items()):
		if not (x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1):
			continue
		if not any(k in s for k in ("moss_block", "grass", "dirt", "podzol", "mossy_cobble")):
			continue
		if not (b.empty(x, y + 1, z) and b.empty(x, y + 2, z)) or (x, y + 1, z) in b.keep_clear:
			continue
		if b.hrand(x, y, z, salt) > density:
			continue
		b.set(x, y + 1, z, "minecraft:large_fern[half=lower]")
		b.set(x, y + 2, z, "minecraft:large_fern[half=upper]")


def life(b, m):
	ceiba(b, m)
	fallen_tree(b, m)
	everything = ((0, G + 1, 0), (SX - 1, SY - 1, SZ - 1))
	veg.replace_surface(b, {"minecraft:stone_bricks": "minecraft:moss_block", "minecraft:mossy_stone_bricks": "minecraft:moss_block",
		"minecraft:cobblestone": "minecraft:mossy_cobblestone", "minecraft:polished_andesite": "minecraft:mossy_stone_bricks"},
		density=0.3, region=((0, G, 0), (SX - 1, TOPY, SZ - 1)))
	veg.vines(b, density=0.34, max_len=8, region=((0, G + 2, 0), (SX - 1, TOPY, SZ - 1)))
	veg.vines(b, density=0.3, max_len=4, region=((0, TOPY + 1, 0), (SX - 1, TOPY + 11, PZ + 2)))
	# the jungle on the ledges and the plaza: moss, ferns, bushes of leaves
	veg.floor_cover(b, [("minecraft:moss_carpet", 4), ("minecraft:fern", 4), ("minecraft:short_grass", 2),
		(leaves(), 3), (leaves("minecraft:azalea_leaves"), 1), ("minecraft:bush", 2)], density=0.3,
		region=everything, sky=True, need=lambda s: any(k in s for k in ("moss", "cobble", "brick", "dirt", "andesite",
			"podzol")))
	bamboo(b, ((0, 0), (11, 9)), 0.4, 81)
	bamboo(b, ((55, 46), (63, 63)), 0.25, 82)
	bamboo(b, ((0, 36), (6, 50)), 0.3, 83)
	jungle_bushes(b)
	# around the cenote: vines down its walls, hanging roots, glow berries, lily pads, dripleaves
	cx, cz = CEN
	cenote_vines(b)
	cen = ((int(cx) - 10, WATER, int(cz) - 10), (int(cx) + 10, G, int(cz) + 10))
	veg.hanging(b, "minecraft:hanging_roots", density=0.12, region=cen, salt=33)
	veg.hanging(b, "minecraft:cave_vines[age=25,berries=true]", density=0.08, region=cen, max_len=4,
		plant="minecraft:cave_vines_plant[berries=false]", salt=34)
	veg.lichen(b, density=0.06, region=cen)
	for (x, y, z), s in list(b.t.cells.items()):
		if s.startswith("minecraft:water") and y == WATER and b.empty(x, y + 1, z) and dist(x, z, CEN) < 9:
			r = b.hrand(x, y, z, 51)
			if r < 0.12:
				b.set(x, y + 1, z, "minecraft:lily_pad")
			elif r < 0.16 and b.get(x, y - 1, z) == "minecraft:water":
				b.set(x, y - 1, z, "minecraft:big_dripleaf_stem[facing=north,waterlogged=true]")
				b.set(x, y, z, "minecraft:big_dripleaf_stem[facing=north,waterlogged=true]")
				b.set(x, y + 1, z, "minecraft:big_dripleaf[facing=north,tilt=none,waterlogged=false]")
	# inside: cobwebs in the passages, roots and moss creeping into the hall
	inside = ((PX - 14, FEET, PZ - 12), (PX + 14, TOPY, PZ + 30))
	veg.cobwebs(b, density=0.12, region=((PX - 8, G + 1, PZ - 4), (PX + 8, TOPY, LANDING + 7)))
	veg.hanging(b, "minecraft:hanging_roots", density=0.05, region=((PX - HALL, FEET, PZ - HALL), (PX + HALL, G + 6, PZ + HALL)))
	veg.lichen(b, density=0.03, region=inside)
	veg.floor_cover(b, [("minecraft:moss_carpet", 1)], density=0.18, region=((PX + 2, FEET, PZ - 6), (PX + HALL, FEET, PZ + 6)),
		sky=False)
	decay.cleanup(b)
	double_plants(b, ((0, G, 0), (SX - 1, TOPY, SZ - 1)), 0.05, 61)
	prune(b, lambda s: any(k in s for k in ("leaves", "_log", "_wood", "rooted_dirt")))
	decay.cleanup(b)


def prune(b, match):
	"""Removes the matching cells (leaves, wood) that do not hold to the ground through structural blocks."""
	from collections import deque
	cells = {p for p, s in b.t.cells.items() if K.is_structural(s) and not s.startswith(AIR)}
	seen = set()
	queue = deque()
	for (x, y, z) in cells:
		if y <= b.ground or (b.t.get(x, y - 1, z) is None and y - 1 <= b.ground):
			seen.add((x, y, z))
			queue.append((x, y, z))
	while queue:
		x, y, z = queue.popleft()
		for q in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
			if q in cells and q not in seen:
				seen.add(q)
				queue.append(q)
	for p in cells - seen:
		if match(b.t.get(*p)):
			b.t.set(*p, AIR)


if __name__ == "__main__":
	import render
	print(render.render_ruin(build()))
