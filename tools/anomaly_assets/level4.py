"""Level 4 anomalies: textures and sounds.

The looping stairwells (grey painted block walls, the floor sign that always says 4, footsteps far
up and down the shaft), the Window (a clear pane onto a blurry, sunny, familiar place; the panes
around it covered with cardboard and tape; its whispers and its grab), the vanishing elevator
(brushed-steel car, rubber floor, the ride down and the chime) and Level 4.3, the Cubicles (dim
white hallways; paper, pencils, murmurs and muffled rain).

Run only these with:  python tools/gen_assets.py level4_
"""
from __future__ import annotations

import threading

import numpy as np

import gen_assets as G

S = G.S


def _blk(name: str):
    return G.TEX / "block" / f"{name}.png"


def _mcmeta(name: str, frametime: int, interpolate: bool) -> None:
    _blk(name).with_suffix(".png.mcmeta").write_text(
        '{\n  "animation": {\n    "frametime": %d,\n    "interpolate": %s\n  }\n}\n'
        % (frametime, "true" if interpolate else "false"))


# ---------------------------------------------------------------- looping stairwell ---

def tex_level4_stairwell_wall() -> None:
    """Plain grey painted concrete block: 2 blocks by 4 courses per face, running bond, thin joints."""
    rng = G.rng_for(9701)
    X, Y = G.grid()
    img = G._smooth_concrete(rng, (150, 151, 150), mottle=2.6, fine=1.0)
    img = G._pores(img, rng, density=0.015, depth=8)
    course = np.floor(Y / 32).astype(int)
    xo = (X + 32 * (course % 2)) % 64
    yo = Y % 32
    joint = np.clip(G.smoothstep(2.4, 1.0, np.minimum(xo, 64 - xo)) + G.smoothstep(2.4, 1.0, np.minimum(yo, 32 - yo)), 0, 1)
    img = G.add_lum(img, -4 * G.smoothstep(6, 31, yo))                    # paint sags a little on each block
    img = G.over(img, np.array([124, 125, 124]), joint * 0.8)
    scuff = G.smoothstep(1.2, 2.4, G.fnoise(rng, beta=2.0, aniso=(4, 1))) * 0.25
    img = G.over(img, np.array([110, 108, 104]), scuff)
    G.save_png(img, _blk("l4_stairwell_wall"))


def tex_level4_floor_sign() -> None:
    """The landing sign: a charcoal plate (model area x 16..112, y 8..120) with FLOOR, a big 4,
    STAIR B and a row of braille dots, like real stairwell signage."""
    rng = G.rng_for(9702)
    X, Y = G.grid()
    img = G.add_lum(G.fill((52, 54, 58), (S, S)), G.fnoise(rng, beta=1.0, aniso=(1, 8)) * 2.5 + rng.normal(0, 1.2, (S, S)))
    plate = G.box((S, S), 16, 8, 112, 120)
    border = plate - G.box((S, S), 20, 12, 108, 116)
    img = G.add_lum(img, 30 * border)
    white = np.array([232, 232, 226])
    img = G.over(img, white, G._text("FLOOR", 64, 26, 15, maxw=80) * 0.95)
    img = G.over(img, white, G._text("4", 64, 66, 58, maxw=80))
    img = G.over(img, white, G._text("STAIR B", 64, 98, 11, maxw=80) * 0.9)
    dots = np.zeros((S, S))
    for i, x in enumerate(range(40, 92, 6)):
        for j, y in enumerate((106, 110)):
            if (i * 3 + j) % 4 != 1:
                dots = np.maximum(dots, G.smoothstep(1.6, 0.8, np.hypot(X - x, Y - y)))
    img = G.over(img, np.array([150, 150, 148]), dots)
    G.save_png(img, _blk("l4_floor_sign"))


# ---------------------------------------------------------------- the Window ---

WINDOW_FRAMES = 32


