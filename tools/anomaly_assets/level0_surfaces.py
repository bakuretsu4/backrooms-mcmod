"""Level 0 surfaces: the wallpaper, its baseboard, the carpet and the acoustic ceiling tiles,
plus their aged versions.

Every texture is one block (128 px = 1 m) and tiles with itself and with its own versions:
- the plain texture of each surface is what most blocks get (about 70-80%);
- versions 1-3 add one local sign of age (a leak, grime, a worn patch, a cracked tile). The
  world generator picks the version per block (block/Level0SurfaceBlock; the chances are in
  registry/ModBlocks and Level0Blocks), and the aged ones cluster in "worn patches", so a damp
  stretch of hall has damp carpet, water-ringed tiles and a stained wall together. Marks stay
  clear of the block edges, unless the edge is where the mark really starts (a leak running down
  from the ceiling, damp rising from the floor).
- the blockstates also turn and mirror the carpet and ceiling blocks at random
  (gen_assets.SURFACE_TURNS), so even the plain texture does not repeat in a visible grid.

Colours are agreed with the lighting (client/ClientLighting, AtmosphereGrade): mean albedo
wallpaper (186, 168, 111), carpet (146, 121, 74), ceiling (212, 204, 184) (gen_assets.WALLPAPER,
CARPET, CEILING). That is a faded, warm yellow-beige, not olive. The plain textures are scaled to
hit those means exactly.

Run with `python tools/gen_assets.py level0_surfaces` from the project root. Textures derived
from these (wall drawings, the carpet arrow, graffiti, the corridor light strip, the red-room and
anomaly papers) come from their own generators: `lobby_carpet_arrow wall_decals rug
level0_revamp_chevron level0_revamp_graffiti level0_revamp_strip level0_a_`. Every texture uses
fixed seeds, so re-running gives identical files.
"""
from __future__ import annotations

import numpy as np

import gen_assets as G

S = G.S

WALL_MEAN = G.WALLPAPER      # the agreed mean albedos (see gen_assets.py, Level 0)
CARPET_MEAN = G.CARPET
TILE_MEAN = G.CEILING
BOARD = (178, 160, 110)       # the baseboard paint: a slightly deeper cream than the paper
TBAR = G.TBAR                 # the same painted T-bar as round the light panels
STAIN = (148, 112, 58)        # old water: tea-brown tide lines
GRIME = (104, 92, 70)         # hand grease and dirt
PLENUM = (26, 24, 21)         # the dark void above the ceiling


def _calibrate(img: np.ndarray, mean) -> np.ndarray:
    """Scale each channel so the texture's average colour is exactly `mean` (the lighting is
    tuned against these numbers)."""
    return img * (np.asarray(mean, float) / img.reshape(-1, 3).mean(axis=0))


def _wobble(rng, n=S, amp=1.0, smooth=6.0) -> np.ndarray:
    """A smooth random 1D curve (n samples, wraps round) with unit-ish amplitude."""
    k = np.fft.fftfreq(n)
    a = np.real(np.fft.ifft(np.fft.fft(rng.normal(0, 1, n)) * np.exp(-2 * (np.pi * smooth * k) ** 2)))
    return amp * a / (a.std() + 1e-12)


def _sat(img: np.ndarray, amount) -> np.ndarray:
    """Change saturation: amount < 0 greys the colour, > 0 deepens it (per pixel if an array)."""
    grey = img.mean(axis=2, keepdims=True)
    a = np.asarray(amount, float)
    return grey + (img - grey) * (1 + (a[..., None] if a.ndim else a))


# =============================================================================
# Wallpaper
# =============================================================================
# The real Backrooms paper (see tools/../l0ref/user_wallpaper_target.png): repeating vertical
# bands (thin lines either side of a column of small "flame" shapes) and plain panels with
# feathered ikat chevrons, the chevrons of every other panel half a repeat lower. Printed tone on
# tone, only ~7% darker than the paper, and at half the old scale (32 px = 25 cm per repeat), so
# from across a hall it reads as a faint vertical texture, never as big zigzags.

REPEAT = 32.0                 # px across one band + panel
DROP = 64.0                   # px from one chevron pair to the next in the same panel


def _thread(rng, amp, sigma=0.5) -> np.ndarray:
    """One random offset per (sub-)pixel column: the dyed threads of an ikat each stop at a
    slightly different height, which gives its edges their ragged look."""
    return _wobble(rng, S * 4, amp, sigma * 4)


def _flame(U, V):
    """The soft tulip/flame shape of the band column (U -1..1 across, V 0..1 top to point)."""
    half = np.where(V < 0.3, 0.7 + V, np.clip(1.0 - (V - 0.3) / 0.7, 0, 1))
    notch = (V < 0.3 - 0.8 * np.abs(U)) & (np.abs(U) < 0.3)
    return (V > 0) & (V < 1) & (np.abs(U) < half) & ~notch


