"""The Crimson Field chairlift: the side entrance to Level 39 (textures, models and sounds).

A rare chairlift line stands in Level 9.1's endless cold grass: a small loading station (timber
deck, a sheet-steel housing over the bull wheel, a rime-covered sign), steel lattice towers
marching off into the fog, each taller than the last, and open bench chairs hanging from a dark
steel cable, everything crusted in frost. Ride a chair and it carries you up into a white-out
and out into Level 39's sky (see src/main/java/com/rickr/backrooms/world/anomaly/level39/gondola).

Textures (src/main/resources/assets/backrooms/textures/):
  block/l39_gondola_lattice(_top)   painted tower lattice, rime feathers blown off the members (cutout)
  block/l39_gondola_steel           painted structural steel, chipped and frosted
  block/l39_gondola_frame           the chairs' dark tube frame under a thick frost crust
  block/l39_gondola_seat            navy chair padding, cracked, frosted
  block/l39_gondola_rime            thick rime ice (crust pieces on top of things)
  block/l39_gondola_sheave          a rubber-lined sheave wheel seen side-on (cutout)
  block/l39_gondola_rubber          the wheels' black rubber liners
  block/l39_gondola_bullwheel       the station's spoked bull wheel seen from above (cutout)
  block/l39_gondola_concrete        frosted concrete of the piers
  block/l39_gondola_hood(_top)      riveted oxide-red housing steel; its rime-crusted roof
  block/l39_gondola_deck            frosted timber deck boards
  block/l39_gondola_sign            the station's enamel sign
  entity/l39_gondola_cable          twisted steel rope (the client draws the cable with it)
Models/blockstates/item models: every l39_gondola_* block (LF JSON, built in code like level39_props).
Sounds (sounds/gondola/): wind, cable_creak (loops), sheave_clack, board, whiteout.

Run only these with:  python tools/gen_assets.py l39_gondola
Every generator uses its own fixed random seed (39500-39599), so re-running gives identical files.
"""
from __future__ import annotations

import numpy as np

import gen_assets as G
from anomaly_assets import level39_props as P
from anomaly_assets.level39_audio import _save_ogg

S = G.S
SR = G.SR
SQ2 = np.sqrt(2.0)

#: Rime ice: a cold, faintly blue white.
RIME = np.array([222.0, 230.0, 238.0])
#: Clear glaze ice at the edges of the crust, where the colour underneath shows through.
GLAZE = np.array([164.0, 182.0, 200.0])


def _blk(name: str):
    return G.TEX / "block" / f"{name}.png"


def _ref(name: str) -> str:
    return f"backrooms:block/{name}"


def _snd(name: str):
    return G.SND / "gondola" / f"{name}.ogg"


# =============================================================================
# Surface helpers
# =============================================================================

def _frost(img, rng, amount, bias=None):
    """Frost over a surface: a few coherent patches of rime crust with crisp, slightly ragged
    edges (a rim of clear glaze ice round them, where the colour underneath shows through), a
    faint film of hoar frost that dulls the rest, and the odd sparkling crystal.

    amount 0..1 is the share of the surface the crust covers; bias (an array like the image)
    decides WHERE it sits (top edges, seams, the windward side) without changing how much there
    is. Returns (image, crust mask).
    """
    shape = img.shape[:2]
    n = G.fnoise(rng, shape, beta=2.6) + 0.22 * G.fnoise(rng, shape, beta=1.5, kmin=5)
    if bias is not None:
        n = n + bias
    edge = np.quantile(n, 1.0 - amount) if amount > 0 else n.max() + 1.0
    cover = G.smoothstep(edge - 0.05, edge + 0.07, n)
    depth = G.smoothstep(edge, edge + 0.8, n)  # thicker (brighter, smoother) towards the middle of a patch
    grain = G.blur(G.fnoise(rng, shape, beta=0.6, kmin=16), 0.5)
    rime = G.add_lum(G.fill(RIME, shape), grain * 5 + 8 * depth - 10, (0.96, 0.98, 1.0))
    rim = np.clip(G.smoothstep(edge - 0.25, edge, n) - cover, 0, 1)
    out = G.over(img, GLAZE, rim * 0.45)
    # Hoar frost: a thin pale film over everything, a little thicker where the crust will grow.
    film = 0.10 + 0.12 * G.smoothstep(edge - 1.0, edge, n)
    out = G.over(out, np.array([200.0, 210.0, 222.0]), film)
    out = G.over(out, rime, cover)
    sparkle = rng.random(shape) < 0.0015
    out[sparkle & (cover > 0.5)] = (250.0, 252.0, 255.0)
    return out, cover


def _top_bias(strength, shape=(S, S), start=0.0, end=0.45):
    """More frost toward the top of a texture (rime builds on upper edges)."""
    _, Y = G.grid(shape)
    return strength * G.smoothstep(end, start, Y / shape[0])


def _runs(mask, rng, length=10.0, steps=14) -> np.ndarray:
    """Streaks running down from a mask (rust or melt water), fading with distance."""
    acc = np.zeros_like(mask)
    for k in range(1, steps + 1):
        acc += np.roll(mask, k, axis=0) * np.exp(-k / length)
    wob = G.smoothstep(-0.5, 1.0, G.fnoise(rng, mask.shape, beta=1.2, aniso=(1, 10)))
    return np.clip(acc * 0.35 * wob, 0, 1)


def _paint_steel(rng, paint, chip=0.4, shape=(S, S)) -> np.ndarray:
    """Painted structural steel left out for decades: chalky faded paint, chips down to rusty
    steel, rust running down from the chips."""
    under = P._rust(rng, (70, 64, 60), shape=shape, amount=0.65)
    painted = G._smooth_concrete(rng, paint, mottle=3.5, fine=1.0, shape=shape)
    chalk = G.smoothstep(-0.2, 1.6, G.fnoise(rng, shape, beta=2.2))
    painted = G.over(painted, np.array(paint) * 1.18 + 12, chalk * 0.35)
    c = G.fnoise(rng, shape, beta=1.4, kmin=4) + 0.45 * G.fnoise(rng, shape, beta=0.8, kmin=12)
    chips = G.smoothstep(1.55 - chip, 1.85 - chip, c)
    img = G.over(painted, under, chips)
    img = G.over(img, np.array([116.0, 64.0, 40.0]), _runs(chips, rng) * 0.6)
    return G.add_lum(img, rng.normal(0, 2.5, shape))


def _cutout(img, solid) -> np.ndarray:
    return np.concatenate([img, np.where(solid, 255.0, 0.0)[..., None]], axis=2)


# =============================================================================
# Textures
# =============================================================================

