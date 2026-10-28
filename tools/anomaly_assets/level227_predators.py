"""Level 227 "Megaflora" predators: the wolf (split, sideways-opening jaws), the stag (long horns,
five tails, red eyes) and the fluorescent squirrel.

Textures follow the UV maps written next to every box in client/L227WolfModel.java,
client/L227StagModel.java and client/L227SquirrelModel.java. The wolf and the stag use a
128 x 128 UV layout saved at 256 x 256; the squirrel a 64 x 64 layout saved at 128 x 128 (2 texels
per model pixel everywhere, for crisp fur strands and teeth). Each also gets a second, mostly black
texture that the game draws full-bright and additively: the wolf's pale eye glint, the stag's red
eyes, the squirrel's fluorescent fur.

How a Minecraft box unfolds on the texture (box w x h x d at texOffs (u, v)):

           u   u+d     u+d+w   u+2d+w  u+2d+2w
      v    .   [ top ][bottom]
      v+d  [ -X ][front][ +X ][ back ]
      v+d+h

Sounds are mono 44.1 kHz OGG. Outdoors in a vast forest: no tunnel echo, a soft, long, dark
tail instead (big trunks, moss, leaves). Run with: python tools/gen_assets.py l227pred
"""
import numpy as np

import gen_assets as G

OUT_TEX = G.TEX / "entity"
OUT_SND = G.SND / "entity"


# =============================================================================
# Texture helpers (a UV atlas at 2 texels per model pixel)
# =============================================================================

class Atlas:
    def __init__(self, uv_size: int, k: int = 2):
        self.k = k
        self.n = uv_size * k
        self.X, self.Y = G.grid((self.n, self.n))

    def faces(self, u, v, w, h, d) -> dict:
        """Texel rectangles (x0, y0, x1, y1) of the six faces of a box at texOffs (u, v)."""
        r = {
            "top": (u + d, v, u + d + w, v + d),
            "bottom": (u + d + w, v, u + d + 2 * w, v + d),
            "right": (u, v + d, u + d, v + d + h),          # the -X face
            "front": (u + d, v + d, u + d + w, v + d + h),
            "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),   # the +X face
            "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
        }
        return {key: tuple(c * self.k for c in rect) for key, rect in r.items()}

    def whole(self, u, v, w, h, d) -> tuple:
        return (u * self.k, v * self.k, (u + 2 * (d + w)) * self.k, (v + d + h) * self.k)

    def rect(self, x0, y0, x1, y1) -> tuple:
        return (x0 * self.k, y0 * self.k, x1 * self.k, y1 * self.k)

    def local(self, r):
        """(mask, fx, fy) for a texel rectangle: fx, fy run 0..1 across it."""
        x0, y0, x1, y1 = r
        m = (self.X >= x0) & (self.X < x1) & (self.Y >= y0) & (self.Y < y1)
        fx = (self.X - x0) / max(x1 - x0, 1e-6)
        fy = (self.Y - y0) / max(y1 - y0, 1e-6)
        return m, fx, fy

    def shade_box(self, lum, box, top=6.0, bottom=-20.0, side_ao=-14.0, convex=4.0) -> None:
        """Fake lighting for one box: lighter top, dark underside, sides darkening toward the
        bottom, and a soft dome over every face so boxes read as rounded fur, not crates."""
        f = self.faces(*box)
        m, _, _ = self.local(f["top"])
        lum[m] += top
        m, _, _ = self.local(f["bottom"])
        lum[m] += bottom
        for key in ("right", "front", "left", "back"):
            m, fx, fy = self.local(f[key])
            lum[m] += (side_ao * G.smoothstep(0.35, 1.0, fy))[m]
        if convex:
            for r in f.values():
                m, fx, fy = self.local(r)
                lum[m] += (convex * (1.0 - ((fx - 0.5) ** 2 + (fy - 0.5) ** 2) * 2.2))[m]


def fur(rng, n, base, dark, light, streak=10.0, coarse=1.0) -> np.ndarray:
    """A coat of fur: hair strands running down the texture (v), clumped into locks, dark at the
    roots between locks and grizzled at the tips."""
    img = G.fill(base, (n, n))
    locks = G.fnoise(rng, (n, n), beta=1.6, aniso=(1, streak))
    fine = G.fnoise(rng, (n, n), beta=0.7, aniso=(1, streak * 0.6), kmin=n / 10)
    broad = G.fnoise(rng, (n, n), beta=3.0)
    img = G.add_lum(img, broad * 6 * coarse + locks * 7 * coarse + fine * 4)
    img = G.over(img, np.asarray(dark, float), G.smoothstep(-0.4, -1.8, locks) * 0.75)
    img = G.over(img, np.asarray(light, float), G.smoothstep(1.0, 2.2, locks + fine * 0.5) * 0.55)
    return img


def apply(img, mask, color, alpha=1.0):
    a = np.asarray(mask, float) * alpha
    return G.over(img, np.asarray(color, float), a)


def save(img, name) -> None:
    G.save_png(img, OUT_TEX / f"{name}.png")


def glow_canvas(n) -> np.ndarray:
    """Black RGBA: drawn additively, black adds nothing."""
    out = np.zeros((n, n, 4))
    out[..., 3] = 255
    return out


# =============================================================================
# Wolf
# =============================================================================

W_CHEST = (0, 0, 9, 10, 12)
W_WAIST = (44, 0, 7, 8, 8)
W_HIPS = (76, 0, 8, 9, 8)
W_MANE = (0, 24, 11, 8, 6)
W_NECK = (36, 24, 6, 6, 8)
W_SKULL = (66, 24, 7, 7, 7)
W_BROW = (92, 40, 7.2, 1.4, 2)
W_THROAT = (60, 40, 4.4, 3.6, 3)
W_EARS = [(76, 40, 2.6, 5, 1.2), (84, 40, 2.6, 5, 1.2)]
W_JAWS = [(0, 40, 2.6, 4.2, 7), (22, 40, 2.6, 4.2, 7)]       # right, left
W_NOSES = [(44, 40, 2.2, 3.0, 1.6), (52, 40, 2.2, 3.0, 1.6)]
W_UPPER = [(0, 56, 2.8, 8.6, 2.8), (12, 56, 2.8, 8.6, 2.8)]
W_FORE = [(24, 56, 2, 7.5, 2), (32, 56, 2, 7.5, 2)]
W_THIGH = [(40, 56, 3.4, 7.8, 3.6), (54, 56, 3.4, 7.8, 3.6)]
W_SHIN = [(68, 56, 2.2, 6.5, 2.2), (77, 56, 2.2, 6.5, 2.2)]
W_HOCK = [(86, 56, 1.8, 5.5, 1.8), (94, 56, 1.8, 5.5, 1.8)]
W_PAW = (0, 70, 2.6, 1.4, 3.4)
W_TAIL = [(14, 70, 3, 3, 6), (32, 70, 3.6, 3.6, 6), (52, 70, 3.2, 3.2, 5.5)]
W_FUR = (104, 48, 128, 96)
W_TEETH = (72, 70, 88, 78)
W_CLAWS = (88, 70, 100, 76)
W_TONGUE = (72, 80, 84, 88)

WOLF_BASE = (70, 67, 58)        # slate grey-brown
WOLF_DARK = (22, 22, 20)
WOLF_LIGHT = (128, 124, 108)    # grizzled tips
WOLF_BELLY = (104, 98, 84)
LICHEN = (92, 104, 66)          # the green of the high branches, grown into the coat


