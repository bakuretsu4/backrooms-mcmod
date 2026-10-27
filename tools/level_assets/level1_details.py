"""Level 1 at the "spacious" scale: the details of a real parking garage / warehouse.

Bevelled 3x3 pillars with big numbers on a band in the sector's colour, garage signs, parking
markings on the floor (stall lines and numbers, lane dashes, arrows, SLOW, drains, oil, tyre marks),
wheel stops and speed bumps, the ceiling services (red sprinkler main and heads, conduit, a cable
tray, a big duct, hanging fluorescent fixtures), extinguisher cabinets and convex mirrors on the
pillars, and steel pallet racking with pallets, shrink-wrapped stacks and loose boxes.

Textures, and the block models and blockstates (most of these blocks are shaped, not cubes).

Run only these with:  python tools/gen_assets.py l1x_
"""
from __future__ import annotations

import json

import numpy as np
from PIL import Image, ImageDraw, ImageFont

import gen_assets as G

S = G.S
FONT = G.Path(G.__file__).resolve().parent / "fonts" / "Anton-Regular.ttf"
ASSETS = G.TEX.parent
LABELS = (1, 2, 3, 5, 6, 9, 12, 99)                 # PillarNumberBlock.LABELS
# The sector colours (Level1Areas.AQUILA, GILD, RUST): blue parking, warehouse yellow, oxide red.
SECTOR_PAINT = ((38, 86, 160), (222, 166, 34), (150, 52, 40))
SECTOR_INK = ((242, 244, 240), (24, 22, 20), (240, 232, 220))
FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


def _blk(name: str):
    return G.TEX / "block" / f"{name}.png"


def _load(name: str) -> np.ndarray:
    return np.asarray(Image.open(_blk(name)).convert("RGB"), float)


def _font(size: int):
    return ImageFont.truetype(str(FONT), size)


def _paint(base, color, mask, rng, wear=0.35, alpha=0.95) -> np.ndarray:
    """Paint over a surface, worn: chipped where the noise is high, thinner along the edges."""
    shape = mask.shape
    chip = G.smoothstep(0.6, 1.6, G.fnoise(rng, shape, beta=1.8, kmin=6)) * wear
    a = np.clip(mask * alpha * (1 - chip), 0, 1)
    img = G.over(base, np.array(color, float), a)
    return G.add_lum(img, G.fnoise(rng, shape, beta=2.0) * 5 * a)


def _rgba_layer(draw_fn, size=(S, S), ss=4) -> np.ndarray:
    im = Image.new("RGBA", (size[0] * ss, size[1] * ss), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(im), ss)
    return np.asarray(im.resize(size, Image.LANCZOS), float)


def _grime(img, rng, amount=0.25, color=(52, 48, 42)) -> np.ndarray:
    g = G.smoothstep(0.2, 1.8, G.fnoise(rng, img.shape[:2], beta=2.3)) * amount
    return G.over(img, np.array(color, float), g)


# ---------------------------------------------------------------------------------------------
# Pillars
# ---------------------------------------------------------------------------------------------

def tex_l1x_pillar_bands() -> None:
    """The sector band on the pillar corners: the whole block painted, worn at random spots."""
    base = _load("concrete_wall")
    for sector, color in enumerate(SECTOR_PAINT):
        rng = G.rng_for(7700 + sector)
        G.save_png(_paint(base, color, np.ones((S, S)), rng, wear=0.3), _blk(f"l1x_band_{sector}"))


def tex_l1x_pillar_numbers() -> None:
    """Big stencilled numbers, two blocks tall, on the band: each label in each sector, split in halves."""
    base = np.concatenate([_load("concrete_wall"), _load("concrete_wall")[::-1]], axis=0)
    for sector, color in enumerate(SECTOR_PAINT):
        for label in LABELS:
            rng = G.rng_for(7710 + sector * 10 + LABELS.index(label))
            img = _paint(base.copy(), color, np.ones((2 * S, S)), G.rng_for(7700 + sector), wear=0.3)

            def draw(d, k, text=str(label)):
                # As big as fits: 200 px tall (of the two blocks' 256), 108 px wide, centred on the
                # glyphs' own bounding box so the split between the blocks falls mid-number.
                size = 260 * k
                while True:
                    f = _font(size)
                    x0, y0, x1, y1 = d.textbbox((0, 0), text, font=f)
                    if x1 - x0 <= 108 * k and y1 - y0 <= 200 * k:
                        break
                    size -= 4 * k
                d.text((64 * k - (x0 + x1) / 2, 128 * k - (y0 + y1) / 2), text, font=f, fill=(255, 255, 255, 255))
            ink = _rgba_layer(draw, (S, 2 * S))[..., 3] / 255.0
            # Stencil bridges: thin gaps across the strokes, like a sprayed stencil.
            Y = np.arange(2 * S)[:, None]
            ink = ink * (1 - 0.9 * ((Y % 64 > 60) & (Y > 40) & (Y < 216)))
            img = _paint(img, SECTOR_INK[sector], ink, rng, wear=0.25, alpha=0.97)
            img = _grime(img, rng, 0.18)
            G.save_png(img[S:], _blk(f"l1x_number_{label}_{sector}_lo"))
            G.save_png(img[:S], _blk(f"l1x_number_{label}_{sector}_hi"))


