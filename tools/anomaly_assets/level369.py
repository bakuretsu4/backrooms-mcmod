"""Level 369 ("Comely") blocks: textures, block models, blockstates and item models, and the
level's ambience loop.

Comely is an endless indoor garden: rooms of pastel-painted plaster under acoustic ceilings and
frosted skylights, every floor a lawn carpeted with flowers (docs/level-369-comely.md). The look
comes from the user's two pictures (a white hallway of bright windows with blue flowers in the
grass; a low peach hall of cream pillars with red flowers and a brick path) and the wiki's
pastel, skylit rooms.

Atlas budget: the block atlas must stay at 4096 x 4096 and has only ~156 000 texels free, so
surfaces are 64 px and plants 32 px, like Level 227's flora (this adds about 90 000 texels).
The young trees' trunks are Level 227's logs; the hallway doors are the Lobby's.

Run with:  python tools/gen_assets.py l369
Every generator has its own fixed seed (36900-36999), so re-running gives identical files.
"""
from __future__ import annotations

import numpy as np

import gen_assets as G
from anomaly_assets import level227_flora as F

N = F.N          # 64 px surfaces
M = F.M          # 32 px plants
SS = F.SS        # supersampling for drawn shapes

PLASTERS = {
    "white": (232, 228, 218),
    "peach": (224, 168, 132),
    "mint": (182, 214, 190),
    "lavender": (196, 186, 220),
    "butter": (236, 222, 168),
    "rose": (232, 184, 184),
    "dusk": (78, 96, 84),
}

_col = F._col
_save = F._save


def _plaster(seed: int, base) -> np.ndarray:
    """Painted plaster: a soft trowelled mottling, faint vertical roller marks, a fine grain."""
    rng = G.rng_for(seed)
    img = G.fill(base, (N, N))
    mottle = G.blur(F._noise(rng, N, beta=2.2, kmin=1.5), 1.2)
    roller = F._noise(rng, N, beta=1.4, aniso=(1.0, 0.12), kmin=3)
    grain = rng.normal(0, 1.0, (N, N))
    lum = mottle * 3.2 + roller * 1.6 + grain * 1.4
    img = G.add_lum(img, lum * (0.6 + 0.4 * np.mean(base) / 230.0))
    return img


def tex_l369_plasters() -> None:
    """The seven painted plasters."""
    for i, (name, base) in enumerate(PLASTERS.items()):
        _save(_plaster(36900 + i, base), f"l369_plaster_{name}")


def _acoustic(seed: int, base) -> np.ndarray:
    """Acoustic ceiling tiles, two by two to a block: fissured, pinholed, a recessed grid."""
    rng = G.rng_for(seed)
    X, Y = F._grid(N)
    img = G.fill(base, (N, N))
    fissure = G.smoothstep(1.2, 2.2, F._noise(rng, N, beta=1.3, kmin=6))
    pins = (rng.random((N, N)) < 0.03).astype(float)
    img = G.add_lum(img, -fissure * 16 - G.blur(pins, 0.4) * 30 + rng.normal(0, 1.4, (N, N)))
    half = N / 2
    d = np.minimum(np.minimum(X % half, half - X % half), np.minimum(Y % half, half - Y % half))
    img = G.add_lum(img, -G.smoothstep(1.4, 0.2, d) * 34)   # the grid between the tiles
    img = G.add_lum(img, G.smoothstep(2.6, 1.4, d) * G.smoothstep(0.2, 1.4, d) * 8)
    return img


def tex_l369_ceilings() -> None:
    """White acoustic tile (hallways, skylit and dark rooms) and the pillar halls' salmon tile."""
    _save(_acoustic(36910, (226, 224, 216)), "l369_ceiling")
    _save(_acoustic(36911, (212, 156, 136)), "l369_ceiling_rose")


