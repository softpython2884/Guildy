#!/usr/bin/env python3
"""
Builds the ruins and writes everything the mod needs under src/main/resources/data/guildy:
  structure/ruin/<id>.nbt (or ruin/<id>/<piece>.nbt), worldgen/structure|template_pool|processor_list/ruin/...,
  worldgen/structure_set/ruins_overworld.json + ruins_nether.json, biome tags, structure tags.
Then validates every template (check.py rules) and renders the previews (tools/ruins/previews/<id>.png).

  python tools/ruins/build_all.py [ids...] [--no-render]
"""
import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import check  # noqa: E402
import render  # noqa: E402
import ruins  # noqa: E402
from ruinlib.ruin import RESOURCES  # noqa: E402

# Structure sets: one per dimension (random spread, unique salts). A set tries its structures by weight until one
# accepts the biome of the chunk, so the biome tags decide which ruin appears where; the weights (Ruin.set_weight)
# only matter in the biomes several ruins share.
SETS = {
	"ruins_overworld": {"spacing": 34, "separation": 12, "salt": 1847203559},
	"ruins_nether": {"spacing": 20, "separation": 7, "salt": 739018562},
}


def write_json(rel, data):
	path = os.path.join(RESOURCES, *rel.split("/"))
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, "w", encoding="utf-8", newline="\n") as f:
		json.dump(data, f, indent=2)
		f.write("\n")
	return path


def write_sets(built):
	by_dim = {"overworld": [], "nether": []}
	for ruin in built:
		by_dim[ruin.dimension].append(ruin)
	for dim, items in by_dim.items():
		name = "ruins_" + dim
		s = SETS[name]
		if not items:
			continue
		write_json(f"worldgen/structure_set/{name}.json", {
			"structures": [{"structure": f"guildy:ruin/{r.id}", "weight": r.set_weight} for r in items],
			"placement": {"type": "minecraft:random_spread", "spacing": s["spacing"], "separation": s["separation"],
				"salt": s["salt"]},
		})
		write_json(f"tags/worldgen/structure/ruins/{dim}.json",
			{"replace": False, "values": [f"guildy:ruin/{r.id}" for r in items]})
	write_json("tags/worldgen/structure/ruins.json",
		{"replace": False, "values": ["#guildy:ruins/overworld", "#guildy:ruins/nether"]})


def main():
	sys.stdout.reconfigure(encoding="utf-8")
	ap = argparse.ArgumentParser()
	ap.add_argument("ids", nargs="*")
	ap.add_argument("--no-render", action="store_true")
	args = ap.parse_args()
	ids = args.ids or ruins.available()
	built = []
	failed = False
	for ruin_id in ids:
		ruin = ruins.load(ruin_id)
		for path in ruin.write():
			pass
		report = check.check_ruin(ruin)
		print(report.text())
		failed |= not report.ok
		if not args.no_render:
			print("  preview:", render.render_ruin(ruin))
			for v in ruin.variants[1:] if ruin.variants and not ruin.variants[0].name else ruin.variants:
				print("  preview:", render.render_variant(ruin, v))
		built.append(ruin)
	write_sets([r for r in built] if not args.ids else [ruins.load(i) for i in ruins.available()])
	print("structure sets and tags written")
	if failed:
		sys.exit(1)


if __name__ == "__main__":
	main()
