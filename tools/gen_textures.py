#!/usr/bin/env python3
"""
Guildy - generator of the optional resource pack of the unique weapons and spell scrolls.

Draws ORIGINAL 16x16 pixel art (simple vector shapes: lines, polylines, polygons, discs; automatic shading and
outline; palette cycling for the animated ones) and writes, under src/main/resources/assets/guildy/:
  textures/item/<model>.png (+ .png.mcmeta when animated), models/item/<model>.json, items/<model>.json
and tools/resourcepack/pack.png (pack icon). No Minecraft texture is used or copied: models only reference vanilla
*parent models* (item/handheld, item/bow...) for their in-hand transforms.

Usage (from the project root):  python tools/gen_textures.py [--preview preview.png]
Pure standard library (zlib + struct for the PNG files).
"""
import json
import os
import struct
import sys
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


# ---------------------------------------------------------------------------------------------------------- canvas

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

	def line(self, x0, y0, x1, y1, part, width=1):
		dx, dy = abs(x1 - x0), -abs(y1 - y0)
		sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
		err = dx + dy
		x, y = x0, y0
		while True:
			self.dot(x, y, part, width)
			if x == x1 and y == y1:
				break
			e2 = 2 * err
			if e2 >= dy:
				err += dy
				x += sx
			if e2 <= dx:
				err += dx
				y += sy

	def dot(self, x, y, part, width):
		self.put(x, y, part)
		if width >= 2:
			self.put(x - 1, y, part)
		if width >= 3:
			self.put(x, y + 1, part)
		if width >= 4:
			self.put(x - 1, y + 1, part)

	def polyline(self, points, part, width=1):
		for (x0, y0), (x1, y1) in zip(points, points[1:]):
			self.line(x0, y0, x1, y1, part, width)

	def polygon(self, points, part):
		"""Scanline fill (pixel centers) + outline of the polygon edges."""
		for y in range(SIZE):
			cy = y + 0.5
			xs = []
			for (x0, y0), (x1, y1) in zip(points, points[1:] + points[:1]):
				if (y0 <= cy < y1) or (y1 <= cy < y0):
					xs.append(x0 + (cy - y0) * (x1 - x0) / (y1 - y0))
			xs.sort()
			for a, b in zip(xs[::2], xs[1::2]):
				for x in range(SIZE):
					if a <= x + 0.5 <= b:
						self.put(x, y, part)
		self.polyline(points + points[:1], part)

	def disc(self, cx, cy, r, part):
		for y in range(SIZE):
			for x in range(SIZE):
				if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
					self.put(x, y, part)

	def pixels(self, points, part):
		for x, y in points:
			self.put(x, y, part)

	def ascii(self, lines, mapping):
		"""Hand-drawn layer: one string per row, mapping char -> part ('.' and unknown chars = untouched)."""
		for y, line in enumerate(lines):
			for x, ch in enumerate(line):
				if ch in mapping:
					self.put(x, y, mapping[ch])


# ---------------------------------------------------------------------------------------------------------- render

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


# ---------------------------------------------------------------------------------------------------------- sprites

def sword(blade, guard, handle, pommel, accent=None, length=9, curve=0, spikes=False, short=False):
	"""Diagonal sword, tip in the upper right corner. blade/guard/handle/pommel: style tuples."""
	c = Canvas()
	base = 5 if not short else 6
	tip = 14 if not short else 12
	# handle and pommel along the diagonal
	c.line(base - 3, 15 - (base - 3), base - 1, 15 - (base - 1), "handle", 1)
	c.put(base - 4, 15 - (base - 4), "pommel")
	c.put(base - 5, 15 - (base - 5), "pommel")
	# blade: 2 px band from the guard to the tip, optionally curved toward the lower right
	points = []
	for a in range(base, tip + 1):
		bend = int(round(curve * ((a - base) / max(1, tip - base)) ** 2))
		points.append((a + bend, 15 - a + bend))
	c.polyline(points, "blade", 2)
	if spikes:
		for i, (x, y) in enumerate(points[1:-2]):
			if i % 3 == 0:
				c.put(x - 2, y - 1, "spike")
				c.put(x - 2, y, "spike")
	# guard across the blade
	gx, gy = base - 1, 15 - (base - 1)
	for k in range(-2, 3):
		c.put(gx + k, gy + k, "guard")
	c.put(gx + 1, gy, "guard")
	c.put(gx, gy - 1, "guard")
	if accent:
		for (x, y), part in accent:
			c.put(x, y, part)
	styles = {"blade": blade, "guard": guard, "handle": handle, "pommel": pommel}
	return c, styles


