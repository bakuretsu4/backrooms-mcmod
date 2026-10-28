"""Level 0's events and set pieces: tubes dying in a wave and coming back, the blackout rolling
away, a far district going dark, the Front Office alarm and its beacon, the Atrium's air, sounds
from up the Shaft Hall's shafts, a falling ceiling tile, the drawn door that is real, and the
tones of the hidden rooms (a radio, a clock, a sealed room, the hidden office behind the door).

Played by (see client/audio/Level0Soundscape and client/light/FixtureSounds):
  level0.wave_pop            positional, a tube going dark in a blackout wave or a local outage:
                             its ballast buzz sagging in pitch and dying, the holder's clack, a soft
                             gas "pft" and a glint of glass. Auto-played per tube by FixtureSounds.
  level0.tube_strike         positional, a tube coming back: real starters ticking and catching.
  level0.emergency_relay     positional, an emergency lamp switching on: the transfer relay's clunk,
                             its charger whining up, the lamp's filament tink.
  level0.blackout_wave       in your head (replaces event.power_down on Level 0): a far breaker,
                             the whole floor's hum sagging, ballasts popping one after another,
                             each further off and duller, rolling away; then nothing.
  level0.power_restore       in your head (replaces event.power_up on Level 0): far contactors, the
                             building's mains swelling back in the walls. The tubes near you then
                             strike one by one on their own (tube_strike), some never.
  level0.district_down       positional, heard far: a district's contactor dropping out, a muffled
                             cascade of ballasts and the hum sagging somewhere out there.
  level0.office_alarm        positional loop, heard to 80 blocks: an old mechanical fire horn in the
                             temporal-3 pattern (three blasts, a pause), each blast spinning up; it
                             is failing: one blast chatters (a bad relay), the last one sags.
  level0.beacon_motor        positional loop, close: the red beacon's gear motor, a cam clack per turn.
  level0.atrium_air          positional loop, high in the atrium: cold air moving in a huge shaft,
                             its low resonances swelling like breath across a giant bottle.
  level0.shaft_above         positional one-shots up a shaft: a scrape, a knock, a creak, grit
                             trickling, a far thump; all coming down a long hard flue.
  level0.tile_crack          positional, at a shaft top: a ceiling tile working loose.
  level0.tile_fall           positional: a mineral-fibre tile hitting the carpet, crumbs, dust.
  level0.drawn_door          positional: the drawn door turning out real: paper crackle, a latch,
                             the pressure of another room.
  level0.radio               positional loop (hidden room): an old radio, a slow waltz drifting in
                             and out of static, and a voice reading the same message again.
  level0.clock_tick          positional loop (hidden room): a wall clock that skips a beat.
  level0.sealed_room         positional loop (hidden room): the stillness of a room shut for years.
  level0.hidden_office       positional loop (behind the Front Office's drawn door): a clock and the
                             faint hum of a PA microphone left switched on.
  level0.intercom_front_office  the PA asking for someone to come to the front office (our own words).
  level0.still_creak         positional one-shots (the room of chairs facing the wall): a chair's
                             seat and gas lift creaking as weight leaves it, as if someone stood up.
  level0.pillar_settle       positional (Peripheral Shift): a pillar settling where it now stands.
  level0.pit_draught         positional loop (Pitfalls): cold, hollow air breathing up out of shafts.
  level0.red_loop            positional (Red Rooms): their hum's tail dragged low, a lap completed.

Loudness (LUFS, the same meter as level2_audio; README "Mix": nothing above about -18): the alarm
-19.5 (its blasts reach -18 momentary; it must carry 80 blocks; its beacon -33), event beds -25 to
-27, a tube's pop -28 and strike -30, the relay -28, the district -23 (heard from far, distance
does the rest), the atrium air -30, shaft sounds -29, tile crack -30, tile fall -24, the drawn door
-27, the radio -31, the clock -34, the sealed room -37, the hidden office -33, the chair creak -36,
the pillar -30, the pit draught -30, the red lap -27, and the PA line -18 (as loud as the other PA
lines are heard: event.intercom's files times their sounds.json volumes).

Run only these with:  python tools/gen_assets.py snd_l0sp_
Every generator has its own fixed seed (40300-40449), so re-running gives identical files.
"""
from __future__ import annotations

import numpy as np

import gen_assets as G
import anomaly_assets.level2_audio as L2  # the LUFS meter
import anomaly_assets.level0_audio as A  # _save, _whole, _slow, _gain_to
import level_assets.recorded as R  # the vendored recordings

SR = G.SR
OUT = G.SND / "level0"


# =============================================================================
# Shared pieces
# =============================================================================

def _ballast_cut(rng, lead: float, sag: float) -> np.ndarray:
    """A tube's ballast buzz (the real recording) for `lead` seconds, then the power leaves it:
    the buzz sags in pitch and dies over about `sag` seconds."""
    hum = R._clean(R._rec("638895_fluorescent_hum"), lo=70)
    off = int(rng.uniform(0.5, 8.0) * SR)
    src = hum[off:off + 3 * SR]
    n = int((lead + sag * 4) * SR)
    t = np.arange(n) / SR
    after = np.clip(t - lead, 0, None)
    rate = np.where(t < lead, 1.0, 0.35 + 0.65 * np.exp(-after / sag))
    y = R._resample_varying(src, rate)
    y = np.pad(y, (0, max(0, n - len(y))))[:n]
    y *= np.where(t < lead, np.clip(t / 0.03, 0, 1), np.exp(-after / (sag * 0.6)))
    return G.norm(y)


def _tube_pop(rng) -> np.ndarray:
    """The moment a tube goes out: the lamp holder's dry clack, a soft gas 'pft' and a glint of
    glass."""
    n = int(0.3 * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    clack = G._da_hit(rng, [rng.uniform(1100, 1700), rng.uniform(2600, 3400), rng.uniform(320, 460)],
                      [0.006, 0.003, 0.02], [1.0, 0.5, 0.7], noise_ms=0.7, noise_lp=6000)
    G.add_at(out, G.norm(clack), 0.0, False)
    pft = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 150, 1500, 2)) * np.exp(-t / 0.012)
    G.add_at(out, 0.7 * G.norm(pft), 0.002, False)
    tink = G.ring([rng.uniform(4500, 6500), rng.uniform(7500, 9000)], [0.01, 0.006], [1.0, 0.4], 0.08)
    G.add_at(out, 0.18 * G.norm(tink), 0.004, False)
    return G.norm(out)


