"""The plain block textures of Level 228 (block atlas: it is full, so these keep their old names and
32 px sizes). In v2 the props draw almost everything; the blocks only show where no prop covers
them, in particles and as item icons:
  l228_floor, l228_ceiling   under every floor and ceiling decal: dark worn boards / dark boards
  l228_panel                 the solid walls of the foyer, hall, chamber and backstage: dark
                             painted plywood (the WALL props cover the faces you walk past)
  l228_mirror_frame          dark oiled walnut (v1 frames; kept for old worlds)
  l228_mirror                a pane's particles and icon: neutral silver glass, no tint
  l228_light_blue|green|red  v1's coloured light panels (kept registered for old worlds, no longer
                             placed): now all three a dim warm-white frosted panel, no colour
  l228_exit_mirror           v1's exit: black glass with a slow neutral shimmer (16 frames)
"""
from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image

from . import mat as M

BLOCK = Path(__file__).resolve().parents[3] / "src" / "main" / "resources" / "assets" / "backrooms" / "textures" / "block"
N = 32


def _save(rgb, name, a=None):
    img = M.rgba(rgb, a)
    Image.fromarray(np.clip(np.round(img * 255), 0, 255).astype(np.uint8), "RGBA").save(BLOCK / f"{name}.png")


def _boards(seed, palette, horizontal=True):
    r = M.rng(seed)
    big = M.wood(r, N * 4, N * 4, N * 4, palette=palette, grain="h" if horizontal else "v", varnish=0.1)
    Y, X = np.mgrid[0:N * 4, 0:N * 4]
    gaps = ((Y if horizontal else X) % (N)) < 2
    big = np.where(gaps[..., None], big * 0.4, big)
    return big.reshape(N, 4, N, 4, 3).mean(axis=(1, 3))


def save():
    _save(_boards(22980, (M.col("#2a1c12"), M.col("#3a2818"), M.col("#160e08"))), "l228_floor")
    _save(_boards(22981, (M.col("#1a1512"), M.col("#241d18"), M.col("#0d0a08"))), "l228_ceiling")
    r = M.rng(22982)
    ply = M.wood(r, N * 4, N * 4, N * 4, palette=(M.col("#26231f"), M.col("#302c27"), M.col("#1a1815")), grain="v",
                 rings=2.0, figure=3.0, varnish=0.0)
    _save(ply.reshape(N, 4, N, 4, 3).mean(axis=(1, 3)), "l228_panel")
    _save(_boards(22983, M.WALNUT, horizontal=False), "l228_mirror_frame")
    r = M.rng(22984)
    X, Y = M.grid(N, N)
    glass = M.lerp(M.col("#9e9d99"), M.col("#c9c8c3"), np.clip(1.0 - Y + 0.15 * M.fnoise(r, N, N, beta=2.4), 0, 1))
    edge = (np.minimum(np.minimum(X, 1 - X), np.minimum(Y, 1 - Y)) < 1.5 / N)
    glass = np.where(edge[..., None], M.col("#3a3936"), glass)
    _save(glass, "l228_mirror", np.where(edge, 1.0, 0.55))
    panel = M.lerp(M.col("#6d665a"), M.col("#8a8273"), M.fnoise(M.rng(22985), N, N, beta=2.0) * 0.5 + 0.5)
    for c in ("blue", "green", "red"):
        _save(panel, f"l228_light_{c}")
    frames = []
    r = M.rng(22986)
    base = M.fnoise(r, N * 16, N, beta=2.6)
    for k in range(16):
        n = np.roll(base, k * N // 2, axis=0)[:N] * 0.5 + 0.5
        frames.append(M.lerp(M.col("#050506"), M.col("#1c1c1e"), n ** 2))
    _save(np.concatenate(frames, axis=0), "l228_exit_mirror")
