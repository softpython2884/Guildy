#!/usr/bin/env python3
"""
Guildy - generator of the optional resource pack: unique weapons, spell scrolls, grimoire and incantation scroll.

Every texture is ORIGINAL pixel art (16x16 icons, 32x32 in-hand textures, 64x32 worn armor layers). The unique
weapons, the grimoire and the incantation scroll are hand-drawn here as character grids (one character = one pixel,
one palette per sprite: 1-pixel hue-shifted outlines, light from the top-left, 3-5 tone ramps per material) with
frame-by-frame animation layers; the spell scrolls are drawn from simple shapes (rolled parchment + rune). The script
writes, under src/main/resources/assets/guildy/:
  textures/item/<name>.png (+ .png.mcmeta when animated), models/item/<name>.json, items/<name>.json,
  equipment/<name>.json + textures/entity/equipment/humanoid[_leggings]/<name>.png (worn look of the unique armors)
and tools/resourcepack/pack.png (pack icon); stale files of those generated folders are removed. No Minecraft texture
is used or copied: models only reference vanilla *parent models* (item/handheld, item/bow, item/crossbow...) for their
in-hand transforms (the 3D shield copies the display numbers of the vanilla shield models) and the equipment assets
only point baby mobs to the vanilla armor textures. Previews: tools/previews/textures.png (every texture, every frame,
in-game sizes), tools/previews/textures_animated.png (animated PNG playing the animations as in game),
tools/previews/textures_in_hand.png (in-hand spear) and tools/previews/armor_worn.png (worn armors and 3D shield,
rendered in 3D).

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
				  os.path.join(ASSETS, "items"), os.path.join(ASSETS, "equipment"),
				  os.path.join(ASSETS, "textures", "entity", "equipment", "humanoid"),
				  os.path.join(ASSETS, "textures", "entity", "equipment", "humanoid_leggings")]


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
		if not os.path.isdir(folder):
			continue
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
	"banniere_de_guerre": ["##.....", ".######", ".#####.", ".######", ".#.....", ".#.....", ".#....."],
	"fleau": [".#####.", "#######", "#..#..#", "#..#..#", "###.###", ".#####.", ".#.#.#."],
	"permutation": ["....#..", "######.", "....#..", ".......", "..#....", ".######", "..#...."],
	"ralliement": ["...#...", "..###..", ".#####.", ".#####.", ".#####.", "#######", "...#..."],
	"chaines_spectrales": [".##....", "#..#...", "#..#...", ".##.##.", "...#..#", "...#..#", "....##."],
	"hate_du_vent": ["....##.", "#####.#", ".......", ".####..", "######.", ".....#.", "......."],
}

# Sigil colors that differ from the scroll palette (the earthy browns of the earthquake vanish on the dark panel; the
# spectral chains and the wind are drawn darker on the light parchment and glow pale on the cover).
GRIMOIRE_GLOW = {"seisme": ["#f4c070", "#d8913e", "#a8662a", "#d8913e"],
				 "chaines_spectrales": ["#e0fff8", "#7fe8d0", "#2e8c7a", "#7fe8d0"],
				 "hate_du_vent": ["#dce4ff", "#a0b0ff", "#5c6cd8", "#a0b0ff"]}

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


# ---------------------------------------------------------------------------------------------------------- cursed grimoire
# Same select as the grimoire (custom_model_data string 0 = selected scroll id), fallback = the dormant cursed tome.

GRIMOIRE_MAUDIT = [
	"OOOOOOOOOOOOO...",
	"OBbccdYdccbBOO..",
	"ObXkkkkkkkXbOPO.",
	"OckKKKKKKKcCOpkO",
	"OckKKKKKKKcCOPkO",
	"OckKKKKKKKcCOrkO",
	"ObkKKKKKKKcbOLlO",
	"OckKKKKKKKcllHLO",
	"OckKKKKKKKcdOldO",
	"OckKKKKKKKcCOpkO",
	"OcXcccccccXCOPkO",
	"OLlLlLlLlLlLOlLO",
	"OBbkkkdkkkddOPkO",
	"OOOOOOOOOOOOOpkO",
	".OPpPrPpPRPpPpkO",
	"..OkkkkkkRkkkkkO",
]

GRIMOIRE_MAUDIT_PALETTE = {"O": "#050208", "c": "#553070", "C": "#3a1d52", "k": "#24112f", "K": "#0c0514",
						   "B": "#f2ead6", "b": "#c4b590", "d": "#7e7056", "X": "#4b2f78", "Y": "#5a0a14",
						   "L": "#a8a2b6", "l": "#5d5768", "H": "#6a0a16", "P": "#cbbd98", "p": "#94845f",
						   "R": "#9e0a1e", "r": "#6e0814"}

# Inverted pentagram of the cover panel (7 x 7, like the star of the cursed incantation scroll).
CURSED_SIGIL = [".#...#.", ".##.##.", "..###..", ".#.#.#.", "#######", "...#...", "...#..."]
# Heartbeat of the blood eye (top stud) and of the padlock's eye, one color per frame (a double beat, then rest).
CURSED_BEAT = ["#ff2a3a", "#c8142a", "#ff4a50", "#c8142a", "#8e0c18", "#5a0a14", "#5a0a14", "#8e0c18"]


def grimoire_maudit(spell=None):
	"""
	Cursed tome: black-violet leather, bone corners, obsidian studs, an eye-shaped bone stud at the top, an iron chain wrapped
	around the book to a padlock whose keyhole is a blood eye, aged pages stained with blood and a blood ribbon. Dormant
	(fallback): the inverted pentagram of the panel in dried blood, eyes closed. With a spell: the pentagram glows in the
	colors of its scroll (palette cycling) over a halo of its color, the eyes beat like a heart, a glint runs along the chain
	and a drop of blood falls from the ribbon.
	"""
	sigil = [(3 + x, 3 + y) for y, line in enumerate(CURSED_SIGIL) for x, ch in enumerate(line) if ch == "#"]
	if spell is None:
		g = paint(GRIMOIRE_MAUDIT, GRIMOIRE_MAUDIT_PALETTE)
		put(g, sigil, "#6a0c18")
		put(g, [(6, 7)], "#8e1020")
		return Sprite([g])
	palette = GRIMOIRE_GLOW.get(spell, SCROLL_RUNES[spell][1])
	mark = set(sigil)
	halo = [(x, y) for x in range(3, 10) for y in range(3, 10) if (x, y) not in mark
			and any((x + dx, y + dy) in mark for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
	chain = cells(GRIMOIRE_MAUDIT, "Ll")
	chain = [p for p in chain if p[1] == 11]
	frames = []
	for f in range(8):
		g = paint(GRIMOIRE_MAUDIT, GRIMOIRE_MAUDIT_PALETTE)
		glow = palette[(f // 2 + 2) % 4]
		for x, y in halo:
			g[y][x] = mix(mix(g[y][x], glow, 0.3), "#5a0010", 0.15)
		for i, p in enumerate(sorted(sigil, key=lambda q: (q[0] + q[1], q[0]))):
			put(g, [p], palette[(i + f // 2) % 4])
		beat = CURSED_BEAT[f]
		put(g, [(6, 1)], beat)
		g[7][13] = mix(rgba(beat), palette[(f // 2) % 4], 0.35)
		gleam(g, chain, 2 * f, ["#f4f0ff", "#c8c2d6"])
		if f in (5, 6):
			put(g, [(9, 15)], "#d81a30")
		frames.append(g)
	return Sprite(frames, anim(3))


def grimoire_maudit_sprites():
	"""Texture name -> Sprite of the cursed grimoire: the dormant tome and one texture per spell scroll."""
	sprites = {"grimoire_maudit": grimoire_maudit()}
	for spell in GRIMOIRE_SIGILS:
		sprites["grimoire_maudit_" + spell] = grimoire_maudit(spell)
	return sprites


def grimoire_maudit_model():
	"""guildy:grimoire_maudit: one model per spell, selected by the string 0 of minecraft:custom_model_data."""
	model_file("grimoire_maudit", "minecraft:item/generated")
	cases = []
	for spell in GRIMOIRE_SIGILS:
		model_file("grimoire_maudit_" + spell, "minecraft:item/generated")
		cases.append({"when": spell, "model": ref("grimoire_maudit_" + spell)})
	item_file("grimoire_maudit", {
		"type": "minecraft:select",
		"property": "minecraft:custom_model_data",
		"index": 0,
		"cases": cases,
		"fallback": ref("grimoire_maudit"),
	})


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


# ---------------------------------------------------------------------------------------------------------- uniques of the ruins
# The seven uniques dropped by the ruin guardians (uniques.json5 format 4). Same conventions as the weapons above; the
# spear also has its 32 x 32 in-hand texture (vanilla spear layout: tip toward the top-left corner), the shield a 2D
# inventory icon (in hand, the vanilla shield model draws the banner patterns of the item).

def paint_sized(rows, palette, size, frame=0):
	"""paint() for a size x size layer (the 32 x 32 in-hand spear)."""
	if len(rows) != size or any(len(row) != size for row in rows):
		raise ValueError("a %d px layer must be %d rows of %d characters: %r" % (size, size, size, rows))
	grid = [[TRANSPARENT] * size for _ in range(size)]
	for y, row in enumerate(rows):
		for x, ch in enumerate(row):
			if ch in "._":
				continue
			color = palette[ch]
			if isinstance(color, list):
				color = color[frame % len(color)]
			grid[y][x] = rgba(color)
	return grid


def put_sized(grid, points, color):
	"""put() for a frame of any size."""
	for x, y in points:
		if 0 <= y < len(grid) and 0 <= x < len(grid[0]):
			grid[y][x] = rgba(color)
	return grid


JAGUAR_SPEAR = [
	".............OOO",
	"............OWWO",
	"...........OWGhO",
	"..........OWGshO",
	".........OWsGghO",
	"........OWGGshO.",
	"........OWGghO..",
	".......OJWghO...",
	"......OJjOOO....",
	".....OwkORO.....",
	"....OyYOOrO.....",
	"...OYyO..O......",
	"..OyYO..........",
	".ObnO...........",
	"OwdO............",
	"nnO.............",
]

JAGUAR_PALETTE = {"O": "#2a1406", "W": "#fff6c4", "G": "#ffd447", "g": "#e3a020", "h": "#a85e14", "s": "#6a3608",
				  "y": "#f7c84a", "J": "#8af5c8", "j": "#22a070", "k": "#0f5a40", "w": "#a0603a", "d": "#5e3218",
				  "b": "#ffe27a", "n": "#b87a1e", "Y": "#3a1c08", "R": "#ff5a3a", "r": "#a82418"}


def lance_du_jaguar_celeste():
	"""
	Spear of the celestial jaguar: a golden fang-shaped head spotted with black rosettes, two little jaguar-ear guards, a
	jade collar, a mahogany shaft with a spotted pelt wrap and a golden band, a red quetzal tassel. A sun glint runs up
	the blade, then the jade collar flashes.
	"""
	base = paint(JAGUAR_SPEAR, JAGUAR_PALETTE)
	blade = cells(JAGUAR_SPEAR, "WGgh")
	frames = [copy(base)] + sweep(base, blade, -3, 15, (0.65, 0.3), "#fffbe6")
	flash = put(copy(base), [(8, 7), (7, 8)], "#e4fff4")
	put(flash, [(8, 8), (7, 9)], "#5ad8a8")
	frames.append(flash)
	order = [{"index": 0, "time": 50}] + list(range(1, len(frames) - 1)) + [{"index": len(frames) - 1, "time": 4}]
	return Sprite(frames, anim(1, order))


def lance_du_jaguar_celeste_in_hand():
	"""
	The spear in hand, 32 x 32 like the vanilla spears (tip toward the top-left corner): the head of the inventory icon
	mirrored, the tassel longer, the shaft carried to the corner (pelt wrap, golden bands, a golden butt). Same animation.
	"""
	gui = lance_du_jaguar_celeste()
	size = 32
	shaft = {13: "Yy", 14: "yY", 15: "Yy", 16: "bn", 25: "bn", 28: "bn", 29: "yn", 30: "yn", 31: "Wn"}
	frames = []
	for frame in gui.frames:
		g = [[TRANSPARENT] * size for _ in range(size)]
		for y in range(13):
			for x in range(SIZE):
				g[y][SIZE - 1 - x] = frame[y][x]
		for y in range(13, size):
			light, dark = shaft.get(y, "wd")
			put_sized(g, [(y + 1, y), (y - 2, y)], JAGUAR_PALETTE["O"])
			put_sized(g, [(y, y)], JAGUAR_PALETTE[light])
			put_sized(g, [(y - 1, y)], JAGUAR_PALETTE[dark])
		# A grain mark on the wood here and there, the tassel hanging longer from the jade collar.
		put_sized(g, [(20, 20), (23, 23)], "#7a4426")
		put_sized(g, [(6, 11)], JAGUAR_PALETTE["R"])
		put_sized(g, [(6, 12)], JAGUAR_PALETTE["r"])
		put_sized(g, [(5, 11), (7, 11), (5, 12), (7, 12), (6, 13)], JAGUAR_PALETTE["O"])
		frames.append(g)
	return Sprite(frames, gui.meta)


def couronne_du_roi_de_givre():
	"""
	Helmet-crown of the frost king: five ice spikes above a band of frosted silver set with frost gems, cheek guards with
	icicles and a gem hanging on the nose guard over the dark inside of the helmet. A frost glint crosses the band, the
	gems pulse, a star twinkles on the highest spike.
	"""
	rows = [
		"................",
		".......OO.......",
		"...O...OIO...O..",
		"..OIO.OIiCO.OIO.",
		"..OiCOOicCOOicO.",
		".OOicCOicCCOicOO",
		".OSSSSSSSSSSSSsO",
		".OSSEeSSSSSeEstO",
		".OstsfssEssfsttO",
		".OsOOOOseeOOOOtO",
		".OsOKKKOfOKKKOtO",
		".OcOKKKKOKKKKOcO",
		"..OcO.......OcO.",
		"...O.........O..",
		"................",
		"................",
	]
	pal = {"O": "#0b2238", "I": "#ffffff", "i": "#c4ecff", "c": "#7cc8f0", "C": "#3f8fd0", "S": "#dfeaf4", "s": "#98aec4",
		   "t": "#5a7088", "K": "#13314c", "E": "#c6fbff", "e": "#38c4ee", "f": "#1660a8"}
	frames = []
	band = cells(rows, "Ss")
	for f in range(8):
		light, mid, dark = [("#d6fdff", "#5ad6f6", "#1a6cb8"), ("#ecffff", "#7ae4ff", "#2280c8"),
							("#ffffff", "#a8f0ff", "#3496d8"), ("#ecffff", "#7ae4ff", "#2280c8")][f % 4]
		g = paint(rows, dict(pal, E=light, e=mid, f=dark))
		shine(g, band, -8 + f * 3, (0.55, 0.25))
		if f in (2, 3):
			put(g, [(8, 0), (7, 1), (9, 1)], "#ffffff" if f == 2 else "#c4ecff")
		frames.append(g)
	return Sprite(frames, anim(3))


def egide_des_ames():
	"""
	Shield of the souls: a heater shield of blackened soul-steel with a cyan rim, a bone skull with glowing eyes amid soul
	flames in the middle. The soul fire flickers, the eyes flare and a soul glint runs down the rim.
	"""
	rows = [
		"................",
		".OOOOOOOOOOOOOO.",
		".OaAAAAAAAAAAdO.",
		".OAkkfkkkkfkkdO.",
		".OAkfBBBBBBfkdO.",
		".OAkBBBBBBBbkdO.",
		".OAkBnnBBnnbkdO.",
		".OAkBnEBBEnbkdO.",
		".OAkBBBnnBBbkdO.",
		".OAkkbBnBnbkkdO.",
		"..OAkfkbbkfkdO..",
		"..OAkFkkkkFkdO..",
		"...OAkfkkfkdO...",
		"....OAkkkkdO....",
		".....OOddOO.....",
		".......OO.......",
	]
	pal = {"O": "#04121a", "A": "#7ff6ff", "a": "#e4ffff", "d": "#1d7486", "k": "#0e2028", "B": "#eaf6f2", "b": "#94bcc0",
		   "n": "#03090c", "F": "#5ff0ff", "f": "#1aa8c0", "E": "#ffffff"}
	rim = [(2, y) for y in range(3, 10)] + [(3, 10), (3, 11), (4, 12), (5, 13), (6, 14)]
	frames = []
	for f in range(8):
		flame, ember = [("#5ff0ff", "#1aa8c0"), ("#8ffbff", "#2ac4dc"), ("#3fd8f0", "#128ca4"), ("#8ffbff", "#2ac4dc")][f % 4]
		eye = ["#ffffff", "#bffcff", "#5ff0ff", "#bffcff"][f % 4]
		g = paint(rows, dict(pal, F=flame, f=ember, E=eye))
		gleam(g, rim, f * 2 - 1, ["#ffffff", "#c8fdff"])
		frames.append(g)
	return Sprite(frames, anim(3))


def masse_du_zenith():
	"""
	Mace of the zenith: a golden sun disc with eight rays and a white-hot core, on a dark bronze-wood handle bound with gold
	and a sunstone pommel. The core breathes while the tips of the rays flare one after the other.
	"""
	rows = [
		"..........OyO...",
		"......OyO.OGO.Oy",
		".......OGOOGOOGO",
		"........OOGWGOO.",
		".....OyOOGWCWGOO",
		"......OGGWCcCWGG",
		".......OOGWCWGOO",
		".......OaOOGWGO.",
		"......OabOOGOOGO",
		".....OabO.OGO.Oy",
		"....OnNO..OyO...",
		"...OabO.........",
		"..OabO..........",
		".OnNO...........",
		"OabO............",
		"OsO.............",
	]
	pal = {"O": "#3a1606", "y": "#ffe27a", "G": "#f0a524", "W": "#fff3b0", "a": "#8a5a30", "b": "#4a2a14",
		   "n": "#ffd96a", "N": "#b0761e", "s": "#ff8a2a"}
	import math
	tips = sorted(cells(rows, "y"), key=lambda p: math.atan2(p[1] - 5, p[0] - 11))
	pulse = [("#fff6c8", "#ffcf3a"), ("#ffffff", "#ffe070"), ("#fffbe0", "#fff0a0"), ("#ffffff", "#ffe070"),
			 ("#fff6c8", "#ffcf3a"), ("#ffe8a0", "#ff9a20")]
	frames = []
	for f, (hot, core) in enumerate(pulse):
		g = paint(rows, dict(pal, C=core, c=hot))
		for i, p in enumerate(tips):
			if i % len(pulse) == f:
				put(g, [p], "#ffffff")
		frames.append(g)
	return Sprite(frames, anim(4, interpolate=True))


def lame_du_pacte():
	"""
	Blade of the pact: a blade of black steel engraved with blood runes, a guard of curved bone horns around a violet gem,
	a grip wrapped in blood-red leather and a pommel with a violet eye. The runes light up from the guard to the tip, the
	eye blinks.
	"""
	rows = [
		".............OOO",
		"............OKkO",
		"...........OKRkO",
		"..........OKkkdO",
		".........OKRkdO.",
		"........OKkkdO..",
		"...BO..OKRkdO...",
		"..BbO.OKkkdO....",
		"..ObBOKRkdO.....",
		"...ObHHkdO......",
		"....OHeHO.BO....",
		"...OrHOObbO.....",
		"..OrgO..OO......",
		".OrgO...........",
		"OEeO............",
		"eeO.............",
	]
	pal = {"O": "#0a050e", "K": "#b0a8c0", "k": "#544a62", "d": "#262030", "R": "#6a1424", "B": "#f2e6d0", "b": "#b4a48a",
		   "H": "#2e1c2a", "e": "#b02ad8", "r": "#5a0e1a", "g": "#a02234", "E": "#e070ff"}
	runes = sorted(cells(rows, "R"), key=lambda p: -p[1])
	glow = ["#ff3a5a", "#d0203e", "#a01a30"]
	frames = []
	for f in range(10):
		g = paint(rows, pal)
		for i, p in enumerate(runes):
			d = f - i * 2
			if 0 <= d < len(glow):
				put(g, [p], glow[d])
				if d == 0:
					x, y = p
					g[y - 1][x] = mix(g[y - 1][x], "#ff3a5a", 0.35)
					g[y][x + 1] = mix(g[y][x + 1], "#ff3a5a", 0.35)
		if f in (8, 9):
			put(g, [(1, 14)], "#3a0a52")
		frames.append(g)
	return Sprite(frames, anim(2))


def hache_du_seigneur_de_guerre():
	"""
	Axe of the warlord: a huge bearded head of blackened netherite veined with magma, a sharpened steel edge, gilded
	blackstone trim and a back spike, on a crimson-stem haft bound with gold. The magma veins throb, an ember flies off.
	"""
	rows = [
		"........OOO.....",
		".......OEKKOO...",
		"......OEKmKkeO..",
		"......OKKmkkkeO.",
		".....OgKkkmkkeO.",
		"...OOgGgKkkmkeO.",
		"...OGgOOgGkkmEO.",
		"....OO.OcOggkEO.",
		"......OcbOOOkKO.",
		".....OnNO...OO..",
		"....OcbO........",
		"...OcbO.........",
		"..OcbO..........",
		".OnNO...........",
		"OcbO............",
		"OOO.............",
	]
	pal = {"O": "#120808", "E": "#a89ca4", "K": "#5e5056", "k": "#2c2226", "e": "#d8d0d6", "g": "#c8902a", "G": "#ffd45a",
		   "c": "#8a2a3a", "b": "#4e1420", "n": "#ffcf5a", "N": "#a8701a"}
	pulse = ["#c83a0c", "#ff6a1a", "#ffa040", "#ffd070", "#ffa040", "#ff6a1a"]
	frames = []
	for i, color in enumerate(pulse):
		g = paint(rows, dict(pal, m=color))
		if i in (3, 4):
			put(g, [(15, 1 + i)], "#ffb040" if i == 3 else "#ff7a20")
		frames.append(g)
	return Sprite(frames, anim(4))


def plastron_du_serpent():
	"""
	Chestplate of the feathered serpent: rows of jade scales lit from the top-left, a golden collar opening on a serpent
	medallion with ruby eyes, quetzal feathers (green, red tips) on the shoulders, a golden belt. A glint runs over the
	scales, the serpent's eyes shine.
	"""
	rows = outlined([
		"................",
		"................",
		".RF..........FR.",
		".FfJJ......JJfF.",
		".fJjGggggggGjJf.",
		"..JjjGJjjJGjjJ..",
		"...jJjGMMGjJj...",
		"...JjJMeeMJjJ...",
		"...jJjJMMJjJj...",
		"...JjJjJjJjJj...",
		"...jJjJjJjJjJ...",
		"....JjJjJjJj....",
		"....gggggggg....",
		"................",
		"................",
		"................",
	])
	pal = {"O": "#0a2a1c", "J": "#4fd090", "j": "#1f8a5a", "G": "#ffd45a", "g": "#c8902a", "F": "#3ccf78", "f": "#1c7a44",
		   "R": "#ff5a3a", "M": "#e8b032", "e": "#ff3a30"}
	base = paint(rows, pal)
	scales = cells(rows, "Jj")
	for x, y in scales:
		if x >= 10:
			base[y][x] = mix(base[y][x], "#06301e", 0.3)
		elif x <= 4:
			base[y][x] = mix(base[y][x], "#e8fff0", 0.15)
	frames = [copy(base)] + sweep(base, scales, -12, 7, (0.45, 0.2), "#e8fff0")
	eyes = put(copy(base), [(7, 7), (8, 7)], "#ffd0a0")
	frames.append(eyes)
	order = [{"index": 0, "time": 40}] + list(range(1, len(frames) - 1)) + [{"index": len(frames) - 1, "time": 6}]
	return Sprite(frames, anim(2, order))


RUIN_UNIQUES = {
	# name: (draw, parent model)
	"lance_du_jaguar_celeste": (lance_du_jaguar_celeste, "minecraft:item/generated"),
	"couronne_du_roi_de_givre": (couronne_du_roi_de_givre, "minecraft:item/generated"),
	"egide_des_ames": (egide_des_ames, "minecraft:item/generated"),
	"masse_du_zenith": (masse_du_zenith, "minecraft:item/handheld_mace"),
	"lame_du_pacte": (lame_du_pacte, "minecraft:item/handheld"),
	"hache_du_seigneur_de_guerre": (hache_du_seigneur_de_guerre, "minecraft:item/handheld"),
	"plastron_du_serpent": (plastron_du_serpent, "minecraft:item/generated"),
}

SPEAR_UNIQUES = ("lance_du_jaguar_celeste",)
SHIELD_UNIQUES = ("egide_des_ames",)


def ruin_unique_sprites():
	"""Texture name -> Sprite of the 16 x 16 textures of the uniques of the ruins (inventory icons)."""
	return {name: draw() for name, (draw, _) in RUIN_UNIQUES.items()}


def write_sprite_sized(name, sprite):
	"""write_sprite() for a texture of any size (the 32 x 32 in-hand spear)."""
	size = len(sprite.frames[0][0])
	path = os.path.join(ASSETS, "textures", "item", name + ".png")
	write_png(path, size, size * len(sprite.frames), [row for frame in sprite.frames for row in frame])
	WRITTEN.add(os.path.normcase(path))
	if sprite.meta:
		write_json(path + ".mcmeta", sprite.meta)


def spear_model(name):
	"""Same item definition as the vanilla spears: the flat icon in inventories, frames and on the ground (and shelves), the
	long in-hand model (vanilla parent item/spear_in_hand, 32 x 32 texture) everywhere else."""
	model_file(name, "minecraft:item/generated")
	model_file(name + "_in_hand", "minecraft:item/spear_in_hand")
	write_json(os.path.join(ASSETS, "items", name + ".json"), {
		"model": {
			"type": "minecraft:select",
			"property": "minecraft:display_context",
			"cases": [{"when": ["gui", "ground", "fixed", "on_shelf"], "model": ref(name)}],
			"fallback": ref(name + "_in_hand"),
		},
		"swap_animation_scale": 1.95,
	})


def shield_model(name):
	"""The flat icon in inventories; everywhere else the 3D shield model (same states as minecraft:items/shield.json:
	carried / blocking), see shield_3d_models()."""
	model_file(name, "minecraft:item/generated")
	shield_3d_models(name)
	item_file(name, {
		"type": "minecraft:select",
		"property": "minecraft:display_context",
		"cases": [{"when": "gui", "model": ref(name)}],
		"fallback": {
			"type": "minecraft:condition",
			"property": "minecraft:using_item",
			"on_false": ref(name + "_in_hand"),
			"on_true": ref(name + "_in_hand_blocking"),
		},
	})


def ruin_unique_models():
	"""Models and item definitions of the uniques of the ruins, and the in-hand textures of the spear and the shield."""
	for name, (_, parent) in RUIN_UNIQUES.items():
		if name in SPEAR_UNIQUES:
			spear_model(name)
		elif name in SHIELD_UNIQUES:
			shield_model(name)
		else:
			simple_model(name, parent)
	for name in SPEAR_UNIQUES:
		write_sprite_sized(name + "_in_hand", lance_du_jaguar_celeste_in_hand())
	for name in SHIELD_UNIQUES:
		write_sprite_sized(name + "_in_hand", egide_des_ames_in_hand())


def ruin_unique_preview(path):
	"""tools/previews/textures_in_hand.png: the 32 x 32 in-hand spear (x6, every frame at x2) beside its inventory icon."""
	sprite = lance_du_jaguar_celeste_in_hand()
	icon = lance_du_jaguar_celeste().frames[0]
	frames = sprite.frames
	strip = 10
	lines = (len(frames) + strip - 1) // strip
	img = Image(max(12 + 32 * 6 + 24 + 16 * 6 + 12, 12 + strip * 70), 12 + 18 + 32 * 6 + 12 + lines * 70 + 12, (34, 34, 42, 255))
	img.text(12, 12, "lance_du_jaguar_celeste_in_hand (%d)" % len(frames), 2, (230, 230, 230, 255))

	def draw(x0, y0, frame, scale, cell):
		img.checker(x0, y0, len(frame) * scale, cell)
		for y, row in enumerate(frame):
			for x, px in enumerate(row):
				if px[3]:
					img.rect(x0 + x * scale, y0 + y * scale, scale, scale, px[:3] + (255,))

	draw(12, 30, frames[0], 6, 12)
	draw(12 + 32 * 6 + 24, 30, icon, 6, 12)
	for i, frame in enumerate(frames):
		draw(12 + (i % strip) * 70, 30 + 32 * 6 + 12 + (i // strip) * 70, frame, 2, 4)
	img.save(path)


# ---------------------------------------------------------------------------------------------------------- armor sets
# The three armor sets of the ruins (uniques.json5 format 5), four pieces each: inventory icons drawn like the vanilla
# armor icons (helmet, chestplate, leggings, boots silhouettes) and their worn layers (see the worn armor section).

FROST_PALETTE = {"O": "#0b2238", "W": "#ffffff", "L": "#dbe8f4", "M": "#9fb9d4", "D": "#6482a8", "N": "#3f5b85",
				 "B": "#3a7ccc", "b": "#1f4a8a", "I": "#c8f6ff", "C": "#5ad0f2", "c": "#2a8ccc", "K": "#0b1e34", "E": "#8ff4ff"}


def frost_frames(rows, gems, frames=10, frametime=2):
	"""Frost steel: a cold glint sweeps over the steel (W, L, M cells), the frost gems (cells drawn 'E') breathe."""
	base = paint(rows, FROST_PALETTE)
	steel = cells(rows, "WLM")
	out = []
	pulse = ["#8ff4ff", "#c8fbff", "#ffffff", "#c8fbff", "#8ff4ff", "#5ad8f8", "#3ab8ec", "#5ad8f8"]
	for f in range(frames):
		g = copy(base)
		shine(g, steel, -14 + f * 4, (0.6, 0.25), "#ffffff")
		put(g, cells(rows, gems), pulse[f % len(pulse)])
		out.append(g)
	return Sprite(out, anim(frametime))


def heaume_hiver_eternel():
	"""
	Great helm of the eternal winter: frosted steel lit from the top-left, a crest of three ice spikes on a sapphire band,
	a T visor whose eyes glow with frost, a sapphire rim. A cold glint crosses the steel, the eyes breathe.
	"""
	rows = outlined([
		"................",
		"................",
		".......WI.......",
		"...W...IC...C...",
		"...IC..IC..IC...",
		"..bBCBBICBBCBb..",
		"..LWLLLLLLLLMD..",
		"..LKEEKKKKEEKD..",
		"..LWLLMKKMLLMD..",
		"..LLLMMKKMMMDN..",
		"..LLMMDKKDMDDN..",
		"..bBBBbKKbBBbb..",
		"...c........c...",
		"................",
		"................",
		"................",
	])
	return frost_frames(rows, "E")


def cuirasse_hiver_eternel():
	"""
	Cuirass of the eternal winter: frosted steel plates under sapphire-trimmed pauldrons crowned with ice spikes, a frost
	crystal set in the chest, a sapphire belt. The glint crosses the plates, the crystal breathes.
	"""
	rows = outlined([
		"................",
		".C............C.",
		".IC..........CI.",
		".BBBb......bBBb.",
		".LWLLB....BLMMD.",
		".LWLLLBBBBLLMMD.",
		".DLWLLcIIcLLMDN.",
		"...LWcIEEIcLM...",
		"...LLLcIIcLMM...",
		"...LLMMMMMMMD...",
		"...LWLLLLLMMD...",
		"...bBBBBBBBBb...",
		"...LMMMMMMDDN...",
		"................",
		"................",
		"................",
	])
	return frost_frames(rows, "E")


def jambieres_hiver_eternel():
	"""
	Leggings of the eternal winter: a sapphire belt set with a frost gem, frosted plates, sapphire knee guards set with ice
	crystals, dark steel at the ankles. The glint runs down the plates, the gem and the crystals breathe.
	"""
	rows = outlined([
		"................",
		"................",
		"...bBBBEEBBBb...",
		"...LWLLLLLLMD...",
		"...LWLLLLLMMD...",
		"...LWLD..LLMD...",
		"...LLMD..LLMD...",
		"...BEBb..BEBb...",
		"...LLMD..LLMD...",
		"...LLMD..LMMD...",
		"...LMMD..LMDD...",
		"...NNDN..NDDN...",
		"................",
		"................",
		"................",
		"................",
	])
	return frost_frames(rows, "E")


def solerets_hiver_eternel():
	"""
	Sabatons of the eternal winter: rime-white cuffs, frosted steel, a sapphire strap, a frost gem on the instep, an ice spur
	at the heel, dark steel soles. The glint runs over the steel, the gems breathe.
	"""
	rows = outlined([
		"................",
		"................",
		"................",
		"..IIIc....IIIc..",
		"...LMD....LMD...",
		"...LMD....LMD...",
		"...BBb....BBb...",
		"...LMDC...LMDC..",
		"..LLMD....LMMD..",
		"..LEMD....LEMD..",
		".LLMMD....LMMMD.",
		".bbbbb....bbbbb.",
		"................",
		"................",
		"................",
		"................",
	])
	return frost_frames(rows, "E")


JAGUAR_SET_PALETTE = {"O": "#2a1406", "P": "#d98a34", "p": "#a8611f", "q": "#6e3c12", "S": "#2e1a0a", "G": "#ffe066",
					  "g": "#e8b020", "h": "#a86a14", "Y": "#fff8c8", "J": "#4fd090", "j": "#1f8a5a", "W": "#fff6e0", "w": "#d8c8a8",
					  "K": "#1a0c04", "R": "#ff5a3a", "r": "#a82418", "F": "#3ccf78", "f": "#1c7a44", "l": "#6a3a1a", "L": "#9a5a30",
					  "k": "#45230e", "e": "#d8f060"}


def jaguar_frames(rows, frames=10, frametime=2):
	"""Gold and jade: a sun glint runs over the gold (G, g, Y cells), the jade beads (J) sparkle one after the other."""
	base = paint(rows, JAGUAR_SET_PALETTE)
	gold = cells(rows, "GgY")
	beads = sorted(cells(rows, "J"))
	out = []
	for f in range(frames):
		g = copy(base)
		shine(g, gold, -12 + f * 3, (0.7, 0.3), "#ffffff")
		if beads:
			put(g, [beads[f % len(beads)]], "#c8ffe4")
		out.append(g)
	return Sprite(out, anim(frametime))


def masque_guerrier_jaguar():
	"""
	Mask of the jaguar warrior: a jaguar head of spotted pelt with its ears and jade eyes, a quetzal plume (green feathers,
	red tips) on the top, a golden band set with jade beads over the brow, the warrior's face in the open jaws (white
	fangs). A sun glint runs over the gold, the jade beads sparkle.
	"""
	rows = outlined([
		".......RR.......",
		"......RFFR......",
		"..Pp..fFFf..pP..",
		"..PPp..gG..pPq..",
		"..PPPPSPPSPPpq..",
		".PSPeSPPPPeSPpq.",
		".PPPPPSppSPPpqq.",
		".gGYGJGGGGJGgGh.",
		".PPWKKKKKKKKWpq.",
		".PPwKKKKKKKKwpq.",
		".PpKKKKKKKKKKpq.",
		".qq..........qq.",
		"................",
		"................",
		"................",
		"................",
	])
	return jaguar_frames(rows)


def pectoral_guerrier_jaguar():
	"""
	Pectoral of the jaguar warrior: a tunic of spotted jaguar pelt, a wide golden collar set with jade beads around the
	neck, a golden belt, a fringe of quetzal feathers. A sun glint runs over the gold, the jade beads sparkle.
	"""
	rows = outlined([
		"................",
		"................",
		".PSPG......GPSq.",
		".PPPYg....gGPpq.",
		".PSPPYGJJGgPSpq.",
		".qPSPPGggGPPpqq.",
		"...PPSPPPPSPq...",
		"...PSPPPSPPpq...",
		"...PPPSPPPSpq...",
		"...PSPPPPPPpq...",
		"...gGYGJGGGgh...",
		"...RFRFRFRFRF...",
		"...r.f.r.f.r....",
		"................",
		"................",
		"................",
	])
	return jaguar_frames(rows)


def jambieres_guerrier_jaguar():
	"""
	Leggings of the jaguar warrior: spotted pelt, a golden belt with a jade buckle, golden knee bands set with jade. A sun
	glint runs over the gold, the jade sparkles.
	"""
	rows = outlined([
		"................",
		"................",
		"...gGYGJJGGgh...",
		"...PSPPPPPSpq...",
		"...PPPSPPSPpq...",
		"...PSPp..PSpq...",
		"...PPPq..PPpq...",
		"...GJgh..GJgh...",
		"...PSPq..PSpq...",
		"...PPpq..PPpq...",
		"...PSpq..PSpq...",
		"...pqqS..pqqS...",
		"................",
		"................",
		"................",
		"................",
	])
	return jaguar_frames(rows)


def sandales_guerrier_jaguar():
	"""
	Sandals of the jaguar warrior: anklets of spotted pelt, golden straps crossing over the dark leather, a jade stud on the
	instep, golden soles. A sun glint runs over the gold, the jade sparkles.
	"""
	rows = outlined([
		"................",
		"................",
		"................",
		"..PSPp....PSPp..",
		"...Llk....Llk...",
		"...GGg....GGg...",
		"...Llk....Llk...",
		"...GGg....GGg...",
		"..LLlk....LLlk..",
		"..LJlk....LJlk..",
		".LLllk....LLllk.",
		".gGGGh....gGGGh.",
		"................",
		"................",
		"................",
		"................",
	])
	return jaguar_frames(rows)


SOUL_SET_PALETTE = {"O": "#04121a", "L": "#6e6878", "M": "#4c4756", "D": "#302b38", "N": "#1c1922", "K": "#0a080e",
					"A": "#7ff6ff", "a": "#e4ffff", "d": "#1d7486", "F": "#5ff0ff", "f": "#1aa8c0", "B": "#eaf6f2", "b": "#94bcc0"}
SOUL_FLICKER = [("#5ff0ff", "#1aa8c0", "#7ff6ff"), ("#8ffbff", "#2ac4dc", "#bffcff"), ("#3fd8f0", "#128ca4", "#5ff0ff"),
				("#8ffbff", "#2ac4dc", "#e4ffff"), ("#5ff0ff", "#1aa8c0", "#7ff6ff"), ("#3fd8f0", "#128ca4", "#3fd8f0")]


def soul_frames(rows, frametime=3):
	"""Soul fire: the flames (F, f) flicker and the cracks / eyes (A) pulse."""
	out = []
	for flame, ember, glow in SOUL_FLICKER:
		out.append(paint(rows, dict(SOUL_SET_PALETTE, F=flame, f=ember, A=glow)))
	return Sprite(out, anim(frametime))


def heaume_brasier_des_ames():
	"""
	Helm of the soul pyre: blackened soul-steel with two bone horns, a crest of soul fire, eyes and cracks glowing with
	the souls it holds. The flames flicker, the eyes pulse.
	"""
	rows = outlined([
		"................",
		".B.....FF.....B.",
		".Bb...fFFf...bB.",
		"..Bb..dLLd..bB..",
		"..bLLLLLLLLMMb..",
		"..LLMLLLLLLMMD..",
		"..LKAAKddKAAKD..",
		"..LMLMKddKMMDD..",
		"..MMdMKddKMdDN..",
		"..MDDDKKKKDDNN..",
		"..dDN......NDd..",
		"................",
		"................",
		"................",
		"................",
		"................",
	])
	return soul_frames(rows)


def cuirasse_brasier_des_ames():
	"""
	Cuirass of the soul pyre: blackened soul-steel, a ribcage of bone over a chest of soul fire, flames rising from the
	pauldrons, a belt with cyan studs. The flames flicker, the soul fire pulses between the ribs.
	"""
	rows = outlined([
		"................",
		".F............F.",
		".fF..........Ff.",
		".LMMd......dMMD.",
		".LLMDd....dLMMD.",
		".LLMMLddddLMMDN.",
		"...LMBbBBbBMD...",
		"...LMdAAAAdMD...",
		"...LMBbBBbBMD...",
		"...LMMdAAdMMD...",
		"...LMMMBBMMDD...",
		"...dDDADDADDd...",
		"...MDDDDDDDDN...",
		"................",
		"................",
		"................",
	])
	return soul_frames(rows)


def greves_brasier_des_ames():
	"""
	Greaves of the soul pyre: blackened soul-steel veined with soul fire, a belt with a bone skull buckle, knee guards with
	a glowing core. The soul fire pulses in the cracks.
	"""
	rows = outlined([
		"................",
		"................",
		"...dDDDAADDDd...",
		"...LLMMddMMDN...",
		"...LMMMMMMMDN...",
		"...LMAD..LMAN...",
		"...LMMD..LMMD...",
		"...dAdD..dAdN...",
		"...LMMD..LMDN...",
		"...LMAD..LMAN...",
		"...LMMD..MDDN...",
		"...DDNN..DNNN...",
		"................",
		"................",
		"................",
		"................",
	])
	return soul_frames(rows)


def bottes_brasier_des_ames():
	"""
	Boots of the soul pyre: blackened soul-steel, soul fire burning around the ankles, cyan cracks, bone toe caps. The
	flames flicker, the cracks pulse.
	"""
	rows = outlined([
		"................",
		"................",
		"..F.F.....F.F...",
		"..fFfF....fFfF..",
		"...LMD....LMD...",
		"...LAD....LAD...",
		"...dDd....dDd...",
		"...LMD....LMD...",
		"..LLMD....LMMD..",
		"..LAMD....LAMD..",
		".BbMMD....BbMMD.",
		".NNDDN....NDDNN.",
		"................",
		"................",
		"................",
		"................",
	])
	return soul_frames(rows)


SET_PIECES = {
	# name: (draw, parent model)
	"heaume_hiver_eternel": (heaume_hiver_eternel, "minecraft:item/generated"),
	"cuirasse_hiver_eternel": (cuirasse_hiver_eternel, "minecraft:item/generated"),
	"jambieres_hiver_eternel": (jambieres_hiver_eternel, "minecraft:item/generated"),
	"solerets_hiver_eternel": (solerets_hiver_eternel, "minecraft:item/generated"),
	"masque_guerrier_jaguar": (masque_guerrier_jaguar, "minecraft:item/generated"),
	"pectoral_guerrier_jaguar": (pectoral_guerrier_jaguar, "minecraft:item/generated"),
	"jambieres_guerrier_jaguar": (jambieres_guerrier_jaguar, "minecraft:item/generated"),
	"sandales_guerrier_jaguar": (sandales_guerrier_jaguar, "minecraft:item/generated"),
	"heaume_brasier_des_ames": (heaume_brasier_des_ames, "minecraft:item/generated"),
	"cuirasse_brasier_des_ames": (cuirasse_brasier_des_ames, "minecraft:item/generated"),
	"greves_brasier_des_ames": (greves_brasier_des_ames, "minecraft:item/generated"),
	"bottes_brasier_des_ames": (bottes_brasier_des_ames, "minecraft:item/generated"),
}


def set_piece_sprites():
	"""Texture name -> Sprite of the armor set pieces (inventory icons)."""
	return {name: draw() for name, (draw, _) in SET_PIECES.items()}


# ---------------------------------------------------------------------------------------------------------- 3D shield
# The Egide des Ames in hand: a JSON element model instead of the vanilla shield (special model drawing banner patterns).
# The vanilla shield renderer draws its entity model (plate 12 x 22 x 1, handle 2 x 6 x 6) flipped by the item
# transformation scale(1, -1, -1); the same box in element coordinates is plate x -6..6, y -11..11, z 1..2 and handle
# x -1..1, y -3..3, z -5..1 (the design faces +z). The elements below stay inside those bounds and the models reuse the
# display transforms of minecraft:item/shield and item/shield_blocking, so the shield sits exactly where the vanilla one
# does (in hand, blocking, on the ground, in frames and on shelves). The texture is an item atlas sprite: it animates.

SHIELD_TEXTURE = 32
# Texture layout (pixels): front 12 x 22 at (0, 0), back 12 x 22 at (12, 0), plate edges (1 x 22 at (24, 0), 12 x 1 at
# (0, 22)), skull boss front 6 x 7 at (25, 0) with its sides (1 x 7 at (31, 0)) and top/bottom (6 x 1 at (25, 7)), handle
# side 6 x 6 at (25, 8), handle front/back 2 x 6 at (25, 14), handle top/bottom 2 x 6 at (27, 14).

# Heater outline: rows of the 12 x 22 front (top to bottom) -> first and last column of the plate.
SHIELD_STEPS = [(0, 13, 0, 11), (14, 16, 1, 10), (17, 18, 2, 9), (19, 20, 3, 8), (21, 21, 4, 7)]


def shield_elements():
	"""The elements of the 3D shield: (name, from, to, faces {side: (x, y, w, h) texture pixels, flip}) in element units."""
	elements = []
	for i, (top, bottom, first, last) in enumerate(SHIELD_STEPS):
		x0, x1 = first - 6, last + 1 - 6
		y0, y1 = 11 - (bottom + 1), 11 - top
		w, h = x1 - x0, y1 - y0
		faces = {
			"south": (first, top, w, h),
			"north": (12 + 11 - last, top, w, h),
			"west": (24, top, 1, h),
			"east": (24, top, 1, h),
			"down": (first, 22, w, 1),
		}
		if i == 0:
			faces["up"] = (first, 22, w, 1)
		elements.append(("plate_%d" % i, [x0, y0, 1], [x1, y1, 2], faces))
	# The skull boss stands one pixel out of the plate: cranium 6 x 5, jaw 4 x 2.
	elements.append(("cranium", [-3, 2, 2], [3, 7, 3], {"south": (25, 0, 6, 5), "west": (31, 0, 1, 5), "east": (31, 0, 1, 5),
														   "up": (25, 7, 6, 1), "down": (25, 7, 6, 1)}))
	elements.append(("jaw", [-2, 0, 2], [2, 2, 3], {"south": (26, 5, 4, 2), "west": (31, 5, 1, 2), "east": (31, 5, 1, 2),
													 "down": (26, 7, 4, 1)}))
	elements.append(("handle", [-1, -3, -5], [1, 3, 1], {"north": (25, 14, 2, 6), "west": (25, 8, 6, 6), "east": (25, 8, 6, 6),
														   "up": (27, 14, 2, 6), "down": (27, 14, 2, 6)}))
	return elements


# Display transforms of the vanilla shield models (minecraft:models/item/shield.json and shield_blocking.json).
SHIELD_DISPLAY = {
	"thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [10, 6, -4], "scale": [1, 1, 1]},
	"thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [10, 6, 12], "scale": [1, 1, 1]},
	"firstperson_righthand": {"rotation": [0, 180, 5], "translation": [-10, 1.75, -10], "scale": [1.25, 1.25, 1.25]},
	"firstperson_lefthand": {"rotation": [0, 180, 5], "translation": [10, 0, -10], "scale": [1.25, 1.25, 1.25]},
	"gui": {"rotation": [15, -25, -5], "translation": [2, 3, 0], "scale": [0.65, 0.65, 0.65]},
	"fixed": {"rotation": [0, 180, 0], "translation": [-4.5, 4.5, -5], "scale": [0.55, 0.55, 0.55]},
	"on_shelf": {"rotation": [0, 0, 0], "translation": [11, 18.5, 8.7], "scale": [1.4, 1.4, 1.4]},
	"ground": {"rotation": [0, 0, 0], "translation": [2, 4, 2], "scale": [0.25, 0.25, 0.25]},
}
SHIELD_BLOCKING_DISPLAY = {
	"thirdperson_righthand": {"rotation": [45, 155, 0], "translation": [-3.49, 11, -2], "scale": [1, 1, 1]},
	"thirdperson_lefthand": {"rotation": [45, 155, 0], "translation": [11.51, 7, 2.5], "scale": [1, 1, 1]},
	"firstperson_righthand": {"rotation": [0, 180, -5], "translation": [-15, 3.25, -11], "scale": [1.25, 1.25, 1.25]},
	"firstperson_lefthand": {"rotation": [0, 180, -5], "translation": [5, 5, -11], "scale": [1.25, 1.25, 1.25]},
}


def shield_3d_models(name):
	"""models/item/<name>_in_hand.json (elements + vanilla shield transforms) and <name>_in_hand_blocking.json (its child
	with the blocking transforms)."""
	texture = "guildy:item/" + name + "_in_hand"
	scale = 16.0 / SHIELD_TEXTURE

	def uv(rect, side):
		x, y, w, h = rect
		u0, v0, u1, v1 = x * scale, y * scale, (x + w) * scale, (y + h) * scale
		return [u0, v0, u1, v1]

	elements = []
	for label, start, end, faces in shield_elements():
		elements.append({
			"name": label,
			"from": start,
			"to": end,
			"faces": {side: {"uv": uv(rect, side), "texture": "#shield"} for side, rect in faces.items()},
		})
	write_json(os.path.join(ASSETS, "models", "item", name + "_in_hand.json"), {
		"textures": {"shield": texture, "particle": texture},
		"elements": elements,
		"display": SHIELD_DISPLAY,
	})
	write_json(os.path.join(ASSETS, "models", "item", name + "_in_hand_blocking.json"), {
		"parent": "guildy:item/" + name + "_in_hand",
		"display": SHIELD_BLOCKING_DISPLAY,
	})


SHIELD_FRONT = [
	"aAAAAAAAAAAd",
	"AkkkkkkkkkkD",
	"AkfkkkkkkfkD",
	"AkkkkkkkkkkD",
	"AkkkkkkkkkkD",
	"AkFkkkkkkFkD",
	"AkfkkkkkkfkD",
	"AkFfkkkkfFkD",
	"AfFfkkkkfFfD",
	"AkFFfkkfFFkD",
	"AkfFFkkFFfkD",
	"AkkfFFFFfkkD",
	"AkkkfFFfkkkD",
	"AkfkkfFkkfkD",
	"OAkkkFfkkkdO",
	"OAkfkfFkfkdO",
	"OAkkkFfkkkdO",
	"OOAkkfFkkdOO",
	"OOAkkFfkkdOO",
	"OOOAkfFkdOOO",
	"OOOAkFfkdOOO",
	"OOOOAddDOOOO",
]

SHIELD_BACK = [
	"DddddddddddD",
	"dKKKKKKKKKKd",
	"dKssssssssKd",
	"dKKKKKKKKKKd",
	"dKKKKKKKKKKd",
	"dKKKrKKrKKKd",
	"dKKKKKKKKKKd",
	"dKKKKKKKKKKd",
	"dKssssssssKd",
	"dKKKKKKKKKKd",
	"dKKKKKKKKKKd",
	"dKKKrKKrKKKd",
	"dKKKKKKKKKKd",
	"dKssssssssKd",
	"DdKKKKKKKKdD",
	"DdKKKKKKKKdD",
	"DDdKKKKKKdDD",
	"DDdKKKKKKdDD",
	"DDDdKKKKdDDD",
	"DDDddKKddDDD",
	"DDDDdKKdDDDD",
	"DDDDddddDDDD",
]

SKULL = [
	".BBBB.",
	"BBBBBb",
	"BnBBnb",
	"BEBBEb",
	"bBnnBb",
	".BbBb.",
	".bnbn.",
]

SHIELD_PALETTE = {"O": "#04121a", "A": "#7ff6ff", "a": "#e4ffff", "d": "#1d7486", "D": "#0f4452", "k": "#0e2028",
				  "K": "#1a262c", "s": "#4a3a2c", "r": "#9fe8f0", "B": "#eaf6f2", "b": "#94bcc0", "n": "#03090c",
				  "F": "#5ff0ff", "f": "#1aa8c0", "E": "#ffffff", "w": "#5a3a22", "W": "#7a5232", "h": "#2c1a10",
				  "i": "#3a4a52"}


def egide_des_ames_in_hand():
	"""
	The 32 x 32 texture of the 3D shield: the heater plate of blackened soul-steel with its cyan rim and soul flames, the
	skull boss with glowing eyes, the dark back with leather straps and the handle. Same animation as the icon (the soul
	fire flickers, the eyes flare).
	"""
	frames = []
	for f in range(8):
		flame, ember = [("#5ff0ff", "#1aa8c0"), ("#8ffbff", "#2ac4dc"), ("#3fd8f0", "#128ca4"), ("#8ffbff", "#2ac4dc")][f % 4]
		eye = ["#bffcff", "#5ff0ff", "#2ad8f0", "#5ff0ff"][f % 4]
		pal = dict(SHIELD_PALETTE, F=flame, f=ember, E=eye)
		g = [[TRANSPARENT] * SHIELD_TEXTURE for _ in range(SHIELD_TEXTURE)]

		def block(x0, y0, rows):
			for y, row in enumerate(rows):
				for x, ch in enumerate(row):
					if ch != ".":
						g[y0 + y][x0 + x] = rgba(pal[ch])

		block(0, 0, SHIELD_FRONT)
		block(12, 0, SHIELD_BACK)
		block(24, 0, ["A"] * 22)
		block(0, 22, ["A" * 12])
		block(25, 0, SKULL)
		block(31, 0, ["b"] * 7)
		block(25, 7, ["bbbbbb"])
		block(25, 8, ["wWwwWw", "hhhhhh", "wwWwwW", "wWwwWw", "hhhhhh", "Wwwwww"])
		block(25, 14, ["wW", "hh", "wW", "Ww", "hh", "wW"])
		block(27, 14, ["wW", "wW", "wW", "Ww", "Ww", "Ww"])
		frames.append(g)
	return Sprite(frames, anim(3))


# ---------------------------------------------------------------------------------------------------------- worn armor
# The unique armors on the player: equipment assets assets/guildy/equipment/<name>.json, each with its 64 x 32 layer
# textures/entity/equipment/humanoid/<name>.png. Layout = top half of the player skin: head 0,0 (+ hat 32,0), right leg
# 0,16, body 16,16, right arm 40,16, the left limbs mirror the right ones. Helmet and chestplate use the player model
# inflated by 1, the boots the legs inflated by 0.9; the leggings use the humanoid_leggings layer (textures/entity/equipment/
# humanoid_leggings/<name>.png, same layout: body inflated by 0.5, legs by 0.4).
# These layers are not dyeable (the drawing replaces the dyed leather) and baby mobs keep the vanilla look of the base
# item (other layout). Equipment textures are plain textures, not atlas sprites: Minecraft does not animate them.

# Boxes of the layout: part -> (u, v, width, height, depth).
LAYOUT = {"head": (0, 0, 8, 8, 8), "hat": (32, 0, 8, 8, 8), "body": (16, 16, 8, 12, 4), "arm": (40, 16, 4, 12, 4),
		  "leg": (0, 16, 4, 12, 4)}


def face_rect(part, side):
	"""(x, y, width, height) of one face of a box in the texture. 'ring' = the four sides unrolled (right, front, left,
	back: each one continues the previous one around the box); right = the outer side of a right limb, back seen from
	behind."""
	u, v, w, h, d = LAYOUT[part]
	return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
			"left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h), "ring": (u, v + d, 2 * (d + w), h)}[side]


class Layer:
	"""A 64 x 32 equipment texture being drawn, face by face (character grids, '.' = transparent)."""

	def __init__(self, palette):
		self.palette = palette
		self.grid = [[TRANSPARENT] * 64 for _ in range(32)]

	def draw(self, part, side, rows):
		x0, y0, w, h = face_rect(part, side)
		if len(rows) != h or any(len(row) != w for row in rows):
			raise ValueError("%s %s: %d rows of %d pixels expected: %r" % (part, side, h, w, rows))
		for y, row in enumerate(rows):
			for x, ch in enumerate(row):
				if ch != ".":
					self.grid[y0 + y][x0 + x] = rgba(self.palette[ch])
		return self

	def put(self, part, side, x, y, color):
		x0, y0, _, _ = face_rect(part, side)
		self.grid[y0 + y][x0 + x] = rgba(color)
		return self


def couronne_worn():
	"""
	The frost king's crown on the head: a ring of ice crystals (tall ones above the brow and the nape, smaller ones above
	the ears, one on each corner), open on the top so the hair shows through, on a frosted silver circlet set with frost
	gems (the largest one hanging on the brow), icicles at the temples and a frost veil over the nape.
	"""
	pal = {"W": "#ffffff", "I": "#c8f2ff", "i": "#78d4f6", "c": "#3aa0e0", "C": "#1f64b0",
		   "S": "#eef3f8", "s": "#b2c0d0", "t": "#7a8ca4", "T": "#55667e",
		   "E": "#f0ffff", "e": "#50e8ff", "f": "#1a86e0", "g": "#0a3c88"}
	layer = Layer(pal)
	# right (back edge -> front edge) | front | left (front edge -> back edge) | back
	layer.draw("head", "ring", [
		"........" + "...Wi..." + "........" + "...Wi...",
		"i..Ii..I" + "i..IW..I" + "i..Ii..I" + "i..IW..I",
		"c.IWic.i" + "c.IWic.i" + "c.IWic.i" + "c.IWic.i",
		"SSSSeSSS" + "SSSEeSSS" + "SSSeSSSS" + "SSSEeSSS",
		"tTttfttT" + "t..ef..t" + "TttfttTt" + "tTtefTtT",
		".....ic." + "........" + ".ic....." + "iCciCciC",
		"......c." + "........" + ".c......" + "c.i.c.i.",
		"........" + "........" + "........" + "..c...c.",
	])
	return layer.grid


def scale_rows(width, rows, start=0, shift=0):
	"""Jade scales in staggered rows (two pixels high, offset by half a scale from one row to the next), lit from the
	top-left; start = index of the first row in the pattern, shift = horizontal offset of the pattern."""
	tile = ["JJjk", "jjkK", "jkJJ", "kKjj"]
	return ["".join(tile[(start + y) % 4][(shift + x) % 4] for x in range(width)) for y in range(rows)]


def plastron_worn():
	"""
	The feathered serpent's chestplate: jade scales lit from the top-left, a golden torque and belt, a golden serpent
	medallion with ruby eyes on the chest, a golden feathered serpent winding down the back, quetzal feather mantles
	(green feathers, red and turquoise tips) on the shoulders.
	"""
	pal = {"J": "#6ee8a8", "j": "#34b878", "k": "#1f8a56", "K": "#0f5e38", "G": "#ffe68a", "g": "#e8b03a", "h": "#a86e1c",
		   "H": "#6a4210", "R": "#ff6a48", "r": "#c22c1c", "e": "#ff2a20", "q": "#4ae8e0", "F": "#5fe896", "f": "#24a85e",
		   "d": "#146a3a"}
	layer = Layer(pal)
	layer.draw("body", "front", [
		"hgGGGGgh",
		"JhgGGghK",
		"jhGGGGhK",
		"JgeGGegK",
		"jhgGGghK",
	] + scale_rows(8, 5, 0) + [
		"hgGGGGgh",
		"JjkGgJkK",
	])
	back = ["hgGGGGgh"] + scale_rows(8, 9, 1, 1) + ["hgGGGGgh", "JjkKJjkK"]
	layer.draw("body", "back", back)
	# The serpent winding down the back: head between the shoulders, body swinging left and right, tail at the belt.
	for y, x in enumerate([3, 4, 5, 5, 4, 3, 2, 2, 3], start=1):
		layer.put("body", "back", x, y, "#ffe68a").put("body", "back", x + 1, y, "#e8b03a")
	layer.put("body", "back", 3, 1, "#ff2a20").put("body", "back", 4, 1, "#ff2a20")
	for side in ("right", "left"):
		layer.draw("body", side, ["gggh"] + scale_rows(4, 9, 1, 2) + ["gGgh", "JjkK"])
	layer.draw("body", "top", ["hgGGGGgh", "Jj....jK", "jJ....kK", "hggggggh"])
	layer.draw("body", "bottom", scale_rows(8, 4))
	layer.draw("arm", "ring", [
		"gGgh" + "GGgh" + "gggh" + "gGgh",
		"FfdF" + "FfFd" + "dfFf" + "FfdF",
		"FfdF" + "fFdf" + "dfFf" + "FfdF",
		"Rfdq" + "FrqF" + "qdfR" + "qfrF",
		"r.R." + ".r.q" + "...." + ".R.r",
		"...." + "...." + "...." + "....",
		"...." + "...." + "...." + "....",
		"...." + "...." + "...." + "....",
		"...." + "...." + "...." + "....",
		"...." + "...." + "...." + "....",
		"...." + "...." + "...." + "....",
		"...." + "...." + "...." + "....",
	])
	layer.draw("arm", "top", ["GggG", "gFfg", "gfdg", "hggh"])
	return layer.grid


def zephyr_worn():
	"""
	The Zephyr boots: cloud-white boots shaded with sky blue, golden cuffs set with a sky gem on the front, a golden strap,
	golden soles, and a feathered wing rising from each ankle on the outer side (the left boot mirrors the right one).
	"""
	pal = {"W": "#ffffff", "L": "#f4faff", "M": "#cfe6fb", "D": "#8fb8e0", "N": "#5f88b4", "G": "#ffe48a", "g": "#e0a830",
		   "h": "#a06a1c", "C": "#5ad8ff", "c": "#2a9ad0", "F": "#f0f8ff", "e": "#b4d4f0", "E": "#7aa8d8"}
	layer = Layer(pal)
	# outer side (back -> front) | front | inner side (front -> back) | back
	layer.draw("leg", "ring", [
		"W..." + "...." + "...." + "...W",
		"WW.." + "...." + "...." + "...W",
		"WW.." + "...." + "...." + "...W",
		"EWW." + "...." + "...." + "...E",
		"EEWW" + "W..." + "...." + "...E",
		".EEg" + "W..." + "...." + "....",
		"GgGg" + "GCcg" + "gGgh" + "gGgh",
		"LLMD" + "LLMD" + "LMDN" + "MMDN",
		"LMMD" + "LMMD" + "MMDN" + "MDDN",
		"gGgh" + "gGgh" + "gGgh" + "gGgh",
		"LLMD" + "LLMD" + "LMDN" + "MDDN",
		"hggh" + "GGgh" + "gggh" + "hhhh",
	])
	layer.draw("leg", "bottom", ["hggh", "gGgh", "gggh", "hhhh"])
	return layer.grid


def coursier_worn():
	"""
	The courier's boots: tall riding boots of fine green leather with folded tan cuffs, brass buckles on the straps, dark
	heeled soles.
	"""
	pal = {"L": "#7cc86e", "M": "#4a9a4e", "D": "#2e6a36", "N": "#1e4a26", "C": "#f0d8a0", "c": "#c8a060", "x": "#9a7440",
		   "b": "#a8741e", "B": "#ffe080", "s": "#4a2e1a", "S": "#6e4a2c", "t": "#a6e090"}
	layer = Layer(pal)
	# outer side (back -> front) | front | inner side (front -> back) | back
	layer.draw("leg", "ring", [
		"...." + "...." + "...." + "....",
		"...." + "...." + "...." + "....",
		"...." + "...." + "...." + "....",
		"...." + "...." + "...." + "....",
		"CCCc" + "CCCc" + "CCcx" + "CCcx",
		"cxcx" + "cxcx" + "cxxx" + "cxxx",
		"LMMD" + "LMMD" + "MMDN" + "LMDN",
		"LtMD" + "LtMD" + "MMDN" + "MMDN",
		"bBbs" + "bBbs" + "MDDN" + "sbBb",
		"LMMD" + "LMMD" + "MDDN" + "MMDN",
		"LMMD" + "LMMD" + "MDDN" + "sSSs",
		"ssSs" + "SSss" + "sSss" + "SSSs",
	])
	layer.draw("leg", "bottom", ["sSss", "Ssss", "ssss", "ssss"])
	return layer.grid


# ---------------------------------------------------------------------- worn layers of the armor sets
# Helmets, chestplates and boots on the humanoid layer, leggings on the humanoid_leggings layer (body box = the waist, leg
# boxes = thighs to shins, under the boots). The helmets also draw on the hat box (head inflated by half a pixel more):
# crests, ears and horns stand out of the helm.

def mirror(rows):
	"""The rows of a face seen from the other side (left side of a head drawn from its right side)."""
	return [row[::-1] for row in rows]


def frost_worn_head():
	"""
	Great helm of the eternal winter: frosted steel, a sapphire brow band and rim, a T visor whose eyes glow with frost,
	frost crystals on the ear guards, a crest of ice along the top (on the hat box, standing out of the helm).
	"""
	layer = Layer(FROST_PALETTE)
	side = ["MLLLLLWL", "MLLLLWLL", "bBBBBBBb", "DMLLLLLK", "DMLcIcLL", "DMMLcLLL", "NDMMMMML", "bBBBBBBb"]
	front = ["LWLLLLMD", "LWLLLMMD", "bBBBBBBb", "KEEKKEEK", "LWLKKLMD", "LLMKKMMD", "LMMKKMDN", "bBBbbBBb"]
	back = ["MLLWLLLM", "MLLWLLMD", "bBBBBBBb", "DMLLLLMD", "DMLIcLMD", "DMLLcLMD", "NDMMMMDN", "bBBBBBBb"]
	layer.draw("head", "ring", [side[y] + front[y] + mirror(side)[y] + back[y] for y in range(8)])
	layer.draw("head", "top", ["MLLIcLMD", "LWLIcLMD", "LWLIcLLD", "LWLIcLMD", "LLLIcLMD", "LWLIcLMD", "LLLIcLMD", "bBBIcBBb"])
	layer.draw("head", "bottom", ["NDDDDDDN"] * 8)
	# The ice crest, half a pixel above the helm: a ridge from the brow to the nape.
	layer.draw("hat", "top", ["...WI..."] + ["...Ic..."] * 6 + ["...IC..."])
	layer.draw("hat", "ring", ["........" + "...IC..." + "........" + "...cI..."] + ["................................"] * 7)
	return layer.grid


def frost_worn_chest():
	"""
	Cuirass of the eternal winter: frosted steel plates under a sapphire collar, a frost snowflake on the chest, a sapphire
	spine down the back, a belt set with a frost gem; sapphire-trimmed pauldrons set with ice crystals, steel sleeves and
	cuffs of rime.
	"""
	layer = Layer(FROST_PALETTE)
	layer.draw("body", "front", ["bBBBBBBb", "LLcLLcLD", "LLLIILMD", "LcIEEIcD", "LWLIILMD", "LLcLLcMD", "MMMMMMMD", "LWLLLLMD",
								 "LWLLLMMD", "MMMMMMDN", "bBBEEBBb", "LMMMMMDN"])
	layer.draw("body", "back", ["bBBBBBBb", "MLLBBLLM", "MLLbBLLD", "MLWBbLMD", "MLLbBLMD", "MMMBBMMD", "MLLbBLMD", "MLLBbLMD",
								"MLLbBMMD", "MMMBBMDN", "bBBBBBBb", "MMMMMDDN"])
	sides = ["bBBb", "LLMD", "LWMD", "LLMD", "LLMD", "LLMD", "MMMD", "LLMD", "LMMD", "MMDN", "bBBb", "MMDN"]
	layer.draw("body", "right", sides).draw("body", "left", mirror(sides))
	layer.draw("body", "top", ["bBBBBBBb", "LWLLLLMD", "LLLLLLMD", "bBBBBBBb"])
	layer.draw("body", "bottom", ["MMMMMMDN"] * 4)
	layer.draw("arm", "ring", [
		"bBBb" + "bBBb" + "bBBb" + "bBBb",
		"cIIc" + "LWLM" + "LLMD" + "LLMD",
		"IEcI" + "LWLM" + "LMMD" + "MLMD",
		"cIIc" + "LWLM" + "LMMD" + "LLMD",
		"bBBb" + "bBBb" + "bBBb" + "bBBb",
		"LMMD" + "LWMD" + "MMDN" + "LMMD",
		"LLMD" + "LWMD" + "MMDN" + "LMMD",
		"LMMD" + "LLMD" + "MDDN" + "LMDD",
		"bBBb" + "bBBb" + "bBBb" + "bBBb",
		"IcIc" + "cIcI" + "IcIc" + "cIcI",
		"...." + "...." + "...." + "....",
		"...." + "...." + "...." + "....",
	])
	layer.draw("arm", "top", ["bIcb", "IEcI", "cIIc", "bBBb"])
	return layer.grid


def frost_worn_legs():
	"""
	Leggings of the eternal winter: a sapphire belt set with a frost gem and frosted tassets at the waist, steel thigh
	plates, sapphire knee guards set with a frost crystal, steel greaves.
	"""
	layer = Layer(FROST_PALETTE)
	empty = ["........"] * 8
	layer.draw("body", "front", empty + ["bBBEEBBb", "LWLLLLMD", "LWLMMLMD", "LLMDDMMD"])
	layer.draw("body", "back", empty + ["bBBBBBBb", "MLLLLLMD", "MLLMMLMD", "DMMDDMMN"])
	sides = ["...."] * 8 + ["bBBb", "LLMD", "LLMD", "MMDN"]
	layer.draw("body", "right", sides).draw("body", "left", mirror(sides))
	layer.draw("leg", "ring", [
		"LLMD" + "LWLM" + "LMMD" + "MMMD",
		"LLMD" + "LWLM" + "LMMD" + "LMMD",
		"LLMD" + "LWLD" + "MMDN" + "LMMD",
		"bBBb" + "bBBb" + "bBBb" + "bBBb",
		"IcIc" + "BEEb" + "BcBb" + "bBBb",
		"bBBb" + "bBBb" + "bBBb" + "bBBb",
		"LLMD" + "LWLM" + "LMMD" + "LMMD",
		"LLMD" + "LWLM" + "MMDN" + "LMMD",
		"LMMD" + "LLMD" + "MMDN" + "MMDD",
		"MMDN" + "LMMD" + "MDDN" + "MMDN",
		"bBBb" + "bBBb" + "bBBb" + "bBBb",
		"...." + "...." + "...." + "....",
	])
	layer.draw("leg", "top", ["LLMD", "LWMD", "LLMD", "MMDN"])
	return layer.grid


def frost_worn_feet():
	"""
	Sabatons of the eternal winter: cuffs of white rime, frosted steel, a sapphire strap set with a frost gem on the
	instep, sapphire soles.
	"""
	layer = Layer(FROST_PALETTE)
	layer.draw("leg", "ring", [
		"...." + "...." + "...." + "....",
		"...." + "...." + "...." + "....",
		"...." + "...." + "...." + "....",
		"...." + "...." + "...." + "....",
		"...." + "...." + "...." + "....",
		"IWII" + "IWII" + "IIIc" + "IIIc",
		"cIcc" + "IcIc" + "cIcc" + "cccc",
		"LLMD" + "LWLM" + "LMMD" + "LMMD",
		"LLMD" + "LWEM" + "LMMD" + "LMMD",
		"LLMD" + "LWLM" + "LMMD" + "LMMD",
		"LMMD" + "LWMD" + "MMDN" + "MMDN",
		"bbbb" + "bBBb" + "bbbb" + "bbbb",
	])
	layer.draw("leg", "bottom", ["bbbb", "bBBb", "bbbb", "bbbb"])
	return layer.grid


JAGUAR_ROSETTES = ["PPSPPPPP", "PSpSPPPP", "PPSPPPPS", "PPPPPPPP", "PPPPPSPP", "SPPPSpPP", "PPPPPSSP", "PPPPPPPP"]


def pelt(width, height, start=0, shift=0):
	"""Rows of spotted jaguar pelt (open rosettes on ochre) for a face; start / shift move the pattern."""
	return ["".join(JAGUAR_ROSETTES[(start + y) % 8][(shift + x) % 8] for x in range(width)) for y in range(height)]


def over(rows, *changes):
	"""The rows with some of them replaced: changes = (row index, text); '_' in a text keeps the pixel below."""
	out = list(rows)
	for y, text in changes:
		out[y] = "".join(old if new == "_" else new for old, new in zip(out[y], text))
	return out


def jaguar_worn_head():
	"""
	Mask of the jaguar warrior: the jaguar's head of spotted pelt over the warrior's head, its jade-yellow eyes above a
	golden band set with jade, the face in its open jaws (fangs at the corners), its ears standing up and a quetzal plume
	running over the top and hanging down the back (on the hat box).
	"""
	layer = Layer(JAGUAR_SET_PALETTE)
	front = over(pelt(8, 8, 3, 1), (1, "_e____e_"), (2, "gGJGGJGg"), (3, "PW....WP"), (4, "Pw....wP"), (5, "P......P"),
				 (6, "p......p"), (7, "pWqppqWp"))
	side = over(pelt(8, 8, 1, 2), (2, "gGgGJGgG"), (7, "qp____pq"))
	back = over(pelt(8, 8, 5, 3), (2, "gGgGGgGg"), (7, "qp____pq"))
	for y in range(8):
		back[y] = back[y][:3] + ("Rr" if y >= 6 else "Ff") + back[y][5:]
	layer.draw("head", "ring", [side[y] + front[y] + mirror(side)[y] + back[y] for y in range(8)])
	top = pelt(8, 8, 2, 4)
	layer.draw("head", "top", [row[:3] + "Ff" + row[5:] for row in top])
	layer.draw("head", "bottom", ["qqqqqqqq"] * 8)
	# Ears at the front corners, the plume standing over the top and falling down the back.
	layer.draw("hat", "ring", [
		"........" + "Pp....pP" + "........" + "...Ff...",
		"........" + "pq....qp" + "........" + "...Ff...",
		"........" + "........" + "........" + "...Ff...",
		"........" + "........" + "........" + "...Ff...",
		"........" + "........" + "........" + "...Rr...",
		"........" + "........" + "........" + "........",
		"........" + "........" + "........" + "........",
		"........" + "........" + "........" + "........",
	])
	layer.draw("hat", "top", ["...Ff..."] * 5 + ["...gG...", "........", "........"])
	return layer.grid


def jaguar_worn_chest():
	"""
	Pectoral of the jaguar warrior: a tunic of spotted jaguar pelt, a wide golden gorget set with jade beads and a jade
	pendant, a golden belt with a fringe of quetzal feathers (green and red); pelt mantles on the shoulders held by golden
	arm bands set with jade, bare forearms.
	"""
	layer = Layer(JAGUAR_SET_PALETTE)
	layer.draw("body", "front", over(pelt(8, 12, 0, 0), (0, "GgJGGJgG"), (1, "gGGJJGGg"), (2, "PgGYYGgP"), (3, "_PgJJgP_"),
									 (4, "___gg___"), (10, "gGYGJGgh"), (11, "RFRFRFRF")))
	layer.draw("body", "back", over(pelt(8, 12, 4, 2), (0, "gGGGGGGg"), (1, "PgGGGGgP"), (10, "gGgGGgGh"), (11, "FRFRFRFR")))
	sides = over(pelt(4, 12, 2, 5), (0, "gGGg"), (10, "gGgh"), (11, "RFRF"))
	layer.draw("body", "right", sides).draw("body", "left", mirror(sides))
	layer.draw("body", "top", ["gGJGGJGg", "GgGGGGgG", "gGGGGGGg", "PPSPPSPP"])
	layer.draw("body", "bottom", ["FRFRFRFR"] * 4)
	arm = over(pelt(16, 12, 1, 0), (4, "gGJg" + "gGJg" + "gGgh" + "gGJg"))
	layer.draw("arm", "ring", arm[:5] + ["................"] * 7)
	layer.draw("arm", "top", pelt(4, 4, 6, 1))
	return layer.grid


def jaguar_worn_legs():
	"""
	Leggings of the jaguar warrior: a golden belt with a jade buckle, a red loincloth with a golden fringe hanging at the
	front, spotted pelt down the legs, golden knee bands set with jade.
	"""
	layer = Layer(JAGUAR_SET_PALETTE)
	empty = ["........"] * 8
	layer.draw("body", "front", empty + ["gGYGJGgh", "PSRRRRSP", "PPRrrRPp", "pPRYYRpq"])
	layer.draw("body", "back", empty + over(pelt(8, 4, 3, 1), (0, "gGgGGgGh")))
	sides = ["...."] * 8 + over(pelt(4, 4, 5, 2), (0, "gGgh"))
	layer.draw("body", "right", sides).draw("body", "left", mirror(sides))
	legs = over(pelt(16, 12, 2, 3), (0, "____" + "__Rr" + "________"), (1, "____" + "__Rr" + "________"),
				(2, "____" + "__rR" + "________"), (3, "____" + "__YG" + "________"),
				(4, "gGJg" + "gGJg" + "gGgh" + "gGgh"), (9, "pPPq" + "pPpq" + "ppqq" + "pPPq"))
	layer.draw("leg", "ring", legs[:10] + ["................"] * 2)
	layer.draw("leg", "top", pelt(4, 4, 0, 2))
	return layer.grid


def jaguar_worn_feet():
	"""
	Sandals of the jaguar warrior: anklets of spotted pelt, a golden strap set with jade, golden straps crossing over the
	leg down to the leather soles.
	"""
	layer = Layer(JAGUAR_SET_PALETTE)
	layer.draw("leg", "ring", [
		"...." + "...." + "...." + "....",
		"...." + "...." + "...." + "....",
		"...." + "...." + "...." + "....",
		"...." + "...." + "...." + "....",
		"...." + "...." + "...." + "....",
		"SPPS" + "PSPP" + "SPPS" + "PPSP",
		"pPSp" + "PPSp" + "pSPp" + "SPPp",
		"gGgh" + "gGJg" + "gGgh" + "gGgh",
		"G..g" + ".GG." + "G..g" + ".gg.",
		".GG." + "G..G" + ".gg." + "G..g",
		"G..g" + "gGJg" + "G..g" + ".gg.",
		"lllk" + "LLlk" + "llkk" + "lkkk",
	])
	layer.draw("leg", "bottom", ["lllk", "Llkk", "llkk", "lkkk"])
	return layer.grid


def soul_worn_head():
	"""
	Helm of the soul pyre: blackened soul-steel, eyes and cracks glowing with souls, a soul gem at each ear, a crest of
	soul fire along the top and two bone horns rising from the temples (on the hat box).
	"""
	layer = Layer(dict(SOUL_SET_PALETTE, F="#5ff0ff", f="#1aa8c0", A="#7ff6ff"))
	front = ["MLLddLLM", "LMMdAMMD", "LMMMMMMD", "KAAKKAAK", "MDDKKDDM", "LMdKKdMD", "MDDKKDDN", "dNNbbNNd"]
	side = ["DMLLLLLM", "DMLMLLLM", "NDMMdMLL", "NDMdAdLL", "NDMMdMLM", "NDDMMMMM", "NNDDDDMD", "dNNNNNDd"]
	back = ["MLLddLLM", "MLMdAMLM", "DMMdAMMD", "DMLMdMMD", "DMMMdMMD", "DDMMdMDN", "NDDDdDNN", "dNNNNNNd"]
	layer.draw("head", "ring", [side[y] + front[y] + mirror(side)[y] + back[y] for y in range(8)])
	layer.draw("head", "top", ["DMLdAMMD", "MLLdAMMD", "MLLdAMMD", "MLLdAMMD", "MLLdAMMD", "MLLdAMMD", "DMMdAMMN", "dDDdADDd"])
	layer.draw("head", "bottom", ["NNNNNNNN"] * 8)
	horn = ["....bB..", "...bB...", "...b....", "........", "........", "........", "........", "........"]
	tip = ["...fF..."] + ["........"] * 7
	layer.draw("hat", "ring", [horn[y] + tip[y] + mirror(horn)[y] + mirror(tip)[y] for y in range(8)])
	layer.draw("hat", "top", ["........", "...Ff...", "...fF...", "...Ff...", "...fF...", "...Ff...", "...fF...", "........"])
	return layer.grid


def soul_worn_chest():
	"""
	Cuirass of the soul pyre: blackened soul-steel, a ribcage of bone over a chest of soul fire, a bone spine down the back,
	a belt with a soul gem; pauldrons rimmed with soul fire and set with a bone spike, dark sleeves and cuffs.
	"""
	layer = Layer(SOUL_SET_PALETTE)
	layer.draw("body", "front", ["dMMMMMMd", "LMMBbMMD", "LbBBbBbD", "LMAdbAMD", "LbBBbBbD", "LMAdbAMD", "LMbBbBMD", "LMMdAMMD",
								 "LMMMMMMD", "MMDDDDDN", "dDDAADDd", "MDDDDDDN"])
	layer.draw("body", "back", ["dMMMMMMd", "MLMBbMMD", "MLdbBdMD", "MLMBbMMD", "MLdbBdMD", "MLMBbMMD", "MLdbBdMD", "MMMBbMMD",
								"MLMbBMMD", "MMDDDDDN", "dDDddDDd", "MDDDDDDN"])
	sides = ["dMMd", "LMMD", "LMMD", "LMdD", "LMMD", "LMMD", "LdMD", "LMMD", "LMMD", "MDDN", "dDDd", "MDDN"]
	layer.draw("body", "right", sides).draw("body", "left", mirror(sides))
	layer.draw("body", "top", ["dMMMMMMd", "LMMMMMMD", "LMMMMMMD", "dDDDDDDd"])
	layer.draw("body", "bottom", ["NDDDDDDN"] * 4)
	layer.draw("arm", "ring", [
		"dAAd" + "dAAd" + "dAAd" + "dAAd",
		"LBbD" + "LMMD" + "MMDN" + "LMMD",
		"LbMD" + "LMAD" + "MMDN" + "LMMD",
		"LMMD" + "LMMD" + "MDDN" + "LMdD",
		"dDDd" + "dDDd" + "dDDd" + "dDDd",
		"MDDN" + "MMDN" + "DDNN" + "MDDN",
		"MDDN" + "MMDN" + "DDNN" + "MDDN",
		"MDdN" + "MMDN" + "DDNN" + "MDDN",
		"dAAd" + "dAAd" + "dAAd" + "dAAd",
		"...." + "...." + "...." + "....",
		"...." + "...." + "...." + "....",
		"...." + "...." + "...." + "....",
	])
	layer.draw("arm", "top", ["dAAd", "LBbD", "LbMD", "dDDd"])
	return layer.grid


def soul_worn_legs():
	"""
	Greaves of the soul pyre: a belt with a soul gem buckle and dark tassets at the waist, blackened soul-steel veined with
	soul fire down the legs, knee guards with a glowing core.
	"""
	layer = Layer(SOUL_SET_PALETTE)
	empty = ["........"] * 8
	layer.draw("body", "front", empty + ["dDDAADDd", "LMMddMMD", "LMMDDMMN", "MDDNNDDN"])
	layer.draw("body", "back", empty + ["dDDDDDDd", "LMMMMMMD", "LMMDDMMN", "MDDNNDDN"])
	sides = ["...."] * 8 + ["dDDd", "LMMD", "LMMD", "MDDN"]
	layer.draw("body", "right", sides).draw("body", "left", mirror(sides))
	layer.draw("leg", "ring", [
		"LMMD" + "LMMD" + "MDDN" + "LMMD",
		"LAMD" + "LMAD" + "MDDN" + "LMdD",
		"LMMD" + "LMMD" + "MDDN" + "LMMD",
		"dDDd" + "dDDd" + "dDDd" + "dDDd",
		"LMMD" + "dAAd" + "MDDN" + "LMMD",
		"dDDd" + "dDDd" + "dDDd" + "dDDd",
		"LMMD" + "LMMD" + "MDDN" + "LMMD",
		"LMdD" + "LAMD" + "MDDN" + "LdMD",
		"LMMD" + "LMMD" + "MDDN" + "LMMD",
		"MMDN" + "LMMD" + "DDNN" + "MMDN",
		"dDDd" + "dDDd" + "dDDd" + "dDDd",
		"...." + "...." + "...." + "....",
	])
	layer.draw("leg", "top", ["LMMD", "LMMD", "MMDN", "MDDN"])
	return layer.grid


def soul_worn_feet():
	"""
	Boots of the soul pyre: tongues of soul fire around the ankles above a glowing cuff, blackened soul-steel with cyan
	cracks, bone toe caps, dark soles.
	"""
	layer = Layer(dict(SOUL_SET_PALETTE, F="#5ff0ff", f="#1aa8c0", A="#7ff6ff"))
	layer.draw("leg", "ring", [
		"...." + "...." + "...." + "....",
		"...." + "...." + "...." + "....",
		"...." + "...." + "...." + "....",
		"...." + "...." + "...." + "....",
		".F.f" + "F.F." + ".f.F" + "f.F.",
		"fFfF" + "FfFf" + "fFfF" + "FfFf",
		"dAAd" + "dAAd" + "dAAd" + "dAAd",
		"LMMD" + "LMMD" + "MDDN" + "LMMD",
		"LAMD" + "LMAD" + "MDDN" + "LMdD",
		"LMMD" + "LMMD" + "MDDN" + "MMDN",
		"LMMD" + "BbbB" + "MDDN" + "MDDN",
		"NNNN" + "NBbN" + "NNNN" + "NNNN",
	])
	layer.draw("leg", "bottom", ["NNNN", "NNNN", "NNNN", "NNNN"])
	return layer.grid


WORN_ARMORS = {
	# name: (draw, slot, vanilla material of the base item)
	"couronne_du_roi_de_givre": (couronne_worn, "head", "diamond"),
	"plastron_du_serpent": (plastron_worn, "chest", "leather"),
	"bottes_de_zephyr": (zephyr_worn, "feet", "netherite"),
	"bottes_du_coursier": (coursier_worn, "feet", "leather"),
	"heaume_hiver_eternel": (frost_worn_head, "head", "diamond"),
	"cuirasse_hiver_eternel": (frost_worn_chest, "chest", "diamond"),
	"jambieres_hiver_eternel": (frost_worn_legs, "legs", "diamond"),
	"solerets_hiver_eternel": (frost_worn_feet, "feet", "diamond"),
	"masque_guerrier_jaguar": (jaguar_worn_head, "head", "gold"),
	"pectoral_guerrier_jaguar": (jaguar_worn_chest, "chest", "gold"),
	"jambieres_guerrier_jaguar": (jaguar_worn_legs, "legs", "gold"),
	"sandales_guerrier_jaguar": (jaguar_worn_feet, "feet", "gold"),
	"heaume_brasier_des_ames": (soul_worn_head, "head", "netherite"),
	"cuirasse_brasier_des_ames": (soul_worn_chest, "chest", "netherite"),
	"greves_brasier_des_ames": (soul_worn_legs, "legs", "netherite"),
	"bottes_brasier_des_ames": (soul_worn_feet, "feet", "netherite"),
}

EQUIPMENT_DIR = os.path.join(ASSETS, "equipment")
EQUIPMENT_TEXTURES = os.path.join(ASSETS, "textures", "entity", "equipment")


def equipment_layer(slot):
	return "humanoid_leggings" if slot == "legs" else "humanoid"


def worn_armor_assets():
	"""assets/guildy/equipment/<name>.json and its texture for every unique armor."""
	for name, (draw, slot, material) in WORN_ARMORS.items():
		layer = equipment_layer(slot)
		path = os.path.join(EQUIPMENT_TEXTURES, layer, name + ".png")
		write_png(path, 64, 32, draw())
		WRITTEN.add(os.path.normcase(path))
		layers = {layer: [{"texture": "guildy:" + name}]}
		if material == "leather":
			layers["humanoid_baby"] = [{"texture": "minecraft:leather", "dyeable": {"color_when_undyed": -6265536}},
									   {"texture": "minecraft:leather_overlay"}]
		else:
			layers["humanoid_baby"] = [{"texture": "minecraft:" + material}]
		data = {"layers": layers}
		if material in ("diamond", "netherite", "gold"):
			# Same trim palettes as the base armor (a trim of the armor's own material is drawn darker).
			data["trim_overrides"] = [{"palette": "minecraft:trim/%s_darker" % material, "when": {"material": "minecraft:" + material}}]
		write_json(os.path.join(EQUIPMENT_DIR, name + ".json"), data)


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
	# Guildy 1.12: war banner, scourge, permutation, rally, spectral chains, tailwind.
	"banniere_de_guerre": ([
		"##......",
		".######.",
		".#####..",
		".######.",
		".#......",
		".#......",
	], ["#ff5a3a", "#d8281e", "#ffc94a", "#d8281e"], "#c86bff"),
	"fleau": ([
		".#####..",
		"#######.",
		"#..#..#.",
		"###.###.",
		".#####..",
		".#.#.#..",
	], ["#c8ff6a", "#7ae030", "#3d9a18", "#7ae030"], "#c86bff"),
	"permutation": ([
		".....#..",
		"#######.",
		".....#..",
		"..#.....",
		".#######",
		"..#.....",
	], ["#d08bff", "#9a4ae0", "#2ac0b0", "#9a4ae0"], "#5aa0ff"),
	"ralliement": ([
		"...##...",
		"..####..",
		"..####..",
		".######.",
		"########",
		"...##...",
	], ["#ffd060", "#ff9a1a", "#3f8cff", "#ff9a1a"], "#c86bff"),
	"chaines_spectrales": ([
		".##.....",
		"#..#....",
		"#..###..",
		".###..#.",
		"...#..#.",
		"....##..",
	], ["#4fd0b8", "#2a9c88", "#1c5a6a", "#2a9c88"], "#ffb000"),
	"hate_du_vent": ([
		".....##.",
		"######.#",
		"........",
		"..####..",
		"#######.",
		"......#.",
	], ["#8a98ff", "#5c6cd8", "#3a46a0", "#5c6cd8"], "#5aa0ff"),
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


# ---------------------------------------------------------------------------------------------------------- Grand spectacle
# The fireworks show scroll of Guildy 1.14 (scrolls.json5 format 6). Scroll: a rocket rising from the lower left corner to
# a burst, its pixels cycling through the colours of a show (red, gold, green, blue). Grimoire and cursed grimoire pages:
# a burst above its rocket stick, glowing in brighter tones of the same colours (the dark covers). Added here so that the
# grimoire select lists it last.

SCROLL_RUNES["grand_spectacle"] = ([
	"...#.#.#",
	"....###.",
	"...##.##",
	"....###.",
	"..##.#.#",
	"##......",
], ["#e8283c", "#e0920c", "#24a044", "#2a62d8"], "#c86bff")
GRIMOIRE_SIGILS["grand_spectacle"] = [".#.#.#.", "..###..", ".##.##.", "..###..", ".#.#.#.", "...#...", "...#..."]
GRIMOIRE_GLOW["grand_spectacle"] = ["#ff5a6a", "#ffd24a", "#6aff8a", "#5ab8ff"]


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
	sprites.update(ruin_unique_sprites())
	sprites.update(set_piece_sprites())
	for name, (rune, palette, seal) in SCROLL_RUNES.items():
		sprites["parchemin_" + name] = scroll(rune, palette, seal)
	sprites["parchemin_incantation"] = parchemin_incantation()
	sprites["parchemin_incantation_maudit"] = parchemin_incantation_maudit()
	sprites["grimoire"] = grimoire()
	for spell in GRIMOIRE_SIGILS:
		sprites["grimoire_" + spell] = grimoire(spell)
	sprites.update(grimoire_maudit_sprites())
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
	weapons = [n for n in sprites if not n.startswith(("parchemin_", "grimoire")) and n not in SET_PIECES]
	return [("armes uniques", weapons),
			("panoplies (armures uniques par ensembles de 4)", [n for n in sprites if n in SET_PIECES]),
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


# ---------------------------------------------------------------------------------------------------------- 3D previews
# tools/previews/armor_worn.png: the worn armors on a plain player figure (front, side, back, three-quarter views) and the
# 3D shield, drawn by a tiny orthographic rasterizer (z-buffer, nearest texel, cutout transparency, no back-face culling
# for the armor layers, like Minecraft's armor render type). Model space is Minecraft's entity model space: y down, the
# figure faces -z, x = the figure's left.

import math


class Quad:
	"""A textured parallelogram: corner origin + edges u (texture x) and v (texture y), outward normal, texture rect;
	cull = drawn only from the front (otherwise double-sided)."""

	def __init__(self, origin, u, v, normal, texture, rect, cull):
		self.origin, self.u, self.v, self.normal = origin, u, v, normal
		self.texture, self.rect, self.cull = texture, rect, cull


def box_quads(texture, uv, start, size, inflate=0.0, mirror=False, offset=(0.0, 0.0, 0.0), cull=False, dims=None):
	"""The six faces of a model box (ModelPart.Cube): UV layout from texOffs uv and the box size, mirrored like the left
	limbs. dims: texture size of the faces when it differs from the box size (block-model elements)."""
	w, h, d = size
	x0, y0, z0 = (start[i] - inflate + offset[i] for i in range(3))
	x1, y1, z1 = (start[i] + size[i] + inflate + offset[i] for i in range(3))
	if mirror:
		x0, x1 = x1, x0
	u, v = uv
	tw, th, td = dims or size
	side = -1 if mirror else 1
	faces = [
		((x0, y0, z0), (x1 - x0, 0, 0), (0, y1 - y0, 0), (0, 0, -1), (u + td, v + td, tw, th)),  # front (north)
		((x1, y0, z1), (x0 - x1, 0, 0), (0, y1 - y0, 0), (0, 0, 1), (u + td + tw + td, v + td, tw, th)),  # back (south)
		((x0, y0, z1), (0, 0, z0 - z1), (0, y1 - y0, 0), (-side, 0, 0), (u, v + td, td, th)),  # right side (west)
		((x1, y0, z0), (0, 0, z1 - z0), (0, y1 - y0, 0), (side, 0, 0), (u + td + tw, v + td, td, th)),  # left side (east)
		((x0, y0, z1), (x1 - x0, 0, 0), (0, 0, z0 - z1), (0, -1, 0), (u + td, v, tw, td)),  # top
		((x0, y1, z1), (x1 - x0, 0, 0), (0, 0, z0 - z1), (0, 1, 0), (u + td + tw, v, tw, td)),  # bottom
	]
	return [Quad(o, eu, ev, n, texture, rect, cull) for o, eu, ev, n, rect in faces]


def element_quads(texture, elements, texel=1.0):
	"""Faces of block-model elements (y up, rects in texture pixels), converted to model space (y down)."""
	quads = []
	for _, start, end, faces in elements:
		x0, y0, z0 = start
		x1, y1, z1 = end
		# Block model face orientation: (corner seen at the texture's top-left, edge to the right, edge downward).
		geometry = {
			"south": ((x0, y1, z1), (x1 - x0, 0, 0), (0, y0 - y1, 0), (0, 0, 1)),
			"north": ((x1, y1, z0), (x0 - x1, 0, 0), (0, y0 - y1, 0), (0, 0, -1)),
			"west": ((x0, y1, z0), (0, 0, z1 - z0), (0, y0 - y1, 0), (-1, 0, 0)),
			"east": ((x1, y1, z1), (0, 0, z0 - z1), (0, y0 - y1, 0), (1, 0, 0)),
			"up": ((x0, y1, z0), (x1 - x0, 0, 0), (0, 0, z1 - z0), (0, 1, 0)),
			"down": ((x0, y0, z1), (x1 - x0, 0, 0), (0, 0, z0 - z1), (0, -1, 0)),
		}
		for side, rect in faces.items():
			(ox, oy, oz), eu, ev, n = geometry[side]
			quads.append(Quad((ox * texel, -oy * texel, oz * texel), tuple(c * texel for c in (eu[0], -eu[1], eu[2])),
							  tuple(c * texel for c in (ev[0], -ev[1], ev[2])), (n[0], -n[1], n[2]), texture, rect, True))
	return quads


def rotate(p, yaw, pitch):
	"""Model point -> camera space (x right, y down, z away from the viewer)."""
	ca, sa = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
	x, y, z = p[0] * ca + p[2] * sa, p[1], -p[0] * sa + p[2] * ca
	cb, sb = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
	return x, y * cb - z * sb, y * sb + z * cb


def render_quads(img, quads, yaw, pitch, scale, cx, cy, clip=None, light=(-0.45, -1.0, -0.7)):
	"""Rasterizes the quads into img (z-buffer); (cx, cy) = screen position of the model origin, clip = (x0, y0, x1, y1)."""
	cx0, cy0, cx1, cy1 = clip or (0, 0, img.width, img.height)
	norm = math.sqrt(sum(c * c for c in light))
	light = tuple(c / norm for c in light)
	depth = {}
	for index, q in enumerate(quads):
		# Coplanar faces (shoulder pads over the chest, as in game): the first one drawn wins, without z-fighting noise.
		bias = index * 1e-6
		o = rotate(q.origin, yaw, pitch)
		pu = rotate((q.origin[0] + q.u[0], q.origin[1] + q.u[1], q.origin[2] + q.u[2]), yaw, pitch)
		pv = rotate((q.origin[0] + q.v[0], q.origin[1] + q.v[1], q.origin[2] + q.v[2]), yaw, pitch)
		eu = tuple(a - b for a, b in zip(pu, o))
		ev = tuple(a - b for a, b in zip(pv, o))
		normal = rotate(q.normal, yaw, pitch)
		facing = normal[2] < 0
		if q.cull and not facing:
			continue
		if not facing:
			normal = tuple(-c for c in normal)
		bright = 0.62 + 0.38 * max(0.0, sum(a * b for a, b in zip(normal, light)))
		ax, ay = eu[0] * scale, eu[1] * scale
		bx, by = ev[0] * scale, ev[1] * scale
		det = ax * by - ay * bx
		if abs(det) < 1e-6:
			continue
		sx0, sy0 = cx + o[0] * scale, cy + o[1] * scale
		xs = [sx0, sx0 + ax, sx0 + bx, sx0 + ax + bx]
		ys = [sy0, sy0 + ay, sy0 + by, sy0 + ay + by]
		tx, ty, tw, th = q.rect
		tex = q.texture
		for py in range(max(cy0, int(min(ys))), min(cy1, int(max(ys)) + 1)):
			row = img.rows[py]
			for px in range(max(cx0, int(min(xs))), min(cx1, int(max(xs)) + 1)):
				dx, dy = px + 0.5 - sx0, py + 0.5 - sy0
				s = (dx * by - dy * bx) / det
				t = (ax * dy - ay * dx) / det
				if not (0.0 <= s < 1.0 and 0.0 <= t < 1.0):
					continue
				texel = tex[ty + min(th - 1, int(t * th))][tx + min(tw - 1, int(s * tw))]
				if texel[3] == 0:
					continue
				z = o[2] + s * eu[2] + t * ev[2] + bias
				key = (px, py)
				if key in depth and depth[key] <= z:
					continue
				depth[key] = z
				row[px] = (int(texel[0] * bright), int(texel[1] * bright), int(texel[2] * bright), 255)


def figure_skin():
	"""64 x 32 texture of the plain preview figure (skin, hair, grey shirt, dark trousers, shoes)."""
	pal = {"s": "#c99a78", "S": "#b0846a", "h": "#4b3424", "H": "#3a2618", "w": "#f0f0f0", "e": "#2a3a6a", "m": "#9a6a58",
		   "c": "#7a8090", "C": "#666b7a", "p": "#3c4250", "P": "#30353f", "b": "#26262b", "x": "#58606e"}
	layer = Layer(pal)
	layer.draw("head", "top", ["hhhhhhhh"] * 8)
	layer.draw("head", "bottom", ["SSSSSSSS"] * 8)
	layer.draw("head", "ring", [
		"hhhhhhhh" + "hhhhhhhh" + "hhhhhhhh" + "hhhhhhhh",
		"hhhhhhhh" + "hhhhhhhh" + "hhhhhhhh" + "hhhhhhhh",
		"hhhhhhss" + "hssssssh" + "sshhhhhh" + "hhhhhhhh",
		"hhhhhsss" + "ssssssss" + "ssshhhhh" + "hhhhhhhh",
		"hhhhssss" + "swessews" + "sssshhhh" + "HhhhhhhH",
		"hhhsssss" + "ssssssss" + "ssssshhh" + "HHhhhhHH",
		"hhssssss" + "sssmmsss" + "sssssshh" + "SHHHHHHS",
		"SSssssss" + "Ssssssss" + "ssssssSS" + "SSSSSSSS",
	])
	layer.draw("body", "top", ["cccccccc"] * 4)
	layer.draw("body", "bottom", ["pppppppp"] * 4)
	layer.draw("body", "ring", ["cccc" + "ccxxxxcc" + "cccc" + "cccccccc"] + ["cccc" + "cccxxccc" + "cccc" + "cccccccc"]
			   + ["cccc" + "cccccccc" + "cccc" + "cccccccc"] * 7 + ["CCCC" + "CCCCCCCC" + "CCCC" + "CCCCCCCC"]
			   + ["pppp" + "pppPPppp" + "pppp" + "pppppppp"] * 2)
	layer.draw("arm", "top", ["cccc"] * 4)
	layer.draw("arm", "bottom", ["ssss"] * 4)
	layer.draw("arm", "ring", ["cccC" * 4] * 8 + ["CCCC" * 4] + ["sssS" * 4] * 3)
	layer.draw("leg", "top", ["pppp"] * 4)
	layer.draw("leg", "bottom", ["bbbb"] * 4)
	layer.draw("leg", "ring", ["pppP" * 4] * 10 + ["bbbb" * 4] * 2)
	return layer.grid


# Player model (wide arms): part -> (texOffs, box start, box size, pivot, mirrored).
FIGURE = {
	"head": ((0, 0), (-4, -8, -4), (8, 8, 8), (0, 0, 0), False),
	"body": ((16, 16), (-4, 0, -2), (8, 12, 4), (0, 0, 0), False),
	"right_arm": ((40, 16), (-3, -2, -2), (4, 12, 4), (-5, 2, 0), False),
	"left_arm": ((40, 16), (-1, -2, -2), (4, 12, 4), (5, 2, 0), True),
	"right_leg": ((0, 16), (-2, 0, -2), (4, 12, 4), (-1.9, 12, 0), False),
	"left_leg": ((0, 16), (-2, 0, -2), (4, 12, 4), (1.9, 12, 0), True),
}
# Armor model of each slot: parts and inflation (HumanoidModel.createArmorMeshSet: helmet/chestplate 1, leggings 0.5 for
# the body and 0.4 for the legs, boots 0.9).
ARMOR_PARTS = {"head": (("head", 1.0),), "chest": (("body", 1.0), ("right_arm", 1.0), ("left_arm", 1.0)),
			   "legs": (("body", 0.5), ("right_leg", 0.4), ("left_leg", 0.4)), "feet": (("right_leg", 0.9), ("left_leg", 0.9))}


def figure_quads(skin, armors):
	"""The figure and its worn armor layers (armors: [(texture, slot)])."""
	quads = []
	for uv, start, size, pivot, mirror in FIGURE.values():
		quads += box_quads(skin, uv, start, size, 0.0, mirror, pivot, cull=True)
	for texture, slot in armors:
		for part, inflate in ARMOR_PARTS[slot]:
			uv, start, size, pivot, mirror = FIGURE[part]
			quads += box_quads(texture, uv, start, size, inflate, mirror, pivot)
			if part == "head":
				quads += box_quads(texture, (32, 0), start, size, inflate + 0.5, mirror, pivot)
	return quads


VIEWS = [("face", 0, 8), ("profil", -90, 8), ("dos", 180, 8), ("3/4", -35, 18)]
# Rows of the armor sets in the preview: title, suffix of the ids of their pieces.
SET_PREVIEWS = [("panoplie hiver eternel (4 pieces)", "_hiver_eternel"), ("panoplie guerrier jaguar (4 pieces)", "_guerrier_jaguar"),
				("panoplie brasier des ames (4 pieces)", "_brasier_des_ames")]
ZOOMS = [("zoom 3/4 avant", -35, 18), ("zoom 3/4 arriere", 145, 18)]
# Close-up of each slot: model y of the center of the piece, scale (pixels per model unit).
FOCUS = {"head": (-4.5, 14), "chest": (5, 9), "feet": (17.5, 14), "all": (7, 6)}


def checker_rect(img, x, y, w, h, cell):
	for yy in range(0, h, cell):
		for xx in range(0, w, cell):
			color = (139, 139, 139, 255) if (xx // cell + yy // cell) % 2 else (124, 124, 124, 255)
			img.rect(x + xx, y + yy, min(cell, w - xx), min(cell, h - yy), color)


def texture_panel(img, x, y, grid, scale):
	checker_rect(img, x, y, len(grid[0]) * scale, len(grid) * scale, scale * 2)
	for ty, line in enumerate(grid):
		for tx, px in enumerate(line):
			if px[3]:
				img.rect(x + tx * scale, y + ty * scale, scale, scale, px[:3] + (255,))


def armor_preview(path):
	"""tools/previews/armor_worn.png: per unique armor outside the sets the figure wearing it (front, profile, back,
	three-quarter), two close-ups and its 64 x 32 layer (x4), then all of them together; each armor set worn complete with
	its four layers (x2); then the 3D shield (front, back, profile, three-quarter, close-up) and its texture (x5)."""
	skin = figure_skin()
	layers = {name: (draw(), slot) for name, (draw, slot, _) in WORN_ARMORS.items()}
	single = [name for name in layers if name not in SET_PIECES]
	rows = [(name, [layers[name]], layers[name][1]) for name in single]
	rows.append(("armures uniques ensemble", [layers[name] for name in single], "all"))
	for title, suffix in SET_PREVIEWS:
		rows.append((title, [layers[name] for name in SET_PIECES if name.endswith(suffix)], "set"))
	cell_w, cell_h, zoom_w = 150, 236, 236
	width = 12 + len(VIEWS) * cell_w + len(ZOOMS) * (zoom_w + 8) + 64 * 4 + 12
	height = 12 + (len(rows) + 1) * (cell_h + 26) + 12
	img = Image(width, height, (34, 34, 42, 255))
	panel = (52, 54, 66, 255)
	label = (190, 190, 200, 255)
	y = 12
	for title, armors, slot in rows:
		img.text(12, y, title, 2, (255, 214, 120, 255))
		y += 18
		quads = figure_quads(skin, armors)
		x = 12
		for name, yaw, pitch in VIEWS:
			img.rect(x, y, cell_w - 8, cell_h, panel)
			render_quads(img, quads, yaw, pitch, 6, x + (cell_w - 8) // 2, y + 26 + 6 * 9, (x, y, x + cell_w - 8, y + cell_h))
			img.text(x + 4, y + 4, name, 2, label)
			x += cell_w
		focus_y, scale = FOCUS["all" if slot == "set" else slot]
		for name, yaw, pitch in ZOOMS:
			img.rect(x, y, zoom_w, cell_h, panel)
			render_quads(img, quads, yaw, pitch, scale, x + zoom_w // 2, y + cell_h // 2 - int(focus_y * scale),
						 (x, y, x + zoom_w, y + cell_h))
			img.text(x + 4, y + 4, name, 2, label)
			x += zoom_w + 8
		if len(armors) == 1:
			texture_panel(img, x, y, armors[0][0], 4)
		elif slot == "set":
			# The four layers of the set: helmet, chestplate, leggings (humanoid_leggings), boots.
			for i, (texture, _) in enumerate(armors):
				texture_panel(img, x + (i % 2) * 132, y + (i // 2) * 70, texture, 2)
		y += cell_h + 8
	img.text(12, y, "egide des ames en main (modele 3d)", 2, (255, 214, 120, 255))
	y += 18
	sprite = egide_des_ames_in_hand()
	quads = element_quads(sprite.frames[0], shield_elements())
	x = 12
	# Element space: the plate front faces +z, turned toward the viewer (yaw + 180).
	for name, yaw, pitch, scale, w in [("face", 0, 8, 7, cell_w), ("dos", 180, 8, 7, cell_w), ("profil", -90, 8, 7, cell_w),
									   ("3/4", -40, 20, 7, cell_w), ("zoom 3/4 avant", -35, 18, 9, zoom_w + 8),
									   ("zoom 3/4 arriere", 145, 18, 9, zoom_w + 8)]:
		box_w = w - 8
		img.rect(x, y, box_w, cell_h, panel)
		render_quads(img, quads, yaw + 180, pitch, scale, x + box_w // 2, y + cell_h // 2 + 8, (x, y, x + box_w, y + cell_h))
		img.text(x + 4, y + 4, name, 2, label)
		x += w
	texture_panel(img, x, y, sprite.frames[0], 5)
	img.save(path)


def main():
	sprites = all_sprites()
	for name, sprite in sprites.items():
		write_sprite(name, sprite)
	for name, (_, parent) in WEAPONS.items():
		simple_model(name, parent)
	bow_model("arc_du_phenix")
	crossbow_model("arbalete_du_traqueur")
	ruin_unique_models()
	for name, (_, parent) in SET_PIECES.items():
		simple_model(name, parent)
	for name in SCROLL_RUNES:
		simple_model("parchemin_" + name, "minecraft:item/generated")
	simple_model("parchemin_incantation", "minecraft:item/generated")
	simple_model("parchemin_incantation_maudit", "minecraft:item/generated")
	grimoire_model()
	grimoire_maudit_model()
	worn_armor_assets()
	remove_stale()
	pack_icon(sprites)
	preview_sheet(os.path.join(PREVIEWS, "textures.png"), sprites)
	animated_preview(os.path.join(PREVIEWS, "textures_animated.png"), sprites)
	ruin_unique_preview(os.path.join(PREVIEWS, "textures_in_hand.png"))
	armor_preview(os.path.join(PREVIEWS, "armor_worn.png"))
	print("textures, models, item definitions and equipment assets written to", ASSETS)


if __name__ == "__main__":
	main()
