"""Level 0 anomalies: textures and sounds.

The Red Rooms (crimson wallpaper, black mould, tally marks, coarse red carpet, a darkroom-red
lamp, little mushrooms) and their warning signs (yellow wallpaper peeling off crimson, sticky
carpet), the flickering wall (the way to Level 1), Blackout Zones (rough dark wallpaper, murky
fluid on the carpet), the Manila Room (manila wallpaper, the brown wall, a volunteer's note) and
the pale plaster of the arch wall. Sounds: the Red Rooms' "klaxon synth" hum, the Manila Room's
piano from nowhere, and a blackout's wandering fluorescent buzz.

Run only these with:  python tools/gen_assets.py level0_a_
"""
from __future__ import annotations

import numpy as np

import gen_assets as G

S = G.S
CRIMSON = np.array([172.0, 38.0, 40.0])
INK = (30, 18, 18)


def _blk(name: str):
    return G.TEX / "block" / f"{name}.png"


def _recolour(img: np.ndarray, src, dst) -> np.ndarray:
    """Keeps a texture's pattern and shading but moves its base colour from src to dst."""
    return img * (np.asarray(dst, float) / np.asarray(src, float))


def _red_wallpaper() -> np.ndarray:
    """The Lobby's damask wallpaper (same pattern, same stains) printed in crimson."""
    return _recolour(G._wallpaper(), G.WALLPAPER, CRIMSON)


def _mould(img: np.ndarray, rng, amount: float) -> np.ndarray:
    """Black mould: clusters of dark specks, thicker towards the bottom of the block."""
    X, Y = G.grid()
    base = G.fnoise(rng, beta=2.6)
    speck = G.fnoise(rng, beta=0.4, kmin=20)
    grow = 0.35 + 0.65 * (Y / S) ** 1.5
    patch = G.smoothstep(0.6, 1.6, base + 0.8 * grow - (1.0 - amount))
    dots = G.smoothstep(0.2, 1.2, speck + 1.5 * patch - 0.8)
    img = G.over(img, np.array([40, 30, 26]), np.clip(patch * 0.35, 0, 0.5))
    return G.over(img, np.array([14, 12, 12]), np.clip(dots * patch, 0, 0.9))


# =============================================================================
# Red Rooms
# =============================================================================

def tex_level0_a_red_wallpaper() -> None:
    G.save_png(_red_wallpaper(), _blk("red_wallpaper"))


def tex_level0_a_red_wallpaper_mould() -> None:
    G.save_png(_mould(_red_wallpaper(), G.rng_for(5001), 0.8), _blk("red_wallpaper_mould"))


def tex_level0_a_red_wall_tally() -> None:
    """Crimson wallpaper covered in tally marks scratched in dark marker: someone counting loops."""
    rng = G.rng_for(5002)
    img = _red_wallpaper()
    strokes = []
    for row, y0 in enumerate((14, 50, 86)):
        x = 10 + rng.uniform(0, 6)
        for group in range(3 if row != 1 else 2):
            h = rng.uniform(20, 26)
            for k in range(4):
                xx = x + k * 6 + rng.uniform(-1, 1)
                strokes.append([(xx, y0 + rng.uniform(-1, 2)), (xx + rng.uniform(-1.5, 1.5), y0 + h)])
            strokes.append([(x - 3, y0 + h * 0.75), (x + 23, y0 + h * 0.25)])   # the fifth, across
            x += 38 + rng.uniform(-2, 3)
    # the last group of the middle row is unfinished
    for k in range(2):
        xx = 86 + k * 6
        strokes.append([(xx, 52), (xx + 1, 74)])
    img = G.marker(img, rng, strokes, width=2.6, color=INK, alpha=0.85)
    G.save_png(img, _blk("red_wall_tally"))


def tex_level0_a_red_carpet_coarse() -> None:
    """Coarse, sticky dark-red carpet: the Lobby carpet's weave, wine red, with tacky dark patches."""
    rng = G.rng_for(5003)
    img = _recolour(G._carpet(5003), G.CARPET, (122, 30, 28))
    img = G.add_lum(img, rng.normal(0, 1, (S, S)) * 7.0, (1.0, 0.5, 0.45))
    tacky = G.smoothstep(0.5, 1.8, G.fnoise(rng, beta=2.8))
    img = G.over(img, np.array([60, 14, 14]), tacky * 0.45)
    G.save_png(img, _blk("red_carpet_coarse"))


