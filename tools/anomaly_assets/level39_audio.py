"""Level 39 "Megalophobia": the atmosphere's assets (sounds, and the far-facade/cloud pictures).

Level 39 is a foggy 1940s-ish night town shut inside a colossal factory-like superstructure.
Everything here is about SIZE and ECHO: the town is one gigantic enclosed hall, so far sounds come
back late and long; the marble tunnels underneath swallow sound instead (short and damped).

Sounds (src/main/resources/assets/backrooms/sounds/...):
  ambient/level39_town, level39_tunnel, level39_rain      seamless loops (client/Level39Audio)
  level39/echo_boom*, quake_rumble, window_rattle*         echo quakes (client/Level39QuakeEffects)
  level39/radio_garbled*                                   radios are useless here
  ambient/distant/level39/*                                far-off one-offs (client/audio/DistantAmbience)
Textures (src/main/resources/assets/backrooms/textures/environment/):
  l39_facade_far(_top)(_glow)(_soft).png   the facade seen from far away, with crisp and diffused
                                           window light (client/Level39FarFacade)
  l39_clouds.png                    the superstructure's cloud ceiling (client/Level39Sky)

Run only these with:  python tools/gen_assets.py level39_
Every generator uses its own fixed random seed (39000-39999), so re-running gives identical files.
"""
from __future__ import annotations

import numpy as np

import gen_assets as G
from anomaly_assets import level9 as L9

SR = G.SR


def _snd(*parts: str):
    path = G.SND
    for p in parts[:-1]:
        path = path / p
    return path / f"{parts[-1]}.ogg"


def _env(name: str):
    return G.TEX / "environment" / f"{name}.png"


# =============================================================================
# Shared sound helpers
# =============================================================================

def _save_ogg(x, path, peak: float = 0.89, loop: bool = False, fade_in: float = 0.003, fade_out: float = 0.02) -> None:
    """Like G.save_ogg, but written in one-second blocks: libsndfile's Vorbis encoder overflows
    its stack when handed a long clip in one go (see level0._save_long_loop)."""
    import soundfile as sf
    x = np.asarray(x, float)
    x = x - x.mean()
    if not loop:
        n = len(x)
        fi = max(1, int(fade_in * SR))
        fo = max(1, int(fade_out * SR))
        x[:fi] *= np.linspace(0, 1, fi)
        x[n - fo:] *= np.linspace(1, 0, fo)
    x = G.norm(x, peak)
    path.parent.mkdir(parents=True, exist_ok=True)
    gain = 1.0
    extra = {"compression_level": 0.0} if loop else {}
    for _ in range(6):
        with sf.SoundFile(str(path), "w", SR, 1, format="OGG", subtype="VORBIS", **extra) as out:
            y = (x * gain).astype(np.float32)
            for i in range(0, len(y), SR):
                out.write(y[i:i + SR])
        decoded = np.abs(sf.read(str(path))[0]).max()
        if decoded <= max(peak, 0.9) + 0.02:
            break
        gain *= peak / decoded


def _far(x, lp, hp=30.0) -> np.ndarray:
    """Distance filter: far-away sounds lose their highs (and a little of their deepest lows)."""
    return G.fft_filter(x, lambda f: G.lp_gain(f, lp, 2) * G.hp_gain(f, hp, 1))


def _hall(rng, x, decay=3.2, length=7.0, lp=2200.0, wet=0.8, slaps=((0.42, 0.5), (0.95, 0.35), (1.7, 0.22))):
    """The town's echo: a long, dark hall reverb plus a few distinct slap-backs off far facades.

    The slaps are what make it sound like a colossal ENCLOSED space rather than a big room: the
    sound clearly comes back from walls hundreds of metres away, then dissolves into the tail."""
    n = len(x)
    ir = G.reverb_ir(rng, length, decay, lp=lp, predelay=0.06)
    out = np.concatenate([x, np.zeros(int(length * SR))])
    tail = G.conv(x, ir)[:len(out)]
    tail = np.pad(tail, (0, len(out) - len(tail)))
    out = out + wet * G.norm(tail) * np.abs(x).max()
    for delay, gain in slaps:
        echo = _far(x, lp * 0.7)
        G.add_at(out, gain * echo, delay, False)
    return out


def _tunnel_room(rng, x, wet=0.25):
    """The marble tunnels damp sound: a short, soft, dull room, no long tail."""
    n = len(x)
    ir = G.reverb_ir(rng, 0.6, 0.12, lp=1800, predelay=0.008)
    return x + wet * G.norm(G.conv(x, ir)[:n]) * np.abs(x).max()


def _whole(f, T):
    """A frequency rounded to a whole number of cycles over T seconds (keeps loops seamless)."""
    return max(1.0, round(f * T)) / T


# =============================================================================
# Ambient loops (every part periodic over the loop length: seamless)
# =============================================================================

