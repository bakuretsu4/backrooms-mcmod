"""The Poolrooms' anomalies: textures and sounds.

* The Sanguine Reservoir (Wikidot Level 37.1), the red copy of the level you drift into from a
  dark corner: dim tile, a dark floor under red water, a red pool lamp, glowing red sprouts; a
  faint chorus of chants, slithering, whimpers, and the dull swell of the drift itself.
* The "obtrusive" water of both pool levels: a loud, close rush of lapping and sloshing water.
* The rush of a current in a stair pit.

The Poolrooms are calm: nothing here is a jump scare. Sounds stay soft and low (modest peaks),
and the voices are wordless and far away.

Run with e.g. `python tools/gen_assets.py tex_poolrooms_reservoir snd_poolrooms_chant`.
Every generator uses its own fixed random seed (3700-3799), so re-running gives identical files.
"""
from __future__ import annotations

import numpy as np
from PIL import Image, ImageDraw

import gen_assets as G

S = G.S     # 128 px block texture size
SR = G.SR   # 44.1 kHz sample rate


def _blk(name: str):
    return G.TEX / "block" / f"{name}.png"


def _snd(name: str):
    return G.SND / "anomaly" / "poolrooms" / f"{name}.ogg"


# =============================================================================
# Textures
# =============================================================================

RES_TILE = (150, 140, 142)     # the same big tiles, dimmed and a touch warm
RES_GROUT = (78, 68, 72)


def tex_poolrooms_reservoir_tile() -> None:
    """The Reservoir's tile: the Poolrooms' big glazed tiles, dimmer, with darker grout."""
    save = G._pool_tiles(3700, RES_TILE, RES_GROUT, glaze=1.8, speck=0.8)
    G.save_png(save, _blk("reservoir_tile"))


def tex_poolrooms_reservoir_floor() -> None:
    """Floor under the red water: dark tiles with a faint, slow, reddish shimmer (no bright caustics)."""
    frames = 16
    base = G._pool_tiles(3701, (112, 92, 94), (62, 50, 54), glaze=1.2, speck=0.6)
    strip = []
    for i in range(frames):
        c = G._caustics(i, frames)
        strip.append(G.add_lum(base, c * 9, (1.0, 0.45, 0.4)))
    path = _blk("reservoir_floor")
    G.save_png(np.concatenate(strip, axis=0), path)
    path.with_name("reservoir_floor.png.mcmeta").write_text(
        '{"animation": {"frametime": 3, "interpolate": true}}\n')


def tex_poolrooms_reservoir_lamp() -> None:
    """The pool lamp's face in the Reservoir: a deep red glowing lens in a dull steel ring."""
    img = G._pool_tiles(3700, RES_TILE, RES_GROUT, glaze=1.8, speck=0.8)
    X, Y = G.grid()
    r = np.hypot(X - 64, Y - 64)
    R = 38.0
    img = G.add_lum(img, -18 * G.smoothstep(R + 9, R + 4, r) * (r > R + 4))
    ring = np.clip(R + 4.5 - r, 0, 1)
    shade = 0.7 + 0.2 * np.clip((64 - Y) / 40, -1, 1)
    img = G.over(img, np.array([150, 140, 140]) * shade[..., None], ring)
    lens = np.clip(R - r + 0.5, 0, 1)
    t = np.clip(r / R, 0, 1)[..., None] ** 1.5
    col = G.lerp(np.array([255, 150, 130]), np.array([196, 18, 22]), t)
    img = G.lerp(img, col, lens[..., None])
    G.save_png(img, _blk("reservoir_lamp"))