def tex_l39_gondola_lattice() -> None:
    """Tower lattice: faded grey-green angle steel (legs on the side edges, half a strut at the
    top and bottom so stacked blocks make whole struts, X bracing with gusset plates and bolts),
    with rime ice feathers blown sideways off every member, the way rime grows into the wind on
    real mountain towers. The top/bottom texture is the frame ring, crusted over."""
    rng = G.rng_for(39500)
    X, Y = G.grid()
    steel = _paint_steel(rng, (94, 104, 100), chip=0.3)
    t = 6.0
    legs = (X < 11) | (X > S - 11)
    struts = (Y < 4) | (Y > S - 4)
    diag = (np.abs(X - Y) < t / 2 * SQ2) | (np.abs(X + Y - S) < t / 2 * SQ2)
    gus = np.zeros((S, S), bool)
    for cx, cy in ((11, 11), (S - 11, 11), (11, S - 11), (S - 11, S - 11)):
        gus |= (np.abs(X - cx) + np.abs(Y - cy) < 11) & (np.abs(X - S / 2) > S / 2 - 22) & (np.abs(Y - S / 2) > S / 2 - 22)
    gus |= np.hypot(X - 64, Y - 64) < 7
    solid = legs | struts | diag | gus
    lum = np.where(legs, 14 * G.smoothstep(4, 0, np.minimum(X, S - X)) - 18 * G.smoothstep(7, 11, np.minimum(X, S - X)), 0)
    lum += np.where(diag & ~legs, -6, 0)
    img = G.add_lum(steel, lum)
    img = G.over(img, np.array([70.0, 76.0, 74.0]), (gus & ~legs) * 0.3)
    for cx, cy in ((5, 18), (S - 5, 18), (5, S - 18), (S - 5, S - 18), (64, 64), (18, 5), (S - 18, 5),
                   (18, S - 5), (S - 18, S - 5)):
        img = P._nail(img, cx, cy, rng, r=1.8, rust=0.7)
    img, _ = _frost(img, rng, 0.16)
    # Rime feathers: from each member, ragged blades of ice reaching sideways (+x), longest
    # where gusts streamed past; their roots are thick, their tips thin and broken.
    dist = np.full((S, S), 99.0)
    for d in range(1, 15):
        hit = np.roll(solid, d, axis=1) & (dist > 90)
        dist[hit] = d
    reach = 3.0 + 9.0 * G.smoothstep(-1.0, 1.2, G.blur(G.fnoise(rng, beta=1.6, aniso=(12, 1)), 0.8))
    ragged = G.fnoise(rng, beta=1.2, aniso=(12, 1), kmin=3) > -0.9 + 1.5 * dist / np.maximum(reach, 1.0)
    feather = ~solid & (dist <= reach) & ragged
    rime = G.add_lum(G.fill(RIME, (S, S)), G.fnoise(rng, beta=0.6, kmin=16) * 9 - dist * 1.6, (0.95, 0.98, 1.0))
    img = np.where(feather[..., None], rime, img)
    G.save_png(_cutout(img, solid | feather), _blk("l39_gondola_lattice"))

    rng = G.rng_for(39501)
    steel = _paint_steel(rng, (94, 104, 100), chip=0.3)
    d = np.minimum(np.minimum(X, S - X), np.minimum(Y, S - Y))
    ring = d < 10
    dg = np.abs(X - Y) < 3 * SQ2
    img = G.add_lum(steel, np.where(ring, 12 * G.smoothstep(5, 0, d) - 16 * G.smoothstep(6, 10, d), 0))
    for cx, cy in ((5, 5), (S - 5, 5), (5, S - 5), (S - 5, S - 5)):
        img = P._nail(img, cx, cy, rng, r=2.0, rust=0.7)
    img, _ = _frost(img, rng, 0.45)
    G.save_png(_cutout(img, ring | dg), _blk("l39_gondola_lattice_top"))


def tex_l39_gondola_steel() -> None:
    """Structural steel of the crossarms, braces, posts and sheave yokes: the towers' faded
    grey-green paint, chipped to rust, rime built up along the top."""
    rng = G.rng_for(39502)
    img = _paint_steel(rng, (94, 104, 100), chip=0.35)
    img, _ = _frost(img, rng, 0.24, _top_bias(1.6))
    G.save_png(img, _blk("l39_gondola_steel"))


def tex_l39_gondola_frame() -> None:
    """The chairs' tube frame: dark charcoal paint almost hidden under a thick, granular frost
    crust (in the photo the chairs are white with it), with long needle crystals standing off it."""
    rng = G.rng_for(39503)
    img = _paint_steel(rng, (50, 53, 58), chip=0.18)
    img, cover = _frost(img, rng, 0.45, _top_bias(1.2))
    X, Y = G.grid()
    needles = np.zeros((S, S))
    for _ in range(70):
        x0, y0 = rng.uniform(0, S, 2)
        ang = rng.normal(-np.pi / 2, 0.5)
        L = rng.uniform(3, 9)
        for s in np.linspace(0, 1, 12):
            px = int((x0 + np.cos(ang) * L * s) % S)
            py = int((y0 + np.sin(ang) * L * s) % S)
            needles[py, px] = max(needles[py, px], 1.0 - 0.5 * s)
    img = G.over(img, np.array([240.0, 246.0, 252.0]), np.clip(G.blur(needles, 0.4) * 1.4, 0, 0.9))
    G.save_png(img, _blk("l39_gondola_frame"))


def tex_l39_gondola_seat() -> None:
    """Chair padding: navy vinyl over foam in four stitched panels, faded and cracked by cold,
    a split or two, frost filling the seams and crusting the edges."""
    rng = G.rng_for(39504)
    X, Y = G.grid()
    img = G._smooth_concrete(rng, (36, 48, 92), mottle=4.0, fine=1.4)
    fade = G.smoothstep(-0.4, 1.8, G.fnoise(rng, beta=2.4))
    img = G.over(img, np.array([70.0, 82.0, 116.0]), fade * 0.4)
    # Panels: puffy in the middle, pulled in at the stitched seams every 32 px.
    v = (Y % 32) / 32.0
    img = G.add_lum(img, 10 * np.sin(np.pi * v) - 8)
    seam = np.abs(Y % 32 - 0.5) < 1.2
    img = G.over(img, np.array([16.0, 20.0, 34.0]), seam * 0.8)
    stitch = (np.abs((Y % 32) - 3.0) < 0.7) & ((X % 6) < 3)
    img = G.over(img, np.array([92.0, 98.0, 120.0]), stitch * 0.5)
    cracks = P._checks(rng, 9, vertical=False, length=(10, 40))
    img = G.add_lum(img, -30 * cracks)
    split = np.exp(-((Y - 80) / 1.3) ** 2) * (np.abs(X - 40) < 14)
    img = G.over(img, np.array([196.0, 186.0, 160.0]), split * 0.7)  # foam showing through
    seam_bias = 1.4 * np.exp(-((Y % 32) / 3.0) ** 2) + _top_bias(0.5)
    img, _ = _frost(img, rng, 0.2, seam_bias)
    G.save_png(img, _blk("l39_gondola_seat"))


def tex_l39_gondola_rime() -> None:
    """Thick rime ice: packed white granules in feathery layers that grew into the wind, faint
    blue shadows in the hollows, glassy glaze where it partly melted and froze again."""
    rng = G.rng_for(39505)
    img = G.fill(RIME, (S, S))
    layers = G.fnoise(rng, beta=1.6, aniso=(8, 1)) * 10
    hollows = G.smoothstep(0.6, 1.8, G.fnoise(rng, beta=1.0, kmin=6))
    img = G.add_lum(img, layers - 22 * hollows, (0.92, 0.97, 1.0))
    grain = G.fnoise(rng, beta=0.4, kmin=24)
    img = G.add_lum(img, grain * 8)
    glaze = G.smoothstep(1.2, 2.0, G.fnoise(rng, beta=2.0))
    img = G.over(img, GLAZE, glaze * 0.45)
    sparkle = rng.random((S, S)) < 0.006
    img[sparkle] = (252.0, 253.0, 255.0)
    G.save_png(img, _blk("l39_gondola_rime"))


