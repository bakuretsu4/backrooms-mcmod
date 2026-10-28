"""Level 0's sound: the room tone, each tube's own buzz, the faulty tubes' ticks, and the rare
far-off sounds of an empty office building at night.

Played by:
  ambient.level0_hum (the level biome's ambient loop) <- ambient/level0_hum
  client/light/FixtureSounds (positional, per tube)   <- level0/tube_hum*, tube_hum_faulty*, tube_tick*
  client/audio/DistantAmbience (Level 0 schedule)      <- ambient/distant/level0/{settle_creak,
                                                          door_click,relay,vent_tick}1..3

  ambient/level0_hum        93 s seamless loop. The real room-tone recording (tools/vendor/freesound,
                            626096) taken apart: its 60 Hz mains hum and harmonics are re-synthesised
                            from the measured peaks, each partial slowly swelling on its own, a few
                            beating gently against a detuned twin (other tubes on the same circuit);
                            its air is spread over the 93 s in long cross-faded grains, so the 20 s
                            recording never repeats audibly. Under it, very faint distant HVAC air
                            (a duct breathing, surging slowly) and a thin, faint ballast whine.
  level0/tube_hum1..4       one tube's buzz (the real ballast recording 638895), four characters:
                            as recorded, a harsher old magnetic ballast, a deeper duller one, one
                            with a faint coil whine. Loops of 12.7, 11.3, 13.9 and 10.1 s, so tubes
                            near each other never line up. Each tube picks one by its position
                            (and its own pitch), see FixtureHumSound.
  level0/tube_hum_faulty1..2  a failing tube: the same buzz, but unsteady, with the lens rattling in
                            bursts and faint arcing crackle. Loops of 9.1 and 10.7 s.
  level0/tube_tick1..5      a glow starter's bimetal contact ticking (sometimes bouncing, once with a
                            short buzz as the tube almost strikes).
  distant settle_creak      the building settling: a slow low creak in a stud wall, a tick after.
  distant door_click        a door somewhere: latch, a soft close against the frame.
  distant relay             a relay or contactor clacking in a far electrical panel, a short hum swell.
  distant vent_tick         sheet-metal ducting ticking as it cools, hollow.

Loudness is set in LUFS (EBU R128), measured with the same meter as level2_audio (lufs/loud):
the room tone file at -24 (its sounds.json volume 0.36 brings it to about -33, under the README
"Mix" -30 so the spaces between lit areas feel near-silent), tube hums -27 like the
recorded lamp hum (the faulty one -28), starter ticks -35, the distant one-shots -29 to -32
(fainter than the older Level 0 set at -22 to -28: they are meant to be barely caught).

Run only these with:  python tools/gen_assets.py snd_l0audio_
Every generator uses its own fixed random seed (40100-40199), so re-running gives identical files.
"""
from __future__ import annotations

import numpy as np

import gen_assets as G
import anomaly_assets.level2_audio as L2  # the LUFS meter: L2.lufs, L2.loud
import level_assets.recorded as R  # the vendored recordings: R._rec, R._clean, R._loop

SR = G.SR
DIST = G.SND / "ambient" / "distant" / "level0"


def _save(x, path, target: float, peak: float = 0.9, loop: bool = False,
          fade_in: float = 0.002, fade_out: float = 0.05, compression: float = 0.15) -> None:
    """Brings `x` to `target` LUFS and writes it as OGG at that level (no normalising). Written
    in one-second blocks (libsndfile's Vorbis encoder overflows on long clips). `compression`
    (0 = best) is used for loops only."""
    import soundfile as sf
    x = np.asarray(x, float)
    x = x - x.mean()
    if not loop:
        n = len(x)
        fi, fo = max(1, int(fade_in * SR)), max(1, int(fade_out * SR))
        x[:fi] *= np.linspace(0, 1, fi)
        x[n - fo:] *= np.linspace(1, 0, fo)
    x = L2.loud(x, target, peak)
    path.parent.mkdir(parents=True, exist_ok=True)
    # Loops get a higher Vorbis quality: otherwise the codec's low-frequency error leaves a small
    # step where the end meets the start.
    extra = {"compression_level": compression} if loop else {}
    with sf.SoundFile(str(path), "w", SR, 1, format="OGG", subtype="VORBIS", **extra) as out:
        y = x.astype(np.float32)
        for i in range(0, len(y), SR):
            out.write(y[i:i + SR])


