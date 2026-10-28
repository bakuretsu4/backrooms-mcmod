"""Level 227 "Megaflora": the atmosphere's sounds, its music and the void bubbles' glimpse pictures.

Level 227 is a planet-sized tree hanging upside down over a black void, with a whole rainforest
growing on each of its branches. The sound is about SIZE and LIFE gone slightly wrong: wind in an
ocean of leaves, birds whose songs are not quite bird songs, insects, the whole tree groaning as it
moves, the void breathing up from below the edges, and the Sun over the Void singing faintly.

Sounds (src/main/resources/assets/backrooms/sounds/...):
  ambient/level227_forest        seamless loop: wind in leaves, wrong birdsong, insects   (-30 LUFS)
  ambient/level227_void_wind     seamless loop: the hollow wind rising out of the void    (-27 LUFS)
  ambient/level227_sun_voice     seamless loop: faint choral whispers (the sun speaking)  (-30 LUFS)
  level227/beam_hum              seamless loop: a sunbeam's hum and sizzle                (-27 LUFS)
  level227/bubble_hum            seamless loop: a fallen void bubble's glassy shimmer     (-30 LUFS)
  level227/lurch1..2             the whole tree lurching: a vast groan, cracks, leaves    (-20 LUFS)
  level227/unstable              the tree tearing you loose (before the noclip)           (-21 LUFS)
  level227/drag                  something dragging you into Level 227 (from outside)     (-22 LUFS)
  level227/sun_burn              a beam finding you: the hum rising into a choir          (-20 LUFS)
  level227/disintegrate1..2      turning to white ash                                     (-21 LUFS)
  level227/sun_whisper1..4       a whispered phrase (with the sun's line in the action bar) (-27 LUFS)
  level227/bubble_enter          stepping into a bubble: a glassy pop and an in-rush      (-21 LUFS)
  ambient/distant/level227/*     far-off one-offs (client/audio/DistantAmbience), the common
                                 distant-sound loudness of gen_assets._da_save
  music/megaflora                ~2.6 min, wonder turning to dread (client/BackroomsMusic)
Textures (src/main/resources/assets/backrooms/textures/entity/void_bubble/<level>.png):
  a blurred, curved glimpse of each other level, mirrored on a bubble's surface

Loudness is set in LUFS (README "Mix"; the meter is level2_audio.lufs). Loops sit at -30 (the hum
and the void wind -27), one-off events well under the -18 ceiling. Files are written at that
level (not peak-normalised), so sounds.json plays them at volume 1.

Run only these with:  python tools/gen_assets.py l227atmos_
Every generator uses its own fixed random seed (22700-22799), so re-running gives identical files.
"""
from __future__ import annotations

import numpy as np

import gen_assets as G
from anomaly_assets import level2_audio as A

SR = G.SR


def _snd(*parts: str):
    path = G.SND
    for p in parts[:-1]:
        path = path / p
    return path / f"{parts[-1]}.ogg"


# =============================================================================
# Shared helpers
# =============================================================================

def _save(x, path, target: float, peak: float = 0.9, loop: bool = False,
          fade_in: float = 0.003, fade_out: float = 0.05) -> None:
    """Brings `x` to `target` LUFS and writes it (mono Ogg) at that level, in one-second blocks
    (libsndfile's Vorbis encoder overflows its stack on one long write)."""
    import soundfile as sf
    x = np.asarray(x, float)
    x = x - x.mean()
    if not loop:
        n = len(x)
        fi, fo = max(1, int(fade_in * SR)), max(1, int(fade_out * SR))
        x[:fi] *= np.linspace(0, 1, fi)
        x[n - fo:] *= np.linspace(1, 0, fo) ** 2
    x = A.loud(x, target, peak)
    path.parent.mkdir(parents=True, exist_ok=True)
    extra = {"compression_level": 0.0} if loop else {}
    with sf.SoundFile(str(path), "w", SR, 1, format="OGG", subtype="VORBIS", **extra) as out:
        y = x.astype(np.float32)
        for i in range(0, len(y), SR):
            out.write(y[i:i + SR])


def _loopify(x, T: float, xfade: float) -> np.ndarray:
    """Makes a seamless loop of T seconds from a clip at least T + xfade long: the extra tail is
    cross-faded (equal power) into the start, so the end runs straight into the beginning. Used for
    textures that can't easily be built periodic (filter sweeps, crackle)."""
    n, m = int(T * SR), int(xfade * SR)
    out = np.array(x[:n], float)
    w = np.linspace(0, np.pi / 2, m)
    out[:m] = out[:m] * np.sin(w) + np.asarray(x[n:n + m], float) * np.cos(w)
    return out


def _whole(f, T):
    """A frequency rounded to a whole number of cycles over T seconds (keeps loops seamless)."""
    return max(1.0, round(f * T)) / T


def _far(x, lp, hp=30.0) -> np.ndarray:
    """Distance: far sounds lose their highs (and a little of their deepest lows)."""
    return G.fft_filter(x, lambda f: G.lp_gain(f, lp, 2) * G.hp_gain(f, hp, 1))


def _forest_room(rng, x, wet=0.5, length=3.5, decay=1.1, lp=3200.0) -> np.ndarray:
    """The forest's 'room': thousands of trunks and leaves scatter sound into a soft, diffuse tail
    with no hard slap-back (unlike Level 39's facades)."""
    ir = G.reverb_ir(rng, length, decay, lp=lp, predelay=0.02)
    out = np.concatenate([x, np.zeros(int(length * SR))])
    tail = G.conv(out, ir)[:len(out)]
    return out + wet * G.norm(tail) * np.abs(x).max()


def _env(n, pts):
    """A piecewise-linear envelope through (seconds, level) points."""
    t = np.arange(n) / SR
    return np.interp(t, [p[0] for p in pts], [p[1] for p in pts])


def _choir_voice(rng, f0, vowel: str, nh: int = 40) -> np.ndarray:
    """One sung voice on the pitch curve f0 (Hz per sample), vowel 'a', 'o' or 'u'."""
    formants = {
        "a": [(800, 90, 1.0), (1150, 110, 0.55), (2900, 180, 0.18), (3300, 220, 0.1)],
        "o": [(450, 70, 1.0), (800, 90, 0.5), (2830, 180, 0.12)],
        "u": [(325, 60, 1.0), (700, 80, 0.3), (2530, 170, 0.08)],
    }[vowel]
    return G.voice(f0, formants, rng, nh=nh, tilt=0.02)


