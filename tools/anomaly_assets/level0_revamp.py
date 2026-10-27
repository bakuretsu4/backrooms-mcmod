"""Level 0 revamp (Kane Pixels' series + the A24 film "Backrooms"): textures, block models,
blockstates and item models of the blocks in registry/Level0Blocks.java.

The film's chevron wallpaper, the chest-high partitions with their wooden cap rail, the corridor
light strip, missing ceiling tiles, the green exit sign and hazard stripes of the exit alcove,
wall switch boxes with conduit, scrawled words on the walls, teal tape lines, mannequins, the
gold-and-purple throne, an Async equipment case, a cable hanging out of the ceiling, a dark hole
high in a wall and the metal ladder up to it.

Run with `python tools/gen_assets.py level0_revamp` from the project root (the chevron paper's
versions come from `python tools/gen_assets.py surface_variants variant_models`). Every texture
uses its own fixed random seed (60100-60199), so re-running gives identical files. Models reuse
the box helpers of level39_props.py (`P._el` etc.): every box is listed in block pixels (0..16,
allowed -16..32) for the block facing north; the blockstates turn it for the other directions.
"""
from __future__ import annotations

import numpy as np

import gen_assets as G
from anomaly_assets import level39_props as P

S = G.S


def _blk(name: str):
    return G.TEX / "block" / f"{name}.png"


def _ref(name: str) -> str:
    return f"backrooms:block/{name}"


def _fill(color) -> np.ndarray:
    return G.fill(color, (S, S))


# =============================================================================
# The film's chevron wallpaper ("Classic" areas, the half-wall rooms)
# =============================================================================

CHEVRON = (190, 178, 116)     # a touch paler and creamier than Kane's paper, still the same family


def chevron_base() -> np.ndarray:
    """Tone-on-tone vertical columns (two per block, half a block wide) of small, shallow stacked
    chevrons, printed a shade lighter than the paper, with a faint darker line between the
    columns, on plain matte paper (not the lobby's printed design). Kept fine and faint: from a
    few blocks away it should read as a texture, not as a zigzag."""
    rng = G.rng_for(60100)
    img = G._paper(G.rng_for(60101), CHEVRON)
    X, Y = G.grid()
    col = 64.0
    u = G.wrapd(X - col / 2, col)                                    # -32..32 across a column
    pitch = S / 20                                                   # 20 chevrons per block height
    t = G.wrapd(Y + np.abs(u) * 0.12, pitch)                         # shallow ^-shaped rows
    arms = G.smoothstep(30.0, 26.0, np.abs(u))
    line = np.exp(-(t / 0.8) ** 2) * arms
    under = np.exp(-((t - 1.4) / 0.7) ** 2) * arms
    gutter = np.exp(-((np.abs(u) - 32) / 0.8) ** 2)
    sheen = 1 + 0.25 * G.fnoise(rng, beta=2.5)                       # the print isn't perfectly even
    img = G.add_lum(img, (4.0 * line - 1.5 * under) * sheen - 2.5 * gutter, (1.0, 0.97, 0.85))
    return img


def chevron_marks(i, rng, img):
    if i == 1:
        return G.mark_damp(img, rng, (36, 32), 0.05, (1.0, 0.97, 0.85))
    return G.mark_paper_scuff(img, rng)


def tex_level0_revamp_chevron() -> None:
    base = chevron_base()
    G.save_png(base, _blk("l0_wallpaper_chevron"))
    G.save_png(G._baseboard(base), _blk("l0_wallpaper_chevron_baseboard"))


# =============================================================================
# Wood, metal, plastic and fabric
# =============================================================================

WOOD = (107, 62, 34)          # the film's dark stained cap rails, #6B3E22


def _wood(rng, base=WOOD, along_x=True) -> np.ndarray:
    """Dark stained hardwood: fine grain lines running along x (or y), a few darker figure
    streaks, a soft satin sheen."""
    aniso = (14, 1) if along_x else (1, 14)      # (n, 1) stretches the noise along x
    img = _fill(base)
    grain = G.fnoise(rng, beta=1.4, aniso=aniso, kmin=2)
    figure = G.fnoise(rng, beta=2.2, aniso=aniso, kmin=1)
    lum = 7 * grain + 6 * figure + 1.5 * rng.normal(0, 1, (S, S))
    img = G.add_lum(img, lum, (1.0, 0.8, 0.6))
    return G.add_lum(img, 5 * G.smoothstep(0.8, 2.0, G.fnoise(rng, beta=1.0, aniso=aniso, kmin=6)), (1.0, 0.85, 0.7))


def _paint(rng, base, mottle=1.6, fine=0.6) -> np.ndarray:
    """Smooth painted steel or plastic: very soft mottling and a fine grain."""
    return G._smooth_concrete(rng, base, mottle=mottle, fine=fine)


def tex_level0_revamp_materials() -> None:
    rng = G.rng_for(60110)
    rail = _wood(rng)
    X, Y = G.grid()
    G.save_png(rail, _blk("l0_wood_rail"))
    # Ladder steel: dark grey enamel, worn bright where hands and boots go.
    steel = _paint(rng, (78, 80, 80), mottle=2.5, fine=1.0)
    wear = G.smoothstep(0.9, 1.8, G.fnoise(rng, beta=1.4, aniso=(1, 4), kmin=3))
    G.save_png(G.over(steel, np.array([130, 128, 120]), wear * 0.5), _blk("l0_ladder_steel"))
    # Galvanised conduit and switch-box steel: pale grey, a faint zinc spangle.
    zinc = _paint(rng, (168, 170, 166), mottle=2.0, fine=0.8)
    zinc = G.add_lum(zinc, 4 * G.blur(G.fnoise(rng, beta=0.6, kmin=10), 1.2))
    G.save_png(zinc, _blk("l0_conduit"))
    # Mannequin plastic: matte warm grey, very smooth, with faint handling marks.
    plastic = _paint(rng, (166, 158, 156), mottle=2.2, fine=0.4)
    G.save_png(G.add_lum(plastic, -4 * G.smoothstep(1.0, 2.2, G.fnoise(rng, beta=2.0))), _blk("l0_mannequin"))
    # Its round display base: pale blue-green glass, darker towards the edge.
    glass = _paint(rng, (150, 172, 172), mottle=1.5, fine=0.3)
    G.save_png(G.add_lum(glass, 14 * np.exp(-((X - 40) / 30) ** 2 - ((Y - 40) / 30) ** 2)), _blk("l0_mannequin_glass"))
    # Red price/size sticker with a white mark.
    tag = _fill((196, 36, 32))
    mark = G.pil_mask(lambda d, k: d.line([(40 * k, 34 * k), (86 * k, 34 * k), (58 * k, 98 * k)], fill=255, width=int(14 * k)))
    G.save_png(G.over(G.add_lum(tag, rng.normal(0, 2, (S, S))), np.array([240, 236, 230]), mark), _blk("l0_red_tag"))
    # Black rubber cable and bare copper.
    rubber = _paint(rng, (30, 30, 32), mottle=2.0, fine=0.8)
    G.save_png(G.add_lum(rubber, 10 * np.exp(-((X % 32 - 12) / 4) ** 2)), _blk("l0_cable"))
    copper = G.add_lum(_fill((184, 104, 52)), 18 * np.sin(Y / 3.0) + rng.normal(0, 6, (S, S)))
    G.save_png(copper, _blk("l0_copper"))


def _gilt(rng) -> np.ndarray:
    """Gilded carved wood: warm gold, a reeded/beaded moulding pattern, darker gold in the
    recesses and a few bright worn highlights. Kept smooth: gold reads by its broad sheen."""
    X, Y = G.grid()
    img = _fill((188, 150, 64))
    sheen = G.fnoise(rng, beta=3.0, kmin=1, kmax=5)
    reeds = np.cos(2 * np.pi * X / 16)                                                # carved reeds along y
    beads = G.smoothstep(0.3, 0.9, np.cos(2 * np.pi * Y / 10) * (np.abs(G.wrapd(X, 32)) < 5))
    img = G.add_lum(img, 16 * sheen + 10 * reeds + 14 * beads, (1.0, 0.9, 0.6))
    img = G.over(img, np.array([104, 74, 28]), G.smoothstep(0.7, 1.0, -reeds) * 0.45)   # dark gold in the grooves
    return G.add_lum(img, 26 * G.smoothstep(1.2, 2.0, sheen) * G.smoothstep(0.6, 1.0, reeds), (1.0, 0.97, 0.85))


def _velvet(rng, tufted=False) -> np.ndarray:
    """Royal purple velvet: soft pile sheen, crushed patches; tufted = buttoned diamond pattern."""
    img = _fill((92, 46, 120))
    pile = G.fnoise(rng, beta=2.4, kmin=2)
    img = G.add_lum(img, 8 * pile + 1.2 * rng.normal(0, 1, (S, S)), (1.0, 0.7, 1.0))
    if tufted:
        # buttons on a diamond grid, the fabric pulled into creases between them
        X, Y = G.grid()
        u, v = G.wrapd(X, 32), G.wrapd(Y - 16, 32)
        u2, v2 = G.wrapd(X - 16, 32), G.wrapd(Y, 32)
        d = np.minimum(np.hypot(u, v), np.hypot(u2, v2))
        crease = np.exp(-(G.wrapd(X + Y - 16, 32) / 1.6) ** 2) + np.exp(-(G.wrapd(X - Y - 16, 32) / 1.6) ** 2)
        img = G.add_lum(img, -34 * np.exp(-(d / 3.2) ** 2) + 12 * G.smoothstep(4, 12, d) - 8 * crease - 6, (1.0, 0.7, 1.0))
    return img


