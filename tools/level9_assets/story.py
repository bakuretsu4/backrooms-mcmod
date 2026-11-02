"""Level 9 (the Suburbs) story items: textures, models, blockstates and the answering machine tapes.

What it makes (all procedural; the voices are Windows SAPI text-to-speech):
  * missing-person posters: block textures (one per person), the close-up poster sheet and the
    photo atlas for the reading screen (client/level9/Level9PaperScreen);
  * the Neighborhood Watch flyer: block texture (pinned), item texture and the close-up sheet;
  * four children's crayon drawings: block textures and big close-up copies;
  * the answering machine: textures, the blinking red light (two animated speeds) and its model;
  * every model and blockstate file for missing_poster, watch_flyer_pinned, kids_drawing and
    answering_machine, plus their item models;
  * eight answering-machine messages (sounds/level9/story/machine_message_N.ogg): ordinary
    voicemails that go wrong, spoken by the installed SAPI voices (pitched for a child, an older
    neighbour, a mother), then run through a cheap microcassette: band-limited, wow and flutter,
    hiss, a beep at the start and the end. They are kept quiet (peak about -6 dB) on purpose:
    nothing on Level 9 may be loud.

Run:  python tools/level9_assets/story.py [textures] [models] [sounds]   (no argument = all)
At the end it prints each message's length in ticks: copy them into
AnsweringMachineBlock.LENGTH_TICKS if a message changed.
Every generator uses its own fixed random seed (9300-9399), so re-running gives the same files
(the SAPI voices are deterministic too).
"""
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
import gen_assets as G  # noqa: E402  (the mod's shared asset helpers)

ROOT = G.ROOT
TEX = G.TEX
SR = G.SR
MODELS = ROOT / "models"
STATES = ROOT / "blockstates"
SND = G.SND / "level9" / "story"
FONTS = TOOLS / "fonts"

PEOPLE = 8          # MissingPosterBlock.PEOPLE
DRAWINGS = 4        # KidsDrawingBlock.DRAWINGS
MESSAGES = 8        # AnsweringMachineBlock.MESSAGES
FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))
# PaperMount: name and how far behind the block's back edge the surface lies (pixels).
MOUNTS = (("wall", 0.0), ("lamp", 5.5), ("post", 6.0))
# Paper rectangles {x0, y0, x1, y1} per mount: the same numbers as the Java blocks' click shapes.
POSTER_SHEETS = ((3, 1, 13, 15), (4, 0, 12, 11), (5, 3, 11, 12))
FLYER_SHEETS = ((4, 2, 12, 14), (4.5, 3, 11.5, 13), (5, 3, 11, 12))

INK = (28, 26, 24)
WATCH_RED = (150, 30, 28)


# =============================================================================
# Small helpers
# =============================================================================

def anton(px: int):
    return ImageFont.truetype(str(FONTS / "Anton-Regular.ttf"), px)


def arial(px: int):
    return G.font(px)


def arial_regular(px: int):
    try:
        return ImageFont.truetype("arial.ttf", px)
    except OSError:
        return G.font(px)


def crayon_font(px: int):
    """A rounded, childish face for the crayon words (Comic Sans Bold on Windows)."""
    for name in ("comicbd.ttf", "comic.ttf"):
        try:
            return ImageFont.truetype(name, px)
        except OSError:
            pass
    return G.font(px)


def write_json(path: Path, obj) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8", newline="\n")


def smooth_noise(rng, h: int, w: int, scale: float) -> np.ndarray:
    """Soft noise in 0..1 with blobs about `scale` pixels across."""
    gh, gw = max(2, int(h / scale) + 2), max(2, int(w / scale) + 2)
    g = Image.fromarray((rng.random((gh, gw)) * 255).astype(np.uint8), "L")
    return np.asarray(g.resize((w, h), Image.BICUBIC), float) / 255.0


def paper(rng, w: int, h: int, base=(234, 229, 214), stains: float = 1.0) -> np.ndarray:
    """Cheap office paper left out in the damp: fibres, soft creases, a few water stains."""
    img = np.zeros((h, w, 3)) + np.array(base, float)
    fibre = rng.normal(0, 1, (h, w))
    fibre = np.asarray(Image.fromarray(((fibre * 20) + 128).clip(0, 255).astype(np.uint8)).filter(
        ImageFilter.GaussianBlur(0.6)), float) - 128
    img += fibre[..., None] * 0.35
    crease = smooth_noise(rng, h, w, max(w, h) / 3) - 0.5
    img += crease[..., None] * 14
    if stains > 0:
        blot = smooth_noise(rng, h, w, max(w, h) / 6)
        mask = np.clip((blot - 0.72) * 6, 0, 1) * stains
        img = img * (1 - mask[..., None] * 0.18) + np.array([150, 125, 80]) * (mask[..., None] * 0.18)
        # a darker tide line round each stain
        edge = np.clip(1 - np.abs(blot - 0.72) * 40, 0, 1) * stains
        img -= edge[..., None] * np.array([18, 22, 30])
    # dirt creeping in from the edges
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.minimum.reduce([xx, yy, w - 1 - xx, h - 1 - yy]) / max(4.0, min(w, h) * 0.08)
    img *= (0.86 + 0.14 * np.clip(d, 0, 1))[..., None]
    return img


def paste(dst: np.ndarray, src: np.ndarray, x: int, y: int, alpha: np.ndarray | None = None) -> None:
    h, w = src.shape[:2]
    region = dst[y:y + h, x:x + w, :3]
    if alpha is None:
        region[:] = src[..., :3]
    else:
        region[:] = region * (1 - alpha[..., None]) + src[..., :3] * alpha[..., None]


def text_mask(size, xy, text, fnt, anchor="la", spacing=0) -> np.ndarray:
    im = Image.new("L", size, 0)
    ImageDraw.Draw(im).text(xy, text, font=fnt, fill=255, anchor=anchor, spacing=spacing)
    return np.asarray(im, float) / 255.0


def ink(img: np.ndarray, mask: np.ndarray, colour, strength: float = 1.0) -> None:
    """Print `mask` onto img in `colour` (toner sits in the paper: multiply, not cover)."""
    a = np.clip(mask * strength, 0, 1)[..., None]
    img[..., :3] = img[..., :3] * (1 - a) + np.array(colour, float) * a


def to_rgba(img: np.ndarray, alpha: np.ndarray | None = None) -> np.ndarray:
    a = np.full(img.shape[:2], 255.0) if alpha is None else alpha * 255.0
    return np.concatenate([img[..., :3], a[..., None]], axis=2)


def shrink(img: np.ndarray, w: int, h: int) -> np.ndarray:
    """High-quality downscale of an RGB(A) float image."""
    mode = "RGBA" if img.shape[2] == 4 else "RGB"
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), mode)
    return np.asarray(im.resize((w, h), Image.LANCZOS), float)


def staples(img: np.ndarray, points, rng) -> None:
    """Little grey staples (and a rust dot) at the given points."""
    d = Image.fromarray(np.clip(img[..., :3], 0, 255).astype(np.uint8), "RGB")
    dr = ImageDraw.Draw(d)
    for (x, y) in points:
        ang = rng.uniform(-0.4, 0.4)
        dx, dy = np.cos(ang) * 5, np.sin(ang) * 5
        dr.line([(x - dx, y - dy), (x + dx, y + dy)], fill=(120, 122, 126), width=2)
        dr.point((x + dx, y + dy + 1), fill=(120, 70, 40))
    img[..., :3] = np.asarray(d, float)


# =============================================================================
# Photos (missing-person posters)
# =============================================================================

# Who is on each poster (see poster.backrooms.l9.N in the language file).
#   kind, skin grey, hair grey, hair style, extras
PERSONS = (
    ("man", 150, 70, "short", ()),               # 0 Daniel Howard, 34
    ("woman", 160, 60, "long", ("glasses",)),     # 1 Susan Park, 41
    ("child", 170, 55, "short", ("smile",)),      # 2 Tommy Reyes, 9
    ("old", 150, 205, "bun", ("smile",)),         # 3 Margaret Ellis, 72
    ("family", 160, 70, "", ()),                  # 4 the Kowalski family
    ("man", 120, 30, "short", ("hood",)),         # 5 James Okafor, 19
    ("unknown", 175, 80, "long", ()),             # 6 the woman nobody knows
    ("man", 145, 110, "cap", ()),                 # 7 Alan Whitfield, 50
)


