"""
Structure template model (the vanilla structure .nbt layout, StructureTemplate.save / load):

  {DataVersion: int, size: [x, y, z], palette: [{id, properties}], blocks: [{pos: [x, y, z], state: n, nbt?}],
   entities: [{pos: [dx, dy, dz], blockPos: [x, y, z], nbt}]}

Cells that are not in the template keep the world block when it is placed (what a structure_void does);
"minecraft:air" cells carve.
"""
from . import blocks as B
from . import nbt as N

AIR = "minecraft:air"


class Template:
	def __init__(self, sx, sy, sz, name=""):
		self.size = [sx, sy, sz]
		self.name = name
		self.cells = {}  # (x, y, z) -> canonical state
		self.nbt = {}  # (x, y, z) -> Compound (block entity data)
		self.entities = []  # {"pos": (dx, dy, dz), "blockPos": (x, y, z), "nbt": Compound}
		self._canon = {}

	# ---------------------------------------------------------------------------------------------- access

	def inside(self, x, y, z):
		return 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]

	def canon(self, state):
		cached = self._canon.get(state)
		if cached is None:
			cached = B.canon(state)
			self._canon[state] = cached
		return cached

	def set(self, x, y, z, state, nbt=None):
		"""Sets a cell (None = structure void). Out-of-bounds writes are ignored."""
		if not self.inside(x, y, z):
			return
		key = (x, y, z)
		if state is None:
			self.cells.pop(key, None)
			self.nbt.pop(key, None)
			return
		self.cells[key] = self.canon(state)
		if nbt is not None:
			self.nbt[key] = nbt
		else:
			self.nbt.pop(key, None)

	def get(self, x, y, z):
		return self.cells.get((x, y, z))

	def block(self, x, y, z):
		state = self.cells.get((x, y, z))
		return None if state is None else state.split("[", 1)[0]

	def clear(self, x, y, z):
		self.set(x, y, z, None)

	def positions(self):
		return self.cells.keys()

	def bounds(self):
		"""(min, max) corners of the non-void cells, or None if empty."""
		if not self.cells:
			return None
		xs, ys, zs = zip(*self.cells.keys())
		return (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))

	def copy(self):
		other = Template(*self.size, name=self.name)
		other.cells = dict(self.cells)
		other.nbt = {k: v for k, v in self.nbt.items()}
		other.entities = list(self.entities)
		return other

	def crop(self, margin=0):
		"""Shrinks the template to its non-void bounds (+ margin of void)."""
		bounds = self.bounds()
		if bounds is None:
			return self
		(x0, y0, z0), (x1, y1, z1) = bounds
		x0, y0, z0 = max(0, x0 - margin), max(0, y0 - margin), max(0, z0 - margin)
		other = Template(x1 - x0 + 1 + margin, y1 - y0 + 1 + margin, z1 - z0 + 1 + margin, self.name)
		for (x, y, z), state in self.cells.items():
			other.cells[(x - x0, y - y0, z - z0)] = state
		for (x, y, z), tag in self.nbt.items():
			other.nbt[(x - x0, y - y0, z - z0)] = tag
		for e in self.entities:
			px, py, pz = e["pos"]
			bx, by, bz = e["blockPos"]
			other.entities.append({"pos": (px - x0, py - y0, pz - z0), "blockPos": (bx - x0, by - y0, bz - z0),
				"nbt": e["nbt"]})
		return other

	# -------------------------------------------------------------------------------------------- file I/O

	def to_nbt(self):
		palette, index = [], {}
		full, other, with_nbt = [], [], []
		for pos in sorted(self.cells, key=lambda p: (p[1], p[0], p[2])):
			state = self.cells[pos]
			if state not in index:
				index[state] = len(palette)
				block_id, props = B.parse(state)
				entry = N.Compound(id=N.String(block_id))
				if props:
					entry["properties"] = N.Compound({k: N.String(v) for k, v in sorted(props.items())})
				palette.append(entry)
			tag = N.Compound(pos=N.List([N.Int(c) for c in pos], N.INT), state=N.Int(index[state]))
			if pos in self.nbt:
				tag["nbt"] = self.nbt[pos]
				with_nbt.append(tag)
			elif _is_full(state):
				full.append(tag)
			else:
				other.append(tag)
		entities = N.List([], N.COMPOUND)
		for e in self.entities:
			entities.append(N.Compound(
				pos=N.List([N.Double(c) for c in e["pos"]], N.DOUBLE),
				blockPos=N.List([N.Int(c) for c in e["blockPos"]], N.INT),
				nbt=e["nbt"]))
		return N.Compound(
			size=N.List([N.Int(c) for c in self.size], N.INT),
			entities=entities,
			blocks=N.List(full + other + with_nbt, N.COMPOUND),
			palette=N.List(palette, N.COMPOUND),
			DataVersion=N.Int(B.DATA_VERSION))

	def save(self, path):
		N.save(path, self.to_nbt())

	@staticmethod
	def from_nbt(root, name="", strict=False):
		size = [int(c) for c in root.get("size", [0, 0, 0])]
		t = Template(*size, name=name)
		palettes = root.get("palettes")
		palette = palettes[0] if palettes else root.get("palette", [])
		states = []
		for entry in palette:
			block_id = str(entry.get("id", entry.get("Name", "minecraft:air")))
			props = {k: str(v) for k, v in entry.get("properties", entry.get("Properties", {})).items()}
			state = B.fmt(B.full_id(block_id), props)
			try:
				state = B.canon(state, strict=strict)
			except B.BlockError:
				if strict:
					raise
			states.append(state)
		for tag in root.get("blocks", []):
			pos = tuple(int(c) for c in tag["pos"])
			state = states[int(tag["state"])]
			if state.startswith("minecraft:structure_void"):
				continue
			t.cells[pos] = state
			if "nbt" in tag:
				t.nbt[pos] = tag["nbt"]
		for e in root.get("entities", []):
			t.entities.append({"pos": tuple(float(c) for c in e["pos"]),
				"blockPos": tuple(int(c) for c in e["blockPos"]), "nbt": e.get("nbt", N.Compound())})
		return t

	@staticmethod
	def load(path, strict=False):
		return Template.from_nbt(N.load(path), name=path, strict=strict)

	# ------------------------------------------------------------------------------------------- markers

	def markers(self):
		"""[(pos, metadata)] of the DATA-mode structure blocks."""
		result = []
		for pos, state in self.cells.items():
			if state.startswith("minecraft:structure_block"):
				tag = self.nbt.get(pos, {})
				if str(tag.get("mode", "")).upper() == "DATA":
					result.append((pos, str(tag.get("metadata", ""))))
		return sorted(result)


def _is_full(state):
	block_id = state.split("[", 1)[0]
	if block_id == AIR:
		return False
	for part in ("stairs", "slab", "wall", "fence", "pane", "bars", "door", "carpet", "torch", "lantern", "button",
		"plate", "sign", "banner", "vine", "lichen", "roots", "candle", "chain", "ladder", "skull", "head", "pot",
		"rail", "snow", "web", "fire", "grass", "fern", "flower", "sapling", "bush", "leaves", "glass", "chest",
		"campfire", "lectern", "stand", "cauldron", "anvil", "bell", "trapdoor", "dripleaf", "mushroom", "fungus",
		"sprouts", "lily", "kelp", "coral", "petals", "moss_carpet", "scaffolding", "hopper", "bed", "cake",
		"rod", "pickle", "egg", "amethyst_cluster", "bud", "dripstone", "spore", "azalea", "frogspawn", "tripwire",
		"redstone_wire", "lever", "repeater", "comparator", "item_frame", "structure_block", "spawner"):
		if part in block_id:
			return False
	return True
