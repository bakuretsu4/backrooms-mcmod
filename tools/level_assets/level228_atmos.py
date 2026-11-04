"""Level 228 ("Maze of Mirrors"): the look and the sound of the place. Everything here is drawn or
synthesised from scratch (no recordings, nothing copied); the idea of the level comes from the
Backrooms wiki, none of its text or pictures.

Textures (block atlas: few and small, it must stay 4096 x 4096):
  block/l228_light_blue|green|red   32 px  the coloured light panels set in the ceiling. Each is
                                           the colour at its palest; the block's `shade` darkens
                                           and deepens it through a block tint (client/light/
                                           Level228Lamps), so 3 textures cover 12 shades.
  block/l228_mirror                 32 px  the glass of a mirror pane: a faint dark green film
                                           (about 17 % cover). Every image deeper in the mirrors is
                                           seen through one more pane, so each depth comes out a
                                           little darker and greener on its own, like real mirrors.
  block/l228_exit_mirror            32 px  the exit: a black glass that shows nothing, with a faint
                                           cold shimmer drifting over it (16 frames, animated).

Sounds (all quiet; the level is quiet):
  level228/ambience        32 s loop   the halls' room tone: still air, the glass faintly singing
  level228/light_hum        4 s loop   the low electrical hum of a light panel (positional)
  level228/glass_tick_1-5              a pane ticking as it settles
  level228/glass_creak_1-4             a pane creaking in its frame
  exit_hint/level_228       8 s loop   the exit's cold draught, with a faint glassy ring
  music/level228           ~2.4 min    "Silvering": a tune and its reflection

Run only these with:  python tools/gen_assets.py l228
"""
from __future__ import annotations

import numpy as np

import gen_assets as G
from anomaly_assets import music_c as C

SR = G.SR
OUT = G.SND / "level228"


# ------------------------------------------------------------------------------- textures

def _wrap_noise(rng, n: int, beta: float, kmin: float = 1.0) -> np.ndarray:
    """Seamless fractal noise on an n x n tile, zero mean, unit std."""
    return G.fnoise(rng, (n, n), beta=beta, kmin=kmin)


def tex_l228_lights() -> None:
    """The light panels: a frosted diffuser in a thin dark frame, brightest in the middle (where the
    tube sits behind it) and deeper in colour toward the frame, where less light gets through. The
    core is near white with only a hint of the colour, so the panel clips softly and blooms; the
    colour shows in the falloff, which is what the eye reads as "a blue light" even when the camera
    blows the middle out. All three share one drawing; only the colours differ."""
    n = 32
    rng = G.rng_for(22801)
    X, Y = G.grid((n, n))
    # Distance from the panel's middle, squarish (a panel, not a bulb).
    d = np.maximum(np.abs(X - n / 2), np.abs(Y - n / 2)) / (n / 2)
    r = np.hypot(X - n / 2, Y - n / 2) / (n / 2)
    glow = np.clip(1.0 - 0.55 * r ** 1.6, 0, 1)
    grain = 1.0 + 0.025 * _wrap_noise(rng, n, 2.2)
    frame = G.smoothstep(0.86, 0.94, d)                 # the frame round the diffuser
    colours = {
        # (core, edge): the core near white with a hint of the hue, the edge saturated.
        "blue": ((214, 228, 255), (52, 104, 255)),
        "green": ((216, 255, 226), (40, 214, 96)),
        "red": ((255, 220, 214), (236, 44, 40)),
    }
    for name, (core, edge) in colours.items():
        t = G.smoothstep(0.15, 1.0, r)[..., None]
        img = G.lerp(core, edge, t) * (glow * grain)[..., None]
        img = G.over(img, (22, 24, 28), frame)
        G.save_png(np.clip(img, 0, 255), G.TEX / "block" / f"l228_light_{name}.png")


def tex_l228_mirror() -> None:
    """The mirror pane's glass. Mostly see-through: what you see is the real geometry behind it (the
    image volume), so the pane itself only lays a faint dark green film over it (old mirror glass is
    green and swallows some light). A little uneven (smudges, a faint diagonal sheen), so the pane
    reads as a surface when you stand close and not as an open gap. Kept low on purpose: three panes
    deep the picture is already about 40 % darker and visibly greener, and the fog does the rest."""
    n = 32
    rng = G.rng_for(22802)
    X, Y = G.grid((n, n))
    smudge = G.smoothstep(0.4, 1.8, _wrap_noise(rng, n, 2.6))
    fine = _wrap_noise(rng, n, 1.2, kmin=4)
    # A soft diagonal sheen band (wraps: whole periods across the tile).
    sheen = 0.5 + 0.5 * np.cos(2 * np.pi * ((X + Y) / n))
    sheen = sheen ** 6
    rgb = G.fill((30, 46, 41), (n, n))
    rgb = G.add_lum(rgb, sheen * 34 + fine * 2.0, (0.9, 1.0, 0.98))
    alpha = 43 + 10 * smudge + 14 * sheen + 2.0 * fine
    img = np.concatenate([rgb, alpha[..., None]], axis=2)
    G.save_png(np.clip(img, 0, 255), G.TEX / "block" / "l228_mirror.png")


