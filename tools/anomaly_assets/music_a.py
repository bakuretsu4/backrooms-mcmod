"""Level music, group A: "Fluorescent" (Level 0) and "Concrete" (Level 1).

Two ORIGINAL pieces composed note by note in code, like the soundtrack in gen_assets.py
(snd_music_liminal, snd_music_deep, snd_music_drift) and Level 227's megaflora. Nothing is sampled
or copied; the style notes below describe moods, not anyone else's melodies.

  music/level0   "Fluorescent": office muzak through a wall at half speed, a Rhodes-like electric
                 piano and a ghost of a bossa rhythm, against the 60 Hz hum of the lights
  music/level1   "Concrete": cold, wide industrial ambient in C# Dorian: a sub drone, far metal
                 rings, a breathing ventilation pulse, a swelling pad, a distant two-note alarm,
                 drips drifting in and out of time

Both use the shared track and tape master of gen_assets (G._MTrack, G._m_save), so they sound like
the same worn cassette as the rest of the soundtrack. A few new instruments live here (an FM
electric piano, a soft bass, brushes, struck metal, drips, a far alarm) because the existing ones
(pads, music box, drones) can't make those sounds.

Loudness: _m_save scales every piece to the same PEAK, which leaves quiet, sparse pieces quieter
than busy ones. So _save_music first measures the mix (stereo integrated loudness, ITU BS.1770,
the meter in level2_audio) and picks the peak that lands the piece on TARGET_LUFS, the average of
liminal, deep and megaflora. The peak never goes above -1.5 dBFS.

Run only these with:  python tools/gen_assets.py snd_music_level0 snd_music_level1
(each takes under a minute).
Fixed random seeds (13010, 13011): re-running gives identical files.
"""
from __future__ import annotations

import numpy as np

import gen_assets as G
from anomaly_assets import level2_audio as A

SR = G.SR

# Measured on the existing files (stereo BS.1770): liminal -16.8, deep -16.4, megaflora -14.8.
TARGET_LUFS = -16.0
MAX_PEAK = 0.84   # -1.5 dBFS before the codec; _m_save allows +0.03 of codec overshoot


# =============================================================================
# Shared helpers
# =============================================================================

