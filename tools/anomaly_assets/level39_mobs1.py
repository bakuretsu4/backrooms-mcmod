"""Level 39 "Megalophobia": Hounds (Entity 8) and Facelings (Entity 9).

Textures follow the UV maps written next to every box in client/HoundModel.java and
client/FacelingModel.java. Both models use a 128 x 128 UV layout; the PNGs are 256 x 256, so every
model pixel gets 2 x 2 texels (crisper hair strands, teeth and faint face features).

How a Minecraft box unfolds on the texture (box w x h x d at texOffs (u, v)):

           u   u+d     u+d+w   u+2d+w  u+2d+2w
      v    .   [ top ][bottom]
      v+d  [ -X ][front][ +X ][ back ]
      v+d+h

The front face is drawn as you look at it; on the side faces the edge that touches the front
face is the front edge; on the top face the lower edge is the front edge.

Sounds are mono 44.1 kHz OGG like everything else: wet, close, animal-but-human for the Hound;
breath through a mouth that isn't there for the Faceling.
"""
import numpy as np

import gen_assets as G

K = 2              # texels per model pixel
N = 128 * K        # texture size
OUT_TEX = G.TEX / "entity"
OUT_SND = G.SND / "entity"
XX, YY = G.grid((N, N))


# =============================================================================
# Texture helpers
# =============================================================================

def faces(u, v, w, h, d) -> dict:
    """Texel rectangles (x0, y0, x1, y1) of the six faces of a box at texOffs (u, v)."""
    r = {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }
    return {k: tuple(c * K for c in rect) for k, rect in r.items()}


def whole(u, v, w, h, d) -> tuple:
    """The texel rectangle covering every face of a box (its full UV footprint)."""
    return (u * K, v * K, (u + 2 * (d + w)) * K, (v + d + h) * K)


def rect(x0, y0, x1, y1):
    """A rectangle given in model-UV units, as texels."""
    return (x0 * K, y0 * K, x1 * K, y1 * K)


def local(r):
    """(mask, fx, fy) for a texel rectangle: fx, fy run 0..1 across it."""
    x0, y0, x1, y1 = r
    m = (XX >= x0) & (XX < x1) & (YY >= y0) & (YY < y1)
    fx = (XX - x0) / max(x1 - x0, 1e-6)
    fy = (YY - y0) / max(y1 - y0, 1e-6)
    return m, fx, fy


def shade_box(lum, u, v, w, h, d, top=6.0, bottom=-22.0, side_ao=-16.0, convex=0.0) -> None:
    """Fake lighting for one box: lighter top, dark underside, sides darkening toward the bottom,
    and optionally a soft dome across every face (smooth, rounded surfaces)."""
    f = faces(u, v, w, h, d)
    m, fx, fy = local(f["top"])
    lum[m] += top
    m, fx, fy = local(f["bottom"])
    lum[m] += bottom
    for k in ("right", "front", "left", "back"):
        m, fx, fy = local(f[k])
        lum[m] += (side_ao * G.smoothstep(0.35, 1.0, fy))[m]
    if convex:
        for k, r in f.items():
            m, fx, fy = local(r)
            dome = 1.0 - ((fx - 0.5) ** 2 + (fy - 0.5) ** 2) * 2.2
            lum[m] += (convex * dome)[m]


def ellipse(cx, cy, rx, ry):
    """Soft elliptical blob in texels (1 in the middle, 0 outside)."""
    return np.clip(1.0 - ((XX - cx) / rx) ** 2 - ((YY - cy) / ry) ** 2, 0, 1) ** 1.5


def apply(img, mask, color, alpha=1.0):
    """Paint `color` over `img` where mask (bool or 0..1) is set."""
    a = np.asarray(mask, float) * alpha
    return G.over(img, np.asarray(color, float), a)


# =============================================================================
# Hound texture
# =============================================================================

SKIN = (172, 164, 145)

# Every Hound box: (u, v, w, h, d), as in HoundModel.
H_CHEST = (0, 0, 9, 7, 10)
H_WAIST = (0, 18, 6, 5, 7)
H_PELVIS = (40, 0, 8, 6, 6)
H_NECK = (70, 0, 3.5, 3.5, 6)
H_SKULL = (40, 16, 6, 6, 7)
H_MAXILLA = (70, 12, 6.4, 2, 6)
H_JAW = (70, 22, 6, 1.8, 6.5)
H_MOUTH = (64, 64, 5.6, 2.5, 5.5)
H_UPPER_ARMS = [(0, 32, 2, 9.5, 2), (8, 32, 2, 9.5, 2)]
H_FOREARMS = [(16, 32, 1.8, 8.5, 1.8), (24, 32, 1.8, 8.5, 1.8)]
H_THIGHS = [(32, 32, 2.6, 10, 2.6), (44, 32, 2.6, 10, 2.6)]
H_SHINS = [(0, 46, 2, 9.5, 2), (8, 46, 2, 9.5, 2)]
H_PALM = (16, 46, 3, 1, 3)
H_FOOT = (16, 52, 2.4, 1.2, 5)


def _hound_skin(rng) -> np.ndarray:
    """Starved, grey-yellow skin: mottled, bruised, veined, grimy, scratched."""
    img = G.fill(SKIN, (N, N))
    lum = (G.fnoise(rng, (N, N), beta=2.8) * 7
           + G.fnoise(rng, (N, N), beta=1.3, kmin=24) * 2.5
           + rng.normal(0, 1.4, (N, N)))
    img = G.add_lum(img, lum)
    # Sallow, jaundiced patches and greyer, colder ones.
    sallow = G.smoothstep(0.2, 1.4, G.fnoise(rng, (N, N), beta=3.0))
    img = G.over(img, np.array([168, 166, 128]), sallow * 0.35)
    cold = G.smoothstep(0.4, 1.6, G.fnoise(rng, (N, N), beta=3.0))
    img = G.over(img, np.array([150, 150, 150]), cold * 0.3)
    # Bruises: sparse purple-grey blotches with soft yellowed rims.
    b = G.fnoise(rng, (N, N), beta=3.2)
    img = G.over(img, np.array([150, 140, 100]), G.smoothstep(1.2, 1.6, b) * 0.35)
    img = G.over(img, np.array([118, 98, 112]), G.smoothstep(1.55, 2.2, b) * 0.6)
    # Veins: thin branching blue-grey lines just under the skin.
    for seed_aniso in ((1, 3), (3, 1), (1, 1)):
        ridge = 1.0 - np.abs(G.fnoise(rng, (N, N), beta=2.0, aniso=seed_aniso))
        img = G.over(img, np.array([116, 124, 142]), G.smoothstep(0.93, 0.985, ridge) * 0.4)
    # Grime: dark brown dirt, worst in blotches.
    grime = G.smoothstep(0.5, 1.8, G.fnoise(rng, (N, N), beta=2.4))
    img = G.over(img, np.array([84, 74, 60]), grime * 0.3)
    # Scratches and scabbed scars.
    def scratches(d, k):
        for _ in range(30):
            x, y = rng.uniform(0, N), rng.uniform(0, N)
            a = rng.uniform(0, np.pi)
            length = rng.uniform(3, 12)
            d.line([(x * k, y * k), ((x + np.cos(a) * length) * k, (y + np.sin(a) * length) * k)],
                   fill=255, width=max(1, int(rng.uniform(0.6, 1.4) * k)))
    sc = G.pil_mask(scratches, (N, N))
    img = G.over(img, np.array([112, 66, 58]), sc * 0.45)
    return img


