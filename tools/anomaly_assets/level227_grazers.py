"""Level 227 "Megaflora": the grazers. The bear (fifteen eyes, six legs), the doe (the black scar)
and the boar (the largest animal), their food items and their sounds.

Textures follow the UV notes written next to every box in client/L227BearModel.java,
client/L227DoeModel.java and client/L227BoarModel.java. The bear and the boar use a 256 x 256 UV
layout and the doe a 128 x 128 one; every PNG has 2 x 2 texels per model pixel.

How a Minecraft box unfolds on the texture (box w x h x d at texOffs (u, v)):

           u   u+d     u+d+w   u+2d+w  u+2d+2w
      v    .   [ top ][bottom]
      v+d  [ -X ][front][ +X ][ back ]
      v+d+h

Written files:
  textures/entity/l227_bear.png, l227_bear_eyes.png (the faint eyeshine, additive)
  textures/entity/l227_doe.png
  textures/entity/l227_boar.png
  textures/item/boar_meat.png, cooked_boar_meat.png
  sounds/entity/l227_*.ogg (see the SOUNDS list at the end)

Loudness is set in LUFS like the rest of the mix (README "Mix"): creature calls well under the
-18 ceiling, breathing and snoring far quieter. Every generator has its own fixed seed
(22700-22799), so re-running gives identical files.

Run only these with:  python tools/gen_assets.py l227_grazer
"""
from __future__ import annotations

import numpy as np

import gen_assets as G
from anomaly_assets.level2_audio import loud

K = 2                   # texels per model pixel
OUT_TEX = G.TEX / "entity"
SR = G.SR


# =============================================================================
# Texture helpers (a canvas of n x n texels for a UV layout of n/K x n/K)
# =============================================================================

class Canvas:
    """One entity texture being painted: the image, a luminance layer added at the end, and the
    texel grid. Rectangles are given in model-UV units and scaled by K."""

    def __init__(self, uv_size: int):
        self.n = uv_size * K
        self.X, self.Y = G.grid((self.n, self.n))
        self.img = np.zeros((self.n, self.n, 3))
        self.alpha = np.zeros((self.n, self.n))
        self.lum = np.zeros((self.n, self.n))

    def rect(self, x0, y0, x1, y1):
        """(mask, fx, fy) for a UV rectangle: fx, fy run 0..1 across it."""
        x0, y0, x1, y1 = x0 * K, y0 * K, x1 * K, y1 * K
        m = (self.X >= x0) & (self.X < x1) & (self.Y >= y0) & (self.Y < y1)
        fx = (self.X - x0) / max(x1 - x0, 1e-6)
        fy = (self.Y - y0) / max(y1 - y0, 1e-6)
        return m, fx, fy

    def fill(self, r, layer):
        """Copies a full-canvas layer into the UV rectangle r = (x0, y0, x1, y1) and makes it opaque."""
        m, _, _ = self.rect(*r)
        self.img[m] = layer[m]
        self.alpha[m] = 255

    def png(self):
        out = np.concatenate([G.add_lum(self.img, self.lum), self.alpha[..., None]], axis=2)
        return np.clip(out, 0, 255)


def faces(u, v, w, h, d) -> dict:
    """UV rectangles (x0, y0, x1, y1) of the six faces of a box at texOffs (u, v)."""
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def whole(u, v, w, h, d) -> tuple:
    """The UV rectangle covering every face of a box."""
    return (u, v, u + 2 * (d + w), v + d + h)


def shade_box(c: Canvas, box, top=7.0, bottom=-26.0, side_ao=-18.0, convex=6.0) -> None:
    """Fake lighting for one box: lighter top, dark underside, sides darkening toward the bottom,
    and a soft dome across every face so big flat boxes read as rounded bodies."""
    f = faces(*box)
    m, fx, fy = c.rect(*f["top"])
    c.lum[m] += top
    m, fx, fy = c.rect(*f["bottom"])
    c.lum[m] += bottom
    for k in ("right", "front", "left", "back"):
        m, fx, fy = c.rect(*f[k])
        c.lum[m] += (side_ao * G.smoothstep(0.35, 1.0, fy))[m]
    for k, r in f.items():
        m, fx, fy = c.rect(*r)
        dome = 1.0 - ((fx - 0.5) ** 2 + (fy - 0.5) ** 2) * 2.2
        c.lum[m] += (convex * dome)[m]


def dot(c: Canvas, cx, cy, r):
    """Soft round blob at UV (cx, cy), radius r (UV units): 1 in the middle, 0 outside."""
    d2 = ((c.X - cx * K) ** 2 + (c.Y - cy * K) ** 2) / (r * K) ** 2
    return np.clip(1.0 - d2, 0, 1) ** 1.2


def fur(rng, n, base, dark, tip, streak=(1, 9), grizzle=0.35) -> np.ndarray:
    """Coarse animal fur in strands: streaks along `streak`, dark gaps between clumps, lighter
    grizzled tips."""
    img = G.fill(base, (n, n))
    strands = G.fnoise(rng, (n, n), beta=1.4, aniso=streak)
    fine = G.fnoise(rng, (n, n), beta=0.7, aniso=streak, kmin=40)
    img = G.add_lum(img, strands * 7 + fine * 4)
    clumps = G.fnoise(rng, (n, n), beta=2.6)
    img = G.over(img, np.array(dark, float), G.smoothstep(0.2, 1.4, -clumps) * 0.55)
    img = G.over(img, np.array(dark, float), G.smoothstep(1.0, 2.2, -strands) * 0.6)
    tips = G.smoothstep(1.1, 2.3, strands + 0.5 * fine) * G.smoothstep(-0.6, 0.8, clumps)
    img = G.over(img, np.array(tip, float), tips * grizzle)
    return img


# =============================================================================
# The bear
# =============================================================================

BEAR_UV = 256
# Every bear box (u, v, w, h, d), as in L227BearModel.
B_HIPS = (0, 0, 18, 14.5, 16)
B_HIPS_TOP = (0, 128, 14, 1.5, 12)
B_RUMP = (166, 128, 14, 11, 1.5)
B_MID = (70, 0, 19, 16.5, 14)
B_MID_TOP = (54, 128, 15, 1.5, 12)
B_CHEST = (140, 0, 20, 18.5, 12)
B_HUMP = (206, 0, 15, 5, 9)
B_CHEST_TOP = (110, 128, 16, 1.5, 11.5)
B_NECK = (0, 34, 12, 11, 8)
B_SKULL = (42, 34, 14, 11.5, 11)
B_SNOUT = (94, 34, 7.5, 5.5, 8)
B_NOSE = (126, 34, 4, 2.5, 1)
B_MOUTH = (186, 34, 5.6, 2, 6)
B_CHEEKS = (154, 34, 12, 4, 3)
B_SKULL_TOP = (0, 142, 12, 1.2, 9)
B_JOWLS = (44, 142, 13, 2, 6)
B_JAW = (126, 40, 6, 2.5, 7)
B_EAR = (154, 42, 3, 3, 1.5)
B_UPPER = [(p * 26, 58, 6, 8.5, 6) for p in range(3)]
B_LOWER = [(p * 22, 74, 5, 6.5, 5) for p in range(3)]
B_PAW = [(p * 28, 88, 6, 2, 7) for p in range(3)]
B_EYE_SIZES = (2.2, 1.6, 1.2)
B_EYE_UV = (0, 8, 14)
B_LID_UV = (20, 28, 34)
B_SOCKET_UV = (40, 48, 54)
B_STRANDS = (160, 58, 256, 100)
B_CLAWS = (230, 34, 246, 44)
B_FANGS = (200, 44, 216, 52)
B_MOSS = (0, 112, 34, 122)
B_FERN = (40, 112, 58, 120)
B_FUNGUS = (60, 112, 72, 116)

B_BODY = [B_HIPS, B_HIPS_TOP, B_RUMP, B_MID, B_MID_TOP, B_CHEST, B_HUMP, B_CHEST_TOP, B_NECK]
B_HEAD = [B_SKULL, B_SKULL_TOP, B_JOWLS, B_CHEEKS, B_JAW, B_EAR]


