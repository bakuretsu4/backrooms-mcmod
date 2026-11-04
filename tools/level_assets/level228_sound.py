"""Level 228 v2 ("Hall of Mirrors", an abandoned carnival mirror maze): every sound of the level.
Everything is synthesised from scratch in code (no recordings, nothing sampled or copied).

The level is meant to be close to silent: a dead funfair building, its bulbs mostly gone, its
mirrors settling in their frames. Nothing here has a voice. Played by client/level228/
Level228Ambience (loops, creaks, glass, the organ, the footstep echo), client/light/FixtureSounds
(the bulbs), player/PlayerGameplay via world/Level228Footsteps (the floors) and client/hint/
ExitHints (the exit hint).

Sounds (sounds/level228/ unless noted):
  ambience             40 s loop  the building's room tone ("in your head"): a big closed timber
                                  shed at night, a faint roof wind high above, nothing else
  bulb_buzz             4 s loop  a live bare bulb's filament singing at the mains hum (positional,
                                  heard only right under it)
  bulb_fail_1-5                   a failing bulb's contact chattering as it flickers
  creak_1-6                       the timber building settling somewhere (beams, floor joists)
  glass_tick_1-5                  a big mirror pane ticking as it settles in its wooden frame
  glass_creak_1-4                 a pane creaking against its frame
  step_boards_1-6                 footsteps on worn old floorboards (foyer, Crystal Maze, chamber)
  step_tile_1-6                   footsteps on faded patterned tile (Alhambra Hall)
  step_lino_1-6                   footsteps on peeling checker linoleum (Funhouse)
  step_plywood_1-6                footsteps on plywood over joists (backstage passages)
  organ_1-3          ~20-30 s     the carnival's band organ, far away through the walls, warped
                                  and out of tune: a waltz, its trio, and a phrase that runs down
  exit_wind            30 s loop  the exit hallway: night wind at the door gaps, leaves skittering,
                                  a loose door rattling in its frame ("in your head")
  chamber_hush         20 s loop  the final chamber: the pressure of total silence, felt more
                                  than heard
  exit_hint/level_228   8 s loop  the honest exit hint: cold outside air drawn through a gap
  music/level228      ~1.6 min    "Carousel Under Glass": a band-organ waltz remembered through a
                                  music box, played back on a dying tape
  music/level228_silvering        "Silvering" (the v1 piece, kept as the level's second track)

Run only these with:  python tools/gen_assets.py l228
Every sound has its own fixed seed, so re-running gives identical files.
"""
from __future__ import annotations

import numpy as np

import gen_assets as G
from anomaly_assets import music_c as C

SR = G.SR
OUT = G.SND / "level228"


# =============================================================================
# Shared helpers
# =============================================================================

def _loop_tone(freq: float, T: float, t: np.ndarray, phase: float = 0.0) -> np.ndarray:
    """A sine snapped to a whole number of cycles per loop (so the loop seam is continuous)."""
    return np.sin(2 * np.pi * round(freq * T) / T * t + phase)


def _band(x: np.ndarray, lo: float, hi: float, order: int = 2, circular: bool = False) -> np.ndarray:
    return G.fft_filter(x, lambda f: G.bp_gain(f, lo, hi, order), circular=circular)


def _modes(rng, n: int, freqs, decays, amps, spread: float = 0.01) -> np.ndarray:
    """A struck body: a few damped sine modes, each a hair detuned (no two takes ring alike)."""
    t = np.arange(n) / SR
    x = np.zeros(n)
    for f, d, a in zip(freqs, decays, amps):
        f = f * rng.uniform(1 - spread, 1 + spread)
        x += a * np.sin(2 * np.pi * f * t + rng.uniform(0, 6.3)) * np.exp(-t / d)
    return x


def _burst(rng, n: int, decay: float, lo: float, hi: float) -> np.ndarray:
    """A short band-limited noise burst (the contact of a heel, a knuckle, a spark)."""
    t = np.arange(n) / SR
    return _band(rng.normal(size=n) * np.exp(-t / decay), lo, hi)


def _gust(n: int, T: float, rng, periods, depth: float) -> np.ndarray:
    """A slow periodic swell built from whole-cycle sines (loops seamlessly), 1 - depth .. 1."""
    t = np.arange(n) / SR
    s = np.zeros(n)
    for period in periods:
        cycles = max(1, round(T / period))
        s += np.sin(2 * np.pi * cycles * t / T + rng.uniform(0, 6.3))
    s = (s - s.min()) / (s.max() - s.min() + 1e-12)
    return 1.0 - depth + depth * s


def _varispeed(x: np.ndarray, ratio: np.ndarray) -> np.ndarray:
    """Tape varispeed: output sample i reads the input at sum(ratio[:i]); ratio < 1 plays slower
    and lower together, the way a tape (or an organ's failing bellows) runs down. `ratio` is per
    OUTPUT sample and must be long enough; the output ends where the input runs out."""
    pos = np.cumsum(ratio)
    end = int(np.searchsorted(pos, len(x) - 2))
    pos = pos[:end]
    return np.interp(pos, np.arange(len(x)), x)


def _far(rng, x: np.ndarray, lo: float, hi: float, room: float, wet: float) -> np.ndarray:
    """A sound heard from far off through timber walls: highs gone, the lows thinned, and much of
    it arriving as the big dark building's reverb rather than directly."""
    dry = _band(x, lo, hi, 2)
    ir = G.reverb_ir(rng, room, room / 3.2, lp=hi * 0.8, predelay=0.04)
    rev = G.conv(dry, ir)[: len(dry)]
    return (1 - wet) * G.norm(dry) + wet * G.norm(rev)


# =============================================================================
# Room tone, bulbs, the building, the glass
# =============================================================================