def _stereo_lufs(L: np.ndarray, R: np.ndarray) -> float:
    """Integrated loudness of a stereo mix (both channels' K-weighted power added, 400 ms blocks,
    absolute and relative gates), so the level matches what a loudness meter would show."""
    pad = np.zeros(SR // 2)
    y = A._k_weight(np.concatenate([L, pad])) ** 2 + A._k_weight(np.concatenate([R, pad])) ** 2
    block, hop = int(0.4 * SR), int(0.1 * SR)
    cs = np.concatenate([[0.0], np.cumsum(y)])
    starts = np.arange(0, len(y) - block + 1, hop)
    z = (cs[starts + block] - cs[starts]) / block
    z = z[-0.691 + 10 * np.log10(z + 1e-20) > -70.0]
    rel = -0.691 + 10 * np.log10(z.mean()) - 10.0
    z = z[-0.691 + 10 * np.log10(z) > rel]
    return float(-0.691 + 10 * np.log10(z.mean()))


def _save_music(L: np.ndarray, R: np.ndarray, name: str, fade_out: float) -> None:
    """Writes music/<name>.ogg through the shared tape save, at the peak that puts the piece on
    TARGET_LUFS (measured on the mix scaled to _m_save's usual peak, 0.62)."""
    ref = 0.62
    m = max(np.abs(L).max(), np.abs(R).max())
    loud = _stereo_lufs(L / m * ref, R / m * ref)
    peak = ref * 10 ** ((TARGET_LUFS - loud) / 20)
    if peak > MAX_PEAK:
        print(f"  {name}: would need peak {peak:.2f} for {TARGET_LUFS} LUFS; capped at {MAX_PEAK}")
        peak = MAX_PEAK
    G._m_save(L, R, G.SND / "music" / f"{name}.ogg", peak=peak, fade_out=fade_out)


def _pan(x: np.ndarray, pan: float):
    """Equal-power pan: -1 left, 0 centre, +1 right."""
    return x * np.sqrt(0.5 * (1 - pan)), x * np.sqrt(0.5 * (1 + pan))


def _curve(n: int, pts) -> np.ndarray:
    """A piecewise-linear curve through (seconds, value) points, one value per sample."""
    t = np.arange(n) / SR
    return np.interp(t, [p[0] for p in pts], [p[1] for p in pts])


def _semi(hz: float, steps: float) -> float:
    return hz * 2 ** (steps / 12)


def _filt(l: np.ndarray, r: np.ndarray, gain_fn):
    return G.fft_filter(l, gain_fn), G.fft_filter(r, gain_fn)


# =============================================================================
# Level 0: "Fluorescent"
# =============================================================================

def _ep(tr, bus: str, start: float, dur: float, hz: float, vel: float, pan: float = 0.0) -> None:
    """A Rhodes-like electric piano note made with FM (one sine bending the phase of another at
    the same frequency): a 'bark' of extra harmonics when the key is struck that mellows into a
    round tone, plus the faint metallic 'tine' ping of a real electric piano's attack."""
    rel = 0.35
    n = int((dur + rel + 0.1) * SR)
    t = np.arange(n) / SR
    ph = tr.rng.uniform() + np.cumsum(hz * tr._ratio(start, n)) / SR
    # Harder notes bark more; the index (how hard the phase is bent) falls as the note rings.
    index = vel * (2.2 * np.exp(-t / 0.22) + 0.6 * np.exp(-t / 1.6)) + 0.25
    x = np.sin(2 * np.pi * ph + index * np.sin(2 * np.pi * ph))
    if hz * 14 < 8000:   # the tine ping, a high partial that dies in a few hundredths of a second
        x += 0.18 * vel * np.sin(2 * np.pi * 14 * ph) * np.exp(-t / 0.02)
    decay = 2.4 * (200.0 / hz) ** 0.35          # low notes ring longer, like real tines
    env = np.exp(-t / decay) * np.clip(t / 0.003, 0, 1)
    env *= np.where(t < dur, 1.0, np.exp(-(t - dur) / (rel / 3)))   # key released: damper
    tail = int(0.02 * SR)
    env[-tail:] *= np.linspace(1, 0, tail)
    tr.add(bus, start, *_pan(x * env * vel, pan))


def _bass(tr, bus: str, start: float, dur: float, hz: float, vel: float) -> None:
    """A soft upright-ish bass pluck: a round fundamental, a little 2nd and 3rd harmonic, a short
    brighter thump at the start (the finger), dying away like a muted string."""
    n = int((dur + 0.2) * SR)
    t = np.arange(n) / SR
    ph = tr.rng.uniform() + np.cumsum(hz * tr._ratio(start, n)) / SR
    x = (np.sin(2 * np.pi * ph) + 0.3 * np.sin(4 * np.pi * ph + 0.4)
         + (0.12 + 0.3 * np.exp(-t / 0.05)) * np.sin(6 * np.pi * ph + 1.1))
    env = np.clip(t / 0.012, 0, 1) * np.exp(-t / 0.9)
    env *= np.where(t < dur, 1.0, np.exp(-(t - dur) / 0.04))
    env[-int(0.01 * SR):] *= np.linspace(1, 0, int(0.01 * SR))
    x = x * env * vel
    tr.add(bus, start, x, x)


def _rim(tr, bus: str, start: float, vel: float) -> None:
    """A soft cross-stick click (the bossa clave): two short wooden tones and a tick of noise."""
    n = int(0.12 * SR)
    t = np.arange(n) / SR
    x = (np.sin(2 * np.pi * 1150 * t) + 0.6 * np.sin(2 * np.pi * 1790 * t + 0.5)) * np.exp(-t / 0.018)
    x += 0.5 * tr.rng.normal(size=n) * np.exp(-t / 0.002)
    x *= np.clip(t / 0.0007, 0, 1) * vel
    tr.add(bus, start, *_pan(x, 0.25))


def _brush_tap(tr, bus: str, start: float, vel: float) -> None:
    """A brush slapped on the snare (beats 2 and 4): a soft burst of high noise."""
    n = int(0.3 * SR)
    t = np.arange(n) / SR
    x = G.fft_filter(tr.rng.normal(size=n), lambda f: G.bp_gain(f, 1200, 6500, 2))
    x = G.norm(x) * np.exp(-t / 0.07) * np.clip(t / 0.003, 0, 1) * vel
    tr.add(bus, start, *_pan(x, -0.15))


def _dropout_gain(n: int, cuts, depth: float = 0.04) -> np.ndarray:
    """1.0 normally; down to `depth` during each (start, length) cut, with short raised-cosine
    ramps so a dropout sounds like a bad speaker contact rather than a click."""
    g = np.ones(n)
    down, up = int(0.015 * SR), int(0.03 * SR)
    for s, length in cuts:
        i0 = int(s * SR)
        i1 = min(n, int((s + length) * SR))
        g[i0:i1] = depth
        r = 0.5 - 0.5 * np.cos(np.linspace(0, np.pi, down))
        a = max(0, i0 - down)
        g[a:i0] = np.minimum(g[a:i0], 1 - (1 - depth) * r[: i0 - a])
        b = min(n, i1 + up)
        r = 0.5 - 0.5 * np.cos(np.linspace(0, np.pi, up))
        g[i1:b] = np.minimum(g[i1:b], depth + (1 - depth) * r[: b - i1])
    return g


def snd_music_level0() -> None:
    """Level 0, "Fluorescent", ~2 min 46 s. Office muzak heard through a wall and slowed to half
    speed. 70 bpm in 4/4 (one bar = 3.43 s). Key: B-flat major, but the music never lands on B-flat:
    an endless loop of lounge chords (E-flat maj9, D m9, G m9, C m9, F 9sus4, then a drift through
    G-flat maj7, A-flat maj7 and D-flat maj9 back to E-flat), played by a soft FM electric piano in a
    lazy bossa comp, with a slow walking melody on top in the middle of the piece.

    The 60 Hz mains hum of the ceiling lights (with its 120 and 180 Hz harmonics, and a thin ballast
    buzz) is part of the music and never wobbles; the whole band is tuned so B-flat sits about 30
    cents flat of the hum, and in the middle section the tape sags another 35 cents, so the music
    always rubs slightly against the room. A ghost of a bossa rhythm (brushes, a cross-stick clave,
    a soft bass) fades in and out like a speaker in another room. The speaker drops out now and
    then, and when it does the hum swells a little to fill the gap.

    Structure (bars): 0-3 hum alone, one far chord; 3-11 the loop, far away, rhythm creeping in;
    11-19 closer, full rhythm, the melody; 19-21 stuttering dropouts, then the hum alone; 21-29 the
    loop again with the tape sagging and the rhythm fading out; 29-37 back, half of the melody,
    more dropouts; 37-43 only slow piano chords, far off; a held G-flat maj7 is cut off by a
    dropout and never comes back; the hum fades last."""
    beat = 60.0 / 70.0
    bar = 4 * beat
    # 47 bars (161 s) plus a tail. 166 s exactly: the FFT filters work on the whole track, and this
    # length factors into small primes, which keeps them fast (an odd length took minutes).
    total = 166.0
    tr = G._MTrack(total, seed=13010, wow=0.0028, flutter=0.0007)
    n = tr.n

    # Tuning: B-flat 30 cents under the 60 Hz hum (and so its 5th, F, 30 cents under 180 Hz).
    # Baking it into the shared tape-speed curve tunes every note at once; the tape then sags a
    # further 35 cents through section B and only partly recovers.
    base = 60.0 * 2 ** (-30 / 1200) / G._m_hz("Bb1")
    sag = _curve(n, [(0, 0), (21 * bar, 0), (27 * bar, -35), (30 * bar, -35), (34 * bar, -12), (total, -12)])
    tr.ratio = tr.ratio * base * 2 ** (sag / 1200)

    # The loop: (bass root, piano voicing). Voiced low and close, like half-speed muzak.
    loop = [("Eb2", ["G3", "Bb3", "D4", "F4"]),          # E-flat maj9
            ("D2", ["F3", "A3", "C4", "E4"]),            # D m9
            ("G1", ["F3", "A3", "Bb3", "D4"]),           # G m9
            ("C2", ["Eb3", "G3", "Bb3", "D4"]),          # C m9
            ("F2", ["Eb3", "G3", "Bb3", "C4"]),          # F 9sus4 (where B-flat should come; it doesn't)
            ("Gb2", ["F3", "Bb3", "Db4", "Eb4"]),        # G-flat maj7 (6/9 colour)
            ("Ab2", ["G3", "C4", "Eb4", "F4"]),          # A-flat maj7 (6)
            ("Db2", ["F3", "Ab3", "C4", "Eb4"])]         # D-flat maj9 -> back to E-flat, never home

    def comp(b: int, vel: float, sparse: bool = False) -> None:
        """One bar of electric-piano comping: a 3+3+2 bossa push, or one long chord when sparse."""
        root, voicing = loop[(b - 3) % 8]
        at = b * bar
        hits = [(0.0, 3.6, 1.0)] if sparse else (
            [(0.0, 2.2, 1.0), (2.5, 1.3, 0.75)] if b % 2 == 0 else [(0.0, 1.2, 1.0), (1.5, 1.2, 0.8), (3.0, 0.8, 0.7)])
        for off, length, acc in hits:
            for k, note in enumerate(voicing):   # a lazy roll from the bottom up
                s = at + off * beat + 0.014 * k + tr.rng.uniform(-0.012, 0.018)
                _ep(tr, "ep", s, length * beat, G._m_hz(note), vel * acc * tr.rng.uniform(0.85, 1.0),
                    pan=-0.25 + 0.15 * k)

    # Bars 2: one far chord through the wall, then nothing.
    for k, note in enumerate(loop[5][1]):
        _ep(tr, "ep", 2 * bar + 0.02 * k, 3.4 * beat, G._m_hz(note), 0.55)
    # The comping, section by section.
    for b in range(3, 43):
        if 19 <= b < 21:
            continue                                   # the stutter and the gap
        if b >= 37:
            if b % 2 == 1:
                comp(b, 0.55, sparse=True)            # only slow chords, every other bar
            continue
        thin = 25 <= b < 29                            # section B thins out as the tape sags
        comp(b, 0.6 if b < 11 else 0.7, sparse=thin and b % 2 == 0)
    # The last chord: G-flat maj7 held (a dropout will swallow it at bar 42.6).
    for k, note in enumerate(["Gb2", "F3", "Bb3", "Db4", "Eb4"]):
        _ep(tr, "ep", 41 * bar + 0.03 * k, 8 * beat, G._m_hz(note), 0.6, pan=-0.2 + 0.1 * k)

    # The melody (bars 11-19, and its second half again at 33-37): slow, lounge-y, chord tones and
    # 9ths, one per bar of the loop. (beat offset, note, length in beats)
    melody = [[(0, "G4", 1.5), (1.5, "Bb4", 0.5), (2, "D5", 2)],
              [(0.5, "E5", 1), (1.5, "D5", 0.5), (2, "C5", 2)],
              [(0, "A4", 3), (3.5, "Bb4", 0.5)],
              [(0, "D5", 1.5), (1.5, "Eb5", 0.5), (2, "G4", 2)],
              [(1, "C5", 1), (2, "Bb4", 1), (3, "G4", 1)],
              [(0, "F4", 2), (2.5, "Db5", 1.5)],
              [(0, "C5", 1.5), (1.5, "Eb5", 0.5), (2, "G5", 2)],
              [(0, "F5", 1.5), (2, "Eb5", 1), (3, "C5", 1)]]
    for i, phrase in enumerate(melody):
        for off, note, length in phrase:
            _ep(tr, "mel", (11 + i) * bar + off * beat + tr.rng.uniform(0, 0.03), length * beat,
                G._m_hz(note), 0.62, pan=0.3)
    for i, phrase in enumerate(melody[4:]):           # bars 33-36 play the loop's chords 4-7
        for off, note, length in phrase:
            _ep(tr, "mel", (33 + i) * bar + off * beat + tr.rng.uniform(0, 0.04), length * beat,
                G._m_hz(note), 0.5, pan=0.35)
    # Section B: a fragment of the melody an octave down, slower, as if the tape is tired.
    for off, note, length in melody[0] + [(4 + o, nn, ln) for o, nn, ln in melody[1]]:
        _ep(tr, "mel", 22 * bar + off * beat * 1.15, length * beat, G._m_hz(note) / 2, 0.55, pan=0.3)

    # The rhythm section (bars 3-43, gated later by how present the other-room speaker is):
    # bass on 1 and 3 with the bossa anticipations, brushes, a 3-2 clave on the cross-stick.
    for b in range(3, 43):
        root_hz = G._m_hz(loop[(b - 3) % 8][0])
        if root_hz < 60:
            root_hz *= 2                               # keep G1 up where the small speaker can play it
        nxt = G._m_hz(loop[(b - 2) % 8][0])
        fifth = _semi(root_hz, 7)
        at = b * bar
        for off, hz, length, v in ((0, root_hz, 1.4, 1.0), (1.5, root_hz, 0.45, 0.6),
                                   (2, fifth, 1.4, 0.9), (3.5, _semi(nxt, -1) if b % 4 == 3 else fifth, 0.45, 0.6)):
            _bass(tr, "bass", at + off * beat + tr.rng.uniform(0, 0.02), length * beat, hz, 0.5 * v)
        for off in (1, 3):
            _brush_tap(tr, "brush", at + off * beat + tr.rng.uniform(-0.01, 0.02), 0.35)
        if b % 2 == 1:
            for eighth in (0, 3, 6, 10, 13):           # the clave across two bars
                _rim(tr, "rim", at + eighth * beat / 2 + tr.rng.uniform(0, 0.015), 0.22)
    # The brushes' circular sweep: soft noise that swells once per beat.
    phase = (np.arange(n) / SR / beat) % 1.0
    sweep = G.fft_filter(tr.rng.normal(size=n), lambda f: G.bp_gain(f, 1500, 7000, 2))
    sweep = sweep / sweep.std() * (0.35 + 0.65 * np.sin(np.pi * phase) ** 2) * 0.05
    sweep *= _curve(n, [(0, 0), (3 * bar, 0), (3.2 * bar, 1), (43 * bar, 1), (43.2 * bar, 0), (total, 0)])
    tr.add("brush", 0.0, sweep, np.roll(sweep, 300))

    # How present everything is. near: 1 = in this room, 0 = muffled through the wall.
    # rhythm: the other room's speaker fading in and out (0 = gone).
    near = _curve(n, [(0, 0.0), (3 * bar, 0.0), (9 * bar, 0.25), (11 * bar, 0.7), (19 * bar, 0.75),
                      (21 * bar, 0.45), (29 * bar, 0.35), (31 * bar, 0.6), (37 * bar, 0.55),
                      (38 * bar, 0.15), (total, 0.1)])
    rhythm = _curve(n, [(0, 0.0), (6 * bar, 0.0), (10 * bar, 0.55), (12 * bar, 0.9), (19 * bar, 0.9),
                        (21 * bar, 0.7), (24 * bar, 0.6), (27 * bar, 0.0), (30 * bar, 0.0),
                        (32 * bar, 0.45), (35 * bar, 0.35), (37 * bar, 0.0), (total, 0.0)])
    cuts = [(9.3 * bar, 0.25), (14.7 * bar, 0.4), (17.2 * bar, 0.15),
            (19.0 * bar, 0.3), (19.4 * bar, 0.2), (19.75 * bar, 0.5), (20.3 * bar, 0.7 * bar),
            (24.5 * bar, 0.3), (27.8 * bar, 0.6),
            (30.6 * bar, 0.2), (31.1 * bar, 0.35), (33.4 * bar, 0.15), (33.7 * bar, 0.5), (36.2 * bar, 0.3),
            (39.9 * bar, 0.4), (42.6 * bar, total)]
    drop = _dropout_gain(n, cuts)
    # A tiny crackle when the speaker comes back (very quiet: a loose contact, not a pop).
    for s, length in cuts[:-1]:
        m = int(0.05 * SR)
        c = G.fft_filter(tr.rng.normal(size=m), lambda f: G.bp_gain(f, 900, 3500, 2))
        c = G.norm(c) * np.exp(-np.arange(m) / SR / 0.01) * 0.04
        tr.add("fx", s + length, *_pan(c, tr.rng.uniform(-0.3, 0.3)))

    # Through-the-wall sound: the near version is the instrument with a dull top; the far version
    # is mono (one speaker somewhere), thin and boxy, only the mids getting through the wall. The
    # brushes and the clave live up where that wall filter would erase them, so their 'far' is a
    # small, honky speaker instead (still thin, but you can hear them tick and swish).
    near_fn = lambda f: G.lp_gain(f, 3200, 2) * G.hp_gain(f, 70, 2)
    far_fn = lambda f: G.bp_gain(f, 170, 1000, 2) * (1 + 0.6 * np.exp(-((f - 450) / 220) ** 2))
    tinny_fn = lambda f: G.bp_gain(f, 220, 3200, 2) * (1 + 0.5 * np.exp(-((f - 1200) / 500) ** 2))
    for name in ("ep", "mel", "bass", "brush", "rim"):
        l, r = tr.buses[name]
        nl, nr = _filt(l, r, near_fn)
        mono = G.fft_filter(0.5 * (l + r), tinny_fn if name in ("brush", "rim") else far_fn)
        c = near if name in ("ep", "mel") else 0.5 * near   # the rhythm is always further away
        g = drop * (rhythm if name in ("bass", "brush", "rim") else 1.0)
        tr.buses[name] = ((nl * c + 1.4 * mono * (1 - c)) * g, (nr * c + 1.4 * mono * (1 - c)) * g)
    # The electric piano's slow stereo tremolo (a suitcase amp's auto-pan, at half speed).
    for name in ("ep", "mel"):
        l, r = tr.buses[name]
        wob = 0.28 * np.sin(2 * np.pi * 1.9 * np.arange(n) / SR)
        tr.buses[name] = (l * (1 + wob), r * (1 - wob))

    # The lights: 60 Hz mains hum with its harmonics, rock steady (the tape wobbles, the room does
    # not). It swells gently when the music drops out, as if it was always the loudest thing.
    t = np.arange(n) / SR
    hum_level = _curve(n, [(0, 0.0), (6, 1.0), (total - 16, 1.0), (total - 3, 0.0), (total, 0.0)])
    fill = G.fft_filter(1.0 - drop, lambda f: G.lp_gain(f, 1.5, 2))
    fill = np.clip(fill, 0, 1) * np.clip((t - 3 * bar) / bar, 0, 1)   # not during the intro
    hum_level = hum_level * (1 + 0.5 * fill) * (1 + G.smooth_jitter(tr.rng, n, 0.08, 0.12))
    hl = np.zeros(n)
    hr = np.zeros(n)
    # 60 Hz is felt more than heard; the 120-300 Hz harmonics are what small speakers can play.
    for k, a in enumerate((1.0, 0.8, 0.6, 0.25, 0.15, 0.08, 0.05), 1):
        hl += a * np.sin(2 * np.pi * 60 * k * t + 0.7 * k)
        hr += a * np.sin(2 * np.pi * 60 * k * t + 0.7 * k + 0.25 * k)
    tr.add("hum", 0.0, hl * hum_level, hr * hum_level)
    # The ballast buzz: a peaky 120 Hz pulse, thin and high, flickering now and then.
    pulse = np.abs(np.sin(2 * np.pi * 60 * t)) ** 12
    buzz = G.fft_filter(pulse - pulse.mean(), lambda f: G.bp_gain(f, 500, 4000, 2))
    flicker = 1 + 1.5 * np.clip(G.smooth_jitter(tr.rng, n, 0.6, 1.0) - 0.55, 0, None)
    buzz = G.norm(buzz) * hum_level * flicker
    tr.add("buzz", 0.0, buzz, np.roll(buzz, 40))

    tr.noise_bed("room", 30, 140, [(0, 0.0), (6, 0.012), (total, 0.012)], drift_amt=0.2)

    L, R = tr.master(mix={"ep": 1.0, "mel": 0.9, "bass": 0.55, "brush": 1.8, "rim": 1.4, "fx": 1.0,
                          "hum": 0.32, "buzz": 0.07, "room": 1.0},
                     reverb={"ep": 0.3, "mel": 0.35, "bass": 0.15, "brush": 0.5, "rim": 0.55, "fx": 0.2,
                             "buzz": 0.1},
                     room_s=2.8, room_lp=3000, lp=6000, drive=1.4)
    _save_music(L, R, "level0", fade_out=5.0)


# =============================================================================
# Level 1: "Concrete"
# =============================================================================

# Free-free steel bar (rebar, a beam): its vibration modes are inharmonic, which is what makes a
# struck bar sound like metal instead of a note.
_BAR = (1.0, 2.756, 5.404, 8.933, 13.344)
# A thick concrete-and-steel column rings more like a plate: modes packed closer together.
_PILLAR = (1.0, 1.59, 2.14, 2.30, 2.65, 2.92, 3.16)


def _struck(tr, bus: str, start: float, hz: float, vel: float, pan: float, modes, decay: float,
            lp: float = 2600.0) -> None:
    """Metal struck far away: each mode is a pair of slightly detuned sines (so it beats, like a
    real bar), the high modes die first, a short noise burst is the hammer. Filtered dark because
    it is far across the structure."""
    rng = tr.rng
    n = int(min(decay * 4.5, 16.0) * SR)
    t = np.arange(n) / SR
    ratio = tr._ratio(start, n)
    x = np.zeros(n)
    for k, m in enumerate(modes):
        fk = hz * m
        if fk > 6000:
            break
        tau = decay / (1 + 0.7 * k) * rng.uniform(0.8, 1.2)
        amp = rng.uniform(0.6, 1.0) / (1 + k) ** 0.5
        for d in (0.0, rng.uniform(0.0005, 0.0018)):
            ph = rng.uniform() + np.cumsum(fk * (1 + d) * ratio) / SR
            x += 0.5 * amp * np.sin(2 * np.pi * ph) * np.exp(-t / tau)
    m = int(0.03 * SR)
    hit = G.fft_filter(rng.normal(size=m), lambda f: G.bp_gain(f, 300, 4000, 2)) * np.exp(-t[:m] / 0.004)
    x[:m] += 0.6 * G.norm(hit)
    x = G.fft_filter(x * np.clip(t / 0.002, 0, 1), lambda f: G.lp_gain(f, lp, 2) * G.hp_gain(f, 60, 2))
    x = G.norm(x) * vel
    x[-int(0.05 * SR):] *= np.linspace(1, 0, int(0.05 * SR))
    tr.add(bus, start, *_pan(x, pan))


def _thud(tr, bus: str, start: float, vel: float, pan: float) -> None:
    """The dull body of something heavy hitting a concrete pillar: a short falling low thump."""
    n = int(1.2 * SR)
    t = np.arange(n) / SR
    f = 42 + 30 * np.exp(-t / 0.08)
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.25)
    nz = G.fft_filter(tr.rng.normal(size=n), lambda fr: G.bp_gain(fr, 70, 400, 2))
    x = G.norm(x) + 0.4 * G.norm(nz) * np.exp(-t / 0.08)
    x = x * np.clip(t / 0.004, 0, 1) * vel
    tr.add(bus, start, *_pan(x, pan))


