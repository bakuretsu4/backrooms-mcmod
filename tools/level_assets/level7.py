"""Level 7 "Thalassophobia": textures and sounds.

Textures: the stained concrete ceiling over the sea, the entrance room's riveted rusty plates,
grating and hatch, its soggy carpet, swollen bookcase of fake books, waterlogged coffee table and
armchair, the rope; down in the sea the pale silt, dark wet rock, bone heaps, tar, scrap, and
around the exit the giant pipes, stone pillars, the platform and the old wooden door. Also the
murk at the bottom of Level 94's flooded houses, and the skin of the Thing (seen only as a shape).
Sounds: the hollow sea ambience, the Thing's groans and the heave of the water as it passes, and
the gut-drop lurch when you fall through the hatch.

Run only these with:  python tools/gen_assets.py l7_
"""
from __future__ import annotations

import numpy as np

import gen_assets as G

S = G.S


def _blk(name: str):
    return G.TEX / "block" / f"{name}.png"


def _water_streaks(img: np.ndarray, rng, amount: float) -> np.ndarray:
    """Long vertical runs of darker water staining, the kind concrete gets under a leak."""
    streak = G.fnoise(rng, beta=2.2, aniso=(1, 14))
    return G.add_lum(img, -amount * G.smoothstep(0.2, 1.6, streak), (0.95, 1.0, 1.0))


def _rust(img: np.ndarray, rng, cover: float) -> np.ndarray:
    """Patches of orange-brown rust with darker pitted cores, bleeding downward."""
    patch = G.fnoise(rng, beta=2.6) + 0.6 * G.fnoise(rng, beta=1.6, kmin=8)
    m = G.smoothstep(1.2 - cover * 2.0, 1.9 - cover * 2.0, patch)
    rust = G.add_lum(G.fill((124, 70, 40), (S, S)), G.fnoise(rng, beta=1.0, kmin=10) * 10, (1.0, 0.7, 0.45))
    img = G.over(img, rust, m * 0.9)
    pits = (rng.random((S, S)) < 0.02) * m
    img = G.add_lum(img, -G.blur(pits, 0.6) * 60)
    bleed = G.blur(np.roll(m, 6, axis=0), 1.0) * G.smoothstep(0.0, 1.5, G.fnoise(rng, beta=2.0, aniso=(1, 10)))
    return G.over(img, np.array([110, 64, 38.0]), np.clip(bleed, 0, 1) * 0.35)


def tex_l7_concrete() -> None:
    rng = G.rng_for(7001)
    img = G._smooth_concrete(rng, (116, 118, 115), mottle=5.0)
    img = G._pores(img, rng, density=0.01)
    img = _water_streaks(img, rng, 22.0)
    green = G.smoothstep(0.8, 2.0, G.fnoise(rng, beta=2.8))
    img = G.over(img, np.array([70, 82, 66.0]), green * 0.35)
    G.save_png(img, _blk("l7_concrete"))


def tex_l7_rust_metal() -> None:
    """Riveted metal plates, 64 px square, painted grey once and now mostly rust."""
    rng = G.rng_for(7002)
    X, Y = G.grid()
    img = G.add_lum(G.fill((96, 92, 86), (S, S)), G.fnoise(rng, beta=2.4) * 6)
    img = _rust(img, rng, 0.45)
    seam = np.minimum(np.abs(G.wrapd(X, 64)), np.abs(G.wrapd(Y, 64)))
    img = G.add_lum(img, -38 * G.smoothstep(1.4, 0.3, seam) + 10 * G.smoothstep(1.4, 2.6, seam) * G.smoothstep(4.0, 2.6, seam))
    for c in (6, 22, 42, 58):
        for axis in range(2):
            px = (X if axis == 0 else Y)
            other = (Y if axis == 0 else X)
            d = np.hypot(G.wrapd(px - c, 64), G.wrapd(other - 4, 64))
            img = G.add_lum(img, 26 * G.smoothstep(2.6, 1.0, d) - 24 * G.smoothstep(3.6, 2.6, d) * (G.wrapd(other - 4, 64) > 0))
    G.save_png(img, _blk("l7_rust_metal"))


