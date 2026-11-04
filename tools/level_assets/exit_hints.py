"""Exit hints: the faint sound each level's exit gives off, the infrared arrow only NightShot shows,
and the torn note found in supply containers.

Every sound is a seamless 8 s loop (periodic noise, whole-cycle modulation, circular event
placement), quiet and low so it reads as part of the level until you notice it stays in one place:
  level_0     a draft whistling up the stairwell
  level_1     the hollow drone of a concrete stairwell, a drip far down
  level_2     the emergency EXIT door's alarm buzz, heard through the door
  level_4     cold air drawn up the office stairwell, hissing under the fire door, a deep hollow drone
  poolrooms   daylight air spilling down the great hall's stair, its pool lapping far below
  level_37    water rushing down the pit
  level_9     highway traffic humming through the underpass
  level_94    the dark behind the door, breathing
  level_7     bubbles rising from the sunken door, a low hum
  level_39    the sweet electric hum of a lit mill window with something living in it
  level_490   wind keening past the structure's chains, and once a loop a deep groan of iron
  level_369   leaves stirring in the arch of vines, water lapping far behind it, a faint warm chord
  level_228   the exit mirror's cold draught (made with the level's other sounds in level228_atmos.py)

Run only these with:  python tools/gen_assets.py exit_hint
"""
from __future__ import annotations

import numpy as np

import gen_assets as G

T = 8.0
N = int(T * G.SR)
t = np.arange(N) / G.SR


def _save(x: np.ndarray, name: str, peak: float = 0.5) -> None:
    G.save_ogg(x, G.SND / "exit_hint" / f"{name}.ogg", peak=peak, loop=True)


def _swell(period: float, depth: float, phase: float = 0.0) -> np.ndarray:
    """Slow loudness swell with a whole number of cycles per loop (seamless)."""
    cycles = max(1, round(T / period))
    return 1.0 - depth * 0.5 * (1.0 + np.sin(2 * np.pi * cycles * t / T + phase))


def _tone(freq: float) -> np.ndarray:
    """Sine snapped to a whole number of cycles per loop (seamless)."""
    return np.sin(2 * np.pi * round(freq * T) / T * t)


def _bubbles(rng, count: int, f_lo: float, f_hi: float) -> np.ndarray:
    """Bubbles: short sines whose pitch rises as they leave (Minnaert), placed round the loop."""
    out = np.zeros(N)
    for start in rng.uniform(0, T, count):
        f0 = rng.uniform(f_lo, f_hi)
        dur = rng.uniform(0.04, 0.12)
        k = np.arange(int(dur * G.SR)) / G.SR
        f = f0 * (1 + 2.5 * k / dur)
        b = np.sin(2 * np.pi * np.cumsum(f) / G.SR) * np.exp(-k / (dur * 0.35))
        G.add_at(out, b * rng.uniform(0.3, 1.0), start, circular=True)
    return out


def snd_exit_hint_level_0() -> None:
    rng = G.rng_for(7401)
    air = G.periodic_noise(N, rng, lambda f: G.bp_gain(f, 250, 1800, 2) / np.sqrt(np.maximum(f, 20)))
    whistle = G.periodic_noise(N, rng, lambda f: G.bp_gain(f, 640, 700, 6))
    rumble = G.periodic_noise(N, rng, lambda f: G.bp_gain(f, 30, 90, 2))
    x = G.norm(air) * _swell(4.0, 0.6) + 0.35 * G.norm(whistle) * _swell(8.0, 0.8, 1.0) + 0.4 * G.norm(rumble)
    _save(x, "level_0")


def snd_exit_hint_level_1() -> None:
    rng = G.rng_for(7402)
    drone = G.periodic_noise(N, rng, lambda f: G.bp_gain(f, 50, 220, 3))
    hollow = sum(_tone(f) * a for f, a in ((92, 0.5), (184, 0.25), (276, 0.12)))
    drips = np.zeros(N)
    for start in (1.3, 5.9):
        G.add_at(drips, G.ring([1300, 2500], [0.04, 0.025], [1, 0.4], 0.3), start, circular=True)
    drips = G.circ_conv(drips, G.reverb_ir(rng, 3.0, 1.2, lp=3000, predelay=0.05))
    x = G.norm(drone) * _swell(8.0, 0.4) + 0.25 * hollow + 0.3 * G.norm(drips)
    _save(x, "level_1")


