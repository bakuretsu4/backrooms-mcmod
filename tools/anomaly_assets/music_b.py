"""Level music, group B: two ORIGINAL pieces, composed here note by note (nothing sampled or copied).

  music/level2   "Pressure"     Level 2, Pipe Dreams: struck pipes, a boiler heartbeat, steam.
  music/level4   "Night Shift"  Level 4, the Abandoned Office: solo night piano in the rain.

Both use the shared music toolkit in gen_assets (G._MTrack, G._m_save): the same worn-tape
master as the main theme (a shared pitch wobble, soft saturation, a dull top, hiss and a room),
so they sound like part of one soundtrack. What is new here are the instruments each level needs:
tuned pipes and a boiler for Level 2, a piano, rain, thunder and a muted horn for Level 4.

Loudness: _m_save scales a piece to a fixed PEAK, so how loud it sounds depends on how spiky it
is (a piano is spikier than a pad). _save_matched therefore writes the file, measures it the way a
loudness meter does (integrated LUFS, both channels, BS.1770) and writes it again at the level of
the existing pieces (MUSIC_LUFS, the average of liminal, deep and megaflora), never louder than
-1.5 dBFS on the peaks.

Run only these with:  python tools/gen_assets.py music_pressure music_night_shift
Every piece uses its own fixed random seed (31020, 31040), so re-running gives identical audio (the
.ogg bytes still differ: the Ogg container stamps each file with a random stream number).
"""
from __future__ import annotations

import numpy as np
import soundfile as sf

import gen_assets as G
from anomaly_assets import level2_audio as A   # the K-weighting filter of the loudness meter

SR = G.SR

# Integrated loudness of the existing music (stereo LUFS): liminal -16.8, deep -16.4,
# megaflora -14.8. Their average is where the new pieces sit, so no level jumps out.
MUSIC_LUFS = -16.0
PEAK_CEILING = 0.84          # -1.5 dBFS: leaves room for the Vorbis codec's small overshoots


def _lufs_stereo(x: np.ndarray) -> float:
    """Integrated loudness of a stereo (n, 2) signal: the BS.1770 meter of level2_audio.lufs,
    with the power of both channels added together (that is how the standard handles stereo)."""
    y = sum(A._k_weight(np.asarray(x[:, c], float)) ** 2 for c in range(x.shape[1]))
    block, hop = int(0.4 * SR), int(0.1 * SR)
    z = np.array([y[s:s + block].mean() for s in range(0, len(y) - block + 1, hop)])
    z = z[-0.691 + 10 * np.log10(z + 1e-20) > -70.0]                 # absolute gate
    rel = -0.691 + 10 * np.log10(z.mean()) - 10.0                     # relative gate
    z = z[-0.691 + 10 * np.log10(z) > rel]
    return float(-0.691 + 10 * np.log10(z.mean()))


def _save_matched(L: np.ndarray, R: np.ndarray, name: str, fade_out: float) -> None:
    """Writes music/<name>.ogg through the shared _m_save, then measures the file and writes it
    again with the peak level that lands it on MUSIC_LUFS (loudness scales 1:1 with the peak)."""
    path = G.SND / "music" / f"{name}.ogg"
    peak = 0.62                                   # _m_save's default, as a first guess
    for _ in range(3):
        G._m_save(L, R, path, peak=peak, fade_out=fade_out)
        off = MUSIC_LUFS - _lufs_stereo(sf.read(str(path))[0])
        if abs(off) < 0.2:
            break
        peak = min(PEAK_CEILING, peak * 10 ** (off / 20))


def _shape(tr, bus: str, gain_fn) -> None:
    """Filters both channels of one bus (e.g. to darken or thin a whole instrument at once)."""
    l, r = tr.buses[bus]
    tr.buses[bus] = (G.fft_filter(l, gain_fn), G.fft_filter(r, gain_fn))


def _narrow(L: np.ndarray, R: np.ndarray, width: float):
    """Pulls the stereo image towards the middle: keeps the mid (L+R) and only `width` of the
    side (L-R). Level 2 uses it so the music feels like a tight corridor, not a hall."""
    mid, side = (L + R) / 2, (L - R) / 2 * width
    return mid + side, mid - side


def _panned(x: np.ndarray, pan: float):
    """Equal-power pan: -1 hard left, 0 middle, +1 hard right."""
    return x * np.sqrt(0.5 * (1 - pan)), x * np.sqrt(0.5 * (1 + pan))


# =============================================================================
# Level 2 "Pressure"
# =============================================================================

# E Phrygian dominant (E F G# A B C D): the flat second (F) and the major third (G#) next to each
# other are what makes it sound hot, foreign and tense. Index 0 is E2, 7 is E3, 14 is E4 ...
_PD = [("E", 0), ("F", 0), ("G#", 0), ("A", 0), ("B", 0), ("C", 1), ("D", 1)]


def _deg(i: int) -> str:
    """Scale step i of E Phrygian dominant as a note name (0 = E2). C and D belong to the next
    octave number because note names count octaves from C."""
    o, d = divmod(i, 7)
    name, up = _PD[d]
    return f"{name}{2 + o + up}"