def tex_level0_revamp_throne() -> None:
    rng = G.rng_for(60120)
    G.save_png(_gilt(rng), _blk("l0_gilt"))
    G.save_png(_velvet(rng), _blk("l0_velvet"))
    G.save_png(_velvet(rng, tufted=True), _blk("l0_velvet_tufted"))


def tex_level0_revamp_case() -> None:
    """Async hard equipment case: black ribbed polypropylene, a lid seam, a white ASYNC sticker."""
    rng = G.rng_for(60130)
    X, Y = G.grid()
    shell = _paint(rng, (34, 34, 36), mottle=2.0, fine=1.2)
    ribs = G.smoothstep(0.4, 0.9, np.cos(2 * np.pi * X / 16))
    side = G.add_lum(shell, 10 * ribs)
    side = G.add_lum(side, 6 * G.box((S, S), -1, 80, S + 1, 82))                 # the lit top edge of the shell
    G.save_png(side, _blk("l0_case_side"))
    top = G.add_lum(shell, 6 * G.smoothstep(0.6, 0.95, np.cos(2 * np.pi * Y / 24)))
    label = G.box((S, S), 28, 48, 100, 80)
    top = G.over(top, np.array([226, 224, 214]), label)
    text = G.pil_mask(lambda d, k: d.text((64 * k, 61 * k), "ASYNC", fill=255, anchor="mm", font=G.font(15 * k)))
    top = G.over(top, np.array([24, 24, 26]), text)
    bar = G.pil_mask(lambda d, k: d.rectangle((36 * k, 75 * k, 92 * k, 78 * k), fill=255))
    top = G.over(top, np.array([214, 170, 30]), bar)                                 # the yellow hazard bar
    G.save_png(top, _blk("l0_case_top"))
    metal = _paint(rng, (150, 150, 146), mottle=2.0, fine=0.8)
    G.save_png(metal, _blk("l0_case_latch"))


# =============================================================================
# Ceiling: the corridor light strip and the missing tile
# =============================================================================

def _strip(on: bool) -> np.ndarray:
    """One block of the film corridor's continuous light strip: a 5 px (of 16) lens running along
    x down the middle of a ceiling tile, in a thin painted frame. The strip runs out of both ends
    of the block (no T-bar across it), so a row of them reads as one long light.

    The whole block draws full-bright when lit, so the tile either side is darkened a little to
    sit next to the ordinary (lit, not glowing) tiles around it."""
    rng = G.rng_for(60140 + int(on))
    X, Y = G.grid()
    img = G._ceiling_tile(stain=False, seed=60142)
    if on:
        img = img * 0.86
    c0, c1 = S / 2 - 20, S / 2 + 20                     # 5 px of 16
    dy = np.minimum(np.abs(Y - c0), np.abs(Y - c1))
    inside = (Y > c0 - 4) & (Y < c1 + 4)
    frame = _paint(rng, (206, 206, 200), mottle=1.0, fine=0.4)
    img = np.where(inside[..., None], frame, img)
    img = G.add_lum(img, -9 * np.exp(-((Y - (c0 - 4.5)) / 1.0) ** 2) - 9 * np.exp(-((Y - (c1 + 4.5)) / 1.0) ** 2))
    lens = G.box((S, S), -1, c0, S + 1, c1)
    tube = np.exp(-((Y - S / 2) / 7.0) ** 2)
    pr = G._prism(X, Y)
    if on:
        diff = G.add_lum(_fill((242, 250, 240)), 10 * tube - 2 * pr - 5 * G.smoothstep(6, 0, dy), (0.96, 1.0, 0.97))
    else:
        diff = G.add_lum(_fill((170, 174, 172)), 8 * tube - 3 * pr - 6 * G.smoothstep(8, 0, dy) + 2 * G.fnoise(rng, beta=3.0))
    img = G.over(img, diff, lens)
    return G.add_lum(img, -12 * np.exp(-(dy / 0.8) ** 2) * lens)    # the frame's inner lip


def tex_level0_revamp_strip() -> None:
    G.save_png(_strip(True), _blk("l0_troffer_strip"))
    G.save_png(_strip(False), _blk("l0_troffer_strip_off"))


def tex_level0_revamp_plenum() -> None:
    """Up in a missing tile: the dark plenum (dusty underside of the slab, a cable sagging across),
    a galvanised duct and a steel joist, all barely lit."""
    rng = G.rng_for(60150)
    X, Y = G.grid()
    slab = _paint(rng, (40, 37, 32), mottle=4.0, fine=1.5)
    slab = G.add_lum(slab, -14 * G.smoothstep(0.5, 1.5, G.fnoise(rng, beta=2.0)))
    cable = G.pil_mask(lambda d, k: d.line([(0, 30 * k), (40 * k, 52 * k), (90 * k, 58 * k), (128 * k, 40 * k)], fill=255, width=int(2.5 * k)))
    G.save_png(G.over(slab, np.array([16, 16, 18]), cable), _blk("l0_plenum"))
    # The inside edge of the tile opening: the cut edge of the tile board, then darkness above.
    edge = G.over(slab, _fill((150, 144, 126)), G.box((S, S), -1, S - 8, S + 1, S + 1))
    edge = G.add_lum(edge, -40 * G.smoothstep(S - 8, 0, Y) * (Y < S - 8))
    G.save_png(edge, _blk("l0_plenum_side"))
    duct = _paint(rng, (76, 78, 78), mottle=3.0, fine=1.0)
    duct = G.add_lum(duct, -18 * (np.abs(G.wrapd(X, 64)) < 1.2) + 8 * (np.abs(G.wrapd(X - 2, 64)) < 1.0))   # seams
    G.save_png(G.add_lum(duct, 10 * G.smoothstep(0.6, 1.6, G.fnoise(rng, beta=1.0, aniso=(8, 1), kmin=4))), _blk("l0_duct"))
    joist = _paint(rng, (58, 50, 44), mottle=3.0, fine=1.2)
    G.save_png(G.over(joist, np.array([96, 60, 36]), G.smoothstep(0.8, 1.8, G.fnoise(rng, beta=1.6)) * 0.5), _blk("l0_joist"))


# =============================================================================
# The exit alcove: EXIT sign and hazard stripes
# =============================================================================

SIGN_GREEN = (10, 112, 72)


def tex_level0_revamp_exit_sign() -> None:
    """Kane's green running-man EXIT sign (ref: the exit alcove): a dark green panel with a white
    border, the running figure, an arrow and a white door. Drawn in the top half of the texture
    (the sign is twice as wide as tall)."""
    rng = G.rng_for(60160)
    H = S // 2
    img = G.add_lum(G.fill(SIGN_GREEN, (S, S)), rng.normal(0, 1.5, (S, S)))
    white = np.array([244, 248, 240])

    def draw(d, k):
        d.rectangle((2 * k, 2 * k, (S - 2) * k, (H - 2) * k), outline=255, width=int(3 * k))
        # the running man: head, torso leaning forward, arms and legs mid-stride
        d.ellipse((24 * k, 12 * k, 32 * k, 20 * k), fill=255)
        w = int(5 * k)
        d.line([(27 * k, 22 * k), (22 * k, 36 * k)], fill=255, width=w)                       # torso
        d.line([(27 * k, 24 * k), (34 * k, 30 * k), (38 * k, 27 * k)], fill=255, width=int(4 * k))   # front arm
        d.line([(26 * k, 24 * k), (19 * k, 27 * k), (15 * k, 33 * k)], fill=255, width=int(4 * k))   # back arm
        d.line([(22 * k, 36 * k), (30 * k, 42 * k), (28 * k, 52 * k)], fill=255, width=w)      # front leg
        d.line([(22 * k, 36 * k), (16 * k, 44 * k), (9 * k, 45 * k)], fill=255, width=w)       # back leg
        # the arrow
        d.line([(44 * k, H / 2 * k), (70 * k, H / 2 * k)], fill=255, width=int(5 * k))
        d.polygon([(70 * k, (H / 2 - 7) * k), (80 * k, H / 2 * k), (70 * k, (H / 2 + 7) * k)], fill=255)
        # the door, open a crack
        d.rectangle((90 * k, 10 * k, 116 * k, (H - 9) * k), fill=255)

    m = G.pil_mask(draw)
    img = G.over(img, white, m)
    door_gap = G.box((S, S), 94, 14, 112, H - 13)
    img = G.over(img, np.array(SIGN_GREEN) * 0.8, door_gap)
    img[H:] = G.fill((236, 238, 232), (S - H, S))                     # the bottom half: the plastic edge
    G.save_png(img, _blk("l0_exit_sign"))
    G.save_png(_paint(rng, (232, 234, 228), mottle=1.0, fine=0.4), _blk("l0_exit_sign_edge"))


def tex_level0_revamp_hazard() -> None:
    """Green and white diagonal hazard stripes (45 degrees, 16 px each), painted on steel; a
    little scuffed, the paint worn through at the lower corners."""
    rng = G.rng_for(60170)
    X, Y = G.grid()
    t = (X + Y) % 32
    stripe = G.smoothstep(0.0, 0.8, t) * G.smoothstep(16.8, 16.0, t)
    green = G.add_lum(_fill((26, 112, 72)), rng.normal(0, 2, (S, S)))
    white = G.add_lum(_fill((232, 232, 222)), rng.normal(0, 2, (S, S)))
    img = G.lerp(white, green, stripe[..., None])
    scuff = G.smoothstep(0.9, 1.8, G.fnoise(rng, beta=1.0, aniso=(1, 5), kmin=4)) * G.smoothstep(60, 128, Y)
    img = G.over(img, np.array([120, 120, 112]), scuff * 0.35)
    G.save_png(G.add_lum(img, 3 * G.fnoise(rng, beta=3.0)), _blk("l0_hazard_frame"))


