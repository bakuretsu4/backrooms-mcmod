"""Level 9 (the Suburbs), street life: textures, block models and sounds.

What this makes (all procedural, fixed random seeds 9300-9399, so re-running gives identical files):

* Parked cars: one 128 px texture (``block/l9_car``) holding every car surface (paint, glass,
  rubber, the front and back faces), and the models. A car is 2 x 4 blocks; two of its cells
  draw half of the car each (elements may reach one block outside their own cell), and the
  other six are invisible collision pieces. The paint is grey in the texture and coloured in
  game (block tint), so one set of models gives every colour.
* Yard props on one shared 128 px texture (``block/l9_yard_props``): a tipped-over tricycle,
  a ball, a kid's bike lying in the grass, a wheelie bin, a lawn sprinkler and the swing set
  frame. The swing seats and chains are drawn by code (client/level9/SwingSetRenderer) from
  ``entity/l9_swing``.
* The mailbox with its flag up (a copy of mailbox.json with the flag arm raised) and the
  mailbox blockstate with the flag property.
* Sounds: the Level 9 night bed (``ambient/level9_ambience``: crickets, traffic far off that
  never comes closer, faint wind chimes), a barking dog chorus that the game cuts off mid-bark
  (``level9/dog_chorus1-4``), the swing set's rusty creak (``level9/swing_creak1-3``) and a lawn
  sprinkler's tick-tick-tick-hiss (``level9/sprinkler1-2``).

Run:  python tools/level9_assets/street.py [name_filter ...]
"""
from __future__ import annotations

import json
import sys
import threading
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import gen_assets as G  # noqa: E402  (needs the tools folder on the path first)

SR = G.SR
MODELS = G.ROOT / "models"
BLOCKSTATES = G.ROOT / "blockstates"


# =============================================================================
# Small helpers
# =============================================================================

def _save_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def _rgba(img: np.ndarray, alpha: np.ndarray | None = None) -> np.ndarray:
    a = np.full(img.shape[:2], 255.0) if alpha is None else alpha
    return np.concatenate([img, a[..., None]], axis=2)


def _noise(rng, size=128, beta=2.2) -> np.ndarray:
    return G.fnoise(rng, (size, size), beta=beta)