def wallpaper_design(seed=101) -> np.ndarray:
    """Where the ink is (0..1), drawn at 4x and averaged down so the thin lines stay smooth."""
    rng = G.rng_for(seed)
    ss = 4
    yy, xx = np.mgrid[0:S * ss, 0:S * ss]
    X, Y = (xx + 0.5) / ss, (yy + 0.5) / ss
    xb = X % REPEAT
    m = np.zeros(X.shape)
    for x0 in (0.4, 1.9, 2.9, 11.6, 12.6, 14.1):                         # the thin lines
        m = np.maximum(m, ((xb >= x0) & (xb < x0 + 0.6)).astype(float))
    c0, c1 = 4.6, 9.8                                                    # the flame column
    tp = S / 6
    U, V = (xb - (c0 + c1) / 2) / 2.0, ((Y % tp) - 2.5) / (tp * 0.62)
    m = np.maximum(m, ((xb >= c0) & (xb < c1)) * (1 - 0.95 * _flame(U, V)))
    p0, p1 = 15.2, 31.6                                                  # the chevron panel
    pc, hw = (p0 + p1) / 2, (p1 - p0) / 2
    ys = (Y + np.floor(X / REPEAT) * (DROP / 2)) % DROP                  # every other panel half a drop lower
    ax = np.abs(xb - pc)
    col = np.floor(xx).astype(int) % (S * ss)
    chev = np.zeros(X.shape, bool)
    for apex, thick in ((5.0, 7.5), (19.5, 7.0)):                        # outer ^, inner ^ below it
        top = _thread(rng, 0.7) - np.clip(_thread(rng, 1.0), 0.6, None) * 2.0 + 1.2
        bottom = thick + _thread(rng, 0.8) + np.clip(_thread(rng, 1.0), 0.7, None) * 2.5 - 1.8
        t = ys - (apex + 1.4 * ax)
        chev |= (t > top[col]) & (t < bottom[col]) & (ax < hw)
    m = np.maximum(m, chev.astype(float))
    m = m.reshape(S, ss, S, ss).mean(axis=(1, 3))
    flecks = G.fnoise(rng, beta=0.8, aniso=(1, 6), kmin=10) > 1.5        # where the dye didn't take
    return np.clip(m * (1 - 0.45 * flecks), 0, 1)


def _paper_field(rng) -> np.ndarray:
    """Plain matte paper: two strips per block (64 px each) from slightly different dye lots,
    with a faint yellowed glue line at each seam, fine fibre grain and a very faint unevenness.
    The unevenness is kept tiny because it repeats every block; the real light/dark variation
    comes from the lighting and from the aged versions."""
    X, Y = G.grid()
    img = G.fill(WALL_MEAN, (S, S))
    lum = (0.8 * G.fnoise(rng, beta=3.0, kmin=1, kmax=3)
           + 0.9 * G.blur(G.fnoise(rng, beta=0.8, kmin=24), 0.6)
           + 0.7 * rng.normal(0, 1, (S, S))
           + 0.6 * G.fnoise(rng, beta=1.2, aniso=(1, 5), kmin=6))       # faint vertical paper grain
    img = G.add_lum(img, lum, (1.0, 0.97, 0.86))
    lot = np.where(X < 64, 0.0, 1.0)[..., None]                          # the second strip's dye lot
    img = img * G.lerp(np.array([1.0, 1.0, 1.0]), np.array([1.006, 0.996, 0.975]), lot)
    seam = np.abs(G.wrapd(X, 64.0))                                      # distance to a seam (x = 0, 64)
    img = G.add_lum(img, -3.5 * np.exp(-(seam / 0.7) ** 2), (1.0, 1.0, 0.7))
    img = G.add_lum(img, 2.0 * np.exp(-((seam - 1.3) / 0.6) ** 2))       # the overlap catches light
    return img


def wallpaper(stains=False, seed=101) -> np.ndarray:
    """The plain Lobby wallpaper (calibrated to WALL_MEAN). stains=True adds two faint old water
    marks: only for drawings and anomaly blocks derived from the paper; the wall blocks get
    their marks from the versions instead, so the same mark never repeats in a grid."""
    rng = G.rng_for(seed + 1)
    img = _paper_field(rng)
    ink = img * np.array([0.935, 0.928, 0.885])                         # ~7% darker, a touch richer
    img = G.lerp(img, ink, wallpaper_design(seed)[..., None])
    img = _calibrate(img, WALL_MEAN)
    if stains:
        img = _stain_blob(img, G.rng_for(seed + 2), 34, 40, 16, 20, 0.07)
        img = _stain_blob(img, G.rng_for(seed + 3), 94, 88, 12, 14, 0.05)
    return img


def _stain_blob(img, rng, cx, cy, sx, sy, a) -> np.ndarray:
    """A soft old water mark with a darker tide line, kept inside the block."""
    m = G._local(cx, cy, sx, sy) * (1 + 0.35 * G.fnoise(rng, beta=2.0))
    rim = np.clip(G.smoothstep(0.26, 0.34, m) - G.smoothstep(0.34, 0.46, m), 0, 1)
    return G.over(img, np.array(STAIN), (a * G.smoothstep(0.3, 0.6, m) + 1.6 * a * rim) * G._edge_fade())


