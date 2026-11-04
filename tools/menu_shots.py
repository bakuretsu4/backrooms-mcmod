"""Builds the main-menu footage shots: picked PNG screenshots -> 1920x1080 baseline JPEGs + shots.json.

The title screen (client/menu/FootageBackground.java) plays these shots as found footage, level by
level. Edit SHOTS below, then run from the repo root:

    python tools/menu_shots.py                 # convert everything, rewrite shots.json, prune stale JPEGs
    python tools/menu_shots.py --only level0_a # reconvert just these ids (manifest is still rewritten)
    python tools/menu_shots.py --check         # validate SHOTS and print framing hints, write nothing
    python tools/menu_shots.py --src D:/caps    # where relative `src` paths are looked up

Output: src/main/resources/assets/backrooms/textures/gui/menu/shots/<id>.jpg and shots.json there.

Why JPEG: 1920x1080 PNG screenshots are 3-5 MB each; a q88 JPEG is ~0.4 MB and the menu draws
them under grain and scanlines anyway. Baseline (not progressive) so stb_image decodes it fast.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "backrooms"
OUT = ASSETS / "textures" / "gui" / "menu" / "shots"
LANG = ASSETS / "lang" / "en_us.json"
DEFAULT_SRC = ROOT / "run" / "screenshots"

SIZE = (1920, 1080)
QUALITY = 88
MOVES = ("in", "out", "left", "right", "up", "down")
ID_RE = re.compile(r"^[a-z0-9_]+$")

# Where the title screen wants the focal point (fraction of the window), and how far it may zoom
# to put it there. Mirrors FootageBackground/BackroomsTitleScreen; only used for the hints below.
TITLE_TARGET = (0.60, 0.53)
ZOOM_MAX = 1.12

# One entry per shot, in any order (the menu groups them by level and shuffles the levels).
#   id      file name (<id>.jpg), [a-z0-9_]
#   level   level key: the OSD caption is lang "menu.backrooms.scene.<level>" unless `caption` is set
#   src     the PNG (relative to --src, or absolute); if it is missing but <id>.jpg already exists,
#           the existing JPEG is kept as is
#   focus   (fx, fy): the most important point, as fractions of the OUTPUT image (0..1, from the
#           top-left). The menu frames the shot so this lands in the free space between the menu
#           column and the taped note; around (0.60, 0.53) needs no extra zoom.
#   move    the slow camera drift during the shot: "in"/"out" = push in / pull back on the focus;
#           "left"/"right"/"up"/"down" = the camera pans that way (the picture slides the other way)
#   crop    optional (x0, y0, x1, y1) fractions of the source to use before fitting to 16:9
#   caption optional full lang key overriding the level's caption
#
# The PNGs are full-size in-game captures (2560x1440 window); they are not committed, only the
# JPEGs built from them. Re-shoot a level by replacing its PNG and running with --only <id>.
SHOTS: list[dict] = [
    dict(id="level0_rooms", level="level_0", src="menu/level0_rooms.png", focus=(0.585, 0.520), move="in"),
    dict(id="level0_stack", level="level_0", src="menu/level0_stack.png", focus=(0.550, 0.580), move="out"),
    dict(id="level0_street", level="level_0", src="menu/level0_street.png", focus=(0.600, 0.440), move="in"),
    dict(id="level1_hall", level="level_1", src="menu/level1_hall.png", focus=(0.550, 0.500), move="left"),
    dict(id="level1_wet", level="level_1", src="menu/level1_wet.png", focus=(0.580, 0.480), move="in"),
    dict(id="level2_pipes", level="level_2", src="menu/level2_pipes.png", focus=(0.550, 0.470), move="in"),
    dict(id="level4_office", level="level_4", src="menu/level4_office.png", focus=(0.600, 0.550), move="in"),
    dict(id="poolrooms_skycourt", level="poolrooms", src="menu/poolrooms_skycourt.png", focus=(0.580, 0.480), move="in"),
    dict(id="poolrooms_tiles", level="poolrooms", src="menu/poolrooms_tiles.png", focus=(0.560, 0.420), move="left"),
    dict(id="level37_pool", level="level_37", src="menu/level37_pool.png", focus=(0.620, 0.420), move="in"),
    dict(id="level39_facade", level="level_39", src="menu/level39_facade.png", focus=(0.570, 0.420), move="up"),
    dict(id="level39_tunnel", level="level_39", src="menu/level39_tunnel.png", focus=(0.550, 0.620), move="in"),
    dict(id="level7_thing", level="level_7", src="menu/level7_thing.png", focus=(0.660, 0.720), move="in"),
    dict(id="level9_street", level="level_9", src="menu/level9_street.png", focus=(0.520, 0.550), move="in"),
    dict(id="level94_castle", level="level_94", src="menu/level94_castle.png", focus=(0.620, 0.320), move="in"),
    dict(id="level94_road", level="level_94", src="menu/level94_road.png", focus=(0.560, 0.480), move="right"),
    dict(id="level227_branch", level="level_227", src="menu/level227_branch.png", focus=(0.600, 0.480), move="left"),
    dict(id="level227_forest", level="level_227", src="menu/level227_forest_iris.png", focus=(0.620, 0.550), move="in"),
    dict(id="level228_arcade", level="level_228", src="menu/level228_arcade.png", focus=(0.580, 0.500), move="in"),
    dict(id="level369_meadow", level="level_369", src="menu/level369_meadow.png", focus=(0.570, 0.500), move="in"),
    dict(id="level490_islands", level="level_490", src="menu/level490_islands.png", focus=(0.520, 0.550), move="in"),
]


def fail(msg: str) -> None:
    print("ERROR", msg)
    sys.exit(1)


def validate(shots: list[dict]) -> None:
    lang = json.loads(LANG.read_text(encoding="utf-8"))
    seen: set[str] = set()
    for s in shots:
        sid = s.get("id", "")
        if not ID_RE.match(sid):
            fail(f"bad id {sid!r} (use [a-z0-9_])")
        if sid in seen:
            fail(f"duplicate id {sid}")
        seen.add(sid)
        if not s.get("level") or not ID_RE.match(s["level"]):
            fail(f"{sid}: bad level {s.get('level')!r}")
        if s.get("move", "in") not in MOVES:
            fail(f"{sid}: move must be one of {MOVES}")
        fx, fy = s.get("focus", (0.5, 0.5))
        if not (0.0 <= fx <= 1.0 and 0.0 <= fy <= 1.0):
            fail(f"{sid}: focus must be within 0..1")
        if "crop" in s:
            x0, y0, x1, y1 = s["crop"]
            if not (0.0 <= x0 < x1 <= 1.0 and 0.0 <= y0 < y1 <= 1.0):
                fail(f"{sid}: crop must be 0 <= x0 < x1 <= 1, 0 <= y0 < y1 <= 1")
        key = caption_key(s)
        if key not in lang:
            print(f"WARNING {sid}: lang key {key} is missing from en_us.json")


def caption_key(s: dict) -> str:
    return s.get("caption") or f"menu.backrooms.scene.{s['level']}"


def zoom_needed(fx: float, fy: float) -> float:
    """Zoom (1 = cover-fit) that puts the focus exactly on the title-screen target without showing
    past the picture's edges (same maths as FootageBackground.zoomFor, for a 16:9 window)."""
    tx, ty = TITLE_TARGET
    fx = min(max(fx, 0.02), 0.98)
    fy = min(max(fy, 0.02), 0.98)
    return max(1.0, tx / fx, (1 - tx) / (1 - fx), ty / fy, (1 - ty) / (1 - fy))


