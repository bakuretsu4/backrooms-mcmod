"""Printed and painted things with words on them: posters, staff notes, signs, banners, the facade's
lettering. Lettering uses the OFL fonts in tools/fonts (Rye and Sancreek for circus display type,
Abril Fatface and Anton for bills, IM Fell for old print, Special Elite for typed notices, Caveat for
handwriting). Everything is aged: faded inks, foxed paper, creases, water stains, torn edges."""
from __future__ import annotations

import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from . import mat as M

FONTS = Path(__file__).resolve().parents[2] / "fonts"
RYE = FONTS / "Rye-Regular.ttf"
SANCREEK = FONTS / "Sancreek-Regular.ttf"
ABRIL = FONTS / "AbrilFatface-Regular.ttf"
ANTON = FONTS / "Anton-Regular.ttf"
FELL = FONTS / "IMFeENrm28P.ttf"
TYPE = FONTS / "SpecialElite-Regular.ttf"
HAND = FONTS / "Caveat_wght.ttf"

PAPER = M.col("#d6c9a6")
INK_RED = M.col("#9b2a1f")
INK_BLUE = M.col("#263a63")
INK_BLACK = M.col("#1d1a17")
INK_GOLD = M.col("#b8892e")


def font(path, size):
    return ImageFont.truetype(str(path), int(size))


class Sheet:
    """A drawing surface (supersampled) for one printed piece; inks are drawn as masks."""

    def __init__(self, w, h, ss=2):
        self.w, self.h, self.ss = w, h, ss
        self.layers = []  # (mask image, colour)

    def layer(self, colour):
        img = Image.new("L", (self.w * self.ss, self.h * self.ss), 0)
        self.layers.append((img, M.col(colour) if isinstance(colour, str) else np.asarray(colour, float)))
        return ImageDraw.Draw(img)

    def text(self, d, text, path, cx, cy, max_w, max_h, anchor="mm", spacing=0):
        """Draw text fitted into max_w x max_h (fractions of the sheet) centred at (cx, cy)."""
        W, H = self.w * self.ss, self.h * self.ss
        size = M.fit_text_size(text, path, max_w * W, max_h * H, start=int(max_h * H * 1.4) + 4)
        f = font(path, size)
        d.text((cx * W, cy * H), text, font=f, fill=255, anchor=anchor)
        return size

    def masks(self):
        out = []
        for img, c in self.layers:
            m = np.asarray(img.resize((self.w, self.h), Image.LANCZOS), float) / 255.0
            out.append((m, c))
        return out


def aged_paper(r, h, w, tone=PAPER, foxing=0.6, stains=2, creases=2):
    rgb = M.lerp(tone * 0.9, tone * 1.03, M.fnoise(r, h, w, beta=2.4) * 0.5 + 0.5)
    # Fibre mottling at a few pixels' scale, not per-pixel grain (that only costs PNG bytes and
    # shimmers once mipmapped).
    fibre = M.fnoise(r, h, w, beta=1.8) * 0.025
    rgb = rgb * (1 + fibre)[..., None]
    # Browned toward the edges (light and air got at them).
    X, Y = M.grid(h, w)
    edge = np.clip(1 - np.minimum(np.minimum(X, 1 - X), np.minimum(Y, 1 - Y)) / 0.12, 0, 1)
    rgb = M.lerp(rgb, M.col("#8f6f43"), edge ** 2 * 0.5)
    Yp, Xp = np.mgrid[0:h, 0:w].astype(float)
    for _ in range(int(foxing * 40)):
        cx, cy = r.random() * w, r.random() * h
        rad = (0.003 + 0.01 * r.random()) * max(w, h)
        rgb = M.lerp(rgb, M.col("#7d5a33"), np.clip(1 - np.hypot(Xp - cx, Yp - cy) / rad, 0, 1) * 0.5)
    rgb = M.damp_stains(rgb, r, stains, tone=(0.45, 0.33, 0.18), strength=0.35)
    shade = np.ones((h, w))
    for k in range(creases):
        # A fold: a line, light on one side and dark on the other.
        if k % 2 == 0:
            y = (0.3 + 0.4 * r.random()) * h
            d = Yp - y - (Xp - w / 2) * r.normal() * 0.03
        else:
            x = (0.3 + 0.4 * r.random()) * w
            d = Xp - x - (Yp - h / 2) * r.normal() * 0.03
        shade += np.tanh(d / 2.0) * np.exp(-(d / 6.0) ** 2) * 0.12 - np.exp(-(d / 1.2) ** 2) * 0.12
    return np.clip(rgb * shade[..., None], 0, 1)


