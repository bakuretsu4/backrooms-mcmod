"""Level 39 ("Megalophobia") props: textures, block models, blockstates and item models.

Everything small and hand-shaped in the foggy mill town: weeds, bushes and bare trees, the
unpainted picket fences, creosoted utility poles with their crossarms, insulators and
transformers, the rusty lattice of the dead telephone towers, the house shell details (white
trim, shingles, chimney brick, fieldstone foundation, 6/6 sash windows, the light-blue front
door) and the park and M.E.G. camp props (bench, gazebo posts, swing set, canvas, crates,
a dead lantern and the survey clipboards).

Run with `python tools/gen_assets.py l39_props` from the project root. Every texture uses its
own fixed random seed (39100-39199), so re-running gives identical files. The models are built
in code (see `_el`) so the hand-made shapes stay readable: every box is listed in block pixels
(0..16, allowed -16..32), and the face UVs are worked out from the box position the same way
Minecraft does it when a model leaves "uv" out, but always kept inside the texture.
"""
from __future__ import annotations

import json

import numpy as np
from PIL import Image, ImageDraw

import gen_assets as G

S = G.S          # 128 px block texture size
SQ2 = np.sqrt(2.0)


def _blk(name: str):
    return G.TEX / "block" / f"{name}.png"


def _itm(name: str):
    return G.TEX / "item" / f"{name}.png"


def _ref(name: str) -> str:
    return f"backrooms:block/{name}"


# =============================================================================
# Small drawing helpers
# =============================================================================

def _resolve(canvas: Image.Image, ss: int, thr: float = 0.5) -> np.ndarray:
    """Shrink a supersampled RGBA drawing to a cutout texture (alpha strictly 0 or 255).

    Colours are averaged over the covered sub-pixels only, so edges don't get a dark fringe from
    the transparent black around them (same trick as level9._resolve_cutout).
    """
    h, w = canvas.size[1] // ss, canvas.size[0] // ss
    a = np.asarray(canvas, float).reshape(h, ss, w, ss, 4)
    cov = a[..., 3].mean(axis=(1, 3)) / 255.0
    wgt = a[..., 3:4] / 255.0
    rgb = (a[..., :3] * wgt).sum(axis=(1, 3)) / np.maximum(wgt.sum(axis=(1, 3)), 1e-6)
    out = np.zeros((h, w, 4))
    out[..., :3] = rgb
    out[..., 3] = np.where(cov >= thr, 255, 0)
    return out


def _col(c, a=255) -> tuple:
    return tuple(int(v) for v in np.clip(c, 0, 255)) + (a,)


