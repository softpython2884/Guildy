"""
Ruin = one or more variants (each one or two template pieces) + worldgen settings. Writes the templates and the
data-pack JSON (structure, template pools, processor list, biome tag) under src/main/resources/data/guildy.

Overworld variants have a "surface" piece (layer 0 = ground floor, replacing the natural top block; projected on
WORLD_SURFACE_WG with start_height 0) and, when they need foundations / cellars / crypts, a "below" piece hung
under it by a jigsaw pair (rigid-to-rigid: the child keeps the ground level of the start piece, so the terrain
adaptation (beard) is computed at the real ground level and not at the bottom of the crypt).
Nether variants are one piece at a fixed height (start_height absolute, no projection).

Template ids: the procedural variant is guildy:ruin/<id> (one piece) or guildy:ruin/<id>/surface + /below;
an imported variant v is guildy:ruin/<id>/<v> or guildy:ruin/<id>/<v>/surface + /below. The start pool lists the
start piece of every variant (weights), each two-piece variant has its own "below" pool.
"""
import json
import os

from .template import Template

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
RESOURCES = os.path.join(ROOT, "src", "main", "resources", "data", "guildy")
RESERVED = ("surface", "below")


class Piece:
	def __init__(self, name, template, offset):
		self.name = name
		self.template = template
		self.offset = offset  # position of the piece origin in the assembled frame


class Variant:
	def __init__(self, name, pieces, ground, weight=1):
		if name in RESERVED:
			raise ValueError(f"variant name '{name}' is reserved")
		self.name = name  # "" = the procedural one
		self.pieces = pieces  # first = start piece
		self.ground = ground  # assembled-frame layer of the ground floor
		self.weight = weight

	@property
	def multi(self):
		return len(self.pieces) > 1

	def assembled(self, rid=""):
		xs, ys, zs = [], [], []
		for p in self.pieces:
			ox, oy, oz = p.offset
			sx, sy, sz = p.template.size
			xs += [ox, ox + sx]
			ys += [oy, oy + sy]
			zs += [oz, oz + sz]
		x0, y0, z0 = min(xs), min(ys), min(zs)
		out = Template(max(xs) - x0, max(ys) - y0, max(zs) - z0, rid)
		for p in self.pieces:
			ox, oy, oz = p.offset
			for (x, y, z), state in p.template.cells.items():
				out.cells[(x + ox - x0, y + oy - y0, z + oz - z0)] = state
			for (x, y, z), tag in p.template.nbt.items():
				out.nbt[(x + ox - x0, y + oy - y0, z + oz - z0)] = tag
		return out


class Ruin:
	def __init__(self, ruin_id, title, dimension, tier, biomes, adaptation, pieces, ground, start_height,
		terrain="minecraft:grass_block", processors=None, salt=0):
		self.id = ruin_id
		self.title = title
		self.dimension = dimension
		self.tier = tier
		self.biomes = biomes  # biome ids / #tags
		self.adaptation = adaptation
		self.variants = [Variant("", pieces, ground)] if pieces else []
		self.start_height = start_height
		self.terrain = terrain  # preview only
		self.processors = processors or []
		self.big = False
		self.set_weight = 1  # weight in the dimension's structure set (matters where ruins share biomes)

	# the procedural variant (kept for the scripts / previews)
	@property
	def pieces(self):
		return self.variants[0].pieces

	@property
	def ground(self):
		return self.variants[0].ground

	@property
	def multi(self):
		return self.variants[0].multi

	def assembled(self):
		return self.variants[0].assembled(self.id)

	def add_variant(self, variant, replace=False):
		if replace:
			self.variants = [v for v in self.variants if v.name]
		self.variants.append(variant)

	def base(self, variant):
		return f"ruin/{self.id}/{variant.name}" if variant.name else f"ruin/{self.id}"

	def template_id(self, variant, piece):
		base = self.base(variant)
		return f"guildy:{base}/{piece.name}" if variant.multi else f"guildy:{base}"

	def below_pool(self, variant):
		return f"guildy:{self.base(variant)}/below"

	# ------------------------------------------------------------------------------------------- writing

	def template_paths(self):
		root = os.path.join(RESOURCES, "structure")
		out = []
		for v in self.variants:
			base = os.path.join(root, *self.base(v).split("/"))
			if v.multi:
				out += [(v, p, os.path.join(base, p.name + ".nbt")) for p in v.pieces]
			else:
				out.append((v, v.pieces[0], base + ".nbt"))
		return out

	def write(self):
		written = []
		for v, piece, path in self.template_paths():
			os.makedirs(os.path.dirname(path), exist_ok=True)
			piece.template.save(path)
			written.append(path)
		written += self.write_worldgen()
		return written

	def _json(self, rel, data):
		path = os.path.join(RESOURCES, *rel.split("/"))
		os.makedirs(os.path.dirname(path), exist_ok=True)
		with open(path, "w", encoding="utf-8", newline="\n") as f:
			json.dump(data, f, indent=2)
			f.write("\n")
		return path

	def write_worldgen(self):
		out = []
		processors = f"guildy:ruin/{self.id}" if self.processors else "minecraft:empty"
		if self.processors:
			out.append(self._json(f"worldgen/processor_list/ruin/{self.id}.json", {"processors": self.processors}))
		out.append(self._json(f"worldgen/template_pool/ruin/{self.id}.json",
			pool([(self.template_id(v, v.pieces[0]), v.weight) for v in self.variants], processors)))
		for v in self.variants:
			for piece in v.pieces[1:]:
				out.append(self._json(f"worldgen/template_pool/{self.base(v)}/{piece.name}.json",
					pool([(self.template_id(v, piece), 1)], processors)))
		structure = {
			"type": "minecraft:jigsaw",
			"biomes": f"#guildy:has_structure/ruin/{self.id}",
			"step": "surface_structures",
			"terrain_adaptation": self.adaptation,
			"spawn_overrides": {},
			"start_pool": f"guildy:ruin/{self.id}",
			"size": 1,
			"start_height": {"absolute": self.start_height},
			"max_distance_from_center": 80,
			"use_expansion_hack": False,
		}
		if self.dimension == "overworld":
			structure["project_start_to_heightmap"] = "WORLD_SURFACE_WG"
		out.append(self._json(f"worldgen/structure/ruin/{self.id}.json", structure))
		out.append(self._json(f"tags/worldgen/biome/has_structure/ruin/{self.id}.json",
			{"replace": False, "values": self.biomes}))
		return out


