"""Level 9 (the Suburbs) anomalies: textures and sounds.

Level 9 is a silent, foggy, midnight suburb. Everything here is meant to feel quiet and wrong:
there is NO wind, NO rain and NO real thunderstorm, so none of the sounds use whooshing air or
rain noise. Sounds are kept soft (modest peaks) because they play over near-silence, where even a
medium-loud sound feels like a jump scare.

Run with e.g. `python tools/gen_assets.py tex_level9_pillar snd_level9_quake`.
Every generator uses its own fixed random seed (7900-7999), so re-running gives identical files.
"""
from __future__ import annotations

import numpy as np
from PIL import Image, ImageChops, ImageDraw

import gen_assets as G

S = G.S     # 128 px block texture size
SR = G.SR   # 44.1 kHz sample rate


def _blk(name: str):
    return G.TEX / "block" / f"{name}.png"


def _snd(name: str):
    return G.SND / "level9" / f"{name}.ogg"


def _resolve_cutout(canvas: Image.Image, ss: int) -> np.ndarray:
    """Shrink a supersampled RGBA drawing to S x S for a Minecraft cutout texture.

    Colours are averaged over the covered sub-pixels only (so blade edges don't get a dark
    fringe from the transparent black around them), and alpha is made strictly 0 or 255 because
    Minecraft's cutout render type can't do partial transparency anyway.
    """
    h, w = canvas.size[1] // ss, canvas.size[0] // ss
    a = np.asarray(canvas, float).reshape(h, ss, w, ss, 4)
    cov = a[..., 3].mean(axis=(1, 3)) / 255.0
    wgt = a[..., 3:4] / 255.0
    rgb = (a[..., :3] * wgt).sum(axis=(1, 3)) / np.maximum(wgt.sum(axis=(1, 3)), 1e-6)
    out = np.zeros((h, w, 4))
    out[..., :3] = rgb
    out[..., 3] = np.where(cov >= 0.5, 255, 0)
    return out


# =============================================================================
# Textures
# =============================================================================

