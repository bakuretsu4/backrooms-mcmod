"""Level 227's pond water: splash sounds, the underwater bed and the splash particle textures.

Level 227's ponds are forest pools under a giant canopy: tea-coloured (tannin) water over a soft,
peaty bottom. Nothing here is harsh: soft water, a damp forest around it, no hard tiles to ring.

Sounds (src/main/resources/assets/backrooms/sounds/level227/water/...), mono Ogg at a set loudness
(LUFS, the meter is anomaly_assets/level2_audio.lufs; see README "Mix"), so sounds.json plays
them at volume 1:
  splash_small1..4   a small plop: a foot, a hand, a jump from the bank            (-27 LUFS)
  splash_medium1..3  a proper splash: a drop of a few blocks                         (-23 LUFS)
  splash_big1..2     a belly-flop from high up: slap, thump, a rain of drops falling  (-20 LUFS)
  paddle1..4         a soft stroke / water step at the surface (wading, swimming)    (-31 LUFS)
  under_bed          seamless 24 s loop: the muffled hush of being under the water   (-33 LUFS)
  under_enter1..2    ducking under: a soft rush closing over your ears, bubbles      (-26 LUFS)
  under_exit1..2     coming up: the muffle opening, water running off, a few drips    (-28 LUFS)

Particle textures (src/main/resources/assets/backrooms/textures/particle/, on the particle atlas;
white, the game tints them):
  l227_water_droplet_0..2  8 x 8   a flying drop with a highlight
  l227_water_ring_0        32 x 32 an expanding ring on the surface
  l227_water_foam_0..2     32 x 32 a patch of foam (holes in it, soft edges)
  l227_water_bubble_0..1   8 x 8   a bubble (a bright rim and a glint)

Run:  python tools/level227_water/gen_water.py
Every generator uses its own fixed random seed (22710-22729), so re-running gives identical files.
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))  # tools/, for gen_assets

import gen_assets as G  # noqa: E402
from anomaly_assets import level2_audio as A  # noqa: E402

SR = G.SR
SND_DIR = G.SND / "level227" / "water"
TEX_DIR = G.TEX / "particle"


# =============================================================================
# Helpers
# =============================================================================

def _save(x, name: str, target: float, peak: float = 0.9, loop: bool = False,
          fade_in: float = 0.002, fade_out: float = 0.06) -> None:
    """Brings `x` to `target` LUFS and writes level227/water/<name>.ogg at that level (no
    normalising), in one-second blocks (libsndfile's Vorbis encoder overflows on long writes)."""
    import soundfile as sf
    x = np.asarray(x, float)
    x = x - x.mean()
    if not loop:
        n = len(x)
        fi, fo = max(1, int(fade_in * SR)), max(1, int(fade_out * SR))
        x[:fi] *= np.linspace(0, 1, fi)
        x[n - fo:] *= np.linspace(1, 0, fo) ** 2
    x = A.loud(x, target, peak)
    SND_DIR.mkdir(parents=True, exist_ok=True)
    extra = {"compression_level": 0.0} if loop else {}
    with sf.SoundFile(str(SND_DIR / f"{name}.ogg"), "w", SR, 1, format="OGG", subtype="VORBIS", **extra) as out:
        y = x.astype(np.float32)
        for i in range(0, len(y), SR):
            out.write(y[i:i + SR])


def _env(n, pts):
    """A piecewise-linear envelope through (seconds, level) points."""
    t = np.arange(n) / SR
    return np.interp(t, [p[0] for p in pts], [p[1] for p in pts])


def _bubble(f0, dur, rise=1.6, decay=0.02) -> np.ndarray:
    """One bubble pinching off: a sine whose pitch rises as it shrinks (the 'plip' of water),
    dying away in `decay` seconds."""
    t = np.arange(int(dur * SR)) / SR
    f = f0 * (1.0 + (rise - 1.0) * (1.0 - np.exp(-t / (decay * 1.5))))
    phase = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(phase) * np.exp(-t / decay) * (1.0 - np.exp(-t / 0.0008))


def _room(rng, x, wet=0.18, length=1.6, decay=0.45, lp=3000.0) -> np.ndarray:
    """The forest around the pond: a soft, diffuse, short tail (trunks and leaves, no walls)."""
    ir = G.reverb_ir(rng, length, decay, lp=lp, predelay=0.015)
    out = np.concatenate([x, np.zeros(int(length * SR))])
    tail = G.conv(out, ir)[:len(out)]
    return out + wet * G.norm(tail) * np.abs(x).max()


def _dull_to_bright(rng, n, bright_from: float, bright_to: float, pts) -> np.ndarray:
    """Noise whose tone goes between muffled (low-passed) and open (band-passed) as the
    `pts` curve (seconds, 0 = muffled .. 1 = open) says: hearing through water and through air."""
    base = rng.normal(size=n)
    dull = G.fft_filter(base, lambda f: G.lp_gain(f, 380, 2))
    bright = G.fft_filter(base, lambda f: G.bp_gain(f, bright_from, bright_to, 2))
    w = _env(n, pts)
    return dull * (1.0 - w) * 1.6 + bright * w


# =============================================================================
# Splashes
# =============================================================================

def _splash(rng, size: float) -> np.ndarray:
    """A body meeting the water. `size` 0 = a small plop, 1 = a belly-flop from high up.
    The parts of a real splash, in order: the slap of the surface, the thump of the body (big
    ones), the cavity closing behind it (one or more rising 'bloops'), the drops thrown up
    raining back down, and a fizzing wash dying away."""
    dur = 0.55 + 1.5 * size
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)

    # 1. The slap.
    slap_len = 0.025 + 0.06 * size
    m = int(slap_len * SR)
    slap = rng.normal(size=m) * np.exp(-np.arange(m) / SR / (slap_len / 4.0))
    slap = G.fft_filter(slap, lambda f: G.lp_gain(f, 2200 + 1800 * size, 2) * G.hp_gain(f, 140, 1))
    out[:m] += G.norm(slap) * (0.35 + 0.65 * size)

    # 2. The body's thump (only a real weight hitting the water has one).
    if size > 0.25:
        thump = np.sin(2 * np.pi * (52 + 25 * (1 - size)) * t) * np.exp(-t / (0.05 + 0.1 * size))
        out += thump * (size - 0.2) * 0.9

    # 3. The cavity closing: rising bloops, lower for bigger splashes.
    for i in range(1 + int(round(2 * size))):
        f0 = rng.uniform(420, 720) * (1.35 - 0.55 * size)
        b = _bubble(f0, 0.16 + 0.1 * size, rise=rng.uniform(1.5, 2.1), decay=0.028 + 0.03 * size)
        G.add_at(out, b * rng.uniform(0.45, 0.8), 0.015 + rng.uniform(0.0, 0.04) + 0.06 * size * i, circular=False)

    # 4. Drops raining back onto the water: tiny high plinks, densest just after the impact.
    for _ in range(int(14 + 110 * size)):
        tt = 0.05 + rng.gamma(1.6, 0.08 + 0.3 * size)
        if tt > dur - 0.08:
            continue
        b = _bubble(rng.uniform(1300, 4000), 0.03, rise=rng.uniform(1.1, 1.5), decay=rng.uniform(0.004, 0.011))
        G.add_at(out, b * rng.uniform(0.05, 0.22) * np.exp(-tt / (0.35 + 0.6 * size)), tt, circular=False)

    # 5. The fizzing wash.
    fizz = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 600, 4800, 2))
    out += G.norm(fizz) * _env(n, [(0, 0), (0.02, 1), (0.12 + 0.3 * size, 0.45), (dur, 0)]) * (0.05 + 0.1 * size)

    # Big ones: the water sloshing back over the hole.
    if size > 0.6:
        slosh = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 110, 800, 2))
        out += G.norm(slosh) * _env(n, [(0, 0), (0.12, 0.8), (0.6, 0.45), (dur, 0)]) * 0.3 * size

    out = _room(rng, out)
    # Soft water and a damp forest: nothing sharp up top.
    return G.fft_filter(out, lambda f: G.lp_gain(f, 6500, 1))