def tex_hound() -> None:
    rng = G.rng_for(39801)
    img = _hound_skin(rng)
    lum = np.zeros((N, N))

    # Lighting per box.
    for b in [H_CHEST, H_WAIST, H_PELVIS, H_NECK, H_SKULL, H_JAW] + H_UPPER_ARMS + H_FOREARMS + H_THIGHS + H_SHINS:
        shade_box(lum, *b, convex=5.0)
    shade_box(lum, *H_PALM, bottom=-30)
    shade_box(lum, *H_FOOT, bottom=-30)

    # Ribs: ridges and deep hollows down both flanks of the ribcage.
    f = faces(*H_CHEST)
    for side in ("right", "left"):
        m, fx, fy = local(f[side])
        x = fx if side == "right" else 1.0 - fx      # 0 = back end, 1 = shoulders
        ribs = np.cos(2 * np.pi * (x * 5.2 + 0.15))
        env = G.smoothstep(0.05, 0.25, fy) * (1 - G.smoothstep(0.75, 1.0, fy)) * G.smoothstep(0.0, 0.25, x)
        lum[m] += (ribs * 13 * env)[m]
        lum[m] -= (G.smoothstep(0.6, 1.0, ribs * -1) * 10 * env)[m]
    # The breastbone and the sunken belly under the ribs.
    m, fx, fy = local(f["bottom"])
    lum[m] += (np.exp(-((fx - 0.5) / 0.1) ** 2) * 9)[m]
    # Spine groove along the back of chest, waist and pelvis, with vertebra bumps.
    for b in (H_CHEST, H_WAIST, H_PELVIS):
        m, fx, fy = local(faces(*b)["top"])
        groove = np.exp(-((fx - 0.5) / 0.09) ** 2)
        lum[m] += (-14 * groove + 6 * groove * np.cos(2 * np.pi * fy * 4.5))[m]
        # Shoulder-blade and hip shadows either side of it.
        lum[m] += (6 * np.exp(-((np.abs(fx - 0.5) - 0.3) / 0.08) ** 2))[m]
    # Waist: hollow flanks.
    fw = faces(*H_WAIST)
    for side in ("right", "left", "bottom"):
        m, fx, fy = local(fw[side])
        lum[m] -= (9 * np.sin(np.pi * fx) * np.sin(np.pi * np.clip(fy, 0, 1)))[m]

    # Limbs: tendons running their length, dirt toward the hands and feet.
    for b in H_UPPER_ARMS + H_FOREARMS + H_THIGHS + H_SHINS:
        for k in ("right", "front", "left", "back"):
            m, fx, fy = local(faces(*b)[k])
            lum[m] += (5 * np.cos(2 * np.pi * fx * 2.0) * (0.4 + 0.6 * fy))[m]
            grime = G.smoothstep(0.4, 1.0, fy)
            img[m] = G.lerp(img[m], np.array([92, 80, 64]), (grime * 0.35)[m][:, None])
    # Hands and feet: filthy, calloused soles, knuckle creases.
    for b in (H_PALM, H_FOOT):
        r = whole(*b)
        m, fx, fy = local(r)
        img[m] = G.lerp(img[m], np.array([96, 84, 66]), 0.45)
        mb, _, _ = local(faces(*b)["bottom"])
        img[mb] = G.lerp(img[mb], np.array([58, 50, 42]), 0.6)

    # Skull: sunken eye sockets on the face, mostly hidden by hair (just glimpses).
    m, fx, fy = local(faces(*H_SKULL)["front"])
    for ex in (0.28, 0.72):
        sock = np.clip(1 - ((fx - ex) / 0.17) ** 2 - ((fy - 0.42) / 0.14) ** 2, 0, 1)
        lum[m] -= (sock * 55)[m]
        glint = np.clip(1 - ((fx - ex) / 0.03) ** 2 - ((fy - 0.43) / 0.03) ** 2, 0, 1)
        lum[m] += (glint * 60)[m]
    lum[m] -= (G.smoothstep(0.6, 1.0, fy) * 14)[m]
    # Upper mouth: lipless gums along the front, a ridged palate underneath.
    fmx = faces(*H_MAXILLA)
    m, fx, fy = local(fmx["front"])
    img = apply(img, m & (fy > 0.35), (112, 44, 46), 0.85)
    for k in ("right", "left"):
        m, fx, fy = local(fmx[k])
        img = apply(img, m & (fy > 0.55), (112, 44, 46), 0.75)
    m, fx, fy = local(fmx["bottom"])
    img = apply(img, m, (150, 72, 74))
    lum[m] += (np.cos(2 * np.pi * fy * 5) * 7)[m]
    # Jaw: gums and tongue on top, darker toward the throat.
    fj = faces(*H_JAW)
    m, fx, fy = local(fj["top"])
    img = apply(img, m, (130, 52, 54))
    lum[m] -= (G.smoothstep(0.6, 0.0, fy) * 30)[m]   # top of the face = back of the jaw
    lum[m] += (np.exp(-((fx - 0.5) / 0.18) ** 2) * 10 * G.smoothstep(0.2, 1.0, fy))[m]  # tongue
    for k in ("right", "left", "front"):
        m, fx, fy = local(fj[k])
        img = apply(img, m & (fy < 0.3), (112, 44, 46), 0.8)
    # Inside of the mouth: dark, wet, red-black.
    m, fx, fy = local(whole(*H_MOUTH))
    wet = G.fnoise(rng, (N, N), beta=2.0)
    inner = G.lerp(np.array([92, 26, 30]), np.array([30, 8, 10]), G.smoothstep(-1, 1.2, wet)[..., None])
    img[m] = inner[m]
    lum[m] += (G.smoothstep(2.0, 2.6, G.fnoise(rng, (N, N), beta=0.8)) * 40)[m]   # wet glints

    img = G.add_lum(img, lum)

    # Teeth: long, yellowed, stained brown at the gum, with dark gaps between.
    m, fx, fy = local(rect(64, 56, 80, 64))
    streak = G.fnoise(rng, (N, N), beta=1.6, aniso=(1, 8))
    tooth = G.lerp(np.array([140, 112, 74]), np.array([222, 212, 180]), G.smoothstep(0.0, 0.6, fy)[..., None])
    tooth = G.add_lum(tooth, streak * 9)
    img[m] = tooth[m]
    # Claws: black horn, ridged, paling to a dirty yellow at the tip.
    m, fx, fy = local(rect(80, 56, 96, 64))
    claw = G.lerp(np.array([34, 30, 28]), np.array([128, 116, 92]), G.smoothstep(0.45, 1.0, fy)[..., None])
    claw = G.add_lum(claw, G.fnoise(rng, (N, N), beta=1.4, aniso=(1, 10)) * 8)
    img[m] = claw[m]

    # Hair: lank black strands with a cold, wet sheen; a few greyer and browner strands.
    m, fx, fy = local(rect(96, 0, 128, 64))
    strands = G.fnoise(rng, (N, N), beta=1.5, aniso=(1, 14))
    fine = G.fnoise(rng, (N, N), beta=0.6, aniso=(1, 6), kmin=40)
    hair = G.fill((16, 15, 17), (N, N))
    hair = G.add_lum(hair, strands * 5 + fine * 2.5)
    sheen = G.smoothstep(1.0, 2.2, strands)
    hair = G.over(hair, np.array([74, 80, 92]), sheen * 0.55)
    odd = G.smoothstep(1.7, 2.4, G.fnoise(rng, (N, N), beta=1.2, aniso=(1, 16)))
    hair = G.over(hair, np.array([58, 46, 36]), odd * 0.6)
    hair = G.over(hair, np.array([6, 5, 6]), G.smoothstep(-1.0, -2.0, strands) * 0.8)
    img[m] = hair[m]

    G.save_png(img, OUT_TEX / "hound.png")


