"""
Schematic formats <-> Template: Sponge .schem (v2 and v3, WorldEdit / FAWE), Litematica .litematic, vanilla
structure .nbt. Readers keep every block (air included) and block entity data; writers exist so the converter can
be tested on files produced from known templates.
"""
import os

from . import blocks as B
from . import nbt as N
from .template import Template

# Old ids still found in schematics made with older versions.
RENAMED = {
	"minecraft:grass": "minecraft:short_grass",
	"minecraft:chain": "minecraft:iron_chain",
	"minecraft:sign": "minecraft:oak_sign",
	"minecraft:wall_sign": "minecraft:oak_wall_sign",
}


def _state(name, props):
	name = RENAMED.get(B.full_id(str(name)), B.full_id(str(name)))
	return B.fmt(name, {str(k): str(v) for k, v in props.items()})


def _parse_state_string(s):
	block_id, props = B.parse(str(s))
	return _state(block_id, props)


# ----------------------------------------------------------------------------------------------- varints

def _varints(data):
	out = []
	value = shift = 0
	for byte in data:
		byte &= 0xFF
		value |= (byte & 0x7F) << shift
		if byte & 0x80:
			shift += 7
			continue
		out.append(value)
		value = shift = 0
	return out


def _to_varints(values):
	out = []
	for v in values:
		while True:
			byte = v & 0x7F
			v >>= 7
			if v:
				out.append(byte | 0x80)
			else:
				out.append(byte)
				break
	return [b - 256 if b > 127 else b for b in out]


# ------------------------------------------------------------------------------------------------ sponge

def read_schem(root):
	"""Sponge schematic v2 (root = schematic) or v3 (root = {Schematic: {...}})."""
	if "Schematic" in root and isinstance(root["Schematic"], dict):
		root = root["Schematic"]
	version = int(root.get("Version", 2))
	w, h, l = int(root["Width"]) & 0xFFFF, int(root["Height"]) & 0xFFFF, int(root["Length"]) & 0xFFFF
	if version >= 3:
		blocks = root.get("Blocks", {})
		palette = blocks.get("Palette", {})
		data = blocks.get("Data", [])
		entities = blocks.get("BlockEntities", [])
	else:
		palette = root.get("Palette", {})
		data = root.get("BlockData", [])
		entities = root.get("BlockEntities", root.get("TileEntities", []))
	states = {int(i): _parse_state_string(name) for name, i in palette.items()}
	t = Template(w, h, l)
	for index, value in enumerate(_varints(data)):
		y, rem = divmod(index, w * l)
		z, x = divmod(rem, w)
		state = states.get(value, "minecraft:air")
		if not state.startswith("minecraft:structure_void"):
			t.cells[(x, y, z)] = state
	for e in entities:
		pos = tuple(int(c) for c in e["Pos"])
		if version >= 3:
			tag = N.Compound(e.get("Data", {}))
			tag["id"] = N.String(str(e.get("Id", tag.get("id", ""))))
		else:
			tag = N.Compound({k: v for k, v in e.items() if k not in ("Pos", "Id")})
			tag["id"] = N.String(str(e.get("Id", e.get("id", ""))))
		t.nbt[pos] = tag
	return t


def write_schem(t, path, version=2, data_version=B.DATA_VERSION):
	"""Writes a Sponge schematic (air for every unset cell, structure_void kept as air too)."""
	w, h, l = t.size
	palette = {}
	values = []
	for y in range(h):
		for z in range(l):
			for x in range(w):
				state = t.cells.get((x, y, z), "minecraft:structure_void")
				if state not in palette:
					palette[state] = len(palette)
				values.append(palette[state])
	pal_tag = N.Compound({s: N.Int(i) for s, i in palette.items()})
	data = N.ByteArray(_to_varints(values))
	ents = []
	for (x, y, z), tag in t.nbt.items():
		body = N.Compound({k: v for k, v in tag.items() if k != "id"})
		if version >= 3:
			ents.append(N.Compound(Pos=N.IntArray([x, y, z]), Id=N.String(str(tag.get("id", ""))), Data=body))
		else:
			e = N.Compound(body)
			e["Pos"] = N.IntArray([x, y, z])
			e["Id"] = N.String(str(tag.get("id", "")))
			ents.append(e)
	common = dict(Version=N.Int(version), DataVersion=N.Int(data_version), Width=N.Short(w), Height=N.Short(h),
		Length=N.Short(l), Offset=N.IntArray([0, 0, 0]))
	if version >= 3:
		root = N.Compound(Schematic=N.Compound(common, Blocks=N.Compound(Palette=pal_tag, Data=data,
			BlockEntities=N.List(ents, N.COMPOUND))))
		N.save(path, root, name="")
	else:
		root = N.Compound(common, PaletteMax=N.Int(len(palette)), Palette=pal_tag, BlockData=data,
			BlockEntities=N.List(ents, N.COMPOUND))
		N.save(path, root, name="Schematic")


# --------------------------------------------------------------------------------------------- litematica

def _bits(n):
	return max(2, (max(1, n - 1)).bit_length())