def tex_l227_grazer_bear() -> None:
    rng = G.rng_for(22700)
    c = Canvas(BEAR_UV)
    n = c.n
    FUR = (52, 41, 32)
    DARK = (20, 16, 13)
    TIP = (132, 112, 86)
    base = fur(rng, n, FUR, DARK, TIP)
    # Algae and moss stain in the fur of the back: it has lain in the damp a very long time.
    stain = G.smoothstep(0.3, 1.5, G.fnoise(rng, (n, n), beta=2.4))
    base_back = G.over(base, np.array([58, 74, 34]), stain * 0.55)
    face = fur(rng, n, (96, 80, 62), (52, 42, 34), (160, 142, 112), streak=(1, 4), grizzle=0.25)
    snout = fur(rng, n, (118, 100, 78), (70, 58, 46), (176, 160, 130), streak=(4, 1), grizzle=0.2)

    # Body, neck, head and legs: fur. The back pieces get the moss stain on top.
    for b in B_BODY + B_HEAD + B_UPPER + B_LOWER + B_PAW:
        c.fill(whole(*b), base)
    for b in (B_HIPS, B_HIPS_TOP, B_MID, B_MID_TOP, B_CHEST, B_HUMP, B_CHEST_TOP, B_SKULL_TOP):
        m, fx, fy = c.rect(*faces(*b)["top"])
        c.img[m] = base_back[m]
    for b in (B_HIPS_TOP, B_MID_TOP, B_CHEST_TOP, B_HUMP):
        for k in ("right", "left", "front", "back"):
            m, _, _ = c.rect(*faces(*b)[k])
            c.img[m] = base_back[m]
    # The face: a paler, short-furred mask over the skull's front and the cheeks; the snout paler still.
    m, fx, fy = c.rect(*faces(*B_SKULL)["front"])
    c.img[m] = face[m]
    c.fill(whole(*B_CHEEKS), face)
    c.fill(whole(*B_SNOUT), snout)
    # Lighting per box.
    for b in B_BODY + B_HEAD + [B_SNOUT] + B_UPPER + B_LOWER:
        shade_box(c, b)
    for b in B_PAW:
        shade_box(c, b, bottom=-40, convex=2.0)
        m, fx, fy = c.rect(*faces(*b)["bottom"])
        # Paw pads: black leather.
        c.img[m] = np.array([26, 22, 21])
        c.lum[m] += (np.sin(fx * np.pi * 4) * 4)[m]
    # Fur hangs darker and thicker toward the bottom of the flanks and legs.
    for b in B_BODY + B_UPPER + B_LOWER:
        for k in ("right", "left", "front", "back"):
            m, fx, fy = c.rect(*faces(*b)[k])
            c.lum[m] -= (G.smoothstep(0.5, 1.0, fy) * 8)[m]

    # Eye sockets (thin plates around each eye): dark, wet, hairless skin, wrinkled at the rim.
    for s, u in enumerate(B_SOCKET_UV):
        r = B_EYE_SIZES[s] + 0.9
        box = (u, 100, r, r, 0.2)
        m, fx, fy = c.rect(*whole(*box))
        skin = G.add_lum(G.fill((34, 24, 22), (n, n)), G.fnoise(rng, (n, n), beta=1.2) * 6)
        c.img[m] = skin[m]
        c.alpha[m] = 255
        m, fx, fy = c.rect(*faces(*box)["front"])
        d = np.maximum(np.abs(fx - 0.5), np.abs(fy - 0.5))
        c.lum[m] += (G.smoothstep(0.32, 0.5, d) * 14 - 6)[m]
    # Nose: black, wet, with nostrils and a shine.
    c.fill(whole(*B_NOSE), G.fill((24, 21, 21), (n, n)))
    m, fx, fy = c.rect(*faces(*B_NOSE)["front"])
    for nx in (0.28, 0.72):
        c.lum[m] -= (np.clip(1 - ((fx - nx) / 0.14) ** 2 - ((fy - 0.55) / 0.3) ** 2, 0, 1) * 18)[m]
    c.lum[m] += (np.clip(1 - ((fx - 0.5) / 0.4) ** 2 - ((fy - 0.18) / 0.12) ** 2, 0, 1) * 40)[m]
    # Lips and gums along the snout's lower edge and the jaw's top.
    for k in ("right", "left", "front"):
        m, fx, fy = c.rect(*faces(*B_SNOUT)[k])
        c.img[m] = G.lerp(c.img[m], np.array([40, 26, 24]), (G.smoothstep(0.75, 0.95, fy) * 0.9)[m][:, None])
    m, fx, fy = c.rect(*faces(*B_JAW)["top"])
    c.img[m] = G.lerp(np.array([110, 44, 46]), np.array([60, 20, 24]), fy[m][:, None])
    # Inside of the mouth: dark wet red.
    wet = G.fnoise(rng, (n, n), beta=2.0)
    inner = G.lerp(np.array([96, 30, 32]), np.array([34, 10, 12]), G.smoothstep(-1, 1.2, wet)[..., None])
    c.fill(whole(*B_MOUTH), inner)
    # Ears: the inside darker.
    m, fx, fy = c.rect(*faces(*B_EAR)["front"])
    c.img[m] = G.lerp(c.img[m], np.array([30, 22, 20]), 0.6)

    # Eyeballs: glossy black, a thin amber ring, a sharp highlight.
    for s, u in enumerate(B_EYE_UV):
        r = B_EYE_SIZES[s]
        box = (u, 100, r, r, 0.8)
        c.fill(whole(*box), G.fill((10, 9, 10), (n, n)))
        m, fx, fy = c.rect(*faces(*box)["front"])
        d = np.sqrt((fx - 0.5) ** 2 + (fy - 0.5) ** 2)
        c.img[m] = G.lerp(c.img[m], np.array([86, 58, 24]), (G.smoothstep(0.3, 0.38, d) * (1 - G.smoothstep(0.42, 0.5, d)) * 0.7)[m][:, None])
        c.lum[m] += (np.clip(1 - ((fx - 0.32) / 0.14) ** 2 - ((fy - 0.3) / 0.14) ** 2, 0, 1) * 210)[m]
    # Lids: short fur over dark skin, a black lash line along the bottom edge.
    lidfur = fur(rng, n, (58, 46, 37), DARK, TIP, streak=(1, 3), grizzle=0.2)
    for s, u in enumerate(B_LID_UV):
        r = B_EYE_SIZES[s]
        box = (u, 100, r + 0.4, r + 0.25, 0.3)
        c.fill(whole(*box), lidfur)
        m, fx, fy = c.rect(*faces(*box)["front"])
        c.img[m] = G.lerp(c.img[m], np.array([14, 10, 10]), (G.smoothstep(0.7, 0.92, fy))[m][:, None])

    # Fur strands (the shag): dark roots, grizzled tips.
    strands = fur(rng, n, (40, 31, 25), DARK, (150, 128, 98), streak=(1, 16), grizzle=0.5)
    m, fx, fy = c.rect(*B_STRANDS)
    c.img[m] = strands[m]
    c.alpha[m] = 255
    # Claws: black horn, ridged, paling at the tips.
    m, fx, fy = c.rect(*B_CLAWS)
    claw = G.lerp(np.array([22, 20, 19]), np.array([118, 106, 86]), G.smoothstep(0.5, 1.0, fy)[..., None])
    c.img[m] = G.add_lum(claw, G.fnoise(rng, (n, n), beta=1.4, aniso=(1, 10)) * 7)[m]
    c.alpha[m] = 255
    # Fangs: yellowed ivory, stained at the root.
    m, fx, fy = c.rect(*B_FANGS)
    c.img[m] = G.lerp(np.array([132, 110, 72]), np.array([226, 214, 184]), G.smoothstep(0.0, 0.6, fy)[..., None])[m]
    c.alpha[m] = 255
    # Moss pads: vivid, bumpy green with sporophyte specks.
    moss = G.fill((66, 104, 38), (n, n))
    moss = G.add_lum(moss, G.fnoise(rng, (n, n), beta=1.2) * 12 + rng.normal(0, 4, (n, n)))
    moss = G.over(moss, np.array([104, 132, 50]), G.smoothstep(1.4, 2.2, G.fnoise(rng, (n, n), beta=0.6)) * 0.8)
    moss = G.over(moss, np.array([34, 54, 24]), G.smoothstep(0.6, 1.6, -G.fnoise(rng, (n, n), beta=2.0)) * 0.6)
    m, _, _ = c.rect(*B_MOSS)
    c.img[m] = moss[m]
    c.alpha[m] = 255
    # Fern fronds: green with dark, diagonal gaps between leaflets and a pale midrib.
    m, fx, fy = c.rect(*B_FERN)
    pinnae = np.sin((c.X + c.Y * 0.8) * 1.6)
    fern = G.lerp(np.array([40, 82, 34]), np.array([86, 132, 52]), G.smoothstep(-0.3, 0.8, pinnae)[..., None])
    c.img[m] = fern[m]
    c.alpha[m] = 255
    # Bracket fungus: orange-tan bands on top, pale cream gills underneath.
    m, fx, fy = c.rect(*B_FUNGUS)
    bands = np.sin(c.Y * 1.3 + G.fnoise(rng, (n, n), beta=2.0) * 1.5)
    fungus = G.lerp(np.array([150, 86, 40]), np.array([206, 150, 84]), G.smoothstep(-0.5, 0.8, bands)[..., None])
    c.img[m] = fungus[m]
    c.alpha[m] = 255

    G.save_png(c.png(), OUT_TEX / "l227_bear.png")

    # The eyeshine: black (adds nothing) except a faint amber glow in each pupil.
    glow = Canvas(BEAR_UV)
    for s, u in enumerate(B_EYE_UV):
        r = B_EYE_SIZES[s]
        box = (u, 100, r, r, 0.8)
        m, fx, fy = glow.rect(*faces(*box)["front"])
        d = np.sqrt((fx - 0.5) ** 2 + (fy - 0.5) ** 2)
        g = (1 - G.smoothstep(0.12, 0.42, d))
        glow.img[m] = (np.array([120, 84, 34])[None, :] * g[m][:, None])
    glow.alpha[:] = 255
    G.save_png(glow.png(), OUT_TEX / "l227_bear_eyes.png")