def snd_l227water_splashes() -> None:
    rng = np.random.default_rng(22710)
    for i in range(4):
        _save(_splash(rng, rng.uniform(0.0, 0.15)), f"splash_small{i + 1}", -27.0)
    rng = np.random.default_rng(22711)
    for i in range(3):
        _save(_splash(rng, rng.uniform(0.42, 0.58)), f"splash_medium{i + 1}", -23.0)
    rng = np.random.default_rng(22712)
    for i in range(2):
        _save(_splash(rng, rng.uniform(0.92, 1.0)), f"splash_big{i + 1}", -20.0, fade_out=0.2)


def snd_l227water_paddle() -> None:
    """A soft stroke through the surface: a short swish and a couple of small bubbles."""
    rng = np.random.default_rng(22713)
    for i in range(4):
        dur = rng.uniform(0.45, 0.6)
        n = int(dur * SR)
        swish = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 240, 1700, 2))
        attack = rng.uniform(0.05, 0.09)
        out = G.norm(swish) * _env(n, [(0, 0), (attack, 1), (attack + 0.13, 0.5), (dur, 0)]) * 0.5
        for _ in range(rng.integers(2, 5)):
            b = _bubble(rng.uniform(520, 1400), 0.06, rise=rng.uniform(1.3, 1.8), decay=rng.uniform(0.008, 0.02))
            G.add_at(out, b * rng.uniform(0.15, 0.4), rng.uniform(0.04, dur * 0.6), circular=False)
        t = np.arange(n) / SR
        glug = np.sin(2 * np.pi * rng.uniform(170, 250) * t) * np.exp(-((t - attack) / 0.05) ** 2)
        out += glug * 0.18
        _save(_room(rng, out, wet=0.12), f"paddle{i + 1}", -31.0)