def tex_poolrooms_reservoir_sprout() -> None:
    """Red sprouts (cross model, cutout): a clump of fleshy stalks with glowing bulbous tips.

    Drawn 4x larger with PIL, then shrunk; colours are averaged over covered pixels only so the
    edges get no dark fringe, and alpha is strictly 0/255 for Minecraft's cutout render type.
    """
    rng = G.rng_for(3702)
    ss = 4
    W = S * ss
    canvas = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    n = 7
    for k in range(n):
        # Each stalk leaves the ground near the middle and arcs up and out.
        base_x = 64 + rng.uniform(-14, 14)
        lean = (k - (n - 1) / 2) / n * 2 + rng.uniform(-0.3, 0.3)
        height = rng.uniform(52, 104)
        steps = 24
        pts = []
        for i in range(steps + 1):
            t = i / steps
            x = base_x + lean * 34 * t ** 1.6 + 3 * np.sin(t * 5 + k)
            y = 127 - height * t
            pts.append((x, y, t))
        for (x0, y0, t0), (x1, y1, t1) in zip(pts[:-1], pts[1:]):
            w = (7.5 - 4.5 * t0) * ss
            c = G.lerp(np.array([92, 6, 12]), np.array([222, 30, 34]), t0 ** 1.3)
            draw.line([(x0 * ss, y0 * ss), (x1 * ss, y1 * ss)], fill=tuple(int(v) for v in c) + (255,), width=int(w))
            draw.ellipse([x1 * ss - w / 2, y1 * ss - w / 2, x1 * ss + w / 2, y1 * ss + w / 2],
                         fill=tuple(int(v) for v in c) + (255,))
        # The glowing tip: a bulb, hot pink-white in the middle.
        tx, ty, _ = pts[-1]
        rb = rng.uniform(5.5, 8.5)
        for j, (scale, col) in enumerate(((1.0, (236, 46, 42)), (0.68, (255, 112, 96)), (0.36, (255, 206, 186)))):
            rr = rb * scale * ss
            draw.ellipse([tx * ss - rr, ty * ss - rr * 1.15, tx * ss + rr, ty * ss + rr * 0.95], fill=col + (255,))
    a = np.asarray(canvas, float).reshape(S, ss, S, ss, 4)
    cov = a[..., 3].mean(axis=(1, 3)) / 255.0
    wgt = a[..., 3:4] / 255.0
    rgb = (a[..., :3] * wgt).sum(axis=(1, 3)) / np.maximum(wgt.sum(axis=(1, 3)), 1e-6)
    alpha = np.where(cov >= 0.5, 255.0, 0.0)
    G.save_png(np.concatenate([rgb, alpha[..., None]], axis=2), _blk("reservoir_sprout"))


# =============================================================================
# Sounds
# =============================================================================

def snd_poolrooms_obtrusive_water() -> None:
    """12 s loop: the water turned loud and close. Dense lapping against the tiles, heavy sloshing
    swells, gurgles and bubbles everywhere; it fills the whole soundscape (but stays watery and
    smooth, no hiss)."""
    rng = G.rng_for(3710)
    T = 12.0
    n = int(T * SR)
    t = np.arange(n) / SR
    # Lapping: band-passed noise in many overlapping irregular swells (periodic over the loop).
    lap = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 150, 1400, 2))
    env = np.zeros(n)
    for _ in range(26):
        c, w = rng.uniform(0, T), rng.uniform(0.15, 0.5)
        env += rng.uniform(0.4, 1.0) * np.exp(-0.5 * (G.wrapd(t - c, T) / w) ** 2)
    lap = lap * (0.35 + env)
    # Heavy slow sloshing (the body of the water rocking) and a low rumble under it.
    slosh = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 50, 320, 2)) * G.drift(n, rng, 0.6, 0.15, 0.6)
    rumble = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 25, 90, 2)) * G.drift(n, rng, 0.3, 0.08, 0.3)
    # Gurgles and bubbles: many short rising "plips", placed around the loop.
    bubbles = np.zeros(n)
    for _ in range(320):
        f0 = float(np.exp(rng.uniform(np.log(250), np.log(2400))))
        decay = 0.043 * f0 + 0.0014 * f0 ** 1.5
        dur = min(6.0 / decay, 0.2)
        bt = np.arange(int(dur * SR)) / SR
        b = np.sin(2 * np.pi * np.cumsum(f0 * (1 + 0.3 * decay * bt)) / SR) * np.exp(-decay * bt)
        G.add_at(bubbles, b * rng.uniform(0.2, 1.0) * (1000.0 / f0) ** 0.5, rng.uniform(0, T), circular=True)
    bubbles = G.fft_filter(bubbles, lambda f: G.lp_gain(f, 3500, 2), circular=True)
    x = G.norm(lap) * 0.7 + G.norm(slosh) * 0.55 + G.norm(rumble) * 0.35 + G.norm(bubbles) * 0.3
    # Close and dry: a very short tiled-room tail only (the canon sound has no proper reverb).
    x = x + 0.2 * G.circ_conv(x, G.reverb_ir(rng, 0.4, 0.08, lp=3000))
    G.save_ogg(x, _snd("obtrusive_water"), peak=0.8, loop=True)


