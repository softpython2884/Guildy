#!/usr/bin/env python3
"""
Converts a schematic built in creative into a ruin template that honours the Guildy ruin contract.

Inputs: Sponge .schem v2 / v3 (WorldEdit, FAWE), Litematica .litematic, vanilla structure .nbt.

  python tools/ruins/import_schematic.py build.schem --ruin crypte_oubliee --variant chapelle_noire [--ground N]
      [--nether] [--weight 1] [--replace] [--air smart|keep|void] [--default-loot common|none] [--dry-run]

What it does:
  1. reads the file (all regions of a litematic merged), maps a few renamed ids, validates every block state against
     Minecraft 26.3 (unknown properties dropped, unknown blocks reported);
  2. turns the marker signs into gameplay blocks (first line of the front text, case / accents ignored):
       [boss]  [sbire]  [garde]  [entree]             -> DATA structure block guildy:boss / guildy:minion /
                                                        guildy:guard / guildy:entrance at the sign's cell (the
                                                        sign disappears)
       [coffre commun]  [coffre rare]  [coffre tresor] -> loot table on the chest / barrel the sign is attached to
                                                        (wall sign: the block behind it; standing sign: the block
                                                        under it), or a new chest in the sign's cell
     containers left without a table get the --default-loot one (common), with a warning;
  3. structure_void cells stay "void" (the natural terrain is kept there); air outside the build becomes void too
     (--air smart: air above the highest block of its neighbourhood, i.e. open sky, is void; air under roofs and in
     rooms is kept so it carves); barriers become air, light blocks / jigsaws / command blocks are removed;
  4. auto-crop, then the contract checks (check.py) and a preview (tools/ruins/previews/<ruin>__<variant>.png);
  5. writes tools/ruins/imported/<ruin>/<variant>/template.nbt + variant.json. build_all.py then adds the variant
     to the ruin's template pool (or replaces the procedural build with --replace) and writes the mod resources.

--ground N: index (from the lowest block of the build) of the ground floor layer, the one that replaces the top
block of the natural ground. Layers under it are cellars / crypts / foundations. Default 0 (no basement).
--natural: blocks of the creative world's ground copied with the build (default grass_block, dirt, bedrock) that
become natural terrain (void) at and under the ground floor.
"""
import argparse
import json
import os
import re
import sys
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from ruinlib import blocks as B, deco, formats, nbt as N, shapes  # noqa: E402
from ruinlib.build import Builder, DIRS, OPP  # noqa: E402
from ruinlib.template import Template, AIR  # noqa: E402
import check  # noqa: E402
import render  # noqa: E402
import ruins  # noqa: E402

TAGS = {
	"boss": ("marker", deco.BOSS),
	"sbire": ("marker", deco.MINION),
	"sbires": ("marker", deco.MINION),
	"minion": ("marker", deco.MINION),
	"garde": ("marker", deco.GUARD),
	"gardes": ("marker", deco.GUARD),
	"guard": ("marker", deco.GUARD),
	"entree": ("marker", deco.ENTRANCE),
	"entrance": ("marker", deco.ENTRANCE),
	"coffre commun": ("loot", "common"),
	"coffre rare": ("loot", "rare"),
	"coffre tresor": ("loot", "treasure"),
}
REMOVED = ("minecraft:light", "minecraft:jigsaw", "minecraft:command_block", "minecraft:chain_command_block",
	"minecraft:repeating_command_block", "minecraft:structure_block", "minecraft:test_block",
	"minecraft:test_instance_block", "minecraft:moving_piston")
CONTAINERS = ("minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel")


def plain(text):
	text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode("ascii")
	return re.sub(r"\s+", " ", text.strip().lower())


def component_text(value):
	"""Plain text of a sign line: JSON text component string, SNBT compound or plain string."""
	if isinstance(value, dict):
		out = str(value.get("text", ""))
		for extra in value.get("extra", []):
			out += component_text(extra)
		return out
	if isinstance(value, list):
		return "".join(component_text(v) for v in value)
	s = str(value)
	if s[:1] in ("{", "[", '"'):
		try:
			return component_text(json.loads(s))
		except ValueError:
			return s.strip('"')
	return s


def sign_lines(tag):
	if tag is None:
		return []
	front = tag.get("front_text")
	if isinstance(front, dict):
		return [component_text(m) for m in front.get("messages", [])]
	return [component_text(tag.get(f"Text{i}", "")) for i in range(1, 5)]


def is_sign(state):
	return state is not None and state.split("[", 1)[0].endswith("_sign")


def attached_block(state, pos):
	"""The block a sign is placed against: behind a wall sign, under a standing / hanging sign."""
	x, y, z = pos
	block_id, props = B.parse(state)
	if "_wall_" in block_id or block_id.endswith("_wall_sign"):
		f = props.get("facing", "north")
		dx, dz = DIRS[OPP[f]]
		return (x + dx, y, z + dz), f
	if "hanging" in block_id:
		return (x, y + 1, z), "north"
	return (x, y - 1, z), _rotation_facing(props.get("rotation", "0"))