def snd_l228_ambience() -> None:
    """The room tone (40 s loop, "in your head"). A dead funfair attraction is a big timber shed:
    the air inside it is nearly still and very low (a soft rumble well under 120 Hz that breathes
    slowly), the night wind brushes the roof far overhead now and then (a faint mid-band rush that
    swells and sinks), and a breath of dusty high air. Nothing tonal, nothing that sings: the v1
    tone's 'singing glass' read as a hum the player kept searching for. The silence has to be
    quiet enough that every tick of a mirror and every creak of a beam stands out against it."""
    T = 40.0
    n = int(T * SR)
    rng = G.rng_for(22830)
    low = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 18, 110, 2) / np.sqrt(np.maximum(f, 15)))
    low = G.norm(low) * G.drift(n, rng, 0.22, 0.03, 0.12)
    roof = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 160, 900, 2) / np.maximum(f, 40) ** 0.7)
    roof = G.norm(roof) * _gust(n, T, rng, (20.0, 40.0 / 3.0, 8.0), 0.85) ** 2.2
    dust = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 2800, 8000, 2))
    x = 0.85 * low + 0.20 * roof + 0.018 * G.norm(dust)
    G.save_ogg(x, OUT / "ambience.ogg", peak=0.40, loop=True)


def snd_l228_bulb_buzz() -> None:
    """A live bare bulb (4 s loop). A tungsten filament on AC heats and cools 100 times a second
    and its coil flexes with the current, so an old bulb sings faintly at 100 Hz and its
    harmonics, with a thin sizzle where a loose turn of the coil chatters at the peaks. Barely
    there: you notice it right under a bulb, and the dark between bulbs is silent."""
    T = 4.0
    n = int(T * SR)
    t = np.arange(n) / SR
    rng = G.rng_for(22831)
    sing = sum(a * _loop_tone(100.0 * k, T, t, rng.uniform(0, 6.3))
               for k, a in ((1, 0.30), (2, 1.0), (3, 0.55), (4, 0.42), (5, 0.20), (7, 0.10), (9, 0.05)))
    # The chatter: narrow noise around 3 kHz gated by the 100 Hz peaks (|sin| to a high power).
    gate = np.abs(_loop_tone(50.0, T, t)) ** 8
    sizzle = G.periodic_noise(n, rng, lambda f: np.exp(-((f - 3100) / 700) ** 2)) * gate
    sizzle = G.fft_filter(sizzle, lambda f: G.bp_gain(f, 1500, 6500, 2), circular=True)
    x = G.norm(sing) + 0.18 * G.norm(sizzle)
    G.save_ogg(x * G.drift(n, rng, 0.08, 0.25, 1.0), OUT / "bulb_buzz.ogg", peak=0.45, loop=True)


def snd_l228_bulb_fail() -> None:
    """A failing bulb (five takes): its worn contact or broken filament touching and parting as it
    flickers. A short run of tiny arcs (dry crackles), the coil 'tinking' as it jerks, and a brief
    rise of the buzz while it holds. Played at the bulb when it drops out or catches again."""
    rng = G.rng_for(22832)
    for k in range(5):
        dur = rng.uniform(0.18, 0.45)
        n = int(dur * SR)
        t = np.arange(n) / SR
        x = np.zeros(n)
        for _ in range(rng.integers(3, 9)):
            at = rng.uniform(0.0, dur * 0.75)
            m = int(rng.uniform(0.002, 0.012) * SR)
            arc = rng.normal(size=m) * np.exp(-np.arange(m) / SR / rng.uniform(0.0008, 0.003))
            G.add_at(x, arc * rng.uniform(0.3, 1.0), at, circular=False)
        x = _band(x, 1800, 11000)
        tink = _modes(rng, n, [rng.uniform(4200, 5600), rng.uniform(7000, 8400)], [0.02, 0.012], [1.0, 0.5])
        tink *= np.clip((t - rng.uniform(0.0, 0.05)) / 0.0005, 0, 1)
        held = (_loop_tone(100, dur, t) * 0.6 + _loop_tone(200, dur, t)) * np.sin(np.pi * t / dur) ** 2
        y = G.norm(x) + 0.35 * G.norm(tink) + 0.25 * G.norm(held)
        G.save_ogg(y, OUT / f"bulb_fail_{k + 1}.ogg", peak=0.6, fade_out=0.03)