def _shaft_ir(rng, dur: float, decay: float, lp: float, flutter: float = 0.034, count: int = 16) -> np.ndarray:
    """The answer of a tall, hard, narrow shaft: a long dark tail plus a flutter echo (the sound
    bouncing between its parallel walls every `flutter` seconds, fading)."""
    ir = G.reverb_ir(rng, dur, decay, lp=lp, predelay=0.02)
    k = np.zeros_like(ir)
    for j in range(1, count):
        i = int(j * flutter * SR * rng.uniform(0.97, 1.03))
        if i < len(k):
            k[i] += 0.6 ** j
    k = G.fft_filter(k, lambda f: G.lp_gain(f, lp * 0.7, 1))[:len(ir)]
    ir = ir + k * np.abs(ir).max() * 5.0
    return ir / (np.sqrt((ir ** 2).sum()) + 1e-12)


def _wet(x: np.ndarray, ir: np.ndarray, mix: float) -> np.ndarray:
    """x plus its reverb (mix = the reverb's peak relative to x's), the tail kept."""
    n = len(x) + len(ir)
    dry = np.pad(x, (0, len(ir)))
    wet = np.pad(G.conv(x, ir), (0, 1))[:n]
    return dry + mix * G.norm(wet) * np.abs(x).max()


def _starter_cluster(rng, at: float) -> tuple[np.ndarray, float]:
    """A real tube trying to strike: about 0.7 s of starter ticks and the catch, from the
    recording, starting at its `at`-th onset. Returns (sound, onset used)."""
    starts = R._clean(R._rec("452625_fluorescent_switch_starts"), lo=90)
    on = R._onsets(starts)
    t = on[int(at) % len(on)]
    seg = starts[max(0, int((t - 0.01) * SR)): int((t + 0.75) * SR)]
    return G.norm(seg * G.env_adsr(len(seg), 0.001, 0.12)), t


def _sapi_pa(text: str, voice_name: str, rate: int, rng, chime: bool) -> np.ndarray:
    """A PA announcement like event.intercom's (gen_assets.snd_intercom): the two-tone chime,
    the voice through a tinny distorted horn, and a big room."""
    speech = G.norm(G._sapi(text, voice_name, rate))
    lead = 1.4 if chime else 0.3
    n = int((lead + len(speech) / SR + 3.5) * SR)
    dry = np.zeros(n)
    if chime:
        G.add_at(dry, 0.6 * G._chime(rng), 0.05, False)
    G.add_at(dry, speech, lead, False)
    dry = G.fft_filter(dry, lambda f: G.bp_gain(f, 300, 3400, 3) * (1 + 1.5 * np.exp(-((f - 1800) / 400) ** 2)))[:n]
    dry = G.dist(G.norm(dry), 3.0)
    dry = G.bitcrush(dry, 10, 2)
    hiss = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 400, 4000, 1))[:n]
    dry = G.norm(dry) + 0.015 * G.norm(hiss) * (np.arange(n) < n - SR)
    ir = G.reverb_ir(rng, 4.0, 0.9, lp=3000, predelay=0.03, early=((0.07, 0.5), (0.16, 0.3), (0.29, 0.2)))
    wet = G.conv(dry, ir)[:n]
    return G.norm(dry) * 0.7 + G.norm(wet) * 0.55


# =============================================================================
# Tubes dying and coming back
# =============================================================================

def snd_l0sp_wave_pop() -> None:
    """Four tubes going out (one sound per tube as a blackout wave or an outage reaches it):
    the buzz, then the power leaves: the holder clacks, the gas goes 'pft', the buzz sags and dies.
    3 arcs for a moment first; 4 goes out with no pop at all, only its buzz sinking."""
    for i in range(1, 5):
        rng = G.rng_for(40300 + i)
        lead = rng.uniform(0.08, 0.14)
        sag = rng.uniform(0.12, 0.22)
        x = np.zeros(int(1.2 * SR))
        G.add_at(x, 0.55 * _ballast_cut(rng, lead, sag), 0.0, False)
        if i == 3:
            m = int(0.14 * SR)
            crackle = np.zeros(m)
            tt = 0.0
            while tt < 0.13:
                k = int(rng.uniform(0.0006, 0.003) * SR)
                G.add_at(crackle, rng.normal(size=k) * rng.uniform(0.2, 1.0), tt, False)
                tt += rng.exponential(0.012)
            crackle = G.fft_filter(crackle, lambda f: G.bp_gain(f, 1500, 8000, 2))
            G.add_at(x, 0.35 * G.norm(crackle), max(0.0, lead - 0.12), False)
        if i != 4:
            G.add_at(x, _tube_pop(rng) * rng.uniform(0.8, 1.0), lead, False)
        x = G._da_far(x, rng, rng.uniform(6000, 8000), hp=90, air=0.25, room=0.35)
        A._save(x, OUT / f"wave_pop{i}.ogg", -28.0, peak=0.6, fade_out=0.1)


def snd_l0sp_tube_strike() -> None:
    """Three tubes coming back: real starters ticking as the tube tries, then catching with a
    short swell of buzz (tubes return one by one after a blackout, some never)."""
    for i in range(1, 4):
        rng = G.rng_for(40310 + i)
        seg, _ = _starter_cluster(rng, i * 2 - 1)
        n = int(1.6 * SR)
        x = np.zeros(n)
        G.add_at(x, seg, 0.01, False)
        hum = R._clean(R._rec("638895_fluorescent_hum"), lo=70)[int(rng.uniform(1, 6) * SR):]
        m = int(0.9 * SR)
        swell = hum[:m] * G.smoothstep(0.0, 0.25, np.arange(m) / SR) * np.exp(-np.arange(m) / (0.5 * SR))
        G.add_at(x, 0.35 * G.norm(swell), 0.01 + len(seg) / SR * 0.8, False)
        A._save(x, OUT / f"tube_strike{i}.ogg", -30.0, peak=0.5, fade_out=0.15)