def _sunny_view(frame: int, frames: int) -> np.ndarray:
    """One frame of the Window's view (128 x 256, two panes tall): a summer afternoon somewhere
    you've been, out of focus. Sky, sun, slow clouds, hills, a meadow, a tree and a little house."""
    rng = G.rng_for(9710)
    H, W = 2 * S, S
    shp = (H, W)
    X, Y = G.grid(shp)
    t = frame / frames
    horizon = 168.0
    sky_t = G.smoothstep(0, horizon, Y)[..., None]
    img = G.lerp(np.array([104, 152, 214]), np.array([252, 228, 176]), sky_t ** 1.6)
    # The low sun, a little to the right: a wide warm bloom and a bright core.
    img = G.add_lum(img, 70 * np.exp(-0.5 * (((X - 90) / 34) ** 2 + ((Y - 118) / 30) ** 2)), (1.0, 0.92, 0.72))
    img = G.add_lum(img, 60 * np.exp(-0.5 * (((X - 90) / 9) ** 2 + ((Y - 118) / 9) ** 2)), (1.0, 0.98, 0.9))
    # Clouds drifting by: a periodic noise field moved one whole width per loop (so it loops).
    clouds = G.fnoise(rng, shp, beta=3.0, aniso=(0.6, 1.4))
    clouds = np.roll(clouds, int(round(t * W)), axis=1)
    cover = G.smoothstep(0.5, 1.6, clouds) * G.smoothstep(horizon - 10, 30, Y)
    img = G.over(img, np.array([250, 240, 226]), cover * 0.75)
    # Far hills, blue-green in the haze; then the meadow, golden green, rolling toward you.
    far = horizon - 14 + 8 * np.sin(X / 23.0 + 1.1) + 5 * np.sin(X / 9.0)
    img = G.over(img, np.array([132, 160, 150]), G.smoothstep(far - 1.5, far + 1.5, Y))
    near = horizon + 2 + 6 * np.sin(X / 31.0 + 2.0)
    meadow = G.smoothstep(near - 1.5, near + 1.5, Y)
    grass = G.lerp(np.array([196, 196, 104]), np.array([118, 150, 64]), G.smoothstep(near, H, Y)[..., None])
    # The grass shimmers in a breeze (a slow travelling ripple, whole cycles per loop).
    ripple = np.sin(2 * np.pi * (t * 2 + X / 64.0 + Y / 40.0))
    grass = G.add_lum(grass, 8 * ripple + G.fnoise(rng, shp, beta=2.2) * 6, (1.0, 1.0, 0.8))
    img = G.over(img, grass, meadow)
    # A big tree on the left, swaying a touch.
    sway = 1.5 * np.sin(2 * np.pi * t)
    ground = lambda x: horizon + 2 + 6 * np.sin(x / 31.0 + 2.0)   # the meadow's edge at column x
    crown = np.exp(-0.5 * (((X - 26 - sway) / 17) ** 2 + ((Y - (ground(26) - 30)) / 21) ** 2))
    img = G.over(img, np.array([58, 92, 46]), G.smoothstep(0.3, 0.6, crown))
    img = G.over(img, np.array([70, 52, 40]), G.box(shp, 24, ground(26) - 14, 29, ground(26) + 4))
    # A little white house with a red roof, half behind the meadow's rise.
    hx, hy = 70, ground(70) - 6
    img = G.over(img, np.array([238, 228, 206]), G.box(shp, hx - 9, hy - 8, hx + 9, hy + 4))
    roof = G.pil_mask(lambda d, k: d.polygon([((hx - 12) * k, (hy - 8) * k), (hx * k, (hy - 18) * k),
                                              ((hx + 12) * k, (hy - 8) * k)], fill=255), shp)
    img = G.over(img, np.array([176, 70, 52]), roof)
    img = G.over(img, np.array([92, 120, 150]), G.box(shp, hx - 5, hy - 5, hx - 1, hy - 1))
    # Out of focus, and glowing a little: it's a memory more than a place.
    img = G.blur(img, 3.2)
    img = img + 0.35 * G.blur(np.clip(img - 200, 0, None), 9)
    # Spotless glass: just one faint reflected streak (no rain on this one).
    img = G.add_lum(img, 10 * G.smoothstep(0.9, 1.0, np.sin((X + Y * 0.5) / 22.0)))
    return np.clip(img, 0, 255)


def tex_level4_clear_window() -> None:
    lower, upper = [], []
    for f in range(WINDOW_FRAMES):
        sheet = _sunny_view(f, WINDOW_FRAMES)
        upper.append(G._l4_window_frame(sheet[:S], lower=False))
        lower.append(G._l4_window_frame(sheet[S:], lower=True))
    for name, frames in (("l4_clear_window_upper", upper), ("l4_clear_window_lower", lower)):
        G.save_png(np.concatenate(frames, axis=0), _blk(name))
        _mcmeta(name, 12, True)


