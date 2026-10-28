"""Level 2 anomalies: textures and sounds.

The brownout (a battery emergency light with a violet lens, caged lamps whose bulbs burst, thick
black ooze from burst pipes and its drips), the series circuits (the pop and fizz of a circuit
failing), the hot zones (faulty machine cabinets, their loud whirring, steam hissing out of the
pipes) and the void doors (a steel door with a pitch-black window, and the matte black of the
void itself).

Run only these with:  python tools/gen_assets.py tex_level2_ snd_level2_circuit snd_level2_machine snd_level2_steam snd_level2_ooze
(plain "level2_" would also re-run gen_assets.py's own snd_level2_ambience).
"""
from __future__ import annotations

import numpy as np

import gen_assets as G

S = G.S
SR = G.SR


def _blk(name: str):
    return G.TEX / "block" / f"{name}.png"


def _snd(name: str):
    return G.SND / "level2" / f"{name}.ogg"


def _small(img, size=64) -> np.ndarray:
    """Box-filter a 128 px texture down to size x size. The block atlas is full, and these are
    small props (a lamp's housing and lens, a bulb) where 64 px is already plenty."""
    k = img.shape[0] // size
    return img.reshape(size, k, size, k, img.shape[2]).mean(axis=(1, 3))


# ---------------------------------------------------------------- brownout ---

def tex_level2_emergency_housing() -> None:
    """Off-white moulded plastic of the emergency light, yellowed and grimy, a fine seam line."""
    rng = G.rng_for(2201)
    X, Y = G.grid()
    img = G._smooth_concrete(rng, (196, 190, 172), mottle=2.0, fine=0.6)
    grime = G.smoothstep(0.4, 1.8, G.fnoise(rng, beta=2.4))
    img = G.over(img, np.array([138, 128, 104]), grime * 0.35)
    img = G.add_lum(img, -18 * G.smoothstep(1.2, 0.3, np.abs(Y - 40)))   # moulding seam
    G.save_png(_small(img), _blk("l2_emergency_housing"))


def tex_level2_emergency_lens() -> None:
    """Frosted violet lens (drawn full-bright): brightest along the middle, prismatic ribs."""
    rng = G.rng_for(2202)
    X, Y = G.grid()
    edge = np.abs(Y - 64) / 64
    img = G.lerp(np.array([226, 196, 255]), np.array([150, 82, 230]), np.clip(edge, 0, 1)[..., None] ** 1.2)
    ribs = 0.5 + 0.5 * np.cos(X / S * 2 * np.pi * 16)
    img = G.add_lum(img, -14 * ribs + G.fnoise(rng, beta=2.0) * 2.0)
    G.save_png(_small(img), _blk("l2_emergency_lens"))


def tex_level2_dead_bulb() -> None:
    """A burst bulb: smoky dark glass, sooty inside, a jagged hole showing the dark socket."""
    rng = G.rng_for(2203)
    X, Y = G.grid()
    r = np.hypot(X - 64, Y - 64) / 64
    img = G.lerp(np.array([92, 88, 84]), np.array([46, 44, 42]), np.clip(r, 0, 1)[..., None])
    soot = G.smoothstep(-0.2, 1.4, G.fnoise(rng, beta=2.2))
    img = G.over(img, np.array([26, 24, 22]), soot * 0.7)
    hole = G.smoothstep(0.15, -0.05, G.fnoise(rng, beta=2.6, kmin=2) * 0.25 + (np.hypot(X - 70, Y - 56) / 64 - 0.28))
    img = G.over(img, np.array([10, 10, 10]), hole)
    cracks = G.smoothstep(0.04, 0.0, np.abs(G.fnoise(rng, beta=2.4, kmin=3)))
    img = G.add_lum(img, 40 * cracks * (1 - hole))
    G.save_png(_small(img), _blk("l2_dead_bulb"))


