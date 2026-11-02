"""Level music, group E: Level 490 "Penance" and Level 9 "Suburbs".

Two ORIGINAL pieces, composed note by note in code like the main theme (gen_assets.py, the
"Music" section) and megaflora (level227_audio.py). Nothing is sampled, and no existing melody is
quoted; the style notes below describe moods only.

  music/level490   "Penance", ~2:47. Level 490: crumbling ruins on floating islands over a sea
                   of storm clouds at night, rain, giant chains, a long climb. A slow processional
                   for a synthesized pipe organ, a low male choir, a tolling bell, distant thunder,
                   rattling chains and a solo bowed-string lament.
  music/level9     "Suburbs", ~2:22. Level 9: an endless suburb at midnight, thick fog, dead
                   sodium streetlamps; the last level, almost home. A warped 1980s analog-synth
                   lullaby on a worn cassette that, at the end, slows down and dies.

Both go through the same worn-tape master as every other piece (gen_assets._MTrack.master and
_m_save), so the whole soundtrack sounds like one old tape. Each piece is brought to the loudness
of the existing music (about -16 LUFS integrated, the average of liminal, deep and megaflora) by
choosing the peak level _m_save normalises to; peaks stay well under -1 dBFS.

Run only these with:  python tools/gen_assets.py musice_
(on this Windows machine through the %TEMP% wrapper that puts ffmpeg on PATH). Fixed random seeds
(49001, 9001) make every re-run identical.
"""
from __future__ import annotations

import numpy as np

import gen_assets as G
from anomaly_assets import level2_audio as A

SR = G.SR
MUSIC = G.SND / "music"
TARGET_LUFS = -16.0   # average integrated loudness of liminal (-16.8), deep (-16.4), megaflora (-14.8)


# =============================================================================
# Shared helpers
# =============================================================================

def _pan(x, pan):
    """Equal-power pan: -1 is hard left, +1 hard right (the same law _MTrack uses)."""
    return x * np.sqrt(0.5 * (1 - pan)), x * np.sqrt(0.5 * (1 + pan))


def _mono(pts):
    """Splits (time, value) points into two arrays for np.interp, nudging any time that is not
    later than the one before (np.interp needs strictly rising times; very short notes can make
    two points land on the same moment)."""
    ts, vs = [], []
    for t, v in pts:
        if ts and t <= ts[-1]:
            t = ts[-1] + 1e-4
        ts.append(t)
        vs.append(v)
    return np.array(ts), np.array(vs)


def _swell_env(n, attack, release):
    """A smooth (S-curve) fade in over `attack` seconds and out over the last `release` seconds,
    used for long sung notes that should breathe in and out rather than stop."""
    t = np.arange(n) / SR
    a = np.clip(t / max(attack, 1e-3), 0, 1)
    r = np.clip((n / SR - t) / max(release, 1e-3), 0, 1)
    return a * a * (3 - 2 * a) * r * r * (3 - 2 * r)


def _groups(phrase, beat):
    """A phrase is a list of (note, beats) or (note, beats, loudness) with None for a rest.
    Rests split it into legato groups (one breath or one bow stroke series each). Yields
    (start of the group in seconds, [(onset within the group, length, Hz, loudness), ...])."""
    cursor = 0.0
    group, g_start = [], 0.0
    for item in phrase:
        note, beats = item[0], item[1]
        dyn = item[2] if len(item) > 2 else 1.0
        if note is None:
            if group:
                yield g_start, group
                group = []
        else:
            if not group:
                g_start = cursor
            group.append((cursor - g_start, beats * beat, G._m_hz(note), dyn))
        cursor += beats * beat
    if group:
        yield g_start, group


def _curves(notes, n, glide, attack, dip, end_level, vib, vib_delay, release):
    """Per-sample control curves for one legato group of a solo line:
    - pitch (log2 Hz) that slides into each new note over `glide` seconds (a finger sliding on a
      string, or a synth's portamento),
    - loudness: each note swells up over `attack`, sags to `end_level`, and the next note starts
      from `dip` of that (a bow change, or a synth re-triggering); the last note fades over `release`,
    - vibrato depth: none at first, then it blooms after `vib_delay` (as a player warms a note)."""
    lf_p, a_p, v_p = [], [], []
    prev = 0.0
    for i, (s, d, hz, dyn) in enumerate(notes):
        lf = np.log2(hz)
        if i == 0:
            lf_p.append((s, lf))
        lf_p += [(s + glide, lf), (s + d, lf)]
        att = min(attack, 0.45 * d)
        a_p += [(s, 0.0 if i == 0 else dip * prev), (s + att, dyn), (s + d - 0.05, dyn * end_level)]
        prev = dyn * end_level
        vd = min(vib_delay, 0.5 * d)
        v_p += [(s, 0.0), (s + vd, 0.0), (s + min(d - 0.06, vd + 0.6), vib), (s + d - 0.05, vib)]
    end = notes[-1][0] + notes[-1][1]
    a_p.append((end + release, 0.0))
    t = np.arange(n) / SR
    return (np.interp(t, *_mono(lf_p)), np.interp(t, *_mono(a_p)), np.interp(t, *_mono(v_p)))


def _filter_bus(tr, name, gain_fn):
    """Runs one bus (both channels) through a zero-phase filter, before the master."""
    if name in tr.buses:
        l, r = tr.buses[name]
        tr.buses[name] = (G.fft_filter(l, gain_fn), G.fft_filter(r, gain_fn))


def _stereo_lufs(L, R) -> float:
    """Integrated loudness (ITU BS.1770: K-weighting, 400 ms blocks, the two gates) of a stereo
    signal: the two channels' powers are summed, as the standard does. The K-weighting filter is
    level2_audio's, the meter the rest of the project uses for its sounds."""
    y = A._k_weight(np.asarray(L, float)) ** 2 + A._k_weight(np.asarray(R, float)) ** 2
    block, hop = int(0.4 * SR), int(0.1 * SR)
    cs = np.concatenate([[0.0], np.cumsum(y)])
    starts = np.arange(0, len(y) - block + 1, hop)
    z = (cs[starts + block] - cs[starts]) / block
    z = z[-0.691 + 10 * np.log10(z + 1e-20) > -70.0]
    rel = -0.691 + 10 * np.log10(z.mean()) - 10.0
    z = z[-0.691 + 10 * np.log10(z) > rel]
    return float(-0.691 + 10 * np.log10(z.mean()))


