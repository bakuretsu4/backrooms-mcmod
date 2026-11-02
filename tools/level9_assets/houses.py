"""Level 9 houses (Houses slice): textures, block models, blockstates and item models.

Everything the house plans and lived-in interiors of world/layout/Level9Houses need: the TV left
on (soft animated static), dinner on the table, unmade beds, a child's bed and toys, framed family
photos (and the same photos with the faces scratched out), boarded windows, dust, dust sheets,
the basement's water heater and washer, the half-built house's studs, lumber, plywood, sawhorse
and tarp, and the upside-down room's ceiling furniture (the normal furniture models turned over).

Run from the project root:  python tools/level9_assets/houses.py
Every texture has its own fixed random seed (9400-9499), so re-running gives identical files.
Textures are 64 px (half the mod's usual 128) to save room in the block atlas; they are small
props seen up close only in passing. JSON files are written with LF line ends like the other
generated models.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))   # tools/, for gen_assets

import gen_assets as G  # noqa: E402
from anomaly_assets import level39_props as P  # noqa: E402  (model/blockstate helpers)

N = 64                       # texture size of this module
SH = (N, N)
MODELS = G.ROOT / "models" / "block"


def _blk(name: str) -> Path:
    return G.TEX / "block" / f"{name}.png"


def _ref(name: str) -> str:
    return f"backrooms:block/{name}"


def _noise(rng, beta=2.0, aniso=(1.0, 1.0), kmin=1.0):
    return G.fnoise(rng, SH, beta=beta, aniso=aniso, kmin=kmin)


def _save(img, name):
    G.save_png(img, _blk(name))


def _load_model(name):
    return json.loads((MODELS / f"{name}.json").read_text())


# =============================================================================
# Textures
# =============================================================================

def tex_tv_static() -> None:
    """l9_tv_static: 16 frames of soft grey-blue snow (32 px) with a slow bright band rolling
    down, cut to the rounded CRT glass (cutout alpha). Dim on purpose: it glows full-bright."""
    rng = G.rng_for(9400)
    n, frames = 32, 16
    X, Y = G.grid((n, n))
    glass = (((X - 16) / 16) ** 4 + ((Y - 16) / 16) ** 4) < 1.0
    out = np.zeros((n * frames, n, 4))
    for f in range(frames):
        snow = rng.normal(0, 1, (n, n))
        rows = rng.normal(0, 0.5, (n, 1))                     # faint horizontal tearing
        band_y = (f / frames) * (n + 12) - 6
        band = np.exp(-((Y - band_y) / 3.0) ** 2)
        lum = 104 + 26 * snow + 10 * rows + 26 * band
        vign = 1.0 - 0.35 * (((X - 16) / 16) ** 2 + ((Y - 16) / 16) ** 2)
        lum = lum * vign
        frame = np.stack([lum * 0.92, lum * 0.97, lum * 1.08], axis=-1)
        alpha = np.where(glass, 255.0, 0.0)
        out[f * n:(f + 1) * n] = np.concatenate([frame, alpha[..., None]], axis=-1)
    G.save_png(out, _blk("l9_tv_static"))
    _blk("l9_tv_static").with_suffix(".png.mcmeta").write_text(
        json.dumps({"animation": {"frametime": 2, "interpolate": False}}, indent=2) + "\n", newline="\n")


def tex_dinner() -> None:
    """l9_dinner: the dinner seen from above, laid out at the table-top positions the model's
    elements use (4 px per model pixel): two plates (meatloaf, mash and peas; spaghetti half
    eaten), a glass of milk, a knife and fork. The top rows are the food's sides."""
    rng = G.rng_for(9401)
    X, Y = G.grid(SH)
    img = G.fill((96, 66, 42), SH)                           # table wood, mostly never seen
    img = G.add_lum(img, _noise(rng, beta=2.0, aniso=(8, 1)) * 6)
    img[:6] = G.add_lum(G.fill((128, 84, 54), (6, N)), rng.normal(0, 6, (6, N)))   # food sides
    for cx, cy in ((18, 22), (46, 42)):                       # plates: rim and well
        d = np.hypot(X - cx, Y - cy)
        img = G.over(img, np.array([226, 222, 208]), G.smoothstep(10.6, 9.6, d))
        img = G.add_lum(img, -14 * G.smoothstep(1.2, 0.2, np.abs(d - 7.2)))
    # Plate 1: a slab of meatloaf, a heap of mash, peas.
    img = G.over(img, np.array([112, 62, 42]), G.box(SH, 12, 16, 22, 24) * (0.8 + 0.2 * (rng.random(SH) > 0.5)))
    img = G.over(img, np.array([226, 214, 170]), G.smoothstep(4.2, 3.2, np.hypot(X - 22, Y - 27)))
    for _ in range(9):
        px, py = rng.uniform(12, 20), rng.uniform(25, 29)
        img = G.over(img, np.array([86, 128, 54]), G.smoothstep(1.3, 0.6, np.hypot(X - px, Y - py)))
    # Plate 2: spaghetti in red sauce, a fork-twirled gap where someone stopped eating.
    pasta = G.smoothstep(6.5, 5.5, np.hypot(X - 46, Y - 42)) * (G.smoothstep(2.2, 3.2, np.hypot(X - 50, Y - 46)))
    strands = 0.5 + 0.5 * np.sin((X + 0.6 * Y) * 1.7 + 3 * _noise(rng, beta=2.5))
    img = G.over(img, np.array([214, 178, 104]) * 0.9 + 20 * strands[..., None], pasta)
    img = G.over(img, np.array([150, 40, 30]), pasta * G.smoothstep(0.4, 1.2, _noise(rng, beta=2.2)) * 0.8)
    # Milk glass (rim ring and milk) and the knife and fork.
    dg = np.hypot(X - 43, Y - 15)
    img = G.over(img, np.array([238, 236, 228]), G.smoothstep(3.4, 2.6, dg))
    img = G.add_lum(img, 14 * G.smoothstep(0.8, 0.0, np.abs(dg - 2.8)))
    img = G.over(img, np.array([176, 178, 182]), G.box(SH, 30.5, 13, 31.8, 31))
    img = G.over(img, np.array([176, 178, 182]), G.box(SH, 32.6, 33, 33.8, 51))
    _save(img, "l9_dinner")


