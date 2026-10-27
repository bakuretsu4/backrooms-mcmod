"""Chalk (registry/ChalkRegistry.java): the chalk stick's icon, the marks players draw with it
(block/ChalkMarkBlock.java) with their models and blockstate, and the two chalk sounds.

The marks are white classroom chalk drawn by hand: single strokes that start heavy where the
chalk touches down and thin out as it lifts, broken up where the chalk skipped over the bumps of
the wall, with a faint dusty smudge around them. Each symbol has three versions (a different
hand each time); the blockstate lists all three and Minecraft picks one from the block position,
so a row of marks never looks stamped. They are meant to look nothing like Level 0's own
painted arrows (thick, dark brown, solid), so players can tell their marks from the level's lies.

  textures/block/chalk_mark_arrow_<v>_<r>.png   arrow, version v (0..2), pointing r quarter turns
                                                clockwise from up (0 up, 1 right, 2 down, 3 left)
  textures/block/chalk_mark_cross_<v>.png       a big X
  textures/block/chalk_mark_circle_<v>.png      a ring drawn in one go, ends overlapping
  textures/block/chalk_mark_dust.png            break particles (chalk dust)
  textures/item/chalk.png                       the stick
  sounds/chalk/draw1..3.ogg                     a quick scratch of chalk on a wall (-26 LUFS)
  sounds/chalk/wipe1..2.ogg                     a sleeve brushing a mark off (-30 LUFS)

Textures are 64 x 64 (half the world textures' 128: a chalk line doesn't need more, and it keeps
the 18 drawings small on the block atlas).

Models: one flat plane per texture, 0.1 px in front of the wall, for a mark facing north (on the
wall to its south). The blockstate turns it: y for the other walls, x 270 onto the floor, x 90 onto
the ceiling. Seen from above the floor plane shows the texture's top towards the south; seen from
below the ceiling plane shows it towards the north (ChalkMarkBlock.rotationOnFloor/OnCeiling
rely on this).

Run with `python tools/gen_assets.py chalk` from the project root. Every generator uses its own
fixed random seed (61500-61599), so re-running gives identical files.
"""
from __future__ import annotations

import numpy as np

import gen_assets as G
from anomaly_assets import level2_audio as A
from anomaly_assets import level39_props as P

T = 64                         # texture size of the marks
CHALK = (238, 236, 228)        # classroom chalk: white with the faintest warm cast
SR = G.SR


def _blk(name: str):
    return G.TEX / "block" / f"{name}.png"


def _ref(name: str) -> str:
    return f"backrooms:block/{name}"


# =============================================================================
# Drawing with chalk
# =============================================================================

def _resample(pts, spacing=0.5):
    """Points every `spacing` px along a polyline (so round stamps along it make a smooth line)."""
    pts = np.asarray(pts, float)
    seg = np.hypot(*np.diff(pts, axis=0).T)
    s = np.concatenate([[0], np.cumsum(seg)])
    n = max(2, int(s[-1] / spacing) + 1)
    u = np.linspace(0, s[-1], n)
    return np.stack([np.interp(u, s, pts[:, 0]), np.interp(u, s, pts[:, 1])], axis=1)


def _smooth_path(rng, pts, jitter=0.35):
    """A hand's path through the given points: each corner a little rounded, the line a little
    unsteady (a slow sideways wander rather than per-point noise, which would look like a tremor)."""
    p = _resample(pts, 0.5)
    k = np.exp(-0.5 * (np.arange(-4, 5) / 2.0) ** 2)
    k /= k.sum()
    pad = np.concatenate([np.repeat(p[:1], 4, 0), p, np.repeat(p[-1:], 4, 0)])
    p = np.stack([np.convolve(pad[:, i], k, mode="valid") for i in (0, 1)], axis=1)
    n = len(p)
    wander = np.cumsum(rng.normal(0, 1, (n, 2)), axis=0)
    wander -= np.linspace(0, 1, n)[:, None] * wander[-1]          # ends stay where they were aimed
    wander = wander / (np.abs(wander).max() + 1e-9) * jitter * rng.uniform(1.0, 2.0)
    return p + wander


