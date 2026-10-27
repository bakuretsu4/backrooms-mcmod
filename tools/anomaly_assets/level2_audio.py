"""Level 2 "Pipe Dreams", the linear revamp: the sounds of the long pipe corridor.

All synthesized (the level's ambience loop and the owner's friend's recorded lamp sounds stay as
they are). Played by src/main/java/.../client/anomaly/level2/Level2Emitters.java:

  level2/pipe_knock1..4   water hammer: a dull knock with a short steel ring (a run of these
                          travels along the corridor)
  level2/pipe_clank1..3   a heavy clank that rings on, a bracket chattering after it
  level2/pipe_creak1..3   hot metal stretching: a slow stick-slip groan and a couple of ticks
  level2/pipe_rush1..2    water rushing through the overhead bundle (the game moves it past you)
  level2/steam_leak       seamless loop: a leaking steam valve's steady hiss and faint whistle
  level2/pump_hum         seamless loop: a pump's motor hum, slow piston thump, bearing whine
  level2/drip1..5         a drop landing in the wet gutter

Loudness is set in LUFS (EBU R128, measured here with the same gating ffmpeg's ebur128 uses), in
line with the rest of the mix (README "Mix", tools/level_assets/recorded.py): the loops sit at
the level of a lamp hum or quieter (-28 / -30), one-offs well under the -18 ceiling. The files
are NOT peak-normalised: they are written at the level given.

Run only these with:  python tools/gen_assets.py snd_l2line_
Every generator uses its own fixed random seed (20200-20299), so re-running gives identical files.
"""
from __future__ import annotations

import numpy as np

import gen_assets as G

SR = G.SR


def _snd(name: str):
    return G.SND / "level2" / f"{name}.ogg"


# =============================================================================
# Loudness (ITU-R BS.1770-4 / EBU R128 integrated loudness, gated)
# =============================================================================

def _biquad(x, b, a):
    from scipy.signal import lfilter
    return lfilter(np.asarray(b) / a[0], np.asarray(a) / a[0], x)


def _k_weight(x):
    """The K-weighting filter: a +4 dB high shelf (head) and a high pass at ~38 Hz."""
    # High shelf, 1681.97 Hz, +3.9998 dB, Q 0.7071752 (BS.1770's 48 kHz filter, re-derived for SR).
    f0, gain_db, q = 1681.974450955533, 3.999843853973347, 0.7071752369554196
    k = np.tan(np.pi * f0 / SR)
    vh = 10 ** (gain_db / 20)
    vb = vh ** 0.4996667741545416
    a0 = 1 + k / q + k * k
    b = [(vh + vb * k / q + k * k) / a0, 2 * (k * k - vh) / a0, (vh - vb * k / q + k * k) / a0]
    a = [1.0, 2 * (k * k - 1) / a0, (1 - k / q + k * k) / a0]
    x = _biquad(x, b, a)
    # High pass, 38.13547 Hz, Q 0.5003270.
    f0, q = 38.13547087602444, 0.5003270373238773
    k = np.tan(np.pi * f0 / SR)
    a0 = 1 + k / q + k * k
    a = [1.0, 2 * (k * k - 1) / a0, (1 - k / q + k * k) / a0]
    return _biquad(x, [1.0, -2.0, 1.0], a)


