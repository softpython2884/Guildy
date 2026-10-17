#!/usr/bin/env python3
"""
Preview sheets of the ruins (isometric views, cut-aways, plans) -> tools/ruins/previews/<id>.png

  python tools/ruins/render.py                    every ruin (built from the scripts)
  python tools/ruins/render.py crypte_oubliee     one ruin
  python tools/ruins/render.py --nbt file.nbt [--ground N] [--out preview.png]   any template file
  python tools/ruins/render.py <id> --detail      extra large panels (for close inspection, not committed)
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from ruinlib import render as R  # noqa: E402
from ruinlib.template import Template  # noqa: E402

PREVIEWS = os.path.join(HERE, "previews")


def under_brightness(template, ground):
	"""Brightening factor for underground views: dark rooms (deepslate) need more than sandstone ones."""
	lum = []
	for (x, y, z), state in template.cells.items():
		if y < ground and not state.startswith("minecraft:air"):
			top, side, _ = R.color(state.split("[", 1)[0])
			lum.append(0.3 * side[0] + 0.59 * side[1] + 0.11 * side[2])
	if not lum:
		return 1.0
	avg = sum(lum) / len(lum)
	return max(1.0, min(1.8, 135.0 / max(1.0, avg)))


def sheet_for(template, title, ground, terrain, unit=None, detail=False, levels=None, cut=None, under=None):
	sx, sy, sz = template.size
	glow = under_brightness(template, ground)
	if unit is None:
		unit = max(4, min(9, int(560 / (sx + sz) * 2)))
	if detail:
		unit *= 2
	panels = []
	panels.append((R.render_iso(template, unit, terrain, ground, rotation=0), "Vue sud-est"))
	panels.append((R.render_iso(template, unit, terrain, ground, rotation=2), "Vue nord-ouest"))
	cx, cz = sx // 2, sz // 2
	panels.append((R.render_iso(template, unit, terrain, ground, rotation=0,
		hide=lambda x, y, z: x > cx and z > cz - (y // 3) * 0), "Coupe (quart avant retire)"))
	if cut is None:
		cut = ground + 6
	panels.append((R.render_iso(template, unit, terrain, ground, rotation=1,
		hide=lambda x, y, z: y > cut), f"Coupe horizontale (couche {cut - ground} / sol)"))
	if under is not None:
		panels.append((R.render_iso(template, unit, None, ground, rotation=0, bright=glow,
			hide=lambda x, y, z: y > under), "Sous-sol (coupe a 4 blocs du sol des salles)"))
		panels.append((R.render_iso(template, unit, None, ground, rotation=2, bright=glow,
			hide=lambda x, y, z: y > under), "Sous-sol (autre angle)"))
	plan_unit = max(3, min(7, int(300 / max(sx, sz))))
	if detail:
		plan_unit *= 2
	for y, caption in (levels or [(ground + 1, "Plan du rez-de-chaussee")]):
		panels.append((R.render_plan(template, y, plan_unit, bright=1.0 if y > ground else glow, terrain=terrain,
			ground=ground), caption))
	img = R.sheet(panels, 2)
	out = R.Image.new("RGB", (img.width, img.height + 34), (236, 233, 226))
	out.paste(img, (0, 34))
	R.label(out, title, 22, (10, 6), (20, 20, 20))
	return out


def render_variant(ruin, variant, out=None):
	"""Preview of an imported variant: previews/<id>__<variant>.png."""
	t = variant.assembled(ruin.id)
	img = sheet_for(t, f"{ruin.title} - variante {variant.name}  ({t.size[0]}x{t.size[1]}x{t.size[2]})", variant.ground,
		ruin.terrain, levels=[(variant.ground + 1, "Plan du rez-de-chaussee")], cut=variant.ground + 5,
		under=variant.ground - 6 if variant.ground > 6 else None)
	path = out or os.path.join(PREVIEWS, f"{ruin.id}__{variant.name}.png")
	R.save_png(img, path)
	return path


def render_ruin(ruin, detail=False, out=None):
	if ruin.variants and ruin.variants[0].name:
		return render_variant(ruin, ruin.variants[0], out)
	t = ruin.assembled()
	levels = getattr(ruin, "plan_levels", None)
	img = sheet_for(t, f"{ruin.title}  ({ruin.id}, palier {ruin.tier}, {t.size[0]}x{t.size[1]}x{t.size[2]})",
		ruin.ground, ruin.terrain, detail=detail, levels=levels, cut=getattr(ruin, "cut_level", None),
		under=getattr(ruin, "under_cut", None))
	os.makedirs(PREVIEWS, exist_ok=True)
	path = out or os.path.join(PREVIEWS, ruin.id + (".detail" if detail else "") + ".png")
	if detail:
		img.save(path)
	else:
		R.save_png(img, path)
	return path


def main():
	sys.stdout.reconfigure(encoding="utf-8")
	ap = argparse.ArgumentParser()
	ap.add_argument("ids", nargs="*")
	ap.add_argument("--nbt")
	ap.add_argument("--ground", type=int, default=0)
	ap.add_argument("--terrain", default="minecraft:grass_block")
	ap.add_argument("--out")
	ap.add_argument("--detail", action="store_true")
	args = ap.parse_args()
	if args.nbt:
		t = Template.load(args.nbt)
		img = sheet_for(t, os.path.basename(args.nbt), args.ground, args.terrain, detail=args.detail)
		path = args.out or os.path.splitext(args.nbt)[0] + ".png"
		R.save_png(img, path)
		print(path)
		return
	import ruins
	for ruin_id in args.ids or ruins.IDS:
		ruin = ruins.load(ruin_id)
		print(render_ruin(ruin, args.detail, args.out))


if __name__ == "__main__":
	main()