def _save_music(L, R, name, fade_out, fade_in=0.5):
    """Writes music/<name>.ogg through the shared _m_save (fades, block-wise Vorbis), choosing the
    peak level it normalises to so the piece lands at TARGET_LUFS like the other music. _m_save
    scales the whole piece to one peak, so loudness depends on how spiky the piece is: measure it
    once at the house peak (0.62), then scale. Capped at 0.8 (-1.9 dBFS) so the encoder's small
    overshoot still stays under -1 dBFS."""
    x = np.stack([L, R], axis=1)
    x = x - x.mean(axis=0)
    fi, fo = int(fade_in * SR), int(fade_out * SR)
    x[:fi] *= np.linspace(0, 1, fi)[:, None]
    x[-fo:] *= (np.linspace(1, 0, fo) ** 2)[:, None]
    x = x / np.abs(x).max() * 0.62
    loud = _stereo_lufs(x[:, 0], x[:, 1])
    peak = 0.62 * 10 ** ((TARGET_LUFS - loud) / 20)
    if peak > 0.8:
        print(f"  {name}: too spiky to reach {TARGET_LUFS} LUFS under the peak cap; "
              f"lands at {loud + 20 * np.log10(0.8 / 0.62):.1f}")
        peak = 0.8
    G._m_save(L, R, MUSIC / f"{name}.ogg", peak=peak, fade_in=fade_in, fade_out=fade_out)


# =============================================================================
# Level 490 "Penance": instruments
# =============================================================================

# Pipe-organ stops as (harmonic number, level). A stopped flute pipe is closed at the top, so it
# speaks mostly odd harmonics (soft and hollow); a principal is the bright, singing open pipe; the
# 4' octave doubles it an octave up; the mixture adds high fifths and octaves (the organ's "crown").
_FLUTE = [(1, 1.0), (2, 0.10), (3, 0.30), (5, 0.10), (7, 0.04)]
_PRINCIPAL = [(k, a) for k, a in zip(range(1, 11), (1.0, 0.55, 0.36, 0.25, 0.18, 0.13, 0.09, 0.07, 0.05, 0.04))]
_OCTAVE = [(2 * k, 0.45 * a) for k, a in _PRINCIPAL[:6]]
_MIXTURE = [(3, 0.16), (4, 0.14), (6, 0.11), (8, 0.08), (12, 0.05), (16, 0.03)]
_ORGAN_REG = {
    "soft": _FLUTE,
    "choir": _FLUTE + [(k, 0.6 * a) for k, a in _PRINCIPAL],
    "full": _FLUTE + _PRINCIPAL + _OCTAVE + _MIXTURE,
    # pedal: a 16' bourdon (written at its sounding pitch) with an 8' principal for definition
    "pedal": [(1, 1.0), (2, 0.45), (3, 0.22), (4, 0.12), (5, 0.06)],
}


def _organ(tr, bus, start, dur, note, vel, reg="soft", attack=0.35, release=0.7):
    """One organ pipe set sounding `note`: additive partials from the chosen stops, each rank a
    hair out of tune with the others (real ranks never agree exactly), the high partials speaking
    a moment before the fundamental, and a little breathy "chiff" as the pipe starts. Organs have
    no vibrato; only the tape wobbles it. Pipes alternate between the two sides of the organ case
    by pitch, as real organs lay them out, so chords spread across the stereo field."""
    rng = tr.rng
    freq = G._m_hz(note)
    n = int((dur + release) * SR)
    t = np.arange(n) / SR
    base = np.cumsum(freq * tr._ratio(start, n)) / SR   # the fundamental's phase, in cycles
    x = np.zeros(n)
    total = 0.0
    for mult, amp in _ORGAN_REG[reg]:
        if freq * mult > 7500:
            continue
        detune = 2 ** (rng.uniform(-1.5, 1.5) / 1200)
        speech = np.clip(t / (0.02 + 0.10 / mult), 0, 1)
        x += amp * speech * np.sin(2 * np.pi * (base * mult * detune + rng.uniform()))
        total += amp
    x /= total
    m = int(0.12 * SR)
    chiff = G.fft_filter(rng.normal(size=m), lambda f: G.bp_gain(f, freq * 2, freq * 6, 2))
    x[:m] += chiff / (np.abs(chiff).max() + 1e-9) * np.exp(-np.arange(m) / SR / 0.025) * 0.05
    x *= G._m_env(n, attack, dur, release) * vel
    midi = int(round(69 + 12 * np.log2(freq / 440.0)))
    tr.add(bus, start, *_pan(x, 0.35 if midi % 2 else -0.35))


def _organ_chord(tr, start, dur, notes, pedal, vel, reg, pedal_vel=None, attack=0.35):
    """A chord on the manuals (bus "organ") and its bass note on the pedals (bus "pedal")."""
    for note in notes:
        _organ(tr, "organ", start + tr.rng.uniform(0, 0.03), dur, note, vel, reg, attack=attack)
    if pedal:
        _organ(tr, "pedal", start, dur, pedal, vel if pedal_vel is None else pedal_vel, "pedal",
               attack=attack * 1.3)


# Male voice formants (Hz, width, level) for "oo", "oh" and "ah". Lower than the sun's choir in
# level227_audio (those are higher, lighter voices); "ah" keeps a small singer's formant near 2.9 kHz.
_MALE = {
    "u": [(300, 70, 1.0), (870, 90, 0.30), (2250, 160, 0.05), (3300, 250, 0.02)],
    "o": [(450, 80, 1.0), (800, 90, 0.55), (2600, 170, 0.07), (3300, 250, 0.03)],
    "a": [(700, 90, 1.0), (1150, 110, 0.55), (2500, 170, 0.12), (2900, 220, 0.06)],
}


def _formant_gain(k, hz, forms):
    f = k * hz
    return 0.02 / k + sum(g * np.exp(-0.5 * ((f - F) / bw) ** 2) for F, bw, g in forms)


