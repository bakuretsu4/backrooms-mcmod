"""The rooms that are not maze: the foyer, the final chamber, the exit hallway and the backstage
service passages. Their floors and ceilings (one 3 x 3 cell per prop, as in the maze) and their
walls (WALL: one block of wall face, the -z face of the wall block at rot 0, three variants each
so a run of wall doesn't repeat every block).

Texture budget (the halls sheet ships <= 1.7 MB): floors and ceilings are painted as small tiles
and laid MIRRORED across the cell (a mirrored copy meets its neighbour seamlessly, so no tile edge
shows; the patterns are sized to repeat exactly within a tile so their symmetry lines fall on the
tile edges). Floors are 2 x 2 quads of a 1.5-block tile (budget 4 quads), ceilings 3 x 3 quads of
a 1-block tile (budget 80). The walls carry the storytelling, one block per variant."""
from __future__ import annotations

import math

import numpy as np

from . import geo as G
from . import mat as M
from . import paper as P
from .dims import CEIL, CELL0, CELL1

SHEET = "halls"
ZONES = ("foyer", "chamber", "exithall", "backstage")
WALL_PPB = 120  # wall texture pixels per block


# ------------------------------------------------------------------------------------------------
# Floors (tiles 1.5 blocks square unless noted)
# ------------------------------------------------------------------------------------------------

def _floor_foyer(h, w):
    """Quarry tiles laid on the diagonal, faded terracotta and cream; cracked, one gone to the
    screed, grime packed in the joints, worn paler in drifting patches. Six diagonal tiles across
    the 1.5-block tile, so the mirrored copies continue the pattern exactly."""
    r = M.rng(22700)
    ppb = w / 1.5
    Y, X = np.mgrid[0:h, 0:w].astype(float) + 0.5
    d = w / 4.0
    u, v = (X + Y) / d, (X - Y) / d
    iu, iv = np.floor(u), np.floor(v)
    fu, fv = u - iu, v - iv
    checker = ((iu + iv) % 2) == 0
    soft = M.blur(M.fnoise(r, h, w, beta=2.8), 1.5) * 0.5 + 0.5
    terr = M.lerp(M.col("#753828"), M.col("#87452d"), soft)
    cream = M.lerp(M.col("#a8977a"), M.col("#b9a98a"), soft)
    rgb = np.where(checker[..., None], terr, cream)
    tid = ((iu.astype(np.int64) * 73856093) ^ (iv.astype(np.int64) * 19349663)) % 997 / 997.0
    rgb = rgb * (0.9 + 0.16 * tid)[..., None]
    jd = np.minimum(np.minimum(fu, 1 - fu), np.minimum(fv, 1 - fv)) * d / math.sqrt(2)
    joint = M.smooth(2.2, 0.8, jd)
    rgb = M.lerp(rgb, M.col("#2c2620"), joint)
    missing = (tid > 0.97) & (jd > 1.5)
    rgb = np.where(missing[..., None], M.lerp(M.col("#58534a"), M.col("#6c665b"), soft[..., None]), rgb)
    cracks = M.craquelure(r, h, w, cells=10, width=0.7)
    rgb = M.lerp(rgb, M.col("#221d18"), cracks * 0.5)
    wear = M.smooth(0.45, 0.8, M.blur(M.fnoise(r, h, w, beta=3.2), 3.0) * 0.5 + 0.5)
    rgb = M.lerp(rgb, rgb * 1.15 + 0.03, wear * 0.5)
    rgb = M.dust(rgb, 0.15, r)
    sc = M.blur(M.scratches(r, h, w, 30, (0.02, 0.08), 1.0), 0.6)
    return M.lerp(rgb, rgb * 0.7, sc * 0.4)


def _floor_chamber(h, w):
    """Herringbone parquet (one 3 x 3 tile: herringbone does not mirror): old wax gone grey-brown,
    a pale path worn from the door to the three mirrors and nowhere else. Dark, but it must read
    as wood in the chamber's little light, so the mid-tones are honey-brown, not black."""
    r = M.rng(22701)
    ppb = w / 3.0
    Y, X = np.mgrid[0:h, 0:w].astype(float)
    L = ppb * 0.4
    W_ = L / 4
    u = (X + Y) / math.sqrt(2)
    v = (X - Y) / math.sqrt(2)
    col_ = np.floor(u / L)
    shift = (col_ % 2) * (L / 2)
    vv = v + shift
    row = np.floor(vv / W_)
    alt = ((col_ + row) % 2) == 0
    pal = (M.col("#4a3020"), M.col("#6a4a30"), M.col("#2a1a10"))
    base = M.wood(r, h, w, ppb, palette=pal, grain="h", varnish=0.2, pores=0.2)
    base2 = M.wood(r, h, w, ppb, palette=pal, grain="v", varnish=0.2, pores=0.2)
    rgb = np.where(alt[..., None], base, base2)
    tid = ((col_ * 7349) + row * 9151) % 101 / 101.0
    rgb = rgb * (0.85 + 0.3 * tid)[..., None]
    jl = ((u % L) < 1.0) | ((vv % W_) < 0.8)
    rgb = np.where(jl[..., None], rgb * 0.4, rgb)
    Xn, Yn = M.grid(h, w)
    rgb = M.lerp(rgb, M.col("#5d544a"), 0.12)
    path = np.exp(-((Xn - 0.5) / 0.2) ** 2) * 0.4
    rgb = M.lerp(rgb, rgb * 1.35 + 0.03, path)
    return M.dust(rgb, 0.15, r)