def tex_level9_field_grass() -> None:
    """Tall wild field grass for a vanilla cross (X-shaped) plant model.

    Blades grow up from the bottom edge; the tallest reach 85-95 % of the texture height. Colours
    are cold and desaturated (it's midnight, colour mostly drains away in the dark) with a few pale
    dew drops near the tips that catch the light.
    """
    rng = G.rng_for(7901)
    ss = 4
    canvas = Image.new("RGBA", (S * ss, S * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(canvas)
    greens = np.array([(46, 58, 50), (54, 64, 54), (40, 50, 46), (62, 70, 62), (50, 60, 46),
                       (58, 64, 58)], float)
    grey_tip = np.array([84, 88, 82], float)   # dry, greyish blade ends
    dew = (168, 180, 186, 255)

    # Each blade gets a "depth": back blades are drawn first and darker, so the front ones read
    # as closer and the clump looks thick instead of flat.
    blades = []
    for _ in range(230):
        tall = rng.random() < 0.45
        h = S * (rng.uniform(0.84, 0.94) if tall else rng.uniform(0.35, 0.82))
        blades.append((rng.random(), h, tall))
    blades.sort(key=lambda b: b[0])

    segs = 10
    for depth, h, tall in blades:
        x0 = rng.uniform(0, S)
        w0 = rng.uniform(2.0, 4.2)                     # width at the root, in pixels
        lean = rng.normal(0, 0.14) * h                 # how far the tip drifts sideways
        bend = rng.uniform(1.4, 2.4)                   # >1 = blade stays upright, then droops
        col = greens[rng.integers(len(greens))] * (0.7 + 0.45 * depth) * rng.uniform(0.9, 1.08)
        s = np.linspace(0, 1, segs + 1)
        cx = x0 + lean * s ** bend
        cy = S + 1 - h * s                             # start just below the bottom edge
        hw = 0.5 * (w0 * (1 - s) ** 0.8 + 0.15)        # tapers to a point
        quads = []
        for i in range(segs):
            sm = (s[i] + s[i + 1]) / 2
            # Darker at the root (shadowed by the other blades), lighter higher up.
            c = col * (0.55 + 0.5 * sm ** 0.8)
            c = G.lerp(c, grey_tip, 0.3 * sm ** 3)
            quads.append(([(cx[i] - hw[i], cy[i]), (cx[i] + hw[i], cy[i]),
                           (cx[i + 1] + hw[i + 1], cy[i + 1]), (cx[i + 1] - hw[i + 1], cy[i + 1])],
                          tuple(int(v) for v in np.clip(c, 0, 255)) + (255,)))
        drop = None
        if tall and rng.random() < 0.35:
            sd = rng.uniform(0.72, 0.9)
            j = int(sd * segs)
            drop = (cx[j], cy[j], rng.uniform(0.55, 0.8))
        # Draw at three horizontal offsets so a blade leaning over the side edge continues on the
        # other side (keeps the texture tidy if it's ever tiled sideways).
        for ox in (-S, 0, S):
            for pts, fill in quads:
                d.polygon([((x + ox) * ss, y * ss) for x, y in pts], fill=fill)
            if drop is not None:
                x, y, r = drop
                d.ellipse(((x + ox - r) * ss, (y - r) * ss, (x + ox + r) * ss, (y + r) * ss), fill=dew)

    img = _resolve_cutout(canvas, ss)
    # A whisper of per-pixel noise so flat blade colours don't look like vector art.
    img[..., :3] = np.clip(img[..., :3] + rng.normal(0, 2.0, (S, S, 1)), 0, 255)
    G.save_png(img, _blk("l9_field_grass"))


def tex_level9_pillar() -> None:
    """The black pillars: almost pure black (RGB 4-12) with very faint vertical smoky streaks.

    They must read as a hole in the world, so the texture barely varies; the streaks only stop it
    from looking like an untextured placeholder when you stand right next to one.
    """
    rng = G.rng_for(7902)
    img = G.fill((8, 8, 9), (S, S))
    streaks = G.fnoise(rng, beta=1.4, aniso=(1, 10), kmin=2)              # thin vertical lines
    smoke = G.smoothstep(0.3, 2.0, G.fnoise(rng, beta=2.2, aniso=(1, 4)))  # soft rising wisps
    lum = (streaks * 1.1 + smoke * 3.0 + G.fnoise(rng, beta=3.0) * 0.9
           + rng.normal(0, 0.35, (S, S)))
    img = G.add_lum(img, lum, (1.0, 1.0, 1.08))                           # the faintest cold tint
    G.save_png(np.clip(img, 4, 12), _blk("l9_pillar"))


def _voronoi_edge(rng, pts, jitter):
    """Plate index and distance (px) to the nearest plate border for a wrap-around Voronoi.

    Plates are the cells around scattered points. Distances use wrap-around maths and the jitter
    noise is seamless, so the crack network continues across the texture's edges.
    """
    X, Y = G.grid()
    Xj = X + G.fnoise(rng, beta=2.2, kmin=2) * jitter    # wobble so cracks are jagged, not straight
    Yj = Y + G.fnoise(rng, beta=2.2, kmin=2) * jitter
    dist = np.stack([np.hypot(G.wrapd(Xj - px, S), G.wrapd(Yj - py, S)) for px, py in pts])
    order = np.argsort(dist, axis=0)
    ds = np.take_along_axis(dist, order, axis=0)
    return order[0], (ds[1] - ds[0]) / 2


def tex_level9_crater_asphalt() -> None:
    """Shattered, heaved asphalt around a crater: plates split by deep black cracks.

    The base is the same _asphalt() recipe as the normal road (tex_asphalt), so the crater blends
    into the street. Each plate is tilted a little differently (some catch more light), the cracks
    have lighter raw broken edges, and loose gravel collects along them.
    """
    rng = G.rng_for(7903)
    img = G._asphalt(7903)
    X, Y = G.grid()

    # 3x3 jittered grid of plate centres: random but never two plates on top of each other.
    pts = np.array([((i + 0.5 + rng.uniform(-0.35, 0.35)) * S / 3, (j + 0.5 + rng.uniform(-0.35, 0.35)) * S / 3)
                    for i in range(3) for j in range(3)])
    cell, edge = _voronoi_edge(rng, pts, 2.0)

    # Heaved plates: a gentle brightness slope across each plate in a random direction.
    ang = rng.uniform(0, 2 * np.pi, len(pts))
    amt = rng.uniform(0.08, 0.22, len(pts))
    lift = rng.normal(0, 4, len(pts))
    dxc = G.wrapd(X - pts[cell, 0], S)
    dyc = G.wrapd(Y - pts[cell, 1], S)
    img = G.add_lum(img, (np.cos(ang[cell]) * dxc + np.sin(ang[cell]) * dyc) * amt[cell] + lift[cell])

    # Crack width changes along its length (0.9-2.3 px half width).
    cw = 0.9 + 1.4 * G.smoothstep(-1.0, 1.5, G.fnoise(rng, beta=2.0))
    crack = G.smoothstep(cw + 0.7, cw - 0.5, edge)
    beyond = np.maximum(edge - cw, 0)
    img = G.add_lum(img, -14 * np.exp(-beyond / 3.0) * (1 - crack))               # shadow into the gap
    rim = np.exp(-((edge - cw - 1.0) / 0.9) ** 2) * G.smoothstep(-0.4, 0.9, G.fnoise(rng, beta=1.4, kmin=4))
    img = G.add_lum(img, rim * 16, (1.0, 0.97, 0.92))                              # raw broken edge
    deep = G.fill((7, 7, 8), (S, S))
    deep = G.add_lum(deep, rng.normal(0, 1.2, (S, S)))
    img = G.lerp(img, deep, crack[..., None])

    # Fine hairline cracks inside the plates, only in some areas (the shock didn't split it all).
    pts2 = rng.uniform(0, S, (26, 2))
    _, edge2 = _voronoi_edge(rng, pts2, 1.5)
    hair = G.smoothstep(0.65, 0.15, edge2) * G.smoothstep(0.0, 0.8, G.fnoise(rng, beta=1.8)) * 0.65
    img = G.over(img, np.array([14, 14, 15]), hair * (1 - crack))

    # Loose gravel, denser near the cracks; each stone gets a 1 px shadow under it.
    p = 0.015 + 0.09 * np.exp(-beyond / 3.5)
    peb = (rng.random((S, S)) < p) * rng.uniform(0.4, 1.0, (S, S)) * (1 - 0.5 * crack)
    pb = G.blur(peb, 0.5)
    img = G.add_lum(img, pb * 70, (1.0, 0.98, 0.95))
    img = G.add_lum(img, -np.roll(pb, 1, axis=0) * 25)
    G.save_png(img, _blk("l9_crater_asphalt"))


def _asphalt_chip(d, rng, x, y, k) -> None:
    """One small angular broken piece of asphalt (for _cutout_scatter)."""
    r = rng.uniform(1.5, 4.0)
    nv = int(rng.integers(4, 7))
    angs = np.sort(rng.uniform(0, 2 * np.pi, nv))
    pts = [(x + np.cos(a) * r * rng.uniform(0.6, 1.0) * k, y + np.sin(a) * r * rng.uniform(0.6, 1.0) * k)
           for a in angs]
    g = int(rng.uniform(52, 76))
    d.polygon(pts, fill=(g, g, g + 3, 255))


def tex_level9_crater_soil() -> None:
    """Freshly churned, dark wet earth at the bottom of a crater, with asphalt chips and gravel.

    A bumpy 'height map' (noise + round clods) is lit from the top-left so the clods look 3D; the
    low spots are darker and wetter with tiny shiny glints, like mud.
    """
    rng = G.rng_for(7904)
    h = G.fnoise(rng, beta=2.4) * 0.8 + G.fnoise(rng, beta=1.5, kmin=6) * 0.35
    for _ in range(70):                                                      # lumps of dirt
        cx, cy = rng.uniform(0, S, 2)
        r = rng.uniform(1.5, 4.5)
        h += G.blob((S, S), cx, cy, r, r * rng.uniform(0.6, 1.2)) * rng.uniform(0.5, 1.4)
    h = G.blur(h, 0.6)
    shade = (h - np.roll(h, 1, axis=1)) + (h - np.roll(h, 1, axis=0))         # light from top-left

    mix = G.smoothstep(-1.0, 1.2, G.fnoise(rng, beta=2.2))
    img = G.lerp(np.array([40, 33, 27]), np.array([58, 46, 36]), mix[..., None])
    img = G.add_lum(img, shade * 26 + rng.normal(0, 2.5, (S, S)))
    clay = G.smoothstep(1.0, 2.0, G.fnoise(rng, beta=2.0, aniso=(2, 1)))
    img = G.over(img, np.array([74, 60, 46]), clay * 0.4)                    # streaks of paler subsoil
    hz = (h - h.mean()) / (h.std() + 1e-9)
    wet = G.smoothstep(0.2, -0.9, hz)
    img = img * (1 - 0.35 * wet[..., None])                                  # puddled = darker
    glint = (rng.random((S, S)) < 0.012) * wet
    img = G.add_lum(img, G.blur(glint, 0.4) * 55, (0.9, 0.95, 1.0))

    # Broken asphalt chips blasted into the soil, each casting a small shadow down-right.
    chips = G._cutout_scatter(rng, 30, _asphalt_chip)
    ca = chips[..., 3] / 255.0
    chip_rgb = chips[..., :3] + rng.normal(0, 4, (S, S, 1))
    img = img * (1 - ca[..., None]) + chip_rgb * ca[..., None]
    img = G.add_lum(img, -np.roll(ca, (1, 1), axis=(0, 1)) * (1 - ca) * 12)

    # Pale gravel specks.
    peb = (rng.random((S, S)) < 0.03) * rng.uniform(0.4, 1.0, (S, S))
    pb = G.blur(peb, 0.45)
    img = G.add_lum(img, pb * 60, (1.0, 0.97, 0.93))
    img = G.add_lum(img, -np.roll(pb, 1, axis=0) * 18)
    G.save_png(img, _blk("l9_crater_soil"))


def tex_level9_sandbags() -> None:
    """Stacked burlap sandbags: 4 rows per tile, 2 bags per row, every other row shifted half a bag.

    Each bag is a rounded 'pillow' shape lit from above. Gaps between bags are nearly black.
    Colours are worn olive drab / khaki, kept dark so the wall doesn't glow at night.
    """
    rng = G.rng_for(7905)
    X, Y = G.grid()
    row = (Y // 32).astype(int)
    xs = (X - (row % 2) * 32) % S                        # stagger like bricks
    col = (xs // 64).astype(int)
    bag = row * 2 + col                                  # which of the 8 bags this pixel is on
    u = (xs % 64) / 64
    v = (Y % 32) / 32

    tones = np.array([(78, 76, 50), (92, 86, 60), (84, 82, 56), (100, 92, 66), (72, 72, 50)], float)
    tone = tones[rng.integers(len(tones), size=8)] * rng.uniform(0.88, 1.0, (8, 1))
    sa = rng.uniform(0.98, 1.03, 8)                      # bags aren't all the same size, and they
    sb = rng.uniform(0.95, 1.03, 8)                      # are squashed against their neighbours
    a = (u - 0.5) * 2 / sa[bag]
    b = (v - 0.52) * 2 / sb[bag]                         # sits slightly low: bags slump
    body = 1 - np.abs(a) ** 6 - np.abs(b) ** 3          # boxy pillow: flat-ish face, rounded rim
    inside = G.smoothstep(0.0, 0.08, body)
    hgt = np.sqrt(np.clip(body, 0, 1))
    shade = 0.45 + 0.55 * hgt + 0.22 * (-b) * hgt        # rounded and brighter on top
    img = tone[bag] * shade[..., None]

    # Burlap: a coarse checker weave plus uneven threads running both ways.
    xi, yi = X.astype(int), Y.astype(int)
    weave = (((xi + yi) % 2) - 0.5) * 5 + rng.normal(0, 1, S)[None, :] * 2.5 + rng.normal(0, 1, S)[:, None] * 2.5
    img = G.add_lum(img, weave * inside)
    # Gathered folds at the tied ends and random wrinkles.
    folds = np.sin(b * np.pi * 3 + G.fnoise(rng, beta=2.0) * 1.5) * G.smoothstep(0.55, 0.95, np.abs(a)) * 7
    img = G.add_lum(img, (folds + G.fnoise(rng, beta=1.8, aniso=(1, 3)) * 4) * inside)
    # Dirt toward the bottom of each bag, stains, and rubbed lighter patches on top.
    img = G.add_lum(img, -G.smoothstep(0.2, 0.9, b) * 10 - G.smoothstep(0.5, 1.6, G.fnoise(rng, beta=2.4)) * 9)
    img = G.add_lum(img, G.smoothstep(0.6, 1.0, hgt) * G.smoothstep(0.5, 1.5, G.fnoise(rng, beta=2.0)) * 8)
    img = G.add_lum(img, -12 * G.smoothstep(0.25, 0.0, v) * inside)        # shadow of the bag above

    gap = G.add_lum(G.fill((18, 18, 14), (S, S)), rng.normal(0, 1.5, (S, S)))
    img = G.lerp(gap, img, inside[..., None])
    G.save_png(img, _blk("l9_sandbags"))


def tex_level9_frost() -> None:
    """256x256 full-screen frost overlay: feathery ice crystals creeping in from the screen edges.

    The middle stays fully transparent so the player can still see. Frost grows inward from the
    edges and is thickest in the corners (where real window frost starts too). Each crystal is a
    'dendrite': a stem with side branches at about 60 degrees, which themselves branch again.
    """
    rng = G.rng_for(7906)
    W, ss = 256, 3
    R0 = 0.75  # inside this "radius" (a rounded square) the overlay is completely clear

    def rnorm(x, y):
        # Rounded-square distance from the centre: 0 in the middle, 1 at the edge midpoints and
        # ~1.19 in the corners, so the corners naturally get more frost.
        dx = abs(x / W - 0.5) * 2
        dy = abs(y / W - 0.5) * 2
        return (dx ** 4 + dy ** 4) ** 0.25

    def grow(d, x, y, ang, length, depth, width, inten):
        step = 1.5
        steps = max(2, int(length / step))
        gap = int(rng.integers(3, 6))
        for i in range(steps):
            nx, ny = x + step * np.cos(ang), y + step * np.sin(ang)
            if rnorm(nx, ny) < R0 - 0.03:                # never grow into the clear middle
                return
            f = 1 - i / steps                            # fainter and thinner toward the tip
            d.line([(x * ss, y * ss), (nx * ss, ny * ss)], fill=int(255 * inten * (0.35 + 0.65 * f)),
                   width=max(1, int(round(width * ss * (0.4 + 0.6 * f)))))
            if depth > 0 and i % gap == gap - 1 and i < steps - 2:
                rem = (steps - i) * step
                for side in (-1, 1):
                    if rng.random() < 0.8:
                        grow(d, nx, ny, ang + side * rng.uniform(0.95, 1.15), rem * rng.uniform(0.25, 0.5),
                             depth - 1, width * 0.65, inten * 0.85)
            ang += rng.normal(0, 0.05)
            x, y = nx, ny

    seeds = []
    for edge in range(4):
        for _ in range(55):
            tpos = rng.beta(0.45, 0.45)                  # U-shaped: more crystals near the corners
            corner = abs(tpos - 0.5) * 2
            length = W * rng.uniform(0.6, 1.2) * (0.09 + 0.2 * corner ** 2)
            along = tpos * W
            x, y, ang = ((along, 0.5, np.pi / 2), (along, W - 0.5, -np.pi / 2),
                         (0.5, along, 0.0), (W - 0.5, along, np.pi))[edge]
            seeds.append((x, y, ang + rng.normal(0, 0.45), length))
    for cx, cy, a0 in ((0, 0, 0.0), (W, 0, np.pi / 2), (W, W, np.pi), (0, W, 1.5 * np.pi)):
        for _ in range(7):                               # extra big crystals fanning out of corners
            seeds.append((cx + (0.5 if cx == 0 else -0.5), cy + (0.5 if cy == 0 else -0.5),
                          a0 + rng.uniform(0.1, np.pi / 2 - 0.1), W * rng.uniform(0.2, 0.33)))

    # Each crystal is drawn on its own layer and merged with "lighter" (max), so where crystals
    # overlap the brighter one wins instead of a later one painting over it.
    mask = Image.new("L", (W * ss, W * ss), 0)
    for x, y, ang, length in seeds:
        layer = Image.new("L", mask.size, 0)
        grow(ImageDraw.Draw(layer), x, y, ang, length, 2, rng.uniform(1.2, 1.8), rng.uniform(0.75, 1.0))
        mask = ImageChops.lighter(mask, layer)
    m = np.asarray(mask.resize((W, W), Image.BOX), float) / 255.0

    X, Y = G.grid((W, W))
    dx = np.abs(X / W - 0.5) * 2
    dy = np.abs(Y / W - 0.5) * 2
    r = (dx ** 4 + dy ** 4) ** 0.25
    fall = G.smoothstep(R0, R0 + 0.22, r)                # soft fade from clear centre to frosty edge
    haze = G.smoothstep(R0 + 0.1, 1.15, r) * (0.6 + 0.4 * G.smoothstep(-1.2, 1.2, G.fnoise(rng, (W, W), beta=2.2)))
    alpha = np.clip(m * 0.9 + haze * 0.5, 0, 1) * fall
    dense = np.clip(m * 0.8 + haze * 0.4, 0, 1)[..., None]
    rgb = G.lerp(np.array([170, 205, 240]), np.array([245, 250, 255]), dense)   # thin = blue, thick = white
    img = np.concatenate([rgb, (alpha * 235)[..., None]], axis=2)
    G.save_png(img, G.TEX / "misc" / "l9_frost.png")


# =============================================================================
# Shared sound pieces
# =============================================================================

# Formant frequencies (F1, F2, F3 in Hz) of a few average adult vowels. Gliding between these is
# what makes synthetic "voice" sound like speech even when no real words are said.
VOWELS = {
    "a": (730, 1090, 2440), "e": (530, 1840, 2480), "i": (300, 2200, 2950),
    "o": (570, 840, 2410), "u": (320, 900, 2250), "@": (500, 1500, 2500), "ae": (660, 1720, 2410),
}
_VKEYS = list(VOWELS)


def _babble(rng, dur, f0=120.0, fscale=1.0, start=0.15, end_pad=0.3, reverse=0):
    """Unintelligible but speech-like voice: phrases of 3-7 syllables with real speech rhythm.

    How it imitates talking:
    - syllables last 0.11-0.26 s (about 4-6 per second, like normal speech), grouped into phrases
      separated by short pauses (breaths);
    - each syllable starts with a "consonant" (a hiss, a short pop, or a nasal hum) and then
      glides into a vowel, because formants move continuously in real speech;
    - pitch (f0) falls slowly over each phrase, jumps up on stressed syllables, and sometimes
      rises at the end (a question), which is what gives speech its "melody".
    `reverse` phrases are played backwards in place. Returns (signal, list of phrase spans).
    """
    n = int(dur * SR)
    t = np.arange(n) / SR
    phrases = []
    tc = start
    while tc < dur - end_pad - 0.35:
        syls = []
        for _ in range(int(rng.integers(3, 8))):
            d = rng.uniform(0.11, 0.26)
            if tc + d > dur - end_pad:
                break
            syls.append(dict(t0=tc, d=d, v=_VKEYS[int(rng.integers(len(_VKEYS)))],
                             c=str(rng.choice(["none", "fric", "stop", "nasal"], p=[0.3, 0.25, 0.3, 0.15])),
                             stress=rng.random() < 0.3, locus=rng.uniform(900, 2000)))
            tc += d
        if syls:
            phrases.append(syls)
        tc += rng.uniform(0.2, 0.55)

    # Formant tracks: consonant "locus" at the syllable start, then the vowel target.
    bt, b1, b2, b3 = [0.0], [500.0], [1500.0], [2500.0]
    env = np.zeros(n)
    cons = np.zeros(n)
    accents = np.zeros(n)
    f0t, f0v = [0.0], [1.0]
    for ph in phrases:
        p0, p1 = ph[0]["t0"], ph[-1]["t0"] + ph[-1]["d"]
        question = rng.random() < 0.3
        f0t += [p0, p1]
        f0v += [1.12, 1.0 if question else 0.84]          # declination: pitch sinks over a phrase
        if question:
            accents += 0.25 * G.smoothstep(p1 - 0.25, p1, t) * (t < p1 + 0.05)
        i0, i1 = int(p0 * SR), int(p1 * SR)
        env[i0:i1] = np.maximum(env[i0:i1], 0.06)       # voice never fully stops mid-phrase
        for j, s in enumerate(ph):
            F1, F2, F3 = np.array(VOWELS[s["v"]], float) * rng.uniform(0.93, 1.07, 3)
            closure = s["c"] != "none"
            bt += [s["t0"], s["t0"] + 0.35 * s["d"], s["t0"] + 0.75 * s["d"]]
            b1 += [300.0 if closure else F1 * 0.85, F1, F1]
            b2 += [s["locus"] if closure else F2, F2, F2 * rng.uniform(0.96, 1.04)]
            b3 += [F3, F3, F3]

            g = (1.0 if s["stress"] else 0.68) * rng.uniform(0.85, 1.0)
            if j == len(ph) - 1:
                g *= 0.8                                  # phrase endings trail off
            m = int(s["d"] * SR)
            u = np.arange(m) / m
            on = {"none": 0.0, "nasal": 0.05, "stop": 0.15, "fric": 0.3}[s["c"]]
            shape = G.smoothstep(on, on + 0.18, u) * G.smoothstep(1.0, 0.72, u) * g
            k0 = int(s["t0"] * SR)
            seg = env[k0:k0 + m]
            env[k0:k0 + m] = np.maximum(seg, shape[:len(seg)])
            if s["stress"]:
                accents += 0.18 * np.exp(-0.5 * ((t - s["t0"] - 0.5 * s["d"]) / 0.05) ** 2)

            if s["c"] == "fric":                          # "s" / "sh" hiss before the vowel
                fm = int(rng.uniform(0.05, 0.09) * SR)
                lo, hi = (2500, 6000) if rng.random() < 0.5 else (1600, 3800)
                hiss = G.fft_filter(rng.normal(size=fm), lambda f: G.bp_gain(f, lo, hi, 2))
                G.add_at(cons, 0.3 * g * G.norm(hiss * np.sin(np.pi * np.arange(fm) / fm)), s["t0"], False)
            elif s["c"] == "stop":                        # "t" / "k" / "p" release pop
                fm = int(0.015 * SR)
                pop = G.fft_filter(rng.normal(size=fm), lambda f: G.bp_gain(f, 700, 4000, 2))
                G.add_at(cons, 0.45 * g * G.norm(pop * np.exp(-np.arange(fm) / (0.004 * SR))),
                         s["t0"] + 0.1 * s["d"], False)
    bt.append(dur)
    b1.append(500.0)
    b2.append(1500.0)
    b3.append(2500.0)
    f0t.append(dur)
    f0v.append(1.0)

    F1 = np.interp(t, bt, b1) * fscale
    F2 = np.interp(t, bt, b2) * fscale
    F3 = np.interp(t, bt, b3) * fscale
    f0a = f0 * (np.interp(t, f0t, f0v) + accents)
    f0a *= 1 + G.smooth_jitter(rng, n, 5, 0.03) + G.smooth_jitter(rng, n, 30, 0.008)   # natural wobble
    env = np.clip(G.fft_filter(env, lambda f: G.lp_gain(f, 35, 1)), 0, None)           # no clicks

    voiced = G.voice(f0a, [(F1, 70 * fscale, 1.0), (F2, 100 * fscale, 0.55), (F3, 150 * fscale, 0.25)],
                     rng, nh=int(4500 / f0))
    breath = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 500, 3000, 1))
    x = G.norm(voiced * env) + 0.04 * G.norm(breath * env) + cons

    spans = [(ph[0]["t0"], ph[-1]["t0"] + ph[-1]["d"]) for ph in phrases]
    if reverse and spans:
        for k in rng.choice(len(spans), size=min(reverse, len(spans)), replace=False):
            i0, i1 = int(spans[k][0] * SR), int(spans[k][1] * SR)
            x[i0:i1] = x[i0:i1][::-1].copy()
    return x, spans


def _blip(rng, dur, f0, fscale=1.0) -> np.ndarray:
    """One flat, monotone spoken syllable (like a numbers-station voice reading digits)."""
    m = int(dur * SR)
    tt = np.arange(m) / SR
    F = np.array(VOWELS[_VKEYS[int(rng.integers(len(_VKEYS)))]], float) * fscale
    f0a = f0 * (1 - 0.04 * tt / dur)
    v = G.voice(f0a, [(F[0], 70, 1.0), (F[1], 100, 0.55), (F[2], 150, 0.25)], rng, nh=int(4500 / f0))
    return G.norm(v) * G.env_adsr(m, 0.012, 0.05)


def _click(rng, amp) -> np.ndarray:
    """Sharp little relay/key click."""
    m = int(0.006 * SR)
    c = rng.normal(size=m) * np.exp(-np.arange(m) / (0.0008 * SR)) + 0.6 * G.ring([1900], [0.002], [1], 0.006)[:m]
    return amp * G.norm(c)


def _far(x, lp, hp=40.0) -> np.ndarray:
    """Distance filter: far-away sounds lose their highs (and, through small things, their lows)."""
    return G.fft_filter(x, lambda f: G.lp_gain(f, lp, 2) * G.hp_gain(f, hp, 1))


# =============================================================================
# Sounds
# =============================================================================

def snd_level9_frost_onset() -> None:
    """~3 s: the temperature suddenly drops. Thin crystalline ice crackles spread fast, a few glassy
    tinkles ring out, and a soft low pressure 'drop' swells underneath. Deliberately no air noise:
    cold arrives in total stillness."""
    rng = G.rng_for(7910)
    dur = 3.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    # Crackle density rises almost instantly then thins out: ice racing across surfaces.
    dens = np.interp(t, [0, 0.05, 0.35, 0.9, 1.8, 2.6, dur], [0, 60, 260, 180, 50, 8, 0])
    times = t[rng.random(n) < dens / SR]
    crack = G.clicks(rng, times, dur, 3500, 8500, 0.0006, 0.0025, amp_pow=2.0)
    crack *= np.interp(t, [0, 0.5, dur], [1.0, 0.8, 0.5])            # spreading = moving away
    tink = np.zeros(n)
    for st in np.sort(rng.uniform(0.08, 1.8, 10)):
        f = rng.uniform(2500, 5500)
        dk = rng.uniform(0.08, 0.25)
        G.add_at(tink, rng.uniform(0.4, 1.0) * G.ring([f, f * 2.76], [dk, dk * 0.6], [1, 0.4], dk * 5), st, False)
    # Pressure drop: a sub tone that swells in and sinks in pitch, like ears adjusting.
    fd = np.interp(t, [0, 0.4, 2.0, dur], [70, 48, 36, 34])
    drop = np.sin(2 * np.pi * np.cumsum(fd) / SR) + 0.25 * np.sin(4 * np.pi * np.cumsum(fd) / SR)
    drop *= G.smoothstep(0.0, 0.35, t) * np.exp(-np.maximum(t - 0.35, 0) / 0.9)
    x = 0.45 * G.norm(crack) + 0.25 * G.norm(tink) + 0.7 * G.norm(drop)
    x = G.fft_filter(x, lambda f: G.lp_gain(f, 10000, 2))[:n]
    x = x + 0.25 * G.norm(G.conv(x, G.reverb_ir(rng, 1.2, 0.3, lp=7000))[:n]) * np.abs(x).max()
    G.save_ogg(x, _snd("frost_onset"), peak=0.6, fade_in=0.002, fade_out=0.3)


def _boom(seed, modes, decays, hits, bumps) -> np.ndarray:
    """Distant structural boom: deep impact + hollow resonance + long rolling low tail.

    modes/decays are the hollow 'body' resonances (low, slightly inharmonic, so it sounds like a
    huge hollow structure rather than a drum); hits = (start, gain); bumps = rolls of the tail
    (time after impact, width, gain), which is what makes thunder 'roll'.
    """
    rng = G.rng_for(seed)
    dur = 6.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for st, gain in hits:
        tt = G.t_axis(dur - st)
        f = 22 + 38 * np.exp(-tt / 0.09)                              # pitch drops right after the hit
        body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt / 0.45)
        thud = G.fft_filter(rng.normal(size=len(tt)), lambda fr: G.lp_gain(fr, 220, 2)) * np.exp(-tt / 0.06)
        hollow = G.ring(modes, decays, [1.0, 0.8, 0.6, 0.45, 0.3][:len(modes)], dur - st)
        G.add_at(x, gain * (G.norm(body) + 0.5 * G.norm(thud) + 0.45 * G.norm(hollow)), st, False)
    st0 = hits[0][0]
    roll_env = sum(g * np.exp(-0.5 * ((t - st0 - c) / w) ** 2) for c, w, g in bumps)
    roll_env *= G.smoothstep(st0, st0 + 0.08, t)
    roll = G.fft_filter(rng.normal(size=n), lambda f: G.lp_gain(f, 110, 3) * G.hp_gain(f, 18, 2))
    roll = G.norm(roll) * roll_env * (1 + G.smooth_jitter(rng, n, 4, 0.35))
    x = x + 0.7 * roll
    x = G.fft_filter(x, lambda f: G.lp_gain(f, 320, 2))[:n]          # far away: only lows survive
    x = x + 0.6 * G.norm(G.conv(x, G.reverb_ir(rng, 4.0, 1.4, lp=350, predelay=0.04))[:n]) * np.abs(x).max()
    return x


def snd_level9_pillar_boom() -> None:
    """Two ~6 s takes of a far-off thunder-like boom, as if something enormous hit the ground."""
    x = _boom(7920, [41, 63, 97, 142, 208], [1.4, 1.1, 0.8, 0.6, 0.4], [(0.05, 1.0)],
              [(0.3, 0.35, 0.9), (1.0, 0.5, 0.7), (1.9, 0.6, 0.55), (2.9, 0.7, 0.35), (3.9, 0.8, 0.2)])
    G.save_ogg(x, _snd("pillar_boom1"), peak=0.75, fade_in=0.005, fade_out=1.0)
    # Second take: different body tones and a smaller echo-hit a moment later (a second impact
    # bouncing off the houses).
    x = _boom(7921, [36, 57, 88, 131], [1.6, 1.2, 0.9, 0.6], [(0.08, 1.0), (0.62, 0.45)],
              [(0.2, 0.3, 0.8), (0.8, 0.45, 0.9), (1.6, 0.6, 0.6), (2.6, 0.8, 0.4), (3.8, 0.9, 0.25)])
    G.save_ogg(x, _snd("pillar_boom2"), peak=0.75, fade_in=0.005, fade_out=1.0)


def snd_level9_garbled_speech() -> None:
    """Two takes of a distant, muffled voice talking in the fog: speech rhythm and intonation,
    but no actual words, with one phrase played backwards so it never quite makes sense."""
    for i, (seed, dur, f0, fs) in enumerate(((7930, 5.0, 116.0, 1.0), (7931, 4.6, 168.0, 1.12)), 1):
        rng = G.rng_for(seed)
        n = int(dur * SR)
        dry, _ = _babble(rng, dur, f0=f0, fscale=fs, start=0.2, end_pad=0.8, reverse=1)
        dry = _far(dry, 1100, 160)                                     # fog swallows the highs
        wet = G.conv(dry, G.reverb_ir(rng, 2.2, 0.55, lp=1400, predelay=0.05))[:n]
        # Plenty of echo says "far away", but not so much that it smears the syllables together:
        # the on/off rhythm of syllables is what makes the ear hear it as talking.
        x = 0.75 * G.norm(dry) + 0.5 * G.norm(wet)
        G.save_ogg(x, _snd(f"garbled_speech{i}"), peak=0.5, fade_in=0.02, fade_out=0.4)


def snd_level9_quake() -> None:
    """~8 s low ground rumble and groan with a little debris trickling down, fading out."""
    rng = G.rng_for(7950)
    dur = 8.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    env = np.interp(t, [0, 1.2, 2.0, 4.5, dur], [0, 0.7, 1.0, 0.8, 0]) * (1 + G.smooth_jitter(rng, n, 3, 0.35))
    rumble = G.fft_filter(rng.normal(size=n), lambda f: G.lp_gain(f, 70, 3) * G.hp_gain(f, 16, 2))
    # The groan: a narrow band of noise whose centre slides slowly, so it sounds like rock and
    # earth straining (a moan with pitch) instead of plain noise.
    fc = np.interp(t, [0, 2.0, 4.0, 6.0, dur], [55, 95, 110, 75, 60]) * (1 + G.smooth_jitter(rng, n, 1.5, 0.15))
    groan = G.svf(rng.normal(size=n), fc, 8.0)
    gran = G.norm(groan) * (0.6 + 0.4 * G.smoothstep(-0.5, 0.5, G.smooth_jitter(rng, n, 2, 1.0)))
    dens = 25 * G.smoothstep(0.8, 2.2, t) * np.interp(t, [0, 5.0, 7.0, dur], [1, 0.8, 0.2, 0])
    times = t[rng.random(n) < dens / SR]
    debris = G.clicks(rng, times, dur, 700, 2600, 0.002, 0.008, amp_pow=2.0)
    debris = G.fft_filter(debris, lambda f: G.lp_gain(f, 3500, 2))[:n]
    trickle = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 800, 3000, 2))
    trickle *= G.smoothstep(0.3, 1.2, G.smooth_jitter(rng, n, 3, 1.0)) * G.smoothstep(1.5, 3.0, t) * (t < 6.5)
    x = G.norm(rumble) * env + 0.45 * gran * env + 0.15 * G.norm(debris) + 0.04 * G.norm(trickle)
    x = x + 0.4 * G.norm(G.conv(x, G.reverb_ir(rng, 2.5, 0.8, lp=500))[:n]) * np.abs(x).max()
    G.save_ogg(x, _snd("quake"), peak=0.75, fade_in=0.2, fade_out=1.5)