# =============================================================================
# The doe
# =============================================================================

DOE_UV = 128
# Every doe box (u, v, w, h, d), as in L227DoeModel.
D_TORSO = (0, 0, 7, 7.5, 18)
D_CHEST = (52, 0, 7.2, 9, 7)
D_RUMP = (82, 0, 7.6, 7.7, 6)
D_TAIL = (112, 0, 2.4, 4, 1.5)
D_NECK = (0, 26, 3.4, 12, 3.8)
D_THROAT = (16, 26, 3.8, 4, 2.6)
D_SKULL = (30, 26, 4.4, 4, 4.5)
D_MUZZLE = (50, 26, 3, 2.6, 4)
D_NOSE = (66, 26, 2, 1.3, 0.5)
D_JAW = (66, 30, 2.6, 0.8, 4)
D_EAR = (82, 26, 2.2, 4.5, 0.5)
D_EYE = (90, 26, 0.3, 1.2, 1.2)
D_FRONT_UPPER = (0, 44, 2.2, 7.5, 2.4)
D_FRONT_LOWER = (10, 44, 1.2, 7.7, 1.2)
D_HOOF = (16, 44, 1.4, 1.3, 1.6)
D_THIGH = (24, 44, 2.8, 8, 3.2)
D_CANNON = (38, 44, 1.2, 9.3, 1.2)
D_SCAR = (0, 64, 20, 80)
D_LIPS = (0, 84, 20, 98)

D_COAT_BOXES = [D_TORSO, D_CHEST, D_RUMP, D_TAIL, D_NECK, D_THROAT, D_SKULL, D_MUZZLE, D_JAW, D_EAR,
                D_FRONT_UPPER, D_FRONT_LOWER, D_THIGH, D_CANNON]


def tex_l227_grazer_doe() -> None:
    rng = G.rng_for(22710)
    c = Canvas(DOE_UV)
    n = c.n
    # A short, sleek coat, a little too pale and a little too grey: a deer seen through fog.
    coat = fur(rng, n, (146, 136, 118), (104, 96, 84), (186, 178, 160), streak=(1, 4), grizzle=0.2)
    cream = fur(rng, n, (204, 196, 178), (170, 162, 146), (226, 220, 206), streak=(1, 3), grizzle=0.15)
    legs = fur(rng, n, (116, 104, 90), (74, 66, 58), (150, 140, 124), streak=(1, 6), grizzle=0.15)
    for b in D_COAT_BOXES:
        c.fill(whole(*b), coat)
    for b in (D_FRONT_UPPER, D_FRONT_LOWER, D_THIGH, D_CANNON):
        c.fill(whole(*b), legs)
    # Countershading: the belly, the inside of the legs and under the neck fade to cream.
    for b in (D_TORSO, D_CHEST, D_RUMP):
        m, fx, fy = c.rect(*faces(*b)["bottom"])
        c.img[m] = cream[m]
        for k in ("right", "left"):
            m, fx, fy = c.rect(*faces(*b)[k])
            c.img[m] = G.lerp(c.img[m], cream[m], G.smoothstep(0.6, 0.95, fy)[m][:, None])
    m, fx, fy = c.rect(*faces(*D_THROAT)["front"])
    c.img[m] = cream[m]                                  # the pale throat patch
    for k in ("front", "bottom"):
        m, fx, fy = c.rect(*faces(*D_JAW)[k])
        c.img[m] = cream[m]
    # The tail: brown on top, a white flag underneath (it shows when she raises it).
    for k in ("front", "bottom"):
        m, fx, fy = c.rect(*faces(*D_TAIL)[k])
        c.img[m] = G.fill((232, 228, 220), (n, n))[m]
    # Lighting.
    for b in D_COAT_BOXES:
        shade_box(c, b, top=6.0, bottom=-14.0, side_ao=-10.0, convex=5.0)
    # The muzzle: darker at the nose end, a pale band behind the nose, a dark chin.
    for k in ("right", "left", "top"):
        m, fx, fy = c.rect(*faces(*D_MUZZLE)[k])
        along = fx if k == "left" else (1 - fx) if k == "right" else 1 - fy
        c.lum[m] -= (G.smoothstep(0.55, 1.0, along) * 18)[m]
    c.fill(whole(*D_NOSE), G.fill((22, 20, 21), (n, n)))
    m, fx, fy = c.rect(*faces(*D_NOSE)["front"])
    c.lum[m] += (np.clip(1 - ((fx - 0.5) / 0.35) ** 2 - ((fy - 0.25) / 0.2) ** 2, 0, 1) * 45)[m]
    # Ears: a dark rim, pale and almost hairless inside (the front face).
    m, fx, fy = c.rect(*faces(*D_EAR)["front"])
    rim = np.maximum(np.abs(fx - 0.5) * 2, fy < 0.12)
    c.img[m] = G.lerp(np.array([196, 168, 160]), np.array([58, 50, 46]), G.smoothstep(0.7, 0.95, rim)[m][:, None])
    for k in ("back", "right", "left", "top"):
        m, fx, fy = c.rect(*faces(*D_EAR)[k])
        c.lum[m] -= (G.smoothstep(0.0, 0.25, 0.25 - fy) * 30)[m]
    # Eyes: big, glossy, black; a soft highlight; a darker ring of skin round them.
    c.fill(whole(*D_EYE), G.fill((12, 11, 12), (n, n)))
    for k in ("right", "left"):
        m, fx, fy = c.rect(*faces(*D_EYE)[k])
        c.lum[m] += (np.clip(1 - ((fx - 0.35) / 0.18) ** 2 - ((fy - 0.3) / 0.18) ** 2, 0, 1) * 150)[m]
    # Legs: darker and greyer toward the hooves; hooves black and split.
    for b in (D_FRONT_LOWER, D_CANNON):
        for k in ("right", "front", "left", "back"):
            m, fx, fy = c.rect(*faces(*b)[k])
            c.lum[m] -= (G.smoothstep(0.4, 1.0, fy) * 22)[m]
    c.fill(whole(*D_HOOF), G.fill((30, 27, 26), (n, n)))
    m, fx, fy = c.rect(*faces(*D_HOOF)["front"])
    c.lum[m] -= (np.exp(-((fx - 0.5) / 0.06) ** 2) * 20)[m]
    # The scar: black as the void, with a faint wet shine along it.
    m, fx, fy = c.rect(*D_SCAR)
    shine = G.smoothstep(1.6, 2.4, G.fnoise(rng, (n, n), beta=1.0, aniso=(1, 6)))
    c.img[m] = G.lerp(np.array([4, 3, 4]), np.array([34, 30, 36]), shine[..., None])[m]
    c.alpha[m] = 255
    # Its lips: raw, pinkish grey skin, darker toward the opening.
    m, fx, fy = c.rect(*D_LIPS)
    raw = G.add_lum(G.fill((150, 112, 108), (n, n)), G.fnoise(rng, (n, n), beta=1.4) * 9)
    c.img[m] = raw[m]
    c.alpha[m] = 255
    G.save_png(c.png(), OUT_TEX / "l227_doe.png")


