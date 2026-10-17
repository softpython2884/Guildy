#!/usr/bin/env python3
"""
Validates ruin templates against the Guildy ruin contract.

  python tools/ruins/check.py                     every ruin in src/main/resources/data/guildy/structure/ruin
  python tools/ruins/check.py file.nbt [--ground N] [--nether] [--big]   one template (e.g. a converted schematic)

Rules: valid 26.3 block states, no forbidden block (command blocks, barriers, light, leftover jigsaws...), size
budget, exactly one guildy:boss DATA marker with free space (3 air above, free cells around, standing on a
block), 2-8 guildy:minion markers with the same rule, at most one guildy:entrance, a boss arena of at least
13 x 13 walkable cells and 6 blocks high without lava, every chest / barrel with a guildy loot table (1 treasure,
1-3 rare, 2-6 common), at most 2 spawners with SpawnData, only decorative entities, no floating blocks.
"""
import argparse
import json
import os
import sys
from collections import deque

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from ruinlib import blocks as B  # noqa: E402
from ruinlib.build import is_structural, DIRS  # noqa: E402
from ruinlib.template import Template, AIR  # noqa: E402
from ruinlib.ruin import RESOURCES  # noqa: E402

MAX_FOOTPRINT, MAX_FOOTPRINT_BIG, MAX_HEIGHT = 48, 64, 64
CONTAINERS = ("minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel")
ALLOWED_ENTITIES = ("minecraft:item_frame", "minecraft:glow_item_frame", "minecraft:armor_stand")
PASSABLE = ("air", "cave_air", "short_grass", "fern", "moss_carpet", "pale_moss_carpet", "snow", "cobweb", "vine",
	"glow_lichen", "hanging_roots", "dead_bush", "short_dry_grass", "crimson_roots", "warped_roots", "nether_sprouts",
	"leaf_litter", "structure_block", "tall_dry_grass", "weeping_vines", "weeping_vines_plant", "candle", "torch",
	"wall_torch", "iron_chain", "lantern", "soul_lantern", "crimson_fungus", "warped_fungus", "dandelion", "poppy",
	"white_tulip", "oxeye_daisy", "lily_of_the_valley", "azalea", "fire", "soul_fire")


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
	return n in PASSABLE or n.endswith("_carpet")


def check_template(t, report, nether=False, ground=0, big=False, pieces_with_jigsaw=False):
	"""Checks one (assembled) template. ground = layer of the ground floor (natural top block level)."""
	sx, sy, sz = t.size
	limit = MAX_FOOTPRINT_BIG if big else MAX_FOOTPRINT
	report.info.append(f"taille {sx} x {sy} x {sz}, sol a la couche {ground}, {len(t.cells)} blocs")
	if sx > limit or sz > limit:
		report.errors.append(f"emprise {sx} x {sz} > {limit} x {limit}")
	if sy > MAX_HEIGHT:
		report.errors.append(f"hauteur {sy} > {MAX_HEIGHT}")
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
	entrances = [p for p, meta in markers if meta == "guildy:entrance"]
	others = [(p, meta) for p, meta in markers if meta not in ("guildy:boss", "guildy:minion", "guildy:entrance")]
	report.info.append(f"marqueurs : {len(bosses)} boss, {len(minions)} sbires, {len(entrances)} entree")
	if len(bosses) != 1:
		report.errors.append(f"il faut exactement 1 marqueur guildy:boss (trouve {len(bosses)})")
	if not 2 <= len(minions) <= 8:
		report.errors.append(f"il faut 2 a 8 marqueurs guildy:minion (trouve {len(minions)})")
	if len(entrances) > 1:
		report.errors.append(f"au plus 1 marqueur guildy:entrance (trouve {len(entrances)})")
	for p, meta in others:
		report.warnings.append(f"marqueur DATA inconnu '{meta}' en {p}")
	for p in bosses + minions:
		spawn_room(t, p, report, ground)
	# containers
	loot = {"common": 0, "rare": 0, "treasure": 0}
	prefix = "guildy:chests/ruin/" + ("nether_" if nether else "")
	treasure_pos = []
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
	spawners = sum(1 for s in t.cells.values() if s.startswith("minecraft:spawner"))
	report.info.append(f"coffres : {loot['treasure']} tresor, {loot['rare']} rares, {loot['common']} communs ; {spawners} spawner(s)")
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
		arena(t, bosses[0], report)
		reachable(t, bosses[0], entrances[0] if entrances else None, report, ground)
	floating(t, report, ground)


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