def tex_l228_exit_mirror() -> None:
    """The exit: a mirror that shows nothing. Near-opaque black glass (it is drawn full-bright, so
    no light ever greys it), with a faint cold shimmer moving across it: two soft bands of pale
    blue-white drifting at different speeds and angles, never brighter than a dim glow. 16 frames,
    interpolated, one loop every 3.2 s; every band moves a whole number of tiles per loop, so the
    loop and the tile are both seamless."""
    n = 32
    frames = 16
    rng = G.rng_for(22803)
    X, Y = G.grid((n, n))
    warp = _wrap_noise(rng, n, 2.8)
    strip = []
    for f in range(frames):
        ph = f / frames
        a = 0.5 + 0.5 * np.cos(2 * np.pi * ((X + 0.5 * Y) / n + ph + 0.06 * warp))
        b = 0.5 + 0.5 * np.cos(2 * np.pi * ((2 * X - Y) / n - 2 * ph + 0.04 * warp + 0.3))
        shimmer = 0.65 * a ** 8 + 0.45 * b ** 10
        rgb = G.fill((3, 4, 6), (n, n)) + shimmer[..., None] * np.array([26.0, 38.0, 48.0])
        alpha = 236 + 12 * shimmer
        strip.append(np.concatenate([rgb, alpha[..., None]], axis=2))
    path = G.TEX / "block" / "l228_exit_mirror.png"
    G.save_png(np.clip(np.concatenate(strip, axis=0), 0, 255), path)
    path.with_name("l228_exit_mirror.png.mcmeta").write_text('{"animation": {"frametime": 4, "interpolate": true}}\n')


# ------------------------------------------------------------------------------- sounds

def _loop_tone(freq: float, T: float, t: np.ndarray) -> np.ndarray:
    """A sine snapped to a whole number of cycles per loop (seamless)."""
    return np.sin(2 * np.pi * round(freq * T) / T * t)


def snd_l228_ambience() -> None:
    """The halls' room tone (32 s loop, played "in your head"): still air in narrow glass corridors
    (a soft low rush that breathes slowly) and, very faint, the glass singing: two narrow, high
    resonances that swell and fade on their own, like a wet finger far away on a rim. Nothing else:
    the level is quiet, and every tick of the glass should stand out against it."""
    T = 32.0
    n = int(T * SR)
    t = np.arange(n) / SR
    rng = G.rng_for(22811)
    air = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 30, 420, 2) / np.sqrt(np.maximum(f, 20)))
    air = G.norm(air) * G.drift(n, rng, 0.25, 0.04, 0.2)
    sing = np.zeros(n)
    for f0, period, ph in ((1871.0, 16.0, 0.0), (2806.0, 32.0 / 3.0, 2.1), (1244.0, 32.0, 4.0)):
        cycles = max(1, round(T / period))
        swell = (0.5 - 0.5 * np.cos(2 * np.pi * cycles * t / T + ph)) ** 3
        band = G.periodic_noise(n, rng, lambda f, f0=f0: np.exp(-((f - f0) / 6.0) ** 2))
        sing += G.norm(band) * swell
    hiss = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 2500, 9000, 2))
    x = 0.8 * air + 0.10 * G.norm(sing) + 0.03 * G.norm(hiss)
    G.save_ogg(x, OUT / "ambience.ogg", peak=0.45, loop=True)


def snd_l228_light_hum() -> None:
    """A light panel's hum (4 s loop): the low electrical hum of its ballast, mostly 100 Hz with a
    little 50 and 150 and a faint rasp high up (the neon-coloured tubes' transformer), steadier and
    much lower than Level 0's fluorescent buzz. Every partial makes whole cycles over the loop."""
    T = 4.0
    n = int(T * SR)
    t = np.arange(n) / SR
    rng = G.rng_for(22812)
    hum = sum(a * _loop_tone(f, T, t + p) for f, a, p in
              ((50, 0.35, 0.0), (100, 1.0, 0.13), (150, 0.28, 0.4), (200, 0.22, 0.7), (300, 0.08, 1.1)))
    # The rasp: the hum's peaks clipping a little, kept high and faint.
    rasp = np.tanh(3.0 * _loop_tone(100, T, t)) - np.sin(2 * np.pi * 100 * t)
    rasp = G.fft_filter(rasp, lambda f: G.hp_gain(f, 900, 2) * G.lp_gain(f, 4000, 2), circular=True)
    air = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 80, 600, 2))
    x = G.norm(hum) + 0.06 * G.norm(rasp) + 0.04 * G.norm(air)
    G.save_ogg(x * G.drift(n, rng, 0.06, 0.25, 1.0), OUT / "light_hum.ogg", peak=0.5, loop=True)