def _stroke_maps(rng, strokes, width, ss=4):
    """Shape coverage C, chalk pressure P and striation S (all T x T, 0..1) of hand strokes.

    A stroke starts heavy where the chalk lands, gets lighter towards the end as the hand lifts,
    and wavers a little in between; the line is a touch narrower where the pressure is light.
    The worn end of the stick isn't flat, so it leaves a few fine streaks running along the
    stroke: S is a pattern that varies ACROSS the line and stays the same along it."""
    n = T * ss
    yy, xx = np.mgrid[0:n, 0:n]
    X, Y = (xx + 0.5) / ss, (yy + 0.5) / ss
    cov = np.zeros((n, n))
    prs = np.zeros((n, n))
    stri = np.ones((n, n))
    for st in strokes:
        path = _smooth_path(rng, st)
        t = np.linspace(0, 1, len(path))
        p = ((0.78 + 0.22 * G.smoothstep(0.0, 0.12, t)) * (1 - 0.5 * G.smoothstep(0.7, 1.0, t))
             * (1 + 0.08 * np.sin(2 * np.pi * (t * rng.uniform(1.5, 3.0) + rng.uniform(0, 1)))))
        w = width * rng.uniform(0.9, 1.1)
        tang = np.gradient(path, axis=0)
        tang /= np.maximum(np.hypot(tang[:, 0], tang[:, 1]), 1e-9)[:, None]
        k1, k2 = rng.uniform(0.35, 0.55), rng.uniform(0.8, 1.2)
        f1, f2 = rng.uniform(0, 2 * np.pi, 2)
        for i in range(0, len(path), 2):
            px, py = path[i]
            r = w / 2 * (0.75 + 0.3 * p[i])
            x0, x1 = int(max(0, (px - r - 1) * ss)), int(min(n, (px + r + 1) * ss + 1))
            y0, y1 = int(max(0, (py - r - 1) * ss)), int(min(n, (py + r + 1) * ss + 1))
            if x0 >= x1 or y0 >= y1:
                continue
            dx, dy = X[y0:y1, x0:x1] - px, Y[y0:y1, x0:x1] - py
            e = np.clip((r - np.hypot(dx, dy)) * ss / 1.5 + 0.5, 0, 1)
            across = -dx * tang[i, 1] + dy * tang[i, 0]
            s = 0.5 + 0.3 * np.sin(2 * np.pi * k1 * across + f1) + 0.2 * np.sin(2 * np.pi * k2 * across + f2)
            win = e > cov[y0:y1, x0:x1]
            stri[y0:y1, x0:x1] = np.where(win, s, stri[y0:y1, x0:x1])
            cov[y0:y1, x0:x1] = np.maximum(cov[y0:y1, x0:x1], e)
            prs[y0:y1, x0:x1] = np.maximum(prs[y0:y1, x0:x1], e * p[i])
    down = lambda a: a.reshape(T, ss, T, ss).mean(axis=(1, 3))
    C = down(cov)
    return C, np.clip(down(prs) / np.maximum(C, 1e-6), 0, 1), down(stri * cov) / np.maximum(C, 1e-6)


SMUDGE = (112, 110, 104)       # chalk dust rubbed into the grime of the wall