def _floor_exithall(h, w):
    """Cracked, stained concrete, dirt blown in and drifted, tannin prints of rotted leaves, a
    trolley's rubber skid marks and boot scuffs (the drifts against the walls are the leaves and
    litter props)."""
    r = M.rng(22702)
    soft = M.blur(M.fnoise(r, h, w, beta=2.6), 1.5) * 0.5 + 0.5
    rgb = M.lerp(M.col("#57534c"), M.col("#706b62"), soft)
    agg = M.blur(M.fnoise(r, h, w, beta=1.2), 0.7)
    rgb = rgb * (1 + 0.035 * agg)[..., None]
    cr = M.craquelure(r, h, w, cells=6, width=0.6)
    rgb = M.lerp(rgb, M.col("#25231f"), cr * 0.6)
    dirt = M.smooth(0.5, 0.85, M.blur(M.fnoise(r, h, w, beta=3.0), 3.0) * 0.5 + 0.5)
    rgb = M.lerp(rgb, M.col("#4a3c2a"), dirt * 0.45)
    rgb = M.damp_stains(rgb, r, 2, tone=(0.25, 0.2, 0.14), strength=0.3)
    Y, X = np.mgrid[0:h, 0:w].astype(float)
    for _ in range(7):
        cx, cy = r.random() * w, r.random() * h
        a = r.random() * math.pi
        uu = (X - cx) * math.cos(a) + (Y - cy) * math.sin(a)
        vv = -(X - cx) * math.sin(a) + (Y - cy) * math.cos(a)
        Ls = (0.04 + 0.03 * r.random()) * w
        leaf = np.clip(1 - ((uu / Ls) ** 2 + (vv / (Ls * 0.5 * (1 - np.clip(uu / Ls, -1, 1) ** 2 * 0.4))) ** 2), 0, 1)
        rgb = M.lerp(rgb, M.col("#3d2d1c"), np.clip(leaf * 3, 0, 1) * 0.3)
    skid = M.blur(M.scratches(r, h, w, 6, (0.3, 0.6), 3.0, angle=0.15), 1.2)
    rgb = M.lerp(rgb, M.col("#24221f"), np.clip(skid, 0, 1) * 0.35)
    sc = M.blur(M.scratches(r, h, w, 25, (0.03, 0.1), 1.0), 0.6)
    rgb = M.lerp(rgb, rgb * 0.75, sc * 0.4)
    return M.dust(rgb, 0.12, r)


def _floor_backstage(h, w):
    """Old plywood sheets screwed down over joists, paint drips and boot scuffs, a sheet joint."""
    r = M.rng(22703)
    ppb = w / 1.5
    rgb = M.wood(r, h, w, ppb, palette=(M.col("#6e5a3f"), M.col("#8b7552"), M.col("#4d3e2a")), grain="h", rings=2.0,
                 figure=3.0, varnish=0.0, pores=0.15)
    Y, X = np.mgrid[0:h, 0:w].astype(float)
    joint = np.abs(Y - h * 0.4) < 1.2
    rgb = np.where(joint[..., None], rgb * 0.3, rgb)
    for xs in np.arange(ppb * 0.2, w, ppb * 0.4):
        for ys in (h * 0.4 - ppb * 0.06, h * 0.4 + ppb * 0.06):
            d = np.hypot(X - xs, Y - ys)
            rgb = M.lerp(rgb, M.col("#2a2824"), np.clip(1.6 - d / 1.6, 0, 1))
    for _ in range(6):
        cx, cy = r.random() * w, r.random() * h
        d = np.hypot(X - cx, Y - cy) / ((0.006 + 0.02 * r.random()) * w)
        c = M.col("#d8d2c4") if r.random() < 0.6 else M.col("#7a2a22")
        rgb = M.lerp(rgb, c, np.clip(1.2 - d, 0, 1))
    sc = M.blur(M.scratches(r, h, w, 60, (0.02, 0.08), 1.0), 0.6)
    return M.dust(M.lerp(rgb, rgb * 0.6, sc * 0.5), 0.18, r)


# ------------------------------------------------------------------------------------------------
# Ceilings (1-block tiles, mirrored 3 x 3)
# ------------------------------------------------------------------------------------------------

def _ceiling_foyer(h, w):
    """Pressed tin: embossed squares with a rosette (two per block), painted cream long ago,
    peeling to rusty tin, soot and damp."""
    r = M.rng(22710)
    ppb = w
    Y, X = np.mgrid[0:h, 0:w].astype(float) + 0.5
    p = ppb * 0.5
    fx, fy = (X % p) / p - 0.5, (Y % p) / p - 0.5
    d = np.maximum(np.abs(fx), np.abs(fy))
    rr = np.hypot(fx, fy)
    height = M.smooth(0.48, 0.42, d) * 0.5 + M.smooth(0.34, 0.30, d) * 0.3 + np.exp(-(rr / 0.12) ** 2) * 0.5
    height += M.smooth(0.03, 0.0, np.abs(np.abs(fx) - np.abs(fy))) * (d < 0.3) * 0.25
    shade = M.relief(M.blur(height, 1.0) * 24, 1.0, light=(0.0, 1.0)) * M.cavity(height * 24, 3.0, 0.5)
    soft = M.blur(M.fnoise(r, h, w, beta=2.8), 1.0) * 0.5 + 0.5
    paint = M.lerp(M.col("#b2a686"), M.col("#c6bb9c"), soft)
    tin = M.lerp(M.col("#5a4636"), M.col("#7a4b2a"), soft)
    rgb = M.chipped_paint(paint, tin, r, amount=0.16, scale=ppb * 0.5, grain=2.4)
    rgb = rgb * shade[..., None]
    return M.damp_stains(rgb, r, 1, strength=0.3)