# =============================================================================
# The boar
# =============================================================================

BOAR_UV = 256
# Every boar box (u, v, w, h, d), as in L227BoarModel.
R_BARREL = (0, 0, 26, 24, 38)
R_SHOULDER = (128, 0, 28, 27, 16)
R_RUMP = (128, 46, 24, 22, 10)
R_BELLY = (0, 66, 22, 3, 28)
R_CREST = (0, 100, 7, 6, 30)
R_SKULL = (76, 100, 20, 18, 12)
R_FACE = (140, 100, 16, 14.5, 8)
R_SNOUT = (188, 100, 12, 10, 10)
R_DISC = (232, 100, 10, 8, 1.5)
R_MOUTH = (206, 120, 9, 2, 14)
R_JAW = (0, 136, 11, 4, 15)
R_CHEEK = (52, 136, 3, 9, 10)
R_EAR = (80, 136, 5, 6, 1)
R_EYE = (94, 136, 0.4, 1.6, 1.6)
R_TUSKS = (100, 136, 132, 152)
R_HOOF = (136, 136, 6.6, 2.5, 6.6)
R_TAIL = (166, 136, 1.6, 9, 1.6)
R_TUFT = (180, 136, 3, 5, 3)
R_UPPER = [(0, 156, 8, 10.5, 8), (34, 156, 8, 10.5, 8)]
R_LOWER = [(68, 156, 6, 7.5, 6), (94, 156, 6, 7.5, 6)]
R_BRISTLES = (120, 156, 256, 200)
R_MOSS = (0, 180, 52, 194)
R_FERN = (56, 180, 76, 188)

R_HIDE = [R_BARREL, R_SHOULDER, R_RUMP, R_BELLY, R_CREST, R_SKULL, R_FACE, R_SNOUT, R_JAW, R_CHEEK, R_EAR,
          R_TAIL] + R_UPPER + R_LOWER


def tex_l227_grazer_boar() -> None:
    rng = G.rng_for(22720)
    c = Canvas(BOAR_UV)
    n = c.n
    # Coarse, wiry, dark grey-brown hide, grizzled, with old pale scars and caked mud.
    hide = fur(rng, n, (66, 58, 50), (30, 26, 23), (150, 134, 112), streak=(1, 5), grizzle=0.4)
    mossy = G.over(hide, np.array([62, 80, 38]), G.smoothstep(0.4, 1.6, G.fnoise(rng, (n, n), beta=2.5)) * 0.6)
    mud = G.fill((88, 70, 50), (n, n))
    mud = G.add_lum(mud, G.fnoise(rng, (n, n), beta=1.8) * 10 + rng.normal(0, 3, (n, n)))
    skin = G.add_lum(G.fill((152, 124, 114), (n, n)), G.fnoise(rng, (n, n), beta=2.2) * 8)
    for b in R_HIDE:
        c.fill(whole(*b), hide)
    # The back: moss and algae in the bristles.
    for b in (R_BARREL, R_SHOULDER, R_RUMP, R_CREST):
        m, fx, fy = c.rect(*faces(*b)["top"])
        c.img[m] = mossy[m]
    # The belly and the underside: pale, wrinkled, thin-haired skin (it shows when it lounges).
    for b in (R_BELLY, R_BARREL, R_SHOULDER, R_RUMP):
        m, fx, fy = c.rect(*faces(*b)["bottom"])
        c.img[m] = skin[m]
        wrinkles = np.sin(c.Y * 1.9 + G.fnoise(rng, (n, n), beta=2.0) * 2.0)
        c.lum[m] -= (G.smoothstep(0.6, 1.0, wrinkles) * 14)[m]
    for k in ("right", "left", "front", "back"):
        m, fx, fy = c.rect(*faces(*R_BELLY)[k])
        c.img[m] = G.lerp(c.img[m], skin[m], 0.65)
    # Old scars: a few long pale welts down the flanks (it has fought).
    def welts(d, k):
        for _ in range(26):
            x, y = rng.uniform(0, n), rng.uniform(0, n)
            a = rng.uniform(-0.5, 0.5) + np.pi / 2 * rng.integers(0, 2)
            length = rng.uniform(8, 24)
            d.line([(x * k, y * k), ((x + np.cos(a) * length) * k, (y + np.sin(a) * length) * k)],
                   fill=255, width=max(1, int(rng.uniform(1.0, 2.0) * k)))
    scars = G.pil_mask(welts, (n, n))
    for b in (R_BARREL, R_SHOULDER, R_RUMP):
        for k in ("right", "left"):
            m, fx, fy = c.rect(*faces(*b)[k])
            c.img[m] = G.lerp(c.img[m], np.array([130, 104, 94]), (scars * 0.55)[m][:, None])
    # Mud caked up the legs and the lower flanks.
    for b in R_UPPER + R_LOWER + [R_BARREL, R_SHOULDER, R_RUMP]:
        for k in ("right", "front", "left", "back"):
            m, fx, fy = c.rect(*faces(*b)[k])
            lim = 0.15 if b in R_LOWER else 0.55 if b in R_UPPER else 0.78
            edge = lim + G.fnoise(rng, (n, n), beta=2.0) * 0.06
            c.img[m] = G.lerp(c.img[m], mud[m], (G.smoothstep(edge - 0.04, edge + 0.04, fy) * 0.8)[m][:, None])
    # Lighting.
    for b in R_HIDE:
        shade_box(c, b, top=6.0, bottom=-16.0, side_ao=-12.0, convex=7.0)
    # The snout: darker, nearly hairless skin; the disc pinkish grey, wet, two big nostrils.
    for b in (R_SNOUT,):
        for k in ("right", "left", "top", "bottom"):
            m, fx, fy = c.rect(*faces(*b)[k])
            c.img[m] = G.lerp(c.img[m], np.array([70, 58, 54]), 0.5)
    c.fill(whole(*R_DISC), G.add_lum(G.fill((128, 100, 96), (n, n)), G.fnoise(rng, (n, n), beta=1.5) * 6))
    m, fx, fy = c.rect(*faces(*R_DISC)["front"])
    for nx in (0.3, 0.7):
        c.lum[m] -= (np.clip(1 - ((fx - nx) / 0.11) ** 2 - ((fy - 0.55) / 0.2) ** 2, 0, 1) * 90)[m]
    c.lum[m] += (np.clip(1 - ((fx - 0.5) / 0.45) ** 2 - ((fy - 0.2) / 0.12) ** 2, 0, 1) * 30)[m]
    # Lips, gums and the inside of the mouth.
    m, fx, fy = c.rect(*faces(*R_JAW)["top"])
    c.img[m] = G.lerp(np.array([132, 76, 76]), np.array([80, 36, 38]), fy[m][:, None])
    wet = G.fnoise(rng, (n, n), beta=2.0)
    c.fill(whole(*R_MOUTH), G.lerp(np.array([110, 44, 46]), np.array([40, 14, 16]), G.smoothstep(-1, 1.2, wet)[..., None]))
    # Warty cheeks.
    m, fx, fy = c.rect(*whole(*R_CHEEK))
    warts = G.smoothstep(1.3, 2.0, G.fnoise(rng, (n, n), beta=0.4))
    c.lum[m] += (warts * 20)[m]
    # Ears: darker, the inside pink-grey.
    m, fx, fy = c.rect(*faces(*R_EAR)["front"])
    c.img[m] = G.lerp(c.img[m], np.array([120, 92, 88]), 0.55)
    # Eyes: tiny, black, a wet red-brown rim.
    c.fill(whole(*R_EYE), G.fill((14, 10, 10), (n, n)))
    for k in ("right", "left"):
        m, fx, fy = c.rect(*faces(*R_EYE)[k])
        d = np.maximum(np.abs(fx - 0.5), np.abs(fy - 0.5))
        c.img[m] = G.lerp(c.img[m], np.array([96, 40, 34]), G.smoothstep(0.3, 0.45, d)[m][:, None])
        c.lum[m] += (np.clip(1 - ((fx - 0.4) / 0.15) ** 2 - ((fy - 0.35) / 0.15) ** 2, 0, 1) * 120)[m]
    # Tusks: yellowed ivory, stained brown toward the root, worn whiter at the tips.
    m, fx, fy = c.rect(*R_TUSKS)
    tusk = G.lerp(np.array([150, 124, 84]), np.array([232, 222, 196]), G.smoothstep(0.0, 0.9, fy)[..., None])
    c.img[m] = G.add_lum(tusk, G.fnoise(rng, (n, n), beta=1.4, aniso=(1, 8)) * 8)[m]
    c.alpha[m] = 255
    # Hooves: black-grey horn, split.
    c.fill(whole(*R_HOOF), G.add_lum(G.fill((40, 36, 34), (n, n)), G.fnoise(rng, (n, n), beta=1.2, aniso=(1, 6)) * 6))
    m, fx, fy = c.rect(*faces(*R_HOOF)["front"])
    c.lum[m] -= (np.exp(-((fx - 0.5) / 0.05) ** 2) * 30)[m]
    # The tail tuft: a brush of long dark hair.
    c.fill(whole(*R_TUFT), fur(rng, n, (40, 34, 30), (18, 15, 13), (120, 104, 86), streak=(1, 12), grizzle=0.3))
    # Bristles: black at the root, tan tips.
    m, fx, fy = c.rect(*R_BRISTLES)
    bristles = fur(rng, n, (44, 38, 34), (16, 14, 12), (168, 146, 112), streak=(1, 16), grizzle=0.55)
    c.img[m] = bristles[m]
    c.alpha[m] = 255
    # Moss pads and ferns (as on the bear).
    moss = G.fill((66, 104, 38), (n, n))
    moss = G.add_lum(moss, G.fnoise(rng, (n, n), beta=1.2) * 12 + rng.normal(0, 4, (n, n)))
    moss = G.over(moss, np.array([104, 132, 50]), G.smoothstep(1.4, 2.2, G.fnoise(rng, (n, n), beta=0.6)) * 0.8)
    m, _, _ = c.rect(*R_MOSS)
    c.img[m] = moss[m]
    c.alpha[m] = 255
    m, fx, fy = c.rect(*R_FERN)
    pinnae = np.sin((c.X + c.Y * 0.8) * 1.6)
    c.img[m] = G.lerp(np.array([40, 82, 34]), np.array([86, 132, 52]), G.smoothstep(-0.3, 0.8, pinnae)[..., None])[m]
    c.alpha[m] = 255
    G.save_png(c.png(), OUT_TEX / "l227_boar.png")