def tex_level0_a_red_room_light() -> None:
    """A small square lamp behind deep-red glass, like a photographer's darkroom safelight."""
    rng = G.rng_for(5004)
    img = G._smooth_concrete(rng, (120, 100, 98), mottle=1.5, fine=0.5)
    X, Y = G.grid()
    core = G.box((S, S), 20, 20, S - 20, S - 20)
    groove = G.box((S, S), 16, 16, S - 16, S - 16) - core
    img = G.over(img, np.array([50, 36, 36]), groove)
    glow = 0.8 + 0.2 * np.exp(-(((X - 64) / 40) ** 2 + ((Y - 64) / 40) ** 2))
    glass = np.stack([255 * glow, 40 * glow, 30 * glow], axis=-1)
    G.save_png(G.over(img, glass, core), _blk("red_room_light"))


def tex_level0_a_red_mushrooms() -> None:
    """A little cluster of pale-capped mushrooms on thin stems (cutout, drawn on a cross)."""
    rng = G.rng_for(5005)
    rgb = np.zeros((S, S, 3))
    alpha = np.zeros((S, S))
    caps = [(30, 96, 9, 0.8), (52, 104, 6, 1.0), (70, 90, 11, 0.9), (94, 102, 7, 0.7), (108, 94, 5, 1.0)]
    for cx, top, r, tone in caps:
        stem = G.pil_mask(lambda d, k, cx=cx, top=top, r=r: d.rectangle(
            ((cx - r * 0.25) * k, (top + r * 0.3) * k, (cx + r * 0.25) * k, (S - 1) * k), fill=255))
        cap = G.pil_mask(lambda d, k, cx=cx, top=top, r=r: d.pieslice(
            ((cx - r) * k, (top - r * 0.8) * k, (cx + r) * k, (top + r * 0.8) * k), 180, 360, fill=255))
        stem_col = np.array([210, 196, 170]) * (0.9 + 0.1 * tone)
        cap_col = np.array([158, 112, 76]) * tone + np.array([40, 20, 10]) * (1 - tone)
        X, Y = G.grid()
        shade = np.clip(1.0 - (Y - (top - r * 0.8)) / (r * 2.0), 0.6, 1.0)[..., None]
        rgb = rgb * (1 - stem[..., None]) + stem_col * stem[..., None]
        rgb = rgb * (1 - cap[..., None]) + (cap_col * shade) * cap[..., None]
        alpha = np.maximum(alpha, np.maximum(stem, cap))
    rgb = G.add_lum(rgb, rng.normal(0, 4, (S, S)))
    G.save_png(np.concatenate([rgb, np.where(alpha >= 0.5, 255.0, 0.0)[..., None]], axis=2), _blk("red_mushrooms"))


# =============================================================================
# Warning signs around the Red Rooms
# =============================================================================

def tex_level0_a_peeling_wallpaper() -> None:
    """Yellow wallpaper coming away in strips and flaps, crimson paper showing underneath."""
    rng = G.rng_for(5010)
    yellow = G._wallpaper()
    red = _red_wallpaper()
    tear = G.fnoise(rng, beta=2.2, aniso=(1, 3)) + 0.5 * G.fnoise(rng, beta=1.2, kmin=6)
    gone = G.smoothstep(0.55, 0.75, tear)                   # where the yellow layer has peeled away
    edge = G.smoothstep(0.42, 0.55, tear) - G.smoothstep(0.55, 0.68, tear)
    img = yellow * (1 - gone[..., None]) + red * gone[..., None]
    # the torn edge: the paper curls up (lighter), with a shadow on the crimson just beside it
    shadow = np.roll(G.smoothstep(0.55, 0.62, tear), 2, axis=0) * (1 - G.smoothstep(0.62, 0.7, tear))
    img = G.add_lum(img, 22 * np.clip(edge, 0, 1), (1.0, 0.98, 0.9))
    img = G.add_lum(img, -30 * np.clip(shadow, 0, 1))
    G.save_png(img, _blk("peeling_wallpaper"))


