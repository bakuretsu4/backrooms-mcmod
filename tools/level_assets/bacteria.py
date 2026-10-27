"""The Bacteria's sounds: quiet, close and wrong, never a shriek.

Built from the two breathing recordings (CC0, tools/vendor/freesound): slowed right down so the
breath becomes something too big for its throat, sometimes played backwards so it swells instead
of fading, with soft wet clicks (a mouth that doesn't close properly) and, under the worst of it,
a faint tone beating against itself like ringing ears. Everything sits in a small dead room, low.

| Sound   | When                                   | Loudness (LUFS) |
|---------|----------------------------------------|-----------------|
| ambient | now and then while it wanders          | -34             |
| search  | every 2 s while it hunts for you       | -36             |
| screech | it has seen you (a long backwards inhale and a rattle, not a scream) | -27 |
| step    | each footstep                          | -38             |
| hurt    | it is hit                              | -31             |
| death   | it dies                                | -30             |
| lost    | it gives up (you made it)              | -31             |

Run only these with:  python tools/gen_assets.py bacteria_
"""
from __future__ import annotations

import numpy as np

import gen_assets as G
from level_assets import recorded as rec

SR = G.SR
HEAVY = "757113_heavy_breathing"
SCARED = "554307_scared_breathing"


def _breaths(name: str) -> list[np.ndarray]:
    """Every breath in a recording, as its own clip (from one quiet gap to the next)."""
    x = rec._clean(rec._rec(name), lo=60, hi=11000)
    onsets = rec._onsets(x, jump_db=7.0, gap=0.35)
    clips = []
    for a, b in zip(onsets, onsets[1:]):
        if 0.25 < b - a < 1.6:
            clips.append(x[max(0, int((a - 0.03) * SR)): int((b - 0.05) * SR)])
    assert len(clips) >= 6, (name, len(clips))
    return clips


def _slow(x: np.ndarray, rate: float) -> np.ndarray:
    """Play slower (rate < 1): lower and longer, like a tape running down."""
    return rec._resample_varying(x, np.full(int(len(x) / rate), rate))


def _clicks(rng, n: int, times) -> np.ndarray:
    """Soft wet clicks: a tiny noise burst into one or two low, damp resonances."""
    out = np.zeros(n)
    for t0 in times:
        k = int(0.06 * SR)
        tt = np.arange(k) / SR
        burst = rng.normal(size=k) * np.exp(-tt / 0.0015)
        burst = G.fft_filter(burst, lambda f: G.bp_gain(f, 500, 2600, 2))
        body = sum(np.sin(2 * np.pi * f * tt) * np.exp(-tt / d)
                   for f, d in ((rng.uniform(280, 520), 0.018), (rng.uniform(900, 1400), 0.008)))
        G.add_at(out, (0.6 * G.norm(burst) + 0.4 * G.norm(body)) * rng.uniform(0.5, 1.0), t0, circular=False)
    return out


def _whine(n: int, f0: float, f1: float, level: float) -> np.ndarray:
    """A faint tone beating against a copy of itself 3 Hz apart, gliding from f0 to f1."""
    t = np.arange(n) / SR
    f = np.linspace(f0, f1, n)
    ph = 2 * np.pi * np.cumsum(f) / SR
    tone = np.sin(ph) + np.sin(ph + 2 * np.pi * 3.0 * t)
    env = G.smoothstep(0, 0.4, t) * G.smoothstep(n / SR, n / SR - 0.5, t)
    return tone * env * level


def _room(rng, x: np.ndarray, wet: float = 0.3) -> np.ndarray:
    """A small, dead room: short and dark, so it sounds close and wrong, not like an echoing hall."""
    ir = G.reverb_ir(rng, 0.7, 0.18, lp=2500, predelay=0.01)
    return x + wet * G.conv(x, ir)[:len(x)] / (np.abs(G.conv(x, ir)[:len(x)]).max() + 1e-9) * np.abs(x).max()


def _darken(x: np.ndarray, hz: float) -> np.ndarray:
    return G.fft_filter(x, lambda f: G.lp_gain(f, hz, 2))


def _fit(x: np.ndarray, seconds: float) -> np.ndarray:
    n = int(seconds * SR)
    return np.pad(x, (0, max(0, n - len(x))))[:n]


def _save(x, name: str, target: float, fade_out: float = 0.08) -> None:
    rec._save(rec.loud(x, target, peak=0.6), G.SND / "entity" / f"{name}.ogg", fade_out=fade_out)


# ---------------------------------------------------------------------------------------------

def snd_bacteria_ambient() -> None:
    """Three takes: a slow, too-deep exhale with a few wet clicks in it."""
    heavy = _breaths(HEAVY)
    for i in range(1, 4):
        rng = G.rng_for(2460 + i)
        dur = 2.2
        breath = _darken(_slow(heavy[(i * 3) % len(heavy)], rng.uniform(0.5, 0.6)), 2200)
        x = _fit(breath, dur) * np.exp(-np.arange(int(dur * SR)) / SR / 1.6)
        clicks = _clicks(rng, len(x), np.sort(rng.uniform(0.2, 1.6, 2 + i)))
        x = G.norm(x) + 0.35 * G.norm(clicks)
        if i == 3:
            x += _whine(len(x), 610, 585, 0.05)
        _save(_room(rng, x), f"bacteria_ambient{i}", -34.0)


