"""
Citadelle des âmes (tier 5, Nether: soul sand valley, basalt deltas, warped forest).

A citadel of blackstone and basalt on a stepped platform ringed by a moat of eternal soul fire. Octagonal curtain
walls with four drum towers (soul fire braziers on top), a gatehouse with a hanging portcullis on the south, a
tall keep on the north crowned by a soul beacon (the landmark seen across the valley). Inside: the Field of Souls,
a round court (23 across) paved with basalt, ringed by bone monoliths and soul campfires: the arena of the
champion. Around it, an ossuary chapel (east) and a blaze foundry (west). Ways in / out: the gate, a breach in the
west wall, the postern of the east chapel.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from ruinlib import build as K, decay, veg, deco, shapes  # noqa: E402
from ruinlib.build import Mat, Mix, AIR  # noqa: E402
from ruinlib.ruin import Ruin, Piece, rules  # noqa: E402

ID = "citadelle_des_ames"
SEED = 6607
SX, SY, SZ = 60, 50, 60
C = 29.5
FLOOR = 3
WALL_TOP = FLOOR + 10
TOWER_TOP = FLOOR + 16
KEEP = (23, 3, 36, 14)  # x0, z0, x1, z1
KEEP_TOP = FLOOR + 34
ARENA_C = (29.5, 30.0)
ARENA_R = 11.6


def build():
	b = K.new(SX, SY, SZ, SEED, ground=0, name=ID)
	m = materials()
	platform(b, m)
	walls(b, m)
	towers(b, m)
	gatehouse(b, m)
	keep(b, m)
	arena(b, m)
	courtyard(b, m)
	chapel(b, m)
	foundry(b, m)
	ruin_it(b, m)
	life(b, m)
	# the cavern the citadel needs when the site is inside a netherrack / soul soil mass (no effect in open space)
	b.carve_dome(C, C, 31.5, FLOOR + 1, SY - FLOOR - 2)
	b.open_clear_zones()
	shapes.fix(b.t)
	ruin = Ruin(ID, "Citadelle des âmes", "nether", 5,
		["minecraft:soul_sand_valley", "minecraft:basalt_deltas", "minecraft:warped_forest"],
		"beard_box", [Piece("main", b.t, (0, 0, 0))], 0, 33, terrain="minecraft:soul_soil", processors=[rules(
			("minecraft:polished_blackstone_bricks", "minecraft:cracked_polished_blackstone_bricks", 0.1),
			("minecraft:soul_soil", "minecraft:soul_sand", 0.2))])
	ruin.big = True
	ruin.plan_levels = [(FLOOR + 1, "Plan : champ des âmes (arène), donjon, chapelle, fonderie")]
	ruin.cut_level = FLOOR + 6
	return ruin


def materials():
	return {
		"brick": Mat("polished_blackstone_brick", cracked="minecraft:cracked_polished_blackstone_bricks", crack=0.25,
			seed=SEED, extra=[("minecraft:blackstone", 0.1), ("minecraft:polished_blackstone", 0.05)]),
		"pol": Mat("polished_blackstone", seed=SEED + 1),
		"rough": Mat("blackstone", seed=SEED + 2, extra=[("minecraft:basalt[axis=y]", 0.25), ("minecraft:smooth_basalt", 0.1)]),
		"tile": Mat("deepslate_tile", cracked="minecraft:cracked_deepslate_tiles", crack=0.15, seed=SEED + 3),
		"soul": Mix([("minecraft:soul_soil", 5), ("minecraft:soul_sand", 3), ("minecraft:bone_block[axis=y]", 1)], seed=SEED + 4, scale=0.3),
		"pave": Mix([("minecraft:polished_basalt[axis=y]", 3), ("minecraft:smooth_basalt", 3), ("minecraft:polished_blackstone", 2),
			("minecraft:soul_soil", 1)], seed=SEED + 5, scale=0.3),
		"rubble": Mix([("minecraft:blackstone", 4), ("minecraft:basalt[axis=y]", 3), ("minecraft:polished_blackstone_bricks", 2),
			("minecraft:soul_soil", 2), ("minecraft:bone_block[axis=x]", 1)], seed=SEED + 6),
	}


def dist(x, z, c=(C, C)):
	return math.hypot(x - c[0], z - c[1])


def octa(x, z):
	dx, dz = abs(x - C), abs(z - C)
	return max(dx, dz, (dx + dz) / 1.414)


def inward(x, z, c=(C, C)):
	dx, dz = c[0] - x, c[1] - z
	if abs(dx) >= abs(dz):
		return "east" if dx > 0 else "west"
	return "south" if dz > 0 else "north"


# -------------------------------------------------------------------------------------------- platform

def platform(b, m):
	rough = m["rough"]
	for x in range(SX):
		for z in range(SZ):
			o = octa(x, z)
			if o > 29.4:
				continue
			for y in range(0, FLOOR + 1):
				if o > 29.4 - (FLOOR - y) * 1.0:
					continue
				b.set(x, y, z, rough.full(x, y, z) if y < FLOOR else m["soul"].full(x, y, z))
			if 27.4 < o <= 29.4:
				ey = int(FLOOR - (o - 27.4))
				if 0 <= ey <= FLOOR:
					b.set(x, ey, z, m["brick"].stairs(x, ey, z, inward(x, z), "bottom"))
			# soul fire moat between the platform rim and the wall: soul soil trench with eternal soul fire
			if 26.2 < o <= 27.4:
				b.set(x, FLOOR, z, "minecraft:soul_soil")
				b.set(x, FLOOR + 1, z, "minecraft:soul_fire")
			for y in range(FLOOR + 1, FLOOR + 8):
				if o <= 26.2 and b.get(x, y, z) is None:
					b.set(x, y, z, AIR)


# ----------------------------------------------------------------------------------------------- walls

def walls(b, m):
	brick, pol, tile = m["brick"], m["pol"], m["tile"]
	for x in range(SX):
		for z in range(SZ):
			o = octa(x, z)
			if 23.2 < o <= 26.2:
				outer = o > 25.2
				for y in range(FLOOR, WALL_TOP + 1):
					if y == WALL_TOP or (y - FLOOR) % 5 == 0:
						s = pol.full(x, y, z)
					else:
						s = brick.full(x, y, z) if outer else m["rough"].full(x, y, z)
					b.set(x, y, z, s)
				if outer:
					merlon = (x + z) % 3 == 0
					b.set(x, WALL_TOP + 1, z, "minecraft:polished_blackstone" if merlon else "minecraft:polished_blackstone_slab[type=bottom]")
					if merlon:
						b.set(x, WALL_TOP + 2, z, "minecraft:polished_blackstone_wall")
	# basalt pilasters on the outer face, every 6 along the perimeter
	for x in range(SX):
		for z in range(SZ):
			o = octa(x, z)
			if 26.2 < o <= 27.0 and (x * 7 + z * 13) % 6 == 0:
				for y in range(FLOOR, WALL_TOP):
					b.set(x, y, z, "minecraft:polished_basalt[axis=y]")
				b.set(x, WALL_TOP, z, tile.stairs(x, WALL_TOP, z, inward(x, z), "bottom"))


# ---------------------------------------------------------------------------------------------- towers

TOWERS = [(10, 10), (49, 10), (10, 49), (49, 49)]


def towers(b, m):
	brick, pol = m["brick"], m["pol"]
	for (cx, cz) in TOWERS:
		for x in range(cx - 6, cx + 7):
			for z in range(cz - 6, cz + 7):
				d = dist(x, z, (cx, cz))
				if d > 5.4:
					continue
				for y in range(FLOOR, TOWER_TOP + 1):
					if d > 4.2:
						s = pol.full(x, y, z) if (y - FLOOR) % 6 == 0 else brick.full(x, y, z)
					else:
						s = AIR if FLOOR < y < TOWER_TOP else "minecraft:polished_blackstone"
					b.set(x, y, z, s)
				if 4.2 < d <= 5.4:
					b.set(x, TOWER_TOP + 1, z, "minecraft:polished_blackstone_wall" if (x + z) % 2 else "minecraft:polished_blackstone")
		# soul brazier on the roof: soul soil bowl with soul fire, chains of soul lanterns around
		for (dx, dz) in ((0, 0), (1, 0), (0, 1), (1, 1)):
			b.set(cx + dx - 1, TOWER_TOP + 1, cz + dz - 1, "minecraft:soul_soil")
			b.set(cx + dx - 1, TOWER_TOP + 2, cz + dz - 1, "minecraft:soul_fire")
		for (dx, dz) in ((-2, -1), (-2, 0), (1, -1), (1, 0), (-1, -2), (0, -2), (-1, 1), (0, 1)):
			b.set(cx + dx, TOWER_TOP + 1, cz + dz, "minecraft:polished_blackstone_bricks")
		# slits
		for a in range(0, 360, 60):
			ra = math.radians(a)
			x, z = int(round(cx + math.cos(ra) * 5)), int(round(cz + math.sin(ra) * 5))
			for y in (FLOOR + 7, FLOOR + 8, FLOOR + 12):
				b.set(x, y, z, AIR)
		# door toward the courtyard
		sx = 1 if cx < C else -1
		sz = 1 if cz < C else -1
		for t in range(3, 8):
			x, z = cx + sx * t, cz + sz * t
			for y in range(FLOOR + 1, FLOOR + 4):
				if b.get(x, y, z) is not None:
					b.set(x, y, z, AIR)
			b.set(x, FLOOR, z, "minecraft:polished_blackstone")
		b.clear_zone(cx + sx * 3, FLOOR + 1, cz + sz * 3, cx + sx * 7, FLOOR + 2, cz + sz * 7)
	deco.chest(b, 10, FLOOR + 1, 49, "north", "nether_common")
	deco.chest(b, 49, FLOOR + 1, 49, "north", "nether_common", kind="barrel")


# -------------------------------------------------------------------------------------------- gatehouse

def gatehouse(b, m):
	brick, pol = m["brick"], m["pol"]
	for x in range(25, 35):
		for z in range(52, 58):
			for y in range(FLOOR, FLOOR + 15):
				edge = x in (25, 34) or z in (52, 57) or y in (FLOOR, FLOOR + 14)
				b.set(x, y, z, (pol.full(x, y, z) if (y - FLOOR) % 7 == 0 else brick.full(x, y, z)) if edge else AIR)
		b.set(x, FLOOR + 15, 57, "minecraft:polished_blackstone_wall" if x % 2 else "minecraft:polished_blackstone")
		b.set(x, FLOOR + 15, 52, "minecraft:polished_blackstone_wall" if x % 2 else "minecraft:polished_blackstone")
	# passage (4 wide, 7 high) through the gatehouse and the wall, bridge over the fire moat
	for x in range(28, 32):
		for z in range(46, SZ):
			o = octa(x, z)
			if o > 29.4:
				continue
			b.set(x, FLOOR, z, "minecraft:polished_blackstone_bricks")
			for y in range(FLOOR + 1, FLOOR + 8):
				b.set(x, y, z, AIR)
			b.clear_zone(x, FLOOR + 1, z, x, FLOOR + 3, z)
	for z in range(46, 58):
		b.set(27, FLOOR + 7, z, pol.stairs(27, FLOOR + 7, z, "west", "top"))
		b.set(32, FLOOR + 7, z, pol.stairs(32, FLOOR + 7, z, "east", "top"))
		for x in range(28, 32):
			b.set(x, FLOOR + 8, z, pol.full(x, FLOOR + 8, z))
	# hanging portcullis (iron bars) half down, chains and soul lanterns
	for x in range(28, 32):
		for y in range(FLOOR + 4, FLOOR + 8):
			b.set(x, y, 55, "minecraft:iron_bars")
	for x in (26, 33):
		b.set(x, FLOOR + 5, 58, "minecraft:soul_lantern[hanging=false]") if b.get(x, FLOOR + 4, 58) else None
	for z in range(58, 60):
		for x in (27, 32):
			b.set(x, FLOOR + 1, z, "minecraft:polished_blackstone_wall")
	deco.marker(b, 29, FLOOR + 1, 57, deco.ENTRANCE)


# ------------------------------------------------------------------------------------------------- keep

def keep(b, m):
	brick, pol, tile = m["brick"], m["pol"], m["tile"]
	x0, z0, x1, z1 = KEEP
	for x in range(x0, x1 + 1):
		for z in range(z0, z1 + 1):
			chamfer = (x in (x0, x1)) and (z in (z0, z1))
			if chamfer:
				continue
			edge = x in (x0, x1) or z in (z0, z1) or (x in (x0 + 1, x1 - 1) and z in (z0, z1)) or \
				(z in (z0 + 1, z1 - 1) and x in (x0, x1))
			for y in range(FLOOR, KEEP_TOP + 1):
				if edge:
					s = pol.full(x, y, z) if (y - FLOOR) % 8 == 0 else brick.full(x, y, z)
				else:
					s = AIR if FLOOR < y < KEEP_TOP else "minecraft:polished_blackstone_bricks"
				b.set(x, y, z, s)
			if not edge:
				for fy in (FLOOR + 10, FLOOR + 18, FLOOR + 26):
					b.set(x, fy, z, "minecraft:warped_planks")
	# polished basalt pilasters on the long faces, chiseled bands at each floor
	for x in range(x0 + 2, x1 - 1, 3):
		for z, out in ((z0 - 1, -1), (z1 + 1, 1)):
			if 27 <= x <= 32 and out == 1:
				continue  # the great door
			for y in range(FLOOR, KEEP_TOP - 1):
				b.set(x, y, z, "minecraft:chiseled_polished_blackstone" if (y - FLOOR) % 8 == 0 else "minecraft:polished_basalt[axis=y]")
			b.set(x, KEEP_TOP - 1, z, tile.stairs(x, KEEP_TOP - 1, z, "south" if out < 0 else "north", "bottom"))
	for z in range(z0 + 2, z1 - 1, 3):
		for x, out in ((x0 - 1, -1), (x1 + 1, 1)):
			for y in range(FLOOR, KEEP_TOP - 1):
				b.set(x, y, z, "minecraft:chiseled_polished_blackstone" if (y - FLOOR) % 8 == 0 else "minecraft:polished_basalt[axis=y]")
			b.set(x, KEEP_TOP - 1, z, tile.stairs(x, KEEP_TOP - 1, z, "east" if out < 0 else "west", "bottom"))
	# basalt buttresses on the corners, rising in steps
	for (x, z, ox, oz) in ((x0, z0 + 1, -1, 0), (x1, z0 + 1, 1, 0), (x0, z1 - 1, -1, 0), (x1, z1 - 1, 1, 0)):
		for k, top in ((1, FLOOR + 24), (2, FLOOR + 14)):
			for y in range(FLOOR, top):
				b.set(x + ox * k, y, z, "minecraft:polished_basalt[axis=y]")
			b.set(x + ox * k, top, z, tile.stairs(x + ox * k, top, z, "east" if ox < 0 else "west", "bottom"))
	# great door onto the arena (south face), tall windows
	b.opening(27, z1, "east", FLOOR + 1, 6, 8, 1, "south", pol, arch="pointed")
	b.clear_zone(27, FLOOR + 1, z1 - 2, 32, FLOOR + 4, z1 + 1)
	for y0 in (FLOOR + 12, FLOOR + 20, FLOOR + 28):
		for x in (26, 33):
			for y in range(y0, y0 + 4):
				b.set(x, y, z1, "minecraft:cyan_stained_glass_pane" if b.hrand(x, y, z1, 3) < 0.4 else AIR)
				b.set(x, y, z0, AIR)
	# the crown: horned battlements and the soul beacon
	for x in range(x0 - 1, x1 + 2):
		for z in range(z0 - 1, z1 + 2):
			ring = x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)
			if ring and not (x in (x0 - 1, x1 + 1) and z in (z0 - 1, z1 + 1)):
				b.set(x, KEEP_TOP, z, tile.stairs(x, KEEP_TOP, z, inward(x, z, ((x0 + x1) / 2, (z0 + z1) / 2)), "top"))
				if (x + z) % 2 == 0:
					b.set(x, KEEP_TOP + 1, z, "minecraft:polished_blackstone")
					b.set(x, KEEP_TOP + 2, z, "minecraft:polished_blackstone_wall")
	cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
	for dx in range(-2, 3):
		for dz in range(-2, 3):
			b.set(cx + dx, KEEP_TOP + 1, cz + dz, "minecraft:polished_blackstone_bricks" if max(abs(dx), abs(dz)) == 2 else "minecraft:soul_soil")
			if max(abs(dx), abs(dz)) < 2:
				b.set(cx + dx, KEEP_TOP + 2, cz + dz, "minecraft:soul_fire")
	for (dx, dz) in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
		for y in range(KEEP_TOP + 1, KEEP_TOP + 7):
			b.set(cx + dx, y, cz + dz, "minecraft:polished_basalt[axis=y]" if y < KEEP_TOP + 6 else "minecraft:chiseled_polished_blackstone")
		b.set(cx + dx, KEEP_TOP + 7, cz + dz, "minecraft:soul_campfire[facing=north,lit=true,signal_fire=true]")
		b.t.nbt[(cx + dx, KEEP_TOP + 7, cz + dz)] = deco.N.Compound(id=deco.N.String("minecraft:campfire"))
	# ground floor: the reliquary of the champion (treasure facing the arena), soul campfires
	for x in range(x0 + 3, x1 - 2):
		b.set(x, FLOOR + 1, z0 + 2, "minecraft:polished_blackstone_bricks")
	b.set(29, FLOOR + 1, z1 - 3, "minecraft:polished_blackstone_bricks")
	deco.chest(b, 29, FLOOR + 2, z1 - 3, "south", "nether_treasure")
	b.set(30, FLOOR + 1, z1 - 3, "minecraft:polished_blackstone_bricks")
	b.set(30, FLOOR + 2, z1 - 3, "minecraft:wither_skeleton_skull[rotation=8]")
	b.t.nbt[(30, FLOOR + 2, z1 - 3)] = deco.skull_nbt()
	for x in (26, 33):
		b.set(x, FLOOR + 2, z0 + 2, "minecraft:soul_campfire[facing=south,lit=true,signal_fire=false]")
		b.t.nbt[(x, FLOOR + 2, z0 + 2)] = deco.N.Compound(id=deco.N.String("minecraft:campfire"))
	for x in (25, 34):
		for y in range(FLOOR + 1, FLOOR + 9):
			b.set(x, y, z1 - 2, "minecraft:polished_basalt[axis=y]")
	deco.hanging_lantern(b, 29, FLOOR + 7, 9, 2, soul=True)
	b.protect(x0, FLOOR, z0, x1, FLOOR + 9, z1)


# ------------------------------------------------------------------------------------------------ arena

def arena(b, m):
	cx, cz = ARENA_C
	for x in range(SX):
		for z in range(SZ):
			d = dist(x, z, ARENA_C)
			if d <= ARENA_R:
				b.set(x, FLOOR, z, field(x, z, d))
				for y in range(FLOOR + 1, FLOOR + 9):
					b.set(x, y, z, AIR)
			elif d <= ARENA_R + 1.0:
				b.set(x, FLOOR, z, "minecraft:polished_blackstone_bricks")
				b.set(x, FLOOR + 1, z, "minecraft:polished_blackstone_brick_wall" if (x + z) % 4 else "minecraft:polished_blackstone_bricks")
	# openings in the low ring wall: north (keep), south (gate), east and west
	for (x0, z0, x1, z1) in ((27, 17, 32, 19), (27, 41, 32, 43), (40, 28, 43, 32), (16, 28, 19, 32)):
		for x in range(x0, x1 + 1):
			for z in range(z0, z1 + 1):
				if b.get(x, FLOOR + 1, z) and "wall" in b.get(x, FLOOR + 1, z) or b.get(x, FLOOR + 1, z) == "minecraft:polished_blackstone_bricks":
					b.set(x, FLOOR + 1, z, AIR)
	# central dais (the champion's) and six bone monoliths with soul campfires
	for x in range(27, 33):
		for z in range(28, 33):
			if dist(x, z, ARENA_C) <= 2.6:
				b.set(x, FLOOR, z, "minecraft:chiseled_polished_blackstone" if dist(x, z, ARENA_C) < 1 else "minecraft:polished_blackstone")
	for k in range(6):
		a = math.radians(30 + 60 * k)
		x, z = int(round(cx + math.cos(a) * 7.5)), int(round(cz + math.sin(a) * 7.5))
		for y in range(FLOOR + 1, FLOOR + 5):
			b.set(x, y, z, "minecraft:bone_block[axis=y]" if y < FLOOR + 4 else "minecraft:polished_basalt[axis=y]")
		b.set(x, FLOOR + 5, z, "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false]")
		b.t.nbt[(x, FLOOR + 5, z)] = deco.N.Compound(id=deco.N.String("minecraft:campfire"))
		deco.skull(b, x + (1 if math.cos(a) > 0 else -1), FLOOR + 1, z, "skeleton_skull")
	deco.marker(b, 29, FLOOR + 1, 30, deco.BOSS)
	for k in range(6):
		a = math.radians(60 * k)
		x, z = int(round(cx + math.cos(a) * 5)), int(round(cz + math.sin(a) * 5))
		deco.marker(b, x, FLOOR + 1, z, deco.MINION)
	b.clear_zone(int(cx - 9), FLOOR + 1, int(cz - 9), int(cx + 9), FLOOR + 4, int(cz + 9))
	b.protect(int(cx - 12), FLOOR, int(cz - 12), int(cx + 12), FLOOR, int(cz + 12))


def field(x, z, d):
	if 9.5 < d <= ARENA_R:
		return "minecraft:soul_soil" if (x + z) % 3 else "minecraft:bone_block[axis=y]"
	if 5.6 < d <= 6.6:
		return "minecraft:polished_blackstone_bricks"
	return "minecraft:polished_basalt[axis=y]" if (x * 3 + z) % 5 else "minecraft:smooth_basalt"


def courtyard(b, m):
	"""Processional ways (gate -> arena -> keep) lit by soul lanterns, bone piles, basalt spikes, eternal soul fires."""
	cx, cz = ARENA_C
	for (z0, z1) in ((15, 18), (42, 51)):
		for z in range(z0, z1 + 1):
			for x in range(27, 33):
				b.set(x, FLOOR, z, "minecraft:polished_blackstone_bricks" if x in (27, 32) else "minecraft:polished_blackstone")
				b.clear_zone(x, FLOOR + 1, z, x, FLOOR + 3, z)
		for z in range(z0, z1 + 1, 3):
			for x in (26, 33):
				b.set(x, FLOOR + 1, z, "minecraft:polished_blackstone_wall")
				b.set(x, FLOOR + 2, z, "minecraft:polished_blackstone_wall")
				deco.lantern(b, x, FLOOR + 3, z, soul=True)
	rnd = b.rng
	placed = 0
	tries = 0
	while placed < 26 and tries < 600:
		tries += 1
		x, z = rnd.randrange(8, 52), rnd.randrange(8, 52)
		d = dist(x, z, ARENA_C)
		if not (ARENA_R + 2.5 < d) or octa(x, z) > 22.5 or (x, FLOOR + 1, z) in b.keep_clear:
			continue
		if not b.is_air(x, FLOOR + 1, z) or not (b.get(x, FLOOR, z) or "").startswith("minecraft:soul"):
			continue
		kind = rnd.random()
		if kind < 0.45:
			axis = rnd.choice("xyz")
			b.set(x, FLOOR + 1, z, f"minecraft:bone_block[axis={axis}]")
			if rnd.random() < 0.5 and b.is_air(x + 1, FLOOR + 1, z):
				b.set(x + 1, FLOOR + 1, z, f"minecraft:bone_block[axis={rnd.choice('xz')}]")
			if rnd.random() < 0.4:
				deco.skull(b, x, FLOOR + 2, z, "skeleton_skull")
		elif kind < 0.7:
			h = 2 + int(rnd.random() * 4)
			b.set(x, FLOOR + 1, z, "minecraft:smooth_basalt")
			for y in range(FLOOR + 2, FLOOR + 1 + h):
				b.set(x, y, z, "minecraft:basalt[axis=y]")
		elif kind < 0.85:
			b.set(x, FLOOR + 1, z, "minecraft:soul_fire")
		else:
			b.set(x, FLOOR + 1, z, rnd.choice(["minecraft:warped_roots", "minecraft:nether_sprouts", "minecraft:crimson_roots"]))
		placed += 1


# ---------------------------------------------------------------------------------------- side buildings

def hall(b, m, x0, z0, x1, z1, top, roof_axis):
	brick, pol, tile = m["brick"], m["pol"], m["tile"]
	for x in range(x0, x1 + 1):
		for z in range(z0, z1 + 1):
			edge = x in (x0, x1) or z in (z0, z1)
			b.set(x, FLOOR, z, "minecraft:polished_blackstone" if not edge else pol.full(x, FLOOR, z))
			for y in range(FLOOR + 1, top + 1):
				b.set(x, y, z, (pol.full(x, y, z) if y == top else brick.full(x, y, z)) if edge else AIR)
	b.gable_roof(x0, z0, x1, z1, top + 1, roof_axis, tile, overhang=1, fill=brick, under="minecraft:polished_blackstone_bricks")


def chapel(b, m):
	"""Ossuary chapel (east): bone pillars, skull niches, the rare reliquary chest; postern to the outside."""
	x0, z0, x1, z1 = 42, 22, 50, 36
	hall(b, m, x0, z0, x1, z1, FLOOR + 7, "z")
	for z in range(z0 + 2, z1 - 1, 3):
		for x in (x0 + 2, x1 - 2):
			for y in range(FLOOR + 1, FLOOR + 7):
				b.set(x, y, z, "minecraft:bone_block[axis=y]")
	for z in range(z0 + 1, z1):
		for x in (x0, x1):
			if z % 2 == 0:
				b.set(x, FLOOR + 3, z, "minecraft:skeleton_wall_skull[facing=" + ("east" if x == x0 else "west") + "]")
				b.t.nbt[(x, FLOOR + 3, z)] = deco.skull_nbt()
	deco.chest(b, 46, FLOOR + 1, z0 + 1, "south", "nether_rare")
	deco.candles(b, 45, FLOOR + 1, z0 + 1, 4, color="cyan")
	deco.candles(b, 47, FLOOR + 1, z0 + 1, 3, color="cyan")
	# door to the arena (west) and postern through the outer wall (east)
	for z in range(28, 32):
		for y in range(FLOOR + 1, FLOOR + 5):
			b.set(x0, y, z, AIR)
	b.clear_zone(x0 - 1, FLOOR + 1, 28, x0 + 1, FLOOR + 3, 31)
	for x in range(x1, SX):
		for z in (29, 30):
			if octa(x, z) <= 29.4:
				b.set(x, FLOOR, z, "minecraft:polished_blackstone_bricks")
				for y in range(FLOOR + 1, FLOOR + 4):
					b.set(x, y, z, AIR)
				b.clear_zone(x, FLOOR + 1, z, x, FLOOR + 2, z)


def foundry(b, m):
	"""Blaze foundry (west): a caged brazier holding the blaze spawner, anvils, common loot."""
	x0, z0, x1, z1 = 9, 22, 17, 36
	hall(b, m, x0, z0, x1, z1, FLOOR + 7, "z")
	cx, cz = 13, 29
	for dx in range(-1, 2):
		for dz in range(-1, 2):
			b.set(cx + dx, FLOOR + 1, cz + dz, "minecraft:magma_block" if (dx, dz) != (0, 0) else "minecraft:polished_blackstone")
			if (dx, dz) != (0, 0):
				b.set(cx + dx, FLOOR + 2, cz + dz, "minecraft:iron_bars" if dx == 0 or dz == 0 else "minecraft:polished_blackstone_wall")
	deco.spawner(b, cx, FLOOR + 2, cz, "minecraft:blaze", count=2, max_nearby=4)
	for dx in range(-1, 2):
		for dz in range(-1, 2):
			b.set(cx + dx, FLOOR + 3, cz + dz, "minecraft:polished_blackstone_slab[type=bottom]")
	b.set(11, FLOOR + 1, 24, "minecraft:chipped_anvil[facing=east]")
	b.set(15, FLOOR + 1, 24, "minecraft:blast_furnace[facing=south,lit=false]")
	b.set(11, FLOOR + 1, 34, "minecraft:lava_cauldron")
	deco.chest(b, 15, FLOOR + 1, 34, "north", "nether_common")
	deco.chest(b, 11, FLOOR + 1, 34 - 1, "east", "nether_rare", kind="barrel")
	for z in range(28, 32):
		for y in range(FLOOR + 1, FLOOR + 5):
			b.set(x1, y, z, AIR)
	b.clear_zone(x1 - 1, FLOOR + 1, 28, x1 + 1, FLOOR + 3, 31)


# ------------------------------------------------------------------------------------------------- decay

def ruin_it(b, m):
	# the west curtain wall is breached down to the platform (second way in), the keep lost a corner of its crown,
	# the north-east tower its brazier, the chapel roof fell in
	decay.breach(b, 5, FLOOR + 6, 30, 3.0, 7.5, 4.0, roughness=0.3)
	decay.breach(b, 9, FLOOR + 4, 31, 1.6, 3.4, 2.2, roughness=0.2)  # ... and into the foundry behind it
	decay.breach(b, 36, KEEP_TOP + 2, 3, 3.5, 6.0, 3.5, roughness=0.4)
	decay.breach(b, 49, TOWER_TOP + 1, 10, 4.0, 4.0, 4.0)
	decay.breach(b, 46, FLOOR + 10, 33, 4.0, 3.0, 4.0)
	decay.breach(b, 52, WALL_TOP, 30, 3.0, 3.0, 4.5)
	decay.erode(b, 0.22, top_bias=1.0, y0=FLOOR + 3, match=lambda s: "blackstone" in s or "basalt" in s)
	decay.stabilize(b)
	decay.cleanup(b)
	decay.rubble(b, m["rubble"], keep=0.35, scatter=2.0, top=m["brick"], max_height=3)
	decay.cleanup(b)
	# rubble ramp in the west breach so it can be climbed
	for x in range(1, 9):
		for z in range(28, 33):
			top = FLOOR + max(0, 2 - abs(x - 5))
			for y in range(FLOOR + 1, top + 1):
				if b.empty(x, y, z) or (b.get(x, y, z) or "").endswith("soul_fire"):
					b.set(x, y, z, m["rubble"].full(x, y, z))
			if (b.get(x, FLOOR + 1, z) or "").endswith("soul_fire"):
				b.set(x, FLOOR + 1, z, AIR)  # the fallen wall smothered the fire moat here


def life(b, m):
	veg.floor_cover(b, [("minecraft:warped_roots", 2), ("minecraft:nether_sprouts", 2), ("minecraft:warped_fungus", 1)],
		density=0.1, region=((0, 0, 0), (SX - 1, FLOOR + 2, SZ - 1)), sky=True, need=lambda s: "soul_soil" in s)
	veg.replace_surface(b, {"minecraft:blackstone": "minecraft:warped_nylium"}, density=0.12, region=((0, 0, 0), (SX - 1, FLOOR, SZ - 1)))
	veg.hanging(b, "minecraft:weeping_vines", density=0.06, region=((0, FLOOR + 4, 0), (SX - 1, SY - 1, SZ - 1)),
		max_len=3, plant="minecraft:weeping_vines_plant")
	veg.cobwebs(b, density=0.08, region=((0, FLOOR + 1, 0), (SX - 1, FLOOR + 9, SZ - 1)))
	decay.cleanup(b)


if __name__ == "__main__":
	import render
	print(render.render_ruin(build()))
