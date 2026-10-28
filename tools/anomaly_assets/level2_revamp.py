"""Level 2 "Pipe Dreams", the linear revamp: prop textures, block models and blockstates.

Along the corridor run several kinds of pipe, each a hand-made multi-element model (rusted steel
with bolted flanges, copper with green patina at the joints, off-white insulated mains with metal
bands and torn patches, painted service pipes with colour bands and stencilled labels, conduit),
plus the fittings, props and room furniture of registry/Level2Blocks. The materials themselves
(concrete, pipe metals, paint, grating) are drawn in anomaly_assets/level2_materials.py.

Run with `python tools/gen_assets.py level2_revamp level2_materials` (textures, models,
blockstates), then `python tools/gen_assets.py pale_duct pipe_valve floor_grate bulkhead` for the
older Level 2 blocks that gen_assets still owns by name (they call into this file). Every
texture has its own fixed seed (2300-2499), so re-running gives identical files.

Pipe models: the pipe runs along texture u (one block = the full 128 px), so every texture here
that wraps a pipe tiles along u; each face of a pipe takes its own slice of v, so the faces of
one pipe don't show the same marks. All model rotations are 0 or +-45 degrees (the only legal
ones besides +-22.5).
"""
from __future__ import annotations

import math

import numpy as np

import gen_assets as G
from anomaly_assets import level39_props as P
from anomaly_assets.level2_materials import (RUST, SHINE, SMALL, _along, _across, _fill, _painted_metal, _rivets,
                                             _round, _save, _spray, _stencil_text, _wear, band, cladding, floor)

S = G.S


def _blk(name: str):
    return G.TEX / "block" / f"{name}.png"


def _ref(name: str) -> str:
    return f"backrooms:block/{name}"


# =============================================================================
# Fittings, props and furniture textures
# =============================================================================

def hazard(rng) -> np.ndarray:
    """Yellow and black diagonal hazard stripes (45 degrees, 16 px each) on steel: scuffed,
    the paint worn through to dark steel along the edges, grime over it."""
    X, Y = G.grid()
    stripe = (np.floor((X + Y) / 22.6) % 2).astype(float)
    stripe = G.blur(stripe, 0.4)
    img = G.lerp(_fill((206, 164, 34)), _fill((30, 28, 26)), stripe[..., None])
    img = G.add_lum(img, 4 * G.fnoise(rng, beta=2.0))
    chips = 1 - _wear(rng, 0.32, 1.2)
    img = G.over(img, np.array([84, 80, 76]), chips * 0.85)
    return G.over(img, np.array([66, 58, 46]), G.smoothstep(-0.2, 1.6, G.fnoise(rng, beta=2.2)) * 0.3)


FRAME_GREEN = (86, 98, 88)     # the bulkhead rings' old machinery green-grey


def frame_plate(rng, band_side=None, gusset=False) -> np.ndarray:
    """A face of the bulkhead ring: riveted green-grey plate, weld seams, rust weeping from the
    rivets; band_side ("right" / "bottom") paints a hazard band along the opening's edge."""
    X, Y = G.grid()
    img = _painted_metal(rng, FRAME_GREEN, wear=0.18, grime=0.3)
    rivets = [(x, y) for x in (10, 64, 118) for y in (10, 64, 118)]
    img = _rivets(img, rivets, 2.6)
    for x, y in rivets:
        run = np.exp(-((X - x) / 1.4) ** 2) * (Y > y + 2) * np.clip(1 - (Y - y) / 30, 0, 1)
        img = G.over(img, np.array(RUST), run * 0.4)
    if gusset:
        tri = G.supersample((S, S), lambda x, y: (x > 30) & (y < 98) & (x - y > 30))
        img = G.add_lum(img, 10 * tri - 18 * np.clip(G.blur(tri, 1.2) - tri, 0, 1))
        img = _rivets(img, [(92, 30), (104, 50), (104, 18), (76, 18), (104, 76)], 2.2)
    if band_side:
        band = (X > S - 34) if band_side == "right" else (Y > S - 34)
        hz = hazard(G.rng_for(2399))
        img = np.where(band[..., None], hz, img)
        edge = np.abs(X - (S - 34)) if band_side == "right" else np.abs(Y - (S - 34))
        img = G.add_lum(img, -24 * np.exp(-(edge / 1.0) ** 2))
    return img


def wheel_paint(rng, colour) -> np.ndarray:
    """Cast-iron hand wheel paint (red or grey): chipped at the rim, hand-polished patches."""
    base = (150, 34, 28) if colour == "red" else (110, 112, 112)
    img = _painted_metal(rng, base, wear=0.3, grime=0.2, scratches=6)
    polish = G.smoothstep(1.0, 2.0, _along(rng, 1.4, 4))
    return G.over(img, np.array([96, 94, 92]), polish * 0.35)


def gauge_dial(rng) -> np.ndarray:
    """A pressure gauge face (cutout round): chrome bezel, yellowed white dial, black ticks and
    numbers 0-16, a red zone at the top end, the needle near the red, a fogged glass sheen."""
    X, Y = G.grid()
    c = 64
    r = np.hypot(X - c, Y - c)
    ang = np.degrees(np.arctan2(X - c, -(Y - c)))      # 0 = up, clockwise
    img = _fill((222, 214, 190))
    img = G.add_lum(img, 6 * G.fnoise(rng, beta=2.0))
    img = G.over(img, np.array([190, 170, 120]), G.smoothstep(30, 60, r) * 0.25)   # yellowed edge
    sweep = (ang > -135) & (ang < 135)
    for k in range(0, 41):
        a = -135 + 270 * k / 40
        major = k % 5 == 0
        m = (np.abs(((ang - a + 180) % 360) - 180) < (1.6 if major else 0.8)) & (r > (40 if major else 44)) & (r < 50)
        img = G.over(img, np.array([24, 22, 20]), m.astype(float))
    red = sweep & (ang > 85) & (ang < 135) & (r > 44) & (r < 50)
    img = G.over(img, np.array([180, 30, 26]), red.astype(float))
    for k, num in enumerate(("0", "4", "8", "12", "16")):
        a = np.radians(-135 + 270 * k / 4)
        tx, ty = c + 31 * np.sin(a), c - 31 * np.cos(a)
        img = G.over(img, np.array([24, 22, 20]), G._text(num, tx, ty, 11))
    img = G.over(img, np.array([24, 22, 20]), G._text("kPa x10", 64, 102, 8))
    needle_a = np.radians(98)
    nx, ny = np.sin(needle_a), -np.cos(needle_a)
    along = (X - c) * nx + (Y - c) * ny
    across = np.abs((X - c) * ny - (Y - c) * nx)
    needle = (along > -8) & (along < 46) & (across < 1.6 - along * 0.02)
    img = G.over(img, np.array([150, 24, 20]), G.blur(needle.astype(float), 0.4))
    img = G.over(img, np.array([30, 30, 30]), G.smoothstep(5, 4, r))
    img = G.add_lum(img, 26 * G.smoothstep(40, 0, np.hypot(X - 46, Y - 40)) * (r < 54))   # glass glare
    bezel = (r > 54) & (r < 63)
    chrome = 150 + 60 * np.cos(np.radians(ang - 315))
    img = np.where(bezel[..., None], np.stack([chrome, chrome, chrome * 1.02], -1) + 6 * G.fnoise(rng, beta=1.0)[..., None], img)
    img = G.add_lum(img, -30 * G.smoothstep(50, 54, r) * (r < 54))
    out = np.zeros((S, S, 4))
    out[..., :3] = img
    out[..., 3] = np.where(r < 63, 255, 0)
    return out


CABLE_COLOURS = ((34, 34, 36), (110, 112, 114), (176, 92, 34), (40, 40, 44), (64, 78, 96), (130, 34, 30), (30, 30, 32), (180, 176, 164))


def cables(rng) -> np.ndarray:
    """Cable sheaths, one 16 px row per cable (black, grey, orange, black, blue-grey, red, black,
    white): a soft sheen along each, printed marking text dashes, dust on top."""
    X, Y = G.grid()
    img = np.zeros((S, S, 3))
    for k, col in enumerate(CABLE_COLOURS):
        row = (Y >= 16 * k) & (Y < 16 * k + 16)
        v = (Y - 16 * k) / 16
        shade = 0.75 + 0.35 * np.sin(np.pi * v) + 0.25 * np.exp(-((v - 0.35) / 0.12) ** 2)
        c = np.array(col, float) * shade[..., None]
        img = np.where(row[..., None], c, img)
    marks = (np.abs(G.wrapd(X - 20, 48)) < 7) & (np.abs(G.wrapd(Y - 8, 16)) < 1.0)
    img = G.add_lum(img, 22 * marks * (img.mean(axis=2) < 80) - 10 * marks * (img.mean(axis=2) >= 80))
    img = G.over(img, np.array([110, 104, 94]), G.smoothstep(0.4, 1.6, G.fnoise(rng, beta=2.0)) * 0.25)
    return G.add_lum(img, rng.normal(0, 1.5, (S, S)))


def enclosure(rng, label=None, sub=None) -> np.ndarray:
    """Grey electrical enclosure steel; optional yellow-and-black label plate with lettering."""
    img = _painted_metal(rng, (120, 122, 118), wear=0.12, grime=0.28)
    if label:
        plate = G.box((S, S), 28, 14, 100, 40)
        img = G.over(img, np.array([196, 160, 40]), plate)
        img = G.over(img, np.array([24, 22, 20]), G._text(label, 64, 23, 13, maxw=66) * plate)
        if sub:
            img = G.over(img, np.array([24, 22, 20]), G._text(sub, 64, 34, 8, maxw=66) * plate)
        img = _rivets(img, [(32, 18), (96, 18), (32, 36), (96, 36)], 1.4, (90, 88, 84))
    return img


def _bolt_sign(img, cx, cy, size=26) -> np.ndarray:
    """A small yellow electrical warning triangle with a black lightning bolt."""
    X, Y = G.grid()
    h = size
    tri = G.supersample((S, S), lambda x, y: (y < cy + h / 2) & (np.abs(x - cx) < (y - (cy - h / 2)) * 0.6))
    inner = G.supersample((S, S), lambda x, y: (y < cy + h / 2 - 3) & (np.abs(x - cx) < (y - (cy - h / 2) - 4) * 0.6))
    img = G.over(img, np.array([24, 22, 20]), tri)
    img = G.over(img, np.array([214, 172, 36]), inner)
    bolt = G.pil_mask(lambda d, k: d.polygon([((cx + 2) * k, (cy - 6) * k), ((cx - 4) * k, (cy + 3) * k), (cx * k, (cy + 3) * k),
                                              ((cx - 2) * k, (cy + 11) * k), ((cx + 5) * k, (cy + 1) * k), ((cx + 1) * k, (cy + 1) * k)], fill=255))
    return G.over(img, np.array([24, 22, 20]), bolt)


def breaker_door(rng) -> np.ndarray:
    """The breaker panel's door, 128 x 256 (top half above, bottom half below): recessed door
    panel, piano hinge on the left, a key handle, louvres low down, the label and warning."""
    rng2 = G.rng_for(2421)
    img = np.concatenate([enclosure(rng), enclosure(rng2)], axis=0)
    H = 2 * S
    yy, xx = np.mgrid[0:H, 0:S] + 0.5
    door = G.box((H, S), 10, 10, S - 10, H - 10)
    img = G.add_lum(img, -20 * np.clip(G.box((H, S), 8, 8, S - 8, H - 8) - door, 0, 1))      # the gap round the door
    img = G.add_lum(img, 10 * np.exp(-((xx - 11) / 0.8) ** 2) * door + 10 * np.exp(-((yy - 11) / 0.8) ** 2) * door)
    hinge = (xx > 3) & (xx < 9) & (yy > 14) & (yy < H - 14)
    img = G.add_lum(img, (14 * np.sin(yy * 1.2) - 6) * hinge)
    lab = (xx > 30) & (xx < 98) & (yy > 26) & (yy < 58)
    img = np.where(lab[..., None], 215, img)
    t = np.zeros((H, S))
    t[:S] = G._text("PANEL 2B", 64, 36, 14, maxw=62)
    t[:S] = np.maximum(t[:S], G._text("480 V  3 PH", 64, 50, 8, maxw=62))
    img = G.over(img, np.array([24, 22, 20]), t * lab)
    warn = np.zeros((H, S, 3)) + img
    warn[:S] = _bolt_sign(img[:S].copy(), 64, 92, 30)
    img = warn
    handle = (xx > 104) & (xx < 112) & (yy > 112) & (yy < 150)
    img = np.where(handle[..., None], np.array([46, 46, 48]) + 30 * np.exp(-((xx - 106) / 1.5) ** 2)[..., None], img)
    img = G.add_lum(img, -20 * np.exp(-((xx - 113.5) / 1.0) ** 2) * (yy > 112) * (yy < 151))
    for k in range(7):
        y0 = 176 + 8 * k
        lv = (xx > 28) & (xx < 100) & (yy > y0) & (yy < y0 + 4)
        img = G.add_lum(img, -34 * lv + 14 * ((xx > 28) & (xx < 100) & (yy > y0 + 4) & (yy < y0 + 5.5)))
    drip = np.exp(-((xx - 70) / 2.0) ** 2) * (yy > 150) * np.clip(1 - (yy - 150) / 90, 0, 1)
    return G.over(img, np.array(RUST), drip * 0.25)


def cardboard(rng, top=False) -> np.ndarray:
    """A cardboard carton: kraft brown with flute ripples, brown parcel tape over the flap seam,
    a printed arrow-up and a handwritten marker label (side), dust and a water stain."""
    X, Y = G.grid()
    img = G.add_lum(_fill((150, 112, 72)), 5 * G.fnoise(rng, beta=2.0) + 1.5 * np.sin(X * 1.6))
    tape = (np.abs(X - 64) < 14) if top else (Y < 18)
    img = G.over(img, np.array([168, 128, 76]), tape * 0.85)
    img = G.add_lum(img, 18 * np.exp(-((G.wrapd(X - 50 if top else Y - 4, S)) / 3) ** 2) * tape)
    if top:
        img = G.add_lum(img, -26 * np.exp(-((X - 64) / 0.7) ** 2) * (np.abs(X - 64) < 1.5))
    else:
        img = G.marker(img, rng, [[(24, 52), (46, 50), (70, 53), (96, 51)], [(30, 70), (40, 64), (52, 72), (64, 66), (76, 72)]], 3.0, (34, 30, 28))
        arrow = G.supersample((S, S), lambda x, y: ((np.abs(x - 110) < 2) & (y > 92) & (y < 114)) | ((y > 88) & (y < 98) & (np.abs(x - 110) < (y - 88) * 0.7)))
        img = G.over(img, np.array([40, 34, 30]), arrow * 0.8)
    img = G.mark_stain(img, rng, (110, 80, 50), (14, 12), 0.3)
    return G.over(img, np.array([170, 160, 140]), G.smoothstep(0.2, 1.6, G.fnoise(rng, beta=2.2)) * 0.2)


def wood_top(rng) -> np.ndarray:
    """A workbench's oily hardwood top: strips along u, dark oil soak, saw cuts and dents."""
    X, Y = G.grid()
    strip = np.floor(Y / 12)
    img = G.add_lum(_fill((112, 78, 48)), (np.sin(strip * 7.13) * 10), (1.0, 0.85, 0.7))
    img = G.add_lum(img, 7 * G.fnoise(rng, beta=1.2, aniso=(1, 14), kmin=3), (1.0, 0.85, 0.7))
    img = G.add_lum(img, -18 * np.exp(-((G.wrapd(Y, 12)) / 0.6) ** 2))
    oil = G.smoothstep(-0.4, 1.2, G.blur(G.fnoise(rng, beta=2.4), 1.0))
    img = G.over(img, np.array([52, 38, 28]), oil * 0.55)
    for _ in range(8):
        x0, y0, a = rng.uniform(0, S), rng.uniform(0, S), rng.uniform(0, np.pi)
        dist = np.abs((X - x0) * np.sin(a) - (Y - y0) * np.cos(a))
        along = np.abs((X - x0) * np.cos(a) + (Y - y0) * np.sin(a))
        img = G.add_lum(img, -22 * np.exp(-(dist / 0.5) ** 2) * (along < rng.uniform(4, 14)))
    return img


