"""Procedural asset generator for the Backrooms mod.

Generates every binary texture (PNG) and sound (mono 44.1 kHz
OGG Vorbis) used by the mod. Fully deterministic: every asset uses its own fixed
RNG seed, so re-running produces identical output.

Requirements: numpy, Pillow, soundfile (libsndfile with OGG/Vorbis support).

Usage:  python tools/gen_assets.py [name_filter ...]
        (filters: only run generators whose function name contains one of them)
"""
from __future__ import annotations

from pathlib import Path

import numpy as np
import soundfile as sf
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1] / "src" / "main" / "resources" / "assets" / "backrooms"
TEX = ROOT / "textures"
SND = ROOT / "sounds"
SR = 44100


def rng_for(seed: int) -> np.random.Generator:
    return np.random.default_rng(seed)


# =============================================================================
# Image helpers
# =============================================================================

def blur_wrap(a: np.ndarray, r: int) -> np.ndarray:
    """Box blur with wrap-around edges (keeps textures seamless)."""
    for axis in (0, 1):
        a = sum(np.roll(a, d, axis=axis) for d in range(-r, r + 1)) / (2 * r + 1)
    return a


def fill(color, shape=(16, 16)) -> np.ndarray:
    return np.tile(np.array(color, dtype=float), (shape[0], shape[1], 1))


def lerp(a, b, t):
    a = np.asarray(a, float)
    b = np.asarray(b, float)
    return a + (b - a) * t