def tex_level4_covered_window() -> None:
    """A window covered from the inside: cardboard and a black bin bag held on with grey duct tape,
    and a warning scrawled in marker. Two panes tall (upper and lower halves)."""
    rng = G.rng_for(9711)
    H, W = 2 * S, S
    shp = (H, W)
    X, Y = G.grid(shp)
    # Cardboard: brown, corrugation ribs, a crease, scuffed print.
    card = G.add_lum(G.fill((166, 128, 86), shp), G.fnoise(rng, shp, beta=2.4) * 6 + rng.normal(0, 2, shp))
    card = G.add_lum(card, 4 * np.sin(2 * np.pi * X / 5.0))
    card = G.add_lum(card, -18 * G.smoothstep(1.5, 0.3, np.abs(Y - 150 - 0.1 * X)))
    img = card
    # A black plastic bag over the bottom of the lower pane, wrinkled and shiny.
    bag = G.box(shp, 4, 196, W - 4, H)
    wrinkles = G.fnoise(rng, shp, beta=1.6, aniso=(2.5, 1.0))
    plastic = G.add_lum(G.fill((24, 24, 26), shp), 14 * G.smoothstep(0.8, 2.0, wrinkles) + 4 * wrinkles)
    img = G.over(img, plastic, bag)
    # Duct tape: strips along the edges, a cross over the middle.
    tape = np.zeros(shp)
    for y0 in (10, 128, 196, 244):
        tape = np.maximum(tape, G.box(shp, 0, y0 - 5, W, y0 + 5))
    for x0 in (8, W - 8):
        tape = np.maximum(tape, G.box(shp, x0 - 5, 0, x0 + 5, H))
    for sgn in (1, -1):
        d = np.abs((X - W / 2) * 1.6 - sgn * (Y - 100)) / np.hypot(1.6, 1)
        tape = np.maximum(tape, G.smoothstep(6, 4.5, d) * G.box(shp, 14, 30, W - 14, 170))
    silver = G.add_lum(G.fill((168, 170, 172), shp), G.fnoise(rng, shp, beta=0.8, aniso=(1, 6)) * 8 + rng.normal(0, 3, shp))
    img = G.over(img, silver, tape * 0.95)
    # "DON'T LOOK" in black marker, a little crooked.
    text = G.pil_mask(lambda d, k: d.text((64 * k, 66 * k), "DON'T", fill=255, font=G.font(22 * k), anchor="mm"), shp)
    text2 = G.pil_mask(lambda d, k: d.text((66 * k, 92 * k), "LOOK", fill=255, font=G.font(24 * k), anchor="mm"), shp)
    img = G.over(img, np.array([28, 24, 24]), np.clip(text + text2, 0, 1) * 0.85)
    upper = G._l4_window_frame(img[:S], lower=False)
    lower = G._l4_window_frame(img[S:], lower=True)
    G.save_png(upper, _blk("l4_covered_window_upper"))
    G.save_png(lower, _blk("l4_covered_window_lower"))


# ---------------------------------------------------------------- vanishing elevator ---

def tex_level4_elevator_wall() -> None:
    """Brushed stainless steel car panel: vertical grain, a seam on one edge, a faint smudge."""
    rng = G.rng_for(9720)
    X, Y = G.grid()
    img = G.add_lum(G.fill((168, 170, 174), (S, S)),
                    G.fnoise(rng, beta=0.5, aniso=(1, 40)) * 10 + rng.normal(0, 2.5, (S, S)))
    img = G.add_lum(img, 10 * G.smoothstep(S, 0, Y))                       # the lamp's sheen up high
    img = G.add_lum(img, -46 * G.box((S, S), -1, -1, 1.5, S + 1) + 18 * G.box((S, S), 1.5, -1, 3, S + 1))
    smudge = G.smoothstep(1.3, 2.4, G.fnoise(rng, beta=2.2)) * 0.25
    img = G.over(img, np.array([140, 140, 140]), smudge)
    G.save_png(img, _blk("l4_elevator_wall"))


