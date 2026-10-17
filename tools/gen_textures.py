#!/usr/bin/env python3
"""
Guildy - generator of the optional resource pack: unique weapons, spell scrolls, grimoire and incantation scroll.

Every texture is ORIGINAL 16x16 pixel art. The unique weapons, the grimoire and the incantation scroll are hand-drawn
here as character grids (one character = one pixel, one palette per sprite: 1-pixel hue-shifted outlines, light from
the top-left, 3-5 tone ramps per material) with frame-by-frame animation layers; the spell scrolls are drawn from
simple shapes (rolled parchment + rune). The script writes, under src/main/resources/assets/guildy/:
  textures/item/<name>.png (+ .png.mcmeta when animated), models/item/<name>.json, items/<name>.json
and tools/resourcepack/pack.png (pack icon); stale files of those generated folders are removed. No Minecraft texture
is used or copied: models only reference vanilla *parent models* (item/handheld, item/bow, item/crossbow...) for their
in-hand transforms. Previews: tools/previews/textures.png (every texture, every frame, in-game sizes) and
tools/previews/textures_animated.png (animated PNG playing the animations as in game).

Usage (from the project root):  python tools/gen_textures.py
Pure standard library (zlib + struct for the PNG files). Re-running it reproduces the committed files exactly.
"""
import json
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "guildy")
PACK = os.path.join(ROOT, "tools", "resourcepack")
SIZE = 16


# ---------------------------------------------------------------------------------------------------------- PNG

def write_png(path, width, height, rows):
	"""rows: list of rows, each a list of (r, g, b, a)."""
	raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in rows)

	def chunk(kind, data):
		body = kind + data
		return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

	data = b"\x89PNG\r\n\x1a\n"
	data += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
	data += chunk(b"IDAT", zlib.compress(raw, 9))
	data += chunk(b"IEND", b"")
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, "wb") as f:
		f.write(data)


def rgba(value):
	value = value.lstrip("#")
	if len(value) == 6:
		value += "ff"
	return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4, 6))


TRANSPARENT = (0, 0, 0, 0)


# ---------------------------------------------------------------------------------------------------------- hand-drawn sprites

def paint(rows, palette, frame=0, base=None):
	"""
	A 16x16 frame from a character grid: palette maps a character to a color (hex string, or a list of hex strings
	cycled per frame). '.' keeps the pixel of base (transparent without base), '_' clears it.
	"""
	if len(rows) != SIZE or any(len(row) != SIZE for row in rows):
		raise ValueError("a sprite layer must be 16 rows of 16 characters: %r" % (rows,))
	grid = [list(row) for row in base] if base else [[TRANSPARENT] * SIZE for _ in range(SIZE)]
	for y, row in enumerate(rows):
		for x, ch in enumerate(row):
			if ch == ".":
				continue
			if ch == "_":
				grid[y][x] = TRANSPARENT
				continue
			color = palette[ch]
			if isinstance(color, list):
				color = color[frame % len(color)]
			grid[y][x] = rgba(color)
	return grid


def outlined(rows, char="O", diagonal=False):
	"""The grid with every empty pixel touching a drawn one (4-neighbours, or 8 with diagonal) set to char."""
	near = ((1, 0), (-1, 0), (0, 1), (0, -1)) + (((1, 1), (-1, -1), (1, -1), (-1, 1)) if diagonal else ())
	out = []
	for y, row in enumerate(rows):
		line = ""
		for x, ch in enumerate(row):
			if ch == "." and any(0 <= x + dx < SIZE and 0 <= y + dy < SIZE and rows[y + dy][x + dx] not in "."
								 for dx, dy in near):
				ch = char
			line += ch
		out.append(line)
	return out


def put(grid, points, color):
	"""Sets single pixels (x, y) of a frame (color: hex string)."""
	for x, y in points:
		if 0 <= x < SIZE and 0 <= y < SIZE:
			grid[y][x] = rgba(color)
	return grid


def copy(grid):
	return [list(row) for row in grid]


def gleam(grid, path, head, colors):
	"""A traveling glint: path[head] gets colors[0], the pixels behind it colors[1], colors[2]... (trail)."""
	for i, color in enumerate(colors):
		k = head - i
		if 0 <= k < len(path):
			put(grid, [path[k]], color)
	return grid


def cells(rows, chars):
	"""Coordinates (x, y) of the pixels drawn with one of chars."""
	return [(x, y) for y, row in enumerate(rows) for x, ch in enumerate(row) if ch in chars]


def mix(color, target, t):
	"""color (RGBA tuple) moved toward target (hex) by t in [0, 1]."""
	goal = rgba(target)
	return tuple(int(round(c + (g - c) * t)) for c, g in zip(color[:3], goal[:3])) + (color[3],)


def shine(grid, area, k, strengths=(0.7, 0.3), target="#ffffff"):
	"""A light band crossing a blade (pixels with x - y == k, fading on both sides): polished metal glint."""
	for x, y in area:
		d = abs((x - y) - k)
		if d < len(strengths):
			grid[y][x] = mix(grid[y][x], target, strengths[d])
	return grid


def sweep(base, area, start, end, strengths=(0.7, 0.3), target="#ffffff"):
	"""Frames of a glint band traveling from x - y == start to end (inclusive)."""
	step = 1 if end >= start else -1
	return [shine(copy(base), area, k, strengths, target) for k in range(start, end + step, step)]


def anim(frametime, frames=None, interpolate=False):
	"""The .mcmeta animation section: frametime (ticks), optional frame order ({index, time} items), interpolation."""
	section = {"frametime": frametime}
	if frames is not None:
		section["frames"] = frames
	if interpolate:
		section["interpolate"] = True
	return {"animation": section}


class Sprite:
	"""Frames (lists of 16 rows of RGBA pixels) + the .mcmeta content (None = still)."""

	def __init__(self, frames, meta=None):
		self.frames = frames
		self.meta = meta if len(frames) > 1 else None


def still(rows, palette):
	return Sprite([paint(rows, palette)])


# ---------------------------------------------------------------------------------------------------------- files

# Asset files written by this run: anything else in the generated folders is a stale output and is removed.
WRITTEN = set()
GENERATED_DIRS = [os.path.join(ASSETS, "textures", "item"), os.path.join(ASSETS, "models", "item"),
				  os.path.join(ASSETS, "items")]


def write_sprite(name, sprite):
	path = os.path.join(ASSETS, "textures", "item", name + ".png")
	rows = [row for frame in sprite.frames for row in frame]
	write_png(path, SIZE, SIZE * len(sprite.frames), rows)
	WRITTEN.add(os.path.normcase(path))
	if sprite.meta:
		write_json(path + ".mcmeta", sprite.meta)


def write_json(path, data):
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, "w", encoding="utf-8", newline="\n") as f:
		json.dump(data, f, indent=2)
		f.write("\n")
	WRITTEN.add(os.path.normcase(path))


def remove_stale():
	"""Deletes the textures, models and item definitions of the generated folders that this run did not write."""
	for folder in GENERATED_DIRS:
		for entry in sorted(os.listdir(folder)):
			path = os.path.join(folder, entry)
			if os.path.isfile(path) and os.path.normcase(path) not in WRITTEN:
				os.remove(path)
				print("removed stale", os.path.relpath(path, ROOT))


def model_file(name, parent, texture=None):
	write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
			   {"parent": parent, "textures": {"layer0": "guildy:item/" + (texture or name)}})


def ref(name):
	return {"type": "minecraft:model", "model": "guildy:item/" + name}


def item_file(name, model):
	write_json(os.path.join(ASSETS, "items", name + ".json"), {"model": model})


def simple_model(name, parent="minecraft:item/handheld"):
	model_file(name, parent)
	item_file(name, ref(name))


def bow_model(name):
	"""Same states and thresholds as the vanilla bow (minecraft:items/bow.json)."""
	for suffix in ("", "_pulling_0", "_pulling_1", "_pulling_2"):
		model_file(name + suffix, "minecraft:item/bow")
	item_file(name, {
		"type": "minecraft:condition",
		"property": "minecraft:using_item",
		"on_false": ref(name),
		"on_true": {
			"type": "minecraft:range_dispatch",
			"property": "minecraft:use_duration",
			"scale": 0.05,
			"entries": [
				{"threshold": 0.65, "model": ref(name + "_pulling_1")},
				{"threshold": 0.9, "model": ref(name + "_pulling_2")},
			],
			"fallback": ref(name + "_pulling_0"),
		},
	})


