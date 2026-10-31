"""Level 490 ("Penance"): the storm's thunder and the air of the place, from real recordings.

Thunder in three distances (client/Level490Lightning picks by how far the strike is):
  thunder_close  a strike a few hundred blocks off: the crack, then the roar breaking up
  thunder_roll   farther: one long roll
  thunder_far    far off in the storm: a low rumble, its highs gone
and the loops of client/Level490Ambience:
  wind       wind howling over the void
  wire       wind singing in the colossal chains (an iron wire in the wind)
  whirlwind  the far-off roar of the whirlwind pillars
  rumble     a constant low rumble under everything
  drone      a deep, slow, voice-like drone (made here, not recorded)
and the chains (client/Level490ChainEvents, client/Level490SkyHung):
  chain_creak  a colossal chain near by groaning under its load
  chain_jolt   a far chain jerked taut: a clank rolling on like thunder
  chain_snap   a far chain breaking under a hung ruin
  collapse     the ruin falling into the cloud sea, heard long after
Everything that is meant to sound enormous is a real sound slowed down (lower and longer), its
highs taken away by the distance, and given a long dark tail (a made-up impulse response: no
extra recordings).

The recordings are CC0 field recordings from Freesound (tools/vendor/freesound, see its
License.txt and CREDITS.md). Loudness targets (LUFS): close -16, roll -20, far -27; the loops
-30 to -34 (their sounds.json volumes do the rest); snap -20, jolt -21, collapse -22, creak -24.

Run only these with:  python tools/gen_assets.py level490_
"""
from __future__ import annotations

import numpy as np
from scipy.signal import fftconvolve

import gen_assets as G
from level_assets.recorded import _clean, _loop, _rec, _save, loud

SR = G.SR
OUT = G.SND / "level490"


def _shot(name: str, lo: float, hi: float, target: float, tail: float) -> np.ndarray:
    """One thunder: cleaned, band-limited to lo..hi Hz, set to `target` LUFS, its tail faded."""
    x = _clean(_rec(name), lo, hi)
    x = loud(x, target)
    n = len(x)
    k = int(tail * SR)
    x[n - k:] *= np.linspace(1, 0, k) ** 1.5
    return x


def snd_level490_thunder_close() -> None:
    for k, name in enumerate(("243614_thunderstorm_lightning_strike_a", "243614_thunderstorm_lightning_strike_b",
                              "248121_lightning_a", "248121_lightning_b", "194364_thunder_clap")):
        _save(_shot(name, 30, 15000, -16, 3.0), OUT / f"thunder_close_{k + 1}.ogg")


def snd_level490_thunder_roll() -> None:
    for k, name in enumerate(("367702_thunder_long_rumbling_a", "367702_thunder_long_rumbling_b",
                              "367702_thunder_long_rumbling_c", "414050_thunder_big", "243780_thunder_growling")):
        _save(_shot(name, 25, 6000, -20, 4.0), OUT / f"thunder_roll_{k + 1}.ogg")


def snd_level490_thunder_far() -> None:
    for k in range(5):
        _save(_shot(f"361401_distant_thunder_{chr(97 + k)}", 25, 1400, -27, 4.0), OUT / f"thunder_far_{k + 1}.ogg")


def snd_level490_wind() -> None:
    x = _clean(_rec("250036_mystic_wind_howling"), 60, 9000)
    _save(loud(_loop(x, 38.0, 3.0), -30), OUT / "wind.ogg", loop=True)


def snd_level490_wire() -> None:
    # The iron wire's singing, a little lower and slower: something much bigger than a wire.
    x = _clean(_rec("146993_wind_iron_wire"), 80, 10000)
    slow = np.interp(np.arange(0, len(x) - 1, 0.8), np.arange(len(x)), x)
    _save(loud(_loop(slow, 46.0, 3.0), -33), OUT / "wire.ogg", loop=True)


def snd_level490_whirlwind() -> None:
    # The hurricane far off: only its roar, the highs gone with the distance.
    x = _clean(_rec("735350_wind_hurricane"), 25, 1200)
    _save(loud(_loop(x, 38.0, 3.0), -31), OUT / "whirlwind.ogg", loop=True)


def snd_level490_rumble() -> None:
    x = _clean(_rec("512687_rumble"), 20, 400)
    _save(loud(_loop(x, 35.0, 3.0), -32), OUT / "rumble.ogg", loop=True)


