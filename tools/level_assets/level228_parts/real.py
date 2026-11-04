"""Preview shots of Level 228 built from the REAL floor plan (not shipped).

The plan's props and mirrors are dumped to JSON by the test-source tool
client/level228/Level228PropDump (plain java on the compiled classes, see level228_props.py
--real), then every record is resolved and placed here exactly as the game does it:
Level228PropCatalog picks the meshes, Level228Props places them (flip about the pivot, quarter
turns about the anchor block's centre), Level228MirrorSurfaces lays the aged surfaces over the
mirror records and the Glass Labyrinth's clear panes, and the lit emitters become the lights. So
what these shots show is what a player standing there sees (minus the stand-ins and Veil's fog).
"""
from __future__ import annotations

import json
import math
from pathlib import Path

import numpy as np

from . import geo as G
from .dims import PANE_Y1
from .rooms import placement

FEET_Y = 41
ZONES = ["FOYER", "CRYSTAL", "ALHAMBRA", "FUNHOUSE", "GLASS", "CHAMBER", "EXIT_HALL", "BACKSTAGE", "NONE"]
MAZE = {"CRYSTAL", "ALHAMBRA", "FUNHOUSE", "GLASS"}
(P_COLUMN, P_PANEL, P_OPENING, P_FLOOR, P_CEILING, P_WALL, P_BULB, P_SCONCE, P_EXIT_SIGN, P_FACADE, P_TICKET_BOOTH,
 P_TURNSTILE, P_STANCHION, P_POSTER, P_BUNTING, P_SHARDS, P_COBWEB, P_LITTER, P_NOTE, P_CRATE, P_MIRROR_STACK,
 P_BUCKET, P_BANNER, P_LEAVES, P_CHAMBER_MIRROR, P_ARRIVAL_MIRROR, P_CURTAIN, P_EXIT_DOOR, P_PALM, P_CHAIR) = range(1, 31)
PANEL_MIRROR, PANEL_GLASS, PANEL_EMPTY_FRAME, PANEL_SOLID = range(4)
LIGHT_LIT, LIGHT_FAILING, LIGHT_DEAD = range(3)
# Emitter colours (linear, preview units), after LightAgent's: warm 2500 K bulbs, candle, marquee,
# red EXIT sign, cold hallway/crack light.
LIGHT_COL = {G.EMIT_BULB: (1.55, 1.02, 0.58), G.EMIT_CANDLE: (1.0, 0.62, 0.30), G.EMIT_MARQUEE: (0.55, 0.36, 0.2),
             G.EMIT_SIGN: (0.9, 0.12, 0.08), G.EMIT_COLD: (0.55, 0.68, 0.9)}

_SIMPLE = {P_SCONCE: "sconce", P_EXIT_SIGN: "exit_sign", P_FACADE: "facade", P_TICKET_BOOTH: "ticket_booth",
           P_TURNSTILE: "turnstile", P_BUNTING: "bunting", P_COBWEB: "cobweb", P_CRATE: "crate",
           P_MIRROR_STACK: "mirror_stack", P_BUCKET: "bucket", P_BANNER: "banner", P_CHAMBER_MIRROR: "chamber_mirror",
           P_ARRIVAL_MIRROR: "arrival_mirror", P_CURTAIN: "curtain", P_EXIT_DOOR: "exit_door", P_PALM: "palm",
           P_CHAIR: "chair"}
_SERIES = {P_POSTER: ("poster_", 6), P_NOTE: ("note_", 3), P_SHARDS: ("shards_", 3), P_LITTER: ("litter_", 3),
           P_LEAVES: ("leaves_", 2)}


def _maze_style(z):
    return z.lower() if z in MAZE else None


def _surface_style(z):
    return {"CRYSTAL": "crystal", "NONE": "crystal", "ALHAMBRA": "alhambra", "FUNHOUSE": "funhouse", "GLASS": "glass",
            "FOYER": "foyer", "CHAMBER": "chamber", "EXIT_HALL": "exithall", "BACKSTAGE": "backstage"}[z]


def _wall_style(z):
    return {"FOYER": "foyer", "FUNHOUSE": "foyer", "CHAMBER": "chamber", "CRYSTAL": "chamber", "EXIT_HALL": "exithall",
            "ALHAMBRA": "exithall"}.get(z, "backstage")