def tex_level0_a_sticky_carpet() -> None:
    """The Lobby carpet with dark, tacky reddish-brown patches that catch the light."""
    rng = G.rng_for(5011)
    img = G._carpet(5011)
    stain = G.smoothstep(0.3, 1.4, G.fnoise(rng, beta=2.6))
    img = G.over(img, np.array([92, 40, 26]), stain * 0.6)
    gloss = (rng.random((S, S)) < 0.03) * stain * 30.0
    G.save_png(G.add_lum(img, gloss), _blk("sticky_carpet"))


# =============================================================================
# The flickering wall
# =============================================================================

FLICKER_LEVELS = (1.0, 0.55, 1.18, 0.3, 0.92, 1.3, 0.62, 0.12)


def tex_level0_a_flickering_wall() -> None:
    """Yellow wallpaper stuttering like a dying tube: 8 frames of light and dark with torn,
    shifted bands of picture, played in an irregular order (flickering_wall.png.mcmeta)."""
    rng = G.rng_for(5020)
    base = G._wallpaper()
    frames = []
    for k, level in enumerate(FLICKER_LEVELS):
        img = base * level
        # a few horizontal bands slide sideways, like a picture that can't hold still
        for _ in range(rng.integers(1, 4)):
            y0 = int(rng.integers(0, S - 8))
            h = int(rng.integers(3, 14))
            img[y0:y0 + h] = np.roll(img[y0:y0 + h], int(rng.integers(-12, 13)), axis=1)
        X, Y = G.grid()
        scan = 1.0 + 0.06 * np.sin(2 * np.pi * (Y + 7 * k) / 4.0)
        img = img * scan[..., None] + rng.normal(0, 5, (S, S, 1))
        frames.append(img)
    G.save_png(np.concatenate(frames, axis=0), _blk("flickering_wall"))
    order = [0, 1, 0, 0, 2, 3, 0, 4, 5, 0, 0, 6, 7, 0, 2, 0, 0, 1, 4, 0]
    _blk("flickering_wall").with_suffix(".png.mcmeta").write_text(
        '{"animation": {"frametime": 2, "frames": [' + ", ".join(str(i) for i in order) + ']}}\n')


# =============================================================================
# Blackout Zones
# =============================================================================

def tex_level0_a_blackout_wallpaper() -> None:
    """The same wallpaper after years in the dark and the damp: grimy, rough, water-streaked."""
    rng = G.rng_for(5030)
    img = G._wallpaper() * 0.62
    grey = img.mean(axis=2, keepdims=True)
    img = img * 0.6 + grey * 0.4                                  # most of the yellow gone
    X, Y = G.grid()
    rough = G.fnoise(rng, beta=0.3, kmin=24) * 9.0 + G.fnoise(rng, beta=1.4, kmin=6) * 6.0
    img = G.add_lum(img, rough, (1.0, 0.95, 0.85))
    streaks = np.clip(G.fnoise(rng, beta=1.0, aniso=(1, 16), kmin=2), 0, None)
    img = G.over(img, np.array([52, 44, 30]), np.clip(streaks * 0.25, 0, 0.45))
    img = _mould(img, rng, 0.55)
    G.save_png(img, _blk("blackout_wallpaper"))


def tex_level0_a_carpet_fluid() -> None:
    """Murky carpet fluid (translucent): brownish-olive, slow ripples, floating fibres."""
    rng = G.rng_for(5031)
    img = G.fill((86, 76, 42), (S, S))
    ripple = G.fnoise(rng, beta=2.4)
    img = G.add_lum(img, ripple * 9.0 + G.fnoise(rng, beta=1.0, kmin=12) * 4.0, (1.0, 0.95, 0.7))
    glint = G.smoothstep(1.6, 2.4, G.fnoise(rng, beta=1.8, kmin=4))
    img = G.add_lum(img, glint * 40.0)
    fibres = (rng.random((S, S)) < 0.01) * 25.0
    img = G.add_lum(img, -fibres)
    alpha = np.clip(165 + ripple * 12 + glint * 40, 120, 220)
    G.save_png(np.concatenate([img, alpha[..., None]], axis=2), _blk("carpet_fluid"))


# =============================================================================
# The Manila Room and the arch wall
# =============================================================================