def _patchwork(rng, pal) -> np.ndarray:
    X, Y = G.grid(SH)
    idx = rng.integers(0, len(pal), (4, 4))
    img = np.asarray(pal, float)[idx[(Y // 16).astype(int), (X // 16).astype(int)]]
    img = G.add_lum(img, rng.normal(0, 3, SH) + _noise(rng, beta=2.6) * 5)
    stitch = np.minimum(np.abs(G.wrapd(X, 16)), np.abs(G.wrapd(Y, 16)))
    return G.add_lum(img, -16 * G.smoothstep(1.0, 0.3, stitch))


def tex_beds() -> None:
    """Unmade bed: the quilt pushed down in creases (l9_quilt_rumpled) and the bare, crumpled
    sheet left behind (l9_sheet_rumpled)."""
    rng = G.rng_for(9402)
    X, Y = G.grid(SH)
    pal = [(132, 100, 84), (104, 112, 96), (150, 134, 104), (96, 92, 110), (140, 116, 98)]
    quilt = _patchwork(rng, pal)
    folds = np.sin((Y + 6 * _noise(rng, beta=3.0)) / 64 * 2 * np.pi * 5)
    quilt = quilt * (0.84 + 0.16 * folds)[..., None]
    _save(quilt, "l9_quilt_rumpled")
    sheet = G.fill((204, 200, 188), SH)
    crumple = np.abs(_noise(rng, beta=2.2, kmin=2))
    sheet = G.add_lum(sheet, -26 * G.smoothstep(0.25, 0.0, crumple) + _noise(rng, beta=3.0) * 6)
    _save(sheet, "l9_sheet_rumpled")


def tex_kids_quilt() -> None:
    """l9_kids_quilt: a faded sky-blue child's quilt with yellow stars and a stitched border."""
    rng = G.rng_for(9403)
    X, Y = G.grid(SH)
    img = G.fill((112, 150, 184), SH)
    img = G.add_lum(img, _noise(rng, beta=2.6) * 6 + rng.normal(0, 2, SH))
    for _ in range(9):
        cx, cy, r = rng.uniform(4, 60), rng.uniform(4, 60), rng.uniform(3.0, 4.5)
        th = np.arctan2(Y - cy, X - cx)
        star = np.hypot(X - cx, Y - cy) < r * (0.55 + 0.45 * np.cos(5 * th) ** 2)
        img = G.over(img, np.array([226, 200, 96]), star * 0.9)
    edge = np.minimum(np.minimum(X, N - X), np.minimum(Y, N - Y))
    img = G.add_lum(img, 22 * ((edge > 2) & (edge < 3.2) & (((X + Y).astype(int) % 4) < 2)))
    _save(img * 0.94, "l9_kids_quilt")


def tex_toys() -> None:
    """l9_toys, four 32 px quarters: teddy fur, a red letter block, a blue letter block, a
    red-and-white rubber ball."""
    rng = G.rng_for(9404)
    q = (32, 32)
    X, Y = G.grid(q)
    fur = G.add_lum(G.fill((150, 108, 66), q), rng.normal(0, 9, q) + G.fnoise(rng, q, beta=2.4) * 8)

    def letter_block(col, ch, seed):
        b = G.add_lum(G.fill(col, q), G.fnoise(G.rng_for(seed), q, beta=2.0) * 6)
        b = G.add_lum(b, -26 * (np.minimum(np.minimum(X, 32 - X), np.minimum(Y, 32 - Y)) < 2))
        return G.over(b, np.array([236, 228, 200]), G._text(ch, 16, 17, 22, maxw=24, shape=q))

    red = letter_block((176, 52, 44), "A", 9405)
    blue = letter_block((60, 86, 160), "B", 9406)
    ball = np.where(((np.floor((X + Y) / 8)) % 2 == 0)[..., None], np.array([198, 48, 40.0]), np.array([230, 226, 214.0]))
    ball = G.add_lum(ball, G.fnoise(rng, q, beta=2.4) * 5)
    img = np.zeros((N, N, 3))
    img[:32, :32], img[:32, 32:], img[32:, :32], img[32:, 32:] = fur, red, blue, ball
    _save(img, "l9_toys")


# ---- family photos ---------------------------------------------------------------------------

PHOTO_SCENES = (
    # (background top, background bottom, [(x, head y, body colour, height)] people)
    ((128, 160, 176), (92, 124, 70), [(18, 24, (60, 72, 112), 1.0), (32, 22, (150, 64, 60), 1.05), (45, 33, (196, 160, 70), 0.6)]),
    ((168, 150, 120), (140, 120, 96), [(24, 22, (40, 38, 40), 1.05), (40, 24, (232, 228, 216), 1.0)]),
    ((190, 116, 60), (120, 74, 44), [(14, 26, (90, 120, 80), 1.0), (27, 24, (150, 120, 60), 1.0), (39, 34, (180, 70, 70), 0.6), (50, 36, (70, 100, 150), 0.55)]),
)


def tex_family_photos() -> None:
    """family_photo_N (and _scratched): faded 1970s snapshots in a dark wooden frame with a cream
    mat. 0: three people outdoors, 1: the wedding photo, 2: four on the orange-wallpaper sofa.
    The scratched copies have every face gouged out to the white paper underneath."""
    X, Y = G.grid(SH)
    for i, (top, bottom, people) in enumerate(PHOTO_SCENES):
        rng = G.rng_for(9410 + i)
        t = np.clip((Y - 10) / 44, 0, 1)[..., None]
        img = G.lerp(np.array(top, float), np.array(bottom, float), t)
        img = G.add_lum(img, _noise(rng, beta=2.6) * 6)
        heads = []
        for x, hy, body, h in people:
            r = 4.6 * h + 1.2
            torso = G.supersample(SH, lambda xx, yy, x=x, hy=hy, r=r: (np.abs(xx - x) < r * 1.25) & (yy > hy + r * 0.8))
            img = G.over(img, np.array(body, float), torso)
            head = G.supersample(SH, lambda xx, yy, x=x, hy=hy, r=r: np.hypot(xx - x, yy - hy) < r)
            img = G.over(img, np.array([214, 172, 140], float), head)
            hair = G.supersample(SH, lambda xx, yy, x=x, hy=hy, r=r: (np.hypot(xx - x, yy - hy + 1.2) < r) & (yy < hy - r * 0.35))
            img = G.over(img, np.array([84, 60, 40], float), hair)
            heads.append((x, hy, r))
        # Fade: warm cast, lifted blacks, a little grain.
        img = G.lerp(img, np.array([220, 196, 150.0]), 0.22)
        img = G.add_lum(img, rng.normal(0, 3, SH))
        clean = _frame(img)
        _save(clean, f"family_photo_{i}")
        scratched = img.copy()
        for x, hy, r in heads:
            for _ in range(14):
                a = rng.uniform(0, np.pi)
                cx, cy = x + rng.normal(0, r * 0.4), hy + rng.normal(0, r * 0.4)
                L = rng.uniform(r * 1.2, r * 2.4)
                dx, dy = np.cos(a), np.sin(a)
                along = (X - cx) * dx + (Y - cy) * dy
                off = np.abs(-(X - cx) * dy + (Y - cy) * dx)
                line = (np.abs(along) < L / 2) * G.smoothstep(1.1, 0.3, off)
                scratched = G.over(scratched, np.array([236, 232, 222.0]), line * rng.uniform(0.7, 1.0))
        _save(_frame(scratched), f"family_photo_{i}_scratched")


def _frame(photo: np.ndarray) -> np.ndarray:
    """Dark wooden frame (5 px) and a cream mat (3 px) round a photo."""
    rng = G.rng_for(9419)
    X, Y = G.grid(SH)
    edge = np.minimum(np.minimum(X, N - X), np.minimum(Y, N - Y))
    wood = G._wood(rng, np.array([70, 46, 30]), horizontal=True, shape=SH)
    img = np.where((edge < 8)[..., None], np.array([214, 204, 180.0]), photo)
    img = np.where((edge < 5)[..., None], wood, img)
    return G.add_lum(img, 18 * ((edge > 4) & (edge < 5.2)) - 14 * ((edge > 7.6) & (edge < 8.6)))


# ---- walls, dust, cloth ----------------------------------------------------------------------

def tex_boarded_window() -> None:
    """l9_boarded_window (128 px, like house_window): the house window with three grey weathered
    boards nailed across it, one of them crooked."""
    rng = G.rng_for(9420)
    from PIL import Image
    base = np.asarray(Image.open(_blk("house_window")).convert("RGB"), float)
    S = base.shape[0]
    X, Y = G.grid((S, S))
    img = base
    for k, (y0, tilt) in enumerate(((22, 0.0), (56, -0.12), (90, 0.05))):
        yc = y0 + (X - S / 2) * tilt
        board = (np.abs(Y - yc) < 11) & (X > 4) & (X < S - 4)
        wood = G._wood(G.rng_for(9421 + k), np.array([128, 118, 102]), horizontal=True, shape=(S, S))
        wood = G.add_lum(wood, -22 * G.smoothstep(9, 11, np.abs(Y - yc)))
        img = np.where(board[..., None], wood, img)
        for nx in (12, S - 12):
            nail = np.hypot(X - nx, Y - (y0 + (nx - S / 2) * tilt)) < 2.2
            img = np.where(nail[..., None], np.array([60, 56, 52.0]), img)
    img = G._weather(img, rng, dirt=0.25, scratches=4)
    G.save_png(img, _blk("l9_boarded_window"))


def tex_house_dust() -> None:
    """l9_house_dust: grey fluff and dust lying in drifts (cutout: the floor shows between)."""
    rng = G.rng_for(9430)
    cover = G.smoothstep(0.35, 0.9, _noise(rng, beta=2.4)) * (0.6 + 0.4 * (rng.random(SH) > 0.3))
    col = G.add_lum(G.fill((150, 146, 138), SH), rng.normal(0, 8, SH))
    alpha = np.where(cover > 0.45, 255.0, 0.0)
    G.save_png(np.concatenate([col, alpha[..., None]], axis=-1), _blk("l9_house_dust"))


def tex_dust_sheet() -> None:
    """l9_dust_sheet: off-white cotton thrown over furniture, long soft folds, greyed with dust."""
    rng = G.rng_for(9431)
    X, Y = G.grid(SH)
    img = G.fill((206, 200, 186), SH)
    folds = np.sin((X + 8 * _noise(rng, beta=3.0)) / 64 * 2 * np.pi * 3)
    img = img * (0.88 + 0.12 * folds)[..., None]
    img = G.add_lum(img, rng.normal(0, 2.5, SH))
    img = G.over(img, np.array([150, 146, 136]), G.smoothstep(0.2, 1.6, _noise(rng, beta=2.2)) * 0.45)
    _save(img, "l9_dust_sheet")


# ---- basement ----------------------------------------------------------------------------------

def tex_basement() -> None:
    """Water heater (white enamel tank, a yellowed rating sticker, rust at the foot; its top) and
    the washer (front with the lid seam and a chrome badge, the lid, the dial panel)."""
    rng = G.rng_for(9440)
    X, Y = G.grid(SH)
    enamel = G._smooth_concrete(rng, (208, 204, 190), mottle=2.0, fine=0.5, shape=SH)
    # Round tank: brightest down the middle, darker toward both edges.
    tank = enamel * (0.72 + 0.28 * np.cos((X - 32) / 64 * np.pi) ** 0.5)[..., None]
    tank = G.over(tank, np.array([214, 200, 140]), G.box(SH, 20, 14, 44, 30))
    for row in (18, 22, 26):
        tank = G.add_lum(tank, -40 * G.box(SH, 23, row, 41, row + 1))
    tank = G.over(tank, np.array([120, 70, 40]), G.smoothstep(52, 62, Y) * G.smoothstep(0.0, 1.0, _noise(rng, beta=1.4)) * 0.8)
    _save(G._weather(tank, rng, dirt=0.15, scratches=2), "l9_water_heater")
    top = G.add_lum(G.fill((150, 148, 140), SH), _noise(rng, beta=2.4) * 5)
    d = np.hypot(X - 32, Y - 32)
    top = G.add_lum(top, -30 * G.smoothstep(1.4, 0.2, np.abs(d - 26)) - 20 * (d < 6))
    _save(top, "l9_water_heater_top")
    front = enamel.copy()
    front = G.add_lum(front, -36 * G.smoothstep(1.2, 0.3, np.abs(Y - 10)))
    front = G.over(front, np.array([186, 190, 196]), G.box(SH, 22, 16, 42, 21))
    _save(G._weather(front, rng, dirt=0.12, scratches=2), "l9_washer_front")
    lid = enamel.copy()
    lid = G.add_lum(lid, -30 * (G.box(SH, 8, 6, 56, 50) - G.box(SH, 9.5, 7.5, 54.5, 48.5)))
    _save(lid, "l9_washer_top")
    panel = enamel.copy()
    panel[:16] = G.add_lum(G.fill((70, 66, 60), (16, N)), rng.normal(0, 3, (16, N)))
    for cx in (12, 32, 52):
        knob = np.hypot(X[:16] - cx, Y[:16] - 8) < 5
        panel[:16] = np.where(knob[..., None], np.array([206, 204, 196.0]), panel[:16])
    _save(panel, "l9_washer_panel")


# ---- the half-built house ------------------------------------------------------------------------

def tex_lumber() -> None:
    """Raw kiln-dried pine: the side grain (l9_lumber), end grain rings (l9_lumber_end), the
    plywood subfloor with its nail lines and a mill stamp (l9_plywood) and a blue tarp."""
    rng = G.rng_for(9450)
    X, Y = G.grid(SH)
    side = G._wood(rng, np.array([200, 166, 116]), horizontal=True, shape=SH)
    side = G.add_lum(side, -26 * G.smoothstep(1.2, 0.2, np.abs(G.wrapd(Y, 16))))     # board edges
    _save(side, "l9_lumber")
    end = G.fill((196, 160, 110), SH)
    rings = np.sin(np.hypot(G.wrapd(X - 8, 16), G.wrapd(Y - 8, 16)) * 2.4 + _noise(rng, beta=2.6))
    end = G.add_lum(end, 14 * rings - 30 * G.smoothstep(1.0, 0.2, np.minimum(np.abs(G.wrapd(X, 16)), np.abs(G.wrapd(Y, 16)))))
    _save(end, "l9_lumber_end")
    ply = G.fill((196, 170, 124), SH)
    ply = G.add_lum(ply, _noise(rng, beta=2.0, aniso=(6, 1)) * 12 + _noise(rng, beta=3.0) * 8)
    ply = G.over(ply, np.array([150, 112, 70]), G.smoothstep(1.0, 2.2, _noise(rng, beta=1.8, aniso=(5, 1))) * 0.5)
    for nx in (2, 34):
        ply = G.add_lum(ply, -50 * (((Y.astype(int) % 8) == 3) & (np.abs(X - nx) < 1.0)))
    ply = G.over(ply, np.array([60, 70, 120]), G._text("APA", 44, 50, 9, maxw=18, shape=SH) * 0.5)
    _save(ply, "l9_plywood")
    tarp = G.fill((44, 86, 156), SH)
    weave = ((X.astype(int) + Y.astype(int)) % 2) * 2 - 1
    tarp = G.add_lum(tarp, weave * 4 + 16 * np.sin((X - 0.5 * Y + 6 * _noise(rng, beta=3)) / 64 * 2 * np.pi * 3))
    tarp = G.over(tarp, np.array([170, 170, 170]), (np.hypot(G.wrapd(X - 8, 32), G.wrapd(Y - 4, 64)) < 2.4) * 0.9)
    _save(tarp, "l9_tarp")


# =============================================================================
# Models and blockstates
# =============================================================================

FACES = P.FACES
FACING_Y = P.FACING_Y


def _el(f, t, tex, uv=None, faces=FACES, rot=None):
    return P._el(f, t, tex, faces=faces, uv=uv, rot=rot, cull=False)


def _furniture(name, textures, elements, render=None) -> None:
    """A FurnitureBlock-style block: one model facing north, turned by the blockstate."""
    P._bm(name, P._model(textures, elements, render=render, ao=False))
    P._facing_state(name)
    P._im(name)


def models_photos() -> None:
    variants = {}
    for i in range(3):
        for scratched in (False, True):
            name = f"family_photo_{i}" + ("_scratched" if scratched else "")
            tex = {"particle": _ref(name), "photo": _ref(name), "frame": _ref("tv_cabinet")}
            el = _el((4.5, 3, 15), (11.5, 12, 16), {"north": "#photo", "south": "#frame", "east": "#frame",
                                                    "west": "#frame", "up": "#frame", "down": "#frame"},
                     uv={"north": [0, 0, 16, 16]})
            P._bm(name, P._model(tex, [el], render=None, ao=False))
            for facing, y in FACING_Y.items():
                v = {"model": _ref(name)}
                if y:
                    v["y"] = y
                variants[f"facing={facing},photo={i},scratched={'true' if scratched else 'false'}"] = v
    P._bs("family_photo", {"variants": variants})
    P._im("family_photo", parent=_ref("family_photo_0"))


def models_tv_on() -> None:
    """The plain TV model plus a full-bright plane of static over its glass (the glass of
    tv_screen spans u 1.75-10.75, v 2.75-12.75 of the 10 px screen face at x 3-13, y 6-16)."""
    m = _load_model("tv")
    m["render_type"] = "minecraft:cutout"
    m["textures"]["static"] = _ref("l9_tv_static")
    m["elements"].append({
        "from": [6.28, 8.03, 3.4], "to": [11.91, 14.28, 3.45], "shade": False,
        "faces": {"north": {"texture": "#static", "uv": [0, 0, 16, 16],
                            "neoforge_data": {"block_light": 15, "sky_light": 15}}}})
    P._bm("l9_tv_on", m)
    P._facing_state("l9_tv_on")
    P._im("l9_tv_on")


def models_dinner() -> None:
    m = _load_model("dining_table")
    m["textures"].update({"dinner": _ref("l9_dinner"), "rim": _ref("house_trim")})
    side4 = ("north", "south", "east", "west")
    plate = lambda f, t: _el(f, t, {**{s: "#rim" for s in side4}, "up": "#dinner", "down": "#rim"})  # noqa: E731
    food = lambda f, t: _el(f, t, "#dinner", faces=side4 + ("up",))  # noqa: E731
    m["elements"] += [
        plate((2, 14.5, 3), (7, 14.9, 8)),
        plate((9, 14.5, 8), (14, 14.9, 13)),
        food((3, 14.9, 4), (5.5, 15.8, 6)),            # meatloaf
        food((4.5, 14.9, 6), (6.2, 15.6, 7.6)),        # mash
        food((10, 14.9, 9), (13, 15.5, 12)),           # spaghetti
        _el((10, 14.5, 3), (11.5, 17, 4.5), {**{s: "#rim" for s in side4}, "up": "#dinner", "down": "#rim"}),  # milk glass
        _el((7.6, 14.5, 3.25), (7.95, 14.62, 7.75), "#rim", faces=("up",)),     # knife
        _el((8.15, 14.5, 8.25), (8.45, 14.62, 12.75), "#rim", faces=("up",)),   # fork
    ]
    P._bm("l9_dinner_table", m)
    P._facing_state("l9_dinner_table")
    P._im("l9_dinner_table")


def models_unmade_bed() -> None:
    """bed_foot_unmade / bed_head_unmade: the quilt shoved down into a heap at the foot, the
    crumpled sheet showing at the head, the pillow knocked askew. Rewrites bed.json with them."""
    foot = _load_model("bed_foot")
    foot["textures"]["quilt"] = _ref("l9_quilt_rumpled")
    foot["textures"]["particle"] = _ref("l9_quilt_rumpled")
    foot["elements"].append(_el((0, 9, 0.5), (16, 11.2, 6), "#quilt", faces=("north", "south", "east", "west", "up")))
    P._bm("bed_foot_unmade", foot)
    head = _load_model("bed_head")
    head["textures"]["quilt"] = _ref("l9_sheet_rumpled")
    head["textures"]["particle"] = _ref("l9_sheet_rumpled")
    elements = []
    for e in head["elements"]:
        tex = {f["texture"] for f in e["faces"].values()}
        if e["from"] == [0, 9, 7] and tex == {"#pillow"}:
            continue                                    # the folded-back sheet edge: gone
        if e["from"] == [2, 8.5, 1.5]:
            e["rotation"] = {"origin": [8, 9.5, 3.5], "axis": "y", "angle": 22.5}   # pillow askew
        if tex == {"#quilt"}:
            e["to"] = [16, 8.6, 16]                     # just the sheet now, lower than the quilt
        elements.append(e)
    head["elements"] = elements
    P._bm("bed_head_unmade", head)
    variants = {}
    for facing, y in FACING_Y.items():
        for half in ("false", "true"):
            for unmade in ("false", "true"):
                model = ("bed_head" if half == "true" else "bed_foot") + ("_unmade" if unmade == "true" else "")
                v = {"model": _ref(model)}
                if y:
                    v["y"] = y
                variants[f"facing={facing},head={half},unmade={unmade}"] = v
    P._bs("bed", {"variants": variants})


def models_kids() -> None:
    tex = {"particle": _ref("l9_kids_quilt"), "frame": _ref("tv_cabinet"), "quilt": _ref("l9_kids_quilt"),
           "pillow": _ref("bed_pillow")}
    _furniture("l9_kids_bed", tex, [
        _el((1, 0, 0.5), (15, 4, 15), "#frame"),
        _el((1.5, 4, 0.5), (14.5, 7, 14.5), "#quilt"),
        _el((3, 7, 11), (13, 8.5, 14.3), "#pillow"),
        _el((1, 0, 14.5), (15, 12, 16), "#frame"),
        _el((1, 0, 0), (15, 6, 1), "#frame"),
    ])
    fur, red, blue, ball = [0, 0, 8, 8], [8, 0, 16, 8], [0, 8, 8, 16], [8, 8, 16, 16]
    full = lambda q: {f: q for f in FACES}  # noqa: E731
    t = {"particle": _ref("l9_toys"), "toys": _ref("l9_toys")}
    _furniture("l9_toys", t, [
        _el((3, 0, 8), (7, 4, 11.5), "#toys", uv=full(fur)),            # teddy: body
        _el((3.5, 4, 8.75), (6.5, 7, 11.25), "#toys", uv=full(fur)),    # head
        _el((3.5, 7, 9.5), (4.5, 8, 10.5), "#toys", uv=full(fur)),      # ears
        _el((5.5, 7, 9.5), (6.5, 8, 10.5), "#toys", uv=full(fur)),
        _el((4.5, 4.8, 8.25), (5.5, 5.8, 8.75), "#toys", uv=full(fur)),  # snout
        _el((2.3, 1, 8.8), (3, 3.4, 10.4), "#toys", uv=full(fur)),      # arms
        _el((7, 1, 8.8), (7.7, 3.4, 10.4), "#toys", uv=full(fur)),
        _el((9, 0, 3.5), (11.5, 2.5, 6), "#toys", uv=full(red)),        # letter blocks
        _el((11.5, 0, 5), (14, 2.5, 7.5), "#toys", uv=full(blue)),
        _el((10, 2.5, 4), (12.5, 5, 6.5), "#toys", uv=full(red)),
        _el((4, 0, 2.5), (7, 3, 5.5), "#toys", uv=full(ball)),          # ball
    ])


def models_misc() -> None:
    # Boarded window: like house_window (a trim cube with the window on its FACING side).
    P._bm("l9_boarded_window", {"parent": "minecraft:block/cube", "textures": {
        "particle": _ref("house_trim"), "north": _ref("l9_boarded_window"), "south": _ref("house_trim"),
        "east": _ref("house_trim"), "west": _ref("house_trim"), "up": _ref("house_trim"), "down": _ref("house_trim")}})
    P._facing_state("l9_boarded_window")
    P._im("l9_boarded_window")
    # Dust: the dust pile's 1 px layer with the house dust texture.
    dust = _load_model("dust_pile")
    dust["textures"] = {"particle": _ref("l9_house_dust"), "layer": _ref("l9_house_dust")}
    P._bm("l9_house_dust", dust)
    P._plain_state("l9_house_dust")
    P._im("l9_house_dust")
    # Dust sheet over an armchair.
    t = {"particle": _ref("l9_dust_sheet"), "sheet": _ref("l9_dust_sheet")}
    _furniture("l9_dust_sheet", t, [
        _el((0, 0, 0.5), (16, 3, 16), "#sheet"),
        _el((0.5, 3, 1), (15.5, 9, 15.5), "#sheet"),
        _el((0.5, 9, 11), (15.5, 14.5, 15.5), "#sheet"),
        _el((0.5, 9, 1.5), (2.8, 12, 11), "#sheet"),
        _el((13.2, 9, 1.5), (15.5, 12, 11), "#sheet"),
    ])
    # Water heater and washer.
    t = {"particle": _ref("l9_water_heater"), "side": _ref("l9_water_heater"), "top": _ref("l9_water_heater_top"),
         "metal": _ref("streetlamp_arm")}
    side = {f: "#side" for f in ("north", "south", "east", "west")}
    _furniture("l9_water_heater", t, [
        _el((2, 0, 2), (14, 15, 14), {**side, "up": "#top", "down": "#top"}),
        _el((7, 15, 7), (9, 16, 9), "#metal"),
        _el((7, 2, 1), (9, 4, 2), "#metal"),
    ])
    t = {"particle": _ref("l9_washer_front"), "front": _ref("l9_washer_front"), "top": _ref("l9_washer_top"),
         "side": _ref("fridge_side"), "panel": _ref("l9_washer_panel")}
    _furniture("l9_washer", t, [
        _el((1, 0, 1), (15, 14, 15), {"north": "#front", "south": "#side", "east": "#side", "west": "#side",
                                      "up": "#top", "down": "#side"}, uv={"north": [0, 0, 16, 16], "up": [0, 0, 16, 16]}),
        _el((1, 14, 12), (15, 16, 15), {"north": "#panel", "south": "#side", "east": "#side", "west": "#side",
                                        "up": "#side", "down": "#side"}, uv={"north": [0, 0, 16, 4]}),
    ])


def models_half_built() -> None:
    lumber = {"particle": _ref("l9_lumber"), "side": _ref("l9_lumber"), "end": _ref("l9_lumber_end")}
    stud = _el((6.5, 0, 6.5), (9.5, 16, 9.5), {"north": "#side", "south": "#side", "east": "#side", "west": "#side",
                                                "up": "#end", "down": "#end"})
    P._bm("l9_stud", P._model(lumber, [stud], render=None, ao=False))
    P._plain_state("l9_stud")
    P._im("l9_stud")
    P._cube("l9_lumber", {"end": "l9_lumber_end", "side": "l9_lumber"}, parent="minecraft:block/cube_column")
    P._slab("l9_lumber_slab", "l9_lumber", "l9_lumber")
    P._stairs("l9_lumber_stairs", "l9_lumber")
    P._cube("l9_plywood", "l9_plywood")
    # Sawhorse: a beam along x on two pairs of splayed legs.
    legs = []
    for x0 in (1.5, 13.5):
        for angle in (22.5, -22.5):
            legs.append(_el((x0, 0, 7.25), (x0 + 1, 11, 8.75), "#side", rot=((x0 + 0.5, 11, 8), "x", angle)))
    _furniture("l9_sawhorse", lumber, [_el((0, 10, 6.5), (16, 12, 9.5), {**{f: "#side" for f in FACES},
                                                                        "east": "#end", "west": "#end"})] + legs)
    t = {"particle": _ref("l9_tarp"), "tarp": _ref("l9_tarp")}
    _furniture("l9_tarp", t, [
        _el((0, 0, 0), (16, 7, 16), "#tarp"),
        _el((1.5, 7, 1.5), (13.5, 10, 13), "#tarp"),
        _el((3, 10, 3), (10, 11, 9), "#tarp"),
    ])


CEILING_PIECES = ("sofa", "tv", "dining_table", "wooden_chair", "kitchen_counter", "fridge", "cardboard_boxes")
# Turned over ("x": 180) a north-facing model faces south; then this "y" turns it to FACING.
CEILING_Y = {"south": 0, "west": 90, "north": 180, "east": 270}


def models_ceiling() -> None:
    variants = {}
    for p, model in enumerate(CEILING_PIECES):
        for facing, y in CEILING_Y.items():
            v = {"model": _ref(model), "x": 180}
            if y:
                v["y"] = y
            variants[f"facing={facing},piece={p}"] = v
    P._bs("l9_ceiling_furniture", {"variants": variants})
    P._im("l9_ceiling_furniture", parent=_ref("sofa"))


TEXTURES = [tex_tv_static, tex_dinner, tex_beds, tex_kids_quilt, tex_toys, tex_family_photos,
            tex_boarded_window, tex_house_dust, tex_dust_sheet, tex_basement, tex_lumber]
MODEL_STEPS = [models_photos, models_tv_on, models_dinner, models_unmade_bed, models_kids, models_misc,
               models_half_built, models_ceiling]


def main() -> None:
    for fn in TEXTURES + MODEL_STEPS:
        fn()
        print("ok", fn.__name__)


if __name__ == "__main__":
    main()