def wall_variant(noise):
    """Port of Level228PropCatalog.wallVariant (32-bit integer hash): 0-1 plain faces 80 %,
    the sign/notice faces 2 and 3 at 10 % each."""
    m = 0xFFFFFFFF
    h = (noise * 0x9E3779B1) & m
    h ^= h >> 15
    h = (h * 0x85EBCA77) & m
    h ^= h >> 13
    r = (h >> 8) % 20
    return 0 if r < 8 else 1 if r < 16 else 2 if r < 18 else 3


def mesh_names(p, known=()):
    """Port of client/level228/Level228PropCatalog.meshes: the mesh names one record draws."""
    t, var, flags, noise = p["type"], p["variant"], p["flags"], p["noise"]
    z = ZONES[var] if 0 <= var < len(ZONES) else "NONE"
    maze = _maze_style(z)
    out = []
    if t == P_COLUMN and maze:
        out.append("column_" + maze)
    elif t == P_PANEL:
        if maze:
            out.append("panel_" + maze)
        kind = (flags >> 1) & 3
        if kind == PANEL_SOLID and maze:
            out.append("solid_" + maze)
        elif kind == PANEL_EMPTY_FRAME:
            out.append("panel_empty_shards")
    elif t == P_OPENING and maze:
        out.append("opening_" + maze)
    elif t == P_FLOOR:
        out.append("floor_" + _surface_style(z))
    elif t == P_CEILING:
        out.append("ceiling_" + _surface_style(z))
    elif t == P_WALL:
        v = wall_variant(noise)
        name = f"wall_{_wall_style(z)}_{v}"
        out.append(name if v < 3 or name in known else f"wall_{_wall_style(z)}_2")
    elif t == P_BULB:
        cage = z in ("GLASS", "EXIT_HALL") and "bulb_cage" in known
        if (flags >> 4) & 7 == G.EMIT_CANDLE and "chandelier" in known:
            out.append("chandelier")
        else:
            out.append("bulb_shade" if z == "CRYSTAL" else "bulb_cage" if cage else "bulb")
    elif t == P_STANCHION:
        out.append("stanchion_fallen" if var == 1 else "stanchion")
        if var != 1 and flags & 2:
            out.append("stanchion_rope")
    elif t == P_LITTER and var == 7:
        out.append("ticket_stub")
    elif t in _SIMPLE:
        out.append(_SIMPLE[t])
    elif t in _SERIES:
        pre, n = _SERIES[t]
        out.append(f"{pre}{var % n}")
    return out


def load(path):
    L = json.loads(Path(path).read_text())
    keys = ["type", "variant", "x", "y", "z", "rot", "flags", "aux", "noise"]
    L["props"] = [dict(zip(keys, r)) for r in L["props"]]
    mk = ["id", "axis", "plane", "min", "max", "y0", "y1", "kind", "surface", "zone", "flags", "front", "noise"]
    L["mirrors"] = [dict(zip(mk, r)) for r in L["mirrors"]]
    return L


class Local:
    """A mesh turned into flat arrays once (prop-local), split into opaque/glow/glass batches."""

    def __init__(self, R, mesh, sheet_of):
        self.mesh = mesh
        self.parts = {}  # (sheet, layer) -> (pos, uv, n, shade)
        for q in mesh.quads:
            key = (sheet_of(q.region), q.layer)
            uv = np.array([R.uv_of(q.region, q.uv[k, 0], q.uv[k, 1]) for k in range(4)])
            self.parts.setdefault(key, []).append((q.p, uv, q.n, q.shade))
        for k, lst in list(self.parts.items()):
            idx = [0, 1, 2, 0, 2, 3]
            self.parts[k] = (np.concatenate([a[0][idx] for a in lst]), np.concatenate([a[1][idx] for a in lst]),
                             np.concatenate([a[2][idx] for a in lst]), np.concatenate([a[3][idx] for a in lst]))