def tex_l7_grating() -> None:
    rng = G.rng_for(7003)
    X, Y = G.grid()
    img = G.fill((18, 17, 16), (S, S))
    bar_x = G.smoothstep(2.6, 1.6, np.abs(G.wrapd(X, 16)))
    bar_y = G.smoothstep(1.6, 0.8, np.abs(G.wrapd(Y, 8)))
    metal = G.add_lum(G.fill((84, 78, 72), (S, S)), G.fnoise(rng, beta=2.0) * 6)
    metal = _rust(metal, rng, 0.5)
    img = G.over(img, metal, np.maximum(bar_x, bar_y * 0.85))
    img = G.add_lum(img, 14 * G.smoothstep(1.4, 0.4, np.abs(G.wrapd(X + 1, 16))) * bar_x)
    G.save_png(img, _blk("l7_grating"))


def tex_l7_carpet() -> None:
    """Carpet that has been under water for years: dark, flattened, grey-green, with silt."""
    rng = G.rng_for(7004)
    img = G.fill((62, 66, 60), (S, S))
    img = G.add_lum(img, rng.normal(0, 5, (S, S)) + G.blur(rng.normal(0, 8, (S, S)), 1.0))
    img = G.add_lum(img, G.fnoise(rng, beta=2.8) * 7, (0.9, 1.0, 0.95))
    silt = G.smoothstep(0.7, 1.8, G.fnoise(rng, beta=2.4))
    img = G.over(img, np.array([120, 116, 104.0]), silt * 0.35)
    G.save_png(img, _blk("l7_carpet"))


def tex_l7_silt() -> None:
    """Pale bone-dust silt with faint ripples."""
    rng = G.rng_for(7005)
    X, Y = G.grid()
    img = G.fill((164, 160, 148), (S, S))
    warp = G.fnoise(rng, beta=3.0) * 6
    ripple = np.sin((Y + warp) * 2 * np.pi / 16)
    img = G.add_lum(img, ripple * 4 + G.fnoise(rng, beta=1.2, kmin=8) * 5 + rng.normal(0, 3, (S, S)))
    img = G.add_lum(img, G.fnoise(rng, beta=2.8) * 6)
    G.save_png(img, _blk("l7_silt"))


def tex_l7_rock() -> None:
    """Dark wet rock with cracks and a faint wet sheen."""
    rng = G.rng_for(7006)
    img = G.add_lum(G.fill((52, 56, 56), (S, S)), G.fnoise(rng, beta=2.2) * 8 + G.fnoise(rng, beta=1.2, kmin=8) * 4)
    crack = np.abs(G.fnoise(rng, beta=2.0))
    img = G.add_lum(img, -26 * G.smoothstep(0.12, 0.0, crack))
    sheen = G.smoothstep(1.4, 2.4, G.fnoise(rng, beta=1.8))
    img = G.add_lum(img, 18 * sheen, (0.95, 1.0, 1.05))
    G.save_png(img, _blk("l7_rock"))


def tex_l7_bone() -> None:
    """A tangle of old bones: pale rods with knobbed ends over dark gaps."""
    rng = G.rng_for(7007)
    img = G.add_lum(G.fill((84, 80, 70), (S, S)), G.fnoise(rng, beta=2.0) * 10)
    for _ in range(46):
        x, y = rng.uniform(0, S, 2)
        a = rng.uniform(0, np.pi)
        ln = rng.uniform(26, 60)
        w = rng.uniform(3.0, 6.0)
        dx, dy = np.cos(a) * ln / 2, np.sin(a) * ln / 2

        def draw(d, k, x=x, y=y, dx=dx, dy=dy, w=w):
            for ox in (-S, 0, S):
                for oy in (-S, 0, S):
                    x0, y0, x1, y1 = (x - dx + ox) * k, (y - dy + oy) * k, (x + dx + ox) * k, (y + dy + oy) * k
                    d.line((x0, y0, x1, y1), fill=255, width=int(w * k))
                    r = w * 0.9 * k
                    for cx, cy in ((x0, y0), (x1, y1)):
                        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=255)
        m = G.pil_mask(draw)
        tone = rng.uniform(-18, 10)
        bone = G.add_lum(G.fill((196 + tone, 186 + tone, 160 + tone), (S, S)), G.fnoise(rng, beta=1.4, kmin=8) * 6)
        img = G.add_lum(img, -20 * G.blur(m, 2.0))     # shadow on what is under it
        img = G.over(img, bone, m)
    G.save_png(img, _blk("l7_bone"))