def _chalk(rng, strokes, width=4.8) -> np.ndarray:
    """RGBA chalk drawing (T x T) of the strokes on a clear background."""
    C, Pm, Sm = _stroke_maps(rng, strokes, width)
    # The chalk skips over the bumps of the wall: fine grain cuts holes in the line, more of them
    # where the hand pressed lightly, the streaks thin it along its length, and a coarser
    # patchiness makes some stretches fainter.
    fine = G.fnoise(rng, (T, T), beta=0.3, kmin=12)
    coarse = G.fnoise(rng, (T, T), beta=2.0, kmin=2)
    dens = np.clip(1.3 * Pm - 0.12 - 0.3 * np.maximum(fine, 0) - 0.08 * coarse - 0.35 * (1 - Sm), 0, 1)
    a_chalk = np.clip(C * dens * 0.97, 0, 1)
    # Around the line a soft smudge: a thin haze of white dust right next to it, and further out a
    # faint grey rim where the dust got rubbed into the wall's dirt. The rim is what still shows
    # the mark on white tile or pale ceiling, where white on white would vanish.
    near = G.blur(C, 1.4)
    dust = np.clip(G.blur(C * Pm, 1.2) * 0.35, 0, 1) * (1 - C)
    rim = np.clip(G.blur(C, 2.0) * 1.6 - near * 0.5, 0, 1) * (1 - C) * 0.48
    specks = (rng.random((T, T)) < 0.05) * G.smoothstep(0.02, 0.15, G.blur(C, 3.0)) * (1 - C) * 0.35
    white = 1 - (1 - a_chalk) * (1 - dust) * (1 - specks)
    alpha = white + rim * (1 - white)
    w = white / np.maximum(alpha, 1e-6)
    col = G.lerp(np.array(SMUDGE, float), np.array(CHALK, float), w[..., None]) + rng.normal(0, 4, (T, T, 1))
    return np.concatenate([col, (np.clip(alpha, 0, 1) * 255)[..., None]], axis=2)


def _turn(strokes, deg, scale=1.0, dx=0.0, dy=0.0):
    """Turn (degrees, clockwise on screen), scale and shift strokes about the texture centre."""
    a = np.radians(deg)
    c, s = np.cos(a), np.sin(a)
    out = []
    for st in strokes:
        out.append([(T / 2 + scale * ((x - T / 2) * c - (y - T / 2) * s) + dx,
                     T / 2 + scale * ((x - T / 2) * s + (y - T / 2) * c) + dy) for x, y in st])
    return out


# The three hands. Coordinates in texture pixels (0..64, y down), every arrow pointing UP.
ARROWS = [
    # careful: a straight shaft, the head one "V" drawn in a single stroke
    [[(32, 55), (32, 11)], [(20, 24), (32, 11), (44, 24)]],
    # hurried: the shaft bowed, the head two separate flicks overshooting the tip, the right one
    # shorter and steeper
    [[(28.5, 56), (31.5, 36), (33.5, 12)], [(19, 21), (34.5, 8.5)], [(33, 10), (42, 26)]],
    # a different hand: a short fat arrow, the head a wide flat "V" gone over twice, a little
    # tick at the tail where the chalk landed
    [[(33, 50), (31.5, 17)], [(16, 31), (31.5, 15), (48, 30)], [(18, 30.5), (31, 16.5), (46, 29)],
     [(30, 53), (33.5, 49.5)]],
]
CROSSES = [
    [[(16, 15), (49, 49)], [(48, 15), (15, 49)]],
    [[(19, 12), (34, 31), (45, 52)], [(51, 21), (13, 43)]],                          # one leg bent, flat
    [[(14, 18), (50, 46)], [(46, 12), (18, 52)], [(47.5, 14.5), (20, 51)]],          # second stroke doubled
]


def _ring(cx, cy, rx, ry, a0, sweep, wobble, rng, n=40):
    t = np.linspace(a0, a0 + sweep, n)
    k = 1 + wobble * np.sin(t * 2 + rng.uniform(0, 2 * np.pi))
    return [(cx + rx * k[i] * np.cos(t[i]), cy + ry * k[i] * np.sin(t[i])) for i in range(n)]


def _circles(rng):
    return [
        [_ring(32, 32, 17.5, 17, -2.0, 2 * np.pi + 0.55, 0.04, rng)],              # ends overlap
        [_ring(32.5, 31, 18, 16, 2.4, 2 * np.pi + 0.25, 0.06, rng)],               # a bit squashed
        [_ring(31.5, 32.5, 16.5, 18, -1.1, 2 * np.pi - 0.35, 0.05, rng)],          # not quite closed
    ]