def tex_l39_gondola_wheels() -> None:
    """Sheave wheel side-on (cutout disc: rubber liner ring, painted steel web with five
    lightening holes, hub and bolt circle, frost on the upper half), the black rubber of the liners,
    and the station's spoked bull wheel seen from above (cutout: rim, eight I-beam spokes, hub)."""
    rng = G.rng_for(39506)
    X, Y = G.grid()
    r = np.hypot(X - 64, Y - 64)
    steel = _paint_steel(rng, (150, 150, 140), chip=0.3)
    rubber = G.add_lum(G.fill((28, 28, 30), (S, S)), G.fnoise(rng, beta=1.2) * 4)
    img = np.where((r > 48)[..., None], rubber, steel)
    img = G.add_lum(img, -26 * np.exp(-((r - 55) / 1.2) ** 2))           # the groove the cable runs in
    img = G.add_lum(img, 14 * G.smoothstep(46, 40, r) * G.smoothstep(30, 38, r))  # the web's raised rim
    holes = np.zeros((S, S), bool)
    for k in range(5):
        a = k * 2 * np.pi / 5 + 0.3
        holes |= np.hypot(X - 64 - 29 * np.cos(a), Y - 64 - 29 * np.sin(a)) < 8.5
    hub = r < 12
    img = np.where(hub[..., None], G.add_lum(steel, 10 - r * 0.8), img)
    for k in range(6):
        a = k * np.pi / 3
        img = P._nail(img, 64 + 7 * np.cos(a), 64 + 7 * np.sin(a), rng, r=1.4, rust=0.6)
    img, _ = _frost(img, rng, 0.18, _top_bias(1.6, end=0.55))
    G.save_png(_cutout(img, (r < 61) & ~holes), _blk("l39_gondola_sheave"))

    rng = G.rng_for(39507)
    img = G.add_lum(G.fill((26, 26, 28), (S, S)), G.fnoise(rng, beta=1.4, aniso=(10, 1)) * 5)
    img = G.add_lum(img, -14 * np.exp(-((Y - 64) / 5.0) ** 2))  # worn groove along the middle
    img, _ = _frost(img, rng, 0.1)
    G.save_png(img, _blk("l39_gondola_rubber"))

    rng = G.rng_for(39508)
    steel = _paint_steel(rng, (148, 132, 70), chip=0.4)  # old safety yellow, mostly gone grey
    steel = G.over(steel, np.array([120.0, 118.0, 110.0]), G.smoothstep(-0.5, 1.5, G.fnoise(rng, beta=2.0)) * 0.5)
    rim = (r > 55) & (r < 63)
    spokes = np.zeros((S, S), bool)
    for k in range(8):
        a = k * np.pi / 4
        along = (X - 64) * np.cos(a) + (Y - 64) * np.sin(a)
        across = -(X - 64) * np.sin(a) + (Y - 64) * np.cos(a)
        spokes |= (along > 0) & (along < 58) & (np.abs(across) < 3.2)
    hub = r < 15
    solid = rim | spokes | hub
    img = G.add_lum(steel, np.where(rim, 10 * np.cos((r - 59) / 4 * np.pi / 2), 0) - 10 * (spokes & ~hub & ~rim))
    for k in range(8):
        a = k * np.pi / 4 + np.pi / 8
        img = P._nail(img, 64 + 10 * np.cos(a), 64 + 10 * np.sin(a), rng, r=1.6, rust=0.8)
    img, _ = _frost(img, rng, 0.25)
    G.save_png(_cutout(img, solid), _blk("l39_gondola_bullwheel"))


def tex_l39_gondola_concrete() -> None:
    """Pier concrete: grey, board-formed (faint horizontal form lines), tie holes, rust stains
    bleeding from the anchor bolts, frost heaped on top."""
    rng = G.rng_for(39509)
    X, Y = G.grid()
    img = G._smooth_concrete(rng, (126, 126, 122), mottle=5.0, fine=2.0)
    img = G.add_lum(img, G.fnoise(rng, beta=1.4, aniso=(12, 1), kmin=3) * 5)
    for y0 in (0, 42, 85):
        img = G.add_lum(img, -14 * np.exp(-((Y - y0) / 1.0) ** 2) + 5 * np.exp(-((Y - y0 - 2) / 1.5) ** 2))
    stains = np.zeros((S, S))
    for cx, cy in ((24, 21), (104, 21), (24, 64), (104, 64), (64, 106)):
        d = np.hypot(X - cx, Y - cy)
        img = G.add_lum(img, -40 * G.smoothstep(3.2, 2.0, d))
        stains += G.smoothstep(4, 0, d)
    img = G.over(img, np.array([118.0, 74.0, 46.0]), _runs(stains, rng, 18.0, 30) * 0.8)
    img = G.add_lum(img, -18 * G.smoothstep(1.6, 2.6, G.fnoise(rng, beta=0.9, kmin=10)))  # air holes
    img, _ = _frost(img, rng, 0.22, _top_bias(1.8))
    G.save_png(img, _blk("l39_gondola_concrete"))


def tex_l39_gondola_hood() -> None:
    """The bull-wheel housing: oxide-red sheet steel panels with standing seams and rivet rows,
    paint chalked pale and chipped, rust and melt streaks running down, rime streaks blown
    sideways across it; and its roof under a thick crust of rime."""
    rng = G.rng_for(39510)
    X, Y = G.grid()
    img = _paint_steel(rng, (112, 50, 42), chip=0.32)
    seams = np.minimum(np.abs(X - 0.5) % 64, 64 - np.abs(X - 0.5) % 64)
    img = G.add_lum(img, 18 * np.exp(-(seams / 1.2) ** 2) - 16 * np.exp(-((seams - 2.5) / 1.0) ** 2))
    for x0 in (5, 59, 69, 123):
        for y0 in range(8, S, 20):
            img = P._nail(img, x0, y0, rng, r=1.5, rust=0.7)
    streaks = G.fnoise(rng, beta=1.0, aniso=(14, 1), kmin=4)
    img, _ = _frost(img, rng, 0.2, 0.7 * streaks + _top_bias(1.2))
    G.save_png(img, _blk("l39_gondola_hood"))

    rng = G.rng_for(39511)
    img = _paint_steel(rng, (112, 50, 42), chip=0.4)
    img, _ = _frost(img, rng, 0.72)
    G.save_png(img, _blk("l39_gondola_hood_top"))


