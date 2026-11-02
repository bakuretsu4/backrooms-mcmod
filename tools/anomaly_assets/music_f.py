"""Music, group F: an ORIGINAL piece for Level 369, composed note by note in code (nothing is
sampled or copied), in the same "worn tape" sound as the rest of the soundtrack.

  music/level369   "Comely"   Level 369 (an endless indoor garden, pastel walls, flowers for
                              floors, a place that makes you forget): a music-box waltz, a
                              lullaby in F, that forgets itself

Built on gen_assets' music track (G._MTrack) and music_c's instruments (the glassy electric piano
played softly as a harp, the chorused pad) and loudness matching (-16 LUFS like the rest).

Run only this with:  python tools/gen_assets.py music_level369
Fixed random seed 13601, so re-running gives identical files.
"""
from __future__ import annotations

import numpy as np

import gen_assets as G
from anomaly_assets import music_c as C

# The lullaby: (bar, beat, note, beats) in 3/4. Chords: F F Dm Dm Bb Bb C C F Am Dm Bb F/C C F F.
MELODY = [
    (0, 0, "A5", 2), (0, 2, "C6", 1), (1, 0, "F6", 2), (1, 2, "E6", 1),
    (2, 0, "D6", 2), (2, 2, "C6", 1), (3, 0, "A5", 3),
    (4, 0, "Bb5", 2), (4, 2, "D6", 1), (5, 0, "D6", 2), (5, 2, "Bb5", 1),
    (6, 0, "C6", 2), (6, 2, "E6", 1), (7, 0, "C6", 3),
    (8, 0, "A5", 2), (8, 2, "C6", 1), (9, 0, "E6", 2), (9, 2, "C6", 1),
    (10, 0, "F6", 2), (10, 2, "D6", 1), (11, 0, "D6", 2), (11, 2, "Bb5", 1),
    (12, 0, "A5", 2), (12, 2, "C6", 1), (13, 0, "G5", 1), (13, 1, "A5", 1), (13, 2, "Bb5", 1),
    (14, 0, "A5", 3), (15, 0, "F5", 3),
]
CHORDS = [("F2", ["A3", "C4", "F4"]), ("F2", ["A3", "C4", "F4"]), ("D2", ["F3", "A3", "D4"]), ("D2", ["F3", "A3", "D4"]),
          ("Bb1", ["F3", "Bb3", "D4"]), ("Bb1", ["F3", "Bb3", "D4"]), ("C2", ["G3", "C4", "E4"]), ("C2", ["G3", "Bb3", "E4"]),
          ("F2", ["A3", "C4", "F4"]), ("A1", ["E3", "A3", "C4"]), ("D2", ["F3", "A3", "D4"]), ("Bb1", ["F3", "Bb3", "D4"]),
          ("C2", ["F3", "A3", "C4"]), ("C2", ["E3", "G3", "Bb3"]), ("F2", ["A3", "C4", "F4"]), ("F2", ["A3", "C4", "F4"])]


def snd_music_level369() -> None:
    """"Comely", ~2.6 minutes for Level 369: lovely, and slowly less there. F major, a waltz at 66
    bpm (one bar every 2.7 s). A music box plays a simple lullaby over a soft harp and a warm pad;
    the garden's breeze moves underneath.
      Intro (4 bars): the pad and the breeze; the harp starts turning.
      A (16 bars): the lullaby, whole.
      A' (16 bars): again, but about a third of its notes are gone, as if the box skipped them;
        the pad grows warmer, as if nothing were wrong.
      A'' (16 bars): two thirds gone; the harp keeps only its bass on every other bar.
      Coda (4 bars): the box starts the tune once more, plays three notes, and stops. The pad holds
        F with a G in it (add9) and fades."""
    beat = 60.0 / 66.0
    bar = 3 * beat
    intro, sec = 4, 16
    total = (intro + 3 * sec + 4) * bar + 8.0
    tr = G._MTrack(total, seed=13601, wow=0.003, flutter=0.0007)
    rng = tr.rng

    # Which notes each pass forgets: a fixed random order of the tune's notes; A' drops the first
    # third of that order, A'' the first two thirds (so what A' forgot stays forgotten).
    order = rng.permutation(len(MELODY))
    forgotten = [set(), set(order[: len(MELODY) // 3]), set(order[: 2 * len(MELODY) // 3])]

    def harp(at, root, tones, vel, bass_only=False):
        C._glass_ep(tr, "harp", at, G._m_hz(root), vel * 1.1, decay=2.4, pan=-0.1, glass=0.15)
        if bass_only:
            return
        for k, note in enumerate(tones[:2] if rng.random() < 0.5 else tones[1:]):
            C._glass_ep(tr, "harp", at + (k + 1) * beat, G._m_hz(note), vel * rng.uniform(0.75, 0.9), decay=1.6,
                        pan=0.25 - 0.5 * k, glass=0.2)

    # Intro: pad and harp only.
    for b in range(intro):
        root, tones = CHORDS[b % 2]
        harp(b * bar, root, tones, 0.18)
    tr.chord("pad", 0.5, intro * bar + 2, ["F3", "A3", "C4", "G4"], 0.16, bright=1400, attack=4.0, release=5.0)

    for p in range(3):
        start = (intro + p * sec) * bar
        warmth = 0.17 + 0.03 * p
        for b, (root, tones) in enumerate(CHORDS):
            at = start + b * bar
            harp(at, root, tones, 0.2 - 0.03 * p, bass_only=(p == 2 and b % 2 == 1))
            if b % 2 == 0:
                C._chorus_pad(tr, "pad", at, 2 * bar, tones[0], warmth * 0.7, bright=1300, attack=2.0, release=3.0)
                C._chorus_pad(tr, "pad", at, 2 * bar, tones[-1], warmth * 0.6, bright=1300, attack=2.4, release=3.0)
        for i, (b, bt, note, beats) in enumerate(MELODY):
            if i in forgotten[p]:
                continue
            vel = 0.34 * rng.uniform(0.85, 1.0) * (1.0 - 0.12 * p)
            tr.bell("box", start + b * bar + bt * beat + rng.uniform(0, 0.015), note, vel, decay=1.2 + 0.4 * beats,
                    pan=float(rng.uniform(-0.15, 0.15)))

    # Coda: three notes of the tune, then nothing; the pad holds F add9 and fades.
    coda = (intro + 3 * sec) * bar
    for b, bt, note, beats in MELODY[:3]:
        tr.bell("box", coda + b * bar + bt * beat, note, 0.26, decay=1.2 + 0.4 * beats)
    harp(coda, "F2", ["A3", "C4", "F4"], 0.14, bass_only=True)
    tr.chord("pad", coda, 4 * bar, ["F3", "C4", "G4", "A4"], 0.2, bright=1300, attack=2.0, release=7.0)

    # The garden's breeze underneath, swelling a little in the middle.
    tr.noise_bed("air", 180, 2400, [(0, 0.0), (5, 0.010), (total * 0.5, 0.014), (total - 6, 0.008), (total, 0.0)],
                 drift_amt=0.6)

    L, R = tr.master(mix={"box": 1.0, "harp": 0.75, "pad": 0.55, "air": 1.0},
                     reverb={"box": 0.55, "harp": 0.45, "pad": 0.5, "air": 0.0},
                     room_s=4.5, room_lp=5200, lp=8000, drive=1.15)
    C._save_music(L, R, "level369", fade_out=7.0)


SOUNDS = [snd_music_level369]
