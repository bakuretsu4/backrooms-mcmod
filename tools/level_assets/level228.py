"""Level 228 ("Maze of Mirrors") blocks: the plain textures, and the block models, blockstates and
item models of every l228_ block (world/layout/Level228Plan builds the maze out of them).

Textures (block atlas: few and small, it must stay 4096 x 4096): 32 px each.
  block/l228_mirror_frame   the dark metal of the posts between panes and the lintels over them
  block/l228_panel          the dark matte panels: the walls that are not glass
  block/l228_floor          polished black floor with a faint sheen
  block/l228_ceiling        the low dark ceiling
The glass, the light panels and the black exit mirror are the level's look and have their own
generator (level_assets/level228_atmos.py); the models here only point at them.

Shapes (must match block/Level228MirrorBlock): a mirror pane is a 2 px sheet through the middle
of its block; AXIS is the pane's normal (axis=x: the sheet in the plane x = const).

Run only these with:  python tools/gen_assets.py tex_l228_blocks tex_l228_models
Every generator has its own fixed seed (22850-22859), so re-running gives identical files.
"""
from __future__ import annotations

import numpy as np

import gen_assets as G
from anomaly_assets import level227_flora as F

N = 32
FULL_BRIGHT = {"block_light": 15, "sky_light": 15}
COLOURS = ("blue", "green", "red")


def _noise(rng, beta: float, aniso=(1.0, 1.0), kmin: float = 1.0) -> np.ndarray:
    return G.fnoise(rng, (N, N), beta=beta, aniso=aniso, kmin=kmin)


def tex_l228_blocks() -> None:
    """The four plain surfaces: dark greys, told apart by their grain more than their colour (the
    coloured lights are what you see). Not near black: the first version (albedo 14-34 / 255) left
    the lit maze black on camera in both renderers, since the light has nothing to bounce off. At
    these values a corridor under its panels reads around 30-45 / 255 in its light's colour."""
    rng = G.rng_for(22850)
    # Frame: brushed dark metal, fine horizontal brushing, a slightly lighter bevel at the edges.
    img = G.fill((84, 86, 92), (N, N))
    img = G.add_lum(img, _noise(rng, 1.2, aniso=(1.0, 0.08), kmin=2) * 3.0 + rng.normal(0, 0.8, (N, N)))
    X, Y = G.grid((N, N))
    edge = np.minimum(np.minimum(X, N - 1 - X), np.minimum(Y, N - 1 - Y))
    img = G.add_lum(img, np.where(edge < 1.5, 14.0, 0.0))
    F._save(img, "l228_mirror_frame")

    rng = G.rng_for(22851)
    # Panel: matte, a soft mottle, a faint seam round the edge (panels butt up against each other).
    img = G.fill((78, 78, 84), (N, N))
    img = G.add_lum(img, G.blur(_noise(rng, 2.2, kmin=1.5), 1.0) * 3.5 + rng.normal(0, 0.9, (N, N)))
    img = G.add_lum(img, np.where(edge < 0.5, -12.0, 0.0))
    F._save(img, "l228_panel")

    rng = G.rng_for(22852)
    # Floor: polished dark stone, a few long soft streaks of sheen and a very fine speckle.
    img = G.fill((58, 58, 64), (N, N))
    sheen = G.smoothstep(0.6, 1.8, _noise(rng, 2.6, aniso=(1.0, 0.25), kmin=1.0))
    img = G.add_lum(img, sheen * 14.0 + (rng.random((N, N)) < 0.03) * 10.0, (0.95, 0.97, 1.0))
    F._save(img, "l228_floor")

    rng = G.rng_for(22853)
    # Ceiling: mid-grey board with a pinhole texture (it catches the panels' colour around them).
    img = G.fill((104, 104, 108), (N, N))
    img = G.add_lum(img, G.blur(_noise(rng, 2.0, kmin=2), 0.8) * 3.0 - (rng.random((N, N)) < 0.06) * 14.0)
    F._save(img, "l228_ceiling")


def _pane_model(name: str, texture: str, bright: bool, tint: bool) -> None:
    """A 2 px sheet in the plane x = 8 px (blockstates turn it for axis=z). The big faces carry the
    glass; the thin edges are culled against whatever sits above, below and beside it."""
    def face(direction: str, uv, cull: str | None = None) -> dict:
        f = {"uv": uv, "texture": "#glass"}
        if cull:
            f["cullface"] = cull
        if tint:
            f["tintindex"] = 0
        return f
    element = {"from": [7, 0, 0], "to": [9, 16, 16],
               "faces": {"east": face("east", [0, 0, 16, 16]), "west": face("west", [0, 0, 16, 16]),
                         "north": face("north", [7, 0, 9, 16], "north"), "south": face("south", [7, 0, 9, 16], "south"),
                         "up": face("up", [7, 0, 9, 16], "up"), "down": face("down", [7, 0, 9, 16], "down")}}
    if bright:
        element["neoforge_data"] = FULL_BRIGHT
    F._model(name, {"parent": "minecraft:block/block", "render_type": "minecraft:translucent", "ambientocclusion": False,
                    "textures": {"glass": F._ref(texture), "particle": F._ref(texture)}, "elements": [element]})


def tex_l228_models() -> None:
    """Block models, blockstates and item models of every Level 228 block."""
    for name in ("l228_mirror_frame", "l228_panel", "l228_floor", "l228_ceiling"):
        F._model(name, {"parent": "minecraft:block/cube_all", "textures": {"all": F._ref(name)}})
        F._state(name, {"": F._v(name)})
        F._item_block(name, name)

    # The glass and the black exit mirror: one sheet, turned a quarter for axis=z.
    _pane_model("l228_mirror", "l228_mirror", bright=False, tint=True)
    _pane_model("l228_exit_mirror", "l228_exit_mirror", bright=True, tint=False)
    for name in ("l228_mirror", "l228_exit_mirror"):
        F._state(name, {"axis=x": F._v(name), "axis=z": F._v(name, 0, 90)})
        F._item_block(name, name)

    # The light panels: full-bright cubes (the colour reads the same under any lighting), one per
    # colour; every face takes tint 0, through which the client deepens each SHADE.
    for colour in COLOURS:
        name = f"l228_light_{colour}"
        faces = {f: {"texture": "#all", "cullface": f, "tintindex": 0} for f in ("north", "south", "east", "west", "up", "down")}
        F._model(name, {"parent": "minecraft:block/block", "textures": {"all": F._ref(name), "particle": F._ref(name)},
                        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "neoforge_data": FULL_BRIGHT, "faces": faces}]})
    F._state("l228_light", {f"color={colour}": F._v(f"l228_light_{colour}") for colour in COLOURS})
    F._item_block("l228_light", "l228_light_blue")


TEXTURES = [tex_l228_blocks, tex_l228_models]
SOUNDS = []
