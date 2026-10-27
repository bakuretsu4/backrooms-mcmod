"""Level 39 houses: the insides of the light-blue clapboard houses (textures, models, blockstates).

The 1920s rooms behind the clapboard: five wallpapers in Level 0's tones (each also with a painted
skirting board for the bottom row of a room), white beadboard, dark oak wainscot, bare horsehair
plaster, honey and walnut floorboards, a cream and oxblood linoleum, a white hexagon bathroom
mosaic and the reference photo's white popcorn ceiling. Plus the outside wall blocks (clapboard on
one side, the room's finish on the others), the rotten "knock" wall you break to get in, a sash
window with its roller shade pulled down, the bare bulbs screwed into the ceilings, a white
four-panel interior door, oak and porch stairs, the spindle banister and a Craftsman porch column.
And one sound: a rotten wall giving way.

The furniture lives in level39_houses_furniture.py.

Run with `python tools/gen_assets.py tex_l39_houses_ snd_l39_houses_` from the project root.
Every texture has its own fixed random seed (39600-39699), so re-running gives identical files.

The wall and floor blocks are Level39SurfaceBlocks: a plain texture and two worn versions
(<name>_v1, <name>_v2, picked by the world generator, chances 6:2:1). Each worn version is the same
seamless texture plus a few marks that fade out before the block's edges (the same rule as
gen_assets' "Surface variants"), so any two versions meet without a seam. Their blockstates are
written here (not by gen_assets.variant_models) because they also have the INVERTED property: the
upside-down houses use the same models turned over (x 180), which also puts the skirting boards
at the top of the walls and the popcorn ceiling underfoot.
"""
from __future__ import annotations

import numpy as np
from PIL import Image, ImageDraw

import gen_assets as G
from anomaly_assets import level39 as L39
from anomaly_assets import level39_audio as A
from anomaly_assets import level39_props as P

S = G.S                 # 128 px block texture size
SR = G.SR
X, Y = G.grid()         # pixel-centre coordinates of a block texture

_blk = P._blk
_itm = P._itm
_ref = P._ref


def _save(img, name):
    G.save_png(img, _blk(name))


def _load(name):
    """An existing block texture (RGB, float), e.g. L39Core's clapboard."""
    return np.asarray(Image.open(_blk(name)).convert("RGB"), float)


# =============================================================================
# Small drawing helpers
# =============================================================================