def snd_level39_town() -> None:
    """24 s loop: the town inside the superstructure. Quiet and huge.

    A very low industrial thrum (the superstructure's machinery, felt more than heard), a soft
    sub-bass air mass, a faint whistle of wind in the overhead wires coming and going, and now
    and then a far, soft thud that rolls back off the facades."""
    rng = G.rng_for(39001)
    T = 24.0
    n = int(T * SR)
    t = np.arange(n) / SR
    # Thrum: a 29 Hz machine fundamental with a few harmonics, slowly breathing.
    f0 = _whole(29.0, T)
    thrum = sum(a * np.sin(2 * np.pi * f0 * k * t + 0.9 * k) for k, a in ((1, 1.0), (2, 0.55), (3, 0.3), (4, 0.12), (6, 0.05)))
    thrum = thrum * G.drift(n, rng, 0.25, 0.04, 0.2)
    air = G.periodic_noise(n, rng, lambda f: G.hp_gain(f, 15, 2) * G.lp_gain(f, 140, 3) / np.maximum(f, 15) ** 0.5)
    air = air * G.drift(n, rng, 0.3, 0.05, 0.25)
    # Wind in the wires: narrow whistles (aeolian tones) that swell and fade, plus a breath of
    # band-limited wind noise under them.
    wires = np.zeros(n)
    for fw, depth in ((_whole(612.0, T), 0.9), (_whole(917.0, T), 0.7), (_whole(1336.0, T), 0.45)):
        # Whole-cycle tones under a slow, periodic swell: they come and go, and the loop stays seamless.
        swell = G.periodic_noise(n, rng, lambda f: ((f > 0.03) & (f < 0.18)).astype(float))
        swell = np.clip(swell / np.abs(swell).max(), 0, 1) ** 2
        wires += depth * np.sin(2 * np.pi * fw * t + rng.uniform(0, 6.28)) * swell
    wind = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 250, 1400, 2)) * G.drift(n, rng, 0.6, 0.03, 0.15)
    # Far thuds: soft, low, each followed by its echoes (circular, so the loop stays seamless).
    thuds = np.zeros(n)
    for start in (3.2, 13.7, 19.9):
        m = int(1.2 * SR)
        tt = np.arange(m) / SR
        hit = np.sin(2 * np.pi * np.cumsum(38 + 30 * np.exp(-tt / 0.08)) / SR) * np.exp(-tt / 0.35)
        hit = _far(hit, 220)
        for delay, gain in ((0.0, 1.0), (0.55, 0.45), (1.3, 0.28), (2.4, 0.16)):
            G.add_at(thuds, gain * hit * rng.uniform(0.8, 1.0), start + delay, True)
    thuds = G.circ_conv(thuds, G.reverb_ir(rng, 4.0, 1.6, lp=400))
    x = (G.norm(thrum) * 0.55 + G.norm(air) * 0.5 + G.norm(wires) * 0.05 + G.norm(wind) * 0.06
         + G.norm(thuds) * 0.22)
    _save_ogg(x, _snd("ambient", "level39_town"), peak=0.36, loop=True)


def snd_level39_tunnel() -> None:
    """16 s loop: the marble tunnels. Still water lapping softly against the tiled rims, a few
    drips into the water, all of it close and damped (the marble swallows sound)."""
    rng = G.rng_for(39002)
    T = 16.0
    n = int(T * SR)
    # Lapping: low band noise in slow, irregular little waves.
    lap = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 120, 700, 2))
    waves = G.periodic_noise(n, rng, lambda f: ((f > 0.25) & (f < 1.1)).astype(float))
    waves = np.clip(waves / np.abs(waves).max(), 0, 1) ** 1.5
    lap = lap * (0.25 + waves)
    # Small bubbles where the ripples meet the rim (placed circularly).
    bubbles = np.zeros(n)
    for _ in range(36):
        f0 = float(np.exp(rng.uniform(np.log(500), np.log(1600))))
        b = _bubble(rng, f0)
        G.add_at(bubbles, b * rng.uniform(0.2, 0.7), rng.uniform(0, T), True)
    # Drips: a clear "plip" now and then.
    drips = np.zeros(n)
    for start in (1.7, 5.9, 6.4, 11.2, 14.6):
        G.add_at(drips, G.norm(G._water_bubble(rng, rng.uniform(900, 1500), 0.09)) * rng.uniform(0.6, 1.0), start, True)
    room = G.periodic_noise(n, rng, lambda f: G.hp_gain(f, 30, 2) * G.lp_gain(f, 300, 3))
    x = G.norm(lap) * 0.5 + G.norm(bubbles) * 0.25 + G.norm(drips) * 0.35 + G.norm(room) * 0.15
    # A short, damped room (circular so it wraps round the loop seam).
    x = x + 0.2 * G.norm(G.circ_conv(x, G.reverb_ir(rng, 0.5, 0.1, lp=1600, predelay=0.006))) * np.abs(x).max()
    _save_ogg(x, _snd("ambient", "level39_tunnel"), peak=0.45, loop=True)


def _bubble(rng, f0):
    """One small bubble (Minnaert ring, rising slightly in pitch as it dies)."""
    decay = 0.043 * f0 + 0.0014 * f0 ** 1.5
    dur = min(6.0 / decay, 0.2)
    t = np.arange(int(dur * SR)) / SR
    f = f0 * (1.0 + 0.25 * decay * t)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-decay * t) * G.smoothstep(0, 0.0006, t)