def ink(rgb, masks, r, fade=0.3, wear=0.25):
    """Print the ink masks onto the paper: faded toward the paper, rubbed through in places."""
    out = rgb.copy()
    h, w = rgb.shape[:2]
    for m, c in masks:
        mm = M.worn_print(r, m, wear)
        cc = M.lerp(c, M.col("#c9b48c"), fade)
        # Ink sits in the paper's grain: a little (soft) variation in its density.
        dens = 0.85 + 0.15 * (M.fnoise(r, h, w, beta=1.8) * 0.5 + 0.5)
        out = M.lerp(out, out * cc / np.maximum(M.col("#d6c9a6"), 1e-3), mm * dens * 0.95)
    return np.clip(out, 0, 1)


def torn_alpha(r, h, w, corners=1, ragged=0.012, missing_corner=True):
    """Alpha of a torn sheet: ragged edges, one corner torn away, staple holes stay."""
    X, Y = M.grid(h, w)
    n = M.fnoise(r, h, w, beta=1.6) * ragged
    edge = np.minimum(np.minimum(X, 1 - X), np.minimum(Y, 1 - Y)) + n
    a = (edge > 0.012).astype(float)
    if missing_corner:
        for _ in range(corners):
            cx, cy = (r.random() < 0.5) * 1.0, (r.random() < 0.5) * 1.0
            size = 0.12 + 0.15 * r.random()
            d = (np.abs(X - cx) + np.abs(Y - cy) * w / h * 0.8) + M.fnoise(r, h, w, beta=1.2) * 0.02
            a *= (d > size).astype(float)
    return a


