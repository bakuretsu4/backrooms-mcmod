"""Procedural materials for Level 228's props: everything painted from noise, Voronoi cells and
height fields, then aged (dust, grime, chips, flaking gilt, rust). Arrays are float sRGB 0..1,
(rows, cols, 3) or (rows, cols, 4); row 0 is the TOP of the texture.

Relief that the meshes are too coarse to model (carving, knotwork, wood grain, chipped paint
edges) is painted in as baked shading from a height field, lit from above as the hanging bulbs
light everything in the maze. Normal-map textures are not an option (no _n textures), and baked
shading also reads the same under plain Veil and under shader packs.
"""
from __future__ import annotations

import math

import numpy as np
from scipy import ndimage
from scipy.spatial import cKDTree

# ------------------------------------------------------------------------------------------------
# Noise and fields
# ------------------------------------------------------------------------------------------------


def rng(seed: int) -> np.random.Generator:
    return np.random.default_rng(seed)


def fnoise(r, h, w, beta=2.0, aniso=(1.0, 1.0), kmin=1.0, kmax=None) -> np.ndarray:
    """Seamless fractal noise, power ~ 1/k^beta, zero mean, unit std (as gen_assets.fnoise)."""
    KY, KX = np.meshgrid(np.fft.fftfreq(h) * h, np.fft.fftfreq(w) * w, indexing="ij")
    k = np.hypot(KX * aniso[0], KY * aniso[1])
    band = k >= kmin
    if kmax is not None:
        band &= k <= kmax
    amp = np.zeros_like(k)
    amp[band] = k[band] ** (-beta / 2)
    spec = (r.normal(size=k.shape) + 1j * r.normal(size=k.shape)) * amp
    a = np.real(np.fft.ifft2(spec))
    return a / (a.std() + 1e-12)


def blur(a: np.ndarray, s: float, wrap=False) -> np.ndarray:
    mode = "wrap" if wrap else "reflect"
    if a.ndim == 3:
        return np.stack([ndimage.gaussian_filter(a[..., c], s, mode=mode) for c in range(a.shape[2])], axis=-1)
    return ndimage.gaussian_filter(a, s, mode=mode)


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def grid(h, w):
    """Pixel-centre coordinates: X 0..1 left to right, Y 0..1 TOP to bottom."""
    y, x = np.mgrid[0:h, 0:w].astype(np.float64)
    return (x + 0.5) / w, (y + 0.5) / h


def lerp(a, b, t):
    a = np.asarray(a, float)
    b = np.asarray(b, float)
    t = np.asarray(t, float)
    if t.ndim == 2 and (a.ndim == 1 or a.ndim == 3 or b.ndim == 1 or b.ndim == 3):
        t = t[..., None]
    return a + (b - a) * t


def col(hexstr_or_tuple):
    if isinstance(hexstr_or_tuple, str):
        s = hexstr_or_tuple.lstrip("#")
        return np.array([int(s[i:i + 2], 16) / 255.0 for i in (0, 2, 4)])
    return np.asarray(hexstr_or_tuple, float)


def fill(h, w, c) -> np.ndarray:
    return np.broadcast_to(col(c), (h, w, 3)).copy()


def voronoi(r, h, w, n, wrap=True, aspect=1.0):
    """Cells of n random sites: (id, d1, d2) in pixels (d2 - d1 = distance to the nearest edge)."""
    pts = r.random((n, 2)) * [w, h]
    if wrap:
        offs = [(dx * w, dy * h) for dx in (-1, 0, 1) for dy in (-1, 0, 1)]
        allp = np.concatenate([pts + o for o in offs])
        ids = np.tile(np.arange(n), 9)
    else:
        allp, ids = pts, np.arange(n)
    tree = cKDTree(allp * [1.0, aspect])
    yy, xx = np.mgrid[0:h, 0:w]
    q = np.stack([xx.ravel() + 0.5, (yy.ravel() + 0.5) * aspect], axis=1)
    d, i = tree.query(q, k=2)
    return ids[i[:, 0]].reshape(h, w), d[:, 0].reshape(h, w), d[:, 1].reshape(h, w)