# =============================================================================
# Faceling textures
# =============================================================================

# Every Faceling box: (u, v, w, h, d), as in FacelingModel.
F_SKULL = (0, 0, 6.4, 7, 6.8)
F_SIDES = (28, 0, 7, 5.6, 6)
F_CROWN = (56, 0, 5.6, 0.6, 6)
F_CHIN = (56, 8, 5.2, 1.6, 3)
F_FACE = (108, 6, 5.4, 4.8, 0.3)
F_HAIR = (80, 0, 6.8, 3.5, 6.9)
F_NECK = (108, 0, 2, 1.8, 2)
F_CHEST = (0, 16, 7.5, 5, 3.8)
F_ABDOMEN = (24, 16, 6, 4, 3.2)
F_PELVIS = (44, 16, 7, 3, 3.6)
F_SHIRT = (0, 28, 8, 5.4, 4.2)
F_SHIRT_LOW = (28, 28, 6.6, 4.4, 3.7)
F_COLLAR = (76, 28, 4.4, 0.8, 3.2)
F_BELT = (52, 28, 7.4, 3.2, 3.9)
F_UPPER_ARM = (0, 40, 2.2, 5.5, 2.2)
F_SLEEVE = (10, 40, 2.7, 4, 2.7)
F_FOREARM = (22, 40, 1.9, 5, 1.9)
F_CUFF = (32, 40, 2.4, 4.2, 2.4)
F_PALM = (44, 40, 1.8, 2.4, 2.2)
F_FINGERS = (54, 40, 1.7, 1.4, 2.1)
F_THUMB = (64, 40, 0.7, 1.6, 0.7)
F_THIGH = (0, 52, 3, 6.5, 3)
F_TROUSER = (14, 52, 3.5, 6.7, 3.5)
F_SHIN = (30, 52, 2.4, 5.5, 2.4)
F_TROUSER_LOW = (42, 52, 2.9, 5, 2.9)
F_SHOE = (56, 52, 2.8, 1.3, 4.4)

SKIN_BOXES = [F_SKULL, F_SIDES, F_CROWN, F_CHIN, F_FACE, F_NECK, F_CHEST, F_ABDOMEN, F_PELVIS,
              F_UPPER_ARM, F_FOREARM, F_PALM, F_FINGERS, F_THUMB, F_THIGH, F_SHIN]

# Outfits: skin, shirt, shirt kind, trousers, shoes, shoe kind, hair (None = bald), long sleeves.
OUTFITS = [
    dict(skin=(226, 206, 188), shirt=(222, 214, 192), kind="button", trousers=(108, 108, 112),
         shoes=(30, 28, 28), shoe_kind="leather", hair=None, long=False),
    dict(skin=(198, 160, 130), shirt=(160, 184, 208), kind="button", trousers=(42, 48, 72),
         shoes=(92, 60, 38), shoe_kind="leather", hair=(64, 46, 32), long=True),
    dict(skin=(232, 212, 200), shirt=(134, 136, 138), kind="tee", trousers=(186, 168, 132),
         shoes=(214, 210, 200), shoe_kind="sneaker", hair=None, long=False),
    dict(skin=(150, 108, 82), shirt=(56, 76, 58), kind="sweater", trousers=(34, 34, 36),
         shoes=(24, 22, 22), shoe_kind="leather", hair=(22, 18, 16), long=True),
]


def _fabric(rng, base, kind) -> np.ndarray:
    """Full-canvas cloth of one colour: weave, soft folds, a little wear."""
    img = G.fill(base, (N, N))
    lum = G.fnoise(rng, (N, N), beta=2.6) * 3.5
    lum += G.fnoise(rng, (N, N), beta=1.8, aniso=(5, 1), kmin=3) * 3.0   # horizontal creases
    if kind == "sweater":
        # Knit: columns of little V stitches.
        lum += np.sin(2 * np.pi * XX / 2.6) * 5 + np.sin(2 * np.pi * (YY + np.abs(((XX % 2.6) - 1.3))) / 2.2) * 3
    elif kind == "twill":
        lum += np.sin(2 * np.pi * (XX + YY) / 2.2) * 2.0
    else:
        lum += (np.sin(np.pi * XX) * np.sin(np.pi * YY)) * 1.5          # fine weave
    lum += rng.normal(0, 0.8, (N, N))
    return G.add_lum(img, lum)


def _skin(rng, base) -> np.ndarray:
    """Mannequin skin: almost no texture at all, just a faint warm/cool variation. Too clean."""
    img = G.fill(base, (N, N))
    lum = G.fnoise(rng, (N, N), beta=3.4) * 2.0 + rng.normal(0, 0.35, (N, N))
    img = G.add_lum(img, lum)
    warm = G.smoothstep(-0.5, 1.5, G.fnoise(rng, (N, N), beta=3.0))
    return G.over(img, np.array(base) * np.array([1.03, 0.95, 0.93]), warm * 0.3)


def _copy_box(dst, src, b) -> None:
    m, _, _ = local(whole(*b))
    dst[m] = src[m]


