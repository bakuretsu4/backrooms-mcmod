"""Level 39 house furniture and smooth roofs: textures, block models, blockstates and item models.

The period furniture of the Level 39 houses (1900-1930s): a wingback armchair and a camelback
sofa in faded velvet, a fringed table lamp, a cathedral console radio, a bookcase of sun-faded
books, an upright piano, the bedroom set (wardrobe with an oval mirror, chest of drawers with a
swing mirror, an iron bed under a patchwork quilt), the bathroom (clawfoot tub, pedestal basin,
toilet), the kitchen (monitor-top fridge, a pantry shelf of Almond Water, an enamel range,
cupboards, a farmhouse sink, a Hoosier cabinet), the dining set, a dim portrait on the wall and
the basement coal boiler. The block ids, properties and collision boxes come from
registry/Level39HouseBlocks.java; every model here is drawn facing north (front at z = 0, back
against the wall at z = 16) and matches those boxes. The shingle roofs (l39_roof, l39_roof_low)
are at the end of the file; their models follow Level39RoofBlock.surface exactly.

Run with `python tools/gen_assets.py l39_housefurn` from the project root. Every texture has its
own fixed seed (39700-39799), so re-running gives identical files. Textures are 128 x 128; the
"front" textures are painted in block pixels (8 texels per pixel) at the spot where the model's
front box sits, so the automatic face UVs line them up with the boxes. The model helpers (`_el`,
`_model`, `_cyl`, `_bm`, `_bs`, `_im`) are the ones from level39_props.
"""
from __future__ import annotations

import numpy as np
from PIL import Image

import gen_assets as G
from anomaly_assets import level39_props as P

S = G.S                  # 128 px block texture size
SH = (S, S)
X, Y = G.grid()          # pixel-centre coordinates of a block texture
FACES = P.FACES
SIDES = ("north", "south", "east", "west")

WALNUT = ((104, 70, 46), (56, 35, 23))
MAHOGANY = ((98, 44, 32), (48, 19, 14))
OAK = ((92, 65, 42), (44, 30, 19))
PINE = ((180, 142, 94), (134, 94, 56))
CREAM = (222, 208, 168)
JADE = (92, 134, 104)


def _ref(name: str) -> str:
    return f"backrooms:block/{name}"


def _save(img, name: str) -> None:
    G.save_png(img, G.TEX / "block" / f"{name}.png")


def _c(v) -> np.ndarray:
    return np.array(v, float)


def _fill(color, shape=SH) -> np.ndarray:
    """A flat colour image (G.fill defaults to 16 x 16; these textures are 128 x 128)."""
    return G.fill(color, shape)


# =============================================================================
# Drawing helpers
# =============================================================================

def _fr(a0, y0, a1, y1, top=16.0):
    """A front-face rectangle in texels. a = block pixels from the viewer's left (16 - x on a
    north face), y = block height in pixels; `top` is the height of texture row 0."""
    return (8.0 * a0, 8.0 * (top - y1), 8.0 * a1, 8.0 * (top - y0))


def _box(rect, shape=SH, soft=0.5):
    return G.box(shape, *rect, soft=soft)


def _grow(rect, d):
    return (rect[0] - d, rect[1] - d, rect[2] + d, rect[3] + d)


def _emboss(mask, amt, sigma=0.7):
    """Light from the top-left on the edges of a mask: amt > 0 = raised, < 0 = sunk."""
    m = G.blur(mask, sigma) if sigma else mask
    gy, gx = np.gradient(m)
    return (gx + gy) * amt


def _panel(img, rect, w=4.0, depth=16.0, raised=False, shape=SH):
    """A panel with a bevelled frame of width w (texels), lit from the top-left."""
    a0, b0, a1, b1 = rect
    Xs, Ys = G.grid(shape)
    outer = G.box(shape, a0, b0, a1, b1)
    inner = G.box(shape, a0 + w, b0 + w, a1 - w, b1 - w)
    band = np.clip(outer - inner, 0, 1)
    dl, dr, dt, db = Xs - a0, a1 - Xs, Ys - b0, b1 - Ys
    m = np.minimum(np.minimum(dl, dr), np.minimum(dt, db))
    face = np.select([dt == m, dl == m, db == m], [1.0, 0.55, -1.0], -0.6)
    img = G.add_lum(img, band * face * depth * (1 if raised else -1))
    return G.add_lum(img, _emboss(outer, 28 if raised else -28))


def _gap(img, rect, w=1.2, dark=46, shape=SH):
    """The dark shadow gap round a door or drawer."""
    ring = np.clip(G.box(shape, *_grow(rect, w)) - G.box(shape, *rect), 0, 1)
    return G.add_lum(img, -dark * ring)


def _dome(img, cx, cy, r, col, shape=SH, hl=1.0, shadow=0.5):
    """A round knob / rivet / ball, shaded as a sphere with a soft cast shadow."""
    Xs, Ys = G.grid(shape)
    d = np.hypot(Xs - cx, Ys - cy) / r
    sh = np.exp(-(np.hypot(Xs - cx - 0.35 * r, Ys - cy - 0.45 * r) / (1.2 * r)) ** 4)
    img = G.add_lum(img, -45 * shadow * sh * (d > 0.9))
    cov = G.supersample(shape, lambda x, y: np.hypot(x - cx, y - cy) < r)
    nz = np.sqrt(np.clip(1 - d ** 2, 0, 1))
    nx, ny = (Xs - cx) / r, (Ys - cy) / r
    light = np.clip(0.35 + 0.65 * (nz * 0.6 - nx * 0.45 - ny * 0.55), 0, 1.4)
    c = _c(col) * (0.45 + 0.75 * light)[..., None]
    c = c + (np.exp(-((nx + 0.35) ** 2 + (ny + 0.4) ** 2) / 0.05) * hl * 110)[..., None]
    return G.over(img, c, cov)


def _smudge(img, cx, cy, sx, sy, a=0.25, col=(70, 56, 40), rng=None, shape=SH):
    """A soft local patch of grime (hands, grease, damp)."""
    Xs, Ys = G.grid(shape)
    m = np.exp(-(((Xs - cx) / sx) ** 2 + ((Ys - cy) / sy) ** 2))
    if rng is not None:
        m = m * np.clip(1 + 0.6 * G.fnoise(rng, shape, beta=1.8), 0, 2)
    return G.over(img, _c(col), np.clip(m * a, 0, 1))


def _chip(img, rng, cx, cy, r, under=(58, 56, 60), rust=True, shape=SH):
    """An enamel/paint chip: a ragged, elongated flake gone, showing the grey iron (or wood)
    beneath, the broken glaze edge catching the light and rust creeping out under it."""
    Xs, Ys = G.grid(shape)
    ang = rng.uniform(0, np.pi)
    dx, dy = Xs - cx, Ys - cy
    du, dv = dx * np.cos(ang) + dy * np.sin(ang), (-dx * np.sin(ang) + dy * np.cos(ang)) * rng.uniform(1.3, 2.0)
    th = np.arctan2(dv, du)
    rad = np.full(shape, r)
    for k in range(2, 9):
        rad = rad + r * rng.normal(0, 0.22 / k ** 0.8) * np.cos(k * th + rng.uniform(0, 2 * np.pi))
    d = np.hypot(du, dv)
    m = G.smoothstep(rad + 0.5, rad - 0.5, d)
    if rust:
        img = G.over(img, _c((136, 92, 58)), G.smoothstep(rad + 3.0, rad, d) * (1 - m) * 0.35)
    img = G.add_lum(img, 26 * G.smoothstep(rad + 1.2, rad + 0.2, d) * (1 - m))
    img = G.over(img, _c(under), m)
    img = G.add_lum(img, -18 * G.smoothstep(rad - 1.5, rad - 0.2, d) * m)
    if rust:
        img = G.over(img, _c((118, 70, 44)), m * G.smoothstep(-0.5, 1.2, G.fnoise(rng, shape, beta=1.4)) * 0.6)
    return img


def _voronoi_edge(rng, n, shape=SH):
    """Distance to the nearest cell border of a wrapping Voronoi pattern (crazing, cracks)."""
    h, w = shape
    Xs, Ys = G.grid(shape)
    pts = rng.random((n, 2)) * [w, h]
    best = np.full(shape, 1e9)
    second = np.full(shape, 1e9)
    for px, py in pts:
        d = np.hypot(G.wrapd(Xs - px, w), G.wrapd(Ys - py, h))
        second = np.where(d < best, best, np.minimum(second, d))
        best = np.minimum(best, d)
    return second - best


def _scratches(rng, n, shape=SH, wrap=True, length=(6, 30), width=(0.4, 0.8)):
    lines = []
    h, w = shape
    for _ in range(n):
        x, y = rng.uniform(0, w), rng.uniform(0, h)
        ang, L = rng.uniform(0, np.pi), rng.uniform(*length)
        lines.append((x, y, x + L * np.cos(ang), y + L * np.sin(ang), rng.uniform(*width), rng.uniform(0.4, 1)))

    def draw(d, k, ox, oy):
        for x0, y0, x1, y1, wd, a in lines:
            d.line([((x0 + ox) * k, (y0 + oy) * k), ((x1 + ox) * k, (y1 + oy) * k)],
                   fill=int(255 * a), width=max(1, int(wd * k)))
    if wrap:
        return P._wrap_mask(draw, shape)
    return G.pil_mask(lambda d, k: draw(d, k, 0, 0), shape)


def _lines_mask(segs, width, shape=SH):
    """Anti-aliased mask of polylines given in texels: segs = [[(x, y), ...], ...]."""
    def draw(d, k):
        for pts in segs:
            d.line([(x * k, y * k) for x, y in pts], fill=255, width=max(1, int(width * k)), joint="curve")
    return G.pil_mask(draw, shape)


def _ellipse(cx, cy, rx, ry, shape=SH):
    return G.supersample(shape, lambda x, y: ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 < 1)


# =============================================================================
# Materials (all tile seamlessly)
# =============================================================================

def _wood(rng, base, dark, vertical=True, rings=7, figure=0.55, pores=0.5, rays=0.0, shape=SH):
    """Varnished hardwood: growth rings warped into figure, fibres, open pores, medullary rays."""
    h, w = shape
    Xs, Ys = G.grid(shape)
    an = (1, 12) if vertical else (12, 1)
    across = Xs / w if vertical else Ys / h
    warp = G.fnoise(rng, shape, beta=3.4, aniso=(1, 3) if vertical else (3, 1))
    phase = rings * across + figure * warp + 0.05 * G.fnoise(rng, shape, beta=1.8, aniso=an, kmin=3)
    t = phase % 1.0
    late = G.smoothstep(0.5, 0.86, t) * (1 - G.smoothstep(0.9, 0.995, t))
    tone = G.fnoise(rng, shape, beta=2.6)
    img = G.lerp(_c(base), _c(dark), np.clip(0.45 * late + 0.12 * tone + 0.12, 0, 1)[..., None])
    img = G.add_lum(img, G.fnoise(rng, shape, beta=1.0, aniso=an, kmin=6) * 4.5, (1, .85, .7))
    if pores:
        n = G.fnoise(rng, shape, beta=0.3, aniso=an, kmin=20)
        img = G.add_lum(img, -24 * pores * G.smoothstep(1.7, 2.7, n))
    if rays:
        n = G.fnoise(rng, shape, beta=0.6, aniso=(an[1], an[0]), kmin=12)
        img = G.add_lum(img, 16 * rays * G.smoothstep(1.9, 2.8, n), (1, .9, .75))
    return img


def _finish(img, rng, wear=1.0, scratches=14, shape=SH, wrap=True):
    """Old varnish: dull rubbed patches and fine light scratches."""
    worn = G.smoothstep(1.0, 2.3, G.fnoise(rng, shape, beta=2.4))
    grey = img.mean(axis=2, keepdims=True)
    dull = G.lerp(img, grey * np.array([1.06, 1.0, 0.9]) + 12, 0.4)
    img = G.over(img, dull, worn * 0.45 * wear)
    if scratches:
        img = G.add_lum(img, 14 * _scratches(rng, scratches, shape, wrap), (1, .9, .78))
    return img


def _craze(img, rng, amt=9.0, n=120, shape=SH):
    """Alligatored old varnish / glaze: a fine net of dark hairlines."""
    e = _voronoi_edge(rng, n, shape)
    keep = G.smoothstep(-0.4, 0.4, G.fnoise(rng, shape, beta=2.0))
    return G.add_lum(img, -amt * G.smoothstep(0.9, 0.0, e) * keep)


def _woodmat(seed, pal, vertical=True, rings=7, rays=0.0, craze=0.0, wear=1.0):
    rng = G.rng_for(seed)
    img = _wood(rng, *pal, vertical=vertical, rings=rings, rays=rays)
    if craze:
        img = _craze(img, rng, craze)
    return _finish(img, rng, wear)


def _velvet(rng, base, shape=SH):
    """Old velvet: crushed pile catching the light in patches, fibre grain, bald worn spots."""
    base = _c(base)
    img = _fill(base, shape)
    img = G.add_lum(img, G.fnoise(rng, shape, beta=2.8) * 9)
    sheen = G.smoothstep(0.6, 2.0, G.fnoise(rng, shape, beta=2.3, aniso=(1, 2.5)))
    img = G.over(img, G.lerp(base, (196, 192, 176), 0.32), sheen * 0.42)
    img = G.add_lum(img, G.fnoise(rng, shape, beta=0.2, kmin=24) * 3.0)
    img = G.add_lum(img, G.fnoise(rng, shape, beta=0.8, aniso=(1, 6), kmin=10) * 2.5)
    bald = G.smoothstep(1.3, 2.3, G.fnoise(rng, shape, beta=2.6))
    Xs, Ys = G.grid(shape)
    weave = ((np.floor(Xs / 1.5) + np.floor(Ys / 1.5)) % 2) - 0.5
    img = G.over(img, G.lerp(base, (150, 136, 110), 0.45), bald * 0.55)
    img = G.add_lum(img, bald * weave * 10)
    img = G.lerp(img, img.mean(axis=2, keepdims=True), 0.18)
    for _ in range(2):
        img = G.mark_damp(img, rng, size=(26, 22), dark=0.08)
    return img


def _enamel(rng, base, crazing=0.5, chips=3, under=(58, 56, 60), shape=SH):
    """Vitreous enamel / glazed porcelain: soft glaze waves, crazing, yellow grime, chips."""
    img = G._smooth_concrete(rng, base, mottle=2.2, fine=0.6, shape=shape)
    img = G.add_lum(img, G.fnoise(rng, shape, beta=3.5, aniso=(1, 2)) * 4)
    if crazing:
        img = _craze(img, rng, 14 * crazing, 70, shape)
    grime = G.smoothstep(-0.2, 1.8, G.fnoise(rng, shape, beta=2.4))
    img = G.over(img, _c((176, 160, 120)), grime * 0.18)
    for _ in range(chips):
        r = rng.uniform(1.4, 3.0)
        img = _chip(img, rng, rng.uniform(10, shape[1] - 10), rng.uniform(10, shape[0] - 10), r, under)
    return img


def _paint(rng, base=CREAM, under=(108, 136, 104), shape=SH, chips=1.0):
    """Many-times-painted wood: brush marks, yellowing, grain telegraphing, flakes to the old
    green coat and the bare wood."""
    img = _fill(base, shape)
    img = G.add_lum(img, G.fnoise(rng, shape, beta=1.3, aniso=(1, 9), kmin=3) * 3.0)
    img = G.add_lum(img, G.fnoise(rng, shape, beta=2.8) * 4.5, (1, .97, .88))
    img = G.over(img, _c((196, 172, 116)), G.smoothstep(-0.6, 1.6, G.fnoise(rng, shape, beta=2.4)) * 0.25)
    img = G.add_lum(img, G.fnoise(rng, shape, beta=1.6, aniso=(1, 14), kmin=4) * 2.0)
    fl = G.fnoise(rng, shape, beta=2.2, kmin=3)
    flake = G.smoothstep(2.55, 2.7, fl) * chips
    deep = G.smoothstep(2.95, 3.1, fl) * chips
    img = G.add_lum(img, _emboss(flake, -40, 0.5))
    img = G.over(img, _c(under), flake)
    return G.over(img, _c((120, 92, 62)), deep)


def _black_iron(rng, shape=SH):
    """Black-painted cast iron: dull sheen, rust blooms and flaked paint."""
    img = G._smooth_concrete(rng, (42, 40, 40), mottle=3.0, fine=1.0, shape=shape)
    img = G.add_lum(img, G.smoothstep(0.5, 2.0, G.fnoise(rng, shape, beta=2.8, aniso=(1, 3))) * 14)
    img = G.over(img, _c((104, 58, 36)), G.smoothstep(1.2, 2.2, G.fnoise(rng, shape, beta=1.8)) * 0.6)
    img = G.over(img, _c((86, 52, 36)), G.smoothstep(1.8, 2.5, G.fnoise(rng, shape, beta=1.0, kmin=8)) * 0.7)
    pits = (rng.random(shape) < 0.015) * 1.0
    img = G.add_lum(img, -G.blur(pits, 0.5) * 40)
    return G.over(img, _c((120, 114, 104)), G.smoothstep(0.8, 2.2, G.fnoise(rng, shape, beta=2.5)) * 0.18)


def _brass(rng, shape=SH):
    img = _fill((158, 122, 58), shape)
    img = G.add_lum(img, G.fnoise(rng, shape, beta=2.5) * 12, (1, .9, .6))
    img = G.over(img, _c((84, 68, 40)), G.smoothstep(-0.3, 1.5, G.fnoise(rng, shape, beta=2.2)) * 0.55)
    img = G.over(img, _c((92, 120, 96)), G.smoothstep(1.6, 2.4, G.fnoise(rng, shape, beta=1.6)) * 0.4)
    img = G.over(img, _c((226, 198, 122)), G.smoothstep(1.0, 2.2, G.fnoise(rng, shape, beta=3.0, aniso=(1, 2))) * 0.4)
    return G.add_lum(img, G.fnoise(rng, shape, beta=0.5, kmin=20) * 3)


def _chrome(rng, shape=SH):
    """Old nickel/chrome plate: streaky reflections, dull haze, pitting and rust specks."""
    img = _fill((150, 152, 154), shape)
    img = G.add_lum(img, G.fnoise(rng, shape, beta=2.6, aniso=(6, 1)) * 26)
    img = G.add_lum(img, G.smoothstep(1.2, 2.2, G.fnoise(rng, shape, beta=3.0, aniso=(6, 1))) * 50)
    img = G.over(img, _c((140, 132, 116)), G.smoothstep(0.2, 1.8, G.fnoise(rng, shape, beta=2.0)) * 0.35)
    pits = (rng.random(shape) < 0.004) * 1.0
    img = G.over(img, _c((110, 70, 44)), np.clip(G.blur(pits, 0.6) * 3, 0, 0.8))
    return img


