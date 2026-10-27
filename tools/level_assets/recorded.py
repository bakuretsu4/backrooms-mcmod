"""The lights and the rooms of Levels 0-2, and your own breathing, from real recordings.

The fluorescent hum, the flicker clicks, the power going out and coming back, Level 1's garage
air, Level 0's room tone and the breathing are built from CC0 field recordings (tools/vendor/freesound, see its
License.txt and CREDITS.md), cleaned up and looped, with a little synthesis layered in where it
helps (the deep rumble under the garage, drips far off, the hum winding down when a breaker trips).

Loudness (LUFS, how loud it sounds) is set per sound, quiet on purpose: the level loops are
heard at about -30 (their sounds.json volume does the last step), one lamp's hum close up at -27
(several near you add up), flicker clicks -31, the power going out or coming back -25, breathing
-29 (out of breath) and -31 (scared).

Run only these with:  python tools/gen_assets.py recorded_
"""
from __future__ import annotations

import numpy as np
import soundfile as sf

import gen_assets as G

SR = G.SR
REC = G.Path(G.__file__).resolve().parent / "vendor" / "freesound"


def _rec(name: str) -> np.ndarray:
    x, sr = sf.read(str(REC / f"{name}.ogg"))
    assert sr == SR, (name, sr)
    return x.mean(axis=1) if x.ndim == 2 else x


def _clean(x: np.ndarray, lo: float = 40.0, hi: float = 14000.0) -> np.ndarray:
    """Cut the rumble of handling and wind below `lo`, and the hiss above `hi`."""
    return G.fft_filter(x, lambda f: G.hp_gain(f, lo, 2) * G.lp_gain(f, hi, 2))


def _loop(x: np.ndarray, length: float, fade: float) -> np.ndarray:
    """A seamless loop of `length` seconds: the extra `fade` seconds at the end are cross-faded
    (equal power) into the start, so there is no click or bump where it wraps."""
    n, k = int(length * SR), int(fade * SR)
    assert len(x) >= n + k, (len(x), n + k)
    body = x[:n].copy()
    w = np.linspace(0, np.pi / 2, k)
    body[:k] = x[:k] * np.sin(w) + x[n:n + k] * np.cos(w)
    return body


def _rms(x: np.ndarray) -> float:
    return float(np.sqrt(np.mean(x ** 2)))


def lufs(x: np.ndarray) -> float:
    """Integrated loudness (LUFS, EBU R128: how loud it sounds, not just its level), by ffmpeg."""
    import subprocess
    import tempfile
    with tempfile.NamedTemporaryFile(suffix=".wav") as tmp:
        sf.write(tmp.name, np.asarray(x, np.float32), SR, subtype="FLOAT")
        out = subprocess.run(["ffmpeg", "-nostats", "-i", tmp.name, "-filter_complex", "apad=pad_dur=0.5,ebur128", "-f", "null", "-"],
                             capture_output=True, text=True).stderr
    return float([line for line in out.splitlines() if line.strip().startswith("I:")][-1].split()[1])


def loud(x: np.ndarray, target: float, peak: float = 0.9) -> np.ndarray:
    """Scale to a loudness in LUFS, but never past `peak` (a soft limiter takes the rare peaks
    down instead). The targets live with each sound; see the table in this module's docstring."""
    x = x * 10 ** ((target - lufs(x)) / 20)
    knee = peak * 0.8
    over = np.abs(x) > knee
    if over.any():
        x = np.where(over, np.sign(x) * (knee + (peak - knee) * np.tanh((np.abs(x) - knee) / (peak - knee))), x)
    return x


def _onsets(x: np.ndarray, jump_db: float = 9.0, gap: float = 0.25) -> list[float]:
    """Times where the level jumps well above what came just before (clicks, strikes)."""
    win = int(0.004 * SR)
    env = np.sqrt(np.convolve(x ** 2, np.ones(win) / win, "same"))
    db = 20 * np.log10(env + 1e-9)
    hop = win
    times, last = [], -1.0
    for i in range(int(0.1 * SR), len(x) - hop, hop):
        before = np.median(db[i - int(0.1 * SR):i])
        if db[i] - before > jump_db and i / SR - last > gap:
            times.append(i / SR)
            last = i / SR
    return times


def _save(x: np.ndarray, path, loop: bool = False, fade_out: float = 0.0) -> None:
    """Like G.save_ogg but at the level given (no normalising), encoded by ffmpeg's Vorbis
    encoder (libsndfile crashes writing long OGG files)."""
    import subprocess
    import tempfile
    x = np.asarray(x, float) - np.mean(x)
    if not loop:
        fi, fo = int(0.002 * SR), max(1, int(fade_out * SR)) if fade_out else 1
        x[:fi] *= np.linspace(0, 1, fi)
        x[len(x) - fo:] *= np.linspace(1, 0, fo)
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav") as tmp:
        sf.write(tmp.name, x.astype(np.float32), SR, subtype="FLOAT")
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", tmp.name, "-c:a", "libvorbis", "-q:a", "7" if loop else "6",
                        str(path)], check=True)