def tex_l369_lights() -> None:
    """The light panel (a prismatic diffuser in a thin frame), the frosted skylight glass (almost
    white, a faint cross of glazing bars) and the hallway window (pale blue-white light behind
    white glazing bars)."""
    rng = G.rng_for(36920)
    X, Y = F._grid(N)
    # Panel: warm white, a fine grid of prisms, a frame.
    img = G.fill((255, 246, 222), (N, N))
    prism = (np.sin(X * np.pi / 2) * np.sin(Y * np.pi / 2)) * 5
    edge = np.minimum(np.minimum(X, N - X), np.minimum(Y, N - Y))
    img = G.add_lum(img, prism - G.smoothstep(4.0, 2.0, edge) * 30)
    img = G.over(img, (214, 206, 190), G.smoothstep(2.0, 1.0, edge))
    _save(img, "l369_panel")

    # Skylight: frosted, overexposed, glazing bars at the block edges and across the middle.
    img = G.fill((255, 252, 240), (N, N))
    img = G.add_lum(img, G.blur(F._noise(rng, N, beta=2.0, kmin=2), 1.5) * 2)
    bars = np.minimum(np.minimum(edge, np.abs(X - N / 2)), np.abs(Y - N / 2))
    img = G.over(img, (228, 224, 212), G.smoothstep(1.6, 0.6, bars))
    _save(img, "l369_skylight")

    # Window: a pane of sky-blue light (the first picture: blue windows full of light), paler toward
    # the top, a white frame at the sides and a bar across the bottom (stacked three high they make
    # one tall window). The model is full-bright, so these are the colours you see.
    glow = Y / N
    img = G.lerp(_col((196, 226, 255)), _col((120, 172, 238)), glow[..., None]) * np.ones((N, N, 1))
    img = G.add_lum(img, G.blur(F._noise(rng, N, beta=2.4, kmin=1), 2.0) * 4)
    side = np.minimum(X, N - X)
    frame = np.maximum(G.smoothstep(5.0, 4.0, side), G.smoothstep(4.0, 3.0, N - Y))
    img = G.over(img, (236, 232, 224), frame)
    img = G.add_lum(img, -G.smoothstep(5.6, 4.6, side) * G.smoothstep(4.0, 4.6, side) * 30)
    _save(img, "l369_window")


def tex_l369_brick() -> None:
    """Terracotta brick pavers in a running bond (four courses a block), each brick its own tone,
    worn smooth on top, sandy joints."""
    rng = G.rng_for(36930)
    X, Y = F._grid(N)
    rows, cols = 4, 2
    h, w = N / rows, N / cols
    row = np.floor(Y / h).astype(int)
    xs = X + (w / 2) * (row % 2)
    col = np.floor(xs / w).astype(int) % cols
    idx = row * cols + col
    tones = rng.normal(0, 12, rows * cols)
    warm = rng.uniform(-1, 1, rows * cols)
    img = G.fill((170, 92, 66), (N, N))
    img = G.add_lum(img, tones[idx])
    img[..., 1] += (warm[idx] * 8)
    img = G.add_lum(img, F._noise(rng, N, beta=1.6, kmin=4) * 5 + rng.normal(0, 2.0, (N, N)))
    dy = np.minimum(Y % h, h - Y % h)
    dx = np.minimum(xs % w, w - xs % w)
    joint = G.smoothstep(1.4, 0.5, np.minimum(dx, dy))
    img = G.over(img, (178, 160, 128), joint * 0.9)
    _save(img, "l369_brick")


def tex_l369_ground() -> None:
    """The lawn: mostly hidden under the meadow, so a calm, even green with soft, broad changes
    of tone and the faintest grain (no speckle); its side (the soil under a fringe of grass) and
    the soil itself."""
    rng = G.rng_for(36940)
    X, Y = F._grid(N)
    img = G.fill((56, 100, 40), (N, N))
    patch = G.blur(F._noise(rng, N, beta=2.6, kmin=1.0), 2.5)
    img = G.add_lum(img, patch * 5, (0.8, 1.0, 0.6))
    streak = G.blur(F._noise(rng, N, beta=1.2, aniso=(1.0, 0.35), kmin=4), 1.0)
    img = G.add_lum(img, streak * 2.0, (0.8, 1.0, 0.6))
    _save(img, "l369_grass")

    soil = G.fill((86, 62, 44), (N, N))
    soil = G.add_lum(soil, F._noise(rng, N, beta=1.8, kmin=3) * 8 + rng.normal(0, 3, (N, N)))
    pebbles = G.smoothstep(2.0, 2.6, F._noise(rng, N, beta=1.2, kmin=10))
    soil = G.over(soil, (128, 112, 96), pebbles * 0.7)
    _save(soil, "l369_soil")

    fringe = 0.32 * N + 5 * np.sin(X * 0.9) + 3 * np.sin(X * 2.3 + 1.0) + rng.normal(0, 1.2, (N,))[None, :]
    side = np.where((Y < fringe)[..., None], img, soil)
    _save(side, "l369_grass_side")


