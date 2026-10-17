#!/usr/bin/env python3
"""
Regenerates tools/ruins/data/blocks.json (block ids, state properties and defaults) and tools/ruins/data/colors.json
(approximate top / side colours used by the preview renderer) for a new Minecraft version.

Inputs:
  1. the "blocks.json" report of the data generator:
       java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --reports --output out
     -> out/reports/blocks.json
  2. the client jar of the same version (only read to average the block textures; nothing is copied).

Usage:  python tools/ruins/gen_block_data.py <reports/blocks.json> <client.jar> <minecraft version>
"""
import io
import json
import os
import sys
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data")

GRASS_TINT = (0x91, 0xBD, 0x59)
FOLIAGE_TINT = (0x77, 0xAB, 0x2F)
WATER_TINT = (0x3F, 0x76, 0xE4)
TINTED_GRASS = {"grass_block", "short_grass", "tall_grass", "fern", "large_fern", "sugar_cane", "potted_fern", "bush"}
TINTED_FOLIAGE = {"oak_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves", "mangrove_leaves", "vine",
	"spruce_leaves", "birch_leaves", "lily_pad", "attached_melon_stem", "attached_pumpkin_stem", "melon_stem",
	"pumpkin_stem"}
TOP_KEYS = ("top", "up", "end", "all", "texture", "cross", "plant", "flower", "lantern", "torch", "pane", "rail",
	"particle", "side")
SIDE_KEYS = ("side", "all", "texture", "front", "north", "cross", "plant", "flower", "lantern", "torch", "pane",
	"wall", "particle", "end")


def load_model(jar, ref, cache):
	"""Resolved texture map + parent chain of a block model."""
	if ref in cache:
		return cache[ref]
	name = ref.split(":")[-1]
	path = "assets/minecraft/models/" + name + ".json"
	try:
		data = json.loads(jar.read(path))
	except KeyError:
		cache[ref] = ({}, [])
		return cache[ref]
	textures, chain = {}, [name]
	if "parent" in data:
		parent_tex, parent_chain = load_model(jar, data["parent"], cache)
		textures.update(parent_tex)
		chain += parent_chain
	textures.update(data.get("textures", {}))
	cache[ref] = (textures, chain)
	return cache[ref]


def resolve(textures, key, depth=0):
	value = textures.get(key)
	while depth < 10:
		if isinstance(value, dict):  # {"sprite": ..., "force_translucent": ...}
			value = value.get("sprite")
		if isinstance(value, str) and value.startswith("#"):
			value = textures.get(value[1:])
			depth += 1
			continue
		break
	return value if isinstance(value, str) else None


def average(jar, tex_ref, cache):
	if tex_ref is None:
		return None
	if tex_ref in cache:
		return cache[tex_ref]
	name = tex_ref.split(":")[-1]
	try:
		img = Image.open(io.BytesIO(jar.read("assets/minecraft/textures/" + name + ".png"))).convert("RGBA")
	except KeyError:
		cache[tex_ref] = None
		return None
	w = img.width
	img = img.crop((0, 0, w, min(w, img.height)))  # first frame of animations
	px = list(img.getdata())
	solid = [p for p in px if p[3] > 40]
	if not solid:
		cache[tex_ref] = None
		return None
	r = sum(p[0] for p in solid) / len(solid)
	g = sum(p[1] for p in solid) / len(solid)
	b = sum(p[2] for p in solid) / len(solid)
	cache[tex_ref] = ((r, g, b), len(solid) / len(px))
	return cache[tex_ref]


def tint(rgb, t):
	return tuple(c * k / 255.0 for c, k in zip(rgb, t))


def first_model(jar, block):
	try:
		states = json.loads(jar.read("assets/minecraft/blockstates/" + block + ".json"))
	except KeyError:
		return None
	if "variants" in states:
		variant = next(iter(states["variants"].values()))
	else:
		parts = states.get("multipart", [])
		if not parts:
			return None
		variant = parts[0]["apply"]
	if isinstance(variant, list):
		variant = variant[0]
	return variant.get("model")


def main():
	if len(sys.argv) != 4:
		print(__doc__)
		sys.exit(1)
	report, client_jar, version = sys.argv[1:]
	with open(report, encoding="utf-8") as f:
		blocks = json.load(f)
	jar = zipfile.ZipFile(client_jar)
	out_blocks = {}
	out_colors = {}
	model_cache, tex_cache = {}, {}
	for block_id, entry in sorted(blocks.items()):
		props = entry.get("properties", {})
		default = next((s.get("properties", {}) for s in entry["states"] if s.get("default")), {})
		out_blocks[block_id] = {"p": props, "d": default} if props else {}
		name = block_id.split(":")[1]
		model = first_model(jar, name)
		if model is None:
			continue
		textures, _ = load_model(jar, model, model_cache)
		top = next((t for t in (resolve(textures, k) for k in TOP_KEYS) if t), None)
		side = next((t for t in (resolve(textures, k) for k in SIDE_KEYS) if t), None)
		top_c = average(jar, top, tex_cache)
		side_c = average(jar, side, tex_cache) or top_c
		top_c = top_c or side_c
		if top_c is None:
			continue
		t_rgb, coverage = top_c
		s_rgb, s_cov = side_c
		if name in TINTED_GRASS:
			t_rgb = tint(t_rgb, GRASS_TINT)
			if name != "grass_block":
				s_rgb = tint(s_rgb, GRASS_TINT)
		elif name in TINTED_FOLIAGE:
			t_rgb, s_rgb = tint(t_rgb, FOLIAGE_TINT), tint(s_rgb, FOLIAGE_TINT)
		elif name in ("water", "bubble_column"):
			t_rgb = s_rgb = WATER_TINT
		# [top r, g, b, side r, g, b, opaque pixel ratio]
		out_colors[block_id] = [round(c) for c in t_rgb] + [round(c) for c in s_rgb] + [round(min(coverage, s_cov), 2)]
	os.makedirs(DATA, exist_ok=True)
	with open(os.path.join(DATA, "blocks.json"), "w", encoding="utf-8") as f:
		json.dump({"version": version, "blocks": out_blocks}, f, separators=(",", ":"), sort_keys=True)
	with open(os.path.join(DATA, "colors.json"), "w", encoding="utf-8") as f:
		json.dump({"version": version, "colors": out_colors}, f, separators=(",", ":"), sort_keys=True)
	print(f"{len(out_blocks)} blocks, {len(out_colors)} colours written to {DATA}")


if __name__ == "__main__":
	main()