def tex_l227pred_wolf() -> None:
    A = Atlas(128)
    rng = G.rng_for(227101)
    n = A.n
    img = fur(rng, n, WOLF_BASE, WOLF_DARK, WOLF_LIGHT)
    lum = np.zeros((n, n))

    # Lichen-green cast in the coat, patchy: they sleep on mossy bark.
    lichen = G.smoothstep(0.6, 2.0, G.fnoise(rng, (n, n), beta=2.6))
    img = G.over(img, np.array(LICHEN), lichen * 0.35)

    body = [W_CHEST, W_WAIST, W_HIPS, W_MANE, W_NECK, W_SKULL] + W_UPPER + W_FORE + W_THIGH + W_SHIN + W_HOCK + W_TAIL
    for b in body:
        A.shade_box(lum, b)
    # A dark saddle along the back; the belly and inner legs paler.
    for b in (W_CHEST, W_WAIST, W_HIPS, W_NECK, W_MANE):
        f = A.faces(*b)
        m, fx, fy = A.local(f["top"])
        img[m] = G.lerp(img[m], np.array(WOLF_DARK), (0.45 * np.exp(-((fx - 0.5) / 0.3) ** 2))[m][:, None])
        m, fx, fy = A.local(f["bottom"])
        img[m] = G.lerp(img[m], np.array(WOLF_BELLY), 0.5)
        for side in ("right", "left"):
            m, fx, fy = A.local(f[side])
            img[m] = G.lerp(img[m], np.array(WOLF_DARK), (0.35 * (1 - G.smoothstep(0.0, 0.45, fy)))[m][:, None])
            img[m] = G.lerp(img[m], np.array(WOLF_BELLY), (0.3 * G.smoothstep(0.65, 1.0, fy))[m][:, None])
    # Ribs showing under the thin coat of the waist.
    fw = A.faces(*W_WAIST)
    for side in ("right", "left"):
        m, fx, fy = A.local(fw[side])
        lum[m] += (np.cos(2 * np.pi * fx * 3.5) * 6 * G.smoothstep(0.2, 0.6, fy) * (1 - G.smoothstep(0.7, 1, fy)))[m]
    # The mane: longer, darker, wilder.
    m, _, _ = A.local(A.whole(*W_MANE))
    img[m] = G.lerp(img[m], np.array(WOLF_DARK), 0.3)
    # Legs: darker lower down, pale stockings at the back.
    for b in W_FORE + W_SHIN + W_HOCK:
        for key in ("right", "front", "left", "back"):
            m, fx, fy = A.local(A.faces(*b)[key])
            img[m] = G.lerp(img[m], np.array((40, 38, 34)), (0.35 * fy)[m][:, None])

    # The head: dark mask round the eyes, eyes small and pale, a scar.
    fs = A.faces(*W_SKULL)
    m, fx, fy = A.local(fs["front"])
    img[m] = G.lerp(img[m], np.array((30, 28, 26)), 0.4)
    for ex in (0.2, 0.8):
        socket = np.clip(1 - ((fx - ex) / 0.16) ** 2 - ((fy - 0.27) / 0.09) ** 2, 0, 1)
        img[m] = G.lerp(img[m], np.array((12, 10, 9)), (socket ** 0.5)[m][:, None])
        eye = np.clip(1 - ((fx - ex) / 0.075) ** 2 - ((fy - 0.27) / 0.05) ** 2, 0, 1)
        img[m] = G.lerp(img[m], np.array((206, 210, 168)), (G.smoothstep(0.0, 0.3, eye))[m][:, None])
        pupil = np.clip(1 - ((fx - ex) / 0.02) ** 2 - ((fy - 0.27) / 0.045) ** 2, 0, 1)
        img[m] = G.lerp(img[m], np.array((10, 8, 6)), (G.smoothstep(0.0, 0.2, pupil))[m][:, None])
    # Brow: heavy and dark.
    m, _, _ = A.local(A.whole(*W_BROW))
    img[m] = G.lerp(img[m], np.array(WOLF_DARK), 0.5)

    # The two jaw halves: fur outside, a seam of black lip along the inner edge, and the inner
    # face (seen only when they spread) all gum: wet red-black with ridges where the teeth root.
    for i, b in enumerate(W_JAWS):
        f = A.faces(*b)
        inner = "left" if i == 0 else "right"       # right jaw's +X face meets the left jaw's -X face
        outer = "right" if i == 0 else "left"
        m, fx, fy = A.local(f[inner])
        gum = G.lerp(np.array((96, 30, 34)), np.array((40, 10, 14)), G.smoothstep(-1.0, 1.4, G.fnoise(rng, (n, n), beta=2.0))[..., None])
        img[m] = gum[m]
        rows = np.exp(-((fy - 0.1) / 0.08) ** 2) + np.exp(-((fy - 0.78) / 0.08) ** 2)
        lum[m] += (rows * 18 - 10 + np.cos(2 * np.pi * fx * 7) * 4 * rows)[m]
        # Black lips along the long edges of the top, bottom and front faces (the seam side and
        # the rim alike: it reads as a dark lip line either way), the outer face a little darker.
        m, _, _ = A.local(f[outer])
        img[m] = G.lerp(img[m], np.array((44, 42, 38)), 0.2)
        for key in ("top", "bottom", "front"):
            m, fx, fy = A.local(f[key])
            edge = np.abs(fx - 0.5) * 2
            img[m] = G.lerp(img[m], np.array((14, 10, 10)), G.smoothstep(0.7, 0.98, edge)[m][:, None])
        m, fx, fy = A.local(f["front"])
        img[m] = G.lerp(img[m], np.array((24, 20, 18)), 0.5)
    for i, b in enumerate(W_NOSES):
        f = A.faces(*b)
        m, fx, fy = A.local(f["front"])
        # A split black nose, wet.
        img[m] = G.lerp(np.array((16, 14, 14)), np.array((52, 46, 46)), G.smoothstep(0.6, 1.0, 1 - fy)[..., None])[m]
        lum[m] += (G.smoothstep(1.6, 2.4, G.fnoise(rng, (n, n), beta=0.8)) * 30)[m]
        m, _, _ = A.local(f["left" if i == 0 else "right"])
        img[m] = np.array((80, 24, 28))
    # The throat: deep wet red into black.
    m, fx, fy = A.local(A.whole(*W_THROAT))
    img[m] = G.lerp(np.array((110, 30, 36)), np.array((18, 4, 6)), G.smoothstep(0.2, 0.9, fy)[..., None])[m]
    # Ears: pink-grey inside (the front face), dark rims.
    for b in W_EARS:
        f = A.faces(*b)
        m, fx, fy = A.local(f["front"])
        inner = np.clip(1 - ((fx - 0.5) / 0.32) ** 2, 0, 1) * G.smoothstep(0.1, 0.5, fy)
        img[m] = G.lerp(img[m], np.array((96, 72, 70)), (inner * 0.8)[m][:, None])
        m, fx, fy = A.local(A.whole(*b))
        img[m] = G.lerp(img[m], np.array(WOLF_DARK), (0.6 * (1 - G.smoothstep(0.0, 0.25, fy)))[m][:, None])
    # Paws: dark fur, black pads underneath.
    f = A.faces(*W_PAW)
    m, _, _ = A.local(A.whole(*W_PAW))
    img[m] = G.lerp(img[m], np.array((34, 32, 30)), 0.55)
    m, fx, fy = A.local(f["bottom"])
    img[m] = np.array((18, 16, 16))
    # Tail: darker towards the tip, with a black tip.
    for j, b in enumerate(W_TAIL):
        m, _, _ = A.local(A.whole(*b))
        img[m] = G.lerp(img[m], np.array(WOLF_DARK), 0.15 + 0.25 * j)

    img = G.add_lum(img, lum)

    # Fur tufts: longer strands, darker roots, light grizzled ends.
    m, fx, fy = A.local(A.rect(*W_FUR))
    tuft = fur(rng, n, WOLF_BASE, WOLF_DARK, WOLF_LIGHT, streak=16.0, coarse=1.3)
    tuft = G.over(tuft, np.array(LICHEN), lichen * 0.3)
    tuft = G.add_lum(tuft, (G.fnoise(rng, (n, n), beta=1.0, aniso=(1, 6)) * 10))
    img[m] = tuft[m]
    # Teeth: long, yellowed, translucent at the tips, stained at the root.
    m, fx, fy = A.local(A.rect(*W_TEETH))
    tooth = G.lerp(np.array((150, 128, 96)), np.array((232, 226, 204)), G.smoothstep(0.0, 0.7, fy)[..., None])
    tooth = G.add_lum(tooth, G.fnoise(rng, (n, n), beta=1.6, aniso=(1, 8)) * 10)
    img[m] = tooth[m]
    m, fx, fy = A.local(A.rect(*W_CLAWS))
    img[m] = G.lerp(np.array((20, 18, 16)), np.array((70, 64, 56)), fy[..., None])[m]
    m, fx, fy = A.local(A.rect(*W_TONGUE))
    tongue = G.lerp(np.array((120, 50, 64)), np.array((70, 22, 34)), G.smoothstep(-1, 1.5, G.fnoise(rng, (n, n), beta=1.5))[..., None])
    img[m] = tongue[m]
    save(img, "l227_wolf")

    # The eye glint: pale yellow-green, faint, only in the eyes.
    glow = glow_canvas(n)
    m, fx, fy = A.local(fs["front"])
    for ex in (0.2, 0.8):
        eye = np.clip(1 - ((fx - ex) / 0.075) ** 2 - ((fy - 0.27) / 0.05) ** 2, 0, 1)
        pupil = np.clip(1 - ((fx - ex) / 0.02) ** 2 - ((fy - 0.27) / 0.045) ** 2, 0, 1)
        e = G.smoothstep(0.0, 0.4, eye) * (1 - G.smoothstep(0.0, 0.2, pupil))
        glow[m, :3] += (e[m][:, None] * np.array((150, 160, 110)))
    save(glow, "l227_wolf_eyes")


