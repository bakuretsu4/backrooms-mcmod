"""Getting into the Backrooms: the camcorder item you film the real world with.

Run only these with:  python tools/gen_assets.py tex_entry_
"""
from __future__ import annotations

import numpy as np

import gen_assets as G


def tex_entry_camcorder() -> None:
    """16x16 item: a boxy dark-grey 90s camcorder seen from the side, lens to the right,
    viewfinder on top, a red REC light."""
    img = np.zeros((16, 16, 4))
    body = np.array([58, 58, 62, 255.0])
    img[6:13, 2:12] = body                                      # the box
    img[6, 2:12, :3] += 22                                      # lit top edge
    img[12, 2:12, :3] -= 18                                     # shadowed bottom
    img[7:12, 12:15] = (40, 40, 44, 255)                        # lens barrel
    img[8:11, 14] = (110, 130, 150, 255)                        # glass
    img[9, 14, :3] = (190, 210, 225)                            # glint
    img[4:6, 3:8] = (46, 46, 50, 255)                           # viewfinder
    img[4, 3:8, :3] += 20
    img[7, 10] = (220, 40, 40, 255)                             # REC light
    img[9:12, 3:5, :3] = (36, 36, 40)                           # hand strap
    img[13, 4:10] = (30, 30, 34, 255)                           # strap below
    G.save_png(np.clip(img, 0, 255), G.TEX / "item" / "camcorder.png")


TEXTURES = [tex_entry_camcorder]
SOUNDS = []