def _leak(img, rng) -> np.ndarray:
    """Version for the wall row right under the ceiling only: water that came through the ceiling
    ran down the paper and dried. Lobed brown stain from the top edge, nested tide lines, a
    paler bleached middle and one or two drip trails below it."""
    X, Y = G.grid()
    cx, w = rng.uniform(52, 76), rng.uniform(32, 44)
    depth = rng.uniform(66, 88)
    x = X[0]
    reach = np.zeros(S)
    for _ in range(int(rng.integers(3, 5))):                              # the water ran down in a few lobes
        c = cx + rng.uniform(-0.55, 0.55) * w
        s = rng.uniform(0.4, 0.65) * w
        reach = np.maximum(reach, depth * rng.uniform(0.6, 1.0) * np.exp(-((x - c) / s) ** 2) ** 0.7)
    reach = reach * G.smoothstep(cx - w, cx - 0.55 * w, x) * G.smoothstep(cx + w, cx + 0.55 * w, x)
    reach = (reach * (1 + 0.05 * _wobble(rng, S, 1.0, 1.5)))[None, :]   # how far down it got at x
    fill = G.smoothstep(-1.5, 3.0, reach - Y) * G.smoothstep(0.5, 4.0, reach)
    img = _sat(img, -0.12 * fill * G.smoothstep(6, 30, reach - Y))       # the middle washed out
    img = G.add_lum(img, 2.0 * fill * G.smoothstep(6, 30, reach - Y))
    img = G.over(img, np.array(STAIN), 0.1 * fill)
    for f, a in ((1.0, 0.36), (0.74, 0.16), (0.5, 0.1)):                 # this leak's and older ones' tide lines
        r = reach * f + (0 if f == 1.0 else 2.0 * _wobble(rng, S, 1.0, 3.0)[None, :])
        line = np.exp(-((Y - r) / 1.5) ** 2) * G.smoothstep(2, 8, r)
        img = G.over(img, np.array(STAIN) * 0.85, a * line)
    for _ in range(int(rng.integers(1, 3))):                              # drip trails
        x0 = cx + rng.uniform(-0.5, 0.5) * w
        y0 = float(reach[0, int(x0)]) - 2
        L = min(rng.uniform(16, 30), 114 - y0)                            # stop clear of the block below
        trail = np.exp(-((X - x0 - 0.6 * np.sin((Y - y0) / 9)) / 0.9) ** 2) * (Y > y0) * G.smoothstep(y0 + L, y0, Y)
        img = G.over(img, np.array(STAIN), 0.2 * trail)
    return img


def _palm(cx, cy, scale, ang) -> np.ndarray:
    """A faint greasy palm print (palm oval and four finger streaks), as a 0..1 mask."""
    X, Y = G.grid()
    c, s = np.cos(ang), np.sin(ang)
    u, v = (X - cx) * c + (Y - cy) * s, -(X - cx) * s + (Y - cy) * c
    m = np.exp(-((u / (4.2 * scale)) ** 2 + (v / (5.0 * scale)) ** 2) ** 1.5)
    for fx, len_ in ((-3.6, 6.0), (-1.2, 7.2), (1.2, 7.0), (3.4, 5.6)):
        fy = -5.0 * scale - len_ * scale / 2
        m = np.maximum(m, 0.8 * np.exp(-(((u - fx * scale) / (1.0 * scale)) ** 2 + ((v - fy) / (len_ * scale / 2)) ** 2) ** 1.5))
    return m


def _hand_grime(img, rng) -> np.ndarray:
    """Version for the wall row just above the baseboard (1-2 m up) only: where people steadied
    themselves for years, faint greasy hand marks in the lower half, a grey rub band at hip height
    where carts and shoulders brushed it, and the print worn a little paler there."""
    X, Y = G.grid()
    m = np.zeros((S, S))
    for _ in range(int(rng.integers(3, 6))):
        cx, cy = rng.uniform(26, 102), rng.uniform(72, 104)
        m = np.maximum(m, rng.uniform(0.4, 1.0) * _palm(cx, cy, rng.uniform(0.85, 1.1), rng.uniform(-0.4, 0.4)))
    m = G.blur(m, 0.7)
    smear = G.smoothstep(0.3, 1.6, G.fnoise(rng, beta=2.2, kmin=2)) * G._local(64, 92, 40, 16)
    y0 = rng.uniform(104, 112)
    rub = np.exp(-((Y - y0 - 1.2 * _wobble(rng, S, 1.0, 8.0)[None, :]) / 3.0) ** 2)
    rub = rub * (0.6 + 0.4 * np.clip(G.fnoise(rng, beta=1.0, aniso=(8, 1), kmin=3), -1, 1)) * G.smoothstep(4, 30, np.minimum(X, S - X))
    worn = G.lerp(img, G.blur(img, 1.0) + 4.0, (0.5 * rub)[..., None])
    worn = G.over(worn, np.array(GRIME), 0.22 * m + 0.1 * smear + 0.1 * rub)
    return G.lerp(img, worn, G._edge_fade(10)[..., None])