def snd_exit_hint_level_2() -> None:
    rng = G.rng_for(7403)
    f0 = 100.0
    buzz = sum(_tone(f0 * k) / k ** 0.8 for k in range(1, 14))
    buzz = G.fft_filter(buzz, lambda f: G.lp_gain(f, 700, 2), circular=True)   # through the steel door
    pulse = np.clip(np.sin(2 * np.pi * 2 * t), 0, 1) ** 0.3                   # the alarm's on/off, 2 per s
    hum = G.periodic_noise(N, rng, lambda f: G.bp_gain(f, 40, 120, 2))
    x = G.norm(buzz) * (0.45 + 0.55 * pulse) + 0.3 * G.norm(hum)
    _save(x, "level_2", peak=0.45)


def snd_exit_hint_level_4() -> None:
    # The stairwell's cold draft hissing through the gap under the fire door (it swells as air is
    # pulled up the shaft), and the shaft's own hollow drone with a faint ring of its concrete.
    rng = G.rng_for(7404)
    draft = G.periodic_noise(N, rng, lambda f: G.bp_gain(f, 500, 4000, 2) / np.sqrt(np.maximum(f, 20)))
    gap = G.periodic_noise(N, rng, lambda f: G.bp_gain(f, 1150, 1300, 6))       # the narrow slot under the door
    drone = G.periodic_noise(N, rng, lambda f: G.bp_gain(f, 35, 160, 3))
    hollow = sum(_tone(f) * a for f, a in ((71, 0.5), (142, 0.2)))
    x = (G.norm(draft) * _swell(4.0, 0.55) + 0.3 * G.norm(gap) * _swell(8.0, 0.7, 2.0)
         + 0.55 * G.norm(drone) + 0.15 * hollow)
    _save(x, "level_4")


def snd_exit_hint_poolrooms() -> None:
    # The way out is the opening of daylight at the top of the exit hall's long stair: soft open
    # air spilling in through it, and under that the hall's huge pool lapping in a long echo.
    rng = G.rng_for(7405)
    air = G.periodic_noise(N, rng, lambda f: G.bp_gain(f, 180, 2600, 2) / np.sqrt(np.maximum(f, 20)))
    laps = np.zeros(N)
    for start in rng.uniform(0, T, 14):
        dur = rng.uniform(0.25, 0.6)
        k = np.arange(int(dur * G.SR)) / G.SR
        lap = rng.normal(size=k.size) * np.sin(np.pi * k / dur) ** 2
        G.add_at(laps, lap * rng.uniform(0.4, 1.0), start, circular=True)
    laps = G.fft_filter(laps, lambda f: G.bp_gain(f, 150, 900, 2), circular=True)
    laps = G.circ_conv(laps, G.reverb_ir(rng, 4.0, 1.8, lp=1800, predelay=0.08))
    x = G.norm(air) * _swell(8.0, 0.5) + 0.55 * G.norm(laps)
    _save(x, "poolrooms")


def snd_exit_hint_level_37() -> None:
    rng = G.rng_for(7406)
    rush = G.periodic_noise(N, rng, lambda f: G.bp_gain(f, 120, 1600, 2)) * G.drift(N, rng, 0.35, 0.2, 0.6)
    deep = G.periodic_noise(N, rng, lambda f: G.bp_gain(f, 25, 90, 3))
    x = G.norm(rush) + 0.7 * G.norm(deep) + 0.3 * G.norm(_bubbles(rng, 25, 250, 600))
    _save(x, "level_37")


