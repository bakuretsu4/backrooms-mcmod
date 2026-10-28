"""Level 227 "Megaflora": the flora's assets (block textures, models, blockstates, the giant
Venus flytrap's texture, particles and sounds).

Level 227 is a planet-sized tree hanging upside down over a black void; a whole rainforest grows
on each of its branches. The look follows the references in the design notes: a misty, towering
tropical forest in diffuse grey-green light (wet, dark, deeply ridged bark; vivid moss; lush big
leaves; ferns), plus the wiki drawing's fern-like foliage of lime and blue-green leaflets.

Atlas budget: every PNG in textures/block is stitched into the one block atlas, which must stay at
4096 x 4096 and is nearly full. So these textures are 64 px (big surfaces) and 32 px (small
plants), not the 128 px of other levels; animated textures (the swaying leaves) only take one
frame's room on the atlas. Everything here adds about 123 000 texels (24 x 64^2 + 24 x 32^2).

Run with:  python tools/gen_assets.py l227
Every generator has its own fixed seed (22700-22999), so re-running gives identical files.

Textures (textures/block/l227_*.png, 64 px unless noted):
  bark_0..3, bark_end           the giant tree's bark: plates and deep furrows along the grain
  bark_mossy_v / _h             bark with moss over the top edge (grain vertical / horizontal)
  palette                       the Palette: bark, with the faintest scale pattern
  heartwood, rings, rings_diag  the inside of a branch and its growth rings (straight / diagonal)
  knot                          a knothole, resin (an amber overlay drawn full-bright)
  root_soil, moss, forest_grass(_side), ash
  tree_log, tree_log_mossy, tree_log_end (32)
  leaves, fern_leaves           animated (8 frames, interpolated sway), cutout
  giant_fern, fallen_leaf, and at 32 px: tall_grass_lower/_upper, hanging_fronds(_tip),
  hanging_moss(_tip), liana(_tip), undergrowth_0..3, flower_0..3, bracket_top/_under,
  epiphyte, lestron(_cut), gammapali
  item/lestron (32)
Entity: textures/entity/venus_flytrap.png (256 x 256 for the model's 128 x 128 UV layout).
Particles: textures/particle/l227_spore_haze_0..1, l227_falling_leaf_0..2.
Sounds: sounds/l227/*.ogg (see SOUNDS at the bottom).
"""
from __future__ import annotations

import json

import numpy as np

import gen_assets as G

SR = G.SR
N = 64           # big surfaces
M = 32           # small plants
SS = 4           # supersampling for drawn shapes
ASSETS = G.ROOT


# =============================================================================
# Helpers
# =============================================================================

def _blk(name: str):
    return G.TEX / "block" / f"{name}.png"


def _ref(name: str) -> str:
    return f"backrooms:block/{name}"


def _noise(rng, n, beta=2.0, aniso=(1.0, 1.0), kmin=1.0, kmax=None):
    return G.fnoise(rng, (n, n), beta=beta, aniso=aniso, kmin=kmin, kmax=kmax)


def _grid(n, h=None):
    return G.grid((h or n, n))


def _relief(h, k, light=(-0.7, -0.7)):
    """Shading of a (seamless) height map lit from the upper left: + where it faces the light."""
    dx = (np.roll(h, -1, axis=1) - np.roll(h, 1, axis=1)) * 0.5
    dy = (np.roll(h, -1, axis=0) - np.roll(h, 1, axis=0)) * 0.5
    return -(dx * light[0] + dy * light[1]) * k