def _ceiling_chamber(h, w):
    """Black-painted boards: the chamber's ceiling is meant not to be looked at."""
    r = M.rng(22711)
    rgb = M.wood(r, h, w, w, palette=(M.col("#161311"), M.col("#211c18"), M.col("#0c0a08")), grain="h", varnish=0.0,
                 pores=0.1)
    Y, X = np.mgrid[0:h, 0:w].astype(float)
    rgb = np.where(((Y % (h * 0.25)) < 1.0)[..., None], rgb * 0.45, rgb)
    return rgb


def _ceiling_exithall(h, w):
    """Suspended acoustic tiles (three variants side by side, each one block of 2 x 2 tiles):
    yellowed, pitted, water-stained in tide-marked blooms, one tile in the third variant gone
    to the black void above."""
    r = M.rng(22712)
    out = np.zeros((h, w, 3))
    q = w // 3
    for k in range(3):
        Y, X = np.mgrid[0:h, 0:q].astype(float) + 0.5
        p = q * 0.5
        dgrid = np.minimum(np.minimum(X % p, p - X % p), np.minimum(Y % p, p - Y % p))
        grid_ = dgrid < 2.0
        tile = M.lerp(M.col("#a39a86"), M.col("#b6ae98"), M.blur(M.fnoise(r, h, q, beta=2.8), 1.0) * 0.5 + 0.5)
        pits = M.fnoise(r, h, q, beta=0.6) > 1.6
        tile = M.lerp(tile, tile * 0.78, pits * 1.0)
        tile = M.damp_stains(tile, r, 1 + k, tone=(0.45, 0.33, 0.18), strength=0.5)
        tile = tile * (1 - 0.12 * M.smooth(6.0, 2.0, dgrid))[..., None]
        rgb = np.where(grid_[..., None], M.col("#55524c"), tile)
        if k == 2:
            gone = (X > p) & (Y < p) & ~grid_
            rgb = np.where(gone[..., None], M.col("#060505"), rgb)
        out[:, k * q:(k + 1) * q] = rgb
    return out


def _fluoro(h, w):
    """The dead fluorescent fitting's opal diffuser: yellowed plastic, the shadows of two dark
    tubes behind it, a drift of dead flies in the bottom of the trough."""
    r = M.rng(22714)
    X, Y = M.grid(h, w)
    rgb = M.lerp(M.col("#8f8a78"), M.col("#a9a38e"), M.blur(M.fnoise(r, h, w, beta=2.6), 1.0) * 0.5 + 0.5)
    for yc in (0.33, 0.67):
        rgb = M.lerp(rgb, M.col("#3d3b35"), np.exp(-((Y - yc) / 0.09) ** 2) * 0.55)
    # Tube-end burn: black rings near the ends of a tube that died slowly.
    for xc in (0.06, 0.94):
        rgb = M.lerp(rgb, M.col("#1d1c19"), np.exp(-((X - xc) / 0.03) ** 2) * np.exp(-((Y - 0.33) / 0.12) ** 2) * 0.6)
    flies = (M.fnoise(r, h, w, beta=0.5) > 2.2) & (np.abs(Y - 0.5) < 0.35)
    rgb = M.lerp(rgb, M.col("#16140f"), flies * 0.8)
    rgb = M.lerp(rgb, M.col("#4a3a22"), M.smooth(0.2, 0.0, np.minimum(X, 1 - X)) * 0.3)
    return rgb


def _ceiling_backstage(h, w):
    """The underside of the floor above: dark plywood between the joists (the joists are geometry)."""
    r = M.rng(22713)
    rgb = M.wood(r, h, w, w, palette=(M.col("#2e271e"), M.col("#3d3428"), M.col("#1c1712")), grain="h", varnish=0.0,
                 pores=0.15)
    return M.damp_stains(rgb, r, 1, strength=0.3)


# ------------------------------------------------------------------------------------------------
# Walls (one block wide, four tall; NV variants side by side in each region). Variants 0 and 1
# are PLAIN faces (picked 40 % each by Level228PropCatalog.wallVariant: no signs, no text, just
# varied wear); 2 and 3 are the rare feature faces (10 % each), so a sign never repeats on
# every block of a run.
# ------------------------------------------------------------------------------------------------

NV = 4


def _wall_grid(h, w):
    """Per-pixel (variant index, x within the block 0..1, height above the floor in blocks)."""
    X, Y = M.grid(h, w)
    v = np.minimum((X * NV).astype(int), NV - 1)
    return v, X * NV - v, (1 - Y) * CEIL


def _signs(h, w, draw):
    """Paint text/shapes into a wall region: draw(S, W, H, bx) gets a supersampled Sheet, its size
    and the pixels per block (supersampled; variant k spans x k*bx..(k+1)*bx); returns the
    (mask, colour) layers."""
    S = P.Sheet(w, h, ss=2)
    draw(S, w * S.ss, h * S.ss, w * S.ss / NV)
    return S.masks()


