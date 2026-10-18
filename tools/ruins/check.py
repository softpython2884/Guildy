#!/usr/bin/env python3
"""
Validates ruin templates against the Guildy ruin contract.

  python tools/ruins/check.py                     every ruin in src/main/resources/data/guildy/structure/ruin
  python tools/ruins/check.py file.nbt [--ground N] [--nether] [--big]   one template (e.g. a converted schematic)
  python tools/ruins/check.py file.nbt --monument --ground 1              a monument (statue placed by the mod)

Monuments (ruins.MONUMENTS, e.g. the jaguar statues of the trail to the grand temple) have their own contract
(check_monument): no boss, no arena, no marker, no container nor block entity, no entity, at most 16 x 16 x 24, glowing
eyes on the north face, nothing floating, the water audit.

Rules: valid 26.3 block states, no forbidden block (command blocks, barriers, light, leftover jigsaws...), size
budget, exactly one guildy:boss DATA marker with free space (3 air above, free cells around, standing on a
block), 2-8 guildy:minion markers with the same rule, 2-8 guildy:guard markers (room guards: 2 free cells above,
a solid floor, room to move, on the walkable path), at most one guildy:entrance, a boss arena of at least 13 x 13
walkable cells and 6 blocks high without lava (25 x 25 and 12 high for the grand temple), every chest / barrel with
a guildy loot table (1 treasure, 1-3 rare, 2-6 common), every decorated pot with the pot loot table, at most 2
spawners with SpawnData, only decorative entities, no floating blocks.

Water audit (ruinlib/water.py, the vanilla flow rules): the template's water (and lava) is let flow until it is
stable; water that ends up anywhere it was not built (on floors, steps, in the arena, waterlogging blocks) is an
error, a fall into a basin is fine; dry troughs that look like canals are reported. Under the ground floor: holes in
the rooms (structure void: the terrain shows through, or the water of a flooded ground), the openings through which
the water of a flooded ground (an aquifer) would get in, and the structures that let the placement waterlog their
blocks with the world's water (liquid_settings apply_waterlogging). Ice melted by nearby lights is reported.
"""
import argparse
import json
import os
import sys
from collections import deque

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from ruinlib import blocks as B  # noqa: E402
from ruinlib import water as W  # noqa: E402
from ruinlib.build import is_structural, DIRS  # noqa: E402
from ruinlib.template import Template, AIR  # noqa: E402
from ruinlib.ruin import RESOURCES  # noqa: E402

# Size classes: (footprint, height, arena side, arena height). "big" = the 64 x 64 landmarks, "grand" = the grand
# temple (116 x 116: the start piece of a jigsaw structure, randomly rotated around the corner of its start chunk,
# must stay within the 8 chunks the structure references reach, with the 12-block margin of the terrain adaptation).
LIMITS = {
	"normal": (48, 64, 13, 6),
	"big": (64, 64, 13, 6),
	"grand": (116, 128, 25, 12),
}
BIG_IDS = {"bastion_de_givre", "temple_maya", "citadelle_des_ames"}
GRAND_IDS = {"grand_temple_maya"}
# Monuments (placed by the mod at runtime, no boss): footprint and height limits, light blocks that count as eyes.
MONUMENT_LIMITS = (16, 24)
EYE_BLOCKS = ("minecraft:verdant_froglight", "minecraft:ochre_froglight", "minecraft:pearlescent_froglight",
	"minecraft:sea_lantern", "minecraft:glowstone", "minecraft:shroomlight", "minecraft:redstone_lamp")
BLOCK_ENTITIES = ("minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "minecraft:spawner", "minecraft:decorated_pot", "minecraft:hopper", "minecraft:dropper",
	"minecraft:dispenser", "minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker", "minecraft:brewing_stand",
	"minecraft:shulker_box", "minecraft:trial_spawner", "minecraft:vault", "minecraft:lectern", "minecraft:chiseled_bookshelf")