def snd_exit_hint_level_9() -> None:
    rng = G.rng_for(7407)
    roar = G.periodic_noise(N, rng, lambda f: G.bp_gain(f, 60, 700, 2) / np.sqrt(np.maximum(f, 20)))
    # Cars passing over the underpass: two broad swells per loop, at different places.
    cars = 0.35 + np.exp(-((G.wrapd(t - 2.0, T)) / 0.9) ** 2) + 0.7 * np.exp(-((G.wrapd(t - 6.2, T)) / 1.2) ** 2)
    tyre = G.periodic_noise(N, rng, lambda f: G.bp_gain(f, 300, 1500, 2))
    x = G.norm(roar) * cars + 0.25 * G.norm(tyre) * cars
    _save(x, "level_9")


def snd_exit_hint_level_94() -> None:
    rng = G.rng_for(7408)
    hum = 0.6 * _tone(48) + 0.25 * _tone(96)
    breath = G.periodic_noise(N, rng, lambda f: G.bp_gain(f, 150, 900, 2))
    inout = np.sin(np.pi * ((t / 4.0) % 1.0)) ** 2                        # slow breaths, 4 s each
    x = hum * 0.5 + G.norm(breath) * inout * 0.6
    _save(x, "level_94", peak=0.45)


def snd_exit_hint_level_7() -> None:
    rng = G.rng_for(7409)
    bubbles = _bubbles(rng, 80, 220, 700)
    hum = 0.6 * _tone(41) + 0.3 * _tone(82)
    water = G.periodic_noise(N, rng, lambda f: G.lp_gain(f, 300, 2))
    x = G.norm(G.fft_filter(bubbles, lambda f: G.lp_gain(f, 1800, 2), circular=True)) + 0.5 * hum + 0.3 * G.norm(water)
    _save(x, "level_7")


def snd_exit_hint_level_39() -> None:
    # The Window's lure: a sweet, slightly beating electric hum (two close partials) over a soft
    # high shimmer of light through glass, swelling slowly as if the window breathes.
    rng = G.rng_for(7411)
    hum = 0.5 * _tone(120) + 0.35 * _tone(240.5) + 0.2 * _tone(361) + 0.12 * _tone(480)
    shimmer = G.periodic_noise(N, rng, lambda f: G.bp_gain(f, 2500, 6000, 2))
    x = hum * _swell(4.0, 0.4) + 0.25 * G.norm(shimmer) * _swell(2.0, 0.6, 1.0)
    _save(x, "level_39", peak=0.42)


def snd_exit_hint_level_490() -> None:
    # Wind thin and high over the void, whistling round the great chains (two narrow resonances
    # that drift), and once per loop the slow deep groan of a loaded chain shifting.
    rng = G.rng_for(7412)
    wind = G.periodic_noise(N, rng, lambda f: G.bp_gain(f, 250, 2200, 2))
    whistle = (G.periodic_noise(N, rng, lambda f: np.exp(-((f - 1180) / 45) ** 2))
               + 0.7 * G.periodic_noise(N, rng, lambda f: np.exp(-((f - 1730) / 60) ** 2)))
    gust = _swell(4.0, 0.55) * _swell(8.0 / 3, 0.3, 2.0)
    dt = G.wrapd(t - 5.0, T)                                        # the groan, centred at 5 s
    env = np.exp(-(dt / 0.9) ** 2)
    phase = 2 * np.pi * np.cumsum(46.0 - 4.0 * np.clip(dt, -1.5, 1.5)) / G.SR
    groan = np.tanh(2.2 * (np.sin(phase) + 0.45 * np.sin(2 * phase + 0.6) + 0.25 * np.sin(3 * phase))) * env
    x = G.norm(wind) * gust * 0.55 + G.norm(whistle) * gust * 0.35 + G.norm(groan) * 0.8
    _save(x, "level_490", peak=0.42)