def tex_level4_elevator_floor() -> None:
    """Dark grey studded rubber flooring (a raised round stud every 16 px)."""
    rng = G.rng_for(9721)
    X, Y = G.grid()
    img = G.add_lum(G.fill((50, 50, 52), (S, S)), G.fnoise(rng, beta=2.0) * 3 + rng.normal(0, 1.5, (S, S)))
    dx = G.wrapd(X - 8, 16)
    dy = G.wrapd(Y - 8, 16)
    r = np.hypot(dx, dy)
    stud = G.smoothstep(4.2, 3.4, r)
    img = G.add_lum(img, 10 * stud - 14 * G.smoothstep(1.2, 0, np.abs(r - 4.2)) * (dy > 0))
    img = G.add_lum(img, 12 * G.smoothstep(2.0, 0.5, np.hypot(dx + 1.2, dy + 1.2)) * stud)   # top-left highlight
    scuff = G.smoothstep(1.0, 2.2, G.fnoise(rng, beta=1.8)) * 0.3
    img = G.over(img, np.array([86, 84, 80]), scuff)
    G.save_png(img, _blk("l4_elevator_floor"))


# ---------------------------------------------------------------- the Cubicles ---

def tex_level4_white_wall() -> None:
    """The dim white hallways: plain white paint over drywall, cleaner than the rest of the office."""
    G.save_png(G._l4_drywall(9730, (234, 234, 230)), _blk("l4_white_wall"))


def tex_level4_white_floor() -> None:
    """Off-white vinyl composition tile, 2 x 2 per block, faint flecks and seams."""
    rng = G.rng_for(9731)
    X, Y = G.grid()
    img = G.add_lum(G.fill((222, 221, 214), (S, S)), G.fnoise(rng, beta=2.6) * 3 + rng.normal(0, 1.5, (S, S)))
    fleck = (rng.random((S, S)) < 0.05) * rng.uniform(-1, 1, (S, S))
    img = G.add_lum(img, G.blur(fleck, 0.7) * 40)
    tile = (np.floor(X / 64) + np.floor(Y / 64)) % 2
    img = G.add_lum(img, 3 * tile)
    seam = np.clip(G.smoothstep(1.2, 0.3, np.minimum(X % 64, 64 - X % 64)) + G.smoothstep(1.2, 0.3, np.minimum(Y % 64, 64 - Y % 64)), 0, 1)
    img = G.add_lum(img, -16 * seam)
    G.save_png(img, _blk("l4_white_floor"))


# ---------------------------------------------------------------- sounds: helpers ---

def _save_long_ogg(x, path, **kwargs) -> None:
    """G.save_ogg for long loops. libsndfile's Vorbis encoder (at the loop quality) overflows the
    main thread's stack on Windows for ~20 s of audio, so the file is written from a thread with a
    big stack instead."""
    errors = []

    def write():
        try:
            G.save_ogg(x, path, **kwargs)
        except Exception as e:  # handed back to the caller below
            errors.append(e)

    old = threading.stack_size(64 * 1024 * 1024)
    try:
        worker = threading.Thread(target=write)
        worker.start()
        worker.join()
    finally:
        threading.stack_size(old)
    if errors:
        raise errors[0]


def _room(x, rng, dur, decay, lp, wet, predelay=0.02):
    """x plus a reverb tail of the given length (s), decay and darkness."""
    n = len(x)
    tail = G.conv(x, G.reverb_ir(rng, dur, decay, lp=lp, predelay=predelay))[:n]
    return G.norm(x) + wet * G.norm(tail)


def _step(rng, hard=1.0) -> np.ndarray:
    """One shoe on a concrete stair: a short scuff-click and a dull thump."""
    k = int(0.09 * G.SR)
    t = np.arange(k) / G.SR
    click = G.fft_filter(rng.normal(size=k) * np.exp(-t / 0.006), lambda f: G.bp_gain(f, 400, 3500, 1))
    thump = np.sin(2 * np.pi * rng.uniform(90, 130) * t) * np.exp(-t / 0.025)
    scuff = G.fft_filter(rng.normal(size=k) * np.exp(-t / 0.03) * 0.3, lambda f: G.bp_gain(f, 1500, 6000, 1))
    return hard * G.norm(click) + 0.7 * G.norm(thump) + 0.25 * G.norm(scuff)