# =============================================================================
# Switch box
# =============================================================================

def tex_level0_revamp_switch_box() -> None:
    """Grey painted steel switch box face (two toggle switches in a cover plate, four screws).
    The plate sits at pixels 40-88 x 24-96: that is where the model's box takes its front from."""
    rng = G.rng_for(60180)
    img = _paint(rng, (176, 176, 170), mottle=2.0, fine=0.8)
    X, Y = G.grid()
    plate = G.box((S, S), 42, 26, 86, 94)
    img = G.add_lum(img, 8 * plate - 10 * (G.box((S, S), 41, 25, 87, 95) - plate))
    for cy in (46, 74):
        slot = G.box((S, S), 58, cy - 7, 70, cy + 7)
        img = G.over(img, np.array([60, 60, 58]), slot)
    for sx, sy in ((48, 31), (80, 31), (48, 89), (80, 89)):
        screw = np.exp(-((X - sx) ** 2 + (Y - sy) ** 2) / 4.0)
        img = G.add_lum(img, -30 * screw + 20 * np.exp(-((X - sx + 0.8) ** 2 + (Y - sy + 0.8) ** 2) / 1.0))
    img = G.add_lum(img, -14 * G.smoothstep(0.8, 1.6, G.fnoise(rng, beta=1.8)) * plate)   # grimy where hands go
    G.save_png(img, _blk("l0_switch_box"))


# =============================================================================
# Wall scrawls (two blocks wide)
# =============================================================================

# A rough hand-lettering stroke alphabet: each letter is a list of strokes on a box 1 high (0 =
# top) and `w` wide, drawn with a slightly wobbly brush (see _brush).
_O = [[(0.32 + 0.32 * np.cos(a), 0.5 + 0.5 * np.sin(a)) for a in np.linspace(-np.pi / 2, 1.5 * np.pi, 14)]]
LETTERS = {
    "A": (0.64, [[(0, 1), (0.32, 0), (0.64, 1)], [(0.14, 0.62), (0.5, 0.62)]]),
    "C": (0.6, [[(0.6, 0.12), (0.4, 0), (0.15, 0.05), (0, 0.4), (0.05, 0.8), (0.3, 1), (0.6, 0.9)]]),
    "D": (0.6, [[(0, 0), (0, 1)], [(0, 0), (0.35, 0.05), (0.6, 0.3), (0.6, 0.7), (0.35, 0.95), (0, 1)]]),
    "E": (0.55, [[(0.55, 0), (0, 0), (0, 1), (0.55, 1)], [(0, 0.5), (0.42, 0.5)]]),
    "G": (0.62, [[(0.6, 0.15), (0.4, 0), (0.15, 0.05), (0, 0.35), (0, 0.7), (0.2, 0.97), (0.45, 1),
                  (0.62, 0.85), (0.62, 0.6), (0.35, 0.6)]]),
    "H": (0.6, [[(0, 0), (0, 1)], [(0.6, 0), (0.6, 1)], [(0, 0.5), (0.6, 0.5)]]),
    "I": (0.12, [[(0.06, 0), (0.06, 1)]]),
    "L": (0.5, [[(0, 0), (0, 1), (0.5, 1)]]),
    "M": (0.75, [[(0, 1), (0, 0), (0.37, 0.6), (0.75, 0), (0.75, 1)]]),
    "N": (0.6, [[(0, 1), (0, 0), (0.6, 1), (0.6, 0)]]),
    "O": (0.64, _O),
    "R": (0.6, [[(0, 1), (0, 0), (0.4, 0.02), (0.58, 0.18), (0.5, 0.42), (0, 0.5)], [(0.22, 0.5), (0.62, 1)]]),
    "S": (0.6, [[(0.6, 0.1), (0.4, 0), (0.15, 0.02), (0, 0.2), (0.1, 0.42), (0.5, 0.58), (0.62, 0.8),
                 (0.45, 1), (0.15, 0.98), (0, 0.88)]]),
    "T": (0.66, [[(0, 0), (0.66, 0)], [(0.33, 0), (0.33, 1)]]),
    "V": (0.64, [[(0, 0), (0.32, 1), (0.64, 0)]]),
    "Y": (0.64, [[(0, 0), (0.32, 0.5), (0.64, 0)], [(0.32, 0.5), (0.32, 1)]]),
    "'": (0.1, [[(0.08, 0), (0.03, 0.22)]]),
    " ": (0.35, []),
}

PAINT = (30, 26, 14)


def _line_width(text) -> float:
    """Width of a line of text in letter heights (before the random size changes)."""
    return sum(LETTERS[ch][0] + 0.24 for ch in text) - 0.24


def _scrawl_line(rng, text, cx, y, h, max_w=224.0, tilt=0.0):
    """Strokes of one line of rough capitals centred on x = cx, top at y, letters about h px
    high (smaller if the line would not fit in max_w). Each letter is a bit bigger or smaller and
    higher or lower than the last, and the whole line drifts by `tilt` px per px."""
    h = min(h, max_w / (_line_width(text) * 1.08))
    x = cx - _line_width(text) * h / 2 + rng.normal(0, 2)
    strokes, bottoms = [], []
    for ch in text:
        w, letter = LETTERS[ch]
        s = h * rng.uniform(0.9, 1.12)
        dy = rng.normal(0, h * 0.05) + tilt * (x - cx) + (h - s) * 0.5
        for st in letter:
            pts = [(x + px * s * 0.92, y + dy + py * s) for px, py in st]
            strokes.append(pts)
            bottoms += [p for p in pts if p[1] > y + dy + 0.9 * s]
        x += (w * 0.92 + 0.24) * s
    return strokes, bottoms


def _brush(img, rng, strokes, width=3.0, color=PAINT, alpha=0.9) -> np.ndarray:
    """Paint hand strokes: gently wobbling lines with round ends, the paint a little patchy
    (thinner where the brush ran dry)."""
    lines = [G.wobble_line(rng, s, jitter=0.3, steps=5) for s in strokes]
    widths = [width * rng.uniform(0.8, 1.15) for _ in lines]

    def draw(d, k):
        for ln, w in zip(lines, widths):
            p = [(x * k, y * k) for x, y in ln]
            d.line(p, fill=255, width=int(w * k), joint="curve")
            r = w * k / 2
            for x, y in (p[0], p[-1]):
                d.ellipse((x - r, y - r, x + r, y + r), fill=255)

    m = G.pil_mask(draw, img.shape[:2])
    dry = 0.78 + 0.22 * np.clip(G.fnoise(rng, img.shape[:2], beta=1.6, kmin=4), -1, 1)
    return G.over(img, np.array(color), m * alpha * dry)


def _paint_drips(rng, img, bottoms, chance=0.12):
    """A few thin paint runs hanging down from stroke ends."""
    runs = [[(bx, by), (bx + rng.normal(0, 0.3), by + rng.uniform(3, 9))] for bx, by in bottoms if rng.random() < chance]
    return _brush(img, rng, runs, width=1.1, alpha=0.75) if runs else img


def _scrawl_sheet(variant: int) -> np.ndarray:
    """The two-block (256 x 128) drawing on clean Kane paper (the right half has its own paper
    copy, so both blocks join the plain wall next to them seamlessly)."""
    rng = G.rng_for(60190 + variant)
    paper = G._wallpaper(stains=False)
    img = np.concatenate([paper, paper], axis=1)
    strokes, bottoms = [], []
    lines = {0: (("DON'T MOVE", 120, 24, 32), ("STAY STILL", 134, 72, 34)),
             1: (("IT HEARS ME", 128, 42, 34),),
             2: (("GOD CLOSED", 124, 24, 31), ("HIS EYES", 136, 72, 33))}
    if variant in lines:
        for text, cx, y, h in lines[variant]:
            s, b = _scrawl_line(rng, text, cx, y, h, tilt=rng.uniform(-0.04, 0.04))
            strokes += s
            bottoms += b
        if variant == 1:
            strokes.append([(30, 90), (128, 94), (226, 87)])               # angrily underlined
        width = 3.0
    else:
        # a child's doodle: a window with a cross of panes and a big sun with rays shining in it,
        # and next to it a stick figure looking at it
        strokes += [[(28, 22), (110, 20), (112, 104), (26, 106), (28, 22)], [(69, 21), (69, 105)], [(27, 63), (111, 62)]]
        sx, sy = 48, 42
        strokes += [[(sx + 9 * np.cos(a), sy + 9 * np.sin(a)) for a in np.linspace(0, 2 * np.pi, 13)]]
        for a in np.linspace(0, 2 * np.pi, 9)[:-1] + 0.2:
            strokes.append([(sx + 12 * np.cos(a), sy + 12 * np.sin(a)), (sx + 17 * np.cos(a), sy + 17 * np.sin(a))])
        fx = 176
        strokes += [[(fx + 8 * np.cos(a), 44 + 8 * np.sin(a)) for a in np.linspace(0, 2 * np.pi, 11)],
                    [(fx, 52), (fx + 1, 82)], [(fx - 14, 62), (fx, 58), (fx + 14, 64)],
                    [(fx - 10, 104), (fx + 1, 82), (fx + 12, 104)]]
        width = 2.6
    img = _brush(img, rng, strokes, width=width)
    return _paint_drips(rng, img, bottoms)


def tex_level0_revamp_graffiti() -> None:
    for v in range(4):
        sheet = _scrawl_sheet(v)
        for part in (0, 1):
            G.save_png(sheet[:, part * S:(part + 1) * S], _blk(f"l0_graffiti_{v}_{part}"))


