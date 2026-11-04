"""Zone 4, the Funhouse: barber-striped posts with a ring of dead bulbs, painted frames with a
scalloped crest and bulb sockets (the distorting mirrors hang in these), boarded segments painted
like an old show front (a clown, a strongman, a fortune-teller, stars and ribbons, peeling and
rain-stained), a striped tent canvas ceiling sagging between the lines, peeling checker linoleum.

And zone 5, the Glass Labyrinth, the darkest place in the maze: rusted black steel (H-section posts
on gusseted base plates, riveted channels and header girders, a truss of angle iron in the band),
clear glass and mirrors, riveted steel plate where a way is closed, worn coin-pattern rubber mats
over the terrazzo, and a ceiling grid of wired glass with dead cables hanging out of it (its caged
work lamps, bulb_cage, are decor.py's).

Quad budgets (the maze holds hundreds of each): column 260, panel/opening 320, solid 160, floor 4,
ceiling 80. Detail that would cost thousands of quads (rivet rows, painted figures,
wire mesh) lives in the textures; real depth is kept for what reads in silhouette (mouldings,
sockets, flanges, gussets, rivet heads on plates)."""
from __future__ import annotations

import math

import numpy as np
from PIL import Image, ImageDraw

from . import geo as G
from . import mat as M
from .dims import BAND_Y0, CEIL, CELL0, CELL1, PANE_Y0, PANE_Y1, PLANE

SHEET = "funhouse"
RED = M.col("#8e2a21")
CREAM = M.col("#d3c39b")
MUSTARD = M.col("#b68a2e")
NAVY = M.col("#24365c")
TEAL = M.col("#2e6a66")
ORANGE = M.col("#c3642c")
ROSE = M.col("#c46a5c")
PURPLE = M.col("#2e2347")
INK = M.col("#1a1517")
SKIN = M.col("#c99b74")
WHITE = M.col("#e4dcc8")
GOLD = M.col("#c99d42")


# ------------------------------------------------------------------------------------------------
# Sign-painter's pen: shapes in block units, supersampled
# ------------------------------------------------------------------------------------------------

class _Pen:
    """Paints shapes in block units (y up) on a supersampled canvas. The boards are hand-painted
    show-front art, so they are drawn as clean cut-in shapes with outlines (as a sign painter
    works), then the whole board is aged at once."""

    def __init__(self, w, h, box, ss=3, bg=(0.0, 0.0, 0.0), mode="RGB"):
        self.w, self.h, self.ss, self.box, self.mode = w, h, ss, box, mode
        self.img = Image.new(mode, (w * ss, h * ss), self._c(bg))
        self.d = ImageDraw.Draw(self.img)

    def _c(self, col):
        if self.mode == "L":
            return int(round(255 * (float(col) if np.isscalar(col) else float(col[0]))))
        return tuple(int(round(255 * min(max(float(v), 0.0), 1.0))) for v in col)

    def _like(self, mode, img):
        q = _Pen.__new__(_Pen)
        q.w, q.h, q.ss, q.box, q.mode = self.w, self.h, self.ss, self.box, mode
        q.img = img
        q.d = ImageDraw.Draw(img)
        return q

    def P(self, x, y):
        x0, y0, x1, y1 = self.box
        return ((x - x0) / (x1 - x0) * self.w * self.ss, (y1 - y) / (y1 - y0) * self.h * self.ss)

    def L(self, d):
        x0, _, x1, _ = self.box
        return max(1, int(round(d / (x1 - x0) * self.w * self.ss)))

    def poly(self, pts, fill=None, line=None, width=0.015):
        P = [self.P(*p) for p in pts]
        if fill is not None:
            self.d.polygon(P, fill=self._c(fill))
        if line is not None:
            self.d.line(P + [P[0]], fill=self._c(line), width=self.L(width), joint="curve")

    def ellipse(self, cx, cy, rx, ry, fill=None, line=None, width=0.015):
        (ax, ay), (bx, by) = self.P(cx - rx, cy + ry), self.P(cx + rx, cy - ry)
        self.d.ellipse([ax, ay, bx, by], fill=None if fill is None else self._c(fill),
                       outline=None if line is None else self._c(line), width=self.L(width) if line is not None else 1)

    def line(self, pts, col, width):
        self.d.line([self.P(*p) for p in pts], fill=self._c(col), width=self.L(width), joint="curve")

    def star(self, cx, cy, ro, fill=GOLD, n=5, rot=0.0, inner=0.42, line=None):
        pts = [(cx + ro * (1 if k % 2 == 0 else inner) * math.sin(rot + k * math.pi / n),
                cy + ro * (1 if k % 2 == 0 else inner) * math.cos(rot + k * math.pi / n)) for k in range(2 * n)]
        self.poly(pts, fill, line, ro * 0.12)

    def clipped(self, clip, paint):
        """paint(pen) draws on a copy of the canvas; clip(mask pen) draws where it shows (white)."""
        layer = self._like(self.mode, self.img.copy())
        paint(layer)
        mk = self._like("L", Image.new("L", self.img.size, 0))
        clip(mk)
        self.img = Image.composite(layer.img, self.img, mk.img)
        self.d = ImageDraw.Draw(self.img)

    def rgb(self):
        return np.asarray(self.img.resize((self.w, self.h), Image.LANCZOS), float) / 255.0


def _arc(cx, cy, rx, ry, a0, a1, n=12):
    return [(cx + rx * math.cos(a0 + (a1 - a0) * k / n), cy + ry * math.sin(a0 + (a1 - a0) * k / n)) for k in range(n + 1)]


def _rays(P, cx, cy, n, cols, r=3.0, phase=0.0):
    for k in range(n):
        a0 = phase + 2 * math.pi * k / n
        a1 = phase + 2 * math.pi * (k + 1) / n
        P.poly([(cx, cy), (cx + r * math.cos(a0), cy + r * math.sin(a0)), (cx + r * math.cos(a1), cy + r * math.sin(a1))],
               cols[k % len(cols)])


# ------------------------------------------------------------------------------------------------
# Funhouse materials
# ------------------------------------------------------------------------------------------------

def _stripes(h, w):
    """Barber stripes (red and cream, wound round the post), the varnish yellowed, chipped to the
    grey primer where hands and shoulders brushed it."""
    r = M.rng(22601)
    X, Y = M.grid(h, w)
    turns = 4.0  # stripes per texture width round the post
    s = (X * 2 + Y * 3.5 * 0.55) * turns
    stripe = (s % 2) < 1
    rgb = np.where(stripe[..., None], RED, CREAM)
    rgb = rgb * (0.93 + 0.08 * M.fnoise(r, h, w, beta=2.6) * 0.5)[..., None]
    rgb = rgb * np.array([1.0, 0.95, 0.84])
    rub = np.exp(-((Y - 0.5) / 0.15) ** 2)
    prim = M.lerp(M.col("#7e786c"), M.col("#9a9384"), M.fnoise(r, h, w, beta=2.4) * 0.5 + 0.5)
    rgb = M.chipped_paint(rgb, prim, r, amount=0.12, scale=40, edge=rub * 0.5, grain=2.2)
    rgb = M.grime_gradient(rgb, r, 0.35, 0.05)
    return M.dust(rgb, 0.1, r)


def _paint(colour, seed):
    def f(h, w):
        r = M.rng(seed)
        rgb = M.lerp(colour * 0.85, colour * 1.05, M.fnoise(r, h, w, beta=2.6) * 0.5 + 0.5)
        under = M.wood(r, h, w, 192, palette=M.PINE, grain="v", varnish=0)
        rgb = M.chipped_paint(rgb, under, r, 0.14, 40, grain=2.2)
        return M.dust(M.grime_gradient(rgb, r, 0.25, 0.05), 0.15, r)
    return f


def _frame(h, w):
    """The painted frame moulding: mustard with a red bead and cream dots (like a show front)."""
    r = M.rng(22602)
    X, Y = M.grid(h, w)
    rgb = np.broadcast_to(MUSTARD, (h, w, 3)).copy()
    bead = np.abs(Y - 0.5) < 0.12
    rgb = np.where(bead[..., None], RED, rgb)
    dots = (np.hypot(((X * 64) % 1 - 0.5) / 2.0, (Y - 0.5)) < 0.06)
    rgb = np.where(dots[..., None], CREAM, rgb)
    under = M.wood(r, h, w, 128, palette=M.PINE, grain="h", varnish=0)
    rgb = M.chipped_paint(rgb, under, r, 0.18, 30, grain=2.0)
    return M.dust(M.grime_gradient(rgb, r, 0.2, 0.05), 0.15, r)


CREST_W, CREST_H = 3.0, 0.78
CREST_Y = BAND_Y0 + 0.02          # the crest stands on the frame's top rail
CREST_D = 0.06                    # board thickness
MED_Y, MED_RX, MED_RY = 0.42, 0.42, 0.34      # medallion (crest-local, x 0 = segment centre)
HOLE_RX, HOLE_RY = 0.28, 0.22                 # the round opening a bulb once lit


def _crest_top(x):
    """Height of the crest's outline above its foot at crest-local x (-1.5..1.5): scallops along
    the board, a hump in the middle, the medallion rising out of it."""
    per = 0.5
    t = ((x + 1.5) % per - per / 2) / (per / 2)
    scal = 0.32 + 0.1 * np.sqrt(np.clip(1 - t ** 2, 0, 1))
    hump = 0.3 + 0.32 * np.clip(1 - x ** 2, 0, 1)
    med = np.where(np.abs(x) < MED_RX, MED_Y + MED_RY * np.sqrt(np.clip(1 - (x / MED_RX) ** 2, 0, 1)), 0.0)
    return np.maximum(np.maximum(scal, hump), med)


def crest_mask(cols, rows):
    """The crest over a funhouse panel (row 0 = top), the same outline the geometry follows."""
    Y, X = np.mgrid[0:rows, 0:cols].astype(float)
    x = (X + 0.5) / cols * CREST_W - 1.5
    y = (1 - (Y + 0.5) / rows) * CREST_H
    hole = np.hypot(x / HOLE_RX, (y - MED_Y) / HOLE_RY) < 1
    return (y < _crest_top(x)) & ~hole