def snd_l0sp_emergency_relay() -> None:
    """Two emergency lamps switching on: the transfer relay's heavy clunk (and a bounce), the
    charger's faint whine rising, the lamp filament's tink as it heats."""
    for i in range(1, 3):
        rng = G.rng_for(40320 + i)
        n = int(1.3 * SR)
        t = np.arange(n) / SR
        x = np.zeros(n)
        clunk = G._da_hit(rng, [rng.uniform(150, 210), rng.uniform(380, 480), rng.uniform(2300, 3000)],
                          [0.035, 0.015, 0.004], [1.0, 0.6, 0.5], noise_ms=1.2, noise_lp=5000)
        G.add_at(x, G.norm(clunk), 0.01, False)
        G.add_at(x, 0.35 * G.norm(clunk), 0.01 + rng.uniform(0.012, 0.02), False)
        f = rng.uniform(1700, 1900) + 500 * (1 - np.exp(-t / 0.25))
        whine = np.sin(2 * np.pi * np.cumsum(f) / SR) * G.smoothstep(0.05, 0.2, t) * np.exp(-np.clip(t - 0.2, 0, None) / 0.4)
        G.add_at(x, 0.08 * whine, 0.0, False)
        tink = G.ring([rng.uniform(5200, 6400), rng.uniform(8800, 9800)], [0.03, 0.015], [1.0, 0.4], 0.2)
        G.add_at(x, 0.12 * G.norm(tink), rng.uniform(0.12, 0.18), False)
        x = G._da_far(x, rng, 7000, hp=70, air=0.2, room=0.3)
        A._save(x, OUT / f"emergency_relay{i}.ogg", -28.0, peak=0.6, fade_out=0.12)


def _cascade(rng, n: int, start: float, span: float, count: int, far_lp: float) -> np.ndarray:
    """Ballasts going out one after another, each further off: quieter and duller with each pop,
    spreading out in time like a wave rolling away."""
    out = np.zeros(n)
    for k in range(count):
        d = k / max(1, count - 1)
        at = start + span * d ** 0.85 + rng.uniform(-0.02, 0.02)
        pop = _tube_pop(rng) * (1.0 - 0.85 * d) ** 1.5 * rng.uniform(0.6, 1.0)
        lp = 6500 * (far_lp / 6500) ** d
        pop = G.fft_filter(pop, lambda f, lp=lp: G.lp_gain(f, lp, 2))
        G.add_at(out, pop, max(0.0, at), False)
    return out


def snd_l0sp_blackout_wave() -> None:
    """The blackout on Level 0: a far breaker trips, the hum of the whole floor sags and dies over
    about a second and a half while ballasts pop one after another, each further away and duller,
    as the dark rolls out from where it began. Then the tail fades into silence."""
    rng = G.rng_for(40331)
    dur = 4.5
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    brk = G.fft_filter(R._breaker(), lambda f: G.lp_gain(f, 1800, 2))
    G.add_at(x, 0.7 * G.norm(brk), 0.02, False)
    t0 = 0.1
    after = np.clip(t - t0, 0, None)
    rate = np.where(t < t0, 1.0, 0.3 + 0.7 * np.exp(-after / 0.6))
    hum = R._resample_varying(R._hum_bed(8.0), rate)
    hum = np.pad(hum, (0, max(0, n - len(hum))))[:n]
    hum *= np.where(t < t0, 1.0, np.exp(-after / 0.55))
    G.add_at(x, 0.5 * G.norm(hum), 0.0, False)
    G.add_at(x, 0.9 * G.norm(_cascade(rng, n, 0.12, 1.6, 22, 700)), 0.0, False)
    x = _wet(x, G.reverb_ir(rng, 2.6, 0.7, lp=3000, predelay=0.03), 0.4)
    A._save(x, OUT / "blackout_wave.ogg", -25.0, peak=0.7, fade_out=0.5)


def snd_l0sp_power_restore() -> None:
    """The power coming back on Level 0: contactors clack in a far panel, one after another, and
    the mains hum swells back into the walls. No lights yet: they strike on their own after."""
    rng = G.rng_for(40332)
    dur = 3.6
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    at = 0.03
    for k in range(3):
        clack = G._da_hit(rng, [rng.uniform(140, 200), rng.uniform(900, 1300), rng.uniform(2200, 2800)],
                          [0.04, 0.01, 0.004], [1.0, 0.5, 0.4], noise_ms=1.0, noise_lp=3500)
        G.add_at(x, G.norm(clack) * (1.0 - 0.25 * k), at, False)
        at += rng.uniform(0.18, 0.35)
    m = n
    hum, rasp = G._mains_buzz(2 * np.pi * 60 * np.arange(m) / SR)
    swell = (hum + 0.2 * rasp) * G.smoothstep(0.25, 1.4, t) * (1 - 0.6 * G.smoothstep(1.6, 3.4, t))
    swell = G.fft_filter(swell, lambda f: G.lp_gain(f, 500, 2) * G.hp_gain(f, 45, 2))
    G.add_at(x, 0.45 * G.norm(swell), 0.0, False)
    x = G._da_far(x, rng, 2400, hp=40, air=0.3, room=0.8)
    A._save(x, OUT / "power_restore.ogg", -27.0, peak=0.6, fade_out=0.6)


def snd_l0sp_district_down() -> None:
    """Two takes of a district going dark far away: its contactor drops out with a heavy clunk,
    a muffled cascade of ballasts and the hum sagging out there, heard through walls."""
    for i in range(1, 3):
        rng = G.rng_for(40340 + i)
        dur = 3.2
        n = int(dur * SR)
        t = np.arange(n) / SR
        x = np.zeros(n)
        clunk = G._da_hit(rng, [rng.uniform(90, 130), rng.uniform(260, 340), rng.uniform(1400, 1900)],
                          [0.06, 0.03, 0.008], [1.0, 0.6, 0.4], noise_ms=2.0, noise_lp=2500)
        G.add_at(x, G.norm(clunk), 0.02, False)
        t0 = 0.05
        after = np.clip(t - t0, 0, None)
        rate = np.where(t < t0, 1.0, 0.35 + 0.65 * np.exp(-after / 0.45))
        hum = R._resample_varying(R._hum_bed(7.0)[int(rng.uniform(0, 1) * SR):], rate)
        hum = np.pad(hum, (0, max(0, n - len(hum))))[:n] * np.where(t < t0, 1.0, np.exp(-after / 0.4))
        G.add_at(x, 0.6 * G.norm(hum), 0.0, False)
        G.add_at(x, 0.7 * G.norm(_cascade(rng, n, 0.08, 1.1, 12, 900)), 0.0, False)
        x = G._da_far(x, rng, rng.uniform(1100, 1500), hp=50, air=0.45, room=1.2)
        A._save(x, OUT / f"district_down{i}.ogg", -23.0, peak=0.7, fade_out=0.4)