def relief(height: np.ndarray, strength=1.0, light=(0.35, -1.0), wrap=False) -> np.ndarray:
    """Baked shading of a height field (pixels): 1 = flat, brighter on slopes facing the light.
    light is (x, y) in texture space with y pointing DOWN the texture; (0, -1) = light from the top."""
    mode = "wrap" if wrap else "nearest"
    gx = ndimage.sobel(height, axis=1, mode=mode) / 8.0
    gy = ndimage.sobel(height, axis=0, mode=mode) / 8.0
    lx, ly = light
    ln = math.hypot(lx, ly)
    lx, ly = lx / ln, ly / ln
    nz = 1.0
    nlen = np.sqrt(gx * gx + gy * gy + nz * nz)
    # Normal = (-gx, -gy, 1)/len; light = (lx, ly, 0.9) normalised.
    L = np.array([lx, ly, 0.9])
    L /= np.linalg.norm(L)
    dot = (-gx * L[0] - gy * L[1] + nz * L[2]) / nlen
    return 1.0 + strength * (dot - L[2])


def cavity(height: np.ndarray, radius=3.0, strength=1.0) -> np.ndarray:
    """Ambient occlusion from a height field: 1 on tops, darker down in grooves."""
    c = height - ndimage.gaussian_filter(height, radius, mode="nearest")
    c = c / (np.abs(c).max() + 1e-9)
    return np.clip(1.0 + strength * np.minimum(c, 0.0) * 1.6 + 0.15 * strength * np.maximum(c, 0.0), 0.2, 1.3)


def scratches(r, h, w, count, length=(0.05, 0.3), width=1.0, angle=None, strength=1.0) -> np.ndarray:
    """Thin straight-ish scratch lines (0..1 mask)."""
    from PIL import Image, ImageDraw
    img = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(img)
    for _ in range(count):
        x, y = r.random() * w, r.random() * h
        a = r.random() * math.pi if angle is None else angle + r.normal() * 0.25
        L = (length[0] + r.random() * (length[1] - length[0])) * max(w, h)
        pts = []
        for k in range(6):
            t = k / 5
            pts.append((x + math.cos(a) * L * t + r.normal() * 0.6, y + math.sin(a) * L * t + r.normal() * 0.6))
        d.line(pts, fill=int(255 * (0.4 + 0.6 * r.random()) * strength), width=max(1, int(round(width))))
    return np.asarray(img, float) / 255.0


# ------------------------------------------------------------------------------------------------
# Ageing
# ------------------------------------------------------------------------------------------------


def dust(rgb, amount, r, tone=(0.55, 0.52, 0.47), up=None):
    """Settle dust: a pale grey film, thicker where `up` (0..1 mask of upward faces/top edges)."""
    h, w = rgb.shape[:2]
    n = fnoise(r, h, w, beta=2.4) * 0.5 + 0.5
    f = np.clip(amount * (0.55 + 0.6 * n), 0, 1)
    if up is not None:
        f = np.clip(f * (0.6 + 0.9 * up), 0, 1)
    return lerp(rgb, col(tone), f * 0.6)


def grime_gradient(rgb, r, bottom=0.35, top=0.1, tone=(0.09, 0.07, 0.05), streak=0.4):
    """Grime: darker toward the bottom (floor splash, mop marks) and in vertical runs (damp)."""
    h, w = rgb.shape[:2]
    X, Y = grid(h, w)
    n = fnoise(r, h, w, beta=2.2) * 0.5 + 0.5
    runs = fnoise(r, h, w, beta=2.0, aniso=(1.0, 14.0)) * 0.5 + 0.5
    g = bottom * smooth(0.55, 1.0, Y) + top * smooth(0.25, 0.0, Y)
    g = g * (0.6 + 0.8 * n) + streak * 0.25 * np.clip(runs - 0.55, 0, 1) * 2
    return lerp(rgb, col(tone), np.clip(g, 0, 0.85))