def _choir(tr, bus, start, dur, note, vel, vowel="u", to=None, voices=3, pan=0.0, attack=3.0,
           release=3.0):
    """A section of low male voices on one note: `voices` singers, each slightly out of tune with
    the others, with his own slow vibrato and drift, spread a little across the stereo field. The
    vowel can open slowly from one to another over the note (e.g. "oo" to "ah"). Additive: every
    harmonic of the voice gets the level the vowel's formants give it (worked out once per note,
    which is cheap; the pitch moves so little that the colour hardly changes)."""
    rng = tr.rng
    hz = G._m_hz(note)
    n = int(dur * SR)
    t = np.arange(n) / SR
    ratio = tr._ratio(start, n)
    morph = None
    if to is not None:
        u = np.clip(t / dur, 0, 1)
        morph = u * u * (3 - 2 * u)
    nh = int(min(48, 3600 / hz))
    env = _swell_env(n, attack, release) * vel / voices
    for v in range(voices):
        det = 2 ** (rng.uniform(-7, 7) / 1200)
        f0 = hz * det * ratio * (1 + 0.0035 * np.sin(2 * np.pi * rng.uniform(4.4, 5.4) * t + rng.uniform(0, 6.3))
                                 + G.smooth_jitter(rng, n, 0.4, 0.0025))
        ph = 2 * np.pi * np.cumsum(f0) / SR
        x = np.zeros(n)
        for k in range(1, nh + 1):
            a = _formant_gain(k, hz, _MALE[vowel])
            if morph is None:
                amp = a
            else:
                amp = a + (_formant_gain(k, hz, _MALE[to]) - a) * morph
            if np.max(amp) < 2e-3:
                continue
            x += amp * np.sin(k * ph + rng.uniform(0, 2 * np.pi))
        x /= np.abs(x).max() + 1e-9
        breath = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 500, 3000, 2))
        x += 0.05 * breath / (np.abs(breath).max() + 1e-9)
        tr.add(bus, start, *_pan(x * env, np.clip(pan + (v - (voices - 1) / 2) * 0.35, -1, 1)))


# A big tower bell's partials relative to its "prime" (multiple, level, decay seconds): the hum an
# octave below rings longest, the minor-third "tierce" is what makes church bells sound mournful,
# the "nominal" an octave up is the note you hear as the strike.
_BELL = [(0.5, 1.0, 14.0), (1.0, 0.8, 9.0), (1.19, 0.55, 6.5), (1.5, 0.35, 4.5), (2.0, 0.6, 4.0),
         (2.52, 0.25, 2.4), (2.66, 0.2, 2.0), (3.01, 0.18, 1.6), (4.07, 0.12, 1.1), (5.4, 0.06, 0.7)]


def _toll(tr, bus, start, note, vel, pan=0.0):
    """One stroke of a deep tolling bell. Each partial is really two a fraction of a hertz apart
    (a cast bell is never perfectly round), so the tone slowly beats and warbles as it dies."""
    rng = tr.rng
    f = G._m_hz(note)
    n = int(16.0 * SR)
    t = np.arange(n) / SR
    ph = np.cumsum(tr._ratio(start, n)) / SR   # seconds of tape time
    x = np.zeros(n)
    for mult, amp, dk in _BELL:
        w = rng.uniform(0.2, 0.9)
        for split in (-0.5 * w, 0.5 * w):
            x += 0.5 * amp * np.sin(2 * np.pi * (f * mult + split) * ph + rng.uniform(0, 6.3)) * np.exp(-t / dk)
    m = int(0.2 * SR)
    thunk = G.fft_filter(rng.normal(size=m), lambda fr: G.lp_gain(fr, 1500, 2)) * np.exp(-np.arange(m) / SR / 0.03)
    x[:m] += 0.15 * thunk / (np.abs(thunk).max() + 1e-9)
    x = x / np.abs(x).max() * np.clip(t / 0.003, 0, 1) * vel
    tr.add(bus, start, *_pan(x, pan))


def _thunder(tr, bus, start, vel, dur=10.0, pan=0.0):
    """Thunder miles away: no crack, only a low rolling rumble made of several overlapping bursts
    that swell and die, slightly different in each ear."""
    rng = tr.rng
    n = int(dur * SR)
    t = np.arange(n) / SR
    env = np.zeros(n)
    for _ in range(rng.integers(4, 8)):
        c = rng.uniform(0, dur * 0.45)
        u = np.clip(t - c, 0, None)
        env += rng.uniform(0.3, 1.0) * (t > c) * (1 - np.exp(-u / 0.25)) * np.exp(-u / rng.uniform(0.9, 2.6))
    env /= env.max() + 1e-9
    env *= np.clip((dur - t) / 1.0, 0, 1)
    chans = []
    for _ in range(2):
        low = G.fft_filter(rng.normal(size=n), lambda f: G.lp_gain(f, 150, 3) * G.hp_gain(f, 24, 2))
        mid = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 200, 650, 2))
        chans.append((low / low.std() + 0.12 * mid / mid.std()) * env)
    peak = max(np.abs(chans[0]).max(), np.abs(chans[1]).max())
    l, r = (c / peak * vel for c in chans)
    tr.add(bus, start, l * np.sqrt(0.5 * (1 - pan)), r * np.sqrt(0.5 * (1 + pan)))


def _chains(tr, bus, start, vel, pan=0.0, heavy=True):
    """Giant chains shifting: an optional heavy clank (a link the size of a door), then a run of
    small metal clinks that bunch up and thin out as the chain settles."""
    rng = tr.rng
    n = int(2.2 * SR)
    L = np.zeros(n)
    R = np.zeros(n)

    def hit(at, freqs, amps, decay, level, p):
        m = int(decay * 6 * SR)
        tt = np.arange(m) / SR
        x = sum(a * np.sin(2 * np.pi * f * tt + rng.uniform(0, 6.3)) * np.exp(-tt / (decay / (1 + 0.6 * j)))
                for j, (f, a) in enumerate(zip(freqs, amps)))
        x = x * np.clip(tt / 0.0015, 0, 1) * level
        i0 = int(at * SR)
        end = min(n, i0 + m)
        l, r = _pan(x[: end - i0], np.clip(p, -1, 1))
        L[i0:end] += l
        R[i0:end] += r

    if heavy:
        f = rng.uniform(380, 600)
        hit(0.0, [f, f * 2.71, f * 5.13], [1.0, 0.5, 0.2], 0.35, 1.0, pan)
        m = int(0.3 * SR)
        thud = G.fft_filter(rng.normal(size=m), lambda fr: G.lp_gain(fr, 400, 2)) * np.exp(-np.arange(m) / SR / 0.06)
        thud = 0.6 * thud / (np.abs(thud).max() + 1e-9)
        L[:m] += thud
        R[:m] += thud
    times = np.sort(rng.gamma(1.6, 0.16, rng.integers(10, 22)))
    for tc in times[times < 1.6]:
        f = rng.uniform(1600, 4800)
        hit(0.05 + tc, [f, f * 2.32, f * 3.81], [1.0, 0.5, 0.25], rng.uniform(0.03, 0.09),
            rng.uniform(0.25, 0.7) * np.exp(-tc / 0.6), pan + rng.uniform(-0.25, 0.25))
    peak = max(np.abs(L).max(), np.abs(R).max()) + 1e-9
    lp = lambda fr: G.lp_gain(fr, 5500, 2)
    tr.add(bus, start, G.fft_filter(L, lp) / peak * vel, G.fft_filter(R, lp) / peak * vel)


