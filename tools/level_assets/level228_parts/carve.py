"""Height fields of carved and moulded ornament, drawn at high resolution with PIL and rounded off
(distance transform), for baked relief: scrolling acanthus friezes, rosettes, interlaced knotwork,
Moorish fretwork lattices, star tiles. All return float arrays, row 0 at the top."""
from __future__ import annotations

import math

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

from . import mat as M


def _rounded(mask: np.ndarray, radius: float) -> np.ndarray:
    """A 0/1 mask to a rounded height (a carved strand is rounded, not flat-topped)."""
    d = ndimage.distance_transform_edt(mask > 0.5)
    return np.sqrt(np.clip(d / max(radius, 1e-6), 0, 1))


def _canvas(w, h, ss):
    img = Image.new("L", (w * ss, h * ss), 0)
    return img, ImageDraw.Draw(img)


def _down(img, w, h):
    return np.asarray(img.resize((w, h), Image.LANCZOS), float) / 255.0


def scroll_frieze(r, h, w, waves=2.0, stem=0.06, leaf=0.16, ss=3):
    """A running acanthus scroll: a wavy stem with a curling volute in every half wave and leaves
    along it; returns (height, mask). waves = full waves across the width (an even count tiles)."""
    img, d = _canvas(w, h, ss)
    W, H = w * ss, h * ss
    lam = W / waves
    amp = H * 0.18
    cy = H * 0.5
    sw = max(2, int(stem * H))
    pts = [(x, cy + amp * math.sin(2 * math.pi * x / lam)) for x in np.linspace(-lam * 0.25, W + lam * 0.25, 400)]
    d.line(pts, fill=255, width=sw, joint="curve")
    for k in range(int(waves * 2) + 2):
        x0 = (k - 0.5) * lam / 2
        up = k % 2 == 0
        # Volute: an Archimedean spiral springing off the stem toward the free side.
        sx = x0 + lam * 0.25
        sy = cy + (amp if not up else -amp) * 0.35
        R = H * 0.30
        spiral = []
        for t in np.linspace(0, 2.6 * math.pi, 120):
            rr = R * (1 - t / (3.0 * math.pi))
            a = t * (1 if up else -1) + (math.pi if up else 0)
            spiral.append((sx + rr * math.cos(a) * 0.9, sy + rr * math.sin(a) * (1 if up else 1) * 0.75))
        widths = np.linspace(sw * 0.9, sw * 0.35, len(spiral))
        for i in range(len(spiral) - 1):
            d.line([spiral[i], spiral[i + 1]], fill=255, width=max(1, int(widths[i])))
        # Rosette at the eye of the volute.
        ex, ey = spiral[-1]
        rr = sw * 0.9
        d.ellipse([ex - rr, ey - rr, ex + rr, ey + rr], fill=255)
        # Acanthus leaves: lobed teardrops along the stem.
        for j in range(3):
            t = (j + 1) / 4.0
            lx = x0 + t * lam / 2
            ly = cy + amp * math.sin(2 * math.pi * lx / lam)
            ang = math.atan2(amp * 2 * math.pi / lam * math.cos(2 * math.pi * lx / lam), 1.0)
            side = -1 if (j + k) % 2 else 1
            L = leaf * H * (1.1 - 0.2 * j)
            for lobe in range(3):
                la = ang + side * (0.9 + 0.35 * lobe)
                bx = lx + math.cos(la) * L * (0.35 + 0.22 * lobe)
                by = ly + math.sin(la) * L * (0.35 + 0.22 * lobe)
                rr = L * (0.34 - 0.06 * lobe)
                d.ellipse([bx - rr, by - rr * 0.7, bx + rr, by + rr * 0.7], fill=255)
    mask = _down(img, w, h)
    height = _rounded(mask > 0.5, max(1.5, stem * h * 0.6)) * 0.8 + M.blur(mask, 1.0) * 0.2
    return height, mask


def rosette(r, n, petals=8, ss=3):
    """A carved rosette (n x n): petals round a boss."""
    img, d = _canvas(n, n, ss)
    N = n * ss
    c = N / 2
    for k in range(petals):
        a = 2 * math.pi * k / petals
        px, py = c + math.cos(a) * N * 0.24, c + math.sin(a) * N * 0.24
        rr = N * 0.16
        d.ellipse([px - rr, py - rr, px + rr, py + rr], fill=200)
    rr = N * 0.14
    d.ellipse([c - rr, c - rr, c + rr, c + rr], fill=255)
    m = _down(img, n, n)
    return _rounded(m > 0.3, n * 0.08) * (0.6 + 0.4 * m), m > 0.3


def knotwork(h, w, cell, line=0.16, ss=2):
    """Moorish interlace: two families of diagonal bands crossing, with an over/under weave and an
    eight-point star in every cell (the capitals and spandrels of the Alhambra hall)."""
    y, x = np.mgrid[0:h, 0:w].astype(float)
    u = (x + y) / cell
    v = (x - y) / cell
    du = np.abs(u - np.round(u))
    dv = np.abs(v - np.round(v))
    band_u = du < line
    band_v = dv < line
    # Weave: at crossings alternate which band is on top.
    cross = band_u & band_v
    parity = ((np.floor(u + 0.5) + np.floor(v + 0.5)) % 2) == 0
    hu = np.where(band_u, 1.0 - du / line, 0.0)
    hv = np.where(band_v, 1.0 - dv / line, 0.0)
    top = np.where(cross, np.where(parity, hu, hv), np.maximum(hu, hv))
    # Stars in the diamonds' middles.
    cu = u - np.floor(u) - 0.5
    cv = v - np.floor(v) - 0.5
    rr = np.hypot(cu, cv)
    ang = np.arctan2(cv, cu)
    star_r = 0.17 + 0.07 * np.cos(8 * ang)
    star = np.clip((star_r - rr) / 0.05, 0, 1)
    height = np.maximum(np.sqrt(np.clip(top, 0, 1)), star * 0.75)
    mask = (top > 0) | (star > 0.5)
    return height, mask


def lattice_fret(h, w, cell, bar=0.13):
    """Open fretwork lattice (sebka-like): rotated squares and diamonds; mask True = solid."""
    y, x = np.mgrid[0:h, 0:w].astype(float)
    u = x / cell
    v = y / cell
    fu = u - np.floor(u) - 0.5
    fv = v - np.floor(v) - 0.5
    diamond = np.abs(fu) + np.abs(fv)
    ring = np.abs(diamond - 0.38) < bar * 0.5
    grid_ = (np.abs(fu) > 0.5 - bar * 0.35) | (np.abs(fv) > 0.5 - bar * 0.35)
    circ = np.abs(np.hypot(fu, fv) - 0.17) < bar * 0.35
    return ring | grid_ | circ


def star_tiles(r, h, w, cell):
    """Zellige-style floor: eight-point stars with crosses between (ids per tile kind)."""
    y, x = np.mgrid[0:h, 0:w].astype(float)
    u = x / cell
    v = y / cell
    fu = u - np.floor(u) - 0.5
    fv = v - np.floor(v) - 0.5
    sq = np.maximum(np.abs(fu), np.abs(fv))
    rot = np.maximum(np.abs(fu + fv), np.abs(fu - fv)) / math.sqrt(2)
    star = np.maximum(sq, rot * 1.0) < 0.30
    joint = (np.abs(np.maximum(sq, rot) - 0.30) < 0.012) | (np.abs(np.abs(fu) - 0.5) < 0.012) | (np.abs(np.abs(fv) - 0.5) < 0.012)
    return star, joint