# =============================================================================
# The Front Office
# =============================================================================

def snd_l0sp_office_alarm() -> None:
    """12 s seamless loop: an old mechanical fire horn in the temporal-3 pattern (three half-second
    blasts, a second and a half of quiet), three times. Each blast spins up in pitch as the
    diaphragm catches, buzzing hard through the 120 Hz of its interrupter contacts and a horn
    throat that rattles a little at its peaks. It is failing: in the third round the middle blast
    chatters (a relay that won't hold) and the last one sags. A big dark room around it."""
    rng = G.rng_for(40351)
    T = 12.0
    n = int(T * SR)
    x = np.zeros(n)
    ticks = np.zeros(n)
    for c in range(3):
        for b in range(3):
            start = c * 4.0 + b * 1.0 + 0.05
            dur = 0.5
            m = int((dur + 0.02) * SR)
            tt = np.arange(m) / SR
            f0 = 452.0 * (1 - 0.22 * np.exp(-tt / 0.025)) * rng.uniform(0.995, 1.005)
            if c == 2 and b == 2:
                f0 = f0 * (1 - 0.07 * np.clip((tt - 0.12) / 0.38, 0, 1))
            ph = 2 * np.pi * np.cumsum(f0) / SR
            s = np.sin(ph)
            y = np.sign(s) * np.abs(s) ** 0.35
            y *= 0.72 + 0.28 * np.sign(np.sin(2 * np.pi * 120 * tt + rng.uniform(0, 6.28)))
            env = np.clip(tt / 0.012, 0, 1) * np.clip((dur - tt) / 0.015, 0, 1)
            if c == 2 and b == 1:
                gate = ((tt < 0.08) | ((tt > 0.15) & (tt < 0.21)) | ((tt > 0.3) & (tt < 0.36)) | (tt > 0.44)).astype(float)
                env *= G.fft_filter(gate, lambda f: G.lp_gain(f, 200, 1))
            G.add_at(x, y * env, start, True)
            tick = G._da_hit(rng, [rng.uniform(2200, 2800), rng.uniform(600, 800)], [0.003, 0.01], [1.0, 0.5],
                             noise_ms=0.4, noise_lp=6000)
            G.add_at(ticks, G.norm(tick), start - 0.008, True)
    horn = lambda f: G.bp_gain(f, 380, 4200, 2) * (1 + 1.8 * np.exp(-((f - 1350) / 260) ** 2)
                                                   + 0.9 * np.exp(-((f - 2700) / 400) ** 2))
    x = G.fft_filter(x, horn, circular=True)
    x = G.dist(G.norm(x) * 1.2, 2.5)
    # The throat rattling at the loudest moments.
    env = G.fft_filter(np.abs(x), lambda f: G.lp_gain(f, 30, 1), circular=True)
    rattle = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 2000, 5000, 2)) * np.clip(env / (env.max() + 1e-12) - 0.4, 0, None)
    x = G.norm(x) + 0.05 * G.norm(rattle) + 0.12 * G.norm(ticks)
    ir = G.reverb_ir(rng, 2.4, 0.7, lp=3500, predelay=0.02, early=((0.04, 0.4), (0.09, 0.3)))
    x = G.norm(x) + 0.35 * G.norm(G.circ_conv(x, ir))
    # As loud as the mix allows: its blasts (momentary, 400 ms) reach the -18 ceiling, the loop as a
    # whole (with its pauses) -19.5. It must carry 80 blocks through walls that swallow its highs.
    A._save(x, OUT / "office_alarm.ogg", -19.5, peak=0.85, loop=True)


def snd_l0sp_beacon_motor() -> None:
    """The red beacon's motor turning its mirror at 2.2 turns a second: a faint gear whine pulsing
    with the turn, the motor's low hum and a soft cam clack once per turn. Ten whole turns."""
    rng = G.rng_for(40352)
    T = 10.0 / 2.2
    n = int(T * SR)
    t = np.arange(n) / SR
    rot = 2 * np.pi * _turns(T) * t
    whine = np.sin(2 * np.pi * A._whole(1320.0, T) * t) * (0.6 + 0.4 * np.sin(rot))
    hum = np.sin(2 * np.pi * A._whole(120.0, T) * t) + 0.4 * np.sin(2 * np.pi * A._whole(240.0, T) * t + 0.5)
    brush = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 2500, 7000, 2))
    clacks = np.zeros(n)
    for k in range(10):
        c = G._da_hit(rng, [rng.uniform(1600, 2000), rng.uniform(500, 650)], [0.004, 0.012], [1.0, 0.6], noise_ms=0.5)
        G.add_at(clacks, G.norm(c) * rng.uniform(0.8, 1.0), k * T / 10, True)
    x = 0.25 * whine + 0.5 * hum + 0.04 * brush + 0.5 * clacks
    A._save(x, OUT / "beacon_motor.ogg", -33.0, peak=0.5, loop=True)


def _turns(T: float) -> float:
    """The beacon's turns per second, rounded so a whole number fits the loop."""
    return round(2.2 * T) / T


def snd_l0sp_drawn_door() -> None:
    """The drawn door is real: the drawing's paper crackling as it gives, a latch letting go, and
    the soft push of air from a room that should not be there."""
    rng = G.rng_for(40353)
    dur = 2.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    crackle = np.zeros(n)
    tt = 0.0
    while tt < 0.5:
        k = int(rng.uniform(0.0005, 0.003) * SR)
        G.add_at(crackle, rng.normal(size=k) * rng.uniform(0.1, 1.0) ** 2 * (0.3 + tt * 1.6), tt, False)
        tt += rng.exponential(0.006)
    G.add_at(x, 0.6 * G.norm(G.fft_filter(crackle, lambda f: G.bp_gain(f, 1800, 9000, 2))), 0.0, False)
    latch = G._da_hit(rng, [rng.uniform(2400, 3000), rng.uniform(4400, 5200)], [0.004, 0.002], [1.0, 0.5],
                      noise_ms=0.5, noise_lp=6000)
    G.add_at(x, 0.7 * G.norm(latch), 0.52, False)
    whoomp = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 25, 140, 2)) * G._da_env(n, [(0, 0), (0.55, 0), (0.75, 1), (1.6, 0), (dur, 0)])
    air = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 300, 2500, 1)) * G._da_env(n, [(0, 0), (0.6, 0), (0.85, 1), (1.8, 0), (dur, 0)])
    G.add_at(x, G.norm(whoomp) + 0.12 * G.norm(air), 0.0, False)
    A._save(x, OUT / "drawn_door.ogg", -27.0, peak=0.6, fade_out=0.2)