def _string_body(f):
    """A bowed instrument's wooden body: a few broad resonances (log-frequency bumps) over a floor,
    with the lowest lows and the highest fizz rolled off."""
    lf = np.log2(np.maximum(f, 1.0))
    g = 0.25 + sum(a * np.exp(-0.5 * ((lf - np.log2(fc)) / 0.28) ** 2)
                   for fc, a in ((290, 0.7), (470, 1.0), (1050, 0.75), (2600, 0.6), (3600, 0.3)))
    return g * G.hp_gain(f, 180, 2) * G.lp_gain(f, 6000, 2)


def _bowed(tr, bus, start, beat, phrase, vel, pan=0.0):
    """A solo bowed string (violin/viola-like) playing `phrase` (see _groups) legato: a sawtooth
    (what a bow does to a string) whose pitch slides between notes, swells on each bow stroke, gets
    a vibrato that blooms late in each note, then sounds through the instrument's body, with a
    little bow hiss on top."""
    rng = tr.rng
    for off, notes in _groups(phrase, beat):
        dur = notes[-1][0] + notes[-1][1] + 1.0
        n = int(dur * SR)
        t = np.arange(n) / SR
        lf, amp, vib = _curves(notes, n, glide=0.11, attack=0.6, dip=0.55, end_level=0.8,
                               vib=0.0045, vib_delay=0.3, release=0.9)
        f0 = (2 ** lf * (1 + vib * np.sin(2 * np.pi * 5.3 * t + rng.uniform(0, 6.3)))
              * tr._ratio(start + off, n) * (1 + G.smooth_jitter(rng, n, 1.5, 0.0012)))
        ph = 2 * np.pi * np.cumsum(f0) / SR
        x = np.zeros(n)
        for k in range(1, int(7000 / f0.min()) + 1):
            x += np.clip((7000 - k * f0) / 1500, 0, 1) / k * np.sin(k * ph + rng.uniform(0, 6.3))
        x = G.fft_filter(x, _string_body)
        x /= np.abs(x).max() + 1e-9
        hiss = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 2500, 7000, 2))
        x += 0.03 * hiss / (hiss.std() * 4 + 1e-9)
        tr.add(bus, start + off, *_pan(x * amp * vel, pan))


# =============================================================================
# Level 490 "Penance"
# =============================================================================