def damp_stains(rgb, r, count=3, tone=(0.32, 0.25, 0.16), strength=0.35):
    """Tide-mark rings of old damp: a faint brown blot with a darker edge."""
    h, w = rgb.shape[:2]
    out = rgb.copy()
    X, Y = np.mgrid[0:h, 0:w].astype(float)
    warp = fnoise(r, h, w, beta=2.6) * 0.18
    for _ in range(count):
        cx, cy = r.random() * w, r.random() * h
        rad = (0.12 + 0.25 * r.random()) * min(h, w)
        d = np.hypot(X - cy, Y - cx) / rad + warp
        inside = smooth(1.0, 0.75, d) * 0.5
        rim = np.exp(-((d - 0.97) / 0.035) ** 2)
        f = np.clip((inside + rim) * strength, 0, 1)
        out = lerp(out, col(tone), f)
    return out


def chipped_paint(top_rgb, under_rgb, r, amount=0.25, scale=40.0, edge=None, height_out=False, grain=1.6):
    """Paint chipped off over an undercoat: blotchy chips with a thin dark rim and lighter broken
    edge. edge: optional 0..1 mask of worn edges (chips gather there). grain: spectral slope of the
    chip pattern (higher = fewer, larger, smoother-edged flakes: peeling boards rather than
    speckle, and a much smaller PNG). Returns rgb (and the paint mask if height_out)."""
    h, w = top_rgb.shape[:2]
    n = fnoise(r, h, w, beta=grain, kmin=max(1.0, min(h, w) / scale / 4))
    n2 = fnoise(r, h, w, beta=grain + 0.6)
    t = n * 0.7 + n2 * 0.5
    if edge is not None:
        t = t + edge * 2.2
    thr = np.quantile(t, 1.0 - amount)
    chip = t > thr
    paint = ~chip
    dist_in = ndimage.distance_transform_edt(paint)
    rim = np.exp(-(dist_in / 1.3) ** 2) * paint
    out = np.where(chip[..., None], under_rgb, top_rgb)
    out = lerp(out, out * 0.55, rim * 0.7)
    # Thin lighter lip just inside the chip (paint edge catching light).
    dist_out = ndimage.distance_transform_edt(chip)
    lip = np.exp(-((dist_out - 1.0) / 0.8) ** 2) * chip
    out = lerp(out, np.minimum(top_rgb * 1.25, 1.0), lip * 0.35)
    if height_out:
        return out, paint.astype(float)
    return out


def craquelure(r, h, w, cells=180, width=0.9):
    """Fine crack network of old varnish or gilding (0..1 mask)."""
    _, d1, d2 = voronoi(r, h, w, cells)
    edge = d2 - d1
    warp = fnoise(r, h, w, beta=2.0) * 0.4
    return np.clip(1.0 - (edge + warp) / width, 0, 1) ** 1.5


# ------------------------------------------------------------------------------------------------
# Base materials
# ------------------------------------------------------------------------------------------------

# Albedos are kept out of the near-black: v1's 14-34/255 surfaces read as pure black on camera in
# both renderers (dim light has nothing to bounce off). Dark walnut here is about 70/255.
WALNUT = (col("#4a2f1c"), col("#6e4a2e"), col("#28180d"))   # mid, light, dark
PINE = (col("#8a6a44"), col("#b08b5c"), col("#5a4026"))
GOLD = col("#b38c45")
GOLD_HI = col("#dcc07a")
GOLD_LO = col("#5e4319")
BOLE = col("#5e3220")
GESSO = col("#cfc6aa")


