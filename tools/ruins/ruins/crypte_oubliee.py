"""
Crypte oubliée (tier 1, Overworld: plains, forests, meadow, taiga).

A small walled graveyard around a ruined gothic chapel (west belfry with a broken spire, nave with buttresses
and lancet windows, polygonal apse). The middle bay of the roof fell in. In the south aisle a stairwell goes down
under the nave to the ossuary, a vaulted gallery leads west to the burial gallery (spawner) and to the Hall of the
Forgotten Lord (17 x 17, 7 high, four pillars, throne dais). The graveyard ground caved in over the south-east
corner of the hall: a sinkhole ramp is the second way in / out.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from ruinlib import build as K, decay, veg, deco, shapes  # noqa: E402
from ruinlib.build import Mat, Mix, AIR, stairs  # noqa: E402
from ruinlib.ruin import Ruin, split_pieces, rules  # noqa: E402

ID = "crypte_oubliee"
SEED = 1101
SX, SY, SZ = 41, 50, 41
G = 16  # ground floor layer
FEET = G - 9  # crypt walking level
FLOOR = FEET - 1


def build():
	b = K.new(SX, SY, SZ, SEED, ground=G, name=ID)
	m = materials()
	site(b, m)
	chapel(b, m)
	tower(b, m)
	crypt(b, m)
	graveyard(b, m)
	sinkhole(b, m)
	ruin_it(b, m)
	life(b, m)
	b.open_clear_zones()
	shapes.fix(b.t)
	pieces = split_pieces(ID, b.t, G, jigsaw_xz=(0, 0))
	ruin = Ruin(ID, "Crypte oubliée", "overworld", 1,
		["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:forest",
			"minecraft:birch_forest", "minecraft:old_growth_birch_forest", "minecraft:dark_forest",
			"minecraft:pale_garden", "minecraft:dappled_forest", "minecraft:taiga"],
		"beard_thin", pieces, G, 0, processors=[rules(
			("minecraft:stone_bricks", "minecraft:cracked_stone_bricks", 0.06),
			("minecraft:cobblestone", "minecraft:mossy_cobblestone", 0.12),
			("minecraft:deepslate_bricks", "minecraft:cracked_deepslate_bricks", 0.08),
			("minecraft:deepslate_tiles", "minecraft:cracked_deepslate_tiles", 0.06))])
	ruin.plan_levels = [(G + 1, "Plan : chapelle et cimetière"), (FEET, "Plan : crypte (niveau du boss)")]
	ruin.cut_level = G + 5
	ruin.under_cut = FEET + 3
	return ruin


def materials():
	return {
		"wall": Mat("stone_brick", mossy="mossy_stone_brick", cracked="minecraft:cracked_stone_bricks",
			moss=0.42, moss_top=0.04, crack=0.22, crack_top=0.12, y_low=G + 1, y_high=G + 10, seed=SEED,
			extra=[("minecraft:stone", 0.03), ("minecraft:andesite", 0.03)]),
		"rough": Mat("cobblestone", mossy="mossy_cobblestone", moss=0.5, moss_top=0.2, y_low=G, y_high=G + 6,
			seed=SEED + 1, extra=[("minecraft:stone_bricks", 0.15)]),
		"trim": Mat("tuff_brick", cracked=None, seed=SEED + 2, extra=[("minecraft:chiseled_tuff_bricks", 0.04)]),
		"pillar": Mat("polished_andesite", seed=SEED + 3),
		"roof": Mat("deepslate_tile", cracked="minecraft:cracked_deepslate_tiles", crack=0.12, seed=SEED + 4),
		"floor": Mix([("minecraft:stone_bricks", 5), ("minecraft:polished_andesite", 3), ("minecraft:cracked_stone_bricks", 2),
			("minecraft:cobblestone", 1), ("minecraft:mossy_stone_bricks", 1)], seed=SEED + 5),
		"cwall": Mat("deepslate_brick", cracked="minecraft:cracked_deepslate_bricks", crack=0.2, seed=SEED + 6,
			extra=[("minecraft:deepslate_tiles", 0.12), ("minecraft:cobbled_deepslate", 0.08), ("minecraft:tuff", 0.05)]),
		"ctile": Mat("deepslate_tile", cracked="minecraft:cracked_deepslate_tiles", crack=0.25, seed=SEED + 7),
		"cpillar": Mat("polished_deepslate", seed=SEED + 8),
		"cfloor": Mix([("minecraft:deepslate_tiles", 4), ("minecraft:polished_deepslate", 2),
			("minecraft:cracked_deepslate_tiles", 2), ("minecraft:cobbled_deepslate", 1), ("minecraft:tuff", 1)], seed=SEED + 9),
		"earth": Mix([("minecraft:dirt", 5), ("minecraft:coarse_dirt", 3), ("minecraft:rooted_dirt", 1),
			("minecraft:gravel", 1)], seed=SEED + 10),
		"rubble": Mix([("minecraft:cobblestone", 4), ("minecraft:mossy_cobblestone", 3), ("minecraft:gravel", 2),
			("minecraft:stone_bricks", 2), ("minecraft:cracked_stone_bricks", 2), ("minecraft:andesite", 1)], seed=SEED + 11),
		"crubble": Mix([("minecraft:cobbled_deepslate", 4), ("minecraft:gravel", 2), ("minecraft:deepslate_tiles", 1),
			("minecraft:tuff", 1)], seed=SEED + 12),
		"pew": K.F["spruce"],
	}


# ------------------------------------------------------------------------------------------------- the site

def site(b, m):
	"""Graveyard enclosure: a low wall with posts, broken later; path from the south gate to the belfry door."""
	trim = m["trim"]
	x0, z0, x1, z1 = 1, 1, 39, 39
	for x in range(x0, x1 + 1):
		for z in (z0, z1):
			low_wall_cell(b, m, x, z, (x - x0) % 5 == 0)
	for z in range(z0 + 1, z1):
		for x in (x0, x1):
			low_wall_cell(b, m, x, z, (z - z0) % 5 == 0)
	# gate in the south wall: two tall posts with lanterns, a fallen iron gate
	for x in (5, 9):
		for y in range(G + 1, G + 5):
			b.set(x, y, z1, trim)
		b.set(x, G + 5, z1, trim.slab(x, G + 5, z1, "bottom"))
		deco.skull(b, x, G + 6, z1, "skeleton_skull", rotation=8) if x == 5 else deco.lantern(b, x, G + 6, z1)
	for x in range(6, 9):
		for y in range(G + 1, G + 5):
			b.set(x, y, z1, AIR)
		b.set(x, G, z1, "minecraft:gravel")
	b.set(6, G + 1, z1, "minecraft:iron_bars")
	b.set(8, G + 1, z1, "minecraft:iron_bars")
	b.set(8, G + 2, z1, "minecraft:iron_bars")
	b.clear_zone(6, G + 1, z1 - 1, 8, G + 3, z1)
	# the path (ground floor layer: replaces the natural top block): north from the gate, then east to the portal
	path = Mix([("minecraft:gravel", 4), ("minecraft:coarse_dirt", 3), ("minecraft:cobblestone", 2),
		("minecraft:mossy_cobblestone", 1), ("minecraft:dirt_path", 2)], seed=SEED + 20)
	for z in range(14, z1):
		for x in range(6, 9):
			if b.hrand(x, G, z, 3) < 0.92:
				b.set(x, G, z, path)
			b.clear_zone(x, G + 1, z, x, G + 3, z)
	# side path to the south door of the nave
	for x in range(10, 16):
		for z in (17, 18):
			if b.hrand(x, G, z, 4) < 0.8:
				b.set(x, G, z, path)
			b.clear_zone(x, G + 1, z, x, G + 3, z)


def low_wall_cell(b, m, x, z, post):
	rough, wall = m["rough"], m["wall"]
	b.set(x, G, z, rough)
	b.set(x, G + 1, z, rough)
	if post:
		b.set(x, G + 2, z, wall)
		b.set(x, G + 3, z, wall.slab(x, G + 3, z, "bottom") if b.hrand(x, 0, z, 9) < 0.7 else AIR)
	else:
		b.set(x, G + 2, z, wall.wall(x, G + 2, z))


# ---------------------------------------------------------------------------------------------- the chapel

NAVE_X0, NAVE_X1 = 10, 31  # west (facade) and east (chancel arch) walls
NAVE_Z0, NAVE_Z1 = 5, 15  # north and south walls
WALL_TOP = G + 9


def chapel(b, m):
	wall, rough, trim, pillar, roof = m["wall"], m["rough"], m["trim"], m["pillar"], m["roof"]
	# foundations under the whole chapel (below the ground floor, so it never floats on a slope)
	for x in range(NAVE_X0 - 1, 37):
		for z in range(NAVE_Z0 - 1, NAVE_Z1 + 2):
			for y in range(G - 4, G):
				if b.get(x, y, z) is None:
					b.set(x, y, z, m["rough"])
	# floor
	for x in range(NAVE_X0, NAVE_X1 + 1):
		for z in range(NAVE_Z0, NAVE_Z1 + 1):
			b.set(x, G, z, m["floor"])
	# long walls (north z=5, south z=15) with buttresses every 4, plinth and cornice
	b.wall_run((NAVE_X0, NAVE_Z0), (NAVE_X1, NAVE_Z0), G + 1, WALL_TOP, wall, "north", pillars=0, plinth=False,
		cornice=False)
	b.wall_run((NAVE_X0, NAVE_Z1), (NAVE_X1, NAVE_Z1), G + 1, WALL_TOP, wall, "south", pillars=0, plinth=False,
		cornice=False)
	for z, out in ((NAVE_Z0, "north"), (NAVE_Z1, "south")):
		dz = -1 if out == "north" else 1
		for x in range(NAVE_X0, NAVE_X1 + 1):
			bay = (x - NAVE_X0) % 4
			if bay == 1 or x in (NAVE_X0, NAVE_X1):
				b.buttress(x, z, out, G + 1, WALL_TOP - 1, rough, depth=2)
				b.set(x, WALL_TOP, z + dz, trim)
				b.set(x, WALL_TOP + 1, z + dz, trim.slab(x, WALL_TOP + 1, z + dz, "bottom"))
			else:
				b.set(x, G + 1, z + dz, rough.stairs(x, G + 1, z + dz, K.OPP[out], "bottom"))
				b.set(x, WALL_TOP, z + dz, wall.stairs(x, WALL_TOP, z + dz, K.OPP[out], "top"))
				if bay == 3:
					# lancet window: 1 wide, 4 high, tuff frame, pointed head, broken glass
					for y in range(G + 4, G + 8):
						b.set(x, y, z, "minecraft:gray_stained_glass_pane" if b.hrand(x, y, z, 1) < 0.35 else AIR)
					b.set(x, G + 8, z, trim.stairs(x, G + 8, z, K.OPP[out], "top"))
					b.set(x, G + 3, z + dz, trim.stairs(x, G + 3, z + dz, K.OPP[out], "top"))
					for side in (-1, 1):
						for y in range(G + 4, G + 8):
							b.set(x + side, y, z, trim)
	# west facade wall (x=10) and east wall (x=31) with the chancel arch
	for z in range(NAVE_Z0, NAVE_Z1 + 1):
		for y in range(G + 1, WALL_TOP + 1):
			b.set(NAVE_X0, y, z, wall)
			b.set(NAVE_X1, y, z, wall)
	# gable ends (above the walls, under the roof)
	gable(b, m, NAVE_X0)
	gable(b, m, NAVE_X1)
	# chancel arch x=31, z=8..12, 7 high, pointed
	b.opening(NAVE_X1, 8, "south", G + 1, 5, 7, 1, "east", trim, arch="pointed")
	for z in (7, 13):
		for y in range(G + 1, G + 8):
			b.set(NAVE_X1, y, z, pillar)
	# side door in the south wall (x=14..15)
	b.opening(14, NAVE_Z1, "east", G + 1, 2, 3, 1, "south", trim, arch="round")
	b.clear_zone(14, G + 1, NAVE_Z1 - 1, 15, G + 3, NAVE_Z1 + 1)
	# interior: columns along the walls carry the roof trusses
	for x in range(NAVE_X0 + 1, NAVE_X1, 4):
		for z in (NAVE_Z0 + 1, NAVE_Z1 - 1):
			b.column(x, z, G + 1, WALL_TOP - 1, pillar)
			b.set(x, WALL_TOP, z, trim)
		# truss (spruce beam across the nave under the roof)
		for z in range(NAVE_Z0 + 1, NAVE_Z1):
			b.set(x, WALL_TOP + 1, z, "minecraft:stripped_spruce_log[axis=z]")
	b.gable_roof(NAVE_X0, NAVE_Z0, NAVE_X1, NAVE_Z1, WALL_TOP + 1, "x", roof, overhang=1, under="minecraft:spruce_planks")
	apse(b, m)
	nave_interior(b, m)


def gable(b, m, x):
	wall, trim = m["wall"], m["trim"]
	for k in range(0, 6):
		for z in range(NAVE_Z0 + k + 1, NAVE_Z1 - k):
			b.set(x, WALL_TOP + 1 + k, z, wall)
	# a round window (oculus) in the gable
	for z in (9, 10, 11):
		b.set(x, WALL_TOP + 3, z, AIR)
	b.set(x, WALL_TOP + 4, 10, AIR)
	b.set(x, WALL_TOP + 2, 10, AIR)
	b.set(x, WALL_TOP + 4, 9, trim.stairs(x, WALL_TOP + 4, 9, "north", "top"))
	b.set(x, WALL_TOP + 4, 11, trim.stairs(x, WALL_TOP + 4, 11, "south", "top"))
	b.set(x, WALL_TOP + 2, 9, trim.stairs(x, WALL_TOP + 2, 9, "north", "bottom"))
	b.set(x, WALL_TOP + 2, 11, trim.stairs(x, WALL_TOP + 2, 11, "south", "bottom"))


APSE = [(32, z) for z in range(6, 15)] + [(33, z) for z in range(6, 15)] + [(34, z) for z in range(7, 14)] + \
	[(35, z) for z in range(8, 13)]


def apse(b, m):
	"""Polygonal apse east of the chancel arch: walls on the outline of APSE, raised floor, altar."""
	wall, trim, roof = m["wall"], m["trim"], m["roof"]
	inside = set(APSE)
	outline = set()
	for (x, z) in inside:
		for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1)):
			p = (x + dx, z + dz)
			if p not in inside and p[0] > NAVE_X1:
				outline.add(p)
	for (x, z) in outline:
		for y in range(G, WALL_TOP - 1):
			b.set(x, y, z, wall)
		b.set(x, WALL_TOP - 1, z, trim)
	for (x, z) in inside:
		b.set(x, G, z, "minecraft:polished_andesite")
		b.set(x, G + 1, z, "minecraft:smooth_stone_slab[type=bottom]")
		for y in range(G + 2, WALL_TOP):
			b.set(x, y, z, AIR)
	# lancets in the three apse faces
	for (x, z, out) in ((36, 10, "east"), (35, 6, "north"), (35, 14, "south")):
		for y in range(G + 4, G + 8):
			b.set(x, y, z, "minecraft:red_stained_glass_pane" if b.hrand(x, y, z, 2) < 0.4 else AIR)
	# half-cone roof over the apse, leaning on the east gable
	b.spire(31.5, 10, 5.6, WALL_TOP, 6, roof, round_=True, only=lambda x, z: x >= 32)
	# altar
	for z in (9, 10, 11):
		b.set(34, G + 1, z, "minecraft:polished_deepslate")
		b.set(34, G + 2, z, "minecraft:polished_deepslate_slab[type=bottom]" if z != 10 else
			"minecraft:chiseled_deepslate")
	deco.candles(b, 34, G + 3, 10, 3, lit=True)
	deco.candles(b, 34, G + 3, 9, 2, color="white", lit=False)
	b.set(33, G + 2, 10, "minecraft:lectern[facing=west,has_book=false,powered=false]")
	b.set(35, G + 2, 10, "minecraft:skeleton_skull[rotation=12]")
	b.t.nbt[(35, G + 2, 10)] = deco.skull_nbt()
	# steps up to the apse
	for z in range(8, 13):
		b.set(NAVE_X1 - 1, G + 1, z, m["wall"].slab(NAVE_X1 - 1, G + 1, z, "bottom"))
	deco.chest(b, 32, G + 2, 13, "north", "common", kind="barrel")


def nave_interior(b, m):
	pew = m["pew"]
	# clear the inside
	for x in range(NAVE_X0 + 1, NAVE_X1):
		for z in range(NAVE_Z0 + 1, NAVE_Z1):
			for y in range(G + 1, WALL_TOP + 1):
				if b.get(x, y, z) is None:
					b.set(x, y, z, AIR)
	# pews (backrest to the west, facing the altar), a central aisle at z = 10
	for x in range(13, 29, 2):
		for z in list(range(7, 10)) + list(range(11, 14)):
			if 19 <= x <= 23 and z >= 11:
				continue  # stairwell
			if b.hrand(x, 0, z, 30) < 0.78:
				b.set(x, G + 1, z, stairs(pew.stairs, "west"))
	# the crypt stairwell in the south aisle: railing of stone brick walls and candles
	for x in range(19, 24):
		b.set(x, G + 1, 10, m["wall"].wall(x, G + 1, 10))
	deco.candles(b, 19, G + 2, 10, 3)
	deco.candles(b, 23, G + 2, 10, 2)
	# a fallen chandelier and a hanging one
	deco.hanging_lantern(b, 25, G + 6, 10, chain_len=3)
	b.set(16, G + 1, 9, "minecraft:iron_chain[axis=x]")
	b.set(17, G + 1, 9, "minecraft:lantern[hanging=false]")


# ----------------------------------------------------------------------------------------------- belfry

TX0, TZ0, TX1, TZ1 = 4, 7, 10, 13  # tower footprint (shares x=10 with the facade), centre (7, 10)
TOWER_TOP = G + 17


def tower(b, m):
	wall, rough, trim, pillar, roof = m["wall"], m["rough"], m["trim"], m["pillar"], m["roof"]
	for x in range(TX0 - 1, TX1 + 1):
		for z in range(TZ0 - 1, TZ1 + 2):
			for y in range(G - 4, G):
				b.set(x, y, z, rough)
	for x in range(TX0, TX1 + 1):
		for z in range(TZ0, TZ1 + 1):
			b.set(x, G, z, m["floor"])
	for y in range(G + 1, TOWER_TOP + 1):
		for x in range(TX0, TX1 + 1):
			for z in range(TZ0, TZ1 + 1):
				edge = x in (TX0, TX1) or z in (TZ0, TZ1)
				corner = x in (TX0, TX1) and z in (TZ0, TZ1)
				b.set(x, y, z, (trim if corner else wall) if edge else AIR)
	# corner buttresses (two per free corner, stepping back as the tower rises)
	for (x, z, out) in ((TX0, TZ0, "west"), (TX0, TZ0, "north"), (TX0, TZ1, "west"), (TX0, TZ1, "south"),
		(TX1, TZ0, "north"), (TX1, TZ1, "south")):
		b.buttress(x, z, out, G + 1, G + 12, rough, depth=2)
	# string courses
	for y in (G + 7, G + 11):
		for x in range(TX0 - 1, TX1 + 1):
			for z, face in ((TZ0 - 1, "south"), (TZ1 + 1, "north")):
				if b.empty(x, y, z):
					b.set(x, y, z, trim.stairs(x, y, z, face, "top"))
		for z in range(TZ0, TZ1 + 1):
			if b.empty(TX0 - 1, y, z):
				b.set(TX0 - 1, y, z, trim.stairs(TX0 - 1, y, z, "east", "top"))
	# south portal (main door) with a small gable over it, and the arch into the nave
	b.opening(6, TZ1, "east", G + 1, 3, 4, 1, "south", trim, arch="round")
	for x in (5, 9):
		b.column(x, TZ1 + 1, G + 1, G + 4, pillar)
	for x in range(5, 10):
		f = "east" if x < 7 else "west" if x > 7 else None
		b.set(x, G + 5, TZ1 + 1, trim.stairs(x, G + 5, TZ1 + 1, f, "bottom") if f else trim.full(x, G + 5, TZ1 + 1))
	b.set(7, G + 6, TZ1 + 1, trim.slab(7, G + 6, TZ1 + 1, "bottom"))
	b.clear_zone(6, G + 1, TZ1 - 1, 8, G + 3, TZ1 + 2)
	b.clear_zone(TX0 + 1, G + 1, 9, TX1 + 1, G + 3, 11)
	b.opening(TX1, 9, "south", G + 1, 3, 5, 1, "east", trim, arch="round")
	# belfry: two pointed openings per face, plank floor, the bell
	for y in range(G + 12, G + 16):
		for i in (6, 8):
			b.set(i, y, TZ0, AIR)
			b.set(i, y, TZ1, AIR)
		for j in (9, 11):
			b.set(TX0, y, j, AIR)
			b.set(TX1, y, j, AIR)
	for i in (6, 8):
		for z, f in ((TZ0, "north"), (TZ1, "south")):
			b.set(i, G + 11, z, trim.slab(i, G + 11, z, "top"))
	for x in range(TX0 + 1, TX1):
		for z in range(TZ0 + 1, TZ1):
			b.set(x, G + 11, z, "minecraft:spruce_planks")
			b.set(x, TOWER_TOP, z, "minecraft:spruce_planks")
	b.set(7, G + 15, 10, "minecraft:bell[attachment=ceiling,facing=north,powered=false]")
	b.t.nbt[(7, G + 15, 10)] = deco.N.Compound(id=deco.N.String("minecraft:bell"))
	for y in range(G + 16, TOWER_TOP):
		b.set(7, y, 10, "minecraft:dark_oak_log[axis=y]")
	# ladder up to the belfry floor
	for y in range(G + 1, G + 12):
		b.set(8, y, TZ0 + 1, "minecraft:ladder[facing=south]")
	b.set(8, G + 11, TZ0 + 1, "minecraft:ladder[facing=south]")
	# parapet with corner pinnacles, then a solid tiled spire
	for x in range(TX0 - 1, TX1 + 2):
		for z in range(TZ0 - 1, TZ1 + 2):
			ring = x in (TX0 - 1, TX1 + 1) or z in (TZ0 - 1, TZ1 + 1)
			if ring and not (x in (TX0 - 1, TX1 + 1) and z in (TZ0 - 1, TZ1 + 1)):
				f = "east" if x == TX0 - 1 else "west" if x == TX1 + 1 else "south" if z == TZ0 - 1 else "north"
				b.set(x, TOWER_TOP, z, trim.stairs(x, TOWER_TOP, z, f, "top"))
	for (x, z) in ((TX0, TZ0), (TX0, TZ1), (TX1, TZ0), (TX1, TZ1)):
		b.set(x, TOWER_TOP + 1, z, trim)
		b.set(x, TOWER_TOP + 2, z, trim.wall(x, TOWER_TOP + 2, z))
	top = b.spire(7, 10, 3.0, TOWER_TOP + 1, 10, roof)
	# finial: a slender iron-bound tip (broken later)
	for y in range(top - 1, top + 3):
		b.set(7, y, 10, "minecraft:deepslate_tile_wall" if y < top + 2 else "minecraft:iron_bars")
	deco.marker(b, 7, G + 1, TZ1 + 3, deco.ENTRANCE)


# ------------------------------------------------------------------------------------------------- crypt

HALL = (2, 13, 18, 29)  # interior x0, z0, x1, z1
HALL_CEIL = G - 2  # ceiling layer (air FEET .. G-3: 7 high)


def crypt(b, m):
	cwall, ctile, cpillar = m["cwall"], m["ctile"], m["cpillar"]
	# solid shell around the whole crypt so caves / water never break in
	carve_box(b, m, 0, FLOOR - 1, 11, 40, G - 2, 31, solid_only=True)
	# --- main stairwell from the nave (south aisle, x=20..28, z=12..14), descending east
	for k in range(9):
		x = 20 + k
		y = G - 1 - k
		for z in (12, 13, 14):
			b.set(x, y, z, cwall.stairs(x, y, z, "west", "bottom"))
			for h in range(1, 4):
				b.set(x, y + h, z, AIR)
	for x in range(20, 23):
		for z in (12, 13, 14):
			b.set(x, G, z, AIR)
	b.clear_zone(19, G + 1, 12, 19, G + 3, 14)
	# landing and passage south under the nave wall
	room(b, m, 29, 12, 31, 17, FEET, 4)
	b.vault_z(12, 17, 29, 31, FEET + 3, ctile)
	# --- ossuary antechamber
	room(b, m, 26, 18, 34, 24, FEET, 5)
	b.vault_x(26, 34, 18, 24, FEET + 4, ctile)
	for x in range(26, 35):
		for z in (18, 24):
			b.set(x, FEET + 4, z, ctile.stairs(x, FEET + 4, z, "north" if z == 18 else "south", "top"))
	ossuary_niches(b, m, 26, 34, 18, 24)
	deco.sarcophagus(b, 29, FEET, 21, "east", cpillar, lid=ctile, skull_kind="skeleton_skull")
	deco.candles(b, 30, FEET + 2, 21, 3)
	deco.chest(b, 34, FEET, 19, "west", "common")
	b.clear_zone(26, FEET, 20, 34, FEET + 2, 22)
	# --- reliquary (east): rare chest
	room(b, m, 36, 19, 39, 23, FEET, 4)
	b.set(35, FEET, 21, AIR)
	b.set(35, FEET + 1, 21, AIR)
	b.set(35, FEET + 2, 21, ctile.stairs(35, FEET + 2, 21, "east", "top"))
	b.clear_zone(35, FEET, 21, 36, FEET + 1, 21)
	for z in (19, 23):
		b.set(39, FEET, z, "minecraft:polished_deepslate")
		deco.candles(b, 39, FEET + 1, z, 4)
	deco.chest(b, 39, FEET, 21, "west", "rare")
	b.set(38, FEET, 21, "minecraft:polished_deepslate_slab[type=bottom]")
	deco.skull(b, 39, FEET + 1, 21, "skeleton_skull", rotation=4)
	# --- gallery of niches toward the hall (x=20..25, z=20..22)
	room(b, m, 19, 20, 25, 22, FEET, 4)
	b.vault_x(19, 25, 20, 22, FEET + 3, ctile)
	for x in range(20, 25):
		for z, face in ((19, "south"), (23, "north")):
			if x % 2 == 0 and x != 22:
				b.set(x, FEET + 1, z, AIR)
				deco.skull(b, x, FEET + 1, z, "skeleton_skull", rotation=0 if face == "south" else 8)
				b.set(x, FEET, z, "minecraft:bone_block[axis=x]")
	b.clear_zone(19, FEET, 21, 25, FEET + 2, 21)
	for x in (20, 24):
		deco.hanging_lantern(b, x, FEET + 2, 21, 1, soul=True)
	# --- burial gallery (south of the gallery): sarcophagi, zombie spawner, rare chest
	room(b, m, 19, 24, 25, 29, FEET, 4)
	b.set(22, FEET, 23, AIR)
	b.set(22, FEET + 1, 23, AIR)
	b.clear_zone(22, FEET, 23, 22, FEET + 1, 24)
	for (x, z, along) in ((19, 25, "south"), (25, 25, "south")):
		deco.sarcophagus(b, x, FEET, z, along, cpillar, lid=ctile, length=3)
	b.set(22, FEET, 27, "minecraft:polished_deepslate")
	deco.spawner(b, 22, FEET + 1, 27, "minecraft:zombie", count=2, max_nearby=4)
	deco.chest(b, 22, FEET, 29, "north", "rare")
	deco.candles(b, 20, FEET, 29, 2)
	deco.candles(b, 24, FEET, 29, 3)
	# --- the hall
	hall(b, m)


def carve_box(b, m, x0, y0, z0, x1, y1, z1, solid_only=False):
	for x in range(x0, x1 + 1):
		for y in range(y0, y1 + 1):
			for z in range(z0, z1 + 1):
				if b.get(x, y, z) is None:
					b.set(x, y, z, m["cwall"] if y < G - 3 else m["earth"])


def room(b, m, x0, z0, x1, z1, feet, height):
	"""Rectangular crypt room: floor, air, walls on the outline (only where the shell is)."""
	for x in range(x0, x1 + 1):
		for z in range(z0, z1 + 1):
			b.set(x, feet - 1, z, m["cfloor"])
			for y in range(feet, feet + height):
				b.set(x, y, z, AIR)


def ossuary_niches(b, m, x0, x1, z0, z1):
	"""Bone niches in the north and south walls of the antechamber."""
	for x in range(x0 + 1, x1, 2):
		for z, rot in ((z0 - 1, 0), (z1 + 1, 8)):
			if b.hrand(x, 0, z, 70) < 0.8:
				b.set(x, FEET + 1, z, AIR)
				b.set(x, FEET, z, "minecraft:bone_block[axis=y]")
				deco.skull(b, x, FEET + 1, z, "skeleton_skull", rotation=rot)
				b.set(x, FEET + 2, z, m["ctile"].stairs(x, FEET + 2, z, "north" if rot == 0 else "south", "top"))


def hall(b, m):
	cwall, ctile, cpillar = m["cwall"], m["ctile"], m["cpillar"]
	x0, z0, x1, z1 = HALL
	for x in range(x0 - 1, x1 + 2):
		for z in range(z0 - 1, z1 + 2):
			for y in range(FLOOR - 1, HALL_CEIL + 2):
				if b.get(x, y, z) is None or True:
					b.set(x, y, z, cwall)
	room(b, m, x0, z0, x1, z1, FEET, HALL_CEIL - FEET)
	# floor pattern: a cross of polished deepslate toward the dais
	for x in range(x0, x1 + 1):
		for z in (20, 21, 22):
			b.set(x, FLOOR, z, "minecraft:polished_deepslate" if z == 21 else "minecraft:deepslate_tiles")
	# ribbed ceiling: tile beams between the pillars, lower crown between the ribs
	for x in range(x0, x1 + 1):
		for z in range(z0, z1 + 1):
			b.set(x, HALL_CEIL, z, ctile)
	for z in range(z0, z1 + 1):
		for x in (6, 7, 13, 14):
			b.set(x, HALL_CEIL - 1, z, cwall)
	for x in range(x0, x1 + 1):
		for z in (16, 17, 25, 26):
			b.set(x, HALL_CEIL - 1, z, cwall)
	# side springers along the walls
	for x in range(x0, x1 + 1):
		b.set(x, HALL_CEIL - 1, z0, ctile.stairs(x, HALL_CEIL - 1, z0, "north", "top"))
		b.set(x, HALL_CEIL - 1, z1, ctile.stairs(x, HALL_CEIL - 1, z1, "south", "top"))
	for z in range(z0, z1 + 1):
		b.set(x0, HALL_CEIL - 1, z, ctile.stairs(x0, HALL_CEIL - 1, z, "west", "top"))
		b.set(x1, HALL_CEIL - 1, z, ctile.stairs(x1, HALL_CEIL - 1, z, "east", "top"))
	# four 2x2 pillars with bases and capitals
	for px in (6, 13):
		for pz in (16, 25):
			for x in (px, px + 1):
				for z in (pz, pz + 1):
					for y in range(FEET, HALL_CEIL):
						b.set(x, y, z, cpillar)
			for x in range(px - 1, px + 3):
				for z in range(pz - 1, pz + 3):
					if b.is_air(x, FEET, z):
						if x in (px - 1, px + 2) and z in (pz - 1, pz + 2):
							continue
						facing = "east" if x == px - 1 else "west" if x == px + 2 else "south" if z == pz - 1 else "north"
						b.set(x, FEET, z, ctile.stairs(x, FEET, z, facing, "bottom"))
						b.set(x, HALL_CEIL - 2, z, ctile.stairs(x, HALL_CEIL - 2, z, facing, "top"))
			deco.hanging_lantern(b, px + (2 if px == 6 else -1), HALL_CEIL - 3, pz + (2 if pz == 16 else -1), 2, soul=True)
	# wall pilasters with wither skulls
	for z in range(z0 + 2, z1, 4):
		for x, f in ((x0 - 1, "east"), (x1 + 1, "west")):
			if b.get(x, FEET + 1, z) is not None:
				b.set(x, FEET + 3, z, AIR)
				deco.skull(b, x, FEET + 3, z, "wither_skeleton_skull", rotation=4 if f == "east" else 12)
	# the dais (west end): two steps, throne, braziers, treasure chest behind the throne
	for x in range(x0, x0 + 4):
		for z in range(17, 26):
			b.set(x, FEET, z, "minecraft:polished_deepslate")
	for z in range(17, 26):
		b.set(x0 + 4, FEET, z, ctile.stairs(x0 + 4, FEET, z, "west", "bottom"))
	for x in range(x0, x0 + 4):
		b.set(x, FEET, 16, ctile.stairs(x, FEET, 16, "south", "bottom"))
		b.set(x, FEET, 26, ctile.stairs(x, FEET, 26, "north", "bottom"))
	# throne
	b.set(x0 + 1, FEET + 1, 21, stairs("minecraft:polished_deepslate_stairs", "west"))
	b.set(x0 + 1, FEET + 1, 20, "minecraft:polished_deepslate_wall")
	b.set(x0 + 1, FEET + 1, 22, "minecraft:polished_deepslate_wall")
	b.set(x0, FEET + 1, 21, "minecraft:polished_deepslate")
	b.set(x0, FEET + 2, 21, "minecraft:polished_deepslate")
	b.set(x0, FEET + 3, 21, "minecraft:chiseled_deepslate")
	deco.skull(b, x0, FEET + 4, 21, "wither_skeleton_skull", rotation=4)
	for z in (18, 24):
		b.set(x0 + 2, FEET + 1, z, "minecraft:soul_campfire[facing=east,lit=true,signal_fire=false]")
		b.t.nbt[(x0 + 2, FEET + 1, z)] = deco.N.Compound(id=deco.N.String("minecraft:campfire"))
	deco.chest(b, x0, FEET + 1, 19, "east", "treasure")
	deco.candles(b, x0, FEET + 1, 23, 4)
	deco.candles(b, x0, FEET + 1, 25, 3)
	# entrance from the gallery (east wall, z=20..22)
	for z in (20, 21, 22):
		for y in range(FEET, FEET + 4):
			b.set(x1 + 1, y, z, AIR)
	b.set(x1 + 1, FEET + 3, 20, ctile.stairs(x1 + 1, FEET + 3, 20, "north", "top"))
	b.set(x1 + 1, FEET + 3, 22, ctile.stairs(x1 + 1, FEET + 3, 22, "south", "top"))
	# markers: the lord in front of the dais, his guard around the pillars
	deco.marker(b, 9, FEET, 21, deco.BOSS)
	for (x, z) in ((5, 14), (5, 28), (10, 18), (10, 24), (16, 15), (16, 27)):
		deco.marker(b, x, FEET, z, deco.MINION)
	b.clear_zone(x0, FEET, z0, x1, FEET + 4, z1)
	b.protect(x0 - 1, FLOOR - 1, z0 - 1, x1 + 1, HALL_CEIL + 1, z1 + 1)


# ------------------------------------------------------------------------------------------- graveyard

GRAVES = [(x, z) for x in (3, 13, 17, 21, 25, 29, 33) for z in (19, 23, 27, 31, 35)] + \
	[(x, z) for x in (3,) for z in (6, 10, 14)]


def graveyard(b, m):
	for (x, z) in GRAVES:
		if 4 <= x <= 10:
			continue
		if 13 <= x <= 17 and z >= 29:
			continue  # the sinkhole
		if 29 <= x <= 37 and 27 <= z <= 37:
			continue  # the mausoleum
		r = b.hrand(x, 0, z, 40)
		grave(b, m, x, z, r)
	mausoleum(b, m)
	dead_tree(b, 24, 34)
	# lantern posts along the path
	for z in (18, 26, 34):
		for x in (5, 9):
			if b.hrand(x, 0, z, 41) < 0.75:
				b.set(x, G + 1, z, "minecraft:spruce_fence")
				b.set(x, G + 2, z, "minecraft:spruce_fence")
				deco.lantern(b, x, G + 3, z) if b.hrand(x, 1, z, 42) < 0.7 else None


def grave(b, m, x, z, r):
	"""Headstone at the west end (x), mound toward the east (x+1..x+2)."""
	trim, wall = m["trim"], m["wall"]
	mound = "minecraft:podzol" if r < 0.3 else "minecraft:coarse_dirt" if r < 0.6 else "minecraft:rooted_dirt"
	for dx in (1, 2):
		b.set(x + dx, G, z, mound)
	style = int(r * 5)
	if style == 0:
		b.set(x, G + 1, z, wall.wall(x, G + 1, z))
		b.set(x, G + 2, z, wall.wall(x, G + 2, z))
	elif style == 1:
		b.set(x, G + 1, z, trim.full(x, G + 1, z))
		b.set(x, G + 2, z, trim.stairs(x, G + 2, z, "west", "bottom"))
	elif style == 2:
		b.set(x, G + 1, z, "minecraft:cobblestone_wall")
		b.set(x, G + 2, z, "minecraft:cobblestone_wall")
		b.set(x, G + 2, z - 1, "minecraft:cobblestone_wall") if b.hrand(x, 3, z, 1) < 0.5 else None
	elif style == 3:
		b.set(x, G + 1, z, wall.slab(x, G + 1, z, "bottom"))  # sunken, broken stone
		deco.skull(b, x + 1, G + 1, z, "skeleton_skull") if b.hrand(x, 4, z, 1) < 0.4 else None
	else:
		b.set(x, G + 1, z, wall.stairs(x, G + 1, z, "west", "bottom"))
		b.set(x, G + 2, z, trim.slab(x, G + 2, z, "bottom"))
	if b.hrand(x, 5, z, 1) < 0.35:
		deco.candles(b, x + 1, G + 1, z, lit=b.hrand(x, 6, z, 1) < 0.5)
	elif b.hrand(x, 7, z, 1) < 0.4:
		b.set(x + 2, G + 1, z, b.pick(["minecraft:fern", "minecraft:short_grass", "minecraft:white_tulip",
			"minecraft:oxeye_daisy", "minecraft:lily_of_the_valley"]))


def mausoleum(b, m):
	"""Small family vault in the south-east corner (common loot)."""
	wall, trim, roof, pillar = m["wall"], m["trim"], m["roof"], m["pillar"]
	x0, z0, x1, z1 = 32, 30, 36, 35
	for x in range(x0, x1 + 1):
		for z in range(z0, z1 + 1):
			b.set(x, G - 1, z, m["rough"])
			b.set(x, G, z, "minecraft:polished_andesite")
			for y in range(G + 1, G + 5):
				edge = x in (x0, x1) or z in (z0, z1)
				corner = x in (x0, x1) and z in (z0, z1)
				b.set(x, y, z, (pillar if corner else wall) if edge else AIR)
	for x in range(x0, x1 + 1):
		for z in (z0 - 1, z1 + 1):
			b.set(x, G + 1, z, wall.stairs(x, G + 1, z, "south" if z == z1 + 1 else "north", "bottom") if x not in (x0, x1)
				else pillar)
	b.gable_roof(x0, z0, x1, z1, G + 5, "z", roof, overhang=1, fill=wall, under="minecraft:deepslate_tiles")
	# door on the west face: pilasters, pediment, iron bars ajar
	b.opening(x0, 32, "south", G + 1, 2, 3, 1, "west", trim, arch="flat")
	for z in (31, 34):
		b.column(x0 - 1, z, G + 1, G + 3, pillar)
	for z in range(31, 35):
		b.set(x0 - 1, G + 4, z, trim.stairs(x0 - 1, G + 4, z, "east", "top"))
	b.set(x0, G + 1, 33, "minecraft:iron_bars")
	b.clear_zone(x0 - 1, G + 1, 32, x0 + 1, G + 2, 32)
	b.set(34, G + 1, 31, "minecraft:polished_andesite")
	b.set(34, G + 2, 31, "minecraft:polished_andesite_slab[type=bottom]")
	deco.skull(b, 35, G + 1, 31, "skeleton_skull", rotation=8)
	deco.sarcophagus(b, 34, G + 1, 33, "south", m["cpillar"], lid=m["ctile"], length=2)
	deco.chest(b, 35, G + 1, 34, "west", "common")
	deco.candles(b, 33, G + 1, 34, 3)


def dead_tree(b, x, z):
	"""A dead oak: crooked trunk, bare branches (logs) ending in twigs (fences), a lantern hanging from a bough."""
	log = "minecraft:dark_oak_log"
	b.set(x, G, z, "minecraft:rooted_dirt")
	trunk = [(x, G + 1, z), (x, G + 2, z), (x, G + 3, z), (x + 1, G + 4, z), (x + 1, G + 5, z), (x + 1, G + 6, z),
		(x, G + 7, z)]
	for (tx, ty, tz) in trunk:
		b.set(tx, ty, tz, log + "[axis=y]")
	b.set(x, G + 4, z, log + "[axis=x]")
	b.set(x, G + 6, z, log + "[axis=x]")
	branches = [((x + 2, G + 5, z), "x", [(x + 3, G + 6, z), (x + 4, G + 6, z)]),
		((x - 1, G + 6, z), "x", [(x - 2, G + 7, z), (x - 2, G + 8, z)]),
		((x + 1, G + 6, z + 1), "z", [(x + 1, G + 7, z + 2), (x + 1, G + 7, z + 3)]),
		((x, G + 7, z - 1), "z", [(x, G + 8, z - 2), (x - 1, G + 8, z - 2)])]
	for (bx, by, bz), axis, twigs in branches:
		b.set(bx, by, bz, f"{log}[axis={axis}]")
		for i, (tx, ty, tz) in enumerate(twigs):
			b.set(tx, ty, tz, f"{log}[axis={axis}]" if i == 0 else "minecraft:dark_oak_fence")
	b.set(x, G + 8, z, "minecraft:dark_oak_fence")
	b.set(x + 4, G + 5, z, "minecraft:iron_chain[axis=y]")
	deco.lantern(b, x + 4, G + 4, z, hanging=True)


# --------------------------------------------------------------------------------------------- sinkhole

def sinkhole(b, m):
	"""The ground collapsed over the south-east corner of the hall: an earth ramp from the graveyard down."""
	earth = m["earth"]
	x0, x1 = 13, 16
	for k in range(9):
		z = 38 - k
		top = G - 1 - k  # ramp surface block
		for x in range(x0 - 1, x1 + 2):
			edge = x in (x0 - 1, x1 + 1)
			for y in range(FLOOR, G + 1):
				if edge:
					if y <= top + 2 and b.get(x, y, z) is None:
						b.set(x, y, z, earth)
					continue
				if y < top:
					if b.get(x, y, z) is None:
						b.set(x, y, z, earth)
				elif y == top:
					b.set(x, y, z, earth.full(x, y, z) if b.hrand(x, y, z, 3) < 0.55 else
						stairs("minecraft:cobblestone_stairs" if b.hrand(x, y, z, 4) < 0.5 else
							"minecraft:mossy_cobblestone_stairs", "south"))
				else:
					b.set(x, y, z, AIR)
	# break through the hall wall at z=30 and the hall ceiling edge
	for x in range(x0, x1 + 1):
		for y in range(FEET, HALL_CEIL + 1):
			b.t.set(x, y, 30, AIR)
		for y in range(FEET, G):
			b.t.set(x, y, 30, AIR if y > FEET - 1 else b.get(x, y, 30))
		b.set(x, FEET - 1, 30, m["earth"])
		b.set(x, FEET, 30, stairs("minecraft:cobblestone_stairs", "south"))
	for x in range(x0, x1 + 1):
		b.clear_zone(x, FEET, 29, x, FEET + 3, 38)
	# roots hanging into the hole and a fallen headstone
	b.set(12, G + 1, 31, "minecraft:mossy_cobblestone_wall")
	deco.skull(b, 14, FEET + 1 + 0, 29, "skeleton_skull")


# ------------------------------------------------------------------------------------------------- decay

def ruin_it(b, m):
	masonry = lambda st: any(k in st for k in ("brick", "cobble", "stone", "tuff", "andesite"))
	# the middle bays of the nave roof fell in with their trusses; the north wall is breached under them
	decay.breach(b, 21, WALL_TOP + 3, 10, 4.5, 5.5, 8.5, roughness=0.5)
	decay.breach(b, 17, WALL_TOP - 1, 4, 3.2, 4.0, 2.2)
	decay.breach(b, 27, WALL_TOP + 1, 16, 2.4, 2.4, 1.8)
	# the spire lost its tip, one belfry corner crumbled
	decay.breach(b, 8, TOWER_TOP + 12, 9, 1.6, 2.2, 1.6, roughness=0.2)
	decay.breach(b, 4, TOWER_TOP, 7, 2.2, 2.6, 2.2)
	decay.breach(b, 36, WALL_TOP + 1, 10, 2.2, 3, 2.2)
	# the enclosure wall is broken in places
	for (cx, cz, r) in ((1, 22, 3), (18, 39, 2.5), (39, 9, 3.5), (30, 1, 3), (39, 28, 2), (12, 1, 2)):
		decay.breach(b, cx, G + 2, cz, r, 3, r)
	decay.erode(b, 0.28, top_bias=1.0, y0=G + 2, match=masonry)
	decay.stabilize(b)
	decay.cleanup(b)
	def source(state):
		if "planks" in state or "log" in state:
			return "minecraft:spruce_slab[type=bottom]" if b.rng.random() < 0.35 else False
		if "tile" in state:
			return "minecraft:deepslate_tile_slab[type=bottom]" if b.rng.random() < 0.3 else False
		return None
	decay.rubble(b, m["rubble"], keep=0.32, scatter=1.8, top=m["wall"], max_height=2, source=source)
	decay.cleanup(b)


def life(b, m):
	veg.vines(b, density=0.32, max_len=6, region=((0, G + 2, 0), (40, SY - 1, 40)))
	veg.replace_surface(b, {"minecraft:stone_bricks": "minecraft:mossy_stone_bricks",
		"minecraft:cobblestone": "minecraft:mossy_cobblestone"}, density=0.25, region=((0, G, 0), (40, G + 2, 40)))
	nave = ((NAVE_X0 + 1, G + 1, NAVE_Z0 + 1), (NAVE_X1 - 1, G + 2, NAVE_Z1 - 1))
	veg.floor_cover(b, [("minecraft:moss_carpet", 5), ("minecraft:short_grass", 2), ("minecraft:fern", 1)],
		density=0.35, region=nave, sky=True)
	veg.floor_cover(b, [("minecraft:moss_carpet", 3), ("minecraft:short_grass", 3), ("minecraft:fern", 2),
		("minecraft:dandelion", 1)], density=0.22, region=((0, G + 1, 0), (40, G + 3, 40)), sky=True,
		need=lambda s: "cobble" in s or "dirt" in s or "podzol" in s or "gravel" in s)
	crypt_region = ((0, FEET, 11), (40, G - 2, 40))
	veg.cobwebs(b, density=0.22, region=crypt_region)
	veg.cobwebs(b, density=0.12, region=((TX0, G + 8, TZ0), (TX1, G + 17, TZ1)))
	veg.hanging(b, "minecraft:hanging_roots", density=0.09, region=crypt_region)
	veg.lichen(b, density=0.05, region=crypt_region)
	decay.cleanup(b)


if __name__ == "__main__":
	import render
	print(render.render_ruin(build()))