def _faded_seam(img, rng) -> np.ndarray:
    """Version for any wall row: one strip faded paler and greyer than its neighbour (a different
    dye lot that aged worse), and the seam between them lifting: a hairline shadow under the
    raised edge, the edge catching the light, and yellowed paste showing along it."""
    X, Y = G.grid()
    left = rng.random() < 0.5
    side = (X < 64) if left else (X >= 64)
    to_edge = np.minimum(X, S - X)                                        # fade towards the block's sides
    fade = side * G.smoothstep(2, 30, to_edge) * G.smoothstep(2, 26, np.minimum(Y, S - Y))
    fade = fade * (0.8 + 0.2 * np.clip(G.fnoise(rng, beta=3.5, kmin=1), -1, 1))
    img = _sat(img, -0.15 * fade)
    img = G.add_lum(img, 3.5 * fade, (1.0, 1.0, 1.04))
    y0, y1 = sorted(rng.uniform(18, 110, 2))
    if y1 - y0 < 34:
        y0, y1 = max(16.0, y0 - 18), min(112.0, y1 + 18)
    along = G.smoothstep(y0, y0 + 10, Y) * G.smoothstep(y1, y1 - 10, Y)
    paste = np.exp(-((X - 64) / 1.8) ** 2) * G.smoothstep(6, 26, np.minimum(Y, S - Y))
    img = G.over(img, np.array([170, 140, 70]), 0.16 * paste)
    lift = 1 if left else -1                                               # the faded strip's edge lifts
    img = G.add_lum(img, 16 * along * np.exp(-((X - 64 + 0.8 * lift) / 0.55) ** 2))
    return G.add_lum(img, -14 * along * np.exp(-((X - 64 - 1.0 * lift) / 0.7) ** 2))


def wallpaper_marks(i, rng, img):
    """Versions 1-3 of the wallpaper. Version 1 and 2 are only used on their own wall row
    (Level0SurfaceBlock), so the leak always meets the ceiling and the hand marks sit at hand
    height."""
    if i == 1:
        return _leak(img, rng)
    if i == 2:
        return _hand_grime(img, rng)
    return _faded_seam(img, rng)


# =============================================================================
# Baseboard (the wall row on the floor)
# =============================================================================

BOARD_TOP = S - 16            # 2 px of 16: Kane's slim board


def baseboard(img, board_col=BOARD) -> np.ndarray:
    """A slim painted board on the bottom 16 px of a wall texture: a rounded top edge catching the
    light, a hairline shadow on the paper above, the face shading away, a grey line of dust
    along its top, and the bottom few cm of paper a shade dirtier where mops have hit it for
    decades."""
    rng = G.rng_for(121)
    X, Y = G.grid()
    top = BOARD_TOP
    img = G.over(img, np.array(GRIME), 0.06 * G.smoothstep(top - 12, top, Y) * (Y < top))   # mop line
    board = G._smooth_concrete(rng, board_col, mottle=1.2, fine=0.5)
    board = G.add_lum(board, 1.0 * G.fnoise(rng, beta=1.5, aniso=(8, 1), kmin=3))           # faint wood grain under the paint
    board = G.add_lum(board, -9 * G.smoothstep(top + 4, S, Y))                            # face turns away from the light
    img = G.add_lum(img, -22 * np.exp(-((Y - (top - 0.6)) / 1.0) ** 2) * (Y < top))       # hairline shadow on the paper
    img = G.over(img, board, G.box((S, S), -1, top, S + 1, S + 1))
    img = G.add_lum(img, 26 * np.exp(-((Y - (top + 1.5)) / 1.3) ** 2) * (Y > top), (1.0, 1.0, 1.04))   # rounded top edge
    img = G.add_lum(img, -9 * np.exp(-((Y - (top + 4.2)) / 0.9) ** 2))                    # under the round-over
    dust = np.exp(-((Y - (top + 0.6)) / 0.8) ** 2) * (0.6 + 0.4 * np.clip(G.fnoise(rng, beta=1.0, aniso=(6, 1), kmin=4), -1, 1))
    img = G.over(img, np.array([150, 144, 128]), 0.18 * dust)                              # dust on the top edge
    return G.add_lum(img, -12 * G.box((S, S), -1, S - 1.5, S + 1, S + 1))                 # where it meets the carpet


def _rising_damp(img, rng) -> np.ndarray:
    """Water wicked up from the wet carpet and dried: the paper below a wavy tide line is
    darker, browner and blotchy, the board's paint a little darker. Strongest in the middle of
    the block and faint at its sides, so neighbouring damp blocks join into one ragged line."""
    X, Y = G.grid()
    h = rng.uniform(68, 84) + 5 * _wobble(rng, S, 1.0, 7.0)[None, :] + 0.8 * _wobble(rng, S, 1.0, 2.5)[None, :]
    env = G.smoothstep(0, 46, np.minimum(X, S - X)) * (0.8 + 0.2 * np.clip(G.fnoise(rng, beta=2.0, kmin=1), -1, 1))
    below = G.smoothstep(h - 3, h + 8, Y)
    blot = 0.55 + 0.45 * G.smoothstep(-0.8, 1.2, G.fnoise(rng, beta=2.6, kmin=2))
    img = G.over(img, np.array(STAIN) * 0.9, 0.15 * below * blot * env)
    img = _sat(img, 0.08 * below * env)
    line = np.exp(-((Y - h) / 1.7) ** 2) + 0.45 * np.exp(-((Y - h - 8 - 2 * _wobble(rng, S, 1.0, 6.0)[None, :]) / 1.5) ** 2)
    return G.over(img, np.array(STAIN) * 0.8, 0.2 * line * env * (Y < BOARD_TOP))