CONTAINERS = ("minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel")
POT_LOOT = ("guildy:pots/ruin", "guildy:pots/ruin_nether")
ALLOWED_ENTITIES = ("minecraft:item_frame", "minecraft:glow_item_frame", "minecraft:armor_stand")
PASSABLE = ("air", "cave_air", "short_grass", "fern", "moss_carpet", "pale_moss_carpet", "snow", "cobweb", "vine",
	"glow_lichen", "hanging_roots", "dead_bush", "short_dry_grass", "crimson_roots", "warped_roots", "nether_sprouts",
	"leaf_litter", "structure_block", "tall_dry_grass", "weeping_vines", "weeping_vines_plant", "candle", "torch",
	"wall_torch", "iron_chain", "lantern", "soul_lantern", "crimson_fungus", "warped_fungus", "dandelion", "poppy",
	"white_tulip", "oxeye_daisy", "lily_of_the_valley", "azalea", "fire", "soul_fire", "cave_vines", "cave_vines_plant",
	"copper_wall_torch", "copper_torch", "tripwire")


class Report:
	def __init__(self, name):
		self.name = name
		self.errors = []
		self.warnings = []
		self.info = []

	@property
	def ok(self):
		return not self.errors

	def text(self):
		lines = [f"[{'OK' if self.ok else 'ERREUR'}] {self.name}"]
		lines += ["  - " + i for i in self.info]
		lines += ["  ! " + w for w in self.warnings]
		lines += ["  X " + e for e in self.errors]
		return "\n".join(lines)


def _name(state):
	return None if state is None else state.split("[", 1)[0].split(":")[1]


def passable(state):
	if state is None:
		return False
	n = _name(state)
	return n in PASSABLE or n.endswith("_carpet") or n.endswith("_pressure_plate")


def size_class(rid):
	return "grand" if rid in GRAND_IDS else "big" if rid in BIG_IDS else "normal"