def write_sprite(name, frames_rows, frametime=3):
	path = os.path.join(ASSETS, "textures", "item", name + ".png")
	frames = len(frames_rows)
	rows = [row for frame in frames_rows for row in frame]
	write_png(path, SIZE, SIZE * frames, rows)
	meta = path + ".mcmeta"
	if frames > 1:
		with open(meta, "w", encoding="utf-8", newline="\n") as f:
			json.dump({"animation": {"frametime": frametime, "interpolate": False}}, f, indent=2)
			f.write("\n")
	elif os.path.exists(meta):
		os.remove(meta)
	return frames_rows[0]


def write_json(path, data):
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, "w", encoding="utf-8", newline="\n") as f:
		json.dump(data, f, indent=2)
		f.write("\n")


def simple_model(name, parent="minecraft:item/handheld"):
	write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
			   {"parent": parent, "textures": {"layer0": "guildy:item/" + name}})
	write_json(os.path.join(ASSETS, "items", name + ".json"),
			   {"model": {"type": "minecraft:model", "model": "guildy:item/" + name}})


def bow_model(name):
	for suffix in ("", "_pulling_0", "_pulling_1", "_pulling_2"):
		write_json(os.path.join(ASSETS, "models", "item", name + suffix + ".json"),
				   {"parent": "minecraft:item/bow", "textures": {"layer0": "guildy:item/" + name + suffix}})
	write_json(os.path.join(ASSETS, "items", name + ".json"), {
		"model": {
			"type": "minecraft:condition",
			"property": "minecraft:using_item",
			"on_false": {"type": "minecraft:model", "model": "guildy:item/" + name},
			"on_true": {
				"type": "minecraft:range_dispatch",
				"property": "minecraft:use_duration",
				"scale": 0.05,
				"entries": [
					{"threshold": 0.65, "model": {"type": "minecraft:model", "model": "guildy:item/" + name + "_pulling_1"}},
					{"threshold": 0.9, "model": {"type": "minecraft:model", "model": "guildy:item/" + name + "_pulling_2"}},
				],
				"fallback": {"type": "minecraft:model", "model": "guildy:item/" + name + "_pulling_0"},
			},
		}
	})


# ---------------------------------------------------------------------------------------------------------- items

OUTLINE = "#14121c"
WOOD = ("#8a5a34", "#6b4428", "#4a2e1a")
DARK_WOOD = ("#5c3d2a", "#43291b", "#2b1a10")
GOLD = ("#ffe58a", "#e8b434", "#9c6b12")


def foudrelame():
	spark = ["#ffffff", "#fff27a", "#ffd23a", "#7fdbff"]
	c, styles = sword(blade=("#e9f6ff", "#9fb3cf", "#4d5a78"), guard=GOLD, handle=("#7a5c96", "#4f3a66", "#2e2140"),
					  pommel=flat("#7fdbff"))
	# zigzag lightning along the blade (animated, traveling)
	bolt = [(6, 9), (7, 8), (7, 7), (8, 7), (9, 6), (9, 5), (10, 5), (11, 4), (11, 3), (12, 3), (13, 2)]
	for i, (x, y) in enumerate(bolt):
		part = "bolt%d" % (i % 4)
		c.put(x, y, part)
		styles[part] = flat(cycle(spark, i % 4))
	return render(c, styles, OUTLINE, frames=4)


def croc_de_givre():
	shine = ["#ffffff", "#d6f6ff", "#a8e6ff"]
	c, styles = sword(blade=("#e6fbff", "#9ddff5", "#3f8fb5"), guard=("#e8f1f6", "#9fb8c8", "#5d7484"),
					  handle=("#4f7fa6", "#2f5577", "#1b334a"), pommel=flat("#c8f4ff"), spikes=True)
	styles["spike"] = ("#ffffff", "#c8f4ff", "#7cc7e6")
	for i, (x, y) in enumerate([(8, 7), (10, 5), (12, 3)]):
		part = "shine%d" % i
		c.put(x, y, part)
		styles[part] = flat(cycle(shine, i))
	return render(c, styles, "#0c2433", frames=3)