def snd_l0sp_intercom_front_office() -> None:
    """Two takes of the PA asking for someone at the front office (our own words), through the
    same tinny horn and big room as event.intercom."""
    lines = (
        ("Attention, please. Would whoever is still on this floor, please come to the front office. "
         "The front office. Thank you.", "Microsoft Zira Desktop", -1),
        ("Front office, please. Somebody, to the front office. We are waiting for you.",
         "Microsoft David Desktop", -2),
    )
    for i, (text, voice_name, rate) in enumerate(lines, 1):
        rng = G.rng_for(40354 + i)
        x = _sapi_pa(text, voice_name, rate, rng, True)
        A._save(x, OUT / f"intercom_front_office{i}.ogg", -18.0, peak=0.89, fade_out=0.5)


# =============================================================================
# The Atrium and the Shaft Hall
# =============================================================================

def snd_l0sp_atrium_air() -> None:
    """61 s seamless loop, placed high in the atrium: cold air moving in a huge shaft. Low dark air
    surging slowly, the shaft's low resonances swelling and fading on their own like breath across
    a giant bottle, and now and then a thin cold whistle where the air finds a gap."""
    rng = G.rng_for(40361)
    T = 61.0
    n = int(T * SR)
    air = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 25, 320, 2) / np.sqrt(np.maximum(f, 20)))
    air *= A._slow(n, rng, 0.45, 0.012, 0.05)
    tones = np.zeros(n)
    for fc, q, a in ((41.0, 18.0, 1.0), (62.0, 22.0, 0.8), (83.0, 25.0, 0.55), (124.0, 30.0, 0.3)):
        bw = fc / q
        band = G.periodic_noise(n, rng, lambda f, fc=fc, bw=bw: np.exp(-0.5 * ((f - fc) / bw) ** 2))
        tones += a * band * np.clip(A._slow(n, rng, 0.9, 0.01, 0.04) - 0.3, 0, None)
    whistle = G.periodic_noise(n, rng, lambda f: np.exp(-0.5 * ((f - 1900) / 60) ** 2))
    whistle *= np.clip(A._slow(n, rng, 1.0, 0.012, 0.035) - 1.45, 0, None) / 0.55
    x = A._gain_to(air, -30.0) + A._gain_to(tones, -33.0) + A._gain_to(whistle, -50.0) * 6.0
    ir = _shaft_ir(rng, 3.5, 1.2, 2500)
    x = x + 0.3 * G.norm(G.circ_conv(x, ir)) * np.abs(x).max()
    A._save(x, OUT / "atrium_air.ogg", -30.0, peak=0.6, loop=True, compression=0.4)


def snd_l0sp_shaft_above() -> None:
    """Five things heard far up a shaft, all coming down a long hard flue (dark, with a flutter
    echo): 1 something heavy dragged a little way, 2 a duct knocking twice, 3 a long slow creak,
    4 grit trickling down the walls, 5 a far heavy thump."""
    for i in range(1, 6):
        rng = G.rng_for(40370 + i)
        if i == 1:
            d = rng.uniform(1.2, 1.8)
            x = G._da_stickslip(rng, d, [(0, 18), (d * 0.4, 30), (d, 12)], [rng.uniform(80, 110), rng.uniform(190, 260), 520],
                                [0.04, 0.02, 0.008], [1.0, 0.6, 0.3])
            x = G.norm(x) * G._da_env(len(x), [(0, 0), (0.2, 1), (d * 0.7, 0.8), (d, 0)])
        elif i == 2:
            x = np.zeros(int(1.4 * SR))
            for k, at in enumerate((0.02, rng.uniform(0.35, 0.6))):
                knock = G._da_hit(rng, [rng.uniform(70, 95), rng.uniform(180, 230), rng.uniform(480, 620)],
                                  [0.09, 0.05, 0.02], [1.0, 0.6, 0.35], noise_ms=2.0, noise_lp=1800)
                G.add_at(x, G.norm(knock) * (1.0 - 0.3 * k), at, False)
        elif i == 3:
            d = rng.uniform(2.0, 2.8)
            x = G._da_stickslip(rng, d, [(0, 6), (d * 0.5, 11), (d, 4)], [rng.uniform(140, 190), rng.uniform(330, 420), 760],
                                [0.03, 0.02, 0.006], [1.0, 0.5, 0.2])
            x = G.norm(x) * G._da_env(len(x), [(0, 0), (0.3, 1), (d * 0.8, 0.6), (d, 0)])
        elif i == 4:
            d = 2.6
            m = int(d * SR)
            x = np.zeros(m)
            tt = 0.0
            while tt < d - 0.1:
                k = int(rng.uniform(0.0004, 0.0015) * SR)
                dens = np.interp(tt, [0, 0.5, 1.6, d], [0.2, 1.0, 0.6, 0.0])
                G.add_at(x, rng.normal(size=k) * rng.uniform(0.2, 1.0) * dens, tt, False)
                tt += rng.exponential(0.004 / max(dens, 0.05))
            x = G.fft_filter(x, lambda f: G.bp_gain(f, 1200, 7000, 2))
            pebble = G.ring([rng.uniform(2800, 3600)], [0.01], [1.0], 0.06)
            G.add_at(x, 0.5 * G.norm(pebble) * np.abs(x).max(), 1.9, False)
        else:
            x = np.zeros(int(1.5 * SR))
            thump = G._da_hit(rng, [rng.uniform(45, 60), rng.uniform(110, 140)], [0.15, 0.06], [1.0, 0.5],
                              noise_ms=6.0, noise_lp=900)
            G.add_at(x, G.norm(thump), 0.02, False)
        x = _wet(G._da_far(x, rng, rng.uniform(1400, 2400), hp=40, air=0.0), _shaft_ir(rng, 4.0, 1.3, 2200), 0.8)
        A._save(x, OUT / f"shaft_above{i}.ogg", -29.0, peak=0.6, fade_out=0.5)