def shelf_items(rng) -> dict:
    """Small textures for the shelf props: toolbox, paint tin, jerry can."""
    X, Y = G.grid()
    out = {}
    tb = _painted_metal(rng, (158, 40, 30), wear=0.25, grime=0.3)
    tb = G.add_lum(tb, -28 * np.exp(-((Y - 40) / 1.0) ** 2) + 10 * np.exp(-((Y - 42) / 1.0) ** 2))   # lid seam
    tb = G.over(tb, np.array([170, 170, 168]), G.box((S, S), 54, 32, 74, 50))                         # latch
    out["l2_toolbox"] = tb
    tin = G._galvanised(rng, (160, 160, 156))
    label = (Y > 36) & (Y < 104)
    tin = np.where(label[..., None], G.add_lum(_fill((44, 70, 128)), 4 * G.fnoise(rng, beta=1.4)), tin)
    tin = G.over(tin, np.array([230, 226, 214]), G._text("PRIMER", 64, 62, 18) * label)
    tin = G.over(tin, np.array([230, 226, 214]), G._text("5 L", 64, 84, 12) * label)
    drips = np.clip(_across(rng, 1.2, 10), 0, None) * (Y < 70)
    tin = G.over(tin, np.array([128, 130, 136]), G.smoothstep(0.8, 1.6, drips) * 0.8)
    out["l2_tin"] = tin
    jc = _painted_metal(rng, (78, 88, 52), wear=0.2, grime=0.3)
    for sgn in (1, -1):
        d = np.abs((X - 64) - sgn * (Y - 64))
        jc = G.add_lum(jc, (12 * np.exp(-((d - 3) / 1.2) ** 2) - 14 * np.exp(-((d + 0) / 1.2) ** 2))
                       * (np.abs(X - 64) < 44) * (np.abs(Y - 64) < 44))
    out["l2_jerrycan"] = jc
    return out


def motor_green(rng) -> np.ndarray:
    """Machinery enamel, dark green, oil-stained, with an aluminium nameplate in one corner."""
    img = _painted_metal(rng, (60, 86, 68), wear=0.16, grime=0.35)
    plate = G.box((S, S), 18, 18, 66, 46)
    img = G.over(img, np.array([176, 178, 172]), plate)
    for k, y in enumerate((24, 30, 36, 41)):
        img = G.add_lum(img, -60 * G.box((S, S), 22, y, 22 + (40 if k == 0 else 26 + 6 * k), y + 2) * plate)
    return img


def guard(rng) -> np.ndarray:
    """Safety-orange perforated coupling guard: round holes in rows, chipped paint."""
    X, Y = G.grid()
    img = _painted_metal(rng, (198, 124, 30), wear=0.2, grime=0.3)
    holes = np.hypot(G.wrapd(X - 4, 10), G.wrapd(Y - 4 + 5 * (np.floor(X / 10) % 2), 10)) < 3.2
    return np.where(holes[..., None], np.array([30, 26, 22]), img)


def starter(rng) -> np.ndarray:
    """The pump's starter cabinet front: grey door, a green START and red STOP button, an amber
    running lamp (dark), a label and an amp meter."""
    X, Y = G.grid()
    img = enclosure(rng, "P-2 PUMP", "STARTER")
    for cx, col, txt in ((44, (40, 120, 52), "START"), (84, (170, 34, 30), "STOP")):
        r = np.hypot(X - cx, Y - 70)
        img = G.add_lum(img, -30 * G.smoothstep(10, 8, r))
        img = G.over(img, np.array(col), G.smoothstep(7.5, 6.5, r))
        img = G.add_lum(img, 30 * G.smoothstep(4, 0, np.hypot(X - cx + 2, Y - 72)))
        img = G.over(img, np.array([24, 22, 20]), G._text(txt, cx, 86, 8))
    r = np.hypot(X - 64, Y - 106)
    img = G.over(img, np.array([120, 76, 20]), G.smoothstep(6, 5, r))
    return G.add_lum(img, 40 * G.smoothstep(3, 0, np.hypot(X - 62, Y - 104)))


def fan_grille(rng) -> np.ndarray:
    """Duct fan front (cutout): galvanised square plate with a round opening, a wire guard of
    rings and spokes across it."""
    X, Y = G.grid()
    r = np.hypot(X - 64, Y - 64)
    ang = np.arctan2(Y - 64, X - 64)
    plate = G._galvanised(rng, (122, 124, 124))
    plate = _rivets(plate, [(10, 10), (118, 10), (10, 118), (118, 118)], 3.0)
    rings = (np.abs(G.wrapd(r - 4, 9)) < 1.1) & (r < 58)
    spokes = (np.abs(np.sin(2 * ang)) * r < 1.3) | (np.abs(np.cos(2 * ang)) * r < 1.3)
    wire = rings | (spokes & (r < 58)) | (r < 7)
    img = np.where((r >= 58)[..., None], plate, np.array([70, 72, 74]) + 30 * np.cos(ang - 2.4)[..., None])
    img = G.add_lum(img, -30 * G.smoothstep(64, 58, r) * (r >= 58))
    out = np.zeros((S, S, 4))
    out[..., :3] = img
    out[..., 3] = np.where((r >= 58) | wire, 255, 0)
    return out


def fan_blades(rng) -> np.ndarray:
    """Five swept fan blades round a hub (cutout), grey-black with dust on the leading edges."""
    X, Y = G.grid()
    r = np.hypot(X - 64, Y - 64)
    ang = np.arctan2(Y - 64, X - 64)
    phase = (ang * 5 / (2 * np.pi) + r / 60) % 1
    blade = (phase < 0.42) & (r < 58) & (r > 9)
    img = G.add_lum(_fill((58, 60, 62)), 12 * np.cos(phase * 7) + 4 * G.fnoise(rng, beta=1.4))
    img = G.over(img, np.array([140, 132, 118]), G.smoothstep(0.3, 0.42, phase) * 0.6)
    img = np.where((r <= 10)[..., None], np.array([40, 40, 42]) + 20 * G.smoothstep(10, 0, r)[..., None], img)
    out = np.zeros((S, S, 4))
    out[..., :3] = img
    out[..., 3] = np.where(blade | (r <= 10), 255, 0)
    return out


def duct_galv(rng) -> np.ndarray:
    """Galvanised duct sheet: spangle, a standing seam along u, a row of rivets, a little rust."""
    X, Y = G.grid()
    img = G._galvanised(rng, (132, 134, 134))
    img = G.add_lum(img, 18 * np.exp(-((Y - 8) / 1.2) ** 2) - 14 * np.exp(-((Y - 11) / 1.0) ** 2))
    img = _rivets(img, [(x, 20) for x in range(8, S, 24)], 1.6)
    return G.over(img, np.array(RUST), G.smoothstep(1.4, 2.2, G.fnoise(rng, beta=1.6)) * 0.4)


def _door_sheet(rng, colour) -> np.ndarray:
    """A steel door, 128 wide x 256 tall (cut into top and bottom): door leaf in a frame,
    rust creeping up from the bottom, scuffs, a kick plate."""
    H = 2 * S
    img = np.concatenate([_painted_metal(rng, colour, 0.16, 0.3), _painted_metal(G.rng_for(2490), colour, 0.2, 0.4)], 0)
    yy, xx = np.mgrid[0:H, 0:S] + 0.5
    edge = np.minimum(np.minimum(xx, S - xx), np.minimum(yy, H - yy))
    img = G.add_lum(img, -26 * (edge < 3) + 10 * ((edge >= 3) & (edge < 4.5)))
    rust = G.smoothstep(H - 70, H, yy) * G.smoothstep(-0.4, 1.0, np.concatenate([G.fnoise(rng, beta=1.6), G.fnoise(rng, beta=1.6)], 0))
    img = G.over(img, np.array(RUST), rust * 0.6)
    kick = (yy > H - 34) & (yy < H - 6) & (xx > 8) & (xx < S - 8)
    img = np.where(kick[..., None], G.over(img, np.array([140, 140, 136]), 0.55 * kick), img)
    img = G.add_lum(img, 12 * (np.abs(yy - (H - 34)) < 1.0) * (xx > 8) * (xx < S - 8))
    return img


def steel_door(rng) -> np.ndarray:
    """Maintenance door: grey-green steel, a small wired-glass window, a lever handle, and
    MAINTENANCE stencilled across it."""
    H = 2 * S
    img = _door_sheet(rng, (96, 104, 98))
    yy, xx = np.mgrid[0:H, 0:S] + 0.5
    win = (xx > 40) & (xx < 88) & (yy > 40) & (yy < 104)
    glass = np.array([22, 26, 28]) + 18 * np.exp(-((xx - yy + 50) / 8) ** 2)[..., None]
    wires = ((np.abs(G.wrapd(xx - yy, 12)) < 0.6) | (np.abs(G.wrapd(xx + yy, 12)) < 0.6))
    glass = np.where(wires[..., None], np.array([90, 92, 90]), glass)
    img = np.where(win[..., None], glass, img)
    frame = ((xx > 36) & (xx < 92) & (yy > 36) & (yy < 108)) & ~win
    img = np.where(frame[..., None], np.array([60, 62, 60]), img)
    t = np.zeros((H, S))
    t[S:] = _stencil_text("MAINTENANCE", 60, 56, 17, maxw=96)
    img = G.over(img, np.array([226, 222, 206]), t * 0.85)
    lever = (xx > 98) & (xx < 120) & (yy > 146) & (yy < 154)
    img = np.where(lever[..., None], np.array([150, 148, 140]) + 20 * np.exp(-((yy - 148) / 1.5) ** 2)[..., None], img)
    rose = np.hypot(xx - 102, yy - 150) < 6
    return np.where(rose[..., None], np.array([120, 118, 112]), img)


def exit_bulkhead(rng) -> np.ndarray:
    """The EXIT bulkhead's door leaf: heavy riveted steel, a raised rounded panel, two latch
    dogs, EXIT stencilled big in green with an arrow, EMERGENCY EXIT above, oil and rust."""
    H = 2 * S
    img = _door_sheet(rng, (84, 92, 88))
    yy, xx = np.mgrid[0:H, 0:S] + 0.5
    px, py = np.abs(xx - 64) - 40, np.abs(yy - 128) - 100
    panel = np.hypot(np.maximum(px, 0), np.maximum(py, 0)) + np.minimum(np.maximum(px, py), 0) - 10
    img = G.add_lum(img, 14 * np.exp(-((panel + 1.5) / 1.2) ** 2) - 18 * np.exp(-((panel - 1.5) / 1.2) ** 2))
    riv = np.zeros((H, S))
    for x in range(12, S, 17):
        for y in (10, H - 10):
            riv = np.maximum(riv, G.smoothstep(2.6, 1.6, np.hypot(xx - x, yy - y)))
    img = G.add_lum(img, 22 * riv)
    word = np.zeros((H, S))
    word[:S] = _stencil_text("EXIT", 62, 92, 50, maxw=96)
    word[:S] = np.maximum(word[:S], _stencil_text("EMERGENCY", 64, 46, 15, maxw=90))
    green = np.array([40, 150, 74])
    img = G.over(img, green, word * 0.92)
    arrow = ((np.abs(yy - 152) < 7) & (xx > 30) & (xx < 80)) | ((xx >= 80) & (xx < 100) & (np.abs(yy - 152) < (100 - xx) * 0.9))
    img = G.over(img, green, arrow * 0.9)
    for y0 in (110, 182):
        dog = (xx > 100) & (xx < 122) & (np.abs(yy - y0) < 4)
        img = np.where(dog[..., None], np.array([46, 46, 48]) + 30 * np.exp(-((yy - y0 + 2) / 1.5) ** 2)[..., None], img)
        img = G.add_lum(img, -24 * ((xx > 100) & (xx < 123) & (np.abs(yy - y0 - 5) < 1.2)))
    return img


STENCILS = {
    "sec_a03": ("2-A", "03"), "sec_a11": ("2-A", "11"), "sec_b14": ("2-B", "14"), "sec_b27": ("2-B", "27"),
    "sec_c06": ("2-C", "06"), "sec_c19": ("2-C", "19"), "sec_d08": ("2-D", "08"), "sec_d32": ("2-D", "32"),
}


def stencil(rng, name) -> np.ndarray:
    """One wall stencil (cutout decal over the wall): spray paint with overspray halo, worn and
    dirtied where it is old."""
    X, Y = G.grid()
    ink = np.zeros((S, S))
    col = np.zeros((S, S, 3)) + np.array([22, 20, 18])
    if name in STENCILS:
        a, b = STENCILS[name]
        ink = np.maximum(_stencil_text(a, 64, 40, 40, maxw=100), _stencil_text(b, 64, 88, 48, maxw=100))
        ink = np.maximum(ink, G.box((S, S), 22, 60, 106, 64))
    elif name == "danger_hot":
        panel = G.box((S, S), 6, 14, 122, 114)
        tri_o = G.supersample((S, S), lambda x, y: (y < 56) & (np.abs(x - 64) < (y - 20) * 0.62))
        tri_i = G.supersample((S, S), lambda x, y: (y < 52) & (np.abs(x - 64) < (y - 27) * 0.62))
        txt = np.maximum(_stencil_text("DANGER", 64, 72, 22, maxw=108), _stencil_text("HOT SURFACE", 64, 96, 16, maxw=108))
        mark = np.clip(tri_o - tri_i, 0, 1) + G.box((S, S), 62, 34, 66, 45) + G.box((S, S), 62, 47, 66, 50)
        dark = np.clip(txt + mark, 0, 1)
        ink = np.clip(panel, 0, 1)
        col = G.lerp(np.array([206, 166, 36]) * np.ones((S, S, 3)), np.array([22, 20, 18]), dark[..., None])
    elif name == "no_smoking":
        r = np.hypot(X - 64, Y - 48)
        ring = (r > 30) & (r < 38)
        slash = (np.abs((X - 64) + (Y - 48)) < 5) & (r < 34)
        cig = G.box((S, S), 40, 44, 84, 52)
        red = np.clip(ring + slash, 0, 1).astype(float)
        ink = np.clip(red + cig + _stencil_text("NO SMOKING", 64, 104, 18, maxw=112), 0, 1)
        col = np.where(red[..., None] > 0.5, np.array([170, 34, 28]), np.array([22, 20, 18]))
    elif name in ("arrow_left", "arrow_right"):
        flip = name == "arrow_left"
        xs = (S - X) if flip else X
        ink = (((np.abs(Y - 64) < 12) & (xs > 14) & (xs < 76)) | ((xs >= 76) & (xs < 114) & (np.abs(Y - 64) < (114 - xs) * 1.05))).astype(float)
        col = col * 0 + np.array([206, 170, 40])
    elif name in ("steam", "return", "h2o"):
        word = {"steam": "STEAM", "return": "RETURN", "h2o": "H2O"}[name]
        ink = np.maximum(_stencil_text(word, 64, 52, 40, maxw=110),
                         (((np.abs(Y - 92) < 5) & (X > 24) & (X < 88)) | ((X >= 88) & (X < 106) & (np.abs(Y - 92) < (106 - X) * 0.75))).astype(float))
        col = col * 0 + np.array([226, 222, 210])
    elif name == "exit":
        ink = np.maximum(_stencil_text("EXIT", 54, 60, 52, maxw=80),
                         (((np.abs(Y - 60) < 6) & (X > 92) & (X < 108)) | ((X >= 108) & (X < 124) & (np.abs(Y - 60) < (124 - X) * 1.0))).astype(float))
        col = col * 0 + np.array([44, 156, 80])
    a = _spray(rng, ink) if name not in ("danger_hot",) else ink * 0.95
    a = a * np.clip(0.9 - 0.4 * G.smoothstep(1.2, 2.2, G.fnoise(rng, beta=1.4)), 0, 1)    # worn
    col = G.over(col, np.array([90, 84, 74]), G.smoothstep(0.2, 1.6, G.fnoise(rng, beta=2.0)) * 0.25)
    out = np.zeros((S, S, 4))
    out[..., :3] = col
    out[..., 3] = np.where(a > 0.42, 255, 0)
    return out