def murmure_du_neant():
	pulse = ["#ff7cf5", "#e056fd", "#a33fd6", "#6a2aa0"]
	c, styles = sword(blade=("#8c5bc4", "#4c2a7a", "#22113a"), guard=("#5a5a66", "#34343d", "#1c1c22"),
					  handle=("#6a3d82", "#47285a", "#2a1736"), pommel=flat("#e056fd"), short=True)
	for i, (x, y) in enumerate([(7, 8), (8, 7), (9, 6), (10, 5), (11, 4), (12, 3)]):
		part = "void%d" % i
		c.put(x, y, part)
		styles[part] = flat(cycle(pulse, i % 4))
	return render(c, styles, "#09040f", frames=4)


def coutelas_du_contrebandier():
	c, styles = sword(blade=("#f1f4f6", "#b4bec7", "#6b7682"), guard=("#f3d77e", "#d4a537", "#8a6614"),
					  handle=("#8c5a34", "#6b4226", "#43281a"), pommel=flat("#d4a537"), curve=2)
	return render(c, styles, "#1f1d22")


def hache_sylvestre():
	c = Canvas()
	c.line(1, 14, 12, 3, "handle")
	c.ascii([
		"...ee...........",
		"..ehhh..........",
		"..ehhhh.........",
		"..ehlhhhh.......",
		"...ehhlhhhh.....",
		"....eehhhh......",
		"......ehh.......",
		".......e........",
	], {"h": "head", "e": "edge", "l": "leaf"})
	styles = {"handle": WOOD, "head": ("#9cc46a", "#5f8f3a", "#36561f"), "edge": ("#f2ffe0", "#d7f0b4", "#a9cf7a"),
			  "leaf": flat("#d8ff8f")}
	return render(c, styles, OUTLINE)


def pioche_du_titan():
	c = Canvas()
	c.line(1, 14, 9, 6, "handle")
	c.polyline([(2, 4), (4, 2), (7, 1), (10, 2), (12, 3), (13, 5), (14, 8), (13, 11)], "head", 2)
	c.pixels([(9, 2), (12, 5)], "rivet")
	styles = {"handle": DARK_WOOD, "head": ("#e2e4ee", "#8d90a3", "#4a4c5c"), "rivet": flat("#f1c24a")}
	return render(c, styles, OUTLINE)


def pic_du_filon():
	c = Canvas()
	c.line(1, 14, 9, 6, "handle")
	c.polyline([(2, 4), (4, 2), (7, 1), (10, 2), (12, 3), (13, 5), (14, 8), (13, 11)], "head", 2)
	c.pixels([(6, 2), (11, 3), (13, 7)], "vein")
	styles = {"handle": WOOD, "head": ("#b8fff0", "#4fd1bd", "#1f7f72"), "vein": flat("#ff9a3c")}
	return render(c, styles, OUTLINE)


def marteau_du_seisme():
	c = Canvas()
	c.line(1, 14, 8, 7, "handle", 2)
	c.ascii([
		"..........s.....",
		"........s.s.s...",
		".........hhh....",
		"......shhhhhhs..",
		".......hhchhh...",
		"......shhcchhs..",
		"........hhhch...",
		".......bhhhhhs..",
		"......bb.s.s....",
		"..........s.....",
	], {"h": "head", "s": "spike", "c": "crack", "b": "band"})
	styles = {"handle": ("#7b7b86", "#4d4d57", "#26262b"), "head": ("#d29a5a", "#9a6330", "#5a3818"),
			  "spike": ("#f0f0f6", "#a9a9b6", "#5e5e6a"), "band": GOLD,
			  "crack": flat(["#ff7a1a", "#ffb347", "#ffe08a", "#ffb347"])}
	return render(c, styles, OUTLINE, frames=4)


