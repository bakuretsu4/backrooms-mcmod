"""Level 39 ("Megalophobia") anomalies: the hostile mill windows, the holes, Hydrolitis and the
lying houses. Textures and sounds only (the Java side lives in world/anomaly/level39 and
client/anomaly/level39).

Textures (src/main/resources/assets/backrooms/textures/...):
  entity/l39_window_shadow.png   384x320, 2 frames side by side (lurking | reaching): the Window
                                 Shadow pressed behind frosted glass (soft alpha, no hard cutout)
  entity/l39_window_glow.png     192x320, additive (black = nothing): the window's lustrous light
  entity/l39_window_blinds.png   192x320 RGBA: old yellowed venetian blinds, half raised
  mob_effect/hydrolitis.png      18x18 effect icon
  gui/l39_survey_note.png        256x256 clipboard page for the note-reading screen
Sounds (src/main/resources/assets/backrooms/sounds/level39_anoms/*.ogg), one sounds.json event
"level39_anoms.<name>" each: window_wake, window_lure*, window_blinds*, window_shriek,
glass_shards*, window_grab, window_fumes, window_noclip, ash_smell*, house_scream*, hole_scrape*,
hole_breath, hole_growl, hole_climb, hydrolitis_chills*, hydrolitis_heartbeat, hydrolitis_retch,
hydrolitis_whisper*, door_vanish, bigger_inside, camp_stash   (* = several takes)

Run only these with:  python tools/gen_assets.py l39anom_
Every generator uses its own fixed random seed (39300-39499), so re-running gives identical files.
"""
from __future__ import annotations

import numpy as np
from PIL import Image, ImageDraw, ImageFont

import gen_assets as G
from anomaly_assets import level4 as L4
from anomaly_assets import level39_audio as A

SR = G.SR


def _snd(name: str):
    return G.SND / "level39_anoms" / f"{name}.ogg"


def _tex(folder: str, name: str):
    return G.TEX / folder / f"{name}.png"


# =============================================================================
# Texture helpers
# =============================================================================

def _over(rgb, a, col, cov):
    """Paint colour `col` with coverage `cov` OVER a straight-alpha RGBA picture (rgb, a).

    Plain lerping would darken the edges of things painted onto transparent pixels (they'd mix
    with the black 'nothing' colour), so the colours are mixed by how much each layer covers."""
    cov = np.clip(cov, 0, 1)
    col = np.broadcast_to(np.asarray(col, float), rgb.shape)
    out_a = cov + a * (1 - cov)
    w = np.where(out_a > 1e-6, cov / np.maximum(out_a, 1e-6), 0)[..., None]
    return rgb * (1 - w) + col * w, out_a


def _spline(pts, per=12) -> np.ndarray:
    """Catmull-Rom curve through the points: smooth, organic limbs instead of straight sticks."""
    p = np.asarray(pts, float)
    p = np.vstack([p[0], p, p[-1]])
    out = []
    for i in range(1, len(p) - 2):
        p0, p1, p2, p3 = p[i - 1], p[i], p[i + 1], p[i + 2]
        for s in np.linspace(0, 1, per, endpoint=False):
            out.append(0.5 * (2 * p1 + (-p0 + p2) * s + (2 * p0 - 5 * p1 + 4 * p2 - p3) * s * s
                              + (-p0 + 3 * p1 - 3 * p2 + p3) * s ** 3))
    out.append(p[-2])
    return np.array(out)


SH_W, SH_H = 192, 320      # one Window Shadow frame (a 3x5-block window)
PAD = 40                   # empty margin while blurring, so the blur never wraps round the edges


class _Mask:
    """A greyscale drawing (supersampled 3x) with an empty margin round it.

    The figure is drawn as several of these, one per 'distance from the glass', because frosted
    glass blurs things more the further they are behind it."""

    def __init__(self, w=SH_W, h=SH_H, pad=PAD, ss=3):
        self.w, self.h, self.pad, self.ss = w, h, pad, ss
        self.im = Image.new("L", ((w + 2 * pad) * ss, (h + 2 * pad) * ss), 0)
        self.d = ImageDraw.Draw(self.im)

    def _xy(self, x, y):
        return ((x + self.pad) * self.ss, (y + self.pad) * self.ss)

    def dot(self, x, y, r, v=255):
        cx, cy = self._xy(x, y)
        rr = r * self.ss
        self.d.ellipse((cx - rr, cy - rr, cx + rr, cy + rr), fill=v)

    def ellipse(self, cx, cy, rx, ry, ang=0.0, v=255):
        s = np.linspace(0, 2 * np.pi, 48, endpoint=False)
        ex, ey = rx * np.cos(s), ry * np.sin(s)
        c, sn = np.cos(ang), np.sin(ang)
        self.d.polygon([self._xy(cx + x * c - y * sn, cy + x * sn + y * c) for x, y in zip(ex, ey)], fill=v)

    def limb(self, pts, radii, v=255):
        """A tapering limb through the points; radii are given per point and blend smoothly."""
        c = _spline(pts)
        u = np.linspace(0, len(pts) - 1, len(c))
        r = np.interp(u, np.arange(len(pts)), radii)
        s = np.concatenate([[0], np.cumsum(np.hypot(*np.diff(c, axis=0).T))])
        steps = np.arange(0, s[-1] + 1e-6, 0.35)
        for x, y, rr in zip(np.interp(steps, s, c[:, 0]), np.interp(steps, s, c[:, 1]), np.interp(steps, s, r)):
            self.dot(x, y, rr, v)

    def array(self) -> np.ndarray:
        size = (self.w + 2 * self.pad, self.h + 2 * self.pad)
        return np.asarray(self.im.resize(size, Image.BOX), float) / 255.0


def _hand(L, rng, wrist, ang, thumb, splay, press, scale=1.0):
    """A long-fingered hand starting at the wrist and pointing along `ang` (radians, image space).

    thumb (+1/-1) picks the side the thumb sticks out on. press=True: palm and fingers are flat
    on the glass, so they go into the sharp, dark 'contact' layer (a handprint); otherwise only
    the fingertips brush the glass."""
    u = np.array([np.cos(ang), np.sin(ang)])
    p = np.array([-u[1], u[0]])
    w = np.array(wrist, float)
    palm_len, palm_w = 12.0 * scale, 5.6 * scale
    pc = w + u * palm_len * 0.5
    if press:
        L["near"].ellipse(*pc, palm_len * 0.8, palm_w * 1.35, ang)
        L["contact"].ellipse(*pc, palm_len * 0.62, palm_w, ang)
        # The middle of a palm doesn't touch the glass: the print is pads round a lighter hollow.
        L["contact"].ellipse(*(pc - u * 0.6), palm_len * 0.26, palm_w * 0.45, ang, v=120)
    else:
        L["near"].ellipse(*pc, palm_len * 0.62, palm_w, ang)

    def d(a):
        return np.array([np.cos(a), np.sin(a)])

    for i, length in enumerate((29, 34, 31, 24)):
        off = (i - 1.5) / 1.5
        base = w + u * palm_len * 0.92 + p * off * palm_w * 0.8
        a = ang + off * splay + rng.normal(0, 0.04)
        curl = 0.0 if press else rng.uniform(-0.16, 0.16)
        length *= scale
        p1 = base + d(a) * length * 0.40
        p2 = p1 + d(a + curl) * length * 0.33
        p3 = p2 + d(a + 2 * curl) * length * 0.27
        pts = [base, p1, p2, p3]
        radii = np.array([1.7, 1.45, 1.2, 0.95]) * scale
        if press:
            L["near"].limb(pts, radii * 1.5)
            # Flat on the glass the finger pads print darkest, the creases at the joints barely.
            L["contact"].limb(pts, radii, v=190)
            for j in (p1, p2):
                L["contact"].dot(*j, radii[1] * 0.9, v=110)
            L["contact"].ellipse(*(p2 + (p3 - p2) * 0.55), np.linalg.norm(p3 - p2) * 0.55, radii[2] * 1.15,
                                 float(np.arctan2((p3 - p2)[1], (p3 - p2)[0])))
        else:
            L["near"].limb(pts, radii)
            L["contact"].dot(*p3, 1.1 * scale)            # only the fingertips touch
    tb = w + u * palm_len * 0.3 + p * thumb * palm_w * 0.9
    ta = ang + thumb * (1.1 if press else 0.5)
    t1 = tb + d(ta) * 9 * scale
    t2 = t1 + d(ta - thumb * 0.25) * 9 * scale
    L["contact" if press else "near"].limb([tb, t1, t2], np.array([2.0, 1.6, 1.1]) * scale, v=210 if press else 255)
    if press:
        L["near"].limb([tb, t1, t2], np.array([2.0, 1.6, 1.1]) * scale * 1.5)


def _body(L, head, head_ang, legs):
    """Head, neck and the small narrow torso (shared by both poses), plus the two bent,
    mismatched legs. Torso and legs are furthest from the glass ('far'), the head is 'mid'."""
    hx, hy = head
    L["mid"].ellipse(hx, hy, 10.0, 13.5, head_ang)
    L["mid"].limb([(hx - 1, hy + 11), (97, 72), (97, 80)], [3.0, 3.3, 4.4])
    L["far"].limb([(76, 86), (97, 79), (119, 87)], [5.0, 6.0, 5.0])        # bony shoulders
    L["far"].ellipse(97, 100, 17, 21)                                     # small narrow chest
    L["far"].limb([(97, 105), (98, 130), (96, 150)], [12, 8.5, 10])       # pinched waist
    L["far"].ellipse(97, 158, 14, 8)                                      # hips
    for pts, radii, foot in legs:
        L["far"].limb(pts, radii)
        L["far"].limb(foot, [2.6, 2.2, 1.6])


def _pose_lurking(L, rng):
    """Frame 0: standing still behind the glass, very long thin arms hanging past its knees."""
    _body(L, (98, 50), 0.18, [
        ([(89, 160), (83, 190), (72, 222), (77, 258), (71, 296)], [7, 5.5, 4.4, 4.0, 2.7],
         [(71, 296), (64, 306), (56, 309)]),
        # The right leg is shorter and its knee bends the wrong way.
        ([(106, 160), (115, 184), (126, 207), (113, 236), (119, 266), (121, 290)], [6.5, 5, 4, 4.2, 3.2, 2.4],
         [(121, 290), (128, 297), (134, 296)]),
    ])
    radii = [4.2, 3.4, 3.3, 2.8, 2.4, 2.2]
    L["mid"].limb([(77, 86), (70, 124), (64, 165), (60, 205), (57, 243), (55, 262)], radii)
    L["mid"].limb([(119, 87), (127, 126), (133, 167), (137, 207), (140, 246), (141, 264)], radii)
    L["mid"].dot(64, 165, 3.6)          # knobbly elbows
    L["mid"].dot(133, 167, 3.6)
    _hand(L, rng, (55, 262), np.pi / 2 + 0.08, -1, 0.22, False)
    _hand(L, rng, (141, 264), np.pi / 2 - 0.06, +1, 0.26, False)


