"""Mesh building for Level 228's props (see level228_props.py).

A prop mesh is a list of quads (triangles are quads with a repeated last corner) in prop-local block
units: the origin is the minimum corner of the prop's anchor block, y = 0 is the floor the prop
stands on, +y up. The game (client/level228/Level228PropMeshes) turns a mesh by quarter turns about
the anchor block's vertical centre line (0.5, *, 0.5) and mirrors it about its pivot.

Every quad points at a texture Region by name with region-local UVs in 0..1; sheet.py packs the
regions into the texture sheets afterwards and resolves the final UVs. Shapes:
  lathe          turned wood, balusters, bulbs, round or n-sided (square/octagonal) profiles
  box / bevel    boxes with chamfered edges (plinths, abaci, crates)
  sweep          a moulding profile swept round a (mitred) path: picture frames, rails, cornices
  mask_extrude   a 2D cut-out (arch, fretwork, lettering) with real thickness: front and back are
                 single alpha-cut quads showing the mask's texture, the sides follow its outline
  tube           a round strand along a polyline (rope, cord, cable)
  surface        a parametric grid (sagging canvas)
Shade (vertex grey) carries the ambient occlusion the textures cannot know about: concave corners
of profiles, contact with the floor and ceiling.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field

import numpy as np

LAYER_CUT = 0     # static: lit, alpha-cut, both sides drawn (baked into the chunk vertex buffers)
LAYER_GLOW = 1    # drawn every frame: bulb glass / sign faces whose brightness follows Level228Bulbs.glow
LAYER_GLASS = 2   # drawn every frame, sorted: see-through glass (ticket booth window)

EMIT_BULB, EMIT_CANDLE, EMIT_MARQUEE, EMIT_SIGN, EMIT_COLD = range(5)


def v3(*a) -> np.ndarray:
    return np.asarray(a if len(a) > 1 else a[0], dtype=np.float64)


def norm(v) -> np.ndarray:
    v = np.asarray(v, dtype=np.float64)
    n = np.linalg.norm(v)
    return v / n if n > 1e-12 else v


def smoothstep(a, b, x):
    t = np.clip((np.asarray(x, float) - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


@dataclass
class Quad:
    p: np.ndarray          # (4, 3)
    uv: np.ndarray         # (4, 2) region-local
    n: np.ndarray          # (4, 3)
    shade: np.ndarray      # (4,)
    region: str
    layer: int = LAYER_CUT
    emitter: int = 0


@dataclass
class Emitter:
    pos: tuple
    kind: int
    size: float


@dataclass
class Mesh:
    name: str
    pivot: tuple = (0.5, 0.5)
    quads: list = field(default_factory=list)
    emitters: list = field(default_factory=list)

    # ---- raw quads -------------------------------------------------------------------------------

    def quad(self, p, uv, region, n=None, shade=None, layer=LAYER_CUT, emitter=0, flip=False):
        p = np.asarray(p, dtype=np.float64).reshape(4, 3)
        uv = np.asarray(uv, dtype=np.float64).reshape(4, 2)
        if flip:
            p = p[::-1].copy()
            uv = uv[::-1].copy()
        if n is None:
            a = p[2] - p[0]
            b = p[3] - p[1] if np.linalg.norm(p[3] - p[2]) > 1e-9 else p[2] - p[1]
            if np.linalg.norm(p[3] - p[2]) <= 1e-9:
                a, b = p[1] - p[0], p[2] - p[0]
            fn = norm(np.cross(a, b))
            n = np.tile(fn, (4, 1))
        else:
            n = np.asarray(n, dtype=np.float64)
            if n.ndim == 1:
                n = np.tile(n, (4, 1))
            elif flip:
                n = n[::-1].copy()
        if shade is None:
            shade = np.ones(4)
        else:
            shade = np.asarray(shade, dtype=np.float64)
            if shade.ndim == 0:
                shade = np.full(4, float(shade))
            elif flip:
                shade = shade[::-1].copy()
        self.quads.append(Quad(p, uv, n, shade, region, layer, emitter))

    def tri(self, a, b, c, uva, uvb, uvc, region, n=None, shade=None, layer=LAYER_CUT, emitter=0):
        sh = None if shade is None else np.asarray([shade[0], shade[1], shade[2], shade[2]], float) \
            if np.ndim(shade) else shade
        nn = n
        if n is not None and np.ndim(n) == 2:
            nn = np.asarray([n[0], n[1], n[2], n[2]])
        self.quad([a, b, c, c], [uva, uvb, uvc, uvc], region, nn, sh, layer, emitter)

    def emitter(self, pos, kind, size) -> int:
        self.emitters.append(Emitter(tuple(float(c) for c in pos), kind, float(size)))
        return len(self.emitters) - 1

    # ---- combining -------------------------------------------------------------------------------

    def add(self, other: "Mesh", m: np.ndarray | None = None, t=(0.0, 0.0, 0.0)):
        """Append another mesh's quads transformed by 3x3 matrix m then translation t."""
        m = np.eye(3) if m is None else np.asarray(m, float)
        t = np.asarray(t, float)
        nm = np.linalg.inv(m).T
        mirrored = np.linalg.det(m) < 0
        base = len(self.emitters)
        for q in other.quads:
            p = q.p @ m.T + t
            n = q.n @ nm.T
            n /= np.maximum(np.linalg.norm(n, axis=1, keepdims=True), 1e-12)
            if mirrored:
                p, n = p[::-1].copy(), n[::-1].copy()
                uv, sh = q.uv[::-1].copy(), q.shade[::-1].copy()
            else:
                uv, sh = q.uv.copy(), q.shade.copy()
            self.quads.append(Quad(p, uv, n, sh, q.region, q.layer, q.emitter + base if q.layer == LAYER_GLOW else 0))
        for e in other.emitters:
            self.emitters.append(Emitter(tuple(np.asarray(e.pos) @ m.T + t), e.kind, e.size))
        return self

    def bounds(self):
        pts = np.concatenate([q.p for q in self.quads]) if self.quads else np.zeros((1, 3))
        return pts.min(axis=0), pts.max(axis=0)

    def contact_ao(self, floor=0.0, ceiling=None, reach=0.45, strength=0.45):
        """Darken vertices near the floor (and the ceiling): the light under a skirting, at the foot
        of a column, against a ceiling, which a lightmap one block coarse cannot show."""
        for q in self.quads:
            y = q.p[:, 1]
            f = 1.0 - strength * (1.0 - smoothstep(floor, floor + reach, y))
            if ceiling is not None:
                f *= 1.0 - 0.6 * strength * (1.0 - smoothstep(ceiling, ceiling - reach * 0.7, y))
            q.shade = q.shade * f
        return self


