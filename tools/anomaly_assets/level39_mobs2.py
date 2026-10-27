"""Level 39 "Megalophobia": Clumps (Entity 5), Skin-stealers (Entity 10) and Wretches (Entity 15).

Textures
--------
The three models (client/ClumpModel, SkinStealerModel, WretchModel) are built in code from many
small boxes, each given a random texture offset INSIDE one material "swatch" (a rectangle of the
texture holding one material: pale skin, raw flesh, nails...). So each swatch here is a seamless,
detailed material tile, not a hand-placed UV map. All three use a 128 x 128 UV layout and the PNGs
are 256 x 256 (2 texels per model pixel).

Sounds
------
Mono 44.1 kHz OGG, synthesized:
* Clump: creaking limbs (stick-slip friction through fleshy resonances), knuckle cracks (the tell
  before it strikes), wet hand claps and pounding on the rock (its lures), the whip of the long
  arm, the wet grab, dragging, hurt and death.
* Skin-stealer: the mimic voice. These are NOT recordings: a small formant synthesizer builds
  speech-like "words" from vowel formants, fricative noise and plosive bursts, then garbles them
  (stutters, wrong pitch jumps, a second voice an octave down, a vocal tract slightly too big).
  Also breathing, heavy bare footsteps, the low rattle when it starts to hunt, tearing, hurt, death.
* Wretch: pained moans, hitching sobs, panicked screams, splashing, lapping water, bare wet
  footsteps, hurt and death.

The tunnels are white marble that "dampens sound", so the reverb tails are short and dark.
Seeds 39200-39499. Run with `python tools/gen_assets.py level39m2` or a single generator name.
"""
from __future__ import annotations

import numpy as np
from PIL import Image, ImageDraw

import gen_assets as G

SR = G.SR
K = 2              # texels per model pixel
N = 128 * K        # texture size (texels)
OUT_TEX = G.TEX / "entity"
OUT_SND = G.SND / "entity"


# =============================================================================
# Texture helpers
# =============================================================================

def _n(rng, shape, beta=2.0, aniso=(1.0, 1.0)) -> np.ndarray:
    return G.fnoise(rng, shape, beta=beta, aniso=aniso)


def _lines(rng, shape, width=0.06, beta=2.4, aniso=(1.0, 1.0)) -> np.ndarray:
    """Thin wandering lines (veins, creases): where a smooth noise field crosses zero."""
    return 1.0 - G.smoothstep(0.0, width, np.abs(_n(rng, shape, beta, aniso)))


def _mix(img, mask, color, amount=1.0) -> np.ndarray:
    m = np.clip(mask * amount, 0.0, 1.0)[..., None]
    return img * (1.0 - m) + np.asarray(color, float)[None, None, :] * m


def _strokes(rng, shape, count, length, width=1.0, angle=None) -> np.ndarray:
    """Short strokes (hairs, scratches) drawn anti-aliased; returns a 0..1 mask."""
    h, w = shape

    def draw(d, k):
        for _ in range(count):
            x = rng.uniform(0, w)
            y = rng.uniform(0, h)
            a = rng.uniform(0, np.pi * 2) if angle is None else angle + rng.normal(0, 0.25)
            ln = length * rng.uniform(0.5, 1.2)
            d.line([(x * k, y * k), ((x + np.cos(a) * ln) * k, (y + np.sin(a) * ln) * k)], fill=255, width=max(1, int(width * k)))
    return G.pil_mask(draw, shape=shape)


def _spots(rng, shape, count, r_lo, r_hi) -> np.ndarray:
    """Soft round spots (moles, scabs, glints); returns 0..1."""
    out = np.zeros(shape)
    for _ in range(count):
        r = rng.uniform(r_lo, r_hi)
        out = np.maximum(out, G.blob(shape, rng.uniform(0, shape[1]), rng.uniform(0, shape[0]), r, r * rng.uniform(0.7, 1.3)))
    return out


def _skin(rng, shape, base, mottle=10.0, pores=4.0, livor=None, dirt=None, veins=None, creases=0.0, hairs=0) -> np.ndarray:
    """A seamless human-skin tile: mottled tone, fine pores, optional livid patches, grime, veins,
    creases and short hairs."""
    img = np.ones(shape + (3,)) * np.asarray(base, float)
    low = _n(rng, shape, 3.2)
    img += low[..., None] * mottle * np.array([1.0, 0.92, 0.88])
    # Slight red-blue variation, like real skin (redder where it's thinner).
    hue = _n(rng, shape, 3.0)
    img += hue[..., None] * np.array([4.0, -1.5, -2.5])
    if livor is not None:
        img = _mix(img, G.smoothstep(0.7, 1.9, _n(rng, shape, 3.0)), livor, 0.55)
    if dirt is not None:
        img = _mix(img, G.smoothstep(0.4, 2.0, _n(rng, shape, 2.2)), dirt, 0.4)
    if veins is not None:
        reach = G.smoothstep(-0.3, 0.8, _n(rng, shape, 3.0))
        img = _mix(img, _lines(rng, shape, 0.05, 2.6) * reach, veins, 0.5)
    if creases > 0:
        img -= (_lines(rng, shape, 0.07, 2.2, (1.0, 5.0)) * creases)[..., None]
    if hairs > 0:
        img = _mix(img, _strokes(rng, shape, hairs, 2.5, 0.5), (60, 45, 38), 0.45)
    # Fine wrinkles running two ways (the skin's grain), then pores.
    img -= (_lines(rng, shape, 0.03, 1.4, (1.0, 2.5)) * 7.0)[..., None]
    img -= (_lines(rng, shape, 0.03, 1.4, (2.5, 1.0)) * 5.0)[..., None]
    img += _n(rng, shape, 0.4)[..., None] * pores
    return img


def _scabs(rng, shape, count, r_lo, r_hi) -> np.ndarray:
    """Irregular crusty scabs: soft spots broken up by noise so no two are round."""
    return G.smoothstep(0.35, 0.6, _spots(rng, shape, count, r_lo, r_hi) * (0.6 + 0.6 * _n(rng, shape, 1.2)))


def _put(img, x, y, tile) -> None:
    """Pastes a tile into the texture at texel (x, y)."""
    h, w = tile.shape[:2]
    img[y:y + h, x:x + w] = tile


def _sw(u, v):
    """A swatch's texel corner from its model-UV corner."""
    return u * K, v * K


def _save(img, name) -> None:
    rgba = np.concatenate([np.clip(img, 0, 255), np.full(img.shape[:2] + (1,), 255.0)], axis=2)
    G.save_png(rgba, OUT_TEX / f"{name}.png")


# =============================================================================
# Clump
# =============================================================================