def snd_level39_rain() -> None:
    """20 s loop: rain falling inside the superstructure. A wide soft hiss over the whole
    enormous space, the low roar of it hitting thousands of roofs and the facade glass far
    away, a nearer patter of drops, and gutters trickling now and then."""
    rng = G.rng_for(39003)
    T = 20.0
    n = int(T * SR)
    t = np.arange(n) / SR
    hiss = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 500, 7000, 2) / np.maximum(f, 200) ** 0.3)
    hiss = hiss * G.drift(n, rng, 0.15, 0.05, 0.3)
    roar = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 60, 450, 2)) * G.drift(n, rng, 0.2, 0.03, 0.15)
    # Patter: many tiny drops (circular placement).
    patter = np.zeros(n)
    count = int(T * 140)
    m = int(0.012 * SR)
    tt = np.arange(m) / SR
    for start in rng.uniform(0, T, count):
        f = rng.uniform(2500, 6500)
        drop = np.sin(2 * np.pi * f * tt) * np.exp(-tt / rng.uniform(0.0015, 0.004))
        G.add_at(patter, drop * rng.uniform(0.1, 1.0) ** 2, start, True)
    # Gutters: a trickle that comes and goes.
    trickle = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 900, 3200, 2))
    trickle *= np.clip(G.periodic_noise(n, rng, lambda f: ((f > 0.05) & (f < 0.3)).astype(float)), 0, None)
    x = G.norm(hiss) * 0.6 + G.norm(roar) * 0.45 + G.norm(patter) * 0.25 + G.norm(trickle) * 0.08
    # The hall's echo smooths it all into one huge sound (circular for the seam).
    x = x + 0.35 * G.norm(G.circ_conv(x, G.reverb_ir(rng, 3.0, 1.0, lp=3000, predelay=0.05))) * np.abs(x).max()
    _save_ogg(x, _snd("ambient", "level39_rain"), peak=0.5, loop=True)


# =============================================================================
# Echo quakes
# =============================================================================

def snd_level39_echo_boom() -> None:
    """Two ~8 s takes: a colossal boom far up in the superstructure, like a huge steel door or
    girder slamming: a deep thump, a hollow iron clang, and the long rolling echo of the hall."""
    for i, (seed, modes) in enumerate(((39010, (61, 97, 148, 233, 341)), (39011, (55, 88, 131, 207, 318))), 1):
        rng = G.rng_for(seed)
        dur = 1.6
        tt = G.t_axis(dur)
        body = np.sin(2 * np.pi * np.cumsum(26 + 44 * np.exp(-tt / 0.07)) / SR) * np.exp(-tt / 0.5)
        thud = G.fft_filter(rng.normal(size=len(tt)), lambda f: G.lp_gain(f, 300, 2)) * np.exp(-tt / 0.05)
        clang = G.ring(list(modes), [1.1, 0.9, 0.7, 0.5, 0.35], [1.0, 0.7, 0.5, 0.35, 0.2], dur)
        hit = G.norm(body) + 0.5 * G.norm(thud) + 0.4 * G.norm(clang)
        hit = _far(hit, 900, 20)
        # Round off the very first transient (gentle saturation): it is what clipped when the
        # boom, its echoes and the town's reverb piled up. The weight stays in the low end.
        hit = G.dist(G.norm(hit), 1.6)
        x = _hall(rng, hit, decay=2.4, length=6.5, lp=700, wet=0.9,
                  slaps=((0.48, 0.55), (1.05, 0.4), (1.8, 0.3), (2.9, 0.18)))
        _save_ogg(x, _snd("level39", f"echo_boom{i}"), peak=0.7, fade_in=0.003, fade_out=1.5)


def snd_level39_quake_rumble() -> None:
    """~11 s: the magnitude-5 quake. A deep rumble that builds as the echoes feed it, the ground
    groaning, wood frames of a hundred houses creaking, grit trickling, then settling."""
    rng = G.rng_for(39012)
    dur = 11.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    env = np.interp(t, [0, 1.0, 4.0, 6.5, 9.5, dur], [0, 0.25, 1.0, 0.9, 0.2, 0]) * (1 + G.smooth_jitter(rng, n, 3, 0.3))
    rumble = G.fft_filter(rng.normal(size=n), lambda f: G.lp_gain(f, 65, 3) * G.hp_gain(f, 14, 2))
    fc = np.interp(t, [0, 3.0, 5.5, 8.0, dur], [50, 90, 115, 80, 55]) * (1 + G.smooth_jitter(rng, n, 1.5, 0.15))
    groan = G.svf(rng.normal(size=n), fc, 8.0)
    creaks = np.zeros(n)
    for start in np.sort(rng.uniform(2.0, 8.5, 9)):
        c = G._creak(rng, rng.uniform(0.4, 1.0), 18, 45, [rng.uniform(180, 320), rng.uniform(500, 800), rng.uniform(1100, 1600)])
        G.add_at(creaks, G.norm(c) * rng.uniform(0.3, 1.0), start, False)
    dens = 30 * np.interp(t, [0, 2.5, 5.0, 8.0, dur], [0, 0.6, 1.0, 0.3, 0])
    times = t[rng.random(n) < dens / SR]
    debris = G.fft_filter(G.clicks(rng, times, dur, 700, 2600, 0.002, 0.008, amp_pow=2.0), lambda f: G.lp_gain(f, 3500, 2))[:n]
    x = G.norm(rumble) * env + 0.5 * G.norm(groan) * env + 0.18 * G.norm(creaks) + 0.14 * G.norm(debris)
    x = x + 0.4 * G.norm(G.conv(x, G.reverb_ir(rng, 3.0, 1.0, lp=500))[:n]) * np.abs(x).max()
    x = G.dist(G.norm(x), 1.4)   # a soft limit on the peaks; the rumble's body is untouched
    _save_ogg(x, _snd("level39", "quake_rumble"), peak=0.7, fade_in=0.3, fade_out=1.5)