# =============================================================================
# Item textures (16 x 16): boar meat, raw and cooked
# =============================================================================

def _meat(cooked: bool) -> np.ndarray:
    """A thick slab of boar: dark red (or browned) meat, a cap of fat and a strip of bristly hide."""
    rng = G.rng_for(22731 if cooked else 22730)
    img = np.zeros((16, 16, 4))
    X, Y = np.meshgrid(np.arange(16) + 0.5, np.arange(16) + 0.5)
    # A rounded slab lying diagonally across the icon.
    u = (X - 8) * 0.8 + (Y - 8) * 0.6
    v = -(X - 8) * 0.6 + (Y - 8) * 0.8
    slab = (np.abs(u) / 7.0) ** 2.4 + (np.abs(v) / 4.6) ** 2.4 <= 1.0
    fat = slab & (v < -2.2)
    hide = slab & (v < -3.4)
    if cooked:
        meat_col, fat_col, hide_col = np.array([122, 70, 38]), np.array([206, 160, 100]), np.array([70, 40, 24])
    else:
        meat_col, fat_col, hide_col = np.array([150, 38, 42]), np.array([226, 206, 190]), np.array([60, 52, 46])
    noise = rng.normal(0, 1, (16, 16))
    col = np.where(hide[..., None], hide_col, np.where(fat[..., None], fat_col, meat_col)).astype(float)
    col += noise[..., None] * (10 if cooked else 8)
    # Marbling / grill marks.
    if cooked:
        col[slab & (((X + Y) % 5) < 1.0) & ~fat] *= 0.62
    else:
        streak = slab & ~fat & (np.abs(np.sin(u * 1.3 + noise * 0.4)) < 0.18)
        col[streak] = col[streak] * 0.4 + np.array([230, 200, 190]) * 0.6
    # Shading: lighter top-left, darker rim.
    edge = (np.abs(u) / 7.0) ** 2.4 + (np.abs(v) / 4.6) ** 2.4
    col *= (1.08 - 0.25 * np.clip(edge, 0, 1) ** 3)[..., None]
    col *= (1.0 + 0.08 * ((8 - X) + (8 - Y)) / 8)[..., None]
    img[..., :3] = np.clip(col, 0, 255)
    img[..., 3] = slab * 255.0
    # A dark outline so it reads at icon size.
    out = np.zeros_like(slab)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        out |= np.roll(np.roll(slab, dx, 1), dy, 0)
    rim = out & ~slab
    img[rim] = (np.array([40, 18, 14, 255]) if not cooked else np.array([48, 26, 14, 255]))
    return img


def tex_l227_grazer_meat() -> None:
    G.save_png(_meat(False), G.TEX / "item" / "boar_meat.png")
    G.save_png(_meat(True), G.TEX / "item" / "cooked_boar_meat.png")


# =============================================================================
# Sounds
# =============================================================================

def _t(dur):
    n = int(dur * SR)
    return n, np.arange(n) / SR


def _env(t, pts):
    return np.interp(t, *zip(*pts))


def _bp(x, lo, hi, order=2):
    return G.fft_filter(x, lambda f: G.bp_gain(f, lo, hi, order))


def _lp(x, fc, order=2):
    return G.fft_filter(x, lambda f: G.lp_gain(f, fc, order))


def _fry(rng, n, rate, irregular=0.35) -> np.ndarray:
    """Vocal fry: a train of soft pulses at an uneven rate, 0..1 (the rattle in a big throat)."""
    r = rate * (1 + G.smooth_jitter(rng, n, 9, irregular))
    ph = np.cumsum(r) / SR
    return (0.5 + 0.5 * np.cos(2 * np.pi * ph)) ** 3


def _forest(rng, x, amount=0.16, decay=0.35) -> np.ndarray:
    """Outdoors under a canopy: no walls, but the trunks and leaves give a soft, diffuse tail."""
    n = len(x)
    ir = G.reverb_ir(rng, 1.2, decay, lp=3000, predelay=0.02)
    return x + amount * G.norm(G.conv(x, ir)[:n]) * np.abs(x).max()


def _save(x, name: str, target: float, fade_in: float = 0.003, fade_out: float = 0.06) -> None:
    """Brings `x` to `target` LUFS (no peak normalising) and writes entity/<name>.ogg."""
    import soundfile as sf
    x = np.asarray(x, float)
    x = x - x.mean()
    n = len(x)
    fi, fo = max(1, int(fade_in * SR)), max(1, int(fade_out * SR))
    x[:fi] *= np.linspace(0, 1, fi)
    x[n - fo:] *= np.linspace(1, 0, fo)
    x = loud(x, target)
    path = G.SND / "entity" / f"{name}.ogg"
    path.parent.mkdir(parents=True, exist_ok=True)
    with sf.SoundFile(str(path), "w", SR, 1, format="OGG", subtype="VORBIS") as out:
        y = x.astype(np.float32)
        for i in range(0, len(y), SR):
            out.write(y[i:i + SR])


