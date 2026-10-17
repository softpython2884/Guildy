"""
Autel infernal (tier 4, Nether: nether wastes, crimson forest).

An octagonal sanctum of polished blackstone and red nether bricks on a stepped platform ringed by a lava moat.
The way in is a bridge over the moat between two horned pylons (south); the octagon (25 across) is the arena of
the warlord: polished floor with a glowing sigil, eight pillars carrying a broken ring roof open on the sky of the
Nether, eternal fire braziers. The altar stands at the north end, the back door behind it leads to a balcony and a
second bridge (second way in / out). Two wings: a forge (magma cube spawner) and a barracks.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from ruinlib import build as K, decay, veg, deco, shapes  # noqa: E402
from ruinlib.build import Mat, Mix, AIR  # noqa: E402
from ruinlib.ruin import Ruin, Piece, rules  # noqa: E402

ID = "autel_infernal"
SEED = 5501
SX, SY, SZ = 44, 26, 44
C = 21.5
FLOOR = 3  # sanctum floor layer (players walk at FLOOR + 1)
WALL_TOP = FLOOR + 9


def build():
	b = K.new(SX, SY, SZ, SEED, ground=0, name=ID)
	m = materials()
	platform(b, m)
	sanctum(b, m)
	wings(b, m)
	approach(b, m)
	ruin_it(b, m)
	life(b, m)
	# the cavern the sanctum needs when the site is inside a netherrack mass (no effect in open space)
	b.carve_dome(C, C, 23.0, FLOOR + 1, SY - FLOOR - 2)
	b.open_clear_zones()
	shapes.fix(b.t)
	ruin = Ruin(ID, "Autel infernal", "nether", 4, ["minecraft:nether_wastes", "minecraft:crimson_forest"],
		"beard_box", [Piece("main", b.t, (0, 0, 0))], 0, 33, terrain="minecraft:netherrack", processors=[rules(
			("minecraft:polished_blackstone_bricks", "minecraft:cracked_polished_blackstone_bricks", 0.12),
			("minecraft:nether_bricks", "minecraft:cracked_nether_bricks", 0.1))])
	ruin.plan_levels = [(FLOOR + 1, "Plan : sanctuaire (arène), forge et caserne")]
	ruin.cut_level = FLOOR + 5
	return ruin


def materials():
	return {
		"brick": Mat("polished_blackstone_brick", cracked="minecraft:cracked_polished_blackstone_bricks", crack=0.22,
			seed=SEED, extra=[("minecraft:blackstone", 0.08), ("minecraft:polished_blackstone", 0.06)]),
		"red": Mat("red_nether_brick", seed=SEED + 1, extra=[("minecraft:nether_bricks", 0.15)]),
		"nb": Mat("nether_brick", cracked="minecraft:cracked_nether_bricks", crack=0.2, seed=SEED + 2),
		"pol": Mat("polished_blackstone", seed=SEED + 3),
		"rough": Mat("blackstone", seed=SEED + 4, extra=[("minecraft:basalt[axis=y]", 0.1), ("minecraft:netherrack", 0.08)]),
		"rubble": Mix([("minecraft:blackstone", 4), ("minecraft:polished_blackstone_bricks", 2),
			("minecraft:cracked_polished_blackstone_bricks", 2), ("minecraft:netherrack", 2), ("minecraft:nether_bricks", 1)],
			seed=SEED + 5),
	}


def dist(x, z):
	return math.hypot(x - C, z - C)


def octa(x, z):
	"""Octagonal distance (regular-ish octagon)."""
	dx, dz = abs(x - C), abs(z - C)
	return max(dx, dz, (dx + dz) / 1.414)


# -------------------------------------------------------------------------------------------- platform

def platform(b, m):
	rough, brick = m["rough"], m["brick"]
	for x in range(SX):
		for z in range(SZ):
			o = octa(x, z)
			if o > 20.6:
				continue
			for y in range(0, FLOOR):
				if o > 20.6 - (FLOOR - y):  # stepped outer edge
					continue
				b.set(x, y, z, rough.full(x, y, z) if y < FLOOR - 1 else brick.full(x, y, z))
			# outer step stairs
			edge_y = FLOOR - 1 - int(max(0, o - 18.6))
			if o > 18.6 and edge_y >= 0:
				b.set(x, edge_y, z, brick.stairs(x, edge_y, z, _inward(x, z), "bottom"))
	# lava moat: ring 15 < o <= 17, one deep, closed bottom and sides
	for x in range(SX):
		for z in range(SZ):
			o = octa(x, z)
			if 15.2 < o <= 17.2:
				b.set(x, FLOOR - 1, z, "minecraft:lava")
				b.set(x, FLOOR - 2, z, rough.full(x, FLOOR - 2, z))
				b.set(x, FLOOR, z, AIR)
			elif 14.2 < o <= 15.2 or 17.2 < o <= 18.2:
				b.set(x, FLOOR - 1, z, "minecraft:polished_blackstone")
				b.set(x, FLOOR, z, "minecraft:polished_blackstone_brick_wall" if b.hrand(x, 0, z, 3) < 0.7 else AIR)
	# inner terrace (between the moat and the sanctum)
	for x in range(SX):
		for z in range(SZ):
			o = octa(x, z)
			if o <= 14.2:
				b.set(x, FLOOR - 1, z, brick.full(x, FLOOR - 1, z))
				b.set(x, FLOOR, z, "minecraft:polished_blackstone" if o > 13.2 else "minecraft:polished_blackstone_bricks")
			if o <= 18.2:
				for y in range(FLOOR + 1, FLOOR + 6):
					if b.get(x, y, z) is None:
						b.set(x, y, z, AIR)


def _inward(x, z):
	dx, dz = C - x, C - z
	if abs(dx) >= abs(dz):
		return "east" if dx > 0 else "west"
	return "south" if dz > 0 else "north"


# --------------------------------------------------------------------------------------------- sanctum

def sanctum(b, m):
	brick, red, pol, nb = m["brick"], m["red"], m["pol"], m["nb"]
	# octagonal wall 12.2 < o <= 13.2, floor inside
	for x in range(SX):
		for z in range(SZ):
			o = octa(x, z)
			if 12.2 < o <= 13.2:
				for y in range(FLOOR + 1, WALL_TOP + 1):
					band = (y - FLOOR) % 4 == 0
					b.set(x, y, z, red.full(x, y, z) if band else brick.full(x, y, z))
			elif o <= 12.2:
				b.set(x, FLOOR, z, floor_pattern(x, z))
				for y in range(FLOOR + 1, WALL_TOP + 3):
					b.set(x, y, z, AIR)
	# pilasters outside every corner of the octagon, with horns
	for a in range(0, 360, 45):
		ra = math.radians(a + 22.5)
		x, z = int(round(C + math.cos(ra) * 13.8)), int(round(C + math.sin(ra) * 13.8))
		for y in range(FLOOR + 1, WALL_TOP + 3):
			b.set(x, y, z, pol.full(x, y, z))
		horn(b, x, WALL_TOP + 3, z, ra)
	# two great horns over the altar (north-west and north-east corners) and two smaller ones at the gate
	big_horn(b, 12, 12, WALL_TOP + 1, -1, -1)
	big_horn(b, 30, 12, WALL_TOP + 1, 1, -1)
	# ring roof (o 9..13) on eight pillars, open in the middle
	for x in range(SX):
		for z in range(SZ):
			o = octa(x, z)
			if 9.2 < o <= 13.2:
				b.set(x, WALL_TOP + 1, z, nb.full(x, WALL_TOP + 1, z))
				if o <= 10.2:
					b.set(x, WALL_TOP + 2, z, nb.stairs(x, WALL_TOP + 2, z, _outward(x, z), "bottom"))
					b.set(x, WALL_TOP, z, nb.stairs(x, WALL_TOP, z, _outward(x, z), "top"))
				elif o > 12.2:
					b.set(x, WALL_TOP + 2, z, "minecraft:red_nether_brick_wall" if (x + z) % 2 else AIR)
	for a in range(0, 360, 45):
		ra = math.radians(a + 22.5)  # off the north / south axis: the doors stay free
		x, z = int(math.floor(C + math.cos(ra) * 10.2 + 0.5)), int(math.floor(C + math.sin(ra) * 10.2 + 0.5))
		for y in range(FLOOR + 1, WALL_TOP + 1):
			b.set(x, y, z, "minecraft:polished_basalt[axis=y]" if 1 < y - FLOOR < WALL_TOP - FLOOR - 1 else "minecraft:chiseled_polished_blackstone")
		for (dx, dz, f) in ((1, 0, "west"), (-1, 0, "east"), (0, 1, "north"), (0, -1, "south")):
			if b.is_air(x + dx, FLOOR + 1, z + dz):
				b.set(x + dx, FLOOR + 1, z + dz, pol.stairs(x + dx, FLOOR + 1, z + dz, f, "bottom"))
	# windows: tall slits in the walls between pillars
	for a in range(0, 360, 45):
		if a in (90, 270):
			continue  # doors (south / north in this frame: 90 = +z = south)
		ra = math.radians(a)
		x, z = int(round(C + math.cos(ra) * 12.7)), int(round(C + math.sin(ra) * 12.7))
		for y in range(FLOOR + 3, FLOOR + 7):
			b.set(x, y, z, "minecraft:iron_bars" if b.hrand(x, y, z, 2) < 0.5 else AIR)
	# south gate (3 wide, 6 high) and north back door (behind the altar)
	for zz in (34, 33, 35):
		pass
	for x in range(20, 24):
		for z in range(33, 36):
			for y in range(FLOOR + 1, FLOOR + 7):
				if octa(x, z) > 12.0:
					b.set(x, y, z, AIR)
	for z in range(33, 36):
		b.set(19, FLOOR + 6, z, brick.stairs(19, FLOOR + 6, z, "east", "top"))
		b.set(24, FLOOR + 6, z, brick.stairs(24, FLOOR + 6, z, "west", "top"))
	b.clear_zone(20, FLOOR + 1, 32, 23, FLOOR + 3, 36)
	for x in range(21, 23):
		for z in range(7, 10):
			for y in range(FLOOR + 1, FLOOR + 4):
				if octa(x, z) > 12.0:
					b.set(x, y, z, AIR)
	b.clear_zone(21, FLOOR + 1, 7, 22, FLOOR + 2, 10)
	# the altar (north): stepped dais, obsidian slab, crying obsidian, eternal fires, the treasure behind it
	for x in range(16, 28):
		for z in range(10, 14):
			if octa(x, z) <= 12.2:
				b.set(x, FLOOR + 1, z, "minecraft:polished_blackstone_bricks" if z < 13 else
					brick.stairs(x, FLOOR + 1, z, "north", "bottom"))
	for x in range(19, 25):
		b.set(x, FLOOR + 2, 11, "minecraft:obsidian" if x in (19, 24) else "minecraft:crying_obsidian")
		b.set(x, FLOOR + 3, 11, "minecraft:polished_blackstone_slab[type=bottom]")
	for x in (18, 25):
		brazier(b, x, FLOOR + 2, 12)
	deco.chest(b, 23, FLOOR + 2, 10, "south", "nether_treasure")
	b.set(20, FLOOR + 2, 10, "minecraft:lava_cauldron")
	deco.skull(b, 21, FLOOR + 4, 11, "wither_skeleton_skull", rotation=0)
	deco.skull(b, 22, FLOOR + 4, 11, "skeleton_skull", rotation=0)
	# braziers around the arena (on the pillars' bases), shroomlight lamps hanging under the ring roof
	for a in range(0, 360, 90):
		ra = math.radians(a + 45)
		x, z = int(math.floor(C + math.cos(ra) * 7.5 + 0.5)), int(math.floor(C + math.sin(ra) * 7.5 + 0.5))
		brazier(b, x, FLOOR + 1, z)
	for a in range(22, 360, 45):
		ra = math.radians(a)
		x, z = int(round(C + math.cos(ra) * 11.5)), int(round(C + math.sin(ra) * 11.5))
		b.set(x, WALL_TOP, z, "minecraft:iron_chain[axis=y]")
		b.set(x, WALL_TOP - 1, z, "minecraft:shroomlight")
	# markers
	deco.marker(b, 21, FLOOR + 1, 22, deco.BOSS)
	for (x, z) in ((16, 18), (27, 18), (16, 27), (27, 27), (21, 29)):
		deco.marker(b, x, FLOOR + 1, z, deco.MINION)
	b.clear_zone(13, FLOOR + 1, 14, 30, FLOOR + 4, 31)
	b.protect(8, 0, 8, 35, FLOOR + 1, 35)


def floor_pattern(x, z):
	d = dist(x, z)
	a = math.degrees(math.atan2(z - C, x - C)) % 360
	# the glowing sigil: shroomlight (not magma: the warlord and his guard must not burn on it)
	if d < 1.6:
		return "minecraft:shroomlight"
	if 4.4 < d < 5.6 and int(a / 30) % 2 == 0:
		return "minecraft:shroomlight"
	if min(a % 60, 60 - a % 60) < 5 and 2 < d < 8:
		return "minecraft:red_nether_bricks"
	if d > 10.5:
		return "minecraft:polished_blackstone"
	return "minecraft:polished_blackstone_bricks" if (x + z) % 3 else "minecraft:chiseled_polished_blackstone"


def horn(b, x, y, z, ra):
	"""A curved horn of blackstone stairs / walls on top of a pilaster, pointing outward."""
	dx, dz = math.cos(ra), math.sin(ra)
	sx, sz = (1 if dx > 0.35 else -1 if dx < -0.35 else 0), (1 if dz > 0.35 else -1 if dz < -0.35 else 0)
	b.set(x, y, z, "minecraft:polished_blackstone")
	b.set(x + sx, y + 1, z + sz, "minecraft:polished_blackstone_wall")
	b.set(x + sx, y, z + sz, "minecraft:polished_blackstone")
	b.set(x + 2 * sx, y + 2, z + 2 * sz, "minecraft:blackstone_wall")
	b.set(x + 2 * sx, y + 1, z + 2 * sz, "minecraft:polished_blackstone_wall")


def _outward(x, z):
	return K.OPP[_inward(x, z)]


def big_horn(b, x, z, y0, sx, sz):
	"""
	A great curved horn on a corner of the sanctum: a 2 x 2 root that thins and bends outward (one axis at a time,
	so every block touches the one below or beside it), then up, ending in a wall tip with a hanging soul lantern.
	"""
	path = [(0, 0, 2), (0, 0, 2), (0, 0, 2), (1, 0, 1), (1, 1, 1), (2, 1, 1), (2, 2, 1), (3, 2, 1), (3, 3, 1),
		(3, 3, 1), (3, 3, 0)]
	prev = None
	for i, (ox, oz, w) in enumerate(path):
		y = y0 + i
		cx, cz = x + sx * ox, z + sz * oz
		if w == 2:
			for dx in (0, -sx):
				for dz in (0, -sz):
					b.set(cx + dx, y, cz + dz, "minecraft:polished_blackstone" if i % 3 else "minecraft:red_nether_bricks")
		elif w == 1:
			b.set(cx, y, cz, "minecraft:polished_blackstone" if i % 3 else "minecraft:chiseled_polished_blackstone")
			if prev is not None and prev != (cx, cz):
				b.set(prev[0], y, prev[1], "minecraft:polished_blackstone")  # elbow: keeps the horn face-connected
		else:
			b.set(cx, y, cz, "minecraft:polished_blackstone_wall")
		prev = (cx, cz)
	tx, tz = x + sx * 3, z + sz * 3
	b.set(tx + sx, y0 + 8, tz, "minecraft:polished_blackstone_wall")
	b.set(tx + sx, y0 + 7, tz, "minecraft:iron_chain[axis=y]")
	b.set(tx + sx, y0 + 6, tz, "minecraft:soul_lantern[hanging=true]")


def brazier(b, x, y, z):
	b.set(x, y, z, "minecraft:polished_blackstone_bricks")
	b.set(x, y + 1, z, "minecraft:netherrack")
	b.set(x, y + 2, z, "minecraft:fire[age=0,east=false,north=false,south=false,up=false,west=false]")


# ----------------------------------------------------------------------------------------------- wings

def wings(b, m):
	"""Forge (west) and barracks (east), small vaulted halls against the octagon."""
	brick, red, nb = m["brick"], m["red"], m["nb"]
	for (x0, x1, door_x) in ((3, 9, 9), (34, 40, 34)):
		z0, z1 = 18, 25
		for x in range(x0, x1 + 1):
			for z in range(z0, z1 + 1):
				edge = x in (x0, x1) or z in (z0, z1)
				for y in range(FLOOR - 1, FLOOR + 7):
					if y < FLOOR:
						b.set(x, y, z, brick.full(x, y, z))
					elif y == FLOOR:
						b.set(x, y, z, "minecraft:polished_blackstone" if not edge else brick.full(x, y, z))
					elif edge:
						b.set(x, y, z, red.full(x, y, z) if y == FLOOR + 6 else nb.full(x, y, z))
					else:
						b.set(x, y, z, AIR)
		b.gable_roof(x0, z0, x1, z1, FLOOR + 7, "x", nb, overhang=0, under="minecraft:nether_bricks")
		# passage through the moat ring and the octagon wall
		step = 1 if door_x < C else -1
		x = door_x
		while octa(x, 21) > 11.5:
			for z in (21, 22):
				b.set(x, FLOOR, z, "minecraft:polished_blackstone_bricks")
				for y in range(FLOOR + 1, FLOOR + 4):
					b.set(x, y, z, AIR)
				b.set(x, FLOOR - 1, z, brick.full(x, FLOOR - 1, z))
				b.set(x, FLOOR + 4, z, nb.full(x, FLOOR + 4, z))
			for z in (20, 23):
				for y in range(FLOOR, FLOOR + 5):
					if octa(x, z) > 13.2:
						b.set(x, y, z, nb.full(x, y, z))
			b.clear_zone(x, FLOOR + 1, 21, x, FLOOR + 2, 22)
			x += step
	# forge (west): anvils, blast furnaces, lava cauldron, magma cube spawner
	b.set(4, FLOOR + 1, 19, "minecraft:blast_furnace[facing=east,lit=false]")
	b.set(4, FLOOR + 1, 24, "minecraft:blast_furnace[facing=east,lit=false]")
	b.set(5, FLOOR + 1, 19, "minecraft:damaged_anvil[facing=north]")
	b.set(4, FLOOR + 1, 21, "minecraft:lava_cauldron")
	deco.spawner(b, 6, FLOOR + 1, 23, "minecraft:magma_cube", count=2, max_nearby=4)
	deco.chest(b, 8, FLOOR + 1, 19, "south", "nether_common")
	deco.chest(b, 4, FLOOR + 1, 22, "east", "nether_rare", kind="barrel")
	# barracks (east): bunks of slabs, a weapon rack, chests
	for z in (19, 24):
		for x in (36, 37, 38):
			b.set(x, FLOOR + 1, z, "minecraft:crimson_slab[type=bottom]")
	b.set(39, FLOOR + 1, 21, "minecraft:polished_blackstone_wall")
	b.set(39, FLOOR + 2, 21, "minecraft:iron_chain[axis=y]")
	deco.chest(b, 39, FLOOR + 1, 19, "west", "nether_common")
	deco.chest(b, 39, FLOOR + 1, 24, "west", "nether_rare")
	deco.lantern(b, 37, FLOOR + 5, 21, hanging=True)
	b.set(37, FLOOR + 6, 21, "minecraft:iron_chain[axis=y]")


# --------------------------------------------------------------------------------------------- approach

def approach(b, m):
	brick, pol = m["brick"], m["pol"]
	# south bridge over the moat and the outer steps
	for z in range(33, SZ):
		for x in range(20, 24):
			o = octa(x, z)
			if o > 20.6:
				continue
			b.set(x, FLOOR, z, "minecraft:polished_blackstone_bricks")
			b.set(x, FLOOR - 1, z, brick.full(x, FLOOR - 1, z))
			for y in range(FLOOR + 1, FLOOR + 4):
				b.set(x, y, z, AIR)
		for x in (19, 24):
			if octa(x, z) <= 18.6:
				b.set(x, FLOOR, z, brick.full(x, FLOOR, z))
				b.set(x, FLOOR + 1, z, "minecraft:nether_brick_fence")
		b.clear_zone(20, FLOOR + 1, z, 23, FLOOR + 2, z)
	for z in range(40, SZ):
		for x in range(20, 24):
			b.set(x, FLOOR, z, brick.stairs(x, FLOOR, z, "north", "bottom") if z == 40 else AIR)
	# tapering horned pylons at the bridge head
	for x0 in (16, 25):
		for x in range(x0, x0 + 3):
			for z in range(36, 39):
				for y in range(FLOOR, FLOOR + 5):
					b.set(x, y, z, pol.full(x, y, z) if y != FLOOR + 3 else "minecraft:red_nether_bricks")
				ring = x in (x0, x0 + 2) or z in (36, 38)
				if ring:
					f = "east" if x == x0 else "west" if x == x0 + 2 else "south" if z == 36 else "north"
					b.set(x, FLOOR + 5, z, pol.stairs(x, FLOOR + 5, z, f, "bottom") if (x - x0) % 2 or (z - 36) % 2 else
						"minecraft:polished_blackstone_wall")
		for y in range(FLOOR + 5, FLOOR + 9):
			b.set(x0 + 1, y, 37, "minecraft:chiseled_polished_blackstone" if y == FLOOR + 8 else pol.full(x0 + 1, y, 37))
		big_horn(b, x0 + 1, 37, FLOOR + 9, -1 if x0 < C else 1, 1)
	deco.marker(b, 21, FLOOR, 41, deco.ENTRANCE)
	# north balcony and second bridge (behind the altar)
	for z in range(0, 9):
		for x in (21, 22):
			if octa(x, z) <= 20.6:
				b.set(x, FLOOR, z, "minecraft:polished_blackstone_bricks")
				for y in range(FLOOR + 1, FLOOR + 4):
					b.set(x, y, z, AIR)
		for x in (20, 23):
			if octa(x, z) <= 18.6:
				b.set(x, FLOOR + 1, z, "minecraft:nether_brick_fence")
		b.clear_zone(21, FLOOR + 1, z, 22, FLOOR + 2, z)


# ------------------------------------------------------------------------------------------------- decay

def ruin_it(b, m):
	decay.breach(b, 32, WALL_TOP, 27, 4.5, 4.5, 4.5, roughness=0.4)
	decay.breach(b, 21, WALL_TOP + 1, 33, 5.0, 2.0, 3.0, roughness=0.4)
	decay.breach(b, 12, WALL_TOP + 1, 30, 3.5, 4.5, 3.5, roughness=0.4)
	decay.breach(b, 33, FLOOR + 6, 31, 3.0, 4.0, 3.0)
	decay.erode(b, 0.2, top_bias=1.0, y0=FLOOR + 2, match=lambda s: "brick" in s or "blackstone" in s)
	decay.stabilize(b)
	decay.cleanup(b)
	decay.rubble(b, m["rubble"], keep=0.35, scatter=1.8, top=m["brick"], max_height=3)
	decay.cleanup(b)


def life(b, m):
	veg.floor_cover(b, [("minecraft:crimson_roots", 3), ("minecraft:crimson_fungus", 1), ("minecraft:nether_sprouts", 1)],
		density=0.12, region=((0, 0, 0), (SX - 1, FLOOR + 1, SZ - 1)), sky=True,
		need=lambda s: "netherrack" in s or "nylium" in s)
	veg.replace_surface(b, {"minecraft:blackstone": "minecraft:crimson_nylium"}, density=0.2,
		region=((0, 0, 0), (SX - 1, FLOOR - 1, SZ - 1)))
	veg.floor_cover(b, [("minecraft:crimson_roots", 3), ("minecraft:crimson_fungus", 1)], density=0.3,
		region=((0, 0, 0), (SX - 1, FLOOR + 1, SZ - 1)), sky=True, need=lambda s: "nylium" in s)
	veg.hanging(b, "minecraft:weeping_vines", density=0.12, region=((0, FLOOR + 3, 0), (SX - 1, SY - 1, SZ - 1)),
		max_len=4, plant="minecraft:weeping_vines_plant")
	veg.cobwebs(b, density=0.08, region=((0, FLOOR + 1, 0), (SX - 1, FLOOR + 6, SZ - 1)))
	decay.cleanup(b)


if __name__ == "__main__":
	import render
	print(render.render_ruin(build()))