def save_png(img: np.ndarray, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if img.shape[2] == 3:
        img = np.concatenate([img, np.full(img.shape[:2] + (1,), 255.0)], axis=2)
    arr = np.clip(np.round(img), 0, 255).astype(np.uint8)
    Image.fromarray(arr, "RGBA").save(path)


# =============================================================================
# Block textures: 128x128 "realistic" style, albedos tuned so that, lit by the
# Veil lights in game, surfaces land on the colours measured in the reference
# videos (v2 contract "Target look numbers"). All tileable textures wrap seamlessly.
# =============================================================================

S = 128


def fnoise(rng, shape=(S, S), beta=2.0, aniso=(1.0, 1.0), kmin=1.0, kmax=None) -> np.ndarray:
    """Seamless fractal noise with power spectrum ~ 1/k^beta; zero mean, unit std.

    aniso scales x/y frequencies: (8, 1) gives horizontal streaks, (1, 8) vertical.
    """
    h, w = shape
    KY, KX = np.meshgrid(np.fft.fftfreq(h) * h, np.fft.fftfreq(w) * w, indexing="ij")
    k = np.hypot(KX * aniso[0], KY * aniso[1])
    band = k >= kmin
    if kmax is not None:
        band &= k <= kmax
    amp = np.zeros_like(k)
    amp[band] = k[band] ** (-beta / 2)
    spec = (rng.normal(size=k.shape) + 1j * rng.normal(size=k.shape)) * amp
    a = np.real(np.fft.ifft2(spec))
    return a / (a.std() + 1e-12)


def blur(a: np.ndarray, sigma: float) -> np.ndarray:
    """Periodic (wrap-around) gaussian blur of a 2D or HxWxC array."""
    if a.ndim == 3:
        return np.stack([blur(a[..., c], sigma) for c in range(a.shape[2])], axis=-1)
    h, w = a.shape
    ky = np.fft.fftfreq(h)[:, None]
    kx = np.fft.fftfreq(w)[None, :]
    g = np.exp(-2 * (np.pi * sigma) ** 2 * (kx ** 2 + ky ** 2))
    return np.real(np.fft.ifft2(np.fft.fft2(a) * g))


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def grid(shape=(S, S)):
    """Pixel-centre coordinates (X, Y)."""
    yy, xx = np.mgrid[0:shape[0], 0:shape[1]]
    return xx + 0.5, yy + 0.5


def wrapd(d, period):
    return (d + period / 2) % period - period / 2


def blob(shape, cx, cy, sx, sy) -> np.ndarray:
    """Wrap-around gaussian blob (seamless)."""
    X, Y = grid(shape)
    dx = wrapd(X - cx, shape[1])
    dy = wrapd(Y - cy, shape[0])
    return np.exp(-0.5 * ((dx / sx) ** 2 + (dy / sy) ** 2))


def box(shape, x0, y0, x1, y1, soft=0.5) -> np.ndarray:
    """Anti-aliased coverage of the rectangle [x0,x1) x [y0,y1) in pixel units."""
    X, Y = grid(shape)
    cx = np.clip(np.minimum(X - x0, x1 - X) / (2 * soft) + 0.5, 0, 1)
    cy = np.clip(np.minimum(Y - y0, y1 - Y) / (2 * soft) + 0.5, 0, 1)
    return cx * cy


def supersample(shape, inside, ss=4) -> np.ndarray:
    """Coverage of an arbitrary shape: inside(X, Y) -> bool evaluated at ss x ss subsamples."""
    h, w = shape
    yy, xx = np.mgrid[0:h * ss, 0:w * ss]
    m = inside((xx + 0.5) / ss, (yy + 0.5) / ss).astype(float)
    return m.reshape(h, ss, w, ss).mean(axis=(1, 3))


def add_lum(img, lum, tint=(1.0, 1.0, 1.0)) -> np.ndarray:
    return img + lum[..., None] * np.asarray(tint, float)


def over(img, color, alpha) -> np.ndarray:
    return lerp(img, color, np.asarray(alpha, float)[..., None])


def _smooth_concrete(rng, color, mottle=4.0, fine=0.8, shape=(S, S)) -> np.ndarray:
    img = fill(color, shape)
    lum = (fnoise(rng, shape, beta=3.0) * mottle
           + fnoise(rng, shape, beta=1.6, kmin=6) * mottle * 0.35
           + blur(fnoise(rng, shape, beta=0.5, kmin=16), 0.5) * fine)
    return add_lum(img, lum, (1.0, 1.0, 1.02))


def pil_mask(draw_fn, shape=(S, S), ss=4) -> np.ndarray:
    """Anti-aliased 0..1 mask drawn with PIL at ss x resolution.

    draw_fn(draw, k) draws in white; k is the scale factor (multiply pixel coords by k).
    """
    h, w = shape
    im = Image.new("L", (w * ss, h * ss), 0)
    draw_fn(ImageDraw.Draw(im), ss)
    return np.asarray(im.resize((w, h), Image.BOX), float) / 255.0


def font(px: int):
    """Bold system font (Windows Arial Bold) with Pillow's built-in font as a fallback."""
    for name in ("arialbd.ttf", "DejaVuSans-Bold.ttf"):
        try:
            return ImageFont.truetype(name, px)
        except OSError:
            pass
    return ImageFont.load_default(size=px)


def wobble_line(rng, pts, jitter=0.8, steps=8):
    """Hand-drawn polyline: subdivide each segment and jitter the points a little."""
    out = []
    for (x0, y0), (x1, y1) in zip(pts[:-1], pts[1:]):
        for i in range(steps):
            t = i / steps
            out.append((x0 + (x1 - x0) * t + rng.normal(0, jitter),
                        y0 + (y1 - y0) * t + rng.normal(0, jitter)))
    out.append(pts[-1])
    return out


def marker(img, rng, strokes, width=6.0, color=(26, 24, 22), alpha=0.92) -> np.ndarray:
    """Draw black felt-marker strokes (list of point lists) with slightly patchy ink."""
    lines = [wobble_line(rng, s) for s in strokes]

    def draw(d, k):
        for ln in lines:
            p = [(x * k, y * k) for x, y in ln]
            d.line(p, fill=255, width=int(width * k), joint="curve")
            r = width * k / 2
            for x, y in (p[0], p[-1]):
                d.ellipse((x - r, y - r, x + r, y + r), fill=255)

    m = pil_mask(draw, img.shape[:2])
    ink = alpha * (0.85 + 0.15 * np.clip(fnoise(rng, img.shape[:2], beta=1.0), -1, 1))
    return over(img, np.array(color), m * ink)


def tiled(img, n=3) -> np.ndarray:
    return np.tile(img, (n, n, 1))


def _pores(img, rng, density=0.012, depth=22.0) -> np.ndarray:
    """Small dark air-holes typical of cast concrete (per-pixel -> always seamless)."""
    p = (rng.random(img.shape[:2]) < density) * rng.uniform(0.4, 1.0, img.shape[:2])
    return add_lum(img, -blur(p, 0.6) * depth * 2.5)


# ---------------------------------------------------------------- Level 0 ---

WALLPAPER = (226, 206, 110)   # x Veil light (1.0, .94, .39) -> ~#CCB633 lit
CARPET = (198, 176, 112)      # -> ~#BCA03F lit
CEILING = (208, 202, 186)


def _wallpaper() -> np.ndarray:
    """Lemon wallpaper: faint large damask lattice, paper mottling, a few water stains."""
    rng = rng_for(101)
    img = fill(WALLPAPER, (S, S))
    X, Y = grid()
    # damask: a diamond lattice (period 64 px = 2 motifs per block) with a leaf motif inside
    u = wrapd(X - 32, 64) / 32
    v = wrapd(Y - 32, 64) / 32
    lattice = np.exp(-((np.abs(u) + np.abs(v) - 1.0) / 0.07) ** 2)
    leaf = (np.exp(-(u ** 2 / 0.012 + (v + 0.05) ** 2 / 0.10))
            + 0.8 * np.exp(-((np.abs(u) - 0.2) ** 2 / 0.008 + (v - 0.2) ** 2 / 0.03))
            + 0.6 * np.exp(-(u ** 2 / 0.03 + (v + 0.45) ** 2 / 0.006)))
    pattern = lattice + np.clip(leaf, 0, 1)
    stripes = np.cos(2 * np.pi * X / 8)          # printed paper ribbing
    lum = (-7.0 * pattern + 1.2 * stripes
           + fnoise(rng, beta=3.0) * 4.5
           + fnoise(rng, beta=1.8, kmin=6) * 2.0
           + fnoise(rng, beta=0.6, kmin=24) * 2.2
           + fnoise(rng, beta=1.5, aniso=(1, 6), kmin=3) * 1.5)
    img = add_lum(img, lum, (1.0, 0.97, 0.85))
    # water stains: noisy blobs with a darker tide-line rim, brown tint
    stain_col = np.array([150, 118, 58])
    for cx, cy, sx, sy, a in ((30, 18, 22, 30, 0.22), (96, 92, 16, 20, 0.15), (70, 6, 30, 14, 0.18)):
        m = blob((S, S), cx, cy, sx, sy) * (1 + 0.35 * fnoise(rng, beta=2.0))
        inner = smoothstep(0.35, 0.6, m)
        rim = smoothstep(0.28, 0.36, m) - smoothstep(0.36, 0.48, m)
        img = over(img, stain_col, a * inner + 1.6 * a * np.clip(rim, 0, 1))
    # drip runs from the top edge (wrap into the bottom, so tiling stays seamless)
    runs = np.clip(fnoise(rng, beta=1.2, aniso=(1, 14), kmin=2), 0, None)
    fade = np.exp(-((Y - 2) % S) / 40)
    img = over(img, stain_col, np.clip(runs * fade * 0.06, 0, 0.12))
    return img


def tex_yellow_wallpaper() -> None:
    save_png(_wallpaper(), TEX / "block" / "yellow_wallpaper.png")


def _baseboard(img) -> np.ndarray:
    """Light-grey painted baseboard on the bottom 1/8 with a small top lip and shadow."""
    rng = rng_for(121)
    X, Y = grid()
    board = _smooth_concrete(rng, (204, 202, 198), mottle=2.0, fine=0.6)
    m = box((S, S), -1, S - 16, S + 1, S + 1)
    img = add_lum(img, -14 * np.exp(-((Y - (S - 17)) / 1.8) ** 2) * (Y < S - 16))  # contact shadow
    img = over(img, board, m)
    img = add_lum(img, 18 * box((S, S), -1, S - 16, S + 1, S - 14))   # lit top lip
    img = add_lum(img, -10 * box((S, S), -1, S - 14, S + 1, S - 13))  # lip underside
    img = add_lum(img, -8 * box((S, S), -1, S - 2, S + 1, S + 1))     # floor scuffs
    return img


def tex_yellow_wallpaper_baseboard() -> None:
    save_png(_baseboard(_wallpaper()), TEX / "block" / "yellow_wallpaper_baseboard.png")


def _carpet(seed=102) -> np.ndarray:
    """Coarse beige-ochre loop carpet: per-pixel speckle + 2x2 fibre clumps + soft wear."""
    rng = rng_for(seed)
    img = fill(CARPET, (S, S))
    clump = np.kron(rng.normal(0, 1, (S // 2, S // 2)), np.ones((2, 2)))
    lum = (rng.normal(0, 1, (S, S)) * 8.0 + clump * 6.0
           + fnoise(rng, beta=3.0) * 5.0 + fnoise(rng, beta=1.6, kmin=8) * 3.0)
    lum -= (rng.random((S, S)) < 0.035) * 22.0      # dark fibre tips
    lum += (rng.random((S, S)) < 0.02) * 14.0       # light fibre tips
    img = add_lum(img, lum, (1.0, 0.94, 0.75))
    dirt = smoothstep(0.9, 2.2, fnoise(rng, beta=3.2))   # damp/dirty patches
    return over(img, np.array([120, 100, 52]), dirt * 0.25)


def tex_lobby_carpet() -> None:
    save_png(_carpet(), TEX / "block" / "lobby_carpet.png")


def tex_lobby_carpet_arrow() -> None:
    """Carpet with a faded dark arrow pointing to the texture's top (north)."""
    img = _carpet()
    rng = rng_for(118)
    img = marker(img, rng, [[(64, 108), (64, 24)], [(40, 48), (64, 22), (88, 48)]],
                 width=11, color=(70, 52, 24), alpha=0.6)
    save_png(img, TEX / "block" / "lobby_carpet_arrow.png")


def tex_ceiling_tile() -> None:
    """2x2 small acoustic tiles per block, fissured surface, dark seams and bevels."""
    rng = rng_for(103)
    img = fill(CEILING, (S, S))
    X, Y = grid()
    tone = np.kron(rng.normal(0, 3.0, (2, 2)), np.ones((S // 2, S // 2)))
    pits = blur((rng.random((S, S)) < 0.07) * rng.uniform(0.3, 1, (S, S)), 0.5)
    fiss = smoothstep(0.07, 0.0, np.abs(fnoise(rng, beta=2.4, kmin=3)))
    lum = tone - pits * 40 - fiss * 8 + fnoise(rng, beta=3.0) * 3 + rng.normal(0, 1.5, (S, S))
    img = add_lum(img, lum, (1.0, 0.98, 0.94))
    st = smoothstep(0.4, 0.65, blob((S, S), 100, 100, 18, 14) * (1 + 0.4 * fnoise(rng, beta=2)))
    img = over(img, np.array([168, 146, 100]), st * 0.35)     # water-stained corner
    dx, dy = wrapd(X, 64), wrapd(Y, 64)
    for d in (dx, dy):
        img = add_lum(img, 10 * ((d > 1.5) & (d < 3.5)))      # lit bevel
        img = add_lum(img, -9 * ((d < -1.5) & (d > -3.5)))    # shadowed bevel
    seam = np.clip(smoothstep(1.8, 0.8, np.abs(dx)) + smoothstep(1.8, 0.8, np.abs(dy)), 0, 1)
    img = over(img, np.array([92, 86, 76]), seam)
    save_png(img, TEX / "block" / "ceiling_tile.png")


def _fluorescent_panel(on: bool) -> np.ndarray:
    rng = rng_for(104)
    img = _smooth_concrete(rng, (212, 212, 216), mottle=1.5, fine=0.5)  # thin painted frame
    core = box((S, S), 8, 8, S - 8, S - 8)
    groove = box((S, S), 6, 6, S - 6, S - 6) - core
    img = over(img, np.array([120, 120, 126]), groove)
    if on:
        diff = fill((255, 255, 255), (S, S))   # pure white emissive diffuser
    else:
        X, Y = grid()
        diff = _smooth_concrete(rng, (160, 162, 168), mottle=1.5, fine=0.4)
        for tx in (40, 88):           # dead tubes faintly visible through the diffuser
            tube = np.exp(-((X - tx) / 7) ** 2)
            ends = (smoothstep(26, 14, Y) + smoothstep(S - 26, S - 14, Y)) * tube
            diff = add_lum(diff, tube * 10 - ends * 30)
    return over(img, diff, core)


def tex_fluorescent_light() -> None:
    save_png(_fluorescent_panel(True), TEX / "block" / "fluorescent_light.png")


def tex_fluorescent_light_off() -> None:
    save_png(_fluorescent_panel(False), TEX / "block" / "fluorescent_light_off.png")


def tex_wall_decals() -> None:
    """Black-marker drawings on wallpaper (wall_arrow_left/right, handprint, window, door)."""
    base = _wallpaper()
    rng = rng_for(130)
    left = [[(104, 66), (24, 62)], [(48, 40), (22, 62), (50, 88)]]
    save_png(marker(base.copy(), rng, left, width=7), TEX / "block" / "wall_arrow_left.png")
    right = [[(24, 62), (104, 66)], [(80, 40), (106, 66), (78, 90)]]
    save_png(marker(base.copy(), rng, right, width=7), TEX / "block" / "wall_arrow_right.png")

    def hand(d, k):
        # palm + four fingers + thumb as thick round-capped strokes
        d.ellipse((44 * k, 58 * k, 84 * k, 102 * k), fill=255)
        for cx, cy, ang, ln in ((47, 50, -18, 26), (58, 40, -6, 32), (70, 39, 4, 33),
                                (81, 46, 15, 28), (36, 76, -55, 22)):
            a = np.radians(ang)
            ex, ey = cx + np.sin(a) * ln * 0.5, cy - np.cos(a) * ln * 0.5
            sx, sy = cx - np.sin(a) * ln * 0.5, cy + np.cos(a) * ln * 0.5 + 10
            d.line((sx * k, sy * k, ex * k, ey * k), fill=255, width=int(9 * k))
            d.ellipse(((ex - 4.5) * k, (ey - 4.5) * k, (ex + 4.5) * k, (ey + 4.5) * k), fill=255)
    m = pil_mask(hand)
    breakup = smoothstep(-1.8, 0.0, blur(fnoise(rng, beta=1.4), 0.8))   # grimy, partly smudged print
    hp = over(base.copy(), np.array([62, 34, 22]), m * breakup * 0.8)
    save_png(hp, TEX / "block" / "wall_handprint.png")

    win = [[(26, 28), (102, 26), (104, 98), (24, 100), (26, 28)],
           [(64, 27), (64, 99)], [(25, 63), (103, 62)]]
    w = marker(base.copy(), rng, win, width=5)
    w = marker(w, rng, [[(34, 40), (52, 36)], [(74, 38), (94, 34)]], width=3, alpha=0.6)
    save_png(w, TEX / "block" / "wall_window_drawing.png")

    # door spans two blocks: draw on a 128x256 sheet (bottom half has the baseboard), split
    sheet = np.concatenate([base, _baseboard(base)], axis=0)
    sheet = marker(sheet, rng, [[(24, 238), (26, 22), (102, 20), (104, 238)]], width=6)
    sheet = marker(sheet, rng, [[(86, 148), (90, 144), (94, 148), (90, 152), (86, 148)]], width=5)
    save_png(sheet[:S], TEX / "block" / "wall_door_drawing_top.png")
    save_png(sheet[S:], TEX / "block" / "wall_door_drawing_bottom.png")


def tex_rug() -> None:
    """Round cream/red/teal swirl rug over carpet, 3x3 blocks -> rug_0..rug_8 (row-major)."""
    rng = rng_for(131)
    N = 3 * S
    img = tiled(_carpet())     # outside the circle the floor carpet continues seamlessly
    X, Y = grid((N, N))
    dx, dy = X - N / 2, Y - N / 2
    r = np.hypot(dx, dy)
    th = np.arctan2(dy, dx)
    R = N / 2 - 10
    cream, red, teal = np.array([226, 212, 178]), np.array([150, 44, 38]), np.array([36, 112, 112])
    rug = fill(cream, (N, N))
    # spiral swirl arms: phase grows with radius -> arms curl around the centre
    ph = 5 * th + r / 16 + 1.5 * np.sin(r / 40)
    rug = over(rug, red, smoothstep(0.55, 0.75, np.sin(ph)) * (r < R - 22))
    rug = over(rug, teal, smoothstep(0.65, 0.85, np.sin(ph + np.pi)) * (r < R - 22))
    rug = over(rug, red, smoothstep(26, 20, r))                      # centre medallion
    rug = over(rug, cream, smoothstep(12, 8, r))

    def band(a, b):
        return smoothstep(a - 1, a + 1, r) * smoothstep(b + 1, b - 1, r)
    rug = over(rug, red, band(R - 22, R - 12))
    rug = over(rug, teal, band(R - 9, R - 5))
    rug = add_lum(rug, rng.normal(0, 5, (N, N)) + blur(rng.normal(0, 4, (N, N)), 1)
                  + fnoise(rng, (N, N), beta=3) * 4)
    edge = np.clip(R - r + 0.5, 0, 1)
    img = add_lum(img, -25 * smoothstep(R + 5, R, r) * (1 - edge))   # slight edge shadow
    img = over(img, rug, edge)
    for i in range(9):
        ry, rx = divmod(i, 3)
        save_png(img[ry * S:(ry + 1) * S, rx * S:(rx + 1) * S], TEX / "block" / f"rug_{i}.png")


WOOD = np.array([150, 104, 62])
WOOD_DARK = np.array([88, 60, 36])


def _wood(rng, base, horizontal: bool, shape=(S, S)) -> np.ndarray:
    aniso = (12, 1) if horizontal else (1, 12)
    g = fnoise(rng, shape, beta=2.2, aniso=aniso)
    fine = fnoise(rng, shape, beta=1.0, aniso=aniso, kmin=6)
    return add_lum(fill(base, shape), g * 9 + fine * 4, (1.0, 0.78, 0.55))


def tex_lobby_door() -> None:
    """Wooden panel door with casing and brass knob; 2 blocks tall, split top/bottom."""
    rng = rng_for(132)
    shp = (2 * S, S)
    X, Y = grid(shp)
    img = _wood(rng, WOOD, horizontal=False, shape=shp)
    leaf = box(shp, 10, 10, S - 10, 2 * S + 20)
    img = over(img, _wood(rng, WOOD_DARK, False, shp), 1 - leaf)          # casing
    img = add_lum(img, -30 * (box(shp, 8, 8, S - 8, 2 * S + 20) - leaf))  # gap shadow
    for y0, y1 in ((28, 112), (152, 236)):              # recessed panels with bevels
        outer = box(shp, 26, y0, S - 26, y1)
        inner = box(shp, 32, y0 + 6, S - 32, y1 - 6)
        bevel = outer - inner
        lit = ((X < 32) | (Y < y0 + 6)) & ~((X > S - 32) | (Y > y1 - 6))
        img = add_lum(img, bevel * np.where(lit, 16, -20))
        img = add_lum(img, -6 * inner)
    plate = box(shp, 97, 126, 107, 158)
    img = over(img, np.array([150, 120, 50]), plate * 0.85)
    knob = supersample(shp, lambda x, y: np.hypot(x - 102, y - 140) < 6)
    img = over(img, np.array([196, 160, 70]), knob)
    hl = supersample(shp, lambda x, y: np.hypot(x - 100, y - 138) < 2.2)
    img = over(img, np.array([255, 236, 170]), hl)
    save_png(img[:S], TEX / "block" / "lobby_door_top.png")
    save_png(img[S:], TEX / "block" / "lobby_door_bottom.png")


def tex_red_alarm_lamp() -> None:
    """Round red lens on a dark metal base plate (emissive in game)."""
    rng = rng_for(133)
    img = _smooth_concrete(rng, (62, 62, 68), mottle=3, fine=1.0)
    X, Y = grid()
    r = np.hypot(X - 64, Y - 64)
    img = over(img, np.array([120, 120, 128]), np.clip(46 - r, 0, 1))
    ringm = smoothstep(46, 40, r)
    img = add_lum(img, 20 * ringm * (Y < 60) - 20 * ringm * (Y > 68))
    lens = np.clip(37 - r, 0, 1)
    t = np.clip(r / 37, 0, 1)[..., None]
    col = lerp(np.array([255, 110, 90]), np.array([225, 26, 18]), np.clip(t * 1.6, 0, 1))
    col = lerp(col, np.array([140, 8, 8]), np.clip(t * 3 - 2, 0, 1))
    img = lerp(img, col, lens[..., None])
    spec = np.exp(-(((X - 52) / 6) ** 2 + ((Y - 50) / 4) ** 2))
    img = add_lum(img, 90 * spec * lens, (1, 0.9, 0.85))
    for a in range(45, 360, 90):                      # base screws
        sx, sy = 64 + 54 * np.cos(np.radians(a)), 64 + 54 * np.sin(np.radians(a))
        img = over(img, np.array([150, 150, 156]), np.clip(3.2 - np.hypot(X - sx, Y - sy), 0, 1))
    save_png(img, TEX / "block" / "red_alarm_lamp.png")


# ---------------------------------------------------------------- Level 1 ---

CONCRETE = (146, 140, 146)     # lit by cool white -> ~#6A646B (faint magenta, no sepia)


def _concrete_wall(seed=106) -> np.ndarray:
    rng = rng_for(seed)
    img = _smooth_concrete(rng, CONCRETE, mottle=6.0, fine=1.6)
    img = add_lum(img, fnoise(rng, beta=1.4, aniso=(1, 8), kmin=2) * 2.5)   # run-off streaks
    img = add_lum(img, fnoise(rng, beta=1.2, aniso=(10, 1), kmin=2) * 1.5)  # formwork grain
    return _pores(img, rng)


def tex_concrete_wall() -> None:
    save_png(_concrete_wall(), TEX / "block" / "concrete_wall.png")


def tex_garage_pillar() -> None:
    """Concrete pillar with a darker painted base band (bottom ~40%) and a ragged top edge."""
    rng = rng_for(119)
    img = _concrete_wall()
    X, Y = grid()
    edge = 78 + 1.5 * fnoise(rng, (1, S), beta=2.6)
    band = smoothstep(-0.7, 0.7, Y - edge)
    paint = _smooth_concrete(rng, (92, 86, 96), mottle=3.0, fine=1.0)
    chips = smoothstep(2.0, 2.6, blur(fnoise(rng, beta=1.3), 0.8))
    img = over(img, paint, band * (1 - chips))
    img = add_lum(img, -10 * np.exp(-((Y - edge) / 1.2) ** 2))
    img = add_lum(img, -16 * smoothstep(S - 8, S, Y))  # dirt at the floor
    save_png(img, TEX / "block" / "garage_pillar.png")


PILLAR_LABELS = ("1", "2", "3", "5", "6", "9", "12", "99")


def tex_pillar_numbers() -> None:
    """Concrete + big worn painted dark-yellow numbers (labels 1,2,3,5,6,9,12,99)."""
    rng = rng_for(134)
    base = _concrete_wall()
    paint = np.array([150, 132, 52])
    for label in PILLAR_LABELS:
        px = 96 if len(label) == 1 else 76

        def draw(d, k, label=label, px=px):
            d.text((64 * k, 66 * k), label, fill=255, font=font(px * k), anchor="mm")
        m = pil_mask(draw)
        wear = (np.clip(0.9 + 0.1 * fnoise(rng, beta=1.5), 0, 1)
                * (1 - smoothstep(1.5, 2.1, fnoise(rng, beta=1.2))))
        save_png(over(base.copy(), paint, m * wear * 0.95), TEX / "block" / f"pillar_number_{label}.png")


def _garage_floor(rng, color) -> np.ndarray:
    img = _smooth_concrete(rng, color, mottle=5.0, fine=1.4)
    img = add_lum(img, rng.normal(0, 2.0, (S, S)))                       # aggregate grit
    tyre = fnoise(rng, beta=1.6, aniso=(1, 10), kmin=2)
    img = add_lum(img, -smoothstep(1.0, 2.2, tyre) * 8)                  # tyre streaks
    oil = smoothstep(0.6, 1.0, blob((S, S), 40, 80, 18, 12) * (1 + 0.5 * fnoise(rng, beta=2.2)))
    img = add_lum(img, -oil * 8, (1.0, 0.95, 1.05))
    return _pores(img, rng, density=0.008, depth=10)


def tex_concrete_floor() -> None:
    save_png(_garage_floor(rng_for(107), (72, 64, 78)), TEX / "block" / "concrete_floor.png")


def tex_wet_concrete_floor() -> None:
    rng = rng_for(108)
    img = _garage_floor(rng, (66, 59, 72))
    pud = smoothstep(-0.1, 0.6, fnoise(rng, beta=3.4))
    img = lerp(img, blur(img, 1.5) * 0.78, pud[..., None])             # dark, still water
    sheen = smoothstep(0.6, 1.8, fnoise(rng, beta=2.0, aniso=(3, 1))) * pud
    img = add_lum(img, sheen * 30, (0.95, 0.97, 1.1))                  # reflected light
    rim = np.clip(smoothstep(0.0, 0.2, pud) - smoothstep(0.2, 0.6, pud), 0, 1)
    img = add_lum(img, -rim * 8)
    save_png(img, TEX / "block" / "wet_concrete_floor.png")


def _tube(img, vertical: bool) -> np.ndarray:
    """Metal housing + pure white tube. Horizontal tubes span the full width (they tile)."""
    sh = (S, S)
    if vertical:
        rim, house, core = box(sh, 48, 2, 80, S - 2), box(sh, 50, 4, 78, S - 4), box(sh, 56, 10, 72, S - 10)
    else:
        rim, house, core = box(sh, -1, 48, S + 1, 80), box(sh, -1, 50, S + 1, 78), box(sh, -1, 56, S + 1, 72)
    img = over(img, np.array([60, 58, 64]), rim)
    img = over(img, np.array([176, 176, 182]), house)
    return over(img, np.array([255, 255, 255]), core)


def tex_warehouse_light() -> None:
    save_png(_tube(_concrete_wall(), True), TEX / "block" / "warehouse_light.png")


def tex_ceiling_tube() -> None:
    save_png(_tube(_concrete_wall(122), False), TEX / "block" / "ceiling_tube.png")


def tex_ceiling_beam() -> None:
    rng = rng_for(123)
    img = _smooth_concrete(rng, (112, 104, 114), mottle=5.0, fine=1.4)
    img = add_lum(img, fnoise(rng, beta=1.2, aniso=(10, 1), kmin=2) * 2.0)
    X, Y = grid()
    img = add_lum(img, -9 * smoothstep(1.2, 0.3, np.abs(wrapd(Y, 32))))   # board-form joints
    save_png(_pores(img, rng), TEX / "block" / "ceiling_beam.png")


# ---------------------------------------------------------------- Level 2 ---

# Level 2 v2: pipes are real 3D model blocks now, so the wall is plain painted concrete
# and the pipe/duct/bracket textures are uniform surfaces that look right on any UV slice
# of a thin model element (pipe axis = texture x; everything tiles in both directions).

PIPE_WALL = (184, 179, 204)    # pale lavender-white paint, face mean ~#B4AFC8


def _pale_wall(seed=110) -> np.ndarray:
    """Smooth painted concrete: very subtle mottling, faint vertical drip stains."""
    rng = rng_for(seed)
    img = _smooth_concrete(rng, PIPE_WALL, mottle=2.5, fine=0.7)
    drips = np.clip(fnoise(rng, beta=1.2, aniso=(1, 14), kmin=2), 0, None)
    img = over(img, np.array([140, 134, 160]), np.clip(drips * 0.12, 0, 0.18))
    return _pores(img, rng, density=0.004, depth=6)


def tex_pipe_wall() -> None:
    """Pipe-corridor wall with a thin darker skirting band at the bottom of each block.

    The band repeats on every block (walls are stacked), so it is kept thin and soft:
    it reads as a pour/formwork joint higher up and as a skirting at floor level.
    """
    img = _pale_wall()
    X, Y = grid()
    band = box((S, S), -1, S - 5, S + 1, S + 1)
    img = over(img, np.array([150, 145, 170]), band * 0.45)
    img = add_lum(img, -6 * np.exp(-((Y - (S - 5.5)) / 0.9) ** 2))        # small shadow lip
    save_png(img, TEX / "block" / "pipe_wall.png")


def tex_pipe_floor() -> None:
    """Slightly greyer worn concrete with scuffs and a few hairline cracks."""
    rng = rng_for(112)
    img = _smooth_concrete(rng, (146, 142, 160), mottle=4.0, fine=1.2)
    scuff = smoothstep(0.8, 2.0, fnoise(rng, beta=2.0, aniso=(1, 3)))
    img = over(img, np.array([104, 100, 118]), scuff * 0.35)
    img = add_lum(img, rng.normal(0, 1.8, (S, S)))
    crack = smoothstep(0.035, 0.0, np.abs(fnoise(rng, beta=2.6, kmin=2)))   # zero lines = cracks
    crack *= smoothstep(0.6, 1.4, fnoise(rng, beta=3.0))                    # only in a few places
    img = add_lum(img, -34 * crack)
    save_png(_pores(img, rng, density=0.006, depth=8), TEX / "block" / "pipe_floor.png")


def _pipe_ceiling() -> np.ndarray:
    """Plain concrete a touch darker than the walls, faint board seams every half block."""
    rng = rng_for(125)
    img = _smooth_concrete(rng, (166, 162, 184), mottle=3.0, fine=0.9)
    X, Y = grid()
    img = add_lum(img, -7 * smoothstep(1.0, 0.2, np.abs(wrapd(Y, 64))))
    return _pores(img, rng, density=0.006, depth=8)


def tex_pipe_ceiling() -> None:
    save_png(_pipe_ceiling(), TEX / "block" / "pipe_ceiling.png")


def tex_pipe_light() -> None:
    save_png(_tube(_pipe_ceiling(), False), TEX / "block" / "pipe_light.png")


def _bolts(img, spacing=32, r=2.6, off=(16, 16), col=(150, 150, 156)) -> np.ndarray:
    """Round bolt heads on a regular grid (spacing divides 128 -> seamless) with a highlight."""
    X, Y = grid()
    dx, dy = wrapd(X - off[0], spacing), wrapd(Y - off[1], spacing)
    d = np.hypot(dx, dy)
    img = add_lum(img, -20 * smoothstep(r + 1.6, r, d) * (d > r))            # contact shadow
    img = over(img, np.array(col), np.clip(r - d + 0.5, 0, 1))
    return add_lum(img, 40 * np.clip(r * 0.45 - np.hypot(dx + 0.8, dy + 0.8) + 0.5, 0, 1))


def tex_rusty_pipe() -> None:
    """Thin rusty pipe surface (axis = x): red-brown, darker rust patches, worn light streaks."""
    rng = rng_for(111)
    img = fill((136, 74, 56), (S, S))   # between #7A3E2E and #9A5A44
    img = add_lum(img, fnoise(rng, beta=1.6, aniso=(6, 1), kmin=2) * 6, (1.0, 0.75, 0.6))  # streaks along x
    patches = smoothstep(0.3, 1.3, fnoise(rng, beta=2.2, aniso=(3, 1)))      # stretched along the pipe
    img = over(img, np.array([92, 46, 34]), patches * 0.7)                   # dark rust
    flakes = smoothstep(1.4, 2.2, fnoise(rng, beta=1.0, kmin=8))
    img = over(img, np.array([168, 96, 60]), flakes * 0.6)                   # orange flaking
    worn = smoothstep(1.0, 2.2, fnoise(rng, beta=1.4, aniso=(10, 1), kmin=2))
    img = over(img, np.array([160, 120, 104]), worn * 0.45)                  # rubbed-bare streaks
    save_png(add_lum(img, rng.normal(0, 3.0, (S, S))), TEX / "block" / "rusty_pipe.png")


def _galvanised(rng, base=(84, 86, 92)) -> np.ndarray:
    img = _smooth_concrete(rng, base, mottle=2.5, fine=0.8)
    img = add_lum(img, np.kron(rng.normal(0, 2.5, (S // 16, S // 16)), np.ones((16, 16))))  # spangle
    grime = smoothstep(0.6, 1.8, fnoise(rng, beta=2.4))
    return over(img, np.array([56, 52, 50]), grime * 0.4)


def tex_pipe_bracket() -> None:
    """Dark grey galvanised steel shelf bracket with bolts (32 px grid)."""
    rng = rng_for(127)
    img = _galvanised(rng)
    rust = smoothstep(1.5, 2.3, fnoise(rng, beta=1.6))
    img = over(img, np.array([110, 64, 44]), rust * 0.5)
    save_png(_bolts(img, col=(128, 130, 136)), TEX / "block" / "pipe_bracket.png")


def tex_pale_duct() -> None:
    """Big pale painted duct (axis = x): faint longitudinal seam at v=0.5, subtle dirt."""
    rng = rng_for(124)
    img = _smooth_concrete(rng, (168, 164, 182), mottle=3.0, fine=0.8)
    img = add_lum(img, fnoise(rng, beta=1.4, aniso=(8, 1), kmin=2) * 2.0)   # faint lengthwise grain
    X, Y = grid()
    seam = np.abs(Y - 64)
    img = add_lum(img, -14 * smoothstep(1.2, 0.3, seam) + 6 * smoothstep(2.6, 1.6, seam) * (Y < 63))
    dirt = smoothstep(0.5, 1.8, fnoise(rng, beta=2.6, aniso=(3, 1)))
    img = over(img, np.array([120, 114, 128]), dirt * 0.3)
    save_png(img, TEX / "block" / "pale_duct.png")


def tex_pale_duct_flange() -> None:
    """Duct flange ring: slightly darker painted metal with a bolt every 32 px."""
    rng = rng_for(128)
    img = _smooth_concrete(rng, (140, 136, 154), mottle=2.5, fine=0.8)
    dirt = smoothstep(0.5, 1.8, fnoise(rng, beta=2.4))
    img = over(img, np.array([100, 96, 110]), dirt * 0.3)
    save_png(_bolts(img, r=3.0, col=(116, 114, 124)), TEX / "block" / "pale_duct_flange.png")


def tex_pipe_valve() -> None:
    """Red cast-iron valve wheel (cutout RGBA): rim, 4 spokes, hub; stem metal in a corner."""
    rng = rng_for(129)
    X, Y = grid()
    r = np.hypot(X - 64, Y - 64)
    th = np.arctan2(Y - 64, X - 64)
    rim = (r > 46) & (r < 58)
    spoke = (np.abs(np.sin(2 * (th - np.pi / 4))) * r < 5.5) & (r < 50)   # 4 spokes (x shape)
    hub = r < 13
    solid = rim | spoke | hub
    paint = fill((168, 34, 30), (S, S))
    # rounded cast look: lighter towards the top-left of each part, chipped paint, grime
    paint = add_lum(paint, 22 * np.clip((64 - (X + Y) / 2) / 64, -1, 1))
    rim_round = np.cos((r - 52) / 6 * np.pi / 2)
    paint = add_lum(paint, np.where(rim, 18 * rim_round - 10, 0))
    chips = smoothstep(1.3, 2.0, fnoise(rng, beta=1.2, kmin=4))
    paint = over(paint, np.array([60, 56, 54]), chips * 0.8)
    paint = add_lum(paint, rng.normal(0, 4, (S, S)))
    hub_col = over(paint, np.array([70, 70, 74]), (r < 6).astype(float))      # stem nut
    img = np.zeros((S, S, 4))
    img[..., :3] = hub_col
    img[..., 3] = np.where(solid, 255, 0)
    # solid dark stem metal in the bottom-left 16x16 corner (UV [0,14,2,16]) for the stem box
    stem = (X < 16) & (Y > S - 16)
    stem_col = _galvanised(rng, (64, 64, 70))
    img[stem, :3] = stem_col[stem]
    img[stem, 3] = 255
    save_png(img, TEX / "block" / "pipe_valve.png")


def tex_floor_grate() -> None:
    """Dark steel drain grate (slots over blackness) set in a pipe_floor concrete border."""
    rng = rng_for(130)
    img = _smooth_concrete(rng, (146, 142, 160), mottle=4.0, fine=1.2)
    img = add_lum(img, rng.normal(0, 1.8, (S, S)))
    X, Y = grid()
    frame = box((S, S), 12, 12, S - 12, S - 12)
    img = add_lum(img, -30 * (box((S, S), 10, 10, S - 10, S - 10) - frame))   # dirt gap
    steel = _galvanised(rng, (62, 64, 70))
    img = over(img, steel, frame)
    # slots: 6 px wide openings every 12 px, running across, inside a 6 px steel border
    slots = np.zeros((S, S))
    for x0 in range(22, S - 20, 12):
        slots = np.maximum(slots, box((S, S), x0, 20, x0 + 6, S - 20))
    depth = 1 - 0.6 * np.clip((Y - 20) / (S - 40), 0, 1)
    img = over(img, np.array([6, 6, 8]) * depth[..., None] + 4, slots)
    img = add_lum(img, 20 * np.clip(box((S, S), 12, 12, S - 12, 14) + box((S, S), 12, 12, 14, S - 12), 0, 1))
    save_png(img, TEX / "block" / "floor_grate.png")


def tex_bulkhead_lamp() -> None:
    """Frosted warm-white bulb glass (full-bright); brightest in the centre."""
    rng = rng_for(131)
    X, Y = grid()
    r = np.hypot(X - 64, Y - 64) / 64
    img = lerp(np.array([255, 250, 236]), np.array([236, 226, 204]), np.clip(r, 0, 1)[..., None] ** 1.5)
    img = add_lum(img, fnoise(rng, beta=2.2) * 1.5 + rng.normal(0, 0.8, (S, S)))   # frosting
    save_png(img, TEX / "block" / "bulkhead_lamp.png")


def tex_bulkhead_cage() -> None:
    """Dark painted metal for the lamp's wire cage and back plate (screws near the corners)."""
    rng = rng_for(132)
    img = _smooth_concrete(rng, (46, 46, 52), mottle=2.0, fine=0.8)
    img = add_lum(img, 6 * np.clip(1 - grid()[1] / S * 2, -1, 1))   # soft top highlight
    for cx, cy in ((14, 14), (S - 14, 14), (14, S - 14), (S - 14, S - 14)):
        img = _bolts(img, spacing=S, r=3.0, off=(cx, cy), col=(110, 110, 116))
    save_png(img, TEX / "block" / "bulkhead_cage.png")


# -------------------------------------------------------------- Stairwell ---

def tex_stairwell_wall() -> None:
    rng = rng_for(126)
    img = _smooth_concrete(rng, (224, 220, 236), mottle=3.5, fine=1.0)
    X, Y = grid()
    scuffs = smoothstep(0.5, 1.0, Y / S) * smoothstep(0.8, 2.0, fnoise(rng, beta=2.0, aniso=(3, 1)))
    img = over(img, np.array([150, 144, 166]), scuffs * 0.3)
    save_png(_pores(img, rng, density=0.006, depth=8), TEX / "block" / "stairwell_wall.png")


def tex_chain_link() -> None:
    """Cutout diamond chain-link mesh (alpha is strictly 0 or 255)."""
    X, Y = grid()
    p = 16
    d1 = np.abs(wrapd(X + Y, p)) / np.sqrt(2)
    d2 = np.abs(wrapd(X - Y, p)) / np.sqrt(2)
    wire1, wire2 = d1 < 0.95, d2 < 0.95
    # weave: at alternating crossings one wire passes over the other
    over1 = (np.floor((X + Y + p / 2) / p) + np.floor((X - Y + p / 2) / p)) % 2 == 0
    shade = np.where(wire1 & wire2, np.where(over1, 1.08, 0.82), 1.0)
    lum = 168 * shade - 30 * np.minimum(np.where(wire1, d1, 9), np.where(wire2, d2, 9))
    img = np.zeros((S, S, 4))
    img[..., 0] = lum
    img[..., 1] = lum * 1.01
    img[..., 2] = lum * 1.05
    img[..., 3] = np.where(wire1 | wire2, 255, 0)
    save_png(img, TEX / "block" / "chain_link.png")


def tex_stair_tread() -> None:
    """Concrete tread with a worn yellow safety nosing along the top (north) edge."""
    rng = rng_for(120)
    img = _smooth_concrete(rng, (150, 146, 154), mottle=5.0, fine=1.3)
    img = _pores(img, rng, density=0.01, depth=12)
    nose = box((S, S), -1, -1, S + 1, 16)
    wear = 1 - smoothstep(1.2, 2.0, fnoise(rng, beta=1.4))
    img = over(img, np.array([214, 182, 42]), nose * wear * 0.95)
    for gy in (5, 10):                                        # anti-slip grooves
        img = add_lum(img, -22 * box((S, S), -1, gy, S + 1, gy + 1.5))
    img = add_lum(img, -24 * box((S, S), -1, 16, S + 1, 17.5))
    save_png(img, TEX / "block" / "stair_tread.png")


def tex_exit_sign() -> None:
    X, Y = grid()
    r2 = np.clip(((X - 64) / 60) ** 2 + ((Y - 64) / 60) ** 2, 0, 1)
    img = lerp(np.array([40, 170, 80]), np.array([18, 100, 44]), r2[..., None])
    img = over(img, np.array([226, 230, 226]), 1 - box((S, S), 6, 6, S - 6, S - 6))
    m = pil_mask(lambda d, k: d.text((64 * k, 66 * k), "EXIT", fill=255, font=font(40 * k), anchor="mm"))
    img = over(img, np.array([250, 255, 250]), m)
    save_png(img, TEX / "block" / "exit_sign.png")


def tex_exit_door() -> None:
    """Grey steel fire door with push bar and wired window, 2 blocks tall."""
    rng = rng_for(115)
    shp = (2 * S, S)
    X, Y = grid(shp)
    img = _smooth_concrete(rng, (126, 128, 132), mottle=3.0, fine=0.8, shape=shp)
    leaf = box(shp, 10, 10, S - 10, 2 * S + 20)
    img = over(img, np.array([70, 72, 76]), 1 - box(shp, 8, 8, S - 8, 2 * S + 20))
    img = add_lum(img, -18 * (box(shp, 8, 8, S - 8, 2 * S + 20) - leaf))
    img = over(img, np.array([168, 170, 176]), box(shp, 18, 140, S - 18, 150))
    img = add_lum(img, 30 * box(shp, 18, 140, S - 18, 142) - 30 * box(shp, 18, 149, S - 18, 152))
    win = box(shp, 40, 40, 88, 100)
    img = over(img, np.array([40, 48, 52]), win)
    img = add_lum(img, 18 * win * (Y < 60))
    save_png(img[:S], TEX / "block" / "exit_door_top.png")
    save_png(img[S:], TEX / "block" / "exit_door_bottom.png")


def _plank_crate(rng, top: bool) -> np.ndarray:
    """Dark-wood crate face: horizontal planks with dark gaps, frame boards, nails."""
    base = np.array([96, 66, 42])
    img = _wood(rng, base, horizontal=True)
    X, Y = grid()
    for y in range(0, S, 32):
        img = add_lum(img, rng.normal(0, 6) * box((S, S), -1, y, S + 1, y + 32))
        img = add_lum(img, -40 * box((S, S), -1, y - 1, S + 1, y + 1.5))
    if not top:
        frame = np.clip(box((S, S), -1, -1, S + 1, 14) + box((S, S), -1, S - 14, S + 1, S + 1)
                        + box((S, S), -1, -1, 14, S + 1) + box((S, S), S - 14, -1, S + 1, S + 1), 0, 1)
        img = over(img, _wood(rng, base * 0.85, horizontal=False), frame)
        img = add_lum(img, -28 * np.clip(blur(frame, 1.5) - frame, 0, 1) * 2)   # frame shadow
    for x, y in ((7, 7), (S - 7, 7), (7, S - 7), (S - 7, S - 7), (7, 64), (S - 7, 64)):
        img = over(img, np.array([120, 118, 112]), np.clip(2.2 - np.hypot(X - x, Y - y), 0, 1))
    return img


def tex_supply_crate() -> None:
    save_png(_plank_crate(rng_for(116), False), TEX / "block" / "supply_crate.png")


def tex_supply_crate_top() -> None:
    save_png(_plank_crate(rng_for(117), True), TEX / "block" / "supply_crate_top.png")

# -------------------------------------------------------------- Poolrooms ---

POOL_WHITE = (231, 228, 235)   # cool off-white; with grout the face averages ~#DCD9E0 (no clipping)
POOL_GROUT = (142, 138, 147)   # #8E8A93
POOL_DARK = (20, 28, 40)       # #141C28
POOL_TILE = 32                 # 4x4 big tiles per 128 px face


def _pool_tiles(seed, tile_col, grout_col, glaze=2.5, speck=1.0) -> np.ndarray:
    """Big glazed ceramic tiles (32 px = 4x4 per block) with thin 2 px grout; seamless."""
    rng = rng_for(seed)
    T = POOL_TILE
    n = S // T
    X, Y = grid()
    u, v = (X % T) / T, (Y % T) / T
    # very subtle per-tile shade + tiny speckle + faint large-scale mottling
    tone = np.kron(rng.normal(0, glaze, (n, n)), np.ones((T, T)))
    img = add_lum(fill(tile_col, (S, S)),
                  tone + rng.normal(0, speck, (S, S)) + fnoise(rng, beta=3.0) * glaze * 0.4)
    # faint glaze: soft diagonal highlight toward each tile's top-left + a gloss streak
    img = add_lum(img, (0.5 - 0.5 * (u + v)) * glaze * 1.6)
    img = add_lum(img, glaze * 1.8 * np.exp(-((u - v * 0.35 - 0.3) / 0.05) ** 2) * (v < 0.55) * (v > 0.1))
    # pillowed tile edges next to the grout
    du, dv = X % T, Y % T
    img = add_lum(img, 3 * ((du < 3.5) | (dv < 3.5)) - 4 * ((du > T - 3.5) | (dv > T - 3.5)))
    g = np.minimum(np.abs(wrapd(X, T)), np.abs(wrapd(Y, T)))   # distance to the nearest grout line
    return over(img, np.array(grout_col), smoothstep(1.5, 0.7, g))  # ~2 px grout, wraps seamlessly


def tex_pool_tile() -> None:
    save_png(_pool_tiles(140, POOL_WHITE, POOL_GROUT), TEX / "block" / "pool_tile.png")


def tex_pool_tile_dark() -> None:
    save_png(_pool_tiles(141, POOL_DARK, (58, 66, 80), glaze=1.4, speck=0.6),
             TEX / "block" / "pool_tile_dark.png")


def _caustics(frame: int, frames: int) -> np.ndarray:
    """Pool caustic network (0..1), periodic in space (128 px) and in time (frames).

    Classic caustic look = the edges of a Voronoi cell pattern (where the distance to the
    nearest and second-nearest seed points is almost equal), with wavy edges from a
    gentle domain warp. Seed points circle around fixed homes and the warp waves advance
    a whole number of cycles per loop, so the last frame flows back into the first.
    Distances wrap around the face, so the texture tiles seamlessly.
    """
    rng = rng_for(145)                       # same seeds every frame -> coherent motion
    X, Y = grid()
    tt = 2 * np.pi * frame / frames
    # domain warp: low integer-frequency waves (seamless), drifting one cycle per loop
    wx = 4.0 * np.sin(2 * np.pi * (2 * Y + X) / S + tt) + 2.0 * np.sin(2 * np.pi * 3 * Y / S - tt + 1.3)
    wy = 4.0 * np.sin(2 * np.pi * (2 * X - Y) / S - tt + 2.1) + 2.0 * np.sin(2 * np.pi * 3 * X / S + tt + 0.4)
    Xw, Yw = X + wx, Y + wy
    n = 14
    home = rng.uniform(0, S, (n, 2))
    rad = rng.uniform(3.0, 7.0, n)
    ph0 = rng.uniform(0, 2 * np.pi, n)
    spin = rng.choice([-1, 1], n)
    d = np.empty((n, S, S))
    for k in range(n):
        px = home[k, 0] + rad[k] * np.cos(spin[k] * tt + ph0[k])
        py = home[k, 1] + rad[k] * np.sin(spin[k] * tt + ph0[k])
        d[k] = np.hypot(wrapd(Xw - px, S), wrapd(Yw - py, S))
    d.sort(axis=0)
    edge = d[1] - d[0]                       # 0 on cell borders
    lines = np.exp(-(edge / 2.2) ** 2)       # thin bright borders
    glow = np.exp(-(edge / 7.0) ** 2) * 0.25  # soft halo around them
    return np.clip(blur(lines + glow, 0.5), 0, 1)


def tex_pool_floor() -> None:
    """Animated underwater tile floor: bluish 4x4 tiles with drifting caustic ripples."""
    frames = 16
    base = _pool_tiles(143, (196, 208, 222), (118, 128, 144))   # bluer, as seen through water
    strip = []
    for i in range(frames):
        c = _caustics(i, frames)
        strip.append(add_lum(base, c * 26, (0.85, 1.0, 1.05)))   # subtle-medium, slightly cyan
    path = TEX / "block" / "pool_floor.png"
    save_png(np.concatenate(strip, axis=0), path)
    path.with_name("pool_floor.png.mcmeta").write_text(
        '{"animation": {"frametime": 2, "interpolate": true}}\n')


def tex_pool_skylight() -> None:
    """Frosted daylight glass panel (full-bright): pale sky-white with a thin mullion frame."""
    rng = rng_for(144)
    X, Y = grid()
    r = np.hypot(X - 64, Y - 64) / 64
    glass = lerp(np.array([230, 246, 255]), np.array([208, 228, 242]), np.clip(r, 0, 1)[..., None] ** 2)
    glass = add_lum(glass, fnoise(rng, beta=2.5) * 1.5 + rng.normal(0, 0.8, (S, S)))   # frosting
    frame_col = _smooth_concrete(rng, (206, 210, 216), mottle=1.0, fine=0.4)
    inner = box((S, S), 5, 5, S - 5, S - 5)
    img = over(frame_col, glass, inner)
    img = add_lum(img, -26 * (box((S, S), 4, 4, S - 4, S - 4) - inner))   # thin dark seal line
    save_png(img, TEX / "block" / "pool_skylight.png")


def tex_pool_lamp() -> None:
    """Underwater pool lamp face: glowing cyan-white round lens in a stainless ring on tiles."""
    img = _pool_tiles(140, POOL_WHITE, POOL_GROUT)
    X, Y = grid()
    r = np.hypot(X - 64, Y - 64)
    R = 38.0                       # lens ~60 % of the face
    img = add_lum(img, -18 * smoothstep(R + 9, R + 4, r) * (r > R + 4))   # soft recess shadow
    ring = np.clip(R + 4.5 - r, 0, 1)
    shade = 0.8 + 0.25 * np.clip((64 - Y) / 40, -1, 1)                    # brushed steel, lit top
    img = over(img, np.array([186, 190, 196]) * shade[..., None], ring)
    img = add_lum(img, 30 * np.exp(-((r - R - 3.5) / 0.8) ** 2) * (Y < 64))  # ring highlight
    lens = np.clip(R - r + 0.5, 0, 1)
    t = np.clip(r / R, 0, 1)[..., None] ** 1.6
    col = lerp(np.array([191, 246, 255]), np.array([63, 217, 240]), t)
    img = lerp(img, col, lens[..., None])
    save_png(img, TEX / "block" / "pool_lamp.png")


def remove_stale() -> None:
    """Delete files from earlier versions that are no longer generated.

    Fixtures are static textures now (per-fixture flicker is done by the lighting code);
    the v1 Poolrooms panel light and teal trim tile were removed.
    """
    for name in ("fluorescent_light", "warehouse_light", "pipe_light"):
        (TEX / "block" / f"{name}.png.mcmeta").unlink(missing_ok=True)
    for name in ("pool_light", "pool_tile_trim"):
        (TEX / "block" / f"{name}.png").unlink(missing_ok=True)



# =============================================================================
# Item textures
# =============================================================================

def tex_camcorder_battery() -> None:
    rng = rng_for(201)
    img = np.zeros((16, 16, 4))
    x0, x1, y0, y1 = 2, 13, 4, 13
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            c = np.array([34, 34, 38]) + rng.normal(0, 2)
            if y == y0 + 1:
                c = np.array([62, 62, 68])
            if y >= y1 - 1:
                c = c * 0.7
            if x == x1 - 1:
                c = c * 0.8
            img[y, x] = (*c, 255)
    img[y0, x0:x1 + 1] = (10, 10, 12, 255)
    img[y1, x0:x1 + 1] = (8, 8, 10, 255)
    img[y0:y1 + 1, x0] = (10, 10, 12, 255)
    img[y0:y1 + 1, x1] = (8, 8, 10, 255)
    # gold terminals on top
    for tx in (5, 6, 9, 10):
        img[y0 - 1, tx] = (204, 170, 78, 255)
    img[y0 - 1, 4] = img[y0 - 1, 7] = img[y0 - 1, 8] = img[y0 - 1, 11] = (10, 10, 12, 255)
    # silver label with "text"
    img[6:9, 4:12] = (150, 154, 162, 255)
    img[7, 5:9] = (60, 62, 70, 255)
    img[7, 10] = (60, 62, 70, 255)
    img[6, 4:12, :3] += 18
    # charge stripe
    img[10, 4:12] = (20, 22, 20, 255)
    img[10, 4:10] = (72, 214, 96, 255)
    img[10, 4, :3] = (140, 240, 150)
    save_png(img, TEX / "item" / "camcorder_battery.png")


def tex_almond_water() -> None:
    img = np.zeros((16, 16, 4))
    outline = (96, 108, 116, 255)
    # silhouette rows: (y, x_left, x_right)
    rows = {3: (6, 9), 4: (5, 10), 5: (4, 11)}
    for y in range(6, 15):
        rows[y] = (4, 11)
    rows[15] = (5, 10)
    # cap
    img[1, 6:10] = (58, 112, 214, 255)
    img[2, 6:10] = (36, 82, 178, 255)
    img[0, 6:10] = (24, 56, 140, 255)
    img[1, 6] = (120, 170, 240, 255)
    for y, (xl, xr) in rows.items():
        for x in range(xl, xr + 1):
            if y >= 6:
                c = (232, 216, 176, 230)       # almond liquid
                if y == 6:
                    c = (246, 238, 214, 235)   # meniscus
            else:
                c = (214, 230, 238, 110)       # empty clear plastic
            img[y, x] = c
        img[y, xl] = outline
        img[y, xr] = outline
    img[15, 5:11] = outline
    img[3, 6] = img[3, 9] = outline
    img[4, 5] = img[4, 10] = outline
    # label band
    img[9:12, 5:11] = (246, 246, 240, 255)
    img[10, 6:10] = (150, 118, 74, 255)
    img[9, 5:11, :3] -= 10
    # glossy highlight
    for y in range(5, 14):
        if not (9 <= y <= 11):
            img[y, 5] = (255, 255, 255, 220)
    img[13, 10] = (196, 176, 136, 235)
    img[14, 6:10] = (210, 192, 150, 235)
    save_png(img, TEX / "item" / "almond_water.png")


# =============================================================================
# Entity texture
# =============================================================================

def tex_bacteria() -> None:
    rng = rng_for(301)
    N = 64
    v = np.full((N, N), 15.0) + blur_wrap(rng.normal(0, 1.5, (N, N)), 1)
    hl = np.zeros((N, N))       # highlight mask (tarry sheen on wire edges)

    def wire_px(x, y, bright):
        xi, yi = int(x) % N, int(y) % N
        v[yi, xi] = min(v[yi, xi], 11.0)
        xh = (xi + 1) % N
        v[yi, xh] = max(v[yi, xh], bright)
        hl[yi, xh] = 1.0

    # twisted rebar pairs running vertically
    for _ in range(12):
        x0 = rng.uniform(0, N)
        ph = rng.uniform(0, 2 * np.pi)
        per = rng.uniform(5, 9)
        amp = rng.uniform(1.0, 2.0)
        for y in range(N):
            for s in (0.0, np.pi):
                x = round(x0 + amp * np.sin(2 * np.pi * y / per + ph + s))
                front = np.cos(2 * np.pi * y / per + ph + s) > 0
                wire_px(x, y, 27.0 if front else 21.0)
    # tangled wire strands
    for _ in range(150):
        x, y = rng.uniform(0, N, 2)
        ang = rng.normal(np.pi / 2, 0.5) if rng.random() < 0.75 else rng.uniform(0, np.pi)
        bright = rng.uniform(19, 25)
        for _s in range(int(rng.integers(5, 22))):
            wire_px(x, y, bright)
            ang += rng.normal(0, 0.3)
            x += np.cos(ang)
            y += np.sin(ang)
    v = np.clip(v, 10, 30)
    img = np.stack([v + hl * 1.5, v + hl * 0.5, v - hl * 0.5], axis=-1)
    img = np.clip(img, 10, 30)

    # Rows 40-63: the flesh (organs in BacteriaModel.organs, and the sinew and muscle filling out
    # the limbs). The same near-black as the wires (about 10-30), so the whole body reads as one
    # colour; only a faint mottle, slightly darker veins and a few dull wet specks give it the
    # feel of flesh when the flashlight is on it.
    frng = rng_for(302)
    fh = N - 40
    mott = blur(fnoise(frng, (fh, N), beta=2.2), 0.6)
    lum = np.clip(0.55 + 0.18 * mott, 0.2, 1.0)
    grey = 11.0 + 14.0 * lum
    vein = np.zeros((fh, N))
    for _ in range(14):
        x, y = frng.uniform(0, N), frng.uniform(0, fh)
        ang = frng.uniform(0, 2 * np.pi)
        for _s in range(int(frng.integers(6, 18))):
            vein[int(y) % fh, int(x) % N] = 1.0
            ang += frng.normal(0, 0.45)
            x += np.cos(ang)
            y += np.sin(ang)
    vein = np.clip(blur(vein, 0.5) * 2.5, 0, 1)
    grey = grey * (1 - 0.25 * vein)
    gloss = (frng.random((fh, N)) < 0.03).astype(float)
    grey = np.clip(grey + gloss * 8.0, 10, 32)
    img[40:] = np.stack([grey + gloss * 1.5, grey + gloss * 0.5, grey - gloss * 0.5], axis=-1)
    save_png(img, TEX / "entity" / "bacteria.png")

    # NightShot (infrared) version, drawn full-bright over the model only while your NightShot is
    # on. Same wire pattern so the two line up; the black wires turn pale like IR-lit objects do
    # in camcorder night mode, and the darkest gaps stay grey so the shape keeps its texture.
    # The flesh uses the same mapping as the wires, so in NightShot the body is one colour too.
    ir = 120.0 + (v - 10.0) * 4.5 + hl * 25.0
    ir[40:] = 120.0 + (grey - 10.0) * 4.5 + gloss * 25.0
    save_png(np.clip(np.stack([ir, ir, ir], axis=-1), 0, 255), TEX / "entity" / "bacteria_ir.png")


# =============================================================================
# Audio helpers
# =============================================================================

def t_axis(dur: float) -> np.ndarray:
    return np.arange(int(dur * SR)) / SR


def lp_gain(f, fc, order=2):
    return 1.0 / np.sqrt(1.0 + (f / fc) ** (2 * order))


def hp_gain(f, fc, order=2):
    return 1.0 / np.sqrt(1.0 + (fc / np.maximum(f, 1e-3)) ** (2 * order))


def bp_gain(f, lo, hi, order=2):
    return lp_gain(f, hi, order) * hp_gain(f, lo, order)


def fft_filter(x: np.ndarray, gain_fn, circular: bool = False) -> np.ndarray:
    """Zero-phase FFT-domain filter. circular=True keeps loops periodic."""
    n = len(x)
    xx = x if circular else np.concatenate([x, np.zeros(SR // 2)])
    f = np.fft.rfftfreq(len(xx), 1 / SR)
    y = np.fft.irfft(np.fft.rfft(xx) * gain_fn(f), len(xx))
    return y[:n]


def periodic_noise(n: int, rng, gain_fn=None) -> np.ndarray:
    """Noise that loops perfectly over n samples, shaped by gain_fn(freq)."""
    f = np.fft.rfftfreq(n, 1 / SR)
    spec = rng.normal(size=f.size) + 1j * rng.normal(size=f.size)
    if gain_fn is not None:
        spec *= gain_fn(f)
    spec[0] = 0
    x = np.fft.irfft(spec, n)
    return x / (x.std() + 1e-12)


def conv(x: np.ndarray, ir: np.ndarray) -> np.ndarray:
    n = len(x) + len(ir) - 1
    N = 1 << int(np.ceil(np.log2(n)))
    return np.fft.irfft(np.fft.rfft(x, N) * np.fft.rfft(ir, N), N)[:n]


def circ_conv(x: np.ndarray, ir: np.ndarray) -> np.ndarray:
    n = len(x)
    irp = np.zeros(n)
    for i in range(0, len(ir), n):
        seg = ir[i:i + n]
        irp[: len(seg)] += seg
    return np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(irp), n)


def ring(freqs, decays, amps, dur) -> np.ndarray:
    t = t_axis(dur)
    out = np.zeros_like(t)
    for f, d, a in zip(freqs, decays, amps):
        out += a * np.sin(2 * np.pi * f * t) * np.exp(-t / d)
    return out


def reverb_ir(rng, dur, decay, lp=6000.0, predelay=0.0, early=()) -> np.ndarray:
    t = t_axis(dur)
    ir = rng.normal(size=t.size) * np.exp(-t / decay)
    ir = fft_filter(ir, lambda f: lp_gain(f, lp, 1))
    ir[: int(predelay * SR)] = 0
    for dt, a in early:
        ir[int(dt * SR)] += a * np.abs(ir).max() * 8
    ir /= np.sqrt((ir ** 2).sum()) + 1e-12
    return ir


def add_at(buf: np.ndarray, sig: np.ndarray, start: float, circular: bool) -> None:
    i0 = int(round(start * SR))
    if circular:
        idx = (i0 + np.arange(len(sig))) % len(buf)
        np.add.at(buf, idx, sig)
    else:
        end = min(len(buf), i0 + len(sig))
        if end > i0:
            buf[i0:end] += sig[: end - i0]


def env_adsr(n, attack, release, hold_end=None) -> np.ndarray:
    t = np.arange(n) / SR
    dur = n / SR
    hold_end = dur - release if hold_end is None else hold_end
    e = np.clip(t / max(attack, 1e-4), 0, 1)
    e *= np.clip((hold_end + release - t) / max(release, 1e-4), 0, 1)
    return e


def svf(x: np.ndarray, fc, q: float, mode: str = "bp") -> np.ndarray:
    """Chamberlin state-variable filter with per-sample cutoff (for sweeps)."""
    fc = np.broadcast_to(np.minimum(np.asarray(fc, float), 6500.0), x.shape)
    fco = 2.0 * np.sin(np.pi * fc / SR)
    low = band = 0.0
    out = np.empty_like(x)
    damp = 1.0 / q
    for i in range(len(x)):
        f = fco[i]
        low += f * band
        high = x[i] - low - damp * band
        band += f * high
        out[i] = band if mode == "bp" else low
    return out


def voice(f0: np.ndarray, formants, rng, nh: int = 48, tilt: float = 0.03) -> np.ndarray:
    """Additive formant voice: harmonics of time-varying f0 weighted by formant peaks."""
    phase = 2 * np.pi * np.cumsum(f0) / SR
    out = np.zeros_like(f0)
    for k in range(1, nh + 1):
        fk = k * f0
        amp = tilt / k + sum(g * np.exp(-0.5 * ((fk - F) / bw) ** 2) for F, bw, g in formants)
        amp = amp * (fk < 15000)
        out += amp * np.sin(k * phase + rng.uniform(0, 2 * np.pi))
    return out


def smooth_jitter(rng, n, cutoff, amount) -> np.ndarray:
    j = fft_filter(rng.normal(size=n), lambda f: lp_gain(f, cutoff, 2))
    return j / (np.abs(j).max() + 1e-12) * amount


def bitcrush(x, bits, hold) -> np.ndarray:
    q = 2 ** (bits - 1)
    y = np.round(x * q) / q
    if hold > 1:
        y = y[(np.arange(len(y)) // hold) * hold]
    return y


def dist(x, drive) -> np.ndarray:
    return np.tanh(drive * x) / np.tanh(drive)


def norm(x, peak=1.0) -> np.ndarray:
    m = np.abs(x).max()
    return x / m * peak if m > 0 else x


def clicks(rng, times, dur, f_lo, f_hi, decay_lo=0.002, decay_hi=0.006, amp_pow=1.0) -> np.ndarray:
    buf = np.zeros(int(dur * SR))
    for tt in times:
        f = rng.uniform(f_lo, f_hi)
        d = rng.uniform(decay_lo, decay_hi)
        c = ring([f, f * rng.uniform(1.4, 2.3)], [d, d * 0.6], [1, 0.5], d * 6)
        c[:20] += rng.normal(size=20) * 0.6
        add_at(buf, c * rng.uniform(0.3, 1.0) ** amp_pow, tt, circular=False)
    return buf


def save_ogg(x: np.ndarray, path: Path, peak: float = 0.89, loop: bool = False,
             fade_in: float = 0.003, fade_out: float = 0.02) -> None:
    x = np.asarray(x, float)
    x = x - x.mean()
    if not loop:
        n = len(x)
        fi = max(1, int(fade_in * SR))
        fo = max(1, int(fade_out * SR))
        x[:fi] *= np.linspace(0, 1, fi)
        x[n - fo:] *= np.linspace(1, 0, fo)
    x = norm(x, peak)
    path.parent.mkdir(parents=True, exist_ok=True)
    # Vorbis can overshoot on hard-edged (distorted/crushed) material: re-encode
    # with a lower gain until the decoded peak is safely below clipping.
    gain = 1.0
    for _ in range(6):
        # loops get a higher Vorbis quality: the codec's low-frequency error otherwise
        # leaves a small step where the end meets the start
        extra = {"compression_level": 0.0} if loop else {}
        sf.write(str(path), (x * gain).astype(np.float32), SR, format="OGG", subtype="VORBIS", **extra)
        decoded_peak = np.abs(sf.read(str(path))[0]).max()
        if decoded_peak <= max(peak, 0.9) + 0.02:
            break
        gain *= peak / decoded_peak


# =============================================================================
# Ambient loops. Every component is periodic over the loop length (periodic
# noise, whole-cycle sines, circular event placement/reverb), so the loop seam
# is sample-continuous. Content is kept low and smooth: almost nothing above
# ~1.5 kHz, no rasp or amplitude-modulated grit (that read as "flies").
# =============================================================================

def drift(n, rng, amount=0.1, lo=0.15, hi=0.5) -> np.ndarray:
    """Very slow periodic gain wobble: 1 +/- amount, energy only between lo..hi Hz."""
    d = periodic_noise(n, rng, lambda f: ((f >= lo) & (f <= hi)).astype(float))
    return 1 + amount * d / (np.abs(d).max() + 1e-12)


def mains_hum(t, f0=120.0, levels_db=(0, -10, -18, -24, -32, -40)) -> np.ndarray:
    """Smooth transformer hum: f0 and harmonics falling off steeply (dB per harmonic)."""
    return sum(10 ** (db / 20) * np.sin(2 * np.pi * f0 * (k + 1) * t + 0.7 * k)
               for k, db in enumerate(levels_db))


def snd_level0_hum() -> None:
    """12 s office room tone: soft brown noise, distant HVAC rumble, faint hum bed."""
    rng = rng_for(401)
    T = 12.0
    n = int(T * SR)
    t = np.arange(n) / SR
    room = periodic_noise(n, rng, lambda f: hp_gain(f, 20, 2) * lp_gain(f, 450, 3) / np.maximum(f, 20))
    hvac = periodic_noise(n, rng, lambda f: bp_gain(f, 40, 90, 3)) * drift(n, rng, 0.2, 0.08, 0.3)
    hum = mains_hum(t, levels_db=(0, -12, -22, -30)) * drift(n, rng, 0.1)
    room, hvac = norm(room), norm(hvac)
    rms = np.sqrt(np.mean(room ** 2))
    hum = hum / np.sqrt(np.mean(hum ** 2)) * rms * 10 ** (-20 / 20)    # -20 dB under the noise
    x = room + 0.6 * hvac + hum
    save_ogg(x, SND / "ambient" / "level0_hum.ogg", peak=0.5, loop=True)


def snd_level1_ambience() -> None:
    """12 s concrete garage: deep rumble, faint ventilation, rare soft drips in a big room."""
    rng = rng_for(402)
    T = 12.0
    n = int(T * SR)
    t = np.arange(n) / SR
    rumble = periodic_noise(n, rng, lambda f: bp_gain(f, 22, 70, 3)) * drift(n, rng, 0.15, 0.08, 0.3)
    vent = periodic_noise(n, rng, lambda f: bp_gain(f, 150, 600, 2) / np.sqrt(np.maximum(f, 20)))
    vent *= drift(n, rng, 0.25, 0.08, 0.25)
    room = periodic_noise(n, rng, lambda f: hp_gain(f, 20, 2) * lp_gain(f, 300, 3) / np.maximum(f, 20))
    drips = np.zeros(n)
    for st, f0, a in ((2.6, 950, 1.0), (8.9, 820, 0.6)):
        dt = t_axis(0.12)
        f = f0 * (1 + 0.5 * np.clip(dt / 0.05, 0, 1))
        d = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-dt / 0.03) * smoothstep(0, 0.004, dt)
        add_at(drips, d * a, st, circular=True)
    drips = fft_filter(drips, lambda f: lp_gain(f, 1400, 2), circular=True)
    wet = circ_conv(drips, reverb_ir(rng, 4.0, 1.3, lp=1200, predelay=0.05))
    x = norm(rumble) * 0.8 + norm(vent) * 0.18 + norm(room) * 0.5 + norm(drips) * 0.04 + norm(wet) * 0.12
    save_ogg(x, SND / "ambient" / "level1_ambience.ogg", peak=0.55, loop=True)


def snd_level2_ambience() -> None:
    """12 s pipe maze: low machinery drone, slow metallic pipe resonances, soft hiss swells."""
    rng = rng_for(403)
    T = 12.0
    n = int(T * SR)
    t = np.arange(n) / SR
    drone = np.zeros(n)
    for h, db in ((1, 0), (2, -6), (3, -12), (4, -18), (5, -26), (6, -32)):
        f = 37.5 * h
        a = 10 ** (db / 20)
        drone += a * np.sin(2 * np.pi * f * t + rng.uniform(0, 6.28))
        drone += 0.5 * a * np.sin(2 * np.pi * (f + 1 / T * 3) * t + rng.uniform(0, 6.28))  # slow beating
    rumble = periodic_noise(n, rng, lambda f: bp_gain(f, 20, 80, 3))
    # metallic resonance: narrow noise bands at pipe modes, each slowly swelling
    metal = np.zeros(n)
    for fc in (196.0, 311.0, 467.0, 702.0):
        band = periodic_noise(n, rng, lambda f, fc=fc: np.exp(-0.5 * ((f - fc) / 2.5) ** 2))
        metal += band * drift(n, rng, 0.8, 0.08, 0.2) * (fc / 196.0) ** -1.2
    hiss = periodic_noise(n, rng, lambda f: bp_gain(f, 400, 1500, 2))
    swell = sum(np.exp(-0.5 * (wrapd(t - c, T) / w) ** 2) for c, w in ((3.0, 0.9), (9.5, 0.6)))
    x = norm(drone) * 0.6 + norm(rumble) * 0.4 + norm(metal) * 0.25 + norm(hiss) * swell * 0.05
    save_ogg(x, SND / "ambient" / "level2_ambience.ogg", peak=0.55, loop=True)


# =============================================================================
# Bacteria entity sounds
# =============================================================================

def _rattle(rng, dur, f_lo, f_hi, rate) -> np.ndarray:
    n = int(dur * SR)
    t = np.arange(n) / SR
    f0 = np.interp(t, [0, dur * 0.4, dur], [f_lo, f_hi, f_lo * 0.9]) * (1 + smooth_jitter(rng, n, 12, 0.08))
    v = voice(f0, [(420, 140, 1.0), (980, 220, 0.6), (2300, 400, 0.25)], rng, nh=40)
    am = (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * np.cumsum(rate * (1 + smooth_jitter(rng, n, 5, 0.3))) / SR)))
    am = fft_filter(am, lambda f: lp_gain(f, 300, 1))
    return dist(norm(v) * am, 3.0) * env_adsr(n, 0.05, dur * 0.3)


def _creak(rng, dur, rate_lo, rate_hi, res) -> np.ndarray:
    n = int(dur * SR)
    ct = np.arange(n) / SR
    rate = np.interp(ct, [0, dur * 0.5, dur], [rate_lo, rate_hi, rate_lo]) * (1 + smooth_jitter(rng, n, 8, 0.25))
    ph = np.cumsum(rate) / SR
    p = np.zeros(n)
    p[np.nonzero(np.diff(np.floor(ph)) > 0)[0]] = 1.0
    p *= rng.uniform(0.4, 1.0, n) * np.sin(np.pi * ct / dur) ** 0.7
    return conv(p, ring(res, [0.012, 0.009, 0.006], [1, 0.7, 0.4], 0.06))[:n]


def _crackle(rng, dur, density, f_lo=2500) -> np.ndarray:
    n = int(dur * SR)
    x = np.zeros(n)
    k = rng.random(n) < density / SR
    x[k] = rng.choice([-1, 1], k.sum()) * rng.random(k.sum()) ** 3
    x = fft_filter(x, lambda f: hp_gain(f, f_lo, 2))
    return conv(x, ring([3200, 5100], [0.0015, 0.001], [1, 0.6], 0.01))[:n]


def snd_bacteria_ambient() -> None:
    specs = [
        # (seed, dur, weights: clicks, crackle, creak, rattle)
        (501, 1.7, (1.0, 0.35, 0.0, 0.8)),
        (502, 1.3, (0.8, 0.2, 1.0, 0.0)),
        (503, 2.3, (0.5, 0.6, 0.5, 1.0)),
    ]
    for i, (seed, dur, (wc, wk, wr, wg)) in enumerate(specs, 1):
        rng = rng_for(seed)
        n = int(dur * SR)
        times = np.sort(rng.uniform(0.02, dur - 0.1, int(rng.integers(7, 14))))
        # bunch some clicks into quick wet bursts
        times = np.concatenate([times, times[::3] + rng.uniform(0.01, 0.03)])
        wet = clicks(rng, times, dur, 700, 1800, 0.004, 0.012)
        dry = clicks(rng, times + 0.004, dur, 2500, 4800, 0.0015, 0.004)
        x = np.zeros(n)
        x += wc * norm(wet * 0.7 + dry * 0.5)
        x += wk * norm(_crackle(rng, dur, 220)) * np.clip(np.sin(np.pi * np.arange(n) / n) * 1.5, 0, 1)
        if wr:
            cr = norm(_creak(rng, dur * 0.8, 25, 70, [310, 690, 1450]))
            x += wr * 0.8 * np.pad(cr, (int(dur * 0.1 * SR), n))[:n]
        if wg:
            rd = dur * 0.75
            g = _rattle(rng, rd, 70 + 10 * i, 105 + 8 * i, 22 + 3 * i)
            x += wg * np.pad(norm(g), (int(dur * 0.15 * SR), n))[:n] * 0.7
        ir = reverb_ir(rng, 0.6, 0.12, lp=5000)
        x = x + 0.25 * norm(conv(x, ir)[:n]) * np.abs(x).max()
        save_ogg(x, SND / "entity" / f"bacteria_ambient{i}.ogg", peak=0.85, fade_out=0.05)


def _scream(rng, dur, f_pts, f1_pts, f2_pts, env_pts, drive=5.0, bits=6, hold=2,
            detune=1.059, sub=0.35) -> np.ndarray:
    n = int(dur * SR)
    t = np.arange(n) / SR
    f0 = np.interp(t, *zip(*f_pts))
    vib = 1 + 0.035 * np.sin(2 * np.pi * 6.3 * t + rng.uniform(0, 6)) + smooth_jitter(rng, n, 25, 0.04)
    f0 = f0 * vib
    F1 = np.interp(t, *zip(*f1_pts))
    F2 = np.interp(t, *zip(*f2_pts))
    forms = [(F1, 260, 1.0), (F2, 320, 0.75), (3300, 420, 0.5), (4400, 500, 0.3)]
    v1 = voice(f0, forms, rng, nh=44)
    v2 = voice(f0 * detune, [(F * 1.08 if np.ndim(F) else F * 1.08, bw, g) for F, bw, g in forms], rng, nh=40)
    v3 = voice(f0 * 0.5, [(F1 * 0.6, 300, 1.0), (1200, 300, 0.4)], rng, nh=30)
    breath = fft_filter(rng.normal(size=n), lambda f: bp_gain(f, 1500, 6500, 2))
    x = norm(v1) + 0.6 * norm(v2) + sub * norm(v3) + 0.2 * norm(breath)
    e = np.interp(t, *zip(*env_pts))
    x = norm(x) * e
    d = bitcrush(dist(x, drive), bits, hold)
    return 0.75 * d + 0.25 * x


def _metal_tail(rng, x, tail_level=0.4) -> np.ndarray:
    n = len(x)
    ir = ring([1130, 1790, 2650, 3420], [0.5, 0.42, 0.35, 0.28], [1, 0.7, 0.55, 0.35], 1.2)
    ir = ir / np.abs(ir).max() * 0.02 + reverb_ir(rng, 1.2, 0.35, lp=6000) * 0.5
    wet = conv(x, ir)[:n]
    return x + tail_level * norm(wet) * np.abs(x).max()


def snd_bacteria_screech() -> None:
    rng = rng_for(601)
    dur = 2.7
    x = _scream(rng, dur,
                f_pts=[(0, 170), (0.3, 640), (0.8, 720), (1.25, 930), (1.7, 760), (2.1, 520), (dur, 420)],
                f1_pts=[(0, 700), (0.5, 1050), (1.5, 1150), (dur, 850)],
                f2_pts=[(0, 1700), (0.5, 2500), (1.5, 2700), (dur, 2000)],
                env_pts=[(0, 0), (0.08, 0.8), (0.4, 1.0), (1.6, 1.0), (2.15, 0.25), (2.35, 0), (dur, 0)])
    save_ogg(_metal_tail(rng, x, 0.45), SND / "entity" / "bacteria_screech1.ogg", peak=0.89, fade_out=0.15)

    rng = rng_for(602)
    dur = 2.5
    x = _scream(rng, dur,
                f_pts=[(0, 1150), (0.2, 980), (0.55, 620), (0.7, 380), (0.85, 820), (1.3, 1020),
                       (1.75, 690), (dur, 300)],
                f1_pts=[(0, 1100), (0.7, 750), (1.0, 1200), (dur, 800)],
                f2_pts=[(0, 2800), (0.7, 1900), (1.0, 2700), (dur, 1800)],
                env_pts=[(0, 0), (0.04, 1.0), (0.6, 0.85), (0.72, 0.35), (0.86, 1.0), (1.55, 0.9),
                         (1.95, 0.15), (2.1, 0), (dur, 0)],
                drive=7.0, bits=5, hold=3, detune=1.12, sub=0.5)
    save_ogg(_metal_tail(rng, x, 0.5), SND / "entity" / "bacteria_screech2.ogg", peak=0.89, fade_out=0.15)


def snd_bacteria_steps() -> None:
    for i, (seed, dur) in enumerate(((701, 0.22), (702, 0.28), (703, 0.25)), 1):
        rng = rng_for(seed)
        n = int(dur * SR)
        t = np.arange(n) / SR
        t0 = 0.006
        tt = np.clip(t - t0, 0, None)
        on = (t >= t0).astype(float)
        f = np.interp(tt, [0, 0.06], [rng.uniform(100, 130), rng.uniform(42, 52)])
        thud = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt / rng.uniform(0.035, 0.055)) * on
        muff = fft_filter(rng.normal(size=n), lambda fr: lp_gain(fr, 380, 2)) * np.exp(-tt / 0.03) * on
        scuff = fft_filter(rng.normal(size=n), lambda fr: bp_gain(fr, 900, 3500, 1)) * np.exp(-tt / 0.02) * on
        ctimes = [t0 + 0.001, t0 + rng.uniform(0.014, 0.03), t0 + rng.uniform(0.05, 0.09)]
        if i == 2:
            ctimes.append(t0 + rng.uniform(0.11, 0.15))
        clk = clicks(rng, ctimes, dur, 2600, 5200, 0.0012, 0.003)
        x = norm(thud) + 0.5 * norm(muff) + 0.12 * norm(scuff) + 0.45 * norm(clk)
        save_ogg(x, SND / "entity" / f"bacteria_step{i}.ogg", peak=0.85, fade_in=0.001, fade_out=0.03)


def snd_bacteria_hurt() -> None:
    rng = rng_for(801)
    dur = 0.6
    x = _scream(rng, dur,
                f_pts=[(0, 620), (0.1, 1350), (0.25, 1100), (0.45, 700), (dur, 560)],
                f1_pts=[(0, 900), (0.2, 1200), (dur, 800)],
                f2_pts=[(0, 2400), (0.2, 3000), (dur, 2100)],
                env_pts=[(0, 0), (0.015, 1.0), (0.3, 0.8), (0.5, 0.15), (dur, 0)],
                drive=6.0, bits=6, hold=2, detune=1.08, sub=0.3)
    save_ogg(_metal_tail(rng, x, 0.25), SND / "entity" / "bacteria_hurt.ogg", peak=0.89, fade_out=0.05)


def snd_bacteria_death() -> None:
    rng = rng_for(901)
    dur = 2.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = _scream(rng, dur,
                f_pts=[(0, 700), (0.18, 1050), (0.5, 760), (1.0, 380), (1.4, 150), (dur, 90)],
                f1_pts=[(0, 1000), (0.4, 1150), (1.4, 600), (dur, 450)],
                f2_pts=[(0, 2600), (0.4, 2800), (1.4, 1500), (dur, 1100)],
                env_pts=[(0, 0), (0.03, 1.0), (0.6, 0.9), (1.2, 0.5), (1.55, 0.12), (1.75, 0), (dur, 0)],
                drive=8.0, bits=5, hold=3, detune=1.1, sub=0.6)
    dens = np.interp(t, [0, 0.8, 1.3, 1.7, dur], [20, 120, 500, 260, 0])
    crk = np.zeros(n)
    k = rng.random(n) < dens / SR
    crk[k] = rng.choice([-1, 1], k.sum()) * rng.random(k.sum()) ** 2
    crk = conv(crk, ring([2400, 4100, 900], [0.002, 0.0012, 0.004], [1, 0.6, 0.5], 0.02))[:n]
    wet_clk = clicks(rng, np.sort(rng.uniform(1.2, 1.85, 12)), dur, 700, 1600, 0.005, 0.012)
    y = _metal_tail(rng, x, 0.3) + 0.45 * norm(crk) + 0.35 * norm(wet_clk)
    save_ogg(y, SND / "entity" / "bacteria_death.ogg", peak=0.89, fade_out=0.08)


def snd_bacteria_search() -> None:
    """Searching: quiet, fast wire ticking with a sniffing rasp. Plays every 2 s while it hunts
    for you, so a hidden player can hear how close it is and which way it's moving."""
    for i, (seed, dur) in enumerate(((1001, 1.4), (1002, 1.7)), 1):
        rng = rng_for(seed)
        n = int(dur * SR)
        t = np.arange(n) / SR
        # Bursts of 4-7 quick ticks, like wire fingers tapping along a wall.
        times = []
        start = 0.03
        while start < dur - 0.25:
            k = int(rng.integers(4, 8))
            gap = rng.uniform(0.025, 0.045)
            times.extend(start + np.arange(k) * gap + rng.uniform(-0.004, 0.004, k))
            start += k * gap + rng.uniform(0.12, 0.3)
        ticks = clicks(rng, np.array(times), dur, 2200, 4600, 0.0012, 0.003, amp_pow=1.5)
        # Two short inhaled rasps (band-limited noise swells), the "sniffing".
        rasp = np.zeros(n)
        for s0 in rng.uniform(0.1, dur - 0.5, 2):
            m = int(0.28 * SR)
            seg = fft_filter(rng.normal(size=m), lambda f: bp_gain(f, 1800, 5200, 2))[:m]
            seg *= np.sin(np.pi * np.arange(m) / m) ** 2
            add_at(rasp, seg, s0, circular=False)
        x = norm(ticks) + 0.35 * norm(rasp) + 0.3 * norm(_crackle(rng, dur, 90))
        x *= np.clip(np.sin(np.pi * t / dur) * 1.4, 0, 1)
        x = x + 0.2 * norm(conv(x, reverb_ir(rng, 0.5, 0.1, lp=5000))[:n]) * np.abs(x).max()
        save_ogg(x, SND / "entity" / f"bacteria_search{i}.ogg", peak=0.8, fade_out=0.05)


def snd_bacteria_lost() -> None:
    """Giving up: a long creak that falls in pitch, with ticks thinning out as it moves away.
    It's the 'you made it' signal, so it must sound like the opposite of the screech."""
    rng = rng_for(1101)
    dur = 2.2
    n = int(dur * SR)
    t = np.arange(n) / SR
    # Creak whose pulse rate only falls (60 -> 14 per second): the wires slowing and settling.
    cdur = dur * 0.9
    cn = int(cdur * SR)
    ct = np.arange(cn) / SR
    rate = np.interp(ct, [0, cdur], [60, 14]) * (1 + smooth_jitter(rng, cn, 8, 0.25))
    ph = np.cumsum(rate) / SR
    pulses = np.zeros(cn)
    pulses[np.nonzero(np.diff(np.floor(ph)) > 0)[0]] = 1.0
    pulses *= rng.uniform(0.4, 1.0, cn)
    cr = conv(pulses, ring([260, 590, 1240], [0.012, 0.009, 0.006], [1, 0.7, 0.4], 0.06))[:cn]
    cr = np.pad(cr, (0, n))[:n]
    times = np.sort(rng.uniform(0.05, dur - 0.2, 26))
    keep = rng.random(len(times)) > times / dur  # fewer and fewer ticks toward the end
    ticks = clicks(rng, times[keep], dur, 1500, 3800, 0.002, 0.005)
    x = norm(cr) + 0.4 * norm(ticks)
    x = fft_filter(x, lambda f: lp_gain(f, 5200, 2))[:n]
    x *= np.interp(t, [0, 0.05, 1.0, dur], [0, 1, 0.7, 0])
    x = x + 0.3 * norm(conv(x, reverb_ir(rng, 0.9, 0.25, lp=3500))[:n]) * np.abs(x).max()
    save_ogg(x, SND / "entity" / "bacteria_lost.ogg", peak=0.82, fade_out=0.2)


# =============================================================================
# Camcorder sounds
# =============================================================================

def _beep(freq, dur, total, start=0.0, square=0.0) -> np.ndarray:
    n = int(total * SR)
    out = np.zeros(n)
    m = int(dur * SR)
    tt = np.arange(m) / SR
    s = np.sin(2 * np.pi * freq * tt) + square * np.sin(2 * np.pi * 3 * freq * tt) / 3
    s *= env_adsr(m, 0.003, 0.015)
    add_at(out, s, start, circular=False)
    return out


def snd_rec_beep() -> None:
    x = _beep(2200, 0.14, 0.25, start=0.005)
    save_ogg(x, SND / "camcorder" / "rec_beep.ogg", peak=0.7, fade_in=0.001, fade_out=0.005)


def snd_battery_low() -> None:
    x = _beep(1250, 0.12, 0.6, start=0.02, square=0.25) + _beep(1250, 0.12, 0.6, start=0.3, square=0.25)
    save_ogg(x, SND / "camcorder" / "battery_low.ogg", peak=0.7, fade_in=0.001, fade_out=0.005)


def snd_noclip() -> None:
    rng = rng_for(1001)
    dur = 2.5
    n = int(dur * SR)
    t = np.arange(n) / SR
    depth = np.clip(t / 1.6, 0, 1) ** 1.5
    wow = (0.06 * np.sin(2 * np.pi * 1.3 * t) + 0.03 * np.sin(2 * np.pi * 0.7 * t + 1)
           + 0.02 * np.sin(2 * np.pi * 11.0 * t)) * (0.3 + 1.7 * depth) + smooth_jitter(rng, n, 20, 0.03) * depth
    speed = 1 + wow
    tone = np.zeros(n)
    for f, a in ((110, 0.5), (165, 0.35), (220, 0.4), (330, 0.25), (440, 0.15), (60, 0.3)):
        tone += a * np.sin(2 * np.pi * np.cumsum(f * speed) / SR)
    hiss = fft_filter(rng.normal(size=n), lambda f: bp_gain(f, 1500, 9000, 1))
    tape = (norm(tone) * 0.6 + 0.15 * norm(hiss)) * np.interp(t, [0, 0.1, 1.55, 1.75], [0, 1, 1, 0.2])
    # swept whoosh
    fc = np.interp(t, [0, 0.6, 1.5, 1.9, dur], [250, 900, 4200, 1200, 200])
    who = svf(rng.normal(size=n), fc, 2.5)
    who = norm(who) * np.interp(t, [0, 0.4, 1.5, 1.8, 2.2], [0, 0.2, 1.0, 0.6, 0])
    # static burst with choppy gating + crush
    st = rng.normal(size=n)
    gate = (fft_filter(rng.normal(size=n), lambda f: lp_gain(f, 40, 2)) > -0.002).astype(float)
    gate = fft_filter(gate, lambda f: lp_gain(f, 600, 1))
    st = bitcrush(norm(st * gate), 4, 4) * np.interp(t, [1.5, 1.58, 2.05, 2.2], [0, 1, 0.8, 0])
    # low thump at the end
    tt = np.clip(t - 2.15, 0, None)
    f = np.interp(tt, [0, 0.12], [75, 30])
    thump = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt / 0.12) * (t >= 2.15)
    thump = dist(thump, 2.0)
    x = tape * 0.55 + who * 0.5 + st * 0.38 + thump * 0.9
    save_ogg(x, SND / "camcorder" / "noclip.ogg", peak=0.89, fade_in=0.01, fade_out=0.05)


def snd_escape() -> None:
    rng = rng_for(1101)
    dur = 2.5
    n = int(dur * SR)
    t = np.arange(n) / SR
    # source "playback": chord + hum + hiss at normal speed
    src = np.zeros(n)
    for f, a in ((220, 0.5), (277.2, 0.35), (329.6, 0.4), (440, 0.25), (660, 0.12), (60, 0.25), (120, 0.2)):
        src += a * np.sin(2 * np.pi * f * t + rng.uniform(0, 6))
    src = norm(src) + 0.12 * norm(fft_filter(rng.normal(size=n), lambda f: bp_gain(f, 1200, 9000, 1)))
    stop_t0, stop_t1 = 0.15, 1.6
    s = np.clip(1 - (t - stop_t0) / (stop_t1 - stop_t0), 0, 1) ** 1.6
    s[t < stop_t0] = 1.0
    pos = np.cumsum(s) - 1
    stopped = np.interp(pos, np.arange(n), src) * np.sqrt(s)
    stopped *= np.clip(t / 0.02, 0, 1)
    # mechanical click / clunk
    tc = np.clip(t - 1.72, 0, None)
    on = (t >= 1.72).astype(float)
    click = (ring([2800, 5200], [0.0015, 0.001], [1, 0.5], 0.02))
    clk = np.zeros(n)
    add_at(clk, click, 1.72, circular=False)
    clunk = np.sin(2 * np.pi * 130 * tc) * np.exp(-tc / 0.025) * on
    # static afterwards
    stat = fft_filter(rng.normal(size=n), lambda f: bp_gain(f, 500, 8000, 1))
    crk = _crackle(rng, dur, 300, 1500)
    stat_env = np.interp(t, [1.76, 1.8, 2.3, dur], [0, 1, 0.35, 0])
    x = stopped * 0.8 + norm(clk) * 0.9 + clunk * 0.5 + (norm(stat) * 0.25 + norm(crk) * 0.3) * stat_env
    save_ogg(x, SND / "camcorder" / "escape.ogg", peak=0.89, fade_in=0.005, fade_out=0.04)


def snd_flashlight_click() -> None:
    rng = rng_for(1102)
    dur = 0.15
    n = int(dur * SR)
    x = np.zeros(n)
    for st, a, sc in ((0.004, 1.0, 1.0), (0.011, 0.35, 1.15), (0.016, 0.15, 0.9)):
        c = ring(np.array([1850, 3300, 5600, 7400]) * sc, [0.004, 0.003, 0.002, 0.0015],
                 [0.8, 1.0, 0.6, 0.35], 0.03)
        c += ring([640 * sc], [0.008], [0.5], 0.04)[: len(c)]
        c[:40] += rng.normal(size=40) * 0.8 * np.linspace(1, 0, 40)
        add_at(x, c * a, st, circular=False)
    save_ogg(x, SND / "camcorder" / "flashlight_click.ogg", peak=0.8, fade_in=0.001, fade_out=0.02)


def snd_wake_up() -> None:
    rng = rng_for(1103)
    dur = 3.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    # tinnitus: high pure tone with a faint beating partner, slowly fading
    wob = 1 + 0.08 * np.sin(2 * np.pi * 0.7 * t) + smooth_jitter(rng, n, 3, 0.06)
    tin = (np.sin(2 * np.pi * 4100 * t) + 0.3 * np.sin(2 * np.pi * 4163 * t)
           + 0.12 * np.sin(2 * np.pi * 8200 * t)) * wob
    tin_env = np.clip(t / 0.12, 0, 1) * np.clip(1 - t / dur, 0, 1) ** 1.8
    # muffled world fading back in: low-passed room noise and a dull heartbeat-like thump
    bed = fft_filter(rng.normal(size=n), lambda f: lp_gain(f, 260, 2)) * smoothstep(0.2, 2.4, t)
    thumps = np.zeros(n)
    for st, a in ((0.04, 1.0), (0.38, 0.45)):
        tt = t_axis(0.5)
        f = np.interp(tt, [0, 0.15], [62, 34])
        th = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt / 0.16)
        th += fft_filter(rng.normal(size=tt.size), lambda fr: lp_gain(fr, 180, 2)) * np.exp(-tt / 0.06) * 0.6
        add_at(thumps, th * a, st, circular=False)
    x = 0.32 * norm(tin) * tin_env + 0.25 * norm(bed) + 0.9 * norm(thumps)
    x = x + 0.2 * norm(conv(x, reverb_ir(rng, 0.8, 0.25, lp=2500))[:n]) * np.abs(x).max()
    save_ogg(x, SND / "camcorder" / "wake_up.ogg", peak=0.85, fade_in=0.002, fade_out=0.1)


# =============================================================================
# Facility event sounds
# =============================================================================

def _mains_buzz(phase: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Hum (60 Hz family) and ballast rasp for a given running mains phase (radians)."""
    hum = np.zeros_like(phase)
    for h, a in ((1, 0.35), (2, 1.0), (3, 0.3), (4, 0.45), (6, 0.2), (8, 0.1), (10, 0.06)):
        hum += a * np.sin(h * phase)
    s = np.sin(2 * phase)
    rasp = fft_filter(np.sign(s) * np.abs(s) ** 0.15, lambda f: bp_gain(f, 900, 5000, 2))
    return norm(hum), norm(rasp)


def snd_power_down() -> None:
    rng = rng_for(1201)
    dur = 1.5
    n = int(dur * SR)
    t = np.arange(n) / SR
    t0 = 0.05
    after = np.clip(t - t0, 0, None)
    # the hum winds down in pitch and level after the breaker trips
    fac = np.where(t < t0, 1.0, 0.22 + 0.78 * np.exp(-after / 0.45))
    env = np.where(t < t0, 1.0, np.exp(-after / 0.32))
    hum, rasp = _mains_buzz(2 * np.pi * np.cumsum(60 * fac) / SR)
    on = (t >= t0).astype(float)
    f = np.interp(after, [0, 0.15], [72, 36])
    thud = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-after / 0.13) * on
    knock = fft_filter(rng.normal(size=n), lambda fr: bp_gain(fr, 150, 2500, 2)) * np.exp(-after / 0.025) * on
    metal = np.zeros(n)
    add_at(metal, ring([310, 780, 1450, 2300], [0.14, 0.09, 0.06, 0.035], [1, 0.6, 0.4, 0.2], 0.8),
           t0, circular=False)
    x = (hum * 0.45 * env + rasp * 0.14 * env ** 1.6 + norm(thud) * 0.9
         + norm(knock) * 0.45 + norm(metal) * 0.22)
    x = x + 0.3 * norm(conv(x, reverb_ir(rng, 1.0, 0.3, lp=4000))[:n]) * np.abs(x).max()
    save_ogg(x, SND / "event" / "power_down.ogg", peak=0.89, fade_in=0.01, fade_out=0.08)


def snd_power_up() -> None:
    rng = rng_for(1202)
    dur = 1.5
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    # relay clack: two sharp contacts plus a short body thunk
    for st, a in ((0.02, 1.0), (0.036, 0.6)):
        c = ring([1800, 3700, 5200], [0.006, 0.004, 0.003], [1, 0.7, 0.4], 0.04)
        c += ring([180], [0.015], [0.8], 0.04)
        c[:30] += rng.normal(size=30) * np.linspace(1, 0, 30)
        add_at(x, c * a, st, circular=False)
    hum, rasp = _mains_buzz(2 * np.pi * 60 * t)
    # starter "tink" pings, each with a brief burst of buzz, then the tubes strike
    gate = np.zeros(n)
    for st, a in ((0.34, 0.9), (0.57, 0.7), (0.73, 1.0)):
        p = ring([4200, 6100, 8300], [0.03, 0.02, 0.015], [1, 0.6, 0.3], 0.15)
        add_at(x, p * a * 0.5, st, circular=False)
        gate += np.exp(-0.5 * ((t - st - 0.03) / 0.02) ** 2) * a
    gate += smoothstep(0.86, 1.05, t) * (1 + 0.25 * np.sin(2 * np.pi * 9 * t) * np.exp(-(t - 0.86).clip(0) / 0.2))
    buzz = (hum * 0.5 + rasp * 0.25) * np.clip(gate, 0, 1.3)
    x = norm(x) * 0.8 + norm(buzz) * 0.55
    x = x + 0.2 * norm(conv(x, reverb_ir(rng, 0.8, 0.22, lp=5000))[:n]) * np.abs(x).max()
    save_ogg(x, SND / "event" / "power_up.ogg", peak=0.89, fade_in=0.002, fade_out=0.08)


def snd_alarm() -> None:
    rng = rng_for(1203)
    T = 6.0
    n = int(T * SR)
    t = np.arange(n) / SR
    # hi-lo two-tone; every partial completes whole cycles over T so the loop is seamless
    def tone(f):
        return sum(np.sin(2 * np.pi * f * k * t) / k for k in (1, 3, 5, 7))

    gate = 0.5 + 0.5 * np.tanh(np.sin(2 * np.pi * t) / 0.03)    # 1 Hz, ~5 ms crossfades
    sig = tone(784) * gate + tone(588) * (1 - gate)
    # far away behind concrete: low-passed, big reverberant space
    sig = fft_filter(sig, lambda f: lp_gain(f, 1100, 2) * hp_gain(f, 150, 1), circular=True)
    ir = reverb_ir(rng, 3.5, 1.1, lp=2200, predelay=0.05, early=((0.09, 0.5), (0.21, 0.35), (0.38, 0.25)))
    wet = circ_conv(sig, ir)
    room = periodic_noise(n, rng, lambda f: hp_gain(f, 20, 2) * lp_gain(f, 140, 2))
    x = 0.22 * norm(sig) + norm(wet) + 0.08 * norm(room)
    save_ogg(x, SND / "event" / "alarm.ogg", peak=0.7, loop=True)


# =============================================================================
# Food item textures (16x16)
# =============================================================================

def _px(img, x, y, c) -> None:
    img[y, x] = (*c, 255) if len(c) == 3 else c


def tex_canned_food() -> None:
    """Tin can: steel lid/rim, red label with a cream band."""
    img = np.zeros((16, 16, 4))
    for y in range(3, 15):
        for x in range(4, 12):
            shade = 0.75 + 0.4 * np.sin((x - 3.5) / 8 * np.pi)   # cylinder shading
            if y in (3, 14):
                c = np.array([150, 152, 158]) * shade
            elif 6 <= y <= 12:
                c = (np.array([224, 210, 170]) if y in (8, 9) else np.array([170, 40, 34])) * shade
            else:
                c = np.array([186, 188, 194]) * shade
            _px(img, x, y, tuple(c))
    for x in range(5, 11):                       # lid ellipse
        _px(img, x, 2, (200, 202, 208))
    for x in (4, 11):
        _px(img, x, 2, (110, 112, 118))
    _px(img, 7, 2, (240, 242, 246))
    save_png(img, TEX / "item" / "canned_food.png")


def tex_backshroom() -> None:
    """Pale ochre mushroom with a dome cap and cream stem."""
    img = np.zeros((16, 16, 4))
    cap = [(2, 6, 9), (3, 4, 11), (4, 3, 12), (5, 2, 13), (6, 2, 13), (7, 2, 13)]
    for y, x0, x1 in cap:
        for x in range(x0, x1 + 1):
            shade = 1.08 - 0.08 * (y - 2) - 0.03 * abs(x - 7.5)
            _px(img, x, y, tuple(np.array([196, 160, 74]) * shade))
    for x, y in ((5, 4), (9, 3), (11, 6), (4, 6), (8, 5)):
        _px(img, x, y, (226, 206, 140))
    for x in range(3, 13):
        _px(img, x, 8, (120, 92, 44))            # gill shadow
    for y in range(9, 15):
        for x in range(6, 10):
            _px(img, x, y, tuple(np.array([222, 212, 180]) * (0.85 + 0.05 * x / 4 if x < 8 else 0.85)))
    for x in range(5, 11):
        _px(img, x, 14, (150, 130, 96))
    save_png(img, TEX / "item" / "backshroom.png")


# =============================================================================
# v2 sounds: fixture hum/blinks, footsteps, floor events, transitions
# =============================================================================

def snd_light_hum() -> None:
    """Seamless 4 s positional fixture hum: calm 120 Hz transformer hum, no buzz/whine."""
    rng = rng_for(1301)
    T = 4.0
    n = int(T * SR)
    t = np.arange(n) / SR                    # 480 whole cycles of 120 Hz -> periodic
    hum = mains_hum(t) + 0.06 * np.sin(2 * np.pi * 60 * t)      # touch of 60 Hz body
    bed = periodic_noise(n, rng, lambda f: bp_gain(f, 60, 250, 2))
    x = (norm(hum) + 0.01 * norm(bed)) * drift(n, rng, 0.1, 0.2, 0.5)
    save_ogg(x, SND / "light" / "hum.ogg", peak=0.57, loop=True)


def snd_light_blink() -> None:
    """8 relay clicks / starter tinks, some with a short ballast buzz."""
    for i in range(1, 9):
        rng = rng_for(1310 + i)
        dur = 0.55
        n = int(dur * SR)
        x = np.zeros(n)
        nclick = 1 + i % 3
        tt = 0.01
        for _ in range(nclick):
            k = int(0.004 * SR)
            burst = rng.normal(size=k) * np.exp(-np.arange(k) / (0.0008 * SR))
            burst = fft_filter(burst, lambda f: hp_gain(f, 1500, 2))[:k]
            tink = ring(rng.uniform(2500, 5200, 3), rng.uniform(0.01, 0.05, 3), (1, 0.6, 0.4), 0.15)
            body = ring([rng.uniform(300, 600)], [0.01], [0.8], 0.05)
            add_at(x, norm(burst), tt, False)
            add_at(x, 0.35 * norm(tink) * rng.uniform(0.4, 1.0), tt, False)
            add_at(x, 0.4 * body, tt, False)
            tt += rng.uniform(0.03, 0.12)
        if i % 2 == 0:                     # brief ballast buzz as the tube strikes
            m = int(rng.uniform(0.06, 0.18) * SR)
            ph = 2 * np.pi * 60 * np.arange(m) / SR
            s = np.sin(2 * ph)
            bz = fft_filter(np.sign(s) * np.abs(s) ** 0.2, lambda f: bp_gain(f, 300, 5000, 1))[:m]
            add_at(x, 0.3 * norm(bz) * env_adsr(m, 0.005, 0.03), tt, False)
        ir = reverb_ir(rng, 0.4, 0.08, lp=7000)
        x = x + 0.25 * conv(x, ir)[:n]
        save_ogg(x, SND / "light" / f"blink{i}.ogg", peak=0.85, fade_in=0.0005, fade_out=0.05)


VENDOR = Path(__file__).resolve().parent / "vendor"


def _load_mono(path: Path) -> np.ndarray:
    x, sr = sf.read(str(path))
    assert sr == SR, (path, sr)
    return x.mean(axis=1) if x.ndim == 2 else x


def _trim(x, thresh_db=-42.0, pre=0.002) -> np.ndarray:
    """Cut leading/trailing silence (relative to the peak), keeping a tiny pre-roll."""
    loud = np.nonzero(np.abs(x) > np.abs(x).max() * 10 ** (thresh_db / 20))[0]
    return x[max(0, loud[0] - int(pre * SR)): loud[-1] + 1]


def _repitch(x, ratio) -> np.ndarray:
    """Resample by ratio (>1 = higher/shorter) with linear interpolation."""
    idx = np.arange(0, len(x) - 1, ratio)
    return np.interp(idx, np.arange(len(x)), x)


def snd_footsteps() -> None:
    """Real footstep recordings (Kenney Impact Sounds, CC0; see CREDITS.md).

    Walk: lowpassed ~4 kHz, quieter (-6 dB), slight pitch/level variation.
    Run:  full band, a little low-end thump under the impact, louder.
    """
    for surface in ("carpet", "concrete"):
        for i in range(1, 6):
            src = _trim(_load_mono(VENDOR / "kenney_impact" / f"footstep_{surface}_{i - 1:03d}.ogg"))
            for run in (False, True):
                rng = rng_for(1400 + (20 if surface == "concrete" else 0) + (10 if run else 0) + i)
                x = _repitch(src, rng.uniform(0.95, 1.04))
                if run:
                    x = np.concatenate([x, np.zeros(int(0.05 * SR))])
                    tt = np.arange(len(x)) / SR
                    thump = np.sin(2 * np.pi * rng.uniform(60, 80) * tt) * np.exp(-tt / 0.035)
                    thump *= smoothstep(0, 0.003, tt)
                    x = norm(x) + 0.3 * thump
                    peak = 0.8
                else:
                    x = fft_filter(x, lambda f: lp_gain(f, 4000, 2))[:len(x)]
                    peak = 0.4 * 10 ** (rng.uniform(-1, 1) / 20)     # -6 dB, +/-1 dB variation
                kind = "run" if run else "walk"
                save_ogg(x, SND / "step" / f"{surface}_{kind}{i}.ogg", peak=peak,
                         fade_in=0.001, fade_out=0.015)


INTERCOM_LINES = (
    ("Attention. Please report to the front desk.", "Microsoft Zira Desktop", -1, True),
    ("Will the last person remaining, please return to their station.", "Microsoft David Desktop", -2, True),
    ("Thank you for your patience. The exit is temporarily unavailable.", "Microsoft Zira Desktop", -1, True),
    ("Is anyone there?", "Microsoft David Desktop", -4, False),
)


def _sapi(text: str, voice_name: str, rate: int) -> np.ndarray:
    """Speak text with Windows SAPI (System.Speech) into a 44.1 kHz mono wav and load it."""
    import subprocess
    import tempfile
    with tempfile.TemporaryDirectory() as td:
        wav = Path(td) / "speech.wav"
        ps = ("Add-Type -AssemblyName System.Speech;"
              "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer;"
              f"try {{ $s.SelectVoice('{voice_name}') }} catch {{}};"
              f"$s.Rate = {rate};"
              "$fmt = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(44100,"
              " [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,"
              " [System.Speech.AudioFormat.AudioChannel]::Mono);"
              f"$s.SetOutputToWaveFile('{wav}', $fmt);"
              f"$s.Speak('{text}'); $s.Dispose()")
        subprocess.run(["powershell", "-NoProfile", "-Command", ps], check=True, capture_output=True)
        x, sr = sf.read(str(wav))
    assert sr == SR, sr
    return np.asarray(x, float)


def _chime(rng) -> np.ndarray:
    """Two-tone PA 'ding-dong' (bell partials)."""
    out = np.zeros(int(1.6 * SR))
    for start, f in ((0.0, 659.3), (0.45, 523.3)):
        bell = ring([f, 2 * f, 3.01 * f, 4.2 * f], [0.9, 0.5, 0.3, 0.15], [1, 0.35, 0.15, 0.08], 1.1)
        add_at(out, bell * env_adsr(len(bell), 0.004, 0.05), start, False)
    return norm(out)


def snd_intercom() -> None:
    """PA announcements: chime + SAPI speech through a tinny, distorted horn and a big room."""
    for i, (text, voice_name, rate, chime) in enumerate(INTERCOM_LINES, 1):
        rng = rng_for(1500 + i)
        speech = norm(_sapi(text, voice_name, rate))
        lead = 1.4 if chime else 0.3
        tail = 3.5
        n = int((lead + len(speech) / SR + tail) * SR)
        dry = np.zeros(n)
        if chime:
            add_at(dry, 0.6 * _chime(rng), 0.05, False)
        add_at(dry, speech, lead, False)
        # tinny horn: telephone band, resonant peak, soft clipping, slight crush
        dry = fft_filter(dry, lambda f: bp_gain(f, 300, 3400, 3) * (1 + 1.5 * np.exp(-((f - 1800) / 400) ** 2)))[:n]
        dry = dist(norm(dry) * 1.0, 3.0 if i < 4 else 5.0)
        dry = bitcrush(dry, 10, 2)
        hiss = fft_filter(rng.normal(size=n), lambda f: bp_gain(f, 400, 4000, 1))[:n]
        dry = norm(dry) + 0.015 * norm(hiss) * (np.arange(n) < n - int(1.0 * SR))
        ir = reverb_ir(rng, 4.0, 0.9, lp=3000, predelay=0.03, early=((0.07, 0.5), (0.16, 0.3), (0.29, 0.2)))
        wet = conv(dry, ir)[:n]
        x = norm(dry) * 0.7 + norm(wet) * 0.55
        save_ogg(x, SND / "event" / f"intercom{i}.ogg", peak=0.89, fade_in=0.01, fade_out=0.5)


def snd_distant_knock() -> None:
    for i in range(1, 4):
        rng = rng_for(1600 + i)
        n = int(3.5 * SR)
        dry = np.zeros(n)
        tt = 0.05
        for _ in range(2 + i):
            k = ring([rng.uniform(95, 125), rng.uniform(170, 200), rng.uniform(250, 300)],
                     [0.07, 0.05, 0.03], [1, 0.6, 0.4], 0.3)
            m = len(k)
            hit = fft_filter(rng.normal(size=m) * np.exp(-np.arange(m) / (0.004 * SR)), lambda f: lp_gain(f, 1500, 2))[:m]
            add_at(dry, (norm(k) + 0.5 * norm(hit)) * rng.uniform(0.7, 1.0), tt, False)
            tt += rng.uniform(0.2, 0.32)
        dry = fft_filter(dry, lambda f: lp_gain(f, 1200, 2))[:n]       # far away
        wet = conv(dry, reverb_ir(rng, 3.0, 0.7, lp=1800, predelay=0.04))[:n]
        save_ogg(0.5 * norm(dry) + norm(wet), SND / "event" / f"distant_knock{i}.ogg",
                 peak=0.85, fade_out=0.4)


def snd_distant_creak() -> None:
    for i in range(1, 4):
        rng = rng_for(1700 + i)
        dur = 2.2 + 0.6 * i
        n = int(dur * SR)
        t = np.arange(n) / SR
        # stick-slip friction: pulse train whose rate wanders, through wood/metal resonances
        rate = np.interp(t, [0, dur * 0.3, dur * 0.7, dur], [18, rng.uniform(40, 70), rng.uniform(25, 45), 15])
        rate *= 1 + smooth_jitter(rng, n, 6, 0.3)
        ph = np.cumsum(rate) / SR
        pulses = (np.diff(np.floor(ph), prepend=0) > 0) * rng.uniform(0.5, 1.0, n)
        res = ring([rng.uniform(300, 420), rng.uniform(700, 900), rng.uniform(1300, 1700), 2400],
                   [0.03, 0.02, 0.012, 0.008], [1, 0.8, 0.5, 0.3], 0.12)
        dry = conv(pulses, res)[:n] * env_adsr(n, 0.2, 0.5)
        dry = fft_filter(dry, lambda f: lp_gain(f, 2500, 2))[:n]
        wet = conv(dry, reverb_ir(rng, 3.0, 0.8, lp=2000, predelay=0.03))
        out = np.zeros(len(wet))
        out[:n] += 0.5 * norm(dry)
        out += norm(wet)
        save_ogg(out[: n + int(2.0 * SR)], SND / "event" / f"distant_creak{i}.ogg", peak=0.85, fade_out=0.6)


def snd_thunder() -> None:
    for i in range(1, 3):
        rng = rng_for(1800 + i)
        dur = 7.0 + i
        n = int(dur * SR)
        t = np.arange(n) / SR
        env = np.zeros(n)
        for _ in range(7):                      # rolling swells
            c = rng.uniform(0.3, dur * 0.55)
            env += rng.uniform(0.4, 1.0) * np.exp(-0.5 * ((t - c) / rng.uniform(0.2, 0.9)) ** 2)
        env *= np.exp(-t / (dur * 0.45)) * (1 + smooth_jitter(rng, n, 20, 0.5))
        rumble = fft_filter(rng.normal(size=n), lambda f: lp_gain(f, 160, 3) * hp_gain(f, 25, 2))[:n]
        mid = fft_filter(rng.normal(size=n), lambda f: bp_gain(f, 200, 900, 2))[:n]
        x = norm(rumble) * env + 0.25 * norm(mid) * env ** 2
        if i == 1:                              # close crack at the start
            k = int(0.6 * SR)
            crack = rng.normal(size=k) * np.exp(-np.arange(k) / (0.08 * SR))
            crack = fft_filter(crack, lambda f: bp_gain(f, 300, 5000, 1))[:k]
            add_at(x, 0.8 * norm(crack), 0.12, False)
        x = x + 0.4 * conv(x, reverb_ir(rng, 3.0, 1.0, lp=1500))[:n]
        save_ogg(x, SND / "event" / f"thunder{i}.ogg", peak=0.89, fade_in=0.02, fade_out=1.0)


def snd_radio_static() -> None:
    """Seamless 4 s loop: band-limited hiss, flutter, crackle, wandering heterodyne whistle."""
    rng = rng_for(1901)
    T = 4.0
    n = int(T * SR)
    t = np.arange(n) / SR
    hiss = periodic_noise(n, rng, lambda f: bp_gain(f, 400, 5000, 2))
    flutter = 0.75 + 0.15 * np.sin(2 * np.pi * 3 * t / T) + 0.1 * np.sin(2 * np.pi * 37 * t / T)
    crack = np.zeros(n)
    for _ in range(60):
        k = int(rng.uniform(0.0005, 0.004) * SR)
        add_at(crack, rng.normal(size=k) * rng.uniform(0.3, 1.0), rng.uniform(0, T), True)
    whistle = np.sin(2 * np.pi * 1300 * t - 220 * T / 2 * np.cos(2 * np.pi * 2 * t / T) / np.pi)
    x = norm(hiss) * flutter + 0.6 * norm(crack) + 0.04 * whistle
    x = fft_filter(x, lambda f: bp_gain(f, 250, 6000, 1), circular=True)
    save_ogg(x, SND / "event" / "radio_static.ogg", peak=0.8, loop=True)


def snd_death_sting() -> None:
    """Harsh stinger: boom + dissonant distorted cluster + screeching glide + big room."""
    rng = rng_for(2101)
    dur = 4.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    boom = np.sin(2 * np.pi * np.cumsum(30 + 60 * np.exp(-t / 0.15)) / SR) * np.exp(-t / 0.6)
    hit = fft_filter(rng.normal(size=n) * np.exp(-t / 0.05), lambda f: lp_gain(f, 4000, 1))[:n]
    cluster = np.zeros(n)
    for f in (110.0, 116.5, 155.6, 164.8, 233.1, 246.9):
        ph = 2 * np.pi * np.cumsum(f * (1 + 0.004 * np.sin(2 * np.pi * rng.uniform(4, 7) * t))) / SR
        cluster += 2 * (ph / (2 * np.pi) % 1) - 1          # saw
    cluster = dist(norm(cluster) * 1.5, 4.0) * np.exp(-t / 1.4) * np.clip(t / 0.02, 0, 1)
    glide = 1500 * np.exp(-t / 1.5) + 700
    scr = np.sin(2 * np.pi * np.cumsum(glide) / SR) + 0.5 * np.sin(2 * np.pi * np.cumsum(glide * 1.06) / SR)
    scr = dist(scr, 3.0) * np.clip(t / 0.05, 0, 1) * np.exp(-t / 1.0)
    x = norm(boom) + 0.6 * norm(hit) + 0.7 * cluster + 0.25 * scr
    x = x + 0.5 * conv(x, reverb_ir(rng, 3.0, 0.9, lp=5000))[:n]
    save_ogg(x, SND / "death" / "sting.ogg", peak=0.9, fade_in=0.001, fade_out=0.6)


# =============================================================================
# Intro (noclip into Level 0) sounds
# =============================================================================

def snd_intro_glitch() -> None:
    """1.5 s: camcorder tape warble + digital crackle + low drone swell (reality breaking)."""
    rng = rng_for(2201)
    dur = 1.5
    n = int(dur * SR)
    t = np.arange(n) / SR
    # tape warble: a soft chord whose speed wobbles (wow & flutter) and sags at the end
    speed = 1 + 0.03 * np.sin(2 * np.pi * 5.5 * t) + 0.015 * np.sin(2 * np.pi * 13 * t) - 0.25 * (t / dur) ** 3
    ph = 2 * np.pi * np.cumsum(speed) / SR
    chord = sum(a * np.sin(f * ph) for f, a in ((220, 1.0), (277.2, 0.6), (329.6, 0.5), (110, 0.7)))
    hiss = fft_filter(rng.normal(size=n), lambda f: bp_gain(f, 800, 6000, 1))[:n]
    tape = norm(chord) * 0.6 + norm(hiss) * 0.12
    tape = fft_filter(tape, lambda f: lp_gain(f, 3500, 2))[:n] * smoothstep(0, 0.1, t)
    # digital crackle: short bit-crushed / sample-held bursts and dropouts
    crack = np.zeros(n)
    gate = np.ones(n)
    for _ in range(14):
        st = rng.uniform(0.15, dur - 0.1)
        m = int(rng.uniform(0.01, 0.06) * SR)
        seg = bitcrush(rng.normal(size=m) * 0.6 + np.sign(np.sin(2 * np.pi * rng.uniform(300, 2000) * np.arange(m) / SR)) * 0.4,
                       3, int(rng.integers(4, 30)))
        add_at(crack, seg * rng.uniform(0.3, 1.0), st, False)
        i0 = int(st * SR)
        gate[i0:i0 + m // 2] *= 0.2                               # tape drops out under the burst
    gate = fft_filter(gate, lambda f: lp_gain(f, 300, 1))[:n]
    drone = sum(np.sin(2 * np.pi * f * t + p) for f, p in ((41.2, 0), (55.0, 1.0), (82.4, 2.0)))
    drone = norm(drone) * smoothstep(0.1, dur, t) ** 1.5
    x = tape * gate + 0.45 * norm(crack) + 0.7 * drone
    save_ogg(x, SND / "intro" / "glitch.ogg", peak=0.85, fade_in=0.01, fade_out=0.04)


def snd_intro_impact() -> None:
    """1.2 s: body thud on carpet + muffled crunch of ceiling tiles + short low boom."""
    rng = rng_for(2202)
    dur = 1.2
    n = int(dur * SR)
    t = np.arange(n) / SR
    thud = fft_filter(rng.normal(size=n) * np.exp(-t / 0.05), lambda f: lp_gain(f, 500, 2))[:n]
    body = np.sin(2 * np.pi * np.cumsum(55 + 50 * np.exp(-t / 0.04)) / SR) * np.exp(-t / 0.12)
    crunch = np.zeros(n)
    for _ in range(90):                                        # tile fragments, muffled
        st = 0.01 + rng.exponential(0.06)
        m = int(rng.uniform(0.002, 0.01) * SR)
        g = rng.normal(size=m) * np.exp(-np.arange(m) / (m / 3))
        add_at(crunch, g * rng.uniform(0.2, 1.0) * np.exp(-st / 0.12), st, False)
    crunch = fft_filter(crunch, lambda f: bp_gain(f, 300, 2200, 2))[:n]
    boom = np.sin(2 * np.pi * 38 * t) * np.exp(-t / 0.35) * smoothstep(0, 0.01, t)
    x = norm(thud) + 0.8 * norm(body) + 0.35 * norm(crunch) + 0.7 * norm(boom)
    x = x + 0.15 * conv(x, reverb_ir(rng, 0.8, 0.25, lp=2500))[:n]
    save_ogg(x, SND / "intro" / "impact.ogg", peak=0.9, fade_in=0.001, fade_out=0.2)


def snd_intro_wind() -> None:
    """2.5 s low, airy falling-through-darkness rush: fades in, peaks at the 1.6 s landing."""
    rng = rng_for(2203)
    dur = 2.5
    n = int(dur * SR)
    t = np.arange(n) / SR
    noise = rng.normal(size=n)
    fc = 220 + 380 * (t / dur) ** 1.3 * (1 + smooth_jitter(rng, n, 1.5, 0.3))
    air = norm(svf(noise, fc, 1.2)) + 0.4 * norm(svf(noise, fc * 2.2, 2.5))
    low = norm(fft_filter(rng.normal(size=n), lambda f: lp_gain(f, 110, 2))[:n])
    # quick fade-in, build until the landing at 1.6 s, then trail off under the impact
    shape = smoothstep(0, 0.5, t) * (0.6 + 0.4 * smoothstep(0, 1.6, t)) * np.exp(-np.clip(t - 1.6, 0, None) / 0.35)
    env = shape * (0.85 + 0.15 * (1 + smooth_jitter(rng, n, 3, 1.0)))
    x = (air + 0.6 * low) * env
    x = fft_filter(x, lambda f: lp_gain(f, 2500, 2))[:n]
    save_ogg(x, SND / "intro" / "wind.ogg", peak=0.8, fade_in=0.05, fade_out=0.15)


# =============================================================================
# Poolrooms sounds
# =============================================================================

def snd_poolrooms_ambience() -> None:
    """12 s loop: big tiled hall, long bright reverb, gentle lapping, low filter hum, rare drip."""
    rng = rng_for(2301)
    T = 12.0
    n = int(T * SR)
    t = np.arange(n) / SR
    # lapping: band-passed noise with slow, irregular (but periodic) swell envelopes
    lap = periodic_noise(n, rng, lambda f: bp_gain(f, 180, 1100, 2))
    lap_env = np.zeros(n)
    for k in range(9):
        c, w = rng.uniform(0, T), rng.uniform(0.25, 0.6)
        lap_env += rng.uniform(0.4, 1.0) * np.exp(-0.5 * (wrapd(t - c, T) / w) ** 2)
    lap = lap * (0.15 + lap_env)
    slosh = periodic_noise(n, rng, lambda f: bp_gain(f, 60, 250, 2)) * drift(n, rng, 0.5, 0.1, 0.4)
    # pool filter pump: smooth low hum (whole cycles over T) under a soft noise bed
    hum = mains_hum(t, f0=50.0, levels_db=(0, -14, -24, -34)) * drift(n, rng, 0.1, 0.1, 0.3)
    room = periodic_noise(n, rng, lambda f: hp_gain(f, 25, 2) * lp_gain(f, 400, 2) / np.maximum(f, 25))
    # one soft distant drip
    drips = np.zeros(n)
    dt = t_axis(0.12)
    d = np.sin(2 * np.pi * np.cumsum(900 * (1 + 0.6 * np.clip(dt / 0.05, 0, 1))) / SR)
    add_at(drips, d * np.exp(-dt / 0.025) * smoothstep(0, 0.004, dt), 7.3, circular=True)
    drips = fft_filter(drips, lambda f: lp_gain(f, 1800, 2), circular=True)
    events = norm(lap) * 0.6 + norm(drips) * 0.5
    # long bright tiled-hall reverb (circular -> the tail wraps across the loop seam)
    ir = reverb_ir(rng, 4.5, 1.6, lp=3200, predelay=0.04, early=((0.05, 0.6), (0.11, 0.4), (0.19, 0.3)))
    wet = circ_conv(events, ir)
    x = (norm(lap) * 0.25 + norm(slosh) * 0.15 + norm(wet) * 0.6 + norm(hum) * 0.12 + norm(room) * 0.25)
    save_ogg(x, SND / "ambient" / "poolrooms_ambience.ogg", peak=0.55, loop=True)


def snd_hallucination_loop() -> None:
    """8 s loop: muffled dream drone, slow chorus wobble, wordless formant 'voice' swells."""
    rng = rng_for(2302)
    T = 8.0
    n = int(T * SR)
    t = np.arange(n) / SR
    # drone: detuned voices whose frequencies are multiples of 1/T -> exactly periodic
    drone = np.zeros(n)
    for f, a in ((55.0, 1.0), (82.5, 0.6), (110.0, 0.5), (130.875, 0.35), (165.0, 0.25)):
        for det in (-0.25, 0.0, 0.375):                      # chorus: slow beating
            drone += a * np.sin(2 * np.pi * (f + det) * t + rng.uniform(0, 6.28))
    drone *= drift(n, rng, 0.2, 0.125, 0.5)
    # formant swells: harmonics of 110 Hz weighted by slowly gliding vowel formants
    voice_sig = np.zeros(n)
    f1 = 500 + 200 * np.sin(2 * np.pi * t / T) + 80 * np.sin(2 * np.pi * 3 * t / T + 1)
    f2 = 1100 + 450 * np.sin(2 * np.pi * 2 * t / T + 2)
    # 2.5 Hz vibrato (+/-0.4 % pitch) as a periodic time warp: integral of 0.004*sin(2*pi*2.5*t)
    warp = -0.004 / (2 * np.pi * 2.5) * np.cos(2 * np.pi * 2.5 * t)
    for h in range(1, 18):
        fh = 110.0 * h
        w = np.exp(-0.5 * ((fh - f1) / 90) ** 2) + 0.6 * np.exp(-0.5 * ((fh - f2) / 120) ** 2)
        voice_sig += w / h ** 0.3 * np.sin(2 * np.pi * fh * (t + warp))
    swell = sum(np.exp(-0.5 * (wrapd(t - c, T) / wd) ** 2) for c, wd in ((1.6, 0.7), (5.2, 0.9), (6.6, 0.4)))
    voice_sig = norm(voice_sig) * swell
    water = periodic_noise(n, rng, lambda f: lp_gain(f, 300, 2) / np.maximum(f, 30))
    x = norm(drone) * 0.6 + voice_sig * 0.35 + norm(water) * 0.3
    x = fft_filter(x, lambda f: lp_gain(f, 700, 3), circular=True)              # underwater muffle
    wet = circ_conv(x, reverb_ir(rng, 3.0, 1.2, lp=900))
    save_ogg(norm(x) * 0.7 + norm(wet) * 0.5, SND / "poolrooms" / "hallucination_loop.ogg", peak=0.55, loop=True)


def snd_phantom_vanish() -> None:
    """~2.2 s: reversed soft splash swelling into an airy shimmer that cuts off, tiled tail."""
    rng = rng_for(2303)
    dur = 2.2
    n = int(dur * SR)
    t = np.arange(n) / SR
    cut = 0.75
    m = int(cut * SR)
    tm = t[:m]
    splash = rng.normal(size=m) * np.exp(-tm / 0.18)
    splash = fft_filter(splash, lambda f: bp_gain(f, 300, 3500, 1))[:m][::-1]      # reversed
    shimmer = np.zeros(m)
    for f in (1318.5, 1567.98, 1975.5, 2637.0):
        shimmer += np.sin(2 * np.pi * f * tm * (1 + 0.003 * np.sin(2 * np.pi * 6 * tm))) / (f / 1318.5)
    shimmer *= smoothstep(0.2, cut, tm) ** 2
    air = fft_filter(rng.normal(size=m), lambda f: bp_gain(f, 2000, 6000, 1))[:m] * smoothstep(0.3, cut, tm)
    dry = np.zeros(n)
    dry[:m] = norm(splash) * 0.8 + norm(shimmer) * 0.35 + norm(air) * 0.12
    dry[m - int(0.006 * SR):m] *= np.linspace(1, 0, int(0.006 * SR))              # hard-ish cut
    wet = conv(dry, reverb_ir(rng, 2.5, 0.8, lp=4500, predelay=0.03, early=((0.05, 0.5), (0.12, 0.3))))[:n]
    save_ogg(norm(dry) * 0.8 + norm(wet) * 0.6, SND / "poolrooms" / "phantom_vanish.ogg",
             peak=0.85, fade_in=0.02, fade_out=0.3)


def snd_drain_pull() -> None:
    """~3 s: deep gurgling suction whoosh of a big drain, bubbles, low rumble."""
    rng = rng_for(2304)
    dur = 3.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    noise = rng.normal(size=n)
    fc = 120 + 380 * np.exp(-((t - 1.2) / 0.8) ** 2) * (1 + smooth_jitter(rng, n, 4, 0.4))
    whoosh = norm(svf(noise, fc, 2.0))
    gurgle_env = 0.5 + 0.5 * np.clip(smooth_jitter(rng, n, 9, 1.0), -1, 1)
    gurgle = norm(svf(rng.normal(size=n), 260 + 120 * smooth_jitter(rng, n, 6, 1.0), 6.0)) * gurgle_env
    bubbles = np.zeros(n)
    for _ in range(45):
        st = rng.uniform(0.1, dur - 0.3)
        bd = rng.uniform(0.03, 0.08)
        bt = t_axis(bd)
        f0 = rng.uniform(150, 600)
        b = np.sin(2 * np.pi * np.cumsum(f0 * (1 + 1.5 * bt / bd)) / SR) * np.exp(-bt / (bd / 3))
        add_at(bubbles, b * rng.uniform(0.3, 1.0), st, False)
    rumble = norm(fft_filter(rng.normal(size=n), lambda f: bp_gain(f, 25, 90, 2))[:n])
    env = smoothstep(0, 0.4, t) * (1 - smoothstep(2.2, dur, t))
    x = (whoosh + 0.6 * gurgle + 0.35 * norm(bubbles) + 0.9 * rumble) * env
    x = fft_filter(x, lambda f: lp_gain(f, 2200, 2))[:n]
    x = x + 0.3 * conv(x, reverb_ir(rng, 1.5, 0.5, lp=1500))[:n]
    save_ogg(x, SND / "poolrooms" / "drain_pull.ogg", peak=0.88, fade_in=0.02, fade_out=0.3)


# =============================================================================

TEXTURES = [
    remove_stale,
    tex_yellow_wallpaper, tex_yellow_wallpaper_baseboard, tex_lobby_carpet, tex_lobby_carpet_arrow,
    tex_ceiling_tile, tex_fluorescent_light, tex_fluorescent_light_off, tex_wall_decals, tex_rug,
    tex_lobby_door, tex_red_alarm_lamp,
    tex_concrete_wall, tex_garage_pillar, tex_pillar_numbers, tex_concrete_floor, tex_wet_concrete_floor,
    tex_warehouse_light, tex_ceiling_tube, tex_ceiling_beam,
    tex_pipe_wall, tex_pipe_floor, tex_pipe_ceiling, tex_pipe_light, tex_rusty_pipe, tex_pipe_bracket,
    tex_pale_duct, tex_pale_duct_flange, tex_pipe_valve, tex_floor_grate,
    tex_bulkhead_lamp, tex_bulkhead_cage,
    tex_stairwell_wall, tex_chain_link, tex_stair_tread, tex_exit_sign, tex_exit_door,
    tex_supply_crate, tex_supply_crate_top,
    tex_pool_tile, tex_pool_tile_dark, tex_pool_floor, tex_pool_skylight, tex_pool_lamp,
    tex_camcorder_battery, tex_almond_water, tex_canned_food, tex_backshroom, tex_bacteria,
]
SOUNDS = [
    snd_level0_hum, snd_level1_ambience, snd_level2_ambience,
    snd_bacteria_ambient, snd_bacteria_screech, snd_bacteria_steps, snd_bacteria_hurt,
    snd_bacteria_death, snd_bacteria_search, snd_bacteria_lost, snd_rec_beep, snd_battery_low, snd_noclip, snd_escape,
    snd_flashlight_click, snd_wake_up, snd_power_down, snd_power_up, snd_alarm,
    snd_light_hum, snd_light_blink, snd_footsteps, snd_intercom, snd_distant_knock,
    snd_distant_creak, snd_thunder, snd_radio_static, snd_death_sting,
    snd_intro_glitch, snd_intro_impact, snd_intro_wind,
    snd_poolrooms_ambience, snd_hallucination_loop, snd_phantom_vanish, snd_drain_pull,
]


def main() -> None:
    import sys
    filters = sys.argv[1:]
    for fn in TEXTURES + SOUNDS:
        if filters and not any(f in fn.__name__ for f in filters):
            continue
        fn()
        print("ok", fn.__name__)


if __name__ == "__main__":
    main()