def _radio(seed, dur, events, path) -> None:
    """Radio chatter through a walkie-talkie channel.

    events: ("key", t_on, t_off) a transmission (click on, static, squelch burst + click off);
    ("voice", t0, length, f0) a growling garbled voice; ("numbers", t0, t_end) monotone number-like
    syllables. Everything goes through a 300-3000 Hz band-pass + mild overdrive, which is what
    makes anything sound like it's coming out of a radio.
    """
    rng = G.rng_for(seed)
    n = int(dur * SR)
    t = np.arange(n) / SR
    sig = np.zeros(n)
    keyed = np.zeros(n)
    sq = np.zeros(n)
    for ev in events:
        if ev[0] == "key":
            _, a, b = ev
            keyed[int(a * SR):int(b * SR)] = 1
            G.add_at(sq, _click(rng, 0.9), a, False)
            m = int(0.14 * SR)                       # squelch tail: a short "kssht" when it lets go
            G.add_at(sq, 0.55 * G.norm(rng.normal(size=m) * np.exp(-np.arange(m) / (0.05 * SR))), b, False)
            G.add_at(sq, _click(rng, 0.6), b + 0.14, False)
        elif ev[0] == "voice":
            _, t0, length, f0 = ev
            v, _ = _babble(rng, length, f0=f0, fscale=0.95, start=0.05, end_pad=0.05)
            vt = np.arange(len(v)) / SR
            # Growl: a fast rough wobble on the level + overdrive turns a voice into a snarl.
            v = G.dist(G.norm(v) * (1 + 0.6 * np.sin(2 * np.pi * 31 * vt + G.smooth_jitter(rng, len(v), 8, 2.0))), 3.5)
            G.add_at(sig, 0.9 * G.norm(v), t0, False)
        elif ev[0] == "numbers":
            _, tc, t_end = ev
            while tc < t_end - 0.4:
                f = float(rng.choice([128.0, 138.0, 150.0]))   # same few pitches: a reading voice
                for k in range(int(rng.integers(1, 3))):        # 1-2 syllables, like "four"/"se-ven"
                    b = _blip(rng, rng.uniform(0.11, 0.17), f * (1 - 0.05 * k))
                    G.add_at(sig, 0.8 * b, tc, False)
                    tc += len(b) / SR + 0.03
                tc += rng.uniform(0.18, 0.3)
    keyed = G.fft_filter(keyed, lambda f: G.lp_gain(f, 200, 1))
    static = rng.normal(size=n) * 0.12 + 0.15 * G.norm(G._crackle(rng, dur, 40, f_lo=1500))
    x = sig * keyed + static * (0.015 + 0.16 * keyed) + sq
    x = G.fft_filter(x, lambda f: G.bp_gain(f, 300, 3000, 3))
    x = G.dist(G.norm(x), 2.0)
    x = G.fft_filter(x, lambda f: G.bp_gain(f, 250, 3400, 1))
    # Peak target 0.5: the overdriven, band-passed signal overshoots a little after OGG encoding.
    G.save_ogg(x, path, peak=0.5, fade_in=0.005, fade_out=0.1)


