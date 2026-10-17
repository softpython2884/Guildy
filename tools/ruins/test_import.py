#!/usr/bin/env python3
"""
Self-test of the schematic converter: every ruin is exported the way a builder would hand it in (DATA markers
replaced by [boss] / [sbire] / [garde] / [entree] signs, loot tables replaced by [coffre ...] signs on the chests,
the whole box filled with air, a structure_void border) as Sponge v2, Sponge v3, Litematica and structure .nbt
files, then converted back. The result must have the same markers, loot tables and blocks as the original.

  python tools/ruins/test_import.py [ids...]
"""
import os
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from ruinlib import formats, nbt as N  # noqa: E402
from ruinlib.template import Template, AIR  # noqa: E402
import import_schematic as I  # noqa: E402
import ruins  # noqa: E402

LABEL = {"guildy:boss": "[boss]", "guildy:minion": "[sbire]", "guildy:guard": "[Garde]", "guildy:entrance": "[Entrée]"}
TIER_LABEL = {"common": "[coffre commun]", "rare": "[coffre rare]", "treasure": "[Coffre trésor]"}


def sign(text, wall_facing=None):
	tag = N.Compound(id=N.String("minecraft:sign"), front_text=N.Compound(
		messages=N.List([N.String(text), N.String(""), N.String(""), N.String("")], N.STRING),
		color=N.String("black"), has_glowing_text=N.Byte(0)), is_waxed=N.Byte(0))
	if wall_facing:
		return f"minecraft:oak_wall_sign[facing={wall_facing},waterlogged=false]", tag
	return "minecraft:oak_sign[rotation=0,waterlogged=false]", tag


def as_builder_file(t):
	"""What a builder hands in: signs instead of markers / loot tables, air everywhere else, a void border."""
	out = Template(t.size[0] + 2, t.size[1], t.size[2] + 2)
	for x in range(out.size[0]):
		for y in range(out.size[1]):
			for z in range(out.size[2]):
				if x in (0, out.size[0] - 1) or z in (0, out.size[2] - 1):
					continue  # structure_void border (written as void)
				out.cells[(x, y, z)] = AIR
	moved = {}
	for (x, y, z), state in t.cells.items():
		out.cells[(x + 1, y, z + 1)] = state
		if (x, y, z) in t.nbt:
			out.nbt[(x + 1, y, z + 1)] = N.Compound(t.nbt[(x, y, z)])
	expected_loot = {}
	for (x, y, z), meta in t.markers():
		p = (x + 1, y, z + 1)
		state, tag = sign(LABEL[meta])
		out.cells[p] = state
		out.nbt[p] = tag
	k = 0
	for (x, y, z), tag in list(t.nbt.items()):
		table = str(tag.get("LootTable", ""))
		if not table.startswith("guildy:chests/ruin/"):
			continue
		p = (x + 1, y, z + 1)
		tier = table.rsplit("/", 1)[1].replace("nether_", "")
		expected_loot[p] = table
		del out.nbt[p]["LootTable"]
		k += 1
		if k % 2 == 0:
			continue  # every other container keeps no sign: it must get the default (common) table
		# a wall sign on a free side of the container, else a standing sign on top
		for f, (dx, dz) in (("south", (0, 1)), ("north", (0, -1)), ("east", (1, 0)), ("west", (-1, 0))):
			q = (p[0] + dx, p[1], p[2] + dz)
			if out.cells.get(q, AIR) == AIR:
				state, stag = sign(TIER_LABEL[tier], wall_facing=f)
				out.cells[q] = state
				out.nbt[q] = stag
				break
		else:
			q = (p[0], p[1] + 1, p[2])
			state, stag = sign(TIER_LABEL[tier])
			out.cells[q] = state
			out.nbt[q] = stag
		moved[p] = q
	return out, expected_loot, moved


def compare(original, converted, expected_loot, nether, k_signed):
	"""Errors between the original ruin and the converted file (positions shifted back by the crop)."""
	errors = []
	om = sorted(original.markers())
	cm = converted.markers()
	if len(om) != len(cm):
		errors.append(f"markers: {len(om)} expected, {len(cm)} found")
	ob = next(p for p, meta in om if meta == "guildy:boss")
	cb = next(p for p, meta in cm if meta == "guildy:boss")
	shift = (ob[0] - cb[0], ob[1] - cb[1], ob[2] - cb[2])
	for (x, y, z), meta in cm:
		if ((x + shift[0], y + shift[1], z + shift[2]), meta) not in om:
			errors.append(f"marker {meta} at {(x, y, z)} not in the original")
	for (x, y, z), state in original.cells.items():
		if state.startswith("minecraft:structure_block") or state.startswith(AIR):
			continue
		c = converted.cells.get((x - shift[0], y - shift[1], z - shift[2]))
		if c != state:
			errors.append(f"block {(x, y, z)}: {state} -> {c}")
			if len(errors) > 12:
				break
	for (x, y, z), tag in original.nbt.items():
		table = str(tag.get("LootTable", ""))
		if not table:
			continue
		ct = converted.nbt.get((x - shift[0], y - shift[1], z - shift[2]), {})
		got = str(ct.get("LootTable", ""))
		default = "guildy:chests/ruin/" + ("nether_" if nether else "") + "common"
		if got != table and got != default:
			errors.append(f"loot {(x, y, z)}: {table} -> {got}")
	return errors


def main():
	sys.stdout.reconfigure(encoding="utf-8")
	ids = sys.argv[1:] or ruins.IDS
	failed = 0
	with tempfile.TemporaryDirectory() as tmp:
		for rid in ids:
			ruin = ruins.load(rid, with_imports=False)
			original = ruin.assembled()
			original.cells = {p: s for p, s in original.cells.items() if not s.startswith("minecraft:jigsaw")}
			nether = ruin.dimension == "nether"
			handed, expected_loot, moved = as_builder_file(original)
			files = {
				"v2.schem": lambda p: formats.write_schem(handed, p, version=2),
				"v3.schem": lambda p: formats.write_schem(handed, p, version=3),
				"build.litematic": lambda p: formats.write_litematic(handed, p),
				"structure.nbt": lambda p: handed.save(p),
			}
			for name, writer in files.items():
				path = os.path.join(tmp, f"{rid}_{name}")
				writer(path)
				raw, fmt = formats.load_any(path)
				converted, msgs = I.convert(raw, nether=nether, air_mode="smart", ground=ruin.ground, natural=())
				errors = compare(original, converted, expected_loot, nether, len(moved))
				status = "OK" if not errors else "ECHEC"
				print(f"[{status}] {rid} {fmt:14s} {converted.size} {len(converted.markers())} marqueurs")
				for e in errors[:12]:
					print("    " + e)
				failed += bool(errors)
	sys.exit(1 if failed else 0)


if __name__ == "__main__":
	main()