def poster(idx, h, w):
    """The six carnival bills pasted round the foyer and the backstage passages."""
    r = M.rng(22850 + idx)
    paper = aged_paper(r, h, w, tone=M.col("#dccfab") if idx != 4 else M.col("#e3dcc7"))
    S = Sheet(w, h)
    if idx == 0:
        red, blue, black = S.layer(INK_RED), S.layer(INK_BLUE), S.layer(INK_BLACK)
        S.text(black, "SEE YOURSELF", ABRIL, 0.5, 0.11, 0.86, 0.08)
        S.text(black, "AS OTHERS SEE YOU", ABRIL, 0.5, 0.2, 0.86, 0.07)
        # A big hand mirror with a face in it.
        W, H = w * S.ss, h * S.ss
        blue.ellipse([W * 0.22, H * 0.27, W * 0.78, H * 0.62], outline=255, width=int(W * 0.03))
        blue.rectangle([W * 0.46, H * 0.62, W * 0.54, H * 0.76], fill=255)
        for k in range(9):
            a = k / 9 * 2 * math.pi
            red.line([(W * 0.5 + math.cos(a) * W * 0.32, H * 0.445 + math.sin(a) * H * 0.2),
                      (W * 0.5 + math.cos(a) * W * 0.4, H * 0.445 + math.sin(a) * H * 0.25)], fill=255, width=int(W * 0.012))
        black.ellipse([W * 0.40, H * 0.33, W * 0.60, H * 0.52], outline=255, width=int(W * 0.012))
        black.ellipse([W * 0.445, H * 0.40, W * 0.47, H * 0.415], fill=255)
        black.ellipse([W * 0.53, H * 0.40, W * 0.555, H * 0.415], fill=255)
        black.arc([W * 0.45, H * 0.43, W * 0.55, H * 0.48], 200, 340, fill=255, width=int(W * 0.008))
        S.text(red, "HALL OF MIRRORS", RYE, 0.5, 0.83, 0.9, 0.08)
        S.text(black, "A THOUSAND REFLECTIONS - ONE WAY OUT", FELL, 0.5, 0.92, 0.9, 0.04)
    elif idx == 1:
        red, black = S.layer(INK_RED), S.layer(INK_BLACK)
        S.text(black, "THE GREAT", FELL, 0.5, 0.1, 0.6, 0.06)
        S.text(red, "CRYSTAL", SANCREEK, 0.5, 0.21, 0.9, 0.12)
        S.text(red, "MAZE", SANCREEK, 0.5, 0.34, 0.6, 0.12)
        W, H = w * S.ss, h * S.ss
        # A little maze plan.
        cell = W * 0.07
        x0, y0 = W * 0.22, H * 0.45
        rr = M.rng(4)
        for i in range(9):
            for j in range(6):
                if rr.random() < 0.55:
                    black.line([(x0 + i * cell, y0 + j * cell), (x0 + (i + 1) * cell, y0 + j * cell)], fill=255, width=int(W * 0.008))
                if rr.random() < 0.45:
                    black.line([(x0 + i * cell, y0 + j * cell), (x0 + i * cell, y0 + (j + 1) * cell)], fill=255, width=int(W * 0.008))
        black.rectangle([x0, y0, x0 + 9 * cell, y0 + 6 * cell], outline=255, width=int(W * 0.01))
        S.text(black, "CAN YOU FIND", ABRIL, 0.5, 0.8, 0.8, 0.06)
        S.text(black, "THE WAY OUT?", ABRIL, 0.5, 0.88, 0.8, 0.06)
    elif idx == 2:
        red, blue, black = S.layer(INK_RED), S.layer(INK_BLUE), S.layer(INK_BLACK)
        S.text(red, "LAUGH!", SANCREEK, 0.5, 0.12, 0.85, 0.14)
        W, H = w * S.ss, h * S.ss
        # Two distorted figures: one stretched, one squashed.
        blue.ellipse([W * 0.18, H * 0.27, W * 0.34, H * 0.33], fill=255)
        blue.rectangle([W * 0.22, H * 0.33, W * 0.30, H * 0.70], fill=255)
        blue.ellipse([W * 0.58, H * 0.47, W * 0.86, H * 0.55], fill=255)
        blue.ellipse([W * 0.54, H * 0.55, W * 0.90, H * 0.70], fill=255)
        black.rectangle([W * 0.12, H * 0.24, W * 0.40, H * 0.73], outline=255, width=int(W * 0.015))
        black.rectangle([W * 0.50, H * 0.42, W * 0.94, H * 0.73], outline=255, width=int(W * 0.015))
        S.text(black, "THE FUNHOUSE", ABRIL, 0.5, 0.8, 0.85, 0.07)
        S.text(red, "DISTORTING MIRRORS", ANTON, 0.5, 0.89, 0.85, 0.06)
    elif idx == 3:
        red, black = S.layer(INK_RED), S.layer(INK_BLACK)
        S.text(black, "ADMISSION", ABRIL, 0.5, 0.12, 0.8, 0.08)
        S.text(black, "ADULTS", ANTON, 0.3, 0.32, 0.4, 0.08)
        S.text(red, "25c", RYE, 0.72, 0.32, 0.35, 0.12)
        S.text(black, "CHILDREN", ANTON, 0.3, 0.5, 0.42, 0.08)
        S.text(red, "10c", RYE, 0.72, 0.5, 0.35, 0.12)
        S.text(black, "NO RE-ENTRY", FELL, 0.5, 0.7, 0.7, 0.05)
        S.text(black, "MANAGEMENT NOT RESPONSIBLE", FELL, 0.5, 0.8, 0.9, 0.035)
        S.text(black, "FOR PERSONS LOST IN THE MAZE", FELL, 0.5, 0.86, 0.9, 0.035)
    elif idx == 4:
        black, red = S.layer(INK_BLACK), S.layer(INK_RED)
        S.text(red, "NOTICE", ABRIL, 0.5, 0.1, 0.7, 0.08)
        lines = ["PLEASE DO NOT TOUCH", "THE MIRRORS.", "", "KEEP HOLD OF", "CHILDREN'S HANDS.", "",
                 "IF YOU ARE LOST", "STAND STILL AND", "WAIT FOR STAFF.", "", "DO NOT FOLLOW", "YOUR REFLECTION."]
        for k, line in enumerate(lines):
            if line:
                S.text(black, line, TYPE, 0.5, 0.22 + k * 0.058, 0.84, 0.042)
    else:
        red, black = S.layer(INK_RED), S.layer(INK_BLACK)
        S.text(black, "STAY WITH", ABRIL, 0.5, 0.14, 0.8, 0.09)
        S.text(black, "YOUR PARTY", ABRIL, 0.5, 0.25, 0.8, 0.09)
        W, H = w * S.ss, h * S.ss
        # Three figures holding hands, and one more, apart, facing the wrong way.
        for k in range(3):
            cx = W * (0.22 + 0.18 * k)
            black.ellipse([cx - W * 0.04, H * 0.40, cx + W * 0.04, H * 0.45], fill=255)
            black.rectangle([cx - W * 0.035, H * 0.45, cx + W * 0.035, H * 0.6], fill=255)
            black.line([(cx + W * 0.035, H * 0.5), (cx + W * 0.145, H * 0.5)], fill=255, width=int(W * 0.012))
        cx = W * 0.84
        red.ellipse([cx - W * 0.04, H * 0.40, cx + W * 0.04, H * 0.45], fill=255)
        red.rectangle([cx - W * 0.035, H * 0.45, cx + W * 0.035, H * 0.6], fill=255)
        S.text(red, "ONLY ONE OF THEM", FELL, 0.5, 0.72, 0.86, 0.05)
        S.text(red, "IS YOU", FELL, 0.5, 0.79, 0.5, 0.05)
        S.text(black, "- THE MANAGEMENT -", FELL, 0.5, 0.9, 0.7, 0.035)
    rgb = ink(paper, S.masks(), r, fade=0.32, wear=0.3)
    # Pasted up: a darker sheen of old paste near the top, and two rusty drawing pins.
    a = torn_alpha(r, h, w)
    X, Y = M.grid(h, w)
    for px in (0.08, 0.92):
        d = np.hypot((X - px) * w, (Y - 0.035) * h)
        rgb = M.lerp(rgb, M.col("#5c3a1e"), np.clip(1 - d / (w * 0.02), 0, 1))
        rgb = M.lerp(rgb, M.col("#7a4a22"), np.exp(-((d - w * 0.03) / (w * 0.01)) ** 2) * 0.4)
    rgb = M.dust(rgb, 0.12, r)
    return M.rgba(rgb, a)


