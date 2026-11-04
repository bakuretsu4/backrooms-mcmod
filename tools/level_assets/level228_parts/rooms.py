"""Assembled preview rooms (not shipped): a patch of each zone's grid built from its props exactly as
the game places them (Level228Props: flip about the pivot, quarter turns about the anchor block's
centre, then the anchor), with translucent mirror surfaces on the mirror panels and warm bulbs,
seen from eye height. Because the rooms are laid out mirror-symmetrically, what shows through a
mirror pane is the room's own twin, as in the game."""
from __future__ import annotations

import math

import numpy as np

from . import geo as G
from .dims import PANE_Y0, PANE_Y1

ROT = [np.eye(3),
       np.array([[0, 0, -1], [0, 1, 0], [1, 0, 0]], float),
       np.array([[-1, 0, 0], [0, 1, 0], [0, 0, -1]], float),
       np.array([[0, 0, 1], [0, 1, 0], [-1, 0, 0]], float)]


def placement(mesh: G.Mesh, x, y, z, rot=0, flip=False):
    """(3x3 matrix, translation) taking prop-local points to world, as the game does."""
    F = np.diag([-1.0, 1.0, 1.0]) if flip else np.eye(3)
    tf = np.array([2 * mesh.pivot[0], 0, 0]) if flip else np.zeros(3)
    c = np.array([0.5, 0.0, 0.5])
    R = ROT[rot % 4]
    m = R @ F
    t = np.array([x, y, z], float) + c + R @ (tf - c)
    return m, t


class Scene:
    def __init__(self, R, meshes, sheet_of):
        self.R = R
        self.meshes = meshes
        self.sheet_of = sheet_of
        self.batches = []
        self.trans = []
        self.lights = []

    def put(self, name, x, y, z, rot=0, flip=False, glow=0.9):
        mesh = self.meshes.get(name)
        if mesh is None:
            return
        self.batches.append(self.R.arrays(mesh, placement(mesh, x, y, z, rot, flip), self.sheet_of, glow_default=glow))

    def mirror(self, tile, x, z, rot):
        """A mirror surface over the panel anchored at (x, z) with rotation rot (0: along +x)."""
        m = G.Mesh("mir")
        y0, y1 = PANE_Y0, PANE_Y1
        m.quad([(-0.5, y0, 0.5), (2.5, y0, 0.5), (2.5, y1, 0.5), (-0.5, y1, 0.5)], [(0, 1), (1, 1), (1, 0), (0, 0)],
               tile, (0, 0, 1))
        self.trans.append(self.R.arrays(m, placement(m, x, 0, z, rot), self.sheet_of))

    def light(self, p, c):
        self.lights.append((np.asarray(p, float), np.asarray(c, float)))

    def shot(self, eye, target, size=(960, 600), fov=70, ambient=(0.012, 0.011, 0.010), fog=0.06):
        # The nearest bulbs only (the preview shader takes 8).
        e = np.asarray(eye, float)
        ls = sorted(self.lights, key=lambda l: np.linalg.norm(l[0] - e))[:8]
        return self.R.render(self.batches, eye, target, size=size, fov=fov, lights=ls, ambient=ambient, fog=fog,
                             translucent=self.trans)


def grid_room(scene: Scene, zone, n=6, kinds=None, bulbs=True, mirror_tile="mir_clean_0", light=(1.6, 1.1, 0.68)):
    """An n x n cell patch of the zone's grid. kinds(i, j, axis) -> 'mirror'|'opening'|'solid'|
    'glass'|'empty' for the segment from node (i, j) along x (axis 0) or z (axis 1)."""
    P = 3
    tiles = ["mir_clean_0", "mir_clean_1", "mir_tarnish_0", "mir_crack_0", "mir_clean_2", "mir_tarnish_1",
             "mir_shatter_0", "mir_crack_1"]
    for i in range(n + 1):
        for j in range(n + 1):
            scene.put(f"column_{zone}", i * P, 0, j * P)
            for axis in (0, 1):
                if (axis == 0 and i == n) or (axis == 1 and j == n):
                    continue
                k = kinds(i, j, axis)
                ax, az = (i * P + 1, j * P) if axis == 0 else (i * P, j * P + 1)
                rot = 0 if axis == 0 else 1
                if k == "opening":
                    scene.put(f"opening_{zone}", ax, 0, az, rot)
                    continue
                scene.put(f"panel_{zone}", ax, 0, az, rot)
                if k == "solid":
                    scene.put(f"solid_{zone}", ax, 0, az, rot)
                elif k == "empty":
                    scene.put("panel_empty_shards", ax, 0, az, rot)
                elif k == "glass":
                    scene.mirror("mir_glass_0", ax, az, rot)
                else:
                    t = tiles[(i * 7 + j * 3 + axis * 5) % len(tiles)] if mirror_tile is None else mirror_tile
                    scene.mirror(t, ax, az, rot)
    for i in range(n):
        for j in range(n):
            flip = (i + j) % 2 == 1
            scene.put(f"floor_{zone}", i * P, 0, j * P, 0, flip)
            scene.put(f"ceiling_{zone}", i * P, 4, j * P, 0, flip)
            if bulbs:
                bx, bz = i * P + 1, j * P + 1
                dead = (i * 5 + j * 3) % 4 == 0
                scene.put("bulb", bx, 3, bz, glow=0.0 if dead else 0.9)
                if not dead:
                    scene.light((bx + 0.5, 3.45, bz + 0.5), light)


def render_all(R, meshes, sheet_of, out):
    def maze(i, j, axis):
        # A walkable pattern: openings on most interior segments, mirrors on a regular subset
        # (mirror-symmetric about every third line), the outside all mirror.
        n = 6
        if (axis == 0 and (j == 0 or j == n)) or (axis == 1 and (i == 0 or i == n)):
            return "mirror"
        h = (i * 3 + j * 5 + axis * 7) % 5
        return "mirror" if h in (0, 2) else "solid" if h == 4 and (i + j) % 3 == 0 else "opening"

    def maze_glass(i, j, axis):
        k = maze(i, j, axis)
        return "glass" if k == "opening" and (i + j + axis) % 2 == 0 else k

    for zone, kinds, tile in (("crystal", maze, None), ("alhambra", maze, None), ("funhouse", maze, "mir_concave"),
                              ("glass", maze_glass, "mir_tarnish_0")):
        if f"column_{zone}" not in meshes:
            continue
        sc = Scene(R, meshes, sheet_of)
        grid_room(sc, zone, kinds=kinds, mirror_tile=tile, light=(1.0, 0.75, 0.5) if zone == "glass" else (1.6, 1.1, 0.68))
        for k, (eye, tgt) in enumerate((((7.5, 1.62, 7.6), (16.0, 1.6, 9.0)), ((4.6, 1.62, 4.4), (4.6, 1.9, 14.0)),
                                        ((8.0, 1.62, 8.2), (9.2, 1.2, 8.4)))):
            sc.shot(eye, tgt).save(out / f"room_{zone}_{k}.png")