def snd_level490_drone() -> None:
    """A deep drone like distant voices: three low notes of buzzing tones through vowel-like
    resonances, each swelling and fading on its own whole-number cycle, so the loop is seamless."""
    length = 48.0
    n = int(length * SR)
    t = np.arange(n) / SR
    rng = G.rng_for(49101)
    voices = np.zeros(n)
    for f0, amp, cycles in ((55.0, 1.0, 3), (82.4, 0.7, 4), (110.0, 0.55, 5), (55.0 * 1.003, 0.6, 2)):
        # A buzzing tone (rich in harmonics, like a voice), a slow wobble in pitch.
        wob = 1.0 + 0.003 * np.sin(2 * np.pi * (cycles + 1) * t / length + rng.uniform(0, 6.28))
        phase = 2 * np.pi * np.cumsum(f0 * wob) / SR
        tone = sum(np.sin(h * phase) / h for h in range(1, 18))
        swell = 0.55 + 0.45 * np.sin(2 * np.pi * cycles * t / length + rng.uniform(0, 6.28))
        voices += amp * tone * swell
    # Vowel resonances ("oh" and "ah"), slowly trading places.
    oh = G.fft_filter(voices, lambda f: np.exp(-((f - 420) / 90) ** 2) + 0.6 * np.exp(-((f - 800) / 120) ** 2), circular=True)
    ah = G.fft_filter(voices, lambda f: np.exp(-((f - 700) / 110) ** 2) + 0.5 * np.exp(-((f - 1150) / 150) ** 2), circular=True)
    mix = 0.5 + 0.5 * np.sin(2 * np.pi * 2 * t / length)
    x = oh * mix + ah * (1 - mix) + 0.4 * G.fft_filter(voices, lambda f: G.lp_gain(f, 200, 2), circular=True)
    air = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 200, 2000, 2))
    x = G.norm(x) + 0.08 * G.norm(air)
    _save(loud(x, -34), OUT / "drone.ogg", loop=True)


def _slow(x: np.ndarray, factor: float) -> np.ndarray:
    """Played at `factor` times the speed: below 1, slower and lower, as if from something far bigger."""
    return np.interp(np.arange(0, len(x) - 1, factor), np.arange(len(x)), x)


def _dark(x: np.ndarray, lo: float, hi: float) -> np.ndarray:
    """Only lo..hi Hz left (distance takes the highs)."""
    return G.fft_filter(x, lambda f: G.hp_gain(f, lo, 2) * G.lp_gain(f, hi, 2))


def _space(x: np.ndarray, seconds: float, mix: float, seed: int, hi: float = 2500.0) -> np.ndarray:
    """x with a long dark tail `seconds` long: convolved with decaying, filtered noise."""
    rng = np.random.default_rng(seed)
    n = int(seconds * SR)
    ir = rng.normal(0, 1, n) * np.exp(-np.arange(n) / (seconds * SR / 6.9))
    ir = G.fft_filter(ir, lambda f: G.lp_gain(f, hi, 2))
    dry = np.concatenate([x, np.zeros(n)])
    wet = np.zeros(len(dry))
    full = fftconvolve(x, ir)
    wet[:min(len(full), len(dry))] = full[:len(dry)]
    wet *= np.sqrt(np.mean(dry ** 2)) / (np.sqrt(np.mean(wet ** 2)) + 1e-12)
    return dry * (1 - mix) + wet * mix


def _grab(x: np.ndarray, at: float, before: float, after: float, search: float = 0.6) -> np.ndarray:
    """The part of x round its loudest moment within `search` s of `at` s: `before` s ahead of it
    to `after` s past it."""
    a, b = int((at - search) * SR), int((at + search) * SR)
    peak = a + int(np.argmax(np.abs(x[a:b])))
    return x[max(0, peak - int(before * SR)): peak + int(after * SR)].copy()


def _mixed(*parts: tuple[np.ndarray, float, float]) -> np.ndarray:
    """(sound, start s, gain) parts laid over each other."""
    n = max(int(s * SR) + len(x) for x, s, _ in parts)
    out = np.zeros(n)
    for x, s, g in parts:
        i = int(s * SR)
        out[i:i + len(x)] += x * g
    return out


