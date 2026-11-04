"""Level 228 ("Hall of Mirrors") blocks: the block models, blockstates and item models of every
l228_ block (world/layout/Level228Plan builds the maze out of them). The block textures and all
of the level's look come from level_assets/level228_props.py.

Shapes (must match block/Level228MirrorBlock): a mirror pane is a 2 px sheet through the middle
of its block; AXIS is the pane's normal (axis=x: the sheet in the plane x = const). In v2 the pane
blocks render nothing in the world (the props draw every pane); the models remain for items.
l228_post (a column's invisible collision) has a particle-only model.

Run only these with:  python tools/gen_assets.py tex_l228_models
"""
from __future__ import annotations

from anomaly_assets import level227_flora as F

FULL_BRIGHT = {"block_light": 15, "sky_light": 15}
COLOURS = ("blue", "green", "red")


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

    # v2: a column's collision. It draws nothing (RenderShape.INVISIBLE); the model only names the
    # particle texture, so no missing-model warning.
    F._model("l228_post", {"textures": {"particle": F._ref("l228_panel")}})
    F._state("l228_post", {"": F._v("l228_post")})


TEXTURES = [tex_l228_models]
SOUNDS = []
