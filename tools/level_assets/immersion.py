"""Immersion: the steam and dust particles' textures (four soft, ragged steam wisps and a fine
dust speck). The breathing is made from recordings, in recorded.py.

Run only these with:  python tools/gen_assets.py immersion_
"""
from __future__ import annotations

import numpy as np

import gen_assets as G


def tex_immersion_steam() -> None:
    """Four soft, ragged wisps: white with a feathered, noisy edge and thin streaks inside."""
    size = 64
    for k in range(4):
        rng = G.rng_for(7610 + k)
        X, Y = G.grid((size, size))
        cx, cy = size / 2 + rng.uniform(-4, 4), size / 2 + rng.uniform(-4, 4)
        r = np.hypot((X - cx) / rng.uniform(0.8, 1.2), (Y - cy) / rng.uniform(0.8, 1.2)) / (size / 2)
        shape = G.smoothstep(1.0, 0.15, r + 0.35 * G.fnoise(rng, (size, size), beta=2.2))
        detail = 0.65 + 0.35 * G.smoothstep(-1.0, 1.5, G.fnoise(rng, (size, size), beta=1.6, aniso=(1, 2.5)))
        alpha = np.clip(shape * detail, 0, 1) ** 1.3 * 255
        img = np.dstack([np.full((size, size), 250.0), np.full((size, size), 250.0), np.full((size, size), 252.0), alpha])
        G.save_png(img, G.TEX / "particle" / f"steam_{k}.png")


def tex_immersion_dust() -> None:
    size = 8
    X, Y = G.grid((size, size))
    r = np.hypot(X - size / 2, Y - size / 2) / (size / 2)
    alpha = G.smoothstep(1.0, 0.2, r) * 255
    img = np.dstack([np.full((size, size), 255.0)] * 3 + [alpha])
    G.save_png(img, G.TEX / "particle" / "dust_mote.png")


TEXTURES = [tex_immersion_steam, tex_immersion_dust]
SOUNDS: list = []