def wood(r, h, w, ppb, palette=WALNUT, grain="v", rings=7.0, figure=1.0, pores=0.5, varnish=0.35):
    """Sawn/turned wood. grain 'v' = fibres run down the texture, 'h' across. ppb (pixels per block)
    keeps the grain the same real size whatever the texture size."""
    if grain == "h":
        return np.transpose(wood(r, w, h, ppb, palette, "v", rings, figure, pores, varnish), (1, 0, 2))
    mid, light, dark = palette
    Y, X = np.mgrid[0:h, 0:w].astype(float)
    # Growth rings seen on a flat-sawn face: stripes along the fibres, wobbling (cathedral figure).
    wob = fnoise(r, h, w, beta=3.2, aniso=(1.0, 6.0)) * (w / ppb) * 0.06 * ppb
    wob2 = fnoise(r, h, w, beta=2.6, aniso=(1.0, 3.0)) * 0.5
    phase = (X + wob * figure) / ppb * rings + wob2
    ring = 0.5 + 0.5 * np.sin(phase * 2 * math.pi)
    ring = ring ** 2.5
    # Fibres: very fine streaks along the grain.
    fib = fnoise(r, h, w, beta=1.2, aniso=(1.0, 24.0))
    tone = fnoise(r, h, w, beta=2.8) * 0.5
    t = np.clip(0.45 + 0.35 * ring + 0.08 * fib + 0.15 * tone, 0, 1)
    rgb = lerp(lerp(dark, mid, smooth(0.0, 0.55, t)), light, smooth(0.55, 1.0, t))
    # Pores: short dark dashes along the grain.
    pr = fnoise(r, h, w, beta=0.6, aniso=(1.0, 10.0))
    pm = np.clip((pr - 2.0) * 0.6, 0, 1) * pores
    rgb = lerp(rgb, dark * 0.7, pm)
    # Old varnish: a slightly amber, uneven sheen (worn through in patches).
    vn = fnoise(r, h, w, beta=2.4) * 0.5 + 0.5
    rgb = lerp(rgb, rgb * np.array([1.08, 1.0, 0.86]), varnish * vn)
    return np.clip(rgb, 0, 1)


def gilt(r, h, w, ppb, wear=0.3, flake=0.22, base=None, height=None):
    """Water gilding on gesso over red bole, aged: tarnished gold, the leaf rubbed through to the bole
    on high points, flaked off down to the gesso or the wood in patches, dirt in the hollows.
    height: optional relief (carving) height field in pixels (high = proud)."""
    X, Y = grid(h, w)
    n1 = fnoise(r, h, w, beta=2.4) * 0.5 + 0.5
    n2 = fnoise(r, h, w, beta=1.4)
    sheen = fnoise(r, h, w, beta=2.8, aniso=(1.0, 3.0)) * 0.5 + 0.5
    g = lerp(GOLD_LO, GOLD, smooth(0.15, 0.7, n1))
    g = lerp(g, GOLD_HI, smooth(0.62, 0.95, sheen) * 0.55)
    # Gold leaf is laid in squares: faint overlapping seams.
    leaf = max(6, int(ppb * 0.33))
    seams = ((np.mgrid[0:h, 0:w][1] % leaf) < 1) | ((np.mgrid[0:h, 0:w][0] % leaf) < 1)
    g = lerp(g, g * 0.9, seams * 0.25)
    high = np.zeros((h, w)) if height is None else (height - height.min()) / (np.ptp(height) + 1e-9)
    # Rubbed through to the bole on the highest points only (more read as rust-red paint, not gilt).
    rub = np.clip((high * 1.2 + n2 * 0.2 - (1.25 - wear * 0.5)), 0, 1)
    g = lerp(g, BOLE, smooth(0.0, 0.3, rub) * 0.65)
    # Flakes: the gilding gone, gesso showing, and the wood under that in the worst spots.
    under = lerp(GESSO, WALNUT[0] if base is None else base, smooth(0.3, 0.8, fnoise(r, h, w, beta=2.0) * 0.5 + 0.5))
    g = chipped_paint(g, under, r, amount=flake, scale=ppb * 0.3)
    cr = craquelure(r, h, w, cells=max(40, int(h * w / (ppb * ppb) * 300)))
    g = lerp(g, g * 0.55, cr * 0.45)
    if height is not None:
        g = g * relief(height, 1.3)[..., None] * cavity(height, radius=max(2.0, ppb / 40), strength=0.9)[..., None]
        # Black dirt packed in the hollows.
        hollow = np.clip(1.0 - high * 2.2, 0, 1)
        g = lerp(g, col("#1a120a"), hollow * 0.55)
    return np.clip(g, 0, 1)