def snd_level39_window_rattle() -> None:
    """Two ~3.5 s takes: every window pane in town rattling in its frame at the quake's peak:
    fast irregular glassy ticks swelling and fading, the panes ringing faintly, a sash knocking."""
    for i, seed in enumerate((39014, 39015), 1):
        rng = G.rng_for(seed)
        dur = 3.5
        n = int(dur * SR)
        t = np.arange(n) / SR
        dens = 220 * np.interp(t, [0, 0.4, 1.6, 2.6, dur], [0, 0.8, 1.0, 0.4, 0]) * (1 + G.smooth_jitter(rng, n, 6, 0.5))
        times = t[rng.random(n) < np.clip(dens, 0, None) / SR]
        ticks = G.clicks(rng, times, dur, 2200, 5200, 0.001, 0.004, amp_pow=1.5)
        panes = np.zeros(n)
        for start in rng.uniform(0.2, 2.4, 6):
            f = rng.uniform(1800, 3600)
            G.add_at(panes, G.ring([f, f * 2.32], [0.18, 0.1], [1, 0.4], 0.5) * rng.uniform(0.2, 0.6), start, False)
        knocks = np.zeros(n)
        for start in np.sort(rng.uniform(0.3, 2.6, 7)):
            G.add_at(knocks, G.ring([rng.uniform(160, 260), rng.uniform(420, 600)], [0.03, 0.02], [1, 0.5], 0.12), start, False)
        x = 0.6 * G.norm(ticks) + 0.25 * G.norm(panes) + 0.35 * G.norm(knocks)
        x = _hall(rng, x, decay=1.4, length=3.0, lp=3500, wet=0.45, slaps=((0.5, 0.25),))
        _save_ogg(x, _snd("level39", f"window_rattle{i}"), peak=0.6, fade_out=0.8)


def snd_level39_radio_garbled() -> None:
    """Two ~4.5 s takes: a radio in Level 39. Something that might be a voice, smeared, crushed
    and swallowed by its own echoes until nothing is left to understand."""
    for i, (seed, f0) in enumerate(((39016, 104.0), (39017, 132.0)), 1):
        rng = G.rng_for(seed)
        dur = 4.5
        n = int(dur * SR)
        t = np.arange(n) / SR
        voice, _ = L9._babble(rng, 2.6, f0=f0, fscale=1.0, start=0.1, end_pad=0.1, reverse=1)
        sig = np.zeros(n)
        G.add_at(sig, G.norm(voice), 0.15, False)
        # Warble (the station drifting) and a crushed, band-limited radio sound.
        warp = 1 + 0.5 * G.smooth_jitter(rng, n, 3, 1.0)
        sig = sig * np.clip(warp, 0.2, 1.6)
        static = rng.normal(size=n) * 0.25 + 0.3 * G.norm(G._crackle(rng, dur, 60, f_lo=1500))
        x = G.norm(sig) + static * (0.3 + 0.3 * G.smoothstep(0.5, 1.0, G.smooth_jitter(rng, n, 2, 1.0)))
        x = G.fft_filter(x, lambda f: G.bp_gain(f, 350, 2600, 3))
        x = G.bitcrush(G.dist(G.norm(x), 2.5), 5, 3)
        # Its own echoes pile up on top of it: the hall answers the radio.
        echoes = np.zeros(n)
        for delay, gain in ((0.23, 0.6), (0.51, 0.45), (0.86, 0.33), (1.3, 0.22)):
            G.add_at(echoes, gain * G.fft_filter(x, lambda f: G.lp_gain(f, 1600, 2)), delay, False)
        x = x + echoes[:n]
        x = x + 0.5 * G.norm(G.conv(x, G.reverb_ir(rng, 2.0, 0.8, lp=1800))[:n]) * np.abs(x).max()
        _save_ogg(x, _snd("level39", f"radio_garbled{i}"), peak=0.5, fade_in=0.01, fade_out=0.6)


# =============================================================================
# Far-off one-offs (DistantAmbience: 48-block range, played 14-40 blocks away)
# =============================================================================

def _dist(name: str, i: int):
    return _snd("ambient", "distant", "level39", f"{name}{i}")


def snd_level39_distant_groan() -> None:
    """Three ~6 s takes: the superstructure groaning, a vast steel frame shifting under its own
    weight: a low moan sliding in pitch, a slow stick-slip creak, the hall carrying it."""
    for i, seed in enumerate((39020, 39021, 39022), 1):
        rng = G.rng_for(seed)
        dur = 3.6
        n = int(dur * SR)
        t = np.arange(n) / SR
        fc = np.interp(t, [0, 1.2, 2.6, dur], [rng.uniform(45, 60), rng.uniform(75, 95), rng.uniform(60, 80), rng.uniform(40, 55)])
        moan = G.svf(rng.normal(size=n), fc, 14.0)
        moan2 = G.svf(rng.normal(size=n), fc * 2.03, 12.0)
        creak = G._creak(rng, dur, 6, 14, [rng.uniform(140, 200), rng.uniform(330, 420), rng.uniform(700, 900)])
        env = G.smoothstep(0, 0.8, t) * G.smoothstep(dur, dur - 1.0, t)
        x = (G.norm(moan) + 0.4 * G.norm(moan2)) * env + 0.25 * G.norm(creak)
        x = _far(x, 900)
        x = _hall(rng, x, decay=2.6, length=4.0, lp=800, wet=0.8, slaps=((0.6, 0.3), (1.4, 0.18)))
        _save_ogg(x, _dist("superstructure_groan", i), peak=0.7, fade_in=0.2, fade_out=1.2)


