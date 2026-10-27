"""Hiding in furniture: the sounds of getting in and out, and the gasp after holding your breath.

Played by src/main/java/.../player/HidingService.java (registered in registry/HidingAudio.java):

  hide/locker_in1..2     a metal locker door creaking shut on its hinge, then the latch
  hide/locker_out1..2    the latch, then the door creaking open
  hide/wardrobe_in1..2   a wooden wardrobe door: a lower, drier creak and a soft wooden knock
  hide/wardrobe_out1..2  the knock of the door coming free, then the creak
  hide/shuffle_in1..2    crawling under a desk or a bed: clothes scuffing over the floor, a knee
  hide/shuffle_out1..2   crawling back out
  hide/gasp1..2          letting a held breath go: cut from the same CC0 breathing recordings
                         as your breathing loops (tools/level_assets/recorded.py)

Quiet on purpose (README "Mix"): the creaks sit at -24 LUFS, the shuffles at -27, the gasp at
-23, all well under the -18 ceiling. Other players nearby hear them too, so they must sound like
furniture and a person, not like an alarm.

Run only these with:  python tools/gen_assets.py snd_hide_
Every generator uses its own fixed random seed (20700-20799), so re-running gives identical files.
"""
from __future__ import annotations

import numpy as np

import gen_assets as G
from anomaly_assets import level2_audio as L2

SR = G.SR


def _save(x, name: str, target: float, fade_in: float = 0.003, fade_out: float = 0.06) -> None:
    """Brings `x` to `target` LUFS (no peak normalising) and writes hide/<name>.ogg."""
    import soundfile as sf
    x = np.asarray(x, float)
    x = x - x.mean()
    n = len(x)
    fi, fo = max(1, int(fade_in * SR)), max(1, int(fade_out * SR))
    x[:fi] *= np.linspace(0, 1, fi)
    x[n - fo:] *= np.linspace(1, 0, fo)
    x = L2.loud(x, target, peak=0.85)
    path = G.SND / "hide" / f"{name}.ogg"
    path.parent.mkdir(parents=True, exist_ok=True)
    with sf.SoundFile(str(path), "w", SR, 1, format="OGG", subtype="VORBIS") as out:
        y = x.astype(np.float32)
        for i in range(0, len(y), SR):
            out.write(y[i:i + SR])


# =============================================================================
# Building blocks
# =============================================================================

def _ring(rng, freqs, decays, amps, dur) -> np.ndarray:
    """The impulse response of a resonant body: a few decaying sine modes."""
    t = G.t_axis(dur)
    out = np.zeros_like(t)
    for f, d, a in zip(freqs, decays, amps):
        out += a * np.sin(2 * np.pi * f * rng.uniform(0.98, 1.02) * t + rng.uniform(0, 2 * np.pi)) * np.exp(-t / d)
    return out


def _creak(rng, dur: float, rate0: float, rate1: float, modes, decays, amps, grit: float) -> np.ndarray:
    """A hinge creaking: stick-slip friction, a train of tiny jolts whose rate slides from
    `rate0` to `rate1` Hz (the door speeding up and slowing down), each jolt ringing the hinge
    and the door panel. `grit` mixes in rough friction noise between the jolts."""
    n = int(dur * SR)
    x = np.zeros(n)
    ir = _ring(rng, modes, decays, amps, 0.12)
    t, i = 0.0, 0
    while t < dur:
        u = t / dur
        # Faster in the middle of the swing, slower at both ends, a little unsteady.
        rate = rate0 + (rate1 - rate0) * np.sin(np.pi * u) ** 0.7
        rate *= rng.uniform(0.85, 1.15)
        k = int(t * SR)
        amp = (0.35 + 0.65 * np.sin(np.pi * u) ** 0.5) * rng.uniform(0.6, 1.0)
        end = min(n, k + len(ir))
        x[k:end] += amp * ir[:end - k]
        t += 1.0 / max(rate, 5.0)
        i += 1
    noise = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 800, 5000, 2))[:n]
    env = G.smoothstep(0.0, 0.05, G.t_axis(dur)) * G.smoothstep(dur, dur - 0.08, G.t_axis(dur))
    return G.norm(x) * env + grit * G.norm(noise) * env


def _latch(rng, metal: bool) -> np.ndarray:
    """The door meeting its frame: a click and, for a metal door, a short tinny ring; for a wooden
    one, a soft knock."""
    dur = 0.35
    t = G.t_axis(dur)
    n = len(t)
    click = G.fft_filter(rng.normal(size=n) * np.exp(-t / 0.003), lambda f: G.bp_gain(f, 1500, 9000, 1))[:n]
    if metal:
        body = _ring(rng, [420, 980, 1730, 2650], [0.05, 0.04, 0.03, 0.02], [1.0, 0.6, 0.4, 0.25], dur)
        thud = np.sin(2 * np.pi * 120 * t) * np.exp(-t / 0.03)
        return 0.6 * G.norm(click) + 0.5 * G.norm(body) + 0.4 * thud
    body = _ring(rng, [190, 340, 610], [0.03, 0.02, 0.015], [1.0, 0.5, 0.3], dur)
    thud = np.sin(2 * np.pi * 95 * t) * np.exp(-t / 0.04)
    return 0.35 * G.norm(click) + 0.6 * G.norm(body) + 0.6 * thud