def _disc(shape, cx, cy, r) -> np.ndarray:
    """True inside a circle (pixel centres)."""
    yy, xx = np.mgrid[0:shape[0], 0:shape[1]]
    return (xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2 <= r * r


# Block model building. A face is (region, tint, flip): region = [u0, v0, u1, v1] in model UV
# units (0-16 across the whole texture), stretched over the face.
def _face(region, tint=False, flip=False):
    u0, v0, u1, v1 = region
    uv = [u1, v0, u0, v1] if flip else [u0, v0, u1, v1]
    f = {"uv": [round(c, 4) for c in uv], "texture": "#t"}
    if tint:
        f["tintindex"] = 0
    return f


def _el(fr, to, faces: dict, rotation=None) -> dict:
    # Minecraft refuses a block model whose element corners lie outside -16..32 (the model fails
    # to load), so catch that here instead of in game.
    if any(c < -16 or c > 32 for c in (*fr, *to)):
        raise ValueError(f"model element {fr} -> {to} reaches outside -16..32")
    e = {"from": [round(c, 4) for c in fr], "to": [round(c, 4) for c in to], "faces": faces}
    if rotation is not None:
        origin, axis, angle = rotation
        e["rotation"] = {"origin": list(origin), "axis": axis, "angle": angle}
    return e


def _all(region, tint=False) -> dict:
    return {d: _face(region, tint) for d in ("north", "south", "east", "west", "up", "down")}


def _model(texture: str, elements: list, cutout: bool = False) -> dict:
    m = {"parent": "minecraft:block/block", "ambientocclusion": False}
    if cutout:
        m["render_type"] = "minecraft:cutout"
    m["textures"] = {"particle": f"backrooms:block/{texture}", "t": f"backrooms:block/{texture}"}
    m["elements"] = elements
    return m


def _facing_states(model: str) -> dict:
    """The usual four-way blockstate: the model faces north, the others are turned."""
    return {"variants": {f"facing={d}": ({"model": model} | ({"y": y} if y else {}))
                         for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}}


FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


# =============================================================================
# Parked car
# =============================================================================

# Regions of block/l9_car (model UV units: 8 px each).
CAR_PAINT = (0, 0, 4, 4)       # plain paint (grey; tinted in game)
CAR_GLASS = (4, 0, 8, 4)
CAR_TRIM = (8, 0, 12, 4)       # black rubber and plastic
CAR_TIRE = (12, 0, 16, 4)      # tyre seen from the side, with its hubcap
CAR_SIDE = (0, 4, 16, 6)       # the whole body side, front at the left (tinted)
CAR_FRONT = (0, 6, 8, 8)       # grille and headlights
CAR_REAR = (8, 6, 16, 8)       # tail lights and number plate

# The car in its own frame (it faces north: front at z = 0): x 0..32 across (two cells), z 0..64
# front to back (four cells), y up. The body side texture covers z 1..63.
BODY_Z0, BODY_Z1 = 1.0, 63.0


def _side_u(z0, z1):
    """UV columns of the side texture for body z0..z1 (front of the car = left of the image)."""
    k = 16.0 / (BODY_Z1 - BODY_Z0)
    return (z0 - BODY_Z0) * k, (z1 - BODY_Z0) * k


def tex_l9_car() -> None:
    """block/l9_car: every surface of a parked 1990s sedan on one 128 px sheet."""
    rng = G.rng_for(9301)
    S = 128
    img = np.zeros((S, S, 3))
    n1 = _noise(rng)
    n2 = _noise(rng, beta=1.4)

    # Paint: a light grey that the block tint colours; faint orange-peel and a little road grime.
    paint = 212 + 5 * n1 - 3 * np.clip(n2, 0, None)
    img[0:32, 0:32] = paint[0:32, 0:32, None]

    # Glass: dark, a cold sheen, a faint diagonal reflection band.
    yy, xx = np.mgrid[0:32, 0:32]
    band = np.exp(-(((xx - yy * 0.7) - 10) / 3.5) ** 2) * 0.6 + np.exp(-(((xx - yy * 0.7) - 20) / 1.5) ** 2) * 0.3
    glass = np.stack([28 + 18 * band, 34 + 20 * band, 42 + 22 * band], axis=-1) + 2.5 * n1[0:32, 32:64, None]
    img[0:32, 32:64] = glass

    # Trim: black plastic and rubber.
    img[0:32, 64:96] = (30 + 4 * n2[0:32, 64:96])[..., None]

    # Tyre side with a grey hubcap and five lug bolts.
    tyre = np.full((32, 32, 3), 22.0) + 2 * n1[0:32, 96:128, None]
    hub = _disc((32, 32), 16, 16, 9.5)
    rim = _disc((32, 32), 16, 16, 10.5) & ~hub
    cap = _disc((32, 32), 16, 16, 3.2)
    tyre[rim] = 70
    tyre[hub] = np.array([118, 120, 124]) + 6 * n2[0:32, 96:128][hub][:, None]
    for k in range(5):
        a = k * 2 * np.pi / 5
        tyre[_disc((32, 32), 16 + 6 * np.cos(a), 16 + 6 * np.sin(a), 1.2)] = 70
    tyre[cap] = 150
    img[0:32, 96:128] = tyre

    # The body side (rows 32-47, 16 px for the 8 px tall body; 128 px for 62 px of length):
    # door seams, handles, a black rubbing strip, wheel arches and grime along the sill.
    side = (210 + 5 * n1[32:48, :] - 3 * np.clip(n2[32:48, :], 0, None))[..., None].repeat(3, axis=2)
    px = lambda z: (z - BODY_Z0) / (BODY_Z1 - BODY_Z0) * 128.0  # noqa: E731
    side[7:9, :] = 60                                              # rubbing strip
    for z in (22.0, 37.0, 48.5):
        c = int(round(px(z)))
        side[0:14, c:c + 1] = 95                                   # door seams
    for z in (35.0, 46.5):
        c = int(round(px(z)))
        side[3:5, c - 4:c] = 70                                    # door handles
    side[14:16, :] *= 0.72                                         # grime along the sill
    for zc in (13.0, 50.0):                                        # wheel arches
        yy, xx = np.mgrid[0:16, 0:128]
        arch = ((xx + 0.5 - px(zc)) / 10.5) ** 2 + ((yy + 0.5 - 16.5) / 10.0) ** 2 <= 1.0
        side[arch] = 16
    img[32:48, :] = side

    # Front (rows 48-63, cols 0-63): black fascia, slatted grille, two headlights, amber corners.
    front = np.full((16, 64, 3), 34.0) + 3 * n2[48:64, 0:64, None]
    front[3:10, 20:44] = 26
    front[4:10:2, 21:43] = 58                                      # grille slats
    for c0 in (3, 47):
        front[2:10, c0:c0 + 14] = 88                               # headlight bezels
        lamp = np.linspace(0.85, 1.0, 12)[None, :, None]
        front[3:9, c0 + 1:c0 + 13] = np.array([214, 208, 182]) * lamp
    front[10:12, 3:9] = (190, 120, 44)
    front[10:12, 55:61] = (190, 120, 44)
    front[12:16, :] = 24                                           # bumper shadow
    front[5:7, 31:33] = 160                                        # badge
    img[48:64, 0:64] = front

    # Rear (rows 48-63, cols 64-127): tail lights, number plate, the boot lid's edge.
    rear = np.full((16, 64, 3), 38.0) + 3 * n2[48:64, 64:128, None]
    rear[1:2, :] = 22
    for c0 in (2, 48):
        rear[2:10, c0:c0 + 14] = (128, 26, 24)
        rear[3:5, c0 + 1:c0 + 13] = (170, 44, 40)
        rear[8:10, c0:c0 + 5] = (190, 190, 182)                    # reversing lamp
    rear[4:10, 24:40] = (196, 196, 182)
    for c in range(26, 39, 3):
        rear[6:8, c:c + 2] = 48                                    # plate lettering
    rear[12:16, :] = 26
    img[48:64, 64:128] = rear

    # The unused bottom half: plain trim, so mip-maps don't bleed odd colours.
    img[64:128, :] = 30
    G.save_png(img, G.TEX / "block" / "l9_car.png")


def _car_half(front: bool) -> list:
    """The elements of one half of the car, in the coordinates of the cell that draws it.

    The front half is drawn by the front-left cell (car z 16-32), the back half by the back-left
    cell (car z 32-48), so car z maps to model z = car z - 16 / - 32.
    """
    dz = -16.0 if front else -32.0
    els = []

    def box(x0, y0, z0, x1, y1, z1, faces, rotation=None):
        els.append(_el((x0, y0, z0 + dz), (x1, y1, z1 + dz), faces, rotation))

    if front:
        z0, z1 = BODY_Z0, 32.0
        u0, u1 = _side_u(z0, z1)
        box(1, 3, z0, 31, 11, z1, {
            "north": _face(CAR_FRONT),
            "west": _face((u0, 4, u1, 6), tint=True),
            "east": _face((u0, 4, u1, 6), tint=True, flip=True),
            "up": _face(CAR_PAINT, tint=True),
            "down": _face(CAR_TRIM)})
        box(0.5, 3, 0, 31.5, 6.5, 1, _all(CAR_TRIM))                       # front bumper
        # Windscreen: a plate leaning back from the scuttle (+22.5 degrees about x tips its top
        # toward +z, the back of the car).
        box(3, 11, 22, 29, 20, 23, {
            "north": _face(CAR_GLASS), "south": _face(CAR_GLASS),
            "east": _face(CAR_TRIM), "west": _face(CAR_TRIM), "up": _face(CAR_TRIM)},
            rotation=((16, 11, 22 + dz), "x", 22.5))
        box(3, 11, 25, 29, 19.3, 32, {"west": _face(CAR_GLASS), "east": _face(CAR_GLASS, flip=True)})
        box(3, 19, 24.5, 29, 20, 32, {
            "north": _face(CAR_PAINT, tint=True), "east": _face(CAR_PAINT, tint=True),
            "west": _face(CAR_PAINT, tint=True), "up": _face(CAR_PAINT, tint=True),
            "down": _face(CAR_TRIM)})
        # Door mirrors. They stick out past the body (x 1-31) but stay inside the model's limit:
        # block model elements may only reach x 32 (the right-hand cell's far edge).
        box(-0.5, 11, 23, 1, 12.5, 25, _all(CAR_TRIM))
        box(31, 11, 23, 32, 12.5, 25, _all(CAR_TRIM))
        wz = (9, 17)
    else:
        z0, z1 = 32.0, BODY_Z1
        u0, u1 = _side_u(z0, z1)
        box(1, 3, z0, 31, 11, z1, {
            "south": _face(CAR_REAR),
            "west": _face((u0, 4, u1, 6), tint=True),
            "east": _face((u0, 4, u1, 6), tint=True, flip=True),
            "up": _face(CAR_PAINT, tint=True),
            "down": _face(CAR_TRIM)})
        box(0.5, 3, 63, 31.5, 6.5, 64, _all(CAR_TRIM))                     # rear bumper
        box(3, 11, 32, 29, 19.3, 45, {"west": _face(CAR_GLASS), "east": _face(CAR_GLASS, flip=True)})
        box(3, 19, 32, 29, 20, 45.5, {
            "south": _face(CAR_PAINT, tint=True), "east": _face(CAR_PAINT, tint=True),
            "west": _face(CAR_PAINT, tint=True), "up": _face(CAR_PAINT, tint=True),
            "down": _face(CAR_TRIM)})
        # Rear window, leaning forward (-22.5 degrees tips its top toward the front).
        box(3, 11, 46, 29, 20, 47, {
            "north": _face(CAR_GLASS), "south": _face(CAR_GLASS),
            "east": _face(CAR_TRIM), "west": _face(CAR_TRIM), "up": _face(CAR_TRIM)},
            rotation=((16, 11, 47 + dz), "x", -22.5))
        wz = (46, 54)
    for x0, x1 in ((1.5, 5.5), (26.5, 30.5)):                               # the two tyres
        box(x0, 0, wz[0], x1, 7, wz[1], {
            "west": _face(CAR_TIRE), "east": _face(CAR_TIRE),
            "north": _face(CAR_TRIM), "south": _face(CAR_TRIM),
            "up": _face(CAR_TRIM), "down": _face(CAR_TRIM)})
    return els


PIECES = ("nose_l", "nose_r", "front_l", "front_r", "back_l", "back_r", "rear_l", "rear_r")


def model_l9_car() -> None:
    """Car models (front half, back half, empty hull piece) and the l9_parked_car blockstate."""
    _save_json(MODELS / "block" / "l9_car_front.json", _model("l9_car", _car_half(True)))
    _save_json(MODELS / "block" / "l9_car_back.json", _model("l9_car", _car_half(False)))
    _save_json(MODELS / "block" / "l9_car_hull.json",
               {"parent": "minecraft:block/block", "textures": {"particle": "backrooms:block/l9_car"}})
    variants = {}
    for d, y in FACINGS:
        for p in PIECES:
            m = {"front_l": "l9_car_front", "back_l": "l9_car_back"}.get(p, "l9_car_hull")
            variants[f"facing={d},piece={p}"] = {"model": f"backrooms:block/{m}"} | ({"y": y} if y else {})
    _save_json(BLOCKSTATES / "l9_parked_car.json", {"variants": variants})


# =============================================================================
# Yard props
# =============================================================================

P_RED = (0, 0, 4, 4)
P_TIRE = (4, 0, 8, 4)
P_WHEEL = (8, 0, 12, 4)        # a whole wheel seen from the side (cut out, round)
P_WHITE = (12, 0, 16, 4)
P_BALL = (0, 4, 4, 8)
P_BLUE = (4, 4, 8, 8)
P_CHROME = (8, 4, 12, 8)
P_BIN = (12, 4, 16, 8)
P_LID = (0, 8, 4, 12)
P_BRASS = (4, 8, 8, 12)
P_FRAME = (8, 8, 12, 12)
P_BIN_FRONT = (12, 8, 16, 12)
P_RUBBER = (0, 12, 4, 16)


def tex_l9_yard_props() -> None:
    """block/l9_yard_props: the colours of the yard props, 32 px squares on one 128 px sheet."""
    rng = G.rng_for(9302)
    S = 128
    img = np.zeros((S, S, 3))
    alpha = np.full((S, S), 255.0)
    n1 = _noise(rng)
    n2 = _noise(rng, beta=1.2)

    def put(region, rgb, grain=5.0, noise=n1):
        x0, y0, x1, y1 = (int(c * 8) for c in region)
        img[y0:y1, x0:x1] = np.asarray(rgb, float) + grain * noise[y0:y1, x0:x1, None]

    put(P_RED, (150, 34, 30))
    img[0:32, 0:32][np.clip(n2[0:32, 0:32], 0, None) > 1.6] = (120, 104, 96)   # chipped paint
    put(P_TIRE, (24, 24, 26), 2)
    img[0:32, 32:64][::4] = 40                                                  # tread
    put(P_WHITE, (204, 200, 190), 4)
    put(P_BALL, (176, 40, 36), 4)
    img[44:52, 0:32] = (214, 210, 200)                                          # the stripe
    put(P_BLUE, (42, 72, 142), 5)
    img[32:64, 32:64][np.clip(n2[32:64, 32:64], 0, None) > 1.8] = (150, 150, 156)
    put(P_CHROME, (150, 152, 158), 7, n2)
    put(P_BIN, (42, 72, 50), 4)
    img[32:64, 96:128][:, ::6] -= 10                                            # moulded ribs
    put(P_LID, (34, 60, 40), 4)
    img[64:66, 0:32] = (60, 92, 66)
    put(P_BRASS, (150, 118, 58), 8, n2)
    put(P_FRAME, (70, 96, 122), 5)
    rust = np.clip(n2[64:96, 64:96], 0, None) > 1.2
    img[64:96, 64:96][rust] = (118, 70, 42)
    put(P_BIN_FRONT, (42, 72, 50), 4)
    img[74:82, 104:120] = (200, 198, 186)                                       # address label
    img[76:80, 107:110] = 40
    img[76:80, 112:115] = 40
    put(P_RUBBER, (26, 25, 24), 3)

    # The wheel: round (transparent corners), black tyre, grey rim, spokes, a hub.
    w = np.full((32, 32, 3), 24.0)
    rim = _disc((32, 32), 16, 16, 12.0)
    hub = _disc((32, 32), 16, 16, 2.6)
    w[rim] = (40, 40, 42)
    w[_disc((32, 32), 16, 16, 11.0)] = (90, 92, 96)
    w[_disc((32, 32), 16, 16, 10.0)] = (0, 0, 0)
    yy, xx = np.mgrid[0:32, 0:32]
    ang = np.arctan2(yy + 0.5 - 16, xx + 0.5 - 16)
    spokes = (np.abs(((ang * 8 / np.pi) % 2) - 1) > 0.82) & _disc((32, 32), 16, 16, 10.0)
    w[spokes] = (150, 152, 158)
    w[hub] = (170, 170, 172)
    open_ = _disc((32, 32), 16, 16, 10.0) & ~spokes & ~hub
    a = np.where(_disc((32, 32), 16, 16, 15.5), 255.0, 0.0)
    a[open_] = 0.0
    w[~_disc((32, 32), 16, 16, 15.5)] = 24
    img[0:32, 64:96] = w
    alpha[0:32, 64:96] = a

    G.save_png(_rgba(img, alpha), G.TEX / "block" / "l9_yard_props.png")


def tex_l9_swing() -> None:
    """entity/l9_swing: the swing set's chains (left half) and rubber seats (right half)."""
    rng = G.rng_for(9303)
    img = np.zeros((32, 32, 3))
    n = G.fnoise(rng, (32, 32), beta=1.5)
    img[:, 0:16] = 108 + 6 * n[:, 0:16, None]
    img[::3, 0:16] = 64                                       # the links
    img[:, 16:32] = 30 + 3 * n[:, 16:32, None]
    img[0:2, 16:32] = 46
    img[16:32, :] = 30
    G.save_png(img, G.TEX / "entity" / "l9_swing.png")


def model_l9_props() -> None:
    """Models, item models and blockstates of the tricycle, ball, bike, bin, sprinkler, swing set."""
    tex = "l9_yard_props"

    def wheel(fr, to):
        # A flat wheel lying in the grass: round on top and bottom, tyre round the rim.
        return _el(fr, to, {"up": _face(P_WHEEL), "down": _face(P_WHEEL), "north": _face(P_TIRE),
                            "south": _face(P_TIRE), "east": _face(P_TIRE), "west": _face(P_TIRE)})

    tricycle = [
        wheel((3, 0.5, -1), (13, 1.75, 9)),                               # front wheel, flat
        _el((7.5, 1.75, 3.5), (8.5, 4.5, 4.5), _all(P_CHROME)),           # crank sticking up
        _el((6.5, 4.0, 3.0), (9.5, 5.0, 5.0), _all(P_WHITE)),             # its pedal
        _el((7, 1.5, 4), (9, 3, 14), _all(P_RED)),                        # frame
        _el((7.5, 1.5, 1.5), (8.5, 3.5, 4.5), _all(P_RED)),               # head tube
        _el((7, 3.5, 2), (9, 8, 3), _all(P_WHITE)),                       # handlebar grip, up
        _el((5, 3, 9.5), (9, 4.5, 12.5), _all(P_WHITE)),                  # seat
        _el((7.5, 0.5, 13.5), (8.5, 9, 14.5), _all(P_CHROME)),            # rear axle, now upright
        wheel((5, 0, 11.5), (11, 1, 17.5)),                               # rear wheel on the ground
        wheel((5, 8, 11.5), (11, 9, 17.5)),                               # rear wheel up in the air
    ]
    ball = [
        _el((4.5, 0.5, 4.5), (11.5, 7.5, 11.5), _all(P_BALL)),
        _el((5.5, 0, 5.5), (10.5, 8, 10.5), _all(P_BALL)),
        _el((4, 1.5, 5.5), (12, 6.5, 10.5), _all(P_BALL)),
        _el((5.5, 1.5, 4), (10.5, 6.5, 12), _all(P_BALL)),
    ]
    bike = [
        wheel((2.5, 0, -6), (13.5, 1, 5)),                                # front wheel
        wheel((2.5, 0, 11), (13.5, 1, 22)),                               # back wheel
        _el((10, 1, 0), (11.5, 2.5, 12), _all(P_BLUE)),                   # top tube
        _el((6, 1, 1), (7.5, 2.5, 12), _all(P_BLUE), ((6.75, 1.75, 6.5), "y", 22.5)),  # down tube
        _el((7.25, 1, 11), (8.75, 2, 17), _all(P_CHROME)),                # chain stay
        _el((7.5, 1, -0.5), (11, 2, 1.5), _all(P_CHROME)),                # fork
        _el((10.5, 1, 0.5), (11.5, 9, 1.5), _all(P_CHROME)),              # handlebar, one end up
        _el((10.25, 7, 0.25), (11.75, 10, 1.75), _all(P_RUBBER)),         # its grip
        _el((11, 0, 1), (16, 1.5, 2), _all(P_RUBBER)),                    # the other grip, in the grass
        _el((11, 1, 11.5), (14.5, 3.5, 15), _all(P_RUBBER)),              # saddle
        _el((5, 1, 10.5), (6, 5, 11.5), _all(P_CHROME)),                  # crank
        _el((4.5, 4.5, 10), (6.5, 5.5, 12), _all(P_RUBBER)),              # pedal
    ]
    bin_ = [
        _el((2.5, 1, 2), (13.5, 14, 13.5), {
            "north": _face(P_BIN_FRONT), "south": _face(P_BIN), "east": _face(P_BIN),
            "west": _face(P_BIN), "down": _face(P_BIN)}),
        _el((2, 14, 1.5), (14, 15.5, 14.5), _all(P_LID)),
        _el((3, 13.5, 14), (13, 15, 15.5), _all(P_LID)),                  # handle
        _el((1.5, 0, 12), (3, 3, 15), _all(P_RUBBER)),                    # wheels
        _el((13, 0, 12), (14.5, 3, 15), _all(P_RUBBER)),
        _el((3, 0.5, 13), (13, 2, 14.5), _all(P_RUBBER)),                 # axle
        _el((3, 0, 2.5), (13, 1, 4), _all(P_BIN)),                        # front foot
    ]
    sprinkler = [
        _el((7, 0, 7), (9, 1.5, 9), _all(P_RUBBER)),                      # spike in the lawn
        _el((7.5, 1.5, 7.5), (8.5, 4, 8.5), _all(P_BRASS)),               # riser
        _el((6.75, 4, 6.75), (9.25, 5.5, 9.25), _all(P_BRASS)),           # head
        _el((7.6, 5, 3.5), (8.4, 5.8, 7), _all(P_BRASS)),                 # nozzle (points north)
        _el((7.75, 5.5, 7), (8.25, 6.25, 12), _all(P_RUBBER)),            # impact arm
        _el((7.5, 5.25, 11.5), (8.5, 6.5, 12.5), _all(P_BRASS)),          # its counterweight
    ]
    for name, els, cutout in (("l9_tricycle", tricycle, True), ("l9_toy_ball", ball, False),
                              ("l9_bike", bike, True), ("l9_trash_bin", bin_, False),
                              ("l9_sprinkler", sprinkler, False)):
        _save_json(MODELS / "block" / f"{name}.json", _model(tex, els, cutout))
        _save_json(MODELS / "item" / f"{name}.json", {"parent": f"backrooms:block/{name}"})
        _save_json(BLOCKSTATES / f"{name}.json", _facing_states(f"backrooms:block/{name}"))

    # Swing set frame, drawn by the middle cell for the whole 3-block set (the swings face north,
    # the top bar runs along x). A-frame legs at both ends lean 22.5 degrees out to the front and
    # back and meet under the bar. The seats and chains are drawn by the block entity renderer.
    frame = [_el((-15.5, 29.5, 7), (31.5, 31.5, 9), _all(P_FRAME))]
    for x0 in (-15.5, 30.0):
        for angle in (22.5, -22.5):
            frame.append(_el((x0, -2.5, 7.25), (x0 + 1.5, 31, 8.75), _all(P_FRAME),
                             ((x0 + 0.75, 30.5, 8), "x", angle)))
    _save_json(MODELS / "block" / "l9_swing_set.json", _model(tex, frame))
    _save_json(MODELS / "block" / "l9_swing_set_leg.json",
               {"parent": "minecraft:block/block", "textures": {"particle": f"backrooms:block/{tex}"}})
    variants = {}
    for d, y in FACINGS:
        for part in ("left", "middle", "right"):
            m = "l9_swing_set" if part == "middle" else "l9_swing_set_leg"
            variants[f"facing={d},part={part}"] = {"model": f"backrooms:block/{m}"} | ({"y": y} if y else {})
    _save_json(BLOCKSTATES / "l9_swing_set.json", {"variants": variants})


def model_mailbox_flag() -> None:
    """The mailbox with its red flag raised (someone left mail to go out), and the blockstate.

    Built from mailbox.json: the two flag elements (the arm lying along the box and the plate)
    are swapped for an arm standing up and the plate at its top.
    """
    src = json.loads((MODELS / "block" / "mailbox.json").read_text(encoding="utf-8"))
    flag_faces = src["elements"][-1]["faces"]
    up = dict(src)
    up["elements"] = src["elements"][:-2] + [
        {"from": [10.5, 11.5, 11.75], "to": [11, 18, 12.5], "faces": flag_faces},
        {"from": [10.5, 15.5, 9.25], "to": [11, 18, 11.75], "faces": flag_faces},
    ]
    _save_json(MODELS / "block" / "mailbox_flag_up.json", up)
    variants = {}
    for d, y in FACINGS:
        for flag, m in (("false", "mailbox"), ("true", "mailbox_flag_up")):
            variants[f"facing={d},flag={flag}"] = {"model": f"backrooms:block/{m}"} | ({"y": y} if y else {})
    _save_json(BLOCKSTATES / "mailbox.json", {"variants": variants})


# =============================================================================
# Sounds
# =============================================================================

def snd_level9_ambience() -> None:
    """ambient/level9_ambience, a 24 s seamless loop: the Level 9 night outdoors.

    Crickets (five of them, each its own pitch and rhythm, plus a far chorus), the low wash of
    traffic on a highway somewhere that swells as if a car were coming and then never arrives,
    and now and then a wind chime on a porch stirring a little although there is no wind. Every
    part is periodic over the loop (circular placement, periodic noise), so the seam is clean.
    """
    rng = G.rng_for(9310)
    T = 24.0
    n = int(T * SR)
    t = np.arange(n) / SR

    # Still night air, barely there.
    air = G.periodic_noise(n, rng, lambda f: G.hp_gain(f, 40, 2) * G.lp_gain(f, 700, 2) / np.maximum(f, 40) ** 0.5)
    air *= G.drift(n, rng, 0.2, 0.05, 0.2)

    # Crickets: short chirps of 2-3 pulses on a 4-5 kHz carrier.
    crickets = np.zeros(n)
    for k in range(5):
        fc = rng.uniform(4100, 4900)
        rate = rng.uniform(1.5, 2.6)
        pulses = int(rng.integers(2, 4))
        pulse_hz = rng.uniform(28, 40)
        near = rng.uniform(0.25, 1.0)
        tt = rng.uniform(0, 1 / rate)
        while tt < T:
            m = int(pulses / pulse_hz * SR)
            tc = np.arange(m) / SR
            am = np.sin(np.pi * pulse_hz * tc) ** 2
            chirp = np.sin(2 * np.pi * fc * tc + rng.uniform(0, 2 * np.pi)) * am
            G.add_at(crickets, chirp * near * rng.uniform(0.75, 1.0), tt, circular=True)
            tt += rng.uniform(0.9, 1.1) / rate
            if rng.random() < 0.06:
                tt += rng.uniform(0.8, 2.5)  # a cricket pauses now and then
    crickets = G.fft_filter(crickets, lambda f: G.lp_gain(f, 6500, 2), circular=True)
    chorus = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 3900, 5300, 3))
    chorus *= (0.55 + 0.45 * np.sin(2 * np.pi * 33.0 * t) ** 2) * G.drift(n, rng, 0.35, 0.05, 0.25)

    # Traffic far off: a steady low wash, and one swell (a car coming?) that fades away again.
    wash = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 35, 380, 2) / np.maximum(f, 35) ** 0.4)
    swell_env = np.interp(t, [0, 3.0, 11.0, 13.0, 18.0, T], [0, 0, 1.0, 0.85, 0, 0]) ** 1.6
    swell = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 60, 900, 2) / np.maximum(f, 60) ** 0.3)
    whine_f = np.interp(t, [0, 11.0, 18.0, T], [104, 101, 97, 97])
    whine = np.sin(2 * np.pi * np.cumsum(whine_f) / SR)
    traffic = G.norm(wash) * 0.55 + (G.norm(swell) + 0.25 * whine) * swell_env

    # Wind chimes, faint: two little clusters of strikes.
    chimes = np.zeros(n)
    base = rng.uniform(780, 880)
    for start in (6.3, 17.6):
        tt = start
        for _ in range(int(rng.integers(3, 6))):
            f = base * rng.choice([1, 9 / 8, 5 / 4, 3 / 2, 5 / 3]) * rng.uniform(0.99, 1.01)
            hit = G.ring([f, f * 2.76, f * 5.4], [rng.uniform(1.4, 2.2), 0.6, 0.25], [1, 0.3, 0.1], 3.0)
            G.add_at(chimes, hit * rng.uniform(0.3, 1.0), tt, circular=True)
            tt += rng.exponential(0.22)
    chimes = G.fft_filter(chimes, lambda f: G.lp_gain(f, 3800, 2), circular=True)

    def rms(x):
        return np.sqrt(np.mean(x ** 2)) + 1e-12

    def db(x, level):
        return x / rms(x) * 10 ** (level / 20)

    x = (db(crickets, 0) + db(chorus, -13) + db(air, -15) + db(traffic, -12)
         + chimes / (np.abs(chimes).max() + 1e-12) * 10 ** (-12 / 20) * 3)
    G.save_ogg(x, G.SND / "ambient" / "level9_ambience.ogg", peak=0.42, loop=True)