def tex_l39_gondola_deck() -> None:
    """Loading deck boards (four across the tile): silver-grey weathered timber, dark gaps packed
    with ice, nails at the joists, rime in drifts and glassy glaze patches where feet polished it."""
    rng = G.rng_for(39512)
    X, Y = G.grid()
    img = P._grey_wood(rng, vertical=False, base=(118, 116, 110), checks=5)
    img = P._knots(img, rng, 3, vertical=False)
    board = (Y // 32).astype(int)
    img = G.add_lum(img, (board * 37 % 11 - 5) * 1.6)
    gap = (Y % 32) < 2.0
    img = G.over(img, np.array([30.0, 30.0, 30.0]), gap * 0.85)
    for b in range(4):
        for x0 in (10, 74):
            img = P._nail(img, x0 + (b * 13) % 7, b * 32 + 9, rng, r=1.5, rust=0.6)
            img = P._nail(img, x0 + (b * 13) % 7, b * 32 + 23, rng, r=1.5, rust=0.6)
    gap_bias = 1.6 * np.exp(-((Y % 32) / 2.5) ** 2)
    img, _ = _frost(img, rng, 0.22, gap_bias)
    glaze = G.smoothstep(1.0, 1.8, G.fnoise(rng, beta=2.4))
    img = G.over(img, GLAZE, glaze * 0.35)
    G.save_png(img, _blk("l39_gondola_deck"))


def tex_l39_gondola_sign() -> None:
    """The station sign: old white enamel on steel with a red border, "LIFT" over an up arrow and
    "39", and a small "KEEP BAR DOWN"; enamel chipped to black at the edges and round the bolts,
    rime grown over the top corner and frost everywhere. Drawn in the board's part of the tile
    (x 8..120, y 16..112: the model maps uv 1,2,15,14 onto the board)."""
    rng = G.rng_for(39513)
    X, Y = G.grid()
    img = G._smooth_concrete(rng, (226, 224, 214), mottle=3.0, fine=1.2)
    img = G.over(img, np.array([200.0, 190.0, 160.0]), G.smoothstep(-0.3, 1.8, G.fnoise(rng, beta=2.3)) * 0.4)
    inside = (X > 8) & (X < 120) & (Y > 16) & (Y < 112)
    border = inside & ~((X > 14) & (X < 114) & (Y > 22) & (Y < 106))
    img = G.over(img, np.array([150.0, 34.0, 30.0]), border * 0.95)
    ink = np.array([22.0, 30.0, 52.0])
    img = G.over(img, ink, G._text("LIFT", 64, 40, 30, maxw=90) * 0.95)
    arrow = G.pil_mask(lambda d, k: d.polygon([(46 * k, 72 * k), (56 * k, 58 * k), (66 * k, 72 * k),
                                               (60 * k, 72 * k), (60 * k, 82 * k), (52 * k, 82 * k), (52 * k, 72 * k)], fill=255))
    img = G.over(img, ink, arrow * 0.95)
    img = G.over(img, ink, G._text("39", 82, 70, 24, maxw=34) * 0.95)
    img = G.over(img, np.array([150.0, 34.0, 30.0]), G._text("KEEP BAR DOWN", 64, 96, 9, maxw=90) * 0.9)
    # Enamel chips: black steel showing, most at the edges and round the four bolts.
    edge = np.minimum(np.minimum(X - 8, 120 - X), np.minimum(Y - 16, 112 - Y))
    chip = G.fnoise(rng, beta=1.0, kmin=8) + 1.4 * G.smoothstep(6, 0, edge)
    for cx, cy in ((18, 26), (110, 26), (18, 102), (110, 102)):
        chip += 1.8 * G.smoothstep(6, 1, np.hypot(X - cx, Y - cy))
    chips = G.smoothstep(1.6, 1.9, chip) * inside
    img = G.over(img, np.array([34.0, 32.0, 32.0]), chips)
    img = G.over(img, np.array([116.0, 64.0, 40.0]), _runs(chips, rng, 8.0, 12) * 0.5 * inside)
    for cx, cy in ((18, 26), (110, 26), (18, 102), (110, 102)):
        img = P._nail(img, cx, cy, rng, r=2.0, rust=0.8)
    corner = 1.6 * G.smoothstep(70, 10, np.hypot(X - 8, Y - 16)) + _top_bias(0.7)
    img, _ = _frost(img, rng, 0.14, corner)
    G.save_png(img, _blk("l39_gondola_sign"))


def tex_l39_gondola_cable() -> None:
    """16 x 64 tile of the haul rope, u across it and v along it: six strands twisted round each
    other (diagonal bands, dark grooves between them, the wires of each strand laid the other
    way), dark greasy steel, rounded by shading across, glints of frost caught in the grooves."""
    rng = G.rng_for(39514)
    shape = (64, 16)
    X, Y = G.grid(shape)
    img = G.fill((62, 64, 68), shape)
    lay = ((Y + X * 1.0) % 16.0) / 16.0                          # strand lay: 4 strands per tile length
    img = G.add_lum(img, 26 * np.sin(np.pi * lay) - 22, (1.0, 1.0, 1.02))
    wires = ((Y - X * 0.6) % 3.0) / 3.0
    img = G.add_lum(img, 6 * np.sin(np.pi * wires) - 3)
    img = G.add_lum(img, 18 * (1.0 - ((X - 8) / 8.0) ** 2) - 12)  # round: dark at both edges
    grease = G.smoothstep(0.5, 1.6, G.fnoise(rng, shape, beta=1.6, aniso=(1, 6)))
    img = G.over(img, np.array([26.0, 25.0, 24.0]), grease * 0.5)
    groove = np.exp(-((lay - 0.0) / 0.08) ** 2) + np.exp(-((lay - 1.0) / 0.08) ** 2)
    glints = (rng.random(shape) < 0.25) * groove
    img = G.over(img, np.array([214.0, 224.0, 234.0]), np.clip(glints, 0, 0.85))
    G.save_png(img, G.TEX / "entity" / "l39_gondola_cable.png")


# =============================================================================
# Models
# =============================================================================

_el = P._el
_model = P._model
_bm = P._bm
_im = P._im
FACES = P.FACES
SIDES = ("north", "south", "east", "west")


def _rot_xyz(rx, ry, rz):
    """Rotation matrix as ItemTransform builds it (Quaternionf.rotationXYZ: X, then Y, then Z)."""
    a, b, c = np.radians([rx, ry, rz])
    mx = np.array([[1, 0, 0], [0, np.cos(a), -np.sin(a)], [0, np.sin(a), np.cos(a)]])
    my = np.array([[np.cos(b), 0, np.sin(b)], [0, 1, 0], [-np.sin(b), 0, np.cos(b)]])
    mz = np.array([[np.cos(c), -np.sin(c), 0], [np.sin(c), np.cos(c), 0], [0, 0, 1]])
    return mx @ my @ mz


def _display(centre, scales):
    """Item display transforms that put the model point `centre` (block pixels) in the middle,
    for models whose shape sticks far out of their block (the chair hangs off to one side and
    two blocks up). scales: {context: (rotation, scale)}."""
    d = np.array(centre, float) - 8.0
    out = {}
    for ctx, (rot, s) in scales.items():
        t = -(_rot_xyz(*rot) @ (s * d))
        out[ctx] = {"rotation": list(rot), "translation": [round(float(v), 2) for v in t], "scale": [s, s, s]}
    return out


def _item_views(gui=0.4, hand=0.3, ground=0.2, fixed=0.45):
    return {"gui": ((30, 225, 0), gui), "ground": ((0, 0, 0), ground), "fixed": ((0, 0, 0), fixed),
            "thirdperson_righthand": ((75, 45, 0), hand), "thirdperson_lefthand": ((75, 45, 0), hand),
            "firstperson_righthand": ((0, 45, 0), hand), "firstperson_lefthand": ((0, 225, 0), hand)}


def _chair_models() -> None:
    """The chair, facing north (the way you look sitting in it). It hangs half a block east of its
    block: centred on x 16, z 8, so its grip (top centre, y 32) sits on a cable running exactly
    along the block's east edge, two blocks up. Seat, leaning back, armrests, a hanger arm rising
    behind the back and arching over to the grip, a safety bar (raised; lowered in the _lowered
    model, with its footrest), and frost crust on everything that faces up."""
    tex = {"particle": _ref("l39_gondola_frame"), "frame": _ref("l39_gondola_frame"),
           "seat": _ref("l39_gondola_seat"), "rime": _ref("l39_gondola_rime")}
    back = ((16, 1, 13), "x", 22.5)
    e = [
        # Seat: padding on a tube frame.
        _el((9, 1, 2.5), (23, 2.5, 12.5), "#seat", cull=False),
        _el((8.5, 0, 2), (9.5, 1.5, 12.5), "#frame", cull=False),
        _el((22.5, 0, 2), (23.5, 1.5, 12.5), "#frame", cull=False),
        _el((8.5, 0, 1.5), (23.5, 1.25, 2.5), "#frame", cull=False),
        _el((9.3, 2.5, 2.6), (22.7, 2.9, 10.5), "#rime", faces=("up", "north", "east", "west"), cull=False),
        # Backrest, leaning back.
        _el((9, 3, 12.5), (23, 13, 13.5), "#seat", rot=back),
        _el((8.5, 0, 12.5), (9.5, 14, 13.5), "#frame", rot=back),
        _el((22.5, 0, 12.5), (23.5, 14, 13.5), "#frame", rot=back),
        _el((8.5, 13, 12.25), (23.5, 14, 13.75), "#frame", rot=back),
        _el((8.6, 14, 12.2), (23.4, 14.7, 13.8), "#rime", faces=("up", "north", "south", "east", "west"), rot=back),
        # Armrests on their front posts, crusted on top.
        _el((8.5, 6.5, 3), (9.5, 7.5, 13), "#frame", cull=False),
        _el((22.5, 6.5, 3), (23.5, 7.5, 13), "#frame", cull=False),
        _el((8.5, 1.25, 3), (9.5, 6.5, 4), "#frame", cull=False),
        _el((22.5, 1.25, 3), (23.5, 6.5, 4), "#frame", cull=False),
        _el((8.35, 7.5, 3.1), (9.65, 8.1, 12.6), "#rime", faces=("up", "east", "west", "north"), cull=False),
        _el((22.35, 7.5, 3.1), (23.65, 8.1, 12.6), "#rime", faces=("up", "east", "west", "north"), cull=False),
        # Hanger: a crossbar behind the back, the arm up behind it and over to the grip.
        _el((8.5, 8.5, 16), (23.5, 9.5, 17), "#frame", cull=False),
        _el((15.25, 8.5, 16.25), (16.75, 26, 17.75), "#frame", cull=False),
        _el((15.25, 24.75, 7.5), (16.75, 26.25, 17.75), "#frame", cull=False),
        _el((15.25, 23.5, 15.0), (16.75, 25.0, 18.5), "#frame", rot=((16, 24.25, 16.75), "x", 45)),
        _el((15.1, 26.25, 7.7), (16.9, 26.9, 17.6), "#rime", faces=("up", "east", "west", "north", "south"), cull=False),
        # Grip: the clamp body and its jaws closing round the cable at y 32.
        _el((14, 26.25, 6), (18, 30, 10), "#frame", cull=False),
        _el((14.5, 30, 6.5), (15.5, 32, 9.5), "#frame", cull=False),
        _el((16.5, 30, 6.5), (17.5, 32, 9.5), "#frame", cull=False),
        _el((13.9, 30, 5.9), (18.1, 30.5, 10.1), "#rime", faces=("up", "north", "south", "east", "west"), cull=False),
    ]
    # The safety bar pivots on the back posts at y 14, z 14: two side arms and a front bar.
    raised = [
        _el((7.75, 14, 13.5), (8.75, 25, 14.5), "#frame", cull=False),
        _el((23.25, 14, 13.5), (24.25, 25, 14.5), "#frame", cull=False),
        _el((7.75, 24, 13.25), (24.25, 25, 14.75), "#frame", cull=False),
        _el((7.7, 25, 13.2), (24.3, 25.5, 14.8), "#rime", faces=("up", "north", "south", "east", "west"), cull=False),
    ]
    lowered = [
        _el((7.75, 11, 2.5), (8.75, 12, 14.5), "#frame", cull=False),
        _el((23.25, 11, 2.5), (24.25, 12, 14.5), "#frame", cull=False),
        _el((7.75, 11, 1.75), (24.25, 12, 2.75), "#frame", cull=False),
        _el((7.7, 12, 1.7), (24.3, 12.5, 2.8), "#rime", faces=("up", "north", "south"), cull=False),
        _el((7.7, 12, 2.8), (8.8, 12.4, 14.4), "#rime", faces=("up",), cull=False),
        _el((23.2, 12, 2.8), (24.3, 12.4, 14.4), "#rime", faces=("up",), cull=False),
        # Footrest on two rods under the front bar.
        _el((11, -6, 1.75), (12, 11, 2.75), "#frame", cull=False),
        _el((20, -6, 1.75), (21, 11, 2.75), "#frame", cull=False),
        _el((10, -7, 0.25), (22, -6, 3.25), "#frame", cull=False),
        _el((10.1, -6, 0.35), (21.9, -5.6, 3.15), "#rime", faces=("up", "north", "south"), cull=False),
    ]
    display = _display((16, 12.5, 9), _item_views(gui=0.36, hand=0.28, ground=0.2, fixed=0.4))
    _bm("l39_gondola_chair", _model(tex, e + raised, display=display))
    _bm("l39_gondola_chair_lowered", _model(tex, e + lowered, display=display))
    P._facing_state("l39_gondola_chair", extra=("lowered", ("false", "true"),
                                               lambda v: "l39_gondola_chair" + ("_lowered" if v == "true" else "")))
    _im("l39_gondola_chair")


def _lattice_box(tex_side="#side", tex_top="#top"):
    """A cube of open lattice; each side also gets an inward-facing copy, so through one side's
    gaps you see the far side's bracing (back faces are otherwise culled)."""
    t = {f: tex_side for f in FACES}
    t["up"] = t["down"] = tex_top
    full = [0, 0, 16, 16]
    return [_el((0, 0, 0), (16, 16, 16), t, cull=False),
            _el((0, 0, 16), (16, 16, 16), tex_side, faces=("north",), cull=False, uv={"north": full}),
            _el((0, 0, 0), (16, 16, 0), tex_side, faces=("south",), cull=False, uv={"south": full}),
            _el((16, 0, 0), (16, 16, 16), tex_side, faces=("west",), cull=False, uv={"west": full}),
            _el((0, 0, 0), (0, 16, 16), tex_side, faces=("east",), cull=False, uv={"east": full})]


def _tower_models() -> None:
    lat = {"particle": _ref("l39_gondola_lattice"), "side": _ref("l39_gondola_lattice"),
           "top": _ref("l39_gondola_lattice_top"), "steel": _ref("l39_gondola_steel"),
           "rime": _ref("l39_gondola_rime"), "concrete": _ref("l39_gondola_concrete"),
           "wheel": _ref("l39_gondola_sheave"), "rubber": _ref("l39_gondola_rubber")}

    # Mast lattice.
    _bm("l39_gondola_lattice", _model({k: lat[k] for k in ("particle", "side", "top")}, _lattice_box()))
    P._plain_state("l39_gondola_lattice")
    _im("l39_gondola_lattice")

    # Pier: a concrete block with a chamfered top, the mast's base plate, four anchor bolts and leg stubs.
    e = [_el((0, 0, 0), (16, 14, 16), "#concrete"),
         _el((1, 14, 1), (15, 15.5, 15), "#concrete", cull=False),
         _el((2, 15.5, 2), (14, 16.25, 14), "#steel", cull=False)]
    for x, z in ((3, 3), (12, 3), (3, 12), (12, 12)):
        e.append(_el((x, 16.25, z), (x + 1, 17.25, z + 1), "#steel", cull=False))
    for x, z in ((0.25, 0.25), (14.25, 0.25), (0.25, 14.25), (14.25, 14.25)):
        e.append(_el((x, 16, z), (x + 1.5, 20, z + 1.5), "#steel", cull=False))
    e.append(_el((1, 16.25, 1), (15, 16.6, 15), "#rime", faces=("up",), cull=False))
    _bm("l39_gondola_footing", _model({"particle": lat["concrete"], "concrete": lat["concrete"], "steel": lat["steel"],
                                        "rime": lat["rime"]}, e, render="minecraft:cutout"))
    P._plain_state("l39_gondola_footing")
    _im("l39_gondola_footing")

    # Brace (FACING north: the mast is the next block north). A small footing, a 2x2 angle
    # leaning in to the mast's face 1.7 blocks up, a horizontal tie, rime on the brace's top.
    lean = ((8, 2, 12), "x", -22.5)
    e = [_el((4, 0, 8), (12, 3, 16), "#concrete"),
         _el((5, 3, 9), (11, 3.75, 15), "#steel", cull=False),
         _el((7, 2, 11), (9, 30, 13), "#steel", rot=lean),
         _el((6.9, 2, 12.9), (9.1, 30, 13.5), "#rime", faces=("south", "up", "east", "west"), rot=lean),
         _el((7.25, 9, 0), (8.75, 10.25, 9.5), "#steel", cull=False),
         _el((7.2, 10.25, 0), (8.8, 10.8, 9.5), "#rime", faces=("up", "east", "west"), cull=False)]
    _bm("l39_gondola_leg", _model({"particle": lat["steel"], "steel": lat["steel"], "concrete": lat["concrete"],
                                    "rime": lat["rime"]}, e))
    P._facing_state("l39_gondola_leg")
    _im("l39_gondola_leg")

    # Crossarm head (AXIS x): the mast's last lattice block, a cap plate, the beam across it
    # at y 16-20 (continued by the sheave blocks on both sides), rime along the top.
    e = _lattice_box() + [
        _el((2, 16, 2), (14, 17, 14), "#steel", cull=False),
        _el((0, 16, 5.5), (16, 20, 10.5), "#steel", cull=False),
        _el((0, 20, 5.4), (16, 20.8, 10.6), "#rime", faces=("up", "north", "south"), cull=False),
        _el((3, 13, 7), (13, 16, 9), "#steel", rot=((8, 16, 8), "z", 22.5)),
        _el((3, 13, 7), (13, 16, 9), "#steel", rot=((8, 16, 8), "z", -22.5))]
    _bm("l39_gondola_crossarm", _model(lat, e))
    P._axis_state("l39_gondola_crossarm")
    _im("l39_gondola_crossarm")

    # Sheave train (FACING north = outward; the crossarm is south of it): the beam's last block
    # with an end plate, a yoke hanging from it and four rubber-lined wheels whose tops (y 16)
    # carry the cable along the block's north edge.
    e = [_el((5.5, 16, 2), (10.5, 20, 16), "#steel", cull=False),
         _el((5.4, 20, 2), (10.6, 20.8, 16), "#rime", faces=("up", "east", "west"), cull=False),
         _el((5, 15, 1.5), (11, 21, 2.5), "#steel", cull=False),
         _el((-1, 9, 1.25), (17, 12, 2.25), "#steel", cull=False),
         _el((-1, 12, 1.4), (17, 12.5, 2.25), "#rime", faces=("up",), cull=False),
         _el((7, 12, 1.25), (9, 16, 2.25), "#steel", cull=False)]
    for cx in (1.5, 5.8, 10.2, 14.5):
        wheel = {"north": "#wheel", "south": "#wheel", "up": "#rubber", "down": "#rubber", "east": "#rubber", "west": "#rubber"}
        e.append(_el((cx - 2.25, 11.5, -1.25), (cx + 2.25, 16, 0.75), wheel, cull=False,
                     uv={"north": [0, 0, 16, 16], "south": [0, 0, 16, 16]}))
        e.append(_el((cx - 0.5, 13.25, 0.75), (cx + 0.5, 14.25, 1.5), "#steel", cull=False))
    _bm("l39_gondola_sheave", _model(lat, e))
    P._facing_state("l39_gondola_sheave")
    _im("l39_gondola_sheave")


def _station_models() -> None:
    # Bull wheel: three blocks across (rim radius 24 px round the block centre), its rim's top
    # at y 16 where the cable runs on it; spokes, hub and a gearbox under it.
    tex = {"particle": _ref("l39_gondola_bullwheel"), "wheel": _ref("l39_gondola_bullwheel"),
           "steel": _ref("l39_gondola_steel"), "rubber": _ref("l39_gondola_rubber"), "rime": _ref("l39_gondola_rime")}
    e = [_el((-16, 14, -16), (32, 14, 32), "#wheel", faces=("up", "down"), cull=False, uv="full"),
         _el((4.5, 9, 4.5), (11.5, 16.5, 11.5), "#steel", cull=False),
         _el((5, 16.5, 5), (11, 17, 11), "#rime", faces=("up",), cull=False),
         _el((5, 0, 5), (11, 9, 11), "#steel", cull=False)]
    seg = 2 * 24 * np.tan(np.pi / 16) + 0.4
    # The rim: 16 straight steel segments round the wheel, their outer face lined with rubber.
    # Each box is laid out in one of the four quarters (q) and turned by the rest of its angle
    # (models only allow turns of -45..45 degrees); the outer face stays at the 32 px limit.
    outer = ("east", "south", "west", "north")
    for k in range(16):
        q, rest = divmod(k, 4)
        r = (-22.5, 0.0, 22.5, 45.0)[rest]
        if q % 2 == 0:
            x0 = 30.0 if q == 0 else -16.0
            box = ((x0, 12, 8 - seg / 2), (x0 + 2, 16, 8 + seg / 2))
        else:
            z0 = 30.0 if q == 1 else -16.0
            box = ((8 - seg / 2, 12, z0), (8 + seg / 2, 16, z0 + 2))
        faces = {f: "#steel" for f in FACES}
        faces[outer[q]] = "#rubber"
        rot = ((8, 14, 8), "y", r) if r != 0.0 else None
        e.append(_el(box[0], box[1], faces, rot=rot, cull=False))
        top = ((box[0][0], 16, box[0][2]), (box[1][0], 16.5, box[1][2]))
        e.append(_el(top[0], top[1], "#rime", faces=("up",), rot=rot, cull=False))
    display = _display((8, 8, 8), _item_views(gui=0.22, hand=0.18, ground=0.14, fixed=0.25))
    _bm("l39_gondola_bullwheel", _model(tex, e, display=display))
    P._plain_state("l39_gondola_bullwheel")
    _im("l39_gondola_bullwheel")

    # Housing steel: red panels on the sides, the rime-crusted roof on top.
    _bm("l39_gondola_hood", {"parent": "minecraft:block/cube_bottom_top",
                             "textures": {"side": _ref("l39_gondola_hood"), "top": _ref("l39_gondola_hood_top"),
                                          "bottom": _ref("l39_gondola_hood")}})
    P._plain_state("l39_gondola_hood")
    _im("l39_gondola_hood")

    # Deck slab (and the full block a double slab would be).
    _bm("l39_gondola_deck_double", {"parent": "minecraft:block/cube_all", "textures": {"all": _ref("l39_gondola_deck")}})
    P._slab("l39_gondola_deck", "l39_gondola_deck", "l39_gondola_deck_double")

    # Sign (FACING north = the side you read): enamel board on two posts, rime on top.
    tex = {"particle": _ref("l39_gondola_sign"), "sign": _ref("l39_gondola_sign"), "steel": _ref("l39_gondola_steel"),
           "rime": _ref("l39_gondola_rime")}
    board = {"north": "#sign", "south": "#steel", "up": "#steel", "down": "#steel", "east": "#steel", "west": "#steel"}
    e = [_el((2.5, 0, 7.75), (4, 28, 9.25), "#steel", cull=False),
         _el((12, 0, 7.75), (13.5, 28, 9.25), "#steel", cull=False),
         _el((1, 14, 7), (15, 26, 8), board, cull=False, uv={"north": [1, 2, 15, 14]}),
         _el((0.9, 26, 6.9), (15.1, 26.9, 8.3), "#rime", faces=("up", "north", "south", "east", "west"), cull=False),
         _el((2.4, 28, 7.65), (4.1, 28.8, 9.35), "#rime", cull=False),
         _el((11.9, 28, 7.65), (13.6, 28.8, 9.35), "#rime", cull=False)]
    _bm("l39_gondola_sign", _model(tex, e))
    P._facing_state("l39_gondola_sign")
    _im("l39_gondola_sign")


def tex_l39_gondola_models() -> None:
    """Block models, blockstates and item models for every l39_gondola_* block (LF JSON)."""
    _chair_models()
    _tower_models()
    _station_models()


# =============================================================================
# Sounds
# =============================================================================

def _whole(f, T):
    """A frequency rounded to a whole number of cycles over T seconds (keeps loops seamless)."""
    return max(1.0, round(f * T)) / T


def _swell(n, rng, lo, hi):
    """A slow periodic 0..1 swell with energy only between lo..hi Hz."""
    s = G.periodic_noise(n, rng, lambda f: ((f > lo) & (f < hi)).astype(float))
    return np.clip(0.5 + 0.5 * s / (np.abs(s).max() + 1e-12), 0, 1)


def _outdoor(rng, x, wet=0.18):
    """The open, foggy field: almost no echo, just a short soft diffuse tail."""
    ir = G.reverb_ir(rng, 0.9, 0.22, lp=2500, predelay=0.012)
    y = np.concatenate([x, np.zeros(len(ir))])
    tail = G.conv(x, ir)[:len(y)]
    tail = np.pad(tail, (0, len(y) - len(tail)))
    return y + wet * G.norm(tail) * np.abs(x).max()


def snd_l39_gondola_wind() -> None:
    """12 s loop: wind on an exposed lift line. A broad roar that gusts and lulls, snow grains
    hissing on the gusts, low buffeting, and the cable singing: thin aeolian whistles that rise
    with each gust and waver in pitch."""
    rng = G.rng_for(39550)
    T = 12.0
    n = int(T * SR)
    t = np.arange(n) / SR
    gust = 0.2 + 0.8 * _swell(n, rng, 0.06, 0.35) ** 1.5
    body = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 60, 900, 2) / np.maximum(f, 60) ** 0.6) * gust
    hiss = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 1800, 7000, 2)) * gust ** 2.5
    low = G.periodic_noise(n, rng, lambda f: G.lp_gain(f, 90, 3) * G.hp_gain(f, 18, 2)) * gust
    whistle = np.zeros(n)
    for f0, depth in ((540.0, 1.0), (812.0, 0.6), (1236.0, 0.35)):
        fw = _whole(f0, T)
        wob = 1.2 * np.sin(2 * np.pi * _whole(0.35, T) * t + rng.uniform(0, 6.3)) \
            + 0.6 * np.sin(2 * np.pi * _whole(0.9, T) * t + rng.uniform(0, 6.3))
        sw = _swell(n, rng, 0.08, 0.4) ** 2 * gust
        whistle += depth * np.sin(2 * np.pi * fw * t + wob) * sw
    x = G.norm(body) * 0.6 + G.norm(hiss) * 0.13 + G.norm(low) * 0.35 + G.norm(whistle) * 0.07
    _save_ogg(x, _snd("wind"), peak=0.6, loop=True)


