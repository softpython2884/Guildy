"""
Tour du Mage déchu (tier 2, Overworld: forests, old growth taigas, jungles, windswept forest).

A tall round tower of dark deepslate masonry on a buttressed plinth, crowned by a corbelled observatory under a
copper dome (the dome's eastern half fell, a breach opened the north-east of the shaft). A stair turret on the
south side climbs in a spiral past the four floors (entrance hall, alchemy laboratory, library, study) and comes
up through the observatory floor: the boss arena (19 across, 8 pillars, star mosaic). A ladder shaft on the north
side is the second way in / out (door at the foot of the tower, hatch at the edge of the arena).
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from ruinlib import build as K, decay, veg, deco, shapes  # noqa: E402
from ruinlib.build import Mat, Mix, AIR, stairs  # noqa: E402
from ruinlib.ruin import Ruin, split_pieces, rules  # noqa: E402

ID = "tour_du_mage"
SEED = 2207
SX, SY, SZ = 41, 56, 41
G = 6  # ground floor layer (the below piece holds the foundations)
CX, CZ = 20, 20
F1, F2, F3, ARENA = G + 8, G + 16, G + 24, G + 32
R_OUT, R_IN = 7.3, 5.3


def build():
	b = K.new(SX, SY, SZ, SEED, ground=G, name=ID)
	m = materials()
	grounds(b, m)
	shaft(b, m)
	turret(b, m)
	ladder_shaft(b, m)
	observatory(b, m)
	floors(b, m)
	ruin_it(b, m)
	life(b, m)
	b.open_clear_zones()
	shapes.fix(b.t)
	pieces = split_pieces(ID, b.t, G, jigsaw_xz=(0, 0))
	ruin = Ruin(ID, "Tour du Mage déchu", "overworld", 2,
		["minecraft:forest", "minecraft:flower_forest", "minecraft:old_growth_pine_taiga",
			"minecraft:old_growth_spruce_taiga", "minecraft:jungle", "minecraft:sparse_jungle",
			"minecraft:bamboo_jungle", "minecraft:windswept_forest"],
		"beard_thin", pieces, G, 0, processors=[rules(
			("minecraft:deepslate_bricks", "minecraft:cracked_deepslate_bricks", 0.07),
			("minecraft:deepslate_tiles", "minecraft:cracked_deepslate_tiles", 0.05),
			("minecraft:cobblestone", "minecraft:mossy_cobblestone", 0.15))])
	ruin.plan_levels = [(G + 1, "Plan : rez-de-chaussée"), (F2 + 1, "Plan : bibliothèque"),
		(ARENA + 1, "Plan : observatoire (arène)")]
	ruin.cut_level = ARENA + 3
	return ruin


def materials():
	return {
		"body": Mat("deepslate_brick", cracked="minecraft:cracked_deepslate_bricks", crack=0.16, crack_top=0.08,
			y_low=G, y_high=ARENA, seed=SEED, extra=[("minecraft:deepslate_tiles", 0.1), ("minecraft:tuff_bricks", 0.05),
				("minecraft:polished_deepslate", 0.04)]),
		"trim": Mat("tuff_brick", seed=SEED + 1, extra=[("minecraft:chiseled_tuff_bricks", 0.05)]),
		"band": Mat("polished_tuff", seed=SEED + 2),
		"pillar": Mat("polished_deepslate", seed=SEED + 3),
		"rough": Mat("cobblestone", mossy="mossy_cobblestone", moss=0.55, moss_top=0.25, y_low=G, y_high=G + 4,
			seed=SEED + 4, extra=[("minecraft:cobbled_deepslate", 0.25), ("minecraft:tuff", 0.1)]),
		"copper": Mat(K.Fam("oxidized_cut_copper", "oxidized_cut_copper_stairs", "oxidized_cut_copper_slab", None),
			seed=SEED + 5, extra=[("minecraft:weathered_cut_copper", 0.12)]),
		"roof": Mat("deepslate_tile", cracked="minecraft:cracked_deepslate_tiles", crack=0.1, seed=SEED + 6),
		"wood": K.F["dark_oak"],
		"rubble": Mix([("minecraft:cobbled_deepslate", 4), ("minecraft:deepslate_bricks", 2),
			("minecraft:cracked_deepslate_bricks", 2), ("minecraft:mossy_cobblestone", 2), ("minecraft:gravel", 1),
			("minecraft:tuff", 1)], seed=SEED + 7),
		"found": Mix([("minecraft:cobbled_deepslate", 3), ("minecraft:cobblestone", 2), ("minecraft:stone", 2)], seed=SEED + 8),
	}


def dist(x, z, cx=CX, cz=CZ):
	return math.hypot(x - cx, z - cz)


def angle(x, z):
	return math.degrees(math.atan2(z - CZ, x - CX)) % 360


def disc_cells(r, cx=CX, cz=CZ):
	ri = int(r) + 1
	for x in range(cx - ri, cx + ri + 1):
		for z in range(cz - ri, cz + ri + 1):
			if dist(x, z, cx, cz) <= r:
				yield x, z


# --------------------------------------------------------------------------------------------- grounds

def grounds(b, m):
	"""Foundations, plinth with buttresses, a ruined garden wall and the approach path from the west."""
	rough = m["rough"]
	for x, z in disc_cells(10.3):
		for y in range(0, G):
			b.set(x, y, z, m["found"])
	for x, z in disc_cells(9.3):
		b.set(x, G, z, rough)
		b.set(x, G + 1, z, rough)
		if dist(x, z) > 8.3:
			b.set(x, G + 2, z, rough.stairs(x, G + 2, z, _toward_center(x, z), "bottom"))
		else:
			b.set(x, G + 2, z, rough)
	# radial buttresses (not on the portal / turret / ladder axes)
	for a in (22.5, 67.5, 112.5, 157.5, 202.5, 247.5, 292.5, 337.5):
		if a in (157.5, 202.5):
			continue
		ra = math.radians(a)
		for step, (r, top) in enumerate(((7.6, G + 14), (8.6, G + 10), (9.6, G + 6))):
			x, z = int(round(CX + math.cos(ra) * r)), int(round(CZ + math.sin(ra) * r))
			for y in range(G + 1, top + 1):
				b.set(x, y, z, rough if y < G + 5 else m["body"])
			b.set(x, top + 1, z, m["body"].stairs(x, top + 1, z, _toward_center(x, z), "bottom"))
	# approach path from the west edge to the portal + a ring path
	path = Mix([("minecraft:gravel", 3), ("minecraft:coarse_dirt", 3), ("minecraft:cobbled_deepslate", 2),
		("minecraft:mossy_cobblestone", 2), ("minecraft:moss_block", 1)], seed=SEED + 30)
	for x in range(0, 12):
		for z in range(18, 23):
			if (z in (18, 22) and b.hrand(x, 0, z, 1) < 0.5) or b.hrand(x, 1, z, 2) < 0.12:
				continue
			b.set(x, G, z, path)
			b.clear_zone(x, G + 1, z, x, G + 3, z)
	for x, z in disc_cells(11.4):
		if dist(x, z) > 9.4 and b.hrand(x, 2, z, 3) < 0.7:
			b.set(x, G, z, path)
	# the approach cuts through the plinth at the floor level of the hall (level with the portal)
	for x in range(9, 14):
		for z in range(18, 23):
			b.set(x, G, z, "minecraft:polished_deepslate" if 19 <= z <= 21 else rough.full(x, G, z))
			for y in range(G + 1, G + 5):
				if 19 <= z <= 21:
					b.set(x, y, z, AIR)
			if z in (18, 22):
				b.set(x, G + 1, z, rough.full(x, G + 1, z))
				b.set(x, G + 2, z, m["trim"].stairs(x, G + 2, z, "south" if z == 18 else "north", "bottom"))
	b.clear_zone(8, G + 1, 19, 14, G + 4, 21)
	# a ring of eight rune obelisks (r = 15), three of them toppled
	for k in range(8):
		a = math.radians(22.5 + 45 * k)
		ox, oz = int(round(CX + math.cos(a) * 15)), int(round(CZ + math.sin(a) * 15))
		obelisk(b, m, ox, oz, toppled=k in (1, 4, 6), direction=a)
	# amethyst growing out of the rubble: the magic leaking from the broken observatory
	for (x, z, h) in ((29, 10, 3), (31, 13, 2), (27, 8, 2), (33, 16, 1)):
		for y in range(G, G + h):
			b.set(x, y, z, "minecraft:amethyst_block" if y < G + h - 1 else "minecraft:budding_amethyst")
		b.set(x, G + h, z, "minecraft:amethyst_cluster[facing=up]")
		for (dx, dz, f) in ((1, 0, "east"), (0, 1, "south")):
			if h > 1:
				b.set(x + dx, G + h - 1, z + dz, f"minecraft:large_amethyst_bud[facing={f}]")


def obelisk(b, m, x, z, toppled, direction):
	trim = m["trim"]
	b.set(x, G, z, m["rough"])
	b.set(x + 1, G, z, m["rough"])
	b.set(x, G, z + 1, m["rough"])
	b.set(x + 1, G, z + 1, m["rough"])
	if toppled:
		# the shaft lies broken on the ground, pointing outward
		dx = 1 if math.cos(direction) > 0.3 else -1 if math.cos(direction) < -0.3 else 0
		dz = 1 if math.sin(direction) > 0.3 else -1 if math.sin(direction) < -0.3 else 0
		b.set(x, G + 1, z, trim.full(x, G + 1, z))
		for i in range(1, 5):
			px, pz = x + dx * i, z + dz * i
			if 0 <= px < SX and 0 <= pz < SZ and i != 3:
				b.set(px, G + 1, pz, trim.full(px, G + 1, pz) if i < 4 else "minecraft:chiseled_tuff_bricks")
		return
	for y in range(G + 1, G + 5):
		b.set(x, y, z, "minecraft:chiseled_tuff_bricks" if y == G + 3 else trim.full(x, y, z))
	for (dx, dz, f) in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
		b.set(x + dx, G + 1, z + dz, trim.stairs(x + dx, G + 1, z + dz, f, "bottom"))
	b.set(x, G + 5, z, "minecraft:tuff_brick_wall")
	b.set(x, G + 6, z, "minecraft:amethyst_cluster[facing=up]")


def _toward_center(x, z):
	dx, dz = CX - x, CZ - z
	if abs(dx) >= abs(dz):
		return "east" if dx > 0 else "west"
	return "south" if dz > 0 else "north"


# ----------------------------------------------------------------------------------------------- shaft

def shaft(b, m):
	body, trim, band, pillar = m["body"], m["trim"], m["band"], m["pillar"]
	for y in range(G + 1, ARENA - 3):
		for x, z in disc_cells(R_OUT):
			d = dist(x, z)
			if d > R_IN:
				b.set(x, y, z, band if (y - G) % 8 == 0 else body)
			else:
				b.set(x, y, z, AIR)
	# vertical pilasters continuing the buttresses up to the corbels (the shaft never reads as a plain cylinder)
	for a in (22.5 + 45 * k for k in range(8)):
		if a in (67.5, 112.5):
			continue  # the turret
		ra = math.radians(a)
		x, z = int(round(CX + math.cos(ra) * 7.9)), int(round(CZ + math.sin(ra) * 7.9))
		for y in range(G + 1, ARENA - 2):
			b.set(x, y, z, pillar if (y - G) % 8 else trim.full(x, y, z))
	# string courses at each floor (upside-down stairs ring)
	for y in (F1, F2, F3):
		for x, z in disc_cells(8.3):
			if 7.3 < dist(x, z) <= 8.3 and b.empty(x, y, z):
				b.set(x, y, z, trim.stairs(x, y, z, _toward_center(x, z), "top"))
	# west portal: 3 wide, 4 high, round arch, deep reveal
	for d in range(2):
		b.opening(13 + d, 19, "south", G + 1, 3, 4, 1, "west", trim, arch="round")
	for z in (18, 22):
		b.column(12, z, G + 1, G + 5, pillar)
	for z in range(18, 23):
		b.set(12, G + 6, z, trim.stairs(12, G + 6, z, "east", "top"))
	b.clear_zone(12, G + 1, 19, 15, G + 3, 21)
	deco.marker(b, 9, G + 1, 20, deco.ENTRANCE)
	# windows: 4 lancets per floor on the diagonals (skip the turret side)
	for base in (G + 2, F1 + 2, F2 + 2, F3 + 2):
		for a in (45, 135, 225, 315):
			ra = math.radians(a)
			for r in (5.6, 6.5, 7.2):
				x, z = int(round(CX + math.cos(ra) * r)), int(round(CZ + math.sin(ra) * r))
				for y in range(base, base + 3):
					b.set(x, y, z, AIR)
			x, z = int(round(CX + math.cos(ra) * 7.2)), int(round(CZ + math.sin(ra) * 7.2))
			b.set(x, base + 3, z, trim.full(x, base + 3, z))
			b.set(x, base - 1, z, trim.full(x, base - 1, z))
			for y in range(base, base + 3):
				if b.hrand(x, y, z, 12) < 0.45:
					b.set(x, y, z, "minecraft:purple_stained_glass_pane")
	# cardinal slit windows (north / east)
	for base in (F1 + 2, F2 + 2, F3 + 2):
		for (x0, z0, dx, dz) in ((CX + 6, CZ, 1, 0), (CX + 7, CZ, 1, 0)):
			for y in range(base, base + 2):
				b.set(x0, y, z0, "minecraft:purple_stained_glass_pane" if x0 == CX + 7 else AIR)


# ---------------------------------------------------------------------------------------------- turret

RING = [(20, 28), (21, 28), (21, 29), (21, 30), (20, 30), (19, 30), (19, 29), (19, 28)]


def _dir(a, b2):
	return K.dir_of(b2[0] - a[0], b2[1] - a[1])


def turret(b, m):
	"""Spiral stair turret (5 x 5, newel post): 8 steps per turn, a landing + door at every floor (every 8)."""
	body, trim, pillar = m["body"], m["trim"], m["pillar"]
	tx, tz = 20, 29
	for y in range(G + 1, ARENA):
		for x in range(tx - 2, tx + 3):
			for z in range(tz - 2, tz + 3):
				if abs(x - tx) == 2 and abs(z - tz) == 2:
					continue
				edge = abs(x - tx) == 2 or abs(z - tz) == 2
				if edge:
					b.set(x, y, z, body if (y - G) % 8 else m["band"])
				else:
					b.set(x, y, z, AIR)
	for x in range(tx - 2, tx + 3):
		for z in range(tz - 2, tz + 3):
			b.set(x, G, z, m["rough"])
	for y in range(G + 1, ARENA):
		b.set(tx, y, tz, pillar)
	for s in range(0, ARENA - G + 1):
		cell = RING[s % 8]
		y = G + s
		if s % 8 == 0:
			b.set(cell[0], y, cell[1], trim.full(cell[0], y, cell[1]))
			# door into the floor (through the 2-thick tower wall)
			if y < ARENA:
				for zz in (27, 26, 25):
					for h in range(1, 4):
						b.set(20, y + h, zz, AIR)
					b.set(20, y, zz, trim.full(20, y, zz) if zz != 25 else b.get(20, y, zz) or trim.full(20, y, zz))
				b.set(20, y + 4, 27, trim.full(20, y + 4, 27))
				b.clear_zone(20, y + 1, 25, 20, y + 3, 28)
		else:
			nxt = RING[(s + 1) % 8]
			b.set(cell[0], y, cell[1], trim.stairs(cell[0], y, cell[1], _dir(cell, nxt), "bottom"))
			b.clear_zone(cell[0], y + 1, cell[1], cell[0], y + 2, cell[1])
		b.protect(cell[0], y, cell[1], cell[0], y, cell[1])
	# slit windows in the turret
	for y in range(G + 4, ARENA - 2, 6):
		for (x, z) in ((22, 29), (18, 29), (20, 31)):
			b.set(x, y, z, AIR)
			b.set(x, y + 1, z, AIR)
	b.protect(18, G, 27, 22, ARENA - 1, 31)


def ladder_shaft(b, m):
	"""Narrow ladder shaft on the north face: from a low door to a hatch at the north edge of the arena."""
	body = m["body"]
	for y in range(G + 1, ARENA):
		for (x, z) in ((19, 11), (20, 11), (21, 11), (19, 12), (21, 12)):
			b.set(x, y, z, body)
		b.set(20, y, 12, "minecraft:ladder[facing=north]")
	for x in range(19, 22):
		for z in (11, 12):
			b.set(x, G, z, m["rough"])
	b.set(20, ARENA, 12, "minecraft:ladder[facing=north]")
	# door at the foot (west side)
	for y in (G + 1, G + 2):
		b.set(19, y, 12, AIR)
	b.set(18, G, 12, m["rough"])
	b.clear_zone(18, G + 1, 12, 20, G + 2, 12)
	b.protect(19, G, 11, 21, ARENA, 12)


# ------------------------------------------------------------------------------------------ observatory

def observatory(b, m):
	body, trim, pillar, copper = m["body"], m["trim"], m["pillar"], m["copper"]
	# corbels stepping out from the shaft (r 7.3) to the observatory wall (r 10.3), around the stair turret and
	# the ladder shaft that climb through them
	keep_open = {(x, z) for x in range(19, 22) for z in range(28, 31)} | {(20, 12)}
	for x, z in disc_cells(10.4):
		d = dist(x, z)
		for y in range(ARENA - 3, ARENA):
			if (x, z) in keep_open:
				continue
			reach = {ARENA - 3: 8.3, ARENA - 2: 9.3, ARENA - 1: 10.4}[y]
			if R_IN < d <= reach:
				if d > reach - 1.0 and y < ARENA - 1:
					b.set(x, y, z, trim.stairs(x, y, z, _toward_center(x, z), "top"))
				else:
					b.set(x, y, z, body if y < ARENA - 1 else trim.full(x, y, z))
		# arena floor: star mosaic
		b.set(x, ARENA, z, floor_pattern(x, z))
	# stairwell slot (top of the spiral) and the ladder hatch
	for (x, z) in ((19, 28), (19, 29)):
		b.set(x, ARENA, z, AIR)
	b.set(20, ARENA, 12, "minecraft:ladder[facing=north]")
	# walls with 8 pillars and tall windows
	for x, z in disc_cells(10.4):
		d = dist(x, z)
		if d <= 9.3:
			for y in range(ARENA + 1, ARENA + 6):
				b.set(x, y, z, AIR)
			continue
		a = angle(x, z)
		near_pillar = min(abs((a - 22.5) % 45 - 0), abs((a - 22.5) % 45 - 45)) < 7
		for y in range(ARENA + 1, ARENA + 6):
			window = not near_pillar and ARENA + 2 <= y <= ARENA + 4
			b.set(x, y, z, AIR if window else (pillar if near_pillar else trim))
		if not near_pillar:
			b.set(x, ARENA + 1, z, trim.full(x, ARENA + 1, z))
	for a in (22.5 + 45 * k for k in range(8)):
		ra = math.radians(a)
		x, z = int(round(CX + math.cos(ra) * 11.0)), int(round(CZ + math.sin(ra) * 11.0))
		for y in range(ARENA - 1, ARENA + 6):
			b.set(x, y, z, pillar)
		b.set(x, ARENA + 6, z, trim.stairs(x, ARENA + 6, z, _toward_center(x, z), "bottom"))
		b.set(x, ARENA - 2, z, trim.stairs(x, ARENA - 2, z, _toward_center(x, z), "top"))
	# railing around the stairwell slot and the ladder hatch
	for (x, z) in ((18, 28), (18, 29), (20, 29), (19, 30)):
		b.set(x, ARENA + 1, z, "minecraft:deepslate_brick_wall")
	for (x, z) in ((19, 12), (21, 12)):
		b.set(x, ARENA + 1, z, "minecraft:deepslate_brick_wall")
	# the dome
	b.dome(CX, CZ, ARENA + 6, 10.3, copper, shell=1.0)
	for k in range(0, 11):
		rk = math.sqrt(max(0.0, 10.3 ** 2 - k * k))
		for x, z in disc_cells(rk):
			if b.get(x, ARENA + 6 + k, z) is None:
				b.set(x, ARENA + 6 + k, z, AIR)
	for x, z in disc_cells(10.4):
		if dist(x, z) > 9.3:
			b.set(x, ARENA + 6, z, trim.full(x, ARENA + 6, z))
	# grates (light slits) in the dome
	for x, z in disc_cells(10.4):
		for y in range(ARENA + 7, ARENA + 12):
			s = b.get(x, y, z)
			if s and "copper" in s and abs(angle(x, z) % 45 - 22.5) < 4:
				b.set(x, y, z, "minecraft:oxidized_copper_grate")
	# telescope on a dais at the west edge, the treasure behind it
	for x in range(11, 14):
		for z in range(18, 23):
			if dist(x, z) <= 9.3:
				b.set(x, ARENA + 1, z, trim.slab(x, ARENA + 1, z, "bottom") if x == 13 else trim.full(x, ARENA + 1, z))
	b.set(12, ARENA + 2, 21, "minecraft:dark_oak_fence")
	b.set(12, ARENA + 3, 21, "minecraft:lightning_rod[facing=east]")
	b.set(13, ARENA + 3, 21, "minecraft:lightning_rod[facing=east]")
	b.set(11, ARENA + 3, 21, "minecraft:lightning_rod[facing=west]")
	deco.chest(b, 12, ARENA + 2, 19, "east", "treasure")
	deco.candles(b, 11, ARENA + 2, 19, 3, color="purple")
	deco.candles(b, 11, ARENA + 2, 22, 2, color="purple")
	# hanging soul lanterns from the dome
	for a in (0, 90, 180, 270):
		ra = math.radians(a)
		x, z = int(round(CX + math.cos(ra) * 6)), int(round(CZ + math.sin(ra) * 6))
		top = ARENA + 6 + int(math.sqrt(max(0, 10.3 ** 2 - 36))) - 1
		for y in range(ARENA + 5, top + 1):
			b.set(x, y, z, "minecraft:iron_chain[axis=y]")
		deco.lantern(b, x, ARENA + 4, z, soul=True, hanging=True)
	# markers: the fallen mage in the middle, his servants around
	deco.marker(b, CX, ARENA + 1, CZ, deco.BOSS)
	for a in (30, 90, 150, 210, 270, 330):
		ra = math.radians(a)
		x, z = int(round(CX + math.cos(ra) * 5.5)), int(round(CZ + math.sin(ra) * 5.5))
		deco.marker(b, x, ARENA + 1, z, deco.MINION)
	b.clear_zone(CX - 8, ARENA + 1, CZ - 8, CX + 8, ARENA + 4, CZ + 8)
	b.protect(CX - 11, ARENA - 3, CZ - 11, CX + 11, ARENA + 1, CZ + 11)


def floor_pattern(x, z):
	d = dist(x, z)
	a = angle(x, z)
	ray = min(a % 45, 45 - a % 45) < 4.5 and 2 < d < 8
	if d < 1.2:
		return "minecraft:chiseled_tuff_bricks"
	if ray:
		return "minecraft:amethyst_block" if d > 6 else "minecraft:polished_tuff"
	if 4.6 < d <= 5.6:
		return "minecraft:tuff_bricks"
	if d > 8.6:
		return "minecraft:polished_deepslate"
	return "minecraft:deepslate_tiles" if (x + z) % 2 else "minecraft:polished_deepslate"


# ---------------------------------------------------------------------------------------------- floors

def floors(b, m):
	# plank floors with a dark oak beam ring
	for fy in (F1, F2, F3):
		for x, z in disc_cells(R_IN):
			d = dist(x, z)
			b.set(x, fy, z, "minecraft:dark_oak_planks" if d > 4.3 else "minecraft:spruce_planks")
	for x, z in disc_cells(R_IN):
		b.set(x, G, z, "minecraft:polished_deepslate" if dist(x, z) > 4.3 else
			("minecraft:deepslate_tiles" if (x + z) % 2 else "minecraft:polished_tuff"))
	# ground floor: entrance hall with a ritual circle, chandelier, a barrel
	for a in range(0, 360, 45):
		ra = math.radians(a)
		x, z = int(round(CX + math.cos(ra) * 2)), int(round(CZ + math.sin(ra) * 2))
		deco.candles(b, x, G + 1, z, 2, color="purple", lit=b.hrand(x, 0, z, 1) < 0.5)
	b.set(CX, G + 1, CZ, "minecraft:crying_obsidian")
	deco.hanging_lantern(b, CX, G + 5, CZ, 2, soul=True)
	deco.chest(b, 24, G + 1, 17, "west", "common", kind="barrel")
	b.set(24, G + 1, 23, "minecraft:lectern[facing=west,has_book=false,powered=false]")
	# laboratory
	for x, z in disc_cells(R_IN):
		d = dist(x, z)
		if 4.0 < d <= R_IN and not (18 <= x <= 22 and z >= 23):
			b.set(x, F1 + 1, z, "minecraft:polished_deepslate_slab[type=top]" if b.hrand(x, 9, z, 1) < 0.6 else "minecraft:chiseled_tuff")
	for (x, z, what) in ((16, 20, "minecraft:brewing_stand"), (17, 17, "minecraft:water_cauldron[level=2]"),
		(17, 23, "minecraft:cauldron"), (23, 17, "minecraft:brewing_stand"), (16, 19, "minecraft:amethyst_cluster[facing=up]")):
		b.set(x, F1 + 1 if "cauldron" in what else F1 + 2, z, what)
		if "brewing" in what:
			b.t.nbt[(x, F1 + 2, z)] = deco.N.Compound(id=deco.N.String("minecraft:brewing_stand"))
	# central alchemy table: dark oak counter with jars (decorated pots), a cauldron of water, purple candles
	for x in range(CX - 1, CX + 2):
		b.set(x, F1 + 1, CZ, stairs("minecraft:dark_oak_stairs", "north", "top"))
	b.set(CX - 1, F1 + 2, CZ, "minecraft:decorated_pot[cracked=false,facing=south,waterlogged=false]")
	b.t.nbt[(CX - 1, F1 + 2, CZ)] = deco.N.Compound(id=deco.N.String("minecraft:decorated_pot"))
	b.set(CX, F1 + 2, CZ, "minecraft:amethyst_cluster[facing=up]")
	deco.candles(b, CX + 1, F1 + 2, CZ, 3, color="purple")
	for (x, z) in ((CX - 2, CZ + 2), (CX + 2, CZ - 2)):
		b.set(x, F1 + 1, z, "minecraft:water_cauldron[level=3]")
	# shelves of jars and herbs along the wall above the counters
	for (x, z) in ((16, 18), (16, 22), (18, 16), (22, 16), (24, 22)):
		if b.is_air(x, F1 + 2, z):
			b.set(x, F1 + 2, z, b.pick(["minecraft:potted_crimson_fungus", "minecraft:potted_warped_fungus",
				"minecraft:potted_dead_bush", "minecraft:potted_brown_mushroom", "minecraft:amethyst_cluster[facing=up]"]))
	# a carpet of purple wool and a lectern in the study
	for x in range(CX - 2, CX + 3):
		for z in range(CZ - 2, CZ + 3):
			if b.is_air(x, F3 + 1, z) and b.hrand(x, 0, z, 33) < 0.85:
				b.set(x, F3 + 1, z, "minecraft:purple_carpet" if max(abs(x - CX), abs(z - CZ)) < 2 else "minecraft:magenta_carpet")
	b.set(17, F3 + 1, 18, "minecraft:lectern[facing=east,has_book=false,powered=false]")
	b.set(19, F3 + 1, 24, "minecraft:chiseled_bookshelf[facing=north,slot_0_occupied=true,slot_1_occupied=true,slot_2_occupied=false,slot_3_occupied=false,slot_4_occupied=true,slot_5_occupied=false]")
	b.set(21, F3 + 1, 24, "minecraft:bookshelf")
	deco.chest(b, 24, F1 + 1, 20, "west", "rare")
	deco.hanging_lantern(b, CX, F1 + 6, CZ, 1)
	# library: bookshelf ring 3 high, lecterns, a reading table
	for x, z in disc_cells(R_IN):
		d = dist(x, z)
		if 4.3 < d <= R_IN and not (19 <= x <= 21 and z >= 24):
			window = any(abs(angle(x, z) - a) < 12 for a in (45, 135, 225, 315))
			for y in range(F2 + 1, F2 + 4):
				if not (window and y >= F2 + 2):
					b.set(x, y, z, "minecraft:chiseled_bookshelf[facing=north,slot_0_occupied=true,slot_1_occupied=false,slot_2_occupied=true,slot_3_occupied=true,slot_4_occupied=false,slot_5_occupied=true]"
						if b.hrand(x, y, z, 3) < 0.12 else "minecraft:bookshelf")
	for (x, z, f) in ((18, 18, "south"), (22, 22, "north")):
		b.set(x, F2 + 1, z, f"minecraft:lectern[facing={f},has_book=false,powered=false]")
	for x in (19, 20, 21):
		b.set(x, F2 + 1, 20, stairs("minecraft:dark_oak_stairs", "north", "top"))
	deco.candles(b, 20, F2 + 2, 20, 3)
	deco.chest(b, 16, F2 + 1, 20, "east", "common")
	# study: desk, map table, candles, a chest; the ceiling beams
	b.set(23, F3 + 1, 19, "minecraft:cartography_table")
	b.set(23, F3 + 1, 20, stairs("minecraft:dark_oak_stairs", "east", "top"))
	b.set(23, F3 + 1, 21, stairs("minecraft:dark_oak_stairs", "east", "top"))
	deco.candles(b, 23, F3 + 2, 21, 2, color="purple")
	b.set(22, F3 + 1, 20, stairs("minecraft:dark_oak_stairs", "west", "bottom"))
	deco.chest(b, 16, F3 + 1, 21, "east", "common", kind="barrel")
	b.set(16, F3 + 1, 19, "minecraft:bookshelf")
	b.set(16, F3 + 2, 19, "minecraft:bookshelf")
	for x in range(CX - 5, CX + 6):
		if dist(x, CZ) <= R_IN:
			b.set(x, ARENA - 1, CZ, "minecraft:stripped_dark_oak_log[axis=x]")
	deco.hanging_lantern(b, CX, F3 + 5, CZ, 1, soul=True)


# ------------------------------------------------------------------------------------------------- decay

def ruin_it(b, m):
	# the eastern half of the dome fell in
	n = b.noise

	def dome_fall(x, y, z, state):
		east = (x - CX) + 2.5 * n.n3(x * 0.25, y * 0.25, z * 0.25)
		north = (CZ - z) + 2.5 * n.n3(z * 0.25, y * 0.25, x * 0.25)
		return y >= ARENA + 7 and east > 0.5 and north > -3.5

	decay.damage(b, dome_fall)
	# the eastern wall of the observatory broke down to its sill
	decay.breach(b, CX + 10, ARENA + 5, CZ + 2, 2.5, 3.0, 4.5, roughness=0.4)
	# a breach in the north-east of the shaft (library and laboratory)
	decay.breach(b, CX + 5, F1 + 5, CZ - 5, 2.6, 5.5, 2.6, roughness=0.4)
	decay.erode(b, 0.18, top_bias=0.6, y0=G + 3, match=lambda s: "brick" in s or "cobble" in s or "tuff" in s)
	decay.erode(b, 0.45, top_bias=0.0, y0=G + 1, y1=G + 5, match=lambda s: "cobble" in s)
	decay.stabilize(b)
	decay.cleanup(b)

	def source(state):
		if "copper" in state:
			return "minecraft:oxidized_cut_copper_slab[type=bottom]" if b.rng.random() < 0.3 else (
				"minecraft:oxidized_copper" if b.rng.random() < 0.12 else False)
		return None
	decay.rubble(b, m["rubble"], keep=0.3, scatter=2.2, top=m["body"], max_height=3, source=source)
	decay.cleanup(b)


def life(b, m):
	veg.vines(b, density=0.35, max_len=9, region=((0, G + 3, 0), (SX - 1, SY - 1, SZ - 1)))
	veg.replace_surface(b, {"minecraft:cobblestone": "minecraft:moss_block",
		"minecraft:cobbled_deepslate": "minecraft:mossy_cobblestone"}, density=0.3, region=((0, G, 0), (40, G + 3, 40)))
	veg.floor_cover(b, [("minecraft:moss_carpet", 3), ("minecraft:fern", 3), ("minecraft:short_grass", 2),
		("minecraft:oak_leaves[persistent=true]", 2), ("minecraft:azalea", 1)], density=0.25,
		region=((0, G + 1, 0), (40, G + 4, 40)), sky=True,
		need=lambda s: any(k in s for k in ("cobble", "moss", "gravel", "dirt", "tuff")))
	# leaves and moss creeping over the ledges and the breach
	veg.floor_cover(b, [("minecraft:oak_leaves[persistent=true]", 3), ("minecraft:moss_carpet", 2)],
		density=0.18, region=((0, G + 5, 0), (40, ARENA - 1, 40)), sky=True)
	veg.cobwebs(b, density=0.18, region=((CX - 6, G + 1, CZ - 6), (CX + 6, ARENA - 1, CZ + 6)))
	decay.cleanup(b)


if __name__ == "__main__":
	import render
	print(render.render_ruin(build()))