def check_template(t, report, nether=False, ground=0, big=False, pieces_with_jigsaw=False,
	liquid="ignore_waterlogging"):
	"""
	Checks one (assembled) template. ground = layer of the ground floor (natural top block level). big: False /
	True (the 64 x 64 landmarks) or a size class name of LIMITS ("normal", "big", "grand"). liquid: the liquid_settings
	of the structure (apply_waterlogging lets the placement waterlog the blocks standing in the world's water).
	"""
	sx, sy, sz = t.size
	limits = LIMITS[big if isinstance(big, str) else ("big" if big else "normal")]
	limit, max_height = limits[0], limits[1]
	report.info.append(f"taille {sx} x {sy} x {sz}, sol a la couche {ground}, {len(t.cells)} blocs")
	if sx > limit or sz > limit:
		report.errors.append(f"emprise {sx} x {sz} > {limit} x {limit}")
	if sy > max_height:
		report.errors.append(f"hauteur {sy} > {max_height}")
	# block states
	bad = {}
	for pos, state in t.cells.items():
		try:
			B.canon(state)
		except B.BlockError as e:
			bad.setdefault(str(e), pos)
		block_id = state.split("[", 1)[0]
		if block_id in B.FORBIDDEN and not (block_id == "minecraft:jigsaw" and pieces_with_jigsaw):
			bad.setdefault(f"bloc interdit {block_id}", pos)
	for msg, pos in bad.items():
		report.errors.append(f"{msg} (ex. {pos})")
	# jigsaws: only connectors with a final state
	for pos, state in t.cells.items():
		if state.startswith("minecraft:jigsaw"):
			tag = t.nbt.get(pos, {})
			if "final_state" not in tag or "pool" not in tag:
				report.errors.append(f"jigsaw incomplet en {pos}")
	# entities
	for e in t.entities:
		eid = str(e["nbt"].get("id", "?"))
		if eid not in ALLOWED_ENTITIES:
			report.errors.append(f"entite non decorative {eid} en {e['blockPos']}")
	# markers
	markers = t.markers()
	bosses = [p for p, meta in markers if meta == "guildy:boss"]
	minions = [p for p, meta in markers if meta == "guildy:minion"]
	guards = [p for p, meta in markers if meta == "guildy:guard"]
	entrances = [p for p, meta in markers if meta == "guildy:entrance"]
	others = [(p, meta) for p, meta in markers if meta not in ("guildy:boss", "guildy:minion", "guildy:guard",
		"guildy:entrance")]
	report.info.append(f"marqueurs : {len(bosses)} boss, {len(minions)} sbires, {len(guards)} gardes, {len(entrances)} entree")
	if len(bosses) != 1:
		report.errors.append(f"il faut exactement 1 marqueur guildy:boss (trouve {len(bosses)})")
	if not 2 <= len(minions) <= 8:
		report.errors.append(f"il faut 2 a 8 marqueurs guildy:minion (trouve {len(minions)})")
	if not 2 <= len(guards) <= 8:
		report.errors.append(f"il faut 2 a 8 marqueurs guildy:guard (trouve {len(guards)})")
	if len(entrances) > 1:
		report.errors.append(f"au plus 1 marqueur guildy:entrance (trouve {len(entrances)})")
	for p, meta in others:
		report.warnings.append(f"marqueur DATA inconnu '{meta}' en {p}")
	for p in bosses + minions:
		spawn_room(t, p, report, ground)
	for p in guards:
		guard_post(t, p, report, ground)
	# containers
	loot = {"common": 0, "rare": 0, "treasure": 0}
	prefix = "guildy:chests/ruin/" + ("nether_" if nether else "")
	treasure_pos = []
	pots = 0
	for pos, state in t.cells.items():
		block_id = state.split("[", 1)[0]
		if block_id in CONTAINERS:
			table = str(t.nbt.get(pos, {}).get("LootTable", ""))
			if not table.startswith(prefix) or table[len(prefix):] not in loot:
				report.errors.append(f"{block_id} en {pos} : table de butin '{table}' invalide (attendu {prefix}common|rare|treasure)")
				continue
			loot[table[len(prefix):]] += 1
			if table.endswith("treasure"):
				treasure_pos.append(pos)
		if block_id == "minecraft:spawner":
			if "SpawnData" not in t.nbt.get(pos, {}):
				report.errors.append(f"spawner sans SpawnData en {pos}")
		if block_id == "minecraft:decorated_pot":
			pots += 1
			table = str(t.nbt.get(pos, {}).get("LootTable", ""))
			if table != POT_LOOT[1 if nether else 0]:
				report.errors.append(f"pot décoré en {pos} : table de butin '{table}' invalide (attendu {POT_LOOT[1 if nether else 0]})")
	spawners = sum(1 for s in t.cells.values() if s.startswith("minecraft:spawner"))
	report.info.append(f"coffres : {loot['treasure']} tresor, {loot['rare']} rares, {loot['common']} communs ; {spawners} spawner(s) ; "
		f"{pots} pot(s) décoré(s)")
	if loot["treasure"] != 1:
		report.errors.append(f"il faut 1 coffre tresor (trouve {loot['treasure']})")
	if not 1 <= loot["rare"] <= 3:
		report.errors.append(f"il faut 1 a 3 coffres rares (trouve {loot['rare']})")
	if not 2 <= loot["common"] <= 6:
		report.errors.append(f"il faut 2 a 6 coffres communs (trouve {loot['common']})")
	if spawners > 2:
		report.errors.append(f"au plus 2 spawners (trouve {spawners})")
	if bosses and treasure_pos:
		d = min(_dist(bosses[0], p) for p in treasure_pos)
		if d > 24:
			report.warnings.append(f"le coffre tresor est a {d:.0f} blocs du boss (le mettre dans / pres de l'arene)")
	if bosses:
		arena(t, bosses[0], report, limits[2], limits[3])
		walkable = reachable(t, bosses[0], entrances[0] if entrances else None, report, ground)
		lost = [p for p in guards if p not in walkable]
		if lost:
			report.warnings.append(f"{len(lost)} garde(s) hors du chemin praticable depuis l'entrée, ex. {lost[:3]}")
	floating(t, report, ground)
	water(t, report, nether, ground, liquid, bosses[0] if bosses else None)


def _dist(a, b):
	return sum((i - j) ** 2 for i, j in zip(a, b)) ** 0.5


def spawn_room(t, p, report, ground):
	x, y, z = p
	what = "boss" if any(q == p and m == "guildy:boss" for q, m in t.markers()) else "sbire"
	for dy in (1, 2, 3):
		s = t.get(x, y + dy, z)
		if not passable(s):
			report.errors.append(f"marqueur {what} en {p} : pas 3 blocs d'air au-dessus (y+{dy} = {s or 'vide'})")
			return
	below = t.get(x, y - 1, z)
	if below is None and y - 1 > ground or below is not None and not is_structural(below):
		report.errors.append(f"marqueur {what} en {p} : rien de solide dessous")
	blocked = 0
	for dx in (-1, 0, 1):
		for dz in (-1, 0, 1):
			if dx == 0 and dz == 0:
				continue
			for dy in (0, 1):
				if not passable(t.get(x + dx, y + dy, z + dz)):
					blocked += 1
					break
	if blocked > 3:
		report.errors.append(f"marqueur {what} en {p} : trop encombre ({blocked} cases voisines bloquees)")