def _pose_reaching(L, rng):
    """Frame 1: lunging at the glass, arms up and splayed, palms flat on the pane, head tilted."""
    _body(L, (109, 55), 0.55, [
        ([(89, 160), (80, 192), (70, 224), (78, 260), (70, 298)], [7, 5.5, 4.4, 4.0, 2.7],
         [(70, 298), (63, 307), (55, 309)]),
        ([(106, 160), (117, 182), (128, 204), (114, 234), (122, 266), (124, 292)], [6.5, 5, 4, 4.2, 3.2, 2.4],
         [(124, 292), (131, 298), (137, 296)]),
    ])
    radii = [4.2, 3.5, 3.2, 2.8, 2.5, 2.2]
    # Elbows drop out and down, forearms come up to the glass (foreshortened: reaching at you).
    L["mid"].limb([(77, 86), (50, 108), (28, 118), (22, 100), (25, 82), (28, 72)], radii)
    L["mid"].limb([(119, 87), (147, 104), (170, 110), (175, 92), (170, 78), (166, 70)], radii)
    L["near"].limb([(25, 90), (28, 72)], [2.6, 2.2])       # forearms nearly touching the glass
    L["near"].limb([(172, 88), (166, 70)], [2.6, 2.2])
    _hand(L, rng, (28, 72), -np.pi / 2 + 0.05, +1, 0.42, True)
    _hand(L, rng, (166, 70), -np.pi / 2 - 0.05, -1, 0.42, True)
    # Smears: the hands have slid down the pane before, leaving grubby streaks under the prints.
    for wx in (20, 26, 31, 36, 156, 161, 167, 172):
        y0 = 62 + rng.uniform(-3, 3)
        L["smear"].limb([(wx, y0), (wx + rng.uniform(-1.5, 1.5), y0 + rng.uniform(14, 30))], [1.6, 0.6])


def _shadow_frame(pose, rng) -> np.ndarray:
    L = {k: _Mask() for k in ("far", "mid", "near", "contact", "smear")}
    pose(L, rng)
    M = {k: v.array() for k, v in L.items()}
    hp = M["far"].shape
    layers = [
        G.blur(np.maximum(M["far"], M["mid"]), 13.0) * 0.30,  # the dark bleeding into the frost
        G.blur(M["far"], 5.0) * 0.80,
        G.blur(M["mid"], 3.2) * 0.86,
        G.blur(M["near"], 1.9) * 0.92,
        G.blur(M["smear"], 1.4) * 0.30,
        G.blur(M["contact"], 0.85) * 0.97,
    ]
    alpha = 1 - np.prod([1 - np.clip(l, 0, 1) for l in layers], axis=0)
    a_con = np.clip(layers[5] * 1.2, 0, 1)
    # Frosting is never even: fine stipple plus big patches, wiped clear where it's touching.
    frost = 1 + 0.035 * G.fnoise(rng, hp, beta=1.5, kmin=12) + 0.10 * G.fnoise(rng, hp, beta=3.0)
    alpha = np.clip(alpha * (frost + (1 - frost) * a_con), 0, 1)
    close = np.clip(a_con + 0.6 * layers[3] + 0.4 * layers[4], 0, 1)
    edge, core, touch = np.array([54, 60, 74]), np.array([21, 24, 33]), np.array([6, 7, 11])
    col = G.lerp(edge, core, G.smoothstep(0.15, 0.85, alpha)[..., None])
    col = G.lerp(col, touch, close[..., None])
    col = col + 3 * G.fnoise(rng, hp, beta=2.0)[..., None]
    img = np.concatenate([col, 255 * alpha[..., None]], axis=2)
    return img[PAD:PAD + SH_H, PAD:PAD + SH_W]


def tex_l39anom_window_shadow() -> None:
    """The Window Shadow (Entity 2) behind a mill window: frame 0 lurking, frame 1 reaching.

    Built from layers by distance to the glass: torso and legs far back (very blurred), head
    and arms closer, hands closest and the parts touching the pane sharp and nearly black."""
    rng = G.rng_for(39300)
    img = np.concatenate([_shadow_frame(_pose_lurking, rng), _shadow_frame(_pose_reaching, rng)], axis=1)
    G.save_png(img, _tex("entity", "l39_window_shadow"))


def tex_l39anom_window_glow() -> None:
    """The window 'waking': warm light swelling behind a 4x6-pane mullion grid, drawn for ADDITIVE
    blending (black adds nothing). The game pulses its brightness; this is the peak."""
    rng = G.rng_for(39301)
    W, H = SH_W, SH_H
    shp = (H, W)
    X, Y = G.grid(shp)
    r = np.hypot((X - W / 2) / 96, (Y - 140) / 150)
    light = 0.7 * np.exp(-(r / 0.6) ** 2) + 0.45 * np.exp(-(r / 1.2) ** 2) + 0.16
    fr, bar = 8.0, 4.0
    xs, ys = np.linspace(fr, W - fr, 5), np.linspace(fr, H - fr, 7)
    bars = 1 - G.box(shp, fr, fr, W - fr, H - fr)
    for x in xs[1:-1]:
        bars = np.maximum(bars, G.box(shp, x - bar / 2, 0, x + bar / 2, H))
    for y in ys[1:-1]:
        bars = np.maximum(bars, G.box(shp, 0, y - bar / 2, W, y + bar / 2))
    # Every pane of old glass is a little different, and drawn glass is faintly wavy.
    col = np.clip(((X - fr) / ((W - 2 * fr) / 4)).astype(int), 0, 3)
    row = np.clip(((Y - fr) / ((H - 2 * fr) / 6)).astype(int), 0, 5)
    pane = rng.uniform(0.92, 1.05, (6, 4))[row, col]
    wavy = 1 + 0.025 * G.fnoise(rng, shp, beta=2.6, aniso=(1, 3))
    # Coal traces (canon): sooty smudges and run-down streaks on the glass dim the light.
    soot = G.smoothstep(0.8, 2.2, G.fnoise(rng, shp, beta=3.2))
    runs = G.smoothstep(0.6, 1.9, G.fnoise(rng, shp, beta=1.5, aniso=(1, 10), kmin=3)) * G.smoothstep(0.2, 0.9, Y / H)
    specks = G.blur((rng.random(shp) < 0.003) * rng.uniform(0.4, 1.0, shp), 0.8) * 3
    dim = np.clip(1 - 0.2 * soot - 0.1 * runs - 0.25 * specks, 0.5, 1)
    glass = light * pane * wavy * dim * (1 - bars)
    # The light spills over the bars and haloes past them (that's what makes it 'lustrous').
    I = glass + 0.35 * G.blur(glass, 5.0) * bars + 0.14 * G.blur(glass, 16.0)
    I = I / I.max()
    warm = G.lerp(np.array([226, 162, 80]), np.array([255, 246, 226]), G.smoothstep(0.15, 0.95, I)[..., None])
    rgb = warm * (I ** 0.9)[..., None]
    edge = np.minimum(np.minimum(X, W - X), np.minimum(Y, H - Y))
    rgb *= G.smoothstep(0, 5, edge)[..., None]
    G.save_png(np.clip(rgb, 0, 255), _tex("entity", "l39_window_glow"))