def _pipe(tr, bus: str, start: float, note: str, vel: float, decay: float = 2.2, pan: float = 0.0,
          ombak: float = 4.0) -> None:
    """A tuned metal pipe struck with a padded mallet, like a gamelan metallophone. The pitch is a
    pair of tones `ombak` Hz apart (gamelan instruments are tuned in slightly mistuned pairs so
    every note shimmers), plus the inharmonic overtones of a metal tube and a short clank."""
    f = G._m_hz(note)
    n = int(decay * 2.6 * SR)
    t = np.arange(n) / SR
    clock = np.cumsum(tr._ratio(start, n)) / SR   # time as the worn tape plays it (wobbles)
    rng = tr.rng
    x = np.zeros(n)
    for mult, amp, dk in ((1.0, 0.6, 1.0), (2.02, 0.22, 0.45), (2.76, 0.3, 0.3), (4.1, 0.1, 0.16),
                          (5.4, 0.07, 0.1)):
        if f * mult > 6000:
            break
        ph = clock * f * mult + rng.uniform()
        x += amp * np.sin(2 * np.pi * ph) * np.exp(-t / (decay * dk))
    # The shimmer: the same pitch again, `ombak` Hz higher, decaying a little slower.
    x += 0.5 * np.sin(2 * np.pi * (clock * (f + ombak) + rng.uniform())) * np.exp(-t / (decay * 1.1))
    m = int(0.03 * SR)
    clank = G.fft_filter(rng.normal(size=m), lambda fr: G.bp_gain(fr, f * 2.5, f * 5.0, 2))
    x[:m] += 0.25 * G.norm(clank) * np.exp(-t[:m] / 0.006)
    x *= np.clip(t / 0.003, 0, 1) * vel          # 3 ms: a felt mallet, not a hard click
    tr.add(bus, start, *_panned(x, pan))


def _brass(tr, bus: str, start: float, dur: float, note: str, vel: float, drive: float = 2.0,
           attack: float = 4.0, release: float = 5.0) -> None:
    """A dark, distorted brass-like drone. Two slightly detuned saw voices go through a soft
    clipper whose drive follows the swell, so the tone gets brighter and rougher as it gets louder
    (that is what makes real brass sound like brass). The bus is darkened afterwards."""
    f = G._m_hz(note)
    n = int((dur + release) * SR)
    env = G._m_env(n, attack, dur, release)
    clock = np.cumsum(tr._ratio(start, n)) / SR
    tab = G._m_table(int(np.clip(4000 / f, 1, 64)), 1.0)
    x = np.zeros(n)
    for cents in (-7.0, 6.0):
        ph = (tr.rng.uniform() + clock * f * 2 ** (cents / 1200)) % 1.0
        x += np.interp(ph * 4096, np.arange(4097), tab)
    growl = 1 + 0.08 * np.sin(2 * np.pi * 5.7 * np.arange(n) / SR)   # a slight rasp in the air
    y = np.tanh(drive * env * growl * x / 2) / np.tanh(drive)
    tr.add(bus, start, y * vel, y * vel * 0.97)


def _heartbeat(beat_times, vels, seed: int, n_total: int) -> np.ndarray:
    """The boiler: a low double thump (lub-dub) on every beat, with a little valve chuff on the
    second one, at the given times and strengths."""
    rng = G.rng_for(seed)
    m = int(0.6 * SR)
    t = np.arange(m) / SR

    def thump(f0, f1, dk):
        ph = np.cumsum(f1 + (f0 - f1) * np.exp(-t / 0.05)) / SR
        body = np.sin(2 * np.pi * ph) * np.exp(-t / dk)
        knock = G.fft_filter(rng.normal(size=m), lambda f: G.lp_gain(f, 220, 2)) * np.exp(-t / 0.03)
        return (G.norm(body) + 0.25 * G.norm(knock)) * np.clip(t / 0.004, 0, 1)

    chuff = G.fft_filter(rng.normal(size=m), lambda f: G.bp_gain(f, 500, 1800, 2)) * np.exp(-t / 0.05)
    lub = thump(70, 42, 0.16)
    dub = 0.7 * thump(62, 38, 0.13) + 0.12 * G.norm(chuff) * np.clip(t / 0.02, 0, 1)
    out = np.zeros(n_total)
    for at, v in zip(beat_times, vels):
        if v > 0:
            G.add_at(out, lub * v, at, False)
            G.add_at(out, dub * v, at + 0.23, False)
    return out