def lufs(x) -> float:
    """Integrated loudness in LUFS. Pads 0.5 s of silence like recorded.lufs (apad) so very short
    sounds still fill a 400 ms block."""
    x = np.concatenate([np.asarray(x, float), np.zeros(SR // 2)])
    y = _k_weight(x) ** 2
    block, hop = int(0.4 * SR), int(0.1 * SR)
    if len(y) < block:
        y = np.pad(y, (0, block - len(y)))
    starts = range(0, len(y) - block + 1, hop)
    z = np.array([y[s:s + block].mean() for s in starts])
    loud = -0.691 + 10 * np.log10(z + 1e-20)
    z = z[loud > -70.0]                                   # absolute gate
    if z.size == 0:
        return -70.0
    rel = -0.691 + 10 * np.log10(z.mean()) - 10.0       # relative gate
    z = z[-0.691 + 10 * np.log10(z) > rel]
    return float(-0.691 + 10 * np.log10(z.mean()))


def loud(x, target: float, peak: float = 0.9) -> np.ndarray:
    """Scale to `target` LUFS, never past `peak` (a soft knee takes the rare peaks down), as
    recorded.loud does for the friend's sounds."""
    x = np.asarray(x, float)
    for _ in range(3):  # the knee lowers the loudness a little: settle in a few passes
        x = x * 10 ** ((target - lufs(x)) / 20)
        knee = peak * 0.8
        over = np.abs(x) > knee
        if over.any():
            x = np.where(over, np.sign(x) * (knee + (peak - knee) * np.tanh((np.abs(x) - knee) / (peak - knee))), x)
    return x


def _save(x, name: str, target: float, peak: float = 0.9, loop: bool = False,
          fade_in: float = 0.002, fade_out: float = 0.05) -> None:
    """Brings `x` to `target` LUFS and writes level2/<name>.ogg at that level (no normalising).
    Written in one-second blocks (libsndfile's Vorbis encoder overflows on long clips)."""
    import soundfile as sf
    x = np.asarray(x, float)
    x = x - x.mean()
    if not loop:
        n = len(x)
        fi, fo = max(1, int(fade_in * SR)), max(1, int(fade_out * SR))
        x[:fi] *= np.linspace(0, 1, fi)
        x[n - fo:] *= np.linspace(1, 0, fo)
    x = loud(x, target, peak)
    path = _snd(name)
    path.parent.mkdir(parents=True, exist_ok=True)
    extra = {"compression_level": 0.0} if loop else {}
    with sf.SoundFile(str(path), "w", SR, 1, format="OGG", subtype="VORBIS", **extra) as out:
        y = x.astype(np.float32)
        for i in range(0, len(y), SR):
            out.write(y[i:i + SR])


# =============================================================================
# Building blocks
# =============================================================================

def _modes(rng, f0, ratios, decays, amps, dur, detune=0.0):
    """A struck steel pipe: inharmonic modes (a tube's partials are not whole multiples), each
    dying at its own rate. `detune` splits every mode into two close ones that beat slowly, the
    shimmer of a long pipe ringing."""
    t = G.t_axis(dur)
    out = np.zeros_like(t)
    for r, d, a in zip(ratios, decays, amps):
        f = f0 * r * rng.uniform(0.985, 1.015)
        ph = rng.uniform(0, 2 * np.pi)
        tone = np.sin(2 * np.pi * f * t + ph)
        if detune:
            tone = 0.6 * tone + 0.4 * np.sin(2 * np.pi * f * (1 + detune) * t + ph * 1.3)
        out += a * tone * np.exp(-t / d)
    return out


def _thud(rng, f, decay, dur):
    """The dull body of an impact: a low sine that drops a little in pitch as it dies."""
    t = G.t_axis(dur)
    freq = f * (1.0 + 0.35 * np.exp(-t / 0.012))
    return np.sin(2 * np.pi * np.cumsum(freq) / SR) * np.exp(-t / decay) * G.smoothstep(0, 0.0015, t)


def _click(rng, dur, lo, hi, decay):
    """The very first instant of metal on metal: a short band-passed noise tick."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, lo, hi, 2))[:n]
    return G.norm(x) * np.exp(-t / decay)


def _corridor(rng, x, wet=0.18, decay=0.35, length=1.2):
    """A touch of the concrete corridor baked in (the game adds its own room echo on top, so
    keep this short and light): a few early slaps off the close walls and a short dark tail."""
    ir = G.reverb_ir(rng, length, decay, lp=3500.0, predelay=0.004, early=((0.011, 0.5), (0.023, 0.3)))
    out = np.concatenate([x, np.zeros(int(length * SR))])
    tail = G.conv(x, ir)[:len(out)]
    tail = np.pad(tail, (0, len(out) - len(tail)))
    return out + wet * G.norm(tail) * np.abs(x).max()


# Steel tube partial ratios (free-free bar modes, close to a long pipe struck on its side).
TUBE = (1.0, 2.756, 5.404, 8.933, 13.34)


# =============================================================================
# Pressure in the pipes (one-offs)
# =============================================================================

def snd_l2line_pipe_knock() -> None:
    """Four ~0.9 s knocks: water hammer slamming through a pipe at a bracket. Mostly the dull
    thud of the water column stopping (60-110 Hz), a click, and a short muted steel ring."""
    for i, seed in enumerate((20201, 20202, 20203, 20204), 1):
        rng = G.rng_for(seed)
        dur = 0.9
        f0 = rng.uniform(190, 320)
        ring = _modes(rng, f0, TUBE[:4], (0.16, 0.09, 0.05, 0.03), (1.0, 0.55, 0.3, 0.15), dur)
        thud = _thud(rng, rng.uniform(60, 110), rng.uniform(0.05, 0.08), dur)
        click = np.zeros(int(dur * SR))
        G.add_at(click, _click(rng, 0.03, 1200, 6000, 0.004), 0.0, False)
        x = 1.0 * G.norm(thud) + 0.45 * G.norm(ring) + 0.25 * click
        # Muffled a little: it comes through the insulation and the water inside.
        x = G.fft_filter(x, lambda f: G.lp_gain(f, 2600, 2))
        _save(_corridor(rng, x, wet=0.15), f"pipe_knock{i}", -24.0, fade_out=0.15)


def snd_l2line_pipe_clank() -> None:
    """Three ~2.6 s clanks: something heavy shifts inside a main and the steel rings on for a
    second or more, slowly beating; a loose bracket chatters a few times right after."""
    for i, seed in enumerate((20211, 20212, 20213), 1):
        rng = G.rng_for(seed)
        dur = 2.6
        f0 = rng.uniform(110, 190)
        ring = _modes(rng, f0, TUBE, (1.1, 0.7, 0.4, 0.22, 0.12), (1.0, 0.7, 0.45, 0.25, 0.12), dur,
                      detune=rng.uniform(0.002, 0.005))
        thud = _thud(rng, rng.uniform(55, 80), 0.12, dur)
        x = 0.8 * G.norm(thud) + 0.7 * G.norm(ring)
        G.add_at(x, 0.35 * _click(rng, 0.05, 900, 7000, 0.006), 0.0, False)
        # The bracket rattling: 3-5 small, quickly fading taps 40-200 ms after the hit.
        t_tap = 0.04
        for k in range(rng.integers(3, 6)):
            tap = _modes(rng, rng.uniform(600, 900), TUBE[:3], (0.03, 0.02, 0.01), (1.0, 0.5, 0.2), 0.12)
            G.add_at(x, 0.22 * (0.7 ** k) * G.norm(tap), t_tap, False)
            t_tap += rng.uniform(0.025, 0.06)
        x = G.fft_filter(x, lambda f: G.lp_gain(f, 4500, 2))
        _save(_corridor(rng, x, wet=0.2), f"pipe_clank{i}", -22.0, fade_out=0.4)


def snd_l2line_pipe_creak() -> None:
    """Three ~2.4 s creaks: a hot pipe sliding in its hanger. Stick-slip friction (a train of
    tiny grabs whose rate wanders from a slow crackle to a groan), resonating in the pipe's
    walls, swelling and dying; then one or two sharp ticks as it settles."""
    for i, seed in enumerate((20221, 20222, 20223), 1):
        rng = G.rng_for(seed)
        dur = 2.4
        n = int(dur * SR)
        t = np.arange(n) / SR
        # The grab rate: 25-140 per second, wandering smoothly.
        rate = 25 + 115 * (0.5 + 0.5 * G.smooth_jitter(rng, n, 1.5, 1.0))
        rate *= 1.0 + 0.25 * np.sin(2 * np.pi * rng.uniform(0.3, 0.7) * t)
        phase = np.cumsum(rate) / SR
        grabs = np.zeros(n)
        idx = np.nonzero(np.diff(np.floor(phase)) > 0)[0]
        grabs[idx] = rng.uniform(0.4, 1.0, idx.size)
        # Each grab rings the pipe wall briefly.
        body = np.zeros(int(0.06 * SR))
        tb = np.arange(body.size) / SR
        for fc, d, a in ((rng.uniform(280, 360), 0.020, 1.0), (rng.uniform(640, 760), 0.012, 0.6),
                         (rng.uniform(1200, 1450), 0.007, 0.35)):
            body += a * np.sin(2 * np.pi * fc * tb) * np.exp(-tb / d)
        x = G.conv(grabs, body)[:n]
        env = G.smoothstep(0.0, rng.uniform(0.4, 0.8), t) * (1.0 - G.smoothstep(dur * 0.55, dur * 0.85, t))
        x = G.norm(x) * env
        # Settling ticks.
        for k in range(rng.integers(1, 3)):
            tick = _modes(rng, rng.uniform(900, 1400), TUBE[:3], (0.03, 0.015, 0.008), (1.0, 0.4, 0.2), 0.1)
            G.add_at(x, 0.5 * G.norm(tick), dur * rng.uniform(0.78, 0.92), False)
        x = G.fft_filter(x, lambda f: G.bp_gain(f, 120, 3500, 2))
        _save(_corridor(rng, x, wet=0.22), f"pipe_creak{i}", -24.0, fade_in=0.05, fade_out=0.3)


def snd_l2line_pipe_rush() -> None:
    """Two ~4.3 s rushes: a surge of water through the overhead bundle. A low flowing body and
    a softer wash on top, a few gurgles as air rides along, swelling in and dying away (the game
    moves the sound over your head while it plays)."""
    for i, seed in enumerate((20231, 20232), 1):
        rng = G.rng_for(seed)
        dur = 4.3
        n = int(dur * SR)
        t = np.arange(n) / SR
        body = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 50, 260, 2))[:n]
        wash = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 250, 1400, 2))[:n]
        # Smooth swells only (no fast amplitude grit: that reads as insects, not water).
        swell = 1.0 + 0.35 * G.smooth_jitter(rng, n, 2.0, 1.0)
        gurgle = np.zeros(n)
        for _ in range(rng.integers(5, 9)):
            g = G._water_bubble(rng, rng.uniform(220, 520), rng.uniform(0.06, 0.12))
            G.add_at(gurgle, rng.uniform(0.4, 1.0) * g, rng.uniform(0.6, dur - 1.0), False)
        x = (G.norm(body) * 0.9 + G.norm(wash) * 0.35) * swell + 0.25 * G.norm(gurgle)
        x = x * G.smoothstep(0.0, 0.9, t) * (1.0 - G.smoothstep(dur - 1.4, dur, t))
        # Heard through steel and insulation: dull.
        x = G.fft_filter(x, lambda f: G.lp_gain(f, 1600, 2))
        _save(x, f"pipe_rush{i}", -24.0, fade_in=0.01, fade_out=0.2)


# =============================================================================
# Loops (every part periodic over the loop length: seamless)
# =============================================================================

def _whole(f, T):
    """A frequency rounded to a whole number of cycles over T seconds (keeps loops seamless)."""
    return max(1.0, round(f * T)) / T


def snd_l2line_steam_leak() -> None:
    """6 s seamless loop: a valve that never quite closes. A steady soft hiss, a faint unsteady
    whistle riding on it, and a low flutter of the valve seat. Kept soft and dark (no sharp top)
    so standing next to it for a while doesn't grate."""
    rng = G.rng_for(20241)
    T = 6.0
    n = int(T * SR)
    t = np.arange(n) / SR
    hiss = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 900, 7000, 2) * G.lp_gain(f, 4500, 1))
    hiss *= G.drift(n, rng, 0.12, 0.2, 0.8)
    fw = _whole(2150.0, T)
    # The whistle wavers a little in pitch (a whole number of wobbles per loop, so it wraps cleanly)
    # and comes and goes.
    whistle = np.sin(2 * np.pi * fw * t + 1.5 * np.sin(2 * np.pi * _whole(0.5, T) * t))
    whistle *= np.clip(G.drift(n, rng, 0.8, 0.15, 0.5) - 0.5, 0.0, None)
    flutter = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 60, 220, 2)) * G.drift(n, rng, 0.4, 0.3, 1.0)
    x = G.norm(hiss) + 0.05 * G.norm(whistle) + 0.18 * G.norm(flutter)
    _save(x, "steam_leak", -30.0, peak=0.5, loop=True)