def _wall_foyer(h, w):
    """Red panelled dado to 1.1, a gilt chair rail (geometry), then carnival wallpaper: cream with
    dull-rose stripes and a small gold star sprig, peeling at the seams and browned by damp from the
    top. Plain variants: 0 with a hand-height smear, 1 with a seam lifting low and a water run.
    Features: 2 a painted board screwed high on the wall (NO REFUNDS / NO RE-ENTRY), 3 the ghost
    of a torn-down bill (a paler rectangle, paste stains, scraps with the bottom of a word)."""
    r = M.rng(22720)
    ppb = w / NV
    v, fx, y_up = _wall_grid(h, w)
    X, Y = M.grid(h, w)
    soft = M.blur(M.fnoise(r, h, w, beta=2.8), 1.5) * 0.5 + 0.5
    s = (fx * 6) % 1
    stripe = M.smooth(0.02, 0.0, np.abs(s - 0.5) - 0.2)
    paper = M.lerp(M.col("#bba886"), M.col("#8e6458"), stripe)
    pin = M.smooth(0.03, 0.0, np.abs(np.abs(s - 0.5) - 0.24))
    paper = M.lerp(paper, M.col("#a08250"), pin * 0.6)
    # Star sprigs in the cream bands (two per band per block height).
    sy = (y_up * 3) % 1
    star = np.hypot(((fx * 6) % 1 - 0.0) * 1.0, (sy - 0.5) * 0.5)
    star = np.minimum(star, np.hypot(((fx * 6) % 1 - 1.0), (sy - 0.5) * 0.5))
    paper = M.lerp(paper, M.col("#a8884a"), M.smooth(0.07, 0.04, star) * 0.7)
    paper = paper * (0.92 + 0.12 * soft)[..., None]
    # Seams every half block, lifting at the top; damp browning from the cornice down.
    seam = np.abs(((fx * 2) % 1) - 0.5) > 0.49
    lift = seam & (y_up > 3.0)
    plaster = M.lerp(M.col("#8c8270"), M.col("#9d927e"), soft)
    paper = np.where(lift[..., None], plaster, paper)
    paper = M.lerp(paper, M.col("#6d5638"), M.smooth(3.0, 4.0, y_up)[..., None] * (0.3 + 0.4 * soft[..., None]))
    dado = M.lerp(M.col("#5c1915"), M.col("#6f221b"), soft)
    field = (np.abs(fx - 0.5) < 0.36) & (y_up > 0.24) & (y_up < 0.95)
    edge = np.clip(1 - np.abs(np.maximum(np.abs(fx - 0.5) / 0.36, np.abs(y_up - 0.595) / 0.355) - 1.0) / 0.05, 0, 1)
    dado = dado * (1 - 0.35 * edge)[..., None] * np.where(field, 1.0, 0.9)[..., None]
    under = M.lerp(M.col("#7a6448"), M.col("#8f7856"), soft)
    dado = M.chipped_paint(dado, under, r, 0.1, 50, grain=2.4)
    out = np.where((y_up < 1.1)[..., None], dado, paper)
    # Variant 3: torn poster ghost.
    g = (v == 3) & (np.abs(fx - 0.5) < 0.33) & (np.abs(y_up - 2.05) < 0.45)
    out = np.where(g[..., None], M.lerp(out, M.col("#d8caa8"), 0.6), out)
    scrap = g & (M.blur(M.fnoise(r, h, w, beta=2.0), 1.0) > 0.5) & (np.abs(y_up - 1.7) < 0.12)
    out = np.where(scrap[..., None], M.col("#c8b48c") * soft[..., None] * 0.2 + M.col("#bfae88") * 0.8, out)
    # Variant 1: a seam lifting lower down, and a brown water run from the cornice.
    low = (v == 1) & (np.abs(fx - 0.5) < 0.012) & (y_up > 2.2)
    out = np.where(low[..., None], plaster, out)
    run = (v == 1) & (y_up > 1.2) & (np.abs(fx - 0.3 - 0.02 * np.sin(y_up * 5)) < 0.03 + 0.02 * M.smooth(1.2, 4.0, y_up))
    out = M.lerp(out, out * np.array([0.7, 0.6, 0.45]), run * 0.6)

    def draw(S, W, H, bx):
        red, board, cream = S.layer(M.col("#7f2a20")), S.layer(M.col("#22201d")), S.layer(M.col("#d6c8a2"))
        # Variant 3: the bottom of the torn bill's last line, on the scraps.
        f = P.font(P.ABRIL, 0.11 * bx)
        red.text((3.5 * bx, H - 1.7 * bx), "MIRR", font=f, fill=255, anchor="mm")
        # Variant 2: the notice board.
        x0, x1, y0, y1 = 2.22 * bx, 2.78 * bx, H - 3.02 * bx, H - 2.42 * bx
        board.rectangle([x0, y0, x1, y1], fill=255)
        cream.rectangle([x0 + 0.025 * bx, y0 + 0.025 * bx, x1 - 0.025 * bx, y1 - 0.025 * bx], outline=255, width=int(0.01 * bx))
        for (t, yy, sz, fnt) in (("NO", 0.2, 0.14, P.ANTON), ("REFUNDS", 0.42, 0.12, P.ANTON), ("NO RE-ENTRY", 0.64, 0.07, P.ANTON),
                                 ("THE MANAGEMENT", 0.83, 0.05, P.FELL)):
            size = M.fit_text_size(t, fnt, (x1 - x0) * 0.82, sz * bx, start=int(sz * bx * 1.3))
            cream.text(((x0 + x1) / 2, y0 + (y1 - y0) * yy), t, font=P.font(fnt, size), fill=255, anchor="mm")
        for (sx_, sy_) in ((x0 + 0.06 * bx, y0 + 0.06 * bx), (x1 - 0.06 * bx, y0 + 0.06 * bx), (x0 + 0.06 * bx, y1 - 0.06 * bx),
                           (x1 - 0.06 * bx, y1 - 0.06 * bx)):
            red.ellipse([sx_ - 0.012 * bx, sy_ - 0.012 * bx, sx_ + 0.012 * bx, sy_ + 0.012 * bx], fill=255)
    for k, (m, c) in enumerate(_signs(h, w, draw)):
        out = M.lerp(out, c, (m if k == 1 else M.worn_print(r, m, 0.2)) * 0.95)
    # Variant 0: a greasy smear where hands and shoulders brushed the paper.
    smear = (v == 0) & (np.abs(y_up - 1.45) < 0.25)
    out = M.lerp(out, out * 0.75, smear * M.smooth(0.25, 0.0, np.abs(y_up - 1.45)) * soft)
    out = M.grime_gradient(out, r, 0.3, 0.12, streak=0.6)
    return M.damp_stains(M.dust(out, 0.08, r), r, 2, strength=0.3)


