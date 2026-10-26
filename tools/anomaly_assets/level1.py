"""Level 1 anomalies: textures and sounds.

Crate halls (orange storage racks), the painting rooms (crude, unsettling paintings on the walls
and drawings on the floor), the fog patches (a film of condensation on the floor, drips), and
the Corrupted Corridor, Level 1.1 (white cinderblock, blue tile, the walk-through wall, bright
patches, black-and-white walls and floor, and the drone of its fourth sector).

Run only these with:  python tools/gen_assets.py level1_
"""
from __future__ import annotations

import numpy as np

import gen_assets as G

S = G.S
BLACK = (24, 22, 22)
RED = (150, 32, 26)
BLUE = (40, 62, 140)
YELLOW = (214, 172, 44)
GREEN = (52, 110, 58)


def _blk(name: str):
    return G.TEX / "block" / f"{name}.png"


def _rgba(rgb: np.ndarray, alpha: np.ndarray) -> np.ndarray:
    """RGB image + 0..1 coverage -> RGBA with a hard cutout edge (Minecraft cutout)."""
    return np.concatenate([rgb, (np.where(alpha >= 0.5, 255.0, 0.0))[..., None]], axis=2)


def _fill_mask(draw_fn) -> np.ndarray:
    """Anti-aliased 0..1 mask of shapes drawn (filled) with PIL."""
    return G.pil_mask(draw_fn)


def _ink(img, mask, color, alpha=0.92, rng=None) -> np.ndarray:
    """Paint a mask in one colour, a bit patchy like cheap poster paint."""
    patch = 1.0 if rng is None else 0.85 + 0.15 * np.clip(G.fnoise(rng, beta=1.2), -1, 1)
    return G.over(img, np.array(color, float), mask * alpha * patch)


# ---------------------------------------------------------------- crate halls ---

def tex_level1_storage_shelf() -> None:
    """Orange pallet racking: uprights at the sides, a beam on top, cardboard boxes on the deck."""
    rng = G.rng_for(9101)
    X, Y = G.grid()
    img = G.fill((30, 29, 33), (S, S))                                     # the dark inside of the rack
    img = G.add_lum(img, G.blur(G.fnoise(rng, beta=2.5), 2.0) * 3)
    img = G.add_lum(img, -14 * G.smoothstep(40, 0, Y))                     # shadow under the deck above
    # Cardboard boxes standing on the deck below (the bottom edge of the block).
    for x0, x1, top, col in ((14, 58, 52, (148, 110, 68)), (60, 92, 74, (132, 98, 62)), (94, 116, 40, (156, 118, 74))):
        box = G.box((S, S), x0, top, x1, S + 1)
        face = G.add_lum(G.fill(col, (S, S)), G.fnoise(rng, beta=2.0, aniso=(1, 6)) * 5)
        face = G.add_lum(face, -26 * G.smoothstep(top + 10, top, Y))       # the top flap edge in shadow
        tape = G.box((S, S), (x0 + x1) / 2 - 3, top, (x0 + x1) / 2 + 3, S + 1)
        face = G.over(face, np.array([186, 170, 130]), tape * 0.8)
        img = G.over(img, face, box)
        img = G.add_lum(img, -20 * np.clip(G.blur(box, 1.2) - box, 0, 1) * 2)
    # Beam on top, uprights at the sides: painted orange steel with scuffs.
    steel = G.add_lum(G.fill((206, 104, 34), (S, S)), G.fnoise(rng, beta=1.8) * 6)
    scuffs = G.smoothstep(1.6, 2.2, G.fnoise(rng, beta=1.0, kmin=8))
    steel = G.over(steel, np.array([92, 84, 80]), scuffs * 0.8)
    frame = np.clip(G.box((S, S), -1, -1, S + 1, 13) + G.box((S, S), -1, -1, 10, S + 1)
                    + G.box((S, S), S - 10, -1, S + 1, S + 1), 0, 1)
    img = G.over(img, steel, frame)
    img = G.add_lum(img, -30 * G.box((S, S), -1, 13, S + 1, 15))           # the beam's lower lip
    for y in range(24, S, 16):                                             # punched holes in the uprights
        for x in (5, S - 5):
            img = G.over(img, np.array([20, 18, 20]), np.clip(1.8 - np.hypot(X - x, Y - y), 0, 1))
    G.save_png(img, _blk("l1_storage_shelf"))

    # Top: the plywood deck between two beams.
    rng = G.rng_for(9102)
    top = G._wood(rng, np.array([168, 138, 98]), horizontal=True)
    top = G.over(top, steel, np.clip(G.box((S, S), -1, -1, S + 1, 10) + G.box((S, S), -1, S - 10, S + 1, S + 1), 0, 1))
    G.save_png(top, _blk("l1_storage_shelf_top"))