def plaster(r, h, w, ppb, tone=col("#cbbc9c"), cracks=0.4, stains=2):
    X, Y = grid(h, w)
    n = fnoise(r, h, w, beta=2.6) * 0.5 + 0.5
    fine = fnoise(r, h, w, beta=0.9)
    rgb = lerp(tone * 0.88, tone * 1.04, n)
    rgb = rgb * (1.0 + 0.025 * fine)[..., None]
    if cracks > 0:
        c = craquelure(r, h, w, cells=max(6, int(h * w / (ppb * ppb) * 6)), width=0.7)
        rgb = lerp(rgb, rgb * 0.62, c * cracks)
    if stains:
        rgb = damp_stains(rgb, r, stains, strength=0.22)
    return np.clip(rgb, 0, 1)


def faux_marble(r, h, w, ppb, base=col("#d8c8a6"), vein=col("#9a4f3c"), vein2=col("#6b3a2c")):
    """Painted marble (scagliola effect) as on the Lucerne columns: cream with wandering rust-red
    veins, varnished and yellowed."""
    Y, X = np.mgrid[0:h, 0:w].astype(float)
    warp1 = fnoise(r, h, w, beta=3.0) * 0.9
    warp2 = fnoise(r, h, w, beta=2.4) * 0.35
    s = (X / ppb * 1.6 + Y / ppb * 0.55) + warp1 + warp2
    v = np.abs(np.sin(s * math.pi * 1.3))
    veins = np.clip(1.0 - v / 0.06, 0, 1) ** 1.2
    s2 = (X / ppb * -0.8 + Y / ppb * 1.4) + fnoise(r, h, w, beta=2.8) * 1.1
    v2 = np.clip(1.0 - np.abs(np.sin(s2 * math.pi * 2.1)) / 0.03, 0, 1)
    cloud = fnoise(r, h, w, beta=2.8) * 0.5 + 0.5
    rgb = lerp(base * 0.92, base * 1.05, cloud)
    rgb = lerp(rgb, lerp(vein, base, 0.45), smooth(0.0, 1.0, blur(veins, 2.5)) * 0.55)
    rgb = lerp(rgb, vein, veins * 0.7)
    rgb = lerp(rgb, vein2, v2 * 0.45)
    # Yellowed varnish.
    rgb = rgb * np.array([1.0, 0.96, 0.86])
    return np.clip(rgb, 0, 1)


def steel_black(r, h, w, ppb, rust=0.3):
    """Black-painted steel: satin paint, scuffs to bare grey metal, rust blooms at the bottom."""
    X, Y = grid(h, w)
    n = fnoise(r, h, w, beta=2.4) * 0.5 + 0.5
    rgb = lerp(col("#222224"), col("#323235"), n)
    sc = scratches(r, h, w, int(h * w / (ppb * ppb) * 30) + 4, (0.02, 0.12), 1.0)
    rgb = lerp(rgb, col("#5b5b5e"), sc * 0.55)
    rn = fnoise(r, h, w, beta=1.8) * 0.5 + 0.5
    rmask = np.clip((rn - 0.72 + 0.35 * smooth(0.6, 1.0, Y)) * 3.0, 0, 1) * rust
    rcol = lerp(col("#3a1a0c"), col("#7a3a16"), fnoise(r, h, w, beta=1.2) * 0.5 + 0.5)
    rgb = lerp(rgb, rcol, rmask)
    return np.clip(rgb, 0, 1)


