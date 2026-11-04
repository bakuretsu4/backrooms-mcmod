"""Level 228's mirror surfaces: textures/level228/mirror.png, drawn over every pane by
client/level228/Level228MirrorSurfaces (translucent, no depth write).

A pane is see-through: what you "see in it" is the real twin space or sealed image volume built
behind it, so the surface only adds what an old silvered mirror adds on top of the reflection:
  - a faint neutral silver veil (no tint), dust, wipe smears and the odd handprint at hand height;
  - desilvering: black-brown loss of the silver creeping in from the edges, and foxing spots;
  - cracks radiating from an impact, catching the light (bright core, dark refracted edge);
  - shattered panes: shards missing down to the black backing board, the rest crazed;
  - funhouse (distorting) panes: a dark veil with warped chrome streaks (alpha at most 0.55, so the
    stretched stand-in behind still shows);
  - clear glass (the Glass Labyrinth): smudges, fingerprints, the dark edge of the glass.

Tiles are painted 512 x 480 px (shipped at half that, see shipped()) = one pane from column centre
to column centre (3 blocks) by its visible height (2.82 blocks). Alpha is straight (not
premultiplied). The edges of the glass that a viewer
sees are where the column shafts hide it: about 0.2 block (u 0.067) in from each end.
"""
from __future__ import annotations

import math

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

from . import mat as M

SIZE = 2048
TW, TH = 512, 480
PAD = 0
NAMES = [
    "mir_clean_0", "mir_clean_1", "mir_clean_2", "mir_tarnish_0",
    "mir_tarnish_1", "mir_tarnish_2", "mir_crack_0", "mir_crack_1",
    "mir_crack_2", "mir_shatter_0", "mir_shatter_1", "mir_concave",
    "mir_convex", "mir_wave", "mir_glass_0", "mir_glass_1",
]
# Small solid regions below the tiles: white (vertex colour does the rest: the grazing-angle
# darkening and the shattered pane's black gaps use it) and the clean silver colour.
EXTRA = {"mir_white": (0, 1920 + 8, 64, 64), "mir_edge": (64 + 8, 1920 + 8, 64, 64)}

EDGE_U = 0.2 / 3.0
PPB_X = TW / 3.0
PPB_Y = TH / 2.82