# =============================================================================
# Stag
# =============================================================================

S_CHEST = (0, 0, 12, 12, 13)
S_HAUNCH = (50, 0, 11, 11, 13)
S_WITHERS = (0, 28, 8, 3, 10)
S_NECK = (38, 28, 7, 8, 13)
S_SKULL = (78, 28, 6, 6, 7)
S_MUZZLE = (0, 44, 4.2, 4.5, 7)
S_JAW = (24, 50, 3.6, 1.4, 6.5)
S_EARS = [(46, 50, 1.8, 4.5, 0.8), (52, 50, 1.8, 4.5, 0.8)]
S_EYE = (58, 50, 1, 1.2, 1.4)
S_UPPER = [(0, 58, 3.4, 10, 3.8), (15, 58, 3.4, 10, 3.8)]
S_FORE = [(30, 58, 2, 8.5, 2.2), (39, 58, 2, 8.5, 2.2)]
S_THIGH = [(48, 58, 4, 10.5, 5), (66, 58, 4, 10.5, 5)]
S_SHIN = [(84, 58, 2.4, 9, 2.6), (94, 58, 2.4, 9, 2.6)]
S_CANNON = [(0, 74, 1.6, 7.5, 1.8), (7, 74, 1.6, 7.5, 1.8)]
S_PASTERN = [(14, 74, 1.6, 4, 1.8), (21, 74, 1.6, 4, 1.8)]
S_HOOF = (28, 74, 2.2, 1.8, 2.6)
S_HOOF_BITS = (28, 80, 32, 84)
S_TAIL = (40, 74, 1.1, 1.1, 4.5)
S_HORN = (104, 28, 128, 64)
S_SHAG = (104, 66, 128, 100)

STAG_BASE = (84, 56, 38)        # wet umber
STAG_DARK = (34, 22, 16)
STAG_LIGHT = (150, 118, 84)
STAG_BELLY = (132, 116, 92)
STAG_MOSS = (70, 92, 46)


def tex_l227pred_stag() -> None:
    A = Atlas(128)
    rng = G.rng_for(227201)
    n = A.n
    img = fur(rng, n, STAG_BASE, STAG_DARK, STAG_LIGHT, streak=12.0, coarse=0.8)
    lum = np.zeros((n, n))
    body = [S_CHEST, S_HAUNCH, S_WITHERS, S_NECK, S_SKULL, S_MUZZLE] + S_UPPER + S_FORE + S_THIGH + S_SHIN + S_CANNON + S_PASTERN
    for b in body:
        A.shade_box(lum, b)

    # Dark dorsal line, buff belly, and old moss grown into the back like on a fallen log.
    moss = G.smoothstep(0.7, 1.9, G.fnoise(rng, (n, n), beta=2.4)) * G.smoothstep(-0.5, 1.0, G.fnoise(rng, (n, n), beta=1.2, kmin=12))
    for b in (S_CHEST, S_HAUNCH, S_WITHERS, S_NECK):
        f = A.faces(*b)
        m, fx, fy = A.local(f["top"])
        img[m] = G.lerp(img[m], np.array(STAG_DARK), (0.5 * np.exp(-((fx - 0.5) / 0.22) ** 2))[m][:, None])
        img[m] = G.lerp(img[m], np.array(STAG_MOSS), (moss * 0.8)[m][:, None])
        for side in ("right", "left"):
            m, fx, fy = A.local(f[side])
            img[m] = G.lerp(img[m], np.array(STAG_MOSS), (moss * 0.6 * (1 - G.smoothstep(0.0, 0.4, fy)))[m][:, None])
            img[m] = G.lerp(img[m], np.array(STAG_BELLY), (0.45 * G.smoothstep(0.7, 1.0, fy))[m][:, None])
        m, _, _ = A.local(f["bottom"])
        img[m] = G.lerp(img[m], np.array(STAG_BELLY), 0.55)
    # A dirty pale rump patch (the back face of the haunches) with a dark rim.
    m, fx, fy = A.local(A.faces(*S_HAUNCH)["back"])
    patch = np.clip(1 - ((fx - 0.5) / 0.42) ** 2 - ((fy - 0.45) / 0.45) ** 2, 0, 1)
    patch = patch * (0.75 + 0.25 * G.smoothstep(-1, 1, G.fnoise(rng, (n, n), beta=1.4)))
    img[m] = G.lerp(img[m], np.array((150, 136, 110)), (G.smoothstep(0.0, 0.4, patch) * 0.55)[m][:, None])
    # Old scars: pale lines raked across the flanks (other stags' horns).
    def scars(d, k):
        for _ in range(9):
            x, y = rng.uniform(0, 100), rng.uniform(6, 28)
            a = rng.uniform(-0.5, 0.5)
            L = rng.uniform(6, 14)
            d.line([(x * k * 2, y * k * 2), ((x + np.cos(a) * L) * k * 2, (y + np.sin(a) * L) * k * 2)], fill=255, width=max(1, k))
    sc = G.pil_mask(scars, (n, n))
    img = G.over(img, np.array((168, 140, 118)), sc * 0.55)
    # The neck: thick dark mane on the underside.
    fneck = A.faces(*S_NECK)
    m, _, _ = A.local(fneck["bottom"])
    img[m] = G.lerp(img[m], np.array(STAG_DARK), 0.55)
    # Legs: darker and shorter-haired towards the hooves; a dark gland stripe on the hind cannons.
    for b in S_FORE + S_SHIN + S_CANNON + S_PASTERN:
        for key in ("right", "front", "left", "back"):
            m, fx, fy = A.local(A.faces(*b)[key])
            img[m] = G.lerp(img[m], np.array((40, 30, 24)), (0.25 + 0.35 * fy)[m][:, None])

    # The face: a long dark face, a pale band round the muzzle, a black wet nose; bulging red eyes.
    fsk = A.faces(*S_SKULL)
    m, _, _ = A.local(A.whole(*S_SKULL))
    img[m] = G.lerp(img[m], np.array((54, 38, 30)), 0.35)
    fm = A.faces(*S_MUZZLE)
    for key in ("right", "left", "top"):
        m, fx, fy = A.local(fm[key])
        t = fx if key == "right" else (1 - fx) if key == "left" else 1 - fy
        img[m] = G.lerp(img[m], np.array((160, 146, 124)), (0.6 * G.smoothstep(0.55, 0.9, 1 - t))[m][:, None])
    m, fx, fy = A.local(fm["front"])
    img[m] = G.lerp(np.array((20, 16, 16)), np.array((60, 50, 48)), (fy ** 2)[..., None])[m]
    lum[m] += (G.smoothstep(1.5, 2.4, G.fnoise(rng, (n, n), beta=0.8)) * 30)[m]
    m, _, _ = A.local(A.whole(*S_JAW))
    img[m] = G.lerp(img[m], np.array((140, 126, 104)), 0.4)
    m, fx, fy = A.local(A.whole(*S_EYE))
    img[m] = G.lerp(np.array((150, 12, 10)), np.array((60, 4, 4)), G.smoothstep(0.2, 1.0, np.hypot(fx - 0.5, fy - 0.5) * 2)[..., None])[m]
    # Tear-streak glands under the eyes on the skull sides.
    for key in ("right", "left"):
        m, fx, fy = A.local(fsk[key])
        gland = np.clip(1 - ((fx - 0.25) / 0.3) ** 2 - ((fy - 0.7) / 0.18) ** 2, 0, 1)
        img[m] = G.lerp(img[m], np.array((24, 14, 12)), (gland * 0.8)[m][:, None])
    for b in S_EARS:
        f = A.faces(*b)
        m, fx, fy = A.local(f["front"])
        img[m] = G.lerp(img[m], np.array((112, 86, 76)), (np.clip(1 - ((fx - 0.5) / 0.35) ** 2, 0, 1) * 0.7)[m][:, None])

    img = G.add_lum(img, lum)

    # Hooves: black horn, glossy, ridged.
    m, fx, fy = A.local(A.whole(*S_HOOF))
    hoof = G.lerp(np.array((44, 40, 38)), np.array((14, 12, 12)), fy[..., None])
    hoof = G.add_lum(hoof, G.fnoise(rng, (n, n), beta=1.4, aniso=(8, 1)) * 6)
    img[m] = hoof[m]
    m, _, _ = A.local(A.rect(*S_HOOF_BITS))
    img[m] = np.array((16, 14, 14))
    # Tails: thin, grey-brown, ringed darker.
    m, fx, fy = A.local(A.whole(*S_TAIL))
    tail = fur(rng, n, (70, 58, 48), (26, 20, 16), (130, 116, 96), streak=4.0)
    img[m] = tail[m]

    # Horns: pale, dead-bone grey, ridged and pitted lengthwise, darker in the grooves, polished
    # paler on the points, rust-dark stains where they've been through things.
    m, fx, fy = A.local(A.rect(*S_HORN))
    ridges = G.fnoise(rng, (n, n), beta=1.2, aniso=(10, 1))
    horn = G.fill((176, 168, 148), (n, n))
    horn = G.add_lum(horn, ridges * 16 + G.fnoise(rng, (n, n), beta=2.4) * 10)
    horn = G.over(horn, np.array((92, 84, 70)), G.smoothstep(0.8, 2.0, -ridges) * 0.7)
    pits = G.smoothstep(2.0, 2.6, G.fnoise(rng, (n, n), beta=0.6))
    horn = G.over(horn, np.array((60, 54, 46)), pits * 0.8)
    stain = G.smoothstep(1.2, 2.2, G.fnoise(rng, (n, n), beta=2.2)) * G.smoothstep(-0.2, 1.2, G.fnoise(rng, (n, n), beta=1.0, aniso=(1, 6)))
    horn = G.over(horn, np.array((84, 30, 22)), stain * 0.55)
    img[m] = horn[m]
    # Shaggy mane hair.
    m, _, _ = A.local(A.rect(*S_SHAG))
    shag = fur(rng, n, (60, 42, 30), (20, 14, 10), (120, 96, 70), streak=18.0, coarse=1.4)
    img[m] = shag[m]
    save(img, "l227_stag")

    # The red eyes, burning (plus a faint red rim reflection in the tear glands).
    glow = glow_canvas(n)
    m, fx, fy = A.local(A.whole(*S_EYE))
    e = 1 - G.smoothstep(0.3, 1.0, np.hypot(fx - 0.5, fy - 0.5) * 2)
    glow[m, :3] = (e[m][:, None] * np.array((255, 40, 24)))
    save(glow, "l227_stag_eyes")