def _whole(f: float, T: float) -> float:
    """A frequency rounded to a whole number of cycles over T seconds (keeps loops seamless)."""
    return max(1.0, round(f * T)) / T


def _slow(n: int, rng, amount: float, lo: float, hi: float) -> np.ndarray:
    """A slow, seamless gain wobble 1 +/- amount with energy only between lo..hi Hz (like G.drift,
    but drawn at 100 points a second and stretched, so a 93 s loop needs no giant FFTs)."""
    m = max(64, int(n / SR * 100))
    f = np.fft.rfftfreq(m, 1 / 100)
    spec = (rng.normal(size=f.size) + 1j * rng.normal(size=f.size)) * ((f >= lo) & (f <= hi))
    spec[0] = 0
    d = np.fft.irfft(spec, m)
    d = d / (np.abs(d).max() + 1e-12)
    pos = np.arange(n) * (m / n)
    return 1 + amount * np.interp(pos, np.arange(m + 1), np.append(d, d[0]))


def _tonal_peaks(x: np.ndarray, lo: float = 30.0, hi: float = 6000.0, over_db: float = 14.0):
    """The steady tones in a recording: [(Hz, amplitude)] for every narrow peak standing at least
    `over_db` above the noise around it. Amplitude from the energy within +-0.6 Hz (Parseval), so
    a hum that wanders a little in pitch is still measured whole."""
    n = len(x)
    X = np.fft.rfft(x)
    p = np.abs(X) ** 2
    f = np.fft.rfftfreq(n, 1 / SR)
    df = f[1]
    half = max(2, int(round(0.6 / df)))
    db = 10 * np.log10(p + 1e-30)
    out = []
    i = int(lo / df)
    top = int(hi / df)
    while i < top:
        w = db[i - half:i + half + 1]
        if db[i] == w.max():
            span = max(half * 4, int(i * 0.05))
            floor = np.median(db[max(1, i - span):i + span])
            if db[i] - floor > over_db:
                e = p[i - half:i + half + 1].sum()
                out.append((float(f[i]), 2.0 * np.sqrt(e) / n))
                i += half
        i += 1
    return out


def _remove_tones(x: np.ndarray, peaks) -> np.ndarray:
    """The recording with its steady tones cut out (each peak's bins pulled down to the noise
    around it), leaving the room's air."""
    n = len(x)
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    df = f[1]
    half = max(3, int(round(0.9 / df)))
    mag = np.abs(X)
    for fp, _ in peaks:
        i = int(round(fp / df))
        a, b = max(1, i - half), min(len(X), i + half + 1)
        ring_ = np.concatenate([mag[max(1, a - 4 * half):a], mag[b:b + 4 * half]])
        floor = np.median(ring_) if ring_.size else 0.0
        seg = X[a:b]
        X[a:b] = seg / (np.abs(seg) + 1e-30) * np.minimum(np.abs(seg), floor)
    return np.fft.irfft(X, n)