def tex_l39anom_window_blinds() -> None:
    """Old yellowed venetian blinds, half raised: 22 slats over the upper 70%, a bottom rail, the
    headrail, ladder strings and lift cords, pull cords with wooden acorns. Two slats are bent
    (one pulled down to peek), one sags, one is missing and one broke off."""
    rng = G.rng_for(39302)
    W, H = SH_W, SH_H
    shp = (H, W)
    X, Y = G.grid(shp)
    rgb, a = np.zeros(shp + (3,)), np.zeros(shp)
    cord = np.array([172, 160, 128])
    grime = 1 + 0.05 * G.fnoise(rng, shp, beta=2.4) + 0.025 * G.fnoise(rng, shp, beta=1.0, aniso=(10, 1), kmin=4)

    # Lift cords run through holes in the slats: painted first, so they only show in the gaps.
    for cx in (48.5, 143.5):
        cov = np.clip(1.0 - np.abs(X - cx) / 0.9, 0, 1) * G.box(shp, 0, 8, W, 228)
        rgb, a = _over(rgb, a, cord * 0.9, cov)

    top, pitch, thick = 11.0, (222.0 - 11.0) / 22, 7.4
    x1d = X[0]
    base = np.array([210, 193, 148])
    for i in range(22):
        y0 = top + i * pitch + rng.uniform(-0.3, 0.3)
        bend = np.zeros(W)
        th = np.full(W, thick)
        xa, xb = 4.0 + rng.uniform(-1, 1), 188.0 + rng.uniform(-1, 1)
        crease = np.zeros(W)
        if i == 14:
            continue                                                   # this slat is gone
        if i == 5:      # pulled down in the middle to peek through: a sharp V kink
            bend = 5.0 * np.clip(1 - np.abs(x1d - 92) / 20, 0, 1) ** 1.2
            crease = np.exp(-((x1d - 92) / 1.5) ** 2) + 0.6 * np.exp(-((np.abs(x1d - 92) - 20) / 1.5) ** 2)
        elif i == 10:   # the left end sags off its ladder
            bend = 6.0 * G.smoothstep(64, 4, x1d)
        elif i == 16:   # warped by damp
            bend = 1.2 * np.sin(x1d / 13.0 + 1.3)
        elif i == 2:    # twisted: tilts away towards one end, so it looks thinner and darker there
            th = thick - 3.2 * x1d / W
        elif i == 19:   # broken off: a jagged end two thirds along
            xb = 126.0
        t0 = y0 + bend[None, :]
        t1 = t0 + th[None, :]
        xcov = np.clip(np.minimum(X - xa, xb - X) + 0.5, 0, 1)
        if i == 19:
            jag = 126 + 4 * np.abs(np.sin((Y - y0) * 1.7))
            xcov = np.clip(np.minimum(X - xa, jag - X) + 0.5, 0, 1)
        cov = np.clip(np.minimum(Y - t0, t1 - Y) + 0.5, 0, 1) * xcov
        v = np.clip((Y - t0) / th[None, :], 0, 1)
        # A slat is a shallow curve: lit along its top, falling to a dark lower lip.
        shade = 1.06 - 0.10 * v - 0.24 * G.smoothstep(0.72, 1.0, v) + 0.07 * np.exp(-((v - 0.12) / 0.08) ** 2)
        if i == 2:
            shade = shade * (1 - 0.18 * x1d[None, :] / W)
        shade = shade * (1 - 0.35 * crease[None, :])
        tint = base * rng.uniform(0.93, 1.04) * np.array([1.0, rng.uniform(0.97, 1.01), rng.uniform(0.9, 1.0)])
        colr = tint * (shade * grime)[..., None]
        # Dust lying along the top of the slat, a little greyer.
        dust = G.smoothstep(0.18, 0.0, v) * (0.4 + 0.3 * G.fnoise(rng, shp, beta=1.2, aniso=(6, 1)))
        colr = G.lerp(colr, np.array([176, 172, 160]), np.clip(dust, 0, 1)[..., None] * 0.5)
        # Cord holes, darkened round the edges by years of the cord rubbing.
        for hx in (48.5, 143.5):
            yc = y0 + bend[int(hx)] + th[int(hx)] / 2
            hole = np.hypot((X - hx) / 1.4, (Y - yc) / 1.1)
            colr = colr * (1 - 0.65 * G.smoothstep(1.3, 0.6, hole) - 0.15 * G.smoothstep(3.5, 1.2, hole))[..., None]
        rgb, a = _over(rgb, a, colr, cov)

    # Nicotine-brown water stains near the right end of a few slats, and fly specks.
    stain = G.smoothstep(0.9, 1.9, G.fnoise(rng, shp, beta=3.4)) * G.smoothstep(120, 180, X) * (Y < 222)
    rgb = rgb * (1 - 0.22 * stain[..., None] * np.array([0.6, 0.85, 1.2]))
    specks = (rng.random(shp) < 0.006) * rng.uniform(0.3, 1.0, shp) * (Y < 222)
    rgb = rgb * (1 - 0.45 * G.blur(specks, 0.5)[..., None] * 2)

    # Ladder strings down the front of the slats.
    for lx in (40.5, 151.5):
        cov = np.clip(1.0 - np.abs(X - lx) / 0.7, 0, 1) * 0.9 * G.box(shp, 0, 8, W, 226)
        rgb, a = _over(rgb, a, cord * grime[..., None] * 0.95, cov)

    def rail(y0, y1, slope, colour, rng_rail):
        tt = y0 + slope * X / W
        bb = y1 + slope * X / W
        cov = np.clip(np.minimum(Y - tt, bb - Y) + 0.5, 0, 1) * G.box(shp, 1, 0, W - 1, H)
        v = np.clip((Y - tt) / (bb - tt), 0, 1)
        shade = 1.08 - 0.08 * v - 0.3 * G.smoothstep(0.7, 1.0, v) + 0.1 * np.exp(-((v - 0.2) / 0.1) ** 2)
        scuff = 1 - 0.12 * G.smoothstep(0.8, 2.0, G.fnoise(rng_rail, shp, beta=1.4, aniso=(8, 1)))
        return np.asarray(colour, float) * (shade * scuff * grime)[..., None], cov

    # Headrail (painted steel channel with end brackets and rust) and the heavier bottom rail.
    hc, hcov = rail(0.0, 10.0, 0.0, (198, 190, 166), rng)
    hc = hc * (1 - 0.25 * np.exp(-((Y - 6.5) / 0.6) ** 2))[..., None]
    rust = G.smoothstep(1.0, 2.2, G.fnoise(rng, shp, beta=2.0)) * (Y < 10)
    hc = G.lerp(hc, np.array([118, 72, 40]), (0.7 * rust)[..., None])
    rgb, a = _over(rgb, a, hc, hcov)
    for bx in (0.0, W - 6.0):
        rgb, a = _over(rgb, a, np.array([92, 90, 88]) * grime[..., None], G.box(shp, bx, 0, bx + 6, 12))
    bc, bcov = rail(223.0, 233.0, 2.0, (194, 178, 134), rng)
    rgb, a = _over(rgb, a, bc, bcov)
    for bx in (1.0, W - 7.0):
        caps = G.box(shp, bx, 222, bx + 6, 236) * bcov.max()
        rgb, a = _over(rgb, a, np.array([74, 56, 40]) * grime[..., None], caps * G.smoothstep(219, 223, Y))

    # Pull cords hanging in front, finger-greyed at the bottom, ending in wooden acorns.
    for cx, end, ph in ((174.5, 284.0, 0.0), (179.0, 293.0, 1.7)):
        sway = cx + 1.2 * np.sin(Y / 40 + ph) * G.smoothstep(10, end, Y)
        cov = np.clip(1.1 - np.abs(X - sway), 0, 1) * G.box(shp, 0, 9, W, end)
        dirt = G.lerp(cord, np.array([110, 100, 82]), G.smoothstep(200, end, Y)[..., None])
        rgb, a = _over(rgb, a, dirt, cov)
        ax = cx + 1.2 * np.sin(end / 40 + ph)
        acorn = np.hypot((X - ax) / 3.6, (Y - end - 5) / 6.0)
        av = np.clip(1.0 - acorn, 0, 1)
        acol = np.array([118, 82, 50]) * (0.7 + 0.5 * G.smoothstep(0.9, 0.0, np.hypot((X - ax + 1.2) / 2, (Y - end - 3) / 3)))[..., None]
        rgb, a = _over(rgb, a, acol, np.clip((1 - acorn) * 3, 0, 1) * (av > 0))
    G.save_png(np.concatenate([np.clip(rgb, 0, 255), 255 * a[..., None]], axis=2), _tex("entity", "l39_window_blinds"))


def tex_l39anom_hydrolitis_icon() -> None:
    """18x18 effect icon in the vanilla style: a crisp pixel water drop with a dark outline, a
    sickly turquoise body, a green bacterial film settling in the bottom and dark specks."""
    rng = G.rng_for(39303)
    n = 18
    cx, cy, rad, tip = 9.0, 11.3, 5.4, 1.4

    def inside(X, Y):
        circle = (X - cx) ** 2 + (Y - cy) ** 2 <= rad ** 2
        # The pointed top: a triangle from the tip down to where it meets the circle.
        k = (Y - tip) / (cy - tip)
        cone = (Y >= tip) & (Y <= cy) & (np.abs(X - cx) <= k * rad * 0.98)
        return circle | cone

    m = G.supersample((n, n), inside, ss=8) > 0.45
    nb = np.zeros_like(m)
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            nb |= np.roll(np.roll(m, dy, 0), dx, 1)
    outline = nb & ~m
    X, Y = G.grid((n, n))
    # Light from the top left: bright cyan there, deep teal bottom right.
    lit = np.clip(((X - cx) * 0.6 + (Y - cy) * 0.8) / 7 + 0.5, 0, 1)
    body = G.lerp(np.array([92, 214, 198]), np.array([24, 112, 116]), lit[..., None])
    # A shade ring just inside the outline, like vanilla's bevel.
    inner = np.zeros_like(m)
    for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        inner |= ~np.roll(np.roll(m, dy, 0), dx, 1)
    inner &= m
    body = np.where((inner & (lit > 0.45))[..., None], body * 0.78, body)
    film = (Y > 13.0) & m
    body = np.where(film[..., None], G.lerp(body, np.array([118, 164, 58]), 0.7), body)
    img = np.zeros((n, n, 4))
    img[m, :3] = body[m]
    img[m, 3] = 255
    img[outline, :3] = (16, 40, 44)
    img[outline, 3] = 255
    # Highlight: a short glint up the left shoulder.
    for x, y, c in ((6, 9, (214, 255, 246)), (6, 10, (190, 246, 236)), (7, 7, (170, 240, 228))):
        if m[y, x]:
            img[y, x, :3] = c
    # Bacteria specks (dark) and a couple of pale green blooms.
    for x, y, c in ((10, 10, (40, 92, 60)), (12, 12, (52, 100, 44)), (8, 14, (62, 98, 34)),
                    (11, 15, (54, 86, 30)), (9, 12, (150, 200, 96)), (12, 14, (168, 210, 90))):
        if m[y, x] and not outline[y, x]:
            img[y, x, :3] = c
    img[..., :3] += (rng.normal(0, 3, (n, n, 1)) * m[..., None])
    G.save_png(img, _tex("mob_effect", "hydrolitis"))


def _typewriter(px: int):
    for name in ("courbd.ttf", "cour.ttf"):
        try:
            return ImageFont.truetype(name, px)
        except OSError:
            continue
    return G.font(px)