def _wall_chamber(h, w):
    """Tall dark walnut panels, the varnish crazed; nothing on them at all. Plain: 0 one tall
    panel, 1 the same with the varnish more perished. Features: 2 a paler rectangle where
    something was once taken down, 3 a panel divided by a mid rail into two."""
    r = M.rng(22721)
    ppb = w / NV
    rgb = M.wood(r, h, w, ppb, palette=(M.col("#2a1a10"), M.col("#3e2a1d"), M.col("#140c07")), grain="v", varnish=0.3,
                 pores=0.2)
    v, fx, y_up = _wall_grid(h, w)
    hgt = np.zeros((h, w))
    for (a, b, sel) in ((0.45, 3.5, v != 3), (0.45, 1.85, v == 3), (2.05, 3.5, v == 3)):
        inside = sel & (np.abs(fx - 0.5) < 0.36) & (y_up > a) & (y_up < b)
        d = np.minimum(0.36 - np.abs(fx - 0.5), np.minimum(y_up - a, b - y_up) / 4.0)
        hgt = np.where(inside, np.clip(d / 0.06, 0, 1), hgt)
    rgb = rgb * (M.relief(hgt * 18, 0.9) * M.cavity(hgt * 18, 4.0, 0.6))[..., None]
    ghost = (v == 2) & (np.abs(fx - 0.5) < 0.24) & (np.abs(y_up - 2.2) < 0.4)
    rgb = M.lerp(rgb, rgb * 1.3, ghost * 0.6)
    crz = M.craquelure(r, h, w, cells=200, width=0.6)
    rgb = M.lerp(rgb, rgb * 0.7, crz * np.where(v == 1, 0.6, 0.3))
    bloom = M.smooth(0.55, 0.8, M.blur(M.fnoise(r, h, w, beta=2.6), 2.0) * 0.5 + 0.5)
    rgb = M.lerp(rgb, M.col("#5a4a3a"), (v == 1) * bloom * 0.25)
    return M.dust(rgb, 0.06, r)


def _wall_exithall(h, w):
    """Institutional: a glossy dark green-grey dado to 1.2 under a black line, pale cold grey-
    green gloss above, scuffed by trolleys, blistered by damp. The way-out signs painted straight
    on the plaster in red and black. Plain: 0, and 1 with a patched rectangle of fresher plaster.
    Features: 2 THANK YOU / FOR / VISITING, PLEASE COME AGAIN under it; 3 an EXIT with an arrow
    pointing on (up the wall reads as 'ahead' whichever side of the hall it is on)."""
    r = M.rng(22722)
    ppb = w / NV
    v, fx, y_up = _wall_grid(h, w)
    soft = M.blur(M.fnoise(r, h, w, beta=2.8), 1.5) * 0.5 + 0.5
    upper = M.lerp(M.col("#9aa197"), M.col("#aab0a5"), soft)
    dado = M.lerp(M.col("#3f4a44"), M.col("#4b5650"), soft)
    rgb = np.where((y_up < 1.2)[..., None], dado, upper)
    rgb = np.where((np.abs(y_up - 1.2) < 0.02)[..., None], M.col("#1e211f"), rgb)
    under = M.lerp(M.col("#8c8476"), M.col("#a0988a"), soft)
    rgb = M.chipped_paint(rgb, under, r, 0.06, 40, grain=2.6)
    patch = (v == 1) & (np.abs(fx - 0.45) < 0.25) & (np.abs(y_up - 2.0) < 0.3)
    rgb = np.where(patch[..., None], M.lerp(M.col("#b8b6a8"), M.col("#c4c1b2"), soft[..., None]), rgb)

    def draw(S, W, H, bx):
        red, black = S.layer(M.col("#8e2a22")), S.layer(M.col("#1f1d1b"))
        # Variant 3: EXIT and an arrow.
        cx = 3.5 * bx
        f = P.font(P.ANTON, 0.28 * bx)
        red.text((cx, H - 2.55 * bx), "EXIT", font=f, fill=255, anchor="mm")
        ay0, ay1 = H - 2.3 * bx, H - 1.45 * bx
        red.rectangle([cx - 0.06 * bx, ay0 + 0.18 * bx, cx + 0.06 * bx, ay1], fill=255)
        red.polygon([(cx - 0.2 * bx, ay0 + 0.22 * bx), (cx, ay0), (cx + 0.2 * bx, ay0 + 0.22 * bx)], fill=255)
        # Variant 2: THANK YOU / FOR / VISITING, and PLEASE COME AGAIN smaller below.
        cx = 2.5 * bx
        for (t, yy, sz, col) in (("THANK", 2.8, 0.22, black), ("YOU", 2.55, 0.22, black), ("FOR", 2.3, 0.16, red),
                                 ("VISITING", 2.07, 0.2, black), ("PLEASE COME AGAIN", 1.75, 0.08, red)):
            size = M.fit_text_size(t, P.ANTON, 0.84 * bx, sz * bx, start=int(sz * bx * 1.4))
            col.text((cx, H - yy * bx), t, font=P.font(P.ANTON, size), fill=255, anchor="mm")
    for m, c in _signs(h, w, draw):
        rgb = M.lerp(rgb, c, M.worn_print(r, m, 0.25) * 0.9)
    # Paint runs under the hand-painted letters (feature faces only).
    runs = M.blur(M.fnoise(r, h, w, beta=1.0, aniso=(1.0, 0.05)), 0.5) > 2.2
    rgb = M.lerp(rgb, rgb * 0.8, runs * (y_up > 1.3) * (v >= 2) * 0.5)
    sc = M.blur(M.scratches(r, h, w, 40, (0.02, 0.12), 1.0, angle=0.0), 0.5) * M.smooth(0.8, 0.4, np.abs(y_up - 0.5))
    rgb = M.lerp(rgb, M.col("#2e302c"), sc * 0.5)
    rgb = M.grime_gradient(rgb, r, 0.35, 0.15, streak=0.8)
    return M.damp_stains(M.dust(rgb, 0.06, r), r, 3, tone=(0.4, 0.33, 0.2), strength=0.35)