def _print(prims, ss=4, size=(S, S)):
    """Soft-edged drawing of PIL primitives, wrapped so the texture tiles: (rgb, coverage 0..1).

    prims as in level39_props._render: ("line", pts, width, rgba) | ("poly", pts, 0, rgba) |
    ("dot", [(x, y)], radius, rgba). Unlike _render the coverage stays soft, so a print can be laid
    over a paper with a partial alpha and some wear (ink never sits on paper as a hard cutout)."""
    w, h = size
    canvas = Image.new("RGBA", (w * ss, h * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(canvas)
    for kind, pts, width, col in prims:
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        pad = width + 2
        for ox in (-w, 0, w):
            if max(xs) + ox + pad < 0 or min(xs) + ox - pad > w:
                continue
            for oy in (-h, 0, h):
                if max(ys) + oy + pad < 0 or min(ys) + oy - pad > h:
                    continue
                q = [((x + ox) * ss, (y + oy) * ss) for x, y in pts]
                if kind == "line":
                    d.line(q, fill=col, width=max(1, int(round(width * ss))), joint="curve")
                elif kind == "poly":
                    d.polygon(q, fill=col)
                else:
                    (x, y), r = q[0], width * ss
                    d.ellipse((x - r, y - r, x + r, y + r), fill=col)
    a = np.asarray(canvas, float).reshape(h, ss, w, ss, 4)
    cov = a[..., 3].mean(axis=(1, 3)) / 255.0
    wgt = a[..., 3:4] / 255.0
    rgb = (a[..., :3] * wgt).sum(axis=(1, 3)) / np.maximum(wgt.sum(axis=(1, 3)), 1e-6)
    return rgb, cov


def _c(col, a=255):
    return tuple(int(v) for v in np.clip(col, 0, 255)) + (a,)


def _ink(img, col, cov, rng, a=1.0, wear=0.12):
    """Lay a printed colour (flat colour or an RGB image) over img. The ink is worn a little
    thinner in broad soft patches only, so the printed shapes themselves stay crisp."""
    w = np.clip(1 - wear + wear * G.fnoise(rng, beta=3.0), 0.5, 1.0)
    return G.over(img, np.asarray(col, float), np.clip(cov * a * w, 0, 1))


def _dots(points, r, shape=(S, S)):
    """Soft round dots (wrapped) at the given points, 0..1."""
    Xs, Ys = G.grid(shape)
    m = np.zeros(shape)
    for x, y in points:
        d = np.hypot(G.wrapd(Xs - x, shape[1]), G.wrapd(Ys - y, shape[0]))
        m = np.maximum(m, G.smoothstep(r + 0.5, r - 0.5, d))
    return m


def _profile(rows, lums, y0=0.0):
    """Brightness down a moulding profile: lums at the given texture rows (relative to y0)."""
    return np.interp(Y - y0, rows, lums)


# =============================================================================
# Wallpapers
# =============================================================================
# Period wallpaper: a fine printed pattern on a tinted ground. The pattern repeats every 32 px
# (a quarter block, about 25 cm, as real 1920s papers do), so a block shows a 4 x 4 repeat. Each
# repeat is drawn once as anti-aliased vector shapes at 8x supersampling and then tiled, so every
# repeat is identical and sharp (a print, not a painting). On top: broad gentle discolouration,
# the faint butt seams of the two half-metre drops per block, and a quiet paper grain.
# (No top-to-bottom fade inside the block: walls stack these blocks, so it would band.)

REP = 32                                   # pattern repeat in texture px


def _rep(tile):
    """A 32 px tile (2D, or HxWxC) repeated 4 x 4 to fill a block texture."""
    return np.tile(tile, (S // REP, S // REP) + (1,) * (tile.ndim - 2))


def _shape(inside, ss=8):
    """Anti-aliased coverage (0..1) of a shape drawn on the 32 px tile and repeated over the
    block. inside(x, y) gets supersample coordinates (0..32) and wraps them itself."""
    return _rep(G.supersample((REP, REP), inside, ss))


def _tile_print(prims, ss=8):
    """_print on the 32 px tile (wrapped there), repeated over the block: (rgb, coverage)."""
    rgb, cov = _print(prims, ss, (REP, REP))
    return _rep(rgb), _rep(cov)


def _tw(v):
    return G.wrapd(v, REP)


def _paper_ground(rng, col):
    """Wallpaper stock: the printed ground colour, gentle mottling and a fine, quiet grain."""
    img = G.fill(col, (S, S))
    lum = G.fnoise(rng, beta=3.2) * 2.5 + G.fnoise(rng, beta=1.0, kmin=20) * 0.9
    return G.add_lum(img, lum, (1.0, 0.97, 0.88))


def _paper_age(img, rng, yellow=0.12):
    """Gentle uneven yellowing and darkening in large soft patches, the faint roll seams (a darker
    hairline at x 0 and 64 with the lifted edge beside it catching light), fine grain."""
    yel = G.smoothstep(-1.0, 1.8, G.fnoise(rng, beta=3.2))
    img = G.over(img, np.array([186, 156, 92]), yel * yellow)
    dis = G.smoothstep(0.6, 2.2, G.fnoise(rng, beta=2.8, aniso=(1, 3)))
    img = G.over(img, np.array([132, 118, 92]), dis * 0.07)
    col = np.floor(X) % 64
    img = G.add_lum(img, -7.0 * (col == 0) + 3.0 * (col == 1))
    return G.add_lum(img, rng.normal(0, 0.8, (S, S)))


# ---- damask -----------------------------------------------------------------
# One ogee medallion per repeat (centre 16, 16): a fine outline round a satin field, a palmette
# with a cut vein, two curled side leaves, a cup and a finial; a four-petal fleuron in the gap at
# the repeat's corner and a small diamond linking the medallions above and below each other.

def _ogee(x, y):
    u, v = np.abs(_tw(x - 16)), _tw(y - 16)
    half = 7.2 * np.clip(1 - (v / 12.5) ** 2, 0, 1) ** 0.65
    return u, v, half


def _damask_frame(x, y):
    u, v, half = _ogee(x, y)
    return ((np.abs(v) < 12.4) & (np.abs(u - half) < 0.5)) | (np.hypot(u, np.abs(v) - 12.9) < 0.75)


def _damask_field(x, y):
    u, v, half = _ogee(x, y)
    return (np.abs(v) < 12.4) & (u < half - 0.5)


def _damask_palmette(x, y):
    u, v, _ = _ogee(x, y)
    t = (v + 8) / 13
    pw = 3.0 * np.sin(np.pi * np.clip(t, 0, 1)) ** 0.9 * (1 - 0.25 * t)
    body = (t > 0) & (t < 1) & (u < pw) & ~((u < 0.35) & (t > 0.15) & (t < 0.85))
    a = 0.55
    lx = (u - 4.4) * np.cos(a) - (v - 2.6) * np.sin(a)
    ly = (u - 4.4) * np.sin(a) + (v - 2.6) * np.cos(a)
    lobe = (lx / 0.95) ** 2 + (ly / 2.5) ** 2 < 1
    curl = (np.abs(np.hypot(u - 4.9, v + 2.2) - 1.2) < 0.38) & (v < -1.6)
    cup = (u / 2.8) ** 2 + ((v - 7.3) / 1.0) ** 2 < 1
    stem = (u < 0.42) & (v > 5) & (v < 7)
    finial = np.hypot(u, v + 10.1) < 0.95
    return body | lobe | curl | cup | stem | finial


def _damask_fleuron(x, y):
    fu, fv = np.abs(_tw(x)), np.abs(_tw(y))
    petals = ((fu / 0.95) ** 2 + ((fv - 2.5) / 1.6) ** 2 < 1) | (((fu - 2.5) / 1.6) ** 2 + (fv / 0.95) ** 2 < 1)
    dots = (np.hypot(fu - 3.5, fv - 3.5) < 0.6) | (np.hypot(fu, fv) < 0.75)
    du, dv = np.abs(_tw(x - 16)), np.abs(_tw(y))
    eu, ev = np.abs(_tw(x)), np.abs(_tw(y - 16))
    links = (du + dv < 1.4) | (eu + ev < 1.4)
    return petals | dots | links


def paper_damask():
    """Level 0's mustard in a fine damask: ochre outline and fleurons, a slightly lighter satin
    field inside each medallion, the palmette in a mid tone."""
    rng = G.rng_for(39600)
    img = _paper_ground(rng, (212, 184, 94))
    img = _ink(img, (222, 196, 106), _shape(_damask_field), rng, 0.85)
    img = _ink(img, (180, 144, 64), _shape(_damask_palmette), rng, 0.9)
    img = _ink(img, (168, 130, 54), _shape(_damask_frame), rng, 0.9)
    img = _ink(img, (172, 134, 58), _shape(_damask_fleuron), rng, 0.85)
    return _paper_age(img, rng, 0.1)


# ---- stripe -----------------------------------------------------------------

def _vband(x0, x1):
    return _rep(G.box((REP, REP), x0, -2, x1, REP + 2))


def paper_stripe():
    """Cream with an olive-gold satin stripe (fine darker edge lines, a woven rib inside it) and
    a pair of thin olive pinstripes between the stripes; a pale gold hairline on the other side."""
    rng = G.rng_for(39601)
    img = _paper_ground(rng, (230, 218, 184))
    band = _vband(10, 21)
    img = _ink(img, (196, 178, 112), band, rng, 0.9)
    img = G.add_lum(img, band * np.where(np.floor(X) % 2 == 0, 2.5, -2.5))
    for x0 in (11.0, 19.2):
        img = _ink(img, (164, 148, 86), _vband(x0, x0 + 0.8), rng, 0.85)
    for x0 in (25.8, 28.2):
        img = _ink(img, (146, 134, 78), _vband(x0, x0 + 0.8), rng, 0.9)
    img = _ink(img, (206, 188, 122), _vband(4.4, 5.2), rng, 0.8)
    return _paper_age(img, rng, 0.1)


# ---- floral -----------------------------------------------------------------

def _flower(x, y, r, rot, petal, centre):
    pr = [("dot", [(x + 1.05 * r * np.cos(rot + p * 2 * np.pi / 5), y + 1.05 * r * np.sin(rot + p * 2 * np.pi / 5))],
           0.95 * r, _c(petal)) for p in range(5)]
    pr.append(("dot", [(x, y)], 0.62 * r, _c(centre)))
    pr.append(("dot", [(x - 0.2, y - 0.2)], 0.28 * r, _c((222, 192, 124))))
    return pr


def paper_floral():
    """Off-white/beige with small sprigs on a half-drop repeat: a dusty-rose five-petal flower
    on a curved stem with two sage leaves, and a rosebud sprig between them; tiny dots fill in."""
    rng = G.rng_for(39602)
    img = _paper_ground(rng, (228, 216, 188))
    rose, rose_d, sage, sage_d, stem = (192, 124, 120), (156, 86, 84), (128, 144, 106), (112, 128, 94), (110, 120, 86)
    pr = [("line", [(17.8, 23.0), (17.4, 20.6), (16.3, 18.0)], 0.55, _c(stem)),
          ("poly", P._leaf_poly(17.5, 21.6, 4.6, 1.7, -0.45), 0, _c(sage)),
          ("poly", P._leaf_poly(17.0, 19.6, 4.0, 1.5, np.pi + 0.55), 0, _c(sage_d))]
    pr += _flower(16.0, 15.6, 1.5, 0.3, rose, rose_d)
    # the bud sprig at the repeat's corner (half-drop)
    pr += [("line", [(0.6, 4.2), (0.2, 2.2), (0.0, 0.4)], 0.5, _c(stem)),
           ("poly", P._leaf_poly(0.4, 2.8, 3.0, 1.2, 0.5), 0, _c(sage)),
           ("poly", P._leaf_poly(0.3, 2.2, 2.8, 1.1, np.pi - 0.6), 0, _c(sage_d)),
           ("dot", [(0.0, -0.6)], 1.25, _c(rose)),
           ("dot", [(0.3, -1.2)], 0.7, _c(rose_d)),
           ("poly", [(-1.0, 0.2), (1.0, 0.2), (0.0, 1.2)], 0, _c(sage_d))]
    for cx, cy in ((0.0, 16.0), (16.0, 0.0)):
        for a in (0.0, 2.1, 4.2):
            pr.append(("dot", [(cx + 1.1 * np.cos(a), cy + 1.1 * np.sin(a))], 0.45, _c((206, 156, 150))))
    rgb, cov = _tile_print(pr)
    img = _ink(img, rgb, cov, rng, 0.92)
    return _paper_age(img, rng, 0.12)


# ---- rosebud trellis --------------------------------------------------------

def _diag(period, w, dy=0.0):
    def inside(x, y):
        y = y - dy
        return (np.abs(G.wrapd(x - y, period)) / np.sqrt(2) < w) | (np.abs(G.wrapd(x + y, period)) / np.sqrt(2) < w)
    return inside


def paper_rose():
    """Dusty-rose ground with a fine light trellis (a darker shadow line under each lath), a
    rosebud with two leaves at every crossing and three tiny dots in the middle of each diamond."""
    rng = G.rng_for(39603)
    img = _paper_ground(rng, (198, 150, 144))
    line = _shape(_diag(REP, 0.42))
    shadow = np.clip(_shape(_diag(REP, 0.42, 0.9)) - line, 0, 1)
    img = _ink(img, (176, 128, 122), shadow, rng, 0.7)
    img = _ink(img, (220, 182, 172), line, rng, 0.9)
    pr = []
    for x, y in ((0.0, 0.0), (16.0, 16.0)):
        pr += [("poly", P._leaf_poly(x - 0.4, y + 1.4, 3.2, 1.3, np.pi * 0.78), 0, _c((126, 128, 92))),
               ("poly", P._leaf_poly(x + 0.4, y + 1.4, 3.2, 1.3, np.pi * 0.22), 0, _c((114, 118, 86))),
               ("dot", [(x, y - 0.2)], 1.9, _c((156, 80, 84))),
               ("dot", [(x + 0.4, y - 1.0)], 1.1, _c((178, 100, 102))),
               ("line", [(x - 1.0, y + 0.1), (x - 0.1, y - 0.9), (x + 0.9, y - 0.2)], 0.4, _c((128, 62, 66))),
               ("poly", [(x - 1.2, y + 1.2), (x + 1.2, y + 1.2), (x, y + 2.3)], 0, _c((114, 118, 86)))]
    for x, y in ((8.0, 8.0), (24.0, 8.0), (8.0, 24.0), (24.0, 24.0)):
        for a in (-np.pi / 2, np.pi / 6, 5 * np.pi / 6):
            pr.append(("dot", [(x + 1.0 * np.cos(a), y + 1.0 * np.sin(a))], 0.42, _c((224, 190, 180))))
    rgb, cov = _tile_print(pr)
    img = _ink(img, rgb, cov, rng, 0.95)
    return _paper_age(img, rng, 0.1)


# ---- sage lattice -----------------------------------------------------------

def paper_lattice():
    """Sage-green with a fine light diamond lattice (16 px diamonds, a darker shadow line under
    each line), a tiny four-petal fleuron in every other diamond and a dot in the rest."""
    rng = G.rng_for(39604)
    img = _paper_ground(rng, (148, 164, 128))
    line = _shape(_diag(16, 0.38))
    shadow = np.clip(_shape(_diag(16, 0.38, 0.8)) - line, 0, 1)
    img = _ink(img, (122, 138, 104), shadow, rng, 0.8)
    img = _ink(img, (194, 204, 170), line, rng, 0.9)
    pr = []
    for x, y in ((8.0, 0.0), (24.0, 0.0), (8.0, 16.0), (24.0, 16.0)):
        for dx, dy in ((1.5, 0), (-1.5, 0), (0, 1.5), (0, -1.5)):
            pr.append(("dot", [(x + dx, y + dy)], 0.9, _c((104, 122, 90))))
        pr.append(("dot", [(x, y)], 0.55, _c((222, 214, 176))))
    for x, y in ((0.0, 8.0), (16.0, 8.0), (0.0, 24.0), (16.0, 24.0)):
        pr.append(("dot", [(x, y)], 0.6, _c((112, 130, 96))))
    rgb, cov = _tile_print(pr)
    img = _ink(img, rgb, cov, rng, 0.95)
    return _paper_age(img, rng, 0.1)


SKIRT_TOP = S - 28            # 3.5 model px of skirting board


def _skirting(img, rng=None):
    """A cream painted wooden skirting board along the bottom: ogee top moulding, flat face with
    horizontal grain under the paint, a scuffed shoe at the floor, a soft shadow on the paper."""
    rng = G.rng_for(39610)
    col = np.array([214, 206, 182])
    board = G.add_lum(G.fill(col, (S, S)), G.fnoise(rng, beta=1.6, aniso=(12, 1), kmin=2) * 3
                      + G.fnoise(rng, beta=0.8, aniso=(12, 1), kmin=8) * 1.4)
    t = SKIRT_TOP
    board = G.add_lum(board, _profile([0, 1, 2.5, 4, 5.5, 7, 8, 26, 26.8, 28], [12, 16, 6, -10, -4, 8, 0, 0, 6, -12], t))
    board = G.add_lum(board, -8 * G.smoothstep(S - 6, S, Y))           # dust settles on the shoe
    cracks = P._checks(rng, 4, False, (20, 60))
    board = G.add_lum(board, -12 * cracks * (Y > t + 9))
    img = G.add_lum(img, -16 * np.exp(-((Y - t) / 2.0) ** 2) * (Y < t))  # contact shadow on the paper
    img = G.over(img, board, G.box((S, S), -2, t, S + 2, S + 2))
    scuff = G.smoothstep(1.2, 2.2, G.fnoise(rng, beta=2.6, aniso=(8, 1), kmin=2)) * (Y > t + 10)
    return G.over(img, np.array([120, 112, 100]), scuff * 0.35)


# ---- worn versions ------------------------------------------------------------

def _tide_stain(img, rng):
    """A faint old leak: a pale brown stain with crisp darker tide-mark rings where the water
    stopped spreading each time (contours of one soft blob), and a thin drip run below it."""
    sx, sy = rng.uniform(15, 20), rng.uniform(18, 24)
    cx, cy = G._spot(rng, 1.3 * sx, 1.3 * sy, (0.1, 0.55))
    wob = G.blur(G.fnoise(rng, beta=3.0), 3.0)
    m = G._local(cx, cy, sx, sy) * (1 + 0.14 * wob / wob.std())                 # a smooth, irregular outline
    fade = G._edge_fade()
    img = G.over(img, np.array([170, 132, 74]), G.smoothstep(0.3, 0.5, m) * 0.13 * fade)
    gy, gx = np.gradient(m)
    g = np.maximum(np.hypot(gx, gy), 1e-4)
    for lv, a in ((0.3, 0.24), (0.47, 0.15), (0.66, 0.1)):
        ring = G.smoothstep(0.9, 0.25, np.abs(m - lv) / g)
        img = G.over(img, np.array([138, 98, 52]), ring * a * fade)
    return G.mark_drip(img, rng, (150, 112, 62), 0.15, (22, 36))


def _peel_seam(img, rng, x0=64.0, under=(178, 168, 146)):
    """The roll seam at x 64 come unstuck: the paper's edge has lifted and curled back, showing
    the plaster under it (with scraps of an older green-striped paper and brown paste), the curl's
    pale back shaded across its roll, and a thin shadow cast beside it. Crisp-edged polygons."""
    y0 = rng.uniform(16, 26)
    y1 = y0 + rng.uniform(40, 54)
    ts = np.linspace(0, 1, 11)
    ys = y0 + ts * (y1 - y0)
    ws = rng.uniform(8, 12) * np.sin(np.pi * ts) ** 0.8 * rng.uniform(0.85, 1.15, ts.size)
    cws = 2.6 * np.sin(np.pi * ts) ** 0.5 + 0.3
    edge = [(x0 + w, y) for w, y in zip(ws, ys)]
    outer = [(x0 + w + c, y) for w, c, y in zip(ws, cws, ys)]

    def poly(pts):
        return G.pil_mask(lambda d, k: d.polygon([(x * k, y * k) for x, y in pts], fill=255), ss=8)

    shadow = poly(outer + [(x + 2.0, y + 1.5) for x, y in outer[::-1]])
    bare = poly([(x0, y0)] + edge + [(x0, y1)])
    curl = np.clip(poly(edge + outer[::-1]) - bare, 0, 1)
    img = G.add_lum(img, -22 * np.clip(shadow - curl - bare, 0, 1))
    old = G.add_lum(G.fill(under, (S, S)), G.fnoise(rng, beta=3.0) * 4 + rng.normal(0, 1.2, (S, S)))
    scraps = G.smoothstep(0.1, 0.4, G.fnoise(rng, beta=3.0))
    old = G.over(old, np.array([150, 164, 134]), scraps * (np.floor(X) % 6 < 3) * 0.7)
    old = G.over(old, np.array([164, 132, 80]), G.smoothstep(0.4, 1.4, G.fnoise(rng, beta=2.8)) * 0.35)
    wrow = np.interp(Y, ys, ws)
    cwrow = np.interp(Y, ys, cws)
    old = G.add_lum(old, -20 * G.smoothstep(wrow - 3.5, wrow, X - x0))          # under the lifted edge
    img = G.over(img, old, bare)
    back = G.add_lum(G.fill((212, 202, 176), (S, S)), G.fnoise(rng, beta=3.0) * 3)
    across = np.clip((X - x0 - wrow) / np.maximum(cwrow, 0.3), 0, 1)
    back = G.add_lum(back, 6 - 30 * across ** 1.4)
    return G.over(img, back, curl)


def _picture_ghost(img, rng):
    """Where a picture hung for decades: a crisp rectangle of the paper's original, richer colour
    (the rest has faded round it), with a line of dust along its top edge."""
    w, h = rng.uniform(20, 28), rng.uniform(24, 32)
    cx, cy = G._spot(rng, w / 2 + 4, h / 2 + 4, (0.15, 0.6))
    m = G.box((S, S), cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, soft=0.6)
    lum = L39._lum(img)[..., None]
    rich = lum + (img - lum) * 1.18 - 4
    img = G.lerp(img, rich, m[..., None] * 0.8)
    return G.add_lum(img, -8 * G.box((S, S), cx - w / 2, cy - h / 2 - 1, cx + w / 2, cy - h / 2, soft=0.4))


def _paper_marks(i, rng, img):
    if i == 1:                                     # an old leak's tide marks, scuffs low down
        img = _tide_stain(img, rng)
        return G.mark_scuffs(img, rng, (120, 108, 90), 3, (0.76, 0.9), 0.22)
    img = _peel_seam(img, rng)                     # the seam lifting, a picture's ghost, scuffs
    img = _picture_ghost(img, rng)
    return G.mark_scuffs(img, rng, (110, 100, 86), 4, (0.74, 0.9), 0.25)


PAPERS = {"damask": paper_damask, "stripe": paper_stripe, "floral": paper_floral,
          "rose": paper_rose, "lattice": paper_lattice}


def _versions(name, base, marks, seed):
    """Save name.png and the worn versions name_v1/_v2 (same base + local marks)."""
    _save(base, name)
    out = [base]
    for i in (1, 2):
        img = marks(i, G.rng_for(seed + 7 * i), base.copy())
        _save(img, f"{name}_v{i}")
        out.append(img)
    return out


def tex_l39_houses_papers() -> None:
    """The five wallpapers and their skirting-board versions (each with two worn versions)."""
    for k, (key, fn) in enumerate(PAPERS.items()):
        versions = _versions(f"l39_paper_{key}", fn(), _paper_marks, 39650 + 3 * k)
        rng = G.rng_for(39670 + k)
        for i, img in enumerate(versions):
            based = _skirting(img)
            if i:
                based = G.mark_scuffs(based, rng, (92, 84, 72), 3, (0.82, 0.95), 0.35)
            _save(based, f"l39_paper_{key}_base" + (f"_v{i}" if i else ""))


# =============================================================================
# Beadboard, wainscot, plaster
# =============================================================================

def _painted_rows(rng, col, rows, lums, y0):
    """A horizontally grained painted strip with a moulding profile (cap rails and baseboards)."""
    img = G.add_lum(G.fill(col, (S, S)), G.fnoise(rng, beta=1.6, aniso=(12, 1), kmin=2) * 3)
    return G.add_lum(img, _profile(rows, lums, y0))


def beadboard():
    """White painted vertical tongue-and-groove (4 boards per block, a bead down the middle of
    each), a chair-rail cap on top and a baseboard at the bottom; yellowed, chipped, scuffed."""
    rng = G.rng_for(39620)
    col = np.array([222, 214, 188])
    img = G.add_lum(G.fill(col, (S, S)), G.fnoise(rng, beta=1.6, aniso=(1, 12), kmin=2) * 3.5
                    + G.fnoise(rng, beta=0.8, aniso=(1, 12), kmin=8) * 1.5)
    board = np.floor(X / 32).astype(int)
    img = G.add_lum(img, rng.normal(0, 2.5, 4)[board % 4])
    j = G.wrapd(X, 32)
    img = G.add_lum(img, -38 * G.smoothstep(1.1, 0.2, np.abs(j)) - 12 * G.smoothstep(2.6, 0.8, j) * (j > 0)
                    + 6 * G.smoothstep(2.4, 1.0, -j) * (j < 0))
    b = G.wrapd(X - 16, 32)
    img = G.add_lum(img, 10 * np.exp(-((b + 0.7) / 0.8) ** 2) - 10 * np.exp(-((b - 1.0) / 0.7) ** 2)
                    - 20 * np.exp(-((np.abs(b) - 2.4) / 0.45) ** 2))
    img = G.add_lum(img, -10 * P._checks(rng, 6, True, (20, 50)))       # hairline paint cracks
    # Chair-rail cap: rows 0-16 (2 model px), a rounded nosing over a cove.
    cap = _painted_rows(rng, col * 1.01, [0, 1.5, 3, 6, 7, 8, 11, 14, 16], [14, 10, 4, -6, -16, 8, 0, -10, -18], 0)
    img = np.where((Y < 16)[..., None], cap, img)
    img = G.add_lum(img, -16 * G.smoothstep(20, 16, Y) * (Y >= 16))
    # Baseboard: rows 104-128, bevelled top, flat face, shoe moulding.
    base = _painted_rows(rng, col * 0.99, [104, 105, 107, 109, 123, 124, 125.5, 127, 128], [16, 12, 4, 0, 0, -10, 8, 0, -8], 0)
    img = G.add_lum(img, -14 * G.smoothstep(98, 104, Y) * (Y < 104))
    img = np.where((Y >= 104)[..., None], base, img)
    # Ageing: yellowed unevenly, a few chips to bare wood, grime and kicks low down.
    yel = G.smoothstep(-1.0, 1.6, G.fnoise(rng, beta=2.6))
    img = G.over(img, np.array([200, 176, 120]), yel * 0.18)
    chips = G.smoothstep(2.1, 2.5, G.blur(G.fnoise(rng, beta=1.3, kmin=4), 0.6))
    img = G.over(img, np.array([150, 120, 84]), chips * 0.85)
    img = G.add_lum(img, -14 * np.clip(G.blur(chips, 1.0) - chips, 0, 1) * 3)
    grime = G.smoothstep(70, 128, Y) * G.smoothstep(0.0, 1.5, G.fnoise(rng, beta=2.0, aniso=(3, 1)))
    img = G.over(img, np.array([120, 110, 92]), grime * 0.22)
    return G.add_lum(img, rng.normal(0, 1.2, (S, S)))


def _beadboard_marks(i, rng, img):
    if i == 1:                                     # chair and shoe scuffs, a chipped patch
        img = G.mark_scuffs(img, rng, (90, 84, 74), 5, (0.55, 0.85), 0.35)
        m, _, _ = L39._lmask(rng, (10, 8), (0.3, 0.7), rough=0.35, lo=0.4, hi=0.5)
        img = G.over(img, np.array([146, 116, 80]), m * 0.9)
        return G.add_lum(img, -16 * np.clip(G.blur(m, 1.0) - m, 0, 1) * 3)
    img = G.mark_damp(img, rng, (24, 18), 0.18, (0.9, 1.0, 1.25), (0.55, 1.0))   # damp rising from the floor
    return G.mark_stain(img, rng, (166, 136, 84), (18, 14), 0.25, (0.5, 0.9))


WOOD_DARK = np.array([92, 60, 36])


def _oak(rng, col, vertical):
    """Stained quarter-sawn oak: grain streaks plus the short pale ray flecks across them."""
    aniso = (1, 12) if vertical else (12, 1)
    img = G.add_lum(G.fill(col, (S, S)), G.fnoise(rng, beta=2.0, aniso=aniso) * 6
                    + G.fnoise(rng, beta=0.9, aniso=aniso, kmin=8) * 3, (1.0, 0.78, 0.55))
    fl = G.fnoise(rng, beta=1.0, aniso=(1, 3) if not vertical else (3, 1), kmin=10)
    return G.add_lum(img, 9 * G.smoothstep(1.6, 2.4, fl), (1.0, 0.86, 0.66))


def wainscot():
    """Dark stained oak wainscot: two raised panels between stiles and rails, a chair-rail
    moulding on top and a baseboard at the bottom. Varnish worn dull in places, scratched."""
    rng = G.rng_for(39621)
    V = _oak(rng, WOOD_DARK, True)
    H = _oak(rng, WOOD_DARK * 1.02, False)
    Pn = _oak(rng, WOOD_DARK * 1.05, True)
    img = V.copy()
    rails = ((Y >= 14) & (Y < 30)) | ((Y >= 96) & (Y < 104))
    img = np.where(rails[..., None], H, img)
    xs = (6, 58, 70, 122)
    for x0, x1 in ((xs[0], xs[1]), (xs[2], xs[3])):
        outer = G.box((S, S), x0, 30, x1, 96)
        inner = G.box((S, S), x0 + 7, 37, x1 - 7, 89)
        img = G.over(img, Pn, outer)
        bevel = outer - inner
        top = (Y - 30) < np.minimum(np.minimum(X - x0, x1 - X), 96 - Y)
        left = (X - x0) < np.minimum(np.minimum(Y - 30, 96 - Y), x1 - X)
        right = (x1 - X) < np.minimum(np.minimum(Y - 30, 96 - Y), X - x0)
        lum = np.where(top, 16, np.where(left, 8, np.where(right, -14, -20)))
        img = G.add_lum(img, bevel * lum + 5 * inner)
        img = G.add_lum(img, -28 * (G.box((S, S), x0 - 1, 29, x1 + 1, 97) - outer))       # sticking line
        img = G.add_lum(img, -10 * (G.box((S, S), x0 + 6, 36, x1 - 6, 90) - inner))      # field edge
        img = G.over(img, np.array([118, 104, 88]), G.smoothstep(84, 89, Y) * inner * 0.18)   # dust on the bottom bevel
    img = G.add_lum(img, -20 * G.smoothstep(0.9, 0.2, np.abs(Y - 30)) * ((X < 6) | (X > 122) | ((X > 58) & (X < 70))))
    img = G.add_lum(img, -20 * G.smoothstep(0.9, 0.2, np.abs(Y - 96)) * ((X < 6) | (X > 122) | ((X > 58) & (X < 70))))
    # Chair rail (rows 0-14) and baseboard (104-128), both horizontal grain.
    cap = G.add_lum(H, _profile([0, 1, 3, 5, 6, 7.5, 10, 12, 13, 14], [18, 22, 10, -4, -18, 6, 10, -2, -20, -26]))
    img = np.where((Y < 14)[..., None], cap, img)
    base = G.add_lum(_oak(rng, WOOD_DARK * 0.95, False),
                     _profile([104, 105, 107, 109, 110, 124, 125, 126.5, 128], [-24, 16, 10, -4, 2, 0, -14, 8, -8]))
    img = np.where((Y >= 104)[..., None], base, img)
    img = G.add_lum(img, -14 * G.smoothstep(17, 14, Y) * (Y >= 14))   # the chair rail's shadow
    # Varnish: a soft sheen, dull worn patches, fine scratches.
    sheen = G.smoothstep(0.6, 2.0, G.fnoise(rng, beta=2.2, aniso=(1, 3)))
    img = G.add_lum(img, 10 * sheen, (1.0, 0.9, 0.75))
    dull = G.smoothstep(0.6, 1.8, G.fnoise(rng, beta=2.4))
    img = G.over(img, np.array([118, 92, 66]), dull * 0.25)
    scr = P._checks(rng, 10, False, (10, 30))
    img = G.add_lum(img, 14 * scr * dull)
    return G.add_lum(img, rng.normal(0, 1.5, (S, S)))


def _wainscot_marks(i, rng, img):
    if i == 1:                                     # varnish worn through by chair backs: pale, scratched
        m, _, _ = L39._lmask(rng, (30, 10), (0.15, 0.45), rough=0.5)
        img = G.over(img, np.array([140, 108, 74]), m * 0.45)
        return G.add_lum(img, 18 * P._checks(rng, 8, False, (8, 22)) * m)
    img = G.mark_damp(img, rng, (26, 20), 0.28, (0.9, 1.0, 1.2), (0.5, 1.0))   # water-blackened, a split panel
    pts = P._wander(rng, rng.uniform(30, 50), 40, np.pi / 2 + rng.normal(0, 0.1), 3.0, 14, 0.08)
    crack = G.pil_mask(lambda d, k: d.line([(x * k, y * k) for x, y in pts], fill=255, width=k)) * G._edge_fade()
    img = G.add_lum(img, -34 * crack)
    return G.add_lum(img, 10 * np.roll(crack, -1, 1) * (1 - crack))


def _plaster_base(seed, col=(178, 174, 164)):
    """Bare horsehair plaster, greyed with age: trowel sweeps, sand grain, the hair binding it,
    two newer patches (smoother, whiter, with a seam), and hairline cracks. Seamless."""
    rng = G.rng_for(seed)
    img = G._smooth_concrete(rng, col, mottle=4.0, fine=1.0)
    trowel = G.fnoise(rng, beta=2.4, aniso=(3, 1)) * 4 + G.fnoise(rng, beta=1.6, aniso=(1, 2), kmin=4) * 2
    img = G.add_lum(img, trowel + G.blur(rng.normal(0, 3, (S, S)), 0.5))
    prims = []
    for _ in range(320):
        x, y = rng.uniform(0, S, 2)
        a = rng.uniform(0, np.pi)
        L = rng.uniform(1.5, 4.5)
        pts = [(x, y), (x + L * np.cos(a) * 0.5, y + L * np.sin(a) * 0.5 + rng.normal(0, 0.4)), (x + L * np.cos(a), y + L * np.sin(a))]
        prims.append(("line", pts, 0.28, _c(rng.choice([(84, 70, 56), (130, 112, 90), (60, 52, 46)]))))
    rgb, cov = _print(prims)
    img = G.over(img, rgb, cov * 0.45)
    for _ in range(2):
        cx, cy = rng.uniform(0, S, 2)
        w, h = rng.uniform(14, 26), rng.uniform(10, 20)
        poly = [(cx + w * np.cos(t) * rng.uniform(0.85, 1.1), cy + h * np.sin(t) * rng.uniform(0.85, 1.1))
                for t in np.linspace(0, 2 * np.pi, 9)[:-1]]
        m = P._wrap_mask(lambda d, k, ox, oy, poly=poly: d.polygon([((x + ox) * k, (y + oy) * k) for x, y in poly], fill=255))
        patch = G.add_lum(G.fill(np.array(col) + (10, 10, 8), (S, S)), G.fnoise(rng, beta=2.6) * 2)
        img = G.over(img, patch, m * 0.6)
        img = G.add_lum(img, -8 * np.clip(G.blur(m, 0.8) - m, 0, 1) * 3)
    lines = []
    for _ in range(4):
        lines.append(P._wander(rng, rng.uniform(0, S), rng.uniform(0, S), rng.uniform(0, 2 * np.pi), 3.0, int(rng.integers(8, 18)), 0.45))
    m = P._wrap_mask(lambda d, k, ox, oy: [d.line([((x + ox) * k, (y + oy) * k) for x, y in ln], fill=255, width=k) for ln in lines])
    img = G.add_lum(img, -26 * m - 6 * G.blur(m, 1.2))
    grime = G.smoothstep(0.2, 1.8, G.fnoise(rng, beta=2.4))
    return G.over(img, np.array([120, 114, 102]), grime * 0.18)


def plaster():
    return _plaster_base(39622)


def _lath_hole(img, rng, size=(16, 12), y_range=(0.2, 0.8)):
    """Plaster fallen off: the wooden lath strips behind it with dark gaps and plaster keys."""
    m, cx, cy = L39._lmask(rng, size, y_range, rough=0.7, lo=0.42, hi=0.5)
    lath = G.add_lum(G.fill((136, 110, 80), (S, S)), G.fnoise(rng, beta=1.6, aniso=(10, 1)) * 8)
    row = (Y % 11)
    lath = np.where((row > 8)[..., None], G.fill((26, 22, 20), (S, S)), lath)
    lath = G.add_lum(lath, 10 * G.smoothstep(1.5, 0, row) - 12 * G.smoothstep(6.5, 8.5, row) * (row <= 8))
    keys = (row > 8) & (G.fnoise(rng, beta=1.2, kmin=4) > 0.4)
    lath = np.where(keys[..., None], G.fill((150, 146, 136), (S, S)), lath)
    img = G.over(img, lath, m)
    rim = np.clip(G.blur(m, 1.2) - m, 0, 1) * 3
    img = G.add_lum(img, 18 * np.roll(rim, -1, 0) - 22 * np.roll(rim, 1, 0))
    return img, m, cx, cy


def _plaster_marks(i, rng, img):
    if i == 1:                                     # a chunk fallen out, cracks running from it
        img, _, _, _ = _lath_hole(img, rng)
        img = G.mark_crack(img, rng, 28, (30, 50), 2)
        return G.mark_crack(img, rng, 22, (20, 40), 1)
    img = G.mark_stain(img, rng, (148, 116, 70), (24, 30), 0.3, (0.0, 0.6))
    return G.mark_damp(img, rng, (20, 16), 0.16, (1.0, 1.0, 1.1))


def tex_l39_houses_panelling() -> None:
    """Beadboard, wainscot and plaster (each with two worn versions)."""
    _versions("l39_beadboard", beadboard(), _beadboard_marks, 39623)
    _versions("l39_wainscot", wainscot(), _wainscot_marks, 39626)
    _versions("l39_plaster", plaster(), _plaster_marks, 39629)


# =============================================================================
# Floors and the popcorn ceiling
# =============================================================================

def _boards(seed, col, wear, nails=True):
    """Floorboards along x, 4 boards per block (32 px): long planks (one end joint per row, a
    short plank with a second joint in some rows, staggered), per-plank tone and cathedral grain,
    face nails at the joints, dark gaps, varnish worn by traffic."""
    rng = G.rng_for(seed)
    row = np.floor(Y / 32).astype(int) % 4
    yb = Y - np.floor(Y / 32) * 32
    o1 = rng.uniform(0, S, 4)
    two = rng.random(4) < 0.4
    o2 = (o1 + rng.uniform(36, 60, 4)) % S
    span = np.where(two, (o2 - o1) % S, S)
    pl = (((X - o1[row]) % S) < span[row]).astype(int)
    pid = row * 2 + pl
    tone = rng.normal(0, 1, 8)[pid]
    phase = rng.uniform(0, S, 8)[pid]
    mid = rng.uniform(10, 22, 8)[pid]
    img = G.fill(col, (S, S)) + (tone * 6)[..., None] * np.array([1.0, 0.82, 0.62])
    img = G.add_lum(img, G.fnoise(rng, beta=1.8, aniso=(14, 1)) * 7 + G.fnoise(rng, beta=0.9, aniso=(16, 1), kmin=8) * 4,
                    (1.0, 0.8, 0.6))
    warp = G.fnoise(rng, beta=3.0, aniso=(5, 1)) * 2.5
    h = np.abs(yb - mid + warp) * 0.9 + 7 * np.cos(2 * np.pi * (X - phase) / S)
    fr = (h / 3.2) % 1
    img = G.add_lum(img, -16 * G.smoothstep(0.55, 0.92, fr) * G.smoothstep(1.0, 0.92, fr), (1.0, 0.85, 0.7))
    # Gaps, lit board edges, end joints.
    e = np.minimum(yb, 32 - yb)
    img = G.add_lum(img, -46 * G.smoothstep(1.2, 0.3, e) + 7 * G.smoothstep(1.0, 1.6, e) * G.smoothstep(3.0, 1.6, e) * (yb < 16))
    dj = np.minimum(np.abs(G.wrapd(X - o1[row], S)), np.where(two[row], np.abs(G.wrapd(X - o2[row], S)), S))
    img = G.add_lum(img, -38 * G.smoothstep(1.0, 0.3, dj))
    if nails:
        pts = []
        for r in range(4):
            for o in ((o1[r], o2[r]) if two[r] else (o1[r],)):
                for side in (-3.5, 3.5):
                    pts += [(o + side, 32 * r + 7), (o + side, 32 * r + 25)]
        nail = _dots(pts, 0.75)
        img = G.over(img, np.array([44, 34, 26]), nail * 0.85)
        img = G.add_lum(img, 6 * np.clip(G.blur(nail, 0.8) - nail, 0, 1) * 3)
    # Varnish: sheen along the boards, worn dull and pale where people walk, scratches.
    worn = G.smoothstep(0.2, 1.6, G.fnoise(rng, beta=2.4, aniso=(2, 1))) * wear
    img = G.add_lum(img, 9 * G.smoothstep(0.8, 2.0, G.fnoise(rng, beta=2.0, aniso=(4, 1))) * (1 - worn), (1.0, 0.92, 0.8))
    pale = L39._lum(img)[..., None] * np.array([1.0, 0.86, 0.68]) * 0.5 + img * 0.5 + 18
    img = G.lerp(img, pale, worn[..., None] * 0.55)
    scr = P._checks(rng, 16, False, (6, 26))
    img = G.add_lum(img, 12 * scr * (0.3 + worn))
    img = G.add_lum(img, -10 * G.smoothstep(2.0, 0.6, e) * worn)            # dirt packed in the gaps
    return G.add_lum(img, rng.normal(0, 1.4, (S, S)))


def floorboards():
    return _boards(39630, (170, 120, 70), 0.7)


def floorboards_dark():
    return _boards(39631, (82, 54, 34), 1.2)


def _floor_marks(i, rng, img):
    if i == 1:                                     # a worn traffic patch (bare grey wood, scratched)
        m, _, _ = L39._lmask(rng, (30, 22), rough=0.5)
        pale = L39._lum(img)[..., None] * np.array([0.98, 0.9, 0.8]) * 0.6 + img * 0.4 + 14
        img = G.lerp(img, pale, m[..., None] * 0.7)
        return G.add_lum(img, 14 * P._checks(rng, 14, False, (6, 20)) * m)
    img = G.mark_stain(img, rng, (60, 40, 26), (18, 14), 0.4)                 # a dark ring from a pot plant, a spill
    return G.mark_damp(img, rng, (18, 14), 0.25, (0.9, 1.0, 1.2))


CREAM = np.array([214, 200, 160])
OXBLOOD = np.array([112, 40, 36])


def linoleum():
    """Worn cream and oxblood checkerboard linoleum (8 model px squares), marbled colour, waxed
    sheen, scuffed traffic paths, black heel marks, dirt in the tile seams."""
    rng = G.rng_for(39632)
    chk = ((np.floor(X / 64) + np.floor(Y / 64)) % 2).astype(bool)
    base = np.where(chk[..., None], G.fill(OXBLOOD, (S, S)), G.fill(CREAM, (S, S)))
    marble = G.fnoise(rng, beta=1.6, aniso=(3, 1))
    veins = np.exp(-(marble / 0.18) ** 2)
    img = G.add_lum(base, G.fnoise(rng, beta=2.4) * 5 + G.fnoise(rng, beta=1.2, kmin=6) * 2)
    img = G.add_lum(img, np.where(chk, 14, -12) * veins * 0.8)
    s = np.minimum(np.abs(G.wrapd(X, 64)), np.abs(G.wrapd(Y, 64)))
    img = G.add_lum(img, -20 * G.smoothstep(1.0, 0.2, s))
    img = G.add_lum(img, 10 * G.smoothstep(0.6, 2.2, G.fnoise(rng, beta=2.6)), (1.0, 0.97, 0.9))   # wax
    worn = G.smoothstep(0.1, 1.5, G.fnoise(rng, beta=2.6, aniso=(1, 3)))
    img = G.over(img, np.array([150, 128, 100]), worn * 0.32)
    img = G.add_lum(img, 12 * P._checks(rng, 18, False, (6, 20)) * (0.2 + worn) - 18 * G.smoothstep(2.2, 0.4, s) * worn)
    prims = []
    for _ in range(12):
        x, y = rng.uniform(0, S, 2)
        a = rng.uniform(0, np.pi)
        pts = [(x + 4 * np.cos(a + t) * k, y + 2 * np.sin(a + t) * k) for k, t in ((1, -0.5), (1.05, 0), (1, 0.6))]
        prims.append(("line", pts, rng.uniform(0.5, 1.0), _c((24, 20, 18))))
    rgb, cov = _print(prims)
    img = G.over(img, rgb, cov * 0.5)
    return G.add_lum(img, rng.normal(0, 1.4, (S, S)))


def _lino_marks(i, rng, img):
    if i == 1:                                     # lino torn away: black backing and the boards under it
        m, _, _ = L39._lmask(rng, (18, 12), rough=0.4, lo=0.4, hi=0.48)
        m = G.smoothstep(0.35, 0.65, G.blur(m, 1.2))
        under = _boards(39633, (150, 120, 88), 1.0, nails=False)
        backing = G.add_lum(G.fill((40, 34, 30), (S, S)), G.fnoise(rng, beta=1.4) * 5)
        mb = G.smoothstep(-0.5, 1.0, G.fnoise(rng, beta=2.6, kmin=2))
        img = G.over(img, G.lerp(under, backing, mb[..., None] * 0.8), m)
        rim = np.clip(G.blur(m, 1.0) - m, 0, 1) * 3
        return G.add_lum(img, 26 * rim)                                     # the torn, curled edge catches light
    img = G.mark_stain(img, rng, (70, 50, 34), (14, 12), 0.45)              # a scorch/spill and a crack
    return G.mark_crack(img, rng, 26, (24, 40), 1)


def _hex_tiles(cols=12, rows=14):
    """Squashed hex lattice (cols x rows per block, odd rows shifted half a tile): per pixel the
    tile (i, j) and the distance to its border in px. Seamless."""
    dx, dy = S / cols, S / rows
    j0 = np.floor(Y / dy).astype(int)
    cands = []
    for dj in (-1, 0, 1, 2):
        j = j0 + dj
        sh = (j % 2) * dx / 2
        ir = np.round((X - sh) / dx).astype(int)
        for di in (-1, 0, 1):
            i = ir + di
            cx, cy = i * dx + sh, j * dy
            cands.append((i % cols, j % rows, cx, cy))
    ds = [np.hypot(X - cx, Y - cy) for _, _, cx, cy in cands]
    order = np.argsort(np.stack(ds), axis=0)
    D = np.stack(ds)
    CX = np.stack([c[2] for c in cands])
    CY = np.stack([c[3] for c in cands])
    I = np.stack([c[0] for c in cands])
    J = np.stack([c[1] for c in cands])
    k1, k2 = order[0], order[1]
    take = lambda A, k: np.take_along_axis(A, k[None], 0)[0]
    d1, d2 = take(D, k1), take(D, k2)
    L = np.hypot(take(CX, k1) - take(CX, k2), take(CY, k1) - take(CY, k2))
    edge = (d2 ** 2 - d1 ** 2) / (2 * np.maximum(L, 1e-3))
    bi, bj = take(I, k1), take(J, k1)
    return bi, bj, edge, (take(CX, k1), take(CY, k1))


ROSETTES = ((2, 3), (8, 10))


def _rosette_tiles(cols=12, rows=14):
    out = set()
    for i, j in ROSETTES:
        out.add((i, j))
        out.update({((i - 1) % cols, j), ((i + 1) % cols, j)})
        for jj in (j - 1, j + 1):
            a, b = (i - 1, i) if j % 2 == 0 else (i, i + 1)
            out.update({(a % cols, jj % rows), (b % cols, jj % rows)})
    return out


def _bath_tile_parts():
    rng = G.rng_for(39634)
    I, J, edge, (cx, cy) = _hex_tiles()
    tid = J * 12 + I
    tone = rng.normal(0, 1, 12 * 14)[tid]
    black = np.zeros((S, S), bool)
    for i, j in _rosette_tiles():
        black |= (I == i) & (J == j)
    img = np.where(black[..., None], G.fill((26, 26, 28), (S, S)), G.fill((230, 228, 220), (S, S)))
    img = img + (tone * 3.0)[..., None]
    glaze = np.exp(-(((X - cx + 1.5) ** 2 + (Y - cy + 1.5) ** 2) / 6.0))
    img = G.add_lum(img, 12 * glaze + G.fnoise(rng, beta=2.4) * 2)
    tile = G.smoothstep(0.35, 1.1, edge)
    img = G.add_lum(img, -10 * G.smoothstep(1.8, 0.9, edge) * tile)       # rounded tile edge
    grout = G.add_lum(G.fill((150, 148, 140), (S, S)), G.fnoise(rng, beta=1.8) * 8 + rng.normal(0, 3, (S, S)))
    dirt = G.smoothstep(0.0, 1.6, G.fnoise(rng, beta=2.4))
    grout = G.over(grout, np.array([96, 92, 80]), dirt * 0.55)
    img = G.over(img, grout, 1 - tile)
    return G.add_lum(img, rng.normal(0, 1.0, (S, S))), I, J, tile


def bath_tile():
    """Small white hexagon mosaic with grey grout and two black flower rosettes per block."""
    return _bath_tile_parts()[0]


def _bath_marks(i, rng, img):
    _, I, J, tile = _bath_tile_parts()
    if i == 1:                                     # a few tiles popped out: grey mortar bed with the tile marks
        cx, cy = G._spot(rng, 16, 16)
        miss = np.zeros((S, S))
        dx, dy = S / 12, S / 14
        for _ in range(5):
            x, y = cx + rng.normal(0, 7), cy + rng.normal(0, 7)
            jj = int(round(y / dy)) % 14
            ii = int(round((x - (jj % 2) * dx / 2) / dx)) % 12
            miss = np.maximum(miss, ((I == ii) & (J == jj)) * 1.0)
        miss = G.blur(miss, 0.4) * G._edge_fade()
        bed = G.add_lum(G.fill((112, 108, 100), (S, S)), G.fnoise(rng, beta=1.4, kmin=6) * 8 + rng.normal(0, 4, (S, S)))
        bed = G.add_lum(bed, -16 * (np.cos(2 * np.pi * X / 4) > 0.6))     # comb marks of the trowel
        img = G.over(img, bed, miss)
        return G.add_lum(img, -20 * np.clip(G.blur(miss, 0.8) - miss, 0, 1) * 3)
    img = G.mark_crack(img, rng, 34, (30, 55), 2)                           # cracked tiles, black grout grime
    m, _, _ = L39._lmask(rng, (24, 20))
    return G.over(img, np.array([60, 58, 50]), (1 - tile) * m * 0.6)


def popcorn():
    """The reference photo's ceiling: off-white sprayed popcorn texture. Bumps of three sizes
    clumped together, soft shading lit from the room, little shadows in the hollows."""
    rng = G.rng_for(39635)
    h = np.zeros((S, S))
    for dens, sig, amp in ((0.12, 0.55, 1.0), (0.035, 0.95, 2.6), (0.01, 1.5, 5.0)):
        imp = (rng.random((S, S)) < dens) * rng.uniform(0.5, 1.0, (S, S))
        h += G.blur(imp, sig) * amp
    clump = G.smoothstep(-1.2, 1.4, G.fnoise(rng, beta=2.0, kmin=3))
    h = h * (0.55 + 0.7 * clump)
    h = (h - h.mean()) / h.std()
    img = G.fill((224, 221, 212), (S, S))
    img = G.add_lum(img, L39._relief(h, 16.0))
    cav = np.clip(G.blur(h, 1.4) - h, 0, None)
    img = G.add_lum(img, -10 * cav + 3.0 * np.clip(h, 0, None))
    img = G.add_lum(img, -14 * (h < np.percentile(h, 3)) * G.blur(rng.random((S, S)) < 0.5, 0.3))
    img = G.add_lum(img, G.fnoise(rng, beta=3.0) * 2.5, (1.0, 0.99, 0.96))
    return img


def _popcorn_marks(i, rng, img):
    if i == 1:                                     # a leak from above: a brown ring with nested tide lines
        img = G.mark_stain(img, rng, (150, 112, 66), (26, 24), 0.32)
        m, cx, cy = L39._lmask(rng, (16, 14), rough=0.35, lo=0.3, hi=0.5)
        rings = np.clip(np.abs(np.sin(np.pi * m * 3.0)) ** 8, 0, 1) * (m > 0.05)
        return G.over(img, np.array([132, 96, 54]), rings * 0.25 + m * 0.12)
    # A hairline crack along a sagging board joint: the ceiling bulges a little either side of it.
    y0 = rng.uniform(46, 82)
    wob = G.fnoise(rng, (1, S), beta=2.2)[0] * 2.0
    yc = y0 + wob[np.clip(X.astype(int), 0, S - 1)]
    fade = G._edge_fade(20)
    sag = np.exp(-((Y - yc) / 14) ** 2)
    img = G.add_lum(img, (10 * np.tanh((yc - Y) / 6) * sag - 6 * sag) * fade)
    crack = G.smoothstep(0.9, 0.2, np.abs(Y - yc)) * fade
    img = G.add_lum(img, -42 * crack - 8 * G.blur(crack, 1.4))
    flakes = (G.fnoise(rng, beta=1.0, kmin=8) > 1.4) * G.smoothstep(5, 1, np.abs(Y - yc)) * fade
    return G.over(img, np.array([196, 192, 182]), flakes * 0.7)


def _floor_side(seed, kind):
    """The side of a floor block = a section through the floor: the finish on top, pine
    subfloor, the dark joist space with joist ends, lath, and the plaster ceiling below it with
    its popcorn at the very bottom. Tiles in x; the floor blocks show it where houses are cut."""
    rng = G.rng_for(seed)
    img = G.add_lum(G.fill((30, 26, 22), (S, S)), G.fnoise(rng, beta=1.6) * 4)
    # finish band
    if kind in ("floorboards", "floorboards_dark"):
        col = (170, 120, 70) if kind == "floorboards" else (82, 54, 34)
        band = G.add_lum(G.fill(col, (S, S)), G.fnoise(rng, beta=1.8, aniso=(14, 1)) * 6, (1, 0.8, 0.6))
        joints = rng.uniform(0, S, 2)
        for o in joints:
            band = G.add_lum(band, -36 * G.smoothstep(1.0, 0.3, np.abs(G.wrapd(X - o, S))))
        top, h = band, 22
    elif kind == "linoleum":
        lino = np.where(((np.floor(X / 64)) % 2 == 0)[..., None], G.fill(CREAM, (S, S)), G.fill(OXBLOOD, (S, S)))
        pine = G.add_lum(G.fill((150, 120, 84), (S, S)), G.fnoise(rng, beta=1.8, aniso=(14, 1)) * 6)
        top = np.where((Y < 5)[..., None], lino, pine)
        top = G.add_lum(top, -24 * G.smoothstep(1.0, 0, np.abs(Y - 5)))
        h = 22
    else:
        tile = G.add_lum(G.fill((228, 226, 218), (S, S)), G.fnoise(rng, beta=2.0) * 3)
        tile = G.add_lum(tile, -60 * G.smoothstep(1.0, 0.3, np.abs(G.wrapd(X, S / 12))))
        bed = G.add_lum(G.fill((120, 116, 108), (S, S)), rng.normal(0, 6, (S, S)))
        top = np.where((Y < 7)[..., None], tile, bed)
        h = 22
    img = np.where((Y < h)[..., None], top, img)
    img = G.add_lum(img, 10 * G.smoothstep(1.5, 0, Y) - 20 * G.smoothstep(1.0, 0, np.abs(Y - h)))
    sub = G.add_lum(G.fill((128, 104, 74), (S, S)), G.fnoise(rng, beta=1.8, aniso=(10, 1)) * 8)
    img = np.where(((Y >= h) & (Y < h + 10))[..., None], sub, img)
    img = G.add_lum(img, -26 * G.smoothstep(1.0, 0, np.abs(Y - h - 10)))
    # joist ends (2 per block), lath, plaster
    for x0 in (14, 78):
        rings = 4 * np.sin(np.hypot(X - x0 - 30, Y - 120) * 1.3)      # end-grain growth rings
        joist = G.add_lum(G.fill((140, 110, 76), (S, S)), rings + rng.normal(0, 3, (S, S)))
        m = G.box((S, S), x0, h + 10, x0 + 16, 108)
        img = G.over(img, joist, m)
        img = G.add_lum(img, -14 * (G.box((S, S), x0 - 1, h + 10, x0 + 17, 108) - m))
    lath = G.add_lum(G.fill((126, 100, 72), (S, S)), G.fnoise(rng, beta=1.6, aniso=(10, 1)) * 7)
    img = np.where(((Y >= 108) & (Y < 114))[..., None], lath, img)
    pl = G.add_lum(G.fill((206, 202, 192), (S, S)), G.fnoise(rng, beta=2.2) * 4 + rng.normal(0, 2, (S, S)))
    bump = (rng.random((S, S)) < 0.25) * rng.uniform(0, 1, (S, S))
    pl = G.add_lum(pl, (G.blur(bump, 0.6) * 20 - 6) * (Y > 124))
    img = np.where((Y >= 114)[..., None], pl, img)
    img = G.add_lum(img, -18 * G.smoothstep(1.0, 0, np.abs(Y - 114)) - 10 * G.smoothstep(1.0, 0, np.abs(Y - 108)))
    return G.add_lum(img, rng.normal(0, 1.2, (S, S)))


FLOORS = {"l39_floorboards": (floorboards, _floor_marks, 39640),
          "l39_floorboards_dark": (floorboards_dark, _floor_marks, 39643),
          "l39_linoleum": (linoleum, _lino_marks, 39646),
          "l39_bath_tile": (bath_tile, _bath_marks, 39649)}


def tex_l39_houses_floors() -> None:
    """The four floors (each with two worn versions) and their side (section) textures."""
    for k, (name, (fn, marks, seed)) in enumerate(FLOORS.items()):
        _versions(name, fn(), marks, seed)
        _save(_floor_side(39680 + k, name[4:]), f"{name}_side")


def tex_l39_houses_ceiling() -> None:
    """The popcorn ceiling (water-stain and crack versions)."""
    _versions("l39_popcorn_ceiling", popcorn(), _popcorn_marks, 39636)


# =============================================================================
# The knock wall
# =============================================================================

BARE = np.array([112, 106, 96])


def _split(img, rng, x0, x1, yc, wmax, cavity=True):
    """A split running along a board's grain, x0..x1 at height yc: a wedge widest in the middle,
    raw grey wood in it with lighter splintered lips and, where it's widest, black wall cavity."""
    t = np.clip((X - x0) / (x1 - x0), 0, 1)
    inside = (X > x0) & (X < x1)
    w = wmax * np.sin(np.pi * t) ** 0.8 * inside + 0.3 * inside
    wob = 0.6 * G.fnoise(rng, (1, S), beta=2.0)[0][np.clip(X.astype(int), 0, S - 1)]
    d = np.abs(Y - yc - wob)
    gap = G.smoothstep(w / 2 + 0.6, w / 2 - 0.2, d) * inside
    wood = G.add_lum(G.fill(BARE, (S, S)), G.fnoise(rng, beta=1.4, aniso=(10, 1)) * 10)
    img = G.over(img, wood, gap)
    if cavity:
        core = G.smoothstep(w / 2 - 0.6, w / 2 - 1.4, d) * inside * (w > 2.0)
        img = G.over(img, np.array([16, 14, 12]), core)
    lip = G.smoothstep(w / 2 + 1.6, w / 2 + 0.6, d) * (1 - gap) * inside
    return G.add_lum(img, 22 * lip * (Y < yc) - 20 * lip * (Y > yc))


def _knock_outside(shade_name, seed):
    """Cracked, split clapboard: splits along the grain, one board sagging out of line, a jagged
    fracture across several boards, popped nails and their rusty holes, paint flaking off."""
    rng = G.rng_for(seed)
    img = _load(shade_name)
    # Sagging board (row 4, y 64-80): the middle of it dropped and pushed out; the gap above it
    # shows the dark wall cavity and the raw edge of the board above.
    x0, x1 = 22.0, 104.0
    t = np.clip((X - x0) / (x1 - x0), 0, 1)
    sag = 4.5 * np.sin(np.pi * t) * ((X > x0) & (X < x1))
    src = np.clip((Y - sag).astype(int), 0, S - 1)
    board = (Y >= 64) & (Y < 80 + sag)
    shifted = img[src, np.clip(X.astype(int), 0, S - 1)]
    img = np.where(board[..., None], shifted, img)
    gap = (Y >= 64) & (Y < 64 + sag)
    img = np.where(gap[..., None], G.add_lum(G.fill((18, 16, 14), (S, S)), rng.normal(0, 3, (S, S))), img)
    img = G.add_lum(img, 18 * ((Y >= 64 + sag) & (Y < 65.5 + sag)) * (sag > 0.5))
    # Splits along the grain.
    for x0_, x1_, yc, w in ((6, 58, 37, 2.6), (60, 120, 101, 3.2), (34, 90, 21, 1.6), (70, 112, 54, 2.2)):
        img = _split(img, rng, x0_, x1_, yc, w)
    # A rotten corner broken clean out: jagged raw edges, the dark diagonal sheathing behind.
    poly = [(14, 84), (24, 81), (31, 86), (40, 82), (49, 90), (46, 99), (52, 108), (43, 116), (33, 112),
            (24, 118), (17, 108), (12, 98)]
    jag = [(x + rng.uniform(-1.5, 1.5), y + rng.uniform(-1.0, 1.0)) for x, y in poly]
    hole = G.pil_mask(lambda d, k: d.polygon([(x * k, y * k) for x, y in jag], fill=255))
    sheath = G.add_lum(G.fill((70, 62, 52), (S, S)), G.fnoise(rng, beta=1.5, aniso=(4, 4), kmin=3) * 5
                       - 26 * G.smoothstep(1.2, 0.3, np.abs(G.wrapd(X + Y, 14))))
    sheath = G.add_lum(sheath, -18 * G.smoothstep(6, 0, G.blur(hole, 2.5) * 10 - 4))    # deep shadow under the rim
    img = G.over(img, sheath, hole)
    rim = np.clip(G.blur(hole, 1.2) - hole, 0, 1) * 3 * (G.fnoise(rng, beta=0.8, kmin=10) > -0.6)
    img = G.over(img, BARE * 1.15, np.clip(rim, 0, 1))
    # A fracture zigzagging down across four boards.
    pts = [(96, 6)]
    for k in range(10):
        pts.append((pts[-1][0] - rng.uniform(2, 7), pts[-1][1] + rng.uniform(5, 9)))
    m = G.pil_mask(lambda d, k: d.line([(x * k, y * k) for x, y in pts], fill=255, width=int(2.2 * k)))
    inner = G.pil_mask(lambda d, k: d.line([(x * k, y * k) for x, y in pts], fill=255, width=k))
    img = G.over(img, BARE, m)
    img = G.over(img, np.array([14, 12, 10]), inner * 0.9)
    img = G.add_lum(img, 16 * np.clip(np.roll(m, -1, 1) - m, 0, 1) - 14 * np.clip(np.roll(m, 1, 1) - m, 0, 1))
    # Nails: some still there (rusty, a run below), some popped (dark holes).
    for nx, ny in ((10, 9), (10, 41), (10, 105), (118, 25), (118, 89), (64, 9), (64, 121)):
        img = P._nail(img, nx, ny, rng, 1.4, 0.8)
    holes = _dots([(36, 89), (90, 57), (52, 9), (106, 41)], 0.9)
    img = G.over(img, np.array([20, 16, 14]), holes)
    # Paint gone in flakes to grey wood, more near the damage.
    near = G.blur(np.clip(m + gap, 0, 1), 6) * 4
    flakes = G.smoothstep(1.5, 2.1, G.fnoise(rng, beta=1.3, kmin=4) + near)
    img = G.over(img, BARE * 1.05, flakes * 0.85)
    return G.add_lum(img, -8 * np.clip(G.blur(flakes, 1.0) - flakes, 0, 1) * 3)


def _knock_inside():
    """The room side: cracked plaster, a big piece fallen out showing the lath, the wallpaper
    torn off in ragged strips, its torn edge lifted."""
    rng = G.rng_for(39660)
    img = _plaster_base(39661, (184, 178, 166))
    paper = paper_stripe()
    rag = G.blur(G.fnoise(rng, beta=2.6, kmin=2), 2.0)                     # big tears
    rag = rag / rag.std() + 0.15 * G.fnoise(rng, beta=2.0, kmin=6)         # with ragged edges
    torn = G.smoothstep(0.15, 0.3, rag + 0.9 * G.smoothstep(40, 10, Y) + 0.6 * G.smoothstep(90, 125, X)
                        - 1.1 * np.exp(-(((X - 60) / 34) ** 2 + ((Y - 70) / 30) ** 2)))
    img = G.over(img, paper, torn)
    edge = np.clip(G.blur(torn, 0.8) - torn, 0, 1) * 3
    img = G.add_lum(img, 30 * edge - 12 * np.clip(G.blur(torn, 1.6) - torn, 0, 1) * 2 * (1 - edge))
    img, hole, cx, cy = _lath_hole(img, G.rng_for(39662), (24, 18), (0.35, 0.65))
    ang = np.linspace(0, 2 * np.pi, 7)[:-1] + rng.uniform(0, 1)
    lines = [P._wander(rng, cx + 18 * np.cos(a), cy + 14 * np.sin(a), a, 3.0, int(rng.integers(6, 12)), 0.4) for a in ang]
    cr = G.pil_mask(lambda d, k: [d.line([(x * k, y * k) for x, y in ln], fill=255, width=k) for ln in lines])
    img = G.add_lum(img, -30 * cr - 8 * G.blur(cr, 1.2))
    return img


def tex_l39_houses_knock_wall() -> None:
    """Outside faces of the knock wall for each paint shade (from L39Core's clapboard), and the
    room side."""
    for k, src in enumerate(("l39_clapboard", "l39_clapboard_faded", "l39_clapboard_weathered")):
        _save(_knock_outside(src, 39663 + k), f"l39_knock_wall_{k}")
    _save(_knock_inside(), "l39_knock_wall_inside")


# =============================================================================
# Sash window with the shade down
# =============================================================================

def _shade_cloth(rng, col=(206, 194, 160)):
    """Cream roller-shade cloth: fine weave, faint horizontal roll banding, soft vertical folds,
    yellowed and fly-specked."""
    img = G.add_lum(G.fill(col, (S, S)), G.fnoise(rng, beta=1.2, aniso=(1, 6), kmin=3) * 4 + np.sin(Y * 1.6) * 1.2
                    + np.sin(X * 2.1) * 0.8)
    img = G.add_lum(img, -10 * np.exp(-((X - 36) / 6) ** 2) - 8 * np.exp(-((X - 94) / 7) ** 2))
    img = G.over(img, np.array([186, 160, 106]), G.smoothstep(0.0, 1.8, G.fnoise(rng, beta=2.2)) * 0.3)
    return G.add_lum(img, -10 * (rng.random((S, S)) < 0.004))


def tex_l39_houses_sash_shade() -> None:
    """l39_sash_window with a cream roller shade pulled all the way down behind the glass:
    outside it shows dimly through the dark old glass; inside it hangs in front of the sashes
    (roller at the top, hem bar and pull ring at the bottom)."""
    panes = P._pane_rects(P.UPPER, 6, 5) + P._pane_rects(P.LOWER, 5, 9)
    pm = np.zeros((S, S))
    for x0, y0, x1, y1 in panes:
        pm = np.maximum(pm, G.box((S, S), x0, y0, x1, y1))
    rng = G.rng_for(39690)
    out = P._sash_window(39130, False, False)                 # the same frame and cracked pane as l39_sash_window
    cloth = _shade_cloth(rng) * 0.8                            # seen at night through the glass: a little dim
    cloth = G.add_lum(cloth, -14 * G.smoothstep(40, 0, Y))     # the roller's shadow at the top
    out = G.over(out, out * 0.18 + cloth * 0.82, pm)
    _save(out, "l39_sash_window_shade")

    rng = G.rng_for(39691)
    ins = P._sash_window(39131, False, True)
    cloth = _shade_cloth(rng, (212, 200, 166))
    m = G.box((S, S), 14, 13, 114, 112)
    cloth = G.add_lum(cloth, 6 * G.smoothstep(100, 60, Y))      # lit a little more where it hangs free
    ins = G.add_lum(ins, -18 * (G.box((S, S), 13, 12, 115, 114) - m))
    ins = G.over(ins, cloth, m)
    roller = G.box((S, S), 12, 9, 116, 16)
    rl = G.add_lum(G.fill((200, 190, 160), (S, S)), _profile([9, 10.5, 13, 16], [12, 16, -4, -20]))
    ins = G.over(ins, rl, roller)
    for bx in (12, 113):
        ins = G.over(ins, np.array([150, 138, 112]), G.box((S, S), bx, 8, bx + 3, 17))
    hem = G.box((S, S), 15, 106, 113, 111)
    hb = G.add_lum(G.fill((150, 118, 74), (S, S)), _profile([106, 107.5, 109, 111], [10, 14, -6, -16]))
    ins = G.over(ins, hb, hem)
    ins = G.add_lum(ins, -14 * G.box((S, S), 15, 111, 113, 113))
    ring = np.abs(np.hypot(X - 64, Y - 115.5) - 2.6) < 0.8
    ins = G.over(ins, np.array([120, 96, 56]), ring * 0.95)
    ins = G.over(ins, np.array([180, 170, 140]), G.box((S, S), 63.4, 110.5, 64.6, 113))
    _save(ins, "l39_sash_window_shade_inside")


# =============================================================================
# Bare bulb
# =============================================================================
# One atlas per state (texture px; model units = 8 px):
#   socket side band u 0-5 v 0-1   socket underside ring u 0-5 v 1-6   screw base u 6-9 v 0-2
#   glass sides u 0-5 v 7-11       glass bottom u 6-11 v 7-12         filament u 11-16 v 0-5

def _bulb_atlas(lit: bool):
    rng = G.rng_for(39692 + lit)
    img = np.zeros((S, S, 4))
    # Socket: a small dark metal cup sunk flush in the plaster (reference photo): blackened steel
    # with a dull rim highlight, no brass, no wood-like bands.
    band = G.add_lum(G.fill((44, 42, 40), (8, 40)), rng.normal(0, 2, (8, 40)))
    band[0, :] += 14
    band[6:, :] -= 10
    img[0:8, 0:40, :3] = band
    sx, sy = G.grid((40, 40))
    r = np.hypot(sx - 20, sy - 20)
    ring = G.add_lum(G.fill((50, 48, 46), (40, 40)), 16 * np.exp(-((r - 16) / 1.6) ** 2) + rng.normal(0, 2, (40, 40)))
    ring = G.over(ring, np.array([18, 17, 16]), G.smoothstep(12.0, 11.0, r))
    img[8:48, 0:40, :3] = ring
    # Neck: the last of the screw base showing below the socket, dark grey metal with fine threads.
    bx, by = G.grid((16, 24))
    th = np.sin(2 * np.pi * (by + bx * 0.25) / 4.0)
    base = G.add_lum(G.fill((78, 76, 72), (16, 24)), 10 * th + rng.normal(0, 2, (16, 24)))
    img[0:16, 48:72, :3] = base
    img[0:48, 0:72, 3] = np.where(img[0:48, 0:72, :3].sum(-1) > 0, 255, 0)
    # Glass: old clear glass, a dust film, a vertical highlight; lit = warm and glowing.
    gx, gy = G.grid((40, 40))
    if lit:
        glass = G.fill((255, 226, 168), (40, 40))
        glass = G.add_lum(glass, 20 * np.exp(-((gx - 20) / 10) ** 2) - 30 * G.smoothstep(10, 0, np.minimum(gx, 40 - gx)), (1, 0.9, 0.7))
        a = 200 + 30 * np.exp(-((gx - 20) / 10) ** 2)
    else:
        glass = G.fill((196, 196, 188), (40, 40))
        glass = G.add_lum(glass, 50 * np.exp(-((gx - 12) / 2.5) ** 2) + G.fnoise(rng, (40, 40), beta=1.6) * 8)
        dust = G.smoothstep(0.0, 1.5, G.fnoise(rng, (40, 40), beta=2.0))
        glass = G.over(glass, np.array([150, 140, 120]), dust * 0.4)
        a = 96 + 40 * dust + 90 * np.exp(-((gx - 12) / 2.5) ** 2)
    img[56:96, 0:40, :3] = glass
    img[56:96, 0:40, 3] = np.clip(a, 0, 255)
    r = np.hypot(gx - 20, gy - 20)
    bot = glass.copy()
    bot = G.add_lum(bot, (30 if lit else 20) * np.exp(-(r / 8) ** 2))
    img[56:96, 48:88, :3] = bot
    img[56:96, 48:88, 3] = np.clip(a * (0.9 if lit else 1.0) + (20 if lit else 0) * np.exp(-(r / 8) ** 2), 0, 255)
    # Filament: two lead wires up to a little zigzag coil and a support hook.
    fil = Image.new("RGBA", (160, 160), (0, 0, 0, 0))
    d = ImageDraw.Draw(fil)
    wire = (90, 86, 80, 255) if not lit else (255, 214, 140, 255)
    coil = (40, 34, 30, 255) if not lit else (255, 250, 228, 255)
    d.line([(56, 0), (56, 70), (44, 104)], fill=wire, width=4)
    d.line([(104, 0), (104, 70), (116, 104)], fill=wire, width=4)
    d.line([(80, 0), (80, 120)], fill=wire, width=3)
    zz = [(44 + 8 * k, 104 + (10 if k % 2 else 0)) for k in range(10)]
    d.line(zz, fill=coil, width=5)
    fa = np.asarray(fil.resize((40, 40), Image.BOX), float)
    fa[..., 3] = np.where(fa[..., 3] > 90, 255, 0)
    img[0:40, 88:128] = fa
    return img


def tex_l39_houses_bulb() -> None:
    """The bare ceiling bulb: dead (grey dusty glass, dark filament) and lit (warm glowing glass,
    bright filament; the block renders it full-bright)."""
    G.save_png(_bulb_atlas(False), _blk("l39_bulb"))
    G.save_png(_bulb_atlas(True), _blk("l39_bulb_lit"))


# =============================================================================
# Interior door
# =============================================================================

def tex_l39_houses_door() -> None:
    """White four-panel interior door, 2 blocks tall: two tall panels above the lock rail and two
    below, slightly yellowed paint with brush marks, hairline cracks round the panels, finger
    grime round the brass knob, kick scuffs at the bottom."""
    rng = G.rng_for(39695)
    shp = (2 * S, S)
    Xd, Yd = G.grid(shp)
    col = np.array([224, 218, 196])
    img = G.add_lum(G.fill(col, shp), G.fnoise(rng, shp, beta=1.6, aniso=(1, 12), kmin=2) * 3
                    + G.fnoise(rng, shp, beta=0.8, aniso=(1, 12), kmin=8) * 1.4)
    panels = [(20, 20, 60, 116), (68, 20, 108, 116), (20, 142, 60, 236), (68, 142, 108, 236)]
    for x0, y0, x1, y1 in panels:
        outer, inner = G.box(shp, x0, y0, x1, y1), G.box(shp, x0 + 6, y0 + 6, x1 - 6, y1 - 6)
        bevel = outer - inner
        top = (Yd - y0) < np.minimum(np.minimum(Xd - x0, x1 - Xd), y1 - Yd)
        left = (Xd - x0) < np.minimum(np.minimum(Yd - y0, y1 - Yd), x1 - Xd)
        right = (x1 - Xd) < np.minimum(np.minimum(Yd - y0, y1 - Yd), Xd - x0)
        lum = np.where(top, 16, np.where(left, 8, np.where(right, -14, -20)))
        img = G.add_lum(img, bevel * lum + 4 * inner)
        img = G.add_lum(img, -22 * (G.box(shp, x0 - 1.5, y0 - 1.5, x1 + 1.5, y1 + 1.5) - outer))
        img = G.add_lum(img, -8 * (G.box(shp, x0 + 5, y0 + 5, x1 - 5, y1 - 5) - inner))
        img = G.over(img, np.array([170, 160, 136]), G.smoothstep(y1 - 9, y1 - 6, Yd) * bevel * 0.4)   # dust on the bottom bevel
    cr = P._checks(rng, 10, True, (20, 70), shape=shp)
    img = G.add_lum(img, -12 * cr)
    yel = G.smoothstep(-1.0, 1.6, G.fnoise(rng, shp, beta=2.6))
    img = G.over(img, np.array([204, 184, 128]), yel * 0.2)
    img = G._weather(img, rng, dirt=0.12, scratches=4)
    img = G.over(img, np.array([120, 110, 92]), G.smoothstep(0.9, 1.0, Yd / (2 * S))
                 * G.smoothstep(0.4, 1.4, G.fnoise(rng, shp, beta=1.4, aniso=(3, 1))) * 0.5)
    # Finger grime round the knob, then the brass rosette and knob, keyhole below.
    img = G.over(img, np.array([150, 136, 108]), np.exp(-(((Xd - 104) / 9) ** 2 + ((Yd - 130) / 12) ** 2)) * 0.35)
    ros = G.supersample(shp, lambda x, y: np.hypot(x - 104, y - 130) < 7.5)
    img = G.add_lum(img, -26 * np.clip(G.blur(ros, 1.0) - ros, 0, 1) * 2 * (Yd > 130))
    img = G.over(img, np.array([136, 106, 56]), ros)
    knob = G.supersample(shp, lambda x, y: np.hypot(x - 104, y - 130) < 5.0)
    brass = G.fill((176, 140, 72), shp) * (1.2 - 0.55 * G.smoothstep(0, 6, np.hypot(Xd - 102, Yd - 128)))[..., None]
    img = G.over(img, brass, knob)
    img = G.over(img, np.array([240, 220, 164]), G.supersample(shp, lambda x, y: np.hypot(x - 102.3, y - 128.2) < 1.4))
    img = G.over(img, np.array([16, 12, 10]), G.supersample(shp, lambda x, y: (np.hypot(x - 104, y - 143) < 1.5)
                                                            | ((np.abs(x - 104) < 0.7) & (y > 143) & (y < 147.5))))
    img = G.add_lum(img, -10 * (G.box(shp, 100.5, 139.5, 107.5, 150.5) - G.box(shp, 101.5, 140.5, 106.5, 149.5)))
    G.save_png(img[:S], _blk("l39_interior_door_top"))
    G.save_png(img[S:], _blk("l39_interior_door_bottom"))
    door = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((16, 32), Image.LANCZOS)
    icon = np.zeros((32, 32, 4))
    icon[:, 8:24, :3] = np.asarray(door, float)
    icon[:, 8:24, 3] = 255
    icon[:, 8, :3] *= 0.7
    icon[:, 23, :3] *= 0.7
    G.save_png(icon, _itm("l39_interior_door"))


# =============================================================================
# Banister, porch column
# =============================================================================

def tex_l39_houses_trim() -> None:
    """Spindle (white paint with the shadows of its turnings), dark stained handrail, and the
    white Craftsman porch column."""
    sp = P._white_paint(39696, (222, 218, 204))
    # Turnings: the bands where the spindle swells and narrows (block y -> texture row (16-y)*8).
    turn = _profile([0, 8, 10, 14, 18, 22, 24, 40, 56, 64, 72, 80, 96, 104, 108, 112, 120, 128],
                    [-14, -14, 10, -16, 10, -12, 4, 0, 10, -8, 10, 0, -4, 10, -14, 8, -12, -12])
    sp = G.add_lum(sp, turn)
    _save(sp, "l39_spindle")
    rng = G.rng_for(39697)
    rail = _oak(rng, np.array([66, 42, 26]), False)
    rail = G.add_lum(rail, 12 * G.smoothstep(0.8, 2.0, G.fnoise(rng, beta=2.0, aniso=(6, 1))), (1.0, 0.9, 0.75))
    rail = G.add_lum(rail, 14 * P._checks(rng, 10, False, (6, 24)))
    _save(rail, "l39_handrail")
    post = P._white_paint(39698, (216, 212, 198))
    post = G.over(post, np.array([118, 110, 94]), G.smoothstep(100, 128, Y)
                  * G.smoothstep(0.0, 1.4, G.fnoise(rng, beta=1.4, aniso=(3, 1))) * 0.4)
    _save(post, "l39_craftsman_post")


# =============================================================================
# Models, blockstates, item models
# =============================================================================

_el, _model, _bm, _bs, _im = P._el, P._model, P._bm, P._bs, P._im
FACES = P.FACES
FACING_Y = P.FACING_Y


def _surface_state(name, models):
    """VARIANT 0..3 x INVERTED: versions 1 and 2 use their worn model, 0 and 3 the plain one;
    inverted turns the model over (x 180), so floors show their popcorn underside on top."""
    variants = {}
    for inv in ("false", "true"):
        for i in range(4):
            v = {"model": _ref(models[i] if i < len(models) else models[0])}
            if inv == "true":
                v["x"] = 180
            variants[f"inverted={inv},variant={i}"] = v
    _bs(name, {"variants": variants})


def _surface(name, model_for):
    """Plain + _v1 + _v2 models (model_for(suffix) builds one), blockstate, item model."""
    names = [name, f"{name}_v1", f"{name}_v2"]
    for n, suffix in zip(names, ("", "_v1", "_v2")):
        _bm(n, model_for(suffix))
    _surface_state(name, names)
    _im(name)


INNERS = ("damask", "stripe", "floral", "rose", "lattice",
          "damask_base", "stripe_base", "floral_base", "rose_base", "lattice_base",
          "beadboard", "wainscot", "plaster")
CLAPBOARDS = ("l39_clapboard", "l39_clapboard_faded", "l39_clapboard_weathered")


def _inner_tex(inner):
    return f"l39_{inner}" if inner in ("beadboard", "wainscot", "plaster") else f"l39_paper_{inner}"


def _wall_cube(out_tex, in_tex):
    """Full cube, clapboard (#out) on the north face, the room finish (#in) on the other five."""
    faces = {f: "#in" for f in FACES}
    faces["north"] = "#out"
    e = _el((0, 0, 0), (16, 16, 16), faces, uv="full")
    return _model({"particle": _ref(in_tex), "out": _ref(out_tex), "in": _ref(in_tex)}, [e], render=None)


def _house_wall_models():
    variants = {}
    for k, inner in enumerate(INNERS):
        for s, clap in enumerate(CLAPBOARDS):
            m = f"l39_house_wall_{inner}_{s}"
            _bm(m, _wall_cube(clap, _inner_tex(inner)))
            for facing, y in FACING_Y.items():
                for inv in ("false", "true"):
                    yy = (y + 180) % 360 if inv == "true" else y
                    v = {"model": _ref(m)}
                    if inv == "true":
                        v["x"] = 180
                    if yy:
                        v["y"] = yy
                    variants[f"facing={facing},inner={k},inverted={inv},shade={s}"] = v
    _bs("l39_house_wall", {"variants": variants})
    _im("l39_house_wall", parent=_ref("l39_house_wall_damask_0"))


def _knock_wall_models():
    variants = {}
    for s in range(3):
        m = f"l39_knock_wall_{s}"
        out = f"l39_knock_wall_{s}"
        tex = {"particle": _ref(out), "out": _ref(out), "in": _ref("l39_knock_wall_inside")}
        faces = {f: "#in" for f in FACES}
        faces["north"] = "#out"
        e = [_el((0, 0, 0), (16, 16, 16), faces, uv="full")]
        # The broken end of the sagging board standing proud of the wall, and two splinters.
        e.append(_el((3, 6.6, -0.6), (8, 8, 0), "#out", faces=("north", "east", "west", "up", "down"),
                     uv={f: [3, 8, 8, 9.4] for f in FACES}, rot=((8, 7.3, 0), "z", -22.5)))
        e.append(_el((11.5, 11, -0.4), (12.25, 13.5, 0), "#out", faces=("north", "east", "west", "up", "down"),
                     uv={f: [4, 1, 4.75, 3.5] for f in FACES}, rot=((11.8, 11, 0), "x", 22.5)))
        e.append(_el((5, 2.2, -0.3), (9, 2.8, 0), "#out", faces=("north", "up", "down"),
                     uv={f: [7, 13.2, 11, 13.8] for f in FACES}, rot=((9, 2.5, 0), "z", 22.5)))
        _bm(m, _model(tex, e, render=None))
        for facing, y in FACING_Y.items():
            v = {"model": _ref(m)}
            if y:
                v["y"] = y
            variants[f"facing={facing},shade={s}"] = v
    _bs("l39_knock_wall", {"variants": variants})
    _im("l39_knock_wall", parent=_ref("l39_knock_wall_0"))


def _sash_shade_model():
    """The l39_sash_window model (see level39_props._house_models) with the shade textures."""
    name = "l39_sash_window_shade"
    tex = {"particle": _ref("l39_white_trim"), "trim": _ref("l39_white_trim"), "out": _ref(name),
           "in": _ref(f"{name}_inside")}
    body = {f: "#trim" for f in FACES}
    body["north"], body["south"] = "#out", "#in"
    e = [_el((0, 0, 1.5), (16, 16, 16), body, uv={"north": [0, 0, 16, 16], "south": [0, 0, 16, 16]})]
    e[0]["faces"]["north"].pop("cullface", None)
    e.append(_el((0, 1.5, 0), (1.5, 16, 1.5), "#trim"))
    e.append(_el((14.5, 1.5, 0), (16, 16, 1.5), "#trim"))
    e.append(_el((1.5, 14.5, 0), (14.5, 16, 1.5), "#trim", faces=("north", "up", "down")))
    e.append(_el((0, 0, -1.25), (16, 1.5, 1.5), "#trim", cull=False))
    e.append(_el((0.5, -1.0, -0.75), (15.5, 0, 1.0), "#trim", faces=("north", "east", "west", "down"), cull=False))
    _bm(name, _model(tex, e, render=None))
    P._facing_state(name)
    _im(name)


def _bulb_models():
    """A small bare bulb screwed upside down into the ceiling (the reference photo): a dark metal
    socket ring 6 px across and 1.5 px deep, flush with the plaster, a sliver of threaded neck, and
    a glass pear 4 px wide hanging 5.5 px below it with the filament inside (two crossed planes).
    Translucent glass."""
    side = ("north", "south", "east", "west")
    for lit in (False, True):
        name = "l39_ceiling_bulb" + ("_lit" if lit else "")
        atlas = "l39_bulb" + ("_lit" if lit else "")
        tex = {"particle": _ref("l39_bulb"), "b": _ref(atlas)}
        e = []
        # Socket ring, flush with the ceiling (its top face is hidden in the plaster).
        e.append(_el((5, 14.5, 5), (11, 16, 11), "#b", faces=side + ("down",),
                     uv={**{f: [0, 0, 5, 1] for f in side}, "down": [0, 1, 5, 6]}))
        # The threaded neck just below it.
        e.append(_el((6.5, 13.75, 6.5), (9.5, 14.5, 9.5), "#b", faces=side, uv={f: [6, 0, 9, 1] for f in side}))
        # The glass pear: narrow at the neck, widest a third of the way down, a rounded tip.
        glass = [((6.75, 12.75, 6.75), (9.25, 13.75, 9.25), [1.25, 7, 3.75, 8], [7.25, 8.25, 9.75, 10.75]),
                 ((6, 10.75, 6), (10, 12.75, 10), [0.5, 8, 4.5, 10], [6.5, 7.5, 10.5, 11.5]),
                 ((6.5, 9.25, 6.5), (9.5, 10.75, 9.5), [1, 10, 4, 11.5], [7, 8, 10, 11]),
                 ((7.25, 8.5, 7.25), (8.75, 9.25, 8.75), [1.75, 11.5, 3.25, 12], [7.75, 8.75, 9.25, 10.25])]
        for k, (f, t, suv, duv) in enumerate(glass):
            faces = side + (("down",) if k >= 1 else ())
            e.append(_el(f, t, "#b", faces=faces, uv={**{s: suv for s in side}, "down": duv}, shade=not lit))
        for axis_faces, f, t in ((("north", "south"), (7.1, 9.75, 8), (8.9, 13.0, 8)),
                                 (("east", "west"), (8, 9.75, 7.1), (8, 13.0, 8.9))):
            e.append(_el(f, t, "#b", faces=axis_faces, uv={a: [11, 0, 16, 5] for a in axis_faces},
                         rot=((8, 11.5, 8), "y", 45), shade=not lit))
        _bm(name, _model(tex, e, render="minecraft:translucent", ao=False,
                         display={"gui": {"rotation": [30, 225, 0], "translation": [0, -6, 0], "scale": [1.4, 1.4, 1.4]},
                                  "ground": {"rotation": [0, 0, 0], "translation": [0, -2, 0], "scale": [0.6, 0.6, 0.6]},
                                  "fixed": {"rotation": [0, 0, 0], "translation": [0, -4, 0], "scale": [1, 1, 1]}}))
    variants = {}
    for lit in ("false", "true"):
        for hanging in ("false", "true"):
            v = {"model": _ref("l39_ceiling_bulb" + ("_lit" if lit == "true" else ""))}
            if hanging == "false":
                v["x"] = 180
            variants[f"hanging={hanging},lit={lit}"] = v
    _bs("l39_ceiling_bulb", {"variants": variants})
    _im("l39_ceiling_bulb")


SPINDLE_W = 0.75     # half width of a spindle


def _spindle(cz, z_lo=0.0, z_hi=16.0):
    """One turned spindle at z = cz (x centred), from the bottom rail up to the handrail: a thin
    square shaft with a swelling vase and two beads. Clipped to z_lo..z_hi (the half spindles at
    the block edges)."""
    w = SPINDLE_W
    out = []
    for y0, y1, hw in ((2.5, 5.5, w), (5.5, 9.0, w + 0.35), (9.0, 13.0, w)):
        z0, z1 = max(cz - hw, z_lo), min(cz + hw, z_hi)
        if z1 > z0:
            out.append(_el((8 - hw, y0, z0), (8 + hw, y1, z1), "#spindle", faces=("north", "south", "east", "west")))
    return out


def _spindle_rail_models():
    tex = {"particle": _ref("l39_spindle"), "spindle": _ref("l39_spindle"), "rail": _ref("l39_handrail")}
    side4 = ("north", "south", "east", "west")
    post = [_el((6, 0, 6), (10, 2.5, 10), "#spindle", faces=side4 + ("up", "down")),
            _el((6.75, 2.5, 6.75), (9.25, 5.5, 9.25), "#spindle", faces=side4),
            _el((6.4, 5.5, 6.4), (9.6, 9.5, 9.6), "#spindle", faces=side4),
            _el((6.75, 9.5, 6.75), (9.25, 13, 9.25), "#spindle", faces=side4),
            _el((5.5, 13, 5.5), (10.5, 15, 10.5), "#rail", faces=side4 + ("up", "down")),
            _el((6, 15, 6), (10, 15.75, 10), "#rail", faces=side4 + ("up",))]
    # North arm: handrail and bottom rail from the post to the block edge, a spindle at z 4 and
    # half of the one shared with the neighbour at z 0 (the neighbour's south arm has the rest).
    arm = [_el((6.5, 13, 0), (9.5, 15, 5.5), "#rail", faces=("east", "west", "up", "down"), cull=False),
           _el((7, 15, 0), (9, 15.5, 6), "#rail", faces=("east", "west", "up"), cull=False),
           _el((7, 1.25, 0), (9, 2.5, 6.75), "#spindle", faces=("east", "west", "up", "down"), cull=False)]
    for el in arm[:2]:
        el["faces"]["up"]["rotation"] = 90
    arm += _spindle(4.0) + _spindle(0.0, 0.0, 16.0)
    _bm("l39_spindle_rail_post", _model(tex, post, render=None))
    _bm("l39_spindle_rail_side", _model(tex, arm, render=None))
    south = []
    for el in arm:
        f, t = el["from"], el["to"]
        e2 = _el((f[0], f[1], 16 - t[2]), (t[0], t[1], 16 - f[2]), "#rail" if "#rail" in str(el["faces"]) else "#spindle",
                 faces=tuple({"north": "south", "south": "north"}.get(k, k) for k in el["faces"]), cull=False)
        south.append(e2)
    _bm("l39_spindle_rail_inventory", _model(tex, post + arm + south, render=None))
    mp = [{"apply": {"model": _ref("l39_spindle_rail_post")}}]
    for d, y in FACING_Y.items():
        a = {"model": _ref("l39_spindle_rail_side"), "uvlock": True}
        if y:
            a["y"] = y
        mp.append({"when": {d: "true"}, "apply": a})
    _bs("l39_spindle_rail", {"multipart": mp})
    _im("l39_spindle_rail", parent=_ref("l39_spindle_rail_inventory"))


def _craftsman_post_model():
    """Tapered square column inside the 8 px pole box: plinth, a shaft narrowing in steps, a
    simple cap (two mouldings) at the top of the block."""
    tex = {"particle": _ref("l39_craftsman_post"), "paint": _ref("l39_craftsman_post")}
    side4 = ("north", "south", "east", "west")
    e = [_el((4, 0, 4), (12, 1.5, 12), "#paint", faces=side4 + ("down", "up")),
         _el((4.4, 1.5, 4.4), (11.6, 5, 11.6), "#paint", faces=side4 + ("up",)),
         _el((4.7, 5, 4.7), (11.3, 9, 11.3), "#paint", faces=side4 + ("up",)),
         _el((5.0, 9, 5.0), (11.0, 13, 11.0), "#paint", faces=side4 + ("up",)),
         _el((5.2, 13, 5.2), (10.8, 14, 10.8), "#paint", faces=side4),
         _el((4.5, 14, 4.5), (11.5, 14.75, 11.5), "#paint", faces=side4 + ("down",)),
         _el((4, 14.75, 4), (12, 16, 12), "#paint", faces=side4 + ("down", "up"))]
    _bm("l39_craftsman_post", _model(tex, e, render=None))
    P._plain_state("l39_craftsman_post")
    _im("l39_craftsman_post")


def tex_l39_houses_models() -> None:
    """Block models, blockstates and item models for every non-furniture Level 39 house block."""
    for key in PAPERS:
        for name in (f"l39_paper_{key}", f"l39_paper_{key}_base"):
            _surface(name, lambda sfx, name=name: {"parent": "minecraft:block/cube_all",
                                                   "textures": {"all": _ref(name + sfx)}})
    for name in ("l39_beadboard", "l39_wainscot", "l39_plaster", "l39_popcorn_ceiling"):
        _surface(name, lambda sfx, name=name: {"parent": "minecraft:block/cube_all", "textures": {"all": _ref(name + sfx)}})
    for name in FLOORS:
        _surface(name, lambda sfx, name=name: {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "top": _ref(name + sfx), "bottom": _ref("l39_popcorn_ceiling"), "side": _ref(f"{name}_side")}})
    _house_wall_models()
    _knock_wall_models()
    _sash_shade_model()
    _bulb_models()
    P._door("l39_interior_door")
    P._stairs("l39_oak_stairs", "l39_floorboards")
    P._stairs("l39_porch_stairs", "l39_porch_planks")
    _spindle_rail_models()
    _craftsman_post_model()


# =============================================================================
# Sound
# =============================================================================

def _burst(rng, dur, lo, hi, decay):
    t = G.t_axis(dur)
    return G.fft_filter(rng.normal(size=len(t)) * np.exp(-t / decay), lambda f: G.bp_gain(f, lo, hi, 1))


def snd_l39_houses_wall_crack() -> None:
    """Three ~2.4 s takes: a rotten clapboard and plaster wall giving way. A sharp splintering
    crack with the thump of the wall's body, boards tearing off their nails (stick-slip creak,
    a second splinter, a nail popping), then lath and plaster chunks dropping and grit pattering
    down. Close and dry with only a short room tail (the game adds its own reverb, and the
    knock wall itself triggers the town's echo)."""
    for i, seed in enumerate((39697, 39698, 39699), 1):
        rng = G.rng_for(seed)
        dur = 2.4
        n = int(dur * SR)
        x = np.zeros(n)
        t0 = 0.012
        # The crack: a hard broadband snap + a burst of micro-splinters + the wall's body thump.
        G.add_at(x, 1.0 * G.norm(_burst(rng, 0.08, 500, 9000, 0.005)), t0, False)
        spl = np.sort(rng.exponential(0.025, 40))
        G.add_at(x, 0.55 * G.norm(G.clicks(rng, spl, 0.3, 1400, 6500, 0.0006, 0.003, 2.0)), t0, False)
        body = G.ring([rng.uniform(80, 105), rng.uniform(140, 180), rng.uniform(250, 310), rng.uniform(420, 520)],
                      [0.1, 0.07, 0.045, 0.03], [1.0, 0.7, 0.5, 0.35], 0.6)
        body *= np.minimum(1, G.t_axis(0.6) / 0.002)
        G.add_at(x, 0.85 * G.norm(body), t0, False)
        G.add_at(x, 0.5 * G.norm(_burst(rng, 0.25, 60, 600, 0.03)), t0, False)
        # Boards tearing: a fast stick-slip creak with a rough noisy rip over it.
        td = rng.uniform(0.45, 0.65)
        tear = G._creak(rng, td, 140, rng.uniform(380, 520),
                        [rng.uniform(650, 900), rng.uniform(1400, 1900), rng.uniform(2700, 3400)])
        rip = G.fft_filter(rng.normal(size=int(td * SR)), lambda f: G.bp_gain(f, 900, 4500, 1))
        rip *= np.abs(G.smooth_jitter(rng, len(rip), 40, 1.0)) * np.sin(np.pi * np.arange(len(rip)) / len(rip)) ** 0.5
        G.add_at(x, 0.45 * G.norm(tear) + 0.3 * G.norm(rip), 0.06 + rng.uniform(0, 0.04), False)
        # A second splinter as the board lets go, and a nail popping out (a short metallic ping).
        t2 = rng.uniform(0.3, 0.55)
        G.add_at(x, 0.7 * G.norm(_burst(rng, 0.06, 700, 8000, 0.004)), t2, False)
        G.add_at(x, 0.4 * G.norm(G.clicks(rng, t2 + np.sort(rng.exponential(0.02, 16)), 0.2, 1200, 5000, 0.0006, 0.003, 2.0)), 0.0, False)
        G.add_at(x, 0.4 * G.norm(G.ring([rng.uniform(2600, 3400), rng.uniform(5200, 6200)], [0.03, 0.015], [1, 0.4], 0.12)),
                 t2 + rng.uniform(0.04, 0.12), False)
        G.add_at(x, 0.45 * G.norm(G.ring([rng.uniform(120, 160), rng.uniform(260, 330)], [0.05, 0.03], [1, 0.5], 0.3)), t2, False)
        # Lath snapping and plaster chunks hitting the floor.
        for _ in range(int(rng.integers(8, 13))):
            tc = 0.12 + rng.exponential(0.4)
            if tc > dur - 0.6:
                continue
            g = rng.uniform(0.35, 0.75) * np.exp(-tc / 1.4)
            thud = G.ring([rng.uniform(140, 320), rng.uniform(500, 900)], [0.025, 0.012], [1, 0.5], 0.15)
            G.add_at(x, g * (G.norm(thud) + 0.7 * G.norm(_burst(rng, 0.15, 300, 3500, 0.006))), tc, False)
        # Grit and dust pattering down, thinning out.
        times = 0.15 + np.sort(rng.exponential(0.6, 420))
        times = times[times < dur - 0.4]
        grit = G.clicks(rng, times, dur, 1800, 7000, 0.0004, 0.0018, 2.5)
        G.add_at(x, 0.5 * G.norm(grit), 0.0, False)
        hiss = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 2000, 8000, 1))
        tt = G.t_axis(dur)
        hiss *= G.smoothstep(0.1, 0.3, tt) * np.exp(-np.clip(tt - 0.3, 0, None) / 0.5)
        G.add_at(x, 0.06 * G.norm(hiss), 0.0, False)
        # Punch, then a short natural room tail.
        x = G.dist(G.norm(x), 1.8)
        ir = G.reverb_ir(rng, 0.9, 0.16, lp=3800, predelay=0.012)
        x = x + 0.22 * G.norm(G.conv(x, ir)[:n]) * np.abs(x).max()
        A._save_ogg(x, A._snd("level39", f"wall_crack{i}"), peak=0.89, fade_out=0.35)


TEXTURES = [tex_l39_houses_papers, tex_l39_houses_panelling, tex_l39_houses_floors, tex_l39_houses_ceiling,
            tex_l39_houses_knock_wall, tex_l39_houses_sash_shade, tex_l39_houses_bulb, tex_l39_houses_door,
            tex_l39_houses_trim, tex_l39_houses_models]
SOUNDS = [snd_l39_houses_wall_crack]