def snd_level39_distant_metal_slam() -> None:
    """Two ~7 s takes: a heavy metal door slamming somewhere deep inside the superstructure, and
    its echo coming back off facade after facade for a long time."""
    for i, seed in enumerate((39023, 39024), 1):
        rng = G.rng_for(seed)
        dur = 1.4
        tt = G.t_axis(dur)
        crack = G.fft_filter(rng.normal(size=len(tt)), lambda f: G.bp_gain(f, 200, 4000, 2)) * np.exp(-tt / 0.02)
        modes = [rng.uniform(170, 200), rng.uniform(390, 440), rng.uniform(660, 720), rng.uniform(1050, 1200)]
        ring = G.ring(modes, [0.6, 0.45, 0.3, 0.2], [1.0, 0.7, 0.45, 0.3], dur)
        thump = np.sin(2 * np.pi * np.cumsum(40 + 50 * np.exp(-tt / 0.05)) / SR) * np.exp(-tt / 0.2)
        hit = 0.7 * G.norm(crack) + 0.5 * G.norm(ring) + 0.6 * G.norm(thump)
        hit = _far(hit, 2500)
        x = _hall(rng, hit, decay=2.8, length=5.5, lp=1200, wet=0.95,
                  slaps=((0.62, 0.5), (1.31, 0.38), (2.1, 0.26), (3.0, 0.16)))
        _save_ogg(x, _dist("metal_slam", i), peak=0.75, fade_in=0.002, fade_out=1.5)


def _bark(rng, f_start, f_end, dur=0.17):
    """One dog bark: a rough voiced burst with a falling pitch and dog-ish formants."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    f0 = np.interp(t, [0, dur * 0.3, dur], [f_start * 0.9, f_start, f_end]) * (1 + G.smooth_jitter(rng, n, 30, 0.05))
    v = G.voice(f0, [(700, 180, 1.0), (1250, 260, 0.7), (2600, 500, 0.3)], rng, nh=30)
    noise = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 600, 3000, 2))
    e = G.smoothstep(0, 0.012, t) * np.exp(-np.maximum(t - 0.04, 0) / 0.06)
    return G.dist(G.norm(G.norm(v) + 0.35 * G.norm(noise)) * e, 2.0)


def snd_level39_distant_dog_bark() -> None:
    """Three ~5 s takes: a dog barking somewhere in town; each bark comes back off the facades."""
    for i, (seed, barks) in enumerate(((39025, 2), (39026, 3), (39027, 1)), 1):
        rng = G.rng_for(seed)
        dur = 1.4
        x = np.zeros(int(dur * SR))
        at = 0.05
        for _ in range(barks):
            G.add_at(x, _bark(rng, rng.uniform(430, 520), rng.uniform(260, 320)), at, False)
            at += rng.uniform(0.32, 0.45)
        x = _far(x, 2200, 150)
        x = _hall(rng, x, decay=2.0, length=4.0, lp=1600, wet=0.7, slaps=((0.55, 0.42), (1.2, 0.26), (2.0, 0.14)))
        _save_ogg(x, _dist("dog_bark", i), peak=0.6, fade_in=0.002, fade_out=1.0)


def snd_level39_distant_screen_door() -> None:
    """Two ~4 s takes: a screen door swings on its spring and slaps shut, rattling once."""
    for i, seed in enumerate((39028, 39029), 1):
        rng = G.rng_for(seed)
        dur = 1.6
        n = int(dur * SR)
        x = np.zeros(n)
        spring = G._creak(rng, 0.5, 30, 70, [rng.uniform(1400, 1800), rng.uniform(2600, 3200), 4100])
        G.add_at(x, 0.35 * G.norm(spring), 0.0, False)
        for at, g in ((0.55, 1.0), (0.66, 0.35), (0.74, 0.15)):
            tt = G.t_axis(0.25)
            slap = G.fft_filter(rng.normal(size=len(tt)), lambda f: G.bp_gain(f, 300, 3500, 2)) * np.exp(-tt / 0.015)
            wood = G.ring([rng.uniform(250, 320), rng.uniform(640, 760)], [0.05, 0.03], [1, 0.5], 0.25)
            G.add_at(x, g * (G.norm(slap) + 0.6 * G.norm(wood)), at, False)
        x = _far(x, 2800, 100)
        x = _hall(rng, x, decay=1.8, length=3.0, lp=1800, wet=0.6, slaps=((0.5, 0.3), (1.1, 0.18)))
        _save_ogg(x, _dist("screen_door", i), peak=0.6, fade_out=0.8)


def snd_level39_distant_wind_chime() -> None:
    """Two ~6 s takes: a wind chime on a porch somewhere, stirred by a draught that shouldn't exist."""
    scale = [523.3, 587.3, 659.3, 784.0, 880.0, 1046.5]
    for i, seed in enumerate((39030, 39031), 1):
        rng = G.rng_for(seed)
        dur = 3.5
        x = np.zeros(int(dur * SR))
        for at in np.sort(rng.uniform(0.0, 2.2, int(rng.integers(5, 9)))):
            f = float(rng.choice(scale))
            tone = G.ring([f, f * 2.76, f * 5.4], [1.2, 0.5, 0.2], [1.0, 0.35, 0.12], 2.0)
            G.add_at(x, tone * rng.uniform(0.3, 1.0), at, False)
        x = _far(x, 5000, 200)
        x = _hall(rng, x, decay=1.8, length=3.0, lp=3000, wet=0.5, slaps=((0.5, 0.2),))
        _save_ogg(x, _dist("wind_chime", i), peak=0.5, fade_out=1.0)