def snd_level490_chain_jolt() -> None:
    """A colossal chain jerked taut far off: a heavy chain dropped, slowed to something enormous,
    rolling on like thunder; one with the grind of a chain dragged through its fitting under it,
    one from a ship's anchor chain."""
    chain = _rec("611673_heavy_chain")
    drag = _clean(_rec("199282_big_metal_chain"), 30, 5000)
    anchor = _clean(_rec("477129_anchor_raising"), 30, 6000)
    takes = [
        _slow(_clean(_grab(chain, 8.5, 0.03, 1.4), 30, 7000), 0.4),
        _slow(_clean(_grab(chain, 19.5, 0.03, 1.2), 30, 7000), 0.38),
        _mixed((_slow(_clean(_grab(chain, 1.0, 0.03, 1.4), 30, 7000), 0.42), 0.0, 1.0),
               (_slow(drag[int(2.0 * SR):int(4.6 * SR)], 0.35), 0.15, 0.35)),
        _slow(_grab(anchor, 4.0, 0.03, 1.6, search=1.0), 0.4),
    ]
    for k, x in enumerate(takes):
        x = _space(_dark(x, 25, 1600), 4.5, 0.6, 4900 + k)
        _save(loud(x, -21), OUT / f"chain_jolt_{k + 1}.ogg", fade_out=2.0)


def snd_level490_chain_snap() -> None:
    """A colossal chain breaking far off: the crack of overloaded iron, then links lashing loose."""
    creaks = _rec("625616_metal_creaks")
    chain = _rec("611673_heavy_chain")
    for k, (crack_at, lash_at) in enumerate(((1.5, 5.0), (10.2, 11.0))):
        crack = _slow(_clean(_grab(creaks, crack_at, 0.02, 1.2), 60, 9000), 0.55)
        lash = _slow(_clean(_grab(chain, lash_at, 0.05, 3.0), 30, 8000), 0.5)
        x = _mixed((crack, 0.0, 1.0), (lash, 0.35, 0.8))
        x = _space(_dark(x, 25, 2500), 3.5, 0.5, 4910 + k)
        _save(loud(x, -20), OUT / f"chain_snap_{k + 1}.ogg", fade_out=1.5)


def snd_level490_collapse() -> None:
    """A hung ruin falling into the cloud sea far off: a rock slide slowed and darkened, rumbling on."""
    slide = _rec("500638_rock_slide")
    for k, (a, b, factor, hi) in enumerate(((2.0, 14.0, 0.6, 900), (7.0, 20.0, 0.5, 700))):
        x = _slow(_clean(slide[int(a * SR):int(b * SR)], 20, 5000), factor)
        x = _space(_dark(x, 20, hi), 4.0, 0.45, 4920 + k)
        i = int(0.4 * SR)
        x[:i] *= np.linspace(0, 1, i)
        _save(loud(x, -22), OUT / f"collapse_{k + 1}.ogg", fade_out=3.0)


def snd_level490_chain_creak() -> None:
    """A colossal chain near by groaning under its load: metal creaks slowed to half speed."""
    grinding = _rec("59160_grincements_helvetia")
    creaks = _rec("625616_metal_creaks")
    slow = _rec("172411_creaking_metal_slow")
    takes = [_grab(grinding, 2.0, 0.3, 2.2), _grab(grinding, 5.0, 0.3, 2.5), _grab(grinding, 13.5, 0.3, 2.5),
             _grab(creaks, 10.2, 0.4, 2.0), slow[int(5.0 * SR):int(9.0 * SR)]]
    for k, x in enumerate(takes):
        x = _slow(_clean(x, 40, 9000), 0.55)
        x = _space(_dark(x, 30, 3000), 1.8, 0.3, 4930 + k)
        i = int(0.05 * SR)
        x[:i] *= np.linspace(0, 1, i)
        _save(loud(x, -24), OUT / f"chain_creak_{k + 1}.ogg", fade_out=1.0)


SOUNDS = [snd_level490_thunder_close, snd_level490_thunder_roll, snd_level490_thunder_far, snd_level490_wind,
          snd_level490_wire, snd_level490_whirlwind, snd_level490_rumble, snd_level490_drone,
          snd_level490_chain_jolt, snd_level490_chain_snap, snd_level490_collapse, snd_level490_chain_creak]