def _thud(rng, dur, f0, decay, crunch=0.3, crunch_band=(500, 3000)) -> np.ndarray:
    """A heavy footfall on the soft forest floor: a low body thump and the crush of moss and leaves."""
    n, t = _t(dur)
    body = np.sin(2 * np.pi * f0 * t * (1 - 0.25 * t / dur)) * np.exp(-t / decay)
    body += 0.5 * _lp(rng.normal(size=n), 220) * np.exp(-t / (decay * 0.7))
    leaves = _bp(rng.normal(size=n), *crunch_band) * np.exp(-t / 0.05) * (0.4 + 0.6 * rng.random(n) ** 6)
    return G.norm(body) + crunch * G.norm(leaves)


# --- Bear ---------------------------------------------------------------------

def snd_l227_grazer_bear_snore() -> None:
    """2 takes, ~4 s: asleep. A long, slow, rattling in-breath through a huge chest, a pause, a
    deep sighing out-breath. Quiet: you hear it when you are already too close."""
    for i, seed in enumerate((22740, 22741), 1):
        rng = G.rng_for(seed)
        dur = 4.2
        n, t = _t(dur)
        inhale = _env(t, [(0, 0), (0.3, 0.5), (1.4, 1.0), (1.8, 0.0), (dur, 0)])
        exhale = _env(t, [(0, 0), (2.1, 0), (2.4, 0.8), (3.6, 0.4), (dur, 0)])
        rattle = _fry(rng, n, 24.0 + 4 * i, 0.5)
        snore = _bp(rng.normal(size=n), 90, 700) * (0.25 + 0.75 * rattle) * inhale
        f0 = 48 + 6 * i + 4 * np.sin(2 * np.pi * 0.4 * t)
        tone = G.voice(f0, [(300, 120, 1.0), (750, 200, 0.4)], rng, nh=30, tilt=0.05) * inhale * rattle
        sigh = _bp(rng.normal(size=n), 150, 1200) * exhale
        x = G.norm(snore) + 0.6 * G.norm(tone) + 0.5 * G.norm(sigh)
        _save(_forest(rng, _lp(x, 2500), 0.1), f"l227_bear_snore{i}", -32, fade_out=0.3)


def snd_l227_grazer_bear_stir() -> None:
    """2 takes, ~2.4 s: the warning. A low, displeased groan deep in the chest, and the small wet
    clicks of eyelids coming unstuck, one after another."""
    for i, seed in enumerate((22742, 22743), 1):
        rng = G.rng_for(seed)
        dur = 2.4
        n, t = _t(dur)
        f0 = (62 + 8 * i) * (1 + 0.15 * G.smoothstep(0.2, 1.0, t) - 0.2 * G.smoothstep(1.2, dur, t)) * (1 + G.smooth_jitter(rng, n, 5, 0.06))
        v = G.voice(f0, [(320, 140, 1.0), (680, 200, 0.6), (2000, 400, 0.15)], rng, nh=60, tilt=0.06)
        fry = _fry(rng, n, 18.0, 0.6)
        groan = G.dist(G.norm(v) * (0.4 + 0.6 * fry), 2.0) * _env(t, [(0, 0), (0.35, 0.8), (1.2, 1.0), (2.0, 0.4), (dur, 0)])
        clicks = G.clicks(rng, np.sort(rng.uniform(0.4, 2.1, 7)), dur, 1400, 3200, 0.002, 0.006)
        x = G.norm(_lp(groan, 1800)) + 0.35 * G.norm(clicks)
        _save(_forest(rng, x, 0.12), f"l227_bear_stir{i}", -25, fade_out=0.2)


def snd_l227_grazer_bear_roar() -> None:
    """2 takes, ~2.8 s: awake and coming. A huge, ragged roar from a chest four blocks long: a deep
    voice tearing into a rasp, rising, then breaking down into a rumbling growl."""
    for i, seed in enumerate((22744, 22745), 1):
        rng = G.rng_for(seed)
        dur = 2.8
        n, t = _t(dur)
        base = 78 + 10 * i
        f0 = _env(t, [(0, base * 0.8), (0.25, base * 1.5), (0.9, base * 1.7), (1.6, base * 1.4), (2.2, base * 0.9), (dur, base * 0.7)])
        f0 = f0 * (1 + G.smooth_jitter(rng, n, 8, 0.07))
        v = G.voice(f0, [(520, 220, 1.0), (1050, 300, 0.8), (2500, 500, 0.35)], rng, nh=70, tilt=0.07)
        fry = _fry(rng, n, 34.0, 0.5)
        rasp = _bp(rng.normal(size=n), 250, 2600) * (0.5 + 0.5 * fry)
        sub = np.sin(2 * np.pi * np.cumsum(f0 * 0.5) / SR)
        e = _env(t, [(0, 0), (0.12, 0.8), (0.5, 1.0), (1.5, 0.95), (2.2, 0.5), (dur, 0)])
        x = (G.dist(G.norm(v) * (0.5 + 0.5 * fry), 3.5) + 0.55 * G.norm(rasp) + 0.4 * G.norm(sub)) * e
        x = _lp(x, 4200)
        _save(_forest(rng, x, 0.22, 0.5), f"l227_bear_roar{i}", -18, fade_out=0.25)


def snd_l227_grazer_bear_breath() -> None:
    """2 takes, ~1.4 s: hunting. Heavy chuffing breaths, two or three huffs through the nose."""
    for i, seed in enumerate((22746, 22747), 1):
        rng = G.rng_for(seed)
        dur = 1.4
        n, t = _t(dur)
        x = np.zeros(n)
        for k, at in enumerate((0.05, 0.45, 0.85)[: 2 + i % 2]):
            seg = _env(t, [(0, 0), (at, 0), (at + 0.04, 1.0), (at + 0.28, 0.0), (dur, 0)])
            x += _bp(rng.normal(size=n), 120 + 40 * k, 900) * seg
        tone = np.sin(2 * np.pi * 55 * t) * G.smoothstep(0.0, 0.05, t) * np.exp(-t / 0.6)
        _save(_forest(rng, G.norm(x) + 0.3 * tone, 0.1), f"l227_bear_breath{i}", -26, fade_out=0.1)


def snd_l227_grazer_bear_maul() -> None:
    """2 takes, ~0.9 s: the paws come down. A whoosh of a huge limb, a crushing thud, a tearing."""
    for i, seed in enumerate((22748, 22749), 1):
        rng = G.rng_for(seed)
        dur = 0.9
        n, t = _t(dur)
        sweep = G.svf(rng.normal(size=n) * 0.4, _env(t, [(0, 300), (0.22, 1400), (0.3, 500), (dur, 200)]), 2.0)
        whoosh = sweep * _env(t, [(0, 0), (0.15, 1.0), (0.3, 0.0), (dur, 0)])
        impact = np.zeros(n)
        thud = _thud(rng, 0.6, 46 + 6 * i, 0.12, crunch=0.6)
        G.add_at(impact, thud, 0.27, circular=False)
        tear = _bp(rng.normal(size=n), 900, 4000) * _env(t, [(0, 0), (0.29, 0), (0.31, 0.8), (0.55, 0.0), (dur, 0)]) * (0.3 + 0.7 * rng.random(n) ** 4)
        x = 0.6 * G.norm(whoosh) + G.norm(impact) + 0.35 * G.norm(tear)
        _save(_forest(rng, x, 0.14), f"l227_bear_maul{i}", -19, fade_out=0.1)


def snd_l227_grazer_bear_step() -> None:
    """4 takes: a heavy padded footfall on moss and leaf litter."""
    for i in range(1, 5):
        rng = G.rng_for(22750 + i)
        x = _thud(rng, 0.45, 50 + rng.uniform(-6, 6), 0.09, crunch=0.35)
        _save(x, f"l227_bear_step{i}", -28 + rng.uniform(-1.5, 0), fade_out=0.05)