def tex_l39_housefurn_materials() -> None:
    """The shared tiling materials: woods, velvets, enamels, paint, iron, brass, chrome, tile,
    bed linen, mattress ticking and the patchwork quilt."""
    _save(_woodmat(39701, WALNUT, rings=6, craze=8), "l39_furn_walnut")
    rng = G.rng_for(39702)
    img = _wood(rng, *MAHOGANY, rings=9, figure=0.8, pores=0.35)
    img = G.add_lum(img, G.smoothstep(0.6, 2.2, G.fnoise(rng, SH, beta=3.0, aniso=(1, 3))) * 16, (1, .8, .7))
    _save(_finish(_craze(img, rng, 12), rng, 0.8), "l39_furn_mahogany")
    _save(_woodmat(39703, OAK, rings=8, rays=1.0, wear=1.2), "l39_furn_oak")
    rng = G.rng_for(39704)
    img = P._knots(_wood(rng, *PINE, rings=5, figure=0.4, pores=0.15), rng, 2)
    _save(_finish(img, rng, 1.3, 20), "l39_furn_pine")

    # Dining table top: four dark oak boards along x, ring marks and scratches.
    rng = G.rng_for(39705)
    img = np.zeros((S, S, 3))
    for i in range(4):
        b = _wood(rng, *OAK, vertical=False, rings=int(rng.integers(5, 9)), rays=0.8)
        b = b + rng.normal(0, 5)
        img[32 * i:32 * i + 32] = b[32 * i:32 * i + 32]
    seam = G.smoothstep(1.4, 0.3, np.abs(G.wrapd(Y, 32)))
    img = G.add_lum(img, -38 * seam + 10 * G.smoothstep(2.2, 1.4, G.wrapd(Y, 32)) * (G.wrapd(Y, 32) > 0))
    for _ in range(3):
        cx, cy, r = rng.uniform(20, 108), rng.uniform(20, 108), rng.uniform(7, 11)
        d = np.hypot(X - cx, Y - cy)
        ring = np.exp(-((d - r) / 0.9) ** 2) * (0.6 + 0.4 * np.cos(np.arctan2(Y - cy, X - cx) * 2 + rng.uniform(0, 6)))
        img = G.over(img, _c((176, 160, 136)), ring * 0.45)
    _save(_finish(_craze(img, rng, 7), rng, 1.4, 30), "l39_furn_table_top")

    _save(_velvet(G.rng_for(39706), (86, 104, 72)), "l39_furn_velvet_green")
    _save(_velvet(G.rng_for(39707), (112, 42, 50)), "l39_furn_velvet_red")
    _save(_enamel(G.rng_for(39708), (226, 224, 214), crazing=0.4, chips=3), "l39_furn_enamel")
    _save(_enamel(G.rng_for(39709), CREAM, crazing=0.5, chips=2), "l39_furn_enamel_cream")
    _save(_enamel(G.rng_for(39710), JADE, crazing=0.2, chips=2), "l39_furn_enamel_green")
    _save(_enamel(G.rng_for(39711), (232, 233, 228), crazing=0.9, chips=1, under=(150, 146, 136)), "l39_furn_porcelain")
    _save(_paint(G.rng_for(39712)), "l39_furn_cream_paint")
    _save(_black_iron(G.rng_for(39713)), "l39_furn_black_iron")
    _save(_brass(G.rng_for(39714)), "l39_furn_brass")
    _save(_chrome(G.rng_for(39715)), "l39_furn_chrome")

    # Counter top: white 4 px square tiles, dark grout, a crazed tile, a cracked one, a chip.
    rng = G.rng_for(39716)
    img = G._smooth_concrete(rng, (228, 226, 216), mottle=2.0, fine=0.5)
    ix, iy = (X // 32).astype(int), (Y // 32).astype(int)
    img = G.add_lum(img, rng.normal(0, 4, (4, 4))[iy, ix])
    img = G.over(img, _c((214, 204, 170)), rng.uniform(0, 0.35, (4, 4))[iy, ix])
    dg = np.minimum(np.abs(G.wrapd(X, 32)), np.abs(G.wrapd(Y, 32)))
    grout = G.smoothstep(1.9, 1.1, dg)
    img = G.add_lum(img, -14 * G.smoothstep(4.0, 1.6, dg) * (1 - grout))
    img = G.add_lum(img, 10 * G.smoothstep(4.0, 2.0, G.wrapd(X, 32)) * (G.wrapd(X, 32) > 0) * (1 - grout))
    gimg = G.add_lum(_fill((70, 64, 56)), G.fnoise(rng, SH, beta=1.5) * 10)
    img = G.over(img, gimg, grout)
    img = G.over(img, _c((60, 50, 38)), grout * G.smoothstep(0.0, 1.6, G.fnoise(rng, SH, beta=2.0)) * 0.6)
    crack = _lines_mask([P._wander(rng, 70, 40, 1.2, 3.0, 9, 0.4)], 0.8) * (ix == 2) * (iy == 1)
    img = G.add_lum(img, -60 * crack)
    img = _chip(img, rng, 31, 95, 2.4, (120, 112, 100), rust=False)
    _save(img, "l39_furn_tile")

    # Bed linen: yellowed cotton weave with soft creases and old stains.
    rng = G.rng_for(39717)
    img = _fill((214, 206, 186))
    img = G.add_lum(img, (np.sin(X * np.pi) * np.sin(Y * np.pi)) * 3 + G.fnoise(rng, SH, beta=0.3, kmin=30) * 2.5)
    f = G.fnoise(rng, SH, beta=3.2, aniso=(1, 2.5))
    gy, gx = np.gradient(G.blur(f, 2.0))
    img = G.add_lum(img, (gx * 0.6 + gy) * 160)
    for _ in range(3):
        img = G.mark_stain(img, rng, (190, 160, 100), size=(14, 12), a=0.25)
    _save(img, "l39_furn_linen")

    # Mattress ticking: cream with blue-grey stripes, rust and damp stains.
    rng = G.rng_for(39718)
    img = _fill((206, 196, 170))
    ph = X % 16
    stripe = G.smoothstep(0.6, 0.0, np.abs(ph - 3) - 1.6) + 0.8 * G.smoothstep(0.5, 0.0, np.abs(ph - 9) - 0.4)
    img = G.over(img, _c((92, 104, 124)), np.clip(stripe, 0, 1) * 0.85)
    img = G.add_lum(img, G.fnoise(rng, SH, beta=0.4, aniso=(1, 5), kmin=20) * 4 + G.fnoise(rng, SH, beta=2.6) * 5)
    for _ in range(2):
        img = G.mark_stain(img, rng, (150, 110, 70), size=(18, 16), a=0.3)
    _save(G.mark_damp(img, rng, dark=0.12), "l39_furn_ticking")

    _save(_quilt(G.rng_for(39719)), "l39_furn_quilt")


def _quilt(rng) -> np.ndarray:
    """Patchwork quilt: 2 px squares of faded calico prints, puffed and hand-quilted."""
    pal = [(176, 116, 112), (108, 124, 148), (186, 158, 92), (128, 144, 110), (212, 200, 170),
           (140, 106, 82), (148, 126, 146), (196, 168, 150)]
    n, p = 8, 16
    gx, gy = np.mgrid[0:p, 0:p][::-1] + 0.5
    cols = np.zeros((n, n), int)
    img = np.zeros((S, S, 3))
    for i in range(n):
        for j in range(n):
            bad = {cols[i - 1, j] if i else -1, cols[i, j - 1] if j else -1}
            c = int(rng.choice([k for k in range(len(pal)) if k not in bad]))
            cols[i, j] = c
            base = _c(pal[c])
            patch = G.add_lum(_fill(base, (p, p)), G.fnoise(rng, (p, p), beta=1.0) * 3)
            alt = G.lerp(base, (236, 228, 206), 0.55) if c not in (4,) else _c((150, 120, 100))
            kind = int(rng.integers(0, 5))
            if kind == 1:          # polka dots
                m = (np.hypot(gx % 4 - 2, gy % 4 - 2) < 0.9) * 1.0
            elif kind == 2:        # tiny sprigs
                m = np.zeros((p, p))
                for _ in range(3):
                    sx, sy = rng.uniform(3, 13, 2)
                    m = np.maximum(m, (np.hypot(gx - sx, gy - sy) < 1.3) * 1.0)
                    patch = G.over(patch, _c((112, 130, 92)), (np.hypot(gx - sx - 1.5, gy - sy - 1.2) < 0.7) * 0.8)
            elif kind == 3:        # pin stripes
                m = (np.abs((gx + gy * 0.0) % 4 - 2) < 0.5) * 1.0
            elif kind == 4:        # gingham
                m = 0.5 * ((gx % 6) < 3) + 0.5 * ((gy % 6) < 3)
            else:
                m = np.zeros((p, p))
            patch = G.over(patch, alt, m * 0.6)
            img[i * p:(i + 1) * p, j * p:(j + 1) * p] = patch
    fx, fy = X % p, Y % p
    e = np.minimum(np.minimum(fx, p - fx), np.minimum(fy, p - fy))
    img = G.add_lum(img, -16 * G.smoothstep(4.5, 0.0, e) + 4)
    img = G.add_lum(img, -30 * G.smoothstep(0.9, 0.2, e))
    stitch = (np.abs(fx - fy) < 0.5) * ((np.floor((fx + fy) / 2) % 2) == 0)
    img = G.add_lum(img, -10 * stitch)
    img = G.lerp(img, _c((176, 164, 140)), 0.22)
    fray = G.smoothstep(2.2, 2.6, G.fnoise(rng, SH, beta=2.2))
    img = G.over(img, G.add_lum(_fill((228, 222, 206)), G.fnoise(rng, SH, beta=0.5, kmin=20) * 8), fray)
    for _ in range(2):
        img = G.mark_stain(img, rng, (150, 120, 80), size=(20, 16), a=0.25)
    return G.mark_damp(img, rng, dark=0.1)


# =============================================================================
# Small part textures
# =============================================================================

def tex_l39_housefurn_parts() -> None:
    """Piano keys, book spines, lamp shade, dead bulb, bottle atlas, coil drum, tub/basin/bowl
    insides, boiler castings and the portrait."""
    _keys()
    _books()
    _lampshade()
    _bulb()
    _bottle()
    _coil()
    _tub_inside()
    _basin()
    _toilet_bowl()
    _boiler_tex()
    _picture()


def _keys():
    """Piano keys seen from above: two octaves per block, yellowed ivory, a few lost key tops,
    ebony sharps. Texture top = the front of the keys (toward the player)."""
    rng = G.rng_for(39720)
    w = S / 14
    k = np.floor(X / w).astype(int)
    img = _fill((216, 202, 162))
    img = G.add_lum(img, rng.normal(0, 5, 14)[k])
    img = G.over(img, _c((192, 164, 104)), (rng.uniform(0, 0.4, 14)[k]))
    img = G.add_lum(img, G.fnoise(rng, SH, beta=1.2, aniso=(1, 8)) * 4)
    lost = np.isin(k, [3, 10]) & (Y < 46 + 4 * G.fnoise(rng, SH, beta=1.0))
    img = G.over(img, G.add_lum(_fill((150, 118, 74)), G.fnoise(rng, SH, beta=1, aniso=(1, 8)) * 8), lost * 1.0)
    img = G.add_lum(img, -10 * G.smoothstep(1.5, 0.0, np.abs(Y - 48)) * (~lost))      # head/tail joint
    fx = X / w - np.floor(X / w)
    db = np.minimum(fx, 1 - fx) * w
    img = G.over(img, _c((40, 32, 26)), G.smoothstep(1.0, 0.25, db) * 0.85)
    img = G.add_lum(img, 18 * G.smoothstep(4, 0, Y))                                  # front lip catches light
    for octave in range(2):
        for i in (0, 1, 3, 4, 5):
            cx = (octave * 7 + i + 1) * w + rng.normal(0, 0.4)
            m = G.box(SH, cx - 0.29 * w, 52, cx + 0.29 * w, S + 4)
            blk = _fill((30, 26, 24))
            blk = G.add_lum(blk, 26 * G.box(SH, cx - 0.12 * w, 58, cx - 0.02 * w, S + 4))
            blk = G.add_lum(blk, 22 * G.smoothstep(60, 52, Y))
            img = G.add_lum(img, -30 * G.box(SH, cx - 0.29 * w, 50, cx + 0.42 * w, S + 4) * (1 - m))
            img = G.over(img, blk, m)
    img = G.mark_damp(img, rng, dark=0.12)
    _save(img, "l39_furn_keys")


def _books():
    """Four strips (4 px tall) of book spines standing on a shelf: faded cloth and leather,
    gilt bands, title labels, sun-bleached tops; dark gap above the shorter ones."""
    rng = G.rng_for(39721)
    pal = [(118, 40, 38), (52, 64, 98), (58, 84, 60), (150, 120, 76), (172, 142, 70), (44, 40, 38),
           (96, 112, 128), (122, 74, 54), (136, 56, 64), (80, 92, 70), (186, 170, 136)]
    img = np.zeros((S, S, 3))
    for strip in range(4):
        y1 = 32 * strip + 32
        img[y1 - 32:y1] = _c((40, 33, 27))
        x = 0.0
        while x < S:
            bw = float(rng.uniform(5, 11))
            top = y1 - float(rng.uniform(21, 32))
            col = _c(pal[int(rng.integers(len(pal)))])
            col = col * rng.uniform(0.8, 1.15)
            xs = slice(int(x), min(S, int(np.ceil(x + bw))))
            ys = slice(max(int(top), y1 - 32), y1)
            sub_x, sub_y = X[ys, xs], Y[ys, xs]
            u = np.clip((sub_x - x) / bw, 0, 1)
            spine = _fill(col, sub_x.shape)
            spine = G.add_lum(spine, -26 * (2 * u - 1) ** 2 + 10 * np.exp(-((u - 0.3) / 0.15) ** 2))
            spine = G.add_lum(spine, rng.normal(0, 3.5, sub_x.shape))
            fade = G.smoothstep(top + 12, top, sub_y)
            spine = G.lerp(spine, spine.mean(axis=2, keepdims=True) * 0.9 + 30, (0.45 * fade)[..., None])
            gold = _c((176, 146, 76))
            kind = int(rng.integers(0, 4))
            if kind in (0, 1):
                for gy in (top + 3, y1 - 4):
                    spine = G.over(spine, gold, G.smoothstep(0.9, 0.3, np.abs(sub_y - gy)) * 0.7)
            if kind == 1:
                spine = G.over(spine, _c((40, 30, 26)), ((sub_y > top + 6) & (sub_y < top + 11) & (u > 0.15) & (u < 0.85)) * 0.8)
                spine = G.over(spine, gold, ((sub_y > top + 7.6) & (sub_y < top + 8.6) & (u > 0.3) & (u < 0.7)) * 0.6)
            if kind == 2:
                for gy in np.linspace(top + 5, y1 - 5, 4):
                    spine = G.add_lum(spine, 22 * np.exp(-((sub_y - gy) / 0.6) ** 2) - 14 * np.exp(-((sub_y - gy - 1.1) / 0.6) ** 2))
            if kind == 3:
                spine = G.over(spine, _c((214, 200, 160)), ((sub_y > top + 8) & (sub_y < top + 14) & (u > 0.12) & (u < 0.88)) * 0.75)
            spine = G.add_lum(spine, 14 * (np.abs(sub_y - top) < 1.0))
            gap = np.abs(sub_x - x) < 0.7
            spine = G.add_lum(spine, -40 * gap)
            img[ys, xs] = spine
            x += bw
    img = G.add_lum(img, -12 * G.smoothstep(26, 32, Y % 32))     # shelf shadow at the foot of each row
    _save(img, "l39_furn_books")


def _lampshade():
    """Fringed cloth lamp shade: pleated faded rose silk, braid trim, a scorch from the bulb,
    a cut-out bead fringe along the bottom (rows 104-128)."""
    rng = G.rng_for(39722)
    img = _fill((198, 164, 136))
    img = G.add_lum(img, np.sin(X * 2 * np.pi / 8) * 7 + G.fnoise(rng, SH, beta=1.0, aniso=(1, 10)) * 4)
    img = G.add_lum(img, -24 * G.smoothstep(4, 0, np.minimum(X, S - X)))
    img = G.over(img, _c((150, 100, 70)), G.smoothstep(40, 0, Y) * G.smoothstep(0.6, 1.8, G.fnoise(rng, SH, beta=2.4) + 1) * 0.5)
    img = G.add_lum(img, G.fnoise(rng, SH, beta=2.8) * 6)
    for y0, y1 in ((0, 6), (96, 104)):
        band = (Y >= y0) & (Y < y1)
        braid = 0.5 + 0.5 * np.sin((X + Y) * np.pi / 2.5)
        img = np.where(band[..., None], G.add_lum(_fill((168, 136, 74)), braid * 22 - 10), img)
    alpha = np.full(SH, 255.0)
    fringe = Y >= 104
    strand = (X % 4) < 2.2
    bead = (np.abs(Y - 124) < 2.5) & (np.abs(X % 4 - 1.1) < 1.6)
    img = np.where(fringe[..., None], G.add_lum(_fill((170, 132, 96)), 20 * np.sin(Y * 1.3)), img)
    img = np.where((fringe & bead)[..., None], _c((120, 70, 60)), img)
    alpha[fringe & ~(strand | bead)] = 0
    img = G.mark_damp(img, rng, dark=0.15, y_range=(0.2, 0.7))
    _save(np.dstack([img, alpha]), "l39_furn_lampshade")


def _bulb():
    """A dead bulb: grey dusty glass, a broken dark filament, brass screw base at the bottom."""
    rng = G.rng_for(39723)
    img = _fill((138, 138, 132))
    img = G.add_lum(img, 40 * np.exp(-(((X - 44) / 18) ** 2 + ((Y - 40) / 26) ** 2)) - 26 * G.smoothstep(40, 64, np.abs(X - 64)))
    fil = _lines_mask([[(48, 70), (54, 54), (60, 70), (64, 56), (70, 70), (76, 54), (80, 70)],
                       [(52, 100), (50, 72)], [(78, 100), (80, 72)]], 2.2)
    img = G.over(img, _c((44, 40, 36)), fil * 0.85)
    img = G.add_lum(img, G.fnoise(rng, SH, beta=1.5) * 6)
    base = Y > 100
    img = np.where(base[..., None], G.add_lum(_fill((150, 120, 64)), 26 * np.sin(Y * 0.9)), img)
    _save(img, "l39_furn_bulb_dead")


def _bottle():
    """Almond Water bottle atlas (block px): body u0-10 (glass, milky almond water, cream label,
    blue band), clear glass u10-13 v0-8, blue cap u13-16 v0-4, liquid top u13-16 v4-8, rusty tin
    lid u10-16 v8-12, dusty brown paper u10-16 v12-16."""
    rng = G.rng_for(39724)
    img = _fill((196, 204, 200))
    body = X < 80
    u = X / 80
    glass = G.add_lum(_fill((188, 198, 196)), 30 * np.exp(-((u - 0.22) / 0.07) ** 2) - 30 * G.smoothstep(0.35, 0.5, np.abs(u - 0.5)))
    liq = G.add_lum(_fill((228, 214, 178)), 26 * np.exp(-((u - 0.22) / 0.08) ** 2) - 34 * G.smoothstep(0.3, 0.5, np.abs(u - 0.5)))
    liq = G.add_lum(liq, G.fnoise(rng, SH, beta=2.0) * 4)
    part = np.where((Y > 30)[..., None], liq, glass)
    part = G.add_lum(part, 18 * G.smoothstep(2, 0, np.abs(Y - 30)))
    lab = (Y > 60) & (Y < 96)
    label = G.add_lum(_fill((232, 220, 186)), G.fnoise(rng, SH, beta=1.4) * 5 - 20 * G.smoothstep(0.35, 0.5, np.abs(u - 0.5)))
    label = G.over(label, _c((134, 96, 58)), ((Y > 72) & (Y < 84)) * 0.9)
    label = G.over(label, _c((70, 92, 150)), ((Y > 62) & (Y < 65)) * 0.8)
    part = np.where(lab[..., None], label, part)
    part = G.add_lum(part, -30 * G.smoothstep(6, 0, np.minimum(X, 80 - X)))
    img = np.where(body[..., None], part, img)
    cg = (X >= 80) & (X < 104) & (Y < 64)
    img = np.where(cg[..., None], G.add_lum(_fill((196, 206, 204)), 40 * np.exp(-((X - 86) / 3) ** 2) - 20 * G.smoothstep(4, 0, np.minimum(X - 80, 104 - X))), img)
    cap = (X >= 104) & (Y < 32)
    img = np.where(cap[..., None], G.add_lum(_fill((48, 76, 156)), 18 * np.sin(X * 1.6) - 10 * (Y > 26)), img)
    top = (X >= 104) & (Y >= 32) & (Y < 64)
    img = np.where(top[..., None], G.add_lum(_fill((226, 212, 176)), G.fnoise(rng, SH, beta=2) * 4), img)
    lid = (X >= 80) & (Y >= 64) & (Y < 96)
    img = np.where(lid[..., None], _rust(rng), img)
    paper = (X >= 80) & (Y >= 96)
    img = np.where(paper[..., None], G.add_lum(_fill((168, 140, 100)), G.fnoise(rng, SH, beta=1.6) * 8), img)
    img = G.over(img, _c((150, 140, 120)), G.smoothstep(1.0, 2.4, G.fnoise(rng, SH, beta=1.8)) * 0.25)   # dust
    _save(img, "l39_furn_almond_bottle")


def _rust(rng):
    return P._rust(rng, (96, 84, 72), amount=0.6)


def _coil():
    """Monitor-top refrigerator drum: rings of black-painted coil tube with dust and rust."""
    rng = G.rng_for(39725)
    ph = Y % 6
    img = _fill((52, 52, 54))
    img = G.add_lum(img, 34 * np.cos((ph - 2.2) / 6 * 2 * np.pi) * (ph < 4.6) - 30 * (ph >= 4.6))
    img = G.add_lum(img, -26 * G.smoothstep(1.2, 0.2, np.abs(G.wrapd(X, 32))))
    img = G.add_lum(img, 14 * G.smoothstep(1.2, 0.2, np.abs(G.wrapd(X - 1.5, 32))))
    img = G.over(img, _c((150, 142, 126)), G.smoothstep(0.4, 2.0, G.fnoise(rng, SH, beta=2.2)) * 0.35 * (ph < 2.5))
    img = G.over(img, _c((110, 62, 40)), G.smoothstep(1.4, 2.4, G.fnoise(rng, SH, beta=1.6)) * 0.6)
    _save(img, "l39_furn_icebox_coil")


def _tub_inside():
    """Inside floor of the tub's tap end: drain under the spout, rust trail and a grey tide ring."""
    rng = G.rng_for(39726)
    img = _enamel(rng, (224, 222, 212), crazing=0.3, chips=0)
    cx, cy = 64, 100                                # drain at x 8, z 12.5
    trail = np.exp(-((X - cx - 1.5 * np.sin(Y / 3)) / 3.0) ** 2) * (Y > cy) * (Y < 112)
    trail += np.exp(-((X - cx) / 5) ** 2 - ((Y - 110) / 5) ** 2) + 0.35 * np.exp(-((X - cx) / 14) ** 2 - ((Y - 104) / 12) ** 2)
    img = G.over(img, _c((150, 96, 52)), np.clip(trail, 0, 1) * 0.55)
    d = np.hypot(X - cx, Y - cy)
    img = G.over(img, _c((150, 150, 146)), G.smoothstep(5.5, 4.5, d))
    img = G.over(img, _c((40, 38, 36)), G.smoothstep(4.0, 3.2, d) * ((np.abs(X - cx) % 2.6) < 1.2))
    img = G.over(img, _c((150, 140, 116)), G.smoothstep(1.2, 0.0, np.abs(X - 12)) * 0.3 + G.smoothstep(1.2, 0.0, np.abs(X - 116)) * 0.3)
    _save(img, "l39_furn_tub_inside")


def _basin():
    """Floor of a basin (mapped whole on the bowl): drain, rust run and a grime ring."""
    rng = G.rng_for(39727)
    img = _enamel(rng, (232, 232, 226), crazing=0.8, chips=0)
    d = np.hypot((X - 64) / 64, (Y - 64) / 64)
    img = G.add_lum(img, -26 * G.smoothstep(0.55, 1.0, d))
    img = G.over(img, _c((176, 160, 120)), np.exp(-((d - 0.8) / 0.06) ** 2) * 0.4)
    run = np.exp(-((X - 64 - 3 * np.sin(Y / 10)) / 5) ** 2) * (Y < 64) * G.smoothstep(4, 50, Y)
    img = G.over(img, _c((150, 96, 52)), run * 0.5)
    dd = np.hypot(X - 64, Y - 64)
    img = G.over(img, _c((160, 160, 156)), G.smoothstep(15, 13, dd))
    img = G.over(img, _c((36, 34, 32)), G.smoothstep(12, 10.5, dd) * (((X + Y) % 6) < 3.4))
    _save(img, "l39_furn_basin")


def _toilet_bowl():
    """Toilet bowl from above in block pixels (x 4.25-11.75, z 2.5-12.5): glazed rim, a deep
    bowl going into murky water, a brown stain line."""
    rng = G.rng_for(39728)
    img = _enamel(rng, (230, 230, 224), crazing=0.7, chips=0)
    d = np.hypot((X - 64) / 22, (Y - 54) / 30)
    img = G.add_lum(img, -60 * G.smoothstep(1.0, 0.4, d))
    img = G.add_lum(img, 26 * np.exp(-((d - 1.0) / 0.1) ** 2))
    w = np.hypot((X - 64) / 14, (Y - 62) / 17)
    water = G.add_lum(_fill((96, 92, 70)), G.fnoise(rng, SH, beta=2.4) * 8)
    img = G.over(img, water, G.smoothstep(1.0, 0.9, w))
    img = G.over(img, _c((120, 86, 50)), np.exp(-((w - 1.05) / 0.07) ** 2) * 0.7)
    _save(img, "l39_furn_toilet_bowl")


def _boiler_tex():
    """Round sectional coal boiler: body castings in 4 px rings, and the door atlas (fire door
    rows 0-88, ash-pit door rows 92-128, the pressure gauge face at u 12-16, v 12-16)."""
    rng = G.rng_for(39729)
    img = _black_iron(rng)
    ph = Y % 32
    img = G.add_lum(img, 22 * np.sin(ph / 32 * np.pi) - 8)
    img = G.add_lum(img, -46 * G.smoothstep(1.6, 0.3, np.abs(G.wrapd(Y, 32))))
    for _ in range(5):
        cx = rng.uniform(0, S)
        cy = 32 * rng.integers(0, 4) + 1
        run = np.exp(-((G.wrapd(X - cx, S)) / 2.2) ** 2) * ((Y - cy) % S < 26) * (1 - ((Y - cy) % S) / 26)
        img = G.over(img, _c((128, 70, 40)), run * 0.6)
    for k in range(8):
        img = _dome(img, 8 + 16 * k, 3.5, 1.6, (60, 56, 54), hl=0.3)
    _save(img, "l39_furn_boiler_body")

    rng = G.rng_for(39730)
    img = _black_iron(rng)
    fire = (4, 4, 124, 84)
    img = _panel(img, fire, 5, 24, raised=True)
    Xs, Ys = X - 64, Y - 46
    ang = np.arctan2(Ys, Xs)
    rib = G.smoothstep(0.75, 0.95, np.cos(ang * 16)) * G.box(SH, 14, 12, 114, 78) * (np.hypot(Xs, Ys) > 18)
    img = G.add_lum(img, 18 * rib - 8 * G.box(SH, 14, 12, 114, 78))
    d = np.hypot(Xs, Ys)
    img = G.add_lum(img, _emboss(G.smoothstep(17, 16, d), 30))
    img = G.add_lum(img, -30 * G.smoothstep(15, 14, d) * (np.cos(ang * 6) > 0.3))
    img = _dome(img, 64, 46, 3, (70, 64, 60))
    img = _dome(img, 112, 44, 4, (74, 70, 66))
    img = G.over(img, _c((60, 56, 54)), G.box(SH, 96, 41, 116, 47))
    img = _panel(img, (4, 92, 88, 124), 4, 20, raised=True)
    for k in range(6):
        img = G.add_lum(img, -40 * G.box(SH, 14 + 10 * k, 100, 19 + 10 * k, 116))
    img = _dome(img, 78, 108, 3, (70, 64, 60))
    gauge = (X >= 96) & (Y >= 96)
    gz = _fill((206, 194, 160))
    gd = np.hypot(X - 112, Y - 112)
    gz = G.over(gz, _c((150, 150, 150)), G.smoothstep(13, 14, gd))
    for t in np.linspace(-2.4, 0.6, 9):
        gz = G.over(gz, _c((40, 36, 30)), _lines_mask([[(112 + 9 * np.cos(t), 112 + 9 * np.sin(t)), (112 + 12 * np.cos(t), 112 + 12 * np.sin(t))]], 1.2))
    gz = G.over(gz, _c((140, 40, 30)), _lines_mask([[(112, 112), (112 + 10 * np.cos(-0.4), 112 + 10 * np.sin(-0.4))]], 1.6))
    img = np.where(gauge[..., None], gz, img)
    _save(img, "l39_furn_boiler_door")


def _picture():
    """The portrait in the hall: a faded, hand-tinted studio photograph of a woman in a
    high-collared dark dress, in an oval mat. Her eyes are dark hollows and there is no mouth.
    Painted 160 x 128 (the canvas is 10 x 8 px) and squeezed into 128 x 128."""
    rng = G.rng_for(39731)
    shp = (128, 160)
    Xs, Ys = G.grid(shp)
    img = _fill((96, 82, 64), shp)
    img = G.add_lum(img, G.fnoise(rng, shp, beta=2.4) * 9 + 28 * np.exp(-(((Xs - 60) / 46) ** 2 + ((Ys - 46) / 40) ** 2)))

    def ell(cx, cy, rx, ry, soft=2.0):
        return G.smoothstep(1.0 + soft / max(rx, ry), 1.0 - soft / max(rx, ry), np.hypot((Xs - cx) / rx, (Ys - cy) / ry))
    dress = ell(80, 150, 50, 62)
    img = G.over(img, G.add_lum(_fill((40, 36, 38), shp), G.fnoise(rng, shp, beta=2.0) * 6), dress)
    img = G.over(img, _c((44, 40, 42)), ell(80, 80, 9, 16) * (Ys > 70))
    img = G.over(img, _c((178, 158, 128)), ell(80, 72, 7, 8))
    face = ell(80, 52, 15, 20)
    skin = G.add_lum(_fill((188, 168, 136), shp), -24 * G.smoothstep(4, 16, np.abs(Xs - 78)) + G.fnoise(rng, shp, beta=2.6) * 4)
    img = G.over(img, skin, face)
    hair = np.clip(ell(80, 40, 21, 15, 5) + 0.8 * ell(80, 27, 9, 6, 4), 0, 1) * (1 - ell(80, 55, 13.5, 16, 3) * (Ys > 40))
    img = G.over(img, _c((52, 42, 34)), hair)
    for ex in (73, 87):
        img = G.over(img, _c((34, 28, 24)), ell(ex, 50, 3.6, 2.6, 2.5) * 0.92)
    img = G.add_lum(img, -10 * ell(80, 60, 1.5, 4, 2))
    img = G.add_lum(img, -6 * ell(80, 66, 6, 1.2, 1.5))
    img = G.add_lum(img, 12 * ell(132, 44, 7, 9, 4) - 9 * (ell(129.5, 42, 1.2, 1.0, 1) + ell(134.5, 42, 1.2, 1.0, 1)))
    img = G.over(img, _c((150, 120, 120)), (ell(72, 58, 4, 3, 3) + ell(88, 58, 4, 3, 3)) * 0.15)
    img = G.lerp(G.blur(img, 0.7), _c((128, 112, 88)), 0.28)
    img = G.add_lum(img, 30 * G.smoothstep(40, 160, Xs - Ys * 0.4))
    oval = np.hypot((Xs - 80) / 64, (Ys - 64) / 56)
    img = G.add_lum(img, -40 * G.smoothstep(0.7, 1.0, oval))
    mat = G.add_lum(_fill((40, 36, 32), shp), G.fnoise(rng, shp, beta=1.6) * 5)
    img = G.over(img, mat, G.smoothstep(0.98, 1.02, oval))
    img = G.add_lum(img, _emboss(G.smoothstep(1.02, 0.98, oval), -20, 1.0))
    spots = (rng.random(shp) < 0.004) * 1.0
    img = G.over(img, _c((120, 86, 50)), np.clip(G.blur(spots, 1.0) * 6, 0, 0.6))
    d = np.hypot(Xs - 20, Ys - 110)
    img = G.over(img, _c((110, 86, 54)), np.clip(G.smoothstep(34, 22, d) * 0.3 + np.exp(-((d - 30) / 2.0) ** 2) * 0.4, 0, 1))
    img = G.add_lum(img, 18 * np.exp(-((Xs + Ys * 0.8 - 120) / 10) ** 2))
    arr = Image.fromarray(np.clip(np.round(img), 0, 255).astype(np.uint8), "RGB").resize((S, S), Image.LANCZOS)
    _save(np.asarray(arr, float), "l39_furn_picture")


# =============================================================================
# Front textures (painted where each model's front box sits)
# =============================================================================

def tex_l39_housefurn_fronts() -> None:
    """Fronts of the cabinets and appliances: radio, wardrobe, chest of drawers and its mirror,
    piano, range, kitchen cupboards, Hoosier cabinet, monitor-top fridge."""
    _radio_front()
    _wardrobe_front()
    _dresser_front()
    _dresser_mirror()
    _piano_fronts()
    _stove_tex()
    _counter_fronts()
    _hoosier()
    _icebox_front()


def _mirror_glass(rng, shape, cx, cy, rx, ry):
    """Old silvered glass: a dim reflection of the room, desilvered black edges and spots."""
    Xs, Ys = G.grid(shape)
    img = _fill((80, 86, 88), shape)
    img = G.add_lum(img, 34 * np.exp(-(((Xs - (cx - rx * 0.35)) / (rx * 0.9)) ** 2 + ((Ys - (cy - ry * 0.45)) / (ry * 0.8)) ** 2)))
    img = G.add_lum(img, G.fnoise(rng, shape, beta=3.0, aniso=(1, 3)) * 6)
    img = G.add_lum(img, 24 * np.exp(-(((Xs - cx) * 1.0 + (Ys - cy) * 0.55 + rx * 0.3) / (rx * 0.12)) ** 2))
    img = G.add_lum(img, -14 * np.exp(-(((Xs - cx - rx * 0.2) / (rx * 0.25)) ** 2 + ((Ys - cy - ry * 0.1) / (ry * 0.5)) ** 2)))
    rr = np.hypot((Xs - cx) / rx, (Ys - cy) / ry)
    edge = G.smoothstep(0.72, 1.0, rr + 0.12 * G.fnoise(rng, shape, beta=1.8))
    img = G.over(img, _c((34, 32, 30)), edge * 0.75)
    img = G.over(img, _c((30, 28, 26)), G.smoothstep(2.0, 2.6, G.fnoise(rng, shape, beta=1.2, kmin=6)) * 0.85)
    img = G.over(img, _c((120, 96, 70)), G.smoothstep(1.5, 2.4, G.fnoise(rng, shape, beta=1.6)) * 0.3)
    img = G.add_lum(img, 30 * np.exp(-((rr - 0.94) / 0.03) ** 2))       # bevel catches the light
    return img


def _radio_front():
    """Walnut cathedral radio: book-matched veneer, an arched cloth grille behind gothic
    fretwork, a half-moon celluloid dial, a crossbanded lower panel."""
    rng = G.rng_for(39740)
    img = _wood(rng, *WALNUT, vertical=True, rings=7, figure=0.7)
    img[:, 64:] = img[:, :64][:, ::-1]
    for y in (14.0, 15.0, 15.75):                       # arch steps: lit top edge, shadowed foot
        r = 8 * (16 - y)
        img = G.add_lum(img, 18 * G.box(SH, 0, r, S, r + 1.2) - 16 * G.box(SH, 0, r - 1.6, S, r))
    img = G.add_lum(img, 14 * G.box(SH, 0, 20, S, 21.5) - 20 * G.box(SH, 0, 23, S, 24.5))
    # Lower panel with a light inlay line.
    pr = _fr(4.25, 2.0, 11.75, 5.0)
    img = _panel(img, pr, 3, 14)
    line = np.clip(G.box(SH, *_grow(pr, -4)) - G.box(SH, *_grow(pr, -5.5)), 0, 1)
    img = G.over(img, _c((196, 162, 110)), line * 0.75)

    hw, yb, yc = 2.9, 7.4, 11.1

    def opening(grow):
        return G.supersample(SH, lambda x, y: (
            ((np.abs(x / 8 - 8) <= hw + grow) & (16 - y / 8 <= yc) & (16 - y / 8 >= yb - grow))
            | (np.hypot(x / 8 - 8, 16 - y / 8 - yc) <= hw + grow)))
    hole, ring = opening(0.0), opening(0.65)
    img = G.add_lum(img, 16 * np.clip(ring - hole, 0, 1) + _emboss(ring, 30, 0.9))
    cloth = G.add_lum(_fill((150, 126, 84)), np.sin(X * np.pi) * np.sin(Y * np.pi) * 7 + G.fnoise(rng, SH, beta=1.0, aniso=(1, 6)) * 5)
    cloth = G.add_lum(cloth, -30 * G.smoothstep(0.5, 2.0, G.fnoise(rng, SH, beta=2.2)))
    img = G.over(img, cloth, hole)
    img = G.add_lum(img, _emboss(hole, -34, 0.9))
    cx, cyr = 64, 8 * (16 - yc)
    arc = [(cx + 17 * np.cos(t), cyr + 17 * np.sin(t)) for t in np.linspace(np.pi, 2 * np.pi, 24)]
    segs = [[(cx - 17, 8 * (16 - 9.6)), arc[0]], arc, [arc[-1], (cx + 17, 8 * (16 - 9.6))],
            [(cx - 23, 8 * (16 - 9.6)), (cx + 23, 8 * (16 - 9.6))]]
    for a in (6.85, 8.0, 9.15):
        segs.append([(8 * a, 8 * (16 - yb) + 2), (8 * a, cyr - (17 if a == 8.0 else 12))])
    segs.append([(cx - 9, cyr - 8), (cx, cyr - 17), (cx + 9, cyr - 8)])
    fret = _lines_mask(segs, 3.2) * hole
    fwood = _wood(rng, *WALNUT, rings=9) - 10
    img = G.over(img, fwood, fret)
    img = G.add_lum(img, _emboss(fret, 26, 0.6))
    # Dial: half-moon celluloid window in a brass bezel, ticks and a needle.
    dx, dy, dr = 64, 8 * (16 - 5.6), 11.0
    dd = np.hypot(X - dx, Y - dy)
    upper = Y <= dy + 1
    img = G.over(img, _c((160, 126, 62)), G.smoothstep(dr + 2.6, dr + 1.6, dd) * upper)
    face = G.add_lum(_fill((206, 180, 120)), -20 * (dd / dr) + G.fnoise(rng, SH, beta=1.4) * 4)
    img = G.over(img, face, G.smoothstep(dr + 0.6, dr - 0.4, dd) * upper)
    for t in np.linspace(np.pi * 1.1, np.pi * 1.9, 11):
        img = G.over(img, _c((60, 44, 30)), _lines_mask([[(dx + 7 * np.cos(t), dy + 7 * np.sin(t)), (dx + 9.5 * np.cos(t), dy + 9.5 * np.sin(t))]], 0.8) * 0.8)
    t = np.pi * 1.62
    img = G.over(img, _c((130, 40, 28)), _lines_mask([[(dx, dy), (dx + 9.5 * np.cos(t), dy + 9.5 * np.sin(t))]], 1.1))
    img = _dome(img, dx, dy, 1.6, (60, 40, 28), hl=0.3)
    for ax in (4.75, 11.25):
        img = _smudge(img, 8 * ax + 2, 8 * (16 - 5.75) + 2, 9, 9, 0.5, (30, 20, 14))
        img = _smudge(img, 8 * ax, 8 * (16 - 5.75), 16, 12, 0.18, (60, 44, 30), rng)
    img = _craze(img, rng, 10)
    img = G.lerp(img, img.mean(axis=2, keepdims=True) + 18, (0.25 * G.smoothstep(70, 0, Y))[..., None])
    _save(_finish(img, rng, 0.8, 10, wrap=False), "l39_furn_radio_front")


def _wardrobe_front():
    """Two-door walnut wardrobe, 2 blocks tall (painted as one 128 x 256 image, then split):
    raised-panel doors, an oval bevelled mirror on the left door, brass escutcheons, a plinth
    and the crown's frieze and cornice mouldings."""
    rng = G.rng_for(39741)
    shp = (2 * S, S)
    Xs, Ys = G.grid(shp)
    img = _wood(rng, *WALNUT, vertical=True, rings=6, shape=shp)
    top = 32.0

    def fr(a0, y0, a1, y1):
        return _fr(a0, y0, a1, y1, top)
    for a0, a1 in ((1.5, 7.95), (8.05, 14.5)):
        d = fr(a0, 1.5, a1, 28)
        img = G.add_lum(img, _emboss(G.box(shp, *d), 34))
        img = _gap(img, d, 0.8, 40, shp)
    img = _panel(img, fr(9.1, 3.2, 13.45, 13.5), 4, 16, shape=shp)
    img = _panel(img, fr(9.1, 15.5, 13.45, 26.5), 4, 16, shape=shp)
    img = _panel(img, fr(2.55, 3.2, 6.9, 7.6), 4, 16, shape=shp)
    cx, cy, rx, ry = 8 * 4.725, 8 * (32 - 17.6), 8 * 2.45, 8 * 8.0
    rr = np.hypot((Xs - cx) / rx, (Ys - cy) / ry)
    moulding = G.smoothstep(1.24, 1.18, rr)
    img = G.add_lum(img, _emboss(moulding, 30, 0.8) + 10 * moulding)
    glass = G.smoothstep(1.02, 0.98, rr)
    img = G.over(img, _mirror_glass(rng, shp, cx, cy, rx, ry), glass)
    img = G.add_lum(img, _emboss(glass, -26, 0.6))
    for ax in (7.4, 8.7):
        ex, ey = 8 * ax, 8 * (32 - 16.0)
        img = _dome(img, ex, ey, 2.6, (150, 118, 60), shp, hl=0.5, shadow=0.3)
        hole = G.supersample(shp, lambda x, y: (np.hypot(x - ex, y - ey + 0.4) < 0.9) | ((np.abs(x - ex) < 0.4) & (y > ey - 0.4) & (y < ey + 1.8)))
        img = G.over(img, _c((20, 16, 12)), hole)
        img = _smudge(img, ex, ey + 8, 6, 14, 0.25, (40, 26, 16), shape=shp)
    # Plinth (y 0-1.5) and the crown faces (y 28-31): beads, dentils, cornice.
    img = G.add_lum(img, 14 * G.box(shp, 0, 8 * (32 - 1.5), S, 8 * (32 - 1.5) + 1.5) - 26 * G.box(shp, 0, 8 * (32 - 0.25), S, 2 * S))
    fz = (Ys >= 8 * (32 - 29)) & (Ys < 8 * (32 - 28))
    img = G.add_lum(img, fz * (-30 * ((Xs % 6) > 3.5) + 6))
    img = G.add_lum(img, 22 * np.exp(-((Ys - 8 * (32 - 29.5)) / 2.0) ** 2) - 20 * np.exp(-((Ys - 8 * (32 - 30)) / 1.5) ** 2))
    img = G.add_lum(img, 18 * np.exp(-((Ys - 8 * (32 - 30.85)) / 1.2) ** 2))
    img = _craze(img, rng, 7, 160, shp)
    img = _finish(img, rng, 0.9, 18, shp, wrap=False)
    img = G.over(img, _c((60, 46, 34)), G.smoothstep(8 * (32 - 4), 8 * 32, Ys) * 0.3)
    _save(img[:S], "l39_furn_wardrobe_upper")
    _save(img[S:], "l39_furn_wardrobe_lower")


DRAWERS = ((10.4, 12.2), (7.65, 10.1), (4.6, 7.35), (1.3, 4.3))     # (y0, y1) of the 4 drawers
PULL_A = (4.4, 11.6)                                                 # pull centres (viewer a)


def _dresser_front():
    """Walnut chest of four graduated drawers: lipped fronts, brass bail-pull backplates,
    keyhole escutcheons, worn round the pulls."""
    rng = G.rng_for(39742)
    img = _wood(rng, *WALNUT, vertical=False, rings=5, figure=0.7)
    img = G.add_lum(img, -14 * G.box(SH, *_fr(1.25, 1.0, 14.75, 12.5)))
    for y0, y1 in DRAWERS:
        d = _fr(1.75, y0, 14.25, y1)
        img = _gap(img, d, 1.0, 50)
        img = G.add_lum(img, _emboss(G.box(SH, *d), 34) + 6 * G.box(SH, *d))
        line = np.clip(G.box(SH, *_grow(d, -2.5)) - G.box(SH, *_grow(d, -3.5)), 0, 1)
        img = G.add_lum(img, -14 * line)
        yc = 8 * (16 - (y0 + y1) / 2)
        for a in PULL_A:
            px = 8 * a
            plate = G.supersample(SH, lambda x, y: ((x - px) / 6.5) ** 2 + ((y - yc) / 3.6) ** 2 < 1)
            img = _smudge(img, px, yc, 10, 6, 0.3, (40, 26, 16))
            img = G.over(img, _brass(rng) - 10, plate)
            img = G.add_lum(img, _emboss(plate, 24, 0.5))
            for bx in (px - 4, px + 4):
                img = _dome(img, bx, yc, 1.1, (170, 136, 70), hl=0.6, shadow=0.3)
        ex = 64
        img = _dome(img, ex, yc, 2.0, (150, 118, 60), hl=0.4, shadow=0.2)
        img = G.over(img, _c((20, 16, 12)), G.supersample(SH, lambda x, y: (np.hypot(x - ex, y - yc + 0.3) < 0.7) | ((np.abs(x - ex) < 0.3) & (y > yc) & (y < yc + 1.5))))
    img = _craze(img, rng, 8)
    _save(_finish(img, rng, 1.0, 16, wrap=False), "l39_furn_dresser_front")


def _oval_alpha(rx=0.97, ry=0.985):
    return G.supersample(SH, lambda x, y: ((x - 64) / (64 * rx)) ** 2 + ((y - 64) / (64 * ry)) ** 2 < 1) >= 0.5


def _dresser_mirror():
    """The swing mirror (mapped whole on a 10 x 13 px plate): carved walnut oval frame round
    desilvered glass; transparent outside the oval. Plus its plain walnut back."""
    rng = G.rng_for(39743)
    rr = np.hypot((X - 64) / 62, (Y - 64) / 63)
    img = _wood(rng, *WALNUT, vertical=True, rings=6)
    img = G.add_lum(img, 26 * np.cos((rr - 0.87) / 0.13 * np.pi * 1.5) * (rr > 0.8))
    img = G.add_lum(img, -20 * G.smoothstep(0.96, 1.0, rr))
    ang = np.arctan2(Y - 64, X - 64)
    img = G.add_lum(img, -14 * (np.cos(ang * 24) > 0.6) * (rr > 0.84) * (rr < 0.9))
    glass = G.smoothstep(0.8, 0.78, rr)
    img = G.over(img, _mirror_glass(rng, SH, 64, 64, 50, 50), glass)
    img = G.add_lum(img, _emboss(glass, -30, 0.6))
    alpha = np.where(_oval_alpha(), 255.0, 0.0)
    _save(np.dstack([_finish(img, rng, 0.6, 8, wrap=False), alpha]), "l39_furn_dresser_mirror")
    back = _wood(G.rng_for(39744), *WALNUT, vertical=True, rings=5)
    back = G.add_lum(back, -18 * G.smoothstep(0.8, 0.78, rr) - 10)
    _save(np.dstack([back, alpha]), "l39_furn_dresser_mirror_back")


def _piano_fronts():
    """Mahogany upright piano: lower bottom-board with two raised panels and carved scrolls,
    upper front with the folded fallboard (gilt pinstripe) and a fretwork music-desk panel
    over faded red cloth. One panel set per block, so both halves use the same textures."""
    rng = G.rng_for(39745)
    img = _wood(rng, *MAHOGANY, vertical=True, rings=9, figure=0.8)
    img = G.add_lum(img, -24 * G.box(SH, 0, 8 * (16 - 0.75), S, S))
    for a0, a1 in ((1.25, 7.5), (8.5, 14.75)):
        p = _fr(a0, 1.5, a1, 8.0)
        img = _panel(img, p, 4, 18, raised=True)
        cx, cy = (p[0] + p[2]) / 2, (p[1] + p[3]) / 2
        scroll = [[(cx - 14 + 14 * t, cy + 6 * np.sin(t * 2 * np.pi) - 2) for t in np.linspace(0, 1, 16)],
                  [(cx - 6 + 4 * np.cos(t), cy + 4 * np.sin(t)) for t in np.linspace(0, 5, 12)],
                  [(cx + 6 + 4 * np.cos(-t), cy + 4 * np.sin(-t)) for t in np.linspace(0, 5, 12)]]
        m = _lines_mask(scroll, 1.4)
        img = G.add_lum(img, -26 * m + 14 * np.roll(np.roll(m, 1, 0), 1, 1) * (1 - m))
    img = _craze(img, rng, 9)
    img = G.add_lum(img, 14 * _scratches(rng, 10, wrap=False, length=(3, 12)) * G.smoothstep(100, 128, Y))
    _save(_finish(img, rng, 0.7, 6, wrap=False), "l39_furn_piano_lower")

    rng = G.rng_for(39746)
    img = _wood(rng, *MAHOGANY, vertical=True, rings=9, figure=0.8)
    fb = (0, 8 * (16 - 12.25), S, 8 * (16 - 11))                      # folded fallboard
    img = G.add_lum(img, _emboss(G.box(SH, *fb), 26) + 6 * G.box(SH, *fb))
    img = G.over(img, _c((176, 146, 76)), G.box(SH, 6, fb[1] + 3, S - 6, fb[1] + 4) * 0.6)
    desk = _fr(1.6, 12.6, 14.4, 15.0)
    img = _panel(img, desk, 3, 16, raised=False)
    inner = _grow(desk, -4)
    cloth = G.add_lum(_fill((96, 40, 38)), np.sin(X * np.pi) * 4 + G.fnoise(rng, SH, beta=1.6) * 6)
    cx, cy = (inner[0] + inner[2]) / 2, (inner[1] + inner[3]) / 2
    holes = np.zeros(SH)
    for k in range(-2, 3):
        holes = np.maximum(holes, _ellipse(cx + 18 * k, cy, 6.5, 5.5))
    holes = holes * (1 - _lines_mask([[(cx + 18 * k - 6, cy), (cx + 18 * k + 6, cy)] for k in range(-2, 3)]
                                     + [[(cx + 18 * k, cy - 6), (cx + 18 * k, cy + 6)] for k in range(-2, 3)], 1.8))
    holes *= G.box(SH, *inner)
    img = G.over(img, cloth, holes)
    img = G.add_lum(img, _emboss(holes, -30, 0.5))
    img = G.add_lum(img, 18 * np.exp(-((Y - 8 * (16 - 15.6)) / 1.6) ** 2) - 16 * np.exp(-((Y - 8 * (16 - 15.2)) / 1.2) ** 2))
    img = _craze(img, rng, 9)
    _save(_finish(img, rng, 0.7, 6, wrap=False), "l39_furn_piano_upper")


def _enamel_panel(rng, base, frame, rect_list, shape=SH):
    """Cream enamel sheet with green-framed panels (the range's doors and sides)."""
    img = _enamel(rng, base, crazing=0.25, chips=0, shape=shape)
    gimg = _enamel(rng, frame, crazing=0.1, chips=0, shape=shape)
    for r, w in rect_list:
        band = np.clip(G.box(shape, *r) - G.box(shape, *_grow(r, -w)), 0, 1)
        img = G.over(img, gimg, band)
        img = G.add_lum(img, _emboss(G.box(shape, *r), 24, 0.6) + _emboss(G.box(shape, *_grow(r, -w)), -18, 0.6))
    return img


def _stove_tex():
    """Cream-and-green enamel range of the 1920s: oven door with a thermometer and a nameplate,
    broiler and storage doors, a louvred kick; cast-iron cooktop with four burners; framed
    side panel."""
    rng = G.rng_for(39750)
    oven = _fr(1.0, 4.0, 8.75, 12.0)
    broil = _fr(9.5, 8.75, 15.25, 12.0)
    store = _fr(9.5, 4.0, 15.25, 8.25)
    img = _enamel_panel(rng, CREAM, JADE, [((0, 8 * (16 - 13), S, 8 * (16 - 3)), 4), (oven, 5), (broil, 4), (store, 4)])
    for r in (oven, broil, store):
        img = _gap(img, r, 0.8, 40)
    img = G.add_lum(img, -34 * G.box(SH, *_fr(1.0, 3.2, 15.0, 3.9)) * ((X % 6) < 3))
    gx, gy = 8 * 4.875, 8 * (16 - 9.9)
    gd = np.hypot(X - gx, Y - gy)
    img = G.over(img, _chrome(rng), G.smoothstep(8.5, 7.5, gd))
    img = G.over(img, _c((226, 218, 196)), G.smoothstep(6.5, 5.5, gd))
    for t in np.linspace(np.pi * 0.8, np.pi * 2.2, 9):
        img = G.over(img, _c((50, 46, 40)), _lines_mask([[(gx + 3.6 * np.cos(t), gy + 3.6 * np.sin(t)), (gx + 5.4 * np.cos(t), gy + 5.4 * np.sin(t))]], 0.8) * 0.8)
    img = G.over(img, _c((140, 40, 30)), _lines_mask([[(gx, gy), (gx + 5 * np.cos(4.3), gy + 5 * np.sin(4.3))]], 1.0))
    plate = _ellipse(gx, 8 * (16 - 7.2), 18, 6)
    img = G.over(img, _chrome(rng), plate)
    img = G.add_lum(img, _emboss(plate, 22, 0.5) - 26 * _lines_mask([[(gx - 12, 8 * (16 - 7.2)), (gx - 6, 8 * (16 - 7.3)), (gx, 8 * (16 - 7.1)), (gx + 7, 8 * (16 - 7.3)), (gx + 12, 8 * (16 - 7.2))]], 1.2))
    for _ in range(5):
        img = _chip(img, rng, rng.uniform(8, 120), rng.uniform(30, 100), rng.uniform(1.2, 2.4))
    img = _smudge(img, 40, 30, 50, 10, 0.3, (120, 90, 50), rng)
    img = _smudge(img, 64, 104, 70, 10, 0.3, (70, 56, 40), rng)
    _save(img, "l39_furn_stove_front")

    rng = G.rng_for(39751)
    img = _black_iron(rng)
    edge = np.clip(1 - G.box(SH, 6, 24, 122, 122), 0, 1)
    img = G.over(img, _enamel(rng, CREAM, 0.2, 0), edge)
    img = G.add_lum(img, _emboss(G.box(SH, 6, 24, 122, 122), -24))
    for bx, bz in ((4.25, 6.0), (11.75, 6.0), (4.25, 12.25), (11.75, 12.25)):
        cx, cy = 8 * bx, 8 * bz
        d = np.hypot(X - cx, Y - cy)
        img = G.add_lum(img, -30 * G.smoothstep(24, 20, d))
        ang = np.arctan2(Y - cy, X - cx)
        grate = np.clip(G.smoothstep(1.4, 0.4, np.abs(d - 19)) + G.smoothstep(1.2, 0.4, np.abs(d - 12)) +
                        (G.smoothstep(0.92, 0.98, np.cos(ang * 6)) * (d > 7) * (d < 20)), 0, 1)
        img = G.over(img, _black_iron(rng) + 18, grate)
        img = G.add_lum(img, _emboss(grate, 26, 0.5))
        img = _dome(img, cx, cy, 6.5, (60, 56, 52), hl=0.3)
        img = G.over(img, _c((20, 18, 16)), G.smoothstep(4.0, 3.4, d) * ((np.cos(ang * 8) > 0.2) * 1.0) * (d > 2.5))
    img = _smudge(img, 64, 70, 40, 30, 0.4, (90, 60, 34), rng)
    _save(img, "l39_furn_stove_top")

    rng = G.rng_for(39752)
    img = _enamel_panel(rng, CREAM, JADE, [((0, 0, S, S), 7), ((16, 16, 112, 112), 4)])
    for k in range(4):
        img = _dome(img, 4 + 120 * (k % 2), 4 + 120 * (k // 2), 1.6, (160, 160, 156), hl=0.6, shadow=0.2)
    for _ in range(3):
        img = _chip(img, rng, rng.uniform(10, 118), rng.uniform(10, 118), rng.uniform(1.2, 2.2))
    _save(G.mark_damp(img, rng, dark=0.1, y_range=(0.5, 1.0)), "l39_furn_stove_side")


def _counter_fronts():
    """Cream painted kitchen cupboards: a drawer over a panelled door (counter); two doors
    under the sink (whose top half hides behind the sink's apron)."""
    rng = G.rng_for(39753)
    img = _paint(rng)
    drawer = _fr(1.0, 10.5, 15.0, 13.4)
    door = _fr(1.0, 1.75, 15.0, 10.0)
    for r in (drawer, door):
        img = _gap(img, r, 0.9, 44)
        img = G.add_lum(img, _emboss(G.box(SH, *r), 30))
    img = _panel(img, _fr(2.3, 3.0, 13.7, 8.8), 3.5, 16)
    img = _smudge(img, 64, 8 * (16 - 12), 20, 8, 0.3, (110, 90, 60), rng)
    img = _smudge(img, 8 * 14, 8 * (16 - 8.6), 8, 16, 0.35, (110, 90, 60), rng)
    img = G.mark_scuffs(img, rng, (90, 76, 60), n=6, y_range=(0.7, 0.8), a=0.4)
    _save(img, "l39_furn_counter_front")

    rng = G.rng_for(39754)
    img = _paint(rng)
    for a0, a1 in ((1.0, 7.9), (8.1, 15.0)):
        r = _fr(a0, 1.75, a1, 8.0)
        img = _gap(img, r, 0.9, 44)
        img = G.add_lum(img, _emboss(G.box(SH, *r), 30))
        img = _panel(img, _fr(a0 + 1.1, 2.8, a1 - 1.1, 7.0), 3, 14)
    for a in (7.2, 8.8):
        img = _smudge(img, 8 * a, 8 * (16 - 6.5), 6, 14, 0.35, (110, 90, 60), rng)
    img = _smudge(img, 64, 8 * (16 - 9), 50, 6, 0.3, (100, 84, 60), rng)
    img = G.mark_scuffs(img, rng, (90, 76, 60), n=6, y_range=(0.7, 0.8), a=0.4)
    _save(img, "l39_furn_kitchen_sink_front")


def _hoosier():
    """Hoosier cabinet in cream paint. Lower: three drawers on the left, a cupboard door on the
    right. Upper: a tilting tin flour bin with a window on the left, two glazed doors over
    shelves of plates and cups."""
    rng = G.rng_for(39755)
    img = _paint(rng)
    for y0, y1 in ((9.9, 13.4), (6.0, 9.6), (1.5, 5.7)):
        r = _fr(1.0, y0, 6.6, y1)
        img = _gap(img, r, 0.9, 44)
        img = G.add_lum(img, _emboss(G.box(SH, *r), 30))
        img = _smudge(img, 8 * 3.8, 8 * (16 - (y0 + y1) / 2), 10, 6, 0.3, (110, 90, 60), rng)
    door = _fr(7.0, 1.5, 15.0, 13.4)
    img = _gap(img, door, 0.9, 44)
    img = G.add_lum(img, _emboss(G.box(SH, *door), 30))
    img = _panel(img, _fr(8.2, 2.8, 13.8, 12.1), 3.5, 16)
    img = _smudge(img, 8 * 7.6, 8 * (16 - 10), 8, 14, 0.35, (110, 90, 60), rng)
    img = G.mark_scuffs(img, rng, (90, 76, 60), n=6, y_range=(0.78, 0.86), a=0.4)
    _save(img, "l39_furn_hoosier_lower")

    rng = G.rng_for(39756)
    img = _paint(rng, chips=0.6)
    inside = G.add_lum(_fill((64, 58, 50)), G.fnoise(rng, SH, beta=2.0) * 6)
    for sy in (6.6, 1.0):
        inside = G.over(inside, _c((150, 140, 116)), G.box(SH, 0, 8 * (16 - sy) - 2, S, 8 * (16 - sy)))
        inside = G.add_lum(inside, -20 * G.box(SH, 0, 8 * (16 - sy), S, 8 * (16 - sy) + 3))
    plates = [(7.0, 3.3, 1.9), (8.3, 3.3, 1.9), (9.6, 3.3, 1.9), (11.6, 2.6, 1.3), (13.4, 3.1, 1.8),
              (6.9, 8.7, 1.6), (12.6, 8.9, 1.9)]
    for a, y, r in plates:
        px, py, pr = 8 * a, 8 * (16 - y), 8 * r
        d = np.hypot(X - px, Y - py)
        p = G.add_lum(_fill((214, 206, 184)), -16 * d / pr)
        p = G.over(p, _c((90, 110, 150)), G.smoothstep(0.7, 0.0, np.abs(d - pr * 0.82)) * 0.8)
        inside = G.over(inside, p, G.smoothstep(pr + 0.5, pr - 0.5, d))
    for a in (9.2, 10.4):
        cup = G.box(SH, 8 * a - 4, 8 * (16 - 8.6), 8 * a + 4, 8 * (16 - 6.75))
        inside = G.over(inside, G.add_lum(_fill((200, 190, 166)), -12 * np.abs(X - 8 * a) / 4), cup)
    for a0, a1 in ((5.9, 10.3), (10.4, 14.6)):
        r = _fr(a0, 1.0, a1, 12.8)
        img = _gap(img, r, 0.8, 40)
        img = G.add_lum(img, _emboss(G.box(SH, *r), 30))
        for y0, y1 in ((1.6, 6.6), (7.2, 12.2)):
            pane = G.box(SH, *_fr(a0 + 0.6, y0, a1 - 0.6, y1))
            glass = G.add_lum(inside, 22 * np.exp(-(((X + Y * 0.6) % 70 - 30) / 6) ** 2))
            glass = G.over(glass, _c((150, 146, 130)), G.smoothstep(0.5, 2.0, G.fnoise(rng, SH, beta=2.2)) * 0.2)
            img = G.over(img, glass, pane)
            img = G.add_lum(img, _emboss(pane, -24, 0.5))
    bin_r = _fr(1.3, 1.0, 5.7, 12.8)
    tin = G.add_lum(_paint(rng, (214, 204, 172), chips=0.3), G.fnoise(rng, SH, beta=2.8) * 5)
    img = G.over(img, tin, G.box(SH, *bin_r))
    img = _gap(img, bin_r, 0.8, 40)
    img = G.add_lum(img, _emboss(G.box(SH, *bin_r), 30))
    img = _panel(img, _grow(bin_r, -5), 2.5, 12, raised=True)
    wx, wy0, wy1 = 8 * 3.5, 8 * (16 - 10.0), 8 * (16 - 6.4)
    win = G.supersample(SH, lambda x, y: ((np.abs(x - wx) < 9) & (y > wy0) & (y < wy1)) | (np.hypot(x - wx, y - wy0) < 9))
    flour = G.add_lum(_fill((232, 226, 210)), G.fnoise(rng, SH, beta=1.4) * 4)
    winc = np.where((Y > wy0 + 12 + 2 * np.sin(X / 3))[..., None], flour, _c((70, 64, 56)))
    img = G.over(img, winc, win)
    img = G.add_lum(img, _emboss(win, -30, 0.6))
    lab = G.box(SH, wx - 11, 8 * (16 - 5.4), wx + 11, 8 * (16 - 3.8))
    img = G.over(img, G.add_lum(_fill((186, 164, 116)), G.fnoise(rng, SH, beta=1.4) * 6), lab)
    img = G.add_lum(img, -30 * _lines_mask([[(wx - 8, 8 * (16 - 4.6)), (wx - 3, 8 * (16 - 4.75)), (wx + 2, 8 * (16 - 4.5)), (wx + 8, 8 * (16 - 4.65))]], 1.2))
    img = G.add_lum(img, 18 * np.exp(-((Y - 8 * (16 - 13.1)) / 1.4) ** 2) - 16 * np.exp(-((Y - 8 * (16 - 12.9)) / 1.0) ** 2))
    _save(img, "l39_furn_hoosier_upper")


def _icebox_front():
    """Monitor-top fridge front: white enamel cabinet, a rounded door with a pressed groove,
    a round blue-and-chrome badge, a louvred kick plate, rust at the chipped bottom corners."""
    rng = G.rng_for(39757)
    img = _enamel(rng, (228, 228, 220), crazing=0.3, chips=0)
    door = _fr(2.5, 2.5, 13.5, 11.5)
    rd = G.supersample(SH, lambda x, y: (np.maximum(np.abs(x - (door[0] + door[2]) / 2) - (door[2] - door[0]) / 2 + 5, 0) ** 2
                                         + np.maximum(np.abs(y - (door[1] + door[3]) / 2) - (door[3] - door[1]) / 2 + 5, 0) ** 2) < 25)
    img = G.add_lum(img, _emboss(rd, 40, 1.2) - 30 * np.clip(G.box(SH, *_grow(door, 1.2)) - rd, 0, 1))
    groove = np.clip(G.box(SH, *_grow(door, -5)) - G.box(SH, *_grow(door, -6.2)), 0, 1)
    img = G.add_lum(img, -20 * groove + 12 * np.roll(np.roll(groove, 1, 0), 1, 1))
    bx, by = 64, 8 * (16 - 10.3)
    bd = np.hypot(X - bx, Y - by)
    img = G.over(img, _chrome(rng), G.smoothstep(5.2, 4.4, bd))
    img = G.over(img, _c((44, 62, 112)), G.smoothstep(3.6, 3.0, bd))
    img = G.add_lum(img, 60 * _lines_mask([[(bx - 2, by + 1.5), (bx - 1, by - 1.5), (bx + 0.5, by + 1), (bx + 2, by - 1.5)]], 0.8))
    img = G.add_lum(img, -40 * G.box(SH, *_fr(3.0, 2.0, 13.0, 2.4)) * ((X % 4) < 2))
    for hy in (3.6, 10.4):
        img = G.over(img, _chrome(rng), G.box(SH, *_fr(2.55, hy - 0.5, 3.0, hy + 0.5)))
    for cx, cy in ((8 * 3.0, 8 * (16 - 2.8)), (8 * 13.0, 8 * (16 - 2.8))):
        img = _chip(img, rng, cx, cy, 2.6)
    img = _smudge(img, 8 * 12.4, 8 * (16 - 7.5), 9, 20, 0.3, (150, 130, 96), rng)
    _save(G.mark_damp(img, rng, dark=0.1, y_range=(0.6, 1.0)), "l39_furn_icebox_front")


# =============================================================================
# Models
# =============================================================================

def _tx(**names) -> dict:
    """Texture variables; the first one doubles as the particle texture."""
    t = {k: _ref(v) for k, v in names.items()}
    return {"particle": next(iter(t.values())), **t}


def _face(default, **over) -> dict:
    d = {f: default for f in FACES}
    d.update(over)
    return d


def _sp(f, t, tex, **kw):
    """An element spec (kept as data so a half can be mirrored before it's built)."""
    return (tuple(f), tuple(t), tex, kw)


def _build(specs):
    return [P._el(f, t, tex, **kw) for f, t, tex, kw in specs]


_SWAP = {"east": "west", "west": "east"}


def _mirror(specs):
    """Mirror specs left-right (x -> 16 - x): the second half of a WIDE pair."""
    out = []
    for f, t, tex, kw in specs:
        kw = dict(kw)
        if isinstance(tex, dict):
            tex = {_SWAP.get(k, k): v for k, v in tex.items()}
        if "faces" in kw:
            kw["faces"] = tuple(_SWAP.get(k, k) for k in kw["faces"])
        if isinstance(kw.get("uv"), dict):
            kw["uv"] = {_SWAP.get(k, k): v for k, v in kw["uv"].items()}
        if kw.get("rot"):
            (ox, oy, oz), axis, ang = kw["rot"]
            kw["rot"] = ((16 - ox, oy, oz), axis, -ang if axis in ("y", "z") else ang)
        out.append(((16 - t[0], f[1], f[2]), (16 - f[0], t[1], t[2]), tex, kw))
    return out


def _model(name, tex, elements):
    P._bm(name, P._model(tex, elements))


def _furn_state(name, model, extra=None) -> None:
    """FACING x INVERTED (x one extra property) variants. Upside down = "x": 180 plus half a
    turn round y, which keeps the back against the same wall (Level39FurnitureBlock.flip)."""
    variants = {}
    vals = list(extra[1]) if extra else [None]
    for facing, y in P.FACING_Y.items():
        for inv in (False, True):
            for val in vals:
                key = f"facing={facing},inverted={'true' if inv else 'false'}"
                if extra:
                    key += f",{extra[0]}={str(val).lower()}"
                v = {"model": _ref(model(val) if callable(model) else model)}
                if inv:
                    v["x"] = 180
                yy = (y + 180) % 360 if inv else y
                if yy:
                    v["y"] = yy
                variants[key] = v
    P._bs(name, {"variants": variants})


def _single(name, tex, elements) -> None:
    _model(name, tex, elements)
    _furn_state(name, name)
    P._im(name, parent=_ref(name))


def _pair(name, tex, first, second) -> None:
    _model(name, tex, first)
    _model(f"{name}_second", tex, second)
    _furn_state(name, lambda s: f"{name}_second" if s else name, ("second", (False, True)))
    P._im(name, parent=_ref(name))


E = P._el


def _armchair():
    """Wingback: turned walnut feet and seat rail, cushion, scroll arms, wings, tall back."""
    t = _tx(vel="l39_furn_velvet_green", wood="l39_furn_walnut")
    e = []
    for x0 in (2.0, 12.5):
        for z0 in (2.0, 13.0):
            e += [E((x0, 0, z0), (x0 + 1.5, 0.75, z0 + 1.5), "#wood"),
                  E((x0 + 0.25, 0.75, z0 + 0.25), (x0 + 1.25, 2.5, z0 + 1.25), "#wood")]
    e += [E((1.5, 2.5, 1.75), (14.5, 4.0, 14.5), "#wood"),
          E((3.5, 4.0, 2.0), (12.5, 6.0, 12.0), "#vel"),
          E((3.5, 6.0, 1.5), (12.5, 7.75, 11.75), "#vel"),
          E((3.75, 7.75, 1.75), (12.25, 8.25, 11.5), "#vel"),
          E((1.0, 4.0, 1.75), (3.5, 9.5, 15.5), "#vel"),
          E((12.5, 4.0, 1.75), (15.0, 9.5, 15.5), "#vel"),
          E((1.0, 9.5, 2.25), (3.5, 10.75, 14.75), "#vel"),
          E((12.5, 9.5, 2.25), (15.0, 10.75, 14.75), "#vel"),
          E((0.75, 7.0, 1.5), (3.75, 11.0, 2.75), "#vel"),
          E((12.25, 7.0, 1.5), (15.25, 11.0, 2.75), "#vel"),
          E((1.25, 10.75, 10.0), (3.25, 15.0, 15.5), "#vel"),
          E((12.75, 10.75, 10.0), (14.75, 15.0, 15.5), "#vel"),
          E((1.5, 15.0, 11.25), (3.25, 15.75, 15.5), "#vel"),
          E((12.75, 15.0, 11.25), (14.5, 15.75, 15.5), "#vel"),
          E((1.5, 4.0, 14.25), (14.5, 15.0, 15.5), "#vel"),
          E((3.5, 8.0, 11.75), (12.5, 15.25, 14.25), "#vel"),
          E((3.0, 15.0, 12.0), (13.0, 16.0, 15.5), "#vel")]
    _single("l39_armchair", t, e)


def _sofa():
    """Camelback sofa, two wide: carved walnut feet and seat rail, rolled arms, a bolster, the
    back rising to the hump in the middle (where the halves meet)."""
    t = _tx(vel="l39_furn_velvet_red", wood="l39_furn_walnut")
    h = [_sp((0.75, 0, 2.25), (2.5, 0.75, 4.0), "#wood"), _sp((1.0, 0.75, 2.5), (2.25, 2.0, 3.75), "#wood"),
         _sp((0.75, 0, 13.75), (2.5, 0.75, 15.5), "#wood"), _sp((1.0, 0.75, 14.0), (2.25, 2.0, 15.25), "#wood"),
         _sp((14.75, 0, 2.75), (16, 1.75, 4.25), "#wood"),
         _sp((0.5, 2.0, 2.0), (16, 3.0, 15.5), "#wood"),
         _sp((0.5, 3.0, 2.25), (16, 6.0, 15.0), "#vel"),
         _sp((3.0, 6.0, 2.0), (16, 7.75, 12.5), "#vel"),
         _sp((3.0, 5.75, 1.75), (16, 7.5, 2.25), "#vel"),
         _sp((0.5, 3.0, 2.0), (3.0, 9.25, 16), "#vel"),
         _sp((0.5, 9.25, 2.25), (3.0, 10.75, 15.0), "#vel"),
         _sp((0.25, 7.25, 1.75), (3.25, 10.5, 2.75), "#vel"),
         _sp((3.0, 7.75, 3.0), (5.0, 9.75, 11.5), "#vel"),
         _sp((0.5, 3.0, 12.5), (16, 11.5, 16), "#vel"),
         _sp((3.0, 11.5, 12.75), (16, 12.25, 16), "#vel"),
         _sp((6.0, 12.25, 13.0), (16, 13.25, 16), "#vel"),
         _sp((9.5, 13.25, 13.25), (16, 14.25, 16), "#vel"),
         _sp((12.5, 14.25, 13.5), (16, 15.0, 16), "#vel"),
         _sp((3.0, 7.75, 11.75), (16, 11.75, 12.5), "#vel")]
    _pair("l39_sofa", t, _build(h), _build(_mirror(h)))


def _open_box(f, t, tex, uv):
    """The four sides of a box, seen from outside and from inside (lamp shade)."""
    x0, y0, z0 = f
    x1, y1, z1 = t
    u = {s: uv for s in SIDES}
    return [E(f, t, tex, faces=SIDES, uv=u, cull=False),
            E((x0, y0, z0), (x1, y1, z0), tex, faces=("south",), uv=u, cull=False),
            E((x0, y0, z1), (x1, y1, z1), tex, faces=("north",), uv=u, cull=False),
            E((x0, y0, z0), (x0, y1, z1), tex, faces=("east",), uv=u, cull=False),
            E((x1, y0, z0), (x1, y1, z1), tex, faces=("west",), uv=u, cull=False)]


def _side_table():
    """Round walnut pedestal table with a brass lamp, a fringed shade and a dead bulb."""
    t = _tx(wood="l39_furn_walnut", brass="l39_furn_brass", shade="l39_furn_lampshade", bulb="l39_furn_bulb_dead")
    e = [E((4, 0, 7.25), (12, 1, 8.75), "#wood"), E((7.25, 0, 4), (8.75, 1, 12), "#wood"),
         E((7.0, 1, 7.0), (9.0, 2, 9.0), "#wood"), E((7.4, 2, 7.4), (8.6, 4, 8.6), "#wood"),
         E((6.75, 4, 6.75), (9.25, 6.5, 9.25), "#wood"), E((7.4, 6.5, 7.4), (8.6, 9.5, 8.6), "#wood"),
         E((6.5, 9.5, 6.5), (9.5, 10.25, 9.5), "#wood")]
    e += P._cyl(3.75, 12.25, 3.75, 12.25, 9.75, 10.25, "#wood", cut=2.4)
    e += P._cyl(3, 13, 3, 13, 10.25, 11, "#wood", cut=2.75)
    e += P._cyl(6.25, 9.75, 6.25, 9.75, 11, 11.75, "#brass", cut=1.0)
    e += [E((7.5, 11.75, 7.5), (8.5, 12.25, 8.5), "#brass"), E((7.7, 12.25, 7.7), (8.3, 15.5, 8.3), "#brass"),
          E((7.35, 15.5, 7.35), (8.65, 16.5, 8.65), "#brass"),
          E((7.25, 16.5, 7.25), (8.75, 18.0, 8.75), "#bulb", uv="full", cull=False),
          E((7.75, 19.0, 7.75), (8.25, 19.75, 8.25), "#brass", cull=False)]
    e += _open_box((5, 14.25, 5), (11, 17.25, 11), "#shade", [0, 6, 16, 16])
    e += _open_box((5.75, 17.25, 5.75), (10.25, 19.0, 10.25), "#shade", [0, 0, 16, 6])
    _single("l39_side_table", t, e)


def _radio():
    """Cathedral console radio: plinth, pilasters, stepped arch, two knobs."""
    t = _tx(wood="l39_furn_walnut", front="l39_furn_radio_front", knob="l39_furn_mahogany", brass="l39_furn_brass")
    fr = _face("#wood", north="#front")
    e = [E((2.5, 0, 5), (13.5, 0.75, 14), "#wood"), E((2.75, 0.75, 5.25), (13.25, 1.5, 14), "#wood"),
         E((3.5, 1.5, 6), (12.5, 13, 14), fr),
         E((2.5, 1.5, 5), (3.75, 13, 14), "#wood"), E((12.25, 1.5, 5), (13.5, 13, 14), "#wood"),
         E((2.4, 11.75, 4.85), (3.85, 12.5, 5.25), "#wood"), E((12.15, 11.75, 4.85), (13.6, 12.5, 5.25), "#wood"),
         E((2.75, 13, 5.5), (13.25, 14, 14), fr), E((3.5, 14, 5.75), (12.5, 15, 14), fr),
         E((5, 15, 5.75), (11, 15.75, 14), fr), E((6.5, 15.75, 5.75), (9.5, 16, 14), fr)]
    for kx in (4.75, 11.25):
        e += [E((kx - 0.65, 5.1, 5.4), (kx + 0.65, 6.4, 6), "#knob"),
              E((kx - 0.45, 5.3, 5.0), (kx + 0.45, 6.2, 5.4), "#knob"),
              E((kx - 0.1, 5.95, 4.95), (kx + 0.1, 6.15, 5.0), "#brass", faces=("north",))]
    _single("l39_radio", t, e)


def _bookshelf():
    """Shallow oak bookcase: three rows of book groups of different heights and depths, and a
    small stack lying flat."""
    t = _tx(wood="l39_furn_oak", books="l39_furn_books")
    e = [E((0, 0, 7), (1.25, 16, 16), "#wood"), E((14.75, 0, 7), (16, 16, 16), "#wood"),
         E((1.25, 0, 15.25), (14.75, 16, 16), "#wood"), E((1.25, 0, 7), (14.75, 1.25, 15.25), "#wood"),
         E((0, 15, 7), (16, 16, 16), "#wood"),
         E((1.25, 5.5, 7.25), (14.75, 6.25, 15.25), "#wood"), E((1.25, 10.75, 7.25), (14.75, 11.5, 15.25), "#wood"),
         E((0.5, 0, 6.85), (15.5, 1.0, 7.0), "#wood", faces=("north", "up"))]
    rng = G.rng_for(39790)
    for row, (y0, y1) in enumerate(((1.25, 5.5), (6.25, 10.75), (11.5, 15.0))):
        x = 1.25
        while x < 14.5:
            if rng.random() < 0.12 and x < 12:
                x += float(rng.uniform(0.75, 1.75))
                continue
            w = min(float(rng.choice([1.25, 1.5, 2.0, 2.5, 3.0])), 14.75 - x)
            if w < 0.75:
                break
            if row == 1 and 10.0 < x < 11.5:              # a few books lying flat
                for k in range(3):
                    hb = 0.6
                    uv = {"north": [2 + 3 * k, 4 * k + 1, 2 + 3 * k + 0.6, 4 * k + 4]}
                    el = E((x, y0 + k * hb, 8.5 + 0.3 * k), (x + 3.25 - 0.3 * k, y0 + (k + 1) * hb, 14.5), "#books", uv=uv)
                    el["faces"]["north"]["rotation"] = 90
                    e.append(el)
                x += 3.5
                continue
            h = min(float(rng.uniform(2.9, 4.0)), y1 - y0 - 0.25)
            strip = int(rng.integers(0, 4))
            u0 = float(rng.uniform(0, 16 - w))
            z0 = float(rng.uniform(7.75, 8.75))
            v1 = 4 * strip + 4
            e.append(E((x, y0, z0), (x + w, y0 + h, 15.25), "#books",
                       uv={"north": [u0, v1 - h, u0 + w, v1]}))
            x += w
    _single("l39_bookshelf", t, e)


def _piano():
    """Upright piano, two wide: case with cheek at the outer end, keybed with exposed keys,
    music desk, console leg, candle sconce; three brass pedals centred where the halves meet."""
    t = _tx(wood="l39_furn_mahogany", lower="l39_furn_piano_lower", upper="l39_furn_piano_upper",
            keys="l39_furn_keys", brass="l39_furn_brass")
    h = [_sp((0, 0, 8), (1.25, 16, 16), "#wood"),
         _sp((1.25, 0, 8), (16, 9, 16), _face("#wood", north="#lower")),
         _sp((0, 9, 3), (1.25, 11, 8), "#wood"),
         _sp((1.25, 9, 3.5), (16, 10.25, 8), "#wood"),
         _sp((1.25, 9.25, 3.0), (16, 10.5, 3.5), "#wood"),
         _sp((1.25, 11, 8), (16, 16, 16), _face("#wood", north="#upper")),
         _sp((1.25, 12.25, 7.25), (16, 12.75, 8), "#wood"),
         _sp((0, 15.5, 7.6), (16, 16, 8), "#wood"),
         _sp((0.5, 0, 4), (2, 0.75, 7.75), "#wood"),
         _sp((0.75, 0.75, 4.25), (1.75, 8.25, 5.75), "#wood"),
         _sp((0.5, 3.5, 4.0), (2, 4.5, 6.0), "#wood"),
         _sp((0.5, 8.25, 4), (2, 9, 6), "#wood"),
         _sp((13.4, 0.5, 6.25), (14.6, 1.0, 8.0), "#brass"),
         _sp((15.4, 0.5, 6.25), (16, 1.0, 8.0), "#brass"),
         _sp((1.6, 13.0, 6.5), (2.2, 13.5, 8.0), "#brass"),
         _sp((1.35, 13.5, 6.35), (2.45, 14.25, 7.15), "#brass")]
    first = _build(h) + [E((1.25, 10.25, 4.0), (16, 10.6, 7.75), "#keys", uv={"up": [1.25, 0, 16, 16]})]
    second = _build(_mirror(h)) + [E((0, 10.25, 4.0), (14.75, 10.6, 7.75), "#keys", uv={"up": [0, 0, 14.75, 16]})]
    _pair("l39_piano", t, first, second)


def _wardrobe():
    """Two-door wardrobe, two tall: plinth, carcass, doors (oval mirror on the left), brass
    knobs; crown moulding stepping out on the upper half."""
    t = _tx(wood="l39_furn_walnut", lower="l39_furn_wardrobe_lower", upper="l39_furn_wardrobe_upper", brass="l39_furn_brass")
    lo = _face("#wood", north="#lower")
    up = _face("#wood", north="#upper")
    first = [E((1, 0, 4), (15, 1.5, 16), lo), E((1.25, 1.5, 4.5), (14.75, 16, 16), "#wood"),
             E((1.5, 1.5, 4), (7.95, 16, 4.5), lo), E((8.05, 1.5, 4), (14.5, 16, 4.5), lo),
             E((7.15, 14.6, 3.4), (7.75, 15.2, 4), "#brass"), E((8.25, 14.6, 3.4), (8.85, 15.2, 4), "#brass")]
    second = [E((1.25, 0, 4.5), (14.75, 12, 16), "#wood"),
              E((1.5, 0, 4), (7.95, 12, 4.5), up), E((8.05, 0, 4), (14.5, 12, 4.5), up),
              E((1, 12, 4), (15, 13, 16), up), E((0.75, 13, 3.75), (15.25, 14, 16), up),
              E((0.5, 14, 3.5), (15.5, 15, 16), up)]
    _pair("l39_wardrobe", t, first, second)


def _dresser():
    """Chest of four drawers with bail pulls, and an oval swing mirror between turned posts."""
    t = _tx(wood="l39_furn_walnut", front="l39_furn_dresser_front", mirror="l39_furn_dresser_mirror",
            back="l39_furn_dresser_mirror_back", brass="l39_furn_brass")
    e = []
    for x0 in (1.0, 13.5):
        for z0 in (6.0, 14.5):
            e.append(E((x0, 0, z0), (x0 + 1.5, 1.0, z0 + 1.5), "#wood"))
    e += [E((1.25, 1.0, 6.5), (14.75, 12.5, 16), _face("#wood", north="#front")),
          E((1, 12.5, 6), (15, 13, 16), "#wood")]
    for y0, y1 in DRAWERS:
        yc = (y0 + y1) / 2
        for a in PULL_A:
            x = 16 - a
            e += [E((x - 0.75, yc - 0.55, 6.1), (x + 0.75, yc - 0.3, 6.5), "#brass"),
                  E((x - 0.75, yc - 0.3, 6.1), (x - 0.55, yc + 0.1, 6.5), "#brass"),
                  E((x + 0.55, yc - 0.3, 6.1), (x + 0.75, yc + 0.1, 6.5), "#brass")]
    for x0 in (2.0, 13.0):
        e += [E((x0 - 0.25, 13, 11.5), (x0 + 1.25, 14, 14.5), "#wood"),
              E((x0, 14, 12.5), (x0 + 1, 24, 13.5), "#wood"),
              E((x0 - 0.15, 18.5, 12.35), (x0 + 1.15, 19.25, 13.65), "#wood"),
              E((x0 - 0.1, 24, 12.4), (x0 + 1.1, 24.75, 13.6), "#wood"),
              E((x0 + 0.15, 24.75, 12.65), (x0 + 0.85, 25.5, 13.35), "#brass")]
    e += [E((2.9, 20.0, 12.6), (3.25, 20.6, 13.4), "#brass"), E((12.75, 20.0, 12.6), (13.1, 20.6, 13.4), "#brass"),
          E((3.25, 14.75, 12.75), (12.75, 28, 13.25), {"north": "#mirror", "south": "#back"},
            faces=("north", "south"), uv={"north": [0, 0, 16, 16], "south": [0, 0, 16, 16]}, cull=False)]
    _single("l39_dresser", t, e)


def _bed():
    """Black iron bed, two long: foot at P (footboard at the south end), head at P + FACING
    (headboard at its north end, pillow, turned-down sheet). Brass knobs, patchwork quilt."""
    t = _tx(quilt="l39_furn_quilt", iron="l39_furn_black_iron", brass="l39_furn_brass",
            linen="l39_furn_linen", tick="l39_furn_ticking")

    def board(z0, z1, top, rail_ys, knob=True):
        e = [E((0, 0, z0), (1.25, top, z1), "#iron"), E((14.75, 0, z0), (16, top, z1), "#iron")]
        if knob:
            e += [E((0.05, top, z0 + 0.05), (1.2, top + 1, z1 - 0.05), "#brass"),
                  E((14.8, top, z0 + 0.05), (15.95, top + 1, z1 - 0.05), "#brass")]
        for y in rail_ys:
            e.append(E((1.25, y, z0 + 0.2), (14.75, y + 0.75, z1 - 0.2), "#iron"))
        for x in np.arange(2.75, 14, 2.0):
            e.append(E((x - 0.25, rail_ys[0] + 0.75, z0 + 0.3), (x + 0.25, rail_ys[-1], z1 - 0.3), "#iron"))
        e.append(E((7.3, (rail_ys[0] + rail_ys[-1]) / 2 - 0.7, z0 + 0.1), (8.7, (rail_ys[0] + rail_ys[-1]) / 2 + 0.7, z1 - 0.1), "#brass"))
        return e
    foot = [E((0, 2.5, 0), (0.6, 3.5, 15), "#iron"), E((15.4, 2.5, 0), (16, 3.5, 15), "#iron"),
            E((0.5, 3, 0), (15.5, 5, 14.75), "#tick"),
            E((0, 4.5, 0), (16, 8.5, 14.75), "#quilt"), E((0.25, 8.5, 0), (15.75, 9, 14.5), "#quilt")]
    foot += board(15, 16, 10.75, (2.5, 10.0))
    head = [E((0, 2.5, 1), (0.6, 3.5, 16), "#iron"), E((15.4, 2.5, 1), (16, 3.5, 16), "#iron"),
            E((0.5, 3, 1), (15.5, 5, 16), "#tick"),
            E((0.25, 4.5, 1.25), (15.75, 8.25, 8.0), "#linen"),
            E((0, 4.5, 8), (16, 8.5, 16), "#quilt"), E((0.25, 8.5, 8.5), (15.75, 9, 16), "#quilt"),
            E((0.1, 8.25, 7.25), (15.9, 9.1, 8.75), "#linen"),
            E((2, 8.25, 1.5), (14, 10, 5.5), "#linen"), E((2.5, 10, 2), (13.5, 10.5, 5), "#linen")]
    head += board(0, 1, 14.75, (2.5, 9.5, 13.25))
    head += [E((4.5, 14.0, 0.2), (11.5, 14.5, 0.8), "#iron"), E((6.5, 14.5, 0.2), (9.5, 14.85, 0.8), "#iron")]
    _pair("l39_bed", t, foot, head)


def _tub():
    """Clawfoot tub, two long: roll rim, enamel inside and out, iron claw feet. The first block
    has the taps and the drain at its south end, the second the rounded end at z = 0."""
    t = _tx(en="l39_furn_enamel", inside="l39_furn_tub_inside", iron="l39_furn_black_iron", chrome="l39_furn_chrome")

    def feet(z0):
        e = []
        for x0 in (1.75, 12.75):
            e += [E((x0, 0, z0), (x0 + 1.5, 0.6, z0 + 1.75), "#iron"),
                  E((x0 + 0.2, 0.6, z0 + 0.2), (x0 + 1.3, 1.2, z0 + 1.55), "#iron"),
                  E((x0 + 0.35, 1.2, z0 + 0.4), (x0 + 1.15, 2.6, z0 + 1.35), "#iron")]
        return e

    def sides(z0, z1):
        return [E((1.25, 3.75, z0), (2.5, 10, z1), "#en"), E((13.5, 3.75, z0), (14.75, 10, z1), "#en"),
                E((1, 10, z0), (2.75, 11, z1), "#en"), E((13.25, 10, z0), (15, 11, z1), "#en")]
    first = feet(11.0) + sides(0, 15.0) + [
        E((3, 1.5, 0), (13, 2.5, 14), "#en"), E((2, 2.5, 0), (14, 3.75, 14.75), "#en"),
        E((2.5, 3.5, 0), (13.5, 3.75, 14.25), _face("#en", up="#inside")),
        E((2, 3.0, 14.25), (14, 10, 15.5), "#en"), E((2.75, 3.5, 15.5), (13.25, 10, 15.9), "#en"),
        E((1.75, 10, 13.75), (14.25, 11, 15.75), "#en"), E((2.75, 10, 15.75), (13.25, 10.9, 16), "#en")]
    for x in (6.0, 10.0):
        first += [E((x - 0.4, 11, 14.4), (x + 0.4, 12.25, 15.2), "#chrome"),
                  E((x - 1.1, 12.25, 14.6), (x + 1.1, 12.65, 15.0), "#chrome"),
                  E((x - 0.2, 12.25, 13.7), (x + 0.2, 12.65, 15.9), "#chrome"),
                  E((x - 0.25, 12.65, 14.55), (x + 0.25, 12.85, 15.05), "#en")]
    first += [E((7.6, 11, 14.3), (8.4, 12.0, 15.2), "#chrome"), E((7.6, 11.4, 12.9), (8.4, 12.0, 14.3), "#chrome"),
              E((7.65, 10.6, 12.9), (8.35, 11.4, 13.5), "#chrome")]
    second = feet(3.25) + sides(1.0, 16) + [
        E((3, 1.5, 2.5), (13, 2.5, 16), "#en"), E((2, 2.5, 1.25), (14, 3.75, 16), "#en"),
        E((2.5, 3.5, 1.75), (13.5, 3.75, 16), "#en"),
        E((2.5, 3.0, 0.5), (13.5, 10, 1.75), "#en"), E((1.75, 3.5, 0.9), (2.75, 10, 2.25), "#en"),
        E((13.25, 3.5, 0.9), (14.25, 10, 2.25), "#en"),
        E((2.5, 10, 0), (13.5, 11, 1.75), "#en"), E((1.5, 10, 0.5), (3, 11, 2), "#en"), E((13, 10, 0.5), (14.5, 11, 2), "#en")]
    _pair("l39_clawfoot_tub", t, first, second)


def _pedestal_sink():
    """Porcelain basin on a flared column, a real bowl recess, two cross-handle taps and a spout."""
    t = _tx(por="l39_furn_porcelain", basin="l39_furn_basin", chrome="l39_furn_chrome")
    e = [E((5.25, 0, 7.25), (10.75, 1, 12.75), "#por"), E((5.75, 1, 7.75), (10.25, 2, 12.25), "#por"),
         E((6.25, 2, 8.25), (9.75, 8.5, 11.75), "#por"), E((5.75, 8.5, 7.75), (10.25, 10, 12.25), "#por"),
         E((3.5, 10, 7), (12.5, 11, 15), "#por"),
         E((2.5, 11, 6.5), (4, 14, 16), "#por"), E((12, 11, 6.5), (13.5, 14, 16), "#por"),
         E((4, 11, 6), (12, 14, 7.5), "#por"), E((4, 11, 12.5), (12, 14, 16), "#por"),
         E((4, 11, 7.5), (12, 12.25, 12.5), _face("#por", up="#basin"), uv={"up": [0, 0, 16, 16]})]
    for x in (5.0, 11.0):
        e += [E((x - 0.4, 14, 13.6), (x + 0.4, 15, 14.4), "#chrome"),
              E((x - 1.0, 15, 13.75), (x + 1.0, 15.4, 14.25), "#chrome"),
              E((x - 0.25, 15, 13.0), (x + 0.25, 15.4, 15.0), "#chrome"),
              E((x - 0.25, 15.4, 13.75), (x + 0.25, 15.6, 14.25), "#por")]
    e += [E((7.5, 14, 13), (8.5, 14.75, 14.5), "#chrome"), E((7.5, 14.25, 11.75), (8.5, 14.75, 13), "#chrome")]
    _single("l39_pedestal_sink", t, e)


def _toilet():
    """Porcelain bowl on a foot, dark wooden seat round the opening, tank with a chrome lever."""
    t = _tx(por="l39_furn_porcelain", bowl="l39_furn_toilet_bowl", wood="l39_furn_mahogany", chrome="l39_furn_chrome")
    e = [E((5, 0, 6), (11, 1, 13.5), "#por"), E((5.5, 1, 6.5), (10.5, 5, 13), "#por"),
         E((4.25, 5, 2.5), (11.75, 8.25, 12.5), _face("#por", up="#bowl")),
         E((5, 5, 2), (11, 8.25, 2.5), "#por"),
         E((5, 8.25, 2), (11, 9, 3.75), "#wood"), E((4, 8.25, 3), (5.75, 9, 12), "#wood"),
         E((10.25, 8.25, 3), (12, 9, 12), "#wood"), E((4.5, 8.25, 10.25), (11.5, 9, 12.5), "#wood"),
         E((5.5, 9.0, 11.5), (6.5, 9.4, 12.25), "#chrome"), E((9.5, 9.0, 11.5), (10.5, 9.4, 12.25), "#chrome"),
         E((7.25, 8, 12), (8.75, 9.5, 13), "#por"),
         E((3, 9.5, 11.5), (13, 15.5, 16), "#por"), E((2.75, 15.5, 11.25), (13.25, 16, 16), "#por"),
         E((10.5, 13.75, 11.0), (12.5, 14.25, 11.5), "#chrome"), E((12.0, 13.5, 11.25), (12.9, 14.5, 11.5), "#chrome")]
    _single("l39_toilet", t, e)


def _icebox():
    """GE-style monitor-top fridge: enamel cabinet on legs, door with lever and hinges, the
    round coil drum and cap on top."""
    t = _tx(en="l39_furn_enamel", front="l39_furn_icebox_front", chrome="l39_furn_chrome",
            coil="l39_furn_icebox_coil", iron="l39_furn_black_iron")
    e = []
    for x0 in (2.25, 12.75):
        for z0 in (3.75, 13.75):
            e.append(E((x0, 0, z0), (x0 + 1, 2, z0 + 1), "#iron"))
    e += [E((2, 2, 3.5), (14, 12, 15), _face("#en", north="#front")),
          E((2.5, 2.5, 3), (13.5, 11.5, 3.5), _face("#en", north="#front")),
          E((2.25, 12, 3.75), (13.75, 12.5, 14.75), "#en"),
          E((4.5, 12.5, 5.5), (11.5, 12.75, 12.5), "#iron"),
          E((3.25, 6.5, 2.5), (4.0, 9.0, 3.0), "#chrome"), E((3.75, 7.4, 2.4), (5.0, 8.1, 2.9), "#chrome"),
          E((3.0, 6.8, 2.75), (3.6, 8.7, 3.0), "#chrome")]
    for y in (3.2, 10.0):
        e.append(E((13.25, y, 2.85), (13.75, y + 0.9, 3.5), "#chrome"))
    e += P._cyl(4, 12, 5, 13, 12.75, 15.5, "#coil", cut=2.2)
    e += P._cyl(4.75, 11.25, 5.75, 12.25, 15.5, 16.25, "#iron", cut=1.8)
    e += [E((7.5, 16.25, 8.5), (8.5, 16.6, 9.5), "#chrome", cull=False)]
    _model("l39_icebox", t, e)
    _furn_state("l39_icebox", "l39_icebox", ("left", range(5)))
    P._im("l39_icebox", parent=_ref("l39_icebox"))


BOTTLE_SPOTS = ((6.5, 6.75), (11.25, 1.5), (5.0, 12.0), (8.0, 1.5))   # (x, shelf top) in fill order


def _bottle_els(cx, y):
    b = {"north": [0, 0, 10, 16], "south": [0, 0, 10, 16], "east": [0, 0, 10, 16], "west": [0, 0, 10, 16],
         "up": [13, 4, 16, 8], "down": [13, 4, 16, 8]}
    g = {f: [10, 0, 13, 8] for f in FACES}
    c = {f: [13, 0, 16, 4] for f in FACES}
    z = 13.0
    return [E((cx - 0.9, y, z - 0.9), (cx + 0.9, y + 3.0, z + 0.9), "#bottle", uv=b),
            E((cx - 0.65, y + 3.0, z - 0.65), (cx + 0.65, y + 3.4, z + 0.65), "#bottle", uv=g),
            E((cx - 0.35, y + 3.4, z - 0.35), (cx + 0.35, y + 3.95, z + 0.35), "#bottle", uv=g),
            E((cx - 0.45, y + 3.95, z - 0.45), (cx + 0.45, y + 4.3, z + 0.45), "#bottle", uv=c)]


def _almond_shelf():
    """Pine pantry shelf on the wall: sides, back board, three shelves; a dusty empty jar and a
    tin; LEFT bottles of Almond Water (one model per count)."""
    t = _tx(wood="l39_furn_pine", bottle="l39_furn_almond_bottle")
    base = [E((0, 0, 10), (1, 16, 16), "#wood"), E((15, 0, 10), (16, 16, 16), "#wood"),
            E((1, 0, 15.25), (15, 16, 16), "#wood"),
            E((1, 0.75, 10.25), (15, 1.5, 15.25), "#wood"), E((1, 6.0, 10.25), (15, 6.75, 15.25), "#wood"),
            E((1, 11.25, 10.25), (15, 12.0, 15.25), "#wood"),
            E((1, 15.25, 14.5), (15, 16, 15.25), "#wood"), E((1, 0, 10), (15, 0.75, 10.5), "#wood")]
    jar = {f: [10, 0, 13, 8] for f in FACES}
    lid = {f: [10, 8, 16, 12] for f in FACES}
    paper = {f: [10, 12, 16, 16] for f in FACES}
    base += [E((10.5, 12.0, 11.75), (13.25, 14.75, 14.5), "#bottle", uv=jar),
             E((10.75, 14.75, 12.0), (13.0, 15.25, 14.25), "#bottle", uv=lid),
             E((2.0, 6.75, 11.5), (4.0, 9.25, 13.5), "#bottle", uv=lid),
             E((1.95, 7.25, 11.45), (4.05, 8.75, 13.55), "#bottle", uv=paper)]
    for n in range(5):
        e = list(base)
        for cx, y in BOTTLE_SPOTS[:n]:
            e += _bottle_els(cx, y)
        _model(f"l39_almond_shelf_{n}", t, e)
    _furn_state("l39_almond_shelf", lambda n: f"l39_almond_shelf_{n}", ("left", range(5)))
    P._im("l39_almond_shelf", parent=_ref("l39_almond_shelf_4"))


def _stove():
    """Cream-and-green range on iron legs: enamel body, cooktop with raised grates, chrome
    handles and latches, back panel and warming shelf up to y 24."""
    t = _tx(side="l39_furn_stove_side", front="l39_furn_stove_front", top="l39_furn_stove_top",
            iron="l39_furn_black_iron", chrome="l39_furn_chrome", cream="l39_furn_enamel_cream",
            green="l39_furn_enamel_green")
    full = [0, 0, 16, 16]
    e = []
    for x0 in (0.5, 14.0):
        for z0 in (3.0, 14.0):
            e += [E((x0, 0, z0), (x0 + 1.5, 0.5, z0 + 1.5), "#iron"),
                  E((x0 + 0.3, 0.5, z0 + 0.3), (x0 + 1.2, 3, z0 + 1.2), "#iron")]
    e += [E((0, 3, 2.75), (16, 13, 16), _face("#cream", north="#front", east="#side", west="#side"),
            uv={"east": full, "west": full}),
          E((0, 13, 2), (16, 14, 16), _face("#cream", up="#top")),
          E((7.75, 11.5, 2.0), (14.5, 12.0, 2.4), "#chrome"),
          E((8.0, 11.5, 2.4), (8.5, 12.0, 2.75), "#chrome"), E((13.75, 11.5, 2.4), (14.25, 12.0, 2.75), "#chrome"),
          E((1.25, 10.0, 2.25), (2.25, 10.6, 2.75), "#chrome"), E((1.25, 6.4, 2.25), (2.25, 7.0, 2.75), "#chrome"),
          E((0, 12.8, 2.5), (16, 13.1, 2.8), "#chrome")]
    for bx, bz in ((4.25, 6.0), (11.75, 6.0), (4.25, 12.25), (11.75, 12.25)):
        e += [E((bx - 2.4, 14, bz - 0.2), (bx + 2.4, 14.3, bz + 0.2), "#iron", cull=False),
              E((bx - 0.2, 14, bz - 2.4), (bx + 0.2, 14.3, bz + 2.4), "#iron", cull=False)]
    e += [E((0.5, 14, 14.75), (15.5, 21, 16), _face("#cream", north="#side"), uv={"north": full}),
          E((0.25, 21, 11.75), (15.75, 23.5, 16), _face("#cream", north="#green", south="#green")),
          E((0, 23.5, 11.5), (16, 24, 16), "#green")]
    for x0 in (0.5, 14.75):
        e.append(E((x0, 14, 12.5), (x0 + 0.75, 21, 14.75), "#iron"))
    _single("l39_stove", t, e)


def _cupboard(front, sink=False):
    """The painted cupboard body under the counters. Under a sink only the part below the
    basin is solid, with posts at the sides and back round the basin."""
    t = _tx(paint="l39_furn_cream_paint", front=front, tile="l39_furn_tile", kick="l39_furn_oak",
            iron="l39_furn_black_iron", chrome="l39_furn_chrome", por="l39_furn_porcelain", basin="l39_furn_basin")
    fr = _face("#paint", north="#front")
    e = [E((0, 0, 2.5), (16, 1.25, 16), "#kick")]
    if sink:
        e += [E((0, 1.25, 1), (16, 8.5, 16), fr), E((0, 8.5, 1), (2, 14, 16), fr),
              E((14, 8.5, 1), (16, 14, 16), fr), E((2, 8.5, 13), (14, 14, 16), fr)]
    else:
        e.append(E((0, 1.25, 1), (16, 14, 16), fr))
    return t, e


def _counter():
    """Painted kitchen cupboard with a drawer and a door, white tile top."""
    t, e = _cupboard("l39_furn_counter_front")
    e += [E((0, 14, 0), (16, 16, 16), "#tile"),
          E((7.4, 11.4, 0.5), (8.6, 12.6, 1), "#iron"), E((1.6, 8.0, 0.5), (2.6, 9.0, 1), "#iron")]
    _single("l39_counter", t, e)


def _kitchen_sink():
    """The cupboard with a deep white farmhouse sink set into the tiles, apron front, and a
    tall gooseneck tap with two cross handles on the back deck."""
    t, e = _cupboard("l39_furn_kitchen_sink_front", sink=True)
    e += [E((0, 14, 0), (2, 16, 16), "#tile"), E((14, 14, 0), (16, 16, 16), "#tile"),
          E((2, 14, 13), (14, 16, 16), "#tile"),
          E((2, 8.5, 0.5), (14, 16, 1.5), "#por"),
          E((2, 9, 1.5), (2.75, 16, 13), "#por"), E((13.25, 9, 1.5), (14, 16, 13), "#por"),
          E((2, 9, 12.25), (14, 16, 13), "#por"),
          E((2.75, 9, 1.5), (13.25, 9.75, 12.25), _face("#por", up="#basin"), uv={"up": [0, 0, 16, 16]}),
          E((7.0, 6.5, 0.5), (7.6, 7.5, 1), "#iron"), E((8.4, 6.5, 0.5), (9.0, 7.5, 1), "#iron"),
          E((7.25, 16, 13.75), (8.75, 16.5, 15.25), "#chrome", cull=False),
          E((7.6, 16.5, 14.1), (8.4, 19.5, 14.9), "#chrome"),
          E((7.6, 19.5, 12.25), (8.4, 20.25, 14.9), "#chrome"),
          E((7.6, 18.25, 11.75), (8.4, 20.25, 12.5), "#chrome")]
    for x in (5.0, 11.0):
        e += [E((x - 0.4, 16, 14.1), (x + 0.4, 17, 14.9), "#chrome", cull=False),
              E((x - 1.0, 17, 14.25), (x + 1.0, 17.4, 14.75), "#chrome"),
              E((x - 0.25, 17, 13.5), (x + 0.25, 17.4, 15.5), "#chrome")]
    _single("l39_kitchen_sink", t, e)


def _kitchen_dresser():
    """Hoosier cabinet, two tall: lower drawers + cupboard under a white enamel worktop; upper
    glazed cupboard and flour bin with the sifter hanging under it, a cornice on top."""
    t = _tx(paint="l39_furn_cream_paint", lower="l39_furn_hoosier_lower", upper="l39_furn_hoosier_upper",
            en="l39_furn_enamel", chrome="l39_furn_chrome", kick="l39_furn_oak")
    first = [E((0.5, 0, 2.5), (15.5, 1, 16), "#kick"),
             E((0.5, 1, 2), (15.5, 14, 16), _face("#paint", north="#lower")),
             E((0, 14, 1), (16, 15, 16), "#en")]
    for y in (11.65, 7.8, 3.6):
        first += [E((11.6, y - 0.5, 1.5), (13.4, y - 0.15, 2), "#chrome"),
                  E((11.6, y - 0.15, 1.6), (11.85, y + 0.35, 2), "#chrome"),
                  E((13.15, y - 0.15, 1.6), (13.4, y + 0.35, 2), "#chrome")]
    first += [E((8.0, 9.4, 1.4), (8.6, 10.6, 2), "#chrome")]
    second = [E((1, 0, 8.5), (15, 13.5, 16), _face("#paint", north="#upper")),
              E((0.75, 13.5, 7.75), (15.25, 14.25, 16), "#paint"), E((1, 14.25, 8), (15, 15, 16), "#paint"),
              E((5.5, 5.8, 8.0), (6.0, 7.2, 8.5), "#chrome"), E((10.0, 5.8, 8.0), (10.5, 7.2, 8.5), "#chrome"),
              E((11.5, 11.5, 8.0), (13.0, 12.0, 8.5), "#chrome")]
    second += P._cyl(10.9, 14.1, 9.4, 12.6, -1.0, 0, "#chrome", cut=1.0)
    _pair("l39_kitchen_dresser", t, first, second)


def _turned_leg(x0, z0, top, w=2.0):
    """A turned leg in a w x w footprint from y 0 to `top`: foot, bulb, ring, shaft, square block."""
    def b(inset, y0, y1):
        return E((x0 + inset, y0, z0 + inset), (x0 + w - inset, y1, z0 + w - inset), "#wood")
    s = top / 12.5
    return [b(0.25, 0, 1 * s), b(0.6, 1 * s, 3 * s), b(0.0, 3 * s, 6 * s), b(0.2, 6 * s, 6.5 * s),
            b(0.5, 6.5 * s, 9.5 * s), b(0.0, 9.5 * s, top)]


def _dining_table():
    """One block of dark oak table: full plank top (tops join seamlessly), apron, turned legs."""
    t = _tx(wood="l39_furn_oak", top="l39_furn_table_top")
    e = [E((0, 12.5, 0), (16, 14.5, 16), _face("#wood", up="#top", down="#top")),
         E((3.5, 11, 2), (12.5, 12.5, 2.75), "#wood"), E((3.5, 11, 13.25), (12.5, 12.5, 14), "#wood"),
         E((2, 11, 3.5), (2.75, 12.5, 12.5), "#wood"), E((13.25, 11, 3.5), (14, 12.5, 12.5), "#wood")]
    for x0 in (1.5, 12.5):
        for z0 in (1.5, 12.5):
            e += _turned_leg(x0, z0, 12.5)
    _single("l39_dining_table", t, e)


def _dining_chair():
    """Spindle-back dining chair in dark oak (the back on the south side)."""
    t = _tx(wood="l39_furn_oak")
    e = [E((3.25, 0, 3.25), (4.25, 7, 4.25), "#wood"), E((11.75, 0, 3.25), (12.75, 7, 4.25), "#wood"),
         E((3.0, 4.0, 3.0), (4.5, 4.75, 4.5), "#wood"), E((11.5, 4.0, 3.0), (13.0, 4.75, 4.5), "#wood"),
         E((3.25, 0, 11.75), (4.5, 15.25, 12.75), "#wood"), E((11.5, 0, 11.75), (12.75, 15.25, 12.75), "#wood"),
         E((3.15, 15.25, 11.65), (4.6, 16, 12.85), "#wood"), E((11.4, 15.25, 11.65), (12.85, 16, 12.85), "#wood"),
         E((4.25, 2.0, 3.45), (11.75, 2.6, 4.05), "#wood"),
         E((3.45, 2.6, 4.25), (4.05, 3.2, 11.75), "#wood"), E((11.95, 2.6, 4.25), (12.55, 3.2, 11.75), "#wood"),
         E((3, 7, 3), (13, 8.25, 13), "#wood"), E((3.25, 8.25, 3.25), (12.75, 8.5, 11.5), "#wood"),
         E((4.5, 8.5, 11.75), (11.5, 9.25, 12.5), "#wood"),
         E((4.5, 13.5, 11.75), (11.5, 15.25, 12.6), "#wood"), E((6.0, 15.25, 11.85), (10.0, 15.6, 12.5), "#wood")]
    for x in (5.9, 7.4, 8.9, 10.4):
        e.append(E((x - 0.35, 9.25, 11.9), (x + 0.35, 13.5, 12.4), "#wood"))
    _single("l39_dining_chair", t, e)


def _picture_frame():
    """Dark moulded frame flat on the wall (z 15-16) round the dim portrait."""
    t = _tx(wood="l39_furn_oak", pic="l39_furn_picture", brass="l39_furn_brass")
    e = [E((2, 12, 15), (14, 13, 16), "#wood"), E((2, 3, 15), (14, 4, 16), "#wood"),
         E((2, 4, 15), (3, 12, 16), "#wood"), E((13, 4, 15), (14, 12, 16), "#wood"),
         E((2.5, 12.0, 14.75), (13.5, 12.5, 15), "#wood"), E((2.5, 3.5, 14.75), (13.5, 4.0, 15), "#wood"),
         E((2.5, 4.0, 14.75), (3.0, 12.0, 15), "#wood"), E((13.0, 4.0, 14.75), (13.5, 12.0, 15), "#wood"),
         E((3, 4, 15.5), (13, 12, 15.6), "#pic", faces=("north",), uv={"north": [0, 0, 16, 16]}),
         E((7.6, 12.5, 14.9), (8.4, 13.0, 15), "#brass")]
    _single("l39_picture_frame", t, e)


def _boiler():
    """Round sectional coal boiler: plinth, cast body with joint rings, fire and ash doors,
    pressure gauge, dome and the flue pipe up to the top of the block."""
    t = _tx(body="l39_furn_boiler_body", door="l39_furn_boiler_door", iron="l39_furn_black_iron")
    e = []
    e += P._cyl(2, 14, 2, 14, 0, 1.5, "#iron", cut=3.0)
    e += P._cyl(2.75, 13.25, 2.75, 13.25, 1.5, 12, "#body", cut=2.75)
    for y in (4.5, 8.25):
        e += P._cyl(2.5, 13.5, 2.5, 13.5, y, y + 0.5, "#iron", cut=2.9)
    e += P._cyl(3.5, 12.5, 3.5, 12.5, 12, 13.25, "#body", cut=2.5)
    e += P._cyl(5, 11, 5, 11, 13.25, 14, "#iron", cut=1.6)
    e += [E((6.75, 14, 6.75), (9.25, 16, 9.25), "#iron"), E((6.5, 14.75, 6.5), (9.5, 15.25, 9.5), "#iron"),
          E((5, 4.75, 2.0), (11, 8.0, 2.75), _face("#iron", north="#door"), uv={"north": [0, 0, 16, 11]}),
          E((5.5, 1.6, 2.0), (10.5, 3.75, 2.75), _face("#iron", north="#door"), uv={"north": [1, 11.5, 15, 15.5]}),
          E((7, 10.0, 2.25), (9, 12.0, 3.0), _face("#iron", north="#door"), uv={"north": [12, 12, 16, 16]}),
          E((7.75, 9.25, 2.5), (8.25, 10.0, 3.0), "#iron")]
    _single("l39_boiler", t, e)


def tex_l39_housefurn_models() -> None:
    """Block models, blockstates and item models of the 22 furniture blocks (LF JSON)."""
    _armchair()
    _sofa()
    _side_table()
    _radio()
    _bookshelf()
    _piano()
    _wardrobe()
    _dresser()
    _bed()
    _tub()
    _pedestal_sink()
    _toilet()
    _icebox()
    _almond_shelf()
    _stove()
    _counter()
    _kitchen_sink()
    _kitchen_dresser()
    _dining_table()
    _dining_chair()
    _picture_frame()
    _boiler()


# =============================================================================
# Smooth roofs (l39_roof, l39_roof_low)
# =============================================================================
# Level39RoofBlock.surface() is the height every model follows: facing north, each SHAPE is the
# lowest (INNER: the highest) of a few planes, each rising toward one side of the block. In block
# pixels a plane rising toward d has the up-slope distance _rise(d, x, z) (0 at its low edge,
# 16 at its high edge); the steep roof is base + rise, the low one base + rise / 2.
#
# How the planes are built:
# - Steep (45 degrees): one slab per plane, an element turned 45 degrees with "rescale": true.
#   Rescale stretches the turned box back to its full footprint, so its top face lies exactly on
#   the plane and its texture is the shingle sheet seen straight from above (u = x, v = z).
# - Low (26.57 degrees): no element can turn that far, so each plane is a row of 2 px strips
#   turned 22.5 degrees, each passing through the true plane at its middle. The strips meet in
#   0.17 px steps that face down the slope, right on the shingle butt lines (a course is 2 px).
# - Where planes meet on a diagonal (hips and valleys), every plane keeps its whole rectangle
#   and a cut-out copy of the shingle texture hides the texels that belong to another plane, so
#   hips, valleys and the pyramid's corners are exact lines instead of staircases.
# - Shingle courses run along the ridge on every plane and continue across block edges because
#   every texture repeats once per block and the UVs are the block's own x and z.

ROOF_PLANES = {"straight": ("N",), "outer": ("N", "W"), "inner": ("N", "W"), "ridge": ("N", "S"),
               "ridge_end": ("N", "E", "W"), "apex": ("N", "S", "E", "W")}
ROOF_SHAPES = tuple(ROOF_PLANES)
ROOF_TRIMS = ("none", "eave", "gable")
EAVE_EDGES = {"straight": ("south",), "outer": ("south", "east")}    # the low edges, facing north
CLAPBOARD = ("l39_clapboard", "l39_clapboard_faded", "l39_clapboard_weathered")
TEXEL = (np.arange(S) + 0.5) / 8.0       # texel centres in block pixels
DOWNHILL = {"N": "south", "S": "north", "E": "west", "W": "east"}
UPHILL = {"N": "north", "S": "south", "E": "east", "W": "west"}


def _rise(d, x, z):
    """Up-slope distance (px) of a plane rising toward d (Level39RoofBlock.along, times 16)."""
    if d == "N":
        return 16 - z
    if d == "S":
        return z
    return x if d == "E" else 16 - x


def _roof_owner(shape) -> np.ndarray:
    """Index of the plane that IS the roof at each texel (row = z, column = x): the lowest plane,
    the highest for INNER; a tie goes to the first plane, so every texel has exactly one owner."""
    xs, zs = np.meshgrid(TEXEL, TEXEL)
    vals = np.stack([_rise(p, xs, zs) for p in ROOF_PLANES[shape]])
    return np.argmax(vals, 0) if shape == "inner" else np.argmin(vals, 0)


def _roof_facets(shape):
    """Per plane: (plane, bounding box (x0, x1, z0, z1) in px, owned-texel mask, needs a cut)."""
    own = _roof_owner(shape)
    out = []
    for i, p in enumerate(ROOF_PLANES[shape]):
        m = own == i
        rows, cols = np.nonzero(m)
        r0, r1, c0, c1 = rows.min(), rows.max() + 1, cols.min(), cols.max() + 1
        out.append((p, (c0 / 8, c1 / 8, r0 / 8, r1 / 8), m, not m[r0:r1, c0:c1].all()))
    return out


ROOF_OVERLAP = 1


def _grow_mask(m, n) -> np.ndarray:
    """The mask grown by n texels in every direction (no wrap-around)."""
    out = m.copy()
    for _ in range(n):
        g = out.copy()
        g[1:] |= out[:-1]
        g[:-1] |= out[1:]
        g[:, 1:] |= out[:, :-1]
        g[:, :-1] |= out[:, 1:]
        out = g
    return out


def _roof_tex(kind, shape, plane, cut) -> str:
    if kind == "shingles":
        if cut:
            return f"l39_roof_shingles_{shape}_{plane.lower()}"
        return "l39_roof_shingles" + ("" if plane == "N" else "_" + plane.lower())
    if cut:
        return f"l39_roof_underside_{shape}_{plane.lower()}"
    return "l39_roof_underside" + ("" if plane in "NS" else "_z")


def _shingles() -> np.ndarray:
    """Dark weathered shingles, 8 courses per block (2 px each) with the butts at the bottom of
    the texture (down the slope): staggered joints, odd new or lost shingles (black tar paper),
    curled butts, granules, moss along the butts, soot and rain streaks running down."""
    rng = G.rng_for(39760)
    img = np.zeros((S, S, 3))
    for c in range(8):
        y0 = 16 * c
        Xr, Yr = X[y0:y0 + 16], Y[y0:y0 + 16] - y0
        start = rng.uniform(0, S)
        edges = [start]
        while True:
            w = rng.uniform(11, 26)
            if edges[-1] + w > start + S - 8:
                break
            edges.append(edges[-1] + w)
        edges = np.array(edges)
        n = len(edges)
        xs = (Xr - start) % S + start
        idx = np.searchsorted(edges, xs, side="right") - 1
        kind = rng.random(n)
        cols = G.lerp(_c((58, 58, 60)), _c((84, 70, 58)), (rng.uniform(0, 0.55, n))[:, None]) + rng.normal(0, 6, n)[:, None]
        cols[(kind >= 0.04) & (kind < 0.09)] += 26
        cols[kind < 0.04] = (30, 28, 27)
        course = cols[idx] + (Yr / 16 * 8)[..., None]
        d = np.min(np.abs(G.wrapd(xs[..., None] - edges[None, None, :], S)), axis=-1)
        course = G.add_lum(course, -34 * G.smoothstep(1.3, 0.4, d))
        course = G.add_lum(course, 12 * G.smoothstep(13.5, 15.2, Yr) - 30 * G.smoothstep(3.0, 0.3, Yr))
        curl = (rng.random(n) < 0.2)[idx]
        course = G.add_lum(course, 12 * curl * G.smoothstep(9, 15, Yr))
        img[y0:y0 + 16] = course
    img = G.add_lum(img, rng.normal(0, 5, SH) + G.blur(rng.normal(0, 1, SH), 0.6) * 6)
    img = G.add_lum(img, -14 * G.smoothstep(0.5, 2.0, G.fnoise(rng, SH, beta=1.6, aniso=(1, 10))))
    img = G.over(img, _c((98, 98, 96)), G.smoothstep(0.8, 2.2, G.fnoise(rng, SH, beta=2.6)) * 0.25)
    moss = G.add_lum(_fill((66, 84, 50)), G.fnoise(rng, SH, beta=0.8, kmin=10) * 10)
    mm = G.smoothstep(1.0, 2.2, G.fnoise(rng, SH, beta=2.4)) * (0.35 + 0.65 * G.smoothstep(6, 15, Y % 16))
    img = G.over(img, moss, mm * 0.75)
    lichen = np.clip(G.blur((rng.random(SH) < 0.003) * 1.0, 0.8) * 5, 0, 1)
    return G.over(img, _c((148, 152, 128)), lichen * 0.6)


def _sheathing() -> np.ndarray:
    """The underside seen from the attic: rough grey sheathing boards (4 px) with the tips of the
    shingle nails poking through and old leak stains."""
    rng = G.rng_for(39761)
    img = P._grey_wood(rng, vertical=False, base=(118, 112, 102), checks=4)
    img = G.add_lum(img, rng.normal(0, 7, 4)[(Y // 32).astype(int)])
    img = G.add_lum(img, -48 * G.smoothstep(1.4, 0.3, np.abs(G.wrapd(Y, 32))))
    for _ in range(18):
        img = P._nail(img, rng.uniform(4, S - 4), 16 * rng.integers(0, 8) + 7 + rng.normal(0, 0.6), rng, r=0.9, rust=0.8)
    for _ in range(2):
        img = G.mark_stain(img, rng, (110, 86, 60), size=(22, 18), a=0.3)
    return img


GABLES = {"straight": (1.0, 0.0, False), "ridge": (1.0, 0.0, True),
          "low_straight": (0.5, 0.0, False), "low_straight_upper": (0.5, 8.0, False),
          "low_ridge": (0.5, 0.0, True), "low_ridge_upper": (0.5, 8.0, True)}


def _gable_h(profile, z):
    """Height (px) of the roof surface along a gable side, facing north (z = 0 is the high side)."""
    k, base, ridge = GABLES[profile]
    return base + k * (np.minimum(16 - z, z) if ridge else 16 - z)


def _gable_tex(profile, shade) -> np.ndarray:
    """Gable side (u = z, v = 16 - y): clapboard under the roof line, cut away above it, with a
    white rake board ~2.6 px deep following the slope and its shadow on the boards below."""
    def load(name):
        im = Image.open(G.TEX / "block" / f"{name}.png").convert("RGB").resize((S, S), Image.NEAREST)
        return np.asarray(im, float)
    k = GABLES[profile][0]
    y, h = 16 - Y / 8, _gable_h(profile, X / 8)
    band = 2.6 * np.sqrt(1 + k * k)
    img = load(CLAPBOARD[shade])
    img = np.where((y > h - band)[..., None], load("l39_white_trim") - 6, img)
    img = G.add_lum(img, 18 * G.smoothstep(h - 0.4, h, y) - 34 * G.smoothstep(0.7, 0.0, (h - band) - y) * (y <= h - band))
    return np.dstack([img, np.where(y <= h, 255.0, 0.0)])


def tex_l39_housefurn_roof_textures() -> None:
    """Shingles (one per slope direction, plus cut-outs for hips and valleys), the sheathing
    underside and the gable wall sides of the smooth roofs."""
    sh = _shingles()
    dirs = {"N": sh, "S": sh[::-1], "W": sh.transpose(1, 0, 2), "E": sh.transpose(1, 0, 2)[:, ::-1]}
    under = _sheathing()
    unders = {"N": under, "S": under, "E": under.transpose(1, 0, 2), "W": under.transpose(1, 0, 2)}
    for p, img in dirs.items():
        _save(img, _roof_tex("shingles", "straight", p, False))
    _save(under, _roof_tex("underside", "straight", "N", False))
    _save(unders["E"], _roof_tex("underside", "straight", "E", False))
    for shape in ROOF_SHAPES:
        for p, _, m, cut in _roof_facets(shape):
            if cut:
                # Each cut-out reaches ROOF_OVERLAP texels past its own line, so the planes
                # overlap a hair along hips and valleys: a ray that slips between two texels
                # still lands on shingles instead of seeing through the roof.
                a = np.where(_grow_mask(m, ROOF_OVERLAP), 255.0, 0.0)
                _save(np.dstack([dirs[p], a]), _roof_tex("shingles", shape, p, True))
                _save(np.dstack([unders[p], a]), _roof_tex("underside", shape, p, True))
    for profile in GABLES:
        for shade in range(3):
            _save(_gable_tex(profile, shade), f"l39_roof_gable_{profile}_{shade}")


def _edge_owned(m, box, face) -> bool:
    """Does this plane own the whole block edge `face` of its box (so its edge face shows)?"""
    c0, c1, r0, r1 = (int(round(v * 8)) for v in box)
    if face == "north":
        return r0 == 0 and m[0, c0:c1].all()
    if face == "south":
        return r1 == S and m[S - 1, c0:c1].all()
    if face == "west":
        return c0 == 0 and m[r0:r1, 0].all()
    return c1 == S and m[r0:r1, S - 1].all()


def _roof_el(f, t, tex, faces, origin, axis, angle):
    e = P._el(f, t, tex, faces=tuple(faces), uv={"down": [f[0], t[2], t[0], f[2]]}, rot=(origin, axis, angle))
    e["rotation"]["rescale"] = True
    return e


def _roof_slab(p, box, m, base, top, bottom):
    """Steep plane: one 45-degree slab whose top face lies on the plane over the whole box."""
    x0, x1, z0, z1 = box
    xc, zc = (x0 + x1) / 2, (z0 + z1) / 2
    yc = base + _rise(p, xc, zc)
    faces = ["up", "down"] + [f for f in SIDES if _edge_owned(m, box, f)]
    tex = {"up": top, "down": bottom, **{f: "#edge" for f in SIDES}}
    return [_roof_el((x0, yc - 0.85, z0), (x1, yc, z1), tex, faces, (xc, yc, zc),
                     "x" if p in "NS" else "z", 45 if p in "NE" else -45)]


def _roof_strips(p, box, m, cut, base, top, bottom):
    """Low plane: 2 px strips turned 22.5 degrees, cut on the shingle courses (even pixels).
    A strip of a cut plane only spans the texels its plane owns in that strip.

    Each strip ends 0.17 px above the next one down. A turned box's end face leans back under
    the strip, which would leave a hairline you could see through from above, so a thin upright
    riser closes the step at every strip's down-slope end (it reads as a shingle butt)."""
    x0, x1, z0, z1 = box
    along_z = p in "NS"
    lo, hi = (z0, z1) if along_z else (x0, x1)
    cuts = [lo] + [float(v) for v in range(int(np.ceil(lo)), int(np.floor(hi)) + 1) if v % 2 == 0 and lo < v < hi] + [hi]
    uphill_first = p in "NW"
    tex = {"up": top, "down": bottom, **{f: "#edge" for f in SIDES}}
    out = []
    for i, (a, b) in enumerate(zip(cuts, cuts[1:])):
        sub = m[int(round(a * 8)):int(round(b * 8)), :] if along_z else m[:, int(round(a * 8)):int(round(b * 8))].T
        own = np.nonzero(sub.any(0))[0]
        c0, c1 = own.min(), own.max() + 1
        w0, w1 = c0 / 8, c1 / 8
        mid, wc = (a + b) / 2, (w0 + w1) / 2
        ym = base + 0.5 * (_rise(p, wc, mid) if along_z else _rise(p, mid, wc))
        faces = ["up", "down", DOWNHILL[p]]
        top_strip = i == 0 if uphill_first else i == len(cuts) - 2
        if top_strip and not cut and (a == 0 if uphill_first else b == 16):
            faces.append(UPHILL[p])
        side_lo, side_hi = ("west", "east") if along_z else ("north", "south")
        if c0 == 0 and sub[:, 0].all():
            faces.append(side_lo)
        if c1 == S and sub[:, S - 1].all():
            faces.append(side_hi)
        if along_z:
            f, t, o = (w0, ym - 1.1, a), (w1, ym, b), (wc, ym, mid)
        else:
            f, t, o = (a, ym - 1.1, w0), (b, ym, w1), (mid, ym, wc)
        out.append(_roof_el(f, t, tex, faces, o, "x" if along_z else "z", 22.5 if p in "NE" else -22.5))
        lip = ym - (b - a) / 2 * np.tan(np.radians(22.5))
        down_line = sub[-1] if p in "NW" else sub[0]
        own = np.nonzero(down_line)[0]
        r0, r1 = own.min() / 8, (own.max() + 1) / 8
        face = DOWNHILL[p]
        if p == "N":
            f, t = (r0, lip - 0.45, b - 0.1), (r1, lip, b)
        elif p == "S":
            f, t = (r0, lip - 0.45, a), (r1, lip, a + 0.1)
        elif p == "E":
            f, t = (a, lip - 0.45, r0), (a + 0.1, lip, r1)
        else:
            f, t = (b - 0.1, lip - 0.45, r0), (b, lip, r1)
        out.append(P._el(f, t, "#edge", faces=(face,)))
    return out


def _roof_model_name(low, upper, shape, trim, shade) -> str:
    n = ("l39_roof_low_" if low else "l39_roof_") + shape + ("_upper" if upper else "")
    if trim == "eave" and shape in EAVE_EDGES:
        n += "_eave"
    if trim == "gable" and shape in ("straight", "ridge"):
        n += f"_gable_{shade}"
    return n


def _roof_model(low, upper, shape, trim, shade) -> None:
    base = 8.0 if upper else 0.0
    tex = {"particle": _ref("l39_roof_shingles"), "edge": _ref("l39_roof_shingles")}
    els = []
    for p, box, m, cut in _roof_facets(shape):
        tex[f"s{p}"] = _ref(_roof_tex("shingles", shape, p, cut))
        tex[f"u{p}"] = _ref(_roof_tex("underside", shape, p, cut))
        if low:
            els += _roof_strips(p, box, m, cut, base, f"#s{p}", f"#u{p}")
        else:
            els += _roof_slab(p, box, m, base, f"#s{p}", f"#u{p}")
    if trim == "eave" and shape in EAVE_EDGES:
        # A white fascia board hanging under the low edge(s), 3 px deep.
        tex["trim"] = _ref("l39_white_trim")
        for edge in EAVE_EDGES[shape]:
            f, t = ((0, base - 3, 15), (16, base, 16)) if edge == "south" else ((15, base - 3, 0), (16, base, 16))
            els.append(P._el(f, t, "#trim", cull=False))
    if trim == "gable" and shape in ("straight", "ridge"):
        # The gable wall under the roof line: both sides cut to the slope (seen from outside and
        # from the attic), the end walls up to the roof height there, and the bottom.
        profile = ("low_" if low else "") + shape + ("_upper" if upper else "")
        tex["gable"] = _ref(f"l39_roof_gable_{profile}_{shade}")
        tex["clap"] = _ref(CLAPBOARD[shade])
        fwd, back = [0, 0, 16, 16], [16, 0, 0, 16]
        els += [P._el((0, 0, 0), (0, 16, 16), "#gable", faces=("west",), uv={"west": fwd}),
                P._el((0, 0, 0), (0, 16, 16), "#gable", faces=("east",), uv={"east": back}, cull=False),
                P._el((16, 0, 0), (16, 16, 16), "#gable", faces=("east",), uv={"east": back}),
                P._el((16, 0, 0), (16, 16, 16), "#gable", faces=("west",), uv={"west": fwd}, cull=False),
                P._el((0, 0, 0), (16, 0, 16), "#clap", faces=("down",))]
        hn, hs = float(_gable_h(profile, 0.0)), float(_gable_h(profile, 16.0))
        if hn > 0:
            els.append(P._el((0, 0, 0), (16, hn, 0), "#clap", faces=("north",)))
        if hs > 0:
            els.append(P._el((0, 0, 16), (16, hs, 16), "#clap", faces=("south",)))
    P._bm(_roof_model_name(low, upper, shape, trim, shade), P._model(tex, els))


MIRROR_Z = {"north": "south", "south": "north", "east": "east", "west": "west"}
CLOCKWISE = {"north": "east", "east": "south", "south": "west", "west": "north"}


def _roof_rotation(facing, shape, inverted):
    """Blockstate (x, y) of a roof piece.

    Upside down, Level39RoofBlock keeps the same surface and only turns it over (its collision
    is 1 - h at the same x, z; no left-right mirror like the furniture). A model can only be
    turned, never mirrored, so the trick is: turning a piece over about x (x: 180) also swaps
    north and south, so start from the piece that is its north-south mirror image (straight,
    ridge, ridge_end and apex: FACING mirrored; outer and inner: also one step clockwise, because
    a mirror swaps "toward FACING's left" for "toward its right"). x: 180 followed by y: a equals
    y: -a followed by x: 180, hence the negative y."""
    if not inverted:
        return 0, P.FACING_Y[facing]
    f = MIRROR_Z[facing]
    if shape in ("outer", "inner"):
        f = CLOCKWISE[f]
    return 180, (-P.FACING_Y[f]) % 360


def tex_l39_housefurn_roof_models() -> None:
    """Models, blockstates and item models of l39_roof and l39_roof_low (LF JSON)."""
    for low in (False, True):
        name = "l39_roof_low" if low else "l39_roof"
        done = set()
        variants = {}
        for upper in ((False, True) if low else (False,)):
            for shape in ROOF_SHAPES:
                for trim in ROOF_TRIMS:
                    for shade in range(3):
                        model = _roof_model_name(low, upper, shape, trim, shade)
                        if model not in done:
                            _roof_model(low, upper, shape, trim, shade)
                            done.add(model)
                        for facing in P.FACING_Y:
                            for inv in (False, True):
                                key = f"facing={facing},shape={shape},trim={trim},shade={shade},inverted={str(inv).lower()}"
                                if low:
                                    key += f",upper={str(upper).lower()}"
                                v = {"model": _ref(model)}
                                xr, yr = _roof_rotation(facing, shape, inv)
                                if xr:
                                    v["x"] = xr
                                if yr:
                                    v["y"] = yr
                                variants[key] = v
        P._bs(name, {"variants": variants})
        P._im(name, parent=_ref(_roof_model_name(low, False, "straight", "none", 0)))


TEXTURES = [tex_l39_housefurn_materials, tex_l39_housefurn_parts, tex_l39_housefurn_fronts,
            tex_l39_housefurn_models, tex_l39_housefurn_roof_textures, tex_l39_housefurn_roof_models]
SOUNDS = []