def trident_du_leviathan():
	glow = ["#ffffff", "#9ff7ff", "#3fd6e6", "#9ff7ff"]
	c = Canvas()
	c.line(1, 14, 10, 5, "shaft")
	c.line(8, 3, 12, 7, "cross")
	c.line(9, 4, 14, 1, "prong")
	c.line(8, 3, 9, 0, "prong")
	c.line(12, 7, 15, 6, "prong")
	c.pixels([(4, 11), (5, 10)], "ring")
	styles = {"shaft": ("#3f8f8f", "#2e6b6b", "#1b4040"), "cross": ("#f3d58a", "#e9c46a", "#a6801f"),
			  "prong": ("#d9fffb", "#66e0d0", "#2a9d8f"), "ring": GOLD}
	for i, (x, y) in enumerate([(14, 1), (9, 0), (15, 6), (11, 3)]):
		part = "glow%d" % i
		c.put(x, y, part)
		styles[part] = flat(cycle(glow, i))
	return render(c, styles, "#0b2326", frames=4)


def faux_des_ames():
	soul = ["#b8fbff", "#5ce1e6", "#2fb3c4", "#5ce1e6"]
	c = Canvas()
	c.line(2, 14, 12, 4, "handle")
	c.polyline([(12, 3), (10, 1), (6, 1), (3, 2), (1, 4)], "blade", 2)
	c.pixels([(13, 2), (12, 2)], "collar")
	styles = {"handle": ("#5a4636", "#3a2c25", "#221912"), "blade": ("#7a7f9a", "#3f4359", "#1f2130"),
			  "collar": GOLD}
	for i, (x, y) in enumerate([(9, 2), (7, 2), (5, 3), (3, 3), (2, 4)]):
		part = "soul%d" % i
		c.put(x, y, part)
		styles[part] = flat(cycle(soul, i % 4))
	return render(c, styles, "#0a0b12", frames=4)


def bottes(upper, sole, trim, wing=None):
	c = Canvas()
	# a pair of boots, side view, toes to the left (the back one is drawn first)
	rows = [
		"................",
		"................",
		".........tttt...",
		".........uuuu...",
		"..tttt...uuuu...",
		"..uuuu...uuuu...",
		"..uuuu...uuuu...",
		"..uuuu..uuuuu...",
		"..uuuu.uuuuuu...",
		"..uuuu.ssssss...",
		".uuuuu..........",
		"uuuuuu..........",
		"uuuuuu..........",
		"ssssss..........",
	]
	c.ascii(rows, {"u": "upper", "s": "sole", "t": "trim"})
	if wing:
		c.ascii([
			"..............w.",
			".............ww.",
			".............w..",
			"................",
			".......w........",
			"......ww........",
			"......w.........",
		], {"w": "wing"})
	styles = {"upper": upper, "sole": sole, "trim": trim}
	if wing:
		styles["wing"] = wing
	return c, styles


def bottes_de_zephyr():
	c, styles = bottes(("#ffffff", "#cdeeff", "#7fb9de"), ("#e9c46a", "#c9a23a", "#8a6a14"), flat("#ffe58a"),
					   wing=("#ffffff", "#f2fbff", "#bfe9ff"))
	return render(c, styles, "#16324a")


def bottes_du_coursier():
	c, styles = bottes(("#5aa86a", "#3a7d44", "#1e4a26"), ("#7a5236", "#5a3a22", "#3a2414"), flat("#e8c050"))
	return render(c, styles, "#132015")


def arc(pull):
	"""
	Arc du Phénix: limb bowed toward the upper right between its tips (2, 2) and (13, 13), the string between the tips;
	pull 1..3: the string drawn toward the lower left with an arrow aimed at the upper right.
	"""
	flame = ["#ffe066", "#ffb347", "#ff6a3d", "#ffb347"]
	c = Canvas()
	tip_a, tip_b = (2, 2), (13, 13)
	if pull == 0:
		c.line(tip_a[0] + 1, tip_a[1] + 1, tip_b[0] - 1, tip_b[1] - 1, "string")
	else:
		px, py = [(6, 9), (5, 10), (4, 11)][pull - 1]
		c.line(tip_a[0] + 1, tip_a[1] + 1, px, py, "string")
		c.line(px, py, tip_b[0] - 1, tip_b[1] - 1, "string")
		c.line(px + 1, py - 1, px + 5 + pull, py - 5 - pull, "arrow")
		c.put(px + 5 + pull, py - 5 - pull, "arrowhead")
		c.put(px + 6 + pull, py - 6 - pull, "arrowhead")
	c.polyline([tip_a, (5, 1), (8, 1), (11, 2), (13, 4), (14, 7), (14, 10), tip_b], "limb", 2)
	styles = {"limb": ("#ff8a4a", "#b8401c", "#5c1a0c"), "string": flat("#f5e6c8"), "arrow": flat("#c8c8c8"),
			  "arrowhead": flat("#ffb347")}
	for i, (x, y) in enumerate([(5, 0), (12, 2), (15, 7), (14, 12)]):
		part = "flame%d" % i
		c.put(x, y, part)
		styles[part] = flat(cycle(flame, i))
	return render(c, styles, "#24100a", frames=4 if pull == 0 else 1)