# ---------------------------------------------------------------- painting rooms ---

def _sheet(rng, col, x0, y0, x1, y1, ragged=1.6):
    """A sheet of paper or canvas with a slightly ragged edge: (rgb, coverage)."""
    X, Y = G.grid()
    jx = G.fnoise(rng, (S, S), beta=2.5) * ragged
    cover = G.box((S, S), x0 + jx, y0 + jx, x1 - jx, y1 + jx, soft=0.6)
    paper = G.add_lum(G.fill(col, (S, S)), G.fnoise(rng, beta=2.0) * 4 + rng.normal(0, 1.5, (S, S)))
    paper = G.add_lum(paper, -18 * G.smoothstep(6, 0, np.minimum(np.minimum(X - x0, x1 - X), np.minimum(Y - y0, y1 - Y))))
    # Water stains and grime: the paintings have hung in the damp a long time.
    stain = G.smoothstep(1.2, 2.2, G.fnoise(rng, beta=2.6))
    paper = G.add_lum(paper, -22 * stain, (1.0, 0.92, 0.75))
    # Tape on two corners.
    for cx, cy in ((x0 + 4, y0 + 4), (x1 - 4, y0 + 4)):
        tape = G.box((S, S), cx - 7, cy - 3, cx + 7, cy + 3)
        paper = G.over(paper, np.array([206, 196, 160]), tape * 0.7)
        cover = np.maximum(cover, tape)
    return paper, cover


def _figure(cx, foot, h, arms=1.0):
    """Stick figure strokes (no face): head, body, arms, legs."""
    head = h * 0.18
    neck = foot - h + 2 * head
    hip = foot - h * 0.38
    pts = [
        [(cx, neck), (cx, hip)],
        [(cx - h * 0.25 * arms, neck + h * 0.25 * arms), (cx, neck + h * 0.08), (cx + h * 0.25 * arms, neck + h * 0.25 * arms)],
        [(cx - h * 0.15, foot), (cx, hip), (cx + h * 0.15, foot)],
    ]
    t = np.linspace(0, 2 * np.pi, 14)
    pts.append([(cx + head * np.cos(a), neck - head + head * np.sin(a)) for a in t])
    return pts