def snd_musice_level490() -> None:
    """"Penance", ~2:47, for Level 490: a pilgrimage. F minor (natural minor, with a major C chord
    as the dominant), 52 beats a minute in slow 4/4 (one bar = 4.6 s), 35 bars.

    Intro (bars 0-4): rain and far thunder; a low male choir hums "oo" on F and then C; a deep
    bell tolls. A (4-12): the organ enters softly on flute stops, a processional of plain chords
    (F minor, D-flat, B-flat minor, C, F minor, G-flat, E-flat minor, C) over a pedal bass; the
    bell tolls every other bar; chains shift somewhere. B (12-20): the same procession a little
    fuller, and a solo bowed string sings the lament, two phrases. C (20-27), the climb: full organ
    with mixtures, the bass climbing step by step (F, G, A-flat, B-flat, C, D-flat) under a three-
    part choir that opens to "ah", the lament rising with it, the bell on every bar, thunder and
    chains at the peak on a held C major. D (27-31): it falls back to soft flutes and a last
    fragment of the lament. Outro (31-35): only an open fifth (F and C, no third: neither major nor
    minor), the choir humming, the bell, the rain."""
    beat = 60.0 / 52.0
    bar = 4 * beat
    tr = G._MTrack(35 * bar + 6.0, seed=49001, wow=0.0025, flutter=0.0007)
    rng = tr.rng

    # --- Intro: the choir hums under the rain; the bell. -----------------------------------
    _choir(tr, "choir", 0.3, 12.6 * bar, "F2", 0.40, "u", voices=3, attack=10.0, release=5.0)
    _choir(tr, "choir", 1.5 * bar, 11.3 * bar, "C3", 0.28, "u", voices=3, pan=0.15, attack=9.0, release=5.0)

    # --- The procession (A, B and D use it). Each entry: (beats, manual chord, pedal note). ---
    fm, db, bbm = ["F3", "Ab3", "C4"], ["F3", "Ab3", "Db4"], ["F3", "Bb3", "Db4"]
    c_sus, c_maj = ["F3", "G3", "C4"], ["E3", "G3", "C4"]
    gb, ebm = ["Gb3", "Bb3", "Db4"], ["Gb3", "Bb3", "Eb4"]
    procession = [(4, fm, "F1"), (4, db, "Db2"), (4, bbm, "Bb1"), (4, c_maj, "C2"),
                  (4, fm, "F1"), (4, gb, "Gb1"), (4, ebm, "Eb2"), (2, c_sus, "C2"), (2, c_maj, "C2")]

    def play(at, items, vel, reg, pedal_vel, top=False):
        for beats, chord, ped in items:
            notes = chord + ([_up(chord[1])] if top else [])
            _organ_chord(tr, at, beats * beat + 0.12, notes, ped, vel, reg, pedal_vel)
            at += beats * beat

    def _up(note):   # the same note an octave higher (B adds the chord's third on top)
        return note[:-1] + str(int(note[-1]) + 1)

    # Levels: a flute stop is nearly a pure tone, so it carries more energy per note than its soft
    # sound suggests; these are set (by measuring each bus) so A < B < C, then D falls back.
    play(4 * bar, procession, 0.11, "soft", 0.18)                 # A
    play(12 * bar, procession, 0.14, "choir", 0.22, top=True)     # B
    play(27 * bar, procession[:4], 0.10, "soft", 0.15)            # D

    # B: the choir hums a soft "oh" on each chord's bass note, in the basses' own octave.
    for i, (beats, chord, ped) in enumerate(procession[:7]):
        _choir(tr, "choir", (12 + i) * bar, bar + 1.5, ped[:-1] + "2", 0.22, "o", voices=2,
               attack=1.5, release=1.5)

    # --- C, the climb: full organ over a bass rising step by step; a choir in three parts. ---
    climb = [(["F3", "Ab3", "C4", "F4"], "F1", ("F2", "C3", "Ab3")),
             (["G3", "Bb3", "Eb4", "G4"], "G1", ("G2", "Eb3", "Bb3")),
             (["Ab3", "C4", "Eb4", "Ab4"], "Ab1", ("Ab2", "Eb3", "C4")),
             (["Bb3", "Db4", "F4", "Bb4"], "Bb1", ("Bb2", "F3", "Db4")),
             (["C4", "E4", "G4", "C5"], "C2", ("C3", "G3", "E4")),
             (["Db4", "F4", "Ab4", "Db5"], "Db2", ("Db3", "Ab3", "F4")),
             (["C4", "E4", "G4", "C5"], "C2", ("C3", "G3", "E4"))]
    for i, (chord, ped, voices) in enumerate(climb):
        at = (20 + i) * bar
        grow = 0.8 + 0.2 * i / (len(climb) - 1)
        reg = "choir" if i < 2 else "full"
        _organ_chord(tr, at, bar + 0.12, chord, ped, 0.46 * grow, reg, 0.60 * grow, attack=0.5)
        for j, note in enumerate(voices):
            _choir(tr, "choir", at - 0.2, bar + 1.8, note, 0.50 * grow * (1.0, 0.8, 0.7)[j],
                   "o", to="a", voices=2, pan=(-0.3, 0.0, 0.3)[j], attack=1.2, release=1.4)

    # --- D and outro: an open fifth, the choir humming again. -------------------------------
    _organ_chord(tr, 31 * bar, 3.6 * bar, ["F3", "C4"], "F1", 0.09, "soft", 0.14, attack=1.5)
    _choir(tr, "choir", 30.0 * bar, 5.0 * bar + 3.0, "F2", 0.32, "u", voices=3, attack=6.0, release=8.0)
    _choir(tr, "choir", 30.8 * bar, 4.2 * bar + 3.0, "C3", 0.22, "u", voices=3, pan=-0.15, attack=6.0,
           release=8.0)

    # --- The lament (solo bowed string). -----------------------------------------------------
    lament_b = [("F4", 2, 0.75), ("Ab4", 1, 0.8), ("G4", 1, 0.75), ("Ab4", 3, 0.85), ("Db5", 1, 0.9),
                ("C5", 2, 0.9), ("Bb4", 1, 0.8), ("Db5", 1, 0.85), ("C5", 3.5, 0.8), (None, 0.5),
                ("F5", 2, 0.9), ("Eb5", 1, 0.85), ("Db5", 1, 0.8), ("Bb4", 3, 0.8), ("C5", 1, 0.75),
                ("Bb4", 2, 0.75), ("Gb4", 1, 0.7), ("Ab4", 1, 0.7), ("G4", 2, 0.75), ("E4", 1.5, 0.7)]
    _bowed(tr, "string", 12 * bar, beat, lament_b, 0.42, pan=0.12)
    lament_c = [("F4", 2, 0.75), ("G4", 1, 0.78), ("Ab4", 1, 0.8), ("Bb4", 2, 0.82), ("G4", 1, 0.8),
                ("Bb4", 1, 0.84), ("C5", 2, 0.86), ("Eb5", 1, 0.88), ("C5", 1, 0.86), ("Db5", 2, 0.9),
                ("F5", 1, 0.92), ("Db5", 1, 0.9), ("E5", 2, 0.95), ("G5", 1, 0.97), ("E5", 1, 0.95),
                ("F5", 3, 1.0), ("Ab5", 1, 1.0), ("G5", 2, 1.0), ("F5", 1, 0.95), ("E5", 1, 0.9),
                ("F5", 3, 0.75)]
    _bowed(tr, "string", 20 * bar, beat, lament_c, 0.52, pan=0.12)
    lament_d = [("C5", 3, 0.6), ("Bb4", 1, 0.55), ("Ab4", 4, 0.55), (None, 2), ("Db5", 1, 0.5),
                ("C5", 4, 0.45)]
    _bowed(tr, "string", 28 * bar, beat, lament_d, 0.42, pan=0.12)

    # --- The bell, the thunder, the chains. ---------------------------------------------------
    tolls = [1, 3, 4, 8, 12, 16, 20, 21, 22, 23, 24, 25, 26, 26.5, 28, 31, 33]
    for b in tolls:
        loud = 0.55 if 20 <= b <= 26.5 else 0.40
        _toll(tr, "bell", b * bar + rng.uniform(-0.05, 0.05), "F3", loud, pan=-0.25)
    for b, vel, pan in ((2.2, 0.45, 0.4), (10.5, 0.4, -0.5), (18.3, 0.5, 0.3), (24.6, 0.8, -0.2),
                        (26.6, 0.65, 0.5), (30.2, 0.4, -0.4), (33.0, 0.3, 0.3)):
        _thunder(tr, "thunder", b * bar, vel, dur=rng.uniform(8.0, 11.0), pan=pan)
    for b, vel, pan, heavy in ((9.75, 0.40, 0.5, True), (15.8, 0.30, -0.5, False), (21.0, 0.45, 0.4, True),
                               (23.0, 0.40, -0.4, False), (25.0, 0.55, 0.5, True), (26.0, 0.55, -0.3, True),
                               (32.0, 0.25, 0.6, False)):
        _chains(tr, "chain", b * bar + rng.uniform(-0.1, 0.1), vel, pan=pan, heavy=heavy)

    # --- Rain over everything: a fine hiss plus a softer, lower roar; heavier during the climb. ---
    pts = [(0, 0.0), (6, 0.020), (90, 0.022), (110, 0.032), (125, 0.030), (140, 0.020), (170, 0.018)]
    tr.noise_bed("rain", 2000, 9000, pts, drift_amt=0.3)
    tr.noise_bed("rain", 400, 2000, [(t, v * 0.6) for t, v in pts], drift_amt=0.4)

    L, R = tr.master(mix={"organ": 1.0, "pedal": 0.8, "choir": 0.62, "string": 0.8, "bell": 0.55,
                          "thunder": 0.9, "chain": 0.9, "rain": 1.0},
                     reverb={"organ": 0.6, "pedal": 0.2, "choir": 0.7, "string": 0.45, "bell": 0.6,
                             "thunder": 0.15, "chain": 0.6},
                     room_s=8.5, room_lp=3400, lp=7000, drive=1.25)
    _save_music(L, R, "level490", fade_out=7.0)


# =============================================================================
# Level 9 "Suburbs": instruments
# =============================================================================