# =============================================================================
# Squirrel
# =============================================================================

Q_TORSO = (0, 0, 4, 4, 7)
Q_HAUNCH = (22, 0, 5, 4.5, 4.5)
Q_SKULL = (0, 12, 4, 3.8, 3.8)
Q_SNOUT = (16, 12, 2.4, 2.2, 1.8)
Q_EYE = (26, 12, 1, 1.4, 1.4)
Q_JAW = (46, 12, 1.8, 0.6, 1.6)
Q_EAR = (32, 12, 1.4, 2, 0.6)
Q_ARM = [(0, 22, 1.2, 3.1, 1.2), (6, 22, 1.2, 3.1, 1.2)]
Q_FOREARM = [(12, 22, 1, 2.4, 1), (17, 22, 1, 2.4, 1)]
Q_THIGH = [(22, 22, 1.8, 3.6, 2.6), (31, 22, 1.8, 3.6, 2.6)]
Q_SHIN = [(48, 30, 0.9, 2.4, 0.9), (52, 30, 0.9, 2.4, 0.9)]
Q_FOOT = [(40, 22, 1.2, 0.8, 3.2), (49, 22, 1.2, 0.8, 3.2)]
Q_TAIL = [(0, 30, 2.6, 2.6, 3.5), (13, 30, 4, 4, 4), (30, 30, 4.6, 4.6, 4.2), (0, 40, 4, 4, 3.6)]
Q_TEETH = (38, 12, 44, 16)
Q_WHISKER = (44, 12, 46, 16)
Q_CLAWS = (58, 22, 64, 26)
Q_FLUFF = (20, 44, 46, 62)

SQ_BASE = (52, 122, 66)        # under ordinary light it is already a wrong, acid green
SQ_DARK = (14, 46, 30)
SQ_LIGHT = (150, 220, 110)
SQ_BELLY = (170, 196, 96)      # paler, yellower


def tex_l227pred_squirrel() -> None:
    A = Atlas(64)
    rng = G.rng_for(227301)
    n = A.n
    img = fur(rng, n, SQ_BASE, SQ_DARK, SQ_LIGHT, streak=8.0)
    lum = np.zeros((n, n))
    body = [Q_TORSO, Q_HAUNCH, Q_SKULL, Q_SNOUT] + Q_ARM + Q_FOREARM + Q_THIGH + Q_SHIN + Q_FOOT + Q_TAIL
    for b in body:
        A.shade_box(lum, b, top=5, bottom=-14, side_ao=-10, convex=3)
    # A dark stripe down the back, a pale yellow belly.
    for b in (Q_TORSO, Q_HAUNCH):
        f = A.faces(*b)
        m, fx, fy = A.local(f["top"])
        img[m] = G.lerp(img[m], np.array(SQ_DARK), (0.6 * np.exp(-((fx - 0.5) / 0.16) ** 2))[m][:, None])
        m, _, _ = A.local(f["bottom"])
        img[m] = G.lerp(img[m], np.array(SQ_BELLY), 0.7)
        m, _, _ = A.local(f["front"])
        img[m] = G.lerp(img[m], np.array(SQ_BELLY), 0.4)
    # Face: pale eye rings round black eyes, a darker snout.
    m, fx, fy = A.local(A.whole(*Q_EYE))
    eye = G.fill((6, 6, 8), (n, n))
    glint = np.clip(1 - ((fx - 0.35) / 0.12) ** 2 - ((fy - 0.55) / 0.12) ** 2, 0, 1)
    eye = G.over(eye, np.array((230, 240, 230)), G.smoothstep(0.0, 0.4, glint))
    img[m] = eye[m]
    fsk = A.faces(*Q_SKULL)
    for key in ("right", "left"):
        m, fx, fy = A.local(fsk[key])
        ring = np.clip(1 - ((fx - 0.35) / 0.32) ** 2 - ((fy - 0.32) / 0.3) ** 2, 0, 1)
        img[m] = G.lerp(img[m], np.array((210, 236, 150)), (G.smoothstep(0.0, 0.3, ring) * 0.6)[m][:, None])
    m, fx, fy = A.local(A.faces(*Q_SNOUT)["front"])
    img[m] = G.lerp(img[m], np.array((28, 30, 24)), (G.smoothstep(0.2, 0.6, 1 - fy) * 0.8)[m][:, None])
    m, _, _ = A.local(A.whole(*Q_JAW))
    img[m] = np.array((86, 40, 44))
    m, fx, fy = A.local(A.faces(*Q_EAR)["front"])
    img[m] = G.lerp(img[m], np.array((70, 50, 52)), 0.6)
    # Feet and hands: dark, almost black, with the claws.
    for b in Q_FOOT:
        m, _, _ = A.local(A.whole(*b))
        img[m] = G.lerp(img[m], np.array((30, 34, 28)), 0.6)
    img = G.add_lum(img, lum)
    m, fx, fy = A.local(A.rect(*Q_TEETH))
    img[m] = G.lerp(np.array((236, 160, 52)), np.array((150, 80, 26)), fy[..., None])[m]   # orange incisors
    m, _, _ = A.local(A.rect(*Q_WHISKER))
    img[m] = np.array((200, 214, 196))
    m, fx, fy = A.local(A.rect(*Q_CLAWS))
    img[m] = G.lerp(np.array((24, 22, 20)), np.array((90, 84, 70)), fy[..., None])[m]
    m, _, _ = A.local(A.rect(*Q_FLUFF))
    fluff = fur(rng, n, (70, 150, 80), SQ_DARK, (190, 250, 140), streak=14.0, coarse=1.3)
    img[m] = fluff[m]
    save(img, "l227_squirrel")

    # The fluorescent glow: the hair tips light up, densest along the back and over the whole
    # tail, faint on the belly. Not on the eyes, teeth or claws (they stay dark in the glow).
    glow = glow_canvas(n)
    # Fine streaks (single hairs lit at the tips), with a slow patchiness so it isn't uniform.
    strands = G.fnoise(rng, (n, n), beta=0.5, aniso=(1, 9), kmin=n / 14)
    tips = G.smoothstep(0.3, 1.5, strands + 0.5 * G.fnoise(rng, (n, n), beta=2.0)) * (0.55 + 0.45 * G.smoothstep(-1, 1, G.fnoise(rng, (n, n), beta=2.6)))
    colour = np.array((90, 255, 110))
    weight = np.zeros((n, n))
    for b in [Q_TORSO, Q_HAUNCH, Q_SKULL] + Q_ARM + Q_THIGH:
        f = A.faces(*b)
        for key, w in (("top", 1.0), ("right", 0.75), ("left", 0.75), ("back", 0.8), ("front", 0.5), ("bottom", 0.25)):
            m, _, _ = A.local(f[key])
            weight[m] = w
    for b in Q_TAIL:
        m, _, _ = A.local(A.whole(*b))
        weight[m] = 1.15
    m, _, _ = A.local(A.rect(*Q_FLUFF))
    weight[m] = 1.25
    g = np.clip(tips * weight, 0, 1.3)
    glow[..., :3] = g[..., None] * colour
    # A brighter yellow-green sheen on the tail, as if something in the fur itself burns.
    for b in Q_TAIL:
        m, fx, fy = A.local(A.whole(*b))
        glow[m, :3] += (G.smoothstep(0.8, 2.0, G.fnoise(rng, (n, n), beta=1.4))[m][:, None] * np.array((60, 40, 0)))
    glow[..., :3] = np.clip(glow[..., :3], 0, 255)
    save(glow, "l227_squirrel_glow")