def _painting(variant: int) -> np.ndarray:
    rng = G.rng_for(9200 + variant)
    if variant == 0:
        # A family of four, faceless, and a much taller black figure standing beside them.
        img, cover = _sheet(rng, (224, 216, 192), 10, 16, 118, 110)
        strokes = []
        for i, (x, h) in enumerate(((26, 46), (42, 50), (58, 32), (70, 28))):
            img = G.marker(img, rng, _figure(x, 96, h), width=3.0, color=(BLUE, RED, GREEN, YELLOW)[i], alpha=0.85)
        img = G.marker(img, rng, _figure(98, 100, 78, arms=1.6), width=5.0, color=BLACK)
        strokes.append([(14, 100), (114, 102)])
        img = G.marker(img, rng, strokes, width=2.5, color=(70, 120, 60), alpha=0.8)
    elif variant == 1:
        # A house with black windows and a red sun.
        img, cover = _sheet(rng, (230, 224, 206), 14, 12, 116, 114)
        img = G.marker(img, rng, [[(30, 100), (30, 60), (98, 60), (98, 100), (30, 100)], [(26, 62), (64, 30), (102, 62)]],
                       width=3.5, color=BLACK)
        win = _fill_mask(lambda d, k: [d.rectangle((38 * k, 68 * k, 54 * k, 82 * k), fill=255),
                                        d.rectangle((74 * k, 68 * k, 90 * k, 82 * k), fill=255),
                                        d.rectangle((58 * k, 84 * k, 70 * k, 100 * k), fill=255)])
        img = _ink(img, win, BLACK, 0.95, rng)
        sun = _fill_mask(lambda d, k: d.ellipse((92 * k, 18 * k, 110 * k, 36 * k), fill=255))
        img = _ink(img, sun, RED, 0.85, rng)
        rays = [[(101 + 14 * np.cos(a), 27 + 14 * np.sin(a)), (101 + 22 * np.cos(a), 27 + 22 * np.sin(a))]
                for a in np.linspace(0, 2 * np.pi, 9)[:-1]]
        img = G.marker(img, rng, rays, width=2.5, color=RED, alpha=0.8)
    elif variant == 2:
        # A huge grinning face with far too many teeth.
        img, cover = _sheet(rng, (214, 206, 184), 12, 12, 116, 116)
        t = np.linspace(0, 2 * np.pi, 30)
        img = G.marker(img, rng, [[(64 + 46 * np.cos(a), 64 + 48 * np.sin(a)) for a in t]], width=3.5, color=BLACK)
        eyes = _fill_mask(lambda d, k: [d.ellipse((40 * k, 38 * k, 50 * k, 52 * k), fill=255),
                                         d.ellipse((78 * k, 38 * k, 88 * k, 52 * k), fill=255)])
        img = _ink(img, eyes, BLACK, 0.97, rng)
        mouth = _fill_mask(lambda d, k: d.chord((26 * k, 46 * k, 102 * k, 104 * k), 10, 170, fill=255))
        img = _ink(img, mouth, (120, 22, 20), 0.9, rng)
        teeth = [[(30 + 5 * i, 76 + (i % 2) * 6), (32.5 + 5 * i, 76 + ((i + 1) % 2) * 6)] for i in range(14)]
        img = G.marker(img, rng, teeth, width=2.0, color=(236, 232, 220), alpha=0.95)
    elif variant == 3:
        # A scribbled eye, staring.
        img, cover = _sheet(rng, (226, 220, 200), 16, 22, 112, 106)
        t = np.linspace(0, 2 * np.pi, 24)
        img = G.marker(img, rng, [[(64 + 44 * np.cos(a), 64 + 22 * np.sin(a) * np.sign(np.sin(a)) * 1.0) for a in t]],
                       width=3.0, color=BLACK)
        for r in (18, 14, 10):
            img = G.marker(img, rng, [[(64 + r * np.cos(a), 64 + r * np.sin(a)) for a in t]], width=2.5, color=BLUE, alpha=0.8)
        pupil = _fill_mask(lambda d, k: d.ellipse((57 * k, 57 * k, 71 * k, 71 * k), fill=255))
        img = _ink(img, pupil, BLACK, 0.98, rng)
        veins = [[(22 + i * 12, 60 + rng.normal(0, 3)), (30 + i * 12, 64 + rng.normal(0, 4))] for i in (0, 1, 6, 7)]
        img = G.marker(img, rng, veins, width=1.5, color=RED, alpha=0.8)
    elif variant == 4:
        # A black doorway with someone standing in it.
        img, cover = _sheet(rng, (200, 190, 160), 18, 10, 110, 118)
        door = _fill_mask(lambda d, k: d.rectangle((44 * k, 30 * k, 84 * k, 108 * k), fill=255))
        img = _ink(img, door, BLACK, 0.96, rng)
        img = G.marker(img, rng, _figure(64, 104, 62), width=3.0, color=(196, 40, 32), alpha=0.9)
        img = G.marker(img, rng, [[(24, 108), (104, 108)]], width=3.0, color=(90, 80, 70))
    elif variant == 5:
        # Red hand prints, painted straight onto the wall (no sheet).
        img = G.fill(RED, (S, S))
        cover = np.zeros((S, S))
        for cx, cy, ang in ((40, 44, -0.3), (86, 58, 0.25), (54, 94, 0.05)):
            def hand(d, k, cx=cx, cy=cy, ang=ang):
                d.ellipse(((cx - 11) * k, (cy - 10) * k, (cx + 11) * k, (cy + 14) * k), fill=255)
                for i, (fx, fl) in enumerate(((-10, 14), (-4, 19), (2, 20), (8, 17))):
                    x = cx + fx + 2 * np.sin(ang) * i
                    d.ellipse(((x - 2.6) * k, (cy - 10 - fl) * k, (x + 2.6) * k, (cy - 8) * k), fill=255)
                d.ellipse(((cx + 10) * k, (cy - 2) * k, (cx + 20) * k, (cy + 4) * k), fill=255)
            cover = np.maximum(cover, _fill_mask(hand))
        cover = cover * (0.75 + 0.25 * np.clip(G.fnoise(rng, beta=1.0), -1, 1))
        img = G.add_lum(img, G.fnoise(rng, beta=1.5) * 12)
    elif variant == 6:
        # A tall thin figure with long arms, among numbered pillars.
        img, cover = _sheet(rng, (222, 214, 188), 8, 14, 120, 112)
        for x in (22, 98):
            img = G.marker(img, rng, [[(x - 8, 104), (x - 8, 22)], [(x + 8, 104), (x + 8, 22)]], width=2.5, color=(110, 110, 110))
            img = G.marker(img, rng, [[(x - 4, 60), (x + 4, 60)], [(x, 54), (x, 66)]], width=2.0, color=YELLOW)
        img = G.marker(img, rng, [[(60, 104), (62, 40), (66, 104)], [(62, 46), (40, 98)], [(62, 46), (86, 100)]],
                       width=3.5, color=BLACK)
        head = _fill_mask(lambda d, k: d.ellipse((56 * k, 22 * k, 68 * k, 42 * k), fill=255))
        img = _ink(img, head, BLACK, 0.97, rng)
    else:
        # A spiral and a scrawled word.
        img, cover = _sheet(rng, (230, 226, 214), 12, 14, 116, 114)
        t = np.linspace(0, 7 * np.pi, 80)
        img = G.marker(img, rng, [[(56 + 2.6 * a * np.cos(a), 58 + 2.6 * a * np.sin(a)) for a in t]], width=2.5, color=BLACK)

        def word(d, k):
            d.text((64 * k, 100 * k), "WHERE", fill=255, font=G.font(20 * k), anchor="mm")
        img = _ink(img, _fill_mask(word), RED, 0.9, rng)
        img = G.marker(img, rng, [[(20 + 9 * i, 24 + rng.normal(0, 4)), (26 + 9 * i, 30 + rng.normal(0, 4))] for i in range(9)],
                       width=2.0, color=RED, alpha=0.7)
    return _rgba(img, cover)