def _head(dr, cx, cy, r, skin, hair, style, extras, s=1.0):
    """One head and shoulders, drawn in grey (it is a photocopy)."""
    shirt = 90 if "hood" not in extras else 40
    dr.ellipse([cx - r * 2.1, cy + r * 1.35, cx + r * 2.1, cy + r * 4.2], fill=shirt)         # shoulders
    dr.rectangle([cx - r * 0.38, cy + r * 0.7, cx + r * 0.38, cy + r * 1.6], fill=skin - 20)  # neck
    if "hood" in extras:
        dr.ellipse([cx - r * 1.35, cy - r * 1.35, cx + r * 1.35, cy + r * 1.5], fill=35)
    if style == "long":
        dr.rounded_rectangle([cx - r * 1.15, cy - r * 0.9, cx + r * 1.15, cy + r * 1.7], r * 0.6, fill=hair)
    dr.ellipse([cx - r * 0.85, cy - r * 1.05, cx + r * 0.85, cy + r * 1.05], fill=skin)      # face
    if style in ("short", "long"):
        dr.chord([cx - r * 0.95, cy - r * 1.2, cx + r * 0.95, cy + r * 0.5], 180, 360, fill=hair)
    elif style == "bun":
        dr.chord([cx - r * 0.95, cy - r * 1.2, cx + r * 0.95, cy + r * 0.4], 180, 360, fill=hair)
        dr.ellipse([cx - r * 0.45, cy - r * 1.6, cx + r * 0.45, cy - r * 0.8], fill=hair)
    elif style == "cap":
        dr.chord([cx - r * 0.95, cy - r * 1.3, cx + r * 0.95, cy + r * 0.3], 180, 360, fill=60)
        dr.rectangle([cx - r * 1.3, cy - r * 0.45, cx + r * 0.5, cy - r * 0.25], fill=50)
    # eyes, brows, nose shadow, mouth
    ey = cy - r * 0.08
    for sx in (-1, 1):
        ex = cx + sx * r * 0.36
        dr.ellipse([ex - r * 0.12, ey - r * 0.07, ex + r * 0.12, ey + r * 0.07], fill=35)
        dr.line([ex - r * 0.18, ey - r * 0.25, ex + r * 0.16, ey - r * 0.27], fill=max(20, hair - 20), width=max(1, int(r * 0.07)))
        if "glasses" in extras:
            dr.ellipse([ex - r * 0.26, ey - r * 0.2, ex + r * 0.26, ey + r * 0.2], outline=30, width=max(1, int(r * 0.05)))
    dr.line([cx, cy, cx - r * 0.08, cy + r * 0.3], fill=skin - 40, width=max(1, int(r * 0.06)))
    if "smile" in extras:
        dr.arc([cx - r * 0.3, cy + r * 0.25, cx + r * 0.3, cy + r * 0.6], 20, 160, fill=50, width=max(1, int(r * 0.07)))
    else:
        dr.line([cx - r * 0.24, cy + r * 0.5, cx + r * 0.24, cy + r * 0.5], fill=60, width=max(1, int(r * 0.06)))


def portrait(i: int) -> np.ndarray:
    """A grey photocopied snapshot, 64 x 64 (drawn at 256 and shrunk)."""
    rng = G.rng_for(9310 + i)
    kind, skin, hair, style, extras = PERSONS[i]
    n = 256
    bg = int(rng.uniform(150, 205))
    im = Image.new("L", (n, n), bg)
    dr = ImageDraw.Draw(im)
    # a hint of the place the photo was taken: a door frame, a window, a fence
    if rng.random() < 0.6:
        x = int(rng.uniform(10, 60))
        dr.rectangle([x, 0, x + 14, n], fill=bg - 40)
    if kind == "family":
        for k, (cx, r, sk, hr, st) in enumerate(((62, 30, 165, 60, "short"), (124, 28, 170, 90, "long"),
                                                  (178, 20, 175, 70, "short"), (220, 16, 172, 60, "short"))):
            _head(dr, cx, 108 + (30 - r) * 1.4, r, sk, hr, st, ("smile",))
    else:
        r = 46 if kind != "child" else 40
        _head(dr, 128, 104 if kind != "child" else 118, r, skin, hair, style, extras)
    a = np.asarray(im, float)
    if kind == "unknown":
        # a doorbell camera still: washed out, smeared sideways, lines across it
        a = np.asarray(Image.fromarray(a.astype(np.uint8)).filter(ImageFilter.BoxBlur(5)), float)
        a = 255 - (255 - a) * 0.45
        a[::6, :] -= 18
    # photocopy: hard contrast, toner speckle, a little blur
    a = (a - 128) * 1.35 + 128 + rng.normal(0, 10, a.shape)
    a = np.asarray(Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2)), float)
    small = np.asarray(Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)).resize((64, 64), Image.LANCZOS), float)
    # warm grey toner
    rgb = np.stack([small * 0.98 + 4, small * 0.96 + 3, small * 0.92], axis=2)
    return rgb


def photo_grain() -> np.ndarray:
    """RGBA 64 x 64 overlay: photocopier speckle and a streak, mostly see-through."""
    rng = G.rng_for(9330)
    a = np.zeros((64, 64, 4))
    speck = rng.random((64, 64))
    alpha = np.where(speck > 0.93, rng.uniform(40, 110, (64, 64)), 0.0)
    streak = np.zeros((64, 64))
    streak[:, int(rng.uniform(8, 56))] = 40
    alpha = np.clip(alpha + streak + smooth_noise(rng, 64, 64, 20) * 30, 0, 255)
    a[..., :3] = 30
    a[..., 3] = alpha
    return a


# =============================================================================
# Missing-person posters
# =============================================================================

# The block texture's name line (the close-up uses the language file).
POSTER_NAMES = ("DANIEL HOWARD", "SUSAN PARK", "TOMMY REYES", "MARGARET ELLIS", "THE KOWALSKIS",
                "JAMES OKAFOR", "DO YOU KNOW HER?", "ALAN WHITFIELD")