def _creak(rng, dur, r0, r1, modes, decays):
    """One stick-slip creak: friction pulses whose rate slides from r0 to r1 Hz (with jitter),
    ringing the structure's resonances."""
    m = int(dur * SR)
    tt = np.arange(m) / SR
    bump = np.sin(np.pi * tt / dur) ** 0.7
    rate = (r0 + (r1 - r0) * tt / dur) * (1 + 0.12 * rng.normal(size=m).cumsum() / np.sqrt(m))
    phase = np.cumsum(np.maximum(rate, 5.0)) / SR
    pulses = np.zeros(m)
    idx = np.nonzero(np.diff(np.floor(phase)) > 0)[0]
    pulses[idx] = rng.uniform(0.5, 1.0, len(idx))
    pulses *= bump
    ir = G.ring(modes, decays, [1.0, 0.6, 0.35, 0.2][:len(modes)], 0.3)
    return G.conv(pulses, ir)


def snd_l39_gondola_cable_creak() -> None:
    """10 s loop: a haul rope under tension. Slow stick-slip groans from the cable bending over
    the sheaves, a low wandering hum of the rope itself, and now and then the faint double tick
    of a grip running over a wheel far off."""
    rng = G.rng_for(39551)
    T = 10.0
    n = int(T * SR)
    t = np.arange(n) / SR
    creaks = np.zeros(n)
    for start, dur, r0, r1 in ((0.6, 1.1, 40, 95), (3.2, 0.7, 70, 45), (5.4, 1.4, 30, 85), (8.1, 0.9, 55, 110)):
        c = _creak(rng, dur, r0, r1, [168.0, 382.0, 690.0, 1150.0], [0.06, 0.04, 0.022, 0.012])
        G.add_at(creaks, c * rng.uniform(0.6, 1.0), start, True)
    f0 = _whole(61.0, T)
    wob = 3.0 * np.sin(2 * np.pi * _whole(0.2, T) * t)
    hum = (np.sin(2 * np.pi * f0 * t + wob) + 0.4 * np.sin(4 * np.pi * f0 * t + 2 * wob)) * (0.4 + 0.6 * _swell(n, rng, 0.1, 0.5))
    ticks = np.zeros(n)
    for start in (1.9, 2.05, 6.7, 6.85):
        tk = G.ring([2350.0, 3900.0, 5200.0], [0.01, 0.006, 0.004], [1.0, 0.5, 0.3], 0.08)
        G.add_at(ticks, G.fft_filter(tk, lambda f: G.lp_gain(f, 3000, 2)) * 0.6, start, True)
    bed = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 30, 260, 2))
    x = G.norm(creaks) * 0.7 + G.norm(hum) * 0.22 + G.norm(ticks) * 0.12 + G.norm(bed) * 0.16
    x = x + 0.15 * G.norm(G.circ_conv(x, G.reverb_ir(rng, 0.8, 0.2, lp=2000, predelay=0.01))) * np.abs(x).max()
    _save_ogg(x, _snd("cable_creak"), peak=0.55, loop=True)