def tex_level39m2_clump() -> None:
    """clump.png: pale skin, bruised skin, the dark wet core, hands, nails, wrinkled joints."""
    rng = G.rng_for(39201)
    img = np.zeros((N, N, 3))
    big = (128, 128)
    # Pale skin: greyish, cold, livid patches where blood has pooled, grime, blue veins, hairs.
    skin = _skin(rng, big, (192, 164, 148), mottle=11, livor=(150, 112, 132), dirt=(112, 92, 72),
                 veins=(112, 124, 158), creases=14, hairs=260)
    _put(img, *_sw(0, 0), skin)
    # Bruised skin: darker, purple-to-yellow bruises, scabs, scratches, more dirt.
    bruise = _skin(rng, big, (156, 120, 112), mottle=12, livor=(118, 76, 104), dirt=(90, 70, 52),
                   veins=(96, 92, 132), creases=16, hairs=120)
    halo = G.smoothstep(0.9, 2.0, _n(rng, big, 3.0))
    bruise = _mix(bruise, halo, (168, 156, 92), 0.45)
    bruise = _mix(bruise, G.smoothstep(1.4, 2.4, _n(rng, big, 3.0)), (98, 58, 92), 0.7)
    bruise = _mix(bruise, _scabs(rng, big, 22, 1.2, 3.0), (88, 36, 30), 0.85)
    bruise = _mix(bruise, _strokes(rng, big, 40, 6.0, 0.6), (130, 52, 46), 0.6)
    _put(img, *_sw(64, 0), bruise)
    # The core: dark, wet, fibrous flesh with deep crevices and wet glints.
    core = np.ones(big + (3,)) * np.array([88.0, 32.0, 30.0])
    fibres = _n(rng, big, 1.6, (6.0, 1.0)) + 0.6 * _n(rng, big, 1.6, (1.0, 6.0))
    core += fibres[..., None] * np.array([18.0, 6.0, 6.0])
    core += _n(rng, big, 3.0)[..., None] * np.array([20.0, 8.0, 7.0])
    core = _mix(core, G.smoothstep(0.6, 1.8, -_n(rng, big, 2.6)), (26, 8, 9), 0.85)
    core = _mix(core, G.smoothstep(1.2, 2.2, _n(rng, big, 2.4)), (168, 132, 86), 0.35)   # mucus
    core = _mix(core, _spots(rng, big, 70, 0.4, 1.1) > 0.6, (230, 190, 180), 0.8)        # glints
    _put(img, *_sw(0, 64), core)
    # Hands: redder skin with knuckle creases and dirt worked into them.
    hand = _skin(rng, (64, 64), (196, 152, 136), mottle=9, dirt=(104, 80, 62), veins=(118, 120, 150), hairs=30)
    yy = np.arange(64)[:, None] + 3.0 * _n(rng, (64, 64), 2.5)
    knuckle = (np.abs(np.sin(yy * np.pi / 9.0)) < 0.18).astype(float) * G.smoothstep(-0.5, 0.8, _n(rng, (64, 64), 2.0))
    hand = _mix(hand, knuckle, (110, 76, 66), 0.6)
    _put(img, *_sw(64, 64), hand)
    # Nails: yellowed, ridged along their length, dark grime at the edges.
    nail = np.ones((64, 64, 3)) * np.array([204.0, 188.0, 150.0])
    nail += _n(rng, (64, 64), 1.8, (8.0, 1.0))[..., None] * np.array([12.0, 11.0, 8.0])
    nail = _mix(nail, G.smoothstep(0.8, 2.0, _n(rng, (64, 64), 2.4)), (70, 54, 40), 0.75)
    _put(img, *_sw(96, 64), nail)
    # Joints: deep curved creases, reddened.
    joint = _skin(rng, (64, 128), (186, 146, 134), mottle=10, livor=(150, 96, 104), dirt=(100, 78, 60))
    # Curved creases bunched across the joint, darker and reddened in their depths.
    folds = _lines(rng, (64, 128), 0.09, 1.8, (1.0, 6.0)) + 0.6 * _lines(rng, (64, 128), 0.06, 1.8, (1.0, 4.0))
    joint = _mix(joint, np.clip(folds, 0, 1), (112, 70, 66), 0.6)
    joint += (_n(rng, (64, 128), 1.6, (1.0, 6.0)) * 6.0)[..., None]
    _put(img, *_sw(64, 96), joint)
    _save(img, "clump")


# =============================================================================
# Skin-stealer
# =============================================================================

def tex_level39m2_skin_stealer() -> None:
    """skin_stealer.png: its own pale yellow skin, the stolen skin (outside and raw inside), hair,
    bone-tight skin, dark sockets, white eyes, teeth, nails, mouth."""
    rng = G.rng_for(39301)
    img = np.zeros((N, N, 3))
    big = (128, 128)
    # Its own skin: pale sickly yellow, faint grey-green veins, waxy, almost no hair.
    body = _skin(rng, big, (222, 208, 142), mottle=9, livor=(196, 196, 140), veins=(150, 164, 128), creases=8)
    body += (G.smoothstep(1.0, 2.2, _n(rng, big, 1.2)) * 10.0)[..., None]    # waxy sheen
    _put(img, *_sw(0, 0), body)
    # The stolen skin: human tones from several people, sewn and torn. Patches of different tone
    # meet at crude stitched seams; it sags in horizontal folds; lividity, freckles, moles, hairs;
    # dried blood along the tears.
    stolen = _skin(rng, big, (204, 158, 134), mottle=12, livor=(150, 100, 118), dirt=(120, 92, 70),
                   veins=(130, 120, 150), creases=0, hairs=200)
    patch = _n(rng, big, 3.4)
    stolen = _mix(stolen, G.smoothstep(0.3, 0.5, patch), (176, 128, 100), 0.6)
    stolen = _mix(stolen, G.smoothstep(-0.5, -0.3, -patch) * G.smoothstep(0.5, 1.2, _n(rng, big, 3.0)), (226, 186, 164), 0.5)
    seam = 1.0 - G.smoothstep(0.0, 0.035, np.abs(patch - 0.4))
    stolen = _mix(stolen, seam, (100, 52, 44), 0.8)
    # Stitches across the seams: short dark cross strokes, only where the seam is.
    stitch = _strokes(rng, big, 420, 2.2, 0.5) * G.smoothstep(0.2, 0.6, 1.0 - G.smoothstep(0.0, 0.07, np.abs(patch - 0.4)))
    stolen = _mix(stolen, stitch, (40, 26, 22), 0.9)
    folds = _n(rng, big, 2.0, (1.0, 7.0))
    stolen += (folds * 9.0)[..., None]
    stolen -= (_lines(rng, big, 0.05, 2.0, (1.0, 7.0)) * 30.0)[..., None]
    stolen = _mix(stolen, _spots(rng, big, 60, 0.3, 0.8) > 0.6, (122, 82, 62), 0.7)       # freckles, moles
    stolen = _mix(stolen, _strokes(rng, big, 30, 9.0, 0.7, angle=0.0), (168, 136, 140), 0.4)  # stretch marks
    tears = _lines(rng, big, 0.04, 2.0)
    stolen = _mix(stolen, tears * G.smoothstep(0.3, 1.0, _n(rng, big, 3.0)), (92, 34, 28), 0.85)
    _put(img, *_sw(64, 0), stolen)
    # The inside of the stolen skin: dried, reddish-brown tissue with yellow fat specks.
    inner = np.ones((64, 128, 3)) * np.array([138.0, 72.0, 60.0])
    inner += _n(rng, (64, 128), 2.6)[..., None] * np.array([22.0, 12.0, 10.0])
    inner = _mix(inner, _spots(rng, (64, 128), 90, 0.5, 1.4) > 0.5, (214, 186, 120), 0.7)
    inner = _mix(inner, G.smoothstep(0.8, 2.0, _n(rng, (64, 128), 2.0)), (70, 28, 24), 0.6)
    _put(img, *_sw(0, 64), inner)
    # Hair: dark brown, matted, strands running down the hair rods.
    hair = np.ones((64, 64, 3)) * np.array([54.0, 40.0, 30.0])
    hair += _n(rng, (64, 64), 1.4, (8.0, 1.0))[..., None] * np.array([16.0, 12.0, 9.0])
    hair = _mix(hair, G.smoothstep(1.2, 2.2, _n(rng, (64, 64), 1.4, (8.0, 1.0))), (110, 92, 70), 0.5)
    _put(img, *_sw(64, 64), hair)
    # Skin stretched over bone: lighter, waxy yellow with faint pressure marks.
    bone = _skin(rng, (64, 64), (236, 224, 168), mottle=7, creases=6)
    _put(img, *_sw(96, 64), bone)
    # Eye sockets: almost black, a hint of wet red at the edges.
    sock = np.ones((32, 32, 3)) * np.array([22.0, 14.0, 14.0]) + _n(rng, (32, 32), 2.0)[..., None] * np.array([8.0, 3.0, 3.0])
    _put(img, *_sw(0, 96), sock)
    # Eyes: milky white, a faint grey film, no iris at all.
    eye = np.ones((32, 32, 3)) * np.array([236.0, 236.0, 228.0]) + _n(rng, (32, 32), 2.5)[..., None] * np.array([7.0, 7.0, 8.0])
    _put(img, *_sw(16, 96), eye)
    # Teeth: yellow-grey, dark gaps between them.
    xx = np.arange(32)[None, :] + np.zeros((32, 1))
    teeth = np.ones((32, 32, 3)) * np.array([200.0, 188.0, 140.0]) + _n(rng, (32, 32), 2.0)[..., None] * 10.0
    teeth = _mix(teeth, (np.abs(np.sin(xx * np.pi / 3.2)) < 0.25).astype(float), (60, 44, 32), 0.85)
    _put(img, *_sw(32, 96), teeth)
    nails = np.ones((32, 32, 3)) * np.array([72.0, 64.0, 56.0]) + _n(rng, (32, 32), 1.8, (1.0, 8.0))[..., None] * 10.0
    _put(img, *_sw(48, 96), nails)
    mouth = np.ones((32, 32, 3)) * np.array([42.0, 14.0, 16.0]) + _n(rng, (32, 32), 2.0)[..., None] * np.array([14.0, 5.0, 5.0])
    _put(img, *_sw(64, 96), mouth)
    _save(img, "skin_stealer")