def snd_l228_creak() -> None:
    """The building settling (six takes): the timber frame of the funfair shed moving somewhere in
    the dark. A slow stick-slip groan of a beam in its joint (the catches 3-20 a second, ringing low
    wooden resonances), the shorter dry crack of a floor joist, or a creak that ends in a soft knock
    as something comes to rest. Dull and far: heard through walls, never close."""
    rng = G.rng_for(22833)
    for k in range(6):
        kind = k % 3
        if kind == 0:      # a long beam groan
            dur = rng.uniform(2.2, 3.6)
            x = G._creak(rng, dur, rng.uniform(3, 6), rng.uniform(12, 20),
                         [rng.uniform(95, 130), rng.uniform(210, 260), rng.uniform(380, 460)])
        elif kind == 1:    # a joist: quicker, higher, drier
            dur = rng.uniform(0.9, 1.5)
            x = G._creak(rng, dur, rng.uniform(10, 18), rng.uniform(35, 60),
                         [rng.uniform(260, 330), rng.uniform(520, 640), rng.uniform(900, 1100)])
        else:              # a creak that settles with a knock
            dur = rng.uniform(1.6, 2.4)
            x = G._creak(rng, dur * 0.8, rng.uniform(5, 9), rng.uniform(18, 30),
                         [rng.uniform(150, 190), rng.uniform(320, 380), rng.uniform(600, 700)])
            x = np.concatenate([x, np.zeros(int(dur * 0.2 * SR) + SR // 4)])
            m = int(0.25 * SR)
            knock = _modes(rng, m, [rng.uniform(90, 120), rng.uniform(190, 240)], [0.05, 0.03], [1.0, 0.5])
            knock += 0.4 * _burst(rng, m, 0.006, 200, 1400)
            G.add_at(x, G.norm(knock) * 0.8 * np.abs(x).max(), dur * 0.8 + 0.05, circular=False)
        x = _far(rng, x, 60, 1500 if kind != 1 else 2200, 2.4, 0.35)
        G.save_ogg(x, OUT / f"creak_{k + 1}.ogg", peak=0.6, fade_out=0.25)


def _pane(rng, n: int, base: float) -> np.ndarray:
    """A big mirror pane struck lightly: a full-height sheet rings lower and longer than a small
    plate, its modes roughly 1, 1.6, 2.3, 3.4 ... times the lowest, the high ones dying faster."""
    ratios = (1.0, 1.59, 2.31, 3.43, 4.62, 6.07)
    return _modes(rng, n, [base * r for r in ratios], [0.07 / (1 + 0.45 * i) for i in range(6)],
                  [1.0 / (1 + 0.6 * i) for i in range(6)], spread=0.015)


def snd_l228_glass_tick() -> None:
    """A mirror settling in its frame (five takes): a dry click of the glass against the wooden
    rebate, the big pane ringing for a moment, and under it the faint tock of the frame itself;
    sometimes a second smaller tick just after. The sound a cooling building's mirrors make."""
    rng = G.rng_for(22834)
    for k in range(5):
        n = int(0.6 * SR)
        t = np.arange(n) / SR
        x = np.zeros(n)
        hits = [0.004] + ([rng.uniform(0.06, 0.14)] if k in (1, 3) else [])
        for i, at in enumerate(hits):
            m = n - int(at * SR)
            click = _burst(rng, m, 0.0011, 1800, 12000)
            ring = _pane(rng, m, rng.uniform(1100, 1900))
            tock = _modes(rng, m, [rng.uniform(420, 560), rng.uniform(900, 1100)], [0.012, 0.008], [1.0, 0.5])
            hit = 0.6 * G.norm(click) + G.norm(ring) + 0.35 * G.norm(tock)
            G.add_at(x, hit * (1.0 if i == 0 else 0.5), at, circular=False)
        x *= np.clip(t / 0.0008, 0, 1)
        G.save_ogg(x, OUT / f"glass_tick_{k + 1}.ogg", peak=0.6, fade_out=0.06)


def snd_l228_glass_creak() -> None:
    """A pane creaking against its frame (four takes): the glass slipping in its wooden rebate in a
    quick run of tiny stick-slip catches (the rate rising and falling), each catch ringing the pane's
    lowest modes, with the faint low flex of the sheet under it. Short, dry, close."""
    rng = G.rng_for(22835)
    for k in range(4):
        dur = rng.uniform(0.7, 1.4)
        n = int(dur * SR)
        t = np.arange(n) / SR
        rate = np.interp(t, [0, dur * 0.35, dur * 0.7, dur], [16, rng.uniform(60, 120), rng.uniform(35, 60), 12])
        rate *= 1 + G.smooth_jitter(rng, n, 6, 0.3)
        ph = np.cumsum(rate) / SR
        catches = np.diff(np.floor(ph), prepend=0.0) > 0
        pulses = catches * rng.uniform(0.3, 1.0, n)
        res = np.zeros(n)
        for f0, q in ((rng.uniform(640, 780), 16.0), (rng.uniform(1100, 1300), 20.0), (rng.uniform(1900, 2200), 24.0)):
            res += G.fft_filter(pulses, lambda f, f0=f0, q=q: np.exp(-((f - f0) / (f0 / q)) ** 2))
        wood = G.fft_filter(pulses, lambda f: np.exp(-((f - 330) / 60) ** 2))
        flex = _band(rng.normal(size=n), 110, 300)
        env = np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 0.7
        x = (G.norm(res) + 0.3 * G.norm(wood) + 0.15 * G.norm(flex)) * env
        G.save_ogg(x, OUT / f"glass_creak_{k + 1}.ogg", peak=0.55, fade_out=0.08)


# =============================================================================
# Footsteps: one set per floor material (world/Level228Footsteps picks by zone)
# =============================================================================

def _scuff(rng, n: int, at: float, level: float, lo: float = 1500.0) -> np.ndarray:
    """The sole dragging a little dust or grit as it lands."""
    t = np.arange(n) / SR
    env = np.exp(-np.maximum(t - at, 0) / 0.035) * (t >= at)
    return _band(rng.normal(size=n), lo, 9000) * env * level


def snd_l228_step_boards() -> None:
    """Old floorboards (six takes): the heel's dull knock on a board, the board's hollow body
    answering low over the joist gap, the toe a moment later, and on half the takes the board
    creaking in its nails as the weight rolls over it."""
    rng = G.rng_for(22840)
    for k in range(6):
        n = int(0.5 * SR)
        t = np.arange(n) / SR
        heel = _modes(rng, n, [rng.uniform(150, 200), rng.uniform(240, 300), rng.uniform(390, 460)],
                      [0.035, 0.025, 0.015], [1.0, 0.7, 0.4])
        heel += 0.6 * G.norm(_burst(rng, n, 0.012, 300, 2200))
        heel *= np.clip(t / 0.002, 0, 1)
        toe_at = rng.uniform(0.06, 0.10)
        toe = np.zeros(n)
        G.add_at(toe, 0.5 * G.norm(_modes(rng, n, [rng.uniform(220, 280), rng.uniform(480, 560)], [0.02, 0.012], [1.0, 0.5])
                                    + 0.5 * _burst(rng, n, 0.008, 400, 3000)), toe_at, circular=False)
        x = G.norm(heel) + toe + _scuff(rng, n, 0.0, 0.05)
        if k % 2 == 0:
            cr = G._creak(rng, rng.uniform(0.18, 0.3), rng.uniform(25, 40), rng.uniform(70, 110),
                          [rng.uniform(480, 560), rng.uniform(820, 920), rng.uniform(1250, 1400)])
            G.add_at(x, 0.28 * G.norm(cr), rng.uniform(0.04, 0.09), circular=False)
        G.save_ogg(x, OUT / f"step_boards_{k + 1}.ogg", peak=0.8, fade_out=0.06)


def snd_l228_step_tile() -> None:
    """Patterned ceramic tile (six takes): a hard heel click with a short bright ring of the tile,
    a small thud of the floor under it, the toe's click, and the scratch of grit between sole and
    glaze. Brighter and harder than every other floor in the maze."""
    rng = G.rng_for(22841)
    for k in range(6):
        n = int(0.4 * SR)
        t = np.arange(n) / SR
        click = _burst(rng, n, 0.0025, 1800, 12000)
        ring = _modes(rng, n, [rng.uniform(2200, 2700), rng.uniform(3500, 4100), rng.uniform(5000, 5800)],
                      [0.012, 0.008, 0.005], [1.0, 0.6, 0.35])
        thud = _modes(rng, n, [rng.uniform(80, 110)], [0.025], [1.0])
        heel = (G.norm(click) + 0.45 * G.norm(ring) + 0.35 * G.norm(thud)) * np.clip(t / 0.0006, 0, 1)
        toe = np.zeros(n)
        if rng.uniform() < 0.75:
            G.add_at(toe, 0.45 * G.norm(_burst(rng, n, 0.0018, 2500, 12000) + 0.3 * ring), rng.uniform(0.06, 0.09),
                     circular=False)
        grit = G._crackle(rng, 0.12, rng.uniform(250, 500), 3000)
        grit = np.pad(grit, (0, n - len(grit)))
        x = heel + toe + 0.25 * G.norm(grit)
        G.save_ogg(x, OUT / f"step_tile_{k + 1}.ogg", peak=0.8, fade_out=0.05)


def snd_l228_step_lino() -> None:
    """Peeling checker linoleum (six takes): a soft, dull slap (lino over boards swallows the
    highs), the sticky crackle of the sole peeling off the old varnish, and on a few takes the
    small rubbery squeak of a turning shoe."""
    rng = G.rng_for(22842)
    for k in range(6):
        n = int(0.45 * SR)
        t = np.arange(n) / SR
        slap = _burst(rng, n, 0.015, 120, 1100) + 0.6 * _modes(rng, n, [rng.uniform(95, 125)], [0.035], [1.0])
        slap = G.norm(slap) * np.clip(t / 0.003, 0, 1)
        peel_at = rng.uniform(0.10, 0.16)
        m = int(0.06 * SR)
        peel = np.zeros(m)
        hits = rng.random(m) < 900 / SR
        peel[hits] = rng.choice([-1, 1], hits.sum()) * rng.random(hits.sum())
        peel = _band(peel, 1200, 6000) * np.sin(np.pi * np.arange(m) / m)
        x = slap.copy()
        G.add_at(x, 0.22 * G.norm(peel), peel_at, circular=False)
        if k in (1, 4):
            m = int(0.07 * SR)
            tt = np.arange(m) / SR
            f = np.linspace(rng.uniform(1000, 1200), rng.uniform(1400, 1700), m) * (1 + 0.03 * np.sin(2 * np.pi * 38 * tt))
            sq = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.pi * tt / tt[-1]) ** 2
            G.add_at(x, 0.12 * sq, rng.uniform(0.05, 0.12), circular=False)
        G.save_ogg(x, OUT / f"step_lino_{k + 1}.ogg", peak=0.8, fade_out=0.05)


def snd_l228_step_plywood() -> None:
    """Plywood sheeting over joists (six takes, the backstage service passages): a hollow boom of
    the sheet between its joists, louder in the body than boards, a knock where the heel lands,
    and now and then the rattle of a loose sheet against its screws."""
    rng = G.rng_for(22843)
    for k in range(6):
        n = int(0.5 * SR)
        t = np.arange(n) / SR
        body = _modes(rng, n, [rng.uniform(120, 150), rng.uniform(200, 240), rng.uniform(310, 360), rng.uniform(500, 560)],
                      [0.07, 0.05, 0.03, 0.018], [1.0, 0.8, 0.45, 0.25])
        knock = _burst(rng, n, 0.009, 250, 2000)
        x = (G.norm(body) + 0.5 * G.norm(knock)) * np.clip(t / 0.002, 0, 1)
        G.add_at(x, 0.4 * G.norm(_modes(rng, n, [rng.uniform(170, 210)], [0.03], [1.0])
                                 + 0.4 * _burst(rng, n, 0.006, 400, 2500)), rng.uniform(0.07, 0.1), circular=False)
        if k in (2, 5):
            for j in range(rng.integers(3, 6)):
                m = int(0.01 * SR)
                G.add_at(x, 0.12 * rng.uniform(0.5, 1.0) * _burst(rng, m, 0.0015, 800, 6000),
                         0.03 + j * rng.uniform(0.025, 0.04), circular=False)
        x += _scuff(rng, n, 0.0, 0.04)
        G.save_ogg(x, OUT / f"step_plywood_{k + 1}.ogg", peak=0.8, fade_out=0.06)


# =============================================================================
# The band organ (shared by the far-off organ and the music)
# =============================================================================

# Band-organ pipe registers as (harmonic, level). The flutes are wooden stopped pipes (hollow,
# odd-heavy); the "trumpet" reed register is bright and nasal (a formant near 1.3 kHz, see _pipe);
# the bass pipes are big open flues.
_FLUTE = [(1, 1.0), (2, 0.18), (3, 0.34), (4, 0.05), (5, 0.12), (7, 0.04)]
_BASS = [(1, 1.0), (2, 0.65), (3, 0.32), (4, 0.18), (5, 0.08)]


def _pipe(rng, freq: float, dur: float, reg: str, vel: float, wobble: np.ndarray | None = None) -> np.ndarray:
    """One band-organ pipe (mono): additive partials with the pipe's own mistuning (old pipes that
    nobody has voiced in decades drift up to +-14 cents), a quick speech with a breathy chiff, a
    short release. `wobble` is a per-sample pitch ratio shared by the whole performance (the
    tape/bellows warp)."""
    rel = 0.09 if reg != "bass" else 0.14
    n = int((dur + rel) * SR)
    t = np.arange(n) / SR
    ratio = np.ones(n) if wobble is None else np.pad(wobble[:n], (0, max(0, n - len(wobble))), constant_values=1.0)
    f = freq * 2 ** (rng.normal(0.0, 7.0) / 1200)
    if reg == "reed":
        parts = [(k, (1.0 / k ** 0.75) * (1 + 1.6 * np.exp(-((k * f - 1300) / 450) ** 2))) for k in range(1, 16)]
    elif reg == "bass":
        parts = _BASS
    else:
        parts = _FLUTE
    ph = np.cumsum(f * ratio) / SR
    x = np.zeros(n)
    for k, a in parts:
        if k * f > 9000:
            break
        x += a * np.sin(2 * np.pi * k * ph + rng.uniform(0, 6.3))
    attack = 0.018 if reg == "reed" else 0.03
    env = np.clip(t / attack, 0, 1) * np.where(t < dur, 1.0, np.exp(-(t - dur) / (rel / 3)))
    chiff = _band(rng.normal(size=n) * np.exp(-t / 0.02), f * 2, min(f * 8, 9000)) * 0.12
    breath = _band(rng.normal(size=n), f, min(f * 4, 8000)) * 0.015
    return (G.norm(x) + chiff + breath) * env * vel


def _drum(rng, kind: str, vel: float) -> np.ndarray:
    """The organ's own percussion: the bass drum on the bar line, the snare's dry tap."""
    n = int(0.5 * SR)
    t = np.arange(n) / SR
    if kind == "bass":
        f = 52 + 40 * np.exp(-t / 0.04)
        x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.22) + 0.2 * _burst(rng, n, 0.01, 200, 1500)
    else:
        x = 0.5 * _modes(rng, n, [190, 330], [0.05, 0.03], [1.0, 0.5]) + _burst(rng, n, 0.06, 1500, 7000)
    return G.norm(x) * vel


# An original waltz in A minor ("the funhouse waltz"), 3/4, (bar, beat, note, beats). Two
# eight-bar phrases, then a trio in C major that the far organ and the music both use.
_WALTZ = [
    (0, 0, "A4", 1), (0, 1, "C5", 1), (0, 2, "E5", 1),
    (1, 0, "A5", 2), (1, 2, "G#5", 1),
    (2, 0, "A5", 1), (2, 1, "E5", 2),
    (3, 0, "C5", 3),
    (4, 0, "D5", 1), (4, 1, "F5", 1), (4, 2, "A5", 1),
    (5, 0, "B5", 2), (5, 2, "A5", 1),
    (6, 0, "G#5", 1), (6, 1, "F5", 1), (6, 2, "D5", 1),
    (7, 0, "E5", 3),
    (8, 0, "A4", 1), (8, 1, "C5", 1), (8, 2, "E5", 1),
    (9, 0, "A5", 2), (9, 2, "B5", 1),
    (10, 0, "C6", 1), (10, 1, "B5", 1), (10, 2, "A5", 1),
    (11, 0, "G5", 2), (11, 2, "E5", 1),
    (12, 0, "F5", 1), (12, 1, "E5", 1), (12, 2, "D5", 1),
    (13, 0, "C5", 2), (13, 2, "B4", 1),
    (14, 0, "A4", 1), (14, 1, "B4", 1), (14, 2, "G#4", 1),
    (15, 0, "A4", 3),
]
_WALTZ_CHORDS = ["Am", "Am", "Am", "Am", "Dm", "Dm", "E7", "E7", "Am", "Am", "F", "C", "Dm", "Am", "E7", "Am"]
_TRIO = [
    (0, 0, "G5", 2), (0, 2, "E5", 1),
    (1, 0, "C5", 3),
    (2, 0, "F5", 2), (2, 2, "D5", 1),
    (3, 0, "B4", 3),
    (4, 0, "E5", 1), (4, 1, "F5", 1), (4, 2, "G5", 1),
    (5, 0, "A5", 2), (5, 2, "G5", 1),
    (6, 0, "F5", 1), (6, 1, "E5", 1), (6, 2, "D5", 1),
    (7, 0, "E5", 3),
]
_TRIO_CHORDS = ["C", "C", "G7", "G7", "C", "F", "Dm", "E7"]
# chord -> (bass note, the two-or-three-note "pah" voicing)
_VOICING = {
    "Am": ("A2", ["C4", "E4", "A4"]), "Dm": ("D3", ["D4", "F4", "A4"]), "E7": ("E2", ["D4", "G#4", "B4"]),
    "F": ("F2", ["C4", "F4", "A4"]), "C": ("C3", ["C4", "E4", "G4"]), "G7": ("G2", ["B3", "F4", "G4"]),
}


def _band_organ(rng, tune, chords, beat: float, vel: float, wobble: np.ndarray, drums: bool = True,
                tail: float = 2.0) -> np.ndarray:
    """A band organ playing `tune` over its oom-pah-pah (mono): the melody on the reed register
    doubled an octave down on flutes, the bass pipe on beat one, the chord's flutes on two and
    three, the bass drum on every other bar line and a snare tap on three now and then."""
    bars = len(chords)
    n = int((bars * 3 * beat + tail) * SR)
    x = np.zeros(n)

    def put(sig: np.ndarray, at: float) -> None:
        G.add_at(x, sig, at, circular=False)

    def wob(at: float) -> np.ndarray:
        i0 = int(at * SR)
        return wobble[i0:]

    for b, bt, note, beats in tune:
        at = (b * 3 + bt) * beat + rng.uniform(0, 0.012)
        f = G._m_hz(note)
        put(_pipe(rng, f, beats * beat * 0.86, "reed", vel, wob(at)), at)
        put(_pipe(rng, f / 2, beats * beat * 0.86, "flute", vel * 0.45, wob(at)), at)
    for i, name in enumerate(chords):
        bass, tones = _VOICING[name]
        at = i * 3 * beat
        put(_pipe(rng, G._m_hz(bass), beat * 0.8, "bass", vel * 0.8, wob(at)), at)
        for j in (1, 2):
            for tone in tones:
                a2 = at + j * beat + rng.uniform(0, 0.01)
                put(_pipe(rng, G._m_hz(tone), beat * 0.42, "flute", vel * 0.28, wob(a2)), a2)
        if drums:
            if i % 2 == 0:
                put(_drum(rng, "bass", vel * 0.55), at)
            if i % 4 == 3:
                put(_drum(rng, "snare", vel * 0.25), at + 2 * beat)
    return x


def _organ_wobble(rng, n: int, depth: float) -> np.ndarray:
    """The warp shared by every pipe of a performance: the bellows' slow uneven pressure (a few
    tenths of a hertz) and a faster tape-like flutter."""
    t = np.arange(n) / SR
    return (1.0 + depth * (0.6 * np.sin(2 * np.pi * 0.23 * t + rng.uniform(0, 6.3))
                           + 0.5 * G.smooth_jitter(rng, n, 0.4, 1.0))
            + 0.25 * depth * np.sin(2 * np.pi * 4.7 * t))


def snd_l228_organ() -> None:
    """The carnival's band organ, far away (three takes, mono, positional, played every few
    minutes from somewhere beyond the walls). Nobody can say where it plays from or why it still
    plays. Each take is the organ through several timber walls: the highs gone, the bass thinned,
    most of it arriving as the building's reverb; out of tune, and warped by its own bellows.
      1  the waltz's first sixteen bars
      2  the trio in C major, slower, and fainter at the end
      3  the waltz's opening phrase as the bellows run out: it slows, sags in pitch and stops"""
    rng = G.rng_for(22850)
    for k in range(3):
        if k == 0:
            beat, tune, chords = 60 / 132, _WALTZ, _WALTZ_CHORDS
        elif k == 1:
            beat, tune, chords = 60 / 120, _TRIO + [(b + 8, bt, nt, bs) for b, bt, nt, bs in _TRIO], _TRIO_CHORDS * 2
        else:
            beat, tune, chords = 60 / 128, _WALTZ[:17], _WALTZ_CHORDS[:8]
        total = len(chords) * 3 * beat + 3.0
        wob = _organ_wobble(rng, int(total * SR * 1.6), 0.006)
        x = _band_organ(rng, tune, chords, beat, 0.8, wob)
        if k == 2:
            # The bellows giving out: speed (and pitch) falls to about 70 % over the last half.
            m = int(len(x) * 1.6)
            u = np.arange(m) / len(x)
            ratio = np.where(u < 0.45, 1.0, 1.0 - 0.32 * np.clip((u - 0.45) / 0.6, 0, 1) ** 1.6)
            x = _varispeed(x, ratio)
            x *= np.clip((len(x) / SR - np.arange(len(x)) / SR) / 2.5, 0, 1)
        elif k == 1:
            x *= np.clip(1.0 - (np.arange(len(x)) / len(x) - 0.75) / 0.25 * 0.6, 0.4, 1.0)
        x = _far(rng, x, 140, 1300, 3.4, 0.55)
        x = np.concatenate([x, np.zeros(SR)])
        G.save_ogg(x, OUT / f"organ_{k + 1}.ogg", peak=0.5, fade_in=0.4, fade_out=1.5)


# =============================================================================
# Places: the exit hallway, the final chamber, the exit hint
# =============================================================================

def snd_l228_exit_wind() -> None:
    """The exit hallway (30 s loop, "in your head" while you are in it): this is the only place
    the outside gets in. Night wind pressing at the door at the end, a soft rush that gusts, a thin
    whistle through the gap under the door, dead leaves skittering over the concrete in the
    stronger gusts, and twice a loop the loose door rattling once in its frame."""
    T = 30.0
    n = int(T * SR)
    t = np.arange(n) / SR
    rng = G.rng_for(22860)
    gust = _gust(n, T, rng, (15.0, 10.0, 6.0), 0.8)
    rush = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 90, 1800, 2) / np.sqrt(np.maximum(f, 30)))
    rush = G.norm(rush) * gust ** 1.5
    whistle = G.periodic_noise(n, rng, lambda f: np.exp(-((f - 1720) / 35) ** 2) + 0.5 * np.exp(-((f - 2480) / 30) ** 2))
    whistle = G.norm(whistle) * np.clip((gust - 0.55) / 0.45, 0, 1) ** 2
    leaves = np.zeros(n)
    for _ in range(26):
        at = rng.uniform(0, T)
        if gust[int(at * SR) % n] < 0.6:
            continue
        burst = G._crackle(rng, rng.uniform(0.25, 0.7), rng.uniform(60, 160), 1400)
        burst *= np.sin(np.pi * np.arange(len(burst)) / len(burst))
        G.add_at(leaves, burst * rng.uniform(0.4, 1.0), at, circular=True)
    rattle = np.zeros(n)
    for at in (rng.uniform(4, 11), rng.uniform(17, 26)):
        m = int(0.35 * SR)
        r = np.zeros(m)
        for j in range(rng.integers(3, 6)):
            hit = _modes(rng, m, [rng.uniform(180, 230), rng.uniform(420, 520), rng.uniform(900, 1100)],
                         [0.04, 0.025, 0.012], [1.0, 0.6, 0.3]) + 0.4 * _burst(rng, m, 0.004, 600, 4000)
            G.add_at(r, hit * (0.8 ** j), j * rng.uniform(0.045, 0.07), circular=False)
        G.add_at(rattle, G.norm(r), at, circular=True)
    x = 0.8 * rush + 0.07 * whistle + 0.12 * G.norm(leaves) + 0.18 * rattle
    G.save_ogg(x, OUT / "exit_wind.ogg", peak=0.45, loop=True)