def _steam(tr, bus: str, end: float, length: float, vel: float, fall: float = 1.6) -> None:
    """A steam leak building up as a riser: a hiss whose pitch and level climb for `length`
    seconds and then settle back down over `fall` seconds (a valve easing, not a hit)."""
    n = int((length + fall) * SR)
    t = np.arange(n) / SR
    out = []
    for _ in range(2):
        nz = tr.rng.normal(size=n)
        x = np.zeros(n)
        for k, fc in enumerate((700, 1300, 2300, 3800)):      # crossfaded bands = a rising sweep
            band = G.fft_filter(nz, lambda f: G.bp_gain(f, fc * 0.7, fc * 1.4, 2))
            w = np.clip(1 - np.abs(t / length * 4 - (k + 0.5)) / 1.2, 0, 1)
            x += G.norm(band) * w
        rise = np.clip(t / length, 0, 1) ** 2.2
        settle = np.where(t < length, 1.0, np.exp(-(t - length) / (fall / 3)))
        out.append(x * rise * settle * vel)
    tr.add(bus, end - length, out[0], out[1])


def _exhale(tr, bus: str, start: float, vel: float, length: float = 12.0) -> None:
    """The release after the climax: a big valve opening and the pressure slowly bleeding out
    as a long, dark hiss that dies away."""
    n = int(length * SR)
    t = np.arange(n) / SR
    out = []
    for _ in range(2):
        x = G.fft_filter(tr.rng.normal(size=n), lambda f: G.bp_gain(f, 400, 5000, 1))
        x = G.norm(x) * np.clip(t / 0.25, 0, 1) * np.exp(-t / (length / 3.2))
        x *= 1 + G.smooth_jitter(tr.rng, n, 3.0, 0.25)           # the jet sputters a little
        out.append(x * vel)
    tr.add(bus, start, out[0], out[1])


# The gamelan-like core melody, one note per beat, 4 bars per phrase (scale steps, 7 = E3).
# Each phrase ends on its gong tone. P1 rests on E, P2 on F (the flat second: unrest), P3 climbs
# and stops on the fifth (B), P4 is the climax that comes back down to E.
_P1 = [11, 12, 11, 10, 9, 10, 8, 7, 8, 9, 10, 11, 10, 9, 8, 7]
_P2 = [8, 9, 8, 7, 8, 10, 11, 12, 11, 12, 13, 12, 11, 10, 9, 8]
_P3 = [9, 11, 12, 13, 14, 13, 12, 13, 14, 15, 16, 15, 14, 13, 12, 11]
_P4 = [15, 14, 15, 16, 15, 14, 13, 14, 15, 16, 17, 16, 15, 15, 14, 14]