def arena(t, boss, report):
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
	# biggest square of walkable cells (pillars allowed: 75 % of a 13 x 13 window must be walkable)
	best = 0
	for cx in range(min(xs), max(xs) - 11):
		for cz in range(min(zs), max(zs) - 11):
			n = sum(1 for i in range(13) for j in range(13) if (cx + i, cz + j) in seen)
			best = max(best, n)
	height = 0
	while passable(t.get(x0, y0 + height, z0)) and height < 40:
		height += 1
	lava = sum(1 for (x, z), y in seen.items() for dy in (-1, 0) if (t.get(x, y + dy, z) or "").startswith("minecraft:lava"))
	report.info.append(f"arene : {len(seen)} cases praticables autour du boss ({w} x {d}), {height} de haut au boss, "
		f"meilleure fenetre 13x13 = {best}/169")
	if best < 169 * 0.75:
		report.errors.append("arene trop petite : il faut au moins 13 x 13 cases praticables autour du boss (piliers toleres)")
	if height < 6:
		report.errors.append(f"arene trop basse au boss ({height} < 6)")
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
	"""A player can walk (jumps of one block, falls of up to 3, ladders) from the entrance to the boss."""
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
	while queue:
		x, y, z = queue.popleft()
		if (x, y, z) == target:
			report.info.append("chemin praticable de l'entrée jusqu'au boss")
			return True
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
	report.warnings.append("aucun chemin praticable trouvé de l'entrée jusqu'au boss (vérifier les accès)")
	return False


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


# ------------------------------------------------------------------------------------------- ruin files

def check_ruin(ruin):
	"""One report for all the variants of a ruin (each variant must honour the contract on its own)."""
	report = Report(f"{ruin.id} ({ruin.title})")
	for v in ruin.variants:
		sub = Report(v.name or "procedurale")
		check_template(v.assembled(ruin.id), sub, nether=ruin.dimension == "nether", ground=v.ground,
			big=getattr(ruin, "big", False), pieces_with_jigsaw=v.multi)
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

	for rid in sorted(os.listdir(base)):
		path = os.path.join(base, rid)
		if rid.endswith(".nbt"):
			rid = rid[:-4]
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


def _nether(rid):
	path = os.path.join(RESOURCES, "worldgen", "structure", "ruin", rid + ".json")
	try:
		with open(path, encoding="utf-8") as f:
			return "project_start_to_heightmap" not in json.load(f)
	except OSError:
		return False


def main():
	sys.stdout.reconfigure(encoding="utf-8")
	ap = argparse.ArgumentParser()
	ap.add_argument("files", nargs="*")
	ap.add_argument("--ground", type=int, default=0)
	ap.add_argument("--nether", action="store_true")
	ap.add_argument("--big", action="store_true")
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
			check_template(t, report, args.nether, args.ground, args.big)
			print(report.text())
			ok &= report.ok
	else:
		big_ids = {"bastion_de_givre", "temple_maya", "citadelle_des_ames"}
		for rid, t, nether, ground, multi in load_resources():
			report = Report(rid)
			check_template(t, report, nether, ground, rid.split("/")[0] in big_ids, multi)
			print(report.text())
			ok &= report.ok
	sys.exit(0 if ok else 1)


if __name__ == "__main__":
	main()