def snd_l228_chamber_hush() -> None:
    """The final chamber (20 s loop): total silence. Every other sound of the level stops as you
    step in (client/level228/Level228Ambience); what is left is the pressure of a small closed room
    on your ears: a very low, very soft swell well under 60 Hz and the faintest band of air. Played
    quietly; you should feel it, not hear it."""
    T = 20.0
    n = int(T * SR)
    rng = G.rng_for(22861)
    press = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 16, 55, 3))
    press = G.norm(press) * _gust(n, T, rng, (10.0, 20.0 / 3.0), 0.5)
    air = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 250, 1400, 2) / np.maximum(f, 50))
    x = press + 0.04 * G.norm(air)
    G.save_ogg(x, OUT / "chamber_hush.ogg", peak=0.35, loop=True)


def snd_l228_exit_hint() -> None:
    """The exit hint (8 s loop, written next to the other exit hints), honest about what it is:
    cold outside air being drawn through a gap somewhere ahead. A soft hiss that swells and sinks
    with the gusts outside, a thin whistle where it squeezes through, and a hollow low note from
    the corridor behind. Nothing glassy: the hint must never seem to point at one mirror."""
    T = 8.0
    n = int(T * SR)
    t = np.arange(n) / SR
    rng = G.rng_for(22862)
    gust = _gust(n, T, rng, (4.0, 8.0 / 3.0), 0.6)
    draught = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 300, 3400, 2) / np.sqrt(np.maximum(f, 20)))
    whistle = G.periodic_noise(n, rng, lambda f: np.exp(-((f - 1380) / 26) ** 2))
    hollow = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 45, 120, 3))
    x = (G.norm(draught) * gust * 0.7 + G.norm(whistle) * gust ** 2 * 0.2 + G.norm(hollow) * 0.4)
    G.save_ogg(x, G.SND / "exit_hint" / "level_228.ogg", peak=0.42, loop=True)