def tex_level2_black_ooze() -> None:
    """Thick black liquid (cutout): a ragged pool edge near the block borders, dull oily sheen.

    Dark and glossy, never luminous: the sheen is only a slightly lighter, bluish-grey streak."""
    rng = G.rng_for(2204)
    X, Y = G.grid()
    edge = np.minimum(np.minimum(X, S - X), np.minimum(Y, S - Y))          # distance to the border
    ragged = edge + G.fnoise(rng, beta=2.2, kmin=2) * 9.0
    alpha = np.where(ragged > 4.0, 255.0, 0.0)
    img = G.fill((14, 13, 12), (S, S))
    img = G.add_lum(img, G.fnoise(rng, beta=2.6) * 2.5)
    sheen = G.smoothstep(1.0, 2.2, G.fnoise(rng, beta=2.8, aniso=(1, 3)))
    img = G.over(img, np.array([46, 48, 54]), sheen * 0.55)
    bubbles = G.blur((rng.random((S, S)) < 0.004).astype(float), 0.8)
    img = G.add_lum(img, 60 * np.clip(bubbles * 6, 0, 1))
    rgba = np.zeros((S, S, 4))
    rgba[..., :3] = img
    rgba[..., 3] = alpha
    G.save_png(rgba, _blk("l2_black_ooze"))


# ---------------------------------------------------------------- hot zone ---

def _cabinet_steel(rng, base=(92, 104, 96)) -> np.ndarray:
    """Old green-grey painted steel with grime, rust bleeding from the edges and rivets."""
    X, Y = G.grid()
    img = G._smooth_concrete(rng, base, mottle=2.4, fine=0.8)
    grime = G.smoothstep(0.5, 1.9, G.fnoise(rng, beta=2.4, aniso=(1, 2)))
    img = G.over(img, np.array([60, 58, 50]), grime * 0.45)
    border = 1 - G.box((S, S), 6, 6, S - 6, S - 6)
    img = G.add_lum(img, -26 * border)
    rust = G.smoothstep(1.1, 2.0, G.fnoise(rng, beta=1.8, kmin=3)) * np.clip(border + G.smoothstep(100, 128, Y), 0, 1)
    img = G.over(img, np.array([118, 66, 40]), rust * 0.8)
    for cx in (12, S - 12):
        for cy in (12, S - 12):
            d = np.hypot(X - cx, Y - cy)
            img = G.add_lum(img, 30 * np.clip(2.6 - d, 0, 1) - 22 * np.clip(3.6 - d, 0, 1) * (d > 2.6))
    return img


def tex_level2_machine_front() -> None:
    """The cabinet's face: two round pressure gauges (needles pinned in the red), a louvred vent
    stained with soot and oil, and a faded yellow-black warning stripe along the bottom."""
    rng = G.rng_for(2205)
    X, Y = G.grid()
    img = _cabinet_steel(rng)
    # Louvres (rows of slots), darker and sooty above.
    louvre = G.box((S, S), 18, 62, S - 18, 102)
    slats = (np.floor((Y - 62) / 5) % 2 == 0) * louvre
    img = G.over(img, np.array([18, 18, 16]), slats * 0.9)
    img = G.add_lum(img, 16 * louvre * (1 - slats) * G.smoothstep(4, 0, (Y - 62) % 5))
    soot = G.smoothstep(0.2, 1.5, G.fnoise(rng, beta=2.0, aniso=(3, 1))) * G.smoothstep(40, 62, Y) * G.smoothstep(104, 64, Y)
    img = G.over(img, np.array([28, 26, 24]), soot * 0.4)
    # Gauges.
    for cx, ang in ((40, 2.1), (88, 2.5)):
        cy = 34
        d = np.hypot(X - cx, Y - cy)
        img = G.over(img, np.array([70, 70, 72]), np.clip(17.5 - d, 0, 1))          # bezel
        img = G.over(img, np.array([222, 216, 196]), np.clip(14.5 - d, 0, 1))       # dial
        th = np.arctan2(Y - cy, X - cx)
        red = (th > -0.2) & (th < 0.8) & (d > 9) & (d < 13.5)
        img = G.over(img, np.array([186, 36, 30]), red.astype(float))
        ticks = (np.abs(np.sin(th * 6)) < 0.12) & (d > 11) & (d < 14)
        img = G.over(img, np.array([40, 40, 40]), ticks.astype(float) * 0.8)
        nx, ny = np.cos(ang - np.pi), np.sin(ang - np.pi)
        along = (X - cx) * nx + (Y - cy) * ny
        across = np.abs(-(X - cx) * ny + (Y - cy) * nx)
        needle = (along > -2) & (along < 12) & (across < 0.9)
        img = G.over(img, np.array([20, 20, 20]), needle.astype(float))
        img = G.add_lum(img, 18 * np.clip(14.5 - d, 0, 1) * G.smoothstep(14, 0, np.hypot(X - cx + 5, Y - cy + 5)))
    # Warning stripe.
    band = G.box((S, S), 10, 110, S - 10, 120)
    stripes = ((X + Y) % 16 < 8).astype(float)
    img = G.over(img, np.array([196, 160, 40]), band * stripes * 0.75)
    img = G.over(img, np.array([30, 28, 24]), band * (1 - stripes) * 0.75)
    img = G.add_lum(img, rng.normal(0, 1.5, (S, S)))
    G.save_png(img, _blk("l2_machine_front"))