def bulb(rng) -> np.ndarray:
    """Warm frosted bulb glass (drawn full-bright in game): amber-white, hottest in the middle
    where the filament glows through, a little soot at the top."""
    X, Y = G.grid()
    r = np.hypot(X - 64, Y - 60) / 64
    img = G.lerp(np.array([255, 236, 196]), np.array([236, 186, 118]), np.clip(r, 0, 1)[..., None] ** 1.3)
    img = G.add_lum(img, 14 * G.smoothstep(0.25, 0.0, r), (1.0, 0.95, 0.85))
    img = G.add_lum(img, G.fnoise(rng, beta=2.2) * 2.0 + rng.normal(0, 0.8, (S, S)))
    return G.over(img, np.array([150, 120, 90]), G.smoothstep(30, 0, Y) * 0.25)


def cage(rng) -> np.ndarray:
    """The lamp's cast cage and back plate: dark grey enamel, worn at the edges, dusty on top."""
    img = _painted_metal(rng, (52, 52, 54), wear=0.2, grime=0.2, scratches=2)
    return G.over(img, np.array([118, 112, 100]), G.smoothstep(0.6, 1.8, G.fnoise(rng, beta=2.0)) * 0.25)


def drain_grate(rng) -> np.ndarray:
    """A square cast-iron drain grate set in the corridor floor: slots over black, a rusty
    frame, grime round it in the concrete."""
    X, Y = G.grid()
    img = floor()
    ring = G.box((S, S), 10, 10, S - 10, S - 10) - G.box((S, S), 14, 14, S - 14, S - 14)
    img = G.add_lum(img, -30 * np.clip(G.box((S, S), 6, 6, S - 6, S - 6) - G.box((S, S), 10, 10, S - 10, S - 10), 0, 1))
    iron = G.add_lum(_fill((58, 54, 50)), 8 * G.fnoise(rng, beta=1.6))
    iron = G.over(iron, np.array([116, 64, 36]), G.smoothstep(0.4, 1.6, G.fnoise(rng, beta=1.8)) * 0.6)
    inner = G.box((S, S), 10, 10, S - 10, S - 10)
    img = G.lerp(img, iron, inner[..., None])
    slots = np.zeros((S, S))
    for y0 in range(20, S - 24, 12):
        slots = np.maximum(slots, G.box((S, S), 22, y0, S - 22, y0 + 6))
    depth = 1 - 0.6 * np.clip((X - 20) / (S - 40), 0, 1)
    img = G.over(img, np.array([6, 6, 7]) * depth[..., None] + 4, slots)
    img = G.add_lum(img, 18 * np.clip(ring, 0, 1) * (Y < 64))
    return G.over(img, np.array([40, 36, 30]), np.clip(G.box((S, S), 4, 4, S - 4, S - 4) - inner, 0, 1) * 0.3)


def tex_level2_revamp_props() -> None:
    _save(hazard(G.rng_for(2400)), "l2_hazard")
    _save(frame_plate(G.rng_for(2401)), "l2_frame_plate")
    _save(frame_plate(G.rng_for(2402), band_side="right"), "l2_frame_side")
    _save(frame_plate(G.rng_for(2403), band_side="bottom"), "l2_frame_top")
    _save(frame_plate(G.rng_for(2404), gusset=True), "l2_frame_corner")
    _save(wheel_paint(G.rng_for(2405), "grey"), "l2_wheel_grey")
    _save(gauge_dial(G.rng_for(2406)), "l2_gauge_dial")
    _save(cables(G.rng_for(2407)), "l2_cables")
    _save(enclosure(G.rng_for(2408)), "l2_enclosure")
    _save(enclosure(G.rng_for(2409), "J.B. 2-14", "LIGHTING CCT 7"), "l2_jbox_front")
    door = breaker_door(G.rng_for(2410))
    _save(door[:S], "l2_breaker_top")
    _save(door[S:], "l2_breaker_bottom")
    _save(cardboard(G.rng_for(2411)), "l2_carton")
    _save(cardboard(G.rng_for(2412), top=True), "l2_carton_top")
    _save(wood_top(G.rng_for(2413)), "l2_bench_top")
    _save(_painted_metal(G.rng_for(2414), (58, 66, 78), 0.25, 0.35), "l2_bench_steel")
    _save(_painted_metal(G.rng_for(2415), (122, 128, 126), 0.12, 0.3), "l2_shelf_steel")
    _save(_painted_metal(G.rng_for(2416), (70, 92, 112), 0.25, 0.3), "l2_vise")
    for name, img in shelf_items(G.rng_for(2417)).items():
        _save(img, name)
    _save(motor_green(G.rng_for(2418)), "l2_motor")
    _save(guard(G.rng_for(2419)), "l2_guard")
    _save(_painted_metal(G.rng_for(2420), (140, 38, 32), 0.15, 0.3), "l2_tank")
    _save(starter(G.rng_for(2422)), "l2_starter")
    plinth = G.over(floor(), np.array([40, 36, 30]), G.smoothstep(0.3, 1.6, G.fnoise(G.rng_for(2423), beta=2.2)) * 0.4)
    _save(plinth, "l2_plinth")
    _save(fan_grille(G.rng_for(2424)), "l2_fan_grille", S)   # thin wires: they break up at 64
    _save(fan_blades(G.rng_for(2425)), "l2_fan_blades")
    _save(duct_galv(G.rng_for(2426)), "l2_duct")
    door = steel_door(G.rng_for(2427))
    _save(door[:S], "l2_steel_door_top", S)
    _save(door[S:], "l2_steel_door_bottom", S)
    door = exit_bulkhead(G.rng_for(2428))
    _save(door[:S], "l2_exit_bulkhead_top", S)
    _save(door[S:], "l2_exit_bulkhead_bottom", S)
    _save(stencil_sheet(), "l2_stencils", 4 * SMALL)


STENCIL_NAMES = (*STENCILS, "danger_hot", "no_smoking", "arrow_left", "arrow_right", "steam", "return", "h2o", "exit")


def stencil_sheet() -> np.ndarray:
    """All sixteen stencils on one 4 x 4 sheet (each drawn at 128 and shrunk into a 64 px cell);
    stencil k sits in column k % 4, row k // 4."""
    sheet = np.zeros((4 * S, 4 * S, 4))
    for k, name in enumerate(STENCIL_NAMES):
        r, c = divmod(k, 4)
        sheet[r * S:(r + 1) * S, c * S:(c + 1) * S] = stencil(G.rng_for(2440 + k), name)
    return sheet


# The older Level 2 blocks keep their texture files; gen_assets' tex_* of the same names call these.
def write_pale_duct() -> None:
    _save(_round(cladding(G.rng_for(2460)), SHINE["insulated"]), "pale_duct")


def write_pale_duct_flange() -> None:
    _save(_round(band(G.rng_for(2461)), 0.9), "pale_duct_flange")


def write_pipe_valve() -> None:
    _save(wheel_paint(G.rng_for(2462), "red"), "pipe_valve")


def write_floor_grate() -> None:
    _save(drain_grate(G.rng_for(2463)), "floor_grate", S)


def write_bulkhead_lamp() -> None:
    _save(bulb(G.rng_for(2464)), "bulkhead_lamp")


def write_bulkhead_cage() -> None:
    _save(cage(G.rng_for(2465)), "bulkhead_cage")


# =============================================================================
# Models: helpers
# =============================================================================

_bm, _bs, _im = P._bm, P._bs, P._im
FACES = ("north", "south", "east", "west", "up", "down")
FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}
TAN = math.tan(math.radians(22.5))


def _r(v):
    return [round(float(c), 4) for c in v]


def _box(f, t, faces, rot=None, shade=True):
    """One element. faces: {face: (texture, uv or None, uv rotation)}; uv None = auto (from the
    position, so overlapping coplanar faces show the same texels). rot = (origin, axis, angle).
    Faces on the block boundary of unturned boxes cull against a solid neighbour."""
    e = {"from": _r(f), "to": _r(t), "faces": {}}
    if rot is not None:
        e["rotation"] = {"origin": _r(rot[0]), "axis": rot[1], "angle": rot[2]}
    if not shade:
        e["shade"] = False
    edge = {"north": f[2] <= 0, "south": t[2] >= 16, "west": f[0] <= 0, "east": t[0] >= 16,
            "down": f[1] <= 0, "up": t[1] >= 16}
    for face, spec in faces.items():
        tex, uv, r = spec if len(spec) == 3 else (spec[0], spec[1], 0)
        fd = {"uv": _r(uv if uv is not None else P._auto_uv(face, f, t)), "texture": tex}
        if r:
            fd["rotation"] = r
        if rot is None and edge[face]:
            fd["cullface"] = face
        e["faces"][face] = fd
    return e


def _cube_faces(tex, skip=()):
    return {f: (tex, None) for f in FACES if f not in skip}


def _plain_box(f, t, tex, skip=(), rot=None):
    return _box(f, t, _cube_faces(tex, skip), rot)


# A pipe is built in its own frame: t runs along the pipe, (p, q) across it. _xyz maps that to
# model space for a pipe along x, y or z.
#   along x: x = t, y = p, z = q      along y: x = p, y = t, z = q      along z: x = q, y = p, z = t
def _xyz(axis, t, p, q):
    return {"x": (t, p, q), "y": (p, t, q), "z": (q, p, t)}[axis]


# Which model face is the +t/-t, +p/-p, +q/-q side of a pipe along each axis.
_FACE = {
    "x": {"+t": "east", "-t": "west", "+p": "up", "-p": "down", "+q": "south", "-q": "north"},
    "y": {"+t": "up", "-t": "down", "+p": "east", "-p": "west", "+q": "south", "-q": "north"},
    "z": {"+t": "south", "-t": "north", "+p": "up", "-p": "down", "+q": "east", "-q": "west"},
}


_NATURAL_U = {"north": (-1, 0, 0), "south": (1, 0, 0), "east": (0, 0, -1), "west": (0, 0, 1), "up": (1, 0, 0), "down": (1, 0, 0)}
_NATURAL_V = {"north": (0, -1, 0), "south": (0, -1, 0), "east": (0, -1, 0), "west": (0, -1, 0), "up": (0, 0, 1), "down": (0, 0, -1)}


def _turn(axis, angle, v):
    """Right-handed turn of vector v about a model axis (as an element rotation does)."""
    a = math.radians(angle or 0)
    c, s = math.cos(a), math.sin(a)
    x, y, z = v
    if axis == "x":
        return (x, y * c - z * s, y * s + z * c)
    if axis == "y":
        return (x * c + z * s, y, -x * s + z * c)
    return (x * c - y * s, x * s + y * c, z)


def _around(axis, n):
    """Where a face with outward normal n sits round a pipe, as a texture v (0..16) of the baked
    round shading: for a lying pipe 0 = the top, 8 = the side, 16 = the underside; for a standing
    pipe 4 = the side facing out of the wall, 16 = the back."""
    if axis == "y":
        return 4 + math.degrees(math.acos(max(-1.0, min(1.0, n[2])))) / 180 * 12
    return math.degrees(math.acos(max(-1.0, min(1.0, n[1])))) / 180 * 16


def _frame_box(axis, t0, t1, p0, p1, q0, q1, tex, rot_angle=None, centre=None, ends=True, vslot=0, long_tex=None,
               round_=False, cap=None):
    """A box in the pipe frame. Its four long sides take the texture along t (u = t), the two
    ends use auto UVs. rot_angle turns the box about the pipe's axis through centre=(p, q).

    round_: the box is a flat of a round pipe whose texture has the cylinder's shading baked in
    across v (see _round): each long side takes the band of v for the way it faces, running the
    same way round as its neighbours, and Minecraft's own flat face shading is switched off so
    the baked shading reads as one smooth curve. Otherwise each side gets its own slice of v
    (vslot picks where). cap: texture for the ends instead of tex."""
    a = _xyz(axis, t0, p0, q0)
    b = _xyz(axis, t1, p1, q1)
    f = [min(a[i], b[i]) for i in range(3)]
    t = [max(a[i], b[i]) for i in range(3)]
    names = _FACE[axis]
    faces = {}
    widths = {"+p": q1 - q0, "-p": q1 - q0, "+q": p1 - p0, "-q": p1 - p0}
    normals = {"+p": (1, 0), "-p": (-1, 0), "+q": (0, 1), "-q": (0, -1)}
    for k, side in enumerate(("+p", "-p", "+q", "-q")):
        face = names[side]
        # the texture's u must run along the pipe: faces whose own u runs across it are turned
        r = 90 if (axis == "y") or (axis == "z" and face in ("up", "down")) else 0
        if round_:
            n = _turn(axis, rot_angle, _xyz(axis, 0, *normals[side]))
            vc = _around(axis, n)
            hw = 2.0 if axis != "y" else 1.5
            v0, v1 = max(0.0, vc - hw), min(16.0, vc + hw)
            vdir = _NATURAL_V[face] if r == 0 else tuple(-c for c in _NATURAL_U[face])
            ahead = _turn(axis, rot_angle, tuple(nc + 0.2 * dc for nc, dc in zip(_xyz(axis, 0, *normals[side]), vdir)))
            norm = math.sqrt(sum(c * c for c in ahead))
            if _around(axis, tuple(c / norm for c in ahead)) < vc:
                v0, v1 = v1, v0
            uv = [t0, v0, t1, v1]
        else:
            w = min(widths[side], 16)
            v0 = ((vslot * 5 + k * 3.7) % max(16 - w, 0.001)) if w < 16 else 0
            uv = [t0, v0, t1, v0 + w]
        tx = (long_tex or {}).get(side, tex)
        faces[face] = (tx, uv, r)
    if ends:
        faces[names["+t"]] = (cap or tex, None, 0)
        faces[names["-t"]] = (cap or tex, None, 0)
    rot = None
    if rot_angle:
        origin = _xyz(axis, (t0 + t1) / 2, centre[0], centre[1])
        rot = (origin, axis, rot_angle)
    return _box(f, t, faces, rot, shade=not round_)


def _octagon(axis, t0, t1, pc, qc, d, tex, ends=True, vslot=0, flats=None, bars=4, cap=None):
    """A round pipe of diameter d (the octagon's flat-to-flat width) from t0 to t1: two crossed
    bars plus two more turned 45 degrees (the union of the four is exactly a regular octagon).
    flats = {"+q": texture, ...}: another texture on that one outer flat (a label, a tear).

    cap: a round pipe with the cylinder shading (see _frame_box round_) whose ends are capped
    with that flat-coloured texture on all four bars, so a run seen end-on shows a whole
    octagon (the overlapping end faces are one flat colour, so they can't flicker). Without a
    cap only the unturned bars have end faces (auto UVs, so where they overlap they match)."""
    if bars == 1:
        # thin pipes: one square bar; the baked shading makes it read round
        h = d / 2
        return [_frame_box(axis, t0, t1, pc - h, pc + h, qc - h, qc + h, tex, ends=ends, vslot=vslot,
                           long_tex=flats or None, round_=cap is not None, cap=cap)]
    s = d * TAN if bars == 4 else d * 0.6     # thin pipes: just a chunkier cross
    h, w = d / 2, s / 2
    flats = flats or {}
    rnd = cap is not None
    on_a = {k: v for k, v in flats.items() if k in ("+p", "-p")}    # bar A's outer flats face +-p
    on_b = {k: v for k, v in flats.items() if k in ("+q", "-q")}    # bar B's face +-q
    out = [_frame_box(axis, t0, t1, pc - h, pc + h, qc - w, qc + w, tex, ends=ends, vslot=vslot, long_tex=on_a,
                      round_=rnd, cap=cap),
           _frame_box(axis, t0, t1, pc - w, pc + w, qc - h, qc + h, tex, ends=ends, vslot=vslot + 1, long_tex=on_b,
                      round_=rnd, cap=cap)]
    if bars == 4:
        for k, ang in enumerate((45, -45)):
            out.append(_frame_box(axis, t0, t1, pc - h, pc + h, qc - w, qc + w, tex, ang, (pc, qc),
                                  ends=ends and rnd, vslot=vslot + 2 + k, round_=rnd, cap=cap))
    return out


# =============================================================================
# Pipes (l2_pipe, l2_flange, l2_pipe_elbow, l2_pipe_tee); see block/Level2PipeBlock
# =============================================================================