def _drip(tr, bus: str, start: float, hz: float, vel: float, pan: float) -> None:
    """A water drop landing in a puddle: a tiny pitched 'plip' whose pitch rises as the bubble
    closes, with a click at the start."""
    n = int(0.15 * SR)
    t = np.arange(n) / SR
    f = hz * (1 + 0.45 * (1 - np.exp(-t / 0.015)))
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.03) * np.clip(t / 0.001, 0, 1)
    x += 0.25 * tr.rng.normal(size=n) * np.exp(-t / 0.0015)
    x[-int(0.01 * SR):] *= np.linspace(1, 0, int(0.01 * SR))
    tr.add(bus, start, *_pan(x * vel, pan))


def _alarm(tr, bus: str, start: float, hi: str, lo: str, tones: int, step: float, vel: float,
           pan: float, sag_cents: float = 0.0, slow: float = 1.0) -> None:
    """A car alarm somewhere far away in the structure, reduced to its bones: two tones a minor
    second apart, high-low-high-low. A hollow square-ish tone (odd harmonics), fading in and out.
    sag_cents/slow: pitch and speed drift down over the motif, like a dying battery."""
    at = start
    for i in range(tones):
        frac = i / max(1, tones - 1)
        hz = G._m_hz(hi if i % 2 == 0 else lo) * 2 ** (sag_cents * frac / 1200)
        length = step * (1 + (slow - 1) * frac)
        n = int(length * SR)
        t = np.arange(n) / SR
        ph = tr.rng.uniform() + np.cumsum(hz * tr._ratio(at, n)) / SR
        x = sum(np.sin(2 * np.pi * k * ph) / k for k in (1, 3, 5, 7))
        env = np.clip(t / 0.012, 0, 1) * np.clip((length * 0.9 - t) / 0.03, 0, 1)
        shape = np.sin(np.pi * (i + 0.5) / tones) ** 0.7       # the whole motif swells and fades
        tr.add(bus, at, *_pan(x * env * vel * shape, pan))
        at += length