def _grains(src: np.ndarray, n: int, rng, grain: float = 6.0) -> np.ndarray:
    """Spreads a short noisy recording over n samples (a whole number of grain/2 hops) in long
    sine-windowed grains from random places in it, half of them played backwards. Neighbouring
    windows' squares add up to one, so the air's level never dips; the result loops seamlessly."""
    L = int(grain * SR)
    hop = L // 2
    assert n % hop == 0, (n, hop)
    w = np.sin(np.pi * (np.arange(L) + 0.5) / L)
    # Skip spots with a bump in them (a handled mic, a door): 100 ms levels above median + 6 dB.
    blk = SR // 10
    lev = np.array([np.sqrt(np.mean(src[i:i + blk] ** 2)) for i in range(0, len(src) - blk, blk)])
    loud = 20 * np.log10(lev + 1e-12) > 20 * np.log10(np.median(lev) + 1e-12) + 6.0
    out = np.zeros(n)
    for g in range(n // hop):
        for _ in range(40):
            s = int(rng.integers(0, len(src) - L))
            if not loud[s // blk:(s + L) // blk].any():
                break
        seg = src[s:s + L]
        if rng.uniform() < 0.5:
            seg = seg[::-1]
        G.add_at(out, seg * w, g * hop / SR, True)
    return out


def _gain_to(x: np.ndarray, lu: float) -> np.ndarray:
    """Scales x to `lu` LUFS (for setting a mix by loudness rather than by peak)."""
    return x * 10 ** ((lu - L2.lufs(x)) / 20)


# =============================================================================
# The room tone
# =============================================================================

def snd_l0audio_room_tone() -> None:
    """93 s seamless loop: Level 0's room tone (see the module doc). Mix by loudness: the hum
    re-built from the recording 0 LU, the recording's air -3, distant HVAC air -9, the ballast
    whine -24 (just there if you listen for it)."""
    rng = G.rng_for(40101)
    T = 93.0
    n = int(T * SR)
    t = np.arange(n) / SR
    rec = R._clean(R._rec("626096_backrooms_ambience"), lo=30, hi=12000)

    # 1. The mains hum, re-synthesised from the recording's measured tones. Each partial swells
    #    and fades on its own slow rhythm (periods of 15-90 s), and the strongest few beat gently
    #    against a slightly detuned twin, like neighbouring ballasts drifting in and out of step.
    peaks = _tonal_peaks(rec)
    strongest = sorted(peaks, key=lambda p: -p[1])[:5]
    hum = np.zeros(n)
    for fp, a in peaks:
        fw = _whole(fp, T)
        ph = rng.uniform(0, 2 * np.pi)
        part = np.sin(2 * np.pi * fw * t + ph) * _slow(n, rng, rng.uniform(0.12, 0.3), 0.011, 0.065)
        if (fp, a) in strongest:
            k = int(rng.integers(1, 4)) * (1 if rng.uniform() < 0.5 else -1)
            part += rng.uniform(0.2, 0.35) * np.sin(2 * np.pi * (fw + k / T) * t + rng.uniform(0, 2 * np.pi))
        hum += a * part

    # 2. The recording's air, its tones cut out, spread over the loop so it never repeats.
    air = _grains(_remove_tones(rec, peaks), n, rng)

    # 3. Distant HVAC: soft, dark air through a far duct (pink, 35-700 Hz) with a faint low duct
    #    rumble, surging slowly (one long swell and a few smaller ones per loop).
    hvac = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 35, 700, 2) / np.sqrt(np.maximum(f, 20)))
    hvac += 0.5 * G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 22, 70, 2))
    hvac *= _slow(n, rng, 0.35, 0.0105, 0.04)

    # 4. The ballast whine: two thin high tones, barely there, wavering slowly in level.
    whine = np.zeros(n)
    for fw, a in ((_whole(7920.0, T), 1.0), (_whole(11880.0, T), 0.45)):
        whine += a * np.sin(2 * np.pi * fw * t + rng.uniform(0, 2 * np.pi)) * _slow(n, rng, 0.5, 0.02, 0.12)

    x = (_gain_to(hum, -24.0) + _gain_to(air, -27.0) + _gain_to(hvac, -33.0) + _gain_to(whine, -48.0))
    # The whole bed breathes a little (about +-0.7 dB over a minute or so).
    x *= _slow(n, rng, 0.08, 0.0105, 0.03)
    # A long loop: a little more compression keeps the file near 1 MB (it streams).
    _save(x, G.SND / "ambient" / "level0_hum.ogg", -24.0, peak=0.7, loop=True, compression=0.4)


# =============================================================================
# One tube's buzz
# =============================================================================

def _tube_source(offset: float, length: float) -> np.ndarray:
    """A seamless loop of the real ballast recording, `length` seconds from `offset` on."""
    x = R._clean(R._rec("638895_fluorescent_hum"), lo=70)
    return R._loop(x[int(offset * SR):], length, 0.6)


