"""
Ziggourat des sables (tier 2, Overworld: desert, badlands).

A four-tier stepped pyramid of cut sandstone with orange and blue terracotta bands, half swallowed by dunes. A
grand stairway climbs the south face to a pillared shrine (light well down into the hall). The way in is the
west gate between two pylons: a corridor with two side chambers descends into the Hall of the Pharaoh (17 x 17,
9 high, sunk under the ground, four pillars, sarcophagus dais). Tomb robbers dug a tunnel from the eastern dune
into the hall: the second way in / out.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from ruinlib import build as K, decay, veg, deco, shapes  # noqa: E402
from ruinlib.build import Mat, Mix, AIR, stairs  # noqa: E402
from ruinlib.ruin import Ruin, split_pieces, rules  # noqa: E402

ID = "ziggourat_des_sables"
SEED = 3301
SX, SY, SZ = 48, 33, 48
G = 5
C = 24
TIERS = [(18, G, G + 4), (14, G + 5, G + 8), (10, G + 9, G + 12), (6, G + 13, G + 16)]
TOP = G + 16
FEET = G - 3  # hall walking level
HALL = (16, 16, 32, 32)  # interior x0, z0, x1, z1
HALL_CEIL = G + 6


def build():
	b = K.new(SX, SY, SZ, SEED, ground=G, name=ID)
	m = materials()
	pyramid(b, m)
	stairway(b, m)
	shrine(b, m)
	gate(b, m)
	hall(b, m)
	robbers_tunnel(b, m)
	dunes(b, m)
	ruin_it(b, m)
	life(b, m)
	b.open_clear_zones()
	shapes.fix(b.t)
	pieces = split_pieces(ID, b.t, G, jigsaw_xz=(0, 0))
	ruin = Ruin(ID, "Ziggourat des sables", "overworld", 2,
		["minecraft:desert", "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands"],
		"beard_thin", pieces, G, 0, terrain="minecraft:sand", processors=[rules(
			("minecraft:cut_sandstone", "minecraft:sandstone", 0.08),
			("minecraft:smooth_sandstone", "minecraft:sandstone", 0.05))])
	ruin.plan_levels = [(G + 1, "Plan : niveau du sol"), (FEET, "Plan : salle du pharaon (arène)")]
	ruin.cut_level = G + 3
	ruin.under_cut = FEET + 3
	return ruin


def materials():
	return {
		"face": Mat("cut_sandstone", seed=SEED, alt="sandstone", extra=[("minecraft:sandstone", 0.25), ("minecraft:smooth_sandstone", 0.12)]),
		"smooth": Mat("smooth_sandstone", seed=SEED + 1, extra=[("minecraft:sandstone", 0.1)]),
		"rough": Mat("sandstone", seed=SEED + 2, extra=[("minecraft:smooth_sandstone", 0.15)]),
		"core": Mix([("minecraft:sandstone", 5), ("minecraft:smooth_sandstone", 2), ("minecraft:sand", 1)], seed=SEED + 3),
		"red": Mat("smooth_red_sandstone", seed=SEED + 4, alt="red_sandstone", extra=[("minecraft:cut_red_sandstone", 0.3)]),
		"inner": Mat("smooth_sandstone", seed=SEED + 5, extra=[("minecraft:cut_sandstone", 0.3), ("minecraft:chiseled_sandstone", 0.03)]),
		"rubble": Mix([("minecraft:sandstone", 4), ("minecraft:sand", 3), ("minecraft:cut_sandstone", 2),
			("minecraft:smooth_sandstone", 1)], seed=SEED + 6),
	}


DX = {"north": 0, "south": 0, "west": -1, "east": 1}
DZ = {"north": -1, "south": 1, "west": 0, "east": 0}


def band(y, tier_top):
	"""Decorative band just under each tier top: orange terracotta with blue lozenges."""
	return y == tier_top - 1


# ------------------------------------------------------------------------------------------------ pyramid

def pyramid(b, m):
	face, smooth, core = m["face"], m["smooth"], m["core"]
	for half, y0, y1 in TIERS:
		for x in range(C - half, C + half + 1):
			for z in range(C - half, C + half + 1):
				edge = max(abs(x - C), abs(z - C)) == half
				for y in range(y0, y1 + 1):
					if edge:
						if y == y1 - 1:
							s = "minecraft:blue_terracotta" if (x + z) % 6 == 0 else "minecraft:orange_terracotta"
						elif y == y1:
							s = smooth.full(x, y, z)
						else:
							s = face.full(x, y, z)
					else:
						s = core.full(x, y, z)
					b.set(x, y, z, s)
		# pilasters every 4 on each face, a cornice of upside-down stairs at the top
		for i in range(-half, half + 1):
			for (x, z, out) in ((C + i, C - half - 1, "north"), (C + i, C + half + 1, "south"),
				(C - half - 1, C + i, "west"), (C + half + 1, C + i, "east")):
				if i % 4 == 0 and abs(i) != half:
					for y in range(y0, y1):
						b.set(x, y, z, "minecraft:chiseled_red_sandstone" if y == y1 - 1 else m["red"].full(x, y, z))
					b.set(x, y1, z, m["red"].stairs(x, y1, z, K.OPP[out], "bottom"))
				elif i % 4 == 2 and abs(i) < half - 1:
					b.set(x - (DX[out]), y0 + 2, z - (DZ[out]), "minecraft:chiseled_sandstone")
				elif y0 == G:
					b.set(x, y0, z, face.stairs(x, y0, z, K.OPP[out], "bottom"))
	# foundations under the base (below the ground floor)
	for x in range(C - 19, C + 20):
		for z in range(C - 19, C + 20):
			for y in range(0, G):
				if b.get(x, y, z) is None:
					b.set(x, y, z, core.full(x, y, z))


def stairway(b, m):
	"""Grand stairway on the south face: 5 wide, from the sand to the top platform, between balustrades."""
	smooth = m["smooth"]
	for i in range(0, TOP - G):
		z = C + 19 - i
		y = G + 1 + i
		for x in range(C - 2, C + 3):
			b.set(x, y, z, smooth.stairs(x, y, z, "north", "bottom"))
			for h in range(1, 4):
				b.set(x, y + h, z, AIR)
			for yy in range(G, y):
				if b.empty(x, yy, z):
					b.set(x, yy, z, m["core"])
		for x in (C - 3, C + 3):
			b.set(x, y, z, m["red"].full(x, y, z))
			b.set(x, y + 1, z, m["red"].stairs(x, y + 1, z, "north", "bottom"))
			for h in range(2, 4):
				if b.get(x, y + h, z) is not None and y + h > TIERS[0][2]:
					pass
			for yy in range(G, y):
				if b.empty(x, yy, z):
					b.set(x, yy, z, m["core"])
		b.clear_zone(C - 2, y + 1, z, C + 2, y + 2, z)
	# braziers at the foot of the stairway
	for x in (C - 4, C + 4):
		b.set(x, G + 1, C + 20, "minecraft:chiseled_sandstone")
		b.set(x, G + 2, C + 20, "minecraft:campfire[facing=north,lit=false,signal_fire=false]")
		b.t.nbt[(x, G + 2, C + 20)] = deco.N.Compound(id=deco.N.String("minecraft:campfire"))


def shrine(b, m):
	"""Pillared shrine on the top platform, open toward the stairway; light well into the hall."""
	smooth, face, red = m["smooth"], m["face"], m["red"]
	x0, z0, x1, z1 = C - 4, C - 6, C + 4, C + 2
	for x in range(x0, x1 + 1):
		for z in range(z0, z1 + 1):
			b.set(x, TOP, z, "minecraft:smooth_sandstone" if (x + z) % 2 else "minecraft:cut_sandstone")
			for y in range(TOP + 1, TOP + 6):
				b.set(x, y, z, AIR)
	for x in range(x0, x1 + 1, 2):
		for z in (z0, z1):
			for y in range(TOP + 1, TOP + 5):
				b.set(x, y, z, "minecraft:chiseled_sandstone" if y in (TOP + 1, TOP + 4) else face.full(x, y, z))
	for z in range(z0 + 2, z1, 2):
		for x in (x0, x1):
			for y in range(TOP + 1, TOP + 5):
				b.set(x, y, z, "minecraft:chiseled_sandstone" if y in (TOP + 1, TOP + 4) else face.full(x, y, z))
	# back wall (north) with an altar niche
	for x in range(x0, x1 + 1):
		for y in range(TOP + 1, TOP + 5):
			b.set(x, y, z0, red.full(x, y, z0) if y > TOP + 1 else face.full(x, y, z0))
	b.set(C, TOP + 1, z0 + 1, "minecraft:chiseled_red_sandstone")
	b.set(C, TOP + 2, z0 + 1, "minecraft:decorated_pot[cracked=false,facing=south,waterlogged=false]")
	b.t.nbt[(C, TOP + 2, z0 + 1)] = deco.N.Compound(id=deco.N.String("minecraft:decorated_pot"))
	deco.candles(b, C - 1, TOP + 1, z0 + 1, 3, color="orange")
	deco.candles(b, C + 1, TOP + 1, z0 + 1, 2, color="orange")
	deco.chest(b, C + 3, TOP + 1, z0 + 1, "south", "common")
	# roof: smooth slab layer, cornice, stepped crown
	for x in range(x0 - 1, x1 + 2):
		for z in range(z0 - 1, z1 + 2):
			ring = x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)
			b.set(x, TOP + 5, z, smooth.stairs(x, TOP + 5, z, _inward(x, z, x0 - 1, x1 + 1, z0 - 1, z1 + 1), "top")
				if ring else "minecraft:smooth_sandstone")
	for k, (h, y) in enumerate(((3, TOP + 6), (2, TOP + 7))):
		for x in range(C - h, C + h + 1):
			for z in range(C - 2 - h, C - 2 + h + 1):
				b.set(x, y, z, "minecraft:cut_sandstone" if max(abs(x - C), abs(z - C + 2)) == h else "minecraft:smooth_sandstone")
	b.set(C, TOP + 8, C - 2, "minecraft:chiseled_red_sandstone")
	# light well: 3 x 3 shaft from the shrine floor to the hall ceiling, a railing of sandstone walls
	for x in range(C - 1, C + 2):
		for z in range(C - 1, C + 2):
			for y in range(HALL_CEIL, TOP + 1):
				b.set(x, y, z, AIR)
	for x in range(C - 2, C + 3):
		for z in range(C - 2, C + 3):
			if max(abs(x - C), abs(z - C)) == 2:
				b.set(x, TOP + 1, z, "minecraft:sandstone_wall")
	b.protect(x0, TOP, z0, x1, TOP + 1, z1)
	# obelisks at the corners of the platform
	for (x, z) in ((C - 6, C + 6), (C + 6, C + 6), (C - 6, C - 6), (C + 6, C - 6)):
		for y in range(TOP + 1, TOP + 6):
			b.set(x, y, z, "minecraft:chiseled_sandstone" if y == TOP + 3 else "minecraft:smooth_sandstone")
		b.set(x, TOP + 6, z, "minecraft:smooth_sandstone_slab[type=bottom]")


def _inward(x, z, x0, x1, z0, z1):
	if x == x0:
		return "east"
	if x == x1:
		return "west"
	if z == z0:
		return "south"
	return "north"


# ---------------------------------------------------------------------------------------------- the gate

def gate(b, m):
	"""West gate between two pylons, corridor descending east into the hall, two side chambers."""
	smooth, face, inner = m["smooth"], m["face"], m["inner"]
	gx = C - 18  # west face x
	# pylons
	for (z0, z1) in ((C - 6, C - 3), (C + 3, C + 6)):
		for x in range(gx - 3, gx + 1):
			for z in range(z0, z1 + 1):
				top = G + 9 - (1 if x == gx - 3 else 0)
				for y in range(G, top + 1):
					s = face.full(x, y, z)
					if y == top - 1:
						s = "minecraft:orange_terracotta"
					if y == top:
						s = smooth.full(x, y, z)
					b.set(x, y, z, s)
	# lintel over the gate
	for z in range(C - 3, C + 4):
		for x in range(gx - 2, gx + 1):
			b.set(x, G + 7, z, "minecraft:chiseled_sandstone" if z == C else smooth.full(x, G + 7, z))
			b.set(x, G + 8, z, "minecraft:orange_terracotta" if (z + x) % 3 else "minecraft:blue_terracotta")
			b.set(x, G + 9, z, smooth.slab(x, G + 9, z, "bottom"))
	# the passage under the lintel and through the base
	for x in range(gx - 3, gx + 1):
		for z in range(C - 2, C + 3):
			b.set(x, G, z, "minecraft:smooth_sandstone")
			for y in range(G + 1, G + 7):
				b.set(x, y, z, AIR)
	b.clear_zone(gx - 6, G + 1, C - 2, gx + 1, G + 3, C + 2)
	# guardian statues (sitting jackals) in front of the pylons
	for z in (C - 5, C + 5):
		statue(b, gx - 5, z)
	# corridor: 3 wide, 4 high, corbelled ceiling, descending 4 levels
	for x in range(gx + 1, HALL[0]):
		step = max(0, min(4, x - (gx + 3)))
		fy = G - step
		for z in range(C - 1, C + 2):
			if gx + 3 < x <= gx + 7:
				b.set(x, fy, z, inner.stairs(x, fy, z, "west", "bottom"))
				b.set(x, fy - 1, z, inner.full(x, fy - 1, z))
			else:
				b.set(x, fy, z, "minecraft:smooth_sandstone")
			for y in range(fy + 1, fy + 5):
				b.set(x, y, z, AIR)
			b.clear_zone(x, fy + 1, z, x, fy + 2, z)
		for z, f in ((C - 2, "north"), (C + 2, "south")):
			b.set(x, fy + 4, z, inner.stairs(x, fy + 4, z, f, "top"))
			for y in range(fy, fy + 4):
				b.set(x, y, z, "minecraft:cut_sandstone" if (y - fy) != 2 else "minecraft:orange_terracotta")
		for z in range(C - 1, C + 2):
			b.set(x, fy + 5, z, inner.full(x, fy + 5, z))
		if x % 3 == 0:
			b.set(x, fy + 2, C - 2, "minecraft:wall_torch[facing=south]")
			b.set(x, fy + 2, C + 2, "minecraft:wall_torch[facing=north]")
	deco.marker(b, gx - 4, G + 1, C, deco.ENTRANCE)
	# side chambers (north: rare loot, south: husk nest with the spawner)
	chamber(b, m, gx + 3, C - 8, gx + 7, C - 4, G, door_z=C - 3, loot=("rare", "south"))
	chamber(b, m, gx + 3, C + 4, gx + 7, C + 8, G, door_z=C + 3, loot=("common", "north"), spawner=True)


def statue(b, x, z):
	b.set(x, G, z, "minecraft:smooth_sandstone")
	b.set(x, G + 1, z, "minecraft:chiseled_sandstone")
	b.set(x, G + 2, z, "minecraft:sandstone_stairs[facing=east,half=bottom]")
	b.set(x + 1, G + 1, z, "minecraft:sandstone_stairs[facing=west,half=bottom]")
	b.set(x, G + 3, z, "minecraft:cut_sandstone")
	b.set(x - 1, G + 2, z, "minecraft:sandstone_wall")
	b.set(x, G + 4, z, "minecraft:sandstone_stairs[facing=west,half=top]")


def chamber(b, m, x0, z0, x1, z1, floor, door_z, loot, spawner=False):
	inner = m["inner"]
	for x in range(x0 - 1, x1 + 2):
		for z in range(z0 - 1, z1 + 2):
			for y in range(floor, floor + 6):
				edge = x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1) or y in (floor, floor + 5)
				b.set(x, y, z, ("minecraft:orange_terracotta" if y == floor + 3 and edge and y != floor else
					inner.full(x, y, z)) if edge else AIR)
	cx = (x0 + x1) // 2
	for y in range(floor + 1, floor + 4):
		b.set(cx, y, door_z, AIR)
	b.set(cx, floor + 3, door_z, inner.full(cx, floor + 3, door_z))
	b.clear_zone(cx, floor + 1, door_z, cx, floor + 2, door_z)
	far_z = z0 if door_z > z1 else z1
	tier, facing = loot
	deco.chest(b, cx, floor + 1, far_z, facing, tier)
	for x in (x0, x1):
		b.set(x, floor + 1, far_z, "minecraft:decorated_pot[cracked=true,facing=north,waterlogged=false]")
		b.t.nbt[(x, floor + 1, far_z)] = deco.N.Compound(id=deco.N.String("minecraft:decorated_pot"))
	if spawner:
		deco.spawner(b, cx, floor + 1, (z0 + z1) // 2, "minecraft:husk", count=3, max_nearby=5)
	else:
		b.set(x0, floor + 1, (z0 + z1) // 2, "minecraft:chiseled_sandstone")
		deco.candles(b, x0, floor + 2, (z0 + z1) // 2, 3, color="orange")


# ----------------------------------------------------------------------------------------------- the hall

def hall(b, m):
	inner, smooth = m["inner"], m["smooth"]
	x0, z0, x1, z1 = HALL
	for x in range(x0 - 1, x1 + 2):
		for z in range(z0 - 1, z1 + 2):
			for y in range(FEET - 2, HALL_CEIL + 1):
				b.set(x, y, z, inner.full(x, y, z))
	for x in range(x0, x1 + 1):
		for z in range(z0, z1 + 1):
			b.set(x, FEET - 1, z, "minecraft:smooth_sandstone" if (x + z) % 2 else "minecraft:cut_sandstone")
			for y in range(FEET, HALL_CEIL):
				b.set(x, y, z, AIR)
	# floor inlay: a blue terracotta cross toward the dais
	for z in range(z0, z1 + 1):
		b.set(C, FEET - 1, z, "minecraft:blue_terracotta")
	for x in range(x0 + 3, x1 - 2):
		b.set(x, FEET - 1, C, "minecraft:blue_terracotta")
	# corbelled ceiling: two rings of upside-down stairs stepping in
	for k in range(2):
		y = HALL_CEIL - 2 + k
		for x in range(x0 + k, x1 - k + 1):
			for z in range(z0 + k, z1 - k + 1):
				if x in (x0 + k, x1 - k) or z in (z0 + k, z1 - k):
					f = "west" if x == x0 + k else "east" if x == x1 - k else "north" if z == z0 + k else "south"
					b.set(x, y, z, inner.stairs(x, y, z, f, "top"))
	# wall decoration: terracotta frieze and pilasters with torches
	for i in range(x0, x1 + 1):
		for (x, z) in ((i, z0 - 1), (i, z1 + 1), (x0 - 1, i), (x1 + 1, i)):
			b.set(x, FEET + 4, z, "minecraft:orange_terracotta" if i % 4 else "minecraft:blue_terracotta")
	for i in range(x0 + 2, x1, 4):
		for (x, z, f) in ((i, z0, "south"), (i, z1, "north"), (x0, i, "east"), (x1, i, "west")):
			if b.is_air(x, FEET + 2, z) and not (z0 + 6 < i < z1 - 6 and f in ("east", "west")):
				b.set(x, FEET + 2, z, f"minecraft:wall_torch[facing={f}]")
	# four pillars (2 x 2) with lotus capitals
	for px in (C - 5, C + 4):
		for pz in (C - 5, C + 4):
			for x in (px, px + 1):
				for z in (pz, pz + 1):
					for y in range(FEET, HALL_CEIL):
						b.set(x, y, z, "minecraft:chiseled_sandstone" if y in (FEET, HALL_CEIL - 2) else
							("minecraft:orange_terracotta" if y == HALL_CEIL - 3 else smooth.full(x, y, z)))
			for x in range(px - 1, px + 3):
				for z in range(pz - 1, pz + 3):
					if (x in (px - 1, px + 2)) == (z in (pz - 1, pz + 2)):
						continue
					f = "east" if x == px - 1 else "west" if x == px + 2 else "south" if z == pz - 1 else "north"
					b.set(x, HALL_CEIL - 2, z, inner.stairs(x, HALL_CEIL - 2, z, f, "top"))
	# the dais (north end) with the pharaoh's sarcophagus and the treasure behind it
	for x in range(C - 4, C + 5):
		for z in range(z0, z0 + 4):
			b.set(x, FEET, z, "minecraft:smooth_sandstone" if z < z0 + 3 else smooth.stairs(x, FEET, z, "north", "bottom"))
	for x in (C - 5, C + 5):
		for z in range(z0, z0 + 3):
			b.set(x, FEET, z, smooth.stairs(x, FEET, z, "east" if x < C else "west", "bottom"))
	for z in range(z0, z0 + 3):
		b.set(C, FEET + 1, z, "minecraft:blue_terracotta")
		b.set(C, FEET + 2, z, "minecraft:smooth_sandstone_slab[type=bottom]" if z == z0 + 1 else
			stairs("minecraft:smooth_sandstone_stairs", "north" if z == z0 else "south"))
	for x in (C - 1, C + 1):
		for z in range(z0, z0 + 3):
			b.set(x, FEET + 1, z, "minecraft:cut_sandstone_slab[type=bottom]")
	deco.chest(b, C - 3, FEET + 1, z0, "south", "treasure")
	for x in (C - 4, C + 4):
		b.set(x, FEET + 1, z0, "minecraft:chiseled_sandstone")
		b.set(x, FEET + 2, z0, "minecraft:campfire[facing=south,lit=true,signal_fire=false]")
		b.t.nbt[(x, FEET + 2, z0)] = deco.N.Compound(id=deco.N.String("minecraft:campfire"))
	deco.candles(b, C + 2, FEET + 1, z0 + 1, 4, color="orange")
	deco.candles(b, C - 2, FEET + 1, z0 + 1, 3, color="orange")
	# entrance from the corridor (west wall)
	for z in range(C - 1, C + 2):
		for y in range(FEET, FEET + 4):
			b.set(x0 - 1, y, z, AIR)
	b.clear_zone(x0 - 1, FEET, C - 1, x0 + 1, FEET + 2, C + 1)
	# markers: the pharaoh before his sarcophagus, his husks between the pillars
	deco.marker(b, C, FEET, C - 2, deco.BOSS)
	for (x, z) in ((C - 3, C + 6), (C + 3, C + 6), (C - 7, C + 1), (C + 7, C + 1), (C - 7, C - 6), (C + 7, C - 6)):
		deco.marker(b, x, FEET, z, deco.MINION)
	b.clear_zone(x0, FEET, z0 + 4, x1, FEET + 3, z1)
	b.protect(x0 - 1, FEET - 2, z0 - 1, x1 + 1, HALL_CEIL, z1 + 1)


def robbers_tunnel(b, m):
	"""Rough tunnel dug from the eastern dune into the hall (south-east corner), shored up with planks."""
	x0 = HALL[2] + 1
	z = HALL[3] - 2
	for i, x in enumerate(range(x0, C + 22)):
		rise = max(0, (i - 3) // 2)
		fy = min(G + 1, FEET + rise)
		for zz in (z, z + 1):
			b.set(x, fy - 1, zz, "minecraft:sandstone" if b.hrand(x, 0, zz, 4) < 0.6 else "minecraft:sand")
			for yy in range(fy, fy + 3):
				b.set(x, yy, zz, AIR)
		if i % 4 == 2 and fy + 2 < G + 1:
			for zz in (z - 1, z + 2):
				b.set(x, fy, zz, "minecraft:spruce_fence")
				b.set(x, fy + 1, zz, "minecraft:spruce_fence")
			for zz in range(z - 1, z + 3):
				b.set(x, fy + 2, zz, "minecraft:spruce_planks" if zz in (z - 1, z + 2) else "minecraft:spruce_slab[type=top]")
		b.clear_zone(x, fy, z, x, fy + 1, z + 1)
	b.set(x0 + 2, FEET + 1, z - 1, "minecraft:torch")
	b.set(x0 + 1, FEET, z + 1, "minecraft:spruce_slab[type=bottom]")


# ---------------------------------------------------------------------------------------------- dunes

def dunes(b, m):
	"""
	Sand dunes heaped against the pyramid by the wind (east and north sides), falling to nothing before the edge of
	the template (no square sand block in the world), never in front of the gate, the stairway or the tunnel.
	"""
	n = b.noise
	for x in range(SX):
		for z in range(SZ):
			d = max(abs(x - C), abs(z - C))
			out = d - 18
			if out < 1:
				continue
			wind = max(0.0, 0.35 + 0.65 * ((x - C) / 24.0) + 0.45 * ((C - z) / 24.0))
			peak = 7.5 * wind + 1.6 * n.fbm2(x * 0.11, z * 0.11, 3)
			edge = max(0.0, min(1.0, (min(x, z, SX - 1 - x, SZ - 1 - z)) / 3.0))
			h = int(max(0.0, peak - 1.25 * (out - 1)) * edge)
			for y in range(G + 1, G + h + 1):
				if b.get(x, y, z) is None and (x, y, z) not in b.keep_clear:
					b.set(x, y, z, "minecraft:sand")
			if h > 0 and b.get(x, G, z) is None:
				b.set(x, G, z, "minecraft:sand")
	# drifts on the terraces of the windward faces
	for half, y0, y1 in TIERS[1:]:
		for x in range(C - half - 4, C + half + 5):
			for z in range(C - half - 4, C + half + 5):
				d = max(abs(x - C), abs(z - C))
				windward = (x - C) + (C - z) > 4
				if half < d <= half + 4 and windward and b.empty(x, y0, z) and b.get(x, y0 - 1, z) 					and n.n2(x * 0.3, z * 0.3) > -0.1 and (x, y0, z) not in b.keep_clear:
					b.set(x, y0, z, "minecraft:sand")
					if d == half + 1 and n.n2(x * 0.4, z * 0.4) > 0.2 and b.empty(x, y0 + 1, z):
						b.set(x, y0 + 1, z, "minecraft:sand")


# ------------------------------------------------------------------------------------------------- decay

def ruin_it(b, m):
	decay.breach(b, C + 7, TOP + 3, C - 7, 3.0, 3.5, 3.0)
	decay.breach(b, C - 10, G + 11, C + 10, 3.5, 3.0, 3.5)
	decay.breach(b, C + 14, G + 7, C + 14, 3.0, 3.0, 3.0)
	decay.breach(b, C + 2, TOP + 6, C - 3, 2.5, 2.0, 3.0)
	decay.erode(b, 0.22, top_bias=0.8, y0=G + 2, match=lambda s: "sandstone" in s or "terracotta" in s)
	decay.stabilize(b)
	decay.cleanup(b)
	decay.rubble(b, m["rubble"], keep=0.4, scatter=1.8, top=m["smooth"], max_height=3)
	decay.cleanup(b)


def life(b, m):
	veg.floor_cover(b, [("minecraft:dead_bush", 4), ("minecraft:short_dry_grass", 3), ("minecraft:tall_dry_grass", 1)],
		density=0.12, region=((0, G + 1, 0), (SX - 1, SY - 1, SZ - 1)), sky=True, need=lambda s: "sand" in s)
	for (x, z) in ((3, 40), (44, 6), (45, 44)):
		for y in range(G + 1, SY - 1):
			if b.empty(x, y, z) and b.get(x, y - 1, z) == "minecraft:sand":
				for k in range(1 + int(b.hrand(x, 0, z, 9) * 3)):
					b.set(x, y + k, z, "minecraft:cactus[age=0]")
				break
	veg.cobwebs(b, density=0.15, region=((0, FEET, 0), (SX - 1, G + 5, SZ - 1)))
	decay.cleanup(b)


if __name__ == "__main__":
	import render
	print(render.render_ruin(build()))
