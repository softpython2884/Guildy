"""
Gameplay blocks (DATA markers, loot containers, spawners, jigsaw connectors) and interior props.
"""
from . import nbt as N
from .build import DIRS, OPP

BOSS, MINION, ENTRANCE, GUARD = "guildy:boss", "guildy:minion", "guildy:entrance", "guildy:guard"
LOOT = {
	"common": "guildy:chests/ruin/common",
	"rare": "guildy:chests/ruin/rare",
	"treasure": "guildy:chests/ruin/treasure",
	"nether_common": "guildy:chests/ruin/nether_common",
	"nether_rare": "guildy:chests/ruin/nether_rare",
	"nether_treasure": "guildy:chests/ruin/nether_treasure",
}


def marker_nbt(metadata):
	return N.Compound(
		id=N.String("minecraft:structure_block"), mode=N.String("DATA"), metadata=N.String(metadata),
		name=N.String(""), author=N.String("guildy"), posX=N.Int(0), posY=N.Int(1), posZ=N.Int(0),
		sizeX=N.Int(0), sizeY=N.Int(0), sizeZ=N.Int(0), rotation=N.String("NONE"), mirror=N.String("NONE"),
		ignoreEntities=N.Byte(1), strict=N.Byte(0), powered=N.Byte(0), showair=N.Byte(0),
		showboundingbox=N.Byte(0), integrity=N.Float(1.0), seed=N.Long(0))


def marker(b, x, y, z, metadata):
	b.t.set(x, y, z, "minecraft:structure_block[mode=data]", marker_nbt(metadata))
	b.protected.add((x, y, z))


def guard(b, x, y, z):
	"""
	Post of a room guard (guildy:guard): the feet cell of a mob standing on a solid floor. The two cells above are
	kept clear (no rubble, plants or cobwebs) so the post stays free whatever the decay does.
	"""
	marker(b, x, y, z, GUARD)
	b.clear_zone(x, y + 1, z, x, y + 2, z)


def loot_nbt(block_entity_id, tier):
	return N.Compound(id=N.String(block_entity_id), LootTable=N.String(LOOT[tier]))


def chest(b, x, y, z, facing, tier, kind="chest"):
	"""kind: chest | trapped_chest | barrel. facing = the side the lid opens toward (the player stands there)."""
	if kind == "barrel":
		b.t.set(x, y, z, f"minecraft:barrel[facing={facing},open=false]", loot_nbt("minecraft:barrel", tier))
	elif kind == "trapped_chest":
		b.t.set(x, y, z, f"minecraft:trapped_chest[facing={facing},type=single,waterlogged=false]",
			loot_nbt("minecraft:trapped_chest", tier))
	else:
		b.t.set(x, y, z, f"minecraft:chest[facing={facing},type=single,waterlogged=false]", loot_nbt("minecraft:chest", tier))
	b.protected.add((x, y, z))


def spawner(b, x, y, z, entity, count=3, max_nearby=5, range_=16, min_delay=240, max_delay=700):
	tag = N.Compound(
		id=N.String("minecraft:mob_spawner"),
		SpawnData=N.Compound(entity=N.Compound(id=N.String(entity))),
		SpawnPotentials=N.List([N.Compound(data=N.Compound(entity=N.Compound(id=N.String(entity))), weight=N.Int(1))],
			N.COMPOUND),
		SpawnCount=N.Short(count), MaxNearbyEntities=N.Short(max_nearby), RequiredPlayerRange=N.Short(range_),
		SpawnRange=N.Short(4), Delay=N.Short(20), MinSpawnDelay=N.Short(min_delay), MaxSpawnDelay=N.Short(max_delay))
	b.t.set(x, y, z, "minecraft:spawner", tag)
	b.protected.add((x, y, z))


def jigsaw(b, x, y, z, orientation, name, target, pool, final_state, joint="aligned"):
	tag = N.Compound(
		id=N.String("minecraft:jigsaw"), name=N.String(name), target=N.String(target), pool=N.String(pool),
		final_state=N.String(final_state), joint=N.String(joint), placement_priority=N.Int(0),
		selection_priority=N.Int(0))
	b.t.set(x, y, z, f"minecraft:jigsaw[orientation={orientation}]", tag)
	b.protected.add((x, y, z))


def skull_nbt():
	return N.Compound(id=N.String("minecraft:skull"))


def skull(b, x, y, z, kind="skeleton_skull", rotation=None, wall_facing=None):
	if wall_facing:
		b.t.set(x, y, z, f"minecraft:{kind.replace('_skull', '_wall_skull')}[facing={wall_facing}]", skull_nbt())
	else:
		r = rotation if rotation is not None else int(b.hrand(x, y, z, 77) * 16)
		b.t.set(x, y, z, f"minecraft:{kind}[rotation={r}]", skull_nbt())


def candles(b, x, y, z, n=None, color=None, lit=True):
	n = n or 1 + int(b.hrand(x, y, z, 5) * 4)
	block = f"minecraft:{color}_candle" if color else "minecraft:candle"
	b.t.set(x, y, z, f"{block}[candles={n},lit={'true' if lit else 'false'}]")


def lantern(b, x, y, z, soul=False, hanging=False):
	block = "minecraft:soul_lantern" if soul else "minecraft:lantern"
	b.t.set(x, y, z, f"{block}[hanging={'true' if hanging else 'false'}]")


def hanging_lantern(b, x, y, z, chain_len=1, soul=False):
	"""Chain(s) hanging from the ceiling cell above (x, y + chain_len, z) down to a lantern at y."""
	for k in range(1, chain_len + 1):
		b.t.set(x, y + k, z, "minecraft:iron_chain[axis=y]")
	lantern(b, x, y, z, soul, hanging=True)


def sarcophagus(b, x, y, z, along, mat, lid=None, length=3, skull_kind=None):
	"""
	Stone coffin: a base of full blocks `length` long toward `along`, a lid of slabs with stairs at both ends
	(gabled look). The head end gets an optional skull.
	"""
	dx, dz = DIRS[along]
	lid = lid or mat
	for i in range(length):
		cx, cz = x + dx * i, z + dz * i
		b.set(cx, y, cz, mat)
		if i == 0:
			b.t.set(cx, y + 1, cz, lid.stairs(cx, y + 1, cz, OPP[along], "bottom"))
		elif i == length - 1:
			b.t.set(cx, y + 1, cz, lid.stairs(cx, y + 1, cz, along, "bottom"))
		else:
			b.t.set(cx, y + 1, cz, lid.slab(cx, y + 1, cz, "bottom"))
	if skull_kind:
		hx, hz = x - dx, z - dz
		skull(b, hx, y, hz, skull_kind)


def bookshelf_wall(b, x0, z0, x1, z1, y0, y1, chiseled_ratio=0.15, empty_ratio=0.1):
	for x in range(min(x0, x1), max(x0, x1) + 1):
		for z in range(min(z0, z1), max(z0, z1) + 1):
			for y in range(y0, y1 + 1):
				r = b.hrand(x, y, z, 88)
				if r < empty_ratio:
					b.t.set(x, y, z, "minecraft:oak_planks" if b.hrand(x, y, z, 89) < 0.5 else "minecraft:spruce_planks")
				else:
					b.t.set(x, y, z, "minecraft:bookshelf")