def _analog_pad(tr, bus, start, dur, note, vel, attack=1.2, release=2.5, detune=12.0, pulse=0.6):
    """An old polysynth pad note: two sawtooth oscillators detuned against each other (the slow
    "beating" of cheap analog oscillators), plus a pulse wave whose width a slow LFO sweeps (pulse-
    width modulation, the hollow, shifting sound of 80s pads). The oscillators drift a few cents on
    their own, like a synth that hasn't warmed up."""
    rng = tr.rng
    freq = G._m_hz(note)
    n = int((dur + release) * SR)
    t = np.arange(n) / SR
    ratio = tr._ratio(start, n) * (1 + G.smooth_jitter(rng, n, 0.25, 0.004))
    tab = G._m_table(int(np.clip(5000 / freq, 1, 64)), 1.0)
    grid = np.arange(4097)
    L = np.zeros(n)
    R = np.zeros(n)
    for cents, p in ((-detune, -0.7), (0.8 * detune, 0.7)):
        ph = (rng.uniform() + np.cumsum(freq * 2 ** (cents / 1200) * ratio) / SR) % 1.0
        l, r = _pan(np.interp(ph * 4096, grid, tab), p)
        L += l
        R += r
    ph = (rng.uniform() + np.cumsum(freq * ratio) / SR) % 1.0
    width = 0.5 + 0.18 * np.sin(2 * np.pi * rng.uniform(0.15, 0.3) * t + rng.uniform(0, 6.3))
    pw = 0.5 * (np.interp(ph * 4096, grid, tab) - np.interp(((ph + width) % 1.0) * 4096, grid, tab))
    L += pulse * pw
    R += pulse * pw
    env = G._m_env(n, attack, dur, release) * vel / (2 + pulse)
    tr.add(bus, start, L * env, R * env)


def _lead(tr, bus, start, beat, phrase, vel, drift=0.010, pan=0.0):
    """The lullaby's voice: a soft, hollow synth lead (strong odd harmonics, weak even ones, a
    gentle square-ish tone) with portamento between notes and a late vibrato. Its pitch wanders
    on its own by up to `drift` (0.01 = 17 cents) as if the tape and the synth both sag."""
    rng = tr.rng
    for off, notes in _groups(phrase, beat):
        dur = notes[-1][0] + notes[-1][1] + 0.6
        n = int(dur * SR)
        t = np.arange(n) / SR
        lf, amp, vib = _curves(notes, n, glide=0.07, attack=0.03, dip=0.35, end_level=0.65,
                               vib=0.003, vib_delay=0.45, release=0.45)
        f0 = (2 ** lf * (1 + vib * np.sin(2 * np.pi * 5.6 * t + rng.uniform(0, 6.3)))
              * tr._ratio(start + off, n) * (1 + G.smooth_jitter(rng, n, 0.3, drift)))
        ph = 2 * np.pi * np.cumsum(f0) / SR
        x = np.zeros(n)
        for k in range(1, int(5000 / f0.min()) + 1):
            a = (1.0 if k % 2 else 0.3) / k ** 1.5
            x += a * np.clip((5000 - k * f0) / 1200, 0, 1) * np.sin(k * ph + rng.uniform(0, 6.3))
        x /= np.abs(x).max() + 1e-9
        tr.add(bus, start + off, *_pan(x * amp * vel, pan))


def _pluck(tr, bus, start, note, vel, decay=0.45, pan=0.0):
    """An arpeggiator note: a sawtooth whose high harmonics die faster than its low ones, which is
    what a synth filter closing quickly after each note sounds like."""
    rng = tr.rng
    freq = G._m_hz(note)
    n = int(decay * 4 * SR)
    t = np.arange(n) / SR
    ph = 2 * np.pi * np.cumsum(freq * tr._ratio(start, n)) / SR
    x = np.zeros(n)
    for k in range(1, int(min(30, 5000 / freq)) + 1):
        x += np.exp(-t * (1 + 0.35 * (k - 1)) / decay) / k * np.sin(k * ph + rng.uniform(0, 6.3))
    x *= np.clip(t / 0.003, 0, 1) * vel
    tr.add(bus, start, *_pan(x, pan))


def _bass(tr, bus, start, dur, note, vel):
    """A soft analog bass: a sine with a little of its 2nd and 3rd harmonics, a quick swell, a short
    decay to a held level and a soft release."""
    freq = G._m_hz(note)
    n = int((dur + 0.25) * SR)
    t = np.arange(n) / SR
    ph = 2 * np.pi * np.cumsum(freq * tr._ratio(start, n)) / SR
    x = np.sin(ph) + 0.3 * np.sin(2 * ph + 0.4) + 0.12 * np.sin(3 * ph + 1.1)
    env = np.clip(t / 0.015, 0, 1) * (0.7 + 0.3 * np.exp(-t / 0.3))
    env *= np.clip((dur + 0.25 - t) / 0.25, 0, 1)
    tr.add(bus, start, x * env * vel)


def _kick(rng):
    """A soft drum-machine kick: a sine falling quickly from ~115 Hz to 46 Hz."""
    t = np.arange(int(0.5 * SR)) / SR
    f = 46 + 70 * np.exp(-t / 0.045)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.22) * np.clip(t / 0.002, 0, 1)


def _snare(rng):
    """A drum-machine snare: a burst of filtered noise over a short 185 Hz tone."""
    t = np.arange(int(0.4 * SR)) / SR
    nz = G.fft_filter(rng.normal(size=t.size), lambda f: G.bp_gain(f, 900, 5000, 2))
    x = 0.8 * nz / (np.abs(nz).max() + 1e-9) * np.exp(-t / 0.12) + 0.5 * np.sin(2 * np.pi * 185 * t) * np.exp(-t / 0.07)
    return x * np.clip(t / 0.001, 0, 1)


def _hat(rng):
    """A closed hi-hat: a tick of very high noise."""
    t = np.arange(int(0.12 * SR)) / SR
    nz = G.fft_filter(rng.normal(size=t.size), lambda f: G.hp_gain(f, 6000, 2))
    return nz / (np.abs(nz).max() + 1e-9) * np.exp(-t / 0.02)


def _sodium(tr, bus, level, outages, pan, warm=7.0):
    """A sodium streetlamp's ballast buzz: a rough 120 Hz hum (the mains frequency doubled) rich in
    harmonics, made raspy by fast random wobble. 120 Hz falls between B-flat and B, so it is
    always slightly out of tune with the music. `outages` are (off, back on) times: the dying lamp
    cuts out, then comes back with a tick and slowly warms up, as old sodium lamps do."""
    rng = tr.rng
    t = np.arange(tr.n) / SR
    ph = 2 * np.pi * np.cumsum(120.0 * tr.ratio) / SR
    x = np.zeros(tr.n)
    for k in range(1, 25):
        x += k ** -0.8 * (1.3 if k % 2 == 0 else 1.0) * np.sin(k * ph + rng.uniform(0, 6.3))
    rasp = G.fft_filter(rng.normal(size=tr.n), lambda f: G.lp_gain(f, 400, 2))
    x *= 1 + 0.5 * rasp / (np.abs(rasp).max() + 1e-9)
    x = G.fft_filter(x, lambda f: G.bp_gain(f, 140, 3000, 2))
    x /= x.std() + 1e-9
    env = level * (1 + G.smooth_jitter(rng, tr.n, 0.8, 0.15))
    for off, on in outages:
        env *= np.interp(t, [off, off + 0.08, on, on + warm * 0.5, on + warm], [1, 0, 0, 0.25, 1])
        i0 = int(on * SR)
        tick = rng.normal(size=int(0.004 * SR)) * 3.0
        x[i0:i0 + tick.size] += tick
    tr.add(bus, 0.0, *_pan(x * env, pan))