# =============================================================================
# Tape, wall hole
# =============================================================================

def tex_level0_revamp_tape() -> None:
    """Teal cloth gaffer tape (#1F8FA8): a fine woven texture with a soft satin sheen."""
    rng = G.rng_for(60200)
    X, Y = G.grid()
    img = _fill((31, 143, 168))
    weave = 0.5 * np.cos(2 * np.pi * X / 3) + 0.5 * np.cos(2 * np.pi * Y / 3)
    img = G.add_lum(img, 4 * weave + 4 * G.fnoise(rng, beta=2.5) + 2 * rng.normal(0, 1, (S, S)), (0.7, 1.0, 1.0))
    G.save_png(img, _blk("l0_tape"))


def tex_level0_revamp_hole() -> None:
    """Inside the dark square hole in a wall: raw grey-brown board going black towards the back."""
    rng = G.rng_for(60210)
    X, Y = G.grid()
    back = _paint(rng, (22, 20, 16), mottle=3.0, fine=1.2)
    G.save_png(back, _blk("l0_hole_back"))
    # The lining: the model maps the hole's 6 px depth onto the first 48 px of the texture, from
    # the wall face (bright, it catches the room light) to the back (black). l0_hole_side runs
    # that way along x, l0_hole_floor along y.
    side = _paint(rng, (88, 80, 52), mottle=3.0, fine=1.2)
    side = G.add_lum(side, -64 * G.smoothstep(0, 48, X))
    G.save_png(side, _blk("l0_hole_side"))
    floor = _paint(rng, (88, 80, 52), mottle=3.0, fine=1.2)
    G.save_png(G.add_lum(floor, -64 * G.smoothstep(0, 48, Y)), _blk("l0_hole_floor"))


def tex_level0_revamp_plastic() -> None:
    """Off-white moulded garden-chair plastic (the monobloc chair): smooth, a faint sheen, grey
    grime in a few scuffs."""
    rng = G.rng_for(60220)
    img = _paint(rng, (214, 212, 204), mottle=2.5, fine=0.5)
    grime = G.smoothstep(1.0, 2.0, G.fnoise(rng, beta=1.8))
    G.save_png(G.over(img, np.array([150, 146, 136]), grime * 0.35), _blk("l0_plastic"))


def tex_level0_revamp_mattress() -> None:
    """An old mattress in blue-grey ticking: thin stripes along its length on off-white cloth,
    a few faded stains, and the edges shaded and piped so the one thick slab reads as rounded.
    The top is stretched over the mattress's whole length (stripes run along v); the side is
    squeezed onto its 5 px height."""
    rng = G.rng_for(60230)
    X, Y = G.grid()
    cloth = _paint(rng, (222, 218, 204), mottle=2.0, fine=0.8)
    stripes = (np.abs(G.wrapd(X, 16)) < 1.2) | (np.abs(G.wrapd(X - 5, 16)) < 0.6)
    top = G.over(cloth, np.array([112, 128, 146]), stripes * 0.55)
    stain = G.smoothstep(0.9, 1.8, G.fnoise(rng, beta=2.4))
    top = G.over(top, np.array([176, 156, 110]), stain * 0.3)
    edge = np.minimum(np.minimum(X, S - X), np.minimum(Y, S - Y))
    top = G.add_lum(top, -30 * G.smoothstep(14, 0, edge) + 14 * np.exp(-((edge - 9) / 2.5) ** 2))   # rounded rim
    top = G.add_lum(top, -18 * np.exp(-((edge - 4) / 0.9) ** 2))                                     # piping
    G.save_png(top, _blk("l0_mattress_top"))
    side = G.over(cloth, np.array([112, 128, 146]), ((np.abs(G.wrapd(Y, 32)) < 2.5)) * 0.5)
    side = G.add_lum(side, -36 * G.smoothstep(40, 0, Y) - 36 * G.smoothstep(S - 40, S, Y)
                     + 16 * np.exp(-((Y - 44) / 8) ** 2))                                            # rolls over at top and bottom
    side = G.add_lum(side, -20 * (np.exp(-((Y - 12) / 2) ** 2) + np.exp(-((Y - (S - 12)) / 2) ** 2)))   # piping
    G.save_png(G.over(side, np.array([176, 156, 110]), stain * 0.25), _blk("l0_mattress_side"))


TEXTURE_GENS = [tex_level0_revamp_chevron, tex_level0_revamp_materials, tex_level0_revamp_throne,
                tex_level0_revamp_case, tex_level0_revamp_strip, tex_level0_revamp_plenum,
                tex_level0_revamp_exit_sign, tex_level0_revamp_hazard, tex_level0_revamp_switch_box,
                tex_level0_revamp_graffiti, tex_level0_revamp_tape, tex_level0_revamp_hole,
                tex_level0_revamp_plastic, tex_level0_revamp_mattress]


# =============================================================================
# Models
# =============================================================================

_el, _model, _bm, _bs, _im = P._el, P._model, P._bm, P._bs, P._im
SIDES = ("north", "south", "east", "west")
FACING_Y = P.FACING_Y


def _facing_variants(models: dict, extra_props: dict | None = None) -> dict:
    """{"facing=..,<props>": {...}} for models built facing north. models maps the extra
    property string ("" or "half=lower" ...) to a model name."""
    variants = {}
    for facing, y in FACING_Y.items():
        for props, model in models.items():
            key = f"facing={facing}" + (f",{props}" if props else "")
            v = {"model": _ref(model)}
            if y:
                v["y"] = y
            variants[key] = v
    return variants


def _wall_models() -> None:
    # Chevron paper: plain models (the variant models and blockstates come from gen_assets
    # variant_models, which copies these).
    _bm("l0_wallpaper_chevron", {"parent": "minecraft:block/cube_all", "textures": {"all": _ref("l0_wallpaper_chevron")}})
    _bm("l0_wallpaper_chevron_baseboard", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "side": _ref("l0_wallpaper_chevron_baseboard"), "top": _ref("l0_wallpaper_chevron"),
        "bottom": _ref("l0_wallpaper_chevron")}})
    _im("l0_wallpaper_chevron")
    _im("l0_wallpaper_chevron_baseboard")

    # Half-wall cap: paper to 8 px, the dark wood rail 8-10 px overhanging both faces by 3/4 px,
    # with a thin lighter top bevel strip.
    tex = {"particle": _ref("l0_wood_rail"), "paper": _ref("yellow_wallpaper"), "wood": _ref("l0_wood_rail")}
    e = [_el((0, 0, 0), (16, 8, 16), "#paper", faces=SIDES + ("down",)),
         _el((0, 8, -0.75), (16, 10, 16.75), "#wood"),
         _el((0, 10, -0.25), (16, 10.35, 16.25), "#wood", faces=("up", "north", "south"))]
    _bm("l0_half_wall_cap", _model(tex, e, render=None))
    _bs("l0_half_wall_cap", {"variants": _facing_variants({"": "l0_half_wall_cap"})})
    _im("l0_half_wall_cap")

    # Hole in the wall: a 12 x 12 px square sunk 6 px into the north face.
    tex = {"particle": _ref("yellow_wallpaper"), "paper": _ref("yellow_wallpaper"),
           "back": _ref("l0_hole_back"), "side": _ref("l0_hole_side"), "floor": _ref("l0_hole_floor")}
    e = [_el((0, 0, 0), (2, 16, 16), "#paper", faces=("north", "south", "west", "up", "down")),
         _el((14, 0, 0), (16, 16, 16), "#paper", faces=("north", "south", "east", "up", "down")),
         _el((2, 0, 0), (14, 2, 16), "#paper", faces=("north", "south", "down")),
         _el((2, 14, 0), (14, 16, 16), "#paper", faces=("north", "south", "up")),
         _el((2, 2, 6), (14, 14, 16), {"north": "#back", "south": "#paper"}, faces=("north", "south")),
         # The lining of the hole: flat planes facing into it, bright at the mouth (z = 0) and
         # dark at the back. l0_hole_side darkens along its u, l0_hole_floor along its v; the UVs
         # are flipped where needed so the bright end always sits at the mouth.
         _el((2, 2, 0), (2, 14, 6), "#side", faces=("east",), uv={"east": [6, 2, 0, 14]}),
         _el((14, 2, 0), (14, 14, 6), "#side", faces=("west",), uv={"west": [0, 2, 6, 14]}),
         _el((2, 2, 0), (14, 2, 6), "#floor", faces=("up",), uv={"up": [2, 0, 14, 6]}),
         _el((2, 14, 0), (14, 14, 6), "#floor", faces=("down",), uv={"down": [2, 6, 14, 0]})]
    _bm("l0_wall_hole", _model(tex, e, render=None))
    _bs("l0_wall_hole", {"variants": _facing_variants({"": "l0_wall_hole"})})
    _im("l0_wall_hole")

    # Graffiti: full wall blocks, the scrawl on the north face.
    models = {}
    for v in range(4):
        for part in (0, 1):
            name = f"l0_graffiti_{v}_{part}"
            paper = _ref("yellow_wallpaper")
            _bm(name, {"parent": "minecraft:block/cube", "textures": {
                "particle": paper, "north": _ref(name), "south": paper, "east": paper, "west": paper,
                "up": paper, "down": paper}})
            models[f"part={part},variant={v}"] = name
    _bs("l0_graffiti", {"variants": _facing_variants(models)})
    _im("l0_graffiti", parent=_ref("l0_graffiti_0_0"))

    P._cube("l0_hazard_frame", "l0_hazard_frame")


def _floor_models() -> None:
    P._stairs("l0_carpet_stairs", "lobby_carpet")
    P._slab("l0_carpet_slab", "lobby_carpet", "lobby_carpet")