KINDS = ("steel", "copper", "insulated", "painted_red", "painted_blue", "painted_yellow", "painted_green", "conduit")
# size: (diameter, distance of the centre line from the surface the pipes sit against, spread)
# Racks keep clear gaps between their pipes (2.5 px small, 3 px medium) so the wall shows through.
SIZES = {"small": (3.0, 3.25, (2.5, 8, 13.5)), "medium": (5.0, 4.25, (4.0, 12.0)), "large": (10, 6.5, (8,))}
# The insulation jacket is a little fatter than the bare pipe it wraps (still inside the shape).
FATTER = {"insulated": 0.6}
# Slot "hung" (block Level2PipeBlock.Slot.HUNG): how far below the ceiling each size's centre line
# hangs (Level2PipeBlock.Size.hang), so the steel beams (BEAM_BOTTOM up) pass over the pipes.
# The beam is deep (7 px) and the pipes hang well clear of the slab, so from the lane you see the
# beams crossing over the pipes and a dark gap with the hanger rods above them. A hung large main
# reaches 1.5 px below its block (it hangs tight under the beams).
HANG = {"small": 10.0, "medium": 10.5, "large": 12.2}
BEAM_BOTTOM = 9.0


def _centres(size, slot):
    """(p, q) centre of each pipe for a slot of the canonical models: "wall" = against the -q side
    (north for pipes along x or y), "up" = against +p, "hung" = on rods below +p, "down" = against
    -p, "center" = one pipe."""
    d, off, spread = SIZES[size]
    if slot == "wall":
        return [(sp, off) for sp in spread]
    if slot == "up":
        return [(16 - off, sp) for sp in spread]
    if slot == "hung":
        return [(16 - HANG[size], sp) for sp in spread]
    if slot == "down":
        return [(off, sp) for sp in spread]
    return [(8, 8)]


def _ubolt(axis, tc, pc, qc, r, toward, base, tex="#clamp"):
    """A U-bolt round one pipe, its legs bolted to a strut at `base` (a p or q level): the bend of
    the U goes over the far side of the pipe. toward = which way the U reaches from its strut:
    "+q" (out of a wall), "+p" (up from a strut under the pipe), "-p" (down from above)."""
    w, k = 0.45, 0.5   # leg thickness, half the width of the bolt along the pipe
    els = []
    if toward == "+q":
        for s in (-1, 1):
            p0 = pc + s * (r + 0.1)
            els.append(_frame_box(axis, tc - k, tc + k, min(p0, p0 + s * w), max(p0, p0 + s * w), base, qc + r * 0.55, tex, vslot=4))
            els.append(_frame_box(axis, tc - 0.75, tc + 0.75, min(p0, p0 + s * 1.1) - 0.1, max(p0, p0 + s * 1.1) + 0.1,
                                  base, base + 0.7, "#bolt", vslot=6))                     # the nut on the strut
        els.append(_frame_box(axis, tc - k, tc + k, pc - r * 0.72, pc + r * 0.72, qc + r, qc + r + w, tex, vslot=5))
        for s in (-1, 1):   # the shoulders of the U, turned 45 degrees
            els.append(_frame_box(axis, tc - k, tc + k, pc + s * r * 0.84 - w / 2, pc + s * r * 0.84 + w / 2,
                                  qc + r * 0.45, qc + r * 0.95, tex, 45 * s, (pc + s * r * 0.84, qc + r * 0.7)))
    else:
        sgn = 1 if toward == "+p" else -1
        for s in (-1, 1):
            q0 = qc + s * (r + 0.1)
            lo, hi = sorted((base, pc + sgn * r * 0.55))
            els.append(_frame_box(axis, tc - k, tc + k, lo, hi, min(q0, q0 + s * w), max(q0, q0 + s * w), tex, vslot=4))
            nlo, nhi = sorted((base, base + sgn * 0.7))
            els.append(_frame_box(axis, tc - 0.75, tc + 0.75, nlo, nhi, min(q0, q0 + s * 1.1) - 0.1,
                                  max(q0, q0 + s * 1.1) + 0.1, "#bolt", vslot=6))
        cap = (pc + r, pc + r + w) if sgn > 0 else (pc - r - w, pc - r)
        els.append(_frame_box(axis, tc - k, tc + k, cap[0], cap[1], qc - r * 0.72, qc + r * 0.72, tex, vslot=5))
        for s in (-1, 1):
            els.append(_frame_box(axis, tc - k, tc + k, pc + sgn * r * 0.45, pc + sgn * r * 0.95,
                                  qc + s * r * 0.84 - w / 2, qc + s * r * 0.84 + w / 2, tex, -45 * s * sgn,
                                  (pc + sgn * r * 0.7, qc + s * r * 0.84)))
    return els


def _supports(axis, size, slot, tc=14.8):
    """The rack hardware for a slot, centred at tc along the pipe (near the end of the joint
    block, clear of the flanges and the label): a strut channel on the wall with a U-bolt round
    each pipe; a trapeze on two threaded rods from the ceiling with U-bolts (a single clevis
    hanger on one rod for a hung main); or a floor sleeper with a saddle under each pipe."""
    d, off, spread = SIZES[size]
    r = d / 2
    els = []
    cs = _centres(size, slot)
    if slot == "wall":
        els.append(_frame_box(axis, tc - 1, tc + 1, 0, 16, 0, 1.5, "#strut", vslot=2))
        for pc, qc in cs:
            els += _ubolt(axis, tc, pc, qc, r, "+q", 1.5)
    elif slot == "hung" and size == "large":
        pc, qc = cs[0]
        rr = r + 0.4   # round the insulation jacket
        els.append(_frame_box(axis, tc - 0.35, tc + 0.35, pc + rr + 0.9, 16, qc - 0.35, qc + 0.35, "#bolt", ends=False, vslot=3))
        els.append(_frame_box(axis, tc - 0.6, tc + 0.6, pc + rr + 0.3, pc + rr + 1.1, qc - 1.0, qc + 1.0, "#bolt", vslot=6))
        els.append(_frame_box(axis, tc - 0.6, tc + 0.6, pc - rr - 0.5, pc - rr, qc - rr * 0.72, qc + rr * 0.72,
                              "#clamp", vslot=5))
        for s in (-1, 1):
            q0 = qc + s * (rr + 0.05)
            els.append(_frame_box(axis, tc - 0.6, tc + 0.6, pc - rr * 0.55, pc + rr * 0.35, min(q0, q0 + s * 0.5),
                                  max(q0, q0 + s * 0.5), "#clamp", vslot=4))
            els.append(_frame_box(axis, tc - 0.6, tc + 0.6, pc - rr * 0.95, pc - rr * 0.45, qc + s * rr * 0.84 - 0.25,
                                  qc + s * rr * 0.84 + 0.25, "#clamp", 45 * s, (pc - rr * 0.7, qc + s * rr * 0.84)))
            # the clevis' two cheeks meeting the rod over the pipe
            els.append(_frame_box(axis, tc - 0.6, tc + 0.6, pc + rr * 0.35, pc + rr + 0.5, qc + s * 0.9 - 0.25,
                                  qc + s * 0.9 + 0.25, "#clamp", vslot=4))
    elif slot in ("up", "hung"):
        low = min(p for p, _ in cs) - r
        els.append(_frame_box(axis, tc - 1, tc + 1, low - 1.6, low, 0.3, 15.7, "#strut", vslot=1))
        for q in (0.4, 15.0):
            els.append(_frame_box(axis, tc - 0.3, tc + 0.3, low - 1.9, 16, q, q + 0.6, "#bolt", ends=False, vslot=3))
            els.append(_frame_box(axis, tc - 0.6, tc + 0.6, low - 2.4, low - 1.6, q - 0.3, q + 0.9, "#bolt", vslot=6))
        for pc, qc in cs:
            els += _ubolt(axis, tc, pc, qc, r, "+p", low)
    elif slot == "down":
        top = min(p for p, _ in cs) - r
        els.append(_frame_box(axis, tc - 1, tc + 1, 0, top, 0.6, 15.4, "#strut", vslot=1))
        for pc, qc in cs:
            # a saddle the pipe lies in, held down by a U-bolt
            els.append(_frame_box(axis, tc - 0.7, tc + 0.7, top, pc - r * 0.4, qc - r - 0.4, qc + r + 0.4, "#clamp", vslot=5))
            els += _ubolt(axis, tc, pc, qc, r, "+p", pc - r * 0.4)
    return els


# The outer flat of a pipe that faces the corridor (labels and tears go there).
FRONT = {"wall": "+q", "center": "+q", "up": "-p", "hung": "-p", "down": "+p"}


def _bars(d):
    """Profile of a round part: an octagon (very thin parts: one shaded square bar)."""
    return 4 if d > 2.5 else 1


def _pipe_elements(axis, kind, size, slot, joint):
    """Every element of one pipe block in the canonical orientation. Plain lengths are just
    the pipes; the joint block (l2_flange) also carries the rack's support, as racks are
    supported every few metres, next to the joints."""
    d = SIZES[size][0] + FATTER.get(kind, 0)
    els = []
    flats = {FRONT[slot]: "#torn"} if joint and kind == "insulated" else None
    for i, (pc, qc) in enumerate(_centres(size, slot)):
        els += _octagon(axis, 0, 16, pc, qc, d, "#pipe", vslot=3 * i, flats=flats, bars=_bars(d), cap="#cap")
        if joint:
            els += _joint(axis, kind, pc, qc, d, i, slot)
    if joint and slot != "center":
        els += _supports(axis, size, slot)
    return els


def _joint(axis, kind, pc, qc, d, i, slot):
    """The fitting a l2_flange block adds round each pipe, a quarter of the way along (clear of
    the rack's strut in the middle of the block)."""
    els = []
    if kind == "steel":
        # Two bolted flanges with a gasket between, and the bolts through both.
        fd = d + (3.2 if d > 5 else 2.2)
        els += _octagon(axis, 2.4, 3.9, pc, qc, fd, "#flange", vslot=i, bars=_bars(fd), cap="#cap")
        els += _octagon(axis, 4.1, 5.6, pc, qc, fd, "#flange", vslot=i + 1, bars=_bars(fd), cap="#cap")
        els += _octagon(axis, 3.9, 4.1, pc, qc, fd - 1.0, "#gasket", vslot=i, bars=2)
        n = 8 if d > 8 else 4
        br = fd / 2 - (1.1 if d > 5 else 0.7)
        bw = 1.1 if d > 5 else 0.7
        for k in range(n):
            a = math.radians(22.5 + 360 * k / n) if n == 8 else math.radians(45 + 90 * k)
            bp, bq = pc + br * math.sin(a), qc + br * math.cos(a)
            els.append(_frame_box(axis, 1.6, 6.4, bp - bw / 2, bp + bw / 2, bq - bw / 2, bq + bw / 2, "#bolt", vslot=k))
    elif kind == "copper":
        # A soldered coupling sleeve, green with verdigris, with a lip at each end.
        els += _octagon(axis, 2.4, 5.6, pc, qc, d + 0.9, "#joint", vslot=i, bars=_bars(d + 0.9), cap="#cap")
        els += _octagon(axis, 2.2, 2.6, pc, qc, d + 1.3, "#joint", vslot=i + 2, bars=_bars(d + 1.3), cap="#cap")
        els += _octagon(axis, 5.4, 5.8, pc, qc, d + 1.3, "#joint", vslot=i + 3, bars=_bars(d + 1.3), cap="#cap")
    elif kind == "insulated":
        # Two metal bands round the jacket (and the torn flat on the pipe itself, above).
        for t0 in (2.6, 12.6):
            els += _octagon(axis, t0, t0 + 1.0, pc, qc, d + 0.5, "#band", vslot=i, bars=_bars(d + 0.5), cap="#cap")
    elif kind == "conduit":
        # A compression coupling: a fatter sleeve with a nut at each end.
        els += _octagon(axis, 2.6, 5.4, pc, qc, d + 0.6, "#joint", vslot=i, bars=_bars(d + 0.6), cap="#cap")
        els += _octagon(axis, 2.2, 3.0, pc, qc, d + 1.1, "#bolt", vslot=i, bars=2)
        els += _octagon(axis, 5.0, 5.8, pc, qc, d + 1.1, "#bolt", vslot=i, bars=2)
    else:
        # Painted pipe: a skin of the same paint over the pipe, its corridor-side flat printed
        # with the label (white bands, the word, a flow arrow).
        els += _octagon(axis, 0.3, 15.7, pc, qc, d + 0.14, "#pipe", vslot=i, flats={FRONT[slot]: "#label"},
                        ends=False, bars=_bars(d), cap="#cap")
    return els


def _pipe_textures(kind):
    t = {"particle": _ref(f"l2_pipe_{kind}"), "pipe": _ref(f"l2_pipe_{kind}"),
         "strut": _ref("l2_strut"), "clamp": _ref("l2_clamp"), "bolt": _ref("l2_bolt_steel"),
         "cap": _ref(f"l2_pipe_cap_{kind}")}
    if kind == "steel":
        t["flange"] = _ref("l2_pipe_flange")
        t["gasket"] = _ref("l2_bolt_steel")
    elif kind == "copper":
        t["joint"] = _ref("l2_pipe_copper_patina")
    elif kind == "insulated":
        t["band"] = _ref("l2_pipe_band")
        t["torn"] = _ref("l2_pipe_torn")
    elif kind == "conduit":
        t["joint"] = _ref("l2_pipe_conduit")
    else:
        t["label"] = _ref(f"l2_pipe_label_{kind.split('_')[1]}")
    return t


def _label_uv(elements):
    """Label and tear flats show the middle rows of their texture (where the word / the tear
    is), squeezed onto the flat."""
    for e in elements:
        for face, fd in e["faces"].items():
            if fd["texture"] in ("#label", "#torn"):
                t0, _, t1, _ = fd["uv"]
                fd["uv"] = [t0, 4.6, t1, 11.4] if fd["texture"] == "#label" else [t0, 3.0, t1, 13.0]
    return elements


# Geometry of each canonical model: (axis, slot). The blockstates turn them into every state.
PIPE_GEOMS = {"x_wall": ("x", "wall"), "x_up": ("x", "up"), "x_hung": ("x", "hung"), "x_down": ("x", "down"),
              "x_center": ("x", "center"), "y_wall": ("y", "wall"), "y_center": ("y", "center")}


def _pipe_state_model(axis, slot):
    """(geometry name, blockstate y turn) for one AXIS/SLOT pair of block/Level2PipeBlock."""
    side_y = {"north": 0, "east": 90, "south": 180, "west": 270}
    if axis == "y":
        if slot in side_y:
            return "y_wall", side_y[slot]
        return "y_center", 0
    turn = 0 if axis == "x" else 90
    if slot in ("up", "hung", "down"):
        return f"x_{slot}", turn
    # pipes along x hug north/south, along z hug east/west; anything else is drawn as CENTER
    if axis == "x" and slot in ("north", "south"):
        return "x_wall", side_y[slot]
    if axis == "z" and slot in ("east", "west"):
        return "x_wall", side_y[slot]   # x_wall turned 90 = against east, 270 = against west
    return "x_center", turn


def _beam_elements():
    """A rolled steel I-beam along z through the middle of the block, right under the slab (it
    must match BEAM_ALONG_Z in block/Level2PipeBlock): top flange, web, bottom flange, and a few
    bolt heads where the flanges were bolted up."""
    els = [_frame_box("z", 0, 16, 14.8, 16, 5, 11, "#beam", vslot=0),
           _frame_box("z", 0, 16, BEAM_BOTTOM + 1.2, 14.8, 7.5, 8.5, "#beam", vslot=1),
           _frame_box("z", 0, 16, BEAM_BOTTOM, BEAM_BOTTOM + 1.2, 5, 11, "#beam", vslot=2)]
    for x in (5.6, 9.6):
        els.append(_plain_box((x, BEAM_BOTTOM - 0.5, 7.2), (x + 0.8, BEAM_BOTTOM, 8.0), "#bolt"))
    return els


def _beam_model() -> None:
    _bm("l2_pipe_beam", P._model({"particle": _ref("l2_beam"), "beam": _ref("l2_beam"), "bolt": _ref("l2_bolt_steel")},
                                 _beam_elements()))
    # The bare beam block (l2_beam, AxisShapeBlock): the model runs along z.
    _bs("l2_beam", {"variants": {"axis=z": {"model": _ref("l2_pipe_beam")},
                                 "axis=x": {"model": _ref("l2_pipe_beam"), "y": 90}}})
    _im("l2_beam", parent=_ref("l2_pipe_beam"))