def _sung(rng, n, hz, vowel="a", vib=5.2, depth=0.006, drift=0.004) -> np.ndarray:
    """A sustained sung note: gentle vibrato, slow pitch drift, breath noise."""
    t = np.arange(n) / SR
    f0 = hz * (1 + depth * np.sin(2 * np.pi * vib * t + rng.uniform(0, 6.3))
               + G.smooth_jitter(rng, n, 0.6, drift))
    v = G.norm(_choir_voice(rng, f0, vowel))
    breath = G.norm(G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 900, 5000, 2)))
    return v + 0.08 * breath


def _whisper(rng, dur, syllables) -> np.ndarray:
    """A whispered phrase: noise through moving vowel formants, one burst per syllable."""
    n = int(dur * SR)
    out = np.zeros(n)
    t0 = 0.08
    for length, vowel in syllables:
        m = int(length * SR)
        nz = rng.normal(size=m)
        formants = {"a": (800, 1150, 2900), "e": (400, 2000, 2600), "i": (300, 2300, 3000),
                    "o": (450, 800, 2830), "u": (325, 700, 2530), "s": (5000, 7000, 8500)}[vowel]
        syl = sum(G.fft_filter(nz, lambda f, F=F: G.bp_gain(f, F * 0.85, F * 1.15, 3)) * g
                  for F, g in zip(formants, (1.0, 0.7, 0.35)))
        env = np.sin(np.pi * np.clip(np.arange(m) / m, 0, 1)) ** 0.8
        G.add_at(out, G.norm(syl) * env * rng.uniform(0.6, 1.0), t0, False)
        t0 += length * rng.uniform(0.75, 0.95)
    return out


def _bird_call(rng, kind: int) -> np.ndarray:
    """A bird call that is not quite right. kind 0: a too-slow, too-low descending whistle that
    lands on an out-of-tune note; 1: a call played backwards (swells up and stops dead); 2: a
    trill that stutters like a skipping record."""
    if kind == 0:
        notes = [rng.uniform(1500, 1700), rng.uniform(1250, 1350), rng.uniform(1050, 1120), rng.uniform(1135, 1160)]
        out = []
        for i, f in enumerate(notes):
            d = rng.uniform(0.22, 0.34)
            m = int(d * SR)
            tt = np.arange(m) / SR
            glide = f * (1 + 0.06 * np.exp(-tt / 0.05) - 0.03 * tt / d)
            s = np.sin(2 * np.pi * np.cumsum(glide) / SR) * np.sin(np.pi * tt / d) ** 0.6
            out += [s, np.zeros(int(rng.uniform(0.06, 0.14) * SR))]
        return np.concatenate(out)
    if kind == 1:
        d = rng.uniform(0.7, 1.0)
        m = int(d * SR)
        tt = np.arange(m) / SR
        f = rng.uniform(2200, 2800) * (1 + 0.25 * np.sin(2 * np.pi * 7.0 * tt)) * (1 - 0.35 * tt / d)
        s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt / 0.18)
        return s[::-1].copy()
    pieces = []
    f = rng.uniform(3000, 3600)
    for i in range(int(rng.integers(9, 15))):
        d = 0.045
        m = int(d * SR)
        tt = np.arange(m) / SR
        s = np.sin(2 * np.pi * np.cumsum(f * (1 + 0.15 * (1 - tt / d))) / SR) * np.sin(np.pi * tt / d)
        pieces.append(s)
        # The "record" skips: some notes repeat after a hiccup of silence.
        pieces.append(np.zeros(int((0.02 if rng.random() > 0.25 else 0.11) * SR)))
    return np.concatenate(pieces)


# =============================================================================
# Loops
# =============================================================================

def snd_l227atmos_forest() -> None:
    """32 s loop: the forest on a branch. A vast, slow wind through an ocean of leaves (a far roar
    plus near rustles in gusts), insects droning in waves, wrong birdsong far off, and under it all
    the faint low breath of the tree itself."""
    rng = G.rng_for(22700)
    T = 32.0
    n = int(T * SR)
    t = np.arange(n) / SR
    gust = G.periodic_noise(n, rng, lambda f: ((f > 0.03) & (f < 0.14)).astype(float))
    gust = 0.45 + 0.55 * np.clip((gust / np.abs(gust).max() + 1) / 2, 0, 1) ** 1.4
    roar = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 140, 1100, 2)) * (0.6 + 0.4 * gust)
    # Leaf flutter: high rustle amplitude-modulated by a fast, uneven flicker, riding the gusts.
    flutter = G.periodic_noise(n, rng, lambda f: ((f > 3.0) & (f < 14.0)).astype(float))
    flutter = np.clip(0.5 + flutter / np.abs(flutter).max(), 0, 1.5)
    rustle = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 1600, 6500, 2)) * flutter * gust ** 2
    # Insects: two cicada voices pulsing at whole-cycle rates, swelling and fading in long waves.
    insects = np.zeros(n)
    for fc, rate, period, depth in ((4300.0, 47.0, 16.0, 0.9), (6150.0, 61.0, 10.667, 0.6)):
        fcw, ratew, wave = _whole(fc, T), _whole(rate, T), _whole(1.0 / period, T)
        pulse = np.clip(np.sin(2 * np.pi * ratew * t), 0, 1) ** 3
        swell = np.clip(np.sin(2 * np.pi * wave * t + rng.uniform(0, 6.3)), 0, 1) ** 2
        insects += depth * np.sin(2 * np.pi * fcw * t) * pulse * swell
    insects = G.fft_filter(insects, lambda f: G.lp_gain(f, 7000, 2), circular=True)
    # Wrong birds: a few calls placed round the loop, far away and softened by the forest.
    birds = np.zeros(n)
    for start, kind in ((2.5, 0), (9.1, 2), (14.8, 1), (21.6, 0), (27.3, 2)):
        call = _far(_bird_call(rng, kind), 3800)
        G.add_at(birds, call * rng.uniform(0.6, 1.0), start, True)
    birds = birds + 0.6 * G.norm(G.circ_conv(birds, G.reverb_ir(rng, 2.5, 0.7, lp=3000, predelay=0.03))) * np.abs(birds).max()
    # The tree breathing: a faint, very low tone (whole cycles) that swells slowly.
    breath = np.sin(2 * np.pi * _whole(37.0, T) * t) + 0.4 * np.sin(2 * np.pi * _whole(55.5, T) * t + 1.0)
    breath *= 0.5 + 0.5 * np.sin(2 * np.pi * _whole(1 / 16.0, T) * t)
    x = (G.norm(roar) * 0.55 + G.norm(rustle) * 0.22 + G.norm(insects) * 0.05 + G.norm(birds) * 0.16
         + G.norm(breath) * 0.12)
    _save(x, _snd("ambient", "level227_forest"), -30.0, peak=0.5, loop=True)