def snd_level39_distant_telephone() -> None:
    """Two ~7 s takes: an old bell telephone ringing inside an empty house, twice, then stopping."""
    for i, seed in enumerate((39032, 39033), 1):
        rng = G.rng_for(seed)
        dur = 6.0
        n = int(dur * SR)
        x = np.zeros(n)
        bells = [rng.uniform(1180, 1260), rng.uniform(1480, 1560)]
        for ring_start in (0.05, 3.0):
            # The striker hits the two bells about 20 times a second for 1.6 s.
            for k in range(32):
                f = bells[k % 2]
                tone = G.ring([f, f * 2.41, f * 3.9], [0.25, 0.12, 0.06], [1.0, 0.4, 0.15], 0.3)
                G.add_at(x, tone * (0.8 + 0.2 * rng.random()), ring_start + k * 0.05, False)
        # Through the house walls: dull and boxy.
        x = G.fft_filter(x, lambda f: G.lp_gain(f, 1600, 2) * G.hp_gain(f, 300, 1))
        x = x + 0.3 * G.norm(G.conv(x, G.reverb_ir(rng, 0.8, 0.25, lp=1500))[:n]) * np.abs(x).max()
        x = _hall(rng, x, decay=1.6, length=2.5, lp=1300, wet=0.4, slaps=((0.5, 0.18),))
        _save_ogg(x, _dist("telephone", i), peak=0.5, fade_out=0.8)


def snd_level39_distant_ash_rattle() -> None:
    """Two ~3.5 s takes: a gust of coal ash rattling against the facade glass high above (canon:
    the windows show coal traces): a fine gritty hiss of tiny ticks on glass, swelling and dying."""
    for i, seed in enumerate((39039, 39040), 1):
        rng = G.rng_for(seed)
        dur = 2.2
        n = int(dur * SR)
        t = np.arange(n) / SR
        dens = 900 * np.interp(t, [0, 0.5, 1.2, dur], [0, 1.0, 0.6, 0])
        times = t[rng.random(n) < dens / SR]
        grit = G.clicks(rng, times, dur, 4000, 9000, 0.0004, 0.0012, amp_pow=2.5)
        gust = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 800, 4000, 2)) * np.interp(t, [0, 0.6, dur], [0, 1, 0])
        glass = G.ring([2900, 4700], [0.15, 0.08], [1, 0.4], dur) * G.smoothstep(0, 0.4, t)
        x = 0.7 * G.norm(grit) + 0.2 * G.norm(gust) + 0.05 * G.norm(glass)
        x = _far(x, 6000, 300)
        x = _hall(rng, x, decay=1.4, length=2.5, lp=3500, wet=0.45, slaps=((0.5, 0.2),))
        _save_ogg(x, _dist("ash_rattle", i), peak=0.45, fade_in=0.05, fade_out=0.8)


def snd_level39_distant_tunnel_drip() -> None:
    """Three ~1.5 s takes: a drop falling into the still tunnel water, damped by the marble."""
    for i, seed in enumerate((39041, 39042, 39043), 1):
        rng = G.rng_for(seed)
        dur = 0.9
        x = np.zeros(int(dur * SR))
        G.add_at(x, G.norm(G._water_bubble(rng, rng.uniform(800, 1400), 0.11)), 0.02, False)
        if i != 2:
            G.add_at(x, 0.4 * G.norm(G._water_bubble(rng, rng.uniform(1500, 2200), 0.06)), rng.uniform(0.25, 0.45), False)
        x = _tunnel_room(rng, x, wet=0.3)
        _save_ogg(x, _dist("tunnel_drip", i), peak=0.5, fade_out=0.2)


def snd_level39_distant_tunnel_splash() -> None:
    """Two ~3.5 s takes: something moving through the water far down the tunnel: a couple of
    slow wading surges and gurgles, then stillness."""
    for i, seed in enumerate((39044, 39045), 1):
        rng = G.rng_for(seed)
        dur = 3.0
        n = int(dur * SR)
        x = np.zeros(n)
        for start in np.sort(rng.uniform(0.0, 1.8, int(rng.integers(2, 4)))):
            x += 0.8 * G._water_noise(rng, n, start, 0.08, 0.25, 200, 1400, gurgle=0.8)
            x += G._water_bubbles(rng, n, 14, start + 0.05, 0.4, 250, 1100, 0.6)
        x = _far(x, 1500, 80)
        x = _tunnel_room(rng, x, wet=0.35)
        _save_ogg(x, _dist("tunnel_splash", i), peak=0.55, fade_in=0.01, fade_out=0.4)


# =============================================================================
# Textures: the far facade and the cloud ceiling
# =============================================================================

BAY = 6
STOREY = 7
BAYS = 16          # bays across one picture
STOREYS = 8        # storeys up one body picture
PX_PER_BLOCK = 10  # about 10 px per block, so a window is ~30 x 50 px before mipmapping
# Colours match the real (darkened) facade blocks: sooty red-brown brick, mid-dark grey granite,
# near-black window frames. The game darkens the brick further toward the top (vertex colour), so
# here it is the colour low on the wall. The glass in this picture is only the glass's own grey:
# the windows' brightness comes from the light layers added on top (crisp near, diffused far),
# so far away the light can bloom into the fog instead of staying a sharp bright rectangle.
GLASS = np.array([118.0, 126.0, 136.0])
BRICK = np.array([46.0, 31.0, 26.0])        # a touch darker than the low brick #3A2620: the fog lifts it
PILASTER = np.array([52.0, 35.0, 29.0])
GRANITE = np.array([84.0, 84.0, 87.0])      # courses and sills #5A5A5C, in shadow
FRAME = np.array([24.0, 24.0, 26.0])        # frames and muntins #1C1C1E