def tex_level2_machine_side() -> None:
    """Cabinet side panel: a seam down the middle, a stamped data plate, oil streaks running down."""
    rng = G.rng_for(2206)
    X, Y = G.grid()
    img = _cabinet_steel(rng)
    img = G.add_lum(img, -20 * G.smoothstep(1.4, 0.3, np.abs(X - 64)))
    plate = G.box((S, S), 78, 22, 112, 42)
    img = G.over(img, np.array([150, 146, 132]), plate * 0.9)
    img = G.over(img, np.array([70, 66, 58]), plate * (np.abs(Y - 30) < 0.8) * 0.6)
    img = G.over(img, np.array([70, 66, 58]), plate * (np.abs(Y - 35) < 0.8) * 0.6)
    streaks = np.clip(G.fnoise(rng, beta=1.2, aniso=(1, 16), kmin=2), 0, None)
    img = G.over(img, np.array([36, 32, 26]), np.clip(streaks * 0.25, 0, 0.35))
    G.save_png(img, _blk("l2_machine_side"))


def tex_level2_machine_top() -> None:
    """Cabinet top: dusty steel with a bolted access hatch."""
    rng = G.rng_for(2207)
    img = _cabinet_steel(rng, base=(88, 96, 90))
    hatch = G.box((S, S), 32, 32, 96, 96) - G.box((S, S), 34, 34, 94, 94)
    img = G.add_lum(img, -24 * hatch)
    dust = G.smoothstep(0.0, 1.6, G.fnoise(rng, beta=2.0))
    img = G.over(img, np.array([120, 116, 104]), dust * 0.3)
    G.save_png(img, _blk("l2_machine_top"))


# ---------------------------------------------------------------- void doors ---

def tex_level2_void_door() -> None:
    """Grey steel door (2 blocks tall): a tall narrow window of pitch-black glass (only a faint
    diagonal reflection), a lever handle and a kick plate. Same frame layout as the fire door."""
    rng = G.rng_for(2208)
    shp = (2 * S, S)
    X, Y = G.grid(shp)
    img = G._smooth_concrete(rng, (112, 114, 116), mottle=3.0, fine=0.8, shape=shp)
    img = G.over(img, np.array([150, 148, 140]), 1 - G.box(shp, 8, 8, S - 8, 2 * S + 20))
    img = G.add_lum(img, -22 * (G.box(shp, 6, 6, S - 6, 2 * S + 20) - G.box(shp, 8, 8, S - 8, 2 * S + 20)))
    grime = G.smoothstep(0.6, 1.8, G.fnoise(rng, shp, beta=2.2))
    img = G.over(img, np.array([72, 70, 66]), grime * 0.35)
    frame = G.box(shp, 40, 26, 88, 118)
    win = G.box(shp, 44, 30, 84, 114)
    img = G.add_lum(img, -30 * (frame - win))
    glass = np.array([4.0, 4.0, 5.0]) + 0 * img
    reflect = G.smoothstep(0.9, 1.0, np.cos((X - Y * 0.6) / 9.0)) * 10
    glass = G.add_lum(glass, reflect)
    img = img * (1 - win[..., None]) + glass * win[..., None]
    img = G.over(img, np.array([70, 70, 72]), G.box(shp, 14, 134, 32, 140))          # lever
    img = G.add_lum(img, 20 * G.box(shp, 14, 134, 32, 135.5))
    img = G.over(img, np.array([150, 150, 152]), G.box(shp, 12, 2 * S - 30, S - 12, 2 * S - 9))
    img = G.add_lum(img, rng.normal(0, 1.2, shp))
    G.save_png(img[:S], _blk("l2_void_door_top"))
    G.save_png(img[S:], _blk("l2_void_door_bottom"))