def snd_level9_field_radio() -> None:
    """Two ~7 s bursts of radio chatter: a growling voice, then a string of number-like syllables."""
    _radio(7960, 7.0, [("key", 0.15, 2.6), ("voice", 0.3, 2.1, 86.0),
                       ("key", 3.1, 5.6), ("numbers", 3.3, 5.6),
                       ("key", 6.0, 6.7), ("voice", 6.08, 0.55, 92.0)], _snd("field_radio1"))
    _radio(7961, 7.5, [("key", 0.1, 1.6), ("voice", 0.2, 1.3, 95.0),
                       ("key", 2.0, 4.6), ("numbers", 2.15, 4.6),
                       ("key", 5.0, 7.1), ("voice", 5.1, 1.8, 80.0)], _snd("field_radio2"))


def _midi(m) -> float:
    return 440.0 * 2 ** ((m - 69) / 12)


def _tone(freq, dur, partials, decay, attack=0.006, release=0.08) -> np.ndarray:
    """A plucked/struck note: a few sine partials (ratio, gain, decay factor) dying away."""
    m = int((dur + release) * SR)
    tt = np.arange(m) / SR
    s = sum(a * np.sin(2 * np.pi * freq * r * tt) * np.exp(-tt / (decay * dk)) for r, a, dk in partials if freq * r < 18000)
    return s * np.clip(tt / attack, 0, 1) * np.clip((dur + release - tt) / release, 0, 1)