def crossbow_model(name):
	"""Same states and thresholds as the vanilla crossbow (minecraft:items/crossbow.json)."""
	for suffix in ("", "_pulling_0", "_pulling_1", "_pulling_2", "_arrow", "_firework"):
		model_file(name + suffix, "minecraft:item/crossbow")
	item_file(name, {
		"type": "minecraft:select",
		"property": "minecraft:charge_type",
		"cases": [
			{"when": "arrow", "model": ref(name + "_arrow")},
			{"when": "rocket", "model": ref(name + "_firework")},
		],
		"fallback": {
			"type": "minecraft:condition",
			"property": "minecraft:using_item",
			"on_false": ref(name),
			"on_true": {
				"type": "minecraft:range_dispatch",
				"property": "minecraft:crossbow/pull",
				"entries": [
					{"threshold": 0.58, "model": ref(name + "_pulling_1")},
					{"threshold": 1.0, "model": ref(name + "_pulling_2")},
				],
				"fallback": ref(name + "_pulling_0"),
			},
		},
	})


def grimoire_model():
	"""guildy:grimoire: one model per spell, selected by the string 0 of minecraft:custom_model_data."""
	model_file("grimoire", "minecraft:item/generated")
	cases = []
	for spell in GRIMOIRE_SIGILS:
		model_file("grimoire_" + spell, "minecraft:item/generated")
		cases.append({"when": spell, "model": ref("grimoire_" + spell)})
	item_file("grimoire", {
		"type": "minecraft:select",
		"property": "minecraft:custom_model_data",
		"index": 0,
		"cases": cases,
		"fallback": ref("grimoire"),
	})


# ---------------------------------------------------------------------------------------------------------- unique weapons
# Vanilla orientation: blades, heads and prongs toward the top-right corner, handles toward the bottom-left; bows and
# crossbows aim at the top-left corner (their in-hand transforms come from the vanilla parent models).

def foudrelame():
	"""Storm-steel broadsword with a lightning core, gold winged guard and a storm gem; the bolt crackles."""
	rows = [
		".............ooO",
		"............oWLO",
		"...........oWZMO",
		"..........oWZMDO",
		".........oWZZZO.",
		"........oWLMZO..",
		"..HH...oWLMZO...",
		".HgGH.oWZZZO....",
		"..HgGoWZMDO.....",
		"...HgGZMDO......",
		"....HCcDO.......",
		"...OPphgH.......",
		"..OGhOHhgH......",
		".OPpO..HhgH.....",
		"OCcO....HH......",
		"ckO.............",
	]
	pal = {"O": "#0e0f26", "o": "#262a56", "W": "#8794cc", "L": "#5f6cab", "M": "#444d88", "D": "#2d3464",
		   "Z": "#c8f6ff", "G": "#ffe98a", "g": "#e0a632", "h": "#a0601a", "H": "#4a2608",
		   "P": "#3c4a92", "p": "#232a5c", "C": "#d8feff", "c": "#46d0f6", "k": "#1c5aa8"}
	base = paint(rows, pal)
	# the bolt, from the guard to the tip
	path = [(6, 9), (7, 8), (8, 7), (9, 7), (10, 7), (11, 6), (12, 5), (13, 4), (12, 4), (11, 4), (12, 3), (13, 2)]
	frames = [copy(base)]
	for head in range(len(path) + 3):
		frames.append(gleam(copy(base), path, head, ["#ffffff", "#fff6b0", "#c8f6ff"]))
	flash = put(put(copy(base), path, "#ffffff"), [(10, 2), (14, 5), (5, 6)], "#bff6ff")
	frames.append(flash)
	order = [{"index": 0, "time": 40}] + list(range(1, len(frames) - 1)) + [{"index": len(frames) - 1, "time": 2},
																			  {"index": 0, "time": 2},
																			  {"index": len(frames) - 1, "time": 2}]
	return Sprite(frames, anim(1, order))


def croc_de_givre():
	"""Faceted ice blade, frost crescent guard with icicle claws, snow-crystal pommel; a frost glint runs to the tip."""
	rows = [
		".............ooO",
		"............oWLO",
		"...........oWLMO",
		"..........oWWMO.",
		"....T....oWLMDO.",
		"...TST..oWLMDO..",
		"..TSsT.oWLMFO...",
		".TSST.oWWMDO....",
		"..TsSoWLMFO.....",
		"...TsWLMDO......",
		"....TCsDOTT.....",
		"...OPptsTsST....",
		"..OPpOTtssT.....",
		".OPpO..TtT......",
		"OCcO....T.......",
		"ccO.............",
	]
	pal = {"O": "#0a2742", "o": "#1c5480", "W": "#f4fdff", "L": "#b6e8fb", "M": "#74c0e8", "D": "#3f88c4",
		   "F": "#2a62a0", "S": "#eef8ff", "s": "#a2c2dc", "t": "#6890b4", "T": "#1c3a58",
		   "P": "#3a66a0", "p": "#21406c", "C": "#ffffff", "c": "#8ee8ff"}
	base = paint(rows, pal)
	frames = [copy(base)] + sweep(base, cells(rows, "WLMDFo"), -6, 15)
	star = put(copy(base), [(14, 1), (13, 1), (14, 0), (15, 1), (14, 2)], "#ffffff")
	put(star, [(12, 1), (14, 3)], "#c8f4ff")
	frames.append(star)
	order = [{"index": 0, "time": 50}] + list(range(1, len(frames) - 1)) + [{"index": len(frames) - 1, "time": 5}]
	return Sprite(frames, anim(1, order))


