"""Level 39 ("Megalophobia") world blocks: textures, block models, blockstates and item models.

Level 39 is a foggy night town of light-blue clapboard houses on wet dirt yards, walled in by a
colossal old red-brick mill whose thousands of windows glow bright cool white, and undermined by
white marble tunnels full of turquoise water. Everything outdoors is cold, grey and weathered.

Run with `python tools/gen_assets.py l39` (then `tex_surface_variants variant_models` for the
SurfaceBlock versions listed in gen_assets.SURFACE_VARIANTS). Every texture has its own fixed
random seed (39000-39999), so re-running gives identical files.

The props of the town (fences, poles, trees, house trim, park and camp things) live in
level39_props.py; this module is the ground, the house walls, the mill and the tunnels.
"""
from __future__ import annotations

import json

import numpy as np
from PIL import Image, ImageDraw

import gen_assets as G

S = G.S                 # 128 px block texture size
X, Y = G.grid()         # pixel-centre coordinates of a block texture

ASSETS = G.ROOT         # src/main/resources/assets/backrooms


def _blk(name: str):
    return G.TEX / "block" / f"{name}.png"


def _item(name: str):
    return G.TEX / "item" / f"{name}.png"


def _fill(col, shape=(S, S)):
    return G.fill(col, shape)


# =============================================================================
# Shared drawing helpers
# =============================================================================

def _relief(h, k):
    """Brightness change from a height field lit from the upper left (as Minecraft's sun mostly is).

    A slope facing up-left gets lighter, one facing down-right darker. np.roll wraps around, so
    seamless height fields give seamless shading."""
    gx = (np.roll(h, -1, 1) - np.roll(h, 1, 1)) * 0.5
    gy = (np.roll(h, -1, 0) - np.roll(h, 1, 0)) * 0.5
    return (gx * 0.6 + gy * 0.8) * k


def _lum(img):
    return img @ np.array([0.3, 0.59, 0.11])