def rot_y(quarters_or_angle: float, degrees=False) -> np.ndarray:
    a = math.radians(quarters_or_angle) if degrees else quarters_or_angle
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]], float)


def scale(x, y, z) -> np.ndarray:
    return np.diag([x, y, z]).astype(float)


# ------------------------------------------------------------------------------------------------
# Lathe
# ------------------------------------------------------------------------------------------------

def lathe(m: Mesh, profile, n: int, region: str, centre=(0.5, 0.0, 0.5), phase=0.0, flat=False,
          u_turns=1.0, v0=1.0, v1=0.0, crease_deg=38.0, concave_ao=0.45, layer=LAYER_CUT, emitter=0,
          arc=(0.0, 2 * math.pi), u_offset=0.0):
    """Surface of revolution of a (r, y) profile (bottom to top) with n segments around. flat: an
    n-sided prism (r is the apothem, flat faces, sharp edges: square plinths, octagonal capitals).
    V follows the profile's arc length from v0 to v1, U goes once round in u_turns of the region."""
    prof = np.asarray(profile, float)
    cx, cy, cz = centre
    seg = np.diff(prof, axis=0)
    seglen = np.hypot(seg[:, 0], seg[:, 1])
    total = seglen.sum()
    vs = v0 + (v1 - v0) * np.concatenate([[0.0], np.cumsum(seglen)]) / max(total, 1e-9)
    # Per-segment profile normals (r, y components).
    sn = np.stack([seg[:, 1], -seg[:, 0]], axis=1) / np.maximum(seglen[:, None], 1e-12)
    # Per-point shade from concave turns (inside corners collect dirt and get no light).
    shade = np.ones(len(prof))
    for i in range(1, len(prof) - 1):
        if seglen[i - 1] < 1e-9 or seglen[i] < 1e-9:
            continue
        ta, tb = seg[i - 1] / seglen[i - 1], seg[i] / seglen[i]
        cross = ta[0] * tb[1] - ta[1] * tb[0]
        if cross < 0:
            shade[i] = 1.0 - concave_ao * min(1.0, -cross * 1.3)
    cos_c = math.cos(math.radians(crease_deg))
    a0, a1 = arc
    full = abs((a1 - a0) - 2 * math.pi) < 1e-6
    rscale = 1.0 / math.cos(math.pi / n) if flat else 1.0
    for k in range(n):
        t0 = a0 + (a1 - a0) * k / n + phase
        t1 = a0 + (a1 - a0) * (k + 1) / n + phase
        tm = 0.5 * (t0 + t1)
        u_a = u_offset + u_turns * k / n
        u_b = u_offset + u_turns * (k + 1) / n
        for i in range(len(seg)):
            if seglen[i] < 1e-9:
                continue
            (r0, y0), (r1, y1) = prof[i], prof[i + 1]
            if r0 <= 1e-9 and r1 <= 1e-9:
                continue

            def vnormal(j_point, end):
                # Smooth along the profile unless the neighbouring segment turns too sharply.
                nr, ny = sn[i]
                other = i - 1 if end == 0 else i + 1
                if 0 <= other < len(seg) and seglen[other] > 1e-9 and np.dot(sn[i], sn[other]) > cos_c:
                    nr, ny = norm(sn[i] + sn[other])
                return nr, ny

            nr0, ny0 = vnormal(i, 0)
            nr1, ny1 = vnormal(i + 1, 1)

            def pos(r, y, t):
                rr = r * rscale
                return (cx + rr * math.cos(t), cy + y, cz + rr * math.sin(t))

            def nrm(nr, ny, t):
                tt = tm if flat else t
                return norm((nr * math.cos(tt), ny, nr * math.sin(tt)))

            p = [pos(r0, y0, t0), pos(r0, y0, t1), pos(r1, y1, t1), pos(r1, y1, t0)]
            nn = [nrm(nr0, ny0, t0), nrm(nr0, ny0, t1), nrm(nr1, ny1, t1), nrm(nr1, ny1, t0)]
            uv = [(u_a, vs[i]), (u_b, vs[i]), (u_b, vs[i + 1]), (u_a, vs[i + 1])]
            sh = [shade[i], shade[i], shade[i + 1], shade[i + 1]]
            m.quad(p, uv, region, nn, sh, layer, emitter)
    return m