# =============================================================================
# Wretch
# =============================================================================

def tex_level39m2_wretch() -> None:
    """wretch.png: grimy emaciated skin with scratch marks, raw flesh where skin was picked off,
    skin over bone, thinning hair, sockets, bloodshot eyes, teeth, mouth, nails, rag cloth."""
    rng = G.rng_for(39401)
    img = np.zeros((N, N, 3))
    big = (128, 128)
    # Starved skin: grey-pink, dirty, bruised, scratched by its own nails, goose-pimpled.
    skin = _skin(rng, big, (180, 148, 138), mottle=12, livor=(140, 104, 118), dirt=(96, 80, 64),
                 veins=(110, 112, 146), creases=12, hairs=60)
    skin = _mix(skin, _strokes(rng, big, 70, 7.0, 0.5, angle=1.2), (168, 62, 58), 0.7)         # scratches
    skin = _mix(skin, _scabs(rng, big, 22, 1.0, 2.4), (96, 40, 34), 0.8)                         # scabs
    skin += (_n(rng, big, 0.2) * 3.0)[..., None]
    _put(img, *_sw(0, 0), skin)
    # Raw flesh: wet red with dark crusted blood, yellowish weeping patches, glints, and pale
    # ragged edges of torn skin.
    raw = np.ones(big + (3,)) * np.array([176.0, 54.0, 52.0])
    raw += _n(rng, big, 2.0)[..., None] * np.array([24.0, 10.0, 10.0])
    raw += _n(rng, big, 1.0, (4.0, 1.0))[..., None] * np.array([10.0, 4.0, 4.0])
    raw = _mix(raw, G.smoothstep(0.9, 1.9, _n(rng, big, 2.6)), (80, 22, 20), 0.85)              # crust
    raw = _mix(raw, G.smoothstep(1.3, 2.3, _n(rng, big, 2.6)), (206, 170, 110), 0.5)             # weeping
    raw = _mix(raw, _lines(rng, big, 0.04, 2.6), (214, 188, 172), 0.8)                            # torn edges
    raw = _mix(raw, _spots(rng, big, 80, 0.3, 0.8) > 0.6, (250, 214, 206), 0.8)                  # wet glints
    _put(img, *_sw(64, 0), raw)
    bone = _skin(rng, (64, 64), (204, 180, 164), mottle=8, dirt=(110, 92, 74), creases=8)
    _put(img, *_sw(0, 64), bone)
    # Hair: dark and greasy, thinning so the scalp shows through.
    hair = np.ones((64, 64, 3)) * np.array([40.0, 32.0, 28.0])
    hair += _n(rng, (64, 64), 1.4, (8.0, 1.0))[..., None] * np.array([12.0, 10.0, 9.0])
    hair = _mix(hair, G.smoothstep(0.6, 1.6, _n(rng, (64, 64), 2.6)), (176, 144, 132), 0.6)
    _put(img, *_sw(32, 64), hair)
    sock = np.ones((32, 32, 3)) * np.array([34.0, 20.0, 24.0]) + _n(rng, (32, 32), 2.0)[..., None] * np.array([10.0, 4.0, 6.0])
    _put(img, *_sw(64, 64), sock)
    # Bloodshot eye whites.
    eye = np.ones((32, 32, 3)) * np.array([232.0, 222.0, 210.0])
    eye = _mix(eye, _lines(rng, (32, 32), 0.08, 2.0), (190, 50, 46), 0.75)
    eye = _mix(eye, G.smoothstep(0.8, 2.0, _n(rng, (32, 32), 2.0)), (220, 150, 140), 0.4)
    _put(img, *_sw(80, 64), eye)
    xx = np.arange(32)[None, :] + np.zeros((32, 1))
    teeth = np.ones((32, 32, 3)) * np.array([190.0, 170.0, 120.0]) + _n(rng, (32, 32), 2.0)[..., None] * 12.0
    teeth = _mix(teeth, (np.abs(np.sin(xx * np.pi / 2.6)) < 0.3).astype(float), (64, 40, 30), 0.85)
    _put(img, *_sw(96, 64), teeth)
    mouth = np.ones((32, 32, 3)) * np.array([60.0, 18.0, 20.0]) + _n(rng, (32, 32), 2.0)[..., None] * np.array([14.0, 5.0, 5.0])
    _put(img, *_sw(112, 64), mouth)
    nails = np.ones((32, 32, 3)) * np.array([170.0, 150.0, 120.0]) + _n(rng, (32, 32), 1.8, (1.0, 8.0))[..., None] * 10.0
    nails = _mix(nails, G.smoothstep(0.5, 1.8, _n(rng, (32, 32), 2.0)), (90, 40, 34), 0.7)  # torn, bloody
    _put(img, *_sw(64, 80), nails)
    # The rag: dirty grey-brown cloth, a coarse weave, water stains and old blood.
    cloth = np.ones((96, 96, 3)) * np.array([108.0, 98.0, 84.0])
    yy, xx = np.mgrid[0:96, 0:96]
    cloth += ((np.sin(xx * np.pi / 1.5) * np.sin(yy * np.pi / 1.5)) * 6.0)[..., None]
    cloth += _n(rng, (96, 96), 2.6)[..., None] * 14.0
    cloth = _mix(cloth, G.smoothstep(0.8, 2.0, _n(rng, (96, 96), 2.4)), (70, 52, 42), 0.6)
    cloth = _mix(cloth, G.smoothstep(1.4, 2.4, _n(rng, (96, 96), 2.4)), (96, 40, 34), 0.5)
    _put(img, *_sw(80, 80), cloth)
    _save(img, "wretch")


# =============================================================================
# Sound helpers
# =============================================================================

def _t(dur):
    return np.arange(int(dur * SR)) / SR


