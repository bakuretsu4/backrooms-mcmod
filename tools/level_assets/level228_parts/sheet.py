"""Texture sheets of Level 228's props: named regions packed into sheets 2048 wide (and only as tall
as their regions need, a power of two), and the binary mesh file the game reads
(client/level228/Level228PropMeshes).

Regions are painted at a known density (pixels per block) so a texture keeps its real-world size
on every prop that samples it. Each region is padded with a copy of its own edge pixels, so the
mipmaps the game builds (client/level228/Level228PropTexture) never bleed a neighbour in.

Size: these sheets ship in the mod jar, so colour is stored with 6 bits per channel by default
(64 levels: a third smaller PNGs, and the generated textures are noisy enough that the steps never
show); floors and ceilings, always seen mipmapped at a slant, may ask for 5. Alpha keeps 8 bits.
Every uv in props.bin is a fraction of its own sheet's size, so the game needs no sizes.

Mesh file (little-endian), assets/backrooms/models/level228/props.bin:
  "L228" u8 version(=1) u16 sheets [u8 len, name]* u16 meshes
  per mesh: u8 len, name; f32 pivotX, pivotZ; f32 min xyz, max xyz; u8 emitters
            [f32 x y z, u8 kind, f32 size]*; u8 parts
            per part: u8 sheet, u8 layer, u32 quads, then 4 vertices per quad:
                      f32 x y z, u16 u v (0..65535 of the sheet), i8 nx ny nz, u8 shade, u8 emitter
  then u16 named regions the game draws by itself (the bulbs' glow halo):
            [u8 len, name; u8 sheet; f32 u0 v0 u1 v1]*
"""
from __future__ import annotations

import struct
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

import numpy as np
from PIL import Image

SHEET = 2048
PAD = 6
DEFAULT_BITS = 6


def quantize(img: np.ndarray, bits: int) -> np.ndarray:
    """Colour (0..1) snapped to 2^bits levels per channel, exactly as it will be stored."""
    if bits >= 8:
        return img
    n = (1 << bits) - 1
    out = img.copy()
    out[..., :3] = np.round(np.round(img[..., :3] * n) / n * 255.0) / 255.0
    return out


@dataclass
class Region:
    name: str
    sheet: str
    w: int
    h: int
    paint: Callable[[int, int], np.ndarray] | None
    x: int = -1
    y: int = -1
    img: np.ndarray | None = None
    bits: int = DEFAULT_BITS


