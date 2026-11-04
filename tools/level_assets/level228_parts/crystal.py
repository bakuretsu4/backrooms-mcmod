"""Zone 2, the Crystal Maze (after the Petrin and travelling-fair mazes): dark turned-walnut
columns with gilt beads, carved walnut frames with flaking gilt scrollwork friezes, worn dark
floorboards, a coffered walnut ceiling (four sunk coffers per cell, moulded beams, a carved gilt
boss where they cross). Also the solid wall segments: Victorian panelled wainscot with
raised-and-fielded panels, chair rail, egg-and-dart frieze and cornice, the same on both faces."""
from __future__ import annotations

import math

import numpy as np
from scipy import ndimage

from . import carve as C
from . import geo as G
from . import mat as M
from .dims import BAND_Y0, CEIL, CELL0, CELL1, PANE_Y1, PLANE

SHEET = "crystal"

# Walnut a step lighter than the shared M.WALNUT: in the real-layout shots, under 2500 K bulbs with
# real darkness between them, the shared palette read as one murky brown and the turned and
# moulded forms vanished. Kept chocolate rather than orange (a brighter, more saturated light tone
# turned the grain into teak-like orange flecks), so it still reads as dark old walnut. Lifted
# again (~20 % in value, same hue) after in-game review under Iris, where the panelling all but
# disappeared: mean sheet albedo was ~0.27 sRGB (0.07 linear), now ~0.32 (0.09).
WALNUT = (M.col("#6c4a31"), M.col("#966c4a"), M.col("#3c2819"))

# Solid wall ("wainscot") layout, prop-local x along the wall, y up, z out of the pane plane. The
# texture painter and the mesh share these so the painted shading sits on the real bevels.
W_OPEN_X = ((-0.05, 0.92), (1.08, 2.05))   # the two panel columns, either side of the muntin
W_DADO_Y = (0.42, 0.86)                     # low panels under the chair rail
W_UPPER_Y = (1.16, 2.52)                    # tall panels above it
W_SKIRT = 0.27                              # top of the skirting board's flat face
W_BASE = 0.30                               # the frame starts on the skirting's moulding
W_CHAIR = (0.955, 1.07)
W_FRIEZE = (2.62, 2.79)                     # egg-and-dart strip under the cornice
W_TOP = PANE_Y1 - 0.025                     # the panel mesh's own top rail sits on this
ZF, ZG, ZP = 0.045, 0.014, 0.040            # frame face, panel ground, raised field (from PLANE)
SIGHT, MARGIN, BEVEL = 0.03, 0.048, 0.12    # insets from an opening's edge: sight moulding,
                                            # flat ground, field bevel; the field is flat inside

# Coffered ceiling: a moulded frame round the cell (on top of the band's cornice) and two crossing
# beams through the cell centre leave four sunk coffers.
C_PB, C_PD = 0.40, 0.12                     # perimeter frame: width from the grid line, depth
C_BW, C_BD = 0.11, 0.17                     # cross beams: half width, depth


# ------------------------------------------------------------------------------------------------
# Textures
# ------------------------------------------------------------------------------------------------

def _varnish(rgb, r, grain="v", amount=1.0):
    """Old shellac/varnish: still glossy and amber in patches, polish streaks running along the
    grain, gone milky (bloom) where damp got under it. Only diffuse light reaches these props in
    game, so this baked variation is what lets dark wood show its form under a dim bulb. Smooth,
    mid-frequency fields: they read from a distance and cost little in the PNG."""
    h, w = rgb.shape[:2]
    along = (1.0, 8.0) if grain == "v" else (8.0, 1.0)
    gloss = M.smooth(0.4, 0.85, M.fnoise(r, h, w, beta=2.8) * 0.5 + 0.5)
    streak = M.smooth(0.55, 1.0, M.fnoise(r, h, w, beta=2.8, aniso=along) * 0.5 + 0.5)
    lift = 1.0 + amount * (0.12 * gloss + 0.14 * streak * (0.4 + 0.6 * gloss))
    amber = np.array([1.03, 1.0, 0.94]) ** (amount * gloss)[..., None]
    rgb = rgb * lift[..., None] * amber
    bloom = M.smooth(0.62, 0.9, M.fnoise(r, h, w, beta=2.6) * 0.5 + 0.5) * (1 - gloss)
    rgb = M.lerp(rgb, M.col("#6f6357"), bloom * 0.2 * amount)
    return np.clip(rgb, 0, 1)


def _wood(r, h, w, ppb, grain="v", rings=6.0, figure=1.0, varnish=0.4, sheen=1.0):
    rgb = M.wood(r, h, w, ppb=ppb, palette=WALNUT, grain=grain, rings=rings, figure=figure, varnish=varnish)
    # Soften the one-pixel fibre flecks along the grain: at 128-240 px per block they read as
    # bright noise rather than close-grained walnut (and cost PNG bytes for nothing).
    sig = (1.8, 0.6) if grain == "v" else (0.6, 1.8)
    rgb = np.stack([ndimage.gaussian_filter(rgb[..., c], sig, mode="reflect") for c in range(3)], axis=-1)
    return _varnish(rgb, r, grain, sheen)


def _shaft(h, w):
    r = M.rng(22801)
    rgb = _wood(r, h, w, ppb=w, grain="v", rings=5.0, figure=1.4, varnish=0.45)
    # Handled at chest height, kicked at the bottom: polish worn pale, scuffs low down, dust high.
    X, Y = M.grid(h, w)
    rub = np.exp(-((Y - 0.55) / 0.12) ** 2) * (0.5 + 0.5 * (M.fnoise(r, h, w, beta=2.0) * 0.5 + 0.5))
    rgb = M.lerp(rgb, rgb * np.array([1.25, 1.18, 1.05]), rub * 0.35)
    kicks = M.scratches(r, h, w, 30, (0.01, 0.05), 1.0, angle=0.0) * M.smooth(0.8, 0.95, Y)
    rgb = M.lerp(rgb, M.col("#7a6450"), kicks * 0.5)
    rgb = M.grime_gradient(rgb, r, bottom=0.22, top=0.05)
    return M.dust(rgb, 0.1, r, up=M.smooth(0.3, 0.0, Y))