def _env(t, pts):
    """Piecewise-linear envelope through (time, value) points."""
    return np.interp(t, [p[0] for p in pts], [p[1] for p in pts])


def _band(rng, n, lo, hi, order=2):
    return G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, lo, hi, order))[:n]


def _tunnel(rng, x, amount=0.3, decay=0.16, lp=2600.0):
    """The marble tunnels: a short, dark, damped tail."""
    n = len(x)
    wet = G.conv(x, G.reverb_ir(rng, max(0.6, decay * 5), decay, lp=lp, predelay=0.012))[:n]
    return x + amount * G.norm(wet) * np.abs(x).max()


def _pad(x, extra):
    return np.concatenate([x, np.zeros(int(extra * SR))])


def _burst(rng, dur, lo, hi, decay):
    """A short noise burst (a slap, a crack) with an exponential decay."""
    m = int(dur * SR)
    b = _band(rng, m, lo, hi)
    return G.norm(b) * np.exp(-np.arange(m) / SR / decay)


def _thud(rng, dur, f0, decay):
    tt = np.arange(int(dur * SR)) / SR
    return np.sin(2 * np.pi * f0 * tt * (1 - 0.25 * tt / dur)) * np.exp(-tt / decay)


def _creak(rng, dur, rate_lo, rate_hi, res):
    """Stick-slip friction: a train of tiny impulses at a wandering rate, rung through resonances.
    That is how a joint or a stiff limb creaks."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    rate = np.exp(np.log(rate_lo) + (np.log(rate_hi) - np.log(rate_lo)) * (0.5 + 0.5 * G.smooth_jitter(rng, n, 3, 1.0)))
    phase = np.cumsum(rate) / SR
    pulses = np.zeros(n)
    idx = np.nonzero(np.diff(np.floor(phase)) > 0)[0]
    pulses[idx] = rng.uniform(0.4, 1.0, idx.size)
    out = np.zeros(n)
    for f, d, a in res:
        ir = G.ring([f, f * 1.52], [d, d * 0.6], [1.0, 0.4], d * 6)
        out += a * G.conv(pulses, ir)[:n]
    return out * G.smoothstep(0, 0.05, t) * (1 - G.smoothstep(dur - 0.15, dur, t))


# --- The tiny speech synthesizer -----------------------------------------------------------
# Each phoneme: (F1, F2, F3, voicing, noise, noise centre Hz, kind). kind: "v" vowel/sonorant,
# "f" fricative, "h" aspiration (noise through the vowel formants), "p" plosive (closure + burst).
_PH = {
    "a": (730, 1090, 2440, 1.0, 0.0, 0, "v"), "e": (530, 1840, 2480, 1.0, 0.0, 0, "v"),
    "i": (300, 2250, 3000, 1.0, 0.0, 0, "v"), "o": (500, 850, 2400, 1.0, 0.0, 0, "v"),
    "u": (320, 870, 2240, 1.0, 0.0, 0, "v"), "E": (490, 1350, 1690, 1.0, 0.0, 0, "v"),
    "A": (660, 1720, 2410, 1.0, 0.0, 0, "v"), "U": (640, 1190, 2390, 1.0, 0.0, 0, "v"),
    "l": (360, 1300, 2700, 0.6, 0.0, 0, "v"), "r": (420, 1250, 1600, 0.7, 0.0, 0, "v"),
    "m": (260, 1100, 2300, 0.35, 0.0, 0, "v"), "n": (260, 1500, 2500, 0.35, 0.0, 0, "v"),
    "w": (300, 620, 2200, 0.6, 0.0, 0, "v"),
    "v": (300, 1200, 2400, 0.4, 0.25, 4200, "f"), "z": (300, 1500, 2500, 0.3, 0.45, 5600, "f"),
    "D": (300, 1500, 2500, 0.35, 0.2, 3600, "f"), "s": (400, 1600, 2600, 0.0, 0.7, 6000, "f"),
    "f": (400, 1300, 2500, 0.0, 0.3, 5200, "f"), "h": (500, 1500, 2500, 0.0, 0.45, 0, "h"),
    "p": (400, 1000, 2400, 0.0, 0.6, 1100, "p"), "b": (400, 1000, 2400, 0.15, 0.4, 900, "p"),
    "k": (400, 1700, 2500, 0.0, 0.7, 2300, "p"), "t": (400, 1700, 2600, 0.0, 0.7, 4200, "p"),
}


def _speak(rng, phones, f0_pts, tract=1.0):
    """Synthesizes a phoneme list [(symbol, seconds), ...] ('_' = pause) with the pitch contour
    f0_pts [(time 0..1, Hz), ...]. tract > 1 makes the vocal tract bigger (formants lower)."""
    dur = sum(d for _, d in phones) + 0.15
    n = int(dur * SR)
    t = np.arange(n) / SR
    tracks = np.zeros((7, n))  # F1 F2 F3 voice noise noiseFc aspiration
    pos = 0
    plosive_at = []
    for sym, d in phones:
        m = int(d * SR)
        end = min(n, pos + m)
        if sym == "_":
            tracks[3:5, pos:end] = 0.0
            tracks[0:3, pos:end] = tracks[0:3, max(0, pos - 1)][:, None] if pos > 0 else np.array([[500], [1500], [2500]])
        else:
            f1, f2, f3, vo, no, fc, kind = _PH[sym]
            tracks[0, pos:end], tracks[1, pos:end], tracks[2, pos:end] = f1, f2, f3
            tracks[3, pos:end] = vo
            tracks[4, pos:end] = no if kind in ("f", "h") else 0.0
            tracks[5, pos:end] = fc if fc else 3000
            tracks[6, pos:end] = 1.0 if kind == "h" else 0.0
            if kind == "p":
                # Closure (silence) for most of it, then a burst and a breath of aspiration.
                tracks[3, pos:end] = vo * 0.3
                plosive_at.append((pos + int(m * 0.65), fc, no))
        pos = end
    # Coarticulation: the tracks glide instead of jumping (30 ms smoothing).
    k = int(0.03 * SR)
    kern = np.ones(k) / k
    for i in range(7):
        tracks[i] = np.convolve(np.pad(tracks[i], (k, k), mode="edge"), kern, mode="same")[k:-k]
    f0 = _env(t / dur, f0_pts) * (1 + 0.012 * G.smooth_jitter(rng, n, 6, 1.0)) * (1 + 0.004 * np.sin(2 * np.pi * 5.5 * t))
    F1, F2, F3 = tracks[0] / tract, tracks[1] / tract, tracks[2] / tract
    voiced = G.voice(f0, [(F1, 90.0, 1.0), (F2, 120.0, 0.55), (F3, 170.0, 0.25)], rng, nh=48, tilt=0.02)
    voiced = G.norm(voiced) * tracks[3]
    noise = rng.normal(size=n)
    fric = G.svf(noise, tracks[5], 2.5) * tracks[4] * (1 - tracks[6])
    asp = (G.svf(noise, np.minimum(F2, 6000), 4.0) * 0.6 + G.svf(noise, np.minimum(F1, 6000), 3.0)) * tracks[4] * tracks[6]
    bursts = np.zeros(n)
    for at, fc, amt in plosive_at:
        b = _burst(rng, 0.03, fc * 0.6, fc * 1.6, 0.008) * amt
        G.add_at(bursts, b, at / SR, circular=False)
    x = voiced + 0.35 * G.norm(fric) * np.max(tracks[4]) + 0.4 * G.norm(asp) * np.max(tracks[6]) + 0.5 * bursts
    return x


# =============================================================================
# Clump sounds
# =============================================================================

def snd_level39m2_clump_creak() -> None:
    """Limbs creaking as the mass shifts: slow stick-slip creaks through fleshy resonances, with
    soft wet shifts underneath. 3 variants, ~1.6 s."""
    for i in range(1, 4):
        rng = G.rng_for(39210 + i)
        dur = 1.4 + 0.3 * i
        c = _creak(rng, dur, 18 + 6 * i, 70 + 20 * i, [(320 + 60 * i, 0.010, 1.0), (760, 0.006, 0.6), (1450, 0.003, 0.3)])
        n = len(c)
        t = np.arange(n) / SR
        shift = _band(rng, n, 120, 900) * _env(t, [(0, 0), (dur * 0.3, 0.6), (dur * 0.7, 0.3), (dur, 0)])
        x = G.norm(c) + 0.25 * G.norm(shift)
        G.save_ogg(_tunnel(rng, x, 0.25), OUT_SND / f"clump_creak{i}.ogg", peak=0.7, fade_out=0.1)


def snd_level39m2_clump_crack() -> None:
    """Knuckle cracks: a quick ripple of 3-6 sharp pops, like many fingers cracking at once. This is
    the tell before a strike, so it is crisp and close. 3 variants."""
    for i in range(1, 4):
        rng = G.rng_for(39220 + i)
        dur = 0.7
        times = np.sort(rng.uniform(0.02, 0.35, 3 + i))
        pops = G.clicks(rng, times, dur, 1400, 3200, 0.0015, 0.004)
        thump = np.zeros(int(dur * SR))
        for tt in times:
            G.add_at(thump, _thud(rng, 0.05, rng.uniform(140, 220), 0.012) * 0.5, tt, circular=False)
        x = G.norm(pops) + 0.4 * G.norm(thump)
        G.save_ogg(_tunnel(rng, x, 0.2, 0.12), OUT_SND / f"clump_crack{i}.ogg", peak=0.85, fade_out=0.05)


def snd_level39m2_clump_clap() -> None:
    """The lure: three wet, fleshy hand claps from somewhere ahead, echoing off the marble. 2 variants."""
    for i in range(1, 3):
        rng = G.rng_for(39230 + i)
        dur = 1.8
        x = np.zeros(int(dur * SR))
        pos = 0.05
        for _ in range(3):
            clap = _burst(rng, 0.12, 700, 2600, 0.011) + 0.6 * _burst(rng, 0.12, 220, 700, 0.02)
            clap += 0.3 * _burst(rng, 0.12, 400, 1400, 0.035)  # the wet slap after
            G.add_at(x, G.norm(clap) * rng.uniform(0.8, 1.0), pos, circular=False)
            pos += rng.uniform(0.32, 0.46)
        G.save_ogg(_tunnel(rng, x, 0.45, 0.22, 3200), OUT_SND / f"clump_clap{i}.ogg", peak=0.85, fade_out=0.2)


def snd_level39m2_clump_pound() -> None:
    """The lure: a fist (or a lot of them) pounding the rock twice: deep thuds, a fleshy slap,
    grit rattling down. 2 variants."""
    for i in range(1, 3):
        rng = G.rng_for(39240 + i)
        dur = 1.6
        x = np.zeros(int(dur * SR))
        for k, at in enumerate((0.04, 0.5 + 0.08 * i)):
            hit = _thud(rng, 0.4, rng.uniform(55, 75), 0.09) + 0.5 * _thud(rng, 0.4, 140, 0.03)
            hit = G.norm(hit) + 0.4 * _burst(rng, 0.4, 300, 1800, 0.02)
            G.add_at(x, hit * (1.0 if k == 0 else 0.85), at, circular=False)
            grit = G.clicks(rng, at + 0.05 + np.sort(rng.uniform(0, 0.4, 25)), dur, 2500, 6000, 0.0005, 0.0015, amp_pow=2)
            x += 0.08 * G.norm(grit)
        G.save_ogg(_tunnel(rng, x, 0.5, 0.25, 1800), OUT_SND / f"clump_pound{i}.ogg", peak=0.9, fade_out=0.2)


def snd_level39m2_clump_strike() -> None:
    """The long arm whipping out: a rising rush of air and a wet crack at the end."""
    rng = G.rng_for(39251)
    dur = 0.7
    n = int(dur * SR)
    t = np.arange(n) / SR
    fc = 300 + 3500 * (t / 0.3) ** 1.6
    rush = G.svf(rng.normal(size=n), np.minimum(fc, 6000), 1.4) * _env(t, [(0, 0), (0.25, 1.0), (0.31, 0.2), (0.6, 0)])
    snap = np.zeros(n)
    G.add_at(snap, _burst(rng, 0.15, 900, 5000, 0.008) + 0.6 * _burst(rng, 0.15, 200, 900, 0.03), 0.3, circular=False)
    creak = _creak(rng, dur, 60, 160, [(500, 0.006, 1.0), (1200, 0.003, 0.5)]) * _env(t, [(0, 1), (0.3, 1), (0.35, 0)])
    x = G.norm(rush) * 0.8 + G.norm(snap) + 0.3 * G.norm(creak)
    G.save_ogg(_tunnel(rng, x, 0.25), OUT_SND / "clump_strike.ogg", peak=0.9, fade_out=0.08)


def snd_level39m2_clump_grab() -> None:
    """Fingers closing round a body: a wet squeeze, a crunch, creaking tension. 2 variants (the
    second is used, pitched down, for its bites)."""
    for i in range(1, 3):
        rng = G.rng_for(39260 + i)
        dur = 0.9
        n = int(dur * SR)
        t = np.arange(n) / SR
        squelch = _band(rng, n, 200, 1600) * (0.5 + 0.5 * G.smooth_jitter(rng, n, 30, 1.0)) * _env(t, [(0, 0), (0.04, 1), (0.35, 0.4), (0.7, 0)])
        crunch = G.clicks(rng, 0.05 + np.sort(rng.uniform(0, 0.25, 10 + 5 * i)), dur, 900, 3000, 0.001, 0.003)
        squeeze = _creak(rng, dur, 40, 120, [(380, 0.01, 1.0), (900, 0.005, 0.5)]) * _env(t, [(0, 0), (0.1, 1), (0.8, 0.6), (dur, 0)])
        x = G.norm(squelch) * 0.7 + G.norm(crunch) * 0.6 + G.norm(squeeze) * 0.5
        G.save_ogg(_tunnel(rng, x, 0.2), OUT_SND / f"clump_grab{i}.ogg", peak=0.85, fade_out=0.1)


def snd_level39m2_clump_drag() -> None:
    """Something heavy dragged over wet tile and rock toward the hole: scraping, slaps, creaks."""
    for i in range(1, 3):
        rng = G.rng_for(39270 + i)
        dur = 1.0
        n = int(dur * SR)
        t = np.arange(n) / SR
        scrape = _band(rng, n, 400, 3000) * np.clip(0.6 + 0.6 * G.smooth_jitter(rng, n, 18, 1.0), 0, None)
        water = _band(rng, n, 150, 1200) * np.clip(0.5 + 0.7 * G.smooth_jitter(rng, n, 8, 1.0), 0, None)
        creak = _creak(rng, dur, 30, 90, [(300, 0.01, 1.0), (700, 0.006, 0.5)])
        x = (G.norm(scrape) * 0.5 + G.norm(water) * 0.5 + G.norm(creak) * 0.5) * _env(t, [(0, 0), (0.08, 1), (0.85, 1), (dur, 0)])
        G.save_ogg(_tunnel(rng, x, 0.2), OUT_SND / f"clump_drag{i}.ogg", peak=0.75, fade_out=0.1)


def _chorus(rng, dur, f0_lo, f0_hi, voices, vowel=(600, 1000, 2400), env_pts=None, rough=0.02):
    """Several throats groaning at once, slightly out of tune with each other."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for _ in range(voices):
        base = rng.uniform(f0_lo, f0_hi)
        f0 = base * (1 + rough * G.smooth_jitter(rng, n, 12, 1.0)) * _env(t, [(0, 1.0), (dur, rng.uniform(0.7, 0.95))])
        f1, f2, f3 = (v * rng.uniform(0.9, 1.1) for v in vowel)
        out += G.norm(G.voice(f0, [(f1, 110.0, 1.0), (f2, 150.0, 0.5), (f3, 200.0, 0.2)], rng, nh=40))
    if env_pts is not None:
        out *= _env(t, env_pts)
    return out


