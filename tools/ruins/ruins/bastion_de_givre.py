"""
Bastion de givre (tier 3, Overworld: snowy plains, snowy taiga, ice spikes, grove).

A ruined ice fortress: curtain walls of dark deepslate masonry clad with packed ice, a walkway with blue-ice
merlons, four round corner towers with ice cones (the north-east one broke in half, the south-west lost its
cone), a gatehouse with two drum towers and a half-fallen portcullis, and a keep along the north wall whose
throne hall opens onto the courtyard. The courtyard is the arena of the frost king (open sky, 22 x 22, frozen
knight statues and a dry fountain for cover). Ways in / out: the gate, the throne hall, and a breach in the east
curtain wall.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from ruinlib import build as K, decay, veg, deco, shapes  # noqa: E402
from ruinlib.build import Mat, Mix, AIR, stairs  # noqa: E402
from ruinlib.ruin import Ruin, split_pieces, rules  # noqa: E402

ID = "bastion_de_givre"
SEED = 4409
SX, SY, SZ = 56, 32, 56
G = 4
W0, W1 = 6, 49  # curtain wall outer faces
WALL_TOP = G + 10
TOWERS = [(8, 8), (47, 8), (8, 47), (47, 47)]
TOWER_R = 5.3
TOWER_TOP = G + 16
KEEP = (15, 9, 40, 20)  # x0, z0, x1, z1 (outer)
KEEP_TOP = G + 15
ARENA_C = (27.5, 33.0)
# room guards (guildy:guard): the gate passage, the throne hall, the east breach, the courtyard
GUARDS = [(26, G + 1, 48), (29, G + 1, 48), (20, G + 1, 15), (35, G + 1, 15), (43, G + 1, 13), (38, G + 1, 25)]


def build():
	b = K.new(SX, SY, SZ, SEED, ground=G, name=ID)
	m = materials()
	foundations(b, m)
	curtain(b, m)
	towers(b, m)
	gatehouse(b, m)
	keep(b, m)
	courtyard(b, m)
	for p in GUARDS:
		deco.guard(b, *p)
	ruin_it(b, m)
	life(b, m)
	b.open_clear_zones()
	shapes.fix(b.t)
	pieces = split_pieces(ID, b.t, G, jigsaw_xz=(0, 0))
	ruin = Ruin(ID, "Bastion de givre", "overworld", 3,
		["minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:ice_spikes", "minecraft:grove"],
		"beard_thin", pieces, G, 0, terrain="minecraft:snow_block", processors=[rules(
			("minecraft:deepslate_bricks", "minecraft:cracked_deepslate_bricks", 0.08),
			("minecraft:stone_bricks", "minecraft:cracked_stone_bricks", 0.08))])
	ruin.big = True
	ruin.plan_levels = [(G + 1, "Plan : cour (arène), donjon et tours")]
	ruin.cut_level = G + 7
	return ruin


def materials():
	return {
		"base": Mat("deepslate_brick", cracked="minecraft:cracked_deepslate_bricks", crack=0.2, seed=SEED,
			extra=[("minecraft:deepslate_tiles", 0.15), ("minecraft:cobbled_deepslate", 0.1)]),
		"ice": Mix([("minecraft:packed_ice", 8), ("minecraft:blue_ice", 1), ("minecraft:snow_block", 1)], seed=SEED + 1, scale=0.22),
		"trim": Mat("stone_brick", cracked="minecraft:cracked_stone_bricks", crack=0.15, seed=SEED + 2,
			extra=[("minecraft:polished_diorite", 0.05)]),
		"cap": Mat("polished_deepslate", seed=SEED + 3),
		"roof": Mat(K.Fam("blue_ice", "deepslate_tile_stairs", "deepslate_tile_slab", None), seed=SEED + 4),
		"tile": Mat("deepslate_tile", cracked="minecraft:cracked_deepslate_tiles", crack=0.1, seed=SEED + 5),
		"rubble": Mix([("minecraft:cobbled_deepslate", 3), ("minecraft:packed_ice", 3), ("minecraft:snow_block", 3),
			("minecraft:deepslate_bricks", 2), ("minecraft:stone_bricks", 1)], seed=SEED + 6),
		"pave": Mix([("minecraft:snow_block", 5), ("minecraft:packed_ice", 2), ("minecraft:polished_deepslate", 2),
			("minecraft:stone_bricks", 2), ("minecraft:blue_ice", 1)], seed=SEED + 7, scale=0.25),
	}


def dist(x, z, c):
	return math.hypot(x - c[0], z - c[1])


# --------------------------------------------------------------------------------------------- foundations

def foundations(b, m):
	for x in range(2, SX - 2):
		for z in range(2, SZ - 2):
			inside = W0 <= x <= W1 and W0 <= z <= W1 or any(dist(x, z, t) <= TOWER_R + 0.5 for t in TOWERS)
			if inside:
				for y in range(0, G):
					b.set(x, y, z, m["base"].full(x, y, z))


# --------------------------------------------------------------------------------------------- curtain

def wall_cell(b, m, x, y, z, outer):
	"""Masonry of the curtain: dark base courses, ice cladding above, a deepslate band at the walkway."""
	if y <= G + 3:
		return m["base"].full(x, y, z)
	if y == WALL_TOP or y == G + 4:
		return m["cap"].full(x, y, z)
	return m["ice"].full(x, y, z) if outer else m["trim"].full(x, y, z)


def curtain(b, m):
	for side in ("north", "south", "west", "east"):
		for i in range(W0, W1 + 1):
			for t in range(3):
				if side == "north":
					x, z = i, W0 + t
				elif side == "south":
					x, z = i, W1 - t
				elif side == "west":
					x, z = W0 + t, i
				else:
					x, z = W1 - t, i
				for y in range(G, WALL_TOP + 1):
					b.set(x, y, z, wall_cell(b, m, x, y, z, outer=(t == 0)))
			# merlons on the outer edge, walkway behind
			if side == "north":
				ox, oz = i, W0
			elif side == "south":
				ox, oz = i, W1
			elif side == "west":
				ox, oz = W0, i
			else:
				ox, oz = W1, i
			if i % 2 == 0:
				b.set(ox, WALL_TOP + 1, oz, "minecraft:blue_ice")
				b.set(ox, WALL_TOP + 2, oz, m["tile"].slab(ox, WALL_TOP + 2, oz, "bottom"))
			else:
				b.set(ox, WALL_TOP + 1, oz, m["tile"].slab(ox, WALL_TOP + 1, oz, "bottom"))
			# buttress-like pilasters on the outer face every 6, corbels under the walkway inside
			dx, dz = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}[side]
			if i % 6 == 3 and W0 + 4 < i < W1 - 4:
				px, pz = ox + dx, oz + dz
				for y in range(G, WALL_TOP - 1):
					b.set(px, y, pz, m["base"].full(px, y, pz))
				b.set(px, WALL_TOP - 1, pz, m["tile"].stairs(px, WALL_TOP - 1, pz, K.OPP[side], "bottom"))
			else:
				px, pz = ox + dx, oz + dz
				b.set(px, G, pz, m["base"].stairs(px, G, pz, K.OPP[side], "bottom"))
			ix, iz = ox - dx * 3, oz - dz * 3
			b.set(ix, WALL_TOP, iz, m["trim"].stairs(ix, WALL_TOP, iz, side, "top"))
	# inner face: pilasters every 6 with corbelled tops under the walkway, a dark plinth course
	for side, (dx, dz) in (("north", (0, 1)), ("south", (0, -1)), ("west", (1, 0)), ("east", (-1, 0))):
		for i in range(W0 + 6, W1 - 5):
			if side == "north":
				x, z = i, W0 + 3
			elif side == "south":
				x, z = i, W1 - 3
			elif side == "west":
				x, z = W0 + 3, i
			else:
				x, z = W1 - 3, i
			if b.get(x, G + 1, z) is not None:
				continue
			if i % 6 == 0:
				for y in range(G + 1, WALL_TOP - 1):
					b.set(x, y, z, m["base"].full(x, y, z) if y <= G + 3 else m["cap"].full(x, y, z))
				b.set(x, WALL_TOP - 1, z, m["tile"].stairs(x, WALL_TOP - 1, z, K.OPP[side], "top"))
			else:
				b.set(x, G + 1, z, m["base"].stairs(x, G + 1, z, K.OPP[side], "bottom"))
	# icicles under the outer cornice
	for (x, y, z), state in list(b.t.cells.items()):
		if y == WALL_TOP and state.startswith("minecraft:polished_deepslate") and b.hrand(x, y, z, 3) < 0.08:
			for (dx, dz) in ((0, -1), (0, 1), (-1, 0), (1, 0)):
				if b.get(x + dx, y - 1, z + dz) is None and b.get(x + dx, y, z + dz) is None:
					b.set(x + dx, y, z + dz, "minecraft:packed_ice")
					b.set(x + dx, y - 1, z + dz, "minecraft:packed_ice")
					if b.hrand(x, y, z, 4) < 0.5:
						b.set(x + dx, y - 2, z + dz, "minecraft:ice")
					break


# ---------------------------------------------------------------------------------------------- towers

def towers(b, m):
	for k, (cx, cz) in enumerate(TOWERS):
		for x in range(cx - 6, cx + 7):
			for z in range(cz - 6, cz + 7):
				d = dist(x, z, (cx, cz))
				if d > TOWER_R + 0.5:
					continue
				for y in range(G, TOWER_TOP + 1):
					if d > TOWER_R - 1.0:
						if y <= G + 3:
							s = m["base"].full(x, y, z)
						elif (y - G) % 6 == 4:
							s = m["cap"].full(x, y, z)
						else:
							s = m["ice"].full(x, y, z)
					else:
						s = AIR if y > G else "minecraft:polished_deepslate"
					b.set(x, y, z, s)
				# wooden floors
				if d <= TOWER_R - 1.0:
					b.set(x, G + 6, z, "minecraft:spruce_planks")
					b.set(x, G + 12, z, "minecraft:spruce_planks")
		# corbelled parapet
		for x in range(cx - 7, cx + 8):
			for z in range(cz - 7, cz + 8):
				d = dist(x, z, (cx, cz))
				if TOWER_R + 0.5 < d <= TOWER_R + 1.5:
					f = _toward(x, z, cx, cz)
					b.set(x, TOWER_TOP, z, m["tile"].stairs(x, TOWER_TOP, z, f, "top"))
					b.set(x, TOWER_TOP + 1, z, "minecraft:blue_ice" if (x + z) % 2 else m["tile"].slab(x, TOWER_TOP + 1, z, "bottom"))
				elif d <= TOWER_R + 0.5:
					b.set(x, TOWER_TOP, z, "minecraft:spruce_planks" if d < TOWER_R - 1.0 else m["cap"].full(x, TOWER_TOP, z))
				if TOWER_R + 0.5 < d <= TOWER_R + 1.5:
					b.set(x, TOWER_TOP + 1, z, AIR)
		# ice cone
		b.spire(cx, cz, 5.4, TOWER_TOP + 1, 10, m["tile"], round_=True)
		# arrow slits
		for a in range(0, 360, 90):
			ra = math.radians(a + 45)
			x, z = int(round(cx + math.cos(ra) * TOWER_R)), int(round(cz + math.sin(ra) * TOWER_R))
			for y in (G + 8, G + 9, G + 14):
				b.set(x, y, z, AIR)
		# ladder up through the floors (against the inner side)
		lx, lz = cx + (1 if cx < 27 else -1), cz + (1 if cz < 27 else -1)
		for y in range(G + 1, TOWER_TOP):
			b.set(lx, y, lz, AIR)
		# door from the courtyard
		ddx = 1 if cx < 27 else -1
		ddz = 1 if cz < 27 else -1
		for t in range(1, 6):
			x, z = cx + ddx * t, cz + ddz * t
			for y in range(G + 1, G + 4):
				if b.get(x, y, z) is not None:
					b.set(x, y, z, AIR)
			b.set(x, G, z, "minecraft:polished_deepslate")
		b.clear_zone(cx + ddx, G + 1, cz + ddz, cx + ddx * 5, G + 2, cz + ddz * 5)
	# tower contents: a stray spawner (north-west), common chests
	deco.spawner(b, 8, G + 7, 8, "minecraft:stray", count=2, max_nearby=4)
	deco.chest(b, 47, G + 1, 45, "north", "common", kind="barrel")
	deco.chest(b, 8, G + 1, 45, "north", "common")


def _toward(x, z, cx, cz):
	dx, dz = cx - x, cz - z
	if abs(dx) >= abs(dz):
		return "east" if dx > 0 else "west"
	return "south" if dz > 0 else "north"


# -------------------------------------------------------------------------------------------- gatehouse

def gatehouse(b, m):
	gx0, gx1 = 25, 30  # passage x
	# drum towers flanking the gate
	for (cx, cz) in ((22, 50), (33, 50)):
		for x in range(cx - 4, cx + 5):
			for z in range(cz - 4, cz + 5):
				d = dist(x, z, (cx, cz))
				if d > 3.4:
					continue
				for y in range(G, G + 14):
					s = m["base"].full(x, y, z) if y <= G + 4 else (m["cap"].full(x, y, z) if y in (G + 9, G + 13) else m["ice"].full(x, y, z))
					b.set(x, y, z, s if d > 2.2 or y in (G, G + 13) else AIR)
				if 2.4 < d <= 3.4:
					b.set(x, G + 14, z, "minecraft:blue_ice" if (x + z) % 2 else m["tile"].slab(x, G + 14, z, "bottom"))
	# passage through the wall and the drum towers' gap
	for x in range(gx0, gx1 + 1):
		for z in range(W1 - 3, SZ - 2):
			b.set(x, G, z, "minecraft:polished_deepslate" if (x + z) % 2 else "minecraft:deepslate_tiles")
			for y in range(G + 1, G + 7):
				b.set(x, y, z, AIR)
		for z in range(W1 - 3, W1 + 3):
			b.set(x, G + 7, z, m["cap"].full(x, G + 7, z))
			b.set(x, G + 8, z, m["trim"].full(x, G + 8, z))
	for z in range(W1 - 3, W1 + 3):
		b.set(gx0, G + 6, z, m["trim"].stairs(gx0, G + 6, z, "west", "top"))
		b.set(gx1, G + 6, z, m["trim"].stairs(gx1, G + 6, z, "east", "top"))
	# the portcullis: half fallen (iron bars hanging from the arch on one side)
	for x in range(gx0, gx1 + 1):
		for y in range(G + 4 if x < 28 else G + 2, G + 7):
			b.set(x, y, W1, "minecraft:iron_bars")
	b.clear_zone(gx0, G + 1, W1 - 3, gx1, G + 3, SZ - 2)
	deco.marker(b, 27, G + 1, SZ - 3, deco.ENTRANCE)
	# guard rooms in the drum towers
	deco.chest(b, 22, G + 1, 50, "north", "common")
	for (x, z) in ((24, 50), (31, 50)):
		for y in range(G + 1, G + 4):
			b.set(x, y, z, AIR)


# ------------------------------------------------------------------------------------------------- keep

def keep(b, m):
	x0, z0, x1, z1 = KEEP
	for x in range(x0, x1 + 1):
		for z in range(z0, z1 + 1):
			edge = x in (x0, x1) or z in (z0, z1)
			for y in range(G, KEEP_TOP + 1):
				if edge:
					s = m["base"].full(x, y, z) if y <= G + 4 else (m["cap"].full(x, y, z) if y in (G + 9, KEEP_TOP) else m["ice"].full(x, y, z))
				else:
					s = AIR
				b.set(x, y, z, s)
			if not edge:
				b.set(x, G, z, "minecraft:polished_deepslate" if (x + z) % 2 else "minecraft:dark_prismarine")
				b.set(x, G + 9, z, "minecraft:spruce_planks")
	# pilasters on the courtyard face, battlements on top
	for x in range(x0, x1 + 1):
		if (x - x0) % 4 == 0:
			for y in range(G, KEEP_TOP):
				b.set(x, y, z1 + 1, m["base"].full(x, y, z1 + 1))
			b.set(x, KEEP_TOP, z1 + 1, m["tile"].stairs(x, KEEP_TOP, z1 + 1, "north", "bottom"))
		b.set(x, KEEP_TOP + 1, z1, "minecraft:blue_ice" if x % 2 == 0 else m["tile"].slab(x, KEEP_TOP + 1, z1, "bottom"))
		b.set(x, KEEP_TOP + 1, z0, "minecraft:blue_ice" if x % 2 == 0 else m["tile"].slab(x, KEEP_TOP + 1, z0, "bottom"))
	# pitched roof over the keep (slate), will be half collapsed
	b.gable_roof(x0 + 1, z0 + 1, x1 - 1, z1 - 1, KEEP_TOP + 1, "x", m["tile"], overhang=0, under="minecraft:spruce_planks")
	# throne hall: a wide arched opening onto the courtyard
	b.opening(23, z1, "east", G + 1, 10, 7, 1, "south", m["trim"], arch="round")
	for x in (22, 33):
		b.column(x, z1 + 1, G + 1, G + 7, m["cap"])
	b.clear_zone(23, G + 1, z1 - 1, 32, G + 4, z1 + 1)
	# windows in the upper floor
	for x in range(x0 + 2, x1 - 1, 4):
		for z in (z0, z1):
			for y in (G + 11, G + 12):
				b.set(x, y, z, "minecraft:light_blue_stained_glass_pane" if b.hrand(x, y, z, 1) < 0.4 else AIR)
	# the throne on a dais against the north wall, banners of ice, the treasure behind
	for x in range(24, 32):
		for z in range(z0 + 1, z0 + 4):
			b.set(x, G + 1, z, "minecraft:polished_deepslate" if z < z0 + 3 else m["tile"].stairs(x, G + 1, z, "north", "bottom"))
	b.set(27, G + 2, z0 + 1, stairs("minecraft:polished_deepslate_stairs", "north"))
	b.set(28, G + 2, z0 + 1, stairs("minecraft:polished_deepslate_stairs", "north"))
	for x in (26, 29):
		b.set(x, G + 2, z0 + 1, "minecraft:blue_ice")
		b.set(x, G + 3, z0 + 1, "minecraft:packed_ice")
	for x in (27, 28):
		b.set(x, G + 3, z0 + 1, "minecraft:blue_ice")
		b.set(x, G + 4, z0 + 1, "minecraft:packed_ice")
	deco.chest(b, 25, G + 2, z0 + 1, "south", "treasure")
	deco.chest(b, 17, G + 1, z0 + 1, "south", "rare")
	deco.chest(b, 38, G + 1, z1 - 1, "north", "rare", kind="barrel")
	for x in (24, 31):
		deco.lantern(b, x, G + 2, z0 + 2, soul=True)
	# a stair up to the keep's upper floor (west end)
	for i in range(8):
		b.set(x0 + 1, G + 1 + i, z1 - 1 - i, m["tile"].stairs(x0 + 1, G + 1 + i, z1 - 1 - i, "north", "bottom"))
		b.set(x0 + 1, G + 9, z1 - 1 - i, AIR) if i < 6 else None
	b.protect(x0, G, z0, x1, G + 5, z1)


# --------------------------------------------------------------------------------------------- courtyard

def courtyard(b, m):
	pave = m["pave"]
	for x in range(W0 + 3, W1 - 2):
		for z in range(KEEP[3] + 1, W1 - 2):
			b.set(x, G, z, pave)
			for y in range(G + 1, G + 9):
				if b.get(x, y, z) is None:
					b.set(x, y, z, AIR)
	for x in range(W0 + 3, W1 - 2):
		for z in range(W0 + 3, KEEP[1]):
			b.set(x, G, z, pave)
	cx, cz = ARENA_C
	# dry fountain in the middle: low ring (the frost king stands in it)
	for x in range(23, 33):
		for z in range(28, 38):
			d = dist(x, z, (27.5, 32.5))
			if 3.6 < d <= 4.6:
				b.set(x, G + 1, z, m["tile"].slab(x, G + 1, z, "bottom"))
			elif d <= 3.6:
				b.set(x, G, z, "minecraft:packed_ice")
	# frozen knight statues (cover): armour of packed ice on deepslate plinths
	for (x, z) in ((19, 26), (36, 26), (19, 40), (36, 40)):
		knight(b, x, z)
	# broken carts / crates near the walls
	for (x, z) in ((12, 26), (13, 26), (43, 38), (43, 39), (12, 41)):
		b.set(x, G + 1, z, "minecraft:spruce_planks" if b.hrand(x, 0, z, 2) < 0.5 else "minecraft:stripped_spruce_log[axis=x]")
	deco.chest(b, 12, G + 2, 26, "east", "common", kind="barrel")
	# markers
	deco.marker(b, 27, G + 1, 32, deco.BOSS)
	for (x, z) in ((21, 30), (34, 30), (21, 37), (34, 37), (27, 25), (27, 41), (16, 33), (39, 33)):
		deco.marker(b, x, G + 1, z, deco.MINION)
	b.clear_zone(16, G + 1, 23, 39, G + 4, 44)
	b.protect(15, G, 22, 40, G, 45)


def knight(b, x, z):
	b.set(x, G + 1, z, "minecraft:polished_deepslate")
	b.set(x, G + 2, z, "minecraft:packed_ice")
	b.set(x, G + 3, z, "minecraft:blue_ice")
	b.set(x, G + 4, z, "minecraft:packed_ice")
	b.set(x + 1, G + 3, z, "minecraft:polished_deepslate_wall")
	b.set(x + 1, G + 4, z, "minecraft:iron_bars")
	b.set(x - 1, G + 3, z, "minecraft:polished_deepslate_wall")
	b.set(x, G + 5, z, "minecraft:skeleton_skull[rotation=0]")
	b.t.nbt[(x, G + 5, z)] = deco.skull_nbt()


# ------------------------------------------------------------------------------------------------- decay

def ruin_it(b, m):
	# the north-east tower broke in half, the south-west lost its cone, the keep roof fell in on the east
	decay.damage(b, lambda x, y, z, s: dist(x, z, TOWERS[1]) < 8 and y > G + 9 + 3 * b.noise.n3(x * 0.3, y * 0.3, z * 0.3))
	decay.damage(b, lambda x, y, z, s: dist(x, z, TOWERS[2]) < 6 and y > TOWER_TOP + 1 + 2 * b.noise.n3(x * 0.3, y * 0.3, z * 0.3))
	decay.breach(b, 36, KEEP_TOP + 3, 14, 6.0, 4.5, 5.0, roughness=0.4)
	# a breach in the east curtain wall (rubble ramp into the courtyard)
	decay.breach(b, W1 - 1, G + 7, 33, 2.5, 6.0, 4.0, roughness=0.3)
	decay.breach(b, W1 - 1, G + 2, 33, 2.5, 2.0, 2.4, roughness=0.2)
	decay.breach(b, 18, WALL_TOP + 1, W0, 4.0, 3.0, 2.0)
	# merlons and the upper courses crumbled all along the walls
	decay.erode(b, 0.55, top_bias=0.0, y0=WALL_TOP + 1, y1=WALL_TOP + 2,
		match=lambda s: "blue_ice" in s or "slab" in s or "polished_blackstone" in s)
	decay.erode(b, 0.26, top_bias=1.2, y0=G + 4, y1=TOWER_TOP,
		match=lambda s: any(k in s for k in ("packed_ice", "brick", "snow_block", "polished_deepslate")))
	decay.stabilize(b)
	decay.cleanup(b)

	def source(state):
		if "planks" in state or "log" in state:
			return "minecraft:spruce_slab[type=bottom]" if b.rng.random() < 0.3 else False
		return None
	decay.rubble(b, m["rubble"], keep=0.4, scatter=2.0, top=m["tile"], max_height=4, source=source)
	decay.cleanup(b)


def life(b, m):
	veg.snow(b, density=0.85, max_layers=2)
	veg.floor_cover(b, [("minecraft:snow[layers=1]", 3), ("minecraft:short_grass", 1), ("minecraft:fern", 1)],
		density=0.25, region=((0, G + 1, 0), (SX - 1, G + 2, SZ - 1)), sky=False)
	veg.cobwebs(b, density=0.15, region=((0, G + 1, 0), (SX - 1, TOWER_TOP, SZ - 1)))
	decay.cleanup(b)


if __name__ == "__main__":
	import render
	print(render.render_ruin(build()))