def _beam_parts(beam_turn):
    """Multipart entries adding the beam when BEAM = true. beam_turn: {"x|y": 0, "z": 90} says
    how to turn the model (built along z) for each AXIS value, so it always crosses the block."""
    parts = []
    for axes, y in beam_turn.items():
        apply = {"model": _ref("l2_pipe_beam")}
        if y:
            apply["y"] = y
        parts.append({"when": {"beam": "true", "axis": axes}, "apply": apply})
    return parts


def _pipe_models() -> None:
    slots = ("north", "south", "east", "west", "up", "down", "hung", "center")
    for block, joint in (("l2_pipe", False), ("l2_flange", True)):
        for kind in KINDS:
            for size in SIZES:
                for geom, (axis, slot) in PIPE_GEOMS.items():
                    els = _pipe_elements(axis, kind, size, slot, joint)
                    if joint:
                        _label_uv(els)
                    _bm(f"{block}_{kind}_{size}_{geom}", P._model(_pipe_textures(kind), els))
        # Multipart, so a BEAM can be added on top of whichever pipe model the state picks.
        parts = []
        for axis in ("x", "y", "z"):
            for kind in KINDS:
                for size in SIZES:
                    for slot in slots:
                        geom, y = _pipe_state_model(axis, slot)
                        apply = {"model": _ref(f"{block}_{kind}_{size}_{geom}")}
                        if y:
                            apply["y"] = y
                        parts.append({"when": {"axis": axis, "kind": kind, "size": size, "slot": slot}, "apply": apply})
        parts += _beam_parts({"x|y": 0, "z": 90})
        _bs(block, {"multipart": parts})
        _im(block, parent=_ref(f"{block}_steel_medium_x_wall"))


def _elbow_elements(kind, size, up):
    """Bend from the north face (horizontal leg along z) down to the floor (or up to the ceiling):
    both legs run to the middle of the block and the outer corner is filled by a block turned
    45 degrees, which leaves a chamfer like a mitred (welded) elbow; a weld bead round each leg
    where the bend starts (a band on insulation, a soldered sleeve on copper)."""
    d = SIZES[size][0] + FATTER.get(kind, 0)
    bars = _bars(d)
    r = d / 2
    els = _octagon("z", 0, 8, 8, 8, d, "#pipe", bars=bars, cap="#cap")
    if up:
        els += _octagon("y", 8, 16, 8, 8, d, "#pipe", vslot=2, bars=bars, cap="#cap")
    else:
        els += _octagon("y", 0, 8, 8, 8, d, "#pipe", vslot=2, bars=bars, cap="#cap")
    h = r / math.sqrt(2)
    els.append(_box((8 - r, 8 - h, 8 - h), (8 + r, 8 + h, 8 + h), {f: ("#pipe", [0, 6, 16, 10]) for f in FACES},
                    rot=((8, 8, 8), "x", 45), shade=False))
    bead, bcap = {"insulated": ("#band", "#cap"), "copper": ("#joint", "#cap"), "conduit": ("#joint", "#cap")}.get(kind, ("#bolt", None))
    w = 0.5 if bead == "#bolt" else 1.0
    bb = _bars(d + 0.5) if bcap else 2
    els += _octagon("z", 8 - r - w - 0.2, 8 - r - 0.2, 8, 8, d + 0.5, bead, bars=bb, cap=bcap)
    t0 = 8 + r + 0.2 if up else 8 - r - w - 0.2
    els += _octagon("y", t0, t0 + w, 8, 8, d + 0.5, bead, bars=bb, cap=bcap)
    return els


def _tee_elements(kind, size, run, branch):
    """A straight run through the middle with a branch out of one side and a welded collar."""
    d = SIZES[size][0] + FATTER.get(kind, 0)
    bars = _bars(d)
    els = _octagon(run, 0, 16, 8, 8, d, "#pipe", bars=bars, cap="#cap")
    baxis, positive = branch
    lo, hi = (8, 16) if positive else (0, 8)
    els += _octagon(baxis, lo, hi, 8, 8, d, "#pipe", vslot=4, bars=bars, cap="#cap")
    c0, c1 = (8 + d / 2, 8 + d / 2 + 0.8) if positive else (8 - d / 2 - 0.8, 8 - d / 2)
    els += _octagon(baxis, c0, c1, 8, 8, d + 0.9, "#bolt", bars=2)
    return els


def _fitting_models() -> None:
    """Elbows (facing x vertical) and tees (axis x facing), single centred pipes."""
    variants = {}
    for kind in KINDS:
        for size in SIZES:
            tex = _pipe_textures(kind)
            for up in (False, True):
                _bm(f"l2_pipe_elbow_{kind}_{size}_{'up' if up else 'down'}", P._model(tex, _elbow_elements(kind, size, up)))
            for facing, y in FACING_Y.items():
                for vert in ("up", "down"):
                    v = {"model": _ref(f"l2_pipe_elbow_{kind}_{size}_{vert}")}
                    if y:
                        v["y"] = y
                    variants[f"facing={facing},kind={kind},size={size},vertical_direction={vert}"] = v
    _bs("l2_pipe_elbow", {"variants": variants})
    _im("l2_pipe_elbow", parent=_ref("l2_pipe_elbow_steel_medium_down"))

    # Tee geometries: run along x with the branch north / up / down; run along y with it north.
    geoms = {"x_north": ("x", ("z", False)), "x_up": ("x", ("y", True)), "x_down": ("x", ("y", False)),
             "y_north": ("y", ("z", False))}
    variants = {}
    for kind in KINDS:
        for size in SIZES:
            tex = _pipe_textures(kind)
            for g, (run, branch) in geoms.items():
                _bm(f"l2_pipe_tee_{kind}_{size}_{g}", P._model(tex, _tee_elements(kind, size, run, branch)))
            for axis in ("x", "y", "z"):
                for facing in FACES:
                    if axis == "y":
                        model, y = ("y_north", FACING_Y[facing]) if facing in FACING_Y else (None, 0)
                    else:
                        turn = 0 if axis == "x" else 90
                        if facing in ("up", "down"):
                            model, y = f"x_{facing}", turn
                        elif (axis == "x" and facing in ("north", "south")) or (axis == "z" and facing in ("east", "west")):
                            model, y = "x_north", FACING_Y[facing]
                        else:
                            model, y = None, turn
                    if model is None:   # branch along the run: just a straight pipe
                        ref = _ref(f"l2_pipe_{kind}_{size}_{'y' if axis == 'y' else 'x'}_center")
                    else:
                        ref = _ref(f"l2_pipe_tee_{kind}_{size}_{model}")
                    v = {"model": ref}
                    if y:
                        v["y"] = y
                    variants[f"axis={axis},facing={facing},kind={kind},size={size}"] = v
    _bs("l2_pipe_tee", {"variants": variants})
    _im("l2_pipe_tee", parent=_ref("l2_pipe_tee_steel_medium_x_down"))


def _surface_models() -> None:
    """The wall base row (variant models and blockstate come from gen_assets variant_models,
    which copies this plain model) and the lane-line floor (line along the FACING edge)."""
    _bm("l2_wall_base", {"parent": "minecraft:block/cube_all", "textures": {"all": _ref("l2_wall_base")}})
    _im("l2_wall_base")
    floor = _ref("pipe_floor")
    _bm("l2_floor_line", {"parent": "minecraft:block/cube", "textures": {
        "particle": floor, "up": _ref("l2_floor_line"), "down": floor,
        "north": floor, "south": floor, "east": floor, "west": floor}})
    P._facing_state("l2_floor_line")
    _im("l2_floor_line")


# =============================================================================
# Props: models built facing north (front to the north, any wall on the south side)
# =============================================================================

def _faces(default, **special):
    """{face: (texture, auto uv)} for a box: `default` all round, `special` on named faces
    (a value may be (texture, uv) to set the uv too)."""
    out = {}
    for f in FACES:
        v = special.get(f, default)
        if v is None:
            continue
        out[f] = v if isinstance(v, tuple) else (v, None)
    return out


def _ring(axis, pc, qc, t0, t1, radius, thick, tex):
    """An octagonal ring (a wheel rim) in the plane across `axis`: eight bars, four of them
    turned 45 degrees about the axis."""
    side = 2 * radius * TAN + thick * 0.45
    h, s = thick / 2, side / 2
    els = []
    for dp, dq in ((radius, 0), (-radius, 0), (0, radius), (0, -radius)):
        if dp:
            els.append(_frame_box(axis, t0, t1, pc + dp - h, pc + dp + h, qc - s, qc + s, tex, ends=True))
        else:
            els.append(_frame_box(axis, t0, t1, pc - s, pc + s, qc + dq - h, qc + dq + h, tex, ends=True))
    for dp in (radius, -radius):
        for ang in (45, -45):
            els.append(_frame_box(axis, t0, t1, pc + dp - h, pc + dp + h, qc - s, qc + s, tex, ang, (pc, qc)))
    return els


def _valve_elements(stub=True, feed=False):
    """Gate valve on a stub out of the wall with its hand wheel facing the corridor; feed: on a
    pipe dropping from above (l2_feed_pipe, 4 px out from the wall) and on down to the main, the
    whole valve moved back 0.8 px to sit on it, its flanged ends up and down."""
    els = []
    dz = 0.8 if feed else 0.0
    if feed:
        els += _octagon("y", 0, 16, 8, 12, 3.0, "#steel", cap="#cap")
    elif stub:
        els += _octagon("z", 13.2, 16, 8, 8, 4.2, "#steel")
        els += _octagon("z", 12.6, 13.2, 8, 8, 6.6, "#body", bars=2)
    els.append(_plain_box((5.2, 5.4, 9.8 + dz), (10.8, 10.6, 12.6 + dz), "#body"))
    if feed:
        els.append(_plain_box((6.4, 4.7, 10.4 + dz), (9.6, 11.3, 12.0 + dz), "#body"))   # flanged ends, up and down
    else:
        els.append(_plain_box((4.7, 6.4, 10.4), (11.3, 9.6, 12.0), "#body"))       # the body's flanged ends
    els.append(_plain_box((6.3, 6.3, 8.6 + dz), (9.7, 9.7, 9.8 + dz), "#body"))          # bonnet
    els.append(_plain_box((7.6, 7.6, 6.9 + dz), (8.4, 8.4, 8.6 + dz), "#bolt"))          # stem
    els += _ring("z", 8, 8, 7.0 + dz, 8.0 + dz, 5.3, 1.1, "#wheel")
    for ang in (45, -45):
        els.append(_box((3.0, 7.5, 7.2 + dz), (13.0, 8.5, 7.8 + dz), _faces("#wheel"), rot=((8, 8, 7.5 + dz), "z", ang)))
    els.append(_plain_box((6.9, 6.9, 6.7 + dz), (9.1, 9.1, 8.2 + dz), "#wheel"))         # hub
    return els


def _valve_models() -> None:
    tex = {"steel": _ref("l2_pipe_steel"), "body": _ref("l2_pipe_flange"), "bolt": _ref("l2_bolt_steel"),
           "cap": _ref("l2_pipe_cap_steel")}
    variants = {}
    for colour, wheel in (("red", "pipe_valve"), ("grey", "l2_wheel_grey")):
        for feed in (False, True):
            name = f"l2_valve_wheel_{colour}" + ("_feed" if feed else "")
            _bm(name, P._model({"particle": _ref(wheel), "wheel": _ref(wheel), **tex}, _valve_elements(feed=feed)))
            for f, y in FACING_Y.items():
                v = {"model": _ref(name)}
                if y:
                    v["y"] = y
                variants[f"color={colour},facing={f},feed={str(feed).lower()}"] = v
    _bs("l2_valve_wheel", {"variants": variants})
    _im("l2_valve_wheel", parent=_ref("l2_valve_wheel_red"))
    # The older PIPE_VALVE (red wheel on the wall) gets the same valve.
    _bm("pipe_valve", P._model({"particle": _ref("pipe_valve"), "wheel": _ref("pipe_valve"), **tex}, _valve_elements()))
    P._facing_state("pipe_valve")


def _lever_elements(open_, feed):
    """Quarter-turn ball valve (block Level2LeverValveBlock): a squat brass body between two hex
    unions and a flat red lever. Open = the lever lies along the flow. Without feed the flow runs
    straight out of the wall (z) and the lever sits on top; with feed it runs down the wall from a
    branch above (x 8, z 12) and the lever is on the front."""
    els = []
    if feed:
        els += _octagon("y", 0, 4.8, 8, 12, 3.0, "#steel", cap="#cap")
        els += _octagon("y", 11.2, 16, 8, 12, 3.0, "#steel", cap="#cap")
        els += _octagon("y", 4.8, 5.6, 8, 12, 5.0, "#bolt", bars=2)
        els += _octagon("y", 10.4, 11.2, 8, 12, 5.0, "#bolt", bars=2)
        els.append(_plain_box((5.4, 5.6, 9.4), (10.6, 10.4, 14.6), "#body"))
        els.append(_plain_box((7.4, 7.4, 8.2), (8.6, 8.6, 9.4), "#bolt"))                     # stem
        if open_:
            els.append(_plain_box((7.3, 1.2, 7.4), (8.7, 8.8, 8.2), "#handle"))                # pointing down
            els.append(_plain_box((7.1, 0.4, 7.2), (8.9, 3.2, 8.4), "#handle"))                # grip
        else:
            els.append(_plain_box((7.2, 7.3, 7.4), (15.0, 8.7, 8.2), "#handle"))               # across the pipe
            els.append(_plain_box((12.4, 7.1, 7.2), (15.4, 8.9, 8.4), "#handle"))
    else:
        els += _octagon("z", 13.4, 16, 8, 8, 3.4, "#steel", cap="#cap")
        els += _octagon("z", 5.8, 7.6, 8, 8, 3.4, "#steel", cap="#cap")                       # outlet stub
        els += _octagon("z", 12.4, 13.4, 8, 8, 5.0, "#bolt", bars=2)
        els += _octagon("z", 7.6, 8.6, 8, 8, 5.0, "#bolt", bars=2)
        els.append(_plain_box((5.4, 5.4, 8.6), (10.6, 10.6, 12.4), "#body"))
        els.append(_plain_box((7.4, 10.6, 9.9), (8.6, 11.8, 11.1), "#bolt"))                  # stem
        if open_:
            els.append(_plain_box((7.3, 11.8, 4.0), (8.7, 12.5, 11.2), "#handle"))            # along the pipe
            els.append(_plain_box((7.1, 11.6, 3.2), (8.9, 12.7, 6.0), "#handle"))
        else:
            els.append(_plain_box((7.0, 11.8, 9.8), (14.4, 12.5, 11.2), "#handle"))           # across it
            els.append(_plain_box((12.0, 11.6, 9.6), (15.2, 12.7, 11.4), "#handle"))
    return els


def _lever_models() -> None:
    tex = {"particle": _ref("pipe_valve"), "handle": _ref("pipe_valve"), "body": _ref("l2_pipe_copper"),
           "bolt": _ref("l2_bolt_steel"), "steel": _ref("l2_pipe_steel"), "cap": _ref("l2_pipe_cap_steel")}
    variants = {}
    for open_ in (True, False):
        for feed in (False, True):
            name = f"l2_valve_lever_{'open' if open_ else 'shut'}" + ("_feed" if feed else "")
            _bm(name, P._model(tex, _lever_elements(open_, feed)))
            for f, y in FACING_Y.items():
                v = {"model": _ref(name)}
                if y:
                    v["y"] = y
                variants[f"facing={f},feed={str(feed).lower()},open={str(open_).lower()}"] = v
    _bs("l2_valve_lever", {"variants": variants})
    _im("l2_valve_lever", parent=_ref("l2_valve_lever_open"))