def guard_post(t, p, report, ground):
	"""A room guard stands on a solid floor with two free cells above and at least two free cells around."""
	x, y, z = p
	for dy in (1, 2):
		s = t.get(x, y + dy, z)
		if not passable(s) and not (s is None and y + dy > ground):
			report.errors.append(f"marqueur garde en {p} : pas 2 blocs libres au-dessus (y+{dy} = {s or 'vide'})")
			return
	below = t.get(x, y - 1, z)
	if below is None and y - 1 > ground or below is not None and (not is_structural(below) or passable(below)):
		report.errors.append(f"marqueur garde en {p} : pas de sol solide dessous")
	free = 0
	for dx in (-1, 0, 1):
		for dz in (-1, 0, 1):
			if (dx or dz) and all(_free(t.get(x + dx, y + dy, z + dz), y + dy, ground) for dy in (0, 1)):
				free += 1
	if free < 2:
		report.errors.append(f"marqueur garde en {p} : coince ({free} case(s) voisine(s) libre(s))")


def _free(state, y, ground):
	return passable(state) or (state is None and y > ground)


def arena(t, boss, report, side=13, min_height=6):
	"""Walkable area around the boss (feet level +-1), its size, height and lava."""
	x0, y0, z0 = boss
	seen = {(x0, z0): y0}
	queue = deque([(x0, y0, z0)])
	while queue:
		x, y, z = queue.popleft()
		for dx, dz in DIRS.values():
			nx, nz = x + dx, z + dz
			if (nx, nz) in seen or abs(nx - x0) > 24 or abs(nz - z0) > 24:
				continue
			for ny in (y, y + 1, y - 1):
				if abs(ny - y0) > 2:
					continue
				if passable(t.get(nx, ny, nz)) and passable(t.get(nx, ny + 1, nz)) and \
					t.get(nx, ny - 1, nz) is not None and is_structural(t.get(nx, ny - 1, nz)):
					seen[(nx, nz)] = ny
					queue.append((nx, ny, nz))
					break
	xs = [p[0] for p in seen]
	zs = [p[1] for p in seen]
	w, d = max(xs) - min(xs) + 1, max(zs) - min(zs) + 1
	# biggest square of walkable cells (pillars allowed: 75 % of a side x side window must be walkable)
	best = 0
	area = side * side
	for cx in range(min(xs), max(xs) - side + 2):
		for cz in range(min(zs), max(zs) - side + 2):
			n = sum(1 for i in range(side) for j in range(side) if (cx + i, cz + j) in seen)
			best = max(best, n)
	height = 0
	while passable(t.get(x0, y0 + height, z0)) and height < 40:
		height += 1
	lava = sum(1 for (x, z), y in seen.items() for dy in (-1, 0) if (t.get(x, y + dy, z) or "").startswith("minecraft:lava"))
	report.info.append(f"arene : {len(seen)} cases praticables autour du boss ({w} x {d}), {height} de haut au boss, "
		f"meilleure fenetre {side}x{side} = {best}/{area}")
	if best < area * 0.75:
		report.errors.append(f"arene trop petite : il faut au moins {side} x {side} cases praticables autour du boss "
			"(piliers toleres)")
	if height < min_height:
		report.errors.append(f"arene trop basse au boss ({height} < {min_height})")
	if lava:
		report.errors.append(f"lave dans le sol de l'arene ({lava} cases)")


def _walk_cell(t, x, y, z, ground):
	"""Feet cell where a player can stand: two passable cells, something to stand on (or a ladder)."""
	here, head = t.get(x, y, z), t.get(x, y + 1, z)
	free = lambda s, yy: passable(s) or (s is None and yy > ground)
	if not free(here, y) and not (here or "").startswith("minecraft:ladder"):
		return False
	if not free(head, y + 1) and not (head or "").startswith("minecraft:ladder"):
		return False
	if (here or "").startswith("minecraft:ladder"):
		return True
	below = t.get(x, y - 1, z)
	if below is None:
		return y - 1 <= ground
	return is_structural(below) and not passable(below) or below.startswith("minecraft:ladder")