def _wall_backstage(h, w):
    """Plywood sheets on the service passages. Plain: 0 bare sheets with a joint and screws, 1 the
    joint lower and a boot scuff. Features: 2 STAFF ONLY stencilled, pencil notes, tallies and a
    taped staff memo; 3 a sheet missing: the bare studs (geometry), the black behind them."""
    r = M.rng(22723)
    ppb = w / NV
    rgb = M.wood(r, h, w, ppb, palette=(M.col("#7a6546"), M.col("#977f59"), M.col("#57472f")), grain="v", rings=2.0,
                 figure=3.0, varnish=0.0, pores=0.15)
    v, fx, y_up = _wall_grid(h, w)
    jy = np.where(v == 1, 1.6, 2.4)
    rgb = np.where((np.abs(y_up - jy) < 0.012)[..., None], rgb * 0.3, rgb)
    screws = (np.hypot((fx % 0.25 - 0.125) * ppb, (np.abs(y_up - jy) - 0.06) * ppb) < 1.6)
    rgb = M.lerp(rgb, M.col("#2a2824"), screws * 0.8)
    scuff = (v == 1) & (y_up < 0.5)
    rgb = M.lerp(rgb, rgb * 0.6, scuff * M.smooth(0.5, 0.1, y_up) * (M.blur(M.fnoise(r, h, w, beta=2.0), 1.0) > 0.3))
    memo = (v == 2) & (np.abs(fx - 0.62) < 0.2) & (np.abs(y_up - 1.6) < 0.26)
    rgb = np.where(memo[..., None], P.aged_paper(r, h, w, M.col("#e2d8bc"), 0.3, 1, 1), rgb)
    tape = (v == 2) & (np.abs(fx - 0.62) < 0.07) & (np.abs(y_up - 1.86) < 0.03)
    rgb = M.lerp(rgb, M.col("#c9b98a"), tape * 0.7)

    def draw(S, W, H, bx):
        sp, pen, ink = S.layer(M.col("#20201d")), S.layer(M.col("#4a463d")), S.layer(M.col("#232644"))
        # All on variant 2: the stencil, pencil notes, tallies and the memo.
        for (t, yy, sz) in (("STAFF", 2.65, 0.16), ("ONLY", 2.45, 0.16)):
            size = M.fit_text_size(t, P.ANTON, 0.7 * bx, sz * bx, start=int(sz * bx * 1.4))
            sp.text((2.5 * bx, H - yy * bx), t, font=P.font(P.ANTON, size), fill=255, anchor="mm")
        f = P.font(P.HAND, 0.07 * bx)
        pen.text((2.35 * bx, H - 1.2 * bx), "panel 14 - 2 wk", font=f, fill=255, anchor="mm")
        pen.text((2.35 * bx, H - 1.1 * bx), "bulbs x 30", font=f, fill=255, anchor="mm")
        # Tally marks: someone counting something, and stopping.
        for k in range(13):
            x = 2.25 * bx + k * 0.035 * bx + (0.02 * bx if k >= 10 else 0)
            pen.line([(x, H - 2.95 * bx), (x - 0.01 * bx, H - 2.83 * bx)], fill=255, width=2)
        mf = P.font(P.TYPE, 0.045 * bx)
        lines = ["MEMO - ALL STAFF", "", "Mirrors are NOT to be", "moved without telling", "the office.", "",
                 "Count your party in", "and count them out."]
        for k, t in enumerate(lines):
            ink.text((2.45 * bx, H - (1.8 - k * 0.055) * bx), t, font=mf, fill=255, anchor="lm")
    for m, c in _signs(h, w, draw):
        rgb = M.lerp(rgb, c, M.worn_print(r, m, 0.15) * 0.85)
    rgb = np.where((v == 3)[..., None], M.col("#080706"), rgb)
    rgb = M.grime_gradient(rgb, r, 0.35, 0.1)
    return M.dust(rgb, 0.12, r)