def snd_l2line_pump_hum() -> None:
    """8 s seamless loop: a pump in a pump bay. A 50 Hz motor hum with its harmonics, the slow
    thump of the piston (10 strokes per loop) with the click of its valve, a faint bearing whine
    and water churning in the casing. Warm and low, like the lamp hums."""
    rng = G.rng_for(20251)
    T = 8.0
    n = int(T * SR)
    t = np.arange(n) / SR
    hum = G.mains_hum(t, f0=50.0, levels_db=(0, -6, -14, -20, -28, -34)) * G.drift(n, rng, 0.06, 0.2, 0.6)
    thump = np.zeros(n)
    stroke = T / 10
    for k in range(10):
        at = k * stroke + rng.uniform(-0.01, 0.01)
        hit = _thud(rng, rng.uniform(48, 58), 0.09, 0.4)
        click = _click(rng, 0.04, 1500, 5000, 0.005)
        hit[:click.size] += 0.15 * click
        G.add_at(thump, (0.85 + 0.15 * rng.uniform()) * hit, at % T, True)
    whine = np.sin(2 * np.pi * _whole(880.0, T) * t) * G.drift(n, rng, 0.6, 0.12, 0.4)
    churn = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 70, 500, 2)) * G.drift(n, rng, 0.3, 0.2, 0.7)
    x = 0.8 * G.norm(hum) + 0.6 * G.norm(thump) + 0.03 * G.norm(whine) + 0.25 * G.norm(churn)
    # Through the casing: almost nothing above ~1.5 kHz.
    x = G.fft_filter(x, lambda f: G.lp_gain(f, 1500, 2), circular=True)
    _save(x, "pump_hum", -28.0, peak=0.6, loop=True)


