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

The recordings are CC0 field recordings from Freesound (tools/vendor/freesound, see its
License.txt and CREDITS.md). Loudness targets (LUFS): close -16, roll -20, far -27; the loops
-30 to -34 (their sounds.json volumes do the rest).

Run only these with:  python tools/gen_assets.py level490_
"""
from __future__ import annotations

import numpy as np

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


SOUNDS = [snd_level490_thunder_close, snd_level490_thunder_roll, snd_level490_thunder_far, snd_level490_wind,
          snd_level490_wire, snd_level490_whirlwind, snd_level490_rumble, snd_level490_drone]