def _render(prims, ss=4, wrap=(True, True), size=(S, S), thr=0.5) -> np.ndarray:
    """Draw a list of primitives into a cutout texture.

    prims: ("line", [(x, y), ...], width, colour) | ("poly", pts, 0, colour) |
    ("dot", [(x, y)], radius, colour). Coordinates are texture pixels. With wrap, every primitive
    is drawn again one tile to each side, so shapes crossing an edge continue on the other side and
    the texture tiles.
    """
    w, h = size
    canvas = Image.new("RGBA", (w * ss, h * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(canvas)
    oxs = (-w, 0, w) if wrap[0] else (0,)
    oys = (-h, 0, h) if wrap[1] else (0,)
    for kind, pts, width, col in prims:
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        pad = width + 2
        for ox in oxs:
            if max(xs) + ox + pad < 0 or min(xs) + ox - pad > w:
                continue
            for oy in oys:
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
    return _resolve(canvas, ss, thr)


def _wrap_mask(draw_fn, shape=(S, S), ss=4) -> np.ndarray:
    """Like G.pil_mask, but draws at the 9 wrap offsets so the mask tiles seamlessly.

    draw_fn(d, k, ox, oy) draws in white; add (ox, oy) to every coordinate before scaling by k.
    """
    h, w = shape
    im = Image.new("L", (w * ss, h * ss), 0)
    d = ImageDraw.Draw(im)
    for ox in (-w, 0, w):
        for oy in (-h, 0, h):
            draw_fn(d, ss, ox, oy)
    return np.asarray(im.resize((w, h), Image.BOX), float) / 255.0


def _wander(rng, x0, y0, ang, step, n, turn=0.25):
    """Points of a gently wandering line (cracks, twigs, pencil strokes)."""
    pts = [(x0, y0)]
    for _ in range(n):
        ang += rng.normal(0, turn)
        x0, y0 = x0 + step * np.cos(ang), y0 + step * np.sin(ang)
        pts.append((x0, y0))
    return pts


def _checks(rng, n, vertical=True, length=(30, 90), shape=(S, S)) -> np.ndarray:
    """Seasoning checks: long thin cracks running with the grain (0..1 mask, wraps)."""
    lines = []
    for _ in range(n):
        L = rng.uniform(*length)
        x, y = rng.uniform(0, shape[1]), rng.uniform(0, shape[0])
        ang = (np.pi / 2 if vertical else 0.0) + rng.normal(0, 0.03)
        lines.append((_wander(rng, x, y, ang, 3.0, int(L / 3), 0.04), rng.uniform(0.7, 1.4)))

    def draw(d, k, ox, oy):
        for pts, wd in lines:
            d.line([((x + ox) * k, (y + oy) * k) for x, y in pts], fill=255, width=max(1, int(wd * k)))
    return _wrap_mask(draw, shape)


def _grey_wood(rng, vertical=True, base=(124, 122, 116), shape=(S, S), checks=5) -> np.ndarray:
    """Unpainted wood left out for decades: silver-grey, raised grain, dark grain lines, checks.

    The soft parts of the grain wear away first, so the hard lines stand out as darker ridges;
    some boards keep a brownish under-tone where the sun never bleached them.
    """
    an = (1, 14) if vertical else (14, 1)
    img = G.fill(base, shape)
    img = G.add_lum(img, G.fnoise(rng, shape, beta=1.9, aniso=an, kmin=2) * 8
                    + G.fnoise(rng, shape, beta=0.9, aniso=an, kmin=8) * 5)
    lines = G.smoothstep(0.9, 2.0, G.fnoise(rng, shape, beta=1.1, aniso=an, kmin=10))
    img = G.add_lum(img, -24 * lines)
    brown = G.smoothstep(0.2, 1.6, G.fnoise(rng, shape, beta=2.6))
    img = G.over(img, np.array([118, 100, 80]), brown * 0.35)
    img = G.add_lum(img, rng.normal(0, 3.0, shape))
    if checks:
        c = _checks(rng, checks, vertical, shape=shape)
        img = G.add_lum(img, -46 * c - 10 * G.blur(c, 1.0))
    return img


def _knots(img, rng, n, vertical=True) -> np.ndarray:
    """A few small dark knots with a ring of disturbed grain round them."""
    X, Y = G.grid(img.shape[:2])
    for _ in range(n):
        cx, cy = rng.uniform(10, S - 10, 2)
        rx, ry = (rng.uniform(2, 3.5), rng.uniform(4, 7)) if vertical else (rng.uniform(4, 7), rng.uniform(2, 3.5))
        r = np.sqrt(((X - cx) / rx) ** 2 + ((Y - cy) / ry) ** 2)
        img = G.add_lum(img, -40 * G.smoothstep(1.0, 0.6, r) - 10 * np.exp(-((r - 1.6) / 0.35) ** 2))
    return img


def _nail(img, cx, cy, rng, r=1.6, rust=0.5) -> np.ndarray:
    """A rusty nail head with a short rust run below it."""
    X, Y = G.grid(img.shape[:2])
    d = np.hypot(X - cx, Y - cy)
    img = G.over(img, np.array([96, 64, 44]), np.exp(-(d / (r * 2.6)) ** 2) * 0.35 * rust)
    run = np.exp(-((X - cx) / 1.1) ** 2) * (Y > cy) * np.exp(-(Y - cy) / rng.uniform(6, 16))
    img = G.over(img, np.array([104, 62, 38]), np.clip(run * 0.45 * rust, 0, 1))
    img = G.over(img, np.array([52, 46, 42]), G.smoothstep(r + 0.6, r - 0.3, d))
    return G.add_lum(img, 18 * G.smoothstep(r * 0.7, 0, np.hypot(X - cx + 0.5, Y - cy + 0.5)) * (d < r))


def _rust(rng, base=(66, 58, 54), shape=(S, S), amount=0.7) -> np.ndarray:
    """Old steel: dark mill scale with blotchy orange-brown rust, flakes and pitting."""
    img = G._smooth_concrete(rng, base, mottle=4.0, fine=1.2, shape=shape)
    r = G.fnoise(rng, shape, beta=2.0) + 0.4 * G.fnoise(rng, shape, beta=1.2, kmin=6)
    img = G.over(img, np.array([122, 66, 38]), G.smoothstep(-0.6, 1.0, r) * amount)
    img = G.over(img, np.array([150, 88, 46]), G.smoothstep(1.0, 2.0, r) * amount * 0.6)
    pits = (rng.random(shape) < 0.02) * rng.uniform(0.5, 1, shape)
    img = G.add_lum(img, -G.blur(pits, 0.6) * 50)
    flakes = G.smoothstep(1.6, 2.2, G.fnoise(rng, shape, beta=1.1, kmin=8))
    return G.add_lum(img, -16 * flakes + rng.normal(0, 3, shape))


def _dirty_bottom(img, rng, start=0.72, col=(66, 58, 46), a=0.5) -> np.ndarray:
    """Mud splash and damp creeping up from the ground (texture bottom = ground)."""
    X, Y = G.grid(img.shape[:2])
    h = img.shape[0]
    m = G.smoothstep(start, 1.0, Y / h + 0.08 * G.fnoise(rng, img.shape[:2], beta=1.6))
    return G.over(img, np.array(col), m * a)


# =============================================================================
# Ground plants and trees
# =============================================================================

def _blade(rng, x0, h, w0, lean, bend, col, tip, segs=10):
    """Quads of one tapering grass/weed blade growing up from the bottom edge."""
    s = np.linspace(0, 1, segs + 1)
    cx = x0 + lean * s ** bend
    cy = S + 1 - h * s
    hw = 0.5 * (w0 * (1 - s) ** 0.8 + 0.15)
    out = []
    for i in range(segs):
        sm = (s[i] + s[i + 1]) / 2
        c = G.lerp(col * (0.55 + 0.5 * sm ** 0.8), tip, 0.45 * sm ** 2)
        out.append(("poly", [(cx[i] - hw[i], cy[i]), (cx[i] + hw[i], cy[i]),
                             (cx[i + 1] + hw[i + 1], cy[i + 1]), (cx[i + 1] - hw[i + 1], cy[i + 1])], 0, _col(c)))
    return out


def tex_l39_props_weeds() -> None:
    """Tall dry yard weeds for a cross model: bleached grass, dock and foxtail seed heads, dried
    umbels. Cold, washed-out greys and browns: autumn at night in the fog (the photo's yard)."""
    rng = G.rng_for(39101)
    dry = np.array([(100, 96, 74), (90, 88, 68), (110, 102, 80), (78, 80, 62), (94, 98, 76), (72, 74, 60),
                    (114, 106, 86)], float)
    green = np.array([(60, 70, 56), (54, 64, 52), (66, 74, 58)], float)
    tip = np.array([136, 130, 110], float)
    items = []
    for _ in range(150):
        items.append(("blade", rng.random()))
    for _ in range(16):
        items.append(("stalk", rng.random()))
    items.sort(key=lambda t: t[1])
    prims = []
    for kind, depth in items:
        shade = 0.68 + 0.42 * depth
        if kind == "blade":
            h = S * rng.uniform(0.25, 0.72)
            pal = green if rng.random() < 0.3 else dry
            col = pal[rng.integers(len(pal))] * shade
            # Some blades are snapped and droop over (they bend hard high up).
            broken = rng.random() < 0.2
            lean = rng.normal(0, 0.12) * h + (rng.choice([-1, 1]) * h * 0.45 if broken else 0)
            prims += _blade(rng, rng.uniform(0, S), h, rng.uniform(1.6, 3.4), lean,
                            rng.uniform(3.0, 4.5) if broken else rng.uniform(1.3, 2.2), col, tip)
            continue
        # A tall stalk: thin stem, a couple of side shoots, and a seed head.
        h = S * rng.uniform(0.72, 0.97)
        x0 = rng.uniform(6, S - 6)
        stem = np.array([96, 86, 64]) * shade * rng.uniform(0.9, 1.1)
        pts = _wander(rng, x0, S + 1, -np.pi / 2 + rng.normal(0, 0.08), h / 14, 14, 0.05)
        for i in range(len(pts) - 1):
            prims.append(("line", [pts[i], pts[i + 1]], 1.25 - 0.5 * i / 14, _col(stem)))
        for _ in range(rng.integers(1, 3)):
            j = int(rng.integers(5, 11))
            sx, sy = pts[j]
            side = _wander(rng, sx, sy, -np.pi / 2 + rng.choice([-1, 1]) * rng.uniform(0.4, 0.8), 3.5, 5, 0.1)
            prims.append(("line", side, 0.8, _col(stem * 0.95)))
        hx, hy = pts[-1]
        head = rng.choice(["dock", "foxtail", "umbel", "dock"])
        if head == "dock":
            # Rusty-brown dock seeds clustered along the top third of the stem.
            for i in range(int(len(pts) * 0.6), len(pts)):
                px, py = pts[i]
                for _ in range(3):
                    c = np.array([86, 64, 48]) * shade * rng.uniform(0.8, 1.15)
                    prims.append(("dot", [(px + rng.normal(0, 2.2), py + rng.normal(0, 1.5))], rng.uniform(0.9, 1.5), _col(c)))
        elif head == "foxtail":
            # A pale fuzzy spike that nods over.
            ang = -np.pi / 2 + rng.choice([-1, 1]) * rng.uniform(0.4, 1.0)
            spike = _wander(rng, hx, hy, ang, 2.2, 7, 0.25)
            for i, (px, py) in enumerate(spike):
                r = 2.4 * np.sin(np.pi * (i + 1) / (len(spike) + 1)) + 0.6
                for _ in range(6):
                    c = np.array([138, 128, 100]) * shade * rng.uniform(0.85, 1.1)
                    prims.append(("dot", [(px + rng.normal(0, r * 0.6), py + rng.normal(0, r * 0.6))], 0.8, _col(c)))
        else:
            # A dried umbel (wild carrot): rays spreading up into a cupped grey dome of seed dots.
            for _ in range(9):
                a = -np.pi / 2 + rng.uniform(-1.0, 1.0)
                L = rng.uniform(6, 10)
                ex, ey = hx + L * np.cos(a), hy + L * np.sin(a) * 0.55
                prims.append(("line", [(hx, hy), (ex, ey)], 0.6, _col(stem * 1.05)))
                for _ in range(4):
                    c = np.array([150, 146, 128]) * shade * rng.uniform(0.85, 1.1)
                    prims.append(("dot", [(ex + rng.normal(0, 1.4), ey + rng.normal(0, 1.0))], 0.9, _col(c)))
    img = _render(prims, wrap=(True, False))
    img[..., :3] = np.clip(img[..., :3] + rng.normal(0, 2.0, (S, S, 1)), 0, 255)
    G.save_png(img, _blk("l39_weeds"))


def _leaf_poly(x, y, L, W, ang):
    """Outline of one pointed oval leaf starting at (x, y) and pointing along ang."""
    t = np.linspace(0, 1, 9)
    hw = W * np.sin(np.pi * t) ** 0.8
    ca, sa = np.cos(ang), np.sin(ang)
    left = [(x + L * ti * ca - h * sa, y + L * ti * sa + h * ca) for ti, h in zip(t, hw)]
    right = [(x + L * ti * ca + h * sa, y + L * ti * sa - h * ca) for ti, h in zip(t[::-1], hw[::-1])]
    return left + right


def _twig_prims(rng, x, y, ang, length, width, col, depth=0):
    out = []
    pts = _wander(rng, x, y, ang, 3.0, max(2, int(length / 3)), 0.22)
    for i in range(len(pts) - 1):
        out.append(("line", [pts[i], pts[i + 1]], width * (1 - 0.5 * i / len(pts)), _col(col)))
        if depth < 2 and rng.random() < 0.22:
            out += _twig_prims(rng, *pts[i + 1], ang + rng.choice([-1, 1]) * rng.uniform(0.5, 1.0),
                               length * 0.45, width * 0.65, col * 1.05, depth + 1)
    return out


# Mid-dark, slightly cold greens of wet privet/elm leaves at night (before lighting), and the
# lighter yellow-green of new shoots on a clipped hedge.
LEAF_PAL = ((68, 84, 58), (76, 92, 62), (62, 78, 54), (84, 98, 66), (72, 86, 64), (64, 80, 60), (80, 94, 70))
SHOOT_PAL = ((104, 118, 66), (96, 112, 60), (112, 122, 72))
LIGHT = np.array([-0.45, -0.9]) / np.hypot(0.45, 0.9)   # light from the upper left (texture y grows down)


def _leaf_prims(rng, x, y, L, col):
    """One flat leaf (pointed oval + darker midrib) at a random angle."""
    ang = rng.uniform(0, 2 * np.pi)
    return [("poly", _leaf_poly(x, y, L, L * rng.uniform(0.22, 0.32), ang), 0, _col(col)),
            ("line", [(x, y), (x + 0.8 * L * np.cos(ang), y + 0.8 * L * np.sin(ang))], 0.45, _col(col * 0.72))]


def _lit_leaf(rng, x, y, L, col):
    """A two-tone leaf: the half turned toward the light is lighter, the other darker, with a
    dark midrib. That small light/dark split on every leaf is what makes foliage read as leaves
    instead of flat green speckle."""
    ang = rng.uniform(0, 2 * np.pi)
    ca, sa = np.cos(ang), np.sin(ang)
    t = np.linspace(0, 1, 9)
    hw = L * rng.uniform(0.26, 0.36) * np.sin(np.pi * t) ** 0.8
    mid = [(x + L * ti * ca, y + L * ti * sa) for ti in t]
    out = []
    for sgn in (1, -1):
        k = 1.0 + 0.18 * float(np.array([-sa, ca]) * sgn @ LIGHT)
        side = [(x + L * ti * ca - sgn * h * sa, y + L * ti * sa + sgn * h * ca) for ti, h in zip(t, hw)]
        out.append(("poly", side + mid[::-1], 0, _col(col * k)))
    out.append(("line", [mid[1], mid[-2]], 0.5, _col(col * 0.72)))
    return out


def _cluster_leaves(rng, clusters, leaf_len, pal=LEAF_PAL, shade=(0.6, 1.15), dead=0.04):
    """Leaves grouped in clusters, drawn back to front.

    clusters: (cx, cy, R, n, depth0). Each cluster is a little dome: its rim leaves are drawn
    first and darker (they turn away and sit in the hollows between clusters), its middle last
    and lighter; the upper-left of the dome catches the light, the lower-right is in its own
    shadow. depth0 lifts whole clusters forward (lighter) or back (darker).
    """
    items = []
    for cx, cy, R, n, d0 in clusters:
        for _ in range(int(n)):
            a = rng.uniform(0, 2 * np.pi)
            rr = np.sqrt(rng.uniform())
            dx, dy = rr * np.cos(a), rr * np.sin(a)
            dome = 1 - rr * rr
            items.append((d0 + 0.35 * dome + 0.05 * rng.random(), cx + R * dx, cy + R * dy,
                          0.5 - 0.42 * dy - 0.22 * dx, dome))
    items.sort(key=lambda it: it[0])
    lo, hi = shade
    prims = []
    for depth, x, y, lit, dome in items:
        if rng.random() < dead:
            col = np.array([88, 82, 60], float)      # a dry, dull yellow-brown leaf
        else:
            col = np.array(pal[rng.integers(len(pal))], float) * rng.uniform(0.93, 1.07)
        k = lo + (hi - lo) * np.clip(0.45 * lit + 0.3 * dome + 0.35 * depth, 0, 1)
        prims += _lit_leaf(rng, x, y, rng.uniform(*leaf_len), col * k)
    return prims


def _despeckle(img, min_px=5) -> np.ndarray:
    """Cut-out clean-up: drop lone specks of leaf and fill pin-holes, so the edges read as soft
    leaf outlines instead of per-pixel noise."""
    from scipy import ndimage
    solid = img[..., 3] > 0
    lab, n = ndimage.label(solid)
    if n:
        sizes = ndimage.sum(np.ones_like(lab), lab, range(1, n + 1))
        img[..., 3][np.isin(lab, np.nonzero(sizes < min_px)[0] + 1)] = 0
    lab, n = ndimage.label(img[..., 3] == 0)
    if n:
        sizes = ndimage.sum(np.ones_like(lab), lab, range(1, n + 1))
        tiny = np.isin(lab, np.nonzero(sizes < min_px)[0] + 1)
        a = (img[..., 3] > 0).astype(float)
        avg = G.blur(img[..., :3] * a[..., None], 1.2) / np.maximum(G.blur(a, 1.2), 1e-3)[..., None]
        img[tiny, :3] = avg[tiny]
        img[tiny, 3] = 255
    return img


def _clusters_in(rng, n, cx, cy, spread, R, density, d0=(0.0, 0.4), margin=4):
    """n cluster specs inside a disc (cx, cy, spread), each kept fully inside the texture
    (margin px), so no leaf is cut off by the texture edge (that would draw a straight line)."""
    out = []
    while len(out) < n:
        a, rr = rng.uniform(0, 2 * np.pi), spread * np.sqrt(rng.uniform())
        r = rng.uniform(*R)
        x, y = cx + rr * np.cos(a), cy + rr * np.sin(a)
        if margin + r + 4 < x < S - margin - r - 4 and margin + r + 4 < y < S - margin - r - 4:
            out.append((x, y, r, density * r * r, rng.uniform(*d0)))
    return out


def _twig_tree(rng, x, y, ang, length, width, col, prims, depth=0, maxd=4, branch=0.3):
    """A twig that forks as it goes: wandering segments thinning toward the tip, side twigs
    leaving at 20-50 degrees from the current heading. A few segments catch a wet glint."""
    n = max(2, int(length / 4))
    pts = _wander(rng, x, y, ang, length / n, n, 0.16)
    for i in range(n):
        wdt = max(1.0, width * (1 - 0.5 * i / n))
        c = col * (1.5 if rng.random() < 0.12 else 1.0)
        prims.append(("line", [pts[i], pts[i + 1]], wdt, _col(c)))
        if depth < maxd and i >= 1 and rng.random() < branch:
            head = np.arctan2(pts[i + 1][1] - pts[i][1], pts[i + 1][0] - pts[i][0])
            _twig_tree(rng, *pts[i + 1], head + rng.choice([-1, 1]) * rng.uniform(0.35, 0.85),
                       length * rng.uniform(0.35, 0.6), width * 0.68, col * rng.uniform(0.95, 1.1),
                       prims, depth + 1, maxd, branch)
    if depth >= 2:
        prims.append(("dot", [pts[-1]], 0.9, _col(col * 0.8)))   # a bud at the very tip


def tex_l39_props_foliage() -> None:
    """Leaves of the town's shrubs and the few trees that kept some.

    All leaves are two-tone (lit half / shaded half) and grouped in domed clusters: lit upper
    sides, shadowed hollows, mid-dark greens (mean about #3E4A34-#4A5838 before lighting).
    l39_dark_leaves: one leafy mass with big gaps and a ragged edge clear of the texture border.
    l39_bush / l39_bush_top: one rounded clump, dead twigs poking out of the bottom.
    l39_hedge / l39_hedge_top: tiling clipped privet, dense, only pin-holes; the top has new shoots.
    l39_hedge_outer / l39_hedge_crown: the loose outer leaf layer (sides / top) of the hedge.
    l39_hedge_sprig: a single new shoot sticking up out of the hedge.
    """
    # Tree foliage: one leafy mass (not tiling; the leaves model shows it whole on an inset cube
    # and two crossed planes): clustered leaf groups on a few twigs, big gaps between groups,
    # ragged edges that never touch the texture border, so no face reads as a square.
    rng = G.rng_for(39102)
    prims = []
    for _ in range(5):
        _twig_tree(rng, 64 + rng.normal(0, 14), 64 + rng.normal(0, 14), rng.uniform(0, 2 * np.pi),
                   rng.uniform(35, 55), 2.0, np.array([50, 44, 38]) * rng.uniform(0.85, 1.15), prims,
                   maxd=2, branch=0.25)
    prims += _cluster_leaves(rng, _clusters_in(rng, 19, 64, 64, 56, (10, 17), 0.22, margin=1), (7, 11), dead=0.02)
    G.save_png(_despeckle(_render(prims, wrap=(False, False))), _blk("l39_dark_leaves"))

    # Bush: the sides show the whole clump; the top faces a smaller one, so the boxes' flat tops
    # don't stick out past the ragged tops of their sides.
    G.save_png(_bush_clump(39104, 1.0, 9), _blk("l39_bush"))
    G.save_png(_bush_clump(39105, 0.72, 0), _blk("l39_bush_top"))

    # Hedge core (tiling): a dark-green inside behind a deep layer of small shadowed leaves,
    # clipped-privet clusters on top of that (lit tops, shadowed hollows), only pin-point holes.
    G.save_png(_hedge_skin(39170, shoots=0), _blk("l39_hedge"))
    # The hedge's top face: the same, with a sprinkling of lighter new shoots.
    G.save_png(_hedge_skin(39175, shoots=9), _blk("l39_hedge_top"))

    # Outer leaf layer (tiles along u): loose clusters with gaps, so the core shows through and
    # the silhouette is bumpy; rows 0-4 stay clear except where a cluster pokes up.
    rng = G.rng_for(39185)
    cl = [(rng.uniform(0, S), rng.uniform(16, 122), r, 0.4 * r * r, rng.uniform(0.2, 0.6))
          for r in rng.uniform(7, 12, 34)]
    prims = _cluster_leaves(rng, cl, (5, 7.5), shade=(0.7, 1.2), dead=0.02)
    prims += _cluster_leaves(rng, [(rng.uniform(0, S), rng.uniform(14, 34), r, 0.3 * r * r, 0.7)
                                   for r in rng.uniform(4, 7, 6)], (4, 6), SHOOT_PAL, (0.85, 1.1), 0)
    G.save_png(_despeckle(_render(prims, wrap=(True, False))), _blk("l39_hedge_outer"))
    # Outer layer over the top (tiles both ways), with new shoots.
    rng = G.rng_for(39186)
    cl = [(rng.uniform(0, S), rng.uniform(0, S), r, 0.4 * r * r, rng.uniform(0.3, 0.7)) for r in rng.uniform(7, 12, 30)]
    prims = _cluster_leaves(rng, cl, (5, 7.5), shade=(0.72, 1.22), dead=0.02)
    prims += _cluster_leaves(rng, [(rng.uniform(0, S), rng.uniform(0, S), r, 0.3 * r * r, 0.8)
                                   for r in rng.uniform(4, 7, 8)], (4, 6), SHOOT_PAL, (0.85, 1.1), 0)
    G.save_png(_despeckle(_render(prims)), _blk("l39_hedge_crown"))

    # A single new shoot for the sprigs sticking up out of the hedge.
    rng = G.rng_for(39171)
    prims = []
    stem = np.array([58, 56, 40])
    shoots = [_wander(rng, 64, 130, -np.pi / 2 + rng.normal(0, 0.15), 8, 14, 0.12)]
    for _ in range(3):
        p = shoots[0][int(rng.integers(4, 11))]
        shoots.append(_wander(rng, *p, -np.pi / 2 + rng.choice([-1, 1]) * rng.uniform(0.5, 0.9), 7, 7, 0.15))
    for k, pts in enumerate(shoots):
        for i in range(len(pts) - 1):
            prims.append(("line", [pts[i], pts[i + 1]], 2.2 if k == 0 else 1.6, _col(stem)))
    for i in range(60):
        pts = shoots[rng.integers(len(shoots))]
        j = int(rng.integers(2, len(pts)))
        x, y = pts[j]
        pal = SHOOT_PAL if y < 70 else LEAF_PAL           # the young tip leaves are the lightest
        col = np.array(pal[rng.integers(len(pal))], float) * rng.uniform(0.85, 1.1)
        prims += _lit_leaf(rng, x + rng.normal(0, 3), y + rng.normal(0, 3), rng.uniform(6, 10), col)
    G.save_png(_despeckle(_render(prims, wrap=(False, False))), _blk("l39_hedge_sprig"))


def _hedge_skin(seed, shoots) -> np.ndarray:
    """Tiling clipped-privet surface (see tex_l39_props_foliage)."""
    rng = G.rng_for(seed)
    prims = []
    for _ in range(10):
        c = np.array([40, 36, 30]) * rng.uniform(0.85, 1.2)
        prims += _twig_prims(rng, rng.uniform(0, S), rng.uniform(0, S), rng.uniform(0, 2 * np.pi),
                             rng.uniform(20, 45), rng.uniform(1.4, 2.0), c)
    deep = [(rng.uniform(0, S), rng.uniform(0, S), 6.0, 1, 0.0) for _ in range(520)]
    prims += _cluster_leaves(rng, deep, (4, 6), shade=(0.68, 0.86), dead=0.01)
    cl = [(rng.uniform(0, S), rng.uniform(0, S), r, 0.32 * r * r, rng.uniform(0.0, 0.5)) for r in rng.uniform(7, 12, 56)]
    prims += _cluster_leaves(rng, cl, (4, 6.5), shade=(0.74, 1.24), dead=0.02)
    if shoots:
        prims += _cluster_leaves(rng, [(rng.uniform(0, S), rng.uniform(0, S), r, 0.3 * r * r, 0.8)
                                       for r in rng.uniform(4, 7, shoots)], (4, 6), SHOOT_PAL, (0.85, 1.1), 0)
    leaf = _render(prims)
    back = G.add_lum(G.fill((46, 58, 40), (S, S)), G.fnoise(rng, beta=2.4) * 3)
    holes = G.fnoise(rng, beta=2.4) < -1.6
    img = np.zeros((S, S, 4))
    img[..., :3] = np.where(leaf[..., 3:] > 0, leaf[..., :3], back)
    img[..., 3] = np.where((leaf[..., 3] > 0) | ~holes, 255, 0)
    return _despeckle(img)


def _bush_clump(seed, scale, n_twigs) -> np.ndarray:
    """One rounded leaf clump (not tiling), same leaf treatment as the hedge and the trees:
    leaf clusters inside an outline whose radius wobbles with the angle, a few dead twigs from
    the bottom centre, the top of the clump lit and the underside in its own shadow."""
    rng = G.rng_for(seed)
    prims = []
    for _ in range(n_twigs):
        c = np.array([52, 44, 38]) * rng.uniform(0.8, 1.15)
        _twig_tree(rng, 64 + rng.normal(0, 8), 126, -np.pi / 2 + rng.uniform(-1.1, 1.1), rng.uniform(40, 62),
                   2.2, c, prims, maxd=2, branch=0.2)
    phi = rng.uniform(0, 2 * np.pi, 3)

    def rim(th):
        return scale * (52 + 5 * np.sin(3 * th + phi[0]) + 4 * np.sin(5 * th + phi[1]) + 3 * np.sin(2 * th + phi[2]))
    cl = []
    while len(cl) < 20:
        th = rng.uniform(0, 2 * np.pi)
        r = rng.uniform(9, 15) * scale
        d = (rim(th) - r) * np.sqrt(rng.uniform())
        x, y = 64 + d * np.cos(th), 66 + d * np.sin(th)
        # Clusters higher up sit further forward (lighter): the bush is lit from above.
        cl.append((x, y, r, 0.3 * r * r, 0.5 * (1 - y / S) + 0.1 * rng.random()))
    prims += _cluster_leaves(rng, cl, (4.5, 8), shade=(0.58, 1.15), dead=0.05)
    return _despeckle(_render(prims, wrap=(False, False)))


def tex_l39_props_tree() -> None:
    """Bare tree: wet dark bark with ridges running along the rod (l39_tree_bark), its end grain
    (l39_tree_end), and the fine forking twigs (l39_twigs) of the twig block and branch tips."""
    rng = G.rng_for(39173)
    img = G.fill((36, 32, 29), (S, S))
    ridge = G.fnoise(rng, beta=1.5, aniso=(1, 10), kmin=5)
    img = G.add_lum(img, ridge * 6 - G.smoothstep(0.2, 1.2, -ridge) * 10 + rng.normal(0, 2.5, (S, S)))
    # Bark plates break across the ridges here and there.
    img = G.add_lum(img, -8 * G.smoothstep(1.4, 2.0, G.fnoise(rng, beta=1.2, aniso=(4, 1), kmin=6)))
    # Rain on the ridge tops gives a cold wet shine; pale lichen spots, dark moss in the furrows.
    sheen = G.smoothstep(0.6, 1.5, ridge) * G.smoothstep(-0.2, 1.2, G.fnoise(rng, beta=2.0))
    img = G.add_lum(img, 18 * sheen, (0.9, 0.95, 1.05))
    img = G.over(img, np.array([88, 96, 84]), G.smoothstep(1.3, 2.0, G.fnoise(rng, beta=1.8, kmin=3)) * 0.4)
    img = G.over(img, np.array([36, 46, 30]),
                 G.smoothstep(0.8, 1.8, G.fnoise(rng, beta=2.4)) * G.smoothstep(0.0, 1.0, -ridge) * 0.5)
    G.save_png(img, _blk("l39_tree_bark"))

    X, Y = G.grid()
    r = np.hypot(X - 64, Y - 64) + 0.8 * G.fnoise(rng, beta=2.6)
    end = G.fill((60, 50, 40), (S, S))
    end = G.add_lum(end, 5 * np.sin(r / 1.3) + 0.3 * r + rng.normal(0, 3, (S, S)) - 6)
    end = G.over(end, np.array([34, 28, 24]), G.smoothstep(3, 0, r) * 0.7)          # pith
    end = G.over(end, img, G.smoothstep(22, 26, r))                                  # bark round the trunk-size end
    G.save_png(end, _blk("l39_tree_end"))

    rng = G.rng_for(39172)
    prims = []
    base = np.array([44, 38, 34])
    for _ in range(6):
        _twig_tree(rng, rng.uniform(20, 108), 130, -np.pi / 2 + rng.uniform(-0.45, 0.45), rng.uniform(70, 115),
                   2.2, base * rng.uniform(0.85, 1.15), prims)
    for _ in range(4):
        left = rng.random() < 0.5
        _twig_tree(rng, -2 if left else 130, rng.uniform(30, 100),
                   (-0.5 if left else np.pi + 0.5) + rng.normal(0, 0.2), rng.uniform(40, 70), 1.6,
                   base * rng.uniform(0.85, 1.15), prims, maxd=3)
    G.save_png(_render(prims, wrap=(False, False), thr=0.35), _blk("l39_twigs"))


# =============================================================================
# Fences, poles, towers
# =============================================================================

PICKET_CX = (2.0, 6.0, 10.0, 14.0)    # picket centres along the section (block pixels)
PICKET_W = 2.5
RAILS = ((3.0, 5.0), (10.0, 12.0))     # rail heights (y0, y1)


def tex_l39_props_fence() -> None:
    """Unpainted, silver-grey picket fence wood (l39_picket), its rails (l39_picket_rail) and the
    darker square post (l39_fence_post). The nail heads sit exactly where the pickets cross the
    rails: picket centres x = 2/6/10/14 px and rails at y 3-5 / 10-12 (texture rows 40 and 96)."""
    rng = G.rng_for(39110)
    img = _grey_wood(rng, True, (128, 126, 120), checks=7)
    img = _knots(img, rng, 3)
    for cx in PICKET_CX:
        for cy in (40, 96):
            img = _nail(img, cx * 8 + rng.normal(0, 0.6), cy + rng.normal(0, 0.8), rng)
    img = _dirty_bottom(img, rng, 0.78, (62, 58, 46), 0.55)
    green = G.smoothstep(0.85, 1.0, G.grid()[1] / S) * G.smoothstep(0.0, 1.0, G.fnoise(rng, beta=2.0))
    img = G.over(img, np.array([58, 72, 50]), green * 0.4)
    G.save_png(img, _blk("l39_picket"))

    rng = G.rng_for(39111)
    rail = _grey_wood(rng, False, (114, 112, 106), checks=5)
    rail = _knots(rail, rng, 2, vertical=False)
    rail = G.over(rail, np.array([72, 82, 62]), G.smoothstep(1.2, 2.0, G.fnoise(rng, beta=2.0)) * 0.4)
    G.save_png(rail, _blk("l39_picket_rail"))

    rng = G.rng_for(39112)
    post = _grey_wood(rng, True, (106, 102, 94), checks=9)
    post = _knots(post, rng, 2)
    post = _dirty_bottom(post, rng, 0.6, (54, 50, 40), 0.6)
    post = G.over(post, np.array([70, 80, 56]), G.smoothstep(1.0, 1.9, G.fnoise(rng, beta=2.2)) * 0.45)
    G.save_png(post, _blk("l39_fence_post"))


def tex_l39_props_pole() -> None:
    """Utility pole parts: creosoted pole wood (dark, split by long vertical checks, climbing-gaff
    scars, staples), its end grain, the lighter grey crossarm, galvanised braces and the green
    glass insulators."""
    rng = G.rng_for(39113)
    X, Y = G.grid()
    img = G.fill((60, 46, 36), (S, S))
    img = G.add_lum(img, G.fnoise(rng, beta=1.8, aniso=(1, 16), kmin=2) * 9
                    + G.fnoise(rng, beta=0.8, aniso=(1, 16), kmin=10) * 5, (1.0, 0.9, 0.8))
    grey = G.smoothstep(0.0, 1.6, G.fnoise(rng, beta=2.4))
    img = G.over(img, np.array([96, 88, 78]), grey * 0.4)                 # sun-greyed patches
    tar = G.smoothstep(0.6, 1.6, G.fnoise(rng, beta=1.6, aniso=(1, 6)))
    img = G.over(img, np.array([28, 22, 18]), tar * 0.55)                 # creosote bleeding out
    img = G.add_lum(img, 14 * G.smoothstep(1.4, 2.2, G.fnoise(rng, beta=1.4, aniso=(1, 8), kmin=3)) * tar,
                    (0.85, 0.9, 1.0))                                     # oily sheen
    c = _checks(rng, 6, True, (60, 128))
    img = G.add_lum(img, -42 * c - 12 * G.blur(c, 1.4))
    for _ in range(14):                                                   # gaff scars: little slits
        x, y = rng.uniform(4, S - 4), rng.uniform(4, S - 8)
        img = G.add_lum(img, -30 * G.box((S, S), x, y, x + 1.2, y + rng.uniform(3, 6), 0.4))
    for _ in range(5):                                                    # old staples, nail heads
        img = _nail(img, rng.uniform(8, S - 8), rng.uniform(8, S - 8), rng, r=1.2, rust=0.8)
    img = G.add_lum(img, rng.normal(0, 3, (S, S)))
    G.save_png(img, _blk("l39_pole_wood"))

    r = np.hypot(X - 64, Y - 64)
    top = G.fill((66, 52, 40), (S, S))
    top = G.add_lum(top, 4 * np.sin(r / 1.9 + 0.6 * G.fnoise(rng, beta=2.6)) + rng.normal(0, 3, (S, S)))
    ang = np.arctan2(Y - 64, X - 64)
    for a0 in (-2.4, -0.3, 1.6):
        da = np.abs(np.angle(np.exp(1j * (ang - a0))))
        top = G.add_lum(top, -36 * G.smoothstep(0.04, 0.0, da) * G.smoothstep(4, 18, r))
    top = G.over(top, np.array([34, 26, 20]), G.smoothstep(40, 64, r) * 0.7)
    G.save_png(top, _blk("l39_pole_top"))

    rng = G.rng_for(39114)
    arm = _grey_wood(rng, False, (134, 130, 122), checks=6)
    arm = _knots(arm, rng, 2, vertical=False)
    arm = G.over(arm, np.array([86, 74, 60]), G.smoothstep(0.8, 1.8, G.fnoise(rng, beta=1.6, aniso=(6, 1))) * 0.3)
    for _ in range(5):
        arm = G.mark_drip(arm, rng, (98, 70, 50), a=0.18, length=(20, 40))
    G.save_png(arm, _blk("l39_crossarm"))

    rng = G.rng_for(39115)
    steel = G._galvanised(rng, (104, 106, 108))
    steel = G.over(steel, np.array([116, 70, 44]), G.smoothstep(1.0, 2.0, G.fnoise(rng, beta=1.8)) * 0.6)
    G.save_png(steel, _blk("l39_pole_steel"))

    # Insulator glass, mapped whole onto each small box: three drip skirts (dark rings), a wire
    # groove, a bright vertical glint and tiny bubbles. Albedo kept fairly bright: it's glass.
    rng = G.rng_for(39116)
    ins = G.fill((78, 146, 118), (S, S))
    ins = G.add_lum(ins, G.fnoise(rng, beta=2.6) * 6 + 18 * np.exp(-((X - 40) / 9) ** 2)
                    + 40 * np.exp(-((X - 36) / 3) ** 2), (0.8, 1.0, 0.95))
    ins = G.add_lum(ins, -30 * np.exp(-((X - 4) / 6) ** 2) - 30 * np.exp(-((X - 124) / 6) ** 2))
    for y in (28, 60, 98):
        ins = G.add_lum(ins, -34 * np.exp(-((Y - y) / 3.0) ** 2) + 14 * np.exp(-((Y - y - 5) / 2.0) ** 2))
    bub = (rng.random((S, S)) < 0.006)
    ins = G.add_lum(ins, 40 * G.blur(bub.astype(float), 0.7))
    ins = G.over(ins, np.array([60, 70, 60]), G.smoothstep(1.0, 2.0, G.fnoise(rng, beta=2.0)) * 0.35)   # grime
    G.save_png(ins, _blk("l39_insulator"))


def tex_l39_props_transformer() -> None:
    """Pole transformer: grey painted can with a top seam band, rust running down from the lid,
    stencilled kVA rating; its lid, and brown porcelain bushings."""
    rng = G.rng_for(39117)
    X, Y = G.grid()
    img = G._smooth_concrete(rng, (122, 126, 126), mottle=3.5, fine=0.9)
    img = G.add_lum(img, G.fnoise(rng, beta=1.2, aniso=(1, 12), kmin=2) * 5)
    for y in (36, 104):                                                    # welded seams / bands
        img = G.add_lum(img, -26 * np.exp(-((Y - y) / 1.2) ** 2) + 12 * np.exp(-((Y - y - 2.5) / 1.0) ** 2))
    for _ in range(9):
        img = G.mark_drip(img, rng, (112, 70, 42), a=0.35, length=(30, 80))
    img = G._weather(img, rng, dirt=0.3, scratches=4)
    G.save_png(img, _blk("l39_transformer"))
    # The front (only the middle slab's north face uses it): the stencilled rating.
    front = G.over(img, np.array([196, 196, 186]), G._text("25", 64, 72, 22, maxw=28) * 0.6)
    front = G.over(front, np.array([196, 196, 186]), G._text("KVA", 64, 88, 10, maxw=28) * 0.5)
    G.save_png(front, _blk("l39_transformer_front"))

    rng = G.rng_for(39118)
    r = np.hypot(X - 64, Y - 64)
    lid = G._smooth_concrete(rng, (112, 116, 116), mottle=3.0, fine=0.9)
    lid = G.add_lum(lid, -18 * np.exp(-((r - 54) / 2.0) ** 2) + 8 * np.exp(-((r - 50) / 2.0) ** 2))
    lid = G.over(lid, np.array([110, 68, 40]), G.smoothstep(0.8, 1.8, G.fnoise(rng, beta=1.8)) * 0.5)
    lid = G.over(lid, np.array([70, 70, 66]), G.smoothstep(4, 3, r))
    G.save_png(G._weather(lid, rng, dirt=0.35, scratches=3), _blk("l39_transformer_top"))

    rng = G.rng_for(39119)
    bus = G.fill((90, 54, 36), (S, S))
    bus = G.add_lum(bus, G.fnoise(rng, beta=2.4) * 5 + 30 * np.exp(-((X - 44) / 6) ** 2), (1.0, 0.9, 0.8))
    bus = G.add_lum(bus, -36 * (np.abs(G.wrapd(Y, 32)) < 3) - 20 * np.exp(-((X - 120) / 10) ** 2))
    G.save_png(bus, _blk("l39_bushing"))


def tex_l39_props_lattice() -> None:
    """Rusty angle-steel tower lattice (cutout): the angle legs on both side edges, half a strut at
    the top and bottom (two stacked blocks make one whole strut), X bracing with gusset plates and
    bolts; the top/bottom texture is the horizontal frame ring with one diagonal."""
    rng = G.rng_for(39120)
    X, Y = G.grid()
    steel = _rust(rng, (74, 70, 68), amount=0.75)
    steel = G.add_lum(steel, G.fnoise(rng, beta=1.4, aniso=(1, 10), kmin=2) * 6)
    t = 6.0
    legs = (X < 11) | (X > S - 11)
    struts = (Y < 4) | (Y > S - 4)
    diag = (np.abs(X - Y) < t / 2 * SQ2) | (np.abs(X + Y - S) < t / 2 * SQ2)
    gus = np.zeros((S, S), bool)
    for cx, cy in ((11, 11), (S - 11, 11), (11, S - 11), (S - 11, S - 11)):
        gus |= (np.abs(X - cx) + np.abs(Y - cy) < 11) & (np.abs(X - S / 2) > S / 2 - 22) & (np.abs(Y - S / 2) > S / 2 - 22)
    gus |= np.hypot(X - 64, Y - 64) < 7
    solid = legs | struts | diag | gus
    # Shade: the angle's outer flange catches light, the inside corner is dark.
    lum = np.zeros((S, S))
    lum += np.where(legs, 14 * G.smoothstep(4, 0, np.minimum(X, S - X)) - 18 * G.smoothstep(7, 11, np.minimum(X, S - X)), 0)
    lum += np.where(diag & ~legs, -6, 0)
    img = G.add_lum(steel, lum)
    img = G.over(img, np.array([58, 56, 54]), (gus & ~legs) * 0.25)
    for cx, cy in ((5, 18), (S - 5, 18), (5, S - 18), (S - 5, S - 18), (64, 64), (18, 5), (S - 18, 5),
                   (18, S - 5), (S - 18, S - 5)):
        img = _nail(img, cx, cy, rng, r=1.8, rust=0.9)
    img = np.concatenate([img, np.where(solid, 255.0, 0.0)[..., None]], axis=2)
    G.save_png(img, _blk("l39_lattice"))

    rng = G.rng_for(39121)
    steel = _rust(rng, (74, 70, 68), amount=0.75)
    d = np.minimum(np.minimum(X, S - X), np.minimum(Y, S - Y))
    ring = d < 10
    dg = np.abs(X - Y) < 3 * SQ2
    lum = 12 * G.smoothstep(5, 0, d) - 16 * G.smoothstep(6, 10, d)
    img = G.add_lum(steel, np.where(ring, lum, 0))
    for cx, cy in ((5, 5), (S - 5, 5), (5, S - 5), (S - 5, S - 5)):
        img = _nail(img, cx, cy, rng, r=2.0, rust=0.9)
    img = np.concatenate([img, np.where(ring | dg, 255.0, 0.0)[..., None]], axis=2)
    G.save_png(img, _blk("l39_lattice_top"))


# =============================================================================
# Houses
# =============================================================================

def _white_paint(seed, col=(206, 206, 198)) -> np.ndarray:
    """White trim paint a few years past due: grain telegraphing through, hairline cracks along
    the grain, a few flakes off to grey wood, soot/rain grime."""
    rng = G.rng_for(seed)
    X, Y = G.grid()
    img = G.add_lum(G.fill(col, (S, S)), G.fnoise(rng, beta=1.6, aniso=(1, 12), kmin=2) * 5
                    + G.fnoise(rng, beta=0.8, aniso=(1, 12), kmin=8) * 2)
    c = _checks(rng, 7, True, (16, 46))
    img = G.add_lum(img, -20 * c - 4 * G.blur(c, 1.2))
    flakes = G.smoothstep(2.1, 2.5, G.blur(G.fnoise(rng, beta=1.3, kmin=4), 0.6))
    img = G.over(img, np.array([116, 110, 98]), flakes * 0.85)
    img = G.add_lum(img, -16 * np.clip(G.blur(flakes, 1.0) - flakes, 0, 1) * 3)       # flake edges
    grime = G.smoothstep(0.2, 1.8, G.fnoise(rng, beta=2.4))
    img = G.over(img, np.array([120, 118, 108]), grime * 0.22)
    img = G.add_lum(img, -G.smoothstep(0.6, 1.6, G.fnoise(rng, beta=1.2, aniso=(1, 10), kmin=2)) * 10)   # rain streaks
    return G.add_lum(img, rng.normal(0, 1.5, (S, S)))


def tex_l39_props_house() -> None:
    """House shell: white trim, dark asphalt shingles, soot-darkened chimney brick, fieldstone."""
    G.save_png(_white_paint(39122), _blk("l39_white_trim"))

    # Shingles: 3-tab asphalt, 16 px courses, tabs offset by half; some tabs torn off (black tar
    # paper shows), some curled (lit top edge), granule loss, black algae streaks, moss.
    rng = G.rng_for(39123)
    X, Y = G.grid()
    row = np.floor(Y / 16).astype(int)
    bx = (X + 16 * (row % 2) + 8 * (row % 4 == 1)) % S
    tab = np.floor(bx / 32).astype(int)
    tone = rng.normal(0, 7, (8, 4))[row % 8, tab % 4]
    img = G.fill((54, 54, 57), (S, S)) + tone[..., None]
    img = G.add_lum(img, rng.normal(0, 8, (S, S)) + G.blur(rng.normal(0, 5, (S, S)), 0.6))
    v = (Y % 16) / 16
    img = img * (0.78 + 0.27 * v)[..., None]
    img = G.add_lum(img, -30 * G.smoothstep(2.0, 0.4, Y % 16))
    img = G.add_lum(img, -26 * G.smoothstep(1.4, 0.4, np.abs(G.wrapd(bx, 32))) * (v > 0.15))
    torn = rng.random((8, 4)) < 0.1
    tm = torn[row % 8, tab % 4] & (v > 0.12)
    img = np.where(tm[..., None], G.add_lum(G.fill((30, 30, 32), (S, S)), rng.normal(0, 3, (S, S))), img)
    curl = rng.random((8, 4)) < 0.15
    cm = curl[row % 8, tab % 4]
    img = G.add_lum(img, (14 * G.smoothstep(12, 15.5, Y % 16) - 10 * G.smoothstep(4, 0, Y % 16)) * cm)
    bald = G.smoothstep(1.0, 1.8, G.fnoise(rng, beta=2.2))
    img = G.lerp(img, G.blur(img, 1.0) * 0.86, bald[..., None] * 0.7)
    streak = G.smoothstep(0.4, 1.6, G.fnoise(rng, beta=1.4, aniso=(1, 10), kmin=2))
    img = G.over(img, np.array([26, 28, 26]), streak * 0.45)
    moss = G.smoothstep(1.2, 2.0, G.fnoise(rng, beta=2.0)) * G.smoothstep(0.1, 0.6, v)
    img = G.over(img, np.array([58, 70, 44]), moss * 0.55)
    G.save_png(img, _blk("l39_shingles"))

    # Chimney: small 1900s common brick (10 courses), soot from the flue, white efflorescence,
    # some burnt/black headers, eroded mortar.
    rng = G.rng_for(39124)
    ch = S / 10
    row = np.floor(Y / ch).astype(int)
    bx = (X + 16 * (row % 2)) % S
    ci = np.floor(bx / 32).astype(int)
    tone = rng.normal(0, 9, (10, 4))[row % 10, ci % 4]
    burnt = (rng.random((10, 4)) < 0.15)[row % 10, ci % 4]
    img = G.fill((106, 56, 44), (S, S)) + tone[..., None] * np.array([1.0, 0.7, 0.6])
    img = np.where(burnt[..., None], img * 0.55, img)
    img = G.add_lum(img, rng.normal(0, 6, (S, S)) + G.fnoise(rng, beta=2.0, kmin=4) * 6)
    yy = Y - row * ch
    mortar = np.minimum(np.minimum(yy, ch - yy), np.abs(G.wrapd(bx, 32)))
    mortar_col = G.add_lum(G.fill((104, 100, 94), (S, S)), rng.normal(0, 6, (S, S)))
    img = G.over(img, mortar_col, G.smoothstep(1.5, 0.7, mortar))
    img = G.add_lum(img, -14 * G.smoothstep(2.8, 1.5, mortar) * (mortar > 1.5))
    soot = G.smoothstep(-0.6, 1.4, G.fnoise(rng, beta=2.0) + 0.6 * G.fnoise(rng, beta=1.3, aniso=(1, 6), kmin=2))
    img = G.over(img, np.array([24, 22, 22]), soot * 0.7)
    eff = G.smoothstep(1.2, 2.0, G.fnoise(rng, beta=1.8, kmin=3))
    img = G.over(img, np.array([170, 166, 156]), eff * 0.35)
    G.save_png(img, _blk("l39_chimney_brick"))

    G.save_png(_fieldstone(39125), _blk("l39_foundation"))


def _fieldstone(seed) -> np.ndarray:
    """Rounded field stones of mixed colour laid in thick grey mortar; seamless.

    The stones are cells of a wrap-around Voronoi pattern; the distance to the cell border tells
    how far inside a stone a pixel is, which gives both the mortar joint and a domed shading
    (lit from the upper left).
    """
    rng = G.rng_for(seed)
    X, Y = G.grid()
    n = 22
    pts = rng.uniform(0, S, (n, 2))
    # Relax the points a little so stones come out similar in size.
    for _ in range(4):
        for i in range(n):
            dx = G.wrapd(pts[:, 0] - pts[i, 0], S)
            dy = G.wrapd(pts[:, 1] - pts[i, 1], S)
            d = np.hypot(dx, dy) + 1e-6
            push = np.clip(32 - d, 0, None) / d
            push[i] = 0
            pts[i, 0] -= (dx * push).sum() * 0.05
            pts[i, 1] -= (dy * push).sum() * 0.05
        pts %= S
    Xw = X + 2.0 * G.fnoise(rng, beta=2.4) + 0.5 * G.fnoise(rng, beta=1.4, kmin=6)
    Yw = Y + 2.0 * G.fnoise(rng, beta=2.4) + 0.5 * G.fnoise(rng, beta=1.4, kmin=6)
    # Squash the cells a little vertically: field stones are laid flat side down.
    ds = np.stack([np.hypot(G.wrapd(Xw - px, S), 1.25 * G.wrapd(Yw - py, S)) for px, py in pts])
    order = np.argsort(ds, axis=0)
    d1 = np.take_along_axis(ds, order[:1], 0)[0]
    d2 = np.take_along_axis(ds, order[1:2], 0)[0]
    cell = order[0]
    edge = G.blur((d2 - d1) / 2, 1.0)
    pal = np.array([(132, 130, 126), (128, 112, 92), (84, 88, 94), (156, 150, 140), (118, 96, 78),
                    (96, 92, 86), (140, 134, 122), (108, 110, 104)], float)
    sc = pal[rng.integers(len(pal), size=n)] * rng.uniform(0.88, 1.1, (n, 1))
    img = sc[cell]
    img = G.add_lum(img, G.fnoise(rng, beta=1.6, kmin=3) * 6 + rng.normal(0, 4, (S, S)))
    speck = (rng.random((S, S)) < 0.06) * rng.uniform(-34, 24, (S, S))
    img = G.add_lum(img, G.blur(speck, 0.45))
    # Dome: a height that rises from the joint, lit from the upper left.
    h = np.sqrt(np.clip(edge - 2.0, 0, None) / 10.0).clip(0, 1)
    gx = np.gradient(h, axis=1)
    gy = np.gradient(h, axis=0)
    stone = G.smoothstep(1.8, 2.8, edge)
    img = G.add_lum(img, (-160 * (gx + gy) + 10 * h - 8) * stone)
    mortar = G.add_lum(G.fill((78, 76, 72), (S, S)), rng.normal(0, 6, (S, S)) + G.fnoise(rng, beta=2.0) * 5)
    img = G.over(img, mortar, 1 - stone)
    img = G.add_lum(img, -30 * G.smoothstep(3.2, 1.6, edge) * (1 - G.smoothstep(0.6, 1.6, edge)))   # deep joint shadow
    moss = G.smoothstep(0.7, 1.7, G.fnoise(rng, beta=2.2)) * G.smoothstep(3.5, 1.5, edge)
    img = G.over(img, np.array([50, 62, 40]), moss * 0.6)
    lich = G.smoothstep(1.3, 2.0, G.fnoise(rng, beta=1.8, kmin=3)) * stone
    img = G.over(img, np.array([150, 154, 136]), lich * 0.35)
    return G._weather(img, rng, dirt=0.2, scratches=0)


# ---- 6/6 sash windows --------------------------------------------------------
#
# The block's north face is drawn recessed 1.5 px behind a white casing and above a sill, so only
# the texture between px 12 and 116 shows: that area is the window opening. Inside it, the upper
# sash (outer track) overlaps the lower one at the meeting rail.

UPPER = (14, 14, 114, 64)       # x0, y0, x1, y1 of the upper sash (texture px)
LOWER = (14, 62, 114, 114)


def _pane_rects(sash, top_rail, bottom_rail, stile=7, muntin=3):
    x0, y0, x1, y1 = sash
    gx0, gx1 = x0 + stile, x1 - stile
    gy0, gy1 = y0 + top_rail, y1 - bottom_rail
    pw = (gx1 - gx0 - 2 * muntin) / 3
    ph = (gy1 - gy0 - muntin) / 2
    out = []
    for r in range(2):
        for c in range(3):
            px = gx0 + c * (pw + muntin)
            py = gy0 + r * (ph + muntin)
            out.append((px, py, px + pw, py + ph))
    return out


def _sash_window(seed, lit: bool, inside: bool) -> np.ndarray:
    rng = G.rng_for(seed)
    X, Y = G.grid()
    frame = _white_paint(seed + 50, (214, 212, 202) if not inside else (210, 204, 186))
    upper = _pane_rects(UPPER, 6, 5)
    lower = _pane_rects(LOWER, 5, 9)
    panes = upper + lower
    # Glass: dark, a faint cold reflection of the foggy sky, each old pane a little different.
    glass = G.fill((14, 16, 22), (S, S))
    sky = np.exp(-((X - Y * 0.7 - 10) / 16) ** 2) * 16 + np.exp(-((X - Y * 0.7 - 58) / 5) ** 2) * 9
    glass = G.add_lum(glass, sky + G.smoothstep(S, 0, Y) * 10 + G.fnoise(rng, beta=2.8) * 2, (0.85, 0.92, 1.0))
    if lit and not inside:
        # Warm lamplight from deep in the room, a cream shade pulled half way down.
        room = G.fill((122, 82, 44), (S, S))
        room = G.add_lum(room, 40 * np.exp(-(((X - 44) / 40) ** 2 + ((Y - 112) / 36) ** 2)), (1.0, 0.8, 0.5))
        room = G.add_lum(room, -30 * G.smoothstep(40, 0, X) - 30 * G.smoothstep(88, S, X))   # curtains at the sides
        glass = room
        shade_y = 86
        shade = G.fill((206, 170, 112), (S, S))
        shade = G.add_lum(shade, 26 * np.exp(-(((X - 50) / 50) ** 2 + ((Y - 90) / 50) ** 2)) - 20 * (1 - Y / shade_y),
                          (1.0, 0.85, 0.6))
        shade = G.add_lum(shade, G.fnoise(rng, beta=1.2, aniso=(1, 6), kmin=3) * 5 + np.sin(Y * 1.6) * 1.5)
        shade = G.add_lum(shade, -18 * np.exp(-((X - 30) / 5) ** 2) - 14 * np.exp(-((X - 96) / 6) ** 2))   # soft folds
        hem = (Y > shade_y - 3) & (Y < shade_y)
        shade = np.where(hem[..., None], shade * 0.7, shade)
        glass = np.where((Y < shade_y)[..., None], shade, glass)
        ring = np.abs(np.hypot(X - 64, Y - (shade_y + 3)) - 2.2) < 0.8
        glass = G.over(glass, np.array([90, 70, 40]), ring * 0.9)
        frame = frame * np.array([0.46, 0.44, 0.42])                  # full-bright block: keep the paint dim
    elif lit and inside:
        # Seen from the lamplit room: the shade's back, lit from the front; night glass below.
        shade_y = 86
        shade = G.fill((198, 180, 140), (S, S))
        shade = G.add_lum(shade, 18 * np.exp(-(((X - 70) / 60) ** 2 + ((Y - 40) / 60) ** 2)) + G.fnoise(rng, beta=1.2, aniso=(1, 6), kmin=3) * 4)
        shade = G.add_lum(shade, -16 * np.exp(-((X - 34) / 5) ** 2) - 12 * np.exp(-((X - 92) / 6) ** 2))
        hem = (Y > shade_y - 3) & (Y < shade_y)
        shade = np.where(hem[..., None], shade * 0.75, shade)
        refl = G.add_lum(glass, 70 * np.exp(-(((X - 84) / 6) ** 2 + ((Y - 100) / 5) ** 2)), (1.0, 0.8, 0.5))
        glass = np.where((Y < shade_y)[..., None], shade, refl)
        frame = frame * np.array([0.72, 0.66, 0.56])
    elif inside:
        glass = glass * 0.7
    pm = np.zeros((S, S))
    rects = []
    for i, (x0, y0, x1, y1) in enumerate(panes):
        b = G.box((S, S), x0, y0, x1, y1)
        tint = rng.normal(0, 3)
        g = glass + tint
        # Dust and grime gather along the pane's lower edge; old glass is a little wavy.
        g = G.over(g, np.array([70, 70, 66]) if not lit else np.array([120, 96, 70]),
                   G.smoothstep(y1 - 7, y1, Y) * 0.35 * (0.6 + 0.4 * rng.random()))
        pm = np.maximum(pm, b)
        rects.append((b, g))
    win = frame.copy()
    for b, g in rects:
        win = G.over(win, g, b)
    # Shadows of the muntins and rails on the glass (light from the upper left).
    sh = np.clip(np.roll(np.roll(1 - pm, 2, 0), 2, 1) * pm, 0, 1)
    win = G.add_lum(win, -14 * sh)
    # Sash outlines: the upper sash sits in front, so the lower sash's top rail is shadowed.
    for (x0, y0, x1, y1), dk in ((LOWER, -16), (UPPER, -22)):
        edge = G.box((S, S), x0 - 1, y0 - 1, x1 + 1, y1 + 1) - G.box((S, S), x0, y0, x1, y1)
        win = G.add_lum(win, dk * edge)
    win = G.add_lum(win, -20 * G.box((S, S), UPPER[0], UPPER[3], UPPER[2], UPPER[3] + 2))
    # Jamb shadow round the opening.
    jamb = G.box((S, S), 12, 12, 116, 116) - G.box((S, S), 14, 14, 114, 114)
    win = G.add_lum(win, -28 * jamb)
    if inside:
        # Sash lock on the meeting rail, two lift handles on the lower sash.
        lock = G.box((S, S), 58, 60, 70, 66)
        win = G.over(win, np.array([150, 120, 64]), lock)
        for hx in (36, 92):
            win = G.over(win, np.array([140, 112, 60]), G.box((S, S), hx - 3, 107, hx + 3, 110))
    else:
        # One cracked pane, cobweb in a corner.
        x0, y0, x1, y1 = lower[3]
        crack = G.pil_mask(lambda d, k: d.line([(x * k, y * k) for x, y in
                                                [(x0 + 3, y0 + 2), (x0 + 11, y0 + 8), (x0 + 15, y1 - 1)]],
                                               fill=255, width=k))
        win = G.add_lum(win, (60 if not lit else -40) * crack)
        x0, y0, x1, y1 = upper[2]
        web = np.exp(-np.hypot(X - x1, Y - y0) / 6) * (np.sin(np.arctan2(Y - y0, X - x1) * 9) > 0.7)
        win = G.add_lum(win, 30 * web * G.box((S, S), x0, y0, x1, y1))
    return win


def tex_l39_props_windows() -> None:
    """l39_sash_window(_lit) outside faces and the same windows seen from inside (_inside)."""
    G.save_png(_sash_window(39130, False, False), _blk("l39_sash_window"))
    G.save_png(_sash_window(39131, False, True), _blk("l39_sash_window_inside"))
    G.save_png(_sash_window(39132, True, False), _blk("l39_sash_window_lit"))
    G.save_png(_sash_window(39133, True, True), _blk("l39_sash_window_lit_inside"))


def tex_l39_props_door() -> None:
    """Light-blue 1900s front door, 2 blocks tall: a four-lite window with a faint lace curtain in
    the top half over two short panels, two tall panels below the lock rail, brass knob and
    keyhole plate. Paint faded and peeling to grey wood, grime and kick marks at the bottom."""
    rng = G.rng_for(39134)
    shp = (2 * S, S)
    X, Y = G.grid(shp)
    col = np.array([146, 172, 188])
    img = G._wood(rng, col, horizontal=False, shape=shp) * 0.35 + G.fill(col, shp) * 0.65
    img = G.add_lum(img, G.fnoise(rng, shp, beta=2.4) * 4)
    panels = [(20, 100, 60, 124), (68, 100, 108, 124), (20, 150, 60, 236), (68, 150, 108, 236)]
    for x0, y0, x1, y1 in panels:
        outer, inner = G.box(shp, x0, y0, x1, y1), G.box(shp, x0 + 5, y0 + 5, x1 - 5, y1 - 5)
        bevel = outer - inner
        lit = ((X < x0 + 5) | (Y < y0 + 5)) & ~((X > x1 - 5) | (Y > y1 - 5))
        img = G.add_lum(img, bevel * np.where(lit, 20, -26) + 6 * inner)
        img = G.add_lum(img, -20 * (G.box(shp, x0 - 1.5, y0 - 1.5, x1 + 1.5, y1 + 1.5) - outer))   # moulding line
    # Window: 4 lites, dark glass, a pale lace curtain hanging inside.
    wx0, wy0, wx1, wy1 = 24, 18, 104, 88
    glass = G.fill((16, 18, 24), shp)
    lace = (np.sin(X * 1.1) * np.sin(Y * 1.1) > 0.3) * 0.5 + 0.5 * (np.sin((X + Y) * 0.35) > 0.6)
    glass = G.add_lum(glass, 30 * lace * G.smoothstep(0, 40, Y - wy0 + 10) + 14 * np.exp(-((X - Y * 0.8 - 10) / 8) ** 2),
                      (0.95, 0.95, 1.0))
    glass = G.add_lum(glass, -14 * np.exp(-((X - 64) / 16) ** 2))           # the curtain's parting
    lites = np.zeros(shp)
    for x0, x1 in ((wx0, 62), (66, wx1)):
        for y0, y1 in ((wy0, 51), (55, wy1)):
            lites = np.maximum(lites, G.box(shp, x0, y0, x1, y1))
    img = G.add_lum(img, -24 * G.box(shp, wx0 - 4, wy0 - 4, wx1 + 4, wy1 + 4) * (1 - lites) * 0.5)
    img = G.over(img, glass, lites)
    img = G.add_lum(img, -18 * np.clip(np.roll(np.roll(1 - lites, 2, 0), 2, 1) * lites, 0, 1))
    # Peeling paint, grey wood under it; grime low down and kick scuffs.
    chips = G.smoothstep(1.6, 2.2, G.fnoise(rng, shp, beta=1.2, kmin=4))
    img = G.over(img, np.array([118, 112, 100]), chips * 0.8 * (1 - lites))
    img = G._weather(img, rng, dirt=0.22, scratches=5)
    img = G.over(img, np.array([70, 66, 56]), G.smoothstep(0.86, 1.0, Y / (2 * S)) * 0.5)
    # Knob and keyhole plate on the latch side.
    plate = G.box(shp, 98.5, 128, 109.5, 158, 0.7)
    img = G.add_lum(img, -24 * (G.box(shp, 97.5, 127, 110.5, 159, 0.7) - plate))
    img = G.over(img, np.array([96, 76, 44]), plate)
    img = G.add_lum(img, 18 * G.box(shp, 99, 128.5, 100.5, 157.5, 0.4))
    img = G.over(img, np.array([16, 12, 10]), G.box(shp, 103.3, 148, 104.7, 153, 0.4))
    img = G.over(img, np.array([16, 12, 10]), G.supersample(shp, lambda x, y: np.hypot(x - 104, y - 147.5) < 1.5))
    kd = np.hypot(X - 104, Y - 138)
    knob = G.supersample(shp, lambda x, y: np.hypot(x - 104, y - 138) < 5.5)
    brass = G.fill((170, 136, 70), shp) * (1.15 - 0.5 * G.smoothstep(0, 6, np.hypot(X - 102, Y - 136)))[..., None]
    img = G.over(img, brass, knob)
    img = G.add_lum(img, -30 * np.clip(G.blur(knob, 1.0) - knob, 0, 1) * (Y > 138) * (kd < 8))
    img = G.over(img, np.array([236, 214, 160]), G.supersample(shp, lambda x, y: np.hypot(x - 102.2, y - 136.2) < 1.5))
    G.save_png(img[:S], _blk("l39_house_door_top"))
    G.save_png(img[S:], _blk("l39_house_door_bottom"))
    # Inventory icon: the whole door squeezed into 32x32 (door 16 wide, centred).
    door = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((16, 32), Image.LANCZOS)
    icon = np.zeros((32, 32, 4))
    icon[:, 8:24, :3] = np.asarray(door, float)
    icon[:, 8:24, 3] = 255
    icon[:, 8, :3] *= 0.6
    icon[:, 23, :3] *= 0.6
    G.save_png(icon, _itm("l39_house_door"))


# =============================================================================
# Park and M.E.G. camp
# =============================================================================

def tex_l39_props_park() -> None:
    """Bench slats (grey wood with flakes of old green paint), black cast iron, gazebo floor
    planks, the rusty swing-set pipe and a rusty swing chain (cutout)."""
    rng = G.rng_for(39140)
    X, Y = G.grid()
    slat = _grey_wood(rng, False, (120, 116, 108), checks=6)
    paint = G.smoothstep(0.3, 1.0, G.fnoise(rng, beta=1.4, aniso=(4, 1), kmin=3))
    slat = G.over(slat, np.array([58, 80, 64]), paint * 0.75)
    slat = G.add_lum(slat, 8 * paint * G.smoothstep(0.9, 1.0, paint))
    for y in range(8, S, 16):
        for x in (10, 118):
            slat = _nail(slat, x, y, rng, r=1.8, rust=0.8)
    G.save_png(slat, _blk("l39_bench_slat"))

    rng = G.rng_for(39141)
    iron = G._smooth_concrete(rng, (36, 36, 38), mottle=4.0, fine=1.6)
    iron = G.add_lum(iron, rng.normal(0, 4, (S, S)))
    iron = G.over(iron, np.array([96, 56, 36]), G.smoothstep(0.9, 1.9, G.fnoise(rng, beta=1.8)) * 0.6)
    iron = G.add_lum(iron, 10 * G.smoothstep(1.2, 2.0, G.fnoise(rng, beta=1.0, kmin=6)))
    G.save_png(iron, _blk("l39_cast_iron"))

    # Gazebo floor: unpainted grey boards along x, 16 px wide, cupped, nailed at the joists.
    rng = G.rng_for(39142)
    img = _grey_wood(rng, False, (112, 110, 104), checks=8)
    board = np.floor(Y / 16).astype(int)
    img = G.add_lum(img, rng.normal(0, 6, 8)[board % 8])
    yy = Y % 16
    img = G.add_lum(img, 6 * np.sin(np.pi * yy / 16) - 6)                 # cupping
    gap = np.abs(G.wrapd(Y, 16))
    img = G.add_lum(img, -46 * G.smoothstep(1.4, 0.4, gap))
    img = G.over(img, np.array([44, 56, 36]), G.smoothstep(2.2, 0.8, gap) * G.smoothstep(0.2, 1.2, G.fnoise(rng, beta=2.0)) * 0.6)
    offs = rng.integers(0, S, 8)
    for b in range(8):
        img = G.add_lum(img, -34 * G.smoothstep(1.2, 0.3, np.abs(G.wrapd(X - offs[b], S))) * (board == b))
        for jx in (8, 72):
            for dy in (4.5, 11.5):
                img = _nail(img, jx, b * 16 + dy, rng, r=1.3, rust=0.6)
    img = G.mark_damp(img, rng, size=(30, 22), dark=0.18)
    G.save_png(img, _blk("l39_porch_planks"))

    rng = G.rng_for(39143)
    pipe = _rust(rng, (70, 66, 62), amount=0.65)
    paint = G.smoothstep(0.4, 1.2, G.fnoise(rng, beta=1.8))
    pipe = G.over(pipe, np.array([70, 92, 80]), paint * 0.55)              # old green paint left
    G.save_png(pipe, _blk("l39_rusty_pipe"))

    # Chain: links along the column u 56..72 (block px 7..9), period 16 px (2 block px), so any
    # vertical slice of it tiles. Face-on links are rings, edge-on links thin bars.
    rng = G.rng_for(39144)
    prims = []
    for i in range(8):
        cy = i * 16 + 8
        c = np.array([92, 64, 46]) * rng.uniform(0.8, 1.15)
        if i % 2 == 0:
            prims.append(("dot", [(64, cy)], 7.0, _col(c)))
            prims.append(("dot", [(64, cy)], 3.4, (0, 0, 0, 0)))
        else:
            prims.append(("line", [(64, cy - 9.5), (64, cy + 9.5)], 4.2, _col(c * 0.85)))
    canvas = Image.new("RGBA", (S * 4, S * 4), (0, 0, 0, 0))
    d = ImageDraw.Draw(canvas)
    for kind, pts, wd, col in prims:
        for oy in (-S, 0, S):
            if kind == "dot":
                (x, y), r = pts[0], wd
                d.ellipse(((x - r) * 4, (y + oy - r * 1.35) * 4, (x + r) * 4, (y + oy + r * 1.35) * 4), fill=col)
            else:
                d.line([(x * 4, (y + oy) * 4) for x, y in pts], fill=col, width=int(wd * 4))
    ch = _resolve(canvas, 4)
    rust = _rust(rng, (78, 64, 54), amount=0.8)
    ch[..., :3] = rust * 0.6 + ch[..., :3] * 0.4
    ch[..., :3] = G.add_lum(ch[..., :3], 18 * np.exp(-((X - 60) / 2.0) ** 2))
    G.save_png(ch, _blk("l39_chain"))

    # Swing inventory icon: two chains and a plank seat.
    icon = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(icon)
    for x in (26, 102):
        for y in range(4, 92, 10):
            d.ellipse((x - 4, y, x + 4, y + 11), outline=(98, 70, 52, 255), width=3)
    d.rectangle((14, 90, 114, 106), fill=(118, 114, 104, 255))
    d.rectangle((14, 102, 114, 106), fill=(82, 78, 72, 255))
    d.rectangle((14, 90, 114, 92), fill=(146, 142, 132, 255))
    G.save_png(_resolve(icon, 4), _itm("l39_swing"))


def _stencil(text, cx, cy, px, maxw, rng, wear=0.35) -> np.ndarray:
    """Spray-stencilled lettering: bridges cut through the letters, soft overspray, worn patches."""
    m = G._text(text, cx, cy, px, maxw)
    X, Y = G.grid()
    bridges = (np.abs(G.wrapd(X - cx, max(4, px * 0.5))) < 0.7) & (np.abs(Y - cy) < px * 0.12)
    m = m * ~bridges
    m = np.maximum(m, G.blur(m, 1.2) * 0.35)
    worn = G.smoothstep(0.2, 1.4, G.fnoise(rng, beta=1.4, kmin=4))
    return np.clip(m * (1 - worn * wear) * (0.85 + 0.15 * rng.random((S, S))), 0, 1)


def _crate_face(seed, top: bool) -> np.ndarray:
    rng = G.rng_for(seed)
    X, Y = G.grid()
    olive = np.array([84, 90, 58])
    wood = G._wood(rng, np.array([126, 100, 72]), horizontal=True)
    img = G.add_lum(G.fill(olive, (S, S)), G.fnoise(rng, beta=1.6, aniso=(10, 1), kmin=2) * 5 + rng.normal(0, 2.5, (S, S)))
    # Boards: 3 inner planks, a 14 px frame of cleats round the edge.
    frame = (X < 14) | (X > S - 14) | (Y < 14) | (Y > S - 14)
    plank_lines = [47, 80] if not top else [54, 64, 74]
    for y in plank_lines:
        img = G.add_lum(img, -30 * G.smoothstep(1.4, 0.4, np.abs(Y - y)) * ~frame)
    edge = G.box((S, S), 14, 14, S - 14, S - 14) - G.box((S, S), 15.5, 15.5, S - 15.5, S - 15.5)
    img = G.add_lum(img, -34 * edge)
    img = G.add_lum(img, 8 * frame * G.smoothstep(2, 0, np.minimum(np.minimum(X, S - X), np.minimum(Y, S - Y))))
    img = G.add_lum(img, -24 * G.smoothstep(2.0, 0.5, np.minimum(np.minimum(X, S - X), np.minimum(Y, S - Y))))
    chips = G.smoothstep(1.5, 2.1, G.fnoise(rng, beta=1.2, kmin=4))
    img = G.over(img, wood, chips * 0.85)
    for cx, cy in ((7, 7), (S - 7, 7), (7, S - 7), (S - 7, S - 7), (7, 64), (S - 7, 64), (64, 7), (64, S - 7)):
        img = _nail(img, cx, cy, rng, r=1.6, rust=0.9)
    cream = np.array([204, 196, 164])
    if top:
        img = G.over(img, cream, _stencil("M.E.G.", 64, 46, 18, 80, rng, 0.5) * 0.8)
        img = G.over(img, cream, G._text("LVL 39  /  SURVEY", 64, 70, 8, maxw=86) * 0.55)
        ring = np.abs(np.hypot(X - 84, Y - 92) - 15) < 1.6
        img = G.add_lum(img, -18 * ring * G.smoothstep(0, 1, G.fnoise(rng, beta=1.4)))     # a mug ring
        img = G.mark_stain(img, rng, (60, 62, 44), size=(12, 10), a=0.25)
    else:
        img = G.over(img, cream, _stencil("M.E.G.", 64, 50, 28, 92, rng) * 0.85)
        img = G.over(img, cream, G._text("SURVEY TEAM 39-C", 64, 76, 9, maxw=90) * 0.6)
        img = G.over(img, cream, G._text("LOT 0412   NET 18KG", 64, 92, 7, maxw=90) * 0.5)
        # Faded survey spray paint: an orange arrow and tick marks on the frame.
        arrow = G.pil_mask(lambda d, k: d.polygon([(100 * k, 104 * k), (112 * k, 104 * k), (112 * k, 112 * k),
                                                   (118 * k, 112 * k), (106 * k, 122 * k), (94 * k, 112 * k),
                                                   (100 * k, 112 * k)], fill=255))
        img = G.over(img, np.array([186, 104, 52]), arrow * 0.6 * (1 - 0.5 * G.smoothstep(0, 1.5, G.fnoise(rng, beta=1.6))))
        for i in range(4):
            img = G.over(img, np.array([200, 196, 180]), G.box((S, S), 20 + i * 4, 104, 21.2 + i * 4, 116, 0.4) * 0.7)
        img = G.over(img, np.array([200, 196, 180]), G.pil_mask(lambda d, k: d.line((18 * k, 116 * k, 36 * k, 104 * k), fill=255, width=k)) * 0.7)
    img = G._weather(img, rng, dirt=0.3, scratches=6)
    return _dirty_bottom(img, rng, 0.85, (60, 56, 42), 0.35) if not top else img


def tex_l39_props_camp() -> None:
    """M.E.G. camp: olive tent canvas, the stencilled crate (side, lid, bottom), the dead lantern
    (olive paint over rust; dusty unlit globe glass with a dead wick), the survey clipboard."""
    rng = G.rng_for(39150)
    X, Y = G.grid()
    cv = G._canvas(rng, (84, 88, 58), fade=0.18)
    for x in (62, 66):                                                    # a sewn panel seam
        cv = G.add_lum(cv, -18 * np.exp(-((X - x) / 0.9) ** 2) * ((Y % 6) < 4))
    cv = G.add_lum(cv, -10 * np.exp(-((X - 64) / 3) ** 2))
    for _ in range(3):
        cv = G.mark_stain(cv, rng, (58, 62, 40), size=(16, 14), a=0.25)
    mild = (rng.random((S, S)) < 0.01) * G.smoothstep(0.4, 1.4, G.fnoise(rng, beta=2.0))
    cv = G.add_lum(cv, -40 * G.blur(mild, 0.7))
    G.save_png(cv, _blk("l39_canvas"))

    G.save_png(_crate_face(39151, False), _blk("l39_meg_crate"))
    G.save_png(_crate_face(39152, True), _blk("l39_meg_crate_top"))
    rng = G.rng_for(39153)
    bottom = G._wood(rng, np.array([96, 82, 62]), horizontal=True)
    bottom = G.add_lum(bottom, -30 * G.smoothstep(1.4, 0.4, np.abs(G.wrapd(G.grid()[1], 32))))
    G.save_png(G.over(bottom, np.array([56, 50, 38]), G.smoothstep(0.0, 1.5, G.fnoise(rng, beta=2.0)) * 0.6), _blk("l39_meg_crate_bottom"))

    rng = G.rng_for(39154)
    metal = G._smooth_concrete(rng, (62, 68, 50), mottle=3.0, fine=1.0)
    metal = G.over(metal, np.array([112, 66, 40]), G.smoothstep(0.7, 1.7, G.fnoise(rng, beta=1.8)) * 0.7)
    metal = G.add_lum(metal, -20 * G.smoothstep(1.4, 2.0, G.fnoise(rng, beta=1.2, kmin=5)))
    G.save_png(G._weather(metal, rng, dirt=0.25, scratches=6), _blk("l39_lamp_metal"))

    rng = G.rng_for(39155)
    gl = G.fill((58, 60, 54), (S, S))
    gl = G.add_lum(gl, G.fnoise(rng, beta=2.4) * 4)
    wick = G.box((S, S), 54, 70, 74, 128) * 1.0
    gl = G.over(gl, np.array([30, 28, 26]), wick * 0.8)                   # burner & dead wick inside
    gl = G.over(gl, np.array([18, 16, 14]), G.box((S, S), 60, 56, 68, 72) * 0.8)
    gl = G.over(gl, np.array([20, 18, 18]), G.smoothstep(40, 0, Y) * 0.7)  # soot at the top
    gl = G.over(gl, np.array([150, 146, 128]), G.smoothstep(-0.2, 1.4, G.fnoise(rng, beta=2.2)) * 0.3)   # dust film
    gl = G.add_lum(gl, 60 * np.exp(-((X - 30) / 5) ** 2) * G.smoothstep(20, 50, Y), (0.95, 0.97, 1.0))      # glint
    gl = G.add_lum(gl, -26 * G.smoothstep(16, 0, np.minimum(X, S - X)))
    G.save_png(gl, _blk("l39_lamp_glass"))

    rng = G.rng_for(39156)
    board = G.fill((112, 86, 58), (S, S))
    board = G.add_lum(board, rng.normal(0, 6, (S, S)) + G.fnoise(rng, beta=2.0) * 6, (1.0, 0.85, 0.7))
    board = G.mark_stain(board, rng, (70, 52, 36), size=(24, 20), a=0.35)
    G.save_png(board, _blk("l39_clipboard"))
    rng = G.rng_for(39157)
    clip = G._galvanised(rng, (150, 152, 156))
    clip = G.add_lum(clip, 26 * np.exp(-((Y - 40) / 8) ** 2))
    clip = G.over(clip, np.array([110, 70, 44]), G.smoothstep(1.0, 2.0, G.fnoise(rng, beta=1.8)) * 0.5)
    G.save_png(clip, _blk("l39_clip"))

    for i in range(4):
        G.save_png(_survey_paper(39160 + i, i), _blk(f"l39_survey_paper_{i}"))

    # Inventory icon: brown board, damp page, steel clip.
    icon = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(icon)
    d.rounded_rectangle((22, 10, 106, 122), radius=6, fill=(112, 84, 56, 255))
    d.rectangle((30, 24, 98, 114), fill=(214, 208, 188, 255))
    for y in range(40, 108, 9):
        d.line((36, y, 36 + (y * 37) % 52 + 8, y), fill=(84, 84, 90, 255), width=3)
    d.rectangle((48, 6, 80, 26), fill=(160, 162, 166, 255))
    d.rectangle((56, 2, 72, 8), fill=(120, 122, 126, 255))
    G.save_png(_resolve(icon, 4), _itm("l39_survey_note"))


def _survey_paper(seed, kind) -> np.ndarray:
    """One water-stained survey page (mapped onto an 8 x 10 px sheet, so drawn a bit narrow).

    0: printed form, pencilled entries; 1: hand-drawn map (houses, the mill, a path, an X);
    2: tally marks and lines heavily crossed out; 3: a sketch of a house and fence, notes below.
    """
    rng = G.rng_for(seed)
    X, Y = G.grid()
    img = G.fill((216, 210, 192), (S, S))
    img = G.add_lum(img, G.fnoise(rng, beta=2.2) * 5 + rng.normal(0, 2.5, (S, S)))
    pencil = []

    def scrib(x0, y0, x1, size=3.0):
        # Handwriting: short "words" of looping strokes along a slightly wavy baseline.
        x = x0
        while x < x1 - 6:
            wl = rng.uniform(6, 18)
            pts = []
            for t in np.linspace(0, 1, int(wl)):
                pts.append((x + t * wl, y0 + size * 0.5 * np.sin(t * wl * 1.7 + rng.normal(0, 0.4)) + rng.normal(0, 0.3)))
            pencil.append(pts)
            x += wl + rng.uniform(3, 6)

    if kind == 0:
        for y in range(30, 124, 10):                                      # printed blue rules
            img = G.over(img, np.array([150, 170, 196]), G.box((S, S), 6, y, 122, y + 1, 0.4) * 0.7)
        img = G.over(img, np.array([60, 60, 70]), G._text("M.E.G. FIELD SURVEY", 64, 10, 9, maxw=110) * 0.85)
        img = G.over(img, np.array([60, 60, 70]), G.box((S, S), 6, 18, 122, 19.2, 0.4) * 0.8)
        for y in range(28, 120, 10):
            scrib(8, y - 3, rng.uniform(70, 120))
    elif kind == 1:
        for (x0, y0, w, h) in ((14, 70, 14, 12), (36, 74, 12, 10), (16, 92, 12, 12), (90, 96, 14, 12)):
            pencil.append([(x0, y0), (x0 + w, y0), (x0 + w, y0 + h), (x0, y0 + h), (x0, y0)])
            pencil.append([(x0 - 1, y0), (x0 + w / 2, y0 - 6), (x0 + w + 1, y0)])
        pencil.append([(4, 10), (124, 10), (124, 40), (4, 40), (4, 10)])     # the mill, its windows
        for x in range(10, 120, 9):
            pencil.append([(x, 16), (x + 4, 16), (x + 4, 34), (x, 34), (x, 16)])
        path = _wander(rng, 6, 120, -0.3, 6, 20, 0.15)
        for i in range(0, len(path) - 1, 2):
            pencil.append([path[i], path[i + 1]])
        pencil.append([(96, 56), (106, 66)])
        pencil.append([(106, 56), (96, 66)])
        scrib(60, 116, 124)
    elif kind == 2:
        for g in range(5):
            gx, gy = 10 + (g % 3) * 40, 14 + (g // 3) * 24
            for t in range(4):
                pencil.append([(gx + t * 5, gy), (gx + t * 5 + 1, gy + 14)])
            pencil.append([(gx - 2, gy + 12), (gx + 19, gy + 2)])
        for y in range(70, 124, 9):
            scrib(8, y, 120)
        for _ in range(3):                                                # angry crossing-out
            y = rng.uniform(70, 120)
            pencil.append(_wander(rng, 6, y, rng.normal(0, 0.1), 6, 19, 0.5))
    else:
        pencil.append([(30, 70), (30, 36), (64, 14), (98, 36), (98, 70), (30, 70)])
        for wx, wy in ((40, 42), (76, 42)):
            pencil.append([(wx, wy), (wx + 12, wy), (wx + 12, wy + 14), (wx, wy + 14), (wx, wy)])
        pencil.append([(58, 70), (58, 52), (70, 52), (70, 70)])
        pencil.append([(4, 78), (124, 78)])
        for x in range(6, 124, 6):
            pencil.append([(x, 78), (x, 70), (x + 1.5, 67), (x + 3, 70), (x + 3, 78)])
        for y in range(90, 124, 9):
            scrib(8, y, 120)
    lines = [(p, rng.uniform(0.8, 1.2)) for p in pencil]
    pm = G.pil_mask(lambda d, k: [d.line([(x * k, y * k) for x, y in p], fill=255, width=max(1, int(w * k))) for p, w in lines])
    pm = pm * (0.7 + 0.3 * G.smoothstep(-1, 1, G.fnoise(rng, beta=1.6)))
    img = G.over(img, np.array([70, 70, 76]), np.clip(pm, 0, 1) * 0.8)
    img = G.over(img, np.array([90, 88, 90]), G.blur(pm, 2.0) * 0.25)      # smudged graphite
    for _ in range(2):
        img = G.mark_stain(img, rng, (176, 160, 120), size=(20, 18), a=0.35)
    img = G.mark_damp(img, rng, size=(30, 20), dark=0.1, y_range=(0.6, 1.0))
    edge = np.minimum(np.minimum(X, S - X), np.minimum(Y, S - Y))
    return G.add_lum(img, -14 * G.smoothstep(4, 0, edge))


# =============================================================================
# Yard props
# =============================================================================

def _wheel(rng, kind) -> np.ndarray:
    """Cut-out wheel seen from the side, filling the texture.

    "cart": wooden spoked wheel (iron tyre, wooden felloe, 10 spokes, iron-banded hub).
    "barrow": solid rubber tyre on a pressed-steel disc with four holes.
    """
    X, Y = G.grid()
    r = np.hypot(X - 64, Y - 64)
    a = np.arctan2(Y - 64, X - 64)
    if kind == "cart":
        img = _grey_wood(rng, False, (112, 108, 100), checks=4)
        tyre = (r >= 57) & (r < 63.5)
        felloe = (r >= 47) & (r < 57)
        spoke_d = np.abs(np.angle(np.exp(1j * 10 * a))) / 10 * r          # distance to the nearest spoke line
        spokes = (spoke_d < 3.6) & (r >= 13) & (r < 48)
        hub = r < 14
        img = G.add_lum(img, -14 * felloe * G.smoothstep(52, 47, r))
        img = np.where(spokes[..., None], G.add_lum(img, -10 * G.smoothstep(1.5, 3.6, spoke_d)), img)
        img = G.add_lum(img, -30 * ((np.abs(r - 47.5) < 1.0) | (np.abs(r - 57) < 0.8)))
        iron = _rust(rng, (70, 62, 56), amount=0.7)
        img = np.where(tyre[..., None], iron, img)
        img = np.where((hub & (r > 10))[..., None], iron * 0.9, img)
        img = np.where((r <= 10)[..., None], G.add_lum(img * 0.7, -6 * np.ones((S, S))), img)
        img = G.add_lum(img, -26 * (np.abs(r - 10) < 1.0))
        for k in range(10):                                                  # tyre bolts
            bx, by = 64 + 60 * np.cos(k * np.pi / 5 + 0.3), 64 + 60 * np.sin(k * np.pi / 5 + 0.3)
            img = _nail(img, bx, by, rng, r=1.6, rust=0.6)
        solid = tyre | felloe | spokes | hub
    else:
        rubber = G.add_lum(G.fill((34, 34, 36), (S, S)), rng.normal(0, 3, (S, S)) + G.fnoise(rng, beta=2.0) * 3)
        rubber = G.add_lum(rubber, -10 * (np.abs(np.angle(np.exp(1j * 24 * a))) < 0.35) * (r > 58))   # tread
        cracks = G.smoothstep(1.6, 2.1, G.fnoise(rng, beta=1.0, kmin=8)) * (r > 50)
        rubber = G.add_lum(rubber, 30 * cracks)
        steel = _rust(rng, (78, 74, 70), amount=0.8)
        steel = G.add_lum(steel, 14 * np.cos(a - 2.3) * G.smoothstep(18, 46, r) - 12 * (np.abs(r - 30) < 1.2))
        img = np.where((r >= 46)[..., None], rubber, steel)
        img = G.add_lum(img, -24 * (np.abs(r - 46) < 1.0) + 18 * (np.abs(r - 61) < 1.0) * (Y < 64))
        hub = r < 9
        img = np.where(hub[..., None], G.add_lum(steel * 0.8, 8 * np.ones((S, S))), img)
        holes = np.zeros((S, S), bool)
        for k in range(4):
            hx, hy = 64 + 30 * np.cos(k * np.pi / 2 + 0.4), 64 + 30 * np.sin(k * np.pi / 2 + 0.4)
            holes |= np.hypot(X - hx, Y - hy) < 7
        solid = (r < 63.5) & ~holes
    return np.concatenate([img, np.where(solid, 255.0, 0.0)[..., None]], axis=2)


def tex_l39_props_yard() -> None:
    """Yard clutter: firewood (bark/split sides + an atlas of 16 split end-grain faces), the rain
    barrel's staves and dark water, the rural mailbox (tin, door end, faded flag), laundry for
    the clothesline (sheet, shirt, rag and the cord in one atlas), the handcart's boards and
    spoked wheel, and the wheelbarrow's rusty steel, its side with the cut front corner, and its
    wheel."""
    X, Y = G.grid()

    # Firewood sides, grain along u: rough bark in patches, pale split faces greyed by the rain.
    rng = G.rng_for(39174)
    bark = G.add_lum(G.fill((58, 50, 42), (S, S)), G.fnoise(rng, beta=1.6, aniso=(10, 1), kmin=3) * 10
                     - 14 * G.smoothstep(0.6, 1.4, G.fnoise(rng, beta=1.2, aniso=(8, 1), kmin=6)))
    split = _grey_wood(rng, False, (134, 122, 104), checks=6)
    mix = G.smoothstep(-0.25, 0.25, G.fnoise(rng, beta=2.6, aniso=(3, 1)))
    img = G.lerp(bark, split, mix[..., None])
    img = G.over(img, np.array([46, 58, 38]), G.smoothstep(1.1, 1.9, G.fnoise(rng, beta=2.2)) * 0.45 * (1 - mix))
    G.save_png(img, _blk("l39_firewood"))

    # End grain atlas: 4 x 4 cells of 32 px. Each is a piece split out of a round log: rings
    # centred off the cell (where the log's heart was), bark wherever the log's edge passes.
    ends = np.zeros((S, S, 3))
    ly, lx = np.mgrid[0:32, 0:32] + 0.5
    for j in range(4):
        for i in range(4):
            ang = rng.uniform(0, 2 * np.pi)
            dist = rng.uniform(6, 34)
            cx, cy = 16 + dist * np.cos(ang), 16 + dist * np.sin(ang)
            R = dist + rng.uniform(8, 20)
            r = np.hypot(lx - cx, ly - cy) + 0.25 * G.blur(rng.normal(0, 1, (32, 32)), 1.0)
            wood = np.array([126, 106, 82]) * rng.uniform(0.85, 1.08)
            cellimg = np.ones((32, 32, 1)) * wood
            cellimg = cellimg + (7 * np.sin(r * 2 * np.pi / 2.4) + rng.normal(0, 3, (32, 32)))[..., None]
            cellimg = G.lerp(cellimg, np.array([104, 102, 96]), rng.uniform(0.15, 0.5))          # weathered grey
            theta = np.arctan2(ly - cy, lx - cx)
            for a0 in rng.uniform(-np.pi, np.pi, 2):                                           # drying checks
                da = np.abs(np.angle(np.exp(1j * (theta - a0)))) * r
                cellimg = cellimg - (24 * (da < 0.7) * (r < R - 2))[..., None]
            cellimg = np.where((r < 1.6)[..., None], cellimg * 0.6, cellimg)
            cellimg = np.where((r > R)[..., None], np.array([54, 46, 40]) + rng.normal(0, 4, (32, 32, 1)), cellimg)
            cellimg = cellimg - (22 * np.exp(-((r - R) / 0.8) ** 2))[..., None]
            edge = np.minimum(np.minimum(lx, 32 - lx), np.minimum(ly, 32 - ly))
            cellimg = cellimg * (1 - 0.35 * G.smoothstep(2.5, 0, edge))[..., None]
            ends[j * 32:(j + 1) * 32, i * 32:(i + 1) * 32] = cellimg
    ends = G.over(ends, np.array([70, 64, 52]), G.smoothstep(0.6, 1.8, G.fnoise(rng, beta=2.2)) * 0.3)
    G.save_png(ends, _blk("l39_firewood_ends"))

    # Barrel staves: 2 px staves (16 texture px) of dark wet oak, rust running down from the hoops
    # (hoops at y 1.5, 7 and 12.5 -> texture rows below 116, 72 and 28), green slime at the foot.
    rng = G.rng_for(39176)
    stave = np.floor(X / 16).astype(int)
    img = G.add_lum(G.fill((84, 66, 50), (S, S)), rng.normal(0, 8, 8)[stave % 8]
                    + G.fnoise(rng, beta=1.8, aniso=(1, 12), kmin=2) * 7 + rng.normal(0, 3, (S, S)))
    img = G.add_lum(img, -36 * G.smoothstep(1.4, 0.3, np.abs(G.wrapd(X, 16))) + 8 * G.smoothstep(2.5, 1.5, np.abs(G.wrapd(X - 2, 16))))
    img = G.over(img, np.array([110, 106, 98]), G.smoothstep(0.8, 1.8, G.fnoise(rng, beta=2.0)) * 0.3)   # greyed wood
    for row in (28, 72, 116):
        run = G.smoothstep(row, row + 4, Y) * np.exp(-(Y - row) / rng.uniform(10, 18)) * (Y >= row)
        run = run * (0.5 + 0.5 * G.smoothstep(-0.5, 1.0, G.fnoise(rng, beta=1.0, aniso=(1, 8), kmin=4)))
        img = G.over(img, np.array([116, 66, 38]), np.clip(run, 0, 1) * 0.7)
    img = G.over(img, np.array([40, 56, 34]), G.smoothstep(104, 128, Y + 10 * G.fnoise(rng, beta=1.6)) * 0.6)
    img = G.add_lum(img, -14 * G.smoothstep(0, 20, 20 - Y))                                       # wet rim
    G.save_png(img, _blk("l39_barrel_staves"))

    # Water: almost black, the foggy sky reflected as a faint cool gradient, a scum ring at the
    # edge, a few drowned leaves.
    rng = G.rng_for(39177)
    r = np.hypot(X - 64, Y - 64)
    img = G.add_lum(G.fill((14, 16, 19), (S, S)), 12 * G.smoothstep(110, 0, X + Y) + G.fnoise(rng, beta=2.8) * 2,
                    (0.85, 0.92, 1.0))
    img = G.over(img, np.array([52, 58, 40]), G.smoothstep(40, 60, r) * G.smoothstep(-0.6, 1.0, G.fnoise(rng, beta=2.0)) * 0.6)
    prims = []
    for _ in range(5):
        x, y = rng.uniform(25, 103, 2)
        prims += _leaf_prims(rng, x, y, rng.uniform(9, 14), np.array([78, 56, 38]) * rng.uniform(0.7, 1.1))
    lv = _render(prims, wrap=(False, False))
    img = np.where(lv[..., 3:] > 0, lv[..., :3] * 0.8, img)
    G.save_png(img, _blk("l39_barrel_water"))

    # Mailbox: galvanised tin gone dull, rust blooms and dents; the door end (north face: the
    # region u 5-11, v 0-5 of the texture) has the arched door outline, latch and faded lettering.
    rng = G.rng_for(39178)
    tin = G._galvanised(rng, (128, 130, 132))
    tin = G.add_lum(tin, 10 * G.blur(G.fnoise(rng, beta=3.0), 2.0) * np.cos(X / 9))               # dents
    tin = G.over(tin, np.array([118, 70, 42]), G.smoothstep(0.7, 1.7, G.fnoise(rng, beta=1.8)) * 0.65)
    tin = G.over(tin, np.array([90, 52, 34]), G.smoothstep(1.4, 2.0, G.fnoise(rng, beta=1.4, kmin=4)) * 0.6)
    for _ in range(4):
        tin = G.mark_drip(tin, rng, (112, 64, 38), a=0.3, length=(20, 40))
    G.save_png(tin, _blk("l39_mailbox_tin"))
    inside = ((np.abs(X - 64) < 21) & (Y > 24) & (Y < 39)) | ((np.hypot(X - 64, Y - 24) < 21) & (Y <= 24))
    door = inside.astype(float)
    front = G.add_lum(tin, -26 * np.clip(G.blur(door, 0.8) - door, 0, 1) * 3 + 10 * np.clip(door - G.blur(door, 0.8), 0, 1) * 3)
    front = G.over(front, np.array([196, 194, 184]), G._text("U.S. MAIL", 64, 28, 6, maxw=30) * 0.35)
    front = G.over(front, np.array([60, 58, 56]), G.box((S, S), 58, 34, 70, 39, 0.5))
    G.save_png(front, _blk("l39_mailbox_front"))
    rng = G.rng_for(39179)
    flag = G._smooth_concrete(rng, (126, 52, 42), mottle=4.0, fine=1.2)
    flag = G.over(flag, np.array([150, 112, 98]), G.smoothstep(0.0, 1.5, G.fnoise(rng, beta=2.0)) * 0.45)   # sun-faded
    flag = G.over(flag, np.array([96, 56, 36]), G.smoothstep(1.0, 1.8, G.fnoise(rng, beta=1.6)) * 0.6)
    G.save_png(flag, _blk("l39_mailbox_flag"))

    G.save_png(_laundry(G.rng_for(39180)), _blk("l39_laundry"))

    # Handcart boards: grey weathered planks along u, 4 px wide, nailed.
    rng = G.rng_for(39181)
    img = _grey_wood(rng, False, (118, 114, 106), checks=7)
    board = np.floor(Y / 32).astype(int)
    img = G.add_lum(img, rng.normal(0, 6, 4)[board % 4] - 40 * G.smoothstep(1.4, 0.4, np.abs(G.wrapd(Y, 32))))
    for b in range(4):
        for x in (10, 118):
            img = _nail(img, x, b * 32 + 16, rng, r=1.7, rust=0.8)
    img = G.over(img, np.array([62, 58, 46]), G.smoothstep(0.6, 1.8, G.fnoise(rng, beta=2.2)) * 0.4)
    G.save_png(img, _blk("l39_cart_wood"))
    G.save_png(_wheel(G.rng_for(39182), "cart"), _blk("l39_cart_wheel"))

    # Wheelbarrow steel: rust over the remains of red paint; the side copy has its lower front
    # corner cut away (u < 2.64 v / 16) so it follows the sloping front plate.
    rng = G.rng_for(39183)
    steel = _rust(rng, (70, 64, 60), amount=0.75)
    paint = G.smoothstep(0.2, 0.9, G.fnoise(rng, beta=2.0))
    steel = G.over(steel, np.array([118, 46, 36]), paint * 0.55)
    steel = G.add_lum(steel, 8 * G.blur(G.fnoise(rng, beta=3.0), 2.0))
    G.save_png(steel, _blk("l39_barrow_steel"))
    side = np.concatenate([steel, np.where(X < 21.1 * Y / S, 0.0, 255.0)[..., None]], axis=2)
    G.save_png(side, _blk("l39_barrow_side"))
    G.save_png(_wheel(G.rng_for(39184), "barrow"), _blk("l39_barrow_wheel"))


def _laundry(rng) -> np.ndarray:
    """Clothesline atlas (cutout). Cells in block pixels: sheet u 0-8 v 0-12, shirt u 8-16 v 0-10,
    rag u 0-4 v 12-15, cord v 15-16 (full width). Each hanging piece has its clothespins in the top
    8 texture rows, which the model puts over the line."""
    X, Y = G.grid()
    rgb = np.zeros((S, S, 3))
    alpha = np.zeros((S, S), bool)

    def cloth(col, mask, folds, seed_stains=2):
        c = G.add_lum(G.fill(col, (S, S)), folds + G.fnoise(rng, beta=2.2) * 4 + rng.normal(0, 2, (S, S)))
        for _ in range(seed_stains):
            c = G.mark_stain(c, rng, (120, 110, 88), size=(10, 12), a=0.3)
        return np.where(mask[..., None], c, rgb), alpha | mask

    # Sheet: hangs from the line, bottom hem sagging and wavy, vertical folds, damp darker hem.
    hem = 90 + 3 * np.sin(X / 7.0) + 2 * np.sin(X / 3.1 + 1)
    sheet = (X >= 2) & (X < 62) & (Y >= 7) & (Y < hem)
    folds = 9 * np.sin(X / 4.3) * G.smoothstep(7, 40, Y) - 10 * G.smoothstep(hem - 18, hem, Y)
    rgb, alpha = cloth((168, 166, 158), sheet, folds)
    rgb = np.where(sheet[..., None], G.add_lum(rgb, -14 * (np.abs(Y - (hem - 3)) < 0.8)), rgb)
    # Shirt (faded blue-grey work shirt hung by the shoulders): body, drooping sleeves, collar.
    body = (X >= 76) & (X < 116) & (Y >= 8) & (Y < 76 + 2 * np.sin(X / 5))
    sl = ((np.abs((X - 71) - 0.22 * (Y - 8)) < 6) | (np.abs((X - 121) + 0.22 * (Y - 8)) < 6)) & (Y >= 8) & (Y < 52)
    sl &= (X >= 64) & (X < 128)
    shirt = body | sl
    folds = 6 * np.sin(X / 3.7) * G.smoothstep(10, 50, Y) - 8 * sl
    rgb, alpha = cloth((108, 118, 126), shirt, folds, 1)
    rgb = np.where((shirt & (np.abs(X - 96) < 1.0) & (Y > 14))[..., None], rgb * 0.8, rgb)       # placket
    for by in range(20, 70, 11):
        rgb = np.where((np.hypot(X - 98.5, Y - by) < 1.3)[..., None], np.array([170, 168, 160]), rgb)
    collar = (np.abs(X - 96) < 9) & (Y >= 8) & (Y < 13)
    rgb = np.where(collar[..., None], rgb * 0.82, rgb)
    # Rag: small checked dishcloth.
    rag = (X >= 3) & (X < 29) & (Y >= 98) & (Y < 118 + 2 * np.sin(X / 3))
    check = ((X // 4 + Y // 4) % 2).astype(float)
    rgb, alpha = cloth((150, 140, 124), rag, -10 * check, 1)
    # Clothespins: weathered wooden pegs over the top edge of each piece.
    pins = np.zeros((S, S), bool)
    for px, y0 in ((12, 0), (50, 0), (79, 1), (113, 1), (15, 96)):
        pins |= (np.abs(X - px) < 2.0) & (Y >= y0 + 1) & (Y < y0 + 14)
    pinc = G.add_lum(G.fill((122, 104, 80), (S, S)), G.fnoise(rng, beta=1.4, aniso=(1, 6)) * 8)
    pinc = G.add_lum(pinc, -20 * ((Y % 13) < 1))                         # the pin's spring notch
    rgb = np.where(pins[..., None], pinc, rgb)
    alpha |= pins
    # Cord: twisted grey-white line, rows 120-128.
    cord = Y >= 120
    cc = G.add_lum(G.fill((150, 148, 140), (S, S)), 18 * np.sin((X + Y * 2) * 1.2) + rng.normal(0, 4, (S, S)))
    rgb = np.where(cord[..., None], cc, rgb)
    alpha |= cord
    return np.concatenate([rgb, np.where(alpha, 255.0, 0.0)[..., None]], axis=2)


TEXTURE_GENS = [
    tex_l39_props_weeds, tex_l39_props_foliage, tex_l39_props_tree, tex_l39_props_yard, tex_l39_props_fence,
    tex_l39_props_pole, tex_l39_props_transformer, tex_l39_props_lattice, tex_l39_props_house,
    tex_l39_props_windows, tex_l39_props_door, tex_l39_props_park, tex_l39_props_camp,
]


# =============================================================================
# Models
# =============================================================================

FACES = ("north", "south", "east", "west", "up", "down")


def _fit(a, b):
    """Keep a UV span inside 0..16 (shift it by whole textures, squeeze it if it's longer)."""
    w = b - a
    if w >= 16:
        return 0.0, 16.0
    a = a % 16
    if a + w > 16:
        a = 16 - w
    return round(a, 4), round(a + w, 4)


def _auto_uv(face, f, t):
    x0, y0, z0 = f
    x1, y1, z1 = t
    u, v = {
        "north": ((16 - x1, 16 - x0), (16 - y1, 16 - y0)),
        "south": ((x0, x1), (16 - y1, 16 - y0)),
        "east": ((16 - z1, 16 - z0), (16 - y1, 16 - y0)),
        "west": ((z0, z1), (16 - y1, 16 - y0)),
        "up": ((x0, x1), (z0, z1)),
        "down": ((x0, x1), (16 - z1, 16 - z0)),
    }[face]
    (u0, u1), (v0, v1) = _fit(*u), _fit(*v)
    return [u0, v0, u1, v1]


def _r(v):
    return [round(float(c), 4) for c in v]


def _el(f, t, tex, faces=FACES, uv=None, rot=None, cull=True, shade=True):
    """One model box.

    tex: texture variable ("#wood") or {face: variable}. uv: None (auto), "full" (the whole
    texture on every face) or {face: [u0, v0, u1, v1]}. rot: (origin, axis, angle) - the box turns
    about origin (right-handed: +z turns x towards y, +x turns y towards z, +y turns z towards x).
    cull: faces lying on the block boundary are hidden when the neighbour there is solid (only
    for boxes that aren't turned).
    """
    e = {"from": _r(f), "to": _r(t), "faces": {}}
    if rot is not None:
        e["rotation"] = {"origin": _r(rot[0]), "axis": rot[1], "angle": rot[2]}
        if len(rot) > 3 and rot[3]:
            e["rotation"]["rescale"] = True
    if not shade:
        e["shade"] = False
    on_edge = {"north": f[2] == 0, "south": t[2] == 16, "west": f[0] == 0, "east": t[0] == 16,
               "down": f[1] == 0, "up": t[1] == 16}
    for face in faces:
        if uv == "full":
            fuv = [0, 0, 16, 16]
        elif isinstance(uv, dict) and face in uv:
            fuv = uv[face]
        else:
            fuv = _auto_uv(face, f, t)
        fd = {"uv": _r(fuv), "texture": tex[face] if isinstance(tex, dict) else tex}
        if cull and rot is None and on_edge[face]:
            fd["cullface"] = face
        e["faces"][face] = fd
    return e


def _model(textures, elements, render="minecraft:cutout", ao=True, display=None, parent="minecraft:block/block"):
    m = {"parent": parent}
    if render:
        m["render_type"] = render
    if not ao:
        m["ambientocclusion"] = False
    m["textures"] = textures
    if display:
        m["display"] = display
    m["elements"] = elements
    return m


def _write(path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", newline="\n")


def _bm(name, data) -> None:
    _write(G.ROOT / "models" / "block" / f"{name}.json", data)


def _bs(name, data) -> None:
    _write(G.ROOT / "blockstates" / f"{name}.json", data)


def _im(name, parent=None, layer=None) -> None:
    if layer:
        data = {"parent": "minecraft:item/generated", "textures": {"layer0": layer}}
    else:
        data = {"parent": parent or _ref(name)}
    _write(G.ROOT / "models" / "item" / f"{name}.json", data)


def _plain_state(name, model=None) -> None:
    _bs(name, {"variants": {"": {"model": _ref(model or name)}}})


FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}


def _facing_state(name, model=None, extra=None) -> None:
    """Blockstate for models built facing north (FACING turns them; extra = more properties)."""
    variants = {}
    for facing, y in FACING_Y.items():
        if extra:
            prop, values, model_fn = extra
            for val in values:
                v = {"model": _ref(model_fn(val))}
                if y:
                    v["y"] = y
                variants[f"facing={facing},{prop}={val}"] = v
        else:
            v = {"model": _ref(model or name)}
            if y:
                v["y"] = y
            variants[f"facing={facing}"] = v
    _bs(name, {"variants": variants})


def _axis_state(name, model=None) -> None:
    _bs(name, {"variants": {"axis=x": {"model": _ref(model or name)},
                            "axis=z": {"model": _ref(model or name), "y": 90}}})


def _cube(name, tex, render=None, parent="minecraft:block/cube_all", item=True, state=True) -> None:
    textures = {"all": _ref(tex)} if isinstance(tex, str) else {k: _ref(v) for k, v in tex.items()}
    m = {"parent": parent}
    if render:
        m["render_type"] = render
    m["textures"] = textures
    _bm(name, m)
    if state:
        _plain_state(name)
    if item:
        _im(name)


def _stairs(name, tex) -> None:
    """Vanilla stair models + blockstate (facing x half x shape), textured all over with tex."""
    t = {"bottom": _ref(tex), "top": _ref(tex), "side": _ref(tex)}
    for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
        _bm(f"{name}{suffix}", {"parent": f"minecraft:block/{parent}", "textures": t})
    base = {"east": 0, "south": 90, "west": 180, "north": 270}
    variants = {}
    for facing, y0 in base.items():
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                model = name + ("_inner" if shape.startswith("inner") else "_outer" if shape.startswith("outer") else "")
                y = y0
                if half == "bottom" and shape.endswith("left"):
                    y = y0 - 90
                if half == "top" and shape.endswith("right"):
                    y = y0 + 90
                y %= 360
                v = {"model": _ref(model)}
                if half == "top":
                    v["x"] = 180
                if y:
                    v["y"] = y
                if half == "top" or y:
                    v["uvlock"] = True
                variants[f"facing={facing},half={half},shape={shape}"] = v
    _bs(name, {"variants": variants})
    _im(name)


def _slab(name, tex, full_model) -> None:
    t = {"bottom": _ref(tex), "top": _ref(tex), "side": _ref(tex)}
    _bm(name, {"parent": "minecraft:block/slab", "textures": t})
    _bm(f"{name}_top", {"parent": "minecraft:block/slab_top", "textures": t})
    _bs(name, {"variants": {"type=bottom": {"model": _ref(name)}, "type=top": {"model": _ref(f"{name}_top")},
                            "type=double": {"model": _ref(full_model)}}})
    _im(name)


def _door(name) -> None:
    """Door models (vanilla door templates, cutout) and the blockstate with the LOCKED property
    (locked doors look the same; the block just refuses to open)."""
    tex = {"bottom": _ref(f"{name}_bottom"), "top": _ref(f"{name}_top")}
    for half, part in (("lower", "bottom"), ("upper", "top")):
        for hinge in ("left", "right"):
            for op in ("", "_open"):
                _bm(f"{name}_{part}_{hinge}{op}", {"parent": f"minecraft:block/door_{part}_{hinge}{op}",
                                                   "render_type": "minecraft:cutout", "textures": tex})
    base = {"east": 0, "south": 90, "west": 180, "north": 270}
    variants = {}
    for facing in ("east", "north", "south", "west"):
        for half, part in (("lower", "bottom"), ("upper", "top")):
            for hinge in ("left", "right"):
                for op in (False, True):
                    y = base[facing] + ((90 if hinge == "left" else 270) if op else 0)
                    v = {"model": _ref(f"{name}_{part}_{hinge}{'_open' if op else ''}")}
                    if y % 360:
                        v["y"] = y % 360
                    for locked in ("false", "true"):
                        variants[f"facing={facing},half={half},hinge={hinge},open={str(op).lower()},locked={locked}"] = v
    _bs(name, {"variants": variants})
    _im(name, layer=f"backrooms:item/{name}")


# ---- picket fence -----------------------------------------------------------

PZ = (6.75, 7.75)       # picket thickness (z)
RZ = (7.75, 9.25)       # rails behind the pickets


def _picket(cx, h, tip=True, stepped=False, rot=None, y0=0.0):
    """One picket: a 2.5 px board from y0 to h with a pointed (diamond) top.

    The point is a square turned 45 degrees about z sitting on the board's top centre, so its
    lower half hides inside the board. A leaning section (rot about x) can't also turn the tip
    about z (one rotation per box), so there the point is two stepped blocks instead.
    """
    x0, x1 = cx - PICKET_W / 2, cx + PICKET_W / 2
    out = [_el((x0, y0, PZ[0]), (x1, h, PZ[1]), "#picket", rot=rot)]
    if not tip:
        return out
    if stepped:
        out.append(_el((cx - 0.85, h, PZ[0]), (cx + 0.85, h + 0.6, PZ[1]), "#picket", rot=rot))
        out.append(_el((cx - 0.35, h + 0.6, PZ[0]), (cx + 0.35, h + 1.1, PZ[1]), "#picket", rot=rot))
    else:
        s = PICKET_W / SQ2 / 2
        zc = (PZ[0] + PZ[1]) / 2
        out.append(_el((cx - s, h - s, PZ[0]), (cx + s, h + s, PZ[1]), "#picket",
                       rot=((cx, h, zc), "z", 45)))
    return out


def _rail(x0, x1, y, rot=None):
    return _el((x0, y[0], RZ[0]), (x1, y[1], RZ[1]), "#rail", rot=rot)


def _picket_fence_models() -> None:
    tex = {"particle": _ref("l39_picket"), "picket": _ref("l39_picket"), "rail": _ref("l39_picket_rail")}
    heights = (13.25, 12.5, 13.75, 12.75)
    # 0: whole, a little uneven.
    e = [_rail(0, 16, RAILS[0]), _rail(0, 16, RAILS[1])]
    for cx, h in zip(PICKET_CX, heights):
        e += _picket(cx, h)
    _bm("l39_picket_fence_0", _model(tex, e))
    # 1: gappy - one picket gone, another snapped off low (a jagged stub).
    e = [_rail(0, 16, RAILS[0]), _rail(0, 16, RAILS[1])]
    e += _picket(PICKET_CX[0], 13.0) + _picket(PICKET_CX[2], 13.5)
    x0 = PICKET_CX[3] - PICKET_W / 2
    e.append(_el((x0, 0, PZ[0]), (x0 + 1.25, 5.5, PZ[1]), "#picket"))
    e.append(_el((x0 + 1.25, 0, PZ[0]), (x0 + 2.5, 4.25, PZ[1]), "#picket"))
    _bm("l39_picket_fence_1", _model(tex, e))
    # 2: the whole section leaning over (every box turned 22.5 degrees about x at its foot).
    lean = ((8, 0, 8), "x", 22.5)
    e = [_rail(0, 16, RAILS[0], lean), _rail(0, 16, RAILS[1], lean)]
    for cx, h in zip(PICKET_CX, (13.0, 13.5, 12.75, 13.25)):
        e += _picket(cx, h, stepped=True, rot=lean)
    _bm("l39_picket_fence_2", _model(tex, e))
    # 3: broken - top rail snapped (a stub left on the far side), two pickets standing (one with
    # its top broken off), one hanging askew from its top nail, a fallen picket in the grass.
    e = [_rail(0, 16, RAILS[0]), _rail(0, 9.5, RAILS[1]), _rail(13.5, 16, RAILS[1])]
    e += _picket(PICKET_CX[0], 13.25)
    x0 = PICKET_CX[3] - PICKET_W / 2
    e.append(_el((x0, 0, PZ[0]), (x0 + 2.5, 10.5, PZ[1]), "#picket"))
    e.append(_el((x0, 10.5, PZ[0]), (x0 + 1.0, 11.75, PZ[1]), "#picket"))
    # Hanging picket: turned 22.5 degrees about its nail (x 6, y 11), so its foot swings out
    # to about x 10 and lifts off the ground. Its point is turned with it: 45 + 22.5 = 67.5,
    # which for a square looks the same as -22.5, centred on where the board's top centre went.
    cx, h, nail_y = PICKET_CX[1], 13.0, 11.0
    th = np.radians(22.5)
    zc = (PZ[0] + PZ[1]) / 2
    e.append(_el((cx - PICKET_W / 2, 0, PZ[0]), (cx + PICKET_W / 2, h, PZ[1]), "#picket",
                 rot=((cx, nail_y, zc), "z", 22.5)))
    dy = h - nail_y
    tx, ty = cx - dy * np.sin(th), nail_y + dy * np.cos(th)
    s = PICKET_W / SQ2 / 2
    e.append(_el((tx - s, ty - s, PZ[0]), (tx + s, ty + s, PZ[1]), "#picket", rot=((tx, ty, zc), "z", -22.5)))
    # A fallen picket lying in the grass behind the fence, askew.
    e.append(_el((2.5, 0, 11.0), (15.0, 1.0, 13.5), "#picket", rot=((8.75, 0.5, 12.25), "y", 22.5),
                 uv={"up": [6.75, 1.0, 9.25, 13.5], "down": [6.75, 1.0, 9.25, 13.5]}))
    _bm("l39_picket_fence_3", _model(tex, e))
    variants = {}
    for axis in ("x", "z"):
        for style in range(4):
            v = {"model": _ref(f"l39_picket_fence_{style}")}
            if axis == "z":
                v["y"] = 90
            variants[f"axis={axis},style={style}"] = v
    _bs("l39_picket_fence", {"variants": variants})
    _im("l39_picket_fence", parent=_ref("l39_picket_fence_0"))


def _fence_post_model() -> None:
    tex = {"particle": _ref("l39_fence_post"), "wood": _ref("l39_fence_post")}
    e = [_el((6, 0, 6), (10, 16, 10), "#wood", faces=("north", "south", "east", "west", "down")),
         _el((5.5, 16, 5.5), (10.5, 16.75, 10.5), "#wood", cull=False),
         _el((6.25, 16.75, 6.25), (9.75, 17.5, 9.75), "#wood", cull=False),
         _el((7.0, 17.5, 7.0), (9.0, 18.0, 9.0), "#wood", cull=False)]
    _bm("l39_fence_post", _model(tex, e))
    _plain_state("l39_fence_post")
    _im("l39_fence_post")


# ---- utility pole -------------------------------------------------------------

def _pole_boxes(y0=0, y1=16, top=True):
    """The 6 px pole as two crossed boxes (an octagon-ish section), stacked seamlessly."""
    faces = ("north", "south", "east", "west", "down") + (("up",) if top else ())
    tex = {f: "#pole" for f in FACES}
    tex["up"] = tex["down"] = "#end"
    return [_el((5, y0, 6), (11, y1, 10), tex, faces=faces),
            _el((6, y0, 5), (10, y1, 11), tex, faces=faces)]


def _insulator(x, z, y):
    """A green glass insulator on a steel pin: drip skirt, body, crown (texture mapped whole)."""
    return [_el((x - 0.4, y, z - 0.4), (x + 0.4, y + 1.0, z + 0.4), "#steel", cull=False),
            _el((x - 1.4, y + 1.0, z - 1.4), (x + 1.4, y + 2.0, z + 1.4), "#glass", uv="full", cull=False),
            _el((x - 1.1, y + 2.0, z - 1.1), (x + 1.1, y + 3.75, z + 1.1), "#glass", uv="full", cull=False),
            _el((x - 0.7, y + 3.75, z - 0.7), (x + 0.7, y + 4.5, z + 0.7), "#glass", uv="full", cull=False)]


def _pole_models() -> None:
    tex = {"particle": _ref("l39_pole_wood"), "pole": _ref("l39_pole_wood"), "end": _ref("l39_pole_top")}
    _bm("l39_utility_pole", _model(tex, _pole_boxes(), render=None))
    _plain_state("l39_utility_pole")
    _im("l39_utility_pole")

    # Crossarm (axis x): pole top, a 3 x 3 px arm through the pole from x -14 to 30 at y 11.5-14.5
    # (split into three boxes so no face stretches over more than one texture), two steel braces
    # at 45 degrees from inside the pole up to the arm at x -3 and 19, four insulators on the arm
    # at x -11, -3, 19, 27 and one on the pole top. Wires attach at the insulator necks
    # (y about 17.5, z 8).
    tex = dict(tex, arm=_ref("l39_crossarm"), steel=_ref("l39_pole_steel"), glass=_ref("l39_insulator"))
    e = _pole_boxes()
    ay0, ay1, az0, az1 = 11.5, 14.5, 6.5, 9.5
    for x0, x1, faces in ((-14, 2, ("north", "south", "up", "down", "west")), (2, 18, ("north", "south", "up", "down")),
                          (18, 30, ("north", "south", "up", "down", "east"))):
        e.append(_el((x0, ay0, az0), (x1, ay1, az1), "#arm", faces=faces, cull=False))
    L = 11 * SQ2
    for mx, ang in ((2.5, -45), (13.5, 45)):
        e.append(_el((mx - L / 2, 5.5 - 0.35, 7.6), (mx + L / 2, 5.5 + 0.35, 8.4), "#steel", rot=((mx, 5.5, 8), "z", ang)))
    for x in (-11, -3, 19, 27):
        e += _insulator(x, 8, ay1)
    e += _insulator(8, 8, 16)
    disp = {"gui": {"rotation": [30, 225, 0], "translation": [0, -1, 0], "scale": [0.3, 0.3, 0.3]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.15, 0.15, 0.15]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.2, 0.2, 0.2]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.2, 0.2, 0.2]}}
    _bm("l39_pole_crossarm", _model(tex, e, display=disp))
    _axis_state("l39_pole_crossarm")
    _im("l39_pole_crossarm")


def _cyl(x0, x1, z0, z1, y0, y1, tex, cut=2.0, full_uv=False):
    """A round can as three boxes: a cross of two slabs plus a third that rounds the corners."""
    c2 = cut * 0.45
    uv = "full" if full_uv else None
    return [_el((x0, y0, z0 + cut), (x1, y1, z1 - cut), tex, uv=uv, cull=False),
            _el((x0 + cut, y0, z0), (x1 - cut, y1, z1), tex, uv=uv, cull=False),
            _el((x0 + c2, y0, z0 + c2), (x1 - c2, y1, z1 - c2), tex, uv=uv, cull=False)]


def _transformer_model() -> None:
    """Transformer can hanging on the pole (FACING north: the pole is south, in the next block;
    the bracket reaches to z 21, where a 6 px pole's face is)."""
    tex = {"particle": _ref("l39_transformer"), "can": _ref("l39_transformer"), "lid": _ref("l39_transformer_top"),
           "front": _ref("l39_transformer_front"), "steel": _ref("l39_pole_steel"), "bush": _ref("l39_bushing")}
    can = {f: "#can" for f in FACES}
    can["up"] = can["down"] = "#lid"
    e = []
    e += _cyl(4.5, 11.5, 8.0, 15.0, 1.0, 2.5, can, cut=2.0)             # bottom pan, a little smaller
    e += _cyl(3.75, 12.25, 7.25, 15.75, 2.5, 12.0, can, cut=2.25)        # the can
    e[-2]["faces"]["north"]["texture"] = "#front"                        # rating on the slab facing out
    e += _cyl(3.5, 12.5, 7.0, 16.0, 12.0, 12.75, can, cut=2.4)           # lid rim
    e += _cyl(4.5, 11.5, 8.0, 15.0, 12.75, 13.25, can, cut=2.0)          # lid dome
    for bx in (6.0, 10.0):                                               # primary bushings + studs
        e.append(_el((bx - 0.9, 13.25, 10.6), (bx + 0.9, 14.25, 12.4), "#bush", uv="full", cull=False))
        e.append(_el((bx - 0.65, 14.25, 10.85), (bx + 0.65, 16.0, 12.15), "#bush", uv="full", cull=False))
        e.append(_el((bx - 0.9, 15.0, 10.6), (bx + 0.9, 15.5, 12.4), "#bush", uv="full", cull=False))
        e.append(_el((bx - 0.25, 16.0, 11.25), (bx + 0.25, 16.75, 11.75), "#steel", cull=False))
    for bz in (9.0, 11.5):                                               # secondary bushings on the side
        e.append(_el((2.75, 8.5, bz - 0.6), (3.75, 9.7, bz + 0.6), "#bush", uv="full", cull=False))
    e.append(_el((6.25, 3.0, 6.75), (9.75, 4.25, 7.25), "#steel", cull=False))   # rating plate
    for y0 in (3.5, 9.5):                                                # hanger brackets to the pole
        e.append(_el((6.75, y0, 15.5), (9.25, y0 + 1.0, 20.5), "#steel", cull=False))
    e.append(_el((6.5, 2.5, 20.5), (9.5, 11.5, 21.0), "#steel", cull=False))
    _bm("l39_pole_transformer", _model(tex, e))
    _facing_state("l39_pole_transformer")
    _im("l39_pole_transformer")


def _lattice_model() -> None:
    """Cube of open steel lattice. Each side also gets an inward-facing copy of its face, so
    through one side's gaps you see the far side's bracing (back faces are otherwise culled)."""
    tex = {"particle": _ref("l39_lattice"), "side": _ref("l39_lattice"), "top": _ref("l39_lattice_top")}
    t = {f: "#side" for f in FACES}
    t["up"] = t["down"] = "#top"
    e = [_el((0, 0, 0), (16, 16, 16), t)]
    e.append(_el((0, 0, 16), (16, 16, 16), "#side", faces=("north",), cull=False, uv={"north": [0, 0, 16, 16]}))
    e.append(_el((0, 0, 0), (16, 16, 0), "#side", faces=("south",), cull=False, uv={"south": [0, 0, 16, 16]}))
    e.append(_el((16, 0, 0), (16, 16, 16), "#side", faces=("west",), cull=False, uv={"west": [0, 0, 16, 16]}))
    e.append(_el((0, 0, 0), (0, 16, 16), "#side", faces=("east",), cull=False, uv={"east": [0, 0, 16, 16]}))
    e.append(_el((0, 16, 0), (16, 16, 16), "#top", faces=("down",), cull=False, uv={"down": [0, 0, 16, 16]}))
    e.append(_el((0, 0, 0), (16, 0, 16), "#top", faces=("up",), cull=False, uv={"up": [0, 0, 16, 16]}))
    _bm("l39_lattice", _model(tex, e))
    _plain_state("l39_lattice")
    _im("l39_lattice")


TREE_HALF = (0.75, 1.25, 2.0, 3.0)    # rod half widths per THICK (must match block/TreeBlock)
DIAG = 8 * SQ2                        # centre to the middle of a top/bottom edge
TREE_SIDES = ("north", "south", "east", "west")


def _rod(h, y0, y1, faces, rot=None):
    """A straight rod along y through the block's centre column, half width h.

    Thick rods are two crossed boxes (a wide-and-shallow one and a narrow-and-deep one), which
    gives an eight-sided outline instead of a square post. The rod's side faces use the bark (its
    ridges run along the rod), the end faces the end grain.
    """
    tex = {f: "#bark" for f in TREE_SIDES}
    tex["up"] = tex["down"] = "#end"
    if h >= 2:
        k = 0.62 * h
        boxes = [((8 - h, y0, 8 - k), (8 + h, y1, 8 + k)), ((8 - k, y0, 8 - h), (8 + k, y1, 8 + h))]
    else:
        boxes = [((8 - h, y0, 8 - h), (8 + h, y1, 8 + h))]
    return [_el(f, t, tex, faces=faces, rot=rot, cull=False) for f, t in boxes]


def _twig_planes(x0, y0, z0, x1, y1, z1, tex="#twig", mirror=False):
    """Two crossed see-through planes (like a vanilla cross plant) filling the given box."""
    out = []
    for i, ang in enumerate((45, -45)):
        uv = [16, 0, 0, 16] if (i == 1) ^ mirror else [0, 0, 16, 16]
        zc = (z0 + z1) / 2
        out.append(_el((x0, y0, zc), (x1, y1, zc), tex, faces=("north", "south"),
                       rot=(((x0 + x1) / 2, (y0 + y1) / 2, zc), "y", ang, True),
                       uv={"north": uv, "south": uv}, cull=False, shade=False))
    return out


def _tree_models() -> None:
    """l39_tree: one multipart blockstate assembling a piece of bare tree from small models.

    For every THICK there is a knot model (always shown), and half-rods from the centre: down
    (to the bottom face centre or a bottom edge middle) and up (to the top face centre or a top
    edge middle). Diagonal half-rods are vertical rods turned 45 degrees about the centre; they are
    built for NORTH and turned to the other sides with the blockstate's "y". A limb that climbs one
    block outward per block up is then one straight rod: the parent's up-rod ends exactly where the
    child's down-rod starts. Pieces of thickness 0/1 with nothing growing out of them end in a
    little spray of twigs.
    """
    tex = {"particle": _ref("l39_tree_bark"), "bark": _ref("l39_tree_bark"), "end": _ref("l39_tree_end"),
           "twig": _ref("l39_twigs")}
    sides = TREE_SIDES
    for t, h in enumerate(TREE_HALF):
        # The knot: the rod itself, a little longer, so the joins of diagonal rods are filled in
        # (a turned rod's corner reaches 1.41 x h above and below the centre).
        _bm(f"l39_tree_core_{t}", _model(tex, _rod(h, 8 - 1.5 * h, 8 + 1.5 * h, FACES)))
        _bm(f"l39_tree_down_c_{t}", _model(tex, _rod(h, 0, 8, sides)))
        _bm(f"l39_tree_up_c_{t}", _model(tex, _rod(h, 8, 16, sides + ("up",))))
        # Down toward the bottom-north edge: +45 about x swings the rod's lower end north;
        # up toward the top-north edge: -45 swings its upper end north.
        _bm(f"l39_tree_down_n_{t}", _model(tex, _rod(h, 8 - DIAG, 8, sides, ((8, 8, 8), "x", 45))))
        _bm(f"l39_tree_up_n_{t}", _model(tex, _rod(h, 8, 8 + DIAG, sides + ("up",), ((8, 8, 8), "x", -45))))
    for t in (0, 1):
        _bm(f"l39_tree_tip_{t}", _model(tex, _twig_planes(4.0, 7.5, 4.0, 12.0, 15.0, 12.0, mirror=t == 1)))
    mp = []
    yrot = {"north": 0, "east": 90, "south": 180, "west": 270}
    for t in range(4):
        mp.append({"when": {"thick": str(t)}, "apply": {"model": _ref(f"l39_tree_core_{t}")}})
        mp.append({"when": {"thick": str(t), "down": "center"}, "apply": {"model": _ref(f"l39_tree_down_c_{t}")}})
        for d, y in yrot.items():
            a = {"model": _ref(f"l39_tree_down_n_{t}")}
            if y:
                a["y"] = y
            mp.append({"when": {"thick": str(t), "down": d}, "apply": a})
        mp.append({"when": {"thick": str(t), "up_c": "true"}, "apply": {"model": _ref(f"l39_tree_up_c_{t}")}})
        for d, y in yrot.items():
            a = {"model": _ref(f"l39_tree_up_n_{t}")}
            if y:
                a["y"] = y
            mp.append({"when": {"thick": str(t), f"up_{d[0]}": "true"}, "apply": a})
    for t in (0, 1):
        mp.append({"when": {"thick": str(t), "up_c": "false", "up_n": "false", "up_e": "false",
                            "up_s": "false", "up_w": "false"},
                   "apply": {"model": _ref(f"l39_tree_tip_{t}")}})
    _bs("l39_tree", {"multipart": mp})
    # Item: a short piece of trunk with a limb leaving it.
    h = TREE_HALF[3]
    e = _rod(h, 0, 16, FACES) + _rod(TREE_HALF[2], 8, 8 + DIAG, sides + ("up",), ((8, 8, 8), "x", -45))
    _bm("l39_tree_inventory", _model(tex, e))
    _im("l39_tree", parent=_ref("l39_tree_inventory"))

    # Twigs: four crossed planes of fine twigs, every block turned a random quarter.
    tex = {"particle": _ref("l39_twigs"), "twig": _ref("l39_twigs")}
    e = _twig_planes(0.8, 0, 0.8, 15.2, 16, 15.2) + _twig_planes(2, 0, 2, 14, 16, 14, mirror=True)
    e[2]["rotation"]["angle"], e[3]["rotation"]["angle"] = 22.5, -22.5
    _bm("l39_twigs", _model(tex, e, ao=False))
    _random_turns("l39_twigs")
    _im("l39_twigs", layer=_ref("l39_twigs"))

    # Dark leaves: a smaller inset leaf cube plus four planes crossing at the centre (out to the
    # block corners), all showing the whole ragged leaf mass. No face has a straight edge, so a
    # crown of these never reads as a lattice of cubes; turned at random. Plain faces, not
    # vanilla "leaves", which asks for a biome tint.
    tex = {"particle": _ref("l39_dark_leaves"), "leaf": _ref("l39_dark_leaves")}
    # The inset cube has side faces only: flat top/bottom faces would float above/below the
    # ragged side outlines like plates. A horizontal leaf plane through the middle covers the
    # view from below and above instead (edge-on, it's invisible from the side).
    e = [_el((2.5, 2.5, 2.5), (13.5, 13.5, 13.5), "#leaf", faces=TREE_SIDES, uv="full", cull=False)]
    e += _twig_planes(0, 0, 0, 16, 16, 16, tex="#leaf")
    e += _twig_planes(1, 1, 1, 15, 15, 15, tex="#leaf", mirror=True)
    for el, ang in ((e[3], 22.5), (e[4], -22.5)):
        el["rotation"]["angle"] = ang
    e.append(_el((0, 8, 0), (16, 8, 16), "#leaf", faces=("up", "down"), uv="full", cull=False))
    _bm("l39_dark_leaves", _model(tex, e, render="minecraft:cutout_mipped"))
    _random_turns("l39_dark_leaves")
    _im("l39_dark_leaves")

    # Bush: three overlapping leaf boxes of different sizes, each side face showing the whole
    # rounded clump texture (ragged, see-through edges), two crossed planes inside for depth and
    # a horizontal plane (the smaller top clump) for the view from above; like the leaves, the
    # boxes have no flat tops that would float over their ragged sides.
    tex = {"particle": _ref("l39_bush"), "leaf": _ref("l39_bush"), "top": _ref("l39_bush_top")}
    e = [_el((0.5, 0, 0.5), (15.5, 10.5, 15.5), "#leaf", faces=TREE_SIDES + ("down",), uv="full", cull=False),
         _el((1.5, 4.5, 2.5), (12.0, 14.5, 12.5), "#leaf", faces=TREE_SIDES, uv="full", cull=False),
         _el((5.5, 3.0, 5.0), (15.0, 13.0, 14.5), "#leaf", faces=TREE_SIDES, uv="full", cull=False),
         _el((1.0, 9.5, 1.0), (15.0, 9.5, 15.0), "#top", faces=("up", "down"), uv="full", cull=False)]
    e += _twig_planes(1.0, 0, 1.0, 15.0, 15.5, 15.0, tex="#leaf")
    e[0]["faces"]["down"]["cullface"] = "down"
    _bm("l39_bush", _model(tex, e, render="minecraft:cutout_mipped"))
    _random_turns("l39_bush")
    _im("l39_bush")

    # Hedge: clipped privet. A full core cube (tiling, so a row of hedge reads as one) wrapped in
    # a loose outer leaf layer 0.75 px outside it on the four sides and the top: through its gaps
    # you see the core behind (depth), and its clusters make the silhouette soft and bumpy (the
    # side layer reaches 1.5 px above the block with ragged tops). A couple of new shoots stick up.
    tex = {"particle": _ref("l39_hedge"), "core": _ref("l39_hedge"), "top": _ref("l39_hedge_top"),
           "outer": _ref("l39_hedge_outer"), "crown": _ref("l39_hedge_crown"), "sprig": _ref("l39_hedge_sprig"),
           "lump": _ref("l39_bush_top")}
    core = {f: "#core" for f in FACES}
    core["up"] = "#top"
    full = [0, 0, 16, 16]
    e = [_el((0, 0, 0), (16, 16, 16), core),
         _el((0, 0, -0.75), (16, 17.5, -0.75), "#outer", faces=("north",), uv={"north": full}, cull=False),
         _el((0, 0, 16.75), (16, 17.5, 16.75), "#outer", faces=("south",), uv={"south": full}, cull=False),
         _el((-0.75, 0, 0), (-0.75, 17.5, 16), "#outer", faces=("west",), uv={"west": full}, cull=False),
         _el((16.75, 0, 0), (16.75, 17.5, 16), "#outer", faces=("east",), uv={"east": full}, cull=False),
         _el((0, 16.75, 0), (16, 16.75, 16), "#crown", faces=("up",), cull=False)]
    e += _twig_planes(1.5, 16.0, 2.5, 7.5, 21.0, 8.5, tex="#sprig")
    e += _twig_planes(9.0, 16.0, 8.5, 14.0, 20.0, 13.5, tex="#sprig", mirror=True)
    # A few low leafy lumps on top break the straight clipped line.
    for f, t in (((1.0, 16.4, 1.0), (7.0, 18.2, 6.5)), ((8.0, 16.4, 6.0), (13.5, 17.8, 11.5)),
                 ((3.0, 16.4, 10.0), (8.5, 17.5, 15.0))):
        e.append(_el(f, t, "#lump", faces=TREE_SIDES + ("up",), uv="full", cull=False))
    _bm("l39_hedge", _model(tex, e, render="minecraft:cutout_mipped"))
    _random_turns("l39_hedge")
    _im("l39_hedge")

    _bm("l39_weeds", {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
                      "textures": {"cross": _ref("l39_weeds")}})
    _plain_state("l39_weeds")
    _im("l39_weeds", layer=_ref("l39_weeds"))


def _random_turns(name) -> None:
    """Blockstate that turns the model a random quarter per block position (vanilla picks one
    entry of the list from the position), so leaves and shrubs don't repeat block after block."""
    entries = []
    for y in (0, 90, 180, 270):
        v = {"model": _ref(name)}
        if y:
            v["y"] = y
        entries.append(v)
    _bs(name, {"variants": {"": entries}})


# ---- yard props -------------------------------------------------------------------

def _woodpile_model() -> None:
    """Split firewood stacked against a wall (FACING north: the wall is south, z 16): four rows of
    logs running front to back, end grain to the front, a couple of loose ones on top.

    Each log's front shows one cell of the 4 x 4 end-grain atlas (l39_firewood_ends), so
    neighbouring logs look like different split pieces.
    """
    rng = G.rng_for(39190)
    tex = {"particle": _ref("l39_firewood"), "wood": _ref("l39_firewood"), "ends": _ref("l39_firewood_ends")}
    e = []
    cell = 0
    for row, y0 in enumerate((0.0, 3.5, 7.0, 10.5)):
        x = 0.15 + (1.6 if row % 2 else 0.0) * rng.random()
        while x < 15.2:
            w = min(rng.uniform(2.9, 4.2), 15.85 - x)
            if w < 1.8:
                break
            ya = y0 + rng.uniform(0.0, 0.35)
            yb = y0 + 3.5 - rng.uniform(0.0, 0.45)
            zf = 4.0 + rng.uniform(0.0, 1.3)
            L = 16 - zf
            cu, cv = (cell % 4) * 4, (cell // 4 % 4) * 4
            cell += int(rng.integers(1, 4))
            ux, uy = rng.uniform(0, 16 - L), rng.uniform(0, 16 - w)
            ends = {f: "#ends" if f in ("north", "south") else "#wood" for f in FACES}
            e.append(_el((x, ya, zf), (x + w, yb, 16), ends,
                         faces=("north", "south", "east", "west", "up", "down"),
                         uv={"north": [cu, cv, cu + 4, cv + 4], "south": [12 - cu, 12 - cv, 16 - cu, 16 - cv],
                             # The grain runs along u, so the top and bottom faces get a turned
                             # strip (u along the log = along z).
                             "up": [ux, uy, ux + L, uy + w], "down": [ux, uy, ux + L, uy + w]},
                         cull=False))
            e[-1]["faces"]["up"]["rotation"] = 90
            e[-1]["faces"]["down"]["rotation"] = 90
            e[-1]["faces"]["south"]["cullface"] = "south"          # hidden against the wall
            x += w + rng.uniform(0.1, 0.45)
    # Loose logs on top: one across the pile (ends east/west), one askew along it.
    e.append(_el((2.5, 14.0, 7.0), (13.0, 16.8, 10.0), {f: "#ends" if f in ("east", "west") else "#wood" for f in FACES},
                 faces=("north", "south", "east", "west", "up"),
                 uv={"east": [8, 4, 12, 8], "west": [12, 12, 16, 16]}, rot=((7.75, 15.4, 8.5), "y", 22.5), cull=False))
    e.append(_el((10.5, 14.0, 9.0), (13.5, 16.6, 16.0), {f: "#ends" if f == "north" else "#wood" for f in FACES},
                 faces=("north", "east", "west", "up"), uv={"north": [4, 8, 8, 12], "up": [2, 4, 9, 7]}, cull=False))
    e[-1]["faces"]["up"]["rotation"] = 90
    _bm("l39_woodpile", _model(tex, e, render=None))
    _facing_state("l39_woodpile")
    _im("l39_woodpile")


def _rain_barrel_model() -> None:
    """Wooden rain barrel (12 px across, 15 tall) with three iron hoops, brimful of dark water:
    the water is the top of the body at y 13.5, the staves go on up to the rim at 15."""
    tex = {"particle": _ref("l39_barrel_staves"), "staves": _ref("l39_barrel_staves"),
           "water": _ref("l39_barrel_water"), "hoop": _ref("l39_rusty_pipe")}
    body = {f: "#staves" for f in FACES}
    body["up"] = "#water"
    e = _cyl(2.0, 14.0, 2.0, 14.0, 0.0, 13.5, body, cut=2.6)
    e[0]["faces"]["down"]["cullface"] = "down"
    for y in (1.5, 7.0, 12.5):                                          # hoops, a hair proud of the staves
        e += _cyl(1.8, 14.2, 1.8, 14.2, y, y + 0.9, "#hoop", cut=2.55)
    # Rim: the staves standing up round the water (four walls + four corner pieces).
    c, w = 2.6, 0.9
    walls = [((2.0 + c, 13.5, 2.0), (14.0 - c, 15.0, 2.0 + w)), ((2.0 + c, 13.5, 14.0 - w), (14.0 - c, 15.0, 14.0)),
             ((2.0, 13.5, 2.0 + c), (2.0 + w, 15.0, 14.0 - c)), ((14.0 - w, 13.5, 2.0 + c), (14.0, 15.0, 14.0 - c))]
    c2 = c * 0.45
    for sx in (0, 1):
        for sz in (0, 1):
            x0 = 2.0 + c2 if sx == 0 else 14.0 - c2 - 2.2
            z0 = 2.0 + c2 if sz == 0 else 14.0 - c2 - 2.2
            walls.append(((x0, 13.5, z0), (x0 + 2.2, 15.0, z0 + 2.2)))
    for f, t in walls:
        e.append(_el(f, t, "#staves", faces=("north", "south", "east", "west", "up"), cull=False))
    _bm("l39_rain_barrel", _model(tex, e, render=None))
    _facing_state("l39_rain_barrel")
    _im("l39_rain_barrel")


def _clothesline_models() -> None:
    """Clothesline T-post and the line itself (both axis=x: the line runs along x).

    The post's crossbar runs ALONG the line (axis x), so a line arriving at the block edge at
    y 14.6-15.05 meets the end of the bar (y 13.2-15.5), where an eye bolt holds it. The line sags
    a little between posts (each block's middle sits a third of a pixel lower) and carries a few
    grey sheets, shirts and rags: five versions picked at random per block.
    """
    tex = {"particle": _ref("l39_fence_post"), "wood": _ref("l39_fence_post"), "steel": _ref("l39_pole_steel")}
    e = [_el((6.5, 0, 6.5), (9.5, 16, 9.5), "#wood", faces=("north", "south", "east", "west", "down")),
         _el((7.0, 16.0, 7.0), (9.0, 16.4, 9.0), "#wood", cull=False),
         _el((0.3, 13.2, 6.75), (15.7, 15.5, 9.25), "#wood", cull=False)]
    L = 4.6                                                              # braces under the bar
    for xc, ang in ((5.4, 45), (10.6, -45)):
        e.append(_el((xc - 0.6, 11.6 - L / 2, 7.25), (xc + 0.6, 11.6 + L / 2, 8.75), "#wood",
                     rot=((xc, 11.6, 8), "z", ang), cull=False))
    for x0 in (-0.2, 15.7):                                              # eye bolts the line is tied to
        e.append(_el((x0, 14.45, 7.7), (x0 + 0.5, 15.15, 8.3), "#steel", cull=False))
    _bm("l39_clothesline_post", _model(tex, e, render=None))
    _axis_state("l39_clothesline_post")
    _im("l39_clothesline_post")

    tex = {"particle": _ref("l39_laundry"), "cloth": _ref("l39_laundry")}
    cord = {"north": [0, 15, 16, 15.5], "south": [0, 15, 16, 15.5], "up": [0, 15.5, 16, 16], "down": [0, 15.5, 16, 16]}

    def line():
        out = []
        for x0, x1, y in ((0, 5, 14.6), (5, 11, 14.25), (11, 16, 14.6)):
            uv = {k: [x0, v[1], x1, v[3]] for k, v in cord.items()}
            out.append(_el((x0, y, 7.8), (x1, y + 0.45, 8.2), "#cloth", faces=("north", "south", "up", "down"),
                           uv=uv, cull=False))
        return out

    def hang(x0, cell, z=8.0):
        """One piece of laundry from the atlas: cell = (u0, v0, u1, v1); its top row (with the
        clothespins) sits 1 px above the line."""
        u0, v0, u1, v1 = cell
        w, hgt = u1 - u0, v1 - v0
        top = 15.6
        uv = {"north": [u1, v0, u0, v1], "south": [u0, v0, u1, v1]}
        return _el((x0, top - hgt, z - 0.12), (x0 + w, top, z + 0.12), "#cloth", faces=("north", "south"),
                   uv=uv, cull=False)

    SHEET, SHIRT, RAG = (0, 0, 8, 12), (8, 0, 16, 10), (0, 12, 4, 15)
    versions = [line() + [hang(1.0, SHEET)],
                line() + [hang(4.5, SHIRT)],
                line() + [hang(0.2, SHEET, 8.05), hang(10.5, RAG)],
                line() + [hang(11.0, RAG, 7.95)],
                line()]
    for i, e in enumerate(versions):
        _bm(f"l39_clothesline_{i}", _model(tex, e, ao=False))
    weights = (3, 3, 2, 2, 4)
    variants = {}
    for axis, y in (("x", 0), ("z", 90)):
        entries = []
        for i, wgt in enumerate(weights):
            v = {"model": _ref(f"l39_clothesline_{i}"), "weight": wgt}
            if y:
                v["y"] = y
            entries.append(v)
        variants[f"axis={axis}"] = entries
    _bs("l39_clothesline", {"variants": variants})
    _im("l39_clothesline", parent=_ref("l39_clothesline_0"))


def _mailbox_model() -> None:
    """Rural tin mailbox on a wooden post (FACING north = the lid, toward the path): a box with
    a rounded (stepped) top, the door on the north end, the flag folded down on the east side."""
    tex = {"particle": _ref("l39_mailbox_tin"), "tin": _ref("l39_mailbox_tin"), "front": _ref("l39_mailbox_front"),
           "wood": _ref("l39_fence_post"), "flag": _ref("l39_mailbox_flag")}
    box = {f: "#tin" for f in FACES}
    box["north"] = "#front"
    e = [_el((7, 0, 7), (9, 10.5, 9), "#wood", faces=("north", "south", "east", "west", "down")),
         _el((6.5, 10.5, 4.0), (9.5, 11.0, 12.0), "#wood", cull=False)]
    for x0, x1, y0, y1 in ((5.0, 11.0, 11.0, 14.0), (5.3, 10.7, 14.0, 14.9), (5.9, 10.1, 14.9, 15.5),
                           (6.9, 9.1, 15.5, 15.9)):
        e.append(_el((x0, y0, 3.0), (x1, y1, 13.0), box, faces=("north", "south", "east", "west", "up", "down"),
                     cull=False))
    e.append(_el((7.4, 11.3, 2.55), (8.6, 12.1, 3.0), "#tin", cull=False))          # latch tab
    e.append(_el((11.0, 11.6, 6.6), (11.35, 12.1, 12.2), "#flag", cull=False))       # flag arm, down
    e.append(_el((11.0, 11.2, 4.6), (11.35, 13.3, 7.0), "#flag", cull=False))        # flag
    e.append(_el((10.9, 11.55, 11.4), (11.5, 12.15, 12.0), "#tin", cull=False))      # pivot rivet
    _bm("l39_mailbox", _model(tex, e, render=None))
    _facing_state("l39_mailbox")
    _im("l39_mailbox")


def _handcart_model() -> None:
    """Two-wheeled wooden handcart left with its shafts on the ground (FACING north = where the
    shafts point). The bed is built level and then tipped 22.5 degrees forward about the axle
    (y 5.2, z 10.5), which brings the front end of the shafts down onto the ground."""
    tex = {"particle": _ref("l39_cart_wood"), "wood": _ref("l39_cart_wood"), "rail": _ref("l39_picket_rail"),
           "iron": _ref("l39_cast_iron"), "wheel": _ref("l39_cart_wheel")}
    tip = ((8, 5.2, 10.5), "x", -22.5)
    e = [_el((3.0, 6.0, 1.5), (13.0, 6.8, 15.0), "#wood", rot=tip),                       # floor
         _el((2.2, 6.0, 1.5), (3.0, 9.5, 15.0), "#wood", rot=tip),                        # sides
         _el((13.0, 6.0, 1.5), (13.8, 9.5, 15.0), "#wood", rot=tip),
         _el((2.2, 6.0, 0.8), (13.8, 9.8, 1.5), "#wood", rot=tip),                        # front board
         _el((2.6, 6.8, 14.9), (13.4, 8.8, 15.6), "#wood", rot=tip),                      # tailboard
         _el((3.4, 5.0, -2.6), (4.4, 6.0, 15.0), "#rail", rot=tip),                       # shafts
         _el((11.6, 5.0, -2.6), (12.6, 6.0, 15.0), "#rail", rot=tip),
         _el((3.4, 5.1, -2.2), (12.6, 5.9, -1.4), "#rail", rot=tip),                      # handle bar
         _el((1.0, 4.7, 10.0), (15.0, 5.7, 11.0), "#iron", cull=False)]                    # axle
    for x0, x1 in ((0.9, 2.0), (14.0, 15.1)):
        e.append(_el((x0, 0.0, 5.3), (x1, 10.4, 15.7), "#wheel", faces=("east", "west"),
                     uv={"east": [0, 0, 16, 16], "west": [0, 0, 16, 16]}, cull=False))
    for x0, x1 in ((0.3, 2.3), (13.7, 15.7)):                                              # hubs
        e.append(_el((x0, 4.4, 9.7), (x1, 6.0, 11.3), "#iron", cull=False))
    e[0]["faces"].pop("down")
    _bm("l39_handcart", _model(tex, e))
    _facing_state("l39_handcart")
    _im("l39_handcart")


def _wheelbarrow_model() -> None:
    """Rusty wheelbarrow (FACING north = the wheel end): steel tray with a sloped front, two
    wooden handles running back to the grips at z 16, legs at the back, the wheel at the front."""
    tex = {"particle": _ref("l39_barrow_steel"), "steel": _ref("l39_barrow_steel"),
           "side": _ref("l39_barrow_side"), "wood": _ref("l39_picket_rail"), "wheel": _ref("l39_barrow_wheel"),
           "iron": _ref("l39_cast_iron")}
    e = [_el((4.0, 5.0, 3.4), (12.0, 5.6, 12.0), "#steel", cull=False),                   # floor
         # Front, sloping out over the wheel (its top leans 1.8 px north).
         _el((3.4, 5.0, 3.4), (12.6, 9.6, 4.0), "#steel", rot=((8, 5.0, 4.0), "x", -22.5)),
         _el((3.4, 5.0, 12.0), (12.6, 9.25, 12.6), "#steel", cull=False)]                  # back
    # Sides: their front-bottom corner is cut away along the sloping front (cut-out texture).
    side_faces = {"west": [0, 0, 16, 16], "east": [16, 0, 0, 16], "up": [0, 0, 1, 16]}
    for x0 in (3.4, 12.0):
        e.append(_el((x0, 5.0, 1.64), (x0 + 0.6, 9.25, 12.6), {"east": "#side", "west": "#side", "up": "#steel"},
                     faces=("east", "west", "up"), uv=side_faces, cull=False))
    for x0 in (3.1, 12.3):                                                                 # rolled rim
        e.append(_el((x0, 9.2, 1.7), (x0 + 0.6, 9.8, 12.9), "#steel", cull=False))
    e.append(_el((3.1, 9.2, 12.3), (12.9, 9.8, 12.9), "#steel", cull=False))
    for x0 in (4.6, 10.4):
        e.append(_el((x0, 4.0, 1.5), (x0 + 1.0, 5.0, 16.0), "#wood", cull=False))          # handles
        e.append(_el((x0 + 0.2, 0.0, 11.4), (x0 + 0.8, 4.0, 12.0), "#iron", cull=False))   # legs
        e.append(_el((x0 + 0.2, 0.0, 10.4), (x0 + 0.8, 0.6, 12.4), "#iron", cull=False))   # feet
    e.append(_el((5.0, 2.2, 1.95), (5.6, 4.0, 2.55), "#iron", cull=False))                 # wheel forks
    e.append(_el((10.4, 2.2, 1.95), (11.0, 4.0, 2.55), "#iron", cull=False))
    e.append(_el((5.0, 1.95, 1.95), (11.0, 2.55, 2.55), "#iron", cull=False))              # axle
    e.append(_el((7.3, 0.0, -0.25), (8.7, 4.5, 4.25), "#wheel", faces=("east", "west"),
                 uv={"east": [0, 0, 16, 16], "west": [0, 0, 16, 16]}, cull=False))
    e.append(_el((7.45, 0.0, 1.0), (8.55, 0.5, 3.0), "#iron", cull=False))                 # tyre contact
    _bm("l39_wheelbarrow", _model(tex, e))
    _facing_state("l39_wheelbarrow")
    _im("l39_wheelbarrow")


def _yard_models() -> None:
    _woodpile_model()
    _rain_barrel_model()
    _clothesline_models()
    _mailbox_model()
    _handcart_model()
    _wheelbarrow_model()



def _house_models() -> None:
    _cube("l39_white_trim", "l39_white_trim")
    _cube("l39_shingles", "l39_shingles")
    _stairs("l39_shingle_stairs", "l39_shingles")
    _slab("l39_shingle_slab", "l39_shingles", "l39_shingles")
    _cube("l39_chimney_brick", "l39_chimney_brick")
    _cube("l39_foundation", "l39_foundation")
    for name in ("l39_sash_window", "l39_sash_window_lit"):
        # FACING = outside. The glass sits 1.5 px back behind a white casing, above a sill that
        # sticks out 1.25 px; the back (south) face shows the window from inside the room.
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
        _facing_state(name)
        _im(name)
    _door("l39_house_door")


def _bench_model() -> None:
    """Park bench, FACING north = the way you sit, so the backrest is on the south side."""
    tex = {"particle": _ref("l39_bench_slat"), "slat": _ref("l39_bench_slat"), "iron": _ref("l39_cast_iron")}
    e = []
    for x0 in (1.0, 13.75):
        x1 = x0 + 1.25
        e += [_el((x0, 0, 3.5), (x1, 7, 5), "#iron"),                     # front leg
              _el((x0, 0, 10.5), (x1, 7, 12), "#iron"),                   # rear leg
              _el((x0, 7, 11.75), (x1, 16, 13.0), "#iron"),               # back upright
              _el((x0, 6, 3.5), (x1, 7, 12), "#iron"),                    # seat bracket
              _el((x0 - 0.25, 0, 3.0), (x1 + 0.25, 0.75, 5.5), "#iron"),  # feet
              _el((x0 - 0.25, 0, 10.0), (x1 + 0.25, 0.75, 12.5), "#iron"),
              _el((x0, 1.75, 5), (x1, 2.5, 10.5), "#iron"),               # stretcher
              _el((x0, 7, 3.75), (x1, 10, 4.75), "#iron"),                # arm post
              _el((x0 - 0.25, 10, 3.25), (x1 + 0.25, 10.75, 12.0), "#iron"),  # armrest
              _el((x0 - 0.25, 9.5, 2.75), (x1 + 0.25, 10.75, 3.75), "#iron")]  # scroll end
    for z0, z1 in ((3.5, 5.25), (5.75, 7.5), (8.0, 9.75), (10.25, 11.75)):
        e.append(_el((0, 7, z0), (16, 8, z1), "#slat"))
    for y0, y1 in ((9.0, 10.75), (11.5, 13.25), (14.0, 15.75)):
        e.append(_el((0, y0, 11.0), (16, y1, 11.75), "#slat"))
    _bm("l39_park_bench", _model(tex, e))
    _facing_state("l39_park_bench")
    _im("l39_park_bench")


def _post_models() -> None:
    tex = {"particle": _ref("l39_white_trim"), "paint": _ref("l39_white_trim")}
    side = ("north", "south", "east", "west")
    e = [_el((6, 0, 6), (10, 2.5, 10), "#paint", faces=side + ("up", "down")),
         _el((6.25, 2.5, 6.25), (9.75, 3, 9.75), "#paint", faces=side + ("up",)),
         _el((6.75, 3, 6.75), (9.25, 13, 9.25), "#paint", faces=side),
         _el((6.4, 7.5, 6.4), (9.6, 8.5, 9.6), "#paint", faces=side + ("up", "down")),
         _el((6.25, 13, 6.25), (9.75, 13.5, 9.75), "#paint", faces=side + ("down",)),
         _el((6, 13.5, 6), (10, 16, 10), "#paint", faces=side + ("up", "down"))]
    _bm("l39_white_post", _model(tex, e, render=None))
    _plain_state("l39_white_post")
    _im("l39_white_post")

    tex = {"particle": _ref("l39_rusty_pipe"), "pipe": _ref("l39_rusty_pipe")}
    _bm("l39_rusty_post", _model(tex, [_el((6.5, 0, 6.5), (9.5, 16, 9.5), "#pipe")], render=None))
    _plain_state("l39_rusty_post")
    _im("l39_rusty_post")

    # Swing bar (axis x): the pipe at the bottom of its block, with two clamps for the chains at
    # x 3 and 13 (the swing two blocks below reaches up to here).
    e = [_el((0, 0, 6.5), (16, 3, 9.5), "#pipe")]
    for cx in (3.0, 13.0):
        e.append(_el((cx - 1, -0.5, 6.0), (cx + 1, 3.5, 10.0), "#pipe", cull=False))
        e.append(_el((cx - 0.5, -0.75, 7.5), (cx + 0.5, -0.5, 8.5), "#pipe", cull=False))
    _bm("l39_swing_bar", _model(tex, e, render=None))
    _axis_state("l39_swing_bar")
    _im("l39_swing_bar")

    # Swing (FACING north): a plank seat on two steel hangers, chains at x 3 and 13 rising to
    # y 32 = the bottom of the bar two blocks up. Each chain is two crossed planes (like the
    # vanilla chain), split at y 16 so the texture is never stretched.
    tex = {"particle": _ref("l39_bench_slat"), "seat": _ref("l39_bench_slat"), "chain": _ref("l39_chain"),
           "steel": _ref("l39_rusty_pipe")}
    e = [_el((2.5, 3.5, 5.5), (13.5, 5.0, 10.5), "#seat")]
    for cx in (3.0, 13.0):
        e.append(_el((cx - 0.75, 3.0, 6.75), (cx + 0.75, 5.75, 9.25), "#steel"))
        for y0, y1 in ((5.75, 16), (16, 32)):
            v = [7, 16 - (y1 - y0), 9, 16]
            e.append(_el((cx - 1, y0, 8), (cx + 1, y1, 8), "#chain", faces=("north", "south"),
                         rot=((cx, 8, 8), "y", 45), uv={"north": v, "south": v}))
            e.append(_el((cx, y0, 7), (cx, y1, 9), "#chain", faces=("east", "west"),
                         rot=((cx, 8, 8), "y", 45), uv={"east": v, "west": v}))
    _bm("l39_swing", _model(tex, e))
    _facing_state("l39_swing")
    _im("l39_swing", layer="backrooms:item/l39_swing")

    _cube("l39_porch_planks", "l39_porch_planks")


def _camp_models() -> None:
    _cube("l39_canvas", "l39_canvas")
    _stairs("l39_canvas_stairs", "l39_canvas")
    _cube("l39_meg_crate", {"side": "l39_meg_crate", "top": "l39_meg_crate_top", "bottom": "l39_meg_crate_bottom"},
          parent="minecraft:block/cube_bottom_top")

    # Dead hurricane lantern: fuel tank, globe with a wire guard, cap with a vent, air tubes up
    # the sides, wire bail on top. Shape 4.5..11.5 x 0..12.
    tex = {"particle": _ref("l39_lamp_metal"), "metal": _ref("l39_lamp_metal"), "glass": _ref("l39_lamp_glass")}
    e = [_el((4.5, 0, 5.25), (11.5, 2.5, 10.75), "#metal"),
         _el((5.25, 0, 4.5), (10.75, 2.5, 11.5), "#metal"),
         _el((5.5, 2.5, 5.5), (10.5, 3.0, 10.5), "#metal"),
         _el((5.75, 3.0, 5.75), (10.25, 8.5, 10.25), "#glass", uv="full"),
         _el((6.5, 3.5, 5.25), (9.5, 8.0, 10.75), "#glass", uv="full", faces=("north", "south")),
         _el((5.25, 3.5, 6.5), (10.75, 8.0, 9.5), "#glass", uv="full", faces=("east", "west")),
         _el((5.5, 8.5, 5.5), (10.5, 9.5, 10.5), "#metal"),
         _el((6.25, 9.5, 6.25), (9.75, 10.5, 9.75), "#metal"),
         _el((6.75, 10.5, 6.75), (9.25, 11.0, 9.25), "#metal"),
         _el((4.5, 2.0, 7.4), (5.1, 9.5, 8.6), "#metal"),
         _el((10.9, 2.0, 7.4), (11.5, 9.5, 8.6), "#metal"),
         _el((5.1, 9.0, 7.6), (5.75, 9.5, 8.4), "#metal"),
         _el((10.25, 9.0, 7.6), (10.9, 9.5, 8.4), "#metal")]
    for z0 in (4.85, 10.85):                                             # wire guard bars
        e.append(_el((7.85, 3.0, z0), (8.15, 8.5, z0 + 0.3), "#metal"))
        e.append(_el((6.35, 5.5, z0), (9.65, 5.8, z0 + 0.3), "#metal"))
    e += [_el((7.8, 11.5, 5.25), (8.2, 12.0, 10.75), "#metal"),          # bail
          _el((7.8, 9.5, 5.25), (8.2, 11.5, 5.65), "#metal"),
          _el((7.8, 9.5, 10.35), (8.2, 11.5, 10.75), "#metal")]
    _bm("l39_dead_lamp", _model(tex, e))
    _facing_state("l39_dead_lamp")
    _im("l39_dead_lamp")

    # Survey clipboard lying flat (shape 3.5..12.5 x 0..1 x 2.5..14): board, page, clip at the
    # north end. Upright for someone standing south of it. One page texture per NOTE.
    for i in range(4):
        tex = {"particle": _ref(f"l39_survey_paper_{i}"), "board": _ref("l39_clipboard"),
               "paper": _ref(f"l39_survey_paper_{i}"), "clip": _ref("l39_clip")}
        e = [_el((3.5, 0, 2.5), (12.5, 0.6, 14), "#board"),
             _el((4.0, 0.6, 3.5), (12.0, 0.7, 13.5), "#paper", faces=("up", "north", "south", "east", "west"),
                 uv={"up": [0, 0, 16, 16]}),
             _el((5.5, 0.6, 2.75), (10.5, 1.1, 4.5), "#clip"),
             _el((6.5, 1.1, 3.0), (9.5, 1.6, 5.25), "#clip"),
             _el((7.25, 1.1, 2.5), (8.75, 2.0, 3.25), "#clip")]
        _bm(f"l39_survey_note_{i}", _model(tex, e))
    _facing_state("l39_survey_note", extra=("note", range(4), lambda n: f"l39_survey_note_{n}"))
    _im("l39_survey_note", layer="backrooms:item/l39_survey_note")


def tex_l39_props_models() -> None:
    """Block models, blockstates and item models for every Level 39 prop id (LF JSON)."""
    _picket_fence_models()
    _fence_post_model()
    _pole_models()
    _transformer_model()
    _lattice_model()
    _tree_models()
    _yard_models()
    _house_models()
    _bench_model()
    _post_models()
    _camp_models()


TEXTURES = TEXTURE_GENS + [tex_l39_props_models]