def tex_l39anom_survey_note() -> None:
    """A water-stained M.E.G. field survey page on a clipboard, for the note-reading screen. The
    typed letterhead sits top left; the ruled middle stays light and clean enough to read text
    drawn over it. Stains, coffee rings and foxing keep to the edges."""
    rng = G.rng_for(39304)
    N = 256
    shp = (N, N)
    X, Y = G.grid(shp)
    rgb, a = np.zeros(shp + (3,)), np.zeros(shp)

    # The clipboard: brown hardboard, speckled fibres, edges worn pale, rounded corners.
    def rounded(x0, y0, x1, y1, r):
        return G.pil_mask(lambda d, k: d.rounded_rectangle((x0 * k, y0 * k, x1 * k, y1 * k), r * k, fill=255), shp)

    board = rounded(16, 6, 240, 254, 10)
    bcol = np.array([112, 78, 48]) * (1 + 0.06 * G.fnoise(rng, shp, beta=2.2) + 0.05 * G.fnoise(rng, shp, beta=0.8, kmin=20))[..., None]
    bedge = np.minimum(np.minimum(X - 16, 240 - X), np.minimum(Y - 6, 254 - Y))
    bcol = G.lerp(bcol, np.array([150, 116, 80]), (0.5 * G.smoothstep(3, 0, bedge) * G.smoothstep(-0.5, 1.2, G.fnoise(rng, shp, beta=1.6)))[..., None])
    rgb, a = _over(rgb, a, bcol, board)

    # The paper: off-white, faint fibre, yellower towards its edges, a soft shadow on the board.
    sx0, sy0, sx1, sy1 = 28.0, 18.0, 228.0, 248.0
    sheet = G.box(shp, sx0, sy0, sx1, sy1)
    shadow = G.blur(G.box(shp, sx0 + 2, sy0 + 3, sx1 + 2, sy1 + 3), 2.0) * (1 - sheet)
    rgb = rgb * (1 - 0.45 * shadow[..., None])
    paper = np.array([234, 228, 210]) * (1 + 0.012 * G.fnoise(rng, shp, beta=1.0, kmin=20) + 0.02 * G.fnoise(rng, shp, beta=2.6))[..., None]
    sedge = np.minimum(np.minimum(X - sx0, sx1 - X), np.minimum(Y - sy0, sy1 - Y))
    paper = G.lerp(paper, np.array([214, 198, 160]), (0.55 * G.smoothstep(14, 0, sedge))[..., None])
    # Faint pencil grid underneath everything (a surveyor's habit), very light.
    gx = np.minimum((X - sx0) % 10, 10 - (X - sx0) % 10)
    gy = np.minimum((Y - sy0) % 10, 10 - (Y - sy0) % 10)
    pencil = np.clip(1 - np.minimum(gx, gy) / 0.6, 0, 1) * (0.5 + 0.5 * G.smoothstep(-0.5, 1.0, G.fnoise(rng, shp, beta=2.0)))
    paper = G.lerp(paper, np.array([150, 148, 146]), (0.07 * pencil)[..., None])
    # Ruled lines and the red margin, faded.
    rule = np.zeros(shp)
    for y in np.arange(70.0, sy1 - 8, 12.0):
        rule = np.maximum(rule, np.clip(1 - np.abs(Y - y) / 0.6, 0, 1))
    rule *= G.box(shp, sx0 + 4, 0, sx1 - 4, N)
    paper = G.lerp(paper, np.array([128, 150, 182]), (0.42 * rule)[..., None])
    margin = np.clip(1 - np.abs(X - 50.5) / 0.6, 0, 1) * G.box(shp, 0, sy0 + 2, N, sy1 - 2)
    paper = G.lerp(paper, np.array([196, 104, 100]), (0.4 * margin)[..., None])

    # Typed letterhead: uneven typewriter ink (some strikes harder than others), a double rule.
    def typed(text, x, y, px):
        def draw(d, k):
            f = _typewriter(px * k)
            cx = x * k
            for ch in text:
                d.text((cx, y * k + rng.uniform(-0.3, 0.3) * k), ch, fill=int(rng.uniform(170, 255)), font=f)
                cx += d.textlength(ch, font=f)
        return G.pil_mask(draw, shp)

    ink = typed("M.E.G. FIELD SURVEY - LEVEL 39", 36, 28, 10)
    ink = np.maximum(ink, typed("SECTOR: ____   DATE: __/__/__", 36, 42, 8) * 0.8)
    ink *= 0.8 + 0.2 * G.smoothstep(-1, 1, G.fnoise(rng, shp, beta=1.0, kmin=12))
    hr = (np.clip(1 - np.abs(Y - 55.5) / 0.6, 0, 1) + 0.7 * np.clip(1 - np.abs(Y - 57.5) / 0.5, 0, 1)) * G.box(shp, 36, 0, 220, N)
    paper = G.lerp(paper, np.array([30, 30, 36]), np.clip(ink * 0.92 + hr * 0.6, 0, 1)[..., None])

    # Water damage: tide-marked stains creeping in from a corner and the left edge (dark rim,
    # faint inside).
    for (cx, cy, sz, seed_amp) in ((214, 236, 52, 1.0), (26, 128, 30, 0.8)):
        field = np.hypot(X - cx, Y - cy) / sz + 0.2 * G.fnoise(rng, shp, beta=3.4) + 0.03 * G.fnoise(rng, shp, beta=1.6, kmin=8)
        inside = G.smoothstep(1.0, 0.92, field)
        rim = np.exp(-((field - 1.0) / 0.035) ** 2)
        paper = paper * (1 - (0.06 * inside + 0.16 * rim * seed_amp)[..., None] * np.array([0.7, 0.9, 1.4]))
    # Coffee rings: a mug set down twice, rings thicker on one side, a faint inner wash.
    for cx, cy, rr, amt in ((196, 206, 21.0, 1.0), (182, 220, 20.0, 0.55), (58, 222, 15.0, 0.4)):
        d = np.hypot(X - cx, Y - cy)
        ang = np.arctan2(Y - cy, X - cx)
        width = 1.2 + 0.9 * (0.5 + 0.5 * np.cos(ang - rng.uniform(0, 6.28)))
        ringm = np.exp(-((d - rr) / width) ** 2) * (0.6 + 0.4 * G.smoothstep(-0.8, 0.8, G.fnoise(rng, shp, beta=1.6)))
        wash = G.smoothstep(rr, rr - 3, d) * 0.25
        paper = G.lerp(paper, np.array([150, 104, 62]), (amt * (0.5 * ringm + 0.18 * wash))[..., None])
    # Foxing: small rusty spots with soft haloes, mostly near the edges.
    edgew = G.smoothstep(40, 6, sedge)
    spots = (rng.random(shp) < 0.0005 + 0.012 * edgew) * rng.uniform(0.3, 1.0, shp)
    fox = np.clip(G.blur(spots, 0.6) * 4 + G.blur(spots, 2.0) * 6, 0, 1)
    paper = G.lerp(paper, np.array([168, 120, 76]), (0.45 * fox)[..., None])
    rgb, a = _over(rgb, a, np.clip(paper, 0, 255), sheet)

    # The clip: dark steel plate with pressed ridges, two rivets, a wire lever, and its shadow.
    clip = rounded(84, 2, 172, 30, 6)
    rgb = rgb * (1 - 0.4 * (G.blur(rounded(85, 5, 173, 34, 6), 2.0) * (1 - clip))[..., None])
    steel = np.array([66, 68, 74]) * (1 + 0.05 * G.fnoise(rng, shp, beta=1.2, aniso=(6, 1)))[..., None]
    shade = 1.25 - 0.5 * G.smoothstep(2, 30, Y) + 0.25 * np.exp(-((Y - 9) / 2.0) ** 2)
    shade = shade - 0.25 * (np.exp(-((Y - 18) / 0.8) ** 2) + np.exp(-((Y - 22) / 0.8) ** 2)) * G.box(shp, 94, 0, 162, N)
    steel = steel * shade[..., None]
    rust = G.smoothstep(1.1, 2.3, G.fnoise(rng, shp, beta=2.2))
    steel = G.lerp(steel, np.array([110, 70, 44]), (0.6 * rust)[..., None])
    rgb, a = _over(rgb, a, steel, clip)
    for rx in (98.0, 158.0):
        rv = np.hypot(X - rx, Y - 24)
        rcol = np.array([150, 150, 156]) * (1 - 0.5 * G.smoothstep(0, 3, np.hypot(X - rx + 0.8, Y - 23.2)))[..., None]
        rgb, a = _over(rgb, a, rcol, np.clip(3.0 - rv, 0, 1))
    wire = np.abs(np.hypot((X - 128) / 30, (Y - 8) / 6) - 1.0) * 6
    wire_cov = np.clip(1.4 - wire, 0, 1) * (Y < 9)
    rgb, a = _over(rgb, a, np.array([44, 44, 48]) * (1 + 0.3 * np.exp(-((Y - 3) / 1.5) ** 2))[..., None], wire_cov)
    G.save_png(np.concatenate([np.clip(rgb, 0, 255), 255 * a[..., None]], axis=2), _tex("gui", "l39_survey_note"))


# =============================================================================
# Sound helpers
# =============================================================================

def _t(dur):
    return np.arange(int(dur * SR)) / SR


def _env(t, pts):
    tt, vv = zip(*pts)
    return np.interp(t, tt, vv)


def _band(rng, n, lo, hi, order=2):
    return G.norm(G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, lo, hi, order)))


def _trim(x, dur):
    return x[:int(dur * SR)]


def _clicks(rng, times, m, f_lo, f_hi, d_lo, d_hi):
    """G.clicks into a buffer of exactly m samples (seconds -> samples rounding can lose one)."""
    return G.clicks(rng, times, m / SR + 0.01, f_lo, f_hi, d_lo, d_hi)[:m]


def _room(rng, x, length=0.9, decay=0.25, lp=2600.0, wet=0.4, predelay=0.004):
    """A small room (a house, a tent): short, boxy reverb."""
    ir = G.reverb_ir(rng, length, decay, lp=lp, predelay=predelay)
    wet_sig = G.conv(x, ir)
    out = np.concatenate([x, np.zeros(len(wet_sig) - len(x))])
    return out + wet * G.norm(wet_sig) * np.abs(x).max()


def _shaft(rng, x, lp=1800.0, flutter=0.018, tail=2.2, decay=0.8, wet=0.9, dry=0.5):
    """A sound coming up a deep narrow hole: the highs are lost on the way, the close walls throw
    a fast 'flutter' of echoes (which gives the hollow, pipe-like ring), then a long dark tail."""
    x = A._far(x, lp, 60)
    m = int(tail * SR)
    flut = np.zeros(m)
    for k in range(1, 40):
        i = int(round(k * flutter * SR * (1 + 0.04 * rng.normal())))
        if i < m:
            flut[i] += 0.74 ** k
    flut = G.fft_filter(flut, lambda f: G.lp_gain(f, lp * 0.6, 1))
    flut /= np.sqrt((flut ** 2).sum()) + 1e-12
    ir = 0.7 * flut + 0.7 * G.reverb_ir(rng, tail, decay, lp=lp * 0.5, predelay=0.04)
    wet_sig = G.conv(x, ir)
    out = np.concatenate([x, np.zeros(len(wet_sig) - len(x))])
    return dry * out + wet * G.norm(wet_sig) * np.abs(x).max()


def _tink(rng, f, decay):
    """One glass shard ringing: a few inharmonic partials and a tiny click of the hit."""
    r = rng.uniform
    dur = min(decay * 6, 0.6)
    x = G.ring([f, f * r(1.47, 1.62), f * r(2.2, 2.5), f * r(3.3, 3.8)],
               [decay, decay * 0.7, decay * 0.5, decay * 0.35], [1.0, 0.6, 0.35, 0.2], dur)
    k = int(0.0015 * SR)
    x[:k] += rng.normal(size=k) * np.exp(-np.arange(k) / (0.0004 * SR)) * 0.8
    return x


def _clink(rng, f):
    """A glass bottle knocking another: bright bottle-wall modes plus a faint hollow body."""
    x = G.ring([f, f * 2.27, f * 3.9, rng.uniform(190, 260)], [0.16, 0.09, 0.05, 0.05], [1.0, 0.45, 0.2, 0.12], 0.6)
    k = int(0.002 * SR)
    x[:k] += rng.normal(size=k) * 0.5
    return x


def _heart(rng, n, bpm, jitter=0.03, start=0.05):
    """Muffled 'lub-dub' heartbeats, heard from inside your own head."""
    out = np.zeros(n)
    tb = start
    dur = n / SR
    while tb < dur - 0.1:
        period = 60.0 / (bpm * (1 + rng.normal(0, jitter)))
        for off, g, f0 in ((0.0, 1.0, 34.0), (0.12, 0.65, 44.0)):
            tt = _t(0.25)
            th = np.sin(2 * np.pi * np.cumsum(f0 + 22 * np.exp(-tt / 0.03)) / SR) * np.exp(-tt / 0.065)
            G.add_at(out, g * th, tb + off, False)
        tb += period
    return G.fft_filter(out, lambda f: G.lp_gain(f, 150, 2))