def snd_music_pressure() -> None:
    """~2.7 minutes for Level 2, Pipe Dreams: "Pressure". E Phrygian dominant, 80 bpm (bar = 3 s).

    The pipes are the orchestra: tuned metal pipes struck like a gamelan, playing a slow core
    melody (low pipes, every other beat) while two higher pipe parts interlock around it, one on
    the beats (left of centre) and one between them (right of centre), so together they make one
    running line. Under them the boiler beats like a heart at 80 bpm and a dark, distorted
    brass-like drone holds E.
    Structure: intro (bars 0-6) drone, steam and single low pipes; A (6-14) the core melody with
    the upper pipes on the beat; a steam riser into B (14-30), where the interlocking fills every
    eighth note and the drone grinds a flat second (F) against E, then adds G# and B; another riser
    into the climax (30-34), loudest and roughest; then the valve opens: everything stops but a
    long hiss. The boiler slows down, a few pipes ring far away and out of tune, and it ends on E
    and F rubbing against each other in the dark. The stereo image is kept narrow (a corridor).
    """
    beat = 60.0 / 80.0
    bar = 4 * beat
    tr = G._MTrack(54 * bar, seed=31020, wow=0.0025, flutter=0.0007)
    rng = tr.rng

    # The core melody: the low pipes play it every other beat; the high pipes (an octave up)
    # interlock around it. `phrases` = (start bar, melody, density): 0 = only the beat part,
    # 1 = full interlock on every eighth, 2 = full interlock plus a second voice a fourth up.
    phrases = [(6, _P1, 0), (10, _P1, 0), (14, _P2, 1), (18, _P2, 1), (22, _P3, 1), (26, _P3, 2), (30, _P4, 2)]
    for p_bar, mel, density in phrases:
        loud = 0.6 + 0.4 * (p_bar - 6) / 24          # the pipes get louder phrase by phrase
        for j, step in enumerate(mel):
            at = p_bar * bar + j * beat
            if j % 2 == 0:
                _pipe(tr, "low", at, _deg(step), 0.42 * loud, decay=3.2, pan=0.05, ombak=2.5)
            nxt = mel[(j + 1) % len(mel)]
            # The beat part ("polos") lands on the melody note itself, an octave up.
            _pipe(tr, "pipes", at + rng.uniform(-0.006, 0.006), _deg(step + 7), 0.22 * loud,
                  decay=1.8, pan=-0.3, ombak=5.0)
            if density >= 1 or (p_bar >= 10 and j % 4 == 3):
                # The part between the beats ("sangsih") circles the NEXT melody note: a step
                # above or below it, so the line leans forward into where it's going.
                nb = nxt + (1 if (j % 3) != 1 else -1)
                _pipe(tr, "pipes", at + beat / 2 + rng.uniform(-0.006, 0.006), _deg(nb + 7),
                      0.19 * loud, decay=1.6, pan=0.3, ombak=5.5)
                if density >= 2:
                    _pipe(tr, "pipes", at + beat / 2 + 0.01, _deg(nb + 10), 0.09 * loud,
                          decay=1.4, pan=0.35, ombak=6.0)
        # The gong: a big low pipe at the end of every phrase, on its last note (E, F or B).
        gong = {0: "E1", 1: "F1", 4: "B1"}[mel[-1] % 7]
        _pipe(tr, "gong", p_bar * bar + 15 * beat, gong, 0.55 * loud, decay=5.5, ombak=1.1)
    # Intro: the core melody's first half, slowly, on the low pipes only.
    for j, step in enumerate(_P1[::2]):
        _pipe(tr, "low", (2 + j * 0.5) * bar, _deg(step), 0.3 + 0.02 * j, decay=3.5, pan=0.05, ombak=2.5)
    _pipe(tr, "gong", 6 * bar, "E1", 0.45, decay=5.5, ombak=1.1)

    # The drone: dark brass on E, then the flat second grinds against it, then the climax cluster.
    drones = [  # (start bar, length in bars, note, level, drive)
        (0, 14.5, "E1", 0.22, 1.6), (1.5, 13.0, "B1", 0.12, 1.6), (6, 8.5, "E2", 0.10, 1.8),
        (14, 16.5, "E1", 0.34, 2.2), (14, 16.5, "E2", 0.16, 2.4), (16, 14.5, "F2", 0.12, 2.6),
        (22, 8.5, "G#2", 0.10, 2.6), (24, 6.5, "B2", 0.09, 2.8),
        (30, 4.0, "E1", 0.50, 3.2), (30, 4.0, "F2", 0.20, 3.5), (30, 4.0, "G#2", 0.17, 3.5),
        (30, 4.0, "B2", 0.13, 3.5), (30, 4.0, "E3", 0.08, 3.5),
    ]
    for b0, length, note, vel, drive in drones:
        attack = 3.0 if b0 > 0 else 9.0
        _brass(tr, "brass", b0 * bar, length * bar, note, vel, drive=drive, attack=attack,
               release=1.2 if b0 == 30 else 5.0)

    # Steam: two risers that settle, then the big one into the release, which never lands as a
    # hit: everything else simply stops and the pressure hisses out.
    _steam(tr, "steam", 14 * bar, 7.0, 1.4)
    _steam(tr, "steam", 30 * bar, 10.0, 1.6)
    _steam(tr, "steam", 34 * bar, 9.0, 1.8, fall=0.6)
    _exhale(tr, "steam", 34 * bar - 0.3, 1.2, length=20.0)
    _pipe(tr, "gong", 34 * bar, "E1", 0.5, decay=7.0, ombak=0.9)
    _brass(tr, "brass", 34 * bar, 3.0, "E1", 0.18, drive=1.4, attack=0.6, release=7.0)

    # After: the boiler slowing down, far-off pipes out of tune, E against F.
    for k, (b, step) in enumerate(((39.0, 11), (40.5, 10), (42.0, 8), (44.5, 7), (46.0, 8),
                                   (49.0, 7))):
        _pipe(tr, "far", b * bar + rng.uniform(-0.2, 0.2), _deg(step + 7), 0.4, decay=2.6,
              pan=rng.uniform(-0.3, 0.3), ombak=3.0 + k)
    _brass(tr, "brass", 40 * bar, 10.0 * bar, "E1", 0.16, drive=1.3, attack=8.0, release=8.0)
    _brass(tr, "brass", 44 * bar, 6.0 * bar, "F1", 0.08, drive=1.3, attack=8.0, release=8.0)
    _pipe(tr, "gong", 50 * bar, "E1", 0.3, decay=6.0, ombak=0.7)

    # The boiler heartbeat: steady at 80 bpm, swelling with the tension, gone during the release,
    # then back, weaker and slowing (each beat a little longer than the last) until it stops.
    times, vels = [], []
    for k in range(int(34 * bar / beat)):
        at = k * beat
        level = np.interp(at / bar, [2, 6, 14, 22, 30, 33.5, 34], [0.0, 0.25, 0.32, 0.55, 0.75, 0.9, 0.0])
        times.append(at)
        vels.append(level)
    at, gap = 37 * bar, beat * 1.15
    while at < 50 * bar:
        times.append(at)
        vels.append(0.4 * np.interp(at / bar, [37, 40, 50], [0.6, 1.0, 0.25]))
        at += gap
        gap *= 1.045
    hb = _heartbeat(times, vels, 31021, tr.n)
    tr.add("boiler", 0.0, hb, hb)

    # The place itself: a hot, hissing room (steam everywhere) and the low roar of the boilers.
    tr.noise_bed("hiss", 2200, 6500, [(0, 0.0), (8, 0.018), (40, 0.024), (100, 0.032), (102, 0.01),
                                      (130, 0.018), (162, 0.012)], drift_amt=0.5)
    tr.noise_bed("roar", 30, 160, [(0, 0.0), (10, 0.022), (42, 0.026), (60, 0.035), (90, 0.045),
                                   (102, 0.05), (115, 0.025), (162, 0.02)], drift_amt=0.3)

    # Bus colour: the brass dark (a muffled horn through steel walls), the far pipes thin and dull.
    _shape(tr, "brass", lambda f: G.lp_gain(f, 900, 2) * G.hp_gain(f, 35, 2))
    _shape(tr, "far", lambda f: G.bp_gain(f, 250, 1500, 2))
    _shape(tr, "pipes", lambda f: G.lp_gain(f, 4200, 2))
    L, R = tr.master(mix={"pipes": 0.75, "low": 0.7, "gong": 0.55, "brass": 0.45, "boiler": 0.6,
                          "steam": 0.6, "far": 0.4, "hiss": 1.0, "roar": 1.0},
                     reverb={"pipes": 0.45, "low": 0.35, "gong": 0.5, "brass": 0.15, "far": 1.0,
                             "steam": 0.3, "boiler": 0.1},
                     room_s=3.2, room_lp=3200, lp=5600, drive=1.6, hiss=0.003)
    L, R = _narrow(L, R, 0.45)
    _save_matched(L, R, "level2", fade_out=7.0)