def _crest(h, w):
    """Painted crest: red board, cream edging, gold dots along the scallops, stars on the hump, a
    mustard medallion with a sunburst round its opening (the hole a bulb once lit)."""
    r = M.rng(22603)
    m = crest_mask(w, h)
    P = _Pen(w, h, (-1.5, 0.0, 1.5, CREST_H), ss=3, bg=RED)
    # Painted bead just inside the outline (drawn as the outline offset down).
    xs = np.linspace(-1.5, 1.5, 121)
    P.line([(x, _crest_top(x) - 0.055) for x in xs], CREAM, 0.022)
    P.line([(x, 0.06) for x in (-1.5, 1.5)], MUSTARD, 0.03)
    for k in range(12):
        x = -1.5 + 0.125 + 0.25 * k
        if abs(x) > 0.85:
            P.ellipse(x, _crest_top(x) - 0.1, 0.016, 0.016, fill=GOLD)
    for x in (-0.62, 0.62):
        P.star(x, 0.3, 0.07, GOLD, rot=0.2 * np.sign(x))
    for x in (-1.05, 1.05):
        P.star(x, 0.17, 0.045, CREAM)
    P.ellipse(0.0, MED_Y, MED_RX, MED_RY, fill=MUSTARD, line=INK, width=0.012)
    P.clipped(lambda q: q.ellipse(0.0, MED_Y, MED_RX * 0.93, MED_RY * 0.93, fill=1.0),
              lambda q: _rays(q, 0.0, MED_Y, 24, (MUSTARD, M.col("#d8b25a"))))
    P.ellipse(0.0, MED_Y, MED_RX * 0.86, MED_RY * 0.86, line=NAVY, width=0.03)
    P.ellipse(0.0, MED_Y, HOLE_RX * 1.08, HOLE_RY * 1.08, fill=INK)
    rgb = P.rgb()
    under = M.wood(r, h, w, w / 3.0, palette=M.PINE, grain="h", varnish=0)
    rgb = M.lerp(rgb, rgb.mean(axis=2, keepdims=True), 0.2)
    rgb = M.chipped_paint(rgb, under, r, 0.16, 30, grain=2.0)
    rgb = M.grime_gradient(rgb, r, 0.1, 0.2, streak=0.6)
    return M.rgba(M.dust(rgb, 0.2, r), m.astype(float))


def _canvas(h, w):
    """Tent canvas, red and cream stripes, sun-faded, water-stained, a few holes (dark)."""
    r = M.rng(22604)
    X, Y = M.grid(h, w)
    stripe = ((X * 6) % 1) < 0.5
    rgb = np.where(stripe[..., None], RED * 0.95, CREAM)
    rgb = M.lerp(rgb, M.col("#a8987a"), 0.25)
    rgb = rgb * (0.9 + 0.18 * M.canvas_weave(r, h, w, w / 3.0))[..., None]
    rgb = rgb * (0.94 + 0.1 * M.fnoise(r, h, w, beta=3.0))[..., None]
    rgb = M.damp_stains(rgb, r, 4, tone=(0.3, 0.22, 0.12), strength=0.45)
    rgb = M.grime_gradient(rgb, r, 0.0, 0.0, streak=0.3)
    holes = (M.fnoise(r, h, w, beta=2.2) > 2.6)
    rgb = np.where(holes[..., None], M.col("#050404"), rgb)
    return M.dust(rgb, 0.12, r)


def _floor(h, w):
    """Checker linoleum (black and cream), curling and torn away in patches to the boards."""
    r = M.rng(22605)
    ppb = w / 3.0
    Y, X = np.mgrid[0:h, 0:w].astype(float)
    s = ppb / 3.0
    checker = ((np.floor(X / s) + np.floor(Y / s)) % 2) == 0
    rgb = np.where(checker[..., None], M.col("#1b1917"), M.col("#c4b89c"))
    rgb = rgb * (0.92 + 0.1 * (M.fnoise(r, h, w, beta=2.6) * 0.5 + 0.5))[..., None]
    joint = ((X % s) < 1) | ((Y % s) < 1)
    rgb = np.where(joint[..., None], rgb * 0.7, rgb)
    boards = M.wood(r, h, w, ppb, palette=(M.col("#4e3b27"), M.col("#6a5236"), M.col("#2f2317")), grain="h", varnish=0)
    # Tears: a few large ragged patches (smooth noise), not a speckle.
    n = M.fnoise(r, h, w, beta=3.0) + 0.25 * M.fnoise(r, h, w, beta=1.8)
    torn = n > 1.5
    dist = M.edge_distance(~torn)
    rgb = np.where(torn[..., None], boards, rgb)
    rim = (~torn) & (dist < 3)
    rgb = np.where(rim[..., None], M.col("#8b826f"), rgb)
    # Old glue residue blotches on the boards.
    rgb = np.where((torn & (M.fnoise(r, h, w, beta=2.4) > 0.5))[..., None], M.col("#3a2c1a"), rgb)
    sc = M.scratches(r, h, w, 80, (0.01, 0.06))
    rgb = M.lerp(rgb, rgb * 0.6, sc * 0.5)
    return M.dust(rgb, 0.18, r)


def _sockets(h, w):
    r = M.rng(22606)
    return M.dust(M.brass(r, h, w, 128, tarnish=0.9), 0.2, r)


def _bulb_dead(h, w):
    """A dead bulb's glass: grey, blackened inside toward the tip."""
    X, Y = M.grid(h, w)
    return M.lerp(M.col("#8a857c"), M.col("#3a3631"), M.smooth(0.7, 0.2, Y))


def _socket_hole(h, w):
    """The inside of an empty socket: black, a glint of the brass screw thread."""
    X, Y = M.grid(h, w)
    return M.lerp(M.col("#0d0b09"), M.col("#4a3a1e"), M.smooth(0.3, 0.0, np.minimum(Y, 1 - Y)) * 0.6)


# The boarded segment (solid_funhouse): a board filling the frame, its two faces painted.
SOL_X0, SOL_X1 = -0.5, 2.5
SOL_Y0, SOL_Y1 = 0.11, PANE_Y1 - 0.03
# The raised moulding round the painted field (outer edge) and its width.
MOULD = (-0.24, 0.34, 2.24, 2.64)
MOULD_W = 0.08
# Bulb sockets in the bands outside the moulding: every one is painted (a brass ring), the real
# ones (some with a dead bulb, some empty) sit on a few of them.
SOCK_XS = (-0.125, 0.25, 0.625, 1.0, 1.375, 1.75, 2.125)
SOCK_Y = (0.235, 2.765)
# (x, y, has a bulb) per face: front (-z) then back (+z), in the face's own left-to-right x.
REAL_SOCKS = (((0.25, 2.765, True), (1.0, 2.765, False), (1.75, 2.765, True), (1.0, 0.235, False)),
              ((0.25, 2.765, False), (1.0, 2.765, True), (1.75, 2.765, True), (1.0, 0.235, False)))


def _board_border(P, ground, band):
    """Outside the moulding: the band colour with red swags hanging between the sockets, a navy
    pinstripe, and a painted brass ring with a soot mark at every socket position."""
    x0, y0, x1, y1 = MOULD
    P.poly([(SOL_X0, SOL_Y0), (SOL_X1, SOL_Y0), (SOL_X1, SOL_Y1), (SOL_X0, SOL_Y1)], band)
    for k in range(len(SOCK_XS) - 1):
        a, b = SOCK_XS[k] + 0.04, SOCK_XS[k + 1] - 0.04
        c = 0.5 * (a + b)
        P.poly([(a, SOL_Y1)] + _arc(c, SOL_Y1, (b - a) / 2, 0.15, math.pi, 2 * math.pi, 10)[1:-1] + [(b, SOL_Y1)], RED,
               INK, 0.008)
        P.poly([(a, SOL_Y0)] + _arc(c, SOL_Y0, (b - a) / 2, 0.12, math.pi, 0.0, 10)[1:-1] + [(b, SOL_Y0)], RED, INK, 0.008)
    for x in (x0 - 0.035, x1 + 0.035):
        P.line([(x, y0 - 0.035), (x, y1 + 0.035)], NAVY, 0.012)
    for y in (y0 - 0.035, y1 + 0.035):
        P.line([(x0 - 0.035, y), (x1 + 0.035, y)], NAVY, 0.012)
    for y in SOCK_Y:
        for x in SOCK_XS:
            # Soot from years of hot bulbs, rising above each socket.
            P.ellipse(x, y + 0.05, 0.035, 0.06, fill=M.lerp(band, INK, 0.25))
            P.ellipse(x, y, 0.034, 0.034, fill=GOLD * 0.8, line=INK, width=0.006)
            P.ellipse(x, y, 0.018, 0.018, fill=INK)
    # The field inside the moulding, with a gold pinstripe just inside it.
    fx0, fy0, fx1, fy1 = x0 + MOULD_W, y0 + MOULD_W, x1 - MOULD_W, y1 - MOULD_W
    P.poly([(fx0, fy0), (fx1, fy0), (fx1, fy1), (fx0, fy1)], ground)
    i = 0.045
    P.poly([(fx0 + i, fy0 + i), (fx1 - i, fy0 + i), (fx1 - i, fy1 - i), (fx0 + i, fy1 - i)], None, GOLD, 0.012)
    return fx0, fy0, fx1, fy1


def _corner_fans(P, f, rad, cols):
    fx0, fy0, fx1, fy1 = f
    for (cx, cy, a0) in ((fx0, fy0, 0.0), (fx1, fy0, math.pi / 2), (fx1, fy1, math.pi), (fx0, fy1, 1.5 * math.pi)):
        for k in range(5):
            a = a0 + math.pi / 2 * k / 5
            b = a0 + math.pi / 2 * (k + 1) / 5
            P.poly([(cx, cy)] + _arc(cx, cy, rad, rad, a, b, 3), cols[k % 2])
        P.line(_arc(cx, cy, rad, rad, a0, a0 + math.pi / 2, 10), GOLD, 0.018)