def snd_poolrooms_chant() -> None:
    """16 s loop: the faintest chorus of chants in the distance. A few low wordless voices on a
    slow, repeating phrase (rising and falling between two notes), muffled as if through water
    and many rooms, washed in a long dark reverb."""
    rng = G.rng_for(3711)
    T = 16.0
    n = int(T * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    # The phrase: four held syllables per loop; each voice glides gently between its notes.
    phrase = [0, 3, 0, -2]
    for base, gain in ((98.0, 1.0), (146.8, 0.7), (196.0, 0.45), (73.4, 0.6)):
        semis = np.zeros(n)
        for i, s in enumerate(phrase):
            c = (i + 0.5) * T / 4
            semis += s * np.exp(-0.5 * (G.wrapd(t - c, T) / (T / 9)) ** 2)
        f0 = base * 2 ** (semis / 12) * (1 + 0.004 * np.sin(2 * np.pi * 5 * t / T * 8 + rng.uniform(0, 6)))
        # Vowels drifting between "oo" and "ah" with the phrase.
        open_ = 0.5 + 0.5 * np.sin(2 * np.pi * t / T * 4 + rng.uniform(0, 6))
        f1 = 320 + 380 * open_
        f2 = 800 + 400 * open_
        v = G.voice(f0, [(f1, 110, 1.0), (f2, 180, 0.45), (2500, 400, 0.08)], rng, nh=30)
        # Syllables: swell on each of the four notes, a breath of quiet between.
        syl = np.zeros(n)
        for i in range(4):
            c = (i + 0.5) * T / 4
            syl += np.exp(-0.5 * (G.wrapd(t - c, T) / (T / 11)) ** 2)
        out += G.norm(v) * syl * gain
    out = G.fft_filter(out, lambda f: G.lp_gain(f, 900, 3) * G.hp_gain(f, 60, 2), circular=True)
    wet = G.circ_conv(out, G.reverb_ir(rng, 5.0, 2.0, lp=800, predelay=0.08))
    x = G.norm(out) * 0.45 + G.norm(wet) * 0.8
    G.save_ogg(x, _snd("chant"), peak=0.5, loop=True)


def snd_poolrooms_slither() -> None:
    """Something wet sliding over tile, out of sight, 3 variants (~2 s): irregular squelchy
    drags, a few soft slaps, low-passed by distance, a small tiled-room tail."""
    for i in range(1, 4):
        rng = G.rng_for(3720 + i)
        dur = 2.2
        n = int(dur * SR)
        t = np.arange(n) / SR
        noise = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 180, 1600, 2))[:n]
        # Pulses of movement: body segments dragging, each a quick swell.
        env = np.zeros(n)
        pos = 0.1
        while pos < dur - 0.3:
            w = rng.uniform(0.05, 0.14)
            env += rng.uniform(0.4, 1.0) * np.exp(-0.5 * ((t - pos) / w) ** 2)
            pos += rng.uniform(0.12, 0.3)
        wob = 1 + 0.6 * G.smooth_jitter(rng, n, 25, 1.0)
        drag = G.norm(noise) * env * np.clip(wob, 0, None)
        slaps = np.zeros(n)
        for _ in range(rng.integers(2, 5)):
            st = rng.uniform(0.1, dur - 0.4)
            s = G.fft_filter(rng.normal(size=int(0.06 * SR)), lambda f: G.bp_gain(f, 200, 2000, 2))[:int(0.06 * SR)]
            s *= np.exp(-np.arange(s.size) / SR / 0.012)
            G.add_at(slaps, G.norm(s) * rng.uniform(0.3, 0.7), st, circular=False)
        x = drag * 0.8 + slaps * 0.5
        x *= G.smoothstep(0, 0.2, t) * (1 - G.smoothstep(dur - 0.5, dur, t))
        x = G.fft_filter(x, lambda f: G.lp_gain(f, 1800, 2))[:n]
        x = x + 0.35 * G.conv(x, G.reverb_ir(rng, 1.2, 0.35, lp=1500, predelay=0.02))[:n]
        G.save_ogg(x, _snd(f"slither{i}"), peak=0.6, fade_in=0.02, fade_out=0.2)