# =============================================================================
# Music
# =============================================================================

def snd_music_l228() -> None:
    """"Carousel Under Glass", ~1.6 minutes for Level 228: the funhouse waltz (the same tune the
    far organ plays in the level) remembered rather than played. A minor, 3/4, 88 bpm.
      Intro (4 bars):  a music box picks out the waltz's first bars alone, slowly, a little out of
                       tune, in a big dark room.
      A (16 bars):     the band organ comes in as if from the far end of the building (dull, wet,
                       warped), oom-pah-pah under the reed melody; the music box shadows it.
      B (8 bars):      the organ falls away; the trio's tune on a glass harmonica, answered half a
                       bar later by the music box with the same notes mirrored round E5 (the tune
                       and its reflection, as in "Silvering"), over a low drone.
      A' (8 bars):     the organ again, nearer, and then its bellows give out: it slows and sags
                       a fourth in pitch until it stops.
      Coda:            one music-box note and the room.
    Built on gen_assets' music track and music_c's glass instruments, matched to -16 LUFS."""
    beat = 60.0 / 88.0
    bar = 3 * beat
    intro, a_bars, b_bars, a2_bars = 4, 16, 8, 8
    total = (intro + a_bars + b_bars + a2_bars) * bar + 22.0
    tr = G._MTrack(total, seed=22870, wow=0.0045, flutter=0.0009)
    rng = tr.rng

    def box(at: float, note: str, vel: float, pan: float, pitch: float = 1.0) -> None:
        tr.bell("box", at, note, vel, decay=1.6, pitch=pitch * 2 ** (rng.normal(0, 6) / 1200), pan=pan)

    # Intro: the music box alone, slowly (15 % under tempo), the first phrase an octave up (a
    # music box's comb sits high).
    for b, bt, note, beats in _WALTZ[:9]:
        box((b * 3 + bt) * beat * 1.15 + 0.6, note, 0.28, 0.25, pitch=2.0)
    tr.drone("low", 0.0, intro * bar + 2.0, "A1", 0.07, fade=2.5, beat=0.19)

    # A: the band organ, far away (rendered mono, then put far off and slightly to the left).
    a0 = intro * bar
    wob = _organ_wobble(rng, int((a_bars * bar + 6) * SR * 1.2), 0.005)
    org = _band_organ(rng, _WALTZ, _WALTZ_CHORDS, beat, 0.7, wob, tail=3.0)
    org = _far(rng, org, 120, 1700, 3.0, 0.5)
    tr.add("organ", a0, *C._pan(G.norm(org) * 0.55, -0.25))
    for b, bt, note, beats in _WALTZ:
        if (b * 3 + bt) % 6 == 0:
            box(a0 + (b * 3 + bt) * beat + 0.02, note, 0.14, 0.45, pitch=2.0)

    # B: the trio on glass harmonica, and its reflection on the music box half a bar late.
    b0 = (intro + a_bars) * bar
    axis = 2 * 12 * np.log2(G._m_hz("E5") / 440.0)          # E5 in semitones from A4, doubled
    for b, bt, note, beats in _TRIO:
        at = b0 + (b * 3 + bt) * beat
        hz = G._m_hz(note)
        C._glass_harmonica(tr, "glass", at, beats * beat * 0.95, hz, 0.24, pan=-0.4, attack=0.3, release=2.0)
        semis = 12 * np.log2(hz / 440.0)
        mirrored = 440.0 * 2 ** ((axis - semis) / 12)
        tr.bell("box", at + 1.5 * beat, "A4", 0.17, decay=1.8, pitch=mirrored / 440.0, pan=0.5)
    for i, name in enumerate(_TRIO_CHORDS):
        bass, tones = _VOICING[name]
        tr.chord("pad", b0 + i * bar, bar * 0.95, tones, 0.07, bright=1200, attack=1.0, release=2.5)
    tr.drone("low", b0, b_bars * bar + 2.0, "C2", 0.08, fade=2.0, beat=0.21)

    # A': the organ nearer, then the bellows run down.
    c0 = (intro + a_bars + b_bars) * bar
    wob = _organ_wobble(rng, int((a2_bars * bar + 6) * SR * 1.8), 0.006)
    org = _band_organ(rng, _WALTZ[:17], _WALTZ_CHORDS[:8], beat, 0.75, wob, tail=3.0)
    m = int(len(org) * 1.8)
    u = np.arange(m) / len(org)
    ratio = np.where(u < 0.4, 1.0, 1.0 - 0.25 * np.clip((u - 0.4) / 0.7, 0, 1) ** 1.4)
    org = _varispeed(org, ratio)
    org *= np.clip((len(org) / SR - np.arange(len(org)) / SR) / 3.0, 0, 1)
    org = _far(rng, org, 90, 2600, 2.4, 0.35)
    tr.add("organ", c0, *C._pan(G.norm(org) * 0.7, -0.1))
    end = c0 + len(org) / SR

    # Coda: one music-box note in the empty room.
    box(end + 1.5, "E6", 0.2, 0.2)
    tr.drone("low", end - 2.0, 10.0, "A1", 0.06, fade=3.0, beat=0.15)
    tr.noise_bed("air", 100, 1500, [(0, 0.0), (5, 0.005), (total * 0.5, 0.008), (total - 5, 0.004), (total, 0.0)],
                 drift_amt=0.5)

    L, R = tr.master(mix={"organ": 1.0, "box": 0.75, "glass": 0.85, "pad": 0.5, "low": 0.5, "air": 1.0},
                     reverb={"organ": 0.25, "box": 0.6, "glass": 0.5, "pad": 0.5, "low": 0.2, "air": 0.0},
                     room_s=5.5, room_lp=4200, lp=7200, drive=1.15)
    C._save_music(L, R, "level228", fade_out=8.0)