def _down(img, f):
    """Box downsample by f (anti-aliasing for shapes drawn at f x resolution)."""
    h, w = img.shape[:2]
    if img.ndim == 2:
        return img.reshape(h // f, f, w // f, f).mean(axis=(1, 3))
    return img.reshape(h // f, f, w // f, f, img.shape[2]).mean(axis=(1, 3))


def _col(c):
    return np.asarray(c, float)


def _save(img, name):
    G.save_png(np.clip(img, 0, 255), _blk(name))


def _cutout(rgb, alpha, cut=0.5):
    """RGBA with a hard alpha edge (blocks render cutout); the colour under the clear pixels is
    spread from the nearby opaque ones so mipmaps don't get dark or bright fringes."""
    a = (alpha >= cut).astype(float)
    w = G.blur(a, 2.0) + 1e-6
    bleed = G.blur(rgb * a[..., None], 2.0) / w[..., None]
    out = np.where(a[..., None] > 0, rgb, bleed)
    return np.concatenate([out, a[..., None] * 255.0], axis=2)


def _composite(dst_rgb, dst_a, rgb, a):
    """Paints (rgb, a) over the canvas (straight alpha)."""
    a3 = a[..., None]
    out_a = a + dst_a * (1 - a)
    out_rgb = (rgb * a3 + dst_rgb * (dst_a * (1 - a))[..., None]) / np.maximum(out_a, 1e-6)[..., None]
    return out_rgb, out_a


def _leaf_shape(X, Y, n, bx, by, ang, L, W, wrap=True, tip=0.75, blunt=0.25):
    """Local coordinates and coverage of one leaf drawn from (bx, by) toward angle ang (radians,
    0 = +x, image y down), length L, widest W. Returns (t along 0..1, s across, half width, cover)."""
    dx = G.wrapd(X - bx, n) if wrap else X - bx
    dy = G.wrapd(Y - by, n) if wrap else Y - by
    ca, sa = np.cos(ang), np.sin(ang)
    t = (dx * ca + dy * sa) / L
    s = -dx * sa + dy * ca
    tc = np.clip(t, 0, 1)
    half = W * 0.5 * np.sin(np.pi * tc ** tip) ** 0.85 * (1 - blunt * tc)
    half = np.where((t > 0) & (t < 1), half, 0.0)
    cover = np.clip((half - np.abs(s)) / 1.0 + 0.5, 0, 1) * (half > 0)
    return t, s, half, cover


def _wrapped_strokes(n, ss, strokes, width_fn=None):
    """Anti-aliased mask of polylines drawn seamlessly (each drawn at the 9 wrapped offsets).
    strokes: list of (points[(x, y)], width) in final pixels."""
    from PIL import Image, ImageDraw
    big = n * ss
    im = Image.new("L", (big, big), 0)
    d = ImageDraw.Draw(im)
    for pts, w in strokes:
        for ox in (-n, 0, n):
            for oy in (-n, 0, n):
                p = [((x + ox) * ss, (y + oy) * ss) for x, y in pts]
                d.line(p, fill=255, width=max(1, int(round(w * ss))), joint="curve")
    return np.asarray(im.resize((n, n), Image.BOX), float) / 255.0


def _walk(rng, x, y, ang, steps, step, wiggle):
    pts = [(x, y)]
    for _ in range(steps):
        ang += rng.normal(0, wiggle)
        x += np.cos(ang) * step
        y += np.sin(ang) * step
        pts.append((x, y))
    return pts


def _voronoi_edges(rng, n, count, jitter=1.0):
    """Seamless F2-F1 distance (px) of random cells: small along the cell borders (cracks)."""
    X, Y = _grid(n)
    pts = rng.uniform(0, n, size=(count, 2))
    d = []
    for px, py in pts:
        dx = G.wrapd(X - px, n)
        dy = G.wrapd(Y - py, n) * jitter
        d.append(np.hypot(dx, dy))
    d = np.sort(np.stack(d), axis=0)
    return d[1] - d[0], d[0]


# =============================================================================
# The giant tree's bark
# =============================================================================

# Warm, wet, dark browns throughout (forest.png's trunks): no greys, which read as stone.
FURROW = (16, 9, 6)               # the black-brown bottom of the furrows
PLATE = (66, 41, 27)              # wet dark brown of the plate tops
PLATE_RED = (84, 44, 28)          # a reddish cast (the trunks in the forest photograph)
PLATE_DARK = (46, 30, 21)         # older, darker plates
ALGAE = (46, 66, 28)
LICHEN = (104, 108, 66)           # olive lichen (a grey one made the bark look like stone)
WET = (44, 36, 28)                # added on the wet, shiny plate tops


def _bark_height(rng, n, ridges=6, cracks=3, crack_w=0.993, warp=3.0):
    """Height of bark (1 = plate top, 0 = bottom of a furrow) with its grain running up and down:
    long plates between deep furrows, broken across by cracks at different heights."""
    X, Y = _grid(n)
    w1 = _noise(rng, n, beta=3.2, aniso=(1.0, 3.0))
    w2 = _noise(rng, n, beta=2.4)
    u = X + warp * w1 + 1.4 * w2
    f = 0.5 + 0.5 * np.cos(2 * np.pi * ridges * u / n)
    # Rounded plates (not flat-topped), with a shallow groove or two along each.
    plate = G.smoothstep(0.02, 0.6, f)
    sub = 0.5 + 0.5 * np.cos(2 * np.pi * ridges * 3 * u / n + 1.3 * w2)
    k = np.floor(ridges * u / n + 0.5).astype(int) % ridges
    offs = rng.uniform(0, 1, size=(ridges, cracks))
    keep = rng.uniform(0, 1, size=(ridges, cracks)) < 0.7
    v = Y + 3.0 * _noise(rng, n, beta=3.0, aniso=(3.0, 1.0))
    crack = np.zeros((n, n))
    for c in range(cracks):
        # Each plate breaks across at its own heights, and not at every one.
        seg = 0.5 + 0.5 * np.cos(2 * np.pi * (v / n + offs[k, c]))
        crack = np.maximum(crack, G.smoothstep(crack_w, 0.997, seg) * keep[k, c])
    crack *= plate
    # Long fibres along the grain: the strong up-and-down structure of the bark.
    fibre = _noise(rng, n, beta=1.2, aniso=(1.0, 9.0))
    pits = G.smoothstep(1.8, 2.8, _noise(rng, n, beta=1.0, kmin=6))
    h = plate ** 0.7 * (1 - 0.8 * crack) - 0.2 * sub * plate + 0.14 * fibre * plate - 0.2 * pits * plate
    return np.clip(h, 0, 1), plate, crack


def _bark_colour(rng, n, h, plate, wet=1.0, red=0.5):
    """Colours bark by height: black furrows, wet dark-brown plates with a red cast or darker with
    age, a shine on the plate tops facing the light."""
    mott = G.smoothstep(-1.2, 1.2, _noise(rng, n, beta=2.6))
    old = G.smoothstep(0.3, 1.6, _noise(rng, n, beta=2.2))
    top = G.lerp(PLATE, PLATE_RED, (mott * red)[..., None])
    top = G.lerp(top, PLATE_DARK, (old * 0.35)[..., None])
    depth = G.smoothstep(0.0, 0.85, h)
    img = G.lerp(FURROW, top, depth[..., None])
    rel = _relief(h, 30.0)
    img = G.add_lum(img, rel)
    fine = _noise(rng, n, beta=0.9, kmin=8)
    img = G.add_lum(img, fine * 3.0 * depth)
    shine = np.clip(_relief(h, 1.6), 0, 1) * plate * wet
    img = img + shine[..., None] * _col(WET)
    return img


def _bark(seed, n=N, variant=0):
    rng = G.rng_for(seed)
    h, plate, crack = _bark_height(rng, n, cracks=4 if variant == 3 else 3, crack_w=0.985 if variant == 3 else 0.993)
    img = _bark_colour(rng, n, h, plate, red=0.65 if variant != 2 else 0.35)
    X, Y = _grid(n)
    if variant == 1:
        # Green algae: wet streaks running down out of the cracks and along the furrows.
        streak = G.smoothstep(0.6, 1.8, _noise(rng, n, beta=1.6, aniso=(1.0, 6.0)))
        amount = np.clip(streak * (1.1 - 0.6 * h) + G.blur(crack, 1.2) * 1.5, 0, 0.85)
        img = G.lerp(img, _col(ALGAE) * (0.55 + 0.6 * h)[..., None], amount[..., None])
    elif variant == 2:
        # Olive lichen in a few blotches on the plates.
        blot = G.smoothstep(1.1, 1.6, _noise(rng, n, beta=2.0, kmin=2) + 0.4 * _noise(rng, n, beta=0.8, kmin=10))
        blot *= G.smoothstep(0.35, 0.7, h)
        lic = G.add_lum(G.fill(LICHEN, (n, n)), 8 * _noise(rng, n, beta=1.0, kmin=6))
        img = G.lerp(img, lic, (blot * 0.6)[..., None])
    elif variant == 3:
        img = img * 0.85
    return img


def tex_l227flora_bark() -> None:
    """The giant tree's bark (4 versions picked at random per block) and its 'end' face. The end
    is the same ridged bark (another piece of it): stepped faces of a branch then read as one bark
    surface, never as cut blocks."""
    for i in range(4):
        _save(_bark(22700 + i, N, i), f"l227_bark_{i}")
    _save(_bark(22705, N, 0), "l227_bark_end")


# =============================================================================
# Moss, soil, grass, ash
# =============================================================================

# Lush but misty: darker, a little desaturated (the in-game light adds its own green).
MOSS_DEEP = (22, 40, 18)
MOSS_MID = (44, 72, 30)
MOSS_BRIGHT = (72, 104, 44)
MOSS_TIP = (100, 122, 62)


def _moss(seed, n=N):
    """Thick wet moss in soft cushions: big rounded clumps with shaded hollows between them and
    only a faint fuzz of fronds (no per-pixel speckle)."""
    rng = G.rng_for(seed)
    cushion = _noise(rng, n, beta=2.8)
    # Rounded clumps: cells of a few px, each domed, merged with the large cushions.
    _, d0 = _voronoi_edges(rng, n, 22)
    dome = np.clip(1 - d0 / 7.5, 0, 1) ** 0.6
    frond = G.blur(_noise(rng, n, beta=0.8, kmin=8), 0.6)
    h = cushion * 0.7 + dome * 1.1 + frond * 0.12
    t = G.smoothstep(-1.2, 1.9, h)
    img = G.lerp(MOSS_DEEP, MOSS_MID, G.smoothstep(0.0, 0.55, t)[..., None])
    img = G.lerp(img, MOSS_BRIGHT, G.smoothstep(0.5, 1.0, t)[..., None])
    tips = G.smoothstep(1.2, 2.4, frond) * G.smoothstep(0.5, 0.9, t)
    img = G.lerp(img, MOSS_TIP, (tips * 0.35)[..., None])
    img = G.add_lum(img, _relief(G.blur(h, 0.8), 14.0))
    return img, h


def _root_soil(seed, n=N):
    rng = G.rng_for(seed)
    img = G.fill((40, 30, 23), (n, n))
    img = G.add_lum(img, _noise(rng, n, beta=2.4) * 6 + _noise(rng, n, beta=0.8, kmin=8) * 3.5, (1.0, 0.9, 0.8))
    # Damp darker patches.
    wet = G.smoothstep(0.4, 1.6, _noise(rng, n, beta=2.8))
    img = img * (1 - 0.3 * wet)[..., None]
    # Fine roots: thin pale threads wandering through it, and a few thicker dark ones.
    strokes = []
    for _ in range(26):
        pts = _walk(rng, rng.uniform(0, n), rng.uniform(0, n), rng.uniform(0, 2 * np.pi), 12, 1.6, 0.35)
        strokes.append((pts, rng.uniform(0.35, 0.7)))
    pale = _wrapped_strokes(n, SS, strokes)
    img = G.lerp(img, _col((132, 104, 74)), (pale * 0.75)[..., None])
    strokes = []
    for _ in range(5):
        pts = _walk(rng, rng.uniform(0, n), rng.uniform(0, n), rng.uniform(0, 2 * np.pi), 16, 2.0, 0.2)
        strokes.append((pts, rng.uniform(1.1, 1.8)))
    dark = _wrapped_strokes(n, SS, strokes)
    img = G.lerp(img, _col((62, 40, 28)), (dark * 0.85)[..., None])
    img = G.add_lum(img, _relief(G.blur(dark, 0.8), 25.0))
    # Crumbs of rotten wood.
    crumbs = G.smoothstep(2.3, 3.0, _noise(rng, n, beta=0.5, kmin=12))
    img = G.lerp(img, _col((92, 52, 32)), (crumbs * 0.8)[..., None])
    return img


# Deep, cool, slightly misty rainforest greens (the reference photo's undergrowth is dark).
GRASS_COLS = ((36, 64, 30), (46, 78, 34), (56, 90, 40), (30, 54, 26), (66, 98, 46), (50, 74, 36))
GRASS_DARK = (24, 40, 20)
GRASS_LIGHT = (70, 100, 50)


def _grass_top(seed, n=N):
    """Forest grass seen from above: soft tufts several pixels across, each lit on top and shaded
    at its foot, with long blades laid over them at low contrast (no per-pixel speckle)."""
    rng = G.rng_for(seed)
    _, d0 = _voronoi_edges(rng, n, 30)
    tuft = np.clip(1 - d0 / 7.0, 0, 1) ** 0.7
    big = _noise(rng, n, beta=2.8)
    h = tuft + 0.35 * big
    out = G.lerp(GRASS_DARK, GRASS_LIGHT, G.smoothstep(0.0, 1.3, h)[..., None])
    out = G.add_lum(out, _relief(G.blur(h, 1.0), 12.0))
    # Long blades over the tufts, drawn softly.
    from PIL import Image, ImageDraw
    bigpx = n * SS
    im = Image.new("RGBA", (bigpx, bigpx), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for _ in range(260):
        x, y = rng.uniform(0, n), rng.uniform(0, n)
        ang = rng.uniform(0, 2 * np.pi)
        ln = rng.uniform(4.0, 8.0)
        col = np.array(GRASS_COLS[rng.integers(len(GRASS_COLS))], float) * rng.uniform(0.9, 1.1)
        c = tuple(int(v) for v in np.clip(col, 0, 255)) + (255,)
        for ox in (-n, 0, n):
            for oy in (-n, 0, n):
                x0, y0 = (x + ox) * SS, (y + oy) * SS
                d.line([(x0, y0), (x0 + np.cos(ang) * ln * SS, y0 + np.sin(ang) * ln * SS)], fill=c,
                       width=int(rng.uniform(0.8, 1.3) * SS))
    arr = np.asarray(im.resize((n, n), Image.BOX), float)
    a = arr[..., 3] / 255.0
    blades = arr[..., :3] / np.maximum(a, 1e-6)[..., None]
    out = G.lerp(out, blades, (a * 0.45)[..., None])
    return G.blur(out, 0.45)


def tex_l227flora_ground() -> None:
    """Moss, root soil, forest grass (top and side) and the bleached ash under the sunbeams."""
    moss, _ = _moss(22710)
    _save(moss, "l227_moss")
    _save(_root_soil(22711), "l227_root_soil")
    _save(_grass_top(22712), "l227_forest_grass")

    # Grass side: root soil with the grass hanging over the top edge in a ragged fringe.
    rng = G.rng_for(22713)
    soil = _root_soil(22714)
    X, Y = _grid(N)
    edge = 9 + 3.0 * np.interp(X[0], np.arange(N) + 0.5, G.fnoise(rng, (1, N), beta=1.6)[0])
    blades = np.zeros((N, N))
    for x in range(N):
        for _ in range(2):
            ln = edge[x] + rng.uniform(-2, 7)
            blades[:, x] = np.maximum(blades[:, x], (Y[:, x] < ln).astype(float) * rng.uniform(0.7, 1.0))
    blades = np.maximum(blades, (Y < edge[None, :] - 2).astype(float))
    gcol = G.lerp(G.fill((34, 60, 28), (N, N)), G.fill((66, 98, 46), (N, N)),
                  G.smoothstep(-1, 1, G.blur(_noise(rng, N, beta=1.2, kmin=4), 0.6))[..., None])
    gcol = gcol * (1.05 - 0.4 * np.clip(Y / 20.0, 0, 1))[..., None]
    side = G.lerp(soil, gcol, blades[..., None])
    side = G.add_lum(side, -18 * G.smoothstep(0.0, 2.0, Y - (edge[None, :] + 3)) * G.smoothstep(6.0, 0.0, Y - (edge[None, :] + 3)))
    _save(side, "l227_forest_grass_side")

    # Ash: bleached grey-white powder in soft drifts, a few fine cracks, charred flecks and fibres.
    rng = G.rng_for(22715)
    img = G.fill((190, 187, 180), (N, N))
    drift = _noise(rng, N, beta=2.6)
    img = G.add_lum(img, drift * 9 + _noise(rng, N, beta=0.8, kmin=8) * 4 + _relief(drift, 6.0))
    grey = G.smoothstep(0.5, 1.6, _noise(rng, N, beta=2.6))
    img = G.lerp(img, _col((142, 138, 132)), (grey * 0.5)[..., None])
    edge, _ = _voronoi_edges(rng, N, 6)
    cracks = G.smoothstep(0.7, 0.1, edge) * G.smoothstep(0.0, 1.0, _noise(rng, N, beta=2.0))
    img = G.add_lum(img, -28 * cracks)
    flecks = G.smoothstep(2.4, 3.1, _noise(rng, N, beta=0.4, kmin=14))
    img = G.lerp(img, _col((34, 30, 28)), (flecks * 0.9)[..., None])
    strokes = [(_walk(rng, rng.uniform(0, N), rng.uniform(0, N), rng.uniform(0, 6.3), 6, 1.5, 0.3), 0.5) for _ in range(10)]
    img = G.lerp(img, _col((96, 90, 84)), (_wrapped_strokes(N, SS, strokes) * 0.5)[..., None])
    _save(img, "l227_ash")


# =============================================================================
# Mossy bark, the Palette, heartwood, growth rings, knot, resin
# =============================================================================

def _moss_over_edge(rng, n, bark, h, reach=0.42):
    """Moss coming over the top edge and creeping down, further in the furrows, in drips."""
    X, Y = _grid(n)
    moss, mh = _moss(22720, n)
    yy = Y / n
    drip = G.smoothstep(0.6, 1.8, _noise(rng, n, beta=1.5, aniso=(1.0, 5.0)))
    m = (reach - yy) * 3.2 + 0.45 * _noise(rng, n, beta=2.0) + 0.9 * (1 - h) * (yy < 0.75) + drip * (0.9 - yy)
    m = G.smoothstep(0.0, 0.35, m)
    img = G.lerp(bark, moss, m[..., None])
    # A shadow just under the moss's edge (it sits on the bark).
    shade = np.clip(np.roll(m, 2, axis=0) - m, 0, 1)
    return G.add_lum(img, -30 * shade)


def tex_l227flora_wood() -> None:
    """Mossy bark (grain vertical and horizontal), the Palette, heartwood, growth rings, knot, resin."""
    rng = G.rng_for(22721)
    h, plate, _ = _bark_height(G.rng_for(22700), N)
    base = _bark(22700, N, 0)
    _save(_moss_over_edge(rng, N, base, h), "l227_bark_mossy_v")
    rng = G.rng_for(22722)
    _save(_moss_over_edge(rng, N, np.rot90(base).copy(), np.rot90(h).copy()), "l227_bark_mossy_h")

    # The Palette: bark version 0 (the most common), with a barely visible pattern of overlapping
    # scales and a few pale specks. Look closely and the bark is not quite bark.
    X, Y = _grid(N)
    row = np.floor(Y / 8.0)
    sx = X + 4.0 * (row % 2)
    cell = np.hypot(G.wrapd(sx, 8.0), (Y % 8.0) - 1.0)
    scale = G.smoothstep(4.6, 5.2, cell) * G.smoothstep(6.2, 5.4, cell)
    pal = _bark(22700, N, 0)
    pal = G.add_lum(pal, 7.0 * scale * G.smoothstep(0.3, 0.8, h))
    specks = G.smoothstep(2.6, 3.2, _noise(G.rng_for(22723), N, beta=0.3, kmin=16))
    pal = G.lerp(pal, _col((122, 112, 96)), (specks * 0.5)[..., None])
    _save(pal, "l227_palette")

    # Heartwood: pale reddish-amber, fine grain along it, darker streaks and pale ray flecks.
    rng = G.rng_for(22724)
    img = G.fill((146, 88, 58), (N, N))
    grain = _noise(rng, N, beta=1.2, aniso=(1.0, 9.0))
    img = G.add_lum(img, grain * 9 + _noise(rng, N, beta=2.6) * 7, (1.0, 0.85, 0.7))
    streak = G.smoothstep(0.8, 1.8, _noise(rng, N, beta=2.0, aniso=(1.0, 6.0)))
    img = G.lerp(img, _col((104, 56, 38)), (streak * 0.55)[..., None])
    rays = G.smoothstep(2.0, 2.8, _noise(rng, N, beta=0.6, aniso=(5.0, 1.0), kmin=6))
    img = G.lerp(img, _col((176, 120, 82)), (rays * 0.5)[..., None])
    _save(img, "l227_heartwood")

    # Growth rings, nearly straight bands (one block is a sliver of a cut hundreds across):
    # 7 bands per block straight, 5 per block along the diagonal (the same spacing, ~9 px),
    # each a light earlywood band ending in a sharp dark latewood line, with radial checks.
    for name, s_of, period in (("l227_rings", lambda X, Y: Y, N / 7.0),
                               ("l227_rings_diag", lambda X, Y: X + Y, N / 5.0)):
        rng = G.rng_for(22725 if name == "l227_rings" else 22726)
        X, Y = _grid(N)
        s = s_of(X, Y) + 1.1 * _noise(rng, N, beta=3.0)
        ph = (s / period) % 1.0
        early = G.lerp(G.fill((178, 118, 76), (N, N)), G.fill((158, 98, 62), (N, N)),
                       G.smoothstep(-1, 1, _noise(rng, N, beta=2.5))[..., None])
        late = _col((92, 52, 34))
        band = G.smoothstep(0.55, 0.9, ph) * G.smoothstep(1.0, 0.93, ph)
        img = G.lerp(early, late, (band * 0.85)[..., None])
        img = G.add_lum(img, _noise(rng, N, beta=0.8, kmin=10) * 4)
        # Radial checks: short dark cracks across the bands.
        strokes = []
        for _ in range(4):
            x0, y0 = rng.uniform(0, N), rng.uniform(0, N)
            ang = (np.pi / 2 if name == "l227_rings" else np.pi / 4) + rng.normal(0, 0.12)
            strokes.append((_walk(rng, x0, y0, ang, 8, 1.6, 0.08), rng.uniform(0.5, 0.9)))
        img = G.lerp(img, _col((40, 24, 18)), (_wrapped_strokes(N, SS, strokes) * 0.85)[..., None])
        _save(img, name)

    # Knothole: the ridges part round a raised lip of swirled wood and a black hollow.
    rng = G.rng_for(22727)
    X, Y = _grid(N)
    cx, cy = 32.0, 34.0
    rx, ry = 8.5, 13.0
    r = np.hypot((X - cx) / rx, (Y - cy) / ry)
    h, plate, _ = _bark_height(G.rng_for(22728), N)
    img = _bark_colour(G.rng_for(22729), N, h, plate)
    lip = G.smoothstep(2.3, 1.2, r) * G.smoothstep(0.75, 1.05, r)
    ang = np.arctan2((Y - cy) / ry, (X - cx) / rx)
    swirl = 0.5 + 0.5 * np.cos(r * 14.0 + 0.6 * np.sin(ang * 3))
    lipcol = G.lerp(_col((70, 48, 34)), _col((104, 72, 50)), swirl[..., None])
    img = G.lerp(img, lipcol, (lip * 0.9)[..., None])
    img = G.add_lum(img, 26 * lip * np.cos(ang + 2.4))
    hole = G.smoothstep(1.0, 0.85, r)
    inner = G.lerp(_col((6, 5, 4)), _col((40, 26, 18)), np.clip(r, 0, 1)[..., None] ** 3)
    img = G.lerp(img, inner, hole[..., None])
    _save(img, "l227_knot")

    # Resin: amber seeping out of cracks in the bark and running down in thin drips that end in a
    # bead; an overlay (cutout) drawn full-bright. Drawn at 4x for smooth edges.
    rng = G.rng_for(22730)
    n = N * SS
    X, Y = G.grid((n, n))
    alpha = np.zeros((n, n))
    glow = np.zeros((n, n))
    for i in range(5):
        x0, y0 = rng.uniform(10, N - 10) * SS, rng.uniform(6, N * 0.45) * SS
        ln = rng.uniform(10, N - 10 - y0 / SS) * SS
        w = rng.uniform(0.6, 1.1) * SS
        lean = rng.normal(0, 0.06)
        wob = x0 + lean * (Y - y0) + 0.6 * SS * np.sin((Y - y0) / SS * rng.uniform(0.2, 0.35))
        along = np.clip((Y - y0) / ln, 0, 1)
        width = w * (1.0 - 0.35 * along)
        run = np.clip(width - np.abs(X - wob) + 0.5, 0, 1) * (Y > y0) * (Y < y0 + ln)
        br = width * 2.1
        bead = np.clip(br - np.hypot(X - (x0 + lean * ln), Y - (y0 + ln)), 0, 1)
        sr = rng.uniform(2.5, 4.0) * SS
        seep_d = np.hypot((X - x0) / sr, (Y - y0) / (sr * 0.6))
        seep = np.clip((1.0 - seep_d) * 6, 0, 1)
        m = np.maximum(np.maximum(run, bead), seep)
        alpha = np.maximum(alpha, m)
        core = np.maximum.reduce([run * (1 - np.abs(X - wob) / (width + 1e-3)), bead * 0.9, seep * (1 - seep_d)])
        glow = np.maximum(glow, core)
    rgb = G.lerp(_col((140, 58, 14)), _col((244, 160, 52)), np.clip(glow, 0, 1)[..., None] ** 0.6)
    rgb = rgb + (G.smoothstep(0.7, 0.95, glow) * 70)[..., None] * _col((1.0, 0.92, 0.65))
    G.save_png(_cutout(_down(rgb, SS), _down(alpha, SS)), _blk("l227_resin"))


# =============================================================================
# The forest's trees: logs, leaves, ferns
# =============================================================================

def _log_side(seed, n=N):
    """Rainforest trunk bark: wet, warm dark brown (a shade lighter and finer than the giant
    tree's), strong vertical ridges, green algae running down the furrows. No grey and no lichen
    crust: on the buttress fins and snapped stumps those read as stone walls."""
    rng = G.rng_for(seed)
    h, plate, _ = _bark_height(rng, n, ridges=8, cracks=2, crack_w=0.995, warp=2.0)
    mott = G.smoothstep(-1.2, 1.2, _noise(rng, n, beta=2.6, aniso=(1.0, 3.0)))
    top = G.lerp(_col((74, 48, 32)), _col((92, 54, 34)), mott[..., None])
    img = G.lerp(_col((22, 13, 9)), top, G.smoothstep(0.0, 0.85, h)[..., None])
    img = G.add_lum(img, _relief(h, 24.0) + _noise(rng, n, beta=1.0, aniso=(1.0, 10.0), kmin=4) * 4)
    img = img + (np.clip(_relief(h, 1.4), 0, 1) * plate)[..., None] * _col(WET)
    algae = G.smoothstep(0.6, 1.7, _noise(rng, n, beta=1.8, aniso=(1.0, 5.0))) * (1.0 - 0.6 * h)
    img = G.lerp(img, _col((42, 62, 30)), np.clip(algae, 0, 0.7)[..., None])
    return img


def _ellipse_leaf(rng, X, Y, n, bx, by, ang, L, W, col, veins=6, gloss=0.25, holes=0.0):
    """One broad, glossy leaf (rgb, coverage) with a pale midrib, darker veins and a sheen."""
    t, s, half, cover = _leaf_shape(X, Y, n, bx, by, ang, L, W)
    sn = s / np.maximum(half, 1e-3)
    shade = 0.82 + gloss * (0.6 - np.abs(sn + 0.35)) + 0.1 * t
    rgb = _col(col)[None, None, :] * shade[..., None]
    mid = np.exp(-(s / (0.7 * SS)) ** 2) * (t < 0.96)
    rgb = G.lerp(rgb, _col(col) * 1.45 + 12, (mid * 0.75)[..., None])
    v = (t * veins - np.abs(sn) * 0.9) % 1.0
    vein = np.exp(-((v - 0.5) / 0.07) ** 2) * (np.abs(sn) < 0.92) * (t > 0.06)
    rgb = rgb * (1 - 0.22 * vein)[..., None]
    if holes > 0:
        hn = G.smoothstep(2.0, 2.4, _noise(rng, n, beta=0.8, kmin=6))
        cover = cover * (1 - hn * holes)
    # Petiole: a short stalk behind the leaf's base.
    stalk = (t < 0) & (t > -0.18) & (np.abs(s) < 0.6 * SS)
    cover = np.maximum(cover, stalk.astype(float))
    rgb = np.where(stalk[..., None], _col(col) * 0.75, rgb)
    return rgb, cover


LEAF_GREENS = ((38, 78, 30), (52, 98, 34), (30, 64, 26), (70, 118, 42), (46, 90, 40), (58, 108, 30), (84, 132, 48))


def _leaves_frames(seed, frames=8):
    """The big-leaf canopy: 14 broad tropical leaves (and their wrapped copies, so the texture
    tiles), each swinging a few degrees about its stalk over the animation."""
    rng = G.rng_for(seed)
    n = N * SS
    X, Y = _grid(n)
    leaves = []
    for i in range(22):
        L = rng.uniform(17, 30) * SS
        leaves.append(dict(bx=rng.uniform(0, n), by=rng.uniform(0, n), ang=rng.uniform(0, 2 * np.pi), L=L,
                           W=L * rng.uniform(0.38, 0.5), col=np.array(LEAF_GREENS[i % len(LEAF_GREENS)]) * rng.uniform(0.85, 1.1),
                           ph=rng.uniform(0, 2 * np.pi), amp=rng.uniform(0.035, 0.07), dark=i < 5))
    out = []
    for f in range(frames):
        rgb = np.zeros((n, n, 3))
        a = np.zeros((n, n))
        for lf in leaves:
            ang = lf["ang"] + lf["amp"] * np.sin(2 * np.pi * f / frames + lf["ph"])
            col = lf["col"] * (0.7 if lf["dark"] else 1.0)
            lr, la = _ellipse_leaf(rng, X, Y, n, lf["bx"], lf["by"], ang, lf["L"], lf["W"], col)
            rgb, a = _composite(rgb, a, lr, la)
        out.append(_cutout(_down(rgb, SS), _down(a, SS)))
    return out


def _frond(X, Y, n, x0, y0, ang, L, droop, pinnae, cols, width, wrap=True, rachis_col=(34, 44, 22), thin=0.0):
    """A fern frond: a curving rachis from (x0, y0) along ang, bending by droop, with paired
    leaflets (pinnae) alternating between the colours in cols, shorter toward the tip."""
    rgb = np.zeros(X.shape + (3,))
    a = np.zeros(X.shape)
    steps = pinnae
    px, py, pa = x0, y0, ang
    seg = L / steps
    for k in range(steps):
        frac = k / steps
        pa_k = pa + droop * frac
        nx, ny = px + np.cos(pa_k) * seg, py + np.sin(pa_k) * seg
        # Rachis piece.
        dx = (G.wrapd(X - px, n) if wrap else X - px)
        dy = (G.wrapd(Y - py, n) if wrap else Y - py)
        vx, vy = nx - px, ny - py
        tt = np.clip((dx * vx + dy * vy) / (vx * vx + vy * vy), 0, 1)
        dist = np.hypot(dx - tt * vx, dy - tt * vy)
        rw = 0.55 * SS * (1 - 0.6 * frac)
        m = np.clip(rw - dist + 0.5, 0, 1)
        rgb, a = _composite(rgb, a, np.broadcast_to(_col(rachis_col), rgb.shape), m)
        # Two leaflets here, angled forward.
        plen = width * (1 - 0.75 * frac ** 1.3) * (0.6 + 0.4 * min(1.0, frac * 6))
        for side in (-1, 1):
            la = pa_k + side * (1.15 - 0.25 * frac)
            col = cols[(k + (side > 0)) % len(cols)]
            _, s, half, cover = _leaf_shape(X, Y, n, px, py, la, plen, plen * (0.42 - thin), wrap=wrap, tip=0.9, blunt=0.1)
            shade = 0.85 + 0.25 * (0.5 - np.abs(s / np.maximum(half, 1e-3)))
            rgb, a = _composite(rgb, a, _col(col)[None, None, :] * shade[..., None], cover)
        px, py = nx, ny
    return rgb, a


FERN_LIME = (118, 162, 46)
FERN_TEAL = (42, 120, 104)
FERN_GREEN = (64, 128, 44)


def _fern_frames(seed, frames=8):
    """The fern-like foliage of the wiki drawing: drooping fronds with lime and blue-green
    leaflets on dark stems, tiling, each frond swaying about its base."""
    rng = G.rng_for(seed)
    n = N * SS
    X, Y = _grid(n)
    fronds = []
    for i in range(12):
        fronds.append(dict(x=rng.uniform(0, n), y=rng.uniform(0, n), ang=np.pi / 2 + rng.normal(0, 0.7),
                           L=rng.uniform(26, 40) * SS, droop=rng.normal(0, 0.6), ph=rng.uniform(0, 2 * np.pi),
                           cols=(FERN_LIME, FERN_TEAL, FERN_GREEN) if i % 3 else (FERN_GREEN, FERN_TEAL),
                           shade=rng.uniform(0.65, 1.05)))
    out = []
    for f in range(frames):
        rgb = np.zeros((n, n, 3))
        a = np.zeros((n, n))
        for fr in sorted(fronds, key=lambda d: d["shade"]):
            sw = 0.06 * np.sin(2 * np.pi * f / frames + fr["ph"])
            cols = tuple(tuple(np.array(c) * fr["shade"]) for c in fr["cols"])
            r, al = _frond(X, Y, n, fr["x"], fr["y"], fr["ang"] + sw, fr["L"], fr["droop"], 12, cols, 7.5 * SS)
            rgb, a = _composite(rgb, a, r, al)
        out.append(_cutout(_down(rgb, SS), _down(a, SS)))
    return out


def _save_anim(frames, name, frametime=6):
    G.save_png(np.concatenate(frames, axis=0), _blk(name))
    meta = {"animation": {"frametime": frametime, "interpolate": True}}
    (_blk(name).with_suffix(".png.mcmeta")).write_text(json.dumps(meta, indent=2) + "\n", newline="\n")


def tex_l227flora_trees() -> None:
    """Forest tree logs (plain, mossy, end), the big-leaf canopy and the fern foliage (animated)."""
    _save(_log_side(22740), "l227_tree_log")
    rng = G.rng_for(22741)
    log = _log_side(22742)
    moss, mh = _moss(22743)
    cover = G.smoothstep(-0.3, 0.6, _noise(rng, N, beta=2.2, aniso=(1.0, 2.5)) + 0.35 * mh)
    img = G.lerp(log, moss, cover[..., None])
    img = G.add_lum(img, -24 * np.clip(np.roll(cover, 1, axis=0) - cover, 0, 1))
    _save(img, "l227_tree_log_mossy")

    # Log end (32): rings round the pith and a rim of bark.
    rng = G.rng_for(22744)
    X, Y = _grid(M)
    r = np.hypot(X - 16.3, Y - 15.7) + 0.6 * _noise(rng, M, beta=2.6)
    img = G.lerp(G.fill((168, 128, 88), (M, M)), G.fill((120, 84, 56), (M, M)),
                 (0.5 + 0.5 * np.cos(r * 1.9))[..., None] * 0.6)
    img = G.lerp(img, _col((84, 72, 60)), G.smoothstep(13.0, 14.5, r)[..., None])
    _save(img, "l227_tree_log_end")

    _save_anim(_leaves_frames(22745), "l227_leaves")
    _save_anim(_fern_frames(22746), "l227_fern_leaves")


# =============================================================================
# Undergrowth and the small plants (32 px, cutout), drawn at 4x and reduced
# =============================================================================

def _canvas(n=M, h=None):
    big_w, big_h = n * SS, (h or n) * SS
    X, Y = G.grid((big_h, big_w))
    return X, Y, np.zeros((big_h, big_w, 3)), np.zeros((big_h, big_w))


def _stem(X, Y, pts, w, col):
    """Coverage and colour of a polyline (in big pixels) of width w."""
    a = np.zeros(X.shape)
    for (x0, y0), (x1, y1) in zip(pts[:-1], pts[1:]):
        vx, vy = x1 - x0, y1 - y0
        tt = np.clip(((X - x0) * vx + (Y - y0) * vy) / max(vx * vx + vy * vy, 1e-6), 0, 1)
        d = np.hypot(X - x0 - tt * vx, Y - y0 - tt * vy)
        a = np.maximum(a, np.clip(w / 2 - d + 0.5, 0, 1))
    return np.broadcast_to(_col(col), X.shape + (3,)), a


def _leaf_at(X, Y, x, y, ang, L, W, col, veins=5, tip=0.75, blunt=0.25, gloss=0.25):
    t, s, half, cover = _leaf_shape(X, Y, 0, x, y, ang, L, W, wrap=False, tip=tip, blunt=blunt)
    sn = s / np.maximum(half, 1e-3)
    shade = 0.82 + gloss * (0.6 - np.abs(sn + 0.35)) + 0.08 * t
    rgb = _col(col)[None, None, :] * shade[..., None]
    mid = np.exp(-(s / (0.5 * SS)) ** 2) * (t < 0.95)
    rgb = G.lerp(rgb, _col(col) * 1.35 + 10, (mid * 0.6)[..., None])
    v = (t * veins - np.abs(sn) * 0.9) % 1.0
    rgb = rgb * (1 - 0.18 * np.exp(-((v - 0.5) / 0.08) ** 2) * (np.abs(sn) < 0.9))[..., None]
    return rgb, cover


def _finish(rgb, a, name):
    G.save_png(_cutout(_down(rgb, SS), _down(a, SS)), _blk(name))


def _blades(rng, X, Y, rgb, a, count, base_y, top, cols, w=(0.7, 1.3), lean=0.25, x_range=(1, 31), tips=True):
    """Grass blades rising from base_y (big px) up to heights in top (fraction of the image)."""
    hgt = X.shape[0]
    for _ in range(count):
        x = rng.uniform(*x_range) * SS
        h = rng.uniform(*top) * hgt
        bend = rng.normal(0, lean)
        pts = []
        for k in range(7):
            f = k / 6
            pts.append((x + bend * (f ** 2) * h * 0.6, base_y - f * h))
        col = np.array(cols[rng.integers(len(cols))], float) * rng.uniform(0.8, 1.1)
        width = rng.uniform(*w) * SS
        seg_rgb, seg_a = _stem(X, Y, pts, width, col)
        # Taper: thin toward the tip.
        if tips:
            frac = np.clip((base_y - Y) / max(h, 1), 0, 1)
            seg_a = seg_a * np.clip((1 - frac) * 1.6, 0, 1) ** 0.5
        rgb, a = _composite(rgb, a, seg_rgb * (0.85 + 0.3 * np.clip((base_y - Y) / max(h, 1), 0, 1))[..., None], seg_a)
    return rgb, a


TALL_COLS = ((48, 98, 34), (62, 116, 40), (78, 128, 46), (40, 84, 30), (92, 132, 54), (104, 124, 62))


def tex_l227flora_undergrowth() -> None:
    """Tall grass (both halves), giant fern frond, hanging fronds, hanging moss, liana, the four
    undergrowth kinds, the four flowers, bracket fungus, epiphyte, fallen leaf."""
    # Tall grass: one 32 x 64 picture split into the lower and upper half, so the blades join.
    rng = G.rng_for(22750)
    X, Y, rgb, a = _canvas(M, 2 * M)
    rgb, a = _blades(rng, X, Y, rgb, a, 46, 2 * M * SS, (0.55, 0.98), TALL_COLS, lean=0.35)
    rgb, a = _blades(rng, X, Y, rgb, a, 30, 2 * M * SS, (0.25, 0.6), TALL_COLS, w=(1.0, 1.7), lean=0.5)
    # A few seed heads near the top.
    for _ in range(6):
        x, y = rng.uniform(4, 28) * SS, rng.uniform(0.04, 0.22) * 2 * M * SS
        r2, a2 = _leaf_at(X, Y, x, y + 5 * SS, -np.pi / 2 + rng.normal(0, 0.2), 6 * SS, 1.6 * SS, (150, 140, 80), veins=0)
        rgb, a = _composite(rgb, a, r2, a2)
    full_rgb, full_a = _down(rgb, SS), _down(a, SS)
    G.save_png(_cutout(full_rgb[M:], full_a[M:]), _blk("l227_tall_grass_lower"))
    G.save_png(_cutout(full_rgb[:M], full_a[:M]), _blk("l227_tall_grass_upper"))

    # Giant fern frond (64): one big frond standing up the middle, used on tilted planes.
    n = N * SS
    X, Y = G.grid((n, n))
    rgb, a = _frond(X, Y, n, n * 0.5, n - 1, -np.pi / 2, n * 0.97, 0.25, 16, (FERN_GREEN, (78, 138, 46), FERN_TEAL),
                    13 * SS, wrap=False)
    G.save_png(_cutout(_down(rgb, SS), _down(a, SS)), _blk("l227_giant_fern"))

    # Hanging fronds (tile vertically) and their tip.
    for tip in (False, True):
        rng = G.rng_for(22751)
        X, Y, rgb, a = _canvas()
        for x in (8, 22):
            L = (M * (0.7 if tip else 1.6)) * SS
            r2, a2 = _frond(X, Y, M * SS, x * SS, -M * 0.3 * SS, np.pi / 2 + rng.normal(0, 0.08), L, 0.0, 11,
                            (FERN_LIME, FERN_TEAL, FERN_GREEN), 6 * SS, wrap=False)
            rgb, a = _composite(rgb, a, r2, a2)
        _finish(rgb, a, "l227_hanging_fronds_tip" if tip else "l227_hanging_fronds")

    # Hanging moss: many thin wavy strands (tiling vertically), and the wispy ends.
    for tip in (False, True):
        rng = G.rng_for(22752)
        X, Y, rgb, a = _canvas()
        for _ in range(34):
            x = rng.uniform(2, 30) * SS
            ph = rng.uniform(0, 2 * np.pi)
            end = (rng.uniform(0.3, 0.95) if tip else 1.2) * M * SS
            pts = [(x + 1.2 * SS * np.sin(2 * np.pi * yy / (M * SS) * 2 + ph), yy) for yy in np.linspace(0, M * SS, 17) if yy <= end]
            if len(pts) < 2:
                continue
            col = np.array(((84, 104, 44), (102, 118, 60), (66, 88, 38), (120, 128, 76))[rng.integers(4)], float)
            r2, a2 = _stem(X, Y, pts, rng.uniform(0.5, 1.0) * SS, col * rng.uniform(0.8, 1.1))
            rgb, a = _composite(rgb, a, r2, a2)
        _finish(rgb, a, "l227_hanging_moss_tip" if tip else "l227_hanging_moss")

    # Liana: a twisted woody vine down the middle with a few leaves, and its frayed end.
    for tip in (False, True):
        rng = G.rng_for(22753)
        X, Y, rgb, a = _canvas()
        end = (0.6 if tip else 1.0) * M * SS
        cx = 16 * SS + 1.0 * SS * np.sin(2 * np.pi * Y / (M * SS))
        width = 3.6 * SS * (1 - (0.6 * np.clip(Y / end, 0, 1) if tip else 0))
        m = np.clip(width / 2 - np.abs(X - cx) + 0.5, 0, 1) * (Y < end)
        twist = 0.5 + 0.5 * np.sin((Y * 0.9 / SS + (X - cx) / SS * 2.2))
        vine = G.lerp(_col((58, 44, 32)), _col((106, 86, 62)), twist[..., None])
        rgb, a = _composite(rgb, a, vine, m)
        if tip:
            for k in range(5):
                pts = _walk(rng, 16 * SS, end, np.pi / 2 + rng.normal(0, 0.5), 6, 1.6 * SS, 0.3)
                r2, a2 = _stem(X, Y, pts, 0.7 * SS, (88, 70, 52))
                rgb, a = _composite(rgb, a, r2, a2)
        for y, side in ((6, -1), (19, 1)) if not tip else ((8, 1),):
            r2, a2 = _leaf_at(X, Y, 16 * SS, y * SS, np.pi / 2 + side * 1.0, 11 * SS, 5.5 * SS, (48, 96, 34))
            rgb, a = _composite(rgb, a, r2, a2)
        _finish(rgb, a, "l227_liana_tip" if tip else "l227_liana")

    # Undergrowth 0: broadleaf seedlings, small stems with paired oval leaves.
    rng = G.rng_for(22754)
    X, Y, rgb, a = _canvas()
    for _ in range(5):
        x = rng.uniform(6, 26) * SS
        h = rng.uniform(10, 24) * SS
        r2, a2 = _stem(X, Y, [(x, M * SS), (x + rng.normal(0, 1.5) * SS, M * SS - h)], 0.8 * SS, (64, 90, 40))
        rgb, a = _composite(rgb, a, r2, a2)
        for k in range(3):
            yy = M * SS - h * (0.45 + 0.27 * k)
            for side in (-1, 1):
                col = np.array((58, 110, 42)) * rng.uniform(0.8, 1.15)
                r2, a2 = _leaf_at(X, Y, x, yy, -np.pi / 2 + side * rng.uniform(0.7, 1.2), rng.uniform(5, 8) * SS,
                                  rng.uniform(2.6, 3.6) * SS, col)
                rgb, a = _composite(rgb, a, r2, a2)
    _finish(rgb, a, "l227_undergrowth_0")

    # Undergrowth 1: an elephant ear, one huge heart-shaped leaf on its stalk (the model tilts it).
    X, Y, rgb, a = _canvas()
    r2, a2 = _stem(X, Y, [(16 * SS, 32 * SS), (16 * SS, 21 * SS)], 1.4 * SS, (70, 100, 50))
    rgb, a = _composite(rgb, a, r2, a2)
    # The blade: a heart (the classic curve, point up), notched where the stalk meets it, with
    # veins fanning from the notch, dark and glossy.
    hx = (X - 16 * SS) / (13.5 * SS)
    hy = (21.5 * SS - Y) / (13.5 * SS) * 1.15 - 0.15
    heart = (hx ** 2 + hy ** 2 - 1) ** 3 - hx ** 2 * hy ** 3
    cover = np.clip(-heart * 40, 0, 1)
    ang = np.arctan2(hx, hy + 0.2)
    vein = np.exp(-((np.sin(ang * 4.5)) / 0.12) ** 2) * (np.hypot(hx, hy) > 0.12)
    mid = np.exp(-(hx / 0.035) ** 2)
    shade = 0.8 + 0.3 * (0.5 - np.abs(hx)) + 0.15 * hy
    blade = _col((34, 80, 34))[None, None, :] * shade[..., None]
    blade = G.lerp(blade, _col((92, 140, 70)), (np.maximum(vein * 0.45, mid * 0.6))[..., None])
    rgb, a = _composite(rgb, a, blade, cover)
    _finish(rgb, a, "l227_undergrowth_1")

    # Undergrowth 2: a low fern, fronds fanning out from the ground.
    X, Y, rgb, a = _canvas()
    for k in range(5):
        ang = -np.pi / 2 + (k - 2) * 0.42
        r2, a2 = _frond(X, Y, M * SS, 16 * SS, 31 * SS, ang, 26 * SS, (k - 2) * 0.25, 9,
                        (FERN_GREEN, (80, 140, 50)), 6 * SS, wrap=False)
        rgb, a = _composite(rgb, a, r2, a2)
    _finish(rgb, a, "l227_undergrowth_2")

    # Undergrowth 3: a spiny palm sprout, narrow pointed leaflets fanning up.
    rng = G.rng_for(22755)
    X, Y, rgb, a = _canvas()
    for k in range(11):
        ang = -np.pi / 2 + rng.uniform(-1.1, 1.1)
        r2, a2 = _leaf_at(X, Y, 16 * SS, 31 * SS, ang, rng.uniform(16, 28) * SS, 2.2 * SS, np.array((40, 86, 36)) * rng.uniform(0.8, 1.2),
                          veins=0, tip=0.5, blunt=0.6)
        rgb, a = _composite(rgb, a, r2, a2)
    _finish(rgb, a, "l227_undergrowth_3")

    # Flowers of no known species.
    # 0: pale drooping bells on an arching stem.
    X, Y, rgb, a = _canvas()
    stem = [(6 * SS, 32 * SS), (9 * SS, 14 * SS), (16 * SS, 6 * SS), (25 * SS, 8 * SS)]
    r2, a2 = _stem(X, Y, stem, 0.9 * SS, (70, 98, 46))
    rgb, a = _composite(rgb, a, r2, a2)
    for side in (-1, 1):
        r2, a2 = _leaf_at(X, Y, 7 * SS, 30 * SS, -np.pi / 2 + side * 0.6, 12 * SS, 4 * SS, (52, 98, 40))
        rgb, a = _composite(rgb, a, r2, a2)
    for x, y in ((12, 10), (17, 7), (22, 8), (26, 10)):
        r2, a2 = _leaf_at(X, Y, x * SS, y * SS, np.pi / 2, 6.5 * SS, 4.6 * SS, (232, 228, 206), veins=0, tip=1.4, blunt=-0.3)
        rgb, a = _composite(rgb, a, r2, a2)
    _finish(rgb, a, "l227_flower_0")

    # 1: a glossy red spathe with a yellow spadix.
    X, Y, rgb, a = _canvas()
    r2, a2 = _stem(X, Y, [(16 * SS, 32 * SS), (15 * SS, 14 * SS)], 1.0 * SS, (60, 92, 40))
    rgb, a = _composite(rgb, a, r2, a2)
    for side in (-1, 1):
        r2, a2 = _leaf_at(X, Y, 16 * SS, 31 * SS, -np.pi / 2 + side * 0.9, 13 * SS, 6 * SS, (38, 84, 34))
        rgb, a = _composite(rgb, a, r2, a2)
    r2, a2 = _leaf_at(X, Y, 15 * SS, 15 * SS, -np.pi / 2 - 0.1, 13 * SS, 10 * SS, (196, 26, 30), veins=6, tip=0.55, gloss=0.5)
    rgb, a = _composite(rgb, a, r2, a2)
    r2, a2 = _stem(X, Y, [(15 * SS, 14 * SS), (19 * SS, 5 * SS)], 1.6 * SS, (232, 200, 70))
    rgb, a = _composite(rgb, a, r2, a2)
    _finish(rgb, a, "l227_flower_1")

    # 2: clusters of small blue-violet stars on thin stems.
    rng = G.rng_for(22756)
    X, Y, rgb, a = _canvas()
    for _ in range(4):
        x0 = rng.uniform(8, 24) * SS
        top = (rng.uniform(4, 12) * SS)
        r2, a2 = _stem(X, Y, [(x0, 32 * SS), (x0 + rng.normal(0, 2) * SS, top)], 0.7 * SS, (64, 96, 44))
        rgb, a = _composite(rgb, a, r2, a2)
        for _ in range(5):
            cx, cy = x0 + rng.normal(0, 2.5) * SS, top + rng.normal(0, 2.0) * SS
            ang = np.arctan2(Y - cy, X - cx)
            rr = np.hypot(X - cx, Y - cy)
            star = rr < (1.2 + 1.1 * (0.5 + 0.5 * np.cos(5 * ang))) * SS
            col = G.lerp(_col((96, 92, 220)), _col((236, 236, 250)), np.clip(1 - rr / (0.9 * SS), 0, 1)[..., None])
            rgb, a = _composite(rgb, a, col, star.astype(float))
    _finish(rgb, a, "l227_flower_2")

    # 3: the orange "eye": a ring of orange petals round a dark centre with a pale ring, on a
    # stalk that turns it toward you.
    X, Y, rgb, a = _canvas()
    r2, a2 = _stem(X, Y, [(16 * SS, 32 * SS), (16 * SS, 14 * SS)], 1.1 * SS, (58, 90, 40))
    rgb, a = _composite(rgb, a, r2, a2)
    for k in range(9):
        ang = k * 2 * np.pi / 9
        r2, a2 = _leaf_at(X, Y, 16 * SS, 11 * SS, ang, 8 * SS, 4 * SS, (232, 112, 30), veins=0)
        rgb, a = _composite(rgb, a, r2, a2)
    rr = np.hypot(X - 16 * SS, Y - 11 * SS) / SS
    eye = G.lerp(_col((20, 14, 10)), _col((214, 196, 150)), (G.smoothstep(2.0, 2.6, rr) * G.smoothstep(3.6, 3.0, rr))[..., None])
    eye = G.lerp(eye, _col((246, 236, 210)), (np.hypot(X - 15.2 * SS, Y - 10.2 * SS) < 0.6 * SS)[..., None].astype(float))
    rgb, a = _composite(rgb, a, eye, (rr < 3.6).astype(float))
    _finish(rgb, a, "l227_flower_3")

    # Bracket fungus: top (growth zones round the attached edge at the bottom) and pored underside.
    rng = G.rng_for(22757)
    X, Y = _grid(M)
    r = np.hypot(X - 16, (Y - 34) * 1.25) * 0.82 + 0.8 * _noise(rng, M, beta=2.4)
    zones = 0.5 + 0.5 * np.cos(r * 1.25)
    img = G.lerp(_col((124, 92, 48)), _col((176, 140, 80)), zones[..., None])
    img = G.lerp(img, _col((92, 82, 50)), (G.smoothstep(0.5, 1.5, _noise(rng, M, beta=2.2)) * 0.35)[..., None])
    img = G.lerp(img, _col((226, 210, 170)), G.smoothstep(31.0, 33.0, r)[..., None])
    _save(img, "l227_bracket_top")
    img = G.fill((214, 200, 168), (M, M))
    pores = 0.5 + 0.5 * np.cos(X * 2.1) * np.cos(Y * 2.1)
    img = G.add_lum(img, -26 * G.smoothstep(0.6, 0.9, pores) + _noise(rng, M, beta=2.0) * 6)
    _save(img, "l227_bracket_under")

    # Epiphyte: a bromeliad seen from the side, strap leaves in a vase, red at the heart.
    rng = G.rng_for(22758)
    X, Y, rgb, a = _canvas()
    for k in range(13):
        ang = -np.pi / 2 + (k - 6) * 0.2 + rng.normal(0, 0.05)
        inner = abs(k - 6) < 2
        col = (178, 40, 48) if inner else np.array((52, 100, 46)) * rng.uniform(0.8, 1.15)
        r2, a2 = _leaf_at(X, Y, 16 * SS, 31 * SS, ang, (14 if inner else rng.uniform(18, 26)) * SS, 4.2 * SS, col, veins=0,
                          tip=0.6, blunt=0.15)
        rgb, a = _composite(rgb, a, r2, a2)
    _finish(rgb, a, "l227_epiphyte")

    # Fallen leaf (64): one huge leaf lying across the block, going brown at the edges, holed.
    rng = G.rng_for(22759)
    n = N * SS
    X, Y = G.grid((n, n))
    r2, a2 = _ellipse_leaf(rng, X, Y, n, n * 0.12, n * 0.88, -np.pi / 4, n * 0.95, n * 0.42, (82, 98, 40), veins=9,
                           gloss=0.15, holes=0.9)
    _, s, half, _ = _leaf_shape(X, Y, n, n * 0.12, n * 0.88, -np.pi / 4, n * 0.95, n * 0.42)
    brown = G.smoothstep(0.55, 0.95, np.abs(s) / np.maximum(half, 1e-3)) + 0.4 * G.smoothstep(0.5, 1.5, _noise(rng, n, beta=2.4))
    r2 = G.lerp(r2, _col((112, 80, 40)), np.clip(brown, 0, 1)[..., None])
    G.save_png(_cutout(_down(r2, SS), _down(a2, SS)), _blk("l227_fallen_leaf"))


def tex_l227flora_hazards() -> None:
    """Lestron (growing, cut back, and the item) and GammaPali."""
    # Lestron: a clump of square stems with soft oval leaves, silvery purple-grey, and violet spikes.
    for cut in (False, True):
        rng = G.rng_for(22760)
        X, Y, rgb, a = _canvas()
        for k in range(7):
            x = (6 + k * 3.3 + rng.normal(0, 0.6)) * SS
            h = (rng.uniform(4, 8) if cut else rng.uniform(16, 28)) * SS
            top = 32 * SS - h
            r2, a2 = _stem(X, Y, [(x, 32 * SS), (x + rng.normal(0, 1.2) * SS, top)], 0.9 * SS, (96, 84, 104))
            rgb, a = _composite(rgb, a, r2, a2)
            pairs = 1 if cut else 4
            for j in range(pairs):
                yy = 32 * SS - h * (0.25 + 0.2 * j)
                for side in (-1, 1):
                    col = np.array((150, 126, 168)) * rng.uniform(0.85, 1.12)
                    r2, a2 = _leaf_at(X, Y, x, yy, -np.pi / 2 + side * rng.uniform(0.4, 0.85), rng.uniform(5, 7) * SS,
                                      rng.uniform(2.6, 3.4) * SS, col, veins=3, gloss=0.1)
                    # Velvety: the leaves are a little paler than their colour (a fine grey fuzz).
                    rgb, a = _composite(rgb, a, r2 + 18, a2)
            if not cut and k % 2 == 0:
                for j in range(6):
                    cy = top + j * 1.2 * SS
                    m = (np.hypot(X - x, Y - cy) < 1.1 * SS).astype(float)
                    rgb, a = _composite(rgb, a, np.broadcast_to(_col((128, 58, 176)), rgb.shape), m)
        _finish(rgb, a, "l227_lestron_cut" if cut else "l227_lestron")

    # The item: a little bundle of sprigs tied with a grass blade.
    X, Y, rgb, a = _canvas()
    rng = G.rng_for(22761)
    for k in range(4):
        x0, y0 = (8 + k * 1.5) * SS, 27 * SS
        x1, y1 = (20 + k * 2.5) * SS, (5 + k * 1.5) * SS
        r2, a2 = _stem(X, Y, [(x0, y0), (x1, y1)], 1.0 * SS, (96, 84, 104))
        rgb, a = _composite(rgb, a, r2, a2)
        for j in range(4):
            f = 0.35 + 0.17 * j
            px, py = x0 + (x1 - x0) * f, y0 + (y1 - y0) * f
            for side in (-1, 1):
                r2, a2 = _leaf_at(X, Y, px, py, -0.9 + side * 0.9, 6 * SS, 3 * SS, np.array((156, 128, 176)) * rng.uniform(0.9, 1.1), veins=3)
                rgb, a = _composite(rgb, a, r2 + 16, a2)
    r2, a2 = _stem(X, Y, [(10 * SS, 21 * SS), (15 * SS, 25 * SS)], 1.8 * SS, (120, 140, 60))
    rgb, a = _composite(rgb, a, r2, a2)
    G.save_png(_cutout(_down(rgb, SS), _down(a, SS)), G.TEX / "item" / "lestron.png")

    # GammaPali (32): top half = the stems and small leaves (cutout, on crossed planes); bottom
    # half = the glowing skin of its bulbs (opaque; the model draws the bulbs full-bright).
    rng = G.rng_for(22762)
    X, Y, rgb, a = _canvas(M, M // 2)
    for k in range(5):
        x = (5 + k * 5.5) * SS
        r2, a2 = _stem(X, Y, [(x, 16 * SS), (x + rng.normal(0, 1.5) * SS, rng.uniform(2, 7) * SS)], 0.9 * SS, (46, 70, 34))
        rgb, a = _composite(rgb, a, r2, a2)
        r2, a2 = _leaf_at(X, Y, x, 13 * SS, -np.pi / 2 + rng.choice((-1, 1)) * 1.0, 5 * SS, 2.4 * SS, (52, 88, 38))
        rgb, a = _composite(rgb, a, r2, a2)
    top = _cutout(_down(rgb, SS), _down(a, SS))
    X, Y = G.grid((M // 2, M))
    glow = 0.5 + 0.5 * np.cos((X % 8 - 4) / 4 * np.pi) * np.cos((Y % 8 - 4) / 4 * np.pi)
    skin = G.lerp(_col((150, 210, 70)), _col((236, 255, 170)), glow[..., None])
    veins = G.smoothstep(1.6, 2.2, np.abs(_noise(G.rng_for(22763), M, beta=1.2)[: M // 2]))
    skin = G.lerp(skin, _col((90, 150, 40)), (veins * 0.5)[..., None])
    bottom = np.concatenate([skin, np.full((M // 2, M, 1), 255.0)], axis=2)
    G.save_png(np.concatenate([top, bottom], axis=0), _blk("l227_gammapali"))


# =============================================================================
# The giant Venus flytrap (entity texture) and the particles
# =============================================================================

# The lobe's rounded outline: the same numbers as client/VenusFlytrapModel (LOBE_HALF_WIDTH,
# RIM_REACH), so the teeth there stand on the rim drawn here.
LOBE_HALF_WIDTH = 24.0
RIM_REACH = 0.97


def tex_l227flora_flytrap() -> None:
    """The giant Venus flytrap's two textures, 256 x 256 for client/VenusFlytrapModel's
    128 x 128 UV layout (2 texels per model pixel):
      venus_flytrap.png      the camouflage it wears lying open: the lobes' inner faces a dull,
                             mottled green-maroon under moss and leaf litter, like the forest floor
      venus_flytrap_fed.png  from the snap on: the inner faces wet red flesh
    Regions (model px):
      lobe plates A/B/C   (0,0)/(0,15)/(0,30), each a 48 x 13 x 2 box: inner face u 2-50 v 2-15,
                          outer face u 52-100. Each lobe is cut by alpha to a ragged half oval
                          (hinge at A, rim through C), and the thin edge faces are left clear, so
                          nothing about it is square.
      teeth               (120,0) lower piece, dull green; (124,0) upper piece, a paler green tip
      trigger hairs       (120,12)
      grass tufts         (0,104) flat plane (blades from the top edge), (20,104) upright plane
                          (blades from the left / right edge)
      stalk (0,46), crown (30,46), rosette leaves (0,70): a 10 x 1 x 30 box, leaf-shaped by alpha
    """
    P = 2
    for fed in (False, True):
        rng = G.rng_for(22770)
        img = np.zeros((256, 256, 3))
        alpha = np.full((256, 256), 255.0)

        def noise(h, w, beta, aniso=(1.0, 1.0), kmin=1.0):
            return G.fnoise(rng, (h, w), beta=beta, aniso=aniso, kmin=kmin)

        # ---- One lobe as a picture: x across (-24..24), s along from hinge (0) to rim (39),
        # rim at the top row. Rows/cols are texels.
        H, W = 39 * P, 48 * P
        X, Y = G.grid((H, W))
        x = X / P - 24.0
        s = 39.0 - Y / P
        p = s / 39.0
        wob = 0.5 * np.sin(x * 0.7 + p * 9.0) + 0.5 * np.sin(x * 0.31 - p * 5.0)
        half = LOBE_HALF_WIDTH * np.sqrt(np.clip(1.0 - (p / RIM_REACH) ** 2, 0, 1)) * (1 + 0.06 * wob)
        lobe_a = (np.abs(x) < half).astype(float) * 255.0

        # Outer face: dull green, darker veins fanning from the hinge, a little moss.
        outer = G.lerp(_col((48, 70, 32)), _col((70, 90, 42)), (p ** 1.5)[..., None])
        outer = G.add_lum(outer, noise(H, W, 2.2) * 6)
        ang = np.arctan2(x, s + 6.0)
        outer = outer * (1 - 0.18 * G.smoothstep(0.8, 0.98, 0.5 + 0.5 * np.cos(ang * 22)))[..., None]

        if fed:
            # Wet red flesh, crimson at the heart, paler toward the rim, dotted with glands.
            inner = G.lerp(_col((190, 42, 38)), _col((150, 110, 62)), G.smoothstep(0.6, 1.0, p)[..., None])
            inner = G.add_lum(inner, noise(H, W, 2.0) * 10)
            glands = G.smoothstep(1.6, 2.4, noise(H, W, 0.6, kmin=8))
            inner = G.lerp(inner, _col((116, 14, 22)), (glands * 0.7)[..., None])
            inner = inner + (G.smoothstep(1.2, 2.2, noise(H, W, 1.8)) * 36)[..., None]
        else:
            # Camouflage: dull olive mottled with maroon (the flesh showing through), a few dark
            # glands, moss cushions, and dead leaves lying on it.
            inner = G.add_lum(G.fill((60, 64, 38), (H, W)), noise(H, W, 2.4) * 7)
            maroon = G.smoothstep(0.4, 1.4, noise(H, W, 2.6) + 0.6 * (1 - p))
            inner = G.lerp(inner, _col((78, 42, 34)), (maroon * 0.45)[..., None])
            glands = G.smoothstep(2.2, 2.8, noise(H, W, 0.6, kmin=8))
            inner = G.lerp(inner, _col((56, 26, 22)), (glands * 0.6)[..., None])
            pts = rng.uniform((0, 0), (W, H), size=(9, 2))
            d = np.min([np.hypot(X - px, Y - py) for px, py in pts], axis=0)
            moss = np.clip(1 - d / (rng.uniform(9, 14)), 0, 1) ** 0.7
            mcol = G.lerp(_col((40, 62, 28)), _col((70, 96, 44)), G.smoothstep(0.2, 0.9, moss)[..., None])
            inner = G.lerp(inner, mcol, G.smoothstep(0.05, 0.3, moss + 0.15 * noise(H, W, 1.2, kmin=6))[..., None])
            # Dead leaves lying on it (texel units: 2 per model pixel).
            for _ in range(22):
                bx, by = rng.uniform(0, W), rng.uniform(0, H)
                L = rng.uniform(6, 13)
                col = ((96, 70, 40), (80, 60, 34), (104, 88, 46), (66, 70, 36))[rng.integers(4)]
                _, sx, hw, cover = _leaf_shape(X, Y, 0, bx, by, rng.uniform(0, 2 * np.pi), L, L * 0.42, wrap=False)
                leaf = _col(col)[None, None, :] * (0.85 + 0.2 * (0.5 - np.abs(sx / np.maximum(hw, 1e-3))))[..., None]
                inner = G.lerp(inner, leaf, cover[..., None] * 0.9)
            inner = G.add_lum(inner, noise(H, W, 1.2, kmin=4) * 4)

        for k in range(3):
            v0 = 15 * k
            rows = slice((26 - 13 * k) * P, (39 - 13 * k) * P)
            sy = slice((v0 + 2) * P, (v0 + 15) * P)
            img[sy, 2 * P:50 * P] = inner[rows]
            alpha[sy, 2 * P:50 * P] = lobe_a[rows]
            # The outer face shows x mirrored (it faces the other way).
            img[sy, 52 * P:100 * P] = outer[rows][:, ::-1]
            alpha[sy, 52 * P:100 * P] = lobe_a[rows][:, ::-1]
            # Thin edge faces: clear, so the cut-out outline has no straight edges.
            alpha[v0 * P:(v0 + 2) * P, 0:100 * P] = 0
            alpha[(v0 + 2) * P:(v0 + 15) * P, 0:2 * P] = 0
            alpha[(v0 + 2) * P:(v0 + 15) * P, 50 * P:52 * P] = 0

        def rect(u0, v0, u1, v1, colour):
            img[int(v0 * P):int(v1 * P), int(u0 * P):int(u1 * P)] = colour

        # Teeth: thin, dull green with paler green tips; trigger hairs.
        rect(120, 0, 122, 3.5, (66, 90, 44))
        rect(124, 0, 126, 3, (112, 140, 72))
        rect(120, 12, 125, 14.5, (92, 100, 62))

        # Grass tufts: one picture of blades rising from its bottom edge, turned for each face.
        tw = 5 * P
        TX, TY = G.grid((tw * SS, tw * SS))
        trgb = np.zeros((tw * SS, tw * SS, 3))
        ta = np.zeros((tw * SS, tw * SS))
        for _ in range(9):
            x0 = rng.uniform(0.15, 0.85) * tw * SS
            top = rng.uniform(0.0, 0.5) * tw * SS
            pts = [(x0, tw * SS), (x0 + rng.normal(0, 0.12) * tw * SS, top)]
            r2, a2 = _stem(TX, TY, pts, 0.18 * tw * SS / 2, np.array(GRASS_COLS[rng.integers(len(GRASS_COLS))]) * 1.1)
            trgb, ta = _composite(trgb, ta, r2, a2)
        tuft = _cutout(_down(trgb, SS), _down(ta, SS))
        for (u0, v0), turn in (((5, 104), "down"), ((10, 104), "down"), ((20, 109), "left"), ((25, 109), "right")):
            if turn == "down":
                t = tuft[::-1]
            elif turn == "left":
                t = np.rot90(tuft, k=-1)
            else:
                t = np.rot90(tuft, k=1)
            img[v0 * P:v0 * P + tw, u0 * P:u0 * P + tw] = t[..., :3]
            alpha[v0 * P:v0 * P + tw, u0 * P:u0 * P + tw] = t[..., 3]

        # Stalk, crown.
        sh, sw = 23 * P, 28 * P
        X2, _ = G.grid((sh, sw))
        stalk = G.add_lum(G.fill((62, 92, 38), (sh, sw)),
                          -20 * G.smoothstep(0.6, 1.0, 0.5 + 0.5 * np.cos(X2 / sw * 2 * np.pi * 7)) + noise(sh, sw, 1.6, (1.0, 6.0)) * 6)
        img[46 * P:69 * P, 0:28 * P] = stalk
        img[46 * P:61 * P, 30 * P:78 * P] = G.add_lum(G.fill((52, 76, 34), (15 * P, 48 * P)), noise(15 * P, 48 * P, 2.0) * 7)

        # Rosette leaves: the two broad faces leaf-shaped (base at the bottom), edges clear.
        lh, lw = 30 * P, 10 * P
        LX, LY = G.grid((lh, lw))
        t = 1 - LY / lh
        lhalf = 5.0 * P * np.sin(np.pi * np.clip(t, 0, 1) ** 0.8) ** 0.8 * (1 - 0.2 * t)
        leaf_a = (np.abs(LX - lw / 2) < lhalf).astype(float) * 255.0
        leaf = G.add_lum(G.lerp(_col((44, 72, 30)), _col((74, 98, 42)), t[..., None]),
                         -16 * np.exp(-((LX - lw / 2) / 1.2) ** 2) + noise(lh, lw, 2.0) * 5)
        alpha[70 * P:101 * P, 0:80 * P] = 0
        for u0 in (30, 40):
            img[70 * P:100 * P, u0 * P:(u0 + 10) * P] = leaf
            alpha[70 * P:100 * P, u0 * P:(u0 + 10) * P] = leaf_a

        out = _cutout(img, alpha / 255.0)
        G.save_png(out, G.TEX / "entity" / ("venus_flytrap_fed.png" if fed else "venus_flytrap.png"))


def tex_l227flora_particles() -> None:
    """GammaPali haze puffs (white: the particle tints them) and three falling leaves."""
    for i in range(2):
        rng = G.rng_for(22780 + i)
        X, Y = G.grid((M, M))
        r = np.hypot(X - 16, Y - 16) / 15.0
        edge = 1.0 + 0.25 * G.fnoise(rng, (M, M), beta=2.6)
        a = np.clip(1 - r / edge, 0, 1) ** 1.8 * (0.75 + 0.25 * G.fnoise(rng, (M, M), beta=2.0))
        img = np.concatenate([np.full((M, M, 3), 255.0), (np.clip(a, 0, 1) * 255)[..., None]], axis=2)
        G.save_png(img, G.TEX / "particle" / f"l227_spore_haze_{i}.png")
    for i, col in enumerate(((60, 112, 40), (88, 128, 44), (128, 120, 52))):
        n = 16 * SS
        X, Y = G.grid((n, n))
        rgb, a = _ellipse_leaf(G.rng_for(22782 + i), X, Y, n, n * 0.2, n * 0.8, -np.pi / 4 + 0.2 * (i - 1), n * 0.78,
                               n * 0.36, col, veins=4)
        G.save_png(_cutout(_down(rgb, SS), _down(a, SS)), G.TEX / "particle" / f"l227_falling_leaf_{i}.png")
    for name, count in (("l227_spore_haze", 2), ("l227_falling_leaf", 3)):
        path = ASSETS / "particles" / f"{name}.json"
        path.write_text(json.dumps({"textures": [f"backrooms:{name}_{i}" for i in range(count)]}, indent=2) + "\n", newline="\n")


# =============================================================================
# Models, blockstates, item models
# =============================================================================

def _json(rel: str, obj) -> None:
    path = ASSETS / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", newline="\n")


def _model(name: str, obj) -> None:
    _json(f"models/block/{name}.json", obj)


def _state(name: str, variants) -> None:
    _json(f"blockstates/{name}.json", {"variants": variants})


def _item_block(name: str, model: str) -> None:
    _json(f"models/item/{name}.json", {"parent": _ref(model)})


def _item_flat(name: str, texture: str) -> None:
    _json(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": texture}})


def _v(model: str, x: int = 0, y: int = 0, weight: int = 1) -> dict:
    v = {"model": _ref(model)}
    if x:
        v["x"] = x
    if y:
        v["y"] = y
    if weight != 1:
        v["weight"] = weight
    return v


SPINS = (0, 90, 180, 270)
# Blockstate turns that point a model built facing UP (a plant on top of something) any way.
FACE6 = {"up": (0, 0), "down": (180, 0), "north": (90, 0), "east": (90, 90), "south": (90, 180), "west": (90, 270)}
# Turns that point a model built facing NORTH any way (vanilla's observer/dispenser).
FACE6_NORTH = {"north": (0, 0), "east": (0, 90), "south": (0, 180), "west": (0, 270), "up": (270, 0), "down": (90, 0)}
FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


def _cube(faces: dict, render: str | None = None, extra=None, particle: str | None = None) -> dict:
    """A full-block model from per-face (texture, rotation) pairs; textures given by block name."""
    tex = {}
    fdef = {}
    for face, (t, rot) in faces.items():
        key = t.replace("l227_", "")
        tex[key] = _ref(t)
        f = {"uv": [0, 0, 16, 16], "texture": f"#{key}", "cullface": face}
        if rot:
            f["rotation"] = rot
        fdef[face] = f
    tex["particle"] = _ref(particle or next(iter(faces.values()))[0])
    m = {"parent": "minecraft:block/block", "textures": tex,
         "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": fdef}] + (extra or [])}
    if render:
        m["render_type"] = render
    return m


def _column_state(name_for, extra_y_spins=True, weights=None, key=""):
    """Pillar blockstate (AXIS) with random versions: each model may come up turned (about its own
    axis) so the same bark never lines up."""
    def entries(axis):
        out = []
        for i, model in enumerate(name_for):
            w = weights[i] if weights else 1
            if axis == "y":
                for y in (SPINS if extra_y_spins else (0,)):
                    out.append(_v(model, 0, y, w))
            elif axis == "z":
                out += [_v(model, 90, 0, w), _v(model, 90, 180, w)]
            else:
                out += [_v(model, 90, 90, w), _v(model, 90, 270, w)]
        return out
    return {f"axis={a}{key}": entries(a) for a in ("x", "y", "z")}


def _plane(frm, to, tex, rot=None, uv=(0, 0, 16, 16), faces=("north", "south"), light=False):
    """A flat (zero-thickness) element; rot = (axis, angle, origin[, rescale])."""
    e = {"from": list(frm), "to": list(to), "shade": False,
         "faces": {f: {"uv": list(uv), "texture": tex} for f in faces}}
    if rot:
        e["rotation"] = {"origin": list(rot[2]), "axis": rot[0], "angle": rot[1]}
        if len(rot) > 3 and rot[3]:
            e["rotation"]["rescale"] = True
    if light:
        e["neoforge_data"] = {"block_light": 15, "sky_light": 15}
    return e


def _cross(name: str, tex: str) -> None:
    _model(name, {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout", "textures": {"cross": _ref(tex)}})


def _slab_models(name: str, double: str, top: str, side: str, bottom: str, side_uv=(0, 8, 16, 16)) -> None:
    """Slab halves. side_uv picks the part of the side texture a half shows (the grass slab uses
    the top half of the grass side, so its fringe shows)."""
    for suffix, y0, y1 in (("", 0, 8), ("_top", 8, 16)):
        faces = {"down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"} if y0 == 0 else
                 {"uv": [0, 0, 16, 16], "texture": "#bottom"},
                 "up": {"uv": [0, 0, 16, 16], "texture": "#top", "cullface": "up"} if y1 == 16 else
                 {"uv": [0, 0, 16, 16], "texture": "#top"}}
        for f in ("north", "south", "east", "west"):
            faces[f] = {"uv": list(side_uv), "texture": "#side", "cullface": f}
        _model(name + suffix, {"parent": "minecraft:block/block",
                               "textures": {"top": _ref(top), "side": _ref(side), "bottom": _ref(bottom), "particle": _ref(side)},
                               "elements": [{"from": [0, y0, 0], "to": [16, y1, 16], "faces": faces}]})
    _state(name, {"type=bottom": _v(name), "type=top": _v(name + "_top"), "type=double": _v(double)})
    _item_block(name, name)


def tex_l227flora_models() -> None:
    """Block models, blockstates and item models of every Level 227 flora block."""
    # ---- Bark: 4 versions, each also turned about the grain; version 0 is the most common.
    bark = [f"l227_bark_{i}" for i in range(4)]
    for b in bark:
        _model(b, {"parent": "minecraft:block/cube_column", "textures": {"end": _ref("l227_bark_end"), "side": _ref(b)}})
    _state("l227_bark", _column_state(bark, weights=(4, 1, 1, 1)))
    _item_block("l227_bark", "l227_bark_0")

    # ---- The Palette: bark-like on all sides (FALLEN doesn't change the look).
    _model("l227_palette", {"parent": "minecraft:block/cube_column",
                            "textures": {"end": _ref("l227_bark_end"), "side": _ref("l227_palette")}})
    st = {}
    for fallen in ("false", "true"):
        for k, v in _column_state(["l227_palette"]).items():
            st[f"{k},fallen={fallen}"] = v
    _state("l227_palette", st)
    _item_block("l227_palette", "l227_palette")

    # ---- Mossy bark: moss on top, creeping down the sides; the bark's grain follows AXIS.
    mv, mh, moss = "l227_bark_mossy_v", "l227_bark_mossy_h", "l227_moss"
    _model("l227_bark_mossy_y", _cube({"up": (moss, 0), "down": ("l227_bark_end", 0), "north": (mv, 0),
                                       "south": (mv, 0), "east": (mv, 0), "west": (mv, 0)}))
    _model("l227_bark_mossy_x", _cube({"up": (moss, 0), "down": ("l227_bark_0", 90), "north": (mh, 0),
                                       "south": (mh, 0), "east": (mv, 0), "west": (mv, 0)}))
    _model("l227_bark_mossy_z", _cube({"up": (moss, 0), "down": ("l227_bark_0", 0), "north": (mv, 0),
                                       "south": (mv, 0), "east": (mh, 0), "west": (mh, 0)}))
    _state("l227_bark_mossy", {"axis=y": [_v("l227_bark_mossy_y", 0, y) for y in SPINS],
                               "axis=x": [_v("l227_bark_mossy_x"), _v("l227_bark_mossy_x", 0, 180)],
                               "axis=z": [_v("l227_bark_mossy_z"), _v("l227_bark_mossy_z", 0, 180)]})
    _item_block("l227_bark_mossy", "l227_bark_mossy_y")

    _slab_models("l227_bark_slab", "l227_bark_0", "l227_bark_end", "l227_bark_0", "l227_bark_end")

    # ---- Heartwood and the growth rings (see Level227RingsBlock for what RINGS means).
    _model("l227_heartwood", {"parent": "minecraft:block/cube_all", "textures": {"all": _ref("l227_heartwood")}})
    _state("l227_heartwood", {"": [_v("l227_heartwood", 0, y) for y in SPINS]})
    _item_block("l227_heartwood", "l227_heartwood")
    hw = "l227_heartwood"
    pos_face = {"x": "east", "y": "up", "z": "south"}
    neg_face = {"x": "west", "y": "down", "z": "north"}
    # Heartwood faces: its grain should run along AXIS.
    grain_rot = {"x": {"north": 90, "south": 90, "up": 90, "down": 90},
                 "y": {"north": 0, "south": 0, "east": 0, "west": 0},
                 "z": {"east": 90, "west": 90, "up": 0, "down": 0}}
    variants = {}
    for axis in ("x", "y", "z"):
        for r in range(4):
            faces = {}
            for f, rot in grain_rot[axis].items():
                faces[f] = (hw, rot)
            if r < 2:
                faces[pos_face[axis]] = ("l227_rings", 90 * r)
                faces[neg_face[axis]] = ("l227_rings", 90 * r)
            else:
                # A diagonal seen from the other side is the other diagonal, so the back face
                # gets the picture turned a quarter: the bands then line up through the block.
                faces[pos_face[axis]] = ("l227_rings_diag", 0 if r == 2 else 90)
                faces[neg_face[axis]] = ("l227_rings_diag", 90 if r == 2 else 0)
            name = f"l227_growth_rings_{axis}{r}"
            _model(name, _cube(faces, particle=hw))
            variants[f"axis={axis},rings={r}"] = _v(name)
    _state("l227_growth_rings", variants)
    _item_block("l227_growth_rings", "l227_growth_rings_z0")

    # ---- Knothole: the hole on the north face (FACING turns it any of six ways).
    _model("l227_knot", _cube({"north": ("l227_knot", 0), "south": ("l227_bark_0", 0), "east": ("l227_bark_0", 0),
                               "west": ("l227_bark_0", 0), "up": ("l227_bark_end", 0), "down": ("l227_bark_end", 0)}))
    _state("l227_knot", {f"facing={f}": _v("l227_knot", x, y) for f, (x, y) in FACE6_NORTH.items()})
    _item_block("l227_knot", "l227_knot")

    # ---- Resin: bark, and over its sides the amber drips drawn full-bright.
    over = []
    for f in ("north", "south", "east", "west"):
        over.append({"from": [0, 0, 0], "to": [16, 16, 16], "shade": False,
                     "neoforge_data": {"block_light": 15, "sky_light": 15},
                     "faces": {f: {"uv": [0, 0, 16, 16], "texture": "#resin", "cullface": f}}})
    m = _cube({"up": ("l227_bark_end", 0), "down": ("l227_bark_end", 0), "north": ("l227_bark_1", 0),
               "south": ("l227_bark_1", 0), "east": ("l227_bark_1", 0), "west": ("l227_bark_1", 0)},
              render="minecraft:cutout", extra=over)
    m["textures"]["resin"] = _ref("l227_resin")
    _model("l227_resin", m)
    _state("l227_resin", {"": [_v("l227_resin", 0, y) for y in SPINS]})
    _item_block("l227_resin", "l227_resin")

    # ---- Ground.
    for name in ("l227_root_soil", "l227_moss", "l227_ash"):
        _model(name, {"parent": "minecraft:block/cube_all", "textures": {"all": _ref(name)}})
        _state(name, {"": [_v(name, 0, y) for y in SPINS]})
        _item_block(name, name)
    _model("l227_forest_grass", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": _ref("l227_forest_grass"), "side": _ref("l227_forest_grass_side"), "bottom": _ref("l227_root_soil")}})
    _state("l227_forest_grass", {"": [_v("l227_forest_grass", 0, y) for y in SPINS]})
    _item_block("l227_forest_grass", "l227_forest_grass")
    _slab_models("l227_moss_slab", "l227_moss", "l227_moss", "l227_moss", "l227_moss")
    _slab_models("l227_forest_grass_slab", "l227_forest_grass", "l227_forest_grass", "l227_forest_grass_side",
                 "l227_root_soil", side_uv=(0, 0, 16, 8))
    _model("l227_moss_carpet", {"parent": "minecraft:block/block", "textures": {"moss": _ref("l227_moss"), "particle": _ref("l227_moss")},
                                "elements": [{"from": [0, 0, 0], "to": [16, 1, 16], "faces": {
                                    "up": {"uv": [0, 0, 16, 16], "texture": "#moss"},
                                    "down": {"uv": [0, 0, 16, 16], "texture": "#moss", "cullface": "down"},
                                    **{f: {"uv": [0, 15, 16, 16], "texture": "#moss"} for f in ("north", "south", "east", "west")}}}]})
    _state("l227_moss_carpet", {"": [_v("l227_moss_carpet", 0, y) for y in SPINS]})
    _item_block("l227_moss_carpet", "l227_moss_carpet")

    # ---- Forest trees.
    for name in ("l227_tree_log", "l227_tree_log_mossy"):
        _model(name, {"parent": "minecraft:block/cube_column", "textures": {"end": _ref("l227_tree_log_end"), "side": _ref(name)}})
        _state(name, _column_state([name]))
        _item_block(name, name)
    log = "#log"
    # Moss along the fin's top edge: a bare flat top made the fins read as walls.
    _model("l227_buttress", {"parent": "minecraft:block/block", "textures": {"log": _ref("l227_tree_log"), "moss": _ref("l227_moss"),
                                                                            "particle": _ref("l227_tree_log")},
                             "elements": [{"from": [0, 0, 5], "to": [16, 16, 11], "faces": {
                                 "north": {"uv": [0, 0, 16, 16], "texture": log},
                                 "south": {"uv": [0, 0, 16, 16], "texture": log},
                                 "east": {"uv": [5, 0, 11, 16], "texture": log, "cullface": "east"},
                                 "west": {"uv": [5, 0, 11, 16], "texture": log, "cullface": "west"},
                                 "up": {"uv": [0, 5, 16, 11], "texture": "#moss", "cullface": "up"},
                                 "down": {"uv": [0, 5, 16, 11], "texture": log, "cullface": "down"}}}]})
    _state("l227_buttress", {"axis=x": _v("l227_buttress"), "axis=z": _v("l227_buttress", 0, 90)})
    _item_block("l227_buttress", "l227_buttress")
    for name in ("l227_leaves", "l227_fern_leaves"):
        _model(name, {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout_mipped",
                      "textures": {"all": _ref(name)}})
        _state(name, {"": [_v(name, x, y) for x in (0, 180) for y in SPINS]})
        _item_block(name, name)

    # ---- Hanging strands (TIP = the lowest).
    for name in ("l227_hanging_fronds", "l227_hanging_moss", "l227_liana"):
        _cross(name, name)
        _cross(name + "_tip", name + "_tip")
        _state(name, {"tip=false": [_v(name, 0, y) for y in (0, 90)], "tip=true": [_v(name + "_tip", 0, y) for y in (0, 90)]})
        _item_flat(name, _ref(name))

    # ---- Tall grass: four planes per half (two diagonal, two at 22.5 degrees), dense enough to
    # hide what lies in it.
    for half, tex in (("lower", "l227_tall_grass_lower"), ("upper", "l227_tall_grass_upper")):
        t = "#grass"
        els = [_plane((0.8, 0, 8), (15.2, 16, 8), t, ("y", a, (8, 8, 8), abs(a) == 45)) for a in (45, -45, 22.5, -22.5)]
        _model(f"l227_tall_grass_{half}", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                           "ambientocclusion": False,
                                           "textures": {"grass": _ref(tex), "particle": _ref(tex)}, "elements": els})
    _state("l227_tall_grass", {"half=lower": [_v("l227_tall_grass_lower", 0, y) for y in SPINS],
                               "half=upper": [_v("l227_tall_grass_upper", 0, y) for y in SPINS]})
    _item_flat("l227_tall_grass", _ref("l227_tall_grass_upper"))

    # ---- Giant fern: drawn whole by the lower half (models may reach a block past their own):
    # two upright fronds crossing, four leaning out 22.5 degrees and four arching out at 45, about
    # 2.5 blocks high and wide. The upper half only holds the space.
    f = "#frond"
    els = [_plane((0, 0, 8), (16, 28, 8), f, ("y", a, (8, 0, 8), True)) for a in (45, -45)]
    for a in (22.5, -22.5):
        els.append(_plane((0, 0, 8), (16, 28, 8), f, ("x", a, (8, 0, 8))))
        els.append(_plane((8, 0, 0), (8, 28, 16), f, ("z", a, (8, 0, 8)), faces=("east", "west")))
    for a in (45, -45):
        els.append(_plane((1, 1, 8), (15, 25, 8), f, ("x", a, (8, 1, 8))))
        els.append(_plane((8, 1, 1), (8, 25, 15), f, ("z", a, (8, 1, 8)), faces=("east", "west")))
    _model("l227_giant_fern", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
                               "textures": {"frond": _ref("l227_giant_fern"), "particle": _ref("l227_giant_fern")}, "elements": els})
    _model("l227_giant_fern_upper", {"textures": {"particle": _ref("l227_giant_fern")}})
    _state("l227_giant_fern", {"half=lower": [_v("l227_giant_fern", 0, y) for y in SPINS],
                               "half=upper": _v("l227_giant_fern_upper")})
    _item_flat("l227_giant_fern", _ref("l227_giant_fern"))

    # ---- Undergrowth and flowers (KIND 0..3).
    for k in (0, 2, 3):
        _cross(f"l227_undergrowth_{k}", f"l227_undergrowth_{k}")
    # The elephant ear: its big leaf on two planes leaning back a little.
    e = "#leaf"
    _model("l227_undergrowth_1", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
                                  "textures": {"leaf": _ref("l227_undergrowth_1"), "particle": _ref("l227_undergrowth_1")},
                                  "elements": [_plane((0, 0, 8), (16, 16, 8), e, ("x", 22.5, (8, 0, 8))),
                                               _plane((8, 0, 0), (8, 16, 16), e, ("z", -22.5, (8, 0, 8)), faces=("east", "west"))]})
    _state("l227_undergrowth", {f"kind={k}": [_v(f"l227_undergrowth_{k}", 0, y) for y in SPINS] for k in range(4)})
    _item_flat("l227_undergrowth", _ref("l227_undergrowth_1"))
    for k in range(4):
        _cross(f"l227_flower_{k}", f"l227_flower_{k}")
    _state("l227_flower", {f"kind={k}": [_v(f"l227_flower_{k}", 0, y) for y in SPINS] for k in range(4)})
    _item_flat("l227_flower", _ref("l227_flower_3"))

    # ---- Bracket fungus (facing north = on a wall to its south): three shelves.
    els = []
    for frm, to in (((1, 8, 4), (15, 10.5, 16)), ((3, 11.5, 8), (13, 13, 16)), ((5, 5, 10), (12, 6.5, 16))):
        els.append({"from": list(frm), "to": list(to), "faces": {
            "up": {"texture": "#top"}, "down": {"texture": "#under"},
            "north": {"uv": [frm[0], 14, to[0], 16], "texture": "#top"},
            "east": {"uv": [frm[2], 14, to[2], 16], "texture": "#top"},
            "west": {"uv": [frm[2], 14, to[2], 16], "texture": "#top"}}})
    _model("l227_bracket_fungus", {"parent": "minecraft:block/block",
                                   "textures": {"top": _ref("l227_bracket_top"), "under": _ref("l227_bracket_under"),
                                                "particle": _ref("l227_bracket_top")}, "elements": els})
    _state("l227_bracket_fungus", {f"facing={fc}": _v("l227_bracket_fungus", 0, y) for fc, y in FACINGS})
    _item_block("l227_bracket_fungus", "l227_bracket_fungus")

    # ---- Fallen leaf and epiphyte.
    _model("l227_fallen_leaf", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
                                "textures": {"leaf": _ref("l227_fallen_leaf"), "particle": _ref("l227_fallen_leaf")},
                                "elements": [{"from": [0, 0.1, 0], "to": [16, 0.1, 16], "faces": {
                                    "up": {"uv": [0, 0, 16, 16], "texture": "#leaf"},
                                    "down": {"uv": [0, 16, 16, 0], "texture": "#leaf"}}}]})
    _state("l227_fallen_leaf", {"": [_v("l227_fallen_leaf", 0, y) for y in SPINS]})
    _item_flat("l227_fallen_leaf", _ref("l227_fallen_leaf"))
    p = "#plant"
    els = [_plane((1, 0, 8), (15, 11, 8), p, ("x", a, (8, 0, 8))) for a in (22.5, -22.5)]
    els += [_plane((8, 0, 1), (8, 11, 15), p, ("z", a, (8, 0, 8)), faces=("east", "west")) for a in (22.5, -22.5)]
    _model("l227_epiphyte", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
                             "textures": {"plant": _ref("l227_epiphyte"), "particle": _ref("l227_epiphyte")}, "elements": els})
    _state("l227_epiphyte", {f"facing={fc}": _v("l227_epiphyte", x, y) for fc, (x, y) in FACE6.items()})
    _item_flat("l227_epiphyte", _ref("l227_epiphyte"))

    # ---- Lestron (HARVESTED = cut back) and GammaPali (stems, and bulbs drawn full-bright).
    _cross("l227_lestron", "l227_lestron")
    _cross("l227_lestron_cut", "l227_lestron_cut")
    _state("l227_lestron", {"harvested=false": [_v("l227_lestron", 0, y) for y in SPINS],
                            "harvested=true": [_v("l227_lestron_cut", 0, y) for y in SPINS]})
    _item_flat("l227_lestron", _ref("l227_lestron"))
    g = "#g"
    els = [_plane((2, 0, 8), (14, 6, 8), g, ("y", a, (8, 0, 8), True), uv=(2, 2, 14, 8)) for a in (45, -45)]
    for frm, to in (((5, 3, 5), (8, 6, 8)), ((9, 4, 7), (11.5, 6.5, 9.5)), ((6, 5, 9.5), (8.5, 7, 12)),
                    ((10, 2, 3.5), (12, 4, 5.5)), ((4, 1.5, 10), (6, 3.5, 12))):
        els.append({"from": list(frm), "to": list(to), "shade": False,
                    "neoforge_data": {"block_light": 15, "sky_light": 15},
                    "faces": {fc: {"uv": [1 + 2.4 * i, 9, 3.4 + 2.4 * i, 12], "texture": g}
                              for i, fc in enumerate(("north", "south", "east", "west", "up", "down"))}})
    _model("l227_gammapali", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
                              "textures": {"g": _ref("l227_gammapali"), "particle": _ref("l227_gammapali")}, "elements": els})
    _state("l227_gammapali", {f"facing={fc}": _v("l227_gammapali", x, y) for fc, (x, y) in FACE6.items()})
    _item_block("l227_gammapali", "l227_gammapali")

    # ---- Items: Lestron sprigs, the flytrap's spawn egg.
    _item_flat("lestron", "backrooms:item/lestron")
    _json("models/item/venus_flytrap_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})


def data_l227flora_damage_types() -> None:
    """The three ways the plants kill (data/backrooms/damage_type)."""
    data = ASSETS.parents[1] / "data" / "backrooms" / "damage_type"
    for name in ("palette", "gammapali", "venus_flytrap"):
        (data / f"{name}.json").write_text(json.dumps({"exhaustion": 0.0, "message_id": f"backrooms.{name}",
                                                       "scaling": "never"}, indent=2) + "\n", newline="\n")

TEXTURES = [
    tex_l227flora_bark, tex_l227flora_ground, tex_l227flora_wood, tex_l227flora_trees, tex_l227flora_undergrowth,
    tex_l227flora_hazards, tex_l227flora_flytrap, tex_l227flora_particles, tex_l227flora_models,
    data_l227flora_damage_types,
]


# =============================================================================
# Sounds (sounds/l227/*.ogg). Short one-shots; quiet on purpose (README "Mix"): peaks are set
# per sound here and the in-game volume in sounds.json.
# =============================================================================

def _snd(name: str):
    return G.SND / "l227" / f"{name}.ogg"


def _save_snd(x, name, peak=0.7):
    from anomaly_assets import level39_audio as A39
    A39._save_ogg(x, _snd(name), peak=peak, fade_out=0.05)


def _bp(x, lo, hi, order=2):
    return G.fft_filter(x, lambda f: G.bp_gain(f, lo, hi, order))


def _lp(x, fc, order=2):
    return G.fft_filter(x, lambda f: G.lp_gain(f, fc, order))


def _env(n, attack, decay, curve=1.0):
    """Fast attack, exponential-ish decay envelope over n samples (times in seconds)."""
    t = np.arange(n) / SR
    e = np.clip(t / max(attack, 1e-4), 0, 1)
    return e * np.exp(-np.maximum(t - attack, 0) / max(decay, 1e-4)) ** curve


def _clicks(rng, dur, rate, lo, hi, env=None, decay=(0.0006, 0.002)):
    """Dense tiny impulses (leaves clattering, grass blades, splinters) band-limited to lo..hi."""
    n = int(dur * SR)
    buf = np.zeros(n)
    count = int(rate * dur)
    times = rng.uniform(0, dur, size=count)
    for t0 in times:
        i = int(t0 * SR)
        k = int(rng.uniform(*decay) * SR * 6)
        if i + k >= n:
            continue
        buf[i:i + k] += rng.normal(0, 1, k) * np.exp(-np.arange(k) / (k / 6)) * rng.uniform(0.2, 1.0)
    if env is not None:
        buf *= env
    return _bp(buf, lo, hi)


def _formant(x, formants):
    """Sum of band-passes at (centre, width, gain): a crude vowel / body resonance."""
    return sum(g * _bp(x, f - w / 2, f + w / 2, 2) for f, w, g in formants)


def _reverb(x, rng, dur=0.6, decay=0.18, mix=0.25, lp=5000.0):
    ir = G.reverb_ir(rng, dur, decay, lp=lp)
    wet = G.conv(x, ir)[: len(x)]
    return x + mix * wet / (np.abs(wet).max() + 1e-9) * np.abs(x).max()


def snd_l227flora_forest() -> None:
    """Leaf wind (a gust hissing through a canopy of big leaves) and the rustle of tall grass."""
    for i in range(3):
        rng = G.rng_for(22800 + i)
        dur = 4.5
        n = int(dur * SR)
        t = np.arange(n) / SR
        rise, fall = rng.uniform(1.0, 1.8), rng.uniform(1.6, 2.4)
        gust = np.clip(t / rise, 0, 1) ** 1.5 * np.clip((dur - t) / fall, 0, 1) ** 1.2
        gust *= 1 + 0.25 * np.sin(2 * np.pi * t * rng.uniform(0.6, 1.1) + rng.uniform(0, 6))
        whoosh = _bp(rng.normal(0, 1, n), 180, 900) * gust * 0.5
        hiss = _bp(rng.normal(0, 1, n), 1500, 7000) * gust * 0.35
        leaves = _clicks(rng, dur, 2600, 1800, 7500, env=gust ** 1.5) * 1.6
        big = _clicks(rng, dur, 140, 300, 2200, env=gust, decay=(0.003, 0.008)) * 0.9
        _save_snd(whoosh + hiss + leaves + big, f"leaf_wind{i + 1}", peak=0.55)
    for i in range(3):
        rng = G.rng_for(22810 + i)
        dur = 0.6
        n = int(dur * SR)
        e = _env(n, 0.04, 0.16)
        swish = _bp(rng.normal(0, 1, n), 1200, 7500) * e
        blades = _clicks(rng, dur, 900, 1500, 8000, env=e)
        _save_snd(swish * 0.6 + blades, f"rustle{i + 1}", peak=0.5)


def snd_l227flora_hazards() -> None:
    """GammaPali: its gas puffing out (hiss) and you choking on it; the Palette's creak and crash."""
    for i in range(2):
        rng = G.rng_for(22820 + i)
        dur = 1.8
        n = int(dur * SR)
        t = np.arange(n) / SR
        puff = np.clip(t / 0.25, 0, 1) * np.exp(-np.maximum(t - 0.25, 0) / 0.5)
        puff *= 1 + 0.3 * np.sin(2 * np.pi * 7 * t)
        hiss = _bp(rng.normal(0, 1, n), 2500, 10000) * puff
        body = _bp(rng.normal(0, 1, n), 600, 2000) * puff * 0.3
        sizzle = _clicks(rng, dur, 400, 4000, 11000, env=puff) * 0.5
        _save_snd(hiss + body + sizzle, f"gammapali_hiss{i + 1}", peak=0.45)
    for i in range(2):
        rng = G.rng_for(22825 + i)
        dur = 1.4
        n = int(dur * SR)
        out = np.zeros(n)
        t0 = 0.02
        for k in range(rng.integers(2, 4)):
            ln = rng.uniform(0.16, 0.24)
            m = int(ln * SR)
            tt = np.arange(m) / SR
            e = np.clip(tt / 0.008, 0, 1) * np.exp(-tt / (ln * 0.35))
            glottal = np.sign(np.sin(2 * np.pi * rng.uniform(110, 150) * tt)) * 0.3
            src = rng.normal(0, 1, m) + glottal
            cough = _formant(src, ((650, 300, 1.0), (1150, 350, 0.7), (2600, 600, 0.35))) * e
            i0 = int(t0 * SR)
            out[i0:i0 + m] += cough[: n - i0] if i0 + m > n else cough
            t0 += ln + rng.uniform(0.05, 0.14)
            if t0 > dur - 0.4:
                break
        # A strained, wheezing breath in at the end.
        m = int(0.35 * SR)
        i0 = min(n - m, int(t0 * SR))
        tt = np.arange(m) / SR
        wheeze = _bp(rng.normal(0, 1, m), 1800, 3200, 3) * np.sin(np.pi * tt / tt[-1]) * 0.5
        out[i0:i0 + m] += wheeze
        _save_snd(_reverb(out, rng, 0.4, 0.1, 0.15), f"gammapali_choke{i + 1}", peak=0.6)
    for i in range(2):
        rng = G.rng_for(22830 + i)
        dur = 1.3
        n = int(dur * SR)
        t = np.arange(n) / SR
        # Stick-slip: a train of tiny slips whose rate wanders, through the wood's resonances.
        rate = 28 + 18 * np.sin(2 * np.pi * t * rng.uniform(0.6, 1.2)) + rng.normal(0, 3, n).cumsum() / SR * 10
        phase = np.cumsum(np.maximum(rate, 6)) / SR
        pulses = (np.diff(np.floor(phase), prepend=0) > 0).astype(float) * rng.uniform(0.5, 1.0, n)
        # The creak swells for 0.8 s: Level227PaletteBlock.CREAK_TICKS (16 ticks) after it starts,
        # the Palette lets go, which is the crack.
        swell = np.clip(t / 0.6, 0, 1) ** 1.5 * (t < 0.8)
        creak = _formant(pulses * swell, ((190, 60, 1.0), (430, 90, 0.8), (780, 140, 0.5), (1350, 260, 0.3)))
        # It gives: a sharp crack and a rattle of bark bits.
        crack = np.zeros(n)
        i0 = int(0.78 * SR)
        m = int(0.05 * SR)
        crack[i0:i0 + m] = rng.normal(0, 1, m) * np.exp(-np.arange(m) / (0.008 * SR)) * 3
        crack = _bp(crack, 300, 6000)
        bits = _clicks(rng, dur, 60, 800, 5000, env=(t > 0.78) * np.exp(-np.maximum(t - 0.78, 0) / 0.12))
        # The creak is the warning, so it leads the mix; the crack and bits sit under it.
        mix = G.norm(creak) + 0.55 * G.norm(crack) + 0.3 * G.norm(bits)
        _save_snd(_reverb(mix, rng, 0.8, 0.22, 0.25), f"palette_creak{i + 1}", peak=0.75)
    for i in range(2):
        rng = G.rng_for(22835 + i)
        dur = 1.6
        n = int(dur * SR)
        t = np.arange(n) / SR
        thump = np.sin(2 * np.pi * np.cumsum(90 * np.exp(-t / 0.15) + 38) / SR) * np.exp(-t / 0.22)
        crunch = _bp(rng.normal(0, 1, n), 250, 3500) * _env(n, 0.002, 0.09)
        splinter = _clicks(rng, dur, 900, 600, 6000, env=np.exp(-t / 0.18), decay=(0.001, 0.004)) * 1.5
        debris = _clicks(rng, dur, 70, 400, 4000, env=np.exp(-np.maximum(t - 0.1, 0) / 0.5) * (t > 0.1), decay=(0.002, 0.006))
        _save_snd(_reverb(thump * 1.4 + crunch + splinter + debris, rng, 1.0, 0.3, 0.3, 3000), f"palette_crush{i + 1}", peak=0.85)


def snd_l227flora_flytrap() -> None:
    """The giant Venus flytrap: snap, lift, digest, struggle, release, hurt and death."""
    def wet(rng, n, lo=200, hi=2500, sweep=(300, 1200)):
        """A wet squelch: noise through a resonance that sweeps (like a mouth closing)."""
        x = rng.normal(0, 1, n)
        f = np.linspace(sweep[0], sweep[1], n)
        return G.svf(x * 0.2, f, 4.0) + 0.3 * _bp(x, lo, hi)

    for i in range(2):
        rng = G.rng_for(22840 + i)
        dur = 0.9
        n = int(dur * SR)
        t = np.arange(n) / SR
        slap = _bp(rng.normal(0, 1, n), 500, 5000) * _env(n, 0.001, 0.018) * 2.5
        thump = np.sin(2 * np.pi * np.cumsum(140 * np.exp(-t / 0.05) + 55) / SR) * np.exp(-t / 0.13) * 1.4
        sq = wet(rng, n, sweep=(1400, 300)) * _env(n, 0.01, 0.12)
        teeth = _clicks(rng, dur, 300, 1500, 7000, env=np.exp(-t / 0.05), decay=(0.0005, 0.0015))
        _save_snd(_reverb(slap + thump + sq + teeth, rng, 0.6, 0.15, 0.2), f"flytrap_snap{i + 1}", peak=0.85)
    rng = G.rng_for(22845)
    dur = 2.4
    n = int(dur * SR)
    t = np.arange(n) / SR
    f0 = 48 + 30 * np.clip(t / 1.8, 0, 1) + 3 * np.sin(2 * np.pi * 5 * t)
    groan = np.sign(np.sin(2 * np.pi * np.cumsum(f0) / SR)) * 0.5
    groan = _lp(groan, 500) * np.clip(t / 0.3, 0, 1) * np.clip((dur - t) / 0.6, 0, 1)
    fibres = _clicks(rng, dur, 250, 500, 4000, env=np.clip(t / 0.4, 0, 1) * np.clip((dur - t) / 0.8, 0, 1), decay=(0.001, 0.004))
    whoosh = _bp(rng.normal(0, 1, n), 200, 1200) * np.sin(np.pi * np.clip(t / 2.0, 0, 1)) * 0.4
    _save_snd(groan + fibres + whoosh, "flytrap_lift1", peak=0.7)
    for i in range(2):
        rng = G.rng_for(22846 + i)
        dur = 2.6
        n = int(dur * SR)
        t = np.arange(n) / SR
        churn = _lp(rng.normal(0, 1, n), 380) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.9 * t + rng.uniform(0, 6))) * 0.8
        bubbles = np.zeros(n)
        for _ in range(rng.integers(14, 22)):
            t0 = rng.uniform(0, dur - 0.15)
            ln = rng.uniform(0.04, 0.12)
            m = int(ln * SR)
            tt = np.arange(m) / SR
            fb = rng.uniform(140, 420) * (1 + 2.5 * tt / ln)
            b = np.sin(2 * np.pi * np.cumsum(fb) / SR) * np.exp(-tt / (ln * 0.4)) * rng.uniform(0.3, 1.0)
            i0 = int(t0 * SR)
            bubbles[i0:i0 + m] += b
        _save_snd(_reverb(churn + bubbles, rng, 0.5, 0.12, 0.3, 2500), f"flytrap_digest{i + 1}", peak=0.6)
    for i in range(3):
        rng = G.rng_for(22850 + i)
        dur = 0.45
        n = int(dur * SR)
        t = np.arange(n) / SR
        sq = wet(rng, n, sweep=(rng.uniform(300, 500), rng.uniform(1200, 1800))) * _env(n, 0.01, 0.1)
        thud = np.sin(2 * np.pi * 70 * t) * np.exp(-t / 0.06) * 0.8
        _save_snd(sq + thud, f"flytrap_struggle{i + 1}", peak=0.65)
    rng = G.rng_for(22855)
    dur = 1.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    pop = np.sin(2 * np.pi * np.cumsum(220 * np.exp(-t / 0.03) + 90) / SR) * np.exp(-t / 0.06)
    slosh = _lp(rng.normal(0, 1, n), 900) * np.exp(-t / 0.3) * 0.6
    sq = wet(rng, n, sweep=(400, 1600)) * _env(n, 0.02, 0.2)
    _save_snd(pop + slosh + sq, "flytrap_release1", peak=0.75)
    for i in range(2):
        rng = G.rng_for(22856 + i)
        dur = 0.5
        n = int(dur * SR)
        t = np.arange(n) / SR
        rip = _clicks(rng, dur, 1500, 600, 5000, env=np.exp(-t / 0.12), decay=(0.0005, 0.002))
        sq = wet(rng, n, sweep=(1600, 400)) * _env(n, 0.005, 0.08)
        _save_snd(rip + sq, f"flytrap_hurt{i + 1}", peak=0.7)
    rng = G.rng_for(22858)
    dur = 2.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    tear = _clicks(rng, dur, 1200, 500, 5000, env=np.clip(t / 0.1, 0, 1) * np.exp(-t / 0.6), decay=(0.0006, 0.003))
    deflate = G.svf(rng.normal(0, 1, n) * 0.2, np.linspace(1400, 150, n), 3.0) * np.exp(-t / 0.8)
    groan = _lp(np.sign(np.sin(2 * np.pi * np.cumsum(70 - 30 * t / dur) / SR)), 400) * np.exp(-t / 0.7) * 0.4
    _save_snd(_reverb(tear + deflate + groan, rng, 0.8, 0.2, 0.25), "flytrap_death1", peak=0.75)


SOUNDS = [snd_l227flora_forest, snd_l227flora_hazards, snd_l227flora_flytrap]