# =============================================================================
# Drips
# =============================================================================

def snd_l2line_drip() -> None:
    """Five ~0.7 s drops landing in the wet gutter: the tiny tick of impact, the plink of the
    bubble it traps (each a different pitch), sometimes a smaller second drop, and the close
    concrete walls answering."""
    for i, seed in enumerate((20261, 20262, 20263, 20264, 20265), 1):
        rng = G.rng_for(seed)
        dur = 0.35
        x = np.zeros(int(dur * SR))
        G.add_at(x, 0.25 * _click(rng, 0.01, 2500, 9000, 0.0015), 0.0, False)
        G.add_at(x, G.norm(G._water_bubble(rng, rng.uniform(900, 1700), rng.uniform(0.05, 0.09))), 0.002, False)
        if i in (2, 4):
            G.add_at(x, 0.35 * G.norm(G._water_bubble(rng, rng.uniform(1600, 2300), 0.04)), rng.uniform(0.09, 0.16), False)
        x = G.fft_filter(x, lambda f: G.hp_gain(f, 250, 2) * G.lp_gain(f, 7000, 2))
        _save(_corridor(rng, x, wet=0.3, decay=0.28, length=0.6), f"drip{i}", -28.0, fade_out=0.1)


TEXTURES: list = []
SOUNDS = [
    snd_l2line_pipe_knock, snd_l2line_pipe_clank, snd_l2line_pipe_creak, snd_l2line_pipe_rush,
    snd_l2line_steam_leak, snd_l2line_pump_hum, snd_l2line_drip,
]