def _glass_modes(rng, n: int, base: float, count: int, decay: float) -> np.ndarray:
    """A small glass plate struck: a handful of inharmonic plate modes (roughly 1, 1.6, 2.3, 3.4 ...
    times the lowest), each a little detuned and dying away, the high ones faster."""
    t = np.arange(n) / SR
    x = np.zeros(n)
    for k, ratio in enumerate((1.0, 1.59, 2.31, 3.43, 4.62, 6.07)[:count]):
        f = base * ratio * rng.uniform(0.985, 1.015)
        x += rng.uniform(0.5, 1.0) / (1 + 0.6 * k) * np.sin(2 * np.pi * f * t + rng.uniform(0, 6.3)) \
            * np.exp(-t / (decay / (1 + 0.5 * k)))
    return x


def snd_l228_glass_tick() -> None:
    """A mirror pane ticking as it settles in its frame (five takes): a tiny dry click and the
    glass ringing for a moment after it, sometimes twice in quick succession."""
    rng = G.rng_for(22813)
    for k in range(5):
        n = int(0.45 * SR)
        t = np.arange(n) / SR
        x = np.zeros(n)
        hits = [0.004] + ([rng.uniform(0.05, 0.12)] if k in (1, 3) else [])
        for i, at in enumerate(hits):
            m = n - int(at * SR)
            click = rng.normal(size=m) * np.exp(-np.arange(m) / SR / 0.0012)
            ring = _glass_modes(rng, m, rng.uniform(1900, 3100), 5, rng.uniform(0.025, 0.06))
            hit = G.fft_filter(click, lambda f: G.hp_gain(f, 1500, 2)) * 0.6 + ring
            G.add_at(x, hit * (1.0 if i == 0 else 0.55), at, circular=False)
        x *= np.clip(t / 0.0008, 0, 1)
        G.save_ogg(x, OUT / f"glass_tick_{k + 1}.ogg", peak=0.6, fade_out=0.05)


def snd_l228_glass_creak() -> None:
    """A pane creaking in its frame (four takes): the glass slipping against the frame in a quick
    run of tiny stick-slip catches (the rate rising and falling), each catch ringing the pane's
    lowest modes; under it the faint low flex of the sheet. Short, dry, close."""
    rng = G.rng_for(22814)
    for k in range(4):
        dur = rng.uniform(0.7, 1.4)
        n = int(dur * SR)
        t = np.arange(n) / SR
        rate = np.interp(t, [0, dur * 0.35, dur * 0.7, dur], [18, rng.uniform(70, 130), rng.uniform(40, 70), 14])
        rate *= 1 + G.smooth_jitter(rng, n, 6, 0.3)
        ph = np.cumsum(rate) / SR
        catches = np.diff(np.floor(ph), prepend=0.0) > 0
        pulses = catches * rng.uniform(0.3, 1.0, n)
        res = np.zeros(n)
        for f0, q in ((rng.uniform(820, 980), 18.0), (rng.uniform(1350, 1550), 22.0), (rng.uniform(2200, 2500), 26.0)):
            res += G.fft_filter(pulses, lambda f, f0=f0, q=q: np.exp(-((f - f0) / (f0 / q)) ** 2))
        flex = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 120, 320, 2))
        env = np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 0.7
        x = (G.norm(res) + 0.18 * G.norm(flex)) * env
        G.save_ogg(x, OUT / f"glass_creak_{k + 1}.ogg", peak=0.55, fade_out=0.08)