def reachable(t, boss, entrance, report, ground):
	"""
	A player can walk (jumps of one block, falls of up to 3, ladders) from the entrance to the boss. Returns every
	feet cell reached from the entrance.
	"""
	sx, sy, sz = t.size
	starts = []
	if entrance is not None and _walk_cell(t, *entrance, ground):
		starts.append(entrance)
	else:
		if entrance is not None:
			report.errors.append(f"marqueur d'entrée en {entrance} : on ne peut pas s'y tenir (rien dessous ou pas de place)")
		for x in range(sx):
			for z in (0, sz - 1):
				starts.append((x, ground + 1, z))
		for z in range(sz):
			for x in (0, sx - 1):
				starts.append((x, ground + 1, z))
	seen = set()
	queue = deque()
	for p in starts:
		if _walk_cell(t, *p, ground):
			seen.add(p)
			queue.append(p)
	target = boss
	found = False
	while queue:
		x, y, z = queue.popleft()
		if (x, y, z) == target and not found:
			report.info.append("chemin praticable de l'entrée jusqu'au boss")
			found = True
		moves = []
		for dx, dz in DIRS.values():
			for dy in (0, 1, -1, -2, -3):
				moves.append((x + dx, y + dy, z + dz))
		if (t.get(x, y, z) or "").startswith("minecraft:ladder") or (t.get(x, y + 1, z) or "").startswith("minecraft:ladder"):
			moves += [(x, y + 1, z), (x, y - 1, z)]
		for q in moves:
			if q in seen or not t.inside(*q):
				continue
			above = t.get(x, y + 2, z)
			step = t.get(q[0], q[1] - 1, q[2]) or ""
			half_step = "_stairs" in step or "type=bottom" in step
			if q[1] > y and not half_step and above is not None and not passable(above) 				and not above.startswith("minecraft:ladder"):
				continue  # no room to jump (stairs and slabs are climbed without jumping)
			if _walk_cell(t, *q, ground):
				seen.add(q)
				queue.append(q)
	if not found:
		report.warnings.append("aucun chemin praticable trouvé de l'entrée jusqu'au boss (vérifier les accès)")
	return seen


def floating(t, report, ground):
	"""Structural blocks not connected to the ground through other structural blocks (6-neighbourhood)."""
	cells = {p for p, s in t.cells.items() if is_structural(s) and not s.startswith(AIR)}
	seen = set()
	queue = deque()
	for p in cells:
		x, y, z = p
		if y <= ground or (t.get(x, y - 1, z) is None and y - 1 <= ground):
			seen.add(p)
			queue.append(p)
	while queue:
		x, y, z = queue.popleft()
		for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
			q = (x + dx, y + dy, z + dz)
			if q in cells and q not in seen:
				seen.add(q)
				queue.append(q)
	lost = sorted(cells - seen)
	if lost:
		report.errors.append(f"{len(lost)} bloc(s) flottant(s) (non relies au sol), ex. {lost[:4]}")


# ------------------------------------------------------------------------------------------------- water

def _box(cells):
	xs, ys, zs = zip(*cells)
	return f"x {min(xs)}..{max(xs)}, y {min(ys)}..{max(ys)}, z {min(zs)}..{max(zs)}"


def _sunk(flow, p):
	"""The fluid cell p lies in a groove of a floor: two of its sides are solid blocks with open space above (lips)."""
	lips = 0
	for d in W.HORIZONTAL:
		n = W.rel(p, d)
		s = flow.state(n)
		if s is not None and W.classify(s).kind in ("sealed", "container") and n not in flow.fluid and \
			W.is_open(flow.state(W.rel(n, W.UP))):
			lips += 1
	return lips >= 2