def _faceling(outfit: int, partial: bool) -> np.ndarray:
    o = OUTFITS[outfit]
    rng = G.rng_for(39900 + outfit)
    img = np.zeros((N, N, 4))
    img[..., 3] = 255.0
    skin = _skin(rng, o["skin"])
    shirt = _fabric(rng, o["shirt"], o["kind"])
    trousers = _fabric(rng, o["trousers"], "twill")
    img[..., :3] = skin

    for b in (F_SHIRT, F_SHIRT_LOW, F_COLLAR, F_SLEEVE, F_CUFF):
        _copy_box(img[..., :3], shirt, b)
    for b in (F_BELT, F_TROUSER, F_TROUSER_LOW):
        _copy_box(img[..., :3], trousers, b)

    lum = np.zeros((N, N))
    # Smooth, softly lit, rounded: every skin box gets a gentle dome of light.
    for b in SKIN_BOXES:
        shade_box(lum, *b, top=5, bottom=-12, side_ao=-6, convex=7.0)
    for b in (F_SHIRT, F_SHIRT_LOW, F_SLEEVE, F_CUFF, F_BELT, F_TROUSER, F_TROUSER_LOW):
        shade_box(lum, *b, top=4, bottom=-16, side_ao=-9, convex=3.0)

    # --- Shirt details -----------------------------------------------------------------------
    fs = faces(*F_SHIRT)
    m, fx, fy = local(fs["front"])
    if o["kind"] == "button":
        placket = np.abs(fx - 0.5) < 0.05
        lum[m & placket] -= 6
        for by in (0.2, 0.5, 0.8):
            btn = np.clip(1 - ((fx - 0.5) / 0.045) ** 2 - ((fy - by) / 0.05) ** 2, 0, 1)
            img[..., :3] = G.over(img[..., :3], np.array([236, 232, 220]), (btn * m) * 0.9)
        # A breast pocket on the left side of the chest.
        pocket = (fx > 0.62) & (fx < 0.86) & (fy > 0.18) & (fy < 0.5)
        edge = pocket & ((fx < 0.64) | (fx > 0.84) | (fy > 0.47) | (fy < 0.21))
        lum[m & edge] -= 8
    elif o["kind"] == "tee":
        # Crew neck ribbing and a faded old print.
        lum[m & (fy < 0.08)] -= 7
    else:
        # Sweater: ribbed hem on the lower shirt piece below.
        pass
    # Fold shadows under the arms and at the sides.
    for side in ("right", "left"):
        m, fx, fy = local(fs[side])
        lum[m] -= (G.smoothstep(0.6, 1.0, np.abs(fx - 0.5) * 2) * 6)[m]
    # Lower shirt: tucked in (button shirts) or a ribbed hem (tee, sweater).
    for k, r in faces(*F_SHIRT_LOW).items():
        if k in ("top", "bottom"):
            continue
        m, fx, fy = local(r)
        if o["kind"] == "button":
            lum[m] -= (G.smoothstep(0.7, 1.0, fy) * 10)[m]           # bunching where it's tucked
            lum[m] += (np.sin(2 * np.pi * fx * 3) * 3 * G.smoothstep(0.6, 1.0, fy))[m]
        else:
            hem = fy > 0.78
            lum[m & hem] += (np.sin(2 * np.pi * XX / 1.6) * 4)[m & hem]
            lum[m & hem] -= 3
        if k == "front" and o["kind"] == "button":
            lum[m & (np.abs(fx - 0.5) < 0.05)] -= 6
    # Collar: a shirt collar is a little brighter, with a dark crease line.
    m, fx, fy = local(whole(*F_COLLAR))
    lum[m] += 5
    lum[m & (fy > 0.75)] -= 10

    # --- Sleeves -------------------------------------------------------------------------------
    for k, r in faces(*F_SLEEVE).items():
        m, fx, fy = local(r)
        if k in ("top", "bottom"):
            continue
        if not o["long"]:
            lum[m & (fy > 0.82)] -= 9          # rolled hem
        lum[m] += (np.sin(2 * np.pi * fy * 2 + fx) * 2.5)[m]
    m, _, _ = local(faces(*F_SLEEVE)["bottom"])
    if o["long"]:
        lum[m] -= 10
    else:
        img[m, 3] = 0.0                         # open hem: the arm comes out of it
    if o["long"]:
        for k, r in faces(*F_CUFF).items():
            m, fx, fy = local(r)
            if k in ("right", "front", "left", "back"):
                lum[m & (fy > 0.8)] -= 8       # cuff seam
                lum[m] += (np.sin(2 * np.pi * fy * 3) * 2.0)[m]
        m, _, _ = local(faces(*F_CUFF)["bottom"])
        img[m, 3] = 0.0                         # the wrist comes out of the cuff
        m, _, _ = local(faces(*F_CUFF)["top"])
        img[m, 3] = 0.0
    else:
        m, _, _ = local(whole(*F_CUFF))
        img[m, 3] = 0.0                         # short sleeves: no forearm sleeve at all

    # --- Trousers, belt, shoes --------------------------------------------------------------
    for b in (F_TROUSER, F_TROUSER_LOW):
        ft = faces(*b)
        m, fx, fy = local(ft["front"])
        lum[m] += (np.exp(-((fx - 0.5) / 0.06) ** 2) * 6 - np.exp(-((fx - 0.42) / 0.05) ** 2) * 4)[m]   # pressed crease
        for side in ("right", "left"):
            m, fx, fy = local(ft[side])
            lum[m & (np.abs(fx - 0.5) < 0.04)] -= 6                                                  # side seam
        m, _, _ = local(ft["top"])
        img[m, 3] = 0.0
        m, _, _ = local(ft["bottom"])
        img[m, 3] = 0.0
    m, fx, fy = local(faces(*F_TROUSER_LOW)["front"])
    lum[m] -= (G.smoothstep(0.75, 1.0, fy) * 7)[m]      # break over the shoe
    fb = faces(*F_BELT)
    for k in ("right", "front", "left", "back"):
        m, fx, fy = local(fb[k])
        belt = fy < 0.26
        img[..., :3] = G.over(img[..., :3], np.array([38, 28, 22]), (m & belt) * 0.92)
        lum[m & belt] += (np.sin(2 * np.pi * fx * 9) * 1.5)[m & belt]
        if k == "front":
            buckle = belt & (np.abs(fx - 0.5) < 0.08)
            img[..., :3] = G.over(img[..., :3], np.array([170, 160, 130]), (m & buckle) * 0.9)
            lum[m & (np.abs(fx - 0.5) < 0.02) & (fy > 0.3)] -= 8                                     # fly seam
    fsh = faces(*F_SHOE)
    m, fx, fy = local(whole(*F_SHOE))
    img[..., :3] = G.over(img[..., :3], np.array(o["shoes"]), m * 1.0)
    lum[m] += (G.fnoise(rng, (N, N), beta=2.2) * 3)[m]
    sole = (60, 56, 52) if o["shoe_kind"] == "leather" else (226, 222, 214)
    for k in ("right", "front", "left", "back"):
        m, fx, fy = local(fsh[k])
        img[..., :3] = G.over(img[..., :3], np.array(sole), (m & (fy > 0.68)) * 1.0)
        if o["shoe_kind"] == "leather":
            lum[m] += (np.exp(-((fy - 0.3) / 0.15) ** 2) * 12)[m]                                     # polish
    m, fx, fy = local(fsh["bottom"])
    img[..., :3] = G.over(img[..., :3], np.array(sole) * 0.7, m * 1.0)
    m, fx, fy = local(fsh["top"])
    # Laces near the toe end of the top face (the lower edge of the top face is the front).
    laces = (np.abs(fx - 0.5) < 0.22) & (fy > 0.35) & (fy < 0.75) & (np.sin(2 * np.pi * fy * 6) > 0.4)
    lace_col = (210, 206, 196) if o["shoe_kind"] == "sneaker" else (20, 18, 18)
    img[..., :3] = G.over(img[..., :3], np.array(lace_col), (m & laces) * 0.85)

    # --- Hair cap -------------------------------------------------------------------------------
    fh = faces(*F_HAIR)
    if o["hair"] is None:
        m, _, _ = local(whole(*F_HAIR))
        img[m, 3] = 0.0
    else:
        hair = G.fill(o["hair"], (N, N))
        hair = G.add_lum(hair, G.fnoise(rng, (N, N), beta=1.4, aniso=(1, 10)) * 6 + G.fnoise(rng, (N, N), beta=2.5) * 3)
        _copy_box(img[..., :3], hair, F_HAIR)
        ragged = G.fnoise(rng, (N, N), beta=1.0, aniso=(1, 0.2), kmin=8) * 0.06
        m, fx, fy = local(fh["front"])
        img[m & (fy > 0.28 + ragged), 3] = 0.0                     # hairline: only a fringe on the brow
        for side in ("right", "left"):
            m, fx, fy = local(fh[side])
            front_edge = fx if side == "right" else 1.0 - fx       # 1 = toward the face
            cut = 0.92 - 0.45 * G.smoothstep(0.55, 0.95, front_edge) + ragged
            img[m & (fy > cut), 3] = 0.0                            # sideburns, then clear above the ears
        m, fx, fy = local(fh["back"])
        img[m & (fy > 0.95 + ragged), 3] = 0.0
        m, _, _ = local(fh["bottom"])
        img[m, 3] = 0.0
        m, fx, fy = local(fh["top"])
        lum[m] += 5

    # --- The face ------------------------------------------------------------------------------
    # True facelings: nothing at all, just the smooth dome of a mask. Partial: features too faint
    # to be sure of, as if pressed through a sheet.
    if partial:
        m, fx, fy = local(faces(*F_FACE)["front"])
        for ex in (0.24, 0.76):
            sock = np.clip(1 - ((fx - ex) / 0.17) ** 2 - ((fy - 0.38) / 0.11) ** 2, 0, 1) ** 1.3
            lum[m] -= (sock * 16)[m]
            lid = np.clip(1 - ((fx - ex) / 0.12) ** 2 - ((fy - 0.4) / 0.018) ** 2, 0, 1)
            lum[m] -= (lid * 12)[m]                                                       # closed lids
        brow = np.exp(-((fy - 0.24) / 0.06) ** 2) * (np.abs(fx - 0.5) > 0.08)
        lum[m] += (brow * 6)[m]
        ridge = np.exp(-((fx - 0.5) / 0.06) ** 2) * G.smoothstep(0.35, 0.5, fy) * (1 - G.smoothstep(0.66, 0.72, fy))
        lum[m] += (ridge * 8)[m]
        tip = np.clip(1 - ((fx - 0.5) / 0.12) ** 2 - ((fy - 0.75) / 0.05) ** 2, 0, 1)
        lum[m] -= (tip * 10)[m]
        mouth = np.exp(-((fy - 0.93) / 0.03) ** 2) * (np.abs(fx - 0.5) < 0.2)
        lum[m] -= (mouth * 14)[m]
        m, fx, fy = local(faces(*F_CHIN)["front"])
        lum[m] -= (np.exp(-((fy - 0.1) / 0.15) ** 2) * (np.abs(fx - 0.5) < 0.25) * 6)[m]   # under the lip
        m, fx, fy = local(faces(*F_SKULL)["front"])
        cheek = np.exp(-((np.abs(fx - 0.5) - 0.38) / 0.08) ** 2) * G.smoothstep(0.4, 0.7, fy)
        lum[m] -= (cheek * 6)[m]

    img[..., :3] = G.add_lum(img[..., :3], lum)
    return img