def tex_l7_tar() -> None:
    rng = G.rng_for(7008)
    img = G.add_lum(G.fill((18, 16, 16), (S, S)), G.fnoise(rng, beta=3.0) * 3)
    gloss = G.fnoise(rng, beta=3.2)
    img = G.add_lum(img, 40 * G.smoothstep(1.3, 2.2, gloss) + 14 * G.smoothstep(0.6, 1.3, gloss), (0.9, 0.95, 1.0))
    G.save_png(img, _blk("l7_tar"))


def tex_l7_scrap() -> None:
    """Bent rusted sheets, dented and torn."""
    rng = G.rng_for(7009)
    img = G.add_lum(G.fill((88, 80, 72), (S, S)), G.fnoise(rng, beta=2.4) * 10)
    img = _rust(img, rng, 0.7)
    folds = np.abs(G.fnoise(rng, beta=2.6, aniso=(1, 3)))
    img = G.add_lum(img, -30 * G.smoothstep(0.15, 0.0, folds) + 12 * G.smoothstep(0.15, 0.4, folds) * G.smoothstep(0.6, 0.4, folds))
    G.save_png(img, _blk("l7_scrap"))


def tex_l7_pipe() -> None:
    """The giant pipes: dark steel with a riveted flange band, rust running down."""
    rng = G.rng_for(7010)
    X, Y = G.grid()
    img = G.add_lum(G.fill((74, 72, 68), (S, S)), G.fnoise(rng, beta=2.2, aniso=(1, 6)) * 8)
    img = _rust(img, rng, 0.35)
    flange = G.box((S, S), -1, 0, S + 1, 14)
    img = G.add_lum(img, 14 * flange - 30 * G.smoothstep(2.0, 0.0, np.abs(Y - 15)))
    for c in range(8, S, 16):
        d = np.hypot(X - c, Y - 7)
        img = G.add_lum(img, 30 * G.smoothstep(3.0, 1.2, d))
    G.save_png(img, _blk("l7_pipe"))


def tex_l7_pipe_end() -> None:
    """The pipes' end faces: a welded steel cap plate (each pipe is 3x3 blocks, so no hole here)."""
    rng = G.rng_for(7011)
    X, Y = G.grid()
    img = G.add_lum(G.fill((70, 68, 64), (S, S)), G.fnoise(rng, beta=2.2) * 8)
    img = _rust(img, rng, 0.4)
    weld = np.minimum(np.abs(G.wrapd(X, S)), np.abs(G.wrapd(Y, S)))
    img = G.add_lum(img, 16 * G.smoothstep(3.0, 1.0, weld) - 18 * G.smoothstep(5.0, 3.0, weld) * G.smoothstep(1.0, 3.0, weld))
    G.save_png(img, _blk("l7_pipe_end"))