def _clown(P):
    """Face A: a laughing clown's head in a sunburst cartouche, a blank ribbon under it, stars."""
    f = _board_border(P, NAVY, CREAM)
    _corner_fans(P, f, 0.24, (RED, CREAM))
    cx, cy = 1.0, 1.5
    for (x, y, s) in ((-0.06, 1.05, 0.06), (-0.06, 1.95, 0.06), (2.06, 1.05, 0.06), (2.06, 1.95, 0.06), (-0.05, 1.5, 0.04),
                      (2.05, 1.5, 0.04)):
        P.star(x, y, s, GOLD)
    P.clipped(lambda q: q.ellipse(cx, cy, 0.9, 0.97, fill=1.0),
              lambda q: _rays(q, cx, cy + 0.05, 28, (M.col("#d6c69c"), M.col("#c0604c"))))
    P.ellipse(cx, cy, 0.9, 0.97, line=MUSTARD, width=0.06)
    P.ellipse(cx, cy, 0.85, 0.92, line=RED, width=0.016)
    for k in range(18):
        a = 2 * math.pi * k / 18
        P.ellipse(cx + 0.9 * math.cos(a), cy + 0.97 * math.sin(a), 0.014, 0.014, fill=CREAM)
    # Ruffle collar: two rows of scallops fanning under the chin.
    for row, (ry, cols) in enumerate(((cy - 0.5, (MUSTARD, RED)), (cy - 0.42, (CREAM, TEAL)))):
        n = 9 - row * 2
        for k in range(n):
            t = (k - (n - 1) / 2) / ((n - 1) / 2)
            x = cx + t * (0.42 - row * 0.08)
            y = ry + 0.06 * (1 - t * t)
            P.ellipse(x, y, 0.075, 0.065, fill=cols[k % 2], line=INK, width=0.01)
    # Hair tufts.
    for s in (-1, 1):
        for (dx, dy, rr) in ((0.34, 0.16, 0.09), (0.41, 0.03, 0.1), (0.36, -0.11, 0.085)):
            P.ellipse(cx + s * dx, cy + 0.05 + dy, rr, rr * 0.9, fill=ORANGE, line=INK, width=0.01)
    P.ellipse(cx, cy + 0.03, 0.32, 0.38, fill=WHITE, line=INK, width=0.014)
    # Cone hat, tipped, with stripes and a pompom.
    hat = [(cx - 0.2, cy + 0.33), (cx + 0.2, cy + 0.36), (cx + 0.1, cy + 0.92)]
    P.clipped(lambda q: q.poly(hat, 1.0),
              lambda q: [q.poly([(cx - 0.5, cy + 0.3 + 0.14 * k), (cx + 0.5, cy + 0.42 + 0.14 * k),
                                 (cx + 0.5, cy + 0.49 + 0.14 * k), (cx - 0.5, cy + 0.37 + 0.14 * k)],
                                TEAL if k % 2 else MUSTARD) for k in range(-1, 6)])
    P.poly(hat, None, INK, 0.012)
    P.ellipse(cx + 0.1, cy + 0.94, 0.065, 0.06, fill=RED, line=INK, width=0.01)
    for k in range(5):
        P.ellipse(cx - 0.16 + 0.08 * k, cy + 0.34 + 0.0075 * k, 0.045, 0.03, fill=CREAM, line=INK, width=0.008)
    # Eyes: painted diamonds with pupils, arched brows.
    for s in (-1, 1):
        ex, ey = cx + s * 0.13, cy + 0.13
        P.poly([(ex, ey + 0.1), (ex + 0.045, ey), (ex, ey - 0.08), (ex - 0.045, ey)], NAVY)
        P.ellipse(ex, ey, 0.02, 0.028, fill=INK)
        P.line(_arc(ex, ey + 0.08, 0.06, 0.05, 0.3, math.pi - 0.3, 8), INK, 0.014)
        P.ellipse(cx + s * 0.2, cy - 0.08, 0.06, 0.04, fill=ROSE)
    # The grin: a red crescent with cross-stroke dimples.
    up = _arc(cx, cy - 0.09, 0.2, 0.06, math.pi, 2 * math.pi, 12)
    lo = _arc(cx, cy - 0.09, 0.2, 0.19, 2 * math.pi, math.pi, 12)
    P.poly(up + lo, RED, INK, 0.012)
    P.poly(_arc(cx, cy - 0.2, 0.08, 0.035, math.pi, 2 * math.pi, 8), M.col("#e6d2c0"))
    for s in (-1, 1):
        P.line([(cx + s * 0.2, cy - 0.06), (cx + s * 0.235, cy - 0.13)], INK, 0.01)
    P.ellipse(cx, cy + 0.02, 0.068, 0.062, fill=RED, line=INK, width=0.01)
    P.ellipse(cx - 0.02, cy + 0.045, 0.02, 0.014, fill=M.col("#e9b3a0"))
    # Ribbon (left blank: no lettering on maze architecture, which the mirrors flip).
    band = _arc(cx, 1.42, 0.82, 0.82, math.pi * 1.32, math.pi * 1.68, 12)
    top = [(x, y + 0.17) for (x, y) in band]
    for s in (-1, 1):
        ex = cx + s * 0.8
        P.poly([(ex, 0.72), (ex + s * 0.26, 0.70), (ex + s * 0.17, 0.62), (ex + s * 0.27, 0.52), (ex, 0.56)],
               M.col("#6e1e18"), INK, 0.01)
    P.poly(band + top[::-1], RED, INK, 0.012)
    P.line([(x, y + 0.03) for (x, y) in band], CREAM, 0.012)
    P.line([(x, y + 0.14) for (x, y) in band], CREAM, 0.012)
    for (x, y) in band[1:-1:2]:
        P.poly([(x, y + 0.085 + 0.03), (x + 0.025, y + 0.085), (x, y + 0.085 - 0.03), (x - 0.025, y + 0.085)], GOLD)


def _strongman(P, cx):
    """A moustached strongman in a leopard leotard holding a barbell over his head, on a rayed
    teal ground in an arched niche."""
    ink = INK
    skin = M.col("#d7a982")
    # Stage block.
    P.poly([(cx - 0.38, 0.5), (cx + 0.38, 0.5), (cx + 0.38, 0.62), (cx - 0.38, 0.62)], M.col("#5c1a14"), ink, 0.01)
    P.line([(cx - 0.38, 0.61), (cx + 0.38, 0.61)], CREAM, 0.012)
    # Legs in red-and-cream striped tights, black boots, planted wide.
    def tights(q):
        q.poly([(cx - 0.4, 0.6), (cx + 0.4, 0.6), (cx + 0.4, 1.2), (cx - 0.4, 1.2)], CREAM)
        for y in np.arange(0.66, 1.15, 0.08):
            q.poly([(cx - 0.4, y), (cx + 0.4, y), (cx + 0.4, y + 0.04), (cx - 0.4, y + 0.04)], RED)

    for s in (-1, 1):
        leg = [(cx + s * 0.015, 1.13), (cx + s * 0.13, 1.13), (cx + s * 0.2, 0.69), (cx + s * 0.1, 0.69)]
        P.clipped(lambda q, l=leg: q.poly(l, 1.0), tights)
        P.poly(leg, None, ink, 0.012)
        P.poly([(cx + s * 0.085, 0.72), (cx + s * 0.215, 0.72), (cx + s * 0.25, 0.62), (cx + s * 0.07, 0.62)], ink)
    # Arms up (a dark outline under the skin strokes), biceps.
    for s in (-1, 1):
        arm = [(cx + s * 0.16, 1.56), (cx + s * 0.31, 1.79), (cx + s * 0.27, 2.04)]
        P.line(arm, ink, 0.1)
        P.line(arm, skin, 0.072)
        P.ellipse(cx + s * 0.25, 1.69, 0.055, 0.07, fill=skin)
        P.line(_arc(cx + s * 0.25, 1.69, 0.04, 0.05, -0.6 if s > 0 else math.pi - 0.9, 0.9 if s > 0 else math.pi + 0.6, 6),
               ink, 0.01)
        P.ellipse(cx + s * 0.27, 2.06, 0.042, 0.036, fill=skin, line=ink, width=0.01)
    # Barbell.
    P.line([(cx - 0.4, 2.06), (cx + 0.4, 2.06)], M.col("#8a8780"), 0.026)
    for s in (-1, 1):
        P.ellipse(cx + s * 0.38, 2.06, 0.13, 0.13, fill=ink, line=M.col("#58545e"), width=0.016)
        P.ellipse(cx + s * 0.38, 2.06, 0.05, 0.05, line=M.col("#6a6670"), width=0.01)
    # Torso: a broad V of skin, the one-shouldered leopard leotard over it, belt and buckle.
    P.poly([(cx - 0.12, 1.15), (cx + 0.12, 1.15), (cx + 0.22, 1.58), (cx + 0.12, 1.63), (cx - 0.12, 1.63),
            (cx - 0.22, 1.58)], skin, ink, 0.012)
    P.line(_arc(cx - 0.07, 1.5, 0.07, 0.05, math.pi * 1.1, math.pi * 1.9, 6), ink, 0.008)
    P.line(_arc(cx + 0.07, 1.5, 0.07, 0.05, math.pi * 1.1, math.pi * 1.9, 6), ink, 0.008)
    leo = [(cx - 0.135, 1.1), (cx + 0.135, 1.1), (cx + 0.19, 1.47), (cx + 0.15, 1.6), (cx + 0.09, 1.6), (cx + 0.05, 1.44),
           (cx - 0.17, 1.4)]
    P.poly(leo, M.col("#d39a3a"), ink, 0.012)
    for (dx, dy) in ((-0.1, 1.33), (0.03, 1.36), (0.13, 1.46), (-0.04, 1.25), (0.09, 1.27), (-0.1, 1.2), (0.12, 1.53),
                     (0.02, 1.15)):
        P.ellipse(cx + dx, dy, 0.024, 0.017, fill=M.col("#3a2412"))
    P.poly([(cx - 0.145, 1.16), (cx + 0.145, 1.16), (cx + 0.15, 1.225), (cx - 0.15, 1.225)], M.col("#4a2a14"), ink, 0.008)
    P.poly([(cx - 0.035, 1.155), (cx + 0.035, 1.155), (cx + 0.035, 1.23), (cx - 0.035, 1.23)], GOLD, ink, 0.006)
    # Head: bald and shining, eyes, handlebar moustache, a kiss-curl.
    P.poly([(cx - 0.045, 1.6), (cx + 0.045, 1.6), (cx + 0.045, 1.67), (cx - 0.045, 1.67)], skin)
    P.ellipse(cx, 1.75, 0.085, 0.095, fill=skin, line=ink, width=0.012)
    P.ellipse(cx - 0.025, 1.79, 0.03, 0.02, fill=M.col("#f0d2b4"))
    P.ellipse(cx - 0.032, 1.76, 0.011, 0.013, fill=ink)
    P.ellipse(cx + 0.032, 1.76, 0.011, 0.013, fill=ink)
    for s in (-1, 1):
        P.line([(cx, 1.72), (cx + s * 0.05, 1.71), (cx + s * 0.09, 1.725), (cx + s * 0.1, 1.755)], ink, 0.018)
    P.line(_arc(cx + 0.01, 1.83, 0.028, 0.02, 0.0, math.pi * 1.3, 6), ink, 0.01)