def _clank(rng, modes, decays, amps, dur, thump_f=0.0, noise=0.4):
    """A struck steel part: a short noise burst plus ringing modes, and a low thump under it."""
    m = int(dur * SR)
    tt = np.arange(m) / SR
    burst = rng.normal(size=m) * np.exp(-tt / 0.004) * noise
    x = burst + G.ring(modes, decays, amps, dur)[:m]
    if thump_f:
        x = x + 1.2 * np.sin(2 * np.pi * thump_f * tt) * np.exp(-tt / 0.05)
    return x


def snd_l39_gondola_sheave_clack() -> None:
    """~1.7 s: a chair's grip running over a tower's sheave train: a quick rattling run of
    rubber-muffled clacks, one per wheel, then the cable bouncing and humming as it settles."""
    rng = G.rng_for(39552)
    dur = 1.7
    x = np.zeros(int(dur * SR))
    for k in range(6):
        hit = _clank(rng, [720.0 * rng.uniform(0.95, 1.05), 1320.0, 2150.0], [0.03, 0.018, 0.01], [1.0, 0.5, 0.25],
                     0.25, thump_f=95.0, noise=0.6)
        G.add_at(x, hit * (1.0 - 0.08 * k) * rng.uniform(0.8, 1.0), 0.04 + k * 0.105 + rng.normal(0, 0.006), False)
    m = int(1.2 * SR)
    tt = np.arange(m) / SR
    settle = (np.sin(2 * np.pi * 54 * tt) + 0.5 * np.sin(2 * np.pi * 318 * tt + 1.0)) * np.exp(-tt / 0.35) * 0.35
    G.add_at(x, settle, 0.62, False)
    x = G.fft_filter(x, lambda f: G.lp_gain(f, 5000, 2))
    G.save_ogg(_outdoor(rng, x), _snd("sheave_clack"), peak=0.85)