def _ceiling_models() -> None:
    for name in ("l0_troffer_strip", "l0_troffer_strip_off"):
        tile = _ref("ceiling_tile")
        _bm(name, {"parent": "minecraft:block/cube", "textures": {
            "particle": _ref(name), "down": _ref(name), "up": tile, "north": tile, "south": tile,
            "east": tile, "west": tile}})
        P._axis_state(name)
        _im(name)

    # Missing tile: the grid rim at the bottom, the cut edge of the tile opening, the plenum
    # above with a duct and a joist crossing it.
    tex = {"particle": _ref("ceiling_tile"), "tile": _ref("ceiling_tile"), "slab": _ref("l0_plenum"),
           "edge": _ref("l0_plenum_side"), "duct": _ref("l0_duct"), "joist": _ref("l0_joist")}
    rim = 2.2
    e = [_el((0, 0, 0), (rim, 0.8, 16), "#tile", faces=("down", "east")),
         _el((16 - rim, 0, 0), (16, 0.8, 16), "#tile", faces=("down", "west")),
         _el((rim, 0, 0), (16 - rim, 0.8, rim), "#tile", faces=("down", "south")),
         _el((rim, 0, 16 - rim), (16 - rim, 0.8, 16), "#tile", faces=("down", "north")),
         # the walls of the opening, facing in
         _el((0, 0.8, 0), (0, 16, 16), "#edge", faces=("east",)),
         _el((16, 0.8, 0), (16, 16, 16), "#edge", faces=("west",)),
         _el((0, 0.8, 0), (16, 16, 0), "#edge", faces=("south",)),
         _el((0, 0.8, 16), (16, 16, 16), "#edge", faces=("north",)),
         _el((0, 16, 0), (16, 16, 16), "#slab", faces=("down",)),
         # a round-ish duct along x (a box and the same box turned 45 degrees) and a joist along z
         _el((0, 9, 3), (16, 13.5, 8.5), "#duct", faces=("down", "north", "south")),
         _el((0, 8.9, 3.5), (16, 13.6, 8), "#duct", faces=("down", "north", "south"), rot=((8, 11.25, 5.75), "x", 45)),
         _el((10, 13.5, 0), (12.5, 16, 16), "#joist", faces=("down", "east", "west"))]
    _bm("l0_ceiling_hole", _model(tex, e, render=None, ao=False))
    P._plain_state("l0_ceiling_hole")
    _im("l0_ceiling_hole")

    # Dangling cable: two cables out of the ceiling, one long with a frayed copper end. Each
    # cable is a chain of straight pieces, each hanging from the bottom end of the one above and
    # swinging off a little (Minecraft only turns boxes by multiples of 22.5 degrees).
    tex = {"particle": _ref("l0_cable"), "cable": _ref("l0_cable"), "cu": _ref("l0_copper")}

    def hang(top, length, axis, angle, w, texture="#cable"):
        x, y, z = top
        box = _el((x - w / 2, y - length, z - w / 2), (x + w / 2, y, z + w / 2), texture,
                  rot=((x, y, z), axis, angle) if angle else None)
        a = np.radians(angle)
        # where the bottom end ends up after the turn (+x turns y towards z, +z turns x towards y)
        if axis == "x":
            bottom = (x, y - length * np.cos(a), z - length * np.sin(a))
        else:
            bottom = (x + length * np.sin(a), y - length * np.cos(a), z)
        return box, bottom

    e = []
    end = (8.0, 16.0, 8.0)
    for length, axis, angle in ((6.0, "x", 0), (6.2, "x", 22.5), (5.6, "z", -22.5), (2.5, "x", 0)):
        box, end = hang(end, length, axis, angle, 1.0)
        e.append(box)
    for axis, angle in (("z", 22.5), ("x", -22.5), ("z", -22.5)):          # the frayed copper strands
        e.append(hang(end, 1.6, axis, angle, 0.35, "#cu")[0])
    end = (5.5, 16.0, 9.6)
    for length, axis, angle in ((4.0, "x", 0), (3.6, "z", 22.5)):
        box, end = hang(end, length, axis, angle, 0.7)
        e.append(box)
    _bm("l0_dangling_cable", _model(tex, e, render=None))
    _bs("l0_dangling_cable", {"variants": _facing_variants({"": "l0_dangling_cable"})})
    _im("l0_dangling_cable")


def _wall_mount_models() -> None:
    # EXIT sign: 14 x 7 px, 1 px thick, the face from the top half of its texture.
    tex = {"particle": _ref("l0_exit_sign"), "face": _ref("l0_exit_sign"), "edge": _ref("l0_exit_sign_edge")}
    e = [_el((1, 4, 15), (15, 11, 16), {"north": "#face", "south": "#edge", "east": "#edge", "west": "#edge",
                                         "up": "#edge", "down": "#edge"}, uv={"north": [0, 0, 16, 8]})]
    _bm("l0_exit_sign", _model(tex, e, render=None))
    _bs("l0_exit_sign", {"variants": _facing_variants({"": "l0_exit_sign"})})
    _im("l0_exit_sign")

    # Switch box with a conduit stub out of its bottom (meets an upright conduit below).
    tex = {"particle": _ref("l0_switch_box"), "box": _ref("l0_switch_box"), "pipe": _ref("l0_conduit"),
           "dark": _ref("l0_hole_back")}
    e = [_el((5, 4, 14.5), (11, 13, 16), "#box"),
         _el((7.4, 9.7, 13.6), (8.6, 10.7, 14.5), "#pipe"),          # the two toggles
         _el((7.4, 6.2, 13.6), (8.6, 7.2, 14.5), "#pipe"),
         _el((6.6, 3, 14.6), (9.4, 4, 16), "#pipe"),                  # the conduit fitting
         _el((7, 0, 14.75), (9, 3, 16), "#pipe", faces=SIDES)]
    _bm("l0_switch_box", _model(tex, e, render=None))
    _bs("l0_switch_box", {"variants": _facing_variants({"": "l0_switch_box"})})
    _im("l0_switch_box")

    # Conduit: upright and sideways runs, each with one strap clamp.
    tex = {"particle": _ref("l0_conduit"), "pipe": _ref("l0_conduit")}
    up = [_el((7, 0, 14.75), (9, 16, 16), "#pipe", faces=SIDES + ("up", "down")),
          _el((6.4, 7, 14.5), (9.6, 8, 16), "#pipe", faces=SIDES + ("up", "down"))]
    side = [_el((0, 7, 14.75), (16, 9, 16), "#pipe", faces=SIDES + ("up", "down")),
            _el((8, 6.4, 14.5), (9, 9.6, 16), "#pipe", faces=SIDES + ("up", "down"))]
    _bm("l0_conduit_vertical", _model(tex, up, render=None))
    _bm("l0_conduit_horizontal", _model(tex, side, render=None))
    _bs("l0_conduit", {"variants": _facing_variants({"vertical=true": "l0_conduit_vertical",
                                                     "vertical=false": "l0_conduit_horizontal"})})
    _im("l0_conduit", parent=_ref("l0_conduit_vertical"))

    # Ladder: two rails, four rungs, two stand-off brackets to the wall.
    tex = {"particle": _ref("l0_ladder_steel"), "steel": _ref("l0_ladder_steel")}
    e = [_el((2.5, 0, 13.6), (3.7, 16, 15), "#steel", faces=SIDES),
         _el((12.3, 0, 13.6), (13.5, 16, 15), "#steel", faces=SIDES)]
    for y in (2, 6, 10, 14):
        e.append(_el((3.7, y, 13.9), (12.3, y + 0.9, 14.7), "#steel", faces=("north", "south", "up", "down")))
    for x0 in (2.6, 12.4):
        e.append(_el((x0, 8, 15), (x0 + 1, 9, 16), "#steel", faces=("east", "west", "up", "down")))
    _bm("l0_ladder", _model(tex, e, render=None))
    _bs("l0_ladder", {"variants": _facing_variants({"": "l0_ladder"})})
    _im("l0_ladder")


def _tape_models() -> None:
    """Tape strips 0.8 px wide, 0.1 px off the surface (so they never flicker against it)."""
    tex = {"particle": _ref("l0_tape"), "tape": _ref("l0_tape")}
    a, b = 7.6, 8.4
    wall_v = _el((a, 0, 15.9), (b, 16, 16), "#tape", faces=("north", "east", "west"))
    wall_h = _el((0, a, 15.9), (16, b, 16), "#tape", faces=("north", "up", "down"))
    fold = _el((a, 15.9, 0), (b, 16, 15.9), "#tape", faces=("down", "east", "west", "north"))
    ceil_x = _el((0, 15.9, a), (16, 16, b), "#tape", faces=("down", "north", "south"))
    ceil_z = _el((a, 15.85, 0), (b, 16, 16), "#tape", faces=("down", "east", "west"))
    floor_x = _el((0, 0, a), (16, 0.1, b), "#tape", faces=("up", "north", "south"))
    floor_z = _el((a, 0, 0), (b, 0.15, 16), "#tape", faces=("up", "east", "west"))
    for name, els in (("l0_tape_wall_vertical", [wall_v]), ("l0_tape_wall_horizontal", [wall_h]),
                      ("l0_tape_wall_corner", [wall_v, fold]),
                      ("l0_tape_ceiling_x", [ceil_x]), ("l0_tape_ceiling_z", [ceil_z]), ("l0_tape_ceiling_cross", [ceil_x, ceil_z]),
                      ("l0_tape_floor_x", [floor_x]), ("l0_tape_floor_z", [floor_z]), ("l0_tape_floor_cross", [floor_x, floor_z])):
        _bm(name, _model(tex, els, render=None))
    variants = {}
    for facing, y in FACING_Y.items():
        for shape in ("horizontal", "vertical", "corner"):
            v = {"model": _ref(f"l0_tape_wall_{shape}")}
            if y:
                v["y"] = y
            variants[f"facing={facing},shape={shape}"] = v
    for facing, where in (("down", "ceiling"), ("up", "floor")):
        for shape, run in (("horizontal", "x"), ("vertical", "z"), ("corner", "cross")):
            variants[f"facing={facing},shape={shape}"] = {"model": _ref(f"l0_tape_{where}_{run}")}
    _bs("l0_tape", {"variants": variants})
    _im("l0_tape", parent=_ref("l0_tape_wall_vertical"))