def snd_l0audio_tube_hum() -> None:
    """Four tube buzzes, each its own loop length so neighbouring tubes never line up."""
    rng = G.rng_for(40111)
    # 1: as recorded.
    x = _tube_source(1.0, 12.7)
    _save(x, G.SND / "level0" / "tube_hum1.ogg", -27.0, loop=True)

    # 2: an older magnetic ballast: harsher, with more of the buzzy upper harmonics (a soft
    #    clip of the hum itself, so the extra harmonics stay locked to it).
    x = _tube_source(2.0, 11.3)
    y = np.tanh(4.0 * x / np.abs(x).max())
    y = G.fft_filter(y, lambda f: G.hp_gain(f, 400, 2) * G.lp_gain(f, 5000, 2), circular=True)
    _save(G.norm(x) + 0.3 * G.norm(y), G.SND / "level0" / "tube_hum2.ogg", -27.0, loop=True)

    # 3: deeper and duller (a tube behind a thick diffuser): highs rolled off, a little more
    #    120/240 Hz body, swelling slowly.
    T = 13.9
    x = _tube_source(0.3, T)
    n = len(x)
    t = np.arange(n) / SR
    x = G.fft_filter(x, lambda f: G.lp_gain(f, 1600, 2), circular=True)
    body = (np.sin(2 * np.pi * _whole(120.0, T) * t) + 0.6 * np.sin(2 * np.pi * _whole(240.0, T) * t + 0.7))
    body *= _slow(n, rng, 0.2, 0.15, 0.6)
    _save(G.norm(x) + 0.12 * G.norm(body), G.SND / "level0" / "tube_hum3.ogg", -27.0, loop=True)

    # 4: with a faint coil whine riding on it, wavering in level.
    T = 10.1
    x = _tube_source(4.0, T)
    n = len(x)
    t = np.arange(n) / SR
    whine = np.sin(2 * np.pi * _whole(6240.0, T) * t) * _slow(n, rng, 0.6, 0.2, 0.8)
    x = _gain_to(x, -27.0) + _gain_to(whine, -45.0)
    _save(x, G.SND / "level0" / "tube_hum4.ogg", -27.0, loop=True)


def snd_l0audio_tube_hum_faulty() -> None:
    """Two failing tubes' buzz: the ballast hum sagging and catching unsteadily, the lens rattling
    in bursts, and the faint dry crackle of a tube arcing at its ends."""
    for i, (seed, offset, T) in enumerate(((40121, 0.8, 9.1), (40122, 3.4, 10.7)), 1):
        rng = G.rng_for(seed)
        base = _tube_source(offset, T)
        n = len(base)
        t = np.arange(n) / SR
        # The hum struggles: a quick, irregular wobble (1.5-6 Hz) in level.
        base = G.norm(base) * _slow(n, rng, 0.22, 1.5, 6.0)
        # The rattle: a hard, square-ish 120 Hz buzz (the loose lens or the ballast's laminations)
        # that only sounds in bursts.
        s = np.sin(2 * np.pi * _whole(120.0, T) * t)
        rattle = G.fft_filter(np.sign(s) * np.abs(s) ** 0.2, lambda f: G.bp_gain(f, 300, 3000, 2), circular=True)
        gate = np.clip(_slow(n, rng, 1.0, 0.3, 2.5) - 1.25, 0.0, None)
        rattle = G.norm(rattle) * gate / (gate.max() + 1e-12)
        # The arcing: sparse, tiny dry crackles (about two a second, mostly very soft).
        crackle = np.zeros(n)
        tt = rng.uniform(0, 0.4)
        while tt < T:
            m = int(rng.uniform(0.004, 0.03) * SR)
            burst = rng.normal(size=m) * np.exp(-np.arange(m) / (m / 3))
            G.add_at(crackle, burst * rng.uniform(0.15, 1.0) ** 2, tt, True)
            tt += rng.exponential(0.5)
        crackle = G.fft_filter(crackle, lambda f: G.bp_gain(f, 1500, 7000, 2), circular=True)
        x = base + 0.3 * rattle + 0.18 * G.norm(crackle)
        _save(x, G.SND / "level0" / f"tube_hum_faulty{i}.ogg", -28.0, loop=True)