def snd_l39_gondola_board() -> None:
    """~1.9 s: sitting down in the chair. The seat scoops you up with a heavy clunk that rings
    through the frame, the chair swings and the hanger pivot creaks twice, then the safety bar
    comes down and latches with a hard double click."""
    rng = G.rng_for(39553)
    dur = 1.9
    x = np.zeros(int(dur * SR))
    clunk = _clank(rng, [212.0, 468.0, 905.0, 1460.0], [0.18, 0.11, 0.06, 0.03], [1.0, 0.6, 0.35, 0.2], 0.8,
                   thump_f=68.0, noise=0.9)
    G.add_at(x, clunk, 0.02, False)
    for start, dur_c, r0, r1 in ((0.32, 0.28, 60, 110), (0.72, 0.34, 90, 50)):
        G.add_at(x, _creak(rng, dur_c, r0, r1, [240.0, 560.0, 980.0], [0.05, 0.03, 0.015]) * 0.5, start, False)
    m = int(0.25 * SR)
    tt = np.arange(m) / SR
    swish = G.fft_filter(rng.normal(size=m), lambda f: G.bp_gain(f, 600, 3000, 2)) * np.sin(np.pi * tt / 0.25) ** 2 * 0.15
    G.add_at(x, swish, 1.0, False)
    G.add_at(x, _clank(rng, [1210.0, 1890.0, 3020.0], [0.05, 0.03, 0.015], [1.0, 0.6, 0.4], 0.4, thump_f=120.0, noise=1.0),
             1.24, False)
    G.add_at(x, _clank(rng, [2900.0, 4400.0], [0.012, 0.008], [1.0, 0.5], 0.1, noise=0.8) * 0.6, 1.31, False)
    x = G.fft_filter(x, lambda f: G.lp_gain(f, 7000, 2))
    G.save_ogg(_outdoor(rng, x, 0.12), _snd("board"), peak=0.85)