def _spill(flow, comp, boss):
	"""
	(kind, description) of a group of cells the fluid reached outside what was built: "flood" (water on floors, steps,
	in the arena, waterlogged blocks), "trough" (flowing water lower than the rest of a canal: a gap in it), "fall" (a
	fall into a basin).
	"""
	flat = [p for p in comp if not flow.fluid[p][1] and p not in flow.waterlogged]
	if flat and sum(_sunk(flow, p) for p in flat) >= 0.75 * len(flat) and not flow.waterlogged & set(comp):
		return "trough", f"{len(comp)} case(s) d'eau courante plus basse que le reste du canal, {_box(comp)}"
	floor, steps, arena, falls = [], 0, 0, 0
	for p in comp:
		below = (p[0], p[1] - 1, p[2])
		under = flow.state(below)
		if p in flow.waterlogged:
			floor.append(p)
			steps += "_stairs" in flow.cells[p]
		elif below not in flow.fluid and under is not None and W.classify(under).kind != "open":
			floor.append(p)
			steps += "_stairs" in under
		above = flow.fluid.get((p[0], p[1] + 1, p[2]))
		if flow.fluid[p][1] and not (above and above[1]):
			falls += 1
		if boss and p in floor and abs(p[0] - boss[0]) <= 15 and abs(p[2] - boss[2]) <= 15 and abs(p[1] - boss[1]) <= 3:
			arena += 1
	origin = sorted({q for p in comp for d in W.HORIZONTAL + (W.UP,) for q in [W.rel(p, d)] if q in flow.initial})
	basin = sorted({q for p in comp for q in [W.rel(p, W.DOWN)] if q in flow.initial})
	where = f"{len(comp)} case(s), {_box(comp)}, depuis {origin[:2]}"
	if not floor:
		return "fall", f"chute contenue : {where}, {falls} filet(s) qui tombe(nt) dans l'eau en {basin[:2]}"
	what = [f"{len(floor)} sur des sols"]
	if steps:
		what.append(f"{steps} sur des marches")
	if arena:
		what.append(f"{arena} dans l'arène")
	if flow.waterlogged & set(comp):
		what.append(f"{len(flow.waterlogged & set(comp))} bloc(s) noyé(s)")
	if falls:
		what.append(f"{falls} chute(s)")
	return "flood", f"{where} ({', '.join(what)})"


def water(t, report, nether=False, ground=0, liquid="ignore_waterlogging", boss=None):
	"""Water audit of a template (see the module doc)."""
	built = set()  # every cell the template's own water wets
	for fluid in ((W.LAVA_NETHER if nether else W.LAVA), W.WATER):
		name = "eau" if fluid is W.WATER else "lave"
		flow = W.Flow(t, fluid).run()
		if not flow.initial:
			continue
		if fluid is W.WATER:
			built = set(flow.fluid)
		spilled = flow.spilled()
		report.info.append(f"{name} : {len(flow.initial)} case(s) du gabarit, écoulement stable après {flow.ticks} ticks, "
			f"{len(spilled)} case(s) atteinte(s) en plus")
		if not flow.converged:
			report.warnings.append(f"{name} : l'écoulement n'est pas stable après {flow.ticks} ticks")
		for comp in flow.components(spilled):
			kind, text = _spill(flow, comp, boss)
			if kind == "flood":
				report.errors.append(f"{name} qui déborde : {text}")
			elif kind == "trough":
				report.warnings.append(f"canal cassé : {text}")
			else:
				report.info.append(f"{name} : {text}")
		if fluid is W.WATER:
			for group in W.dry_channels(flow):
				report.warnings.append(f"canal à sec : {len(group)} case(s) creusée(s) dans le sol sans eau, {_box(group)}")
	if not nether:
		for p, lv in W.melting_ice(t):
			report.warnings.append(f"glace en {p} : lumière {lv}, elle fondra en eau")
	if nether or ground <= 0:
		return
	for v in W.holes(t, ground):
		report.errors.append(f"trou en {v} : vide de structure au milieu d'une salle souterraine (le terrain s'y montre, "
			"ou l'eau d'un sol noyé)")
	flow = W.Flow(t, W.WATER, terrain_water_below=ground).run()
	leak = [p for p in flow.spilled() if p not in built]
	if leak:
		entries = sorted({q for p in leak for d in W.STEP for q in [W.rel(p, d)] if q in flow.terrain})
		report.warnings.append(f"sol noyé (nappe sous le sol) : l'eau du terrain entrerait par {len(entries)} case(s) "
			f"(ex. {entries[:3]}) et mouillerait {len(leak)} case(s) ({_box(leak)})")
	if liquid != "ignore_waterlogging":
		wet, flooded = W.flooded_ground(t, ground)
		if wet:
			drowned = sum(1 for p in flooded.fluid if p not in built)
			report.errors.append(f"liquid_settings {liquid} : dans un sol noyé, le placement noie {len(wet)} bloc(s) du "
				f"sous-sol (escaliers, dalles, chaînes...) et l'eau inonde {drowned} case(s) : mettre ignore_waterlogging")