def _strokes(rng, count, draw_one, ss=4):
    """RGB + soft coverage (0..1) of many small PIL-drawn shapes, wrapped so the texture tiles.

    Like gen_assets._cutout_scatter, but the coverage stays soft so the shapes can be laid over
    an opaque ground (grass blades over mud, straw, twigs). draw_one(d, rng, x, y, k) draws one
    shape at (x, y) (already scaled by k); it is drawn at the 9 wrap offsets with the same
    random numbers so pieces crossing an edge continue on the other side."""
    canvas = Image.new("RGBA", (S * ss, S * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(canvas)
    for _ in range(count):
        x, y = rng.uniform(0, S), rng.uniform(0, S)
        state = rng.bit_generator.state
        for ox in (-S, 0, S):
            for oy in (-S, 0, S):
                rng.bit_generator.state = state
                draw_one(d, rng, (x + ox) * ss, (y + oy) * ss, ss)
    a = np.asarray(canvas, float).reshape(S, ss, S, ss, 4)
    cov = a[..., 3].mean(axis=(1, 3)) / 255.0
    w = a[..., 3:4] / 255.0
    rgb = (a[..., :3] * w).sum(axis=(1, 3)) / np.maximum(w.sum(axis=(1, 3)), 1e-6)
    return rgb, cov


def _pebbles(rng, n, r=(1.2, 3.5), flat=(0.55, 1.0), mask=None):
    """n wrapped pebbles: (height field, per-pebble tone -1..1-ish, coverage, colour index 0..9).

    mask (0..1, optional) thins the pebbles out: a pebble whose centre lands on a low mask value is
    skipped (so marks can put stones only in one spot)."""
    h = np.zeros((S, S))
    tone = np.zeros((S, S))
    cov = np.zeros((S, S))
    cid = np.zeros((S, S))
    for _ in range(n):
        cx, cy = rng.uniform(0, S), rng.uniform(0, S)
        rx = rng.uniform(*r)
        ry = rx * rng.uniform(*flat)
        a = rng.uniform(0, np.pi)
        t, ci, keep = rng.normal(), rng.integers(0, 10), rng.random()
        if mask is not None and keep > mask[int(cy) % S, int(cx) % S]:
            continue
        dx, dy = G.wrapd(X - cx, S), G.wrapd(Y - cy, S)
        c, s = np.cos(a), np.sin(a)
        d = ((dx * c + dy * s) / rx) ** 2 + ((-dx * s + dy * c) / ry) ** 2
        dome = np.sqrt(np.clip(1 - d, 0, 1)) * ry * 0.9
        sel = dome > h
        h = np.where(sel, dome, h)
        tone = np.where(sel, t, tone)
        cid = np.where(sel, ci, cid)
        cov = np.maximum(cov, G.smoothstep(1.12, 0.82, d))
    return h, tone, cov, cid


def _lay_pebbles(img, rng, n, cols, r=(1.2, 3.5), shade=9.0, mask=None, sink=0.0):
    """Lay pebbles (colours picked from cols) on img with shading and a soft contact shadow.

    sink (0..1) buries them in mud: their tops still show but the edges blend into the ground."""
    h, tone, cov, cid = _pebbles(rng, n, r, mask=mask)
    cols = np.asarray(cols, float)
    rgb = cols[(cid % len(cols)).astype(int)] * (1 + 0.09 * tone)[..., None]
    rgb = G.add_lum(rgb, G.fnoise(rng, beta=0.6, kmin=24) * 5)          # grain of the stone
    shadow = np.roll(G.blur(cov, 1.1), (1, 1), (0, 1)) * (1 - cov)
    img = img * (1 - 0.45 * shadow[..., None])
    a = cov * (1 - sink * G.smoothstep(0.9, 0.2, h / max(h.max(), 1e-6)))
    img = G.over(img, rgb, a)
    return G.add_lum(img, _relief(h, shade) * cov)


def _wet_sheen(img, rng, amount=1.0, mask=None):
    """Tiny bright glints where light catches wet ground (seamless per-pixel speckle)."""
    g = (rng.random((S, S)) < 0.01 * amount) * rng.uniform(0.3, 1.0, (S, S))
    g = G.blur(g, 0.5) * (1 if mask is None else mask)
    return G.add_lum(img, g * 34, (0.92, 0.96, 1.0))


def _puddle(img, rng, m):
    """Standing water in mask m (0..1): dark smooth water with the foggy night sky faintly
    mirrored in it, wet darker ground around its rim and a thin bright meniscus line."""
    rim = np.clip(G.blur(m, 2.2) * 1.8, 0, 1)
    img = img * (1 - 0.3 * rim[..., None])
    sky = G.add_lum(_fill((62, 68, 76)), G.fnoise(rng, beta=3.2) * 5 + G.fnoise(rng, beta=1.5, aniso=(1, 6), kmin=3) * 2)
    water = G.lerp(img * 0.3, sky, 0.72)
    img = G.over(img, water, G.smoothstep(0.25, 0.6, m))
    line = np.clip(1 - np.abs(m - 0.4) / 0.12, 0, 1)
    return G.add_lum(img, line * 14, (0.9, 0.95, 1.0))


def _lmask(rng, size, y_range=(0.0, 1.0), rough=0.45, lo=0.25, hi=0.75):
    """A soft, irregular local patch (0..1) kept inside the block (for variant marks)."""
    sx, sy = size[0] * rng.uniform(0.8, 1.2), size[1] * rng.uniform(0.8, 1.2)
    cx, cy = G._spot(rng, 1.4 * sx, 1.4 * sy, y_range)
    m = G._local(cx, cy, sx, sy) * (1 + rough * (0.8 * G.fnoise(rng, beta=3.0) + 0.45 * G.fnoise(rng, beta=1.8, kmin=3)))
    return G.smoothstep(lo, hi, m) * G._edge_fade(), cx, cy


def _save(img, name):
    G.save_png(img, _blk(name))


# =============================================================================
# The town ground
# =============================================================================

SOIL = (76, 62, 50)
MUD = (62, 52, 43)
GRASS_COLS = ((70, 84, 52), (82, 94, 58), (94, 102, 64), (62, 74, 46), (104, 108, 70), (76, 88, 60))
STRAW_COLS = ((118, 110, 80), (104, 96, 70), (128, 118, 86), (96, 88, 66))
STONE_COLS = ((112, 110, 106), (96, 94, 92), (128, 124, 116), (84, 82, 80), (120, 112, 100),
              (138, 136, 130), (104, 98, 90), (90, 88, 84), (146, 142, 134), (110, 104, 96))
DARK_STONES = tuple(tuple(int(v * 0.74) for v in c) for c in STONE_COLS)   # stones half sunk in wet earth
CLOD_COLS = ((84, 68, 54), (70, 56, 44), (92, 76, 60), (64, 52, 42), (78, 64, 50))


def _earth(rng, col, clods=6.0, grain=5.0):
    """Wet earth: broad mottling, crumbly clods (shaded height field), fine grit and dark pores."""
    img = G.add_lum(_fill(col), G.fnoise(rng, beta=2.8) * 7 + G.fnoise(rng, beta=1.4, kmin=6) * 3)
    h = G.blur(G.fnoise(rng, beta=1.8, kmin=5), 0.7)
    img = G.add_lum(img, _relief(h, clods) + rng.normal(0, grain, (S, S)) * 0.6)
    pores = (rng.random((S, S)) < 0.02) * rng.uniform(0.5, 1.0, (S, S))
    img = G.add_lum(img, -G.blur(pores, 0.5) * 40)
    damp = G.smoothstep(0.2, 1.6, G.fnoise(rng, beta=2.6))
    return img * (1 - 0.18 * damp[..., None])


def _blade(cols, angle=0.5, spread=0.7, length=(4.0, 10.0), field=None, straw=None):
    """Grass-blade drawer for _strokes: a two-segment bent line lying mostly one way (the grass is
    flattened by rain). field (0..1) thins the blades out (mud shows through), straw (0..1) turns
    them to dead straw colours."""
    cols = np.asarray(cols, float)
    straw_cols = np.asarray(STRAW_COLS, float)

    def draw(d, rng, x, y, k):
        L = rng.uniform(*length) * k
        a = angle + rng.normal(0, spread)
        bend = rng.normal(0, 0.5)
        c = cols[rng.integers(len(cols))] * rng.uniform(0.82, 1.15)
        sc = straw_cols[rng.integers(len(straw_cols))] * rng.uniform(0.85, 1.1)
        keep, dead, w = rng.random(), rng.random(), rng.choice([1.0, 1.0, 1.5])
        px, py = int(x / k) % S, int(y / k) % S
        if field is not None and keep > field[py, px]:
            return
        if straw is not None and dead < straw[py, px]:
            c = sc
        x1, y1 = x + np.cos(a) * L * 0.55, y + np.sin(a) * L * 0.55
        x2, y2 = x1 + np.cos(a + bend) * L * 0.45, y1 + np.sin(a + bend) * L * 0.45
        d.line([(x, y), (x1, y1), (x2, y2)], fill=tuple(int(v) for v in np.clip(c, 0, 255)) + (255,),
               width=max(1, int(w * k)))
    return draw


def _grass_cover(img, rng, density, straw, n=(900, 1300)):
    """Two layers of blades: darker ones underneath, lighter on top, each casting a little shadow."""
    under, cu = _strokes(rng, n[0], _blade(tuple(tuple(int(v * 0.72) for v in c) for c in GRASS_COLS),
                                         field=density, straw=straw))
    img = G.over(img, under, cu * 0.95)
    top, ct = _strokes(rng, n[1], _blade(GRASS_COLS, field=density * 0.9, straw=straw, length=(3.0, 8.0)))
    shadow = np.roll(G.blur(ct, 1.0), (1, 1), (0, 1)) * (1 - ct)
    img = img * (1 - 0.35 * shadow[..., None])
    return G.over(img, top, ct)


def yard_grass_base():
    """Uneven yard grass, thin and flattened by rain, mud showing through the thin spots."""
    rng = G.rng_for(39001)
    img = _earth(rng, MUD, clods=5.0)
    density = G.smoothstep(-1.3, 0.6, G.fnoise(rng, beta=2.4))
    straw = 0.25 * G.smoothstep(0.2, 1.5, G.fnoise(rng, beta=2.6))
    img = _grass_cover(img, rng, density, straw)
    img = G.add_lum(img, G.fnoise(rng, beta=3.0) * 4)
    return _wet_sheen(img, rng, 0.6)


def yard_grass_marks(i, rng, img):
    if i == 1:                                             # a bare trodden muddy patch
        m, _, _ = _lmask(rng, (20, 16))
        mud = _wet_sheen(_earth(rng, (58, 48, 40)), rng, 1.5)
        mud = _lay_pebbles(mud, rng, 50, DARK_STONES, (0.8, 2.0), sink=0.5)
        return G.lerp(img, mud, (G.smoothstep(0.05, 0.5, m) * (0.85 + 0.15 * G.fnoise(rng, beta=1.0)))[..., None])
    if i == 2:                                             # a patch of dead, bleached straw
        m, _, _ = _lmask(rng, (24, 20), rough=0.6)
        straw, cs = _strokes(rng, 900, _blade(STRAW_COLS, angle=0.6, spread=0.9, length=(4.0, 11.0)))
        return G.over(img, straw, cs * m)
    m, _, _ = _lmask(rng, (16, 12), rough=0.7, lo=0.35, hi=0.65)   # a shallow rain puddle in the grass
    return _puddle(img, rng, m)


def tex_l39_yard_grass_side() -> None:
    """Soil with the yard grass hanging over its top edge (tiles in x), and the slab version:
    a half slab shows only half of its side texture (the top half for an upper slab, the bottom
    half for a lower one), so l39_grass_slab_side carries the grass edge at the top of both halves."""
    rng = G.rng_for(39002)
    soil = soil_img()
    grass = yard_grass_base()
    fringe = 18 + 7 * np.clip(G.fnoise(rng, (1, S), beta=0.7), -1.6, 1.6)
    hang = (rng.random(S) < 0.25) * rng.uniform(2, 9, S)               # single blades hanging lower
    edge = fringe + G.blur(hang[None, :], 0.6)
    img = G.lerp(soil, grass, (Y < edge).astype(float)[..., None])
    img = G.add_lum(img, -18 * np.exp(-((Y - edge) / 2.2) ** 2) * (Y >= edge))
    _save(img, "l39_yard_grass_side")
    _save(np.concatenate([img[:S // 2], img[:S // 2]], axis=0), "l39_grass_slab_side")


def _root_strands(length, cols=((64, 48, 36), (82, 64, 48), (98, 80, 60), (52, 40, 32)),
                  width=(0.6, 1.6), start=None, down=True):
    """Drawer for _strokes: one wandering root, thick at its start and tapering, with a side
    rootlet or two. down=True: it hangs mostly downwards (strands hanging into a hole); False: it
    wanders any way (roots cut through by a soil face)."""
    cols = np.asarray(cols, float)

    def draw(d, r, x, y, k):
        c = tuple(int(v) for v in np.clip(cols[r.integers(len(cols))] * r.uniform(0.85, 1.15), 0, 255)) + (255,)
        L = r.uniform(*length) * k
        ang = np.pi / 2 + r.normal(0, 0.35) if down else r.uniform(0, 2 * np.pi)
        w0 = r.uniform(*width) * k
        if start is not None:
            y = start * k
        pts, px, py = [(x, y)], x, y
        steps = 10
        for s in range(steps):
            ang += r.normal(0, 0.28)
            if down:
                ang = np.pi / 2 + np.clip(ang - np.pi / 2, -0.9, 0.9)
            px, py = px + np.cos(ang) * L / steps, py + np.sin(ang) * L / steps
            pts.append((px, py))
        for s in range(steps):
            d.line([pts[s], pts[s + 1]], fill=c, width=max(1, int(w0 * (1 - 0.75 * s / steps))))
        for _ in range(int(r.integers(0, 3))):
            s = int(r.integers(2, steps - 1))
            a2 = ang + r.choice([-1, 1]) * r.uniform(0.5, 1.1)
            l2 = L * r.uniform(0.15, 0.35)
            d.line([pts[s], (pts[s][0] + np.cos(a2) * l2, pts[s][1] + np.sin(a2) * l2)], fill=c, width=max(1, int(0.5 * k)))
    return draw


def soil_img():
    """Subsoil: dark brown earth, crumbly clods, plenty of small stones and roots running through
    it (cut where the hole's wall is), with pale dead root hairs."""
    rng = G.rng_for(39003)
    img = _earth(rng, SOIL, clods=7.0)
    img = G.add_lum(img, G.fnoise(rng, beta=1.6, aniso=(4, 1), kmin=3) * 3)    # faint layering
    img = _lay_pebbles(img, rng, 240, CLOD_COLS, (1.0, 2.8), shade=10.0, sink=0.15)   # crumbly clods
    roots, cr = _strokes(rng, 34, _root_strands((18, 46), down=False, width=(0.8, 2.2)))
    img = img * (1 - 0.3 * np.roll(G.blur(cr, 1.0), (1, 1), (0, 1))[..., None] * (1 - cr[..., None]))
    img = G.over(img, roots, cr * 0.9)
    hairs, ch = _strokes(rng, 60, lambda d, r, x, y, k: d.line(
        [(x + r.normal(0, 3) * k * j, y + 2.4 * k * j) for j in range(6)],
        fill=(118, 104, 86, 255), width=max(1, int(0.6 * k))))
    img = G.over(img, hairs, ch * 0.55)
    img = _lay_pebbles(img, rng, 80, DARK_STONES, (1.0, 3.0), shade=9.0, sink=0.3)
    cut = G.smoothstep(0.92, 0.99, 1 - np.abs(G.blur(G.fnoise(rng, beta=2.0, kmin=4), 0.6)))
    return G.add_lum(img, -8 * cut)


def tex_l39_soil() -> None:
    _save(soil_img(), "l39_soil")


def yard_dirt_base():
    """Bare wet yard dirt where nothing grows: darker, a few stones and stray flattened blades."""
    rng = G.rng_for(39004)
    img = _earth(rng, (70, 59, 48), clods=6.0)
    img = _lay_pebbles(img, rng, 150, tuple(tuple(int(v * 0.92) for v in c) for c in CLOD_COLS), (1.0, 2.6),
                       shade=9.0, sink=0.2)
    img = _lay_pebbles(img, rng, 40, DARK_STONES, (1.0, 2.6), sink=0.4)
    blades, cb = _strokes(rng, 70, _blade(GRASS_COLS + STRAW_COLS, length=(3.0, 7.0)))
    img = G.over(img, blades, cb * 0.85)
    return _wet_sheen(img, rng, 1.0)


def yard_dirt_marks(i, rng, img):
    if i == 1:                                             # a puddle
        m, _, _ = _lmask(rng, (20, 14), rough=0.6, lo=0.3, hi=0.6)
        return _puddle(img, rng, m)
    m, _, _ = _lmask(rng, (22, 20), rough=0.6)             # a clump of weeds and twigs
    blades, cb = _strokes(rng, 500, _blade(GRASS_COLS + STRAW_COLS, length=(4.0, 10.0)))
    img = G.over(img, blades, cb * m)
    twigs, ct = _strokes(rng, 8, lambda d, r, x, y, k: d.line(
        [(x, y), (x + r.normal(0, 8) * k, y + r.normal(0, 8) * k)], fill=(52, 42, 34, 255), width=max(1, int(k))))
    return G.over(img, twigs, ct * G._edge_fade())


PATH_MEAN = (92, 82, 70)       # #5C5246: brown-grey packed earth
PATH_STONES = ((104, 98, 88), (90, 84, 76), (118, 110, 98), (80, 76, 70), (126, 120, 108), (98, 88, 78))


def _hoofprints(rng, n, depth=1.0):
    """Height field (negative = dent) of n scattered, half-trodden-out hoof prints (wrapped)."""
    h = np.zeros((S, S))
    for _ in range(n):
        cx, cy, ang = rng.uniform(0, S), rng.uniform(0, S), rng.uniform(0, 2 * np.pi)
        dx, dy = G.wrapd(X - cx, S), G.wrapd(Y - cy, S)
        u = dx * np.cos(ang) + dy * np.sin(ang)
        v = -dx * np.sin(ang) + dy * np.cos(ang)
        rr = np.hypot(u / 1.1, v)
        shoe = np.clip(1 - np.abs(rr - 4.2) / 1.2, 0, 1) * (u > -2.5)    # the U of the shoe
        frog = np.clip(1 - rr / 3.0, 0, 1) * 0.5
        h -= (shoe + frog) * depth * rng.uniform(0.4, 1.0)
    return G.blur(h, 0.6)


def _path_earth(rng, mean, stones=170, damp=0.16):
    """Packed brown-grey path earth: small stones pressed in, a few old hoof prints, dried
    cracks and darker damp patches. The average colour is pulled onto `mean`."""
    img = _earth(rng, mean, clods=3.0, grain=5.0)
    img = G.add_lum(img, G.fnoise(rng, beta=1.4, aniso=(1, 5), kmin=2) * 3)   # faint wear along the path
    img = _lay_pebbles(img, rng, stones, PATH_STONES, (0.6, 1.9), shade=7.0, sink=0.35)
    h = _hoofprints(rng, 7, 1.2)
    img = G.add_lum(img, _relief(h, 9))
    img = img * (1 - 0.15 * G.smoothstep(0.2, 1.5, -h)[..., None])
    cr = 1 - np.abs(G.fnoise(rng, beta=2.0, kmin=3))
    img = G.add_lum(img, -10 * G.smoothstep(0.94, 0.99, cr))           # dried compaction cracks
    wet = G.smoothstep(0.2, 1.6, G.fnoise(rng, beta=2.4, kmin=2))
    img = img * (1 - damp * wet[..., None])
    return _wet_sheen(_to_mean(img, mean), rng, 0.6, 0.4 + wet)


def path_dirt_base():
    """Packed path earth (the town has no asphalt), #5C5246 on average."""
    return _path_earth(G.rng_for(39005), PATH_MEAN)


def _rut_mask(cx, w, wobble):
    return np.exp(-((X - cx - wobble) / w) ** 2)


def path_dirt_marks(i, rng, img):
    fade = G._edge_fade(10)
    if i == 1:                                             # two cart/tyre ruts along the path
        wob = 2.0 * G.fnoise(rng, (S, 1), beta=2.0)
        h = np.zeros((S, S))
        for cx in (rng.uniform(26, 36), rng.uniform(88, 100)):
            h -= _rut_mask(cx, rng.uniform(5, 7), wob) * 3.0
            h += _rut_mask(cx - 9, 2.5, wob) * 0.8 + _rut_mask(cx + 9, 2.5, wob) * 0.8   # pushed-up lips
        tread = (np.sin(Y * 2 * np.pi / 5.0) > 0.4) * (h < -1.5)
        h = (h - tread * 0.4) * fade
        img = G.add_lum(img, _relief(h, 12))
        wet = G.smoothstep(-1.2, -2.6, h)
        img = img * (1 - 0.3 * wet[..., None])
        return _wet_sheen(img, rng, 3.0, wet)
    if i == 2:                                             # a wet dark patch with a sheen of water
        m, _, _ = _lmask(rng, (26, 20), rough=0.5)
        img = img * (1 - 0.32 * m[..., None])
        pm = G.smoothstep(0.65, 0.85, m * (1 + 0.3 * G.fnoise(rng, beta=2.0)))
        return _wet_sheen(_puddle(img, rng, pm), rng, 2.0, m)
    # a line of boot prints walking across, half of them with a hoof print beside
    h = np.zeros((S, S))
    ang = rng.uniform(0, np.pi)
    ca, sa = np.cos(ang), np.sin(ang)
    for j in range(-2, 3):
        side = 1 if j % 2 else -1
        px = 64 + ca * j * 20 - sa * side * 6
        py = 64 + sa * j * 20 + ca * side * 6
        u = (X - px) * ca + (Y - py) * sa
        v = -(X - px) * sa + (Y - py) * ca
        sole = ((u - 3) / 6.5) ** 2 + (v / 3.4) ** 2 < 1
        heel = ((u + 7) / 3.0) ** 2 + (v / 3.0) ** 2 < 1
        lugs = (np.sin(u * 2.2) > 0.3) * sole
        h -= G.blur((sole | heel) * 3.0 + lugs * 0.8, 0.6)
    img = G.add_lum(img, _relief(h * fade, 18))
    return img * (1 - 0.3 * G.smoothstep(0.3, 2.0, -h * fade)[..., None])


def path_gravel_base():
    """Darker stony stretches of the paths: brown-grey earth crowded with stones and a few rocks."""
    rng = G.rng_for(39006)
    img = _earth(rng, (70, 62, 54), clods=2.0)
    stones = tuple(tuple(int(v * 0.86) for v in c) for c in PATH_STONES)
    img = _lay_pebbles(img, rng, 380, stones, (1.2, 3.6), shade=10.0, sink=0.15)
    img = _lay_pebbles(img, rng, 16, stones, (4.0, 6.5), shade=11.0, sink=0.2)        # a few bigger rocks
    img = img * (1 - 0.2 * G.smoothstep(0.2, 1.6, G.fnoise(rng, beta=2.6))[..., None])
    return _wet_sheen(_to_mean(img, (80, 72, 62)), rng, 1.0)


def path_gravel_marks(i, rng, img):
    if i == 1:                                             # wet dark hollow where rain collects
        m, _, _ = _lmask(rng, (24, 20))
        img = img * (1 - 0.35 * m[..., None])
        return _wet_sheen(img, rng, 4.0, m)
    m, _, _ = _lmask(rng, (22, 18), rough=0.6)             # gravel scraped away down to the mud
    mud = _earth(rng, (60, 50, 42))
    mud = _lay_pebbles(mud, rng, 60, DARK_STONES, (1.0, 2.6), sink=0.4)
    return G.lerp(img, mud, G.smoothstep(0.2, 0.8, m)[..., None])


def tex_l39_path_grass() -> None:
    """The grassy strip down the middle of a little-used path: sparse, flattened grass over
    trodden mud, with straw and a few stones."""
    rng = G.rng_for(39009)
    img = _path_earth(rng, (74, 64, 52), stones=90, damp=0.3)
    density = 0.25 + 0.5 * G.smoothstep(-1.2, 1.0, G.fnoise(rng, beta=2.4))
    straw = 0.35 + 0.25 * G.smoothstep(0.0, 1.5, G.fnoise(rng, beta=2.6))
    img = _grass_cover(img, rng, density, straw, n=(500, 800))
    _save(_wet_sheen(img, rng, 0.6), "l39_path_grass")


def tex_l39_path_mud() -> None:
    """Dark churned mud of the ruts and puddle rims, wetter: glossy lumps, water in the hollows."""
    rng = G.rng_for(39007)
    img = _earth(rng, (48, 40, 33), clods=2.0)
    h = G.fnoise(rng, beta=2.8, kmin=2) + 0.35 * G.fnoise(rng, beta=1.8, kmin=6)
    hc, _, _, _ = _pebbles(rng, 160, (1.5, 4.5), flat=(0.4, 0.9))      # churned lumps
    hh = G.blur(h * 3 + hc * 0.9 + _hoofprints(rng, 5, 1.5) * 2, 0.6)
    rel = _relief(hh, 1.0)
    img = G.add_lum(img, rel * 10)
    img = img * (0.78 + 0.22 * G.smoothstep(-1.0, 1.0, h))[..., None]
    spec = G.smoothstep(np.percentile(rel, 88), np.percentile(rel, 99.5), rel)
    img = G.add_lum(img, spec * 36, (0.9, 0.95, 1.0))                  # glossy wet highlights
    img = _lay_pebbles(img, rng, 30, DARK_STONES, (0.8, 2.2), sink=0.6)
    hollow = G.smoothstep(-0.6, -1.5, G.blur(h, 1.5))                   # water standing in the hollows
    img = img * (1 - 0.35 * hollow[..., None])
    img = _wet_sheen(img, rng, 4.0, hollow)
    _save(_wet_sheen(_to_mean(img, (46, 38, 32)), rng, 1.4), "l39_path_mud")


def _voronoi(rng, n, jitter=0.8):
    """Wrapped jittered-grid Voronoi cells: (cell id, distance in px to the nearest cell border)."""
    T = S / n
    g = np.arange(n)
    px = ((g[None, :] + 0.5 + rng.uniform(-jitter / 2, jitter / 2, (n, n))) * T).ravel()
    py = ((g[:, None] + 0.5 + rng.uniform(-jitter / 2, jitter / 2, (n, n))) * T).ravel()
    ds = np.stack([np.hypot(G.wrapd(X - x, S), G.wrapd(Y - y, S)) for x, y in zip(px, py)])
    order = np.argsort(ds, axis=0)
    d = np.take_along_axis(ds, order[:2], axis=0)
    return order[0], (d[1] - d[0]) / 2


def tex_l39_rock() -> None:
    """Grey packed rock between the soil and the vaults: fractured plates, bedding, pale calcite
    veins, pebbles and damp."""
    rng = G.rng_for(39008)
    cid, edge = _voronoi(rng, 4, 0.95)
    fid, fedge = _voronoi(rng, 9, 0.95)
    img = G._smooth_concrete(rng, (100, 98, 94), mottle=6.0, fine=2.2)
    img = G.add_lum(img, rng.normal(0, 1, 16)[cid] * 5, (1.0, 0.98, 0.95))
    img = G.add_lum(img, G.fnoise(rng, beta=2.0, aniso=(8, 1), kmin=2) * 8)      # bedding layers
    open_ = G.smoothstep(-0.4, 0.5, G.fnoise(rng, beta=2.0, kmin=2))            # cracks open here, closed there
    open_f = G.smoothstep(0.2, 1.0, G.fnoise(rng, beta=2.0, kmin=3))
    h = G.fnoise(rng, beta=2.2, kmin=3) * 1.4 + G.smoothstep(0, 3, edge) * open_ * 1.2
    img = G.add_lum(img, _relief(h, 12))
    img = G.add_lum(img, -44 * G.smoothstep(1.1, 0.2, edge) * open_ - 18 * G.smoothstep(0.7, 0.15, fedge) * open_f)
    vein = G.smoothstep(0.975, 0.995, 1 - np.abs(G.fnoise(rng, beta=2.0, kmin=3)))
    img = G.over(img, np.array([176, 172, 160]), vein * 0.5)
    img = _lay_pebbles(img, rng, 30, DARK_STONES, (1.0, 3.0), shade=8.0, sink=0.5)
    damp = G.smoothstep(0.6, 1.8, G.fnoise(rng, beta=2.6))
    _save(img * (1 - 0.18 * damp[..., None]), "l39_rock")


def _cutout(rgb, cov):
    """RGBA with alpha strictly 0 or 255 (Minecraft's cutout can't do partial transparency)."""
    return np.concatenate([rgb, np.where(cov >= 0.5, 255.0, 0.0)[..., None]], axis=2)


def tex_l39_rubble() -> None:
    """The rubble in a hole's wall: soil packed with broken, angular chunks of rock and the odd
    bit of old brick, each chunk a flat facet catching the light its own way."""
    rng = G.rng_for(39080)
    soil = _lay_pebbles(_earth(rng, (64, 52, 42), clods=5.0), rng, 60, CLOD_COLS, (1.0, 2.4), sink=0.2)
    cid, edge = _voronoi(rng, 5, 0.95)
    fid, fedge = _voronoi(rng, 11, 0.95)                              # facets inside the chunks
    nc, nf = 25, 121
    rock = (rng.random(nc) < 0.7)[cid]
    pal = np.array([(96, 92, 86), (104, 92, 78), (74, 72, 70), (88, 86, 82), (112, 108, 100), (80, 54, 46)], float)
    kind = rng.choice(len(pal), nc, p=[0.26, 0.18, 0.2, 0.2, 0.12, 0.04])
    col = pal[kind][cid] + (rng.normal(0, 1, nc) * 5)[cid][..., None]
    facet = (rng.normal(0, 1, nf) * 11)[fid]
    chunk = G.add_lum(col, facet + G.fnoise(rng, beta=1.6, kmin=6) * 4 + G.blur(rng.normal(0, 2.0, (S, S)), 0.5))
    chunk = G.add_lum(chunk, -14 * G.smoothstep(1.0, 0.2, fedge))      # facet ridges
    m = rock * G.smoothstep(0.8, 2.2, edge + 0.9 * G.blur(G.fnoise(rng, beta=2.2, kmin=4), 0.8))
    img = soil * (1 - 0.45 * np.roll(G.blur(m, 1.4), (2, 2), (0, 1))[..., None] * (1 - m[..., None]))
    img = G.over(img, chunk, m)
    img = G.add_lum(img, _relief(G.blur(m, 0.7) * 3, 12))
    _save(_wet_sheen(img, rng, 0.6), "l39_rubble")


def tex_l39_clay() -> None:
    """A band of grey-ochre clay in a hole's wall: smooth and slick, iron-stained streaks, grey
    mottles, fine drying cracks, smears where something slid down it."""
    rng = G.rng_for(39081)
    img = G.add_lum(_fill((110, 102, 84)), G.fnoise(rng, beta=3.0) * 5 + G.fnoise(rng, beta=1.6, aniso=(6, 1), kmin=3) * 4)
    ochre = G.smoothstep(0.3, 1.6, G.blur(G.fnoise(rng, beta=2.2, aniso=(3, 1), kmin=2), 1.0))
    img = G.over(img, np.array([152, 112, 62]), ochre * 0.45)
    grey = G.smoothstep(0.5, 1.7, G.blur(G.fnoise(rng, beta=2.4, kmin=3), 1.0))
    img = G.over(img, np.array([108, 108, 104]), grey * 0.5)
    img = G.add_lum(img, _relief(G.blur(G.fnoise(rng, beta=2.0, aniso=(1, 3), kmin=3), 1.2), 8))   # slick smears
    cid, edge = _voronoi(rng, 5, 0.9)
    crack = G.smoothstep(0.9, 0.3, edge) * G.smoothstep(0.3, 1.0, G.fnoise(rng, beta=2.0, kmin=2))
    img = G.add_lum(img, -34 * crack + 8 * np.roll(crack, -1, 0) * (1 - crack))
    pores = (rng.random((S, S)) < 0.008) * 1.0
    img = G.add_lum(img, -G.blur(pores, 0.5) * 40)
    img = _lay_pebbles(img, rng, 12, PATH_STONES, (0.8, 1.8), sink=0.5)
    spec = G.smoothstep(1.2, 2.2, _relief(G.blur(G.fnoise(rng, beta=2.0, kmin=3), 1.0), 1.0) * 6)
    _save(G.add_lum(img, spec * 14, (0.95, 0.97, 1.0)), "l39_clay")


def tex_l39_rock_layered() -> None:
    """Stratified grey-brown rock in the deep part of a hole: bands of different thickness,
    colour and grain, the soft ones weathered back under the hard ones, vertical joints."""
    rng = G.rng_for(39082)
    thick = []
    while sum(thick) < S - 8:
        thick.append(float(rng.uniform(5, 24)))
    thick[-1] += S - sum(thick)
    tops = np.concatenate([[0.0], np.cumsum(thick)[:-1]])
    nb = len(thick)
    pal = np.array([(110, 104, 96), (92, 86, 78), (124, 112, 96), (78, 74, 70), (104, 92, 78), (132, 124, 112)], float)
    wob = 2.5 * G.fnoise(rng, (1, S), beta=2.6)
    yy = (Y + wob) % S
    band = np.clip(np.searchsorted(tops, yy, side="right") - 1, 0, nb - 1)
    vb = (yy - tops[band]) / np.asarray(thick)[band]                       # 0 at a band's top .. 1 at its bottom
    col = pal[rng.integers(len(pal), size=nb)][band] + (rng.normal(0, 6, nb))[band][..., None]
    grit = np.array(rng.random(nb) < 0.45)[band]
    soft = np.array(rng.random(nb) < 0.4)[band]
    img = G.add_lum(col, G.fnoise(rng, beta=1.4, aniso=(8, 1), kmin=3) * 5 + G.fnoise(rng, beta=2.6) * 4)
    img = G.add_lum(img, grit * G.blur(rng.normal(0, 7, (S, S)), 0.6))
    img = img * (1 - 0.22 * soft * G.smoothstep(0.0, 0.5, vb))[..., None]          # soft beds weathered back
    img = G.add_lum(img, -26 * G.smoothstep(0.08, 0.0, vb) * soft - 18 * G.smoothstep(0.92, 1.0, vb)
                    + 10 * G.smoothstep(0.85, 0.97, vb) * (1 - soft))                 # overhang shadows, lit lips
    joints = np.zeros((S, S))
    for b in range(nb):
        for _ in range(int(rng.integers(1, 4))):
            jx = rng.uniform(0, S)
            joints = np.maximum(joints, (band == b) * np.clip(1 - np.abs(G.wrapd(X - jx - 2 * vb, S)) / 0.8, 0, 1))
    img = G.add_lum(img, -36 * joints)
    seep = G.smoothstep(0.5, 1.6, G.fnoise(rng, beta=1.2, aniso=(1, 10), kmin=3)) * G.smoothstep(0.6, 1.0, vb)
    img = G.over(img, np.array([58, 52, 46]), seep * 0.35)
    _save(_wet_sheen(_to_mean(img, (94, 88, 80)), rng, 0.6), "l39_rock_layered")


def _hanging_roots(seed, count, length, start=0.0):
    rng = G.rng_for(seed)
    return _strokes(rng, count, _root_strands(length, start=start, width=(0.7, 1.8)))


def tex_l39_roots() -> None:
    """Roots hanging into a hole (cross plant, cutout): fine dark-brown strands from the top edge,
    tapering, wandering down, some with side rootlets and a clod of soil caught in them."""
    rgb, cov = _hanging_roots(39083, 56, (50, 125))
    rng = G.rng_for(39084)
    for _ in range(5):                                             # clods of soil caught in the roots
        cx, cy = rng.uniform(10, 118), rng.uniform(10, 60)
        b = G.smoothstep(1.0, 0.6, np.hypot((X - cx) / rng.uniform(2, 4), (Y - cy) / rng.uniform(1.5, 3)))
        b = b * (G.blur(cov, 2.0) > 0.15)
        rgb = G.lerp(rgb, np.array([70, 56, 44]) + G.fnoise(rng, beta=1.0, kmin=10)[..., None] * 6, b[..., None])
        cov = np.maximum(cov, b)
    rgb = G.add_lum(rgb, -G.smoothstep(0, S, Y) * 12)               # darker further down, out of the light
    G.save_png(_cutout(rgb, cov), _blk("l39_roots"))


def tex_l39_grass_overhang_edge() -> None:
    """The ragged front of a turf lip (cutout): grass drooping over the top, a band of soil and
    root mat with a torn lower edge, roots dangling out of it. Row 0 = the top of the turf."""
    rng = G.rng_for(39085)
    soil = _lay_pebbles(_earth(rng, (62, 50, 40), clods=6.0), rng, 40, CLOD_COLS, (1.0, 2.6), sink=0.2)
    mat, cm = _strokes(rng, 40, _root_strands((10, 30), down=False, width=(0.6, 1.4)))
    soil = G.over(soil, mat, cm * 0.8)
    edge = 54 + 8 * G.fnoise(rng, (1, S), beta=2.0, kmin=2) + 2 * G.fnoise(rng, (1, S), beta=1.0, kmin=8)
    body = G.smoothstep(edge + 0.6, edge - 0.6, Y)
    soil = G.add_lum(soil, -26 * G.smoothstep(edge - 8, edge, Y))     # the underside curls away into shadow
    rgb, cov = soil, body
    roots, cr = _strokes(rng, 32, _root_strands((30, 72), start=50.0, width=(0.7, 1.8)))
    rgb = G.lerp(rgb, roots, (cr * (1 - body))[..., None])
    cov = np.maximum(cov, cr)
    grass = yard_grass_base()
    top = G.smoothstep(16, 6, Y + 4 * G.fnoise(rng, (1, S), beta=0.8, kmin=3))
    rgb = G.lerp(rgb, grass, top[..., None])
    blades, cb = _strokes(rng, 160, _blade(GRASS_COLS + STRAW_COLS, angle=np.pi / 2, spread=0.45, length=(6.0, 16.0)))
    cb = cb * G.smoothstep(26, 4, Y)
    rgb = G.lerp(rgb, blades, cb[..., None])
    cov = np.maximum(cov, cb)
    G.save_png(_cutout(rgb, cov), _blk("l39_grass_overhang_edge"))


# =============================================================================
# Clapboard
# =============================================================================

CLAP_FRESH = (150, 178, 196)
CLAP_FADED = (164, 182, 190)
CLAP_WEATHERED = (146, 158, 164)
BARE_WOOD = (102, 98, 90)


def _clap_structure(rng):
    """Lap-board shading (8 boards of 16 px per block): the shadow under each lap, the board's
    outward tilt, a lit bottom edge, per-board tone, and staggered butt joints."""
    v = (Y % 16) / 16
    board = (Y // 16).astype(int) % 8
    shade = 0.88 + 0.14 * v
    shade = shade - 0.28 * G.smoothstep(1.8, 0.3, Y % 16)               # under the lap above
    shade = shade + 0.07 * G.smoothstep(13.5, 15.5, Y % 16) * (Y % 16 < 15.6)
    joints = rng.uniform(0, S, 8)
    jd = np.abs(G.wrapd(X - joints[board], S))
    shade = shade - 0.2 * G.smoothstep(1.0, 0.3, jd) + 0.05 * G.smoothstep(2.2, 1.0, jd) * (X > joints[board])
    tone = rng.normal(0, 0.025, 8)[board]
    return shade + tone, board, jd


def _paint_peel(rng, amount, scale=1.0):
    """Peeling mask (0..1): flakes elongated along the grain, biased to the bottom of each board
    where water sits."""
    v = (Y % 16) / 16
    n = G.blur(0.75 * G.fnoise(rng, beta=1.3, aniso=(4 * scale, 1), kmin=2)
               + 0.55 * G.fnoise(rng, beta=1.3, aniso=(5 * scale, 1), kmin=6), 0.6) + 0.5 * v
    return G.smoothstep(2.4 - amount, 2.6 - amount, n)


def _clapboard(seed, col, chalk=0.1, peel=0.0, mildew=0.2):
    rng = G.rng_for(seed)
    shade, board, jd = _clap_structure(rng)
    grain = G.fnoise(rng, beta=1.6, aniso=(12, 1), kmin=2) * 3 + G.fnoise(rng, beta=0.8, aniso=(12, 1), kmin=8) * 1.2
    paint = G.add_lum(_fill(col), grain + G.fnoise(rng, beta=3.0) * 3)
    paint = G.lerp(paint, np.array([200, 206, 206]), chalk * (0.6 + 0.4 * G.smoothstep(-1, 1, G.fnoise(rng, beta=2.4)))[..., None])
    wood = G.add_lum(_fill(BARE_WOOD), G.fnoise(rng, beta=1.4, aniso=(14, 1), kmin=2) * 9
                     + G.fnoise(rng, beta=0.6, aniso=(16, 1), kmin=10) * 4)
    img = paint
    if peel > 0:
        p = _paint_peel(rng, peel)
        img = G.over(img, wood, p)
        edge = np.clip(G.blur(p, 0.8) - p, 0, 1)
        img = G.add_lum(img, 28 * edge - 18 * np.roll(edge, -1, 0))      # lifted paint lip + its shadow
    img = img * shade[..., None]
    nails = np.zeros((S, S))
    for nx in (10.5, 74.5):                                           # nails at the studs
        nails += G.box((S, S), nx - 0.8, 0, nx + 0.8, S) * (np.abs((Y % 16) - 12.5) < 0.9)
    img = G.add_lum(img, -40 * nails)
    rust = nails * 0
    for nx in (10.5, 74.5):
        run = np.exp(-((X - nx) / 0.9) ** 2) * G.smoothstep(12.5, 13.5, Y % 16) * np.exp(-((Y % 16) - 12.5) / 6)
        rust = rust + run * (rng.random(8)[board] < 0.35)
    img = G.over(img, np.array([104, 78, 58]), np.clip(rust, 0, 0.6))
    v = (Y % 16) / 16
    mild = G.smoothstep(0.2, 1.6, G.fnoise(rng, beta=2.4)) * (0.3 + 0.7 * G.smoothstep(0.75, 1.0, v))
    img = G.over(img, np.array([72, 80, 66]), mild * mildew)
    streaks = G.fnoise(rng, beta=1.2, aniso=(1, 10), kmin=2)
    img = G.add_lum(img, streaks * 3.5)
    return G._weather(img, rng, dirt=0.12, scratches=3)


def clapboard_base():
    """Light-blue lap siding, the paint of every house in town: fairly fresh, a little chalky."""
    return _clapboard(39010, CLAP_FRESH, chalk=0.08, peel=0.15, mildew=0.16)


def clapboard_marks(i, rng, img):
    if i == 1:                                             # a sun/rain-faded patch: paler, chalkier
        m, _, _ = _lmask(rng, (28, 22), rough=0.5)
        chalk = G.add_lum(_fill((192, 202, 206)), G.fnoise(rng, beta=1.2, aniso=(10, 1), kmin=4) * 5)
        faded = img * (chalk / np.array(CLAP_FRESH, float) * 0.8 + 0.2)          # keeps the board shading
        return G.lerp(img, faded, (m * 0.85)[..., None])
    if i == 2:                                             # paint peeling off in a patch
        m, _, _ = _lmask(rng, (26, 20), rough=0.6)
        p = _paint_peel(rng, 1.6) * G.smoothstep(0.1, 0.5, m)
        ratio = _lum(img) / _lum(np.array(CLAP_FRESH, float))
        wood = G.add_lum(ratio[..., None] * np.array(BARE_WOOD, float),
                         G.fnoise(rng, beta=1.4, aniso=(14, 1), kmin=2) * 8)
        img = G.over(img, wood, p)
        edge = np.clip(G.blur(p, 0.8) - p, 0, 1)
        return G.add_lum(img, 30 * edge - 20 * np.roll(edge, -1, 0))
    m, _, _ = _lmask(rng, (30, 30), rough=0.4)              # mildew and rain streaks running down
    streak = G.smoothstep(0.4, 1.8, G.fnoise(rng, beta=1.0, aniso=(1, 14), kmin=3))
    run = np.exp(-(Y % 16) / 9)                             # strongest just below each lap
    img = G.over(img, np.array([66, 74, 64]), np.clip(streak * m * (0.35 + 0.4 * run), 0, 0.6))
    return G.over(img, np.array([84, 92, 74]), G.smoothstep(0.3, 1.5, G.fnoise(rng, beta=2.2)) * m * 0.25)


def tex_l39_clapboard_faded() -> None:
    _save(_clapboard(39011, CLAP_FADED, chalk=0.22, peel=0.5, mildew=0.24), "l39_clapboard_faded")


def tex_l39_clapboard_weathered() -> None:
    _save(_clapboard(39012, CLAP_WEATHERED, chalk=0.18, peel=1.25, mildew=0.4), "l39_clapboard_weathered")


# =============================================================================
# Mill brick, granite, roof
# =============================================================================

BRICK = (70, 44, 36)        # a single brick's face before the mean is pulled onto BRICK_MEAN
BRICK_H = S / 12          # 12 courses per block
MORTAR = (46, 42, 39)       # dark, sooty lime mortar
BRICK_MEAN = (58, 38, 32)   # #3A2620: agreed with L39Atmos so the far facade matches the real wall
SOOTY_MEAN = (42, 28, 24)   # #2A1C18
BLACK_MEAN = (26, 20, 18)   # #1A1412: the smoke-blackened upper storeys
GRANITE_MEAN = (90, 90, 92)  # #5A5A5C


def _to_mean(img, target):
    """Scale each colour channel so the picture's average lands exactly on target. The variation
    (and so all the detail) is kept; only the overall colour moves. Used where the colour has
    been agreed with another agent (the far facade has to match the real wall)."""
    mean = img.reshape(-1, 3).mean(axis=0)
    return img * (np.asarray(target, float) / np.maximum(mean, 1e-6))


def _brick_layout(Xs, Ys, h=BRICK_H, L=32.0, header_every=6, width=S):
    """Running-bond stretcher courses, with a course of half-length headers every `header_every`
    courses (American common bond; pass a huge number for plain running bond).
    Returns (row, brick id, distance to the nearest joint centre-line, v, header)."""
    row = np.floor(Ys / h)
    header = (row % header_every == 0)
    length = np.where(header, L / 2, L)
    off = np.where(header, L / 4, (row % 2) * L / 2)
    bx = (Xs + off) % width
    col = np.floor(bx / length)
    u = bx - col * length
    v = Ys - row * h
    dj = np.minimum(np.minimum(u, length - u), np.minimum(v, h - v))
    bid = ((row % 64) * 32 + col).astype(int)
    return row, bid, dj, v, header


def _brick_faces(rng, base, bid, header, dj, joint, mortar_col, clinker=0.08, chip=1.0, hue=1.0, burnt=(52, 34, 32)):
    """Shared brick shading: per-brick colour, burnt headers, clinkers, chipped arrises, recessed
    mortar with its shadow under each brick."""
    nb = int(bid.max()) + 1
    tone = rng.normal(0, 1, nb)[bid]
    shift = rng.normal(0, 1, nb)[bid] * hue
    clink = (rng.random(nb) < clinker)[bid]
    img = np.asarray(base, float) + tone[..., None] * np.array([7, 5, 4]) + shift[..., None] * np.array([4, -1, -2])
    img = G.lerp(img, np.array(burnt), (header * 0.45 + clink * 0.6)[..., None])   # burnt ends
    img = G.add_lum(img, G.fnoise(rng, img.shape[:2], beta=2.0, kmin=4) * 3.5 + rng.normal(0, 3.0, img.shape[:2]))
    pits = (rng.random(img.shape[:2]) < 0.025) * rng.uniform(0.5, 1, img.shape[:2])
    img = G.add_lum(img, -G.blur(pits, 0.45) * 30)
    ch = chip * G.smoothstep(0.8, 2.0, G.fnoise(rng, img.shape[:2], beta=1.2, kmin=6)) * 1.6
    de = dj - ch
    m = G.smoothstep(joint + 0.55, joint - 0.55, de)
    mort = G.add_lum(_fill(mortar_col, img.shape[:2]), rng.normal(0, 3.5, img.shape[:2]) + G.fnoise(rng, img.shape[:2], beta=2.0) * 3)
    hgt = G.smoothstep(joint - 0.3, joint + 2.2, de)
    img = G.over(img, mort, m)
    return G.add_lum(img, _relief(hgt, 14)), m


def _mill_brick(seed, base=BRICK, mortar=MORTAR, width=S, soot=0.25, mean=BRICK_MEAN, header_every=1000):
    """Running-bond mill brick; the result's average colour is pulled onto `mean`."""
    rng = G.rng_for(seed)
    Xs, Ys = G.grid((S, width))
    row, bid, dj, v, header = _brick_layout(Xs, Ys, width=width, header_every=header_every)
    img, m = _brick_faces(rng, base, bid, header, dj, 1.0, mortar, clinker=0.12)
    g = G.smoothstep(0.0, 1.8, G.fnoise(rng, (S, width), beta=2.4))
    img = G.over(img, np.array([22, 18, 18]), g * soot)                 # old coal smoke
    img = G.add_lum(img, G.fnoise(rng, (S, width), beta=1.2, aniso=(1, 10), kmin=2) * 2)
    return _to_mean(img, mean), bid, m


def mill_brick_base():
    """Old dark sooty red-brown mill brick (#3A2620 on average): running bond, dark mortar, soot."""
    return _mill_brick(39020)[0]


def mill_brick_marks(i, rng, img):
    if i == 1:                                             # soot streaks running down from above
        m = G.smoothstep(0.3, 1.6, G.fnoise(rng, beta=1.0, aniso=(1, 12), kmin=3))
        top = G.smoothstep(S, 0, Y) * G._edge_fade(8)
        lm, _, _ = _lmask(rng, (34, 40), rough=0.3)
        return G.over(img, np.array([16, 13, 13]), np.clip(m * (0.4 * top + 0.5 * lm), 0, 0.75))
    if i == 2:                                             # efflorescence: white salt bloom, mostly in the joints
        _, bid, dj, v, header = _brick_layout(X, Y)
        mortar = G.smoothstep(2.0, 0.8, dj)
        lm, _, _ = _lmask(rng, (32, 22), y_range=(0.4, 1.0), rough=0.6)
        salt = lm * (0.35 + 0.65 * mortar) * G.smoothstep(-0.6, 1.0, G.blur(G.fnoise(rng, beta=2.2, kmin=3), 0.8) * 1.4)
        return G.over(img, np.array([132, 128, 120]), np.clip(salt * 0.55, 0, 0.5))
    # patched: a group of bricks replaced with redder, cleaner newer ones in paler mortar
    patch, bid, m = _mill_brick(39021, base=(84, 48, 38), mortar=(74, 70, 64), soot=0.0, mean=(76, 46, 38))
    lm, cx, cy = _lmask(rng, (20, 14), rough=0.2, lo=0.3, hi=0.31)
    sel = np.isin(bid, np.unique(bid[lm > 0.5]))
    sel = sel * G._edge_fade(10)
    img = G.lerp(img, patch, sel[..., None])
    edge = np.clip(G.blur(sel.astype(float), 1.0) - sel, 0, 1)
    return G.over(img, np.array([92, 88, 82]), edge * 0.6)               # mortar smeared round the patch


def tex_l39_mill_brick_sooty() -> None:
    """The same brick blackened by coal smoke pouring from the windows below (#2A1C18), with
    darker streaks running down and a few bricks scrubbed paler by rain."""
    rng = G.rng_for(39022)
    img = _mill_brick(39023, soot=0.0)[0]
    s = G.smoothstep(-1.4, 1.2, G.fnoise(rng, beta=2.2) + 0.9 * G.fnoise(rng, beta=1.0, aniso=(1, 12), kmin=3))
    img = G.over(img, np.array([18, 14, 14]), 0.25 + 0.45 * s)
    img = G.add_lum(img, G.smoothstep(1.6, 2.2, G.fnoise(rng, beta=1.0, kmin=8)) * 8)
    _save(_to_mean(img, SOOTY_MEAN), "l39_mill_brick_sooty")


def tex_l39_mill_brick_black() -> None:
    """Upper-storey brick, nearly black (#1A1412): only a hint of the courses shows through."""
    rng = G.rng_for(39027)
    img = _mill_brick(39028, soot=0.0)[0]
    img = G.lerp(img, _fill((24, 20, 19)), 0.72)
    img = G.add_lum(img, G.fnoise(rng, beta=1.0, aniso=(1, 12), kmin=3) * 2.5 + G.fnoise(rng, beta=2.4) * 2)
    _save(_to_mean(img, BLACK_MEAN), "l39_mill_brick_black")


def tex_l39_mass() -> None:
    """The dark inside of the superstructure: rough, sooty rubble masonry nobody was meant to see."""
    rng = G.rng_for(39024)
    cid, edge = _voronoi(rng, 7, 0.95)
    stones = G._smooth_concrete(rng, (56, 50, 48), mottle=5.0, fine=2.4)
    stones = G.add_lum(stones, rng.normal(0, 1, 49)[cid] * 6, (1.0, 0.97, 0.95))
    h = G.smoothstep(0.5, 4.0, edge) * 2.5 + G.fnoise(rng, beta=1.8, kmin=4) * 0.6
    img = G.add_lum(stones, _relief(h, 12))
    mortar = G.add_lum(_fill((36, 32, 30)), rng.normal(0, 4, (S, S)))
    img = G.over(img, mortar, G.smoothstep(1.6, 0.6, edge))
    img = G.over(img, np.array([22, 18, 18]), G.smoothstep(0.3, 1.8, G.fnoise(rng, beta=2.2)) * 0.4)   # soot
    _save(G.add_lum(img, G.fnoise(rng, beta=1.2, aniso=(1, 10), kmin=2) * 3), "l39_mass")


def tex_l39_roof_tar() -> None:
    """Tarred flat roof: roll-roofing laps, gravel ballast sunk in black tar, blisters, puddles."""
    rng = G.rng_for(39025)
    img = G.add_lum(_fill((40, 40, 42)), G.fnoise(rng, beta=2.6) * 4 + rng.normal(0, 3, (S, S)))
    lap = Y % 64
    img = G.add_lum(img, -14 * G.smoothstep(2.0, 0.3, lap) + 8 * G.smoothstep(2.2, 0.8, np.abs(lap - 2.6)))
    img = _lay_pebbles(img, rng, 520, ((96, 94, 90), (78, 76, 74), (118, 114, 108), (64, 62, 60), (104, 98, 92)),
                       (0.8, 2.0), shade=8.0, sink=0.35)
    b = np.zeros((S, S))
    for _ in range(5):
        b += G.blob((S, S), rng.uniform(0, S), rng.uniform(0, S), rng.uniform(3, 6), rng.uniform(3, 5))
    img = G.add_lum(img, _relief(b * 3, 10))
    img = _puddle(img, rng, G.smoothstep(1.3, 1.7, G.fnoise(rng, beta=3.0)))
    _save(_wet_sheen(img, rng, 1.0), "l39_roof_tar")


def _granite(rng, shape=(S, S), weather=True):
    """Mid/dark grey speckled granite (#5A5A5C on average): black mica, pale feldspar, a few
    pinkish grains, faint tooling, darker weathering low down and lichen."""
    img = G.add_lum(_fill((96, 96, 98), shape), G.fnoise(rng, shape, beta=2.6) * 4)
    for col, thr, a in (((30, 30, 33), 1.6, 0.85), ((150, 150, 148), 1.4, 0.55), ((122, 108, 104), 1.7, 0.4),
                        ((72, 74, 78), 1.0, 0.45)):
        n = G.blur(rng.normal(0, 1, shape), 0.8)
        n = n / n.std()
        img = G.over(img, np.array(col), G.smoothstep(thr - 0.25, thr + 0.25, n) * a)
    img = G.add_lum(img, G.fnoise(rng, shape, beta=1.0, aniso=(10, 1), kmin=6) * 2.0)   # tooling marks
    if weather:
        Ys = G.grid(shape)[1]
        low = G.smoothstep(shape[0] * 0.55, shape[0], Ys)
        img = img * (1 - 0.12 * low * (0.7 + 0.3 * G.fnoise(rng, shape, beta=2.0)))[..., None]
        img = G.over(img, np.array([40, 38, 38]), G.smoothstep(0.6, 2.0, G.fnoise(rng, shape, beta=1.2, aniso=(1, 10), kmin=2)) * 0.25)
        lich = G.smoothstep(1.5, 2.1, G.fnoise(rng, shape, beta=2.0, kmin=4)) * (0.4 + low)
        img = G.over(img, np.array([84, 90, 74]), np.clip(lich, 0, 1) * 0.35)
    return _to_mean(img, GRANITE_MEAN)


def tex_l39_granite() -> None:
    _save(_granite(G.rng_for(39026)), "l39_granite")


# =============================================================================
# Mill windows
# =============================================================================

FRAME = (28, 28, 30)          # #1C1C1E: frame and muntins (agreed with L39Atmos)
GLASS_TINT = (214, 226, 236)  # cool pale glass; with the white gallery behind it reads ~#D8E2EA
MILL_W, MILL_H = 3 * S, 5 * S  # one mill window = 3 x 5 blocks
TOWER_W, TOWER_H = S, 3 * S    # one tower window = 1 x 3 pieces (the middle piece may repeat)


def _iron(rng, shape):
    """Dark painted cast iron of the frames and muntins: flaking paint, a little rust."""
    img = G.add_lum(_fill(FRAME, shape), G.fnoise(rng, shape, beta=2.0) * 2.5 + G.blur(rng.normal(0, 1.5, shape), 0.6))
    rust = G.smoothstep(1.3, 2.1, G.fnoise(rng, shape, beta=1.6, kmin=4))
    return G.over(img, np.array([74, 48, 34]), rust * 0.55)


def _crack_mask(shape, rng, cx, cy, n_arms, reach):
    """Star-shaped crack in a pane: a few wandering arms out of an impact point, part of a ring."""
    arms = []
    for k in range(n_arms):
        ang = 2 * np.pi * k / n_arms + rng.normal(0, 0.3)
        pts, x, y = [(cx, cy)], cx, cy
        for _ in range(int(rng.integers(4, 8))):
            ang += rng.normal(0, 0.25)
            step = reach * rng.uniform(0.12, 0.22)
            x, y = x + step * np.cos(ang), y + step * np.sin(ang)
            pts.append((x, y))
        arms.append(pts)
    ring = reach * rng.uniform(0.25, 0.4)

    def draw(d, k):
        for pts in arms:
            d.line([(px * k, py * k) for px, py in pts], fill=255, width=max(1, int(0.9 * k)))
        d.arc([(cx - ring) * k, (cy - ring) * k, (cx + ring) * k, (cy + ring) * k], 20, 300, fill=255,
              width=max(1, int(0.7 * k)))
    return G.pil_mask(draw, shape)


def _paint_glass(rng, shape, panes, sooty_from):
    """RGBA picture of a glazed sash.

    panes: list of (x0, y0, x1, y1) pane openings in px. Everything that isn't glass is dark iron
    (opaque). The glass is see-through (alpha ~95-140) with a cool tint, soft reflections and dust
    low in every pane; panes whose top is at or below sooty_from (px) get coal-soot smudges and
    drips, which are much more opaque. Two panes are cracked, one is patched with a sheet of tin
    and one is broken out, with only shards left in its corners."""
    Xs, Ys = G.grid(shape)
    n = len(panes)
    cov = np.zeros(shape)
    pid = np.full(shape, -1)
    pv = np.zeros(shape)                                    # 0 at a pane's top .. 1 at its bottom
    for k, (x0, y0, x1, y1) in enumerate(panes):
        b = G.box(shape, x0, y0, x1, y1, soft=0.5)
        cov = np.maximum(cov, b)
        sel = b > 0.5
        pid[sel] = k
        pv[sel] = ((Ys - y0) / (y1 - y0))[sel]
    has = pid >= 0
    p = np.where(has, pid, 0)
    lower = np.array([pn[1] >= sooty_from for pn in panes])[p] & has
    # Glass colour and opacity per pane (old glass is never quite even).
    bright = rng.uniform(0.95, 1.02, n)[p]
    alpha = rng.uniform(95, 135, n)[p]
    rgb = np.asarray(GLASS_TINT, float) * bright[..., None] + rng.normal(0, 1, n)[p][..., None] * np.array([-2, 0, 2])
    wave = G.blur(G.fnoise(rng, shape, beta=2.8, aniso=(1, 3)), 1.0)                  # cylinder-glass waviness
    rgb = G.add_lum(rgb, wave * 3)
    refl = G.smoothstep(0.2, 1.0, np.sin((Xs * 0.55 + Ys) / 46.0 + wave * 0.35)) * 0.45
    rgb = G.lerp(rgb, np.array([244, 248, 252]), (refl * 0.5)[..., None])          # soft reflection bands
    alpha = alpha + refl * 22
    dust = G.smoothstep(0.55, 1.0, pv) * (0.75 + 0.25 * G.blur(G.fnoise(rng, shape, beta=2.0, kmin=3), 1.5))
    rgb = G.lerp(rgb, np.array([150, 150, 146]), (dust * 0.35)[..., None])
    alpha = alpha + dust * 35
    # Coal soot on the lower sash: smudges, and runs down from the muntin above each pane.
    smudge = G.smoothstep(-0.2, 1.9, G.blur(G.fnoise(rng, shape, beta=3.0, kmin=2), 2.5))
    runs = G.smoothstep(0.5, 1.9, G.blur(G.fnoise(rng, shape, beta=1.3, aniso=(1, 14), kmin=3), 0.7)) * np.exp(-pv / 0.5)
    soot = np.clip(smudge * 0.7 + runs * 0.7 + dust * 0.45, 0, 1) * lower
    upper_dust = G.smoothstep(0.6, 2.2, G.blur(G.fnoise(rng, shape, beta=3.0, kmin=2), 2.5)) * (~lower & has)
    soot = np.maximum(soot, upper_dust * 0.35)
    rgb = G.lerp(rgb, np.array([40, 38, 38]), (soot * 0.85)[..., None])
    alpha = alpha + soot * 120
    # Cracked, patched and missing panes.
    order = rng.permutation(n)
    for k in order[:2]:
        x0, y0, x1, y1 = panes[k]
        cm = _crack_mask(shape, rng, rng.uniform(x0 + 0.3 * (x1 - x0), x1 - 0.3 * (x1 - x0)),
                         rng.uniform(y0 + 0.3 * (y1 - y0), y1 - 0.3 * (y1 - y0)), int(rng.integers(5, 8)),
                         min(x1 - x0, y1 - y0) * 0.55) * (pid == k)
        rgb = G.lerp(rgb, np.array([250, 252, 255]), (cm * 0.8)[..., None])
        alpha = np.maximum(alpha, cm * 210)
    x0, y0, x1, y1 = panes[order[2]]
    tin = pid == order[2]
    sheet = G.add_lum(_fill((78, 80, 82), shape), G.fnoise(rng, shape, beta=1.6, aniso=(1, 6)) * 6)
    sheet = G.over(sheet, np.array([96, 62, 40]), G.smoothstep(0.6, 1.8, G.fnoise(rng, shape, beta=1.8, kmin=3)) * 0.7)
    for nx in (x0 + 4, x1 - 4):
        for ny in (y0 + 4, y1 - 4):
            sheet = G.add_lum(sheet, -50 * np.exp(-((Xs - nx) ** 2 + (Ys - ny) ** 2) / 2.0))
    rgb = np.where(tin[..., None], sheet, rgb)
    alpha = np.where(tin, 255, alpha)
    x0, y0, x1, y1 = panes[order[3]]
    gone = pid == order[3]
    sh = [[(0, 0), (0.42, 0), (0.3, 0.12), (0.18, 0.1), (0.08, 0.34), (0, 0.38)],          # top-left
          [(1, 1), (0.48, 1), (0.62, 0.86), (0.78, 0.9), (0.86, 0.66), (1, 0.55)],          # bottom-right
          [(1, 0), (1, 0.22), (0.9, 0.08), (0.8, 0.1), (0.84, 0)]]                         # top-right sliver
    shard = G.pil_mask(lambda d, k: [d.polygon([((x0 + px * (x1 - x0)) * k, (y0 + py * (y1 - y0)) * k) for px, py in poly],
                                               fill=255) for poly in sh], shape) > 0.5
    alpha = np.where(gone & ~shard, 0, alpha)
    rgb = np.where((gone & shard)[..., None], G.lerp(rgb, np.array([236, 242, 248]), 0.3), rgb)
    # Iron round the panes (its edges catch a little light), a thin line of old putty on the glass.
    iron = _iron(rng, shape)
    rim = np.clip(G.blur(cov, 1.0) - cov, 0, 1) * 2.5
    iron = G.add_lum(iron, 10 * np.clip(rim, 0, 1))
    putty = np.clip(cov - G.smoothstep(0.0, 1.0, G.blur(cov, 1.2) * 2 - 1), 0, 1) * has
    rgb = G.lerp(rgb, np.array([62, 60, 56]), (putty * 0.8)[..., None])
    alpha = np.maximum(alpha, putty * 230)
    out_rgb = G.lerp(iron, rgb, cov[..., None])
    out_a = 255 * (1 - cov) + np.clip(alpha, 0, 255) * cov
    return np.concatenate([out_rgb, out_a[..., None]], axis=2)


def _arch_brick(rng, shape, cx, cy, R, ring_w, key_w):
    """Opaque brick above a circular arch of radius R centred at (cx, cy): the plain mill brick
    (lined up with the brick blocks around it), a rowlock ring of voussoirs over the curve and a
    granite keystone. Returns (rgb, mask of the brick)."""
    h, w = shape
    Xs, Ys = G.grid(shape)
    wall = np.tile(mill_brick_base(), (int(np.ceil(h / S)), int(np.ceil(w / S)), 1))[:h, :w]
    r = np.hypot(Xs - cx, cy - Ys)
    phi = np.arctan2(Xs - cx, cy - Ys) * R                              # arc length along the curve
    seg = 11.0
    vid = (np.floor(phi / seg) + 500).astype(int)
    dj = np.minimum(np.abs(G.wrapd(phi, seg)), np.minimum(r - R, R + ring_w - r))
    vou, _ = _brick_faces(rng, BRICK, vid, np.zeros(shape, bool), dj, 0.9, MORTAR, chip=0.6)
    vou = _to_mean(vou, BRICK_MEAN)
    kd = key_w / 2 + (r - R) * 0.2 - np.abs(phi)
    keym = G.smoothstep(-0.5, 0.5, kd) * G.smoothstep(R + ring_w + 2, R + ring_w + 1, r)
    key = G.add_lum(_granite(G.rng_for(39033), shape, weather=False), _relief(G.blur(keym, 0.8) * 3, 10))
    img = G.lerp(wall, vou, G.smoothstep(R + ring_w + 0.5, R + ring_w - 0.5, r)[..., None])
    img = G.lerp(img, key, keym[..., None])
    img = G.add_lum(img, -14 * np.exp(-(r - R) / 3.0) * (r > R))      # soot under the arch's edge
    return img, G.smoothstep(R - 0.5, R + 0.5, r)


def _close_under_curve(img, inside, frame):
    """Make everything outside `inside` (0..1) opaque frame iron (the frame follows a curve)."""
    img[..., :3] = G.lerp(frame, img[..., :3], inside[..., None])
    img[..., 3] = 255 * (1 - inside) + img[..., 3] * inside


def _lay_brick(img, brick, bm):
    img[..., :3] = G.lerp(img[..., :3], brick, bm[..., None])
    img[..., 3] = img[..., 3] * (1 - bm) + 255 * bm


def _mill_sheet(arch: bool):
    """The whole 3 x 5 block mill window (384 x 640 px, RGBA): a heavy frame round the outside,
    12-over-12 panes (4 across, 3 rows in each sash) with a thicker meeting rail at mid-height.
    arch=True: the top is a shallow segmental arch, brick filling the top corners."""
    rng = G.rng_for(39030)
    shape = (MILL_H, MILL_W)
    F, MR, MU = 10.0, 12.0, 5.0
    x0, x1, y0, y1 = F, MILL_W - F, F, MILL_H - F
    mid = MILL_H / 2
    pw = (x1 - x0 - 3 * MU) / 4
    ph = (mid - MR / 2 - y0 - 2 * MU) / 3
    panes = []
    for r_ in range(6):
        top = y0 + r_ * (ph + MU) if r_ < 3 else mid + MR / 2 + (r_ - 3) * (ph + MU)
        for c in range(4):
            left = x0 + c * (pw + MU)
            panes.append((left, top, left + pw, top + ph))
    img = _paint_glass(rng, shape, panes, sooty_from=mid)
    if arch:
        Xs, Ys = G.grid(shape)
        spring, crown = 104.0, 22.0                                    # px from the top of the window
        half, sag = MILL_W / 2, spring - crown
        R = (half * half + sag * sag) / (2 * sag)
        cx, cy = half, crown + R
        r = np.hypot(Xs - cx, cy - Ys)
        above = Ys < spring
        _close_under_curve(img, np.where(above, G.smoothstep(R - F + 0.5, R - F - 0.5, r), 1.0), _iron(G.rng_for(39031), shape))
        brick, bm = _arch_brick(G.rng_for(39032), shape, cx, cy, R, 16.0, 16.0)
        _lay_brick(img, brick, bm * above)
    return img


def _tower_sheet():
    """The whole tower window (128 x 384 px, RGBA): a narrow lancet with a round head and brick in
    its top corners, a granite sill at the bottom. Muntins fall on every half block, so the middle
    piece can be stacked as often as the tower needs."""
    rng = G.rng_for(39036)
    shape = (TOWER_H, TOWER_W)
    Xs, Ys = G.grid(shape)
    F, MU = 8.0, 5.0
    sill_top = TOWER_H - 14.0
    frame_bot = sill_top - F
    pw = (TOWER_W - 2 * F - MU) / 2
    lines = [64.0, 128.0, 192.0, 256.0, 320.0]                          # horizontal muntin centres
    tops = [F] + [y + MU / 2 for y in lines]
    bots = [y - MU / 2 for y in lines] + [frame_bot]
    panes = [(F + c * (pw + MU), t, F + c * (pw + MU) + pw, b) for t, b in zip(tops, bots) for c in range(2)]
    img = _paint_glass(rng, shape, panes, sooty_from=256.0)
    R, cx, cy = TOWER_W / 2, TOWER_W / 2, TOWER_W / 2                  # round head on the spring line
    r = np.hypot(Xs - cx, cy - Ys)
    above = Ys < cy
    _close_under_curve(img, np.where(above, G.smoothstep(R - F + 0.5, R - F - 0.5, r), 1.0), _iron(G.rng_for(39037), shape))
    brick, bm = _arch_brick(G.rng_for(39038), shape, cx, cy, R, 12.0, 12.0)
    _lay_brick(img, brick, bm * above)
    # granite sill with a lit top edge
    sill = G.box(shape, 0, sill_top, TOWER_W, TOWER_H + 1, soft=0.5)
    stone = _granite(G.rng_for(39039), shape)
    stone = G.add_lum(stone, 22 * G.smoothstep(sill_top + 2.5, sill_top, Ys) - 10 * G.smoothstep(sill_top + 4, TOWER_H, Ys))
    _lay_brick(img, stone, sill)
    return img


def _rows_bottom_up(sheet, rows):
    """Tile row r (0 = bottom) of a sheet that is `rows` blocks high."""
    return [sheet[(rows - 1 - r) * S:(rows - r) * S] for r in range(rows)]


def tex_l39_mill_window() -> None:
    """The 15 pieces l39_mill_window_<col>_<row> (col 0 = left seen from the town, row 0 = bottom)
    and the 3 arched top pieces l39_mill_window_<col>_arch."""
    plain = _rows_bottom_up(_mill_sheet(False), 5)
    arched = _mill_sheet(True)[:S]
    for c in range(3):
        for r in range(5):
            G.save_png(plain[r][:, c * S:(c + 1) * S], _blk(f"l39_mill_window_{c}_{r}"))
        G.save_png(arched[:, c * S:(c + 1) * S], _blk(f"l39_mill_window_{c}_arch"))


def tex_l39_tower_window() -> None:
    for r, tile in enumerate(_rows_bottom_up(_tower_sheet(), 3)):
        G.save_png(tile, _blk(f"l39_tower_window_{r}"))


def tex_l39_boarded_window() -> None:
    """Ground-storey window boarded over with grey planks: dark gaps, rusty nails, a few slits of
    the bright gallery light leaking through."""
    rng = G.rng_for(39034)
    edges = [0, 23, 49, 74, 100, 126, 128]
    img = _fill((14, 14, 16))
    leak = np.zeros((S, S))
    for j in range(len(edges) - 2):
        y0, y1 = edges[j] + rng.uniform(0.3, 1.6), edges[j + 1] - rng.uniform(0.6, 1.8)
        tilt = rng.normal(0, 0.012)
        yy0, yy1 = y0 + tilt * (X - 64), y1 + tilt * (X - 64)
        m = G.smoothstep(yy0 - 0.5, yy0 + 0.5, Y) * G.smoothstep(yy1 + 0.5, yy1 - 0.5, Y)
        wood = G.add_lum(_fill(np.array((118, 114, 106)) * rng.uniform(0.85, 1.08)),
                         G.fnoise(rng, beta=1.4, aniso=(14, 1), kmin=2) * 10 + G.fnoise(rng, beta=0.6, aniso=(16, 1), kmin=10) * 4)
        wood = G.add_lum(wood, -16 * G.smoothstep(4, 0, Y - yy0) + 6 * G.smoothstep(yy1 - 3, yy1, Y))
        knot = G.blob((S, S), rng.uniform(10, 118), (y0 + y1) / 2, 2.5, 1.6)
        wood = G.add_lum(wood, -40 * knot)
        for nx in (6.0, 122.0):
            for ny in (y0 + 5, y1 - 5):
                n = np.exp(-((X - nx) ** 2 + (Y - ny) ** 2) / 1.4)
                wood = G.add_lum(wood, -70 * n)
                run = np.exp(-((X - nx) / 1.0) ** 2) * (Y > ny) * np.exp(-(Y - ny) / 7)
                wood = G.over(wood, np.array([110, 72, 48]), np.clip(run * 0.6, 0, 0.6))
        img = G.over(img, wood, m)
        if j % 2 == 0:
            leak = np.maximum(leak, G.smoothstep(1.0, 0.0, np.abs(Y - yy1 - 0.9)) * G.smoothstep(0.6, 1.4, G.fnoise(rng, beta=1.0, aniso=(8, 1))))
    img = G.over(img, np.array([226, 230, 238]), leak * 0.7)
    img = G._weather(img, rng, dirt=0.2, scratches=4)
    _save(img, "l39_boarded_window")


def tex_l39_bricked_window() -> None:
    """Window bricked up with later, mismatched brick: browner and a little lighter than the
    mill brick, thinner courses, paler mortar smeared over the faces by a hurried bricklayer."""
    rng = G.rng_for(39035)
    row, bid, dj, v, header = _brick_layout(X, Y, h=S / 16, L=32.0, header_every=1000)
    img, m = _brick_faces(rng, (86, 56, 42), bid, header, dj, 0.9, (78, 74, 68), clinker=0.04, chip=0.5, hue=1.2)
    smear = G.smoothstep(1.4, 2.2, G.fnoise(rng, beta=1.6, aniso=(3, 1), kmin=3))
    img = G.over(img, np.array([88, 84, 78]), smear * 0.5)
    img = G.over(img, np.array([22, 18, 18]), G.smoothstep(0.4, 2.0, G.fnoise(rng, beta=1.2, aniso=(1, 10), kmin=2)) * 0.3)
    _save(_to_mean(img, (70, 50, 40)), "l39_bricked_window")


# =============================================================================
# The gallery behind the glass
# =============================================================================

def tex_l39_gallery() -> None:
    """Seamless glossy white panels with a hairline joint, the pure white light panel and the
    polished pale floor with soft reflections of the lights."""
    rng = G.rng_for(39040)
    joint = np.maximum(G.smoothstep(1.2, 0.2, np.abs(G.wrapd(X, S))), G.smoothstep(1.2, 0.2, np.abs(G.wrapd(Y, S))))
    lit = np.clip(G.smoothstep(2.6, 1.4, X) + G.smoothstep(2.6, 1.4, Y), 0, 1)   # bevel catching the light
    panel = G.add_lum(_fill((233, 237, 241)), G.fnoise(rng, beta=3.4) * 1.2)   # ~#E8ECF0 (agreed with L39Atmos)
    panel = G.add_lum(panel, 5 * np.exp(-((X - Y * 0.35 - 40) / 26) ** 2) - 2 * (Y / S))   # broad gloss
    panel = G.add_lum(panel, -34 * joint + 8 * lit * (1 - joint))
    _save(panel, "l39_gallery_panel")

    light = G.add_lum(_fill((252, 253, 255)), G.fnoise(rng, beta=2.0, kmin=8) * 0.8)
    rim = np.minimum(np.minimum(X, S - X), np.minimum(Y, S - Y))
    light = G.add_lum(light, -22 * G.smoothstep(3.0, 1.0, rim) - 8 * G.smoothstep(1.0, 0.0, rim))
    _save(light, "l39_gallery_light")

    floor = G.add_lum(_fill((204, 206, 210)), G.fnoise(rng, beta=3.0) * 2.4 + G.blur(rng.normal(0, 1.6, (S, S)), 0.5))
    flecks = (rng.random((S, S)) < 0.03) * rng.choice([-1.0, 1.0], (S, S))
    floor = G.add_lum(floor, G.blur(flecks, 0.4) * 10)
    refl = G.blob((S, S), 40, 30, 9, 30) + 0.7 * G.blob((S, S), 100, 96, 8, 26)   # the light panels mirrored
    floor = G.add_lum(floor, refl * 16 + G.blur(G.fnoise(rng, beta=1.6, aniso=(1, 4), kmin=3), 1.0) * 1.2, (0.98, 0.99, 1.0))
    floor = G.add_lum(floor, -30 * joint + 6 * lit * (1 - joint))
    _save(floor, "l39_gallery_floor")


# =============================================================================
# The marble tunnels
# =============================================================================

MARBLE_WHITE = (238, 239, 241)
CLOUD = np.array([196, 206, 220], float)      # soft milky blue-grey clouding
DRIFT = np.array([170, 184, 206], float)      # the bluer drifts inside the clouds
VEIN = np.array([98, 112, 140], float)        # fine veins
VEIN_FAINT = np.array([150, 164, 188], float)


def _sample(a, xs, ys):
    """Bilinear lookup of a seamless field at fractional pixel positions (wrapping round)."""
    x0 = np.floor(xs - 0.5)
    y0 = np.floor(ys - 0.5)
    fx, fy = xs - 0.5 - x0, ys - 0.5 - y0
    h, w = a.shape
    x0i, y0i = x0.astype(int) % w, y0.astype(int) % h
    x1i, y1i = (x0i + 1) % w, (y0i + 1) % h
    top = a[y0i, x0i] * (1 - fx) + a[y0i, x1i] * fx
    bot = a[y1i, x0i] * (1 - fx) + a[y1i, x1i] * fx
    return top * (1 - fy) + bot * fy


def _isoline_veins(rng, width: float, kmax: float) -> np.ndarray:
    """Long, continuous veins (0..1, seamless): thin lines along the zero contour of a smooth noise
    field, gently warped so they wander. Real marble veins run on across a whole slab like this."""
    field = G.fnoise(rng, beta=3.0, kmin=1, kmax=kmax)
    field = field / (field.std() + 1e-9)
    warp = G.fnoise(rng, beta=2.6, kmin=2, kmax=6) * 0.35
    d = np.abs(field + warp)
    return np.exp(-(d / width) ** 2)


def _marble(rng, veins=1.0, cloud=1.0):
    """Polished white marble panels (like tunnel.png): a calm milky surface with a soft blue-grey
    clouding, one long faint vein wandering across the panel and a fainter one crossing it, a gentle
    sheen and a fine joint between the panels. Seamless."""
    img = _fill(MARBLE_WHITE)
    big = G.fnoise(rng, beta=3.4, aniso=(1.4, 1.0))
    img = G.over(img, CLOUD, G.smoothstep(-0.2, 1.8, big * cloud) * 0.3)
    drift = G.smoothstep(0.3, 1.6, G.blur(G.fnoise(rng, beta=2.6, aniso=(1.8, 1.0), kmin=2), 1.5))
    img = G.over(img, DRIFT, drift * 0.16 * cloud)
    main = _isoline_veins(rng, 0.05 * veins, 3)
    img = G.over(img, VEIN_FAINT, G.blur(main, 2.5) * 0.2)                # soft halo
    img = G.over(img, VEIN, main * 0.3 * min(veins, 1.6))
    minor = _isoline_veins(rng, 0.03 * veins, 4)
    img = G.over(img, VEIN_FAINT, minor * 0.22 * min(veins, 1.6))
    X, Y = G.grid()
    img = G.add_lum(img, 6 * G.smoothstep(0.9, 0.0, np.abs((X + Y) / (2 * S) - 0.35) * 3))   # polish sheen
    joint = ((X < 0.6) | (Y < 0.6)).astype(float)
    img = G.over(img, np.array([150, 156, 166]), joint * 0.45)
    return img


def marble_base():
    return _marble(G.rng_for(39050))


def marble_marks(i, rng, img):
    if i == 1:                                             # a greyer, more clouded panel
        m, _, _ = _lmask(rng, (60, 50), rough=0.4, lo=0.1, hi=0.7)
        out = G.over(img, DRIFT, m * 0.22)
        out = G.over(out, VEIN, _isoline_veins(rng, 0.04, 3) * m * 0.3)
        return out * (img.mean() / out.mean())        # as bright overall as the plain marble
    if i == 2:                                             # a hairline crack
        return G.mark_crack(img, rng, 48, (45, 75), 2)
    m, cx, cy = _lmask(rng, (18, 14), rough=0.9, y_range=(0.0, 0.6))   # a faint mineral bloom with a drip
    crust = G.smoothstep(-0.8, 1.0, G.blur(G.fnoise(rng, beta=1.4, kmin=8), 0.8) * 1.6)
    img = G.over(img, np.array([236, 232, 214]), m * (0.25 + 0.25 * crust))
    img = G.add_lum(img, _relief(G.blur(m * crust, 1.0) * 2.0, 6))
    rim = np.clip(G.smoothstep(0.05, 0.2, m) - G.smoothstep(0.2, 0.4, m), 0, 1)
    img = G.over(img, np.array([200, 188, 150]), rim * 0.25)
    return G.mark_drip(img, rng, (204, 194, 160), 0.22, (30, 50))


def tex_l39_marble_stained() -> None:
    """Marble of the stagnant (Hydrolitis) stretches: yellowed, a greenish bloom creeping up."""
    rng = G.rng_for(39051)
    img = _marble(rng, cloud=0.8)
    img = G.lerp(img, img * np.array([1.0, 0.96, 0.84]), (0.4 + 0.3 * G.smoothstep(-1, 1, G.fnoise(rng, beta=2.4)))[..., None])
    fingers = G.blur(G.fnoise(rng, beta=2.2, aniso=(1, 3), kmin=2), 1.2)
    rise = (Y / S) * 1.8 + 0.45 * fingers / fingers.std() - 1.05
    bloom = G.smoothstep(0.0, 0.8, rise) * (0.65 + 0.35 * G.smoothstep(-1, 1, G.blur(G.fnoise(rng, beta=1.4, kmin=6), 1.0)))
    img = G.over(img, np.array([128, 138, 98]), np.clip(bloom * 0.5, 0, 0.6))
    img = G.over(img, np.array([88, 98, 70]), G.smoothstep(0.9, 1.6, rise) * 0.35)   # the dark tide line
    img = G.add_lum(img, G.blur(G.fnoise(rng, beta=1.2, aniso=(1, 10), kmin=2), 0.8) * 3)
    _save(img, "l39_marble_stained")


def _tiles(rng, n, tile_col, grout_col, var=5.0, glaze=4.0, chips=0.03, grime=0.3, colour_of=None):
    """n x n small glazed tiles per block with grout, per-tile tone, a soft glaze highlight,
    chipped corners and dirt soaked into the joints. colour_of(row, col) may override a tile."""
    T = S / n
    row = np.floor(Y / T).astype(int)
    col = np.floor(X / T).astype(int)
    tid = row * n + col
    cols = np.array([[colour_of(r_, c_) if colour_of else tile_col for c_ in range(n)] for r_ in range(n)], float)
    base = cols[row, col] + (rng.normal(0, 1, n * n) * var)[tid][..., None]
    u, v = X - col * T, Y - row * T
    img = G.add_lum(base, rng.normal(0, 1.2, (S, S)) + G.fnoise(rng, beta=2.0, kmin=4) * 1.5)
    img = G.add_lum(img, glaze * (np.exp(-((u - T * 0.32) ** 2 + (v - T * 0.3) ** 2) / (T * T * 0.08)) - 0.3))
    d = np.minimum(np.minimum(u, T - u), np.minimum(v, T - v))
    cu = np.where((rng.random(n * n) < 0.5)[tid], u, T - u)
    cv = np.where((rng.random(n * n) < 0.5)[tid], v, T - v)
    chip = (rng.random(n * n) < chips)[tid] * G.smoothstep(4.5, 3.0, cu + cv + G.fnoise(rng, beta=1.0, kmin=10) * 0.8)
    g = np.maximum(G.smoothstep(1.4, 0.6, d), chip * 0.85)            # a chipped corner shows the grout bed
    grout = G.add_lum(_fill(grout_col), rng.normal(0, 4, (S, S)))
    grout = G.over(grout, np.array([96, 92, 78]), G.smoothstep(0.0, 1.6, G.fnoise(rng, beta=2.2)) * grime)
    img = G.over(img, grout, g)
    return G.add_lum(img, _relief(G.smoothstep(0.6, 3.0, d) * (1 - chip), 10))   # rounded glazed edges


RIM = (226, 214, 188)
RIM_GROUT = (170, 160, 140)
NAVY = (30, 44, 92)


def tex_l39_tiles() -> None:
    rng = G.rng_for(39060)
    rim = _tiles(rng, 8, RIM, RIM_GROUT, var=5.0, glaze=5.0, chips=0.04, grime=0.35)
    rim = G.add_lum(rim, G.fnoise(rng, beta=1.4, aniso=(1, 6), kmin=2) * 2)
    rim = G.over(rim, np.array([150, 146, 120]), G.smoothstep(1.0, 2.0, G.fnoise(rng, beta=2.4)) * 0.25)   # wet footprints of grime
    _save(rim, "l39_tile_rim")

    rng = G.rng_for(39061)
    top = _tiles(rng, 8, RIM, RIM_GROUT, var=5.0, glaze=5.0, chips=0.04, grime=0.35,
                 colour_of=lambda r_, c_: NAVY if r_ == 0 else RIM)
    top = G.over(top, np.array([150, 146, 120]), G.smoothstep(1.0, 2.0, G.fnoise(rng, beta=2.4)) * 0.2)
    _save(top, "l39_tile_edge_top")

    rng = G.rng_for(39062)
    face = _tiles(rng, 8, NAVY, (88, 96, 116), var=4.0, glaze=10.0, chips=0.03, grime=0.15)
    wet = G.smoothstep(S * 0.6, S, Y)                                       # darker where the water laps
    face = face * (1 - 0.18 * wet)[..., None]
    face = G.over(face, np.array([96, 112, 92]), G.smoothstep(S * 0.82, S, Y) * 0.25 * G.smoothstep(-0.5, 1.0, G.fnoise(rng, beta=1.4, kmin=4)))
    _save(face, "l39_tile_edge")

    rng = G.rng_for(39063)
    fl = _tiles(rng, 8, (208, 228, 226), (172, 192, 192), var=3.5, glaze=3.0, chips=0.02, grime=0.15)
    _save(G.over(fl, np.array([150, 176, 168]), G.smoothstep(0.8, 2.0, G.fnoise(rng, beta=2.6)) * 0.3), "l39_tile_floor")


def tex_l39_drain_lining() -> None:
    """Rusty riveted iron plates lining the drains: rust scale, pitting, wet runs, rivet rows."""
    rng = G.rng_for(39070)
    img = G._smooth_concrete(rng, (72, 62, 56), mottle=5.0, fine=1.6)
    rust = G.smoothstep(-0.6, 1.4, G.fnoise(rng, beta=2.2) + 0.5 * G.fnoise(rng, beta=1.2, kmin=6))
    img = G.over(img, np.array([126, 70, 40]), rust * 0.7)
    img = G.over(img, np.array([150, 92, 52]), G.smoothstep(1.2, 2.0, G.fnoise(rng, beta=1.0, kmin=6)) * 0.6)
    scale = G.blur(G.fnoise(rng, beta=1.4, kmin=6), 0.6)
    img = G.add_lum(img, _relief(scale * 1.6, 10))
    pits = (rng.random((S, S)) < 0.03) * rng.uniform(0.5, 1, (S, S))
    img = G.add_lum(img, -G.blur(pits, 0.5) * 50)
    seam = np.minimum(np.abs(G.wrapd(Y, S)), np.abs(G.wrapd(X, S)))
    img = G.add_lum(img, -38 * G.smoothstep(1.4, 0.3, seam) + 10 * G.smoothstep(2.6, 1.6, seam) * (seam > 1.4))
    rv = np.zeros((S, S))
    for k in range(8):
        for cx, cy in ((k * 16 + 8, 5.5), (5.5, k * 16 + 8), (k * 16 + 8, S - 5.5), (S - 5.5, k * 16 + 8)):
            rv = np.maximum(rv, np.sqrt(np.clip(1 - ((X - cx) ** 2 + (Y - cy) ** 2) / 5.5, 0, 1)))
    img = G.over(img, np.array([98, 70, 54]), G.smoothstep(0, 0.2, rv) * 0.7)
    img = G.add_lum(img, _relief(rv * 2.2, 16))
    runs = G.smoothstep(0.6, 1.8, G.fnoise(rng, beta=1.0, aniso=(1, 12), kmin=3))
    img = G.over(img, np.array([44, 34, 30]), runs * 0.4)
    _save(_wet_sheen(img, rng, 1.2), "l39_drain_lining")


def tex_l39_stagnant_film() -> None:
    """Greenish-yellow scum floating on stagnant water: lacy islands with holes, floating specks
    and tiny bubbles. Translucent (soft alpha)."""
    rng = G.rng_for(39071)
    n = G.fnoise(rng, beta=2.6) + 0.35 * G.blur(G.fnoise(rng, beta=1.6, kmin=6), 0.7)
    lace = G.smoothstep(-0.4, 0.5, n) * (1 - 0.7 * G.smoothstep(0.93, 0.99, 1 - np.abs(G.fnoise(rng, beta=2.0, kmin=4))))
    rgb = G.lerp(np.array([112, 122, 66]), np.array([152, 150, 88]), G.smoothstep(0.2, 1.4, n)[..., None])
    rgb = G.add_lum(rgb, G.blur(G.fnoise(rng, beta=1.2, kmin=8), 0.6) * 5)
    specks = G.blur((rng.random((S, S)) < 0.004) * 1.0, 0.6) * (n > 0)   # bits of debris caught in the scum
    rgb = G.over(rgb, np.array([46, 44, 30]), np.clip(specks * 3, 0, 1))
    bub = np.zeros((S, S))
    for _ in range(16):
        cx, cy, rr = rng.uniform(0, S), rng.uniform(0, S), rng.uniform(1.0, 2.4)
        d = np.hypot(G.wrapd(X - cx, S), G.wrapd(Y - cy, S))
        bub = np.maximum(bub, np.clip(1 - np.abs(d - rr) / 0.6, 0, 1))
    rgb = G.add_lum(rgb, bub * 60)
    alpha = np.clip(lace * 0.78 + np.clip(specks * 3, 0, 1) * 0.5 + bub * 0.5, 0, 0.9)
    G.save_png(np.concatenate([rgb, (alpha * 255)[..., None]], axis=2), _blk("l39_stagnant_film"))


def tex_l39_hatch() -> None:
    """Cast-iron access hatch: raised diamond checker pattern in a round raised rim, a heavy
    square frame edge, two lifting slots, rust in the recesses and polished worn tops."""
    rng = G.rng_for(39072)
    c = 64
    r = np.hypot(X - c, Y - c)
    u, v = (X - c + Y - c) / np.sqrt(2), (X - c - (Y - c)) / np.sqrt(2)
    p = 11.0
    dia = np.maximum(np.abs(G.wrapd(u, p)), np.abs(G.wrapd(v, p)))
    h = G.smoothstep(4.5, 3.2, dia) * 1.6 * (r < 50)                    # diamond studs
    h += G.smoothstep(49.5, 51, r) * G.smoothstep(58, 56.5, r) * 2.2     # raised round rim
    fr = np.minimum(np.minimum(X, S - X), np.minimum(Y, S - Y))
    h += G.smoothstep(7.5, 6.5, fr) * 2.6                               # square frame edge
    slots = np.zeros((S, S))
    for sx in (40, 88):
        slots = np.maximum(slots, G.box((S, S), sx - 6, 62, sx + 6, 66, soft=0.6))
    h = h - slots * 3
    iron = G.add_lum(_fill((58, 56, 56)), G.fnoise(rng, beta=2.2) * 5 + rng.normal(0, 3.5, (S, S)))
    img = G.add_lum(iron, _relief(h, 22))
    low = G.smoothstep(1.0, 0.0, h) * (1 - slots)
    rust = G.smoothstep(-0.8, 1.2, G.fnoise(rng, beta=1.8) + 0.6 * G.fnoise(rng, beta=1.0, kmin=8))
    img = G.over(img, np.array([118, 70, 42]), np.clip(rust * (0.25 + 0.6 * low), 0, 0.85))
    worn = G.smoothstep(1.2, 2.2, h) * G.smoothstep(-0.5, 1.0, G.fnoise(rng, beta=2.0))
    img = G.add_lum(img, worn * 32, (0.95, 0.97, 1.0))
    img = G.over(img, np.array([10, 10, 12]), slots)
    _save(_wet_sheen(img, rng, 0.8), "l39_hatch")


def tex_l39_rungs() -> None:
    """Galvanised iron gone rusty: the rungs' metal (the model maps small pieces of it)."""
    rng = G.rng_for(39073)
    img = G._galvanised(rng, (96, 98, 102))
    rust = G.smoothstep(-0.4, 1.4, G.fnoise(rng, beta=2.0) + 0.5 * G.fnoise(rng, beta=1.0, kmin=8))
    img = G.over(img, np.array([122, 72, 42]), rust * 0.75)
    img = G.add_lum(img, _relief(G.blur(G.fnoise(rng, beta=1.4, kmin=8), 0.6), 8))
    _save(img, "l39_rungs")
    # inventory icon: two U-shaped staple rungs
    ss = 8
    canvas = Image.new("RGBA", (32 * ss, 32 * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(canvas)
    for y0 in (6, 19):
        pts = [(7 * ss, (y0 + 7) * ss), (9 * ss, y0 * ss), (23 * ss, y0 * ss), (25 * ss, (y0 + 7) * ss)]
        d.line(pts, fill=(40, 30, 26, 255), width=int(4.2 * ss), joint="curve")
        d.line(pts, fill=(132, 86, 56, 255), width=int(2.6 * ss), joint="curve")
        d.line([(10 * ss, (y0 - 0.6) * ss), (22 * ss, (y0 - 0.6) * ss)], fill=(176, 150, 126, 255), width=int(0.9 * ss))
    G.save_png(np.asarray(canvas.resize((32, 32), Image.LANCZOS), float), _item("l39_rungs"))


def tex_l39_orb_light_icon() -> None:
    """Inventory icon of the invisible orb light: a small soft glowing white-blue orb."""
    Xs, Ys = G.grid((32, 32))
    r = np.hypot(Xs - 16, Ys - 16)
    core = G.smoothstep(7.5, 5.5, r)
    glow = np.exp(-(r / 8.5) ** 2)
    rgb = G.lerp(np.array([170, 200, 240]), np.array([255, 255, 255]), core[..., None])
    rgb = G.add_lum(rgb, 30 * np.exp(-((Xs - 13.5) ** 2 + (Ys - 13) ** 2) / 4))
    alpha = np.clip(core + glow * 0.75, 0, 1) * (r < 15)
    G.save_png(np.concatenate([rgb, (alpha * 255)[..., None]], axis=2), _item("l39_orb_light"))


def tex_l39_panel_door() -> None:
    """Plain white panelled door from the tunnels (2 blocks tall): two small upper panels, two
    tall panels above the lock rail and two below, yellowed paint, brass knob and keyhole."""
    rng = G.rng_for(39074)
    shp = (2 * S, S)
    Xs, Ys = G.grid(shp)
    paint = G.add_lum(_fill((226, 222, 204), shp), G.fnoise(rng, shp, beta=2.8) * 3)
    brush_v = G.fnoise(rng, shp, beta=1.2, aniso=(1, 12), kmin=3) * 2.5
    brush_h = G.fnoise(rng, shp, beta=1.2, aniso=(12, 1), kmin=3) * 2.5
    panels = [(22, 14, 60, 42), (68, 14, 106, 42), (22, 54, 60, 116), (68, 54, 106, 116),
              (22, 148, 60, 236), (68, 148, 106, 236)]
    pm = np.zeros(shp)
    h = np.zeros(shp)
    for x0, y0, x1, y1 in panels:
        outer = G.box(shp, x0, y0, x1, y1)
        bevel = G.box(shp, x0 + 5, y0 + 5, x1 - 5, y1 - 5, soft=2.5)
        pm = np.maximum(pm, outer)
        h += outer * (-1.4 + 1.8 * bevel)                       # sunk moulding, raised field
    stile = (Xs < 22) | (Xs > 106)
    img = G.add_lum(paint, np.where(stile | (pm > 0.5), brush_v, brush_h))
    img = G.add_lum(img, _relief(G.blur(h, 0.7), 26))
    img = G.lerp(img, img * np.array([0.98, 0.96, 0.88]), G.smoothstep(60, 256, Ys)[..., None] * 0.6)   # yellowed lower down
    chips = G.smoothstep(1.9, 2.3, G.blur(G.fnoise(rng, shp, beta=1.3, kmin=4), 0.7))
    img = G.over(img, np.array([150, 140, 120]), chips * 0.8)
    grime = np.exp(-((Xs - 112) ** 2 / 300 + (Ys - 138) ** 2 / 500))
    img = G.over(img, np.array([120, 112, 96]), grime * 0.35)
    kick = G.smoothstep(214, 256, Ys) * (0.75 + 0.25 * G.blur(G.fnoise(rng, shp, beta=2.0), 2.0))
    img = G.over(img, np.array([126, 118, 102]), kick * 0.35)
    scuff = G.smoothstep(1.5, 2.3, G.blur(G.fnoise(rng, shp, beta=1.2, aniso=(6, 1), kmin=4), 0.7)) * G.smoothstep(200, 240, Ys)
    img = G.over(img, np.array([96, 90, 82]), scuff * 0.5)                # shoe scuffs low on the door
    rose = np.hypot(Xs - 112, Ys - 138)
    knob = G.smoothstep(6.0, 5.0, rose)
    plate = G.smoothstep(8.5, 7.5, rose)
    brass = G.add_lum(_fill((168, 136, 70), shp), -30 * (rose / 6) + 30 * np.exp(-((Xs - 110) ** 2 + (Ys - 135.5) ** 2) / 5))
    img = G.lerp(img, G.add_lum(brass, -24 * np.ones(shp)), plate[..., None])
    img = G.lerp(img, brass, knob[..., None])
    key = G.smoothstep(1.6, 0.8, np.hypot(Xs - 112, Ys - 152)) + G.box(shp, 111.3, 152, 112.7, 157)
    img = G.add_lum(img, -150 * np.clip(key, 0, 1))
    img = G.add_lum(img, -26 * (G.smoothstep(1.5, 0, Xs) + G.smoothstep(S - 1.5, S, Xs)))   # door edges
    _save(img[:S], "l39_panel_door_top")
    _save(img[S:], "l39_panel_door_bottom")
    icon = Image.fromarray(np.clip(np.round(img), 0, 255).astype(np.uint8), "RGB").resize((14, 28), Image.LANCZOS)
    out = np.zeros((32, 32, 4))
    out[2:30, 9:23, :3] = np.asarray(icon, float)
    out[2:30, 9:23, 3] = 255
    out[2:30, 9, :3] *= 0.7
    out[2:30, 22, :3] *= 0.7
    G.save_png(out, _item("l39_panel_door"))


def tex_l39_surfaces() -> None:
    """Plain (variant 0) textures of the SurfaceBlocks; gen_assets.tex_surface_variants makes the rest."""
    _save(yard_grass_base(), "l39_yard_grass")
    _save(yard_dirt_base(), "l39_yard_dirt")
    _save(path_dirt_base(), "l39_path_dirt")
    _save(path_gravel_base(), "l39_path_gravel")
    _save(clapboard_base(), "l39_clapboard")
    _save(mill_brick_base(), "l39_mill_brick")
    _save(marble_base(), "l39_marble")


# =============================================================================
# Models, blockstates, item models
# =============================================================================

def _json(rel: str, obj, compact: bool = False) -> None:
    path = ASSETS / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(obj, separators=(",", ":")) if compact else json.dumps(obj, indent=2)
    path.write_text(text + "\n", newline="\n")


def _ref(name: str) -> str:
    return f"backrooms:block/{name}"


def _model(name: str, obj, compact: bool = False) -> None:
    _json(f"models/block/{name}.json", obj, compact)


def _state(name: str, variants, compact: bool = False) -> None:
    _json(f"blockstates/{name}.json", {"variants": variants}, compact)


def _plain_state(name: str) -> None:
    _state(name, {"": {"model": _ref(name)}})


def _item_block(name: str, model: str | None = None) -> None:
    _json(f"models/item/{name}.json", {"parent": _ref(model or name)})


def _item_flat(name: str, texture: str) -> None:
    _json(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": texture}})


def _cube_all(name: str, tex: str | None = None) -> None:
    _model(name, {"parent": "minecraft:block/cube_all", "textures": {"all": _ref(tex or name)}})


FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


def _facing_state(name: str) -> None:
    _state(name, {f"facing={f}": ({"model": _ref(name), "y": y} if y else {"model": _ref(name)}) for f, y in FACINGS})


def _el(frm, to, tex, faces=("north", "south", "east", "west", "up", "down")):
    """A model element; uv left out so Minecraft maps the texture by the element's position."""
    return {"from": list(frm), "to": list(to), "faces": {f: {"texture": tex} for f in faces}}


def tex_l39_world_models() -> None:
    """Block models, blockstates and item models of every block this module textures."""
    # SurfaceBlocks: only the plain model (variant_models writes the blockstate and _vN models).
    _model("l39_yard_grass", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": _ref("l39_yard_grass"), "side": _ref("l39_yard_grass_side"), "bottom": _ref("l39_soil")}})
    for name in ("l39_yard_dirt", "l39_path_dirt", "l39_path_gravel", "l39_clapboard", "l39_mill_brick", "l39_marble"):
        _cube_all(name)
    for name in ("l39_yard_grass", "l39_yard_dirt", "l39_path_dirt", "l39_path_gravel", "l39_clapboard",
                 "l39_mill_brick", "l39_marble"):
        _item_block(name)

    # Plain full blocks.
    for name in ("l39_path_mud", "l39_soil", "l39_rock", "l39_clapboard_faded", "l39_clapboard_weathered",
                 "l39_mill_brick_sooty", "l39_mill_brick_black", "l39_mass", "l39_roof_tar", "l39_granite",
                 "l39_boarded_window", "l39_bricked_window", "l39_gallery_panel", "l39_gallery_light",
                 "l39_gallery_floor", "l39_marble_stained", "l39_tile_rim", "l39_tile_floor", "l39_drain_lining",
                 "l39_rubble", "l39_clay", "l39_rock_layered"):
        _cube_all(name)
        _plain_state(name)
        _item_block(name)
    _model("l39_path_grass", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": _ref("l39_path_grass"), "side": _ref("l39_yard_grass_side"), "bottom": _ref("l39_soil")}})
    _plain_state("l39_path_grass")
    _item_block("l39_path_grass")

    # Granite stairs (vanilla stair blockstate, generated like gen_assets' stairs).
    g = _ref("l39_granite")
    for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
        _model(f"l39_granite_stairs{suffix}", {"parent": f"minecraft:block/{parent}",
                                               "textures": {"bottom": g, "top": g, "side": g}})
    stairs = json.loads((ASSETS / "blockstates" / "concrete_stairs.json").read_text())   # vanilla stair table
    _state("l39_granite_stairs", {k: {**v, "model": v["model"].replace("block/concrete_stairs", "block/l39_granite_stairs")}
                                  for k, v in stairs["variants"].items()})
    _item_block("l39_granite_stairs")

    _slab("l39_grass_slab", "l39_yard_grass", top="l39_yard_grass", side="l39_grass_slab_side", bottom="l39_soil")
    _slab("l39_dirt_slab", "l39_yard_dirt", top="l39_yard_dirt", side="l39_yard_dirt", bottom="l39_yard_dirt")
    _slab("l39_mud_slab", "l39_path_mud", top="l39_path_mud", side="l39_path_mud", bottom="l39_path_mud")

    _window_models()
    _vault_models()
    _dome_models()
    _junction_models()
    _hole_models()

    # Tile edge: north = navy tile face toward the water, up = rim with the dark-blue line on its north edge.
    rim = _ref("l39_tile_rim")
    _model("l39_tile_edge", {"parent": "minecraft:block/cube", "textures": {
        "particle": rim, "north": _ref("l39_tile_edge"), "up": _ref("l39_tile_edge_top"),
        "south": rim, "east": rim, "west": rim, "down": rim}})
    _facing_state("l39_tile_edge")
    _item_block("l39_tile_edge")

    # Panel door: the house_door structure (with the `locked` property), cutout, flat item icon.
    for half in ("bottom", "top"):
        for hinge in ("left", "right"):
            for opn in ("", "_open"):
                m = f"l39_panel_door_{half}_{hinge}{opn}"
                _model(m, {"parent": f"minecraft:block/door_{half}_{hinge}{opn}", "render_type": "minecraft:cutout",
                           "textures": {"bottom": _ref("l39_panel_door_bottom"), "top": _ref("l39_panel_door_top")}})
    house = json.loads((ASSETS / "blockstates" / "house_door.json").read_text())
    _state("l39_panel_door", {k: {**v, "model": v["model"].replace("block/house_door_", "block/l39_panel_door_")}
                              for k, v in house["variants"].items()})
    _item_flat("l39_panel_door", "backrooms:item/l39_panel_door")

    # Hatch: vanilla orientable (iron) trapdoor.
    for part in ("bottom", "top", "open"):
        _model(f"l39_hatch_{part}", {"parent": f"minecraft:block/template_orientable_trapdoor_{part}",
                                      "render_type": "minecraft:cutout", "textures": {"texture": _ref("l39_hatch")}})
    _state("l39_hatch", _trapdoor_variants("l39_hatch"))
    _item_block("l39_hatch", "l39_hatch_bottom")

    # Rungs (ladder): two U-shaped staples standing out from the wall at the south side.
    t = "#iron"
    els = []
    for y0 in (3.0, 11.0):
        els.append(_el((3.5, y0, 10.5), (12.5, y0 + 1.5, 12.0), t))
        els.append(_el((3.5, y0, 12.0), (5.0, y0 + 1.5, 16.0), t, ("north", "east", "west", "up", "down")))
        els.append(_el((11.0, y0, 12.0), (12.5, y0 + 1.5, 16.0), t, ("north", "east", "west", "up", "down")))
        for x0 in (2.75, 10.25):                                        # flat anchor washers on the wall
            els.append(_el((x0, y0 - 0.75, 15.5), (x0 + 3.0, y0 + 2.25, 16.0), t, ("north", "east", "west", "up", "down")))
    _model("l39_rungs", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                         "ambientocclusion": False,
                         "textures": {"iron": _ref("l39_rungs"), "particle": _ref("l39_rungs")}, "elements": els})
    _facing_state("l39_rungs")
    _item_flat("l39_rungs", "backrooms:item/l39_rungs")

    # Orb light: invisible block; the model only names a particle texture.
    _model("l39_orb_light", {"textures": {"particle": _ref("l39_marble")}})
    _plain_state("l39_orb_light")
    _item_flat("l39_orb_light", "backrooms:item/l39_orb_light")

    # Stagnant film: a translucent sheet floating just above the bottom of its block (clear of
    # the water surface below it, so the two don't flicker), seen from above and below.
    f = "#film"
    _model("l39_stagnant_film", {"parent": "minecraft:block/block", "render_type": "minecraft:translucent",
                                 "ambientocclusion": False,
                                 "textures": {"film": _ref("l39_stagnant_film"), "particle": _ref("l39_stagnant_film")},
                                 "elements": [{"from": [0, 0.2, 0], "to": [16, 0.2, 16], "shade": False, "faces": {
                                     "up": {"uv": [0, 0, 16, 16], "texture": f},
                                     "down": {"uv": [0, 16, 16, 0], "texture": f}}}]})
    _state("l39_stagnant_film", {"": [{"model": _ref("l39_stagnant_film"), **({"y": y} if y else {})} for y in (0, 90, 180, 270)]})
    _item_flat("l39_stagnant_film", _ref("l39_stagnant_film"))


def _slab(name: str, double: str, top: str, side: str, bottom: str) -> None:
    """Vanilla slab: bottom / top halves, and the full block's own model for a double slab
    (waterlogging doesn't change the look, so it isn't in the blockstate)."""
    tex = {"bottom": _ref(bottom), "top": _ref(top), "side": _ref(side)}
    _model(name, {"parent": "minecraft:block/slab", "textures": tex})
    _model(f"{name}_top", {"parent": "minecraft:block/slab_top", "textures": tex})
    _state(name, {"type=bottom": {"model": _ref(name)}, "type=top": {"model": _ref(f"{name}_top")},
                  "type=double": {"model": _ref(double)}})
    _item_block(name)


def _pane_model(name: str, tex: str) -> None:
    """A window piece: one thin translucent pane at z 11..12 (set back in the wall), the picture
    on its front (north) and mirrored on its back, so from inside you see the same window."""
    _model(name, {"parent": "minecraft:block/block", "render_type": "minecraft:translucent", "ambientocclusion": False,
                  "textures": {"glass": _ref(tex), "particle": _ref(tex)},
                  "elements": [{"from": [0, 0, 11], "to": [16, 16, 12], "faces": {
                      "north": {"uv": [0, 0, 16, 16], "texture": "#glass"},
                      "south": {"uv": [16, 0, 0, 16], "texture": "#glass"}}}]})


def _rot(model: str, y: int, uvlock: bool = False) -> dict:
    v = {"model": _ref(model)}
    if y:
        v["y"] = y
        if uvlock:
            v["uvlock"] = True
    return v


def _window_models() -> None:
    """Mill window (block/MillWindowBlock: facing, col, row, arch) and tower window
    (block/TowerWindowBlock: facing, row). ARCH only changes the top row."""
    for c in range(3):
        for r in range(5):
            _pane_model(f"l39_mill_window_{c}_{r}", f"l39_mill_window_{c}_{r}")
        _pane_model(f"l39_mill_window_{c}_arch", f"l39_mill_window_{c}_arch")
    variants = {}
    for f, y in FACINGS:
        for c in range(3):
            for r in range(5):
                for arch in ("false", "true"):
                    m = f"l39_mill_window_{c}_arch" if (r == 4 and arch == "true") else f"l39_mill_window_{c}_{r}"
                    variants[f"facing={f},col={c},row={r},arch={arch}"] = _rot(m, y)
    _state("l39_mill_window", variants)
    _item_block("l39_mill_window", "l39_mill_window_1_2")
    for r in range(3):
        _pane_model(f"l39_tower_window_{r}", f"l39_tower_window_{r}")
    _state("l39_tower_window", {f"facing={f},row={r}": _rot(f"l39_tower_window_{r}", y) for f, y in FACINGS for r in range(3)})
    _item_block("l39_tower_window", "l39_tower_window_1")


# The tunnel ceilings, exactly as world/layout/Level39Vault.java works them out.
VAULT_Y0, VAULT_R = 43.0, 4.5
DOME_R, DOME_Y0, DOME_WALL = 10.0, 40.0, 7.5
BASE_Y = 41                              # Level39Layout.TUNNEL_FEET_Y: row 0 of the pieces
AIR, SOLID, PIECE = 0, 1, 2
# FACING turns the canonical piece (drawn for SOUTH) by this many quarter turns (blockstate y / 90).
CURVE_ROT = (("south", 0), ("west", 1), ("north", 2), ("east", 3))
DOME_TILES = 48                          # marble tiles once round the dome's wall (circumference 47.1)


def _barrel_ceiling(u: float) -> float:
    a = abs(u)
    return -np.inf if a >= VAULT_R else VAULT_Y0 + np.sqrt(VAULT_R * VAULT_R - a * a)


def _dome_ceiling(r: float) -> float:
    return -np.inf if r > DOME_WALL else DOME_Y0 + np.sqrt(DOME_R * DOME_R - r * r)


def _barrel_cell(ao: int, row: int) -> int:
    if ao > 4:
        return SOLID
    bottom = BASE_Y + row
    # Below the springing line the sides are straight walls at R: open walkways (Level39Vault.barrelCell).
    if bottom + 1 <= VAULT_Y0 and ao + 0.5 <= VAULT_R:
        return AIR
    c_max = _barrel_ceiling(max(0, ao - 0.5))
    c_min = _barrel_ceiling(ao + 0.5)
    return AIR if c_min >= bottom + 1 else SOLID if c_max <= bottom else PIECE


def _dome_cell(a: int, b: int, row: int) -> int:
    if a > 8 or b > 8:
        return SOLID
    bottom = BASE_Y + row
    c_max = _dome_ceiling(np.hypot(max(0, a - 0.5), max(0, b - 0.5)))
    c_min = _dome_ceiling(np.hypot(a + 0.5, b + 0.5))
    return AIR if c_min >= bottom + 1 else SOLID if c_max <= bottom else PIECE


# ---- OBJ meshes ----------------------------------------------------------------------------
# The vault and dome are smooth OBJ meshes (NeoForge's OBJ loader, as Level 37's curved tiles):
# smooth normals, no steps. Every piece is a closed solid (box minus the tunnel's air), so where a
# piece meets the air of another run or chamber you never see into it. The marble is mapped in
# world space, so it runs on across pieces and into the plain marble walls; because a texture
# can't be turned with an OBJ model, each FACING gets its own model with its own mapping.
# Shading is baked into vertex colours (flat lighting, no directional shade, no AO), so wall,
# vault and dome read as one evenly lit white shell; only the crown gets a hint of shadow.


def _turn(p, turns, about=0.5):
    x, y, z = p
    for _ in range(turns):                  # one quarter turn = blockstate y 90: (dx, dz) -> (-dz, dx)
        x, z = about - (z - about), about + (x - about)
    return (x, y, z)


def _shade(n) -> float:
    return 1.0 - 0.06 * max(0.0, -n[1])


class _Poly:
    """One convex face: positions, smooth normals, a kind ('flat', 'curve', 'skin', 'wall') and
    optional per-vertex data `src` (for 'curve': the (u, Y) each vertex sits at in the profile)."""

    def __init__(self, ps, ns, want, kind, src=None):
        a, b, c = (np.array(p, float) for p in ps[:3])
        if np.dot(np.cross(b - a, c - a), want) < 0:      # counter-clockwise seen from outside
            ps, ns = ps[::-1], ns[::-1]
            src = src[::-1] if src is not None else None
        self.ps, self.ns, self.kind, self.src = list(ps), list(ns), kind, src


def _split_tiles(ps, ns, s):
    """Cut a convex polygon (with a linear attribute s) along every whole number of s, so each
    part's texture coordinate stays inside one tile of the atlas sprite."""
    lo, hi = int(np.floor(min(s) + 1e-9)), int(np.ceil(max(s) - 1e-9))
    out = []
    for k in range(lo, max(hi, lo + 1)):
        cur = list(zip(ps, ns, s))
        for bound, keep_ge in ((k, True), (k + 1, False)):
            nxt = []
            for i in range(len(cur)):
                (p0, n0, s0), (p1, n1, s1) = cur[i], cur[(i + 1) % len(cur)]
                in0 = s0 >= bound - 1e-9 if keep_ge else s0 <= bound + 1e-9
                in1 = s1 >= bound - 1e-9 if keep_ge else s1 <= bound + 1e-9
                if in0:
                    nxt.append((p0, n0, s0))
                if in0 != in1:
                    t = (bound - s0) / (s1 - s0)
                    p = tuple(a + (b - a) * t for a, b in zip(p0, p1))
                    n = np.array(n0) + (np.array(n1) - np.array(n0)) * t
                    nxt.append((p, tuple(n / (np.linalg.norm(n) + 1e-12)), float(bound)))
            cur = nxt
            if len(cur) < 3:
                break
        if len(cur) >= 3:
            out.append(([c[0] for c in cur], [c[1] for c in cur], [c[2] - k for c in cur]))
    return out


def _write_obj(folder, name: str, faces) -> None:
    """faces: list of (positions, normals, uvs, shades); vertex data shared where identical."""
    tables = {"v": {}, "vt": {}, "vn": {}, "vc": {}}
    lines, body = ["# generated by tools/anomaly_assets/level39.py", "mtllib l39_marble.mtl", "usemtl marble"], []

    def ref(kind, key, text):
        t = tables[kind]
        if key not in t:
            t[key] = len(t) + 1
            lines.append(f"{kind} {text}")
        return t[key]
    for ps, ns, uvs, shades in faces:
        refs = []
        for p, n, uv, sh in zip(ps, ns, uvs, shades):
            pk = tuple(round(c, 5) for c in p)
            uk = tuple(round(c, 5) for c in uv)
            nk = tuple(round(c, 4) for c in n)
            ck = round(sh, 3)
            refs.append("/".join(str(i) for i in (
                ref("v", pk, " ".join(f"{c:.5f}" for c in pk)), ref("vt", uk, " ".join(f"{c:.5f}" for c in uk)),
                ref("vn", nk, " ".join(f"{c:.4f}" for c in nk)), ref("vc", ck, f"{ck:.3f} {ck:.3f} {ck:.3f} 1.0"))))
        # the loader takes triangles and quads; bigger (convex) polygons go in as a fan
        if len(refs) <= 4:
            body.append("f " + " ".join(refs))
        else:
            body.extend(f"f {refs[0]} {refs[k]} {refs[k + 1]}" for k in range(1, len(refs) - 1))
    (folder / f"{name}.obj").write_text("\n".join(lines + body) + "\n", newline="\n")
    m = "backrooms:block/l39_marble"
    (folder / f"{name}.json").write_text(json.dumps({
        "loader": "neoforge:obj",
        "model": f"backrooms:models/block/l39/{folder.name}/{name}.obj",
        "flip_v": True,
        "shade_quads": False,
        "emissive_ambient": False,
        "automatic_culling": True,
        "ambientocclusion": False,
        "textures": {"marble": m, "particle": m},
    }, indent=2) + "\n", newline="\n")


def _planar_uv(p, n):
    """World-aligned flat mapping (block-local coordinates), like a plain marble block's faces."""
    ax = int(np.argmax(np.abs(n)))
    return (p[2], p[1]) if ax == 0 else (p[0], p[2]) if ax == 1 else (p[0], p[1])


def _bake(polys, turns, curve_uv, curve_s):
    """Turn canonical polygons into world faces with world-space texture coordinates.
    curve_s(src) -> s per vertex for 'curve' polygons (cut into whole tiles here);
    curve_uv(p_world, s) -> (u, v)."""
    faces = []
    for poly in polys:
        if poly.kind == "curve":
            parts = _split_tiles(poly.ps, poly.ns, [curve_s(*v) for v in poly.src])
        else:
            parts = [(poly.ps, poly.ns, None)]
        for ps, ns, s in parts:
            wps = [_turn(p, turns) for p in ps]
            wns = [_turn(n, turns, 0.0) for n in ns]
            if poly.kind == "curve":
                uvs = [curve_uv(p, sv) for p, sv in zip(wps, s)]
                shades = [_shade(n) for n in wns]
            else:
                uvs = [_planar_uv(p, wns[0]) for p in wps]
                shades = [1.0] * len(wps)
            faces.append((wps, wns, uvs, shades))
    return faces


def _profile_s(w: float, Y: float) -> float:
    """Distance along the tunnel's cross-section (blocks), measured from the bottom of the wall
    on the -w side, up over the vault and down the +w wall: continuous all the way round."""
    R = VAULT_R
    if Y >= VAULT_Y0:
        return float(R * (np.arctan2(w, Y - VAULT_Y0) + np.pi / 2))
    return float(Y - VAULT_Y0) if w < 0 else float(R * np.pi + (VAULT_Y0 - Y))


def _strip_faces(prof):
    """End caps (x = 0 and x = 1) of a cross-section that is solid above the profile
    prof [(z, y)] (sorted by z) up to y = 1, as vertical strips."""
    x0, x1 = 0.0, 1.0
    polys = []
    for (za, ya), (zb, yb) in zip(prof, prof[1:]):
        if zb - za < 1e-9 or (ya >= 1 - 1e-9 and yb >= 1 - 1e-9):
            continue
        pts = [(za, ya), (zb, yb), (zb, 1.0), (za, 1.0)]
        pts = [q for i, q in enumerate(pts) if all(abs(q[0] - r[0]) + abs(q[1] - r[1]) > 1e-9 for r in pts[:i])]
        for x, sgn in ((x0, -1.0), (x1, 1.0)):
            n = (sgn, 0.0, 0.0)
            polys.append(_Poly([(x, y, z) for z, y in pts], [n] * len(pts), n, "flat"))
    return polys


def _barrel_polys(o: int, row: int):
    """Canonical (SOUTH) barrel piece: block-local x along the run, z = u - (o - 0.5), y = Y - base."""
    R, base = VAULT_R, BASE_Y + row
    u0, u1 = o - 0.5, o + 0.5
    t_u0, t_u1 = np.arcsin(np.clip(u0 / R, -1, 1)), np.arcsin(np.clip(u1 / R, -1, 1))
    t_a = np.arccos(np.clip((base + 1 - VAULT_Y0) / R, -1, 1))       # |theta| >= t_a: below the top
    t_b = np.arccos(np.clip((base - VAULT_Y0) / R, -1, 1))           # |theta| <= t_b: above the bottom
    t_b = min(t_b, np.pi / 2)
    arcs = []
    for lo, hi in ((-t_b, -t_a), (t_a, t_b)):
        lo, hi = max(lo, t_u0), min(hi, t_u1)
        if hi - lo > 1e-9:
            n = max(1, int(np.ceil((hi - lo) * R * 12)))                # 12 segments per block of arc
            arcs.append([lo + (hi - lo) * k / n for k in range(n + 1)])

    def zy(t):
        return (float(np.clip(R * np.sin(t) - u0, 0, 1)), float(np.clip(VAULT_Y0 + R * np.cos(t) - base, 0, 1)))

    def f(z):
        c = _barrel_ceiling(min(u0 + z, R - 1e-9))
        return float(np.clip(c - base, 0, 1))
    prof = {0.0: f(0.0), 1.0: f(1.0)}
    for arc in arcs:
        for t in arc:
            z, y = zy(t)
            prof[round(z, 9)] = y
    prof = sorted(prof.items())
    polys = []
    for arc in arcs:                                                  # the curved underside
        for ta, tb in zip(arc, arc[1:]):
            (za, ya), (zb, yb) = zy(ta), zy(tb)
            na, nb = (0.0, -np.cos(ta), -np.sin(ta)), (0.0, -np.cos(tb), -np.sin(tb))
            tm = (ta + tb) / 2
            polys.append(_Poly([(0.0, ya, za), (1.0, ya, za), (1.0, yb, zb), (0.0, yb, zb)], [na, na, nb, nb],
                               (0.0, -np.cos(tm), -np.sin(tm)), "curve",
                               [(u0 + za, base + ya), (u0 + za, base + ya), (u0 + zb, base + yb), (u0 + zb, base + yb)]))
    polys += _strip_faces(prof)                                        # ends of the run piece
    for (za, ya), (zb, yb) in zip(prof, prof[1:]):
        if zb - za < 1e-9:
            continue
        if (ya + yb) / 2 < 1 - 1e-9:                                    # top, where solid
            polys.append(_Poly([(0.0, 1.0, za), (1.0, 1.0, za), (1.0, 1.0, zb), (0.0, 1.0, zb)], [(0, 1, 0)] * 4, (0, 1, 0), "flat"))
        if ya <= 1e-9 and yb <= 1e-9:                                   # bottom, where solid to the floor
            polys.append(_Poly([(0.0, 0.0, za), (1.0, 0.0, za), (1.0, 0.0, zb), (0.0, 0.0, zb)], [(0, -1, 0)] * 4, (0, -1, 0), "flat"))
    for z, sgn in ((0.0, -1.0), (1.0, 1.0)):                          # sides toward the neighbours
        y = prof[0][1] if z == 0.0 else prof[-1][1]
        if y < 1 - 1e-9:
            n = (0.0, 0.0, sgn)
            polys.append(_Poly([(0.0, y, z), (1.0, y, z), (1.0, 1.0, z), (0.0, 1.0, z)], [n] * 4, n, "flat"))
    return polys


def _wall_polys(row: int):
    """Canonical (SOUTH) straight wall under the springing (o = 5): a full cube whose north face
    (z = 0, u = 4.5) faces the tunnel and maps like the vault's curve."""
    polys = []
    cube = {"north": ([(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)], (0, 0, -1)),
            "south": ([(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)], (0, 0, 1)),
            "west": ([(0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)], (-1, 0, 0)),
            "east": ([(1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0)], (1, 0, 0)),
            "down": ([(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)], (0, -1, 0)),
            "up": ([(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)], (0, 1, 0))}
    for name, (ps, n) in cube.items():
        ps = [tuple(float(c) for c in p) for p in ps]
        nn = tuple(float(c) for c in n)
        if name == "north":
            polys.append(_Poly(ps, [nn] * 4, nn, "curve", [(VAULT_R, BASE_Y + row + p[1]) for p in ps]))
        else:
            polys.append(_Poly(ps, [nn] * 4, nn, "flat"))
    return polys


def _vault_models() -> None:
    """Barrel vault pieces (block/VaultBlock: facing, o 0..5, row 0..7), as smooth OBJ meshes.

    Canonical frame = SOUTH: block-local x runs along the vault, z is the offset u = o - 0.5 + z
    from the run's centre line, y = Y - (41 + row). The arc: Y = 43 + sqrt(4.5^2 - u^2) (world/
    layout/Level39Vault.java). o = 5, rows 0..1 is the straight wall under the springing."""
    folder = ASSETS / "models" / "block" / "l39" / "vault"
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "l39_marble.mtl").write_text("newmtl marble\nKd 1.0 1.0 1.0\nmap_Kd #marble\n", newline="\n")
    canon = {}
    for o in range(5):
        for row in range(8):
            if _barrel_cell(o, row) == PIECE:
                canon[(o, row)] = _barrel_polys(o, row)
    for row in range(2):
        canon[(5, row)] = _wall_polys(row)
    variants = {}
    for facing, turns in CURVE_ROT:
        sign = 1.0 if facing in ("south", "east") else -1.0           # world side of the centre line
        along_x = facing in ("south", "north")

        def curve_uv(p, s, along_x=along_x):
            return ((p[0] if along_x else p[2]), s)

        def curve_s(u, Y, sign=sign):
            return _profile_s(sign * u, Y)
        for (o, row), polys in canon.items():
            name = f"vault_{facing}_{o}_{row}"
            faces = _bake(polys, turns, curve_uv, curve_s)
            if faces:
                _write_obj(folder, name, faces)
                variants[f"facing={facing},o={o},row={row}"] = {"model": f"backrooms:block/l39/vault/{name}"}
            else:
                variants[f"facing={facing},o={o},row={row}"] = {"model": _ref("l39_vault_empty")}
        for o in range(6):
            for row in range(8):
                variants.setdefault(f"facing={facing},o={o},row={row}", {"model": _ref("l39_marble")})
    _model("l39_vault_empty", {"parent": "minecraft:block/block", "textures": {"particle": _ref("l39_marble")}})
    _state("l39_vault", variants)
    _item_block("l39_vault", "l39/vault/vault_south_3_4")


def _clip_convex(poly, inside, cut):
    """Sutherland-Hodgman: keep the part of a convex polygon where inside(p) holds; cut(p, q)
    returns the crossing point on segment p-q."""
    out = []
    for i in range(len(poly)):
        p, q = poly[i], poly[(i + 1) % len(poly)]
        ip, iq = inside(p), inside(q)
        if ip:
            out.append(p)
        if ip != iq:
            out.append(cut(p, q))
    return out


DOME_CIRCLE = [(DOME_WALL * np.cos(2 * np.pi * k / 512), DOME_WALL * np.sin(2 * np.pi * k / 512)) for k in range(512)]


def _in_circle(p) -> bool:
    """Inside the dome wall's 512-gon (the same polygon for every piece, so they all fit)."""
    ang = np.arctan2(p[1], p[0]) % (2 * np.pi)
    k = int(ang / (2 * np.pi / 512)) % 512
    (x0, z0), (x1, z1) = DOME_CIRCLE[k], DOME_CIRCLE[(k + 1) % 512]
    return (x1 - x0) * (p[1] - z0) - (z1 - z0) * (p[0] - x0) >= -1e-12


def _clip_to_circle(poly):
    for k in range(512):
        (x0, z0), (x1, z1) = DOME_CIRCLE[k], DOME_CIRCLE[(k + 1) % 512]
        xs = [p[0] for p in poly]
        zs = [p[1] for p in poly]
        # only the edges whose line can cut this small polygon matter
        side = [(x1 - x0) * (z - z0) - (z1 - z0) * (x - x0) for x, z in zip(xs, zs)]
        if min(side) >= -1e-12:
            continue

        def inside(p, x0=x0, z0=z0, x1=x1, z1=z1):
            return (x1 - x0) * (p[1] - z0) - (z1 - z0) * (p[0] - x0) >= -1e-12

        def cut(p, q, x0=x0, z0=z0, x1=x1, z1=z1):
            sp = (x1 - x0) * (p[1] - z0) - (z1 - z0) * (p[0] - x0)
            sq = (x1 - x0) * (q[1] - z0) - (z1 - z0) * (q[0] - x0)
            t = sp / (sp - sq)
            return (p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t)
        poly = _clip_convex(poly, inside, cut)
        if len(poly) < 3:
            return []
    return poly


def _dome_h(x: float, z: float, base: float) -> float:
    """Height of the dome's surface over (x, z) (chamber-relative), in blocks above base; the
    wall's 512-gon is treated as the circle r = 7.5 (points on it get the rim height)."""
    r = min(float(np.hypot(x, z)), DOME_WALL)
    return DOME_Y0 + np.sqrt(DOME_R * DOME_R - r * r) - base


def _dome_polys(a: int, b: int, row: int, n_grid: int = 4):
    """Canonical (SOUTH = south-east quarter) dome piece. Block-local x, z; chamber-relative
    X = a - 0.5 + x, Z = b - 0.5 + z; y = Y - (41 + row). The air is r < 7.5 under the sphere
    Y = 40 + sqrt(100 - r^2); everything else in the block is marble."""
    base = BASE_Y + row
    ox, oz = a - 0.5, b - 0.5
    polys = []
    g = 1.0 / n_grid

    def sk_normal(X, Y, Z):
        v = np.array([-X, DOME_Y0 - Y, -Z])
        return tuple(v / np.linalg.norm(v))
    for i in range(n_grid):
        for j in range(n_grid):
            sq = [(ox + i * g, oz + j * g), (ox + (i + 1) * g, oz + j * g), (ox + (i + 1) * g, oz + (j + 1) * g), (ox + i * g, oz + (j + 1) * g)]
            disc = _clip_to_circle(sq)
            if len(disc) < 3:
                continue
            for k in range(1, len(disc) - 1):
                tri = [(p[0], p[1], _dome_h(p[0], p[1], base)) for p in (disc[0], disc[k], disc[k + 1])]

                def lerp3(p, q, t):
                    return tuple(pa + (qa - pa) * t for pa, qa in zip(p, q))
                below_top = _clip_convex(tri, lambda p: p[2] <= 1 + 1e-12,
                                         lambda p, q: lerp3(p, q, (1 - p[2]) / (q[2] - p[2])))
                if len(below_top) < 3:
                    continue
                skin = _clip_convex(below_top, lambda p: p[2] >= -1e-12, lambda p, q: lerp3(p, q, (0 - p[2]) / (q[2] - p[2])))
                floor = _clip_convex(below_top, lambda p: p[2] <= 1e-12, lambda p, q: lerp3(p, q, (0 - p[2]) / (q[2] - p[2])))
                if len(skin) >= 3:
                    ps = [(p[0] - ox, p[2], p[1] - oz) for p in skin]
                    ns = [sk_normal(p[0], base + p[2], p[1]) for p in skin]
                    polys.append(_Poly(ps, ns, np.mean(ns, axis=0), "skin"))
                if len(floor) >= 3:
                    ps = [(p[0] - ox, 0.0, p[1] - oz) for p in floor]
                    polys.append(_Poly(ps, [(0.0, -1.0, 0.0)] * len(ps), (0, -1, 0), "flat"))
    # the round wall below the dome's rim
    hw = min(1.0, DOME_Y0 + np.sqrt(DOME_R ** 2 - DOME_WALL ** 2) - base)
    if hw > 1e-9:
        for k in range(512):
            p, q = DOME_CIRCLE[k], DOME_CIRCLE[(k + 1) % 512]
            seg = _clip_segment(p, q, ox, oz, ox + 1, oz + 1)
            if seg is None:
                continue
            (px, pz), (qx, qz) = seg
            npn = (-px / DOME_WALL, 0.0, -pz / DOME_WALL)
            nqn = (-qx / DOME_WALL, 0.0, -qz / DOME_WALL)
            ps = [(px - ox, 0.0, pz - oz), (qx - ox, 0.0, qz - oz), (qx - ox, hw, qz - oz), (px - ox, hw, pz - oz)]
            polys.append(_Poly(ps, [npn, nqn, nqn, npn], np.add(npn, nqn), "wall"))
    # sides of the block toward its neighbours, solid above the profile along each side
    for axis, val, sgn in (("x", 0.0, -1.0), ("x", 1.0, 1.0), ("z", 0.0, -1.0), ("z", 1.0, 1.0)):
        def world(t):
            return (ox + val, oz + t) if axis == "x" else (ox + t, oz + val)
        ts = {0.0, 1.0} | {k * g for k in range(n_grid + 1)}
        crossings = set()
        for k in range(512):                                         # where the wall crosses this side
            p, q = DOME_CIRCLE[k], DOME_CIRCLE[(k + 1) % 512]
            c0 = (p[0] - (ox + val)) if axis == "x" else (p[1] - (oz + val))
            c1 = (q[0] - (ox + val)) if axis == "x" else (q[1] - (oz + val))
            if c0 * c1 < 0:
                t = c0 / (c0 - c1)
                other = (p[1] + (q[1] - p[1]) * t - oz) if axis == "x" else (p[0] + (q[0] - p[0]) * t - ox)
                if 0 < other < 1:
                    crossings.add(float(other))
        rim = float(np.clip(_dome_h(DOME_WALL, 0.0, base), 0, 1))
        prof = []                                                   # (t, y, inside the wall?)
        for t in sorted(ts | crossings):
            if t in crossings:                                       # straight down the wall's face
                before_in = _in_circle(world(max(0.0, t - 1e-6)))
                prof += [(t, rim, True), (t, 0.0, False)] if before_in else [(t, 0.0, False), (t, rim, True)]
                continue
            X, Z = world(t)
            inside = _in_circle((X, Z))
            prof.append((t, float(np.clip(_dome_h(X, Z, base), 0, 1)) if inside else 0.0, inside))
        # add the points where the profile reaches the top or the floor between samples
        full = []
        for (ta, ya, ia), (tb, yb, ib) in zip(prof, prof[1:]):
            full.append((ta, ya))
            if ia and ib and tb - ta > 1e-9:
                ha, hb = _dome_h(*world(ta), base), _dome_h(*world(tb), base)
                for lvl in (0.0, 1.0):
                    if (ha - lvl) * (hb - lvl) < 0:
                        full.append((ta + (tb - ta) * (lvl - ha) / (hb - ha), lvl))
        full.append(prof[-1][:2])
        for (ta, ya), (tb, yb) in zip(full, full[1:]):
            if tb - ta < 1e-9 or (ya >= 1 - 1e-9 and yb >= 1 - 1e-9):
                continue
            pts = [(ta, ya), (tb, yb), (tb, 1.0), (ta, 1.0)]
            pts = [q for m, q in enumerate(pts) if all(abs(q[0] - r[0]) + abs(q[1] - r[1]) > 1e-9 for r in pts[:m])]
            if axis == "x":
                ps, n = [(val, y, t) for t, y in pts], (sgn, 0.0, 0.0)
            else:
                ps, n = [(t, y, val) for t, y in pts], (0.0, 0.0, sgn)
            polys.append(_Poly(ps, [n] * len(ps), n, "flat"))
    return polys


def _clip_segment(p, q, x0, z0, x1, z1):
    """Liang-Barsky clip of segment p-q to the box [x0, x1] x [z0, z1]; None if outside."""
    t0, t1 = 0.0, 1.0
    dx, dz = q[0] - p[0], q[1] - p[1]
    for pp, qq in ((-dx, p[0] - x0), (dx, x1 - p[0]), (-dz, p[1] - z0), (dz, z1 - p[1])):
        if abs(pp) < 1e-15:
            if qq < 0:
                return None
            continue
        t = qq / pp
        if pp < 0:
            t0 = max(t0, t)
        else:
            t1 = min(t1, t)
        if t0 > t1:
            return None
    if t1 - t0 < 1e-9:
        return None
    return (p[0] + dx * t0, p[1] + dz * t0), (p[0] + dx * t1, p[1] + dz * t1)


def _dome_models() -> None:
    """Dome pieces (block/DomeBlock: facing, piece = (a * 9 + b) * 10 + row) as smooth OBJ meshes.
    The dome's surface is mapped from above (world x, z), its round wall by the distance round
    the chamber (DOME_TILES tiles per turn, so it closes up) and height."""
    folder = ASSETS / "models" / "block" / "l39" / "dome"
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "l39_marble.mtl").write_text("newmtl marble\nKd 1.0 1.0 1.0\nmap_Kd #marble\n", newline="\n")
    variants = {}
    for a in range(9):
        for b in range(9):
            for row in range(10):
                piece = (a * 9 + b) * 10 + row
                if _dome_cell(a, b, row) != PIECE:
                    for facing, _ in CURVE_ROT:
                        variants[f"facing={facing},piece={piece}"] = {"model": _ref("l39_marble")}
                    continue
                canon = _dome_polys(a, b, row)
                for facing, turns in CURVE_ROT:
                    faces = []
                    for poly in canon:
                        wps = [_turn(p, turns) for p in poly.ps]
                        wns = [_turn(n, turns, 0.0) for n in poly.ns]
                        if poly.kind == "skin":
                            faces.append((wps, wns, [(p[0], p[2]) for p in wps], [_shade(n) for n in wns]))
                        elif poly.kind == "wall":
                            rel = [_turn((p[0] + a - 0.5, p[1], p[2] + b - 0.5), turns, 0.0) for p in poly.ps]
                            s = [float((np.arctan2(r[2], r[0]) % (2 * np.pi)) * DOME_TILES / (2 * np.pi)) for r in rel]
                            if max(s) - min(s) > DOME_TILES / 2:
                                s = [v + DOME_TILES if v < DOME_TILES / 2 else v for v in s]
                            for ps, ns, sv in _split_tiles(wps, wns, s):
                                faces.append((ps, ns, [(sv_, p[1]) for p, sv_ in zip(ps, sv)], [_shade(n) for n in ns]))
                        else:
                            faces.append((wps, wns, [_planar_uv(p, wns[0]) for p in wps], [1.0] * len(wps)))
                    name = f"dome_{facing}_{a}_{b}_{row}"
                    _write_obj(folder, name, faces)
                    variants[f"facing={facing},piece={piece}"] = {"model": f"backrooms:block/l39/dome/{name}"}
    _state("l39_dome", variants, compact=True)
    _item_block("l39_dome", "l39/dome/dome_south_3_3_8")


# ---- Junction pieces: where a run's barrel vault comes into a domed chamber ----------------
# Seen from the chamber, the barrel (a half cylinder) cuts into the dome (a sphere): the true
# surface is their union, meeting in a sharp crease. Built block by block from the two separate
# piece sets, that crease came out as a saw-tooth ring of steps round every arch, with odd bright
# and dark faces where pieces were cut off. These cells get their own pieces, meshed from the exact
# union. Canonical: the run leaves the chamber toward +z (SOUTH); `across` = x offset from the
# chamber's centre, `along` = z offset; FACING turns it like the dome (WEST = run toward -x...).
JUNCTION_ALONG = range(4, 9)                 # 4..8 blocks from the centre along the run
JUNCTION_ACROSS = range(-4, 5)               # inside the barrel's half-width (4.5)
JUNCTION_ROWS = range(10)
JUNCTION_SAMPLES = 9                          # per axis, for deciding which cells need a piece
JUNCTION_MESH = 16                            # marching-cubes cells per block


def _sdf_barrel(X, Y, Z):
    """> 0 inside the barrel's air (run along z through x = 0), same shape as Level39Vault."""
    above = Y >= VAULT_Y0
    d_arc = VAULT_R - np.sqrt(X * X + (Y - VAULT_Y0) ** 2)
    d_wall = VAULT_R - np.abs(X)
    return np.where(above, d_arc, d_wall)


def _sdf_dome(X, Y, Z):
    """> 0 inside the chamber's air: under the sphere and inside its round wall."""
    r = np.sqrt(X * X + Z * Z)
    return np.minimum(DOME_R - np.sqrt(X * X + Z * Z + (Y - DOME_Y0) ** 2), DOME_WALL - r)


def junction_cells() -> list:
    """(across, along, row) of every cell where the union is neither the barrel's nor the dome's
    shape alone. The same list is written for the Java side (data/backrooms/level39/junction_cells.txt)."""
    k = (np.arange(JUNCTION_SAMPLES) + 0.5) / JUNCTION_SAMPLES
    gx, gy, gz = np.meshgrid(k, k, k, indexing="ij")
    cells = []
    for along in JUNCTION_ALONG:
        for across in JUNCTION_ACROSS:
            for row in JUNCTION_ROWS:
                X, Y, Z = across - 0.5 + gx, BASE_Y + row + gy, along - 0.5 + gz
                fb, fd = _sdf_barrel(X, Y, Z) > 0, _sdf_dome(X, Y, Z) > 0
                union = fb | fd
                if union.all() or not union.any():
                    continue
                if (union != fb).any() and (union != fd).any():
                    cells.append((across, along, row))
    return cells


def junction_piece_index(across: int, along: int, row: int) -> int:
    return ((along - JUNCTION_ALONG.start) * len(JUNCTION_ACROSS) + (across - JUNCTION_ACROSS.start)) * len(JUNCTION_ROWS) + row


def _junction_faces(across: int, along: int, row: int):
    """The cell's solid (marble outside the union's air) as triangles in block-local coordinates,
    with normals: smooth on the curves (the surface each triangle lies on), flat on the cell's faces."""
    from skimage.measure import marching_cubes
    n, pad = JUNCTION_MESH, 1
    s = (np.arange(-pad, n + pad + 1)) / n
    gx, gy, gz = np.meshgrid(s, s, s, indexing="ij")
    X, Y, Z = across - 0.5 + gx, BASE_Y + row + gy, along - 0.5 + gz
    f = np.maximum(_sdf_barrel(X, Y, Z), _sdf_dome(X, Y, Z))        # > 0 in air
    outside = (gx < 0) | (gx > 1) | (gy < 0) | (gy > 1) | (gz < 0) | (gz > 1)
    f = np.where(outside, 1.0 / n, f)                                   # air round the cell closes the solid
    verts, tris, _, _ = marching_cubes(-f, level=0.0, spacing=(1.0 / n,) * 3)
    verts = np.clip(verts - pad / n, 0.0, 1.0)
    faces = []
    eps = 1e-6
    caps = {}                                   # (axis, 0 or 1) -> the flat triangles on that face
    for tri in tris:
        ps = [tuple(verts[i]) for i in tri]
        P = np.array(ps)
        cap = None
        for ax in range(3):
            for val in (0.0, 1.0):
                if np.all(np.abs(P[:, ax] - val) < eps):
                    cap = (ax, val)
        if cap is not None:
            caps.setdefault(cap, []).append(P)
            continue
        else:
            W = P + np.array([across - 0.5, BASE_Y + row, along - 0.5])
            c = W.mean(axis=0)
            on_barrel = _sdf_barrel(*c) < _sdf_dome(*c)                # the surface the solid follows here
            ns = []
            for x, y, z in W:
                if on_barrel:
                    g = (-x, -(y - VAULT_Y0), 0.0) if y >= VAULT_Y0 else (-np.sign(x), 0.0, 0.0)
                elif DOME_R - np.sqrt(x * x + z * z + (y - DOME_Y0) ** 2) < DOME_WALL - np.sqrt(x * x + z * z):
                    g = (-x, -(y - DOME_Y0), -z)
                else:
                    g = (-x, 0.0, -z)
                g = np.array(g, float)
                ns.append(tuple(g / (np.linalg.norm(g) + 1e-12)))
        a_, b_, c_ = (np.array(p) for p in ps)
        if np.dot(np.cross(b_ - a_, c_ - a_), np.mean(ns, axis=0)) < 0:
            ps, ns = ps[::-1], ns[::-1]
        if np.linalg.norm(np.cross(np.array(ps[1]) - ps[0], np.array(ps[2]) - ps[0])) < 1e-10:
            continue
        faces.append((ps, ns))
    # Marching cubes cuts the flat faces into hundreds of tiny triangles; merge each face's into one
    # outline and fill that with as few triangles as its corners need (same edge points, no cracks).
    from shapely import constrained_delaunay_triangles
    from shapely.geometry import Polygon
    from shapely.ops import unary_union
    for (ax, val), tris2 in caps.items():
        u, v = [k for k in range(3) if k != ax]
        region = unary_union([Polygon([(p[u], p[v]) for p in tri2]) for tri2 in tris2]).buffer(0)
        nrm = [0.0, 0.0, 0.0]
        nrm[ax] = -1.0 if val == 0.0 else 1.0
        for tri2 in constrained_delaunay_triangles(region).geoms:
            ps = []
            for cu, cv in list(tri2.exterior.coords)[:3]:
                p = [0.0, 0.0, 0.0]
                p[ax], p[u], p[v] = val, cu, cv
                ps.append(tuple(p))
            a_, b_, c_ = (np.array(p) for p in ps)
            cr = np.cross(b_ - a_, c_ - a_)
            if np.linalg.norm(cr) < 1e-10:
                continue
            if np.dot(cr, nrm) < 0:
                ps = ps[::-1]
            faces.append((ps, [tuple(nrm)] * 3))
    return faces


def _junction_models() -> None:
    """Junction pieces (block/JunctionBlock: facing, piece) as OBJ meshes, plus the cell list."""
    folder = ASSETS / "models" / "block" / "l39" / "junction"
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "l39_marble.mtl").write_text("newmtl marble\nKd 1.0 1.0 1.0\nmap_Kd #marble\n", newline="\n")
    cells = junction_cells()
    keep = {junction_piece_index(*cell) for cell in cells}
    variants = {}
    total = len(JUNCTION_ALONG) * len(JUNCTION_ACROSS) * len(JUNCTION_ROWS)
    for piece in range(total):
        if piece not in keep:
            for facing, _ in CURVE_ROT:
                variants[f"facing={facing},piece={piece}"] = {"model": _ref("l39_marble")}
    for across, along, row in cells:
        piece = junction_piece_index(across, along, row)
        canon = _junction_faces(across, along, row)
        for facing, turns in CURVE_ROT:
            faces = []
            for ps, ns in canon:
                wps = [_turn(p, turns) for p in ps]
                wns = [_turn(nv, turns, 0.0) for nv in ns]
                tri_n = np.cross(np.array(wps[1]) - wps[0], np.array(wps[2]) - wps[0])
                faces.append((wps, wns, [_planar_uv(p, tri_n) for p in wps], [_shade(nv) for nv in wns]))
            name = f"junction_{facing}_{across + 4}_{along}_{row}"
            _write_obj(folder, name, faces)
            variants[f"facing={facing},piece={piece}"] = {"model": f"backrooms:block/l39/junction/{name}"}
    _state("l39_junction", variants, compact=True)
    _item_block("l39_junction", f"l39/junction/junction_south_{cells[0][0] + 4}_{cells[0][1]}_{cells[0][2]}")
    table = ASSETS.parent.parent / "data" / "backrooms" / "level39" / "junction_cells.txt"
    table.parent.mkdir(parents=True, exist_ok=True)
    table.write_text("# across along row: cells that use a junction piece (tools/anomaly_assets/level39.py)\n"
                     + "".join(f"{a} {b} {r}\n" for a, b, r in cells), newline="\n")
    print(f"  junction pieces: {len(cells)}")


def _hole_models() -> None:
    """The turf lip over a hole's edge and the roots hanging into it."""
    def face(tex, uv=None, cull=None):
        f = {"texture": tex}
        if uv:
            f["uv"] = uv
        if cull:
            f["cullface"] = cull
        return f
    # Drawn facing NORTH = out over the hole (the lip's ragged front at -z).
    els = [
        {"from": [0, 9, 1], "to": [16, 16, 16], "faces": {
            "up": face("#grass"), "down": face("#soil"), "north": face("#side"), "east": face("#side"),
            "west": face("#side"), "south": face("#soil", cull="south")}},
        {"from": [0, 3, 0.5], "to": [16, 16, 0.5], "faces": {                       # ragged front edge
            "north": face("#edge", [0, 0, 16, 13]), "south": face("#edge", [16, 0, 0, 13])}},
    ]
    for z in (4, 9):                                                               # roots hanging underneath
        els.append({"from": [0, 2, z], "to": [16, 9, z], "faces": {
            "north": face("#roots", [0, 0, 16, 7]), "south": face("#roots", [16, 0, 0, 7])}})
    els.append({"from": [8, 2, 1], "to": [8, 9, 16], "faces": {
        "west": face("#roots", [0, 0, 15, 7]), "east": face("#roots", [15, 0, 0, 7])}})
    _model("l39_grass_overhang", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                  "ambientocclusion": False, "textures": {
                                      "grass": _ref("l39_yard_grass"), "side": _ref("l39_yard_grass_side"),
                                      "soil": _ref("l39_soil"), "edge": _ref("l39_grass_overhang_edge"),
                                      "roots": _ref("l39_roots"), "particle": _ref("l39_yard_grass")},
                                  "elements": els})
    _facing_state("l39_grass_overhang")
    _item_block("l39_grass_overhang")
    _model("l39_roots", {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
                         "textures": {"cross": _ref("l39_roots")}})
    _state("l39_roots", {"": [{"model": _ref("l39_roots")}, {"model": _ref("l39_roots"), "y": 90}]})
    _item_flat("l39_roots", _ref("l39_roots"))


def _trapdoor_variants(name: str) -> dict:
    """Vanilla orientable trapdoor blockstate (the iron_trapdoor table)."""
    fy = {"north": 0, "east": 90, "south": 180, "west": 270}
    out = {}
    for facing, y in fy.items():
        for half in ("bottom", "top"):
            for opn in ("false", "true"):
                if opn == "true":
                    v = {"model": _ref(f"{name}_open")}
                    if half == "top":
                        v["x"] = 180
                        yy = (y + 180) % 360
                    else:
                        yy = y
                else:
                    v = {"model": _ref(f"{name}_{half}")}
                    yy = y
                if yy:
                    v["y"] = yy
                out[f"facing={facing},half={half},open={opn}"] = v
    return out


TEXTURES = [
    tex_l39_surfaces,
    tex_l39_yard_grass_side,
    tex_l39_soil,
    tex_l39_path_mud,
    tex_l39_path_grass,
    tex_l39_rock,
    tex_l39_rubble,
    tex_l39_clay,
    tex_l39_rock_layered,
    tex_l39_roots,
    tex_l39_grass_overhang_edge,
    tex_l39_clapboard_faded,
    tex_l39_clapboard_weathered,
    tex_l39_mill_brick_sooty,
    tex_l39_mill_brick_black,
    tex_l39_mass,
    tex_l39_roof_tar,
    tex_l39_granite,
    tex_l39_mill_window,
    tex_l39_tower_window,
    tex_l39_boarded_window,
    tex_l39_bricked_window,
    tex_l39_gallery,
    tex_l39_marble_stained,
    tex_l39_tiles,
    tex_l39_drain_lining,
    tex_l39_stagnant_film,
    tex_l39_hatch,
    tex_l39_rungs,
    tex_l39_orb_light_icon,
    tex_l39_panel_door,
    tex_l39_world_models,
]