def _door_slam(rng) -> np.ndarray:
    """A heavy steel fire door swinging shut: latch clack, a deep boom, a rattle."""
    x = np.zeros(int(1.2 * G.SR))
    G.add_at(x, 0.9 * G.ring([62, 96, 151], [0.25, 0.15, 0.09], [1.0, 0.6, 0.35], 1.0), 0.02, False)
    G.add_at(x, 0.5 * G.ring([820, 1340, 2210], [0.03, 0.02, 0.012], [1.0, 0.6, 0.4], 0.2), 0.0, False)
    k = int(0.05 * G.SR)
    G.add_at(x, 0.4 * G.fft_filter(rng.normal(size=k) * np.exp(-np.arange(k) / (0.01 * G.SR)),
                                   lambda f: G.lp_gain(f, 900, 2)), 0.0, False)
    for i in range(4):
        G.add_at(x, 0.12 * G.ring([440 + 60 * i, 990], [0.02, 0.01], [1.0, 0.4], 0.08), 0.12 + 0.05 * i, False)
    return x


def _whisper_breath(rng, dur, rate_lo=0.16, rate_hi=0.34) -> np.ndarray:
    """Soft unintelligible whispering: breath through two wandering formants, in slow syllables."""
    n = int(dur * G.SR)
    t = np.arange(n) / G.SR
    breath = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 300, 6000, 1))
    f1 = 420 + 260 * (0.5 + 0.5 * np.sin(2 * np.pi * rng.uniform(1.5, 2.5) * t + rng.uniform(0, 6)))
    f1 = f1 + G.smooth_jitter(rng, n, 4.0, 110.0)
    f2 = 1300 + 700 * (0.5 + 0.5 * np.sin(2 * np.pi * rng.uniform(2.0, 3.2) * t + rng.uniform(0, 6)))
    f2 = f2 + G.smooth_jitter(rng, n, 5.0, 300.0)
    x = G.svf(breath, f1, 5.0) + 0.7 * G.svf(breath, f2, 7.0)
    env = np.zeros(n)
    tt = 0.1
    while tt < dur - 0.35:
        length = rng.uniform(rate_lo, rate_hi)
        seg = np.arange(int(length * G.SR)) / G.SR
        G.add_at(env, np.sin(np.pi * seg / length) ** 2 * rng.uniform(0.5, 1.0), tt, False)
        tt += length + rng.uniform(0.05, 0.25)
    return x * env


def _murmur(rng, dur, f0_base) -> np.ndarray:
    """An unclear voice through a wall: a formant voice in syllables, everything above 1 kHz gone."""
    n = int(dur * G.SR)
    t = np.arange(n) / G.SR
    f0 = f0_base * (1 + 0.08 * np.sin(2 * np.pi * rng.uniform(0.3, 0.7) * t) + G.smooth_jitter(rng, n, 3.0, 0.06))
    vowels = [((700, 90, 1.0), (1200, 110, 0.6)), ((400, 80, 1.0), (2000, 140, 0.4)),
              ((300, 70, 1.0), (900, 100, 0.6)), ((550, 90, 1.0), (1700, 130, 0.5))]
    x = np.zeros(n)
    tt = 0.05
    while tt < dur - 0.2:
        length = rng.uniform(0.12, 0.3)
        m = int(length * G.SR)
        seg = G.voice(f0[int(tt * G.SR):int(tt * G.SR) + m], vowels[rng.integers(len(vowels))], rng, nh=24)
        seg = seg * np.sin(np.pi * np.arange(len(seg)) / max(len(seg), 1)) ** 1.5 * rng.uniform(0.4, 1.0)
        G.add_at(x, seg, tt, False)
        tt += length + (rng.uniform(0.25, 0.6) if rng.random() < 0.2 else rng.uniform(0.01, 0.06))
    return G.fft_filter(x, lambda f: G.lp_gain(f, 900, 3))


def _paper(rng, dur) -> np.ndarray:
    """Paper being shuffled and straightened: crinkly bursts, a tap of the stack on the desk."""
    n = int(dur * G.SR)
    x = np.zeros(n)
    tt = 0.02
    while tt < dur - 0.25:
        m = int(rng.uniform(0.05, 0.2) * G.SR)
        tm = np.arange(m) / G.SR
        crackle = rng.normal(size=m) * (rng.random(m) < 0.25) * np.sin(np.pi * tm / tm[-1])
        burst = G.fft_filter(crackle, lambda f: G.bp_gain(f, 1800, 9000, 1))
        G.add_at(x, burst * rng.uniform(0.3, 1.0), tt, False)
        tt += m / G.SR + rng.uniform(0.0, 0.12)
    G.add_at(x, 0.6 * G.ring([180, 420], [0.03, 0.015], [1.0, 0.4], 0.12), dur - 0.22, False)
    return x