def snd_poolrooms_whimper() -> None:
    """A soft whimper from nowhere, 3 variants (~1.5 s): two or three small breathy sobs with a
    falling pitch, wordless, muffled and far off."""
    for i in range(1, 4):
        rng = G.rng_for(3730 + i)
        dur = 1.8
        n = int(dur * SR)
        t = np.arange(n) / SR
        x = np.zeros(n)
        count = 2 + int(rng.integers(0, 2))
        pos = 0.08
        for _ in range(count):
            d = rng.uniform(0.28, 0.45)
            m = int(d * SR)
            tt = np.arange(m) / SR
            top = rng.uniform(330, 420)
            f0 = top * (1 - 0.28 * (tt / d) ** 1.2) * (1 + 0.01 * np.sin(2 * np.pi * 7 * tt))
            v = G.voice(f0, [(700, 160, 1.0), (1150, 200, 0.5), (2600, 400, 0.12)], rng, nh=24)
            breath = G.fft_filter(rng.normal(size=m), lambda f: G.bp_gain(f, 800, 4000, 1))[:m]
            sob = G.norm(v) * 0.75 + G.norm(breath) * 0.25
            sob *= G.smoothstep(0, 0.04, tt) * (1 - G.smoothstep(d * 0.55, d, tt))
            G.add_at(x, sob * rng.uniform(0.6, 1.0), pos, circular=False)
            pos += d + rng.uniform(0.08, 0.2)
        x = G.fft_filter(x, lambda f: G.lp_gain(f, 1400, 2))[:n]
        x = x + 0.5 * G.conv(x, G.reverb_ir(rng, 1.6, 0.5, lp=1200, predelay=0.03))[:n]
        G.save_ogg(x, _snd(f"whimper{i}"), peak=0.5, fade_in=0.01, fade_out=0.25)


def snd_poolrooms_drift() -> None:
    """~4.5 s: the moment you drift. A deep, muffled swell like sinking under water, a soft
    pressure in the ears, the faint chorus rising through it, then a slow fade."""
    rng = G.rng_for(3740)
    dur = 4.5
    n = int(dur * SR)
    t = np.arange(n) / SR
    swell = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 30, 300, 2))[:n]
    env = G.smoothstep(0, 1.6, t) * (1 - G.smoothstep(2.0, dur, t))
    sub = np.sin(2 * np.pi * np.cumsum(38 + 6 * np.exp(-t / 1.2)) / SR) * env
    tone = np.zeros(n)
    for f, a in ((98.0, 1.0), (146.8, 0.6), (196.0, 0.35)):
        tone += a * np.sin(2 * np.pi * f * t + rng.uniform(0, 6))
    tone *= G.smoothstep(0.8, 2.2, t) * (1 - G.smoothstep(2.6, dur, t))
    tone = G.fft_filter(tone, lambda f: G.lp_gain(f, 400, 2))[:n]
    x = G.norm(swell) * env * 0.8 + G.norm(sub) * 0.7 + G.norm(tone) * 0.25
    x = x + 0.4 * G.conv(x, G.reverb_ir(rng, 2.5, 0.9, lp=700))[:n]
    G.save_ogg(x, _snd("drift"), peak=0.7, fade_in=0.05, fade_out=0.6)


def snd_poolrooms_current() -> None:
    """An underwater rush, 2 variants (~1.6 s): a muffled surge of moving water with a stream of
    bubbles, as a current grabs you."""
    for i in range(1, 3):
        rng = G.rng_for(3750 + i)
        dur = 1.6
        n = int(dur * SR)
        t = np.arange(n) / SR
        fc = 180 + 260 * np.exp(-((t - 0.5) / 0.35) ** 2)
        rush = G.norm(G.svf(rng.normal(size=n), fc, 1.4))
        env = G.smoothstep(0, 0.25, t) * (1 - G.smoothstep(0.7, dur, t))
        bubbles = np.zeros(n)
        for _ in range(60):
            f0 = float(np.exp(rng.uniform(np.log(300), np.log(1500))))
            bd = rng.uniform(0.02, 0.06)
            bt = np.arange(int(bd * SR)) / SR
            b = np.sin(2 * np.pi * np.cumsum(f0 * (1 + 1.2 * bt / bd)) / SR) * np.exp(-bt / (bd / 3))
            G.add_at(bubbles, b * rng.uniform(0.2, 0.8), rng.uniform(0.05, dur * 0.75), circular=False)
        x = rush * env + G.norm(bubbles) * 0.3 * env
        x = G.fft_filter(x, lambda f: G.lp_gain(f, 1200, 2))[:n]
        G.save_ogg(x, _snd(f"current{i}"), peak=0.7, fade_in=0.03, fade_out=0.3)


TEXTURES = [
    tex_poolrooms_reservoir_tile, tex_poolrooms_reservoir_floor, tex_poolrooms_reservoir_lamp,
    tex_poolrooms_reservoir_sprout,
]
SOUNDS = [
    snd_poolrooms_obtrusive_water, snd_poolrooms_chant, snd_poolrooms_slither, snd_poolrooms_whimper,
    snd_poolrooms_drift, snd_poolrooms_current,
]