def tex_level0_a_manila_wallpaper() -> None:
    """Manila-folder beige wallpaper: soft paper fibre, faint pinstripes, a little age."""
    rng = G.rng_for(5040)
    img = G.fill((222, 200, 152), (S, S))
    X, Y = G.grid()
    stripes = np.cos(2 * np.pi * X / 16) * 2.0 + np.cos(2 * np.pi * X / 4) * 0.8
    lum = (stripes + G.fnoise(rng, beta=3.0) * 4.0 + G.fnoise(rng, beta=0.8, kmin=20) * 2.5
           + G.fnoise(rng, beta=1.5, aniso=(1, 6), kmin=3) * 1.5)
    img = G.add_lum(img, lum, (1.0, 0.96, 0.85))
    age = G.smoothstep(0.8, 2.0, G.fnoise(rng, beta=3.2))
    G.save_png(G.over(img, np.array([186, 156, 104]), age * 0.3), _blk("manila_wallpaper"))


def tex_level0_a_manila_brown_wall() -> None:
    """The brown wall in front of the Manila Room: old brown paint over plaster, scuffed."""
    rng = G.rng_for(5041)
    img = G._smooth_concrete(rng, (118, 82, 54), mottle=5.0, fine=1.2)
    scuff = G.smoothstep(0.9, 2.0, G.fnoise(rng, beta=1.6, aniso=(4, 1)))
    G.save_png(G.over(img, np.array([150, 116, 84]), scuff * 0.35), _blk("manila_brown_wall"))


def tex_level0_a_volunteer_note() -> None:
    """A sheet of lined paper taped to the wall, covered in small handwriting."""
    rng = G.rng_for(5042)
    img = G.fill((236, 230, 210), (S, S))
    img = G.add_lum(img, G.fnoise(rng, beta=2.0) * 3.0)
    X, Y = G.grid()
    for y in range(26, S - 6, 9):
        img = G.add_lum(img, -14 * G.smoothstep(0.8, 0.0, np.abs(Y - y)), (1.0, 1.0, 0.6))   # ruled lines
    strokes = []
    for y in range(24, S - 10, 9):
        x = 10.0
        end = rng.uniform(70, 118) if y < S - 20 else 60
        while x < end:
            w = rng.uniform(5, 14)
            pts = [(x + i * w / 5, y - 1.5 * abs(np.sin(i * 1.7 + x))) for i in range(6)]
            strokes.append(pts)
            x += w + rng.uniform(2.5, 5)
    img = G.marker(img, rng, strokes, width=1.4, color=(40, 44, 70), alpha=0.8)
    tape = G.box((S, S), 44, 0, 84, 12)
    img = G.over(img, np.array([220, 214, 180]), tape * 0.6)
    G.save_png(img, _blk("volunteer_note"))


def tex_level0_a_arch_wall() -> None:
    """Pale, smooth plaster of the arch rooms, with a few hairline cracks."""
    rng = G.rng_for(5043)
    img = G._smooth_concrete(rng, (228, 222, 204), mottle=3.0, fine=0.8)
    crack = G.smoothstep(0.03, 0.0, np.abs(G.fnoise(rng, beta=2.2, kmin=2)))
    G.save_png(G.add_lum(img, -18 * crack * G.smoothstep(0.5, 1.5, G.fnoise(rng, beta=3.0))), _blk("arch_wall"))


TEXTURES = [
    tex_level0_a_red_wallpaper, tex_level0_a_red_wallpaper_mould, tex_level0_a_red_wall_tally,
    tex_level0_a_red_carpet_coarse, tex_level0_a_red_room_light, tex_level0_a_red_mushrooms,
    tex_level0_a_peeling_wallpaper, tex_level0_a_sticky_carpet, tex_level0_a_flickering_wall,
    tex_level0_a_blackout_wallpaper, tex_level0_a_carpet_fluid,
    tex_level0_a_manila_wallpaper, tex_level0_a_manila_brown_wall, tex_level0_a_volunteer_note,
    tex_level0_a_arch_wall,
]


# =============================================================================
# Sounds
# =============================================================================