def _mannequin_models() -> None:
    """A slim featureless shop mannequin (the film's grey female mannequins) on a round glass
    base with a thin rod up into one calf; red stickers on the hip and shoulder. Lower half: base,
    legs, hips (y 0-16). Upper half: waist, torso, arms, neck and head (y 16-32, drawn 0-16).
    Faces north."""
    tex = {"particle": _ref("l0_mannequin"), "skin": _ref("l0_mannequin"), "glass": _ref("l0_mannequin_glass"),
           "tag": _ref("l0_red_tag"), "rod": _ref("l0_conduit")}
    lower = [
        _el((3.5, 0, 3.5), (12.5, 0.5, 12.5), "#glass"),
        _el((3.5, 0.02, 3.5), (12.5, 0.48, 12.5), "#glass", rot=((8, 0.25, 8), "y", 45)),
        _el((9.2, 0.5, 8.6), (9.7, 5, 9.1), "#rod"),
        # feet (on tiptoe, heels up), shins, knees, thighs
        _el((6.1, 0.5, 6.6), (7.5, 1.4, 9.0), "#skin"),
        _el((8.5, 0.6, 6.4), (9.9, 1.5, 8.8), "#skin"),
        _el((6.2, 1.4, 7.4), (7.5, 8.2, 8.9), "#skin"),
        _el((8.6, 1.5, 7.0), (9.9, 8.2, 8.5), "#skin"),     # (Minecraft only turns boxes by 22.5-degree steps, so the pose is built straight)
        _el((5.9, 8, 7.1), (7.7, 14, 9.2), "#skin"),
        _el((8.3, 8, 7.0), (10.1, 14, 9.1), "#skin"),
        # hips
        _el((5.5, 12.8, 6.8), (10.5, 16, 9.6), "#skin"),
        _el((9.0, 13.6, 6.74), (10.0, 15.2, 6.8), "#tag", faces=("north",)),
    ]
    upper = [
        _el((6.0, 0, 7.0), (10.0, 3, 9.3), "#skin"),                    # waist
        _el((5.4, 3, 6.6), (10.6, 9.2, 9.6), "#skin"),                  # ribcage and chest
        _el((5.8, 5.6, 6.2), (10.2, 8.2, 6.6), "#skin"),                # bust
        _el((4.6, 8.6, 7.0), (11.4, 10.2, 9.3), "#skin"),               # shoulders
        _el((7.3, 10.2, 7.4), (8.7, 11.8, 8.8), "#skin"),               # neck
        _el((6.7, 11.6, 6.6), (9.3, 15.2, 9.6), "#skin"),               # head
        _el((7.0, 11.9, 6.3), (9.0, 14.6, 6.6), "#skin"),               # face
        # arms hanging down past the hips (the lower arms reach into the block below)
        _el((3.8, 2.6, 7.3), (5.0, 9.6, 8.7), "#skin"),
        _el((11.0, 2.6, 7.3), (12.2, 9.6, 8.7), "#skin"),
        _el((3.6, -3.8, 7.4), (4.6, 2.8, 8.6), "#skin"),
        _el((11.4, -3.8, 7.4), (12.4, 2.8, 8.6), "#skin"),
        _el((3.7, -5.6, 7.3), (4.5, -3.8, 8.9), "#skin"),
        _el((11.5, -5.6, 7.3), (12.3, -3.8, 8.9), "#skin"),
        _el((5.6, 7.0, 9.6), (6.6, 8.4, 9.66), "#tag", faces=("south",)),
    ]
    _bm("l0_mannequin_lower", _model(tex, lower, render=None))
    _bm("l0_mannequin_upper", _model(tex, upper, render=None))
    _bs("l0_mannequin", {"variants": _facing_variants({"half=lower": "l0_mannequin_lower",
                                                       "half=upper": "l0_mannequin_upper"})})
    _im("l0_mannequin", parent=_ref("l0_mannequin_lower"))


def _throne_model() -> None:
    """The lone throne: a gilded frame chair, purple velvet seat and buttoned back, a carved crest
    rail with finials. Faces north (the back stands on the south side)."""
    tex = {"particle": _ref("l0_gilt"), "gold": _ref("l0_gilt"), "seat": _ref("l0_velvet"),
           "back": _ref("l0_velvet_tufted")}
    e = []
    for x0, z0 in ((2, 2), (12.6, 2), (2, 12.4), (12.6, 12.4)):        # legs
        e.append(_el((x0, 0, z0), (x0 + 1.4, 7, z0 + 1.4), "#gold"))
    e += [_el((1.6, 6.6, 1.6), (14.4, 8.4, 14.4), "#gold"),               # seat rail
          _el((2.4, 8.4, 2.4), (13.6, 10, 13.2), "#seat"),                # cushion
          # back posts, crest and the buttoned panel
          _el((1.8, 8.4, 12.2), (3.4, 25, 14.2), "#gold"),
          _el((12.6, 8.4, 12.2), (14.2, 25, 14.2), "#gold"),
          _el((1.8, 23.6, 12.0), (14.2, 25.8, 14.4), "#gold"),
          _el((5, 25.8, 12.3), (11, 27.4, 14.1), "#gold"),
          _el((7, 27.4, 12.6), (9, 28.6, 13.8), "#gold"),
          _el((2, 25, 12.6), (3.2, 26.6, 13.8), "#gold"),
          _el((12.8, 25, 12.6), (14, 26.6, 13.8), "#gold"),
          _el((3.4, 10.5, 12.6), (12.6, 23.6, 13.6), "#back"),
          # arms: posts and rests
          _el((1.6, 8.4, 2.4), (3.0, 13, 3.8), "#gold"),
          _el((13.0, 8.4, 2.4), (14.4, 13, 3.8), "#gold"),
          _el((1.4, 13, 2.0), (3.2, 14.2, 12.4), "#gold"),
          _el((12.8, 13, 2.0), (14.6, 14.2, 12.4), "#gold"),
          _el((1.6, 14.2, 3.0), (3.0, 14.6, 11.5), "#seat"),
          _el((13.0, 14.2, 3.0), (14.4, 14.6, 11.5), "#seat")]
    _bm("l0_throne", _model(tex, e, render=None))
    _bs("l0_throne", {"variants": _facing_variants({"": "l0_throne"})})
    _im("l0_throne")


def _case_model() -> None:
    """Async hard case lying flat: ribbed black shell, carry handle on the lid, two latches on the
    front (north)."""
    tex = {"particle": _ref("l0_case_side"), "side": _ref("l0_case_side"), "top": _ref("l0_case_top"),
           "metal": _ref("l0_case_latch")}
    e = [_el((1.5, 0, 4), (14.5, 6, 12.5), {"north": "#side", "south": "#side", "east": "#side", "west": "#side",
                                          "up": "#top", "down": "#side"}, uv={"up": [0, 0, 16, 16]}),
         _el((1.2, 2.7, 3.7), (14.8, 3.3, 12.8), "#side", faces=SIDES),           # lid lip
         _el((3.5, 2.2, 3.4), (5.5, 4.6, 4), "#metal"),
         _el((10.5, 2.2, 3.4), (12.5, 4.6, 4), "#metal"),
         _el((6, 6, 4.2), (6.8, 7.4, 5), "#side"),                               # handle on the long front edge
         _el((9.2, 6, 4.2), (10, 7.4, 5), "#side"),
         _el((6, 7.4, 4.2), (10, 8.2, 5), "#side")]
    _bm("l0_hazmat_case", _model(tex, e, render=None))
    _bs("l0_hazmat_case", {"variants": _facing_variants({"": "l0_hazmat_case"})})
    _im("l0_hazmat_case")


# ---- noclipped furniture (l0_embedded) ---------------------------------------
# Every prop is first built standing on the floor of its own block, front to the north. A mount
# then turns it (whole quarter turns are baked into the boxes), pushes part of it out of the block
# into the wall, floor or ceiling, and tilts it by a legal 22.5 degrees (one element rotation
# shared by all its boxes, so it turns as one piece). The neighbour's face hides what's inside.

def _faces(default, **special):
    """A per-face texture dict: `default` everywhere, `special` on the named faces."""
    return {f: special.get(f, default) for f in P.FACES}


def _dresser():
    """A full-size dresser, 1 x 1.5 blocks: carcass, overhanging top, plinth, four drawers with
    a dark gap round each and two brass handles each."""
    b = [((0, 1, 3.5), (16, 24, 12.5), "#wood", ()),
         ((-0.5, 24, 3), (16.5, 25.2, 13), "#wood", ()),
         ((0.5, 0, 4), (15.5, 1, 12), "#dark", ())]
    for y0 in (1.6, 7.2, 12.8, 18.4):
        b.append(((1, y0, 2.8), (15, y0 + 5, 3.5), _faces("#wood", north="#front"), ()))
        for hx in (3.5, 10.5):
            b.append(((hx, y0 + 2.1, 2.1), (hx + 2, y0 + 2.9, 2.8), "#brass", ()))
    return {"wood": _ref("desk_side"), "front": _ref("desk_top"), "brass": _ref("l0_gilt"), "dark": _ref("chair_base")}, b