def snd_l0audio_tube_tick() -> None:
    """Five glow-starter ticks: the bimetal contact snapping shut (a tiny dry click, a glint of
    metal and the thin knock of the fixture housing). 2 and 4 bounce; 5 is followed by a short
    buzz as the tube almost strikes."""
    for i in range(1, 6):
        rng = G.rng_for(40130 + i)
        x = np.zeros(int(0.3 * SR))

        def tick(at, amp):
            m = int(0.04 * SR)
            k = rng.normal(size=m) * np.exp(-np.arange(m) / (0.0006 * SR))
            k = G.fft_filter(k, lambda f: G.hp_gain(f, 1500, 2))
            glint = G.ring([rng.uniform(4200, 6800)], [rng.uniform(0.002, 0.004)], [1.0], 0.04)
            housing = G.ring([rng.uniform(280, 420), rng.uniform(900, 1300)], [0.012, 0.006], [1.0, 0.5], 0.04)
            G.add_at(x, amp * (G.norm(k) + 0.35 * G.norm(glint) + 0.3 * G.norm(housing)), at, False)

        tick(0.005, 1.0)
        if i in (2, 4):
            tick(0.005 + rng.uniform(0.009, 0.022), rng.uniform(0.35, 0.55))
        if i == 5:
            m = int(0.05 * SR)
            hum, rasp = G._mains_buzz(2 * np.pi * 60 * np.arange(m) / SR)
            G.add_at(x, 0.25 * (hum + 0.5 * rasp) * G.env_adsr(m, 0.004, 0.02), rng.uniform(0.03, 0.05), False)
        x = G.fft_filter(x, lambda f: G.hp_gain(f, 150, 2) * G.lp_gain(f, 9000, 2))
        _save(x, G.SND / "level0" / f"tube_tick{i}.ogg", -35.0, peak=0.5, fade_out=0.08)


# =============================================================================
# Far-off one-shots (DistantAmbience, Level 0 schedule)
# =============================================================================