def _window_values(rng, count):
    """Brightness of each window: most evenly bright (the gallery behind is clean and lit), some
    a touch dimmer, a fair few clearly dim, and now and then a dark one (a blind or a dead panel)."""
    v = rng.uniform(0.8, 1.0, count)
    v[rng.random(count) < 0.15] *= 0.7
    v[rng.random(count) < 0.07] *= 0.45
    v[rng.random(count) < 0.04] = 0.06
    return v


def _facade(rng, width_px, height_px, rows, top: bool):
    """Paints one facade picture at 4x and box-downsamples it (sharp, anti-aliased edges).

    rows: block rows from the top, each a kind: 'glass', 'arch', 'spandrel', 'course', 'cornice',
    'parapet'. Returns (albedo RGB, window light mask) at the final size.

    Made to read at 150-500 blocks: big simple shapes with strong contrast. Dark piers between the
    windows, a dark spandrel + grey string course between the storeys, a heavy dark frame round
    each window, and only faint muntins (fine high-contrast lines turn into a wire mesh far away)."""
    ss = 4
    W, H = width_px * ss, height_px * ss
    bx = (np.arange(W) + 0.5) / W * (BAYS * BAY)          # block coordinate across
    by = (np.arange(H) + 0.5) / H * len(rows)              # block row from the top
    BX, BY = np.meshgrid(bx, by)
    col = np.floor(BX).astype(int) % BAY
    bay = np.floor(BX / BAY).astype(int)
    row = np.clip(np.floor(BY).astype(int), 0, len(rows) - 1)
    fy = BY - np.floor(BY)
    kinds = np.array(rows)[row]
    # Brick everywhere first: only broad, soft variation (patches of soot a few bays across). No
    # mortar lines or fine grain: seen from 150+ blocks those turn into a glittering streaky noise.
    noise = G.fnoise(rng, (H, W), beta=2.4, kmin=2, kmax=48)
    streaks = G.fnoise(rng, (H, W), beta=2.4, aniso=(1.0, 6.0), kmin=2, kmax=40)
    img = np.empty((H, W, 3))
    img[:] = BRICK
    img *= (1.0 + 0.05 * noise - 0.05 * np.clip(streaks, 0, None))[..., None]
    # Pilasters (bay column 0) stand a little proud: lighter face, a shadow down one side.
    pil = col == 0
    img[pil] = PILASTER * (1.0 + 0.05 * noise[pil])[..., None]
    img[(col == 1) & ((BX % 1.0) < 0.25)] *= 0.7
    # Courses: granite string course, cornice, parapet.
    for kind, colour in (("course", GRANITE), ("cornice", GRANITE * 0.92), ("parapet", GRANITE * 0.7)):
        m = kinds == kind
        img[m] = colour * (1.0 + 0.05 * noise[m])[..., None]
    # Under the course its own shadow; the cornice's deep shadow and its dentils.
    img[(kinds == "spandrel") & (fy < 0.25)] *= 0.7
    cornice = kinds == "cornice"
    img[cornice & (fy > 0.7)] *= 0.5
    dent = cornice & (np.abs(((BX * 2.0) % 1.0) - 0.5) < 0.2) & (fy > 0.75)
    img[dent] = GRANITE * 0.65
    # Windows: columns 2-4 of each bay on glass rows (and the arch row where the arch allows).
    win_col = (col >= 2) & (col <= 4)
    ux = BX - np.floor(BX / BAY) * BAY - 2.0               # 0..3 across the window
    ax = (ux - 1.5) / 1.5                                  # -1..1 across the window
    arch_ok = fy > (1.0 - np.sqrt(np.clip(1 - ax ** 2, 0, 1))) * 0.85
    opening = win_col & ((kinds == "glass") | ((kinds == "arch") & arch_ok))
    # Where in the opening: 0 at the bottom of the window's lowest glass row, 1 at its top.
    if not top:
        # 5 glass rows at the top of each 7-row storey.
        vy = 1.0 - (BY % STOREY) / 5.0
    else:
        lit = [r for r, k in enumerate(rows) if k in ("glass", "arch")]
        vy = 1.0 - (BY - lit[0]) / len(lit)
    # A heavy dark frame round the opening (sash and casing), about 0.22 block.
    frame = opening & ((ux < 0.22) | (ux > 2.78) | (vy < 0.05) | ((vy > 0.955) & ~(kinds == "arch")))
    glass = opening & ~frame
    # Each window (bay x storey) gets its own brightness.
    storey = np.floor(BY / STOREY).astype(int) if not top else np.zeros_like(row)
    wv = _window_values(rng, BAYS * 16).reshape(16, BAYS)
    bright = wv[np.clip(storey, 0, 15), bay % BAYS]
    # Colour temperature per window: mostly neutral, some a touch warm or cool (different panels).
    wt = np.where(rng.random((16, BAYS)) < 0.18, rng.uniform(-1.0, 1.0, (16, BAYS)), 0.0)
    temp = wt[np.clip(storey, 0, 15), bay % BAYS]
    # No muntins here: at these distances they are thinner than a pixel and only shimmer. Just the
    # coal soot low on the panes, and the light a touch brighter toward the top of each window.
    shade = 0.86 + 0.14 * np.clip(vy * 1.4, 0, 1)
    glass_col = GLASS[None, None, :] * (bright * shade)[..., None]
    img = np.where(glass[..., None], glass_col, img)
    img[frame] = FRAME
    # Coal traces: a sooty smudge on the spandrel under each window.
    soot = (kinds == "spandrel") & win_col
    img[soot] *= 0.8
    small = img.reshape(height_px, ss, width_px, ss, 3).mean(axis=(1, 3))
    mask = glass.astype(float) * bright
    mask_small = mask.reshape(height_px, ss, width_px, ss).mean(axis=(1, 3))
    temp_small = (glass * temp).reshape(height_px, ss, width_px, ss).mean(axis=(1, 3))
    return small, mask_small, temp_small


