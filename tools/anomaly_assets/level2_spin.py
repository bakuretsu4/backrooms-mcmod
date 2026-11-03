"""Level 2's spinning props (registry/Level2SpinBlocks): block models, blockstates and items.

  l2_wall_fan       a square duct fan set into a wall: frame, back plate, hub and struts behind a
                    round grille; the blades are drawn turning by client/anomaly/level2/WallFanRenderer
  l2_spin_beacon    a caged warning beacon set into the ceiling or a wall: a steel body with a
                    hazard-striped rim round a recess, two-by-two cage bars over it; the amber lens,
                    its turning hot spots and (without Veil) its beams are drawn by SpinBeaconRenderer

No new block textures (the block atlas is full): the fan reuses the old fan housing's l2_fan_grille,
l2_fan_blades, l2_duct, l2_bolt_steel and l2_void_black; the beacon l2_frame_plate, l2_hazard and
bulkhead_cage, and its renderer draws the lens from bulkhead_lamp. The one new texture, the beams'
soft glow, is an entity texture (textures/entity/level2/l2_beam_glow.png, 32 x 64), which is bound
on its own and never enters the block atlas.

Both models are built facing UP (the open front on the +y face) and turned by the blockstate for
the six FACING values, like the painted band. The renderers place the moving parts to match:
the fan's blades 5.6 px behind the grille (y 10.4), the beacon's lens on the recess floor (y 8).
Run with:  python tools/gen_assets.py l2spin
"""
from __future__ import annotations

import numpy as np

import gen_assets as G
from anomaly_assets import level39_props as P
from anomaly_assets.level2_revamp import _box, _facing6_variants, _faces, _octagon, _plain_box, _ref


def _fan_model() -> None:
    els = [_plain_box((0, 0, 0), (1.2, 15, 16), "#duct"), _plain_box((14.8, 0, 0), (16, 15, 16), "#duct"),
           _plain_box((1.2, 0, 0), (14.8, 15, 1.2), "#duct"), _plain_box((1.2, 0, 14.8), (14.8, 15, 16), "#duct"),
           _box((1.2, 0, 1.2), (14.8, 1, 14.8), _faces("#dark")),
           _box((0, 15, 0), (16, 16, 16), _faces("#duct", up=("#grille", [0, 0, 16, 16]), down=("#grille", [16, 0, 0, 16])))]
    # The motor hub on two struts across the duct, behind the blades.
    els += _octagon("y", 4.0, 9.8, 8, 8, 4.4, "#bolt")
    els.append(_plain_box((1.2, 4.8, 7.6), (14.8, 5.6, 8.4), "#duct"))
    els.append(_plain_box((7.6, 4.8, 1.2), (8.4, 5.6, 14.8), "#duct"))
    tex = {"particle": _ref("l2_duct"), "duct": _ref("l2_duct"), "grille": _ref("l2_fan_grille"),
           "bolt": _ref("l2_bolt_steel"), "dark": _ref("l2_void_black")}
    P._bm("l2_wall_fan", P._model(tex, els))
    P._bs("l2_wall_fan", {"variants": _facing6_variants(lambda f: "l2_wall_fan")})
    P._im("l2_wall_fan")


def _beacon_model() -> None:
    # The body up to the recess floor (y 8), the rim round the 10 x 10 recess, hazard stripes on its face.
    els = [_box((0, 0, 0), (16, 8, 16), _faces("#plate", up="#back")),
           _box((0, 8, 0), (3, 16, 16), _faces("#plate", up="#hazard")),
           _box((13, 8, 0), (16, 16, 16), _faces("#plate", up="#hazard")),
           _box((3, 8, 0), (13, 16, 3), _faces("#plate", up="#hazard")),
           _box((3, 8, 13), (13, 16, 16), _faces("#plate", up="#hazard"))]
    # The cage: two bars each way across the recess, just under the face.
    for a in (5.5, 9.5):
        els.append(_plain_box((3, 14.4, a), (13, 15.4, a + 1), "#cage"))
        els.append(_plain_box((a, 14.4, 3), (a + 1, 15.4, 13), "#cage"))
    tex = {"particle": _ref("l2_hazard"), "plate": _ref("l2_frame_plate"), "hazard": _ref("l2_hazard"),
           "back": _ref("bulkhead_cage"), "cage": _ref("bulkhead_cage")}
    P._bm("l2_spin_beacon", P._model(tex, els))
    P._bs("l2_spin_beacon", {"variants": _facing6_variants(lambda f: "l2_spin_beacon")})
    P._im("l2_spin_beacon")


def tex_l2spin_models() -> None:
    """Models and blockstates of the wall fan and the warning beacon."""
    _fan_model()
    _beacon_model()


def tex_l2spin_beam_glow() -> None:
    """The warning beacon's beam (drawn without Veil's lights, SpinBeaconRenderer): white, all the
    shape in the alpha. Across the beam (u) a soft bell that is zero at both edges; along it (v,
    0 = the lens) a soft start at the lens and a long fade to nothing well before the far end, so
    no edge or end of the quads ever shows: light in haze, not a polygon."""
    W, H = 32, 64
    u = (np.arange(W) + 0.5) / W
    v = (np.arange(H) + 0.5) / H
    across = np.cos((u - 0.5) * np.pi) ** 3
    along = (1.0 - v) ** 2.2 * np.minimum(1.0, v * 8.0 + 0.35)
    a = np.outer(along, across)
    img = np.zeros((H, W, 4))
    img[..., :3] = 255.0
    img[..., 3] = a / a.max() * 255.0
    G.save_png(img, G.TEX / "entity" / "level2" / "l2_beam_glow.png")


TEXTURES = [tex_l2spin_models, tex_l2spin_beam_glow]