def _gilt(h, w):
    """Gilt beads, fillets and slips: tarnished leaf, rubbed through to the red bole in streaks
    along the mouldings (rails sample this region lengthwise), flaked to gesso and the wood."""
    r = M.rng(22802)
    ppb = w / 3.0
    g = M.gilt(r, h, w, ppb=ppb, wear=0.45, flake=0.2, base=WALNUT[0])
    n = M.fnoise(r, h, w, beta=2.0) * 0.5 + 0.5
    # kmin: short rubs, never a whole band of rows (a rail samples a few rows end to end, so one
    # broad patch turned entire beads red).
    rub = M.smooth(0.7, 0.86, M.fnoise(r, h, w, beta=2.0, aniso=(3.0, 1.0), kmin=6.0) * 0.5 + 0.5)
    g = M.lerp(g, M.BOLE * (0.8 + 0.4 * n)[..., None], rub * 0.65)
    return M.dust(g, 0.14, r)


def _wood_h(h, w):
    r = M.rng(22803)
    rgb = _wood(r, h, w, ppb=w / 4.0, grain="h", rings=6.0, varnish=0.4)
    return M.dust(M.grime_gradient(rgb, r, bottom=0.12, top=0.04), 0.1, r)


def _wood_v(h, w):
    r = M.rng(22804)
    rgb = _wood(r, h, w, ppb=w / 1.0, grain="v", rings=6.0, varnish=0.4)
    return M.dust(rgb, 0.08, r)


def _frieze(h, w):
    """Carved walnut frieze: a gilt acanthus scroll on a dark punched ground between gilt fillets."""
    r = M.rng(22805)
    height, mask = C.scroll_frieze(r, h, w, waves=3.0)
    Y, X = np.mgrid[0:h, 0:w].astype(float)
    # Fillets top and bottom (raised flat bands), and a bead below the top one.
    fil = (Y < h * 0.08) | (Y > h * 0.92)
    bead = np.abs(Y - h * 0.12) < h * 0.025
    height = np.maximum(height * 0.85, fil * 0.9)
    height = np.maximum(height, np.sqrt(np.clip(1 - np.abs(Y - h * 0.12) / (h * 0.025), 0, 1)) * bead * 0.8)
    ground = _wood(r, h, w, ppb=w / 3.0, grain="h", rings=5.0, varnish=0.2, sheen=0.6) * 0.62
    # Punched ground (tiny stippled dots, as carvers matted the background).
    st = (M.fnoise(r, h, w, beta=0.2) > 1.6)
    ground = M.lerp(ground, ground * 0.6, st * 0.7)
    carved = M.gilt(r, h, w, ppb=w / 3.0, wear=0.5, flake=0.3, height=height)
    solid = (mask > 0.5) | fil | bead
    rgb = np.where(solid[..., None], carved, ground * M.cavity(height, 3.0, 1.0)[..., None])
    # Shadow cast by the carving onto the ground below it (light from above).
    sh = M.blur(np.roll(solid.astype(float), int(h * 0.03), axis=0), 2.0)
    rgb = np.where(solid[..., None], rgb, rgb * (1 - 0.55 * sh)[..., None])
    rgb = M.dust(rgb, 0.22, r, up=M.smooth(0.3, 0.9, height))
    return rgb


def _impost(h, w):
    """The square pier above each capital: a sunk walnut panel with a carved gilt rosette."""
    r = M.rng(22806)
    rgb = _wood(r, h, w, ppb=w / 0.54, grain="v", varnish=0.35)
    X, Y = M.grid(h, w)
    inner = (np.abs(X - 0.5) < 0.38) & (np.abs(Y - 0.5) < 0.40)
    height = np.where(inner, 0.2, 0.6)
    n = min(h, w)
    ro, rm = C.rosette(r, int(n * 0.62))
    pad_y, pad_x = (h - ro.shape[0]) // 2, (w - ro.shape[1]) // 2
    rh = np.zeros((h, w))
    rh[pad_y:pad_y + ro.shape[0], pad_x:pad_x + ro.shape[1]] = ro
    rmask = np.zeros((h, w), bool)
    rmask[pad_y:pad_y + ro.shape[0], pad_x:pad_x + ro.shape[1]] = rm
    height = np.maximum(height, rh * 0.9 + 0.2)
    sh = M.relief(M.blur(height, 1.2) * 18, 0.9) * M.cavity(height, 4.0, 0.8)
    rgb = rgb * sh[..., None]
    gold = M.gilt(r, h, w, ppb=w / 0.54, wear=0.5, flake=0.3, height=rh)
    rgb = np.where(rmask[..., None], gold, rgb)
    rgb = M.lerp(rgb, rgb * 0.6, np.where(inner & ~rmask, 0.35, 0.0))
    return M.dust(rgb, 0.18, r)