def _fortune(P, cx):
    """A fortune-teller in a headscarf, her face in shadow but for the eyes, hands round a
    glowing crystal ball on a fringed table, a crescent moon and stars behind."""
    P.ellipse(cx + 0.26, 2.22, 0.1, 0.1, fill=CREAM)
    P.ellipse(cx + 0.31, 2.25, 0.085, 0.085, fill=PURPLE)
    for (dx, y, s) in ((-0.3, 2.25, 0.04), (-0.16, 2.05, 0.025), (0.33, 1.9, 0.03), (-0.34, 1.75, 0.025),
                       (0.36, 1.6, 0.02), (0.05, 2.32, 0.02), (-0.08, 2.18, 0.018)):
        P.star(cx + dx, y, s, M.col("#d8c37e"))
    shadow = M.col("#140f1c")
    rim = M.col("#6a5a8a")
    # The scarf's tails behind the shoulders.
    P.poly([(cx + 0.06, 1.74), (cx + 0.24, 1.6), (cx + 0.3, 1.32), (cx + 0.18, 1.36), (cx + 0.1, 1.58)], M.col("#6a1c2c"),
           INK, 0.008)
    # Shawl: a bell from rounded shoulders, a lit rim so she stands off the night ground.
    body = _arc(cx, 1.2, 0.3, 0.3, 0.0, math.pi, 14) + [(cx - 0.4, 0.88), (cx + 0.4, 0.88)]
    P.poly(body, shadow, rim, 0.014)
    for k in range(11):
        x = cx - 0.27 + 0.054 * k
        y = 1.2 + 0.3 * math.sqrt(max(0.0, 1 - ((x - cx) / 0.3) ** 2)) - 0.16
        P.line([(x, y), (x + 0.004, y - 0.07)], M.col("#7a3450"), 0.012)
    P.line(_arc(cx, 1.36, 0.27, 0.12, math.pi * 1.05, math.pi * 1.95, 12), M.col("#7a3450"), 0.016)
    # Head in shadow, scarf over it with coins on the brow, hoop earrings, two pale eyes.
    P.poly([(cx - 0.05, 1.46), (cx + 0.05, 1.46), (cx + 0.05, 1.56), (cx - 0.05, 1.56)], shadow)
    P.ellipse(cx, 1.65, 0.095, 0.115, fill=shadow, line=rim, width=0.01)
    P.poly(_arc(cx, 1.66, 0.125, 0.15, -0.15, math.pi + 0.15, 14), M.col("#8a2436"), INK, 0.01)
    P.ellipse(cx + 0.12, 1.66, 0.04, 0.035, fill=M.col("#8a2436"), line=INK, width=0.008)
    for k in range(7):
        t = (k - 3) / 3
        P.ellipse(cx + 0.1 * t, 1.69 - 0.02 * t * t, 0.013, 0.013, fill=GOLD)
    for s in (-1, 1):
        P.ellipse(cx + s * 0.095, 1.57, 0.022, 0.028, line=GOLD, width=0.009)
        P.poly([(cx + s * 0.065, 1.635), (cx + s * 0.022, 1.645), (cx + s * 0.04, 1.625)], M.col("#d6e2dc"))
    P.ellipse(cx, 1.56, 0.06, 0.035, fill=M.col("#3a3a58"))
    # Arms down to the hands round the ball.
    for s in (-1, 1):
        P.poly([(cx + s * 0.27, 1.34), (cx + s * 0.19, 1.3), (cx + s * 0.16, 1.03), (cx + s * 0.25, 1.0)], shadow, rim,
               0.008)
    # Table drape with a gold fringe.
    P.poly([(cx - 0.42, 0.5), (cx + 0.42, 0.5), (cx + 0.42, 0.9), (cx - 0.42, 0.9)], M.col("#7a2220"), INK, 0.01)
    for k in range(11):
        x = cx - 0.4 + 0.08 * k
        P.poly([(x, 0.62), (x + 0.04, 0.55), (x + 0.08, 0.62)], GOLD)
    P.line([(cx - 0.42, 0.62), (cx + 0.42, 0.62)], GOLD, 0.014)
    # Crystal ball: a glow painted in rings, a stand, the highlight.
    P.poly([(cx - 0.1, 0.9), (cx + 0.1, 0.9), (cx + 0.06, 0.95), (cx - 0.06, 0.95)], GOLD, INK, 0.008)
    for k in range(6):
        t = k / 5
        rr = 0.25 - 0.12 * t
        P.ellipse(cx, 1.08, rr, rr, fill=M.lerp(M.col("#2a2440"), M.col("#7fa6b4"), 0.15 + 0.2 * t))
    P.ellipse(cx, 1.08, 0.13, 0.13, fill=M.col("#a9cdd2"), line=INK, width=0.01)
    P.ellipse(cx, 1.06, 0.07, 0.07, fill=M.col("#d8ecea"))
    P.ellipse(cx - 0.05, 1.13, 0.025, 0.018, fill=WHITE)
    for s in (-1, 1):
        P.ellipse(cx + s * 0.13, 1.02, 0.055, 0.032, fill=M.col("#b99a86"), line=INK, width=0.008)


def _showfront(h, w):
    """Face B: two arched niches either side of a twisted-ribbon pilaster, a strongman and a
    fortune-teller, stars in the spandrels."""
    P = _Pen(w, h, (SOL_X0, SOL_Y0, SOL_X1, SOL_Y1), ss=3)
    f = _board_border(P, RED, MUSTARD * 0.95)
    fx0, fy0, fx1, fy1 = f
    spring = 1.98
    for k, (a, b) in enumerate(((fx0 + 0.09, 0.94), (1.06, fx1 - 0.09))):
        rr = (b - a) / 2
        pts = [(a, fy0 + 0.08), (b, fy0 + 0.08), (b, spring)] + _arc((a + b) / 2, spring, rr, rr, 0.0, math.pi, 16)[1:] + \
              [(a, spring)]
        if k == 0:
            P.clipped(lambda q, p=pts: q.poly(p, 1.0),
                      lambda q, a=a, b=b: _rays(q, (a + b) / 2, 1.6, 22, (M.col("#c48a3a"), M.col("#9b4a26"))))
            _strongman(P, (a + b) / 2)
        else:
            P.poly(pts, PURPLE)
            _fortune(P, (a + b) / 2)
        P.poly(pts, None, CREAM, 0.035)
        P.poly(pts, None, INK, 0.008)
    # Twisted ribbon pilaster between the niches.
    px0, px1 = 0.95, 1.05
    P.clipped(lambda q: q.poly([(px0, fy0 + 0.02), (px1, fy0 + 0.02), (px1, fy1 - 0.02), (px0, fy1 - 0.02)], 1.0),
              lambda q: [q.poly([(px0 - 0.1, y), (px1 + 0.1, y + 0.12), (px1 + 0.1, y + 0.18), (px0 - 0.1, y + 0.06)],
                                CREAM if k % 2 else RED) for k, y in enumerate(np.arange(fy0 - 0.2, fy1 + 0.1, 0.06))])
    P.poly([(px0, fy0 + 0.02), (px1, fy0 + 0.02), (px1, fy1 - 0.02), (px0, fy1 - 0.02)], None, INK, 0.008)
    for x in (fx0 + 0.15, fx1 - 0.15):
        P.star(x, fy1 - 0.13, 0.06, GOLD)
    P.star(1.0, fy1 - 0.08, 0.045, CREAM)
    _corner_fans(P, f, 0.14, (MUSTARD, CREAM))
    return P


def _age_board(rgb, r, ppb):
    """Decades on a show front: chalky sun-fade (most at the top), brush strokes, fine paint
    cracks, flakes peeled off to primer and grey plywood, rain tide marks and runs from the top,
    grime kicked up from the floor, dust."""
    h, w = rgb.shape[:2]
    X, Y = M.grid(h, w)
    rgb = rgb * (1 + 0.05 * M.fnoise(r, h, w, beta=1.8, aniso=(1.0, 5.0)))[..., None]
    rgb = M.lerp(rgb, rgb.mean(axis=2, keepdims=True), 0.28)
    fade = 0.16 + 0.16 * M.smooth(0.9, 0.0, Y) + 0.06 * M.fnoise(r, h, w, beta=3.0)
    rgb = M.lerp(rgb, M.col("#b5aa90"), np.clip(fade, 0, 0.5)[..., None])
    cr = M.craquelure(r, h, w, cells=int(w * h / 700), width=0.8)
    rgb = M.lerp(rgb, rgb * 0.72, cr * 0.3)
    ply = M.wood(r, h, w, ppb, palette=(M.col("#7d705c"), M.col("#9a8c72"), M.col("#544a3c")), grain="h", varnish=0)
    primer = M.lerp(M.col("#a39a86"), M.col("#c0b7a1"), M.fnoise(r, h, w, beta=2.6) * 0.5 + 0.5)
    under = M.lerp(primer, ply, M.smooth(-0.2, 0.5, M.fnoise(r, h, w, beta=3.0))[..., None])
    edge = np.clip(M.smooth(0.06, 0.0, np.minimum(X, 1 - X)) + M.smooth(0.85, 1.0, Y) * 0.6, 0, 1)
    rgb = M.chipped_paint(rgb, under, r, amount=0.12, scale=70, edge=edge * 0.6, grain=2.1)
    rgb = M.damp_stains(rgb, r, 3, tone=(0.3, 0.24, 0.15), strength=0.4)
    # Rain runs from the top edge: long vertical streaks, darker, fading downward.
    runs = M.fnoise(r, h, w, beta=2.0, aniso=(1.0, 18.0)) * 0.5 + 0.5
    run = np.clip((runs - 0.58) * 3.0, 0, 1) * M.smooth(0.95, 0.05, Y)
    rgb = M.lerp(rgb, rgb * np.array([0.62, 0.55, 0.45]), run * 0.55)
    rgb = M.grime_gradient(rgb, r, 0.32, 0.1, streak=0.4)
    return M.dust(rgb, 0.1, r)


def _solid_a(h, w):
    r = M.rng(22620)
    P = _Pen(w, h, (SOL_X0, SOL_Y0, SOL_X1, SOL_Y1), ss=3)
    _clown(P)
    return _age_board(P.rgb(), r, w / (SOL_X1 - SOL_X0))


def _solid_b(h, w):
    r = M.rng(22621)
    P = _showfront(h, w)
    return _age_board(P.rgb(), r, w / (SOL_X1 - SOL_X0))


# ------------------------------------------------------------------------------------------------
# Glass Labyrinth materials
# ------------------------------------------------------------------------------------------------

def _rust_runs(rgb, r, src, amount=0.6):
    """Rust bleeding down from src (0..1 mask of rivets, seams, edges): orange-brown runs under
    each source, longer in damp patches."""
    h, w = rgb.shape[:2]
    acc = np.zeros((h, w))
    s = src.copy()
    for k in range(1, 28):
        s = np.roll(s, 1, axis=0) * 0.92
        s[0] = 0
        acc = np.maximum(acc, s)
    acc *= np.clip(M.fnoise(r, h, w, beta=1.8, aniso=(1.0, 8.0)) * 0.5 + 0.7, 0, 1.2)
    rc = M.lerp(M.col("#3e1c0c"), M.col("#7a3b18"), M.fnoise(r, h, w, beta=2.4) * 0.5 + 0.5)
    return M.lerp(rgb, rc, np.clip(acc * amount + src * 0.7, 0, 0.85)[..., None])