# =============================================================================
# Sound helpers
# =============================================================================

def _t(dur):
    n = int(dur * G.SR)
    return n, np.arange(n) / G.SR


def _env(t, pts):
    return np.interp(t, *zip(*pts))


def _forest(rng, x, amount=0.22, decay=0.22, lp=3000.0) -> np.ndarray:
    """Outdoors among giant trunks and moss: a soft, dark, fairly long tail, no slap-back."""
    n = len(x)
    ir = G.reverb_ir(rng, 1.4, decay, lp=lp, predelay=0.02, early=((0.037, 0.25), (0.071, 0.15)))
    wet = G.conv(x, ir)[:n]
    return x + amount * G.norm(wet) * np.abs(x).max()


def _pad(x, extra):
    return np.concatenate([x, np.zeros(int(extra * G.SR))])


def _fry(rng, n, rate, irregular=0.35) -> np.ndarray:
    """Vocal fry: a train of soft pulses at an uneven rate (0..1), the rattle in a throat."""
    r = rate * (1 + G.smooth_jitter(rng, n, 9, irregular))
    ph = np.cumsum(r) / G.SR
    return (0.5 + 0.5 * np.cos(2 * np.pi * ph)) ** 3


def _band(rng, n, lo, hi, order=2):
    return G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, lo, hi, order))


OUT = OUT_SND


def _save(x, name, peak=0.85, fade_in=0.004, fade_out=0.06):
    G.save_ogg(x, OUT / f"{name}.ogg", peak=peak, fade_in=fade_in, fade_out=fade_out)


# =============================================================================
# Wolf sounds
# =============================================================================

def snd_l227pred_wolf_howl() -> None:
    """~3.6 s, 2 takes: the pack's alarm. A long rising howl in two voices at once, a sour
    interval apart (each half of the split jaw sings its own note), breathy, carried far."""
    for i, (seed, base) in enumerate(((227401, 330), (227402, 300)), 1):
        rng = G.rng_for(seed)
        dur = 3.6
        n, t = _t(dur)
        glide = _env(t, [(0, 0.62), (0.5, 0.95), (1.0, 1.08), (2.4, 1.0), (3.1, 0.82), (dur, 0.7)])
        vib = 1 + 0.012 * np.sin(2 * np.pi * 5.2 * t) * G.smoothstep(0.8, 1.6, t)
        f1 = base * glide * vib * (1 + G.smooth_jitter(rng, n, 4, 0.02))
        f2 = f1 * 1.41 * (1 + G.smooth_jitter(rng, n, 3, 0.012))   # a tritone above: wrong
        oo = [(450, 140, 1.0), (900, 220, 0.35), (2600, 400, 0.08)]
        v1 = G.voice(f1, oo, rng, nh=24, tilt=0.02)
        v2 = G.voice(f2, [(600, 160, 1.0), (1200, 260, 0.3)], rng, nh=18, tilt=0.02)
        breath = _band(rng, n, 400, 3000) * 0.5
        e = _env(t, [(0, 0), (0.25, 0.7), (0.8, 1.0), (2.6, 0.9), (3.3, 0.3), (dur, 0)])
        e2 = _env(t, [(0, 0), (0.6, 0.0), (1.0, 0.7), (2.6, 0.65), (3.2, 0.1), (dur, 0)])
        x = (G.norm(v1) * e + 0.6 * G.norm(v2) * e2 + 0.12 * G.norm(breath) * e)
        x = _pad(x, 1.4)
        _save(_forest(rng, x, 0.45, 0.4, 2600), f"l227_wolf_howl{i}", peak=0.84, fade_out=0.3)


def snd_l227pred_wolf_growl() -> None:
    """~1.8 s, 2 takes: a low rolling growl with a wet double clicking, the two jaws chattering."""
    for i, (seed, f_base) in enumerate(((227411, 72), (227412, 84)), 1):
        rng = G.rng_for(seed)
        dur = 1.8
        n, t = _t(dur)
        f0 = f_base * (1 + 0.15 * G.smoothstep(0, 0.6, t) - 0.1 * G.smoothstep(1.2, dur, t)) * (1 + G.smooth_jitter(rng, n, 6, 0.08))
        v = G.voice(f0, [(420, 170, 1.0), (1000, 260, 0.6), (2300, 380, 0.25)], rng, nh=70, tilt=0.05)
        fry = _fry(rng, n, 30.0, 0.5)
        rough = G.dist(G.norm(v) * (0.35 + 0.65 * fry), 3.2)
        breath = _band(rng, n, 300, 2400) * (0.4 + 0.6 * fry)
        e = _env(t, [(0, 0), (0.2, 0.7), (0.6, 1.0), (1.3, 0.85), (1.7, 0.1), (dur, 0)])
        chatter = G.clicks(rng, np.sort(rng.uniform(0.3, 1.5, 10)), dur, 1500, 3200, 0.002, 0.005)
        x = (G.norm(rough) + 0.35 * G.norm(breath)) * e + 0.18 * G.norm(chatter)
        x = G.fft_filter(x, lambda f: G.lp_gain(f, 4500, 2))
        _save(_forest(rng, _pad(x, 0.4), 0.2), f"l227_wolf_growl{i}", peak=0.8, fade_out=0.15)


def snd_l227pred_wolf_yip() -> None:
    """3 takes, ~0.5 s: a short sharp yelp, packmates answering the howl."""
    for i in range(1, 4):
        rng = G.rng_for(227420 + i)
        dur = 0.5
        n, t = _t(dur)
        top = rng.uniform(900, 1200)
        f0 = _env(t, [(0, top * 0.6), (0.05, top), (0.16, top * 0.85), (0.3, top * 0.55), (dur, top * 0.5)])
        v = G.voice(f0, [(1300, 300, 1.0), (2600, 500, 0.5)], rng, nh=14, tilt=0.03)
        e = _env(t, [(0, 0), (0.02, 1), (0.18, 0.6), (0.32, 0), (dur, 0)])
        x = G.dist(G.norm(v), 1.6) * e
        _save(_forest(rng, _pad(x, 0.6), 0.35, 0.3), f"l227_wolf_yip{i}", peak=0.75, fade_out=0.2)


def snd_l227pred_wolf_snap() -> None:
    """2 takes: the jaws slamming together sideways: two hard clacks a hair apart (left half,
    right half), the grind of teeth across teeth, a wet tear."""
    for i, seed in enumerate((227431, 227432), 1):
        rng = G.rng_for(seed)
        dur = 0.5
        n, t = _t(dur)
        clack = G.conv(G.clicks(rng, [0.008, 0.019 + 0.004 * i], dur, 1800, 3600, 0.003, 0.006),
                       G.ring([1500, 2700, 4100], [0.012, 0.008, 0.004], [1, 0.6, 0.35], 0.05))[:n]
        grind = _band(rng, n, 2000, 7000) * _env(t, [(0, 0), (0.02, 0), (0.04, 0.5), (0.14, 0.1), (0.2, 0), (dur, 0)])
        tear = _band(rng, n, 300, 2500, 1) * np.exp(-np.clip(t - 0.05, 0, None) / 0.06) * (t > 0.05)
        x = G.norm(clack) + 0.4 * G.norm(grind) + 0.45 * G.norm(tear)
        _save(_forest(rng, _pad(x, 0.3), 0.15), f"l227_wolf_snap{i}", peak=0.86, fade_in=0.001)