# The four dogs of the chorus: bark pitch (start, end ranges in Hz), formants, bark length,
# gaps between barks, breathiness.
DOGS = [
    dict(hi=(330, 380), lo=(210, 250), form=[(450, 120, 1.0), (1000, 180, 0.6), (2200, 300, 0.2)],
         dur=(0.18, 0.26), gap=(0.35, 0.6), rough=0.5, lp=1800),     # a big dog, deep
    dict(hi=(480, 620), lo=(300, 380), form=[(700, 150, 1.0), (1450, 200, 0.7), (2600, 300, 0.3)],
         dur=(0.12, 0.2), gap=(0.25, 0.45), rough=0.4, lp=2200),     # an ordinary yard dog
    dict(hi=(850, 1000), lo=(600, 700), form=[(1100, 200, 1.0), (2300, 300, 0.6), (3500, 400, 0.2)],
         dur=(0.07, 0.11), gap=(0.12, 0.22), rough=0.3, lp=2800),    # a small yappy one
    dict(hi=(420, 500), lo=(280, 320), form=[(600, 150, 1.0), (1250, 200, 0.6), (2400, 300, 0.3)],
         dur=(0.15, 0.22), gap=(0.3, 0.5), rough=1.2, lp=2000),      # an old hoarse one
]


def snd_level9_dog_chorus() -> None:
    """level9/dog_chorus1-4: 9 s of steady barking, one dog each, far off down the street.

    Groups of 2-5 barks with short pauses, starting straight away. The game plays 2-4 of these
    at once from different directions and stops them all on the same tick, so the barking ends
    dead in the middle of a bark (client/level9/DogsGoQuiet).
    """
    for i, dog in enumerate(DOGS, start=1):
        rng = G.rng_for(9320 + i)
        T = 9.0
        x = np.zeros(int(T * SR))
        tt = rng.uniform(0.02, 0.12)
        while tt < T - 0.3:
            for _ in range(int(rng.integers(2, 6))):
                bd = rng.uniform(*dog["dur"])
                m = int(bd * SR)
                f0 = np.geomspace(rng.uniform(*dog["hi"]), rng.uniform(*dog["lo"]), m) * (1 + G.smooth_jitter(rng, m, 8, 0.03))
                v = G.voice(f0, dog["form"], rng, nh=24, tilt=0.05)
                bark = (G.norm(v) + dog["rough"] * G._da_band(rng, m, 400, 3000)) \
                    * G._da_env(m, [(0, 0), (0.01, 1), (bd * 0.35, 0.85), (bd, 0)])
                G.add_at(x, bark * rng.uniform(0.75, 1.0), tt, circular=False)
                tt += bd + rng.uniform(*dog["gap"])
                if tt >= T - 0.3:
                    break
            tt += rng.uniform(0.4, 1.0)
        x = G._da_far(x, rng, dog["lp"], hp=200, air=0.12, room=0.5)
        G.save_ogg(x, G.SND / "level9" / f"dog_chorus{i}.ogg", peak=0.7, fade_in=0.002, fade_out=0.05)


