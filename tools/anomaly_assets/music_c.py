"""Music, group C: two ORIGINAL pieces for the bright, watery levels, composed note by note in code
(nothing is sampled or copied), in the same "worn tape" sound as the main theme.

  music/poolrooms   "Chlorine"   the Poolrooms (white tile, glowing cyan pools, daylight, no
                                 monsters): warm, dreamy, a childhood indoor pool at dusk
  music/level37     "Sublimity"  Level 37 (cream mosaic, sea-green water, domes with an oculus):
                                 airy choir and glass, serene and a little too perfect

Both are built on gen_assets' music track (G._MTrack: named buses, one tape wobble shared by every
note, the big room and the tape master) like liminal / deep / drift and Level 227's megaflora. The
instruments those pieces don't have are defined here:
  _chorus_pad        a slowed, chorused "vaporwave" pad
  _glass_ep          a glassy FM electric piano (half electric piano, half glass marimba)
  _echo              a ping-pong tape echo
  _underwater        a low-pass that moves over time, so the music can dip under the water
  _aah / _choir      a sung choir 'aah', re-using Level 227's formant voice
  _glass_harmonica   rubbed glass bowls: an almost pure, slowly swelling tone

Loudness: each piece is written so its integrated loudness lands on TARGET_LUFS, the average of the
existing liminal, deep and megaflora files (about -16 LUFS), with peaks kept under -1 dBFS.

Run only these with:  python tools/gen_assets.py music_poolrooms music_level37
Each piece has its own fixed random seed (13101, 13102), so re-running gives identical files.
"""
from __future__ import annotations

import numpy as np

import gen_assets as G
from anomaly_assets import level2_audio as A
from anomaly_assets import level227_audio as L227

SR = G.SR

# The average integrated loudness of music/liminal, deep and megaflora (-16.8, -16.4, -14.8 LUFS),
# so these pieces sit at the same volume as the rest of the soundtrack.
TARGET_LUFS = -16.0
# The highest peak we let _m_save aim for (-1.4 dBFS). _m_save accepts a decoded peak up to 0.03
# above its target, so even then the file stays under -1 dBFS.
PEAK_CAP = 0.85


# =============================================================================
# Shared helpers
# =============================================================================