def snd_level0_a_red_rooms_hum() -> None:
    """12 s seamless loop: the Red Rooms' buzz, "like a klaxon synth". A low, buzzy synth drone
    that slides slowly between two notes a minor third apart (an alarm in slow motion), with a
    faint 1.5 Hz pulse and a sub-bass hum under it. Dark: almost nothing above 1 kHz."""
    rng = G.rng_for(5101)
    T = 12.0
    n = int(T * G.SR)
    t = np.arange(n) / G.SR
    # Frequency: glides between 98 Hz and 116.5 Hz twice per loop (smooth, periodic over T).
    glide = 0.5 - 0.5 * np.cos(2 * np.pi * t / (T / 2))
    f = 98.0 * (2 ** (3 / 12)) ** glide
    # Make the total number of cycles whole, so every harmonic lines up at the loop seam.
    cycles = np.round(f.mean() * T)
    f = f * cycles / (f.mean() * T)
    phase = 2 * np.pi * np.cumsum(f) / G.SR
    phase -= phase[0]
    synth = sum(np.sin(k * phase + 0.3 * k) / k ** 1.1 for k in range(1, 18))
    detuned = sum(np.sin(k * phase * 1.0 + np.sin(2 * np.pi * t * 3 / T) * 0.8 * k) / k ** 1.3 for k in range(1, 10))
    x = G.norm(synth) + 0.4 * G.norm(detuned)
    x = G.fft_filter(x, lambda fr: G.lp_gain(fr, 900, 3) * G.hp_gain(fr, 50, 2), circular=True)
    pulse = 1.0 - 0.18 * (0.5 + 0.5 * np.cos(2 * np.pi * 1.5 * t))      # 18 whole pulses per loop
    sub = np.sin(2 * np.pi * 49.0 * t)                                     # 588 whole cycles
    bed = G.periodic_noise(n, rng, lambda fr: G.bp_gain(fr, 60, 400, 2))
    x = G.norm(x) * pulse + 0.35 * sub + 0.04 * G.norm(bed)
    G.save_ogg(x, G.SND / "level0" / "red_rooms_hum.ogg", peak=0.6, loop=True)


def _piano_note(freq: float, dur: float, vel: float, rng) -> np.ndarray:
    """A soft, slightly worn upright-piano note: stretched partials, felt thump, natural decay."""
    n = int(dur * G.SR)
    t = np.arange(n) / G.SR
    x = np.zeros(n)
    for k in range(1, 9):
        fk = freq * k * np.sqrt(1 + 0.0004 * k * k)
        if fk > 5000:
            break
        decay = 1.8 / (1 + 0.35 * k) * (220.0 / freq) ** 0.3
        x += (0.68 ** (k - 1)) * np.sin(2 * np.pi * fk * t + rng.uniform(0, 6.28)) * np.exp(-t / decay)
    x *= 1 - np.exp(-t / 0.005)
    thump = G.fft_filter(rng.normal(size=n) * np.exp(-t / 0.01), lambda f: G.bp_gain(f, 80, 800, 1))[:n]
    return vel * (x + 0.1 * G.norm(thump))


def _hz(note: str) -> float:
    names = {"C": -9, "D": -7, "E": -5, "F": -4, "G": -2, "A": 0, "B": 2}
    acc = 1 if "#" in note else (-1 if "b" in note[1:] else 0)
    return 440.0 * 2 ** ((names[note[0]] + acc + 12 * (int(note[-1]) - 4)) / 12)