def snd_level39m2_clump_hurt() -> None:
    """Hurt: a wet squelch and a short groan from many throats at once."""
    rng = G.rng_for(39281)
    dur = 0.7
    n = int(dur * SR)
    t = np.arange(n) / SR
    groan = _chorus(rng, dur, 85, 150, 5, (560, 950, 2400), [(0, 0), (0.05, 1), (0.4, 0.7), (dur, 0)])
    sq = _band(rng, n, 200, 1400) * _env(t, [(0, 1), (0.15, 0.2), (0.3, 0)])
    x = G.norm(groan) + 0.6 * G.norm(sq)
    G.save_ogg(_tunnel(rng, x, 0.3), OUT_SND / "clump_hurt.ogg", peak=0.85, fade_out=0.1)


def snd_level39m2_clump_death() -> None:
    """Death: the chorus of groans sinks and dies out while the limbs crack and go slack, then
    the whole mass slumps wetly."""
    rng = G.rng_for(39291)
    dur = 2.6
    n = int(dur * SR)
    t = np.arange(n) / SR
    groan = _chorus(rng, dur, 70, 140, 7, (520, 900, 2300), [(0, 0), (0.1, 1), (1.4, 0.6), (2.2, 0)], rough=0.04)
    cracks = G.clicks(rng, np.sort(rng.uniform(0.2, 2.0, 24)), dur, 1200, 3200, 0.001, 0.004)
    slump = np.zeros(n)
    G.add_at(slump, _thud(rng, 0.6, 60, 0.15) + 0.5 * _burst(rng, 0.6, 200, 1500, 0.06), 2.0, circular=False)
    x = G.norm(groan) + 0.4 * G.norm(cracks) + 0.8 * G.norm(slump)
    G.save_ogg(_tunnel(rng, x, 0.35), OUT_SND / "clump_death.ogg", peak=0.85, fade_out=0.3)