def _feed_models() -> None:
    """The branch from a manifold header down to a FEED valve (block Level2FeedPipeBlock): a 3 px
    pipe 4 px out from the wall, reaching up into the ceiling band to the centre of the header's
    wall-side pipe (MEDIUM, HUNG: 7.5 px up in the block above), with a welded saddle under the
    header and a union nut part way down."""
    variants = {}
    for kind in KINDS:
        els = _octagon("y", 0, 16 + 16 - HANG["medium"], 8, 12, 3.0, "#pipe", cap="#cap")
        els += _octagon("y", 9.0, 10.6, 8, 12, 4.2, "#bolt", bars=2)
        hb = 16 + 16 - HANG["medium"] - SIZES["medium"][0] / 2   # the header's underside, in this block's units
        els += _octagon("y", hb - 1.6, hb, 8, 12, 4.4, "#collar", bars=2)
        tex = {**_pipe_textures(kind), "collar": _ref("l2_pipe_flange")}
        _bm(f"l2_feed_pipe_{kind}", P._model(tex, els))
        for f, y in FACING_Y.items():
            v = {"model": _ref(f"l2_feed_pipe_{kind}")}
            if y:
                v["y"] = y
            variants[f"facing={f},kind={kind}"] = v
    _bs("l2_feed_pipe", {"variants": variants})
    _im("l2_feed_pipe", parent=_ref("l2_feed_pipe_painted_red"))


def _gauge_models() -> None:
    """Pressure gauge on a pigtail: a pipe out of the wall, up into the back of a round case,
    the dial behind glass on the front."""
    els = _octagon("z", 12.2, 16, 4.6, 8, 1.8, "#steel", bars=2)
    els += _octagon("y", 3.7, 6.0, 8, 12.9, 1.8, "#steel", bars=2)
    els.append(_plain_box((7.1, 4.4, 11.9), (8.9, 5.6, 13.7), "#bolt"))         # hex fitting
    els += _octagon("z", 10.7, 13.4, 9, 8, 7.6, "#case")
    els += _octagon("z", 10.5, 10.9, 9, 8, 8.0, "#bolt", bars=2)                  # bezel
    els.append(_box((4.2, 5.2, 10.42), (11.8, 12.8, 10.5), {"north": ("#dial", [0, 0, 16, 16])}))
    tex = {"particle": _ref("l2_bolt_steel"), "steel": _ref("l2_pipe_copper"), "bolt": _ref("l2_bolt_steel"),
           "case": _ref("l2_bolt_steel"), "dial": _ref("l2_gauge_dial")}
    _bm("l2_gauge", P._model(tex, els))
    P._facing_state("l2_gauge")
    _im("l2_gauge")


CABLE_RUN = ((3.0, 2.2, 0), (5.2, 1.8, 2), (7.2, 2.0, 1), (9.3, 2.2, 3), (11.4, 1.6, 5), (13.1, 1.8, 4),
             (4.2, 1.7, 6, 2.0), (8.3, 1.9, 7, 2.0), (11.8, 1.6, 0, 1.7))


def _cable_uv(els, row):
    """Cable faces show one row (one cable colour) of the cable texture."""
    for e in els:
        for fd in e["faces"].values():
            if fd["texture"] == "#cable" and "rotation" not in fd and fd.get("cullface") not in ("east", "west"):
                u0, _, u1, _ = fd["uv"]
                fd["uv"] = [u0, 2 * row + 0.3, u1, 2 * row + 1.7]
    return els


# The tray hangs this much lower than it used to, under the beams (BEAM_BOTTOM) instead of in their place.
TRAY_DROP = 6.0


def _cable_tray_models() -> None:
    """Ladder tray on a trapeze hung from two threaded rods, carrying a layer and a half of
    cables (the colours come from rows of the cable texture) tied into bundles with black ties.
    It hangs below the beam line (block Level2CableTrayBlock); the DROOP part is a loop of two
    cables that have slipped out and sag under it."""
    b = 9 - TRAY_DROP
    els = [_plain_box((0, b, 1), (16, b + 3, 1.8), "#strut"), _plain_box((0, b, 14.2), (16, b + 3, 15), "#strut")]
    for x in (2, 6, 10, 14):
        els.append(_plain_box((x - 0.5, b, 1.8), (x + 0.5, b + 0.7, 14.2), "#strut"))
    els.append(_plain_box((7, b - 0.8, 0.3), (9, b, 15.7), "#strut"))
    for z in (0.5, 14.9):
        els.append(_box((7.7, b - 1.0, z), (8.3, 16, z + 0.6), _faces("#bolt", up=None)))
        els.append(_plain_box((7.4, b - 1.4, z - 0.3), (8.6, b - 0.8, z + 0.9), "#bolt"))       # nut under the trapeze
    for spec in CABLE_RUN:
        z, d, row = spec[:3]
        y = b + 0.7 + d / 2 + (spec[3] if len(spec) > 3 else 0)
        els += _cable_uv(_octagon("x", 0, 16, y, z, d, "#cable", bars=2), row)
    for x in (4.0, 12.0):
        # a black tie round the bundle
        els.append(_plain_box((x - 0.3, b + 0.7, 2.0), (x + 0.3, b + 5.2, 14.0), "#tie"))
    tex = {"particle": _ref("l2_strut"), "strut": _ref("l2_strut"), "bolt": _ref("l2_bolt_steel"),
           "cable": _ref("l2_cables"), "tie": _ref("l2_bolt_steel")}
    _bm("l2_cable_tray", P._model(tex, els))
    # The droop: two cables leaving the tray's south side, sagging in a loop and climbing back.
    droop = []
    under = b - 0.8
    for z, d, row, ang, sag in ((12.4, 1.6, 0, 45, 2.6), (11.0, 1.3, 5, 22.5, 1.7)):
        run = sag / math.tan(math.radians(ang))        # how far along x each sloping piece goes
        lo = under - sag                                # centre line at the bottom of the loop
        length = math.hypot(run, sag)
        for x0, sgn in ((2.0, -1), (14.0, 1)):
            cx = x0 - sgn * run / 2
            cy = under - sag / 2
            droop += _cable_uv([_box((cx - length / 2 - 0.3, cy - d / 2, z - d / 2), (cx + length / 2 + 0.3, cy + d / 2, z + d / 2),
                                     _faces("#cable"), rot=((cx, cy, z), "z", sgn * ang), shade=True)], row)
            droop += _cable_uv([_plain_box((x0 - d / 2, under - 0.2, z - d / 2), (x0 + d / 2, b + 1.0, z + d / 2), "#cable")], row)
        x1, x2 = 2.0 + run, 14.0 - run
        if x2 > x1:
            droop += _cable_uv([_plain_box((x1 - 0.2, lo - d / 2, z - d / 2), (x2 + 0.2, lo + d / 2, z + d / 2), "#cable")], row)
    _bm("l2_cable_tray_droop", P._model(tex, droop))
    parts = [{"when": {"axis": "x"}, "apply": {"model": _ref("l2_cable_tray")}},
             {"when": {"axis": "z"}, "apply": {"model": _ref("l2_cable_tray"), "y": 90}},
             {"when": {"droop": "true", "axis": "x"}, "apply": {"model": _ref("l2_cable_tray_droop")}},
             {"when": {"droop": "true", "axis": "z"}, "apply": {"model": _ref("l2_cable_tray_droop"), "y": 90}}]
    _bs("l2_cable_tray", {"multipart": parts + _beam_parts({"x": 0, "z": 90})})
    _im("l2_cable_tray")


def _junction_box_models() -> None:
    front = ("#front", [0, 0, 16, 16])
    els = [_box((4, 4, 13), (12, 12, 16), _faces("#encl", north=front)),
           _box((3.6, 3.6, 12.5), (12.4, 12.4, 13), _faces("#encl", north=front))]
    for x, y in ((4.1, 4.1), (11.2, 4.1), (4.1, 11.2), (11.2, 11.2)):
        els.append(_plain_box((x, y, 12.2), (x + 0.7, y + 0.7, 12.5), "#bolt"))
    els += _octagon("y", 0, 3.6, 8, 14.2, 2.2, "#conduit", bars=2)
    els += _octagon("y", 2.8, 4.0, 8, 14.2, 2.9, "#bolt", bars=2)
    els.append(_plain_box((7.4, 0, 15.2), (8.6, 1.2, 16), "#bolt"))              # strap to the wall
    tex = {"particle": _ref("l2_enclosure"), "encl": _ref("l2_enclosure"), "front": _ref("l2_jbox_front"),
           "bolt": _ref("l2_bolt_steel"), "conduit": _ref("l2_pipe_conduit")}
    _bm("l2_junction_box", P._model(tex, els))
    P._facing_state("l2_junction_box")
    _im("l2_junction_box")


def _split_halves(elements):
    """Elements of a 32-tall model -> (lower, upper) models: each element goes to the half its
    centre is in, the upper ones moved down a block."""
    lower, upper = [], []
    for e in elements:
        cy = (e["from"][1] + e["to"][1]) / 2
        if cy < 16:
            lower.append(e)
        else:
            upper.append(_shift(e, (0, -16, 0)))
    return lower, upper


def _shift(e, d):
    e = json_copy(e)
    e["from"] = _r([e["from"][i] + d[i] for i in range(3)])
    e["to"] = _r([e["to"][i] + d[i] for i in range(3)])
    if "rotation" in e:
        o = e["rotation"]["origin"]
        e["rotation"]["origin"] = _r([o[i] + d[i] for i in range(3)])
    # faces that now lie on the block boundary may cull; ones that moved off it may not
    edge = {"north": e["from"][2] <= 0, "south": e["to"][2] >= 16, "west": e["from"][0] <= 0,
            "east": e["to"][0] >= 16, "down": e["from"][1] <= 0, "up": e["to"][1] >= 16}
    for face, fd in e["faces"].items():
        fd.pop("cullface", None)
        if "rotation" not in e and edge[face] and _inside_block(e):
            fd["cullface"] = face
    return e


def _inside_block(e):
    return all(0 <= e["from"][i] and e["to"][i] <= 16 for i in range(3))


def json_copy(o):
    import json
    return json.loads(json.dumps(o))


def _tall_state(name, models, extra=None):
    """facing x half (x extra property values) blockstate for two-block furniture."""
    variants = {}
    for f, y in FACING_Y.items():
        for half in ("lower", "upper"):
            for key, model in models(half):
                v = {"model": _ref(model)}
                if y:
                    v["y"] = y
                variants[f"facing={f},half={half}" + (f",{key}" if key else "")] = v
    _bs(name, {"variants": variants})


def _breaker_models() -> None:
    els = [_box((2.5, 4, 12.5), (13.5, 16, 16), _faces("#encl", north=("#door_bottom", [0, 0, 16, 16]), up=None)),
           _box((2.5, 16, 12.5), (13.5, 30, 16), _faces("#encl", north=("#door_top", [0, 0, 16, 16]), down=None)),
           _plain_box((2.0, 29.6, 12.0), (14.0, 30.4, 16), "#encl"),                       # drip lip
           _plain_box((12.6, 15.0, 11.9), (13.2, 19.0, 12.5), "#bolt")]                    # door handle
    for x in (4.6, 8, 11.4):
        els += _octagon("y", 30.4, 32, x, 14.4, 2.0, "#conduit", bars=2)
        els += _octagon("y", 30.3, 30.9, x, 14.4, 2.8, "#bolt", bars=2)
    lower, upper = _split_halves(els)
    tex = {"particle": _ref("l2_enclosure"), "encl": _ref("l2_enclosure"), "door_top": _ref("l2_breaker_top"),
           "door_bottom": _ref("l2_breaker_bottom"), "bolt": _ref("l2_bolt_steel"), "conduit": _ref("l2_pipe_conduit")}
    _bm("l2_breaker_panel_lower", P._model(tex, lower))
    _bm("l2_breaker_panel_upper", P._model(tex, upper))
    _tall_state("l2_breaker_panel", lambda half: [("", f"l2_breaker_panel_{half}")])
    _im("l2_breaker_panel", parent=_ref("l2_breaker_panel_lower"))


SHELF_DECKS = (0.8, 10.6, 20.6, 30.4)


def _carton(x0, y0, z0, w, h, d, rot=None):
    side = "#carton"
    return _box((x0, y0, z0), (x0 + w, y0 + h, z0 + d),
                _faces(side, up=("#carton_top", None)), rot=rot)


def _tin(x, y0, z, d=3.4, h=4.2):
    els = _octagon("y", y0, y0 + h, x, z, d, "#tin", bars=2)
    els += _octagon("y", y0 + h, y0 + h + 0.3, x, z, d - 0.4, "#bolt", bars=2)
    return els


def _jerrycan(x0, y0, z0):
    return [_plain_box((x0, y0, z0), (x0 + 2.6, y0 + 6.5, z0 + 5.4), "#jerry"),
            _plain_box((x0 + 0.6, y0 + 6.5, z0 + 1.0), (x0 + 2.0, y0 + 7.4, z0 + 3.4), "#jerry"),
            _octagon("y", y0 + 6.5, y0 + 7.6, x0 + 1.3, z0 + 4.4, 1.2, "#bolt", bars=2)[0]]


def _toolbox(x0, y0, z0, w=7.0):
    return [_box((x0, y0, z0), (x0 + w, y0 + 3.6, z0 + 3.6), _faces("#toolbox", north=("#toolbox", [2, 4, 14, 12]))),
            _plain_box((x0 + 1.5, y0 + 3.6, z0 + 1.6), (x0 + w - 1.5, y0 + 4.4, z0 + 2.0), "#bolt")]


def _shelf_items(variant):
    """What stands on each deck of shelving unit VARIANT (fixed, so the four look different)."""
    rng = np.random.default_rng(2470 + variant)
    els = []
    for k, deck in enumerate(SHELF_DECKS):
        top = deck + 0.8
        head = (SHELF_DECKS[k + 1] - 0.4 - top) if k < 3 else 1.6
        if k == 3:
            # the top deck only gets low things (it is right under the next block's ceiling)
            if variant in (0, 1):
                els.append(_carton(2 + rng.uniform(0, 3), top, 6, 6, 1.4, 8))
            continue
        x = 1.2
        while x < 14:
            w = rng.uniform(4.0, 6.5)
            if x + w > 15.0:
                break
            pick = rng.random()
            if variant == 0 or (variant == 1 and pick < 0.15) or (variant == 2 and pick < 0.25):
                h = min(head, rng.uniform(4.5, 8.5))
                d = rng.uniform(7, 11)
                els.append(_carton(x, top, 15.6 - d, w, h, d))
            elif variant == 1:
                if pick < 0.7:
                    els += _toolbox(x, top, 9.0, min(w + 1, 7.5))
                else:
                    els += _octagon("y", top, top + 1.8, x + 2.5, 10, 4.6, "#cable", bars=4)   # a coil of cable
                    w = 5.0
            elif variant == 2:
                if pick < 0.6:
                    for i in range(2):
                        els += _tin(x + 1.8 + i * 3.6, top, 9 + rng.uniform(0, 3))
                    w = 7.4
                else:
                    els += _jerrycan(x, top, 9)
                    w = 3.0
            else:   # variant 3: nearly bare
                if pick < 0.3:
                    els += _tin(x + 1.8, top, 11)
                elif pick < 0.5:
                    els.append(_carton(x, top, 8, w, min(head, 4.0), 7, rot=((x + w / 2, top, 11.5), "y", 22.5)))
                elif pick < 0.65:
                    els += _octagon("y", top, top + 1.4, x + 2.4, 11, 4.4, "#cable", bars=4)
                w += rng.uniform(1, 4)
            x += w + rng.uniform(0.3, 1.6)
    return els


def _shelf_models() -> None:
    """Grey steel shelving, 2 blocks tall: angle posts, four decks with a front lip, cross
    bracing at the back, and on the decks what VARIANT says."""
    frame = []
    for x in (0.4, 14.4):
        for z in (3.4, 14.6):
            frame.append(_plain_box((x, 0, z), (x + 1.2, 16, z + 1.2), "#shelf"))
            frame.append(_plain_box((x, 16, z), (x + 1.2, 32, z + 1.2), "#shelf"))
    for y in SHELF_DECKS:
        frame.append(_plain_box((0.2, y, 3.2), (15.8, y + 0.8, 15.9), "#shelf"))
        frame.append(_plain_box((0.2, y - 0.8, 3.0), (15.8, y + 0.8, 3.4), "#shelf"))
    for y0 in (8, 24):   # X bracing across the back
        for ang in (45, -45):
            frame.append(_box((1.4, y0, 15.5), (14.6, y0 + 0.5, 15.8), _faces("#shelf"), rot=((8, y0 + 0.25, 15.65), "z", ang)))
    tex = {"particle": _ref("l2_shelf_steel"), "shelf": _ref("l2_shelf_steel"), "carton": _ref("l2_carton"),
           "carton_top": _ref("l2_carton_top"), "toolbox": _ref("l2_toolbox"), "tin": _ref("l2_tin"),
           "jerry": _ref("l2_jerrycan"), "bolt": _ref("l2_bolt_steel"), "cable": _ref("l2_cables")}
    for v in range(4):
        lower, upper = _split_halves(frame + _shelf_items(v))
        _bm(f"l2_shelf_{v}_lower", P._model(tex, lower))
        _bm(f"l2_shelf_{v}_upper", P._model(tex, upper))
    _tall_state("l2_shelf", lambda half: [(f"variant={v}", f"l2_shelf_{v}_{half}") for v in range(4)])
    _im("l2_shelf", parent=_ref("l2_shelf_0_lower"))