def tex_l1x_pillar_signs() -> None:
    """Garage signs on a pillar: P1, headroom 2.1 m, no parking, 5 km/h, zone A (blue), zone G (yellow)."""
    base = _load("concrete_wall")
    white, black, blue, red, yellow = (240, 242, 238, 255), (24, 24, 26, 255), (30, 74, 160, 255), (196, 34, 30, 255), (236, 190, 30, 255)

    def plate(d, k, fill):
        d.rounded_rectangle((20 * k, 20 * k, 108 * k, 108 * k), radius=8 * k, fill=fill)
        d.rounded_rectangle((25 * k, 25 * k, 103 * k, 103 * k), radius=5 * k, outline=white, width=3 * k)

    def p1(d, k):
        plate(d, k, blue)
        d.text((64 * k, 64 * k), "P1", font=_font(56 * k), fill=white, anchor="mm")

    def headroom(d, k):
        d.rectangle((14 * k, 34 * k, 114 * k, 94 * k), fill=yellow)
        for i in range(0, 100, 14):                     # hazard edge stripes
            d.polygon([((14 + i) * k, 34 * k), ((21 + i) * k, 34 * k), ((14 + i) * k, 41 * k)], fill=black)
        d.text((64 * k, 64 * k), "2.1 m", font=_font(34 * k), fill=black, anchor="mm")
        for y0, s in ((38, 1), (90, -1)):
            d.polygon([(64 * k, (y0 + s * 0) * k), (58 * k, (y0 - s * 0) * k), (70 * k, y0 * k)], fill=black)
        d.polygon([(24 * k, 50 * k), (30 * k, 42 * k), (36 * k, 50 * k)], fill=black)
        d.polygon([(24 * k, 78 * k), (30 * k, 86 * k), (36 * k, 78 * k)], fill=black)
        d.polygon([(92 * k, 50 * k), (98 * k, 42 * k), (104 * k, 50 * k)], fill=black)
        d.polygon([(92 * k, 78 * k), (98 * k, 86 * k), (104 * k, 78 * k)], fill=black)

    def no_parking(d, k):
        d.ellipse((24 * k, 24 * k, 104 * k, 104 * k), fill=red)
        d.ellipse((32 * k, 32 * k, 96 * k, 96 * k), fill=blue)
        d.line((38 * k, 38 * k, 90 * k, 90 * k), fill=red, width=9 * k)

    def speed(d, k):
        d.ellipse((24 * k, 24 * k, 104 * k, 104 * k), fill=red)
        d.ellipse((33 * k, 33 * k, 95 * k, 95 * k), fill=white)
        d.text((64 * k, 58 * k), "5", font=_font(40 * k), fill=black, anchor="mm")
        d.text((64 * k, 84 * k), "km/h", font=_font(13 * k), fill=black, anchor="mm")

    def zone(fill, ink, letter):
        def f(d, k):
            plate(d, k, fill)
            d.text((64 * k, 50 * k), letter, font=_font(52 * k), fill=ink, anchor="mm")
            d.text((64 * k, 90 * k), "ZONE", font=_font(14 * k), fill=ink, anchor="mm")
        return f

    signs = (p1, headroom, no_parking, speed, zone(blue, white, "A"), zone(yellow, black, "G"))
    for i, fn in enumerate(signs):
        rng = G.rng_for(7740 + i)
        layer = _rgba_layer(fn)
        a = layer[..., 3:] / 255.0
        img = base * (1 - a) + layer[..., :3] * a
        img = _grime(img, rng, 0.22)
        img = G.add_lum(img, -10 * (rng.random((S, S)) < 0.012) * a[..., 0])
        G.save_png(img, _blk(f"l1x_sign_{i}"))


# ---------------------------------------------------------------------------------------------
# Floor
# ---------------------------------------------------------------------------------------------

FLOOR_MARKS = ("line", "arrow", "oil", "tyres", "drain", "dashes", "slow")