def rail(m: Mesh, profile, x0: float, x1: float, region: str, rsize=(1.0, 1.0), u_off=0.0, v_off=0.0,
         crease_deg=40.0, concave_ao=0.4, shade=1.0, caps=False, layer=LAYER_CUT):
    """A moulding running along x from x0 to x1: profile is a polyline of (z, y) points (the cross
    section, e.g. a skirting or a cornice). U follows x and V the profile, both at physical scale
    (rsize = region size in blocks). The visible side of each segment is to the RIGHT walking along
    the profile seen from +x: list points so the outside is on your right (e.g. going up the front
    face of something on the +z side). caps: close both ends (convex-ish profiles only)."""
    prof = np.asarray(profile, float)
    seg = np.diff(prof, axis=0)
    seglen = np.hypot(seg[:, 0], seg[:, 1])
    cum = np.concatenate([[0.0], np.cumsum(seglen)])
    # Right-hand normal of (dz, dy) seen from +x looking toward -x: (dy... ) -> (z, y) = (dy, -dz)
    # mirrored so the outward side is right of the walking direction.
    sn = np.stack([seg[:, 1], -seg[:, 0]], axis=1) / np.maximum(seglen[:, None], 1e-12)
    psh = np.ones(len(prof))
    for i in range(1, len(prof) - 1):
        if seglen[i - 1] < 1e-9 or seglen[i] < 1e-9:
            continue
        ta, tb = seg[i - 1] / seglen[i - 1], seg[i] / seglen[i]
        cr = ta[0] * tb[1] - ta[1] * tb[0]
        if cr > 0:
            psh[i] = 1.0 - concave_ao * min(1.0, cr * 1.3)
    cos_c = math.cos(math.radians(crease_deg))
    rw, rh = rsize
    for i in range(len(seg)):
        if seglen[i] < 1e-9:
            continue

        def vn(end):
            nz, ny = sn[i]
            other = i - 1 if end == 0 else i + 1
            if 0 <= other < len(seg) and seglen[other] > 1e-9 and np.dot(sn[i], sn[other]) > cos_c:
                nz, ny = norm(sn[i] + sn[other])
            return np.array([0.0, ny, nz])

        (za, ya), (zb, yb) = prof[i], prof[i + 1]
        na, nb = vn(0), vn(1)
        va, vb = v_off + cum[i] / rh, v_off + cum[i + 1] / rh
        ua, ub = u_off, u_off + (x1 - x0) / rw
        m.quad([(x0, ya, za), (x1, ya, za), (x1, yb, zb), (x0, yb, zb)],
               [(ua, va), (ub, va), (ub, vb), (ua, vb)], region, [na, na, nb, nb],
               np.array([psh[i], psh[i], psh[i + 1], psh[i + 1]]) * shade, layer)
    if caps:
        for x, s in ((x0, -1.0), (x1, 1.0)):
            poly = [(x, y, z) for (z, y) in prof]
            uvs = [((z - prof[:, 0].min()) / rw, (prof[:, 1].max() - y) / rh) for (z, y) in prof]
            polygon(m, poly, uvs, region, (s, 0.0, 0.0), shade * 0.85, layer)
    return m