def snd_l0sp_tile_crack() -> None:
    """Two ceiling tiles working loose high up: the mineral fibre cracking against its metal grid,
    a short dry creak and a tick of the grid, with the shaft's echo."""
    for i in range(1, 3):
        rng = G.rng_for(40380 + i)
        x = np.zeros(int(1.0 * SR))
        crk = np.zeros(int(0.35 * SR))
        tt = 0.0
        while tt < 0.3:
            k = int(rng.uniform(0.0008, 0.004) * SR)
            G.add_at(crk, rng.normal(size=k) * rng.uniform(0.2, 1.0), tt, False)
            tt += rng.exponential(0.02)
        G.add_at(x, G.norm(G.fft_filter(crk, lambda f: G.bp_gain(f, 400, 4000, 2))), 0.02, False)
        creak = G._da_stickslip(rng, 0.35, [(0, 25), (0.35, 40)], [rng.uniform(600, 800), 1500], [0.01, 0.005], [1.0, 0.4])
        G.add_at(x, 0.5 * G.norm(creak), 0.1, False)
        grid = G._da_hit(rng, [rng.uniform(2800, 3400), rng.uniform(5000, 6000)], [0.01, 0.005], [1.0, 0.5], noise_ms=0.4)
        G.add_at(x, 0.4 * G.norm(grid), rng.uniform(0.35, 0.5), False)
        x = _wet(x, _shaft_ir(rng, 2.0, 0.6, 4000), 0.4)
        A._save(x, OUT / f"tile_crack{i}.ogg", -30.0, peak=0.6, fade_out=0.3)


def snd_l0sp_tile_fall() -> None:
    """Three ceiling tiles landing on the carpet: a flat, dull slap (light mineral fibre), the
    corner crumbling off in a few small knocks, then dust and crumbs sifting down for a moment.
    Soft on purpose: startling, never loud."""
    for i in range(1, 4):
        rng = G.rng_for(40390 + i)
        dur = 2.4
        n = int(dur * SR)
        x = np.zeros(n)
        m = int(0.12 * SR)
        tt = np.arange(m) / SR
        slap = G.fft_filter(rng.normal(size=m), lambda f: G.bp_gain(f, 180, 2600, 2)) * np.exp(-tt / 0.018)
        body = G.ring([rng.uniform(110, 160), rng.uniform(260, 340)], [0.03, 0.015], [1.0, 0.5], 0.12)
        G.add_at(x, G.norm(slap) + 0.6 * G.norm(body), 0.01, False)
        at = 0.01 + rng.uniform(0.05, 0.12)
        for _ in range(int(rng.integers(3, 6))):
            frag = G._da_hit(rng, [rng.uniform(600, 1200), rng.uniform(1800, 2600)], [0.006, 0.003], [1.0, 0.5],
                             noise_ms=0.6, noise_lp=4000)
            G.add_at(x, G.norm(frag) * rng.uniform(0.12, 0.35), at, False)
            at += rng.uniform(0.04, 0.16)
        dust = np.zeros(n)
        tt2 = 0.2
        while tt2 < 1.9:
            k = int(rng.uniform(0.0003, 0.001) * SR)
            dens = np.interp(tt2, [0.2, 0.5, 1.9], [1.0, 0.6, 0.0])
            G.add_at(dust, rng.normal(size=k) * rng.uniform(0.1, 1.0) * dens, tt2, False)
            tt2 += rng.exponential(0.006 / max(dens, 0.05))
        G.add_at(x, 0.08 * G.norm(G.fft_filter(dust, lambda f: G.bp_gain(f, 2000, 8000, 2))), 0.0, False)
        x = G._da_far(x, rng, 7000, hp=70, air=0.2, room=0.3)
        A._save(x, OUT / f"tile_fall{i}.ogg", -24.0, peak=0.6, fade_in=0.004, fade_out=0.2)


# =============================================================================
# Hidden rooms
# =============================================================================

def _note(f: float, dur: float, warp: np.ndarray, start: int, rng) -> np.ndarray:
    """One soft piano-ish note (a few slightly stretched partials, a felt attack), following the
    record's wow (`warp` = the warped clock of the whole loop, from sample `start` on)."""
    m = int(dur * SR)
    idx = (start + np.arange(m)) % len(warp)
    tau = warp[idx] - warp[start % len(warp)]
    tau = np.where(tau < 0, tau + warp[-1] + 1.0 / SR, tau)
    t = np.arange(m) / SR
    y = np.zeros(m)
    for k, a in ((1, 1.0), (2, 0.45), (3, 0.22), (4, 0.1), (5, 0.05)):
        y += a * np.sin(2 * np.pi * f * k * (1 + 0.0004 * k * k) * tau) * np.exp(-t / (1.4 / k ** 0.6))
    return y * np.clip(t / 0.006, 0, 1)