# =============================================================================
# The mill windows (Entity 2)
# =============================================================================

def snd_l39anom_window_wake() -> None:
    """~3 s: a mill window waking. A deep glassy swell (the pane 'singing' like a bowed glass),
    an electric hum and fizz rising with the light, all breathing in a slow pulse."""
    rng = G.rng_for(39400)
    dur = 3.2
    t = _t(dur)
    n = len(t)
    rise = G.smoothstep(0.0, 2.2, t)
    pulse = 0.72 + 0.28 * (0.5 - 0.5 * np.cos(2 * np.pi * 1.1 * t))
    f0 = 55.0 * (1 + 0.02 * rise)
    ph = 2 * np.pi * np.cumsum(f0) / SR
    drone = sum(np.sin(k * ph + 0.4 * k) / k ** 1.3 for k in range(1, 6))
    glass = np.zeros(n)
    for f, g in ((742.0, 1.0), (1713.0, 0.5), (3060.0, 0.22)):
        # Two slightly detuned copies beat slowly against each other, like a rubbed glass rim.
        glass += g * (np.sin(2 * np.pi * f * t) + np.sin(2 * np.pi * (f + 0.7) * t + 1.0))
    glass *= rise ** 1.6 * (1 + G.smooth_jitter(rng, n, 4, 0.2))
    hum = G.mains_hum(t, 60.0) * rise
    buzz = G.fft_filter(np.sign(np.sin(2 * np.pi * 120 * t)), lambda f: G.bp_gain(f, 400, 2400, 2)) * rise ** 2
    fizz = G._crackle(rng, dur, 26, 3000) * rise
    sub = _band(rng, n, 25, 110) * rise
    x = (0.5 * G.norm(drone) + 0.32 * G.norm(glass) + 0.25 * G.norm(hum) + 0.06 * G.norm(buzz)
         + 0.08 * G.norm(fizz) + 0.3 * sub) * pulse
    x = A._hall(rng, x, decay=1.6, length=2.5, lp=2600, wet=0.5, slaps=((0.45, 0.2),))
    A._save_ogg(_trim(x, 3.6), _snd("window_wake"), peak=0.6, fade_in=0.2, fade_out=0.6)


def snd_l39anom_window_lure() -> None:
    """Two ~4 s takes: the window calling you closer. A soft warm chord pulsing like slow breath,
    a music box picking out a lullaby above it, a little flat, and a faint high shimmer."""
    takes = ((39401, (220.0, 277.2, 329.6, 440.0), (880.0, 1108.7, 1318.5, 1760.0, 1318.5, 1108.7, 987.8)),
             (39402, (196.0, 246.9, 293.7, 392.0), (784.0, 987.8, 1174.7, 1568.0, 1318.5, 1174.7, 987.8)))
    for i, (seed, chord, melody) in enumerate(takes, 1):
        rng = G.rng_for(seed)
        dur = 4.0
        t = _t(dur)
        n = len(t)
        pad = np.zeros(n)
        for f in chord:
            for det in (0.9985, 1.0015):
                ph = 2 * np.pi * f * det * t + rng.uniform(0, 6.28)
                pad += np.sin(ph) + 0.3 * np.sin(2 * ph) + 0.08 * np.sin(3 * ph)
        breathe = 0.55 + 0.45 * (0.5 - 0.5 * np.cos(2 * np.pi * 0.8 * t)) ** 1.5
        pad = G.norm(pad) * breathe * G.smoothstep(0, 0.8, t)
        box = np.zeros(n)
        at = 0.45
        for f in melody:
            f *= rng.choice([1.0, 0.993, 0.988])        # the music box is a little out of tune
            note = G.ring([f, f * 3.0 * 1.003, f * 5.8], [0.9, 0.3, 0.1], [1.0, 0.22, 0.07], 1.8)
            G.add_at(box, note * rng.uniform(0.6, 1.0), at, False)
            at += rng.uniform(0.38, 0.5)
        shimmer = sum(np.sin(2 * np.pi * f * t + rng.uniform(0, 6)) for f in (2637.0, 3520.0, 4186.0))
        shimmer *= (0.5 + 0.5 * np.sin(2 * np.pi * 0.6 * t + rng.uniform(0, 6))) * G.smoothstep(0.5, 2.0, t)
        x = 0.6 * pad + 0.3 * G.norm(box) + 0.04 * G.norm(shimmer)
        x = G.fft_filter(x, lambda f: G.lp_gain(f, 5000, 2))
        x = A._hall(rng, x, decay=1.8, length=2.5, lp=3000, wet=0.5, slaps=((0.45, 0.18),))
        A._save_ogg(_trim(x, 4.3), _snd(f"window_lure{i}"), peak=0.5, fade_in=0.3, fade_out=0.8)


def snd_l39anom_window_blinds() -> None:
    """Two ~1.5 s takes: venetian blind slats trembling against the glass: quick clattering
    ticks in shivering bursts, a faint glassy ring, the thin slats chattering together."""
    for i, seed in enumerate((39403, 39404), 1):
        rng = G.rng_for(seed)
        dur = 1.5
        t = _t(dur)
        n = len(t)
        tremble = (0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(rng.uniform(11, 14) * (1 + G.smooth_jitter(rng, n, 3, 0.2))) / SR)) ** 3
        dens = 160 * _env(t, [(0, 0), (0.12, 1.0), (0.9, 0.8), (1.4, 0)]) * (0.15 + tremble)
        times = t[rng.random(n) < dens / SR]
        ticks = G.clicks(rng, times, dur, 1400, 3800, 0.002, 0.006, amp_pow=1.5)
        ring = G.clicks(rng, times[::3], dur, 2800, 4600, 0.012, 0.03, amp_pow=2.0)
        clatter = G.clicks(rng, times + 0.003, dur, 600, 1400, 0.004, 0.01, amp_pow=1.5)
        rustle = _band(rng, n, 1000, 5000) * dens / 160
        x = 0.7 * G.norm(ticks) + 0.2 * G.norm(ring) + 0.4 * G.norm(clatter) + 0.12 * rustle
        x = _room(rng, x, 0.5, 0.12, 4000, 0.3)
        A._save_ogg(x, _snd(f"window_blinds{i}"), peak=0.5, fade_out=0.25)


def snd_l39anom_window_shriek() -> None:
    """~1.8 s: the Window Shadow lunging. A body thuds into the pane, something squeals across
    the glass like a wet finger, and an inhuman screech rises over it. Harsh, but filtered so
    it isn't ear-splitting."""
    rng = G.rng_for(39405)
    dur = 1.8
    t = _t(dur)
    n = len(t)
    thud = np.sin(2 * np.pi * np.cumsum(50 + 45 * np.exp(-t / 0.03)) / SR) * np.exp(-t / 0.1)
    thud += 0.4 * _band(rng, n, 200, 3000) * np.exp(-t / 0.01)
    pane = G.ring([1180, 2730, 4310], [0.35, 0.22, 0.14], [1.0, 0.6, 0.35], dur)
    fs = _env(t, [(0, 1900), (0.25, 2600), (0.9, 2300), (1.6, 2050)]) * (1 + G.smooth_jitter(rng, n, 20, 0.02))
    ph = 2 * np.pi * np.cumsum(fs) / SR
    stick = 0.6 + 0.4 * G.fft_filter(np.sign(np.sin(2 * np.pi * np.cumsum(70 * (1 + G.smooth_jitter(rng, n, 6, 0.3))) / SR)),
                                     lambda f: G.lp_gain(f, 400, 1))
    squeal = (np.sin(ph) + 0.4 * np.sin(2 * ph)) * stick * _env(t, [(0, 0), (0.04, 1), (0.6, 0.7), (1.1, 0.3), (1.5, 0)])
    scr = G._scream(rng, 1.6, [(0, 380), (0.15, 720), (0.7, 640), (1.2, 520), (1.6, 300)],
                    [(0, 700), (0.2, 1100), (1.6, 800)], [(0, 1500), (0.3, 2300), (1.6, 1700)],
                    [(0, 0), (0.06, 1), (0.8, 0.85), (1.3, 0.4), (1.6, 0)],
                    drive=3.0, bits=10, hold=1, detune=1.045, sub=0.5)
    screech = np.zeros(n)
    G.add_at(screech, scr, 0.08, False)
    x = 0.7 * G.norm(thud) + 0.2 * G.norm(pane) + 0.3 * G.norm(squeal) + 0.85 * G.norm(screech)
    x = G.fft_filter(x, lambda f: G.lp_gain(f, 6000, 3) * G.hp_gain(f, 35, 1))
    x = A._hall(rng, x, decay=1.2, length=1.6, lp=3000, wet=0.35, slaps=((0.42, 0.18),))
    A._save_ogg(_trim(x, 2.2), _snd("window_shriek"), peak=0.85, fade_in=0.002, fade_out=0.35)


def snd_l39anom_glass_shards() -> None:
    """Two ~1.2 s takes: the pane bursting outwards. A sharp crack, shards whistling past (each
    one a falling-pitch whoosh), then the spray hitting the ground and tinkling to a stop."""
    for i, seed in enumerate((39406, 39407), 1):
        rng = G.rng_for(seed)
        dur = 1.2
        t = _t(dur)
        n = len(t)
        x = np.zeros(n)
        crack = _band(rng, n, 400, 12000, 1) * np.exp(-t / 0.008) + 0.3 * G.ring([3100, 5200, 7400], [0.05, 0.03, 0.02], [1, 0.6, 0.4], dur)
        x += 0.9 * crack
        for _ in range(int(rng.integers(4, 7))):
            m = int(rng.uniform(0.12, 0.25) * SR)
            tt = np.arange(m) / SR
            fc = np.interp(tt, [0, tt[-1]], [rng.uniform(4200, 6000), rng.uniform(1800, 2600)])
            w = G.svf(rng.normal(size=m), fc, 12.0) * np.sin(np.pi * tt / tt[-1]) ** 2
            G.add_at(x, 0.5 * G.norm(w) * rng.uniform(0.5, 1.0), rng.uniform(0.02, 0.3), False)
        for _ in range(int(rng.integers(70, 95))):
            big = rng.random() < 0.15
            f = rng.uniform(1800, 3200) if big else rng.uniform(3000, 9000)
            decay = rng.uniform(0.04, 0.09) if big else rng.uniform(0.008, 0.035)
            at = 0.14 + 0.85 * rng.random() ** 2.2
            G.add_at(x, _tink(rng, f, decay) * rng.uniform(0.15, 0.6) * (1.4 - at), at, False)
        for at in (0.2 + rng.uniform(0, 0.05), 0.36 + rng.uniform(0, 0.05)):
            G.add_at(x, 0.5 * _band(rng, int(0.06 * SR), 1500, 9000) * np.exp(-np.arange(int(0.06 * SR)) / (0.012 * SR)), at, False)
        x = _room(rng, x, 0.8, 0.2, 6000, 0.3)
        A._save_ogg(x, _snd(f"glass_shards{i}"), peak=0.85, fade_in=0.001, fade_out=0.2)


