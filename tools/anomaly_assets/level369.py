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


GRASS_TOP = ((54, 104, 38), (66, 120, 44), (80, 132, 50), (46, 92, 34), (92, 140, 56))


def tex_l369_ground() -> None:
    """The lawn: dense blades seen from above, a little sun-bleached here and there; its side
    (the soil under a fringe of grass) and the soil itself."""
    rng = G.rng_for(36940)
    X, Y = F._grid(N)
    img = G.fill((58, 104, 40), (N, N))
    patch = G.blur(F._noise(rng, N, beta=2.2, kmin=1.5), 1.5)
    img = G.add_lum(img, patch * 6, (0.8, 1.0, 0.6))
    for _ in range(900):
        x, y = rng.uniform(0, N), rng.uniform(0, N)
        ang = rng.uniform(0, np.pi)
        L = rng.uniform(1.5, 3.5)
        col = np.array(GRASS_TOP[rng.integers(len(GRASS_TOP))], float) * rng.uniform(0.85, 1.12)
        for k in np.linspace(0, 1, 5):
            px = int(x + np.cos(ang) * L * k) % N
            py = int(y + np.sin(ang) * L * k) % N
            img[py, px] = G.lerp(img[py, px], col, 0.75)
    img = G.add_lum(img, rng.normal(0, 2.0, (N, N)))
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


# ------------------------------------------------------------------------------- plants (32 px)

def _daisy(rgb, a, X, Y, cx, cy, r, petals, col, centre, tilt=0.55, width=0.42, rng=None):
    """A daisy-like head seen a little from the side (squashed by `tilt`): `petals` petals of
    colour `col` round a centre."""
    for k in range(petals):
        ang = k * 2 * np.pi / petals + (rng.uniform(-0.12, 0.12) if rng is not None else 0)
        ex, ey = np.cos(ang), np.sin(ang) * tilt
        px, py = cx + ex * r * 0.55, cy + ey * r * 0.55
        r2, a2 = F._leaf_at(X, Y, px, py, np.arctan2(ey, ex), r * 1.05, r * width, col, veins=0, gloss=0.15)
        rgb, a = F._composite(rgb, a, r2, a2)
    rr = np.hypot((X - cx) / 1.0, (Y - cy) / tilt)
    rgb, a = F._composite(rgb, a, np.broadcast_to(_col(centre), rgb.shape), np.clip(r * 0.32 - rr + 0.5, 0, 1))
    return rgb, a


def _stems_with_leaves(rng, X, Y, rgb, a, tops, col=(60, 104, 42)):
    for x, y in tops:
        r2, a2 = F._stem(X, Y, [(x + rng.normal(0, 1) * SS, M * SS), (x, y)], 0.8 * SS, col)
        rgb, a = F._composite(rgb, a, r2, a2)
    for _ in range(3):
        x = rng.uniform(8, 24) * SS
        for side in (-1, 1):
            r2, a2 = F._leaf_at(X, Y, x, M * SS, -np.pi / 2 + side * rng.uniform(0.5, 0.9), rng.uniform(8, 12) * SS,
                                2.6 * SS, (52, 100, 38))
            rgb, a = F._composite(rgb, a, r2, a2)
    return rgb, a