def _black_steel(r, h, w, ppb, rust=0.4):
    """Black-painted steel gone to rust in broad blooms (smooth-edged, not a speckle: it reads in
    the dark and keeps the PNG small), the paint flaked at their edges."""
    rgb = M.steel_black(r, h, w, ppb, rust=0.0)
    n = M.blur(M.fnoise(r, h, w, beta=2.8), 1.5, wrap=True) + 0.35 * M.fnoise(r, h, w, beta=1.6)
    bloom = M.smooth(np.quantile(n, 1 - rust), np.quantile(n, 1 - rust * 0.55), n)
    rc = M.lerp(M.col("#3c1b0b"), M.col("#80401a"), M.fnoise(r, h, w, beta=2.2) * 0.5 + 0.5)
    rgb = M.lerp(rgb, rc, bloom[..., None] * 0.9)
    return M.chipped_paint(rgb, rc * 0.8, r, amount=0.05, scale=30, grain=2.2)


def _rivet_field(h, w, centres, rad):
    """Height and mask of round rivet heads at pixel centres."""
    Y, X = np.mgrid[0:h, 0:w].astype(float)
    hgt = np.zeros((h, w))
    for (cx, cy) in centres:
        d = np.hypot(X - cx, Y - cy) / rad
        hgt = np.maximum(hgt, np.sqrt(np.clip(1 - d * d, 0, 1)))
    return hgt


def _riveted(rgb, r, centres, rad):
    hgt = _rivet_field(*rgb.shape[:2], centres, rad)
    sh = M.relief(hgt * rad * 1.2, 1.2)
    # Domes: the paint rubbed thin on top catches what light there is.
    rgb = M.lerp(rgb, M.col("#5c5a56"), (hgt ** 2 * 0.35)[..., None]) * sh[..., None]
    ring = np.clip(hgt * 4, 0, 1) * np.clip(1 - hgt * 2.5, 0, 1)
    return _rust_runs(rgb, r, ring * 0.8, 0.5)


def _steel(h, w):
    r = M.rng(22607)
    rgb = _black_steel(r, h, w, 128, rust=0.35)
    edge = np.zeros((h, w))
    edge[:3] = 1.0
    rgb = _rust_runs(rgb, r, edge * (M.fnoise(r, h, w, beta=2.0) > 0.3), 0.5)
    return M.dust(rgb, 0.12, r)


def _rivet_strip(rows, pitch, seed):
    def f(h, w):
        r = M.rng(seed)
        rgb = _black_steel(r, h, w, 128, rust=0.3)
        p = pitch * 128
        rad = max(2.2, h * 0.09 if len(rows) > 1 else h * 0.14)
        cs = [(x, y * h) for y in rows for x in np.arange(p / 2, w, p)]
        rgb = _riveted(rgb, r, cs, rad)
        return M.dust(rgb, 0.1, r)
    return f


def _flange(h, w):
    """A post flange face (u across its width, v down from the ceiling): two rivet columns at the
    header connection and above the base, rust bled down from each."""
    r = M.rng(22633)
    rgb = _black_steel(r, h, w, 200, rust=0.4)
    ppb = h / CEIL
    cs = []
    for y in (3.06, 3.14, 3.22, 3.94, 0.1, 0.2):
        for u in (0.28, 0.72):
            cs.append((u * w, (CEIL - y) * ppb))
    rgb = _riveted(rgb, r, cs, w * 0.1)
    return M.dust(rgb, 0.1, r)


GLP_X0, GLP_X1, GLP_Y0, GLP_Y1 = -0.45, 2.45, 0.075, PANE_Y1 - 0.035


def _plate(h, w):
    """Riveted steel plate closing a segment: two sheets lapped at mid-height, rivets round the
    border and along the lap, black paint flaking to rust, a faded hazard band at the foot."""
    r = M.rng(22634)
    ppb = w / (GLP_X1 - GLP_X0)
    rgb = _black_steel(r, h, w, ppb, rust=0.5)
    X, Y = M.grid(h, w)
    yb = lambda y: (GLP_Y1 - y) / (GLP_Y1 - GLP_Y0) * h
    xb = lambda x: (x - GLP_X0) / (GLP_X1 - GLP_X0) * w
    lap = yb(1.45)
    Yp = np.mgrid[0:h, 0:w][0].astype(float)
    rgb = np.where((np.abs(Yp - lap) < 1.2)[..., None], rgb * 0.45, rgb)
    rgb = np.where(((Yp - lap) > 1.2)[..., None] & ((Yp - lap) < 4)[..., None], rgb * 1.25, rgb)
    # Hazard band (faded yellow and black diagonal stripes), mostly worn away.
    Xp = np.mgrid[0:h, 0:w][1].astype(float)
    band = (Yp > yb(0.36)) & (Yp < yb(0.16))
    diag = ((Xp + Yp) / (0.16 * ppb)) % 2 < 1
    hz = M.lerp(M.col("#a48a3a"), M.col("#c2a54e"), M.fnoise(r, h, w, beta=2.4) * 0.5 + 0.5)
    keep = band & diag & (M.fnoise(r, h, w, beta=2.2) > -0.4)
    rgb = np.where(keep[..., None], M.lerp(hz, rgb, 0.35), rgb)
    cs = []
    for x in np.arange(-0.3, 2.35, 0.15):
        for y in (GLP_Y0 + 0.07, 1.45 - 0.04, 1.45 + 0.04, GLP_Y1 - 0.07):
            cs.append((xb(x), yb(y)))
    for y in np.arange(0.3, 2.7, 0.15):
        for x in (-0.3, 2.3):
            cs.append((xb(x), yb(y)))
    rgb = _riveted(rgb, r, cs, 0.03 * ppb)
    rgb = M.grime_gradient(rgb, r, 0.3, 0.1, tone=(0.06, 0.05, 0.04), streak=0.5)
    return M.dust(rgb, 0.1, r)


def _wired(h, w):
    """Wired glass ceiling panel, seen from below with nothing lit above it: dark grey-green, a
    square welded mesh in it, grime, dead flies along the frame, a crack."""
    r = M.rng(22608)
    rgb = M.lerp(M.col("#23282a"), M.col("#30363a"), M.fnoise(r, h, w, beta=2.8) * 0.5 + 0.5)
    Y, X = np.mgrid[0:h, 0:w].astype(float)
    p = w / 1.5 * 0.085
    dx = np.abs((X % p) - p / 2)
    dy = np.abs((Y % p) - p / 2)
    wire = np.clip(1.2 - np.minimum(p / 2 - dx, p / 2 - dy), 0, 1)
    rgb = M.lerp(rgb, M.col("#121314"), wire * 0.8)
    Xg, Yg = M.grid(h, w)
    flies = (M.fnoise(r, h, w, beta=0.2) > 2.3) & (Yg > 0.85)
    rgb = np.where(flies[..., None], M.col("#0b0a09"), rgb)
    from .mirror import _crack_lines
    c, d = _crack_lines(r, w * 0.3, h * 0.2, 5, 1, (w, h))
    rgb = M.lerp(rgb, M.col("#5a605e"), c * 0.3)
    rgb = M.lerp(rgb, M.col("#3a2a1c"), np.clip(M.fnoise(r, h, w, beta=2.6) - 0.6, 0, 1) * 0.6)
    edge = np.clip(1 - np.minimum(np.minimum(Xg, 1 - Xg), np.minimum(Yg, 1 - Yg)) / 0.08, 0, 1)
    return M.lerp(rgb, M.col("#101010"), edge ** 2 * 0.7)


def _terrazzo(r, h, w):
    """Dark terrazzo: black and grey chips in charcoal, a brass divider strip along the cell edge."""
    ids, d1, d2 = M.voronoi(r, h, w, 9000)
    tone = (ids * 2654435761 % 1000) / 1000.0
    chip = (d2 - d1) > 0.9
    base = M.lerp(M.col("#2c2a28"), M.col("#383533"), M.fnoise(r, h, w, beta=2.4) * 0.5 + 0.5)
    chips = M.lerp(M.col("#45423e"), M.col("#6f6b64"), tone[..., None] ** 2)
    rgb = np.where((chip & (tone > 0.55))[..., None], chips, base)
    X, Y = M.grid(h, w)
    strip = (np.minimum(np.minimum(X, 1 - X), np.minimum(Y, 1 - Y)) * w) < 3
    return np.where(strip[..., None], M.col("#6c5a33"), rgb)


def _mats(h, w):
    """The floor: worn black coin-pattern rubber matting laid over the terrazzo, two sheets per
    cell butted down the middle, stopping short of the glass lines; coins worn smooth along the
    walking line, grey heel scuffs, dust in the gaps, a lifted corner and a torn bite."""
    r = M.rng(22609)
    rgb = _terrazzo(r, h, w)
    sc = M.scratches(r, h, w, 60, (0.01, 0.05))
    rgb = M.lerp(rgb, M.col("#4a4744"), sc * 0.4)
    ppb = w / 3.0
    Y, X = np.mgrid[0:h, 0:w].astype(float)
    Xg, Yg = M.grid(h, w)
    ins = 0.16 * ppb
    rag = M.fnoise(r, h, w, beta=2.4) * 1.5
    inside = (X > ins + rag) & (X < w - ins + rag) & (Y > ins + rag) & (Y < h - ins + rag)
    # A torn bite out of one sheet's edge (terrazzo shows) and a lifted corner.
    bite = np.hypot(X - 0.73 * w, (Y - (h - ins)) * 1.4) < 0.16 * ppb * (1 + 0.3 * M.fnoise(r, h, w, beta=1.5))
    mat = inside & ~bite
    joint = np.abs(X - w / 2) < 1.2
    p = 0.075 * ppb
    cx = (X % p) - p / 2
    cy = ((Y + (np.floor(X / p) % 2) * p / 2) % p) - p / 2
    d = np.hypot(cx, cy)
    coin = np.clip((p * 0.3 - d) / 1.2 + 0.5, 0, 1)
    walk = np.exp(-((Xg - 0.5) / 0.2) ** 2) * 0.5 + np.exp(-((Yg - 0.5) / 0.2) ** 2) * 0.5
    wear = np.clip(walk * 0.9 + 0.35 * M.fnoise(r, h, w, beta=2.4), 0, 1)
    height = coin * (1 - 0.7 * wear)
    sh = M.relief(height * 4.0, 1.2)
    rub = M.lerp(M.col("#131312"), M.col("#1c1b1a"), M.fnoise(r, h, w, beta=2.6) * 0.5 + 0.5)
    # Coin tops a shade lighter than the ground (worn rubber greys), the walking line greyer still.
    rub = M.lerp(rub, M.col("#353330"), (height * 0.6 + wear * 0.25)[..., None])
    rub = rub * sh[..., None]
    gaps = (1 - coin) * (1 - wear * 0.5)
    rub = M.lerp(rub, M.col("#57524a"), (gaps * np.clip(M.fnoise(r, h, w, beta=2.2) * 0.5 + 0.45, 0, 1))[..., None] * 0.4)
    heels = M.scratches(r, h, w, 140, (0.006, 0.03), width=2.0)
    rub = M.lerp(rub, M.col("#55524d"), heels[..., None] * 0.35)
    # Lifted corner (catches the light, its shadow on the terrazzo beside it).
    lift = np.clip(1 - np.hypot(X - ins, Y - ins) / (0.35 * ppb), 0, 1)
    rub = rub * (1 + 0.5 * lift)[..., None]
    dist = M.edge_distance(mat)
    rub = np.where((dist < 2.5)[..., None], rub * 1.45, rub)
    rgb = np.where(joint[..., None] & mat[..., None], M.col("#0a0a0a"), np.where(mat[..., None], rub, rgb))
    out_d = M.edge_distance(~mat)
    shadow = np.clip(1 - out_d / (3 + 6 * lift), 0, 1) * (~mat)
    rgb = rgb * (1 - 0.5 * shadow)[..., None]
    return M.dust(rgb, 0.14, r)