def snd_l39anom_window_grab() -> None:
    """~1.5 s: something seizes you through the glass. The pane groans and ticks under the
    strain, a wet hand clamps on (squelch and slap), then you're dragged, skin squeaking."""
    rng = G.rng_for(39408)
    dur = 1.5
    t = _t(dur)
    n = len(t)
    x = np.zeros(n)
    strain = G._creak(rng, 0.9, 40, 140, [1800, 3100, 4700])
    G.add_at(x, 0.35 * G.norm(strain), 0.0, False)
    ticks = G.clicks(rng, np.sort(rng.uniform(0.1, 0.7, 12)), dur, 3000, 7000, 0.001, 0.003, amp_pow=1.5)
    x += 0.4 * G.norm(ticks)
    crack = _band(rng, int(0.05 * SR), 2000, 9000) * np.exp(-np.arange(int(0.05 * SR)) / (0.006 * SR))
    G.add_at(x, 0.5 * crack, 0.55, False)
    G.add_at(x, 0.2 * _tink(rng, 3400, 0.08), 0.55, False)
    x += 0.8 * G._water_noise(rng, n, 0.45, 0.02, 0.08, 300, 2200, gurgle=1.2)
    slap = _band(rng, int(0.04 * SR), 200, 1600) * np.exp(-np.arange(int(0.04 * SR)) / (0.008 * SR))
    G.add_at(x, 0.9 * slap, 0.44, False)
    drag_env = _env(t, [(0, 0), (0.6, 0), (0.75, 1), (1.25, 0.8), (1.45, 0)])
    rate = 30 * (1 + G.smooth_jitter(rng, n, 4, 0.4))
    stick = 0.5 + 0.5 * G.fft_filter(np.sign(np.sin(2 * np.pi * np.cumsum(rate) / SR)), lambda f: G.lp_gain(f, 200, 1))
    x += 0.5 * _band(rng, n, 300, 2500) * stick * drag_env
    sq = np.sin(2 * np.pi * np.cumsum(_env(t, [(0, 900), (1.1, 700)])) / SR) * _env(t, [(0, 0), (0.78, 0), (0.85, 1), (1.05, 0.6), (1.12, 0)])
    x += 0.15 * sq * (0.6 + 0.4 * stick)
    x += 0.4 * np.sin(2 * np.pi * 70 * t) * np.exp(-np.maximum(t - 1.3, 0) / 0.08) * (t > 1.3)
    x = _room(rng, x, 0.5, 0.12, 4000, 0.3)
    A._save_ogg(x, _snd("window_grab"), peak=0.75, fade_out=0.2)


def snd_l39anom_window_fumes() -> None:
    """~2.5 s: acrid gas venting from the window frames. A seal pops, then a long sputtering hiss
    with a fizz in it, sinking slowly."""
    rng = G.rng_for(39409)
    dur = 2.5
    t = _t(dur)
    n = len(t)
    env = _env(t, [(0, 0), (0.08, 1), (0.4, 0.85), (1.8, 0.6), (2.5, 0)])
    pop = np.sin(2 * np.pi * np.cumsum(90 + 60 * np.exp(-t / 0.01)) / SR) * np.exp(-t / 0.03)
    pop += 0.5 * _band(rng, n, 800, 6000) * np.exp(-t / 0.006)
    sputter = np.clip(1 + 0.35 * G.smooth_jitter(rng, n, 25, 1.0), 0.3, None)
    hiss = _band(rng, n, 2500, 10000) * sputter * env
    whoosh = _band(rng, n, 300, 1800) * env
    fizz = G.norm(G._crackle(rng, dur, 50, 1500)) * env
    x = 0.5 * G.norm(pop) + 0.8 * hiss + 0.3 * whoosh + 0.15 * fizz
    x = _room(rng, x, 0.6, 0.15, 6000, 0.25)
    A._save_ogg(_trim(x, 2.8), _snd("window_fumes"), peak=0.55, fade_in=0.002, fade_out=0.4)


def snd_l39anom_window_noclip() -> None:
    """~2.5 s: pushed THROUGH the glass. A crystalline crunch that slows and drops in pitch as
    the world closes over you, then a muffled underwater whoosh, low pressure and bubbles."""
    rng = G.rng_for(39410)
    dur = 2.6
    t = _t(dur)
    n = len(t)
    x = np.zeros(n)
    dens = _env(t, [(0, 400), (0.4, 160), (0.9, 25), (1.1, 0)])
    for tt in t[rng.random(n) < dens / SR]:
        f = np.interp(tt, [0, 0.9], [6000, 1800]) * rng.uniform(0.7, 1.3)
        decay = np.interp(tt, [0, 0.9], [0.006, 0.04]) * rng.uniform(0.6, 1.4)
        G.add_at(x, _tink(rng, f, decay) * rng.uniform(0.2, 0.8), tt, False)
    x = G.norm(x) + 0.6 * _band(rng, n, 1000, 6000) * _env(t, [(0, 1), (0.5, 0.5), (1.0, 0)])
    whoosh = _band(rng, n, 80, 1200) * _env(t, [(0, 0), (0.5, 0.2), (1.3, 1.0), (2.6, 0.4)])
    bubbles = G._water_bubbles(rng, n, 40, 1.0, 1.2, 200, 900, 0.5)
    pressure = np.sin(2 * np.pi * 38 * t) * G.smoothstep(0.8, 1.8, t)
    x = 0.9 * x + 0.7 * whoosh + 0.3 * G.norm(bubbles) + 0.25 * pressure
    # Everything is pulled under: the top end closes from bright to dull over a second.
    fc = _env(t, [(0, 6500), (0.6, 5000), (1.4, 600), (2.6, 300)])
    x = G.svf(x, fc, 0.8, mode="lp")
    ir = G.reverb_ir(rng, 1.5, 0.5, lp=600)
    x = x + 0.5 * G.norm(G.conv(x, ir)[:n]) * np.abs(x).max()
    A._save_ogg(x, _snd("window_noclip"), peak=0.7, fade_in=0.002, fade_out=0.5)


def snd_l39anom_ash_smell() -> None:
    """Two ~2 s takes, very quiet: a dry faint rustle of coal ash drifting past, tiny flakes
    ticking down. Meant to be barely there."""
    for i, seed in enumerate((39411, 39412), 1):
        rng = G.rng_for(seed)
        dur = 2.0
        t = _t(dur)
        n = len(t)
        env = _env(t, [(0, 0), (0.5, 1), (1.4, 0.8), (2.0, 0)])
        flutter = np.clip(1 + G.smooth_jitter(rng, n, 3, 0.6) + G.smooth_jitter(rng, n, 20, 0.2), 0.1, None)
        rustle = _band(rng, n, 1500, 7000) * flutter * env
        times = t[rng.random(n) < 25 * env / SR]
        flakes = G.clicks(rng, times, dur, 4000, 9000, 0.0005, 0.0015, amp_pow=2.5)
        crinkle = np.zeros(n)
        for at in rng.uniform(0.3, 1.6, 5):
            m = int(0.01 * SR)
            G.add_at(crinkle, _band(rng, m, 800, 3000) * np.hanning(m) * rng.uniform(0.3, 1.0), at, False)
        x = 0.6 * rustle + 0.3 * G.norm(flakes) + 0.2 * G.norm(crinkle)
        x = _room(rng, x, 0.5, 0.15, 5000, 0.2)
        A._save_ogg(x, _snd(f"ash_smell{i}"), peak=0.22, fade_in=0.2, fade_out=0.4)


# =============================================================================
# The houses
# =============================================================================

def snd_l39anom_house_scream() -> None:
    """Three takes (1.5-3 s): a scream inside a house, heard through its walls. A woman (cut off
    mid-scream), a child's two-breath wail and an animal-like screech. Dulled by the walls,
    boxed in by the room, then carried off by the town's echo. It isn't real."""
    specs = [
        (39413, 2.4, [(0, 420), (0.12, 880), (0.5, 960), (1.2, 900), (1.7, 760), (2.4, 520)],
         [(0, 700), (0.15, 1000), (2.4, 850)], [(0, 1500), (0.2, 1650), (2.4, 1400)],
         [(0, 0), (0.08, 1), (1.4, 0.9), (1.68, 0.85), (1.74, 0), (2.4, 0)], 1.6, 9, 1.012, 0.15),
        (39414, 1.6, [(0, 600), (0.1, 1150), (0.5, 1250), (0.9, 1100), (1.3, 1300), (1.6, 900)],
         [(0, 900), (1.6, 1100)], [(0, 1900), (1.6, 2400)],
         [(0, 0), (0.06, 1), (0.6, 0.9), (0.72, 0.15), (0.85, 1), (1.45, 0.8), (1.6, 0)], 1.8, 9, 1.015, 0.1),
        (39415, 1.9, [(0, 300), (0.2, 620), (0.6, 780), (0.9, 560), (1.3, 700), (1.9, 340)],
         [(0, 800), (0.6, 1300), (1.9, 700)], [(0, 1600), (0.6, 2600), (1.9, 1300)],
         [(0, 0), (0.1, 1), (0.8, 0.9), (1.5, 0.6), (1.9, 0)], 4.0, 8, 1.07, 0.3),
    ]
    for i, (seed, dur, fp, f1, f2, ep, drive, bits, det, sub) in enumerate(specs, 1):
        rng = G.rng_for(seed)
        x = G._scream(rng, dur, fp, f1, f2, ep, drive=drive, bits=bits, hold=1, detune=det, sub=sub)
        x = np.concatenate([np.zeros(int(0.05 * SR)), x])
        # Through the walls: the highs and the deepest lows are gone.
        x = G.fft_filter(x, lambda f: G.lp_gain(f, 1000, 3) * G.hp_gain(f, 120, 1))
        x = _room(rng, x, 0.9, 0.28, 1800, 0.45)
        x = A._hall(rng, x, decay=1.6, length=2.0, lp=1400, wet=0.35, slaps=((0.5, 0.2),))
        A._save_ogg(_trim(x, dur + 0.9), _snd(f"house_scream{i}"), peak=0.6, fade_out=0.6)