def _floor(h, w):
    """Worn dark floorboards along x: about 0.18 block wide, butt joints staggered, nail heads, the
    finish walked off down the middle of the cell and dust thick against the panels."""
    r = M.rng(22807)
    ppb = w / 3.0
    bw = int(round(ppb * 0.1875))  # 16 boards over three blocks
    rows = h // bw + 1
    out = np.zeros((h, w, 3))
    for k in range(rows):
        y0, y1 = k * bw, min(h, (k + 1) * bw)
        if y0 >= h:
            break
        # Each board is 1.3-2.6 blocks long; joints staggered.
        x = -int(r.random() * ppb * 1.5)
        while x < w:
            L = int(ppb * (1.3 + 1.3 * r.random()))
            xa, xb = max(0, x), min(w, x + L)
            if xb > xa:
                rr = M.rng(int(r.integers(1 << 30)))
                board = M.wood(rr, y1 - y0, xb - xa, ppb=ppb, palette=WALNUT, grain="h", rings=4.0 + 3 * rr.random(),
                               pores=0.3, varnish=0.25)
                board = board * (0.8 + 0.3 * rr.random())
                out[y0:y1, xa:xb] = board
                # End joint (dark gap) and two nail heads at each end.
                if x > 0:
                    out[y0:y1, max(0, x - 1):x + 1] *= 0.3
                for nx in (xa + int(ppb * 0.04), xb - int(ppb * 0.05)):
                    for ny in (y0 + bw // 4, y1 - bw // 4):
                        if 0 <= nx < w and 0 <= ny < h:
                            out[max(0, ny - 1):ny + 2, max(0, nx - 1):nx + 2] = M.col("#1a1612")
            x += L
        # Long gap between boards with a lighter worn arris above it.
        out[y0:min(h, y0 + 2)] *= np.array([0.38, 0.35, 0.32])
        out[min(h - 1, y0 + 2):min(h, y0 + 3)] *= 1.15
    out = _varnish(out, r, "h", 0.7)
    Xn, Yn = M.grid(h, w)
    # Walked track: lighter, finish gone, in the middle; dust at the edges under the panels.
    centre = np.exp(-(((Xn - 0.5) / 0.26) ** 2 + ((Yn - 0.5) / 0.26) ** 2))
    track = centre * (0.6 + 0.4 * (M.fnoise(r, h, w, beta=2.4) * 0.5 + 0.5))
    out = M.lerp(out, out * np.array([1.32, 1.25, 1.12]) + 0.03, track * 0.45)
    edge = np.clip(1 - np.minimum(np.minimum(Xn, 1 - Xn), np.minimum(Yn, 1 - Yn)) / 0.14, 0, 1)
    out = M.lerp(out, M.col("#5f594f"), edge ** 1.5 * 0.5 * (0.6 + 0.4 * (M.fnoise(r, h, w, beta=2.4) * 0.5 + 0.5)))
    # Scuffs and heel marks.
    sc = M.scratches(r, h, w, 70, (0.01, 0.06), 1.2)
    out = M.lerp(out, out * 0.6, sc * 0.45)
    out = M.damp_stains(out, r, 1, tone=(0.1, 0.075, 0.05), strength=0.3)
    return np.clip(out, 0, 1)


def _coffer_fields(X, Z):
    """Per pixel of a cell's soffit (cell-local X, Z): inset from the nearest coffer edge, which
    edge (0 -x, 1 +x, 2 -z, 3 +z of the coffer) and the coffer's centre."""
    spans = ((CELL0 + C_PB, 2.0 - C_BW), (2.0 + C_BW, CELL1 - C_PB))

    def axis(A):
        lo = np.where(A < 2.0, spans[0][0], spans[1][0])
        hi = np.where(A < 2.0, spans[0][1], spans[1][1])
        return A - lo, hi - A, (lo + hi) * 0.5

    xl, xh, cx = axis(X)
    zl, zh, cz = axis(Z)
    dd = np.stack([xl, xh, zl, zh])
    return dd.min(0), dd.argmin(0), cx, cz


def _ceiling(h, w):
    """The soffit of one cell (beams and boss are geometry): four sunk walnut coffers, each inside a
    painted cove and gilt bead with a small carved rosette in the middle; smoke-darkened over the
    bulbs, cobweb dust in the corners, old damp."""
    r = M.rng(22808)
    ppb = w / 3.0
    rgb = _wood(r, h, w, ppb, grain="h", rings=5.0, varnish=0.25) * 1.05
    Xn, Yn = M.grid(h, w)
    X, Z = CELL0 + 3.0 * Xn, CELL0 + 3.0 * Yn
    d, side, cx, cz = _coffer_fields(X, Z)
    # Cove (d < 0.06) curving up into the sunk panel, catching the light of the bulb hanging in
    # the coffer; the sunk panel inside it is in its own shadow.
    cove_lit = 0.75 + 0.4 * np.sin(np.clip(d / 0.06, 0, 1) * math.pi)
    rgb = rgb * np.where(d < 0.06, cove_lit, 0.9)[..., None]
    bead = np.exp(-((d - 0.068) / 0.006) ** 2)
    gold = M.gilt(r, h, w, ppb=ppb, wear=0.6, flake=0.35, base=WALNUT[0])
    rgb = M.lerp(rgb, gold, bead * 0.85)
    # Carved rosette in the middle of each coffer.
    n = int(ppb * 0.34)
    ro, rm = C.rosette(r, n)
    rh = np.zeros((h, w))
    rmask = np.zeros((h, w), bool)
    for c0 in (0.5 * (CELL0 + C_PB + 2.0 - C_BW), 0.5 * (2.0 + C_BW + CELL1 - C_PB)):
        for c1 in (0.5 * (CELL0 + C_PB + 2.0 - C_BW), 0.5 * (2.0 + C_BW + CELL1 - C_PB)):
            px, py = int((c0 - CELL0) * ppb) - n // 2, int((c1 - CELL0) * ppb) - n // 2
            rh[py:py + n, px:px + n] = np.maximum(rh[py:py + n, px:px + n], ro)
            rmask[py:py + n, px:px + n] |= rm
    gold2 = M.gilt(r, h, w, ppb=ppb, wear=0.6, flake=0.3, height=rh * 6)
    rgb = np.where(rmask[..., None], gold2, rgb * M.cavity(rh * 6, 2.5, 0.6)[..., None])
    # Soot from the bulbs, and cobweb-grey dust in the coffers' corners.
    rr = np.hypot(X - cx, Z - cz)
    rgb = M.lerp(rgb, M.col("#0d0907"), np.exp(-(rr / 0.3) ** 2) * 0.28)
    corner = np.clip(1 - np.hypot(np.minimum(np.abs(X - cx), 0.5) - 0.5, np.minimum(np.abs(Z - cz), 0.5) - 0.5) / 0.14, 0, 1)
    rgb = M.lerp(rgb, M.col("#5a554c"), corner ** 2 * 0.45)
    rgb = M.damp_stains(rgb, r, 2, strength=0.22)
    return np.clip(rgb, 0, 1)


def _wainscot(h, w):
    """Both faces of a solid wall, in wall coordinates (x -0.5..2.5, y 0..W_TOP): stiles, rails and
    muntin with their joints and draw-bore pegs, four raised-and-fielded panels (the mesh's bevels
    sample the matching bands, so the shading here is baked onto real slopes), and the wear: kicked
    and scuffed low, rubbed pale at hand height, grimy at the foot, dust on every upward ledge."""
    r = M.rng(22809)
    ppb = w / 3.0
    Xn, Yn = M.grid(h, w)
    X = -0.5 + 3.0 * Xn
    Y = W_TOP * (1.0 - Yn)
    vert = _wood(r, h, w, ppb, grain="v", rings=6.0, varnish=0.45)
    hori = _wood(r, h, w, ppb, grain="h", rings=6.0, varnish=0.45)
    rows = ((Y > W_DADO_Y[0]) & (Y < W_DADO_Y[1])) | ((Y > W_UPPER_Y[0]) & (Y < W_UPPER_Y[1]))
    stile_l = X < W_OPEN_X[0][0]
    stile_r = X > W_OPEN_X[1][1]
    muntin = (X > W_OPEN_X[0][1]) & (X < W_OPEN_X[1][0]) & rows
    d = np.full((h, w), -1.0)
    side = np.zeros((h, w), int)    # nearest edge of the opening: 0 bottom, 1 right, 2 top, 3 left
    pid = np.full((h, w), -1)
    k = 0
    for (xa, xb) in W_OPEN_X:
        for (ya, yb) in (W_DADO_Y, W_UPPER_Y):
            inside = (X > xa) & (X < xb) & (Y > ya) & (Y < yb)
            dd = np.stack([Y - ya, xb - X, yb - Y, X - xa])
            d = np.where(inside, dd.min(0), d)
            side = np.where(inside, dd.argmin(0), side)
            pid = np.where(inside, k, pid)
            k += 1
    opening = d >= 0
    rgb = np.where((stile_l | stile_r | muntin)[..., None], vert, hori)
    # Each field is its own board: shift the grain and tone so they don't run on from each other.
    for k in range(4):
        sel = pid == k
        rgb = np.where(sel[..., None], np.roll(vert, 37 * (k + 1), axis=1) * (0.9 + 0.07 * k), rgb)
    # Joints between the frame members (and pegs through each tenon).
    board = np.where(stile_l, 1, np.where(stile_r, 2, np.where(muntin, 3, np.where(opening, 0, 4))))
    joint = np.zeros((h, w), bool)
    for ax in (0, 1):
        nb = np.roll(board, 1, axis=ax)
        joint |= (nb != board) & (nb > 0) & (board > 0)
    rgb = np.where(joint[..., None], rgb * 0.45, rgb)
    Yp, Xp = np.mgrid[0:h, 0:w]
    for yc in (0.5 * (W_BASE + W_DADO_Y[0]), 0.5 * (W_UPPER_Y[1] + W_FRIEZE[0])):
        for xc in (-0.2, 2.2, 1.0):
            for dy in (-0.025, 0.025):
                cxp, cyp = (xc + 0.5) * ppb, (W_TOP - yc - dy) * ppb * h / (W_TOP * ppb)
                peg = np.hypot(Xp + 0.5 - cxp, (Yp + 0.5 - cyp)) < max(1.2, ppb * 0.011)
                rgb = np.where(peg[..., None], rgb * 0.55 + 0.02, rgb)
    # Baked shading of the real slopes (light from the bulbs overhead): the bottom sight edge and
    # the top field bevel face up, the others away; the ground margin is in shadow.
    sight_k = np.array([1.25, 0.88, 0.55, 1.0])
    bevel_k = np.array([0.6, 0.86, 1.4, 1.1])
    shade = np.ones((h, w))
    shade = np.where(opening & (d < SIGHT), sight_k[side], shade)
    shade = np.where(opening & (d >= SIGHT) & (d < MARGIN), 0.45, shade)
    shade = np.where(opening & (d >= MARGIN) & (d < BEVEL), bevel_k[side], shade)
    field = opening & (d >= BEVEL)
    shade = np.where(field, 1.1, shade)
    shade = M.blur(shade, 0.7)
    rgb = rgb * shade[..., None]
    # Old varnish crazed on the fields.
    cr = M.craquelure(r, h, w, cells=260)
    rgb = M.lerp(rgb, rgb * 0.7, cr * field * 0.35)
    # Wear: hands at chair-rail height rub the varnish pale; feet and brooms scuff the bottom.
    rub = np.exp(-((Y - 1.15) / 0.3) ** 2) * (0.5 + 0.5 * (M.fnoise(r, h, w, beta=2.2) * 0.5 + 0.5))
    rgb = M.lerp(rgb, rgb * np.array([1.22, 1.15, 1.04]), rub * 0.4)
    low = M.smooth(0.65, 0.25, Y)
    sc = M.scratches(r, h, w, 60, (0.01, 0.07), 1.0) * low
    rgb = M.lerp(rgb, M.col("#8c7a66"), sc * 0.4)
    heel = M.smooth(0.7, 0.9, M.fnoise(r, h, w, beta=2.0) * 0.5 + 0.5) * low
    rgb = M.lerp(rgb, M.col("#16110c"), heel * 0.45)
    rgb = M.grime_gradient(rgb, r, bottom=0.28, top=0.03)
    rgb = M.damp_stains(rgb, r, 1, strength=0.25)
    ledge = (opening & (((d < SIGHT) & (side == 0)) | ((d >= MARGIN) & (d < BEVEL) & (side == 2)))).astype(float)
    up = np.clip(M.blur(ledge, 0.8) * 1.4 + M.smooth(1.4, 2.9, Y) * 0.7, 0, 1)
    return M.dust(rgb, 0.1, r, up=up)


def _eggdart(h, w):
    """The carved egg-and-dart under the solid walls' cornice: gilt eggs and darts in walnut shells
    under a gilt fillet, the eggs' crowns rubbed through to the bole."""
    r = M.rng(22812)
    Y, X = np.mgrid[0:h, 0:w].astype(float) + 0.5
    p = w / 28.0
    fx = (X % p) / p - 0.5          # -0.5..0.5 across one egg's cell
    cy = h * 0.6
    egg = 1.0 - (fx / 0.27) ** 2 - ((Y - cy) / (h * 0.30)) ** 2
    shell = ((fx / 0.43) ** 2 + ((Y - cy + h * 0.03) / (h * 0.42)) ** 2 < 1.0) & (egg < -0.25) & (Y > h * 0.3)
    t = np.clip((Y - h * 0.26) / (h * 0.66), 0, 1)
    dart = (np.abs(np.abs(fx) - 0.5) < 0.08 * (1 - t)) & (Y > h * 0.26)
    fillet = Y < h * 0.2
    egg_h = np.sqrt(np.clip(egg, 0, 1))
    height = np.maximum.reduce([egg_h, shell * 0.55, dart * 0.6, fillet * 0.75])
    gild = (egg > 0) | dart | fillet
    carved = gild | shell
    gold = M.gilt(r, h, w, ppb=w / 3.0, wear=0.6, flake=0.22, height=height * 5)
    gold = M.lerp(gold, M.BOLE, M.smooth(0.75, 1.0, egg_h) * 0.4)
    wood = _wood(r, h, w, ppb=w / 3.0, grain="h", rings=5.0, varnish=0.3)
    wood = wood * M.relief(height * 5, 1.0)[..., None]
    ground = wood * 0.45
    rgb = np.where(gild[..., None], gold, np.where(shell[..., None], wood, ground))
    sh = M.blur(np.roll(carved.astype(float), max(1, h // 14), axis=0), 1.0)
    rgb = np.where(carved[..., None], rgb, rgb * (1 - 0.5 * sh)[..., None])
    return M.dust(rgb, 0.16, r, up=M.smooth(0.3, 0.9, height))


def _boss(h, w):
    """Underside of the ceiling boss: a carved gilt rosette on a dark sunk ground."""
    r = M.rng(22813)
    ro, rm = C.rosette(r, h)
    gold = M.gilt(r, h, w, ppb=w * 5.0, wear=0.6, flake=0.25, height=ro * 6)
    ground = _wood(r, h, w, ppb=w * 5.0, grain="h") * 0.55 * M.cavity(ro * 6, 2.0, 0.8)[..., None]
    rgb = np.where(rm[..., None], gold, ground)
    return M.dust(rgb, 0.12, r)


def _bracket(h, w):
    """Side view of a carved scroll corbel (mask = the shape; gilt carved face)."""
    r = M.rng(22811)
    m = bracket_mask(h, w)
    height = C._rounded(m, w * 0.12)
    rgb = M.gilt(r, h, w, ppb=512, wear=0.5, flake=0.25, height=height * 20)
    return M.rgba(rgb, m.astype(float))


def bracket_mask(h, w):
    """An S-scroll corbel seen from the side: wide at the top (against the rail), curling to a
    volute at the bottom (row 0 = top)."""
    from PIL import Image, ImageDraw
    ss = 4
    img = Image.new("L", (w * ss, h * ss), 0)
    d = ImageDraw.Draw(img)
    W, H = w * ss, h * ss
    pts = [(0, 0), (W, 0)]
    for t in np.linspace(0, 1, 40):
        x = W * (1.0 - 0.55 * t) - W * 0.15 * math.sin(t * math.pi)
        y = H * 0.78 * t
        pts.append((x, y))
    cx, cy, R = W * 0.36, H * 0.80, W * 0.30
    for t in np.linspace(0, 1.6 * math.pi, 50):
        rr = R * (1 - t / (2.2 * math.pi))
        pts.append((cx + rr * math.cos(t - 0.2), cy + rr * math.sin(t - 0.2)))
    pts.append((0, H * 0.75))
    d.polygon(pts, fill=255)
    m = np.asarray(img.resize((w, h), Image.LANCZOS), float) / 255.0
    return m > 0.5


def regions(S):
    S.region("cr_shaft", SHEET, 192, 704, _shaft)
    S.region("cr_gilt", SHEET, 384, 128, _gilt)
    S.region("cr_wood_h", SHEET, 512, 128, _wood_h)
    S.region("cr_wood_v", SHEET, 128, 256, _wood_v)
    S.region("cr_frieze", SHEET, 1024, 212, _frieze)
    S.region("cr_impost", SHEET, 128, 168, _impost)
    # Floor and ceiling are only ever seen at a slant and mipmapped: 5-bit colour.
    S.region("cr_floor", SHEET, 512, 512, _floor, bits=5)
    S.region("cr_ceiling", SHEET, 512, 512, _ceiling, bits=5)
    # The solid walls are seen from a block away in the narrow passages: ~170 px per block.
    S.region("cr_wainscot", SHEET, 512, 494, _wainscot)
    S.region("cr_eggdart", SHEET, 512, 32, _eggdart)
    S.region("cr_boss", SHEET, 64, 64, _boss)
    S.region("cr_bracket", SHEET, 96, 160, _bracket)


# ------------------------------------------------------------------------------------------------
# Meshes
# ------------------------------------------------------------------------------------------------

def _bead_profile(r, n=6, z0=0.0, y0=0.0):
    """A half-round bead of radius r lying on a surface, as (z, y) going up its face."""
    return [(z0 + r * math.sin(t), y0 - r * math.cos(t)) for t in np.linspace(0, math.pi, n)]


def column() -> G.Mesh:
    m = G.Mesh("column_crystal", pivot=(0.5, 0.5))
    # Square plinth (chamfered; no bottom face, it stands on the floor), turned base, shaft,
    # capital, square abacus, impost pier, crown.
    G.lathe(m, [(0.37, 0.0), (0.37, 0.12), (0.355, 0.15), (0.0, 0.15)], 4, "cr_wood_v", flat=True,
            phase=math.pi / 4, u_turns=1.0, v0=0.3, v1=0.0)
    # 12 sides, smooth-shaded: a chunk holds a few dozen columns and every one is drawn again in
    # each mirror image, so the profiles carry only the turns that change the silhouette.
    sides = 12
    G.lathe(m, [(0.33, 0.15), (0.338, 0.185), (0.32, 0.228), (0.275, 0.25)], sides, "cr_gilt",
            v0=0.9, v1=0.6, u_turns=1.3)
    G.lathe(m, [(0.275, 0.25), (0.238, 0.29), (0.258, 0.33), (0.205, 0.40), (0.214, 1.15), (0.183, 2.42)], sides,
            "cr_shaft", v0=1.0, v1=0.18)
    # Gilt astragal under the capital.
    G.lathe(m, [(0.183, 2.42), (0.201, 2.445), (0.183, 2.47)], sides, "cr_gilt", v0=0.5, v1=0.3, u_turns=1.1)
    G.lathe(m, [(0.183, 2.47), (0.19, 2.6), (0.268, 2.73), (0.315, 2.79)], sides, "cr_shaft", v0=0.18, v1=0.05)
    G.lathe(m, [(0.315, 2.79), (0.337, 2.815), (0.31, 2.84)], sides, "cr_gilt", v0=0.25, v1=0.05, u_turns=1.3)
    # Abacus: square, chamfered (its top is under the impost).
    G.lathe(m, [(0.2, 2.84), (0.34, 2.84), (0.355, 2.86), (0.355, 2.95), (0.34, 2.97)], 4, "cr_wood_h",
            flat=True, phase=math.pi / 4, u_turns=0.55, v0=0.6, v1=0.2)
    # Impost pier through the frieze band, carved panel on every face, then the crown block.
    for k in range(4):
        rot = G.rot_y(k * math.pi / 2)
        face = G.Mesh("tmp")
        G.card(face, (-0.27, BAND_Y0 - 0.03, 0.27), (1, 0, 0), (0, 1, 0), 0.54, 0.72, "cr_impost",
               normal=np.array([0.0, 0.0, 1.0]))
        m.add(face, rot, (0.5, 0.0, 0.5))
    G.lathe(m, [(0.27, 3.69), (0.30, 3.71), (0.36, 3.86), (0.37, 3.95), (0.385, 3.965), (0.385, 4.0)], 4, "cr_wood_h",
            flat=True, phase=math.pi / 4, u_turns=0.6, v0=0.9, v1=0.4)
    # Slimmer below the abacus than first drawn: seen from one block away in a 2-wide corridor the
    # full-size shaft read as a tree trunk; the references' columns are about a seventh of the bay.
    for q in m.quads:
        if q.p[:, 1].max() <= 2.841:
            q.p[:, 0] = 0.5 + (q.p[:, 0] - 0.5) * 0.84
            q.p[:, 2] = 0.5 + (q.p[:, 2] - 0.5) * 0.84
    m.contact_ao(0.0, CEIL, reach=0.4, strength=0.5)
    return m


def _rails_front(m: G.Mesh, x0, x1, skirting=True, top=True):
    """The front (+z) half of a panel's frame mouldings, x0..x1, about the plane z = PLANE."""
    z = PLANE
    if skirting:
        prof = [(0.075, 0.0), (0.075, 0.085), (0.068, 0.095), (0.068, 0.105), (0.055, 0.118), (0.04, 0.128),
                (0.028, 0.135), (0.012, 0.135)]
        G.rail(m, [(z + a, b) for a, b in prof], x0, x1, "cr_wood_h", rsize=(4.0, 1.0), v_off=0.1)
        G.rail(m, [(z + 0.03 + a, 0.141 + b) for a, b in _bead_profile(0.011)], x0, x1, "cr_gilt", rsize=(3.0, 1.0))
    if top:
        prof = [(0.012, PANE_Y1 - 0.025), (0.03, PANE_Y1 - 0.025), (0.045, PANE_Y1 - 0.012), (0.052, PANE_Y1 + 0.01),
                (0.055, BAND_Y0)]
        G.rail(m, [(z + a, b) for a, b in prof], x0, x1, "cr_wood_h", rsize=(4.0, 1.0), v_off=0.4)
        G.rail(m, [(z + 0.025 + a, PANE_Y1 - 0.02 + b) for a, b in _bead_profile(0.01)], x0, x1, "cr_gilt",
               rsize=(2.0, 1.0), v_off=0.3)


def _band_front(m: G.Mesh, x0, x1):
    """Frieze and cornice over a panel or opening (front half)."""
    z = PLANE
    # Lower fillet, carved frieze face, upper fillet.
    G.rail(m, [(z + 0.055, BAND_Y0), (z + 0.09, BAND_Y0 + 0.005), (z + 0.095, BAND_Y0 + 0.03), (z + 0.08, BAND_Y0 + 0.045)],
           x0, x1, "cr_gilt", rsize=(3.0, 1.0), v_off=0.5)
    G.card(m, (x0, BAND_Y0 + 0.045, z + 0.08), (1, 0, 0), (0, 1, 0), x1 - x0, 0.62, "cr_frieze",
           uv=((0.0, 0.0), (min(1.0, (x1 - x0) / 3.0), 1.0)), normal=np.array([0.0, 0.0, 1.0]))
    y = BAND_Y0 + 0.665
    cyma = [(0.08, y), (0.11, y + 0.005), (0.115, y + 0.03)]
    for t in np.linspace(0, 1, 7)[1:]:
        # Cyma recta: concave below, convex above.
        zz = 0.115 + 0.11 * t
        yy = y + 0.03 + 0.15 * (0.5 - 0.5 * math.cos(t * math.pi)) * (1 - 0.3 * math.sin(t * math.pi * 2))
        cyma.append((zz, yy))
    cyma += [(0.235, y + 0.19), (0.25, y + 0.20), (0.25, y + 0.27), (0.265, y + 0.275), (0.265, CEIL)]
    G.rail(m, [(z + a, b) for a, b in cyma], x0, x1, "cr_wood_h", rsize=(4.0, 1.0), v_off=0.6)
    G.rail(m, [(z + 0.236 + a, y + 0.195 + b) for a, b in _bead_profile(0.008)], x0, x1, "cr_gilt", rsize=(3.0, 1.0), v_off=0.7)


def _both_sides(front: G.Mesh, into: G.Mesh):
    into.add(front)
    into.add(front, G.scale(1, 1, -1), (0.0, 0.0, 2 * PLANE))


def _brackets(m: G.Mesh, y_top):
    """Carved gilt corbels at both ends of an opening's lintel, facing along the opening."""
    mask = bracket_mask(40, 24)
    for x, s in ((0.0, 1.0), (2.0, -1.0)):
        # The corbel's side profile lies in the plane of the panel (x-y), thickness along z.
        sub = G.Mesh("b")
        G.mask_extrude(sub, mask, (0.24, 0.40), (0.0, y_top - 0.40, -0.04), (1, 0, 0), (0, 1, 0), (0, 0, 1), 0.08,
                       "cr_bracket", "cr_gilt", edge_rsize=(2.0, 1.0))
        mm = G.scale(s, 1, 1)
        m.add(sub, mm, (x, 0.0, PLANE))


def panel() -> G.Mesh:
    m = G.Mesh("panel_crystal", pivot=(1.0, 0.5))
    f = G.Mesh("f")
    _rails_front(f, -0.5, 2.5)
    _band_front(f, -0.5, 2.5)
    _both_sides(f, m)
    m.contact_ao(0.0, CEIL, reach=0.3, strength=0.45)
    return m


def opening() -> G.Mesh:
    m = G.Mesh("opening_crystal", pivot=(1.0, 0.5))
    f = G.Mesh("f")
    _rails_front(f, -0.5, 2.5, skirting=False)
    _band_front(f, -0.5, 2.5)
    _both_sides(f, m)
    # Soffit board under the top rail.
    G.box(m, (-0.5, PANE_Y1 - 0.03, PLANE - 0.012), (2.5, PANE_Y1 - 0.025, PLANE + 0.012), "cr_wood_h", rsize=(4.0, 1.0),
          faces="y")
    _brackets(m, PANE_Y1 - 0.025)
    m.contact_ao(0.0, CEIL, reach=0.3, strength=0.45)
    return m


def _ring(m: G.Mesh, rect, prof, region, uvf=None, rsize=(3.0, 1.0), v_off=0.0, smooth_deg=40.0):
    """A moulding run round the inside of a rectangular opening in a wall facing +z, mitred at the
    corners: rect = (x0, y0, x1, y1); prof = [(inset from the edge, z), ...] going inward. uvf maps a
    point to region uv (planar projection); otherwise u runs along each side, v across the profile
    (both at physical scale on a region rsize blocks big, as G.rail)."""
    x0, y0, x1, y1 = rect
    sides = (((x0, y0), (x1, y0), (1, 0), (0, 1)), ((x1, y0), (x1, y1), (0, 1), (-1, 0)),
             ((x1, y1), (x0, y1), (-1, 0), (0, -1)), ((x0, y1), (x0, y0), (0, -1), (1, 0)))
    prof = np.asarray(prof, float)
    seg = np.diff(prof, axis=0)
    seglen = np.hypot(seg[:, 0], seg[:, 1])
    cum = np.concatenate([[0.0], np.cumsum(seglen)])
    cos_s = math.cos(math.radians(smooth_deg))
    rw, rh = rsize
    for cs, ce, e, inw in sides:
        cs, ce, e, inw = (np.asarray(a, float) for a in (cs, ce, e, inw))
        L = float(np.hypot(*(ce - cs)))

        def nrm2(i):
            # Outward normal of profile segment i in (inward, z) terms -> 3D.
            dd, dz = seg[i] / seglen[i]
            return np.array([-inw[0] * dz, -inw[1] * dz, dd])

        segn = [nrm2(i) for i in range(len(seg))]
        vn = []
        for j in range(len(prof)):
            cand = [segn[i] for i in (j - 1, j) if 0 <= i < len(seg)]
            if len(cand) == 2 and np.dot(cand[0], cand[1]) > cos_s:
                vn.append([G.norm(cand[0] + cand[1])] * 2)
            else:
                vn.append([cand[0], cand[-1]])
        for i in range(len(seg)):
            pts, uvs = [], []
            for j, end in ((i, 0), (i, 1), (i + 1, 1), (i + 1, 0)):
                ins, zz = prof[j]
                c = ce if end else cs
                xy = c + ins * inw + (-ins if end else ins) * e
                p = (xy[0], xy[1], zz)
                pts.append(p)
                if uvf is not None:
                    uvs.append(uvf(p))
                else:
                    along = (L - ins) if end else ins
                    uvs.append((along / rw, v_off + cum[j] / rh))
            ns = [vn[i][1], vn[i][1], vn[i + 1][0], vn[i + 1][0]]
            m.quad(pts, uvs, region, ns)


def solid() -> G.Mesh:
    """A solid wall segment between two columns: Victorian panelled walnut, the same on both faces.
    Skirting board, a frame of stiles, rails and a muntin round two low and two tall
    raised-and-fielded panels (gilt slips round the tall ones), a moulded chair rail with a gilt
    bead, and under the panel mesh's top rail a carved egg-and-dart frieze and a small cornice."""
    m = G.Mesh("solid_crystal", pivot=(1.0, 0.5))
    f = G.Mesh("f")
    z = PLANE

    def proj(p):
        return (p[0] + 0.5) / 3.0, 1.0 - p[1] / W_TOP

    def flat(x0, y0, x1, y1, dz, region="cr_wainscot"):
        pts = [(x0, y0, z + dz), (x1, y0, z + dz), (x1, y1, z + dz), (x0, y1, z + dz)]
        f.quad(pts, [proj(p) for p in pts], region, (0.0, 0.0, 1.0))

    # Panel ground behind everything, then the frame: three rails across (the middle one behind the
    # chair rail), stiles and muntin between them. The ends run into the columns.
    flat(-0.5, W_BASE, 2.5, W_FRIEZE[0], ZG)
    flat(-0.5, W_BASE, 2.5, W_DADO_Y[0], ZF)
    flat(-0.5, W_DADO_Y[1], 2.5, W_UPPER_Y[0], ZF)
    flat(-0.5, W_UPPER_Y[1], 2.5, W_FRIEZE[0], ZF)
    (xa0, xb0), (xa1, xb1) = W_OPEN_X
    for (ya, yb) in (W_DADO_Y, W_UPPER_Y):
        flat(-0.5, ya, xa0, yb, ZF)
        flat(xb0, ya, xa1, yb, ZF)
        flat(xb1, ya, 2.5, yb, ZF)
    for (xa, xb) in W_OPEN_X:
        for upper, (ya, yb) in ((False, W_DADO_Y), (True, W_UPPER_Y)):
            rect = (xa, ya, xb, yb)
            if upper:
                # Gilt ovolo slip round the tall panels.
                _ring(f, rect, [(0.0, z + ZF), (0.011, z + ZF - 0.007), (SIGHT, z + ZG)], "cr_gilt", rsize=(3.0, 1.0),
                      v_off=0.2)
            else:
                _ring(f, rect, [(0.0, z + ZF), (SIGHT, z + ZG)], "cr_wainscot", uvf=proj)
            # The raised field: bevelled up from the ground to a flat nearly flush with the frame.
            _ring(f, rect, [(MARGIN, z + ZG), (BEVEL, z + ZP)], "cr_wainscot", uvf=proj, smooth_deg=0.0)
            flat(xa + BEVEL, ya + BEVEL, xb - BEVEL, yb - BEVEL, ZP)
    # Skirting board on the panel mesh's own skirting, with an ogee top onto the frame.
    G.rail(f, [(z + 0.062, 0.10), (z + 0.062, W_SKIRT), (z + 0.065, W_SKIRT + 0.012), (z + 0.056, W_SKIRT + 0.024),
               (z + ZF, W_BASE)], -0.5, 2.5, "cr_wood_h", rsize=(4.0, 1.0), v_off=0.15)
    # Chair rail: a cove under a gilt bead, then a rounded nosing.
    c0, c1 = W_CHAIR
    G.rail(f, [(z + ZF, c0), (z + 0.066, c0 + 0.007), (z + 0.07, c0 + 0.023)], -0.5, 2.5, "cr_wood_h", rsize=(4.0, 1.0),
           v_off=0.55)
    G.rail(f, _bead_profile(0.012, 3, z + 0.07, c0 + 0.035), -0.5, 2.5, "cr_gilt", rsize=(3.0, 1.0), v_off=0.4)
    G.rail(f, [(z + 0.07, c0 + 0.047), (z + 0.088, c0 + 0.057), (z + 0.093, c0 + 0.08), (z + 0.082, c0 + 0.103),
               (z + ZF, c1)], -0.5, 2.5, "cr_wood_h", rsize=(4.0, 1.0), v_off=0.7)
    # Egg-and-dart frieze on a gilt bead, and the cornice it carries.
    fz = 0.048
    f.quad([(-0.5, W_FRIEZE[0], z + fz), (2.5, W_FRIEZE[0], z + fz), (2.5, W_FRIEZE[1], z + fz), (-0.5, W_FRIEZE[1], z + fz)],
           [(0.0, 1.0), (1.0, 1.0), (1.0, 0.0), (0.0, 0.0)], "cr_eggdart", (0.0, 0.0, 1.0))
    G.rail(f, _bead_profile(0.012, 3, z + fz, W_FRIEZE[0]), -0.5, 2.5, "cr_gilt", rsize=(3.0, 1.0), v_off=0.8)
    G.rail(f, [(z + fz, W_FRIEZE[1]), (z + 0.056, W_FRIEZE[1] + 0.01), (z + 0.062, W_FRIEZE[1] + 0.035),
               (z + 0.08, W_FRIEZE[1] + 0.07), (z + 0.09, W_FRIEZE[1] + 0.085), (z + 0.09, W_TOP), (z + 0.055, W_TOP)],
           -0.5, 2.5, "cr_wood_h", rsize=(4.0, 1.0), v_off=0.3)
    _both_sides(f, m)
    m.contact_ao(0.0, CEIL, reach=0.35, strength=0.4)
    return m


def floor() -> G.Mesh:
    m = G.Mesh("floor_crystal", pivot=(2.0, 2.0))
    y = 0.004
    m.quad([(CELL0, y, CELL1), (CELL1, y, CELL1), (CELL1, y, CELL0), (CELL0, y, CELL0)],
           [(0, 1), (1, 1), (1, 0), (0, 0)], "cr_floor", (0, 1, 0))
    return m


def ceiling() -> G.Mesh:
    """One cell of the coffered ceiling: the soffit (four painted coffers), a moulded frame round
    the cell sitting on the bands' cornices (half of it; the next cell draws the other half... of
    its own frame, so each grid line gets two), two crossing beams with gilt ovolos, and a carved
    gilt boss where they cross. Everything hangs from y = CEIL."""
    m = G.Mesh("ceiling_crystal", pivot=(2.0, 2.0))
    y = CEIL - 0.004
    m.quad([(CELL0, y, CELL0), (CELL1, y, CELL0), (CELL1, y, CELL1), (CELL0, y, CELL1)],
           [(0, 0), (1, 0), (1, 1), (0, 1)], "cr_ceiling", (0, -1, 0))
    c = np.array([2.0, 0.0, 2.0])
    # Perimeter frame along local x at z = CELL0 (rotated round the cell for the other three):
    # inner face, gilt ovolo, then the bottom back to the grid line.
    e = CELL0 + C_PB
    edge = G.Mesh("edge")
    G.rail(edge, [(e, CEIL), (e, CEIL - 0.05)], CELL0, CELL1, "cr_wood_h", rsize=(4.0, 1.0), v_off=0.2)
    G.rail(edge, [(e, CEIL - 0.05), (e - 0.006, CEIL - 0.08), (e - 0.024, CEIL - C_PD)], CELL0, CELL1, "cr_gilt",
           rsize=(3.0, 1.0), v_off=0.1)
    G.rail(edge, [(e - 0.024, CEIL - C_PD), (CELL0, CEIL - C_PD)], CELL0, CELL1, "cr_wood_h", rsize=(4.0, 1.0), v_off=0.35)
    for k in range(4):
        R = G.rot_y(k * math.pi / 2)
        m.add(edge, R, c - R @ c)
    # Cross beams through the cell centre, one along x and one along z: side, gilt ovolo, a
    # stepped-down soffit, ovolo, side.
    a, b = 2.0 + C_BW, 2.0 - C_BW
    beam = G.Mesh("beam")
    G.rail(beam, [(a, CEIL), (a, CEIL - 0.06)], CELL0, CELL1, "cr_wood_h", rsize=(4.0, 1.0), v_off=0.5)
    G.rail(beam, [(a, CEIL - 0.06), (a - 0.006, CEIL - 0.09), (a - 0.022, CEIL - 0.105)], CELL0, CELL1, "cr_gilt",
           rsize=(3.0, 1.0), v_off=0.3)
    G.rail(beam, [(a - 0.022, CEIL - 0.105), (a - 0.03, CEIL - 0.12), (a - 0.03, CEIL - C_BD), (b + 0.03, CEIL - C_BD),
                  (b + 0.03, CEIL - 0.12), (b + 0.022, CEIL - 0.105)], CELL0, CELL1, "cr_wood_h", rsize=(4.0, 1.0), v_off=0.65)
    G.rail(beam, [(b + 0.022, CEIL - 0.105), (b + 0.006, CEIL - 0.09), (b, CEIL - 0.06)], CELL0, CELL1, "cr_gilt",
           rsize=(3.0, 1.0), v_off=0.6)
    G.rail(beam, [(b, CEIL - 0.06), (b, CEIL)], CELL0, CELL1, "cr_wood_h", rsize=(4.0, 1.0), v_off=0.8)
    for k in range(2):
        R = G.rot_y(k * math.pi / 2)
        m.add(beam, R, c - R @ c)
    # The boss hides the beams' crossing (its rim reaches past the square's corners).
    G.lathe(m, [(0.10, CEIL - C_BD - 0.045), (0.15, CEIL - C_BD - 0.025), (0.165, CEIL - C_BD)], 8, "cr_gilt",
            centre=(2.0, 0.0, 2.0), v0=0.2, v1=0.0, u_turns=0.6)
    yb = CEIL - C_BD - 0.045
    pts = [(2.0 + 0.1 * math.cos(t), yb, 2.0 + 0.1 * math.sin(t)) for t in np.arange(8) * math.pi / 4]
    G.polygon(m, pts, [(0.5 + 0.5 * math.cos(t), 0.5 + 0.5 * math.sin(t)) for t in np.arange(8) * math.pi / 4],
              "cr_boss", (0.0, -1.0, 0.0))
    return m


def meshes():
    return [column(), panel(), opening(), solid(), floor(), ceiling()]