def tex_l369_vines() -> None:
    """Overgrown masonry (the arch of vines): small leaves crowding over dark stone, with tiny
    white and pink flowers; and the veil, a curtain of hanging strands of blossom, with gaps."""
    rng = G.rng_for(36950)
    n = N * SS
    X, Y = G.grid((n, n))
    rgb = np.zeros((n, n, 3)) + _col((38, 46, 36))
    a = np.ones((n, n))
    for _ in range(170):
        x, y = rng.uniform(0, n), rng.uniform(0, n)
        col = np.array(((44, 92, 36), (58, 110, 40), (36, 78, 32), (72, 122, 48))[rng.integers(4)], float)
        for ox in (-n, 0, n):           # wrap so the texture tiles
            for oy in (-n, 0, n):
                if abs(x + ox - n / 2) > n * 0.75 or abs(y + oy - n / 2) > n * 0.75:
                    continue
                r2, a2 = F._leaf_at(X, Y, x + ox, y + oy, rng.uniform(0, 2 * np.pi), rng.uniform(5, 9) * SS,
                                    rng.uniform(2.5, 4) * SS, col * rng.uniform(0.85, 1.15), veins=3)
                rgb, a = F._composite(rgb, a, r2, a2)
    for _ in range(40):
        cx, cy = rng.uniform(0, n), rng.uniform(0, n)
        rr = np.hypot(G.wrapd(X - cx, n), G.wrapd(Y - cy, n))
        col = _col(((246, 244, 236), (240, 180, 204), (250, 236, 170))[rng.integers(3)])
        rgb = G.lerp(rgb, col, np.clip(1.6 * SS - rr, 0, 1)[..., None])
    _save(F._down(rgb, SS), "l369_vine_wall")

    # Leaves for the bushes and the young trees: small rounded leaves crowding together, with gaps
    # to see through (cutout).
    rng = G.rng_for(36951)
    rgb = np.zeros((n, n, 3)) + _col((30, 58, 28))
    a = np.zeros((n, n))
    for _ in range(150):
        x, y = rng.uniform(0, n), rng.uniform(0, n)
        col = np.array(((52, 104, 40), (66, 122, 46), (44, 90, 36), (84, 136, 54))[rng.integers(4)], float)
        for ox in (-n, 0, n):
            for oy in (-n, 0, n):
                if abs(x + ox - n / 2) > n * 0.75 or abs(y + oy - n / 2) > n * 0.75:
                    continue
                r2, a2 = F._leaf_at(X, Y, x + ox, y + oy, rng.uniform(0, 2 * np.pi), rng.uniform(5, 8) * SS,
                                    rng.uniform(3, 4.5) * SS, col * rng.uniform(0.85, 1.15), veins=3, tip=0.9)
                rgb, a = F._composite(rgb, a, r2, a2)
    G.save_png(F._cutout(F._down(rgb, SS), F._down(a, SS)), F._blk("l369_leaves"))

    # The veil: strands of blossom hanging straight down, glowing softly (the model is full-bright).
    rgb = np.zeros((n, n, 3)) + _col((60, 84, 52))
    a = np.zeros((n, n))
    for k in range(9):
        x0 = (k + rng.uniform(0.2, 0.8)) * n / 9
        sway = 1.2 * SS * np.sin(2 * np.pi * Y / n + rng.uniform(0, 6.3))
        thread = np.clip(0.6 * SS - np.abs(X - x0 - sway) + 0.5, 0, 1)
        rgb, a = F._composite(rgb, a, np.broadcast_to(_col((52, 86, 46)), rgb.shape), thread)
        for y in np.arange(rng.uniform(0, 6), N, rng.uniform(4.5, 7.0)):
            cy = y * SS
            cx = x0 + 1.2 * SS * np.sin(2 * np.pi * cy / n) + rng.normal(0, 0.6) * SS
            rr = np.hypot(X - cx, G.wrapd(Y - cy, n))
            col = _col(((252, 248, 236), (248, 196, 214), (200, 214, 252), (252, 238, 180))[rng.integers(4)])
            bloom = np.clip(2.2 * SS - rr, 0, 1)
            rgb, a = F._composite(rgb, a, np.broadcast_to(col, rgb.shape) * (0.9 + 0.1 * rng.random()), bloom)
    G.save_png(F._cutout(F._down(rgb, SS), F._down(a, SS)), F._blk("l369_veil"))


# ------------------------------------------------------------------------------- plants (64 px)
#
# The meadow of the user's pictures: long soft blades and lots of small flowers. Every plant is
# drawn at 64 px from a few dozen broad blades, each one shaded from dark at its foot to sunlit at
# its tip with a light edge down one side, so the grass reads as soft and smooth (no one-pixel
# speckle, which the camcorder's sharpening turns to grit). Flower heads are small, several to a
# block. The models (tex_l369_models) stand two crossed planes of the plant with two straight
# planes of grass, so neighbouring blocks merge into one meadow.

P = 64          # plant texture size

# Blade colour from foot to tip: shadowed root green, fresh mid green, sunlit yellow-green.
BLADE_FOOT = (34, 72, 30)
BLADE_MID = (72, 132, 48)
BLADE_TIP = (150, 190, 78)


def _blade_canvas():
    return F._canvas(P)