def snd_l227pred_wolf_pant() -> None:
    """3 takes, ~2 s: fast, shallow panting through a mouth that opens sideways (a doubled hiss)."""
    for i in range(1, 4):
        rng = G.rng_for(227440 + i)
        dur = 2.0
        n, t = _t(dur)
        x = np.zeros(n)
        pos = rng.uniform(0.02, 0.1)
        rate = rng.uniform(0.17, 0.22)
        k = 0
        while pos < dur - 0.2:
            length = rate * rng.uniform(0.7, 0.9)
            seg_n = int(length * G.SR)
            st = np.arange(seg_n) / G.SR
            centre = rng.uniform(900, 1300) * (1.2 if k % 2 == 0 else 1.0)
            air = G.svf(rng.normal(size=seg_n), centre, 2.0) + 0.6 * G.svf(rng.normal(size=seg_n), centre * 1.37, 2.4)
            seg = air * np.sin(np.pi * st / length) ** 1.6
            G.add_at(x, G.norm(seg) * rng.uniform(0.6, 1.0), pos, False)
            pos += rate
            k += 1
        x = G.fft_filter(x, lambda f: G.lp_gain(f, 5000, 2))
        _save(_forest(rng, x, 0.12), f"l227_wolf_pant{i}", peak=0.55, fade_in=0.03, fade_out=0.15)


def snd_l227pred_wolf_step() -> None:
    """4 takes: a heavy pad on moss over wood: a soft thump, claws ticking on bark, a rustle."""
    for i in range(1, 5):
        rng = G.rng_for(227450 + i)
        dur = 0.25
        n, t = _t(dur)
        thud = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 0.05], [110, 55])) / G.SR) * np.exp(-t / 0.025)
        pad = _band(rng, n, 200, 1400, 1) * np.exp(-t / 0.018)
        ticks = G.clicks(rng, [rng.uniform(0.004, 0.02), rng.uniform(0.02, 0.05)], dur, 2500, 5000, 0.001, 0.003)
        rustle = _band(rng, n, 1500, 7000, 1) * np.exp(-np.clip(t - 0.01, 0, None) / 0.06) * 0.6
        x = G.norm(thud) + 0.6 * G.norm(pad) + 0.35 * G.norm(ticks) + 0.3 * G.norm(rustle)
        _save(x, f"l227_wolf_step{i}", peak=0.6 * 10 ** (rng.uniform(-2, 0) / 20), fade_in=0.001, fade_out=0.04)


def snd_l227pred_wolf_hurt() -> None:
    """2 takes: a yelp that tears into a snarl."""
    for i, seed in enumerate((227461, 227462), 1):
        rng = G.rng_for(seed)
        dur = 0.6
        n, t = _t(dur)
        f0 = _env(t, [(0, 500), (0.04, 980 + 60 * i), (0.2, 700), (0.4, 260), (dur, 200)]) * (1 + G.smooth_jitter(rng, n, 25, 0.05))
        v = G.voice(f0, [(1000, 280, 1.0), (2200, 400, 0.6)], rng, nh=24)
        fry = _fry(rng, n, 55.0, 0.5)
        e = _env(t, [(0, 0), (0.01, 1), (0.2, 0.8), (0.5, 0.1), (dur, 0)])
        x = G.dist(G.norm(v) * (0.5 + 0.5 * fry * G.smoothstep(0.15, 0.3, t)), 3.0) * e
        _save(_forest(rng, _pad(x, 0.4), 0.25), f"l227_wolf_hurt{i}", peak=0.82, fade_out=0.15)


def snd_l227pred_wolf_death() -> None:
    """~2 s: a long falling whine in two voices that slide apart, a rattle, nothing."""
    rng = G.rng_for(227471)
    dur = 2.0
    n, t = _t(dur)
    f1 = _env(t, [(0, 700), (0.2, 820), (0.9, 520), (1.5, 260), (dur, 180)]) * (1 + G.smooth_jitter(rng, n, 10, 0.04))
    v1 = G.voice(f1, [(900, 260, 1.0), (2000, 400, 0.4)], rng, nh=20)
    v2 = G.voice(f1 * _env(t, [(0, 1.41), (dur, 1.9)]), [(1200, 300, 1.0)], rng, nh=12)
    e = _env(t, [(0, 0), (0.05, 1), (1.0, 0.7), (1.6, 0.15), (dur, 0)])
    rattle = G.clicks(rng, np.sort(rng.uniform(1.2, 1.8, 14)), dur, 300, 900, 0.005, 0.012)
    x = (G.norm(v1) + 0.5 * G.norm(v2)) * e + 0.25 * G.norm(rattle)
    _save(_forest(rng, _pad(x, 0.5), 0.3), "l227_wolf_death", peak=0.8, fade_out=0.25)


# =============================================================================
# Stag sounds
# =============================================================================

def snd_l227pred_stag_bellow() -> None:
    """~3.2 s, 2 takes: the warning. A rutting stag's roar from a chest far too big, grinding in
    vocal fry, with an elk's screaming whistle riding on top of it, then three hard grunts."""
    for i, (seed, f_base) in enumerate(((227501, 62), (227502, 70)), 1):
        rng = G.rng_for(seed)
        dur = 3.2
        n, t = _t(dur)
        f0 = f_base * _env(t, [(0, 0.8), (0.4, 1.15), (1.6, 1.05), (2.2, 0.9), (dur, 0.85)]) * (1 + G.smooth_jitter(rng, n, 5, 0.07))
        roar = G.voice(f0, [(380, 150, 1.0), (850, 240, 0.7), (2400, 420, 0.25)], rng, nh=110, tilt=0.06)
        fry = _fry(rng, n, 24.0, 0.5)
        roar = G.dist(G.norm(roar) * (0.4 + 0.6 * fry), 3.5)
        e = _env(t, [(0, 0), (0.15, 0.8), (0.5, 1.0), (1.9, 0.9), (2.2, 0.0), (dur, 0)])
        # The whistle: a pure, piercing glide, wrong for a deer this size.
        fw = _env(t, [(0, 700), (0.35, 1500), (1.2, 1650 + 80 * i), (1.8, 1250), (2.1, 900), (dur, 900)]) * (1 + G.smooth_jitter(rng, n, 8, 0.015))
        whistle = np.sin(2 * np.pi * np.cumsum(fw) / G.SR) + 0.3 * np.sin(4 * np.pi * np.cumsum(fw) / G.SR)
        ew = _env(t, [(0, 0), (0.3, 0), (0.55, 0.6), (1.6, 0.5), (2.0, 0.0), (dur, 0)])
        grunts = np.zeros(n)
        for g, start in enumerate((2.3, 2.6, 2.85)):
            gn, gt = _t(0.22)
            gv = G.voice(np.full(gn, f_base * 1.1), [(420, 160, 1.0), (900, 240, 0.5)], rng, nh=60) * np.sin(np.pi * gt / 0.22) ** 0.7
            G.add_at(grunts, G.dist(G.norm(gv), 3.0) * (1 - 0.15 * g), start, False)
        x = G.norm(roar) * e + 0.28 * G.norm(whistle) * ew + 0.7 * G.norm(grunts)
        x = G.fft_filter(x, lambda f: G.lp_gain(f, 5500, 2))
        _save(_forest(rng, _pad(x, 1.2), 0.45, 0.35), f"l227_stag_bellow{i}", peak=0.86, fade_out=0.3)


def snd_l227pred_stag_snort() -> None:
    """3 takes, ~0.6 s: a hard blast of air through big nostrils, a flutter of the lips."""
    for i in range(1, 4):
        rng = G.rng_for(227510 + i)
        dur = 0.6
        n, t = _t(dur)
        air = G.svf(rng.normal(size=n), _env(t, [(0, 1800), (0.1, 1100), (dur, 700)]), 1.5)
        flutter = _fry(rng, n, rng.uniform(28, 40), 0.4)
        e = _env(t, [(0, 0), (0.015, 1), (0.12, 0.7), (0.35, 0.1), (dur, 0)])
        x = G.norm(air) * (0.6 + 0.4 * flutter) * e
        _save(_forest(rng, _pad(x, 0.4), 0.2), f"l227_stag_snort{i}", peak=0.72, fade_out=0.15)