def snd_l39anom_door_vanish() -> None:
    """~1.2 s, quiet: wood and plaster settling behind you as the door you came through stops
    being a door. A slow low creak, a soft settle, a trickle of plaster dust."""
    rng = G.rng_for(39416)
    dur = 1.2
    t = _t(dur)
    n = len(t)
    x = np.zeros(n)
    creak = G._creak(rng, 0.7, 9, 22, [rng.uniform(180, 240), rng.uniform(470, 560), rng.uniform(1050, 1250)])
    G.add_at(x, G.norm(creak), 0.05, False)
    G.add_at(x, 0.4 * G.norm(G._creak(rng, 0.25, 12, 26, [260, 640, 1400])), 0.8, False)
    x += 0.4 * np.sin(2 * np.pi * 70 * t) * np.exp(-np.maximum(t - 0.15, 0) / 0.08) * (t > 0.15)
    times = np.sort(rng.uniform(0.3, 1.0, 20))
    x += 0.25 * G.norm(G.clicks(rng, times, dur, 2500, 6000, 0.0006, 0.002, amp_pow=2.5))
    x += 0.06 * _band(rng, n, 2000, 7000) * _env(t, [(0, 0), (0.3, 0), (0.5, 1), (1.1, 0)])
    x = _room(rng, x, 0.8, 0.25, 2500, 0.35)
    A._save_ogg(_trim(x, 1.5), _snd("door_vanish"), peak=0.38, fade_in=0.02, fade_out=0.3)


def snd_l39anom_bigger_inside() -> None:
    """~4 s, subtle: the room tone of a space far too big for the house around it. A hollow
    low swell with huge, slow room modes, a far creak that takes ages to come back."""
    rng = G.rng_for(39417)
    dur = 4.2
    t = _t(dur)
    n = len(t)
    env = _env(t, [(0, 0), (1.5, 1), (2.8, 0.8), (4.2, 0)])
    base = rng.normal(size=n)
    modes = sum(G.norm(G.svf(base, f, 25.0)) * g for f, g in ((31, 1.0), (43, 0.8), (57, 0.6), (74, 0.4)))
    air = _band(rng, n, 200, 1200) * (1 + G.smooth_jitter(rng, n, 1.5, 0.4))
    x = (0.8 * G.norm(modes) + 0.15 * air + 0.3 * _band(rng, n, 25, 300)) * env
    far = np.zeros(n)
    G.add_at(far, 0.35 * G.norm(G._creak(rng, 0.5, 10, 24, [200, 480, 1100])), 0.8, False)
    G.add_at(far, 0.2 * G.ring([180, 420], [0.06, 0.03], [1, 0.5], 0.3), 1.9, False)
    x = x + A._far(far, 900)
    x = A._hall(rng, x, decay=3.0, length=4.0, lp=900, wet=0.9, slaps=((0.7, 0.35), (1.6, 0.2)))
    A._save_ogg(_trim(x, 4.6), _snd("bigger_inside"), peak=0.4, fade_in=0.5, fade_out=1.0)


# =============================================================================
# The holes in town (they go down to the tunnels, and things come up)
# =============================================================================

def snd_l39anom_hole_scrape() -> None:
    """Two ~2 s takes: claws scraping stone far down a shaft, a few rhythmic strokes like
    something digging, coming up hollow and fluttering."""
    for i, seed in enumerate((39418, 39419), 1):
        rng = G.rng_for(seed)
        dur = 1.6
        t = _t(dur)
        n = len(t)
        x = np.zeros(n)
        at = 0.05
        while at < dur - 0.4:
            d = rng.uniform(0.18, 0.35)
            m = int(d * SR)
            tt = np.arange(m) / SR
            stroke = np.zeros(m)
            for claw in range(3):      # three claws, a hair apart
                fc = np.interp(tt, [0, d], [rng.uniform(2300, 2800), rng.uniform(1600, 2000)])
                grate = G.svf(rng.normal(size=m), fc, 4.0)
                dens = rng.uniform(300, 600)
                ticks = _clicks(rng, tt[rng.random(m) < dens / SR], m, 1500, 5000, 0.0008, 0.002)
                g = np.roll(G.norm(grate) + 0.8 * G.norm(ticks), int(claw * 0.006 * SR))
                stroke += g
            stroke *= G.smoothstep(0, d * 0.6, tt) * G.smoothstep(d, d * 0.85, tt)
            G.add_at(x, G.norm(stroke) * rng.uniform(0.6, 1.0), at, False)
            at += d + rng.uniform(0.08, 0.3)
        x = _shaft(rng, x, lp=2200, flutter=rng.uniform(0.016, 0.02), tail=1.8, decay=0.7)
        A._save_ogg(_trim(x, 2.3), _snd(f"hole_scrape{i}"), peak=0.5, fade_in=0.01, fade_out=0.6)


def snd_l39anom_hole_breath() -> None:
    """~3 s: heavy, wet breathing rising out of a hole. Slow exhale, rasping inhale, exhale,
    with phlegmy crackle and a low voiced rasp, all ringing in the shaft."""
    rng = G.rng_for(39420)
    dur = 2.8
    t = _t(dur)
    n = len(t)
    x = np.zeros(n)
    for t0, d, f1, f2, voiced in ((0.0, 0.9, 500, 1300, True), (1.05, 0.75, 700, 1800, False), (1.9, 0.9, 480, 1250, True)):
        m = int(d * SR)
        tt = np.arange(m) / SR
        nz = rng.normal(size=m)
        b = G.norm(G.svf(nz, f1, 4.0)) + 0.6 * G.norm(G.svf(nz, f2, 5.0)) + 0.2 * G.norm(nz)
        e = G.smoothstep(0, d * 0.35, tt) * G.smoothstep(d, d * 0.55, tt)
        seg = G.norm(b) * e
        if voiced:
            f0 = 62 * (1 + G.smooth_jitter(rng, m, 8, 0.08))
            seg += 0.3 * G.norm(G.voice(f0, [(400, 120, 1.0), (900, 200, 0.5)], rng, nh=30)) * e
        wet = _clicks(rng, tt[rng.random(m) < 70 / SR], m, 300, 900, 0.003, 0.008)
        seg += 0.4 * G.norm(wet) * e
        G.add_at(x, seg, t0 + 0.05, False)
    x = _shaft(rng, x, lp=1600, flutter=0.019, tail=2.0, decay=0.8)
    A._save_ogg(_trim(x, 3.4), _snd("hole_breath"), peak=0.55, fade_in=0.05, fade_out=0.6)


def snd_l39anom_hole_growl() -> None:
    """~2.5 s: a low, guttural hound growl echoing up the shaft: a rough rattling voice under a
    snarl of breath, swelling and holding. Run."""
    rng = G.rng_for(39421)
    dur = 2.3
    t = _t(dur)
    n = len(t)
    f0 = _env(t, [(0, 62), (0.4, 78), (1.4, 84), (2.2, 66)]) * (1 + G.smooth_jitter(rng, n, 20, 0.06))
    v = G.voice(f0, [(380, 120, 1.0), (820, 180, 0.6), (1900, 350, 0.25)], rng, nh=60)
    rate = 28 * (1 + G.smooth_jitter(rng, n, 4, 0.3))
    am = 0.55 + 0.45 * (0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(rate) / SR)) ** 2
    snarl = _band(rng, n, 100, 1500) * am
    env = _env(t, [(0, 0), (0.35, 1), (1.6, 0.9), (2.1, 0.5), (2.3, 0)])
    x = G.dist(G.norm(G.norm(v) * am + 0.3 * snarl) * env, 2.5)
    x = _shaft(rng, x, lp=1400, flutter=0.021, tail=1.8, decay=0.7, dry=0.6)
    A._save_ogg(_trim(x, 3.0), _snd("hole_growl"), peak=0.7, fade_in=0.02, fade_out=0.6)


def snd_l39anom_hole_climb() -> None:
    """~2 s: something climbing fast out of a hole: claws scrabbling in a galloping rhythm that
    gets louder and drier as it nears the top, soil hissing and pebbles rattling back down."""
    rng = G.rng_for(39422)
    dur = 2.0
    t = _t(dur)
    n = len(t)
    near = np.zeros(n)
    at = 0.05
    while at < 1.85:
        rate = np.interp(at, [0, 1.8], [7.0, 10.0])
        g = np.interp(at, [0, 1.8], [0.25, 1.0])
        m = int(rng.uniform(0.05, 0.08) * SR)
        tt = np.arange(m) / SR
        sc = _band(rng, m, 1000, 5000) * np.exp(-tt / 0.02)
        sc += 0.7 * _clicks(rng, tt[rng.random(m) < 400 / SR], m, 1500, 4500, 0.0008, 0.002)
        sc += 0.6 * np.sin(2 * np.pi * 120 * tt) * np.exp(-tt / 0.015)
        G.add_at(near, G.norm(sc) * g * rng.uniform(0.7, 1.0), at, False)
        at += 1.0 / rate * rng.uniform(0.6, 1.3)
    # The last heave over the edge: one big scrape and a heavy thump.
    m = int(0.2 * SR)
    tt = np.arange(m) / SR
    G.add_at(near, 1.1 * G.norm(_band(rng, m, 600, 4000) * np.exp(-tt / 0.06)), 1.82, False)
    G.add_at(near, 0.9 * np.sin(2 * np.pi * np.cumsum(60 + 40 * np.exp(-tt / 0.02)) / SR) * np.exp(-tt / 0.07), 1.85, False)
    g = _env(t, [(0, 0.3), (1.8, 1.0), (2.0, 1.0)])
    soil = _band(rng, n, 200, 2500) * np.clip(1 + G.smooth_jitter(rng, n, 30, 1.0), 0, None) * g
    pebbles = np.zeros(n)
    for _ in range(10):
        p0 = rng.uniform(0.3, 1.9)
        f = rng.uniform(900, 3000)
        amp = rng.uniform(0.4, 1.0)
        gap = rng.uniform(0.06, 0.12)
        for b in range(int(rng.integers(2, 5))):
            G.add_at(pebbles, G.ring([f, f * 1.7], [0.01, 0.006], [1, 0.5], 0.06) * amp, p0, False)
            p0 += gap
            gap *= 0.7
            amp *= 0.55
    near = G.norm(near) + 0.25 * G.norm(soil)
    # Early on it's deep in the shaft (echoey), at the end it's right here (dry).
    wet_part = _shaft(rng, near * _env(t, [(0, 1.0), (1.6, 0.2), (2.0, 0.0)]), lp=2500, flutter=0.018, tail=1.2, decay=0.5, dry=0.0)
    x = np.concatenate([near, np.zeros(len(wet_part) - n)]) + 0.6 * wet_part
    x += 0.4 * G.norm(_shaft(rng, pebbles, lp=3000, flutter=0.018, tail=1.2, decay=0.5))[:len(x)]
    A._save_ogg(_trim(x, 2.5), _snd("hole_climb"), peak=0.7, fade_in=0.01, fade_out=0.4)