# =============================================================================
# Skin-stealer sounds
# =============================================================================

# The "words" it copies, as phonemes with rough durations, and the pitch contour of each. These are
# speech-shaped sounds, not real recordings of anyone.
_WORDS = [
    # "hello?" (rising, a question)
    ([("h", 0.07), ("e", 0.11), ("l", 0.08), ("o", 0.30)], [(0, 175), (0.5, 165), (1, 245)]),
    # "help me"
    ([("h", 0.06), ("e", 0.12), ("l", 0.07), ("p", 0.09), ("m", 0.09), ("i", 0.28)], [(0, 195), (0.6, 180), (1, 145)]),
    # "over here"
    ([("o", 0.15), ("v", 0.07), ("E", 0.11), ("_", 0.07), ("h", 0.06), ("i", 0.15), ("r", 0.18)],
     [(0, 150), (0.4, 128), (0.55, 190), (1, 150)]),
    # "who's there?"
    ([("h", 0.06), ("u", 0.17), ("z", 0.11), ("_", 0.04), ("D", 0.07), ("e", 0.13), ("r", 0.17)],
     [(0, 170), (0.5, 160), (1, 205)]),
    # "come back"
    ([("k", 0.08), ("U", 0.12), ("m", 0.11), ("_", 0.05), ("b", 0.06), ("A", 0.17), ("k", 0.09)],
     [(0, 168), (0.6, 160), (1, 135)]),
]


def _garble(rng, phones):
    """Mangles a word the way a bad copy would: stretched or rushed sounds, a stutter on one
    syllable, a sound dropped."""
    out = []
    for i, (sym, d) in enumerate(phones):
        d *= rng.uniform(0.7, 1.6)
        if sym != "_" and rng.random() < 0.12 and i > 0:
            continue  # dropped
        out.append((sym, d))
        if sym in "eiouaEAU" and rng.random() < 0.3:
            # Stutter: the syllable caught and repeated.
            out += [("_", 0.04), (phones[max(0, i - 1)][0], phones[max(0, i - 1)][1]), (sym, d * 0.6)]
    return out


def snd_level39m2_skin_stealer_mimic() -> None:
    """Five garbled 'words', each a speech-like phrase from the synthesizer, mangled and doubled
    by a second voice an octave down that doesn't quite keep up. Heard from down the tunnel."""
    for i, (phones, contour) in enumerate(_WORDS, 1):
        rng = G.rng_for(39310 + i)
        word = _garble(rng, phones)
        shift = rng.uniform(0.85, 1.12)
        # One unnatural jump in pitch somewhere in the word.
        jump_at = rng.uniform(0.3, 0.7)
        pts = [(p, f * shift * (1.25 if p > jump_at else 1.0)) for p, f in contour]
        pts = sorted(pts + [(jump_at, np.interp(jump_at, [p for p, _ in contour], [f for _, f in contour]) * shift)])
        main = _speak(rng, word, pts, tract=1.12)
        low = _speak(rng, word, [(p, f * 0.5) for p, f in pts], tract=1.25)
        n = len(main)
        lag = np.zeros(n)
        d = int(0.028 * SR)
        lag[d:] = low[:n - d] if len(low) >= n - d else np.pad(low, (0, n - d - len(low)))[:n - d]
        x = G.norm(main) + 0.45 * G.norm(lag)
        # Slightly lo-fi, like a poor copy of a voice; then distance and the damped marble.
        x = 0.85 * x + 0.15 * G.bitcrush(G.norm(x), 7, 2)
        x = G.fft_filter(_pad(x, 0.6), lambda f: G.lp_gain(f, 3800, 2) * G.hp_gain(f, 90, 2))
        x = _tunnel(rng, x, 0.55, 0.28, 2400)
        G.save_ogg(x, OUT_SND / f"skin_stealer_mimic{i}.ogg", peak=0.8, fade_in=0.01, fade_out=0.3)


def snd_level39m2_skin_stealer_breath() -> None:
    """Slow, deep, wet breathing with a faint rattle in the throat. 2 variants (~3 s)."""
    for i in range(1, 3):
        rng = G.rng_for(39320 + i)
        dur = 3.2
        n = int(dur * SR)
        t = np.arange(n) / SR
        inh = _band(rng, n, 300, 1800) * _env(t, [(0, 0), (0.6, 0.6), (1.2, 0.2), (1.3, 0)])
        exh = _band(rng, n, 150, 1100) * _env(t, [(1.4, 0), (1.6, 1.0), (2.6, 0.4), (3.1, 0)])
        rattle = _creak(rng, dur, 25, 40, [(180, 0.01, 1.0), (420, 0.006, 0.4)]) * _env(t, [(1.4, 0), (1.7, 1), (2.6, 0.3), (3.0, 0)])
        x = G.norm(inh) * 0.6 + G.norm(exh) + 0.35 * G.norm(rattle)
        G.save_ogg(_tunnel(rng, x, 0.25), OUT_SND / f"skin_stealer_breath{i}.ogg", peak=0.55, fade_out=0.2)