def poster_block(i: int, photo: np.ndarray) -> np.ndarray:
    """128 x 128 block texture of poster i. Drawn 92 x 128 (the sheet's shape) at 4x, then
    stretched square: the model squeezes it back onto a 10 x 14 sheet."""
    rng = G.rng_for(9340 + i)
    k = 4
    w, h = 92 * k, 128 * k
    img = paper(rng, w, h, base=(232, 228, 214), stains=0.7 + 0.3 * rng.random())
    red_header = i in (2, 4)
    ink(img, text_mask((w, h), (w // 2, 7 * k), "MISSING", anton(22 * k), "mt"), WATCH_RED if red_header else INK, 0.95)
    # photo with a thin black frame
    pw = 56 * k
    ph = shrink(np.concatenate([photo, np.full((64, 64, 1), 255.0)], 2), pw, pw)[..., :3]
    px, py = (w - pw) // 2, 40 * k
    img[py - k:py + pw + k, px - k:px + pw + k] = 30
    paste(img, ph, px, py)
    ink(img, text_mask((w, h), (w // 2, 101 * k), POSTER_NAMES[i], arial(7 * k), "mt"), INK, 0.95)
    # body text: real words, too small to read from the street
    small = arial_regular(4 * k)
    for li in range(3):
        y = (111 + li * 5) * k
        words = "LAST SEEN ON MAPLE CT  PLEASE CALL 555-01" + str(40 + i)
        ink(img, text_mask((w, h), (6 * k, y), words[li * 7:li * 7 + 30], small), INK, 0.7)
    # rain has run the toner a little
    run = smooth_noise(rng, h, w, 30 * k) > 0.8
    img[run] = img[run] * 0.97 + 6
    staples(img, [(8 * k, 4 * k), (w - 8 * k, 4 * k)], rng)
    out = shrink(img, 128, 128)
    return out


def poster_gui(photos) -> None:
    """The close-up sheet (256 x 256, transparent round it): the screen draws photo and words."""
    rng = G.rng_for(9350)
    img = np.zeros((256, 256, 4))
    x0, y0, x1, y1 = 34, 4, 222, 252
    sheet = paper(rng, x1 - x0, y1 - y0, base=(236, 232, 219), stains=0.6)
    w, h = x1 - x0, y1 - y0
    ink(sheet, text_mask((w, h), (w // 2, 8), "MISSING", anton(38), "mt"), INK, 0.95)
    # photo frame (the photo itself is drawn by the screen at 92,52 72x72)
    fx, fy = 92 - x0, 52 - y0
    sheet[fy - 2:fy + 74, fx - 2:fx + 74] = 30
    sheet[fy:fy + 72, fx:fx + 72] = 200
    staples(sheet, [(12, 6), (w - 12, 6), (12, h - 6), (w - 12, h - 6)], rng)
    img[y0:y1, x0:x1, :3] = sheet
    img[y0:y1, x0:x1, 3] = 255
    # a torn-off corner
    for yy in range(8):
        img[y1 - 1 - yy, x1 - 8 + yy:x1, 3] = 0
    G.save_png(img, TEX / "gui" / "l9_missing_poster.png")
    atlas = np.zeros((192, 256, 4))
    for i, p in enumerate(photos):
        atlas[(i // 4) * 64:(i // 4) * 64 + 64, (i % 4) * 64:(i % 4) * 64 + 64, :3] = p
        atlas[(i // 4) * 64:(i // 4) * 64 + 64, (i % 4) * 64:(i % 4) * 64 + 64, 3] = 255
    atlas[128:192, 0:64] = photo_grain()
    G.save_png(atlas, TEX / "gui" / "l9_poster_photos.png")


# =============================================================================
# Neighborhood Watch flyer
# =============================================================================

def watch_eye(img: np.ndarray, cx: float, cy: float, r: float) -> None:
    """The Watch's logo: a wide-open eye with a red iris (as on the notice boards)."""
    h, w = img.shape[:2]
    m = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(m)
    d.ellipse([cx - r, cy - r * 0.55, cx + r, cy + r * 0.55], fill=255)
    white = np.asarray(m, float) / 255
    img[..., :3] = img[..., :3] * (1 - white[..., None]) + np.array([240, 238, 232]) * white[..., None]
    m2 = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m2).ellipse([cx - r * 0.45, cy - r * 0.45, cx + r * 0.45, cy + r * 0.45], fill=255)
    ink(img, (np.asarray(m2, float) / 255) * white, (178, 28, 30))
    m3 = Image.new("L", (w, h), 0)
    ImageDraw.Draw(m3).ellipse([cx - r * 0.2, cy - r * 0.2, cx + r * 0.2, cy + r * 0.2], fill=255)
    ink(img, np.asarray(m3, float) / 255, (15, 12, 12))


def flyer_layout(rng, w: int, h: int, k: float, body_lines: bool) -> np.ndarray:
    """The flyer: black header band with the eye and NEIGHBORHOOD WATCH, a footer, and (for
    the block texture) grey lines of small print where the screen would put the words."""
    img = paper(rng, w, h, base=(238, 236, 228), stains=0.5)
    band = int(48 * k)
    img[int(4 * k):band, int(4 * k):w - int(4 * k)] = (24, 22, 22)
    watch_eye(img, int(30 * k), (int(4 * k) + band) / 2, 18 * k)
    f = arial(int(13 * k))
    ink(img, text_mask((w, h), (int(52 * k), int(13 * k)), "NEIGHBORHOOD", f), (240, 238, 232))
    ink(img, text_mask((w, h), (int(52 * k), int(28 * k)), "WATCH", arial(int(16 * k))), (240, 238, 232))
    foot = arial(int(7 * k))
    ink(img, text_mask((w, h), (w // 2, h - int(16 * k)), "THIS IS YOUR NEIGHBORHOOD. WATCH IT.", foot, "mt"), WATCH_RED, 0.9)
    if body_lines:
        ink(img, text_mask((w, h), (w // 2, int(58 * k)), "RULE", arial(int(14 * k)), "mt"), WATCH_RED, 0.95)
        for li in range(9):
            y = int((80 + li * 12) * k)
            x1 = w - int((14 + rng.uniform(0, 40) * (li == 8)) * k)
            img[y:y + int(5 * k), int(14 * k):x1] = img[y:y + int(5 * k), int(14 * k):x1] * 0.45 + 40
    # photocopier streak down the sheet
    sx = int(rng.uniform(0.2, 0.8) * w)
    img[:, sx:sx + max(1, int(k))] *= 0.93
    return img


def flyer_textures() -> None:
    rng = G.rng_for(9360)
    k = 4
    w, h = 96 * k, 136 * k
    img = flyer_layout(rng, w, h, k * 96 / 184, True)
    staples(img, [(w // 2, 5 * k)], rng)
    G.save_png(shrink(img, 128, 128), TEX / "block" / "watch_flyer_paper.png")
    # the close-up sheet (the screen prints the title at y 70 and the rules from y 88)
    rng = G.rng_for(9361)
    gui = np.zeros((256, 256, 4))
    x0, y0, x1, y1 = 36, 4, 220, 252
    sheet = flyer_layout(rng, x1 - x0, y1 - y0, 1.0, False)
    staples(sheet, [((x1 - x0) // 2, 3)], rng)
    gui[y0:y1, x0:x1, :3] = sheet
    gui[y0:y1, x0:x1, 3] = 255
    G.save_png(gui, TEX / "gui" / "l9_watch_flyer.png")
    # 16 x 16 item: the flyer folded in three, the eye showing on the top panel
    p = np.zeros((16, 16, 4))
    paper_c = np.array([226, 223, 212, 255.0])
    p[2:14, 3:13] = paper_c
    p[2:6, 3:13, :3] = (34, 32, 32)                 # header band
    p[3:5, 4:6, :3] = (236, 234, 228)               # eye white
    p[3:5, 5, :3] = (170, 30, 30)                   # iris
    p[6, 3:13, :3] = (196, 193, 182)                # fold
    p[10, 3:13, :3] = (196, 193, 182)               # fold
    for y in (8, 12):
        p[y, 4:12, :3] = (140, 138, 132)            # print
    p[13, 3:13, :3] = (204, 200, 190)
    p[2:14, 12, :3] = p[2:14, 12, :3] * 0.85        # shade on the right edge
    G.save_png(p, TEX / "item" / "watch_flyer.png")


def paper_back() -> None:
    """The plain back and cut edges of a pinned sheet."""
    rng = G.rng_for(9362)
    G.save_png(shrink(paper(rng, 128, 128, base=(214, 210, 198), stains=0.8), 128, 128), TEX / "block" / "l9_paper_back.png")


# =============================================================================
# Children's drawings (crayon)
# =============================================================================

class Crayon:
    """A sheet of paper drawn on with wax crayons, at 2x (512) for the 256 close-up.

    Wax skips where the paper's tooth is high, so every stroke is multiplied by a tooth mask;
    colouring-in is a scribble of zig-zag strokes that goes a little outside the lines.
    """

    def __init__(self, seed: int, w: int = 480, h: int = 380):
        self.rng = G.rng_for(seed)
        self.w, self.h = w, h
        self.img = paper(self.rng, w, h, base=(240, 238, 230), stains=0.3)
        self.tooth = np.clip(1.15 - smooth_noise(self.rng, h, w, 2.2) * 0.9 - self.rng.random((h, w)) * 0.25, 0, 1)

    def _apply(self, mask: Image.Image, colour, opacity: float = 0.92) -> None:
        a = np.asarray(mask, float) / 255.0 * self.tooth * opacity
        ink(self.img, a, colour)

    def line(self, pts, colour, width: int = 5, jitter: float = 1.2) -> None:
        m = Image.new("L", (self.w, self.h), 0)
        d = ImageDraw.Draw(m)
        pts = [(x + self.rng.normal(0, jitter), y + self.rng.normal(0, jitter)) for x, y in pts]
        d.line(pts, fill=255, width=width, joint="curve")
        self._apply(m, colour)

    def scribble(self, shape_fn, colour, spacing: float = 7, angle: float = 0.5, width: int = 5,
                 bleed: int = 4, opacity: float = 0.85) -> None:
        """Colour in the area that shape_fn(draw) paints white, with zig-zag strokes."""
        area = Image.new("L", (self.w, self.h), 0)
        dd = ImageDraw.Draw(area)
        # every shape the caller draws is filled white (the callers just name the shape)
        shape_fn(type("Filled", (), {"__getattr__": lambda _s, name: (
            lambda *a, **kw: getattr(dd, name)(*a, **{**kw, "fill": 255}))})())
        area = area.filter(ImageFilter.MaxFilter(2 * bleed + 1)) if bleed else area
        ys, xs = np.nonzero(np.asarray(area))
        if len(xs) == 0:
            return
        strokes = Image.new("L", (self.w, self.h), 0)
        d = ImageDraw.Draw(strokes)
        ca, sa = np.cos(angle), np.sin(angle)
        cx, cy = xs.mean(), ys.mean()
        span = int(np.hypot(xs.max() - xs.min(), ys.max() - ys.min()) / 2 + 10)
        pts = []
        for i, t in enumerate(np.arange(-span, span, spacing)):
            a = -span if i % 2 == 0 else span
            b = span if i % 2 == 0 else -span
            for s in (a, b):
                x = cx + ca * s - sa * t + self.rng.normal(0, 1.5)
                y = cy + sa * s + ca * t + self.rng.normal(0, 1.5)
                pts.append((x, y))
        d.line(pts, fill=255, width=width, joint="curve")
        both = np.minimum(np.asarray(strokes, float), np.asarray(area, float))
        self._apply(Image.fromarray(both.astype(np.uint8)), colour, opacity)

    def words(self, xy, text, colour, size: int = 34, wobble: float = 7.0) -> None:
        """Big crayon capitals, each letter a little tilted and off the line."""
        f = crayon_font(size)
        x, y = xy
        for ch in text:
            if ch == " ":
                x += size * 0.45
                continue
            tile = Image.new("L", (size * 2, size * 2), 0)
            ImageDraw.Draw(tile).text((size // 2, size // 3), ch, font=f, fill=255)
            tile = tile.rotate(self.rng.uniform(-wobble, wobble), resample=Image.BICUBIC)
            m = Image.new("L", (self.w, self.h), 0)
            m.paste(tile, (int(x - size // 2), int(y - size // 3 + self.rng.normal(0, 2.5))))
            self._apply(m, colour, 0.95)
            x += f.getlength(ch) * 1.08

    def house(self, x, ground, w, h, wall, roof, lit=True, door=True) -> None:
        self.scribble(lambda d: d.rectangle([x, ground - h, x + w, ground]), wall, spacing=8)
        self.line([(x, ground), (x, ground - h), (x + w, ground - h), (x + w, ground), (x, ground)], (60, 40, 30), 4)
        self.scribble(lambda d: d.polygon([(x - 8, ground - h), (x + w / 2, ground - h - h * 0.7), (x + w + 8, ground - h)]),
                      roof, spacing=7, angle=1.2)
        win = (250, 215, 60) if lit else (40, 40, 60)
        for wx in (x + w * 0.18, x + w * 0.62):
            self.scribble(lambda d, wx=wx: d.rectangle([wx, ground - h * 0.75, wx + w * 0.2, ground - h * 0.45]), win,
                          spacing=5, width=4, bleed=1)
        if door:
            self.scribble(lambda d: d.rectangle([x + w * 0.42, ground - h * 0.42, x + w * 0.58, ground]), (120, 70, 40),
                          spacing=5, width=4, bleed=1)

    def stick(self, x, foot, height, colour, face=True, arms_up=False) -> None:
        r = height * 0.16
        hy = foot - height + r
        self.line([(x + r * np.cos(t), hy + r * np.sin(t)) for t in np.linspace(0, 2 * np.pi, 18)], colour, 4)
        neck = hy + r
        hip = foot - height * 0.4
        self.line([(x, neck), (x, hip)], colour, 4)
        self.line([(x - height * 0.18, foot), (x, hip), (x + height * 0.18, foot)], colour, 4)
        ay = neck + height * 0.12
        if arms_up:
            self.line([(x - height * 0.25, ay - height * 0.2), (x, ay), (x + height * 0.25, ay - height * 0.2)], colour, 4)
        else:
            self.line([(x - height * 0.25, ay + height * 0.12), (x, ay), (x + height * 0.25, ay + height * 0.12)], colour, 4)
        if face:
            self.line([(x - r * 0.4, hy - r * 0.15), (x - r * 0.3, hy - r * 0.1)], (30, 30, 30), 4, 0.3)
            self.line([(x + r * 0.3, hy - r * 0.15), (x + r * 0.4, hy - r * 0.1)], (30, 30, 30), 4, 0.3)
            self.line([(x - r * 0.4, hy + r * 0.35), (x, hy + r * 0.5), (x + r * 0.4, hy + r * 0.35)], (200, 40, 40), 3, 0.3)

    def finish(self, name: str) -> None:
        """Save the close-up (paper on a transparent 256 sheet) and the 128 block texture."""
        gui = np.zeros((512, 512, 4))
        ox, oy = (512 - self.w) // 2, (512 - self.h) // 2
        gui[oy:oy + self.h, ox:ox + self.w, :3] = self.img
        gui[oy:oy + self.h, ox:ox + self.w, 3] = 255
        # two bits of sticky tape at the top
        for tx in (ox + 40, ox + self.w - 100):
            gui[oy - 10:oy + 18, tx:tx + 60, :3] = gui[oy - 10:oy + 18, tx:tx + 60, :3] * 0.4 + np.array([235, 230, 200]) * 0.6
            gui[oy - 10:oy + 18, tx:tx + 60, 3] = np.maximum(gui[oy - 10:oy + 18, tx:tx + 60, 3], 200)
        G.save_png(shrink(gui, 256, 256), TEX / "gui" / f"l9_kids_drawing_{name}.png")
        G.save_png(shrink(np.concatenate([self.img, np.full((self.h, self.w, 1), 255.0)], 2), 128, 128),
                   TEX / "block" / f"kids_drawing_{name}.png")


BLACK = (30, 28, 34)
YELLOW = (250, 210, 50)
NIGHT = (40, 50, 110)
GRASS = (60, 150, 60)


def drawing_watcher() -> None:
    """0: the tall man with the light, over our houses."""
    c = Crayon(9370)
    ground = 300
    c.scribble(lambda d: d.rectangle([0, 0, c.w, 150]), NIGHT, spacing=9, angle=0.3, bleed=0, opacity=0.7)
    c.scribble(lambda d: d.rectangle([0, ground, c.w, c.h]), GRASS, spacing=9, angle=0.2, bleed=0)
    # the light cone first, so the houses sit in it
    c.scribble(lambda d: d.polygon([(318, 70), (180, ground), (330, ground)]), YELLOW, spacing=6, angle=1.4, opacity=0.6)
    c.house(30, ground, 90, 70, (200, 60, 50), (90, 50, 40))
    c.house(150, ground, 100, 80, (80, 120, 200), (60, 60, 60))
    c.house(380, ground, 80, 65, (230, 200, 80), (110, 60, 40), lit=False)
    # him: very tall, very thin, the lamp where his face should be
    c.line([(300, ground), (312, 160), (324, ground)], BLACK, 6)
    c.line([(312, 160), (316, 80)], BLACK, 8)
    c.line([(316, 100), (270, 190)], BLACK, 5)
    c.line([(316, 100), (360, 190)], BLACK, 5)
    c.scribble(lambda d: d.ellipse([302, 52, 334, 84]), YELLOW, spacing=4, bleed=1)
    c.line([(318 + 16 * np.cos(t), 68 + 16 * np.sin(t)) for t in np.linspace(0, 2 * np.pi, 16)], BLACK, 3)
    c.stick(120, ground + 40, 52, (60, 60, 200))   # me, in the garden
    c.words((24, 318), "THE TALL MAN WACHES", BLACK, 30)
    c.words((60, 350), "OUR STREET", BLACK, 30)
    c.finish("0")


def drawing_family() -> None:
    """1: my family (one of us has no face)."""
    c = Crayon(9371)
    ground = 300
    c.scribble(lambda d: d.rectangle([0, ground, c.w, c.h]), GRASS, spacing=9, angle=0.2, bleed=0)
    c.scribble(lambda d: d.ellipse([390, 20, 450, 80]), (240, 240, 180), spacing=5)   # the moon
    c.words((30, 20), "MY FAMILY", (200, 40, 40), 40)
    c.stick(70, ground, 150, (40, 80, 200))
    c.words((50, 310), "DAD", (40, 80, 200), 24)
    c.stick(160, ground, 140, (200, 60, 140))
    c.words((135, 310), "MOM", (200, 60, 140), 24)
    c.stick(240, ground, 95, (60, 160, 60), arms_up=True)
    c.words((225, 310), "ME", (60, 160, 60), 24)
    # the dog
    c.scribble(lambda d: d.ellipse([275, 255, 330, 285]), (140, 90, 50), spacing=5, bleed=1)
    c.line([(285, 285), (285, 300)], (140, 90, 50), 4)
    c.line([(320, 285), (320, 300)], (140, 90, 50), 4)
    c.scribble(lambda d: d.ellipse([318, 240, 342, 264]), (140, 90, 50), spacing=4, bleed=1)
    # and the one who is always there in the pictures: tall, grey, no face
    c.stick(410, ground, 210, (120, 120, 125), face=False)
    c.words((395, 310), "?", BLACK, 30)
    c.finish("1")


def drawing_lamps() -> None:
    """2: the streetlights on our street, one coloured in black."""
    c = Crayon(9372)
    ground = 290
    c.scribble(lambda d: d.rectangle([0, 0, c.w, ground]), NIGHT, spacing=10, angle=0.3, bleed=0, opacity=0.55)
    c.scribble(lambda d: d.rectangle([0, ground, c.w, ground + 22]), (110, 110, 115), spacing=7, angle=0.1, bleed=0)
    for i, x in enumerate((50, 140, 230, 320, 410)):
        black = i == 2
        if not black:
            c.scribble(lambda d, x=x: d.polygon([(x + 22, 92), (x - 12, ground), (x + 58, ground)]), YELLOW,
                       spacing=7, angle=1.5, opacity=0.55)
        else:
            c.scribble(lambda d, x=x: d.polygon([(x + 22, 92), (x - 12, ground), (x + 58, ground)]), BLACK,
                       spacing=5, angle=1.5, opacity=0.85)
        c.line([(x, ground), (x, 80), (x + 24, 80)], (70, 70, 70) if not black else BLACK, 6)
        c.scribble(lambda d, x=x: d.ellipse([x + 12, 74, x + 34, 96]), YELLOW if not black else BLACK, spacing=4, bleed=1)
    c.words((30, 20), "THE STREET LITES", BLACK, 34)
    c.words((40, 320), "DONT GO BY THE", (200, 40, 40), 26)
    c.words((40, 350), "BLACK ONE", (200, 40, 40), 26)
    c.finish("2")


def drawing_porch() -> None:
    """3: our house at night, and the porch light that stays on."""
    c = Crayon(9373)
    ground = 290
    c.scribble(lambda d: d.rectangle([0, 0, c.w, ground]), NIGHT, spacing=9, angle=0.3, bleed=0, opacity=0.75)
    c.scribble(lambda d: d.rectangle([0, ground, c.w, c.h]), GRASS, spacing=9, angle=0.2, bleed=0)
    c.house(140, ground, 200, 140, (230, 230, 230), (70, 60, 60), lit=False)
    # the porch light and its glow
    c.scribble(lambda d: d.ellipse([290, 175, 340, 225]), YELLOW, spacing=4, opacity=0.6)
    c.scribble(lambda d: d.ellipse([308, 192, 322, 208]), (255, 170, 30), spacing=3, bleed=1)
    # three of them waiting on the grass, just outside the light
    for x in (40, 75, 420):
        c.stick(x, ground + 30, 120, BLACK, face=False)
    c.words((20, 300), "OUR HOUSE. MOM SAYS", BLACK, 26)
    c.words((20, 334), "KEEP THE PORCH LITE ON", BLACK, 26)
    c.finish("3")


# =============================================================================
# Answering machine
# =============================================================================

def machine_textures() -> None:
    rng = G.rng_for(9380)
    plastic = np.array([42, 41, 44], float)

    def base() -> np.ndarray:
        img = np.zeros((128, 128, 3)) + plastic
        img += (smooth_noise(rng, 128, 128, 10)[..., None] - 0.5) * 8
        img += rng.normal(0, 1.5, (128, 128, 1))
        return img

    # top: cassette window on the left, four buttons along the front, speaker grille on the right
    top = base()
    top[20:76, 12:72] = (18, 18, 20)                              # smoked window
    for cx in (28, 56):                                            # tape reels behind it
        yy, xx = np.mgrid[0:128, 0:128]
        ring_ = (np.hypot(xx - cx, yy - 48) < 11) & (np.hypot(xx - cx, yy - 48) > 4)
        top[ring_] = (70, 58, 50)
    top[20:22, 12:72] = 70
    for i, bx in enumerate((12, 30, 48, 66)):                      # PLAY REW FF STOP
        top[92:110, bx:bx + 14] = (150, 150, 152) if i else (180, 60, 50)
        top[108:110, bx:bx + 14] = 90
    for gy in range(20, 76, 6):                                    # grille
        for gx in range(86, 118, 6):
            top[gy:gy + 3, gx:gx + 3] = 14
    lab = text_mask((128, 128), (86, 86), "TELEMATE", arial(7))
    ink(top, lab, (170, 168, 160))
    G.save_png(top, TEX / "block" / "answering_machine_top.png")
    side = base()
    side[60:64, :] = 30
    G.save_png(side, TEX / "block" / "answering_machine_side.png")
    front = base()
    ink(front, text_mask((128, 128), (64, 64), "ANSWERING SYSTEM", arial(9), "mm"), (150, 148, 140))
    G.save_png(front, TEX / "block" / "answering_machine_front.png")

    # the light: off, blinking slowly (a message waiting), blinking fast (playing)
    def led(on: bool) -> np.ndarray:
        img = np.zeros((16, 16, 4))
        img[..., :3] = (60, 14, 14) if not on else (255, 40, 30)
        img[..., 3] = 255
        if on:
            img[5:11, 5:11, :3] = (255, 160, 140)
        return img

    G.save_png(led(False), TEX / "block" / "answering_machine_led_off.png")
    strip = np.concatenate([led(True), led(False)], axis=0)
    G.save_png(strip, TEX / "block" / "answering_machine_led_slow.png")
    G.save_png(strip, TEX / "block" / "answering_machine_led_fast.png")
    for name, ft in (("slow", 18), ("fast", 5)):
        (TEX / "block" / f"answering_machine_led_{name}.png.mcmeta").write_text(
            json.dumps({"animation": {"frametime": ft}}) + "\n", encoding="utf-8", newline="\n")


def textures() -> None:
    photos = [portrait(i) for i in range(PEOPLE)]
    for i in range(PEOPLE):
        G.save_png(poster_block(i, photos[i]), TEX / "block" / f"missing_poster_{i}.png")
    poster_gui(photos)
    flyer_textures()
    paper_back()
    drawing_watcher()
    drawing_family()
    drawing_lamps()
    drawing_porch()
    machine_textures()


# =============================================================================
# Models and blockstates
# =============================================================================

def paper_model(texture: str, rect, depth: float) -> dict:
    """A thin sheet on the north side of the block, flat against a surface `depth` px behind
    the block's back edge (z = 16 + depth). It may reach into the next block (lamp, post)."""
    x0, y0, x1, y1 = rect
    z1 = 16 + depth
    z0 = z1 - 0.15
    edge = "#back"
    return {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {"particle": texture, "paper": texture, "back": "backrooms:block/l9_paper_back"},
        "elements": [{
            "from": [x0, y0, z0], "to": [x1, y1, z1],
            "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#paper"},
                "south": {"uv": [0, 0, 16, 16], "texture": edge},
                "east": {"uv": [0, 0, 1, 16], "texture": edge},
                "west": {"uv": [0, 0, 1, 16], "texture": edge},
                "up": {"uv": [0, 0, 16, 1], "texture": edge},
                "down": {"uv": [0, 0, 16, 1], "texture": edge},
            },
        }],
    }


def machine_model(led: str) -> dict:
    return {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": "backrooms:block/answering_machine_side",
            "top": "backrooms:block/answering_machine_top",
            "side": "backrooms:block/answering_machine_side",
            "front": "backrooms:block/answering_machine_front",
            "led": f"backrooms:block/answering_machine_led_{led}",
        },
        "elements": [
            {   # the body; its front (buttons) looks north
                "from": [3, 0, 4], "to": [13, 3, 12],
                "faces": {
                    "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
                    "north": {"uv": [0, 6, 16, 10], "texture": "#front"},
                    "south": {"uv": [0, 6, 16, 10], "texture": "#side"},
                    "east": {"uv": [0, 6, 16, 10], "texture": "#side"},
                    "west": {"uv": [0, 6, 16, 10], "texture": "#side"},
                    "down": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "down"},
                },
            },
            {   # the cassette lid, a little raised
                "from": [4, 3, 6], "to": [9, 3.5, 11],
                "faces": {
                    "up": {"uv": [1, 2, 9, 9], "texture": "#top"},
                    "north": {"uv": [0, 0, 8, 1], "texture": "#side"},
                    "south": {"uv": [0, 0, 8, 1], "texture": "#side"},
                    "east": {"uv": [0, 0, 8, 1], "texture": "#side"},
                    "west": {"uv": [0, 0, 8, 1], "texture": "#side"},
                },
            },
            {   # the red message light, front right; it glows in the dark
                "from": [10.5, 3, 4.75], "to": [11.75, 3.4, 6],
                "shade": False,
                "neoforge_data": {"block_light": 15, "sky_light": 15},
                "faces": {
                    "up": {"uv": [0, 0, 16, 16], "texture": "#led"},
                    "north": {"uv": [0, 0, 16, 16], "texture": "#led"},
                    "east": {"uv": [0, 0, 16, 16], "texture": "#led"},
                    "west": {"uv": [0, 0, 16, 16], "texture": "#led"},
                },
            },
        ],
    }


def models() -> None:
    blk = MODELS / "block"
    # missing posters: one model per (mount, person)
    variants = {}
    for (mount, depth), rect in zip(MOUNTS, POSTER_SHEETS):
        for i in range(PEOPLE):
            name = f"missing_poster_{mount}_{i}"
            write_json(blk / f"{name}.json", paper_model(f"backrooms:block/missing_poster_{i}", rect, depth))
            for facing, rot in FACINGS:
                v = {"model": f"backrooms:block/{name}"}
                if rot:
                    v["y"] = rot
                variants[f"facing={facing},mount={mount},person={i}"] = v
    write_json(STATES / "missing_poster.json", {"variants": variants})
    # pinned flyers: one model per mount (the text is only on the close-up)
    variants = {}
    for (mount, depth), rect in zip(MOUNTS, FLYER_SHEETS):
        name = f"watch_flyer_pinned_{mount}"
        write_json(blk / f"{name}.json", paper_model("backrooms:block/watch_flyer_paper", rect, depth))
        for facing, rot in FACINGS:
            v = {"model": f"backrooms:block/{name}"}
            if rot:
                v["y"] = rot
            variants[f"facing={facing},mount={mount}"] = v
    write_json(STATES / "watch_flyer_pinned.json", {"variants": variants})
    # kids' drawings: a landscape sheet flat on the wall
    variants = {}
    for i in range(DRAWINGS):
        name = f"kids_drawing_{i}"
        m = paper_model(f"backrooms:block/kids_drawing_{i}", (2, 3, 14, 13), 0.0)
        write_json(blk / f"{name}.json", m)
        for facing, rot in FACINGS:
            v = {"model": f"backrooms:block/{name}"}
            if rot:
                v["y"] = rot
            variants[f"drawing={i},facing={facing}"] = v
    write_json(STATES / "kids_drawing.json", {"variants": variants})
    # answering machine: the light is off, blinking slowly (unheard) or fast (playing)
    for led, name in (("off", "answering_machine"), ("slow", "answering_machine_unheard"), ("fast", "answering_machine_playing")):
        write_json(blk / f"{name}.json", machine_model(led))
    variants = {}
    for facing, rot in FACINGS:
        for playing in ("false", "true"):
            for unheard in ("false", "true"):
                name = "answering_machine_playing" if playing == "true" else (
                    "answering_machine_unheard" if unheard == "true" else "answering_machine")
                v = {"model": f"backrooms:block/{name}"}
                if rot:
                    v["y"] = rot
                variants[f"facing={facing},playing={playing},unheard={unheard}"] = v
    write_json(STATES / "answering_machine.json", {"variants": variants})
    # item models
    itm = MODELS / "item"
    for item, layer in (("missing_poster", "block/missing_poster_0"), ("watch_flyer_pinned", "block/watch_flyer_paper"),
                        ("kids_drawing", "block/kids_drawing_0"), ("watch_flyer", "item/watch_flyer")):
        write_json(itm / f"{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"backrooms:{layer}"}})
    write_json(itm / "answering_machine.json", {"parent": "backrooms:block/answering_machine_unheard"})


# =============================================================================
# Answering machine tapes
# =============================================================================

DAVID = "Microsoft David Desktop"
ZIRA = "Microsoft Zira Desktop"


def sapi(text: str, voice: str, rate: int) -> np.ndarray:
    """Windows SAPI speech (System.Speech) as 44.1 kHz mono floats."""
    import soundfile as sf
    safe = text.replace("'", "''")
    with tempfile.TemporaryDirectory() as td:
        wav = Path(td) / "speech.wav"
        ps = ("Add-Type -AssemblyName System.Speech;"
              "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer;"
              f"$s.SelectVoice('{voice}');"
              f"$s.Rate = {rate};"
              "$fmt = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(44100,"
              " [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,"
              " [System.Speech.AudioFormat.AudioChannel]::Mono);"
              f"$s.SetOutputToWaveFile('{wav}', $fmt);"
              f"$s.Speak('{safe}'); $s.Dispose()")
        subprocess.run(["powershell", "-NoProfile", "-Command", ps], check=True, capture_output=True)
        x, sr = sf.read(str(wav))
    assert sr == SR, sr
    x = np.asarray(x, float)
    # trim SAPI's leading/trailing silence
    nz = np.nonzero(np.abs(x) > 0.004)[0]
    return x[max(0, nz[0] - 200): nz[-1] + 400] if len(nz) else x


def pitch(x: np.ndarray, semitones: float) -> np.ndarray:
    """Tape-style pitch change: resample (higher = faster and smaller-sounding)."""
    if semitones == 0:
        return x
    r = 2 ** (semitones / 12)
    n = int(len(x) / r)
    return np.interp(np.arange(n) * r, np.arange(len(x)), x)


def trail_off(x: np.ndarray, seconds: float = 0.7) -> np.ndarray:
    """The speaker stops mid-word: the last part fades, then a hard stop."""
    n = min(len(x), int(seconds * SR))
    y = x.copy()
    y[-n:] *= np.linspace(1, 0.15, n)
    return y


def speak(lines) -> list:
    """lines: (text, voice, rate, semitones, gain, pause_after[, trail]) -> [(signal, pause)]."""
    out = []
    for item in lines:
        text, voice, rate, semi, gain, pause = item[:6]
        x = pitch(sapi(text, voice, rate), semi)
        x = G.norm(x) * gain
        if len(item) > 6 and item[6]:
            x = trail_off(x)
        out.append((x, pause))
    return out


def sequence(parts, lead: float = 0.6, tail: float = 0.8) -> np.ndarray:
    total = lead + sum(len(x) / SR + p for x, p in parts) + tail
    buf = np.zeros(int(total * SR))
    t = lead
    for x, p in parts:
        G.add_at(buf, x, t, False)
        t += len(x) / SR + p
    return buf


def beep(seconds: float = 0.42, f: float = 1020.0, level: float = 0.35) -> np.ndarray:
    t = np.arange(int(seconds * SR)) / SR
    return level * np.sin(2 * np.pi * f * t) * G.env_adsr(len(t), 0.004, 0.02)


def noise_bed(rng, n: int, lo: float, hi: float, level: float) -> np.ndarray:
    x = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, lo, hi, 2))
    return x / (np.abs(x).max() + 1e-9) * level


def car_bed(rng, n: int) -> np.ndarray:
    """Inside a moving car: engine drone, tyre roar, the odd indicator click."""
    t = np.arange(n) / SR
    eng = 0.5 * np.sin(2 * np.pi * 38 * t) + 0.3 * np.sin(2 * np.pi * 76 * t + 0.4)
    eng *= 1 + 0.1 * np.sin(2 * np.pi * 0.2 * t)
    return 0.05 * eng + noise_bed(rng, n, 60, 700, 0.05)


def footsteps(rng, n: int, start: float, stop: float, interval: float, level: float) -> np.ndarray:
    buf = np.zeros(n)
    t = start
    while t < stop:
        thud = G.ring([rng.uniform(70, 110), rng.uniform(180, 260)], [0.03, 0.015], [1, 0.4], 0.12)
        grit = rng.normal(size=int(0.05 * SR)) * np.exp(-np.arange(int(0.05 * SR)) / (0.012 * SR)) * 0.3
        step = thud.copy()
        step[:len(grit)] += grit
        G.add_at(buf, step * level * rng.uniform(0.7, 1.0), t, False)
        t += interval * rng.uniform(0.92, 1.08)
    return buf


def tape(x: np.ndarray, rng, phone: bool, muffle: float = 0.0, hiss: float = 0.012) -> np.ndarray:
    """A cheap microcassette (and, for calls, a phone line before it)."""
    n = len(x)
    if phone:
        x = G.fft_filter(x, lambda f: G.bp_gain(f, 300, 3400, 3))
        x = G.dist(G.norm(x) * 0.9, 1.6)
    if muffle:
        x = G.fft_filter(x, lambda f: G.lp_gain(f, muffle, 3))
    # wow (slow) and flutter (fast) as a wobbling read speed
    t = np.arange(n) / SR
    speed = 1 + 0.0035 * np.sin(2 * np.pi * 0.55 * t + rng.uniform(0, 6)) + 0.0009 * np.sin(2 * np.pi * 7.3 * t)
    pos = np.cumsum(speed)
    pos = pos / pos[-1] * (n - 1)
    x = np.interp(pos, np.arange(n), x)
    # the tape's own band: no deep bass, no air
    x = G.fft_filter(x, lambda f: G.bp_gain(f, 180, 4200, 2))
    # level wobble and a couple of soft dropouts where the oxide is worn
    x *= 1 + 0.05 * G.smooth_jitter(rng, n, 3.0, 1.0)
    for _ in range(2):
        c = rng.uniform(0.2, 0.9) * n
        x *= 1 - 0.45 * np.exp(-((np.arange(n) - c) / (0.05 * SR)) ** 2)
    x = G.norm(x) * 0.8
    h = G.fft_filter(rng.normal(size=n), lambda f: G.hp_gain(f, 2500, 1) * G.lp_gain(f, 9000, 1))
    x += h / (np.abs(h).max() + 1e-9) * hiss
    x += 0.004 * np.sin(2 * np.pi * 60 * t)  # mains hum from the machine
    return x


def with_beeps(x: np.ndarray) -> np.ndarray:
    """The machine's beep, the message, the end-of-message beeps."""
    head = np.zeros(int(0.5 * SR))
    G.add_at(head, beep(), 0.02, False)
    tail = np.zeros(int(0.9 * SR))
    G.add_at(tail, beep(0.16, 880.0, 0.28), 0.15, False)
    G.add_at(tail, beep(0.16, 880.0, 0.28), 0.42, False)
    return np.concatenate([head, x, tail])


def msg_0(rng) -> tuple[np.ndarray, bool]:
    """Hi honey, on my way home... the streetlights on Elm are... why are they all..."""
    parts = speak([
        ("Hi honey, it's me. I'm just leaving now, I'll be home in twenty minutes.", DAVID, 0, 0, 0.8, 0.7),
        ("Can you leave the porch light on for me?", DAVID, 0, 0, 0.8, 1.2),
        ("Huh. The streetlights on Elm are", DAVID, -1, 0, 0.75, 0.6),
        ("out. All of them.", DAVID, -1, 0, 0.7, 0.8),
        ("Why are they all", DAVID, -1, 0, 0.65, 1.8, True),
        ("There's someone standing under the last one.", DAVID, -2, 0, 0.55, 0.9),
        ("I'm going to take Maple instead. Love you.", DAVID, 0, 0, 0.7, 0.4),
    ])
    x = sequence(parts, 0.5, 1.0)
    x += car_bed(rng, len(x))
    return x, True


def msg_1(rng) -> tuple[np.ndarray, bool]:
    """The neighbour: did you see the lights last night?"""
    parts = speak([
        ("Hi Carol, it's Ruth, from next door.", ZIRA, -1, -1.5, 0.8, 0.6),
        ("Did you see the lights last night? Over the Hendersons' roof?", ZIRA, -1, -1.5, 0.8, 0.8),
        ("Walter says it was a helicopter. But it didn't make a sound.", ZIRA, -1, -1.5, 0.8, 0.5),
        ("It just stood there.", ZIRA, -2, -1.5, 0.7, 1.0),
        ("Anyway. Call me back when you get this.", ZIRA, -1, -1.5, 0.8, 1.6),
        ("Carol? Your porch light is off. You always leave it on.", ZIRA, -2, -1.5, 0.65, 0.3),
    ])
    x = sequence(parts, 0.5, 0.8)
    x += noise_bed(rng, len(x), 100, 900, 0.02)
    return x, True


def msg_2(rng) -> tuple[np.ndarray, bool]:
    """Nobody speaks: the phone in a pocket on the street at night. Quiet all the way through."""
    n = int(21.0 * SR)
    t = np.arange(n) / SR
    x = noise_bed(rng, n, 60, 500, 0.03)                                  # night air, far traffic
    x += footsteps(rng, n, 0.8, 9.5, 0.62, 0.25)                           # walking
    x += footsteps(rng, n, 9.5, 12.0, 0.95, 0.18)                          # slowing down
    dog = sapi("Woof. Woof.", DAVID, 4)                                    # a dog far off (a voice, very muffled)
    dog = G.fft_filter(pitch(dog, -5), lambda f: G.lp_gain(f, 600, 3))
    G.add_at(x, G.norm(dog) * 0.12, 3.0, False)
    # a streetlamp's buzz coming up as they pass under it, then the deep hum that isn't a lamp
    buzz = (np.sin(2 * np.pi * 120 * t) + 0.4 * np.sin(2 * np.pi * 240 * t)) * np.clip((t - 5) / 3, 0, 1) * np.clip((11 - t) / 2, 0, 1)
    x += 0.02 * buzz
    hum = np.sin(2 * np.pi * 52 * t) * (1 + 0.3 * np.sin(2 * np.pi * 0.7 * t))
    x += 0.05 * hum * np.clip((t - 12.5) / 2.5, 0, 1) * np.clip((19.5 - t) / 1.5, 0, 1)
    voice = speak([("Hello? Is someone there?", DAVID, -2, 0, 0.18, 0.0)])[0][0]
    G.add_at(x, voice, 12.6, False)
    # fabric rustling over the microphone, now and then
    for at in (1.5, 7.2, 18.0):
        r = noise_bed(rng, int(0.5 * SR), 200, 2500, 0.06) * np.hanning(int(0.5 * SR))
        G.add_at(x, r, at, False)
    return x, False


def msg_3(rng) -> tuple[np.ndarray, bool]:
    """A child counting, as she was told to, in the dark."""
    kid = (ZIRA, -2, 5.0)
    lines = [
        ("Mommy said I have to count to a hundred, and not open the door until the lights come back on.", *kid, 0.6, 1.0),
        ("Fifty one.", *kid, 0.55, 1.0),
        ("Fifty two.", *kid, 0.55, 1.0),
        ("Fifty three.", *kid, 0.5, 1.6),
        ("Fifty", *kid, 0.45, 1.2),
        ("four.", *kid, 0.45, 2.8),
        ("Fifty five.", *kid, 0.4, 0.5),
    ]
    parts = speak([(t, v, r, s, g, p) for t, v, r, s, g, p in lines])
    x = sequence(parts, 0.6, 1.2)
    x = G.fft_filter(x, lambda f: G.lp_gain(f, 2600, 2))                  # inside a closet
    x = G.conv(x, G.reverb_ir(rng, 0.4, 0.08, 3000))[:len(x)]
    # three soft knocks, far away, in the long pause
    knock_at = 0.6 + sum(len(p[0]) / SR + p[1] for p in parts[:5]) + 0.6
    for k in range(3):
        kn = G.ring([95, 190], [0.04, 0.02], [1, 0.3], 0.15)
        G.add_at(x, kn * 0.06, knock_at + k * 0.38, False)
    x += noise_bed(rng, len(x), 80, 400, 0.01)
    return x, False


def msg_4(rng) -> tuple[np.ndarray, bool]:
    """An automated appointment reminder that ends on the wrong sentence."""
    parts = speak([
        ("This is a reminder from Maple Court Family Dental. You have an appointment on", DAVID, 0, 0, 0.8, 0.25),
        ("Tuesday", ZIRA, 0, 0, 0.85, 0.25),
        ("at", DAVID, 0, 0, 0.8, 0.2),
        ("nine. fifteen. P M.", ZIRA, -1, 0, 0.85, 0.6),
        ("To confirm, press one. To cancel, press two.", DAVID, 0, 0, 0.8, 0.8),
        ("If you are no longer at this address,", DAVID, 0, 0, 0.8, 0.35),
        ("please stay indoors.", DAVID, -1, 0, 0.8, 0.6),
        ("Goodbye.", DAVID, 0, 0, 0.8, 0.2),
    ])
    x = sequence(parts, 0.4, 0.6)
    x = G.bitcrush(x, 12, 2)
    return x, True


def msg_5(rng) -> tuple[np.ndarray, bool]:
    """Hushed, from a neighbour's house in a power cut."""
    parts = speak([
        ("It's me. I'm at the Hendersons'. Their power's out too.", ZIRA, 1, 0, 0.6, 0.5),
        ("Don't come over, okay? Stay where it's lit.", ZIRA, 1, 0, 0.6, 1.0),
        ("Dan says the man on the corner has been standing there since six.", ZIRA, 0, 0, 0.55, 0.6),
        ("He hasn't moved. He's just", ZIRA, 0, 0, 0.5, 0.5),
        ("looking at the houses.", ZIRA, -1, 0, 0.5, 1.3),
        ("I'll call you when the streetlights come back on.", ZIRA, 0, 0, 0.55, 0.3),
    ])
    x = sequence(parts, 0.5, 0.8)
    x += noise_bed(rng, len(x), 100, 600, 0.015)
    return x, True


def msg_6(rng) -> tuple[np.ndarray, bool]:
    """The Neighborhood Watch phone tree."""
    parts = speak([
        ("Good evening. This is the Neighborhood Watch phone tree.", DAVID, -1, -1, 0.8, 0.5),
        ("Please pass this message on to the next family on your list.", DAVID, -1, -1, 0.8, 0.8),
        ("Tonight's curfew begins when the streetlights go out.", DAVID, -1, -1, 0.8, 0.6),
        ("Do not answer the door after curfew.", DAVID, -1, -1, 0.8, 0.6),
        ("Members of the Watch will never knock.", DAVID, -2, -1, 0.8, 1.0),
        ("Thank you, and good night.", DAVID, -1, -1, 0.8, 2.2),
        ("Thank you, and good night.", DAVID, -1, -1, 0.55, 0.2),
    ])
    x = sequence(parts, 0.4, 0.8)
    return x, True


def msg_7(rng) -> tuple[np.ndarray, bool]:
    """Mum drove past your house."""
    parts = speak([
        ("Sweetheart, it's Mom. You haven't called in a while.", ZIRA, -1, -2.0, 0.8, 0.7),
        ("I drove past your house tonight. All your lights were off except the porch.", ZIRA, -1, -2.0, 0.8, 1.0),
        ("There was a man standing on your lawn.", ZIRA, -1, -2.0, 0.75, 0.6),
        ("I waved. He didn't wave back.", ZIRA, -2, -2.0, 0.7, 1.4),
        ("Call me.", ZIRA, -1, -2.0, 0.75, 0.2),
    ])
    x = sequence(parts, 0.5, 0.8)
    x += noise_bed(rng, len(x), 100, 800, 0.015)
    return x, True


def save_ogg(x: np.ndarray, path: Path, peak: float) -> None:
    """Mono Vorbis, written one second at a time (libsndfile's Vorbis encoder overflows its stack
    on one long write), re-encoded quieter if the codec overshoots the wanted peak."""
    import soundfile as sf
    x = np.asarray(x, float)
    x = x - x.mean()
    fo = int(0.05 * SR)
    x[:int(0.002 * SR)] *= np.linspace(0, 1, int(0.002 * SR))
    x[-fo:] *= np.linspace(1, 0, fo)
    x = G.norm(x, peak)
    path.parent.mkdir(parents=True, exist_ok=True)
    gain = 1.0
    for _ in range(6):
        with sf.SoundFile(str(path), "w", SR, 1, format="OGG", subtype="VORBIS") as out:
            y = (x * gain).astype(np.float32)
            for i in range(0, len(y), SR):
                out.write(y[i:i + SR])
        decoded = np.abs(sf.read(str(path))[0]).max()
        if decoded <= peak + 0.02:
            break
        gain *= peak / decoded


MESSAGE_MAKERS = (msg_0, msg_1, msg_2, msg_3, msg_4, msg_5, msg_6, msg_7)
# Peak level of each finished tape: speech about -6 dB, the street recording quieter still.
MESSAGE_PEAKS = (0.5, 0.5, 0.38, 0.45, 0.5, 0.45, 0.5, 0.5)


def sounds() -> None:
    ticks = []
    for i, make in enumerate(MESSAGE_MAKERS):
        rng = G.rng_for(9390 + i)
        x, phone = make(rng)
        x = tape(x, rng, phone, muffle=900.0 if i == 2 else 0.0, hiss=0.02 if i == 2 else 0.012)
        x = with_beeps(G.norm(x) * 0.85)
        save_ogg(x, SND / f"machine_message_{i}.ogg", MESSAGE_PEAKS[i])
        ticks.append(int(np.ceil(len(x) / SR * 20)) + 2)
    print("LENGTH_TICKS = {" + ", ".join(str(t) for t in ticks) + "}")


if __name__ == "__main__":
    wanted = set(sys.argv[1:]) or {"textures", "models", "sounds"}
    if "textures" in wanted:
        textures()
    if "models" in wanted:
        models()
    if "sounds" in wanted:
        sounds()