# "Silvering" (v1's piece): E Aeolian scale degrees from E4; the reflection is the diatonic
# inversion round B4 (degree d -> 2 * AXIS - d).
_SCALE = (0, 2, 3, 5, 7, 8, 10)
_AXIS = 4


def _deg_hz(d: int, shift: float = 0.0) -> float:
    """Scale degree (0 = E4) -> Hz; `shift` adds semitones (the reflection's wrong notes)."""
    octave, step = divmod(d, 7)
    semis = 12 * octave + _SCALE[step] + shift
    return G._m_hz("E4") * 2 ** (semis / 12)


_SILVER_TUNE = [
    (0, 0, 7, 2), (0, 2, 6, 1), (0, 3, 4, 1),
    (1, 0, 5, 3), (1, 3, 4, 1),
    (2, 0, 2, 2), (2, 2, 4, 1), (2, 3, 7, 1),
    (3, 0, 8, 4),
    (4, 0, 9, 2), (4, 2, 8, 1), (4, 3, 7, 1),
    (5, 0, 5, 2), (5, 2, 4, 2),
    (6, 0, 3, 1), (6, 1, 4, 1), (6, 2, 5, 2),
    (7, 0, 4, 4),
]
_SILVER_CHORDS = [("E2", ["B3", "E4", "G4"]), ("C2", ["G3", "B3", "E4"]), ("A1", ["C4", "E4", "B4"]),
                  ("B1", ["F#3", "A3", "E4"]), ("E2", ["G3", "B3", "F#4"]), ("C2", ["E3", "B3", "G4"]),
                  ("A1", ["E3", "C4", "B4"]), ("B1", ["F#3", "B3", "E4"])]