def snd_l227pred_stag_paw() -> None:
    """3 takes: a hoof scraping back along bark and moss: a scratch, a thump, flung debris."""
    for i in range(1, 4):
        rng = G.rng_for(227520 + i)
        dur = 0.55
        n, t = _t(dur)
        scrape = _band(rng, n, 600, 5000) * _env(t, [(0, 0), (0.03, 0.8), (0.22, 0.6), (0.3, 0), (dur, 0)])
        scrape *= 0.6 + 0.4 * _fry(rng, n, 60.0, 0.6)
        thud = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 0.06], [120, 50])) / G.SR) * np.exp(-t / 0.04)
        debris = G.clicks(rng, np.sort(rng.uniform(0.25, 0.5, 9)), dur, 1500, 5000, 0.001, 0.003)
        x = 0.8 * G.norm(scrape) + G.norm(thud) + 0.3 * G.norm(debris)
        _save(_forest(rng, x, 0.15), f"l227_stag_paw{i}", peak=0.7, fade_in=0.001)


def snd_l227pred_stag_charge() -> None:
    """2 takes, ~1.6 s: it goes: an explosive snort, then hoofbeats drumming faster and faster
    on the branch, the wood booming under them."""
    for i, seed in enumerate((227531, 227532), 1):
        rng = G.rng_for(seed)
        dur = 1.6
        n, t = _t(dur)
        x = np.zeros(n)
        snort = G.svf(rng.normal(size=int(0.3 * G.SR)), 1300, 1.4) * np.exp(-np.arange(int(0.3 * G.SR)) / G.SR / 0.08)
        G.add_at(x, G.norm(snort) * 0.8, 0.0, False)
        pos, gap = 0.18, 0.26
        while pos < dur - 0.1:
            bn, bt = _t(0.12)
            boom = np.sin(2 * np.pi * np.cumsum(np.interp(bt, [0, 0.05], [95, 42])) / G.SR) * np.exp(-bt / 0.05)
            knock = _band(rng, bn, 300, 2000, 1) * np.exp(-bt / 0.01)
            G.add_at(x, (G.norm(boom) + 0.5 * G.norm(knock)) * rng.uniform(0.75, 1.0), pos, False)
            pos += gap * rng.uniform(0.9, 1.1)
            gap = max(0.09, gap * 0.82)
        _save(_forest(rng, _pad(x, 0.5), 0.3, 0.3), f"l227_stag_charge{i}", peak=0.84, fade_out=0.2)


def snd_l227pred_stag_impale() -> None:
    """2 takes: run through: a blunt hit, a wet punch through, a crack, and a grunt of effort."""
    for i, seed in enumerate((227541, 227542), 1):
        rng = G.rng_for(seed)
        dur = 0.7
        n, t = _t(dur)
        hit = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 0.04], [140, 60])) / G.SR) * np.exp(-t / 0.05)
        wet = _band(rng, n, 200, 1800, 1) * np.exp(-np.clip(t - 0.02, 0, None) / 0.08) * (t > 0.02)
        squelch = G.clicks(rng, np.sort(rng.uniform(0.03, 0.2, 10)), dur, 400, 1200, 0.004, 0.01)
        crack = G.clicks(rng, [0.045, 0.06], dur, 2500, 5000, 0.002, 0.004)
        grunt = G.voice(np.full(n, 95.0), [(420, 150, 1), (900, 240, 0.5)], rng, nh=50) * _env(t, [(0, 0), (0.08, 0), (0.12, 1), (0.35, 0), (dur, 0)])
        x = G.norm(hit) + 0.7 * G.norm(wet) + 0.45 * G.norm(squelch) + 0.4 * G.norm(crack) + 0.4 * G.dist(G.norm(grunt), 3.0)
        _save(_forest(rng, x, 0.18), f"l227_stag_impale{i}", peak=0.88, fade_in=0.001, fade_out=0.1)


def snd_l227pred_stag_horn_hit() -> None:
    """2 takes: the horns driving into a trunk: a huge woody knock, the bark splitting, a creak."""
    for i, seed in enumerate((227551, 227552), 1):
        rng = G.rng_for(seed)
        dur = 1.1
        n, t = _t(dur)
        knock = G.conv(_band(rng, int(0.01 * G.SR), 200, 3000, 1), G.ring([180, 410, 760, 1320], [0.15, 0.09, 0.05, 0.03], [1, 0.7, 0.4, 0.2], 0.5))
        x = np.zeros(n)
        G.add_at(x, G.norm(knock), 0.0, False)
        split = G.clicks(rng, np.sort(rng.uniform(0.01, 0.12, 18)), dur, 1200, 4000, 0.002, 0.006)
        rate = _env(t, [(0, 30), (0.3, 30), (0.6, 18), (dur, 12)])
        pulses = np.sin(2 * np.pi * np.cumsum(rate) / G.SR) ** 21     # a slow train of wood "ticks"
        creak = G.svf(pulses, 700, 6.0)
        creak *= _env(t, [(0, 0), (0.25, 0), (0.4, 0.6), (0.9, 0.2), (dur, 0)])
        x = x + 0.5 * G.norm(split) + 0.35 * G.norm(creak)
        _save(_forest(rng, x, 0.3, 0.3), f"l227_stag_horn_hit{i}", peak=0.86, fade_in=0.001, fade_out=0.15)


def snd_l227pred_stag_step() -> None:
    """4 takes: a heavy cloven hoof on wood under moss: a deep clop and a soft crunch."""
    for i in range(1, 5):
        rng = G.rng_for(227560 + i)
        dur = 0.3
        n, t = _t(dur)
        clop = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 0.04], [220, 90])) / G.SR) * np.exp(-t / 0.03)
        click = G.clicks(rng, [0.002, rng.uniform(0.006, 0.012)], dur, 900, 2200, 0.002, 0.005)
        crunch = _band(rng, n, 1200, 6000, 1) * np.exp(-t / 0.04) * 0.6
        x = G.norm(clop) + 0.5 * G.norm(click) + 0.3 * G.norm(crunch)
        _save(x, f"l227_stag_step{i}", peak=0.65 * 10 ** (rng.uniform(-2, 0) / 20), fade_in=0.001, fade_out=0.05)


def snd_l227pred_stag_hurt() -> None:
    """2 takes: a hoarse bleating roar."""
    for i, seed in enumerate((227571, 227572), 1):
        rng = G.rng_for(seed)
        dur = 0.9
        n, t = _t(dur)
        f0 = _env(t, [(0, 140), (0.08, 260 + 30 * i), (0.4, 200), (dur, 110)]) * (1 + 0.05 * np.sin(2 * np.pi * 9 * t))
        v = G.voice(f0, [(600, 200, 1.0), (1500, 320, 0.6), (2700, 420, 0.25)], rng, nh=60)
        e = _env(t, [(0, 0), (0.03, 1), (0.5, 0.7), (0.8, 0.05), (dur, 0)])
        x = G.dist(G.norm(v) * (0.5 + 0.5 * _fry(rng, n, 35.0, 0.4)), 3.0) * e
        _save(_forest(rng, _pad(x, 0.6), 0.3), f"l227_stag_hurt{i}", peak=0.82, fade_out=0.2)


def snd_l227pred_stag_death() -> None:
    """~2.4 s: a long, sinking groan, the legs kicking at the wood, a last breath."""
    rng = G.rng_for(227581)
    dur = 2.4
    n, t = _t(dur)
    f0 = _env(t, [(0, 180), (0.3, 200), (1.2, 120), (1.8, 70), (dur, 55)]) * (1 + G.smooth_jitter(rng, n, 6, 0.06))
    v = G.voice(f0, [(500, 180, 1.0), (1100, 280, 0.5)], rng, nh=70)
    e = _env(t, [(0, 0), (0.08, 1), (1.3, 0.6), (1.9, 0.1), (dur, 0)])
    kicks = np.zeros(n)
    for start in (0.9, 1.25, 1.7):
        kn, kt = _t(0.1)
        G.add_at(kicks, np.sin(2 * np.pi * np.cumsum(np.interp(kt, [0, 0.04], [150, 60])) / G.SR) * np.exp(-kt / 0.03), start, False)
    last = G.svf(rng.normal(size=n), 700, 1.6) * _env(t, [(0, 0), (1.95, 0), (2.1, 0.5), (dur, 0)])
    x = G.dist(G.norm(v) * (0.4 + 0.6 * _fry(rng, n, 20.0, 0.6)), 2.5) * e + 0.5 * G.norm(kicks) + 0.3 * G.norm(last)
    _save(_forest(rng, _pad(x, 0.6), 0.3), "l227_stag_death", peak=0.82, fade_out=0.3)


# =============================================================================
# Squirrel sounds
# =============================================================================

