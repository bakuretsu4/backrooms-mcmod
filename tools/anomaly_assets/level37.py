"""Level 37 "Sublimity" anomalies: the sounds of the void opening, the dark opening and the
looping tunnel (they reuse the level's own blocks, so there are no textures).

Mono 44.1 kHz OGG like everything else; kept soft and muted (sound in Level 37 is "dampened",
with no proper reverberation, so the tails are short and dark).
"""
import numpy as np

import gen_assets as G

OUT = G.SND / "level37"


def snd_level37_void_tone() -> None:
    """8 s loop: the high, soft tone that sings out of a void opening. Two pure high partials
    (whole cycles over the loop, so the seam is invisible) beating very slowly against each other,
    a faint octave shimmer and a breath of airy noise, as if the glare itself were humming."""
    rng = G.rng_for(3790)
    T = 8.0
    n = int(T * G.SR)
    t = np.arange(n) / G.SR

    def partial(f, cycles_off=0):
        # Round to whole cycles per loop so the loop closes exactly.
        k = round(f * T) + cycles_off
        return np.sin(2 * np.pi * k / T * t)

    tone = partial(1567.98) + 0.8 * partial(1567.98, 2) + 0.18 * partial(3135.96, 1) + 0.12 * partial(2349.32)
    tone *= G.drift(n, rng, 0.25, 0.1, 0.4)
    air = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 2500, 7000, 2)) * G.drift(n, rng, 0.4, 0.08, 0.3)
    x = G.norm(tone) * 0.8 + G.norm(air) * 0.12
    G.save_ogg(x, OUT / "void_tone.ogg", peak=0.3, loop=True)


def snd_level37_void_vanish() -> None:
    """4.5 s: taken by the white. A bright airy swell and the tone rising into a high chord,
    then everything thins out into a soft, almost silent hiss (you hear this as Level 0 fades in)."""
    rng = G.rng_for(3791)
    dur = 4.5
    n = int(dur * G.SR)
    t = np.arange(n) / G.SR
    env = np.clip(t / 0.4, 0, 1) ** 2 * np.exp(-np.maximum(t - 0.6, 0) / 1.1)
    air = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 1200, 9000, 2)) * env
    glide = 1.0 + 0.12 * np.clip(t / 0.8, 0, 1)
    chord = sum(a * np.sin(2 * np.pi * np.cumsum(f * glide) / G.SR)
                for f, a in ((1567.98, 1.0), (1975.53, 0.6), (2349.32, 0.45), (3135.96, 0.25)))
    chord *= env
    dry = G.norm(air) * 0.55 + G.norm(chord) * 0.45
    # Short and dark: no proper reverberation in Level 37.
    wet = G.conv(dry, G.reverb_ir(rng, 1.2, 0.35, lp=5000, predelay=0.02))[:n]
    x = G.norm(dry) * 0.7 + G.norm(wet) * 0.3
    G.save_ogg(x, OUT / "void_vanish.ogg", peak=0.6, fade_out=1.0)


def snd_level37_dark_draft() -> None:
    """10 s loop: cold air breathing out of a dark opening. Low, muffled wind noise in slow gusts
    and a faint hollow resonance, as if the dark beyond it went on a long way."""
    rng = G.rng_for(3792)
    T = 10.0
    n = int(T * G.SR)
    t = np.arange(n) / G.SR
    wind = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 60, 420, 2)) * G.drift(n, rng, 0.55, 0.1, 0.35)
    hiss = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 500, 1600, 2)) * G.drift(n, rng, 0.6, 0.15, 0.4)
    hollow = sum(a * np.sin(2 * np.pi * round(f * T) / T * t) for f, a in ((73.0, 1.0), (110.0, 0.4)))
    hollow *= G.drift(n, rng, 0.5, 0.05, 0.2)
    x = G.norm(wind) * 0.65 + G.norm(hiss) * 0.12 + G.norm(hollow) * 0.18
    x = G.fft_filter(x, lambda f: G.lp_gain(f, 1400, 2), circular=True)
    G.save_ogg(x, OUT / "dark_draft.ogg", peak=0.5, loop=True)


def snd_level37_dark_pass() -> None:
    """2.5 s: passing through the dark. A muffled low whoomp as the air closes behind you and a
    long breath of cold air sucked away into nothing."""
    rng = G.rng_for(3793)
    dur = 2.5
    n = int(dur * G.SR)
    t = np.arange(n) / G.SR
    thump = np.sin(2 * np.pi * np.cumsum(70.0 * np.exp(-t / 0.5) + 35.0) / G.SR) * np.exp(-t / 0.35)
    thump *= np.clip(t / 0.01, 0, 1)
    suck_env = np.clip(t / 0.2, 0, 1) * np.exp(-np.maximum(t - 0.3, 0) / 0.6)
    suck = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 120, 900, 2)) * suck_env
    x = G.norm(thump) * 0.6 + G.norm(suck) * 0.4
    x = G.fft_filter(x, lambda f: G.lp_gain(f, 1200, 2))
    G.save_ogg(x, OUT / "dark_pass.ogg", peak=0.7, fade_out=0.6)


def snd_level37_loop_drip() -> None:
    """1.2 s: one drip into still water, a few steps ahead in the tunnel. A soft plip with a short,
    damped tail (Level 37 swallows its echoes). 3 takes: drop pitch (+-5%), glide speed and splash noise."""
    for i, (seed, p, gl) in enumerate(((3794, 1.0, 0.035), (53794, 1.05, 0.03), (63794, 0.95, 0.041)), 1):
        rng = G.rng_for(seed)
        dur = 1.2
        n = int(dur * G.SR)
        t = np.arange(n) / G.SR
        f0 = 1150.0 * p * (1 + 0.55 * np.clip(t / gl, 0, 1))
        plip = np.sin(2 * np.pi * np.cumsum(f0) / G.SR) * np.exp(-t / 0.028) * G.smoothstep(0, 0.002, t)
        splash = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 800, 3000, 2)) * np.exp(-t / 0.012)
        dry = G.norm(plip) * 0.8 + G.norm(splash) * 0.1
        wet = G.conv(dry, G.reverb_ir(rng, 0.9, 0.22, lp=2600, predelay=0.03))[:n]
        x = G.norm(dry) * 0.65 + G.norm(wet) * 0.35
        x = G.fft_filter(x, lambda f: G.lp_gain(f, 2600, 1))
        name = "loop_drip.ogg" if i == 1 else f"loop_drip{i}.ogg"
        G.save_ogg(x, OUT / name, peak=0.6, fade_out=0.3)


TEXTURES = []
SOUNDS = [snd_level37_void_tone, snd_level37_void_vanish, snd_level37_dark_draft,
          snd_level37_dark_pass, snd_level37_loop_drip]