# =============================================================================
# Level 4 "Night Shift"
# =============================================================================

_NOTE_PC = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
_FLAT_NAMES = ["C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"]


def _midi(note: str) -> int:
    """Note name -> MIDI number (C4 = 60)."""
    pc, rest = _NOTE_PC[note[0]], note[1:]
    while rest and rest[0] in "#b":
        pc += 1 if rest[0] == "#" else -1
        rest = rest[1:]
    return 12 * (int(rest) + 1) + pc


def _name(m: int) -> str:
    """MIDI number -> note name (flats, as this piece is in C minor)."""
    return f"{_FLAT_NAMES[m % 12]}{m // 12 - 1}"


def _piano(tr, bus: str, start: float, dur: float, note: str, vel: float, pan: float = 0.0) -> None:
    """One piano note, built from its strings' partials. What makes it a piano and not a bell:
    the partials are almost harmonic but stretched a little upwards (stiff steel strings), each
    one dies away in two stages (a quick fall, then a long soft tail), higher partials die sooner,
    two strings per note beat slowly against each other, and a soft blow (low `vel`) only excites
    the lower partials (the felt hammer). The damper stops the note `dur` seconds after the key
    goes down; very high notes have no damper and ring on."""
    f0 = G._m_hz(note)
    tail2 = 7.0 * (f0 / 65.0) ** -0.55                   # long tail: seconds, shorter up high
    ring = min(dur, 3.0 * tail2) if f0 < 1400 else 3.0 * tail2
    n = int((ring + 0.35) * SR)
    t = np.arange(n) / SR
    clock = np.cumsum(tr._ratio(start, n)) / SR
    rng = tr.rng
    stiff = float(np.clip(3.5e-4 * (f0 / 261.6), 6e-5, 3e-3))
    hammer = 500.0 + 2600.0 * vel
    x = np.zeros(n)
    for k in range(1, 40):
        fk = k * f0 * np.sqrt(1 + stiff * k * k)
        if fk > 6500:
            break
        amp = k ** -0.85 * G.lp_gain(fk, hammer, 2) * (0.3 + 0.7 * abs(np.sin(np.pi * k / 7.7)))
        if amp < 2e-3:
            continue
        slow = tail2 / (1 + 0.22 * (k - 1))
        e = 0.55 * np.exp(-t / (slow * 0.12)) + 0.45 * np.exp(-t / slow)
        strings = 0.8 + 0.2 * np.cos(2 * np.pi * fk * rng.uniform(2e-4, 8e-4) * t + rng.uniform(0, 6.3))
        x += amp * e * strings * np.sin(2 * np.pi * (fk * clock + rng.uniform()))
    m = int(0.06 * SR)
    knock = G.fft_filter(rng.normal(size=m), lambda f: G.lp_gain(f, 300 + f0, 2)) * np.exp(-t[:m] / 0.015)
    x[:m] += 0.05 * G.norm(knock)
    if f0 < 1400:                                        # the damper falls back on the strings
        x *= np.where(t < ring, 1.0, np.exp(-(t - ring) / 0.08))
    x *= np.clip(t / 0.0015, 0, 1) * vel ** 1.4
    tr.add(bus, start, *_panned(x, pan))