def tex_l1x_floor_marks() -> None:
    """Variants of FloorMarkBlock, drawn for FACING = north (the top of the image is north)."""
    base = _load("concrete_floor")
    white, yellow = (226, 226, 214), (214, 170, 42)
    X, Y = G.grid()
    for v in range(7 + 6):
        rng = G.rng_for(7760 + v)
        img = base.copy()
        if v == 0:                                       # stall line along the north edge
            img = _paint(img, white, (Y < 9).astype(float), rng, wear=0.45, alpha=0.85)
        elif v == 1:                                     # driving arrow, pointing north
            def draw(d, k):
                d.polygon([(64 * k, 10 * k), (34 * k, 46 * k), (54 * k, 46 * k), (54 * k, 118 * k),
                           (74 * k, 118 * k), (74 * k, 46 * k), (94 * k, 46 * k)], fill=255)
            img = _paint(img, white, G.pil_mask(draw), rng, wear=0.45, alpha=0.85)
        elif v == 2:                                     # oil stain: dark core, rainbow-less brown halo
            cx, cy = rng.uniform(40, 88, 2)
            r = np.hypot((X - cx) / rng.uniform(0.8, 1.3), Y - cy) / 40 + 0.25 * G.fnoise(rng, beta=2.2)
            core = G.smoothstep(0.75, 0.2, r)
            img = G.over(img, np.array([28, 26, 24.0]), core * 0.75)
            img = G.over(img, np.array([60, 52, 40.0]), G.smoothstep(1.1, 0.6, r) * (1 - core) * 0.35)
        elif v == 3:                                     # tyre marks: two curved rubber streaks east-west
            for off in (40, 88):
                bend = rng.uniform(-10, 10)
                line = off + bend * ((X - 64) / 64) ** 2
                streak = G.smoothstep(7, 3, np.abs(Y - line)) * (0.5 + 0.5 * G.smoothstep(-1, 1, G.fnoise(rng, beta=1.4, aniso=(6, 1))))
                img = G.over(img, np.array([30, 30, 30.0]), streak * 0.45)
        elif v == 4:                                     # drain: a cast-iron grate set in a dished, stained patch
            r = np.hypot(X - 64, Y - 64) / 64
            img = G.over(img, np.array([58, 56, 50.0]), G.smoothstep(1.0, 0.5, r + 0.2 * G.fnoise(rng, beta=2)) * 0.4)
            frame = (np.abs(X - 64) < 40) & (np.abs(Y - 64) < 40)
            slots = frame & (np.abs(X - 64) < 34) & (np.abs(Y - 64) < 34) & ((Y.astype(int) // 6) % 2 == 0)
            img = np.where(frame[..., None], np.array([70, 64, 58.0]) + G.fnoise(rng, beta=2)[..., None] * 8, img)
            img = np.where(slots[..., None], np.array([12, 12, 12.0]), img)
            img = G.over(img, np.array([120, 70, 40.0]), frame * G.smoothstep(0.6, 1.6, G.fnoise(rng, beta=1.6)) * 0.5)
        elif v == 5:                                     # yellow lane dashes running north-south
            img = _paint(img, yellow, ((np.abs(X - 64) < 4) & (Y < 72)).astype(float), rng, wear=0.4, alpha=0.85)
        else:
            def draw(d, k, v=v):
                text = "SLOW" if v == 6 else str((1, 2, 3, 4, 5, 6)[v - 7] + rng.integers(0, 3) * 6)
                f = _font((52 if v == 6 else 70) * k)
                d.text((64 * k, 64 * k), text, font=f, fill=255, anchor="mm")
            # Read from the FACING (north) side: the text's top is towards the south.
            mask = G.pil_mask(draw)[::-1, ::-1]
            img = _paint(img, white if v != 6 else yellow, mask, rng, wear=0.45, alpha=0.85)
        img = G.add_lum(img, G.fnoise(rng, beta=1.5) * 3)
        G.save_png(img, _blk(f"l1x_floor_{v}"))


def tex_l1x_floor_objects() -> None:
    rng = G.rng_for(7780)
    X, Y = G.grid()
    img = _load("concrete_wall") * 0.95
    ends = (X < 26) | (X > 102)
    img = _paint(img, (220, 172, 36), ends.astype(float), rng, wear=0.5)
    G.save_png(_grime(img, rng, 0.3), _blk("l1x_wheel_stop"))
    rng = G.rng_for(7781)
    stripe = ((X + Y) // 32) % 2 == 0
    img = np.where(stripe[..., None], np.array([214, 168, 30.0]), np.array([30, 30, 30.0]))
    img = G.add_lum(img, G.fnoise(rng, beta=2.0) * 10)
    img = G.add_lum(img, -14 * ((Y.astype(int) % 16) < 2))      # moulded ribs
    G.save_png(_grime(img, rng, 0.35, (40, 38, 34)), _blk("l1x_speed_bump"))


# ---------------------------------------------------------------------------------------------
# Ceiling services
# ---------------------------------------------------------------------------------------------

def _metal(rng, color, scratches=0.4, streak=(1, 8)) -> np.ndarray:
    img = G.add_lum(G.fill(color, (S, S)), G.fnoise(rng, beta=2.2, aniso=streak) * 9)
    return G.add_lum(img, -18 * scratches * G.smoothstep(1.4, 2.4, G.fnoise(rng, beta=1.2, aniso=(1, 12))))


def tex_l1x_services() -> None:
    X, Y = G.grid()
    rng = G.rng_for(7790)
    red = _metal(rng, (168, 36, 30), streak=(8, 1))
    red = G.add_lum(red, 22 * G.smoothstep(0.3, 0, np.abs(Y / S - 0.35)))          # sheen along the pipe
    red = _grime(red, rng, 0.25)
    G.save_png(red, _blk("l1x_red_pipe"))
    rng = G.rng_for(7791)
    G.save_png(G.add_lum(_metal(rng, (176, 140, 70), 0.2), 16 * G.smoothstep(0.3, 0, np.abs(Y / S - 0.4))), _blk("l1x_brass"))
    rng = G.rng_for(7792)
    galv = _metal(rng, (150, 154, 156), streak=(8, 1))
    spangle = G.fnoise(rng, beta=0.6) * 6                                           # galvanised spangle
    galv = G.add_lum(galv, spangle)
    G.save_png(_grime(galv, rng, 0.2), _blk("l1x_galvanised"))
    duct = galv.copy()
    duct = G.add_lum(duct, -26 * ((X < 3) | (X > S - 4)))                           # flanged joints at each end
    duct = G.add_lum(duct, 10 * ((X >= 3) & (X < 6)))
    for x0 in (1, S - 3):
        for y0 in range(10, S, 28):
            duct = G.add_lum(duct, -30 * ((np.abs(X - x0) < 1.5) & (np.abs(Y - y0) < 1.5)))
    duct = G.add_lum(duct, 8 * np.sin(Y / S * np.pi * 6) * ((X > 6) & (X < S - 6)))  # stiffening ridges
    G.save_png(_grime(duct, rng, 0.3), _blk("l1x_duct"))
    rng = G.rng_for(7793)
    cables = np.zeros((S, S, 3))
    for i, y0 in enumerate(range(0, S, 9)):
        col = (24, 24, 26) if i % 3 else (64, 66, 70) if i % 2 else (150, 30, 26)
        band = np.abs(Y - y0 - 4) < 4.5 + np.sin(X / S * 6 + i) * 0.6
        shade = 1 - (np.abs(Y - y0 - 4) / 5) ** 2 * 0.5
        cables = np.where(band[..., None], np.array(col, float) * shade[..., None], cables)
    G.save_png(G.add_lum(cables, G.fnoise(rng, beta=2) * 6), _blk("l1x_cables"))
    rng = G.rng_for(7794)
    G.save_png(_grime(_metal(rng, (112, 114, 116)), rng, 0.2), _blk("l1x_conduit"))


def tex_l1x_light() -> None:
    X, Y = G.grid()
    rng = G.rng_for(7800)
    housing = G.add_lum(G.fill((214, 214, 206), (S, S)), G.fnoise(rng, beta=2.2) * 6)
    housing = G.over(housing, np.array([120, 110, 86.0]), G.smoothstep(0.6, 1.8, G.fnoise(rng, beta=2.0, aniso=(6, 1))) * 0.4)
    housing = G.add_lum(housing, -40 * ((Y < 3) | (Y > S - 4)))
    G.save_png(housing, _blk("l1x_light_housing"))
    # The diffuser: bright, a prismatic grid and the two tubes glowing a little brighter through it.
    diff = np.full((S, S, 3), 236.0) + np.array([0, 2, 8.0])
    diff = G.add_lum(diff, 10 * ((np.abs(Y - S * 0.33) < 10) | (np.abs(Y - S * 0.67) < 10)))
    diff = G.add_lum(diff, -12 * (((X.astype(int) % 6) == 0) | ((Y.astype(int) % 6) == 0)))
    diff = G.add_lum(diff, -50 * ((Y < 6) | (Y > S - 7)))
    diff = G.over(diff, np.array([150, 140, 110.0]), G.smoothstep(1.2, 2.2, G.fnoise(rng, beta=1.6)) * 0.4)  # dead insects
    G.save_png(np.clip(diff, 0, 255), _blk("l1x_light_diffuser"))


# ---------------------------------------------------------------------------------------------
# Pillar dressing
# ---------------------------------------------------------------------------------------------

def tex_l1x_dressing() -> None:
    X, Y = G.grid()
    rng = G.rng_for(7810)
    red = _metal(rng, (176, 30, 28), 0.3, streak=(1, 1))
    G.save_png(_grime(red, rng, 0.2), _blk("l1x_cabinet_side"))

    def front(d, k):
        d.rectangle((14 * k, 18 * k, 114 * k, 104 * k), fill=(40, 46, 50, 255))          # window
        d.rounded_rectangle((44 * k, 30 * k, 84 * k, 98 * k), radius=12 * k, fill=(186, 28, 24, 255))  # extinguisher
        d.rectangle((56 * k, 20 * k, 72 * k, 32 * k), fill=(30, 30, 30, 255))
        d.rectangle((48 * k, 50 * k, 80 * k, 72 * k), fill=(232, 230, 220, 255))           # its label
        d.line((60 * k, 22 * k, 88 * k, 40 * k), fill=(20, 20, 20, 255), width=3 * k)      # hose
        d.rectangle((14 * k, 108 * k, 114 * k, 124 * k), fill=(240, 240, 232, 255))
        d.text((64 * k, 116 * k), "FIRE EXTINGUISHER", font=_font(11 * k), fill=(176, 30, 28, 255), anchor="mm")
    layer = _rgba_layer(front)
    a = layer[..., 3:] / 255.0
    img = red * (1 - a) + layer[..., :3] * a
    glass = (X > 14) & (X < 114) & (Y > 18) & (Y < 104)
    img = G.add_lum(img, 26 * glass * G.smoothstep(14, 0, np.abs(X - Y * 0.6 - 30)))   # reflection on the glass
    G.save_png(_grime(img, rng, 0.15), _blk("l1x_cabinet_front"))

    rng = G.rng_for(7811)
    r = np.hypot(X - 64, Y - 64) / 64
    # The convex mirror: a squashed, fish-eye view of a dim garage, a yellow and black rim.
    view = np.full((S, S, 3), 70.0)
    view = G.add_lum(view, -40 * G.smoothstep(0.3, 1.0, r))
    view = G.add_lum(view, 60 * G.smoothstep(0.08, 0.0, np.abs(Y / S - 0.32 - 0.12 * ((X - 64) / 64) ** 2)))  # bent tube light
    view = G.add_lum(view, 30 * ((np.abs(X - 64 - (Y - 64) * 0.3) < 6) & (Y > 64)))                        # a pillar
    view = G.add_lum(view, 45 * G.smoothstep(0.25, 0.0, np.hypot(X / S - 0.36, Y / S - 0.3)))             # highlight
    rim = (r > 0.86) & (r <= 1.0)
    ang = np.arctan2(Y - 64, X - 64)
    rimcol = np.where(((ang * 4 / np.pi) % 2 < 1)[..., None], np.array([220, 176, 30.0]), np.array([28, 28, 28.0]))
    img = np.where(rim[..., None], rimcol, view)
    alpha = (r <= 1.0) * 255.0
    G.save_png(np.dstack([img, alpha]), _blk("l1x_mirror"))
    G.save_png(_grime(_metal(rng, (60, 62, 64)), rng, 0.2), _blk("l1x_bracket"))


# ---------------------------------------------------------------------------------------------
# Pallet racks
# ---------------------------------------------------------------------------------------------

def tex_l1x_racks() -> None:
    X, Y = G.grid()
    rng = G.rng_for(7820)
    blue = _metal(rng, (34, 82, 160), 0.6, streak=(1, 6))
    holes = ((Y.astype(int) % 16) < 6) & (np.abs((X.astype(int) % 32) - 16) < 2.5)        # punched slots
    blue = np.where(holes[..., None], np.array([16, 22, 34.0]), blue)
    blue = G.over(blue, np.array([200, 200, 196.0]), G.smoothstep(1.5, 2.5, G.fnoise(rng, beta=1.2, aniso=(1, 6))) * 0.6)  # scrapes
    G.save_png(blue, _blk("l1x_rack_blue"))
    rng = G.rng_for(7821)
    orange = _metal(rng, (226, 112, 26), 0.8, streak=(8, 1))
    orange = G.over(orange, np.array([90, 60, 40.0]), G.smoothstep(1.2, 2.2, G.fnoise(rng, beta=1.6, aniso=(6, 1))) * 0.5)
    G.save_png(orange, _blk("l1x_rack_orange"))
    rng = G.rng_for(7822)
    wood = G.add_lum(G.fill((172, 140, 96), (S, S)), G.fnoise(rng, beta=1.8, aniso=(10, 1)) * 16)
    wood = G.add_lum(wood, 8 * np.sin(Y / 3.0 + G.fnoise(rng, beta=1.5) * 3))
    wood = _grime(wood, rng, 0.35, (70, 56, 40))
    # The deck: seven boards running east-west with gaps between them (see-through).
    board = (Y.astype(int) % 19) < 14
    nails = ((np.abs(X - 10) < 2) | (np.abs(X - 64) < 2) | (np.abs(X - 118) < 2)) & (np.abs((Y.astype(int) % 19) - 7) < 2)
    top = G.add_lum(wood, -40 * nails)
    G.save_png(np.dstack([top, board * 255.0]), _blk("l1x_pallet_top"))
    G.save_png(G.add_lum(wood, -20 * ((X.astype(int) % 64) < 3)), _blk("l1x_pallet_side"))
    rng = G.rng_for(7823)
    card = G.add_lum(G.fill((176, 136, 88), (S, S)), G.fnoise(rng, beta=2.2) * 8)
    card = G.add_lum(card, 6 * np.sin(Y * 1.2))                                           # corrugation
    box = card.copy()
    box = G.over(box, np.array([200, 176, 120.0]), ((np.abs(Y - 64) < 9)).astype(float) * 0.8)  # tape band
    box = G.add_lum(box, -30 * ((X < 2) | (X > S - 3) | (Y < 2) | (Y > S - 3)))
    G.save_png(_grime(box, rng, 0.2), _blk("l1x_box_side"))

    def stencil(d, k):
        d.text((64 * k, 36 * k), "THIS SIDE UP", font=_font(12 * k), fill=(40, 40, 40, 255), anchor="mm")
        for x0 in (46, 76):
            d.polygon([(x0 * k, 20 * k), ((x0 - 6) * k, 28 * k), ((x0 + 6) * k, 28 * k)], fill=(40, 40, 40, 255))
            d.rectangle(((x0 - 2) * k, 28 * k, (x0 + 2) * k, 30 * k), fill=(40, 40, 40, 255))
        d.text((64 * k, 96 * k), "FRAGILE", font=_font(14 * k), fill=(160, 30, 30, 255), anchor="mm")
    layer = _rgba_layer(stencil)
    a = layer[..., 3:] / 255.0
    G.save_png(box * (1 - a) + layer[..., :3] * a, _blk("l1x_box_front"))
    top = G.over(card, np.array([200, 176, 120.0]), (np.abs(X - 64) < 10).astype(float) * 0.85)
    top = G.add_lum(top, -40 * (np.abs(X - 64) < 0.8))
    G.save_png(_grime(top, rng, 0.2), _blk("l1x_box_top"))
    crushed = G.add_lum(box, 30 * G.fnoise(rng, beta=1.4, aniso=(1, 3)))
    G.save_png(_grime(crushed, rng, 0.4, (90, 70, 48)), _blk("l1x_box_crushed"))
    # Shrink-wrapped stacks: a grid of boxes seen through cloudy film, glints, a pallet label.
    rng = G.rng_for(7824)
    stack = card.copy()
    stack = G.add_lum(stack, -34 * (((Y.astype(int) % 43) < 2) | ((X.astype(int) % 64) < 2)))
    film = 0.28 + 0.12 * G.smoothstep(-1, 1.5, G.fnoise(rng, beta=1.4, aniso=(1, 4)))
    stack = G.over(stack, np.array([226, 230, 232.0]), film)
    stack = G.add_lum(stack, 40 * G.smoothstep(0.5, 0, np.abs(G.fnoise(rng, beta=1.2, aniso=(1, 8)))) * 0.6)
    G.save_png(stack, _blk("l1x_wrap_side"))

    def label(d, k):
        d.rectangle((36 * k, 52 * k, 92 * k, 92 * k), fill=(244, 244, 238, 255))
        for i in range(24):
            w = (1 + (i * 7) % 3) * k
            x0 = (40 + i * 2) * k
            d.rectangle((x0, 72 * k, x0 + w - 1, 88 * k), fill=(20, 20, 20, 255))
        d.text((64 * k, 61 * k), "LOT 1-0447", font=_font(9 * k), fill=(20, 20, 20, 255), anchor="mm")
    layer = _rgba_layer(label)
    a = layer[..., 3:] / 255.0
    G.save_png(stack * (1 - a) + layer[..., :3] * a, _blk("l1x_wrap_front"))
    topf = G.over(card, np.array([226, 230, 232.0]), film * 1.4)
    G.save_png(topf, _blk("l1x_wrap_top"))


# ---------------------------------------------------------------------------------------------
# Models and blockstates
# ---------------------------------------------------------------------------------------------

def _write(rel: str, data) -> None:
    path = ASSETS / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def _model(name: str, textures: dict, elements: list, cutout: bool = False, parent: str | None = None) -> None:
    data = {}
    if parent:
        data["parent"] = parent
    if cutout:
        data["render_type"] = "minecraft:cutout"
    data["textures"] = textures
    if elements:
        data["elements"] = elements
    _write(f"models/block/{name}.json", data)


def _el(fr, to, faces: dict, rot=None, shade=True) -> dict:
    e = {"from": list(fr), "to": list(to), "faces": faces}
    if rot:
        e["rotation"] = rot
    if not shade:
        e["shade"] = False
    return e


def _box_faces(tex: str, skip=(), **over) -> dict:
    """All six faces with #tex (auto UV), except skipped ones; keyword args override a face's texture."""
    return {f: {"texture": over.get(f, tex)} for f in ("north", "south", "east", "west", "up", "down") if f not in skip}


def _item(block: str, model: str) -> None:
    _write(f"models/item/{block}.json", {"parent": f"backrooms:block/{model}"})


def _facing_states(block: str, model_for, props=()) -> None:
    """Blockstate over facing (y rotation) times every combination of the other properties."""
    import itertools
    variants = {}
    names = [p[0] for p in props]
    for combo in itertools.product(*[p[1] for p in props]):
        for face, rot in FACING_ORDER:
            key = ",".join([f"facing={face}"] + [f"{n}={str(v).lower()}" for n, v in zip(names, combo)])
            entry = {"model": f"backrooms:block/{model_for(*combo)}"}
            if rot:
                entry["y"] = rot
            variants[key] = entry
    _write(f"blockstates/{block}.json", {"variants": variants})


FACING_ORDER = FACINGS


def _axis_states(block: str, model: str) -> None:
    _write(f"blockstates/{block}.json", {"variants": {
        "axis=x": {"model": f"backrooms:block/{model}"},
        "axis=z": {"model": f"backrooms:block/{model}", "y": 90}}})
    _item(block, model)


def tex_l1x_models() -> None:
    concrete = "backrooms:block/concrete_wall"
    # --- Pillar corners: a chamfered column corner (the NW corner cut off at 45 degrees) -----------
    bands = ["concrete_wall", "garage_pillar", "l1_pillar_painted", "l1x_band_0", "l1x_band_1", "l1x_band_2"]
    d = 6.0                                                  # chamfer size, pixels
    half = d * np.sqrt(2) / 2
    for i, tex in enumerate(bands):
        t = f"backrooms:block/{tex}"
        _model(f"l1_pillar_corner_{i}", {"particle": t, "all": t}, [
            _el((d, 0, 0), (16, 16, 16), {f: {"texture": "#all"} for f in ("north", "east", "south", "up", "down")}),
            _el((0, 0, d), (d, 16, 16), {"west": {"texture": "#all"}, "south": {"texture": "#all"},
                                          "up": {"texture": "#all"}, "down": {"texture": "#all"}}),
            _el((d / 2 - half, 0, d / 2), (d / 2 + half, 16, d / 2 + 3),
                {"north": {"texture": "#all", "uv": [0, 0, round(2 * half, 3), 16]}},
                rot={"origin": [d / 2, 8, d / 2], "axis": "y", "angle": 45}),
        ])
    _facing_states("l1_pillar_corner", lambda band: f"l1_pillar_corner_{band}", [("band", range(6))])
    _item("l1_pillar_corner", "l1_pillar_corner_0")

    # --- Big pillar numbers and garage signs: cubes with the drawing on the north face -------------
    def decal(name, front):
        _model(name, {"particle": concrete, "north": f"backrooms:block/{front}", "south": concrete, "east": concrete,
                      "west": concrete, "up": concrete, "down": concrete}, [], parent="minecraft:block/cube")
    for sector in range(3):
        for n, label in enumerate(LABELS):
            for half_name in ("lo", "hi"):
                decal(f"l1_pillar_number_{n}_{sector}_{half_name}", f"l1x_number_{label}_{sector}_{half_name}")
    _facing_states("l1_pillar_number", lambda number, sector, upper: f"l1_pillar_number_{number}_{sector}_{'hi' if upper else 'lo'}",
                   [("number", range(8)), ("sector", range(3)), ("upper", (False, True))])
    _item("l1_pillar_number", "l1_pillar_number_0_0_lo")
    for v in range(6):
        decal(f"l1_pillar_sign_{v}", f"l1x_sign_{v}")
    _facing_states("l1_pillar_sign", lambda v: f"l1_pillar_sign_{v}", [("variant", range(6))])
    _item("l1_pillar_sign", "l1_pillar_sign_0")

    # --- Floor markings: concrete floor with the mark on top (it turns with FACING) -----------------
    floor = "backrooms:block/concrete_floor"
    for v in range(13):
        _model(f"l1_floor_mark_{v}", {"particle": floor, "down": floor, "up": f"backrooms:block/l1x_floor_{v}",
                                       "north": floor, "south": floor, "east": floor, "west": floor},
               [], parent="minecraft:block/cube")
    _facing_states("l1_floor_mark", lambda v: f"l1_floor_mark_{v}", [("variant", range(13))])
    _item("l1_floor_mark", "l1_floor_mark_1")

    # --- Wheel stop, speed bump -------------------------------------------------------------------
    ws = "backrooms:block/l1x_wheel_stop"
    _model("l1_wheel_stop", {"particle": ws, "t": ws}, [
        _el((1, 0, 5), (15, 2.5, 11), _box_faces("#t", skip=("down",))),
        _el((1.5, 2.5, 6), (14.5, 3, 10), _box_faces("#t", skip=("down",))),
    ])
    _facing_states("l1_wheel_stop", lambda: "l1_wheel_stop")
    _item("l1_wheel_stop", "l1_wheel_stop")
    sb = "backrooms:block/l1x_speed_bump"
    _model("l1_speed_bump", {"particle": sb, "t": sb}, [
        _el((0, 0, 4), (16, 1.2, 12), _box_faces("#t", skip=("down",))),
        _el((0, 1.2, 5.5), (16, 2, 10.5), _box_faces("#t", skip=("down",))),
    ])
    _axis_states("l1_speed_bump", "l1_speed_bump")

    # --- Ceiling services (all run along X) -------------------------------------------------------
    red, brass = "backrooms:block/l1x_red_pipe", "backrooms:block/l1x_brass"
    galv, bracket = "backrooms:block/l1x_galvanised", "backrooms:block/l1x_bracket"
    pipe = _el((0, 12, 7), (16, 15, 10), _box_faces("#pipe", skip=("east", "west")))
    strap = _el((7, 15, 6.5), (9, 16, 10.5), _box_faces("#strap", skip=("up",)))
    _model("l1_sprinkler_pipe", {"particle": red, "pipe": red, "strap": bracket}, [pipe, strap])
    _axis_states("l1_sprinkler_pipe", "l1_sprinkler_pipe")
    _model("l1_sprinkler_head", {"particle": red, "pipe": red, "strap": bracket, "brass": brass}, [
        pipe, strap,
        _el((7.4, 10.5, 8.0), (8.6, 12, 9.2), _box_faces("#brass", skip=("up",))),
        _el((7.0, 10, 7.6), (9.0, 10.5, 9.6), _box_faces("#brass")),
        _el((7.8, 9.4, 8.3), (8.2, 10, 8.9), _box_faces("#pipe")),   # the red glass bulb
    ])
    _axis_states("l1_sprinkler_head", "l1_sprinkler_head")
    cond = "backrooms:block/l1x_conduit"
    _model("l1_conduit", {"particle": cond, "c": cond, "strap": bracket}, [
        _el((0, 14, 3), (16, 15.5, 4.5), _box_faces("#c", skip=("east", "west", "up"))),
        _el((7, 15.5, 2.5), (8, 16, 5), _box_faces("#strap", skip=("up",))),
    ])
    _axis_states("l1_conduit", "l1_conduit")
    cab = "backrooms:block/l1x_cables"
    _model("l1_cable_tray", {"particle": galv, "g": galv, "cables": cab}, [
        _el((0, 9, 2), (16, 11.5, 2.6), _box_faces("#g", skip=("east", "west"))),
        _el((0, 9, 13.4), (16, 11.5, 14), _box_faces("#g", skip=("east", "west"))),
        _el((3, 9, 2.6), (4, 9.6, 13.4), _box_faces("#g")),
        _el((11, 9, 2.6), (12, 9.6, 13.4), _box_faces("#g")),
        _el((0, 9.6, 3.2), (16, 11, 12.8), _box_faces("#cables", skip=("east", "west"))),
        _el((7, 8.4, 1), (9, 9, 15), _box_faces("#g")),                                  # trapeze bar
        _el((7.6, 9, 1.4), (8.4, 16, 2.2), _box_faces("#g", skip=("up",))),               # hanger rods
        _el((7.6, 9, 13.8), (8.4, 16, 14.6), _box_faces("#g", skip=("up",))),
    ])
    _axis_states("l1_cable_tray", "l1_cable_tray")
    duct = "backrooms:block/l1x_duct"
    _model("l1_duct", {"particle": duct, "d": duct, "g": galv}, [
        _el((0, 3, 0), (16, 16, 16), {"north": {"texture": "#d"}, "south": {"texture": "#d"}, "down": {"texture": "#d"},
                                       "up": {"texture": "#g"}}),
    ])
    _axis_states("l1_duct", "l1_duct")

    # --- The hanging fluorescent fixture ----------------------------------------------------------
    hous, diff = "backrooms:block/l1x_light_housing", "backrooms:block/l1x_light_diffuser"
    chain = "minecraft:block/chain"
    _model("l1_hanging_light", {"particle": hous, "h": hous, "d": diff, "chain": chain}, [
        _el((0, 5.5, 4), (16, 8.5, 12), {"north": {"texture": "#h"}, "south": {"texture": "#h"}, "up": {"texture": "#h"},
                                          "down": {"texture": "#h"}}),
        _el((0, 5, 5), (16, 5.5, 11), {"down": {"texture": "#d"}, "north": {"texture": "#h"}, "south": {"texture": "#h"}},
            shade=False),
        _el((6.5, 8.5, 8), (9.5, 16, 8), {"north": {"texture": "#chain", "uv": [0, 0, 3, 7.5]},
                                           "south": {"texture": "#chain", "uv": [0, 0, 3, 7.5]}},
            rot={"origin": [8, 8, 8], "axis": "y", "angle": 45}),
        _el((8, 8.5, 6.5), (8, 16, 9.5), {"west": {"texture": "#chain", "uv": [3, 0, 6, 7.5]},
                                           "east": {"texture": "#chain", "uv": [3, 0, 6, 7.5]}},
            rot={"origin": [8, 8, 8], "axis": "y", "angle": 45}),
    ], cutout=True)
    _axis_states("l1_hanging_light", "l1_hanging_light")

    # --- Extinguisher cabinet and convex mirror (facing north = hung on a south wall) ---------------
    cside, cfront = "backrooms:block/l1x_cabinet_side", "backrooms:block/l1x_cabinet_front"
    _model("l1_extinguisher", {"particle": cside, "s": cside, "f": cfront}, [
        _el((3, 0, 12), (13, 14, 16), _box_faces("#s", north="#f")),
    ])
    _facing_states("l1_extinguisher", lambda: "l1_extinguisher")
    _item("l1_extinguisher", "l1_extinguisher")
    mirror = "backrooms:block/l1x_mirror"
    _model("l1_convex_mirror", {"particle": bracket, "m": mirror, "b": bracket}, [
        _el((3, 3, 6), (13, 13, 6.01), {"north": {"texture": "#m"}},
            rot={"origin": [8, 8, 7], "axis": "x", "angle": -22.5}),
        _el((3.5, 3.5, 6.2), (12.5, 12.5, 7.2), _box_faces("#b", skip=("north",)),
            rot={"origin": [8, 8, 7], "axis": "x", "angle": -22.5}),
        _el((7.5, 7.5, 7.2), (8.5, 8.5, 16), _box_faces("#b")),
        _el((6, 6, 15.4), (10, 10, 16), _box_faces("#b")),
    ], cutout=True)
    _facing_states("l1_convex_mirror", lambda: "l1_convex_mirror")
    _item("l1_convex_mirror", "l1_convex_mirror")

    # --- Pallet racks (facing north = the aisle is north) ------------------------------------------
    blue, orange = "backrooms:block/l1x_rack_blue", "backrooms:block/l1x_rack_orange"
    beams = [_el((0, 0, 0.5), (16, 2.5, 2), _box_faces("#o", skip=("east", "west"))),
             _el((0, 0, 14), (16, 2.5, 15.5), _box_faces("#o", skip=("east", "west")))]
    for with_beams in (False, True):
        els = [
            _el((6.5, 0, 0.5), (9.5, 16, 3.5), _box_faces("#b", skip=("up", "down"))),
            _el((6.5, 0, 12.5), (9.5, 16, 15.5), _box_faces("#b", skip=("up", "down"))),
            _el((7.3, 11, 3.5), (8.7, 12, 12.5), _box_faces("#b")),
            _el((7.5, 7.5, 1.65), (8.5, 8.5, 14.35), _box_faces("#b"), rot={"origin": [8, 8, 8], "axis": "x", "angle": 45}),
        ]
        if with_beams:
            els += beams
        _model(f"l1_rack_upright{'_beams' if with_beams else ''}", {"particle": blue, "b": blue, "o": orange}, els)
    _facing_states("l1_rack_upright", lambda b: f"l1_rack_upright{'_beams' if b else ''}", [("beams", (False, True))])
    _item("l1_rack_upright", "l1_rack_upright_beams")

    tex = {"particle": "backrooms:block/l1x_pallet_side", "o": orange, "pt": "backrooms:block/l1x_pallet_top",
           "ps": "backrooms:block/l1x_pallet_side", "ws": "backrooms:block/l1x_wrap_side",
           "wf": "backrooms:block/l1x_wrap_front", "wt": "backrooms:block/l1x_wrap_top",
           "bs": "backrooms:block/l1x_box_side", "bf": "backrooms:block/l1x_box_front",
           "bt": "backrooms:block/l1x_box_top", "bc": "backrooms:block/l1x_box_crushed"}
    for load in range(5):
        for with_beams in (False, True):
            b = 2.5 if with_beams else 0.0
            els = list(beams) if with_beams else []
            if load != 0:                                    # every load but EMPTY stands on a pallet
                for z0 in (1, 7, 13):
                    els.append(_el((1, b, z0), (15, b + 1.5, z0 + 2), _box_faces("#ps", skip=("up",))))
                els.append(_el((1, b + 1.5, 1), (15, b + 2.5, 15), _box_faces("#ps", up="#pt", down="#pt")))
            top = b + 2.5
            if load == 1:                                    # shrink-wrapped stack
                els.append(_el((1.5, top, 1.5), (14.5, min(16, top + 11), 14.5), _box_faces("#ws", skip=("down",), north="#wf", up="#wt")))
            elif load == 2:                                  # a few loose boxes
                els.append(_el((2, top, 2), (9, top + 7, 9), _box_faces("#bs", skip=("down",), north="#bf", up="#bt")))
                els.append(_el((9.5, top, 3), (14.5, top + 5, 9.5), _box_faces("#bs", skip=("down",), up="#bt")))
                els.append(_el((3, top, 9.5), (11, top + 5.5, 14.5), _box_faces("#bs", skip=("down",), up="#bt")))
                els.append(_el((3.5, top + 7, 3), (8, top + 10, 8), _box_faces("#bs", skip=("down",), up="#bt")))
            elif load == 4:                                  # a slumped stack and a box that fell off it
                els.append(_el((2, top, 2), (12, top + 7, 13), _box_faces("#bc", skip=("down",)),
                               rot={"origin": [2, top, 8], "axis": "z", "angle": 22.5}))
                els.append(_el((9, top, 3), (15, top + 4, 9), _box_faces("#bc", skip=("down",), up="#bt"),
                               rot={"origin": [12, top, 6], "axis": "y", "angle": -22.5}))
            _model(f"l1_rack_bay_{load}{'_beams' if with_beams else ''}", tex, els, cutout=True)
    _facing_states("l1_rack_bay", lambda load, b: f"l1_rack_bay_{load}{'_beams' if b else ''}",
                   [("load", range(5)), ("beams", (False, True))])
    _item("l1_rack_bay", "l1_rack_bay_1_beams")


TEXTURES = [tex_l1x_pillar_bands, tex_l1x_pillar_numbers, tex_l1x_pillar_signs, tex_l1x_floor_marks,
            tex_l1x_floor_objects, tex_l1x_services, tex_l1x_light, tex_l1x_dressing, tex_l1x_racks, tex_l1x_models]
SOUNDS: list = []