def tex_level2_void_black() -> None:
    """Near-perfect black with the faintest grain, so it never reads as a lit surface. Saved at
    16 px: a flat colour looks the same at any size and the block atlas is full."""
    rng = G.rng_for(2209)
    img = G.fill((7, 7, 8), (16, 16))
    img = G.add_lum(img, rng.normal(0, 0.6, (16, 16)))
    G.save_png(img, _blk("l2_void_black"))


# ---------------------------------------------------------------- sounds ---

def snd_level2_circuit_pop() -> None:
    """Three takes of a series circuit failing: a sharp crack, a short electrical fizz with mains
    buzz in it, and the tick of the relay dropping out, in a small concrete tunnel."""
    for i in range(1, 4):
        rng = G.rng_for(2220 + i)
        dur = 0.9
        n = int(dur * SR)
        t = np.arange(n) / SR
        crack = np.zeros(n)
        k = int(0.004 * SR)
        crack[:k] = rng.normal(size=k) * np.exp(-np.arange(k) / (0.0012 * SR))
        crack = G.fft_filter(crack, lambda f: G.hp_gain(f, 600, 2))
        fizz_len = 0.18 + 0.12 * rng.random()
        fizz_env = np.exp(-t / fizz_len) * (t > 0.003)
        fizz = rng.normal(size=n) * fizz_env
        fizz = G.fft_filter(fizz, lambda f: G.bp_gain(f, 1500, 7000, 2))
        chop = (np.sin(2 * np.pi * 120 * t) > 0.2 * rng.normal()).astype(float)   # crackle gated by the mains
        buzz = G.mains_hum(t, 120.0, (0, -6, -10, -14, -18, -22)) * np.exp(-t / (fizz_len * 0.8))
        tick = G.ring([2400, 3900], [0.004, 0.003], [1, 0.5], dur)
        relay = np.zeros(n)
        G.add_at(relay, tick[: int(0.05 * SR)], 0.25 + 0.1 * rng.random(), circular=False)
        x = G.norm(crack) * 1.0 + G.norm(fizz * chop) * 0.45 + G.norm(buzz) * 0.25 + G.norm(relay) * 0.2
        wet = G.conv(x, G.reverb_ir(rng, 0.6, 0.12, lp=5000, predelay=0.006))[:n]
        G.save_ogg(x + G.norm(wet) * 0.25, _snd(f"circuit_pop{i}"), peak=0.85)