def _horn(tr, bus: str, start: float, dur: float, note: str, vel: float, pan: float = 0.1) -> None:
    """A soft muted horn (or a synth playing one): a breathy tone with a nasal bump around 1.2 kHz
    like a trumpet with a mute in it. It scoops up into the note, gets brighter as it swells, and a
    slow vibrato only comes in once the note has been held for a moment."""
    f0 = G._m_hz(note)
    n = int((dur + 1.2) * SR)
    t = np.arange(n) / SR
    env = G._m_env(n, 0.35, dur, 1.2)
    vib = 1 + 0.0035 * np.clip((t - 0.6) / 0.8, 0, 1) * np.sin(2 * np.pi * 4.8 * t)
    scoop = 2 ** (-35 / 1200 * np.exp(-t / 0.06))           # starts a little flat
    clock = np.cumsum(tr._ratio(start, n) * vib * scoop) / SR
    bright = 400.0 + 1600.0 * env
    x = np.zeros(n)
    for k in range(1, 24):
        fk = k * f0
        if fk > 5000:
            break
        mute = 0.25 + np.exp(-((fk - 1200) / 500) ** 2)
        x += (mute / k) / np.sqrt(1 + (fk / bright) ** 4) * np.sin(2 * np.pi * (k * f0 * clock + 0.13 * k))
    breath = G.fft_filter(tr.rng.normal(size=n), lambda f: G.bp_gain(f, 900, 2600, 2))
    x = G.norm(x) + 0.03 * G.norm(breath)
    tr.add(bus, start, *_panned(x * env * vel, pan))


def _rain(tr, bus: str, level_pts) -> None:
    """Rain on a big office window: a soft wash, plus thousands of tiny drops ticking on the glass
    (each a few-millisecond ring at a random pitch), louder in gusts."""
    n = tr.n
    t = np.arange(n) / SR
    level = np.interp(t, [p[0] for p in level_pts], [p[1] for p in level_pts])
    gust = 1 + G.smooth_jitter(tr.rng, n, 0.12, 0.35)
    chans = []
    for _ in range(2):
        rng = tr.rng
        wash = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 300, 4000, 1))
        ticks = np.zeros(n)
        for fc in (1900.0, 2700.0, 3600.0, 4700.0):
            k = int(0.05 * SR)
            tk = np.arange(k) / SR
            kernel = np.sin(2 * np.pi * fc * tk) * np.exp(-tk / 0.004)
            hits = np.zeros(n)
            idx = rng.integers(0, n, size=int(n / SR * 22))
            hits[idx] = rng.uniform(0.1, 1.0, size=idx.size) ** 2
            ticks += G.conv(hits, kernel)[:n]
        ticks = G.fft_filter(ticks, lambda f: G.lp_gain(f, 5000, 2))
        chans.append((G.norm(wash) * 0.5 + G.norm(ticks) * 0.5) * level * gust)
    tr.add(bus, 0.0, chans[0], chans[1])


def _thunder(tr, bus: str, start: float, vel: float, length: float = 10.0) -> None:
    """Thunder miles away, through the windows: no crack, only a low roll that swells in, grumbles
    unevenly and dies away."""
    n = int(length * SR)
    t = np.arange(n) / SR
    rise = 1.0 + tr.rng.uniform(0, 1.2)
    shape = np.clip(t / rise, 0, 1) ** 1.5 * np.exp(-np.maximum(t - rise, 0) / (length / 4.5))
    out = []
    for _ in range(2):
        rumble = G.fft_filter(tr.rng.normal(size=n), lambda f: G.bp_gain(f, 25, 220, 2))
        roll = np.clip(1 + G.smooth_jitter(tr.rng, n, 1.5, 0.6), 0.2, 2)
        out.append(G.norm(rumble * roll) * shape * vel)
    tr.add(bus, start, out[0], out[1])


# The harmony: C minor, eight chords, one per bar, cycled. Lonely "lounge" colours: minor ninths,
# major sevenths with a raised eleventh (the floating, unanswered sound), an altered dominant.
# Each chord: (left hand shell, right hand voicing, bass root as MIDI, chord third/fifth steps).
_CHORDS = [
    (["C2", "G2"], ["Bb3", "D4", "Eb4", "G4"], 36, (3, 7)),          # Cm9
    (["Ab1", "Eb2"], ["G3", "C4", "D4", "Eb4"], 32, (4, 7)),         # Abmaj7#11
    (["F2", "C3"], ["Ab3", "C4", "Eb4", "G4"], 41, (3, 7)),          # Fm9
    (["G1", "F2"], ["B3", "Eb4", "Ab4"], 31, (4, 8)),                # G7 b9 b13 (altered)
    (["Eb2", "Bb2"], ["G3", "A3", "D4"], 39, (4, 7)),                # Ebmaj7#11
    (["D2", "C3"], ["F3", "Ab3", "C4"], 38, (3, 6)),                 # Dm7b5
    (["Db2", "Ab2"], ["F3", "G3", "C4"], 37, (4, 7)),                # Dbmaj7#11
    (["G1", "F2"], ["Ab3", "C4", "F4"], 31, (5, 7)),                 # G7sus4 b9
]