def tex_level1_paintings() -> None:
    """Eight crude paintings and drawings (cutout, hung on the wall)."""
    for v in range(8):
        G.save_png(_painting(v), _blk(f"l1_painting_{v}"))


def _floor_drawing(variant: int) -> np.ndarray:
    rng = G.rng_for(9300 + variant)
    chalk = (226, 224, 216)
    if variant == 0:
        # A chalk circle with marks around it.
        t = np.linspace(0, 2 * np.pi, 40)
        img = G.marker(G.fill(chalk, (S, S)), rng, [[(64 + 44 * np.cos(a), 64 + 44 * np.sin(a)) for a in t]], width=4.0,
                       color=(255, 255, 255), alpha=1.0)
        strokes = [[(64 + 52 * np.cos(a) - 5, 64 + 52 * np.sin(a) - 5), (64 + 52 * np.cos(a) + 5, 64 + 52 * np.sin(a) + 5)]
                   for a in np.linspace(0, 2 * np.pi, 7)[:-1]]
        strokes += [[(64 + 52 * np.cos(a) - 5, 64 + 52 * np.sin(a) + 5), (64 + 52 * np.cos(a) + 5, 64 + 52 * np.sin(a) - 5)]
                    for a in np.linspace(0, 2 * np.pi, 7)[:-1]]
        lines = [[(64 + 44 * np.cos(a), 64 + 44 * np.sin(a)) for a in t]] + strokes
        cover = _chalk_cover(rng, lines, 4.0)
        return _rgba(G.add_lum(G.fill(chalk, (S, S)), rng.normal(0, 4, (S, S))), cover)
    if variant == 1:
        # The outline of someone lying on the floor.
        body = _figure(64, 120, 108, arms=1.4)
        cover = _chalk_cover(rng, body, 4.5)
        return _rgba(G.add_lum(G.fill(chalk, (S, S)), rng.normal(0, 4, (S, S))), cover)
    if variant == 2:
        # Red paint smeared and dragged across the floor.
        cover = np.zeros((S, S))
        drag = [[(20 + rng.normal(0, 2), 30 + 6 * i), (108 + rng.normal(0, 4), 40 + 6 * i + rng.normal(0, 3))] for i in range(4)]
        cover = np.maximum(cover, _chalk_cover(rng, drag, 3.0, patch=0.45))
        blot = G.smoothstep(0.8, 1.4, G.fnoise(rng, beta=3.0) + 1.5 * G.blob((S, S), 34, 92, 16, 12))
        cover = np.maximum(cover, blot)
        img = G.add_lum(G.fill((120, 22, 18), (S, S)), G.fnoise(rng, beta=1.6) * 10)
        return _rgba(img, cover)
    # A big crude eye-sun in white paint, black dots round it.
    t = np.linspace(0, 2 * np.pi, 30)
    lines = [[(64 + 26 * np.cos(a), 64 + 26 * np.sin(a)) for a in t]]
    lines += [[(64 + 32 * np.cos(a), 64 + 32 * np.sin(a)), (64 + 54 * np.cos(a), 64 + 54 * np.sin(a))]
              for a in np.linspace(0, 2 * np.pi, 13)[:-1]]
    cover = _chalk_cover(rng, lines, 5.0, patch=0.2)
    pupil = _fill_mask(lambda d, k: d.ellipse((56 * k, 56 * k, 72 * k, 72 * k), fill=255))
    img = G.over(G.fill((232, 230, 222), (S, S)), np.array(BLACK, float), pupil)
    return _rgba(img, np.maximum(cover, pupil))