# (region, width px, height px, painter, colour bits)
def _regions():
    wp = WALL_PPB
    return [
        ("ha_floor_foyer", 320, 320, _floor_foyer, 5),
        ("ha_floor_chamber", 384, 384, _floor_chamber, 5),
        ("ha_floor_exithall", 320, 320, _floor_exithall, 5),
        ("ha_floor_backstage", 320, 320, _floor_backstage, 5),
        ("ha_ceiling_foyer", 160, 160, _ceiling_foyer, 5),
        ("ha_ceiling_chamber", 64, 64, _ceiling_chamber, 5),
        ("ha_ceiling_exithall", 480, 160, _ceiling_exithall, 5),
        ("ha_ceiling_backstage", 128, 128, _ceiling_backstage, 5),
        ("ha_fluoro", 192, 48, _fluoro, 6),
        ("ha_wall_foyer", NV * wp, 4 * wp, _wall_foyer, 6),
        ("ha_wall_chamber", NV * wp, 4 * wp, _wall_chamber, 5),
        ("ha_wall_exithall", NV * wp, 4 * wp, _wall_exithall, 6),
        ("ha_wall_backstage", NV * wp, 4 * wp, _wall_backstage, 6),
    ]


def regions(S):
    for name, w, h, paint, bits in _regions():
        S.region(name, SHEET, w, h, paint, bits=bits)


# ------------------------------------------------------------------------------------------------
# Meshes
# ------------------------------------------------------------------------------------------------

def _mirror_tiled(m, y, region, n, up, variants=1, pick=None):
    """Cover the cell (x, z CELL0..CELL1) at height y with n x n quads of one tile, every other
    one mirrored so neighbours meet seamlessly. variants: the tile region holds that many tiles
    side by side; pick(i, j) chooses one for quad (i, j)."""
    s = (CELL1 - CELL0) / n
    for i in range(n):
        for j in range(n):
            x0, x1 = CELL0 + i * s, CELL0 + (i + 1) * s
            z0, z1 = CELL0 + j * s, CELL0 + (j + 1) * s
            k = pick(i, j) if pick else 0
            a, b = k / variants, (k + 1) / variants
            u0, u1 = (a, b) if i % 2 == 0 else (b, a)
            v0, v1 = (0.0, 1.0) if j % 2 == 0 else (1.0, 0.0)
            if up:
                m.quad([(x0, y, z1), (x1, y, z1), (x1, y, z0), (x0, y, z0)], [(u0, v1), (u1, v1), (u1, v0), (u0, v0)], region,
                       (0, 1, 0))
            else:
                m.quad([(x0, y, z0), (x1, y, z0), (x1, y, z1), (x0, y, z1)], [(u0, v0), (u1, v0), (u1, v1), (u0, v1)], region,
                       (0, -1, 0))


def floor(z) -> G.Mesh:
    m = G.Mesh(f"floor_{z}", pivot=(2.0, 2.0))
    _mirror_tiled(m, 0.004, f"ha_floor_{z}", 1 if z == "chamber" else 2, True)
    return m


def ceiling(z) -> G.Mesh:
    m = G.Mesh(f"ceiling_{z}", pivot=(2.0, 2.0))
    y = CEIL - 0.004
    if z == "exithall":
        # Three tile variants (the third has a tile gone), chosen so no two neighbours repeat the
        # same stain and only one tile in the cell is missing.
        _mirror_tiled(m, y, "ha_ceiling_exithall", 3, False, variants=3,
                      pick=lambda i, j: 2 if (i, j) == (2, 0) else (i + j) % 2)
        # A dead surface-mounted fluorescent fitting across the cell's middle: a steel box with
        # its yellowed diffuser underneath.
        lo, hi = (1.45, CEIL - 0.1, 1.85), (2.55, CEIL - 0.004, 2.15)
        G.bevel_box(m, lo, hi, 0.015, "de_galv", rsize=(1.0, 0.5), bottom=False)
        G.card(m, (1.47, CEIL - 0.101, 1.87), (1, 0, 0), (0, 0, 1), 1.06, 0.26, "ha_fluoro", normal=np.array([0.0, -1.0, 0.0]))
    else:
        _mirror_tiled(m, y, f"ha_ceiling_{z}", 3, False)
    if z == "backstage":
        # Exposed joists across the cell, and a cable stapled along one.
        for k in range(4):
            x = CELL0 + 0.375 + k * 0.75
            G.box(m, (x - 0.05, CEIL - 0.22, CELL0), (x + 0.05, CEIL - 0.004, CELL1), "de_crate", rsize=(0.84, 0.62),
                  faces="xXy", uv_rot=True)
        G.tube(m, [(CELL0 + 0.45, CEIL - 0.24, CELL0), (CELL0 + 0.45, CEIL - 0.25, 2.0), (CELL0 + 0.45, CEIL - 0.24, CELL1)],
               0.012, 5, "de_wire", v_per_block=2.0)
    return m