def _workbench_models() -> None:
    els = []
    for x in (0.6, 14.0):
        for z in (1.6, 13.6):
            els.append(_plain_box((x, 0, z), (x + 1.4, 13, z + 1.4), "#steel"))
    els.append(_plain_box((0.6, 11.6, 1.6), (15.4, 13, 2.6), "#steel"))
    els.append(_plain_box((0.6, 11.6, 14.0), (15.4, 13, 15.0), "#steel"))
    els.append(_plain_box((0.8, 3.0, 2.0), (15.2, 3.8, 14.6), "#steel"))                # bottom shelf
    els.append(_box((0, 13, 1), (16, 15, 16), _faces("#wood")))
    els.append(_box((9.0, 6.4, 2.2), (15.0, 13, 14.6), _faces("#steel")))               # drawer unit
    for y0 in (6.8, 9.9):
        els.append(_plain_box((9.3, y0, 1.8), (14.7, y0 + 2.8, 2.2), "#steel"))
        els.append(_plain_box((11.0, y0 + 1.9, 1.3), (13.0, y0 + 2.3, 1.8), "#bolt"))
    els.append(_carton(1.6, 3.8, 5.0, 6.0, 5.0, 8.0))
    # the vice on the front left corner
    els.append(_plain_box((1.5, 15, 1.6), (5.5, 15.8, 5.8), "#vise"))
    els.append(_plain_box((2.0, 15.8, 1.4), (5.0, 18.0, 5.2), "#vise"))
    els.append(_plain_box((1.8, 16.2, 0.4), (5.2, 18.4, 1.4), "#bolt"))
    els.append(_plain_box((3.2, 16.8, -1.6), (3.8, 17.4, 0.4), "#bolt"))
    els.append(_plain_box((1.2, 16.8, -1.4), (5.8, 17.2, -1.0), "#bolt"))
    # tools left on top: a big spanner, a hammer, a tin of bolts
    els.append(_box((7.0, 15, 4.0), (12.5, 15.4, 5.0), _faces("#bolt"), rot=((9.7, 15.2, 4.5), "y", 22.5)))
    els.append(_box((6.6, 15, 3.7), (7.8, 15.5, 5.3), _faces("#bolt"), rot=((9.7, 15.2, 4.5), "y", 22.5)))
    els.append(_plain_box((8.5, 15, 8.4), (14.0, 15.6, 9.0), "#wood"))
    els.append(_plain_box((12.6, 15, 7.4), (13.6, 16.2, 10.0), "#bolt"))
    els += _tin(4.0, 15, 10.5, d=3.0, h=2.2)
    tex = {"particle": _ref("l2_bench_steel"), "steel": _ref("l2_bench_steel"), "wood": _ref("l2_bench_top"),
           "bolt": _ref("l2_bolt_steel"), "vise": _ref("l2_vise"), "tin": _ref("l2_tin"),
           "carton": _ref("l2_carton"), "carton_top": _ref("l2_carton_top")}
    _bm("l2_workbench", P._model(tex, els))
    P._facing_state("l2_workbench")
    _im("l2_workbench")