# =============================================================================
# Under the water
# =============================================================================

def snd_l227water_under_bed() -> None:
    """The muffled hush of a forest pool around your ears: a low, soft rumble of water, the
    forest above reduced to a faint dull swell, and now and then a slow, deep bubble rising.
    Seamless: every part is periodic over the loop (periodic noise, circular placement)."""
    rng = np.random.default_rng(22714)
    T = 24.0
    n = int(T * SR)
    rumble = G.periodic_noise(n, rng, lambda f: G.lp_gain(f, 170, 2) * G.hp_gain(f, 28, 1))
    hush = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 140, 650, 2)) * G.drift(n, rng, 0.35, 0.05, 0.2)
    buf = rumble * 0.9 + hush * 0.3
    bubbles = np.zeros(n)
    for _ in range(26):
        b = _bubble(rng.uniform(170, 480), 0.35, rise=rng.uniform(1.15, 1.45), decay=rng.uniform(0.04, 0.08))
        G.add_at(bubbles, b * rng.uniform(0.25, 0.7), rng.uniform(0.0, T), circular=True)
    buf += G.fft_filter(bubbles, lambda f: G.lp_gain(f, 900, 2), circular=True) * 0.9
    _save(buf, "under_bed", -33.0, loop=True)


def snd_l227water_under_enter() -> None:
    """Ducking under: the air's open sound closing into the muffle in a quarter of a second, a
    soft low push, and the bubbles you took down with you rising past your ears."""
    rng = np.random.default_rng(22715)
    for i in range(2):
        dur = 1.3
        n = int(dur * SR)
        t = np.arange(n) / SR
        rush = _dull_to_bright(rng, n, 400, 3500, [(0, 1), (0.05, 0.9), (0.28, 0.0), (dur, 0.0)])
        out = G.norm(rush) * _env(n, [(0, 0), (0.02, 1), (0.3, 0.45), (dur, 0)]) * 0.6
        out += np.sin(2 * np.pi * 68 * t) * np.exp(-t / 0.09) * 0.35
        for _ in range(9 + i * 3):
            b = _bubble(rng.uniform(280, 820), 0.2, rise=rng.uniform(1.3, 1.9), decay=rng.uniform(0.02, 0.045))
            G.add_at(out, b * rng.uniform(0.15, 0.45), 0.08 + rng.gamma(1.4, 0.12), circular=False)
        _save(G.fft_filter(out, lambda f: G.lp_gain(f, 5000, 1)), f"under_enter{i + 1}", -26.0, fade_out=0.2)


def snd_l227water_under_exit() -> None:
    """Coming up: the muffle opening back into air, water sheeting off your head, a few drips."""
    rng = np.random.default_rng(22716)
    for i in range(2):
        dur = 1.15
        n = int(dur * SR)
        sheet = _dull_to_bright(rng, n, 900, 5500, [(0, 0), (0.06, 0.2), (0.25, 1.0), (dur, 1.0)])
        out = G.norm(sheet) * _env(n, [(0, 0), (0.03, 0.8), (0.35, 0.25), (dur, 0)]) * 0.5
        for _ in range(5 + i):
            tt = rng.uniform(0.25, 1.0)
            b = _bubble(rng.uniform(1700, 3100), 0.05, rise=rng.uniform(1.2, 1.6), decay=rng.uniform(0.006, 0.014))
            G.add_at(out, b * rng.uniform(0.12, 0.3), tt, circular=False)
        _save(_room(rng, out, wet=0.15), f"under_exit{i + 1}", -28.0, fade_out=0.15)


# =============================================================================
# Particle textures (white: the game tints them the water's colour)
# =============================================================================

