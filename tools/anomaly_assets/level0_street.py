"""Level 0's street hall (world/layout/Level0Street): the few new blocks and its fan sound.

  l0_road_yellow    asphalt with the double yellow centre line (64 px)
  l0_siding_red     dark red-brown lap siding, the film's right-hand house (64 px)
  l0_stop_sign      a stop sign on its post (32 px face; the post reuses sign_post)
  l0_ceiling_fan    the fan's hub block (drawn by client/CeilingFanRenderer; only an item model here)
  level0/ceiling_fan.ogg   a big slow fan high overhead: a soft whoosh twice a second, a low hum

Atlas budget: the block atlas is nearly full, so 64 px and 32 px (about 9 200 texels).
Run with:  python tools/gen_assets.py l0street
Fixed seeds 3800-3809, so re-running gives identical files.
"""
from __future__ import annotations

import json

import numpy as np

import gen_assets as G
from anomaly_assets import level227_flora as F

N = 64
M = 32


def _json(rel: str, obj) -> None:
    path = G.ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", newline="\n")


def tex_l0street_road() -> None:
    """Pale grey daytime asphalt (the film's street), and the same with two yellow lines down the
    middle, worn and a little patchy (64 px each). The yellow line's model puts it on top only."""
    rng = G.rng_for(3800)
    X, Y = F._grid(N)
    base = G.fill((150, 148, 142), (N, N))
    base = G.add_lum(base, F._noise(rng, N, beta=1.4, kmin=6) * 7 + rng.normal(0, 4.5, (N, N)))
    base = G.add_lum(base, -G.smoothstep(1.6, 2.4, F._noise(rng, N, beta=1.2, kmin=10)) * 16)
    F._save(base, "l0_road")
    line = np.zeros((N, N))
    for cx in (N / 2 - 6, N / 2 + 6):
        line = np.maximum(line, G.smoothstep(4.0, 3.0, np.abs(X - cx)))
    wear = G.smoothstep(-0.8, 0.6, F._noise(rng, N, beta=1.6, kmin=4, aniso=(1.0, 0.3)))
    img = G.over(base, (228, 196, 76), line * (0.6 + 0.4 * wear))
    F._save(img, "l0_road_yellow")


def tex_l0street_siding() -> None:
    """Dark red-brown lap siding (64 px): four boards, each with a shadow under its lip."""
    rng = G.rng_for(3801)
    X, Y = F._grid(N)
    img = G.fill((118, 52, 40), (N, N))
    board = (Y % (N / 4)) / (N / 4)
    img = G.add_lum(img, (board - 0.5) * 18 - G.smoothstep(0.9, 1.0, board) * 30)
    img = G.add_lum(img, F._noise(rng, N, beta=1.2, aniso=(1.0, 0.08), kmin=3) * 5 + rng.normal(0, 2, (N, N)))
    F._save(img, "l0_siding_red")


def tex_l0street_stop() -> None:
    """A stop sign's face (32 px): a red octagon with a white rim and white letters, on transparent."""
    S = M * F.SS
    X, Y = G.grid((S, S))
    cx = cy = S / 2
    # Octagon: the max of |x|, |y| and the diagonal distance.
    dx, dy = np.abs(X - cx), np.abs(Y - cy)
    octa = np.maximum(np.maximum(dx, dy), (dx + dy) / np.sqrt(2) * 1.08)
    r = S * 0.47
    a = (octa < r).astype(float)
    rgb = np.zeros((S, S, 3)) + F._col((246, 244, 240))
    rgb = np.where((octa < r * 0.88)[..., None], F._col((190, 26, 30)), rgb)
    # "STOP": four chunky letters drawn as boxes on a 4x5 grid each.
    glyphs = {
        "S": ["1111", "1000", "1111", "0001", "1111"],
        "T": ["1111", "0110", "0110", "0110", "0110"],
        "O": ["1111", "1001", "1001", "1001", "1111"],
        "P": ["1111", "1001", "1111", "1000", "1000"],
    }
    cell = S * 0.045
    x0 = cx - (4 * 4 + 3) * cell / 2
    y0 = cy - 5 * cell / 2
    for k, ch in enumerate("STOP"):
        for row, bits in enumerate(glyphs[ch]):
            for col, bit in enumerate(bits):
                if bit == "1":
                    gx = x0 + (k * 5 + col) * cell
                    gy = y0 + row * cell
                    m = (X >= gx) & (X < gx + cell) & (Y >= gy) & (Y < gy + cell)
                    rgb = np.where(m[..., None], F._col((246, 244, 240)), rgb)
    G.save_png(F._cutout(F._down(rgb, F.SS), F._down(a, F.SS)), F._blk("l0_stop_sign"))