def uv(name, u, v):
    if name in EXTRA:
        x, y, w, h = EXTRA[name]
    else:
        i = NAMES.index(name)
        x, y, w, h = (i % 4) * TW, (i // 4) * TH, TW, TH
    u = np.clip(u, 1.0 / w, 1 - 1.0 / w)
    v = np.clip(v, 1.0 / h, 1 - 1.0 / h)
    return (x + u * w) / SIZE, (y + v * h) / SIZE


def tile_rect(name):
    i = NAMES.index(name)
    return (i % 4) * TW, (i // 4) * TH, TW, TH


# ------------------------------------------------------------------------------------------------

SILVER = M.col("#bdbbb6")
DUST = M.col("#9c968b")
LOSS = M.col("#0c0907")      # where the silver is gone: the dark backing paint
FOX = M.col("#3b2618")


def _layer(rgb, a, c, alpha):
    """Composite a colour c with coverage alpha over (rgb, a) (straight alpha, 'over')."""
    c = np.asarray(c, float)
    alpha = np.clip(alpha, 0, 1)
    out_a = alpha + a * (1 - alpha)
    num = (c if c.ndim == 3 else c[None, None, :]) * alpha[..., None] + rgb * (a * (1 - alpha))[..., None]
    out_rgb = num / np.maximum(out_a[..., None], 1e-6)
    return out_rgb, out_a


def _edge_dist():
    """Distance in blocks from the visible edge of the glass (column shafts left and right, the
    frame rails top and bottom)."""
    X, Y = M.grid(TH, TW)
    dx = np.minimum(X - EDGE_U, (1 - EDGE_U) - X) * 3.0
    dy = np.minimum(Y, 1 - Y) * 2.82
    return np.maximum(np.minimum(dx, dy), 0.0), X, Y


def _base(r, veil=0.045, dust=0.05, smears=4, hands=1):
    X, Y = M.grid(TH, TW)
    rgb = np.broadcast_to(SILVER, (TH, TW, 3)).copy()
    a = np.full((TH, TW), veil)
    # Dust: mottled, heavier toward the bottom (it settles) and in the top corners.
    n = M.fnoise(r, TH, TW, beta=2.6) * 0.5 + 0.5
    fine = M.fnoise(r, TH, TW, beta=1.0) * 0.5 + 0.5
    d = dust * (0.5 + 0.9 * n) * (0.7 + 0.6 * M.smooth(0.5, 1.0, Y)) * (0.85 + 0.3 * fine)
    rgb, a = _layer(rgb, a, DUST, d)
    # Wipe smears: long soft arcs where someone once cleaned it (cleaner inside, dirt at the ends).
    img = Image.new("L", (TW, TH), 0)
    dr = ImageDraw.Draw(img)
    for _ in range(smears):
        cx, cy = r.random() * TW, (0.25 + 0.6 * r.random()) * TH
        R = (0.15 + 0.25 * r.random()) * TW
        a0 = r.random() * 360
        dr.arc([cx - R, cy - R * 0.6, cx + R, cy + R * 0.6], a0, a0 + 70 + 80 * r.random(),
               fill=int(110 + 100 * r.random()), width=int(TW * (0.03 + 0.04 * r.random())))
    sm = M.blur(np.asarray(img, float) / 255.0, 5.0)
    streak = M.fnoise(r, TH, TW, beta=1.0, aniso=(6.0, 1.0)) * 0.5 + 0.5
    rgb, a = _layer(rgb, a, DUST * 1.05, sm * streak * 0.07)
    # Handprints: a palm and four fingers, smudged, at about 1.0-1.5 blocks up.
    for _ in range(hands):
        hx = (0.25 + 0.5 * r.random()) * TW
        hy = (1.0 - (1.0 + 0.5 * r.random()) / 2.82) * TH
        hand = Image.new("L", (TW, TH), 0)
        hd = ImageDraw.Draw(hand)
        s = PPB_X * 0.16
        tilt = r.normal() * 0.3
        hd.ellipse([hx - s * 0.5, hy - s * 0.35, hx + s * 0.5, hy + s * 0.55], fill=160)
        for k in range(4):
            ang = -math.pi / 2 + (k - 1.5) * 0.28 + tilt
            L = s * (0.95 + 0.25 * (1 - abs(k - 1.5) / 1.5))
            fx, fy = hx + math.cos(ang) * s * 0.35, hy - s * 0.3
            tx, ty = fx + math.cos(ang) * L, fy + math.sin(ang) * L
            hd.line([(fx, fy), (tx, ty)], fill=150, width=max(2, int(s * 0.17)))
            hd.ellipse([tx - s * 0.09, ty - s * 0.09, tx + s * 0.09, ty + s * 0.09], fill=190)
        tx_, ty_ = hx + s * 0.55, hy + s * 0.05
        hd.line([(hx + s * 0.3, hy + s * 0.2), (tx_ + s * 0.3, ty_ - s * 0.35)], fill=140, width=max(2, int(s * 0.18)))
        hm = M.blur(np.asarray(hand, float) / 255.0, 1.2)
        # Skin oil holds dust in ridges: break it up.
        ridges = (M.fnoise(r, TH, TW, beta=0.3) > 0.0).astype(float) * 0.5 + 0.5
        rgb, a = _layer(rgb, a, DUST * 0.95, hm * ridges * 0.16)
    return rgb, a


def _desilver(r, rgb, a, reach=0.08, spots=12, fox=20, strength=1.0):
    """Silver lost from the edges in a ragged front, isolated black spots, brown foxing."""
    dist, X, Y = _edge_dist()
    # Beside the columns the loss creeps in about half as far as along the rails: in the maze
    # every pane meets a column at both ends, and a full-height dark band at each one stacked up
    # through the reflections into black stripes down every column.
    side = np.minimum(X - EDGE_U, (1 - EDGE_U) - X) * 3.0
    rail = np.minimum(Y, 1 - Y) * 2.82
    dist = np.maximum(np.minimum(side * 2.0, rail), 0.0)
    warp = M.fnoise(r, TH, TW, beta=2.2) * 0.5 + M.fnoise(r, TH, TW, beta=1.2) * 0.25
    front = dist - reach * (1.0 + 1.1 * warp)
    # Real desilvering is a cloudy, mottled loss, not an ink stain: a soft front, the backing
    # paint showing through unevenly (some silver flakes left in it), brown-grey rather than
    # black where it thins. A hard black front read as dark ivy growing up the column edges.
    mottle = M.fnoise(r, TH, TW, beta=1.6) * 0.5 + 0.5
    loss = np.clip(1.0 - front / 0.03, 0, 1) * (dist > 0)
    loss = loss * strength * (0.55 + 0.35 * mottle)
    # The dark loss has a halo of thinning silver (brown-grey) just inside the front.
    halo = np.clip(1.0 - np.abs(front - 0.02) / 0.045, 0, 1) * 0.3 * strength
    rgb, a = _layer(rgb, a, M.col("#4a3b2c"), halo)
    lossc = M.lerp(M.col("#2b231c"), LOSS, M.smooth(0.3, 0.9, mottle))
    rgb, a = _layer(rgb, a, lossc, loss * 0.85)
    Yp, Xp = np.mgrid[0:TH, 0:TW].astype(float)
    # Black spots, mostly near the edges.
    for _ in range(spots):
        for _try in range(10):
            sx, sy = r.random() * TW, r.random() * TH
            if dist[int(sy), int(sx)] < reach * 3.5 + 0.05:
                break
        rad = (0.006 + 0.02 * r.random() ** 2) * PPB_X
        dd = np.hypot(Xp - sx, Yp - sy) / rad + warp * 0.6
        rgb, a = _layer(rgb, a, LOSS, np.clip(1.2 - dd, 0, 1) * 0.9 * strength)
    # Foxing: small brown spots with a paler ring, scattered.
    for _ in range(fox):
        sx, sy = r.random() * TW, r.random() * TH
        rad = (0.004 + 0.012 * r.random()) * PPB_X
        dd = np.hypot(Xp - sx, Yp - sy) / rad
        rgb, a = _layer(rgb, a, FOX, np.clip(1.0 - dd, 0, 1) ** 0.7 * 0.55 * strength)
        rgb, a = _layer(rgb, a, M.col("#6d5a46"), np.exp(-((dd - 1.3) / 0.3) ** 2) * 0.18 * strength)
    return rgb, a


def _crack_lines(r, cx, cy, rays, rings, img_size, ss=2):
    """Radial and ring cracks from an impact at (cx, cy) (pixels): returns (core, dark) masks."""
    W, H = img_size
    core = Image.new("L", (W * ss, H * ss), 0)
    dark = Image.new("L", (W * ss, H * ss), 0)
    dc, dd = ImageDraw.Draw(core), ImageDraw.Draw(dark)
    rays_pts = []
    for k in range(rays):
        ang = 2 * math.pi * (k + r.random() * 0.6) / rays
        x, y = cx * ss, cy * ss
        pts = [(x, y)]
        step = 0.012 * W * ss
        L = 0
        maxL = (0.6 + 1.0 * r.random()) * W * ss
        while 0 <= x < W * ss and 0 <= y < H * ss and L < maxL:
            ang += r.normal() * 0.12
            x += math.cos(ang) * step
            y += math.sin(ang) * step
            L += step
            pts.append((x, y))
            # Branches.
            if r.random() < 0.02:
                bx, by, ba = x, y, ang + (r.random() - 0.5) * 1.4
                bpts = [(bx, by)]
                for _ in range(int(10 + 25 * r.random())):
                    ba += r.normal() * 0.15
                    bx += math.cos(ba) * step
                    by += math.sin(ba) * step
                    bpts.append((bx, by))
                dc.line(bpts, fill=200, width=max(1, ss))
                dd.line([(px + ss * 1.2, py + ss * 1.2) for px, py in bpts], fill=160, width=max(1, ss))
        rays_pts.append(pts)
        widths = max(1, int(ss * (1.6 if k % 3 == 0 else 1.0)))
        dc.line(pts, fill=255, width=widths)
        dd.line([(px + ss * 1.5, py + ss * 1.5) for px, py in pts], fill=200, width=widths + ss)
    # Ring cracks: arcs joining neighbouring rays.
    for ring in range(rings):
        rad = (0.04 + 0.07 * ring + 0.02 * r.random()) * W * ss
        for k in range(rays):
            if r.random() < 0.35:
                continue
            a0 = 2 * math.pi * k / rays
            a1 = a0 + 2 * math.pi / rays
            pts = []
            for t in np.linspace(a0, a1, 8):
                rr = rad * (1 + 0.08 * r.normal())
                pts.append((cx * ss + math.cos(t) * rr, cy * ss + math.sin(t) * rr))
            dc.line(pts, fill=220, width=max(1, ss))
            dd.line([(px + ss, py + ss) for px, py in pts], fill=170, width=max(1, ss))
    c = np.asarray(core.resize((W, H), Image.LANCZOS), float) / 255.0
    d = np.asarray(dark.resize((W, H), Image.LANCZOS), float) / 255.0
    return c, d


def _impact(r, rgb, a, cx, cy, rays=12, rings=3):
    c, d = _crack_lines(r, cx, cy, rays, rings, (TW, TH))
    Yp, Xp = np.mgrid[0:TH, 0:TW].astype(float)
    crush = np.exp(-((Xp - cx) ** 2 + (Yp - cy) ** 2) / (2 * (0.025 * TW) ** 2))
    craze = (M.fnoise(r, TH, TW, beta=0.4) > 0.7) * crush
    rgb, a = _layer(rgb, a, M.col("#151210"), d * 0.45)
    rgb, a = _layer(rgb, a, M.col("#e4e1da"), c * 0.75)
    rgb, a = _layer(rgb, a, M.col("#d8d4cc"), np.clip(craze * 0.7 + crush * 0.25, 0, 1))
    return rgb, a


def _shatter(r, rgb, a, cx, cy, missing=0.35):
    """Shards round an impact (cells smaller near it); some gone to the black backing."""
    n = 70
    ang = r.random(n) * 2 * math.pi
    rad = (r.random(n) ** 1.8) * 0.9 * TW
    pts = np.stack([cx + np.cos(ang) * rad, cy + np.sin(ang) * rad * 0.9], axis=1)
    pts = np.concatenate([pts, r.random((20, 2)) * [TW, TH]])
    from scipy.spatial import cKDTree
    tree = cKDTree(pts)
    Yp, Xp = np.mgrid[0:TH, 0:TW]
    q = np.stack([Xp.ravel() + 0.5, Yp.ravel() + 0.5], axis=1)
    dists, ids = tree.query(q, k=2)
    cell = ids[:, 0].reshape(TH, TW)
    edge = (dists[:, 1] - dists[:, 0]).reshape(TH, TW)
    dist_imp = np.hypot(pts[:, 0] - cx, pts[:, 1] - cy)
    gone = (r.random(len(pts)) < missing * np.clip(1.4 - dist_imp / (0.5 * TW), 0.2, 1.2))
    gone_px = gone[cell]
    # Remaining shards sit at slightly different angles: each catches the light a bit differently.
    shard_tone = (r.random(len(pts)) * 0.08)[cell]
    rgb, a = _layer(rgb, a, SILVER, shard_tone)
    line = np.clip(1.0 - edge / 1.6, 0, 1)
    rgb, a = _layer(rgb, a, M.col("#e9e6df"), line * 0.7)
    rgb, a = _layer(rgb, a, M.col("#101010"), np.clip(1.0 - np.abs(edge - 2.2) / 1.2, 0, 1) * 0.35)
    # Missing shards: the backing board, black with a little brown paint and splinters of silver
    # left at the broken edges.
    back = M.lerp(M.col("#070605"), M.col("#1b130c"), M.fnoise(r, TH, TW, beta=2.0) * 0.5 + 0.5)
    lip = gone_px & (ndimage.distance_transform_edt(gone_px) < 2.2)
    rgb = np.where(gone_px[..., None], back, rgb)
    a = np.where(gone_px, 1.0, a)
    rgb = np.where(lip[..., None], M.col("#8d8a83"), rgb)
    return rgb, a


def _distort(r, kind):
    """A funhouse mirror: dark veil and warped chrome streaks. The reflection behind is drawn
    stretched by ReflectionAgent's stand-ins; this sells the warped polished sheet."""
    X, Y = M.grid(TH, TW)
    if kind == "concave":
        # Tall and thin: streaks bow inward toward the middle.
        s = (X - 0.5) / (0.3 + 0.7 * (1 - (2 * Y - 1) ** 2) * 0.35 + 0.35)
        f = np.sin(s * 9.0 + M.fnoise(r, TH, TW, beta=3.0) * 0.6)
        hi = np.clip(f, 0, 1) ** 6
        lo = np.clip(-f, 0, 1) ** 2
    elif kind == "convex":
        s = (Y - 0.5) / (0.35 + 0.65 * (1 - (2 * X - 1) ** 2) * 0.4 + 0.3)
        f = np.sin(s * 8.0 + M.fnoise(r, TH, TW, beta=3.0) * 0.6)
        hi = np.clip(f, 0, 1) ** 6
        lo = np.clip(-f, 0, 1) ** 2
    else:
        s = Y * 6.0 + 0.35 * np.sin(X * 2 * math.pi * 1.5 + Y * 3.0) + M.fnoise(r, TH, TW, beta=3.0) * 0.5
        f = np.sin(s * 2 * math.pi)
        hi = np.clip(f, 0, 1) ** 8
        lo = np.clip(-f, 0, 1) ** 1.5
    rgb = np.broadcast_to(M.col("#2a2826"), (TH, TW, 3)).copy()
    a = np.full((TH, TW), 0.30)
    rgb, a = _layer(rgb, a, M.col("#050505"), lo * 0.25)
    rgb, a = _layer(rgb, a, M.col("#e8e6e0"), hi * 0.35)
    a = np.minimum(a, 0.55)
    # Old funhouse mirrors are dull and scratched.
    sc = M.scratches(r, TH, TW, 40, (0.02, 0.15), 1.0)
    rgb, a = _layer(rgb, a, M.col("#cfcac1"), sc * 0.2)
    rgb, a = _desilver(r, rgb, a, reach=0.04, spots=5, fox=8, strength=0.7)
    return rgb, np.minimum(a, 0.62)


def _glass(r, smudge=1.0):
    X, Y = M.grid(TH, TW)
    rgb = np.broadcast_to(M.col("#a7a9a8"), (TH, TW, 3)).copy()
    a = np.full((TH, TW), 0.025)
    dist, _, _ = _edge_dist()
    # The glass edge: you see the pane's thickness as a dark line where it meets the post.
    rgb, a = _layer(rgb, a, M.col("#1c1e1d"), np.exp(-(dist / 0.012) ** 2) * 0.55)
    n = M.fnoise(r, TH, TW, beta=2.4) * 0.5 + 0.5
    rgb, a = _layer(rgb, a, DUST, n * 0.04 * smudge * (0.6 + 0.8 * M.smooth(0.5, 1.0, Y)))
    for _ in range(int(14 * smudge)):
        img = Image.new("L", (TW, TH), 0)
        d = ImageDraw.Draw(img)
        x, y = r.random() * TW, (0.3 + 0.55 * r.random()) * TH
        s = PPB_X * (0.025 + 0.02 * r.random())
        d.ellipse([x - s, y - s * 1.3, x + s, y + s * 1.3], fill=255)
        m = M.blur(np.asarray(img, float) / 255.0, 1.0) * ((M.fnoise(r, TH, TW, beta=0.2) > 0.2) * 0.6 + 0.4)
        rgb, a = _layer(rgb, a, DUST, m * 0.18)
    sc = M.scratches(r, TH, TW, 25, (0.02, 0.1), 1.0)
    rgb, a = _layer(rgb, a, M.col("#dfe0dd"), sc * 0.15)
    return rgb, a


def _tile(name):
    r = M.rng(22900 + NAMES.index(name))
    kind, _, idx = name[4:].partition("_")
    idx = int(idx) if idx.isdigit() else 0
    if kind == "clean":
        rgb, a = _base(r, veil=0.04 + 0.01 * idx, dust=0.04, smears=3 + idx, hands=idx % 2 + (1 if idx == 2 else 0))
        rgb, a = _desilver(r, rgb, a, reach=0.015 + 0.01 * idx, spots=4, fox=6, strength=0.8)
    elif kind == "tarnish":
        rgb, a = _base(r, veil=0.07, dust=0.08, smears=5, hands=1)
        rgb, a = _layer(rgb, a, M.col("#8f8576"), (M.fnoise(r, TH, TW, beta=2.8) * 0.5 + 0.5) * 0.08)
        rgb, a = _desilver(r, rgb, a, reach=0.07 + 0.05 * idx, spots=16 + 6 * idx, fox=40 + 15 * idx, strength=1.0)
    elif kind == "crack":
        rgb, a = _base(r, veil=0.05, dust=0.06, smears=3, hands=1 if idx != 1 else 0)
        rgb, a = _desilver(r, rgb, a, reach=0.03 + 0.03 * idx, spots=8, fox=15, strength=0.9)
        cx = (0.3 + 0.4 * r.random()) * TW
        cy = (0.35 + 0.35 * r.random()) * TH
        rgb, a = _impact(r, rgb, a, cx, cy, rays=10 + 3 * idx, rings=2 + idx)
    elif kind == "shatter":
        rgb, a = _base(r, veil=0.06, dust=0.07, smears=2, hands=0)
        rgb, a = _desilver(r, rgb, a, reach=0.06, spots=10, fox=20)
        cx = (0.35 + 0.3 * r.random()) * TW
        cy = (0.4 + 0.25 * r.random()) * TH
        rgb, a = _shatter(r, rgb, a, cx, cy, missing=0.3 + 0.1 * idx)
        rgb, a = _impact(r, rgb, a, cx, cy, rays=14, rings=3)
    elif kind in ("concave", "convex", "wave"):
        rgb, a = _distort(r, kind)
    elif kind == "glass":
        rgb, a = _glass(r, smudge=1.0 + 0.6 * idx)
    else:
        raise ValueError(name)
    return np.concatenate([np.clip(rgb, 0, 1), np.clip(a, 0, 1)[..., None]], axis=2)


def build() -> np.ndarray:
    out = np.zeros((SIZE, SIZE, 4))
    for name in NAMES:
        x, y, w, h = tile_rect(name)
        out[y:y + h, x:x + w] = _tile(name)
    x, y, w, h = EXTRA["mir_white"]
    out[y:y + h, x:x + w] = 1.0
    x, y, w, h = EXTRA["mir_edge"]
    out[y:y + h, x:x + w, :3] = SILVER
    out[y:y + h, x:x + w, 3] = 1.0
    return out


# The sheet ships at half the painted size (1024 x 1024, tiles 256 x 240: about 85 px per block,
# more than a pane's veil, dust and cracks need at the distances mirrors are seen from). Painted at
# full size and filtered down so thin cracks stay continuous instead of aliasing. Tile positions
# are fractions of the sheet, so the game's layout constants (Level228MirrorSurfaces) are
# unchanged. Then, for the jar (7.5 MB before, ~1.3 MB now): colour is softened a little inside
# each tile and kept at 5 bits (it only ever shows at the low coverage of a veil, where a step is
# invisible), coverage keeps all 8 bits where it is faint (the veil itself, 4-8 % alpha, must stay
# exact) and 6 bits where it is dense (cracks, loss, backing).
SHIP = SIZE // 2
SOFTEN = 0.7


def _soften_tile(t: np.ndarray) -> np.ndarray:
    a = t[..., 3]
    pm = [ndimage.gaussian_filter(t[..., c] * a, SOFTEN, mode="nearest") for c in range(3)]
    ab = np.maximum(ndimage.gaussian_filter(a, SOFTEN, mode="nearest"), 1e-4)
    out = t.copy()
    out[..., :3] = np.where(a[..., None] > 1e-4, np.clip(np.stack(pm, axis=2) / ab[..., None], 0, 1), t[..., :3])
    return out


def shipped() -> np.ndarray:
    img = build()
    a = img[..., 3:4]
    # Average premultiplied colour so transparent texels don't darken the edges of what's kept.
    pm = np.concatenate([img[..., :3] * a, a], axis=2)
    k = SIZE // SHIP
    pm = pm.reshape(SHIP, k, SHIP, k, 4).mean(axis=(1, 3))
    out = pm.copy()
    out[..., :3] = np.where(pm[..., 3:4] > 1e-6, pm[..., :3] / np.maximum(pm[..., 3:4], 1e-6), 0.0)
    tw, th = TW // k, TH // k
    for name in NAMES:
        x, y, _, _ = tile_rect(name)
        x, y = x // k, y // k
        t = _soften_tile(out[y:y + th, x:x + tw])
        t[..., :3] = np.round(t[..., :3] * 31) / 31
        t[..., 3] = np.where(t[..., 3] > 0.25, np.round(t[..., 3] * 63) / 63, t[..., 3])
        out[y:y + th, x:x + tw] = t
    return out


def save(path):
    img = shipped()
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(np.clip(np.round(img * 255), 0, 255).astype(np.uint8), "RGBA").save(path, optimize=True)