# ------------------------------------------------------------------------------------------- monuments

def monument_ids():
	"""Ids of the monuments (templates under structure/ruin placed by the mod, not by the worldgen)."""
	import ruins
	return set(ruins.MONUMENTS)


def check_monument(t, report, ground):
	"""
	The contract of a monument (a statue of the trail of the grand temple): placed by the mod on the natural ground, so no
	boss and no arena, no marker, no container nor any block entity with loot, no entity; the size budget; glowing eyes
	(light blocks) on its front (north half: the runtime turns the north face toward its target); nothing floating; the
	water audit; the layers under `ground` stay a foundation (no room under the ground).
	"""
	sx, sy, sz = t.size
	limit, max_height = MONUMENT_LIMITS
	report.info.append(f"monument {sx} x {sy} x {sz}, sol a la couche {ground}, {len(t.cells)} blocs")
	if sx > limit or sz > limit:
		report.errors.append(f"emprise {sx} x {sz} > {limit} x {limit}")
	if sy > max_height:
		report.errors.append(f"hauteur {sy} > {max_height}")
	if ground < 0 or ground >= sy:
		report.errors.append(f"couche du sol {ground} hors du gabarit")
	bad = {}
	for pos, state in t.cells.items():
		try:
			B.canon(state)
		except B.BlockError as e:
			bad.setdefault(str(e), pos)
		block_id = state.split("[", 1)[0]
		if block_id in B.FORBIDDEN:
			bad.setdefault(f"bloc interdit {block_id}", pos)
		elif block_id == "minecraft:structure_block":
			bad.setdefault("marqueur (bloc de structure) : un monument n'a ni boss, ni sbire, ni garde", pos)
		elif block_id in BLOCK_ENTITIES:
			bad.setdefault(f"{block_id} : un monument n'a ni coffre, ni pot, ni spawner", pos)
	for msg, pos in bad.items():
		report.errors.append(f"{msg} (ex. {pos})")
	if t.nbt:
		report.errors.append(f"{len(t.nbt)} bloc(s) avec des donnees (ex. {sorted(t.nbt)[0]})")
	for e in t.entities:
		report.errors.append(f"entite {e['nbt'].get('id', '?')} en {e['blockPos']} (aucune entite dans un monument)")
	eyes = sorted(p for p, s in t.cells.items() if s.split("[", 1)[0] in EYE_BLOCKS)
	report.info.append(f"yeux lumineux : {len(eyes)} {eyes[:4]}")
	if len(eyes) < 2:
		report.errors.append("il faut au moins 2 yeux lumineux (froglight, lanterne marine...)")
	elif any(z >= sz / 2 for (_, _, z) in eyes):
		report.warnings.append("des yeux sont dans la moitie sud : la face du monument doit regarder le nord (-z)")
	under = [p for p, s in t.cells.items() if p[1] < ground and s.startswith(AIR)]
	if under:
		report.errors.append(f"{len(under)} case(s) d'air sous le sol (ex. {sorted(under)[:3]}) : les couches enterrees sont une fondation")
	above = {(x, z) for (x, y, z), s in t.cells.items() if y > ground and is_structural(s) and not s.startswith(AIR)}
	report.info.append(f"emprise au sol : {len({(x, z) for (x, y, z) in t.cells if y == ground})} case(s), "
		f"{len(above)} colonne(s) construites au-dessus")
	floating(t, report, ground)
	water(t, report, False, ground, "ignore_waterlogging", None)


def check_monument_object(monument):
	report = Report(f"{monument.id} ({monument.title}, monument)")
	check_monument(monument.assembled(), report, monument.ground)
	return report


# ------------------------------------------------------------------------------------------- ruin files

def check_ruin(ruin):
	"""One report for all the variants of a ruin (each variant must honour the contract on its own)."""
	report = Report(f"{ruin.id} ({ruin.title})")
	for v in ruin.variants:
		sub = Report(v.name or "procedurale")
		cls = "grand" if getattr(ruin, "grand", False) else "big" if getattr(ruin, "big", False) else "normal"
		check_template(v.assembled(ruin.id), sub, nether=ruin.dimension == "nether", ground=v.ground,
			big=cls, pieces_with_jigsaw=v.multi, liquid=ruin.liquid_settings)
		prefix = f"[variante {v.name}] " if v.name else ""
		report.info += [prefix + i for i in sub.info]
		report.warnings += [prefix + w for w in sub.warnings]
		report.errors += [prefix + e for e in sub.errors]
	return report