def _kick_marks(img, rng) -> np.ndarray:
    """Black rubber scuffs from shoes and carts on the board and the paper just above it, and a
    few chips knocked out of the board's paint along its top edge."""
    X, Y = G.grid()
    marks = []
    for _ in range(int(rng.integers(6, 11))):
        x0 = rng.uniform(14, 114)
        y0 = rng.uniform(BOARD_TOP - 14, S - 4)
        L, a = rng.uniform(4, 14), rng.uniform(-0.35, 0.35)
        marks.append([(x0, y0), (x0 + L * np.cos(a), y0 + L * np.sin(a) + rng.uniform(-1.5, 1.5))])
    m = G.pil_mask(lambda d, k: [d.line([(p * k, q * k) for p, q in s], fill=int(rng.uniform(90, 255)),
                                        width=max(1, int(k * rng.uniform(0.7, 1.6)))) for s in marks])
    img = G.over(img, np.array([44, 40, 36]), 0.5 * G.blur(m, 0.35))
    chips = np.zeros((S, S))
    for _ in range(int(rng.integers(2, 5))):
        chips = np.maximum(chips, G._local(rng.uniform(16, 112), BOARD_TOP + 1.5, rng.uniform(1.2, 3.0), 1.0))
    return G.over(img, np.array([132, 104, 70]), 0.6 * G.smoothstep(0.4, 0.7, chips))


def _curled_seam(img, rng) -> np.ndarray:
    """The paper seam lifting off right above the board: a curled triangular corner showing the
    paler paper back and its shadow, yellowed paste along the seam fading upwards, and grime
    collected in the gap."""
    X, Y = G.grid()
    top = BOARD_TOP
    s = 1 if rng.random() < 0.5 else -1                                   # which strip curls
    hgt, wid = rng.uniform(14, 20), rng.uniform(5, 8)
    u = (X - 64) * s
    flap = (u > 0) & (u < wid * (1 - (top - Y) / hgt)) & (Y < top) & (Y > top - hgt)
    flap = G.blur(flap.astype(float), 0.5)
    shadow = G.blur(((u > -1) & (u < wid * 1.4 * (1 - (top - Y) / (hgt * 1.2))) & (Y < top) & (Y > top - hgt * 1.2)).astype(float), 1.2)
    img = G.over(img, np.array([70, 60, 44]), 0.4 * shadow * (1 - flap))
    back = G.add_lum(G.fill((192, 180, 142), (S, S)), 1.5 * G.fnoise(rng, beta=1.0, kmin=8))
    back = G.add_lum(back, -22 * G.smoothstep(0, wid, u) * flap)          # the curl turns away from the light
    img = G.over(img, back, flap)
    paste = np.exp(-((X - 64) / 1.6) ** 2) * G.smoothstep(top - 70, top - 30, Y) * (Y < top)
    img = G.over(img, np.array([168, 138, 70]), 0.18 * paste)
    return G.over(img, np.array(GRIME) * 0.7, 0.25 * np.exp(-((Y - top + 0.8) / 1.2) ** 2) * G._local(64, top, 26, 6))


def baseboard_marks(i, rng, img):
    """Versions 1-3 of the baseboard row (drawn on top of the plain paper + board)."""
    if i == 1:
        return _rising_damp(img, rng)
    if i == 2:
        return _kick_marks(img, rng)
    return _curled_seam(img, rng)


def baseboard_plain() -> np.ndarray:
    return baseboard(wallpaper())


# =============================================================================
# Carpet
# =============================================================================

def carpet(seed=102) -> np.ndarray:
    """The Lobby carpet: short dense cut pile in a mottled tan/mustard heather. At 128 px per
    metre a tuft is smaller than a pixel, so the pile shows as a fine speckle of slightly
    different fibre shades and hues (some browner, some yellower), a nubby mid-scale texture,
    soft cloudy mottling where the pile lies differently and a few dark fibre flecks.
    Calibrated to CARPET_MEAN. It looks the same flipped either way, so the blockstate can flip
    it at random."""
    rng = G.rng_for(seed)
    speck = G.blur(rng.normal(0, 1, (S, S)), 0.55)
    speck /= speck.std()
    flecks = G.blur((rng.random((S, S)) < 0.018).astype(float), 0.4)
    lum = (4.2 * speck
           + 2.0 * G.fnoise(rng, beta=1.0, kmin=12)                      # nubby pile
           + 1.5 * G.fnoise(rng, beta=2.4, kmin=2, kmax=12)              # cloudy mottling (kept low: the
                                                                         # blockstate mirrors the carpet)
           + 1.2 * G.fnoise(rng, beta=3.0, kmin=1, kmax=2)               # the pile lying differently
           - 22 * flecks)
    img = G.add_lum(G.fill(CARPET_MEAN, (S, S)), lum - lum.mean(), (1.0, 0.92, 0.72))
    hue = G.blur(rng.normal(0, 1, (S, S)), 0.6)
    hue = 0.035 * hue / hue.std() + 0.02 * G.fnoise(rng, beta=2.0, kmin=3, kmax=12)   # heather
    img = img * (1 + hue[..., None] * np.array([0.6, 0.1, -1.4]))
    return _calibrate(img, CARPET_MEAN)