def _soft_blades(rng, X, Y, rgb, a, count, heights, width=(1.6, 2.6), lean=0.22, depth=True):
    """`count` tapered, gently curving blades rising from the bottom edge to `heights` (fraction
    of the texture). Drawn back to front: the first are darker (deeper in the meadow)."""
    base = P * SS
    for i in range(count):
        x0 = rng.uniform(2, P - 2) * SS
        h = rng.uniform(*heights) * base
        bend = rng.normal(0, lean) * h
        wb = rng.uniform(*width) * SS
        tint = rng.uniform(0.9, 1.08) * ((0.78 + 0.22 * i / max(count - 1, 1)) if depth else 1.0)
        segs = 8
        pts = [(x0 + bend * (k / segs) ** 2, base - h * k / segs) for k in range(segs + 1)]
        cover = np.zeros(X.shape)
        along = np.zeros(X.shape)
        side = np.zeros(X.shape)
        for k in range(segs):
            (xa, ya), (xb, yb) = pts[k], pts[k + 1]
            vx, vy = xb - xa, yb - ya
            tt = np.clip(((X - xa) * vx + (Y - ya) * vy) / max(vx * vx + vy * vy, 1e-6), 0, 1)
            px, py = X - xa - tt * vx, Y - ya - tt * vy
            d = np.hypot(px, py)
            tg = (k + tt) / segs
            half = 0.5 * wb * (1 - tg) ** 0.85
            c = np.clip(half - d + 0.5, 0, 1)
            better = c > cover
            cover = np.where(better, c, cover)
            along = np.where(better, tg, along)
            # Which side of the blade's spine (for the light edge): sign of the cross product.
            side = np.where(better, np.sign(vx * py - vy * px) * d / np.maximum(half, 1e-3), side)
        col = np.where((along < 0.5)[..., None],
                       G.lerp(_col(BLADE_FOOT), _col(BLADE_MID), (along / 0.5)[..., None]),
                       G.lerp(_col(BLADE_MID), _col(BLADE_TIP), ((along - 0.5) / 0.5)[..., None]))
        col = col * tint * (1.0 + 0.12 * np.clip(side, 0, 1))[..., None]      # the edge facing the light
        rgb, a = F._composite(rgb, a, col, cover)
    return rgb, a


def _stem_to(rng, X, Y, rgb, a, x, y, col=(64, 112, 44)):
    """A thin, slightly bowed stem from the bottom edge up to a flower head at (x, y) (big px)."""
    x0 = x + rng.normal(0, 1.5) * SS
    mid = ((x0 + x) / 2 + rng.normal(0, 1.0) * SS, (P * SS + y) / 2)
    r2, a2 = F._stem(X, Y, [(x0, P * SS), mid, (x, y)], 0.9 * SS, col)
    return F._composite(rgb, a, r2, a2)


def _petal_head(rgb, a, X, Y, cx, cy, r, petals, col, edge, centre, tilt=0.6, width=0.34, rng=None, ragged=0.0):
    """A flower head seen a little from the side: `petals` petals shaded from `col` at the heart to
    `edge` at the tips round a `centre` disc. `ragged` cuts fringed tips (cornflowers)."""
    for k in range(petals):
        ang = k * 2 * np.pi / petals + (rng.uniform(-0.15, 0.15) if rng is not None else 0)
        ex, ey = np.cos(ang), np.sin(ang) * tilt
        L = r * (0.85 + (rng.uniform(0, 0.2) if rng is not None else 0)) * np.hypot(ex, ey)
        t, s, half, cover = F._leaf_shape(X, Y, 0, cx, cy, np.arctan2(ey, ex), L, r * width, wrap=False, tip=0.6, blunt=0.5)
        if ragged:
            cover = cover * (1 - ragged * (t > 0.78) * (np.sin(s / max(r * 0.06, 1e-3)) > 0.3))
        shade = G.lerp(_col(col), _col(edge), np.clip(t, 0, 1)[..., None])
        shade = shade * (0.9 + 0.1 * (1 - np.abs(s) / np.maximum(half, 1e-3)))[..., None]
        rgb, a = F._composite(rgb, a, shade, cover)
    rr = np.hypot(X - cx, (Y - cy) / tilt)
    disc = np.clip(r * 0.26 - rr + 0.5, 0, 1)
    shade = _col(centre) * (1.0 + 0.25 * np.clip(1 - rr / max(r * 0.26, 1e-3), 0, 1))[..., None]
    return F._composite(rgb, a, np.broadcast_to(shade, rgb.shape) if shade.ndim == 1 else shade, disc)


def _flower_texture(seed, heads, draw, grass=26):
    """Grass behind, then each head on its stem: `heads` (x, y) in texture px; `draw(rgb, a, X, Y,
    cx, cy, rng)` paints one head."""
    rng = G.rng_for(seed)
    X, Y, rgb, a = _blade_canvas()
    rgb, a = _soft_blades(rng, X, Y, rgb, a, grass, (0.3, 0.7))
    for hx, hy in heads:
        cx, cy = hx * SS, hy * SS
        rgb, a = _stem_to(rng, X, Y, rgb, a, cx, cy)
        rgb, a = draw(rgb, a, X, Y, cx, cy, rng)
    rgb, a = _soft_blades(rng, X, Y, rgb, a, 8, (0.15, 0.35))     # a few blades in front of the stems
    return rgb, a