def _filing_cabinet():
    """A grey four-drawer filing cabinet, 1.4 blocks tall: drawer fronts set slightly proud with
    gaps between, a pull handle and a label holder on each."""
    b = [((4, 0, 2.5), (12, 22.4, 13.5), "#side", ())]
    for y0 in (0.4, 6, 11.6, 17.2):
        b.append(((4.2, y0, 2), (11.8, y0 + 5, 2.5), "#side", ()))
        b.append(((6.2, y0 + 2.6, 1.4), (9.8, y0 + 3.3, 2), "#metal", ()))
        b.append(((6.8, y0 + 3.7, 1.8), (9.2, y0 + 4.5, 2), "#label", ()))
    return {"side": _ref("filing_cabinet_side"), "metal": _ref("l0_conduit"), "label": _ref("l0_exit_sign_edge")}, b


def _bookshelf():
    """A tall wooden bookshelf (1 x 1.75 blocks): sides, top, base, back, three shelves, and a
    row of books on each shelf (one row of the book texture each), one book fallen over."""
    b = [((0, 0, 5), (1.2, 28, 13), "#wood", ()), ((14.8, 0, 5), (16, 28, 13), "#wood", ()),
         ((-0.4, 28, 4.6), (16.4, 29, 13.2), "#wood", ()), ((1.2, 0, 5), (14.8, 1.6, 13), "#wood", ()),
         ((1.2, 1.6, 12.2), (14.8, 28, 13), "#wood", ())]
    for i, y0 in enumerate((1.6, 8.4, 15.2, 22.0)):
        if i:
            b.append(((1.2, y0 - 0.8, 5), (14.8, y0, 12.2), "#wood", ()))
        top = y0 + (5.4 if i < 3 else 5.2)
        width = 14.8 if i != 2 else 11.0                                 # a gap on the third shelf
        b.append(((1.2, y0, 6.2), (width, top, 12.2), _faces("#wood", north="#books"),
                  {"north": [0, i * 4, 16 * (width - 1.2) / 13.6, i * 4 + 4]}))
    b.append(((11.2, 15.2, 6.4), (14.6, 16.4, 11.8), "#wood", ()))           # the book lying flat
    return {"wood": _ref("desk_side"), "books": _ref("l39_furn_books")}, b


def _office_chair():
    """A swivel office chair: five-star base (as a cross) with casters, gas column, seat pan and
    cushion, back support and a padded backrest, two armrests."""
    b = [((1, 1.2, 7.2), (15, 2.4, 8.8), "#base", ()), ((7.2, 1.2, 1), (8.8, 2.4, 15), "#base", ())]
    for cx, cz in ((1.6, 8), (14.4, 8), (8, 1.6), (8, 14.4)):
        b.append(((cx - 0.9, 0, cz - 0.9), (cx + 0.9, 1.2, cz + 0.9), "#base", ()))
    b += [((7.1, 2.4, 7.1), (8.9, 8, 8.9), "#base", ()),
          ((3, 8, 3), (13, 9, 13), "#base", ()),
          ((2.6, 9, 2.4), (13.4, 11.2, 13.2), "#fabric", ()),
          ((7.2, 9, 12.6), (8.8, 14, 14.2), "#base", ()),
          ((3, 12.5, 12.8), (13, 25, 15.4), "#fabric", ()),
          ((2, 9, 6), (3, 14.5, 7), "#base", ()), ((13, 9, 6), (14, 14.5, 7), "#base", ()),
          ((1.6, 14.5, 3.5), (3.4, 15.5, 10.5), "#base", ()), ((12.6, 14.5, 3.5), (14.4, 15.5, 10.5), "#base", ())]
    return {"fabric": _ref("chair_fabric"), "base": _ref("chair_base")}, b


def _plastic_chair():
    """A white monobloc garden chair: seat, splayed-looking thick legs, a slatted back, armrests."""
    b = [((2, 7.5, 2), (14, 8.8, 14), "#plastic", ())]
    for x0, z0 in ((2, 2), (12.2, 2), (2, 12.2), (12.2, 12.2)):
        b.append(((x0, 0, z0), (x0 + 1.8, 7.5, z0 + 1.8), "#plastic", ()))
    b += [((2, 8.8, 12.6), (3.4, 21, 14), "#plastic", ()), ((12.6, 8.8, 12.6), (14, 21, 14), "#plastic", ()),
          ((3.4, 19, 12.8), (12.6, 21, 13.8), "#plastic", ()),
          ((3.4, 15.5, 12.8), (12.6, 17, 13.8), "#plastic", ()),
          ((3.4, 12, 12.8), (12.6, 13.5, 13.8), "#plastic", ()),
          ((1.5, 12.5, 2.5), (3, 13.6, 12.6), "#plastic", ()), ((13, 12.5, 2.5), (14.5, 13.6, 12.6), "#plastic", ()),
          ((1.7, 8.8, 2.6), (2.8, 12.5, 3.8), "#plastic", ()), ((13.2, 8.8, 2.6), (14.3, 12.5, 3.8), "#plastic", ())]
    return {"plastic": _ref("l0_plastic")}, b


EMBED_PROPS = {
    # name: (textures, boxes); a box is (from, to, texture var or {face: var}, faces with the whole
    # texture as a tuple, or {face: uv}). Built standing, front to the north, 1:1 size.
    "chair": ({"wood": _ref("desk_side")}, [
        ((2.5, 7.5, 2.5), (13.5, 9, 13.5), "#wood", ()),
        ((3, 0, 3), (4.4, 7.5, 4.4), "#wood", ()), ((11.6, 0, 3), (13, 7.5, 4.4), "#wood", ()),
        ((3, 0, 11.6), (4.4, 7.5, 13), "#wood", ()), ((11.6, 0, 11.6), (13, 7.5, 13), "#wood", ()),
        ((4.4, 2.5, 3.3), (11.6, 3.3, 4.1), "#wood", ()), ((4.4, 2.5, 11.9), (11.6, 3.3, 12.7), "#wood", ()),
        ((3, 9, 11.6), (4.4, 24, 13), "#wood", ()), ((11.6, 9, 11.6), (13, 24, 13), "#wood", ()),
        ((2.6, 21, 11.4), (13.4, 24.2, 13.2), "#wood", ()),
        ((5.8, 9, 11.9), (6.8, 21, 12.7), "#wood", ()), ((7.5, 9, 11.9), (8.5, 21, 12.7), "#wood", ()),
        ((9.2, 9, 11.9), (10.2, 21, 12.7), "#wood", ())]),
    "office_chair": _office_chair(),
    "table": ({"top": _ref("desk_top"), "wood": _ref("desk_side")}, [
        ((-6, 13, 0), (22, 14.6, 16), _faces("#wood", up="#top", down="#top"), ()),
        ((-5, 10.5, 1), (21, 13, 2), "#wood", ()), ((-5, 10.5, 14), (21, 13, 15), "#wood", ()),
        ((-5, 0, 1), (-3.4, 13, 2.6), "#wood", ()), ((19.4, 0, 1), (21, 13, 2.6), "#wood", ()),
        ((-5, 0, 13.4), (-3.4, 13, 15), "#wood", ()), ((19.4, 0, 13.4), (21, 13, 15), "#wood", ())]),
    "dresser": _dresser(),
    "lamp": ({"shade": _ref("l39_furn_lampshade"), "metal": _ref("l0_gilt")}, [
        ((4.5, 0, 4.5), (11.5, 1.2, 11.5), "#metal", ()), ((6.5, 1.2, 6.5), (9.5, 2.2, 9.5), "#metal", ()),
        ((7.4, 2.2, 7.4), (8.6, 23, 8.6), "#metal", ()),
        ((2.5, 21.5, 2.5), (13.5, 30.5, 13.5), "#shade", ("north", "south", "east", "west")),
        ((7, 30.5, 7), (9, 31.5, 9), "#metal", ())]),
    "door": ({"lower": _ref("lobby_door_bottom"), "upper": _ref("lobby_door_top"), "wood": _ref("desk_side"),
              "brass": _ref("l0_gilt")}, [
        ((1.5, 0, 7.2), (14.5, 15.25, 8.8), _faces("#wood", north="#lower", south="#lower"), ("north", "south")),
        ((1.5, 15.25, 7.2), (14.5, 30.5, 8.8), _faces("#wood", north="#upper", south="#upper"), ("north", "south")),
        ((0, 0, 6), (1.5, 32, 10), "#wood", ()), ((14.5, 0, 6), (16, 32, 10), "#wood", ()),
        ((1.5, 30.5, 6), (14.5, 32, 10), "#wood", ()),
        ((11.8, 14, 6.4), (13, 15.2, 7.2), "#brass", ()), ((11.8, 14, 8.8), (13, 15.2, 9.6), "#brass", ())]),
    "crt_tv": ({"plastic": _ref("crt_plastic"), "screen": _ref("crt_screen")}, [
        ((0.5, 0, 1), (15.5, 13, 12), _faces("#plastic", north="#screen"), ("north",)),
        ((2.5, 1.5, 12), (13.5, 11.5, 15.5), "#plastic", ()),
        ((4.5, 3.5, 15.5), (11.5, 9.5, 18), "#plastic", ()),
        ((1.5, -0.6, 2), (3.5, 0, 4), "#plastic", ()), ((12.5, -0.6, 2), (14.5, 0, 4), "#plastic", ())]),
    "sofa_end": ({"fabric": _ref("sofa_fabric"), "wood": _ref("desk_side")}, [
        ((-12, 1.5, 0), (13, 7, 16), "#fabric", ()),
        ((-12, 7, 0.5), (0.5, 9.8, 12), "#fabric", ()), ((0.5, 7, 0.5), (13, 9.8, 12), "#fabric", ()),
        ((-12, 9.8, 10.5), (0.5, 18, 14), "#fabric", ()), ((0.5, 9.8, 10.5), (13, 18, 14), "#fabric", ()),
        ((-12, 7, 14), (13, 18.5, 16), "#fabric", ()),
        ((13, 1.5, 0), (17.5, 12.5, 16), "#fabric", ()), ((12.6, 12.5, -0.4), (17.9, 14.2, 16), "#fabric", ()),
        ((14, 0, 1), (15.8, 1.5, 2.8), "#wood", ()), ((14, 0, 13.2), (15.8, 1.5, 15), "#wood", ())]),
    "filing_cabinet": _filing_cabinet(),
    "bookshelf": _bookshelf(),
    "plastic_chair": _plastic_chair(),
    # One thick slab (a single box, so a tilt can never break it into steps); the rounded rim and
    # piping are in its texture.
    "mattress": ({"top": _ref("l0_mattress_top"), "side": _ref("l0_mattress_side")}, [
        ((0, 0, -7), (16, 5.5, 23), _faces("#side", up="#top", down="#top"), tuple(P.FACES))]),
}