NOTES = [
    ["Panel 14 cracked", "again. Nobody", "touched it.", "", "Moved it to the", "stack out back.", "   - R."],
    ["COUNT THE BULBS", "before you lock up.", "", "Last night 18.", "This morning 19.", "", "Nobody's been in."],
    ["If you see someone", "in the glass who", "isn't in the hall", "", "DON'T wave back.", "", "Just walk out."],
]


def note(idx, h, w):
    r = M.rng(22860 + idx)
    tone = [M.col("#e6dcc0"), M.col("#d9cfae"), M.col("#efe6cc")][idx % 3]
    paper = aged_paper(r, h, w, tone=tone, foxing=0.4, stains=1, creases=1 + idx % 2)
    # Ruled lines (an index card / jotter page).
    Y, X = np.mgrid[0:h, 0:w].astype(float)
    lines = (np.abs(((Y - h * 0.16) % (h * 0.105))) < 0.8) & (Y > h * 0.12)
    paper = M.lerp(paper, M.col("#7d8fae"), lines * 0.35)
    margin = np.abs(X - w * 0.12) < 0.8
    paper = M.lerp(paper, M.col("#b06a63"), margin * 0.35)
    S = Sheet(w, h, ss=3)
    pen = S.layer(M.col("#24263a") if idx != 1 else M.col("#3a1e18"))
    W, H = w * S.ss, h * S.ss
    f = font(HAND, H * 0.075)
    for k, line in enumerate(NOTES[idx]):
        pen.text((W * 0.16, H * (0.155 + k * 0.105)), line, font=f, fill=255, anchor="ls")
    if idx == 2:
        pen.line([(W * 0.16, H * 0.62), (W * 0.8, H * 0.615)], fill=255, width=int(S.ss * 1.5))
    rgb = ink(paper, S.masks(), r, fade=0.15, wear=0.08)
    a = torn_alpha(r, h, w, ragged=0.006, missing_corner=idx == 0)
    return M.rgba(rgb, a)


def sign_text(text, path, w, h, fg, bg, border=None, r=None, fade=0.25, shadow=None, pad=0.1):
    """A painted sign: fitted lettering (with an optional offset shadow) on a ground."""
    r = r or M.rng(7)
    rgb = bg.copy() if isinstance(bg, np.ndarray) and bg.ndim == 3 else np.broadcast_to(M.col(bg), (h, w, 3)).copy()
    S = Sheet(w, h)
    lay = S.layer(fg)
    sh = S.layer(shadow) if shadow is not None else None
    W, H = w * S.ss, h * S.ss
    size = M.fit_text_size(text, path, W * (1 - 2 * pad), H * (1 - 2 * pad), start=int(H))
    f = font(path, size)
    if sh is not None:
        sh.text((W * 0.5 + size * 0.06, H * 0.5 + size * 0.06), text, font=f, fill=255, anchor="mm")
    lay.text((W * 0.5, H * 0.5), text, font=f, fill=255, anchor="mm")
    masks = S.masks()
    for m, c in (masks[::-1] if sh is not None else masks):
        mm = M.worn_print(r, m, fade)
        rgb = M.lerp(rgb, c, mm)
    return rgb