def tex_l369_flowers() -> None:
    """Flowers 0-3 (64 px): blue cornflowers (the hallways), red daisies (the pillar halls), pink
    cosmos and white daisies (the skylit rooms), several small heads to a block among grass."""
    def cornflower(rgb, a, X, Y, cx, cy, rng):
        r = rng.uniform(5.6, 6.8) * SS
        return _petal_head(rgb, a, X, Y, cx, cy, r, 10, (58, 76, 196), (120, 156, 250), (70, 40, 110), tilt=0.72,
                           width=0.42, rng=rng, ragged=0.6)
    rgb, a = _flower_texture(36960, [(12, 28), (27, 21), (42, 30), (54, 19), (33, 38)], cornflower)
    F._finish(rgb, a, "l369_flower_0")

    def red_daisy(rgb, a, X, Y, cx, cy, rng):
        r = rng.uniform(9.0, 10.5) * SS
        return _petal_head(rgb, a, X, Y, cx, cy, r, 18, (176, 18, 34), (236, 64, 70), (92, 40, 18), tilt=0.66, rng=rng)
    rgb, a = _flower_texture(36961, [(16, 24), (40, 17), (52, 34)], red_daisy)
    F._finish(rgb, a, "l369_flower_1")

    def cosmos(rgb, a, X, Y, cx, cy, rng):
        r = rng.uniform(8.0, 9.2) * SS
        return _petal_head(rgb, a, X, Y, cx, cy, r, 8, (214, 104, 158), (248, 184, 214), (236, 186, 56), tilt=0.7,
                           width=0.62, rng=rng)
    rgb, a = _flower_texture(36962, [(18, 22), (44, 28)], cosmos)
    F._finish(rgb, a, "l369_flower_2")

    def white_daisy(rgb, a, X, Y, cx, cy, rng):
        r = rng.uniform(6.6, 7.6) * SS
        return _petal_head(rgb, a, X, Y, cx, cy, r, 16, (226, 222, 210), (252, 250, 244), (236, 186, 40), tilt=0.7, rng=rng)
    rgb, a = _flower_texture(36963, [(13, 27), (31, 19), (49, 29)], white_daisy)
    F._finish(rgb, a, "l369_flower_3")


def tex_l369_tufts() -> None:
    """Grass 0-3 (64 px): short soft grass, tall grass, grass with seed heads, grass with tiny
    buttercups. Tuft 0 also fills the straight planes of every plant model."""
    rng = G.rng_for(36964)
    X, Y, rgb, a = _blade_canvas()
    rgb, a = _soft_blades(rng, X, Y, rgb, a, 44, (0.35, 0.75))
    F._finish(rgb, a, "l369_tuft_0")

    X, Y, rgb, a = _blade_canvas()
    rgb, a = _soft_blades(rng, X, Y, rgb, a, 30, (0.6, 0.98), lean=0.3)
    rgb, a = _soft_blades(rng, X, Y, rgb, a, 16, (0.25, 0.5))
    F._finish(rgb, a, "l369_tuft_1")

    X, Y, rgb, a = _blade_canvas()
    rgb, a = _soft_blades(rng, X, Y, rgb, a, 30, (0.3, 0.65))
    for _ in range(5):
        x, top = rng.uniform(8, 56) * SS, rng.uniform(4, 14) * SS
        rgb, a = _stem_to(rng, X, Y, rgb, a, x, top, col=(118, 130, 70))
        r2, a2 = F._leaf_at(X, Y, x, top + 5 * SS, -np.pi / 2, 9 * SS, 2.2 * SS, (204, 190, 128), veins=0, gloss=0.1)
        rgb, a = F._composite(rgb, a, r2, a2)
    F._finish(rgb, a, "l369_tuft_2")

    X, Y, rgb, a = _blade_canvas()
    rgb, a = _soft_blades(rng, X, Y, rgb, a, 32, (0.3, 0.7))

    def buttercup(rgb, a, X, Y, cx, cy, rng):
        return _petal_head(rgb, a, X, Y, cx, cy, 2.6 * SS, 5, (232, 188, 30), (252, 226, 80), (200, 150, 20),
                           width=0.8, rng=rng)
    for hx, hy in ((14, 30), (30, 22), (44, 34), (54, 26)):
        rgb, a = _stem_to(rng, X, Y, rgb, a, hx * SS, hy * SS)
        rgb, a = buttercup(rgb, a, X, Y, hx * SS, hy * SS, rng)
    rgb, a = _soft_blades(rng, X, Y, rgb, a, 8, (0.15, 0.3))
    F._finish(rgb, a, "l369_tuft_3")