# =============================================================================
# Hydrolitis (first-person symptoms: all close and dry)
# =============================================================================

def snd_l39anom_hydrolitis_chills() -> None:
    """Two ~2.5 s takes: shivering with fever. A shaky breath out and a stuttering breath in,
    teeth chattering in bursts, a faint tremble in the chest."""
    for i, seed in enumerate((39423, 39424), 1):
        rng = G.rng_for(seed)
        dur = 2.5
        t = _t(dur)
        n = len(t)
        nz = rng.normal(size=n)
        breath = G.norm(G.svf(nz, 650, 2.0)) + 0.6 * G.norm(G.svf(nz, 1500, 3.0)) + 0.15 * _band(rng, n, 2000, 6000)
        shiver = 0.55 + 0.45 * np.sin(2 * np.pi * np.cumsum(9.0 * (1 + G.smooth_jitter(rng, n, 2, 0.2))) / SR)
        out_ = _env(t, [(0, 0), (0.15, 0), (0.35, 1), (1.0, 0.7), (1.25, 0)])
        stutter = 0.35 + 0.65 * (np.sin(2 * np.pi * 7 * t) > -0.2)
        in_ = _env(t, [(0, 0), (1.45, 0), (1.6, 0.8), (2.2, 0.7), (2.4, 0)]) * G.fft_filter(stutter, lambda f: G.lp_gain(f, 40, 1))
        x = G.norm(breath) * shiver * (out_ + 0.8 * in_) * 0.55
        chatter = np.zeros(n)
        for b0, b1 in ((0.25, 0.75), (1.0, 1.35), (1.7, 2.3)):
            at = b0 + rng.uniform(0, 0.05)
            while at < b1:
                for off in (0.0, rng.uniform(0.004, 0.008)):      # upper and lower teeth
                    c = G.ring([rng.uniform(2200, 3600), rng.uniform(4200, 5200)], [0.003, 0.002], [1, 0.4], 0.03)
                    c += 0.6 * G.ring([rng.uniform(700, 1000)], [0.006], [1], 0.03)
                    G.add_at(chatter, c * rng.uniform(0.5, 1.0), at + off, False)
                at += 1.0 / rng.uniform(13, 16)
        tremble = _band(rng, n, 30, 120) * (0.5 + 0.5 * shiver)
        x = x + 0.35 * G.norm(chatter) + 0.12 * tremble
        x = _room(rng, x, 0.3, 0.06, 4000, 0.1)
        A._save_ogg(_trim(x, 2.6), _snd(f"hydrolitis_chills{i}"), peak=0.5, fade_in=0.05, fade_out=0.3)


def snd_l39anom_hydrolitis_heartbeat() -> None:
    """~3 s: your heart racing in your ears (about 140 bpm), muffled, with the whoosh of blood
    after each beat and a thin fever ring behind it all."""
    rng = G.rng_for(39425)
    dur = 3.0
    t = _t(dur)
    n = len(t)
    heart = _heart(rng, n, 140)
    pulse = G.fft_filter(np.abs(heart), lambda f: G.lp_gain(f, 6, 1))
    whoosh = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 60, 400, 2)) * G.norm(pulse)
    ring = np.sin(2 * np.pi * 6200 * t) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.4 * t))
    x = G.norm(heart) + 0.25 * G.norm(whoosh) + 0.012 * ring
    A._save_ogg(x, _snd("hydrolitis_heartbeat"), peak=0.7, fade_in=0.3, fade_out=0.4)


def snd_l39anom_hydrolitis_retch() -> None:
    """~2 s: two dry heaves (guttural, creaky 'hurk's), then vomit gushing out and splattering
    on the ground, a few drips."""
    rng = G.rng_for(39426)
    dur = 2.1
    t = _t(dur)
    n = len(t)
    x = np.zeros(n)
    for t0, d, g in ((0.05, 0.32, 0.7), (0.55, 0.4, 1.0)):
        m = int(d * SR)
        tt = np.arange(m) / SR
        f0 = np.interp(tt, [0, d], [115, 80]) * (1 + G.smooth_jitter(rng, m, 25, 0.1))
        v = G.voice(f0, [(450, 150, 1.0), (1100, 200, 0.6), (2400, 400, 0.25)], rng, nh=40)
        creak = 0.5 + 0.5 * (np.sin(2 * np.pi * 35 * tt) > 0)
        throat = _band(rng, m, 300, 3000)
        e = G.smoothstep(0, 0.03, tt) * np.exp(-tt / (d * 0.5))
        heave = G.dist(G.norm(G.norm(v) * creak + 0.5 * throat) * e, 2.0)
        G.add_at(x, g * heave, t0, False)
    x += 0.9 * G._water_noise(rng, n, 1.0, 0.05, 0.35, 150, 2200, gurgle=1.5)
    m = int(0.35 * SR)
    choke = G.voice(90 * (1 + G.smooth_jitter(rng, m, 20, 0.1)), [(500, 150, 1.0), (1000, 200, 0.5)], rng, nh=30)
    G.add_at(x, 0.3 * G.norm(choke) * np.hanning(m), 1.0, False)
    x += 0.8 * G._water_noise(rng, n, 1.25, 0.005, 0.12, 400, 5000)
    x += G._water_bubbles(rng, n, 30, 1.25, 0.4, 500, 2500, 0.5)
    for at in (1.6, 1.78, 1.93):
        G.add_at(x, 0.25 * G._water_bubble(rng, rng.uniform(900, 1500), 0.08), at, False)
    x = _room(rng, x, 0.5, 0.12, 4000, 0.15)
    A._save_ogg(_trim(x, 2.3), _snd("hydrolitis_retch"), peak=0.6, fade_out=0.3)


def snd_l39anom_hydrolitis_whisper() -> None:
    """Two ~4 s takes of delirium: several faint whispers overlapping, unintelligible, smeared
    as if heard from under the water (a wobbling dull filter, a watery flange, bubbles)."""
    for i, seed in enumerate((39427, 39428), 1):
        rng = G.rng_for(seed)
        dur = 4.0
        t = _t(dur)
        n = len(t)
        x = np.zeros(n)
        for start, g in ((0.0, 1.0), (rng.uniform(0.3, 0.6), 0.7), (rng.uniform(0.8, 1.2), 0.5)):
            w = L4._whisper_breath(rng, dur - start, 0.14, 0.3)
            G.add_at(x, g * G.norm(w), start, False)
        fc = 700 + 1900 * (0.5 + 0.5 * np.sin(2 * np.pi * 0.5 * t + rng.uniform(0, 6))) + G.smooth_jitter(rng, n, 2, 300)
        x = G.svf(x, np.clip(fc, 300, None), 0.9, mode="lp")
        # Watery flange: the signal plus a copy delayed by a few wobbling milliseconds.
        idx = np.arange(n)
        delay = (0.004 + 0.0025 * np.sin(2 * np.pi * 0.3 * t)) * SR
        x = x + 0.7 * np.interp(idx - delay, idx, x, left=0)
        bubbles = G._water_bubbles(rng, n, 30, 0.2, 3.5, 300, 900, 0.4, skew=1.0)
        x = G.norm(x) + 0.15 * G.norm(bubbles)
        x = A._tunnel_room(rng, x, wet=0.4)
        A._save_ogg(x, _snd(f"hydrolitis_whisper{i}"), peak=0.32, fade_in=0.4, fade_out=0.6)


# =============================================================================
# The M.E.G. camp
# =============================================================================

def snd_l39anom_camp_stash() -> None:
    """~1.5 s: a crate lid creaking open on dry hinges, bottles knocking together inside,
    one sloshing a little, the lid knocking back against the side."""
    rng = G.rng_for(39429)
    dur = 1.5
    t = _t(dur)
    n = len(t)
    x = np.zeros(n)
    creak = G._creak(rng, 0.55, 18, 45, [rng.uniform(320, 420), rng.uniform(800, 950), 1700])
    G.add_at(x, 0.7 * G.norm(creak), 0.02, False)
    x += 0.12 * _band(rng, n, 500, 3000) * _env(t, [(0, 0), (0.05, 1), (0.55, 0.6), (0.6, 0)])
    m = int(0.2 * SR)
    knock = G.ring([150, 390], [0.04, 0.02], [1, 0.5], 0.2) + 0.4 * _band(rng, m, 200, 2500) * np.exp(-np.arange(m) / (0.01 * SR))
    G.add_at(x, 0.7 * G.norm(knock), 0.62, False)
    for at in np.sort(rng.uniform(0.4, 1.25, 6)):
        f1, f2 = rng.uniform(1500, 2600), rng.uniform(1500, 2600)
        G.add_at(x, 0.4 * (_clink(rng, f1) + 0.7 * _clink(rng, f2)) * rng.uniform(0.5, 1.0), at, False)
    x += 0.25 * G._water_noise(rng, n, 0.85, 0.05, 0.15, 300, 1500, gurgle=1.0)
    x = _room(rng, x, 0.5, 0.12, 5000, 0.2)
    A._save_ogg(_trim(x, 1.7), _snd("camp_stash"), peak=0.6, fade_out=0.25)


TEXTURES = [tex_l39anom_window_shadow, tex_l39anom_window_glow, tex_l39anom_window_blinds,
            tex_l39anom_hydrolitis_icon, tex_l39anom_survey_note]
SOUNDS = [snd_l39anom_window_wake, snd_l39anom_window_lure, snd_l39anom_window_blinds,
          snd_l39anom_window_shriek, snd_l39anom_glass_shards, snd_l39anom_window_grab,
          snd_l39anom_window_fumes, snd_l39anom_window_noclip, snd_l39anom_ash_smell,
          snd_l39anom_house_scream, snd_l39anom_door_vanish, snd_l39anom_bigger_inside,
          snd_l39anom_hole_scrape, snd_l39anom_hole_breath, snd_l39anom_hole_growl,
          snd_l39anom_hole_climb, snd_l39anom_hydrolitis_chills, snd_l39anom_hydrolitis_heartbeat,
          snd_l39anom_hydrolitis_retch, snd_l39anom_hydrolitis_whisper, snd_l39anom_camp_stash]