def snd_l228_exit_hint() -> None:
    """The exit mirror's cold draught (8 s loop, written next to the other exit hints): air drawn
    through somewhere it shouldn't be (a soft hiss that swells and sinks), a thin whistle where it
    slips past the frame, a hollow low note from the dark behind the glass, and a faint high glassy
    ring that beats slowly, the black glass answering the draught. Honest about where it is: it
    comes from the exit and nowhere else."""
    T = 8.0
    n = int(T * SR)
    t = np.arange(n) / SR
    rng = G.rng_for(22815)

    def swell(period: float, depth: float, phase: float = 0.0) -> np.ndarray:
        cycles = max(1, round(T / period))
        return 1.0 - depth * 0.5 * (1.0 + np.sin(2 * np.pi * cycles * t / T + phase))

    draught = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 300, 3200, 2) / np.sqrt(np.maximum(f, 20)))
    whistle = G.periodic_noise(n, rng, lambda f: np.exp(-((f - 1460) / 28) ** 2))
    hollow = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 40, 110, 3))
    ring = 0.6 * _loop_tone(2637, T, t) + 0.45 * _loop_tone(2637.5, T, t) + 0.25 * _loop_tone(3951, T, t)
    gust = swell(4.0, 0.6) * swell(8.0 / 3.0, 0.3, 1.7)
    x = (G.norm(draught) * gust * 0.7 + G.norm(whistle) * gust * 0.22 + G.norm(hollow) * 0.45
         + ring * 0.05 * swell(8.0, 0.8, 0.6))
    G.save_ogg(x, G.SND / "exit_hint" / "level_228.ogg", peak=0.42, loop=True)


# ------------------------------------------------------------------------------- music

# E Aeolian, as scale degrees counted from E4 (0 = E4, 7 = E5). Diatonic inversion keeps the
# reflection in the key: a degree d reflects to 2 * AXIS - d.
_SCALE = (0, 2, 3, 5, 7, 8, 10)
_AXIS = 4                                  # B4: the mirror's surface


def _deg_hz(d: int, shift: float = 0.0) -> float:
    """Scale degree (0 = E4) -> Hz; `shift` adds semitones (the reflection's wrong notes)."""
    octave, step = divmod(d, 7)
    semis = 12 * octave + _SCALE[step] + shift
    return G._m_hz("E4") * 2 ** (semis / 12)


# The tune: (bar, beat, degree, beats) in 4/4, eight bars, two phrases.
_TUNE = [
    (0, 0, 7, 2), (0, 2, 6, 1), (0, 3, 4, 1),
    (1, 0, 5, 3), (1, 3, 4, 1),
    (2, 0, 2, 2), (2, 2, 4, 1), (2, 3, 7, 1),
    (3, 0, 8, 4),
    (4, 0, 9, 2), (4, 2, 8, 1), (4, 3, 7, 1),
    (5, 0, 5, 2), (5, 2, 4, 2),
    (6, 0, 3, 1), (6, 1, 4, 1), (6, 2, 5, 2),
    (7, 0, 4, 4),
]
# One chord per bar: (bass, voicing). A palindrome over the two halves of the piece:
# Em  Cmaj7  Am9  Bsus | Bsus  Am9  Cmaj7  Em.
_CHORDS = [("E2", ["B3", "E4", "G4"]), ("C2", ["G3", "B3", "E4"]), ("A1", ["C4", "E4", "B4"]),
           ("B1", ["F#3", "A3", "E4"]), ("E2", ["G3", "B3", "F#4"]), ("C2", ["E3", "B3", "G4"]),
           ("A1", ["E3", "C4", "B4"]), ("B1", ["F#3", "B3", "E4"])]