def snd_level9_swing_creak() -> None:
    """level9/swing_creak1-3: a rusty swing hook squeaking, forth and (sometimes) back."""
    for i in range(1, 4):
        rng = G.rng_for(9330 + i)
        x = np.zeros(int(2.2 * SR))
        tt = 0.03
        for leg in range(1 if i == 2 else 2):
            dur = rng.uniform(0.35, 0.6)
            r0 = rng.uniform(380, 520)
            sq = G._da_stickslip(rng, dur, [(0, r0), (dur * 0.5, r0 * rng.uniform(1.2, 1.45)), (dur, r0 * 0.85)],
                                 [rng.uniform(1100, 1400), rng.uniform(2300, 2800), 4200], [0.02, 0.012, 0.006], [1, 0.6, 0.25])
            sq *= G._da_env(len(sq), [(0, 0), (0.05, 1), (dur * 0.7, 0.8), (dur, 0)])
            G.add_at(x, G.norm(sq) * (1.0 if leg == 0 else 0.7), tt, circular=False)
            tt += dur + rng.uniform(0.35, 0.6)
        x = G._da_far(x, rng, 5000, hp=300, air=0.1, room=0.3)
        G.save_ogg(x, G.SND / "level9" / f"swing_creak{i}.ogg", peak=0.55, fade_in=0.002, fade_out=0.05)