def tex_faceling() -> None:
    for outfit in range(len(OUTFITS)):
        G.save_png(_faceling(outfit, False), OUT_TEX / f"faceling_{outfit}.png")
        G.save_png(_faceling(outfit, True), OUT_TEX / f"faceling_{outfit}_partial.png")


# =============================================================================
# Sound helpers
# =============================================================================

def _t(dur):
    n = int(dur * G.SR)
    return n, np.arange(n) / G.SR


def _tunnel(rng, x, amount=0.18, decay=0.09) -> np.ndarray:
    """A short, damped room: the marble tunnels swallow sound (no long echo)."""
    n = len(x)
    ir = G.reverb_ir(rng, 0.5, decay, lp=3500, early=((0.011, 0.5), (0.023, 0.3)))
    return x + amount * G.norm(G.conv(x, ir)[:n]) * np.abs(x).max()


def _fry(rng, n, rate, irregular=0.35) -> np.ndarray:
    """Vocal fry: a train of soft pulses at an uneven rate, 0..1 (the rattle in a throat)."""
    r = rate * (1 + G.smooth_jitter(rng, n, 9, irregular))
    ph = np.cumsum(r) / G.SR
    pulse = 0.5 + 0.5 * np.cos(2 * np.pi * ph)
    return pulse ** 3


def _env(t, pts):
    return np.interp(t, *zip(*pts))


# =============================================================================
# Hound sounds
# =============================================================================

def snd_hound_growl() -> None:
    """~2.4 s: the warning. A deep, ragged growl from a human throat: a low voice rattling in
    vocal fry under a hoarse breath, rising, holding, and dying back. Wet clicks of saliva."""
    takes = ((39811, 58, 1.0), (39812, 66, 1.12))
    for i, (seed, f_base, form) in enumerate(takes, 1):
        rng = G.rng_for(seed)
        dur = 2.4
        n, t = _t(dur)
        f0 = f_base * (1 + 0.25 * G.smoothstep(0.0, 0.8, t) - 0.12 * G.smoothstep(1.6, dur, t)) * (1 + G.smooth_jitter(rng, n, 6, 0.08))
        forms = [(380 * form, 160, 1.0), (920 * form, 260, 0.7), (2400 * form, 420, 0.3)]
        v = G.voice(f0, forms, rng, nh=90, tilt=0.06)
        fry = _fry(rng, n, 28.0 * form, 0.45)
        rough = G.dist(G.norm(v) * (0.35 + 0.65 * fry), 3.0)
        breath = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 300, 2200, 2)) * (0.4 + 0.6 * fry)
        sub = np.sin(2 * np.pi * np.cumsum(f0 * 0.5) / G.SR) * fry
        e = _env(t, [(0, 0), (0.25, 0.55), (0.7, 0.9), (1.3, 1.0), (1.9, 0.75), (2.25, 0.1), (dur, 0)])
        wet = G.clicks(rng, np.sort(rng.uniform(0.3, 2.0, 9)), dur, 600, 1500, 0.003, 0.009)
        x = (G.norm(rough) + 0.45 * G.norm(breath) + 0.35 * G.norm(sub)) * e + 0.12 * G.norm(wet)
        x = G.fft_filter(x, lambda f: G.lp_gain(f, 4500, 2))
        G.save_ogg(_tunnel(rng, x), OUT_SND / f"hound_growl{i}.ogg", peak=0.88, fade_out=0.1)