def _stone(rng, col, block=64) -> np.ndarray:
    X, Y = G.grid()
    img = G._smooth_concrete(rng, col, mottle=7.0, fine=1.6)
    row = np.floor(Y / (block // 2)).astype(int)
    xoff = (row % 2) * (block // 2)
    joint = np.minimum(np.abs(G.wrapd(X + xoff, block)), np.abs(G.wrapd(Y, block // 2)))
    img = G.add_lum(img, -34 * G.smoothstep(2.2, 0.6, joint))
    algae = G.smoothstep(0.6, 1.8, G.fnoise(rng, beta=2.6))
    return G.over(img, np.array([66, 78, 64.0]), algae * 0.4)


def tex_l7_pillar() -> None:
    G.save_png(_stone(G.rng_for(7012), (124, 122, 114)), _blk("l7_pillar"))


def tex_l7_platform() -> None:
    """The platform top: big square flagstones with a chiselled groove and a carved ring each."""
    rng = G.rng_for(7013)
    X, Y = G.grid()
    img = G._smooth_concrete(rng, (132, 128, 118), mottle=6.0, fine=1.4)
    joint = np.minimum(np.abs(G.wrapd(X, 64)), np.abs(G.wrapd(Y, 64)))
    img = G.add_lum(img, -36 * G.smoothstep(2.4, 0.6, joint) + 10 * G.smoothstep(2.4, 4.0, joint) * G.smoothstep(6.0, 4.0, joint))
    r = np.hypot(G.wrapd(X - 32, 64), G.wrapd(Y - 32, 64))
    img = G.add_lum(img, -22 * G.smoothstep(1.6, 0.4, np.abs(r - 20)) + 8 * G.smoothstep(1.6, 0.4, np.abs(r - 22)))
    G.save_png(img, _blk("l7_platform"))


def tex_l7_exit_door() -> None:
    """An old panelled wooden door, seen lying flat, dark with water, slimy at the edges."""
    rng = G.rng_for(7014)
    X, Y = G.grid()
    img = G._wood(rng, np.array([96, 70, 48]), horizontal=False)
    frame = 1 - G.box((S, S), 8, 4, S - 8, S - 4)
    img = G.add_lum(img, -26 * frame)
    for y0, y1 in ((14, 58), (70, 114)):
        outer = G.box((S, S), 22, y0, S - 22, y1)
        inner = G.box((S, S), 28, y0 + 6, S - 28, y1 - 6)
        img = G.add_lum(img, (outer - inner) * np.where(X < S / 2, 12, -16) - 8 * inner)
    knob = G.supersample((S, S), lambda x, y: np.hypot(x - 100, y - 64) < 5)
    img = G.over(img, np.array([132, 108, 52.0]), knob)
    slime = G.smoothstep(0.5, 1.6, G.fnoise(rng, beta=2.4)) * (0.4 + 0.6 * frame)
    img = G.over(img, np.array([44, 54, 40.0]), np.clip(slime, 0, 1) * 0.6)
    G.save_png(img, _blk("l7_exit_door"))


def tex_l7_exit_door_side() -> None:
    img = G._planks(G.rng_for(7015), (78, 58, 40))
    G.save_png(img, _blk("l7_exit_door_side"))


def tex_l7_rope() -> None:
    """Twisted hemp rope: diagonal strands, frayed and darkened by the damp."""
    rng = G.rng_for(7016)
    X, Y = G.grid()
    strand = np.sin((X + Y * 1.4) * 2 * np.pi / 10)
    img = G.add_lum(G.fill((140, 118, 84), (S, S)), strand * 14 + rng.normal(0, 5, (S, S)))
    img = G.add_lum(img, -24 * G.smoothstep(0.3, -0.9, strand))
    img = G.add_lum(img, -16 * G.smoothstep(0.4, 1.6, G.fnoise(rng, beta=2.2, aniso=(1, 6))))
    G.save_png(img, _blk("l7_rope"))


def tex_l7_hatch() -> None:
    """A rusty steel hatch: raised rim, cross-welded plate, a ring handle."""
    rng = G.rng_for(7017)
    X, Y = G.grid()
    img = G.add_lum(G.fill((92, 88, 82), (S, S)), G.fnoise(rng, beta=2.4) * 7)
    img = _rust(img, rng, 0.55)
    rim = 1 - G.box((S, S), 10, 10, S - 10, S - 10)
    img = G.add_lum(img, 16 * rim - 34 * G.smoothstep(2.0, 0.0, np.minimum(np.abs(X - 10), np.abs(Y - 10)) * (1 - rim) + 4 * rim))
    weld = np.minimum(np.abs(X - S / 2), np.abs(Y - S / 2))
    img = G.add_lum(img, 14 * G.smoothstep(2.4, 0.8, weld) * (1 - rim))
    r = np.hypot(X - S / 2, Y - S / 2 - 26)
    ring = G.smoothstep(2.2, 1.0, np.abs(r - 11))
    img = G.add_lum(img, -30 * G.blur(ring, 2.0))
    img = G.over(img, np.array([70, 60, 52.0]), ring)
    G.save_png(img, _blk("l7_hatch"))


def tex_l7_bookcase() -> None:
    """Two shelves of swollen, water-stained books with no titles: blocks of paper in dust jackets."""
    rng = G.rng_for(7018)
    X, Y = G.grid()
    img = G._wood(rng, np.array([84, 60, 42]), horizontal=True)
    for y0 in (6, 70):
        x = 6
        while x < S - 10:
            w = int(rng.integers(5, 13))
            w = min(w, S - 6 - x)
            top = y0 + int(rng.integers(0, 12))
            tone = rng.uniform(0.55, 1.0)
            col = np.array(rng.choice([(98, 56, 48), (54, 66, 78), (70, 80, 56), (120, 108, 82), (60, 52, 46)])) * tone
            book = G.box((S, S), x, top, x + w, y0 + 56, soft=0.4)
            spine = G.add_lum(G.fill(col, (S, S)), G.fnoise(rng, beta=1.6, kmin=6) * 6)
            img = G.over(img, spine, book)
            img = G.add_lum(img, -18 * G.smoothstep(1.2, 0.2, np.abs(X - x)) * book)
            x += w
        img = G.add_lum(img, -30 * G.box((S, S), 6, y0 + 50, S - 6, y0 + 56))   # shadow on the shelf
    frame = 1 - G.box((S, S), 6, 6, S - 6, S - 6)
    img = G.add_lum(img, 10 * frame)
    img = _water_streaks(img, rng, 18.0)
    tide = G.smoothstep(66, 62, Y) * G.smoothstep(54, 58, Y)
    img = G.add_lum(img, -24 * tide)
    G.save_png(img, _blk("l7_bookcase"))


def tex_l7_bookcase_top() -> None:
    img = G._planks(G.rng_for(7019), (84, 60, 42), joints=False)
    G.save_png(img, _blk("l7_bookcase_top"))


def tex_l7_wet_wood() -> None:
    rng = G.rng_for(7020)
    img = G._wood(rng, np.array([76, 56, 40]), horizontal=True)
    img = G.add_lum(img, -10 * G.smoothstep(0.2, 1.6, G.fnoise(rng, beta=2.6)))
    G.save_png(img, _blk("l7_wet_wood"))


def _soggy_fabric(rng, col) -> np.ndarray:
    X, Y = G.grid()
    weave = ((X.astype(int) + Y.astype(int)) % 2) * 2 - 1
    img = G.add_lum(G.fill(col, (S, S)), weave * 3 + rng.normal(0, 4, (S, S)) + G.fnoise(rng, beta=2.8) * 8)
    mould = G.smoothstep(0.8, 2.0, G.fnoise(rng, beta=2.4))
    return G.over(img, np.array([40, 48, 38.0]), mould * 0.5)


def tex_l7_armchair() -> None:
    G.save_png(_soggy_fabric(G.rng_for(7021), (86, 92, 76)), _blk("l7_armchair"))


def tex_l7_armchair_cushion() -> None:
    G.save_png(_soggy_fabric(G.rng_for(7022), (96, 100, 84)), _blk("l7_armchair_cushion"))


def tex_l7_flood_dark() -> None:
    """The murk at the bottom of a Level 94 flooded house's stairs: green-black, barely moving."""
    rng = G.rng_for(7023)
    img = G.add_lum(G.fill((12, 22, 22), (S, S)), G.fnoise(rng, beta=3.0) * 3, (0.8, 1.0, 1.0))
    G.save_png(img, _blk("l94_flood_dark"))


def tex_l7_thing() -> None:
    """The Thing's skin: dark mottled hide, a paler belly, old pale scars along its length.
    u runs along the body (repeated), v round it (0.5..1 is the underside)."""
    rng = G.rng_for(7024)
    X, Y = G.grid()
    img = G.add_lum(G.fill((34, 38, 40), (S, S)), G.fnoise(rng, beta=2.4) * 6 + G.fnoise(rng, beta=1.2, kmin=8) * 3)
    belly = G.smoothstep(64, 84, Y) * G.smoothstep(128, 108, Y)
    img = G.over(img, np.array([64, 66, 62.0]), belly * 0.7)
    scars = G.smoothstep(1.8, 2.6, G.fnoise(rng, beta=2.0, aniso=(1, 8)))
    img = G.over(img, np.array([96, 98, 92.0]), scars * 0.5)
    G.save_png(img, G.TEX / "entity" / "l7_thing.png")


# ---------------------------------------------------------------- sounds ---

def snd_l7_ambience() -> None:
    """16 s hollow sea under a ceiling: deep swell rumble, soft lapping, rare far drips, all in
    a vast concrete space."""
    rng = G.rng_for(7101)
    T = 16.0
    n = int(T * G.SR)
    t = np.arange(n) / G.SR
    rumble = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 18, 60, 3)) * G.drift(n, rng, 0.35, 0.06, 0.2)
    lap = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 180, 900, 2) / np.sqrt(np.maximum(f, 20)))
    # Whole-cycle swells (periods 4 s and 16/5 s) so the loop stays seamless.
    swell = np.clip(0.55 + 0.45 * np.sin(2 * np.pi * t / 4.0) * np.sin(2 * np.pi * t * 5 / T + 1.0), 0, 1) ** 2
    lap = G.norm(lap) * swell
    drips = np.zeros(n)
    for tt in rng.uniform(0, T, 5):
        f = rng.uniform(900, 1600)
        d = G.ring([f, f * 1.9], [0.05, 0.03], [1, 0.4], 0.25)
        G.add_at(drips, d * rng.uniform(0.3, 1.0), tt, circular=True)
    ir = G.reverb_ir(rng, 4.0, 1.4, lp=2500, predelay=0.04)
    drips = G.circ_conv(drips, ir)
    x = G.norm(rumble) + 0.35 * lap + 0.25 * G.norm(drips)
    G.save_ogg(x, G.SND / "level7" / "ambience.ogg", peak=0.45, loop=True)


def _groan(seed: int, dur: float, f0: float, glide: tuple[float, float, float]) -> np.ndarray:
    """A whale-like groan: a low voice gliding up then down, rough with slow beating, through
    a lot of water (low-passed) and a huge space."""
    rng = G.rng_for(seed)
    n = int(dur * G.SR)
    t = np.arange(n) / G.SR
    a, b, c = glide
    shape = a * np.sin(np.pi * t / dur) + b * t / dur + c * np.sin(3 * np.pi * t / dur)
    f = f0 * 2 ** shape
    f *= 1 + 0.01 * np.sin(2 * np.pi * 3.3 * t)
    phase = 2 * np.pi * np.cumsum(f) / G.SR
    voice = sum((0.75 ** k) * np.sin((k + 1) * phase + rng.uniform(0, 6)) for k in range(7))
    rough = 1 + 0.25 * np.sin(2 * np.pi * 7.0 * t) * G.fnoise(rng, (1, n), beta=2.0)[0] * 0.5
    voice = voice * rough * G.env_adsr(n, dur * 0.25, dur * 0.35)
    voice = G.fft_filter(voice, lambda fr: G.lp_gain(fr, 420, 2))
    breath = G.fft_filter(rng.normal(size=n), lambda fr: G.bp_gain(fr, 40, 300, 2)) * G.env_adsr(n, dur * 0.3, dur * 0.4)
    x = voice + 0.25 * G.norm(breath) * np.abs(voice).max()
    ir = G.reverb_ir(rng, 5.0, 1.8, lp=900, predelay=0.08)
    wet = G.conv(x, ir)[:n + int(3.0 * G.SR)]
    out = np.zeros(len(wet))
    out[:n] += x * 0.4
    return out + G.norm(wet) * np.abs(x).max()


def snd_l7_groans() -> None:
    specs = ((7111, 7.0, 48.0, (0.9, -0.4, 0.15)), (7112, 8.5, 38.0, (0.6, 0.5, -0.2)), (7113, 6.0, 62.0, (1.2, -0.9, 0.3)))
    for i, (seed, dur, f0, glide) in enumerate(specs, start=1):
        G.save_ogg(_groan(seed, dur, f0, glide), G.SND / "level7" / f"groan{i}.ogg", peak=0.8)


def snd_l7_heave() -> None:
    """6 s: the sea lifting and settling, as if something huge passed underneath."""
    rng = G.rng_for(7121)
    dur = 6.5
    n = int(dur * G.SR)
    t = np.arange(n) / G.SR
    env = np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 1.5
    low = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 14, 70, 3)) * env
    slosh = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 150, 800, 2))
    bumps = np.clip(np.sin(2 * np.pi * t / 1.6) ** 3, 0, 1) * env
    x = G.norm(low) + 0.3 * G.norm(slosh) * bumps
    ir = G.reverb_ir(rng, 3.0, 1.2, lp=1500, predelay=0.03)
    G.save_ogg(G.conv(x, ir)[: n + G.SR], G.SND / "level7" / "heave.ogg", peak=0.8)