def wall(z, v) -> G.Mesh:
    """One block of wall face (the -z face of the wall block), with the zone's 3D trim. v 0..1 are
    the plain faces, 2..3 the rare feature faces (see the wall painters)."""
    m = G.Mesh(f"wall_{z}_{v}", pivot=(0.5, 0.5))
    zz = -0.003
    u0, u1 = v / NV, (v + 1) / NV
    m.quad([(1.0, 0.0, zz), (0.0, 0.0, zz), (0.0, CEIL, zz), (1.0, CEIL, zz)], [(u0, 1), (u1, 1), (u1, 0), (u0, 0)],
           f"ha_wall_{z}", (0, 0, -1))

    def trim(prof, region, rsize=(4.0, 1.0)):
        # Profiles are (out of the wall, y); the wall face is z = 0 and out is -z.
        G.rail(m, [(-a, b) for a, b in prof], 0.0, 1.0, region, rsize=rsize)
    if z == "foyer":
        trim([(0.0, 0.0), (0.05, 0.0), (0.05, 0.12), (0.035, 0.15), (0.0, 0.16)], "de_paint_red", (2.0, 2.0))
        trim([(0.0, 1.06), (0.03, 1.07), (0.035, 1.1), (0.03, 1.13), (0.0, 1.14)], "de_frame_gilt", (4.0, 0.25))
        trim([(0.0, CEIL - 0.25), (0.02, CEIL - 0.24), (0.06, CEIL - 0.16), (0.12, CEIL - 0.06), (0.14, CEIL)], "de_paint_cream",
             (2.0, 2.0))
        if v == 2:
            # The NO REFUNDS board stands proud of the paper (its face is the wall texture). It hangs
            # high, over the queue, above where bills (poster props, up to y 2.36) are pasted.
            x0, x1, y0, y1 = 0.22, 0.78, 2.42, 3.02
            for (a, b, n) in (((x0, y0), (x0, y1), (-1, 0, 0)), ((x1, y1), (x1, y0), (1, 0, 0))):
                m.quad([(1 - a[0], a[1], -0.025), (1 - b[0], b[1], -0.025), (1 - b[0], b[1], 0.0), (1 - a[0], a[1], 0.0)],
                       [(0, 1), (1, 1), (1, 0), (0, 0)], "de_crate", n)
            for yy, n in ((y0, (0, -1, 0)), (y1, (0, 1, 0))):
                m.quad([(1 - x0, yy, -0.025), (1 - x1, yy, -0.025), (1 - x1, yy, 0.0), (1 - x0, yy, 0.0)],
                       [(0, 1), (1, 1), (1, 0), (0, 0)], "de_crate", n)
            uu0, uu1 = (v + x0) / NV, (v + x1) / NV
            m.quad([(1 - x0, y0, -0.026), (1 - x1, y0, -0.026), (1 - x1, y1, -0.026), (1 - x0, y1, -0.026)],
                   [(uu0, 1 - y0 / CEIL), (uu1, 1 - y0 / CEIL), (uu1, 1 - y1 / CEIL), (uu0, 1 - y1 / CEIL)], "ha_wall_foyer",
                   (0, 0, -1))
    elif z == "chamber":
        trim([(0.0, 0.0), (0.07, 0.0), (0.07, 0.2), (0.05, 0.24), (0.0, 0.26)], "de_walnut", (2.0, 1.0))
        trim([(0.0, CEIL - 0.3), (0.03, CEIL - 0.28), (0.1, CEIL - 0.15), (0.16, CEIL - 0.05), (0.18, CEIL)], "de_walnut",
             (2.0, 1.0))
    elif z == "exithall":
        trim([(0.0, 0.0), (0.02, 0.0), (0.02, 0.1), (0.0, 0.1)], "de_wire", (1.0, 1.0))
        # A steel conduit along the wall under the ceiling, feeding the caged lamps.
        G.tube(m, [(0.0, CEIL - 0.12, -0.03), (1.0, CEIL - 0.12, -0.03)], 0.018, 4, "de_galv", v_per_block=1.0)
    elif z == "backstage":
        if v == 3:
            # A sheet missing: the bare studs, a noggin, wiring clipped along it.
            for x in (0.06, 0.82):
                G.box(m, (x, 0.0, -0.1), (x + 0.1, CEIL, -0.003), "de_crate", rsize=(0.84, 0.62), faces="xXz", uv_rot=True)
            G.box(m, (0.16, 1.9, -0.1), (0.82, 2.0, -0.003), "de_crate", rsize=(0.84, 0.62), faces="yYz")
            G.tube(m, [(0.12, CEIL, -0.05), (0.12, 2.5, -0.05), (0.4, 2.05, -0.05), (0.88, 2.05, -0.05)], 0.01, 5, "de_wire",
                   v_per_block=2.0)
        else:
            trim([(0.0, 0.0), (0.02, 0.0), (0.02, 0.08), (0.0, 0.08)], "de_crate", (0.84, 0.62))
            # A run of cable clipped along the wall at 2.9, sagging a little between clips.
            G.tube(m, [(0.0, 2.9, -0.012), (0.5, 2.885, -0.012), (1.0, 2.9, -0.012)], 0.01, 4, "de_wire", v_per_block=2.0)
    return m


def meshes():
    out = []
    for z in ZONES:
        out.append(floor(z))
        out.append(ceiling(z))
        out += [wall(z, v) for v in range(NV)]
    return out