def _rotation_facing(rotation):
	r = int(rotation) % 16
	return ["south", "west", "north", "east"][((r + 2) // 4) % 4]


NATURAL = ("minecraft:grass_block", "minecraft:dirt", "minecraft:bedrock")


def convert(t, nether=False, air_mode="smart", default_loot="common", ground=0, natural=NATURAL):
	"""Normalises a raw template (returns a new one) and the list of messages for the report."""
	msgs = []
	out = Template(*t.size, name=t.name)
	# the copy may include the creative world's ground: natural blocks at / under the ground floor become void
	bottom = min((p[1] for p, s in t.cells.items() if not s.startswith(AIR)), default=0)
	ground_abs = bottom + ground
	dropped = 0
	for pos, state in list(t.cells.items()):
		if pos[1] <= ground_abs and state.split("[", 1)[0] in natural:
			t.cells.pop(pos)
			dropped += 1
	if dropped:
		msgs.append(f"{dropped} bloc(s) de terrain ({', '.join(n.split(':')[1] for n in natural)}) au niveau du sol ou dessous -> terrain naturel")
	# 1. block states
	unknown = {}
	forced_air = set()  # barriers: air the builder wants carved whatever happens
	for pos, state in t.cells.items():
		block_id = state.split("[", 1)[0]
		if block_id in REMOVED or block_id == "minecraft:structure_void":
			continue
		if block_id == "minecraft:barrier":
			out.cells[pos] = AIR
			forced_air.add(pos)
			continue
		try:
			out.cells[pos] = B.canon(state, strict=False)
		except B.BlockError:
			unknown[block_id] = unknown.get(block_id, 0) + 1
			continue
		if pos in t.nbt:
			out.nbt[pos] = t.nbt[pos]
	for block_id, n in sorted(unknown.items()):
		msgs.append(f"ERREUR bloc inconnu en 26.3 : {block_id} ({n} fois) -> retiré")
	for name in sorted({s.split('[', 1)[0] for p, s in t.cells.items() if s.split('[', 1)[0] in REMOVED}):
		msgs.append(f"bloc retiré : {name}")
	b = Builder(out, 1)
	prefix = "nether_" if nether else ""
	# 2. marker signs
	tagged_loot = set()
	for pos, state in list(out.cells.items()):
		if not is_sign(state):
			continue
		lines = [plain(l) for l in sign_lines(out.nbt.get(pos))]
		first = next((l for l in lines if l), "")
		key = first.strip("[]").strip()
		if not (first.startswith("[") and key in TAGS):
			continue
		kind, value = TAGS[key]
		out.nbt.pop(pos, None)
		if kind == "marker":
			deco.marker(b, *pos, value)
			msgs.append(f"panneau {first} en {pos} -> marqueur {value}")
			continue
		target, facing = attached_block(state, pos)
		existing = out.cells.get(target)
		if existing is not None and existing.split("[", 1)[0] in CONTAINERS:
			tag = out.nbt.get(target) or N.Compound(id=N.String(existing.split("[", 1)[0].replace("minecraft:trapped_chest",
				"minecraft:trapped_chest")))
			tag["id"] = N.String("minecraft:barrel" if "barrel" in existing else "minecraft:trapped_chest"
				if "trapped" in existing else "minecraft:chest")
			tag["LootTable"] = N.String(deco.LOOT[prefix + value])
			tag.pop("Items", None)
			out.nbt[target] = tag
			out.cells[pos] = AIR
			tagged_loot.add(target)
			msgs.append(f"panneau {first} en {pos} -> table {deco.LOOT[prefix + value]} sur {existing.split('[', 1)[0]} {target}")
		else:
			deco.chest(b, *pos, facing, prefix + value)
			tagged_loot.add(pos)
			msgs.append(f"panneau {first} en {pos} -> nouveau coffre {deco.LOOT[prefix + value]}")
	# 3. other containers
	for pos, state in list(out.cells.items()):
		block_id = state.split("[", 1)[0]
		if block_id not in CONTAINERS or pos in tagged_loot:
			continue
		tag = out.nbt.get(pos) or N.Compound(id=N.String("minecraft:barrel" if "barrel" in block_id else block_id))
		table = str(tag.get("LootTable", ""))
		if table.startswith("guildy:chests/ruin/"):
			continue
		if default_loot == "none":
			msgs.append(f"ATTENTION {block_id} en {pos} sans table de butin guildy (refusé par la vérification)")
			continue
		tag["LootTable"] = N.String(deco.LOOT[prefix + default_loot])
		tag.pop("Items", None)
		out.nbt[pos] = tag
		msgs.append(f"{block_id} en {pos} sans panneau -> table {deco.LOOT[prefix + default_loot]}")
	# 4. air outside the build
	if air_mode == "void":
		for pos, state in list(out.cells.items()):
			if state.startswith(AIR) and pos not in forced_air:
				del out.cells[pos]
	elif air_mode == "smart":
		tops, lows = {}, {}
		for (x, y, z), state in out.cells.items():
			if not state.startswith(AIR):
				tops[(x, z)] = max(tops.get((x, z), -1), y)
				lows[(x, z)] = min(lows.get((x, z), 1 << 20), y)
		for (x, y, z), state in list(out.cells.items()):
			if not state.startswith(AIR) or (x, y, z) in forced_air:
				continue
			if y <= ground_abs:
				# under the ground floor air carves only inside the build (a floor somewhere under it)
				if lows.get((x, z), 1 << 20) >= y:
					del out.cells[(x, y, z)]
				continue
			roof = max((tops.get((x + dx, z + dz), -1) for dx in range(-2, 3) for dz in range(-2, 3)), default=-1)
			if y > roof:
				del out.cells[(x, y, z)]
		# spawn cells and the arena always carve (open-sky arenas included)
		marks = out.markers()
		for (x, y, z), meta in marks:
			if meta == deco.BOSS:
				for dx in range(-9, 10):
					for dz in range(-9, 10):
						if dx * dx + dz * dz > 81:
							continue
						for dy in range(0, 7):
							q = (x + dx, y + dy, z + dz)
							if out.inside(*q) and out.cells.get(q) is None:
								out.cells[q] = AIR
			for dx in (-1, 0, 1):
				for dz in (-1, 0, 1):
					for dy in range(0, 4):
						q = (x + dx, y + dy, z + dz)
						if out.inside(*q) and out.cells.get(q) is None:
							out.cells[q] = AIR
	# 5. crop + shapes
	out = out.crop()
	shapes.fix(out)
	return out, msgs


def main():
	sys.stdout.reconfigure(encoding="utf-8")
	ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
	ap.add_argument("file")
	ap.add_argument("--ruin", required=True, choices=ruins.IDS)
	ap.add_argument("--variant", required=True, help="nom court de la variante (lettres, chiffres, _)")
	ap.add_argument("--ground", type=int, default=0)
	ap.add_argument("--nether", action="store_true", help="(automatique pour les ruines du Nether)")
	ap.add_argument("--weight", type=int, default=1)
	ap.add_argument("--replace", action="store_true", help="remplace la ruine procédurale au lieu de s'y ajouter")
	ap.add_argument("--air", choices=("smart", "keep", "void"), default="smart")
	ap.add_argument("--default-loot", choices=("common", "rare", "none"), default="common")
	ap.add_argument("--natural", default=",".join(NATURAL),
		help="blocs de terrain du monde créatif à rendre au terrain naturel (au niveau du sol et dessous)")
	ap.add_argument("--dry-run", action="store_true", help="rapport + aperçu seulement")
	args = ap.parse_args()
	if not re.fullmatch(r"[a-z0-9_]+", args.variant) or args.variant in ("surface", "below"):
		ap.error("--variant : lettres minuscules, chiffres et _ (pas 'surface' ni 'below')")
	nether = args.nether or args.ruin in ("autel_infernal", "citadelle_des_ames")
	raw, fmt = formats.load_any(args.file)
	print(f"{args.file} : {fmt}, {raw.size[0]} x {raw.size[1]} x {raw.size[2]}, {len(raw.cells)} cellules")
	natural = tuple(B.full_id(n) for n in args.natural.split(",") if n.strip())
	t, msgs = convert(raw, nether, args.air, args.default_loot, args.ground, natural)
	for m in msgs:
		print("  " + m)
	print(f"après conversion : {t.size[0]} x {t.size[1]} x {t.size[2]}, {len(t.cells)} blocs")
	report = check.Report(f"{args.ruin}/{args.variant}")
	check.check_template(t, report, nether, args.ground, check.size_class(args.ruin))
	print(report.text())
	ruin = ruins.load(args.ruin, with_imports=False)
	from ruinlib.ruin import Variant, Piece, split_pieces
	if not nether and args.ground > 0:
		pieces = split_pieces(args.ruin, t, args.ground, variant=args.variant)
	else:
		pieces = [Piece("main", t, (0, 0, 0))]
	variant = Variant(args.variant, pieces, args.ground, args.weight)
	print("aperçu :", render.render_variant(ruin, variant))
	if args.dry_run:
		return 0 if report.ok else 1
	folder = os.path.join(ruins.IMPORTED, args.ruin, args.variant)
	os.makedirs(folder, exist_ok=True)
	t.save(os.path.join(folder, "template.nbt"))
	with open(os.path.join(folder, "variant.json"), "w", encoding="utf-8", newline="\n") as f:
		json.dump({"ground": args.ground, "weight": args.weight, "replace": args.replace,
			"source": os.path.basename(args.file)}, f, indent=2)
		f.write("\n")
	print(f"écrit : {folder}  ->  lancer : python tools/ruins/build_all.py {args.ruin}")
	if not report.ok:
		print("ATTENTION : la variante ne respecte pas encore le contrat (voir les erreurs) ; build_all.py échouera.")
		return 1
	return 0


if __name__ == "__main__":
	sys.exit(main())