class RealScene:
    def __init__(self, R, meshes, sheet_of, layout):
        self.R, self.meshes, self.sheet_of, self.L = R, meshes, sheet_of, layout
        self.cache = {}
        self.missing = set()
        self.walk = layout["zone_rows"]
        self.x0, self.z0 = layout["bounds"][0], layout["bounds"][1]

    def local(self, name):
        if name not in self.cache:
            m = self.meshes.get(name)
            self.cache[name] = None if m is None else Local(self.R, m, self.sheet_of)
        return self.cache[name]

    def zone_char(self, x, z):
        j, i = int(math.floor(z)) - self.z0, int(math.floor(x)) - self.x0
        if 0 <= j < len(self.walk) and 0 <= i < len(self.walk[0]):
            return self.walk[j][i]
        return "."

    def walkable(self, x, z):
        return self.zone_char(x, z).isupper()

    def build(self, eye, radius=44.0):
        # 44 blocks: Level228Props' build radius, so the far end of a shot is what the game shows.
        e = np.asarray(eye, float)
        opaque, glow, glass, lights = {}, {}, {}, []

        def add(dst, key, arr):
            dst.setdefault(key, []).append(arr)

        for p in self.L["props"]:
            if (p["x"] - e[0]) ** 2 + (p["z"] - e[2]) ** 2 > radius * radius:
                continue
            state = (p["flags"] >> 7) & 3
            is_light = p["type"] in (P_BULB, P_SCONCE, P_EXIT_SIGN)
            level = 1.0 if not is_light or state == LIGHT_LIT else 0.45 if state == LIGHT_FAILING else 0.0
            for name in mesh_names(p, self.meshes):
                loc = self.local(name)
                if loc is None:
                    self.missing.add(name)
                    continue
                m3, t3 = placement(loc.mesh, p["x"], p["y"] - FEET_Y, p["z"], p["rot"], bool(p["flags"] & 1))
                for (sheet, layer), (pos, uv, n, sh) in loc.parts.items():
                    P = pos @ m3.T + t3
                    N = n @ m3.T
                    g = np.full((len(P), 1), 0.9 * level if layer == G.LAYER_GLOW else 0.0)
                    arr = np.hstack([P, uv, N, sh[:, None], g]).astype(np.float32)
                    add(glass if layer == G.LAYER_GLASS else opaque, sheet, arr)
                if is_light and level > 0:
                    # Light props take their kind from the record (as Level228Props does), so
                    # the Glass zone's and the hallway's bulbs come out cold.
                    kind = (p["flags"] >> 4) & 7
                    for em in loc.mesh.emitters:
                        wp = np.asarray(em.pos) @ m3.T + t3
                        c = np.asarray(LIGHT_COL.get(kind, LIGHT_COL[G.EMIT_BULB])) * level
                        lights.append((wp, c))
            if p["type"] == P_PANEL and ((p["flags"] >> 1) & 3) == PANEL_GLASS:
                self._glass_pane(p, glass)
        for m in self.L["mirrors"]:
            mid = (m["min"] + m["max"]) / 2
            mx, mz = (m["plane"], mid) if m["axis"] == 0 else (mid, m["plane"])
            if (mx - e[0]) ** 2 + (mz - e[2]) ** 2 > radius * radius:
                continue
            self._mirror(m, glass)
        cat = lambda d: {k: np.concatenate(v) for k, v in d.items()}
        return cat(opaque), cat(glass), lights

    def _tile(self, surface, noise):
        return {1: f"mir_tarnish_{noise % 3}", 2: f"mir_crack_{noise % 3}", 3: f"mir_shatter_{noise % 2}",
                4: "mir_concave", 5: "mir_convex", 6: "mir_wave"}.get(surface, f"mir_clean_{noise % 3}")

    def _quad(self, tile, axis, plane, a0, a1, y0, y1, u0, u1, dst):
        m = G.Mesh("pane")
        if axis == 0:
            pts = [(plane, y0, a0), (plane, y0, a1), (plane, y1, a1), (plane, y1, a0)]
            n = (1, 0, 0)
        else:
            pts = [(a0, y0, plane), (a1, y0, plane), (a1, y1, plane), (a0, y1, plane)]
            n = (0, 0, 1)
        m.quad(pts, [(u0, 1), (u1, 1), (u1, 0), (u0, 0)], tile, n)
        loc = Local(self.R, m, self.sheet_of)
        for (sheet, _), (pos, uv, nn, sh) in loc.parts.items():
            arr = np.hstack([pos, uv, nn, sh[:, None], np.zeros((len(pos), 1))]).astype(np.float32)
            dst.setdefault(sheet, []).append(arr)

    def _mirror(self, m, dst):
        maze = ZONES[m["zone"]] in MAZE
        a0 = m["min"] - 0.5 if maze else m["min"]
        a1 = m["max"] + 0.5 if maze else m["max"]
        eu = 0.0 if maze else 0.2 / 3.0
        self._quad(self._tile(m["surface"], m["noise"]), m["axis"], m["plane"], a0, a1, m["y0"] - FEET_Y, m["y1"] - FEET_Y,
                   eu, 1 - eu, dst)

    def _glass_pane(self, p, dst):
        ax, az = p["x"] + 0.5, p["z"] + 0.5
        rot = p["rot"] & 3
        if rot == 0:
            axis, plane, a0, a1 = 1, az, ax - 1.0, ax + 2.0
        elif rot == 1:
            axis, plane, a0, a1 = 0, ax, az - 1.0, az + 2.0
        elif rot == 2:
            axis, plane, a0, a1 = 1, az, ax - 2.0, ax + 1.0
        else:
            axis, plane, a0, a1 = 0, ax, az - 2.0, az + 1.0
        self._quad(f"mir_glass_{p['noise'] % 2}", axis, plane, a0, a1, 0.1, PANE_Y1, 0.0, 1.0, dst)

    def shot(self, eye, target, size=(960, 600), fov=70, ambient=(0.010, 0.009, 0.008), fog=0.07):
        # fog 0.07 (exponential) is about Level228Atmosphere's linear 6 -> 44 at 15-30 blocks.
        opaque, glass, lights = self.build(eye)
        e = np.asarray(eye, float)
        ls = sorted(lights, key=lambda l: np.linalg.norm(l[0] - e))[:self.R.MAX_LIGHTS]
        return self.R.render([opaque], eye, target, size=size, fov=fov, lights=ls, ambient=ambient, fog=fog,
                             translucent=[glass])