def snd_l0audio_distant() -> None:
    """The rare far sounds of an empty building (see the module doc): settle creak, door click,
    relay, vent tick, three of each. All heard through walls: low-passed, a short dull tail."""
    for i in range(1, 4):
        # The building settling: a slow creak in a stud wall (stick-slip catches exciting the
        # wall's low resonances), dying away; one or two small ticks after it.
        rng = G.rng_for(40140 + i)
        dur = 2.6
        x = np.zeros(int(dur * SR))
        cd = rng.uniform(0.8, 1.5)
        creak = G._da_stickslip(rng, cd, [(0, rng.uniform(5, 9)), (cd * 0.5, rng.uniform(10, 16)), (cd, rng.uniform(4, 7))],
                                [rng.uniform(110, 170), rng.uniform(260, 380), rng.uniform(600, 800)],
                                [0.03, 0.02, 0.008], [1.0, 0.6, 0.25])
        creak = G.norm(creak) * G._da_env(len(creak), [(0, 0), (0.15, 1), (cd * 0.6, 0.7), (cd, 0)])
        G.add_at(x, creak, 0.05, False)
        tt = 0.05 + cd + rng.uniform(0.2, 0.5)
        for _ in range(1 + (i == 2)):
            G.add_at(x, 0.35 * G.norm(G._da_hit(rng, [rng.uniform(500, 700), rng.uniform(1300, 1700)], [0.01, 0.005],
                                                 [1, 0.4], noise_ms=0.6, noise_lp=3000)), tt, False)
            tt += rng.uniform(0.25, 0.6)
        _save(G._da_far(x, rng, rng.uniform(900, 1300), hp=60, air=0.15, room=0.45), DIST / f"settle_creak{i}.ogg",
              -30.0, fade_out=0.15)

        # A door somewhere: the latch tongue slipping back (a small metal click), then the door
        # meeting the frame (a soft wooden thud with the latch snapping in). 3 is only the latch.
        rng = G.rng_for(40150 + i)
        x = np.zeros(int(1.0 * SR))
        G.add_at(x, 0.5 * G.norm(G._da_hit(rng, [rng.uniform(2200, 3200), rng.uniform(4200, 5200)], [0.004, 0.002],
                                            [1, 0.5], noise_ms=0.5, noise_lp=6000)), 0.02, False)
        if i != 3:
            at = 0.02 + rng.uniform(0.18, 0.4)
            thud = G._da_hit(rng, [rng.uniform(85, 130), rng.uniform(230, 340)], [0.06, 0.025], [1, 0.5],
                             noise_ms=4.0, noise_lp=1200)
            G.add_at(x, G.norm(thud), at, False)
            G.add_at(x, 0.45 * G.norm(G._da_hit(rng, [rng.uniform(1800, 2600)], [0.006], [1], noise_ms=0.5,
                                                 noise_lp=5000)), at + 0.004, False)
        _save(G._da_far(x, rng, rng.uniform(1400, 2000), hp=70, air=0.2, room=0.6), DIST / f"door_click{i}.ogg",
              -31.0, fade_out=0.15)

        # A relay or contactor in a far electrical panel: a sharp clack (armature, a bounce),
        # then the transformer's hum swelling for a moment as the load comes on (2 and 3) or
        # dropping away (1).
        rng = G.rng_for(40160 + i)
        x = np.zeros(int(1.4 * SR))
        clack = G._da_hit(rng, [rng.uniform(1700, 2600), rng.uniform(3600, 4600), rng.uniform(380, 520)],
                          [0.006, 0.003, 0.015], [1, 0.5, 0.6], noise_ms=0.8, noise_lp=7000)
        G.add_at(x, G.norm(clack), 0.02, False)
        G.add_at(x, 0.4 * G.norm(clack), 0.02 + rng.uniform(0.003, 0.007), False)
        m = int(rng.uniform(0.35, 0.7) * SR)
        hum, rasp = G._mains_buzz(2 * np.pi * 60 * np.arange(m) / SR)
        env = np.exp(-np.arange(m) / (0.12 * SR)) if i == 1 else G._da_env(m, [(0, 0), (0.06, 1), (m / SR, 0)])
        G.add_at(x, 0.3 * (hum + 0.3 * rasp) * env, 0.03, False)
        _save(G._da_far(x, rng, rng.uniform(1800, 2600), hp=80, air=0.15, room=0.4), DIST / f"relay{i}.ogg",
              -31.0, fade_out=0.15)

        # Sheet-metal ducting ticking as it cools: a few small metallic pings at uneven gaps,
        # the first with a hollow boom of the duct behind it.
        rng = G.rng_for(40170 + i)
        dur = 2.8
        x = np.zeros(int(dur * SR))
        tt = 0.03
        f0 = rng.uniform(600, 900)
        for k in range(1 + i):
            ping = G.ring([f0 * r * rng.uniform(0.97, 1.03) for r in (1.0, 1.58, 2.31, 3.1)],
                          [rng.uniform(0.03, 0.07), 0.03, 0.02, 0.012], [1.0, 0.6, 0.4, 0.25], 0.35)
            ping[:20] += rng.normal(size=20) * 0.4
            if k == 0:
                boom = G.ring([rng.uniform(70, 110), rng.uniform(160, 220)], [0.12, 0.06], [1, 0.4], 0.6)
                G.add_at(x, 0.5 * G.norm(boom), tt, False)
            G.add_at(x, G.norm(ping) * rng.uniform(0.5, 1.0), tt, False)
            tt += rng.uniform(0.3, 0.9)
            if tt > dur - 0.5:
                break
        _save(G._da_far(x, rng, rng.uniform(2200, 3000), hp=60, air=0.25, room=0.5), DIST / f"vent_tick{i}.ogg",
              -32.0, fade_out=0.15)


TEXTURES: list = []
SOUNDS = [snd_l0audio_room_tone, snd_l0audio_tube_hum, snd_l0audio_tube_hum_faulty, snd_l0audio_tube_tick,
          snd_l0audio_distant]
