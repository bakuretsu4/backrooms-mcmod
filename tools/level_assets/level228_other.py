"""Level 228 ("Maze of Mirrors"): the Other, the thing that lives in the reflections.

All made here from noise and sine partials (no recordings), so CC0 like the rest of our own
generators. Sounds (client/level228/Level228Other):
  other_step_1..4  its slow bare footstep on the polished floor, heard from the mirror: a soft
                   heel thump and skin on stone, with a cold glassy ring and a short bright tail
                   (it is behind glass)
  other_tick_1..3  glass under stress ticking when it is near: one to three tiny high clicks
  other_breath     the held breath before a catch: a sharp intake, cut dead, then a faint
                   pressure that fades into nothing
  other_crack      the glass giving way as it takes you: a hard snap, a crackle running out
                   across the pane, a low thud and a long shimmering ring
Loudness targets (LUFS): step -24, tick -27, breath -22, crack -15.

Textures:
  entity/l228_other.png  16x16 pale, faintly mottled skin with cold veins (the figure's boxes)
  misc/l228_crack.png    512x512 transparent: cracks radiating from the middle of the screen
                         with rings between them (the catch's crack, drawn over the picture)

Run only these with:  python tools/gen_assets.py level228_other
"""
from __future__ import annotations

import numpy as np

import gen_assets as G

SR = G.SR
OUT = G.SND / "level228"


def _lufs(x: np.ndarray) -> float:
    """Short-sound loudness, close to BS.1770 (K-weighting approximated by a 100 Hz high-pass and
    a +4 dB lift above 1.5 kHz; blocks quieter than -70 or 10 LU under the mean are gated out).
    Done here in numpy so it needs no external tools."""
    k = G.fft_filter(x, lambda f: G.hp_gain(f, 100.0, 2) * (1.0 + 0.585 * G.hp_gain(f, 1500.0, 2)))
    block = int(0.4 * SR)
    hop = block // 4
    powers = np.array([np.mean(k[i:i + block] ** 2) for i in range(0, max(1, len(k) - block + 1), hop)])
    powers = powers[powers > 10 ** (-70 / 10)]
    if powers.size == 0:
        return -70.0
    gate = np.mean(powers) * 10 ** (-10 / 10)
    kept = powers[powers > gate]
    return float(-0.691 + 10 * np.log10(np.mean(kept if kept.size else powers)))


def _save(x: np.ndarray, name: str, target: float) -> None:
    """Set to `target` LUFS (peaks held under 0.89 by a soft knee) and write; save_ogg normalises
    to its peak, so pass it the peak we have."""
    y = np.asarray(x, float) * 10 ** ((target - _lufs(x)) / 20)
    knee = 0.7
    over = np.abs(y) > knee
    if over.any():
        y = np.where(over, np.sign(y) * (knee + (0.89 - knee) * np.tanh((np.abs(y) - knee) / (0.89 - knee))), y)
    G.save_ogg(y, OUT / f"{name}.ogg", peak=float(min(0.89, np.abs(y).max())), fade_out=0.05)


def _noise(rng, n: int) -> np.ndarray:
    return rng.normal(size=n)


def _band(x: np.ndarray, lo: float, hi: float, order: int = 2) -> np.ndarray:
    return G.fft_filter(x, lambda f: G.bp_gain(f, lo, hi, order))


def _glass_ring(rng, dur: float, base: float, decay: float) -> np.ndarray:
    """Inharmonic partials of a pane of glass (thin plate: partials spread unevenly upward)."""
    ratios = np.array([1.0, 2.32, 3.87, 5.51, 7.9])
    jitter = rng.uniform(0.97, 1.03, ratios.size)
    amps = np.array([1.0, 0.7, 0.45, 0.3, 0.18])
    decays = decay / np.sqrt(ratios)
    return G.ring(base * ratios * jitter, decays, amps, dur)


def snd_level228_other_step() -> None:
    for k in range(4):
        rng = np.random.default_rng(22800 + k)
        dur = 0.9
        n = int(dur * SR)
        t = np.arange(n) / SR
        x = np.zeros(n)
        # Heel: a soft low thump (bare, no shoe click).
        thump = _band(_noise(rng, n), 50, 380) * np.exp(-t / 0.035)
        thump += np.sin(2 * np.pi * rng.uniform(70, 90) * t) * np.exp(-t / 0.05) * 0.6
        x += thump
        # Skin on polished stone: a short damp slap, a little after the heel.
        slap = _band(_noise(rng, n), 700, 3200) * np.exp(-np.maximum(t - 0.018, 0) / 0.012) * (t > 0.018)
        x += slap * 0.35
        # From behind the glass: a cold ring of the pane and a bright, short tail.
        ring = _glass_ring(rng, dur, rng.uniform(1700, 2100), 0.16)
        x += ring * 0.05
        ir = G.reverb_ir(rng, 0.7, 0.16, lp=7000.0, predelay=0.012, early=((0.007, 0.5), (0.019, 0.35)))
        wet = G.conv(_band(x, 250, 8000), ir)[:n]
        x = x * 0.75 + wet * 0.55
        _save(x, f"other_step_{k + 1}", -24)


