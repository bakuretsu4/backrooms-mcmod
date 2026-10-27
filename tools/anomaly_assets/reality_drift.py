"""Separate realities (src/main/java/.../world/RealityDrift.java, registered in
registry/DriftRegistry.java): Level 1's dead lamps and the snap-back thump.

  textures/block/warehouse_light_off.png      the vertical wall tube, dead: a grey tube with
                                              blackened ends, in the same housing
  textures/block/ceiling_tube_off.png         the same for the corridor blocks' ceiling strip
  textures/block/l1x_light_diffuser_off.png   the hanging fixture's diffuser with its tubes out:
                                              grey, the prism grid and the dead insects still there
  sounds/drift/snap_back.ogg                  a very soft, low thump of pressure, like your ears
                                              popping in a lift: heard only by the player whose
                                              room was different, when it snaps back

The dead textures follow the lit ones (gen_assets._tube and level_assets/level1_details
tex_l1x_light, same seeds) so a dead lamp sits in exactly the same housing.

The thump is quiet on purpose (README "Mix"): -30 LUFS, like the background loops, far under the
-18 ceiling. It must feel like something you weren't sure you heard.

Run only these with:  python tools/gen_assets.py reality_drift
"""
from __future__ import annotations

import numpy as np

import gen_assets as G
from anomaly_assets import level2_audio as L2

S = G.S
SR = G.SR


def _blk(name: str):
    return G.TEX / "block" / f"{name}.png"


# ------------------------------------------------------------------ textures ---

def _dead_tube(img: np.ndarray, vertical: bool) -> np.ndarray:
    """gen_assets._tube with the tube gone dark: same rim and housing, a grey tube whose ends
    have blackened (the way a dying fluorescent tube looks)."""
    sh = (S, S)
    if vertical:
        rim, house, core = G.box(sh, 48, 2, 80, S - 2), G.box(sh, 50, 4, 78, S - 4), G.box(sh, 56, 10, 72, S - 10)
        along = G.grid(sh)[1]
    else:
        rim, house, core = G.box(sh, -1, 48, S + 1, 80), G.box(sh, -1, 50, S + 1, 78), G.box(sh, -1, 56, S + 1, 72)
        along = G.grid(sh)[0]
    img = G.over(img, np.array([60, 58, 64]), rim)
    img = G.over(img, np.array([176, 176, 182]), house)
    img = G.over(img, np.array([146, 148, 152]), core)
    if vertical:
        # Blackened ends: the cathodes burnt the glass grey-black near each end cap.
        ends = np.maximum(G.smoothstep(26, 12, along), G.smoothstep(S - 26, S - 12, along))
        img = G.over(img, np.array([70, 70, 74]), core * ends * 0.8)
    else:
        # A horizontal strip tiles end to end: only a faint grey band shows where tubes meet.
        seam = G.smoothstep(10, 0, np.minimum(along, S - along))
        img = G.over(img, np.array([96, 96, 100]), core * seam * 0.6)
    return img


def tex_reality_drift_tubes() -> None:
    G.save_png(_dead_tube(G._concrete_wall(), True), _blk("warehouse_light_off"))
    G.save_png(_dead_tube(G._concrete_wall(122), False), _blk("ceiling_tube_off"))


def tex_reality_drift_diffuser() -> None:
    """level1_details.tex_l1x_light's diffuser with the tubes out: grey instead of glowing white,
    the two tubes faint darker bands, same prism grid and the same dead insects in it."""
    X, Y = G.grid()
    rng = G.rng_for(7800)
    # The housing's two noise draws come first in the lit generator: repeat them so the insect
    # pattern below comes out of the random generator exactly as it does on the lit diffuser.
    G.fnoise(rng, beta=2.2)
    G.fnoise(rng, beta=2.0, aniso=(6, 1))
    diff = np.full((S, S, 3), 150.0) + np.array([0, 1, 4.0])
    diff = G.add_lum(diff, -9 * ((np.abs(Y - S * 0.33) < 10) | (np.abs(Y - S * 0.67) < 10)))
    diff = G.add_lum(diff, -10 * (((X.astype(int) % 6) == 0) | ((Y.astype(int) % 6) == 0)))
    diff = G.add_lum(diff, -40 * ((Y < 6) | (Y > S - 7)))
    diff = G.over(diff, np.array([96, 88, 70.0]), G.smoothstep(1.2, 2.2, G.fnoise(rng, beta=1.6)) * 0.45)
    G.save_png(np.clip(diff, 0, 255), _blk("l1x_light_diffuser_off"))


# ------------------------------------------------------------------ sound ---

def snd_reality_drift_snap() -> None:
    """A soft pressure thump: a low sine sliding down (the "whump" you feel in your ears), a
    little body around 110 Hz so small speakers still carry it, and a breath of low rumbling air
    that swells and settles. No click and no sharp edge: it should feel more than sound."""
    import soundfile as sf
    rng = G.rng_for(20900)
    dur = 1.4
    t = np.arange(int(dur * SR)) / SR
    # Pressure: 58 Hz falling to 34 Hz, a soft 25 ms attack and a slow fall.
    f = 34 + 24 * np.exp(-t / 0.18)
    low = np.sin(2 * np.pi * np.cumsum(f) / SR)
    low_env = (1 - np.exp(-t / 0.025)) * np.exp(-t / 0.32)
    # Body: a muffled knock an octave above, gone faster.
    body = np.sin(2 * np.pi * 112 * t + 0.4) * (1 - np.exp(-t / 0.012)) * np.exp(-t / 0.09)
    # Air: low-passed noise that swells for ~0.2 s and settles, like a door closing far off.
    noise = rng.normal(0, 1, len(t))
    for _ in range(4):
        noise = np.convolve(noise, np.ones(48) / 48, mode="same")
    air = G.norm(noise) * G.smoothstep(0.0, 0.2, t) * np.exp(-np.maximum(t - 0.2, 0) / 0.35)
    x = 1.0 * low * low_env + 0.35 * body + 0.25 * air
    x = x - x.mean()
    fo = int(0.25 * SR)
    x[-fo:] *= np.linspace(1, 0, fo)
    x = L2.loud(x, -30.0, peak=0.7)
    path = G.SND / "drift" / "snap_back.ogg"
    path.parent.mkdir(parents=True, exist_ok=True)
    with sf.SoundFile(str(path), "w", SR, 1, format="OGG", subtype="VORBIS") as out:
        y = x.astype(np.float32)
        for i in range(0, len(y), SR):
            out.write(y[i:i + SR])


TEXTURES = [tex_reality_drift_tubes, tex_reality_drift_diffuser]
SOUNDS = [snd_reality_drift_snap]