# ---------------------------------------------------------------------------------------------

def snd_recorded_light_hum() -> None:
    """The positional hum of one fluorescent fixture: a real ballast buzz (120/240 Hz), 8 s loop."""
    x = _clean(_rec("638895_fluorescent_hum"), lo=70)
    x = _loop(x[int(1.0 * SR):], 8.0, 0.6)
    _save(loud(x, -27.0), G.SND / "light" / "hum.ogg", loop=True)


def snd_recorded_light_blink() -> None:
    """Eight flicker clicks: real tubes and starters catching and dropping out (the moment of
    each, with the bit of buzz around it), from three recordings."""
    events = []
    for name in ("777053_fluorescent_flicker", "452625_fluorescent_switch_starts", "125064_faulty_starter_hum"):
        x = _clean(_rec(name), lo=90)
        for t in _onsets(x):
            seg = x[max(0, int((t - 0.012) * SR)): int((t + 0.5) * SR)]
            if len(seg) < int(0.3 * SR):
                continue
            events.append((float(np.abs(seg[:int(0.05 * SR)]).max()), name, t, seg))
    # The eight sharpest, at least two from each recording if they have them.
    events.sort(key=lambda e: -e[0])
    chosen = []
    for name in ("777053_fluorescent_flicker", "452625_fluorescent_switch_starts", "125064_faulty_starter_hum"):
        chosen += [e for e in events if e[1] == name][:2]
    chosen += [e for e in events if e not in chosen][:8 - len(chosen)]
    assert len(chosen) == 8, len(chosen)
    for i, (_, name, t, seg) in enumerate(chosen, start=1):
        seg = seg * G.env_adsr(len(seg), 0.002, 0.12)
        print(f"  blink{i}: {name} at {t:.2f} s")
        _save(loud(seg, -31.0, peak=0.5), G.SND / "light" / f"blink{i}.ogg")


def _breaker() -> np.ndarray:
    """The big breaker being thrown: its clunk and the short ring after, no fan."""
    x = _clean(_rec("139970_big_breaker"), lo=35)
    t = _onsets(x, jump_db=12)[0]
    seg = x[max(0, int((t - 0.01) * SR)): int((t + 0.7) * SR)]
    return seg * G.env_adsr(len(seg), 0.001, 0.25)


def _hum_bed(seconds: float) -> np.ndarray:
    x = _clean(_rec("638895_fluorescent_hum"), lo=70)
    return x[:int(seconds * SR)]


def _resample_varying(x: np.ndarray, rate: np.ndarray) -> np.ndarray:
    """Plays x at a speed that changes over time (rate per output sample), like a tape slowing."""
    pos = np.cumsum(rate)
    pos = pos[pos < len(x) - 1]
    return np.interp(pos, np.arange(len(x)), x)


def snd_recorded_power_down() -> None:
    """The lights going out: the breaker clunks, and the hum of a whole floor of tubes sags in
    pitch and dies over half a second, ringing on in the big concrete room."""
    rng = G.rng_for(1201)
    dur = 2.4
    n = int(dur * SR)
    t = np.arange(n) / SR
    t0 = 0.12
    after = np.clip(t - t0, 0, None)
    rate = np.where(t < t0, 1.0, 0.35 + 0.65 * np.exp(-after / 0.35))
    hum = _resample_varying(_hum_bed(6.0), rate)
    hum = np.pad(hum, (0, max(0, n - len(hum))))[:n]
    hum *= np.where(t < t0, 1.0, np.exp(-after / 0.22))
    x = np.zeros(n)
    G.add_at(x, _breaker(), t0 - 0.005, circular=False)
    x = G.norm(x) + 0.55 * G.norm(hum)
    wet = G.conv(x, G.reverb_ir(rng, 1.8, 0.55, lp=3500, predelay=0.02))[:n]
    x = x + 0.35 * G.norm(wet) * np.abs(x).max()
    _save(loud(x, -25.0, peak=0.7), G.SND / "event" / "power_down.ogg", fade_out=0.15)