def tex_l0street_models() -> None:
    """Models and blockstates of the street's blocks."""
    F._model("l0_road", {"parent": "minecraft:block/cube_all", "textures": {"all": F._ref("l0_road")}})
    F._state("l0_road", {"": F._v("l0_road")})
    F._item_block("l0_road", "l0_road")
    F._model("l0_road_yellow", {"parent": "minecraft:block/cube", "textures": {
        "particle": F._ref("l0_road"), "up": F._ref("l0_road_yellow"), "down": F._ref("l0_road"),
        "north": F._ref("l0_road"), "south": F._ref("l0_road"), "east": F._ref("l0_road"), "west": F._ref("l0_road")}})
    F._state("l0_road_yellow", {"axis=z": F._v("l0_road_yellow"), "axis=x": F._v("l0_road_yellow", 0, 90)})
    F._item_block("l0_road_yellow", "l0_road_yellow")

    F._model("l0_siding_red", {"parent": "minecraft:block/cube_all", "textures": {"all": F._ref("l0_siding_red")}})
    F._state("l0_siding_red", {"": F._v("l0_siding_red")})
    F._item_block("l0_siding_red", "l0_siding_red")

    # The stop sign: a thin post (sign_post texture, one plain patch) up to a face 12 px across,
    # standing above the block (the sign sits at about 2 blocks, like the film's).
    post = {"texture": "#post", "uv": [7, 0, 9, 16]}
    face = {"from": [2, 18, 7.4], "to": [14, 30, 7.6], "faces": {
        "north": {"uv": [0, 0, 16, 16], "texture": "#face"},
        "south": {"uv": [0, 0, 16, 16], "texture": "#back"}}}
    F._model("l0_stop_sign", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                              "textures": {"post": F._ref("sign_post"), "face": F._ref("l0_stop_sign"),
                                           "back": F._ref("sign_post"), "particle": F._ref("l0_stop_sign")},
                              "elements": [{"from": [7.5, 0, 7.6], "to": [8.5, 30, 8.6],
                                            "faces": {f: dict(post) for f in ("north", "south", "east", "west", "up", "down")}},
                                           face]})
    F._state("l0_stop_sign", {f"facing={f}": F._v("l0_stop_sign", 0, y) for f, y in F.FACINGS})
    F._item_flat("l0_stop_sign", F._ref("l0_stop_sign"))

    # The fan: drawn by its renderer; the block itself has no model (an invisible hub). Its item
    # shows the ceiling tile so it isn't a missing texture in the creative tab.
    F._model("l0_ceiling_fan", {"textures": {"particle": F._ref("ceiling_tile")}, "elements": []})
    F._state("l0_ceiling_fan", {"": F._v("l0_ceiling_fan")})
    _json("models/item/l0_ceiling_fan.json", {"parent": "minecraft:item/generated",
                                              "textures": {"layer0": F._ref("ceiling_tile")}})


def snd_l0street_fan() -> None:
    """A big industrial fan turning slowly high overhead (8 s seamless loop): eight blades about
    once every 3 seconds, so a blade passes every ~0.37 s; each pass is a soft swell of air, a deep
    rhythmic whoosh, over a low motor hum (whole cycles per loop)."""
    rng = G.rng_for(3809)
    T = 8.0
    n = int(T * G.SR)
    t = np.arange(n) / G.SR
    air = G.norm(G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 80, 900, 2) / np.sqrt(np.maximum(f, 40))))
    passes = round(T / 0.375)
    swell = 0.55 + 0.45 * np.sin(2 * np.pi * passes * t / T) ** 2
    beat = 0.85 + 0.15 * np.sin(2 * np.pi * 3 * t / T + 1.0)     # the blades never quite even
    hum = sum(a * np.sin(2 * np.pi * round(f * T) / T * t + rng.uniform(0, 6.3)) for f, a in ((57, 1.0), (114, 0.4), (171, 0.15)))
    x = air * swell * beat + 0.12 * G.norm(hum)
    G.save_ogg(x, G.SND / "level0" / "ceiling_fan.ogg", peak=0.5, loop=True)


TEXTURES = [tex_l0street_road, tex_l0street_siding, tex_l0street_stop, tex_l0street_models]
SOUNDS = [snd_l0street_fan]