def snd_l0sp_radio() -> None:
    """18 bars of an old waltz (about 39 s, seamless) coming through a small radio: the record's
    wow, AM's narrow band and grit, the station drifting under static and back, a heterodyne
    whistle when it is weakest, and a voice reading the same message under the music."""
    rng = G.rng_for(40400 - 1)
    beat = 60.0 / 84.0
    bars = 18
    T = bars * 3 * beat
    n = int(T * SR)
    t = np.arange(n) / SR
    wow = 1 + 0.003 * np.sin(2 * np.pi * A._whole(0.55, T) * t)
    warp = np.cumsum(wow) / SR
    chords = {
        "Am": (110.0, (220.0, 261.63, 329.63)), "Dm": (146.83, (293.66, 349.23, 440.0)),
        "E": (82.41, (329.63, 415.30, 493.88)), "E7": (82.41, (293.66, 415.30, 493.88)),
        "F": (87.31, (261.63, 349.23, 440.0)), "C": (130.81, (261.63, 329.63, 392.0)),
    }
    prog = ["Am", "Am", "Dm", "Am", "E", "E7", "Am", "F", "Dm", "E", "Am", "Am", "F", "C", "Dm", "E7", "Am", "Am"]
    music = np.zeros(n)
    for b, name in enumerate(prog):
        bass, upper = chords[name]
        bar0 = b * 3 * beat
        G.add_at(music, 0.9 * _note(bass, 2.5, warp, int(bar0 * SR), rng), bar0, True)
        for beat_i in (1, 2):
            at = bar0 + beat_i * beat
            for f in upper:
                G.add_at(music, 0.22 * _note(f, 1.0, warp, int(at * SR), rng), at, True)
        top = upper[int(rng.integers(0, 3))] * 2
        G.add_at(music, 0.55 * _note(top, 2.2, warp, int(bar0 * SR), rng), bar0, True)
        if rng.uniform() < 0.5:
            f2 = upper[int(rng.integers(0, 3))] * 2
            G.add_at(music, 0.4 * _note(f2, 1.0, warp, int((bar0 + 2 * beat) * SR), rng), bar0 + 2 * beat, True)
    # The voice: a recorded message, read slowly, under the music.
    speech = G.norm(G._sapi("This is a recorded message. The building is closed. If you can hear this, "
                            "stay where you are. Someone will come for you.", "Microsoft David Desktop", -3))
    voice = np.zeros(n)
    G.add_at(voice, speech, T * 0.55, True)
    signal = np.clip(A._slow(n, rng, 0.8, 0.02, 0.07), 0.25, 1.0)
    static = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 300, 4500, 2))
    crack = np.zeros(n)
    for _ in range(int(T * 6)):
        k = int(rng.uniform(0.0005, 0.004) * SR)
        G.add_at(crack, rng.normal(size=k) * rng.uniform(0.2, 1.0) ** 2, rng.uniform(0, T), True)
    whistle = np.sin(2 * np.pi * np.cumsum(1300 + 180 * np.sin(2 * np.pi * A._whole(0.07, T) * t)) / SR)
    weak = np.clip((0.6 - signal) / 0.35, 0, 1)
    prog_x = (A._gain_to(music, -30.0) + A._gain_to(voice, -34.0)) * signal
    x = prog_x + A._gain_to(static, -40.0) * (0.5 + 1.2 * weak) + A._gain_to(crack, -44.0) + A._gain_to(whistle, -52.0) * weak
    x = G.fft_filter(x, lambda f: G.bp_gain(f, 280, 3200, 2) * (1 + 0.8 * np.exp(-((f - 900) / 300) ** 2)), circular=True)
    x = G.dist(G.norm(x) * 1.3, 1.8)
    ir = G.reverb_ir(rng, 0.6, 0.12, lp=4000, predelay=0.004)
    x = G.norm(x) + 0.25 * G.norm(G.circ_conv(x, ir))
    A._save(x, OUT / "radio.ogg", -31.0, peak=0.6, loop=True, compression=0.4)


def _clock_loop(rng, T: float, skip: int) -> np.ndarray:
    """A wall clock ticking once a second (tick and tock slightly different) for T seconds,
    seamless, with the beat after second `skip` missing and the next one a little late."""
    n = int(T * SR)
    x = np.zeros(n)
    for k in range(int(T)):
        if k == skip:
            continue
        late = 0.12 if k == skip + 1 else 0.0
        hi = 3600 if k % 2 == 0 else 3100
        pallet = G._da_hit(rng, [hi * rng.uniform(0.98, 1.02), hi * 1.7], [0.003, 0.0015], [1.0, 0.4], noise_ms=0.3, noise_lp=8000)
        case = G.ring([rng.uniform(780, 820) if k % 2 == 0 else rng.uniform(690, 720), 2300], [0.025, 0.01], [1.0, 0.3], 0.12)
        G.add_at(x, G.norm(pallet) * (1.0 if k % 2 == 0 else 0.8), k + late, True)
        G.add_at(x, 0.5 * G.norm(case) * (1.0 if k % 2 == 0 else 0.8), k + late, True)
        G.add_at(x, 0.25 * G.norm(pallet), k + late + 0.009, True)
    return x


def snd_l0sp_clock_tick() -> None:
    """30 s seamless loop: a wall clock in a sealed room, tick, tock; once a loop it skips a beat."""
    rng = G.rng_for(40401)
    x = _clock_loop(rng, 30.0, 22)
    ir = G.reverb_ir(rng, 0.5, 0.09, lp=6000, predelay=0.003)
    x = G.norm(x) + 0.3 * G.norm(G.circ_conv(x, ir))
    A._save(x, OUT / "clock_tick.ogg", -34.0, peak=0.5, loop=True)


def snd_l0sp_sealed_room() -> None:
    """37 s seamless loop: the stillness of a room shut for years. A very low, soft pressure in
    the air, the faint 60 Hz of a transformer behind the wall, and a thin ringing that comes and
    goes like the ringing in your own ears."""
    rng = G.rng_for(40402)
    T = 37.0
    n = int(T * SR)
    t = np.arange(n) / SR
    low = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 18, 110, 2)) * A._slow(n, rng, 0.3, 0.02, 0.06)
    hum = (np.sin(2 * np.pi * 60 * t) * 0.3 + np.sin(2 * np.pi * 120 * t) + 0.3 * np.sin(2 * np.pi * 180 * t + 1.0))
    ring_ = np.sin(2 * np.pi * A._whole(7200.0, T) * t) * np.clip(A._slow(n, rng, 1.0, 0.02, 0.06) - 1.2, 0, None)
    x = A._gain_to(low, -37.0) + A._gain_to(hum, -44.0) + A._gain_to(ring_, -56.0)
    A._save(x, OUT / "sealed_room.ogg", -37.0, peak=0.4, loop=True)


def snd_l0sp_hidden_office() -> None:
    """30 s seamless loop for the office behind the drawn door: its wall clock, and the faint hum
    and hiss of a PA microphone left switched on somewhere, which swells now and then as if
    someone leaned close to it, and never speaks."""
    rng = G.rng_for(40403)
    T = 30.0
    n = int(T * SR)
    t = np.arange(n) / SR
    clock = _clock_loop(rng, T, 17)
    hum, rasp = G._mains_buzz(2 * np.pi * 60 * t)
    pa = G.fft_filter(hum + 0.3 * rasp, lambda f: G.bp_gain(f, 100, 3000, 2), circular=True)
    hiss = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 500, 5000, 2))
    lean = np.clip(A._slow(n, rng, 1.0, 0.02, 0.05) - 1.3, 0, None) / 0.7
    breath = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 200, 1500, 2)) * lean
    x = (A._gain_to(clock, -34.0) + A._gain_to(pa, -40.0) * (1 + 1.5 * lean) + A._gain_to(hiss, -46.0)
         + A._gain_to(breath, -52.0) * 3.0)
    A._save(x, OUT / "hidden_office.ogg", -33.0, peak=0.5, loop=True)


