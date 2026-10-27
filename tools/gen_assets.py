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
# Kane Pixels' Lobby, as in his "Found Footage" frames. Colours are albedos for the
# Level 0 lights, which are near-white with a faint cool tint, plus the camcorder grade that cuts
# blue and adds saturation; so the textures sit a touch LESS saturated than the colours on screen
# (wallpaper ~#B7A746, carpet ~#A08534, ceiling ~#D8D0B0 once lit and graded). Wallpaper, carpet
# and ceiling stay within ~15% of each other: the place reads as one flat yellow.

WALLPAPER = (189, 175, 99)    # the pale field; with the darker print on top the paper averages ~(184,170,96), near #B7A746 lit
CARPET = (142, 120, 64)       # browner and ~0.75x the wall's brightness (floor tops get full face light); ~#A3893A on screen
CEILING = (214, 207, 182)
TBAR = (190, 184, 164)        # the painted steel T-bar grid of the dropped ceiling


def _paper(rng, base, shape=(S, S)) -> np.ndarray:
    """Plain matte paper in one colour: a very faint soft unevenness and fibre grain, so a flat
    field never reads as a flat fill."""
    lum = (1.2 * fnoise(rng, shape, beta=3.0, kmin=1, kmax=3)
           + 0.9 * blur(fnoise(rng, shape, beta=0.8, kmin=24), 0.6)
           + 0.6 * rng.normal(0, 1, shape))
    return add_lum(fill(base, shape), lum - lum.mean(), (1.0, 0.97, 0.85))


def _thread_noise(rng, amp, sigma=0.7) -> np.ndarray:
    """One random offset per pixel column (lightly smoothed, wraps round): the dyed threads of an
    ikat each stop at a slightly different height, which gives its edges their ragged look."""
    k = np.fft.fftfreq(S)
    a = np.real(np.fft.ifft(np.fft.fft(rng.normal(0, 1, S)) * np.exp(-2 * (np.pi * sigma * k) ** 2)))
    return amp * a / a.std()


def _flame(U, V):
    """The soft "tulip/flame" shape of the stripe columns (U -1..1 across, V 0..1 top to point):
    widest just below its notched top, narrowing to a point."""
    half = np.where(V < 0.3, 0.7 + V, np.clip(1.0 - (V - 0.3) / 0.7, 0, 1))
    notch = (V < 0.3 - 0.8 * np.abs(U)) & (np.abs(U) < 0.3)
    return (V > 0) & (V < 1) & (np.abs(U) < half) & ~notch


def _wallpaper_design(seed=101) -> np.ndarray:
    """Where the print is (0..1) on the real Backrooms paper (the one the A24 film printed).

    Across the paper repeat stripe bands and plain panels, two of each per block (64 px each):
    - a band: a thin line and a pair of thin lines, a column of light "flame/tulip" shapes left
      in a dark stripe (three per block height), then a pair of lines and a thin line again;
    - a panel: feathered ikat chevrons, an outer ^ with an inner ^ below it, their edges ragged
      thread by thread, with spiky tips above and long threads running down below.
    The chevrons of the second panel sit half a repeat lower (staggered, as on the real paper).
    Why two repeats per block and not one: a block texture has to tile with itself, so the
    half-repeat stagger needs both panels inside the block.
    """
    rng = rng_for(seed)
    X, Y = grid()
    P = 64.0
    xb = X % P
    m = np.zeros((S, S))

    def vline(x0, w):
        return np.clip(np.minimum(xb - x0 + 0.5, x0 + w - xb + 0.5), 0, 1)

    for x0 in (0.5, 3.5, 5.5, 23.0, 25.0, 28.0):                        # the thin lines
        m = np.maximum(m, vline(x0, 1.0))
    c0, c1 = 9.0, 19.5                                                   # the flame column
    cx, tp = (c0 + c1) / 2, S / 3
    flames = supersample((S, S), lambda x, y: _flame(((x % P) - cx) / 4.0, ((y % tp) - 5) / (tp * 0.62)))
    m = np.maximum(m, vline(c0, c1 - c0) * (1 - 0.95 * flames))
    p0, p1 = 30.0, 63.0                                                  # the chevron panel
    pc, hw = (p0 + p1) / 2, (p1 - p0) / 2
    ys = (Y + np.floor(X / P) * 64) % S                                  # the second panel half a repeat lower
    ax = np.abs(xb - pc)
    chev = np.zeros((S, S))
    for apex, thick in ((10.0, 15.0), (39.0, 14.0)):                     # outer ^, inner ^ below it
        top = _thread_noise(rng, 1.3) - np.clip(_thread_noise(rng, 1.0), 0.6, None) * 4 + 2.4
        bottom = thick + _thread_noise(rng, 1.6) + np.clip(_thread_noise(rng, 1.0), 0.7, None) * 5 - 3.5
        t = ys - (apex + 1.4 * ax)
        chev = np.maximum(chev, ((t > top[None, :]) & (t < bottom[None, :]) & (ax < hw)).astype(float))
    flecks = fnoise(rng, beta=0.8, aniso=(1, 6), kmin=10) > 1.5          # where the dye didn't take
    chev = chev * (1 - 0.55 * flecks) * ((xb > p0) & (xb < p1))
    return np.clip(np.maximum(m, chev), 0, 1)


def _wallpaper(stains=True, base=WALLPAPER, seed=101) -> np.ndarray:
    """The Lobby wallpaper: the real Backrooms design (see _wallpaper_design), printed tone on
    tone in a yellow about 10% darker and a little more saturated than the pale field. Up close
    it reads as a printed pattern, from across a hall as soft vertical stripes.

    stains=True adds two faint old water marks (used by drawings and anomaly blocks that are
    based on the paper); the wall blocks use the clean paper and get their marks from the random
    variants instead (see Surface variants), so the same mark doesn't repeat on every block.
    """
    rng = rng_for(seed + 1)
    img = _paper(rng, base)
    ink = img * np.array([0.90, 0.895, 0.84])                           # the print: ~10% darker, a bit more saturated
    img = lerp(img, ink, _wallpaper_design(seed)[..., None])
    if stains:
        stain_col = np.array([150, 126, 62])
        for cx, cy, sx, sy, a in ((34, 30, 18, 22, 0.08), (94, 88, 14, 16, 0.06)):
            m = blob((S, S), cx, cy, sx, sy) * (1 + 0.35 * fnoise(rng, beta=2.0))
            rim = smoothstep(0.28, 0.36, m) - smoothstep(0.36, 0.48, m)
            img = over(img, stain_col, a * smoothstep(0.35, 0.6, m) + 1.4 * a * np.clip(rim, 0, 1))
    return img


def tex_yellow_wallpaper() -> None:
    save_png(_wallpaper(stains=False), TEX / "block" / "yellow_wallpaper.png")


def _baseboard(img, board_col=None) -> np.ndarray:
    """Kane's slim baseboard: a painted strip in the wall colour on the bottom 16 px (2 px of 16),
    a clearly lighter rounded top edge catching the ceiling light, a dark hairline shadow on the
    paper above it and a slightly shaded face, so it still reads from across a hall.

    board_col: the paint colour (default: the average colour of the wall it sits on).
    """
    rng = rng_for(121)
    X, Y = grid()
    top = S - 16
    col = np.asarray(board_col if board_col is not None else img.reshape(-1, 3).mean(axis=0), float)
    board = _smooth_concrete(rng, col, mottle=1.5, fine=0.5)
    board = add_lum(board, -10 * smoothstep(top + 4, S, Y))                          # face turns away from the light
    img = add_lum(img, -24 * np.exp(-((Y - (top - 0.6)) / 1.0) ** 2) * (Y < top))    # dark hairline shadow on the paper
    img = over(img, board, box((S, S), -1, top, S + 1, S + 1))
    # the rounded top edge: a bright 3 px band rolling off into the face
    img = add_lum(img, 36 * np.exp(-((Y - (top + 1.5)) / 1.4) ** 2) * (Y > top), (1.0, 1.0, 1.05))
    img = add_lum(img, -10 * np.exp(-((Y - (top + 4.2)) / 0.9) ** 2))                 # under the round-over
    img = add_lum(img, -12 * box((S, S), -1, S - 1.5, S + 1, S + 1))                   # where it meets the carpet
    return img


def tex_yellow_wallpaper_baseboard() -> None:
    save_png(_baseboard(_wallpaper(stains=False)), TEX / "block" / "yellow_wallpaper_baseboard.png")


def _carpet(seed=102, base=CARPET) -> np.ndarray:
    """Kane's carpet: dry, short, tight loop pile. No pattern, just rows of small rounded loops
    (about 5 x 4 px) with dark crevices between them, each loop a slightly different shade, and a
    very soft unevenness. Plain enough that a whole hall reads as one even floor."""
    rng = rng_for(seed)
    X, Y = grid()
    img = fill(base, (S, S))
    px, py = S / 24, S / 32                       # loop pitch (whole loops per block, so it tiles)
    row = np.floor(Y / py)
    xs = X + (row % 2) * px / 2 + fnoise(rng, beta=2.2, kmin=3) * 0.9   # every other row offset half a loop
    ys = Y + fnoise(rng, beta=2.2, kmin=3) * 0.6
    u, v = wrapd(xs, px) / (px / 2), wrapd(ys, py) / (py / 2)
    loop = np.cos(np.clip(np.hypot(u * 0.95, v), 0, 1) * np.pi / 2)    # 1 on top of a loop, 0 in the crevice
    tone = rng.normal(0, 1, (64, 64))[np.floor(ys / py).astype(int) % 64, np.floor(xs / px).astype(int) % 64]
    lum = (-9.0 * (1 - loop) ** 2 + 3.0 * np.clip(-v, -1, 1) * loop + 2.0 * tone * loop
           + 1.5 * fnoise(rng, beta=3.0, kmin=1, kmax=4) + 1.0 * fnoise(rng, beta=1.5, kmin=6, kmax=20)
           + 1.0 * rng.normal(0, 1, (S, S)))
    return add_lum(img, lum - lum.mean(), (1.0, 0.95, 0.8))


def tex_lobby_carpet() -> None:
    save_png(_carpet(), TEX / "block" / "lobby_carpet.png")


def tex_lobby_carpet_arrow() -> None:
    """Carpet with a faded dark arrow pointing to the texture's top (north)."""
    img = _carpet()
    rng = rng_for(118)
    img = marker(img, rng, [[(64, 108), (64, 24)], [(40, 48), (64, 22), (88, 48)]],
                 width=11, color=(70, 52, 24), alpha=0.6)
    save_png(img, TEX / "block" / "lobby_carpet_arrow.png")


def _tbar_grid(img, width=2.2) -> np.ndarray:
    """The dropped ceiling's grid along the block edges: `width` px of painted T-bar on each side
    (twice that where two blocks meet), lit on its north/west lip, shaded on the other."""
    X, Y = grid()
    d = np.minimum(np.minimum(X, S - X), np.minimum(Y, S - Y))
    bar = add_lum(fill(TBAR, (S, S)), 7 * ((X < 1.4) | (Y < 1.4)) - 6 * ((X > S - 1.4) | (Y > S - 1.4)))
    return over(img, bar, smoothstep(width + 0.8, width, d))


def _ceiling_tile(stain=True, seed=103) -> np.ndarray:
    """ONE acoustic tile per block (Kane's ~1.2 m tiles), set in a thin warm T-bar grid at the
    block edge. Warm off-white mineral fibre, fine short dark fissures and pin-holes, a soft
    shadow where the tile sits down in the grid.

    stain=True adds a faint water ring (the ceiling blocks get theirs from the random variants).
    """
    rng = rng_for(seed)
    X, Y = grid()
    img = fill(CEILING, (S, S))

    def fissures(d, k):
        for _ in range(200):
            x, y, a = rng.uniform(0, S), rng.uniform(0, S), rng.uniform(0, np.pi)
            step = rng.uniform(0.8, 2.4)
            pts = [(x, y)]
            for _ in range(2):
                a += rng.normal(0, 0.6)
                x, y = x + np.cos(a) * step, y + np.sin(a) * step
                pts.append((x, y))
            d.line([(p * k, q * k) for p, q in pts], fill=int(rng.uniform(110, 255)), width=int(k * rng.uniform(0.8, 1.5)))

    fiss = pil_mask(fissures)
    pits = blur((rng.random((S, S)) < 0.02) * rng.uniform(0.3, 1, (S, S)), 0.45)
    lum = (-30 * fiss - 30 * pits + 2.0 * fnoise(rng, beta=3.0, kmin=1, kmax=4)
           + 1.5 * blur(fnoise(rng, beta=0.5, kmin=20), 0.5) + 1.2 * rng.normal(0, 1, (S, S)))
    img = add_lum(img, lum, (1.0, 0.98, 0.92))
    if stain:
        img = mark_stain(img, rng, CEILING_STAIN, (16, 14), 0.25)
    d = np.minimum(np.minimum(X, S - X), np.minimum(Y, S - Y))
    img = add_lum(img, -10 * np.exp(-(d - 3.0) / 2.5) * (d > 3.0))    # the tile sits a little up in the grid
    return _tbar_grid(img)


def tex_ceiling_tile() -> None:
    save_png(_ceiling_tile(stain=False), TEX / "block" / "ceiling_tile.png")


def _troffer_frame(rng, lip=10.5):
    """The painted steel troffer frame (warm white enamel) with the T-bar rim round the outside
    and a shadowed groove where the frame steps up to the lens. Returns (frame, lens_mask, d)."""
    X, Y = grid()
    d = np.minimum(np.minimum(X, S - X), np.minimum(Y, S - Y))
    img = _smooth_concrete(rng, (230, 230, 224), mottle=1.2, fine=0.5)
    img = add_lum(img, -20 * smoothstep(1.0, 0.0, np.abs(d - lip)) + 9 * smoothstep(1.0, 0.0, np.abs(d - lip + 2)))
    img = _tbar_grid(img)
    return img, box((S, S), lip + 0.5, lip + 0.5, S - lip - 0.5, S - lip - 0.5), d


def _tube_glow(X, Y, centres, along_y=True, width=6.5):
    """Soft bright bands where tubes sit behind a lens, and how close each pixel is to a tube end."""
    c, a = (X, Y) if along_y else (Y, X)
    tubes = sum(np.exp(-((c - t) / width) ** 2) for t in centres)
    ends = smoothstep(24, 15, a) + smoothstep(S - 24, S - 15, a)
    return tubes, ends


def _prism(X, Y, pitch=4.0):
    """The fine pyramid pattern of a prismatic lens (0 at the tips, 1 in the grooves)."""
    return (np.abs(wrapd(X + Y, pitch)) + np.abs(wrapd(X - Y, pitch))) / pitch


def _fluorescent_panel(on: bool) -> np.ndarray:
    """Kane's square 4-tube troffer, flush in the grid: thin painted frame, prismatic lens.

    Lit: a near-white, faintly cool lens (#F4FFF2-ish, so it clips and blooms) with four faint
    brighter bands where the tubes are. Dead: the same frame with a grey lens, the four tubes and
    their darker ends just visible through it.
    """
    rng = rng_for(104)
    X, Y = grid()
    img, lens, d = _troffer_frame(rng)
    tubes, ends = _tube_glow(X, Y, [11 + (S - 22) * (i + 0.5) / 4 for i in range(4)])
    pr = _prism(X, Y)
    if on:
        diff = add_lum(fill((242, 250, 240), (S, S)), 13 * tubes * (1 - 0.5 * ends) - 2 * pr, (0.96, 1.0, 0.97))
        diff = add_lum(diff, -6 * smoothstep(18, 11, d))               # a touch dimmer right at the frame
    else:
        diff = add_lum(fill((170, 174, 172), (S, S)), 9 * tubes - 26 * tubes * ends - 3 * pr
                       + 2 * fnoise(rng, beta=3.0))
    return over(img, diff, lens)


def tex_fluorescent_light() -> None:
    save_png(_fluorescent_panel(True), TEX / "block" / "fluorescent_light.png")


def tex_fluorescent_light_off() -> None:
    save_png(_fluorescent_panel(False), TEX / "block" / "fluorescent_light_off.png")


def tex_wall_decals() -> None:
    """Black-marker drawings on wallpaper (wall_arrow_left/right, handprint, window, door)."""
    base = _wallpaper(stains=False)   # the plain wall paper, so a drawing doesn't bring a stain with it
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


def _pipe_wall() -> np.ndarray:
    """Pipe-corridor wall with a thin darker skirting band at the bottom of each block.

    The band repeats on every block (walls are stacked), so it is kept thin and soft:
    it reads as a pour/formwork joint higher up and as a skirting at floor level.
    """
    img = _pale_wall()
    X, Y = grid()
    band = box((S, S), -1, S - 5, S + 1, S + 1)
    img = over(img, np.array([150, 145, 170]), band * 0.45)
    return add_lum(img, -6 * np.exp(-((Y - (S - 5.5)) / 0.9) ** 2))        # small shadow lip


def tex_pipe_wall() -> None:
    save_png(_pipe_wall(), TEX / "block" / "pipe_wall.png")


def _pipe_floor() -> np.ndarray:
    """Slightly greyer worn concrete with scuffs and a few hairline cracks."""
    rng = rng_for(112)
    img = _smooth_concrete(rng, (146, 142, 160), mottle=4.0, fine=1.2)
    scuff = smoothstep(0.8, 2.0, fnoise(rng, beta=2.0, aniso=(1, 3)))
    img = over(img, np.array([104, 100, 118]), scuff * 0.35)
    img = add_lum(img, rng.normal(0, 1.8, (S, S)))
    crack = smoothstep(0.035, 0.0, np.abs(fnoise(rng, beta=2.6, kmin=2)))   # zero lines = cracks
    crack *= smoothstep(0.6, 1.4, fnoise(rng, beta=3.0))                    # only in a few places
    img = add_lum(img, -34 * crack)
    return _pores(img, rng, density=0.006, depth=8)


def tex_pipe_floor() -> None:
    save_png(_pipe_floor(), TEX / "block" / "pipe_floor.png")


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


def _round_shade(Y, y0, y1, hl=-0.3, outside=1.0) -> np.ndarray:
    """Brightness multiplier across a band [y0, y1): a lit round pipe seen side-on.

    Darker towards both edges (surface turns away), soft highlight line slightly above the
    centre (hl in -1..1 band units). Rows outside the band get `outside` (None = keep the
    band-edge value, so there is no step where the full texture is shown, e.g. end caps).
    """
    u = np.clip((Y - (y0 + y1) / 2) / ((y1 - y0) / 2), -1, 1)
    shade = 0.62 + 0.38 * np.sqrt(1 - u ** 2) + 0.22 * np.exp(-((u - hl) / 0.16) ** 2)
    if outside is None:
        return shade
    return np.where((Y >= y0) & (Y < y1), shade, outside)


# PoolWorld's pipe-rack model maps pipe k onto texture rows [32k, 32k+24) (u = along the pipe)
PIPE_BANDS = tuple((32 * k, 32 * k + 24) for k in range(4))


def tex_rusty_pipe() -> None:
    """Thin rusty metal pipes: 4 round-shaded bands, blotchy rust, pitting, drip runs.

    Rust is isotropic (round patches) so it doesn't read as wood grain; every feature is
    built from wrap-around noise, so each band tiles along x.
    """
    rng = rng_for(111)
    X, Y = grid()
    img = fill((140, 76, 58), (S, S))                                       # #8C4C3A base rust
    img = add_lum(img, fnoise(rng, beta=2.4) * 7, (1.0, 0.8, 0.65))        # broad tone changes
    blotch = smoothstep(0.2, 1.1, fnoise(rng, beta=2.2, kmin=2))
    img = over(img, np.array([104, 54, 40]), blotch * 0.75)                # dark round rust patches
    orange = smoothstep(1.0, 1.8, fnoise(rng, beta=1.6, kmin=4))
    img = over(img, np.array([164, 94, 58]), orange * 0.6)                 # fresh orange rust spots
    worn = smoothstep(0.9, 1.7, fnoise(rng, beta=1.8, kmin=3))
    img = over(img, np.array([74, 58, 54]), worn * 0.7)                    # iron-grey bare metal #4A3A36
    pits = blur((rng.random((S, S)) < 0.018) * rng.uniform(0.5, 1, (S, S)), 0.5)
    img = add_lum(img, -pits * 60)                                         # pitting
    # a few short drip runs around the pipe (across the band = texture y)
    runs = np.zeros((S, S))
    for _ in range(10):
        cx, cy, ln = rng.uniform(0, S), rng.uniform(0, S), rng.uniform(6, 14)
        dy = (Y - cy) % S
        runs = np.maximum(runs, np.exp(-(wrapd(X - cx, S) / 0.9) ** 2) * (dy < ln) * (1 - dy / ln))
    img = over(img, np.array([96, 46, 32]), runs * 0.8)
    img = add_lum(img, rng.normal(0, 2.0, (S, S)))
    shade = np.ones((S, S))
    for y0, y1 in PIPE_BANDS:
        shade *= _round_shade(Y, y0, y1)
    sheen = 60 * np.clip(shade - 0.98, 0, None)                            # metallic glint on the highlight
    img = img * shade[..., None] + sheen[..., None]
    save_png(img, TEX / "block" / "rusty_pipe.png")


def _galvanised(rng, base=(84, 86, 92)) -> np.ndarray:
    img = _smooth_concrete(rng, base, mottle=2.5, fine=0.8)
    img = add_lum(img, np.kron(rng.normal(0, 2.5, (S // 16, S // 16)), np.ones((16, 16))))  # spangle
    grime = smoothstep(0.6, 1.8, fnoise(rng, beta=2.4))
    return over(img, np.array([56, 52, 50]), grime * 0.4)


def tex_pipe_bracket() -> None:
    """Dark grey galvanised steel; the whole texture is squeezed onto thin plates, so no bolts."""
    rng = rng_for(127)
    img = _galvanised(rng)
    rust = smoothstep(1.5, 2.3, fnoise(rng, beta=1.6))
    save_png(over(img, np.array([110, 64, 44]), rust * 0.5), TEX / "block" / "pipe_bracket.png")


def tex_pale_duct() -> None:
    """Big pale painted duct (axis = x): faint longitudinal seam at v=0.5, subtle dirt."""
    rng = rng_for(124)
    img = _smooth_concrete(rng, (168, 164, 182), mottle=3.0, fine=0.8)
    img = add_lum(img, fnoise(rng, beta=1.4, aniso=(8, 1), kmin=2) * 2.0)   # faint lengthwise grain
    X, Y = grid()
    seam = np.abs(Y - 64)
    img = add_lum(img, -14 * smoothstep(1.2, 0.3, seam) + 6 * smoothstep(2.6, 1.6, seam) * (Y < 63))
    # gentle roundness across the band the duct model uses (rows 40..80), softer than the pipes
    img = img * (1 + 0.55 * (_round_shade(Y, 40, 80, hl=-0.35, outside=None) - 1))[..., None]
    dirt = smoothstep(0.5, 1.8, fnoise(rng, beta=2.6, aniso=(3, 1)))
    img = over(img, np.array([120, 114, 128]), dirt * 0.3)
    save_png(img, TEX / "block" / "pale_duct.png")


def tex_pale_duct_flange() -> None:
    """Duct flange ring: darker painted metal with one column of bolts.

    The model squeezes the full width onto ~4 units along the duct (4x), so the bolts are
    drawn 4x wider than tall and come out round in game.
    """
    rng = rng_for(128)
    img = _smooth_concrete(rng, (140, 136, 154), mottle=2.5, fine=0.8)
    dirt = smoothstep(0.5, 1.8, fnoise(rng, beta=2.4))
    img = over(img, np.array([100, 96, 110]), dirt * 0.3)
    X, Y = grid()
    dx, dy = (X - 64) / 4.0, wrapd(Y - 16, 32)
    d = np.hypot(dx, dy)
    img = add_lum(img, -20 * smoothstep(4.6, 3.0, d) * (d > 3.0))                # contact shadow
    img = over(img, np.array([116, 114, 124]), np.clip(3.0 - d + 0.5, 0, 1))
    img = add_lum(img, 40 * np.clip(1.4 - np.hypot(dx + 0.8, dy + 0.8) + 0.5, 0, 1))
    save_png(img, TEX / "block" / "pale_duct_flange.png")


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
    """Fairly uniform dark painted metal for the lamp's thin cage bars and small back plate."""
    rng = rng_for(132)
    img = _smooth_concrete(rng, (46, 46, 52), mottle=2.0, fine=0.8)
    save_png(add_lum(img, rng.normal(0, 1.5, (S, S))), TEX / "block" / "bulkhead_cage.png")


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


# ---------------------------------------------------------------- Level 9 ---
# "The Suburbs": wet midnight streets, clapboard houses, dark yards and eerie signs.
# Albedos are realistic-but-readable: the level is lit only by sparse sodium lamps.

def _blk(name: str):
    return TEX / "block" / f"{name}.png"


def _cutout_scatter(rng, count, draw_one, ss=4) -> np.ndarray:
    """RGBA cutout made of many small drawn shapes, wrapped so the texture tiles.

    draw_one(draw, rng, x, y, k) draws ONE shape centred at (x, y) (already scaled by k).
    Each shape is drawn at the 9 wrap offsets so pieces crossing an edge continue on the
    opposite side. Colours are averaged over covered sub-pixels only (no dark fringes);
    alpha is thresholded to strictly 0/255 (Minecraft cutout).
    """
    canvas = Image.new("RGBA", (S * ss, S * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(canvas)
    for _ in range(count):
        x, y = rng.uniform(0, S), rng.uniform(0, S)
        state = rng.bit_generator.state                    # same shape for every wrap copy
        for ox in (-S, 0, S):
            for oy in (-S, 0, S):
                rng.bit_generator.state = state
                draw_one(d, rng, (x + ox) * ss, (y + oy) * ss, ss)
    a = np.asarray(canvas, float).reshape(S, ss, S, ss, 4)
    cov = a[..., 3].mean(axis=(1, 3)) / 255.0
    w = a[..., 3:4] / 255.0
    rgb = (a[..., :3] * w).sum(axis=(1, 3)) / np.maximum(w.sum(axis=(1, 3)), 1e-6)
    out = np.zeros((S, S, 4))
    out[..., :3] = rgb
    out[..., 3] = np.where(cov >= 0.5, 255, 0)
    return out


def _fit_font_px(text: str, px: int, maxw: float) -> int:
    """Largest font size <= px whose rendered width fits in maxw pixels."""
    probe = ImageDraw.Draw(Image.new("L", (1, 1)))
    while px > 6 and probe.textlength(text, font=font(px)) > maxw:
        px -= 1
    return px


def _text(text, cx, cy, px, maxw=118.0, shape=(S, S)) -> np.ndarray:
    """Anti-aliased mask of bold text centred at (cx, cy), shrunk to fit maxw."""
    px = _fit_font_px(text, px, maxw)
    return pil_mask(lambda d, k: d.text((cx * k, cy * k), text, fill=255, font=font(px * k), anchor="mm"),
                    shape)


def _weather(img, rng, dirt=0.25, scratches=6) -> np.ndarray:
    """Grime, faint streaks and a few scratches for outdoor painted surfaces."""
    X, Y = grid(img.shape[:2])
    g = smoothstep(0.2, 1.8, fnoise(rng, img.shape[:2], beta=2.4))
    img = over(img, np.array([60, 56, 50]), g * dirt)
    img = add_lum(img, fnoise(rng, img.shape[:2], beta=1.3, aniso=(1, 10), kmin=2) * 3)   # rain streaks
    for _ in range(scratches):
        x0, y0 = rng.uniform(10, S - 10, 2)
        ang = rng.uniform(0, np.pi)
        ln = rng.uniform(6, 20)
        dist_ = np.abs((X - x0) * np.sin(ang) - (Y - y0) * np.cos(ang))
        along = np.abs((X - x0) * np.cos(ang) + (Y - y0) * np.sin(ang))
        img = add_lum(img, 22 * np.exp(-(dist_ / 0.5) ** 2) * (along < ln / 2))
    return img


# ----- ground & street -----

def _asphalt(seed, base=(70, 70, 74)) -> np.ndarray:
    """Dark grainy asphalt: aggregate grain, pale pebbles, tar patches, patchy wet sheen."""
    rng = rng_for(seed)
    img = fill(base, (S, S))
    img = add_lum(img, rng.normal(0, 7, (S, S)) + blur(rng.normal(0, 6, (S, S)), 0.7)
                  + fnoise(rng, beta=2.8) * 5)
    peb = (rng.random((S, S)) < 0.025) * rng.uniform(0.5, 1, (S, S))
    img = add_lum(img, blur(peb, 0.45) * 95, (1.0, 0.98, 0.94))          # pale stone chips
    tar = smoothstep(0.6, 1.6, fnoise(rng, beta=2.6))
    img = over(img, np.array([40, 40, 44]), tar * 0.45)                   # patched / oily areas
    wet = smoothstep(-0.2, 1.2, fnoise(rng, beta=3.2))
    img = img * (1 - 0.18 * wet[..., None])                               # damp = darker
    glint = (rng.random((S, S)) < 0.01) * wet
    return add_lum(img, blur(glint, 0.4) * 60, (0.95, 0.97, 1.0))        # wet glints


def tex_asphalt() -> None:
    save_png(_asphalt(901), _blk("asphalt"))


def tex_asphalt_wet() -> None:
    """Asphalt with a big shallow puddle: very dark, smooth, faint sky reflection."""
    rng = rng_for(902)
    img = _asphalt(902) * 0.85
    pud = smoothstep(0.1, 0.6, blur(fnoise(rng, beta=4.0), 2.0))           # one or two smooth puddles
    water = blur(img, 2.0) * 0.45 + np.array([6, 8, 12])
    sheen = 0.5 + 0.5 * np.sin(2 * np.pi * (grid()[1] / S) + fnoise(rng, beta=3.0))   # soft sky reflection
    water = add_lum(water, sheen * 22, (0.9, 0.95, 1.05))
    img = lerp(img, water, pud[..., None])
    rim = np.clip(smoothstep(0.0, 0.15, pud) - smoothstep(0.15, 0.5, pud), 0, 1)
    save_png(add_lum(img, rim * 10), _blk("asphalt_wet"))


def tex_asphalt_marking() -> None:
    """Faded, worn white road paint: one 32 px stripe along y through the centre."""
    rng = rng_for(903)
    img = _asphalt(903)
    X, Y = grid()
    stripe = box((S, S), 48, -1, 80, S + 1, soft=0.8)
    wear = smoothstep(-1.2, 0.3, fnoise(rng, beta=1.8)) * (0.85 + 0.15 * blur(rng.normal(0, 1, (S, S)), 0.8))
    paint = add_lum(fill((206, 204, 194), (S, S)), rng.normal(0, 6, (S, S)))
    img = over(img, paint, stripe * wear * 0.8)
    save_png(img, _blk("asphalt_marking"))


def _sidewalk(seed, base=(150, 148, 144)) -> np.ndarray:
    """Concrete slab: fine aggregate, stains, a hairline crack, tooled joints at the block edges."""
    rng = rng_for(seed)
    img = _smooth_concrete(rng, base, mottle=5.0, fine=1.6)
    img = add_lum(img, rng.normal(0, 3.0, (S, S)))
    stain = smoothstep(0.6, 1.8, fnoise(rng, beta=2.8))
    img = over(img, np.array([92, 88, 80]), stain * 0.35)
    crack = smoothstep(0.03, 0.0, np.abs(fnoise(rng, beta=2.6, kmin=2))) * smoothstep(0.5, 1.2, fnoise(rng, beta=3))
    img = add_lum(img, -40 * crack)
    X, Y = grid()
    joint = np.minimum(np.abs(wrapd(X, S)), np.abs(wrapd(Y, S)))         # joints on block borders
    img = add_lum(img, -38 * smoothstep(2.2, 0.8, joint) + 10 * smoothstep(3.8, 2.6, joint) * (joint > 2.2))
    return _pores(img, rng, density=0.006, depth=8)


def tex_sidewalk_slab() -> None:
    save_png(_sidewalk(904), _blk("sidewalk_slab"))


def _curb_concrete(rng) -> np.ndarray:
    """Darker curb concrete with tyre scuffs running along x (shared by the curb face and top)."""
    curb = _smooth_concrete(rng, (118, 116, 112), mottle=4.0, fine=1.4)
    return add_lum(curb, -smoothstep(0.6, 1.6, fnoise(rng, beta=1.6, aniso=(6, 1))) * 14)


def tex_sidewalk_curb() -> None:
    """Curb side face: sidewalk with a darker, rounded curb band along the top edge.

    The curb model shows the top half (v 0..8), so the band + lip sit at the curb's top edge.
    """
    img = _sidewalk(904)
    rng = rng_for(905)
    X, Y = grid()
    band = box((S, S), -1, -1, S + 1, 22)
    img = over(img, _curb_concrete(rng), band)
    img = add_lum(img, 22 * np.exp(-((Y - 19) / 1.6) ** 2) - 18 * np.exp(-((Y - 23) / 1.2) ** 2))  # rounded lip
    save_png(img, _blk("sidewalk_curb"))


def tex_sidewalk_curb_top() -> None:
    """Curb top (plain slab, no facing): darker curb concrete over the whole tile, edge joints."""
    img = _sidewalk(906, base=(118, 116, 112))
    rng = rng_for(907)
    scuffs = smoothstep(0.6, 1.6, fnoise(rng, beta=1.6, aniso=(6, 1)))
    save_png(add_lum(img, -scuffs * 12), _blk("sidewalk_curb_top"))


def tex_storm_drain() -> None:
    """Curb inlet: concrete with a dark slot and a cast-iron grate of vertical bars."""
    rng = rng_for(906)
    img = _sidewalk(906)
    X, Y = grid()
    img = add_lum(img, -26 * (box((S, S), 10, 34, S - 10, 106) - box((S, S), 14, 38, S - 14, 102)))
    iron = _galvanised(rng, (54, 50, 48))
    iron = over(iron, np.array([96, 54, 36]), smoothstep(0.8, 2.0, fnoise(rng, beta=1.6)) * 0.6)
    img = over(img, iron, box((S, S), 14, 38, S - 14, 102))
    slots = np.zeros((S, S))
    for x0 in range(22, S - 22, 10):
        slots = np.maximum(slots, box((S, S), x0, 46, x0 + 5, 94))
    depth = 0.25 + 0.75 * np.clip((Y - 46) / 48, 0, 1)
    img = over(img, np.array([10, 10, 12]), slots * (1 - 0.3 * depth))
    leaves = smoothstep(0.9, 1.6, fnoise(rng, beta=1.4)) * (Y > 80)
    img = over(img, np.array([92, 60, 34]), leaves * 0.6)               # leaves caught in the grate
    save_png(img, _blk("storm_drain"))


def tex_manhole() -> None:
    """Round cast-iron manhole cover with raised grid studs and SEWER lettering, in asphalt."""
    rng = rng_for(907)
    img = _asphalt(907)
    X, Y = grid()
    r = np.hypot(X - 64, Y - 64)
    img = over(img, np.array([58, 56, 54]), np.clip(60 - r, 0, 1))      # frame ring
    cover = np.clip(55 - r, 0, 1)
    iron = _galvanised(rng, (64, 60, 58))
    iron = over(iron, np.array([90, 56, 40]), smoothstep(0.9, 2.0, fnoise(rng, beta=1.8)) * 0.5)
    studs = (np.abs(wrapd(X - 4, 8)) < 1.6) & (np.abs(wrapd(Y - 4, 8)) < 1.6)
    iron = add_lum(iron, 14 * studs - 6 * ~studs)
    img = lerp(img, iron, cover[..., None])
    img = add_lum(img, -30 * np.exp(-((r - 55.5) / 0.9) ** 2))          # gap between cover and frame
    img = over(img, iron * 0.95, np.clip(22 - r, 0, 1))                 # smooth centre disc for the lettering
    txt = _text("SEWER", 64, 64, 13, maxw=38)
    img = add_lum(img, 40 * txt - 20 * np.roll(txt, (1, 1), axis=(0, 1)))  # raised cast letters
    save_png(img, _blk("manhole"))


def _grass_layer(rng) -> np.ndarray:
    """Dark damp night grass: dense short blades, clumps, a few dead patches. Seamless."""
    img = fill((50, 62, 42), (S, S))
    blades = rng.normal(0, 1, (S, S))
    blades = (blades + np.roll(blades, 1, 0) + np.roll(blades, 2, 0)) / 1.7   # short vertical blades
    img = add_lum(img, blades * 9 + np.kron(rng.normal(0, 4, (S // 4, S // 4)), np.ones((4, 4))),
                  (0.85, 1.0, 0.7))
    img = add_lum(img, fnoise(rng, beta=2.6) * 6)
    dead = smoothstep(0.8, 1.8, fnoise(rng, beta=2.4))
    img = over(img, np.array([88, 76, 52]), dead * 0.5)
    return add_lum(img, (rng.random((S, S)) < 0.06) * -22.0)             # dark gaps between blades


def tex_lawn_grass_top() -> None:
    save_png(_grass_layer(rng_for(908)), _blk("lawn_grass_top"))


def tex_lawn_grass_side() -> None:
    """Dirt with a ragged dark grass fringe hanging over the top edge (tiles in x)."""
    rng = rng_for(909)
    img = _smooth_concrete(rng, (82, 66, 50), mottle=5.0, fine=1.5)
    img = add_lum(img, rng.normal(0, 6, (S, S)))
    stones = blur((rng.random((S, S)) < 0.01) * 1.0, 0.7)
    img = add_lum(img, stones * 60, (1, 0.95, 0.85))
    grass = _grass_layer(rng_for(908))
    X, Y = grid()
    fringe = 22 + 9 * np.clip(fnoise(rng, (1, S), beta=0.6), -1.5, 1.5)
    img = lerp(img, grass, (Y < fringe).astype(float)[..., None])
    img = add_lum(img, -14 * np.exp(-((Y - fringe) / 2.0) ** 2) * (Y >= fringe))
    save_png(img, _blk("lawn_grass_side"))


LEAF_COLS = ((122, 70, 34), (140, 92, 40), (104, 52, 30), (150, 110, 52), (86, 58, 36), (128, 60, 34))


def _leaf(d, rng, x, y, k, size=(5.0, 9.0), cols=LEAF_COLS) -> None:
    """One dead leaf: lobed outline (maple-ish or oval), random turn, darker midrib."""
    L = rng.uniform(*size)
    ang = rng.uniform(0, 2 * np.pi)
    lobes = rng.choice([0, 5])
    th = np.linspace(0, 2 * np.pi, 28, endpoint=False)
    rr = L * (0.55 + 0.45 * np.abs(np.cos(th / 2))) * (1 + (0.28 * np.cos(lobes * th) if lobes else 0))
    rr *= np.where(np.cos(th) > 0, 1.0, 0.6)
    pts = [(x + k * rr[i] * np.cos(th[i] + ang), y + k * rr[i] * 0.62 * np.sin(th[i] + ang)) for i in range(len(th))]
    c = np.array(cols[rng.integers(len(cols))]) * rng.uniform(0.8, 1.1)
    d.polygon(pts, fill=tuple(int(v) for v in np.clip(c, 0, 255)) + (255,))
    ex, ey = np.cos(ang) * L * k, np.sin(ang) * L * k * 0.62
    d.line((x - ex, y - ey, x + ex, y + ey), fill=tuple(int(v * 0.65) for v in c) + (255,), width=max(1, k // 2))


def tex_leaf_litter() -> None:
    """Cutout carpet of dead leaves (browns/oranges) with transparent gaps."""
    rng = rng_for(910)
    img = _cutout_scatter(rng, 210, lambda d, r, x, y, k: _leaf(d, r, x, y, k, size=(6.0, 11.0)))
    lum = rng.normal(0, 5, (S, S)) + fnoise(rng, beta=2.2) * 8
    img[..., :3] = np.clip(img[..., :3] + lum[..., None], 0, 255)
    save_png(img, _blk("leaf_litter"))


def tex_dust_pile() -> None:
    """Fine grey dust / sand: grain, soft ripples, darker specks."""
    rng = rng_for(911)
    X, Y = grid()
    img = fill((140, 138, 134), (S, S))
    img = add_lum(img, rng.normal(0, 9, (S, S)) + fnoise(rng, beta=2.6) * 7)
    ripple = np.sin(2 * np.pi * (Y + 6 * fnoise(rng, beta=2.5)) / 16)
    img = add_lum(img, ripple * 4)
    img = add_lum(img, (rng.random((S, S)) < 0.02) * -40.0)              # darker specks
    save_png(img, _blk("dust_pile"))


# ----- houses -----

SIDING = {"green": (104, 124, 102), "grey": (128, 130, 128), "blue": (108, 124, 142),
          "yellow": (172, 160, 116), "white": (196, 194, 186)}


def _clapboard(seed, col) -> np.ndarray:
    """Horizontal lap siding, 8 boards per block: lit top edge, shadow under each lap, grime."""
    rng = rng_for(seed)
    X, Y = grid()
    img = add_lum(fill(col, (S, S)), fnoise(rng, beta=1.6, aniso=(10, 1), kmin=2) * 4)   # wood grain
    v = (Y % 16) / 16
    img = img * (0.86 + 0.16 * v)[..., None]                        # each board tilts out toward the bottom
    img = add_lum(img, -34 * smoothstep(1.6, 0.4, Y % 16))            # shadow line under the lap above
    img = add_lum(img, 10 * smoothstep(13, 15.5, Y % 16) * (Y % 16 < 15.5))
    chips = smoothstep(1.7, 2.3, fnoise(rng, beta=1.2, kmin=4))
    img = over(img, np.array([120, 112, 98]), chips * 0.8)           # peeling paint, grey wood below
    mildew = smoothstep(0.4, 1.6, fnoise(rng, beta=2.4)) * (0.4 + 0.6 * v)
    img = over(img, np.array([70, 74, 60]), mildew * 0.22)
    return add_lum(img, fnoise(rng, beta=1.2, aniso=(1, 10), kmin=2) * 3)   # rain streaks


def tex_sidings() -> None:
    for i, (name, col) in enumerate(SIDING.items()):
        save_png(_clapboard(920 + i, col), _blk(f"siding_{name}"))


def _painted_wood(seed, col=(200, 198, 190), vertical=True) -> np.ndarray:
    """Painted boards: visible grain through the paint, a few peeled patches, light grime."""
    rng = rng_for(seed)
    aniso = (1, 12) if vertical else (12, 1)
    img = add_lum(fill(col, (S, S)), fnoise(rng, beta=1.6, aniso=aniso, kmin=2) * 6
                  + fnoise(rng, beta=0.8, aniso=aniso, kmin=8) * 2)
    chips = smoothstep(1.9, 2.4, blur(fnoise(rng, beta=1.3, kmin=3), 0.8))
    img = over(img, np.array([118, 110, 96]), chips * 0.8)
    return _weather(img, rng, dirt=0.18, scratches=0)


def tex_house_trim() -> None:
    save_png(_painted_wood(930), _blk("house_trim"))


def tex_brick_foundation() -> None:
    """Dull red-brown bricks 32x16 in running bond with grey mortar; seamless."""
    rng = rng_for(931)
    X, Y = grid()
    row = np.floor(Y / 16)
    bx = (X + 16 * (row % 2)) % S
    col_idx = np.floor(bx / 32)
    tone = rng.normal(0, 10, (8, 4))
    t = tone[row.astype(int) % 8, col_idx.astype(int) % 4]
    img = fill((124, 70, 56), (S, S)) + t[..., None] * np.array([1.0, 0.7, 0.6])
    img = add_lum(img, rng.normal(0, 6, (S, S)) + fnoise(rng, beta=2.0, kmin=4) * 6)
    mortar = np.minimum(np.abs(wrapd(Y, 16)), np.abs(wrapd(bx, 32)))
    img = over(img, add_lum(fill((126, 122, 114), (S, S)), rng.normal(0, 5, (S, S))), smoothstep(1.6, 0.8, mortar))
    img = add_lum(img, -12 * smoothstep(3.0, 1.6, mortar) * (mortar > 1.6))
    save_png(_weather(img, rng, dirt=0.25, scratches=0), _blk("brick_foundation"))


def tex_roof_shingles() -> None:
    """Dark asphalt shingles: rows of 32 px tabs offset by half a tab, granules, row shadows."""
    rng = rng_for(932)
    X, Y = grid()
    row = np.floor(Y / 16).astype(int)
    bx = (X + 16 * (row % 2)) % S
    tab = np.floor(bx / 32).astype(int)
    tone = rng.normal(0, 7, (8, 4))[row % 8, tab % 4]
    img = fill((62, 60, 62), (S, S)) + tone[..., None]
    img = add_lum(img, rng.normal(0, 8, (S, S)) + blur(rng.normal(0, 5, (S, S)), 0.6))   # granules
    v = (Y % 16) / 16
    img = img * (0.8 + 0.25 * v)[..., None]
    img = add_lum(img, -30 * smoothstep(2.0, 0.4, Y % 16))                 # overlap shadow
    img = add_lum(img, -24 * smoothstep(1.4, 0.4, np.abs(wrapd(bx, 32))) * (v > 0.2))   # tab slots
    moss = smoothstep(1.0, 2.0, fnoise(rng, beta=2.0))
    img = over(img, np.array([60, 70, 46]), moss * 0.4)
    save_png(img, _blk("roof_shingles"))


def tex_house_window() -> None:
    """Blacked-out window: white wooden frame + muntin cross, dark glass, faint reflection."""
    rng = rng_for(933)
    X, Y = grid()
    img = _painted_wood(934)
    glass = fill((14, 16, 20), (S, S))
    refl = np.exp(-((X - Y * 0.8 - 20) / 9) ** 2) * 18 + np.exp(-((X - Y * 0.8 - 52) / 4) ** 2) * 10
    glass = add_lum(glass, refl + fnoise(rng, beta=2.8) * 2, (0.85, 0.9, 1.0))
    panes = np.zeros((S, S))
    for x0, x1 in ((12, 61), (67, 116)):
        for y0, y1 in ((12, 61), (67, 116)):
            panes = np.maximum(panes, box((S, S), x0, y0, x1, y1))
    img = add_lum(img, -26 * np.clip(blur(panes, 1.2) - panes, 0, 1) * 2)   # frame shadow into glass
    img = over(img, glass, panes)
    img = add_lum(img, -16 * box((S, S), 6, S - 10, S - 6, S - 6))          # sill shadow line
    save_png(img, _blk("house_window"))


def tex_house_door() -> None:
    """Painted panel door (muted oxblood) with brass knob, 2 blocks tall, split top/bottom."""
    rng = rng_for(935)
    shp = (2 * S, S)
    X, Y = grid(shp)
    col = np.array([104, 44, 40])
    img = _wood(rng, col, horizontal=False, shape=shp) * 0.4 + fill(col, shp) * 0.6
    for x0, x1 in ((18, 58), (70, 110)):
        for y0, y1 in ((20, 108), (132, 236)):
            outer, inner = box(shp, x0, y0, x1, y1), box(shp, x0 + 6, y0 + 6, x1 - 6, y1 - 6)
            bevel = outer - inner
            lit = ((X < x0 + 6) | (Y < y0 + 6)) & ~((X > x1 - 6) | (Y > y1 - 6))
            img = add_lum(img, bevel * np.where(lit, 22, -30) - 10 * inner)
    chips = smoothstep(1.7, 2.3, fnoise(rng, shp, beta=1.2, kmin=4))
    img = over(img, np.array([96, 82, 66]), chips * 0.7)
    img = _weather(img, rng, dirt=0.2, scratches=4)
    knob = supersample(shp, lambda x, y: np.hypot(x - 104, y - 146) < 5.5)
    img = over(img, np.array([170, 140, 70]), knob)
    img = over(img, np.array([240, 220, 160]), supersample(shp, lambda x, y: np.hypot(x - 102.5, y - 144.5) < 1.8))
    save_png(img[:S], _blk("house_door_top"))
    save_png(img[S:], _blk("house_door_bottom"))


def tex_garage_door() -> None:
    """Sectional garage door: 4 horizontal sections, 2 raised panels per block (tiles in x)."""
    rng = rng_for(936)
    X, Y = grid()
    img = _smooth_concrete(rng, (182, 180, 172), mottle=2.5, fine=0.8)
    for s in range(4):
        y0 = s * 32
        img = add_lum(img, -34 * smoothstep(1.4, 0.3, np.abs(Y - y0)))      # section joints
        for x0 in (8, 72):
            outer, inner = box((S, S), x0, y0 + 7, x0 + 48, y0 + 25), box((S, S), x0 + 3, y0 + 10, x0 + 45, y0 + 22)
            img = add_lum(img, (outer - inner) * np.where(Y < y0 + 16, 14, -16))
    img = over(img, np.array([90, 84, 70]), smoothstep(0.5, 1.0, Y / S) * smoothstep(0.2, 1.6, fnoise(rng, beta=2.0)) * 0.4)
    save_png(_weather(img, rng, dirt=0.15, scratches=5), _blk("garage_door"))


def tex_porch_planks() -> None:
    """Grey-painted porch floorboards along x (16 px), worn paint, dark gaps, staggered joints."""
    rng = rng_for(937)
    X, Y = grid()
    wood = _wood(rng, np.array([132, 104, 76]), horizontal=True)
    paint = add_lum(fill((128, 130, 132), (S, S)), fnoise(rng, beta=1.6, aniso=(10, 1)) * 4)
    worn = smoothstep(0.2, 1.4, fnoise(rng, beta=1.8, aniso=(4, 1)))
    img = over(paint, wood, worn * 0.7)
    board = np.floor(Y / 16).astype(int)
    img = add_lum(img, rng.normal(0, 5, 8)[board % 8])                    # per-board tone
    img = add_lum(img, -40 * smoothstep(1.4, 0.4, np.abs(wrapd(Y, 16))))
    offs = rng.integers(0, S, 8)
    for b in range(8):
        jx = np.abs(wrapd(X - offs[b], S))
        img = add_lum(img, -30 * smoothstep(1.2, 0.3, jx) * (board == b))
    save_png(img, _blk("porch_planks"))


def tex_porch_post() -> None:
    img = _painted_wood(938)
    X, Y = grid()
    img = add_lum(img, -14 * (smoothstep(4, 1, X) + smoothstep(S - 4, S - 1, X)))   # chamfered edges
    save_png(img, _blk("porch_post"))


def tex_porch_light() -> None:
    """Small porch lantern: frosted warm glass with bronze cap (top) and base (bottom)."""
    rng = rng_for(939)
    X, Y = grid()
    r = np.hypot((X - 64) / 64, (Y - 64) / 80)
    glass = lerp(np.array([255, 232, 180]), np.array([236, 190, 120]), np.clip(r, 0, 1)[..., None] ** 1.4)
    glass = add_lum(glass, fnoise(rng, beta=2.2) * 2 + rng.normal(0, 1, (S, S)))
    metal = _galvanised(rng, (62, 52, 40))
    img = glass
    img = over(img, metal, np.clip(box((S, S), -1, -1, S + 1, 18) + box((S, S), -1, S - 16, S + 1, S + 1), 0, 1))
    for x0 in (40, 84):                                                    # thin lantern frame bars
        img = over(img, metal, box((S, S), x0, 18, x0 + 4, S - 16) * 0.85)
    save_png(img, _blk("porch_light"))


# ----- interiors -----

def tex_beige_carpet() -> None:
    rng = rng_for(940)
    X, Y = grid()
    img = fill((168, 152, 122), (S, S))
    img = add_lum(img, rng.normal(0, 6, (S, S)) + np.kron(rng.normal(0, 4, (S // 2, S // 2)), np.ones((2, 2)))
                  + fnoise(rng, beta=3.0) * 5, (1.0, 0.95, 0.82))
    worn = smoothstep(0.6, 1.8, fnoise(rng, beta=3.0))
    img = over(img, np.array([120, 108, 86]), worn * 0.35)
    save_png(img, _blk("beige_carpet"))


def tex_dim_wallpaper() -> None:
    """Faded dusty-rose wallpaper: soft stripes, small floral sprigs, water stains."""
    rng = rng_for(941)
    X, Y = grid()
    img = fill((150, 130, 116), (S, S))
    img = add_lum(img, 6 * np.cos(2 * np.pi * X / 32) + 3 * np.cos(2 * np.pi * X / 8), (1.0, 0.92, 0.88))
    sprig = np.zeros((S, S))
    for cx, cy in ((16, 16), (48, 48), (80, 16), (112, 48), (16, 80), (48, 112), (80, 80), (112, 112)):
        d = np.hypot(wrapd(X - cx, S), wrapd(Y - cy, S))
        th = np.arctan2(wrapd(Y - cy, S), wrapd(X - cx, S))
        sprig = np.maximum(sprig, smoothstep(5.5, 3.5, d / (0.6 + 0.4 * np.abs(np.cos(2.5 * th)))))
    img = over(img, np.array([118, 92, 86]), sprig * 0.5)
    img = add_lum(img, fnoise(rng, beta=3.0) * 5 + rng.normal(0, 1.5, (S, S)))
    stain = smoothstep(0.7, 1.9, fnoise(rng, beta=3.2))
    img = over(img, np.array([120, 100, 72]), stain * 0.3)
    save_png(img, _blk("dim_wallpaper"))


def _planks(rng, col, joints=True) -> np.ndarray:
    """Varnished boards along x, 16 px wide, per-board tone and staggered end joints."""
    X, Y = grid()
    img = _wood(rng, np.array(col), horizontal=True)
    board = np.floor(Y / 16).astype(int)
    img = add_lum(img, rng.normal(0, 7, 8)[board % 8], (1.0, 0.8, 0.6))   # per-board tone
    img = add_lum(img, -30 * smoothstep(1.2, 0.3, np.abs(wrapd(Y, 16))))
    if joints:
        offs = rng.integers(0, S, 8)
        for b in range(8):
            img = add_lum(img, -24 * smoothstep(1.0, 0.3, np.abs(wrapd(X - offs[b], S))) * (board == b))
    return add_lum(img, 8 * smoothstep(0.8, 2.0, fnoise(rng, beta=2.0, aniso=(4, 1))))   # varnish sheen


def tex_wood_floor() -> None:
    save_png(_planks(rng_for(942), (118, 84, 56)), _blk("wood_floor"))


def tex_sofa_fabric() -> None:
    """Faded 70s rust-brown upholstery: tight weave, pile wear."""
    rng = rng_for(943)
    X, Y = grid()
    img = fill((126, 88, 62), (S, S))
    weave = ((X.astype(int) + Y.astype(int)) % 2) * 2 - 1
    img = add_lum(img, weave * 4 + rng.normal(0, 4, (S, S)) + fnoise(rng, beta=2.8) * 6)
    worn = smoothstep(0.6, 1.8, fnoise(rng, beta=2.6))
    img = over(img, np.array([150, 120, 96]), worn * 0.3)
    save_png(img, _blk("sofa_fabric"))


def _walnut(seed, horizontal=True) -> np.ndarray:
    return _wood(rng_for(seed), np.array([92, 62, 40]), horizontal=horizontal)


def tex_tv() -> None:
    """tv_screen: dead CRT (grey-green glass, curved highlight) + knobs on walnut; tv_cabinet: walnut."""
    rng = rng_for(944)
    X, Y = grid()
    img = _walnut(945)
    bez = supersample((S, S), lambda x, y: (np.abs(x - 50) < 42) & (np.abs(y - 62) < 46))
    img = over(img, np.array([40, 38, 36]), bez)
    scr = supersample((S, S), lambda x, y: ((np.abs(x - 50) / 36) ** 4 + (np.abs(y - 62) / 40) ** 4) < 1)
    glass = fill((44, 52, 48), (S, S))
    glass = add_lum(glass, -16 * ((X - 50) ** 2 / 1600 + (Y - 62) ** 2 / 1800) + fnoise(rng, beta=2) * 2)
    glass = add_lum(glass, 30 * np.exp(-(((X - 34) / 10) ** 2 + ((Y - 36) / 6) ** 2)), (0.9, 1.0, 0.95))
    img = lerp(img, glass, scr[..., None])
    for cy in (34, 62):                                                    # knobs
        k = supersample((S, S), lambda x, y, cy=cy: np.hypot(x - 110, y - cy) < 7)
        img = over(img, np.array([170, 164, 150]), k)
        img = add_lum(img, -30 * supersample((S, S), lambda x, y, cy=cy: (np.abs(x - 110) < 1) & (np.abs(y - cy) < 6)))
    grille = (np.abs(X - 110) < 10) & (Y > 80) & (Y < 116) & ((Y.astype(int) % 4) < 2)
    img = add_lum(img, -26 * grille)
    save_png(img, _blk("tv_screen"))
    save_png(_walnut(946), _blk("tv_cabinet"))


def tex_bed() -> None:
    """bed_quilt: muted patchwork squares with stitching; bed_pillow: off-white soft fabric."""
    rng = rng_for(947)
    X, Y = grid()
    pal = np.array([(132, 100, 84), (104, 112, 96), (150, 134, 104), (96, 92, 110), (140, 116, 98)])
    idx = rng.integers(0, len(pal), (8, 8))
    img = pal[idx[(Y // 16).astype(int), (X // 16).astype(int)]].astype(float)
    img = add_lum(img, rng.normal(0, 4, (S, S)) + fnoise(rng, beta=2.6) * 6)
    stitch = np.minimum(np.abs(wrapd(X, 16)), np.abs(wrapd(Y, 16)))
    img = add_lum(img, -20 * smoothstep(1.2, 0.4, stitch))
    dash = ((X + Y).astype(int) % 4 < 2) & (np.abs(wrapd(X - 8, 16)) < 0.6)
    img = add_lum(img, 18 * dash)
    img = img * (0.9 + 0.1 * np.cos(2 * np.pi * fnoise(rng, beta=3.5) * 0.15))[..., None]   # puffy shading
    save_png(img, _blk("bed_quilt"))
    pil = fill((200, 194, 180), (S, S))
    pil = add_lum(pil, fnoise(rng, beta=3.0) * 10 + rng.normal(0, 2, (S, S)))
    wrinkle = smoothstep(0.04, 0.0, np.abs(fnoise(rng, beta=2.4, kmin=2)))
    save_png(add_lum(pil, -18 * wrinkle), _blk("bed_pillow"))


def tex_fridge() -> None:
    """Off-white enamel fridge front (freezer split, chrome handle) and plain side."""
    rng = rng_for(948)
    X, Y = grid()
    base = _smooth_concrete(rng, (210, 204, 186), mottle=2.0, fine=0.5)
    base = _weather(base, rng, dirt=0.12, scratches=3)
    save_png(base, _blk("fridge_side"))
    img = base.copy()
    img = add_lum(img, -40 * smoothstep(1.6, 0.4, np.abs(Y - 40)))         # freezer door gap
    img = add_lum(img, -20 * (smoothstep(3, 0.5, X) + smoothstep(S - 3, S - 0.5, X)))
    for y0, y1 in ((10, 32), (50, 110)):
        h = box((S, S), 108, y0, 116, y1)
        img = over(img, np.array([186, 190, 196]), h)
        img = add_lum(img, 40 * box((S, S), 109, y0, 111, y1) - 20 * box((S, S), 114, y0, 116, y1))
    badge = _text("FROSTKING", 52, 24, 11, maxw=60)
    img = over(img, np.array([140, 136, 130]), badge)
    save_png(img, _blk("fridge"))


def tex_kitchen_counter() -> None:
    """kitchen_counter_top: speckled pale laminate; kitchen_counter_front: wood cabinet doors."""
    rng = rng_for(949)
    X, Y = grid()
    top = fill((196, 190, 168), (S, S))
    top = add_lum(top, rng.normal(0, 3, (S, S)) + fnoise(rng, beta=3.0) * 3)
    spk = (rng.random((S, S)) < 0.05)
    top = over(top, np.array([120, 112, 96]), spk * 0.6)
    save_png(top, _blk("kitchen_counter_top"))
    img = _wood(rng, np.array([134, 98, 62]), horizontal=False)
    img = over(img, top, box((S, S), -1, -1, S + 1, 12))                  # counter edge strip
    img = add_lum(img, -30 * box((S, S), -1, 12, S + 1, 14))
    for x0 in (6, 68):
        outer, inner = box((S, S), x0, 22, x0 + 54, 112), box((S, S), x0 + 7, 29, x0 + 47, 105)
        img = add_lum(img, (outer - inner) * np.where(X < x0 + 27, 14, -14) - 26 * np.clip(box((S, S), x0 - 2, 20, x0 + 56, 114) - outer, 0, 1))
    for hx in (52, 76):
        img = over(img, np.array([176, 170, 156]), box((S, S), hx, 30, hx + 3, 48))
    img = over(img, np.array([28, 24, 22]), box((S, S), -1, 116, S + 1, S + 1))   # toe kick
    save_png(img, _blk("kitchen_counter_front"))


def tex_dining_table() -> None:
    save_png(_planks(rng_for(950), (100, 70, 44), joints=False), _blk("dining_table"))


# ----- yards & nature -----

def tex_picket_fence() -> None:
    """Weathered white fence wood: peeling paint showing grey wood, dirt near the ground."""
    rng = rng_for(951)
    X, Y = grid()
    img = _painted_wood(951, (190, 188, 180))
    peel = smoothstep(0.9, 1.7, fnoise(rng, beta=1.4, aniso=(1, 4)))
    img = over(img, np.array([110, 104, 94]), peel * 0.75)
    img = over(img, np.array([70, 64, 52]), smoothstep(0.7, 1.0, Y / S) * 0.35)
    save_png(img, _blk("picket_fence"))


def tex_mailbox() -> None:
    """Painted dark metal mailbox with rust spots and a faded white house number."""
    rng = rng_for(952)
    img = _galvanised(rng, (52, 56, 60))
    img = over(img, np.array([104, 58, 38]), smoothstep(1.2, 2.0, fnoise(rng, beta=1.8)) * 0.6)
    img = over(img, np.array([180, 178, 170]), _text("1313", 64, 64, 26, maxw=80) * 0.7)
    save_png(img, _blk("mailbox"))


def tex_night_log() -> None:
    """night_log: dark furrowed bark (vertical); night_log_top: dark rings."""
    rng = rng_for(953)
    X, Y = grid()
    img = fill((54, 46, 40), (S, S))
    furrow = fnoise(rng, beta=1.6, aniso=(1, 10), kmin=3)
    img = add_lum(img, furrow * 10 - smoothstep(0.5, 1.4, -furrow) * 18 + rng.normal(0, 4, (S, S)))
    img = over(img, np.array([60, 66, 52]), smoothstep(0.9, 1.8, fnoise(rng, beta=2.0)) * 0.4)   # lichen
    save_png(img, _blk("night_log"))
    r = np.hypot(X - 64, Y - 64)
    top = fill((96, 78, 58), (S, S))
    top = add_lum(top, 10 * np.sin(r / 3.2 + fnoise(rng, beta=2.5) * 1.5) + rng.normal(0, 4, (S, S)))
    top = over(top, img, smoothstep(58, 62, r))
    save_png(top, _blk("night_log_top"))


def _needle(d, rng, x, y, k) -> None:
    """A small spray of dark conifer needles."""
    ang = rng.uniform(0, 2 * np.pi)
    c = (np.array([24, 36, 26]) * rng.uniform(0.75, 1.3)).astype(int)
    for j in range(7):
        a = ang + rng.uniform(-0.5, 0.5)
        L = rng.uniform(4, 8) * k
        sx, sy = x + rng.normal(0, 1.5) * k, y + rng.normal(0, 1.5) * k
        d.line((sx, sy, sx + L * np.cos(a), sy + L * np.sin(a)), fill=tuple(c) + (255,), width=int(1.2 * k))


def tex_night_leaves() -> None:
    rng = rng_for(954)
    img = _cutout_scatter(rng, 260, _needle)
    img[..., :3] = np.clip(img[..., :3] + (fnoise(rng, beta=2.0) * 4)[..., None], 0, 255)
    save_png(img, _blk("night_leaves"))


def tex_dead_branch() -> None:
    rng = rng_for(955)
    img = fill((78, 70, 62), (S, S))
    img = add_lum(img, fnoise(rng, beta=1.4, aniso=(1, 8), kmin=3) * 9 + rng.normal(0, 4, (S, S)))
    img = over(img, np.array([110, 108, 98]), smoothstep(1.0, 1.8, fnoise(rng, beta=1.8)) * 0.4)   # dry grey wood
    save_png(img, _blk("dead_branch"))


def tex_hedge() -> None:
    """Dense dark privet hedge: small leaf clumps with deep gaps (opaque)."""
    rng = rng_for(956)
    img = fill((16, 22, 14), (S, S))
    leaves = _cutout_scatter(rng, 360, lambda d, r, x, y, k: _leaf(d, r, x, y, k, size=(2.5, 4.5),
                                                                   cols=((40, 58, 34), (48, 66, 38), (34, 50, 30), (56, 70, 42))))
    a = leaves[..., 3:4] / 255
    img = img * (1 - a) + leaves[..., :3] * a
    save_png(add_lum(img, fnoise(rng, beta=2.4) * 5), _blk("hedge"))


# ----- street furniture -----

def _pole_metal(seed, along_x=False) -> np.ndarray:
    rng = rng_for(seed)
    img = _smooth_concrete(rng, (112, 114, 116), mottle=3.0, fine=0.8)
    aniso = (10, 1) if along_x else (1, 10)
    img = add_lum(img, fnoise(rng, beta=1.4, aniso=aniso, kmin=2) * 4)
    drips = np.clip(fnoise(rng, beta=1.2, aniso=(1, 14), kmin=2), 0, None)
    return over(img, np.array([104, 66, 46]), np.clip(drips * 0.15, 0, 0.3))


def tex_streetlamp_parts() -> None:
    save_png(_pole_metal(960), _blk("streetlamp_pole"))
    save_png(_pole_metal(961, along_x=True), _blk("streetlamp_arm"))


def _lamp_glass(seed, centre, edge, lit=True) -> np.ndarray:
    """Prismatic lamp glass: radial glow, faint ribs, thin metal rim."""
    rng = rng_for(seed)
    X, Y = grid()
    r = np.hypot((X - 64) / 64, (Y - 64) / 64)
    img = lerp(np.array(centre), np.array(edge), np.clip(r, 0, 1)[..., None] ** 1.3)
    ribs = np.cos(2 * np.pi * X / 8)
    img = add_lum(img, ribs * (3 if lit else 5) + fnoise(rng, beta=2.2) * (2 if lit else 5))
    if not lit:
        img = over(img, np.array([70, 62, 50]), smoothstep(0.3, 1.6, fnoise(rng, beta=2.6)) * 0.5)  # dirt, dead bugs
    rim = 1 - box((S, S), 5, 5, S - 5, S - 5)
    return over(img, np.array([70, 70, 72]), rim)


def tex_streetlamp_heads() -> None:
    save_png(_lamp_glass(962, (255, 214, 150), (244, 150, 66)), _blk("streetlamp_head"))
    save_png(_lamp_glass(963, (64, 56, 46), (40, 36, 32), lit=False), _blk("streetlamp_head_dead"))
    save_png(_lamp_glass(964, (226, 248, 232), (150, 214, 186)), _blk("streetlamp_head_mercury"))
    save_png(_lamp_glass(965, (52, 62, 58), (34, 40, 38), lit=False), _blk("streetlamp_head_mercury_dead"))
    save_png(_galvanised(rng_for(966), (78, 80, 84)), _blk("streetlamp_housing"))


def tex_sign_post() -> None:
    """Galvanised U-channel post with the punched hole row down the middle."""
    rng = rng_for(967)
    X, Y = grid()
    img = _pole_metal(967)
    holes = np.hypot(X - 64, wrapd(Y - 8, 16)) < 3.2
    img = over(img, np.array([12, 12, 14]), holes.astype(float))
    img = add_lum(img, -14 * (smoothstep(8, 2, np.abs(X - 40)) + smoothstep(8, 2, np.abs(X - 88))))   # channel folds
    save_png(img, _blk("sign_post"))
    # sign backs: grey galvanised, cut out to the sign outlines (rounded rect r=9 / diamond)
    back = _weather(_galvanised(rng, (150, 152, 154)), rng, 0.2, 4)
    for name, mask in (
            ("sign_back", pil_mask(lambda d, k: d.rounded_rectangle((2 * k, 2 * k, 126 * k, 126 * k), 9 * k, fill=255))),
            ("sign_back_diamond", pil_mask(lambda d, k: d.polygon(
                [(64 * k, 2 * k), (126 * k, 64 * k), (64 * k, 126 * k), (2 * k, 64 * k)], fill=255)))):
        out = np.zeros((S, S, 4))
        out[..., :3] = back
        out[..., 3] = np.where(mask >= 0.5, 255, 0)
        save_png(out, _blk(name))


def _sign_base(rng, bg, border, radius=12, shape="rect") -> tuple[np.ndarray, np.ndarray]:
    """Sign face (RGB) + coverage mask: rounded rectangle or diamond with an inset border."""
    if shape == "diamond":
        m = pil_mask(lambda d, k: d.polygon([(64 * k, 2 * k), (126 * k, 64 * k), (64 * k, 126 * k), (2 * k, 64 * k)], fill=255))
        inner = pil_mask(lambda d, k: d.polygon([(64 * k, 10 * k), (118 * k, 64 * k), (64 * k, 118 * k), (10 * k, 64 * k)], fill=255))
    else:
        m = pil_mask(lambda d, k: d.rounded_rectangle((2 * k, 2 * k, 126 * k, 126 * k), radius * k, fill=255))
        inner = pil_mask(lambda d, k: d.rounded_rectangle((6 * k, 6 * k, 122 * k, 122 * k), (radius - 3) * k, fill=255))
    img = lerp(np.array(border, float), np.array(bg, float), inner[..., None])
    img = img * np.ones((S, S, 1))
    return img, m


def _finish_sign(img, m, rng, name) -> None:
    """Weathering + cutout alpha outside the sign outline, then save."""
    img = _weather(img, rng, dirt=0.22, scratches=7)
    img = add_lum(img, -10 * smoothstep(0.3, 1.0, np.hypot(*(g - 64 for g in grid())) / 90))
    out = np.zeros((S, S, 4))
    out[..., :3] = img
    out[..., 3] = np.where(m >= 0.5, 255, 0)
    save_png(out, _blk(f"street_sign_{name}"))


def _eye_mask(cx, cy, w, h) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Almond eye outline, iris and pupil masks."""
    X, Y = grid()
    u = (X - cx) / w
    lid = h * np.clip(1 - u ** 2, 0, None)
    eye = np.clip((lid - np.abs(Y - cy)) + 0.5, 0, 1) * (np.abs(u) < 1)
    iris = np.clip(h * 0.85 - np.hypot(X - cx, Y - cy) + 0.5, 0, 1) * eye
    pupil = np.clip(h * 0.38 - np.hypot(X - cx, Y - cy) + 0.5, 0, 1)
    return eye, iris, pupil


def tex_street_signs() -> None:
    rng = rng_for(970)
    green, white, black, red = (30, 98, 64), (228, 228, 220), (20, 20, 22), (168, 30, 28)
    # arrows: green street-sign style, big white arrow + EXIT
    for name, pts in (("arrow_left", [(104, 52), (52, 52), (52, 34), (20, 60), (52, 86), (52, 68), (104, 68)]),
                      ("arrow_right", [(24, 52), (76, 52), (76, 34), (108, 60), (76, 86), (76, 68), (24, 68)]),
                      ("arrow_ahead", [(56, 96), (56, 46), (38, 46), (64, 14), (90, 46), (72, 46), (72, 96)])):
        img, m = _sign_base(rng, green, white)
        arrow = pil_mask(lambda d, k, pts=pts: d.polygon([(x * k, y * k) for x, y in pts], fill=255))
        img = over(img, np.array(white), arrow)
        ty = 108 if name == "arrow_ahead" else 102
        img = over(img, np.array(white), _text("EXIT", 64, ty, 18, maxw=80))
        _finish_sign(img, m, rng, name)
    # neighborhood watch notice
    img, m = _sign_base(rng, white, black, radius=6)
    img = over(img, np.array(black), box((S, S), 6, 6, S - 6, 36))
    eye, iris, pupil = _eye_mask(22, 21, 13, 8)
    img = over(img, np.array(white), eye)
    img = over(img, np.array(red), iris)
    img = over(img, np.array(black), pupil)
    img = over(img, np.array(white), _text("NEIGHBORHOOD", 78, 15, 12, maxw=82))
    img = over(img, np.array(white), _text("WATCH", 78, 28, 13, maxw=82))
    for text, y, col in (("BE A MODEL", 46, black), ("CITIZEN", 58, black), ("KEEP QUIET", 72, black),
                         ("AFTER DARK", 84, black), ("WE ARE", 99, red), ("WATCHING", 112, red)):
        img = over(img, np.array(col), _text(text, 64, y, 13, maxw=110))
    _finish_sign(img, m, rng, "watch_notice")
    # quiet zone
    img, m = _sign_base(rng, white, black, radius=6)
    img = over(img, np.array(black), _text("QUIET", 64, 34, 36, maxw=112))
    img = over(img, np.array(black), _text("ZONE", 64, 68, 36, maxw=112))
    img = over(img, np.array(red), box((S, S), 12, 86, S - 12, 88))
    img = over(img, np.array(red), _text("NO NOISE", 64, 103, 20, maxw=108))
    _finish_sign(img, m, rng, "quiet_notice")
    # warning diamond with an eye: black almond outline, black iris, yellow pupil, white glint
    img, m = _sign_base(rng, (222, 182, 36), black, shape="diamond")
    eye, iris, pupil = _eye_mask(64, 64, 34, 20)
    outline = np.clip(_eye_mask(64, 64, 38, 24)[0] - eye, 0, 1)
    img = over(img, np.array(black), outline)
    img = over(img, np.array(black), iris)
    img = over(img, np.array((222, 182, 36)), pupil)
    X, Y = grid()
    img = over(img, np.array(white), np.clip(3.5 - np.hypot(X - 57, Y - 57), 0, 1))
    _finish_sign(img, m, rng, "warning")
    # nonsense: a regulatory sign gone wrong (scrambled words, warped letters)
    img, m = _sign_base(rng, white, black, radius=6)
    txt = _text("SPEDE", 64, 30, 30, maxw=110) + _text("LIMTI", 64, 62, 30, maxw=110)
    num = _text("-3", 64, 100, 40, maxw=80)
    X, Y = grid()
    shift = (4 * np.sin(Y / 5.0) + 3 * np.sin(Y / 2.3 + 1)).astype(int)
    warped = np.stack([np.roll(np.clip(txt + num, 0, 1)[y], shift[y, 0]) for y in range(S)])
    img = over(img, np.array(black), warped)
    img = over(img, np.array(red), np.fliplr(_text("?", 104, 100, 22, maxw=30)))
    _finish_sign(img, m, rng, "nonsense")


def tex_highway_sign_exit() -> None:
    """512x256 green highway sign split into 8 tiles highway_sign_exit_0..7 (row*4+col, row 0 top).

    White rounded border inset 10 px around the WHOLE sign, big "EXIT" across the top row,
    a down arrow (toward the tunnel) in the bottom row. Opaque, crisp edges.
    """
    rng = rng_for(973)
    H, W = 2 * S, 4 * S
    shp = (H, W)
    X, Y = grid(shp)
    green, white = np.array([11, 107, 58]), np.array([232, 236, 230])
    # reflective sheeting: faint fine grain + broad sheen, a little darker toward the bottom
    img = fill(green, shp)
    img = add_lum(img, rng.normal(0, 2.0, shp) + fnoise(rng, shp, beta=2.6) * 4 - 6 * (Y / H))
    border = pil_mask(lambda d, k: d.rounded_rectangle((10 * k, 10 * k, (W - 10) * k, (H - 10) * k),
                                                       22 * k, outline=255, width=7 * k), shp)
    img = over(img, white, border)
    img = over(img, white, _text("EXIT", W / 2, 70, 104, maxw=330, shape=shp))
    cx = W / 2
    arrow = [(cx - 14, 132), (cx + 14, 132), (cx + 14, 178), (cx + 40, 178), (cx, 224),
             (cx - 40, 178), (cx - 14, 178)]
    img = over(img, white, pil_mask(lambda d, k: d.polygon([(x * k, y * k) for x, y in arrow], fill=255), shp))
    # wear: light grime and rain streaks, kept subtle so the sign stays legible from afar
    img = over(img, np.array([60, 56, 50]), smoothstep(0.6, 2.0, fnoise(rng, shp, beta=2.4)) * 0.22)
    img = add_lum(img, fnoise(rng, shp, beta=1.3, aniso=(1, 10), kmin=2) * 3)
    for i in range(8):
        row, col = divmod(i, 4)
        save_png(img[row * S:(row + 1) * S, col * S:(col + 1) * S], _blk(f"highway_sign_exit_{i}"))


def tex_tunnel_concrete() -> None:
    """Board-formed underpass concrete: form-board lines every 32 px, water stains, pores."""
    rng = rng_for(971)
    X, Y = grid()
    img = _smooth_concrete(rng, (128, 126, 122), mottle=6.0, fine=1.8)
    img = add_lum(img, fnoise(rng, beta=1.4, aniso=(10, 1), kmin=2) * 4)
    img = add_lum(img, -12 * smoothstep(1.2, 0.3, np.abs(wrapd(Y, 32))))
    stains = np.clip(fnoise(rng, beta=1.2, aniso=(1, 12), kmin=2), 0, None)
    img = over(img, np.array([70, 70, 64]), np.clip(stains * 0.22, 0, 0.35))
    save_png(_pores(img, rng), _blk("tunnel_concrete"))


def tex_exit_tunnel_marker() -> None:
    """Road surface deep in the exit underpass: dark worn wet concrete, tyre wear, paint flecks."""
    rng = rng_for(972)
    img = _smooth_concrete(rng, (84, 82, 80), mottle=6.0, fine=1.8)
    img = add_lum(img, rng.normal(0, 3, (S, S)))
    tyre = smoothstep(0.8, 2.0, fnoise(rng, beta=1.6, aniso=(1, 10), kmin=2))
    img = add_lum(img, -14 * tyre)
    wet = smoothstep(0.0, 1.4, fnoise(rng, beta=3.2))
    img = img * (1 - 0.22 * wet[..., None])
    flecks = smoothstep(1.9, 2.4, fnoise(rng, beta=0.8, kmin=8))
    img = over(img, np.array([190, 180, 120]), flecks * 0.5)            # faded yellow paint flecks
    save_png(_pores(img, rng, density=0.008, depth=10), _blk("exit_tunnel_marker"))


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


def tex_smiler() -> None:
    """Smiler atlas (smiler.png, 256x256 for a 64x64 UV model, so 4 pixels per model unit).

    Everything glowing is drawn with additive blending, so black means "nothing" and colour means
    light. Each sprite is a soft white-hot shape inside a warm orange glow, with the colour
    channels pulled apart at the edges (red outside, blue on one side) like the smeared, fringed
    camcorder photos of Smilers. Every sprite is stored twice side by side: the flat planes in the
    model show their front and back from neighbouring regions of the texture.

    Regions (model units): eye (0,0) 6x7, tooth (12,0) 2x6, face haze (16,0) 24x18,
    smoke puffs (0,20) and (32,20) 16x16 each (black with ragged soft alpha, the only sprites
    drawn normally rather than additively).
    """
    S = 4
    img = np.zeros((64 * S, 64 * S, 4))
    img[..., 3] = 255.0

    def place(x0, y0, sprite):
        h, w = sprite.shape[:2]
        for k in range(2):  # front and back copy
            img[y0 * S:y0 * S + h, (x0 * S + k * w):(x0 * S + k * w) + w] = sprite

    def grid_xy(w, h):
        yy, xx = np.mgrid[0:h, 0:w].astype(float)
        return xx + 0.5, yy + 0.5

    def glow_sprite(mask_fn, w, h, fringe_dx, fringe_dy, core_blur, halo_sigma, warmth):
        xx, yy = grid_xy(w, h)
        m = mask_fn(xx, yy)
        m_r = mask_fn(xx - fringe_dx, yy - fringe_dy * 0.5)   # red slips one way...
        m_b = mask_fn(xx + fringe_dx, yy + fringe_dy)          # ...blue the other
        soft = lambda a, s: np.clip(blur(np.pad(a, 8), s)[8:-8, 8:-8], 0, None)
        core, core_r, core_b = soft(m, core_blur), soft(m_r, core_blur * 1.4), soft(m_b, core_blur * 1.2)
        halo = soft(m, halo_sigma)
        halo = halo / (halo.max() + 1e-9)
        r = 255 * np.clip(0.95 * core_r + 0.25 * core + warmth * 0.95 * halo, 0, 1)
        g = 255 * np.clip(0.95 * core + warmth * 0.42 * halo, 0, 1)
        b = 255 * np.clip(0.75 * core * core_b + 0.55 * core_b + warmth * 0.10 * halo, 0, 1)
        out = np.zeros((h, w, 4))
        out[..., 0], out[..., 1], out[..., 2], out[..., 3] = r, g, b, 255.0
        return out

    # Eye: an egg, rounder on top, a little pointed at the bottom.
    def eye_mask(x, y):
        cx, cy, rx, ry = 12.0, 13.0, 6.2, 8.5
        dy = (y - cy) / ry
        rxy = rx * (1.0 - 0.28 * np.clip(dy, 0, None))
        d = ((x - cx) / rxy) ** 2 + dy ** 2
        return np.clip((1.0 - d) * 3.0, 0, 1)
    place(0, 0, glow_sprite(eye_mask, 6 * S, 7 * S, 1.6, 1.2, 0.8, 3.0, 0.9))

    # Tooth: a long sliver, pointed at both ends (the plane is scaled per tooth in the model).
    def tooth_mask(x, y):
        cx, cy, half_h = 4.0, 12.0, 10.5
        t = np.clip(1.0 - ((y - cy) / half_h) ** 2, 0, 1)
        half_w = 1.6 * t ** 1.2
        return np.clip((half_w - np.abs(x - cx)) * 1.6, 0, 1)
    place(12, 0, glow_sprite(tooth_mask, 2 * S, 6 * S, 1.0, 0.0, 0.4, 1.2, 0.55))

    # Face haze: a wide, very faint warm glow that makes it read as a smudge of light from afar.
    xx, yy = grid_xy(24 * S, 18 * S)
    d = ((xx - 12 * S) / (11 * S)) ** 2 + ((yy - 9 * S) / (8.5 * S)) ** 2
    haze = np.exp(-3.2 * d)
    place(16, 0, np.stack([60 * haze, 42 * haze, 30 * haze, np.full_like(haze, 255.0)], axis=-1))

    # Smoke: two ragged puffs of black with soft, uneven alpha. Several of them, turning slowly
    # and overlapping, make the dark shroud that hangs around the face.
    for k, (u, seed) in enumerate(((0, 2703), (32, 2704))):
        rng = rng_for(seed)
        n = 16 * S
        xx, yy = grid_xy(n, n)
        r2 = ((xx - n / 2) / (n / 2)) ** 2 + ((yy - n / 2) / (n / 2)) ** 2
        base = np.exp(-2.6 * r2) * np.clip(1.0 - r2, 0, 1)
        noise = blur(fnoise(rng, (n, n), beta=2.2), 1.2)
        noise = (noise - noise.min()) / (noise.max() - noise.min() + 1e-9)
        alpha = np.clip((base * (0.35 + 0.9 * noise) - 0.06) * 1.5, 0, 1) * 0.6
        puff = np.stack([np.full_like(alpha, 4.0), np.full_like(alpha, 4.0), np.full_like(alpha, 6.0), 255.0 * alpha], axis=-1)
        place(u, 20, puff)
    save_png(img, TEX / "entity" / "smiler.png")
    (TEX / "entity" / "smiler_body.png").unlink(missing_ok=True)  # the old separate body texture



def tex_sanity_silhouette() -> None:
    """64x128 black cut-out of a tall, gaunt Bacteria-like figure for the low-sanity hallucination.
    Only the alpha matters: the client draws it pitch black as a billboard (it isn't really there)."""
    rng = rng_for(2530)

    def draw(d, k):
        def line(pts, w):
            d.line([(x * k, y * k) for x, y in pts], fill=255, width=int(w * k), joint="curve")
        # Hunched torso, thin legs, arms hanging past the knees.
        line([(32, 26), (31, 44), (32, 66)], 9)
        line([(30, 64), (27, 92), (26, 126)], 4)
        line([(34, 64), (37, 92), (38, 126)], 4)
        line([(27, 30), (20, 52), (17, 76), (15, 96)], 3)
        line([(37, 30), (44, 52), (47, 76), (50, 96)], 3)
        for fx in (15, 50):                       # long crooked fingers
            for o in (-3, 0, 3):
                line([(fx, 96), (fx + o, 104)], 1.5)
        line([(31, 26), (33, 18)], 4)             # neck
        # Lumpy, spiky head (the Bacteria's tangled mass).
        d.ellipse([25 * k, 6 * k, 41 * k, 20 * k], fill=255)
        for _ in range(9):
            ang = rng.uniform(-np.pi, 0.2)
            ln = rng.uniform(9, 15)
            line([(33, 13), (33 + np.cos(ang) * ln, 13 + np.sin(ang) * ln)], 2)

    a = pil_mask(draw, shape=(128, 64))
    img = np.zeros((128, 64, 4))
    img[..., 3] = (a > 0.4) * 255.0
    save_png(img, TEX / "misc" / "sanity_silhouette.png")


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
    """Real footstep recordings (Kenney Impact Sounds, CC0; see CREDITS.md), made light and soft:
    someone walking carefully, not stomping.

    The heavy "thud" of a footstep is its low body (below ~250 Hz) and its ring afterwards, so
    both go: the low end is cut, only the first moment of each step is kept (a fast decay), the
    pitch goes up a touch and the very top is softened. Walk: quiet, the faintest scuff. Run: a
    little brighter and louder, but no thump under it.
    """
    # per-surface seed offsets keep each surface's variations stable
    for surface, seed_off in (("carpet", 0), ("concrete", 20), ("grass", 40), ("wood", 60)):
        for i in range(1, 6):
            src = _trim(_load_mono(VENDOR / "kenney_impact" / f"footstep_{surface}_{i - 1:03d}.ogg"))
            for run in (False, True):
                rng = rng_for(1400 + seed_off + (10 if run else 0) + i)
                x = _repitch(src, rng.uniform(1.04, 1.12))
                # Wood floors boom: their cut sits higher so only the knock of the heel is left.
                low_cut = (420.0 if surface == "wood" else 280.0) * (0.85 if run else 1.0)
                x = fft_filter(x, lambda f, c=low_cut: hp_gain(f, c, 4) * lp_gain(f, 6000, 2))[:len(x)]
                decay = 0.045 if run else 0.032
                tt = np.arange(len(x)) / SR
                x = (x * np.exp(-np.maximum(tt - 0.006, 0.0) / decay))[:int((0.006 + 4 * decay) * SR)]
                peak = (0.45 if run else 0.3) * 10 ** (rng.uniform(-1, 1) / 20)   # +/-1 dB variation
                kind = "run" if run else "walk"
                save_ogg(x, SND / "step" / f"{surface}_{kind}{i}.ogg", peak=peak,
                         fade_in=0.001, fade_out=0.01)


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
# Level 9 sounds: near-silent suburb, sodium lamps, the Neighborhood Watch
# =============================================================================

def snd_level9_ambience() -> None:
    """12 s loop: oppressive near-silence. Very faint cold air bed + barely-there far buzz."""
    rng = rng_for(2401)
    T = 12.0
    n = int(T * SR)
    t = np.arange(n) / SR
    air = periodic_noise(n, rng, lambda f: hp_gain(f, 30, 2) * lp_gain(f, 500, 2) / np.maximum(f, 30) ** 0.5)
    air *= drift(n, rng, 0.25, 0.05, 0.2)
    room = periodic_noise(n, rng, lambda f: bp_gain(f, 25, 90, 3))                     # distant city-less rumble
    buzz = mains_hum(t, levels_db=(0, -14, -26, -36)) * drift(n, rng, 0.3, 0.06, 0.2)
    buzz = fft_filter(buzz, lambda f: lp_gain(f, 400, 2), circular=True)              # far away = dull
    air, room, buzz = norm(air), norm(room), norm(buzz)
    rms = np.sqrt(np.mean(air ** 2))
    x = air + 0.5 * room + buzz / np.sqrt(np.mean(buzz ** 2)) * rms * 10 ** (-24 / 20)
    save_ogg(x, SND / "ambient" / "level9_ambience.ogg", peak=0.35, loop=True)


def snd_sodium_hum() -> None:
    """4 s loop: old sodium streetlamp. Low 120 Hz hum + soft, steady ballast harmonics.

    The 'rasp' is a fixed harmonic series of 120 Hz rolling off above ~400 Hz and cut at
    ~1.6 kHz, with only a very slow level drift: buzzy in tone, but no flutter/grit."""
    rng = rng_for(2402)
    T = 4.0
    n = int(T * SR)
    t = np.arange(n) / SR
    hum = mains_hum(t, levels_db=(0, -9, -16, -22))
    rasp = sum((400 / (120 * h)) ** 1.4 * np.sin(2 * np.pi * 120 * h * t + 1.3 * h) for h in range(3, 14))
    rasp = fft_filter(rasp, lambda f: lp_gain(f, 1600, 3), circular=True)
    x = (norm(hum) + 0.18 * norm(rasp)) * drift(n, rng, 0.08, 0.2, 0.5)
    save_ogg(x, SND / "light" / "sodium_hum.ogg", peak=0.55, loop=True)


def snd_lamp_flicker() -> None:
    """Short electrical tick + two ragged buzz bursts of a failing streetlamp (3 takes: the tick's
    ring, the buzz pitch and the burst rhythm shift a little each time)."""
    takes = ((2403, 1.0, ((0.06, 0.12), (0.26, 0.05), (0.36, 0.18))),
             (52403, 1.05, ((0.05, 0.09), (0.19, 0.07), (0.33, 0.21))),
             (62403, 0.96, ((0.07, 0.17), (0.29, 0.07), (0.38, 0.22))))
    for i, (seed, p, bursts) in enumerate(takes, 1):
        rng = rng_for(seed)
        dur = 0.7
        n = int(dur * SR)
        t = np.arange(n) / SR
        x = np.zeros(n)
        k = int(0.004 * SR)
        add_at(x, rng.normal(size=k) * np.exp(-np.arange(k) / (0.0008 * SR)), 0.01, False)   # relay tick
        add_at(x, 0.4 * ring([2900 * p, 4100 * p], [0.02, 0.012], [1, 0.6], 0.1), 0.01, False)
        s = np.sin(2 * np.pi * 120 * p * t)
        buzz = fft_filter(np.sign(s) * np.abs(s) ** 0.3, lambda f: bp_gain(f, 100, 2500, 1))[:n]
        gate = np.zeros(n)
        for st, ln in bursts:
            gate += box((1, n), st * SR, -1, (st + ln) * SR, 2, soft=200)[0] * rng.uniform(0.6, 1.0)
        x += 0.5 * norm(buzz) * gate
        x = x + 0.2 * conv(x, reverb_ir(rng, 0.6, 0.15, lp=4000))[:n]
        name = "lamp_flicker.ogg" if i == 1 else f"lamp_flicker{i}.ogg"
        save_ogg(x, SND / "level9" / name, peak=0.75, fade_in=0.001, fade_out=0.05)


def snd_distant_rumble() -> None:
    """~6 s deep far-away rumble with heavy thuds: something enormous moving in the fog."""
    rng = rng_for(2404)
    dur = 6.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    env = np.zeros(n)
    for c, w, a in ((1.6, 0.9, 0.8), (3.0, 1.2, 1.0), (4.4, 0.9, 0.7)):
        env += a * np.exp(-0.5 * ((t - c) / w) ** 2)
    rumble = fft_filter(rng.normal(size=n), lambda f: lp_gain(f, 90, 3) * hp_gain(f, 18, 2))[:n]
    x = norm(rumble) * env * (1 + smooth_jitter(rng, n, 4, 0.3))
    for st in (1.2, 2.9, 4.5):                                            # giant footfalls
        tt = t_axis(1.2)
        thud = np.sin(2 * np.pi * np.cumsum(28 + 30 * np.exp(-tt / 0.08)) / SR) * np.exp(-tt / 0.35)
        add_at(x, 0.9 * thud, st, False)
    x = fft_filter(x, lambda f: lp_gain(f, 250, 2))[:n]
    x = x + 0.5 * conv(x, reverb_ir(rng, 3.0, 1.2, lp=400))[:n]
    save_ogg(x, SND / "level9" / "distant_rumble.ogg", peak=0.89, fade_in=0.3, fade_out=1.0)


def snd_watcher_hum() -> None:
    """6 s loop: deep wet electrical drone with slow vibrato, swelling 'wet' resonances and faint static."""
    rng = rng_for(2405)
    T = 6.0
    n = int(T * SR)
    t = np.arange(n) / SR
    # 2 Hz vibrato as a periodic time warp (integral of 0.006*sin) -> loops exactly
    warp = -0.006 / (2 * np.pi * 2.0) * np.cos(2 * np.pi * 2.0 * t)
    drone = sum(10 ** (db / 20) * np.sin(2 * np.pi * 50 * h * (t + warp) + h)
                for h, db in ((1, 0), (2, -4), (3, -8), (4, -12), (5, -16), (6, -20), (8, -26)))
    beat = np.sin(2 * np.pi * (50 + 1 / T * 2) * t)                      # slow beating partner
    wet = np.zeros(n)
    for fc in (180.0, 260.0, 410.0):
        band = periodic_noise(n, rng, lambda f, fc=fc: np.exp(-0.5 * ((f - fc) / 14) ** 2))
        wet += band * drift(n, rng, 0.9, 1 / T, 0.6)
    static = periodic_noise(n, rng, lambda f: bp_gain(f, 800, 3500, 2))
    crack = np.zeros(n)
    for _ in range(40):
        m = int(rng.uniform(0.0005, 0.003) * SR)
        add_at(crack, rng.normal(size=m) * rng.uniform(0.3, 1), rng.uniform(0, T), True)
    crack = fft_filter(crack, lambda f: bp_gain(f, 600, 3500, 1), circular=True)
    x = norm(drone) * 0.7 + 0.25 * beat + norm(wet) * 0.3 + norm(static) * 0.03 + norm(crack) * 0.08
    x = fft_filter(x, lambda f: lp_gain(f, 2500, 2), circular=True)
    save_ogg(x, SND / "entity" / "watcher_hum.ogg", peak=0.55, loop=True)


def snd_watcher_spot() -> None:
    """~1.5 s: sharp dissonant tonal sting + static burst, in a big outdoor space."""
    rng = rng_for(2406)
    dur = 1.5
    n = int(dur * SR)
    t = np.arange(n) / SR
    env = smoothstep(0, 0.01, t) * np.exp(-t / 0.45)
    tone = sum(np.sin(2 * np.pi * f * t * (1 + 0.004 * np.sin(2 * np.pi * 7 * t))) for f in (740.0, 784.0, 1046.5, 1108.7))
    tone = dist(norm(tone) * 1.2, 2.0) * env
    sub = np.sin(2 * np.pi * 55 * t) * np.exp(-t / 0.5)
    stat = bitcrush(fft_filter(rng.normal(size=n), lambda f: bp_gain(f, 900, 5000, 1))[:n] * 0.6, 4, 6)
    stat *= smoothstep(0, 0.005, t) * np.exp(-t / 0.18)
    x = 0.7 * tone + 0.5 * norm(sub) + 0.45 * norm(stat)
    x = x + 0.4 * conv(x, reverb_ir(rng, 1.5, 0.6, lp=5000))[:n]
    save_ogg(x, SND / "entity" / "watcher_spot.ogg", peak=0.88, fade_in=0.001, fade_out=0.3)


def snd_watcher_beam() -> None:
    """~2.5 s: rising whine charging up, then a searing hiss."""
    rng = rng_for(2407)
    dur = 2.5
    n = int(dur * SR)
    t = np.arange(n) / SR
    tc = 1.7
    f = 300 * (2400 / 300) ** np.clip(t / tc, 0, 1)
    vib = 1 + 0.01 * (t / tc) * np.sin(2 * np.pi * (5 + 10 * t / tc) * t)
    whine = np.sin(2 * np.pi * np.cumsum(f * vib) / SR) + 0.4 * np.sin(2 * np.pi * np.cumsum(1.5 * f * vib) / SR)
    whine *= smoothstep(0, 0.2, t) * (0.3 + 0.7 * np.clip(t / tc, 0, 1)) * (1 - smoothstep(tc, tc + 0.1, t))
    hiss = fft_filter(rng.normal(size=n), lambda fr: hp_gain(fr, 1500, 2) * lp_gain(fr, 9000, 1))[:n]
    hiss *= smoothstep(tc - 0.05, tc + 0.03, t) * (1 - smoothstep(dur - 0.5, dur, t)) * (1 + smooth_jitter(rng, n, 30, 0.3))
    crackle = _crackle(rng, dur, 400) * smoothstep(tc - 0.05, tc + 0.03, t)
    x = 0.6 * norm(whine) + 0.6 * norm(hiss) + 0.25 * norm(crackle)
    save_ogg(x, SND / "entity" / "watcher_beam.ogg", peak=0.85, fade_in=0.01, fade_out=0.2)


def snd_watcher_dust() -> None:
    """~2 s dry crumbling cascade: a soft collapse thump, then thousands of sand grains."""
    rng = rng_for(2408)
    dur = 2.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    density = 6000 * np.exp(-t / 0.5) * smoothstep(0, 0.05, t)
    grains = (rng.random(n) < density / SR) * rng.uniform(-1, 1, n)
    grains = fft_filter(grains, lambda f: bp_gain(f, 1200, 8000, 1))[:n]
    hush = fft_filter(rng.normal(size=n), lambda f: bp_gain(f, 400, 3000, 1))[:n] * np.exp(-t / 0.6) * smoothstep(0, 0.05, t)
    thump = fft_filter(rng.normal(size=n) * np.exp(-t / 0.06), lambda f: lp_gain(f, 300, 2))[:n]
    x = norm(grains) * 0.7 + norm(hush) * 0.35 + norm(thump) * 0.5
    x = x + 0.2 * conv(x, reverb_ir(rng, 1.2, 0.4, lp=4000))[:n]
    save_ogg(x, SND / "entity" / "watcher_dust.ogg", peak=0.85, fade_in=0.002, fade_out=0.3)


def snd_observer_vanish() -> None:
    """~1.2 s soft airy exhale: band-passed breath noise sweeping down, swell and fade."""
    rng = rng_for(2409)
    dur = 1.2
    n = int(dur * SR)
    t = np.arange(n) / SR
    fc = 1800 * np.exp(-t / 0.6) + 300
    breath = svf(rng.normal(size=n), fc, 1.5)
    env = smoothstep(0, 0.25, t) * (1 - smoothstep(0.35, dur, t))
    x = norm(breath) * env + 0.2 * norm(fft_filter(rng.normal(size=n), lambda f: lp_gain(f, 300, 2))[:n]) * env
    x = x + 0.35 * conv(x, reverb_ir(rng, 1.5, 0.5, lp=3000))[:n]
    save_ogg(x, SND / "entity" / "observer_vanish.ogg", peak=0.75, fade_in=0.02, fade_out=0.15)


# =============================================================================
# Smiler sound
# =============================================================================

def snd_smiler_breath() -> None:
    """~2.4 s faint, slow breathing: the only sound a Smiler makes, when it's a few blocks away.
    Just air: a long, soft inhale and a slower exhale with a little wet catch in it.
    3 takes: different air noise, breath timing and throat resonance (+-5%)."""
    takes = ((2720, 1.0, 0.0), (52720, 1.05, 0.12), (62720, 0.95, -0.08))
    for i, (seed, p, sh) in enumerate(takes, 1):
        rng = rng_for(seed)
        dur = 2.4
        n = int(dur * SR)
        t = np.arange(n) / SR
        inhale = svf(rng.normal(size=n), p * (700 + 900 * smoothstep(0.0, 0.9 + sh, t)), 2.0) * (smoothstep(0.0, 0.6 + sh, t) * (1 - smoothstep(0.8 + sh, 1.0 + sh, t)))
        ex_env = smoothstep(1.1 + sh, 1.4 + sh, t) * (1 - smoothstep(1.8 + sh, dur, t))
        exhale = svf(rng.normal(size=n), p * (1000 - 400 * smoothstep(1.1 + sh, dur, t)), 1.4) * ex_env
        wet = clicks(rng, np.sort(rng.uniform(1.3 + sh, 1.8 + sh, 4)), dur, 500 * p, 1100 * p, 0.004, 0.01)
        x = 0.7 * norm(inhale) + norm(exhale) + 0.08 * norm(wet)
        x = fft_filter(x, lambda f: lp_gain(f, 2500, 2))[:n]
        x = x + 0.15 * norm(conv(x, reverb_ir(rng, 0.6, 0.15, lp=3000))[:n]) * np.abs(x).max()
        name = "smiler_breath.ogg" if i == 1 else f"smiler_breath{i}.ogg"
        save_ogg(x, SND / "entity" / name, peak=0.55, fade_in=0.05, fade_out=0.2)


def snd_leaf_steps() -> None:
    """4 dry dead-leaf crunches: dense crackle bursts with a soft step thud underneath."""
    for i in range(1, 5):
        rng = rng_for(2410 + i)
        dur = 0.4
        n = int(dur * SR)
        t = np.arange(n) / SR
        x = np.zeros(n)
        for c, a in ((0.02, 1.0), (rng.uniform(0.06, 0.12), rng.uniform(0.5, 0.8))):
            dens = 9000 * np.exp(-np.clip(t - c, 0, None) / 0.06) * (t >= c)
            cr = (rng.random(n) < dens / SR) * rng.uniform(-1, 1, n)
            x += a * fft_filter(cr, lambda f: bp_gain(f, 1500, 9000, 1))[:n]
        rustle = fft_filter(rng.normal(size=n), lambda f: bp_gain(f, 800, 5000, 1))[:n] * np.exp(-t / 0.1) * smoothstep(0, 0.01, t)
        thud = fft_filter(rng.normal(size=n) * np.exp(-t / 0.03), lambda f: lp_gain(f, 400, 2))[:n]
        x = norm(x) + 0.3 * norm(rustle) + 0.4 * norm(thud)
        save_ogg(x, SND / "step" / f"leaves{i}.ogg", peak=0.6 * 10 ** (rng.uniform(-1, 1) / 20),
                 fade_in=0.001, fade_out=0.05)

# ---- Sanity (low-sanity hallucinations) ----------------------------------------------------

def snd_sanity_whisper() -> None:
    """3 unintelligible whispers (~2 s): breathy noise through two sweeping 'formant' bandpasses,
    chopped into syllable-like puffs, with a little room so it sounds like it's right behind you."""
    for i in range(1, 4):
        rng = rng_for(2500 + i)
        dur = 1.8 + 0.3 * i
        n = int(dur * SR)
        t = np.arange(n) / SR
        breath = fft_filter(rng.normal(size=n), lambda f: bp_gain(f, 400, 7000, 1))[:n]
        # Two formants that wander like a mouth shaping vowels (but never real words).
        f1 = 500 + 350 * (0.5 + 0.5 * np.sin(2 * np.pi * rng.uniform(2.5, 4.0) * t + rng.uniform(0, 6)))
        f1 += smooth_jitter(rng, n, 6.0, 150.0)
        f2 = 1500 + 900 * (0.5 + 0.5 * np.sin(2 * np.pi * rng.uniform(3.0, 5.0) * t + rng.uniform(0, 6)))
        f2 += smooth_jitter(rng, n, 8.0, 400.0)
        x = svf(breath, f1, 6.0) + 0.8 * svf(breath, f2, 8.0) + 0.15 * fft_filter(breath, lambda f: hp_gain(f, 3500, 2))[:n]
        # Syllables: 4-7 soft puffs with gaps between them.
        env = np.zeros(n)
        tt = 0.05
        while tt < dur - 0.25:
            length = rng.uniform(0.12, 0.3)
            seg = np.arange(int(length * SR)) / SR
            puff = np.sin(np.pi * seg / length) ** 1.5 * rng.uniform(0.5, 1.0)
            add_at(env, puff, tt, False)
            tt += length + rng.uniform(0.03, 0.15)
        x = x * env
        wet = conv(x, reverb_ir(rng, 1.2, 0.25, lp=5000, predelay=0.01))[:n]
        save_ogg(norm(x) + 0.35 * norm(wet), SND / "sanity" / f"whisper{i}.ogg", peak=0.8, fade_out=0.2)


def snd_sanity_heartbeat() -> None:
    """One 'lub-dub' (0.55 s). The client retriggers it once per beat, so the rate can change."""
    rng = rng_for(2510)
    dur = 0.55
    n = int(dur * SR)
    x = np.zeros(n)
    for start, f0, amp in ((0.0, 52.0, 1.0), (0.16, 64.0, 0.7)):
        m = int(0.22 * SR)
        tm = np.arange(m) / SR
        # A low sine that drops in pitch, plus a muffled thump of noise: a beat felt in the chest.
        thump = np.sin(2 * np.pi * (f0 * tm - 12.0 * tm * tm)) * np.exp(-tm / 0.05)
        thump *= np.clip(tm / 0.004, 0, 1)
        body = fft_filter(rng.normal(size=m) * np.exp(-tm / 0.02), lambda f: lp_gain(f, 180, 2))[:m]
        add_at(x, amp * (norm(thump) + 0.35 * norm(body)), start, False)
    x = fft_filter(x, lambda f: lp_gain(f, 260, 2))[:n]
    save_ogg(x, SND / "sanity" / "heartbeat.ogg", peak=0.85, fade_in=0.001, fade_out=0.05)


def snd_sanity_knock() -> None:
    """A single close knock on a wall (dry, wooden, short room tail). 3 takes: different wood
    resonances (+-5%), hit noise and a slightly harder/softer strike."""
    for i, (seed, p, hd) in enumerate(((2520, 1.0, 0.003), (52520, 1.05, 0.0024), (62520, 0.95, 0.0036)), 1):
        rng = rng_for(seed)
        n = int(1.2 * SR)
        k = ring([rng.uniform(110, 130) * p, 190.0 * p, 290.0 * p, 520.0 * p], [0.06, 0.045, 0.03, 0.012], [1, 0.6, 0.4, 0.2], 0.3)
        m = len(k)
        hit = fft_filter(rng.normal(size=m) * np.exp(-np.arange(m) / (hd * SR)), lambda f: lp_gain(f, 2500, 2))[:m]
        dry = np.zeros(n)
        add_at(dry, norm(k) + 0.6 * norm(hit), 0.01, False)
        wet = conv(dry, reverb_ir(rng, 1.0, 0.3, lp=3000, predelay=0.01))[:n]
        name = "knock.ogg" if i == 1 else f"knock{i}.ogg"
        save_ogg(norm(dry) + 0.4 * norm(wet), SND / "sanity" / name, peak=0.85, fade_out=0.2)




# =============================================================================
# Supplies (v3): energy bar, calm pills, bandage, backpack, supply locker
# =============================================================================

def tex_energy_bar() -> None:
    """Energy bar lying diagonally: crimped silver foil ends, red wrapper, yellow bolt stripe."""
    rng = rng_for(3101)
    X, Y = grid((16, 16))
    u = ((X - 8) - (Y - 8)) / np.sqrt(2)          # along the bar (bottom-left -> top-right)
    v = ((X - 8) + (Y - 8)) / np.sqrt(2)          # across the bar
    inside = (np.abs(u) <= 7.2) & (np.abs(v) <= 2.4)
    foil = np.abs(u) > 5.0
    img = np.zeros((16, 16, 4))
    red = np.array([178, 38, 34]) + rng.normal(0, 4, (16, 16, 1))
    silver = np.array([196, 200, 208]) * (0.85 + 0.15 * np.cos(u[..., None] * 3.0))   # crimp ridges
    col = np.where(foil[..., None], silver, red)
    stripe = (~foil) & (np.abs(v) < 0.7) & (np.abs(u) < 4.0)
    col = np.where(stripe[..., None], np.array([246, 204, 60]), col)
    col = col * np.where((v < -1.4)[..., None], 1.12, 1.0)                              # lit top edge
    edge = inside & ((np.abs(v) > 1.6) | (np.abs(u) > 6.4))
    col = np.where(edge[..., None], col * 0.62, col)
    img[..., :3] = np.clip(col, 0, 255)
    img[..., 3] = np.where(inside, 255, 0)
    save_png(img, TEX / "item" / "energy_bar.png")


def tex_calm_pills() -> None:
    """Little amber-orange pill bottle with a white child-proof cap and a white label."""
    img = np.zeros((16, 16, 4))
    outline_cap, outline = (150, 150, 156, 255), (140, 64, 12, 255)
    # cap: rows 1..4, x 5..10, ridged
    for y in range(1, 5):
        for x in range(5, 11):
            c = 236 if x % 2 else 214
            img[y, x] = (c, c, c - 4, 255)
    img[1, 5:11] = (250, 250, 248, 255)
    img[1:5, 5] = img[1:5, 10] = outline_cap
    img[0, 6:10] = outline_cap
    # bottle: rows 5..14, x 4..11, translucent amber with pills showing at the bottom
    for y in range(5, 15):
        for x in range(4, 12):
            img[y, x] = (226, 120, 30, 235)
            if y >= 12:
                img[y, x] = (238, 214, 170, 245) if (x + y) % 3 else (210, 110, 34, 240)
        img[y, 4] = img[y, 11] = outline
    img[15, 5:11] = outline
    img[5, 4:12] = (196, 94, 20, 255)          # shoulder shadow under the cap
    # label band with a little red cross-ish mark and "text" line
    img[8:11, 5:11] = (246, 244, 236, 255)
    img[9, 6:8] = (60, 60, 70, 255)
    img[9, 9] = (196, 40, 40, 255)
    img[8, 5:11, :3] -= 12
    # glossy highlight
    for y in (6, 7, 11, 12, 13):
        img[y, 5] = (255, 214, 160, 240)
    save_png(img, TEX / "item" / "calm_pills.png")


def tex_bandage() -> None:
    """Rolled white gauze: a roll seen from the end (spiral) with a loose strip trailing off."""
    rng = rng_for(3103)
    img = np.zeros((16, 16, 4))
    # loose strip first (the roll is drawn over it)
    for i, x in enumerate(range(8, 15)):
        y0 = 10 + i // 2
        for y in (y0, y0 + 1, y0 + 2):
            if y < 16:
                c = 238 - 10 * (y == y0 + 2) + rng.normal(0, 3)
                img[y, x] = (c, c - 2, c - 10, 255)
    X, Y = grid((16, 16))
    # roll body: a short cylinder lying on its side, end face towards the viewer (circle)
    r = np.hypot(X - 7, Y - 7.5)
    roll = r <= 5.6
    weave = 232 + 8 * ((np.floor(X) + np.floor(Y)) % 2) + rng.normal(0, 3, (16, 16))
    spiral = np.cos(r * 2.2 - np.arctan2(Y - 7.5, X - 7) * 0.35) > 0.55
    shade = np.where(spiral, 0.82, 1.0) * (1.04 - 0.05 * (Y - 7.5) / 5.6)
    col = np.stack([weave, weave - 2, weave - 10], -1) * shade[..., None]
    col = np.where((r <= 1.3)[..., None], np.array([150, 140, 120]), col)       # hollow core
    col = np.where(((r > 4.8) & roll)[..., None], col * 0.78, col)              # outline
    img[roll, :3] = np.clip(col[roll], 0, 255)
    img[roll, 3] = 255
    save_png(img, TEX / "item" / "bandage.png")


# --- Backpack: faded olive canvas, blue-grey nylon webbing ---------------------------------

BACKPACK_CANVAS = (104, 110, 74)
BACKPACK_STRAP = (66, 78, 96)


def _canvas(rng, col, fade=0.15) -> np.ndarray:
    """Coarse cotton canvas: crosshatched weave, sun-faded blotches, grime."""
    X, Y = grid()
    img = fill(col, (S, S))
    weave = np.sin(X * np.pi / 2) * np.sin(Y * np.pi / 2)            # 4 px threads, over/under
    img = add_lum(img, weave * 5 + rng.normal(0, 3.5, (S, S)))
    img = add_lum(img, fnoise(rng, beta=1.4, aniso=(10, 1), kmin=4) * 3)   # slubby weft threads
    faded = smoothstep(0.3, 1.6, fnoise(rng, beta=2.6))
    img = lerp(img, img * 0.4 + np.array([150, 150, 128]) * 0.6, faded[..., None] * fade * 3)
    grime = smoothstep(0.5, 2.0, fnoise(rng, beta=2.2))
    return add_lum(img, -grime * 16)


def _stitch_border(img, inset=6, pitch=8, col=(70, 72, 50)) -> np.ndarray:
    """Darker seam piping round the edge + a dashed stitch line just inside it."""
    X, Y = grid()
    d = np.minimum(np.minimum(X, S - X), np.minimum(Y, S - Y))
    img = add_lum(img, -22 * smoothstep(3.0, 0.5, d))
    line = np.abs(d - inset) < 0.9
    dash = ((X + Y) % pitch) < pitch * 0.6
    return over(img, col, (line & dash) * 0.8)


def tex_backpack() -> None:
    rng = rng_for(3110)
    save_png(_stitch_border(_canvas(rng, BACKPACK_CANVAS)), _blk("backpack"))


def tex_backpack_flap() -> None:
    """Flap: same canvas a shade darker (less sun under it), wide stitched hem, a faded marker tag."""
    rng = rng_for(3111)
    img = _stitch_border(_canvas(rng, (92, 98, 66), fade=0.08), inset=10, pitch=10)
    img = over(img, (40, 40, 36), _text("K.W.", 64, 70, 22) * 0.35)                # owner's initials
    save_png(img, _blk("backpack_flap"))


def tex_backpack_pocket() -> None:
    """Front pocket face: canvas with a metal zipper across the top and a dangling pull tab."""
    rng = rng_for(3112)
    X, Y = grid()
    img = _stitch_border(_canvas(rng, BACKPACK_CANVAS))
    tape = box((S, S), 6, 24, S - 6, 40)
    img = over(img, (54, 58, 44), tape)                                             # zipper tape
    teeth = box((S, S), 6, 29, S - 6, 35) * ((X % 4) < 2)
    img = over(img, (150, 150, 140), teeth)
    img = add_lum(img, -18 * box((S, S), 6, 40, S - 6, 44))                         # shadow under it
    tab = box((S, S), 84, 30, 92, 62, soft=0.8)
    img = over(img, (170, 168, 156), tab)
    img = add_lum(img, 20 * box((S, S), 85, 31, 87, 60) - 25 * box((S, S), 86, 50, 90, 56))
    save_png(img, _blk("backpack_pocket"))


def tex_backpack_strap() -> None:
    """Nylon webbing (weave runs along v) with a grey plastic side-release buckle in the lower half."""
    rng = rng_for(3113)
    X, Y = grid()
    img = fill(BACKPACK_STRAP, (S, S))
    img = add_lum(img, np.sin(X * np.pi / 3) * 6 + fnoise(rng, beta=1.0, aniso=(1, 12), kmin=4) * 4)
    img = add_lum(img, -smoothstep(0.6, 2.0, fnoise(rng, beta=2.2)) * 12)            # grime
    img = add_lum(img, -14 * (smoothstep(8, 2, X) + smoothstep(S - 8, S - 2, X)))   # rolled edges
    buckle = box((S, S), 10, 76, S - 10, 112, soft=1.0)
    img = over(img, (46, 46, 50), buckle)
    img = add_lum(img, 26 * box((S, S), 14, 80, S - 14, 86) - 30 * box((S, S), 30, 92, S - 30, 100))
    save_png(img, _blk("backpack_strap"))


# --- Supply locker: grey-green painted steel ------------------------------------------------

LOCKER_PAINT = (86, 102, 88)


def _locker_paint(rng, col=LOCKER_PAINT) -> np.ndarray:
    """Old enamel on sheet steel: faint mottling, scuffs to bare grey metal, rust spots, low grime."""
    X, Y = grid()
    img = _smooth_concrete(rng, col, mottle=2.0, fine=0.7)
    for _ in range(14):                                                             # scratches
        x0, y0 = rng.uniform(0, S, 2)
        ang = rng.uniform(-0.5, 0.5) + (np.pi / 2 if rng.random() < 0.3 else 0)
        L = rng.uniform(8, 30)
        d = np.abs((X - x0) * np.sin(ang) - (Y - y0) * np.cos(ang))
        t = (X - x0) * np.cos(ang) + (Y - y0) * np.sin(ang)
        img = over(img, (150, 152, 150), ((d < 0.6) & (t > 0) & (t < L)) * rng.uniform(0.4, 0.8))
    scuff = smoothstep(1.2, 2.2, fnoise(rng, beta=2.0, kmin=3))
    img = over(img, (132, 134, 134), scuff * 0.7)
    rust = smoothstep(1.7, 2.6, fnoise(rng, beta=2.4))
    img = over(img, (112, 62, 34), rust * 0.85)
    return add_lum(img, -22 * smoothstep(S * 0.7, S, Y))                           # floor grime


def tex_supply_locker() -> None:
    rng = rng_for(3120)
    X, Y = grid()
    img = _locker_paint(rng)
    img = add_lum(img, 12 * smoothstep(4, 0, X) - 12 * smoothstep(S - 4, S, X))    # folded edges
    save_png(img, _blk("supply_locker"))


def tex_supply_locker_top() -> None:
    rng = rng_for(3121)
    img = _locker_paint(rng, (80, 94, 82))
    dust = smoothstep(-0.2, 1.2, fnoise(rng, beta=2.0))
    save_png(over(img, (150, 146, 136), dust * 0.35), _blk("supply_locker_top"))


def tex_supply_locker_front() -> None:
    """Locker door: pressed frame, two banks of louvre vents, faded stencil, kick scuffs at the bottom."""
    rng = rng_for(3122)
    X, Y = grid()
    img = _locker_paint(rng)
    # pressed frame: light top/left bevel, dark bottom/right bevel
    inner = box((S, S), 8, 8, S - 8, S - 8, soft=1.0)
    img = add_lum(img, 16 * (box((S, S), 6, 6, S - 6, 9) + box((S, S), 6, 6, 9, S - 6))
                  - 18 * (box((S, S), 6, S - 9, S - 6, S - 6) + box((S, S), S - 9, 6, S - 6, S - 6)))
    img = add_lum(img, -4 * (1 - inner))
    # louvre vents: 6 slots at the top and 4 at the bottom
    for y0, count in ((16, 6), (98, 4)):
        for i in range(count):
            y = y0 + i * 6
            slot = box((S, S), 28, y, S - 28, y + 3, soft=0.6)
            img = over(img, (14, 16, 14), slot)
            img = add_lum(img, 22 * box((S, S), 28, y + 3, S - 28, y + 4, soft=0.6))   # lit louvre lip
    # faded white stencil
    img = over(img, (214, 214, 204), _text("SUPPLIES", 64, 72, 15, maxw=80) * 0.5
               * (0.6 + 0.4 * smoothstep(-1, 1, fnoise(rng, beta=1.4))))
    # kick scuffs near the floor
    img = over(img, (60, 60, 58), smoothstep(0.4, 1.6, fnoise(rng, beta=1.8, aniso=(4, 1))) * smoothstep(S - 30, S - 8, Y) * 0.6)
    save_png(img, _blk("supply_locker_front"))


# --- Supply sounds ----------------------------------------------------------------------------

def _clank(rng, dur, freqs, decay) -> np.ndarray:
    """Struck sheet metal: a sharp noise transient into a few inharmonic ringing modes."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    rngf = [f * rng.uniform(0.97, 1.03) for f in freqs]
    tone = ring(rngf, [decay * (1 - 0.15 * i) for i in range(len(rngf))], [1 / (1 + 0.5 * i) for i in range(len(rngf))], dur)[:n]
    hit = fft_filter(rng.normal(size=n) * np.exp(-t / 0.006), lambda f: bp_gain(f, 800, 8000, 1))[:n]
    return norm(tone) * 0.6 + norm(hit)


def snd_locker_open() -> None:
    """Metal latch lifts (click-clack), then the thin door swings with a short hinge squeak."""
    rng = rng_for(3132)
    dur = 0.9
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    add_at(x, _clank(rng, 0.25, [2400, 3700, 5100], 0.05) * 0.8, 0.0, circular=False)
    add_at(x, _clank(rng, 0.3, [1900, 2950, 4300], 0.06), 0.07, circular=False)
    squeak = svf(rng.normal(size=n), np.interp(t, [0, dur], [1300, 1900]), 12.0)
    squeak *= smoothstep(0.18, 0.28, t) * (1 - smoothstep(0.55, 0.8, t)) * (1 + smooth_jitter(rng, n, 25, 0.5))
    door = _clank(rng, dur, [420, 690, 1130, 1580], 0.25) * smoothstep(0.15, 0.2, t) * 0.25
    x = x + 0.35 * norm(squeak) + door
    x = x + 0.25 * conv(x, reverb_ir(rng, 0.6, 0.15, lp=5000))[:n]
    save_ogg(x, SND / "supplies" / "locker_open.ogg", peak=0.8, fade_in=0.001, fade_out=0.08)


def snd_locker_close() -> None:
    """Thin steel door slams: a boomy panel thud, the latch catching, and a short metallic ring."""
    rng = rng_for(3133)
    dur = 0.9
    n = int(dur * SR)
    t = np.arange(n) / SR
    thud = fft_filter(rng.normal(size=n) * np.exp(-t / 0.04), lambda f: lp_gain(f, 500, 2))[:n]
    panel = _clank(rng, dur, [310, 520, 870, 1240, 1710], 0.3)
    x = norm(thud) + 0.6 * panel
    add_at(x, _clank(rng, 0.25, [2600, 4100, 5600], 0.04) * 0.7, 0.045, circular=False)
    x = x + 0.3 * conv(x, reverb_ir(rng, 0.7, 0.18, lp=4500))[:n]
    save_ogg(x, SND / "supplies" / "locker_close.ogg", peak=0.85, fade_in=0.001, fade_out=0.1)


def snd_pills_rattle() -> None:
    """Three shakes of a plastic pill bottle: bursts of small hard clicks."""
    rng = rng_for(3134)
    dur = 0.6
    times = []
    for c in (0.02, 0.2, 0.38):
        times += list(c + np.sort(rng.exponential(0.025, 14)).clip(0, 0.12))
    x = clicks(rng, times, dur, 2500, 6500, 0.0006, 0.0018, amp_pow=1.5)
    n = len(x)
    body = fft_filter(x, lambda f: bp_gain(f, 600, 2500, 1))[:n]                 # the hollow bottle
    save_ogg(norm(x) + 0.4 * norm(body), SND / "supplies" / "pills_rattle.ogg", peak=0.7, fade_in=0.001, fade_out=0.05)


def snd_bandage_wrap() -> None:
    """~1.3 s of gauze being unrolled and wound round an arm: soft fabric swishes, a little crackle."""
    rng = rng_for(3135)
    dur = 1.3
    n = int(dur * SR)
    t = np.arange(n) / SR
    env = np.zeros(n)
    for c in (0.12, 0.5, 0.88):
        env += np.exp(-0.5 * ((t - c) / 0.09) ** 2)
    fc = 1800 + 1400 * env
    swish = svf(rng.normal(size=n), fc, 0.8) * env * (1 + smooth_jitter(rng, n, 20, 0.4))
    crackle = _crackle(rng, dur, 900, f_lo=3000) * (0.3 + env)
    x = norm(swish) + 0.25 * norm(crackle)
    save_ogg(x, SND / "supplies" / "bandage_wrap.ogg", peak=0.65, fade_in=0.01, fade_out=0.08)


# ---- Safe rooms & checkpoints ----------------------------------------------------------------

RADIO_OLIVE = (86, 92, 62)   # army-green painted steel of the old field radio


def _radio_case(rng) -> np.ndarray:
    """Olive-drab painted steel: soft mottling, chipped paint showing dark metal, a little grime."""
    img = _smooth_concrete(rng, RADIO_OLIVE, mottle=3.0, fine=1.0)
    chips = smoothstep(1.6, 2.2, fnoise(rng, beta=1.2, kmin=4))
    img = over(img, np.array([58, 56, 52]), chips * 0.8)
    grime = smoothstep(0.5, 1.8, fnoise(rng, beta=2.6))
    return over(img, np.array([40, 40, 30]), grime * 0.3)


def tex_checkpoint_radio_case() -> None:
    save_png(_radio_case(rng_for(2601)), _blk("checkpoint_radio_case"))


def tex_checkpoint_radio_front() -> None:
    """Field radio front panel, drawn in the top 77 rows (the model maps the 10x6 px front onto
    them): round-hole speaker grille on the left, a backlit amber tuning dial with a red needle on
    the right, dark bezels where the two 3D knobs sit, and a stencilled channel label."""
    rng = rng_for(2602)
    X, Y = grid()
    img = _radio_case(rng)
    H = 77
    frame = box((S, S), 3, 3, S - 3, H - 3) - box((S, S), 6, 6, S - 6, H - 6)      # inset panel line
    img = add_lum(img, -22 * np.clip(frame, 0, 1))
    holes = np.zeros((S, S))
    for cy in range(20, 70, 8):                                                      # speaker grille
        for cx in range(14, 62, 8):
            holes = np.maximum(holes, np.clip(2.6 - np.hypot(X - cx, Y - cy), 0, 1))
    img = over(img, np.array([18, 18, 16]), holes)
    dial = box((S, S), 72, 12, 120, 40, soft=0.8)                                   # tuning dial
    glass = lerp(np.array([255, 196, 96]), np.array([214, 140, 52]), np.clip(np.abs(Y - 26) / 16, 0, 1)[..., None])
    img = over(img, glass, dial)
    ticks = np.zeros((S, S))
    for i, x in enumerate(np.linspace(76, 116, 11)):
        ticks = np.maximum(ticks, box((S, S), x - 0.6, 14, x + 0.6, 20 if i % 2 else 23))
    img = over(img, np.array([60, 36, 16]), ticks * dial)
    img = over(img, np.array([200, 30, 24]), box((S, S), 99, 13, 101, 39) * dial)   # needle
    img = add_lum(img, -30 * np.clip(blur(dial, 1.5) - dial, 0, 1) * 2)             # bezel shadow
    for cx in (82, 108):                                                             # knob bezels
        d = np.hypot(X - cx, Y - 58)
        img = add_lum(img, -30 * (np.clip(9.5 - d, 0, 1) - np.clip(7.0 - d, 0, 1)))
    img = over(img, np.array([214, 210, 180]), _text("CH 7", 38, 11, 8, maxw=40) * 0.85)
    save_png(img, _blk("checkpoint_radio_front"))


def tex_safe_room_lamp() -> None:
    """Safe-room ceiling lamp seen from below: a round frosted warm-white glass dome in a square
    off-white metal frame. It isn't drawn full-bright in game, so the glass is painted bright."""
    rng = rng_for(2603)
    X, Y = grid()
    img = _smooth_concrete(rng, (214, 208, 196), mottle=2.0, fine=0.6)              # painted frame
    img = add_lum(img, -18 * (1 - box((S, S), 4, 4, S - 4, S - 4)))                 # edge seam
    r = np.hypot(X - 64, Y - 64)
    dome = np.clip(50.5 - r, 0, 1)
    glass = lerp(np.array([255, 246, 222]), np.array([250, 206, 140]), (np.clip(r / 50, 0, 1) ** 1.6)[..., None])
    glass = add_lum(glass, fnoise(rng, beta=2.2) * 2.5)
    img = add_lum(img, -26 * np.clip(np.clip(54.5 - r, 0, 1) - dome, 0, 1))          # metal rim
    img = over(img, glass, dome)
    for cx, cy in ((64, 9), (64, 119), (9, 64), (119, 64)):                        # rim screws
        img = over(img, np.array([150, 140, 120]), np.clip(3 - np.hypot(X - cx, Y - cy), 0, 1))
    save_png(img, _blk("safe_room_lamp"))


def tex_safe_room_sign() -> None:
    """'SAFE' plaque (16x12 px in game, drawn in the top 96 rows): deep green enamel, white border,
    our own shelter emblem (a house with a doorway inside a ring) and the word SAFE. The rows
    below stay plain green; the plaque's thin edges use them."""
    rng = rng_for(2604)
    X, Y = grid()
    H = 96
    green, white = np.array([24, 92, 58]), np.array([236, 236, 226])
    img = add_lum(fill(green, (S, S)), fnoise(rng, beta=2.4) * 3)
    border = box((S, S), 4, 4, S - 4, H - 4) - box((S, S), 8, 8, S - 8, H - 8)
    img = over(img, white, np.clip(border, 0, 1))
    cx, cy = 32, 48
    r = np.hypot(X - cx, Y - cy)
    img = over(img, white, np.clip(22.5 - r, 0, 1) - np.clip(18.5 - r, 0, 1))       # ring
    house = pil_mask(lambda d, k: d.polygon([((cx - 10) * k, (cy + 12) * k), ((cx - 10) * k, (cy - 2) * k),
                                             (cx * k, (cy - 12) * k), ((cx + 10) * k, (cy - 2) * k),
                                             ((cx + 10) * k, (cy + 12) * k)], fill=255))
    img = over(img, white, house)
    img = over(img, green, box((S, S), cx - 3.5, cy + 3, cx + 3.5, cy + 12.5) * house)   # doorway
    img = over(img, white, _text("SAFE", 89, 50, 32, maxw=58))
    img = _weather(img, rng, dirt=0.15, scratches=5)
    save_png(img, _blk("safe_room_sign"))


def snd_checkpoint_save() -> None:
    """~2.4 s: the field radio crackles to life (static swelling in, then a squelch cuts it
    down), then a soft two-note chime through its little speaker: 'checkpoint saved'."""
    rng = rng_for(2605)
    dur = 2.4
    n = int(dur * SR)
    t = np.arange(n) / SR
    hiss = fft_filter(rng.normal(size=n), lambda f: bp_gain(f, 500, 4500, 2))[:n]
    static = norm(hiss) * (0.65 + 0.35 * np.sin(2 * np.pi * 7 * t) ** 2) + 0.7 * norm(_crackle(rng, dur, 120))
    static *= np.interp(t, [0, 0.08, 0.55, 0.68, dur], [0, 1, 0.9, 0.1, 0.0])
    chime = np.zeros(n)
    for start, f in ((0.72, 659.3), (1.0, 880.0)):                                  # E5 then A5
        bell = ring([f, 2.0 * f, 2.76 * f, 5.4 * f], [0.7, 0.35, 0.2, 0.08], [1, 0.35, 0.2, 0.08], 1.3)
        add_at(chime, bell * np.clip(t_axis(1.3) / 0.004, 0, 1), start, False)
    chime = fft_filter(chime, lambda f: bp_gain(f, 350, 5000, 2))[:n]               # small speaker
    x = 0.45 * norm(static) + norm(chime)
    wet = conv(x, reverb_ir(rng, 0.8, 0.2, lp=4000, predelay=0.008))[:n]
    save_ogg(x + 0.2 * norm(wet) * np.abs(x).max(), SND / "safe_room" / "checkpoint_save.ogg", peak=0.8, fade_out=0.15)


# =============================================================================
# Main menu (title screen) textures. Fonts: Anton and VT323 (both SIL Open Font License, see
# tools/fonts/*-OFL.txt); only rasterised text ends up in these images.
# =============================================================================

FONTS = Path(__file__).resolve().parent / "fonts"
MENU = TEX / "gui" / "menu"


def tex_menu_logo() -> None:
    """1024x384: "THE BACKROOMS" in tall pale-yellow letters with a VHS look (red/cyan fringes,
    scanline gaps, a few rows shoved sideways) and "FOUND FOOTAGE" spaced out below."""
    rng = rng_for(9101)
    w, h = 1024, 384
    margin = 24  # room for the sideways shoves and colour fringes, so nothing wraps around

    def fitted(path, text, max_w, size, spacing=0):
        """Largest font size (<= size) whose text, with extra letter spacing, fits max_w."""
        while size > 10:
            font = ImageFont.truetype(str(path), size)
            probe = ImageDraw.Draw(Image.new("L", (1, 1)))
            width = sum(probe.textlength(ch, font=font) + spacing for ch in text) - spacing if spacing else probe.textlength(text, font=font)
            if width <= max_w:
                return font
            size -= 2
        return ImageFont.truetype(str(path), size)

    big = fitted(FONTS / "Anton-Regular.ttf", "THE BACKROOMS", w - 2 * margin, 220)
    small = fitted(FONTS / "VT323-Regular.ttf", "FOUND FOOTAGE", w - 2 * margin, 96, spacing=30)

    def text_mask(text, font, top, spacing=0):
        """White-on-black mask of the text with its ink top at y = top; returns (mask, ink bottom)."""
        m = Image.new("L", (w, h), 0)
        d = ImageDraw.Draw(m)
        y = top - d.textbbox((0, 0), text, font=font)[1]
        if spacing:
            x = margin
            for ch in text:
                d.text((x, y), ch, font=font, fill=255)
                x += d.textlength(ch, font=font) + spacing
        else:
            d.text((margin, y), text, font=font, fill=255)
        a = np.asarray(m, float) / 255.0
        rows = np.nonzero(a.max(axis=1) > 0.5)[0]
        return a, int(rows.max()) if rows.size else top

    # Layout: a small "THE", a huge "BACKROOMS" filling the width, then "FOUND FOOTAGE".
    the_font = ImageFont.truetype(str(FONTS / "Anton-Regular.ttf"), 76)
    big = fitted(FONTS / "Anton-Regular.ttf", "BACKROOMS", w - 2 * margin, 300)
    the_mask, the_bottom = text_mask("THE", the_font, 8)
    word, title_bottom = text_mask("BACKROOMS", big, the_bottom + 12)
    title = np.maximum(the_mask, word)
    sub, _ = text_mask("FOUND FOOTAGE", small, title_bottom + 20, spacing=30)
    # Grimy fill: pale fluorescent yellow with darker blotches, a little lighter at the top.
    grime = fnoise(rng, (h, w), beta=2.2)
    ramp = np.linspace(1.0, 0.82, h)[:, None]
    yellow = np.stack([233 * ramp + 10 * grime, 214 * ramp + 10 * grime, 128 * ramp + 8 * grime], axis=2)
    white = fill((226, 226, 218), (h, w))

    def shove(a, rows):
        out = a.copy()
        for y0, y1, dx in rows:
            out[y0:y1] = np.roll(a[y0:y1], dx, axis=1)
        return out

    # Tape damage: a few thin bands pushed sideways (same bands for fill and fringes).
    rows = []
    for _ in range(3):
        y0 = int(rng.integers(the_bottom + 20, max(the_bottom + 21, title_bottom - 10)))
        rows.append((y0, y0 + int(rng.integers(3, 7)), int(rng.integers(-9, 10))))
    title = shove(title, rows)
    # Scanline gaps: every 4th row a little see-through.
    scan = np.where(np.arange(h) % 4 == 3, 0.8, 1.0)[:, None]
    rgb = np.zeros((h, w, 3))
    alpha = np.zeros((h, w))
    # Chromatic fringes: red copy left, cyan copy right, under the main letters.
    for mask, dx, col in ((title, -4, (220, 40, 40)), (title, 4, (40, 200, 220)), (sub, -2, (220, 40, 40)), (sub, 2, (40, 200, 220))):
        m = np.roll(mask, dx, axis=1) * 0.55
        rgb = rgb * (1 - m[..., None]) + np.array(col, float) * m[..., None]
        alpha = np.maximum(alpha, m)
    for mask, colour in ((title, yellow), (sub, white)):
        m = mask * scan
        rgb = rgb * (1 - m[..., None]) + colour * m[..., None]
        alpha = np.maximum(alpha, m)
    # Soft dark shadow so the logo reads on bright scenes.
    sh = np.roll(np.roll(np.maximum(title, sub), 5, axis=0), 5, axis=1)
    for _ in range(3):
        sh = blur_wrap(sh, 2)
    sh_a = np.clip(sh * 0.7 - alpha, 0, 1)
    out = np.concatenate([rgb * (alpha / np.maximum(alpha + sh_a, 1e-6))[..., None], (np.clip(alpha + sh_a, 0, 1) * 255)[..., None]], axis=2)
    save_png(out, MENU / "logo.png")


def tex_menu_noise() -> None:
    """256x256 tileable grey video noise; alpha 0..60 so it can be laid over anything."""
    rng = rng_for(9102)
    v = rng.random((256, 256))
    g = 90 + 140 * v
    a = 60 * np.abs(rng.standard_normal((256, 256))).clip(0, 2.5) / 2.5
    save_png(np.stack([g, g, g, a], axis=2), MENU / "noise.png")


def tex_menu_vignette() -> None:
    """512x288 black vignette: clear in the middle, ~75% black in the corners."""
    w, h = 512, 288
    y, x = np.mgrid[0:h, 0:w]
    d = np.hypot((x - w / 2) / (w / 2), (y - h / 2) / (h / 2)) / np.sqrt(2)
    a = 255 * 0.78 * np.clip((d - 0.35) / 0.65, 0, 1) ** 1.6
    z = np.zeros((h, w))
    save_png(np.stack([z, z, z, a], axis=2), MENU / "vignette.png")


def tex_menu_note() -> None:
    """256x320 taped note: yellowed lined paper, coffee ring, creases, two strips of tape."""
    rng = rng_for(9103)
    w, h = 256, 320
    n = fnoise(rng, (h, w), beta=1.8)
    paper = np.stack([228 + 8 * n, 214 + 8 * n, 168 + 8 * n], axis=2)
    y, x = np.mgrid[0:h, 0:w]
    # Faint blue ruled lines and a red margin line.
    lines = (np.abs((y - 52) % 22) < 1) & (y > 40)
    paper[lines] = paper[lines] * 0.85 + np.array([110, 140, 190]) * 0.15
    margin = np.abs(x - 30) < 1
    paper[margin] = paper[margin] * 0.8 + np.array([200, 80, 80]) * 0.2
    # Coffee ring.
    r = np.hypot(x - 190, y - 250)
    ring = np.exp(-((r - 34) ** 2) / 6.0) * 0.35 + (r < 34) * 0.06
    paper = paper * (1 - ring[..., None]) + np.array([120, 80, 40]) * ring[..., None]
    # Diagonal crease and darker edges.
    crease = np.exp(-((x * 0.55 + y * 0.83 - 210) ** 2) / 8.0) * 0.12
    edge = np.clip(1 - np.minimum.reduce([x, y, w - 1 - x, h - 1 - y]) / 14.0, 0, 1) * 0.25
    paper *= (1 - crease - edge)[..., None]
    alpha = np.full((h, w), 255.0)
    # Tape strips (translucent grey) at the top corners.
    for cx in (40, 216):
        band = (np.abs((x - cx) * 0.7 + (y - 6) * 0.7 * (1 if cx < 128 else -1)) < 26) & (np.abs((x - cx) * 0.7 - (y - 6) * 0.7 * (1 if cx < 128 else -1)) < 9)
        paper[band] = paper[band] * 0.7 + np.array([235, 235, 225]) * 0.3
    save_png(np.concatenate([paper, alpha[..., None]], axis=2), MENU / "note.png")

# =============================================================================
# Pool water (client/water): ripple normal map, caustic network, shallow-water item
# icon, wading steps and a soft splash. Run: python tools/gen_assets.py tex_water_ snd_water_
# =============================================================================

WATER_TEX = TEX / "water"
# Linear filtering and repeat wrap: the water shader tiles these across the pools.
WATER_TEX_META = '{"texture": {"blur": true, "clamp": false}}\n'


def tex_water_ripples() -> None:
    """Tileable ripple normal map, 256 px (= 8 blocks in the shader's first layer).

    Height = band-limited fractal noise (wavelengths ~0.4-3 blocks) plus a finer chop.
    R/G = slope along x/z mapped to 0..1 (0.5 = flat), scaled so the steepest 1% reach the ends;
    B = height. Rows are z, columns are x (texture v / u).
    """
    n = 256
    rng = rng_for(4100)
    h = fnoise(rng, (n, n), beta=3.0, kmin=2.5, kmax=20.0)
    h += 0.35 * fnoise(rng, (n, n), beta=2.0, kmin=14.0, kmax=40.0)
    h = blur(h, 1.0)
    gx = (np.roll(h, -1, axis=1) - np.roll(h, 1, axis=1)) * 0.5
    gz = (np.roll(h, -1, axis=0) - np.roll(h, 1, axis=0)) * 0.5
    scale = np.percentile(np.hypot(gx, gz), 99.0) + 1e-9
    gx = np.clip(gx / scale, -1.0, 1.0)
    gz = np.clip(gz / scale, -1.0, 1.0)
    hh = (h - h.min()) / (h.max() - h.min() + 1e-9)
    img = np.stack([(gx * 0.5 + 0.5) * 255, (gz * 0.5 + 0.5) * 255, hh * 255], axis=-1)
    save_png(img, WATER_TEX / "ripples.png")
    (WATER_TEX / "ripples.png.mcmeta").write_text(WATER_TEX_META)


def _water_caustic_net(n, cells, seed, line_px, glow_px, warp_px) -> np.ndarray:
    """Seamless caustic web (0..1): the walls of Voronoi cells (where the nearest and second
    nearest seed are almost equally far), made wavy by a smooth domain warp. Seeds sit on a
    jittered grid so the cells have similar sizes, like real pool caustics."""
    rng = rng_for(seed)
    X, Y = grid((n, n))
    X = X + fnoise(rng, (n, n), beta=3.0, kmin=1.0, kmax=6.0) * warp_px
    Y = Y + fnoise(rng, (n, n), beta=3.0, kmin=1.0, kmax=6.0) * warp_px
    g = int(round(np.sqrt(cells)))
    d1 = np.full((n, n), 1e9)
    d2 = np.full((n, n), 1e9)
    for i in range(g):
        for j in range(g):
            px = (i + rng.uniform(0.15, 0.85)) * n / g
            py = (j + rng.uniform(0.15, 0.85)) * n / g
            d = np.hypot(wrapd(X - px, n), wrapd(Y - py, n))
            d2 = np.where(d < d1, d1, np.minimum(d2, d))
            d1 = np.minimum(d1, d)
    edge = d2 - d1
    web = np.exp(-(edge / line_px) ** 2) + 0.35 * np.exp(-(edge / glow_px) ** 2)
    # Some stretches of the web are brighter than others (focus varies across the pool).
    web *= 0.75 + 0.25 * np.clip(fnoise(rng, (n, n), beta=2.5, kmin=1.0, kmax=5.0), -1.5, 1.5) / 1.5
    return np.clip(blur(web, 0.6), 0.0, 1.0)


def tex_water_caustics() -> None:
    """Tileable caustic network, 256 px (= 4 blocks in the shader): ~0.5-block cells."""
    c = _water_caustic_net(256, 64, 4110, line_px=2.4, glow_px=9.0, warp_px=6.0)
    save_png(np.repeat(c[..., None] * 255, 3, axis=2), WATER_TEX / "caustics.png")
    (WATER_TEX / "caustics.png.mcmeta").write_text(WATER_TEX_META)


def tex_water_shallow_item() -> None:
    """16x16 icon for the shallow water block item: a flat sea-green puddle with ripple rings."""
    n = 16
    X, Y = grid((n, n))
    r = np.hypot((X - 8.0) / 7.3, (Y - 9.0) / 4.4)
    body = smoothstep(1.0, 0.9, r)
    shade = smoothstep(0.0, 1.0, r)[..., None]
    col = np.array([88.0, 214.0, 178.0]) * (1 - shade) + np.array([22.0, 122.0, 98.0]) * shade
    rings = np.exp(-((r - 0.45) / 0.08) ** 2) * 0.6 + np.exp(-((r - 0.78) / 0.07) ** 2) * 0.45
    col = col + rings[..., None] * np.array([150.0, 120.0, 100.0])
    glint = np.exp(-(((X - 5.0) / 1.6) ** 2 + ((Y - 7.5) / 0.9) ** 2))
    col = col + glint[..., None] * 140.0
    img = np.concatenate([np.clip(col, 0, 255), (body * 235.0)[..., None]], axis=2)
    save_png(img, TEX / "item" / "shallow_water.png")


def _water_bubble(rng, f0, dur) -> np.ndarray:
    """One small bubble / drip: a sine whose pitch rises as it decays (the classic 'plip')."""
    t = t_axis(dur)
    f = f0 * (1.0 + 1.3 * t / dur)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / (dur / 3)) * smoothstep(0, 0.002, t)


def _water_slosh(rng, dur, lo, hi, attack, decay) -> np.ndarray:
    """Band-passed noise burst: water pushed aside / running off."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = fft_filter(rng.normal(size=n), lambda f: bp_gain(f, lo, hi, 2))[:n]
    return norm(x) * smoothstep(0, attack, t) * np.exp(-np.maximum(t - attack, 0) / decay)


def _water_bubbles(rng, n, count, t0, spread, f_lo, f_hi, amp, rise=0.25, skew=1.6) -> np.ndarray:
    """A cloud of air bubbles, the sound that makes water sound like water.

    Each bubble rings at its Minnaert frequency (small bubble = high pitch) and dies away fast,
    gliding slightly UP in pitch as it rises (falling pitch is what makes a thud sound like mud).
    Bigger bubbles ring longer and louder. `count` bubbles start between t0 and t0 + spread
    seconds, bunched towards t0 (skew > 1)."""
    out = np.zeros(n)
    for _ in range(count):
        f0 = float(np.exp(rng.uniform(np.log(f_lo), np.log(f_hi))))
        decay = 0.043 * f0 + 0.0014 * f0 ** 1.5          # 1/s, from the bubble physics literature
        dur = min(6.0 / decay, 0.25)
        t = np.arange(int(dur * SR)) / SR
        f = f0 * (1.0 + rise * decay * t)
        b = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-decay * t)
        b *= smoothstep(0, 0.0006, t) * amp * rng.uniform(0.3, 1.0) * (1000.0 / f0) ** 0.5
        add_at(out, b, t0 + spread * rng.uniform(0, 1) ** skew, False)
    return out


def _water_noise(rng, n, t0, attack, decay, lo, hi, gurgle=0.0) -> np.ndarray:
    """Water being shoved aside: band-passed noise, optionally 'gurgling' (fast, irregular
    wobble of the level, like the surface breaking up)."""
    t = np.arange(n) / SR
    x = fft_filter(rng.normal(size=n), lambda f: bp_gain(f, lo, hi, 2))[:n]
    x = norm(x)
    env = smoothstep(t0, t0 + attack, t) * np.exp(-np.maximum(t - t0 - attack, 0) / decay)
    if gurgle:
        wob = fft_filter(rng.normal(size=n), lambda f: bp_gain(f, 18, 60, 2))[:n]
        env *= np.clip(1 + gurgle * wob / (np.abs(wob).max() + 1e-9), 0, None)
    return x * env


def _wade_step(rng, run: bool) -> np.ndarray:
    """One step through knee-deep water.

    walk: the leg pushes through the water (a gurgling slosh full of bubbles), the foot lifts
          out with water running off it, a few drips.
    run:  the foot slaps down into the water: a bright splash of spray and a burst of bubbles,
          the leg ploughs through, then the spray rains back down onto the surface.
    """
    dur = 0.75 if run else 0.8
    n = int(dur * SR)
    out = np.zeros(n)
    if run:
        # Foot hits the surface: a short bright crack of spray, and the surface tearing open.
        out += 0.45 * _water_noise(rng, n, 0.0, 0.002, 0.025, 1500, 9000)
        out += 0.2 * _water_noise(rng, n, 0.004, 0.006, 0.06, 400, 2500, gurgle=0.8)
        out += _water_bubbles(rng, n, 110, 0.0, 0.12, 500, 3500, 0.4)
        # The leg ploughing on: a long sloshing swish.
        out += 0.18 * _water_noise(rng, n, 0.06, 0.06, 0.16, 250, 1800, gurgle=1.2)
        out += _water_bubbles(rng, n, 70, 0.06, 0.25, 250, 1600, 0.35, skew=1.0)
        # Spray raining back down: lots of tiny, high drops.
        out += _water_bubbles(rng, n, 80, 0.12, 0.45, 1500, 5000, 0.16, rise=0.6, skew=1.3)
        out += 0.05 * _water_noise(rng, n, 0.12, 0.05, 0.2, 3000, 10000)
    else:
        # The leg pushing forward through the water.
        out += 0.22 * _water_noise(rng, n, 0.0, 0.09, 0.2, 200, 1400, gurgle=1.4)
        out += _water_bubbles(rng, n, 60, 0.02, 0.3, 250, 1800, 0.35, skew=1.0)
        # The foot lifting out, water pouring off it.
        lift = rng.uniform(0.28, 0.36)
        out += 0.12 * _water_noise(rng, n, lift, 0.03, 0.12, 600, 4500, gurgle=0.8)
        out += _water_bubbles(rng, n, 40, lift, 0.2, 700, 3000, 0.25)
        # Drips falling back.
        out += _water_bubbles(rng, n, 16, lift + 0.08, 0.35, 1500, 4500, 0.14, rise=0.8, skew=1.0)
    out = fft_filter(out, lambda f: hp_gain(f, 120, 2) * lp_gain(f, 11000 if run else 8000, 2))[:n]
    # A tiled pool hall: short, bright early reflections and a little tail.
    ir = reverb_ir(rng, 0.9, 0.22, lp=6000, predelay=0.006, early=((0.011, 0.5), (0.019, 0.35), (0.031, 0.25)))
    return out + 0.35 * conv(out, ir)[:n]

def snd_water_wade() -> None:
    """Wading steps through knee-deep water, 4 walk + 4 run: bubble clouds, slosh, spray, drips."""
    for i in range(1, 5):
        for run in (False, True):
            rng = rng_for(4200 + (10 if run else 0) + i)
            kind = "run" if run else "walk"
            save_ogg(_wade_step(rng, run), SND / "water" / f"wade_{kind}{i}.ogg",
                     peak=0.85 if run else 0.9, fade_in=0.002, fade_out=0.04)


def snd_water_splash() -> None:
    """Soft splash when dropping into a pool, 3 variants (~1.4 s): muffled impact, low whump,
    a burst of bubbles, drops falling back, tiled-room reverb."""
    for i in range(1, 4):
        rng = rng_for(4300 + i)
        dur = 1.4
        n = int(dur * SR)
        out = np.zeros(n)
        add_at(out, _water_slosh(rng, 0.5, 200, 3000, 0.006, 0.12), 0.0, False)
        wt = t_axis(0.25)
        sweep = np.linspace(rng.uniform(110, 140), rng.uniform(55, 70), wt.size)
        add_at(out, np.sin(2 * np.pi * np.cumsum(sweep) / SR) * np.exp(-wt / 0.07) * 0.6, 0.004, False)
        for _ in range(int(rng.integers(25, 40))):
            st = 0.03 + 0.67 * rng.uniform(0, 1) ** 1.8          # most bubbles right after the impact
            b = _water_bubble(rng, rng.uniform(300, 1300), rng.uniform(0.02, 0.07))
            add_at(out, b * rng.uniform(0.1, 0.35) * np.exp(-st / 0.4), st, False)
        for _ in range(int(rng.integers(10, 18))):
            d = _water_bubble(rng, rng.uniform(900, 2200), rng.uniform(0.01, 0.025))
            add_at(out, d * rng.uniform(0.05, 0.15), rng.uniform(0.25, 1.15), False)
        out = fft_filter(out, lambda f: lp_gain(f, 4500, 2))[:n]
        out = out + 0.3 * conv(out, reverb_ir(rng, 1.0, 0.3, lp=3500, predelay=0.012))[:n]
        save_ogg(out, SND / "water" / f"splash{i}.ogg", peak=0.8, fade_in=0.002, fade_out=0.2)


# =============================================================================
# Level 4: The Abandoned Office. Albedos are chosen so that, under the cool-white
# Veil panel light, lit surfaces land near the reference samples (refs REPORT):
# lit greige carpet ~#867E77, cream walls ~#D8CFC1, ceiling tile ~#A09F9C..#C9C6BD,
# partitions ~#74706A. Run only these with:  python tools/gen_assets.py _l4_
# =============================================================================

L4_CARPET = (124, 116, 110)        # greige-taupe loop pile
L4_CARPET_BLUE = (70, 82, 106)     # blue-grey commercial carpet
L4_WALL = (216, 207, 193)          # off-white / cream drywall
L4_TILE = (201, 198, 189)          # acoustic ceiling tile
L4_TBAR = (236, 235, 230)          # ceiling grid
L4_FABRIC = (116, 112, 106)        # cubicle partition fabric
L4_VINYL = (44, 42, 40)            # vinyl base strip


def _l4_carpet(seed, col, indent=False) -> np.ndarray:
    """Commercial loop-pile carpet: tight rows of loops, fine two-tone fleck, soft traffic wear.

    The indent version is the patch that sat under furniture for years: darker, a touch more
    saturated (not sun-faded), with less wear.
    """
    rng = rng_for(seed)
    X, Y = grid()
    base = np.array(col, float)
    if indent:
        # Unfaded: darker, and a little more saturated (pushed away from its own grey).
        grey = base.mean()
        base = (grey + (base - grey) * 1.25) * 0.80
    img = fill(base, (S, S))
    # Loops: rows of tiny bumps every 4 px, offset by half a loop every other row (4 divides 128: seamless).
    loops = 0.5 + 0.5 * np.cos(2 * np.pi * X / 4 + np.pi * (np.floor(Y / 4) % 2)) * np.cos(2 * np.pi * Y / 4)
    lum = (loops - 0.5) * 9.0
    lum += rng.normal(0, 7.0, (S, S))                                 # fibre noise
    lum += np.kron(rng.normal(0, 4.0, (S // 2, S // 2)), np.ones((2, 2)))
    lum += fnoise(rng, beta=2.8) * (3.0 if indent else 5.5)           # soft mottle
    img = add_lum(img, lum, (1.0, 1.0, 1.02))
    # Two-tone fleck: dark and light fibres.
    dark = rng.random((S, S)) < 0.06
    light = rng.random((S, S)) < 0.035
    img = over(img, base * 0.55, dark * 0.8)
    img = over(img, np.minimum(base * 1.35 + 12, 255), light * 0.7)
    if not indent:
        # Traffic wear: lighter, flattened streaks.
        wear = smoothstep(0.6, 2.0, fnoise(rng, beta=3.0, aniso=(1.0, 2.5)))
        img = add_lum(img, wear * 9.0)
    return img


def tex_l4_carpet() -> None:
    save_png(_l4_carpet(4101, L4_CARPET), _blk("office_carpet"))
    save_png(_l4_carpet(4101, L4_CARPET, indent=True), _blk("office_carpet_indent"))
    save_png(_l4_carpet(4102, L4_CARPET_BLUE), _blk("office_carpet_blue"))
    save_png(_l4_carpet(4102, L4_CARPET_BLUE, indent=True), _blk("office_carpet_blue_indent"))


def _l4_drywall(seed, col=L4_WALL) -> np.ndarray:
    """Painted drywall: fine orange-peel roller texture, faint patching, very slight grime."""
    rng = rng_for(seed)
    img = fill(col, (S, S))
    peel = blur(rng.normal(0, 1, (S, S)), 0.9) * 5.0 + blur(rng.normal(0, 1, (S, S)), 2.0) * 3.0
    img = add_lum(img, peel + fnoise(rng, beta=3.0) * 3.0, (1.0, 0.99, 0.97))
    patch = smoothstep(1.4, 2.4, fnoise(rng, beta=2.6))              # spackle patches, a touch whiter
    img = add_lum(img, patch * 5.0)
    grime = smoothstep(1.0, 2.6, fnoise(rng, beta=2.0)) * 0.12
    return over(img, np.array(col) * 0.8, grime)


def _l4_base_strip(img, height=11) -> np.ndarray:
    """Dark vinyl cove base along the bottom, with a highlight on its rounded top and scuffs."""
    rng = rng_for(4110)
    X, Y = grid()
    top = S - height
    strip = box((S, S), -1, top, S + 1, S + 1)
    vinyl = add_lum(fill(L4_VINYL, (S, S)), rng.normal(0, 2.0, (S, S)) + fnoise(rng, beta=2.0, aniso=(6, 1)) * 2.5)
    img = over(img, vinyl, strip)
    img = add_lum(img, 26 * box((S, S), -1, top, S + 1, top + 1.5) - 8 * box((S, S), -1, S - 2, S + 1, S + 1))
    img = add_lum(img, -14 * box((S, S), -1, top - 3, S + 1, top) * 0.6)   # shadow above the lip
    scuff = smoothstep(1.0, 2.2, fnoise(rng, beta=1.8, aniso=(5, 1))) * smoothstep(top - 14, top, Y) * (Y < top)
    return over(img, (120, 114, 104), scuff * 0.35)


def tex_l4_walls() -> None:
    wall = _l4_drywall(4103)
    save_png(wall, _blk("office_wall"))
    save_png(_l4_base_strip(wall.copy()), _blk("office_wall_base"))
    # Lobby wall: darker two-tone paint under a wooden chair rail at ~0.9 m (rows 9-17 from the top).
    rng = rng_for(4104)
    X, Y = grid()
    rail_top, rail_bot = 9, 17
    low = _l4_drywall(4105, (176, 164, 148))
    img = over(wall.copy(), low, (Y > rail_bot))
    rail = _wood(rng, np.array([112, 78, 52]), horizontal=True)
    img = over(img, rail, box((S, S), -1, rail_top, S + 1, rail_bot))
    img = add_lum(img, 24 * box((S, S), -1, rail_top, S + 1, rail_top + 1.5) - 28 * box((S, S), -1, rail_bot - 1.5, S + 1, rail_bot))
    img = add_lum(img, -16 * box((S, S), -1, rail_bot, S + 1, rail_bot + 3))   # shadow under the rail
    img = _l4_base_strip(img)
    save_png(img, _blk("office_wall_rail"))


def tex_l4_column() -> None:
    """Square column: the same paint, darker at the edges (corner bead catches less light) and dinged."""
    rng = rng_for(4106)
    X, Y = grid()
    img = _l4_drywall(4106, (220, 212, 199))
    img = add_lum(img, -10 * smoothstep(10, 0, X) - 10 * smoothstep(S - 10, S, X))
    img = add_lum(img, 14 * box((S, S), 1, -1, 3, S + 1) * 0.5)      # bead highlight
    dings = (rng.random((S, S)) < 0.0015) * rng.uniform(0.5, 1, (S, S))
    img = add_lum(img, -blur(dings, 0.8) * 120)
    save_png(img, _blk("office_column"))
    save_png(_l4_base_strip(img.copy(), height=14), _blk("office_column_base"))


def _l4_ceiling_tile(seed) -> np.ndarray:
    """One 2x2 ft acoustic tile per block: fissured, pitted mineral fibre in a thin white T-bar grid."""
    rng = rng_for(seed)
    X, Y = grid()
    img = fill(L4_TILE, (S, S))
    pits = blur((rng.random((S, S)) < 0.08) * rng.uniform(0.3, 1, (S, S)), 0.45)
    fiss = smoothstep(0.06, 0.0, np.abs(fnoise(rng, beta=2.2, kmin=4)))
    lum = -pits * 34 - fiss * 10 + fnoise(rng, beta=3.0) * 3.5 + rng.normal(0, 1.8, (S, S))
    img = add_lum(img, lum, (1.0, 0.99, 0.96))
    # Sagging tiles darken toward the middle; a faint water stain now and then.
    img = add_lum(img, -7 * blob((S, S), 64, 64, 40, 40))
    stain = smoothstep(0.35, 0.6, blob((S, S), rng.uniform(20, 108), rng.uniform(20, 108), 22, 16)
                       * (1 + 0.5 * fnoise(rng, beta=2.0))) * (rng.random() < 0.6)
    img = over(img, np.array([170, 150, 112]), stain * 0.28)
    # T-bar: 2 px on every edge (two neighbours make a 4 px bar), with a shadow line inside.
    d = np.minimum.reduce([X, Y, S - X, S - Y])
    img = add_lum(img, -16 * ((d > 2) & (d < 4.5)))
    img = over(img, np.array(L4_TBAR), np.clip(2.6 - d, 0, 1))
    return img


def tex_l4_ceiling() -> None:
    save_png(_l4_ceiling_tile(4107), _blk("office_ceiling"))
    # Missing tile: the dark plenum above the grid, a duct and a sagging cable barely visible.
    rng = rng_for(4108)
    X, Y = grid()
    hole = add_lum(fill((10, 10, 12), (S, S)), fnoise(rng, beta=2.5) * 3)
    duct = box((S, S), 30, -1, 74, S + 1)
    hole = over(hole, np.array([30, 31, 34]), duct)
    hole = add_lum(hole, 8 * box((S, S), 30, -1, 34, S + 1) - 6 * box((S, S), 70, -1, 74, S + 1))
    for yy in range(8, S, 24):
        hole = add_lum(hole, 6 * box((S, S), 30, yy, 74, yy + 2))          # duct seams
    cable = np.abs(Y - (40 + 30 * np.sin((X + 10) / 40))) < 1.6
    hole = over(hole, np.array([22, 22, 24]), cable)
    d = np.minimum.reduce([X, Y, S - X, S - Y])
    hole = over(hole, np.array([150, 150, 148]), (d < 2.6) * 1.0)    # the T-bar grid still frames the hole
    hole = add_lum(hole, -20 * ((d >= 2.6) & (d < 5)))
    save_png(hole, _blk("office_ceiling_missing"))
    # Access hatch: a tile in a hinged metal frame with a cam latch.
    img = _l4_ceiling_tile(4109)
    frame = box((S, S), 10, 10, S - 10, S - 10) - box((S, S), 14, 14, S - 14, S - 14)
    img = over(img, np.array([214, 214, 210]), frame)
    img = add_lum(img, -22 * (box((S, S), 14, 14, S - 14, 16)))
    latch = supersample((S, S), lambda x, y: np.hypot(x - 64, y - 24) < 4)
    img = over(img, np.array([120, 120, 124]), latch)
    img = over(img, np.array([60, 60, 64]), box((S, S), 62.5, 21, 65.5, 27) * latch)
    save_png(img, _blk("office_ceiling_hatch"))


def _l4_panel(on: bool) -> np.ndarray:
    """Half a 2x4 ft troffer seen from below. Its partner is at the texture's bottom edge (north,
    for an unrotated down face): that edge has no frame, so the two halves read as one long lens."""
    rng = rng_for(4111)
    X, Y = grid()
    img = add_lum(fill((226, 226, 228), (S, S)), rng.normal(0, 1.5, (S, S)))   # painted steel frame
    lens = box((S, S), 12, 12, S - 12, S + 4)
    groove = box((S, S), 9, 9, S - 9, S + 4) - lens
    img = over(img, np.array([150, 150, 154]), groove)
    prism = (np.abs(((X + Y) % 6) - 3) + np.abs(((X - Y) % 6) - 3)) / 6.0
    if on:
        # Prismatic lens: a fine diamond grid, two brighter tube bands showing through.
        lit = add_lum(fill((246, 249, 255), (S, S)), -prism * 10)
        for tx in (44, 84):
            lit = add_lum(lit, 9 * np.exp(-((X - tx) / 9) ** 2))
        lit = np.minimum(lit, 255)
    else:
        lit = add_lum(fill((150, 153, 158), (S, S)), -prism * 8 + fnoise(rng, beta=2.5) * 3)
        for tx in (44, 84):           # dead tubes as darker shadows behind the lens, blackened ends
            tube = np.exp(-((X - tx) / 6) ** 2)
            lit = add_lum(lit, -tube * 14 - tube * smoothstep(30, 14, Y) * 30)
        dead_bugs = smoothstep(0.75, 0.9, blob((S, S), 60, 96, 26, 10)) * 0.35
        lit = over(lit, np.array([70, 66, 60]), dead_bugs)
    img = over(img, lit, lens)
    img = add_lum(img, -10 * (box((S, S), 12, 12, S - 12, 14)))   # shadow of the frame on the lens
    return img


def tex_l4_panel() -> None:
    save_png(_l4_panel(True), _blk("office_panel"))
    save_png(_l4_panel(False), _blk("office_panel_off"))


L4_WINDOW_FRAMES = 32


def _l4_rain_sheet(frame: int, frames: int, rng_seed=4120) -> np.ndarray:
    """One frame of the two-block-tall window glass (128 x 256): wet night glass with a faint fog
    glow, beaded droplets and a few runnels sliding down. Every moving thing travels a whole
    number of sheet heights per loop, so the animation loops seamlessly."""
    rng = rng_for(rng_seed)
    H, W = 2 * S, S
    X, Y = grid((H, W))
    # Lit by the window's own "glass" lightmap cell (see Level4Atmosphere), so it can be fairly light.
    bg = lerp(np.array([92, 102, 116]), np.array([116, 126, 140]), smoothstep(0, H, Y)[..., None])
    bg = add_lum(bg, blur(fnoise(rng, (H, W), beta=3.2), 3) * 7)
    for _ in range(3):   # blurry distant lights in the fog
        bx, by = rng.uniform(0, W), rng.uniform(H * 0.4, H)
        bg = add_lum(bg, 18 * blob((H, W), bx, by, rng.uniform(8, 18), rng.uniform(6, 12)), (0.9, 0.95, 1.0))
    img = bg
    t = frame / frames
    # Beaded drops: a light body (they refract the sky) with a dark rim.
    beads = np.zeros((H, W))
    rims = np.zeros((H, W))
    n_beads = 260
    bx = rng.uniform(0, W, n_beads)
    by = rng.uniform(0, H, n_beads)
    br = rng.uniform(0.8, 2.6, n_beads)
    for i in range(n_beads):
        dx = wrapd(X - bx[i], W)
        dy = wrapd(Y - by[i], H)
        r = np.hypot(dx, dy * 0.85)
        beads = np.maximum(beads, np.clip(br[i] - r + 0.5, 0, 1))
        rims = np.maximum(rims, np.clip(1.2 - np.abs(r - br[i]), 0, 1) * (dy > -0.3 * br[i]))
    img = over(img, np.array([150, 160, 172]), beads * 0.55)
    img = add_lum(img, -26 * rims)
    # Runnels: drops racing down, leaving a thin wet trail that fades behind them.
    n_run = 9
    rx = rng.uniform(4, W - 4, n_run)
    phase = rng.uniform(0, 1, n_run)
    speed = rng.integers(1, 3, n_run)        # sheet heights per loop
    wig = rng.uniform(0.5, 2.0, n_run)
    trail = np.zeros((H, W))
    head = np.zeros((H, W))
    for i in range(n_run):
        hy = ((phase[i] + speed[i] * t) % 1.0) * H
        xpath = rx[i] + wig[i] * np.sin(Y / 9.0 + i)
        dxp = np.abs(X - xpath)
        behind = wrapd(hy - Y, H)            # > 0: above the head (already passed)
        tr = np.clip(1.0 - dxp / 1.3, 0, 1) * np.exp(-np.clip(behind, 0, None) / 70.0) * (behind > 0)
        trail = np.maximum(trail, tr)
        hd = np.clip(2.6 - np.hypot(dxp, wrapd(Y - hy, H) * 0.7), 0, 1)
        head = np.maximum(head, hd)
    img = over(img, np.array([118, 128, 140]), trail * 0.6)
    img = add_lum(img, -18 * np.clip(blur(trail, 0.8) - trail, 0, 1) * 3)
    img = over(img, np.array([190, 198, 208]), head * 0.85)
    return img


def _l4_window_frame(img, lower: bool) -> np.ndarray:
    """Dark bronze anodised aluminium: mullions on both sides, sill (lower) or head rail (upper)."""
    rng = rng_for(4121)
    X, Y = grid()
    alu = add_lum(fill((58, 56, 54), (S, S)), fnoise(rng, beta=1.0, aniso=(1, 8)) * 3 + rng.normal(0, 1.5, (S, S)))
    side = box((S, S), -1, -1, 5, S + 1) + box((S, S), S - 5, -1, S + 1, S + 1)
    rail = box((S, S), -1, S - 12, S + 1, S + 1) if lower else box((S, S), -1, -1, S + 1, 10)
    frame = np.clip(side + rail, 0, 1)
    img = over(img, alu, frame)
    img = add_lum(img, 20 * (box((S, S), 4, -1, 5, S + 1) + box((S, S), S - 5, -1, S - 4, S + 1)) * (1 - rail))
    if lower:
        img = add_lum(img, 26 * box((S, S), 5, S - 12, S - 5, S - 11))   # sill lip catches light
        img = add_lum(img, -24 * box((S, S), 5, S - 15, S - 5, S - 12))  # wet gasket shadow
    else:
        img = add_lum(img, -26 * box((S, S), 5, 10, S - 5, 13))
    return img


def tex_l4_window() -> None:
    """Animated rainy window panes (upper and lower half of one tall window), 32 frames."""
    lower_frames = []
    upper_frames = []
    for f in range(L4_WINDOW_FRAMES):
        sheet = _l4_rain_sheet(f, L4_WINDOW_FRAMES)
        upper_frames.append(_l4_window_frame(sheet[:S], lower=False))
        lower_frames.append(_l4_window_frame(sheet[S:], lower=True))
    for name, frames in (("office_window_upper", upper_frames), ("office_window_lower", lower_frames)):
        save_png(np.concatenate(frames, axis=0), _blk(name))
        _blk(name).with_suffix(".png.mcmeta").write_text(
            '{\n  "animation": {\n    "frametime": 2,\n    "interpolate": false\n  }\n}\n')


def tex_l4_glass() -> None:
    """Conference-room glass on black mullions (translucent), and the same with venetian blinds."""
    rng = rng_for(4122)
    X, Y = grid()
    glass = np.concatenate([fill((150, 170, 176), (S, S)), np.full((S, S, 1), 46.0)], axis=2)
    glass[..., :3] = add_lum(glass[..., :3], fnoise(rng, beta=3.0) * 5)
    streak = smoothstep(0.85, 1.0, np.sin((X + Y * 0.6) / 14.0)) * 30   # faint reflection bands
    glass[..., 3] += streak * 0.6
    mull = np.clip(box((S, S), -1, -1, 4, S + 1) + box((S, S), S - 4, -1, S + 1, S + 1), 0, 1)
    glass[..., :3] = over(glass[..., :3], np.array([24, 24, 26]), mull)
    glass[..., 3] = np.maximum(glass[..., 3], mull * 255)
    save_png(glass, _blk("office_glass"))
    blinds = glass.copy()
    slat = ((Y % 8) < 5.5) & (mull < 0.5)
    shade = 0.55 + 0.45 * ((Y % 8) / 5.5)
    col = np.stack([196 * shade, 192 * shade, 184 * shade], axis=-1)
    blinds[..., :3] = np.where(slat[..., None], col, blinds[..., :3])
    blinds[..., 3] = np.where(slat, 250, blinds[..., 3])
    cord = (np.abs(X - 40) < 0.8) | (np.abs(X - 88) < 0.8)
    blinds[..., :3] = np.where((cord & ~slat)[..., None], np.array([210, 206, 196]), blinds[..., :3])
    blinds[..., 3] = np.where(cord & ~slat, 255, blinds[..., 3])
    save_png(blinds, _blk("office_glass_blinds"))


def tex_l4_curtain() -> None:
    """Heavy red velvet stage curtain hanging in deep vertical folds."""
    rng = rng_for(4123)
    X, Y = grid()
    folds = 0.5 + 0.5 * np.sin(2 * np.pi * X / 32 + 0.6 * np.sin(2 * np.pi * X / 64))
    img = fill((122, 18, 24), (S, S))
    img = img * (0.45 + 0.75 * folds[..., None])
    img = add_lum(img, rng.normal(0, 3, (S, S)) + fnoise(rng, beta=1.5, aniso=(1, 6)) * 4, (1.0, 0.35, 0.35))
    img = add_lum(img, 30 * smoothstep(0.92, 1.0, folds), (1.0, 0.5, 0.5))   # velvet sheen on the crests
    save_png(img, _blk("stage_curtain"))


def _l4_door_sheet(seed, base, plaque=False):
    """Wood-veneer office door (2 blocks tall) in a light painted frame, vision panel, lever, kick plate."""
    rng = rng_for(seed)
    shp = (2 * S, S)
    X, Y = grid(shp)
    img = _wood(rng, base, horizontal=False, shape=shp)
    img = add_lum(img, fnoise(rng, shp, beta=1.2, aniso=(1, 14)) * 5, (1.0, 0.8, 0.6))   # veneer figure
    frame_col = np.array([206, 200, 188])
    jamb = 1 - box(shp, 9, 9, S - 9, 2 * S + 20)
    img = over(img, add_lum(fill(frame_col, shp), rng.normal(0, 1.5, shp)), jamb)
    img = add_lum(img, -26 * (box(shp, 7, 7, S - 7, 2 * S + 20) - box(shp, 9, 9, S - 9, 2 * S + 20)))
    vis = box(shp, 84, 40, 100, 120)              # narrow vision panel (dark glass) on the latch side
    img = over(img, np.array([34, 38, 42]), vis)
    img = add_lum(img, 22 * box(shp, 84, 40, 100, 44) + 10 * vis * smoothstep(120, 40, Y))
    img = over(img, np.array([150, 146, 140]), box(shp, 82, 38, 102, 40) + box(shp, 82, 120, 102, 122))
    rose = supersample(shp, lambda x, y: np.hypot(x - 96, y - 142) < 5)   # lever handle + rose
    img = over(img, np.array([168, 166, 160]), rose)
    img = over(img, np.array([186, 184, 178]), box(shp, 78, 139, 97, 144))
    img = add_lum(img, 30 * box(shp, 78, 139, 97, 140) - 30 * box(shp, 78, 143, 97, 144))
    img = over(img, np.array([150, 148, 142]), box(shp, 12, 2 * S - 28, S - 12, 2 * S - 9))   # kick plate
    img = add_lum(img, fnoise(rng, shp, beta=0.8, aniso=(8, 1)) * 5 * box(shp, 12, 2 * S - 28, S - 12, 2 * S - 9))
    if plaque:
        # A brass "6".
        img = over(img, np.array([176, 140, 64]), box(shp, 50, 64, 78, 98) * 0.9)
        img = add_lum(img, 24 * box(shp, 50, 64, 78, 66) - 20 * box(shp, 50, 96, 78, 98))
        six = pil_mask(lambda d, k: d.text((64 * k, 82 * k), "6", fill=255, font=font(30 * k), anchor="mm"), shp)
        img = over(img, np.array([92, 66, 22]), six)
    return img


def tex_l4_doors() -> None:
    wood = np.array([138, 92, 58])
    sheet = _l4_door_sheet(4130, wood)
    save_png(sheet[:S], _blk("office_door_top"))
    save_png(sheet[S:], _blk("office_door_bottom"))
    six = _l4_door_sheet(4130, wood, plaque=True)
    save_png(six[:S], _blk("office_door_six_top"))
    # Glass door: thin aluminium frame, big glass panel (translucent), push bar.
    rng = rng_for(4131)
    shp = (2 * S, S)
    X, Y = grid(shp)
    rgb = add_lum(fill((172, 174, 176), shp), fnoise(rng, shp, beta=1.0, aniso=(1, 10)) * 4)
    alpha = np.full(shp, 255.0)
    pane = box(shp, 12, 12, S - 12, 2 * S - 22)
    rgb = over(rgb, np.array([150, 172, 178]), pane)
    alpha = alpha * (1 - pane) + pane * (52 + 30 * smoothstep(0.85, 1.0, np.sin((X + Y * 0.5) / 13.0)))
    bar = box(shp, 18, 132, S - 18, 138)
    rgb = over(rgb, np.array([200, 200, 198]), bar)
    alpha = np.maximum(alpha, bar * 255)
    sheet = np.concatenate([rgb, alpha[..., None]], axis=2)
    save_png(sheet[:S], _blk("office_glass_door_top"))
    save_png(sheet[S:], _blk("office_glass_door_bottom"))
    # Fire door: grey steel, red push bar, small wired window.
    rng = rng_for(4132)
    img = _smooth_concrete(rng, (118, 122, 126), mottle=3.0, fine=0.8, shape=shp)
    img = over(img, np.array([196, 190, 178]), 1 - box(shp, 8, 8, S - 8, 2 * S + 20))
    img = add_lum(img, -22 * (box(shp, 6, 6, S - 6, 2 * S + 20) - box(shp, 8, 8, S - 8, 2 * S + 20)))
    win = box(shp, 46, 32, 82, 82)
    img = over(img, np.array([38, 44, 48]), win)
    wire = ((np.abs((X - Y) % 9) < 0.7) | (np.abs((X + Y) % 9) < 0.7)) * win
    img = over(img, np.array([92, 96, 98]), wire * 0.8)
    img = add_lum(img, 14 * win * smoothstep(82, 32, Y))
    img = over(img, np.array([168, 34, 30]), box(shp, 16, 140, S - 16, 152))
    img = add_lum(img, 34 * box(shp, 16, 140, S - 16, 142) - 34 * box(shp, 16, 150, S - 16, 152))
    img = over(img, np.array([60, 60, 62]), box(shp, 12, 138, 18, 154) + box(shp, S - 18, 138, S - 12, 154))
    img = over(img, np.array([150, 150, 152]), box(shp, 12, 2 * S - 30, S - 12, 2 * S - 9))
    save_png(img[:S], _blk("fire_door_top"))
    save_png(img[S:], _blk("fire_door_bottom"))


def tex_l4_elevator() -> None:
    """Closed brushed-steel elevator doors, 2 wide x 3 tall (lower/upper leaves + header)."""
    rng = rng_for(4140)
    W, H = 2 * S, 3 * S
    shp = (H, W)
    X, Y = grid(shp)
    wall = np.tile(_l4_drywall(4141), (3, 2, 1))
    steel = add_lum(fill((150, 152, 156), shp), fnoise(rng, shp, beta=0.6, aniso=(1, 30)) * 9 + rng.normal(0, 2, shp))
    steel = add_lum(steel, 14 * smoothstep(H, 0, Y))                  # a soft sheen higher up
    frame_out = box(shp, 14, 18, W - 14, H + 20)
    img = over(wall, add_lum(fill((122, 124, 128), shp), fnoise(rng, shp, beta=0.6, aniso=(1, 20)) * 6), frame_out)
    leaves = box(shp, 26, S + 10, W - 26, H + 20)
    img = over(img, steel, leaves)
    img = add_lum(img, -40 * box(shp, W / 2 - 1, S + 10, W / 2 + 1, H + 20))     # the seam
    img = add_lum(img, -26 * (box(shp, 26, S + 10, W - 26, S + 13)))
    img = add_lum(img, -30 * box(shp, 24, S + 8, 26, H) - 30 * box(shp, W - 26, S + 8, W - 24, H))
    # Header: a dead floor indicator (dark glass with a faint arrow and a "4").
    ind = box(shp, W / 2 - 34, 46, W / 2 + 34, 78)
    img = over(img, np.array([18, 16, 16]), ind)
    arrow = pil_mask(lambda d, k: d.polygon([((W / 2 - 22) * k, 70 * k), ((W / 2 - 12) * k, 54 * k), ((W / 2 - 2) * k, 70 * k)], fill=255), shp)
    img = over(img, np.array([70, 30, 24]), arrow * 0.8)
    four = pil_mask(lambda d, k: d.text(((W / 2 + 16) * k, 62 * k), "4", fill=255, font=font(24 * k), anchor="mm"), shp)
    img = over(img, np.array([72, 30, 22]), four * 0.8)
    smudge = smoothstep(1.2, 2.4, fnoise(rng, shp, beta=2.2)) * leaves     # finger smudges
    img = over(img, np.array([118, 116, 112]), smudge * 0.3)
    img = add_lum(img, -20 * smoothstep(H - 30, H, Y) * leaves)
    for part, (y0, y1) in (("header", (0, S)), ("upper", (S, 2 * S)), ("lower", (2 * S, 3 * S))):
        save_png(img[y0:y1, :S], _blk(f"elevator_door_{part}_left"))
        save_png(img[y0:y1, S:], _blk(f"elevator_door_{part}_right"))


def tex_l4_partition() -> None:
    rng = rng_for(4150)
    X, Y = grid()
    weave = 0.5 + 0.25 * np.cos(np.pi * X) * np.cos(np.pi * Y)
    img = add_lum(fill(L4_FABRIC, (S, S)), (weave - 0.5) * 8 + rng.normal(0, 4, (S, S)) + fnoise(rng, beta=2.6) * 4)
    img = over(img, np.array(L4_FABRIC) * 0.75, smoothstep(1.2, 2.6, fnoise(rng, beta=2.0)) * 0.3)   # stains
    save_png(img, _blk("cubicle_fabric"))
    cap = add_lum(fill((176, 178, 180), (S, S)), fnoise(rng, beta=0.6, aniso=(1, 20)) * 6 + rng.normal(0, 2, (S, S)))
    save_png(cap, _blk("cubicle_cap"))


def tex_l4_radiator() -> None:
    rng = rng_for(4151)
    X, Y = grid()
    img = add_lum(fill((222, 216, 204), (S, S)), fnoise(rng, beta=2.4) * 4 + rng.normal(0, 2, (S, S)))
    img = add_lum(img, -18 * smoothstep(0.4, 1.0, np.cos(2 * np.pi * X / 16) ** 8))   # fin gaps
    rust = smoothstep(1.6, 2.6, fnoise(rng, beta=2.2)) * smoothstep(S * 0.5, S, Y)
    img = over(img, np.array([132, 92, 60]), rust * 0.5)
    save_png(img, _blk("radiator"))


def tex_l4_desk() -> None:
    rng = rng_for(4152)
    X, Y = grid()
    top = _wood(rng, np.array([150, 112, 80]), horizontal=True)
    top = add_lum(top, -10 * smoothstep(1.2, 2.4, fnoise(rng, beta=2.0)))       # wear
    ring = np.abs(np.hypot(X - 90, Y - 40) - 10) < 1.2                          # a coffee ring
    top = over(top, np.array([110, 78, 52]), ring * 0.5)
    save_png(top, _blk("desk_top"))
    side = _wood(rng, np.array([132, 98, 70]), horizontal=False)
    side = add_lum(side, -12 * smoothstep(S - 20, S, Y))
    save_png(side, _blk("desk_side"))
    paper = add_lum(fill((232, 230, 222), (S, S)), rng.normal(0, 2, (S, S)))
    for yy in range(20, 110, 10):
        paper = over(paper, np.array([120, 120, 126]), box((S, S), 18, yy, 18 + rng.uniform(50, 92), yy + 2) * 0.6)
    save_png(paper, _blk("desk_paper"))


def tex_l4_computer() -> None:
    rng = rng_for(4153)
    X, Y = grid()
    plastic = add_lum(fill((196, 188, 166), (S, S)), rng.normal(0, 2, (S, S)) + fnoise(rng, beta=2.4) * 4)
    plastic = over(plastic, np.array([170, 160, 132]), smoothstep(1.0, 2.4, fnoise(rng, beta=2.0)) * 0.4)   # yellowed
    save_png(plastic, _blk("crt_plastic"))
    screen = plastic.copy()                 # bezel, dark curved glass with a dull reflection, power LED
    glass = box((S, S), 14, 14, S - 14, S - 26, soft=2.0)
    scr = add_lum(fill((26, 30, 30), (S, S)), 22 * blob((S, S), 44, 40, 34, 24) - 8 * (1 - blob((S, S), 64, 52, 60, 46)))
    screen = over(screen, scr, glass)
    screen = add_lum(screen, -20 * (box((S, S), 10, 10, S - 10, S - 22) - box((S, S), 14, 14, S - 14, S - 26)))
    screen = over(screen, np.array([40, 60, 40]), supersample((S, S), lambda x, y: np.hypot(x - 108, y - 114) < 3))
    save_png(screen, _blk("crt_screen"))
    kb = add_lum(fill((180, 172, 152), (S, S)), rng.normal(0, 2, (S, S)))
    inside = (X > 4) * (X < S - 4) * (Y > 8) * (Y < S - 8)
    keys = (((X - 6) % 10) < 8) & (((Y - 10) % 18) < 14) & (inside > 0)
    kb = over(kb, np.array([212, 206, 190]), keys)
    kb = add_lum(kb, -18 * ((((X - 6) % 10) >= 8) | (((Y - 10) % 18) >= 14)) * inside)
    save_png(kb, _blk("keyboard"))


def tex_l4_chair() -> None:
    rng = rng_for(4154)
    X, Y = grid()
    weave = 0.5 + 0.25 * np.cos(np.pi * X / 1.5) * np.cos(np.pi * Y / 1.5)
    fab = add_lum(fill((44, 62, 112), (S, S)), (weave - 0.5) * 10 + rng.normal(0, 4, (S, S)) + fnoise(rng, beta=2.4) * 5)
    fab = over(fab, np.array([60, 70, 96]), smoothstep(1.0, 2.2, fnoise(rng, beta=2.0)) * 0.35)   # worn, faded
    save_png(fab, _blk("chair_fabric"))
    base = add_lum(fill((30, 30, 32), (S, S)), rng.normal(0, 2, (S, S)) + fnoise(rng, beta=2.0) * 3)
    save_png(base, _blk("chair_base"))


def _l4_metal(seed, col, scratches=10) -> np.ndarray:
    """Painted sheet steel with a few bright scratches."""
    rng = rng_for(seed)
    X, Y = grid()
    img = _smooth_concrete(rng, col, mottle=2.0, fine=0.6)
    for _ in range(scratches):
        x0, y0 = rng.uniform(0, S, 2)
        ang = rng.uniform(-0.4, 0.4)
        L = rng.uniform(6, 24)
        d = np.abs((X - x0) * np.sin(ang) - (Y - y0) * np.cos(ang))
        t = (X - x0) * np.cos(ang) + (Y - y0) * np.sin(ang)
        img = over(img, np.minimum(np.array(col) * 1.25 + 10, 255), ((d < 0.6) & (t > 0) & (t < L)) * 0.6)
    return img


def tex_l4_cabinet() -> None:
    X, Y = grid()
    side = _l4_metal(4155, (132, 134, 132))
    save_png(add_lum(side, 10 * smoothstep(4, 0, X) - 10 * smoothstep(S - 4, S, X)), _blk("filing_cabinet_side"))
    front = side.copy()
    for i in range(4):   # four drawers with recessed pulls and label holders
        y0 = 4 + i * 31
        front = add_lum(front, 18 * box((S, S), 6, y0, S - 6, y0 + 2) - 22 * box((S, S), 6, y0 + 27, S - 6, y0 + 29))
        front = over(front, np.array([40, 40, 42]), box((S, S), 44, y0 + 14, 84, y0 + 19))
        front = add_lum(front, -14 * box((S, S), 44, y0 + 19, 84, y0 + 21))
        front = over(front, np.array([200, 198, 186]), box((S, S), 54, y0 + 6, 74, y0 + 12))
        front = over(front, np.array([150, 150, 150]), box((S, S), 52, y0 + 5, 76, y0 + 6) + box((S, S), 52, y0 + 12, 76, y0 + 13))
    save_png(front, _blk("filing_cabinet_front"))
    # The supply filing cabinet: same cabinet, an orange "SUPPLIES" label so it can be spotted.
    supply = over(front.copy(), np.array([214, 112, 40]), box((S, S), 30, 38, 98, 58))
    supply = over(supply, np.array([250, 244, 230]), _text("SUPPLIES", 64, 48, 13, maxw=62))
    save_png(supply, _blk("supply_cabinet_front"))


def tex_l4_copier() -> None:
    rng = rng_for(4156)
    X, Y = grid()
    body = add_lum(fill((206, 204, 196), (S, S)), rng.normal(0, 2, (S, S)) + fnoise(rng, beta=2.4) * 4)
    save_png(body, _blk("copier_side"))
    front = body.copy()
    for y0 in (52, 84):          # paper trays
        front = add_lum(front, -26 * box((S, S), 8, y0, S - 8, y0 + 2) + 10 * box((S, S), 8, y0 + 2, S - 8, y0 + 3))
        front = over(front, np.array([110, 110, 112]), box((S, S), 54, y0 + 12, 74, y0 + 16))
    front = over(front, np.array([70, 72, 78]), box((S, S), 8, 8, S - 8, 30))
    front = over(front, np.array([30, 52, 40]), box((S, S), 16, 12, 46, 26))      # dead LCD
    for i, c in enumerate(((40, 120, 60), (60, 60, 64), (60, 60, 64), (160, 40, 30))):
        front = over(front, np.array(c), supersample((S, S), lambda x, y, i=i: np.hypot(x - (66 + i * 13), y - 19) < 4))
    save_png(front, _blk("copier_front"))
    top = add_lum(fill((60, 62, 66), (S, S)), rng.normal(0, 2, (S, S)))
    top = over(top, np.array([214, 212, 206]), box((S, S), 6, 6, S - 6, S - 6))
    top = over(top, np.array([238, 236, 228]), box((S, S), 30, 40, 98, 104) * 0.8)   # a forgotten sheet
    save_png(top, _blk("copier_top"))


def tex_l4_plant() -> None:
    rng = rng_for(4157)
    X, Y = grid()
    pot = add_lum(fill((62, 60, 58), (S, S)), rng.normal(0, 2.5, (S, S)) + fnoise(rng, beta=2.0) * 4)
    pot = add_lum(pot, 14 * box((S, S), -1, 0, S + 1, 10))
    save_png(pot, _blk("plant_pot"))
    soil = add_lum(fill((58, 46, 36), (S, S)), rng.normal(0, 8, (S, S)))
    save_png(soil, _blk("plant_soil"))
    # Dead leaves: drooping brown blades on a transparent background (crossed planes in the model).
    m = np.zeros((S, S))
    col = np.zeros((S, S, 3))
    for i in range(16):
        x0 = 64 + rng.normal(0, 8)
        ang = rng.uniform(-1.2, 1.2)
        L = rng.uniform(40, 100)
        droop = rng.uniform(0.002, 0.012)
        t = np.linspace(0, 1, 120)
        px = x0 + np.sin(ang) * L * t
        py = S - 2 - np.cos(ang) * L * t + droop * (L * t) ** 2
        w = 3.2 * (1 - t) + 0.6
        c = np.array([112, 86, 52]) * rng.uniform(0.7, 1.15)
        for k in range(0, 120, 2):
            d = np.hypot(X - px[k], Y - py[k]) < w[k]
            m = np.maximum(m, d)
            col = np.where(d[..., None], c * (0.85 + 0.15 * t[k]), col)
    col = add_lum(col, rng.normal(0, 6, (S, S)))
    save_png(np.concatenate([col, (m * 255)[..., None]], axis=2), _blk("dead_plant"))


def tex_l4_boxes() -> None:
    rng = rng_for(4158)
    card = add_lum(fill((168, 128, 84), (S, S)), rng.normal(0, 3, (S, S)) + fnoise(rng, beta=1.2, aniso=(10, 1)) * 4)
    card = add_lum(card, -14 * smoothstep(1.2, 2.4, fnoise(rng, beta=2.2)))
    side = over(card.copy(), np.array([60, 52, 44]), _text("FILES", 64, 70, 22, maxw=70) * 0.55)
    side = add_lum(side, -16 * box((S, S), -1, 60, S + 1, 62))
    save_png(side, _blk("cardboard_box"))
    top = add_lum(card.copy(), -24 * box((S, S), -1, 63, S + 1, 65))          # flap seam
    top = over(top, np.array([186, 170, 130]), box((S, S), -1, 54, S + 1, 74) * 0.75)   # packing tape
    save_png(top, _blk("cardboard_box_top"))


def tex_l4_ladder() -> None:
    rng = rng_for(4159)
    img = add_lum(fill((182, 184, 186), (S, S)), fnoise(rng, beta=0.6, aniso=(1, 24)) * 8 + rng.normal(0, 2, (S, S)))
    img = add_lum(img, -16 * smoothstep(1.2, 2.4, fnoise(rng, beta=2.0)))
    save_png(img, _blk("step_ladder"))


def tex_l4_conference() -> None:
    rng = rng_for(4160)
    X, Y = grid()
    top = _wood(rng, np.array([84, 56, 38]), horizontal=True)
    top = add_lum(top, 10 * smoothstep(0.9, 1.0, np.sin(X / 20.0 + Y / 60.0)))   # polish
    save_png(top, _blk("conference_table"))


def tex_l4_server() -> None:
    rng = rng_for(4161)
    X, Y = grid()
    side = add_lum(fill((34, 34, 36), (S, S)), rng.normal(0, 2, (S, S)) + fnoise(rng, beta=2.0) * 3)
    save_png(side, _blk("server_rack_side"))
    front = side.copy()
    for y0 in range(6, S - 6, 14):   # rack units with vents
        front = add_lum(front, 16 * box((S, S), 8, y0, S - 8, y0 + 1) - 12 * box((S, S), 8, y0 + 12, S - 8, y0 + 13))
        vents = (((X - 30) % 4) < 2) & (X > 30) & (X < 110) & (Y > y0 + 4) & (Y < y0 + 9)
        front = add_lum(front, -12 * vents)
        front = over(front, np.array([60, 60, 62]), supersample((S, S), lambda x, y, y0=y0: np.hypot(x - 18, y - (y0 + 6.5)) < 2.2))
    save_png(front, _blk("server_rack_front"))
    leds = np.zeros((S, S, 4))          # status lights (an emissive overlay strip in the model)
    for i, c in enumerate(((60, 255, 90), (60, 255, 90), (255, 170, 40), (60, 255, 90))):
        m = supersample((S, S), lambda x, y, i=i: np.hypot(x - (20 + i * 26), y - 64) < 9)
        leds[..., :3] += m[..., None] * np.array(c)
        leds[..., 3] = np.maximum(leds[..., 3], m * 255)
    save_png(leds, _blk("server_leds"))


def tex_l4_microwave() -> None:
    rng = rng_for(4162)
    X, Y = grid()
    side = add_lum(fill((214, 212, 206), (S, S)), rng.normal(0, 2, (S, S)))
    save_png(side, _blk("microwave_side"))
    front = side.copy()
    door = box((S, S), 6, 14, 92, 114)
    front = over(front, np.array([30, 30, 32]), door)
    mesh = (((X % 4) < 1.2) | ((Y % 4) < 1.2)) * box((S, S), 14, 22, 84, 106)
    front = over(front, np.array([60, 60, 60]), mesh * 0.6)
    front = over(front, np.array([60, 62, 66]), box((S, S), 98, 14, 122, 114))
    front = over(front, np.array([20, 40, 30]), box((S, S), 101, 20, 119, 32))
    for r in range(4):
        for c2 in range(2):
            front = over(front, np.array([150, 150, 154]), box((S, S), 101 + c2 * 10, 40 + r * 14, 108 + c2 * 10, 48 + r * 14))
    save_png(front, _blk("microwave_front"))


def tex_l4_whiteboard() -> None:
    rng = rng_for(4163)
    img = add_lum(fill((236, 238, 236), (S, S)), fnoise(rng, beta=2.6) * 2.5)
    ghost = marker(np.zeros((S, S, 3)), rng, [[(16, 30), (60, 34), (98, 28)], [(20, 50), (40, 70), (80, 64), (100, 80)],
                                               [(24, 92), (56, 92)], [(70, 96), (90, 100), (110, 92)]], width=3)
    img = over(img, np.array([150, 160, 180]), (ghost.sum(axis=2) > 0) * 0.18)   # ghosts of erased writing
    squiggle = marker(np.zeros((S, S, 3)), rng, [[(30, 60), (44, 52), (58, 62), (72, 50)]], width=3)
    img = over(img, np.array([40, 80, 160]), squiggle.sum(axis=2) > 0)
    img = over(img, np.array([170, 172, 176]), 1 - box((S, S), 6, 8, S - 6, S - 8))
    img = over(img, np.array([150, 152, 156]), box((S, S), 4, S - 12, S - 4, S - 6))   # marker tray
    save_png(img, _blk("whiteboard"))


def tex_l4_wall_bits() -> None:
    """Elevator call buttons, the security camera and its lens, the lobby directory board."""
    rng = rng_for(4164)
    X, Y = grid()
    plate = add_lum(fill((170, 170, 168), (S, S)), fnoise(rng, beta=0.6, aniso=(1, 20)) * 6)
    for cy in (40, 88):
        plate = over(plate, np.array([110, 110, 112]), supersample((S, S), lambda x, y, cy=cy: np.hypot(x - 64, y - cy) < 16))
        plate = over(plate, np.array([190, 190, 188]), supersample((S, S), lambda x, y, cy=cy: np.hypot(x - 64, y - cy) < 12))
    arrows = pil_mask(lambda d, k: (d.polygon([(64 * k, 32 * k), (56 * k, 46 * k), (72 * k, 46 * k)], fill=255),
                                    d.polygon([(64 * k, 96 * k), (56 * k, 82 * k), (72 * k, 82 * k)], fill=255)))
    plate = over(plate, np.array([60, 60, 62]), arrows)
    save_png(plate, _blk("elevator_panel"))
    cam = add_lum(fill((228, 228, 224), (S, S)), rng.normal(0, 2, (S, S)))
    cam = add_lum(cam, -18 * smoothstep(S - 40, S, Y))
    save_png(cam, _blk("cctv_camera"))
    lens = add_lum(fill((20, 20, 24), (S, S)), 40 * blob((S, S), 48, 48, 12, 12))
    lens = over(lens, np.array([150, 20, 20]), supersample((S, S), lambda x, y: np.hypot(x - 100, y - 100) < 6) * 0.6)
    save_png(lens, _blk("cctv_lens"))
    board = add_lum(fill((26, 26, 28), (S, S)), rng.normal(0, 2, (S, S)))
    board = over(board, np.array([186, 168, 120]), 1 - box((S, S), 5, 5, S - 5, S - 5))
    board = over(board, np.array([214, 212, 204]), _text("DIRECTORY", 64, 18, 12, maxw=90))
    for i, line in enumerate(("ACCOUNTS  .....  4", "LEGAL  ..........  4", "ARCHIVE  .......  4",
                              "PERSONNEL  ....  4", "STAIRS  ......  END")):
        board = over(board, np.array([200, 198, 190]), _text(line, 64, 40 + i * 17, 10, maxw=104) * 0.9)
    save_png(board, _blk("office_plaque"))


def tex_l4_vending() -> None:
    """Vending machine: red painted steel body; a glowing display of Almond Water bottles and
    energy bars behind glass (upper half), selection panel and dark delivery flap (lower half).
    The emissive overlay ("glow") textures hold only the glowing parts (alpha 0 elsewhere)."""
    side = _l4_metal(4170, (150, 34, 32), scratches=14)
    save_png(side, _blk("vending_side"))
    W, H = S, 2 * S
    shp = (H, W)
    X, Y = grid(shp)
    body = np.tile(side, (2, 1, 1))
    win = box(shp, 8, 10, 92, 170)
    glow = np.zeros((H, W, 4))
    disp = fill((210, 226, 236), shp)
    for row, y0 in enumerate(range(16, 160, 24)):
        disp = over(disp, np.array([110, 112, 116]), box(shp, 8, y0 + 18, 92, y0 + 21))   # shelf
        for col in range(5):
            x0 = 13 + col * 16
            if row % 2 == 0:   # almond water bottles: pale, with a tan label
                bottle = box(shp, x0, y0 + 2, x0 + 9, y0 + 18) + box(shp, x0 + 3, y0 - 2, x0 + 6, y0 + 2)
                disp = over(disp, np.array([226, 232, 226]), bottle)
                disp = over(disp, np.array([196, 168, 120]), box(shp, x0, y0 + 8, x0 + 9, y0 + 13))
            else:              # energy bars: wrapped, blue and orange
                c = (40, 90, 170) if col % 2 == 0 else (220, 120, 40)
                disp = over(disp, np.array(c), box(shp, x0 - 1, y0 + 6, x0 + 11, y0 + 18))
                disp = over(disp, np.array([240, 240, 230]), box(shp, x0 + 1, y0 + 10, x0 + 9, y0 + 13))
    disp = add_lum(disp, 18 * smoothstep(0.9, 1.0, np.sin((X + Y * 0.4) / 16.0)))      # glass glare
    glow[..., :3] = disp
    glow[..., 3] = win * 255
    panel = box(shp, 98, 40, 120, 150)          # selection panel: lit buttons and a price display
    pcol = fill((40, 40, 44), shp)
    for i in range(6):
        pcol = over(pcol, np.array([236, 230, 200]), box(shp, 101, 70 + i * 12, 117, 78 + i * 12))
    pcol = over(pcol, np.array([90, 240, 120]), box(shp, 101, 46, 117, 58))
    glow[..., :3] = np.where(panel[..., None] > 0.5, pcol, glow[..., :3])
    glow[..., 3] = np.maximum(glow[..., 3], panel * 255)
    front = add_lum(body, -26 * (box(shp, 6, 8, 94, 172) - win))
    front = over(front, np.array([24, 24, 26]), box(shp, 14, 196, 86, 232))          # delivery flap
    front = add_lum(front, 22 * box(shp, 14, 196, 86, 198))
    front = over(front, np.array([200, 200, 196]), _text("PUSH", 50, 226, 9, maxw=40, shape=shp) * 0.8)
    front = over(front, np.array([30, 30, 32]), box(shp, 104, 160, 112, 172))          # coin slot
    front = over(front, np.array([240, 236, 220]), _text("ALMOND WATER", 50, 182, 10, maxw=80, shape=shp))
    save_png(front[:S], _blk("vending_front_upper"))
    save_png(front[S:], _blk("vending_front_lower"))
    save_png(glow[:S], _blk("vending_glow_upper"))
    save_png(glow[S:], _blk("vending_glow_lower"))


def tex_l4_cooler() -> None:
    rng = rng_for(4171)
    X, Y = grid()
    body = add_lum(fill((226, 226, 222), (S, S)), rng.normal(0, 2, (S, S)) + fnoise(rng, beta=2.4) * 3)
    body = add_lum(body, -18 * smoothstep(S - 30, S, Y))
    save_png(body, _blk("cooler_body"))
    front = body.copy()
    front = over(front, np.array([60, 60, 64]), box((S, S), 30, 34, 98, 70))          # tap recess
    front = over(front, np.array([40, 90, 200]), box((S, S), 40, 40, 54, 54))          # cold tap
    front = over(front, np.array([200, 50, 40]), box((S, S), 74, 40, 88, 54))          # hot tap
    front = over(front, np.array([150, 150, 152]), box((S, S), 34, 64, 94, 70))        # drip tray
    save_png(front, _blk("cooler_front"))
    water = np.concatenate([fill((96, 156, 214), (S, S)), np.full((S, S, 1), 200.0)], axis=2)
    water[..., :3] = add_lum(water[..., :3], 20 * smoothstep(0.85, 1.0, np.sin(X / 10.0)) + rng.normal(0, 2, (S, S)))
    save_png(water, _blk("cooler_water"))
    glass = np.concatenate([fill((196, 214, 226), (S, S)), np.full((S, S, 1), 70.0)], axis=2)
    glass[..., 3] += 60 * smoothstep(0.9, 1.0, np.sin(X / 9.0))
    save_png(glass, _blk("cooler_glass"))


L4_TEXTURES = [
    tex_l4_carpet, tex_l4_walls, tex_l4_column, tex_l4_ceiling, tex_l4_panel, tex_l4_window, tex_l4_glass,
    tex_l4_curtain, tex_l4_doors, tex_l4_elevator, tex_l4_partition, tex_l4_radiator, tex_l4_desk,
    tex_l4_computer, tex_l4_chair, tex_l4_cabinet, tex_l4_copier, tex_l4_plant, tex_l4_boxes, tex_l4_ladder,
    tex_l4_conference, tex_l4_server, tex_l4_microwave, tex_l4_whiteboard, tex_l4_wall_bits, tex_l4_vending,
    tex_l4_cooler,
]


def _l4_impulses(rng, n, rate, amp_lo=0.2, amp_hi=1.0, power=2.0) -> np.ndarray:
    """A sparse train of random impulses (Poisson, `rate` per second) for circular convolution."""
    buf = np.zeros(n)
    count = rng.poisson(rate * n / SR)
    idx = rng.integers(0, n, count)
    np.add.at(buf, idx, rng.uniform(amp_lo, amp_hi, count) ** power)
    return buf


def snd_l4_rain() -> None:
    """16 s seamless loop: heavy rain streaming down big office windows, heard from inside.

    A dense hiss of rain on glass that swells with the gusts, thousands of tiny drop ticks, now and
    then a fat drop smacking the pane, water gurgling down the frame, the building's low rumble,
    and a light echo of the empty floor on all of it. Everything is periodic over the loop.
    """
    rng = rng_for(4201)
    T = 16.0
    n = int(T * SR)
    gust = drift(n, rng, 0.35, 0.06, 0.25)
    hiss = periodic_noise(n, rng, lambda f: bp_gain(f, 500, 6500, 2) * lp_gain(f, 3500, 1))
    hiss2 = periodic_noise(n, rng, lambda f: bp_gain(f, 1500, 9000, 2))
    # Fine drops: an impulse train shaped by two short click kernels (bright and dull).
    k = int(0.004 * SR)
    kt = np.arange(k) / SR
    bright = fft_filter(rng.normal(size=k) * np.exp(-kt / 0.0008), lambda f: bp_gain(f, 2500, 9000, 1))[:k]
    dull = fft_filter(rng.normal(size=k) * np.exp(-kt / 0.0015), lambda f: bp_gain(f, 700, 3000, 1))[:k]
    ticks = circ_conv(_l4_impulses(rng, n, 260), bright) + 0.8 * circ_conv(_l4_impulses(rng, n, 160), dull)
    ticks *= gust
    # Fat drops hitting the pane: a short glassy "tink" each, ~5 per second.
    smacks = np.zeros(n)
    for _ in range(int(5 * T)):
        f0 = rng.uniform(1800, 4200)
        d = rng.uniform(0.006, 0.02)
        c = ring([f0, f0 * 2.31, f0 * 0.62], [d, d * 0.5, d * 1.6], [1.0, 0.35, 0.5], d * 5)
        c[:30] += rng.normal(size=30) * 0.8
        add_at(smacks, c * rng.uniform(0.15, 1.0) ** 2, rng.uniform(0, T), circular=True)
    # Water running down the frame and gurgling in a downpipe somewhere.
    trickle = periodic_noise(n, rng, lambda f: bp_gain(f, 250, 1100, 2))
    trickle *= 0.6 + 0.4 * drift(n, rng, 0.9, 2.0, 6.0)
    rumble = periodic_noise(n, rng, lambda f: bp_gain(f, 25, 140, 2))
    x = (norm(hiss) * 0.55 * gust + norm(hiss2) * 0.18 * gust + norm(ticks) * 0.32 + norm(smacks) * 0.22
         + norm(trickle) * 0.09 + norm(rumble) * 0.30)
    # Heard through the glass from inside the room: softened highs, a light echo of the empty floor.
    x = fft_filter(x, lambda f: lp_gain(f, 6000, 2), circular=True)
    ir = reverb_ir(rng, 1.4, 0.32, lp=3200, predelay=0.012, early=((0.021, 0.5), (0.037, 0.35), (0.063, 0.2)))
    x = x + 0.45 * circ_conv(x, ir)
    save_ogg(x, SND / "ambient" / "level4_rain.ogg", peak=0.5, loop=True)


def snd_l4_pipes() -> None:
    """Three distant pipe rumbles in the walls: rushing water swelling up, a knock or two, a groan."""
    for i in range(1, 4):
        rng = rng_for(4210 + i)
        dur = 5.0 + i * 0.7
        n = int(dur * SR)
        t = np.arange(n) / SR
        swell = np.exp(-0.5 * ((t - dur * 0.38) / (dur * 0.2)) ** 2)
        rush = fft_filter(rng.normal(size=n), lambda f: bp_gain(f, 60, 500, 2))[:n] * swell
        rumble = fft_filter(rng.normal(size=n), lambda f: bp_gain(f, 25, 110, 3))[:n] * (0.4 + swell)
        x = norm(rumble) * 0.7 + norm(rush) * 0.35
        for _ in range(rng.integers(1, 4)):       # water hammer knocks in old pipes
            f0 = rng.uniform(140, 260)
            knock = ring([f0, f0 * 2.76, f0 * 5.4], [0.25, 0.09, 0.03], [1.0, 0.45, 0.2], 1.2)
            knock[:60] += rng.normal(size=60) * 0.5
            add_at(x, 0.7 * norm(knock), rng.uniform(0.4, dur * 0.7), circular=False)
        if i == 2:
            # A long groan as a pipe settles.
            f = 70 + 18 * np.sin(2 * np.pi * 0.35 * t) + smooth_jitter(rng, n, 4, 6)
            groan = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-0.5 * ((t - dur * 0.6) / 0.9) ** 2)
            x = x + 0.35 * fft_filter(groan + 0.3 * np.sign(groan) * groan ** 2, lambda ff: lp_gain(ff, 400, 2))[:n]
        x = fft_filter(x, lambda f: lp_gain(f, 900, 2))[:n]          # it's behind the drywall
        x = x + 0.5 * conv(x, reverb_ir(rng, 2.0, 0.6, lp=900))[:n]
        save_ogg(x, SND / "level4" / f"pipes{i}.ogg", peak=0.75, fade_in=0.3, fade_out=1.2)


def snd_l4_thunder() -> None:
    """Three thunder claps heard from inside the office: muffled by the glass, rattling the panes."""
    for i in range(1, 4):
        rng = rng_for(4220 + i)
        dur = 7.0 + i
        n = int(dur * SR)
        t = np.arange(n) / SR
        env = np.zeros(n)
        for _ in range(6 + i):
            c = rng.uniform(0.2, dur * 0.5)
            env += rng.uniform(0.4, 1.0) * np.exp(-0.5 * ((t - c) / rng.uniform(0.15, 0.8)) ** 2)
        env *= np.exp(-t / (dur * 0.42)) * (1 + smooth_jitter(rng, n, 18, 0.5))
        rumble = fft_filter(rng.normal(size=n), lambda f: lp_gain(f, 140, 3) * hp_gain(f, 22, 2))[:n]
        mid = fft_filter(rng.normal(size=n), lambda f: bp_gain(f, 180, 700, 2))[:n]
        x = norm(rumble) * env + 0.22 * norm(mid) * env ** 2
        if i != 2:
            # The first crack, softened by the windows.
            k = int(0.5 * SR)
            crack = rng.normal(size=k) * np.exp(-np.arange(k) / (0.07 * SR))
            crack = fft_filter(crack, lambda f: bp_gain(f, 200, 2200, 1))[:k]
            add_at(x, (0.55 if i == 1 else 0.35) * norm(crack), 0.05, False)
        # Window panes buzzing in their frames with the low end.
        rattle = fft_filter(rng.normal(size=n), lambda f: bp_gain(f, 600, 1600, 3))[:n]
        rattle *= (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 31 * t)))      # chattering at ~31 Hz
        x = x + 0.06 * norm(rattle) * np.clip(env, 0, None) ** 1.5
        x = x + 0.35 * conv(x, reverb_ir(rng, 3.0, 0.9, lp=1300))[:n]
        save_ogg(x, SND / "level4" / f"thunder{i}.ogg", peak=0.89, fade_in=0.02, fade_out=1.0)


def _l4_piano_note(freq, dur, vel, rng) -> np.ndarray:
    """A soft upright-piano note: a few slightly stretched partials, a felt-hammer thump, decay."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for k in range(1, 9):
        fk = freq * k * np.sqrt(1 + 0.0004 * k * k)          # piano strings are a little inharmonic
        if fk > 6000:
            break
        decay = 1.6 / (1 + 0.35 * k) * (220.0 / freq) ** 0.3
        x += (0.7 ** (k - 1)) * np.sin(2 * np.pi * fk * t + rng.uniform(0, 6.28)) * np.exp(-t / decay)
    x *= 1 - np.exp(-t / 0.004)
    thump = fft_filter(rng.normal(size=n) * np.exp(-t / 0.01), lambda f: bp_gain(f, 80, 900, 1))[:n]
    return vel * (x + 0.15 * norm(thump))


def snd_l4_door_music() -> None:
    """~11 s of a melancholy waltz on an old piano, heard faintly through a closed door (door 6).

    An original little piece in A minor (3/4, ~84 bpm): Am | Am | F | E, the melody falling from E5.
    Then muffled (the door takes the highs), a room's reverb, a slow warble and a little crackle,
    as if a record were playing in an empty office.
    """
    rng = rng_for(4230)
    beat = 60 / 84.0
    total = 12 * beat + 3.0
    n = int(total * SR)
    x = np.zeros(n)

    def hz(note):
        names = {"C": -9, "D": -7, "E": -5, "F": -4, "G": -2, "A": 0, "B": 2}
        acc = 1 if "#" in note else 0
        return 440.0 * 2 ** ((names[note[0]] + acc + 12 * (int(note[-1]) - 4)) / 12)

    bars = [("A2", ["E3", "A3", "C4"]), ("A2", ["E3", "A3", "C4"]), ("F2", ["C3", "F3", "A3"]), ("E2", ["B2", "E3", "G#3"])]
    melody = [("E5", 0, 1.0), ("D5", 1, 1.0), ("C5", 2, 1.0),
              ("B4", 3, 1.0), ("C5", 4, 0.5), ("A4", 4.5, 1.5),
              ("F4", 6, 1.0), ("A4", 7, 1.0), ("C5", 8, 1.0),
              ("B4", 9, 2.0), ("G#4", 11, 1.0)]
    for b, (bass, chord) in enumerate(bars):
        add_at(x, _l4_piano_note(hz(bass), 3.2, 0.55, rng), b * 3 * beat, circular=False)
        for j, c in enumerate(chord[:2]):
            add_at(x, _l4_piano_note(hz(c), 1.6, 0.25, rng), (b * 3 + 1 + j) * beat, circular=False)
            add_at(x, _l4_piano_note(hz(chord[2]), 1.6, 0.2, rng), (b * 3 + 1 + j) * beat + 0.02, circular=False)
    for note, at, length in melody:
        start = max(0.0, at * beat + rng.uniform(-0.01, 0.02))   # a slightly human, uneven touch
        add_at(x, _l4_piano_note(hz(note), length * beat + 1.2, 0.5, rng), start, circular=False)
    # Old record: a slow pitch warble (resampled), surface crackle.
    t = np.arange(n) / SR
    warp = t + 0.0025 * np.sin(2 * np.pi * 0.55 * t) / (2 * np.pi * 0.55)
    x = np.interp(warp * SR, np.arange(n), x)
    crackle = clicks(rng, np.sort(rng.uniform(0, total, int(total * 9))), total, 1500, 5000, amp_pow=3.0)
    x = norm(x) + 0.05 * norm(crackle[:n])
    # Behind a closed door and across a room.
    x = fft_filter(x, lambda f: lp_gain(f, 700, 2) * hp_gain(f, 90, 1))[:n]
    x = x + 0.6 * conv(x, reverb_ir(rng, 2.2, 0.6, lp=1200, predelay=0.02))[:n]
    save_ogg(x, SND / "level4" / "door_music.ogg", peak=0.6, fade_in=0.4, fade_out=2.5)


def snd_l4_vend() -> None:
    """Vending machine: a motor whirs, the spiral turns, the item drops into the flap with a thud."""
    rng = rng_for(4240)
    dur = 2.2
    n = int(dur * SR)
    t = np.arange(n) / SR
    motor_env = smoothstep(0.0, 0.12, t) * smoothstep(1.35, 1.15, t)
    f = 92 + 12 * smoothstep(0, 0.4, t)
    saw = 2 * ((np.cumsum(f) / SR) % 1.0) - 1
    motor = fft_filter(saw, lambda ff: bp_gain(ff, 80, 1400, 2))[:n] * motor_env
    whine = np.sin(2 * np.pi * np.cumsum(f * 9.0) / SR) * motor_env * 0.15
    spiral = clicks(rng, np.arange(0.2, 1.2, 0.11) + rng.uniform(0, 0.01, 10), dur, 900, 2600, 0.003, 0.008)
    x = norm(motor) * 0.5 + whine + 0.3 * norm(spiral)
    thud = ring([78, 131, 210], [0.07, 0.05, 0.03], [1.0, 0.5, 0.3], 0.5)
    thud[:200] += fft_filter(rng.normal(size=200), lambda ff: bp_gain(ff, 200, 3000, 1))[:200] * 0.6
    add_at(x, 0.9 * norm(thud), 1.42, circular=False)
    clatter = clicks(rng, 1.45 + np.sort(rng.uniform(0, 0.25, 5)), dur, 1200, 3500, 0.004, 0.012)
    x = x + 0.25 * norm(clatter)
    x = x + 0.2 * conv(x, reverb_ir(rng, 0.8, 0.18, lp=4000))[:n]
    save_ogg(x, SND / "level4" / "vending_vend.ogg", peak=0.8, fade_out=0.2)


def snd_l4_cooler() -> None:
    """Water cooler: water pours into the bottle, two or three big air bubbles glug up the jug."""
    rng = rng_for(4241)
    dur = 1.8
    n = int(dur * SR)
    t = np.arange(n) / SR
    pour_env = smoothstep(0.02, 0.1, t) * smoothstep(1.3, 1.0, t)
    pour = fft_filter(rng.normal(size=n), lambda f: bp_gain(f, 600, 3500, 2))[:n] * pour_env
    pour *= 1 + 0.5 * smooth_jitter(rng, n, 30, 1.0)
    x = 0.25 * norm(pour)
    for at in (0.35, 0.75, 1.15)[: rng.integers(2, 4)]:
        k = int(0.12 * SR)
        kt = np.arange(k) / SR
        fq = 260 + 900 * (kt / 0.12) ** 0.7                     # a bubble's pitch rises as it grows
        bub = np.sin(2 * np.pi * np.cumsum(fq) / SR) * np.exp(-kt / 0.035) * (1 - np.exp(-kt / 0.003))
        add_at(x, 0.8 * bub, at + rng.uniform(-0.03, 0.03), circular=False)
    x = x + 0.25 * conv(x, reverb_ir(rng, 0.5, 0.1, lp=5000))[:n]
    save_ogg(x, SND / "level4" / "water_cooler_glug.ogg", peak=0.8, fade_out=0.15)


L4_SOUNDS = [snd_l4_rain, snd_l4_pipes, snd_l4_thunder, snd_l4_door_music, snd_l4_vend, snd_l4_cooler]


# =============================================================================
# Level 37 "Sublimity": fine cream-white mosaic (8 x 8 small tiles per block),
# the overexposed "sunlit void", chrome for the ladders and rails, the curved
# tile models, and the calm ambience. Run: python tools/gen_assets.py tex_l37_ snd_l37_
# =============================================================================

L37_TILE = 16                    # 8 x 8 small tiles per 128 px face
L37_WHITE = (238, 234, 216)      # #EEEAD8, lit tile ~#f7f5ee .. #dcd9b6 in the references
L37_GROUT = (150, 146, 126)      # #96927E: blended half into the two pixels at each joint, the grout reads ~#c2beab lit


def _l37_tiles(seed, tile_col, grout_col, var=1.6, glaze=1.6, grout_mix=0.5) -> np.ndarray:
    """Pristine small square mosaic: 16 px tiles with a thin, light grout line on the joints (seamless).

    The joint falls between two pixels; each gets about half grout, so the line reads ~1 px wide
    and pale grey like the references (the shading darkens it in shadow), never "graph paper".

    Barely any variation (it is new, clean tile): a whisper of per-tile tone, a soft glaze sheen
    toward each tile's upper left and slightly pillowed edges. No per-pixel speckle anywhere: at a
    distance or a grazing angle it only turned into shimmering noise (mipmaps can't average it).
    """
    rng = rng_for(seed)
    T = L37_TILE
    n = S // T
    X, Y = grid()
    u, v = (X % T) / T, (Y % T) / T
    tone = np.kron(rng.normal(0, var, (n, n)), np.ones((T, T)))
    img = add_lum(fill(tile_col, (S, S)), tone + fnoise(rng, beta=3.2) * 0.6)
    img = add_lum(img, (0.5 - 0.5 * (u + v)) * glaze * 2.0)                    # glaze sheen
    du = np.minimum(X % T, T - X % T)
    dv = np.minimum(Y % T, T - Y % T)
    edge = np.minimum(du, dv)
    img = add_lum(img, -1.5 * smoothstep(3.0, 1.0, edge))                         # pillowed edges
    g = np.minimum(np.abs(wrapd(X, T)), np.abs(wrapd(Y, T)))                     # distance to a joint
    grout = fill(grout_col, (S, S))
    return lerp(img, grout, (grout_mix * smoothstep(1.2, 0.2, g))[..., None])


def tex_l37_tiles() -> None:
    save_png(_l37_tiles(3701, L37_WHITE, L37_GROUT), _blk("l37_tile_wall"))
    # Floor: a touch warmer and darker (it reads #b2b19b dry in the references).
    save_png(_l37_tiles(3702, (230, 225, 204), (142, 137, 116), var=1.8), _blk("l37_tile_floor"))
    # Ceiling: the same tile, a hair cooler.
    save_png(_l37_tiles(3703, (236, 234, 220), (148, 146, 130), var=1.4), _blk("l37_tile_ceiling"))
    # Deep pit lining: dark sea-green glazed tile.
    save_png(_l37_tiles(3704, (46, 96, 80), (22, 46, 38), var=2.5, glaze=2.4), _blk("l37_tile_deep"))


def tex_l37_sunlit_void() -> None:
    """Overexposed warm daylight seen through an opening: almost flat warm white with a whisper
    of haze, so a wall of it tiles without seams (drawn full-bright)."""
    rng = rng_for(3705)
    img = add_lum(fill((255, 248, 228), (S, S)), fnoise(rng, beta=3.5) * 1.2)
    save_png(np.clip(img, 0, 255), _blk("l37_sunlit_void"))


def tex_l37_chrome() -> None:
    """Polished chrome tube: dark and bright bands across the face (it reads as a round tube on
    the thin model elements), a little cool blue in the shadows."""
    rng = rng_for(3706)
    X, Y = grid()
    t = X / S
    band = (0.30 + 0.55 * np.exp(-((t - 0.38) / 0.09) ** 2) + 0.35 * np.exp(-((t - 0.75) / 0.05) ** 2)
            - 0.18 * np.exp(-((t - 0.58) / 0.08) ** 2))
    base = np.stack([band * 236 + 16, band * 238 + 18, band * 242 + 24], axis=-1)
    img = add_lum(base, fnoise(rng, beta=2.0, aniso=(1.0, 6.0)) * 2.0)
    save_png(np.clip(img, 0, 255), _blk("l37_chrome"))


# ---- Curved tile pieces ------------------------------------------------------------
# Smooth curved tile (pillars, rounded corners, wavy walls, arches, round rooms and
# pools). Each piece is the part of ONE block cell that a circle of radius R (centred on
# a block corner) cuts: "convex" = solid inside the circle (pillars, bulges), "concave" =
# solid outside it (rounded rooms, arches, pools). Written as NeoForge OBJ models: true
# curved surfaces with smooth per-vertex normals (the dynamic lights shade them like real
# cylinders), already turned into each of the 8 orientations, with the vanilla side
# shading baked into the vertex colours so a curve meets a flat wall without a brightness
# jump. Must match block/CurveShape.java and block/CurveOrientation.java.

L37_CONVEX_RADII = (1, 2, 3)
L37_CONCAVE_RADII = (1, 2, 3, 4, 6)
# name, lying on its side?, quarter turns: see CurveOrientation.java
L37_ORIENTATIONS = (("se", False, 0), ("sw", False, 1), ("nw", False, 2), ("ne", False, 3),
                    ("z_east", True, 0), ("x_south", True, 1), ("z_west", True, 2), ("x_north", True, 3))


def l37_curve_shapes():
    """(convex, R, i, j) for every cell a quarter circle of radius R crosses (cell (i, j) of
    the quarter x >= 0, z >= 0 around the centre). Order = the CurveShape enum."""
    import math

    def crossed(r):
        return [(i, j) for i in range(r + 1) for j in range(r + 1)
                if math.hypot(i, j) < r < math.hypot(i + 1, j + 1)]
    return ([(True, r, i, j) for r in L37_CONVEX_RADII for (i, j) in crossed(r)]
            + [(False, r, i, j) for r in L37_CONCAVE_RADII for (i, j) in crossed(r)])


def l37_curve_name(shape) -> str:
    convex, r, i, j = shape
    return f"{'convex' if convex else 'concave'}_r{r}_{i}_{j}"


def _l37_curve_obj(shape, orient) -> str:
    """One curve piece as OBJ text in its orientation (block units 0..1). The piece is closed:
    curved skin, its cross-section at both ends of its axis, and flat sides wherever it is
    solid on the cell's boundary (hidden against neighbours, closing it where a passage cuts)."""
    import math
    convex, R, ci, cj = shape
    _, horizontal, turns = orient
    eps = 1e-9

    def solid(lx, lz):
        d = math.hypot(ci + lx, cj + lz)
        return d <= R + eps if convex else d >= R - eps

    # ---- solid cross-section polygon (lx, lz), counter-clockwise -------------------------
    corners = [(0.0, 0.0), (1.0, 0.0), (1.0, 1.0), (0.0, 1.0)]
    pts = []
    for k in range(4):
        a, b = corners[k], corners[(k + 1) % 4]
        sa, sb = solid(*a), solid(*b)
        if sa:
            pts.append((a[0], a[1], "c"))
        if sa != sb:
            lo, hi = 0.0, 1.0
            for _ in range(60):
                m = (lo + hi) / 2
                if solid(a[0] + (b[0] - a[0]) * m, a[1] + (b[1] - a[1]) * m) == sa:
                    lo = m
                else:
                    hi = m
            t = (lo + hi) / 2
            pts.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, "x" if sa else "n"))

    def ang(p):
        return math.atan2(cj + p[1], ci + p[0])
    poly, arc = [], []
    for idx, p in enumerate(pts):
        poly.append((p[0], p[1]))
        if p[2] == "x":
            e = next(pts[(idx + k) % len(pts)] for k in range(1, len(pts) + 1) if pts[(idx + k) % len(pts)][2] == "n")
            ta, tb = ang(p), ang(e)
            # arc points ~0.1 block apart: with smooth normals that already reads perfectly round
            steps = max(2, int(math.ceil(abs(tb - ta) * R / 0.1)))
            arc = [ta + (tb - ta) * s / steps for s in range(steps + 1)]
            for t in arc[1:-1]:
                poly.append((R * math.cos(t) - ci, R * math.sin(t) - cj))
    clean = []
    for p in poly:
        if not clean or math.hypot(p[0] - clean[-1][0], p[1] - clean[-1][1]) > 1e-6:
            clean.append(p)
    if len(clean) > 2 and math.hypot(clean[0][0] - clean[-1][0], clean[0][1] - clean[-1][1]) < 1e-6:
        clean.pop()
    poly = clean

    def tris(pg):
        """Ear clipping of a simple counter-clockwise polygon."""
        idx = list(range(len(pg)))
        out = []

        def cross(o, a, b):
            return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
        guard = 0
        while len(idx) > 3 and guard < 10000:
            guard += 1
            for k in range(len(idx)):
                i0, i1, i2 = idx[k - 1], idx[k], idx[(k + 1) % len(idx)]
                a, b, c = pg[i0], pg[i1], pg[i2]
                if cross(a, b, c) <= 1e-12:
                    continue
                if any(cross(a, b, pg[m]) > 0 and cross(b, c, pg[m]) > 0 and cross(c, a, pg[m]) > 0
                       for m in idx if m not in (i0, i1, i2)):
                    continue
                out.append((i0, i1, i2))
                idx.pop(k)
                break
            else:
                break
        if len(idx) == 3:
            out.append(tuple(idx))
        return out

    faces = []

    def face(ps, ns, uvs, want):
        """Adds a face, flipping its winding so it faces along `want`."""
        a, b, c = (np.array(p) for p in ps[:3])
        if np.dot(np.cross(b - a, c - a), want) < 0:
            ps, ns, uvs = ps[::-1], ns[::-1], uvs[::-1]
        faces.append((ps, ns, uvs))

    # ends of the axis (y = 0 facing down, y = 1 facing up): planar tile mapping
    for (i0, i1, i2) in tris(poly):
        tri = [poly[i0], poly[i1], poly[i2]]
        for y, ny in ((0.0, -1.0), (1.0, 1.0)):
            face([(p[0], y, p[1]) for p in tri], [(0.0, ny, 0.0)] * 3, [(p[0], p[1]) for p in tri], (0, ny, 0))
    # flat sides on the cell boundary where the piece is solid
    for axis, val in (("x", 0.0), ("x", 1.0), ("z", 0.0), ("z", 1.0)):
        def on(a):
            return solid(val, a) if axis == "x" else solid(a, val)
        inside = [k / 64 for k in range(65) if on(k / 64)]
        if len(inside) < 2:
            continue
        a0, a1 = inside[0], inside[-1]

        def refine(a_in, a_out):
            lo, hi = a_in, a_out
            for _ in range(50):
                m = (lo + hi) / 2
                lo, hi = (m, hi) if on(m) else (lo, m)
            return lo
        if a0 > 0:
            a0 = refine(a0, a0 - 1 / 64)
        if a1 < 1:
            a1 = refine(a1, a1 + 1 / 64)
        if a1 - a0 < 1e-4:
            continue
        sign = 1.0 if val == 1.0 else -1.0
        if axis == "x":
            ps = [(val, 0.0, a0), (val, 0.0, a1), (val, 1.0, a1), (val, 1.0, a0)]
            nrm = (sign, 0.0, 0.0)
        else:
            ps = [(a0, 0.0, val), (a1, 0.0, val), (a1, 1.0, val), (a0, 1.0, val)]
            nrm = (0.0, 0.0, sign)
        face(ps, [nrm] * 4, [(a0, 0.0), (a1, 0.0), (a1, 1.0), (a0, 1.0)], nrm)
    # the curved skin: smooth radial normals; u runs along the arc, a whole number of tiles
    # (8 per block) per quarter circle so the grout meets the flat walls on a joint
    tiles = max(1, round(R * math.pi / 2 * 8))
    u_per_rad = tiles / 8 / (math.pi / 2)
    out_sign = 1.0 if convex else -1.0
    cuts = list(arc)
    if len(cuts) >= 2:
        u_lo, u_hi = min(cuts) * u_per_rad, max(cuts) * u_per_rad
        for k in range(int(math.floor(u_lo)) + 1, int(math.ceil(u_hi))):
            cuts.append(k / u_per_rad)                 # split where u crosses a whole block of texture
        cuts = sorted(set(round(c, 12) for c in cuts))
        for t0, t1 in zip(cuts, cuts[1:]):
            if t1 - t0 < 1e-9:
                continue
            base = math.floor((t0 + t1) / 2 * u_per_rad)
            u0, u1 = t0 * u_per_rad - base, t1 * u_per_rad - base
            p0 = (R * math.cos(t0) - ci, R * math.sin(t0) - cj)
            p1 = (R * math.cos(t1) - ci, R * math.sin(t1) - cj)
            n0 = (out_sign * math.cos(t0), 0.0, out_sign * math.sin(t0))
            n1 = (out_sign * math.cos(t1), 0.0, out_sign * math.sin(t1))
            mid = (t0 + t1) / 2
            face([(p0[0], 0.0, p0[1]), (p1[0], 0.0, p1[1]), (p1[0], 1.0, p1[1]), (p0[0], 1.0, p0[1])],
                 [n0, n1, n1, n0], [(u0, 0.0), (u1, 0.0), (u1, 1.0), (u0, 1.0)],
                 (out_sign * math.cos(mid), 0.0, out_sign * math.sin(mid)))

    def turn(v, is_normal):
        x, y, z = v
        c = 0.0 if is_normal else 0.5
        if horizontal:          # lying down (blockstate x = 90): +z -> +y, +y -> -z
            y, z = c + (z - c), c - (y - c)
        for _ in range(turns):  # each quarter turn (blockstate y = 90): (dx, dz) -> (-dz, dx)
            x, z = c - (z - c), c + (x - c)
        return (x, y, z)

    def shade(n):
        # Face shading like vanilla up (1) and down (.5), but every upright face the same .7
        # whichever way it faces (vanilla: east/west .6, north/south .8). With vanilla's, a curve
        # bending out of an east-facing wall and back passes through "north-facing" angles and came
        # out lighter than the flat wall on both sides. The flat tile walls use the same .7
        # (block model l37_tile_wall: unshaded sides with the texture darkened to .7, see
        # tex_l37_upright_tile), so walls and curves meet without a seam.
        nx, ny, nz = n
        return L37_UPRIGHT_SHADE * (nx * nx + nz * nz) + (1.0 if ny > 0 else 0.5) * ny * ny

    lines = ["# generated by tools/gen_assets.py (tex_l37_curve_models)", "mtllib l37_curve.mtl", "usemtl tile"]
    body = []
    vi = 1
    for ps, ns, uvs in faces:
        refs = []
        for pnt, nrm, uv in zip(ps, ns, uvs):
            wp, wn = turn(pnt, False), turn(nrm, True)
            s = shade(wn)
            lines.append(f"v {wp[0]:.5f} {wp[1]:.5f} {wp[2]:.5f}")
            lines.append(f"vt {uv[0]:.5f} {uv[1]:.5f}")
            lines.append(f"vn {wn[0]:.5f} {wn[1]:.5f} {wn[2]:.5f}")
            lines.append(f"vc {s:.4f} {s:.4f} {s:.4f} 1.0")
            refs.append(f"{vi}/{vi}/{vi}/{vi}")
            vi += 1
        body.append("f " + " ".join(refs))
    return "\n".join(lines + body) + "\n"


L37_UPRIGHT_SHADE = 0.7


def tex_l37_upright_tile() -> None:
    """The fine wall tile darkened to the upright shade, and the flat wall's model: its four sides
    drawn unshaded with this texture, top and bottom shaded as usual (see the shade in
    _l37_curve_obj). So every upright tile face is .7, flat or curved."""
    import json

    img = np.asarray(Image.open(TEX / "block" / "l37_tile_wall.png").convert("RGBA"), float)
    img[..., :3] *= L37_UPRIGHT_SHADE
    save_png(img, TEX / "block" / "l37_tile_wall_upright.png")
    faces_side = {d: {"texture": "#upright", "cullface": d} for d in ("north", "south", "east", "west")}
    faces_cap = {d: {"texture": "#all", "cullface": d} for d in ("up", "down")}
    model = {
        "parent": "minecraft:block/block",
        "textures": {"particle": "backrooms:block/l37_tile_wall", "all": "backrooms:block/l37_tile_wall",
                     "upright": "backrooms:block/l37_tile_wall_upright"},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16], "shade": False, "faces": faces_side},
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces_cap},
        ],
    }
    (ROOT / "models" / "block" / "l37_tile_wall.json").write_text(json.dumps(model, indent=2) + "\n")


def tex_l37_curve_models() -> None:
    """OBJ models, block models and the blockstate of backrooms:l37_tile_curve."""
    import json
    folder = ROOT / "models" / "block" / "l37" / "curve"
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "l37_curve.mtl").write_text("newmtl tile\nKd 1.0 1.0 1.0\nmap_Kd #tile\n")
    variants = {}
    for shape in l37_curve_shapes():
        name = l37_curve_name(shape)
        for orient in L37_ORIENTATIONS:
            model = f"{name}_{orient[0]}"
            (folder / f"{model}.obj").write_text(_l37_curve_obj(shape, orient))
            (folder / f"{model}.json").write_text(json.dumps({
                "loader": "neoforge:obj",
                "model": f"backrooms:models/block/l37/curve/{model}.obj",
                "flip_v": True,
                "shade_quads": False,
                "emissive_ambient": False,
                "automatic_culling": True,
                # Flat lighting: each piece takes the light of its own cell (smooth lighting
                # sampled inside pillars and made the blocks band). Level 37's fill is flat across
                # lit rooms (Level37Atmosphere.applyFill), so neighbouring pieces match.
                "ambientocclusion": False,
                "textures": {"tile": "backrooms:block/l37_tile_wall", "particle": "backrooms:block/l37_tile_wall"},
            }, indent=2) + "\n")
            variants[f"orientation={orient[0]},shape={name}"] = {"model": f"backrooms:block/l37/curve/{model}"}
    (ROOT / "blockstates" / "l37_tile_curve.json").write_text(json.dumps({"variants": variants}, indent=2) + "\n")


def snd_l37_ambience() -> None:
    """16 s loop: a huge, calm, tiled pool hall. Soft lapping close by, slow sloshing further off,
    three distant drips with a long bright tail, and a damped room tone. No machinery.
    (16 s rather than longer: libsndfile 1.2.2's Vorbis encoder crashes on longer one-shot writes.)"""
    rng = rng_for(3710)
    T = 16.0
    n = int(T * SR)
    t = np.arange(n) / SR
    lap = periodic_noise(n, rng, lambda f: bp_gain(f, 150, 900, 2))
    env = np.zeros(n)
    for _ in range(11):
        c, w = rng.uniform(0, T), rng.uniform(0.35, 0.9)
        env += rng.uniform(0.3, 0.9) * np.exp(-0.5 * (wrapd(t - c, T) / w) ** 2)
    lap = lap * (0.1 + env)
    slosh = periodic_noise(n, rng, lambda f: bp_gain(f, 50, 220, 2)) * drift(n, rng, 0.45, 0.06, 0.25)
    room = periodic_noise(n, rng, lambda f: hp_gain(f, 20, 2) * lp_gain(f, 260, 2) / np.maximum(f, 20))
    drips = np.zeros(n)
    for start, f0 in ((2.9, 1250.0), (8.6, 980.0), (13.1, 1420.0)):
        dt = t_axis(0.14)
        d = np.sin(2 * np.pi * np.cumsum(f0 * (1 + 0.5 * np.clip(dt / 0.04, 0, 1))) / SR)
        add_at(drips, d * np.exp(-dt / 0.03) * smoothstep(0, 0.003, dt) * rng.uniform(0.5, 1.0), start, circular=True)
    drips = fft_filter(drips, lambda f: lp_gain(f, 2200, 2), circular=True)
    events = norm(lap) * 0.5 + norm(drips) * 0.35
    ir = reverb_ir(rng, 5.5, 2.0, lp=2800, predelay=0.05, early=((0.06, 0.5), (0.13, 0.35), (0.21, 0.25)))
    wet = circ_conv(events, ir)
    x = norm(lap) * 0.16 + norm(slosh) * 0.12 + norm(wet) * 0.62 + norm(room) * 0.22
    x = fft_filter(x, lambda f: lp_gain(f, 3200, 1), circular=True)       # muted, damped
    save_ogg(x, SND / "ambient" / "level37_ambience.ogg", peak=0.5, loop=True)


def snd_l37_arrive() -> None:
    """3.5 s: walking into the light. A soft airy swell and a warm, slowly blooming chord that
    dissolves into a long reverb (no glitch, no hiss: the calm opposite of the noclip)."""
    rng = rng_for(3711)
    dur = 3.5
    n = int(dur * SR)
    t = np.arange(n) / SR
    swell = np.sin(np.pi * np.clip(t / 2.6, 0, 1)) ** 2
    air = fft_filter(rng.normal(size=n), lambda f: bp_gain(f, 300, 2600, 2)) * swell
    chord = sum(a * np.sin(2 * np.pi * f * t + p) for f, a, p in
                ((220.0, 1.0, 0.0), (277.18, 0.7, 1.1), (329.63, 0.6, 2.3), (440.0, 0.35, 0.7)))
    chord *= np.clip(t / 1.4, 0, 1) ** 2 * np.exp(-np.maximum(t - 1.6, 0) / 0.9)
    chord *= 1 + 0.004 * np.sin(2 * np.pi * 4.5 * t)
    dry = norm(air) * 0.45 + norm(chord) * 0.55
    wet = conv(dry, reverb_ir(rng, 2.5, 1.0, lp=4000, predelay=0.03))[:n]
    x = norm(dry) * 0.5 + norm(wet) * 0.5
    save_ogg(x, SND / "level37" / "arrive.ogg", peak=0.6, fade_out=0.6)


L37_TEXTURES = [tex_l37_tiles, tex_l37_sunlit_void, tex_l37_chrome, tex_l37_upright_tile, tex_l37_curve_models]
L37_SOUNDS = [snd_l37_ambience, snd_l37_arrive]


# =============================================================================
# Level 94 "Motion" props (Props slice): the siren poles and the happy cartoon
# tune they play, the parked 1930s cars and white milk vans, and the CRT TV
# full of static. The level's SUBTLE crafted look: slightly over-saturated
# colours, hand-brushed paint and grain, toy-like.
# =============================================================================

def _l94_paint(rng, col, shape=(S, S), grain=5.0) -> np.ndarray:
    """Hand-brushed enamel: soft blotches, faint brush strokes running one way, fine grain."""
    img = fill(col, shape)
    lum = (fnoise(rng, shape, beta=3.0) * grain
           + fnoise(rng, shape, beta=1.2, aniso=(10, 1), kmin=3) * grain * 0.45
           + rng.normal(0, 1.2, shape))
    return add_lum(img, lum)


def tex_l94_siren() -> None:
    """The siren pole and its horn loudspeaker.

    l94_siren_pole: weathered dark wooden pole (vertical grain, cracks, sun-bleached streaks).
    l94_siren_metal: grey enamel horn, chipped to dark primer, a few rust runs.
    l94_siren_bracket: dark iron yoke. l94_siren_mouth: looking into the bell (light rim, grey
    flare darkening into the throat, two grooves, the phase plug). The mouth is perfectly round
    and has no noise, because the model draws the bell twice (turned 45 degrees) and the two
    copies must look the same where they overlap.
    """
    rng = rng_for(9401)
    X, Y = grid()
    pole = _wood(rng, np.array([78, 60, 44]), horizontal=False)
    cracks = smoothstep(0.06, 0.0, np.abs(fnoise(rng, beta=1.8, aniso=(1, 14), kmin=2)))
    pole = add_lum(pole, -26 * cracks)
    bleach = smoothstep(0.4, 1.6, fnoise(rng, beta=2.0, aniso=(1, 8)))
    pole = over(pole, np.array([112, 104, 92]), bleach * 0.35)
    save_png(pole, _blk("l94_siren_pole"))

    metal = _l94_paint(rng, (150, 156, 162), grain=4.0)
    chips = smoothstep(1.7, 2.3, blur(fnoise(rng, beta=1.3, kmin=3), 0.8))
    metal = over(metal, np.array([82, 78, 74]), chips * 0.8)
    rust = smoothstep(1.2, 2.2, fnoise(rng, beta=1.5, aniso=(1, 6)))
    metal = over(metal, np.array([140, 84, 50]), rust * 0.45)
    save_png(metal, _blk("l94_siren_metal"))

    bracket = _galvanised(rng, (60, 60, 64))
    bracket = over(bracket, np.array([110, 64, 44]), smoothstep(1.4, 2.2, fnoise(rng, beta=1.6)) * 0.5)
    save_png(bracket, _blk("l94_siren_bracket"))

    r = np.hypot(X - S / 2, Y - S / 2) / (S / 2)          # 0 at the centre, 1 at the edge midpoints
    flare = lerp(np.array([118, 122, 128]), np.array([30, 30, 34]), smoothstep(0.85, 0.25, r)[..., None])
    mouth = lerp(flare, np.array([172, 176, 182]), smoothstep(0.84, 0.9, r)[..., None])   # rolled rim
    mouth = add_lum(mouth, -22 * np.exp(-((r - 0.9) / 0.02) ** 2))                         # rim edge
    for g in (0.68, 0.48):                                                                 # grooves
        mouth = add_lum(mouth, -14 * np.exp(-((r - g) / 0.015) ** 2) + 8 * np.exp(-((r - g - 0.03) / 0.015) ** 2))
    plug = smoothstep(0.19, 0.16, r)
    mouth = over(mouth, np.array([70, 72, 76]), plug)
    mouth = add_lum(mouth, 30 * smoothstep(0.16, 0.0, r) * plug)                           # domed plug
    save_png(mouth, _blk("l94_siren_mouth"))


def _l94_rrect(shape, x0, y0, x1, y1, rad, k=1.0) -> np.ndarray:
    """Anti-aliased rounded rectangle; coordinates are multiplied by k (e.g. pixels per unit)."""
    cx, cy = (x0 + x1) / 2 * k, (y0 + y1) / 2 * k
    hw, hh, rr = (x1 - x0) / 2 * k, (y1 - y0) / 2 * k, rad * k

    def inside(x, y):
        dx = np.maximum(np.abs(x - cx) - (hw - rr), 0)
        dy = np.maximum(np.abs(y - cy) - (hh - rr), 0)
        return (dx * dx + dy * dy) < rr * rr
    return supersample(shape, inside)


def tex_l94_tv() -> None:
    """The Level 94 TV: an old wooden-cabinet CRT on short legs, its screen full of static.

    l94_tv_cabinet: warm, slightly over-saturated veneer. l94_tv_front: the cabinet front as seen
    from the room (8 px per model pixel; the screen element covers u 2.5..10.5, v 3..10.5): cream
    trim and black bezel round the screen, two knob plates, a cloth speaker grille, a brass badge.
    l94_tv_knob: cream bakelite. l94_tv_static: animated snow (24 frames, one per tick) with a
    slow rolling bar and dark CRT corners; drawn full-bright by the model.
    """
    rng = rng_for(9420)
    X, Y = grid()
    k = S / 16
    wood = _wood(rng, np.array([152, 88, 46]), horizontal=True)
    wood = add_lum(wood, fnoise(rng, beta=2.6) * 4)
    save_png(wood, _blk("l94_tv_cabinet"))

    front = wood.copy()
    front = over(front, np.array([222, 204, 158]), _l94_rrect((S, S), 1.9, 2.4, 11.1, 11.1, 1.3, k))
    front = over(front, np.array([30, 26, 24]), _l94_rrect((S, S), 2.2, 2.7, 10.8, 10.8, 1.0, k))
    for cy in (4.5, 7.0):
        plate = supersample((S, S), lambda x, y, cy=cy: np.hypot(x - 12.25 * k, y - cy * k) < 1.1 * k)
        front = over(front, np.array([196, 164, 92]), plate)
    grille = _l94_rrect((S, S), 11.3, 8.4, 13.7, 12.2, 0.4, k)
    cloth = add_lum(fill((206, 184, 132), (S, S)), rng.normal(0, 5, (S, S)))
    cloth = add_lum(cloth, -40 * ((X % (0.5 * k)) < 1.5))
    front = lerp(front, cloth, grille[..., None])
    front = over(front, np.array([200, 168, 90]), box((S, S), 5.4 * k, 11.5 * k, 7.6 * k, 12.1 * k))
    edge = np.minimum.reduce([X - 1.5 * k, 14.5 * k - X, Y - 1.5 * k, 12.5 * k - Y])
    front = add_lum(front, -22 * smoothstep(5, 0, edge))
    save_png(front, _blk("l94_tv_front"))

    knob = add_lum(fill((226, 214, 182), (S, S)), fnoise(rng, beta=2.5) * 4)
    knob = add_lum(knob, -18 * smoothstep(3, 0, np.minimum.reduce([X, S - X, Y, S - Y])))
    save_png(knob, _blk("l94_tv_knob"))

    frames, n = 24, 32
    yy, xx = np.mgrid[0:n, 0:n] + 0.5
    c = n / 2
    corners = np.clip(1.15 - 0.55 * (((xx - c) / c) ** 4 + ((yy - c) / c) ** 4), 0, 1)
    strip = []
    for i in range(frames):
        snow = rng.random((n, n)) ** 1.3
        lines = rng.normal(0, 0.1, (n, 1))                       # whole lines flick brighter/darker
        bar = 0.22 * np.exp(-(wrapd(yy - i / frames * n, n) / 3.0) ** 2)
        v = np.clip(0.16 + 0.66 * snow + lines + bar, 0, 1) * corners
        strip.append(v[..., None] * np.array([226, 232, 242]))
    path = _blk("l94_tv_static")
    save_png(np.concatenate(strip, axis=0), path)
    path.with_name("l94_tv_static.png.mcmeta").write_text('{"animation": {"frametime": 1}}\n')


# Vehicle atlases: 256x256 texture units drawn at 2 px per unit. The rectangles are the material
# "swatches" of client/VehicleModel.java (u0, v0, u1, v1 in texture units): keep them in sync.
L94_VPX = 2
L94_CAR_SWATCHES = {"paint": (0, 0, 160, 80), "fender": (0, 80, 72, 112), "chrome": (72, 80, 152, 112),
                    "grille": (152, 80, 184, 112), "tyre": (184, 80, 216, 112), "wheel": (216, 80, 240, 104),
                    "glass": (0, 112, 64, 144), "lens": (64, 112, 76, 118), "tail": (80, 112, 96, 120)}
L94_VAN_SWATCHES = {"box": (0, 0, 160, 80), "paint": (0, 96, 200, 160), "chrome": (160, 0, 256, 16),
                    "tyre": (160, 16, 192, 48), "wheel": (192, 16, 220, 44), "lens": (224, 16, 238, 23),
                    "tail": (240, 16, 256, 32), "grille": (160, 48, 200, 60), "door": (208, 48, 236, 92),
                    "glass": (0, 162, 64, 176)}


def _l94_put(atlas, rect, img, alpha=None) -> None:
    """Pastes an RGB image into a swatch rectangle (texture units) of an RGBA atlas."""
    u0, v0, u1, v1 = (c * L94_VPX for c in rect)
    atlas[v0:v1, u0:u1, :3] = img
    atlas[v0:v1, u0:u1, 3] = 255.0 if alpha is None else alpha


def _l94_rect_shape(rect):
    return ((rect[3] - rect[1]) * L94_VPX, (rect[2] - rect[0]) * L94_VPX)


def _l94_chrome(rng, shape) -> np.ndarray:
    """Polished chrome: bright and dark reflection bands, a little dull grain."""
    yy = np.arange(shape[0])[:, None] / max(shape[0], 1)
    bands = 26 * np.sin(2 * np.pi * (yy * 2.3 + 0.15)) + 14 * np.sin(2 * np.pi * yy * 7.1)
    img = fill((196, 200, 208), shape)
    return add_lum(img, np.broadcast_to(bands, shape) + rng.normal(0, 3, shape))


def _l94_glass(rng, shape) -> np.ndarray:
    """Dark window glass with the sky's pale reflection along the top and one diagonal streak."""
    yy, xx = np.mgrid[0:shape[0], 0:shape[1]]
    img = fill((54, 70, 86), shape)
    img = add_lum(img, 26 * (1 - yy / shape[0]) ** 2, (0.9, 1.0, 1.1))
    streak = np.exp(-(((xx - yy * 1.2) % 40 - 20) / 3.0) ** 2)
    return add_lum(img, 22 * streak + rng.normal(0, 1.5, shape))


def _l94_disc(units, rings, px=L94_VPX):
    """A round picture on a square (wheel side, headlight lens) with see-through corners.

    rings: list of (outer radius in units, rgb), outermost first. Returns (rgb, alpha)."""
    n = int(units * px)
    yy, xx = np.mgrid[0:n, 0:n] + 0.5
    r = np.hypot(xx - n / 2, yy - n / 2) / px
    img = np.zeros((n, n, 3))
    for radius, col in rings:
        img[r < radius] = col
    alpha = np.where(r < rings[0][0], 255.0, 0.0)
    return img, alpha, r


def _l94_wheel_side(units, rim, whitewall=True):
    """Side of a wheel: black tyre (with a whitewall), coloured rim with spoke lines, chrome cap."""
    R = units / 2
    rings = [(R, (28, 28, 30))]
    if whitewall:
        rings.append((R * 0.82, (232, 228, 214)))
    rings += [(R * 0.62, (24, 24, 26)), (R * 0.57, rim), (R * 0.24, (212, 216, 222)), (R * 0.1, (150, 154, 160))]
    img, alpha, r = _l94_disc(units, rings)
    n = img.shape[0]
    yy, xx = np.mgrid[0:n, 0:n] + 0.5
    ang = np.arctan2(yy - n / 2, xx - n / 2)
    spokes = (np.abs(np.sin(ang * 4)) < 0.18) & (r > R * 0.26) & (r < R * 0.55)
    img[spokes] = np.asarray(rim) * 0.65
    return img, alpha


def _l94_lens(units):
    """Round headlight: chrome ring round a pale yellow glass with a highlight."""
    R = units / 2
    img, alpha, r = _l94_disc(units, [(R, (206, 210, 216)), (R * 0.78, (250, 238, 182))])
    n = img.shape[0]
    yy, xx = np.mgrid[0:n, 0:n] + 0.5
    hl = np.hypot(xx - n * 0.38, yy - n * 0.36) < n * 0.12
    img[hl & (r < R * 0.78)] = (255, 255, 240)
    return img, alpha


def _l94_car_atlas(seed, paint, fender, rim) -> np.ndarray:
    rng = rng_for(seed)
    sw = L94_CAR_SWATCHES
    atlas = np.zeros((256 * L94_VPX, 256 * L94_VPX, 4))
    _l94_put(atlas, sw["paint"], _l94_paint(rng, paint, _l94_rect_shape(sw["paint"])))
    _l94_put(atlas, sw["fender"], _l94_paint(rng, fender, _l94_rect_shape(sw["fender"]), grain=3.0))
    _l94_put(atlas, sw["chrome"], _l94_chrome(rng, _l94_rect_shape(sw["chrome"])))
    shp = _l94_rect_shape(sw["grille"])
    grille = fill((36, 36, 40), shp)
    grille[:, (np.arange(shp[1]) % 3) == 0] = (206, 210, 216)
    _l94_put(atlas, sw["grille"], grille)
    shp = _l94_rect_shape(sw["tyre"])
    tyre = add_lum(fill((30, 30, 32), shp), rng.normal(0, 2, shp) - 8 * ((np.arange(shp[0])[:, None] % 4) == 0))
    _l94_put(atlas, sw["tyre"], tyre)
    _l94_put(atlas, sw["glass"], _l94_glass(rng, _l94_rect_shape(sw["glass"])))
    _l94_put(atlas, sw["tail"], fill((200, 30, 26), _l94_rect_shape(sw["tail"])))
    # Wheel sides: the plane's two faces sit side by side in the bottom 12x12 of its swatch.
    wheel, alpha = _l94_wheel_side(12, rim)
    u0, v0 = sw["wheel"][:2]
    for du in (0, 12):
        _l94_put(atlas, (u0 + du, v0 + 12, u0 + du + 12, v0 + 24), wheel, alpha)
    lens, alpha = _l94_lens(6)
    u0, v0 = sw["lens"][:2]
    for du in (0, 6):
        _l94_put(atlas, (u0 + du, v0, u0 + du + 6, v0 + 6), lens, alpha)
    return atlas


def _l94_van_atlas() -> np.ndarray:
    rng = rng_for(9433)
    sw = L94_VAN_SWATCHES
    white = (238, 236, 226)
    atlas = np.zeros((256 * L94_VPX, 256 * L94_VPX, 4))
    p = L94_VPX
    # Box body: white all over, then the two long sides (44x36) lettered MILK, the back with doors.
    _l94_put(atlas, sw["box"], _l94_paint(rng, white, _l94_rect_shape(sw["box"]), grain=3.5))
    side_shape = (36 * p, 44 * p)
    for u0 in (0, 80):
        side = _l94_paint(rng, white, side_shape, grain=3.5)
        side = over(side, np.array([40, 92, 170]), box(side_shape, -1, 27 * p, 44 * p + 1, 30 * p))   # blue band
        side = over(side, np.array([200, 36, 36]), _text("MILK", 22 * p, 14 * p, 20 * p, maxw=36 * p, shape=side_shape))
        side = over(side, np.array([40, 92, 170]), _text("FRESH DAILY", 22 * p, 23.5 * p, 5 * p, maxw=30 * p, shape=side_shape))
        side = add_lum(side, -20 * (box(side_shape, 0, 0, 44 * p, 1.0 * p) + box(side_shape, 0, 35 * p, 44 * p, 36 * p)))
        _l94_put(atlas, (u0, 44, u0 + 44, 80), side)
    back_shape = (36 * p, 36 * p)
    back = _l94_paint(rng, white, back_shape, grain=3.5)
    back = add_lum(back, -40 * box(back_shape, 17.8 * p, 2 * p, 18.2 * p, 35 * p))                   # door split
    for x0 in (4, 21):
        back = over(back, np.array([54, 70, 86]), box(back_shape, x0 * p, 4 * p, (x0 + 11) * p, 12 * p))   # windows
    for x0 in (15, 19.5):
        back = over(back, np.array([200, 204, 210]), box(back_shape, x0 * p, 17 * p, (x0 + 1.5) * p, 18 * p))  # handles
    back = over(back, np.array([40, 92, 170]), box(back_shape, -1, 27 * p, 36 * p + 1, 30 * p))
    _l94_put(atlas, (124, 44, 160, 80), back)
    _l94_put(atlas, sw["paint"], _l94_paint(rng, white, _l94_rect_shape(sw["paint"]), grain=3.5))
    _l94_put(atlas, sw["chrome"], _l94_chrome(rng, _l94_rect_shape(sw["chrome"])))
    shp = _l94_rect_shape(sw["tyre"])
    _l94_put(atlas, sw["tyre"], add_lum(fill((30, 30, 32), shp), rng.normal(0, 2, shp)))
    _l94_put(atlas, sw["tail"], fill((200, 30, 26), _l94_rect_shape(sw["tail"])))
    shp = _l94_rect_shape(sw["grille"])
    grille = fill((36, 36, 40), shp)
    grille[(np.arange(shp[0]) % 3) == 0, :] = (206, 210, 216)
    _l94_put(atlas, sw["grille"], grille)
    _l94_put(atlas, sw["glass"], _l94_glass(rng, _l94_rect_shape(sw["glass"])))
    # Sliding doors: the two 13x31 side faces of the door slab, a window in the top part.
    shp = _l94_rect_shape(sw["door"])
    _l94_put(atlas, sw["door"], _l94_paint(rng, white, shp, grain=3.5))
    door_shape = (31 * p, 13 * p)
    door = _l94_paint(rng, white, door_shape, grain=3.5)
    door = add_lum(door, -30 * (1 - box(door_shape, 0.6 * p, 0.6 * p, 12.4 * p, 30.4 * p)))          # seam
    door = over(door, np.array([54, 70, 86]), _l94_rrect(door_shape, 2, 3, 11, 13, 1.0, p))            # window
    door = over(door, np.array([200, 204, 210]), box(door_shape, 9.5 * p, 16 * p, 11 * p, 17 * p))     # handle
    door = over(door, np.array([40, 92, 170]), box(door_shape, -1, 18 * p, 13 * p + 1, 21 * p))        # band
    u0, v0 = sw["door"][:2]
    for du in (0, 14):
        _l94_put(atlas, (u0 + du, v0 + 13, u0 + du + 13, v0 + 44), door)
    wheel, alpha = _l94_wheel_side(14, (236, 232, 220), whitewall=False)
    u0, v0 = sw["wheel"][:2]
    for du in (0, 14):
        _l94_put(atlas, (u0 + du, v0 + 14, u0 + du + 14, v0 + 28), wheel, alpha)
    lens, alpha = _l94_lens(7)
    u0, v0 = sw["lens"][:2]
    for du in (0, 7):
        _l94_put(atlas, (u0 + du, v0, u0 + du + 7, v0 + 7), lens, alpha)
    return atlas


def tex_l94_vehicle() -> None:
    """Parked Level 94 vehicles (textures for client/VehicleModel): three 1930s sedans (red with
    black fenders and cream wheels, all black with red wheels, cream with maroon fenders), the
    white milk van, and the inventory icon (a little red sedan from the side)."""
    ent = TEX / "entity"
    save_png(_l94_car_atlas(9430, (196, 38, 36), (34, 32, 34), (232, 218, 176)), ent / "l94_vehicle_red.png")
    save_png(_l94_car_atlas(9431, (40, 40, 46), (30, 30, 32), (190, 40, 36)), ent / "l94_vehicle_black.png")
    save_png(_l94_car_atlas(9432, (234, 216, 168), (112, 36, 34), (190, 40, 36)), ent / "l94_vehicle_cream.png")
    save_png(_l94_van_atlas(), ent / "l94_vehicle_van.png")
    # Icon: 32x32 side view, drawn at 4x and shrunk.
    k = 4
    im = Image.new("RGBA", (32 * k, 32 * k), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    s = lambda *c: [v * k for v in c]
    d.rounded_rectangle(s(9, 7, 21, 15), radius=3 * k, fill=(196, 38, 36, 255))          # cabin
    d.rectangle(s(11, 9, 15, 13), fill=(54, 70, 86, 255))
    d.rectangle(s(16, 9, 19, 13), fill=(54, 70, 86, 255))
    d.rounded_rectangle(s(2, 13, 29, 21), radius=3 * k, fill=(196, 38, 36, 255))         # body
    d.ellipse(s(1, 15, 12, 23), fill=(34, 32, 34, 255))                                    # fenders
    d.ellipse(s(20, 15, 31, 23), fill=(34, 32, 34, 255))
    d.rectangle(s(9, 20, 23, 21), fill=(34, 32, 34, 255))                                  # running board
    for cx in (6.5, 25.5):
        d.ellipse(s(cx - 4, 18, cx + 4, 26), fill=(28, 28, 30, 255))
        d.ellipse(s(cx - 2.6, 19.4, cx + 2.6, 24.6), fill=(232, 228, 214, 255))
        d.ellipse(s(cx - 1.4, 20.6, cx + 1.4, 23.4), fill=(232, 218, 176, 255))
    d.ellipse(s(1, 14, 4, 17), fill=(250, 238, 182, 255))                                  # headlight
    icon = np.asarray(im.resize((32, 32), Image.BOX), float)
    icon[..., 3] = np.where(icon[..., 3] >= 128, 255, 0)
    save_png(icon, TEX / "item" / "l94_vehicle.png")


# ----- the siren song -----

def _l94_midi_hz(m) -> float:
    return 440.0 * 2 ** ((m - 69) / 12)


def _l94_tuba(freq, dur) -> np.ndarray:
    """Oom: a round brassy tuba note with a little scoop and blat at the start."""
    t = t_axis(dur + 0.12)
    f = freq * (1 - 0.03 * np.exp(-t / 0.03))
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = sum(a * (1 + 1.5 * (k > 2) * np.exp(-t / 0.05)) * np.sin(k * ph)
            for k, a in enumerate((1, 0.85, 0.6, 0.42, 0.28, 0.18, 0.11, 0.07, 0.04), start=1))
    env = np.clip(t / 0.025, 0, 1) * np.exp(-t / 0.7) * np.clip((dur + 0.1 - t) / 0.1, 0, 1)
    return x * env


def _l94_clarinet(freq, dur, rng) -> np.ndarray:
    """A woody clarinet lead: mostly odd harmonics, breath on the attack, vibrato on long notes."""
    t = t_axis(dur + 0.08)
    vib = 1 + 0.005 * np.sin(2 * np.pi * 5.6 * t) * smoothstep(0.12, 0.3, t)
    ph = 2 * np.pi * np.cumsum(freq * vib) / SR
    x = sum((1.0 / k if k % 2 else 0.09 / k) * np.sin(k * ph) for k in range(1, 12) if k * freq < 9000)
    breath = fft_filter(rng.normal(size=t.size), lambda fr: bp_gain(fr, freq * 1.5, freq * 4, 2)) * np.exp(-t / 0.04)
    env = np.clip(t / 0.03, 0, 1) * (0.85 + 0.15 * np.exp(-t / 0.1)) * np.clip((dur + 0.06 - t) / 0.06, 0, 1)
    return (x + 0.4 * norm(breath)) * env


def _l94_xylophone(freq, dur) -> np.ndarray:
    """A bright wooden xylophone bar: inharmonic partials that die away fast, a mallet click."""
    t = t_axis(max(dur, 0.25) + 0.3)
    x = ring([freq, freq * 3.93, freq * 9.1], [0.32, 0.07, 0.025], [1.0, 0.35, 0.12], t.size / SR)
    x[:30] += np.linspace(0.6, 0, 30) * np.sign(np.sin(np.arange(30)))
    return x


def _l94_banjo(midis, dur, rng) -> np.ndarray:
    """Pah: a quick banjo strum of the chord (each string a little after the last), short and twangy."""
    out = np.zeros(int((dur + 0.3) * SR))
    for j, m in enumerate(midis):
        f = _l94_midi_hz(m)
        t = t_axis(dur + 0.3 - j * 0.008)
        s = sum((1.0 / k) * np.sin(2 * np.pi * f * k * t + rng.uniform(0, 6.28)) * np.exp(-t * (6 + 5 * k))
                for k in range(1, 9))
        add_at(out, s * np.clip((dur + 0.05 - t) / 0.05, 0, 1), j * 0.008, circular=False)
    return out


def _l94_slide_whistle(f0, f1, dur, rng) -> np.ndarray:
    """A slide whistle swooping from f0 to f1 Hz (smoothly), breathy, with a wobble."""
    t = t_axis(dur)
    glide = f0 * (f1 / f0) ** smoothstep(0.0, 1.0, t / dur)
    f = glide * (1 + 0.012 * np.sin(2 * np.pi * 7 * t))
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = np.sin(ph) + 0.12 * np.sin(2 * ph)
    breath = fft_filter(rng.normal(size=t.size), lambda fr: bp_gain(fr, 900, 4000, 1))
    env = np.clip(t / 0.03, 0, 1) * np.clip((dur - t) / 0.06, 0, 1)
    return (x + 0.08 * norm(breath)) * env


def _l94_brush(rng) -> np.ndarray:
    t = t_axis(0.14)
    return fft_filter(rng.normal(size=t.size), lambda f: hp_gain(f, 2500, 2)) * np.exp(-t / 0.035)


def _l94_woodblock(rng) -> np.ndarray:
    x = ring([1150, 2650], [0.035, 0.012], [1.0, 0.4], 0.15)
    x[:20] += rng.normal(size=20) * 0.5
    return x


def snd_l94_siren_song() -> None:
    """40 s seamless loop: the happy cartoon tune Level 94's siren poles play all day.

    An ORIGINAL little foxtrot in F major, 144 bpm, 24 bars of 4/4 (A, A', B, then round to A
    again): oom-pah tuba bass, a banjo strumming the off-beats, a clarinet lead (a xylophone in
    the B part), slide-whistle swoops, brushes and a woodblock. Then it is played "through" a
    tinny old horn loudspeaker: band-passed 400-3500 Hz with two horn resonances, softly
    overdriven, with record crackle and a slow warble, so it sounds old and a little wrong.
    Every part is placed circularly (notes ringing past the end wrap round to the start) and
    every effect is periodic, so the end flows straight into the start.
    Length is exactly 1 764 000 samples: client/SirenSongs.SONG_SAMPLES must match.
    """
    rng = rng_for(9440)
    beat = 60.0 / 144.0
    bars = 24
    n = int(round(bars * 4 * beat * SR))
    eighth = beat / 2

    # Chords: (tuba root, tuba fifth, banjo chord), MIDI notes.
    chords = {"F": (41, 48, (57, 60, 65)), "Bb": (46, 41, (58, 62, 65)),
              "C7": (48, 43, (58, 60, 64)), "G7": (43, 50, (59, 62, 65))}
    form = ["F", "F", "C7", "C7", "F", "Bb", "C7", "F",
            "F", "F", "C7", "C7", "F", "Bb", "C7", "F",
            "Bb", "Bb", "F", "F", "G7", "G7", "C7", "C7"]
    # Walking bass (four quarter notes) in the bars that lead into a new section.
    walks = {7: (41, 43, 45, 48), 15: (41, 43, 45, 48), 23: (48, 46, 45, 43)}
    # Lead melody per bar: (MIDI note, start, length) in eighth notes. F4=65 A4=69 C5=72 F5=77.
    melody = [
        [(72, 0, 1), (69, 1, 1), (72, 2, 1), (77, 3, 3), (76, 6, 1), (77, 7, 1)],
        [(79, 0, 2), (77, 2, 1), (74, 3, 1), (72, 4, 4)],
        [(70, 0, 1), (72, 1, 1), (74, 2, 1), (76, 3, 2), (79, 5, 1), (76, 6, 2)],
        [(72, 0, 3), (70, 3, 1), (67, 4, 2)],
        [(69, 0, 1), (72, 1, 1), (77, 2, 1), (81, 3, 3), (79, 6, 1), (77, 7, 1)],
        [(74, 0, 2), (77, 2, 1), (74, 3, 1), (70, 4, 3), (72, 7, 1)],
        [(74, 0, 1), (72, 1, 1), (70, 2, 1), (67, 3, 2), (76, 5, 1), (79, 6, 2)],
        [(77, 0, 3), (72, 3, 1), (65, 4, 2)],
        # A': same opening, a brighter second half and a "ta-da" ending.
        [(72, 0, 1), (69, 1, 1), (72, 2, 1), (77, 3, 3), (76, 6, 1), (77, 7, 1)],
        [(79, 0, 1), (81, 1, 1), (79, 2, 1), (77, 3, 1), (74, 4, 4)],
        [(70, 0, 1), (72, 1, 1), (74, 2, 1), (76, 3, 2), (79, 5, 1), (76, 6, 2)],
        [(72, 0, 2), (76, 2, 1), (79, 3, 1), (82, 4, 3), (81, 7, 1)],
        [(81, 0, 2), (77, 2, 1), (72, 3, 1), (69, 4, 2), (72, 6, 2)],
        [(70, 0, 1), (74, 1, 1), (77, 2, 1), (82, 3, 3), (81, 6, 1), (79, 7, 1)],
        [(79, 0, 2), (76, 2, 2), (72, 4, 2), (76, 6, 2)],
        [(77, 0, 4)],
        # B: bouncier, on the xylophone.
        [(77, 0, 1), (74, 1, 1), (77, 2, 1), (74, 3, 1), (70, 4, 2), (74, 6, 2)],
        [(77, 0, 3), (79, 3, 1), (77, 4, 4)],
        [(72, 0, 1), (69, 1, 1), (72, 2, 1), (69, 3, 1), (65, 4, 2), (69, 6, 2)],
        [(72, 0, 3), (74, 3, 1), (72, 4, 4)],
        [(71, 0, 1), (74, 1, 1), (79, 2, 1), (77, 3, 2), (74, 5, 1), (71, 6, 2)],
        [(69, 0, 2), (71, 2, 2), (74, 4, 2), (77, 6, 2)],
        [(76, 0, 1), (79, 1, 1), (76, 2, 1), (72, 3, 1), (70, 4, 2), (67, 6, 2)],
        [(72, 0, 2), (76, 2, 2), (79, 4, 2)],
    ]
    # Slide whistle swoops: (bar, start eighth, length in eighths, from Hz, to Hz).
    slides = [(3, 6, 2, 620, 1250), (7, 6, 2, 1400, 700), (15, 4, 2, 700, 1500), (15, 6, 2, 1500, 800),
              (23, 6, 2, 650, 1400)]

    bass = np.zeros(n)
    strum = np.zeros(n)
    lead = np.zeros(n)
    whistle = np.zeros(n)
    kit = np.zeros(n)
    sharp = 2 ** (12 / 1200)                     # the lead is 12 cents sharp: a little bit wrong
    for b, name in enumerate(form):
        root, fifth, chord = chords[name]
        t0 = b * 4 * beat
        if b in walks:
            for q, m in enumerate(walks[b]):
                add_at(bass, _l94_tuba(_l94_midi_hz(m), beat * 0.8), t0 + q * beat, circular=True)
        else:
            add_at(bass, _l94_tuba(_l94_midi_hz(root), beat * 0.9), t0, circular=True)
            add_at(bass, _l94_tuba(_l94_midi_hz(fifth), beat * 0.9), t0 + 2 * beat, circular=True)
        for q in (1, 3):
            add_at(strum, _l94_banjo(chord, beat * 0.45, rng), t0 + q * beat + rng.uniform(0, 0.006), circular=True)
            add_at(kit, _l94_brush(rng) * 0.8, t0 + q * beat, circular=True)
        if b % 2 == 1:
            add_at(kit, _l94_woodblock(rng), t0 + 3.5 * beat, circular=True)
        for m, at, ln in melody[b]:
            start = t0 + at * eighth + rng.uniform(-0.004, 0.006)
            f = _l94_midi_hz(m) * sharp
            note = _l94_xylophone(f * 2, ln * eighth) * 0.8 if b >= 16 else _l94_clarinet(f, ln * eighth * 0.92, rng)
            add_at(lead, note, start, circular=True)
    for b, at, ln, f0, f1 in slides:
        add_at(whistle, _l94_slide_whistle(f0, f1, ln * eighth, rng), b * 4 * beat + at * eighth, circular=True)

    x = (norm(lead) * 1.0 + norm(bass) * 0.85 + norm(strum) * 0.4 + norm(whistle) * 0.45 + norm(kit) * 0.22)
    # A little slapback off the hills, then the record's own crackle (it plays through the horn too).
    x = x + 0.18 * circ_conv(x, reverb_ir(rng, 0.6, 0.15, lp=3000, predelay=0.03))
    crackle = clicks(rng, np.sort(rng.uniform(0, n / SR - 0.05, int(n / SR * 12))), n / SR, 1200, 5000, amp_pow=3.0)
    hiss = periodic_noise(n, rng, lambda f: bp_gain(f, 1500, 6000, 1))
    x = norm(x) + 0.06 * norm(crackle[:n]) + 0.012 * hiss
    # Old horn loudspeaker: band-pass 400-3500 Hz with two horn resonances, softly overdriven.
    horn = lambda f: bp_gain(f, 400, 3500, 2) * (1 + 1.3 * np.exp(-((f - 1100) / 260) ** 2)
                                                 + 0.7 * np.exp(-((f - 2500) / 420) ** 2))
    x = fft_filter(x, horn, circular=True)
    x = dist(norm(x) * 0.8, 1.6)
    x = fft_filter(x, lambda f: lp_gain(f, 4500, 2) * hp_gain(f, 250, 1), circular=True)
    # Slow warble (0.5 Hz wow, 6 Hz flutter): whole cycles in 40 s, read circularly, so it loops.
    t = np.arange(n) / SR
    pos = t - 0.0035 / (2 * np.pi * 0.5) * np.cos(2 * np.pi * 0.5 * t) \
            - 0.0007 / (2 * np.pi * 6.0) * np.cos(2 * np.pi * 6.0 * t)
    idx = np.mod(pos * SR, n)
    x = np.interp(idx, np.arange(n + 1), np.concatenate([x, x[:1]]))
    path = SND / "level94" / "siren_song.ogg"
    save_ogg(x, path, peak=0.7, loop=True)
    y = sf.read(str(path))[0]
    step = np.abs(np.diff(y)).mean()
    print(f"siren_song: {len(y)} samples ({len(y) / SR:.3f} s); loop seam step {abs(y[-1] - y[0]):.4f} "
          f"vs typical step {step:.4f}")


L94_PROPS_TEXTURES = [tex_l94_siren, tex_l94_tv, tex_l94_vehicle]
L94_PROPS_SOUNDS = [snd_l94_siren_song]


# =============================================================================
# Level 94 "Motion" world (World slice): felt grass in three mown shades, toy
# dirt, smooth grey asphalt, painted-cardboard houses with oversized bricks,
# bright kids'-TV interiors, lollipop trees, water towers, road signs and the
# moulded clay door. A SUBTLE crafted look: soft, slightly over-saturated,
# handmade. Run: python tools/gen_assets.py tex_l94_
# =============================================================================

L94_GRASS = {"light": (134, 206, 72), "mid": (110, 184, 60), "dark": (90, 162, 52)}
L94_DIRT = (150, 100, 62)
L94_CREAM = (238, 226, 192)
L94_WALLPAPER = (242, 150, 176)


def _l94_felt(seed, col) -> np.ndarray:
    """Soft felt: fine fuzz, gentle mottling and a few lighter fibres. Seamless."""
    rng = rng_for(seed)
    img = fill(col, (S, S))
    img = add_lum(img, fnoise(rng, beta=0.8, kmin=20) * 5)
    img = add_lum(img, fnoise(rng, beta=2.4) * 4)
    img = add_lum(img, blur(rng.normal(0, 1, (S, S)), 0.7) * 6)
    fibres = blur((rng.random((S, S)) < 0.02) * 1.0, 0.6)
    return add_lum(img, fibres * 30, (1.0, 1.05, 0.75))


def _l94_dirt(seed) -> np.ndarray:
    """Warm toy dirt like modelling clay: soft lumps, a few pebbles."""
    rng = rng_for(seed)
    img = add_lum(fill(L94_DIRT, (S, S)), fnoise(rng, beta=2.2) * 7 + rng.normal(0, 3, (S, S)))
    lumps = blur(fnoise(rng, beta=1.0, kmin=6), 1.2)
    img = add_lum(img, lumps * 6, (1.0, 0.9, 0.8))
    pebbles = blur((rng.random((S, S)) < 0.006) * 1.0, 0.9)
    return add_lum(img, pebbles * 70, (1.0, 0.95, 0.85))


def _l94_fringed(base, top_img, edge_y, rng) -> np.ndarray:
    """base with top_img hanging over it down to a soft wavy line around edge_y (tiles in x)."""
    X, Y = grid()
    wave = edge_y + 4 * np.sin(X * 2 * np.pi / 64) + 3 * np.clip(fnoise(rng, (1, S), beta=0.8), -1.5, 1.5)
    img = lerp(base, top_img, (Y < wave).astype(float)[..., None])
    return add_lum(img, -18 * np.exp(-((Y - wave) / 2.0) ** 2) * (Y >= wave))


def tex_l94_grass() -> None:
    # The hills are built from steps of whole and half blocks. With dirt on the sides every step
    # showed a brown stripe and the hills looked like stepped pyramids; the reference hills are
    # smooth green mounds. So the sides are the same felt, a little darker (they face away from the
    # sun), with a faint darker seam along the bottom edge that reads as a mowing line.
    _, Y = grid()
    for i, (name, col) in enumerate(L94_GRASS.items()):
        felt = _l94_felt(9400 + i, col)
        save_png(felt, _blk(f"l94_grass_top_{name}"))
        side = add_lum(felt, -14 - 8 * np.exp(-((S - 1 - Y) / 6.0) ** 2))
        save_png(side, _blk(f"l94_grass_side_{name}"))
        save_png(side, _blk(f"l94_grass_slab_side_{name}"))


def tex_l94_ground() -> None:
    save_png(_l94_dirt(9430), _blk("l94_dirt"))
    # Tan footpath: packed dirt with small pebbles.
    rng = rng_for(9431)
    path = add_lum(fill((210, 176, 120), (S, S)), fnoise(rng, beta=2.0) * 6 + rng.normal(0, 3, (S, S)))
    path = add_lum(path, blur((rng.random((S, S)) < 0.01) * 1.0, 0.8) * -50)
    save_png(path, _blk("l94_path_top"))
    save_png(_l94_fringed(_l94_dirt(9432), path, 14, rng), _blk("l94_path_side"))
    save_png(_l94_fringed(_l94_dirt(9432), path, 64 + 8, rng), _blk("l94_path_slab_side"))
    # Smooth grey asphalt, no lines at all.
    rng = rng_for(9433)
    asphalt = add_lum(fill((108, 108, 112), (S, S)), fnoise(rng, beta=2.6) * 3 + rng.normal(0, 3.5, (S, S)))
    save_png(add_lum(asphalt, blur((rng.random((S, S)) < 0.01) * 1.0, 0.5) * 25), _blk("l94_asphalt"))
    # Light concrete curb: smooth, a hairline joint every half block.
    rng = rng_for(9434)
    X, Y = grid()
    curb = add_lum(fill((214, 212, 204), (S, S)), fnoise(rng, beta=2.4) * 3 + rng.normal(0, 2, (S, S)))
    save_png(add_lum(curb, -22 * smoothstep(1.2, 0.3, np.abs(wrapd(X, 64)))), _blk("l94_curb"))
    # Plaza paving: pale square stones with thin grout.
    rng = rng_for(9435)
    tone = np.kron(rng.normal(0, 5, (4, 4)), np.ones((32, 32)))
    pave = add_lum(fill((224, 210, 180), (S, S)), tone + fnoise(rng, beta=2.2) * 3 + rng.normal(0, 2, (S, S)))
    joint = np.minimum(np.abs(wrapd(X, 32)), np.abs(wrapd(Y, 32)))
    save_png(over(pave, np.array([168, 156, 136]), smoothstep(1.5, 0.5, joint)), _blk("l94_paving"))
    # Fountain basin: big cut stones, bevelled.
    rng = rng_for(9436)
    row = np.floor(Y / 32)
    bx = (X + 32 * (row % 2)) % S
    stone = add_lum(fill((180, 174, 162), (S, S)), np.kron(rng.normal(0, 6, (4, 2)), np.ones((32, 64)))
                    + fnoise(rng, beta=2.0) * 4 + rng.normal(0, 2, (S, S)))
    mortar = np.minimum(np.abs(wrapd(Y, 32)), np.abs(wrapd(bx, 64)))
    stone = add_lum(stone, 14 * smoothstep(4, 1.5, mortar) * ((Y % 32) < 16))
    save_png(over(stone, np.array([130, 124, 114]), smoothstep(1.6, 0.6, mortar)), _blk("l94_fountain_stone"))
    # The king's glossy painted plaster: near-white with a soft sheen.
    rng = rng_for(9437)
    statue = add_lum(fill((240, 234, 216), (S, S)), fnoise(rng, beta=3.0) * 3 + 6 * np.sin(X / S * 2 * np.pi))
    save_png(statue, _blk("l94_statue"))


def tex_l94_trees_towers() -> None:
    X, Y = grid()
    # Lollipop leaves: soft round bright-green puffs.
    rng = rng_for(9440)
    leaves = fill((118, 198, 62), (S, S))
    for _ in range(26):
        cx, cy, r = rng.uniform(0, S), rng.uniform(0, S), rng.uniform(10, 20)
        d = np.hypot(wrapd(X - cx, S), wrapd(Y - cy, S)) / r
        leaves = add_lum(leaves, 14 * np.clip(1 - d, 0, 1) ** 0.7 - 10 * np.exp(-((d - 1) / 0.12) ** 2), (0.9, 1.0, 0.6))
    save_png(add_lum(leaves, rng.normal(0, 2.5, (S, S))), _blk("l94_leaves"))
    # Trunk: brown, vertical grain.
    rng = rng_for(9441)
    save_png(add_lum(fill((122, 82, 50), (S, S)), fnoise(rng, beta=1.4, aniso=(10, 1), kmin=2) * 8), _blk("l94_trunk"))
    # Water tower steel.
    rng = rng_for(9442)
    leg = add_lum(fill((156, 160, 166), (S, S)), 12 * np.cos((X - 40) / S * 2 * np.pi) + rng.normal(0, 2, (S, S)))
    save_png(leg, _blk("l94_tower_leg"))
    tank = add_lum(fill((160, 166, 170), (S, S)), fnoise(rng, beta=1.6, aniso=(1, 8), kmin=2) * 4 + rng.normal(0, 2, (S, S)))
    tank = add_lum(tank, -20 * smoothstep(1.4, 0.4, np.abs(wrapd(X, 32))) + 10 * smoothstep(3, 1.4, np.abs(wrapd(X - 2, 32))))
    rivets = (np.hypot(wrapd(X - 4, 32), wrapd(Y - 8, 16)) < 1.6).astype(float)
    tank = add_lum(tank, blur(rivets, 0.5) * 30)
    save_png(add_lum(tank, -10 * (Y / S)), _blk("l94_tank"))
    roof = add_lum(fill((112, 116, 122), (S, S)), fnoise(rng, beta=2.0) * 4 - 16 * smoothstep(1.2, 0.3, np.abs(wrapd(X, 16))))
    save_png(roof, _blk("l94_tank_roof"))
    # Lattice bracing: a steel X between top and bottom bars, transparent between.
    bars = pil_mask(lambda d, k: (d.line((0, 0, 128 * k, 128 * k), fill=255, width=7 * k),
                                  d.line((0, 128 * k, 128 * k, 0), fill=255, width=7 * k),
                                  d.rectangle((0, 0, 128 * k, 8 * k), fill=255),
                                  d.rectangle((0, 120 * k, 128 * k, 128 * k), fill=255)))
    out = np.zeros((S, S, 4))
    out[..., :3] = leg
    out[..., 3] = np.where(bars >= 0.5, 255, 0)
    save_png(out, _blk("l94_tower_brace"))


def _l94_siding(seed) -> np.ndarray:
    """Cream painted-cardboard siding: soft lap boards, a faint corrugated grain, paper fibres."""
    rng = rng_for(seed)
    X, Y = grid()
    img = add_lum(fill(L94_CREAM, (S, S)), fnoise(rng, beta=1.2, aniso=(10, 1), kmin=3) * 2.5 + rng.normal(0, 1.8, (S, S)))
    v = (Y % 32) / 32
    img = img * (0.95 + 0.06 * v)[..., None]
    img = add_lum(img, -24 * smoothstep(1.8, 0.3, Y % 32))
    return add_lum(img, 2.0 * np.sin(X * 2 * np.pi / 8))


def _l94_wallpaper(seed) -> np.ndarray:
    """Bright pink nursery wallpaper with cream polka dots."""
    rng = rng_for(seed)
    X, Y = grid()
    img = add_lum(fill(L94_WALLPAPER, (S, S)), fnoise(rng, beta=2.2) * 2.5 + rng.normal(0, 1.5, (S, S)))
    dots = np.clip(5.5 - np.hypot(wrapd(X - 16, 32), wrapd(Y - 16, 32)), 0, 1)
    dots = np.maximum(dots, np.clip(4.0 - np.hypot(wrapd(X, 32), wrapd(Y, 32)), 0, 1))
    return over(img, np.array([255, 240, 214]), dots)


def _l94_window(surround, seed) -> np.ndarray:
    """Dark mullioned window (2 x 3 panes) in a thick white frame on the given surround."""
    rng = rng_for(seed)
    X, Y = grid()
    white = add_lum(fill((246, 244, 238), (S, S)), rng.normal(0, 1.5, (S, S)))
    img = over(surround, white, box((S, S), 14, 10, 114, 118))
    glass = add_lum(fill((32, 38, 56), (S, S)), np.exp(-((X - Y * 0.7 - 30) / 10) ** 2) * 16, (0.8, 0.9, 1.0))
    panes = np.zeros((S, S))
    for x0, x1 in ((24, 60), (68, 104)):
        for y0, y1 in ((20, 47), (53, 80), (86, 108)):
            panes = np.maximum(panes, box((S, S), x0, y0, x1, y1))
    img = over(img, glass, panes)
    return add_lum(img, -30 * box((S, S), 10, 114, 118, 120))          # sill shadow


def tex_l94_house_walls() -> None:
    X, Y = grid()
    siding = _l94_siding(9450)
    save_png(siding, _blk("l94_siding"))
    paper = _l94_wallpaper(9451)
    save_png(paper, _blk("l94_wallpaper"))
    rng = rng_for(9452)
    skirting = add_lum(fill((248, 246, 240), (S, S)), rng.normal(0, 1.5, (S, S)) - 18 * smoothstep(1.5, 0.3, np.abs(Y - 100)))
    save_png(lerp(paper, skirting, (Y > 100).astype(float)[..., None]), _blk("l94_wallpaper_base"))
    save_png(_l94_window(siding, 9453), _blk("l94_window"))
    save_png(_l94_window(paper, 9454), _blk("l94_window_inside"))
    # Oversized bricks: two to a row, four rows, warm red-orange with soft cream mortar.
    rng = rng_for(9455)
    row = np.floor(Y / 32)
    bx = (X + 32 * (row % 2)) % S
    tone = rng.normal(0, 8, (4, 2))[row.astype(int) % 4, (bx // 64).astype(int) % 2]
    brick = fill((190, 84, 58), (S, S)) + tone[..., None] * np.array([1.0, 0.6, 0.5])
    brick = add_lum(brick, fnoise(rng, beta=2.0) * 4 + rng.normal(0, 2.5, (S, S)))
    mortar = np.minimum(np.abs(wrapd(Y, 32)), np.abs(wrapd(bx, 64)))
    brick = add_lum(brick, 12 * smoothstep(5, 2, mortar) * ((Y % 32) < 16) - 10 * smoothstep(5, 2, mortar) * ((Y % 32) >= 16))
    save_png(over(brick, np.array([226, 214, 190]), smoothstep(2.6, 1.4, mortar)), _blk("l94_brick"))
    rng = rng_for(9456)
    trim = add_lum(fill((246, 244, 238), (S, S)), fnoise(rng, beta=1.4, aniso=(1, 10), kmin=2) * 2 + rng.normal(0, 1.2, (S, S)))
    save_png(trim, _blk("l94_trim"))
    for name, col, seed in (("red", (184, 60, 46), 9457), ("brown", (128, 82, 52), 9458)):
        rng = rng_for(seed)
        r = np.floor(Y / 16).astype(int)
        sx = (X + 16 * (r % 2)) % S
        tone = rng.normal(0, 6, (8, 4))[r % 8, (sx // 32).astype(int) % 4]
        img = fill(col, (S, S)) + tone[..., None]
        img = add_lum(img, rng.normal(0, 3, (S, S)))
        img = img * (0.86 + 0.18 * ((Y % 16) / 16))[..., None]
        img = add_lum(img, -26 * smoothstep(1.6, 0.3, Y % 16) - 18 * smoothstep(1.2, 0.3, np.abs(wrapd(sx, 32))) * ((Y % 16) > 3))
        save_png(img, _blk(f"l94_roof_{name}"))
    save_png(fill((0, 0, 0), (S, S)), _blk("l94_dark_void"))


def tex_l94_doors() -> None:
    """The toy house door (cheerful red, panels, round window) and the moulded clay door."""
    shp = (2 * S, S)
    X, Y = grid(shp)
    rng = rng_for(9460)
    img = add_lum(fill((206, 54, 50), shp), fnoise(rng, shp, beta=1.6, aniso=(1, 8), kmin=2) * 3 + rng.normal(0, 1.5, shp))
    img = over(img, np.array([246, 244, 238]), 1 - box(shp, 8, 8, 120, 248))          # white frame
    for x0, x1 in ((22, 58), (70, 106)):
        for y0, y1 in ((132, 236),):
            outer, inner = box(shp, x0, y0, x1, y1), box(shp, x0 + 5, y0 + 5, x1 - 5, y1 - 5)
            img = add_lum(img, (outer - inner) * np.where(Y < y0 + 50, 18, -22) - 8 * inner)
    window = supersample(shp, lambda x, y: np.hypot(x - 64, y - 64) < 30)
    rim = supersample(shp, lambda x, y: np.hypot(x - 64, y - 64) < 36)
    img = over(img, np.array([246, 244, 238]), rim)
    img = over(img, np.array([34, 40, 58]), window)
    img = add_lum(img, 20 * window * np.exp(-((X - Y * 0.6 - 30) / 8) ** 2))
    img = over(img, np.array([238, 196, 80]), supersample(shp, lambda x, y: np.hypot(x - 100, y - 140) < 6))
    save_png(img[:S], _blk("l94_house_door_top"))
    save_png(img[S:], _blk("l94_house_door_bottom"))

    # Clay: warm terracotta with ochre swirls, lumpy, thumb-printed, rough edges, like plasticine.
    rng = rng_for(9461)
    swirl = fnoise(rng, shp, beta=2.8)
    img = lerp(np.array([196, 102, 62], float), np.array([216, 150, 72], float), smoothstep(-0.4, 0.8, swirl)[..., None])
    lumps = blur(fnoise(rng, shp, beta=1.6, kmin=3), 2.0)
    gy, gx = np.gradient(lumps)
    img = add_lum(img, (-gx * 0.6 - gy * 0.8) * 60 + lumps * 6)                       # soft light from the top left
    for _ in range(9):                                                                # thumb prints: little ridged ovals
        cx, cy = rng.uniform(16, S - 16), rng.uniform(16, 2 * S - 16)
        ang = rng.uniform(0, np.pi)
        u = (X - cx) * np.cos(ang) + (Y - cy) * np.sin(ang)
        v = -(X - cx) * np.sin(ang) + (Y - cy) * np.cos(ang)
        r = np.hypot(u / 1.0, v / 1.35)
        img = add_lum(img, np.where(r < 11, 7 * np.sin(r * 2.2) - 4, 0))
    for x0, x1, y0, y1 in ((20, 58, 24, 112), (70, 108, 24, 112), (20, 58, 136, 236), (70, 108, 136, 236)):
        wob = 2.5 * np.sin(Y / 9 + x0) + 2 * np.sin(X / 7 + y0)
        inner = smoothstep(0, 3, np.minimum(np.minimum(X - x0, x1 - X), np.minimum(Y - y0, y1 - Y)) + wob)
        img = add_lum(img, -14 * inner * (1 - inner) * 4 - 8 * inner)                  # pressed-in panels
    edge = np.minimum(np.minimum(X, S - X), np.minimum(Y, 2 * S - Y)) + 2 * np.sin(Y / 6) + 2 * np.sin(X / 5)
    img = add_lum(img, -30 * smoothstep(8, 1, edge))
    knob = supersample(shp, lambda x, y: np.hypot(x - 100, (y - 140) * 1.1) < 8)
    img = over(img, np.array([226, 168, 70]), knob)
    img = add_lum(img, 30 * supersample(shp, lambda x, y: np.hypot(x - 97, y - 137) < 3))
    save_png(img[:S], _blk("l94_clay_door_top"))
    save_png(img[S:], _blk("l94_clay_door_bottom"))


def tex_l94_interior() -> None:
    X, Y = grid()
    # Bright turquoise vinyl floor in big tiles, sprinkled with confetti specks.
    rng = rng_for(9470)
    floor = add_lum(fill((62, 188, 190), (S, S)), np.kron(rng.normal(0, 4, (2, 2)), np.ones((64, 64))) + rng.normal(0, 1.5, (S, S)))
    floor = add_lum(floor, 16 * smoothstep(1.4, 0.4, np.minimum(np.abs(wrapd(X, 64)), np.abs(wrapd(Y, 64)))))
    for col in ((250, 220, 80), (250, 120, 150), (255, 255, 255)):
        specks = blur((rng.random((S, S)) < 0.004) * 1.0, 0.7)
        floor = over(floor, np.array(col), np.clip(specks * 3, 0, 1))
    save_png(floor, _blk("l94_floor"))

    # Toy-road play rug, 3 x 3 blocks (384 px): green with a grey road loop, a crossing road,
    # dashed white lines, a blue pond and little houses. Split into l94_rug_0..8 (row * 3 + column).
    R = 3 * S
    shp = (R, R)
    RX, RY = grid(shp)
    rng = rng_for(9471)
    rug = add_lum(fill((104, 182, 84), shp), rng.normal(0, 3, shp) + fnoise(rng, shp, beta=1.0, kmin=30) * 4)
    border = np.minimum(np.minimum(RX, R - RX), np.minimum(RY, R - RY))
    rug = over(rug, np.array([236, 70, 60]), smoothstep(18.5, 17.5, border))
    rug = over(rug, np.array([250, 214, 70]), smoothstep(9.5, 8.5, border))
    loop = np.abs(np.maximum(np.abs(RX - R / 2) - 70, 0) ** 2 + np.maximum(np.abs(RY - R / 2) - 70, 0) ** 2) ** 0.5
    ring_d = np.abs(np.where((np.abs(RX - R / 2) > 70) | (np.abs(RY - R / 2) > 70), loop, 0) - 50)
    ring_d = np.where((np.abs(RX - R / 2) <= 70) & (np.abs(RY - R / 2) <= 70),
                      np.minimum(120 - np.abs(RX - R / 2), 120 - np.abs(RY - R / 2)), ring_d)
    cross = np.abs(RY - 300)
    road_d = np.minimum(np.abs(ring_d), np.where(RX > 30, cross, 1e9))
    road = smoothstep(17, 15, road_d)
    rug = over(rug, np.array([96, 96, 104]), road)
    dash_x = ((RX // 16) % 2 == 0) & (cross < 2)
    rug = over(rug, np.array([250, 250, 246]), (dash_x & (RX > 30)).astype(float))
    pond = supersample(shp, lambda x, y: np.hypot((x - 192) / 1.3, y - 192) < 30)
    rug = over(rug, np.array([70, 150, 230]), pond)
    for hx, hy, col in ((60, 60, (250, 230, 190)), (320, 50, (250, 230, 190)), (330, 200, (250, 200, 80)),
                        (50, 200, (240, 140, 170)), (110, 340, (250, 230, 190))):
        rug = over(rug, np.array(col), box(shp, hx - 12, hy - 8, hx + 12, hy + 14))
        rug = over(rug, np.array([180, 60, 46]), pil_mask(lambda d, k, hx=hx, hy=hy: d.polygon(
            [((hx - 15) * k, (hy - 8) * k), (hx * k, (hy - 20) * k), ((hx + 15) * k, (hy - 8) * k)], fill=255), shp))
    for i in range(9):
        r, c = divmod(i, 3)
        save_png(rug[r * S:(r + 1) * S, c * S:(c + 1) * S], _blk(f"l94_rug_{i}"))

    # Armchair: bright orange velvety fabric with buttons; the cushion a lighter tangerine.
    rng = rng_for(9472)
    fabric = add_lum(fill((232, 118, 42), (S, S)), fnoise(rng, beta=1.0, kmin=12) * 4 + fnoise(rng, beta=2.4) * 5)
    buttons = np.hypot(wrapd(X - 32, 64), wrapd(Y - 32, 64))
    fabric = add_lum(fabric, -26 * smoothstep(6, 1, buttons) + 10 * smoothstep(14, 8, buttons) * (buttons > 6))
    save_png(fabric, _blk("l94_armchair"))
    cushion = add_lum(fill((248, 162, 72), (S, S)), fnoise(rng, beta=1.0, kmin=12) * 4)
    edge = np.minimum(np.minimum(X, S - X), np.minimum(Y, S - Y))
    save_png(add_lum(cushion, -24 * smoothstep(6, 1, edge) + 12 * smoothstep(14, 6, edge) * (edge > 6)), _blk("l94_armchair_cushion"))
    # The odd lamp: a sunny yellow shade with red spots, a teal base, a warm bulb.
    rng = rng_for(9473)
    shade = add_lum(fill((250, 214, 82), (S, S)), fnoise(rng, beta=2.0) * 3)
    spots = np.clip(9 - np.hypot(wrapd(X - 20, 48), wrapd(Y - 20 - 24 * ((X // 48) % 2), 48)), 0, 1)
    save_png(over(shade, np.array([226, 64, 60]), spots), _blk("l94_lamp_shade"))
    save_png(add_lum(fill((58, 160, 172), (S, S)), 14 * np.cos((X - 40) / S * 2 * np.pi) + rng.normal(0, 1.5, (S, S))),
             _blk("l94_lamp_base"))
    save_png(add_lum(fill((255, 244, 210), (S, S)), fnoise(rng, beta=3.0) * 2), _blk("l94_lamp_bulb"))


def tex_l94_road_signs() -> None:
    """STOP (red octagon), NO OUTLET (yellow diamond) and the grey octagonal back of the STOP sign."""
    rng = rng_for(9480)
    c = 36
    octagon = [(2 + c, 2), (126 - c, 2), (126, 2 + c), (126, 126 - c), (126 - c, 126), (2 + c, 126), (2, 126 - c), (2, 2 + c)]
    inner = [(x + (6 if x < 64 else -6) * (1 if x in (2, 126) else 0.42), y + (6 if y < 64 else -6) * (1 if y in (2, 126) else 0.42))
             for x, y in octagon]
    m = pil_mask(lambda d, k: d.polygon([(x * k, y * k) for x, y in octagon], fill=255))
    ring = m - pil_mask(lambda d, k: d.polygon([(x * k, y * k) for x, y in inner], fill=255))
    img = fill((200, 34, 36), (S, S))
    img = over(img, np.array([248, 248, 244]), np.clip(ring, 0, 1))
    img = over(img, np.array([248, 248, 244]), _text("STOP", 64, 66, 44, maxw=100))
    _finish_sign(img, m, rng, "stop")
    img, m = _sign_base(rng, (246, 206, 40), (24, 22, 20), shape="diamond")
    img = over(img, np.array([24, 22, 20]), _text("NO", 64, 52, 26, maxw=60))
    img = over(img, np.array([24, 22, 20]), _text("OUTLET", 64, 80, 22, maxw=78))
    _finish_sign(img, m, rng, "no_outlet")
    back = _weather(_galvanised(rng, (150, 152, 154)), rng, 0.2, 4)
    out = np.zeros((S, S, 4))
    out[..., :3] = back
    out[..., 3] = np.where(pil_mask(lambda d, k: d.polygon([(x * k, y * k) for x, y in octagon], fill=255)) >= 0.5, 255, 0)
    save_png(out, _blk("sign_back_octagon"))


L94_WORLD_TEXTURES = [tex_l94_grass, tex_l94_ground, tex_l94_trees_towers, tex_l94_house_walls, tex_l94_doors,
                      tex_l94_interior, tex_l94_road_signs]


# =============================================================================
# Level 94 castle (Castle slice): the see-through glass mountain and its pale
# opal ramp, the cartoon-white castle with red roofs, and the funhouse inside.
# Outside keeps the toy look; inside it fades into photographic, slightly grimy
# play-centre surfaces ("realistic but wrong"). Run: python tools/gen_assets.py tex_l94c_
# =============================================================================

def _l94c_tiles(rng, cols, n, grout=(70, 70, 74), gw=1.5, gloss=6.0) -> np.ndarray:
    """n x n glossy vinyl tiles per block, colours picked as a checker from cols, dark grout, scuffs."""
    X, Y = grid()
    t = S / n
    idx = ((X // t).astype(int) + (Y // t).astype(int)) % len(cols)
    img = np.asarray(cols, float)[idx]
    img = add_lum(img, fnoise(rng, beta=2.6) * 5 + rng.normal(0, 1.5, (S, S)))
    img = add_lum(img, smoothstep(1.2, 2.2, fnoise(rng, beta=1.8, aniso=(6, 1))) * gloss)       # wax sheen
    scuffs = smoothstep(1.5, 2.4, fnoise(rng, beta=1.2, aniso=(1, 5), kmin=4))
    img = over(img, np.array([60, 58, 60]), scuffs * 0.35)
    gx = np.minimum(X % t, t - X % t)
    gy = np.minimum(Y % t, t - Y % t)
    g = smoothstep(gw + 0.8, gw - 0.2, np.minimum(gx, gy))
    return over(img, np.array(grout), g * 0.9)


def _l94c_plastic(rng, col, ribs=0, gloss=22.0) -> np.ndarray:
    """Moulded glossy plastic: smooth colour, soft highlight streaks, optional ribs every S/ribs px."""
    X, Y = grid()
    img = add_lum(fill(col, (S, S)), fnoise(rng, beta=3.0) * 4 + rng.normal(0, 0.8, (S, S)))
    img = add_lum(img, smoothstep(0.9, 2.0, fnoise(rng, beta=2.2, aniso=(1, 8))) * gloss)
    if ribs:
        p = S / ribs
        u = (Y % p) / p
        img = add_lum(img, 18 * np.cos(u * 2 * np.pi) - 6)
    return add_lum(img, -smoothstep(1.4, 2.4, fnoise(rng, beta=1.4, kmin=4)) * 18)   # grubby hand marks


def _l94c_paper_sheet(img, rng, x0, y0, w, h, angle, sketch=True) -> np.ndarray:
    """A sheet of slightly yellowed paper pinned on img (rotated rectangle), with pencil sketches."""
    ca, sa = np.cos(angle), np.sin(angle)
    cx, cy = x0 + w / 2, y0 + h / 2

    def inside(x, y):
        u = (x - cx) * ca + (y - cy) * sa
        v = -(x - cx) * sa + (y - cy) * ca
        return (np.abs(u) < w / 2) & (np.abs(v) < h / 2)
    m = supersample((S, S), inside)
    shadow = blur(m, 1.5)
    img = add_lum(img, -26 * np.clip(shadow - m, 0, 1))
    paper = add_lum(fill((232, 226, 208), (S, S)), fnoise(rng, beta=2.0) * 3)
    img = over(img, paper, m)
    if sketch:
        strokes = []
        for _ in range(int(rng.integers(3, 6))):
            px, py = cx + rng.uniform(-w * 0.3, w * 0.3), cy + rng.uniform(-h * 0.3, h * 0.3)
            r = rng.uniform(3, min(w, h) * 0.22)
            n = int(rng.integers(5, 9))
            strokes.append([(px + r * np.cos(a + angle) * rng.uniform(0.8, 1.2), py + r * np.sin(a + angle) * rng.uniform(0.8, 1.2))
                            for a in np.linspace(0, 2 * np.pi * rng.uniform(0.7, 1.0), n)])
        lines = marker(fill((0, 0, 0), (S, S)), rng, strokes, width=1.2, color=(255, 255, 255), alpha=1.0)[..., 0] / 255
        img = over(img, np.array([96, 94, 100]), lines * m * 0.8)
    pin = blob((S, S), cx, y0 + 3, 1.6, 1.6)
    return over(img, np.array(rng.choice([[200, 40, 40], [40, 90, 200], [240, 200, 40]])), np.clip(pin * 1.4, 0, 1))


def tex_l94c_mountain() -> None:
    """l94_glass_mountain: faint blue-white glass with soft cloud/sky reflections (translucent RGBA).
    l94_opal_path: pale pearly paving, 2x2 slabs per block with a faint opal colour play."""
    rng = rng_for(9440)
    X, Y = grid()
    cloud = smoothstep(-0.2, 1.8, fnoise(rng, beta=2.8))
    rgb = lerp(np.array([176, 204, 236]), np.array([246, 250, 255]), cloud[..., None])
    rgb = add_lum(rgb, fnoise(rng, beta=1.4, aniso=(10, 1), kmin=3) * 5)                 # faint ripples
    alpha = 58 + 70 * cloud + 10 * smoothstep(0.8, 2.0, fnoise(rng, beta=1.6, aniso=(8, 1)))
    save_png(np.concatenate([rgb, alpha[..., None]], axis=2), _blk("l94_glass_mountain"))

    img = fill((228, 226, 232), (S, S))
    hue = np.stack([fnoise(rng, beta=3.2) for _ in range(3)], axis=-1) * 5                 # pink/green/blue sheen
    img = img + hue + (fnoise(rng, beta=2.0) * 3)[..., None]
    img = add_lum(img, rng.normal(0, 1.2, (S, S)))
    t = S / 2
    gx = np.minimum(X % t, t - X % t)
    gy = np.minimum(Y % t, t - Y % t)
    img = add_lum(img, -26 * smoothstep(2.2, 0.6, np.minimum(gx, gy)) + 8 * smoothstep(3.5, 2.2, np.minimum(gx, gy)) * (np.minimum(gx, gy) > 2.2))
    save_png(img, _blk("l94_opal_path"))


def tex_l94c_exterior() -> None:
    """Cartoon outside: l94_castle_wall (smooth white plaster), l94_castle_window (narrow arched slit),
    l94_castle_roof (bright red round-ended tiles)."""
    rng = rng_for(9441)
    X, Y = grid()
    wall = _l94_paint(rng, (240, 238, 232), grain=3.0)
    wall = add_lum(wall, -smoothstep(1.6, 2.6, fnoise(rng, beta=1.6, aniso=(1, 6))) * 8)    # faint rain marks
    save_png(wall, _blk("l94_castle_wall"))

    slit = supersample((S, S), lambda x, y: ((np.abs(x - 64) < 13) & (y > 30) & (y < 112))
                       | (np.hypot(x - 64, y - 30) < 13))
    frame = supersample((S, S), lambda x, y: ((np.abs(x - 64) < 19) & (y > 30) & (y < 118))
                        | (np.hypot(x - 64, y - 30) < 19))
    win = over(wall, np.array([206, 200, 190]), frame)                                     # stone surround
    glass = lerp(np.array([70, 92, 128]), np.array([30, 38, 58]), smoothstep(30, 110, Y)[..., None])
    glass = add_lum(glass, 30 * np.exp(-((X - Y * 0.3 - 40) / 4) ** 2))                   # glint
    win = over(win, glass, slit)
    save_png(win, _blk("l94_castle_window"))

    roof = fill((204, 44, 38), (S, S))
    row = 16
    yy = Y % row
    shift = ((Y // row) % 2) * 8
    xx = (X + shift) % 16
    curve = np.hypot(xx - 8, np.maximum(yy - 8, 0) * 1.0)
    roof = add_lum(roof, -38 * smoothstep(6.0, 8.0, curve))                                # rounded tile ends
    roof = add_lum(roof, 22 * (1 - yy / row) - 8)
    roof = add_lum(roof, fnoise(rng, beta=2.4) * 5)
    save_png(roof, _blk("l94_castle_roof"))


def tex_l94c_stone_light() -> None:
    """l94_castle_stone: real grey ashlar (2 courses per block), l94_red_carpet: worn velvet pile,
    l94_throne_light: pure white, l94_castle_lamp: bright square ceiling light in a metal frame."""
    rng = rng_for(9442)
    X, Y = grid()
    st = _smooth_concrete(rng, (132, 130, 126), mottle=7.0, fine=2.0)
    st = _pores(st, rng, density=0.008)
    course = (Y // 64).astype(int)
    xs = (X + course * 48) % 96
    joint = np.minimum(np.minimum(Y % 64, 64 - Y % 64), np.minimum(xs, 96 - xs))
    blockt = np.floor((X + course * 48) / 96) + course * 7
    st = add_lum(st, (np.sin(blockt * 12.9898) * 43758.5453 % 1 - 0.5) * 10)
    st = over(st, np.array([86, 84, 82]), smoothstep(2.4, 0.8, joint))
    save_png(st, _blk("l94_castle_stone"))

    carpet = add_lum(fill((138, 18, 26), (S, S)), rng.normal(0, 5, (S, S)) + fnoise(rng, beta=2.0) * 6)
    wear = smoothstep(0.6, 2.0, fnoise(rng, beta=2.6, aniso=(1, 3)))
    carpet = over(carpet, np.array([110, 40, 44]), wear * 0.5)
    save_png(carpet, _blk("l94_red_carpet"))

    save_png(add_lum(fill((255, 255, 255), (S, S)), -np.abs(fnoise(rng, beta=3.0)) * 1.5), _blk("l94_throne_light"))

    lamp = _galvanised(rng, (176, 178, 182))
    core = box((S, S), 10, 10, S - 10, S - 10, soft=1.0)
    glow = lerp(np.array([255, 255, 250]), np.array([236, 240, 246]), smoothstep(20, 54, np.maximum(np.abs(X - 64), np.abs(Y - 64)))[..., None])
    lamp = over(lamp, glow, core)
    save_png(lamp, _blk("l94_castle_lamp"))


def tex_l94c_floors() -> None:
    """Funhouse floors (photo): l94_checker_floor blue/yellow vinyl, l94_stripe_path red/white bands
    across the path, l94_star_tile, l94_disc_floor, l94_ring_floor, l94_play_mat foam puzzle tiles,
    l94_play_carpet (upper-floor carpet with confetti flecks)."""
    rng = rng_for(9443)
    X, Y = grid()
    save_png(_l94c_tiles(rng, [(40, 66, 156), (230, 182, 44)], 2), _blk("l94_checker_floor"))

    band = ((Y // 32) % 2).astype(int)
    stripes = np.asarray([(196, 34, 42), (236, 230, 216)], float)[band]
    stripes = add_lum(stripes, fnoise(rng, beta=2.6) * 5 + rng.normal(0, 1.5, (S, S)))
    stripes = add_lum(stripes, smoothstep(1.2, 2.2, fnoise(rng, beta=1.8, aniso=(6, 1))) * 6)
    stripes = over(stripes, np.array([70, 64, 64]), smoothstep(1.5, 2.4, fnoise(rng, beta=1.2, kmin=4)) * 0.3)
    save_png(stripes, _blk("l94_stripe_path"))

    disc = _l94c_tiles(rng, [(232, 170, 44)], 1, grout=(150, 110, 40), gw=0.6)
    save_png(disc, _blk("l94_disc_floor"))
    save_png(_l94c_tiles(rng, [(46, 146, 76)], 1, grout=(30, 90, 50), gw=0.6), _blk("l94_ring_floor"))
    points = [(64 + (50 if i % 2 == 0 else 20) * np.cos(-np.pi / 2 + i * np.pi / 5),
               66 + (50 if i % 2 == 0 else 20) * np.sin(-np.pi / 2 + i * np.pi / 5)) for i in range(10)]
    star = pil_mask(lambda d, k: d.polygon([(x * k, y * k) for x, y in points], fill=255))
    tile = over(disc, np.array([200, 36, 44]), star)
    save_png(tile, _blk("l94_star_tile"))

    mat_cols = [(208, 50, 46), (44, 96, 196), (50, 156, 74), (236, 196, 50)]
    q = ((X // 64).astype(int) + 2 * (Y // 64).astype(int)) % 4
    mat = np.asarray(mat_cols, float)[q]
    mat = add_lum(mat, rng.normal(0, 3.5, (S, S)) + fnoise(rng, beta=2.0) * 4)              # foam texture
    gx = np.minimum(X % 64, 64 - X % 64)
    gy = np.minimum(Y % 64, 64 - Y % 64)
    nub = (np.hypot(gx, (Y % 64) - 32) < 7) | (np.hypot((X % 64) - 32, gy) < 7)
    seam = (np.minimum(gx, gy) < 1.2) & ~nub
    mat = add_lum(mat, -45 * seam)
    save_png(mat, _blk("l94_play_mat"))

    carpet = add_lum(fill((70, 80, 108), (S, S)), rng.normal(0, 6, (S, S)) + fnoise(rng, beta=2.2) * 5)
    for col in ((220, 60, 60), (240, 200, 60), (70, 170, 90), (230, 230, 230)):
        fleck = blur((rng.random((S, S)) < 0.004) * 1.0, 0.5)
        carpet = over(carpet, np.array(col), np.clip(fleck * 3, 0, 1))
    save_png(carpet, _blk("l94_play_carpet"))


def tex_l94c_walls() -> None:
    """l94_play_wall (scuffed teal paint), l94_play_ceiling (stained acoustic tiles), l94_play_pillar
    (red with yellow bands) and its porthole version, l94_play_pipe(+_end) green painted duct."""
    rng = rng_for(9444)
    X, Y = grid()
    wall = _smooth_concrete(rng, (120, 184, 198), mottle=3.0, fine=0.8)
    smudge = smoothstep(1.2, 2.4, fnoise(rng, beta=1.6, kmin=3))
    wall = over(wall, np.array([90, 120, 126]), smudge * 0.35)
    chips = blur((rng.random((S, S)) < 0.0015) * 1.0, 0.8)
    wall = over(wall, np.array([210, 210, 204]), np.clip(chips * 4, 0, 1))
    save_png(wall, _blk("l94_play_wall"))

    ceil = _smooth_concrete(rng, (214, 210, 200), mottle=3.0, fine=1.0)
    ceil = add_lum(ceil, -blur((rng.random((S, S)) < 0.05) * 1.0, 0.5) * 30)            # pinholes
    t = 64
    g = np.minimum(np.minimum(X % t, t - X % t), np.minimum(Y % t, t - Y % t))
    ceil = over(ceil, np.array([120, 118, 112]), smoothstep(1.8, 0.4, g))
    stain = smoothstep(1.6, 2.4, fnoise(rng, beta=2.8))
    ceil = over(ceil, np.array([170, 140, 90]), stain * 0.5)
    save_png(ceil, _blk("l94_play_ceiling"))

    band = np.where((X % 64 > 22) & (X % 64 < 42), 1, 0)
    pil = np.asarray([(198, 34, 40), (238, 192, 44)], float)[band]
    pil = add_lum(pil, fnoise(rng, beta=2.4) * 4 + rng.normal(0, 1.2, (S, S)))
    pil = add_lum(pil, smoothstep(1.4, 2.4, fnoise(rng, beta=1.2, aniso=(1, 6))) * 10)    # varnish sheen
    pil = over(pil, np.array([90, 70, 60]), smoothstep(1.6, 2.6, fnoise(rng, beta=1.4, kmin=4)) * 0.4)
    save_png(pil, _blk("l94_play_pillar"))
    r = np.hypot(X - 64, Y - 64)
    port = over(pil, np.array([30, 30, 34]), smoothstep(38, 36, r))
    port = over(port, np.array([236, 232, 222]), smoothstep(33, 31, r))
    port = over(port, np.array([200, 40, 46]), smoothstep(22, 20, r))
    port = add_lum(port, 40 * np.exp(-((X - 56) ** 2 + (Y - 54) ** 2) / 40) * (r < 21))
    save_png(port, _blk("l94_play_pillar_porthole"))

    pipe = _smooth_concrete(rng, (64, 158, 66), mottle=2.5, fine=0.8)
    pipe = pipe * _round_shade(Y, 24, 104, outside=None)[..., None]
    flange = np.minimum(X, S - X) < 6
    pipe = add_lum(pipe, -26 * flange)
    save_png(pipe, _blk("l94_play_pipe"))
    end = _smooth_concrete(rng, (54, 140, 58), mottle=2.5, fine=0.8)
    end = add_lum(end, -30 * smoothstep(30, 24, np.hypot(X - 64, Y - 64)))
    save_png(end, _blk("l94_play_pipe_end"))


def tex_l94c_play() -> None:
    """l94_ball_pit (packed glossy balls), l94_climbing_frame (padded bars round a rope net, cutout),
    l94_slide (yellow plastic), l94_play_tube (ribbed blue plastic)."""
    rng = rng_for(9445)
    X, Y = grid()
    img = fill((36, 36, 44), (S, S))
    depth = np.full((S, S), -1e9)
    cols = [(214, 40, 40), (240, 200, 40), (40, 100, 210), (50, 170, 80), (240, 120, 30), (180, 60, 170)]
    for _ in range(170):
        cx, cy = rng.uniform(0, S, 2)
        rr = rng.uniform(9, 12)
        z = rng.uniform(0, 1)
        dx = wrapd(X - cx, S)
        dy = wrapd(Y - cy, S)
        d = np.hypot(dx, dy) / rr
        hit = (d < 1) & (z > depth)
        if not hit.any():
            continue
        nz = np.sqrt(np.clip(1 - d ** 2, 0, 1))
        light = 0.45 + 0.55 * np.clip((-dx * 0.4 - dy * 0.5) / rr + nz * 0.8, 0, 1)
        c = np.asarray(cols[int(rng.integers(len(cols)))], float)
        shade = c * light[..., None] + 90 * np.exp(-(((dx + rr * 0.35) ** 2 + (dy + rr * 0.4) ** 2) / (rr * 0.18) ** 2))[..., None]
        img = np.where(hit[..., None], shade, img)
        depth = np.where(hit, z, depth)
    save_png(img, _blk("l94_ball_pit"))

    rgba = np.zeros((S, S, 4))
    net_u = np.abs(wrapd(X + Y, 32))
    net_v = np.abs(wrapd(X - Y, 32))
    net = (np.minimum(net_u, net_v) < 2.0)
    rope = add_lum(fill((222, 214, 190), (S, S)), rng.normal(0, 8, (S, S)))
    rgba[..., :3] = rope
    rgba[..., 3] = np.where(net, 255, 0)
    edge = np.minimum(np.minimum(X, S - X), np.minimum(Y, S - Y))
    bar = edge < 12
    bar_col = np.where((np.minimum(X, S - X) < 12)[..., None], np.array([214, 44, 44.]), np.array([40, 96, 206.]))
    pad = bar_col * (0.75 + 0.25 * np.sin(np.clip(edge / 12, 0, 1) * np.pi))[..., None]
    rgba[..., :3] = np.where(bar[..., None], pad, rgba[..., :3])
    rgba[..., 3] = np.where(bar, 255, rgba[..., 3])
    save_png(rgba, _blk("l94_climbing_frame"))

    save_png(_l94c_plastic(rng, (238, 194, 42)), _blk("l94_slide"))
    save_png(_l94c_plastic(rng, (44, 110, 204), ribs=8), _blk("l94_play_tube"))


def tex_l94c_studio() -> None:
    """Animation studio: l94_studio_wood, l94_desk_paper (drawing board with a sketch), l94_sketch_wall
    (sheets pinned on the play wall), l94_studio_table_top (green model-board with grey streets),
    l94_studio_table_side, and the miniature town: l94_mini_house_front/_side, l94_mini_roof,
    l94_mini_leaves, l94_mini_trunk, l94_mini_stone, l94_mini_water."""
    rng = rng_for(9446)
    X, Y = grid()
    wood = _wood(rng, np.array([196, 160, 112]), horizontal=True)
    save_png(wood, _blk("l94_studio_wood"))
    save_png(_l94c_paper_sheet(wood, rng, 14, 12, 100, 104, 0.04), _blk("l94_desk_paper"))

    wall = np.asarray(Image.open(_blk("l94_play_wall")).convert("RGB"), float)
    for (x0, y0, w, h, a) in ((6, 10, 40, 52, -0.06), (54, 6, 44, 56, 0.05), (20, 70, 46, 50, 0.03), (78, 66, 40, 54, -0.08)):
        wall = _l94c_paper_sheet(wall, rng, x0, y0, w, h, a)
    save_png(wall, _blk("l94_sketch_wall"))

    board = add_lum(fill((84, 138, 70), (S, S)), rng.normal(0, 4, (S, S)) + fnoise(rng, beta=2.2) * 5)
    road = (np.abs(X - 64) < 8) | (np.abs(Y - 64) < 8)
    board = over(board, np.array([120, 120, 124]), road * 1.0)
    board = add_lum(board, 30 * ((np.abs(X - 64) < 0.8) & (np.abs(Y - 64) > 8) | (np.abs(Y - 64) < 0.8) & (np.abs(X - 64) > 8)))
    save_png(board, _blk("l94_studio_table_top"))
    side = _wood(rng, np.array([120, 84, 52]), horizontal=True)
    side = add_lum(side, -20 * (Y < 6) + 10 * ((Y >= 6) & (Y < 9)))
    save_png(side, _blk("l94_studio_table_side"))

    front = _l94_paint(rng, (236, 228, 204), grain=3.0)
    front = over(front, np.array([150, 70, 50]), (Y > 112) * 1.0)                        # brick band
    front = over(front, np.array([150, 40, 40]), box((S, S), 54, 72, 74, 112))           # door
    for wx in (18, 92):
        front = over(front, np.array([250, 250, 246]), box((S, S), wx - 2, 56, wx + 20, 82))
        front = over(front, np.array([60, 76, 98]), box((S, S), wx, 58, wx + 18, 80))
    save_png(front, _blk("l94_mini_house_front"))
    side = _l94_paint(rng, (236, 228, 204), grain=3.0)
    side = over(side, np.array([150, 70, 50]), (Y > 112) * 1.0)
    side = over(side, np.array([250, 250, 246]), box((S, S), 52, 56, 76, 84))
    side = over(side, np.array([60, 76, 98]), box((S, S), 54, 58, 74, 82))
    save_png(side, _blk("l94_mini_house_side"))
    roof = add_lum(fill((170, 52, 40), (S, S)), -30 * ((Y % 16) < 2) + fnoise(rng, beta=2.2) * 5)
    save_png(roof, _blk("l94_mini_roof"))
    leaves = add_lum(fill((60, 130, 56), (S, S)), blur(rng.normal(0, 14, (S, S)), 1.2) + fnoise(rng, beta=1.6, kmin=4) * 10)
    save_png(leaves, _blk("l94_mini_leaves"))
    save_png(_wood(rng, np.array([100, 70, 44]), horizontal=False), _blk("l94_mini_trunk"))
    save_png(_smooth_concrete(rng, (196, 192, 186), mottle=4.0, fine=1.2), _blk("l94_mini_stone"))
    water = add_lum(fill((206, 206, 186), (S, S)), fnoise(rng, beta=2.0, aniso=(1, 1)) * 6)
    save_png(water, _blk("l94_mini_water"))


L94_CASTLE_TEXTURES = [tex_l94c_mountain, tex_l94c_exterior, tex_l94c_stone_light, tex_l94c_floors,
                       tex_l94c_walls, tex_l94c_play, tex_l94c_studio]


# =============================================================================
# Level 94 "Motion" sky (Sky slice): the big painted clouds and the far-off
# castle on its glass mountain that client/Level94Sky draws in the sky, the soft
# wind of the hills (the biome's ambient loop), very faint synthetic birdsong by
# day and the distant cartoon laughter at night (client/Level94Atmosphere).
# Run: python tools/gen_assets.py l94_sky l94_castle_far snd_l94_wind snd_l94_birds snd_l94_laugh
# =============================================================================

def _l94_env(name: str):
    return TEX / "environment" / f"{name}.png"


def _l94_save_sky(img, name: str) -> None:
    """Saves a sky picture plus a .mcmeta asking for smooth (blurred) filtering and clamped edges,
    because the sky draws these much bigger than their pixels."""
    path = _l94_env(name)
    save_png(img, path)
    path.with_name(path.name + ".mcmeta").write_text('{"texture": {"blur": true, "clamp": true}}\n')


def tex_l94_sky_clouds() -> None:
    """Four big fluffy painted cumulus clouds, l94_cloud_0..3 (512x256, see-through round them).

    Each is a heap of soft round puffs on a flat base, painted back to front: bright white where
    the sun catches the top of each puff, a soft blue-grey underneath and a faint darker rim where
    one puff sits in front of another, like a painted theatre backdrop. The sky tints them (orange
    at dusk, dark at night) with the vertex colour."""
    W, H = 512, 256
    X, Y = grid((H, W))
    white = np.array([255.0, 255.0, 255.0])
    shade = np.array([186.0, 200.0, 224.0])
    for i in range(4):
        rng = rng_for(9490 + i)
        base_y = H * rng.uniform(0.74, 0.8)
        width = rng.uniform(0.62, 0.8) * W
        puffs = []
        for _ in range(int(rng.integers(9, 14))):
            u = rng.uniform(-0.5, 0.5)
            cx = W / 2 + u * width
            mid = 1.0 - abs(u) * 2.0                       # bigger puffs in the middle: a dome
            r = rng.uniform(26, 40) + 56 * mid * rng.uniform(0.55, 1.0)
            cy = base_y - r * rng.uniform(0.35, 0.8)
            puffs.append((cy, cx, r))
        # Higher puffs are further back: paint them first.
        puffs.sort(key=lambda p: p[0])
        rgb = np.broadcast_to(shade, (H, W, 3)).copy()   # see-through parts keep the cloud colour (no dark fringe)
        alpha = np.zeros((H, W))
        for cy, cx, r in puffs:
            d = np.hypot(X - cx, Y - cy) / r
            cover = smoothstep(1.0, 0.9, d) * smoothstep(base_y + 3, base_y - 6, Y)   # flat bottom
            lit = smoothstep(0.75, -0.6, (Y - cy) / r + 0.25 * (X - cx) / r)          # sun up and to the right
            col = lerp(shade, white, lit[..., None])
            col = col - 26 * smoothstep(0.7, 1.0, d)[..., None] * (1 - lit[..., None])  # soft rim
            rgb = lerp(rgb, col, cover[..., None])
            alpha = alpha + (1 - alpha) * cover
        # The base is in its own shadow; a little paper grain so it reads as painted.
        rgb = rgb - 18 * smoothstep(base_y - 40, base_y, Y)[..., None]
        rgb = rgb + (fnoise(rng, (H, W), beta=2.6) * 3.0)[..., None]
        alpha = blur(alpha, 1.2)
        img = np.concatenate([np.clip(rgb, 0, 255), (np.clip(alpha, 0, 1) * 255)[..., None]], axis=2)
        _l94_save_sky(img, f"l94_cloud_{i}")


def tex_l94_castle_far() -> None:
    """The castle on its glass mountain as seen from far away: l94_castle_far.png (256x352).

    2 px per block: the picture is 128 blocks wide (the mountain's foot, radius 64) and covers
    y40 (bottom row, the ground) to y216 (top), so client/Level94Sky can size it from the real
    distance. The mountain is faint, see-through pale-blue glass that curves up to the flat top at
    y150 (radius 24); on it stands a white tiered castle with red pointed roofs, lit from the right."""
    W, H = 256, 352
    k = 4                                          # drawn at 4x, then shrunk (anti-aliasing)

    def px(x_block, y_block):
        """Block offset from the centre / block height -> pixel position at k x size."""
        return ((W / 2 + x_block * 2) * k, (H - (y_block - 40) * 2) * k)

    # --- the glass mountain (numpy, in pixels) ---
    X, Y = grid((H, W))
    yb = 216 - Y / 2                                   # block height of each row
    t = np.clip((yb - 40) / 110, 0, 1)
    radius = 24 + 40 * (1 - t) ** 1.35                 # concave sides, flat top at y150
    xb = (X - W / 2) / 2
    inside = smoothstep(1.0, -1.0, np.abs(xb) - radius) * (yb <= 150.5) * (yb >= 40)
    side = np.clip(xb / np.maximum(radius, 1), -1, 1)  # -1 left edge .. 1 right edge
    glass = lerp(np.array([118, 158, 204]), np.array([214, 232, 248]), smoothstep(-0.9, 0.7, side)[..., None])
    rng = rng_for(9495)
    streaks = smoothstep(0.6, 1.8, blur(fnoise(rng, (H, W), beta=3.0, aniso=(1, 3)), 2.0))   # cloud reflections
    glass = lerp(glass, np.array([246, 250, 255]), (streaks * 0.6)[..., None])
    edge = smoothstep(0.75, 1.0, np.abs(side))          # glass looks thicker at its rims
    a_mtn = inside * (0.26 + 0.26 * edge + 0.18 * streaks + 0.12 * smoothstep(146, 150, yb))
    img = np.concatenate([glass, (np.clip(a_mtn, 0, 1) * 255)[..., None]], axis=2)

    # --- the castle (PIL) ---
    castle = Image.new("RGBA", (W * k, H * k), (0, 0, 0, 0))
    d = ImageDraw.Draw(castle)
    wall_lit, wall_dark = (246, 244, 238, 255), (204, 206, 214, 255)
    roof_lit, roof_dark = (214, 46, 40, 255), (158, 30, 32, 255)
    window = (70, 80, 104, 255)

    def block(x0, x1, y0, y1):
        """A white wall section from block x0..x1 (offset from the centre), heights y0..y1, shaded
        on its left third (the sun is to the right)."""
        mid = x0 + (x1 - x0) * 0.33
        d.rectangle([*px(x0, y1), *px(x1, y0)], fill=wall_lit)
        d.rectangle([*px(x0, y1), *px(mid, y0)], fill=wall_dark)

    def roof(x0, x1, y0, y1):
        """A red pointed roof from the eave (y0) to the tip (y1), its left half in shade."""
        cx = (x0 + x1) / 2
        d.polygon([px(x0 - 0.6, y0), px(cx, y1), px(x1 + 0.6, y0)], fill=roof_lit)
        d.polygon([px(x0 - 0.6, y0), px(cx, y1), px(cx, y0)], fill=roof_dark)

    def windows(x0, x1, y, count, h=2.0):
        step = (x1 - x0) / (count + 1)
        for j in range(1, count + 1):
            wx = x0 + step * j
            d.rectangle([*px(wx - 0.6, y + h), *px(wx + 0.6, y)], fill=window)

    block(-19, 19, 150, 172)                           # the big lower storey
    windows(-19, 19, 158, 7, 3)
    block(-13, 13, 172, 188)                           # second tier
    windows(-13, 13, 178, 5)
    block(-8, 8, 188, 199)                             # third tier
    windows(-8, 8, 192, 3)
    block(-4, 4, 199, 205)                             # the top turret
    roof(-5, 5, 205, 215.5)
    for x in (-19, 15):                                # corner towers with their own roofs
        block(x, x + 4, 150, 180)
        roof(x, x + 4, 180, 189)
    for x in (-12, 9):                                 # little turrets on the second tier
        block(x, x + 3, 172, 192)
        roof(x, x + 3, 192, 199)
    d.rectangle([*px(-3, 158), *px(3, 150)], fill=(96, 70, 56, 255))   # the gate
    castle = castle.resize((W, H), Image.LANCZOS)
    c = np.asarray(castle, float)
    ca = c[..., 3:4] / 255
    out = img.copy()
    out[..., :3] = img[..., :3] * (1 - ca) + c[..., :3] * ca
    out[..., 3] = np.clip(img[..., 3] + (255 - img[..., 3]) * ca[..., 0], 0, 255)
    _l94_save_sky(out, "l94_castle_far")


def snd_l94_wind() -> None:
    """16 s loop: the near-silent hills. A soft, airy breeze that swells and settles in slow
    gusts, with a faint high whistle on the strongest ones. Kept very quiet and smooth."""
    rng = rng_for(9496)
    T = 16.0
    n = int(T * SR)
    gust = drift(n, rng, 0.55, 0.05, 0.18)
    air = periodic_noise(n, rng, lambda f: bp_gain(f, 120, 900, 2) / np.maximum(f, 60) ** 0.3) * gust
    low = periodic_noise(n, rng, lambda f: bp_gain(f, 40, 160, 2)) * drift(n, rng, 0.3, 0.05, 0.15)
    whistle = periodic_noise(n, rng, lambda f: bp_gain(f, 1300, 1500, 4)) * np.clip(gust - 1.2, 0, None)
    x = norm(air) + 0.35 * norm(low) + 0.05 * norm(whistle)
    save_ogg(x, SND / "level94" / "wind.ogg", peak=0.3, loop=True)


def _l94_chirp(f0, f1, dur, rng) -> np.ndarray:
    """One clean, slightly too-perfect sine chirp gliding from f0 to f1 Hz (a toy bird)."""
    t = t_axis(dur)
    f = f0 * (f1 / f0) ** (t / dur)
    f = f * (1 + 0.04 * np.sin(2 * np.pi * rng.uniform(25, 40) * t))      # a little warble
    phase = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(phase) * np.sin(np.pi * t / dur) ** 2


def snd_l94_birds() -> None:
    """Three short phrases of faint synthetic birdsong (birds1..3), played far off by day: pure
    sine chirps and trills, too clean and too regular to be real birds, in a little open-air echo."""
    phrases = [
        # (seed, [(start, f0, f1, dur), ...])
        (9497, [(0.0, 3400, 2600, 0.09), (0.16, 3400, 2600, 0.09), (0.32, 3600, 2400, 0.14)]),
        (9498, [(0.0, 2400, 3800, 0.12), (0.30, 4200, 3000, 0.07), (0.40, 4200, 3000, 0.07),
                (0.50, 4200, 3000, 0.07), (0.60, 4200, 3000, 0.07)]),
        (9499, [(0.0, 3000, 3000, 0.25), (0.34, 2600, 3400, 0.10), (0.50, 3400, 2200, 0.18)]),
    ]
    for i, (seed, notes) in enumerate(phrases, 1):
        rng = rng_for(seed)
        dur = 1.8
        x = np.zeros(int(dur * SR))
        for start, f0, f1, ln in notes:
            add_at(x, _l94_chirp(f0, f1, ln, rng), start + 0.05, circular=False)
        wet = conv(x, reverb_ir(rng, 1.2, 0.25, lp=5000, predelay=0.03))[:len(x)]
        x = norm(x) + 0.3 * norm(wet)
        save_ogg(x, SND / "level94" / f"birds{i}.ogg", peak=0.5, fade_out=0.2)


def snd_l94_laugh() -> None:
    """Three takes of distant cartoon laughter at night (laugh1..3), echoing over the hills.

    Entirely synthetic (no recorded voice): an additive formant voice says "hoo", "hoo", "ha"
    in a bouncy cartoon rhythm, the pitch wobbling like a warped record. Then it is pushed far
    away: everything above ~1.2 kHz rolled off, a long hazy reverb and two slap-back echoes
    off the hills, so it's barely a voice at all."""
    hoo = [(320, 90, 1.0), (800, 140, 0.45), (2300, 300, 0.05)]
    ha = [(760, 120, 1.0), (1220, 160, 0.7), (2500, 300, 0.12)]
    takes = [
        # (seed, base pitch Hz, [(vowel, start s, length s, pitch multiplier), ...])
        (9500, 240, [(hoo, 0.0, 0.20, 1.0), (hoo, 0.30, 0.20, 1.12), (ha, 0.62, 0.55, 1.35)]),
        (9501, 200, [(ha, 0.0, 0.18, 1.2), (ha, 0.26, 0.18, 1.1), (ha, 0.52, 0.18, 1.0), (hoo, 0.84, 0.6, 0.85)]),
        (9502, 280, [(hoo, 0.0, 0.16, 1.0), (hoo, 0.24, 0.16, 1.06), (hoo, 0.48, 0.16, 1.12), (ha, 0.76, 0.7, 1.45)]),
    ]
    for i, (seed, base, syllables) in enumerate(takes, 1):
        rng = rng_for(seed)
        dur = 6.0
        n = int(dur * SR)
        dry = np.zeros(n)
        for formants, start, ln, mult in syllables:
            m = int(ln * SR)
            t = np.arange(m) / SR
            # Each syllable bounces: up quickly, then sags (the long last one sags a lot).
            contour = mult * (1 + 0.08 * np.sin(np.pi * np.clip(t / ln * 1.4, 0, 1)) - 0.18 * (t / ln) ** 2 * (ln > 0.4))
            f0 = base * contour * (1 + 0.012 * np.sin(2 * np.pi * 6.0 * t))
            v = voice(f0, formants, rng, nh=24, tilt=0.02)
            breath = fft_filter(rng.normal(size=m), lambda f: bp_gain(f, 300, 1500, 2)) * 0.04
            env = env_adsr(m, 0.03, min(0.12, ln * 0.5))
            add_at(dry, norm(v + breath) * env, 0.1 + start, circular=False)
        # The warped-record wobble: the whole thing drifts in pitch by a few percent.
        idx = np.arange(n) + np.cumsum(0.04 * np.sin(2 * np.pi * 0.9 * np.arange(n) / SR) + smooth_jitter(rng, n, 3, 0.02))
        dry = np.interp(np.clip(idx, 0, n - 1), np.arange(n), dry)
        dry = fft_filter(dry, lambda f: lp_gain(f, 1200, 2) * hp_gain(f, 120, 2))[:n]   # far away: muffled
        echoes = np.zeros(n)
        add_at(echoes, dry * 0.45, 0.38, circular=False)
        add_at(echoes, dry * 0.22, 0.95, circular=False)
        hall = conv(dry + echoes, reverb_ir(rng, 4.0, 1.3, lp=1400, predelay=0.08))[:n]
        x = 0.35 * norm(dry) + 0.3 * norm(echoes) + 1.0 * norm(hall)
        save_ogg(x, SND / "level94" / f"laugh{i}.ogg", peak=0.7, fade_out=0.6)


L94_SKY_TEXTURES = [tex_l94_sky_clouds, tex_l94_castle_far]
L94_SKY_SOUNDS = [snd_l94_wind, snd_l94_birds, snd_l94_laugh]


# =============================================================================


# =============================================================================
# Music: three ORIGINAL pieces for the Backrooms, composed here note by note
# (nothing is sampled or copied). Inspired by the feel of slow "liminal" tape
# music and by the empty, unsettling sound of Minecraft's disc 13, not by their
# notes:
#   liminal - the main theme (title menu, and now and then in the levels): slow,
#             washed-out tape chords in D minor with an unsettling E-flat. It
#             starts with single tones fading in, swells into a wall of sound,
#             then falls apart into wind and far-off noises.
#   deep    - mostly drones, wind and distant noises with long near-silences;
#             fragments of the theme drift past as if heard through walls.
#   drift   - softer and dreamier for the Poolrooms and Level 37: warm chords
#             that are never quite at rest, water drips, a lot of space.
# All three are stereo and go through the same "worn tape" chain: a slow pitch
# wow and a little flutter shared by every note, soft saturation, hiss, a dull
# top end and a big empty room.

_M_PC = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
_M_TABLES: dict = {}


def _m_hz(name: str) -> float:
    """Note name -> Hz: 'D3', 'Eb4', 'F#2' (A4 = 440 Hz)."""
    pc = _M_PC[name[0]]
    rest = name[1:]
    while rest and rest[0] in "#b":
        pc += 1 if rest[0] == "#" else -1
        rest = rest[1:]
    return 440.0 * 2 ** ((12 * (int(rest) + 1) + pc - 69) / 12)


def _m_table(nh: int, tilt: float) -> np.ndarray:
    """One cycle (4096 samples) of a soft saw-like wave with nh harmonics falling as 1/k^tilt.
    The top third of the harmonics fades out so the band limit has no hard edge."""
    key = (nh, tilt)
    if key not in _M_TABLES:
        ph = np.arange(4096) / 4096
        k = np.arange(1, nh + 1)
        amps = 1.0 / k ** tilt * np.clip((nh + 1 - k) / max(1.0, nh * 0.35), 0, 1)
        tab = (amps[:, None] * np.sin(2 * np.pi * k[:, None] * ph[None, :])).sum(0)
        _M_TABLES[key] = np.append(tab / np.abs(tab).max(), 0.0)
        _M_TABLES[key][-1] = _M_TABLES[key][0]
    return _M_TABLES[key]


def _m_env(n: int, attack: float, hold_end: float, release: float) -> np.ndarray:
    """Raised-cosine swell in, hold, then an exponential release (about -26 dB after `release`)."""
    t = np.arange(n) / SR
    e = 0.5 - 0.5 * np.cos(np.pi * np.clip(t / max(attack, 1e-3), 0, 1))
    e = e * np.where(t < hold_end, 1.0, np.exp(-(t - hold_end) / (release / 3.0)))
    tail = min(n, int(0.05 * SR))
    e[n - tail:] *= np.linspace(1, 0, tail)
    return e


class _MTrack:
    """A stereo multitrack: named buses of (left, right) float32 arrays plus one shared tape
    pitch curve, so every note wobbles together like a real worn cassette."""

    def __init__(self, seconds: float, seed: int, wow: float = 0.0035, flutter: float = 0.0009):
        self.n = int(seconds * SR)
        self.rng = rng_for(seed)
        t = np.arange(self.n) / SR
        self.ratio = (1 + wow * (0.6 * np.sin(2 * np.pi * 0.29 * t + self.rng.uniform(0, 6.3))
                                 + 0.4 * smooth_jitter(self.rng, self.n, 0.2, 1.0))
                      + flutter * np.sin(2 * np.pi * 5.3 * t))
        self.buses: dict = {}

    def bus(self, name: str):
        if name not in self.buses:
            self.buses[name] = (np.zeros(self.n, np.float32), np.zeros(self.n, np.float32))
        return self.buses[name]

    def add(self, name: str, start: float, left: np.ndarray, right=None) -> None:
        L, R = self.bus(name)
        right = left if right is None else right
        i0 = max(0, int(round(start * SR)))
        end = min(self.n, i0 + len(left))
        if end > i0:
            L[i0:end] += left[: end - i0].astype(np.float32)
            R[i0:end] += right[: end - i0].astype(np.float32)

    def _ratio(self, start: float, n: int) -> np.ndarray:
        i0 = max(0, int(round(start * SR)))
        seg = self.ratio[i0:i0 + n]
        return np.pad(seg, (0, n - len(seg)), constant_values=1.0)

    def pad(self, name: str, start: float, dur: float, note: str, vel: float, bright: float = 2500.0,
            attack: float = 1.6, release: float = 3.5, tilt: float = 1.15, cents=(-8.0, 0.0, 7.0),
            pitch: float = 1.0) -> None:
        """A slow pad note: three detuned saw-ish voices spread across the stereo field."""
        freq = _m_hz(note) * pitch
        n = int((dur + release) * SR)
        ratio = self._ratio(start, n)
        tab = _m_table(int(np.clip(bright / freq, 1, 64)), tilt)
        env = _m_env(n, attack, dur, release) * vel
        L = np.zeros(n)
        R = np.zeros(n)
        for c, pan in zip(cents, (-0.8, 0.0, 0.8)):
            ph = (self.rng.uniform() + np.cumsum(freq * 2 ** (c / 1200) * ratio) / SR) % 1.0
            v = np.interp(ph * 4096, np.arange(4097), tab)
            L += v * np.sqrt(0.5 * (1 - pan))
            R += v * np.sqrt(0.5 * (1 + pan))
        self.add(name, start, L * env / len(cents), R * env / len(cents))

    def chord(self, name: str, start: float, dur: float, notes, vel: float, spread: float = 0.18, **kw) -> None:
        """Several pad notes, each entering a moment after the last (an unhurried hand)."""
        for i, note in enumerate(notes):
            self.pad(name, start + i * spread * self.rng.uniform(0.6, 1.2), dur, note,
                     vel * self.rng.uniform(0.85, 1.0), **kw)

    def bell(self, name: str, start: float, note: str, vel: float, decay: float = 2.4,
             pitch: float = 1.0, pan: float = 0.0) -> None:
        """Music-box / celesta tone: a few inharmonic partials, a soft click of an attack."""
        freq = _m_hz(note) * pitch
        n = int(decay * 3.2 * SR)
        t = np.arange(n) / SR
        ratio = self._ratio(start, n)
        x = np.zeros(n)
        for mult, amp, dk in ((1.0, 1.0, 1.0), (2.0, 0.28, 0.55), (2.76, 0.22, 0.35),
                              (5.40, 0.10, 0.16), (8.93, 0.04, 0.09)):
            ph = np.cumsum(freq * mult * ratio) / SR + self.rng.uniform()
            x += amp * np.sin(2 * np.pi * ph) * np.exp(-t / (decay * dk))
        x *= np.clip(t / 0.004, 0, 1) * vel
        self.add(name, start, x * np.sqrt(0.5 * (1 - pan)), x * np.sqrt(0.5 * (1 + pan)))

    def drone(self, name: str, start: float, dur: float, note: str, vel: float, fade: float = 8.0,
              beat: float = 0.35) -> None:
        """Low sine drone with a slightly detuned twin (slow beating) and a little 2nd harmonic."""
        freq = _m_hz(note)
        n = int(dur * SR)
        t = np.arange(n) / SR
        ratio = self._ratio(start, n)
        ph = np.cumsum(freq * ratio) / SR
        x = np.sin(2 * np.pi * ph) + 0.25 * np.sin(4 * np.pi * ph + 1.0)
        y = np.sin(2 * np.pi * np.cumsum((freq + beat) * ratio) / SR + 0.5)   # beats `beat` times a second
        e = np.clip(t / fade, 0, 1) * np.clip((dur - t) / fade, 0, 1)
        e = e * e * (3 - 2 * e) * vel
        self.add(name, start, (x + 0.7 * y) * e * 0.6, (0.7 * x + y) * e * 0.6)

    def noise_bed(self, name: str, lo: float, hi: float, vel_pts, drift_amt: float = 0.35) -> None:
        """Band-limited noise (wind, room, water) whose level follows (seconds, level) points and
        breathes slowly on its own. Left and right are independent, so it fills the space."""
        t = np.arange(self.n) / SR
        level = np.interp(t, [p[0] for p in vel_pts], [p[1] for p in vel_pts])
        chans = []
        for _ in range(2):
            nz = fft_filter(self.rng.normal(size=self.n), lambda f: bp_gain(f, lo, hi, 2))
            nz /= nz.std() + 1e-12
            chans.append(nz * level * (1 + smooth_jitter(self.rng, self.n, 0.15, drift_amt)))
        self.add(name, 0.0, chans[0], chans[1])

    def boom(self, name: str, start: float, vel: float) -> None:
        """Something heavy, very far away: a falling sub thump with a dull rumble."""
        n = int(4.0 * SR)
        t = np.arange(n) / SR
        f = 26 + 30 * np.exp(-t / 0.5)
        x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 1.1)
        rum = fft_filter(self.rng.normal(size=n), lambda fr: bp_gain(fr, 30, 160, 2))
        x = norm(x) + 0.5 * norm(rum) * np.exp(-t / 0.9)
        x *= np.clip(t / 0.02, 0, 1) * vel
        self.add(name, start, x, x)

    def creak(self, name: str, start: float, vel: float, pan: float = 0.3) -> None:
        """A slow, far-off creak (a door or a beam somewhere you can't see)."""
        x = _creak(self.rng, 2.6, 7, 26, [420, 760, 1310])
        x = fft_filter(x, lambda f: lp_gain(f, 1600, 2))
        x = norm(x) * vel
        self.add(name, start, x * np.sqrt(0.5 * (1 - pan)), x * np.sqrt(0.5 * (1 + pan)))

    def scrape(self, name: str, start: float, vel: float, f0: float = 900.0, f1: float = 280.0,
               dur: float = 3.2) -> None:
        """Metal dragged on concrete far away: a resonant noise band sliding down in pitch."""
        n = int(dur * SR)
        t = np.arange(n) / SR
        nz = self.rng.normal(size=n)
        x = np.zeros(n)
        for k in range(3):   # a few overlapping fixed bands crossfaded = a cheap sweep
            fc = f0 + (f1 - f0) * (k + 0.5) / 3
            band = fft_filter(nz, lambda f: bp_gain(f, fc * 0.92, fc * 1.08, 4))
            w = np.clip(1 - np.abs(t / dur * 3 - (k + 0.5)), 0, 1)
            x += band * w
        x = norm(x) * np.sin(np.pi * t / dur) ** 1.5 * vel
        self.add(name, start, x * 0.8, x)

    def swell(self, name: str, end: float, note: str, vel: float, length: float = 5.0) -> None:
        """A reversed reverb tail that rises and stops dead at `end` (classic dread swell)."""
        n = int(length * SR)
        rng = self.rng
        src = np.zeros(n)
        f = _m_hz(note)
        tt = np.arange(int(0.4 * SR)) / SR
        hit = sum(np.sin(2 * np.pi * f * m * tt) * a for m, a in ((1, 1), (2, 0.4), (3, 0.2))) * np.exp(-tt / 0.15)
        src[: len(hit)] = hit
        for ch in range(2):
            wet = conv(src, reverb_ir(rng, length, length / 3.5, lp=3000))[:n][::-1]
            wet = norm(wet) * vel * np.clip(np.arange(n) / (0.3 * n), 0, 1)
            if ch == 0:
                left = wet
            else:
                right = wet
        self.add(name, end - length, left, right)

    def master(self, mix: dict, reverb: dict, room_s: float = 5.5, room_lp: float = 3800.0,
               lp: float = 6500.0, drive: float = 1.3, hiss: float = 0.0025):
        """Sum the buses (gain per bus), send some of each into a big dark room, then the tape:
        soft saturation, a dull top, a little hiss."""
        L = np.zeros(self.n)
        R = np.zeros(self.n)
        sendL = np.zeros(self.n)
        sendR = np.zeros(self.n)
        for name, (bl, br) in self.buses.items():
            g = mix.get(name, 1.0)
            L += g * bl
            R += g * br
            s = reverb.get(name, 0.0) * g
            if s:
                sendL += s * bl
                sendR += s * br
        irL = reverb_ir(self.rng, room_s, room_s / 4.0, lp=room_lp, predelay=0.03)
        irR = reverb_ir(self.rng, room_s, room_s / 4.0, lp=room_lp, predelay=0.037)
        L += conv(sendL, irL)[: self.n] * 4.0
        R += conv(sendR, irR)[: self.n] * 4.0
        peak = max(np.abs(L).max(), np.abs(R).max()) + 1e-9
        L = np.tanh(drive * L / peak) / np.tanh(drive)
        R = np.tanh(drive * R / peak) / np.tanh(drive)
        tape = lambda f: lp_gain(f, lp, 2) * hp_gain(f, 28, 2)
        L = fft_filter(L, tape)
        R = fft_filter(R, tape)
        for ch in (L, R):
            ch += hiss * fft_filter(self.rng.normal(size=self.n), lambda f: bp_gain(f, 1500, 9000, 1))
        return L, R


def _m_save(L: np.ndarray, R: np.ndarray, path: Path, peak: float = 0.62,
            fade_in: float = 0.5, fade_out: float = 5.0) -> None:
    """Stereo Ogg Vorbis with gentle fades; re-encoded quieter if the codec overshoots."""
    x = np.stack([L, R], axis=1)
    x -= x.mean(axis=0)
    fi, fo = int(fade_in * SR), int(fade_out * SR)
    x[:fi] *= np.linspace(0, 1, fi)[:, None]
    x[-fo:] *= (np.linspace(1, 0, fo) ** 2)[:, None]
    x = x / np.abs(x).max() * peak
    path.parent.mkdir(parents=True, exist_ok=True)
    gain = 1.0
    for _ in range(6):
        # Written in blocks: libsndfile's Vorbis encoder overflows its stack on one huge write.
        with sf.SoundFile(str(path), "w", SR, 2, format="OGG", subtype="VORBIS") as out:
            y = (x * gain).astype(np.float32)
            for i in range(0, len(y), SR):
                out.write(y[i:i + SR])
        decoded = np.abs(sf.read(str(path))[0]).max()
        if decoded <= peak + 0.03:
            break
        gain *= peak / decoded


def snd_music_liminal() -> None:
    """The main theme, ~3 minutes. D minor with E-flat (the Phrygian colour that makes it uneasy),
    one chord every 4.4 s. Intro: single tones fade in one by one over a low D. A: soft chords
    and a slow music-box line. A reversed swell, then B: the same world, but loud, wide and
    saturated. Then everything drops away: wind, a creak, a far boom, the music box again from
    somewhere far off and out of tune, and a last D against E-flat that never resolves."""
    bar = 4.4
    tr = _MTrack(39 * bar + 6.0, seed=13001)

    # Intro (bars 0-6): a low D, then tones enter one at a time and pile up into a cluster.
    tr.drone("drone", 0.0, 14.5 * bar, "D2", 0.18, fade=14.0)
    for at, length, note, vel in ((0.6, 5.6, "D3", 0.16), (1.5, 4.7, "A3", 0.13), (2.4, 3.8, "E4", 0.10),
                                  (3.3, 2.9, "F4", 0.10), (4.2, 2.0, "C4", 0.08)):
        tr.pad("soft", at * bar, length * bar, note, vel, bright=1400, attack=4.0, release=5.0)

    # A (bars 6-14): soft chords, root an octave below, a slow music-box line on top.
    a_chords = [["D3", "A3", "C4", "E4", "F4"], ["Bb2", "F3", "A3", "D4"], ["G2", "D3", "Bb3", "A4"],
                ["Eb3", "G3", "Bb3", "D4", "A4"], ["D3", "A3", "C4", "E4", "F4"], ["Bb2", "F3", "A3", "D4", "F4"],
                ["G2", "F3", "Bb3", "D4", "A4"], ["A2", "E3", "G3", "C#4", "Bb4"]]
    a_roots = ["D2", "Bb1", "G1", "Eb2", "D2", "Bb1", "G1", "A1"]
    for i, (ch, root) in enumerate(zip(a_chords, a_roots)):
        at = (6 + i) * bar
        tr.chord("soft", at, bar, ch, 0.19, bright=1500, attack=1.8, release=4.0)
        tr.pad("bass", at, bar, root, 0.30, bright=300, attack=1.0, release=3.0)
    motif = [(7.0, "A5"), (7.5, "F5"), (8.0, "E5"), (8.75, "D5"), (9.0, "Eb5"), (9.5, "D5"),
             (11.0, "F5"), (11.5, "E5"), (12.0, "D5"), (12.5, "A4"), (13.0, "Bb4"), (13.5, "C#5"), (13.8, "A4")]
    for k, (b, note) in enumerate(motif):
        tr.bell("bell", b * bar + tr.rng.uniform(-0.04, 0.06), note, 0.24, pan=-0.3 + 0.6 * (k % 2))

    # Bar 14: everything holds its breath; a reversed swell rushes up into B.
    tr.pad("soft", 14 * bar, bar, "D3", 0.15, bright=900, attack=1.0, release=2.5)
    tr.swell("fx", 15 * bar, "D3", 0.55, length=5.5)

    # B (bars 15-27): a wall of saturated, wide chords; quieter octave shimmer on top.
    b_chords = [["D3", "A3", "E4", "F4", "A4"], ["D3", "Eb3", "G3", "Bb3", "G4"], ["F3", "A3", "C4", "D4", "F4"],
                ["D3", "F3", "Bb3", "A4"], ["D3", "A3", "E4", "F4", "A4"], ["Eb3", "G3", "Bb3", "D4", "A4"],
                ["F3", "Bb3", "D4", "A4"], ["E3", "G3", "C#4", "Bb4"], ["F3", "A3", "D4", "F4"],
                ["G3", "A3", "Bb3", "D4"], ["E3", "G3", "Bb3", "C#4"], ["D3", "A3", "E4", "F4"]]
    b_roots = ["D2", "D2", "Bb1", "G1", "D2", "Eb2", "F2", "A1", "F2", "Bb1", "C#2", "D2"]
    for i, (ch, root) in enumerate(zip(b_chords, b_roots)):
        at = (15 + i) * bar
        last = i == len(b_chords) - 1
        swell = 0.75 + 0.25 * np.sin(np.pi * min(i, 8) / 8)   # builds to bar ~22, then eases
        tr.chord("full", at, bar * (2.5 if last else 1.0), ch, 0.32 * swell, bright=3600,
                 attack=1.2, release=6.0 if last else 3.0, spread=0.08)
        tr.chord("shimmer", at + 0.3, bar, ch[-2:], 0.35 * swell, bright=5000, attack=2.0,
                 release=3.5, pitch=2.0)
        tr.pad("bass", at, bar * (2.5 if last else 1.0), root, 0.70, bright=420, attack=0.8,
               release=6.0 if last else 2.5)
    for k, (b, note) in enumerate(motif[:6]):
        tr.bell("bell", (b + 10) * bar, note, 0.22, pan=0.4 - 0.8 * (k % 2))
    for k, (b, note) in enumerate(motif[6:]):
        tr.bell("bell", (b + 10) * bar, note, 0.20, pan=-0.4 + 0.8 * (k % 2), pitch=0.5)

    # Breakdown (bars 27-33): the music is gone; only the place is left.
    tr.drone("drone", 26.5 * bar, 12.5 * bar, "D2", 0.2, fade=10.0)
    tr.creak("fx", 121.0, 0.22, pan=0.5)
    tr.boom("fx", 127.0, 0.5)
    tr.swell("fx", 134.0, "Eb3", 0.35, length=6.0)
    far = [(135.0, "A4"), (136.3, "F4"), (137.6, "E4"), (139.2, "D4"), (140.6, "Eb4"), (142.4, "D4")]
    for k, (s, note) in enumerate(far):
        tr.bell("far", s + tr.rng.uniform(-0.1, 0.1), note, 0.32, decay=3.0,
                pitch=0.985 - 0.004 * k, pan=-0.5)

    # Outro (bars 33-39): A, D and E-flat rubbing against each other, fading into the hiss.
    for note, vel in (("A2", 0.13), ("D3", 0.13), ("Eb3", 0.08)):
        tr.pad("soft", 33 * bar + tr.rng.uniform(0, 1.5), 5.5 * bar, note, vel, bright=1100,
               attack=5.0, release=7.0)

    tr.noise_bed("wind", 180, 900, [(0, 0.0), (12, 0.03), (60, 0.018), (66, 0.01), (118, 0.012),
                                    (124, 0.045), (150, 0.03), (178, 0.02)])
    tr.noise_bed("room", 25, 120, [(0, 0.0), (10, 0.02), (178, 0.02)], drift_amt=0.2)

    for name, (l, r) in list(tr.buses.items()):
        if name == "soft":
            tr.buses[name] = (fft_filter(l, lambda f: lp_gain(f, 1400, 2)), fft_filter(r, lambda f: lp_gain(f, 1400, 2)))
        elif name == "full":
            l2 = fft_filter(np.tanh(2.2 * l / (np.abs(l).max() + 1e-9)), lambda f: lp_gain(f, 3600, 2))
            r2 = fft_filter(np.tanh(2.2 * r / (np.abs(r).max() + 1e-9)), lambda f: lp_gain(f, 3600, 2))
            tr.buses[name] = (l2 * np.abs(l).max(), r2 * np.abs(r).max())
        elif name == "far":
            tr.buses[name] = (fft_filter(l, lambda f: bp_gain(f, 250, 1300, 2)), fft_filter(r, lambda f: bp_gain(f, 250, 1300, 2)))
    L, R = tr.master(mix={"soft": 1.0, "full": 1.1, "shimmer": 0.5, "bass": 0.7, "bell": 0.55, "drone": 0.55,
                          "fx": 0.6, "far": 0.35, "wind": 1.0, "room": 1.0},
                     reverb={"soft": 0.5, "full": 0.35, "shimmer": 0.8, "bass": 0.1, "bell": 0.7, "far": 1.0,
                             "fx": 0.4, "drone": 0.1})
    _m_save(L, R, SND / "music" / "liminal.ogg", fade_out=6.0)


def snd_music_deep() -> None:
    """~2.5 minutes in the spirit of an empty, unsettling ambient record: drones on G, wind, long
    near-silences, things happening far away, and only fragments of harmony (one of them a
    memory of the main theme, muffled as if through walls)."""
    tr = _MTrack(152.0, seed=13002, wow=0.005)
    tr.drone("drone", 0.0, 88.0, "G1", 0.3, fade=15.0)
    tr.drone("drone", 2.0, 86.0, "D2", 0.15, fade=18.0, beat=0.21)
    tr.drone("drone", 104.0, 48.0, "G1", 0.25, fade=12.0)
    tr.bell("bell", 9.0, "G4", 0.35, decay=3.5, pan=-0.4)
    tr.bell("bell", 9.05, "Ab4", 0.25, decay=3.5, pan=0.4)
    tr.boom("fx", 18.0, 0.55)
    tr.creak("fx", 26.0, 0.25, pan=-0.6)
    tr.chord("memory", 34.0, 12.0, ["D3", "A3", "C4", "F4"], 0.5, bright=900, attack=4.0, release=6.0, pitch=0.97)
    tr.swell("fx", 58.0, "G3", 0.45, length=6.0)
    tr.bell("bell", 58.2, "G3", 0.4, decay=4.0, pan=0.2)
    tr.bell("bell", 61.0, "Db4", 0.3, decay=4.0, pan=-0.3)
    tr.scrape("fx", 66.0, 0.18)
    tr.boom("fx", 75.0, 0.4)
    for s, note in ((80.0, "C6"), (81.3, "B5"), (84.0, "C6")):
        tr.bell("drip", s, note, 0.18, decay=0.6, pan=tr.rng.uniform(-0.7, 0.7))
    for k, (s, note) in enumerate(((110.0, "A4"), (111.6, "F4"), (113.2, "E4"), (115.1, "D4"), (116.8, "Eb4"))):
        tr.bell("far", s, note, 0.5, decay=3.2, pitch=0.94 - 0.006 * k, pan=0.5)
    tr.creak("fx", 122.0, 0.2, pan=0.6)
    tr.swell("fx", 129.0, "Ab2", 0.35, length=5.0)
    tr.chord("memory", 129.0, 12.0, ["G2", "Ab2", "D3"], 0.45, bright=700, attack=5.0, release=6.0)
    tr.noise_bed("wind", 150, 800, [(0, 0.0), (10, 0.035), (50, 0.05), (88, 0.018), (104, 0.008),
                                    (108, 0.03), (140, 0.045), (152, 0.02)], drift_amt=0.5)
    tr.noise_bed("room", 20, 110, [(0, 0.012), (152, 0.018)], drift_amt=0.25)
    for name in ("memory", "far"):
        l, r = tr.buses[name]
        g = (lambda f: lp_gain(f, 520, 2)) if name == "memory" else (lambda f: bp_gain(f, 250, 1300, 2))
        tr.buses[name] = (fft_filter(l, g), fft_filter(r, g))
    L, R = tr.master(mix={"drone": 0.6, "bell": 0.45, "fx": 0.6, "memory": 0.6, "drip": 0.35, "far": 0.35,
                          "wind": 1.0, "room": 1.0},
                     reverb={"bell": 0.8, "fx": 0.45, "memory": 0.5, "drip": 1.0, "far": 1.0, "drone": 0.1},
                     room_s=7.0, room_lp=3000)
    _m_save(L, R, SND / "music" / "deep.ogg", fade_out=8.0)


def snd_music_drift() -> None:
    """~2 minutes for the Poolrooms and Level 37: soft, warm chords around F (with a raised B
    that keeps it floating), one borrowed D-flat chord that makes it slightly wrong, water drips,
    a slow music-box line, and an ending on two notes a semitone apart."""
    bar = 5.0
    tr = _MTrack(23 * bar + 7.0, seed=13003, wow=0.0045)
    tr.drone("drone", 0.0, 23 * bar + 4.0, "F2", 0.15, fade=12.0)
    chords = [["F3", "A3", "C4", "E4", "B4"], ["E3", "A3", "B3", "C4"], ["Db3", "F3", "Ab3", "C4"],
              ["G2", "E3", "G3", "B3", "C4"]]
    roots = ["F2", "E2", "Db2", "G1"]
    for rep in range(2):
        for i, (ch, root) in enumerate(zip(chords, roots)):
            at = (1 + rep * 4 + i) * bar
            tr.chord("soft", at, bar, ch, 0.30, bright=1100, tilt=1.6, attack=2.2, release=4.5, spread=0.3)
            tr.pad("bass", at, bar, root, 0.35, bright=260, attack=1.5, release=3.5)
    for i, (ch, root) in enumerate(zip(chords, roots)):   # second half: an octave of air above
        at = (9 + i) * bar
        tr.chord("soft", at, bar, ch, 0.34, bright=1300, tilt=1.6, attack=2.0, release=4.5, spread=0.3)
        tr.chord("shimmer", at + 0.5, bar, ch[-2:], 0.14, bright=4000, attack=2.5, release=4.0, pitch=2.0)
        tr.pad("bass", at, bar, root, 0.38, bright=260, attack=1.5, release=3.5)
    for i, (ch, root) in enumerate(zip(chords[:2], roots[:2])):
        at = (13 + i) * bar
        tr.chord("soft", at, bar * (2.0 if i else 1.0), ch, 0.28, bright=1000, tilt=1.6, attack=2.0, release=5.0)
    line = [(2.0, "C5"), (2.5, "E5"), (3.0, "B4"), (3.75, "A4"), (4.0, "Ab4"), (4.5, "C5"), (5.0, "G4"),
            (10.0, "E5"), (10.5, "B4"), (11.0, "C5"), (11.75, "Ab4"), (12.0, "G4")]
    for k, (b, note) in enumerate(line):
        tr.bell("bell", b * bar + tr.rng.uniform(-0.05, 0.05), note, 0.26, decay=3.0, pan=-0.35 + 0.7 * (k % 2))
    rng = tr.rng
    for s in np.sort(rng.uniform(3.0, 23 * bar, 26)):
        tr.bell("drip", float(s), rng.choice(["E6", "G6", "B6", "C7", "A6"]), float(rng.uniform(0.06, 0.16)),
                decay=0.35, pan=float(rng.uniform(-0.8, 0.8)))
    # The end: F and E together, never resolving.
    tr.pad("soft", 16 * bar, 6.0 * bar, "F3", 0.26, bright=900, tilt=1.6, attack=4.0, release=7.0)
    tr.pad("soft", 16.5 * bar, 5.5 * bar, "E3", 0.20, bright=900, tilt=1.6, attack=5.0, release=7.0)
    tr.swell("fx", 16 * bar, "C4", 0.25, length=5.0)
    tr.noise_bed("water", 300, 2500, [(0, 0.0), (8, 0.012), (115, 0.012), (122, 0.0)], drift_amt=0.6)
    tr.noise_bed("room", 25, 120, [(0, 0.0), (8, 0.015), (122, 0.015)], drift_amt=0.2)
    l, r = tr.buses["soft"]
    g = lambda f: lp_gain(f, 1600, 2)
    tr.buses["soft"] = (fft_filter(l, g), fft_filter(r, g))
    L, R = tr.master(mix={"soft": 1.0, "shimmer": 0.45, "bass": 0.6, "bell": 0.5, "drip": 0.3, "drone": 0.45,
                          "fx": 0.5, "water": 1.0, "room": 1.0},
                     reverb={"soft": 0.55, "shimmer": 0.9, "bell": 0.8, "drip": 1.0, "fx": 0.4, "bass": 0.1},
                     room_s=6.5, room_lp=4200)
    _m_save(L, R, SND / "music" / "drift.ogg", fade_out=7.0)


MUSIC_SOUNDS = [snd_music_liminal, snd_music_deep, snd_music_drift]


# =============================================================================
# Surface variants
# =============================================================================
# The big flat surfaces repeat one 128 px texture on every block, so a stain or a crack on it shows
# up in a grid across a whole room. These surfaces get a few extra versions (<name>_v1.png, ...),
# and the world generator picks one for each block, mostly the plain one (block/SurfaceBlock).
# Every version is the same seamless base plus a few local marks that fade out well before the
# block's edges, so any two versions still meet without a seam.

EDGE_MARGIN = 14


def _edge_fade(margin=EDGE_MARGIN) -> np.ndarray:
    """1 in the middle of the block, falling to 0 at its edges."""
    X, Y = grid()
    d = np.minimum(np.minimum(X, S - X), np.minimum(Y, S - Y))
    return smoothstep(2.0, margin, d)


def _spot(rng, rx, ry, y_range=(0.0, 1.0)) -> tuple:
    """A random centre that keeps a mark of radius (rx, ry) clear of the edges.

    y_range (0 = top, 1 = bottom) narrows where it may go vertically (e.g. low on a wall).
    """
    lo_x, hi_x = EDGE_MARGIN + rx, S - EDGE_MARGIN - rx
    lo_y = max(EDGE_MARGIN + ry, S * y_range[0])
    hi_y = min(S - EDGE_MARGIN - ry, S * y_range[1])
    cx = rng.uniform(lo_x, hi_x) if lo_x < hi_x else S / 2
    cy = rng.uniform(lo_y, hi_y) if lo_y < hi_y else (lo_y + hi_y) / 2
    return cx, cy


def _local(cx, cy, sx, sy) -> np.ndarray:
    """A gaussian blob that does NOT wrap around (unlike blob())."""
    X, Y = grid()
    return np.exp(-(((X - cx) / sx) ** 2 + ((Y - cy) / sy) ** 2) / 2)


def mark_stain(img, rng, color, size=(14, 16), a=0.2, y_range=(0.0, 1.0)) -> np.ndarray:
    """A water stain: a noisy blob with a darker tide-line rim."""
    sx, sy = size[0] * rng.uniform(0.75, 1.2), size[1] * rng.uniform(0.75, 1.2)
    cx, cy = _spot(rng, 1.5 * sx, 1.5 * sy, y_range)
    m = _local(cx, cy, sx, sy) * (1 + 0.3 * fnoise(rng, beta=3.2) + 0.06 * fnoise(rng, beta=1.6))
    inner = smoothstep(0.35, 0.6, m)
    rim = np.clip(smoothstep(0.28, 0.36, m) - smoothstep(0.36, 0.48, m), 0, 1)
    return over(img, np.array(color), (a * inner + 1.6 * a * rim) * _edge_fade())


def mark_damp(img, rng, size=(22, 18), dark=0.14, tint=(1.0, 1.0, 1.0), y_range=(0.0, 1.0)) -> np.ndarray:
    """A soft, irregular darker patch: damp, dirt or old spills."""
    sx, sy = size[0] * rng.uniform(0.8, 1.2), size[1] * rng.uniform(0.8, 1.2)
    cx, cy = _spot(rng, 1.4 * sx, 1.4 * sy, y_range)
    m = smoothstep(0.25, 0.75, _local(cx, cy, sx, sy) * (1 + 0.45 * fnoise(rng, beta=3.2))) * _edge_fade()
    return img * (1 - dark * m[..., None] * np.asarray(tint, float))


def mark_drip(img, rng, color, a=0.22, length=(40, 70)) -> np.ndarray:
    """A thin streak running down from a point (rust, water), fading as it goes."""
    X, Y = grid()
    L = rng.uniform(*length)
    cx, y0 = rng.uniform(EDGE_MARGIN + 4, S - EDGE_MARGIN - 4), rng.uniform(EDGE_MARGIN, S - EDGE_MARGIN - L)
    wander = 1.2 * fnoise(rng, (S, 1), beta=2.0)[:, 0]
    w = rng.uniform(1.2, 2.4)
    t = np.clip((Y - y0) / L, 0, 1)
    core = np.exp(-((X - cx - wander[np.clip(Y.astype(int), 0, S - 1)]) / (w * (1 + 0.6 * t))) ** 2)
    m = core * (Y >= y0) * (1 - t) ** 1.4 * (0.8 + 0.2 * fnoise(rng, beta=1.0))
    head = _local(cx, y0 + 2, 3.5, 2.5)
    return over(img, np.array(color), np.clip((m + head) * a, 0, 0.6) * _edge_fade())


def mark_crack(img, rng, depth=30.0, length=(30, 55), branches=2) -> np.ndarray:
    """A thin wandering crack (with a short branch or two), kept inside the block."""
    lo, hi = EDGE_MARGIN + 6, S - EDGE_MARGIN - 6

    def walk(x, y, ang, n):
        pts = [(x, y)]
        for _ in range(n):
            ang += rng.normal(0, 0.45)
            x = float(np.clip(x + 3.0 * np.cos(ang), lo, hi))
            y = float(np.clip(y + 3.0 * np.sin(ang), lo, hi))
            pts.append((x, y))
        return pts

    n = int(rng.uniform(*length) / 3)
    main = walk(rng.uniform(lo, hi), rng.uniform(lo, hi), rng.uniform(0, 2 * np.pi), n)
    lines = [main] + [walk(*main[int(rng.integers(2, len(main) - 2))], rng.uniform(0, 2 * np.pi), n // 3)
                      for _ in range(branches)]

    def draw(d, k):
        for ln in lines:
            d.line([(px * k, py * k) for px, py in ln], fill=255, width=max(1, int(1.1 * k)))

    m = pil_mask(draw) * _edge_fade()
    img = add_lum(img, -depth * m)
    return add_lum(img, -0.25 * depth * blur(m, 1.2))   # a soft dark edge to the crack


def mark_scuffs(img, rng, color, n=4, y_range=(0.72, 0.9), a=0.3) -> np.ndarray:
    """Short horizontal shoe/furniture scuffs low on a wall."""
    X, Y = grid()
    m = np.zeros((S, S))
    for _ in range(n):
        cx, cy = _spot(rng, 10, 2, y_range)
        m = np.maximum(m, _local(cx, cy, rng.uniform(4, 10), rng.uniform(0.6, 1.4)))
    return over(img, np.array(color), np.clip(m * a, 0, 0.5) * _edge_fade())


def mark_grout_grime(img, rng, size=(24, 20), a=0.45) -> np.ndarray:
    """Grey-green grime soaked into the grout lines around one spot (pool tiles)."""
    X, Y = grid()
    g = np.minimum(np.abs(wrapd(X, POOL_TILE)), np.abs(wrapd(Y, POOL_TILE)))
    grout = smoothstep(2.6, 0.8, g)
    cx, cy = _spot(rng, size[0], size[1])
    m = smoothstep(0.2, 0.7, _local(cx, cy, *size) * (1 + 0.4 * fnoise(rng, beta=2.2)))
    img = over(img, np.array([112, 118, 106]), grout * m * a * 0.7 * _edge_fade())
    return over(img, np.array([200, 200, 188]), m * 0.06 * _edge_fade())   # a faint dull film on the glaze


def mark_patch(img, rng, color, size=(24, 18), a=0.55) -> np.ndarray:
    """A rough-edged repair patch (asphalt): smoother and darker than its surroundings."""
    X, Y = grid()
    w, h = size[0] * rng.uniform(0.8, 1.2), size[1] * rng.uniform(0.8, 1.2)
    cx, cy = _spot(rng, w, h)
    edge = np.maximum(np.abs(X - cx) / w, np.abs(Y - cy) / h) + 0.08 * fnoise(rng, beta=1.6)
    m = smoothstep(1.0, 0.92, edge) * _edge_fade()
    patch = lerp(blur(img, 1.0), np.array(color), 0.5)
    img = lerp(img, patch, (m * a)[..., None])
    return add_lum(img, -10 * np.clip(smoothstep(1.06, 0.98, edge) - m, 0, 1))  # the dark seam round it


WALL_STAIN = (150, 118, 58)
CEILING_STAIN = (168, 146, 100)
RUST = (122, 82, 58)


def mark_paper_scuff(img, rng, y_range=(0.45, 0.85)) -> np.ndarray:
    """A patch where something rubbed the wallpaper: the print is worn paler and fuzzy, a few
    short pulled fibres and one small torn edge where the paper lifted."""
    X, Y = grid()
    sx, sy = rng.uniform(14, 20), rng.uniform(8, 12)
    cx, cy = _spot(rng, 1.5 * sx, 1.5 * sy, y_range)
    m = smoothstep(0.25, 0.7, _local(cx, cy, sx, sy) * (1 + 0.5 * fnoise(rng, beta=2.0))) * _edge_fade()
    worn = blur(img, 1.2) + 7.0 * np.array([1.0, 1.0, 0.9])          # paler, the blotches rubbed soft
    img = lerp(img, worn, (0.8 * m)[..., None])
    streaks = np.clip(fnoise(rng, beta=1.0, aniso=(8, 1), kmin=6), 0, None) * m   # horizontal rub marks
    img = add_lum(img, 6 * streaks - 3 * blur(streaks, 1.0))
    tx, ty = cx - sx * 0.4 + rng.uniform(-2, 2), cy + rng.uniform(-3, 3)
    edge = pil_mask(lambda d, k: d.line([(tx * k, ty * k), ((tx + 5) * k, (ty - 2) * k), ((tx + 9) * k, (ty + 1) * k)],
                                        fill=255, width=max(1, int(0.9 * k))))
    img = add_lum(img, 18 * edge)                                    # the lifted paper edge catches the light
    return add_lum(img, -10 * np.roll(edge, 1, axis=0))              # and casts a hairline shadow under it


def mark_paper_seam(img, rng) -> np.ndarray:
    """A faint horizontal joint between two lengths of wallpaper (about every 0.9 m on some
    walls): a hairline light lip over a hairline shadow, the strip above a shade different."""
    X, Y = grid()
    y0 = float(rng.uniform(28, 100))
    img = add_lum(img, 1.6 * (Y < y0) - 0.8 * (Y >= y0))
    img = add_lum(img, 7 * np.exp(-((Y - y0 + 0.6) / 0.6) ** 2) - 8 * np.exp(-((Y - y0 - 0.7) / 0.6) ** 2))
    return add_lum(img, 2.5 * fnoise(rng, beta=1.5, aniso=(8, 1), kmin=4) * np.exp(-((Y - y0) / 1.5) ** 2))


def _wallpaper_marks(i, rng, img):
    """The paper ages faintly: a subtle water stain, a scuffed patch, a faint horizontal seam.
    The marks never move the print, so neighbouring blocks still line up."""
    if i == 1:
        return mark_stain(img, rng, WALL_STAIN, (18, 22), 0.1)
    if i == 2:
        return mark_paper_scuff(img, rng)
    return mark_paper_seam(img, rng)


def mark_carpet_seam(img, rng) -> np.ndarray:
    """A straight broadloom seam across the block (runs along x): the loops either side lean
    away, leaving a thin dark groove with a slightly lighter ridge on each side."""
    X, Y = grid()
    y0 = float(rng.uniform(36, 92))
    wob = 0.4 * fnoise(rng, beta=2.0, aniso=(6, 1), kmin=2)
    t = Y - y0 - wob
    return add_lum(img, -16 * np.exp(-(t / 0.7) ** 2) + 5 * np.exp(-((np.abs(t) - 2.0) / 0.9) ** 2))


def mark_carpet_worn(img, rng) -> np.ndarray:
    """A traffic-worn lane: the pile crushed flat, so the loops show less and it looks a little
    paler and greyer (a soft band across the block, along x)."""
    X, Y = grid()
    y0, w = float(rng.uniform(44, 84)), float(rng.uniform(16, 26))
    m = np.exp(-((Y - y0 + 4 * fnoise(rng, beta=2.0, aniso=(4, 1), kmin=1)) / w) ** 2)
    m = m * (0.7 + 0.3 * smoothstep(-1, 1, fnoise(rng, beta=2.0)))
    flat = blur(img, 1.0) + 5.0 * np.array([1.0, 1.0, 1.08])
    return lerp(img, flat, (0.7 * m)[..., None])


def _carpet_marks(i, rng, img):
    if i == 1:
        return mark_carpet_seam(img, rng)
    if i == 2:
        return mark_carpet_worn(img, rng)
    return mark_damp(mark_stain(img, rng, (104, 82, 36), (9, 8), 0.32), rng, (16, 12), 0.08)   # a rare old spill


def mark_tile_sag(img, rng) -> np.ndarray:
    """A ceiling tile that has sagged a little in its grid: the middle drops into shadow, a soft
    lighter crease near the edge where it bends."""
    X, Y = grid()
    cx, cy = rng.uniform(52, 76), rng.uniform(52, 76)
    r = np.hypot((X - cx) / 60, (Y - cy) / 54)
    img = add_lum(img, -14 * np.clip(1 - r, 0, 1) ** 1.5)
    d = np.minimum(np.minimum(X, S - X), np.minimum(Y, S - Y))
    return add_lum(img, 4 * np.exp(-((d - 12) / 4) ** 2) * smoothstep(3, 6, d))


def _ceiling_marks(i, rng, img):
    if i == 1:
        return mark_stain(img, rng, CEILING_STAIN, (16, 13), 0.3)             # water-stained ring
    if i == 2:
        return mark_tile_sag(img, rng)
    return mark_damp(img, rng, (34, 30), 0.06, (1.0, 0.96, 0.8))             # faintly yellowed


def _l0w():
    """The film's chevron paper lives in anomaly_assets/level0_revamp.py (imported lazily: it imports this file)."""
    import anomaly_assets.level0_revamp as m
    return m


def _l39w():
    """Level 39 world textures live in anomaly_assets/level39.py (imported lazily: it imports this file)."""
    import anomaly_assets.level39 as m
    return m


# name: (base image, marks for version i (1..n-1), n = number of versions incl. the plain one).
# n must match the number of weights the block gets in ModBlocks (block/SurfaceBlock), which also
# decides how often each version is used.
SURFACE_VARIANTS = {
    "yellow_wallpaper": (lambda: _wallpaper(stains=False), _wallpaper_marks, 4),
    "lobby_carpet": (_carpet, _carpet_marks, 4),
    "ceiling_tile": (lambda: _ceiling_tile(stain=False), _ceiling_marks, 4),
    "l0_wallpaper_chevron": (lambda: _l0w().chevron_base(), lambda i, rng, img: _l0w().chevron_marks(i, rng, img), 3),
    "concrete_wall": (_concrete_wall, lambda i, rng, img: (
        mark_drip(mark_damp(img, rng, (20, 26), 0.12), rng, (84, 80, 88), 0.25) if i == 1 else
        mark_crack(img, rng, 26)), 3),
    "concrete_floor": (lambda: _garage_floor(rng_for(107), (72, 64, 78)), lambda i, rng, img: (
        mark_damp(img, rng, (20, 14), 0.3, (1.0, 0.95, 1.05)) if i == 1 else
        mark_crack(img, rng, 18) if i == 2 else
        mark_damp(mark_damp(img, rng, (9, 8), 0.3), rng, (24, 20), 0.1)), 4),
    "pipe_wall": (_pipe_wall, lambda i, rng, img: (
        mark_drip(img, rng, RUST, 0.3) if i == 1 else
        mark_damp(img, rng, (22, 18), 0.1, (1.0, 1.0, 0.9))), 3),
    "pipe_floor": (_pipe_floor, lambda i, rng, img: (
        mark_stain(img, rng, RUST, (14, 12), 0.25) if i == 1 else
        mark_damp(img, rng, (24, 18), 0.16)), 3),
    "pool_tile": (lambda: _pool_tiles(140, POOL_WHITE, POOL_GROUT), lambda i, rng, img: (
        mark_grout_grime(img, rng) if i == 1 else
        mark_crack(img, rng, 22, (16, 26), 1)), 3),
    "office_carpet": (lambda: _l4_carpet(4101, L4_CARPET), lambda i, rng, img: (
        mark_stain(img, rng, (92, 70, 46), (9, 8), 0.4) if i == 1 else
        mark_damp(img, rng, (24, 20), 0.15)), 3),
    "office_wall": (lambda: _l4_drywall(4103), lambda i, rng, img: (
        mark_scuffs(img, rng, (120, 112, 100), 5) if i == 1 else
        mark_stain(img, rng, (170, 150, 112), (16, 18), 0.18, y_range=(0.0, 0.6))), 3),
    "asphalt": (lambda: _asphalt(901), lambda i, rng, img: (
        mark_crack(img, rng, 28, (40, 60), 3) if i == 1 else
        mark_patch(img, rng, (44, 44, 48)) if i == 2 else
        mark_damp(img, rng, (18, 14), 0.3, (1.0, 0.97, 0.9))), 4),
    "l39_yard_grass": (lambda: _l39w().yard_grass_base(), lambda i, rng, img: _l39w().yard_grass_marks(i, rng, img), 4),
    "l39_yard_dirt": (lambda: _l39w().yard_dirt_base(), lambda i, rng, img: _l39w().yard_dirt_marks(i, rng, img), 3),
    "l39_path_dirt": (lambda: _l39w().path_dirt_base(), lambda i, rng, img: _l39w().path_dirt_marks(i, rng, img), 4),
    "l39_path_gravel": (lambda: _l39w().path_gravel_base(), lambda i, rng, img: _l39w().path_gravel_marks(i, rng, img), 3),
    "l39_clapboard": (lambda: _l39w().clapboard_base(), lambda i, rng, img: _l39w().clapboard_marks(i, rng, img), 4),
    "l39_mill_brick": (lambda: _l39w().mill_brick_base(), lambda i, rng, img: _l39w().mill_brick_marks(i, rng, img), 4),
    "l39_marble": (lambda: _l39w().marble_base(), lambda i, rng, img: _l39w().marble_marks(i, rng, img), 4),
}


def _variant_seed(name: str, i: int) -> int:
    import zlib
    return zlib.crc32(name.encode()) + 7919 * i


def surface_variant_images(name: str) -> list:
    """[variant 1, variant 2, ...] for one surface (variant 0 is the plain texture)."""
    base_fn, marks, count = SURFACE_VARIANTS[name]
    base = base_fn()
    return [marks(i, rng_for(_variant_seed(name, i)), base.copy()) for i in range(1, count)]


def tex_surface_variants() -> None:
    for name in SURFACE_VARIANTS:
        for i, img in enumerate(surface_variant_images(name), start=1):
            save_png(img, _blk(f"{name}_v{i}"))
    # The wallpapers with their baseboard follow the wallpaper's variants (same marks above the board).
    for paper in ("yellow_wallpaper", "l0_wallpaper_chevron"):
        for i, img in enumerate(surface_variant_images(paper), start=1):
            save_png(_baseboard(img), _blk(f"{paper}_baseboard_v{i}"))


def variant_models() -> None:
    """Block models for the versions (copies of the plain model) and blockstates by VARIANT.

    SurfaceBlock's VARIANT runs 0..3 for every surface; numbers past a block's last version show
    the plain model (the world generator never picks them).
    """
    import json
    counts = {n: c for n, (_, _, c) in SURFACE_VARIANTS.items()}
    counts["yellow_wallpaper_baseboard"] = SURFACE_VARIANTS["yellow_wallpaper"][2]
    counts["l0_wallpaper_chevron_baseboard"] = SURFACE_VARIANTS["l0_wallpaper_chevron"][2]
    for name, count in counts.items():
        plain = json.loads((ROOT / "models" / "block" / f"{name}.json").read_text())
        variants = {}
        for i in range(4):
            model = name if i == 0 or i >= count else f"{name}_v{i}"
            variants[f"variant={i}"] = {"model": f"backrooms:block/{model}"}
            if 0 < i < count:
                version = dict(plain)
                version["textures"] = {k: (f"backrooms:block/{model}" if v == f"backrooms:block/{name}" else v)
                                       for k, v in plain["textures"].items()}
                (ROOT / "models" / "block" / f"{model}.json").write_text(json.dumps(version, indent=2) + "\n", newline="\n")
        (ROOT / "blockstates" / f"{name}.json").write_text(json.dumps({"variants": variants}, indent=2) + "\n", newline="\n")


# ------------------------------------------------------- Distant ambience ---
# One-off sounds heard far away, a few per level, so long sessions don't loop audibly. The client
# (client/audio/DistantAmbience) picks one every 18-55 s and places it 14-40 blocks off in a random
# direction. Every sound here is "dry plus a little air": low-passed the way distance and walls do,
# with a short faint early tail but NO big room reverb, because the game adds its own room reverb
# at runtime and a baked-in one would double up. Three takes of each, so repeats differ.
# Files: sounds/ambient/distant/<level>/<name><1..3>.ogg (events distant.<level>.<name>).

DIST = SND / "ambient" / "distant"


def _da_env(n, pts) -> np.ndarray:
    """Piecewise-linear envelope over n samples: pts = [(seconds, level), ...]."""
    ts, vs = zip(*pts)
    return np.interp(np.arange(n) / SR, ts, vs)


def _da_band(rng, n, lo, hi, order=2) -> np.ndarray:
    """Band-limited noise, unit peak."""
    return norm(fft_filter(rng.normal(size=n), lambda f: bp_gain(f, lo, hi, order)))


def _da_hit(rng, freqs, decays, amps, noise_ms=3.0, noise_lp=2500.0) -> np.ndarray:
    """An impact: decaying resonances (the struck object) plus a short noise click (the strike)."""
    k = norm(ring(freqs, decays, amps, max(decays) * 6))
    m = len(k)
    click = rng.normal(size=m) * np.exp(-np.arange(m) / (noise_ms * 1e-3 * SR))
    return k + 0.5 * norm(fft_filter(click, lambda f: lp_gain(f, noise_lp, 2)))


def _da_chirp(f0, f1, dur, decay=None) -> np.ndarray:
    """A decaying sine gliding from f0 to f1: the sound of a bubble or a drip."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = f0 * (f1 / f0) ** (t / dur)
    env = np.exp(-t / (decay or dur / 3)) * np.clip(t / 0.002, 0, 1)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * env


def _da_stickslip(rng, dur, rate_pts, freqs, decays, amps) -> np.ndarray:
    """Friction (creaks, groans, scrapes): tiny catches at a wandering rate (rate_pts = [(s, Hz)])
    exciting the resonances of whatever is rubbing."""
    n = int(dur * SR)
    ts, rs = zip(*rate_pts)
    rate = np.interp(np.arange(n) / SR, ts, rs) * (1 + smooth_jitter(rng, n, 6, 0.3))
    ph = np.cumsum(rate) / SR
    pulses = (np.diff(np.floor(ph), prepend=0) > 0) * rng.uniform(0.4, 1.0, n)
    return conv(pulses, ring(freqs, decays, amps, max(decays) * 5))[:n]


def _da_far(x, rng, lp, hp=60.0, air=0.15, room=0.3) -> np.ndarray:
    """Distance: low-pass (air and walls swallow the highs), trim the sub-bass, add a little air.
    The 'air' is a short, faint early tail (room seconds long), not a reverb."""
    x = np.concatenate([x, np.zeros(int(room * SR))])
    x = fft_filter(x, lambda f: lp_gain(f, lp, 2) * hp_gain(f, hp, 2))
    if air > 0:
        wet = conv(x, reverb_ir(rng, room, room / 5, lp=lp, predelay=0.01))[:len(x)]
        x = x + air * norm(wet) * np.abs(x).max()
    return x


def _da_save(x, level, name, i, loud=1.0) -> None:
    """Write ambient/distant/<level>/<name><i>.ogg at a common loudness.

    Normalising the peak would make a lone click as loud as a long rumble. Instead the loudest
    0.3 s of every sound is brought to the same level (times loud), so the in-game volumes in
    DistantAmbience compare like with like. The peak stays below clipping."""
    x = np.asarray(x, float)
    x = x - x.mean()
    w = int(0.3 * SR)
    c = np.concatenate([[0.0], np.cumsum(x * x)])
    rms = np.sqrt((c[w:] - c[:-w]).max() / w) if len(x) > w else np.sqrt((x * x).mean())
    peak = min(0.89, np.abs(x).max() * 0.12 * loud / (rms + 1e-12))
    save_ogg(x, DIST / level / f"{name}{i}.ogg", peak=peak, fade_in=0.002, fade_out=0.12)


def _da_voice(rng, f0a, f0b, dur, formants, nh=30) -> np.ndarray:
    """One syllable of a far-off voice: a pitch glide through fixed formants, with some breath."""
    n = int(dur * SR)
    f0 = np.geomspace(f0a, f0b, n) * (1 + smooth_jitter(rng, n, 5, 0.02))
    v = norm(voice(f0, formants, rng, nh=nh)) + 0.12 * _da_band(rng, n, 500, 3000)
    return v * env_adsr(n, 0.03, 0.08)


def snd_dist_amb_level0() -> None:
    """Level 0 (https://backrooms-wiki.wikidot.com/level-0): the lights' buzz "can fluctuate severely
    and randomly" (a far tube pops and stutters out); "scratching sounds from behind the walls, as if
    something is sharpening its nails"; the "Peripheral Shift" rearranging halls out of sight (a deep
    settle); fluids soaking the carpet (dull drips); "familiar voices calling out" (a muffled call)."""
    for i in range(1, 4):
        # A ballast pops somewhere: a sharp tock, one to three stuttering buzz flares, a dying hum.
        rng = rng_for(9600 + i)
        x = np.zeros(int(1.6 * SR))
        add_at(x, _da_hit(rng, [rng.uniform(150, 210), rng.uniform(900, 1300), rng.uniform(2600, 3600)],
                          [0.02, 0.012, 0.02], [1, 0.4, 0.35], noise_ms=1.2, noise_lp=6000), 0.02, False)
        tt = 0.02 + rng.uniform(0.05, 0.12)
        for _ in range(i):
            m = int(rng.uniform(0.05, 0.16) * SR)
            hum, rasp = _mains_buzz(2 * np.pi * 60 * np.arange(m) / SR)
            add_at(x, (0.5 * hum + 0.6 * rasp) * env_adsr(m, 0.004, 0.02) * rng.uniform(0.35, 0.7), tt, False)
            tt += m / SR + rng.uniform(0.04, 0.15)
        m = int(0.6 * SR)
        hum, _ = _mains_buzz(2 * np.pi * 60 * np.arange(m) / SR)
        add_at(x, 0.25 * hum * np.exp(-np.arange(m) / (0.15 * SR)), tt, False)
        _da_save(_da_far(x, rng, 2200, hp=90), "level0", "ballast_pop", i)

        # Scratching behind the wall: short gritty strokes, heard through plaster.
        rng = rng_for(9610 + i)
        dur = 3.2
        n = int(dur * SR)
        x = np.zeros(n)
        tt = 0.05
        while tt < dur - 0.7:
            sd = rng.uniform(0.25, 0.6)
            grit = _da_stickslip(rng, sd, [(0, rng.uniform(90, 160)), (sd, rng.uniform(200, 320))],
                                 [rng.uniform(1400, 2200), rng.uniform(2800, 3800)], [0.002, 0.0015], [1, 0.6])
            m = len(grit)
            stroke = (norm(grit) + 0.3 * _da_band(rng, m, 1200, 4000)) * _da_env(m, [(0, 0), (0.04, 1), (sd * 0.7, 0.8), (sd, 0)])
            add_at(x, stroke * rng.uniform(0.6, 1.0), tt, False)
            tt += sd + rng.uniform(0.08, 0.35)
        wall = ring([rng.uniform(180, 240), rng.uniform(420, 520)], [0.01, 0.006], [1, 0.6], 0.06)
        x = x + 0.6 * norm(conv(x, wall)[:n]) * np.abs(x).max()
        _da_save(_da_far(x, rng, 1300, hp=150, air=0.1), "level0", "wall_scratch", i, loud=0.8)

        # The halls shifting out of sight: a deep muffled thump, a long low groan, ceiling tiles ticking.
        rng = rng_for(9620 + i)
        x = np.zeros(int(4.2 * SR))
        th = norm(ring([rng.uniform(48, 62), rng.uniform(95, 120)], [0.25, 0.12], [1, 0.5], 1.2))
        th += 0.4 * norm(fft_filter(rng.normal(size=len(th)) * np.exp(-np.arange(len(th)) / (0.03 * SR)),
                                    lambda f: lp_gain(f, 250, 2)))
        add_at(x, th, 0.05, False)
        gd = rng.uniform(2.0, 2.8)
        groan = _da_stickslip(rng, gd, [(0, 10), (gd * 0.4, rng.uniform(18, 30)), (gd, 8)],
                              [rng.uniform(90, 120), rng.uniform(170, 220), rng.uniform(300, 380)],
                              [0.05, 0.035, 0.02], [1, 0.7, 0.4])
        add_at(x, 0.7 * norm(groan) * _da_env(len(groan), [(0, 0), (0.4, 1), (gd - 0.6, 0.8), (gd, 0)]),
               0.3 + rng.uniform(0, 0.3), False)
        add_at(x, 0.25 * norm(clicks(rng, np.sort(rng.uniform(0.15, 0.9, 6 + 3 * i)), 1.2, 900, 2200, amp_pow=2.0)),
               0.05, False)
        _da_save(_da_far(x, rng, 750, hp=35, air=0.12, room=0.4), "level0", "layout_shift", i)

        # Drips falling onto the soaked carpet: dull, wet pats.
        rng = rng_for(9630 + i)
        dur = 3.0
        x = np.zeros(int(dur * SR))
        tt = 0.05
        for _ in range(3 + i):
            m = int(0.12 * SR)
            pat = norm(ring([rng.uniform(140, 220), rng.uniform(380, 520)], [0.012, 0.006], [1, 0.4], 0.12))
            wet = 0.6 * _da_band(rng, m, 400, 1400) * np.exp(-np.arange(m) / (0.02 * SR))
            add_at(x, (pat + wet) * rng.uniform(0.5, 1.0), tt, False)
            tt += rng.uniform(0.35, 0.8)
            if tt > dur - 0.3:
                break
        _da_save(_da_far(x, rng, 1500, hp=90, air=0.1), "level0", "carpet_drip", i, loud=0.7)

        # A voice calling, far off and through many walls: never clear enough to make out.
        rng = rng_for(9640 + i)
        e_vowel = [(550, 90, 1.0), (1800, 160, 0.5), (2500, 200, 0.2)]
        o_vowel = [(480, 80, 1.0), (850, 120, 0.7), (2400, 200, 0.1)]
        a_vowel = [(700, 100, 1.0), (1200, 140, 0.6), (2500, 200, 0.15)]
        if i == 1:      # "hel-lo?"
            sylls = [(0.05, 190, 200, 0.16, e_vowel), (0.25, 185, 250, 0.34, o_vowel)]
        elif i == 2:    # "hey?" ... "hey?"
            sylls = [(0.05, 230, 215, 0.3, e_vowel), (1.0, 215, 245, 0.32, e_vowel)]
        else:           # a lower voice, two syllables, like a name
            sylls = [(0.05, 135, 150, 0.2, a_vowel), (0.3, 150, 120, 0.42, o_vowel)]
        x = np.zeros(int(2.0 * SR))
        for k, (start, fa, fb, sd, formants) in enumerate(sylls):
            add_at(x, _da_voice(rng, fa, fb, sd, formants) * (0.6 if i == 2 and k == 1 else 1.0), start, False)
        _da_save(_da_far(x, rng, 850, hp=180, air=0.2, room=0.35), "level0", "far_call", i, loud=0.75)


def snd_dist_amb_level1() -> None:
    """Level 1 (https://backrooms-wiki.wikidot.com/level-1): "random clangs echoed through the pipes
    on the ceiling" and "the intermittent rumbling through the pipes above"; a warehouse's metal
    shutters; "if you feel or hear the walls, ceiling or roof vibrate, leave the area in silence";
    and a door scraped by claws in the dark."""
    for i in range(1, 4):
        # A clang in the ceiling pipes, carried along the run as a few fainter repeats.
        rng = rng_for(9700 + i)
        x = np.zeros(int(3.0 * SR))
        f1 = rng.uniform(260, 420)
        for k in range(1 if i == 2 else 2):
            hit = _da_hit(rng, [f1, f1 * 2.76, f1 * 5.40, f1 * 8.93], [0.9, 0.5, 0.3, 0.15], [1, 0.7, 0.4, 0.2],
                          noise_ms=2, noise_lp=5000)
            add_at(x, hit * (1.0 if k == 0 else 0.5), 0.03 + k * rng.uniform(0.18, 0.4), False)
        y = x.copy()
        for k, d in enumerate(np.sort(rng.uniform(0.07, 0.25, 3)), 1):
            add_at(y, x * 0.35 / k, d, False)
        _da_save(_da_far(y, rng, 2400, hp=120), "level1", "pipe_clang", i)

        # Water rushing through the pipes overhead, with one knock as the flow catches.
        rng = rng_for(9710 + i)
        dur = 4.0 + 0.4 * i
        n = int(dur * SR)
        x = _da_band(rng, n, 70, 500) * (1 + 0.6 * smooth_jitter(rng, n, 9, 1.0))
        x *= _da_env(n, [(0, 0), (dur * 0.35, 1), (dur * 0.6, 0.8), (dur, 0)])
        add_at(x, 0.8 * _da_hit(rng, [rng.uniform(110, 150), rng.uniform(300, 380)], [0.08, 0.05], [1, 0.5]),
               dur * rng.uniform(0.3, 0.6), False)
        _da_save(_da_far(x, rng, 900, hp=40), "level1", "pipe_rumble", i)

        # A roll-up metal shutter shaken in its frame: bursts of slats knocking together.
        rng = rng_for(9720 + i)
        dur = 2.4
        x = np.zeros(int(dur * SR))
        tt = 0.05
        for _ in range(2 + (i == 3)):
            bd = rng.uniform(0.4, 0.9)
            rate = rng.uniform(22, 34)
            u = 0.0
            while u < bd:
                h = _da_hit(rng, [rng.uniform(380, 520), rng.uniform(900, 1200), rng.uniform(1700, 2300)],
                            [0.03, 0.02, 0.012], [1, 0.6, 0.4], noise_ms=1.5, noise_lp=4000)
                add_at(x, h * np.sin(np.pi * u / bd) * rng.uniform(0.4, 1.0), tt + u, False)
                u += 1 / (rate * rng.uniform(0.7, 1.3))
            tt += bd + rng.uniform(0.15, 0.4)
            if tt > dur - 0.5:
                break
        _da_save(_da_far(x, rng, 2300, hp=120), "level1", "shutter_rattle", i, loud=0.9)

        # The ceiling vibrating: a low shake with loose sheet metal buzzing on each swing.
        rng = rng_for(9730 + i)
        dur = 3.5
        n = int(dur * SR)
        f0 = rng.uniform(42, 58) * (1 + smooth_jitter(rng, n, 2, 0.03))
        ph = 2 * np.pi * np.cumsum(f0) / SR
        drone = np.sin(ph) + 0.5 * np.sin(2 * ph)
        buzz = fft_filter(np.clip(np.sin(ph) * 3 - 2, 0, None), lambda f: bp_gain(f, 250, 1200, 2))
        x = (norm(drone) + 0.5 * norm(buzz)) * _da_env(n, [(0, 0), (1.2, 1), (2.2, 0.9), (dur, 0)])
        _da_save(_da_far(x, rng, 900, hp=30), "level1", "ceiling_vibrate", i, loud=0.9)

        # Claws dragged down a steel door, somewhere in the dark.
        rng = rng_for(9740 + i)
        dur = 2.6
        n = int(dur * SR)
        x = np.zeros(n)
        tt = 0.05
        for _ in range(2 + (i == 3)):
            sd = rng.uniform(0.35, 0.7)
            g = _da_stickslip(rng, sd, [(0, rng.uniform(150, 250)), (sd * 0.5, rng.uniform(300, 450)), (sd, rng.uniform(120, 200))],
                              [rng.uniform(700, 900), rng.uniform(1500, 1900), rng.uniform(2600, 3200)],
                              [0.01, 0.006, 0.004], [1, 0.7, 0.4])
            add_at(x, norm(g) * _da_env(len(g), [(0, 0), (0.05, 1), (sd * 0.8, 0.7), (sd, 0)]) * rng.uniform(0.6, 1.0), tt, False)
            tt += sd + rng.uniform(0.2, 0.5)
            if tt > dur - 0.4:
                break
        door = ring([rng.uniform(140, 180), rng.uniform(330, 400)], [0.15, 0.1], [1, 0.6], 0.6)
        x = x + 0.3 * norm(conv(x, door)[:n]) * np.abs(x).max()
        _da_save(_da_far(x, rng, 1800, hp=120), "level1", "door_scrape", i, loud=0.75)


def snd_dist_amb_level2() -> None:
    """Level 2 (https://backrooms-wiki.wikidot.com/level-2): "pipes transferring fluids/gasses" (water
    hammer knocks, a pipe groaning under pressure); "quiet internal whirring noises, as if the
    machines are faulty"; the B.N.T.G.'s narrow-gauge tramway rumbling behind the walls; "a few of
    the fluorescents are known to have burst"."""
    for i in range(1, 4):
        # Water hammer: a run of knocks travelling away down a pipe.
        rng = rng_for(9800 + i)
        x = np.zeros(int(2.8 * SR))
        f1 = rng.uniform(170, 240)
        tt, amp, gap = 0.04, 1.0, rng.uniform(0.12, 0.2)
        for _ in range(3 + i):
            h = _da_hit(rng, [f1 * rng.uniform(0.97, 1.03), f1 * 2.7, f1 * 5.1, rng.uniform(60, 80)],
                        [0.12, 0.07, 0.04, 0.06], [1, 0.5, 0.25, 0.7], noise_ms=2.5, noise_lp=3000)
            add_at(x, h * amp, tt, False)
            tt += gap * rng.uniform(0.8, 1.4)
            amp *= rng.uniform(0.55, 0.8)
        _da_save(_da_far(x, rng, 1600, hp=50), "level2", "pipe_knock", i)

        # A pipe groaning under pressure: a slow, rough low tone sinking in pitch.
        rng = rng_for(9810 + i)
        dur = 3.0 + 0.5 * i
        n = int(dur * SR)
        f0 = np.geomspace(rng.uniform(52, 64), rng.uniform(40, 48), n) * (1 + smooth_jitter(rng, n, 3, 0.04))
        v = voice(f0, [(rng.uniform(200, 260), 60, 1.0), (rng.uniform(480, 560), 90, 0.6), (rng.uniform(1000, 1200), 160, 0.3)],
                  rng, nh=40, tilt=0.02)
        x = norm(v) * (1 + 0.35 * smooth_jitter(rng, n, 25, 1.0))
        x *= _da_env(n, [(0, 0), (0.6, 0.8), (dur * 0.5, 1), (dur - 0.5, 0.6), (dur, 0)])
        _da_save(_da_far(x, rng, 1100, hp=35), "level2", "pressure_groan", i)

        # A faulty machine spins up, sags and catches, then winds down with a clunk.
        rng = rng_for(9820 + i)
        dur = 4.0
        n = int(dur * SR)
        top = rng.uniform(70, 110)
        up = rng.uniform(1.0, 1.6)
        hold = up + rng.uniform(0.3, 0.8)
        rot = _da_env(n, [(0, 6), (up, top), (hold, top * rng.uniform(0.85, 0.95)), (dur, 4)])
        rot *= 1 - 0.12 * np.clip(smooth_jitter(rng, n, 4, 1.0), 0, None)
        ph = 2 * np.pi * np.cumsum(rot) / SR
        motor = sum(a * np.sin(k * ph) for k, a in ((1, 1.0), (2, 0.6), (3, 0.3), (4, 0.2)))
        whine = 0.25 * np.sin(int(rng.integers(9, 14)) * ph)
        env = _da_env(n, [(0, 0), (0.3, 0.6), (up, 1), (hold, 1), (dur, 0)])
        x = (motor + whine) * env + 0.15 * _da_band(rng, n, 300, 2000) * env
        add_at(x, 0.8 * _da_hit(rng, [rng.uniform(80, 100), rng.uniform(180, 220)], [0.06, 0.04], [1, 0.5]),
               hold + rng.uniform(0.1, 0.4), False)
        _da_save(_da_far(x, rng, 1700, hp=40), "level2", "machine_whir", i)

        # A tram passing far behind the walls: a swelling rumble, bogies clacking over rail joints.
        rng = rng_for(9830 + i)
        dur = 5.5
        n = int(dur * SR)
        env = _da_env(n, [(0, 0), (dur * 0.45, 1), (dur * 0.6, 0.9), (dur, 0)])
        x = _da_band(rng, n, 35, 220) * (1 + 0.3 * smooth_jitter(rng, n, 8, 1.0)) * env
        period = rng.uniform(0.55, 0.75)
        tt = rng.uniform(0, 0.3)
        while tt < dur - 0.2:
            a = env[int(tt * SR)]
            for dt in (0.0, rng.uniform(0.09, 0.13)):
                h = _da_hit(rng, [rng.uniform(250, 330), rng.uniform(700, 900), rng.uniform(1600, 2000)],
                            [0.04, 0.025, 0.015], [1, 0.6, 0.3], noise_ms=2, noise_lp=3000)
                add_at(x, 0.9 * h * a, tt + dt, False)
            tt += period
        _da_save(_da_far(x, rng, 1000, hp=30, air=0.15, room=0.4), "level2", "tram_rumble", i)

        # A fluorescent tube bursting: a dull thup, then glass shards tinkling and thinning out.
        rng = rng_for(9840 + i)
        dur = 1.8
        x = np.zeros(int(dur * SR))
        add_at(x, _da_hit(rng, [rng.uniform(90, 130), rng.uniform(240, 300)], [0.03, 0.02], [1, 0.5],
                          noise_ms=1.5, noise_lp=8000), 0.03, False)
        for tm in 0.04 + np.minimum(rng.exponential(0.12, 25 + 10 * i), dur - 0.4):
            f = rng.uniform(2500, 6500)
            shard = ring([f, f * rng.uniform(1.3, 1.8)], [rng.uniform(0.01, 0.04), 0.01], [1, 0.4], 0.2)
            add_at(x, shard * 0.4 * rng.uniform(0.2, 1.0) * np.exp(-(tm - 0.04) / 0.25), tm, False)
        _da_save(_da_far(x, rng, 3500, hp=150), "level2", "tube_burst", i, loud=0.8)


def snd_dist_amb_level4() -> None:
    """Level 4 (https://backrooms-wiki.wikidot.com/level-4): an abandoned office with "water coolers,
    vending machines, and fountains containing Almond Water", "office-style stairways or occasional
    elevators", and power still on. A desk phone ringing, a vending machine's compressor, a drinking
    fountain running, an elevator stopping in its shaft, a copier warming up: nobody in sight."""
    for i in range(1, 4):
        # A desk phone ringing once (take 2: and starting a second ring; take 3: cut short).
        rng = rng_for(9900 + i)
        fa, fb = [(1020, 1280), (880, 1100), (1150, 1450)][i - 1]
        trill = rng.uniform(16, 20)

        def ringburst(sec):
            m = int(sec * SR)
            t = np.arange(m) / SR
            ph = 2 * np.pi * np.cumsum(np.where(np.sin(2 * np.pi * trill * t) > 0, fa, fb)) / SR
            tone = fft_filter(np.sin(ph) + 0.4 * np.sign(np.sin(ph)), lambda f: bp_gain(f, 400, 3500, 2))
            return tone * env_adsr(m, 0.01, 0.03)
        x = np.zeros(int(4.0 * SR))
        add_at(x, ringburst(0.7 if i == 3 else 1.2), 0.05, False)
        if i == 2:
            add_at(x, ringburst(0.35), 3.25, False)
        _da_save(_da_far(x, rng, 2400, hp=300), "level4", "phone_ring", i, loud=0.75)

        # A vending machine's compressor: kicking on with a shudder (takes 1, 3) or shutting off (take 2).
        rng = rng_for(9910 + i)
        dur = 3.5
        n = int(dur * SR)
        t = np.arange(n) / SR
        ph = 2 * np.pi * 60 * t
        hum = np.sin(ph) + 0.6 * np.sin(2 * ph) + 0.25 * np.sin(3 * ph) + 0.15 * np.sin(4 * ph)
        relay = _da_hit(rng, [rng.uniform(70, 90), rng.uniform(400, 600)], [0.05, 0.01], [1, 0.4], noise_ms=1.5, noise_lp=3000)
        if i != 2:
            hum *= 1 + 0.4 * np.sin(2 * np.pi * rng.uniform(5, 8) * t) * np.exp(-t / 0.5)
            x = hum * _da_env(n, [(0, 0), (0.1, 0), (0.25, 1), (dur - 1.2, 0.8), (dur, 0)])
            add_at(x, 0.8 * relay, 0.05, False)
            stop = 0.3
        else:
            x = hum * _da_env(n, [(0, 0), (0.6, 0.8), (1.6, 0.8), (1.75, 0)])
            add_at(x, 0.8 * relay, 1.6, False)
            stop = 1.7
        add_at(x, 0.3 * norm(clicks(rng, np.sort(rng.uniform(0.0, 0.5, 5)), 0.8, 600, 1500)), stop, False)
        _da_save(_da_far(x, rng, 800, hp=40), "level4", "vending_compressor", i)

        # A drinking fountain: valve click, a thin stream on steel, the drain swallowing the last of it.
        rng = rng_for(9920 + i)
        run = rng.uniform(1.3, 2.0)
        x = np.zeros(int(3.2 * SR))
        valve = _da_hit(rng, [rng.uniform(1800, 2400), 600], [0.006, 0.01], [1, 0.5], noise_ms=1, noise_lp=6000)
        add_at(x, 0.5 * valve, 0.05, False)
        m = int(run * SR)
        stream = _da_band(rng, m, 900, 4500) * (1 + 0.3 * smooth_jitter(rng, m, 30, 1.0))
        basin = ring([rng.uniform(700, 900), rng.uniform(1500, 1800)], [0.03, 0.02], [1, 0.6], 0.15)
        stream = (stream + 0.4 * norm(conv(stream, basin)[:m])) * env_adsr(m, 0.08, 0.15)
        add_at(x, 0.7 * stream, 0.08, False)
        add_at(x, 0.4 * valve, 0.08 + run, False)
        for _ in range(4 + i):
            add_at(x, 0.4 * _da_chirp(rng.uniform(250, 400), rng.uniform(500, 800), 0.05),
                   0.08 + run + rng.uniform(0, 0.6), False)
        _da_save(_da_far(x, rng, 2400, hp=150), "level4", "fountain_run", i, loud=0.75)

        # An elevator stopping somewhere in its shaft: motor hum, brake clunk, the cables creaking.
        rng = rng_for(9930 + i)
        dur = 3.4
        n = int(dur * SR)
        t = np.arange(n) / SR
        t_stop = rng.uniform(1.2, 1.6)
        ph = 2 * np.pi * 60 * t
        motor = np.sin(2 * ph) + 0.5 * np.sin(4 * ph) + 0.3 * np.sin(6 * ph) + 0.4 * np.sin(ph)
        x = 0.5 * motor * _da_env(n, [(0, 0), (0.3, 1), (t_stop - 0.4, 1), (t_stop, 0), (dur, 0)])
        add_at(x, _da_hit(rng, [rng.uniform(55, 70), rng.uniform(140, 180), rng.uniform(420, 500)],
                          [0.2, 0.1, 0.05], [1, 0.6, 0.3], noise_ms=3, noise_lp=1500), t_stop, False)
        creak = _da_stickslip(rng, 0.8, [(0, 12), (0.4, 25), (0.8, 9)], [300, 650, 1100], [0.03, 0.02, 0.01], [1, 0.6, 0.3])
        add_at(x, 0.3 * norm(creak) * _da_env(len(creak), [(0, 0), (0.1, 1), (0.8, 0)]), t_stop + 0.15, False)
        _da_save(_da_far(x, rng, 850, hp=35), "level4", "elevator_clunk", i)

        # A copier warming up: relay, motor spinning up, the lamp carriage sweeping, paper-feed clunks.
        rng = rng_for(9940 + i)
        dur = 4.2
        n = int(dur * SR)
        rot = _da_env(n, [(0, 0), (0.1, 0), (0.6, rng.uniform(85, 105)), (3.3, 100), (dur, 10)])
        ph = 2 * np.pi * np.cumsum(rot) / SR
        motor = (np.sin(ph) + 0.5 * np.sin(2 * ph) + 0.3 * np.sin(3 * ph) + 0.15 * np.sin(8 * ph))
        x = 0.6 * motor * _da_env(n, [(0, 0), (0.1, 0), (0.6, 1), (3.3, 1), (dur, 0)])
        sweep_f = _da_env(n, [(0, 500), (0.7, 500), (2.2, 700), (dur, 700)])
        sweep = np.sin(2 * np.pi * np.cumsum(sweep_f) / SR) * 0.3 + 0.5 * _da_band(rng, n, 600, 2500)
        x += 0.5 * sweep * _da_env(n, [(0, 0), (0.7, 0), (0.9, 1), (2.0, 1), (2.2, 0), (dur, 0)])
        add_at(x, 0.5 * _da_hit(rng, [1900, 700], [0.006, 0.01], [1, 0.5], noise_ms=1, noise_lp=5000), 0.05, False)
        for k in range(2 + (i == 2)):
            add_at(x, 0.7 * _da_hit(rng, [rng.uniform(120, 180), rng.uniform(480, 560)], [0.04, 0.02], [1, 0.5]),
                   2.4 + 0.45 * k + rng.uniform(-0.05, 0.05), False)
        _da_save(_da_far(x, rng, 1700, hp=60), "level4", "copier", i, loud=0.85)


def snd_dist_amb_poolrooms() -> None:
    """The Poolrooms, built on Level 37's canon (https://backrooms-wiki.wikidot.com/level-37): "sound
    is severely altered", "the sudden dropping-off of noises", "soundwaves not reverberating as they
    should", "a minimal and constant rippling is present in the water". Everything is soft and
    muffled (low cut-offs, very little air); one sound is a splash that simply stops."""
    for i in range(1, 4):
        # Water lapping at a pool edge: slow soft swells, a few small bubbles.
        rng = rng_for(10000 + i)
        x = np.zeros(int(3.4 * SR))
        tt = 0.05
        for _ in range(2 + i % 3):
            m = int(rng.uniform(0.5, 0.8) * SR)
            lap = _da_band(rng, m, 150, 900) * _da_env(m, [(0, 0), (0.08, 1), (m / SR, 0)]) ** 1.5
            add_at(x, lap * rng.uniform(0.6, 1.0), tt, False)
            for _ in range(3):
                add_at(x, 0.2 * _da_chirp(rng.uniform(400, 700), rng.uniform(800, 1100), 0.03), tt + rng.uniform(0.05, 0.4), False)
            tt += m / SR + rng.uniform(0.2, 0.6)
            if tt > 2.6:
                break
        _da_save(_da_far(x, rng, 1100, hp=60, air=0.08, room=0.25), "poolrooms", "water_lap", i, loud=0.8)

        # A far drain gurgling: low bubbling over a soft suction.
        rng = rng_for(10010 + i)
        gd = rng.uniform(1.4, 2.0)
        n = int((gd + 0.5) * SR)
        env = _da_env(n, [(0, 0), (0.3, 1), (gd * 0.7, 0.9), (gd, 0), (gd + 0.5, 0)])
        x = 0.4 * _da_band(rng, n, 60, 400) * env
        for tm in np.sort(rng.uniform(0.1, 0.1 + gd, int(gd * rng.uniform(12, 20)))):
            add_at(x, env[int(tm * SR)] * _da_chirp(rng.uniform(150, 300), rng.uniform(350, 700), rng.uniform(0.03, 0.07)),
                   tm, False)
        _da_save(_da_far(x, rng, 900, hp=40, air=0.08, room=0.25), "poolrooms", "drain_gurgle", i)

        # A drip into the pool, its tiny glassy ring on the tiles; then one or two smaller drips.
        rng = rng_for(10020 + i)
        x = np.zeros(int(2.6 * SR))
        tt, amp = 0.05, 1.0
        for _ in range(1 + i):
            f0 = rng.uniform(800, 1100)
            add_at(x, amp * _da_chirp(f0, f0 * rng.uniform(1.6, 2.2), 0.06, decay=0.02), tt, False)
            add_at(x, amp * ring([rng.uniform(1800, 2600)], [0.15], [0.15], 0.6), tt + 0.01, False)
            tt += rng.uniform(0.6, 1.0)
            amp *= rng.uniform(0.4, 0.7)
        _da_save(_da_far(x, rng, 1800, hp=200, air=0.1), "poolrooms", "tile_drip", i, loud=0.6)

        # A pump somewhere under the tiles: a muffled thunk and a hum that swells and drops away
        # (take 3: the hum stops dead, the level's "dropping-off of noises").
        rng = rng_for(10030 + i)
        dur = 3.2
        n = int(dur * SR)
        t = np.arange(n) / SR
        ph = 2 * np.pi * 60 * t
        hum = np.sin(ph) + 0.6 * np.sin(2 * ph) + 0.2 * np.sin(3 * ph)
        if i == 3:
            env = _da_env(n, [(0, 0), (0.6, 1), (1.6, 1), (1.605, 0), (dur, 0)])
        else:
            env = _da_env(n, [(0, 0), (0.6, 1), (1.6, 0.8), (dur, 0)])
        x = 0.5 * hum * env
        add_at(x, _da_hit(rng, [rng.uniform(55, 75), rng.uniform(150, 190)], [0.12, 0.06], [1, 0.5], noise_ms=4, noise_lp=600),
               0.05, False)
        _da_save(_da_far(x, rng, 600, hp=30, air=0.05, room=0.2), "poolrooms", "pump_thunk", i)

        # Sloshing that cuts off mid-splash: the sound just stops, echo and all.
        rng = rng_for(10040 + i)
        cut = rng.uniform(0.8, 1.4)
        n = int((cut + 0.6) * SR)
        x = _da_band(rng, n, 200, 2200) * (1 + 0.5 * smooth_jitter(rng, n, 12, 1.0))
        x *= _da_env(n, [(0, 0), (0.15, 1), (cut, 0.8), (cut + 0.6, 0.8)])
        for _ in range(6):
            add_at(x, 0.3 * _da_chirp(rng.uniform(300, 600), rng.uniform(700, 1200), 0.04), rng.uniform(0.05, cut), False)
        x = _da_far(x, rng, 1600, hp=80, air=0.08, room=0.25)
        c = int(cut * SR)
        x[c - int(0.006 * SR):c] *= np.linspace(1, 0, int(0.006 * SR))
        x[c:] = 0.0
        _da_save(x, "poolrooms", "cutoff_splash", i, loud=0.8)


def snd_dist_amb_level37() -> None:
    """Level 37 "Sublimity" (https://backrooms-wiki.wikidot.com/level-37): calm, bright, "devoid of
    entities"; "water rippling through the halls", the water "quiet, almost calming"; "you feel a
    calming sensation". Only gentle things: a ripple washing past, a warm far hum, rising bubbles, a
    trickle over a step, a soft breath of air. Nothing that could read as a threat."""
    for i in range(1, 4):
        # A gentle wash of water through a far arch.
        rng = rng_for(10100 + i)
        dur = 4.5
        n = int(dur * SR)
        x = _da_band(rng, n, 120, 1400) * _da_env(n, [(0, 0), (1.5, 1), (2.2, 0.9), (dur, 0)]) ** 1.3
        for _ in range(5):
            add_at(x, 0.1 * _da_chirp(rng.uniform(1200, 1800), rng.uniform(1900, 2600), 0.03), rng.uniform(1.0, 3.0), False)
        _da_save(_da_far(x, rng, 2200, hp=80, air=0.12), "level37", "ripple_wash", i, loud=0.7)

        # A warm, distant hum: a soft major chord swelling and fading, slowly beating.
        rng = rng_for(10110 + i)
        dur = 5.0
        n = int(dur * SR)
        t = np.arange(n) / SR
        chord = [(87.3, 130.8, 174.6, 220.0), (116.5, 174.6, 233.1, 293.7), (98.0, 146.8, 196.0, 246.9)][i - 1]
        x = sum(np.sin(2 * np.pi * f * t + rng.uniform(0, 6.28)) + 0.6 * np.sin(2 * np.pi * (f + rng.uniform(0.2, 0.5)) * t)
                for f in chord) * _da_env(n, [(0, 0), (1.6, 1), (2.6, 1), (dur, 0)])
        _da_save(_da_far(x, rng, 1400, hp=50, air=0.1), "level37", "warm_hum", i, loud=0.6)

        # A few bubbles rising and breaking the surface.
        rng = rng_for(10120 + i)
        x = np.zeros(int(2.5 * SR))
        tt = 0.05
        for _ in range(3 + i):
            add_at(x, rng.uniform(0.5, 1.0) * _da_chirp(rng.uniform(300, 500), rng.uniform(600, 900), rng.uniform(0.04, 0.08)),
                   tt, False)
            tt += rng.uniform(0.15, 0.45)
            if tt > 2.0:
                break
        _da_save(_da_far(x, rng, 2000, hp=120, air=0.12), "level37", "water_bubble", i, loud=0.6)

        # A short trickle of water spilling over a step into a pool.
        rng = rng_for(10130 + i)
        run = rng.uniform(1.4, 2.2)
        n = int((run + 0.4) * SR)
        env = _da_env(n, [(0, 0), (0.3, 1), (run - 0.4, 0.8), (run, 0), (run + 0.4, 0)])
        x = 0.3 * _da_band(rng, n, 800, 3000) * env
        for tm in np.sort(rng.uniform(0.05, run, int(run * rng.uniform(40, 70)))):
            add_at(x, 0.3 * env[int(tm * SR)] * _da_chirp(rng.uniform(600, 1400), rng.uniform(1500, 2400), 0.02), tm, False)
        _da_save(_da_far(x, rng, 2600, hp=200, air=0.12), "level37", "trickle", i, loud=0.65)

        # A soft breath of warm air through the openings, with a faint whistle on its peak.
        rng = rng_for(10140 + i)
        dur = 4.5
        n = int(dur * SR)
        env = _da_env(n, [(0, 0), (1.8, 1), (2.6, 0.9), (dur, 0)])
        wf = rng.uniform(600, 900) * (1 + smooth_jitter(rng, n, 1, 0.03))
        x = (_da_band(rng, n, 250, 1800) + 0.05 * np.sin(2 * np.pi * np.cumsum(wf) / SR)) * env
        _da_save(_da_far(x, rng, 2500, hp=120, air=0.1), "level37", "soft_breeze", i, loud=0.6)


def snd_dist_amb_level9() -> None:
    """Level 9 (https://backrooms-wiki.wikidot.com/level-9): an "infinite suburban area at midnight",
    houses "furnished", "Items that require a power source are not functional" (so nothing
    electric here), "wet asphalt roads" and "puddles", Hounds and Death Rats among its entities.
    A dog barking that stops dead, wind chimes on a porch, a screen door slapping shut, a gutter
    dripping, a trash can lid knocked over and wobbling still."""
    for i in range(1, 4):
        # Barking, far off, the last bark cut off short.
        rng = rng_for(10200 + i)
        x = np.zeros(int(2.2 * SR))
        barks = [2, 3, 1][i - 1]
        tt = 0.05
        for b in range(barks):
            bd = rng.uniform(0.12, 0.2)
            m = int(bd * SR)
            f0 = np.geomspace(rng.uniform(480, 620), rng.uniform(300, 380), m)
            v = voice(f0, [(rng.uniform(650, 800), 150, 1.0), (rng.uniform(1300, 1600), 200, 0.7), (2600, 300, 0.3)],
                      rng, nh=20, tilt=0.05)
            bark = (norm(v) + 0.4 * _da_band(rng, m, 500, 3000)) * _da_env(m, [(0, 0), (0.012, 1), (bd * 0.4, 0.8), (bd, 0)])
            if b == barks - 1:
                c = int(m * 0.55)
                bark[c - 200:c] *= np.linspace(1, 0, 200)
                bark[c:] = 0.0
            add_at(x, bark, tt, False)
            tt += bd + rng.uniform(0.25, 0.45)
        _da_save(_da_far(x, rng, 1600, hp=250, air=0.12, room=0.4), "level9", "dog_bark", i, loud=0.85)

        # Wind chimes stirred on a porch: slightly out-of-tune tubes, ringing long.
        rng = rng_for(10210 + i)
        x = np.zeros(int(5.0 * SR))
        base = rng.uniform(780, 900)
        tt = 0.05
        for _ in range(5 + 2 * i):
            f = base * rng.choice([1, 9 / 8, 5 / 4, 3 / 2, 5 / 3]) * rng.uniform(0.985, 1.015)
            add_at(x, rng.uniform(0.3, 1.0) * ring([f, f * 2.76, f * 5.40], [rng.uniform(1.2, 2.0), 0.6, 0.25], [1, 0.35, 0.12], 2.5),
                   tt, False)
            tt += rng.exponential(0.18)
            if tt > 2.2:
                break
        _da_save(_da_far(x, rng, 3500, hp=300, air=0.15, room=0.4), "level9", "wind_chime", i, loud=0.7)

        # A screen door: the spring creaks it open, it slaps shut and bounces twice in the frame.
        rng = rng_for(10220 + i)
        x = np.zeros(int(2.3 * SR))
        creak = _da_stickslip(rng, 0.6, [(0, 30), (0.3, rng.uniform(70, 110)), (0.6, 40)],
                              [rng.uniform(900, 1200), rng.uniform(1800, 2300), 3200], [0.008, 0.005, 0.003], [1, 0.6, 0.3])
        add_at(x, 0.5 * norm(creak) * _da_env(len(creak), [(0, 0), (0.1, 1), (0.5, 0.7), (0.6, 0)]), 0.05, False)
        slap_t = 0.65 + rng.uniform(0.05, 0.15)
        for k, a in enumerate((1.0, 0.35, 0.15)):
            slap = _da_hit(rng, [rng.uniform(160, 210), rng.uniform(420, 520), rng.uniform(900, 1100)],
                           [0.04, 0.025, 0.015], [1, 0.7, 0.4], noise_ms=2.5, noise_lp=4000)
            add_at(x, a * slap, slap_t + k * rng.uniform(0.06, 0.1), False)
        add_at(x, ring([rng.uniform(250, 350)], [0.25], [0.15], 0.6), slap_t, False)
        _da_save(_da_far(x, rng, 2200, hp=150, air=0.15, room=0.4), "level9", "screen_door", i, loud=0.85)

        # A gutter dripping: tinny plinks in the metal, now and then one into a puddle.
        rng = rng_for(10230 + i)
        x = np.zeros(int(3.6 * SR))
        tt = 0.05
        while tt < 3.2:
            if rng.random() < 0.25:
                add_at(x, 0.6 * _da_chirp(rng.uniform(650, 800), rng.uniform(1200, 1500), 0.05), tt, False)
            else:
                add_at(x, rng.uniform(0.4, 1.0) * _da_hit(rng, [rng.uniform(1500, 2400), rng.uniform(3200, 4200)], [0.03, 0.015],
                                                          [1, 0.5], noise_ms=0.6, noise_lp=6000), tt, False)
            tt += rng.uniform(0.3, 0.8)
        _da_save(_da_far(x, rng, 3000, hp=300, air=0.15), "level9", "gutter_drip", i, loud=0.6)

        # A metal trash can lid knocked down: one clang, then it wobbles faster and faster to rest.
        rng = rng_for(10240 + i)
        x = np.zeros(int(3.0 * SR))
        f1 = rng.uniform(380, 480)
        lid = [f1, f1 * 2.3, f1 * 3.9, f1 * 5.7]
        add_at(x, _da_hit(rng, lid, [0.4, 0.25, 0.15, 0.1], [1, 0.6, 0.4, 0.2], noise_ms=2, noise_lp=5000), 0.05, False)
        tt, gap, amp = 0.05 + rng.uniform(0.25, 0.4), rng.uniform(0.15, 0.2), 0.5
        while gap > 0.012 and tt < 2.6:
            add_at(x, amp * _da_hit(rng, [f * rng.uniform(0.98, 1.02) for f in lid], [0.15, 0.1, 0.06, 0.04], [1, 0.6, 0.4, 0.2],
                                    noise_ms=1, noise_lp=4000), tt, False)
            tt += gap
            gap *= 0.82
            amp *= 0.87
        _da_save(_da_far(x, rng, 2400, hp=150, air=0.15, room=0.4), "level9", "bin_rattle", i, loud=0.8)


def snd_dist_amb_level94() -> None:
    """Level 94 "Motion" (https://backrooms-wiki.wikidot.com/level-94): a toy-like town with "1930s-
    styled cars, and milk vans" and "a water tower", safe "with no entities during the day"; at
    night the music stops and things come out, the Death Rats with their "stop-motion look". By
    day: a cartoon car horn, milk bottles clinking; any time: the water tower creaking; by night: a
    choppy stop-motion skitter, sneaky cartoon tiptoes that stop mid-phrase."""
    honk = [(700, 200, 1.0), (1400, 250, 0.8), (2500, 300, 0.4)]
    for i in range(1, 4):
        # A far car horn: "ah-oo-gah" (1), a double honk of two reeds (2), one long toot (3).
        rng = rng_for(10300 + i)
        x = np.zeros(int(2.2 * SR))
        if i == 1:
            m = int(0.75 * SR)
            f0 = _da_env(m, [(0, 180), (0.15, 260), (0.45, 300), (0.75, 230)])
            add_at(x, norm(voice(f0, honk, rng, nh=24)) * env_adsr(m, 0.02, 0.06), 0.05, False)
        else:
            notes = [(0.05, 0.22), (0.39, 0.22)] if i == 2 else [(0.05, 0.5)]
            fa = rng.uniform(330, 380)
            for start, ln in notes:
                m = int(ln * SR)
                f0 = np.full(m, fa)
                tone = norm(voice(f0, honk, rng, nh=24)) + 0.7 * norm(voice(f0 * 1.26, honk, rng, nh=20))
                add_at(x, norm(tone) * env_adsr(m, 0.015, 0.04), start, False)
        _da_save(_da_far(x, rng, 2600, hp=200, air=0.2, room=0.5), "level94", "car_horn", i, loud=0.8)

        # Milk bottles clinking in a crate on a van: little clusters of glassy clinks.
        rng = rng_for(10310 + i)
        x = np.zeros(int(2.0 * SR))
        tt = 0.05
        for _ in range(2 + (i == 3)):
            add_at(x, 0.4 * ring([rng.uniform(180, 240)], [0.03], [1], 0.2), tt, False)
            for _ in range(int(rng.integers(2, 5))):
                f = rng.uniform(2200, 3800)
                d = rng.uniform(0.04, 0.12)
                add_at(x, rng.uniform(0.4, 1.0) * ring([f, f * 1.52, f * 2.4], [d, d * 0.7, d * 0.5], [1, 0.5, 0.3], 0.4), tt, False)
                tt += rng.uniform(0.03, 0.09)
            tt += rng.uniform(0.3, 0.5)
        _da_save(_da_far(x, rng, 3800, hp=250, air=0.15), "level94", "milk_bottles", i, loud=0.65)

        # The water tower's tank creaking as it settles, and one metallic tick.
        rng = rng_for(10320 + i)
        x = np.zeros(int(3.6 * SR))
        cr = _da_stickslip(rng, 2.6, [(0, 6), (1.0, rng.uniform(14, 22)), (2.0, rng.uniform(9, 14)), (2.6, 5)],
                           [rng.uniform(110, 150), rng.uniform(230, 290), rng.uniform(420, 520)], [0.06, 0.04, 0.025], [1, 0.7, 0.4])
        add_at(x, norm(cr) * _da_env(len(cr), [(0, 0), (0.5, 1), (2.0, 0.8), (2.6, 0)]), 0.05, False)
        add_at(x, 0.4 * _da_hit(rng, [rng.uniform(900, 1300), rng.uniform(2100, 2600)], [0.2, 0.1], [1, 0.4],
                                noise_ms=0.8, noise_lp=5000), 2.8, False)
        _da_save(_da_far(x, rng, 1200, hp=50, air=0.2, room=0.5), "level94", "water_tower_creak", i, loud=0.85)

        # Night: a stop-motion skitter. Claw ticks come in clumps on 12-per-second "frames", with
        # nothing in between, so the movement sounds as choppy as it looks.
        rng = rng_for(10330 + i)
        x = np.zeros(int(2.2 * SR))
        tt = 0.05
        for _ in range(3 + i % 2):
            frames = int(rng.integers(3, 7))
            for fr in range(frames):
                for tm in tt + fr / 12 + rng.uniform(0, 0.012, int(rng.integers(2, 5))):
                    tick = _da_hit(rng, [rng.uniform(2500, 4500), rng.uniform(5000, 7000)], [0.003, 0.002], [1, 0.5],
                                   noise_ms=0.4, noise_lp=8000)
                    add_at(x, rng.uniform(0.4, 1.0) * tick, tm, False)
            tt += frames / 12 + rng.uniform(0.15, 0.45)
            if tt > 1.7:
                break
        _da_save(_da_far(x, rng, 3200, hp=400, air=0.15), "level94", "rat_skitter", i, loud=0.65)

        # Night: cartoon "sneaking" pizzicato steps, a little flat, that stop before the phrase ends.
        rng = rng_for(10340 + i)
        x = np.zeros(int(3.0 * SR))
        steps = [[45, 47, 48, 50, 51], [52, 50, 48, 47], [45, 48, 45, 48, 50, 51]][i - 1]
        tt = 0.05
        for midi in steps:
            f = _l94_midi_hz(midi) * 2 ** (-15 / 1200)
            pluck = ring([f, 2 * f, 3 * f], [0.12, 0.06, 0.04], [1, 0.4, 0.2], 0.4)
            add_at(x, pluck, tt, False)
            add_at(x, 0.3 * _da_hit(rng, [f * 4], [0.005], [1], noise_ms=0.5, noise_lp=3000), tt, False)
            tt += rng.uniform(0.3, 0.4)
        _da_save(_da_far(x, rng, 2000, hp=70, air=0.2, room=0.5), "level94", "cartoon_tiptoe", i, loud=0.75)


DISTANT_SOUNDS = [
    snd_dist_amb_level0, snd_dist_amb_level1, snd_dist_amb_level2, snd_dist_amb_level4,
    snd_dist_amb_poolrooms, snd_dist_amb_level37, snd_dist_amb_level9, snd_dist_amb_level94,
]


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
    tex_asphalt, tex_asphalt_wet, tex_asphalt_marking, tex_sidewalk_slab, tex_sidewalk_curb, tex_sidewalk_curb_top,
    tex_storm_drain, tex_manhole, tex_lawn_grass_top, tex_lawn_grass_side, tex_leaf_litter, tex_dust_pile,
    tex_sidings, tex_house_trim, tex_brick_foundation, tex_roof_shingles, tex_house_window, tex_house_door,
    tex_garage_door, tex_porch_planks, tex_porch_post, tex_porch_light,
    tex_beige_carpet, tex_dim_wallpaper, tex_wood_floor, tex_sofa_fabric, tex_tv, tex_bed, tex_fridge,
    tex_kitchen_counter, tex_dining_table,
    tex_picket_fence, tex_mailbox, tex_night_log, tex_night_leaves, tex_dead_branch, tex_hedge,
    tex_streetlamp_parts, tex_streetlamp_heads, tex_sign_post, tex_street_signs, tex_tunnel_concrete,
    tex_exit_tunnel_marker, tex_highway_sign_exit,
    tex_camcorder_battery, tex_almond_water, tex_canned_food, tex_backshroom, tex_bacteria, tex_smiler,
    tex_sanity_silhouette,
    tex_energy_bar, tex_calm_pills, tex_bandage, tex_backpack, tex_backpack_flap, tex_backpack_pocket,
    tex_backpack_strap, tex_supply_locker, tex_supply_locker_top, tex_supply_locker_front,
    tex_checkpoint_radio_case, tex_checkpoint_radio_front, tex_safe_room_lamp, tex_safe_room_sign,
    tex_menu_logo, tex_menu_noise, tex_menu_vignette, tex_menu_note,
    tex_water_ripples, tex_water_caustics, tex_water_shallow_item,
    *L4_TEXTURES,
    *L37_TEXTURES,
    *L94_PROPS_TEXTURES,
    *L94_WORLD_TEXTURES,
    *L94_CASTLE_TEXTURES,
    *L94_SKY_TEXTURES,
    tex_surface_variants, variant_models,
]
SOUNDS = [
    snd_level2_ambience,
    snd_rec_beep, snd_battery_low, snd_noclip, snd_escape,
    snd_flashlight_click, snd_wake_up, snd_alarm,
    snd_footsteps, snd_intercom, snd_distant_knock,
    snd_distant_creak, snd_thunder, snd_radio_static, snd_death_sting,
    snd_intro_glitch, snd_intro_impact, snd_intro_wind,
    snd_poolrooms_ambience, snd_hallucination_loop, snd_phantom_vanish, snd_drain_pull,
    snd_level9_ambience, snd_sodium_hum, snd_lamp_flicker, snd_distant_rumble, snd_watcher_hum,
    snd_watcher_spot, snd_watcher_beam, snd_watcher_dust, snd_observer_vanish, snd_leaf_steps,
    snd_smiler_breath,
    snd_sanity_whisper, snd_sanity_heartbeat, snd_sanity_knock,
    snd_locker_open, snd_locker_close, snd_pills_rattle, snd_bandage_wrap,
    snd_checkpoint_save,
    snd_water_wade, snd_water_splash,
    *L4_SOUNDS,
    *L37_SOUNDS,
    *L94_PROPS_SOUNDS,
    *L94_SKY_SOUNDS,
    *MUSIC_SOUNDS,
    *DISTANT_SOUNDS,
]


def module_generators() -> list:
    """Generator functions of tools/anomaly_assets/*.py and tools/level_assets/*.py (each
    module's TEXTURES + SOUNDS)."""
    import importlib
    import pkgutil
    import anomaly_assets
    import level_assets
    fns = []
    for package in (anomaly_assets, level_assets):
        for info in sorted(pkgutil.iter_modules(package.__path__), key=lambda m: m.name):
            module = importlib.import_module(f"{package.__name__}.{info.name}")
            fns += list(getattr(module, "TEXTURES", [])) + list(getattr(module, "SOUNDS", []))
    return fns


def main() -> None:
    import sys
    filters = sys.argv[1:]
    for fn in TEXTURES + SOUNDS + module_generators():
        if filters and not any(f in fn.__name__ for f in filters):
            continue
        fn()
        print("ok", fn.__name__)


if __name__ == "__main__":
    main()