def snd_level39m2_skin_stealer_step() -> None:
    """Heavy bare footfalls on wet tile: a deep thud, the slap of a big flat foot. 3 variants."""
    for i in range(1, 4):
        rng = G.rng_for(39330 + i)
        dur = 0.4
        thud = _thud(rng, dur, rng.uniform(55, 70), 0.06)
        slap = _burst(rng, dur, 300, 2200, 0.012)
        wet = _burst(rng, dur, 600, 3000, 0.04) * 0.3
        x = G.norm(thud) + 0.6 * slap + wet
        G.save_ogg(_tunnel(rng, x, 0.2), OUT_SND / f"skin_stealer_step{i}.ogg", peak=0.8, fade_out=0.05)


def snd_level39m2_skin_stealer_hunt() -> None:
    """The moment it stops pretending: a deep guttural rattle that climbs into a long, wrong,
    rasping intake of breath."""
    rng = G.rng_for(39341)
    dur = 2.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    rattle = _creak(rng, dur, 30, 70, [(140, 0.014, 1.0), (330, 0.008, 0.6), (900, 0.004, 0.3)])
    f0 = _env(t, [(0, 70), (1.0, 95), (2.0, 160)])
    growl = G.voice(f0 * (1 + 0.05 * G.smooth_jitter(rng, n, 30, 1.0)), [(500, 120.0, 1.0), (900, 160.0, 0.5)], rng, nh=40)
    intake = G.svf(rng.normal(size=n), _env(t, [(0, 900), (2.0, 3200)]), 3.0) * _env(t, [(0.9, 0), (1.5, 1), (2.0, 0.6)])
    x = (G.norm(rattle) * 0.8 + G.norm(growl) * 0.6) * _env(t, [(0, 0), (0.15, 1), (1.4, 0.9), (2.0, 0)]) + 0.7 * G.norm(intake)
    x = G.dist(G.norm(x), 2.5)
    G.save_ogg(_tunnel(rng, x, 0.35, 0.22), OUT_SND / "skin_stealer_hunt.ogg", peak=0.9, fade_out=0.15)


def snd_level39m2_skin_stealer_tear() -> None:
    """Hands tearing: a ripping, crackling wet tear. 2 variants."""
    for i in range(1, 3):
        rng = G.rng_for(39350 + i)
        dur = 0.8
        n = int(dur * SR)
        t = np.arange(n) / SR
        rip = G.clicks(rng, np.sort(rng.uniform(0.02, 0.45, 70)), dur, 700, 3500, 0.0008, 0.002)
        body = _band(rng, n, 250, 2500) * _env(t, [(0, 0), (0.03, 1), (0.45, 0.6), (0.6, 0)])
        x = G.norm(rip) * 0.8 + G.norm(body) * 0.6
        G.save_ogg(_tunnel(rng, x, 0.2), OUT_SND / f"skin_stealer_tear{i}.ogg", peak=0.85, fade_out=0.1)


def snd_level39m2_skin_stealer_hurt() -> None:
    """Hurt: a short croak in its two voices at once."""
    rng = G.rng_for(39361)
    x = _speak(rng, [("a", 0.25), ("U", 0.15)], [(0, 210), (0.3, 260), (1, 120)], tract=1.15)
    x2 = _speak(rng, [("a", 0.25), ("U", 0.15)], [(0, 105), (1, 60)], tract=1.3)
    m = min(len(x), len(x2))
    y = G.dist(G.norm(x[:m] + 0.6 * x2[:m]), 3.0)
    G.save_ogg(_tunnel(rng, _pad(y, 0.4), 0.3), OUT_SND / "skin_stealer_hurt.ogg", peak=0.85, fade_out=0.1)


def snd_level39m2_skin_stealer_death() -> None:
    """Death: a long falling wail in both voices, breaking into a wet gurgle."""
    rng = G.rng_for(39371)
    phones = [("a", 0.6), ("o", 0.5), ("u", 0.5)]
    hi = _speak(rng, phones, [(0, 300), (0.3, 340), (1, 110)], tract=1.1)
    lo = _speak(rng, phones, [(0, 150), (1, 55)], tract=1.3)
    m = min(len(hi), len(lo))
    x = G.norm(hi[:m]) + 0.6 * G.norm(lo[:m])
    t = np.arange(m) / SR
    gurgle = _band(rng, m, 100, 900) * np.clip(G.smooth_jitter(rng, m, 14, 1.5), 0, None) * G.smoothstep(1.0, 1.4, t)
    x = x * (1 - G.smoothstep(1.2, 1.6, t)) + 0.6 * G.norm(gurgle)
    G.save_ogg(_tunnel(rng, _pad(x, 0.4), 0.35), OUT_SND / "skin_stealer_death.ogg", peak=0.85, fade_out=0.3)


# =============================================================================
# Wretch sounds
# =============================================================================