def snd_exit_hint_level_369() -> None:
    # The arch of vines: its leaves stirring in a draft from somewhere else (a dry, fine rustle in
    # little gusts), water lapping slowly far behind it (it leads to the sea), and a faint warm
    # chord, the lure, swelling and fading as the draft comes and goes.
    rng = G.rng_for(7413)
    rustle = G.periodic_noise(N, rng, lambda f: G.bp_gain(f, 1800, 7000, 2))
    flutter = np.clip(G.periodic_noise(N, rng, lambda f: G.bp_gain(f, 3, 14, 1)), 0, None)
    gust = _swell(4.0, 0.7) * _swell(8.0 / 3, 0.35, 1.3)
    lap = G.periodic_noise(N, rng, lambda f: G.bp_gain(f, 60, 380, 2)) * _swell(2.0, 0.8, 0.4)
    chord = 0.5 * _tone(220) + 0.35 * _tone(277.5) + 0.3 * _tone(330) + 0.12 * _tone(440.5)
    x = (G.norm(rustle) * (0.25 + G.norm(flutter)) * gust * 0.45 + G.norm(lap) * 0.5
         + chord * gust * 0.22)
    _save(x, "level_369", peak=0.42)


def tex_exit_hint_ir_arrow() -> None:
    """A spray-painted arrow pointing right (+u), white on transparent (tinted when drawn), with
    ragged edges and runs. Drawn dark, in NightShot only: the paint swallows the infrared lamp."""
    rng = G.rng_for(7410)
    S = G.S
    m = G.pil_mask(lambda d, k: (
        d.rectangle((14 * k, 54 * k, 78 * k, 74 * k), fill=255),
        d.polygon([(70 * k, 30 * k), (114 * k, 64 * k), (70 * k, 98 * k)], fill=255)))
    rough = G.fnoise(rng, beta=1.4, kmin=6)
    m = np.clip(G.blur(m, 1.6) * 1.4 + rough * 0.18 - 0.2, 0, 1)
    # Paint runs below the shaft.
    for x0 in rng.uniform(20, 100, 4):
        X, Y = G.grid()
        run = np.exp(-((X - x0) / 1.3) ** 2) * (Y > 66) * (Y < 66 + rng.uniform(10, 26))
        m = np.maximum(m, run * 0.8)
    speckle = (rng.random((S, S)) < 0.03) * G.smoothstep(0.1, 0.0, m) * rng.uniform(0.3, 0.8, (S, S))
    alpha = np.clip(np.maximum(m, speckle), 0, 1) * 255
    img = np.dstack([np.full((S, S), 255.0)] * 3 + [alpha])
    G.save_png(img, G.TEX / "misc" / "ir_arrow.png")


def tex_exit_hint_wanderer_note() -> None:
    """16x16 item: a torn, folded scrap of lined paper with pencil scribble."""
    img = np.zeros((16, 16, 4))
    paper = np.array([214, 204, 176, 255.0])
    shape = [(3, 2, 12), (4, 2, 13), (5, 2, 13), (6, 3, 13), (7, 2, 13), (8, 2, 12), (9, 3, 13), (10, 2, 13),
             (11, 2, 12), (12, 3, 12), (13, 3, 11)]
    for y, x0, x1 in shape:
        img[y, x0:x1 + 1] = paper
    for y in (5, 8, 11):
        img[y, 3:12, :3] -= 26                                  # faded ruled lines
    for y, x0, x1 in ((4, 4, 9), (6, 4, 11), (7, 5, 8), (9, 4, 10), (10, 6, 11), (12, 4, 7)):
        img[y, x0:x1 + 1, :3] = (70, 64, 60)                    # pencil
    img[2, 2:13, :3] *= 0.85                                    # torn top edge, darker
    img[3:13, 12:14, :3] *= 0.9
    G.save_png(np.clip(img, 0, 255), G.TEX / "item" / "wanderer_note.png")


TEXTURES = [tex_exit_hint_ir_arrow, tex_exit_hint_wanderer_note]
SOUNDS = [snd_exit_hint_level_0, snd_exit_hint_level_1, snd_exit_hint_level_2, snd_exit_hint_level_4,
          snd_exit_hint_poolrooms, snd_exit_hint_level_37, snd_exit_hint_level_9, snd_exit_hint_level_94,
          snd_exit_hint_level_7, snd_exit_hint_level_39, snd_exit_hint_level_490, snd_exit_hint_level_369]