# ------------------------------------------------------------------------------------------------
# Shot list: framed from the plan itself, so the shots follow PlanAgent's layout as it changes.

def _front(rot):
    return [(0, -1), (1, 0), (0, 1), (-1, 0)][rot & 3]


def _yaw_dir(yaw):
    a = math.radians(yaw)
    return -math.sin(a), math.cos(a)


def _look_at_prop(sc: RealScene, p, dist=(5.0, 4.0, 3.0, 2.2), height=1.62, aim_y=1.6, side=0.0, pivot=(0.5, 0.5)):
    fx, fz = _front(p["rot"])
    rx, rz = -fz, fx
    cx, cz = p["x"] + 0.5, p["z"] + 0.5
    for d in dist:
        ex, ez = cx + fx * d + rx * side, cz + fz * d + rz * side
        if sc.walkable(ex, ez):
            return (ex, height, ez), (cx, aim_y, cz)
    d = dist[-1]
    return (cx + fx * d + rx * side, height, cz + fz * d + rz * side), (cx, aim_y, cz)


def shots(sc: RealScene):
    L = sc.L
    out = []
    st = L["starts"]

    def from_start(name, zone, turn=0.0, back=0.0, h=1.62):
        x, _, z, yaw = st[zone]
        dx, dz = _yaw_dir(yaw + turn)
        ex, ez = x - dx * back, z - dz * back
        out.append((name, (ex, h, ez), (ex + dx * 10, 1.5, ez + dz * 10)))

    by_type = {}
    for p in L["props"]:
        # Real props only (not their reflected copies in sealed image space) for the close-ups.
        if not p["flags"] & 1:
            by_type.setdefault(p["type"], []).append(p)
    # Foyer: the facade, the ticket booth, the posters, the arrival view.
    from_start("real_foyer_arrival", "FOYER")
    from_start("real_foyer_turn", "FOYER", turn=55)
    from_start("real_foyer_turn2", "FOYER", turn=-55)
    for i, p in enumerate(by_type.get(P_FACADE, [])[:1]):
        e, t = _look_at_prop(sc, p, dist=(9.0, 8.0, 7.0, 6.0, 5.0), aim_y=2.2)
        out.append(("real_foyer_facade", e, t))
    for p in by_type.get(P_TICKET_BOOTH, [])[:1]:
        e, t = _look_at_prop(sc, p, dist=(4.5, 3.5, 2.5), aim_y=1.3, side=0.8)
        out.append(("real_foyer_booth", e, t))
    foyer_posters = [p for p in by_type.get(P_POSTER, []) if sc.zone_char(p["x"], p["z"]).upper() == "A"]
    for p in foyer_posters[:1]:
        e, t = _look_at_prop(sc, p, dist=(2.6, 2.0, 1.6), aim_y=1.5)
        out.append(("real_foyer_poster", e, t))
    # Each maze zone: from its start, turned, and further in.
    for zone in ("CRYSTAL", "ALHAMBRA", "FUNHOUSE", "GLASS"):
        z = zone.lower()
        from_start(f"real_{z}_0", zone)
        from_start(f"real_{z}_1", zone, turn=70)
        from_start(f"real_{z}_2", zone, turn=-35, back=-4.0)
        from_start(f"real_{z}_up", zone, turn=20, h=1.5)
    # The chamber: the three mirrors from the doorway and from the middle.
    cx, cz = L["chamber"]
    cms = by_type.get(P_CHAMBER_MIRROR, [])
    if cms:
        mx = sum(p["x"] for p in cms) / len(cms) + 0.5
        mz = sum(p["z"] for p in cms) / len(cms) + 0.5
        x, _, z, yaw = st["CHAMBER"]
        out.append(("real_chamber_0", (x, 1.62, z), (mx, 1.6, mz)))
        out.append(("real_chamber_1", (cx + 0.5, 1.62, cz + 0.5), (mx, 1.5, mz)))
    # The chamber's candle chandelier, seen from a couple of blocks off, looking up at it.
    for p in L["props"]:
        if p["type"] == P_BULB and (p["flags"] >> 4) & 7 == G.EMIT_CANDLE and not p["flags"] & 1:
            px, pz = p["x"] + 0.5, p["z"] + 1.0
            ex = px + 2.2 if sc.walkable(px + 2.2, pz) else px - 2.2
            out.append(("real_chamber_chandelier", (ex, 1.62, pz + 0.4), (px, p["y"] - FEET_Y + 0.6, pz)))
            break
    # The exit hallway: from the entry towards the door, from halfway, and at the door.
    x, _, z, yaw = st["EXIT_HALL"]
    dx, dz = L["door"]
    out.append(("real_exithall_0", (x, 1.62, z), (dx + 0.5, 1.5, dz)))
    mid = (z + dz) / 2
    out.append(("real_exithall_1", (x, 1.62, mid), (dx + 0.5, 1.4, dz)))
    out.append(("real_exithall_back", (x, 1.62, mid), (x, 1.4, z - 3)))
    out.append(("real_exithall_door", (dx + 0.5, 1.62, dz - 4.0), (dx + 0.5, 1.3, dz)))
    # Backstage.
    from_start("real_backstage_0", "BACKSTAGE")
    from_start("real_backstage_1", "BACKSTAGE", turn=180)
    return out


