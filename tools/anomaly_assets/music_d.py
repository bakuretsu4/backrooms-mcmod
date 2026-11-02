"""Music for Level 7 ("Thalassophobia") and Level 39 ("Megalophobia").

Two ORIGINAL pieces, composed note by note in code like the rest of the soundtrack (liminal, deep
and drift in tools/gen_assets.py, megaflora in level227_audio.py). Nothing is sampled or copied.
They are built on the same parts as those pieces (gen_assets' _MTrack multitrack, its shared tape
wobble, its worn-tape master and _m_save), so all the music sounds like one soundtrack; this file
only adds the instruments these two places need.

  music/level7   ~2.6 min  "Thalassophobia": a flooded room hanging over an endless sea with
                           something enormous below. Huge low drones, slow waves, far whale-like
                           calls, sonar pings, and once a brass cluster rising from the deep.
  music/level39  ~2.9 min  "Megalophobia": a little town's waltz on an old upright piano, heard
                           from impossibly far away across a colossal ringing hall, interrupted
                           by the steel superstructure groaning.

Loudness: _m_save scales a piece so its loudest sample sits at a chosen peak. Here that peak is
chosen so each piece lands at -16 LUFS, the average of liminal, deep and megaflora (measured as
stereo integrated loudness, EBU R128), and never above -1.5 dBFS. If a piece is too spiky to get
there under that ceiling, a gentle limiter rounds off its few loudest moments first.

Run only these with:  python tools/gen_assets.py musicd_
Each piece uses its own fixed random seed (70700, 39390), so re-running gives identical files.
"""
from __future__ import annotations

import numpy as np

import gen_assets as G
from anomaly_assets import level2_audio as A

SR = G.SR

# Average stereo integrated loudness of music/liminal, deep and megaflora (-16.8, -16.3, -14.8).
TARGET_LUFS = -16.0
# Highest sample allowed (about -1.5 dBFS). _m_save accepts up to +0.03 of codec overshoot, which
# still keeps the decoded file under -1 dBFS.
CEILING = 0.84


# =============================================================================
# Shared helpers
# =============================================================================

def _ease(t, pts) -> np.ndarray:
    """A curve through (time, value) points that eases in and out of each point (a half cosine
    between neighbours), so pitch glides and swells start and stop smoothly instead of with a
    kink like a straight-line interpolation would have."""
    times = np.array([p[0] for p in pts], float)
    vals = np.array([p[1] for p in pts], float)
    i = np.clip(np.searchsorted(times, t, side="right") - 1, 0, len(times) - 2)
    u = np.clip((t - times[i]) / (times[i + 1] - times[i]), 0, 1)
    s = 0.5 - 0.5 * np.cos(np.pi * u)
    return vals[i] + (vals[i + 1] - vals[i]) * s


def _pan(x, pan: float):
    """Equal-power pan: -1 is hard left, +1 hard right, and the loudness stays the same in between."""
    return x * np.sqrt(0.5 * (1 - pan)), x * np.sqrt(0.5 * (1 + pan))


def _tail(x, sec: float = 0.04) -> np.ndarray:
    """Fades out the last few hundredths of a second of a sound, so that cutting it off at the end
    of its buffer can never click (even a ring that is already very quiet would)."""
    m = min(len(x), int(sec * SR))
    x[len(x) - m:] *= np.linspace(1, 0, m)
    return x


def _filter_bus(tr, name: str, gain_fn) -> None:
    """Runs one whole bus (both channels) through a filter, e.g. to make it sound far away."""
    l, r = tr.buses[name]
    tr.buses[name] = (G.fft_filter(l, gain_fn), G.fft_filter(r, gain_fn))


