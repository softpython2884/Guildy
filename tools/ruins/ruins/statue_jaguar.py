"""
Statue du jaguar de pierre (monument, Overworld). The trail that leads to the Grand Temple du Jaguar céleste: the mod
places a line of these statues between a guild's atelier and the temple, each one turned toward the next.

A seated jaguar carved in tuff on a Mayan plinth: a ragged apron of old paving with a short paved way in front, a base
course with a step all around, a frieze of glyph panels (chiseled tuff, jade inlays, red paint) with a jade mask in the
middle of each face under an overhanging cornice, then the jaguar itself: forepaws on the edge of the plinth, straight
front legs, a deep chest under a jade collar, folded haunches, the tail curled around its right side, a broad head
pushed forward over the paws with a jutting muzzle, an open red mouth with white fangs, jade eyes that glow (verdant
froglights) deep in their sockets under heavy brows, short ears. Traces of ochre paint and black rosettes on the pelt,
moss on the haunches, vines hanging from the cornice and the head, ferns on the paving, two offering stones with candles
before the step; a broken ear, cracked and missing stones.

Facing north (-z) in the template; layer 0 is a buried foundation, layer 1 (GROUND) the paving at the level of the
natural ground. 15 x 15 x 15: a plinth 4 blocks high and the jaguar 9 blocks high, the tips of its ears 13 blocks above
the ground.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from ruinlib import build as K, decay, veg, shapes  # noqa: E402
from ruinlib import blocks as B  # noqa: E402
from ruinlib.build import Mat, Mix, AIR  # noqa: E402
from ruinlib.ruin import Monument  # noqa: E402

ID = "statue_jaguar"
SEED = 6161
SX, SY, SZ = 15, 15, 15
C = 7  # centre column (x and z)
GROUND = 1  # layer of the natural ground (layer 0: buried foundation)
BASE = GROUND + 1  # base course of the plinth (2)
FRIEZE = (BASE + 1, BASE + 2)  # the two layers of glyph panels (3, 4)
CORNICE = BASE + 3  # overhanging cornice, top of the plinth (5)
CAT = CORNICE + 1  # first layer of the jaguar (6)

# Glyph panels of the frieze (two windows of 3 wide x 2 high on each face, top row first), as on the terraces of the
# grand temple: C chiseled tuff bricks, P polished tuff, R red paint, J jade inlay, W worn tuff.
GLYPHS = {
	"north": (("JCJ", "PRP"), ("JCJ", "PRP")),
	"east": (("RCR", "CPC"), ("PJP", "RCR")),
	"south": (("PJP", "RCR"), ("CRC", "RPR")),
	"west": (("CRC", "RPR"), ("RCR", "CPC")),
}

# The jaguar, layer by layer from CAT up: rows from north (z = C - 5) to south (z = C + 4), columns from west
# (x = C - 4) to east (x = C + 4). Legend:
#   B body (tuff, ochre paint and black rosettes added later), K chiseled tuff, J jade (oxidized cut copper),
#   j carved jade (oxidized chiseled copper), E eye (verdant froglight), M mouth (red terracotta), F fang (calcite),
#   Y ochre paint, D black paint (yellow / black terracotta), u white muzzle (polished diorite stairs facing south),
#   o air (eye sockets), stairs ^ v < > (bottom, facing north / south / west / east: the high side is toward that
#   direction), A V [ ] (upside-down, facing north / south / west / east), _ bottom slab, = top slab, . nothing.
CAT_LAYERS = [
	# 6: forepaws (toes forward), wrists, chest, the folded haunches either side; the tail along the back and the east side
	[
		".........",
		"..vv.vv..",
		"..BB.BB..",
		"..BB.BB..",
		".BBBBBBB.",
		".BBBBBBB_",
		".BBBBBBB_",
		".BBBBBBB_",
		"..^BBB^._",
		"...______",
	],
	# 7: front legs (the chest set back between them), haunches
	[
		".........",
		".........",
		"..BB.BB..",
		"..BB.BB..",
		".>BBBBB<.",
		".BBBBBBB.",
		".BBBBBBB.",
		".>BBBBB<.",
		"...^^^...",
		".........",
	],
	# 8: legs, the chest bulging between them with a jade pendant, the haunches rounding off, the back
	[
		".........",
		".........",
		"..BBjBB..",
		"..BBBBB..",
		"..BBBBB..",
		".>BBBBB<.",
		".>BBBBB<.",
		"...^^^...",
		".........",
		".........",
	],
	# 9: the chin, the jade collar, shoulders, the back sloping up to the neck
	[
		".........",
		"...VVV...",
		"..jJjJj..",
		"..JBBBJ..",
		"..BBBBB..",
		"..>BBB<..",
		"...^^^...",
		".........",
		".........",
		".........",
	],
	# 10: the open mouth (red, white fangs) under the muzzle, the rounded jowls, the broad head
	[
		"...FMF...",
		".]YYYYY[.",
		".BBBBBBB.",
		".BBBBBBB.",
		".>BBBBB<.",
		"...^^^...",
		".........",
		".........",
		".........",
		".........",
	],
	# 11: the white muzzle sloping down to the dark nose, the cheeks with their black tear marks, the head
	[
		"...uDu...",
		".YDBBBDY.",
		".BBBBBBB.",
		".BBBBBBB.",
		".BBBBBBB.",
		"...^^^...",
		".........",
		".........",
		".........",
		".........",
	],
	# 12: the jade eyes deep in their black-rimmed sockets, the jade mark on the forehead, the rounded temples
	[
		".........",
		".>oDjDo<.",
		".BEBBBEB.",
		".BBBBBBB.",
		".>BBBBB<.",
		".........",
		".........",
		".........",
		".........",
		".........",
	],
	# 13: heavy brows over the sockets, the crown of the head, the ears
	[
		".........",
		"..A___A..",
		".B_____B.",
		".^_____^.",
		".........",
		".........",
		".........",
		".........",
		".........",
		".........",
	],
	# 14: the tips of the ears, leaning inward
	[
		".........",
		".........",
		".>.....<.",
		".........",
		".........",
		".........",
		".........",
		".........",
		".........",
		".........",
	],
]


def build():
	b = K.new(SX, SY, SZ, SEED, ground=GROUND, name=ID)
	m = materials()
	plinth(b, m)
	jaguar(b, m)
	offerings(b, m)
	paint(b)
	ruin_it(b, m)
	life(b)
	shapes.fix(b.t)
	return Monument(ID, "Statue du jaguar de pierre", b.t, GROUND)


def materials():
	return {
		"found": Mix([("minecraft:cobblestone", 4), ("minecraft:mossy_cobblestone", 3), ("minecraft:tuff", 2),
			("minecraft:stone", 2), ("minecraft:andesite", 1)], seed=SEED),
		"pave": Mix([("minecraft:mossy_stone_bricks", 5), ("minecraft:stone_bricks", 3), ("minecraft:cracked_stone_bricks", 2),
			("minecraft:mossy_cobblestone", 2), ("minecraft:moss_block", 2), ("minecraft:polished_andesite", 1),
			("minecraft:coarse_dirt", 1)], seed=SEED + 1, scale=0.35),
		"base": Mat("stone_brick", mossy="mossy_stone_brick", cracked="minecraft:cracked_stone_bricks", moss=0.55,
			moss_top=0.3, crack=0.15, y_low=BASE, y_high=CORNICE, seed=SEED + 2, scale=0.4),
		"tuff": Mat("tuff_brick", seed=SEED + 3, extra=[("minecraft:polished_tuff", 0.18), ("minecraft:chiseled_tuff_bricks", 0.04)]),
		# the carved body of the jaguar: polished tuff with tuff bricks and plain tuff where the carving wore away
		"body": Mat("polished_tuff", seed=SEED + 5, extra=[("minecraft:tuff", 0.1), ("minecraft:tuff_bricks", 0.08)]),
		"red": Mix([("minecraft:red_terracotta", 6), ("minecraft:terracotta", 1), ("minecraft:granite", 1)], seed=SEED + 7),
	}


def d(x, z):
	"""Chebyshev distance from the centre column."""
	return max(abs(x - C), abs(z - C))


def inward(x, z):
	"""Direction from the cell toward the centre (the dominant axis)."""
	dx, dz = C - x, C - z
	if abs(dx) >= abs(dz):
		return "east" if dx > 0 else "west"
	return "south" if dz > 0 else "north"


# ------------------------------------------------------------------------------------------------- plinth

def plinth(b, m):
	"""
	Foundation (layer 0), the paving at ground level (ragged outer ring, a short paved way to the north), the base course
	(11 x 11) and its step, the frieze (9 x 9, two layers of glyph panels), the cornice (9 x 9 top, a ring of upside-down
	stairs overhanging the frieze).
	"""
	n = b.noise
	for x in range(SX):
		for z in range(SZ):
			r = d(x, z)
			way = abs(x - C) <= 1 and z < C
			if r <= 6 or way or (r == 7 and n.n2(x * 0.45, z * 0.45) + (b.hrand(x, 0, z, 3) - 0.5) * 0.6 > -0.1):
				b.set(x, 0, z, m["found"])
				b.set(x, GROUND, z, m["pave"])
			if r <= 5:
				b.set(x, BASE, z, m["base"])
			elif r == 6:
				b.set(x, BASE, z, m["base"].stairs(x, BASE, z, inward(x, z), "bottom"))
			if r <= 4:
				for y in FRIEZE:
					b.set(x, y, z, m["tuff"] if r <= 3 else frieze_block(b, m, x, y, z))
				b.set(x, CORNICE, z, "minecraft:polished_tuff" if (x + z) % 2 else "minecraft:tuff_bricks")
			elif r == 5:
				b.set(x, CORNICE, z, m["tuff"].stairs(x, CORNICE, z, inward(x, z), "top"))
	# the corners of the plinth top carved like the sun
	for (x, z) in ((C - 4, C - 4), (C + 4, C - 4), (C - 4, C + 4), (C + 4, C + 4)):
		b.set(x, CORNICE, z, "minecraft:chiseled_tuff")
	b.protect(0, BASE, 0, SX - 1, CORNICE, SZ - 1)


def frieze_block(b, m, x, y, z):
	"""A block of the frieze (|d| = 4): corner posts, the jade mask in the middle of each face, two glyph panels."""
	dx, dz = x - C, z - C
	if abs(dx) == 4 and abs(dz) == 4:
		return "minecraft:polished_tuff"
	face = "north" if dz == -4 else "south" if dz == 4 else "west" if dx == -4 else "east"
	a = dx if face in ("north", "south") else dz
	if a == 0:
		return "minecraft:oxidized_chiseled_copper" if y == FRIEZE[1] else "minecraft:chiseled_tuff_bricks"
	glyph = GLYPHS[face][0 if a < 0 else 1]
	ch = glyph[0 if y == FRIEZE[1] else 1][(a + 3) if a < 0 else (a - 1)]
	if b.hrand(x, y, z, 19) < 0.1:
		ch = "W"
	return {
		"C": "minecraft:chiseled_tuff_bricks",
		"P": "minecraft:polished_tuff",
		"R": m["red"].full(x, y, z),
		"J": "minecraft:oxidized_chiseled_copper",
		"W": "minecraft:tuff",
	}[ch]


# ------------------------------------------------------------------------------------------------- jaguar

STAIRS = {"^": ("north", "bottom"), "v": ("south", "bottom"), "<": ("west", "bottom"), ">": ("east", "bottom"),
	"A": ("north", "top"), "V": ("south", "top"), "[": ("west", "top"), "]": ("east", "top")}
FULL = {
	"K": "minecraft:chiseled_tuff",
	"J": "minecraft:oxidized_cut_copper",
	"j": "minecraft:oxidized_chiseled_copper",
	"E": "minecraft:verdant_froglight[axis=y]",
	"M": "minecraft:red_terracotta",
	"F": "minecraft:calcite",
	"Y": "minecraft:yellow_terracotta",
	"D": "minecraft:black_terracotta",
	"u": "minecraft:polished_diorite_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]",
	"o": AIR,
}


def jaguar(b, m):
	body = m["body"]
	for k, rows in enumerate(CAT_LAYERS):
		y = CAT + k
		for row, line in enumerate(rows):
			z = C - 5 + row
			for col, ch in enumerate(line):
				x = C - 4 + col
				if ch == ".":
					continue
				if ch in STAIRS:
					facing, half = STAIRS[ch]
					state = body.stairs(x, y, z, facing, half)
				elif ch == "_":
					state = body.slab(x, y, z, "bottom")
				elif ch == "=":
					state = body.slab(x, y, z, "top")
				elif ch == "B":
					state = body.full(x, y, z)
				else:
					state = FULL[ch]
				b.set(x, y, z, state)
	# the tail ends in a curl on the east side of the plinth top, toward the front, its tip raised
	b.set(C + 4, CAT, C - 1, body.full(C + 4, CAT, C - 1))
	b.set(C + 4, CAT + 1, C - 1, body.stairs(C + 4, CAT + 1, C - 1, "south", "bottom"))
	b.protect(C - 4, CAT, C - 5, C + 4, SY - 1, C + 4)


def offerings(b, m):
	"""Two offering stones with candles either side of the paved way, before the step."""
	for side in (-1, 1):
		x, z = C + 2 * side, C - 7
		b.set(x, 0, z, m["found"])
		b.set(x, GROUND, z, "minecraft:polished_tuff")
		b.set(x, BASE, z, "minecraft:chiseled_tuff")
		b.set(x, BASE + 1, z, "minecraft:green_candle[candles=3,lit=true,waterlogged=false]")
		b.protect(x, 0, z, x, BASE + 1, z)


def paint(b):
	"""Traces of ochre paint on the pelt (exposed body blocks, not the face) and black rosettes on the paint."""
	n = b.noise
	for (x, y, z), s in list(b.t.cells.items()):
		if y < CAT or s.split("[", 1)[0] not in ("minecraft:polished_tuff", "minecraft:tuff", "minecraft:tuff_bricks"):
			continue
		if not (C - 4 <= x <= C + 4 and z >= C - 5):
			continue
		if y >= CAT + 4 and z <= C - 4:
			continue  # the face stays carved stone
		exposed = any(b.empty(x + dx, y + dy, z + dz) for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0)))
		if not exposed:
			continue
		v = 0.5 + 0.5 * n.fbm3(x * 0.33, y * 0.4, z * 0.33, 2) * 1.6 + (b.hrand(x, y, z, 76) - 0.5) * 0.2
		if v < 0.32:
			continue  # the paint wore away: carved stone
		spot = (x * 3 + z * 5 + y * 2) % 6 == 0 or b.hrand(x, y, z, 77) < 0.08
		b.set(x, y, z, "minecraft:black_terracotta" if spot else "minecraft:yellow_terracotta")


# ------------------------------------------------------------------------------------------------- decay, life

def ruin_it(b, m):
	# the tip of the west ear broke off long ago
	b.set(C - 3, CAT + 8, C - 3, AIR)
	b.set(C - 3, CAT + 7, C - 2, m["body"].slab(C - 3, CAT + 7, C - 2, "bottom"))
	# a corner of the cornice fell (south-west), a glyph block is gone, the paving and the step are broken in places
	for pos in ((C - 5, CORNICE, C + 5), (C - 5, CORNICE, C + 4), (C - 4, CORNICE, C + 5), (C + 4, FRIEZE[1], C + 2)):
		b.protected.discard(pos)
		decay.remove(b, pos)
	masonry = lambda s: any(k in s for k in ("brick", "cobble", "andesite", "stone"))
	decay.erode(b, 0.35, top_bias=0.2, y0=GROUND, y1=BASE, match=masonry)
	decay.stabilize(b)
	decay.cleanup(b)
	# the fallen stones lie on the paving
	fallen = ((C - 6, C + 6, "minecraft:mossy_cobblestone"), (C - 7, C + 4, "minecraft:tuff"),
		(C - 6, C + 3, B.fmt("minecraft:tuff_brick_stairs", {"facing": "west", "half": "bottom", "shape": "straight",
			"waterlogged": "false"})), (C + 6, C - 5, "minecraft:polished_tuff_slab[type=bottom,waterlogged=false]"))
	for (x, z, s) in fallen:
		below = b.get(x, GROUND, z)
		if below is not None and not below.startswith(AIR) and b.empty(x, BASE, z):
			b.set(x, BASE, z, s)


def life(b):
	# moss on the haunches, the back and the plinth; moss blocks in the paving
	veg.replace_surface(b, {"minecraft:polished_tuff": "minecraft:moss_block", "minecraft:tuff": "minecraft:moss_block",
		"minecraft:yellow_terracotta": "minecraft:moss_block"}, density=0.22, region=((C - 4, CAT, C - 1), (C + 4, CAT + 3, C + 4)),
		salt=8)
	veg.replace_surface(b, {"minecraft:stone_bricks": "minecraft:moss_block", "minecraft:mossy_stone_bricks": "minecraft:moss_block"},
		density=0.25, region=((0, GROUND, 0), (SX - 1, BASE, SZ - 1)), salt=9)
	# vines hanging from the cornice, the head and the haunches
	veg.vines(b, density=0.42, max_len=3, region=((0, BASE, 0), (SX - 1, CORNICE, SZ - 1)), scale=0.3)
	veg.vines(b, density=0.3, max_len=3, region=((C - 4, CAT, C - 5), (C + 4, SY - 1, C + 4)), scale=0.35)
	# ferns, grass and moss on the paving and the ledges
	veg.floor_cover(b, [("minecraft:fern", 4), ("minecraft:short_grass", 3), ("minecraft:moss_carpet", 4),
		("minecraft:bush", 1)], density=0.35, region=((0, GROUND, 0), (SX - 1, GROUND, SZ - 1)), sky=True,
		need=lambda s: any(k in s for k in ("moss", "cobble", "brick", "dirt", "andesite")), salt=21)
	veg.floor_cover(b, [("minecraft:moss_carpet", 3), ("minecraft:fern", 1)], density=0.3,
		region=((0, BASE, 0), (SX - 1, CORNICE, SZ - 1)), sky=True, need=lambda s: "stairs" not in s and "candle" not in s, salt=23)
	veg.floor_cover(b, [("minecraft:moss_carpet", 2)], density=0.25, region=((C - 4, CAT, C - 1), (C + 4, CAT + 3, C + 4)),
		sky=True, need=lambda s: "moss_block" in s, salt=25)
	decay.cleanup(b)


if __name__ == "__main__":
	import render
	print(render.render_monument(build()))
