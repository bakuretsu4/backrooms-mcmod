"""Zone 3, the Alhambra Hall (after Castan's Lucerne maze and its Moorish fantasy): slender faux-
marble columns on black plinths with gilt bases and painted rings, cube capitals carved with
interlace under a tall impost, true horseshoe arches over every panel (Cordoba style: red and
cream voussoirs, a rectangular alfiz round the arch, carved spandrels, a pseudo-kufic frieze), a
carved plaster lattice set back in every arch's head, zellige-and-sebka walls, a terracotta floor
with glazed insets and a painted wooden coffered ceiling.

The arch is too big for the 1-block band: a horseshoe over a 3-block bay needs ~1.4 blocks, so
it springs at YF (just above head height) and its spandrels cover the top corners of the mirror
behind it. The mirror's top edge hides behind a gilt transom; above that the arch's head is filled
with the lattice, the same over a mirror, a glass pane and an opening, so an arch never tells you
which of them is under it."""
from __future__ import annotations

import math

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

from . import carve as C
from . import geo as G
from . import mat as M
from .dims import CEIL, CELL0, CELL1, PANE_Y1, PLANE

SHEET = "alhambra"
PLASTER = M.col("#cdbd98")
OCHRE = M.col("#b88f45")
BLUE = M.col("#4f6f86")
RED = M.col("#8a3f2c")
BRICK = M.col("#97472f")
STONE = M.col("#d6c6a0")
GREEN = M.col("#2f6a52")
BLACK = M.col("#2a2622")
HONEY = M.col("#b9863a")
TURQ = M.col("#3f8a8a")
GLAZE_WHITE = M.col("#ddd5bf")
BISCUIT = M.col("#c49a74")
WOOD = M.col("#4c2a1a")

# ---- The arch (panel-local, see dims) ----------------------------------------------------------
# Every column carries a square pier above its impost; the arch is exactly as deep as the pier, so
# the four arches round a column meet it in clean inside corners.
PIER = 0.11
AX = 1.0                          # the arch's centre line (half way between the column centres)
X0, X1 = -0.5 + PIER, 2.5 - PIER  # the arch wall runs pier face to pier face
A, B = 1.12, 0.96                 # intrados semi-axes: a circle squashed by 14% to fit the bay
K = 0.40                          # horseshoe: the curve runs on 0.4 B below its centre, so the feet
                                  # return inward (foot 0.92 of the widest span)
TOP_I = 3.53                      # crown of the intrados
YC = TOP_I - B                    # centre of the intrados
YF = YC - K * B                   # springing: the top of the impost the arch stands on (~2.19)
# Extrados: wider, its centre higher, so the voussoirs grow from 0.14 at the feet to 0.19 at the
# crown (as at Cordoba, a heavy ring: a fifth of the span would not fit the band).
AE, BE, DYE = A + 0.15, B + 0.14, 0.05
TOP_E = YC + DYE + BE
NV = 17                           # voussoirs; odd so the crown has a keystone. Joints radiate from
                                  # O = (AX, YF), the middle of the springing line.
ALFIZ_IN, ALFIZ_OUT = AE + 0.02, AE + 0.085  # the alfiz frame's verticals, |x - AX|
ALFIZ_Y0, ALFIZ_Y1 = TOP_E + 0.02, TOP_E + 0.09
FRIEZE_Y1 = 3.95                  # the inscription band ALFIZ_Y1..here, then a small cornice
RING_PROUD = 0.02                 # voussoirs stand proud of the spandrels
ALFIZ_PROUD = 0.025
LAT_Y0 = PANE_Y1 + 0.07           # the lattice starts on the transom over the pane's top edge
N_ARC = 22                        # segments round the arch (+ 2 at the alfiz corners)

# ---- The column ---------------------------------------------------------------------------------
SHAFT_Y1 = 1.76
CUBE_Y0, CUBE_Y1 = 1.94, 2.12
CUBE_R = 0.14

# ---- The plain wall (solid_alhambra) -------------------------------------------------------------
WALL_Y0, WALL_Y1 = 0.115, PANE_Y1 - 0.02
WALL_Z = 0.045                    # half thickness: inside the panel's skirting and transom
DADO_Y1 = 0.95                    # zellige up to here, then a glazed listel
INSCR_Y0, INSCR_Y1 = 1.0, 1.2     # the epigraphic band
BEAD_Y1 = 1.24
SEBKA_X0, SEBKA_X1 = -0.3, 2.3    # the recessed carved panel
SEBKA_Y0, SEBKA_Y1 = 1.32, 2.82
SEBKA_DEPTH = 0.02


# ------------------------------------------------------------------------------------------------
# Pattern helpers
# ------------------------------------------------------------------------------------------------

def _world(h, w, x0, x1, y0, y1):
    """Pixel centres of a region painted in panel-local units (row 0 = y1)."""
    X, Y = M.grid(h, w)
    return x0 + X * (x1 - x0), y1 - Y * (y1 - y0)


def _hash(a, b=0, c=0):
    """A repeatable 0..1 value per integer cell (tile tone, missing tiles)."""
    v = (np.asarray(a, np.int64) * 73856093 + np.asarray(b, np.int64) * 19349663
         + np.asarray(c, np.int64) * 83492791) & 0xFFFFF
    return ((v * 2654435761) & 0xFFFF) / 65536.0


def _round(mask, radius):
    """0/1 mask to a rounded height (a carved strand is rounded, not flat)."""
    d = ndimage.distance_transform_edt(mask > 0.5)
    return np.sqrt(np.clip(d / max(radius, 1e-6), 0, 1))


def _shade(height, strength=1.0, cav=0.7, radius=3.0):
    """Baked relief light of a height field in pixel units."""
    return M.relief(height, strength) * M.cavity(height, radius, cav)


def _star_net(fu, fv, s=0.27):
    """Distance (cell units) to the strapwork of the eight-point star net: a star outline in every
    cell, straps from its points to the neighbours' points and diagonals to the cell corners (the
    'lazo de ocho' of Nasrid tile and lattice work). fu, fv: -0.5..0.5 within the cell."""
    au, av = np.abs(fu), np.abs(fv)
    sq = np.maximum(au, av)
    rot = (au + av) / math.sqrt(2)
    star = np.minimum(sq, rot)
    d = np.abs(star - s)
    far = 10.0
    d = np.minimum(d, np.where((au >= s * math.sqrt(2)) & (au >= av), av, far))
    d = np.minimum(d, np.where((av >= s * math.sqrt(2)) & (av > au), au, far))
    d = np.minimum(d, np.where(sq >= s, np.abs(au - av) / math.sqrt(2), far))
    return d, star