def render_all(R, meshes, sheet_of, layout_path, out: Path, only=None):
    sc = RealScene(R, meshes, sheet_of, load(layout_path))
    out.mkdir(parents=True, exist_ok=True)
    written = []
    for name, eye, tgt in shots(sc):
        if only and not any(o in name for o in only):
            continue
        img = sc.shot(eye, tgt)
        path = out / f"{name}.png"
        img.save(path)
        written.append(path)
        print("  ", path.name, "eye", tuple(round(c, 1) for c in eye))
    if sc.missing:
        print("MISSING MESHES:", sorted(sc.missing))
    chunk_report(meshes, sc.L)
    return written


# Bytes per vertex of the game's static chunk buffers (vanilla NEW_ENTITY: pos 12, colour 4, uv 8,
# overlay 4, light 4, normal 3 + pad 1); Iris's extended entity format adds about a third.
VERTEX_BYTES = 36


def chunk_report(meshes, layout):
    """Static quads per 16 x 16 chunk of the real layout (what Level228Props builds into one
    chunk's buffers), so a heavy mesh times hundreds of placements shows up before the game."""
    per, by_mesh = {}, {}
    for p in layout["props"]:
        for name in mesh_names(p, meshes):
            m = meshes.get(name)
            if m is None:
                continue
            q = sum(1 for qq in m.quads if qq.layer == G.LAYER_CUT)
            key = (p["x"] >> 4, p["z"] >> 4)
            per[key] = per.get(key, 0) + q
            by_mesh[name] = by_mesh.get(name, 0) + q
    if not per:
        return
    v = sorted(per.values())
    mb = lambda q: q * 4 * VERTEX_BYTES / 1e6
    print(f"chunk buffers: {len(v)} chunks, max {v[-1]} quads ({mb(v[-1]):.1f} MB), median {v[len(v) // 2]} "
          f"({mb(v[len(v) // 2]):.1f} MB), whole maze {mb(sum(v)):.0f} MB")
    top = sorted(by_mesh.items(), key=lambda kv: -kv[1])[:8]
    print("  heaviest in total:", ", ".join(f"{k} {mb(q):.0f} MB" for k, q in top))