def tex_l369_flowers() -> None:
    """Flowers 0-3: blue forget-me-nots, red daisies, pink cosmos, white daisies."""
    # 0: forget-me-nots: clusters of small sky-blue five-petalled flowers with yellow eyes.
    rng = G.rng_for(36960)
    X, Y, rgb, a = F._canvas()
    rgb, a = F._blades(rng, X, Y, rgb, a, 10, M * SS, (0.2, 0.5), GRASS_TOP)
    for _ in range(4):
        x0, top = rng.uniform(7, 25) * SS, rng.uniform(6, 15) * SS
        r2, a2 = F._stem(X, Y, [(x0, M * SS), (x0 + rng.normal(0, 1.5) * SS, top)], 0.7 * SS, (64, 104, 44))
        rgb, a = F._composite(rgb, a, r2, a2)
        for _ in range(6):
            cx, cy = x0 + rng.normal(0, 2.6) * SS, top + rng.normal(0, 2.0) * SS
            ang = np.arctan2(Y - cy, X - cx)
            rr = np.hypot(X - cx, Y - cy)
            petal = rr < (1.3 + 0.8 * (0.5 + 0.5 * np.cos(5 * ang))) * SS
            col = G.lerp(_col((64, 112, 236)), _col((150, 186, 252)), np.clip(rr / (2.0 * SS), 0, 1)[..., None])
            rgb, a = F._composite(rgb, a, col, petal.astype(float))
            rgb, a = F._composite(rgb, a, np.broadcast_to(_col((250, 222, 90)), rgb.shape),
                                  (rr < 0.5 * SS).astype(float))
    F._finish(rgb, a, "l369_flower_0")

    # 1: red daisies (gerbera): wide flat heads of narrow red petals round a dark centre.
    rng = G.rng_for(36961)
    X, Y, rgb, a = F._canvas()
    heads = [(10 * SS, 12 * SS), (22 * SS, 9 * SS)]
    rgb, a = _stems_with_leaves(rng, X, Y, rgb, a, heads)
    for cx, cy in heads:
        rgb, a = _daisy(rgb, a, X, Y, cx, cy, 6.5 * SS, 18, (214, 34, 46), (70, 34, 20), rng=rng)
    F._finish(rgb, a, "l369_flower_1")

    # 2: pink cosmos: eight broad petals, a yellow centre, on thin stems with feathery leaves.
    rng = G.rng_for(36962)
    X, Y, rgb, a = F._canvas()
    heads = [(9 * SS, 9 * SS), (21 * SS, 14 * SS)]
    rgb, a = _stems_with_leaves(rng, X, Y, rgb, a, heads, col=(70, 112, 48))
    for cx, cy in heads:
        rgb, a = _daisy(rgb, a, X, Y, cx, cy, 6.0 * SS, 8, (238, 140, 184), (242, 196, 64), width=0.7, rng=rng)
    F._finish(rgb, a, "l369_flower_2")

    # 3: white daisies: small, many petals, yellow button centres, three heads.
    rng = G.rng_for(36963)
    X, Y, rgb, a = F._canvas()
    heads = [(8 * SS, 14 * SS), (16 * SS, 8 * SS), (24 * SS, 15 * SS)]
    rgb, a = _stems_with_leaves(rng, X, Y, rgb, a, heads)
    for cx, cy in heads:
        rgb, a = _daisy(rgb, a, X, Y, cx, cy, 4.6 * SS, 16, (246, 244, 236), (238, 190, 40), rng=rng)
    F._finish(rgb, a, "l369_flower_3")


def tex_l369_tufts() -> None:
    """Grass tufts 0-3: short, tall, clover, seed heads."""
    rng = G.rng_for(36964)
    X, Y, rgb, a = F._canvas()
    rgb, a = F._blades(rng, X, Y, rgb, a, 34, M * SS, (0.18, 0.45), GRASS_TOP)
    F._finish(rgb, a, "l369_tuft_0")

    X, Y, rgb, a = F._canvas()
    rgb, a = F._blades(rng, X, Y, rgb, a, 30, M * SS, (0.45, 0.9), GRASS_TOP, lean=0.35)
    rgb, a = F._blades(rng, X, Y, rgb, a, 16, M * SS, (0.2, 0.4), GRASS_TOP)
    F._finish(rgb, a, "l369_tuft_1")

    X, Y, rgb, a = F._canvas()
    rgb, a = F._blades(rng, X, Y, rgb, a, 10, M * SS, (0.15, 0.35), GRASS_TOP)
    for _ in range(5):
        x0, top = rng.uniform(6, 26) * SS, rng.uniform(17, 25) * SS
        r2, a2 = F._stem(X, Y, [(x0, M * SS), (x0 + rng.normal(0, 1) * SS, top)], 0.6 * SS, (70, 112, 48))
        rgb, a = F._composite(rgb, a, r2, a2)
        for k in range(3):
            ang = -np.pi / 2 + (k - 1) * 2.1
            col = np.array((62, 118, 46)) * rng.uniform(0.9, 1.1)
            r2, a2 = F._leaf_at(X, Y, x0, top, ang, 3.4 * SS, 3.0 * SS, col, veins=0, tip=1.2, blunt=0.6)
            rgb, a = F._composite(rgb, a, r2, a2)
    F._finish(rgb, a, "l369_tuft_2")

    X, Y, rgb, a = F._canvas()
    rgb, a = F._blades(rng, X, Y, rgb, a, 22, M * SS, (0.25, 0.55), GRASS_TOP)
    for _ in range(4):
        x0, top = rng.uniform(6, 26) * SS, rng.uniform(3, 9) * SS
        r2, a2 = F._stem(X, Y, [(x0, M * SS), (x0 + rng.normal(0, 2) * SS, top)], 0.5 * SS, (120, 132, 70))
        rgb, a = F._composite(rgb, a, r2, a2)
        r2, a2 = F._leaf_at(X, Y, x0, top + 3.5 * SS, -np.pi / 2, 7 * SS, 1.8 * SS, (190, 176, 110), veins=0)
        rgb, a = F._composite(rgb, a, r2, a2)
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

    for block, prefix, bright in (("l369_flower", "l369_flower", False), ("l369_tuft", "l369_tuft", False),
                                  ("l369_glow", "l369_glow", True)):
        for k in range(4):
            (_bright_cross if bright else F._cross)(f"{prefix}_{k}", f"{prefix}_{k}")
        F._state(block, {f"kind={k}": [F._v(f"{prefix}_{k}", 0, y) for y in F.SPINS] for k in range(4)})
        F._item_flat(block, F._ref(f"{prefix}_0"))

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