def _pencil(rng, dur) -> np.ndarray:
    """A pencil writing: a dry scratch in quick strokes."""
    n = int(dur * G.SR)
    t = np.arange(n) / G.SR
    scratch = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 2200, 7500, 2))
    strokes = np.clip(np.sin(2 * np.pi * (rng.uniform(5, 8) * t + G.smooth_jitter(rng, n, 2, 0.6))), 0, 1) ** 2
    lift = G.smoothstep(0.0, 0.05, t) * G.smoothstep(dur, dur - 0.1, t)
    gaps = (G.smooth_jitter(rng, n, 1.5, 1.0) > -0.6).astype(float)
    return scratch * strokes * lift * G.fft_filter(gaps, lambda f: G.lp_gain(f, 30, 1))


def _typing(rng, dur) -> np.ndarray:
    """An old keyboard: uneven bursts of key clicks."""
    n = int(dur * G.SR)
    x = np.zeros(n)
    tt = 0.03
    while tt < dur - 0.1:
        f0 = rng.uniform(1800, 3200)
        click = G.ring([f0, f0 * 1.7, 220], [0.006, 0.004, 0.012], [1.0, 0.5, 0.6], 0.05)
        G.add_at(x, click * rng.uniform(0.4, 1.0), tt, False)
        tt += rng.uniform(0.06, 0.16) if rng.random() < 0.85 else rng.uniform(0.25, 0.45)
    return x


# ---------------------------------------------------------------- sounds ---

def snd_level4_stairwell_echo() -> None:
    """Somebody on the stairs a few floors away: footsteps (and a door) ringing down a concrete shaft."""
    for i in range(1, 4):
        rng = G.rng_for(9800 + i)
        dur = 6.0
        n = int(dur * G.SR)
        x = np.zeros(n)
        steps = 6 + i
        gap = rng.uniform(0.45, 0.6)
        start = 0.2 if i != 3 else 1.4
        for s in range(steps):
            fade = 1.0 - 0.6 * s / steps if i != 2 else 0.4 + 0.6 * s / steps   # going away / coming closer
            G.add_at(x, _step(rng, rng.uniform(0.7, 1.0)) * fade, start + s * gap + rng.uniform(-0.02, 0.02), False)
        if i >= 2:
            G.add_at(x, 0.9 * _door_slam(rng), 0.1 if i == 3 else start + steps * gap + 0.2, False)
        x = G.fft_filter(x, lambda f: G.lp_gain(f, 1800, 2))                 # it's floors away
        out = 0.35 * G.norm(x) + G.norm(G.conv(x, G.reverb_ir(rng, 3.0, 0.9, lp=1600, predelay=0.05))[:n])
        G.save_ogg(out, G.SND / "level4" / f"stairwell_echo{i}.ogg", peak=0.7, fade_out=0.6)


def snd_level4_window_whisper() -> None:
    """Soft, warm, comforting whispers (no words), over a faint major chord: come and see."""
    for i in range(1, 4):
        rng = G.rng_for(9810 + i)
        dur = 3.2 + 0.4 * i
        n = int(dur * G.SR)
        t = np.arange(n) / G.SR
        w = _whisper_breath(rng, dur)
        w = G.fft_filter(w, lambda f: G.lp_gain(f, 3800, 2))
        root = [196.0, 220.0, 174.6][i - 1]
        pad = sum(a * np.sin(2 * np.pi * root * r * t + rng.uniform(0, 6)) for r, a in ((1, 1.0), (1.26, 0.7), (1.5, 0.6), (2, 0.3)))
        pad *= G.env_adsr(n, 1.2, 1.4) * (1 + 0.1 * np.sin(2 * np.pi * 0.4 * t))
        x = G.norm(w) + 0.12 * G.norm(pad)
        out = _room(x, rng, 1.6, 0.4, 3000, 0.4)
        G.save_ogg(out, G.SND / "level4" / f"window_whisper{i}.ogg", peak=0.6, fade_in=0.2, fade_out=0.6)