def _chorus(L, R, base_ms=8.0, depth_ms=2.5, rate=0.6):
    """The 80s "ensemble" chorus: each channel mixed with a copy of itself delayed by a few
    milliseconds that a slow LFO sweeps back and forth (opposite phases left and right), which
    thickens and widens the pad."""
    n = len(L)
    t = np.arange(n) / SR
    idx = np.arange(n, dtype=float)
    out = []
    for ch, phase in ((L, 0.0), (R, np.pi)):
        d = (base_ms + depth_ms * np.sin(2 * np.pi * rate * t + phase)) * SR / 1000
        out.append(0.75 * ch + 0.55 * np.interp(idx - d, idx, ch, left=0.0))
    return out[0], out[1]


def _ping_pong(L, R, delay, fb, taps=5, lp=2400.0):
    """A tape echo bouncing between the ears: each repeat quieter and duller than the last."""
    n = len(L)
    d = int(delay * SR)
    L, R = L.copy(), R.copy()
    tap = 0.5 * (L + R)
    for i in range(1, taps + 1):
        tap = G.fft_filter(tap, lambda f: G.lp_gain(f, lp, 1)) * fb
        if d * i >= n:
            break
        target = L if i % 2 else R
        target[d * i:] += tap[: n - d * i]
    return L, R


def _sweep(x, t_pts, lo=650.0, hi=3000.0):
    """A slow filter sweep without a slow filter: cross-fades between a dark and a bright copy
    (both zero-phase, so they line up exactly) following (seconds, 0..1) points."""
    dark = G.fft_filter(x, lambda f: G.lp_gain(f, lo, 2))
    bright = G.fft_filter(x, lambda f: G.lp_gain(f, hi, 2))
    m = np.interp(np.arange(len(x)) / SR, [p[0] for p in t_pts], [p[1] for p in t_pts])
    return dark + (bright - dark) * m


def _tape_death(x, at, length, end_speed=0.4):
    """The cassette dying: from `at` the playback speed sags (slowly, then faster) to `end_speed`
    over `length` seconds, so pitch and tempo fall together, the sound grows duller as it drags,
    and it fades away just after. Returns the new signal (same length)."""
    i0 = int(at * SR)
    m = len(x) - i0
    u = np.clip(np.arange(m) / (length * SR), 0, 1)
    speed = 1 - (1 - end_speed) * u ** 1.5
    pos = i0 + np.cumsum(speed)
    y = x.copy()
    y[i0:] = np.interp(pos, np.arange(len(x)), x, right=0.0)
    dull = G.fft_filter(y, lambda f: G.lp_gain(f, 900, 2))
    t = np.arange(len(x)) / SR
    mix = np.clip((t - at) / length, 0, 1)
    fade = np.clip(1 - (t - (at + 0.7 * length)) / (0.3 * length + 2.0), 0, 1)
    return (y + (dull - y) * mix) * fade


# =============================================================================
# Level 9 "Suburbs"
# =============================================================================