def _stereo_lufs(x: np.ndarray) -> float:
    """Integrated loudness (LUFS) of a stereo (samples, 2) signal. Same meter as level2_audio.lufs,
    except that the two channels' K-weighted power is added before gating, as the loudness
    standard (BS.1770) does for stereo."""
    y = sum(A._k_weight(np.concatenate([x[:, c], np.zeros(SR // 2)])) ** 2 for c in range(2))
    block, hop = int(0.4 * SR), int(0.1 * SR)
    csum = np.concatenate([[0.0], np.cumsum(y)])
    starts = np.arange(0, len(y) - block + 1, hop)
    z = (csum[starts + block] - csum[starts]) / block       # mean power of each 400 ms block
    loud = -0.691 + 10 * np.log10(z + 1e-20)
    z = z[loud > -70.0]                                     # absolute gate: ignore silence
    rel = -0.691 + 10 * np.log10(z.mean()) - 10.0
    z = z[-0.691 + 10 * np.log10(z) > rel]                  # relative gate: ignore the quiet bits
    return float(-0.691 + 10 * np.log10(z.mean()))


def _save_music(L: np.ndarray, R: np.ndarray, name: str, fade_out: float) -> None:
    """Writes music/<name>.ogg through the shared G._m_save, choosing its peak so the file lands on
    TARGET_LUFS. _m_save scales the music to a fixed PEAK, but how loud music sounds depends on how
    spiky it is, so we first measure what loudness a peak of 1.0 would give (after the same fades
    _m_save applies) and scale the peak by the difference."""
    x = np.stack([L, R], axis=1)
    x = x - x.mean(axis=0)
    fi, fo = int(0.5 * SR), int(fade_out * SR)
    x[:fi] *= np.linspace(0, 1, fi)[:, None]
    x[-fo:] *= (np.linspace(1, 0, fo) ** 2)[:, None]
    x /= np.abs(x).max()
    peak = min(PEAK_CAP, 10 ** ((TARGET_LUFS - _stereo_lufs(x)) / 20))
    G._m_save(L, R, G.SND / "music" / f"{name}.ogg", peak=peak, fade_out=fade_out)


def _curve(n: int, pts, log: bool = False) -> np.ndarray:
    """A smooth curve through (seconds, value) points, one value per sample. Each step eases in and
    out (a raised cosine), so sweeps start and stop gently instead of turning sharp corners.
    log=True glides in equal ratios, which is how our ears hear changes of frequency."""
    t = np.arange(n) / SR
    ts = np.array([p[0] for p in pts], float)
    vs = np.array([p[1] for p in pts], float)
    if log:
        vs = np.log(vs)
    i = np.clip(np.searchsorted(ts, t, side="right") - 1, 0, len(ts) - 2)
    u = np.clip((t - ts[i]) / np.maximum(ts[i + 1] - ts[i], 1e-9), 0, 1)
    u = 0.5 - 0.5 * np.cos(np.pi * u)
    y = vs[i] + (vs[i + 1] - vs[i]) * u
    return np.exp(y) if log else y


def _pan(x: np.ndarray, pan: float):
    """(left, right) of a mono sound placed at pan (-1 left .. +1 right) at constant loudness."""
    return x * np.sqrt(0.5 * (1 - pan)), x * np.sqrt(0.5 * (1 + pan))


# =============================================================================
# Poolrooms instruments
# =============================================================================

def _chorus_pad(tr, bus: str, start: float, dur: float, note: str, vel: float, bright: float = 1600.0,
                attack: float = 3.0, release: float = 5.0, tilt: float = 1.45, voices: int = 5,
                spread: float = 11.0, depth: float = 7.0, pitch: float = 1.0) -> None:
    """A slowed, chorused 'vaporwave' pad note. Like _MTrack.pad (soft saw-like voices) but with
    more voices, and each voice's pitch wavers slowly at its own rate: that wavering is exactly what
    a chorus effect does, and it gives the warm, smeared, slightly seasick shimmer of slowed-down
    80s synths. The voices are fanned across the stereo field."""
    freq = G._m_hz(note) * pitch
    n = int((dur + release) * SR)
    t = np.arange(n) / SR
    ratio = tr._ratio(start, n)
    tab = G._m_table(int(np.clip(bright / freq, 1, 64)), tilt)
    env = G._m_env(n, attack, dur, release) * vel / voices
    L = np.zeros(n)
    R = np.zeros(n)
    for k in range(voices):
        pos = k / max(1, voices - 1)
        cents = (-spread + 2 * spread * pos + tr.rng.uniform(-2, 2)
                 + depth * np.sin(2 * np.pi * tr.rng.uniform(0.18, 0.55) * t + tr.rng.uniform(0, 6.3)))
        ph = (tr.rng.uniform() + np.cumsum(freq * 2 ** (cents / 1200) * ratio) / SR) % 1.0
        v = np.interp(ph * 4096, np.arange(4097), tab)
        l, r = _pan(v, -0.9 + 1.8 * pos)
        L += l
        R += r
    tr.add(bus, start, L * env, R * env)


def _glass_ep(tr, bus: str, start: float, hz: float, vel: float, decay: float = 2.0, pan: float = 0.0,
              glass: float = 1.0) -> None:
    """A glassy electric-piano note, made the way 80s digital pianos made them (FM synthesis): a
    sine whose phase is wobbled by another sine at the same pitch. The wobble (the 'index') starts
    strong and dies away fast, so the attack sparkles and the tail is pure. A faint overtone just
    off the 4th harmonic and a quick high ping give it the edge of a glass marimba (`glass` scales
    them)."""
    n = int(decay * 3.0 * SR)
    t = np.arange(n) / SR
    ph = np.cumsum(hz * tr._ratio(start, n)) / SR + tr.rng.uniform()
    index = 1.3 * np.exp(-t / 0.3) + 0.12
    x = np.sin(2 * np.pi * ph + index * np.sin(2 * np.pi * ph)) * np.exp(-t / decay)
    x += glass * 0.22 * np.sin(2 * np.pi * 3.97 * ph) * np.exp(-t / (0.22 * decay))
    x += glass * 0.07 * np.sin(2 * np.pi * 9.13 * ph) * np.exp(-t / 0.07)
    x *= np.clip(t / 0.004, 0, 1) * vel
    x[-int(0.05 * SR):] *= np.linspace(1, 0, int(0.05 * SR))
    tr.add(bus, start, *_pan(x, pan))


def _echo(l: np.ndarray, r: np.ndarray, delay: float, feedback: float, taps: int = 4,
          lp: float = 3000.0):
    """A ping-pong tape echo: each repeat comes back from the other side, quieter and a little
    duller than the one before (every pass through tape loses some treble)."""
    d = int(delay * SR)
    outL, outR = l.copy(), r.copy()
    srcL, srcR = l, r
    dull = lambda f: G.lp_gain(f, lp, 1)
    for k in range(1, taps + 1):
        srcL, srcR = G.fft_filter(srcR, dull), G.fft_filter(srcL, dull)   # swap sides each repeat
        g = feedback ** k
        outL[d * k:] += g * srcL[:-d * k]
        outR[d * k:] += g * srcR[:-d * k]
    return outL, outR


# The ladder of fixed cutoffs _underwater blends between; the top rung is practically open.
_SWEEP_BANK = (220.0, 330.0, 500.0, 760.0, 1150.0, 1750.0, 2700.0, 4200.0, 7000.0, 16000.0)


def _underwater(l: np.ndarray, r: np.ndarray, cutoff: np.ndarray):
    """A low-pass filter whose cutoff (Hz, one value per sample) moves over time: the music sinks
    under the water (only the low, muffled part gets through) and comes back up. A moving filter is
    awkward to do with FFTs, so the bus is filtered at a ladder of fixed cutoffs and we crossfade
    between neighbouring rungs. All rungs are zero-phase, so they line up exactly and the blend is
    smooth: no clicks, no zipper noise."""
    bank = np.array(_SWEEP_BANK)
    pos = np.interp(np.log(np.clip(cutoff, bank[0], bank[-1])), np.log(bank), np.arange(len(bank)))
    out = []
    for ch in (l, r):
        y = np.zeros(len(ch))
        for k, fc in enumerate(bank):
            w = np.clip(1 - np.abs(pos - k), 0, 1)
            if w.max() > 0:
                y += w * G.fft_filter(ch, lambda f: G.lp_gain(f, fc, 3))
        out.append(y)
    return out[0], out[1]


def _lapping(tr, bus: str, lo: float, hi: float, level_pts) -> None:
    """Small waves against the pool edge: band-limited noise that rises and falls every couple of
    seconds (two slow, unrelated waves plus a little randomness, so it never settles into a loop)."""
    n = tr.n
    t = np.arange(n) / SR
    level = np.interp(t, [p[0] for p in level_pts], [p[1] for p in level_pts])
    chans = []
    for _ in range(2):
        nz = G.fft_filter(tr.rng.normal(size=n), lambda f: G.bp_gain(f, lo, hi, 2))
        nz /= nz.std() + 1e-12
        wave = (0.6 * np.sin(2 * np.pi * tr.rng.uniform(0.25, 0.36) * t + tr.rng.uniform(0, 6.3))
                + 0.4 * np.sin(2 * np.pi * tr.rng.uniform(0.47, 0.61) * t + tr.rng.uniform(0, 6.3))
                + G.smooth_jitter(tr.rng, n, 0.8, 0.4))
        chans.append(nz * level * np.clip(0.5 + 0.5 * wave, 0.05, 1.2) ** 2)
    tr.add(bus, 0.0, chans[0], chans[1])


def _caustics(tr, bus: str, notes, level_pts) -> None:
    """Light from the pool dancing on the tiles, made audible: a few very quiet high sines whose
    loudness drifts in and out at random, with a soft ripple (a fast, shallow tremolo) on top. There
    is always a faint shimmer, and it never repeats."""
    n = tr.n
    t = np.arange(n) / SR
    level = np.interp(t, [p[0] for p in level_pts], [p[1] for p in level_pts])
    for k, note in enumerate(notes):
        hz = G._m_hz(note)
        amp = np.clip(G.smooth_jitter(tr.rng, n, 0.4, 1.6), 0, 1) ** 2
        ripple = 1 + 0.35 * np.sin(2 * np.pi * tr.rng.uniform(5.0, 8.0) * t + tr.rng.uniform(0, 6.3))
        x = np.sin(2 * np.pi * np.cumsum(hz * tr.ratio) / SR + tr.rng.uniform(0, 6.3)) * amp * ripple * level
        tr.add(bus, 0.0, *_pan(x, -0.8 + 1.6 * k / max(1, len(notes) - 1)))


def _bubbles(tr, bus: str, start: float, count: int, vel: float) -> None:
    """A little stream of bubbles rising past your ears underwater: each one a tiny sine blip whose
    pitch sweeps upward as it forms (that upward chirp is what makes a bubble sound like a bubble)."""
    rng = tr.rng
    at = start
    for _ in range(count):
        at += rng.uniform(0.07, 0.4)
        dur = rng.uniform(0.04, 0.09)
        m = int(dur * SR)
        t = np.arange(m) / SR
        f = rng.uniform(380, 1100) * (1 + 1.2 * (t / dur) ** 1.5)
        x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / (dur * 0.35)) * np.clip(t / 0.002, 0, 1)
        x[-int(0.005 * SR):] *= np.linspace(1, 0, int(0.005 * SR))
        tr.add(bus, at, *_pan(x * vel * rng.uniform(0.4, 1.0), rng.uniform(-0.6, 0.6)))


# =============================================================================
# Poolrooms: "Chlorine"
# =============================================================================

def snd_music_poolrooms() -> None:
    """"Chlorine", ~2.8 minutes for the Poolrooms: a childhood indoor pool at dusk. F Lydian (the
    raised fourth, B natural, keeps every chord floating and never quite home), 60 bpm, one chord
    every two bars (8 s). Slowed, chorused pads play lush maj7/maj9 chords; a glassy electric piano
    plays a slow arpeggio through a ping-pong echo; small waves lap at the edge and light glints on
    the tiles. Every so often the whole band sinks underwater: the sound goes muffled and a touch
    flat, bubbles rise, then it surfaces again.
      Intro (bars 0-4): only water at first; the first chord is heard from under the surface.
      A (4-16): the music surfaces; the progression Fmaj9#11 - G/F - Em11 - Am9 - Dm9 - Cmaj9/E,
        the arpeggio joins at bar 6.
      Dip (16-20): G/F and F sink underwater and come back up.
      B (20-32): the progression again, wider, with a slow glass melody over the arpeggio.
      Dip (32-36): a deeper, longer dive; the harmony turns to a borrowed E-flat chord over F
        (nostalgic and slightly wrong), then F.
      Outro (36-42): it only half surfaces. Fmaj9#11 hangs, the arpeggio thins out, the last
        note is B, the raised fourth, left unresolved over the water."""
    bar = 4.0
    beat = bar / 4
    total = 42 * bar + 2.0
    tr = G._MTrack(total, seed=13101, wow=0.004)
    n = tr.n
    rng = tr.rng

    # How far underwater we are, as a low-pass cutoff in Hz over time (16 kHz = at the surface).
    cutoff = _curve(n, [(0, 300), (6, 300), (22, 1600), (30, 16000), (64, 16000), (69.5, 380), (74, 380),
                        (80, 16000), (128, 16000), (133, 250), (142, 250), (151, 1800), (total, 1800)], log=True)
    # Under the water everything also sounds a little slower and lower: the shared tape-speed curve
    # is pulled flat by up to ~28 cents in proportion to the depth. (Done before any note is drawn,
    # because every instrument reads its pitch wobble from tr.ratio.)
    depth = np.clip(np.log(16000 / cutoff) / np.log(16000 / 250), 0, 1)
    tr.ratio = tr.ratio * 2 ** (-28 * depth / 1200)

    # The harmony: pad voicings (all in F Lydian except the borrowed E-flat chord) and bass roots.
    F = (["F3", "A3", "E4", "G4", "B4"], "F2")       # Fmaj9#11: home, but floating
    GF = (["F3", "B3", "D4", "G4", "A4"], "F2")      # G/F: the Lydian II chord over F
    Em = (["E3", "B3", "D4", "G4", "A4"], "E2")      # Em11
    Am = (["A3", "C4", "E4", "G4", "B4"], "A1")      # Am9
    Dm = (["D3", "F3", "C4", "E4", "A4"], "D2")      # Dm9
    CE = (["E3", "C4", "G4", "B4", "D5"], "E2")      # Cmaj9/E
    EbF = (["Eb3", "G3", "D4", "F4", "A4"], "F2")    # Ebmaj9#11 over F: the borrowed, wrong one
    prog = [F, GF, Em, Am, Dm, CE]
    # chord per 2-bar slot, from bar 4 to bar 36
    plan = prog + [GF, F] + prog + [EbF, F]

    # Intro: water, then the home chord fading in from under the surface.
    _chorus_pad(tr, "pad", 4.0, 3.2 * bar, F[0][0], 0.20, attack=6.0, release=6.0)
    for note in F[0][1:]:
        _chorus_pad(tr, "pad", 6.0 + rng.uniform(0, 3.0), 2.4 * bar, note, 0.20, attack=6.0, release=6.0)

    for slot, (notes, root) in enumerate(plan):
        at = (4 + 2 * slot) * bar
        in_b = 20 * bar <= at < 32 * bar
        for i, note in enumerate(notes):
            _chorus_pad(tr, "pad", at + i * 0.25 * rng.uniform(0.6, 1.2), 2 * bar, note,
                        (0.32 if in_b else 0.27) * rng.uniform(0.85, 1.0), attack=2.6, release=4.5,
                        spread=14.0 if in_b else 11.0)
        if in_b:   # an octave of chorused air above the top two notes
            for note in notes[-2:]:
                _chorus_pad(tr, "shimmer", at + 0.6, 2 * bar, note, 0.16, bright=4000, attack=3.0,
                            release=4.5, pitch=2.0, voices=4)
        tr.pad("bass", at, 2 * bar, root, 0.32, bright=240, attack=1.6, release=3.0)

    # The arpeggio: the chord's own notes an octave up, eighth notes, up and down (two patterns so it
    # doesn't tick like a machine). Sparser in the deep dive, slowing to quarter notes at the end.
    patterns = ([0, 1, 2, 3, 4, 3, 2, 1], [0, 2, 1, 3, 2, 4, 3, 1])
    for b in range(6, 36):
        notes = plan[(b - 4) // 2][0]
        tones = sorted(G._m_hz(x) * 2 for x in notes)
        pat = patterns[(b // 2) % 2]
        for e, idx in enumerate(pat):
            if 32 <= b < 36 and e % 2:
                continue
            accent = 1.0 if e == 0 else (0.8 if e == 4 else 0.62)
            swell = 0.75 if b < 8 else 1.0
            _glass_ep(tr, "ep", b * bar + e * beat / 2 + rng.uniform(-0.012, 0.02), tones[idx],
                      0.2 * accent * swell * rng.uniform(0.85, 1.0), decay=1.6, pan=-0.45 + 0.9 * idx / 4)
    tones = sorted(G._m_hz(x) * 2 for x in F[0])
    for q, idx in enumerate([0, 2, 4, 3, 1, 2, 4, 3, 2, 4]):   # outro, quarter notes, slowing, thinning
        if q > 5 and rng.uniform() < 0.35:
            continue
        _glass_ep(tr, "ep", 36 * bar + q * beat * (1 + 0.05 * q), tones[idx], 0.17 * (1 - 0.05 * q),
                  decay=2.0, pan=-0.45 + 0.9 * idx / 4)
    _glass_ep(tr, "ep", 39 * bar + 1.0, G._m_hz("B5"), 0.15, decay=3.5, pan=0.3)   # the hanging B

    # B's melody: slow, long glass notes over the arpeggio (bar, beat, note).
    melody = [(20, 0, "C6"), (20, 2, "B5"), (21, 0, "A5"), (21, 3, "E5"),
              (22, 0, "D6"), (22, 2, "B5"), (23, 0, "G5"), (23, 2.5, "A5"),
              (24, 0, "B5"), (24, 2, "G5"), (25, 0, "E5"), (25, 3, "D5"),
              (26, 0, "E5"), (26, 1.5, "G5"), (27, 0, "C6"), (27, 2, "B5"),
              (28, 0, "A5"), (28, 2, "F5"), (29, 0, "E5"), (29, 2, "C5"),
              (30, 0, "D5"), (30, 2, "E5"), (31, 0, "G5"), (31, 2, "B5")]
    for k, (b, bt, note) in enumerate(melody):
        _glass_ep(tr, "lead", b * bar + bt * beat + rng.uniform(-0.03, 0.04), G._m_hz(note), 0.3,
                  decay=3.2, pan=0.25 - 0.5 * (k % 2), glass=0.6)

    # Water: lapping and light on the tiles (above the surface, so they get muffled in the dips
    # too), and under the surface a low pressure rumble and rising bubbles.
    _lapping(tr, "water", 180, 1100, [(0, 0.0), (5, 0.022), (60, 0.016), (100, 0.014), (150, 0.02),
                                      (total, 0.02)])
    _caustics(tr, "glint", ["F6", "A6", "C7", "E7", "G6", "B6"],
              [(0, 0.0), (20, 0.004), (60, 0.006), (130, 0.006), (150, 0.004), (total, 0.003)])
    tr.noise_bed("under", 35, 220, [(0, 0.012), (8, 0.012), (22, 0.0), (64, 0.0), (69, 0.02), (75, 0.02),
                                    (80, 0.0), (128, 0.0), (133, 0.026), (142, 0.026), (152, 0.008),
                                    (total, 0.008)], drift_amt=0.3)
    for at, count in ((2.0, 6), (9.0, 4), (68.0, 9), (73.5, 5), (132.0, 10), (137.0, 7), (141.0, 5)):
        _bubbles(tr, "bubbles", at, count, 0.12)
    tr.noise_bed("room", 25, 120, [(0, 0.0), (8, 0.012), (total, 0.012)], drift_amt=0.2)

    # The arpeggio and melody echo (a dotted eighth = 0.75 s, the vaporwave classic), then
    # everything above the surface goes through the moving underwater filter.
    for name in ("ep", "lead"):
        tr.buses[name] = _echo(*tr.buses[name], delay=0.75, feedback=0.38, taps=4, lp=3000)
    for name in ("pad", "shimmer", "ep", "lead", "water", "glint"):
        tr.buses[name] = _underwater(*tr.buses[name], cutoff)
    l, r = tr.buses["bubbles"]
    dull = lambda f: G.bp_gain(f, 250, 2200, 2)       # bubbles heard through water: no sparkle
    tr.buses["bubbles"] = (G.fft_filter(l, dull), G.fft_filter(r, dull))

    L, R = tr.master(mix={"pad": 1.0, "shimmer": 0.35, "ep": 0.55, "lead": 0.5, "bass": 0.6, "water": 1.0,
                          "glint": 1.0, "under": 1.0, "bubbles": 0.35, "room": 1.0},
                     reverb={"pad": 0.5, "shimmer": 0.9, "ep": 0.55, "lead": 0.6, "bass": 0.08, "glint": 0.9,
                             "bubbles": 0.4, "water": 0.3},
                     room_s=5.0, room_lp=5200, lp=6000, drive=1.35)
    _save_music(L, R, "poolrooms", fade_out=8.0)


# =============================================================================
# Level 37 instruments
# =============================================================================

def _aah(tr, bus: str, start: float, dur: float, hz: float, vel: float, pan: float = 0.0,
         vowel: str = "a", bend=None, attack: float = 3.0, release: float = 4.5) -> None:
    """One choir voice singing 'aah' with Level 227's formant voice: a vibrato that grows in after
    the note starts (as singers do), a slow drift, a little breath. `bend` is optional (seconds into
    the note, cents) points that slowly pull the pitch away, for the voice that goes wrong."""
    rng = tr.rng
    n = int((dur + release) * SR)
    t = np.arange(n) / SR
    vib = 0.006 * np.clip(t / 1.8, 0, 1) * np.sin(2 * np.pi * rng.uniform(4.6, 5.6) * t + rng.uniform(0, 6.3))
    f0 = hz * tr._ratio(start, n) * (1 + vib + G.smooth_jitter(rng, n, 0.6, 0.003))
    if bend:
        f0 = f0 * 2 ** (_curve(n, bend) / 1200)
    # Harmonics above ~7 kHz add nothing to a sung vowel but cost time, so fewer for higher notes.
    v = G.norm(L227._choir_voice(rng, f0, vowel, nh=int(np.clip(7000 / hz, 6, 40))))
    v = v + 0.06 * G.norm(G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 900, 5000, 2)))
    v = G.fft_filter(v, lambda f: G.lp_gain(f, 5500, 2) * G.hp_gain(f, 160, 2))
    v = G.norm(v) * G._m_env(n, attack, dur, release) * vel
    tr.add(bus, start, *_pan(v, pan))


def _choir(tr, bus: str, start: float, dur: float, notes, vel: float, voices: int = 2, bend=None,
           **kw) -> None:
    """A choir chord: `voices` singers per note, a few cents apart (a real section never sings
    exactly together, and that is what makes it sound like a choir), entering one after another and
    spread across the stereo field, lower notes towards the middle."""
    rng = tr.rng
    for i, note in enumerate(notes):
        for v in range(voices):
            side = (-1) ** (i + v)
            _aah(tr, bus, start + i * 0.35 + rng.uniform(0, 0.6), dur, G._m_hz(note) * 2 ** (rng.uniform(-6, 6) / 1200),
                 vel * rng.uniform(0.85, 1.0) / voices ** 0.5, pan=side * (0.2 + 0.6 * i / max(1, len(notes) - 1)),
                 vowel="o" if (v == 1 and i == 0) else "a", bend=bend, **kw)


def _glass_harmonica(tr, bus: str, start: float, dur: float, hz: float, vel: float, pan: float = 0.0,
                     attack: float = 0.9, release: float = 3.5) -> None:
    """A glass harmonica (spinning glass bowls rubbed with a wet finger): an almost pure tone that
    swells in as the finger presses, a faint octave and an even fainter out-of-tune glass mode, a
    second bowl a fraction of a hertz off (the slow beating makes it shimmer), and a tiny roughness
    from the finger. When the finger lifts, the glass rings on and fades."""
    rng = tr.rng
    n = int((dur + release) * SR)
    ratio = tr._ratio(start, n)
    ph = np.cumsum(hz * ratio) / SR + rng.uniform()
    ph2 = np.cumsum((hz + rng.uniform(0.4, 1.1)) * ratio) / SR + rng.uniform()
    x = (np.sin(2 * np.pi * ph) + 0.35 * np.sin(2 * np.pi * ph2) + 0.1 * np.sin(4 * np.pi * ph + 0.5)
         + 0.025 * np.sin(6 * np.pi * ph) + 0.03 * np.sin(2 * np.pi * 2.32 * ph))
    rub = 1 + 0.05 * G.smooth_jitter(rng, n, 9.0, 1.0)
    x = x * rub * G._m_env(n, attack, dur, release) * vel / 1.35
    tr.add(bus, start, *_pan(x, pan))


def _glint(tr, bus: str, start: float, hz: float, vel: float, pan: float) -> None:
    """A single sparkle of light on the mosaic: a short, soft glass ping, very high."""
    n = int(4.0 * SR)
    t = np.arange(n) / SR
    ph = np.cumsum(hz * tr._ratio(start, n)) / SR + tr.rng.uniform()
    x = (np.sin(2 * np.pi * ph) + 0.18 * np.sin(2 * np.pi * 2.32 * ph) * np.exp(-t / 0.3)) * np.exp(-t / 1.1)
    x *= np.clip(t / 0.02, 0, 1) * vel
    x[-int(0.05 * SR):] *= np.linspace(1, 0, int(0.05 * SR))
    tr.add(bus, start, *_pan(x, pan))


# =============================================================================
# Level 37: "Sublimity"
# =============================================================================

def snd_music_level37() -> None:
    """"Sublimity", ~2.8 minutes for Level 37: pristine, sunlit, too beautiful. D Lydian (D major
    with a raised fourth, G sharp: bright and weightless), about 40 bpm, one chord every 9 s.
    An airy choir sings 'aah' in a high register over almost no bass; glass harmonica tones float
    above it; tiny glass sparkles fall like light through the oculus, in Lydian or (in the
    interlude) whole-tone scales. Huge, bright dome reverb.
      Intro (bars 0-2): light only: sparkles and two glass tones.
      A (2-7): the choir enters: Dmaj9 - E/D - F#m7 - Asus2/E - Bm7, glass notes above.
      Interlude (7-9): whole-tone chords (D and E augmented): the floor seems to vanish.
      B (9-14): the most luminous part: a fuller, higher choir, a slow glass-harmonica melody, and
        the only bass of the piece, a soft low D breathing in underneath.
      The turn (14-16): Dmaj9 again, but its top voice slowly sinks out of tune; then on E/D the
        whole choir tilts flat, a fraction of a semitone, while the glass stays true.
      Outro (16-18): the choir holds an open D chord, still tilted; the glass rings true D and G#
        against it. Perfect, and wrong."""
    bar = 9.0
    beat = bar / 6
    total = 18 * bar + 6.0
    tr = G._MTrack(total, seed=13102, wow=0.0025, flutter=0.0006)
    rng = tr.rng

    lydian = ["D6", "E6", "F#6", "G#6", "A6", "B6", "C#7"]
    whole = ["D6", "E6", "F#6", "G#6", "A#6", "C7"]

    # Sparkles: (from, to, how many, scale)
    for t0, t1, count, scale in ((0.5, 18, 9, lydian), (18, 63, 14, lydian), (63, 81, 18, whole),
                                 (81, 126, 18, lydian), (126, 144, 6, lydian), (144, 160, 4, lydian)):
        for s in np.sort(rng.uniform(t0, t1, count)):
            _glint(tr, "glint", float(s), G._m_hz(str(rng.choice(scale))) * float(rng.choice([1.0, 1.0, 0.5])),
                   float(rng.uniform(0.08, 0.18)), float(rng.uniform(-0.85, 0.85)))

    # Intro: light only.
    _glass_harmonica(tr, "glass", 1.5, 9.0, G._m_hz("A5"), 0.22, pan=-0.3, attack=2.5)
    _glass_harmonica(tr, "glass", 8.0, 8.0, G._m_hz("E6"), 0.16, pan=0.35, attack=2.0)

    # A: the choir, high and light.
    a_chords = [["D4", "A4", "C#5", "E5"], ["D4", "G#4", "B4", "E5"], ["F#4", "A4", "C#5", "E5"],
                ["E4", "A4", "B4", "E5"], ["B3", "F#4", "A4", "D5"]]
    for i, ch in enumerate(a_chords):
        # The first chord swells in slowly out of the silence; A then grows a little chord by chord.
        _choir(tr, "choir", (2 + i) * bar, bar, ch, 0.24 if i == 0 else 0.26 + 0.02 * i,
               attack=6.0 if i == 0 else 2.5)
    for b, bt, beats, note in ((2, 2, 4, "G#5"), (3, 3, 3, "F#5"), (4, 1, 5, "C#6"), (5, 3, 3, "B5"), (6, 1, 4, "A5")):
        _glass_harmonica(tr, "glass", b * bar + bt * beat, beats * beat, G._m_hz(note), 0.2,
                         pan=rng.uniform(-0.4, 0.4))

    # Interlude: whole-tone chords, no ground at all.
    _choir(tr, "choir", 7 * bar, bar, ["D4", "F#4", "A#4", "E5"], 0.30)
    _choir(tr, "choir", 8 * bar, bar, ["E4", "G#4", "C5", "F#5"], 0.30)
    _glass_harmonica(tr, "glass", 7 * bar + 2 * beat, 4 * beat, G._m_hz("C6"), 0.18, pan=0.3)
    _glass_harmonica(tr, "glass", 8 * bar + 1 * beat, 4 * beat, G._m_hz("A#5"), 0.18, pan=-0.3)

    # B: fuller and higher, an octave of shimmer on top, the melody, the one breath of bass.
    b_chords = [["D4", "A4", "C#5", "E5", "A5"], ["E4", "G#4", "B4", "E5", "G#5"], ["F#4", "A4", "C#5", "F#5", "A5"],
                ["B3", "F#4", "A4", "D5", "F#5"], ["E4", "G#4", "B4", "D5", "F#5"]]
    for i, ch in enumerate(b_chords):
        at = (9 + i) * bar
        _choir(tr, "choir", at, bar, ch, 0.40 + 0.03 * np.sin(np.pi * i / 4), attack=2.2)
        tr.chord("shimmer", at + 0.8, bar, ch[-2:], 0.18, bright=6000, attack=3.0, release=4.0, pitch=2.0,
                 tilt=1.6)
    melody = [(9, 0.5, 3, "E6"), (9, 3.5, 2.5, "C#6"), (10, 0.5, 3, "B5"), (10, 3.5, 2.5, "G#5"),
              (11, 0.5, 2, "A5"), (11, 2.5, 3, "C#6"), (12, 0.5, 3, "F#6"), (12, 3.5, 2.5, "D6"),
              (13, 0.5, 3, "E6"), (13, 3.5, 2.5, "G#5")]
    for k, (b, bt, beats, note) in enumerate(melody):
        _glass_harmonica(tr, "glass", b * bar + bt * beat, beats * beat - 0.3, G._m_hz(note), 0.26,
                         pan=0.3 - 0.6 * (k % 2), attack=0.6)
    tr.drone("low", 9.5 * bar, 4.5 * bar, "D2", 0.14, fade=12.0, beat=0.2)

    # The turn. Bar 14: Dmaj9 again, and a lone top voice on A sinks ~30 cents flat over 9 s.
    _choir(tr, "choir", 14 * bar, bar, ["D4", "A4", "C#5", "E5"], 0.34)
    _aah(tr, "choir", 14 * bar + 0.8, bar, G._m_hz("A5"), 0.22, pan=0.4, bend=[(0, 0), (3.0, 0), (10.0, -30)])
    # Bar 15: E/D, and the whole choir tilts flat by 40 cents while the glass above stays true.
    _choir(tr, "choir", 15 * bar, bar, ["D4", "G#4", "B4", "E5"], 0.32, bend=[(0, 0), (2.5, 0), (8.5, -40)])
    _glass_harmonica(tr, "glass", 15 * bar + 2 * beat, 3 * beat, G._m_hz("E6"), 0.2, pan=-0.3)
    # Outro: an open D chord, still tilted, the true glass ringing D and G# against it.
    _choir(tr, "choir", 16 * bar, 1.6 * bar, ["D4", "A4", "D5"], 0.3, bend=[(0, -40), (30, -40)],
           attack=4.0, release=7.0)
    _glass_harmonica(tr, "glass", 16 * bar + 1.5 * beat, 4 * beat, G._m_hz("D6"), 0.17, pan=0.35)
    _glass_harmonica(tr, "glass", 16.8 * bar + 1 * beat, 6 * beat, G._m_hz("G#5"), 0.17, pan=-0.35, release=5.0)

    # Air under the dome, and the sea-green water barely moving.
    tr.noise_bed("air", 2500, 9000, [(0, 0.0), (6, 0.004), (80, 0.005), (126, 0.005), (150, 0.003),
                                     (total, 0.002)], drift_amt=0.5)
    _lapping(tr, "water", 220, 1500, [(0, 0.0), (8, 0.008), (total - 10, 0.008), (total, 0.004)])

    L, R = tr.master(mix={"choir": 1.0, "glass": 0.6, "glint": 0.3, "shimmer": 0.3, "low": 0.45,
                          "air": 1.0, "water": 1.0},
                     reverb={"choir": 0.75, "glass": 0.85, "glint": 1.0, "shimmer": 1.0, "low": 0.05, "water": 0.2},
                     room_s=8.5, room_lp=6500, lp=9000, drive=1.2)
    _save_music(L, R, "level37", fade_out=8.0)


SOUNDS = [snd_music_poolrooms, snd_music_level37]