def snd_level9_field_elevator() -> None:
    """~16 s of warped elevator music drifting out of the field: a soft bossa-nova chord loop with a
    vibraphone melody, played on a worn-out tape (pitch wobbles, saturation, a couple of dropouts)
    through a tiny far-off speaker."""
    rng = G.rng_for(7970)
    dur = 16.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    beat, bar = 0.5, 2.0                                   # 120 bpm, 4 beats per bar
    # ii-V-I-VI in C (Dm9, G13, Cmaj9, A7b13), twice: the classic "lounge" turnaround.
    chords = [(50, (53, 57, 60, 64)), (43, (53, 59, 64)), (48, (52, 55, 59, 62)), (45, (55, 61, 65))]
    prog = chords + chords[:3] + [chords[2]]
    pad = np.zeros(n)
    bass = np.zeros(n)
    rim = np.zeros(n)
    ep = ((1, 1.0, 1.0), (2, 0.3, 0.6), (3, 0.1, 0.4))     # soft electric-piano-ish tone
    for bi, (root, notes) in enumerate(prog):
        t0 = bi * bar
        comp = [(0, 0.7), (1.5, 0.5), (3, 0.8)] if bi % 2 == 0 else [(0.5, 0.7), (2, 0.5), (3.5, 0.4)]
        for bt_, ln in comp:                               # syncopated bossa comping
            for m in notes:
                G.add_at(pad, 0.2 * _tone(_midi(m), ln * beat, ep, 1.2), t0 + bt_ * beat, False)
        for bt_, m in ((0, root), (1.5, root), (2, root + 7), (3.5, root + 7)):   # root-fifth bass
            G.add_at(bass, _tone(_midi(m), 0.9 * beat, ((1, 1.0, 1.0), (2, 0.35, 0.5)), 0.8), t0 + bt_ * beat, False)
    for rep in range(4):                                   # bossa clave on a rim click
        for e in (0, 3, 6, 10, 13):
            G.add_at(rim, G.ring([1700, 2900], [0.012, 0.006], [1, 0.4], 0.05), rep * 2 * bar + e * beat / 2, False)
    melody = [  # (bar, beat, midi, length in beats)
        (0, 0, 69, 1), (0, 1, 72, 0.5), (0, 1.5, 76, 2.5),
        (1, 0.5, 74, 0.5), (1, 1, 71, 1), (1, 2, 67, 2),
        (2, 0, 64, 1), (2, 1, 67, 0.5), (2, 1.5, 71, 1.5), (2, 3, 74, 1),
        (3, 0, 73, 1.5), (3, 1.5, 70, 0.5), (3, 2, 69, 2),
        (4, 0, 77, 1), (4, 1, 76, 0.5), (4, 1.5, 74, 1.5), (4, 3, 72, 1),
        (5, 0, 71, 1), (5, 1, 69, 1), (5, 2, 67, 1), (5, 3, 65, 1),
        (6, 0, 64, 2), (6, 2, 67, 1), (6, 3, 71, 1),
        (7, 0, 74, 3.5),
    ]
    vib = np.zeros(n)
    vibes = ((1, 1.0, 1.0), (4.0, 0.22, 0.25), (10.0, 0.05, 0.1))   # vibraphone bar overtones
    for b_, bt_, m, ln in melody:
        G.add_at(vib, _tone(_midi(m), ln * beat + 0.3, vibes, 1.4, attack=0.003), b_ * bar + bt_ * beat, False)
    vib *= 1 + 0.28 * np.sin(2 * np.pi * 5.2 * t)          # the vibraphone's rotating-fan tremolo
    x = 0.35 * G.norm(pad) + 0.55 * G.norm(bass) + 0.6 * G.norm(vib) + 0.12 * G.norm(rim)

    # Worn tape: playback speed wobbles slowly (wow) and quickly (flutter); at the dropouts the
    # tape also sags, so the pitch dips while the sound fades.
    drop = sum(np.exp(-0.5 * ((t - c) / w) ** 2) for c, w in ((6.4, 0.15), (11.7, 0.1)))
    speed = (1 + 0.016 * np.sin(2 * np.pi * 0.43 * t) + 0.007 * np.sin(2 * np.pi * 1.1 * t + 1)
             + 0.003 * np.sin(2 * np.pi * 6.5 * t) + G.smooth_jitter(rng, n, 2.5, 0.006) - 0.05 * drop)
    pos = np.clip(np.cumsum(speed), 0, n - 1)
    y = np.interp(pos, np.arange(n), x) * (1 - 0.88 * np.clip(drop, 0, 1))
    y = G.dist(G.norm(y) * 1.2, 2.5)                       # tape saturation
    y = y + 0.025 * G.norm(G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 800, 8000, 1)))   # hiss
    # Small speaker far away: nothing below ~350 Hz or above ~2.6 kHz, a boxy bump around 1.2 kHz.
    y = G.fft_filter(y, lambda f: G.bp_gain(f, 350, 2600, 2) * (1 + 0.6 * np.exp(-0.5 * ((f - 1200) / 400) ** 2)))
    y = y + 0.35 * G.norm(G.conv(y, G.reverb_ir(rng, 1.5, 0.4, lp=2000))[:n]) * np.abs(y).max()
    G.save_ogg(y, _snd("field_elevator"), peak=0.55, fade_in=0.05, fade_out=0.6)