def snd_l227_grazer_bear_hurt() -> None:
    """2 takes, ~0.8 s: a short, furious bellow."""
    for i, seed in enumerate((22755, 22756), 1):
        rng = G.rng_for(seed)
        dur = 0.8
        n, t = _t(dur)
        f0 = _env(t, [(0, 120), (0.1, 210 + 20 * i), (0.4, 170), (dur, 110)]) * (1 + G.smooth_jitter(rng, n, 12, 0.08))
        v = G.voice(f0, [(600, 240, 1.0), (1200, 320, 0.7), (2600, 500, 0.3)], rng, nh=50, tilt=0.06)
        fry = _fry(rng, n, 40.0, 0.5)
        x = G.dist(G.norm(v) * (0.5 + 0.5 * fry), 3.0) * _env(t, [(0, 0), (0.04, 1.0), (0.45, 0.8), (dur, 0)])
        _save(_forest(rng, _lp(x, 4000), 0.14), f"l227_bear_hurt{i}", -20, fade_out=0.1)


def snd_l227_grazer_bear_death() -> None:
    """~2.6 s: a long groaning bellow that sinks and breaks up into a last rattling breath."""
    rng = G.rng_for(22757)
    dur = 2.6
    n, t = _t(dur)
    f0 = _env(t, [(0, 170), (0.3, 190), (1.2, 120), (2.0, 70), (dur, 50)]) * (1 + G.smooth_jitter(rng, n, 7, 0.09))
    v = G.voice(f0, [(520, 220, 1.0), (980, 280, 0.6), (2400, 500, 0.2)], rng, nh=60, tilt=0.06)
    fry = _fry(rng, n, _env(t, [(0, 30), (dur, 14)]).mean(), 0.6)
    breath = _bp(rng.normal(size=n), 150, 1000) * _env(t, [(0, 0), (1.8, 0.0), (2.1, 0.7), (dur, 0)])
    x = G.dist(G.norm(v) * (0.4 + 0.6 * fry), 2.5) * _env(t, [(0, 0), (0.1, 1.0), (1.4, 0.8), (2.0, 0.2), (dur, 0)])
    x = G.norm(_lp(x, 3500)) + 0.4 * G.norm(breath)
    _save(_forest(rng, x, 0.2, 0.5), "l227_bear_death", -21, fade_out=0.3)


# --- Doe ----------------------------------------------------------------------

def snd_l227_grazer_doe_ambient() -> None:
    """2 takes, ~0.9 s: a soft snort through the nose while grazing, and a chew."""
    for i, seed in enumerate((22760, 22761), 1):
        rng = G.rng_for(seed)
        dur = 0.9
        n, t = _t(dur)
        snort = _bp(rng.normal(size=n), 700, 3200) * _env(t, [(0, 0), (0.02, 1.0), (0.18, 0.2), (0.3, 0.0), (dur, 0)])
        chew = G.clicks(rng, np.sort(rng.uniform(0.4, 0.85, 4)), dur, 300, 900, 0.004, 0.01)
        _save(G.norm(snort) + 0.4 * G.norm(chew), f"l227_doe_ambient{i}", -33, fade_out=0.05)


def snd_l227_grazer_doe_notice() -> None:
    """2 takes, ~1.3 s: she has seen you. One long, slow breath out through the nose, very
    close-sounding, and then nothing."""
    for i, seed in enumerate((22762, 22763), 1):
        rng = G.rng_for(seed)
        dur = 1.3
        n, t = _t(dur)
        air = _bp(rng.normal(size=n), 500, 2600) * _env(t, [(0, 0), (0.15, 0.9), (0.8, 0.5), (1.2, 0.0), (dur, 0)])
        whistle = np.sin(2 * np.pi * (1800 + 120 * i) * t) * _env(t, [(0, 0), (0.2, 0.06), (0.9, 0.02), (dur, 0)])
        _save(G.norm(air) + whistle, f"l227_doe_notice{i}", -29, fade_out=0.1)


def snd_l227_grazer_doe_slip() -> None:
    """2 takes, ~2.2 s: the branch lets you through. A slow, wet parting like thick bark tearing
    from inside, a breathy rush of air turned backwards, and a deep drop under it all."""
    for i, seed in enumerate((22764, 22765), 1):
        rng = G.rng_for(seed)
        dur = 2.2
        n, t = _t(dur)
        # The tear: crackling grains of a fibrous rip, spread across the first second.
        grains = (rng.random(n) ** 10) * _env(t, [(0, 0), (0.1, 0.6), (0.7, 1.0), (1.1, 0.2), (dur, 0)])
        tear = _bp(rng.normal(size=n) * grains, 600, 3800)
        # A reversed rush: air swelling up to a cut-off.
        rush = _bp(rng.normal(size=n), 250, 1800) * _env(t, [(0, 0), (0.9, 0.3), (1.15, 1.0), (1.18, 0.0), (dur, 0)])
        # The drop: a sub tone sliding down into nothing.
        f = _env(t, [(0, 70), (1.2, 55), (dur, 22)])
        drop = np.sin(2 * np.pi * np.cumsum(f) / SR) * _env(t, [(0, 0), (0.4, 0.4), (1.15, 1.0), (dur, 0)])
        x = 0.6 * G.norm(tear) + 0.7 * G.norm(rush) + 0.9 * G.norm(drop)
        _save(_forest(rng, x, 0.25, 0.6), f"l227_doe_slip{i}", -21, fade_out=0.3)


def snd_l227_grazer_doe_step() -> None:
    """4 takes: light, neat hooves on moss and leaves."""
    for i in range(1, 5):
        rng = G.rng_for(22770 + i)
        x = _thud(rng, 0.2, 140 + rng.uniform(-20, 20), 0.025, crunch=0.7, crunch_band=(900, 4500))
        _save(x, f"l227_doe_step{i}", -33 + rng.uniform(-1.5, 0), fade_out=0.03)


def snd_l227_grazer_doe_hurt() -> None:
    """2 takes, ~0.6 s: a thin, nasal distress bleat."""
    for i, seed in enumerate((22775, 22776), 1):
        rng = G.rng_for(seed)
        dur = 0.6
        n, t = _t(dur)
        f0 = _env(t, [(0, 520), (0.08, 760 + 40 * i), (0.4, 640), (dur, 480)]) * (1 + 0.03 * np.sin(2 * np.pi * 9 * t))
        v = G.voice(f0, [(1100, 300, 1.0), (2600, 500, 0.5)], rng, nh=24, tilt=0.03)
        x = v * _env(t, [(0, 0), (0.03, 1.0), (0.4, 0.7), (dur, 0)])
        _save(_forest(rng, x, 0.15), f"l227_doe_hurt{i}", -22, fade_out=0.08)


def snd_l227_grazer_doe_death() -> None:
    """~1.2 s: a bleat that thins away into a breath."""
    rng = G.rng_for(22777)
    dur = 1.2
    n, t = _t(dur)
    f0 = _env(t, [(0, 640), (0.2, 700), (0.8, 420), (dur, 300)]) * (1 + 0.04 * np.sin(2 * np.pi * 7 * t))
    v = G.voice(f0, [(1000, 300, 1.0), (2400, 500, 0.4)], rng, nh=24, tilt=0.03) * _env(t, [(0, 0), (0.05, 1.0), (0.7, 0.4), (1.0, 0.0), (dur, 0)])
    air = _bp(rng.normal(size=n), 500, 2500) * _env(t, [(0, 0), (0.7, 0), (0.9, 0.5), (dur, 0)])
    _save(_forest(rng, G.norm(v) + 0.4 * G.norm(air), 0.2), "l227_doe_death", -23, fade_out=0.15)


# --- Boar ---------------------------------------------------------------------

def snd_l227_grazer_boar_grunt() -> None:
    """3 takes, ~1 s: deep, wet, rumbling grunts, two or three in a row."""
    for i, seed in enumerate((22780, 22781, 22782), 1):
        rng = G.rng_for(seed)
        dur = 1.1
        n, t = _t(dur)
        x = np.zeros(n)
        for at in np.sort(rng.uniform(0.0, 0.7, 2 + i % 2)):
            seg = _env(t, [(0, 0), (at, 0), (at + 0.03, 1.0), (at + 0.22, 0.3), (at + 0.3, 0.0), (dur, 0)])
            f0 = (70 + 12 * rng.random()) * (1 + G.smooth_jitter(rng, n, 10, 0.08))
            v = G.voice(f0, [(380, 160, 1.0), (1000, 260, 0.5)], rng, nh=40, tilt=0.06)
            x += G.dist(G.norm(v) * (0.5 + 0.5 * _fry(rng, n, 30, 0.5)), 2.5) * seg
        _save(_forest(rng, _lp(x, 2500), 0.12), f"l227_boar_grunt{i}", -26, fade_out=0.08)