def snd_hound_snarl() -> None:
    """~1 s: the pounce. A sharp rising snarl with bared-teeth hiss and a clack of teeth."""
    for i, seed in enumerate((39821, 39822), 1):
        rng = G.rng_for(seed)
        dur = 1.0
        n, t = _t(dur)
        lift = 1.0 + 0.1 * (i - 1)
        f0 = _env(t, [(0, 110), (0.12, 240 * lift), (0.35, 300 * lift), (0.7, 190), (dur, 140)]) * (1 + G.smooth_jitter(rng, n, 20, 0.07))
        v = G.voice(f0, [(700, 240, 1.0), (1500, 320, 0.8), (2900, 500, 0.4)], rng, nh=50, tilt=0.05)
        fry = _fry(rng, n, 45.0, 0.5)
        hiss = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 2500, 8000, 2))
        e = _env(t, [(0, 0), (0.04, 1.0), (0.45, 0.85), (0.8, 0.25), (dur, 0)])
        clack = G.clicks(rng, [0.01, 0.025], dur, 1800, 3800, 0.004, 0.008)
        x = (G.dist(G.norm(v) * (0.4 + 0.6 * fry), 5.0) + 0.5 * G.norm(hiss) * e) * e + 0.5 * G.norm(clack)
        G.save_ogg(_tunnel(rng, x, 0.22), OUT_SND / f"hound_snarl{i}.ogg", peak=0.89, fade_out=0.08)


def snd_hound_breath() -> None:
    """~2 s: ragged, wet panting through a mouth full of teeth: quick rasping in-out breaths
    with a phlegmy rattle and a groan barely under them."""
    for i, seed in enumerate((39831, 39832, 39833), 1):
        rng = G.rng_for(seed)
        dur = 2.0
        n, t = _t(dur)
        x = np.zeros(n)
        pos = rng.uniform(0.02, 0.1)
        inhale = True
        while pos < dur - 0.3:
            length = rng.uniform(0.18, 0.32)
            seg_n = int(length * G.SR)
            st = np.arange(seg_n) / G.SR
            # Breaths alternate: in (higher, sharper), out (lower, longer).
            centre = rng.uniform(900, 1500) * (1.25 if inhale else 1.0)
            inhale = not inhale
            air = G.svf(rng.normal(size=seg_n), centre * (1 + 0.3 * st / length), 2.2)
            seg = air * np.sin(np.pi * st / length) ** 1.5
            rattle = _fry(rng, seg_n, rng.uniform(30, 45), 0.4)
            seg *= 0.6 + 0.4 * rattle
            G.add_at(x, G.norm(seg) * rng.uniform(0.6, 1.0), pos, False)
            pos += length + rng.uniform(0.05, 0.18)
        f0 = 85 * (1 + G.smooth_jitter(rng, n, 4, 0.1))
        groan = G.voice(f0, [(420, 160, 1.0), (900, 240, 0.5)], rng, nh=40) * (0.5 + 0.5 * np.sin(np.pi * t / dur))
        wet = G.clicks(rng, np.sort(rng.uniform(0.1, dur - 0.1, 12)), dur, 500, 1300, 0.003, 0.008)
        y = G.norm(x) + 0.12 * G.norm(groan) + 0.15 * G.norm(wet)
        y = G.fft_filter(y, lambda f: G.lp_gain(f, 5000, 2))
        G.save_ogg(_tunnel(rng, y, 0.15), OUT_SND / f"hound_breath{i}.ogg", peak=0.7, fade_in=0.03, fade_out=0.15)


def snd_hound_step() -> None:
    """4 takes: a bare palm slapping marble tile, and the tick of claws."""
    for i in range(1, 5):
        rng = G.rng_for(39840 + i)
        dur = 0.22
        n, t = _t(dur)
        slap = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 500, 2600, 1)) * np.exp(-t / 0.012)
        thud = np.sin(2 * np.pi * np.cumsum(np.interp(t, [0, 0.05], [140, 60])) / G.SR) * np.exp(-t / 0.03)
        ticks = G.clicks(rng, [rng.uniform(0.005, 0.02), rng.uniform(0.03, 0.06), rng.uniform(0.06, 0.1)], dur, 3500, 6500, 0.001, 0.003)
        x = G.norm(slap) + 0.7 * G.norm(thud) + 0.5 * G.norm(ticks)
        G.save_ogg(_tunnel(rng, x, 0.12, 0.05), OUT_SND / f"hound_step{i}.ogg",
                   peak=0.75 * 10 ** (rng.uniform(-1.5, 0) / 20), fade_in=0.001, fade_out=0.03)


def snd_hound_splash() -> None:
    """3 takes: running through shallow water: a hard slap on the surface, a spray of droplets
    landing, and a short wash."""
    for i in range(1, 4):
        rng = G.rng_for(39850 + i)
        dur = 0.55
        n, t = _t(dur)
        slap = G.fft_filter(rng.normal(size=n), lambda f: G.hp_gain(f, 250, 1) * G.lp_gain(f, 7000, 1)) * np.exp(-t / 0.025)
        body = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 150, 900, 1)) * np.exp(-t / 0.06)
        drops = np.zeros(n)
        for _ in range(int(rng.integers(14, 24))):
            t0 = rng.uniform(0.03, 0.4)
            d_n = int(0.03 * G.SR)
            dt = np.arange(d_n) / G.SR
            f = rng.uniform(900, 3200) * (1 + dt * rng.uniform(8, 20))       # rising "plink"
            drop = np.sin(2 * np.pi * np.cumsum(f) / G.SR) * np.exp(-dt / rng.uniform(0.004, 0.01))
            G.add_at(drops, drop * rng.uniform(0.2, 1.0), t0, False)
        wash = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 600, 5000, 1)) * np.exp(-np.clip(t - 0.05, 0, None) / 0.15) * G.smoothstep(0.0, 0.05, t)
        x = G.norm(slap) + 0.6 * G.norm(body) + 0.35 * G.norm(drops) + 0.35 * G.norm(wash)
        G.save_ogg(_tunnel(rng, x, 0.15), OUT_SND / f"hound_splash{i}.ogg", peak=0.8, fade_in=0.001, fade_out=0.08)