def _cable(h, w):
    r = M.rng(22637)
    X, Y = M.grid(h, w)
    rgb = M.lerp(M.col("#0e0e0e"), M.col("#262624"), np.exp(-((X - 0.3) / 0.15) ** 2))
    return M.dust(rgb, 0.25, r)


def _tread(h, w):
    """A steel threshold strip: worn diamond tread plate, rusted at the edges."""
    r = M.rng(22638)
    rgb = _black_steel(r, h, w, 128, rust=0.6)
    Y, X = np.mgrid[0:h, 0:w].astype(float)
    p = 10.0
    a = ((X + Y) % p) - p / 2
    b = ((X - Y + (np.floor((X + Y) / p) % 2) * p / 2) % p) - p / 2
    ridge = np.clip(1.5 - np.abs(a) - 0.6 * np.abs(b) * 0, 0, 1) * (np.abs(b) < p * 0.3)
    rgb = M.lerp(rgb, M.col("#6a6a68"), ridge[..., None] * 0.5)
    Xg, Yg = M.grid(h, w)
    return M.lerp(rgb, M.col("#5a2a10"), M.smooth(0.25, 0.0, np.minimum(Yg, 1 - Yg))[..., None] * 0.5)


def regions(S):
    S.region("fh_stripes", SHEET, 160, 560, _stripes)
    S.region("fh_red", SHEET, 192, 192, _paint(RED, 22610))
    S.region("fh_navy", SHEET, 192, 192, _paint(NAVY, 22611))
    S.region("fh_cream", SHEET, 192, 192, _paint(CREAM, 22612))
    S.region("fh_frame", SHEET, 512, 48, _frame)
    S.region("fh_crest", SHEET, 480, 128, _crest)
    S.region("fh_canvas", SHEET, 384, 384, _canvas, bits=5)
    S.region("fh_floor", SHEET, 512, 512, _floor, bits=5)
    S.region("fh_brass", SHEET, 64, 64, _sockets)
    S.region("fh_bulb_dead", SHEET, 32, 32, _bulb_dead)
    S.region("fh_hole", SHEET, 16, 16, _socket_hole)
    # The boards are the zone's hero art (figures need the pixels): 150 px per block.
    S.region("fh_solid_a", SHEET, 450, 417, _solid_a)
    S.region("fh_solid_b", SHEET, 450, 417, _solid_b)
    S.region("gl_steel", SHEET, 256, 256, _steel)
    S.region("gl_rivets", SHEET, 256, 32, _rivet_strip((0.5,), 0.125, 22630))
    S.region("gl_girder", SHEET, 512, 32, _rivet_strip((0.2, 0.8), 0.1, 22639))
    S.region("gl_flange", SHEET, 32, 512, _flange)
    S.region("gl_plate", SHEET, 288, 280, _plate)
    S.region("gl_wired", SHEET, 256, 256, _wired)
    S.region("gl_floor", SHEET, 512, 512, _mats, bits=5)
    S.region("gl_cable", SHEET, 16, 64, _cable)
    S.region("gl_tread", SHEET, 384, 24, _tread)


# ------------------------------------------------------------------------------------------------
# Shared mesh helpers
# ------------------------------------------------------------------------------------------------

def _rail2(m, prof, x0, x1, region, rsize=(1.0, 1.0), v_off=0.0):
    """A moulding on both faces of the pane plane: prof is (dz, y) for the +z face, listed going
    up it (outside on the right); the -z face gets its mirror image (reversed, so its normals face
    -z as well)."""
    G.rail(m, [(PLANE + dz, y) for dz, y in prof], x0, x1, region, rsize, v_off=v_off)
    G.rail(m, [(PLANE - dz, y) for dz, y in reversed(prof)], x0, x1, region, rsize, v_off=v_off)


def _axis(d):
    """A lathe frame whose +y points along d (unit vector)."""
    d = np.asarray(d, float)
    ref = np.array([0.0, 1.0, 0.0]) if abs(d[1]) < 0.9 else np.array([1.0, 0.0, 0.0])
    a = G.norm(np.cross(ref, d))
    b = np.cross(a, d)  # right-handed, so Mesh.add does not treat it as a mirror
    return np.stack([a, d, b], axis=1)


def _socket(m, pos, d, bulb=True):
    """A brass socket on a surface pointing along d: with a dead bulb in it, or empty (black
    inside). 18 / 12 quads: these come in dozens per segment."""
    s = G.Mesh("s")
    G.lathe(s, [(0.024, 0.0), (0.024, 0.026)], 6, "fh_brass", centre=(0, 0, 0))
    if bulb:
        G.lathe(s, [(0.014, 0.026), (0.023, 0.056), (0.0, 0.09)], 6, "fh_bulb_dead", centre=(0, 0, 0))
    else:
        G.lathe(s, [(0.024, 0.026), (0.0, 0.018)], 6, "fh_hole", centre=(0, 0, 0))
    m.add(s, _axis(d), pos)


def _rivet(m, pos, d, r=0.012, region="gl_steel"):
    """A rivet head on a plate facing d: a six-sided low cone (6 quads; they come in dozens)."""
    s = G.Mesh("r")
    G.lathe(s, [(r, 0.0), (0.0, r * 0.65)], 6, region, centre=(0, 0, 0))
    m.add(s, _axis(d), pos)


def _bolt(m, pos, d, r=0.018):
    """A hex nut and stud end on a base plate."""
    s = G.Mesh("n")
    G.lathe(s, [(r, 0.0), (r, 0.016), (0.0, 0.022)], 6, "gl_steel", centre=(0, 0, 0), flat=True)
    m.add(s, _axis(d), pos)


def _flat(m, pts, region, normal, rsize=(2.0, 2.0), shade=1.0):
    """A flat plate quad in the pane plane (gussets): uv from its x and y at physical scale."""
    uv = [(p[0] / rsize[0], (CEIL - p[1]) / rsize[1]) for p in pts]
    m.quad(pts, uv, region, normal, shade)


# ------------------------------------------------------------------------------------------------
# Funhouse meshes
# ------------------------------------------------------------------------------------------------

def column() -> G.Mesh:
    """Barber post: square navy plinth, brass collar, striped shaft, red capital with four dead
    bulbs (on the diagonals, so every corridor sees one), the post running on into the band to a
    gilt ball under the canvas."""
    m = G.Mesh("column_funhouse", pivot=(0.5, 0.5))
    G.lathe(m, [(0.0, 0.0), (0.24, 0.0), (0.24, 0.16), (0.22, 0.18), (0.0, 0.18)][1:], 4, "fh_navy", flat=True,
            phase=math.pi / 4, v0=0.9, v1=0.2, u_turns=0.6)
    G.lathe(m, [(0.175, 0.18), (0.185, 0.22), (0.15, 0.26)], 12, "fh_brass", v0=0.9, v1=0.1)
    G.lathe(m, [(0.15, 0.26), (0.148, 2.6)], 12, "fh_stripes", v0=1.0, v1=0.0)
    G.lathe(m, [(0.148, 2.6), (0.18, 2.64), (0.15, 2.72), (0.15, 2.83), (0.22, 2.9), (0.22, 2.98)], 12, "fh_red",
            v0=0.9, v1=0.1, u_turns=0.8)
    for k in range(4):
        a = k * math.pi / 2 + math.pi / 4
        d = np.array([math.cos(a), 0.0, math.sin(a)])
        _socket(m, (0.5 + d[0] * 0.148, 2.775, 0.5 + d[2] * 0.148), d, bulb=k != 2)
    G.lathe(m, [(0.09, 2.98), (0.09, 3.62), (0.12, 3.7)], 8, "fh_stripes", v0=0.3, v1=0.0, u_turns=0.6)
    G.lathe(m, [(0.0, 3.7), (0.1, 3.74), (0.11, 3.8), (0.08, 3.88), (0.0, 3.92)], 8, "fh_brass", v0=1.0, v1=0.0)
    m.contact_ao(0.0, CEIL, reach=0.35, strength=0.5)
    return m


def _frame_rails(m):
    """Painted kick-board and a moulded frame head round the pane (both faces)."""
    _rail2(m, [(0.06, 0.0), (0.06, 0.11), (0.04, 0.13), (0.012, 0.13)], -0.5, 2.5, "fh_navy", rsize=(1.5, 1.5))
    _rail2(m, [(0.012, PANE_Y1 - 0.04), (0.05, PANE_Y1 - 0.03), (0.06, PANE_Y1), (0.055, BAND_Y0 + 0.02)], -0.5, 2.5,
           "fh_frame", rsize=(4.0, 0.1))