# Whole quarter turns baked into the boxes (p' = M p): stand as built, upside down, lying with the
# top pointing out of the wall (-z), on its back (front facing up), stood on end, on its side.
_POSES = {
    "stand": ((1, 0, 0), (0, 1, 0), (0, 0, 1)),
    "flip": ((-1, 0, 0), (0, -1, 0), (0, 0, 1)),
    "lay_out": ((1, 0, 0), (0, 0, 1), (0, -1, 0)),
    "lay_flat": ((1, 0, 0), (0, 0, -1), (0, 1, 0)),
    "on_end": ((1, 0, 0), (0, 0, 1), (0, 1, 0)),
    "on_side": ((0, -1, 0), (1, 0, 0), (0, 0, 1)),
}

# prop: (wall, floor, ceiling) mounts as (pose, how much of it is buried 0..1, tilt (axis, angle) or None).
# 40-70% stays visible, always at a clear angle, so it obviously cuts through the surface.
EMBED_MOUNTS = {
    "chair": (("stand", 0.45, ("y", 22.5)), ("stand", 0.4, ("x", 45)), ("flip", 0.45, ("z", 22.5))),
    "office_chair": (("stand", 0.4, ("y", -22.5)), ("lay_flat", 0.35, ("x", 45)), ("flip", 0.45, ("x", 22.5))),
    "table": (("stand", 0.4, ("z", 22.5)), ("stand", 0.45, ("z", 22.5)), ("flip", 0.4, ("x", 22.5))),
    "dresser": (("stand", 0.5, ("y", 22.5)), ("stand", 0.45, ("x", -22.5)), ("flip", 0.5, ("z", 22.5))),
    "lamp": (("lay_out", 0.4, ("z", 22.5)), ("stand", 0.35, ("x", 45)), ("flip", 0.4, ("x", 22.5))),
    "door": (("stand", 0.5, ("y", 22.5)), ("lay_flat", 0.45, ("z", 22.5)), ("stand", 0.5, ("x", 22.5))),
    # The screen always has to be the face you see: on the wall it lies on its side facing the room,
    # on the floor it is tipped back 22.5 degrees (rear and bottom sunk, screen facing up and out),
    # from the ceiling it hangs upside down with the screen tipped towards the room below.
    "crt_tv": (("on_side", 0.45, ("y", -22.5)), ("stand", 0.35, ("x", 22.5)), ("flip", 0.45, ("x", -22.5))),
    "sofa_end": (("stand", 0.45, ("y", -22.5)), ("stand", 0.45, ("z", 22.5)), ("flip", 0.5, ("z", -22.5))),
    "filing_cabinet": (("stand", 0.5, ("y", 22.5)), ("stand", 0.5, ("z", -22.5)), ("flip", 0.45, ("x", 22.5))),
    "bookshelf": (("stand", 0.55, ("y", -22.5)), ("stand", 0.5, ("x", 22.5)), ("flip", 0.5, ("x", 22.5))),
    "plastic_chair": (("stand", 0.4, ("y", 22.5)), ("flip", 0.4, ("x", 22.5)), ("stand", 0.5, ("z", 22.5))),
    "mattress": (("on_end", 0.5, ("y", 22.5)), ("stand", 0.5, ("x", 22.5)), ("stand", 0.5, ("z", -22.5))),
}

_NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def _embed_boxes(prop, mount_index):
    """The prop's boxes for one mount: (from, to, {new face: (texture, uv or None)}, rotation or None)."""
    _, boxes = EMBED_PROPS[prop]
    pose, buried, tilt = EMBED_MOUNTS[prop][mount_index]
    M = np.array(_POSES[pose], float)
    placed = []
    for f, t, tex, whole in boxes:
        a, b = M @ np.array(f, float), M @ np.array(t, float)
        faces = {}
        for face, n in _NORMALS.items():
            nn = tuple(int(round(c)) for c in M @ np.array(n, float))
            new = next(k for k, v in _NORMALS.items() if v == nn)
            uv = whole.get(face) if isinstance(whole, dict) else ([0, 0, 16, 16] if face in whole else None)
            faces[new] = (tex[face] if isinstance(tex, dict) else tex, uv)
        placed.append([np.minimum(a, b), np.maximum(a, b), faces])
    lo = np.min([p[0] for p in placed], axis=0)
    hi = np.max([p[1] for p in placed], axis=0)
    size = hi - lo
    # where the prop goes: centred in x (and z on the floor/ceiling), pushed into its neighbour
    if mount_index == 0:                                                 # wall: the wall is at z = 16
        y0 = 8 - size[1] / 2 if pose == "lay_out" else 0.0
        shift = np.array([8 - (lo[0] + hi[0]) / 2, y0 - lo[1], 16 + buried * size[2] - hi[2]])
        origin = (8, y0 + size[1] / 2, 16)
    elif mount_index == 1:                                               # floor: the floor is at y = 0
        shift = np.array([8 - (lo[0] + hi[0]) / 2, -buried * size[1] - lo[1], 8 - (lo[2] + hi[2]) / 2])
        origin = (8, 0, 8)
    else:                                                                # ceiling: the ceiling is at y = 16
        shift = np.array([8 - (lo[0] + hi[0]) / 2, 16 + buried * size[1] - hi[1], 8 - (lo[2] + hi[2]) / 2])
        origin = (8, 16, 8)
    rot = (origin, tilt[0], tilt[1]) if tilt else None
    return [(p[0] + shift, p[1] + shift, p[2], rot) for p in placed]


def _visible_box(boxes):
    """The box around the part of the prop inside its own block (after the tilt): its collision."""
    lo, hi = np.full(3, 16.0), np.zeros(3)
    for f, t, _, rot in boxes:
        corners = np.array([[x, y, z] for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])])
        if rot:
            o, axis, ang = np.array(rot[0], float), rot[1], np.radians(rot[2])
            c, s = np.cos(ang), np.sin(ang)
            p = corners - o
            x, y, z = p[:, 0].copy(), p[:, 1].copy(), p[:, 2].copy()
            if axis == "x":
                y, z = y * c - z * s, y * s + z * c
            elif axis == "y":
                x, z = x * c + z * s, -x * s + z * c
            else:
                x, y = x * c - y * s, x * s + y * c
            corners = np.stack([x, y, z], axis=1) + o
        a, b = np.clip(corners.min(axis=0), 0, 16), np.clip(corners.max(axis=0), 0, 16)
        if np.all(b - a > 0.05):
            lo, hi = np.minimum(lo, a), np.maximum(hi, b)
    return [round(float(v) * 2) / 2 for v in (*lo, *hi)]


def _embedded_models() -> None:
    """l0_embedded: one model per prop and mount, one blockstate for facing x mount x prop.
    Prints the collision boxes for block/Level0EmbeddedBlock (keep the two in step)."""
    variants, mounts = {}, ("wall", "floor", "ceiling")
    for prop, (textures, _) in EMBED_PROPS.items():
        tex = dict(textures)
        tex["particle"] = next(iter(textures.values()))
        line = []
        for mi, mount in enumerate(mounts):
            boxes = _embed_boxes(prop, mi)
            els = [_el(tuple(f), tuple(t), {k: v[0] for k, v in faces.items()},
                       uv={k: v[1] for k, v in faces.items() if v[1] is not None}, rot=rot, cull=False)
                   for f, t, faces, rot in boxes]
            name = f"l0_embedded_{prop}_{mount}"
            _bm(name, _model(tex, els, render=None))
            line.append(_visible_box(boxes))
            for facing, y in FACING_Y.items():
                v = {"model": _ref(name)}
                if y:
                    v["y"] = y
                variants[f"facing={facing},mount={mount},prop={prop}"] = v
        print(f"    {prop.upper()}: wall {line[0]}, floor {line[1]}, ceiling {line[2]}")
    _bs("l0_embedded", {"variants": variants})
    _im("l0_embedded", parent=_ref("l0_embedded_chair_wall"))


def tex_level0_revamp_models() -> None:
    """Block models, blockstates and item models for every registry/Level0Blocks id (LF JSON)."""
    _wall_models()
    _floor_models()
    _ceiling_models()
    _wall_mount_models()
    _tape_models()
    _mannequin_models()
    _throne_model()
    _case_model()
    _embedded_models()


TEXTURES = TEXTURE_GENS + [tex_level0_revamp_models]