def _flattened(img, rng) -> np.ndarray:
    """A traffic-worn patch: the pile crushed flat (tufts barely show), a little greyer with
    trodden-in dirt in the middle and a faint sheen. It covers most of the block and fades out
    towards its edges, so a cluster of these reads as a worn path."""
    m = G._local(64 + rng.uniform(-10, 10), 64 + rng.uniform(-10, 10), rng.uniform(30, 40), rng.uniform(30, 40))
    m = np.clip(m * (1.1 + 0.35 * G.fnoise(rng, beta=2.2, kmin=1)), 0, 1) * G._edge_fade(26)
    streak = 0.5 + 0.5 * np.clip(G.fnoise(rng, beta=1.4, aniso=(1, 6), kmin=3), -1, 1)
    flat = G.blur(img, 0.9)
    out = G.lerp(img, flat, (0.75 * m)[..., None])
    out = _sat(out, -0.18 * m)
    out = G.add_lum(out, -6 * m * G._local(64, 64, 26, 26) + 2.5 * m * streak)
    return out


def _damp(img, rng) -> np.ndarray:
    """Damp patch: one irregular soft blotch (a few overlapping lobes, off the middle of the
    block) where the pile is wet, darker and a little browner, the fibres matted, with a faint
    pale edge where an older patch dried. The blockstate's flips put it in four places."""
    m = np.zeros((S, S))
    ox, oy = rng.choice([-1, 1]) * rng.uniform(8, 14), rng.choice([-1, 1]) * rng.uniform(8, 14)
    for _ in range(int(rng.integers(2, 4))):
        cx, cy = 64 + ox + rng.uniform(-10, 10), 64 + oy + rng.uniform(-10, 10)
        m = np.maximum(m, G._local(cx, cy, rng.uniform(12, 20), rng.uniform(10, 18)))
    m = G.smoothstep(0.1, 0.85, m * (1 + 0.3 * G.fnoise(rng, beta=2.8, kmin=1))) * G._edge_fade(22)
    out = G.lerp(img, G.blur(img, 0.6), (0.5 * m)[..., None])
    out = out * (1 - 0.13 * m)[..., None]
    out = _sat(out, 0.06 * m)
    ring = np.exp(-((m - 0.1) / 0.05) ** 2) * G._edge_fade(22)
    return G.add_lum(out, 2.5 * ring, (1.0, 1.0, 1.1))


def _stains(img, rng) -> np.ndarray:
    """Old spills: one coffee-dark stain with a darker dried rim, a couple of small dark spots
    and the pile around them a little flattened."""
    X, Y = G.grid()
    cx, cy = 64 + rng.uniform(-18, 18), 64 + rng.uniform(-18, 18)
    m = G._local(cx, cy, rng.uniform(8, 12), rng.uniform(7, 10)) * (1 + 0.35 * G.fnoise(rng, beta=2.4, kmin=2))
    body = G.smoothstep(0.35, 0.65, m)
    rim = np.clip(G.smoothstep(0.28, 0.36, m) - G.smoothstep(0.36, 0.5, m), 0, 1)
    out = G.lerp(img, G.blur(img, 0.7), G._local(cx, cy, 22, 20)[..., None] * 0.5)
    out = G.over(out, np.array([92, 68, 38]), 0.24 * body + 0.32 * rim)
    for _ in range(int(rng.integers(2, 4))):
        sx, sy = G._spot(rng, 4, 4)
        r = rng.uniform(1.4, 2.6)
        spot = G.smoothstep(r, r * 0.5, np.hypot(X - sx, Y - sy) * (1 + 0.25 * G.fnoise(rng, beta=1.5, kmin=8)))
        out = G.over(out, np.array([78, 58, 34]), 0.3 * spot)
    return out


def carpet_marks(i, rng, img):
    """Versions 1-3 of the carpet."""
    if i == 1:
        return _flattened(img, rng)
    if i == 2:
        return _damp(img, rng)
    return _stains(img, rng)


# =============================================================================
# Ceiling tiles
# =============================================================================
# One 1.2 m acoustic tile per block in a thin painted T-bar grid along the block edges. The grid
# is lit evenly (no lit side), so the blockstate can turn and flip the tile in all 8 ways.

BAR = 2.2                     # px of T-bar on each side of the block edge (twice that between tiles)


def _edge_dist() -> np.ndarray:
    X, Y = G.grid()
    return np.minimum(np.minimum(X, S - X), np.minimum(Y, S - Y))