def _crest_mesh(m):
    """The crest board: an alpha-cut painted card on each face, the outline's thickness as a strip
    of quads following its curve (scallops, hump, medallion), the medallion's opening lined, a
    raised gilt ring round it, and dead bulbs along the scallops on both faces."""
    zf, zb = PLANE - CREST_D / 2, PLANE + CREST_D / 2
    x0, y0 = -0.5, CREST_Y
    G.card(m, (x0, y0, zf), (1, 0, 0), (0, 1, 0), CREST_W, CREST_H, "fh_crest", normal=np.array([0.0, 0.0, -1.0]))
    G.card(m, (x0 + CREST_W, y0, zb), (-1, 0, 0), (0, 1, 0), CREST_W, CREST_H, "fh_crest",
           normal=np.array([0.0, 0.0, 1.0]))
    xs = sorted(set(np.round(np.concatenate([np.linspace(-1.5, 1.5, 37), [-1.0, -0.5, 0.5, 1.0, -0.3, 0.3]]), 4)))
    ys = _crest_top(np.asarray(xs))
    run = 0.0
    for i in range(len(xs) - 1):
        a = np.array([xs[i] + 1.0, y0 + ys[i]])
        b = np.array([xs[i + 1] + 1.0, y0 + ys[i + 1]])
        seg = b - a
        L = float(np.hypot(*seg))
        n = G.norm((-seg[1], seg[0], 0.0))
        m.quad([(a[0], a[1], zf), (b[0], b[1], zf), (b[0], b[1], zb), (a[0], a[1], zb)],
               [(run / 2.0, 0.0), ((run + L) / 2.0, 0.0), ((run + L) / 2.0, CREST_D / 2.0), (run / 2.0, CREST_D / 2.0)],
               "fh_cream", n, 0.85)
        run += L
    # The opening's lining (normals inward, toward its centre).
    nh = 14
    for k in range(nh):
        t0, t1 = 2 * math.pi * k / nh, 2 * math.pi * (k + 1) / nh
        p0 = (1.0 + HOLE_RX * math.cos(t0), y0 + MED_Y + HOLE_RY * math.sin(t0))
        p1 = (1.0 + HOLE_RX * math.cos(t1), y0 + MED_Y + HOLE_RY * math.sin(t1))
        tm = 0.5 * (t0 + t1)
        n = G.norm((-math.cos(tm) / HOLE_RX, -math.sin(tm) / HOLE_RY, 0.0))
        m.quad([(p0[0], p0[1], zb), (p1[0], p1[1], zb), (p1[0], p1[1], zf), (p0[0], p0[1], zf)],
               [(k / nh, 0.0), ((k + 1) / nh, 0.0), ((k + 1) / nh, 0.05), (k / nh, 0.05)], "fh_navy", n, 0.6)
    # Raised gilt ring round the opening on both faces (a lathe squashed to the ellipse).
    for s in (-1, 1):
        ring = G.Mesh("ring")
        G.lathe(ring, [(0.29, 0.0), (0.32, 0.022), (0.355, 0.0)], 10, "fh_brass", centre=(0, 0, 0))
        A = _axis((0.0, 0.0, float(s))) @ G.scale(1.0, 1.0, HOLE_RY / HOLE_RX)
        # _axis maps lathe y to d; scale the lathe's z (which _axis sends to world y) to squash.
        m.add(ring, A, (1.0, y0 + MED_Y, PLANE + s * CREST_D / 2))
    # Two sockets a face (the panels are the maze's most numerous prop: every quad here counts
    # hundreds of times per chunk); one dead bulb and one empty socket, swapped on the back.
    for x in (0.25, 1.75):
        for s in (-1, 1):
            _socket(m, (x, CREST_Y + 0.2, PLANE + s * CREST_D / 2), (0.0, 0.0, float(s)), bulb=(x < 1.0) == (s < 0))


def panel() -> G.Mesh:
    m = G.Mesh("panel_funhouse", pivot=(1.0, 0.5))
    _frame_rails(m)
    _crest_mesh(m)
    m.contact_ao(0.0, CEIL, reach=0.3)
    return m


def opening() -> G.Mesh:
    """An open segment: the crest on a painted lintel, and a scalloped canvas valance hanging."""
    m = G.Mesh("opening_funhouse", pivot=(1.0, 0.5))
    G.bevel_box(m, (-0.5, PANE_Y1 - 0.05, PLANE - 0.05), (2.5, BAND_Y0 + 0.02, PLANE + 0.05), 0.012, "fh_red",
                rsize=(1.5, 1.5))
    _crest_mesh(m)

    def f(u, v):
        x = -0.4 + u * 2.8
        sc = 0.06 * abs(math.sin(u * math.pi * 7))
        y = PANE_Y1 - 0.05 - v * (0.22 - sc)
        return (x, y, PLANE - 0.06 - 0.03 * v)

    G.surface(m, f, 14, 1, "fh_canvas", uv_fn=lambda u, v: (u, v * 0.2))
    return m


def solid() -> G.Mesh:
    """A boarded-up segment (drawn inside panel_funhouse's frame): a board painted on both faces
    like an old show front, a raised moulding round each painted field, bulb sockets in the bands
    outside it (a few real ones, two with dead bulbs, on the painted rings)."""
    m = G.Mesh("solid_funhouse", pivot=(1.0, 0.5))
    zf, zb = PLANE - 0.03, PLANE + 0.03
    w, h = SOL_X1 - SOL_X0, SOL_Y1 - SOL_Y0
    # Seen from -z the viewer's right is -x, so the front image runs from x1 down to x0 (and the
    # back the other way): both faces read as painted, not mirrored.
    G.card(m, (SOL_X1, SOL_Y0, zf), (-1, 0, 0), (0, 1, 0), w, h, "fh_solid_a", normal=np.array([0.0, 0.0, -1.0]))
    G.card(m, (SOL_X0, SOL_Y0, zb), (1, 0, 0), (0, 1, 0), w, h, "fh_solid_b", normal=np.array([0.0, 0.0, 1.0]))
    x0, y0, x1, y1 = MOULD
    prof = [(0.0, 0.0), (0.012, 0.028), (0.045, 0.036), (MOULD_W, 0.0)]
    # Front (-z): W = U x V = -z with U = -x; the path is in (s, t) = (-x, y).
    G.sweep(m, prof, G.rect_path(-x1, y0, -x0, y1), (0.0, 0.0, zf), (-1, 0, 0), (0, 1, 0), (0, 0, -1), "fh_frame",
            u_scale=0.25)
    G.sweep(m, prof, G.rect_path(x0, y0, x1, y1), (0.0, 0.0, zb), (1, 0, 0), (0, 1, 0), (0, 0, 1), "fh_frame",
            u_scale=0.25)
    for face, (z, s) in enumerate(((zf, -1.0), (zb, 1.0))):
        for (x, y, bulb) in REAL_SOCKS[face]:
            wx = x if face == 1 else SOL_X0 + SOL_X1 - x
            _socket(m, (wx, y, z), (0.0, 0.0, s), bulb)
    return m


def floor() -> G.Mesh:
    m = G.Mesh("floor_funhouse", pivot=(2.0, 2.0))
    y = 0.004
    m.quad([(CELL0, y, CELL1), (CELL1, y, CELL1), (CELL1, y, CELL0), (CELL0, y, CELL0)], [(0, 1), (1, 1), (1, 0), (0, 0)],
           "fh_floor", (0, 1, 0))
    return m


def ceiling() -> G.Mesh:
    """The tent ceiling: canvas stretched from line to line, sagging a quarter block in the middle,
    a painted batten along the lines."""
    m = G.Mesh("ceiling_funhouse", pivot=(2.0, 2.0))

    def f(u, v):
        x = CELL0 + u * 3.0
        z = CELL0 + v * 3.0
        sag = 0.26 * math.sin(u * math.pi) * math.sin(v * math.pi)
        return (x, CEIL - 0.02 - sag, z)

    G.surface(m, f, 8, 8, "fh_canvas", shade_fn=lambda u, v: 0.75 + 0.25 * math.sin(u * math.pi) * math.sin(v * math.pi))
    # Batten along the lines (half on each side, the neighbour cell draws the rest).
    for k in range(4):
        sub = G.Mesh("b")
        G.box(sub, (CELL0, CEIL - 0.08, CELL0), (CELL1, CEIL - 0.004, CELL0 + 0.05), "fh_red", rsize=(1.5, 1.5), faces="yZ")
        c = 2.0
        m.add(sub, G.rot_y(k * math.pi / 2), (c, 0, c) - G.rot_y(k * math.pi / 2) @ np.array([c, 0, c]))
    return m


# ------------------------------------------------------------------------------------------------
# Glass Labyrinth meshes
# ------------------------------------------------------------------------------------------------

def column_glass() -> G.Mesh:
    """A black H-section post, floor to ceiling (the x-line panes meet its flanges, the z-line
    panes slide into the channel between them), on a base plate with four diagonal gussets and
    anchor nuts, a cap plate at the ceiling. Rivets are in the flange texture."""
    m = G.Mesh("column_glass", pivot=(0.5, 0.5))
    fw, ft = 0.08, 0.014            # flange half-width (along z), thickness
    for xs in (0.44, 0.56 - ft):
        G.box(m, (xs, 0.03, 0.5 - fw), (xs + ft, CEIL, 0.5 + fw), "gl_flange", rsize=(2 * fw, CEIL), faces="xXzZ")
    G.box(m, (0.44 + ft, 0.03, 0.494), (0.56 - ft, CEIL, 0.506), "gl_flange", rsize=(2 * fw, CEIL), faces="zZ")
    G.bevel_box(m, (0.33, 0.0, 0.33), (0.67, 0.03, 0.67), 0.006, "gl_steel", rsize=(2.0, 2.0), bottom=False)
    # Gussets on the diagonals (the axes are taken by the panes).
    for k in range(4):
        a = math.pi / 4 + k * math.pi / 2
        d = np.array([math.cos(a), 0.0, math.sin(a)])
        p0 = np.array([0.5, 0.03, 0.5]) + d * 0.09
        p1 = np.array([0.5, 0.03, 0.5]) + d * 0.19
        p2 = np.array([0.5, 0.22, 0.5]) + d * 0.09
        nrm = np.array([-d[2], 0.0, d[0]])
        m.quad([p0, p1, p2, p2], [(0.0, 1.0), (0.05, 1.0), (0.0, 0.9), (0.0, 0.9)], "gl_steel", nrm)
        _bolt(m, tuple(np.array([0.5, 0.03, 0.5]) + d * 0.215), (0.0, 1.0, 0.0))
    G.box(m, (0.36, CEIL - 0.03, 0.36), (0.64, CEIL - 0.002, 0.64), "gl_steel", rsize=(2.0, 2.0), faces="xXzZy")
    m.contact_ao(0.0, CEIL, reach=0.3, strength=0.5)
    return m


HEAD_Y0, HEAD_Y1 = BAND_Y0, BAND_Y0 + 0.28      # the header girder in the band
CHORD_Y = CEIL - 0.12                           # underside of the ceiling grid's angle on the line


def _header(m):
    """Riveted I-girder along the line in the band: flanges in plain steel, the web with two rivet
    rows (texture)."""
    _rail2(m, [(0.0, HEAD_Y0), (0.05, HEAD_Y0), (0.05, HEAD_Y0 + 0.025), (0.012, HEAD_Y0 + 0.025)], -0.45, 2.45,
           "gl_steel", rsize=(2.0, 2.0))
    _rail2(m, [(0.012, HEAD_Y0 + 0.025), (0.012, HEAD_Y1 - 0.025)], -0.45, 2.45, "gl_girder", rsize=(4.0, 0.25))
    _rail2(m, [(0.012, HEAD_Y1 - 0.025), (0.05, HEAD_Y1 - 0.025), (0.05, HEAD_Y1), (0.0, HEAD_Y1)], -0.45, 2.45,
           "gl_steel", rsize=(2.0, 2.0))