# The right hand's tune for one pass of the chords: (bar in the cycle, beat 1-4, length in beats, note).
_TUNE = [
    (0, 1, 2, "Eb5"), (0, 3, 1, "D5"), (0, 4, 1, "Bb4"),
    (1, 1, 3, "C5"), (1, 4, 1, "G4"),
    (2, 1, 1.5, "Ab4"), (2, 2.5, 0.5, "C5"), (2, 3, 2, "Eb5"),
    (3, 1, 3, "F5"), (3, 4, 1, "Eb5"),
    (4, 1, 4, "D5"),
    (5, 1, 2, "C5"), (5, 3, 1, "Ab4"), (5, 4, 1, "F4"),
    (6, 1, 3, "G4"), (6, 4, 1, "Ab4"),
    (7, 1, 2, "Ab4"), (7, 3, 2, "G4"),
]

# The muted horn's phrase in the middle (same eight chords): (bar, beat, beats, note).
_HORN = [
    (0, 2, 1, "G4"), (0, 3, 1, "Bb4"), (0, 4, 1, "C5"),
    (1, 1, 3, "D5"), (1, 4, 1, "C5"),
    (2, 1, 2, "Bb4"), (2, 3, 1, "Ab4"), (2, 4, 1, "G4"),
    (3, 1, 4, "F4"),
    (4, 3, 1, "Eb5"), (4, 4, 1, "D5"),
    (5, 1, 3, "C5"), (5, 4, 1, "Ab4"),
    (6, 1, 2, "G4"), (6, 3, 2, "F4"),
    (7, 1, 4, "Ab4"),
]