def snd_music_l228() -> None:
    """"Silvering", ~2.4 minutes for Level 228: a tune and its reflection. E minor, 56 bpm (one bar
    every 4.3 s), on glass: a glass harmonica sings the tune on the left; half a bar later a glassy
    electric piano answers on the right with the same tune turned upside down round B (its mirror
    image, note for note), through a ping-pong echo that keeps throwing it back and forth like two
    facing mirrors. The chords run forward and then backward (a palindrome).
      Intro (4 bars): the pad alone, the echo of a single note.
      A (8 bars):  the tune and its reflection.
      B (8 bars):  everything played backward: the tune in retrograde, the chords reversed, the pad
                   swelling in reverse into each bar.
      A' (8 bars): the tune again, but it stops after four bars; the reflection carries on alone,
                   and goes wrong (a few notes a semitone off): the reflection lies.
      Coda (4 bars): one note and an answer that doesn't match, the pad fading.
    Built on gen_assets' music track and music_c's glass instruments, matched to -16 LUFS."""
    beat = 60.0 / 56.0
    bar = 4 * beat
    intro, sec, coda = 4, 8, 4
    total = (intro + 3 * sec + coda) * bar + 9.0
    tr = G._MTrack(total, seed=22820, wow=0.0028, flutter=0.0006)
    lag = 2 * beat                                          # the reflection answers half a bar late

    def chords(start_bar: int, order, vel: float, reverse: bool = False) -> None:
        for i, idx in enumerate(order):
            bass, tones = _CHORDS[idx]
            at = (start_bar + i) * bar
            if reverse:
                # A reversed tail rising into the bar line, then the chord held quietly.
                tr.swell("pad", at, tones[1], vel * 0.9, length=bar * 0.95)
            tr.chord("pad", at, bar * 0.92, tones, vel, bright=1500, attack=1.4, release=3.2)
            tr.drone("low", at, bar + 1.5, bass, vel * 0.55, fade=1.2, beat=0.21)

    def tune(start_bar: int, notes, vel: float, reflect: bool, wrong=frozenset(), until_bar: int = 99) -> None:
        for i, (b, bt, d, beats) in enumerate(notes):
            if b >= until_bar:
                continue
            at = (start_bar + b) * bar + bt * beat
            if reflect:
                shift = 1.0 if i in wrong else 0.0
                C._glass_ep(tr, "refl", at + lag, _deg_hz(2 * _AXIS - d + 7, shift), vel * 0.8,
                            decay=1.2 + 0.5 * beats, pan=0.55, glass=0.6)
            else:
                C._glass_harmonica(tr, "lead", at, beats * beat * 0.95, _deg_hz(d), vel, pan=-0.45,
                                   attack=0.35, release=2.2)

    # Intro: the pad, and one note bouncing between the mirrors.
    chords(0, [0, 1, 2, 3], 0.12)
    C._glass_ep(tr, "refl", 1.5 * bar, _deg_hz(_AXIS + 7), 0.22, decay=2.0, pan=0.0, glass=0.8)

    # A: the tune and its reflection.
    a = intro
    chords(a, range(8), 0.14)
    tune(a, _TUNE, 0.30, reflect=False)
    tune(a, _TUNE, 0.30, reflect=True)

    # B: everything backward. The retrograde: the last note first, each note now starting where the
    # old one ended, counted back from the end of bar 8.
    b0 = intro + sec
    chords(b0, range(7, -1, -1), 0.13, reverse=True)
    retro = []
    for bb, bt, d, beats in reversed(_TUNE):
        end = 32 - (bb * 4 + bt)                            # in beats from the section start
        start = end - beats
        retro.append((start // 4, start % 4, d, beats))
    tune(b0, retro, 0.26, reflect=False)
    tune(b0, retro, 0.26, reflect=True)

    # A': the tune stops after four bars; the reflection goes on alone and lies.
    a2 = intro + 2 * sec
    chords(a2, range(8), 0.14)
    tune(a2, _TUNE, 0.30, reflect=False, until_bar=4)
    wrong = {11, 13, 16}
    tune(a2, _TUNE, 0.32, reflect=True, wrong=frozenset(wrong))

    # Coda: one note, and an answer that isn't its reflection.
    c0 = (intro + 3 * sec) * bar
    C._glass_harmonica(tr, "lead", c0, 2.5 * beat, _deg_hz(7), 0.24, pan=-0.45, attack=0.5, release=3.0)
    C._glass_ep(tr, "refl", c0 + lag, _deg_hz(2, 1.0), 0.24, decay=3.0, pan=0.55, glass=0.6)
    tr.chord("pad", c0, coda * bar, ["E3", "B3", "F#4", "G4"], 0.15, bright=1300, attack=2.0, release=7.0)
    tr.drone("low", c0, coda * bar + 4.0, "E2", 0.08, fade=3.0, beat=0.17)

    # The still air of the halls underneath.
    tr.noise_bed("air", 120, 1800, [(0, 0.0), (6, 0.006), (total * 0.5, 0.009), (total - 6, 0.005), (total, 0.0)],
                 drift_amt=0.5)

    # The reflection bounces between two facing mirrors: a ping-pong echo, each return duller.
    L, R = tr.buses["refl"]
    eL, eR = C._echo(L.astype(float), R.astype(float), delay=beat * 0.75, feedback=0.42, taps=5, lp=2600)
    tr.buses["refl"] = (eL.astype(np.float32), eR.astype(np.float32))

    L, R = tr.master(mix={"lead": 1.0, "refl": 0.8, "pad": 0.5, "low": 0.45, "air": 1.0},
                     reverb={"lead": 0.5, "refl": 0.45, "pad": 0.55, "low": 0.2, "air": 0.0},
                     room_s=5.0, room_lp=5000, lp=7800, drive=1.15)
    C._save_music(L, R, "level228", fade_out=8.0)


TEXTURES = [tex_l228_lights, tex_l228_mirror, tex_l228_exit_mirror]
SOUNDS = [snd_l228_ambience, snd_l228_light_hum, snd_l228_glass_tick, snd_l228_glass_creak,
          snd_l228_exit_hint, snd_music_l228]