def _join(*parts) -> np.ndarray:
    """Plays the parts one after another; each part is (signal, gap before it in seconds)."""
    total = sum(len(p) + int(g * SR) for p, g in parts)
    out = np.zeros(total + SR // 4)
    pos = 0
    for p, g in parts:
        pos += int(g * SR)
        out[pos:pos + len(p)] += p
        pos += len(p)
    return out


def _metal_creak(rng, dur: float) -> np.ndarray:
    return _creak(rng, dur, rng.uniform(18, 26), rng.uniform(70, 110),
                  [rng.uniform(850, 1000), rng.uniform(1650, 1850), rng.uniform(2500, 2800), 3900],
                  [0.03, 0.022, 0.016, 0.01], [1.0, 0.7, 0.45, 0.2], grit=0.08)


def _wood_creak(rng, dur: float) -> np.ndarray:
    return _creak(rng, dur, rng.uniform(14, 20), rng.uniform(45, 70),
                  [rng.uniform(330, 400), rng.uniform(600, 700), rng.uniform(1050, 1200), 1800],
                  [0.02, 0.014, 0.01, 0.007], [1.0, 0.6, 0.35, 0.15], grit=0.18)


def _scuffs(rng, dur: float, count: int, rising: bool) -> np.ndarray:
    """Clothes and shoes scuffing over a floor, and a knee or elbow touching down."""
    n = int(dur * SR)
    t = G.t_axis(dur)
    cloth = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 350, 3200, 2))[:n]
    env = np.zeros(n)
    starts = np.sort(rng.uniform(0.02, dur - 0.25, count))
    for k, s in enumerate(starts):
        length = rng.uniform(0.12, 0.28)
        w = G.smoothstep(s, s + 0.03, t) * G.smoothstep(s + length, s + length * 0.5, t)
        weight = (0.5 + 0.5 * k / max(1, count - 1)) if rising else (1.0 - 0.5 * k / max(1, count - 1))
        env = np.maximum(env, w * weight * rng.uniform(0.7, 1.0))
    x = G.norm(cloth) * env
    for s in starts[:2]:
        tt = G.t_axis(0.12)
        knock = np.sin(2 * np.pi * rng.uniform(90, 130) * tt) * np.exp(-tt / 0.025)
        G.add_at(x, 0.35 * knock, float(s) + 0.02, circular=False)
    return x


# =============================================================================
# Generators
# =============================================================================

def snd_hide_locker() -> None:
    for i, seed in enumerate((20700, 20701), start=1):
        rng = G.rng_for(seed)
        creak = _metal_creak(rng, rng.uniform(0.55, 0.75))
        _save(_join((creak, 0.0), (_latch(rng, True), 0.02)), f"locker_in{i}", -24.0)
        rng = G.rng_for(seed + 10)
        creak = _metal_creak(rng, rng.uniform(0.6, 0.85))
        _save(_join((_latch(rng, True) * 0.7, 0.0), (creak, 0.05)), f"locker_out{i}", -24.0)


def snd_hide_wardrobe() -> None:
    for i, seed in enumerate((20720, 20721), start=1):
        rng = G.rng_for(seed)
        creak = _wood_creak(rng, rng.uniform(0.6, 0.85))
        _save(_join((creak, 0.0), (_latch(rng, False), 0.03)), f"wardrobe_in{i}", -24.0)
        rng = G.rng_for(seed + 10)
        creak = _wood_creak(rng, rng.uniform(0.7, 0.95))
        _save(_join((_latch(rng, False) * 0.6, 0.0), (creak, 0.04)), f"wardrobe_out{i}", -24.0)


def snd_hide_shuffle() -> None:
    for i, seed in enumerate((20740, 20741), start=1):
        rng = G.rng_for(seed)
        _save(_scuffs(rng, rng.uniform(0.9, 1.2), 4, rising=False), f"shuffle_in{i}", -27.0)
        rng = G.rng_for(seed + 10)
        _save(_scuffs(rng, rng.uniform(0.8, 1.1), 3, rising=True), f"shuffle_out{i}", -27.0)


def _loudest_breath(x: np.ndarray, avoid: list[float], length: float) -> float:
    """Start time of the loudest `length`-second stretch not overlapping any start in `avoid`."""
    win = int(0.05 * SR)
    env = np.sqrt(np.convolve(x ** 2, np.ones(win) / win, "same"))
    span = int(length * SR)
    csum = np.concatenate([[0.0], np.cumsum(env)])
    score = csum[span:] - csum[:-span]
    for a in avoid:
        lo, hi = max(0, int((a - length) * SR)), int((a + length) * SR)
        score[lo:hi] = -1.0
    return float(np.argmax(score)) / SR


def snd_hide_gasp() -> None:
    """A big breath let out and dragged back in, from the heavy-breathing recording (take 1) and
    the scared-breathing one (take 2), starting and ending in the quiet between breaths."""
    from level_assets import recorded as R
    picks = [("757113_heavy_breathing", 1.1), ("554307_scared_breathing", 1.2)]
    for i, (name, length) in enumerate(picks, start=1):
        x = R._clean(R._rec(name), lo=90, hi=11000)
        start = _loudest_breath(x[: len(x) - int(2 * SR)], [], length)
        a = R._quiet_near(x, max(0.6, start), 0.25)
        b = R._quiet_near(x, a / SR + length, 0.3)
        seg = x[a:b].copy()
        # A touch closer and drier than the loops: you're shut in with it.
        seg = G.fft_filter(seg, lambda f: 1.0 + 0.4 * G.bp_gain(f, 200, 900, 1))[: len(seg)]
        _save(seg, f"gasp{i}", -23.0, fade_in=0.01, fade_out=0.12)


TEXTURES: list = []
SOUNDS = [snd_hide_locker, snd_hide_wardrobe, snd_hide_shuffle, snd_hide_gasp]