def _chuck(rng, rate=1.0):
    """One squirrel 'chuk': a short hard click with a high, buzzy squeak on its tail."""
    dur = 0.06
    n, t = _t(dur)
    f0 = rng.uniform(2600, 3600) * rate * _env(t, [(0, 1.1), (dur, 0.85)])
    sq = G.voice(f0, [(4000 * rate, 900, 1.0)], rng, nh=4, tilt=0.02) * np.exp(-t / 0.018)
    click = _band(rng, n, 2000, 9000, 1) * np.exp(-t / 0.003)
    return G.norm(click) + 0.8 * G.norm(sq)


def snd_l227pred_squirrel_chitter() -> None:
    """4 takes, ~0.7 s: a squirrel's scolding alarm call, a fast run of chuks, a trailing whine."""
    for i in range(1, 5):
        rng = G.rng_for(227600 + i)
        dur = 0.75
        n, t = _t(dur)
        x = np.zeros(n)
        pos = 0.01
        gap = rng.uniform(0.05, 0.07)
        while pos < 0.5:
            G.add_at(x, _chuck(rng, rng.uniform(0.9, 1.1)) * rng.uniform(0.6, 1.0), pos, False)
            pos += gap * rng.uniform(0.85, 1.15)
        f0 = _env(t, [(0, 0), (0.45, 2400), (0.7, 1600), (dur, 1500)])
        whine = G.voice(np.maximum(f0, 1.0), [(3200, 700, 1.0)], rng, nh=4) * _env(t, [(0, 0), (0.45, 0), (0.5, 0.5), (0.7, 0), (dur, 0)])
        x = G.norm(x) + 0.35 * G.norm(whine)
        _save(_forest(rng, x, 0.15, 0.15, 5000), f"l227_squirrel_chitter{i}", peak=0.6, fade_out=0.08)


def snd_l227pred_squirrel_swarm() -> None:
    """3 takes, ~2.8 s: dozens of them: overlapping chattering from everywhere, a dense patter of
    claws on bark, the undergrowth thrashing. Built from many single calls, each a little off."""
    for i in range(1, 4):
        rng = G.rng_for(227610 + i)
        dur = 2.8
        n, t = _t(dur)
        x = np.zeros(n)
        for _ in range(60):
            start = rng.uniform(0, dur - 0.4)
            burst = np.zeros(int(0.4 * G.SR))
            pos = 0.0
            gap = rng.uniform(0.04, 0.08)
            for _ in range(int(rng.integers(3, 8))):
                G.add_at(burst, _chuck(rng, rng.uniform(0.75, 1.25)), pos, False)
                pos += gap
            far = rng.uniform(0.15, 1.0)
            b = G.fft_filter(burst, lambda f, c=3000 + 6000 * far: G.lp_gain(f, c, 1))
            G.add_at(x, G.norm(b) * far, start, False)
        dens = 900 * (0.7 + 0.3 * np.sin(2 * np.pi * t / dur * 1.5 + i))
        claws = (rng.random(n) < dens / G.SR) * rng.uniform(-1, 1, n)
        claws = G.fft_filter(claws, lambda f: G.bp_gain(f, 2000, 9000, 1))
        rustle = _band(rng, n, 800, 6000) * (0.6 + 0.4 * G.smooth_jitter(rng, n, 3, 1.0))
        e = _env(t, [(0, 0), (0.4, 1), (2.3, 1), (dur, 0)])
        y = (G.norm(x) + 0.35 * G.norm(claws) + 0.35 * G.norm(rustle)) * e
        _save(_forest(rng, y, 0.25, 0.2, 5000), f"l227_squirrel_swarm{i}", peak=0.7, fade_in=0.2, fade_out=0.4)


def snd_l227pred_squirrel_screech() -> None:
    """3 takes, ~0.7 s: a harsh, shrieking scream, too loud for something so small."""
    for i in range(1, 4):
        rng = G.rng_for(227620 + i)
        dur = 0.7
        n, t = _t(dur)
        top = rng.uniform(2200, 3000)
        f0 = _env(t, [(0, top * 0.7), (0.05, top), (0.35, top * 0.9), (dur, top * 0.6)]) * (1 + 0.04 * np.sin(2 * np.pi * 23 * t))
        v = G.voice(f0, [(3500, 800, 1.0), (6000, 1200, 0.5)], rng, nh=6, tilt=0.03)
        rasp = _band(rng, n, 3000, 9000) * 0.4
        e = _env(t, [(0, 0), (0.02, 1), (0.4, 0.8), (0.6, 0.1), (dur, 0)])
        x = (G.dist(G.norm(v), 2.5) + 0.4 * G.norm(rasp)) * e
        _save(_forest(rng, _pad(x, 0.4), 0.2, 0.2, 6000), f"l227_squirrel_screech{i}", peak=0.7, fade_out=0.15)


def snd_l227pred_squirrel_bite() -> None:
    """3 takes: tiny incisors: a sharp crunch, a tear, a wet click."""
    for i in range(1, 4):
        rng = G.rng_for(227630 + i)
        dur = 0.25
        n, t = _t(dur)
        crunch = G.clicks(rng, np.sort(rng.uniform(0.0, 0.05, 5)), dur, 2500, 6000, 0.001, 0.003)
        tear = _band(rng, n, 1500, 6000, 1) * np.exp(-np.clip(t - 0.04, 0, None) / 0.03) * (t > 0.04)
        wet = G.clicks(rng, [0.1, 0.13], dur, 600, 1400, 0.004, 0.008)
        x = G.norm(crunch) + 0.6 * G.norm(tear) + 0.4 * G.norm(wet)
        _save(x, f"l227_squirrel_bite{i}", peak=0.7, fade_in=0.001, fade_out=0.04)


def snd_l227pred_squirrel_step() -> None:
    """3 takes: a quick skitter of little claws."""
    for i in range(1, 4):
        rng = G.rng_for(227640 + i)
        dur = 0.18
        n, t = _t(dur)
        x = G.clicks(rng, np.sort(rng.uniform(0.0, 0.12, 6)), dur, 3000, 7000, 0.0008, 0.002)
        x = G.norm(x) + 0.3 * G.norm(_band(rng, n, 2000, 8000, 1) * np.exp(-t / 0.04))
        _save(x, f"l227_squirrel_step{i}", peak=0.45, fade_in=0.001, fade_out=0.03)


def snd_l227pred_squirrel_hurt() -> None:
    """2 takes: a short, piercing squeal."""
    for i in range(1, 3):
        rng = G.rng_for(227650 + i)
        dur = 0.3
        n, t = _t(dur)
        f0 = _env(t, [(0, 2600), (0.03, 3400), (dur, 2200)])
        v = G.voice(f0, [(3600, 800, 1.0)], rng, nh=5)
        x = G.norm(v) * _env(t, [(0, 0), (0.01, 1), (0.15, 0.6), (0.28, 0), (dur, 0)])
        _save(x, f"l227_squirrel_hurt{i}", peak=0.62, fade_out=0.03)


def snd_l227pred_squirrel_death() -> None:
    """A squeal that falls away into a rattling breath."""
    rng = G.rng_for(227661)
    dur = 0.7
    n, t = _t(dur)
    f0 = _env(t, [(0, 3000), (0.05, 3300), (0.4, 1600), (dur, 1200)])
    v = G.voice(f0, [(3200, 800, 1.0)], rng, nh=5) * _env(t, [(0, 0), (0.01, 1), (0.4, 0.4), (0.55, 0), (dur, 0)])
    rattle = G.clicks(rng, np.sort(rng.uniform(0.35, 0.65, 8)), dur, 1500, 3500, 0.002, 0.005)
    x = G.norm(v) + 0.35 * G.norm(rattle)
    _save(x, "l227_squirrel_death", peak=0.62, fade_out=0.05)


TEXTURES = [tex_l227pred_wolf, tex_l227pred_stag, tex_l227pred_squirrel]
SOUNDS = [
    snd_l227pred_wolf_howl, snd_l227pred_wolf_growl, snd_l227pred_wolf_yip, snd_l227pred_wolf_snap,
    snd_l227pred_wolf_pant, snd_l227pred_wolf_step, snd_l227pred_wolf_hurt, snd_l227pred_wolf_death,
    snd_l227pred_stag_bellow, snd_l227pred_stag_snort, snd_l227pred_stag_paw, snd_l227pred_stag_charge,
    snd_l227pred_stag_impale, snd_l227pred_stag_horn_hit, snd_l227pred_stag_step, snd_l227pred_stag_hurt,
    snd_l227pred_stag_death,
    snd_l227pred_squirrel_chitter, snd_l227pred_squirrel_swarm, snd_l227pred_squirrel_screech,
    snd_l227pred_squirrel_bite, snd_l227pred_squirrel_step, snd_l227pred_squirrel_hurt,
    snd_l227pred_squirrel_death,
]