def snd_level9_sprinkler() -> None:
    """level9/sprinkler1-2: one 6 s sweep of an impact sprinkler, matching the game's cycle.

    18 ticks a quarter of a second apart, each the arm snapping back into the jet (a click and a
    spit of water), over a soft spray; then 1.5 s of the fast return ratchet and a louder hiss as
    it swings back to the start (client: SprinklerBlockEntity plays one every 6 s while it runs).
    """
    for i in range(1, 3):
        rng = G.rng_for(9340 + i)
        T = 6.0
        n = int(T * SR)
        t = np.arange(n) / SR
        x = np.zeros(n)
        for k in range(18):
            tt = 0.05 + k * 0.25 + rng.uniform(-0.01, 0.01)
            click = G._da_hit(rng, [rng.uniform(2400, 2900), rng.uniform(3900, 4400)], [0.004, 0.003], [1, 0.5], noise_ms=2.0)
            m = int(0.07 * SR)
            spit = G._da_band(rng, m, 1500, 7000) * np.exp(-np.arange(m) / (0.02 * SR))
            G.add_at(x, 0.6 * G.norm(click), tt, circular=False)
            G.add_at(x, 0.5 * spit, tt, circular=False)
        for tt in np.arange(4.6, 5.9, 0.035):
            click = G._da_hit(rng, [rng.uniform(2600, 3200)], [0.0025], [1], noise_ms=1.5)
            G.add_at(x, 0.3 * G.norm(click), tt + rng.uniform(-0.004, 0.004), circular=False)
        spray = G._da_band(rng, n, 2500, 9000)
        spray_env = np.interp(t, [0, 0.1, 4.5, 4.7, 5.6, 6.0], [0, 0.12, 0.12, 0.35, 0.3, 0])
        x = x + spray * spray_env * (1 + 0.3 * np.sin(2 * np.pi * 4 * t))
        x = G._da_far(x, rng, 9000, hp=350, air=0.05, room=0.2)
        G.save_ogg(x[:n], G.SND / "level9" / f"sprinkler{i}.ogg", peak=0.5, fade_in=0.002, fade_out=0.08)