def _fissures(rng, n=1400) -> np.ndarray:
    """Fissured mineral fibre: dense short irregular grooves of random length and depth."""
    def draw(d, k):
        for _ in range(n):
            x, y, a = rng.uniform(0, S), rng.uniform(0, S), rng.uniform(0, 2 * np.pi)
            pts = [(x, y)]
            for _ in range(int(rng.integers(1, 4))):
                a += rng.normal(0, 0.9)
                step = rng.uniform(0.7, 1.6)
                x, y = x + np.cos(a) * step, y + np.sin(a) * step
                pts.append((x, y))
            d.line([(p * k, q * k) for p, q in pts], fill=int(rng.uniform(60, 255)), width=max(1, int(k * rng.uniform(0.5, 1.0))))
    return G.pil_mask(draw)


def _tbar(img) -> np.ndarray:
    """The painted T-bar along the block edges, and the shadowed reveal where the tile sits up
    in it (the same all round)."""
    d = _edge_dist()
    img = G.add_lum(img, -16 * np.exp(-((d - BAR - 0.7) / 0.8) ** 2) + 4 * np.exp(-((d - BAR - 2.6) / 1.0) ** 2))
    img = G.add_lum(img, -5 * np.exp(-(d - BAR) / 6.0) * (d > BAR))
    rng = G.rng_for(131)
    bar = G.add_lum(G.fill(TBAR, (S, S)), 1.2 * G.fnoise(rng, beta=1.5, kmin=4) - 3 * np.exp(-((d - BAR + 0.4) / 0.5) ** 2))
    return G.over(img, bar, G.smoothstep(BAR + 0.6, BAR - 0.2, d))


def ceiling_tile(stain=False, seed=103) -> np.ndarray:
    """The plain tile: warm off-white mineral fibre, dense fine fissures and pin-holes, a faint
    uneven yellowing, in the T-bar grid. Calibrated so the whole texture averages TILE_MEAN.
    stain=True adds a faint water ring (for blocks derived from the tile)."""
    rng = G.rng_for(seed)
    fiss = _fissures(rng)
    pits = G.blur((rng.random((S, S)) < 0.03) * rng.uniform(0.3, 1, (S, S)), 0.45)
    holes = G.blur((rng.random((S, S)) < 0.004).astype(float), 0.8)          # a few bigger pin-holes
    lum = (-17 * fiss - 20 * pits - 40 * holes
           + 2.2 * G.fnoise(rng, beta=1.2, kmin=10)                          # the bumpy pressed surface
           + 1.2 * G.blur(rng.normal(0, 1, (S, S)), 0.5)
           + 1.5 * G.fnoise(rng, beta=3.0, kmin=1, kmax=4))
    img = G.add_lum(G.fill(TILE_MEAN, (S, S)), lum - lum.mean(), (1.0, 0.98, 0.93))
    yellow = G.smoothstep(-0.5, 1.5, G.fnoise(rng, beta=3.2, kmin=1, kmax=3))
    img = img * (1 - yellow[..., None] * np.array([0.0, 0.012, 0.04]))    # faint uneven yellowing
    img = _calibrate(_tbar(img), TILE_MEAN)
    if stain:
        img = _water_rings(img, G.rng_for(seed + 1), scale=0.6)
    return img


def _tile_area() -> np.ndarray:
    return G.smoothstep(BAR, BAR + 1.0, _edge_dist())


def _water_rings(img, rng, scale=1.0) -> np.ndarray:
    """A leak from above dried on the tile: a brownish stain with several nested irregular tide
    rings (each leak left one), sometimes a smaller second one. A stain may run up to the
    T-bar: the tile simply ends there."""
    X, Y = G.grid()
    out = img
    for k in range(1 if rng.random() < 0.6 else 2):
        r = rng.uniform(24, 36) * scale * (1.0 if k == 0 else 0.5)
        cx, cy = rng.uniform(34, 94), rng.uniform(34, 94)
        ang = np.arctan2(Y - cy, X - cx)
        wob = 1 + 0.08 * np.sin(2 * ang + rng.uniform(0, 6)) + 0.05 * np.sin(5 * ang + rng.uniform(0, 6))
        rr = np.hypot(X - cx, Y - cy) / (r * wob) * (1 + 0.07 * G.fnoise(rng, beta=2.2, kmin=2))
        fill = G.smoothstep(1.02, 0.5, rr)
        out = G.over(out, np.array([186, 160, 108]), 0.18 * fill * _tile_area())
        for lvl, a in ((1.0, 0.32), (0.8, 0.14), (0.62, 0.1)):
            ring = np.exp(-((rr - lvl) / 0.03) ** 2) + 0.4 * np.exp(-((rr - lvl + 0.05) / 0.06) ** 2)
            out = G.over(out, np.array([152, 116, 64]), a * np.clip(ring, 0, 1) * _tile_area())
    return out