def _chalk_cover(rng, strokes, width, patch=0.35) -> np.ndarray:
    """Coverage of rough chalk strokes: broken up where the chalk skipped over the concrete."""
    lines = [G.wobble_line(rng, s, jitter=1.0) for s in strokes]

    def draw(d, k):
        for ln in lines:
            p = [(x * k, y * k) for x, y in ln]
            d.line(p, fill=255, width=int(width * k), joint="curve")
    m = G.pil_mask(draw)
    skips = rng.random((S, S)) < patch
    return m * np.where(skips, 0.0, 1.0)


def tex_level1_floor_paintings() -> None:
    """Four drawings on the floor: chalk circle, chalk outline, red drag marks, painted eye."""
    for v in range(4):
        G.save_png(_floor_drawing(v), _blk(f"l1_floor_painting_{v}"))


# ---------------------------------------------------------------- fog patches ---

def tex_level1_condensation() -> None:
    """A film of still water on the concrete (translucent, seamless, so neighbours join up)."""
    rng = G.rng_for(9401)
    wet = G.smoothstep(-0.35, 0.25, G.fnoise(rng, beta=3.2))
    img = G.add_lum(G.fill((70, 78, 90), (S, S)), G.fnoise(rng, beta=2.0) * 4)
    sheen = G.smoothstep(0.8, 2.0, G.fnoise(rng, beta=2.0, aniso=(3, 1)))
    img = G.add_lum(img, sheen * 50, (0.95, 0.98, 1.1))
    alpha = (0.45 + 0.2 * sheen) * wet
    G.save_png(np.concatenate([img, (alpha * 255)[..., None]], axis=2), _blk("l1_condensation"))


# ---------------------------------------------------------------- the Corrupted Corridor ---

def _cinderblock(rng, base=(226, 226, 222)) -> np.ndarray:
    """White-painted cinderblock: 2 blocks by 4 courses per face, running bond, grey joints."""
    X, Y = G.grid()
    img = G._smooth_concrete(rng, base, mottle=3.0, fine=1.2)
    img = G._pores(img, rng, density=0.02, depth=12)
    course = np.floor(Y / 32).astype(int)
    xo = (X + 32 * (course % 2)) % 64
    yo = Y % 32
    joint = np.clip(G.smoothstep(2.6, 1.2, np.minimum(xo, 64 - xo)) + G.smoothstep(2.6, 1.2, np.minimum(yo, 32 - yo)), 0, 1)
    # Each block a slightly different white; faces bulge a little (light from above).
    cell = (course * 7 + np.floor((X + 32 * (course % 2)) / 64).astype(int) * 13) % 17
    img = G.add_lum(img, (cell - 8) * 0.5)
    img = G.add_lum(img, -6 * G.smoothstep(8, 31, yo))
    img = G.over(img, np.array([176, 176, 172]), joint * 0.9)
    return img


def tex_level1_cinderblock() -> None:
    G.save_png(_cinderblock(G.rng_for(9501)), _blk("l1_cinderblock"))