def load_resources():
	"""[(name, assembled template, nether, ground, multi)] for every variant found in src/main/resources."""
	base = os.path.join(RESOURCES, "structure", "ruin")
	out = []

	def assemble(name, folder):
		surface = Template.load(os.path.join(folder, "surface.nbt"), strict=True)
		below = Template.load(os.path.join(folder, "below.nbt"), strict=True)
		t = Template(surface.size[0], surface.size[1] + below.size[1], surface.size[2], name)
		h = below.size[1]
		t.cells.update(below.cells)
		t.nbt.update(below.nbt)
		for (x, y, z), s in surface.cells.items():
			t.cells[(x, y + h, z)] = s
		for (x, y, z), tag in surface.nbt.items():
			t.nbt[(x, y + h, z)] = tag
		return t, h

	monuments = monument_ids()
	for rid in sorted(os.listdir(base)):
		path = os.path.join(base, rid)
		if rid.endswith(".nbt"):
			rid = rid[:-4]
			if rid in monuments:
				continue
			out.append((rid, Template.load(path, strict=True), _nether(rid), 0, False))
			continue
		if not os.path.isdir(path):
			continue
		if os.path.exists(os.path.join(path, "surface.nbt")):
			t, h = assemble(rid, path)
			out.append((rid, t, _nether(rid), h, True))
		for entry in sorted(os.listdir(path)):
			sub = os.path.join(path, entry)
			if entry.endswith(".nbt") and entry not in ("surface.nbt", "below.nbt"):
				out.append((f"{rid}/{entry[:-4]}", Template.load(sub, strict=True), _nether(rid), 0, False))
			elif os.path.isdir(sub) and os.path.exists(os.path.join(sub, "surface.nbt")):
				t, h = assemble(f"{rid}/{entry}", sub)
				out.append((f"{rid}/{entry}", t, _nether(rid), h, True))
	return out


def _structure(rid):
	path = os.path.join(RESOURCES, "worldgen", "structure", "ruin", rid + ".json")
	try:
		with open(path, encoding="utf-8") as f:
			return json.load(f)
	except OSError:
		return {}


def _nether(rid):
	data = _structure(rid)
	return bool(data) and "project_start_to_heightmap" not in data


def liquid_settings(rid):
	"""liquid_settings of a ruin's structure (the vanilla default is apply_waterlogging)."""
	return _structure(rid.split("/")[0]).get("liquid_settings", "apply_waterlogging")


def main():
	sys.stdout.reconfigure(encoding="utf-8")
	ap = argparse.ArgumentParser()
	ap.add_argument("files", nargs="*")
	ap.add_argument("--ground", type=int, default=0)
	ap.add_argument("--nether", action="store_true")
	ap.add_argument("--big", action="store_true")
	ap.add_argument("--grand", action="store_true", help="limites du grand temple (116 x 116 x 128, arene 25 x 25)")
	ap.add_argument("--monument", action="store_true", help="contrat d'un monument (statue posee par le mod, sans boss)")
	args = ap.parse_args()
	ok = True
	if args.files:
		for path in args.files:
			report = Report(path)
			try:
				t = Template.load(path, strict=True)
			except B.BlockError as e:
				report.errors.append(str(e))
				print(report.text())
				ok = False
				continue
			if args.monument:
				check_monument(t, report, args.ground)
			else:
				check_template(t, report, args.nether, args.ground, "grand" if args.grand else "big" if args.big else "normal")
			print(report.text())
			ok &= report.ok
	else:
		for rid, t, nether, ground, multi in load_resources():
			report = Report(rid)
			check_template(t, report, nether, ground, size_class(rid.split("/")[0]), multi, liquid_settings(rid))
			print(report.text())
			ok &= report.ok
		import ruins
		for mid in sorted(monument_ids()):
			report = Report(f"{mid} (monument)")
			path = os.path.join(RESOURCES, "structure", "ruin", mid + ".nbt")
			if not os.path.exists(path):
				report.errors.append(f"gabarit absent : {path}")
			else:
				check_monument(Template.load(path, strict=True), report, ruins.load_monument(mid).ground)
			print(report.text())
			ok &= report.ok
	sys.exit(0 if ok else 1)


if __name__ == "__main__":
	main()