def snd_bacteria_search() -> None:
    """Two takes: short slow sniffs, three or four, and a click: it is close, looking."""
    scared = _breaths(SCARED)
    for i in range(1, 3):
        rng = G.rng_for(2600 + i)
        dur = 1.6
        x = np.zeros(int(dur * SR))
        t = 0.05
        for k in range(3 + i % 2):
            sniff = _slow(scared[(i * 5 + k) % len(scared)][:int(0.22 * SR)], 0.72)
            sniff = sniff * G.env_adsr(len(sniff), 0.01, 0.06)
            G.add_at(x, sniff * rng.uniform(0.6, 1.0), t, circular=False)
            t += rng.uniform(0.24, 0.32)
        x = G.norm(_darken(x, 3000)) + 0.3 * G.norm(_clicks(rng, len(x), [t + 0.08]))
        _save(_room(rng, x), f"bacteria_search{i}", -36.0)


def snd_bacteria_screech() -> None:
    """It has seen you: a long inhale played backwards and slowed (it swells instead of fading),
    a beating tone rising a little under it, then a short rattle of clicks. Two takes."""
    scared = _breaths(SCARED)
    longest = sorted(scared, key=len)[-4:]
    for i in range(1, 3):
        rng = G.rng_for(2525 + i)
        dur = 2.6
        swell = _slow(longest[i][::-1], 0.5)
        swell = _darken(swell, 2600)
        n = int(dur * SR)
        x = np.zeros(n)
        G.add_at(x, G.norm(swell), 0.05, circular=False)
        end = min(0.05 + len(swell) / SR, dur - 0.6)
        rattle = _clicks(rng, n, end + np.cumsum(rng.uniform(0.025, 0.05, 10)))
        rattle *= np.exp(-np.clip(np.arange(n) / SR - end, 0, None) / 0.25)
        x = G.norm(x) + 0.45 * G.norm(rattle) + _whine(n, 520 + 40 * i, 580 + 40 * i, 0.06)
        _save(_room(rng, x, 0.35), f"bacteria_screech{i}", -27.0, fade_out=0.3)


def snd_bacteria_steps() -> None:
    """Three takes: a light, soft tap of something thin, a whisper of a scrape after it."""
    for i in range(1, 4):
        rng = G.rng_for(2548 + i)
        n = int(0.22 * SR)
        tt = np.arange(n) / SR
        tap = G.fft_filter(rng.normal(size=n) * np.exp(-tt / 0.004), lambda f: G.bp_gain(f, 700, 3500, 2))
        thump = np.sin(2 * np.pi * rng.uniform(110, 150) * tt) * np.exp(-tt / 0.02)
        scrape = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 1500, 5000, 2)) * \
            np.exp(-np.abs(tt - 0.06) / 0.03) * 0.15
        x = G.norm(tap) + 0.5 * G.norm(thump) + scrape
        _save(_darken(x, 4500), f"bacteria_step{i}", -38.0, fade_out=0.03)


def snd_bacteria_hurt() -> None:
    """A breath caught short, and a rattle."""
    rng = G.rng_for(2568)
    scared = _breaths(SCARED)
    gasp = _slow(scared[2][:int(0.3 * SR)], 0.8)
    n = int(0.7 * SR)
    x = _fit(gasp * G.env_adsr(len(gasp), 0.005, 0.03), 0.7)
    x = G.norm(x) + 0.5 * G.norm(_clicks(rng, n, 0.12 + np.cumsum(rng.uniform(0.02, 0.04, 6))))
    _save(_room(rng, _darken(x, 3500)), "bacteria_hurt", -31.0)


def snd_bacteria_death() -> None:
    """One long exhale, slowing and darkening to nothing, the clicks thinning out."""
    rng = G.rng_for(2580)
    heavy = _breaths(HEAVY)
    dur = 2.6
    n = int(dur * SR)
    t = np.arange(n) / SR
    breath = sorted(heavy, key=len)[-1]
    rate = np.linspace(0.55, 0.3, n)
    x = _fit(rec._resample_varying(breath, rate), dur) * np.exp(-t / 1.1)
    x = G.fft_filter(x, lambda f: G.lp_gain(f, 1800, 2))
    clicks = _clicks(rng, n, np.cumsum(rng.uniform(0.08, 0.3, 7))) * np.exp(-t / 0.8)
    _save(_room(rng, G.norm(x) + 0.35 * G.norm(clicks)), "bacteria_death", -30.0, fade_out=0.4)


def snd_bacteria_lost() -> None:
    """It gives up: a slow sigh moving away (it gets darker as it goes), clicks fading behind it."""
    rng = G.rng_for(2630)
    heavy = _breaths(HEAVY)
    dur = 2.6
    n = int(dur * SR)
    t = np.arange(n) / SR
    sigh = _fit(_slow(sorted(heavy, key=len)[-2], 0.6), dur)
    near = G.fft_filter(sigh, lambda f: G.lp_gain(f, 2500, 2))
    far = G.fft_filter(sigh, lambda f: G.lp_gain(f, 500, 2))
    away = G.smoothstep(0.2, 2.2, t)
    x = (near * (1 - away) + far * away) * np.exp(-t / 1.4)
    clicks = _clicks(rng, n, np.cumsum(rng.uniform(0.2, 0.45, 5))) * np.exp(-t / 0.7)
    _save(_room(rng, G.norm(x) + 0.25 * G.norm(_darken(clicks, 1500))), "bacteria_lost", -31.0, fade_out=0.5)


TEXTURES: list = []
SOUNDS = [snd_bacteria_ambient, snd_bacteria_search, snd_bacteria_screech, snd_bacteria_steps,
          snd_bacteria_hurt, snd_bacteria_death, snd_bacteria_lost]