def _cells(elements, auto_split):
    """Share a 32-unit (2 x 2 x 2 block) model out into its eight cells. Boxes in auto_split are
    cut at the cell borders (their inner faces dropped); everything else goes whole to the cell
    its centre is in. Returns {(lx, ly, lz): elements in that block's own coordinates}."""
    cells = {(x, y, z): [] for x in (0, 1) for y in (0, 1) for z in (0, 1)}
    for e in elements:
        c = [max(0, min(int(((e["from"][i] + e["to"][i]) / 2) // 16), 1)) for i in range(3)]
        cells[tuple(c)].append(_shift(e, (-16 * c[0], -16 * c[1], -16 * c[2])))
    for e in auto_split:
        f, t = e["from"], e["to"]
        for cx in (0, 1):
            for cy in (0, 1):
                for cz in (0, 1):
                    lo = [max(f[i], 16 * c) for i, c in enumerate((cx, cy, cz))]
                    hi = [min(t[i], 16 * c + 16) for i, c in enumerate((cx, cy, cz))]
                    if any(hi[i] - lo[i] <= 1e-6 for i in range(3)):
                        continue
                    piece = json_copy(e)
                    piece["from"], piece["to"] = _r(lo), _r(hi)
                    for face in list(piece["faces"]):
                        axis, side = {"north": (2, 0), "south": (2, 1), "west": (0, 0), "east": (0, 1),
                                      "down": (1, 0), "up": (1, 1)}[face]
                        cut = (lo[axis] > f[axis] + 1e-6) if side == 0 else (hi[axis] < t[axis] - 1e-6)
                        if cut:
                            del piece["faces"][face]
                        else:
                            piece["faces"][face]["uv"] = _r(P._auto_uv(face, lo, hi))
                    cells[(cx, cy, cz)].append(_shift(piece, (-16 * cx, -16 * cy, -16 * cz)))
    return cells


def _pump_models() -> None:
    """The pump set (2 x 2 x 2 blocks, front to the north): on a concrete plinth, an end-suction
    pump (blue cast casing, suction from the west) coupled through an orange guard to a green
    motor; the discharge rises from the casing through a gate valve and a gauge to leave the top
    of the machine at the middle of the top-front-west block (where a LARGE CENTER l2_pipe joins
    it); behind, the starter cabinet and a red expansion tank piped across to the riser."""
    split = [_box((1, 0, 1), (31, 3, 31), _faces("#plinth")),
             _box((2, 3, 2.5), (30, 4.4, 19.5), _faces("#steel")),
             _box((19, 4.4, 19), (30, 14, 30), _faces("#encl")),
             _box((19, 14, 19), (30, 26, 30), _faces("#encl", north=("#starter", [0, 0, 16, 16]))),
             _box((18.6, 26, 18.6), (30.4, 26.6, 30.4), _faces("#encl"))]
    els = []
    # pump casing, suction and discharge
    els += _octagon("x", 4, 12, 11, 10, 14, "#casing")
    els += _octagon("x", 12, 13.6, 11, 10, 11, "#casing")
    els += _octagon("x", 0.6, 4, 11, 10, 7, "#steel")
    els += _octagon("x", 1.2, 2.2, 11, 10, 9.6, "#flange")
    els.append(_plain_box((5, 4.4, 6), (11, 6, 14), "#casing"))
    els += _octagon("y", 15, 19.2, 8, 8, 8, "#casing")
    els += _octagon("y", 19.2, 20.2, 8, 8, 13, "#flange")
    els += _octagon("y", 20.2, 32, 8, 8, 10, "#steel")
    els += _octagon("y", 22.2, 23.2, 8, 8, 13, "#flange")
    els += _octagon("y", 23.2, 26.2, 8, 8, 12, "#casing")
    els += _octagon("y", 26.2, 27.2, 8, 8, 13, "#flange")
    els.append(_plain_box((6.4, 23.4, 0.8), (9.6, 26.0, 3.2), "#casing"))       # valve bonnet to the front
    els.append(_plain_box((7.6, 24.3, -0.6), (8.4, 25.1, 0.8), "#bolt"))
    els += _ring("z", 24.7, 8, -1.2, -0.2, 4.0, 1.0, "#wheel")
    for ang in (45, -45):
        els.append(_box((4.4, 24.2, -1.0), (11.6, 25.2, -0.4), _faces("#wheel"), rot=((8, 24.7, -0.7), "z", ang)))
    els += _octagon("z", 1.8, 3.2, 29.6, 8, 1.6, "#steel", bars=2)               # gauge on the riser
    els += _octagon("z", 0.0, 1.8, 29.6, 8, 4.4, "#bolt")
    els.append(_box((5.8, 27.4, -0.06), (10.2, 31.8, 0.0), {"north": ("#dial", [0, 0, 16, 16])}))
    # coupling guard and motor
    els.append(_plain_box((13.6, 4.4, 4.5), (17.6, 16.0, 15.5), "#guard"))
    els += _octagon("x", 17.6, 28.6, 11, 10, 11.5, "#motor")
    for x in (19.0, 21.4, 23.8, 26.2):
        els += _octagon("x", x, x + 0.8, 11, 10, 12.6, "#motor", bars=4)
    els += _octagon("x", 28.6, 30.6, 11, 10, 10.4, "#motor")
    els.append(_plain_box((20.0, 16.4, 6.6), (25.4, 19.2, 12.0), "#motor"))
    els.append(_plain_box((19.0, 4.4, 5.0), (27.6, 6.0, 15.0), "#motor"))
    els += _octagon("y", 19.2, 21.6, 22.7, 12.6, 1.8, "#conduit", bars=2)
    els += _octagon("z", 12.6, 19.0, 21.6, 22.7, 1.8, "#conduit", bars=2)
    # expansion tank behind the pump, piped forward into the riser
    els += _octagon("y", 4.4, 24.0, 8, 24.5, 10, "#tank")
    els += _octagon("y", 24.0, 25.4, 8, 24.5, 7, "#tank")
    els += _octagon("y", 25.4, 29.6, 8, 24.5, 3.0, "#copper", bars=2)
    els += _octagon("z", 13.0, 24.5, 28.8, 8, 3.0, "#copper", bars=2)
    els += _octagon("z", 12.6, 13.6, 28.8, 8, 4.0, "#bolt", bars=2)
    cells = _cells(els, split)
    tex = {"particle": _ref("l2_motor"), "plinth": _ref("l2_plinth"), "steel": _ref("l2_pipe_steel"),
           "casing": _ref("l2_vise"), "flange": _ref("l2_pipe_flange"), "bolt": _ref("l2_bolt_steel"),
           "wheel": _ref("pipe_valve"), "dial": _ref("l2_gauge_dial"), "guard": _ref("l2_guard"),
           "motor": _ref("l2_motor"), "conduit": _ref("l2_pipe_conduit"), "tank": _ref("l2_tank"),
           "copper": _ref("l2_pipe_copper"), "encl": _ref("l2_enclosure"), "starter": _ref("l2_starter")}
    variants = {}
    for (lx, ly, lz), part_els in cells.items():
        part = lx + 2 * lz + 4 * ly
        _bm(f"l2_pump_{part}", P._model(tex, part_els))
        for f, y in FACING_Y.items():
            v = {"model": _ref(f"l2_pump_{part}")}
            if y:
                v["y"] = y
            variants[f"facing={f},part={part}"] = v
    _bs("l2_pump", {"variants": variants})
    _im("l2_pump", parent=_ref("l2_pump_0"))


def _fan_models() -> None:
    els = [_plain_box((0, 0, 1), (16, 1.2, 16), "#duct"), _plain_box((0, 14.8, 1), (16, 16, 16), "#duct"),
           _plain_box((0, 1.2, 1), (1.2, 14.8, 16), "#duct"), _plain_box((14.8, 1.2, 1), (16, 14.8, 16), "#duct"),
           _box((1.2, 1.2, 15), (14.8, 14.8, 16), _faces("#dark")),
           _box((0, 0, 0), (16, 16, 1), _faces("#duct", north=("#grille", [0, 0, 16, 16]), south=("#grille", [16, 0, 0, 16])))]
    els += _octagon("z", 6.2, 12.0, 8, 8, 4.4, "#bolt")
    els.append(_plain_box((1.2, 7.6, 10.4), (14.8, 8.4, 11.2), "#duct"))
    els.append(_plain_box((7.6, 1.2, 10.4), (8.4, 14.8, 11.2), "#duct"))
    els.append(_box((1, 1, 5.4), (15, 15, 5.8), {"north": ("#blades", [0, 0, 16, 16]), "south": ("#blades", [16, 0, 0, 16])},
                    rot=((8, 8, 5.6), "z", 22.5)))
    tex = {"particle": _ref("l2_duct"), "duct": _ref("l2_duct"), "grille": _ref("l2_fan_grille"),
           "blades": _ref("l2_fan_blades"), "bolt": _ref("l2_bolt_steel"), "dark": _ref("l2_void_black")}
    _bm("l2_fan_housing", P._model(tex, els))
    P._facing_state("l2_fan_housing")
    _im("l2_fan_housing")


def _frame_models() -> None:
    """Bulkhead ring pieces: a solid steel block, the opening side painted with hazard stripes
    and a lip standing 2 px proud of it; the ring's faces (along the corridor) riveted plate
    with the stripe band along the opening edge."""
    hz = "#hazard"
    flip = [16, 0, 0, 16]
    lip = {f: (hz, None) for f in FACES}
    models = {
        "side": [_box((0, 0, 0), (16, 16, 16), _faces("#plate", north=hz, east="#side", west=("#side", flip))),
                 _box((4, 0, -2), (12, 16, 0), dict(lip))],
        "top": [_box((0, 0, 0), (16, 16, 16), _faces("#plate", down=hz, east="#top", west="#top")),
                _box((4, -2, 0), (12, 0, 16), dict(lip))],
        "corner": [_box((0, 0, 0), (16, 16, 16), _faces("#plate", east="#corner", west=("#corner", flip)))],
    }
    for e in models["side"][1:] + models["top"][1:]:
        for fd in e["faces"].values():
            fd.pop("cullface", None)
    tex = {"particle": _ref("l2_frame_plate"), "plate": _ref("l2_frame_plate"), "hazard": _ref("l2_hazard"),
           "side": _ref("l2_frame_side"), "top": _ref("l2_frame_top"), "corner": _ref("l2_frame_corner")}
    for part, els in models.items():
        for e in els:
            for face, fd in e["faces"].items():
                if fd["texture"] in ("#side", "#top", "#corner") and fd["uv"] != flip:
                    fd["uv"] = [0, 0, 16, 16]
        _bm(f"l2_bulkhead_frame_{part}", P._model(tex, els))
    P._facing_state("l2_bulkhead_frame", extra=("part", ("side", "top", "corner"), lambda p: f"l2_bulkhead_frame_{p}"))
    _im("l2_bulkhead_frame", parent=_ref("l2_bulkhead_frame_side"))


def _door_models() -> None:
    P._door("l2_steel_door")
    _im("l2_steel_door", layer=_ref("l2_steel_door_bottom"))
    # EXIT bulkhead: in place of the wall; door leaf recessed 1 px, a hazard-striped steel
    # frame standing 1.5 px proud of the wall round it.
    for half in ("lower", "upper"):
        leaf = _ref("l2_exit_bulkhead_bottom" if half == "lower" else "l2_exit_bulkhead_top")
        els = [_box((0, 0, 1), (16, 16, 16), _faces("#wall", north=("#leaf", [0, 0, 16, 16])))]
        for x0, x1 in ((0, 2.2), (13.8, 16)):
            els.append(_box((x0, 0, -1.5), (x1, 16, 1), _faces("#frame", north=("#hazard", None), up=None, down=None)))
        if half == "upper":
            els.append(_box((2.2, 13.4, -1.5), (13.8, 16, 1), _faces("#frame", north=("#hazard", None), up=None)))
        else:
            els.append(_plain_box((2.2, 0, -0.6), (13.8, 0.6, 1), "#bolt"))
            for y0 in (2.5, 11.0):
                els.append(_plain_box((1.6, y0, -0.4), (2.8, y0 + 2.5, 1.0), "#bolt"))
        for e in els:
            for fd in e["faces"].values():
                if e["from"][2] < 0:
                    fd.pop("cullface", None)
        tex = {"particle": leaf, "leaf": leaf, "wall": _ref("pipe_wall"), "frame": _ref("l2_frame_plate"),
               "hazard": _ref("l2_hazard"), "bolt": _ref("l2_bolt_steel")}
        _bm(f"l2_exit_bulkhead_{half}", P._model(tex, els))
    _bs("l2_exit_bulkhead", {"variants": {f"facing={f},half={h}": ({"model": _ref(f"l2_exit_bulkhead_{h}"), "y": y} if y else
                                                                   {"model": _ref(f"l2_exit_bulkhead_{h}")})
                                          for f, y in FACING_Y.items() for h in ("lower", "upper")}})
    _im("l2_exit_bulkhead", layer=_ref("l2_exit_bulkhead_top"))


def _stencil_models() -> None:
    variants = {}
    for k, name in enumerate(STENCIL_NAMES):
        r, c = divmod(k, 4)
        tex = _ref("l2_stencils")
        _bm(f"l2_stencil_{name}", P._model({"particle": tex, "paint": tex},
                                           [_box((0, 0, 15.95), (16, 16, 16), {"north": ("#paint", [4 * c, 4 * r, 4 * c + 4, 4 * r + 4])})]))
        for f, y in FACING_Y.items():
            v = {"model": _ref(f"l2_stencil_{name}")}
            if y:
                v["y"] = y
            variants[f"facing={f},variant={name}"] = v
    _bs("l2_stencil", {"variants": variants})
    _im("l2_stencil", parent=_ref("l2_stencil_sec_b14"))


def _floor_models() -> None:
    g = "#grating"
    _bm("l2_grating", P._model({"particle": _ref("l2_grating"), "grating": _ref("l2_grating")},
                               [_box((0, 13, 0), (16, 16, 16), {f: (g, None) for f in FACES})]))
    P._axis_state("l2_grating")
    _im("l2_grating")
    els = [_box((0, 0, 0), (16, 12, 16), _faces("#floor", up=("#channel", None))),
           _box((0, 12, 0), (16, 16, 5), _faces("#floor", down=None)),
           _box((0, 12, 11), (16, 16, 16), _faces("#floor", down=None)),
           _plain_box((0, 15.4, 4.4), (16, 16.02, 5.2), "#bolt"),
           _plain_box((0, 15.4, 10.8), (16, 16.02, 11.6), "#bolt")]
    _bm("l2_gutter", P._model({"particle": _ref("pipe_floor"), "floor": _ref("pipe_floor"),
                               "channel": _ref("l2_gutter"), "bolt": _ref("l2_bolt_steel")}, els))
    P._axis_state("l2_gutter")
    _im("l2_gutter")
    els = [_box((1, 15.94, 1), (15, 16, 15), {"down": ("#stain", [1, 1, 15, 15])}),
           _plain_box((7.3, 14.6, 7.3), (8.7, 16, 8.7), "#nub"),
           _plain_box((7.55, 13.4, 7.55), (8.45, 14.6, 8.45), "#water")]
    _bm("l2_drip", P._model({"particle": _ref("l2_drip_stain"), "stain": _ref("l2_drip_stain"),
                             "nub": _ref("l2_plinth"), "water": _ref("l2_water")}, els))
    P._plain_state("l2_drip")
    _im("l2_drip")


def _mirror_z(elements):
    """The same parts the other way round (z -> 16 - z): north and south faces swap."""
    out = []
    for e in json_copy(elements):
        f, t = e["from"], e["to"]
        e["from"], e["to"] = [f[0], f[1], 16 - t[2]], [t[0], t[1], 16 - f[2]]
        if "rotation" in e:
            o = e["rotation"]["origin"]
            e["rotation"]["origin"] = [o[0], o[1], 16 - o[2]]
            if e["rotation"]["axis"] in ("x", "y"):
                e["rotation"]["angle"] = -e["rotation"]["angle"]
        faces = e["faces"]
        faces["north"], faces["south"] = faces.pop("south", None), faces.pop("north", None)
        e["faces"] = {k: v for k, v in faces.items() if v is not None}
        for k, fd in e["faces"].items():
            if fd.get("cullface") in ("north", "south"):
                fd["cullface"] = k
        out.append(e)
    return out


def _old_block_models() -> None:
    """The older Level 2 blocks, remade to match: the caged bulkhead lamp, the rusty pipe rack
    (now three steel pipes on a strut, against the south wall) and the pale duct (now a round
    insulated main with saddles, bands and a tear when FLANGE)."""
    cage = "#cage"
    els = [_plain_box((4.6, 2.6, 15.2), (11.4, 13.4, 16), cage),
           _plain_box((5.0, 3.0, 12.4), (11.0, 5.0, 15.2), cage),
           _plain_box((5.0, 11.0, 12.4), (11.0, 13.0, 15.2), cage),
           _plain_box((5.7, 5.0, 11.0), (10.3, 11.0, 14.6), "#bulb")]
    for x0 in (5.2, 7.7, 10.2):
        els.append(_plain_box((x0, 4.8, 10.4), (x0 + 0.6, 11.2, 11.0), cage))
    for y0 in (6.6, 9.0):
        els.append(_plain_box((5.2, y0, 10.4), (10.8, y0 + 0.5, 11.0), cage))
        els.append(_plain_box((5.2, y0, 11.0), (5.8, y0 + 0.5, 12.4), cage))
        els.append(_plain_box((10.2, y0, 11.0), (10.8, y0 + 0.5, 12.4), cage))
    els += _octagon("y", 13.0, 16.0, 8, 15.0, 1.6, "#cage", bars=2)
    _bm("bulkhead_lamp", P._model({"particle": _ref("bulkhead_cage"), "cage": _ref("bulkhead_cage"),
                                   "bulb": _ref("bulkhead_lamp")}, els, ao=False))
    tex = {"particle": _ref("l2_pipe_steel"), "pipe": _ref("l2_pipe_steel"), "strut": _ref("l2_strut"),
           "clamp": _ref("l2_clamp"), "bolt": _ref("l2_bolt_steel"), "cap": _ref("l2_pipe_cap_steel")}
    d = SIZES["small"][0]
    pipes = []
    for i, (pc, qc) in enumerate(_centres("small", "wall")):
        pipes += _octagon("x", 0, 16, pc, qc, d, "#pipe", vslot=3 * i, bars=_bars(d), cap="#cap")
    _bm("rusty_pipe", P._model(tex, _mirror_z(pipes)))
    _bm("rusty_pipe_bracket", P._model(tex, _mirror_z(pipes + _supports("x", "small", "wall"))))
    duct = {"particle": _ref("pale_duct"), "pipe": _ref("pale_duct"), "band": _ref("pale_duct_flange"),
            "torn": _ref("l2_pipe_torn"), "strut": _ref("l2_strut"), "cap": _ref("l2_pipe_cap_insulated")}
    for name, flange in (("pale_duct", False), ("pale_duct_flange", True)):
        els = _octagon("x", 0, 16, 6.5, 9.5, 12, "#pipe", flats={"-q": "#torn"} if flange else None, cap="#cap")
        if flange:
            for t0 in (2.6, 12.6):
                els += _octagon("x", t0, t0 + 1.0, 6.5, 9.5, 12.5, "#band", cap="#cap")
            _label_uv(els)
        else:
            els.append(_plain_box((6.5, 0, 5.0), (9.5, 1.2, 14.6), "#strut"))
        _bm(name, P._model(duct, els))


def _mirror_x(elements):
    """The same parts the other way round (x -> 16 - x): east and west faces swap."""
    out = []
    for e in json_copy(elements):
        f, t = e["from"], e["to"]
        e["from"], e["to"] = [16 - t[0], f[1], f[2]], [16 - f[0], t[1], t[2]]
        if "rotation" in e:
            o = e["rotation"]["origin"]
            e["rotation"]["origin"] = [16 - o[0], o[1], o[2]]
            if e["rotation"]["axis"] in ("y", "z"):
                e["rotation"]["angle"] = -e["rotation"]["angle"]
        faces = e["faces"]
        faces["east"], faces["west"] = faces.pop("west", None), faces.pop("east", None)
        e["faces"] = {k: v for k, v in faces.items() if v is not None}
        for k, fd in e["faces"].items():
            if fd.get("cullface") in ("east", "west"):
                fd["cullface"] = k
        out.append(e)
    return out


def _bead(kind, d):
    """The joint ring where a pipe bends: a weld bead on steel and paint, a band on insulation,
    a soldered sleeve on copper, a coupling on conduit. (texture, width, bars, cap)"""
    tex, cap = {"insulated": ("#band", "#cap"), "copper": ("#joint", "#cap"), "conduit": ("#joint", "#cap")}.get(kind, ("#bolt", None))
    return tex, (0.5 if tex == "#bolt" else 1.0), (_bars(d + 0.5) if cap else 2), cap


def _wall_collar(axis, t_wall, pc, qc, d, inward):
    """Where a pipe goes into concrete: a square steel collar plate on the face (bolted at its
    corners) and a flange ring standing off it. t_wall = the face (0 or 16 along axis);
    inward = +1 if the face is at 16."""
    s = d / 2 + 1.3
    t0, t1 = (t_wall - 0.8, t_wall) if inward > 0 else (t_wall, t_wall + 0.8)
    els = [_frame_box(axis, t0, t1, pc - s, pc + s, qc - s, qc + s, "#collar", vslot=1)]
    r0, r1 = (t_wall - 1.8, t_wall - 0.8) if inward > 0 else (t_wall + 0.8, t_wall + 1.8)
    els += _octagon(axis, r0, r1, pc, qc, d + 1.6, "#collar", bars=_bars(d + 1.6), cap="#cap")
    b0, b1 = (t_wall - 1.1, t_wall - 0.8) if inward > 0 else (t_wall + 0.8, t_wall + 1.1)
    for dp in (-1, 1):
        for dq in (-1, 1):
            bp, bq = pc + dp * (s - 0.7), qc + dq * (s - 0.7)
            els.append(_frame_box(axis, b0, b1, bp - 0.35, bp + 0.35, bq - 0.35, bq + 0.35, "#bolt", vslot=2))
    return els


def _pipe_end_elements(kind, size, form):
    """The end of a wall rack (block Level2PipeEndBlock), built facing north: wall on the south
    side (z = 16), pipes along x stacked up the wall as in the l2_pipe rack it finishes.
    bend: arriving from the west (x = 0) each pipe turns 90 degrees into the wall through a collar.
    collar: it runs straight on into the solid block on the east (x = 16) through a collar.
    riser: pipes standing up the wall from below bend into it.
    hung: a run hung under the ceiling (pipes along x, across z) arriving from the west turns up
    into the slab through a collar. The _cw forms are mirrored."""
    d = SIZES[size][0] + FATTER.get(kind, 0)
    _, off, spread = SIZES[size]
    r = d / 2
    h = r / math.sqrt(2)
    bars = _bars(d)
    zc = 16 - off
    base = form.replace("_cw", "").replace("_ccw", "")
    bead, bw, bb, bcap = _bead(kind, d)
    corner = {f: ("#pipe", [0, 6, 16, 10]) for f in FACES}
    els = []
    for i, sp in enumerate(spread):
        if base == "riser":
            # (along y: x = p, z = q) the pipe at x = sp climbs to yb, then turns into the wall
            yb = 8.0 if len(spread) == 1 else 6.5 + 2.5 * i
            els += _octagon("y", 0, yb, sp, zc, d, "#pipe", vslot=3 * i, bars=bars, cap="#cap")
            els += _octagon("z", zc, 16, yb, sp, d, "#pipe", vslot=3 * i + 1, bars=bars, cap="#cap")
            els.append(_box((sp - r, yb - h, zc - h), (sp + r, yb + h, zc + h), corner, rot=((sp, yb, zc), "x", 45), shade=False))
            els += _octagon("y", yb - r - bw - 0.2, yb - r - 0.2, sp, zc, d + 0.5, bead, bars=bb, cap=bcap)
            els += _wall_collar("z", 16, yb, sp, d, 1)
        elif base == "bend":
            # (along x: y = p, z = q) the pipe at height sp runs from the west to xb, then into the wall
            xb = 8.0 if len(spread) == 1 else 6.8 + 1.4 * i
            els += _octagon("x", 0, xb, sp, zc, d, "#pipe", vslot=3 * i, bars=bars, cap="#cap")
            els += _octagon("z", zc, 16, sp, xb, d, "#pipe", vslot=3 * i + 1, bars=bars, cap="#cap")
            els.append(_box((xb - h, sp - r, zc - h), (xb + h, sp + r, zc + h), corner, rot=((xb, sp, zc), "y", 45), shade=False))
            els += _octagon("x", xb - r - bw - 0.2, xb - r - 0.2, sp, zc, d + 0.5, bead, bars=bb, cap=bcap)
            els += _wall_collar("z", 16, sp, xb, d, 1)
        elif base == "hung":
            # (along x: y = p, z = q) the pipe at z = sp, hanging at height yc, runs from the west to xb, then up
            yc = 16 - HANG[size]
            xb = 8.0 if len(spread) == 1 else 7.0 + 2.0 * i
            els += _octagon("x", 0, xb, yc, sp, d, "#pipe", vslot=3 * i, bars=bars, cap="#cap")
            els += _octagon("y", yc, 16, xb, sp, d, "#pipe", vslot=3 * i + 1, bars=bars, cap="#cap")
            els.append(_box((xb - h, yc - h, sp - r), (xb + h, yc + h, sp + r), corner, rot=((xb, yc, sp), "z", 45), shade=False))
            els += _octagon("x", xb - r - bw - 0.2, xb - r - 0.2, yc, sp, d + 0.5, bead, bars=bb, cap=bcap)
            els += _wall_collar("y", 16, xb, sp, d, 1)
        else:
            els += _octagon("x", 0, 16, sp, zc, d, "#pipe", vslot=3 * i, bars=bars, cap="#cap")
            els += _wall_collar("x", 16, sp, zc, d, 1)
    return _mirror_x(els) if form.endswith("_cw") else els


def _pipe_end_models() -> None:
    forms = ("bend_ccw", "bend_cw", "collar_ccw", "collar_cw", "riser", "hung_ccw", "hung_cw")
    variants = {}
    for kind in KINDS:
        tex = {**_pipe_textures(kind), "collar": _ref("l2_pipe_flange")}
        for size in SIZES:
            for form in forms:
                name = f"l2_pipe_end_{kind}_{size}_{form}"
                _bm(name, P._model(tex, _pipe_end_elements(kind, size, form)))
                for f, y in FACING_Y.items():
                    v = {"model": _ref(name)}
                    if y:
                        v["y"] = y
                    variants[f"facing={f},form={form},kind={kind},size={size}"] = v
    _bs("l2_pipe_end", {"variants": variants})
    _im("l2_pipe_end", parent=_ref("l2_pipe_end_steel_medium_bend_ccw"))


def _puddle_model() -> None:
    """The film of dirty water under a leak (block l2_leak_puddle): the wet stain decal lying on
    the floor, a hair above it, turned a random quarter each block so a spread doesn't repeat."""
    els = [_box((0.3, 0.02, 0.3), (15.7, 0.12, 15.7), {"up": ("#stain", [0.3, 0.3, 15.7, 15.7])})]
    _bm("l2_leak_puddle", P._model({"particle": _ref("l2_drip_stain"), "stain": _ref("l2_drip_stain")}, els))
    _bs("l2_leak_puddle", {"variants": {"": [{"model": _ref("l2_leak_puddle"), **({"y": y} if y else {})}
                                             for y in (0, 90, 180, 270)]}})
    _im("l2_leak_puddle", layer=_ref("l2_drip_stain"))


def tex_level2_revamp_models() -> None:
    """Block models, blockstates and item models for registry/Level2Blocks (LF JSON), and the
    remade models of the older Level 2 blocks (their blockstates are unchanged)."""
    _surface_models()
    _pipe_models()
    _beam_model()
    _pipe_end_models()
    _fitting_models()
    _valve_models()
    _lever_models()
    _feed_models()
    _gauge_models()
    _cable_tray_models()
    _puddle_model()
    _junction_box_models()
    _breaker_models()
    _shelf_models()
    _workbench_models()
    _pump_models()
    _fan_models()
    _frame_models()
    _door_models()
    _stencil_models()
    _floor_models()
    _old_block_models()


TEXTURES = [tex_level2_revamp_props, tex_level2_revamp_models]