def snd_level4_window_grab() -> None:
    """The Window grabs: a rush of air sucked in, a deep thud against the glass, a ringing pane."""
    rng = G.rng_for(9820)
    dur = 2.0
    n = int(dur * G.SR)
    t = np.arange(n) / G.SR
    x = np.zeros(n)
    hit = 0.38
    swell = G.smoothstep(0.0, hit, t) ** 3 * (t < hit)
    rush = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 250, 2500, 1)) * swell
    x += 0.6 * G.norm(rush)
    m = int(1.2 * G.SR)
    tm = np.arange(m) / G.SR
    boom = np.sin(2 * np.pi * np.cumsum(90 * np.exp(-tm / 0.25) + 32) / G.SR) * np.exp(-tm / 0.35)
    G.add_at(x, 1.0 * G.norm(boom), hit, False)
    G.add_at(x, 0.35 * G.ring([380, 1010, 2240], [0.6, 0.35, 0.2], [1.0, 0.5, 0.3], 1.4), hit, False)
    k = int(0.04 * G.SR)
    G.add_at(x, 0.5 * G.fft_filter(rng.normal(size=k) * np.exp(-np.arange(k) / (0.008 * G.SR)),
                                   lambda f: G.lp_gain(f, 1200, 2)), hit, False)
    out = _room(x, rng, 1.5, 0.45, 2500, 0.35)
    G.save_ogg(out, G.SND / "level4" / "window_grab.ogg", peak=0.9, fade_out=0.4)


def snd_level4_elevator_ride() -> None:
    """5.5 s in a dark elevator: the doors shut, the motor winds up, and down it goes, and goes."""
    rng = G.rng_for(9830)
    dur = 5.5
    n = int(dur * G.SR)
    t = np.arange(n) / G.SR
    x = np.zeros(n)
    # Doors sliding shut, then meeting with a thunk.
    slide = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 60, 400, 2)) * G.smoothstep(0.0, 0.1, t) * G.smoothstep(0.6, 0.45, t)
    x += 0.4 * G.norm(slide)
    G.add_at(x, 0.8 * G.ring([70, 141, 260], [0.2, 0.1, 0.05], [1.0, 0.5, 0.3], 0.8), 0.6, False)
    # The motor: a hum that winds up and stays, its pitch sagging as the car drops.
    run = G.smoothstep(0.9, 1.6, t) * G.smoothstep(dur, dur - 0.8, t)
    f_motor = 96 + 14 * G.smoothstep(0.9, 1.8, t) - 6 * G.smoothstep(1.8, dur, t)
    hum = sum(a * np.sin(2 * np.pi * np.cumsum(f_motor * h) / G.SR) for h, a in ((1, 1.0), (2, 0.5), (3, 0.25), (4, 0.12)))
    x += 0.35 * G.norm(hum) * run
    # Air rushing past the car and the cables ticking over sheaves: you are falling a long way.
    rush = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 120, 900, 2))
    x += 0.4 * G.norm(rush) * run * (1 + 0.3 * np.sin(2 * np.pi * 0.7 * t))
    tick = 1.4
    while tick < dur - 0.6:
        G.add_at(x, 0.08 * G.ring([1400, 2300], [0.01, 0.006], [1.0, 0.5], 0.04), tick, False)
        tick += rng.uniform(0.18, 0.32)
    # A faint falling whine under it all.
    whine = np.sin(2 * np.pi * np.cumsum(620 - 200 * G.smoothstep(1.2, dur, t)) / G.SR)
    x += 0.04 * whine * run
    x = G.fft_filter(x, lambda f: G.lp_gain(f, 4000, 2))
    out = _room(x, rng, 0.5, 0.12, 2000, 0.25, predelay=0.005)
    G.save_ogg(out, G.SND / "level4" / "elevator_ride.ogg", peak=0.75, fade_out=0.5)