def snd_level0_a_manila_piano() -> None:
    """An original little piano piece for the Manila Room: gentle, a bit hopeful and a bit sad,
    in C major leaning on A minor (8 bars of 4/4 at 66 bpm, ~29 s), looping seamlessly (notes ring
    over the seam into the start). Played softly in a small room, heard "from nowhere"."""
    rng = G.rng_for(5102)
    beat = 60.0 / 66.0
    T = 32 * beat
    n = int(T * G.SR)
    x = np.zeros(n)
    bars = [("C3", ["G3", "C4", "E4"]), ("A2", ["E3", "A3", "C4"]), ("F2", ["C3", "F3", "A3"]), ("G2", ["D3", "G3", "B3"]),
            ("C3", ["G3", "C4", "E4"]), ("E2", ["B2", "E3", "G3"]), ("F2", ["C3", "F3", "A3"]), ("G2", ["D3", "F3", "B3"])]
    for b, (bass, chord) in enumerate(bars):
        start = b * 4 * beat
        G.add_at(x, _piano_note(_hz(bass), 4.0, 0.42, rng), start, circular=True)
        # gentle broken chord: up on beats 2, 2.5, 3 and back down on 4
        for j, (note, at) in enumerate(((chord[0], 1.0), (chord[1], 1.5), (chord[2], 2.0), (chord[1], 3.0))):
            G.add_at(x, _piano_note(_hz(note), 2.2, 0.18 + 0.02 * j, rng), start + at * beat + rng.uniform(0, 0.015), circular=True)
    melody = [("E5", 0, 1.5), ("D5", 1.5, 0.5), ("C5", 2, 1), ("G4", 3, 1),
              ("A4", 4, 1.5), ("B4", 5.5, 0.5), ("C5", 6, 1), ("E5", 7, 1),
              ("F5", 8, 1.5), ("E5", 9.5, 0.5), ("D5", 10, 1), ("C5", 11, 1),
              ("B4", 12, 2), ("D5", 14, 1), ("G4", 15, 1),
              ("E5", 16, 1.5), ("F5", 17.5, 0.5), ("G5", 18, 1), ("E5", 19, 1),
              ("D5", 20, 1), ("B4", 21, 1), ("G4", 22, 2),
              ("A4", 24, 1), ("C5", 25, 1), ("F5", 26, 1), ("E5", 27, 1),
              ("D5", 28, 2), ("B4", 30, 1), ("C5", 31, 1)]
    for note, at, length in melody:
        start = at * beat + rng.uniform(-0.008, 0.02)
        G.add_at(x, _piano_note(_hz(note), length * beat + 1.6, 0.36, rng), max(0.0, start), circular=True)
    # A small wooden room, slightly muffled: "from nowhere", never in your face.
    x = G.fft_filter(x, lambda f: G.lp_gain(f, 2600, 2) * G.hp_gain(f, 70, 1), circular=True)
    x = x + 0.45 * G.circ_conv(x, G.reverb_ir(rng, 1.6, 0.45, lp=2500, predelay=0.015))
    _save_long_loop(x, G.SND / "level0" / "manila_piano.ogg", peak=0.55)


def _save_long_loop(x: np.ndarray, path, peak: float) -> None:
    """Like G.save_ogg(loop=True), but written in one-second blocks: libsndfile's Vorbis encoder
    overflows its stack when handed a long clip in one go (as G._m_save notes for the music)."""
    import soundfile as sf
    x = np.asarray(x, float)
    x = G.norm(x - x.mean(), peak)
    path.parent.mkdir(parents=True, exist_ok=True)
    gain = 1.0
    for _ in range(6):
        with sf.SoundFile(str(path), "w", G.SR, 1, format="OGG", subtype="VORBIS", compression_level=0.0) as out:
            y = (x * gain).astype(np.float32)
            for i in range(0, len(y), G.SR):
                out.write(y[i:i + G.SR])
        decoded = np.abs(sf.read(str(path))[0]).max()
        if decoded <= max(peak, 0.9) + 0.02:
            break
        gain *= peak / decoded


def snd_level0_a_blackout_buzz() -> None:
    """4 s seamless loop: one fluorescent tube buzzing somewhere in the dark. The hum, the
    ballast's rasp and a faint crackle, wavering a little so it sounds alive."""
    rng = G.rng_for(5103)
    T = 4.0
    n = int(T * G.SR)
    t = np.arange(n) / G.SR
    phase = 2 * np.pi * 60.0 * t                     # 240 whole cycles of 60 Hz
    hum = sum(a * np.sin(h * phase) for h, a in ((1, 0.35), (2, 1.0), (3, 0.3), (4, 0.45), (6, 0.2), (8, 0.1)))
    s = np.sin(2 * phase)
    rasp = G.fft_filter(np.sign(s) * np.abs(s) ** 0.15, lambda f: G.bp_gain(f, 900, 5000, 2), circular=True)
    hum, rasp = G.norm(hum), G.norm(rasp)
    crackle = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 1500, 5000, 2))
    flutter = G.drift(n, rng, 0.25, 0.5, 3.0)
    x = hum + 0.35 * rasp * flutter + 0.05 * crackle * (np.abs(np.sin(phase)) ** 6)
    G.save_ogg(x, G.SND / "level0" / "blackout_buzz.ogg", peak=0.6, loop=True)


SOUNDS = [snd_level0_a_red_rooms_hum, snd_level0_a_manila_piano, snd_level0_a_blackout_buzz]