class Sheets:
    def __init__(self):
        self.regions: dict[str, Region] = {}
        self.order: list[str] = []
        self.height: dict[str, int] = {}

    def region(self, name, sheet, w, h, paint, bits=DEFAULT_BITS):
        if name in self.regions:
            raise ValueError(f"region {name} twice")
        self.regions[name] = Region(name, sheet, int(w), int(h), paint, bits=bits)
        self.order.append(name)
        return name

    def sheets(self):
        out = []
        for n in self.order:
            s = self.regions[n].sheet
            if s not in out:
                out.append(s)
        return out

    def pack(self):
        """Skyline bottom-left packing per sheet, tallest first (shelves wasted a fifth of a sheet)."""
        for s in self.sheets():
            regs = sorted((r for r in self.regions.values() if r.sheet == s), key=lambda r: (-r.h, -r.w))
            sky = [(0, 0, SHEET)]  # (x, y, width) segments, left to right
            for r in regs:
                W, H = r.w + 2 * PAD, r.h + 2 * PAD
                best = None
                for i in range(len(sky)):
                    x = sky[i][0]
                    if x + W > SHEET:
                        break
                    # The rect rests on the highest segment it spans.
                    y, span, j = 0, 0, i
                    while span < W:
                        y = max(y, sky[j][1])
                        span += sky[j][2]
                        j += 1
                    if y + H <= SHEET and (best is None or (y, x) < (best[0], best[1])):
                        best = (y, x)
                if best is None:
                    used = sum((q.w + 2 * PAD) * (q.h + 2 * PAD) for q in regs)
                    raise RuntimeError(f"sheet {s} full at {r.name} ({used / SHEET / SHEET:.0%} area)")
                y, x = best
                r.x, r.y = x + PAD, y + PAD
                # Raise the skyline under the new rect.
                new = []
                for (sx, sy, sw) in sky:
                    a, b = sx, sx + sw
                    if b <= x or a >= x + W:
                        new.append((sx, sy, sw))
                        continue
                    if a < x:
                        new.append((a, sy, x - a))
                    if b > x + W:
                        new.append((x + W, sy, b - (x + W)))
                new.append((x, y + H, W))
                new.sort()
                merged = []
                for seg in new:
                    if merged and merged[-1][1] == seg[1] and merged[-1][0] + merged[-1][2] == seg[0]:
                        merged[-1] = (merged[-1][0], seg[1], merged[-1][2] + seg[2])
                    else:
                        merged.append(seg)
                sky = merged
            # Only as tall as needed (a power of two keeps the mip chain exact): the rest of a
            # square sheet would ship and sit in video memory empty.
            top = max(r.y + r.h + PAD for r in regs)
            H = 256
            while H < top:
                H *= 2
            self.height[s] = H

    def paint(self, only=None):
        for n in self.order:
            r = self.regions[n]
            if only and r.sheet not in only:
                continue
            img = r.paint(r.h, r.w)
            if img.shape[2] == 3:
                img = np.concatenate([img, np.ones(img.shape[:2] + (1,))], axis=2)
            if img.shape[:2] != (r.h, r.w):
                raise ValueError(f"region {n}: painted {img.shape[:2]}, wanted {(r.h, r.w)}")
            r.img = quantize(np.clip(img, 0, 1), r.bits)

    def compose(self, sheet) -> np.ndarray:
        out = np.zeros((self.height.get(sheet, SHEET), SHEET, 4))
        for r in self.regions.values():
            if r.sheet != sheet or r.img is None:
                continue
            padded = np.pad(r.img, ((PAD, PAD), (PAD, PAD), (0, 0)), mode="edge")
            out[r.y - PAD:r.y + r.h + PAD, r.x - PAD:r.x + r.w + PAD] = padded
        return out

    def save(self, sheet, path: Path):
        img = self.compose(sheet)
        path.parent.mkdir(parents=True, exist_ok=True)
        arr = np.clip(np.round(img * 255.0), 0, 255).astype(np.uint8)
        Image.fromarray(arr, "RGBA").save(path, optimize=True)
        return img

    def uv(self, name, u, v):
        """Region-local (u, v) -> sheet (u, v), kept half a texel inside the region."""
        r = self.regions[name]
        u = np.clip(u, 0.5 / r.w, 1 - 0.5 / r.w)
        v = np.clip(v, 0.5 / r.h, 1 - 0.5 / r.h)
        return (r.x + u * r.w) / SHEET, (r.y + v * r.h) / self.height[r.sheet]


def write_meshes(path: Path, meshes, sheets: Sheets, named=("de_halo",)):
    names = sheets.sheets()
    sheet_index = {n: i for i, n in enumerate(names)}
    out = bytearray()
    out += b"L228"
    out += struct.pack("<BH", 1, len(names))
    for n in names:
        b = n.encode()
        out += struct.pack("<B", len(b)) + b
    out += struct.pack("<H", len(meshes))
    for m in meshes:
        b = m.name.encode()
        out += struct.pack("<B", len(b)) + b
        lo, hi = m.bounds()
        out += struct.pack("<2f", *m.pivot)
        out += struct.pack("<6f", *lo, *hi)
        out += struct.pack("<B", len(m.emitters))
        for e in m.emitters:
            out += struct.pack("<3fBf", *e.pos, e.kind, e.size)
        parts = {}
        for q in m.quads:
            key = (sheet_index[sheets.regions[q.region].sheet], q.layer)
            parts.setdefault(key, []).append(q)
        out += struct.pack("<B", len(parts))
        for (si, layer), qs in sorted(parts.items()):
            out += struct.pack("<BBI", si, layer, len(qs))
            for q in qs:
                for k in range(4):
                    su, sv = sheets.uv(q.region, q.uv[k, 0], q.uv[k, 1])
                    n = q.n[k]
                    out += struct.pack("<3f2H3bBB", *q.p[k],
                                       int(round(np.clip(su, 0, 1) * 65535)), int(round(np.clip(sv, 0, 1) * 65535)),
                                       *(int(round(np.clip(c, -1, 1) * 127)) for c in n),
                                       int(round(np.clip(q.shade[k], 0, 1) * 255)), q.emitter & 0xFF)
    out += struct.pack("<H", len(named))
    for n in named:
        r = sheets.regions[n]
        b = n.encode()
        out += struct.pack("<B", len(b)) + b
        H = sheets.height[r.sheet]
        out += struct.pack("<B4f", sheet_index[r.sheet], r.x / SHEET, r.y / H, (r.x + r.w) / SHEET, (r.y + r.h) / H)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(bytes(out))
    return len(out)