TEXTURES = [tex_l9_car, tex_l9_yard_props, tex_l9_swing, model_l9_car, model_l9_props, model_mailbox_flag]
SOUNDS = [snd_level9_ambience, snd_level9_dog_chorus, snd_level9_swing_creak, snd_level9_sprinkler]


def run_threaded(fns) -> None:
    """Runs the generators on a thread with a big stack and re-raises any error here.

    libvorbis needs a deep C stack for long loops: on Windows the main thread's 1 MB overflows
    writing the 24 s night bed, so the work runs on a 64 MB thread (as level4.py does).
    gen_assets.py's snd_level9_ambience calls this too.
    """
    errors = []

    def run() -> None:
        try:
            for fn in fns:
                fn()
                print("ok", fn.__name__, flush=True)
        except BaseException as e:  # noqa: BLE001  (re-raised on the calling thread below)
            errors.append(e)

    old = threading.stack_size(64 * 1024 * 1024)
    try:
        worker = threading.Thread(target=run)
        worker.start()
        worker.join()
    finally:
        threading.stack_size(old)
    if errors:
        raise errors[0]


def main() -> None:
    filters = sys.argv[1:]
    run_threaded([fn for fn in TEXTURES + SOUNDS if not filters or any(f in fn.__name__ for f in filters)])


if __name__ == "__main__":
    main()