def snd_level228_other_tick() -> None:
    for k in range(3):
        rng = np.random.default_rng(22810 + k)
        dur = 0.6
        n = int(dur * SR)
        x = np.zeros(n)
        clicks = rng.integers(1, 4)
        for c in range(clicks):
            at = 0.01 + c * rng.uniform(0.06, 0.16)
            tick = _glass_ring(rng, 0.12, rng.uniform(3200, 5200), rng.uniform(0.006, 0.018))
            tick[:24] += _noise(rng, 24) * 0.5
            G.add_at(x, tick * rng.uniform(0.5, 1.0), at, circular=False)
        ir = G.reverb_ir(rng, 0.4, 0.08, lp=9000.0, predelay=0.004)
        x = x + G.conv(x, ir)[:n] * 0.3
        _save(_band(x, 1500, 14000), f"other_tick_{k + 1}", -27)


def snd_level228_other_breath() -> None:
    rng = np.random.default_rng(22820)
    dur = 2.4
    n = int(dur * SR)
    t = np.arange(n) / SR
    # The intake: breath noise rising through the mouth's formants, then cut dead at 0.42 s.
    inhale_end = 0.42
    env = np.clip(t / inhale_end, 0, 1) ** 1.6 * (t < inhale_end)
    breath = _noise(rng, n)
    sweep = 900 + 1400 * np.clip(t / inhale_end, 0, 1)
    formed = G.fft_filter(breath, lambda f: G.bp_gain(f, 400, 5000, 2)) * 0.5
    m = int(0.5 * SR)
    formed[:m] += G.svf(breath[:m], sweep[:m], 3.0)
    x = formed * env
    # A tiny catch in the throat where it stops.
    G.add_at(x, _band(_noise(rng, 600), 300, 2000) * np.exp(-np.arange(600) / 90.0) * 0.6, inhale_end - 0.01, circular=False)
    # Then the pressure of the silence: a very faint low tone, fading out.
    after = t >= inhale_end
    hum = np.sin(2 * np.pi * 38 * t) * 0.03 * np.exp(-(t - inhale_end) / 0.7) * after
    x += hum
    _save(x, "other_breath", -22)


def snd_level228_other_crack() -> None:
    rng = np.random.default_rng(22830)
    dur = 3.2
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    # The snap: a broadband transient with a hard edge.
    snap = _noise(rng, int(0.03 * SR)) * np.exp(-np.arange(int(0.03 * SR)) / (0.004 * SR))
    G.add_at(x, snap * 1.5, 0.0, circular=False)
    # The crack running out across the pane: clicks thick at first, thinning out.
    times = np.cumsum(rng.exponential(0.012, 70) * np.linspace(0.5, 3.0, 70))
    for at in times[times < 0.9]:
        c = _glass_ring(rng, 0.08, rng.uniform(2500, 7000), rng.uniform(0.004, 0.012))
        c[:16] += _noise(rng, 16)
        G.add_at(x, c * rng.uniform(0.2, 0.8) * np.exp(-at / 0.5), 0.004 + at, circular=False)
    # The low thud of the whole pane.
    x += np.sin(2 * np.pi * 62 * t) * np.exp(-t / 0.12) * 0.6
    x += _band(_noise(rng, n), 40, 300) * np.exp(-t / 0.08) * 0.5
    # The long shimmering ring of the glass.
    shimmer = _glass_ring(rng, dur, 1230, 1.1) * 0.12 + _glass_ring(rng, dur, 1870, 0.8) * 0.08
    x += shimmer * (1 + 0.3 * np.sin(2 * np.pi * 5.5 * t))
    ir = G.reverb_ir(rng, 2.0, 0.5, lp=8000.0, predelay=0.01, early=((0.009, 0.4), (0.023, 0.3)))
    x = x + G.conv(x, ir)[:n] * 0.4
    _save(x, "other_crack", -15)


def tex_level228_other_skin() -> None:
    """16x16: pale grey-white skin, faintly mottled, a few cold blue-grey veins."""
    rng = np.random.default_rng(22840)
    base = np.array([222.0, 219.0, 212.0])
    img = np.zeros((16, 16, 4))
    mottle = rng.normal(0, 5.0, (16, 16))
    img[..., :3] = base + mottle[..., None]
    for _ in range(3):
        x, y = rng.integers(0, 16, 2)
        for _ in range(9):
            img[y % 16, x % 16, :3] = (182, 188, 196)
            x += rng.integers(-1, 2)
            y += 1
    img[..., 3] = 255
    G.save_png(np.clip(img, 0, 255), G.TEX / "entity" / "l228_other.png")