def _stereo_lufs(x) -> float:
    """Integrated loudness (EBU R128) of a stereo (n, 2) signal: the same meter as
    level2_audio.lufs, but adding both channels' power as the standard says, so it matches how the
    existing pieces were measured."""
    x = np.concatenate([np.asarray(x, float), np.zeros((SR // 2, x.shape[1]))])
    y = sum(A._k_weight(x[:, c]) ** 2 for c in range(x.shape[1]))
    block, hop = int(0.4 * SR), int(0.1 * SR)
    acc = np.concatenate([[0.0], np.cumsum(y)])
    starts = np.arange(0, len(y) - block + 1, hop)
    z = (acc[starts + block] - acc[starts]) / block
    z = z[-0.691 + 10 * np.log10(z + 1e-20) > -70.0]                     # absolute gate
    rel = -0.691 + 10 * np.log10(z.mean()) - 10.0                       # relative gate
    z = z[-0.691 + 10 * np.log10(z) > rel]
    return float(-0.691 + 10 * np.log10(z.mean()))


def _limit(x, ceiling: float) -> np.ndarray:
    """A gentle look-ahead limiter for a stereo (n, 2) signal: wherever a peak would pass
    `ceiling`, the volume dips smoothly over ~50 ms around it (starting before the peak arrives)
    instead of the wave being clipped, so nothing crackles or distorts."""
    blk = 256
    n = len(x)
    nb = -(-n // blk)
    peaks = np.pad(np.abs(x).max(axis=1), (0, nb * blk - n)).reshape(nb, blk).max(axis=1)
    g = np.minimum(1.0, ceiling / np.maximum(peaks, 1e-9))
    # Spread every dip to the blocks around it, then smooth: the gain is already down when the
    # peak arrives, and every value near a peak is at most that peak's gain, so smoothing can't
    # let it through.
    w = 9
    spread = g.copy()
    for k in range(1, w + 1):
        spread[:-k] = np.minimum(spread[:-k], g[k:])
        spread[k:] = np.minimum(spread[k:], g[:-k])
    smooth = np.convolve(np.pad(spread, w, mode="edge"), np.ones(2 * w + 1) / (2 * w + 1), mode="valid")
    gain = np.interp(np.arange(n), np.arange(nb) * blk + blk / 2, smooth)
    return x * gain[:, None]


def _save_music(L, R, path, fade_in: float = 0.5, fade_out: float = 7.0) -> None:
    """Writes a piece with gen_assets._m_save (the soundtrack's shared ending: fades, then Ogg),
    choosing its peak level so the result lands at TARGET_LUFS. The fades are applied here only to
    MEASURE what _m_save will produce; _m_save then applies them for real.

    Then the written file is measured. The Ogg encoder can overshoot the peak a little, and when it
    does _m_save turns the whole file down, which leaves it quieter than planned; if that happened,
    the shortfall is added on and the piece written again (limiting a little more if needed)."""
    x = np.stack([L, R], axis=1)
    x -= x.mean(axis=0)

    def peak_for(sig):
        y = sig / np.abs(sig).max()
        fi, fo = int(fade_in * SR), int(fade_out * SR)
        y = y.copy()
        y[:fi] *= np.linspace(0, 1, fi)[:, None]
        y[-fo:] *= (np.linspace(1, 0, fo) ** 2)[:, None]
        y = y / np.abs(y).max()
        return 10 ** ((TARGET_LUFS - _stereo_lufs(y)) / 20)

    shortfall = 0.0                  # dB the encoder's overshoot check took off last time
    for _ in range(5):
        peak = peak_for(x) * 10 ** (shortfall / 20)
        for _ in range(30):          # too spiky: round off the loudest moments, a little at a time
            if peak <= CEILING:
                break
            x = _limit(x / np.abs(x).max(), 0.9)
            peak = peak_for(x) * 10 ** (shortfall / 20)
        G._m_save(x[:, 0], x[:, 1], path, peak=min(peak, CEILING), fade_in=fade_in, fade_out=fade_out)
        got = _stereo_lufs(G.sf.read(str(path))[0])
        if abs(got - TARGET_LUFS) <= 0.25:
            break
        shortfall += TARGET_LUFS - got


# =============================================================================
# Level 7: "Thalassophobia"
# =============================================================================

def _wave_envelope(rng, n: int):
    """The slow breathing of the sea: one wave every 6 to 8 seconds (each a little different in
    length and size). Returns (swell, wash): swell rises slowly and peaks 60% of the way through a
    wave (the water heaving up), wash peaks a moment later (the crest breaking and running off).
    Both are exactly zero between waves, so a wave can change size without a jump."""
    t = np.arange(n) / SR
    edges = [-rng.uniform(0, 7)]
    while edges[-1] < t[-1] + 8:
        edges.append(edges[-1] + rng.uniform(6.0, 8.0))
    edges = np.array(edges)
    size = rng.uniform(0.6, 1.0, len(edges))
    i = np.clip(np.searchsorted(edges, t, side="right") - 1, 0, len(edges) - 2)
    phase = np.clip((t - edges[i]) / (edges[i + 1] - edges[i]), 0, 1)
    swell = np.sin(np.pi * phase ** 1.357) ** 2 * size[i]      # 0.5 ** (1 / 1.357) = 0.6: the crest
    wash = np.sin(np.pi * np.clip((phase - 0.5) / 0.5, 0, 1)) ** 2 * size[i]
    return swell, wash


def _sea(tr, bus: str, swell_lr, wash_lr, level) -> None:
    """The open sea under the room: for each ear, a deep rumble that never quite stops, a body of
    rushing water that rises with every swell, a brighter crest at the top of the big ones, and a
    soft hiss as the crest runs off. Separate noise per ear so it is as wide as the horizon."""
    rng = tr.rng
    chans = []
    for swell, wash in zip(swell_lr, wash_lr):
        def band(lo, hi):
            nz = G.fft_filter(rng.normal(size=tr.n), lambda f: G.bp_gain(f, lo, hi, 2))
            return nz / (nz.std() + 1e-12)
        drift = 1 + G.smooth_jitter(rng, tr.n, 0.2, 0.25)
        x = (0.55 * band(35, 180) * (0.35 + 0.65 * swell)
             + 0.55 * band(150, 650) * swell
             + 0.30 * band(500, 2000) * swell ** 2
             + 0.10 * band(1800, 5500) * wash)
        chans.append(x * level * drift)
    tr.add(bus, 0.0, chans[0], chans[1])


def _abyss(tr, bus: str, start: float, dur: float, note: str, vel: float, fade: float,
           pressure=None, cutoff: float = 260.0) -> None:
    """A drone from the bottom of the sea. Its fundamental (31-46 Hz) is felt more than heard, so it
    carries a stack of harmonics up to a few hundred Hz that small speakers can play; a twin tuned
    a tenth of a hertz higher makes the whole mass pulse very slowly. `pressure` (the sea's swell)
    lets the waves push on it a little, like water pressing on your ears."""
    rng = tr.rng
    f = G._m_hz(note)
    n = int(dur * SR)
    t = np.arange(n) / SR
    ratio = tr._ratio(start, n)
    tab = G._m_table(14, 0.85)
    voices = []
    for df in (0.0, 0.11):
        ph = (rng.uniform() + np.cumsum((f + df) * ratio) / SR) % 1.0
        v = np.interp(ph * 4096, np.arange(4097), tab)
        voices.append(G.fft_filter(v, lambda fr: G.lp_gain(fr, cutoff, 2)))
    e = np.clip(t / fade, 0, 1) * np.clip((dur - t) / fade, 0, 1)
    e = e * e * (3 - 2 * e) * vel
    if pressure is not None:
        i0 = int(round(start * SR))
        p = pressure[i0:i0 + n]
        e = e * (0.78 + 0.22 * np.pad(p, (0, n - len(p))))
    a, b = voices
    tr.add(bus, start, (a + 0.6 * b) * e, (0.6 * a + b) * e)


def _whale(tr, bus: str, start: float, pts, vel: float, pan: float = 0.0) -> None:
    """A whale-like call from very far off: a slow, hollow moan whose pitch glides through the
    given (seconds, note) points, with a slight waver in the middle of the call. It is a sung
    vowel-like tone (harmonics shaped by two low resonances, like 'oo' through a long throat), not
    an imitation of a real recording."""
    rng = tr.rng
    dur = pts[-1][0]
    n = int(dur * SR)
    t = np.arange(n) / SR
    logf = _ease(t, [(s, np.log(G._m_hz(note))) for s, note in pts])
    waver = 0.005 * np.sin(2 * np.pi * 2.7 * t) * np.sin(np.pi * t / dur) ** 2
    f0 = np.exp(logf) * (1 + waver + G.smooth_jitter(rng, n, 0.8, 0.003)) * tr._ratio(start, n)
    v = G.voice(f0, [(360, 90, 1.0), (820, 150, 0.45), (1500, 220, 0.1)], rng, nh=18, tilt=0.15)
    env = _ease(t, [(0, 0.0), (min(1.6, dur * 0.25), 1.0), (dur * 0.7, 0.85), (dur, 0.0)])
    x = G.norm(v) * env * vel
    tr.add(bus, start, *_pan(x, pan))


def _ping(tr, bus: str, at: float, note: str, vel: float, gap: float = 1.75, echoes: int = 6,
          fall: float = 0.55, pan: float = -0.3) -> None:
    """A sonar ping: one pure, soft-edged tone, then its echoes coming back off something far
    below, each fainter, duller and more smeared than the last (it has travelled further), and
    bouncing between the ears."""
    rng = tr.rng
    f = G._m_hz(note)
    n = int(2.4 * SR)
    t = np.arange(n) / SR
    ratio = tr._ratio(at, n)
    ph = np.cumsum(f * ratio * (1 - 0.004 * (1 - np.exp(-t / 0.5)))) / SR   # sags a little as it dies
    x = np.sin(2 * np.pi * ph) + 0.06 * np.sin(4 * np.pi * ph)
    x *= (0.5 - 0.5 * np.cos(np.pi * np.clip(t / 0.008, 0, 1))) * np.exp(-t / 0.45) * vel
    x = _tail(x)
    for k in range(echoes + 1):
        y = x
        if k:
            smear = rng.normal(size=int(0.04 * k * SR)) * np.exp(-np.arange(int(0.04 * k * SR)) / (0.012 * k * SR))
            y = G.conv(x, smear / np.sqrt((smear ** 2).sum()))[:n]
            y = G.fft_filter(y, lambda fr: G.lp_gain(fr, 3200 / (1 + 0.8 * k), 1))
        side = pan * (-1) ** k * min(1.0, 0.6 + 0.15 * k)
        tr.add(bus, at + k * gap + rng.uniform(-0.03, 0.03), *_pan(y * fall ** k, side))


def _deep_brass(tr, bus: str, start: float, dur: float, note: str, vel: float, rise: float = 3.0,
                sink: float = 5.0, pan: float = 0.0) -> None:
    """One player of the brass cluster that rises out of the deep. A low horn-like tone (a full
    harmonic series, two slightly detuned players) that starts `rise` semitones flat and almost
    silent, slides up into tune as it swells, holds, then sinks: slides `sink` semitones down while
    fading. Like real brass, it gets brighter the louder it plays (more upper harmonics at the
    top of the swell), so the swell sounds like effort, not a volume knob."""
    rng = tr.rng
    f = G._m_hz(note)
    n = int(dur * SR)
    t = np.arange(n) / SR
    semi = _ease(t, [(0, -rise), (dur * 0.38, 0.0), (dur * 0.6, 0.0), (dur, -sink)])
    env = _ease(t, [(0, 0.0), (dur * 0.4, 1.0), (dur * 0.58, 1.0), (dur, 0.0)])
    freq = f * 2 ** (semi / 12) * tr._ratio(start, n)
    nh = int(min(26, 2600 / f))
    x = np.zeros(n)
    for cents in (-5.0, 4.0):
        ph = np.cumsum(freq * 2 ** (cents / 1200)) / SR
        for k in range(1, nh + 1):
            amp = np.exp(-(k - 1) / (1.2 + 8.0 * env)) / k ** 0.6
            x += amp * np.sin(2 * np.pi * k * ph + rng.uniform(0, 6.3))
    breath = G.fft_filter(rng.normal(size=n), lambda fr: G.bp_gain(fr, 300, 1400, 2))
    x = G.norm(x) + 0.03 * breath / (breath.std() + 1e-12) * env
    tr.add(bus, start, *_pan(x * env * vel, pan))


def _bubbles(tr, bus: str, at: float, count: int, spread: float, vel: float) -> None:
    """A trail of bubbles rising past the room: tiny upward chirps close together, each a little
    higher and quieter (further up) than the one before."""
    rng = tr.rng
    times = np.sort(rng.uniform(0, spread, count))
    for i, s in enumerate(times):
        n = int(0.07 * SR)
        t = np.arange(n) / SR
        f0 = rng.uniform(320, 620) * (1 + 0.6 * i / count)
        ph = np.cumsum(f0 * (1 + 1.6 * t / 0.07)) / SR
        x = _tail(np.sin(2 * np.pi * ph) * np.exp(-t / 0.018) * np.clip(t / 0.003, 0, 1))
        tr.add(bus, at + s, *_pan(x * vel * (1 - 0.5 * i / count), rng.uniform(-0.7, 0.7)))


def snd_musicd_level7() -> None:
    """~2.6 minutes for Level 7, "Thalassophobia": the fear of deep water. B minor, but there is no
    beat: the piece moves in sea waves, one every 6 to 8 seconds (about 8.5 a minute), and the
    sonar's echoes come back 1.75 s apart.

    Opening (0:00-0:42): a drone on B0 (31 Hz) rises out of nothing under the waves; a sonar ping
    goes out and its echoes come back from something far below. Middle (0:42-1:36): a fifth (F#1)
    joins the drone, dark low chords drift past two waves at a time (B minor add9, G major 7,
    E minor add9, F# with a flat ninth), and whale-like calls answer each other from the distance.
    The rise (1:36-2:08): the drone's fifth slips to C1, a semitone against the B (the water itself
    seems to groan), the biggest waves of the piece roll in and a cluster of low brass (B, C, D and
    F, with B and C doubled an octave up) rises from the deep, flat and growing brighter, holds,
    and sinks back.
    After (2:08-2:36): one last call far below sliding down into the dark, a lower, slower ping
    whose echoes never come back, bubbles, and the B drone alone fading out."""
    total = 156.0
    tr = G._MTrack(total, seed=70700, wow=0.004, flutter=0.0006)
    rng = tr.rng

    # The sea: the waves set the time of the whole piece. Their size follows the story:
    # quiet at first, growing through the middle, biggest under the brass, then receding.
    swell_l, wash_l = _wave_envelope(rng, tr.n)
    # Same waves for the right ear (one sea), only 0.35 s later.
    t = np.arange(tr.n) / SR
    shift = int(0.35 * SR)
    swell_r = np.concatenate([np.zeros(shift), swell_l[:-shift]])
    wash_r = np.concatenate([np.zeros(shift), wash_l[:-shift]])
    level = np.interp(t, [0, 8, 40, 70, 98, 112, 126, 140, 156],
                      [0.0, 0.22, 0.45, 0.55, 0.7, 1.0, 0.65, 0.4, 0.3])
    _sea(tr, "sea", (swell_l, swell_r), (wash_l, wash_r), level)

    # The deep: B0 throughout; a fifth above it in the middle, which slides to a semitone (C1)
    # when the brass rises.
    _abyss(tr, "drone", 0.0, 152.0, "B0", 0.55, fade=16.0, pressure=swell_l)
    _abyss(tr, "drone", 40.0, 62.0, "F#1", 0.30, fade=12.0, pressure=swell_r)
    _abyss(tr, "drone", 94.0, 40.0, "C1", 0.34, fade=10.0, pressure=swell_r, cutoff=320.0)

    # Sonar: pings with their echoes. The last one is lower and slower, and fades with nothing
    # coming back (fall 0.25: the echoes are swallowed).
    for at, vel, side in ((17.0, 0.22, -0.3), (31.0, 0.18, 0.35), (55.0, 0.2, -0.2), (86.0, 0.16, 0.3)):
        _ping(tr, "ping", at, "B5", vel, pan=side)
    _ping(tr, "ping", 138.0, "B4", 0.22, gap=2.6, echoes=3, fall=0.25, pan=0.2)

    # Whale-like calls far away: the first two answer each other, the last one sinks into the dark.
    _whale(tr, "whale", 44.0, [(0, "D3"), (2.6, "F#3"), (5.2, "E3"), (8.0, "C#3")], 0.5, pan=-0.45)
    _whale(tr, "whale", 70.0, [(0, "B2"), (3.0, "E3"), (5.5, "D3"), (6.8, "D3"), (9.0, "A2")], 0.45, pan=0.5)
    _whale(tr, "whale", 128.0, [(0, "F#2"), (2.0, "A2"), (6.0, "D2"), (10.5, "B1")], 0.55, pan=-0.15)

    # Dark chords two waves (14 s) each, low and slow, as if felt through the water.
    chords = [["B2", "F#3", "C#4", "D4"], ["G2", "D3", "F#3", "B3"],
              ["E2", "B2", "F#3", "G3"], ["F#2", "C#3", "E3", "G3"]]
    for i, ch in enumerate(chords):
        tr.chord("pad", 49.0 + 14.0 * i, 13.0, ch, 0.2, bright=700, attack=5.0, release=6.0, spread=0.6)

    # The brass cluster: the players enter one after another, the lowest first, each sliding up
    # into the cluster; they all sink back together.
    cluster = [("B1", 0.0, -0.3), ("C2", 1.5, 0.3), ("D2", 3.0, -0.1), ("F2", 4.0, 0.15),
               ("B2", 5.5, -0.4), ("C3", 7.0, 0.4)]
    for note, delay, side in cluster:
        _deep_brass(tr, "brass", 96.0 + delay, 34.0 - delay, note, 0.22, rise=3.0 + delay * 0.2, sink=5.0, pan=side)

    _bubbles(tr, "bubble", 26.0, 9, 1.6, 0.12)
    _bubbles(tr, "bubble", 61.0, 6, 1.2, 0.1)
    _bubbles(tr, "bubble", 133.0, 12, 2.2, 0.12)
    _bubbles(tr, "bubble", 146.0, 7, 1.5, 0.08)

    # Underwater, the highs die first: everything that is "in" the sea is dulled.
    _filter_bus(tr, "whale", lambda f: G.bp_gain(f, 70, 1100, 2))
    _filter_bus(tr, "pad", lambda f: G.lp_gain(f, 800, 2))
    _filter_bus(tr, "brass", lambda f: G.lp_gain(f, 1700, 2))
    _filter_bus(tr, "bubble", lambda f: G.lp_gain(f, 2200, 2))
    L, R = tr.master(mix={"sea": 0.11, "drone": 1.0, "ping": 0.3, "whale": 0.13, "pad": 0.55,
                          "brass": 0.75, "bubble": 0.3},
                     reverb={"ping": 0.9, "whale": 1.0, "pad": 0.5, "brass": 0.55, "bubble": 0.7,
                             "sea": 0.15, "drone": 0.05},
                     room_s=9.0, room_lp=2200.0, lp=5500.0, drive=1.25)
    _save_music(L, R, G.SND / "music" / "level7.ogg", fade_out=8.0)


# =============================================================================
# Level 39: "Megalophobia"
# =============================================================================

def _upright(tr, bus: str, at: float, note: str, vel: float, hold: float, pan: float = 0.0) -> None:
    """One note of an old upright piano. Each note is two strings (one a little out of tune, as
    on a piano nobody has tuned in years, which makes it shimmer), struck by a felt hammer: the
    harder the note, the more upper harmonics. The partials are very slightly stretched (stiff
    steel strings), the high ones die away first, the sound decays in two stages like a real
    piano (a fast first drop, then a long quiet ring), and the damper stops it when the key is let
    go after `hold` seconds."""
    rng = tr.rng
    f = G._m_hz(note)
    n = int((hold + 0.5) * SR)
    t = np.arange(n) / SR
    ratio = tr._ratio(at, n)
    stiff = 0.00045
    tau0 = float(np.clip(2.4 * (261.6 / f) ** 0.6, 0.7, 6.0))
    bright = 2.5 + 8.0 * vel
    x = np.zeros(n)
    detune = (-1.0, 1.5 + rng.uniform(0, 3.0))
    for k in range(1, int(min(14, 4200 / f)) + 1):
        fk = f * k * np.sqrt(1 + stiff * k * k)
        amp = np.exp(-(k - 1) / bright) / k ** 0.5 * abs(np.sin(np.pi * k * 0.12))   # hammer near 1/8
        tau = tau0 / (1 + 0.35 * (k - 1))
        decay = 0.72 * np.exp(-t / tau) + 0.28 * np.exp(-t / (3.5 * tau))
        for c in detune:
            ph = np.cumsum(fk * 2 ** (c / 1200) * ratio) / SR + rng.uniform()
            x += amp * decay * np.sin(2 * np.pi * ph)
    x *= np.clip(t / 0.002, 0, 1) * np.where(t < hold, 1.0, np.exp(-(t - hold) / 0.12))
    thump = G.fft_filter(rng.normal(size=n), lambda fr: G.bp_gain(fr, 60, 1200, 2)) * np.exp(-t / 0.008)
    x = _tail(x / 6.0 + 0.15 * thump / (np.abs(thump).max() + 1e-9))
    tr.add(bus, at, *_pan(x * vel, pan))


# The waltz: 16 bars in E-flat major, 3/4. Each bar is (left hand: bass note on beat 1 and the
# chord played on beats 2 and 3, the "oom-pah-pah"), then the melody as (beat, note, length in
# beats). Bars 1-8: Eb Eb Ab Eb Cm F7 Bb7 Eb. Bars 9-16 borrow A-flat MINOR (the C-flat) for a
# moment of homesickness: Ab Abm Eb/Bb C7 Fm Bb7 Eb Eb.
_WALTZ = [
    (("Eb2", ["G3", "Bb3", "Eb4"]), [(0, "Bb4", 1), (1, "Eb5", 1), (2, "G5", 1)]),
    (("Eb2", ["G3", "Bb3", "Eb4"]), [(0, "Bb5", 2), (2, "G5", 1)]),
    (("Ab1", ["Ab3", "C4", "Eb4"]), [(0, "Ab5", 1), (1, "C6", 1), (2, "Ab5", 1)]),
    (("Eb2", ["G3", "Bb3", "Eb4"]), [(0, "G5", 3)]),
    (("C2", ["G3", "C4", "Eb4"]), [(0, "G5", 1), (1, "F5", 1), (2, "Eb5", 1)]),
    (("F2", ["A3", "C4", "Eb4"]), [(0, "C5", 2), (2, "F5", 1)]),
    (("Bb1", ["Ab3", "D4", "F4"]), [(0, "D5", 1), (1, "Eb5", 1), (2, "F5", 1)]),
    (("Eb2", ["G3", "Bb3", "Eb4"]), [(0, "Eb5", 3)]),
    (("Ab1", ["Ab3", "C4", "Eb4"]), [(0, "C6", 2), (2, "Bb5", 1)]),
    (("Ab1", ["Ab3", "Cb4", "Eb4"]), [(0, "Cb6", 2), (2, "Ab5", 1)]),
    (("Bb1", ["G3", "Bb3", "Eb4"]), [(0, "G5", 2), (2, "Eb5", 1)]),
    (("C2", ["G3", "Bb3", "E4"]), [(0, "E5", 1), (1, "G5", 1), (2, "Bb5", 1)]),
    (("F2", ["Ab3", "C4", "F4"]), [(0, "Ab5", 3)]),
    (("Bb1", ["Ab3", "D4", "F4"]), [(0, "F5", 1), (1, "G5", 1), (2, "Ab5", 1)]),
    (("Eb2", ["G3", "Bb3", "Eb4"]), [(0, "G5", 2), (2, "F5", 1)]),
    (("Eb2", ["G3", "Bb3", "Eb4"]), [(0, "Eb5", 3)]),
]
# Where each beat falls inside the bar, in beats: the second beat comes a touch early, the lilt
# of a waltz played by someone who has played it a thousand times.
_LILT = (0.0, 0.93, 2.0)


def _play_waltz(tr, bus: str, start: float, bars, bpm: float, left_bars=None, drop: float = 0.0,
                rit: float = 0.15, vel: float = 1.0) -> float:
    """Plays the given bars of the waltz on the upright from `start` at `bpm`, slowing down over
    the last bar (`rit`). `left_bars`: the bars (by position in `bars`) whose left hand is played
    (default all); `drop`: the chance that a melody note goes missing (lost in the distance).
    Returns the time the last bar ends."""
    rng = tr.rng
    at = start
    for i, b in enumerate(bars):
        (bass, chord), melody = _WALTZ[b]
        beat = 60.0 / bpm * (1 + rit * (i == len(bars) - 1))
        if left_bars is None or i in left_bars:
            _upright(tr, bus, at + rng.uniform(0, 0.015), bass, 0.42 * vel, 2.6 * beat, pan=-0.15)
            for k in (1, 2):
                for j, nt in enumerate(chord):
                    _upright(tr, bus, at + _LILT[k] * beat + j * 0.007 + rng.uniform(0, 0.012), nt,
                             0.25 * vel, 0.5 * beat, pan=-0.05 + 0.05 * j)
        for b0, nt, length in melody:
            if rng.uniform() < drop:
                continue
            _upright(tr, bus, at + _LILT[b0] * beat + rng.uniform(-0.012, 0.012), nt,
                     0.55 * vel * rng.uniform(0.9, 1.0), length * beat * 0.95, pan=0.15)
        at += 3 * beat
    return at


def _hall_ir(rng, seconds: float = 15.0, predelay: float = 0.32) -> np.ndarray:
    """The echo of the colossal hall, one ear. It is so big that the first echo (off the far
    walls) only arrives a third of a second after the sound, then a few distinct reflections off
    the facades, then a tail that rings for ~11 seconds; the high frequencies die away much
    sooner than the lows, as in a real cathedral (air and stone swallow the highs)."""
    n = int(seconds * SR)
    t = np.arange(n) / SR
    ir = np.zeros(n)
    for gain_fn, tau in ((lambda f: G.lp_gain(f, 450, 2), 1.75),
                         (lambda f: G.bp_gain(f, 450, 1600, 2), 1.45),
                         (lambda f: G.bp_gain(f, 1600, 5000, 2), 0.7)):
        ir += G.fft_filter(rng.normal(size=n), gain_fn) * np.exp(-t / tau)
    ir *= 1 - np.exp(-t / 0.35)                 # the echo gets denser as it spreads through the hall
    ir = np.concatenate([np.zeros(int(predelay * SR)), ir])[:n]
    scale = np.abs(ir).max()
    for dt, a in ((0.0, 0.55), (0.14, 0.4), (0.31, 0.33), (0.55, 0.25), (0.84, 0.18)):
        i0 = int((predelay + dt + rng.uniform(-0.02, 0.02)) * SR)
        m = int(0.03 * SR)
        burst = G.fft_filter(rng.normal(size=m) * np.exp(-np.arange(m) / (0.006 * SR)),
                             lambda f: G.lp_gain(f, 2500, 1))
        ir[i0:i0 + m] += a * scale * 3.0 * burst / (np.abs(burst).max() + 1e-9)
    return ir / (np.sqrt((ir ** 2).sum()) + 1e-12)


def _in_hall(tr, name: str, irs, dry: float, wet: float) -> None:
    """Puts a whole bus into the hall: a little of the sound itself plus a lot of its echo."""
    l, r = tr.buses[name]
    tr.buses[name] = (dry * l + wet * G.conv(l, irs[0])[: tr.n],
                      dry * r + wet * G.conv(r, irs[1])[: tr.n])


def _girder_groan(tr, bus: str, start: float, dur: float, f_pts, vel: float, pan: float = 0.0) -> None:
    """The superstructure groaning: a steel girder the size of a street bending under its own
    weight. Its base pitch follows f_pts ((seconds, Hz), sliding as the load shifts), and it
    sounds at a steel bar's bending modes (1, 2.76, 5.40, 8.93, 13.3 times the base, not whole
    multiples), which is what makes it ring metallic rather than wooden. A slow stick-slip grind
    (steel creeping at a joint) drives it, so it surges and grinds instead of humming; each mode
    keeps ringing for a while after the grinding stops, the low ones longest."""
    from scipy.signal import lfilter
    rng = tr.rng
    n = int(dur * SR)
    t = np.arange(n) / SR
    base = _ease(t, f_pts) * tr._ratio(start, n)
    rate = _ease(t, [(0, 6.0), (dur * 0.5, 13.0), (dur, 5.0)]) * (1 + G.smooth_jitter(rng, n, 1.0, 0.3))
    grind = (0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(rate) / SR)) ** 3
    grind = G.fft_filter(grind, lambda f: G.lp_gain(f, 30, 2))
    drive = _ease(t, [(0, 0.0), (dur * 0.25, 1.0), (dur * 0.5, 0.8), (dur * 0.62, 1.0), (dur * 0.75, 0.0),
                      (dur, 0.0)])
    drive = drive * (0.55 + 0.45 * grind) * (1 + G.smooth_jitter(rng, n, 0.7, 0.3))
    x = np.zeros(n)
    for ratio_k, amp, ring in ((1.0, 1.0, 2.6), (2.756, 0.7, 1.8), (5.404, 0.38, 1.1),
                               (8.933, 0.2, 0.7), (13.34, 0.1, 0.45)):
        a = np.exp(-1.0 / (ring * SR))
        energy = lfilter([1 - a], [1, -a], drive)          # the mode fills up and rings out slowly
        for beat in (0.0, 0.0023):                         # each mode a close pair: a slow metal shimmer
            ph = np.cumsum(base * ratio_k * (1 + beat)) / SR + rng.uniform()
            x += amp * energy * np.sin(2 * np.pi * ph)
    rasp = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 120, 700, 2))
    x = G.norm(x) + 0.06 * rasp / (rasp.std() + 1e-12) * drive * grind
    x *= np.clip((dur - t) / 0.5, 0, 1)
    tr.add(bus, start, *_pan(x * vel, pan))


def _girder_tick(tr, bus: str, at: float, hz: float, vel: float, pan: float = 0.0) -> None:
    """The hall settling after a groan: a huge girder somewhere far up ticks once, a soft low
    metallic knock (bar modes again) that rings on in the hall."""
    n = int(6.0 * SR)
    t = np.arange(n) / SR
    x = sum(a * np.sin(2 * np.pi * hz * r * t + tr.rng.uniform(0, 6.3)) * np.exp(-t / d)
            for r, a, d in ((1.0, 1.0, 2.2), (2.756, 0.5, 1.2), (5.404, 0.25, 0.6), (8.933, 0.1, 0.3)))
    x = _tail(x * (0.5 - 0.5 * np.cos(np.pi * np.clip(t / 0.015, 0, 1))), 0.3)   # soft edge: a knock, not a hit
    tr.add(bus, at, *_pan(G.norm(x) * vel, pan))


def snd_musicd_level39() -> None:
    """~2.9 minutes for Level 39, "Megalophobia": a small town inside a hall so big it has weather.
    E-flat major, a waltz in 3/4 at 76 beats a minute (slowing to 72, 70 and 66 as it goes),
    played on an old upright piano that is somewhere down in the toy-like town, impossibly far
    away: every note reaches you a third of a second late and rings for ten seconds in the hall.

    1 (0:05): the first eight bars of the waltz, pastoral and a little out of tune.
    2 (0:33): the superstructure groans: a steel girder the size of a street bends, low and
       metallic, and the whole hall rings with it. The town falls silent.
    3 (0:50): the second half of the waltz (with its moment of A-flat minor), slower and farther.
    4 (1:19): a bigger, longer groan; afterwards the hall settles, girders ticking far above.
    5 (1:44): the waltz's opening again, even farther: the left hand stops after four bars and
       melody notes go missing.
    6 (2:04): the deepest groan, sliding down, and the longest ring of the hall.
    7 (2:31): two bars of the ending heard from the marble tunnels below, muffled and dry (the
       tunnels swallow the echo), stopping on the dominant (B-flat 7) and one melody note, G, that
       never gets its chord. Then only the air of the hall."""
    total = 172.0
    tr = G._MTrack(total, seed=39390, wow=0.0045, flutter=0.0008)
    rng = tr.rng

    _play_waltz(tr, "town1", 5.0, range(0, 8), 76.0)
    _play_waltz(tr, "town2", 50.0, range(8, 16), 72.0)
    _play_waltz(tr, "town3", 104.0, range(0, 8), 70.0, left_bars=range(0, 4), drop=0.3, vel=0.9)
    end = _play_waltz(tr, "tunnel", 151.0, (12, 13), 66.0, rit=0.1)
    # The note that stays alone. Played harder than the rest: the tunnel's muffling takes most of a
    # note this high, and it should still be the last thing you hear.
    _upright(tr, "tunnel", end + 0.25, "G5", 1.0, 3.0, pan=0.15)

    _girder_groan(tr, "groan", 33.0, 12.0, [(0, 44.0), (6, 41.0), (12, 38.5)], 0.85, pan=-0.2)
    _girder_groan(tr, "groan", 79.0, 15.0, [(0, 40.0), (5, 34.5), (10, 36.5), (15, 33.0)], 1.0, pan=0.25)
    _girder_groan(tr, "groan", 124.0, 16.0, [(0, 38.0), (8, 33.0), (16, 29.5)], 1.0, pan=0.0)
    for at, hz, vel, side in ((95.0, 92.0, 0.35, 0.6), (98.5, 71.0, 0.3, -0.5), (101.5, 104.0, 0.22, 0.3)):
        _girder_tick(tr, "tick", at, hz, vel, pan=side)

    # The hall's own air: a deep, slow movement of air through a space miles across, and the
    # faintest high hiss of it. It swells a little with each groan (the structure moves the air).
    tr.noise_bed("air", 30, 260, [(0, 0.0), (6, 0.02), (33, 0.02), (40, 0.035), (60, 0.02), (79, 0.02),
                                  (88, 0.042), (110, 0.02), (124, 0.02), (135, 0.045), (148, 0.022),
                                  (172, 0.014)], drift_amt=0.45)
    tr.noise_bed("air", 2500, 7000, [(0, 0.0), (8, 0.003), (172, 0.003)], drift_amt=0.4)

    # Distance: the further away the town gets, the more of its high end the air swallows; the
    # tunnel takes nearly all of it.
    _filter_bus(tr, "town1", lambda f: G.bp_gain(f, 170, 2600, 2))
    _filter_bus(tr, "town2", lambda f: G.bp_gain(f, 170, 2000, 2))
    _filter_bus(tr, "town3", lambda f: G.bp_gain(f, 190, 1500, 2))
    _filter_bus(tr, "tunnel", lambda f: G.bp_gain(f, 120, 480, 2))
    _filter_bus(tr, "groan", lambda f: G.lp_gain(f, 1500, 2))
    irs = (_hall_ir(rng), _hall_ir(rng))
    for name, dry, wet in (("town1", 0.18, 1.0), ("town2", 0.12, 1.0), ("town3", 0.08, 1.0),
                           ("groan", 0.5, 0.9), ("tick", 0.15, 1.0)):
        _in_hall(tr, name, irs, dry, wet)
    L, R = tr.master(mix={"town1": 1.0, "town2": 1.0, "town3": 1.0, "tunnel": 1.8, "groan": 0.42,
                          "tick": 0.5, "air": 1.0},
                     reverb={}, room_s=4.0, lp=6000.0, drive=1.25)
    _save_music(L, R, G.SND / "music" / "level39.ogg", fade_out=9.0)


TEXTURES: list = []
SOUNDS = [snd_musicd_level7, snd_musicd_level39]