def tex_l369_glow() -> None:
    """The dark rooms' glowing plants 0-3 (cyan bells, pale green lanterns, violet fronds, a tuft of
    glowing moss) and the glowing vine (and its tip). Dark leaves, luminous flowers: the models are
    full-bright, so the texture's own colours are what you see in the dark."""
    leaf = (26, 54, 36)
    # 0: cyan bells on an arching stem.
    rng = G.rng_for(36970)
    X, Y, rgb, a = F._canvas()
    stem = [(8 * SS, 32 * SS), (10 * SS, 14 * SS), (17 * SS, 7 * SS), (25 * SS, 9 * SS)]
    r2, a2 = F._stem(X, Y, stem, 0.8 * SS, (34, 64, 44))
    rgb, a = F._composite(rgb, a, r2, a2)
    for side in (-1, 1):
        r2, a2 = F._leaf_at(X, Y, 9 * SS, 30 * SS, -np.pi / 2 + side * 0.6, 12 * SS, 4 * SS, leaf)
        rgb, a = F._composite(rgb, a, r2, a2)
    for x, y in ((12, 11), (17, 8.5), (22, 9), (26, 11.5)):
        r2, a2 = F._leaf_at(X, Y, x * SS, y * SS, np.pi / 2, 6 * SS, 4.4 * SS, (120, 236, 236), veins=0, tip=1.4,
                            blunt=-0.3, gloss=0.4)
        rgb, a = F._composite(rgb, a, r2, a2)
    F._finish(rgb, a, "l369_glow_0")

    # 1: pale green paper lanterns hanging from a stalk.
    X, Y, rgb, a = F._canvas()
    r2, a2 = F._stem(X, Y, [(16 * SS, 32 * SS), (15 * SS, 6 * SS)], 0.9 * SS, (34, 64, 44))
    rgb, a = F._composite(rgb, a, r2, a2)
    for y, side in ((26, -1), (20, 1), (14, -1)):
        r2, a2 = F._leaf_at(X, Y, 16 * SS, y * SS, -np.pi / 2 + side * 1.0, 9 * SS, 3.4 * SS, leaf)
        rgb, a = F._composite(rgb, a, r2, a2)
    for x, y in ((11, 13), (20, 10), (14, 21), (21, 19)):
        rr = np.hypot((X - x * SS) / 2.4, (Y - y * SS) / 3.0) / SS
        body = G.lerp(_col((200, 250, 170)), _col((120, 210, 110)), np.clip(rr, 0, 1)[..., None])
        rgb, a = F._composite(rgb, a, body, np.clip(1.0 - rr, 0, 1) * 4)
    F._finish(rgb, a, "l369_glow_1")

    # 2: violet fronds with glowing tips.
    rng = G.rng_for(36971)
    X, Y, rgb, a = F._canvas()
    for k in range(6):
        ang = -np.pi / 2 + (k - 2.5) * 0.36
        L = rng.uniform(18, 26) * SS
        r2, a2 = F._leaf_at(X, Y, 16 * SS, 31 * SS, ang, L, 3.0 * SS, (64, 40, 96), veins=5, tip=0.5)
        rgb, a = F._composite(rgb, a, r2, a2)
        tx, ty = 16 * SS + np.cos(ang) * L * 0.92, 31 * SS + np.sin(ang) * L * 0.92
        rr = np.hypot(X - tx, Y - ty)
        rgb, a = F._composite(rgb, a, np.broadcast_to(_col((214, 160, 255)), rgb.shape), np.clip(1.8 * SS - rr, 0, 1))
    F._finish(rgb, a, "l369_glow_2")

    # 3: a low tuft of glowing moss: fine pale-cyan threads with brighter beads.
    rng = G.rng_for(36972)
    X, Y, rgb, a = F._canvas()
    rgb, a = F._blades(rng, X, Y, rgb, a, 40, M * SS, (0.08, 0.26), ((60, 150, 140), (90, 190, 170), (40, 110, 100)),
                       w=(0.6, 1.0))
    for _ in range(14):
        cx, cy = rng.uniform(3, 29) * SS, rng.uniform(25, 31) * SS
        rr = np.hypot(X - cx, Y - cy)
        rgb, a = F._composite(rgb, a, np.broadcast_to(_col((180, 255, 236)), rgb.shape), np.clip(0.9 * SS - rr, 0, 1))
    F._finish(rgb, a, "l369_glow_3")

    # The glowing vine: a thin strand with small leaves and tiny pale-blue glowing flowers (tiles
    # vertically), and its tip.
    for tip in (False, True):
        rng = G.rng_for(36973)
        X, Y, rgb, a = F._canvas()
        end = (0.65 if tip else 1.2) * M * SS
        for x0 in (11, 21):
            pts = [(x0 * SS + 1.5 * SS * np.sin(2 * np.pi * yy / (M * SS) + x0), yy)
                   for yy in np.linspace(0, M * SS, 17) if yy <= end]
            r2, a2 = F._stem(X, Y, pts, 0.8 * SS, (36, 70, 44))
            rgb, a = F._composite(rgb, a, r2, a2)
            for px, py in pts[1:-1:3]:
                r2, a2 = F._leaf_at(X, Y, px, py, np.pi / 2 + rng.choice([-1, 1]) * 0.9, 5 * SS, 2.2 * SS, leaf)
                rgb, a = F._composite(rgb, a, r2, a2)
            for px, py in pts[2::4]:
                rr = np.hypot(X - px - rng.normal(0, 1.2) * SS, Y - py)
                rgb, a = F._composite(rgb, a, np.broadcast_to(_col((170, 220, 255)), rgb.shape),
                                      np.clip(1.3 * SS - rr, 0, 1))
        F._finish(rgb, a, "l369_glow_vine_tip" if tip else "l369_glow_vine")