def snd_l0sp_still_creak() -> None:
    """Three faint creaks for the room of chairs facing the wall: an office chair's seat and gas
    lift creaking as weight leaves it, a little fabric rustle, as if someone just stood up."""
    for i in range(1, 4):
        rng = G.rng_for(40410 + i)
        d = rng.uniform(0.45, 0.8)
        x = np.zeros(int((d + 0.6) * SR))
        creak = G._da_stickslip(rng, d, [(0, rng.uniform(30, 45)), (d * 0.6, rng.uniform(55, 80)), (d, 20)],
                                [rng.uniform(320, 420), rng.uniform(780, 950), 1800], [0.012, 0.006, 0.003], [1.0, 0.5, 0.2])
        creak = G.norm(creak) * G._da_env(len(creak), [(0, 0), (0.08, 1), (d * 0.7, 0.6), (d, 0)])
        G.add_at(x, creak, 0.02, False)
        m = int(0.35 * SR)
        rustle = G.fft_filter(rng.normal(size=m), lambda f: G.bp_gain(f, 1200, 5000, 2))
        rustle *= G._da_env(m, [(0, 0), (0.05, 1), (0.12, 0.3), (0.2, 0.7), (0.35, 0)]) * np.abs(G.smooth_jitter(rng, m, 40, 1.0))
        G.add_at(x, 0.4 * G.norm(rustle), 0.02 + d * rng.uniform(0.4, 0.8), False)
        x = G._da_far(x, rng, 5000, hp=120, air=0.15, room=0.3)
        A._save(x, OUT / f"still_creak{i}.ogg", -36.0, peak=0.4, fade_in=0.02, fade_out=0.15)


def snd_l0sp_pillar_settle() -> None:
    """Three takes of a pillar of the Peripheral Shift hall settling where it now stands: a soft,
    low drywall thud, a crumb of gypsum and the scuff of carpet under its foot."""
    for i in range(1, 4):
        rng = G.rng_for(40420 + i)
        x = np.zeros(int(0.9 * SR))
        thud = G._da_hit(rng, [rng.uniform(60, 85), rng.uniform(140, 190)], [0.06, 0.03], [1.0, 0.5], noise_ms=5.0, noise_lp=600)
        G.add_at(x, G.norm(thud), 0.01, False)
        m = int(0.08 * SR)
        crunch = G.fft_filter(rng.normal(size=m), lambda f: G.bp_gain(f, 300, 2200, 2)) * np.exp(-np.arange(m) / (0.02 * SR))
        G.add_at(x, 0.3 * G.norm(crunch), 0.015, False)
        m = int(0.3 * SR)
        scuff = G.fft_filter(rng.normal(size=m), lambda f: G.bp_gain(f, 700, 4000, 2)) * G._da_env(m, [(0, 0), (0.06, 1), (0.3, 0)])
        G.add_at(x, 0.25 * G.norm(scuff), rng.uniform(0.05, 0.12), False)
        x = G._da_far(x, rng, 3500, hp=40, air=0.25, room=0.4)
        A._save(x, OUT / f"pillar_settle{i}.ogg", -30.0, peak=0.5, fade_in=0.004, fade_out=0.15)


def snd_l0sp_pit_draught() -> None:
    """31.5 s seamless loop: cold air breathing slowly up out of deep shafts. Low, hollow air (the
    shafts' own resonances in it), swelling and sinking every six to nine seconds."""
    rng = G.rng_for(40431)
    T = 31.5
    n = int(T * SR)
    air = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 30, 500, 2) / np.sqrt(np.maximum(f, 20)))
    hollow = np.zeros(n)
    for fc, a in ((88.0, 1.0), (141.0, 0.6), (233.0, 0.3)):
        hollow += a * G.periodic_noise(n, rng, lambda f, fc=fc: np.exp(-0.5 * ((f - fc) / (fc / 14)) ** 2))
    breath = np.clip(A._slow(n, rng, 0.9, 0.11, 0.17), 0.15, None)
    x = (A._gain_to(air, -30.0) + A._gain_to(hollow, -34.0)) * breath
    A._save(x, OUT / "pit_draught.ogg", -30.0, peak=0.5, loop=True)


def snd_l0sp_red_loop() -> None:
    """The Red Rooms noticing you came round again: the tail of their klaxon hum, dragged down
    low and slowing like a tape running out, far too deep, fading into the room."""
    rng = G.rng_for(40441)
    import soundfile as sf
    src, sr = sf.read(str(OUT / "red_rooms_hum.ogg"))
    assert sr == SR, sr
    src = src.mean(axis=1) if src.ndim == 2 else src
    dur = 3.5
    n = int(dur * SR)
    t = np.arange(n) / SR
    rate = 0.62 - 0.2 * (t / dur) ** 1.5
    seg = R._resample_varying(np.tile(src, 4), rate)
    seg = np.pad(seg, (0, max(0, n - len(seg))))[:n]
    seg *= G._da_env(n, [(0, 0), (0.3, 1), (1.8, 0.7), (dur, 0)])
    seg = G.fft_filter(seg, lambda f: G.lp_gain(f, 900, 2) * G.hp_gain(f, 30, 2))
    x = _wet(seg, G.reverb_ir(rng, 2.0, 0.6, lp=1500, predelay=0.02), 0.5)
    A._save(x, OUT / "red_loop.ogg", -27.0, peak=0.6, fade_out=0.4)


TEXTURES: list = []
SOUNDS = [snd_l0sp_wave_pop, snd_l0sp_tube_strike, snd_l0sp_emergency_relay, snd_l0sp_blackout_wave,
          snd_l0sp_power_restore, snd_l0sp_district_down, snd_l0sp_office_alarm, snd_l0sp_beacon_motor,
          snd_l0sp_drawn_door, snd_l0sp_intercom_front_office, snd_l0sp_atrium_air, snd_l0sp_shaft_above,
          snd_l0sp_tile_crack, snd_l0sp_tile_fall, snd_l0sp_radio, snd_l0sp_clock_tick, snd_l0sp_sealed_room,
          snd_l0sp_hidden_office, snd_l0sp_still_creak, snd_l0sp_pillar_settle, snd_l0sp_pit_draught,
          snd_l0sp_red_loop]