def murmure_du_neant():
	"""Wavy obsidian dagger with a void-glowing fuller, gunmetal guard with a void eye; shadows drift off the blade."""
	rows = [
		".............ooO",
		"............oKVO",
		"...........oKVkO",
		"...........oKVkO",
		"..........oKVkO.",
		".....HH.oKVkO...",
		"....HgGoKVkO....",
		".....HgGVkO.....",
		"......HEeH......",
		".....OPphgH.....",
		"....OwpOHhgH....",
		"...OPpO..HH.....",
		"..OwpO..........",
		".OPpO...........",
		"OCcO............",
		"cxO.............",
	]
	pal = {"O": "#0a0614", "o": "#2a1c4a", "K": "#4c3c7c", "k": "#1e1634", "V": "#c45cff",
		   "G": "#bcaedc", "g": "#7e6ca8", "h": "#54467a", "H": "#140c22", "E": "#ff6cf6", "e": "#b020d8",
		   "P": "#5a4482", "p": "#33264e", "w": "#9a6ac8", "C": "#f4b0ff", "c": "#a838e0", "x": "#4a1478"}
	core = sorted(cells(rows, "V"), key=lambda p: p[0])
	pulse = ["#f2b4ff", "#d77cff", "#b44cf2", "#8f34d6", "#b44cf2", "#d77cff"]
	motes = [[(9, 4), (8, 3), (7, 2), (6, 1)], [(12, 7), (13, 6), (14, 5), (15, 4)]]
	frames = []
	for f in range(12):
		g = paint(rows, pal)
		for i, p in enumerate(core):
			put(g, [p], pulse[(f - i) % len(pulse)])
		mote = motes[(f // 6) % 2]
		step = f % 6
		if step < 4:
			put(g, [mote[step]], ["#d77cff", "#a24ae0", "#6a2aa8", "#3e1a6a"][step])
		frames.append(g)
	return Sprite(frames, anim(3))


def coutelas_du_contrebandier():
	"""Broad curved cutlass, gold knuckle-bow guard, red leather grip, ruby pommel; a glint runs along the blade."""
	rows = [
		"..............oO",
		".............oWO",
		"............oWLO",
		"...........oWLMO",
		"..........oWLMDO",
		"........oWWLMDO.",
		".......oWLMMDO..",
		"...HH.oWLMDDO...",
		"..HGGoWLMDO.....",
		"...HgGLMDO......",
		"....HgGDO.......",
		"...OPpgGGH......",
		"..OPpOHHgH......",
		".OPpO.HgH.......",
		"OPpO.HgH........",
		"GRggggH.........",
	]
	pal = {"O": "#1c2028", "o": "#3a4250", "W": "#f4f6f8", "L": "#c8ced6", "M": "#8e98a6", "D": "#5e6878",
		   "G": "#ffe68a", "g": "#e0aa38", "h": "#9a6418", "H": "#4a2c0a",
		   "P": "#9a3030", "p": "#5a1a1c", "R": "#ff4a5a"}
	base = paint(rows, pal)
	frames = [copy(base)] + sweep(base, cells(rows, "WLMDo"), -6, 15, (0.6, 0.25))
	order = [{"index": 0, "time": 80}] + list(range(1, len(frames)))
	return Sprite(frames, anim(1, order))


def hache_sylvestre():
	"""Bearded axe of verdant steel with a gold leaf inlay, on a living haft wrapped in vine with a sprouting leaf."""
	rows = [
		".......OEO......",
		".....OOELLO.....",
		"....OEeLLMMO.O..",
		"...OEeLLyMMDOaK.",
		"..OEeLLyYMDOaMO.",
		"..OEeLMMDDOnMDO.",
		".OEeMOOODOaKOO..",
		"OeOOOO..BbK.....",
		".O.OffOBaK......",
		"....OFnaK.......",
		".....BbK........",
		"....BaK.........",
		"...BnK..........",
		"..BaK...........",
		"...K............",
		"................",
	]
	pal = {"O": "#142010", "E": "#e4ffcc", "e": "#a6d684", "L": "#74ac5e", "M": "#4f8446", "D": "#335a38",
		   "y": "#ffd75a", "Y": "#c08a1e", "B": "#4a2c14", "K": "#2a1708", "a": "#9a6a38", "b": "#74491f",
		   "n": "#3f8f34", "f": "#9be060", "F": "#4a9a30"}
	return still(rows, pal)


def pioche_du_titan():
	"""Massive pickaxe of titan stone-steel, bronze bands, a molten heart gem and two runes that beat slowly."""
	rows = [
		".......OOOOOO...",
		"....OOOGLLLLLO..",
		"..OOLlrgllMGcGO.",
		".OLlMMDhMMGcCkgO",
		"OLlMDOOOOOhgkgDO",
		"OlDOO....OahlMDO",
		".OO.....OabOlMDO",
		".......OabOOlMDO",
		"......OiiO.OGghO",
		".....OabO..OlDO.",
		"....OabO...OlrO.",
		"...OabO...OlMDO.",
		"..OiiO....OlDO..",
		".OabO....OlMDO..",
		"..OO.....OLDO...",
		"..........OO....",
	]
	pal = {"O": "#1a1816", "L": "#d4d0c8", "l": "#a19c93", "M": "#77726a", "D": "#4f4b45",
		   "G": "#ffcf6e", "g": "#c88a2c", "h": "#7e4c14", "a": "#6e4a30", "b": "#452d1d", "i": "#9ca2ac"}
	beat = [("#fff2a8", "#ffb02a", "#d0500e", "#e0701a"), ("#fff8d0", "#ffc850", "#e8701e", "#ff9a30"),
			("#ffffff", "#ffe080", "#ff9030", "#ffc860"), ("#fff8d0", "#ffc850", "#e8701e", "#ff9a30"),
			("#fff2a8", "#ffb02a", "#d0500e", "#e0701a"), ("#f0d890", "#e09020", "#b04008", "#a84a12")]
	frames = [paint(rows, dict(pal, C=light, c=mid, k=dark, r=rune)) for light, mid, dark, rune in beat]
	return Sprite(frames, anim(5, interpolate=True))


def pic_du_filon():
	"""Miner's pick of bright steel set with an ore vein (gold, diamond, emerald, redstone) that twinkles in turn."""
	rows = [
		".......OOOOOOO..",
		"....OOOLLLLLLLO.",
		"..OOLlyMdMyMMllO",
		".OLlMDOOOODMelMO",
		"OLMDOO....OaYlDO",
		".OOO.....OabYlDO",
		"........OabOOyDO",
		".......OabO.OlMO",
		"......OabO..OrDO",
		".....OabO...OyO.",
		"....OabO...OlDO.",
		"...OwwO....OyDO.",
		"..OwwO....OlDO..",
		".OabO.....OlDO..",
		"..OO......OLO...",
		"...........O....",
	]
	pal = {"O": "#1a2028", "L": "#dde6ee", "l": "#a2b0be", "M": "#6e7d8e", "D": "#4a5664", "y": "#ffd84a",
		   "Y": "#c8922a", "d": "#7ff6e8", "e": "#4cf08a", "r": "#ff4040", "a": "#a87a44", "b": "#6e4a24",
		   "w": "#6a3e22"}
	base = paint(rows, pal)
	vein = cells(rows, "y")
	frames = [copy(base)]
	for x, y in ((9, 2), (12, 3), (13, 8)):
		frames.append(put(copy(base), [(x, y)], "#ffffff"))
		half = copy(base)
		half[y][x] = mix(base[y][x], "#ffffff", 0.5)
		frames.append(half)
	for p in vein:
		frames.append(put(copy(base), [p], "#fff8d0"))
	order = [{"index": 0, "time": 30}] + [{"index": i, "time": 2} for i in range(1, len(frames))]
	return Sprite(frames, anim(2, order))


def marteau_du_seisme():
	"""Earthquake war hammer: basalt head between steel caps, split by a glowing lava fissure that throbs."""
	rows = [
		".......OIO......",
		"......OIIiO.....",
		".....OIIijSO....",
		"....OIIijSSsO...",
		"....OiijSscssO..",
		".....OjstccsstO.",
		"......OttscctjiO",
		".......OttscjijO",
		"......OabttjijO.",
		".....OabOOjijO..",
		"....OabO..OjO...",
		"...OwWO....O....",
		"..OwWO..........",
		".OabO...........",
		"OabO............",
		".OO.............",
	]
	pal = {"O": "#141518", "I": "#eef2f8", "i": "#b0b8c6", "j": "#6a7282", "S": "#8a8278", "s": "#615a52",
		   "t": "#403b36", "a": "#8a8e98", "b": "#545862", "w": "#7a4a2a", "W": "#a86a3a"}
	pulse = ["#e8500c", "#ff7a1a", "#ffa838", "#ffd870", "#ffa838", "#ff7a1a"]
	frames = [paint(rows, dict(pal, c=color)) for color in pulse]
	return Sprite(frames, anim(4, interpolate=True))


def trident_du_leviathan():
	"""Abyssal trident: aquamarine fang prongs, gold-coral crossbar with a glowing abyss pearl, scaled shaft."""
	rows = [
		"........OTO..OTT",
		"........TPO..OPO",
		".....OOOPO..OPpO",
		"....OGGPO..OPpO.",
		".....OGGOOOPpO.O",
		"......OGEePO..OT",
		".......OeEO..OPT",
		".......OaGGOOPO.",
		"......OzOOGGPO..",
		".....OaO..OGhO..",
		"....OzO....OO...",
		"...OaO..........",
		"..OzO...........",
		".OaO............",
		"OzO.............",
		".O..............",
	]
	pal = {"O": "#08262e", "T": "#e8fffa", "P": "#7ee8da", "p": "#2fa8a4", "G": "#f2c878", "g": "#c08436",
		   "h": "#76441a", "a": "#2f6f8c", "z": "#5ab4c4"}
	glow = [("#ffffff", "#5ad8f0"), ("#dffcff", "#3ab8e0"), ("#bff4ff", "#2a98d0"), ("#dffcff", "#3ab8e0")]
	prongs = cells(rows, "TPp")
	frames = []
	for f in range(8):
		light, dark = glow[f % 4]
		g = paint(rows, dict(pal, E=light, e=dark))
		shine(g, prongs, -2 + f * 3, (0.55, 0.25))
		frames.append(g)
	return Sprite(frames, anim(3))


def faux_des_ames():
	"""Soul scythe: crescent of dark soul-steel with a soul-fire edge, a soul gem at the collar, rising wisps."""
	rows = [
		".......OOOOOOO.O",
		"......OLLLLLLwOa",
		"....OOlMMMfffGaO",
		"...OlMDfffOOOaO.",
		"..OlMffOOO.OaO..",
		"..OlMfO...OwO...",
		".OlMfO...OaO....",
		"OlMfO...OaO.....",
		"OLfO...OaO......",
		".OO...OaO.......",
		".....OwO........",
		"....OaO.........",
		"...OaO..........",
		"..OaO...........",
		".OwO............",
		"..O.............",
	]
	pal = {"O": "#0b0b14", "L": "#c4c8da", "l": "#9296ae", "M": "#5a5e78", "D": "#3c3e56", "w": "#e4dcc4",
		   "a": "#6a5048"}
	fire = ["#e0ffff", "#8ff2ff", "#38cce0", "#1c90a8"]
	edge = sorted(cells(rows, "f"), key=lambda p: (p[0] - p[1]))
	wisps = [[(5, 1), (5, 0), (6, 0)], [(3, 2), (2, 1), (2, 0)], [(1, 5), (0, 4), (0, 3)]]
	flicker = [0, 2, 1, 3, 1, 0, 2, 1]
	frames = []
	for f in range(8):
		g = paint(rows, dict(pal, G=["#e0ffff", "#9ff4ff", "#5ad8ee", "#9ff4ff"][f % 4], f="#8ff2ff"))
		for i, p in enumerate(edge):
			put(g, [p], fire[(flicker[(i + f) % 8] + (i % 2)) % 4])
		for k, path in enumerate(wisps):
			step = (f + k * 3) % 8
			if step < 3:
				put(g, [path[step]], ["#bff8ff", "#6ad8ea", "#2a7a8c"][step])
		frames.append(g)
	return Sprite(frames, anim(3))


def bottes_de_zephyr():
	"""Cloud-white boots with gold cuffs set with a sky gem, gold straps and feathered ankle wings; gusts of wind
	stream out from under the soles (the wearer barely touches the ground)."""
	rows = outlined([
		"................",
		"................",
		".F............F.",
		"FFfGCg....GCgfFF",
		"fFeLMD....LMDeFf",
		".feLMD....LMDef.",
		"...LMD....LMD...",
		"...gGg....gGg...",
		"..LLMD....LMMD..",
		"..LMMD....LMMD..",
		".LMMMD....LMMMD.",
		".ggggh....ggggh.",
		"................",
		"................",
		"................",
		"................",
	])
	pal = {"O": "#1d3a5c", "L": "#ffffff", "M": "#cfe6fb", "D": "#8fb8e0", "G": "#ffe48a", "g": "#e0a830",
		   "h": "#a06a1c", "C": "#5ad8ff", "F": "#ffffff", "f": "#dcefff", "e": "#a8cdee"}
	base = paint(rows, pal)
	fade = ["#f0fbff", "#c4e8fa", "#94c8e8", "#6aa4cc"]
	frames = []
	for f in range(8):
		g = copy(base)
		for row, start in ((13, 0), (14, 4)):
			step = f - start
			if 0 <= step < 4:
				put(g, [(3 - step, row), (4 - step, row)], fade[step])
				put(g, [(12 + step, row), (11 + step, row)], fade[step])
		if f % 4 < 2:
			put(g, [(1, 2), (14, 2)], "#dcefff")
		frames.append(g)
	return Sprite(frames, anim(2))


def bottes_du_coursier():
	"""Green riding boots of a guild courier: folded tan cuffs, brass buckles, dark heeled soles."""
	rows = outlined([
		"................",
		"................",
		"..CCCC....CCCC..",
		"..cccc....cccc..",
		"...LMD....LMD...",
		"...LMD....LMD...",
		"...bBb....bBb...",
		"...LMD....LMD...",
		"..LLMD....LMMD..",
		"..LMMD....LMMD..",
		".LMMMD....LMMMD.",
		".sssSs....sSsss.",
		"................",
		"................",
		"................",
		"................",
	])
	pal = {"O": "#142a18", "L": "#7cc86e", "M": "#4a9a4e", "D": "#2e6a36", "C": "#f0d8a0", "c": "#c8a060",
		   "b": "#a8741e", "B": "#ffe080", "s": "#4a2e1a", "S": "#6e4a2c"}
	return still(rows, pal)


def line_cells(x0, y0, x1, y1):
	"""Bresenham line from (x0, y0) to (x1, y1)."""
	out = []
	dx, dy = abs(x1 - x0), -abs(y1 - y0)
	sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
	err = dx + dy
	while True:
		out.append((x0, y0))
		if x0 == x1 and y0 == y1:
			return out
		e2 = 2 * err
		if e2 >= dy:
			err += dy
			x0 += sx
		if e2 <= dx:
			err += dx
			y0 += sy


PHOENIX_BOW = [
	".........OyOOyOf",
	".......OOororoyF",
	".....OOroGGgGggO",
	"....OorGgOOOO.O.",
	"...ORwwOO.....O.",
	"..OowOO.........",
	".OrGO...........",
	".OoGO...........",
	".OrgO...........",
	"OoGO............",
	"yrgO............",
	"OoGO............",
	"OrgO............",
	"yoGO............",
	"OygO............",
	"fFOOO...........",
]


def arc_du_phenix(pull):
	"""
	Phoenix bow (vanilla bow geometry: aims at the top-left corner): feathered limbs of red and gold with flaming
	tips, a ruby grip; pull 1..3: a flaming arrow nocked on a string drawn toward the bottom-right corner.
	"""
	pal = {"O": "#2a0e06", "y": "#ffd24a", "o": "#ff8a2a", "r": "#e0381a", "G": "#ffd870", "g": "#c88a2a",
		   "w": "#7a1a10", "R": "#ff5a3a", "F": "#ffd040", "f": "#ff7a1a"}
	base = paint(PHOENIX_BOW, pal)
	string = "#fff0c0"
	top, bottom = (13, 3), (3, 13)
	if pull == 0:
		put(base, line_cells(*top, *bottom), string)
		head = None
	else:
		nock = 9 + pull
		put(base, line_cells(*top, nock, nock) + line_cells(nock, nock, *bottom), string)
		tip = nock - 9
		put(base, [(k, k) for k in range(tip + 2, nock - 2)], "#e8c890")
		put(base, [(nock - 1, nock - 1), (nock - 2, nock - 2)], "#c0281a")
		put(base, [(nock - 1, nock - 2), (nock - 2, nock - 1), (nock - 2, nock - 3), (nock - 3, nock - 2)], "#ff6a3a")
		head = tip
	frames = []
	flames = [("#fff2a0", "#ffb030", "#ff5a10"), ("#ffd040", "#ff8a1a", "#e03a10"), ("#ffffff", "#ffd050", "#ff7a1a"),
			  ("#ffd040", "#ff8a1a", "#e03a10")]
	for f, (hot, warm, red) in enumerate(flames):
		g = copy(base)
		put(g, [(15, 1), (1, 15)], hot if f % 2 == 0 else warm)
		put(g, [(15, 0), (0, 15)], warm if f % 2 == 0 else red)
		if f in (1, 2):
			put(g, [(14, 0), (0, 14)], red)
		if head is not None:
			put(g, [(head, head)], hot)
			put(g, [(head + 1, head + 1)], warm)
			put(g, [(head + 2, head + 1), (head + 1, head + 2)], red if f % 2 else warm)
			if f % 2 == 0 and head > 0:
				put(g, [(head - 1, head)], warm)
		frames.append(g)
	return Sprite(frames, anim(3))


CROSSBOW_STATES = ("standby", "pulling_0", "pulling_1", "pulling_2", "arrow", "firework")

TRACKER_CROSSBOW = [
	"........OOOOOO..",
	"......OOAAAAvAO.",
	"....OOAABBBBBBO.",
	"...OAABBOOOOO...",
	"..OAWwOO........",
	".OAORwdO........",
	".OABOWwdO.......",
	".OABObWwdO......",
	"OABO.OWwdO......",
	"OvBO..OWwdO.....",
	"OABO...ObwdO....",
	"OABO....OWwdO...",
	"OABO.....OWwdO..",
	"OABO......OWwuO.",
	".OO........OuuO.",
	"............OO..",
]


def arbalete_du_traqueur(state):
	"""
	Tracker crossbow (vanilla crossbow geometry: aims at the top-left corner): dark steel prod with green leather
	grips, walnut stock with brass fittings and a red marking lens that glows. state: standby, pulling_0..2, arrow,
	firework (the string is latched on the stock at the nut when drawn).
	"""
	pal = {"O": "#1a120c", "A": "#b4bcc6", "B": "#5e6670", "v": "#4e8a3e", "W": "#93603a", "w": "#6c4428",
		   "d": "#4a2c18", "b": "#e0aa48", "u": "#eadfc4", "R": "#ff3a30"}
	string = "#a9a9b4"
	top, bottom = (12, 3), (3, 12)
	nut = {"standby": None, "pulling_0": 9, "pulling_1": 10}.get(state, 12)
	extra = []
	if nut is None:
		extra.append((line_cells(*top, *bottom), string))
	else:
		extra.append((line_cells(*top, nut, nut) + line_cells(*bottom, nut, nut), string))
	if state == "arrow":
		extra.append(([(k, k) for k in range(4, 11)], "#e8d4a8"))
		extra.append(([(10, 11), (11, 10), (11, 11)], "#c8282a"))
		extra.append(([(3, 3), (4, 3), (3, 4)], "#9aa2ac"))
		extra.append(([(2, 2)], "#ffffff"))
	elif state == "firework":
		extra.append(([(k, k) for k in range(5, 11)], "#3a3e4c"))
		extra.append(([(1, 1), (2, 1), (1, 2), (3, 2), (2, 3), (3, 4), (4, 3)], "#c8282a"))
		extra.append(([(2, 2), (3, 3), (4, 4)], "#f04040"))
		extra.append(([(0, 0), (1, 0), (0, 1)], "#f4f4f4"))
	frames = []
	for glow in ("#ff3a30", "#ff6a50", "#ffa890", "#ff6a50"):
		g = paint(TRACKER_CROSSBOW, dict(pal, R=glow))
		for points, color in extra:
			put(g, points, color)
		frames.append(g)
	return Sprite(frames, anim(5, interpolate=True))


# ---------------------------------------------------------------------------------------------------------- grimoire
# One texture per selected spell (item definition: select on custom_model_data string 0, fallback = dormant book).

GRIMOIRE = [
	"OOOOOOOOOOOOO...",
	"OMmcccMcccmMOO..",
	"OmGkkkkkkkGmOPO.",
	"OckKKKKKKKcCOpkO",
	"OckKKKKKKKcCOPkO",
	"OckKKKKKKKcCOpkO",
	"OmkKKKKKKKcmOMmO",
	"OckKKKKKKKcmmEMO",
	"OckKKKKKKKcnOmnO",
	"OckKKKKKKKcCOpkO",
	"OcGcccccccGCOPkO",
	"OmCCCCCCCCCnOpkO",
	"OMmkkknkkknnOPkO",
	"OOOOOOOOOOOOOpkO",
	".OPpPpPpPRPpPpkO",
	"..OkkkkkkRkkkkkO",
]

GRIMOIRE_PALETTE = {"O": "#14070f", "c": "#8c4a76", "C": "#64305a", "k": "#42193e", "K": "#1f0b1f",
					"M": "#ffe9a8", "m": "#dcae4c", "n": "#94661e", "G": "#ffd877", "g": "#b98632",
					"P": "#fbeec4", "p": "#d6b77a", "R": "#c8283a", "E": "#6a6074"}

# Sigils of the grimoire (7 x 7, drawn in the 7 x 7 panel of the cover): the motifs of the spell scroll runes.
GRIMOIRE_SIGILS = {
	"pluie_de_meteores": ["......#", ".....#.", "....#..", "..##...", ".###...", "####...", ".##...."],
	"tempete_de_foudre": ["....##.", "...##..", "..##...", ".#####.", "...##..", "..##...", ".##...."],
	"blizzard": ["#..#..#", ".#.#.#.", "..###..", "#######", "..###..", ".#.#.#.", "#..#..#"],
	"nova_solaire": ["#..#..#", "..###..", ".#####.", "#######", ".#####.", "..###..", "#..#..#"],
	"singularite": ["#######", "......#", ".####.#", ".#..#.#", ".#.##.#", ".#....#", ".######"],
	"egide": ["#######", "##.#.##", "#######", "#######", ".#####.", "..###..", "...#..."],
	"rappel": ["...#...", "..###..", ".#####.", "##...##", "##...##", "##...##", "#######"],
	"gardiens": [".#####.", "#######", "#..#..#", "#######", ".#...#.", ".#####.", "......."],
	"benediction": [".......", ".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."],
	"revelation": [".......", ".#####.", "#..#..#", "#.###.#", "#..#..#", ".#####.", "......."],
	"seisme": ["...#...", "...##..", "....#..", "...##..", "..#.#..", ".#...#.", "#######"],
	"rayon_arcanique": ["...#...", "...#...", "#######", "...#...", "..#.#..", ".#...#.", "#.....#"],
}

# Sigil colors that differ from the scroll palette (the earthy browns of the earthquake vanish on the dark panel).
GRIMOIRE_GLOW = {"seisme": ["#f4c070", "#d8913e", "#a8662a", "#d8913e"]}

DORMANT_SIGIL = ["..###..", ".#...#.", "#.....#", "#..#..#", "#.....#", ".#...#.", "..###.."]


def grimoire(spell=None):
	"""
	Ancient spellbook: plum leather cover with gold corners and studs, a recessed panel (bevelled), gilded page
	edges, a gem clasp and a red ribbon. With a spell: its sigil glows in the colors of its scroll (palette cycling, as
	on the scrolls) with a halo in the panel and the clasp gem pulses; without (fallback): the dormant book.
	"""
	if spell is None:
		g = paint(GRIMOIRE, GRIMOIRE_PALETTE)
		for y, line in enumerate(DORMANT_SIGIL):
			for x, ch in enumerate(line):
				if ch == "#":
					put(g, [(3 + x, 3 + y)], "#6e5434")
		put(g, [(6, 6)], "#8a7048")
		return Sprite([g])
	palette = GRIMOIRE_GLOW.get(spell, SCROLL_RUNES[spell][1])
	sigil = [(3 + x, 3 + y) for y, line in enumerate(GRIMOIRE_SIGILS[spell]) for x, ch in enumerate(line) if ch == "#"]
	mark = set(sigil)
	halo = [(x, y) for x in range(3, 10) for y in range(3, 10) if (x, y) not in mark
			and any((x + dx, y + dy) in mark for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
	frames = []
	for f in range(4):
		g = paint(GRIMOIRE, GRIMOIRE_PALETTE)
		for x, y in halo:
			g[y][x] = mix(g[y][x], palette[(f + 2) % 4], 0.3)
		for i, p in enumerate(sorted(sigil, key=lambda q: (q[0] + q[1], q[0]))):
			put(g, [p], palette[(i + f) % 4])
		put(g, [(13, 7)], palette[f % 4])
		g[6][13] = mix(rgba(GRIMOIRE_PALETTE["M"]), palette[f % 4], 0.35)
		frames.append(g)
	return Sprite(frames, anim(4))


# ---------------------------------------------------------------------------------------------------------- incantation scroll

def parchemin_incantation():
	"""
	Ritual parchment for summoning ruin bosses, from the spell scroll family (same rolled shape and shading) but dark:
	ash-grey stained paper with torn corners, ebony rolls with bone knobs, a horned skull rune in blood that throbs
	(its eyes burn) and drips, a blood-red wax seal on black ribbons.
	"""
	c = Canvas()
	c.ascii([
		"................",
		"................",
		".BRRRRRRRRRRRRB.",
		".BRRRRRRRRRRRRB.",
		"....PPPPPPPPP...",
		"...PPPPPPPPPP...",
		"...PPPPPPPPPP...",
		"...PPPPPPPPPP...",
		"...PPPPPPPPPP...",
		"...PPPPPPPPPP...",
		"...PPPPPPPPPP...",
		"...PPPPPPPPP....",
		".BRRRRRRRRRRRRB.",
		".BRRRRRRRRRRRRB.",
		"......KSSK......",
		"......K.SK......",
	], {"R": "roll", "B": "bone", "P": "paper", "S": "seal", "K": "ribbon"})
	rune = [
		".#....#.",
		"#......#",
		"#.####.#",
		".######.",
		".#e##e#.",
		"..####..",
		"..#..#..",
	]
	for y, line in enumerate(rune):
		for x, ch in enumerate(line):
			if ch in "#e":
				c.put(4 + x, 4 + y, "rune" if ch == "#" else "eye")
	# Dried blood halo around the rune (contrast on the paper, as the ink halo of the spell scrolls).
	for y in range(SIZE):
		for x in range(SIZE):
			if c.get(x, y) == "paper" and any((c.get(x + dx, y + dy) or "") in ("rune", "eye")
											  for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
				c.put(x, y, "halo")
	for x, y in ((3, 10), (12, 5), (11, 10)):
		c.put(x, y, "stain")
	beat = [("#ff3a2a", "#ffc070"), ("#e0201a", "#ff7a30"), ("#b51212", "#a02010"), ("#8e0c0c", "#3a0808"),
			("#b51212", "#a02010"), ("#e0201a", "#ff7a30")]
	frames = []
	for f, (blood, eye) in enumerate(beat):
		styles = {"roll": ("#4e444c", "#2e262c", "#181216"), "bone": ("#f4ead2", "#d8caa8", "#9a8a66"),
				  "paper": ("#a0988a", "#7c7466", "#5a5246"), "halo": flat("#5a4440"), "stain": flat("#5e4038"),
				  "seal": ("#e83030", "#b01818", "#5a0808"), "ribbon": flat("#1c1418"),
				  "rune": flat(blood), "eye": flat(eye)}
		frame = render(c, styles, "#120808")[0]
		# drops of blood under the teeth
		if f in (1, 2):
			put(frame, [(6, 11)], "#c01414")
		if f in (3, 4):
			put(frame, [(9, 11)], "#c01414")
		frames.append(frame)
	return Sprite(frames, anim(4))


def parchemin_incantation_maudit():
	"""
	Cursed incantation parchment, same rolled shape as the incantation scroll: purple-black paper, obsidian rolls with
	violet crystal knobs and glowing glyphs, an inverted pentagram of violet runes that pulses (its lines glow then fade)
	over a dark halo, blood running from the star's lower point and dripping under the bottom roll, a black-violet wax seal
	on blood-red ribbons.
	"""
	c = Canvas()
	c.ascii([
		"................",
		"................",
		".BRRRRRRRRRRRRB.",
		".BRRRRRRRRRRRRB.",
		"....PPPPPPPPP...",
		"...PPPPPPPPPP...",
		"...PPPPPPPPPP...",
		"...PPPPPPPPPP...",
		"...PPPPPPPPPP...",
		"...PPPPPPPPPP...",
		"...PPPPPPPPPP...",
		"...PPPPPPPPP....",
		".BRRRRRRRRRRRRB.",
		".BRRRRRRRRRRRRB.",
		"......KSSK......",
		"......K.SK......",
	], {"R": "roll", "B": "knob", "P": "paper", "S": "seal", "K": "ribbon"})
	# Inverted pentagram over the whole sheet: two horns on top, the arms on the bar, the lone point at the bottom.
	star = [
		"..#...#..",
		"..##.##..",
		"...###...",
		"..##.##..",
		".#.#.#.#.",
		"#########",
		"....#....",
		"....#....",
	]
	for y, line in enumerate(star):
		for x, ch in enumerate(line):
			if ch == "#":
				c.put(3 + x, 4 + y, "rune")
	for y in range(SIZE):
		for x in range(SIZE):
			if c.get(x, y) == "paper" and any(c.get(x + dx, y + dy) == "rune" for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
				c.put(x, y, "halo")
	# Blood running from the bar's ends and the lower point.
	for x, y in ((3, 10), (11, 10), (7, 12)):
		c.put(x, y, "blood")
	for x, y in ((12, 6), (4, 5)):
		c.put(x, y, "stain")
	for x, y in ((4, 2), (8, 3), (11, 2)):
		c.put(x, y, "glyph")
	glow = ["#f4d4ff", "#dc94ff", "#b05cf0", "#7a2cc4", "#b05cf0", "#dc94ff"]
	frames = []
	for f in range(len(glow)):
		styles = {"roll": ("#3a2c44", "#1e1626", "#0c0810"), "knob": ("#e6c0ff", "#a868e8", "#5a2a8c"),
				  "paper": ("#4a3058", "#36203f", "#22122a"), "halo": flat("#1a0a22"), "stain": flat("#5a0c1c"),
				  "blood": flat(["#c0102a", "#d81a30", "#a00c20", "#8a0a1a", "#a00c20", "#c0102a"][f]),
				  "seal": ("#b060f0", "#6a20b0", "#2c0a50"), "ribbon": flat("#4a0a14"),
				  "rune": flat(glow[f]), "glyph": flat(glow[(f + 3) % len(glow)])}
		frame = render(c, styles, "#0a0410")[0]
		# Blood dripping under the bottom roll, one drop after the other.
		if f in (1, 2):
			put(frame, [(3, 13 + f)], "#c0102a")
		if f in (4, 5):
			put(frame, [(11, 10 + f)], "#c0102a")
		frames.append(frame)
	return Sprite(frames, anim(4))


WEAPONS = {
	# name: (draw, parent model)
	"foudrelame": (foudrelame, "minecraft:item/handheld"),
	"croc_de_givre": (croc_de_givre, "minecraft:item/handheld"),
	"murmure_du_neant": (murmure_du_neant, "minecraft:item/handheld"),
	"coutelas_du_contrebandier": (coutelas_du_contrebandier, "minecraft:item/handheld"),
	"hache_sylvestre": (hache_sylvestre, "minecraft:item/handheld"),
	"pioche_du_titan": (pioche_du_titan, "minecraft:item/handheld"),
	"pic_du_filon": (pic_du_filon, "minecraft:item/handheld"),
	"marteau_du_seisme": (marteau_du_seisme, "minecraft:item/handheld_mace"),
	"trident_du_leviathan": (trident_du_leviathan, "minecraft:item/handheld"),
	"faux_des_ames": (faux_des_ames, "minecraft:item/handheld"),
	"bottes_de_zephyr": (bottes_de_zephyr, "minecraft:item/generated"),
	"bottes_du_coursier": (bottes_du_coursier, "minecraft:item/generated"),
}


# ---------------------------------------------------------------------------------------------------------- spell scrolls
# (drawn from shapes with automatic shading; the incantation scroll reuses the same renderer)

class Canvas:
	"""A 16x16 grid of part names (None = empty). Parts are colored later (shading, outline, animation)."""

	def __init__(self):
		self.grid = [[None] * SIZE for _ in range(SIZE)]

	def put(self, x, y, part):
		if 0 <= x < SIZE and 0 <= y < SIZE:
			self.grid[y][x] = part

	def get(self, x, y):
		if 0 <= x < SIZE and 0 <= y < SIZE:
			return self.grid[y][x]
		return None

	def ascii(self, lines, mapping):
		"""Hand-drawn layer: one string per row, mapping char -> part ('.' and unknown chars = untouched)."""
		for y, line in enumerate(lines):
			for x, ch in enumerate(line):
				if ch in mapping:
					self.put(x, y, mapping[ch])


def render(canvas, styles, outline, frames=1, shade=True):
	"""
	styles: part -> (light, base, dark) colors; each color is a hex string or a list of hex strings (one per frame,
	cycled). Shading: pixels on the upper-left border of their part get the light color, lower-right border the dark one.
	outline: color of the empty pixels touching the sprite (4-neighbours), None = no outline.
	"""
	out = []
	for frame in range(frames):
		rows = []
		for y in range(SIZE):
			row = []
			for x in range(SIZE):
				part = canvas.get(x, y)
				if part is None:
					touching = any(canvas.get(x + dx, y + dy) is not None for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
					row.append(rgba(outline) if outline and touching else TRANSPARENT)
					continue
				light, base, dark = styles[part]
				color = base
				if shade:
					if canvas.get(x - 1, y) != part or canvas.get(x, y - 1) != part:
						color = light
					elif canvas.get(x + 1, y) != part or canvas.get(x, y + 1) != part:
						color = dark
				if isinstance(color, list):
					color = color[frame % len(color)]
				row.append(rgba(color))
			rows.append(row)
		out.append(rows)
	return out


def flat(color):
	return (color, color, color)


def cycle(colors, offset):
	"""The same palette shifted by offset frames (traveling glints)."""
	return [colors[(i + offset) % len(colors)] for i in range(len(colors))]


# Rune of each spell scroll (8 x 6 pixels drawn in the middle of the parchment), its glowing palette (cycled: animated),
# and the wax seal color of its rarity.
SCROLL_RUNES = {
	"pluie_de_meteores": ([
		"......#.",
		".....#..",
		"...##...",
		".###....",
		"####....",
		".##.....",
	], ["#ffb347", "#ff6a1a", "#c42a0a", "#ff6a1a"], "#ffb000"),
	"tempete_de_foudre": ([
		"....##..",
		"...##...",
		"..####..",
		"....##..",
		"...##...",
		"..##....",
	], ["#7fdbff", "#3fa9ff", "#2a5fd0", "#3fa9ff"], "#c86bff"),
	"blizzard": ([
		"#..#..#.",
		".#.#.#..",
		"..###...",
		"#######.",
		"..###...",
		".#.#.#..",
	], ["#9fe0ff", "#4aa8f0", "#2a62c0", "#4aa8f0"], "#c86bff"),
	"nova_solaire": ([
		"#..##..#",
		"..####..",
		".######.",
		".######.",
		"..####..",
		"#..##..#",
	], ["#ffc21a", "#ff8a00", "#e04a00", "#ff8a00"], "#ffb000"),
	"singularite": ([
		".#####..",
		"#.....#.",
		"#.###.#.",
		"#.#.#.#.",
		"#.#...#.",
		"..#####.",
	], ["#b45aff", "#7a1ad8", "#3a0a6a", "#7a1ad8"], "#ff3b3b"),
	"egide": ([
		".######.",
		".#.##.#.",
		".######.",
		".######.",
		"..####..",
		"...##...",
	], ["#ffc21a", "#e09a00", "#a86a00", "#e09a00"], "#c86bff"),
	"rappel": ([
		"...##...",
		"..####..",
		".######.",
		".##..##.",
		".##..##.",
		".######.",
	], ["#2ac0b0", "#1a9a8a", "#8a4ad8", "#1a9a8a"], "#5aa0ff"),
	"gardiens": ([
		".######.",
		".######.",
		".#.##.#.",
		".######.",
		"..#..#..",
		"..####..",
	], ["#b8c0cc", "#7a8494", "#3e4656", "#7a8494"], "#ffb000"),
	"benediction": ([
		".##..##.",
		"########",
		"########",
		".######.",
		"..####..",
		"...##...",
	], ["#ff7aa8", "#e0408a", "#3fbf3a", "#e0408a"], "#5aa0ff"),
	"revelation": ([
		"..####..",
		".#....#.",
		"#..##..#",
		"#..##..#",
		".#....#.",
		"..####..",
	], ["#00c8e8", "#0090d0", "#2b4cd0", "#0090d0"], "#5aa0ff"),
	"seisme": ([
		"...#....",
		"...##...",
		"....#...",
		"...##...",
		"...#.#..",
		"########",
	], ["#b07a3a", "#8b5a2b", "#5a3418", "#8b5a2b"], "#c86bff"),
	"rayon_arcanique": ([
		"...#....",
		"...#....",
		"#######.",
		"...#....",
		"..#.#...",
		".#...#..",
	], ["#ff3edb", "#c020b0", "#2aa8d8", "#c020b0"], "#ffb000"),
}

PARCHMENT = ("#fbf0d2", "#ecd6a4", "#c9a86c")
ROLL = ("#e3c995", "#c19a5b", "#83602e")


def scroll(rune, palette, seal):
	"""Rolled parchment (two rolls, paper between) with a glowing rune (palette cycling) and a wax seal on a ribbon."""
	c = Canvas()
	c.ascii([
		"................",
		"................",
		"..ERRRRRRRRRRE..",
		"..ERRRRRRRRRRE..",
		"...PPPPPPPPPP...",
		"...PPPPPPPPPP...",
		"...PPPPPPPPPP...",
		"...PPPPPPPPPP...",
		"...PPPPPPPPPP...",
		"...PPPPPPPPPP...",
		"...PPPPPPPPPP...",
		"...PPPPPPPPPP...",
		"..ERRRRRRRRRRE..",
		"..ERRRRRRRRRRE..",
		".......SS.......",
		".......S........",
	], {"R": "roll", "E": "end", "P": "paper", "S": "seal"})
	styles = {"roll": ROLL, "end": ("#6b4a22", "#5a3c1a", "#3e2810"), "paper": PARCHMENT,
			  "seal": (seal, seal, "#5a1010")}
	i = 0
	for y, line in enumerate(rune):
		for x, ch in enumerate(line):
			if ch == "#":
				part = "rune%d" % (i % 4)
				c.put(4 + x, 5 + y, part)
				styles[part] = flat(cycle(palette, i % 4))
				i += 1
	# Ink halo around the rune (contrast on the light parchment).
	for y in range(SIZE):
		for x in range(SIZE):
			if c.get(x, y) == "paper" and any((c.get(x + dx, y + dy) or "").startswith("rune") for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
				c.put(x, y, "halo")
	styles["halo"] = flat("#d2b27a")
	return Sprite(render(c, styles, "#2a1a0c", frames=4), {"animation": {"frametime": 4, "interpolate": False}})


# ---------------------------------------------------------------------------------------------------------- outputs

def all_sprites():
	"""Texture name -> Sprite, in preview order."""
	sprites = {}
	for name, (draw, _) in WEAPONS.items():
		sprites[name] = draw()
	for pull in range(4):
		sprites["arc_du_phenix" + ("" if pull == 0 else "_pulling_%d" % (pull - 1))] = arc_du_phenix(pull)
	for state in CROSSBOW_STATES:
		sprites["arbalete_du_traqueur" + ("" if state == "standby" else "_" + state)] = arbalete_du_traqueur(state)
	for name, (rune, palette, seal) in SCROLL_RUNES.items():
		sprites["parchemin_" + name] = scroll(rune, palette, seal)
	sprites["parchemin_incantation"] = parchemin_incantation()
	sprites["parchemin_incantation_maudit"] = parchemin_incantation_maudit()
	sprites["grimoire"] = grimoire()
	for spell in GRIMOIRE_SIGILS:
		sprites["grimoire_" + spell] = grimoire(spell)
	return sprites


def all_sheets():
	return {name: sprite.frames for name, sprite in all_sprites().items()}


def pack_icon(sprites):
	"""64x64 icon: four uniques at 2x on a dark background."""
	picks = [sprites[n].frames[0] for n in ("foudrelame", "trident_du_leviathan", "faux_des_ames", "croc_de_givre")
			 if n in sprites]
	rows = [[rgba("#1b1530")] * 64 for _ in range(64)]
	for i, sprite in enumerate(picks):
		ox, oy = (i % 2) * 32, (i // 2) * 32
		for y in range(SIZE):
			for x in range(SIZE):
				px = sprite[y][x]
				if px[3]:
					for dy in range(2):
						for dx in range(2):
							rows[oy + y * 2 + dy][ox + x * 2 + dx] = px
	write_png(os.path.join(PACK, "pack.png"), 64, 64, rows)


# ---------------------------------------------------------------------------------------------------------- previews
# tools/previews/textures.png: every texture (frame 0 at x8, in-game sizes in a slot, every frame);
# tools/previews/textures_animated.png: animated PNG (APNG) of every texture played as in game (20 ticks per second).

PREVIEWS = os.path.join(ROOT, "tools", "previews")

# 3 x 5 pixel font for the labels of the previews (rows of 3 pixels, top to bottom).
FONT = {
	"a": ".#./#.#/###/#.#/#.#", "b": "##./#.#/##./#.#/##.", "c": ".##/#../#../#../.##", "d": "##./#.#/#.#/#.#/##.",
	"e": "###/#../##./#../###", "f": "###/#../##./#../#..", "g": ".##/#../#.#/#.#/.##", "h": "#.#/#.#/###/#.#/#.#",
	"i": "###/.#./.#./.#./###", "j": "..#/..#/..#/#.#/.#.", "k": "#.#/#.#/##./#.#/#.#", "l": "#../#../#../#../###",
	"m": "#.#/###/###/#.#/#.#", "n": "##./#.#/#.#/#.#/#.#", "o": ".#./#.#/#.#/#.#/.#.", "p": "##./#.#/##./#../#..",
	"q": ".#./#.#/#.#/##./.##", "r": "##./#.#/##./#.#/#.#", "s": ".##/#../.#./..#/##.", "t": "###/.#./.#./.#./.#.",
	"u": "#.#/#.#/#.#/#.#/###", "v": "#.#/#.#/#.#/#.#/.#.", "w": "#.#/#.#/###/###/#.#", "x": "#.#/#.#/.#./#.#/#.#",
	"y": "#.#/#.#/.#./.#./.#.", "z": "###/..#/.#./#../###", "0": "###/#.#/#.#/#.#/###", "1": ".#./##./.#./.#./###",
	"2": "##./..#/.#./#../###", "3": "##./..#/.#./..#/##.", "4": "#.#/#.#/###/..#/..#", "5": "###/#../##./..#/##.",
	"6": ".##/#../###/#.#/###", "7": "###/..#/.#./.#./.#.", "8": "###/#.#/###/#.#/###", "9": "###/#.#/###/..#/##.",
	"_": ".../.../.../.../###", " ": ".../.../.../.../...", "(": ".#./#../#../#../.#.", ")": ".#./..#/..#/..#/.#.",
	".": ".../.../.../.../.#.", ":": ".../.#./.../.#./...", "-": ".../.../###/.../...", "/": "..#/..#/.#./#../#..",
}


class Image:
	"""A small RGBA canvas for the previews."""

	def __init__(self, width, height, color):
		self.width, self.height = width, height
		self.rows = [[color] * width for _ in range(height)]

	def rect(self, x, y, w, h, color):
		for yy in range(max(0, y), min(self.height, y + h)):
			row = self.rows[yy]
			for xx in range(max(0, x), min(self.width, x + w)):
				row[xx] = color

	def checker(self, x, y, size, cell):
		for yy in range(0, size, cell):
			for xx in range(0, size, cell):
				color = (139, 139, 139, 255) if (xx // cell + yy // cell) % 2 else (124, 124, 124, 255)
				self.rect(x + xx, y + yy, cell, cell, color)

	def sprite(self, x, y, frame, scale):
		for sy in range(SIZE):
			for sx in range(SIZE):
				px = frame[sy][sx]
				if px[3]:
					self.rect(x + sx * scale, y + sy * scale, scale, scale, px[:3] + (255,))

	def slot(self, x, y, frame, scale):
		"""The sprite in an inventory slot (vanilla slot colors) at an in-game size; returns the slot size."""
		size = SIZE * scale + 2 * scale
		self.rect(x, y, size, size, (55, 55, 55, 255))
		self.rect(x + scale, y + scale, size - scale, size - scale, (255, 255, 255, 255))
		self.rect(x + scale, y + scale, size - 2 * scale, size - 2 * scale, (139, 139, 139, 255))
		self.sprite(x + scale, y + scale, frame, scale)
		return size

	def text(self, x, y, value, scale, color):
		for ch in value.lower():
			glyph = FONT.get(ch, FONT[" "]).replace("/", "")
			for i, bit in enumerate(glyph):
				if bit == "#":
					self.rect(x + (i % 3) * scale, y + (i // 3) * scale, scale, scale, color)
			x += 4 * scale
		return x

	def save(self, path):
		write_png(path, self.width, self.height, self.rows)


def preview_groups(sprites):
	weapons = [n for n in sprites if not n.startswith(("parchemin_", "grimoire"))]
	return [("armes uniques", weapons),
			("grimoire (un modele par sort - sinon endormi)", [n for n in sprites if n.startswith("grimoire")]),
			("parchemins", [n for n in sprites if n.startswith("parchemin_")])]


def preview_sheet(path, sprites):
	"""Static sheet: per texture its name, frame 0 at x8, the in-game sizes in a slot, every frame at x3."""
	cols, cell_w, strip = 3, 440, 8
	blocks = []
	for title, names in preview_groups(sprites):
		heights = []
		for name in names:
			n = len(sprites[name].frames)
			lines = (n + strip - 1) // strip if n > 1 else 0
			heights.append(18 + 128 + 10 + lines * 51 + 10)
		blocks.append((title, names, [max(heights[i:i + cols]) for i in range(0, len(names), cols)]))
	height = 12 + sum(30 + sum(rows) for _, _, rows in blocks)
	img = Image(cols * cell_w + 16, height, (34, 34, 42, 255))
	y = 12
	for title, names, rows in blocks:
		img.text(12, y, title, 3, (255, 214, 120, 255))
		y += 30
		for r, row_h in enumerate(rows):
			for c, name in enumerate(names[r * cols:(r + 1) * cols]):
				x = 12 + c * cell_w
				sprite = sprites[name]
				label = name + ("" if len(sprite.frames) == 1 else " (%d)" % len(sprite.frames))
				img.text(x, y, label, 2, (230, 230, 230, 255))
				img.checker(x, y + 18, 128, 8)
				img.sprite(x, y + 18, sprite.frames[0], 8)
				size = img.slot(x + 140, y + 18, sprite.frames[0], 1)
				img.slot(x + 140 + size + 6, y + 18, sprite.frames[0], 2)
				img.slot(x + 140, y + 18 + 42, sprite.frames[0], 3)
				if len(sprite.frames) > 1:
					for i, frame in enumerate(sprite.frames):
						fx = x + (i % strip) * 51
						fy = y + 18 + 128 + 10 + (i // strip) * 51
						img.checker(fx, fy, 48, 3)
						img.sprite(fx, fy, frame, 3)
			y += row_h
	img.save(path)


def timeline(sprite):
	"""[(frame index, ticks)] of an animated texture as Minecraft plays it (.mcmeta frames and frametime)."""
	section = sprite.meta["animation"]
	default = section.get("frametime", 1)
	order = section.get("frames", list(range(len(sprite.frames))))
	return [(e, default) if isinstance(e, int) else (e["index"], e.get("time", default)) for e in order]


def frame_at(sprite, tick):
	"""The frame shown at a game tick (interpolated textures blend toward the next frame, as in game)."""
	if not sprite.meta:
		return sprite.frames[0]
	steps = timeline(sprite)
	t = tick % sum(time for _, time in steps)
	for i, (index, time) in enumerate(steps):
		if t < time:
			if not sprite.meta["animation"].get("interpolate"):
				return sprite.frames[index]
			current, following = sprite.frames[index], sprite.frames[steps[(i + 1) % len(steps)][0]]
			k = t / time
			return [[tuple(int(round(a + (b - a) * k)) for a, b in zip(p, q)) for p, q in zip(r1, r2)]
					for r1, r2 in zip(current, following)]
		t -= time
	return sprite.frames[0]


def write_apng(path, width, height, frames):
	"""frames: [(rows, delay in 1/20 s)]; each frame after the first only stores its changed pixels (blended over)."""
	def chunk(kind, data):
		body = kind + data
		return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

	def pack(rows):
		return zlib.compress(b"".join(b"\x00" + bytes(c for px in row for c in px) for row in rows), 9)

	out = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
	out += chunk(b"acTL", struct.pack(">II", len(frames), 0))
	seq = 0
	previous = None
	for rows, delay in frames:
		blend = 0 if previous is None else 1
		out += chunk(b"fcTL", struct.pack(">IIIIIHHBB", seq, width, height, 0, 0, delay, 20, 0, blend))
		seq += 1
		if previous is None:
			out += chunk(b"IDAT", pack(rows))
		else:
			diff = [[px if px != old else TRANSPARENT for px, old in zip(row, old_row)] for row, old_row in zip(rows, previous)]
			out += chunk(b"fdAT", struct.pack(">I", seq) + pack(diff))
			seq += 1
		previous = rows
	out += chunk(b"IEND", b"")
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, "wb") as f:
		f.write(out)


def animated_preview(path, sprites, ticks=200):
	"""Every texture at x4 in a grid (same order as the sheet), played tick by tick for ticks / 20 seconds."""
	names = [n for _, group in preview_groups(sprites) for n in group]
	cols, cell = 10, 72
	width, height = cols * cell + 8, ((len(names) + cols - 1) // cols) * cell + 8
	background = Image(width, height, (34, 34, 42, 255))
	for i in range(len(names)):
		background.checker(8 + (i % cols) * cell, 8 + (i // cols) * cell, 64, 4)
	frames = []
	for tick in range(ticks):
		img = Image(0, 0, TRANSPARENT)
		img.width, img.height = width, height
		img.rows = [list(row) for row in background.rows]
		for i, name in enumerate(names):
			img.sprite(8 + (i % cols) * cell, 8 + (i // cols) * cell, frame_at(sprites[name], tick), 4)
		if frames and frames[-1][0] == img.rows:
			frames[-1] = (frames[-1][0], frames[-1][1] + 1)
		else:
			frames.append((img.rows, 1))
	write_apng(path, width, height, frames)


def main():
	sprites = all_sprites()
	for name, sprite in sprites.items():
		write_sprite(name, sprite)
	for name, (_, parent) in WEAPONS.items():
		simple_model(name, parent)
	bow_model("arc_du_phenix")
	crossbow_model("arbalete_du_traqueur")
	for name in SCROLL_RUNES:
		simple_model("parchemin_" + name, "minecraft:item/generated")
	simple_model("parchemin_incantation", "minecraft:item/generated")
	simple_model("parchemin_incantation_maudit", "minecraft:item/generated")
	grimoire_model()
	remove_stale()
	pack_icon(sprites)
	preview_sheet(os.path.join(PREVIEWS, "textures.png"), sprites)
	animated_preview(os.path.join(PREVIEWS, "textures_animated.png"), sprites)
	print("textures, models and item definitions written to", ASSETS)


if __name__ == "__main__":
	main()