def brass(r, h, w, ppb, tarnish=0.5):
    """Old handled brass: smooth broad tonal drift (no pixel speckle, which shimmers on small
    turned parts), soft brown tarnish clouds, verdigris only as faint blooms (it gathers in the
    recesses, which on lathed parts are the low-frequency dark patches), a few soft polish rubs."""
    lo = blur(fnoise(r, h, w, beta=3.2, kmax=12), 2.0, wrap=True)
    lo = (lo - lo.min()) / (np.ptp(lo) + 1e-9)
    rgb = lerp(col("#7a5e2c"), col("#b39250"), lo)
    tn = blur(fnoise(r, h, w, beta=3.0, kmax=10), 3.0, wrap=True) * 0.5 + 0.5
    t = smooth(0.35, 0.8, tn) * tarnish
    rgb = lerp(rgb, col("#3e3221"), t * 0.85)
    vg = blur(fnoise(r, h, w, beta=3.0, kmax=8), 3.0, wrap=True) * 0.5 + 0.5
    rgb = lerp(rgb, col("#4f6b55"), smooth(0.62, 0.85, vg) * smooth(0.4, 0.8, tn) * tarnish * 0.55)
    rub = blur(scratches(r, h, w, 10, (0.08, 0.3), width=2.0), 2.5)
    rgb = lerp(rgb, col("#d8bf7e"), np.clip(rub * 1.5, 0, 1) * 0.25 * (1 - t))
    return np.clip(rgb, 0, 1)


def canvas_weave(r, h, w, ppb):
    """The texture of woven cotton duck: a fine crosshatch."""
    X, Y = np.mgrid[0:h, 0:w].astype(float)
    p = max(2.0, ppb / 48.0)
    weave = 0.5 + 0.25 * np.sin(X / p * math.pi) * np.sin(Y / p * math.pi)
    return weave + 0.08 * fnoise(r, h, w, beta=0.8)


def text_mask(text, font_path, size_px, w, h, anchor=(0.5, 0.5), spacing=0, stroke=0, rotate=0.0, fill=255):
    """A grey mask of text drawn with PIL (one line, centred at anchor)."""
    from PIL import Image, ImageDraw, ImageFont
    img = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(str(font_path), size_px)
    if spacing:
        # Letter-spaced: draw char by char.
        widths = [d.textlength(c, font=f) for c in text]
        total = sum(widths) + spacing * (len(text) - 1)
        x = anchor[0] * w - total / 2
        for c, cw in zip(text, widths):
            d.text((x, anchor[1] * h), c, font=f, fill=fill, anchor="lm", stroke_width=stroke, stroke_fill=fill)
            x += cw + spacing
    else:
        d.text((anchor[0] * w, anchor[1] * h), text, font=f, fill=fill, anchor="mm", stroke_width=stroke, stroke_fill=fill)
    if rotate:
        img = img.rotate(rotate, resample=Image.BICUBIC, center=(anchor[0] * w, anchor[1] * h))
    return np.asarray(img, float) / 255.0


def fit_text_size(text, font_path, max_w, max_h, start=400):
    from PIL import ImageFont
    s = start
    while s > 6:
        f = ImageFont.truetype(str(font_path), s)
        l, t, rr, b = f.getbbox(text)
        if rr - l <= max_w and b - t <= max_h:
            return s
        s = int(s * 0.94)
    return s


def worn_print(r, mask, amount=0.35):
    """Printing/paint worn off a sign or poster: holes in the ink, more at random rubbed patches."""
    h, w = mask.shape
    n = fnoise(r, h, w, beta=1.3) * 0.6 + fnoise(r, h, w, beta=2.6) * 0.6
    keep = np.clip(1.0 - np.clip(n - (1.6 - amount * 3.0), 0, 1) * 2.0, 0, 1)
    return mask * keep


def edge_distance(mask):
    """Distance (px) from the inside of a 0/1 mask to its edge."""
    return ndimage.distance_transform_edt(mask > 0.5)


def rgba(rgb, a=None):
    h, w = rgb.shape[:2]
    if a is None:
        a = np.ones((h, w))
    return np.concatenate([np.clip(rgb, 0, 1), np.clip(a, 0, 1)[..., None]], axis=2)


def bevel_shade(h, w, b_px, strength=0.25):
    """Shade of a flat painted board's edges (lighter top bevel, darker bottom), for texture-only
    panels."""
    Y, X = np.mgrid[0:h, 0:w].astype(float)
    top = smooth(b_px, 0, Y)
    bot = smooth(h - b_px, h, Y)
    left = smooth(b_px, 0, X)
    right = smooth(w - b_px, w, X)
    return 1.0 + strength * (top * 0.8 + left * 0.3 - bot * 1.0 - right * 0.5)