def tex_chalk_marks() -> None:
    """The 18 mark textures (see the module docstring)."""
    for v, strokes in enumerate(ARROWS):
        rng = G.rng_for(61500 + v)
        img = _chalk(rng, _turn(strokes, rng.normal(0, 4), rng.uniform(0.94, 1.04), rng.normal(0, 1.2), rng.normal(0, 1.2)))
        for r in range(4):
            # np.rot90 turns anticlockwise; r counts clockwise quarter turns
            G.save_png(np.rot90(img, -r), _blk(f"chalk_mark_arrow_{v}_{r}"))
    for v, strokes in enumerate(CROSSES):
        rng = G.rng_for(61510 + v)
        G.save_png(_chalk(rng, _turn(strokes, rng.normal(0, 5), 1.0, rng.normal(0, 1.2), rng.normal(0, 1.2))),
                   _blk(f"chalk_mark_cross_{v}"))
    rng = G.rng_for(61520)
    for v, strokes in enumerate(_circles(rng)):
        G.save_png(_chalk(G.rng_for(61521 + v), strokes), _blk(f"chalk_mark_circle_{v}"))

    # Break particles: a patch of solid chalk dust (pieces of it fly off when you wipe a mark).
    rng = G.rng_for(61530)
    dust = G.add_lum(G.fill(CHALK, (16, 16)), 6 * rng.normal(0, 1, (16, 16)))
    alpha = np.where(rng.random((16, 16)) < 0.8, 230, 0)
    G.save_png(np.concatenate([dust, alpha[..., None]], axis=2), _blk("chalk_mark_dust"))


def tex_chalk_item() -> None:
    """The chalk stick lying diagonally (bottom left to top right): a white round stick, shaded
    on its lower side, the top end worn to a rounded point, a smear of dust under it."""
    rng = G.rng_for(61540)
    X, Y = G.grid((16, 16))
    u = ((X - 8) - (Y - 8)) / np.sqrt(2)          # along the stick (bottom-left -> top-right)
    v = ((X - 8) + (Y - 8)) / np.sqrt(2)          # across it (+ = lower right side)
    half = np.where(u > 3.6, 1.9 - 0.55 * (u - 3.6), 1.9)   # worn tip narrows
    inside = (u >= -6.4) & (u <= 6.4) & (np.abs(v) <= half)
    shade = 1.0 - 0.16 * np.clip(v / 1.9, -1, 1) - 0.1 * (np.abs(v) > half - 0.6)
    col = np.array(CHALK, float) * shade[..., None] + rng.normal(0, 3, (16, 16, 1))
    col = np.where(((u < -5.6) & inside)[..., None], col * 0.88, col)        # flat broken end
    img = np.zeros((16, 16, 4))
    img[..., :3] = np.clip(col, 0, 255)
    img[..., 3] = np.where(inside, 255, 0)
    smear = (~inside) & (np.abs(v - 2.9) < 0.7) & (u > -4) & (u < 2) & (rng.random((16, 16)) < 0.6)
    img[smear] = (226, 224, 216, 90)
    G.save_png(img, G.TEX / "item" / "chalk.png")


# =============================================================================
# Models and blockstate (LF JSON)
# =============================================================================

SYMBOL_TEXTURES = {
    "arrow": lambda v, r: f"chalk_mark_arrow_{v}_{r}",
    "cross": lambda v, r: f"chalk_mark_cross_{v}",
    "circle": lambda v, r: f"chalk_mark_circle_{v}",
}
# Blockstate turn for each FACING (the model is built facing north, the plane against z = 16).
FACING_ROT = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270},
              "up": {"x": 270}, "down": {"x": 90}}


def tex_chalk_models() -> None:
    """One flat-plane model per mark texture and the blockstate with three versions per state."""
    names = sorted({fn(v, r) for fn in SYMBOL_TEXTURES.values() for v in range(3) for r in range(4)})
    for name in names:
        tex = {"particle": _ref("chalk_mark_dust"), "chalk": _ref(name)}
        plane = P._el((0, 0, 15.9), (16, 16, 15.9), "#chalk", faces=("north",))
        P._bm(name, P._model(tex, [plane], render="minecraft:translucent"))
    variants = {}
    for facing, rot in FACING_ROT.items():
        for symbol, fn in SYMBOL_TEXTURES.items():
            for r in range(4):
                variants[f"facing={facing},rotation={r},symbol={symbol}"] = [
                    {"model": _ref(fn(v, r)), **rot} for v in range(3)]
    P._bs("chalk_mark", {"variants": variants})
    P._im("chalk", layer="backrooms:item/chalk")


