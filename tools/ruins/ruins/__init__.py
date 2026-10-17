"""
The six Guildy ruins. Each module exposes build() -> ruinlib.ruin.Ruin (the procedural variant). Variants
converted from schematics by import_schematic.py live in tools/ruins/imported/<id>/<variant>/ (template.nbt +
variant.json) and are added to the ruin's template pool here.
"""
import importlib
import json
import os

IDS = ["crypte_oubliee", "tour_du_mage", "ziggourat_des_sables", "bastion_de_givre", "autel_infernal",
	"citadelle_des_ames"]
IMPORTED = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "imported")


def available():
	"""Ids whose build script exists (all six once the set is complete)."""
	here = os.path.dirname(os.path.abspath(__file__))
	return [i for i in IDS if os.path.exists(os.path.join(here, i + ".py"))]


def imported_variants(ruin_id):
	"""[(name, template path, settings)] of the converted schematics of a ruin."""
	base = os.path.join(IMPORTED, ruin_id)
	out = []
	if os.path.isdir(base):
		for name in sorted(os.listdir(base)):
			meta = os.path.join(base, name, "variant.json")
			nbt_path = os.path.join(base, name, "template.nbt")
			if os.path.exists(meta) and os.path.exists(nbt_path):
				with open(meta, encoding="utf-8") as f:
					out.append((name, nbt_path, json.load(f)))
	return out


def load(ruin_id, with_imports=True):
	ruin = importlib.import_module(f"ruins.{ruin_id}").build()
	if with_imports:
		from ruinlib.ruin import Variant, Piece, split_pieces
		from ruinlib.template import Template
		for name, path, meta in imported_variants(ruin_id):
			t = Template.load(path, strict=True)
			ground = int(meta.get("ground", 0))
			if ruin.dimension == "overworld" and ground > 0:
				pieces = split_pieces(ruin_id, t, ground, variant=name)
			else:
				pieces = [Piece("main", t, (0, 0, 0))]
			ruin.add_variant(Variant(name, pieces, ground, int(meta.get("weight", 1))), replace=bool(meta.get("replace")))
	return ruin
