"""Flares and glowsticks: item icons, the plain white texture their entity models are tinted
with, and their sounds (strike, burning hiss in air and underwater, sputter, glowstick crack).

Run only these with:  python tools/gen_assets.py light_supplies_
"""
from __future__ import annotations

import numpy as np

import gen_assets as G

SR = G.SR


def _snd(name: str):
    return G.SND / "supplies" / f"{name}.ogg"


# ------------------------------------------------------------------ textures ---

def tex_light_supplies_flare() -> None:
    """A red road flare lying diagonally: paper body, black striker cap, pale unlit tip."""
    rng = G.rng_for(7301)
    img = np.zeros((16, 16, 4))
    for i in range(11):
        x, y = 3 + i, 12 - i
        for w in (-1, 0, 1):
            shade = (200, 34, 26) if w == 0 else ((228, 60, 44) if w < 0 else (140, 20, 16))
            c = np.array(shade, float) + rng.normal(0, 4, 3)
            img[y + w, x] = (*c, 255)
    # black striker cap at the bottom-left end, pale composite tip at the top right
    for i in range(3):
        for w in (-1, 0, 1):
            img[12 - i + w, 3 + i] = (30, 28, 28, 255)
    for i in range(9, 11):
        for w in (-1, 0, 1):
            img[12 - i + w, 3 + i] = (236, 214, 170, 255)
    # a thin white label band
    for w in (-1, 0, 1):
        img[12 - 6 + w, 3 + 6] = (232, 228, 220, 255)
    G.save_png(img, G.TEX / "item" / "flare.png")


def tex_light_supplies_glowstick() -> None:
    """A thin green glowstick lying diagonally, bright core and darker caps."""
    img = np.zeros((16, 16, 4))
    for i in range(12):
        x, y = 2 + i, 13 - i
        img[y, x] = (110, 255, 130, 255)
        img[y - 1, x] = (60, 200, 90, 255)
        img[y, x + 1] = (40, 150, 70, 255) if x + 1 < 16 else img[y, x]
    img[13, 2] = img[12, 2] = (70, 74, 70, 255)
    img[2, 13] = img[1, 13] = (70, 74, 70, 255)
    # little glint
    img[8, 7] = (220, 255, 225, 255)
    G.save_png(img, G.TEX / "item" / "glowstick.png")


def tex_light_supplies_entity() -> None:
    """Plain white: the flare/glowstick models colour it per vertex."""
    img = np.full((16, 16, 4), 255.0)
    G.save_png(img, G.TEX / "entity" / "thrown_light.png")


# -------------------------------------------------------------------- sounds ---

def snd_light_supplies_flare_strike() -> None:
    """Scrape of the striker cap, then the whoosh of the flare catching."""
    rng = G.rng_for(7311)
    n = int(0.9 * SR)
    t = np.arange(n) / SR
    scrape = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 1500, 6000))
    scrape *= np.clip(t / 0.01, 0, 1) * np.clip((0.16 - t) / 0.03, 0, 1)
    scrape *= 1.0 + 0.6 * np.sin(2 * np.pi * 38 * t)  # the grain of the striker
    catch = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 300, 5000))
    env = np.clip((t - 0.12) / 0.06, 0, 1) * np.exp(-np.maximum(t - 0.18, 0) * 4.0)
    x = scrape * 0.8 + catch * env * 0.7
    G.save_ogg(x, _snd("flare_strike"), peak=0.5)


def _hiss(seed: int, lo: float, hi: float, crackle: float) -> np.ndarray:
    rng = G.rng_for(seed)
    n = 4 * SR
    x = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, lo, hi))
    wob = G.periodic_noise(n, rng, lambda f: G.lp_gain(f, 6.0, 2))
    x *= 1.0 + 0.25 * wob
    if crackle > 0:
        pops = np.zeros(n)
        for _ in range(int(4 * 9)):
            p = rng.integers(0, n - 200)
            pops[p:p + 200] += rng.normal(size=200) * np.exp(-np.arange(200) / 30.0)
        x += pops * crackle
    return x


def snd_light_supplies_flare_hiss() -> None:
    """The burning hiss: a soft, wobbling roar with a few crackles. Loops seamlessly."""
    x = _hiss(7312, 900, 7000, 1.2)
    G.save_ogg(x, _snd("flare_hiss"), peak=0.45, loop=True)


def snd_light_supplies_flare_hiss_underwater() -> None:
    """The same flare underwater: muffled low hiss with bubbling. Loops seamlessly."""
    rng = G.rng_for(7313)
    x = _hiss(7313, 120, 900, 0.0)
    n = len(x)
    for _ in range(30):
        start = rng.integers(0, n - SR // 10)
        f0 = rng.uniform(250, 700)
        m = SR // 12
        tt = np.arange(m) / SR
        x[start:start + m] += np.sin(2 * np.pi * f0 * (1 + 3 * tt) * tt) * np.exp(-tt * 40) * 1.5
    G.save_ogg(x, _snd("flare_hiss_underwater"), peak=0.4, loop=True)


def snd_light_supplies_flare_sputter() -> None:
    """The flame catching and dropping a few times, then a last puff."""
    rng = G.rng_for(7314)
    n = int(2.8 * SR)
    t = np.arange(n) / SR
    noise = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 700, 6000))
    gate = np.zeros(n)
    pos = 0.0
    while pos < 2.4:
        ln = rng.uniform(0.05, 0.25)
        a = rng.uniform(0.3, 1.0) * (1.0 - pos / 2.8)
        s, e = int(pos * SR), int(min(pos + ln, 2.8) * SR)
        gate[s:e] = a
        pos += ln + rng.uniform(0.03, 0.2)
    gate = G.fft_filter(gate, lambda f: G.lp_gain(f, 40, 2))
    x = noise * gate
    puff = G.fft_filter(rng.normal(size=n), lambda f: G.lp_gain(f, 900, 2)) * np.exp(-np.maximum(t - 2.4, 0) * 12) * (t > 2.4)
    G.save_ogg(x + puff * 0.6, _snd("flare_sputter"), peak=0.45)


def snd_light_supplies_glowstick_crack() -> None:
    """A plastic snap, then the liquid sloshing as it is shaken a few times."""
    rng = G.rng_for(7315)
    n = int(1.1 * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    snap = G.ring([2400, 3900, 6100], [0.006, 0.004, 0.003], [1.0, 0.7, 0.4], 0.05)
    snap[:60] += rng.normal(size=60) * np.linspace(1, 0, 60)
    G.add_at(x, snap, 0.02, circular=False)
    slosh = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 300, 2500))
    env = np.zeros(n)
    for k, st in enumerate((0.3, 0.52, 0.74)):
        env += np.exp(-((t - st) / 0.05) ** 2) * (1.0 - 0.15 * k)
    x += slosh * env * 0.35
    G.save_ogg(x, _snd("glowstick_crack"), peak=0.5)


TEXTURES = [tex_light_supplies_flare, tex_light_supplies_glowstick, tex_light_supplies_entity]
SOUNDS = [snd_light_supplies_flare_strike, snd_light_supplies_flare_hiss, snd_light_supplies_flare_hiss_underwater,
          snd_light_supplies_flare_sputter, snd_light_supplies_glowstick_crack]