def _truss(m):
    """A Warren truss of angle iron in the band, from the header up to the ceiling grid's angle,
    joined by riveted gusset plates (one each side of the members)."""
    nodes = [(-0.42, HEAD_Y1), (0.3, CHORD_Y), (1.0, HEAD_Y1), (1.7, CHORD_Y), (2.42, HEAD_Y1)]
    for (ax, ay), (bx, by) in zip(nodes[:-1], nodes[1:]):
        a = np.array([ax, ay, PLANE])
        b = np.array([bx, by, PLANE])
        t = G.norm(b - a)
        side = np.array([-t[1], t[0], 0.0]) * 0.03
        # The leg in the plane (both sides drawn) and the outstanding leg along z at its upper edge.
        L = float(np.linalg.norm(b - a))
        m.quad([a - side, b - side, b + side, a + side], [(0, 0), (L / 2, 0), (L / 2, 0.03), (0, 0.03)], "gl_steel",
               (0, 0, 1))
        up = side if side[1] > 0 else -side
        dz = np.array([0.0, 0.0, 0.03])
        m.quad([a + up - dz, b + up - dz, b + up + dz, a + up + dz], [(0, 0), (L / 2, 0), (L / 2, 0.03), (0, 0.03)],
               "gl_steel", G.norm(up))
    gussets = [[(0.8, HEAD_Y1), (1.2, HEAD_Y1), (1.1, HEAD_Y1 + 0.2), (0.9, HEAD_Y1 + 0.2)],
               [(0.1, CHORD_Y), (0.2, CHORD_Y - 0.18), (0.4, CHORD_Y - 0.18), (0.5, CHORD_Y)],
               [(1.5, CHORD_Y), (1.6, CHORD_Y - 0.18), (1.8, CHORD_Y - 0.18), (1.9, CHORD_Y)],
               [(-0.45, HEAD_Y1), (-0.2, HEAD_Y1), (-0.25, HEAD_Y1 + 0.12), (-0.45, HEAD_Y1 + 0.22)],
               [(2.2, HEAD_Y1), (2.45, HEAD_Y1), (2.45, HEAD_Y1 + 0.22), (2.25, HEAD_Y1 + 0.12)]]
    rivets = [((0.92, HEAD_Y1 + 0.09), (1.08, HEAD_Y1 + 0.09)), ((0.22, CHORD_Y - 0.09), (0.38, CHORD_Y - 0.09)),
              ((1.62, CHORD_Y - 0.09), (1.78, CHORD_Y - 0.09)), ((-0.36, HEAD_Y1 + 0.08), (-0.27, HEAD_Y1 + 0.06)),
              ((2.36, HEAD_Y1 + 0.08), (2.27, HEAD_Y1 + 0.06))]
    for s in (-1.0, 1.0):
        z = PLANE + s * 0.008
        for g, rv in zip(gussets, rivets):
            pts = [(x, y, z) for (x, y) in g]
            _flat(m, pts, "gl_steel", (0.0, 0.0, s))
            for (x, y) in rv:
                _rivet(m, (x, y, z), (0.0, 0.0, s), 0.013)


def _channels(m):
    """Glazing channels holding the pane, top and bottom (bolt heads in the texture)."""
    _rail2(m, [(0.034, 0.0), (0.034, 0.075), (0.012, 0.075)], -0.45, 2.45, "gl_rivets", rsize=(2.0, 0.25), v_off=0.35)
    _rail2(m, [(0.012, PANE_Y1 - 0.035), (0.034, PANE_Y1 - 0.035), (0.034, BAND_Y0)], -0.45, 2.45, "gl_rivets",
           rsize=(2.0, 0.25), v_off=0.1)


def panel_glass() -> G.Mesh:
    m = G.Mesh("panel_glass", pivot=(1.0, 0.5))
    _channels(m)
    _header(m)
    _truss(m)
    m.contact_ao(0.0, CEIL, reach=0.25, strength=0.4)
    return m


def opening_glass() -> G.Mesh:
    """An open segment: the header and truss above, a worn steel threshold strip on the floor."""
    m = G.Mesh("opening_glass", pivot=(1.0, 0.5))
    _header(m)
    _truss(m)
    y = 0.01
    m.quad([(-0.45, y, PLANE + 0.09), (2.45, y, PLANE + 0.09), (2.45, y, PLANE - 0.09), (-0.45, y, PLANE - 0.09)],
           [(0, 0), (1, 0), (1, 1), (0, 1)], "gl_tread", (0, 1, 0))
    return m


def solid_glass() -> G.Mesh:
    """Riveted steel plate closing a segment (inside panel_glass's channels): the plate, a flat-bar
    stiffener up the middle and a lap strap across, rivet heads along both."""
    m = G.Mesh("solid_glass", pivot=(1.0, 0.5))
    w, h = GLP_X1 - GLP_X0, GLP_Y1 - GLP_Y0
    t = 0.012
    G.card(m, (GLP_X1, GLP_Y0, PLANE - t), (-1, 0, 0), (0, 1, 0), w, h, "gl_plate", normal=np.array([0.0, 0.0, -1.0]))
    G.card(m, (GLP_X0, GLP_Y0, PLANE + t), (1, 0, 0), (0, 1, 0), w, h, "gl_plate", normal=np.array([0.0, 0.0, 1.0]))
    for s in (-1.0, 1.0):
        z0, z1 = sorted((PLANE + s * t, PLANE + s * (t + 0.022)))
        fz = "z" if s < 0 else "Z"
        G.box(m, (0.97, GLP_Y0, z0), (1.03, GLP_Y1, z1), "gl_steel", rsize=(2.0, 2.0), faces="xX" + fz)
        G.box(m, (GLP_X0, 1.42, z0), (0.97, 1.48, z1), "gl_steel", rsize=(2.0, 2.0), faces="yY" + fz)
        G.box(m, (1.03, 1.42, z0), (GLP_X1, 1.48, z1), "gl_steel", rsize=(2.0, 2.0), faces="yY" + fz)
        zf = PLANE + s * (t + 0.022)
        for y in (0.35, 0.8, 2.05, 2.5):
            _rivet(m, (1.0, y, zf), (0.0, 0.0, s), 0.012)
        for x in (0.1, 0.55, 1.45, 1.9):
            _rivet(m, (x, 1.45, zf), (0.0, 0.0, s), 0.012)
    return m


def floor_glass() -> G.Mesh:
    m = G.Mesh("floor_glass", pivot=(2.0, 2.0))
    y = 0.004
    m.quad([(CELL0, y, CELL1), (CELL1, y, CELL1), (CELL1, y, CELL0), (CELL0, y, CELL0)], [(0, 1), (1, 1), (1, 0), (0, 0)],
           "gl_floor", (0, 1, 0))
    return m


def ceiling_glass() -> G.Mesh:
    """Wired glass in a steel grid: angle iron along the lines (half each side), T-bars across the
    middle, a junction box where they cross with its dead cables: one looping along a bar, one cut
    and hanging, one sagging across to the line."""
    m = G.Mesh("ceiling_glass", pivot=(2.0, 2.0))
    y = CEIL - 0.004
    for (a, b) in ((0, 0), (1, 0), (0, 1), (1, 1)):
        x0, z0 = CELL0 + a * 1.5, CELL0 + b * 1.5
        m.quad([(x0, y, z0), (x0 + 1.5, y, z0), (x0 + 1.5, y, z0 + 1.5), (x0, y, z0 + 1.5)], [(0, 0), (1, 0), (1, 1), (0, 1)],
               "gl_wired", (0, -1, 0))
    c = 2.0
    for k in range(4):
        sub = G.Mesh("b")
        # Bottom leg (flange) and the upright leg's inner face.
        G.box(sub, (CELL0, CHORD_Y, CELL0), (CELL1, CHORD_Y + 0.012, CELL0 + 0.05), "gl_steel", rsize=(2.0, 2.0), faces="yZ")
        G.box(sub, (CELL0, CHORD_Y + 0.012, CELL0), (CELL1, CEIL - 0.004, CELL0 + 0.01), "gl_rivets", rsize=(2.0, 0.25),
              faces="Z", off=(0.0, 0.1))
        m.add(sub, G.rot_y(k * math.pi / 2), (c, 0, c) - G.rot_y(k * math.pi / 2) @ np.array([c, 0, c]))
    ty = CEIL - 0.09
    G.box(m, (CELL0, ty, c - 0.03), (CELL1, ty + 0.01, c + 0.03), "gl_steel", rsize=(2.0, 2.0), faces="y")
    G.box(m, (CELL0, ty + 0.01, c - 0.006), (CELL1, CEIL - 0.004, c + 0.006), "gl_steel", rsize=(2.0, 2.0), faces="zZ")
    G.box(m, (c - 0.03, ty, CELL0), (c + 0.03, ty + 0.01, CELL1), "gl_steel", rsize=(2.0, 2.0), faces="y")
    G.box(m, (c - 0.006, ty + 0.01, CELL0), (c + 0.006, CEIL - 0.004, CELL1), "gl_steel", rsize=(2.0, 2.0), faces="xX")
    # Junction box hung under the crossing.
    by0 = ty - 0.09
    G.box(m, (c - 0.07, by0, c - 0.07), (c + 0.07, ty, c + 0.07), "gl_steel", rsize=(2.0, 2.0), faces="xXzZy")
    # Conduit running out along the T-bar toward the line.
    G.tube(m, [(c + 0.05, ty - 0.03, c - 0.07), (c + 0.05, ty - 0.03, CELL0 + 0.06)], 0.012, 4, "gl_steel", 2.0)
    G.tube(m, G.catenary((c + 0.07, by0 + 0.03, c + 0.02), (CELL1 - 0.1, CHORD_Y - 0.01, c + 0.3), 0.42, 6), 0.009, 3,
           "gl_cable", 4.0)
    G.tube(m, [(c - 0.03, by0, c + 0.04), (c - 0.05, by0 - 0.18, c + 0.07), (c - 0.02, by0 - 0.38, c + 0.05),
               (c + 0.03, by0 - 0.47, c + 0.09)], 0.008, 3, "gl_cable", 4.0)
    G.tube(m, G.catenary((c - 0.07, by0 + 0.05, c - 0.03), (CELL0 + 0.6, CHORD_Y, CELL0 + 0.04), 0.3, 5), 0.008, 3,
           "gl_cable", 4.0)
    return m


def meshes():
    return [column(), panel(), opening(), solid(), floor(), ceiling(), column_glass(), panel_glass(), opening_glass(),
            solid_glass(), floor_glass(), ceiling_glass()]