def snd_music_level1() -> None:
    """Level 1, "Concrete", ~2 min 50 s. Cold industrial ambient for a huge damp parking structure.
    Key: C-sharp Dorian (minor, with the raised sixth A-sharp that gives its major IV chord, F-sharp,
    a hint of light). Pulse: 60 bpm, carried only by the ventilation: filtered noise that breathes
    in and out once a second.

    Instruments: a sub drone on C#1 with C#2 and G#2 over it (so it can be heard on small speakers);
    metal struck somewhere far away (rebar and pillars, long inharmonic rings tuned to the scale);
    a slow pad of Dorian chords (C#m9, F#7/C#, B add9; later E maj9, F#9, G#m7, A#m7b5) that swells
    and recedes; a distant car alarm reduced to a minor second (B/A#, then E/D#) as the motif; and
    drips: two tuned drips with slightly different periods that line up with the vent's pulse and
    then drift out of time with it, plus stray ones. A big, long, dark reverb holds it all.

    Structure (seconds): 0-28 the drone, air and the first far metal; 20-36 the vent starts
    breathing; 28-60 the pad's first chords, drips falling into time, the alarm far left at 50;
    62-100 the pad's widest swell (F#9 at 72), the metal striking every 8 s on the beat, the alarm
    far right at 90; 100-108 the fan winds down and stops; 108-124 only the drone, drips out of
    time and one huge pillar ring; 122-160 the vent returns faintly, the pad comes back low, the
    alarm a last time, slower and sagging flat; then the drone and drips fade."""
    total = 172.0
    tr = G._MTrack(total, seed=13011, wow=0.0022, flutter=0.0005)
    n = tr.n
    t = np.arange(n) / SR

    # The sub drone on C#, with audible upper partials; it thins (but never stops) mid-piece.
    tr.drone("drone", 0.0, total, "C#1", 0.42, fade=16.0, beat=0.23)
    tr.drone("drone", 4.0, 96.0, "C#2", 0.20, fade=14.0, beat=0.31)
    tr.drone("drone", 116.0, total - 116.0, "C#2", 0.17, fade=14.0, beat=0.27)
    tr.drone("drone", 30.0, 70.0, "G#2", 0.07, fade=18.0, beat=0.19)

    # The ventilation: noise breathing once per second. Its tempo curve lets the fan wind down at
    # 100-108 s; phase is the running count of breaths.
    rate = _curve(n, [(0, 1.0), (100, 1.0), (106, 0.55), (108, 0.4), (122, 1.0), (total, 1.0)])
    phase = np.cumsum(rate) / SR
    breath = np.sin(np.pi * (phase % 1.0) ** 0.75) ** 2          # quick in, slower out
    vent_level = _curve(n, [(0, 0), (20, 0), (36, 1.0), (60, 1.0), (75, 0.8), (100, 0.9), (106, 0.4),
                            (108.5, 0.0), (122, 0.0), (134, 0.55), (150, 0.5), (162, 0.0), (total, 0.0)])
    for ch in range(2):
        dark = G.fft_filter(tr.rng.normal(size=n), lambda f: G.bp_gain(f, 140, 700, 2))
        bright = G.fft_filter(tr.rng.normal(size=n), lambda f: G.bp_gain(f, 450, 2400, 2))
        v = (dark / dark.std() * (0.1 + 0.9 * breath) + 0.7 * bright / bright.std() * breath ** 2)
        v *= vent_level * 0.11
        tr.add("vent", 0.0, *((v, np.zeros(n)) if ch == 0 else (np.zeros(n), v)))

    # The pad: Dorian chords that swell in and recede, one every 8-10 s. (start, length, notes, vel)
    pads = [(28, 9, ["C#3", "G#3", "B3", "D#4", "E4"], 0.17),     # C#m9
            (36, 9, ["C#3", "F#3", "A#3", "E4"], 0.19),           # F#7 over C# (Dorian's major IV)
            (44, 9, ["B2", "F#3", "C#4", "D#4"], 0.19),           # B add9
            (52, 11, ["C#3", "G#3", "D#4", "E4"], 0.16),          # C#m add9
            (64, 9, ["E3", "B3", "D#4", "F#4", "G#4"], 0.21),     # E maj9
            (72, 9, ["F#3", "A#3", "E4", "G#4", "C#5"], 0.25),    # F#9: the widest swell
            (80, 9, ["G#2", "F#3", "B3", "D#4"], 0.23),           # G#m7
            (88, 9, ["A#2", "G#3", "C#4", "E4"], 0.19),           # A#m7b5: tension, left hanging
            (96, 9, ["C#3", "B3", "D#4", "E4"], 0.13),            # C#m9, low, receding
            (124, 11, ["C#3", "G#3", "D#4", "E4"], 0.13),
            (134, 10, ["C#3", "B3", "D#4", "F#4"], 0.15),         # B over C#: a soft cluster
            (144, 10, ["C#3", "F#3", "A#3", "C#4"], 0.14),        # F# over C#: a glimmer
            (154, 12, ["C#3", "G#3", "D#4"], 0.11)]
    for s, length, notes, vel in pads:
        tr.chord("pad", s, length, notes, vel, bright=1200, tilt=1.4, attack=4.5, release=6.0,
                 spread=0.6, cents=(-11.0, 0.0, 10.0))
    for s, root in ((64, "E2"), (72, "F#2"), (80, "G#1"), (88, "A#1")):   # the bass moves only here
        tr.pad("bass", s, 8.5, root, 0.3, bright=260, attack=3.0, release=4.0)

    # Metal struck far away. Mid-piece it keeps time with the vent (every 8 s, on the beat).
    strikes = [(6, "bar", "C#4", 0.5, -0.6), (19, "bar", "G#3", 0.4, 0.7), (34, "pillar", "C#2", 0.5, -0.2),
               (44, "bar", "E4", 0.35, 0.5), (52.5, "bar", "C#4", 0.3, -0.7),
               (64, "bar", "B3", 0.45, 0.3), (72, "bar", "G#3", 0.42, -0.5), (80, "bar", "F#3", 0.45, 0.6),
               (88, "bar", "C#4", 0.4, -0.3), (96, "bar", "D#4", 0.35, 0.7),
               (104, "pillar", "G#1", 0.75, 0.1), (113, "bar", "A#3", 0.3, -0.6),
               (128, "bar", "C#4", 0.3, 0.4), (140, "bar", "G#3", 0.3, -0.5), (152, "bar", "C#4", 0.25, -0.4),
               (160, "pillar", "C#2", 0.3, 0.3)]
    for s, kind, note, vel, pan in strikes:
        if kind == "bar":
            _struck(tr, "metal", s, G._m_hz(note), vel, pan, _BAR, decay=7.0, lp=2600)
        else:
            _thud(tr, "metal", s, vel * 0.8, pan)
            _struck(tr, "metal", s + 0.01, G._m_hz(note), vel * 0.7, pan, _PILLAR, decay=5.5, lp=1400)

    # The far car alarm, reduced to a minor second: three times, the last one slow and sagging.
    _alarm(tr, "alarm", 50.0, "B4", "A#4", 12, 0.5, 0.2, -0.75)
    _alarm(tr, "alarm", 90.0, "E5", "D#5", 12, 0.5, 0.17, 0.7)
    _alarm(tr, "alarm", 138.0, "B4", "A#4", 10, 0.62, 0.18, -0.5, sag_cents=-70, slow=1.5)

    # Drips. Two tuned drips: one exactly every 2 s on the vent's grid (offbeat), one every 1.87 s
    # that slides against it and only lines up now and then. Plus stray drips everywhere.
    for s in np.arange(36.5, 100.0, 2.0):
        _drip(tr, "drip", float(s) + tr.rng.uniform(-0.004, 0.004), G._m_hz("A#6"), 0.13, -0.45)
    for s in np.arange(55.0, 158.0, 1.87):
        _drip(tr, "drip", float(s), G._m_hz("F#6"), 0.11, 0.55)
    for s in np.sort(tr.rng.uniform(4.0, 166.0, 34)):
        _drip(tr, "drip", float(s), G._m_hz(str(tr.rng.choice(["C#7", "G#6", "D#7", "B6"]))),
              float(tr.rng.uniform(0.05, 0.11)), float(tr.rng.uniform(-0.85, 0.85)))

    # The space: cold air moving through the levels and a far low rumble (the structure itself).
    tr.noise_bed("air", 250, 3200, [(0, 0.0), (8, 0.016), (60, 0.02), (100, 0.018), (110, 0.026),
                                    (150, 0.02), (total, 0.014)], drift_amt=0.5)
    tr.noise_bed("rumble", 25, 110, [(0, 0.0), (10, 0.02), (total, 0.02)], drift_amt=0.3)

    # Dark, distant colour on the far sounds; the pad gets a dull, damp top.
    for name, fn in (("pad", lambda f: G.lp_gain(f, 1500, 2)),
                     ("alarm", lambda f: G.bp_gain(f, 450, 1800, 2)),
                     ("drip", lambda f: G.lp_gain(f, 5000, 2))):
        tr.buses[name] = _filt(*tr.buses[name], fn)

    L, R = tr.master(mix={"drone": 0.6, "vent": 1.0, "pad": 1.0, "bass": 0.6, "metal": 0.5, "alarm": 0.9,
                          "drip": 0.5, "air": 1.0, "rumble": 1.0},
                     reverb={"drone": 0.05, "vent": 0.12, "pad": 0.6, "bass": 0.1, "metal": 0.9,
                             "alarm": 1.0, "drip": 1.0},
                     room_s=9.0, room_lp=3400, lp=6000, drive=1.35)
    _save_music(L, R, "level1", fade_out=8.0)


SOUNDS = [snd_music_level0, snd_music_level1]