def _glow(mask, temp, wrap_vertical: bool, soft: bool):
    """The windows' light, added on top of the facade in game.

    Crisp (soft=False, seen nearby): each window bright with a halo that bleeds a little into the
    piers and spandrels but leaves them dark, so the rows stay separate. Diffused (soft=True,
    seen far away, cross-faded in by distance): the light spread into the fog, the windows'
    edges lost, the ranks reading as soft bands of glow, as in the photo.
    temp: per-window colour temperature (-1 cool .. +1 warm, mostly 0)."""
    pad = 0 if wrap_vertical else 40

    def blur(a, s):
        a = np.pad(a, ((pad, pad), (0, 0))) if pad else a
        out = G.blur(a, s)
        return out[pad:-pad] if pad else out

    sigmas = ((4.0, 0.35), (12.0, 0.6), (28.0, 0.35)) if soft else ((1.2, 0.55), (3.5, 0.4), (9.0, 0.15))
    warm = mask * np.clip(temp, 0, None)
    cool = mask * np.clip(-temp, 0, None)
    g = sum(w * blur(mask, s) for s, w in sigmas)
    gw = sum(w * blur(warm, s) for s, w in sigmas)
    gc = sum(w * blur(cool, s) for s, w in sigmas)
    base = np.array([214.0, 224.0, 240.0])
    r = np.clip(g + 0.10 * gw - 0.06 * gc, 0, 1) * base[0]
    gg = np.clip(g + 0.02 * gw, 0, 1) * base[1]
    b = np.clip(g - 0.14 * gw + 0.06 * gc, 0, 1) * base[2]
    rgb = np.stack([r, gg, b], axis=-1)
    return np.concatenate([np.clip(rgb, 0, 255), np.full(g.shape + (1,), 255.0)], axis=2)


def tex_level39_facade_far() -> None:
    """The facade seen from far away: 16 bays x 8 storeys (body, tiles both ways) and 16 bays x
    the top storey + cornice + parapet (top strip), each with a crisp and a diffused light layer.

    Rows match world/layout/Level39Layout: each storey (from the top) has 5 glass rows, a brick
    spandrel and a granite string course; the top storey's highest glass row is the segmental
    arch, then a 3-row cornice and the parapet."""
    rng = G.rng_for(39100)
    storey_rows = ["glass"] * 5 + ["spandrel", "course"]
    width = 1024   # 96 blocks across: ~10.7 px per block (power of two: clean mipmaps)
    body, body_mask, body_temp = _facade(rng, width, 512, storey_rows * STOREYS, top=False)
    G.save_png(body, _env("l39_facade_far"))
    G.save_png(_glow(body_mask, body_temp, True, False), _env("l39_facade_far_glow"))
    G.save_png(_glow(body_mask, body_temp, True, True), _env("l39_facade_far_glow_soft"))
    top_rows = ["parapet", "cornice", "cornice", "cornice", "arch"] + ["glass"] * 4 + ["spandrel", "course"]
    top, top_mask, top_temp = _facade(rng, width, 112, top_rows, top=True)
    G.save_png(top, _env("l39_facade_far_top"))
    G.save_png(_glow(top_mask, top_temp, False, False), _env("l39_facade_far_top_glow"))
    G.save_png(_glow(top_mask, top_temp, False, True), _env("l39_facade_far_top_glow_soft"))


def tex_level39_clouds() -> None:
    """The cloud ceiling: soft, billowing, tileable cloud with transparent gaps. Pale grey: the
    game tints it dark, and lights it from below near the facades."""
    rng = G.rng_for(39101)
    size = 256
    # Big soft billows with a little detail on their edges (no fine grain: these are seen from
    # far below, through fog).
    n = G.fnoise(rng, (size, size), beta=4.0) + 0.2 * G.fnoise(rng, (size, size), beta=2.8, kmin=4)
    n = G.blur(n, 1.2)
    n = (n - n.mean()) / n.std()
    alpha = G.smoothstep(-0.5, 1.3, n) * 0.95
    # Shading: brighter where the cloud bulges (towards the light below).
    shade = G.smoothstep(-1.0, 2.0, n - G.blur(n, 6.0) * 0.8)
    v = 170.0 + 85.0 * shade
    img = np.stack([v, v, v * 1.02, alpha * 255.0], axis=-1)
    G.save_png(np.clip(img, 0, 255), _env("l39_clouds"))


TEXTURES = [tex_level39_facade_far, tex_level39_clouds]
SOUNDS = [
    snd_level39_town, snd_level39_tunnel, snd_level39_rain,
    snd_level39_echo_boom, snd_level39_quake_rumble, snd_level39_window_rattle, snd_level39_radio_garbled,
    snd_level39_distant_groan, snd_level39_distant_metal_slam, snd_level39_distant_dog_bark,
    snd_level39_distant_screen_door, snd_level39_distant_wind_chime, snd_level39_distant_telephone,
    snd_level39_distant_ash_rattle,
    snd_level39_distant_tunnel_drip, snd_level39_distant_tunnel_splash,
]
