"""Level 2 "Pipe Dreams": the maintenance chambers' props and the heart gauge (registry/Level2ChamberBlocks).

Textures (both 64 px, the props size of the nearly full block atlas):
  l2_heart_dial    the same yellowed pressure dial as l2_gauge_dial, without its painted needle
                   (client/anomaly/level2/HeartGaugeRenderer draws a live one that follows your pulse)
  l2_chamber_props one sheet for the crews' things: a faded hi-vis work jacket, the shift logbook
                   (green cloth cover, ruled pages), an enamel kettle, a chipped mug, hook steel

Models and blockstates (LF): l2_heart_gauge (a copy of l2_gauge with the needle-less dial, so it
always looks like every other gauge: re-run this after l2_gauge changes), l2_chamber_prop_<prop>.

Run: python tools/gen_assets.py l2chambers      (fixed seeds 2600-2609: identical files every run)

Previews of the chambers' drawn plans (read from world/layout/Level2Chambers.java) and of the
detours (run from tools/): python -c "from anomaly_assets import level2_chambers as m; m.preview('OUT_DIR')"
"""
from __future__ import annotations

import json
import re

import numpy as np

import gen_assets as G

S = G.S          # drawn at 128, saved at 64
SMALL = 64
FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}
PROPS = ("logbook", "kettle", "mug", "jacket")


def _blk(name):
    return G.TEX / "block" / f"{name}.png"


def _ref(name):
    return f"backrooms:block/{name}"


def _save(img, name):
    """Box-filter down to 64 px (a cutout keeps a strictly 0/255 alpha) and save."""
    k = img.shape[0] // SMALL
    if k > 1:
        h, w = img.shape[0] // k, img.shape[1] // k
        img = img.reshape(h, k, w, k, img.shape[2]).mean(axis=(1, 3))
        if img.shape[2] == 4:
            img[..., 3] = np.where(img[..., 3] >= 128, 255, 0)
    G.save_png(img, _blk(name))


def _write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", newline="\n")


# =============================================================================
# Textures
# =============================================================================

def heart_dial(rng) -> np.ndarray:
    """level2 gauge_dial without its needle: chrome bezel, yellowed white face, ticks and numbers
    0-16 kPa x10 with a red zone at the top end, the black hub, a fogged glass sheen."""
    X, Y = G.grid()
    c = 64
    r = np.hypot(X - c, Y - c)
    ang = np.degrees(np.arctan2(X - c, -(Y - c)))
    img = G.fill((222, 214, 190), (S, S))
    img = G.add_lum(img, 6 * G.fnoise(rng, beta=2.0))
    img = G.over(img, np.array([190, 170, 120]), G.smoothstep(30, 60, r) * 0.25)
    sweep = (ang > -135) & (ang < 135)
    for k in range(0, 41):
        a = -135 + 270 * k / 40
        major = k % 5 == 0
        m = (np.abs(((ang - a + 180) % 360) - 180) < (1.6 if major else 0.8)) & (r > (40 if major else 44)) & (r < 50)
        img = G.over(img, np.array([24, 22, 20]), m.astype(float))
    red = sweep & (ang > 85) & (ang < 135) & (r > 44) & (r < 50)
    img = G.over(img, np.array([180, 30, 26]), red.astype(float))
    for k, num in enumerate(("0", "4", "8", "12", "16")):
        a = np.radians(-135 + 270 * k / 4)
        tx, ty = c + 31 * np.sin(a), c - 31 * np.cos(a)
        img = G.over(img, np.array([24, 22, 20]), G._text(num, tx, ty, 11))
    img = G.over(img, np.array([24, 22, 20]), G._text("kPa x10", 64, 102, 8))
    img = G.over(img, np.array([30, 30, 30]), G.smoothstep(5, 4, r))
    img = G.add_lum(img, 26 * G.smoothstep(40, 0, np.hypot(X - 46, Y - 40)) * (r < 54))
    bezel = (r > 54) & (r < 63)
    chrome = 150 + 60 * np.cos(np.radians(ang - 315))
    img = np.where(bezel[..., None], np.stack([chrome, chrome, chrome * 1.02], -1) + 6 * G.fnoise(rng, beta=1.0)[..., None], img)
    img = G.add_lum(img, -30 * G.smoothstep(50, 54, r) * (r < 54))
    out = np.zeros((S, S, 4))
    out[..., :3] = img
    out[..., 3] = np.where(r < 63, 255, 0)
    return out