def tex_level1_noclip_wall() -> None:
    """The same wall, very slightly wrong: a faint cold tint and two courses shifted a pixel."""
    img = _cinderblock(G.rng_for(9501))
    img = np.roll(img, 2, axis=1)
    img[40:44] = np.roll(img[40:44], 3, axis=1)
    img[97:99] = np.roll(img[97:99], -2, axis=1)
    img = img * np.array([0.97, 1.0, 1.03])
    G.save_png(img, _blk("l1_noclip_wall"))


def tex_level1_blue_tile() -> None:
    """Glazed blue tiles, 4 x 4 per face, with pale grout."""
    rng = G.rng_for(9502)
    X, Y = G.grid()
    tx = np.floor(X / 32).astype(int)
    ty = np.floor(Y / 32).astype(int)
    shade = ((tx * 5 + ty * 11) % 7 - 3) * 2.0
    img = G.add_lum(G.fill((64, 112, 178), (S, S)), shade + G.fnoise(rng, beta=2.2) * 3)
    xo, yo = X % 32, Y % 32
    img = G.add_lum(img, 14 * G.smoothstep(10, 2, yo) * G.smoothstep(2, 6, xo))    # glaze catching the light
    grout = np.clip(G.smoothstep(2.0, 0.8, np.minimum(xo, 32 - xo)) + G.smoothstep(2.0, 0.8, np.minimum(yo, 32 - yo)), 0, 1)
    img = G.over(img, np.array([196, 202, 208]), grout)
    img = G.add_lum(img, -10 * G.smoothstep(1.4, 2.4, G.fnoise(rng, beta=2.8)))  # grime
    G.save_png(img, _blk("l1_blue_tile"))


def tex_level1_bright_patch() -> None:
    """An unexplained patch of pure light: overexposed white with a faint grid ghosting through."""
    rng = G.rng_for(9503)
    X, Y = G.grid()
    img = G.fill((246, 246, 240), (S, S))
    img = G.add_lum(img, G.fnoise(rng, beta=3.0) * 2)
    ghost = np.clip(G.smoothstep(1.5, 0.5, np.minimum(X % 32, 32 - X % 32)) + G.smoothstep(1.5, 0.5, np.minimum(Y % 32, 32 - Y % 32)), 0, 1)
    img = G.add_lum(img, -6 * ghost)
    G.save_png(img, _blk("l1_bright_patch"))


def tex_level1_monochrome_wall() -> None:
    """Black and white bands that wave, as if the wall were moving (seamless)."""
    def inside(x, y):
        return np.sin(2 * np.pi * (y / 16.0 + 0.3 * np.sin(2 * np.pi * x / 64.0))) > 0
    m = G.supersample((S, S), inside)
    rng = G.rng_for(9504)
    img = G.lerp(G.fill((16, 16, 16), (S, S)), G.fill((236, 236, 236), (S, S)), m[..., None])
    img = G.add_lum(img, G.fnoise(rng, beta=2.0) * 4)
    G.save_png(img, _blk("l1_monochrome_wall"))


def tex_level1_monochrome_floor() -> None:
    """A warped black-and-white checkerboard (seamless). The false floor uses it too."""
    def inside(x, y):
        cx = np.floor((x + 3 * np.sin(2 * np.pi * y / 64.0)) / 16.0)
        cy = np.floor((y + 3 * np.sin(2 * np.pi * x / 64.0)) / 16.0)
        return (cx + cy) % 2 == 0
    m = G.supersample((S, S), inside)
    rng = G.rng_for(9505)
    img = G.lerp(G.fill((20, 20, 20), (S, S)), G.fill((228, 228, 228), (S, S)), m[..., None])
    img = G.add_lum(img, G.fnoise(rng, beta=2.0) * 4 - 8 * G.smoothstep(1.4, 2.4, G.fnoise(rng, beta=2.6)))
    G.save_png(img, _blk("l1_monochrome_floor"))


# ---------------------------------------------------------------- sounds ---