def snd_level4_elevator_ding() -> None:
    """An elevator arriving: a soft two-tone chime, then the doors rolling open."""
    rng = G.rng_for(9840)
    dur = 2.6
    n = int(dur * G.SR)
    t = np.arange(n) / G.SR
    x = np.zeros(n)
    for start, f0 in ((0.0, 1318.5), (0.42, 1046.5)):
        bell = G.ring([f0, f0 * 2.0, f0 * 3.01, f0 * 4.2], [1.1, 0.5, 0.25, 0.12], [1.0, 0.35, 0.18, 0.08], 1.6)
        G.add_at(x, 0.6 * bell, start, False)
    slide = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 60, 500, 2)) * G.smoothstep(0.9, 1.1, t) * G.smoothstep(2.1, 1.8, t)
    x += 0.25 * G.norm(slide)
    G.add_at(x, 0.3 * G.ring([90, 180], [0.1, 0.05], [1.0, 0.4], 0.4), 2.05, False)
    out = _room(x, rng, 1.2, 0.35, 4000, 0.3)
    G.save_ogg(out, G.SND / "level4" / "elevator_ding.ogg", peak=0.75, fade_out=0.3)


def snd_level4_cubicles_ambience() -> None:
    """20 s seamless loop: a floor full of people working that you never see, in a white fog.

    Shuffling paper, pencils, keys, unclear voices from a few cubicles over, the air handling,
    and Level 4's rain and thunder heard muffled through many walls. Everything is placed
    circularly (and the room is added circularly), so the loop has no seam.
    """
    rng = G.rng_for(9850)
    T = 20.0
    n = int(T * G.SR)
    x = np.zeros(n)
    # Muffled rain and the air handling.
    rain = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 150, 1200, 2)) * G.drift(n, rng, 0.25, 0.05, 0.2)
    hvac = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 40, 160, 2))
    x += 0.10 * G.norm(rain) + 0.06 * G.norm(hvac)
    # One roll of thunder, far away.
    m = int(5.0 * G.SR)
    tm = np.arange(m) / G.SR
    roll = G.fft_filter(rng.normal(size=m), lambda f: G.lp_gain(f, 110, 3)) * G.smoothstep(0, 0.6, tm) * np.exp(-tm / 1.6)
    G.add_at(x, 0.30 * G.norm(roll), rng.uniform(0, T), True)
    # People at work.
    events = []
    for _ in range(14):
        events.append((_paper(rng, rng.uniform(0.5, 1.2)), 0.10))
    for _ in range(10):
        events.append((_pencil(rng, rng.uniform(0.6, 1.6)), 0.05))
    for _ in range(6):
        events.append((_typing(rng, rng.uniform(0.8, 1.8)), 0.07))
    for _ in range(7):
        events.append((_murmur(rng, rng.uniform(1.2, 2.6), rng.uniform(95, 210)), 0.09))
    for sig, gain in events:
        G.add_at(x, gain * rng.uniform(0.4, 1.0) * G.norm(sig), rng.uniform(0, T), True)
    # A big low room, and it all sounds a little far away.
    x = G.fft_filter(x, lambda f: G.lp_gain(f, 5000, 2), circular=True)
    ir = G.reverb_ir(rng, 1.6, 0.45, lp=2400, predelay=0.03)
    x = x + 0.5 * G.circ_conv(x, ir)
    _save_long_ogg(x, G.SND / "level4" / "cubicles_ambience.ogg", peak=0.5, loop=True)


def snd_level4_cubicles_work() -> None:
    """Four one-shots of someone working a few cubicles away: paper, a pencil, a murmur, keys."""
    makers = [lambda r: _paper(r, 1.3), lambda r: _pencil(r, 1.6), lambda r: _murmur(r, 2.2, 140.0),
              lambda r: _typing(r, 1.5)]
    for i, make in enumerate(makers, start=1):
        rng = G.rng_for(9860 + i)
        x = make(rng)
        x = np.concatenate([x, np.zeros(int(0.6 * G.SR))])
        x = G.fft_filter(x, lambda f: G.lp_gain(f, 4500, 2))
        out = _room(x, rng, 1.2, 0.35, 2600, 0.45)
        G.save_ogg(out, G.SND / "level4" / f"cubicles_work{i}.ogg", peak=0.6, fade_out=0.3)


TEXTURES = [
    tex_level4_stairwell_wall, tex_level4_floor_sign, tex_level4_clear_window, tex_level4_covered_window,
    tex_level4_elevator_wall, tex_level4_elevator_floor, tex_level4_white_wall, tex_level4_white_floor,
]
SOUNDS = [
    snd_level4_stairwell_echo, snd_level4_window_whisper, snd_level4_window_grab, snd_level4_elevator_ride,
    snd_level4_elevator_ding, snd_level4_cubicles_ambience, snd_level4_cubicles_work,
]