def _sagged(img, rng) -> np.ndarray:
    """A tile that has taken on water and sagged: yellower and dirtier all over, the middle
    dropping into shadow, a soft crease near the edge where it bends, and a dark gap opening
    under the T-bar along the middle of each side where it has pulled away."""
    X, Y = G.grid()
    d = _edge_dist()
    area = _tile_area()
    tint = 0.55 + 0.45 * G.smoothstep(-1.0, 1.2, G.fnoise(rng, beta=3.0, kmin=1, kmax=4))
    out = img * (1 - (area * tint)[..., None] * np.array([0.01, 0.025, 0.07]))   # yellowed, unevenly
    dirt = G.smoothstep(-0.6, 1.6, G.fnoise(rng, beta=2.6, kmin=1, kmax=6))
    out = G.over(out, np.array([170, 150, 112]), 0.09 * dirt * area)
    cx, cy = rng.uniform(56, 72), rng.uniform(56, 72)
    r = np.hypot((X - cx) / 62, (Y - cy) / 58)
    out = G.add_lum(out, -12 * np.clip(1 - r, 0, 1) ** 1.6 * area)
    out = G.add_lum(out, 4 * np.exp(-((d - 13) / 4) ** 2) * area)
    mid = np.maximum(np.exp(-((X - 64) / 22) ** 2) * (np.minimum(Y, S - Y) < 20), np.exp(-((Y - 64) / 22) ** 2) * (np.minimum(X, S - X) < 20))
    return G.over(out, np.array(PLENUM), 0.45 * mid * np.exp(-((d - BAR - 0.5) / 0.6) ** 2))


def _broken(img, rng) -> np.ndarray:
    """A damaged tile: a corner broken off (the dark plenum shows through the hole, the broken
    edge pale where the fibre core shows), and a crack running in from it with a branch."""
    X, Y = G.grid()
    corner = int(rng.integers(0, 4))
    fx, fy = (corner & 1), (corner >> 1)
    ux = np.where(fx, S - X, X)                                             # distances from that corner
    uy = np.where(fy, S - Y, Y)
    size = rng.uniform(22, 30)
    q = ux + uy * rng.uniform(0.7, 1.3)                                     # the break runs across the corner
    chip_edge = size * (1 + 0.12 * G.fnoise(rng, beta=2.6, kmin=2))
    chip = G.smoothstep(chip_edge, chip_edge - 1.5, q) * _tile_area()
    core = G.smoothstep(chip_edge + 2.5, chip_edge, q) * (1 - chip) * _tile_area()
    out = G.add_lum(img, 14 * core, (1.0, 1.0, 1.04))
    out = G.over(out, np.array(PLENUM), chip)
    pts = [(size * 0.55 if not fx else S - size * 0.55, size * 0.55 if not fy else S - size * 0.55)]
    a = np.arctan2((1 - 2 * fy), (1 - 2 * fx)) + rng.normal(0, 0.25)
    x, y = pts[0]
    for _ in range(9):
        a += rng.normal(0, 0.35)
        x, y = x + np.cos(a) * 6.5, y + np.sin(a) * 6.5
        pts.append((x, y))
    bx, by = pts[4]
    b = a + rng.choice([-1, 1]) * 0.9
    branch = [(bx, by)] + [(bx + np.cos(b + rng.normal(0, 0.3)) * 6 * t, by + np.sin(b + rng.normal(0, 0.3)) * 6 * t) for t in (1, 2, 3)]
    crack = G.pil_mask(lambda d, k: (d.line([(p * k, q * k) for p, q in pts], fill=255, width=max(1, int(1.3 * k))),
                                     d.line([(p * k, q * k) for p, q in branch], fill=220, width=max(1, int(0.9 * k)))))
    out = G.add_lum(out, (-60 * crack + 10 * np.roll(crack, 1, axis=1)) * _tile_area())
    dirt = G._local(*pts[0], 28, 28) * _tile_area()
    return G.over(out, np.array([168, 150, 116]), 0.12 * dirt)


def ceiling_marks(i, rng, img):
    """Versions 1-3 of the ceiling tile."""
    if i == 1:
        return _water_rings(img, rng)
    if i == 2:
        return _sagged(img, rng)
    return _broken(img, rng)


# =============================================================================
# Generator
# =============================================================================

# The SurfaceBlocks drawn here (their entries in gen_assets.SURFACE_VARIANTS).
SURFACES = ("yellow_wallpaper", "yellow_wallpaper_baseboard", "lobby_carpet", "ceiling_tile",
            "l0_wallpaper_chevron", "l0_wallpaper_chevron_baseboard")


def tex_level0_surfaces() -> None:
    """The plain textures, every aged version, and their models and blockstates (the chevron
    paper's plain texture comes from level0_revamp.py, which also uses baseboard() here)."""
    G.tex_yellow_wallpaper()
    G.tex_yellow_wallpaper_baseboard()
    G.tex_lobby_carpet()
    G.tex_ceiling_tile()
    G.save_png(baseboard(G._l0w().chevron_base()), G._blk("l0_wallpaper_chevron_baseboard"))
    for name in SURFACES:
        G.save_surface_variants(name)
        G.write_variant_states(name, G.SURFACE_VARIANTS[name][2])


TEXTURES = [tex_level0_surfaces]