def snd_l7_lurch() -> None:
    """1.3 s gut drop: a falling whoosh, a sagging inner-ear tone and a soft low thud."""
    rng = G.rng_for(7131)
    dur = 1.3
    n = int(dur * G.SR)
    t = np.arange(n) / G.SR
    noise = rng.normal(size=n)
    centre = 1400 * (1 - t / dur) + 200
    whoosh = np.zeros(n)
    hop = 2048
    for i in range(0, n, hop):   # a band that slides down, block by block
        seg = noise[i:i + hop * 2]
        c = centre[min(i, n - 1)]
        whoosh[i:i + len(seg)] += G.fft_filter(seg, lambda f, c=c: G.bp_gain(f, c * 0.6, c * 1.4, 2)) * np.hanning(len(seg))
    whoosh *= G.env_adsr(n, 0.15, 0.6)
    f = 220 * 2 ** (-1.8 * t / dur)
    tone = np.sin(2 * np.pi * np.cumsum(f) / G.SR) * G.env_adsr(n, 0.2, 0.7) * 0.4
    thud = G.ring([48, 70], [0.25, 0.12], [1, 0.4], dur) * 0.6
    x = G.norm(whoosh) * 0.8 + tone + thud
    G.save_ogg(x, G.SND / "level7" / "lurch.ogg", peak=0.8)


TEXTURES = [tex_l7_concrete, tex_l7_rust_metal, tex_l7_grating, tex_l7_carpet, tex_l7_silt, tex_l7_rock,
            tex_l7_bone, tex_l7_tar, tex_l7_scrap, tex_l7_pipe, tex_l7_pipe_end, tex_l7_pillar, tex_l7_platform,
            tex_l7_exit_door, tex_l7_exit_door_side, tex_l7_rope, tex_l7_hatch, tex_l7_bookcase,
            tex_l7_bookcase_top, tex_l7_wet_wood, tex_l7_armchair, tex_l7_armchair_cushion, tex_l7_flood_dark,
            tex_l7_thing]
SOUNDS = [snd_l7_ambience, snd_l7_groans, snd_l7_heave, snd_l7_lurch]