def snd_musice_level9() -> None:
    """"Suburbs", ~2:22, for Level 9, the last level: almost home. B-flat major with borrowed
    minor colours (E-flat minor, G-flat major: the "nostalgic" chords that make a major key ache),
    72 beats a minute in 4/4 (one bar = 3.33 s), on a cassette that is badly worn (much more wow
    and flutter than the other pieces).

    Intro (bars 0-4): fog, a sodium lamp buzzing, a dark pad on B-flat major 7 and G minor 7. A
    (4-12): the pad moves (B-flat maj7, G m7, E-flat maj7, F; then B-flat maj7, G m7, E-flat
    minor 6, B-flat), a soft bass, and the lullaby: two simple four-bar phrases on a hollow synth
    lead that drifts out of tune. B (12-20): the same, brighter; an arpeggio and a very faint drum
    machine fade in from somewhere, then away again. C (20-28): the wrong turn: G-flat maj7, F m7,
    E-flat m6, B-flat maj7, G-flat maj7, E-flat m6, C m7, F; the tape warbles harder, the drums
    almost come back, and the lullaby comes only in fragments. D (28-36): home again, the opening
    phrase, the drums faint; then, on the E-flat minor chord of the last phrase (bar 34), the tape
    dies: pitch and tempo sag together and the music drags to a stop. The lamp keeps buzzing in
    the fog."""
    beat = 60.0 / 72.0
    bar = 4 * beat
    total = 142.0
    tr = G._MTrack(total, seed=9001, wow=0.008, flutter=0.0016)
    rng = tr.rng
    t = np.arange(tr.n) / SR
    # In C the tape warbles harder (a slow, deep extra wobble that fades in and out).
    c_win = np.clip((t - 20 * bar) / 6.0, 0, 1) * np.clip((28 * bar - t) / 6.0, 0, 1)
    tr.ratio = tr.ratio * (1 + 0.006 * np.sin(2 * np.pi * 0.37 * t) * c_win)

    bbM7, gm7, ebM7 = ["Bb2", "F3", "A3", "D4"], ["G2", "D3", "F3", "Bb3"], ["Eb3", "G3", "Bb3", "D4"]
    fsus, fmaj = ["F3", "Bb3", "C4"], ["F3", "A3", "C4"]
    ebm6, bb9 = ["Eb3", "Gb3", "Bb3", "C4"], ["Bb2", "F3", "C4", "D4"]
    gbM7, fm7, cm7 = ["Gb3", "Bb3", "Db4", "F4"], ["F3", "Ab3", "C4", "Eb4"], ["C3", "G3", "Bb3", "Eb4"]
    # (chord, bass root, beats). Phrase a's progression, then phrase b's, then the wrong turn.
    prog_a = [(bbM7, "Bb1", 4), (gm7, "G1", 4), (ebM7, "Eb2", 4), (fsus, "F1", 2), (fmaj, "F1", 2)]
    prog_b = [(bbM7, "Bb1", 4), (gm7, "G1", 4), (ebm6, "Eb2", 4), (bb9, "Bb1", 4)]
    prog_c = [(gbM7, "Gb1", 4), (fm7, "F1", 4), (ebm6, "Eb2", 4), (bbM7, "Bb1", 4),
              (gbM7, "Gb1", 4), (ebm6, "Eb2", 4), (cm7, "C2", 4), (fsus, "F1", 2), (fmaj, "F1", 2)]

    def play(at, items, vel, bass_vel, arp_vel=0.0, last_hold=1.0):
        for i, (chord, root, beats) in enumerate(items):
            d = beats * beat * (last_hold if i == len(items) - 1 else 1.0)
            for note in chord:
                _analog_pad(tr, "pad", at + rng.uniform(0, 0.05), d + 0.1, note, vel * rng.uniform(0.85, 1.0))
            if bass_vel:
                for b0, bl in ((0.0, 1.5), (2.5, 1.5)):
                    if b0 < beats:
                        _bass(tr, "bass", at + b0 * beat, min(bl, beats - b0) * beat * 0.9, root, bass_vel)
            if arp_vel:
                up = [n[:-1] + str(int(n[-1]) + 1) for n in chord]
                order = up + up[-2:0:-1]
                for k in range(int(beats * 2)):
                    _pluck(tr, "arp", at + k * beat / 2, order[k % len(order)], arp_vel * (1.0 if k % 2 == 0 else 0.75),
                           pan=0.3 * np.sin(k * 0.9))
            at += beats * beat

    # Intro: a dark pad, no bass.
    play(0.4, [(bbM7, "Bb1", 8), (gm7, "G1", 8)], 0.14, 0.0)
    play(4 * bar, prog_a + prog_b, 0.22, 0.30)                                  # A
    play(12 * bar, prog_a + prog_b, 0.24, 0.32, arp_vel=0.16)                   # B
    play(20 * bar, prog_c, 0.17, 0.20)                                          # C
    play(28 * bar, prog_a + prog_b, 0.22, 0.30, arp_vel=0.10, last_hold=3.0)    # D

    # The lullaby (all original).
    phrase_a = [("D5", 2), ("C5", 1), ("Bb4", 1), ("D5", 1.5), ("F5", 0.5), ("D5", 2),
                ("C5", 1), ("Bb4", 1), ("G4", 2), ("A4", 3), (None, 1)]
    phrase_b = [("D5", 2), ("C5", 1), ("Bb4", 1), ("G5", 2), ("F5", 1), ("D5", 1),
                ("Eb5", 1), ("C5", 1), ("Gb4", 2), ("F4", 3), (None, 1)]
    phrase_a2 = [("D5", 2), ("C5", 1), ("Bb4", 1), ("D5", 1.5), ("F5", 0.5), ("G5", 2),
                 ("F5", 1), ("D5", 1), ("C5", 2), ("Bb4", 3), (None, 1)]
    fragments = [(None, 1), ("Db5", 2), ("C5", 1), ("Ab4", 4), (None, 2), ("Gb4", 1), ("F4", 1),
                 ("D5", 4), (None, 1), ("Db5", 2), ("Bb4", 1), ("Gb4", 3), (None, 1),
                 ("G4", 2), ("Bb4", 1), ("C5", 1), ("A4", 4)]
    _lead(tr, "lead", 4 * bar, beat, phrase_a + phrase_b, 0.30)
    _lead(tr, "lead", 12 * bar, beat, phrase_a2 + phrase_b, 0.30)
    _lead(tr, "lead", 20 * bar, beat, fragments, 0.22, drift=0.018)
    _lead(tr, "lead", 28 * bar, beat, phrase_a + phrase_b, 0.30, drift=0.013)

    # The drum machine, very faint, coming and going: (bar, level) points.
    drum_level = [(12, 0.0), (15, 1.0), (17, 1.0), (20, 0.0), (23.5, 0.0), (25, 0.3), (26.5, 0.0),
                  (29.5, 0.0), (32, 0.65), (36, 0.65)]
    kick, snare, hat = _kick(rng), _snare(rng), _hat(rng)
    for b in range(12, 36):
        g = np.interp(b + 0.5, [p[0] for p in drum_level], [p[1] for p in drum_level])
        if g < 0.02:
            continue
        at = b * bar
        for k in (0.0, 2.5):
            tr.add("drums", at + k * beat, kick * 0.8 * g)
        for k in (1.0, 3.0):
            tr.add("drums", at + k * beat, snare * 0.45 * g)
        for k in range(8):
            l, r = _pan(hat * (0.22 if k % 2 else 0.14) * g, 0.25)
            tr.add("drums", at + k * beat / 2, l, r)

    # The street: two sodium lamps (one dying), fog air, no insects.
    _sodium(tr, "lamp", 0.010, [(58.0, 63.5), (97.0, 98.2)], pan=0.4)
    _sodium(tr, "lamp", 0.005, [], pan=-0.5)
    tr.noise_bed("air", 60, 450, [(0, 0.0), (4, 0.022), (total, 0.022)], drift_amt=0.4)

    # The pad: ensemble chorus, and a filter that opens for B, closes for C, half opens for D.
    l, r = tr.buses["pad"]
    sweep = [(0, 0.0), (4 * bar, 0.3), (12 * bar, 0.4), (15 * bar, 0.8), (20 * bar, 0.5),
             (24 * bar, 0.25), (28 * bar, 0.55), (total, 0.55)]
    tr.buses["pad"] = _chorus(_sweep(l, sweep), _sweep(r, sweep))
    tr.buses["lead"] = _ping_pong(*tr.buses["lead"], delay=0.75 * beat, fb=0.38)
    tr.buses["arp"] = _ping_pong(*tr.buses["arp"], delay=0.75 * beat, fb=0.32, taps=4, lp=2000)
    _filter_bus(tr, "drums", lambda f: G.lp_gain(f, 2400, 2) * G.hp_gain(f, 40, 2))

    # The tape dies on the E-flat minor chord of the last phrase (bar 34); the lamp stays.
    for name in ("pad", "lead", "bass", "arp", "drums"):
        l, r = tr.buses[name]
        tr.buses[name] = (_tape_death(l, 34 * bar, 13.0), _tape_death(r, 34 * bar, 13.0))

    L, R = tr.master(mix={"pad": 1.0, "lead": 0.8, "bass": 0.3, "arp": 0.3, "drums": 0.16,
                          "lamp": 1.0, "air": 1.0},
                     reverb={"pad": 0.35, "lead": 0.4, "arp": 0.5, "drums": 0.3, "bass": 0.05},
                     room_s=3.2, room_lp=4500, lp=5200, drive=1.5, hiss=0.004)
    _save_music(L, R, "level9", fade_out=8.0)


SOUNDS = [snd_musice_level490, snd_musice_level9]