def pool(locations, processors):
	return {
		"fallback": "minecraft:empty",
		"elements": [{
			"weight": weight,
			"element": {
				"element_type": "minecraft:single_pool_element",
				"location": location,
				"processors": processors,
				"projection": "rigid",
			},
		} for location, weight in locations],
	}


def rule(block, output, probability):
	"""Processor rule: `block` becomes `output` with the given probability (full blocks only: keeps no state)."""
	return {
		"input_predicate": {"predicate_type": "minecraft:random_block_match", "block": block, "probability": probability},
		"location_predicate": {"predicate_type": "minecraft:always_true"},
		"output_state": output,
	}


def rules(*items):
	return {"processor_type": "minecraft:rule", "rules": [rule(*i) for i in items]}


def split_pieces(ruin_id, template, ground, jigsaw_xz=None, variant=""):
	"""
	Splits an assembled overworld template at its ground floor: "surface" (layers ground..top, layer 0 = ground
	floor) and "below" (everything under it), linked by a jigsaw pair at jigsaw_xz (a cell that is void in both:
	the jigsaws become structure_void, i.e. keep the natural block). jigsaw_xz None = first suitable column.
	"""
	from . import deco
	from .build import Builder
	sx, sy, sz = template.size
	below = Template(sx, ground, sz, ruin_id + "/below")
	surface = Template(sx, sy - ground, sz, ruin_id + "/surface")
	for (x, y, z), state in template.cells.items():
		target, yy = (below, y) if y < ground else (surface, y - ground)
		target.cells[(x, yy, z)] = state
		if (x, y, z) in template.nbt:
			target.nbt[(x, yy, z)] = template.nbt[(x, y, z)]
	if jigsaw_xz is None:
		jigsaw_xz = next(((x, z) for z in range(sz) for x in range(sx)
			if surface.get(x, 0, z) is None and below.get(x, ground - 1, z) is None), None)
		if jigsaw_xz is None:
			raise ValueError(f"{ruin_id}: no column void in both pieces for the jigsaw pair")
	jx, jz = jigsaw_xz
	if surface.get(jx, 0, jz) is not None or below.get(jx, ground - 1, jz) is not None:
		raise ValueError(f"{ruin_id}: the jigsaw cell {jigsaw_xz} must be void in both pieces")
	base = f"guildy:ruin/{ruin_id}/{variant}" if variant else f"guildy:ruin/{ruin_id}"
	deco.jigsaw(Builder(surface, 0), jx, 0, jz, "down_north", "guildy:ruin_surface", "guildy:ruin_below",
		base + "/below", "minecraft:structure_void")
	deco.jigsaw(Builder(below, 0), jx, ground - 1, jz, "up_north", "guildy:ruin_below", "minecraft:empty",
		"minecraft:empty", "minecraft:structure_void")
	return [Piece("surface", surface, (0, ground, 0)), Piece("below", below, (0, 0, 0))]