def snd_music_night_shift() -> None:
    """~2.7 minutes for Level 4, the Abandoned Office: "Night Shift". C minor, about 66 bpm, played
    freely (the tempo breathes, and slows down at the end).

    A sparse, melancholy solo piano in the rain, like someone alone on an office floor past
    midnight: lounge chords (Cm9, Abmaj7#11, Fm9, an altered G7, Ebmaj7#11, Dm7b5, Dbmaj7#11, a
    suspended G7 that never resolves), eight bars that keep coming round. Thunder far away is the
    only percussion.
    Structure: rain and a far roll of thunder (bars 0-2); the piano starts alone with slow rolled
    chords (2-4), then plays the tune over held, pedalled chords (A, 4-12); the left hand starts
    walking slowly in quarter notes under quieter chords and fragments of the tune an octave up
    (B, 12-20); in the middle a soft muted horn sings its own lonely phrase over sustained chords
    (C, 20-28); the piano comes back alone, thinner (A', 28-36), slows down (36-39) and stops on a
    held Abmaj7#11 over B-flat, a chord that wants to go somewhere and never does, while the rain
    goes on.
    """
    beat0 = 60.0 / 66.0
    total = 165.0
    tr = G._MTrack(total, seed=31040, wow=0.003, flutter=0.0008)
    rng = tr.rng

    # The tempo map: beats -> seconds. Each bar breathes (a little slower towards its end, like
    # a player leaning on the last beat), and the last four bars slow right down.
    db = 0.01
    grid = np.arange(0, 41 * 4 + db, db)
    stretch = 1 + 0.05 * np.sin(np.pi * (grid % 4) / 4) ** 2 + 0.6 * np.clip((grid - 36 * 4) / 12, 0, 1) ** 1.5
    secs = np.concatenate([[0.0], np.cumsum(stretch * beat0 * db)])[:-1] + 0.2

    def when(bar_no: float, beat_no: float = 1.0) -> float:
        """Seconds at which beat `beat_no` (1-4) of bar `bar_no` falls, with a human wobble."""
        return float(np.interp(bar_no * 4 + beat_no - 1, grid, secs)) + rng.uniform(-0.012, 0.012)

    def chord(bar_no: int, cyc: int, vel: float, lh: bool = True, roll: float = 0.03, hold_bars: float = 1.0):
        """Pedalled chord: left-hand shell and right-hand voicing, held until the next bar."""
        shell, voicing, _, _ = _CHORDS[cyc % 8]
        end = when(bar_no + hold_bars)
        if lh:
            for k, note in enumerate(shell):
                at = when(bar_no) + k * roll * 0.5
                _piano(tr, "piano", at, end - at, note, vel * 0.95, pan=-0.25)
        for k, note in enumerate(voicing):
            at = when(bar_no) + 0.02 + k * roll
            _piano(tr, "piano", at, end - at, note, vel * rng.uniform(0.75, 0.9), pan=0.0 + 0.04 * k)

    def tune(bar0: int, vel: float, octave: int = 0, keep=None, pedal: bool = True):
        for bar_no, b, length, note in _TUNE:
            if keep is not None and not keep(bar_no, b):
                continue
            at = when(bar0 + bar_no, b)
            end = when(bar0 + bar_no + 1) if pedal else when(bar0 + bar_no, b + length) - 0.05
            end = max(end, when(bar0 + bar_no, b + length) - 0.05)
            _piano(tr, "piano", at, end - at, _name(_midi(note) + 12 * octave),
                   vel * rng.uniform(0.85, 1.0), pan=0.2)

    def walk(bar_no: int, cyc: int, vel: float, halves: bool = False):
        """A slow walking bass in the left hand: root, third, fifth, then a half step into the
        next chord's root (or just root and fifth, in half notes, when it thins out)."""
        _, _, root, (third, fifth) = _CHORDS[cyc % 8]
        nxt = _CHORDS[(cyc + 1) % 8][2]
        steps = [root, root + fifth] if halves else [root, root + third, root + fifth,
                                                      nxt + (1 if (cyc % 2) else -1)]
        span = 4 / len(steps)
        for k, m in enumerate(steps):
            while m > 45:          # keep the bass low (below A2)
                m -= 12
            at = when(bar_no, 1 + k * span)
            _piano(tr, "piano", at, when(bar_no, 1 + (k + 1) * span) - at - 0.06, _name(m),
                   vel * rng.uniform(0.9, 1.0), pan=-0.3)

    # Intro (bars 2-4): two slow rolled chords alone.
    chord(2, 0, 0.42, roll=0.16)
    chord(3, 1, 0.40, roll=0.16)
    # A (bars 4-12): the tune over pedalled chords.
    for i in range(8):
        chord(4 + i, i, 0.36)
    tune(4, 0.52)
    # B (bars 12-20): the walk; chords only as soft stabs; fragments of the tune an octave up.
    for i in range(8):
        walk(12 + i, i, 0.5)
        shell, voicing, _, _ = _CHORDS[i]
        for b in (1.0, 3.5) if i % 2 == 0 else (2.5,):
            at = when(12 + i, b)
            for k, note in enumerate(voicing):
                _piano(tr, "piano", at + 0.015 * k, beat0 * 1.2, note, 0.3 * rng.uniform(0.8, 1.0), pan=0.05)
    tune(12, 0.4, octave=1, keep=lambda bar_no, b: b <= 2.5 and bar_no % 2 == 0, pedal=False)
    # C (bars 20-28): the horn's phrase over long, soft chords.
    for i in range(8):
        chord(20 + i, i, 0.3, roll=0.06)
    for bar_no, b, length, note in _HORN:
        at = when(20 + bar_no, b)
        _horn(tr, "horn", at, when(20 + bar_no, b + length) - at, note, 0.5 if length < 3 else 0.55)
    # A' (bars 28-36): the piano alone again, thinner: half-note bass, the tune with holes in it.
    for i in range(8):
        walk(28 + i, i, 0.4, halves=True)
        _, voicing, _, _ = _CHORDS[i]
        at = when(28 + i, 1.5)
        for k, note in enumerate(voicing):
            _piano(tr, "piano", at + 0.03 * k, when(29 + i) - at, note, 0.27 * rng.uniform(0.8, 1.0), pan=0.05)
    tune(28, 0.42, keep=lambda bar_no, b: (bar_no + int(b)) % 3 != 0)
    # Ending (bars 36-39): three chords slowing down, then the held, unresolved one.
    chord(36, 0, 0.34, roll=0.08)
    chord(37, 1, 0.31, roll=0.1)
    chord(38, 2, 0.28, roll=0.12)
    for at_b, length, note in ((1, 1.5, "Eb5"), (3, 1, "D5")):
        _piano(tr, "piano", when(36, at_b), when(37) - when(36, at_b), note, 0.42, pan=0.2)
    _piano(tr, "piano", when(38, 1), when(39) - when(38, 1), "C5", 0.36, pan=0.2)
    last = when(39)
    hold = total - 6.0 - last
    for k, note in enumerate(["Bb1", "Ab2", "C4", "D4", "G4", "Eb4"]):
        _piano(tr, "piano", last + 0.2 * k, hold - 0.2 * k, note, 0.34 if k < 2 else 0.28, pan=-0.25 + 0.1 * k)
    _piano(tr, "piano", last + 1.6, hold - 1.6, "D5", 0.3, pan=0.2)

    # Thunder far off, a handful of times (the only "drums").
    for at, vel in ((4.5, 1.0), (56.0, 0.9), (84.0, 1.2), (118.0, 0.8), (152.0, 1.1)):
        _thunder(tr, "thunder", at, vel, length=11.0)
    # Rain all the way through, heavier in the middle.
    _rain(tr, "rain", [(0, 0.0), (5, 0.25), (40, 0.22), (75, 0.3), (100, 0.3), (130, 0.22),
                       (165, 0.25)])
    tr.noise_bed("room", 25, 140, [(0, 0.0), (6, 0.012), (165, 0.012)], drift_amt=0.2)

    _shape(tr, "piano", lambda f: G.lp_gain(f, 5200, 2) * G.hp_gain(f, 35, 2))
    _shape(tr, "horn", lambda f: G.bp_gain(f, 160, 3600, 2))
    L, R = tr.master(mix={"piano": 1.0, "horn": 0.42, "thunder": 0.7, "rain": 1.0, "room": 1.0},
                     reverb={"piano": 0.35, "horn": 0.6, "thunder": 0.2},
                     room_s=4.5, room_lp=3600, lp=6500, drive=1.2, hiss=0.0025)
    _save_matched(L, R, "level4", fade_out=6.0)


SOUNDS = [snd_music_pressure, snd_music_night_shift]