def _cry(rng, dur, f0_pts, vowel, breath=0.25, shake=0.03, crack=0.0):
    """A human cry: formant voice with wavering pitch, breathiness and (optionally) voice cracks."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    f0 = _env(t, f0_pts) * (1 + shake * G.smooth_jitter(rng, n, 9, 1.0)) * (1 + 0.012 * np.sin(2 * np.pi * 6.0 * t))
    if crack > 0:
        # Voice cracks: brief upward breaks.
        for _ in range(int(crack)):
            c = rng.uniform(0.2, dur - 0.1)
            f0 *= 1 + 0.35 * np.exp(-0.5 * ((t - c) / 0.03) ** 2)
    f1, f2, f3 = vowel
    v = G.voice(f0, [(f1, 110.0, 1.0), (f2, 150.0, 0.5), (f3, 220.0, 0.2)], rng, nh=40)
    air = G.svf(rng.normal(size=n), np.full(n, f2), 3.0)
    return G.norm(v) * (1 - breath) + G.norm(air) * breath


def snd_level39m2_wretch_moan() -> None:
    """A low, pained moan that wavers and breaks off into a shaky breath. 3 variants (~2 s)."""
    for i in range(1, 4):
        rng = G.rng_for(39410 + i)
        dur = 1.8 + 0.3 * i
        base = rng.uniform(120, 160)
        x = _cry(rng, dur, [(0, base * 0.95), (dur * 0.3, base * 1.1), (dur, base * 0.8)],
                 (520 + 40 * i, 900, 2400), breath=0.3, shake=0.04, crack=1)
        t = np.arange(len(x)) / SR
        x *= _env(t, [(0, 0), (0.15, 1), (dur * 0.7, 0.8), (dur, 0)])
        G.save_ogg(_tunnel(rng, x, 0.3), OUT_SND / f"wretch_moan{i}.ogg", peak=0.6, fade_out=0.2)


def snd_level39m2_wretch_sob() -> None:
    """Sobbing: hitching, juddering inhales and short broken cries. 3 variants (~2 s)."""
    for i in range(1, 4):
        rng = G.rng_for(39420 + i)
        dur = 2.2
        n = int(dur * SR)
        x = np.zeros(n)
        pos = 0.05
        while pos < dur - 0.45:
            # A juddering inhale: 3-4 quick catches of breath.
            for _ in range(int(rng.integers(3, 5))):
                g = _band(rng, int(0.06 * SR), 600, 3500) * np.hanning(int(0.06 * SR))
                G.add_at(x, G.norm(g) * 0.35, pos, circular=False)
                pos += rng.uniform(0.07, 0.1)
            d = rng.uniform(0.25, 0.4)
            top = rng.uniform(300, 380)
            c = _cry(rng, d, [(0, top), (d, top * 0.75)], (680, 1150, 2600), breath=0.35, shake=0.05)
            c *= np.hanning(len(c)) ** 0.5
            G.add_at(x, c * rng.uniform(0.7, 1.0), pos, circular=False)
            pos += d + rng.uniform(0.1, 0.25)
        G.save_ogg(_tunnel(rng, x, 0.3), OUT_SND / f"wretch_sob{i}.ogg", peak=0.6, fade_out=0.15)


def snd_level39m2_wretch_scream() -> None:
    """A panicked scream: high, rough, cracking, running out of breath. 2 variants."""
    for i in range(1, 3):
        rng = G.rng_for(39430 + i)
        dur = 1.3 + 0.2 * i
        top = rng.uniform(520, 640)
        x = _cry(rng, dur, [(0, top * 0.7), (0.12, top), (dur * 0.6, top * 1.05), (dur, top * 0.6)],
                 (950, 1550, 2900), breath=0.2, shake=0.06, crack=3)
        x = G.dist(x, 2.5)
        t = np.arange(len(x)) / SR
        x *= _env(t, [(0, 0), (0.06, 1), (dur * 0.75, 0.9), (dur, 0)])
        G.save_ogg(_tunnel(rng, x, 0.3), OUT_SND / f"wretch_scream{i}.ogg", peak=0.85, fade_out=0.15)


def snd_level39m2_wretch_splash() -> None:
    """Splashing through shallow water in a panic: slaps, sloshes and bubbles. 3 variants."""
    for i in range(1, 4):
        rng = G.rng_for(39440 + i)
        dur = 0.8
        n = int(dur * SR)
        t = np.arange(n) / SR
        slosh = _band(rng, n, 200, 2500) * _env(t, [(0, 0), (0.02, 1), (0.3, 0.4), (0.7, 0)])
        bub = np.zeros(n)
        for _ in range(40):
            f0 = float(np.exp(rng.uniform(np.log(400), np.log(2500))))
            dd = min(0.06, 6.0 / (0.05 * f0))
            bt = np.arange(int(dd * SR)) / SR
            b = np.sin(2 * np.pi * np.cumsum(f0 * (1 + 2.0 * bt)) / SR) * np.exp(-bt / (dd / 4))
            G.add_at(bub, b * rng.uniform(0.2, 1.0), rng.uniform(0.02, 0.5), circular=False)
        x = G.norm(slosh) + 0.4 * G.norm(bub) + 0.5 * _burst(rng, dur, 400, 3000, 0.01)
        G.save_ogg(_tunnel(rng, x, 0.2), OUT_SND / f"wretch_splash{i}.ogg", peak=0.75, fade_out=0.1)


def snd_level39m2_wretch_drink() -> None:
    """Lapping and slurping water from cupped hands: small wet sucks and drips. 2 variants."""
    for i in range(1, 3):
        rng = G.rng_for(39450 + i)
        dur = 1.2
        n = int(dur * SR)
        x = np.zeros(n)
        pos = 0.05
        while pos < dur - 0.2:
            m = int(rng.uniform(0.08, 0.16) * SR)
            tt = np.arange(m) / SR
            suck = G.svf(rng.normal(size=m), 900 + 2500 * tt / tt[-1], 6.0) * np.hanning(m)
            G.add_at(x, G.norm(suck) * rng.uniform(0.5, 1.0), pos, circular=False)
            pos += m / SR + rng.uniform(0.05, 0.18)
        drips = G.clicks(rng, np.sort(rng.uniform(0.1, dur - 0.1, 8)), dur, 900, 2200, 0.004, 0.012)
        x = G.norm(x) + 0.35 * G.norm(drips)
        G.save_ogg(_tunnel(rng, x, 0.2), OUT_SND / f"wretch_drink{i}.ogg", peak=0.55, fade_out=0.1)


def snd_level39m2_wretch_step() -> None:
    """Bare, bony feet slapping wet tile. 3 variants."""
    for i in range(1, 4):
        rng = G.rng_for(39460 + i)
        dur = 0.3
        x = G.norm(_thud(rng, dur, rng.uniform(90, 120), 0.025)) * 0.6 + _burst(rng, dur, 500, 3500, 0.01) + 0.3 * _burst(rng, dur, 800, 4000, 0.03)
        G.save_ogg(_tunnel(rng, x, 0.15), OUT_SND / f"wretch_step{i}.ogg", peak=0.7, fade_out=0.04)


def snd_level39m2_wretch_hurt() -> None:
    """Hurt: a sharp, broken yelp. 2 variants."""
    for i in range(1, 3):
        rng = G.rng_for(39470 + i)
        dur = 0.45
        top = rng.uniform(420, 520)
        x = _cry(rng, dur, [(0, top), (0.08, top * 1.2), (dur, top * 0.7)], (800, 1400, 2800), breath=0.25, shake=0.05, crack=1)
        x *= _env(np.arange(len(x)) / SR, [(0, 0), (0.02, 1), (dur * 0.6, 0.7), (dur, 0)])
        G.save_ogg(_tunnel(rng, _pad(x, 0.3), 0.3), OUT_SND / f"wretch_hurt{i}.ogg", peak=0.8, fade_out=0.1)


def snd_level39m2_wretch_death() -> None:
    """Death: a last falling cry, a rattling breath out, and a body dropping into the water."""
    rng = G.rng_for(39481)
    cry = _cry(rng, 1.1, [(0, 380), (0.2, 420), (1.1, 180)], (700, 1150, 2600), breath=0.3, shake=0.06, crack=2)
    cry *= _env(np.arange(len(cry)) / SR, [(0, 0), (0.04, 1), (0.8, 0.6), (1.1, 0)])
    dur = 2.4
    n = int(dur * SR)
    x = np.zeros(n)
    G.add_at(x, cry, 0.0, circular=False)
    rattle = _creak(rng, 0.7, 20, 35, [(160, 0.01, 1.0), (400, 0.006, 0.4)]) * np.hanning(int(0.7 * SR))
    G.add_at(x, G.norm(rattle) * 0.4, 1.05, circular=False)
    G.add_at(x, G.norm(_thud(rng, 0.4, 70, 0.08) + _burst(rng, 0.4, 200, 2500, 0.05)) * 0.8, 1.7, circular=False)
    G.save_ogg(_tunnel(rng, x, 0.3), OUT_SND / "wretch_death.ogg", peak=0.85, fade_out=0.2)


TEXTURES = [
    tex_level39m2_clump, tex_level39m2_skin_stealer, tex_level39m2_wretch,
]

SOUNDS = [
    snd_level39m2_clump_creak, snd_level39m2_clump_crack, snd_level39m2_clump_clap, snd_level39m2_clump_pound,
    snd_level39m2_clump_strike, snd_level39m2_clump_grab, snd_level39m2_clump_drag, snd_level39m2_clump_hurt,
    snd_level39m2_clump_death,
    snd_level39m2_skin_stealer_mimic, snd_level39m2_skin_stealer_breath, snd_level39m2_skin_stealer_step,
    snd_level39m2_skin_stealer_hunt, snd_level39m2_skin_stealer_tear, snd_level39m2_skin_stealer_hurt,
    snd_level39m2_skin_stealer_death,
    snd_level39m2_wretch_moan, snd_level39m2_wretch_sob, snd_level39m2_wretch_scream, snd_level39m2_wretch_splash,
    snd_level39m2_wretch_drink, snd_level39m2_wretch_step, snd_level39m2_wretch_hurt, snd_level39m2_wretch_death,
]