def snd_hound_bite() -> None:
    """2 takes: jaws snapping shut: a hard double clack of teeth, a wet tear, a short grunt."""
    for i, seed in enumerate((39861, 39862), 1):
        rng = G.rng_for(seed)
        dur = 0.45
        n, t = _t(dur)
        clack = G.conv(G.clicks(rng, [0.008, 0.02 + 0.006 * i], dur, 2000, 4200, 0.003, 0.006),
                       G.ring([1700, 2900, 4300], [0.01, 0.007, 0.004], [1, 0.6, 0.4], 0.04))[:n]
        tear = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 400, 3000, 1)) * np.exp(-np.clip(t - 0.03, 0, None) / 0.05) * (t > 0.03)
        f0 = _env(t, [(0, 160), (0.1, 220), (dur, 120)])
        grunt = G.voice(f0, [(600, 200, 1.0), (1300, 300, 0.5)], rng, nh=40) * _env(t, [(0, 0), (0.05, 1), (0.2, 0.4), (0.35, 0), (dur, 0)])
        x = G.norm(clack) + 0.55 * G.norm(tear) + 0.45 * G.dist(G.norm(grunt), 3.0)
        G.save_ogg(_tunnel(rng, x, 0.15), OUT_SND / f"hound_bite{i}.ogg", peak=0.89, fade_in=0.001, fade_out=0.05)


def snd_hound_whine() -> None:
    """2 takes, ~1.1 s: stared down. A thin, uneasy, almost human whimper through the teeth,
    breaking into a hiss as it backs away."""
    for i, seed in enumerate((39871, 39872), 1):
        rng = G.rng_for(seed)
        dur = 1.1
        n, t = _t(dur)
        f0 = _env(t, [(0, 520), (0.2, 640 + 40 * i), (0.5, 560), (0.75, 470), (dur, 420)]) * (1 + 0.04 * np.sin(2 * np.pi * 7 * t) + G.smooth_jitter(rng, n, 15, 0.05))
        v = G.voice(f0, [(1100, 300, 1.0), (2600, 500, 0.5)], rng, nh=20, tilt=0.02)
        breath = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 1500, 6000, 2))
        e = _env(t, [(0, 0), (0.08, 0.8), (0.55, 0.6), (0.8, 0.2), (dur, 0)])
        hiss_e = _env(t, [(0, 0), (0.6, 0.0), (0.75, 0.7), (1.0, 0.2), (dur, 0)])
        x = G.norm(v) * e + 0.35 * G.norm(breath) * (e * 0.6 + hiss_e)
        G.save_ogg(_tunnel(rng, x, 0.18), OUT_SND / f"hound_whine{i}.ogg", peak=0.7, fade_out=0.1)


def snd_hound_hurt() -> None:
    """2 takes: a short yelping shriek-snarl, ragged and furious."""
    for i, seed in enumerate((39881, 39882), 1):
        rng = G.rng_for(seed)
        dur = 0.55
        n, t = _t(dur)
        f0 = _env(t, [(0, 380), (0.06, 820 + 80 * i), (0.25, 600), (dur, 300)]) * (1 + G.smooth_jitter(rng, n, 30, 0.06))
        v = G.voice(f0, [(900, 260, 1.0), (2100, 380, 0.7), (3300, 500, 0.3)], rng, nh=30)
        fry = _fry(rng, n, 60.0, 0.5)
        e = _env(t, [(0, 0), (0.015, 1.0), (0.2, 0.75), (0.45, 0.1), (dur, 0)])
        x = G.dist(G.norm(v) * (0.5 + 0.5 * fry), 4.0) * e
        x += 0.25 * G.norm(G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 2000, 7000, 2))) * e
        G.save_ogg(_tunnel(rng, x), OUT_SND / f"hound_hurt{i}.ogg", peak=0.89, fade_out=0.05)


def snd_hound_death() -> None:
    """~1.9 s: a gurgling howl that sinks and collapses into wet rattles and one last breath."""
    rng = G.rng_for(39891)
    dur = 1.9
    n, t = _t(dur)
    f0 = _env(t, [(0, 420), (0.15, 600), (0.6, 380), (1.1, 160), (1.5, 90), (dur, 70)]) * (1 + G.smooth_jitter(rng, n, 12, 0.08))
    v = G.voice(f0, [(800, 260, 1.0), (1700, 360, 0.6), (2800, 500, 0.25)], rng, nh=50)
    gurgle = _fry(rng, n, 18.0, 0.7)
    e = _env(t, [(0, 0), (0.03, 1.0), (0.6, 0.85), (1.2, 0.45), (1.55, 0.1), (dur, 0)])
    wet = G.clicks(rng, np.sort(rng.uniform(0.7, 1.7, 22)), dur, 300, 900, 0.006, 0.015)
    last = G.svf(rng.normal(size=n), 900, 1.8) * _env(t, [(0, 0), (1.45, 0), (1.6, 0.6), (dur, 0)])
    x = G.dist(G.norm(v) * (0.35 + 0.65 * gurgle), 3.5) * e + 0.4 * G.norm(wet) + 0.3 * G.norm(last)
    G.save_ogg(_tunnel(rng, x, 0.2), OUT_SND / "hound_death.ogg", peak=0.89, fade_out=0.1)


# =============================================================================
# Faceling sounds
# =============================================================================

def _nasal(rng, n, t, dur, rate=1.0, whistle=2300.0) -> np.ndarray:
    """One breath in and out through the nose of something with no mouth: airy hiss through a
    narrow passage, a thin whistle riding on the inhale."""
    inhale_end = 0.45 * dur
    e_in = G.smoothstep(0.0, 0.35 * dur, t) * (1 - G.smoothstep(inhale_end - 0.1 * dur, inhale_end, t))
    e_out = G.smoothstep(inhale_end + 0.05 * dur, inhale_end + 0.2 * dur, t) * (1 - G.smoothstep(0.8 * dur, dur, t))
    air_in = G.svf(rng.normal(size=n), rate * (1200 + 900 * G.smoothstep(0, inhale_end, t)), 3.0) * e_in
    air_out = G.svf(rng.normal(size=n), rate * (1000 - 300 * G.smoothstep(inhale_end, dur, t)), 2.0) * e_out
    tone = np.sin(2 * np.pi * np.cumsum(whistle * rate * (1 + 0.05 * np.sin(2 * np.pi * 3.5 * t) + G.smooth_jitter(rng, n, 6, 0.04))) / G.SR)
    whistle_s = tone * e_in * (0.4 + 0.6 * G.smoothstep(0.3, 1.2, G.smooth_jitter(rng, n, 3, 1.0) + 0.5))
    return G.norm(air_in) * 0.9 + G.norm(air_out) + 0.18 * G.norm(whistle_s)