SPRITES = {
	"foudrelame": (foudrelame, "minecraft:item/handheld", 2),
	"croc_de_givre": (croc_de_givre, "minecraft:item/handheld", 4),
	"murmure_du_neant": (murmure_du_neant, "minecraft:item/handheld", 3),
	"coutelas_du_contrebandier": (coutelas_du_contrebandier, "minecraft:item/handheld", 1),
	"hache_sylvestre": (hache_sylvestre, "minecraft:item/handheld", 1),
	"pioche_du_titan": (pioche_du_titan, "minecraft:item/handheld", 1),
	"pic_du_filon": (pic_du_filon, "minecraft:item/handheld", 1),
	"marteau_du_seisme": (marteau_du_seisme, "minecraft:item/handheld_mace", 4),
	"trident_du_leviathan": (trident_du_leviathan, "minecraft:item/handheld", 3),
	"faux_des_ames": (faux_des_ames, "minecraft:item/handheld", 3),
	"bottes_de_zephyr": (bottes_de_zephyr, "minecraft:item/generated", 1),
	"bottes_du_coursier": (bottes_du_coursier, "minecraft:item/generated", 1),
}


# ---------------------------------------------------------------------------------------------------------- spell scrolls

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
	return render(c, styles, "#2a1a0c", frames=4)


def pack_icon(firsts):
	"""64x64 icon: four uniques at 2x on a dark background."""
	picks = [firsts[n] for n in ("foudrelame", "trident_du_leviathan", "faux_des_ames", "croc_de_givre")]
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


def preview(path, sheets):
	"""Every sprite (all frames) scaled x8 on a checkerboard, one row per sprite."""
	scale = 8
	width = max(len(frames) for frames in sheets.values()) * SIZE * scale
	height = len(sheets) * SIZE * scale
	rows = []
	for y in range(height):
		rows.append([(200, 200, 200, 255) if ((x // 8) + (y // 8)) % 2 else (150, 150, 150, 255) for x in range(width)])
	for r, frames in enumerate(sheets.values()):
		for f, sprite in enumerate(frames):
			for y in range(SIZE):
				for x in range(SIZE):
					px = sprite[y][x]
					if px[3]:
						for dy in range(scale):
							for dx in range(scale):
								rows[r * SIZE * scale + y * scale + dy][f * SIZE * scale + x * scale + dx] = px
	write_png(path, width, height, rows)


def main():
	sheets = {}
	firsts = {}
	for name, (draw, parent, frametime) in SPRITES.items():
		frames = draw()
		firsts[name] = write_sprite(name, frames, frametime)
		simple_model(name, parent)
		sheets[name] = frames
	for pull in range(4):
		name = "arc_du_phenix" + ("" if pull == 0 else "_pulling_%d" % (pull - 1))
		frames = arc(pull)
		write_sprite(name, frames, 3)
		sheets[name] = frames
	bow_model("arc_du_phenix")
	for name, (rune, palette, seal) in SCROLL_RUNES.items():
		model = "parchemin_" + name
		frames = scroll(rune, palette, seal)
		write_sprite(model, frames, 4)
		simple_model(model, "minecraft:item/generated")
		sheets[model] = frames
	pack_icon(firsts)
	if "--preview" in sys.argv:
		preview(sys.argv[sys.argv.index("--preview") + 1], sheets)
	print("textures, models and item definitions written to", ASSETS)


if __name__ == "__main__":
	main()