def polygon(m: Mesh, pts, uvs, region: str, normal, shade=1.0, layer=LAYER_CUT):
    """A flat simple polygon (any winding), ear-clipped into triangles."""
    P = np.asarray(pts, float)
    nrm = norm(normal)
    # Project to 2D on the plane.
    ax = np.argmax(np.abs(nrm))
    keep = [i for i in range(3) if i != ax]
    Q = P[:, keep]
    idx = list(range(len(P)))
    area = 0.0
    for i in range(len(Q)):
        a, b = Q[i], Q[(i + 1) % len(Q)]
        area += a[0] * b[1] - b[0] * a[1]
    if area < 0:
        idx.reverse()

    def inside(p, a, b, c):
        def s(p1, p2, p3):
            return (p1[0] - p3[0]) * (p2[1] - p3[1]) - (p2[0] - p3[0]) * (p1[1] - p3[1])
        d1, d2, d3 = s(p, a, b), s(p, b, c), s(p, c, a)
        return d1 >= 0 and d2 >= 0 and d3 >= 0

    guard = 0
    while len(idx) > 3 and guard < 10000:
        guard += 1
        for k in range(len(idx)):
            i0, i1, i2 = idx[k - 1], idx[k], idx[(k + 1) % len(idx)]
            a, b, c = Q[i0], Q[i1], Q[i2]
            if (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]) <= 1e-12:
                continue
            if any(inside(Q[j], a, b, c) for j in idx if j not in (i0, i1, i2)):
                continue
            m.tri(P[i0], P[i1], P[i2], uvs[i0], uvs[i1], uvs[i2], region, n=nrm, shade=shade, layer=layer)
            idx.pop(k)
            break
        else:
            break
    if len(idx) == 3:
        i0, i1, i2 = idx
        m.tri(P[i0], P[i1], P[i2], uvs[i0], uvs[i1], uvs[i2], region, n=nrm, shade=shade, layer=layer)
    return m


# ------------------------------------------------------------------------------------------------
# Boxes
# ------------------------------------------------------------------------------------------------

def _face_uv(a_len, b_len, rw, rh, off=(0.0, 0.0)):
    """Region-local UVs for a face a_len x b_len blocks on a region rw x rh blocks big."""
    u0, v0 = off
    u1 = u0 + a_len / rw
    v1 = v0 + b_len / rh
    return [(u0, v1), (u1, v1), (u1, v0), (u0, v0)]