def _png(rgba: np.ndarray, name: str) -> None:
    TEX_DIR.mkdir(parents=True, exist_ok=True)
    arr = np.clip(np.round(rgba), 0, 255).astype(np.uint8)
    Image.fromarray(arr, "RGBA").save(TEX_DIR / f"{name}.png")


def _grid(size: int):
    c = (size - 1) / 2.0
    y, x = np.mgrid[0:size, 0:size].astype(float)
    return x - c, y - c


def tex_l227water_droplets() -> None:
    """A drop in flight: a soft, slightly tall blob with a bright glint up and to the left."""
    rng = np.random.default_rng(22720)
    for i in range(3):
        x, y = _grid(8)
        sx, sy = rng.uniform(1.7, 2.1), rng.uniform(2.1, 2.7)
        body = np.exp(-((x / sx) ** 2 + (y / sy) ** 2) ** 2)
        glint = np.exp(-(((x + 0.9) ** 2 + (y + 1.1) ** 2) / 0.6))
        alpha = np.clip(body * 0.85 + glint * 0.3, 0, 1)
        shade = np.clip(0.78 + 0.22 * glint - 0.1 * (y / sy), 0, 1)
        rgba = np.stack([shade * 255, shade * 255, shade * 255, alpha * 255], axis=-1)
        _png(rgba, f"l227_water_droplet_{i}")


def tex_l227water_ring() -> None:
    """A ripple seen from above: one bright crest and a fainter one inside it."""
    x, y = _grid(32)
    r = np.hypot(x, y)
    alpha = np.exp(-((r - 13.2) / 1.1) ** 2) + 0.4 * np.exp(-((r - 9.0) / 0.9) ** 2)
    alpha = np.clip(alpha, 0, 1) * (r < 15.5)
    rgba = np.stack([np.full_like(r, 255), np.full_like(r, 255), np.full_like(r, 255), alpha * 230], axis=-1)
    _png(rgba, "l227_water_ring_0")


def tex_l227water_foam() -> None:
    """A patch of foam: clustered bubbles with holes between them, thinning toward the edge."""
    rng = np.random.default_rng(22721)
    for i in range(3):
        x, y = _grid(32)
        field = np.zeros_like(x)
        for _ in range(90):
            a = rng.uniform(0, 2 * np.pi)
            d = abs(rng.normal(0, 5.5))
            cx, cy = np.cos(a) * d, np.sin(a) * d
            rad = rng.uniform(0.9, 2.4)
            field = np.maximum(field, np.exp(-(((x - cx) ** 2 + (y - cy) ** 2) / (rad * rad)) ** 2) * rng.uniform(0.6, 1.0))
        holes = np.zeros_like(x)
        for _ in range(18):
            cx, cy = rng.normal(0, 5.0, 2)
            rad = rng.uniform(0.7, 1.6)
            holes = np.maximum(holes, np.exp(-((x - cx) ** 2 + (y - cy) ** 2) / (rad * rad)))
        edge = np.clip(1.0 - np.hypot(x, y) / 15.5, 0, 1) ** 0.6
        alpha = np.clip(field * (1.0 - 0.85 * holes) * edge * 1.1, 0, 0.95)
        shade = 0.88 + 0.12 * field
        rgba = np.stack([shade * 255, shade * 255, shade * 250, alpha * 255], axis=-1)
        _png(rgba, f"l227_water_foam_{i}")


def tex_l227water_bubbles() -> None:
    """A bubble under water: a thin bright rim, a clear middle and a glint."""
    for i, rim in enumerate((2.6, 2.9)):
        x, y = _grid(8)
        r = np.hypot(x, y)
        alpha = np.exp(-((r - rim) / 0.7) ** 2) * 0.85 + 0.12 * (r < rim)
        glint = np.exp(-(((x + 1.0) ** 2 + (y + 1.0) ** 2) / 0.45))
        alpha = np.clip(alpha + glint * 0.6, 0, 1)
        rgba = np.stack([np.full_like(r, 255), np.full_like(r, 255), np.full_like(r, 255), alpha * 255], axis=-1)
        _png(rgba, f"l227_water_bubble_{i}")


GENERATORS = [
    snd_l227water_splashes,
    snd_l227water_paddle,
    snd_l227water_under_bed,
    snd_l227water_under_enter,
    snd_l227water_under_exit,
    tex_l227water_droplets,
    tex_l227water_ring,
    tex_l227water_foam,
    tex_l227water_bubbles,
]


def main() -> None:
    filters = sys.argv[1:]
    for gen in GENERATORS:
        if filters and not any(f in gen.__name__ for f in filters):
            continue
        print(gen.__name__)
        gen()


if __name__ == "__main__":
    main()