def snd_faceling_wheeze() -> None:
    """3 takes, ~2.6 s: wheezing through no mouth: a thin whistling inhale, a slow exhale that
    catches and rattles at the end. Muffled, as if behind skin."""
    for i, (seed, rate) in enumerate(((39911, 1.0), (39912, 0.92), (39913, 1.08)), 1):
        rng = G.rng_for(seed)
        dur = 2.6
        n, t = _t(dur)
        x = _nasal(rng, n, t, dur, rate)
        rattle = G.clicks(rng, np.sort(rng.uniform(1.7, 2.3, 10)), dur, 400 * rate, 900 * rate, 0.004, 0.01)
        x = x + 0.25 * G.norm(rattle)
        x = G.fft_filter(x, lambda f: G.lp_gain(f, 3200, 2))
        G.save_ogg(x, OUT_SND / f"faceling_wheeze{i}.ogg", peak=0.55, fade_in=0.05, fade_out=0.2)


def snd_faceling_rasp() -> None:
    """2 takes, ~1.5 s: a hostile one has seen you. Fast, hard rasping gasps with a choked
    groan under them, muffled by skin where a mouth should be."""
    for i, seed in enumerate((39921, 39922), 1):
        rng = G.rng_for(seed)
        dur = 1.5
        n, t = _t(dur)
        x = np.zeros(n)
        for k, start in enumerate((0.0, 0.42, 0.8)):
            length = 0.36 - 0.04 * k
            seg_n = int(length * G.SR)
            st = np.arange(seg_n) / G.SR
            gasp = G.svf(rng.normal(size=seg_n), 1300 + 1400 * st / length, 4.0) * np.sin(np.pi * st / length) ** 0.8
            gasp *= 0.5 + 0.5 * _fry(rng, seg_n, 70.0, 0.5)
            G.add_at(x, G.norm(gasp) * (1.0 - 0.15 * k), start, False)
        f0 = _env(t, [(0, 150), (0.4, 190 + 20 * i), (1.0, 170), (dur, 130)]) * (1 + G.smooth_jitter(rng, n, 10, 0.06))
        groan = G.voice(f0, [(400, 150, 1.0), (800, 200, 0.4)], rng, nh=24)
        groan = G.fft_filter(groan, lambda f: G.lp_gain(f, 650, 3))           # mouth closed: "mmmh"
        y = G.norm(x) + 0.45 * G.norm(groan) * _env(t, [(0, 0), (0.1, 1), (1.2, 0.8), (dur, 0)])
        y = G.fft_filter(y, lambda f: G.lp_gain(f, 4500, 2))
        G.save_ogg(y, OUT_SND / f"faceling_rasp{i}.ogg", peak=0.8, fade_in=0.01, fade_out=0.12)


def snd_faceling_step() -> None:
    """4 takes: plain shoes on packed dirt and grit."""
    for i in range(1, 5):
        rng = G.rng_for(39930 + i)
        dur = 0.26
        n, t = _t(dur)
        heel = G.fft_filter(rng.normal(size=n), lambda f: G.lp_gain(f, 500, 2)) * np.exp(-t / 0.025)
        dens = 5000 * np.exp(-t / 0.05)
        grit = (rng.random(n) < dens / G.SR) * rng.uniform(-1, 1, n)
        grit = G.fft_filter(grit, lambda f: G.bp_gain(f, 1200, 6000, 1))
        toe = G.fft_filter(rng.normal(size=n), lambda f: G.lp_gain(f, 700, 2)) * np.exp(-np.clip(t - 0.06, 0, None) / 0.02) * (t > 0.06)
        x = G.norm(heel) + 0.4 * G.norm(grit) + 0.5 * G.norm(toe)
        G.save_ogg(x, OUT_SND / f"faceling_step{i}.ogg", peak=0.6 * 10 ** (rng.uniform(-1.5, 0) / 20),
                   fade_in=0.001, fade_out=0.04)


def snd_faceling_hurt() -> None:
    """2 takes: a muffled, choked grunt that can't get out ("Mm-!"), and a puff of breath."""
    for i, seed in enumerate((39941, 39942), 1):
        rng = G.rng_for(seed)
        dur = 0.45
        n, t = _t(dur)
        f0 = _env(t, [(0, 150 + 20 * i), (0.06, 230 + 30 * i), (0.25, 170), (dur, 120)])
        v = G.voice(f0, [(300, 120, 1.0), (700, 200, 0.4)], rng, nh=24)
        v = G.fft_filter(v, lambda f: G.lp_gain(f, 600, 3))
        puff = G.svf(rng.normal(size=n), 1100, 1.5) * _env(t, [(0, 0), (0.02, 1), (0.12, 0.2), (dur, 0)])
        e = _env(t, [(0, 0), (0.02, 1.0), (0.18, 0.6), (0.35, 0.0), (dur, 0)])
        x = G.dist(G.norm(v), 2.0) * e + 0.4 * G.norm(puff)
        G.save_ogg(x, OUT_SND / f"faceling_hurt{i}.ogg", peak=0.75, fade_out=0.05)


def snd_faceling_death() -> None:
    """~3.2 s: the death rattle. A long gurgling exhalation through a closed throat, bubbling
    unevenly, two shallow catches of breath, and nothing."""
    rng = G.rng_for(39951)
    dur = 3.2
    n, t = _t(dur)
    f0 = _env(t, [(0, 95), (1.2, 82), (2.4, 62), (dur, 55)]) * (1 + G.smooth_jitter(rng, n, 5, 0.1))
    v = G.voice(f0, [(350, 140, 1.0), (750, 220, 0.5)], rng, nh=40)
    v = G.fft_filter(v, lambda f: G.lp_gain(f, 900, 3))
    bubbles = _fry(rng, n, 13.0, 0.8)
    e = _env(t, [(0, 0), (0.15, 1.0), (1.4, 0.8), (2.1, 0.35), (2.5, 0.0), (dur, 0)])
    rattle = G.clicks(rng, np.sort(rng.uniform(0.2, 2.4, 40)), dur, 250, 800, 0.006, 0.016)
    exhale = G.svf(rng.normal(size=n), 800 - 300 * G.smoothstep(0, 2.4, t), 1.6) * e
    catches = G.svf(rng.normal(size=n), 1400, 3.0) * (_env(t, [(0, 0), (2.55, 0), (2.62, 0.5), (2.72, 0), (2.88, 0), (2.94, 0.3), (3.02, 0), (dur, 0)]))
    x = G.norm(v) * (0.3 + 0.7 * bubbles) * e + 0.3 * G.norm(rattle) + 0.35 * G.norm(exhale) + 0.3 * G.norm(catches)
    G.save_ogg(x, OUT_SND / "faceling_death.ogg", peak=0.8, fade_in=0.02, fade_out=0.15)


TEXTURES = [tex_hound, tex_faceling]
SOUNDS = [snd_hound_growl, snd_hound_snarl, snd_hound_breath, snd_hound_step, snd_hound_splash,
          snd_hound_bite, snd_hound_whine, snd_hound_hurt, snd_hound_death,
          snd_faceling_wheeze, snd_faceling_rasp, snd_faceling_step, snd_faceling_hurt,
          snd_faceling_death]