def box(m: Mesh, lo, hi, region: str, rsize=(1.0, 1.0), faces="xXyYzZ", off=(0.0, 0.0), shade=1.0,
        layer=LAYER_CUT, emitter=0, uv_rot=False):
    """Axis-aligned box lo..hi. Each face samples the region at physical scale (rsize = the region's
    size in blocks), starting at off (region-local)."""
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    rw, rh = rsize
    sx, sy, sz = x1 - x0, y1 - y0, z1 - z0

    def f(p, n, a, b):
        uv = _face_uv(b, a, rw, rh, off) if uv_rot else _face_uv(a, b, rw, rh, off)
        if uv_rot:
            uv = [uv[1], uv[2], uv[3], uv[0]]
        m.quad(p, uv, region, n, shade, layer, emitter)

    if "x" in faces:
        f([(x0, y0, z1), (x0, y0, z0), (x0, y1, z0), (x0, y1, z1)], (-1, 0, 0), sz, sy)
    if "X" in faces:
        f([(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)], (1, 0, 0), sz, sy)
    if "y" in faces:
        f([(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)], (0, -1, 0), sx, sz)
    if "Y" in faces:
        f([(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], (0, 1, 0), sx, sz)
    if "z" in faces:
        f([(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)], (0, 0, -1), sx, sy)
    if "Z" in faces:
        f([(x1, y0, z1), (x0, y0, z1), (x0, y1, z1), (x1, y1, z1)], (0, 0, 1), sx, sy)
    return m


def bevel_box(m: Mesh, lo, hi, b: float, region: str, rsize=(1.0, 1.0), off=(0.0, 0.0), shade=1.0,
              edge_shade=0.82, layer=LAYER_CUT, bottom=True):
    """A box with all twelve edges chamfered by b (plinths, abaci, crate boards, sign boxes): the
    chamfers catch light along every edge, which is most of what makes a box read as made."""
    lo = np.asarray(lo, float)
    hi = np.asarray(hi, float)
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    rw, rh = rsize
    # Inset faces.
    def uvr(a0, a1, b0, b1):
        return [(off[0] + a0 / rw, off[1] + b1 / rh), (off[0] + a1 / rw, off[1] + b1 / rh),
                (off[0] + a1 / rw, off[1] + b0 / rh), (off[0] + a0 / rw, off[1] + b0 / rh)]
    sx, sy, sz = hi - lo
    # -x / +x
    m.quad([(x0, y0 + b, z1 - b), (x0, y0 + b, z0 + b), (x0, y1 - b, z0 + b), (x0, y1 - b, z1 - b)],
           uvr(b, sz - b, b, sy - b), region, (-1, 0, 0), shade, layer)
    m.quad([(x1, y0 + b, z0 + b), (x1, y0 + b, z1 - b), (x1, y1 - b, z1 - b), (x1, y1 - b, z0 + b)],
           uvr(b, sz - b, b, sy - b), region, (1, 0, 0), shade, layer)
    m.quad([(x0 + b, y0 + b, z0), (x1 - b, y0 + b, z0), (x1 - b, y1 - b, z0), (x0 + b, y1 - b, z0)],
           uvr(b, sx - b, b, sy - b), region, (0, 0, -1), shade, layer)
    m.quad([(x1 - b, y0 + b, z1), (x0 + b, y0 + b, z1), (x0 + b, y1 - b, z1), (x1 - b, y1 - b, z1)],
           uvr(b, sx - b, b, sy - b), region, (0, 0, 1), shade, layer)
    m.quad([(x0 + b, y1, z0 + b), (x1 - b, y1, z0 + b), (x1 - b, y1, z1 - b), (x0 + b, y1, z1 - b)],
           uvr(b, sx - b, b, sz - b), region, (0, 1, 0), shade, layer)
    if bottom:
        m.quad([(x0 + b, y0, z1 - b), (x1 - b, y0, z1 - b), (x1 - b, y0, z0 + b), (x0 + b, y0, z0 + b)],
               uvr(b, sx - b, b, sz - b), region, (0, -1, 0), shade, layer)
    es = shade * edge_shade
    c = 0.7071
    # Vertical edges.
    for (xa, za, nx, nz) in ((x0, z0, -c, -c), (x1, z0, c, -c), (x1, z1, c, c), (x0, z1, -c, c)):
        pa = (xa - nx / c * 0 + (b if nx < 0 else -b), za)  # placeholder (unused)
        ex = xa
        ez = za
        # The chamfer runs from the -x/+x face edge to the -z/+z face edge.
        p_on_x = (ex, ez - b if nz > 0 else ez + b)
        p_on_z = (ex - b if nx > 0 else ex + b, ez)
        m.quad([(p_on_x[0], y0 + b, p_on_x[1]), (p_on_z[0], y0 + b, p_on_z[1]),
                (p_on_z[0], y1 - b, p_on_z[1]), (p_on_x[0], y1 - b, p_on_x[1])],
               uvr(0, b * 1.41, b, sy - b), region, (nx, 0, nz), es, layer)
    # Top edges (and bottom).
    for yy, ny in ((y1, 1), (y0, -1)) if bottom else ((y1, 1),):
        yi = yy - b * ny
        m.quad([(x0 + b, yy, z0 + b), (x1 - b, yy, z0 + b), (x1 - b, yi, z0), (x0 + b, yi, z0)],
               uvr(b, sx - b, 0, b * 1.41), region, (0, ny * c, -c), es, layer)
        m.quad([(x1 - b, yy, z1 - b), (x0 + b, yy, z1 - b), (x0 + b, yi, z1), (x1 - b, yi, z1)],
               uvr(b, sx - b, 0, b * 1.41), region, (0, ny * c, c), es, layer)
        m.quad([(x0 + b, yy, z1 - b), (x0 + b, yy, z0 + b), (x0, yi, z0 + b), (x0, yi, z1 - b)],
               uvr(b, sz - b, 0, b * 1.41), region, (-c, ny * c, 0), es, layer)
        m.quad([(x1 - b, yy, z0 + b), (x1 - b, yy, z1 - b), (x1, yi, z1 - b), (x1, yi, z0 + b)],
               uvr(b, sz - b, 0, b * 1.41), region, (c, ny * c, 0), es, layer)
        # Corner triangles.
        k = 0.577
        for (xs, zs) in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
            cxp = x0 if xs < 0 else x1
            czp = z0 if zs < 0 else z1
            a = (cxp - xs * b, yy, czp - zs * b)
            bb = (cxp, yi, czp - zs * b)
            cc = (cxp - xs * b, yi, czp)
            m.tri(a, bb, cc, (0, 0), (b / rw, 0), (0, b / rh), region, n=(xs * k, ny * k, zs * k),
                  shade=es * 0.95, layer=layer)
    return m


# ------------------------------------------------------------------------------------------------
# Sweeps (mouldings)
# ------------------------------------------------------------------------------------------------

def sweep(m: Mesh, profile, path, origin, U, V, W, region: str, closed=True, u_scale=1.0,
          v0=0.0, v1=1.0, crease_deg=40.0, concave_ao=0.4, shade=1.0, per_side_uv=True, layer=LAYER_CUT):
    """Sweep a moulding cross-section along a path lying in a plane.

    The plane: point = origin + s*U + t*V; W (= U x V for a counter-clockwise path seen from the
    front) points out of the wall toward the viewer. The path is a list of (s, t) points; for a closed
    counter-clockwise path the profile's first coordinate o offsets INTO the enclosed shape (toward
    the opening of a picture frame) and the second d comes out along W. Corners are mitred.
    U runs along each side (restarting at every corner when per_side_uv, so a carved pattern fits
    each side of a frame), scaled to u_scale region-widths per block; V follows the profile."""
    origin = np.asarray(origin, float)
    U = np.asarray(U, float)
    V = np.asarray(V, float)
    W = np.asarray(W, float)
    pts = np.asarray(path, float)
    npts = len(pts)
    prof = np.asarray(profile, float)
    seg = np.diff(prof, axis=0)
    seglen = np.hypot(seg[:, 0], seg[:, 1])
    tot = max(seglen.sum(), 1e-9)
    vs = v0 + (v1 - v0) * np.concatenate([[0.0], np.cumsum(seglen)]) / tot
    # Profile normals in (o, d): the visible side of the moulding faces away from the wall (d+)
    # and away from the opening (-o) for convex beads.
    pn = np.stack([seg[:, 1], -seg[:, 0]], axis=1) / np.maximum(seglen[:, None], 1e-12)
    pn = -pn
    # Concavity shade per profile point.
    psh = np.ones(len(prof))
    for i in range(1, len(prof) - 1):
        if seglen[i - 1] < 1e-9 or seglen[i] < 1e-9:
            continue
        ta, tb = seg[i - 1] / seglen[i - 1], seg[i] / seglen[i]
        cr = ta[0] * tb[1] - ta[1] * tb[0]
        if cr > 0:
            psh[i] = 1.0 - concave_ao * min(1.0, cr * 1.3)
    cos_c = math.cos(math.radians(crease_deg))
    nseg = npts if closed else npts - 1

    def inward(i):
        a = pts[i]
        b = pts[(i + 1) % npts]
        d = norm(b - a)
        return np.array([-d[1], d[0]])  # left normal: inside for a CCW path

    def corner_offset(i):
        # Miter vector at path vertex i (in (s, t)), scaled so the offset stays o from both sides.
        if not closed and (i == 0 or i == npts - 1):
            return inward(0 if i == 0 else npts - 2)
        na = inward((i - 1) % npts)
        nb = inward(i % npts)
        mv = na + nb
        c = np.dot(norm(mv), na)
        return norm(mv) / max(c, 0.2)

    for si in range(nseg):
        ia, ib = si, (si + 1) % npts
        A, B = pts[ia], pts[ib]
        L = np.linalg.norm(B - A)
        inw = inward(si)
        ca, cb = corner_offset(ia), corner_offset(ib)
        n_in3 = inw[0] * U + inw[1] * V
        for k in range(len(seg)):
            if seglen[k] < 1e-9:
                continue
            (oa, da), (ob, db) = prof[k], prof[k + 1]

            def P(base, cvec, o, d):
                st = base + cvec * o
                return origin + st[0] * U + st[1] * V + d * W

            def nrm(end):
                no, nd = pn[k]
                other = k - 1 if end == 0 else k + 1
                if 0 <= other < len(seg) and seglen[other] > 1e-9 and np.dot(pn[k], pn[other]) > cos_c:
                    no, nd = norm(pn[k] + pn[other])
                return norm(no * n_in3 + nd * W)

            p = [P(A, ca, oa, da), P(B, cb, oa, da), P(B, cb, ob, db), P(A, ca, ob, db)]
            n0, n1 = nrm(0), nrm(1)
            ua = 0.0 if per_side_uv else si * L
            ub = ua + L * u_scale
            uv = [(ua, vs[k]), (ub, vs[k]), (ub, vs[k + 1]), (ua, vs[k + 1])]
            sh = np.array([psh[k], psh[k], psh[k + 1], psh[k + 1]]) * shade
            m.quad(p, uv, region, [n0, n0, n1, n1], sh, layer)
    return m


def rect_path(s0, t0, s1, t1):
    """Counter-clockwise rectangle in (s, t)."""
    return [(s0, t0), (s1, t0), (s1, t1), (s0, t1)]


# ------------------------------------------------------------------------------------------------
# Cut-out extrusion
# ------------------------------------------------------------------------------------------------

def mask_extrude(m: Mesh, mask: np.ndarray, size, origin, U, V, W, depth: float, face_region: str,
                 edge_region: str, edge_rsize=(1.0, 1.0), back=True, front_shade=1.0, edge_shade=0.85,
                 layer=LAYER_CUT, back_region=None):
    """A flat cut-out with thickness. mask is (rows, cols) bool, row 0 at the TOP; size = (width,
    height) in blocks; the shape spans origin + [0,w]*U + [0,h]*V, from W*0 (back) to W*depth
    (front). The front and back are one quad each sampling face_region (its alpha must equal the
    mask); the sides are the outline's pixel edges, merged into runs."""
    origin = np.asarray(origin, float)
    U, V, W = (np.asarray(a, float) for a in (U, V, W))
    rows, cols = mask.shape
    w, h = size
    px_w, px_h = w / cols, h / rows

    def P(c, r, d):
        # c: column edge 0..cols, r: row edge 0..rows (0 = top)
        return origin + (c * px_w) * U + (h - r * px_h) * V + d * W

    full = [(0.0, 1.0), (1.0, 1.0), (1.0, 0.0), (0.0, 0.0)]
    m.quad([P(0, rows, depth), P(cols, rows, depth), P(cols, 0, depth), P(0, 0, depth)], full,
           face_region, norm(W), front_shade, layer)
    if back:
        m.quad([P(cols, rows, 0), P(0, rows, 0), P(0, 0, 0), P(cols, 0, 0)],
               [(1.0, 1.0), (0.0, 1.0), (0.0, 0.0), (1.0, 0.0)], back_region or face_region, -norm(W), front_shade * 0.9,
               layer)
    er_w, er_h = edge_rsize
    padded = np.pad(mask, 1, constant_values=False)
    # Horizontal edges (between row r-1 and r): runs along columns.
    for r in range(rows + 1):
        above = padded[r, 1:-1]
        below = padded[r + 1, 1:-1]
        for kind, sel in ((1, above & ~below), (-1, below & ~above)):
            # kind 1: solid above, empty below -> faces down (-V); -1 faces up (+V)
            c = 0
            while c < cols:
                if sel[c]:
                    c0 = c
                    while c < cols and sel[c]:
                        c += 1
                    a = P(c0, r, 0)
                    b = P(c, r, 0)
                    nrm = -norm(V) * kind
                    run = (c - c0) * px_w
                    uv = [(0, 0), (run / er_w, 0), (run / er_w, depth / er_h), (0, depth / er_h)]
                    sh = edge_shade * (0.75 if kind == 1 else 1.0)
                    m.quad([a, b, b + depth * W, a + depth * W], uv, edge_region, nrm, sh, layer)
                else:
                    c += 1
    # Vertical edges (between column c-1 and c): runs along rows.
    for c in range(cols + 1):
        left = padded[1:-1, c]
        right = padded[1:-1, c + 1]
        for kind, sel in ((1, left & ~right), (-1, right & ~left)):
            r = 0
            while r < rows:
                if sel[r]:
                    r0 = r
                    while r < rows and sel[r]:
                        r += 1
                    a = P(c, r0, 0)
                    b = P(c, r, 0)
                    nrm = norm(U) * kind
                    run = (r - r0) * px_h
                    uv = [(0, 0), (run / er_w, 0), (run / er_w, depth / er_h), (0, depth / er_h)]
                    m.quad([a, b, b + depth * W, a + depth * W], uv, edge_region, nrm, edge_shade * 0.9, layer)
                else:
                    r += 1
    return m


def simplify_mask(mask: np.ndarray, factor: int) -> np.ndarray:
    """Downsample a high-resolution mask (for the outline geometry) by majority."""
    r, c = mask.shape
    r2, c2 = r // factor, c // factor
    return mask[:r2 * factor, :c2 * factor].reshape(r2, factor, c2, factor).mean(axis=(1, 3)) > 0.5


# ------------------------------------------------------------------------------------------------
# Strands and surfaces
# ------------------------------------------------------------------------------------------------

def tube(m: Mesh, points, radius, sides: int, region: str, v_per_block=1.0, shade=1.0, layer=LAYER_CUT,
         radii=None):
    """A round strand along a polyline: rope, cord, cable. U goes round, V along (v_per_block region
    heights per block of length)."""
    pts = np.asarray(points, float)
    n = len(pts)
    tangents = np.zeros_like(pts)
    for i in range(n):
        a = pts[max(i - 1, 0)]
        b = pts[min(i + 1, n - 1)]
        tangents[i] = norm(b - a)
    up = np.array([0.0, 1.0, 0.0])
    frames = []
    ref = norm(np.cross(tangents[0], up)) if abs(np.dot(tangents[0], up)) < 0.95 else np.array([1.0, 0, 0])
    for i in range(n):
        t = tangents[i]
        b = norm(np.cross(t, ref))
        nn = norm(np.cross(b, t))
        ref = nn
        frames.append((nn, b))
    lens = np.concatenate([[0.0], np.cumsum(np.linalg.norm(np.diff(pts, axis=0), axis=1))])
    for i in range(n - 1):
        ra = radius if radii is None else radii[i]
        rb = radius if radii is None else radii[i + 1]
        for k in range(sides):
            a0 = 2 * math.pi * k / sides
            a1 = 2 * math.pi * (k + 1) / sides

            def ring(j, a, r):
                nn, b = frames[j]
                d = nn * math.cos(a) + b * math.sin(a)
                return pts[j] + d * r, d

            p00, d00 = ring(i, a0, ra)
            p01, d01 = ring(i, a1, ra)
            p11, d11 = ring(i + 1, a1, rb)
            p10, d10 = ring(i + 1, a0, rb)
            va, vb = lens[i] * v_per_block, lens[i + 1] * v_per_block
            m.quad([p00, p01, p11, p10], [(k / sides, va), ((k + 1) / sides, va), ((k + 1) / sides, vb), (k / sides, vb)],
                   region, [d00, d01, d11, d10], shade, layer)
    return m


def catenary(a, b, sag, n=12):
    """Points of a hanging strand from a to b sagging by sag at the middle."""
    a = np.asarray(a, float)
    b = np.asarray(b, float)
    out = []
    for i in range(n + 1):
        t = i / n
        p = a + (b - a) * t
        p[1] -= sag * (1.0 - (2 * t - 1) ** 2) ** 0.85 if 0 < t < 1 else 0.0
        out.append(p)
    return out


def surface(m: Mesh, f, nu: int, nv: int, region: str, uv_fn=None, shade_fn=None, layer=LAYER_CUT):
    """A parametric surface f(u, v) -> xyz over 0..1 x 0..1 (a sagging canvas ceiling, a curved
    awning). Normals by finite differences."""
    U = np.linspace(0, 1, nu + 1)
    Vv = np.linspace(0, 1, nv + 1)
    P = np.array([[f(u, v) for u in U] for v in Vv], float)
    N = np.zeros_like(P)
    for j in range(nv + 1):
        for i in range(nu + 1):
            du = P[j, min(i + 1, nu)] - P[j, max(i - 1, 0)]
            dv = P[min(j + 1, nv), i] - P[max(j - 1, 0), i]
            N[j, i] = norm(np.cross(du, dv))
    for j in range(nv):
        for i in range(nu):
            uv = [(U[i], Vv[j]), (U[i + 1], Vv[j]), (U[i + 1], Vv[j + 1]), (U[i], Vv[j + 1])]
            if uv_fn:
                uv = [uv_fn(*x) for x in uv]
            sh = None
            if shade_fn:
                sh = [shade_fn(U[i], Vv[j]), shade_fn(U[i + 1], Vv[j]), shade_fn(U[i + 1], Vv[j + 1]), shade_fn(U[i], Vv[j + 1])]
            m.quad([P[j, i], P[j, i + 1], P[j + 1, i + 1], P[j + 1, i]], uv, region,
                   [N[j, i], N[j, i + 1], N[j + 1, i + 1], N[j + 1, i]], sh, layer)
    return m


def card(m: Mesh, origin, U, V, w, h, region: str, uv=((0, 0), (1, 1)), shade=1.0, layer=LAYER_CUT, emitter=0,
         normal=None):
    """A flat textured rectangle origin + [0,w]*U + [0,h]*V (posters, notes, cobwebs). uv = the
    region-local (u0, v0 top), (u1, v1 bottom) corners."""
    origin = np.asarray(origin, float)
    U = norm(U)
    V = norm(V)
    (u0, v0), (u1, v1) = uv
    nrm = norm(np.cross(U, V)) if normal is None else normal
    m.quad([origin, origin + U * w, origin + U * w + V * h, origin + V * h],
           [(u0, v1), (u1, v1), (u1, v0), (u0, v0)], region, nrm, shade, layer, emitter)
    return m