def snd_l39_gondola_whiteout() -> None:
    """~4 s: the last seconds of the ride, going white. A breathy, choir-like cloud of partials
    gliding upward together (400 Hz to 2.4 kHz), a soft sine tone rising under it, and wind hiss
    climbing in brightness, all swelling to the end, where the noclip takes over."""
    rng = G.rng_for(39554)
    dur = 4.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    u = t / dur
    sweep = 400.0 * (2400.0 / 400.0) ** (u ** 1.3)
    air = np.zeros(n)
    for _ in range(60):
        k = np.exp(rng.uniform(np.log(0.8), np.log(1.25)))
        flutter = 1.0 + 0.004 * np.sin(2 * np.pi * rng.uniform(3, 7) * t + rng.uniform(0, 6.3))
        amp = 0.5 + 0.5 * np.sin(2 * np.pi * rng.uniform(0.3, 1.2) * t + rng.uniform(0, 6.3))
        air += amp * np.sin(2 * np.pi * np.cumsum(sweep * k * flutter) / SR + rng.uniform(0, 6.3))
    tone_f = 220.0 * 3.0 ** (u ** 1.5)
    tone = np.sin(2 * np.pi * np.cumsum(tone_f) / SR) + 0.3 * np.sin(4 * np.pi * np.cumsum(tone_f) / SR)
    noise = rng.normal(size=n)
    dark = G.fft_filter(noise, lambda f: G.bp_gain(f, 200, 1200, 2))
    bright = G.fft_filter(noise, lambda f: G.bp_gain(f, 2000, 9000, 2))
    hiss = G.norm(dark) * (1 - u) + G.norm(bright) * u
    swell = G.smoothstep(0.0, 0.85, u) ** 1.6
    x = (G.norm(air) * 0.5 + G.norm(tone) * 0.25 + hiss * 0.3) * swell
    x = x * G.smoothstep(1.0, 0.97, u)  # the very end drops away (the noclip sound takes over)
    G.save_ogg(_outdoor(rng, x, 0.25), _snd("whiteout"), peak=0.8, fade_out=0.15)


TEXTURES = [tex_l39_gondola_lattice, tex_l39_gondola_steel, tex_l39_gondola_frame, tex_l39_gondola_seat,
            tex_l39_gondola_rime, tex_l39_gondola_wheels, tex_l39_gondola_concrete, tex_l39_gondola_hood,
            tex_l39_gondola_deck, tex_l39_gondola_sign, tex_l39_gondola_cable, tex_l39_gondola_models]
SOUNDS = [snd_l39_gondola_wind, snd_l39_gondola_cable_creak, snd_l39_gondola_sheave_clack, snd_l39_gondola_board,
          snd_l39_gondola_whiteout]