def snd_recorded_power_up() -> None:
    """The lights coming back: the breaker, then real starters ticking as the tubes try to strike,
    and the hum of the floor swelling in."""
    rng = G.rng_for(1202)
    dur = 2.2
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    G.add_at(x, _breaker(), 0.01, circular=False)
    starts = _clean(_rec("452625_fluorescent_switch_starts"), lo=90)
    on = _onsets(starts)
    # The cluster of starter ticks of the recording's first start, about 0.7 s of it.
    seg = starts[max(0, int((on[0] - 0.01) * SR)): int((on[0] + 0.7) * SR)]
    seg = seg * G.env_adsr(len(seg), 0.001, 0.1)
    G.add_at(x, 0.8 * G.norm(seg), 0.32, circular=False)
    hum = _hum_bed(dur)
    hum = np.pad(hum, (0, max(0, n - len(hum))))[:n] * G.smoothstep(0.85, 1.25, t)
    x = G.norm(x) + 0.5 * G.norm(hum)
    wet = G.conv(x, G.reverb_ir(rng, 1.2, 0.35, lp=4500, predelay=0.02))[:n]
    x = x + 0.25 * G.norm(wet) * np.abs(x).max()
    _save(loud(x, -25.0, peak=0.7), G.SND / "event" / "power_up.ogg", fade_out=0.12)


def snd_recorded_level1_ambience() -> None:
    """24 s of an empty underground garage: the real air (ventilation rattling far off in a big
    concrete room), a deep rumble under it and, now and then, a drip echoing somewhere."""
    rng = G.rng_for(402)
    air = _clean(_rec("452292_parking_garage_vent"), lo=25, hi=9000)
    air = _loop(air, 24.0, 2.0)
    n = len(air)
    t = np.arange(n) / SR
    rumble = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 22, 70, 3)) * G.drift(n, rng, 0.15, 0.08, 0.3)
    drips = np.zeros(n)
    for st, f0, a in ((3.1, 950, 1.0), (11.4, 820, 0.6), (19.8, 1020, 0.8)):
        dt = G.t_axis(0.12)
        f = f0 * (1 + 0.5 * np.clip(dt / 0.05, 0, 1))
        d = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-dt / 0.03) * G.smoothstep(0, 0.004, dt)
        G.add_at(drips, d * a, st, circular=True)
    drips = G.fft_filter(drips, lambda f: G.lp_gain(f, 1400, 2), circular=True)
    wet = G.circ_conv(drips, G.reverb_ir(rng, 4.0, 1.3, lp=1200, predelay=0.05))
    x = G.norm(air) + 0.35 * G.norm(rumble) + 0.03 * G.norm(drips) + 0.1 * G.norm(wet)
    _save(loud(x, -24.0), G.SND / "ambient" / "level1_ambience.ogg", loop=True)


def snd_recorded_level0_hum() -> None:
    """18 s of a fluorescent-lit room's tone (a recording made for exactly this), with a soft
    synthesized room bed under it so the loop never sounds thin."""
    rng = G.rng_for(401)
    room_rec = _clean(_rec("626096_backrooms_ambience"), lo=30, hi=12000)
    room_rec = _loop(room_rec, 18.0, 1.5)
    n = len(room_rec)
    bed = G.periodic_noise(n, rng, lambda f: G.hp_gain(f, 20, 2) * G.lp_gain(f, 450, 3) / np.maximum(f, 20))
    x = G.norm(room_rec) + 0.25 * G.norm(bed)
    _save(loud(x, -24.0), G.SND / "ambient" / "level0_hum.ogg", loop=True)


def _quiet_near(x: np.ndarray, at: float, search: float) -> int:
    """The quietest moment within `search` seconds of `at` (a gap between two breaths)."""
    win = int(0.03 * SR)
    lo, hi = int((at - search) * SR), int((at + search) * SR)
    env = np.convolve(x[lo:hi] ** 2, np.ones(win) / win, "same")
    return lo + int(np.argmin(env))


def _breath_loop(name: str, start: float, length: float) -> np.ndarray:
    """A loop of whole breaths: it starts and ends in the gaps between them, and the short
    cross-fade at the seam falls in a gap too, so no breath is cut or doubled."""
    x = _clean(_rec(name), lo=80, hi=12000)
    a = _quiet_near(x, start, 0.5)
    b = _quiet_near(x, start + length, 0.6)
    k = int(0.12 * SR)
    seg = x[a:b + k]
    return _loop(seg, (b - a) / SR, 0.12)


def snd_recorded_breath_heavy() -> None:
    """Out of breath after a sprint: deep, fast breaths close to the microphone."""
    x = _breath_loop("757113_heavy_breathing", 1.0, 12.0)
    _save(loud(x, -29.0, peak=0.6), G.SND / "player" / "breath_heavy.ogg", loop=True)


def snd_recorded_breath_scared() -> None:
    """Something is near: quick, shaky, held-back breathing."""
    x = _breath_loop("554307_scared_breathing", 1.0, 14.0)
    _save(loud(x, -31.0, peak=0.6), G.SND / "player" / "breath_scared.ogg", loop=True)


TEXTURES: list = []
SOUNDS = [snd_recorded_light_hum, snd_recorded_light_blink, snd_recorded_power_down, snd_recorded_power_up,
          snd_recorded_level1_ambience, snd_recorded_level0_hum, snd_recorded_breath_heavy, snd_recorded_breath_scared]