def _stroke(alpha: np.ndarray, x0: float, y0: float, x1: float, y1: float, width: float, strength: float) -> None:
    """Max-blends an anti-aliased line of `width` px into `alpha`."""
    s = alpha.shape[0]
    pad = width + 2
    lx, hx = int(max(0, min(x0, x1) - pad)), int(min(s, max(x0, x1) + pad + 1))
    ly, hy = int(max(0, min(y0, y1) - pad)), int(min(s, max(y0, y1) + pad + 1))
    if lx >= hx or ly >= hy:
        return
    yy, xx = np.mgrid[ly:hy, lx:hx] + 0.5
    dx, dy = x1 - x0, y1 - y0
    ll = dx * dx + dy * dy + 1e-9
    u = np.clip(((xx - x0) * dx + (yy - y0) * dy) / ll, 0, 1)
    d = np.hypot(xx - (x0 + u * dx), yy - (y0 + u * dy))
    a = np.clip(width * 0.5 + 0.5 - d, 0, 1) * strength
    alpha[ly:hy, lx:hx] = np.maximum(alpha[ly:hy, lx:hx], a)


def tex_level228_other_crack() -> None:
    """512x512 RGBA: a pane cracked from a point a little off the middle. Bright hairlines with a
    darker edge (so they read over both the dark maze and the white-out), radial cracks that wander
    and fork, and broken rings between them."""
    s = 512
    rng = np.random.default_rng(22850)
    core = np.zeros((s, s))
    edge = np.zeros((s, s))
    cx, cy = s * 0.53, s * 0.47
    angles = np.sort(rng.uniform(0, 2 * np.pi, 15))
    for a in angles:
        x, y, ang, w = cx, cy, a, 2.4
        while 0 <= x < s and 0 <= y < s and w > 0.4:
            step = rng.uniform(10, 26)
            ang += rng.normal(0, 0.18)
            nx, ny = x + np.cos(ang) * step, y + np.sin(ang) * step
            _stroke(edge, x, y, nx, ny, w + 2.5, 0.45)
            _stroke(core, x, y, nx, ny, w, 0.95)
            if rng.random() < 0.18:
                bx, by, bang = nx, ny, ang + rng.choice([-1, 1]) * rng.uniform(0.4, 0.9)
                for _ in range(rng.integers(2, 6)):
                    b2x, b2y = bx + np.cos(bang) * rng.uniform(8, 18), by + np.sin(bang) * rng.uniform(8, 18)
                    _stroke(edge, bx, by, b2x, b2y, w * 0.6 + 2.0, 0.4)
                    _stroke(core, bx, by, b2x, b2y, w * 0.6, 0.85)
                    bx, by, bang = b2x, b2y, bang + rng.normal(0, 0.25)
            x, y = nx, ny
            w *= 0.985
    # Rings: arcs between neighbouring radial cracks at a few radii.
    for r in (26, 58, 104, 170):
        for i in range(len(angles)):
            if rng.random() < 0.35:
                continue
            a0, a1 = angles[i], angles[(i + 1) % len(angles)] + (2 * np.pi if i == len(angles) - 1 else 0)
            rr = r * rng.uniform(0.85, 1.15)
            steps = max(2, int((a1 - a0) * rr / 10))
            pts = [(cx + np.cos(a) * rr * rng.uniform(0.96, 1.04), cy + np.sin(a) * rr * rng.uniform(0.96, 1.04))
                   for a in np.linspace(a0, a1, steps)]
            for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
                _stroke(edge, x0, y0, x1, y1, 3.6, 0.4)
                _stroke(core, x0, y0, x1, y1, 1.3, 0.85)
    # The impact: a small crushed star.
    for _ in range(40):
        a = rng.uniform(0, 2 * np.pi)
        l = rng.uniform(3, 14)
        _stroke(core, cx, cy, cx + np.cos(a) * l, cy + np.sin(a) * l, 1.6, 1.0)
    img = np.zeros((s, s, 4))
    a = np.maximum(core, edge)
    shade = np.where(core >= edge, 245.0, 30.0)
    img[..., 0] = shade
    img[..., 1] = shade + np.where(core >= edge, 3.0, 2.0)
    img[..., 2] = shade + np.where(core >= edge, 8.0, 6.0)
    img[..., 3] = a * 255.0
    G.save_png(np.clip(img, 0, 255), G.TEX / "misc" / "l228_crack.png")


TEXTURES = [tex_level228_other_skin, tex_level228_other_crack]
SOUNDS = [snd_level228_other_step, snd_level228_other_tick, snd_level228_other_breath, snd_level228_other_crack]