def snd_level1_drip() -> None:
    """Four single drops falling into a shallow puddle, with the garage's long tail."""
    for i in range(1, 5):
        rng = G.rng_for(9600 + i)
        dur = 1.6
        n = int(dur * G.SR)
        x = np.zeros(n)
        t = G.t_axis(0.09)
        f0 = rng.uniform(800, 1250)
        f = f0 * (1 + 0.6 * np.clip(t / 0.04, 0, 1))
        plink = np.sin(2 * np.pi * np.cumsum(f) / G.SR) * np.exp(-t / rng.uniform(0.018, 0.03)) * G.smoothstep(0, 0.002, t)
        G.add_at(x, plink, 0.01, False)
        splash = rng.normal(size=int(0.02 * G.SR)) * np.exp(-np.arange(int(0.02 * G.SR)) / (0.004 * G.SR))
        G.add_at(x, 0.15 * G.fft_filter(splash, lambda fr: G.bp_gain(fr, 1500, 6000, 2)), 0.01, False)
        x = G.fft_filter(x, lambda fr: G.lp_gain(fr, 3500, 2))
        wet = G.conv(x, G.reverb_ir(rng, 1.4, 0.45, lp=1800, predelay=0.03))[:n]
        out = G.norm(x) + 0.35 * G.norm(wet)
        G.save_ogg(out, G.SND / "level1" / f"drip{i}.ogg", peak=0.7, fade_out=0.2)


def snd_level1_crate_thud() -> None:
    """A heavy wooden crate settling on concrete somewhere behind you: dull, a little hollow.
    3 takes: different scrape noise, body resonances (+-5%) and settle-knock timing."""
    for i, (seed, p, t2) in enumerate(((9610, 1.0, 0.11), (59611, 1.04, 0.095), (69610, 0.95, 0.14)), 1):
        rng = G.rng_for(seed)
        dur = 2.2
        n = int(dur * G.SR)
        x = np.zeros(n)
        k = int(0.08 * G.SR)
        burst = rng.normal(size=k) * np.exp(-np.arange(k) / (0.012 * G.SR))
        G.add_at(x, G.fft_filter(burst, lambda f: G.lp_gain(f, 260, 2)), 0.0, False)
        G.add_at(x, 0.8 * G.ring([72 * p, 118 * p, 183 * p], [0.09, 0.06, 0.04], [1.0, 0.6, 0.35], 0.5), 0.0, False)
        G.add_at(x, 0.35 * G.ring([410 * p, 560 * p, 730 * p], [0.025, 0.02, 0.015], [1.0, 0.7, 0.5], 0.2), 0.002, False)
        # A second, smaller knock as it settles.
        G.add_at(x, 0.3 * G.ring([95 * p, 150 * p, 480 * p], [0.05, 0.04, 0.015], [1.0, 0.5, 0.4], 0.3), t2, False)
        x = G.fft_filter(x, lambda f: G.lp_gain(f, 1600, 2))
        wet = G.conv(x, G.reverb_ir(rng, 2.0, 0.6, lp=1100, predelay=0.04))[:n]
        name = "crate_thud.ogg" if i == 1 else f"crate_thud{i}.ogg"
        G.save_ogg(G.norm(x) + 0.45 * G.norm(wet), G.SND / "level1" / name, peak=0.8, fade_out=0.3)


def snd_level1_corridor_drone() -> None:
    """6 s: a low, warbling, slightly detuned drone (the black-and-white sector)."""
    rng = G.rng_for(9620)
    T = 6.0
    n = int(T * G.SR)
    t = np.arange(n) / G.SR
    wob = 1 + 0.004 * np.sin(2 * np.pi * 0.37 * t) + G.smooth_jitter(rng, n, 1.5, 0.003)
    x = np.zeros(n)
    for f, a in ((55.0, 1.0), (55.8, 0.8), (82.4, 0.45), (110.9, 0.3), (164.2, 0.12)):
        x += a * np.sin(2 * np.pi * np.cumsum(f * wob) / G.SR + rng.uniform(0, 2 * np.pi))
    x *= 1 + 0.25 * np.sin(2 * np.pi * 0.5 * t)                                # slow undulation
    hiss = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 200, 900, 2))
    x = G.norm(x) + 0.05 * G.norm(hiss)
    x *= G.env_adsr(n, 0.8, 1.2)
    G.save_ogg(x, G.SND / "level1" / "corridor_drone.ogg", peak=0.6)


TEXTURES = [
    tex_level1_storage_shelf, tex_level1_paintings, tex_level1_floor_paintings, tex_level1_condensation,
    tex_level1_cinderblock, tex_level1_noclip_wall, tex_level1_blue_tile, tex_level1_bright_patch,
    tex_level1_monochrome_wall, tex_level1_monochrome_floor,
]
SOUNDS = [snd_level1_drip, snd_level1_crate_thud, snd_level1_corridor_drone]