def snd_level2_machine_loop() -> None:
    """6 s seamless loop: a big, tired machine. A low rotary drone with a lumpy harmonic, a
    bearing whine wavering in pitch, a loose part knocking out of time, and a ragged belt slap.
    Everything is periodic over the loop length (whole cycles, circular noise and placement)."""
    rng = G.rng_for(2230)
    T = 6.0
    n = int(T * SR)
    t = np.arange(n) / SR
    drone = np.zeros(n)
    for h, db in ((1, 0), (2, -4), (3, -9), (4, -10), (5, -16), (6, -20), (8, -26)):
        f = 48.0 * h
        drone += 10 ** (db / 20) * np.sin(2 * np.pi * f * t + rng.uniform(0, 6.28))
    lump = 1 + 0.35 * np.sin(2 * np.pi * (8.0 / T * 6) * t)                     # rotation unevenness, 8 Hz
    rumble = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 30, 160, 2))
    wobble = 1 + 0.015 * np.sin(2 * np.pi * (1 / T) * t) + 0.008 * np.sin(2 * np.pi * (3 / T) * t + 1.0)
    whine = np.sin(2 * np.pi * np.cumsum(1180.0 * wobble) / SR)
    whine *= G.drift(n, rng, 0.6, 0.2, 1.0)
    knocks = np.zeros(n)
    times = np.cumsum(rng.uniform(0.55, 1.1, 12))
    times = times[times < T]
    for tt in times:
        k = G.ring([180, 410, 950], [0.03, 0.02, 0.008], [1, 0.6, 0.3], 0.2)
        k[:40] += rng.normal(size=40) * 0.5
        G.add_at(knocks, k * rng.uniform(0.5, 1.0), tt, circular=True)
    slap = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 300, 2500, 2))
    slap *= (np.sin(2 * np.pi * (24 / T) * t) > 0.92).astype(float)
    slap = G.fft_filter(slap, lambda f: G.lp_gain(f, 3000, 2), circular=True)
    x = (G.norm(drone * lump) * 0.8 + G.norm(rumble) * 0.45 + G.norm(whine) * 0.07
         + G.norm(knocks) * 0.35 + G.norm(slap) * 0.12)
    wet = G.circ_conv(x, G.reverb_ir(rng, 1.0, 0.25, lp=3000, predelay=0.01))
    G.save_ogg(x + G.norm(wet) * 0.2, _snd("machine_loop"), peak=0.8, loop=True)


def snd_level2_steam_hiss() -> None:
    """Three bursts of steam escaping a leaky pipe (~1.6-2.4 s): a sharp onset, a broad hiss that
    sputters as the pressure surges, tailing off."""
    for i in range(1, 4):
        rng = G.rng_for(2240 + i)
        dur = 1.6 + 0.4 * (i - 1)
        n = int(dur * SR)
        t = np.arange(n) / SR
        hiss = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 1800, 11000, 2) * (1 + 0.6 * G.bp_gain(f, 3500, 5000, 3)))
        env = G.smoothstep(0.0, 0.04, t) * np.exp(-np.maximum(t - dur * 0.55, 0) / (dur * 0.18))
        sputter = 1 + 0.35 * G.smooth_jitter(rng, n, 14, 1.0)
        x = hiss * env * sputter
        G.save_ogg(x, _snd(f"steam_hiss{i}"), peak=0.7, fade_out=0.05)


def snd_level2_ooze_drip() -> None:
    """Three thick, viscous plops (~0.5 s): a low gloopy blip that bends down in pitch, a small
    sticky smack, a tiny bubble afterwards."""
    for i in range(1, 4):
        rng = G.rng_for(2250 + i)
        dur = 0.5
        n = int(dur * SR)
        t = np.arange(n) / SR
        f0 = 260 + 60 * rng.random()
        f = f0 * (1.0 - 0.45 * np.clip(t / 0.06, 0, 1))
        plop = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.05) * G.smoothstep(0, 0.003, t)
        smack = rng.normal(size=n) * np.exp(-t / 0.006)
        smack = G.fft_filter(smack, lambda ff: G.bp_gain(ff, 300, 2500, 2))
        bubble = np.zeros(n)
        bt = 0.16 + 0.12 * rng.random()
        b = np.sin(2 * np.pi * np.cumsum(np.full(int(0.05 * SR), 520 + 120 * rng.random())) / SR)
        b *= np.exp(-np.arange(b.size) / (0.012 * SR))
        G.add_at(bubble, b, bt, circular=False)
        x = G.norm(plop) + G.norm(smack) * 0.25 + G.norm(bubble) * 0.18
        x = G.fft_filter(x, lambda ff: G.lp_gain(ff, 2600, 2))
        G.save_ogg(x, _snd(f"ooze_drip{i}"), peak=0.7)


TEXTURES = [
    tex_level2_emergency_housing, tex_level2_emergency_lens, tex_level2_dead_bulb, tex_level2_black_ooze,
    tex_level2_machine_front, tex_level2_machine_side, tex_level2_machine_top,
    tex_level2_void_door, tex_level2_void_black,
]
SOUNDS = [
    snd_level2_circuit_pop, snd_level2_machine_loop, snd_level2_steam_hiss, snd_level2_ooze_drip,
]