def snd_level9_field_reversed() -> None:
    """Two takes of backwards talking: speech + its echo, reversed as a whole, so each syllable
    swells up out of its own reversed echo and then cuts off abruptly."""
    for i, (seed, dur, f0, fs) in enumerate(((7980, 5.0, 112.0, 1.0), (7981, 4.4, 150.0, 1.1)), 1):
        rng = G.rng_for(seed)
        n = int(dur * SR)
        x, _ = _babble(rng, dur, f0=f0, fscale=fs, start=0.3, end_pad=0.9)
        x = _far(x, 2600, 140)
        wet = G.conv(x, G.reverb_ir(rng, 1.8, 0.55, lp=2200, predelay=0.02))[:n]
        y = (0.7 * G.norm(x) + 0.8 * G.norm(wet))[::-1].copy()
        # A little normal (forward) room on top, so it sounds like it's out there in the field.
        y = y + 0.15 * G.norm(G.conv(y, G.reverb_ir(rng, 0.6, 0.15, lp=2000))[:n]) * np.abs(y).max()
        G.save_ogg(y, _snd(f"field_reversed{i}"), peak=0.5, fade_in=0.05, fade_out=0.15)


def snd_level9_gloom_drag() -> None:
    """~4 s: being dragged into the red glow. A deep roar/pressure swell rises and brightens while
    a muffled heartbeat races faster and faster, then everything stops dead."""
    rng = G.rng_for(7990)
    dur = 4.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    p = t / dur
    lo = G.fft_filter(rng.normal(size=n), lambda f: G.lp_gain(f, 90, 3) * G.hp_gain(f, 15, 2))
    hi = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 60, 380, 2))
    roar = G.norm(lo) * (1 - p) + G.norm(hi) * p           # the roar gets brighter as it closes in
    fd = 32 * (58 / 32) ** p                               # rising low drone 32 -> 58 Hz
    ph = 2 * np.pi * np.cumsum(fd) / SR
    drone = sum(np.sin(k * ph) / k for k in range(1, 9))
    drone = G.fft_filter(drone, lambda f: G.lp_gain(f, 300, 2))
    swell = p ** 2.2 * (1 + 0.15 * np.sin(2 * np.pi * 3.0 * t * (1 + p)))   # pressure throbbing in
    # Heartbeat: "lub-dub" pairs, tempo racing from 85 to 170 beats per minute.
    rate = np.interp(t, [0, dur], [85, 170]) / 60
    beats = t[np.nonzero(np.diff(np.floor(np.cumsum(rate) / SR)) > 0)[0]]
    heart = np.zeros(n)
    for bt_ in beats:
        period = 60 / np.interp(bt_, [0, dur], [85, 170])
        for off, g in ((0.0, 1.0), (0.3 * period, 0.7)):
            tt = G.t_axis(0.25)
            th = np.sin(2 * np.pi * np.cumsum(35 + 20 * np.exp(-tt / 0.03)) / SR) * np.exp(-tt / 0.07)
            G.add_at(heart, g * th, bt_ + off, False)
    heart = G.fft_filter(heart, lambda f: G.lp_gain(f, 140, 2))   # muffled: heard from inside
    x = 0.8 * G.norm(roar) * swell + 0.5 * G.norm(drone) * swell + 0.55 * G.norm(heart) * (0.4 + 0.6 * p)
    G.save_ogg(x, _snd("gloom_drag"), peak=0.7, fade_in=0.05, fade_out=0.008)   # abrupt end