def snd_l227atmos_void_wind() -> None:
    """20 s loop: the void below the edges. A deep, hollow rush of air coming UP out of nothing,
    with slow howls (resonances sliding up and down) as it pours over the curve of the bark."""
    rng = G.rng_for(22701)
    T, xf = 20.0, 3.0
    n = int((T + xf) * SR)
    t = np.arange(n) / SR
    rush = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 35, 700, 2))
    swell = 0.55 + 0.45 * np.sin(2 * np.pi * t / 11.5 + 1.0) * np.sin(2 * np.pi * t / 7.3)
    howl = np.zeros(n)
    for base, depth, rate in ((160.0, 60.0, 0.09), (260.0, 90.0, 0.13), (95.0, 30.0, 0.07)):
        fc = base + depth * np.sin(2 * np.pi * rate * t + rng.uniform(0, 6.3))
        howl += G.svf(rng.normal(size=n), fc, 22.0) * rng.uniform(0.6, 1.0)
    howl *= np.clip(np.sin(2 * np.pi * t / 9.0 + 0.4), 0, 1) ** 1.5 + 0.2
    sub = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 18, 70, 3))
    x = G.norm(rush) * 0.6 * swell + G.norm(howl) * 0.3 + G.norm(sub) * 0.35
    _save(_loopify(x, T, xf), _snd("ambient", "level227_void_wind"), -27.0, peak=0.6, loop=True)


def snd_l227atmos_sun_voice() -> None:
    """24 s loop: the Sun over the Void speaking. Not words: a faint choir of sung vowels on a
    chord that is almost consonant (a fifth with a flat ninth hanging over it), voices drifting in
    and out, and breathy whispers just under the threshold of understanding."""
    rng = G.rng_for(22702)
    T, xf = 24.0, 4.0
    n = int((T + xf) * SR)
    t = np.arange(n) / SR
    choir = np.zeros(n)
    for hz, vowel, period, phase in ((329.6, "a", 13.0, 0.0), (493.9, "o", 17.0, 1.7), (349.2, "u", 11.0, 3.1),
                                     (659.3, "a", 19.0, 4.4), (246.9, "o", 15.0, 2.2)):
        v = _sung(rng, n, hz * rng.uniform(0.997, 1.003), vowel)
        env = np.clip(np.sin(2 * np.pi * t / period + phase), 0, 1) ** 2
        choir += v * env
    choir = G.fft_filter(choir, lambda f: G.lp_gain(f, 3800, 2) * G.hp_gain(f, 150, 2))
    whispers = np.zeros(n)
    for start in np.arange(0.6, T + xf - 2.5, 2.9):
        w = _whisper(rng, 2.2, [(rng.uniform(0.18, 0.32), rng.choice(list("aeious"))) for _ in range(int(rng.integers(3, 7)))])
        G.add_at(whispers, w * rng.uniform(0.4, 1.0), start + rng.uniform(-0.4, 0.4), False)
    x = G.norm(choir) * 0.7 + G.norm(whispers) * 0.35
    x = _forest_room(rng, x, wet=0.9, length=5.0, decay=1.8, lp=4000)[:n]
    _save(_loopify(x, T, xf), _snd("ambient", "level227_sun_voice"), -30.0, peak=0.5, loop=True)


def snd_l227atmos_beam_hum() -> None:
    """12 s loop: a sunbeam. A clean, slightly beating tone stack (light made audible), a thin
    glassy overtone far above it, and a faint sizzle where the light cooks the air."""
    rng = G.rng_for(22703)
    T, xf = 12.0, 2.0
    n = int((T + xf) * SR)
    t = np.arange(n) / SR
    tone = np.zeros(n)
    for k, a in ((1, 1.0), (2, 0.5), (3, 0.35), (5, 0.22), (7, 0.12), (9, 0.06)):
        f = 110.0 * k
        tone += a * (np.sin(2 * np.pi * f * t) + 0.8 * np.sin(2 * np.pi * (f + 0.35 * k) * t + k))
    glass = np.sin(2 * np.pi * 1762.0 * t) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.21 * t))
    sizzle = G._crackle(rng, T + xf, 900, f_lo=2200)
    x = G.norm(tone) * 0.6 + G.norm(glass) * 0.06 + G.norm(sizzle) * 0.12
    _save(_loopify(x, T, xf), _snd("level227", "beam_hum"), -27.0, peak=0.6, loop=True)


def snd_l227atmos_bubble_hum() -> None:
    """8 s loop: a fallen bubble. A glassy, inharmonic shimmer with a fast tremolo, and air rushing
    round it (its surface moves very fast)."""
    rng = G.rng_for(22704)
    T, xf = 8.0, 1.5
    n = int((T + xf) * SR)
    t = np.arange(n) / SR
    glass = sum(a * np.sin(2 * np.pi * f * t + rng.uniform(0, 6.3))
                for f, a in ((523.0, 1.0), (1443.0, 0.45), (2830.0, 0.25), (4680.0, 0.1)))
    glass *= 0.7 + 0.3 * np.sin(2 * np.pi * 9.0 * t)
    whoosh = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 400, 3000, 2))
    whoosh *= 0.5 + 0.5 * np.sin(2 * np.pi * 2.3 * t) ** 2
    x = G.norm(glass) * 0.45 + G.norm(whoosh) * 0.25
    _save(_loopify(x, T, xf), _snd("level227", "bubble_hum"), -30.0, peak=0.5, loop=True)