# ------------------------------------------------------------------------------- models

FULL_BRIGHT = {"block_light": 15, "sky_light": 15}


def _bright_cube(name: str, tex: str, cutout: bool = False) -> None:
    """A full cube that glows: drawn full-bright (its texture as it is, whatever the light)."""
    faces = {f: {"texture": "#all", "cullface": f} for f in ("north", "south", "east", "west", "up", "down")}
    model = {"parent": "minecraft:block/block", "textures": {"all": F._ref(tex), "particle": F._ref(tex)},
             "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "neoforge_data": FULL_BRIGHT, "faces": faces}]}
    if cutout:
        model["render_type"] = "minecraft:cutout"
    F._model(name, model)


def _bright_cross(name: str, tex: str) -> None:
    """Vanilla's cross (two diagonal planes) drawn full-bright: the glowing plants."""
    def plane(frm, to, faces):
        return {"from": frm, "to": to, "shade": False, "neoforge_data": FULL_BRIGHT,
                "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True},
                "faces": {f: {"uv": [0, 0, 16, 16], "texture": "#cross"} for f in faces}}
    F._model(name, {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
                    "textures": {"cross": F._ref(tex), "particle": F._ref(tex)},
                    "elements": [plane([0.8, 0, 8], [15.2, 16, 8], ("north", "south")),
                                 plane([8, 0, 0.8], [8, 16, 15.2], ("east", "west"))]})


def _simple(name: str) -> None:
    F._state(name, {"": F._v(name)})
    F._item_block(name, name)


def _meadow(name: str, tex: str, fill: str, height: float, fill_height: float) -> None:
    """A meadow plant: two crossed planes of `tex` (`height` px tall, taller than a block for long
    grass), and two straight planes of `fill` grass (`fill_height` px) set off the middle. Every
    face takes tint 0: the block colour is the sway marker the cutout shader looks for
    (assets/minecraft/shaders/core/rendertype_cutout_mipped.vsh), and no top is a whole block
    high (the shader keeps vertices at whole block heights, the roots, still)."""
    def plane(frm, to, faces, texture, rotate):
        e = {"from": frm, "to": to, "shade": False,
             "faces": {f: {"uv": [0, 0, 16, 16], "texture": texture, "tintindex": 0} for f in faces}}
        if rotate:
            e["rotation"] = {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True}
        return e
    assert height % 16 and fill_height % 16
    F._model(name, {"parent": "minecraft:block/block", "render_type": "minecraft:cutout_mipped", "ambientocclusion": False,
                    "textures": {"plant": F._ref(tex), "fill": F._ref(fill), "particle": F._ref(tex)},
                    "elements": [plane([0.8, 0, 8], [15.2, height, 8], ("north", "south"), "#plant", True),
                                 plane([8, 0, 0.8], [8, height, 15.2], ("east", "west"), "#plant", True),
                                 plane([0, 0, 4.5], [16, fill_height, 4.5], ("north", "south"), "#fill", False),
                                 plane([11.5, 0, 0], [11.5, fill_height, 16], ("east", "west"), "#fill", False)]})


def tex_l369_models() -> None:
    """Block models, blockstates and item models of every Level 369 block."""
    for name in [f"l369_plaster_{c}" for c in PLASTERS] + ["l369_ceiling", "l369_ceiling_rose", "l369_brick",
                                                           "l369_soil", "l369_vine_wall"]:
        F._model(name, {"parent": "minecraft:block/cube_all", "textures": {"all": F._ref(name)}})
        _simple(name)
    F._model("l369_grass", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": F._ref("l369_grass"), "side": F._ref("l369_grass_side"), "bottom": F._ref("l369_soil")}})
    F._model("l369_leaves", {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout_mipped",
                             "textures": {"all": F._ref("l369_leaves")}})
    _simple("l369_leaves")
    _simple("l369_grass")
    for name in ("l369_panel", "l369_skylight", "l369_window"):
        _bright_cube(name, name)
        _simple(name)
    _bright_cube("l369_veil", "l369_veil", cutout=True)
    _simple("l369_veil")

    # The meadow: flowers and grass are two crossed planes of their own texture and two straight
    # planes of soft grass (tuft 0; tuft 1 for tuft 0 itself), so blocks merge into one meadow.
    # Mipmapped cutout, so it stays soft in the distance instead of breaking up into speckle.
    for k in range(4):
        _meadow(f"l369_flower_{k}", f"l369_flower_{k}", "l369_tuft_0", 15.5, 12)
        _meadow(f"l369_tuft_{k}", f"l369_tuft_{k}", "l369_tuft_1" if k == 0 else "l369_tuft_0",
                20 if k == 1 else 15, 11)
        _bright_cross(f"l369_glow_{k}", f"l369_glow_{k}")
    for block in ("l369_flower", "l369_tuft", "l369_glow"):
        F._state(block, {f"kind={k}": [F._v(f"{block}_{k}", 0, y) for y in F.SPINS] for k in range(4)})
        F._item_flat(block, F._ref(f"{block}_0"))

    _bright_cross("l369_glow_vine", "l369_glow_vine")
    _bright_cross("l369_glow_vine_tip", "l369_glow_vine_tip")
    F._state("l369_glow_vine", {"tip=false": [F._v("l369_glow_vine"), F._v("l369_glow_vine", 0, 90)],
                                "tip=true": [F._v("l369_glow_vine_tip"), F._v("l369_glow_vine_tip", 0, 90)]})
    F._item_flat("l369_glow_vine", F._ref("l369_glow_vine_tip"))


# ------------------------------------------------------------------------------- ambience

def snd_l369_ambience() -> None:
    """The level's ambience (the biome's ambient loop, 48 s, seamless): a soft breeze moving
    through the rooms in slow gusts, leaves and grass stirring with it, a sprinkler ticking round
    far off now and then, and, when the gusts are strongest, the flowers 'singing' (the wiki): a
    very faint, high, warm chord that swells in and out with the wind."""
    rng = G.rng_for(36990)
    T = 48.0
    n = int(T * G.SR)
    t = np.arange(n) / G.SR

    def swell(cycles, phase):
        return 0.5 + 0.5 * np.sin(2 * np.pi * cycles * t / T + phase)

    gust = 0.35 + 0.65 * (0.6 * swell(3, 0.0) + 0.4 * swell(5, 1.7)) ** 1.6
    breeze = G.norm(G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 70, 900, 1) / np.sqrt(np.maximum(f, 30))))
    rustle = G.norm(G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 2200, 8000, 2)))
    flutter = np.clip(G.norm(G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 4, 16, 1))), 0, None)
    x = breeze * gust * 0.55 + rustle * (0.15 + flutter) * gust ** 2 * 0.22

    # The flowers' song: a warm chord (F major add9), whole cycles per loop, only in the gusts.
    song = np.zeros(n)
    for hz, amp in ((698.5, 1.0), (880.0, 0.7), (1046.5, 0.6), (1568.0, 0.35), (1396.9, 0.4)):
        f = round(hz * T) / T
        song += amp * np.sin(2 * np.pi * f * t + rng.uniform(0, 6.3))
    sing = np.clip((gust - 0.75) / 0.25, 0, 1) ** 2
    x += G.norm(song) * sing * 0.035

    # A sprinkler far off: a soft ticking burst of hiss twice a loop.
    tick = np.zeros(n)
    for start in (11.0, 33.5):
        for k in range(26):
            G.add_at(tick, G.norm(rng.normal(size=int(0.03 * G.SR))) * np.exp(-np.arange(int(0.03 * G.SR)) / 200)
                     * (0.4 + 0.6 * np.sin(np.pi * k / 25)), start + k * 0.18, circular=True)
    tick = G.fft_filter(tick, lambda f: G.bp_gain(f, 1500, 6000, 2), circular=True)
    x += G.norm(tick) * 0.06
    _save_loop(x, G.SND / "ambient" / "level369_ambience.ogg", peak=0.5)


def _save_loop(x: np.ndarray, path, peak: float) -> None:
    """G.save_ogg for a long loop: the same high-quality Vorbis, but written a second at a time
    (libsndfile's Vorbis encoder overflows its stack on one huge write, see G._m_save)."""
    import soundfile as sf
    x = G.norm(x - x.mean(), peak)
    path.parent.mkdir(parents=True, exist_ok=True)
    gain = 1.0
    for _ in range(6):
        with sf.SoundFile(str(path), "w", G.SR, 1, format="OGG", subtype="VORBIS",
                          compression_level=0.0) as out:
            y = (x * gain).astype(np.float32)
            for i in range(0, len(y), G.SR):
                out.write(y[i:i + G.SR])
        decoded = np.abs(sf.read(str(path))[0]).max()
        if decoded <= max(peak, 0.9) + 0.02:
            break
        gain *= peak / decoded


TEXTURES = [tex_l369_plasters, tex_l369_ceilings, tex_l369_lights, tex_l369_brick, tex_l369_ground, tex_l369_vines,
            tex_l369_flowers, tex_l369_tufts, tex_l369_glow, tex_l369_models]
SOUNDS = [snd_l369_ambience]