def convert(src: Path, dst: Path, crop: tuple | None) -> None:
    with Image.open(src) as im:
        im = im.convert("RGB")
        if crop:
            w, h = im.size
            im = im.crop((round(crop[0] * w), round(crop[1] * h), round(crop[2] * w), round(crop[3] * h)))
        # Cover-fit to 16:9: centre-crop the long side, then resample to exactly 1920x1080.
        w, h = im.size
        target = SIZE[0] / SIZE[1]
        if w / h > target:
            cw = round(h * target)
            im = im.crop(((w - cw) // 2, 0, (w - cw) // 2 + cw, h))
        elif w / h < target:
            ch = round(w / target)
            im = im.crop((0, (h - ch) // 2, w, (h - ch) // 2 + ch))
        if im.size != SIZE:
            im = im.resize(SIZE, Image.LANCZOS)
        dst.parent.mkdir(parents=True, exist_ok=True)
        im.save(dst, "JPEG", quality=QUALITY, progressive=False, optimize=True)


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--src", type=Path, default=DEFAULT_SRC, help="folder for relative src paths")
    ap.add_argument("--out", type=Path, default=OUT, help="output folder (JPEGs + shots.json)")
    ap.add_argument("--only", nargs="*", default=None, help="only (re)convert these ids")
    ap.add_argument("--check", action="store_true", help="validate and print hints only")
    ap.add_argument("--no-prune", action="store_true", help="keep JPEGs that are not in SHOTS")
    args = ap.parse_args()

    if not SHOTS:
        fail("SHOTS is empty: add your picked screenshots to the list at the top of this file")
    validate(SHOTS)

    manifest: list[dict] = []
    for s in SHOTS:
        sid = s["id"]
        fx, fy = s.get("focus", (0.5, 0.5))
        z = zoom_needed(fx, fy)
        hint = f"zoom {z:.2f}" + ("" if z <= ZOOM_MAX else f" > {ZOOM_MAX}: focus lands near, not on, the target")
        src = Path(s["src"])
        if not src.is_absolute():
            src = args.src / src
        dst = args.out / f"{sid}.jpg"
        if args.check:
            state = "src ok" if src.is_file() else ("keep jpg" if dst.is_file() else "MISSING")
            print(f"{sid:16s} {s['level']:10s} {state:8s} {hint}")
            continue
        if args.only is None or sid in args.only:
            if src.is_file():
                convert(src, dst, s.get("crop"))
                print(f"wrote {dst.relative_to(ROOT) if dst.is_relative_to(ROOT) else dst}  ({dst.stat().st_size // 1024} KB, {hint})")
            elif dst.is_file():
                print(f"kept  {sid}.jpg (no source {src.name})")
            else:
                print(f"WARNING {sid}: neither {src} nor {dst.name} exists; left out of shots.json")
                continue
        elif not dst.is_file():
            print(f"WARNING {sid}: {dst.name} not built yet; left out of shots.json")
            continue
        manifest.append({
            "id": sid,
            "level": s["level"],
            "caption": caption_key(s),
            "focus": [round(fx, 4), round(fy, 4)],
            "move": s.get("move", "in"),
        })

    if args.check:
        return
    # One shot per line keeps diffs readable when shots are added or re-framed.
    lines = ",\n".join("  " + json.dumps(m, separators=(", ", ": ")) for m in manifest)
    (args.out / "shots.json").write_text("[\n" + lines + "\n]\n", encoding="utf-8", newline="\n")
    print(f"wrote shots.json ({len(manifest)} shots, {len({m['level'] for m in manifest})} levels)")

    if not args.no_prune:
        keep = {m["id"] + ".jpg" for m in manifest}
        for f in sorted(args.out.glob("*.jpg")):
            if f.name not in keep:
                f.unlink()
                print(f"pruned {f.name}")


if __name__ == "__main__":
    main()