# =============================================================================
# Events
# =============================================================================

def _groan(rng, dur, f_pts, q=14.0) -> np.ndarray:
    """A colossal wooden groan: resonant noise following a pitch curve, with a second resonance an
    octave up and a slow stick-slip creak through it."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    fc = np.interp(t, [p[0] for p in f_pts], [p[1] for p in f_pts])
    moan = G.svf(rng.normal(size=n), fc, q) + 0.4 * G.svf(rng.normal(size=n), fc * 2.02, q * 0.8)
    creak = G._creak(rng, dur, 5, 16, [rng.uniform(90, 130), rng.uniform(210, 260), rng.uniform(400, 480)])
    return G.norm(moan) + 0.35 * G.norm(creak)


def _crack(rng) -> np.ndarray:
    """A huge branch cracking: a sharp splintering burst over a deep thud."""
    hit = G._da_hit(rng, [rng.uniform(55, 80), rng.uniform(140, 190), rng.uniform(380, 520)],
                    [0.25, 0.12, 0.05], [1.0, 0.6, 0.3], noise_ms=6.0, noise_lp=5000)
    splinter = G._crackle(rng, 0.6, 2500, f_lo=1200)
    m = max(len(hit), len(splinter))
    return np.pad(G.norm(hit), (0, m - len(hit))) + 0.5 * np.pad(G.norm(splinter), (0, m - len(splinter)))


def _leaves_rush(rng, dur, rise, fall) -> np.ndarray:
    n = int(dur * SR)
    nz = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 700, 7000, 2))
    flick = np.clip(0.5 + G.smooth_jitter(rng, n, 12.0, 1.0), 0, 1.5)
    return G.norm(nz * flick) * _env(n, [(0, 0), (rise, 1), (dur - fall, 0.8), (dur, 0)])


def snd_l227atmos_lurch() -> None:
    """Two ~10 s takes: the whole tree lurching in the void. A vast low groan that slides and
    settles, wood cracking somewhere above, and a wave of leaves rushing as every branch sways."""
    for i, seed in enumerate((22710, 22711), 1):
        rng = G.rng_for(seed)
        dur = 9.0
        n = int(dur * SR)
        g = _groan(rng, dur, [(0, rng.uniform(26, 32)), (1.6, rng.uniform(48, 58)), (4.5, rng.uniform(40, 46)),
                              (7.5, rng.uniform(30, 34)), (dur, 26)])
        g *= _env(n, [(0, 0), (0.7, 1), (6.0, 0.85), (dur, 0)])
        x = _far(g, 700, hp=18)
        for at in (1.2 + rng.uniform(0, 0.4), 2.6 + rng.uniform(0, 0.8)):
            G.add_at(x, _far(_crack(rng), 2600) * rng.uniform(0.35, 0.55), at, False)
        G.add_at(x, _leaves_rush(rng, 6.0, 1.4, 3.0) * 0.4, 1.0, False)
        x = _forest_room(rng, x, wet=0.7, length=5.0, decay=1.6, lp=1500)
        _save(x, _snd("level227", f"lurch{i}"), -20.0, peak=0.85, fade_out=1.5)


def snd_l227atmos_unstable() -> None:
    """~7 s: the tree shudders and tears you loose. A groan building from the deep, creaks
    multiplying, the sun's choir rising in pitch underneath, wind sucking inward, then a crack and
    nothing (the noclip cuts in on the silence)."""
    rng = G.rng_for(22712)
    dur = 7.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    g = _groan(rng, dur, [(0, 24), (3.0, 38), (5.5, 62), (dur, 80)], q=10.0)
    g *= _env(n, [(0, 0), (1.5, 0.5), (5.8, 1.0), (6.55, 1.0), (6.6, 0)])
    creaks = G._creak(rng, dur, 4, 40, [180, 420, 900]) * _env(n, [(0, 0.2), (6.5, 1.0), (6.6, 0)])
    rise = 1 + 0.25 * (t / dur) ** 2
    choir = G.norm(_choir_voice(rng, 330.0 * rise, "a")) + 0.7 * G.norm(_choir_voice(rng, 349.2 * rise, "o"))
    choir = G.fft_filter(choir, lambda f: G.lp_gain(f, 2500, 2)) * _env(n, [(0, 0), (4.0, 0.25), (6.5, 0.6), (6.6, 0)])
    suck = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 300, 3000, 2))
    suck *= _env(n, [(0, 0), (3.0, 0.2), (6.5, 1.0), (6.6, 0)])
    x = _far(g, 900, hp=18) + 0.3 * G.norm(creaks) + 0.25 * G.norm(choir) + 0.25 * G.norm(suck)
    G.add_at(x, _crack(rng) * 0.6, 6.45, False)
    _save(x, _snd("level227", "unstable"), -21.0, peak=0.85, fade_out=0.05)


def snd_l227atmos_drag() -> None:
    """~4.5 s: being dragged into Level 227 from a forest somewhere else. The leaves around you
    suddenly rush toward one point, a deep groan comes from under the ground, and the air pulls."""
    rng = G.rng_for(22713)
    dur = 4.5
    n = int(dur * SR)
    t = np.arange(n) / SR
    leaves = _leaves_rush(rng, dur, 1.0, 0.3)
    g = _groan(rng, dur, [(0, 30), (2.0, 42), (dur, 55)]) * _env(n, [(0, 0), (1.5, 0.6), (4.3, 1.0), (dur, 0)])
    pull = G.svf(rng.normal(size=n), 300 + 1600 * (t / dur) ** 2, 4.0) * _env(n, [(0, 0), (2.5, 0.4), (4.4, 1), (dur, 0)])
    x = 0.5 * leaves + _far(g, 600, hp=18) * 0.7 + 0.3 * G.norm(pull)
    _save(x, _snd("level227", "drag"), -22.0, peak=0.85, fade_out=0.08)


def snd_l227atmos_sun_burn() -> None:
    """~3.2 s: a sunbeam finds you. The beam's hum climbs, a choir swells into one bright chord, and
    a sizzle builds as the light starts to take you apart."""
    rng = G.rng_for(22714)
    dur = 3.2
    n = int(dur * SR)
    t = np.arange(n) / SR
    rise = 1 + 0.5 * (t / dur) ** 1.5
    hum = sum(a * np.sin(2 * np.pi * np.cumsum(110.0 * k * rise) / SR) for k, a in ((1, 1.0), (2, 0.6), (3, 0.4), (5, 0.25)))
    choir = sum(G.norm(_choir_voice(rng, hz * (1 + 0.004 * np.sin(2 * np.pi * 5.5 * t)), "a"))
                for hz in (440.0, 659.3, 880.0, 932.3))
    choir = G.fft_filter(choir, lambda f: G.lp_gain(f, 5000, 2))
    sizzle = G._crackle(rng, dur, 6000, f_lo=1800)
    x = (G.norm(hum) * _env(n, [(0, 0.2), (dur, 0.8)]) + 0.5 * G.norm(choir) * _env(n, [(0, 0), (2.4, 1.0), (dur, 0.8)])
         + 0.45 * G.norm(sizzle) * _env(n, [(0, 0), (1.0, 0.2), (dur, 1.0)]))
    _save(x, _snd("level227", "sun_burn"), -20.0, peak=0.85, fade_out=0.3)


def snd_l227atmos_disintegrate() -> None:
    """Two ~2.4 s takes: a body falling apart into white ash. Dense crackle, a dry crumbling
    rush, and a breath of air rising away."""
    for i, seed in enumerate((22715, 22716), 1):
        rng = G.rng_for(seed)
        dur = 2.4
        n = int(dur * SR)
        crackle = G._crackle(rng, dur, 9000, f_lo=1500)
        crumble = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 200, 2500, 2))
        crumble *= np.clip(0.4 + G.smooth_jitter(rng, n, 30, 1.0), 0, 1.4)
        rise = G.svf(rng.normal(size=n), 400 + 2400 * np.linspace(0, 1, n) ** 2, 5.0)
        x = (G.norm(crackle) * _env(n, [(0, 0.3), (0.3, 1), (dur, 0)]) + 0.6 * G.norm(crumble) * _env(n, [(0, 1), (dur, 0)])
             + 0.3 * G.norm(rise) * _env(n, [(0, 0), (1.0, 1), (dur, 0)]))
        _save(x, _snd("level227", f"disintegrate{i}"), -21.0, peak=0.85, fade_out=0.4)


def snd_l227atmos_sun_whisper() -> None:
    """Four ~2.5 s whispered phrases with a faint sung tone under them: what the sun's voice sounds
    like when it says something (the words themselves only show in the action bar)."""
    shapes = ["aoeu", "iase", "ouai", "esoa"]
    for i, seed in enumerate((22720, 22721, 22722, 22723), 1):
        rng = G.rng_for(seed)
        dur = 2.8
        n = int(dur * SR)
        w = _whisper(rng, dur, [(rng.uniform(0.22, 0.38), v) for v in shapes[i - 1]])
        tone = _sung(rng, n, rng.choice([329.6, 349.2, 246.9]), "o") * _env(n, [(0, 0), (0.6, 1), (dur - 0.5, 0.8), (dur, 0)])
        x = G.norm(w) + 0.18 * G.norm(G.fft_filter(tone, lambda f: G.lp_gain(f, 2000, 2)))
        x = _forest_room(rng, x, wet=0.8, length=3.0, decay=1.0, lp=5000)
        _save(x, _snd("level227", f"sun_whisper{i}"), -27.0, peak=0.7, fade_out=0.8)


def snd_l227atmos_bubble_enter() -> None:
    """~2 s: stepping into a fallen bubble. A glassy ping, a wet pop, and the air of the other level
    rushing in backwards."""
    rng = G.rng_for(22724)
    dur = 2.2
    n = int(dur * SR)
    t = np.arange(n) / SR
    ping = sum(a * np.sin(2 * np.pi * f * t) * np.exp(-t / d)
               for f, a, d in ((1046.0, 1.0, 0.6), (2890.0, 0.4, 0.3), (5240.0, 0.15, 0.15)))
    pop = G._da_chirp(180, 900, 0.08, 0.03)
    inrush = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 250, 4000, 2)) * _env(n, [(0, 0), (1.7, 1), (1.9, 0), (dur, 0)])
    x = 0.6 * G.norm(ping)
    G.add_at(x, G.norm(pop) * 0.7, 0.0, False)
    x = x + 0.45 * G.norm(inrush)
    _save(x, _snd("level227", "bubble_enter"), -21.0, peak=0.85, fade_out=0.1)


# =============================================================================
# Far-off one-offs (DistantAmbience, at the common distant loudness)
# =============================================================================

def snd_l227atmos_distant_tree_groan() -> None:
    """Three ~6 s takes: a branch far off shifting under its own weight as the tree moves."""
    for i, seed in enumerate((22730, 22731, 22732), 1):
        rng = G.rng_for(seed)
        dur = 4.5
        g = _groan(rng, dur, [(0, rng.uniform(40, 55)), (1.5, rng.uniform(70, 95)), (3.2, rng.uniform(55, 70)), (dur, 45)])
        g *= _env(int(dur * SR), [(0, 0), (0.9, 1), (3.0, 0.9), (dur, 0)])
        G._da_save(_forest_room(rng, _far(g, 800), wet=0.7, length=3.0, decay=1.2, lp=1200), "level227", "tree_groan", i)


def snd_l227atmos_distant_branch_crack() -> None:
    """Three takes: a giant limb cracking somewhere in the canopy, then leaves hissing as it settles."""
    for i, seed in enumerate((22733, 22734, 22735), 1):
        rng = G.rng_for(seed)
        x = np.zeros(int(4.0 * SR))
        G.add_at(x, _crack(rng), 0.05, False)
        G.add_at(x, _crack(rng) * 0.45, 0.35 + rng.uniform(0, 0.3), False)
        G.add_at(x, _leaves_rush(rng, 2.6, 0.3, 1.8) * 0.35, 0.4, False)
        G._da_save(_forest_room(rng, _far(x, 2200), wet=0.6), "level227", "branch_crack", i)


def snd_l227atmos_distant_wrong_bird() -> None:
    """Three takes: a bird call far away that is not right (too slow, backwards, or skipping)."""
    for i, seed in enumerate((22736, 22737, 22738), 1):
        rng = G.rng_for(seed)
        call = _bird_call(rng, i - 1)
        call = np.concatenate([call, np.zeros(int(0.4 * SR)), call * 0.8])
        G._da_save(_forest_room(rng, _far(call, 4000), wet=0.55, length=2.5, decay=0.8, lp=3500), "level227", "wrong_bird", i)


def snd_l227atmos_distant_animal_call() -> None:
    """Three takes: something large calling across the forest: a long bellow that slides down and
    breaks, too low for a deer and too long for a bear."""
    for i, seed in enumerate((22739, 22740, 22741), 1):
        rng = G.rng_for(seed)
        dur = rng.uniform(2.0, 2.8)
        n = int(dur * SR)
        t = np.arange(n) / SR
        f0 = np.interp(t, [0, 0.3, dur * 0.7, dur], [rng.uniform(95, 120), rng.uniform(140, 170), rng.uniform(110, 125), rng.uniform(70, 85)])
        f0 = f0 * (1 + G.smooth_jitter(rng, n, 9, 0.03))
        v = G.voice(f0, [(500, 120, 1.0), (900, 150, 0.6), (2400, 260, 0.2)], rng, nh=36, tilt=0.05)
        v = G.norm(v) * _env(n, [(0, 0), (0.15, 1), (dur - 0.4, 0.9), (dur, 0)])
        v = np.tanh(2.0 * v)
        G._da_save(_forest_room(rng, _far(v, 1800), wet=0.7, length=3.5, decay=1.3, lp=2000), "level227", "animal_call", i)


def snd_l227atmos_distant_leaf_fall() -> None:
    """Three takes: a huge leaf (or a dead frond as big as a sail) sliding down through the canopy,
    brushing leaves all the way, and landing with a soft slap."""
    for i, seed in enumerate((22742, 22743, 22744), 1):
        rng = G.rng_for(seed)
        dur = 3.4
        x = _leaves_rush(rng, dur, 0.8, 1.4) * 0.7
        slap = G._da_hit(rng, [rng.uniform(120, 180), rng.uniform(300, 420)], [0.06, 0.03], [1.0, 0.5], noise_ms=8.0, noise_lp=3000)
        G.add_at(x, G.norm(slap) * 0.6, dur - 0.5, False)
        G._da_save(_forest_room(rng, _far(x, 3000), wet=0.5), "level227", "leaf_fall", i)


def snd_l227atmos_distant_void_howl() -> None:
    """Two takes: the wind howling up out of the void somewhere below the edge of the branch."""
    for i, seed in enumerate((22745, 22746), 1):
        rng = G.rng_for(seed)
        dur = 5.0
        n = int(dur * SR)
        t = np.arange(n) / SR
        fc = np.interp(t, [0, 1.5, 3.5, dur], [rng.uniform(120, 160), rng.uniform(260, 330), rng.uniform(200, 240), 140])
        howl = G.svf(rng.normal(size=n), fc, 26.0) * _env(n, [(0, 0), (1.2, 1), (3.8, 0.8), (dur, 0)])
        rush = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 40, 500, 2)) * _env(n, [(0, 0), (1.0, 0.6), (dur, 0)])
        G._da_save(_far(G.norm(howl) + 0.6 * G.norm(rush), 1400), "level227", "void_howl", i)


# =============================================================================
# Music
# =============================================================================

def _m_choir(tr, bus: str, start: float, dur: float, note: str, vel: float, vowel: str = "a", pan: float = 0.0) -> None:
    """A sung note on a music track (the sun's voice joining the music)."""
    rng = tr.rng
    n = int(dur * SR)
    v = _sung(rng, n, G._m_hz(note), vowel, depth=0.008)
    v = G.fft_filter(v, lambda f: G.lp_gain(f, 3500, 2) * G.hp_gain(f, 120, 2))
    v = G.norm(v) * G._m_env(n, min(4.0, dur * 0.4), dur * 0.7, dur * 0.3) * vel
    tr.add(bus, start, v * np.sqrt(0.5 * (1 - pan)), v * np.sqrt(0.5 * (1 + pan)))


def snd_l227atmos_music_megaflora() -> None:
    """~2.6 minutes for Level 227: wonder turning into dread. It opens in E Lydian (the raised
    fourth, A-sharp, is the sound of sunlight through leaves): a slow celesta figure climbing like
    light through a canopy over warm, wide chords. Then the bass slides to that same A-sharp, a
    tritone under the key, and everything above it keeps playing as if nothing happened: the same
    chords now hang over a hole. The sun's choir enters on a long E that bends up to F; the tree
    groans (a far boom, creaks). The celesta comes back slowed and out of tune, and it ends on E and
    F together, unresolved, fading into wind."""
    bar = 4.8
    tr = G._MTrack(32 * bar + 6.0, seed=22790, wow=0.003)
    # Intro (bars 0-4): forest air, a low E fading in, a few high notes like distant birds.
    tr.drone("drone", 0.0, 15.5 * bar, "E1", 0.2, fade=14.0)
    for at, note in ((1.2, "B6"), (2.1, "F#6"), (2.6, "A#6"), (3.4, "E7"), (3.9, "D#7")):
        tr.bell("bird", at * bar + tr.rng.uniform(-0.1, 0.1), note, 0.12, decay=1.2, pan=tr.rng.uniform(-0.7, 0.7))
    # A (bars 4-14): E Lydian chords, root below, the climbing light figure on top.
    a_chords = [["E3", "B3", "D#4", "G#4", "A#4"], ["F#3", "C#4", "E4", "A#4"], ["G#3", "D#4", "F#4", "B4"],
                ["C#3", "G#3", "B3", "D#4", "A#4"], ["E3", "B3", "D#4", "F#4", "A#4"], ["A3", "E4", "G#4", "C#5"],
                ["F#3", "A#3", "C#4", "E4", "G#4"], ["B2", "F#3", "A#3", "D#4"], ["E3", "G#3", "B3", "D#4", "A#4"],
                ["C#3", "E3", "G#3", "B3", "F#4"]]
    a_roots = ["E2", "F#2", "G#2", "C#2", "E2", "A1", "F#2", "B1", "E2", "C#2"]
    for i, (ch, root) in enumerate(zip(a_chords, a_roots)):
        at = (4 + i) * bar
        tr.chord("soft", at, bar, ch, 0.22, bright=1900, attack=2.0, release=4.0, spread=0.22)
        tr.pad("bass", at, bar, root, 0.30, bright=320, attack=1.2, release=3.0)
    light = ["B4", "D#5", "F#5", "A#5", "B5", "D#6", "F#6", "A#6"]
    for k in range(4):
        base = (5 + 2 * k) * bar
        for j, note in enumerate(light[: 5 + k]):
            tr.bell("bell", base + j * 0.42 + tr.rng.uniform(-0.03, 0.05), note, 0.2 - 0.012 * j,
                    decay=2.6, pan=-0.5 + j * 0.14)
    # The turn (bar 14): a swell, and the bass drops to A#, a tritone under E.
    tr.swell("fx", 15 * bar, "A#2", 0.5, length=6.0)
    tr.drone("drone", 14.5 * bar, 12.0 * bar, "A#0", 0.26, fade=8.0, beat=0.17)
    # B (bars 15-24): the same chords, now hanging over A#; the sun's choir, the tree groaning.
    for i, ch in enumerate(a_chords[:8]):
        at = (15 + i) * bar
        swell = 0.8 + 0.2 * np.sin(np.pi * i / 7)
        tr.chord("full", at, bar, ch, 0.27 * swell, bright=3000, attack=1.4, release=3.5, spread=0.1)
        tr.chord("shimmer", at + 0.4, bar, ch[-2:], 0.22 * swell, bright=5000, attack=2.2, release=3.5, pitch=2.0)
        tr.pad("bass", at, bar, "A#1", 0.55, bright=380, attack=1.0, release=2.5)
    _m_choir(tr, "choir", 16 * bar, 4.5 * bar, "E5", 0.32, "a", pan=-0.2)
    _m_choir(tr, "choir", 18.5 * bar, 3.5 * bar, "B4", 0.22, "o", pan=0.3)
    _m_choir(tr, "choir", 20.5 * bar, 3.0 * bar, "F5", 0.30, "a", pan=0.1)
    tr.boom("fx", 18.0 * bar, 0.55)
    tr.creak("fx", 19.2 * bar, 0.22, pan=-0.5)
    tr.boom("fx", 22.4 * bar, 0.45)
    tr.creak("fx", 23.0 * bar, 0.2, pan=0.6)
    # C (bars 23-28): thinning; the light figure again, slowed, far away and out of tune.
    tr.drone("drone", 23.0 * bar, 9.5 * bar, "E1", 0.18, fade=8.0)
    for j, note in enumerate(light[:6]):
        tr.bell("far", 24 * bar + j * 0.9, note, 0.3, decay=3.0, pitch=0.985 - 0.005 * j, pan=0.4)
    tr.pad("soft", 25 * bar, 3 * bar, "A#3", 0.16, bright=900, attack=3.0, release=5.0)
    tr.swell("fx", 28 * bar, "F3", 0.35, length=5.0)
    # Outro (bars 28-32): E and F together, never resolving; one last high note.
    for note, vel in (("E3", 0.14), ("B3", 0.10), ("F3", 0.09)):
        tr.pad("soft", 28 * bar + tr.rng.uniform(0, 1.2), 4.5 * bar, note, vel, bright=1000, attack=4.5, release=6.0)
    tr.bell("bell", 30.5 * bar, "B6", 0.12, decay=3.0, pan=0.5)
    tr.noise_bed("wind", 180, 2600, [(0, 0.0), (8, 0.03), (60, 0.02), (75, 0.035), (110, 0.025),
                                     (130, 0.045), (150, 0.03), (160, 0.02)], drift_amt=0.5)
    tr.noise_bed("room", 25, 120, [(0, 0.0), (10, 0.018), (160, 0.018)], drift_amt=0.2)
    for name in ("soft", "far"):
        l, r = tr.buses[name]
        g = (lambda f: G.lp_gain(f, 1700, 2)) if name == "soft" else (lambda f: G.bp_gain(f, 300, 1500, 2))
        tr.buses[name] = (G.fft_filter(l, g), G.fft_filter(r, g))
    L, R = tr.master(mix={"soft": 1.0, "full": 1.0, "shimmer": 0.45, "bass": 0.7, "bell": 0.5, "bird": 0.4,
                          "drone": 0.55, "fx": 0.6, "far": 0.35, "choir": 0.55, "wind": 1.0, "room": 1.0},
                     reverb={"soft": 0.55, "full": 0.35, "shimmer": 0.85, "bass": 0.1, "bell": 0.75, "bird": 1.0,
                             "far": 1.0, "fx": 0.4, "drone": 0.1, "choir": 0.8},
                     room_s=7.0, room_lp=4200)
    G._m_save(L, R, G.SND / "music" / "megaflora.ogg", fade_out=7.0)


# =============================================================================
# Glimpse textures: what a bubble's surface reflects
# =============================================================================

def _glimpse(rng, sky_top, sky_bottom, ground_top, ground_bottom, horizon=0.55):
    """A soft panorama 128 x 64: a vertical gradient for the 'up' half and one for the 'down' half."""
    h, w = 64, 128
    y = np.linspace(0, 1, h)[:, None] * np.ones((1, w))
    top = np.array(sky_top, float)
    bot = np.array(sky_bottom, float)
    gt = np.array(ground_top, float)
    gb = np.array(ground_bottom, float)
    up = np.clip(y / horizon, 0, 1)[..., None]
    down = np.clip((y - horizon) / (1 - horizon), 0, 1)[..., None]
    img = np.where((y < horizon)[..., None], top + (bot - top) * up, gt + (gb - gt) * down)
    return img


def _rects(img, rng, count, color, wmin, wmax, hmin, hmax, ymin, ymax, soft=1.2):
    """Adds soft glowing rectangles (lamps, windows) to img."""
    h, w, _ = img.shape
    layer = np.zeros((h, w))
    for _ in range(count):
        x0 = int(rng.integers(0, w))
        y0 = int(rng.integers(int(ymin * h), int(ymax * h)))
        rw = int(rng.integers(wmin, wmax + 1))
        rh = int(rng.integers(hmin, hmax + 1))
        for dx in range(rw):
            layer[y0:y0 + rh, (x0 + dx) % w] = 1.0
    layer = G.blur(layer, soft)
    return img + layer[..., None] * (np.array(color, float) - img) * np.clip(layer[..., None] * 1.5, 0, 1)


def _save_glimpse(img, name):
    img = np.clip(img, 0, 255)
    # A bubble's reflection is never sharp: blur it like a curved, rushing surface.
    for c in range(3):
        img[..., c] = G.blur(img[..., c], 1.4)
    alpha = np.full(img.shape[:2] + (1,), 255.0)
    G.save_png(np.concatenate([img, alpha], axis=-1), G.TEX / "entity" / "void_bubble" / f"{name}.png")


def tex_l227atmos_bubble_glimpses() -> None:
    """One picture per level a bubble can mirror. Each is the level's light and colour more than
    its shapes: a yellow hall with buzzing panels, a tiled pool, a night street, a cream sunlit
    pool, a foggy town ringed by lit windows, green hills under a painted sky, and so on."""
    rng = G.rng_for(22780)
    # Level 0: mustard walls, bright ceiling panels, brown carpet.
    img = _glimpse(rng, (196, 180, 98), (214, 196, 110), (150, 130, 70), (96, 80, 44))
    img = _rects(img, rng, 9, (255, 250, 225), 6, 10, 2, 3, 0.05, 0.2)
    for x in range(0, 128, 9):
        img[:35, x] = img[:35, x] * 0.92
    _save_glimpse(img, "level_0")
    # Level 1: grey concrete garage, cold tubes, dark gaps.
    img = _glimpse(rng, (70, 72, 78), (110, 110, 116), (90, 90, 94), (40, 40, 44))
    img = _rects(img, rng, 7, (235, 238, 250), 10, 18, 1, 2, 0.1, 0.25)
    img = _rects(img, rng, 6, (30, 30, 34), 4, 6, 20, 30, 0.3, 0.55)
    _save_glimpse(img, "level_1")
    # Level 2: dark warm pipe corridor, amber lamps, pipe lines.
    img = _glimpse(rng, (34, 26, 20), (70, 52, 36), (60, 46, 32), (24, 18, 14))
    for y in (8, 12, 16, 46, 50):
        img[y:y + 2] = img[y:y + 2] * 0.55 + np.array([90, 70, 50]) * 0.45
    img = _rects(img, rng, 8, (255, 196, 120), 2, 3, 2, 3, 0.25, 0.6, soft=1.6)
    _save_glimpse(img, "level_2")
    # Level 4: cold office at night, window grid, dark carpet.
    img = _glimpse(rng, (54, 60, 74), (84, 92, 108), (70, 74, 84), (34, 36, 44))
    img = _rects(img, rng, 12, (150, 170, 205), 5, 8, 6, 9, 0.2, 0.45)
    _save_glimpse(img, "level_4")
    # Poolrooms: white-blue tile and turquoise water.
    img = _glimpse(rng, (200, 214, 222), (226, 236, 240), (90, 196, 206), (40, 140, 160))
    img[:35, ::6] *= 0.88
    img[::5, :] *= 0.94
    _save_glimpse(img, "poolrooms")
    # Level 37: cream tiles, warm overexposed light, bright arches.
    img = _glimpse(rng, (246, 236, 210), (240, 226, 196), (220, 214, 196), (170, 196, 196))
    img = _rects(img, rng, 5, (255, 252, 240), 10, 16, 14, 22, 0.1, 0.4, soft=2.0)
    _save_glimpse(img, "level_37")
    # Level 9: midnight street, dark blue, house shapes, orange lamps.
    img = _glimpse(rng, (8, 12, 26), (22, 28, 46), (30, 30, 34), (12, 12, 16))
    img = _rects(img, rng, 6, (14, 14, 22), 14, 22, 8, 12, 0.35, 0.5)
    img = _rects(img, rng, 7, (255, 150, 60), 2, 2, 2, 2, 0.32, 0.5, soft=1.8)
    _save_glimpse(img, "level_9")
    # Level 94: painted blue sky, white clouds, bright green hills.
    img = _glimpse(rng, (110, 170, 240), (190, 220, 250), (110, 200, 80), (60, 150, 50), horizon=0.5)
    img = _rects(img, rng, 5, (250, 252, 255), 10, 18, 3, 5, 0.05, 0.3, soft=2.2)
    xs = np.arange(128)
    hill = (34 + 4 * np.sin(xs / 128 * 2 * np.pi * 3) + 2 * np.sin(xs / 128 * 2 * np.pi * 7)).astype(int)
    for x in xs:
        img[hill[x]:32, x] = (120, 205, 90)
    _save_glimpse(img, "level_94")
    # Level 7: concrete sky, black sea.
    img = _glimpse(rng, (62, 64, 66), (90, 92, 94), (16, 24, 28), (4, 6, 8), horizon=0.5)
    _save_glimpse(img, "level_7")
    # Level 39: blue-grey fog and a wall of lit windows all round.
    img = _glimpse(rng, (70, 78, 92), (120, 128, 140), (60, 66, 76), (28, 30, 36))
    img = _rects(img, rng, 60, (225, 232, 245), 1, 2, 2, 3, 0.05, 0.45, soft=0.8)
    _save_glimpse(img, "level_39")


TEXTURES = [tex_l227atmos_bubble_glimpses]
SOUNDS = [
    snd_l227atmos_forest, snd_l227atmos_void_wind, snd_l227atmos_sun_voice, snd_l227atmos_beam_hum,
    snd_l227atmos_bubble_hum,
    snd_l227atmos_lurch, snd_l227atmos_unstable, snd_l227atmos_drag, snd_l227atmos_sun_burn,
    snd_l227atmos_disintegrate, snd_l227atmos_sun_whisper, snd_l227atmos_bubble_enter,
    snd_l227atmos_distant_tree_groan, snd_l227atmos_distant_branch_crack, snd_l227atmos_distant_wrong_bird,
    snd_l227atmos_distant_animal_call, snd_l227atmos_distant_leaf_fall, snd_l227atmos_distant_void_howl,
    snd_l227atmos_music_megaflora,
]