def _region(img, x0, y0, x1, y1, part):
    img[y0:y1, x0:x1, :3] = part[: y1 - y0, : x1 - x0]


def chamber_props(rng) -> np.ndarray:
    """The props sheet (128-px layout, saved at 64). Regions (128-px coordinates):
    jacket 0-64 x 0-64, logbook cover 64-128 x 0-32, pages 64-128 x 32-64, kettle 0-64 x 64-96,
    mug 64-96 x 64-96, coffee 96-128 x 64-80, steel 96-128 x 80-96, lining 0-64 x 96-128,
    page edges 64-128 x 96-128."""
    X, Y = G.grid()
    sheet = np.zeros((S, S, 4))
    sheet[..., 3] = 255
    noise = G.fnoise(rng, beta=2.0)
    fine = rng.normal(0, 1, (S, S))

    # Work jacket: a faded, grimy hi-vis orange with a grey reflective band and a zip line.
    jacket = G.fill((196, 102, 38), (S, S))
    jacket = G.add_lum(jacket, 14 * noise + 3 * fine)
    jacket = G.over(jacket, np.array([150, 150, 146]), ((Y > 36) & (Y < 45)).astype(float) * 0.92)
    jacket = G.over(jacket, np.array([120, 120, 118]), ((Y > 52) & (Y < 56)).astype(float) * 0.8)
    jacket = G.over(jacket, np.array([40, 34, 30]), (np.abs(X - 32) < 1.2).astype(float) * 0.9)  # zip
    jacket = G.over(jacket, np.array([70, 56, 44]), G.smoothstep(0.6, 1.8, noise) * 0.35)        # grime
    jacket = G.over(jacket, np.array([60, 50, 40]), G.smoothstep(40, 64, Y) * 0.25)              # dirty hem
    _region(sheet, 0, 0, 64, 64, jacket)

    # Logbook cover: dark green cloth, worn pale at the edges, a paper label.
    cover = G.fill((38, 64, 46), (S, S))
    cover = G.add_lum(cover, 8 * noise + 4 * fine)
    edge = np.minimum(np.minimum(X - 64, 127 - X), np.minimum(Y, 31 - Y))
    cover = G.over(cover, np.array([110, 120, 100]), G.smoothstep(3, 0, edge) * 0.5)
    label = (X > 80) & (X < 112) & (Y > 8) & (Y < 22)
    cover = G.over(cover, np.array([214, 204, 176]), label.astype(float))
    cover = G.over(cover, np.array([40, 40, 60]), (label & (np.abs(Y - 13) < 0.8) & (X > 84) & (X < 108)).astype(float))
    cover = G.over(cover, np.array([40, 40, 60]), (label & (np.abs(Y - 17) < 0.8) & (X > 86) & (X < 104)).astype(float))
    _region(sheet, 64, 0, 128, 32, cover[0:32, 64:128])

    # Pages: off-white, blue rules, scrawled handwriting, a coffee ring.
    page = G.fill((222, 214, 192), (S, S))
    page = G.add_lum(page, 5 * noise)
    rules = (np.abs(((Y - 32) % 5) - 2.5) < 0.45) & (Y > 36) & (Y < 62)
    page = G.over(page, np.array([120, 140, 190]), rules.astype(float) * 0.6)
    scrawl = (np.sin((X - 64) * 1.7 + np.sin(X * 0.6) * 2) > 0.25) & (np.abs(((Y - 34) % 5) - 1.6) < 0.7) \
        & (Y > 36) & (Y < 61) & (np.abs(X - 96) > 2) & (X > 68) & (X < 124)
    page = G.over(page, np.array([36, 40, 70]), scrawl.astype(float) * 0.85)
    page = G.over(page, np.array([60, 60, 60]), (np.abs(X - 96) < 1.0).astype(float) * 0.5)          # the gutter
    ring = np.abs(np.hypot(X - 112, Y - 54) - 6) < 0.9
    page = G.over(page, np.array([130, 92, 50]), ring.astype(float) * 0.5)
    _region(sheet, 64, 32, 128, 64, page[32:64, 64:128])

    # Kettle: cream enamel, black chips, a dark rim band, rust at the chips.
    kettle = G.fill((214, 206, 180), (S, S))
    kettle = G.add_lum(kettle, 6 * noise + 2 * fine)
    kettle = G.over(kettle, np.array([30, 40, 52]), ((Y > 64) & (Y < 69)).astype(float))
    chips = G.smoothstep(1.4, 2.0, G.fnoise(G.rng_for(2603), beta=1.2))
    kettle = G.over(kettle, np.array([28, 26, 24]), chips * 0.9)
    kettle = G.over(kettle, np.array([120, 60, 30]), np.clip(G.blur(chips, 1.0) - chips, 0, 1) * 0.6)
    _region(sheet, 0, 64, 64, 96, kettle[64:96, 0:64])

    # Mug: white enamel, a blue rim, chips.
    mug = G.fill((228, 226, 220), (S, S))
    mug = G.add_lum(mug, 4 * noise)
    mug = G.over(mug, np.array([40, 70, 130]), ((Y > 64) & (Y < 67)).astype(float))
    mug = G.over(mug, np.array([30, 30, 32]), G.smoothstep(1.6, 2.1, G.fnoise(G.rng_for(2604), beta=1.2)) * 0.9)
    _region(sheet, 64, 64, 96, 96, mug[64:96, 64:96])

    # Cold coffee, with a skin on it.
    coffee = G.fill((44, 28, 18), (S, S))
    coffee = G.add_lum(coffee, 5 * noise)
    _region(sheet, 96, 64, 128, 80, coffee[64:80, 96:128])

    # Hook and handle steel.
    steel = G.fill((112, 114, 116), (S, S))
    steel = G.add_lum(steel, 18 * np.sin(Y * 0.9) + 6 * fine)
    _region(sheet, 96, 80, 128, 96, steel[80:96, 96:128])

    # Jacket lining and collar: dark navy, worn.
    lining = G.fill((30, 34, 48), (S, S))
    lining = G.add_lum(lining, 6 * noise + 3 * fine)
    _region(sheet, 0, 96, 64, 128, lining[96:128, 0:64])

    # The edges of a stack of pages.
    edges = G.fill((206, 198, 176), (S, S))
    edges = G.add_lum(edges, -18 * (((Y // 2) % 2) == 0) + 4 * fine)
    _region(sheet, 64, 96, 128, 128, edges[96:128, 64:128])
    return sheet


def tex_l2chambers_textures() -> None:
    _save(heart_dial(G.rng_for(2600)), "l2_heart_dial")
    _save(chamber_props(G.rng_for(2601)), "l2_chamber_props")


# =============================================================================
# Models and blockstates
# =============================================================================

# UV regions of the props sheet, in model units (16 = the whole 64-px sheet).
UV_JACKET = (0, 0, 8, 8)
UV_COVER = (8, 0, 16, 4)
UV_PAGES = (8, 4, 16, 8)
UV_KETTLE = (0, 8, 8, 12)
UV_MUG = (8, 8, 12, 12)
UV_COFFEE = (12, 8, 16, 10)
UV_STEEL = (12, 10, 16, 12)
UV_LINING = (0, 12, 8, 16)
UV_EDGES = (8, 12, 16, 16)


def _box(frm, to, uv, up=None, rotation=None):
    """One element, every face mapped onto the same sheet region (the up face may differ)."""
    faces = {}
    for f in ("north", "south", "east", "west", "up", "down"):
        faces[f] = {"uv": list(up if (f == "up" and up) else uv), "texture": "#sheet"}
    e = {"from": list(frm), "to": list(to), "faces": faces}
    if rotation:
        e["rotation"] = rotation
    return e


def _prop_model(prop):
    if prop == "logbook":
        # Lying open on the bench top (the model starts 1 px low so it rests on a 15 px bench).
        elements = [
            _box((2.5, -1, 4), (13.5, -0.6, 12), UV_COVER),
            _box((3, -0.6, 4.4), (8, 0.2, 11.6), UV_EDGES, up=(8, 4, 12, 8)),
            _box((8, -0.6, 4.4), (13, 0.1, 11.6), UV_EDGES, up=(12, 4, 16, 8)),
        ]
    elif prop == "kettle":
        elements = [
            _box((4.5, -1, 5), (11.5, 5, 11), UV_KETTLE),
            _box((5.5, 5, 6), (10.5, 6, 10), UV_KETTLE),
            _box((7.5, 6, 7.5), (8.5, 7, 8.5), UV_STEEL),
            _box((5.6, 5, 7.6), (6.4, 8, 8.4), UV_STEEL),
            _box((9.6, 5, 7.6), (10.4, 8, 8.4), UV_STEEL),
            _box((5.6, 8, 7.6), (10.4, 8.8, 8.4), UV_STEEL),
            _box((11.5, 1, 7.4), (14.5, 2.4, 8.6), UV_KETTLE,
                 rotation={"origin": [11.5, 1.7, 8], "axis": "z", "angle": 22.5}),
        ]
    elif prop == "mug":
        elements = [
            _box((6, -1, 6), (10, 3.5, 10), UV_MUG, up=UV_COFFEE),
            _box((10, 0, 7.5), (11.6, 2.6, 8.5), UV_MUG),
        ]
    else:
        # Hung on a hook on the wall (the wall is on the south side as it faces north).
        elements = [
            _box((7.4, 13, 14.6), (8.6, 15.2, 16), UV_STEEL),
            _box((2.5, 0.5, 13), (13.5, 13.5, 15.4), UV_JACKET),
            _box((3, 13.5, 13.4), (13, 14.4, 15.2), UV_JACKET),
            _box((5.5, 13, 12.8), (10.5, 14.8, 15.4), UV_LINING),
            _box((1.4, 1.5, 13.3), (2.9, 12.8, 15.1), UV_JACKET),
            _box((13.1, 1.5, 13.3), (14.6, 12.8, 15.1), UV_JACKET),
        ]
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "textures": {"particle": _ref("l2_chamber_props"), "sheet": _ref("l2_chamber_props")},
            "elements": elements}


def tex_l2chambers_models() -> None:
    root = G.ROOT
    # The heart gauge: exactly the corridor gauge, with the needle-less dial.
    gauge = json.loads((root / "models" / "block" / "l2_gauge.json").read_text())
    gauge["textures"]["dial"] = _ref("l2_heart_dial")
    _write_json(root / "models" / "block" / "l2_heart_gauge.json", gauge)
    _write_json(root / "blockstates" / "l2_heart_gauge.json", {"variants": {
        f"facing={f}": ({"model": "backrooms:block/l2_heart_gauge", "y": y} if y else {"model": "backrooms:block/l2_heart_gauge"})
        for f, y in FACING_Y.items()}})
    _write_json(root / "models" / "item" / "l2_heart_gauge.json", {"parent": "backrooms:block/l2_heart_gauge"})
    variants = {}
    for prop in PROPS:
        _write_json(root / "models" / "block" / f"l2_chamber_prop_{prop}.json", _prop_model(prop))
        for f, y in FACING_Y.items():
            v = {"model": f"backrooms:block/l2_chamber_prop_{prop}"}
            if y:
                v["y"] = y
            variants[f"facing={f},prop={prop}"] = v
    _write_json(root / "blockstates" / "l2_chamber_prop.json", {"variants": variants})
    _write_json(root / "models" / "item" / "l2_chamber_prop.json", {"parent": "backrooms:block/l2_chamber_prop_logbook"})


TEXTURES = [tex_l2chambers_textures, tex_l2chambers_models]


# =============================================================================
# Previews (not run by gen_assets): plans of the chambers and detours
# =============================================================================

JAVA = G.Path(__file__).resolve().parents[2] / "src" / "main" / "java" / "com" / "rickr" / "backrooms" / "world" / "layout"

FLOOR_COLOURS = {
    ".": (70, 68, 62), "S": (120, 126, 124), "W": (96, 74, 52), "Y": (150, 110, 60), "Z": (150, 110, 60),
    "C": (150, 120, 80), "K": (80, 96, 110), "B": (100, 104, 100), "M": (60, 90, 70), "P": (50, 110, 60),
    "H": (50, 110, 60), "J": (110, 112, 108), "V": (170, 60, 50), "X": (200, 40, 30), "T": (120, 84, 50),
    "U": (120, 84, 50), "h": (140, 100, 60), "m": (150, 140, 120), "j": (210, 110, 40), "~": (20, 30, 40),
    "=": (130, 130, 130),
}
LEGEND = {
    "S": "shelving", "W": "workbench", "Y": "bench + LOGBOOK", "Z": "bench + kettle", "C": "crate (loot)",
    "K": "locker (loot)", "B": "breaker panel", "M": "machine cabinet", "P": "pump set", "H": "sump pump in water",
    "J": "junction box", "V": "valve station", "X": "extinguisher", "T": "table + LOGBOOK", "U": "table + mug",
    "h": "chair", "m": "mattress", "j": "jacket on hook", "~": "black water", "=": "grating walkway",
}


def _java_plans():
    """The chambers' drawings, read straight from Level2Chambers.java."""
    src = (JAVA / "Level2Chambers.java").read_text()
    plans = {}
    for m in re.finditer(r"private static final String\[\] (\w+) = \{(.*?)\};", src, re.S):
        rows = [r.encode().decode("unicode_escape") for r in re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(2))]
        plans[m.group(1)] = rows
    return plans


def _draw_plan(name, floor, over, cell=34):
    from PIL import Image, ImageDraw
    w = len(floor[0])
    h = len(floor)
    pad = 40
    img = Image.new("RGB", (w * cell + 2 * pad + 300, (h + 2) * cell + 2 * pad + 20), (24, 24, 26))
    d = ImageDraw.Draw(img)
    d.text((pad, 8), f"{name} (north-side room seen from above; door under the middle column)", fill=(230, 230, 230))
    for row in range(h + 2):
        for col in range(w + 2):
            x0 = pad + col * cell
            y0 = pad + row * cell
            wall = row == 0 or row == h + 1 or col == 0 or col == w + 1
            if wall:
                colour = (150, 146, 136) if not (row == h + 1 and col == w // 2 + 1) else (90, 120, 150)
                d.rectangle([x0, y0, x0 + cell - 1, y0 + cell - 1], fill=colour)
                if row == h + 1 and col == w // 2 + 1:
                    d.text((x0 + 6, y0 + 10), "door", fill=(255, 255, 255))
                continue
            ch = floor[row - 1][col - 1]
            d.rectangle([x0, y0, x0 + cell - 1, y0 + cell - 1], fill=FLOOR_COLOURS.get(ch, (255, 0, 255)), outline=(40, 40, 40))
            if ch != ".":
                d.text((x0 + cell // 2 - 3, y0 + 4), ch, fill=(255, 255, 255))
            oc = over[row - 1][col - 1]
            if oc != ".":
                d.text((x0 + cell // 2 - 3, y0 + cell - 16), oc, fill=(255, 230, 120))
    lx = pad + (w + 2) * cell + 16
    used = sorted({c for r in floor for c in r if c != "."})
    d.text((lx, pad), "floor (white)", fill=(230, 230, 230))
    for k, ch in enumerate(used):
        d.rectangle([lx, pad + 18 + k * 16, lx + 12, pad + 30 + k * 16], fill=FLOOR_COLOURS[ch])
        d.text((lx + 18, pad + 18 + k * 16), f"{ch} {LEGEND.get(ch, '')}", fill=(220, 220, 220))
    y = pad + 30 + len(used) * 16
    d.text((lx, y), "overhead (yellow):", fill=(255, 230, 120))
    d.text((lx, y + 16), "- | tube  = \" pipe  # % tray", fill=(220, 220, 220))
    d.text((lx, y + 32), "L lamp  E/R battery lamp  o dead", fill=(220, 220, 220))
    d.text((lx, y + 48), "g gauge  w valve  n stencil  d drip", fill=(220, 220, 220))
    return img


def _draw_detours(cell=18):
    """Top views of the four detours, from the formulas in Level2Detours (side = south, mid = column 0)."""
    from PIL import Image, ImageDraw
    cols = list(range(-14, 15))
    rows = list(range(-5, 9))
    C = {"#": (150, 146, 136), ".": (70, 68, 62), "P": (90, 80, 70), "c": (190, 186, 176), "t": (14, 14, 18),
         "g": (130, 130, 130), "d": (200, 60, 50), "s": (110, 110, 110), "b": (60, 60, 160), "l": (220, 200, 90),
         "x": (40, 90, 140), "u": (170, 170, 180)}

    def dogleg(m, q):
        am = abs(m)
        if am <= 5:
            if am == 5 and 3 <= q <= 6:
                return "c"
            if am <= 1 and -2 <= q <= 1:
                return "c"
            if 2 <= q <= 5:
                return "."
            if q == 6:
                return "P"
            if q == 7:
                return "#"
        return base(q)

    def duct(m, q):
        if -2 <= m <= 1 and abs(q) <= 3:
            return "u" if m in (-1, 0) else "s"
        return base(q)

    def catwalk(m, q):
        if abs(m) <= 8:
            if -3 <= q <= -1:
                return "l" if (m == -8 and q == -1) else "t"
            if q == -4:
                return "#"
            if 0 <= q <= 2:
                return "g"
        return base(q)

    def bypass(m, q):
        am = abs(m)
        if am < 5 and abs(q) <= 3:
            return "x"
        if am == 5 and abs(q) <= 3:
            return "d" if q == 0 else "c"
        if am <= 7 and q == 5:
            return "."
        if am == 7 and q in (3, 4):
            return "."
        return base(q)

    def base(q):
        a = abs(q)
        if a <= 2:
            return "."
        if a == 3:
            return "P"
        return "#"

    panels = [("dog-leg", dogleg), ("duct crossing (soffit s, duct u overhead)", duct),
              ("catwalk (g grating, t open trench, l ladder)", catwalk), ("bypass (x sealed, d locked door)", bypass)]
    pw = len(cols) * cell
    ph = len(rows) * cell
    img = Image.new("RGB", (pw * 2 + 60, ph * 2 + 100), (24, 24, 26))
    d = ImageDraw.Draw(img)
    for k, (title, fn) in enumerate(panels):
        ox = 20 + (k % 2) * (pw + 20)
        oy = 30 + (k // 2) * (ph + 40)
        d.text((ox, oy - 16), title + "  (x along, detour side down)", fill=(230, 230, 230))
        for r, q in enumerate(rows):
            for cidx, m in enumerate(cols):
                ch = fn(m, q)
                d.rectangle([ox + cidx * cell, oy + r * cell, ox + (cidx + 1) * cell - 1, oy + (r + 1) * cell - 1],
                            fill=C[ch], outline=(36, 36, 36))
    return img


def preview(out_dir):
    out = G.Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    plans = _java_plans()
    names = {"PUMP": "pump_room", "GALLERY": "valve_gallery", "ELECTRICAL": "electrical", "SUMP": "sump", "BREAK": "break_room"}
    images = []
    for key, name in names.items():
        img = _draw_plan(name, plans[f"{key}_FLOOR"], plans[f"{key}_OVER"])
        img.save(out / f"plan_{name}.png")
        images.append(img)
    det = _draw_detours()
    det.save(out / "detours.png")
    from PIL import Image
    sheet_w = max(i.width for i in images)
    sheet = Image.new("RGB", (sheet_w, sum(i.height for i in images)), (24, 24, 26))
    y = 0
    for i in images:
        sheet.paste(i, (0, y))
        y += i.height
    sheet.save(out / "plans_sheet.png")
    # The props sheet and the dial, enlarged so you can see them.
    for tex in ("l2_chamber_props", "l2_heart_dial"):
        p = _blk(tex)
        if p.exists():
            Image.open(p).resize((256, 256), Image.NEAREST).save(out / f"{tex}_x4.png")
    print("previews in", out)