def snd_l227_grazer_boar_snore() -> None:
    """2 takes, ~3.2 s: lounging on its back: a long, honking, snuffling snore."""
    for i, seed in enumerate((22783, 22784), 1):
        rng = G.rng_for(seed)
        dur = 3.2
        n, t = _t(dur)
        inhale = _env(t, [(0, 0), (0.2, 0.6), (1.2, 1.0), (1.5, 0.0), (dur, 0)])
        exhale = _env(t, [(0, 0), (1.7, 0), (1.9, 0.6), (2.9, 0.2), (dur, 0)])
        rattle = _fry(rng, n, 32.0 + 5 * i, 0.4)
        honk = _bp(rng.normal(size=n), 200, 1400) * (0.2 + 0.8 * rattle) * inhale
        f0 = 90 + 10 * i + 6 * np.sin(2 * np.pi * 0.5 * t)
        tone = G.voice(f0, [(500, 160, 1.0), (1300, 300, 0.4)], rng, nh=30, tilt=0.05) * inhale * rattle
        snuffle = _bp(rng.normal(size=n), 400, 2500) * exhale * (0.5 + 0.5 * (rng.random(n) ** 3))
        x = G.norm(honk) + 0.7 * G.norm(tone) + 0.4 * G.norm(snuffle)
        _save(_forest(rng, _lp(x, 3000), 0.1), f"l227_boar_snore{i}", -29, fade_out=0.3)


def snd_l227_grazer_boar_squeal() -> None:
    """2 takes, ~1.3 s: hurt. A piercing, ragged squeal, wavering and cracking: the cue to get away."""
    for i, seed in enumerate((22785, 22786), 1):
        rng = G.rng_for(seed)
        dur = 1.3
        n, t = _t(dur)
        f0 = _env(t, [(0, 500), (0.08, 1100 + 100 * i), (0.6, 1250), (1.0, 900), (dur, 700)])
        f0 = f0 * (1 + 0.06 * np.sin(2 * np.pi * (11 + i) * t)) * (1 + G.smooth_jitter(rng, n, 15, 0.06))
        v = G.voice(f0, [(1600, 500, 1.0), (3200, 700, 0.6)], rng, nh=14, tilt=0.04)
        noise = _bp(rng.normal(size=n), 1200, 6000)
        x = (G.dist(G.norm(v), 4.0) + 0.3 * G.norm(noise)) * _env(t, [(0, 0), (0.05, 1.0), (0.9, 0.85), (dur, 0)])
        _save(_forest(rng, _lp(x, 7000), 0.2), f"l227_boar_squeal{i}", -18, fade_out=0.15)


def snd_l227_grazer_boar_thrash() -> None:
    """3 takes, ~0.9 s: a body the size of a car flailing: heavy thumps, hooves kicking, the
    undergrowth torn up, a furious grunt."""
    for i, seed in enumerate((22787, 22788, 22789), 1):
        rng = G.rng_for(seed)
        dur = 0.9
        n, t = _t(dur)
        x = np.zeros(n)
        for at in np.sort(rng.uniform(0.0, 0.6, 4)):
            G.add_at(x, _thud(rng, 0.35, 40 + rng.uniform(0, 25), 0.07, crunch=0.8) * rng.uniform(0.5, 1.0), at, circular=False)
        rustle = _bp(rng.normal(size=n), 1500, 6000) * (rng.random(n) ** 4) * _env(t, [(0, 0.3), (0.5, 1.0), (dur, 0.0)])
        f0 = (95 + 15 * i) * (1 + G.smooth_jitter(rng, n, 10, 0.1))
        grunt = G.dist(G.norm(G.voice(f0, [(450, 180, 1.0), (1100, 260, 0.5)], rng, nh=40, tilt=0.06)), 3.0)
        grunt *= _env(t, [(0, 0), (0.1, 1.0), (0.4, 0.2), (dur, 0)])
        x = G.norm(x) + 0.4 * G.norm(rustle) + 0.45 * grunt
        _save(_forest(rng, x, 0.14), f"l227_boar_thrash{i}", -21, fade_out=0.08)


def snd_l227_grazer_boar_gore() -> None:
    """2 takes, ~0.6 s: a tusk goes in: a hard blunt impact, a wet rip."""
    for i, seed in enumerate((22790, 22791), 1):
        rng = G.rng_for(seed)
        dur = 0.6
        n, t = _t(dur)
        hit = _thud(rng, dur, 70 + 10 * i, 0.06, crunch=0.2)
        rip = _bp(rng.normal(size=n), 700, 3500) * _env(t, [(0, 0), (0.02, 1.0), (0.25, 0.0), (dur, 0)]) * (0.3 + 0.7 * rng.random(n) ** 3)
        crack = G.clicks(rng, [0.01, 0.03], dur, 1800, 3500, 0.002, 0.004)
        _save(_forest(rng, G.norm(hit) + 0.6 * G.norm(rip) + 0.4 * G.norm(crack), 0.12), f"l227_boar_gore{i}", -19, fade_out=0.06)


def snd_l227_grazer_boar_step() -> None:
    """4 takes: a heavy hoof sinking into the soft forest floor."""
    for i in range(1, 5):
        rng = G.rng_for(22792 + i)
        x = _thud(rng, 0.4, 58 + rng.uniform(-6, 6), 0.07, crunch=0.45, crunch_band=(400, 2500))
        _save(x, f"l227_boar_step{i}", -28 + rng.uniform(-1.5, 0), fade_out=0.05)


def snd_l227_grazer_boar_death() -> None:
    """~2.4 s: a long squeal that breaks down into gurgling grunts."""
    rng = G.rng_for(22797)
    dur = 2.4
    n, t = _t(dur)
    f0 = _env(t, [(0, 900), (0.2, 1200), (0.9, 800), (1.4, 300), (dur, 120)]) * (1 + 0.05 * np.sin(2 * np.pi * 9 * t))
    v = G.voice(f0, [(1400, 500, 1.0), (3000, 700, 0.5)], rng, nh=16, tilt=0.04)
    squeal = G.dist(G.norm(v), 3.0) * _env(t, [(0, 0), (0.05, 1.0), (1.0, 0.6), (1.5, 0.1), (dur, 0)])
    gurgle = _bp(rng.normal(size=n), 200, 1200) * _fry(rng, n, 16, 0.7) * _env(t, [(0, 0), (1.2, 0), (1.5, 0.8), (dur, 0)])
    _save(_forest(rng, G.norm(squeal) + 0.6 * G.norm(gurgle), 0.2, 0.5), "l227_boar_death", -20, fade_out=0.3)


TEXTURES = [tex_l227_grazer_bear, tex_l227_grazer_doe, tex_l227_grazer_boar, tex_l227_grazer_meat]
SOUNDS = [snd_l227_grazer_bear_snore, snd_l227_grazer_bear_stir, snd_l227_grazer_bear_roar, snd_l227_grazer_bear_breath,
          snd_l227_grazer_bear_maul, snd_l227_grazer_bear_step, snd_l227_grazer_bear_hurt, snd_l227_grazer_bear_death,
          snd_l227_grazer_doe_ambient, snd_l227_grazer_doe_notice, snd_l227_grazer_doe_slip, snd_l227_grazer_doe_step,
          snd_l227_grazer_doe_hurt, snd_l227_grazer_doe_death,
          snd_l227_grazer_boar_grunt, snd_l227_grazer_boar_snore, snd_l227_grazer_boar_squeal, snd_l227_grazer_boar_thrash,
          snd_l227_grazer_boar_gore, snd_l227_grazer_boar_step, snd_l227_grazer_boar_death]