def _kufic(r, h, w, ss=4):
    """Pseudo-kufic inscription band (height, mask), row 0 at the top: tall stems with split leaf
    tips, stem pairs braided into a knot or joined by a pointed arch, short stems carrying a lozenge,
    all on a continuous baseline, with small scrolls in the gaps (floriated kufic). Deliberately no
    bowls or hooks, which read as Latin letters; unreadable anyway as props are mirrored."""
    W, H = w * ss, h * ss
    img = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(img)
    sw = max(2, int(H * 0.13))
    base = H * 0.74
    top = H * 0.12
    x = sw * 1.5
    while x < W - sw * 6:
        g = int(r.integers(0, 8))
        if g in (0, 1, 2):
            d.rectangle([x, top + sw, x + sw, base], fill=255)
            d.polygon([(x - sw * 0.7, top), (x + sw * 0.5, top + sw * 1.6), (x + sw * 1.7, top)], fill=255)
            x += sw * 2.6
        elif g == 3:
            # Two stems joined over the top by a pointed arch.
            d.rectangle([x, top + sw * 2.0, x + sw, base], fill=255)
            d.rectangle([x + sw * 3.0, top + sw * 2.0, x + sw * 4.0, base], fill=255)
            d.arc([x, top, x + sw * 4.0, top + sw * 4.4], 180, 360, fill=255, width=sw)
            x += sw * 5.4
        elif g == 4:
            # A short stem with a lozenge on its tip.
            yh = H * 0.45
            d.rectangle([x, yh, x + sw, base], fill=255)
            d.polygon([(x + sw * 0.5, yh - sw * 1.6), (x + sw * 1.5, yh), (x + sw * 0.5, yh + sw * 1.2),
                       (x - sw * 0.5, yh)], fill=255)
            x += sw * 2.8
        elif g == 5:
            d.rectangle([x, top + sw, x + sw, base], fill=255)
            d.rectangle([x + sw * 3.2, top + sw, x + sw * 4.2, base], fill=255)
            d.ellipse([x - sw * 0.3, H * 0.30, x + sw * 4.5, H * 0.62], outline=255, width=sw)
            x += sw * 5.8
        else:
            cx, cy, rr = x + sw * 1.6, H * 0.40, sw * 1.35
            d.arc([cx - rr, cy - rr, cx + rr, cy + rr], 20, 310, fill=255, width=max(1, sw // 2))
            d.ellipse([cx - sw * 0.4, cy - sw * 0.4, cx + sw * 0.4, cy + sw * 0.4], fill=255)
            x += sw * 3.6
    d.rectangle([0, base, W, base + sw], fill=255)
    mask = np.asarray(img.resize((w, h), Image.LANCZOS), float) / 255.0
    return _round(mask > 0.5, max(1.0, sw / ss * 0.5)) * 0.8 + M.blur(mask, 0.7) * 0.2, mask > 0.5


def _sebka(h, w, ox, cw, ch, band, ss=2):
    """Carved sebka net (height, mask): lozenges whose sides are lobed arches bowing in toward
    their own centre, so neighbouring lozenges' arches cross and interlace, with a four-petal
    flower in each lozenge. Sizes in pixels; ox = the x of a lozenge column (symmetry axis)."""
    W, H = w * ss, h * ss
    img = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(img)
    cw, ch, ox = cw * ss, ch * ss, ox * ss
    bw = max(2, int(band * ss))
    ni = int(W / cw) + 3
    nj = int(H / (ch / 2)) + 3
    for j in range(-2, nj):
        for i in range(-ni, ni):
            cx = ox + i * cw + (j % 2) * cw / 2
            cy = H - j * ch / 2
            if cx < -cw or cx > W + cw:
                continue
            verts = [(cx, cy - ch / 2), (cx + cw / 2, cy), (cx, cy + ch / 2), (cx - cw / 2, cy)]
            for k in range(4):
                p0, p1 = np.array(verts[k]), np.array(verts[(k + 1) % 4])
                inward = np.array([cx, cy]) - 0.5 * (p0 + p1)
                inward /= np.linalg.norm(inward)
                L = np.linalg.norm(p1 - p0)
                pts = []
                for t in np.linspace(0, 1, 40):
                    q = p0 + (p1 - p0) * t + inward * L * (0.10 * 4 * t * (1 - t) + 0.035 * abs(math.sin(3 * math.pi * t)))
                    pts.append(tuple(q))
                d.line(pts, fill=255, width=bw, joint="curve")
            pr = min(cw, ch) * 0.08
            for a in range(4):
                px = cx + math.cos(a * math.pi / 2) * pr * 1.3
                py = cy + math.sin(a * math.pi / 2) * pr * 1.3
                d.ellipse([px - pr, py - pr, px + pr, py + pr], fill=230)
            d.ellipse([cx - pr * 0.7, cy - pr * 0.7, cx + pr * 0.7, cy + pr * 0.7], fill=255)
    m = np.asarray(img.resize((w, h), Image.LANCZOS), float) / 255.0
    return _round(m > 0.45, band * 0.5) * (0.6 + 0.4 * m), m > 0.45


def _ataurique(r, h, w, scale):
    """Fine leafy ground of carved stucco (between the bigger motifs): low, soft relief."""
    n = M.fnoise(r, h, w, beta=1.2, kmin=max(1.0, w / scale / 2))
    leaf = M.smooth(0.2, 0.9, n)
    return M.blur(leaf, 0.8) * 0.35


# ------------------------------------------------------------------------------------------------
# Textures
# ------------------------------------------------------------------------------------------------

def _voussoir_index(x, y):
    """Voussoir number (0 = left foot) and the position across it (0..1) of points on the arch."""
    psi = np.arctan2(np.maximum(y - YF, 0.0), x - AX)
    f = np.clip((math.pi - psi) / math.pi, 0, 1 - 1e-9) * NV
    return np.floor(f).astype(int), f - np.floor(f)


def _arch_face(h, w):
    """Both faces of the arch wall (pier face to pier face, springing to ceiling): red and cream
    voussoirs, carved interlace in the spandrels with a star medallion in each corner, the alfiz
    frame, the pseudo-kufic frieze on faded blue. Everything dusty, the red paint flaking."""
    r = M.rng(22811 + 200)
    x, y = _world(h, w, X0, X1, YF, CEIL)
    ppb = w / (X1 - X0)
    dx = x - AX
    adx = np.abs(dx)
    opening = (dx / A) ** 2 + ((y - YC) / B) ** 2 < 1.0
    inside_e = (dx / AE) ** 2 + ((y - YC - DYE) / BE) ** 2 < 1.0
    ring = inside_e & ~opening
    alfiz = (((adx > ALFIZ_IN) & (adx < ALFIZ_OUT)) | ((y > ALFIZ_Y0) & (adx < ALFIZ_OUT))) & (y < ALFIZ_Y1)
    frieze = y >= ALFIZ_Y1

    # Spandrels: carved strapwork of the star net (Nasrid yeseria) over a leafy ground once painted
    # blue, a small rosette in every star; in each top corner a star medallion (an ochre star band
    # and a cream ring on red, a rosette inside).
    cell = 0.19
    su, sv = (x - AX) / cell + 0.5, (y - YF) / cell
    fu, fv = su - np.floor(su) - 0.5, sv - np.floor(sv) - 0.5
    dn, star = _star_net(fu, fv)
    strap_w = 0.06
    strap = np.clip(1.0 - dn / strap_w, 0, 1)
    ros_s = np.hypot(fu, fv) < 0.09 + 0.03 * np.cos(8 * np.arctan2(fv, fu))
    height = np.maximum(np.sqrt(strap), ros_s * 0.7)
    height = np.where(height > 0.05, height, _ataurique(r, h, w, ppb * 0.05))
    ground = M.plaster(r, h, w, ppb, PLASTER * 0.95, cracks=0.2, stains=0)
    paint_n = M.smooth(-0.9, 0.5, M.fnoise(r, h, w, beta=2.2))
    hollow = np.clip(1.0 - height * 2.5, 0, 1)
    rgb = M.lerp(ground, M.lerp(ground, BLUE * 0.75, 0.75), hollow * paint_n * 0.9)
    rgb = M.lerp(rgb, M.lerp(ground, RED, 0.6), hollow * (star < 0.27) * paint_n * 0.85)
    rgb = M.lerp(rgb, M.lerp(ground, OCHRE, 0.45), (height > 0.6) * paint_n * 0.4)
    for sx in (-1, 1):
        u = (x - (AX + sx * 1.07)) / 0.14
        v = (y - 3.52) / 0.14
        rr = np.hypot(u, v)
        au, av = np.abs(u), np.abs(v)
        st = np.minimum(np.maximum(au, av), (au + av) / math.sqrt(2))
        inner = rr < 1.0
        band_s = np.abs(st - 0.6) < 0.09
        ringc = np.abs(rr - 0.92) < 0.08
        ros = rr < 0.24 + 0.07 * np.cos(8 * np.arctan2(v, u))
        med = np.maximum.reduce([band_s * 1.0, ringc * 0.9, ros * 0.8, np.full_like(rr, 0.15)])
        height = np.where(inner, M.blur(med, 0.8), height)
        mc = np.where(band_s[..., None], M.lerp(ground, OCHRE, 0.7), M.lerp(ground, RED * 0.9, 0.65))
        mc = np.where(ringc[..., None], ground * 1.03, mc)
        mc = np.where(ros[..., None], M.lerp(ground, BLUE, 0.5), mc)
        mc = M.lerp(ground, mc, (0.55 + 0.45 * paint_n)[..., None])
        rgb = np.where(inner[..., None], mc, rgb)

    # Voussoirs: radial joints from the springing's mid point, cushioned faces, the red ones of
    # brick in three courses (thin pale mortar lines), the cream ones of stone.
    j, t = _voussoir_index(x, y)
    red = (j % 2) == 0
    rad_px = np.hypot(dx, y - YF) * ppb
    joint_px = np.minimum(t, 1 - t) * (math.pi / NV) * rad_px
    d_in = ndimage.distance_transform_edt(~opening)
    d_out = ndimage.distance_transform_edt(inside_e)
    cush = M.smooth(0.0, 3.0, joint_px) * M.smooth(0.0, 3.5, d_in) * M.smooth(0.0, 2.5, d_out)
    tone = _hash(j, 7)[..., None]
    stone = M.plaster(r, h, w, ppb * 1.5, M.lerp(STONE, M.col("#a8977a"), 0.45), cracks=0.15, stains=0) * (0.9 + 0.12 * tone)
    brick_c = M.lerp(BRICK, M.col("#6e5446"), 0.25)
    brick = M.lerp(brick_c * 0.85, brick_c * 1.02, tone) * (1 + 0.05 * M.fnoise(r, h, w, beta=1.6))[..., None]
    course = (np.abs(t * 3 - np.round(t * 3)) * (math.pi / NV / 3) * rad_px < 0.6) & (t > 0.05) & (t < 0.95)
    brick = np.where(course[..., None], M.lerp(brick, M.col("#a08a70"), 0.5), brick)
    # The red was paint over render: flaked off in a few broad patches, the render showing.
    brick = M.chipped_paint(brick, stone * 0.9, r, amount=0.1, scale=ppb * 0.8, grain=2.8)
    vous = np.where(red[..., None], brick, stone)
    vous = vous * (0.8 + 0.2 * cush)[..., None]
    vous = M.lerp(vous, M.col("#2b1d14"), (joint_px < 0.9) * 0.6)
    rgb = np.where(ring[..., None], vous, rgb)
    height = np.where(ring, 0.9 + 0.1 * cush, height)

    # Alfiz: a cream fillet with a row of pearls between two red lines.
    s_al = np.where(y > ALFIZ_Y0, x, y)
    mid = np.where(y > ALFIZ_Y0, np.abs(y - 0.5 * (ALFIZ_Y0 + ALFIZ_Y1)), np.abs(adx - 0.5 * (ALFIZ_IN + ALFIZ_OUT)))
    pearl = np.hypot((s_al * 30.0) % 1.0 - 0.5, mid * 30.0) < 0.33
    al_rgb = M.lerp(ground, STONE, 0.5)
    al_rgb = np.where((np.abs(mid - 0.022) < 0.006)[..., None], M.lerp(al_rgb, RED, 0.7), al_rgb)
    al_rgb = np.where(pearl[..., None], al_rgb * 1.08, al_rgb)
    rgb = np.where(alfiz[..., None], al_rgb, rgb)
    height = np.where(alfiz, 0.85 + 0.15 * pearl, height)

    # Frieze: the inscription in raised cream on a blue ground gone grey, red rules above and below.
    fy0 = int(round((CEIL - FRIEZE_Y1) / (CEIL - YF) * h))
    fy1 = int(round((CEIL - ALFIZ_Y1) / (CEIL - YF) * h))
    kh, km = _kufic(M.rng(22811 + 201), fy1 - fy0, w)
    fr_h = np.zeros((h, w))
    fr_m = np.zeros((h, w), bool)
    fr_h[fy0:fy1] = kh
    fr_m[fy0:fy1] = km
    blue_g = M.lerp(ground, BLUE * 0.85, 0.6 * (0.7 + 0.3 * paint_n))
    fr_rgb = np.where(fr_m[..., None], M.lerp(ground, OCHRE, 0.25) * 1.05, blue_g)
    rule = (np.abs(y - ALFIZ_Y1) < 0.012) | (np.abs(y - FRIEZE_Y1) < 0.012)
    fr_rgb = np.where(rule[..., None], M.lerp(ground, RED, 0.6), fr_rgb)
    rgb = np.where(frieze[..., None], fr_rgb, rgb)
    height = np.where(frieze, fr_h * 0.8, height)

    rgb = np.where(opening[..., None], STONE * 0.7, rgb)
    rgb = rgb * _shade(M.blur(height, 0.7) * 3.0, 1.0, 0.6)[..., None]
    # Age: dust on everything that faces up, damp runs from the ceiling, soot in the frieze,
    # grime gathered over the imposts.
    up = M.smooth(0.0, 0.25, np.roll(height, 1, axis=0) - height) + 0.3
    rgb = M.dust(rgb, 0.28, r, up=np.clip(up, 0, 1))
    rgb = M.grime_gradient(rgb, r, bottom=0.12, top=0.3, streak=0.8)
    rgb = M.damp_stains(rgb, r, 2, strength=0.25)
    return rgb


def _soffit(h, w):
    """The intrados (u = round the arch foot to foot, v = through the wall): the voussoirs carry
    on underneath, edged with a dark arris, soot thick at the crown."""
    r = M.rng(22811 + 300)
    X, Y = M.grid(h, w)
    f = X * NV
    j = np.floor(f).astype(int)
    t = f - j
    tone = _hash(j, 7)[..., None]
    stone = M.plaster(r, h, w, w / 4.0, M.lerp(STONE, M.col("#a8977a"), 0.45), cracks=0.2, stains=0) * (0.9 + 0.12 * tone)
    brick_c = M.lerp(BRICK, M.col("#6e5446"), 0.3)
    brick = M.lerp(brick_c * 0.85, brick_c * 1.02, tone)
    brick = M.chipped_paint(brick, stone * 0.9, r, amount=0.16, scale=w / 6.0, grain=2.8)
    rgb = np.where(((j % 2) == 0)[..., None], brick, stone)
    rgb = M.lerp(rgb, M.col("#2b1d14"), (np.minimum(t, 1 - t) * w / NV < 0.8) * 0.6)
    edge = np.minimum(Y, 1 - Y) * h
    rgb = rgb * (0.75 + 0.25 * M.smooth(0.0, 2.5, edge))[..., None]
    crown = np.exp(-((X - 0.5) / 0.22) ** 2)
    rgb = M.lerp(rgb, M.col("#1d1510"), crown * 0.35 * (0.6 + 0.4 * (M.fnoise(r, h, w, beta=2.2) * 0.5 + 0.5)))
    return M.dust(rgb, 0.12, r)


def _lattice(h, w):
    """The carved plaster lattice in the arch's head (alpha-cut): the eight-point star net with a
    boss in every star, a solid rim along the intrados and the transom, a few bars broken out."""
    r = M.rng(22811 + 400)
    x, y = _world(h, w, AX - A, AX + A, LAT_Y0, TOP_I)
    ppb = w / (2 * A)
    cell = 0.17
    u = (x - AX) / cell + 0.5
    v = (y - LAT_Y0) / cell
    fu, fv = u - np.floor(u) - 0.5, v - np.floor(v) - 0.5
    dn, star = _star_net(fu, fv)
    bar_w = 0.065
    bars = dn < bar_w
    boss = np.hypot(fu, fv) < 0.08
    inside = (((x - AX) / A) ** 2 + ((y - YC) / B) ** 2) < 1.0
    d_edge = ndimage.distance_transform_edt(inside) / ppb
    rim = (d_edge < 0.035) | (y - LAT_Y0 < 0.03)
    # Broken: a few bars knocked out (never the rim).
    broken = np.zeros((h, w), bool)
    for _ in range(4):
        bx, by = AX + (r.random() - 0.5) * 1.6, LAT_Y0 + 0.1 + r.random() * 0.35
        broken |= np.hypot(x - bx, y - by) < 0.025 + 0.025 * r.random()
    solid = inside & (rim | ((bars | boss) & ~broken))
    height = np.where(rim, 1.0, np.clip(1.0 - dn / bar_w, 0, 1) ** 0.5)
    height = np.where(boss, np.maximum(height, 1.0 - np.hypot(fu, fv) / 0.08), height)
    base = M.plaster(r, h, w, ppb, PLASTER * 0.95, cracks=0.15, stains=0)
    paint = M.smooth(-0.2, 0.9, M.fnoise(r, h, w, beta=2.0))
    rgb = M.lerp(base, OCHRE, (height > 0.7) * paint * 0.45)
    rgb = M.lerp(rgb, RED, boss * 0.4)
    rgb = rgb * _shade(M.blur(height, 0.6) * 2.5, 1.1, 0.5)[..., None]
    rgb = rgb * (0.55 + 0.45 * np.clip(height * 1.3, 0, 1))[..., None]
    rgb = M.dust(rgb, 0.3, r)
    return M.rgba(rgb, solid.astype(float))


def _zellige(r, h, w, x, y, cell):
    """Glazed alicatado field on world coords (x, y; cell in blocks): white straps of the star net
    outlined in dark grout, stars alternately blue and honey with a small white star inside, the
    kite pieces between them green and black in a pinwheel. Hand-cut pieces vary in tone, the glaze
    is chipped at the edges and a few pieces have fallen out, leaving the grey mortar bed."""
    ppb_px = 1.0 / (x[0, 1] - x[0, 0])
    u, v = (x - AX) / cell + 0.5, y / cell
    iu, iv = np.floor(u).astype(int), np.floor(v).astype(int)
    fu, fv = u - iu - 0.5, v - iv - 0.5
    dn, star = _star_net(fu, fv)
    bw = 0.055
    strap = dn < bw
    grout = np.abs(dn - bw) < 0.9 / (ppb_px * cell)
    in_star = (star < 0.27) & ~strap
    small = star < 0.11
    au, av = np.abs(fu), np.abs(fv)
    vert = au > av
    kite_dark = np.where(vert, fv > 0, fu < 0)
    col = np.where(kite_dark[..., None], BLACK, GREEN)
    starc = np.where((((iu + iv) % 2) == 0)[..., None], BLUE * 0.85, HONEY)
    col = np.where(in_star[..., None], starc, col)
    col = np.where((in_star & small)[..., None], GLAZE_WHITE, col)
    col = np.where(strap[..., None], GLAZE_WHITE, col)
    # Pieces: label the glaze between grout lines for per-piece tone, chips and losses.
    lab, n = ndimage.label(~grout)
    pr = M.rng(22919 + 7)
    tone = pr.random(n + 1)
    gone = pr.random(n + 1) < 0.035
    gone[0] = False
    pt = tone[lab][..., None]
    col = col * (0.88 + 0.2 * pt)
    # Glaze: a soft sheen across each hand-laid piece, darker where it pooled at the edges.
    dist = ndimage.distance_transform_edt(~grout)
    col = col * (0.9 + 0.1 * M.smooth(0.0, 3.0, dist))[..., None]
    col = M.lerp(col, np.minimum(col * 1.35, 1.0), M.smooth(0.55, 0.95, M.blur(pt[..., 0] + 0.3 * M.fnoise(pr, h, w, beta=2.0), 1.0)) * 0.25)
    craz = M.craquelure(pr, h, w, cells=int(h * w / 90), width=0.6)
    col = M.lerp(col, col * 0.75, craz * 0.3)
    chip_n = M.fnoise(pr, h, w, beta=1.4)
    chip = (dist < 2.5) & (chip_n > 1.3)
    col = np.where(chip[..., None], BISCUIT * (0.85 + 0.2 * pt), col)
    col = np.where(grout[..., None], M.col("#5a5146"), col)
    bed = gone[lab] & ~grout
    mortar = M.lerp(M.col("#8d8574"), M.col("#6d6558"), M.fnoise(pr, h, w, beta=1.2) * 0.3 + 0.5)[...]
    # The lost piece's hollow: shadowed under its top edge.
    top_sh = M.smooth(0.0, 4.0, ndimage.distance_transform_edt(bed))
    col = np.where(bed[..., None], mortar * (0.6 + 0.4 * top_sh)[..., None], col)
    hgt = np.where(grout | bed, 0.0, 1.0)
    return col, hgt


def _wall(h, w):
    """solid_alhambra, both faces: zellige dado with a saw-tooth border, a glazed listel (the
    moulding is real geometry), the epigraphic band, then a recessed sebka panel of carved stucco in
    a frame of fine interlace. Damp, cracked, dusty, plaster fallen off to the brick in places."""
    r = M.rng(22919 + 200)
    x, y = _world(h, w, -0.5, 2.5, WALL_Y0, WALL_Y1)
    ppb = w / 3.0
    adx = np.abs(x - AX)
    plaster = M.plaster(r, h, w, ppb, PLASTER, cracks=0.3, stains=0)

    # --- dado
    zel, zh = _zellige(r, h, w, x, y, 0.2)
    side = (adx > 1.33) & (y >= 0.2) & (y <= 0.84)
    border = (y > 0.84) | (y < 0.2) | side
    # Saw-tooth bands above and below (black and white triangles, the classic frame of a tiled
    # dado); up the ends next to the columns, a chequer of small black and white squares.
    tooth = 0.06
    s = (x - AX) / tooth
    q = np.where(y > 0.84, (y - 0.84) / (DADO_Y1 - 0.84), (y - WALL_Y0) / (0.2 - WALL_Y0))
    tri = np.abs((s % 1.0) - 0.5) * 2 < q
    bjoint = ((np.abs((s % 1.0) - 0.5) * 2 - q) ** 2 < 0.004) & ~side
    chq = ((np.floor(y / 0.04) + np.floor((adx - 1.33) / 0.04)) % 2) == 0
    bjoint |= side & ((np.abs((y / 0.04) % 1.0 - 0.5) > 0.42) | (np.abs(((adx - 1.33) / 0.04) % 1.0 - 0.5) > 0.42))
    bc = np.where(np.where(side, chq, tri)[..., None], BLACK * 1.1, GLAZE_WHITE * 0.95)
    bc = np.where(bjoint[..., None], M.col("#5a5146"), bc)
    dado = np.where(border[..., None], bc, zel)
    edge_rule = (np.abs(y - 0.84) < 0.006) | (np.abs(y - 0.2) < 0.006) | ((np.abs(adx - 1.33) < 0.006) & (y > 0.2) & (y < 0.84))
    dado = np.where(edge_rule[..., None], M.col("#4a4238"), dado)
    dado_h = np.where(border, 1.0 - bjoint, zh)
    dado = dado * (0.92 + 0.08 * M.relief(M.blur(dado_h, 0.7) * 2.0, 1.0))[..., None]
    # Floor grime and mop line, scuffs.
    dado = M.lerp(dado, M.col("#2a2118"), M.smooth(0.45, 0.12, y) * 0.45 * (0.6 + 0.4 * (M.fnoise(r, h, w, beta=2.0) * 0.5 + 0.5)))
    is_dado = y < DADO_Y1

    # --- inscription band
    iy0 = int(round((WALL_Y1 - INSCR_Y1) / (WALL_Y1 - WALL_Y0) * h))
    iy1 = int(round((WALL_Y1 - INSCR_Y0) / (WALL_Y1 - WALL_Y0) * h))
    kh, km = _kufic(M.rng(22919 + 201), iy1 - iy0, w)
    ins_h = np.zeros((h, w))
    ins_m = np.zeros((h, w), bool)
    ins_h[iy0:iy1] = kh
    ins_m[iy0:iy1] = km
    paint_n = M.smooth(-0.3, 0.8, M.fnoise(r, h, w, beta=2.2))
    ins = np.where(ins_m[..., None], M.lerp(plaster, OCHRE, 0.2) * 1.05, M.lerp(plaster, BLUE * 0.85, 0.55 * (0.6 + 0.4 * paint_n)))
    is_ins = (y >= DADO_Y1) & (y < BEAD_Y1)

    # --- sebka panel and its frame
    sh_, sm = _sebka(h, w, (AX + 0.5) / 3.0 * w, ppb * 0.52, ppb * 0.62, ppb * 0.028)
    in_panel = (x > SEBKA_X0) & (x < SEBKA_X1) & (y > SEBKA_Y0) & (y < SEBKA_Y1)
    frame = ~in_panel & (y >= BEAD_Y1) & (x > SEBKA_X0 - 0.07) & (x < SEBKA_X1 + 0.07) & (y < SEBKA_Y1 + 0.07)
    kh2, km2 = C.knotwork(h, w, cell=ppb * 0.07, line=0.18)
    leaf = _ataurique(r, h, w, ppb * 0.05)
    up_h = np.where(in_panel, np.where(sm, sh_, leaf), np.where(frame, np.where(km2, kh2 * 0.8, 0.1), 0.3))
    up_h = np.where(is_ins, ins_h * 0.8, up_h)
    hollow = np.clip(1.0 - up_h * 2.2, 0, 1)
    carved = M.lerp(plaster, M.lerp(plaster, RED * 1.1, 0.45), hollow * paint_n * in_panel * 0.8)
    carved = M.lerp(carved, M.lerp(plaster, BLUE, 0.4), hollow * paint_n * frame * 0.7)
    carved = np.where(is_ins[..., None], ins, carved)
    carved = carved * _shade(M.blur(up_h, 0.7) * 3.0, 1.0, 0.7)[..., None]
    # Plaster fallen off to the brick core in two places, with a broken, shadowed edge.
    loss = np.zeros((h, w))
    for (lx, ly, lr) in ((2.05, 2.45, 0.16), (-0.1, 1.55, 0.11)):
        warp = M.fnoise(M.rng(22919 + int(lx * 10)), h, w, beta=2.0) * 0.35
        loss = np.maximum(loss, (np.hypot((x - lx) / lr, (y - ly) / lr * 1.3) + warp < 1.0).astype(float))
    loss = loss.astype(bool) & ~is_dado
    brick_u, brick_v = (x + 0.5) / 0.22, y / 0.06
    bjoints = (np.abs(brick_v - np.round(brick_v)) < 0.08) | (np.abs(((brick_u + 0.5 * (np.floor(brick_v) % 2)) % 1.0) - 0.5) > 0.46)
    brick_t = 0.85 + 0.2 * _hash(np.floor(brick_u).astype(int), np.floor(brick_v).astype(int))
    brickc = np.where(bjoints[..., None], M.col("#8b8171"), M.lerp(BRICK, M.col("#7a6a5c"), 0.35) * brick_t[..., None])
    # Scraps of render left stuck on the brick.
    brickc = M.lerp(brickc, plaster * 0.8, M.smooth(0.9, 1.4, M.fnoise(r, h, w, beta=1.8)) * 0.8)
    rim = M.smooth(3.0, 0.0, ndimage.distance_transform_edt(loss))
    carved = np.where(loss[..., None], brickc * 0.75, carved)
    rim_out = M.smooth(3.0, 0.0, ndimage.distance_transform_edt(~loss)) * ~loss
    carved = M.lerp(carved, carved * 0.5, rim_out * 0.6)
    carved = M.lerp(carved, carved * 0.6, rim * loss * 0.5)

    rgb = np.where(is_dado[..., None], dado, carved)
    rgb = M.damp_stains(rgb, r, 3, strength=0.3)
    rgb = M.grime_gradient(rgb, r, bottom=0.08, top=0.25, streak=0.9)
    up = M.smooth(0.0, 0.25, np.roll(up_h, 1, axis=0) - up_h) * ~is_dado + 0.25
    return M.dust(rgb, 0.22, r, up=np.clip(up, 0, 1))


def _listel(h, w):
    """The dado's capping listel: dark green glaze, crazed and chipped to the biscuit."""
    r = M.rng(22919 + 300)
    rgb = M.lerp(GREEN * 0.8, GREEN * 1.1, M.fnoise(r, h, w, beta=2.0) * 0.3 + 0.5)
    seg = (np.mgrid[0:h, 0:w][1] % 24) < 1
    rgb = np.where(seg[..., None], M.col("#4a4238"), rgb)
    rgb = M.chipped_paint(rgb, BISCUIT, r, amount=0.12, scale=8, grain=1.8)
    return M.dust(rgb, 0.25, r)


def _plaster(h, w):
    r = M.rng(22912 + 100)
    return M.dust(M.plaster(r, h, w, 192, PLASTER * 0.92, cracks=0.3, stains=1), 0.2, r)


def _shaft(h, w):
    """Painted marble shaft (one turn across, foot at the bottom): rings of faded blue near the
    top and one at the foot, as on the Lucerne columns; grimy low down, rubbed at hand height."""
    r = M.rng(22913 + 100)
    rgb = M.faux_marble(r, h, w, ppb=w / 0.58)
    X, Y = M.grid(h, w)
    for yc in (0.10, 0.125, 0.15, 0.93):
        rgb = M.lerp(rgb, BLUE, (np.abs(Y - yc) < 0.0055) * 0.85)
    rgb = M.grime_gradient(rgb, r, 0.3, 0.0)
    rgb = M.lerp(rgb, rgb * 0.85, np.exp(-((Y - 0.42) / 0.08) ** 2) * 0.4 * (M.fnoise(r, h, w, beta=2.0) * 0.5 + 0.5))
    return M.dust(rgb, 0.12, r)


def _capital(h, w):
    """Cube capital faces: deep interlace in a square frame, ochre ground in the hollows."""
    r = M.rng(22914 + 100)
    hk, mk = C.knotwork(h, w, cell=w / 3.0, line=0.2)
    X, Y = M.grid(h, w)
    frame = np.maximum(np.abs(X - 0.5), np.abs(Y - 0.5)) > 0.42
    height = np.where(frame, 0.9, hk * 0.8)
    rgb = M.plaster(r, h, w, 400, PLASTER, cracks=0.15, stains=0)
    rgb = np.where((~mk & ~frame)[..., None], M.lerp(rgb, OCHRE, 0.6), rgb)
    rgb = rgb * _shade(M.blur(height, 0.8) * 4.0, 1.1, 0.8)[..., None]
    return M.dust(rgb, 0.3, r)


def _bell(h, w):
    """The capital's round lower part: a ring of carved palmettes, red in the hollows."""
    r = M.rng(22914 + 200)
    X, Y = M.grid(h, w)
    n = 12
    fu = (X * n) % 1.0 - 0.5
    leaf = (np.abs(fu) < 0.42 * (1 - Y) ** 0.6) & (Y > 0.08)
    vein = np.abs(fu) < 0.05
    height = _round(leaf, 3.0) * 0.8 - vein * 0.25
    rgb = M.plaster(r, h, w, 300, PLASTER, cracks=0.1, stains=0)
    rgb = np.where((~leaf)[..., None], M.lerp(rgb, RED, 0.45), rgb)
    rgb = rgb * _shade(M.blur(height, 0.6) * 3.0, 1.0, 0.7)[..., None]
    return M.dust(rgb, 0.3, r)


def _pier(h, w):
    """One face of the square pier over a column (where no arch hides it): a carved interlace
    band in a plain margin, the ground once blue, soot and damp running down from the ceiling."""
    r = M.rng(22912 + 200)
    X, Y = M.grid(h, w)
    hk, mk = C.knotwork(h, w, cell=w / 2.0, line=0.17)
    band = np.abs(X - 0.5) < 0.36
    height = np.where(band, np.where(mk, hk * 0.8, 0.1), 0.9)
    height = np.where(np.abs(np.abs(X - 0.5) - 0.38) < 0.03, 0.5, height)
    base = M.plaster(r, h, w, w / 0.22, PLASTER * 0.95, cracks=0.25, stains=1)
    rgb = M.lerp(base, M.lerp(base, BLUE * 0.8, 0.6), (band & ~mk) * M.smooth(-0.6, 0.6, M.fnoise(r, h, w, beta=2.0)))
    rgb = rgb * _shade(M.blur(height, 0.6) * 3.0, 1.0, 0.7)[..., None]
    rgb = M.grime_gradient(rgb, r, bottom=0.1, top=0.35, streak=1.0)
    return M.dust(rgb, 0.2, r)


def _gilt(h, w):
    r = M.rng(22915 + 100)
    return M.gilt(r, h, w, 192, wear=0.35, flake=0.18)


def _black(h, w):
    r = M.rng(22916 + 100)
    rgb = M.lerp(M.col("#191614"), M.col("#27231f"), M.fnoise(r, h, w, beta=2.0) * 0.5 + 0.5)
    sc = M.scratches(r, h, w, 20, (0.02, 0.1))
    return M.dust(M.lerp(rgb, M.col("#4a4540"), sc * 0.4), 0.25, r)


def _floor(h, w):
    """Terracotta octagons with small glazed insets (olambrillas) at their corners, six to a cell
    side: tiles of uneven firing, worn pale and smooth down the middle, grout black with dirt,
    insets' blue-and-white glaze worn through; the mirror base lines dark along the cell edges."""
    r = M.rng(22917 + 200)
    Xn, Yn = M.grid(h, w)
    n = 6
    u, v = Xn * n, Yn * n
    iu, iv = np.floor(u).astype(int), np.floor(v).astype(int)
    fu, fv = u - iu - 0.5, v - iv - 0.5
    au, av = np.abs(fu), np.abs(fv)
    q = 0.30
    diag = au + av
    ins = diag > 1.0 - q
    px = 1.0 / (w / n)                     # one pixel in tile units
    grout = (np.abs(np.maximum(au, av) - 0.5) < 1.2 * px) & ~ins | (np.abs(diag - (1.0 - q)) < 1.6 * px)
    tone = _hash(iu, iv, 3)[..., None]
    fire = M.fnoise(r, h, w, beta=2.0) * 0.5 + 0.5
    terra = M.lerp(M.col("#8a4a30"), M.col("#b36d46"), tone * 0.7 + 0.3 * fire[..., None])
    # Burnt spots and the pale bloom of salts.
    terra = M.lerp(terra, M.col("#5a2e1e"), M.smooth(0.7, 0.95, M.fnoise(r, h, w, beta=1.6) * 0.3 + 0.5)[..., None] * 0.5)
    terra = M.lerp(terra, M.col("#c9a98c"), M.smooth(0.65, 0.9, M.fnoise(r, h, w, beta=2.4) * 0.3 + 0.5)[..., None] * 0.35)
    # The insets: a corner square of each of four tiles; its own coordinates about the corner.
    cu, cv = np.round(u), np.round(v)
    du, dv = u - cu, v - cv
    rr = (np.abs(du) + np.abs(dv)) / q
    ang = np.arctan2(dv, du)
    flower = rr < 0.45 + 0.18 * np.cos(4 * ang)
    dot = rr < 0.16
    glaze = np.where(flower[..., None], BLUE * 0.9, GLAZE_WHITE)
    glaze = np.where(dot[..., None], HONEY, glaze)
    glaze = np.where(((np.abs(rr - 0.8) < 0.05))[..., None], BLUE * 0.8, glaze)
    gtone = _hash(cu.astype(int), cv.astype(int), 5)[..., None]
    worn = M.smooth(0.3, 0.9, M.fnoise(r, h, w, beta=1.8) * 0.3 + 0.5 + gtone[..., 0] * 0.3)
    glaze = M.lerp(glaze, BISCUIT * 0.9, worn[..., None] * 0.6)
    missing = (gtone[..., 0] > 0.93)
    glaze = np.where(missing[..., None], M.col("#5e574c") * (0.8 + 0.2 * fire[..., None]), glaze)
    rgb = np.where(ins[..., None], glaze, terra)
    # Tile edges slightly lower (hand-made, worn): darker toward the grout.
    d_t = np.minimum(0.5 - np.maximum(au, av), (1.0 - q - diag) / math.sqrt(2)) / px
    rgb = np.where(ins[..., None], rgb, rgb * (0.85 + 0.15 * M.smooth(0.0, 4.0, d_t))[..., None])
    rgb = np.where(grout[..., None], M.col("#2d2620"), rgb)
    cr = M.craquelure(r, h, w, cells=14, width=0.6)
    rgb = M.lerp(rgb, M.col("#2a1d15"), cr * 0.45)
    edge = np.clip(1 - np.minimum(np.minimum(Xn, 1 - Xn), np.minimum(Yn, 1 - Yn)) / 0.05, 0, 1)
    rgb = M.lerp(rgb, M.col("#231a14"), edge ** 2 * 0.55)
    track = np.exp(-(((Xn - 0.5) / 0.3) ** 2 + ((Yn - 0.5) / 0.3) ** 2))
    rgb = M.lerp(rgb, rgb * 1.12 + 0.02, track * 0.35)
    rgb = M.lerp(rgb, M.col("#3a3026"), (1 - track) * 0.15)
    return M.dust(rgb, 0.22, r)


def _ceiling(h, w):
    """Painted pine boards between the beams (u = x, v = z over the cell): each of the nine
    coffers carries a strapwork eight-point star, cream straps on red and green grounds; the middle
    one (inside the octagonal frame) a sixteen-point star in faded gold and blue. Water-stained,
    the paint flaked to the bare wood, soot over the bulbs."""
    r = M.rng(22918 + 200)
    x, _ = _world(h, w, CELL0, CELL1, 0.0, 1.0)
    X, Y = M.grid(h, w)
    z = CELL0 + Y * (CELL1 - CELL0)
    ppb = w / 3.0
    wood = M.wood(r, h, w, ppb, palette=(WOOD, M.col("#6b4028"), M.col("#2a170d")), grain="h", rings=4.0, varnish=0.2)
    # Boards along x, 0.15 wide.
    board = np.abs((z / 0.15) % 1.0 - 0.5) > 0.47
    wood = np.where(board[..., None], wood * 0.55, wood)
    cu = (x - 1.5) % 1.0 - 0.5
    cv = (z - 1.5) % 1.0 - 0.5
    ci = np.floor(x - 0.5).astype(int)
    cj = np.floor(z - 0.5).astype(int)
    dn, star = _star_net(cu * 0.82, cv * 0.82)
    strap = dn < 0.035
    field = np.where((((ci + cj) % 2) == 0)[..., None], M.lerp(RED, WOOD, 0.4), M.lerp(GREEN, WOOD, 0.45))
    paint = np.where(strap[..., None], GLAZE_WHITE * 0.8, np.where((star < 0.27)[..., None], M.lerp(OCHRE, WOOD, 0.4), field))
    # Middle coffer: a sixteen-point star in a ring.
    dx, dz = x - 2.0, z - 2.0
    rr = np.hypot(dx, dz)
    ang = np.arctan2(dz, dx)
    mid = rr < 0.36
    st16 = rr < 0.22 + 0.07 * np.abs(np.cos(ang * 8))
    paint = np.where(mid[..., None], np.where(st16[..., None], M.lerp(OCHRE * 1.1, WOOD, 0.2), M.lerp(BLUE, WOOD, 0.35)), paint)
    paint = np.where((mid & (np.abs(rr - 0.3) < 0.012))[..., None], GLAZE_WHITE * 0.85, paint)
    rgb = M.chipped_paint(paint, wood, r, amount=0.25, scale=ppb * 0.7, grain=2.7)
    rgb = M.lerp(rgb, M.col("#c9bda2"), 0.08)
    rgb = M.damp_stains(rgb, r, 3, tone=(0.25, 0.18, 0.11), strength=0.35)
    for (bx, bz) in ((1.5, 1.5), (2.5, 1.5), (1.5, 2.5), (2.5, 2.5)):
        rgb = M.lerp(rgb, M.col("#120d09"), np.exp(-(((x - bx) ** 2 + (z - bz) ** 2) / 0.09)) * 0.35)
    return M.dust(rgb, 0.12, r)


def _beam(h, w):
    """Painted beam (3 blocks along u, 0.18 across v): dark red ground, cream-edged lozenge chain
    down the middle, black and ochre borders, flaking to the wood."""
    r = M.rng(22918 + 300)
    X, Y = M.grid(h, w)
    ppb = w / 3.0
    wood = M.wood(r, h, w, ppb, palette=(WOOD, M.col("#6b4028"), M.col("#2a170d")), grain="h", rings=3.0, varnish=0.2)
    s = X * 3.0 / 0.12
    fu = s % 1.0 - 0.5
    fv = (Y - 0.5) * 0.18 / 0.12
    loz = np.abs(fu) + np.abs(fv) * 1.6
    paint = np.where((loz < 0.42)[..., None], M.lerp(OCHRE, WOOD, 0.3), M.lerp(RED, WOOD, 0.35))
    paint = np.where((np.abs(loz - 0.42) < 0.05)[..., None], GLAZE_WHITE * 0.85, paint)
    bord = np.abs(Y - 0.5) > 0.36
    paint = np.where(bord[..., None], M.lerp(BLACK, WOOD, 0.3), paint)
    paint = np.where((np.abs(np.abs(Y - 0.5) - 0.36) < 0.03)[..., None], M.lerp(OCHRE, WOOD, 0.2), paint)
    rgb = M.chipped_paint(paint, wood, r, amount=0.25, scale=ppb * 0.5, grain=2.6)
    return M.dust(rgb, 0.15, r)


def regions(S):
    S.region("al_arch", SHEET, 448, 288, _arch_face)
    S.region("al_soffit", SHEET, 512, 32, _soffit)
    S.region("al_lattice", SHEET, 384, 100, _lattice)
    S.region("al_wall", SHEET, 432, 400, _wall)
    S.region("al_listel", SHEET, 192, 16, _listel)
    S.region("al_plaster", SHEET, 192, 192, _plaster)
    S.region("al_pier", SHEET, 48, 256, _pier)
    S.region("al_shaft", SHEET, 128, 320, _shaft)
    S.region("al_capital", SHEET, 112, 80, _capital)
    S.region("al_bell", SHEET, 192, 40, _bell)
    S.region("al_gilt", SHEET, 256, 64, _gilt)
    S.region("al_black", SHEET, 128, 64, _black)
    S.region("al_floor", SHEET, 512, 512, _floor, bits=5)
    S.region("al_ceiling", SHEET, 384, 384, _ceiling, bits=5)
    S.region("al_beam", SHEET, 512, 32, _beam)


# ------------------------------------------------------------------------------------------------
# Meshes
# ------------------------------------------------------------------------------------------------

def column() -> G.Mesh:
    m = G.Mesh("column_alhambra", pivot=(0.5, 0.5))
    q = math.pi / 4
    # Black lacquered plinth, gilt torus base, marble shaft, gilt astragal rings, the bell of the
    # capital carved with palmettes, the cube carved with interlace, a gilt abacus, the impost the
    # arches spring from and the square pier above it to the ceiling.
    G.lathe(m, [(0.2, 0.0), (0.2, 0.085), (0.185, 0.105), (0.15, 0.12)], 4, "al_black", flat=True, phase=q,
            v0=0.9, v1=0.1, u_turns=0.5)
    G.lathe(m, [(0.15, 0.12), (0.153, 0.145), (0.124, 0.172), (0.13, 0.2), (0.094, 0.235)], 12, "al_gilt",
            v0=0.9, v1=0.1, u_turns=0.9)
    G.lathe(m, [(0.094, 0.235), (0.09, 1.0), (0.083, SHAFT_Y1)], 12, "al_shaft", v0=1.0, v1=0.0)
    G.lathe(m, [(0.083, SHAFT_Y1), (0.1, 1.775), (0.088, 1.79), (0.1, 1.805), (0.086, 1.82)], 12, "al_gilt",
            v0=0.8, v1=0.2)
    G.lathe(m, [(0.086, 1.82), (0.095, 1.88), (0.12, CUBE_Y0)], 12, "al_bell", v0=1.0, v1=0.0)
    G.lathe(m, [(0.12, CUBE_Y0), (CUBE_R, CUBE_Y0 + 0.005)], 4, "al_plaster", flat=True, phase=q, v0=0.5, v1=0.45)
    for k in range(4):
        face = G.Mesh("f")
        G.card(face, (-CUBE_R, CUBE_Y0, CUBE_R), (1, 0, 0), (0, 1, 0), 2 * CUBE_R, CUBE_Y1 - CUBE_Y0, "al_capital",
               normal=np.array([0.0, 0.0, 1.0]))
        m.add(face, G.rot_y(k * math.pi / 2), (0.5, 0.0, 0.5))
    G.lathe(m, [(CUBE_R, CUBE_Y1), (0.152, CUBE_Y1 + 0.012), (0.152, CUBE_Y1 + 0.03)], 4, "al_gilt", flat=True, phase=q,
            v0=0.9, v1=0.1, u_turns=0.7)
    G.lathe(m, [(0.152, CUBE_Y1 + 0.03), (0.165, CUBE_Y1 + 0.045), (0.165, YF)], 4, "al_plaster", flat=True, phase=q,
            v0=0.9, v1=0.7)
    G.lathe(m, [(PIER, YF), (PIER, CEIL)], 4, "al_pier", flat=True, phase=q, v0=1.0, v1=0.0, u_turns=4.0)
    m.contact_ao(0.0, CEIL, reach=0.35, strength=0.5)
    return m


def _arch_samples():
    """Rays from O = (AX, YF) foot to foot; each gives an intrados point, an extrados point and a
    point on the wall's outline (pier faces and ceiling). Two rays aim exactly at the top corners
    so every strip quad between neighbouring rays is flat and convex."""
    corner = math.atan2(CEIL - YF, X0 - AX)
    psis = sorted(set(np.round(np.linspace(math.pi, 0.0, N_ARC + 1), 9)) | {round(corner, 9), round(math.pi - corner, 9)},
                  reverse=True)
    out = []
    for psi in psis:
        d = np.array([math.cos(psi), math.sin(psi)])

        def hit(a, b, cy):
            # O + s d on ((x - AX)/a)^2 + ((y - cy)/b)^2 = 1; O is inside, take the positive root.
            oy = YF - cy
            qa = (d[0] / a) ** 2 + (d[1] / b) ** 2
            qb = 2 * oy * d[1] / b ** 2
            qc = (oy / b) ** 2 - 1
            s = (-qb + math.sqrt(qb * qb - 4 * qa * qc)) / (2 * qa)
            return np.array([AX, YF]) + s * d

        p = hit(A, B, YC)
        e = hit(AE, BE, YC + DYE)
        ss = []
        if abs(d[0]) > 1e-9:
            ss.append(((X0 if d[0] < 0 else X1) - AX) / d[0])
        if d[1] > 1e-9:
            ss.append((CEIL - YF) / d[1])
        o = np.array([AX, YF]) + min(ss) * d
        out.append((psi, p, e, o))
    return out


def _face_uv(x, y):
    return ((x - X0) / (X1 - X0), (CEIL - y) / (CEIL - YF))


def _fquad(m, pts2, z, s, region="al_arch"):
    """A quad on an arch face at depth z, facing s (-1 = the -z side), uv from position."""
    p = [(px, py, z) for px, py in pts2]
    uv = [_face_uv(px, py) for px, py in pts2]
    m.quad(p, uv, region, (0, 0, s), flip=s > 0)


def _corbel_outline():
    """Side view (x, y) of the left foot's rolled corbel (Cordoba's 'modillones de rollo'),
    clockwise from the pier: flat on top under the springer, a short nose at the tip, then two
    rolls stepping down toward the pier."""
    foot = AX - A * math.sqrt(1 - K * K)
    xa, xb = X0 - 0.02, foot + 0.045
    xm = xb - (xb - xa) * 0.45
    pts = [(xa, YF), (xb, YF), (xb, YF - 0.025)]
    for k, (xs, xe, d, bulge) in enumerate(((xb, xm, 0.025, 0.035), (xm, xa, 0.055, 0.05))):
        if k:
            pts.append((xs, YF - d))
        for t in np.linspace(0, math.pi, 5)[1:-1]:
            pts.append((0.5 * (xs + xe) + 0.5 * (xs - xe) * math.cos(t), YF - d - bulge * math.sin(t)))
        pts.append((xe, YF - d))
    return pts


def _corbels(m):
    """The two corbels the arch's feet rest on, as deep as the voussoirs (front and back faces
    flush with them), cantilevered from the pier over the column's impost."""
    zf, zb = PLANE - PIER - RING_PROUD, PLANE + PIER + RING_PROUD
    base = _corbel_outline()
    for side in (-1, 1):
        pts = base if side < 0 else [(2 * AX - px, py) for px, py in base]
        uv = [((px - X0) / 0.6, (YF - py) / 0.6) for px, py in pts]
        for z, s in ((zf, -1.0), (zb, 1.0)):
            G.polygon(m, [(px, py, z) for px, py in pts], uv, "al_plaster", (0.0, 0.0, s))
        # The outline strip round the nose and the rolls (the top and the pier end are hidden).
        acc = 0.0
        for i in range(1, len(pts) - 1):
            (x0_, y0_), (x1_, y1_) = pts[i], pts[i + 1]
            dxe, dye = x1_ - x0_, y1_ - y0_
            L = math.hypot(dxe, dye)
            n = np.array([-dye, dxe, 0.0]) / L * (-side)
            m.quad([(x0_, y0_, zf), (x1_, y1_, zf), (x1_, y1_, zb), (x0_, y0_, zb)],
                   [(acc / 0.6, 0.0), ((acc + L) / 0.6, 0.0), ((acc + L) / 0.6, 0.4), (acc / 0.6, 0.4)],
                   "al_plaster", n, shade=0.9)
            acc += L


def _arch(m):
    sm = _arch_samples()
    for s in (-1, 1):
        z_sp = PLANE + s * PIER
        z_ring = z_sp + s * RING_PROUD
        for (psi0, p0, e0, o0), (psi1, p1, e1, o1) in zip(sm, sm[1:]):
            _fquad(m, [e0, e1, o1, o0], z_sp, s)
            _fquad(m, [p0, p1, e1, e0], z_ring, s)
            # The voussoirs' outer edge, standing proud of the spandrel.
            t = e1 - e0
            nrm = np.array([t[1], -t[0], 0.0])
            nrm = nrm / np.linalg.norm(nrm)
            if np.dot(nrm[:2], 0.5 * (e0 + e1) - np.array([AX, YF])) < 0:
                nrm = -nrm
            m.quad([(e0[0], e0[1], z_ring), (e1[0], e1[1], z_ring), (e1[0], e1[1], z_sp), (e0[0], e0[1], z_sp)],
                   [(0.0, 0.0), (0.2, 0.0), (0.2, 0.02), (0.0, 0.02)], "al_plaster", nrm, shade=0.8)
    # Intrados soffit through the wall's whole depth, normals toward the opening.
    zf, zb = PLANE - PIER - RING_PROUD, PLANE + PIER + RING_PROUD
    for (psi0, p0, e0, o0), (psi1, p1, e1, o1) in zip(sm, sm[1:]):
        def inward(p):
            g = np.array([(p[0] - AX) / A ** 2, (p[1] - YC) / B ** 2])
            g = -g / np.linalg.norm(g)
            return (g[0], g[1], 0.0)
        u0, u1 = (math.pi - psi0) / math.pi, (math.pi - psi1) / math.pi
        n0, n1 = inward(p0), inward(p1)
        m.quad([(p0[0], p0[1], zf), (p1[0], p1[1], zf), (p1[0], p1[1], zb), (p0[0], p0[1], zb)],
               [(u0, 0.0), (u1, 0.0), (u1, 1.0), (u0, 1.0)], "al_soffit", [n0, n1, n1, n0], shade=0.85)
    _corbels(m)
    # The alfiz: a raised frame up both sides and over the top.
    for s in (-1, 1):
        z0, z1 = PLANE + s * PIER, PLANE + s * (PIER + ALFIZ_PROUD)
        zlo, zhi = min(z0, z1), max(z0, z1)
        bars = [(AX - ALFIZ_OUT, AX - ALFIZ_IN, YF, ALFIZ_Y0), (AX + ALFIZ_IN, AX + ALFIZ_OUT, YF, ALFIZ_Y0),
                (AX - ALFIZ_OUT, AX + ALFIZ_OUT, ALFIZ_Y0, ALFIZ_Y1)]
        for k, (xa, xb, ya, yb) in enumerate(bars):
            _fquad(m, [(xa, ya), (xb, ya), (xb, yb), (xa, yb)], z1, s)
            sides = "y" if k == 2 else ("X" if k == 0 else "x")
            G.box(m, (xa, ya, zlo), (xb, yb, zhi), "al_plaster", rsize=(0.6, 0.6), faces=sides, shade=0.85)
        # Small cornice under the ceiling.
        zz = PLANE + s * PIER
        G.rail(m, [(zz, FRIEZE_Y1), (zz + s * 0.025, FRIEZE_Y1 + 0.012), (zz + s * 0.04, FRIEZE_Y1 + 0.035),
                   (zz + s * 0.04, CEIL)], X0, X1, "al_beam", rsize=(3.0, 0.18))
    # Transom over the pane's top edge (a gilt bar) and the lattice set back in the arch's head.
    G.box(m, (AX - A, PANE_Y1 - 0.025, PLANE - 0.05), (AX + A, LAT_Y0, PLANE + 0.05), "al_gilt", rsize=(1.0, 0.25),
          faces="yzZ")
    for s in (-1, 1):
        z = PLANE + s * 0.003
        G.card(m, (AX - A, LAT_Y0, z), (1, 0, 0), (0, 1, 0), 2 * A, TOP_I - LAT_Y0, "al_lattice",
               normal=np.array([0.0, 0.0, float(s)]), shade=0.9)


def _skirting(m):
    z = PLANE
    # Black lacquer base board with a gilt top bead, both faces.
    for s in (1, -1):
        G.rail(m, [(z + s * 0.05, 0.0), (z + s * 0.05, 0.09), (z + s * 0.03, 0.11), (z + s * 0.01, 0.115)],
               -0.5, 2.5, "al_black", rsize=(3.0, 1.0))
        G.rail(m, [(z + s * (0.03 + 0.009 * math.sin(t)), 0.115 - 0.009 * math.cos(t)) for t in np.linspace(0, math.pi, 5)],
               -0.5, 2.5, "al_gilt", rsize=(3.0, 0.25))


def panel() -> G.Mesh:
    m = G.Mesh("panel_alhambra", pivot=(1.0, 0.5))
    _skirting(m)
    _arch(m)
    m.contact_ao(0.0, CEIL, reach=0.3, strength=0.45)
    return m


def opening() -> G.Mesh:
    m = G.Mesh("opening_alhambra", pivot=(1.0, 0.5))
    _arch(m)
    m.contact_ao(0.0, CEIL, reach=0.3, strength=0.45)
    return m


def _wall_uv(x, y):
    return ((x + 0.5) / 3.0, (WALL_Y1 - y) / (WALL_Y1 - WALL_Y0))


def _wquad(m, x0, y0, x1, y1, z, s, region="al_wall"):
    pts = [(x0, y0), (x1, y0), (x1, y1), (x0, y1)]
    m.quad([(px, py, z) for px, py in pts], [_wall_uv(px, py) for px, py in pts], region, (0, 0, s), flip=s > 0)


def solid() -> G.Mesh:
    """The plain wall between two columns (drawn with the panel's arch and skirting): tile dado,
    glazed listel, epigraphic band, a bead, then the carved sebka panel recessed in its frame."""
    m = G.Mesh("solid_alhambra", pivot=(1.0, 0.5))
    xa, xb = -0.5, 2.5
    for s in (-1, 1):
        z = PLANE + s * WALL_Z
        _wquad(m, xa, WALL_Y0, xb, DADO_Y1, z, s)
        # Glazed listel (a rounded capping tile) over the dado.
        G.rail(m, [(z, DADO_Y1), (z + s * 0.022, DADO_Y1 + 0.006), (z + s * 0.03, DADO_Y1 + 0.025),
                   (z + s * 0.022, DADO_Y1 + 0.045), (z, INSCR_Y0)], xa, xb, "al_listel", rsize=(1.0, 0.06))
        _wquad(m, xa, INSCR_Y0, xb, INSCR_Y1, z, s)
        # Half-round bead under the carved panel.
        G.rail(m, [(z + s * 0.016 * math.sin(t), INSCR_Y1 + 0.02 - 0.02 * math.cos(t)) for t in np.linspace(0, math.pi, 4)],
               xa, xb, "al_gilt", rsize=(3.0, 0.25))
        # Frame round the recessed panel, the panel and the recess's four returns.
        _wquad(m, xa, BEAD_Y1, xb, SEBKA_Y0, z, s)
        _wquad(m, xa, SEBKA_Y1, xb, WALL_Y1, z, s)
        _wquad(m, xa, SEBKA_Y0, SEBKA_X0, SEBKA_Y1, z, s)
        _wquad(m, SEBKA_X1, SEBKA_Y0, xb, SEBKA_Y1, z, s)
        zr = z - s * SEBKA_DEPTH
        _wquad(m, SEBKA_X0, SEBKA_Y0, SEBKA_X1, SEBKA_Y1, zr, s)
        zlo, zhi = min(z, zr), max(z, zr)
        G.box(m, (SEBKA_X0 - 0.01, SEBKA_Y0 - 0.01, zlo), (SEBKA_X0, SEBKA_Y1 + 0.01, zhi), "al_plaster", rsize=(0.5, 0.5),
              faces="X", shade=0.7)
        G.box(m, (SEBKA_X1, SEBKA_Y0 - 0.01, zlo), (SEBKA_X1 + 0.01, SEBKA_Y1 + 0.01, zhi), "al_plaster", rsize=(0.5, 0.5),
              faces="x", shade=0.7)
        G.box(m, (SEBKA_X0, SEBKA_Y0 - 0.01, zlo), (SEBKA_X1, SEBKA_Y0, zhi), "al_plaster", rsize=(0.5, 0.5),
              faces="Y", shade=0.95)
        G.box(m, (SEBKA_X0, SEBKA_Y1, zlo), (SEBKA_X1, SEBKA_Y1 + 0.01, zhi), "al_plaster", rsize=(0.5, 0.5),
              faces="y", shade=0.6)
    m.contact_ao(0.0, None, reach=0.3, strength=0.35)
    return m


def floor() -> G.Mesh:
    m = G.Mesh("floor_alhambra", pivot=(2.0, 2.0))
    y = 0.004
    m.quad([(CELL0, y, CELL1), (CELL1, y, CELL1), (CELL1, y, CELL0), (CELL0, y, CELL0)], [(0, 1), (1, 1), (1, 0), (0, 0)],
           "al_floor", (0, 1, 0))
    return m


def ceiling() -> G.Mesh:
    """Artesonado: painted boards, two beams each way dividing the bay into nine coffers (the
    bulbs hang from the crossings), an octagonal frame round the middle coffer and a gilt
    muqarnas pendant at its centre."""
    m = G.Mesh("ceiling_alhambra", pivot=(2.0, 2.0))
    y = CEIL - 0.004
    m.quad([(CELL0, y, CELL0), (CELL1, y, CELL0), (CELL1, y, CELL1), (CELL0, y, CELL1)], [(0, 0), (1, 0), (1, 1), (0, 1)],
           "al_ceiling", (0, -1, 0))
    bw = 0.045
    for c in (1.5, 2.5):
        # Beams along x are deeper than those along z, so their soffits never share a plane.
        G.box(m, (CELL0, CEIL - 0.1, c - bw), (CELL1, CEIL, c + bw), "al_beam", rsize=(3.0, 0.18), faces="yzZ", shade=0.9)
        G.box(m, (c - bw, CEIL - 0.075, CELL0), (c + bw, CEIL, CELL1), "al_beam", rsize=(3.0, 0.18), faces="xX", shade=0.9)
        G.box(m, (c - bw, CEIL - 0.075, CELL0), (c + bw, CEIL, CELL1), "al_beam", rsize=(0.18, 3.0), faces="y",
              shade=0.9, uv_rot=True)
    centre = (2.0, 0.0, 2.0)
    G.lathe(m, [(0.36, CEIL), (0.36, CEIL - 0.06), (0.44, CEIL - 0.06), (0.44, CEIL)], 8, "al_beam", centre=centre,
            flat=True, phase=math.pi / 8, v0=0.2, v1=0.8, u_turns=2.0)
    G.lathe(m, [(0.0, CEIL - 0.25), (0.035, CEIL - 0.21), (0.08, CEIL - 0.14), (0.13, CEIL - 0.075), (0.13, CEIL)], 8,
            "al_gilt", centre=centre, flat=True, phase=math.pi / 8, v0=0.9, v1=0.1, u_turns=1.0)
    m.contact_ao(0.0, CEIL, reach=0.25, strength=0.4)
    return m


def meshes():
    return [column(), panel(), opening(), solid(), floor(), ceiling()]