def snd_music_l228_silvering() -> None:
    """"Silvering", ~2.4 minutes, Level 228's second piece: a tune and its reflection. E minor,
    56 bpm, on glass: a glass harmonica sings the tune on the left; half a bar later a glassy
    electric piano answers on the right with the same tune turned upside down round B (its mirror
    image, note for note), through a ping-pong echo like two facing mirrors. The chords run
    forward and then backward. Intro (4 bars), A (8), B (8, everything backward), A' (8: the tune
    stops after four bars, the reflection carries on alone and goes wrong), coda (4)."""
    beat = 60.0 / 56.0
    bar = 4 * beat
    intro, sec, coda = 4, 8, 4
    total = (intro + 3 * sec + coda) * bar + 9.0
    tr = G._MTrack(total, seed=22820, wow=0.0028, flutter=0.0006)
    lag = 2 * beat

    def chords(start_bar: int, order, vel: float, reverse: bool = False) -> None:
        for i, idx in enumerate(order):
            bass, tones = _SILVER_CHORDS[idx]
            at = (start_bar + i) * bar
            if reverse:
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

    chords(0, [0, 1, 2, 3], 0.12)
    C._glass_ep(tr, "refl", 1.5 * bar, _deg_hz(_AXIS + 7), 0.22, decay=2.0, pan=0.0, glass=0.8)
    a = intro
    chords(a, range(8), 0.14)
    tune(a, _SILVER_TUNE, 0.30, reflect=False)
    tune(a, _SILVER_TUNE, 0.30, reflect=True)
    b0 = intro + sec
    chords(b0, range(7, -1, -1), 0.13, reverse=True)
    retro = []
    for bb, bt, d, beats in reversed(_SILVER_TUNE):
        end = 32 - (bb * 4 + bt)
        start = end - beats
        retro.append((start // 4, start % 4, d, beats))
    tune(b0, retro, 0.26, reflect=False)
    tune(b0, retro, 0.26, reflect=True)
    a2 = intro + 2 * sec
    chords(a2, range(8), 0.14)
    tune(a2, _SILVER_TUNE, 0.30, reflect=False, until_bar=4)
    tune(a2, _SILVER_TUNE, 0.32, reflect=True, wrong=frozenset({11, 13, 16}))
    c0 = (intro + 3 * sec) * bar
    C._glass_harmonica(tr, "lead", c0, 2.5 * beat, _deg_hz(7), 0.24, pan=-0.45, attack=0.5, release=3.0)
    C._glass_ep(tr, "refl", c0 + lag, _deg_hz(2, 1.0), 0.24, decay=3.0, pan=0.55, glass=0.6)
    tr.chord("pad", c0, coda * bar, ["E3", "B3", "F#4", "G4"], 0.15, bright=1300, attack=2.0, release=7.0)
    tr.drone("low", c0, coda * bar + 4.0, "E2", 0.08, fade=3.0, beat=0.17)
    tr.noise_bed("air", 120, 1800, [(0, 0.0), (6, 0.006), (total * 0.5, 0.009), (total - 6, 0.005), (total, 0.0)],
                 drift_amt=0.5)
    L, R = tr.buses["refl"]
    eL, eR = C._echo(L.astype(float), R.astype(float), delay=beat * 0.75, feedback=0.42, taps=5, lp=2600)
    tr.buses["refl"] = (eL.astype(np.float32), eR.astype(np.float32))
    L, R = tr.master(mix={"lead": 1.0, "refl": 0.8, "pad": 0.5, "low": 0.45, "air": 1.0},
                     reverb={"lead": 0.5, "refl": 0.45, "pad": 0.55, "low": 0.2, "air": 0.0},
                     room_s=5.0, room_lp=5000, lp=7800, drive=1.15)
    C._save_music(L, R, "level228_silvering", fade_out=8.0)


TEXTURES: list = []
SOUNDS = [snd_l228_ambience, snd_l228_bulb_buzz, snd_l228_bulb_fail, snd_l228_creak, snd_l228_glass_tick,
          snd_l228_glass_creak, snd_l228_step_boards, snd_l228_step_tile, snd_l228_step_lino,
          snd_l228_step_plywood, snd_l228_organ, snd_l228_exit_wind, snd_l228_chamber_hush,
          snd_l228_exit_hint, snd_music_l228, snd_music_l228_silvering]