def read_litematic(root):
	"""All regions merged in one template (positions relative to the lowest corner)."""
	regions = []
	for name, reg in root.get("Regions", {}).items():
		pos = reg["Position"]
		size = reg["Size"]
		px, py, pz = int(pos["x"]), int(pos["y"]), int(pos["z"])
		sx, sy, sz = int(size["x"]), int(size["y"]), int(size["z"])
		# negative sizes: the region extends toward negative coordinates from Position
		ox = px + sx + 1 if sx < 0 else px
		oy = py + sy + 1 if sy < 0 else py
		oz = pz + sz + 1 if sz < 0 else pz
		regions.append((reg, (ox, oy, oz), (abs(sx), abs(sy), abs(sz))))
	if not regions:
		raise ValueError("litematic without regions")
	mx = min(o[0] for _, o, _ in regions)
	my = min(o[1] for _, o, _ in regions)
	mz = min(o[2] for _, o, _ in regions)
	Mx = max(o[0] + s[0] for _, o, s in regions)
	My = max(o[1] + s[1] for _, o, s in regions)
	Mz = max(o[2] + s[2] for _, o, s in regions)
	t = Template(Mx - mx, My - my, Mz - mz)
	for reg, (ox, oy, oz), (sx, sy, sz) in regions:
		palette = [_state(e["Name"], e.get("Properties", {})) for e in reg["BlockStatePalette"]]
		bits = _bits(len(palette))
		mask = (1 << bits) - 1
		longs = [v & 0xFFFFFFFFFFFFFFFF for v in reg["BlockStates"]]
		total = sx * sy * sz
		for index in range(total):
			bit = index * bits
			i, off = divmod(bit, 64)
			value = longs[i] >> off
			if off + bits > 64:
				value |= longs[i + 1] << (64 - off)
			value &= mask
			y, rem = divmod(index, sx * sz)
			z, x = divmod(rem, sx)
			state = palette[value] if value < len(palette) else "minecraft:air"
			if not state.startswith("minecraft:structure_void"):
				t.cells[(x + ox - mx, y + oy - my, z + oz - mz)] = state
		for e in reg.get("TileEntities", []):
			pos = (int(e["x"]) + ox - mx, int(e["y"]) + oy - my, int(e["z"]) + oz - mz)
			t.nbt[pos] = N.Compound({k: v for k, v in e.items() if k not in ("x", "y", "z")})
	return t


def write_litematic(t, path, name="ruin"):
	sx, sy, sz = t.size
	palette = ["minecraft:air"]
	index = {"minecraft:air": 0}
	values = []
	for y in range(sy):
		for z in range(sz):
			for x in range(sx):
				state = t.cells.get((x, y, z), "minecraft:structure_void")
				if state not in index:
					index[state] = len(palette)
					palette.append(state)
				values.append(index[state])
	bits = _bits(len(palette))
	longs = [0] * ((len(values) * bits + 63) // 64)
	for i, v in enumerate(values):
		bit = i * bits
		j, off = divmod(bit, 64)
		longs[j] |= (v << off) & 0xFFFFFFFFFFFFFFFF
		if off + bits > 64:
			longs[j + 1] |= v >> (64 - off)
	longs = [v - (1 << 64) if v >= (1 << 63) else v for v in longs]
	pal = []
	for s in palette:
		block_id, props = B.parse(s)
		e = N.Compound(Name=N.String(block_id))
		if props:
			e["Properties"] = N.Compound({k: N.String(v) for k, v in props.items()})
		pal.append(e)
	tiles = []
	for (x, y, z), tag in t.nbt.items():
		e = N.Compound(tag)
		e["x"], e["y"], e["z"] = N.Int(x), N.Int(y), N.Int(z)
		tiles.append(e)
	region = N.Compound(
		Position=N.Compound(x=N.Int(0), y=N.Int(0), z=N.Int(0)),
		Size=N.Compound(x=N.Int(sx), y=N.Int(sy), z=N.Int(sz)),
		BlockStatePalette=N.List(pal, N.COMPOUND),
		BlockStates=N.LongArray(longs),
		TileEntities=N.List(tiles, N.COMPOUND),
		Entities=N.List([], N.COMPOUND), PendingBlockTicks=N.List([], N.COMPOUND), PendingFluidTicks=N.List([], N.COMPOUND))
	root = N.Compound(
		MinecraftDataVersion=N.Int(B.DATA_VERSION), Version=N.Int(6),
		Metadata=N.Compound(Name=N.String(name), Author=N.String("guildy"), Description=N.String(""),
			RegionCount=N.Int(1), TotalBlocks=N.Int(len(t.cells)), TotalVolume=N.Int(sx * sy * sz),
			EnclosingSize=N.Compound(x=N.Int(sx), y=N.Int(sy), z=N.Int(sz)), TimeCreated=N.Long(0), TimeModified=N.Long(0)),
		Regions=N.Compound({name: region}))
	N.save(path, root)


# ----------------------------------------------------------------------------------------------- loading

def load_any(path):
	"""Template + detected format from a .schem / .schematic / .litematic / .nbt file."""
	root = N.load(path)
	ext = os.path.splitext(path)[1].lower()
	if ext == ".litematic" or "Regions" in root:
		return read_litematic(root), "litematic"
	if ext in (".schem", ".schematic") or "Schematic" in root or "BlockData" in root or "Palette" in root and "Width" in root:
		if "Blocks" in root and isinstance(root.get("Blocks"), list):
			raise ValueError("ancien format MCEdit .schematic (avant 1.13) non pris en charge : exportez en .schem")
		return read_schem(root), "sponge v" + str(int((root.get("Schematic") or root).get("Version", 2)))
	t = Template.from_nbt(root, name=path, strict=False)
	# vanilla templates may hold air everywhere when saved with "show air"; structure_void cells are already absent
	return t, "structure nbt"