def snd_level9_gloom_throb() -> None:
    """~2.5 s: one very soft, very low throb with a faint far hum, for each pulse of the red light.
    3 takes: throb pitch (+-5%), swell/decay timing and hum wobble differ."""
    takes = ((7995, 1.0, 0.35, 0.6), (57995, 1.05, 0.28, 0.55), (67995, 0.95, 0.42, 0.66))
    for i, (seed, p, att, dec) in enumerate(takes, 1):
        rng = G.rng_for(seed)
        dur = 2.5
        n = int(dur * SR)
        t = np.arange(n) / SR
        fd = np.interp(t, [0, 0.5, dur], [44 * p, 39 * p, 36 * p])
        ph = 2 * np.pi * np.cumsum(fd) / SR
        # The 2nd/3rd harmonics let small speakers still hint at a sub-bass note they can't play.
        throb = np.sin(ph) + 0.3 * np.sin(2 * ph) + 0.08 * np.sin(3 * ph)
        throb *= G.smoothstep(0.0, att, t) * np.exp(-np.maximum(t - att, 0) / dec)
        hum = G.mains_hum(t, f0=60.0, levels_db=(0, -8, -16, -26)) * (1 + G.smooth_jitter(rng, n, 2, 0.2))
        hum = G.fft_filter(hum, lambda f: G.lp_gain(f, 350, 2)) * np.sin(np.pi * t / dur) ** 2
        x = G.norm(throb) + 0.08 * G.norm(hum)
        G.save_ogg(x, _snd("gloom_throb" if i == 1 else f"gloom_throb{i}"), peak=0.5, fade_in=0.01, fade_out=0.3)


TEXTURES = [
    tex_level9_field_grass, tex_level9_pillar, tex_level9_crater_asphalt, tex_level9_crater_soil,
    tex_level9_sandbags, tex_level9_frost,
]
SOUNDS = [
    snd_level9_frost_onset, snd_level9_pillar_boom, snd_level9_garbled_speech, snd_level9_quake,
    snd_level9_field_radio, snd_level9_field_elevator, snd_level9_field_reversed,
    snd_level9_gloom_drag, snd_level9_gloom_throb,
]
