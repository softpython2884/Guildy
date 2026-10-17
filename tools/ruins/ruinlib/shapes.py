"""
Neighbour-dependent block shapes, computed like the game does when a player places blocks. Templates are placed
by world generation without shape updates (known shape), so walls / fences / panes / bars connections, stair
corners and chest pairs must be stored in the template itself.
"""
from . import blocks as B
from .build import DIRS, OPP, CW, CCW, is_solid_full

SIDES = ("north", "east", "south", "west")


def _name(state):
	return None if state is None else state.split("[", 1)[0].split(":")[1]


def _kind(state):
	n = _name(state)
	if n is None or n in ("air", "cave_air"):
		return None
	if n.endswith("_wall") and "sign" not in n and "banner" not in n and "torch" not in n and "skull" not in n \
		and "head" not in n and "fan" not in n:
		return "wall"
	if n.endswith("_fence"):
		return "nether_fence" if n == "nether_brick_fence" else "fence"
	if n.endswith("_fence_gate"):
		return "gate"
	if n.endswith("_pane") or n == "glass_pane" or n.endswith("_bars") or n == "iron_bars":
		return "pane"
	return None


def _connects(kind, neighbour, direction):
	nk = _kind(neighbour)
	if kind == "wall":
		if nk in ("wall", "pane", "gate"):
			return True
	elif kind in ("fence", "nether_fence"):
		if nk == kind or nk == "gate":
			return True
	elif kind == "pane":
		if nk in ("pane", "wall"):
			return True
	return is_solid_full(neighbour)


def fix(template):
	"""Recomputes connections of walls / fences / panes / bars and stair shapes in place (stable: two passes, as a
	wall's height on one side depends on the wall above it)."""
	_fix(template)
	_fix(template)


def _fix(template):
	cells = template.cells
	changes = {}
	for (x, y, z), state in cells.items():
		kind = _kind(state)
		block_id, props = B.parse(state)
		if kind in ("wall", "fence", "nether_fence", "pane"):
			new = dict(props)
			connected = {}
			for side in SIDES:
				dx, dz = DIRS[side]
				nb = cells.get((x + dx, y, z + dz))
				connected[side] = _connects(kind, nb, side)
			if kind == "wall":
				above = cells.get((x, y + 1, z))
				above_kind = _kind(above)
				for side in SIDES:
					if not connected[side]:
						new[side] = "none"
					else:
						tall = is_solid_full(above) or (above_kind == "wall" and B.parse(above)[1].get(side, "none") != "none")
						new[side] = "tall" if tall else "low"
				straight = (connected["north"] and connected["south"] and not connected["east"] and not connected["west"]) or \
					(connected["east"] and connected["west"] and not connected["north"] and not connected["south"])
				above_name = _name(above) or ""
				post_above = above_kind == "wall" and B.parse(above)[1].get("up") == "true"
				needs_post = post_above or any(k in above_name for k in ("torch", "lantern", "candle", "skull", "head",
					"pot", "campfire", "chain", "rod", "plate"))
				new["up"] = "true" if (not straight or needs_post) else "false"
			else:
				for side in SIDES:
					new[side] = "true" if connected[side] else "false"
			changes[(x, y, z)] = B.fmt(block_id, new)
		elif block_id.endswith("_stairs"):
			changes[(x, y, z)] = B.fmt(block_id, dict(props, shape=_stair_shape(cells, x, y, z, props)))
	for pos, state in changes.items():
		cells[pos] = B.canon(state)


def _stair_props(state):
	if state is None or not state.split("[", 1)[0].endswith("_stairs"):
		return None
	return B.parse(state)[1]


def _stair_shape(cells, x, y, z, props):
	facing = props.get("facing", "north")
	half = props.get("half", "bottom")
	dx, dz = DIRS[facing]
	behind = _stair_props(cells.get((x + dx, y, z + dz)))
	if behind and behind.get("half") == half:
		bf = behind.get("facing")
		if bf in (CW[facing], CCW[facing]) and _can_take(cells, x, y, z, props, OPP[bf]):
			return "outer_left" if bf == CCW[facing] else "outer_right"
	front = _stair_props(cells.get((x - dx, y, z - dz)))
	if front and front.get("half") == half:
		ff = front.get("facing")
		if ff in (CW[facing], CCW[facing]) and _can_take(cells, x, y, z, props, ff):
			return "inner_left" if ff == CCW[facing] else "inner_right"
	return "straight"


def _can_take(cells, x, y, z, props, side):
	dx, dz = DIRS[side]
	other = _stair_props(cells.get((x + dx, y, z + dz)))
	return other is None or other.get("facing") != props.get("facing") or other.get("half") != props.get("half")