TEXTURES = [tex_chalk_marks, tex_chalk_item, tex_chalk_models]


# =============================================================================
# Sounds
# =============================================================================

def _save(x, name: str, target: float) -> None:
    """Brings `x` to `target` LUFS (not peak-normalised) and writes chalk/<name>.ogg."""
    import soundfile as sf
    x = np.asarray(x, float)
    x = x - x.mean()
    fi, fo = int(0.002 * SR), int(0.03 * SR)
    x[:fi] *= np.linspace(0, 1, fi)
    x[len(x) - fo:] *= np.linspace(1, 0, fo)
    x = A.loud(x, target)
    path = G.SND / "chalk" / f"{name}.ogg"
    path.parent.mkdir(parents=True, exist_ok=True)
    sf.write(str(path), x.astype(np.float32), SR, format="OGG", subtype="VORBIS")


def _scratch(rng, dur):
    """One chalk stroke: the stick chatters over the wall (stick-slip, ~60-140 tiny catches a
    second), each catch a short gritty tick ringing in the wall's surface around 1.5-4 kHz."""
    n = int(dur * SR)
    buf = np.zeros(n)
    rate = rng.uniform(60, 140)
    t = rng.uniform(0, 0.004)
    while t < dur:
        i = int(t * SR)
        buf[i] += rng.uniform(0.4, 1.0)
        t += rng.exponential(1 / rate) * 0.6 + 0.4 / rate
    grain = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 1500, 5500, 2))
    tick = np.exp(-np.arange(int(0.003 * SR)) / (0.0008 * SR))
    chatter = G.conv(buf, tick)[:n] * grain
    hiss = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 2500, 7000, 2)) * 0.25
    env = G.env_adsr(n, 0.006, dur * 0.35) * (1 + G.smooth_jitter(rng, n, 25, 0.35))
    return (chatter + hiss) * env


def snd_chalk_draw() -> None:
    """Three takes of drawing a mark: two or three quick strokes, the first starting with the
    small tap of the chalk touching the wall."""
    for k in range(3):
        rng = G.rng_for(61560 + k)
        out = np.zeros(int(0.7 * SR))
        t = 0.0
        tap = G.fft_filter(rng.normal(size=int(0.012 * SR)), lambda f: G.lp_gain(f, 1800, 2))
        tap *= np.exp(-np.arange(tap.size) / (0.003 * SR))
        G.add_at(out, tap * 3.0, 0.0, circular=False)
        for s in range(rng.integers(2, 4)):
            d = rng.uniform(0.1, 0.19)
            G.add_at(out, _scratch(rng, d) * rng.uniform(0.7, 1.0), t + 0.004, circular=False)
            t += d + rng.uniform(0.03, 0.07)
        _save(out[:int((t + 0.03) * SR)], f"draw{k + 1}", -26.0)


def snd_chalk_wipe() -> None:
    """Two takes of a sleeve brushing a mark off: a soft, dull swish with a faint dusty grit."""
    for k in range(2):
        rng = G.rng_for(61570 + k)
        dur = rng.uniform(0.32, 0.42)
        n = int(dur * SR)
        t = np.arange(n) / SR
        env = np.exp(-0.5 * ((t - dur * 0.42) / (dur * 0.2)) ** 2)
        swish = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 250, 1300, 2))
        grit = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 2500, 6000, 2)) * 0.18
        x = (swish + grit) * env * (1 + G.smooth_jitter(rng, n, 30, 0.3))
        _save(x, f"wipe{k + 1}", -30.0)


SOUNDS = [snd_chalk_draw, snd_chalk_wipe]
