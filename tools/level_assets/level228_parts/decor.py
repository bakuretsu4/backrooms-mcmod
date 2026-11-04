"""Level 228's lights and decor: hanging bulbs, candle sconces, exit signs; the foyer (funhouse
facade with its marquee, ticket booth, turnstile, stanchions and ropes, posters, bunting); the
storytelling clutter (mirror shards, cobwebs, litter, staff notes, crates of spare mirrors, the
broken mirror stack, the mop bucket, blown-in leaves); the final chamber's antique mirrors, the
arrival mirror, the exit hallway's banner and door, the velvet curtains, the dead potted palms.

Facing: at rot 0 every directional prop's front faces -Z (north). Wall-mounted props stand in the
air cell in front of the wall, which is that cell's +Z face (local z = 1)."""
from __future__ import annotations

import math

import numpy as np

from . import carve as C
from . import geo as G
from . import mat as M
from . import paper as P
from .dims import CEIL, PANE_Y0, PANE_Y1, PLANE

SHEET = "decor"
WALL_Z = 1.0


# ------------------------------------------------------------------------------------------------
# Textures
# ------------------------------------------------------------------------------------------------

def _brass(h, w):
    r = M.rng(22870)
    return M.dust(M.brass(r, h, w, 128, tarnish=0.7), 0.06, r)


def _bakelite(h, w):
    r = M.rng(22871)
    n = M.fnoise(r, h, w, beta=2.0) * 0.5 + 0.5
    rgb = M.lerp(M.col("#120d0a"), M.col("#2a1d14"), n)
    return M.dust(rgb, 0.25, r)


def _cord(h, w):
    """Twisted cloth flex: diagonal twists, brown-black, frayed lighter."""
    Y, X = np.mgrid[0:h, 0:w].astype(float)
    tw = np.sin((X / w * 2 + Y / (w * 0.6)) * 2 * math.pi * 2)
    r = M.rng(22872)
    rgb = M.lerp(M.col("#1a130e"), M.col("#4a3a2a"), (tw * 0.5 + 0.5) ** 2)
    return M.dust(rgb, 0.2, r)


def _bulb(h, w):
    """Clear bulb glass with its filament: pale glass (lit: tinted by the glow colour at full
    bright; dead: dusty grey), the zig-zag filament and its support wires darker."""
    r = M.rng(22873)
    X, Y = M.grid(h, w)
    rgb = np.broadcast_to(M.col("#cfc8bb"), (h, w, 3)).copy()
    # Filament across the middle of the texture's width (the lathe wraps U round the bulb).
    fil = np.zeros((h, w))
    zz = 0.5 + 0.06 * np.sign(np.sin(X * 2 * math.pi * 6))
    fil += np.exp(-((Y - zz) / 0.02) ** 2) * (np.abs(X - 0.5) < 0.3)
    wires = (np.abs(X - 0.2) < 0.012) | (np.abs(X - 0.8) < 0.012)
    fil = np.clip(fil + wires * (Y > 0.5) * 0.8, 0, 1)
    rgb = M.lerp(rgb, M.col("#fff6e0"), fil * 0.9)
    # Dust settled on the top and a dark tip at the bottom (the sealing pip).
    rgb = M.lerp(rgb, M.col("#7b746a"), M.smooth(0.3, 0.0, Y) * 0.45)
    rgb = M.lerp(rgb, M.col("#4a4640"), M.smooth(0.92, 1.0, Y) * 0.7)
    return rgb


def _halo(h, w):
    X, Y = M.grid(h, w)
    d = np.hypot(X - 0.5, Y - 0.5) * 2
    g = np.exp(-(d / 0.35) ** 2) * 0.85 + np.exp(-(d / 0.12) ** 2) * 0.4
    g = np.clip(g * (d < 1.0), 0, 1)
    return np.stack([g, g, g, np.ones_like(g)], axis=2)


def _candle(h, w):
    """Candle sleeve: yellowed white card with wax drips (the sconce's fake candle)."""
    r = M.rng(22874)
    X, Y = M.grid(h, w)
    rgb = M.lerp(M.col("#d8cdb2"), M.col("#efe6d0"), M.fnoise(r, h, w, beta=2.0) * 0.5 + 0.5)
    drips = np.zeros((h, w))
    for k in range(7):
        x = r.random()
        L = 0.2 + 0.6 * r.random()
        drips += np.exp(-((X - x) / 0.025) ** 2) * (Y < L) * np.clip(1 - Y / L, 0, 1) ** 0.3
    rgb = M.lerp(rgb, M.col("#f4efe0"), np.clip(drips, 0, 1) * 0.6)
    return M.grime_gradient(rgb, r, bottom=0.1, top=0.2, tone=(0.4, 0.35, 0.28))


def _exit_face(h, w):
    """The sign's face: red EXIT on a dark ground (lit: full bright, the red glows)."""
    r = M.rng(22875)
    rgb = P.sign_text("EXIT", P.ANTON, w, h, M.col("#ff3a26"), np.broadcast_to(M.col("#1a0d0b"), (h, w, 3)).copy(), r=r,
                      fade=0.08, pad=0.14)
    return M.dust(rgb, 0.08, r)


def _signbox(h, w):
    r = M.rng(22876)
    rgb = M.lerp(M.col("#2c2a27"), M.col("#3c3832"), M.fnoise(r, h, w, beta=2.2) * 0.5 + 0.5)
    return M.dust(M.grime_gradient(rgb, r, 0.2, 0.0), 0.25, r)


def _poster(i):
    return lambda h, w: P.poster(i, h, w)


def _note(i):
    return lambda h, w: P.note(i, h, w)


def _figure(d, cx, foot, height, sx, sy, W, H):
    """A painted showman's silhouette (bowler hat, coat, cane) in a distorting mirror: sx/sy
    stretch it sideways / upward. Coordinates in sheet pixels; height = the undistorted height."""
    u = height / 8.0  # a head is an eighth of a figure

    def P(x, y):
        return (cx + x * u * sx, foot - y * u * sy)

    d.polygon([P(-0.9, 7.55), P(0.9, 7.55), P(0.75, 8.1), P(0.6, 8.35), P(-0.6, 8.35), P(-0.75, 8.1)], fill=255)  # hat
    d.polygon([P(-1.35, 7.5), P(1.35, 7.5), P(1.35, 7.62), P(-1.35, 7.62)], fill=255)                            # brim
    x0, y0 = P(-0.55, 7.55)
    x1, y1 = P(0.55, 6.5)
    d.ellipse([min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1)], fill=255)                                    # head
    d.polygon([P(-0.25, 6.6), P(0.25, 6.6), P(0.3, 6.25), P(-0.3, 6.25)], fill=255)                               # neck
    d.polygon([P(-1.3, 6.2), P(1.3, 6.2), P(1.15, 4.6), P(1.25, 3.4), P(-1.25, 3.4), P(-1.15, 4.6)], fill=255)     # coat
    d.polygon([P(1.25, 6.1), P(1.75, 5.0), P(1.6, 3.9), P(1.35, 3.95), P(1.45, 5.0), P(1.05, 5.8)], fill=255)      # arm
    d.polygon([P(-1.25, 6.1), P(-1.85, 4.6), P(-1.55, 4.45), P(-1.0, 5.6)], fill=255)                              # arm
    d.line([P(1.5, 4.0), P(1.9, 0.0)], fill=255, width=max(2, int(u * 0.18 * sx)))                                # cane
    for s in (-1, 1):
        d.polygon([P(s * 0.15, 3.5), P(s * 0.95, 3.5), P(s * 0.8, 0.25), P(s * 0.3, 0.25)], fill=255)              # legs
        d.polygon([P(s * 0.25, 0.3), P(s * 1.15, 0.3), P(s * 1.15, 0.0), P(s * 0.2, 0.0)], fill=255)               # shoes


def _facade(h, w):
    """The painted show front, as a 1930s carnival front was done: maroon boards with a cream and
    gold scroll border, a red-and-cream sunburst fanning round the arched way in, a ribbon over
    it (SEE YOURSELF AS OTHERS SEE YOU), two painted distorting mirrors in gilt ovals (a showman
    stretched tall, the same man squashed fat), a pointing hand (THIS WAY), a black dado with
    painted diamonds. Then decades: sun-faded toward the top, large flakes of peeling paint down
    to grey boards, water runs and grime splashed up from the floor."""
    r = M.rng(22877)
    from PIL import Image, ImageDraw
    ss = 2
    W, H = w * ss, h * ss
    bx = W / FACADE_W                       # pixels per block (supersampled)
    by = H / FACADE_H

    def Y(y_blocks):                         # height above the floor -> image row
        return H - y_blocks * by
    # Laid out as the viewer sees it (the door at 4..6 blocks from their left), flipped at the end
    # because the board's painted side faces -z, where +x is on the viewer's left.
    dc = FACADE_W - (DOOR_X0 + DOOR_X1) / 2
    rad = (DOOR_X1 - DOOR_X0) / 2
    spring = DOOR_TOP - rad * 0.6
    base = np.broadcast_to(M.col("#5e1f1b"), (h, w, 3)).copy()
    base = M.lerp(base, M.col("#4a1715"), M.blur(M.fnoise(r, h, w, beta=3.0), 2.0) * 0.5 + 0.5)
    layers = []

    def layer(c):
        img = Image.new("L", (W, H), 0)
        layers.append((img, M.col(c)))
        return ImageDraw.Draw(img)

    ray_c, ray_r, dado, diam, cream, gold, mir, mir_hi, fig, face, rib, rib_sh, rib_txt, hand, txt = (
        layer("#c9b48a"), layer("#9c2d22"), layer("#1d1a1c"), layer("#8a6a2a"), layer("#d2c29a"), layer("#b48a3a"),
        layer("#5f7884"), layer("#a9bcc0"), layer("#26202e"), layer("#c79a78"), layer("#d8c8a0"), layer("#8f7a52"),
        layer("#7a1a16"), layer("#d8c8a0"), layer("#d8c8a0"))
    # Sunburst round the arch: alternating cream and red rays from the arch's centre.
    cxp, cyp = dc * bx, Y(spring)
    n = 26
    for k in range(n):
        a0 = math.pi + k * math.pi / n
        a1 = a0 + math.pi / n
        L = 2.2 * bx
        (ray_c if k % 2 == 0 else ray_r).polygon(
            [(cxp, cyp), (cxp + math.cos(a0) * L, cyp + math.sin(a0) * L * 0.6),
             (cxp + math.cos(a1) * L, cyp + math.sin(a1) * L * 0.6)], fill=255)
    # ... clipped to a big flattened arch outline (it must stay under the board's top) with a
    # cream-and-gold border band round it.
    arch = Image.new("L", (W, H), 0)
    da = ImageDraw.Draw(arch)
    R2 = rad + 0.7
    ky = 0.52
    da.rectangle([(dc - R2) * bx, Y(spring), (dc + R2) * bx, Y(0)], fill=255)
    da.ellipse([(dc - R2) * bx, Y(spring + R2 * ky), (dc + R2) * bx, Y(spring - R2 * ky)], fill=255)
    archm = np.asarray(arch, float) / 255.0
    for img, _ in layers[:2]:
        img.paste(Image.fromarray((np.asarray(img, float) * archm).astype(np.uint8)))
    for off, dd, wd in ((0.0, cream, 0.09), (0.11, gold, 0.03)):
        Rb = R2 + off
        dd.arc([(dc - Rb) * bx, Y(spring + Rb * ky), (dc + Rb) * bx, Y(spring - Rb * ky)], 180, 360, fill=255,
               width=int(wd * bx))
        for s in (-1, 1):
            xx = (dc + s * Rb) * bx
            dd.line([(xx, Y(spring)), (xx, Y(0.55))], fill=255, width=int(wd * bx))
    # Dado: black with painted mustard diamonds, a cream rail on top.
    dado.rectangle([0, Y(0.55), W, H], fill=255)
    for k in range(int(FACADE_W / 0.5) + 1):
        x = (k + 0.5) * 0.5 * bx
        diam.polygon([(x - 0.18 * bx, Y(0.27)), (x, Y(0.45)), (x + 0.18 * bx, Y(0.27)), (x, Y(0.09))], fill=255)
    cream.rectangle([0, Y(0.6), W, Y(0.53)], fill=255)
    # Border: cream band and gold line along the top and down the ends, a C-scroll at each corner.
    cream.rectangle([0, Y(FACADE_H), W, Y(FACADE_H - 0.1)], fill=255)
    gold.rectangle([0, Y(FACADE_H - 0.14), W, Y(FACADE_H - 0.17)], fill=255)
    for x in (0.0, FACADE_W - 0.1):
        cream.rectangle([x * bx, Y(FACADE_H), (x + 0.1) * bx, Y(0.55)], fill=255)
    for cx_, s in ((0.45, 1), (FACADE_W - 0.45, -1)):
        for t in np.linspace(0, 1.7 * math.pi, 50):
            rr = 0.28 * (1 - t / (2.2 * math.pi)) * bx
            px, py = cx_ * bx + s * rr * math.cos(t), Y(FACADE_H - 0.5) - rr * math.sin(t)
            gold.ellipse([px - 0.03 * bx, py - 0.03 * bx, px + 0.03 * bx, py + 0.03 * bx], fill=255)
    # The two painted mirrors: left panel (0..4) the stretched man, right panel (6..9) the squashed.
    for (mx, my, rw, rh, sx, sy) in ((1.75, 1.95, 0.85, 0.95, 0.55, 1.12), (7.95, 1.95, 0.85, 0.95, 1.75, 0.6)):
        box = [(mx - rw) * bx, Y(my + rh), (mx + rw) * bx, Y(my - rh)]
        gold.ellipse([box[0] - 0.09 * bx, box[1] - 0.09 * by, box[2] + 0.09 * bx, box[3] + 0.09 * by], fill=255)
        cream.ellipse([box[0] - 0.03 * bx, box[1] - 0.03 * by, box[2] + 0.03 * bx, box[3] + 0.03 * by], fill=255)
        mir.ellipse(box, fill=255)
        for j in range(3):
            x0 = (mx - rw * 0.7 + j * 0.18) * bx
            mir_hi.line([(x0, Y(my + rh * 0.55)), (x0 + 0.35 * bx, Y(my + rh * 0.05))], fill=255, width=int(0.05 * bx))
        _figure(fig, mx * bx, Y(my - rh * 0.82), 1.45 * by, sx, sy, W, H)
        u = 1.45 * by / 8
        fx0, fy0 = mx * bx - 0.45 * u * sx, Y(my - rh * 0.82) - 7.45 * u * sy
        face.ellipse([fx0, fy0, fx0 + 0.9 * u * sx, fy0 + 0.75 * u * sy], fill=255)
    # A painted ribbon over the arch with the motto, the ends folded back.
    pts_top, pts_bot = [], []
    x0r, x1r = dc - 2.25, dc + 2.25
    for t in np.linspace(0, 1, 40):
        x = (x0r + (x1r - x0r) * t) * bx
        y = Y(2.95 - 0.12 * math.sin(t * math.pi) + 0.1)
        pts_top.append((x, y))
        pts_bot.append((x, y + 0.3 * by))
    rib.polygon(pts_top + pts_bot[::-1], fill=255)
    for (xe, s) in ((x0r, -1), (x1r, 1)):
        y = Y(3.05)
        rib_sh.polygon([(xe * bx, y), (xe * bx, y + 0.3 * by), ((xe + s * 0.3) * bx, y + 0.42 * by),
                        ((xe + s * 0.18) * bx, y + 0.27 * by), ((xe + s * 0.3) * bx, y + 0.12 * by)], fill=255)
    motto = "SEE YOURSELF AS OTHERS SEE YOU"
    f = P.font(P.RYE, M.fit_text_size(motto, P.RYE, 4.0 * bx, 0.2 * by, start=int(0.3 * by)))
    rib_txt.text((dc * bx, Y(2.93) + 0.15 * by), motto, font=f, fill=255, anchor="mm")
    # The barker's pointing hand under the right-hand mirror (toward the door: the viewer's left,
    # -x in this layout), and a line of painted patter under each mirror.
    hx, hy = 7.55 * bx, Y(0.8)
    hand.rectangle([hx + 0.12 * bx, hy - 0.1 * by, hx + 0.45 * bx, hy + 0.1 * by], fill=255)    # cuff
    hand.ellipse([hx - 0.2 * bx, hy - 0.11 * by, hx + 0.16 * bx, hy + 0.11 * by], fill=255)     # fist
    hand.rectangle([hx - 0.62 * bx, hy - 0.1 * by, hx - 0.05 * bx, hy - 0.04 * by], fill=255)   # index finger
    hand.ellipse([hx - 0.66 * bx, hy - 0.1 * by, hx - 0.58 * bx, hy - 0.04 * by], fill=255)
    txt.text((8.35 * bx, Y(0.8)), "THIS WAY", font=P.font(P.ABRIL, 0.12 * by), fill=255, anchor="mm")
    txt.text((1.75 * bx, Y(0.8)), "LAUGH AT YOURSELF!", font=P.font(P.ABRIL, 0.13 * by), fill=255, anchor="mm")
    rgb = base
    for img, c in layers:
        m = np.asarray(img.resize((w, h), Image.LANCZOS), float) / 255.0
        rgb = M.lerp(rgb, c, M.worn_print(r, m, 0.12))
    # Board joints (vertical planks), sun-fade toward the top, peeling to grey boards, water runs.
    Yp, Xp = np.mgrid[0:h, 0:w].astype(float)
    ppb = w / FACADE_W
    joints = (Xp % (ppb * 0.5)) < 1.2
    rgb = M.lerp(rgb, rgb * 0.5, joints * 0.7)
    rgb = M.lerp(rgb, M.col("#a8957c"), M.smooth(0.55, 0.0, Yp / h) * 0.22)
    under = M.lerp(M.col("#6a6152"), M.col("#857a66"), M.blur(M.fnoise(r, h, w, beta=2.6, aniso=(1.0, 6.0)), 1.0) * 0.5 + 0.5)
    under = M.lerp(under, under * 0.6, joints * 0.7)
    edge = M.smooth(0.2, 0.0, Yp / h) + M.smooth(0.85, 1.0, Yp / h) * 0.6
    rgb = M.chipped_paint(rgb, under, r, amount=0.1, scale=ppb * 0.6, edge=edge * 0.3, grain=2.6)
    rgb = M.grime_gradient(rgb, r, bottom=0.45, top=0.12, streak=0.7)
    rgb = M.damp_stains(rgb, r, 3, strength=0.25)
    return M.rgba(rgb[:, ::-1], facade_mask(w, h).astype(float))


def _letters(h, w):
    """The lettering board: HALL OF MIRRORS, cream letters with a red shadow on dark blue."""
    r = M.rng(22878)
    bg = M.lerp(M.col("#1c2740"), M.col("#27355a"), M.blur(M.fnoise(r, h, w, beta=2.8), 1.5) * 0.5 + 0.5)
    rgb = P.sign_text("HALL OF MIRRORS", P.RYE, w, h, M.col("#e8dcb8"), bg, r=r, fade=0.22, shadow=M.col("#8d241d"),
                      pad=0.14)
    X, Y = M.grid(h, w)
    border = (np.minimum(np.minimum(X, 1 - X) * w / h, np.minimum(Y, 1 - Y)) < 0.06)
    rgb = M.lerp(rgb, M.gilt(r, h, w, 160, wear=0.5, flake=0.3), border * 1.0)
    under = np.broadcast_to(M.col("#5a544a"), (h, w, 3))
    rgb = M.chipped_paint(rgb, under, r, amount=0.12, scale=60)
    rgb = M.dust(M.grime_gradient(rgb, r, 0.2, 0.1, streak=0.6), 0.12, r)
    # Read from -z (mirrored in u, see _facade), cut to the board's arched outline.
    return M.rgba(rgb[:, ::-1], letters_mask(w, h).astype(float))


def _price_board(h, w):
    """The A-board by the booth: a black-green board hand-lettered in cream and red, the prices
    re-painted over older ones (ghosts of the old figures show through), chipped at the edges."""
    r = M.rng(22835)
    rgb = M.lerp(M.col("#1d2620"), M.col("#27322a"), M.blur(M.fnoise(r, h, w, beta=2.8), 1.5) * 0.5 + 0.5)
    S = P.Sheet(w, h, ss=3)
    ghost, cream, red = S.layer(M.col("#3d463c")), S.layer(M.col("#d6c8a2")), S.layer(M.col("#a8352a"))
    S.text(cream, "ADMISSION", P.ABRIL, 0.5, 0.12, 0.84, 0.1)
    S.text(cream, "ADULTS", P.ANTON, 0.32, 0.32, 0.46, 0.09)
    S.text(ghost, "15c", P.RYE, 0.76, 0.35, 0.3, 0.12)
    S.text(red, "25c", P.RYE, 0.76, 0.31, 0.32, 0.12)
    S.text(cream, "CHILDREN", P.ANTON, 0.32, 0.5, 0.46, 0.09)
    S.text(ghost, "5c", P.RYE, 0.76, 0.53, 0.3, 0.12)
    S.text(red, "10c", P.RYE, 0.76, 0.49, 0.32, 0.12)
    S.text(cream, "NO REFUNDS", P.ANTON, 0.5, 0.68, 0.8, 0.09)
    S.text(cream, "ONE WAY ONLY", P.FELL, 0.5, 0.8, 0.8, 0.06)
    W, H = w * S.ss, h * S.ss
    cream.rectangle([W * 0.05, H * 0.03, W * 0.95, H * 0.97], outline=255, width=int(W * 0.015))
    for m, c in S.masks():
        rgb = M.lerp(rgb, c, M.worn_print(r, m, 0.2) * 0.9)
    X, Y = M.grid(h, w)
    edge = np.clip(1 - np.minimum(np.minimum(X, 1 - X) * w, np.minimum(Y, 1 - Y) * h) / 6.0, 0, 1)
    rgb = M.chipped_paint(rgb, np.broadcast_to(M.col("#6a5d48"), (h, w, 3)), r, 0.08, 30, edge=edge, grain=2.4)
    return M.dust(M.grime_gradient(rgb, r, 0.35, 0.05), 0.12, r)


def _closed_card(h, w):
    """The CLOSED card hung in the booth window: yellowed board, red letters, a string hole each
    side, sun-bleached (it has hung there a long time)."""
    r = M.rng(22836)
    paper = P.aged_paper(r, h, w, tone=M.col("#ddd0a8"), foxing=0.3, stains=1, creases=0)
    S = P.Sheet(w, h, ss=3)
    red, black = S.layer(P.INK_RED), S.layer(P.INK_BLACK)
    S.text(red, "CLOSED", P.ANTON, 0.5, 0.48, 0.84, 0.5)
    S.text(black, "SORRY", P.FELL, 0.5, 0.86, 0.5, 0.14)
    W, H = w * S.ss, h * S.ss
    for x in (0.08, 0.92):
        black.ellipse([W * x - 4, H * 0.1 - 4, W * x + 4, H * 0.1 + 4], fill=255)
    rgb = P.ink(paper, S.masks(), r, fade=0.35, wear=0.15)
    return M.rgba(rgb, P.torn_alpha(r, h, w, ragged=0.004, missing_corner=False))


def _walnut(h, w):
    """Dark old walnut for linings and trim (chamber mirror reveals, chamber skirting)."""
    r = M.rng(22837)
    rgb = M.wood(r, h, w, 128, palette=(M.col("#2a1a10"), M.col("#3d281b"), M.col("#140c07")), grain="h", varnish=0.25)
    return M.dust(rgb, 0.08, r)


def booth_head_mask(rows, cols):
    """The ticket window's arched head (solid above an arch)."""
    Y, X = np.mgrid[0:rows, 0:cols].astype(float)
    xx = (X + 0.5) / cols
    yy = 1 - (Y + 0.5) / rows
    return yy > (0.95 - 0.8 * np.sqrt(np.clip(1 - ((xx - 0.5) / 0.5) ** 2, 0, 1)))


def valance_mask(rows, cols):
    """The roof's scalloped valance: eight scallops hanging from a band."""
    Y, X = np.mgrid[0:rows, 0:cols].astype(float)
    per = cols / 8.0
    return (Y / rows) < 0.45 + 0.55 * np.sqrt(np.clip(1 - (((X % per) - (per - 1) / 2) / (per / 2)) ** 2, 0, 1))


def _booth_head(h, w):
    r = M.rng(22832)
    return M.rgba(_paint_cream(h, w), booth_head_mask(h, w).astype(float))


def _valance(h, w):
    r = M.rng(22833)
    X, Y = M.grid(h, w)
    rgb = np.where(((X * 8) % 1 < 0.5)[..., None], _paint_red(h, w), _paint_cream(h, w))
    return M.rgba(rgb, valance_mask(h, w).astype(float))


def _booth(h, w):
    """Ticket-booth panelling: cream fields in red rails with a gold pinstripe, chipped and grimy."""
    r = M.rng(22879)
    X, Y = M.grid(h, w)
    cream = M.col("#cdbb8f")
    red = M.col("#7b211b")
    field = (np.abs(X - 0.5) < 0.36) & (np.abs(Y - 0.5) < 0.36)
    rgb = np.where(field[..., None], cream, red)
    pin = (np.abs(np.maximum(np.abs(X - 0.5), np.abs(Y - 0.5)) - 0.33) < 0.006)
    rgb = M.lerp(rgb, M.col("#c09a45"), pin * 1.0)
    edge = np.clip(1 - np.abs(np.maximum(np.abs(X - 0.5), np.abs(Y - 0.5)) - 0.36) / 0.02, 0, 1)
    rgb = rgb * (1 - 0.35 * edge)[..., None]
    under = M.wood(r, h, w, 256, palette=M.PINE, grain="v", varnish=0)
    rgb = M.chipped_paint(rgb, under, r, amount=0.15, scale=50)
    rgb = M.grime_gradient(rgb, r, 0.45, 0.05)
    return M.dust(rgb, 0.15, r)


def _booth_sign(h, w):
    r = M.rng(22880)
    rgb = P.sign_text("TICKETS", P.SANCREEK, w, h, M.col("#e2c46a"), np.broadcast_to(M.col("#6e1b16"), (h, w, 3)).copy(),
                      r=r, fade=0.25, shadow=M.col("#2a0a08"))
    return M.dust(M.chipped_paint(rgb, np.broadcast_to(M.col("#77684f"), (h, w, 3)), r, 0.12, 40), 0.15, r)


def _booth_glass(h, w):
    """The booth's cracked window (drawn see-through, sorted with the mirrors)."""
    r = M.rng(22881)
    from .mirror import _crack_lines
    rgb = np.broadcast_to(M.col("#a9aaa5"), (h, w, 3)).copy()
    a = 0.14 + 0.1 * (M.fnoise(r, h, w, beta=2.4) * 0.5 + 0.5)
    c, d = _crack_lines(r, w * 0.62, h * 0.4, 9, 2, (w, h))
    rgb = M.lerp(rgb, M.col("#efece4"), c)
    a = np.clip(a + c * 0.6 + d * 0.2, 0, 1)
    # Grime in the bottom corners and a hand-wiped clean patch at the speaking hole.
    X, Y = M.grid(h, w)
    a = a + M.smooth(0.6, 1.0, Y) * 0.25
    a = a * (1 - 0.5 * np.exp(-(((X - 0.5) / 0.2) ** 2 + ((Y - 0.72) / 0.1) ** 2)))
    return M.rgba(rgb, np.clip(a, 0, 0.8))


def _velvet(h, w):
    """Red velvet rope: a twisted pile, darker in the twists."""
    r = M.rng(22882)
    Y, X = np.mgrid[0:h, 0:w].astype(float)
    tw = np.sin((X / w + Y / (w * 1.2)) * 2 * math.pi * 3)
    rgb = M.lerp(M.col("#3a0c0c"), M.col("#7c1f1c"), (tw * 0.5 + 0.5) ** 1.5)
    rgb = rgb * (0.9 + 0.1 * M.fnoise(r, h, w, beta=0.5))[..., None]
    return M.dust(rgb, 0.3, r)


def _bunting(h, w):
    """Six pennants side by side (faded red, cream, blue, mustard, red, cream), cut out, with a
    stitched hem and stains; one torn."""
    r = M.rng(22883)
    cols = ["#8c2a22", "#cfc19c", "#2f4570", "#b98d34", "#8c2a22", "#cfc19c"]
    X, Y = M.grid(h, w)
    k = np.minimum((X * 6).astype(int), 5)
    fx = X * 6 - k
    tri = (np.abs(fx - 0.5) * 2 < (1 - Y)) & (Y < 0.98)
    rgb = np.zeros((h, w, 3))
    for i, c in enumerate(cols):
        rgb[k == i] = M.col(c)
    weave = M.canvas_weave(r, h, w, 256)
    rgb = rgb * (0.85 + 0.25 * weave)[..., None]
    rgb = M.lerp(rgb, M.col("#b7aa8a"), 0.25)
    hem = Y < 0.07
    rgb = M.lerp(rgb, rgb * 0.7, hem * 1.0)
    torn = (k == 4) & (Y > 0.45 + 0.1 * M.fnoise(r, h, w, beta=1.5))
    rgb = M.damp_stains(rgb, r, 2, strength=0.3)
    return M.rgba(M.dust(rgb, 0.2, r), (tri & ~torn).astype(float))


def _shards(h, w):
    """Mirror shards for the floor: silvered glass polygons with bright broken edges (four per tile)."""
    r = M.rng(22884)
    from PIL import Image, ImageDraw
    img = Image.new("L", (w * 2, h * 2), 0)
    d = ImageDraw.Draw(img)
    for qx in range(2):
        for qy in range(2):
            cx, cy = (qx + 0.5) * w, (qy + 0.5) * h
            n = 4 + int(r.integers(3))
            pts = []
            for j in range(n):
                a = 2 * math.pi * j / n + r.normal() * 0.3
                rad = (0.2 + 0.25 * r.random()) * w
                pts.append((cx + math.cos(a) * rad, cy + math.sin(a) * rad * (0.4 + 0.6 * r.random())))
            d.polygon(pts, fill=255)
    m = np.asarray(img.resize((w, h), Image.LANCZOS), float) / 255.0
    inside = M.edge_distance(m)
    rgb = M.lerp(M.col("#6d6c69"), M.col("#b9b7b1"), M.fnoise(r, h, w, beta=3.0) * 0.5 + 0.5)
    rgb = M.lerp(rgb, M.col("#f2f0ea"), np.clip(1 - inside / 2.0, 0, 1) * (m > 0.5))
    rgb = M.lerp(rgb, M.col("#1d1b18"), np.clip(1 - np.abs(inside - 3.5) / 1.5, 0, 1) * 0.35)
    rgb = M.dust(rgb, 0.25, r)
    return M.rgba(rgb, m)


def _cobweb(h, w):
    """A corner web: radial threads and a sagging spiral from the top-right corner, grey with dust,
    some broken and hanging. The layer is alpha-CUT (0.1), so there is no translucent dust sheet:
    the 'dust' near the corner is a felt of many short crossing strands (opaque, fine), thinning
    out with distance, and the alpha is exactly zero everywhere else (no visible card edge)."""
    r = M.rng(22885)
    from PIL import Image, ImageDraw
    ss = 3
    W, H = w * ss, h * ss
    img = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(img)
    ox, oy = W - 2, 2
    rays = 12
    ang = [math.pi / 2 + (k + 0.15 + 0.6 * r.random()) / rays * (math.pi / 2) for k in range(rays)]
    reach = [(0.7 + 0.28 * r.random()) * W for _ in ang]
    for a, L in zip(ang, reach):
        d.line([(ox, oy), (ox + math.cos(a) * L, oy + math.sin(a) * L)], fill=255, width=2)
    for ring in range(16):
        rad = (0.05 + 0.058 * ring) * W
        pts = [(ox + math.cos(a) * rad * (1 + 0.015 * r.normal()), oy + math.sin(a) * rad + 0.05 * W * (ring / 16) ** 1.5)
               for a in ang]
        for j in range(len(pts) - 1):
            if rad > min(reach[j], reach[j + 1]) or r.random() > 0.82:
                continue
            # Each span sags a little between the radials.
            (x0, y0), (x1, y1) = pts[j], pts[j + 1]
            mid = ((x0 + x1) / 2, (y0 + y1) / 2 + 0.012 * W)
            d.line([pts[j], mid, pts[j + 1]], fill=230, width=2)
    # Broken strands hanging down from the spiral.
    for _ in range(9):
        a = ang[int(r.integers(rays))]
        rad = (0.2 + 0.5 * r.random()) * W
        x0, y0 = ox + math.cos(a) * rad, oy + math.sin(a) * rad
        L = (0.06 + 0.16 * r.random()) * H
        d.line([(x0, y0), (x0 + 0.02 * W * r.normal(), y0 + L * 0.6), (x0 + 0.03 * W * r.normal(), y0 + L)], fill=220,
               width=2)
    # Dust felt in the corner: short strands, densest at the corner.
    for _ in range(420):
        rad = (r.random() ** 2.2) * 0.42 * W
        a = math.pi / 2 + r.random() * math.pi / 2
        x0, y0 = ox + math.cos(a) * rad, oy + math.sin(a) * rad
        b = r.random() * math.pi
        L = (0.01 + 0.04 * r.random()) * W
        d.line([(x0, y0), (x0 + math.cos(b) * L, y0 + math.sin(b) * L)], fill=200, width=2)
    m = np.asarray(img.resize((w, h), Image.LANCZOS), float) / 255.0
    a = np.where(m > 0.18, np.clip(m * 1.6, 0, 1), 0.0)
    X, Y = M.grid(h, w)
    corner = np.exp(-((1 - X) ** 2 + Y ** 2) / 0.05)
    # Grey silk, dirtier (darker, browner) where the dust has clogged it near the corner.
    rgb = M.lerp(M.col("#bdb8ae"), M.col("#77705f"), np.clip(corner * 1.2, 0, 1))
    return M.rgba(rgb, a)


def _litter(h, w):
    """A strip of litter textures (4 squares): ticket stubs on a roll's paper, a striped popcorn
    box, a crumpled wrapper, a flattened paper cup."""
    r = M.rng(22886)
    out = np.zeros((h, w, 4))
    q = w // 4
    # Ticket stubs: pink card with ADMIT ONE.
    tick = P.sign_text("ADMIT ONE", P.ANTON, q, h, M.col("#5a1d24"), np.broadcast_to(M.col("#d79a95"), (h, q, 3)).copy(),
                       r=r, fade=0.25, pad=0.2)
    tick = P.aged_paper(r, h, q, M.col("#d4948e"), 0.3, 1, 1) * 0.4 + tick * 0.6
    out[:, :q] = M.rgba(tick)
    # Popcorn box stripes.
    Y, X = np.mgrid[0:h, 0:q].astype(float)
    stripes = ((X // (q / 6)) % 2 == 0)
    box = np.where(stripes[..., None], M.col("#a83228"), M.col("#ddd2b6"))
    box = P.aged_paper(r, h, q, M.col("#ddd2b6"), 0.2, 1, 2) * 0.3 + box * 0.7
    out[:, q:2 * q] = M.rgba(M.dust(box, 0.2, r))
    # Crumpled wrapper: foil-ish with crumple shading.
    hgt = M.fnoise(r, h, q, beta=1.6)
    wr = M.lerp(M.col("#4f6f95"), M.col("#c9a64a"), (M.fnoise(r, h, q, beta=2.8) > 0.3).astype(float))
    wr = wr * M.relief(hgt * 6, 0.8)[..., None]
    out[:, 2 * q:3 * q] = M.rgba(wr)
    # Paper cup: white with a faded red band.
    cup = P.aged_paper(r, h, q, M.col("#e2dccb"), 0.2, 1, 0)
    band = (Y > h * 0.3) & (Y < h * 0.5)
    cup = M.lerp(cup, M.col("#b9473a"), band * 0.8)
    out[:, 3 * q:] = M.rgba(cup)
    return out


def _crate(h, w):
    """Crate boards with a stencil: FRAGILE - MIRROR GLASS - THIS SIDE UP."""
    r = M.rng(22887)
    rgb = M.wood(r, h, w, 256, palette=M.PINE, grain="h", varnish=0.0) * 0.85
    Y, X = np.mgrid[0:h, 0:w].astype(float)
    gaps = (Y % (h / 3)) < 2
    rgb = M.lerp(rgb, rgb * 0.3, gaps * 1.0)
    S = P.Sheet(w, h)
    st = S.layer(M.col("#2a1b14"))
    S.text(st, "FRAGILE", P.ANTON, 0.5, 0.24, 0.55, 0.2)
    S.text(st, "MIRROR GLASS", P.ANTON, 0.5, 0.52, 0.8, 0.16)
    S.text(st, "THIS SIDE UP", P.TYPE, 0.5, 0.8, 0.6, 0.11)
    for m, c in S.masks():
        rgb = M.lerp(rgb, c, M.worn_print(r, m, 0.35) * 0.8)
    rgb = M.grime_gradient(rgb, r, 0.3, 0.05)
    return M.dust(rgb, 0.2, r)


def _oldmirror(h, w):
    """A spare/broken mirror's face seen in the backstage dark: dull silver, foxed, cracked (static
    and opaque; there is nothing to reflect behind a stacked mirror)."""
    r = M.rng(22888)
    from .mirror import _crack_lines
    X, Y = M.grid(h, w)
    rgb = M.lerp(M.col("#2d2c2a"), M.col("#6f6d68"), np.clip(1 - Y * 0.9 + 0.2 * M.fnoise(r, h, w, beta=2.6), 0, 1))
    c, d = _crack_lines(r, w * 0.4, h * 0.35, 8, 2, (w, h))
    rgb = M.lerp(rgb, M.col("#d8d6d0"), c * 0.7)
    rgb = M.lerp(rgb, M.col("#0e0d0c"), d * 0.4)
    edge = np.clip(1 - np.minimum(np.minimum(X, 1 - X), np.minimum(Y, 1 - Y)) / 0.1, 0, 1)
    rgb = M.lerp(rgb, M.col("#16110c"), (edge * (0.6 + 0.4 * M.fnoise(r, h, w, beta=2.0))) .clip(0, 1) * 0.8)
    return M.dust(rgb, 0.2, r)


def _galv(h, w):
    r = M.rng(22889)
    _, d1, d2 = M.voronoi(r, h, w, 60)
    span = (d2 - d1)
    rgb = M.lerp(M.col("#6f716e"), M.col("#9a9b97"), M.fnoise(r, h, w, beta=1.8) * 0.5 + 0.5)
    rgb = M.lerp(rgb, M.col("#4f504d"), np.clip(1 - span / 2, 0, 1) * 0.4)
    rgb = M.grime_gradient(rgb, r, 0.5, 0.1, tone=(0.2, 0.17, 0.12))
    return M.dust(rgb, 0.2, r)


def _mop(h, w):
    r = M.rng(22890)
    n = M.fnoise(r, h, w, beta=1.0, aniso=(1.0, 20.0)) * 0.5 + 0.5
    rgb = M.lerp(M.col("#5f5747"), M.col("#9b917b"), n)
    return M.grime_gradient(rgb, r, 0.6, 0.0, tone=(0.15, 0.12, 0.08))


def _banner(h, w):
    r = M.rng(22891)
    # A soft hint of the weave only: at this size a full-contrast weave is just pixel noise.
    cloth = M.lerp(M.col("#cdc09c"), M.col("#d9cdaa"), M.blur(M.canvas_weave(r, h, w, 256), 0.6))
    S = P.Sheet(w, h)
    t1 = S.layer(M.col("#7b1f1a"))
    t2 = S.layer(M.col("#24365b"))
    S.text(t1, "THANK YOU", P.RYE, 0.5, 0.36, 0.7, 0.36)
    S.text(t2, "FOR VISITING", P.ABRIL, 0.5, 0.74, 0.7, 0.28)
    rgb = P.ink(cloth, S.masks(), r, fade=0.3, wear=0.25)
    rgb = M.damp_stains(rgb, r, 3, strength=0.35)
    rgb = M.grime_gradient(rgb, r, 0.3, 0.1, streak=0.7)
    X, Y = M.grid(h, w)
    a = (Y > 0.02 + 0.02 * M.fnoise(r, h, w, beta=1.5)) & (Y < 0.97 + 0.02 * M.fnoise(r, h, w, beta=1.5))
    return M.rgba(M.dust(rgb, 0.15, r), a.astype(float))


def _leaves(h, w):
    """Dry blown-in leaves, one per quadrant, each pointing up its quadrant (stem at the bottom):
    a lobed oak, a five-pointed maple, a toothed beech and a broken elm. Browns and ochres,
    blotched, a darker curled-under rim, midrib and side veins."""
    r = M.rng(22892)
    from PIL import Image, ImageDraw
    q = w // 2
    out = np.zeros((h, w, 4))
    tones = [("#5a3d22", "#8a6232"), ("#6e3e1e", "#9c7034"), ("#4e3822", "#7d6040"), ("#5e4a2a", "#8f7c4c")]
    for k in range(4):
        ss = 4
        Q = q * ss
        img = Image.new("L", (Q, Q), 0)
        d = ImageDraw.Draw(img)
        cx, base, tip = Q * 0.5, Q * 0.86, Q * 0.07
        pts_l, pts_r = [], []
        if k == 1:
            # Maple: a palmate outline in polar form round a point low on the midrib.
            oy = Q * 0.6
            pts = []
            for t in np.linspace(0, 2 * math.pi, 241):
                a = t - math.pi / 2
                lobe = abs(math.cos(2.5 * (t + 0.0))) ** 0.5
                rad = Q * (0.18 + 0.26 * lobe) * (0.55 if math.sin(a) > 0.6 else 1.0)
                pts.append((cx + math.cos(a) * rad, oy + math.sin(a) * rad * 0.95))
            d.polygon(pts, fill=255)
            veins = [((cx, oy), (cx + math.cos(a) * Q * 0.42, oy + math.sin(a) * Q * 0.42))
                     for a in (-math.pi / 2, -math.pi / 2 + 1.26, -math.pi / 2 - 1.26, 0.3, math.pi - 0.3)]
            stem = [(cx, oy), (cx + Q * 0.02, Q * 0.98)]
        else:
            for s in np.linspace(0, 1, 120):
                y = base + (tip - base) * s
                wmax = (0.3, 0, 0.24, 0.22)[k] * Q
                hw = wmax * math.sin(math.pi * s ** 0.85) ** 0.8
                if k == 0:
                    hw *= 0.62 + 0.38 * abs(math.sin(s * math.pi * 4.5)) ** 0.7
                elif k == 2:
                    hw *= 1.0 - 0.06 * ((s * 22) % 1)
                else:
                    hw *= 1.0 - 0.05 * ((s * 30) % 1)
                pts_l.append((cx - hw * (1.08 if k == 3 else 1.0), y))
                pts_r.append((cx + hw, y))
            d.polygon(pts_l + pts_r[::-1], fill=255)
            if k == 3:
                # A ragged piece missing from one side.
                d.ellipse([cx + Q * 0.06, Q * 0.35, cx + Q * 0.3, Q * 0.6], fill=0)
            veins = [((cx, base), (cx, tip + Q * 0.04))]
            for s in np.linspace(0.12, 0.8, 7 if k != 0 else 5):
                y = base + (tip - base) * s
                for sg in (-1, 1):
                    veins.append(((cx, y), (cx + sg * Q * 0.26, y - Q * 0.12)))
            stem = [(cx, base), (cx - Q * 0.015, Q * 0.98)]
        mask = np.asarray(img.resize((q, q), Image.LANCZOS), float) / 255.0
        vimg = Image.new("L", (Q, Q), 0)
        dv = ImageDraw.Draw(vimg)
        for j, (a, b) in enumerate(veins):
            dv.line([a, b], fill=255, width=ss * (2 if j == 0 else 1))
        vein = np.asarray(vimg.resize((q, q), Image.LANCZOS), float) / 255.0
        simg = Image.new("L", (Q, Q), 0)
        ImageDraw.Draw(simg).line(stem, fill=255, width=ss * 2)
        stemm = np.asarray(simg.resize((q, q), Image.LANCZOS), float) / 255.0
        lo, hi = tones[k]
        n = M.blur(M.fnoise(r, q, q, beta=2.8), 1.5) * 0.5 + 0.5
        rgb = M.lerp(M.col(lo), M.col(hi), n)
        # Darker rot blotches, and the rim curled under (darker, browner).
        blot = M.smooth(0.6, 0.8, M.blur(M.fnoise(r, q, q, beta=2.2), 1.0) * 0.5 + 0.5)
        rgb = M.lerp(rgb, M.col("#3a2414"), blot * 0.6)
        rim = np.clip(1 - M.edge_distance(mask) / (q * 0.05), 0, 1)
        rgb = M.lerp(rgb, rgb * 0.55, rim)
        rgb = M.lerp(rgb, rgb * 0.62, vein * mask)
        rgb = M.lerp(rgb, M.col("#3b2a18"), stemm)
        a = np.clip(np.maximum(mask, stemm) * 1.5, 0, 1)
        y0, x0 = (k // 2) * q, (k % 2) * q
        out[y0:y0 + q, x0:x0 + q] = M.rgba(rgb, a)
    return out


def _curtain(h, w):
    """Black velvet strips: pile catching the light at the folds, dusty and moth-eaten at the hem."""
    r = M.rng(22893)
    X, Y = M.grid(h, w)
    fold = np.sin(X * 2 * math.pi * 3 + 0.3 * M.fnoise(r, h, w, beta=3.0, aniso=(1.0, 8.0)))
    rgb = M.lerp(M.col("#0b0909"), M.col("#2a2224"), (fold * 0.5 + 0.5) ** 2)
    rgb = M.dust(rgb, 0.15, r, up=M.smooth(0.7, 1.0, Y))
    a = (Y < 0.985 - 0.02 * (M.fnoise(r, h, w, beta=1.4) * 0.5 + 0.5)).astype(float)
    return M.rgba(rgb, a)


def _door(h, w):
    """An exit door: painted steel, push plate, a kick plate, EXIT stencilled, scraped."""
    r = M.rng(22894)
    X, Y = M.grid(h, w)
    rgb = M.lerp(M.col("#3e4a3f") * 0.0 + M.col("#5a5348"), M.col("#6b6456"), M.fnoise(r, h, w, beta=2.4) * 0.5 + 0.5)
    kick = Y > 0.88
    rgb = np.where(kick[..., None], M.brass(r, h, w, 256, 0.8), rgb)
    S = P.Sheet(w, h)
    st = S.layer(M.col("#e6dfcf"))
    S.text(st, "EXIT", P.ANTON, 0.5, 0.2, 0.6, 0.1)
    for m, c in S.masks():
        rgb = M.lerp(rgb, c, M.worn_print(r, m, 0.3) * 0.8)
    under = M.steel_black(r, h, w, 256, rust=0.6) * 1.6
    rgb = M.chipped_paint(rgb, under, r, 0.1, 40)
    rgb = M.grime_gradient(rgb, r, 0.35, 0.05)
    sc = M.scratches(r, h, w, 60, (0.02, 0.1))
    return M.dust(M.lerp(rgb, M.col("#9a978f"), sc * 0.4), 0.12, r)


def _palm(h, w):
    """A dead palm frond: a central rib with leaflets, dried brown-grey with a few green-gone-olive
    ones (no green tint, but not all dead the same)."""
    r = M.rng(22895)
    X, Y = M.grid(h, w)
    rib = np.abs(X - 0.5) < 0.012
    k = Y * 26
    leaflet = (np.abs(((k + np.abs(X - 0.5) * 30) % 1) - 0.5) < 0.22) & (np.abs(X - 0.5) < 0.48 * np.sin(np.clip(Y, 0, 1) * math.pi) ** 0.6)
    torn = M.fnoise(r, h, w, beta=1.0) > 1.2
    m = rib | (leaflet & ~torn)
    tone = M.lerp(M.col("#6b5838"), M.col("#8f7d55"), M.fnoise(r, h, w, beta=2.0) * 0.5 + 0.5)
    tone = M.lerp(tone, M.col("#3a3020"), M.smooth(0.35, 0.5, np.abs(X - 0.5)) * 0.5)
    return M.rgba(tone, m.astype(float))


def _pot(h, w):
    r = M.rng(22896)
    rgb = M.brass(r, h, w, 128, tarnish=1.0) * 0.85
    return M.grime_gradient(rgb, r, 0.4, 0.0)


def _crest(h, w):
    """The chamber mirrors' carved gilt crest: a shell flanked by C-scrolls (cut-out)."""
    r = M.rng(22897)
    m = crest_mask(h, w)
    height = C._rounded(m, w * 0.03) + 0.3 * M.blur(m.astype(float), 3)
    rgb = M.gilt(r, h, w, 256, wear=0.45, flake=0.22, height=height * 20)
    return M.rgba(rgb, m.astype(float))


def crest_mask(h, w):
    from PIL import Image, ImageDraw
    ss = 3
    W, H = w * ss, h * ss
    img = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(img)
    # Base rail along the bottom.
    d.rectangle([0, H * 0.82, W, H], fill=255)
    # Shell: a fan of ribs from the bottom centre.
    cx, cy = W * 0.5, H * 0.95
    for k in range(11):
        a = math.pi + (k + 0.5) / 11 * math.pi
        rr = H * 0.85
        d.polygon([(cx, cy), (cx + math.cos(a - 0.11) * rr, cy + math.sin(a - 0.11) * rr),
                   (cx + math.cos(a) * rr * 1.04, cy + math.sin(a) * rr * 1.04),
                   (cx + math.cos(a + 0.11) * rr, cy + math.sin(a + 0.11) * rr)], fill=255)
    # C-scrolls either side.
    for s in (-1, 1):
        sx = cx + s * W * 0.3
        for t in np.linspace(0, 1.5 * math.pi, 40):
            rr = H * 0.32 * (1 - t / (2 * math.pi))
            px = sx + s * rr * math.cos(t)
            py = H * 0.55 - rr * math.sin(t)
            d.ellipse([px - W * 0.018, py - W * 0.018, px + W * 0.018, py + W * 0.018], fill=255)
        d.line([(cx + s * W * 0.1, H * 0.85), (sx + s * W * 0.1, H * 0.6), (cx + s * W * 0.48, H * 0.82)], fill=255,
               width=int(W * 0.03))
    return np.asarray(img.resize((w, h), Image.LANCZOS), float) / 255.0 > 0.5


def _frame_gilt(h, w):
    r = M.rng(22898)
    X, Y = M.grid(h, w)
    # Running ornament along the length (egg and dart, baked relief).
    u = X * 24
    egg = np.exp(-(((u % 1) - 0.5) / 0.22) ** 2) * np.exp(-((Y - 0.5) / 0.3) ** 2)
    dart = np.exp(-(((u + 0.5) % 1 - 0.5) / 0.05) ** 2) * (np.abs(Y - 0.5) < 0.3)
    height = egg * 1.0 + dart * 0.6 + np.exp(-((Y - 0.1) / 0.06) ** 2) * 0.5 + np.exp(-((Y - 0.9) / 0.06) ** 2) * 0.5
    return M.gilt(r, h, w, 512, wear=0.45, flake=0.2, height=height * 12)


def _wire(h, w):
    r = M.rng(22899)
    return M.lerp(M.col("#0c0c0c"), M.col("#2a2a28"), M.fnoise(r, h, w, beta=1.0) * 0.5 + 0.5)


def _paint_red(h, w):
    r = M.rng(22830)
    rgb = M.lerp(M.col("#6d1d18"), M.col("#8a2a22"), M.fnoise(r, h, w, beta=2.2) * 0.5 + 0.5)
    under = M.wood(r, h, w, 256, palette=M.PINE, grain="v", varnish=0)
    rgb = M.chipped_paint(rgb, under, r, 0.16, 50, grain=2.4)
    return M.dust(M.grime_gradient(rgb, r, 0.3, 0.05), 0.15, r)


def _paint_cream(h, w):
    r = M.rng(22831)
    rgb = M.lerp(M.col("#bfae86"), M.col("#d4c49c"), M.fnoise(r, h, w, beta=2.2) * 0.5 + 0.5)
    under = M.wood(r, h, w, 256, palette=M.PINE, grain="v", varnish=0)
    rgb = M.chipped_paint(rgb, under, r, 0.16, 50, grain=2.4)
    return M.dust(M.grime_gradient(rgb, r, 0.3, 0.05), 0.15, r)


def regions(S):
    S.region("de_brass", SHEET, 128, 128, _brass)
    S.region("de_bakelite", SHEET, 64, 64, _bakelite)
    S.region("de_cord", SHEET, 32, 256, _cord)
    S.region("de_bulb", SHEET, 128, 128, _bulb)
    S.region("de_halo", SHEET, 128, 128, _halo)
    S.region("de_candle", SHEET, 64, 128, _candle)
    S.region("de_exit", SHEET, 256, 96, _exit_face)
    S.region("de_signbox", SHEET, 128, 128, _signbox)
    for i in range(6):
        S.region(f"de_poster_{i}", SHEET, 200, 280, _poster(i))
    for i in range(3):
        S.region(f"de_note_{i}", SHEET, 160, 208, _note(i))
    S.region("de_facade", SHEET, 1024, 352, _facade)
    S.region("de_letters", SHEET, 896, 128, _letters)
    S.region("de_price", SHEET, 144, 192, _price_board)
    S.region("de_closed", SHEET, 160, 96, _closed_card)
    S.region("de_walnut", SHEET, 256, 128, _walnut)
    S.region("de_booth", SHEET, 192, 192, _booth)
    S.region("de_booth_sign", SHEET, 384, 96, _booth_sign)
    S.region("de_booth_glass", SHEET, 192, 192, _booth_glass)
    S.region("de_velvet", SHEET, 32, 256, _velvet)
    S.region("de_bunting", SHEET, 512, 96, _bunting)
    S.region("de_shards", SHEET, 192, 192, _shards)
    S.region("de_cobweb", SHEET, 256, 256, _cobweb)
    S.region("de_litter", SHEET, 384, 96, _litter)
    S.region("de_crate", SHEET, 384, 144, _crate)
    S.region("de_oldmirror", SHEET, 160, 208, _oldmirror)
    S.region("de_galv", SHEET, 256, 128, _galv)
    S.region("de_mop", SHEET, 64, 128, _mop)
    S.region("de_banner", SHEET, 576, 144, _banner)
    S.region("de_leaves", SHEET, 256, 256, _leaves)
    S.region("de_curtain", SHEET, 192, 384, _curtain)
    S.region("de_door", SHEET, 160, 320, _door)
    S.region("de_palm", SHEET, 96, 288, _palm)
    S.region("de_pot", SHEET, 128, 128, _pot)
    S.region("de_crest", SHEET, 384, 144, _crest)
    S.region("de_frame_gilt", SHEET, 1024, 64, _frame_gilt)
    S.region("de_wire", SHEET, 16, 64, _wire)
    S.region("de_paint_red", SHEET, 160, 160, _paint_red)
    S.region("de_paint_cream", SHEET, 160, 160, _paint_cream)
    S.region("de_facade_rear", SHEET, 448, 160, _facade_rear)
    S.region("de_letters_rear", SHEET, 512, 80, _letters_rear)
    S.region("de_booth_head", SHEET, 192, 64, _booth_head)
    S.region("de_valance", SHEET, 384, 48, _valance)


def _rear_boards(h, w, ppb, mask):
    """The unpainted back of a show front: grey weathered boards on battens, cut to the mask."""
    r = M.rng(22834)
    rgb = M.wood(r, h, w, ppb, palette=(M.col("#5d5446"), M.col("#7a6f5c"), M.col("#3a342b")), grain="v", varnish=0.0)
    Y, X = np.mgrid[0:h, 0:w].astype(float)
    rgb = M.lerp(rgb, rgb * 0.4, ((X % (ppb * 0.5)) < 1.2) * 0.8)
    batten = (np.abs((Y / h) - 0.3) < 0.03) | (np.abs((Y / h) - 0.75) < 0.03)
    rgb = np.where(batten[..., None], rgb * 1.15, rgb)
    return M.rgba(M.dust(rgb, 0.15, r), mask.astype(float))


def _facade_rear(h, w):
    return _rear_boards(h, w, w / FACADE_W, facade_mask(w, h))


def _letters_rear(h, w):
    return _rear_boards(h, w, w / 7.0, letters_mask(w, h))


# ------------------------------------------------------------------------------------------------
# Lights
# ------------------------------------------------------------------------------------------------

def _glass_bulb(m, centre, scale=1.0, kind=G.EMIT_BULB, halo=0.6, candle=False, sides=6):
    """A clear bulb (pear or candle flame) in the glow layer, with its emitter."""
    cx, cy, cz = centre
    e = m.emitter(centre, kind, halo)
    if candle:
        prof = [(0.0, -0.045), (0.02, -0.025), (0.022, 0.0), (0.014, 0.03), (0.0, 0.055)]
    else:
        prof = [(0.0, -0.075), (0.03, -0.065), (0.045, -0.03), (0.042, 0.01), (0.022, 0.05), (0.015, 0.085)]
    prof = [(a * scale, b * scale) for a, b in prof]
    # Six sides, five or four rings: bulbs are drawn every frame (their brightness changes) and
    # the glow texture hides the facets, so they are kept very light.
    G.lathe(m, prof, sides, "de_bulb", centre=(cx, cy, cz), layer=G.LAYER_GLOW, emitter=e, v0=1.0, v1=0.0)
    return e


def bulb(shade=False) -> G.Mesh:
    """A bare bulb hanging on its flex from a bakelite rose (BULB: anchor = the bulb's air cell
    under the ceiling; the ceiling is at local y 1). Budget 90 quads: there are hundreds."""
    m = G.Mesh("bulb_shade" if shade else "bulb", pivot=(0.5, 0.5))
    c = (0.5, 0.0, 0.5)
    G.lathe(m, [(0.0, 0.965), (0.055, 0.968), (0.07, 0.985), (0.07, 1.0)], 6, "de_bakelite", centre=c)
    top = 0.6 if not shade else 0.66
    G.tube(m, [(0.5, 0.97, 0.5), (0.5, top + 0.02, 0.5)], 0.007, 4, "de_cord", v_per_block=4.0)
    G.lathe(m, [(0.0, top - 0.06), (0.021, top - 0.06), (0.012, top + 0.02), (0.0, top + 0.02)], 6,
            "de_brass", centre=c, v0=0.6, v1=0.3)
    if shade:
        # A chipped enamel cone shade (one ring: the rolled rim is in the texture's shading).
        G.lathe(m, [(0.03, top - 0.05), (0.09, top - 0.1), (0.165, top - 0.175)], 10, "de_paint_cream", centre=c,
                v0=0.9, v1=0.4)
    _glass_bulb(m, (0.5, 0.45, 0.5))
    return m


def bulb_cage() -> G.Mesh:
    """A caged work lamp (the Glass zone and the exit hallway): a steel lampholder on its flex
    with a wire guard of four bent bars round a bulb that gives cold light."""
    m = G.Mesh("bulb_cage", pivot=(0.5, 0.5))
    c = (0.5, 0.0, 0.5)
    G.lathe(m, [(0.0, 0.97), (0.05, 0.975), (0.05, 1.0)], 6, "de_bakelite", centre=c)
    G.tube(m, [(0.5, 0.975, 0.5), (0.5, 0.64, 0.5)], 0.007, 4, "de_cord", v_per_block=4.0)
    # Lampholder and the guard's collar.
    G.lathe(m, [(0.0, 0.54), (0.06, 0.55), (0.022, 0.65), (0.0, 0.65)], 6, "de_galv", centre=c)
    # Guard bars: from the collar out round the bulb and in to the bottom point.
    for k in range(4):
        a = k * math.pi / 2 + math.pi / 4
        ca, sa = math.cos(a), math.sin(a)
        pts = [(0.5 + ca * rr, y, 0.5 + sa * rr) for rr, y in ((0.058, 0.55), (0.075, 0.44), (0.0, 0.365))]
        G.tube(m, pts, 0.005, 3, "de_wire", v_per_block=2.0)
    _glass_bulb(m, (0.5, 0.47, 0.5), scale=0.95, kind=G.EMIT_COLD, halo=0.5)
    return m


def sconce() -> G.Mesh:
    """A brass candle sconce on the wall (+z face of the cell) with a flame-shaped bulb in a card
    candle sleeve."""
    m = G.Mesh("sconce", pivot=(0.5, 0.5))
    # Backplate: an oval brass plate.
    plate = G.Mesh("plate")
    G.lathe(plate, [(0.0, 0.0), (0.065, 0.002), (0.08, 0.01), (0.0, 0.018)], 12, "de_brass", centre=(0.0, 0.0, 0.0))
    rot = np.array([[1, 0, 0], [0, 0, -1], [0, 1, 0]], float)  # lathe axis y -> -z
    m.add(plate, rot @ G.scale(1.0, 1.0, 1.6), (0.5, 0.33, WALL_Z))
    # Arm: a curved tube out from the plate and up to the cup.
    arm = [(0.5, 0.30, WALL_Z - 0.01), (0.5, 0.27, WALL_Z - 0.08), (0.5, 0.27, WALL_Z - 0.15), (0.5, 0.29, WALL_Z - 0.2),
           (0.5, 0.32, WALL_Z - 0.22)]
    G.tube(m, arm, 0.011, 6, "de_brass", v_per_block=2.0)
    cup_c = (0.5, 0.0, WALL_Z - 0.22)
    G.lathe(m, [(0.0, 0.315), (0.02, 0.315), (0.052, 0.332), (0.025, 0.335), (0.02, 0.34)], 10, "de_brass", centre=cup_c)
    G.lathe(m, [(0.017, 0.34), (0.017, 0.43), (0.0, 0.43)], 8, "de_candle", centre=cup_c, v0=1.0, v1=0.0)
    _glass_bulb(m, (0.5, 0.465, WALL_Z - 0.22), kind=G.EMIT_CANDLE, halo=0.35, candle=True)
    return m


def exit_sign() -> G.Mesh:
    """A boxed EXIT sign on the wall, face lit from inside (glow layer)."""
    m = G.Mesh("exit_sign", pivot=(0.5, 0.5))
    w, h, d = 0.46, 0.2, 0.09
    lo = (0.5 - w / 2, 0.45 - h / 2, WALL_Z - d)
    hi = (0.5 + w / 2, 0.45 + h / 2, WALL_Z)
    G.bevel_box(m, lo, hi, 0.012, "de_signbox", rsize=(1.0, 1.0), bottom=True)
    e = m.emitter((0.5, 0.45, WALL_Z - d - 0.01), G.EMIT_SIGN, 0.5)
    G.card(m, (0.5 + w / 2 - 0.02, 0.45 - h / 2 + 0.02, WALL_Z - d - 0.002), (-1, 0, 0), (0, 1, 0), w - 0.04, h - 0.04,
           "de_exit", layer=G.LAYER_GLOW, emitter=e, normal=np.array([0.0, 0.0, -1.0]))
    # A bracket strip and the conduit up into the ceiling.
    G.tube(m, [(0.5, 0.45 + h / 2, WALL_Z - 0.02), (0.5, 1.0, WALL_Z - 0.02)], 0.012, 6, "de_wire", v_per_block=3.0)
    return m


# ------------------------------------------------------------------------------------------------
# Foyer
# ------------------------------------------------------------------------------------------------

FACADE_W = 9.0
FACADE_H = 3.1
DOOR_X0, DOOR_X1, DOOR_TOP = 3.0, 5.0, 2.6


def facade_mask(cols, rows):
    """The painted board front (9 x 3.1 blocks) with its arched door (x 3..6, up to 2.7)."""
    Y, X = np.mgrid[0:rows, 0:cols].astype(float)
    x = (X + 0.5) / cols * FACADE_W
    y = FACADE_H - (Y + 0.5) / rows * FACADE_H
    cx = (DOOR_X0 + DOOR_X1) / 2
    rad = (DOOR_X1 - DOOR_X0) / 2
    spring = DOOR_TOP - rad * 0.6
    door = (np.abs(x - cx) < rad) & ((y < spring) | (np.hypot((x - cx) / rad, (y - spring) / (rad * 0.6)) < 1.0))
    return ~door


def letters_mask(cols, rows):
    """The lettering board above the facade: a wide board with an arched hump in the middle."""
    Y, X = np.mgrid[0:rows, 0:cols].astype(float)
    x = (X + 0.5) / cols
    y = 1 - (Y + 0.5) / rows
    top = 0.62 + 0.38 * np.clip(1 - ((x - 0.5) / 0.32) ** 2, 0, 1) ** 0.5
    return y < top


def facade() -> G.Mesh:
    """The funhouse front in the foyer, facing -z: painted boards with the arched way in, the
    lettering board on top, a frame of dead and dying marquee bulbs round both."""
    m = G.Mesh("facade", pivot=(4.5, 0.5))
    z_front = 0.62
    depth = 0.14
    mask = facade_mask(144, 50)
    # The face textures agree with the masks (each region's alpha is drawn from the same shape).
    # The painted side is the cut-out's back (toward -z, the foyer); its front, toward the maze,
    # is the bare boards and battens of the rear.
    G.mask_extrude(m, mask, (FACADE_W, FACADE_H), (0.0, 0.0, z_front), (1, 0, 0), (0, 1, 0), (0, 0, 1), depth,
                   "de_facade_rear", "de_paint_red", edge_rsize=(2.0, 2.0), back_region="de_facade")
    lm = letters_mask(96, 16)
    G.mask_extrude(m, lm, (7.0, 0.85), (0.5, FACADE_H, z_front + 0.02), (1, 0, 0), (0, 1, 0), (0, 0, 1), 0.1,
                   "de_letters_rear", "de_paint_red", edge_rsize=(2.0, 2.0), back_region="de_letters")
    # Pilasters: square painted posts at both ends with gilt caps.
    for x in (0.0, FACADE_W - 0.3):
        G.bevel_box(m, (x, 0.0, z_front - 0.12), (x + 0.3, FACADE_H + 0.2, z_front + depth), 0.02, "de_paint_red",
                    rsize=(2.0, 2.0))
        G.lathe(m, [(0.0, FACADE_H + 0.2), (0.12, FACADE_H + 0.2), (0.14, FACADE_H + 0.24), (0.1, FACADE_H + 0.3),
                    (0.06, FACADE_H + 0.38), (0.0, FACADE_H + 0.42)], 12, "de_brass", centre=(x + 0.15, 0.0, z_front - 0.0))
    # Marquee bulbs: round the lettering board and the door arch, every 0.3 block.
    pts = []
    for k in range(23):
        t = k / 22
        x = 0.65 + t * 6.7
        xx = (x - 0.5) / 7.0
        top = 0.62 + 0.38 * max(0.0, 1 - ((xx - 0.5) / 0.32) ** 2) ** 0.5
        pts.append((x, FACADE_H + top * 0.85 - 0.08))
    cx = (DOOR_X0 + DOOR_X1) / 2
    rad = (DOOR_X1 - DOOR_X0) / 2
    spring = DOOR_TOP - rad * 0.6
    for k in range(13):
        a = math.pi * k / 12
        pts.append((cx - math.cos(a) * (rad + 0.12), spring + math.sin(a) * (rad * 0.6 + 0.12)))
    for (x, y) in pts:
        zc = z_front - 0.03
        G.lathe(m, [(0.0, -0.035), (0.03, -0.035), (0.03, -0.0), (0.02, 0.0)], 8, "de_brass", centre=(0, 0, 0)) if False else None
        sock = G.Mesh("s")
        G.lathe(sock, [(0.0, 0.0), (0.032, 0.0), (0.032, 0.02), (0.022, 0.03), (0.0, 0.03)], 8, "de_brass",
                centre=(0.0, 0.0, 0.0))
        rot = np.array([[1, 0, 0], [0, 0, 1], [0, -1, 0]], float)  # lathe axis y -> -z (out of the board)
        m.add(sock, rot, (x, y, zc + 0.03))
        bulbm = G.Mesh("b")
        _glass_bulb(bulbm, (0.0, 0.0, 0.0), scale=0.55, kind=G.EMIT_MARQUEE, halo=0.18)
        m.add(bulbm, rot, (x, y, zc - 0.02))
    m.contact_ao(0.0, None, reach=0.3, strength=0.4)
    return m


def ticket_booth() -> G.Mesh:
    """A 2 x 2 ticket booth: panelled walls, an arched window toward -z with a counter, a roof with
    a scalloped valance, the TICKETS sign on top."""
    m = G.Mesh("ticket_booth", pivot=(1.0, 1.0))
    x0, x1, z0, z1 = 0.12, 1.88, 0.12, 1.88
    hgt = 2.35
    # Lower walls (panelled) all round, front wall below the window.
    for (a, b, nrm) in (((x0, z0), (x1, z0), (0, 0, -1)), ((x1, z0), (x1, z1), (1, 0, 0)), ((x1, z1), (x0, z1), (0, 0, 1)),
                        ((x0, z1), (x0, z0), (-1, 0, 0))):
        front = nrm == (0, 0, -1)
        top = 1.05 if front else hgt
        L = math.hypot(b[0] - a[0], b[1] - a[1])
        n_pan = max(1, int(round(L / 0.86)))
        for k in range(n_pan):
            t0, t1 = k / n_pan, (k + 1) / n_pan
            pa = (a[0] + (b[0] - a[0]) * t0, a[1] + (b[1] - a[1]) * t0)
            pb = (a[0] + (b[0] - a[0]) * t1, a[1] + (b[1] - a[1]) * t1)
            for (ya, yb) in ((0.0, min(top, 1.05)),) + (((1.05, hgt),) if not front else ()):
                m.quad([(pa[0], ya, pa[1]), (pb[0], ya, pb[1]), (pb[0], yb, pb[1]), (pa[0], yb, pa[1])],
                       [(0, 1), (1, 1), (1, 0), (0, 0)], "de_booth", nrm)
    # Front window: posts either side, the arched head, the cracked glass.
    zf = z0
    for x in (x0, x1 - 0.18):
        G.bevel_box(m, (x, 1.05, zf - 0.04), (x + 0.18, hgt, zf + 0.04), 0.015, "de_paint_red", rsize=(2.0, 2.0))
    head = booth_head_mask(24, 64)
    G.mask_extrude(m, head, (x1 - x0 - 0.36, 0.5), (x0 + 0.18, hgt - 0.5, zf - 0.035), (1, 0, 0), (0, 1, 0), (0, 0, 1), 0.07,
                   "de_booth_head", "de_paint_red")
    G.card(m, (x0 + 0.18, 1.12, zf), (1, 0, 0), (0, 1, 0), x1 - x0 - 0.36, hgt - 1.12 - 0.08, "de_booth_glass",
           layer=G.LAYER_GLASS, normal=np.array([0.0, 0.0, -1.0]))
    # Counter shelf.
    G.bevel_box(m, (x0 - 0.05, 1.02, zf - 0.22), (x1 + 0.05, 1.07, zf + 0.05), 0.012, "de_paint_red", rsize=(2.0, 2.0))
    # A roll of tickets left on the counter.
    G.lathe(m, [(0.0, 1.07), (0.06, 1.07), (0.06, 1.12), (0.0, 1.12)], 10, "de_litter", centre=(1.3, 0.0, zf - 0.1),
            u_turns=0.25)
    # Loose tickets: a few torn off and left on the counter, two more dropped on the floor.
    for k, (tx, ty, tz, a) in enumerate(((0.55, 1.072, zf - 0.12, 0.3), (0.72, 1.074, zf - 0.07, -0.5),
                                         (0.6, 1.076, zf - 0.15, 1.2), (0.95, 0.004, -0.55, 0.8), (0.4, 0.005, -0.35, 2.1))):
        U = np.array([math.cos(a), 0.0, math.sin(a)]) * 0.06
        V = np.array([-math.sin(a), 0.0, math.cos(a)]) * 0.026
        o = np.array([tx, ty, tz])
        m.quad([o - U - V, o + U - V, o + U + V, o - U + V], [(0.0, 1.0), (0.25, 1.0), (0.25, 0.0), (0.0, 0.0)], "de_litter",
               (0, 1, 0))
    # The CLOSED card hung on a string inside the window (seen through the cracked glass).
    zc = zf + 0.05
    G.card(m, (1.17, 1.52, zc), (-1, 0, 0), (0, 1, 0), 0.4, 0.24, "de_closed", normal=np.array([0.0, 0.0, -1.0]))
    G.tube(m, [(1.13, 1.74, zc + 0.002), (0.97, 2.02, zc + 0.002), (0.81, 1.74, zc + 0.002)], 0.004, 3, "de_cord",
           v_per_block=2.0)
    # The A-board price list leaning against the front, below the counter, toward the queue.
    lean = G.norm(np.array([0.0, 0.86, 0.26]))
    G.card(m, (1.72, 0.0, -0.36), (-1, 0, 0), lean, 0.6, 0.8, "de_price", normal=G.norm(np.array([0.0, 0.26, -0.86])))
    G.box(m, (1.12, 0.0, -0.37), (1.72, 0.03, -0.33), "de_crate", rsize=(0.84, 0.62), faces="xXyz")
    # Roof: a shallow pyramid with a valance.
    roof_y = hgt
    cx = cz = 1.0
    for k in range(4):
        sub = G.Mesh("r")
        sub.quad([(x0 - 0.12, roof_y + 0.05, z0 - 0.12), (x1 + 0.12, roof_y + 0.05, z0 - 0.12), (cx, roof_y + 0.45, cz),
                  (cx, roof_y + 0.45, cz)], [(0, 1), (1, 1), (0.5, 0), (0.5, 0)], "de_paint_red")
        m.add(sub, G.rot_y(k * math.pi / 2), (cx, 0, cz) - G.rot_y(k * math.pi / 2) @ np.array([cx, 0, cz]))
    val = valance_mask(10, 80)
    for k in range(4):
        sub = G.Mesh("v")
        G.mask_extrude(sub, val, (x1 - x0 + 0.24, 0.16), (x0 - 0.12, roof_y - 0.11, z0 - 0.13), (1, 0, 0), (0, 1, 0),
                       (0, 0, 1), 0.02, "de_valance", "de_paint_cream")
        m.add(sub, G.rot_y(k * math.pi / 2), (cx, 0, cz) - G.rot_y(k * math.pi / 2) @ np.array([cx, 0, cz]))
    # The sign on the roof's front.
    G.bevel_box(m, (0.35, roof_y + 0.12, z0 - 0.05), (1.65, roof_y + 0.48, z0 + 0.0), 0.01, "de_paint_red", rsize=(2, 2))
    G.card(m, (1.62, roof_y + 0.14, z0 - 0.052), (-1, 0, 0), (0, 1, 0), 1.24, 0.32, "de_booth_sign",
           normal=np.array([0.0, 0.0, -1.0]))
    m.contact_ao(0.0, None, reach=0.35, strength=0.45)
    return m


def turnstile() -> G.Mesh:
    """A three-arm brass and steel turnstile on a pedestal."""
    m = G.Mesh("turnstile", pivot=(0.5, 0.5))
    c = (0.5, 0.0, 0.5)
    G.lathe(m, [(0.0, 0.0), (0.16, 0.0), (0.16, 0.04), (0.1, 0.06), (0.08, 0.1), (0.075, 0.85), (0.1, 0.9), (0.1, 1.0),
                (0.06, 1.04), (0.0, 1.05)], 12, "de_galv", centre=c, v0=1.0, v1=0.0)
    hub_y = 0.95
    for k in range(3):
        a = k * 2 * math.pi / 3 + 0.3
        d = np.array([math.cos(a), -0.35, math.sin(a)])
        d = d / np.linalg.norm(d)
        p0 = np.array([0.5, hub_y, 0.5])
        G.tube(m, [p0, p0 + d * 0.48], 0.022, 8, "de_brass", v_per_block=1.0)
    m.contact_ao(0.0, None)
    return m


def stanchion(fallen=False) -> G.Mesh:
    """A brass queue post. Variant 1: knocked over, lying on the floor."""
    m = G.Mesh("stanchion_fallen" if fallen else "stanchion", pivot=(0.5, 0.5))
    sub = G.Mesh("s")
    prof = [(0.0, 0.0), (0.15, 0.0), (0.155, 0.015), (0.14, 0.035), (0.07, 0.06), (0.03, 0.08), (0.024, 0.1),
            (0.024, 0.86), (0.032, 0.88), (0.032, 0.9), (0.024, 0.91), (0.04, 0.94), (0.045, 0.97), (0.035, 1.0),
            (0.0, 1.01)]
    G.lathe(sub, prof, 14, "de_brass", centre=(0.0, 0.0, 0.0), v0=1.0, v1=0.0)
    # The rope hook ring at the top.
    G.tube(sub, [(0.04 * math.cos(t), 0.92, 0.04 * math.sin(t)) for t in np.linspace(0, 2 * math.pi, 9)], 0.006, 4, "de_brass")
    if fallen:
        rot = np.array([[0, 1, 0], [-1, 0, 0], [0, 0, 1]], float) @ G.rot_y(0.4)
        m.add(sub, rot, (0.05, 0.155, 0.5))
        rope = G.catenary((1.05, 0.04, 0.5), (1.9, 0.02, 1.1), 0.0, 10)
        G.tube(m, rope, 0.018, 6, "de_velvet", v_per_block=1.5)
    else:
        m.add(sub, None, (0.5, 0.0, 0.5))
    return m


def stanchion_rope() -> G.Mesh:
    """The sagging velvet rope from a post to the next post 2 blocks along +x."""
    m = G.Mesh("stanchion_rope", pivot=(1.5, 0.5))
    pts = G.catenary((0.5 + 0.045, 0.9, 0.5), (2.5 - 0.045, 0.9, 0.5), 0.32, 16)
    G.tube(m, pts, 0.018, 7, "de_velvet", v_per_block=1.5)
    for p in (pts[0], pts[-1]):
        G.lathe(m, [(0.0, -0.03), (0.024, -0.03), (0.024, 0.03), (0.0, 0.03)], 8, "de_brass", centre=tuple(p))
    return m


def poster(i) -> G.Mesh:
    """A bill pasted on the wall, slightly peeling at one corner (a curled flap)."""
    m = G.Mesh(f"poster_{i}", pivot=(0.5, 0.5))
    w, h = 0.72, 1.0
    x0 = 0.5 - w / 2
    # Pasted at eye height, above the foyer's dado and chair rail (1.14).
    y0 = 1.2 + (i % 3) * 0.08
    z = WALL_Z - 0.004
    # Split into a 4 x 4 grid so the curled corner can lift.
    for a in range(4):
        for b in range(4):
            def P(u, v):
                lift = 0.0
                if i % 2 == 0:
                    lift = max(0.0, u - 0.6) * max(0.0, 0.4 - v) * 0.35  # bottom-right corner curls
                return (x0 + w * (1 - u), y0 + h * (1 - v) + 0.0, z - lift)
            u0, u1, v0, v1 = a / 4, (a + 1) / 4, b / 4, (b + 1) / 4
            m.quad([P(u0, v1), P(u1, v1), P(u1, v0), P(u0, v0)], [(u0, v1), (u1, v1), (u1, v0), (u0, v0)],
                   f"de_poster_{i}", (0, 0, -1))
    return m


def note(i) -> G.Mesh:
    m = G.Mesh(f"note_{i}", pivot=(0.5, 0.5))
    w, h = 0.24, 0.31
    x0 = 0.5 - w / 2 + (i - 1) * 0.12
    y0 = 1.3 + 0.1 * i  # pinned at eye height
    z = WALL_Z - 0.003
    G.card(m, (x0 + w, y0, z), (-1, 0, 0), (0, 1, 0), w, h, f"de_note_{i}", normal=np.array([0.0, 0.0, -1.0]))
    # The drawing pin.
    G.lathe(m, [(0.0, 0.0), (0.012, 0.0), (0.012, 0.004), (0.0, 0.006)], 8, "de_brass", centre=(0, 0, 0)) if False else None
    pin = G.Mesh("pin")
    G.lathe(pin, [(0.0, 0.0), (0.013, 0.0), (0.013, 0.004), (0.008, 0.006), (0.0, 0.007)], 8, "de_brass", centre=(0, 0, 0))
    m.add(pin, np.array([[1, 0, 0], [0, 0, 1], [0, -1, 0]], float), (x0 + w / 2, y0 + h - 0.03, z - 0.001))
    return m


def bunting() -> G.Mesh:
    """A string of pennants sagging across a segment from column to column (in the band, x -0.5
    to 2.5 at the pane plane), a couple of flags missing."""
    m = G.Mesh("bunting", pivot=(1.0, 0.5))
    pts = G.catenary((-0.4, 2.98, PLANE), (2.4, 2.98, PLANE), 0.38, 20)
    # A pale tarred-twine line, thick enough to read from across a room against the dark band
    # (the mop's grey-beige fibres are the right stuff).
    G.tube(m, pts, 0.014, 4, "de_mop", v_per_block=2.0)
    pts = np.asarray(pts)
    n_flags = 11
    for k in range(n_flags):
        if k in (3, 8):
            continue
        t = (k + 0.5) / n_flags
        i = t * (len(pts) - 1)
        j = int(i)
        p = pts[j] + (pts[min(j + 1, len(pts) - 1)] - pts[j]) * (i - j)
        w = 0.2
        u0 = (k % 6) / 6
        tw = 0.35 * math.sin(k * 1.7)
        U = np.array([1.0, 0.0, 0.0])
        D = G.norm(np.array([0.0, -1.0, tw]))
        G.card(m, p - U * w / 2, U, D, w, 0.26, "de_bunting", uv=((u0, 0.0), (u0 + 1 / 6, 1.0)))
    return m


def shards(v) -> G.Mesh:
    """Broken mirror on the floor: a scatter of shards, a few lying on each other."""
    m = G.Mesh(f"shards_{v}", pivot=(0.5, 0.5))
    r = M.rng(22920 + v)
    for k in range(7 + 3 * v):
        cx, cz = 0.15 + 0.7 * r.random(), 0.15 + 0.7 * r.random()
        s = 0.06 + 0.12 * r.random()
        a = r.random() * 2 * math.pi
        U = np.array([math.cos(a), 0.0, math.sin(a)])
        V = np.array([-math.sin(a), 0.0, math.cos(a)])
        y = 0.004 + 0.003 * k
        qx, qy = int(r.integers(2)), int(r.integers(2))
        tilt = np.array([0.0, r.random() * 0.04, 0.0])
        o = np.array([cx, y, cz]) - U * s / 2 - V * s / 2
        m.quad([o, o + U * s + tilt, o + U * s + V * s + tilt, o + V * s], [(qx / 2, (qy + 1) / 2), ((qx + 1) / 2, (qy + 1) / 2),
                                                                           ((qx + 1) / 2, qy / 2), (qx / 2, qy / 2)],
               "de_shards", (0, 1, 0))
    return m


def panel_empty_shards() -> G.Mesh:
    """What is left in an empty frame: jagged glass stuck in the top and bottom rails, and the
    rest of it on the floor below."""
    m = G.Mesh("panel_empty_shards", pivot=(1.0, 0.5))
    r = M.rng(22930)
    for (y, up) in ((PANE_Y0 + 0.03, True), (PANE_Y1 - 0.03, False)):
        x = -0.2
        while x < 2.2:
            w = 0.1 + 0.25 * r.random()
            hgt = 0.05 + 0.2 * r.random() ** 2
            tip = x + w * r.random()
            yt = y + hgt if up else y - hgt
            qx = int(r.integers(2))
            m.tri((x, y, PLANE), (x + w, y, PLANE), (tip, yt, PLANE), (qx / 2, 1.0), ((qx + 1) / 2, 1.0),
                  ((qx + 0.5) / 2, 0.55), "de_shards", n=(0, 0, 1))
            x += w + 0.05 * r.random()
    floor = shards(2)
    m.add(floor, None, (0.5, 0.0, -0.4))
    m.add(floor, G.rot_y(1.3), (1.4, 0.0, 0.9))
    return m


def cobweb() -> G.Mesh:
    """A web across the top corner where the cell's +z wall meets its +x side, with dust strands."""
    m = G.Mesh("cobweb", pivot=(0.5, 0.5))
    # Corner at (1, 1, 1): the web spans from the wall (+z) to the side (+x), hanging from the top.
    a = np.array([1.0, 1.0, 1.0 - 0.7])
    b = np.array([1.0 - 0.7, 1.0, 1.0])
    c = np.array([1.0, 0.25, 1.0])
    m.quad([b, a, a, c], [(0.0, 0.0), (1.0, 0.0), (1.0, 0.0), (1.0, 1.0)], "de_cobweb", None)
    m.quad([b, a + np.array([0, -0.02, 0]), c, c], [(0.0, 0.0), (1.0, 0.02), (1.0, 1.0), (1.0, 1.0)], "de_cobweb", None)
    # Second, smaller web lower down on the wall.
    d = np.array([0.98, 0.75, 0.99])
    m.quad([d + np.array([-0.4, 0.2, 0]), d + np.array([0, 0.2, 0]), d + np.array([0, -0.25, 0]), d + np.array([-0.4, -0.25, 0])],
           [(0.0, 0.0), (1.0, 0.0), (1.0, 1.0), (0.0, 1.0)], "de_cobweb", (0, 0, -1))
    return m


def litter(v) -> G.Mesh:
    """Floor litter: ticket stubs, a popcorn box on its side, a crumpled wrapper, a paper cup."""
    m = G.Mesh(f"litter_{v}", pivot=(0.5, 0.5))
    r = M.rng(22940 + v)
    for k in range(4 + v):
        cx, cz = 0.15 + 0.7 * r.random(), 0.15 + 0.7 * r.random()
        a = r.random() * math.pi
        U = np.array([math.cos(a), 0.0, math.sin(a)])
        V = np.array([-math.sin(a), 0.0, math.cos(a)])
        s = 0.05 + 0.03 * r.random()
        o = np.array([cx, 0.005 + 0.002 * k, cz])
        m.quad([o - U * s - V * s * 0.45, o + U * s - V * s * 0.45, o + U * s + V * s * 0.45, o - U * s + V * s * 0.45],
               [(0.0, 1.0), (0.25, 1.0), (0.25, 0.0), (0.0, 0.0)], "de_litter", (0, 1, 0))
    if v != 1:
        # Popcorn box lying on its side.
        box = G.Mesh("box")
        G.box(box, (-0.06, 0.0, -0.04), (0.06, 0.08, 0.04), "de_litter", rsize=(0.48, 0.12), off=(0.25, 0.0), faces="xXyYz")
        m.add(box, np.array([[1, 0, 0], [0, 0, -1], [0, 1, 0]], float) @ G.rot_y(r.random() * 3), (0.3 + 0.4 * r.random(), 0.04,
                                                                                                    0.3 + 0.4 * r.random()))
    # Crumpled wrapper (a lumpy octahedron).
    cx, cz = 0.2 + 0.6 * r.random(), 0.2 + 0.6 * r.random()
    s = 0.035
    top = np.array([cx, 0.05, cz])
    ring = [np.array([cx + math.cos(t) * s * (0.8 + 0.4 * r.random()), 0.02, cz + math.sin(t) * s * (0.8 + 0.4 * r.random())])
            for t in np.linspace(0, 2 * math.pi, 7)[:-1]]
    for j in range(6):
        p, q = ring[j], ring[(j + 1) % 6]
        m.tri(top, p, q, (0.6, 0.2), (0.55, 0.8), (0.7, 0.8), "de_litter")
        m.tri(np.array([cx, 0.002, cz]), q, p, (0.6, 0.2), (0.55, 0.8), (0.7, 0.8), "de_litter")
    if v == 2:
        # A flattened paper cup.
        G.lathe(m, [(0.03, 0.0), (0.04, 0.09), (0.0, 0.09)], 10, "de_litter", centre=(0.0, 0.0, 0.0), u_turns=0.25,
                u_offset=0.75) if False else None
        cup = G.Mesh("cup")
        G.lathe(cup, [(0.028, 0.0), (0.04, 0.1)], 10, "de_litter", centre=(0, 0, 0), u_turns=0.24, u_offset=0.755)
        m.add(cup, np.array([[1, 0, 0], [0, 0, -1], [0, 1, 0]], float) @ G.scale(1.0, 1.0, 0.6), (0.6, 0.025, 0.4))
    return m


def chandelier() -> G.Mesh:
    """The final chamber's candle chandelier (a P_BULB of kind CANDLE, mirrored into the three
    sealed images): a ceiling cup, a short chain, a turned brass baluster with five S-scroll arms,
    each ending in a drip pan with a wax-dripped candle and a flame bulb. Aged brass gone brown,
    wax run down the candles and onto the pans. Centred on the anchor cell's +z edge (the room's
    middle line, between the two cells its light spans), the flames at about the light's height.

    ONE emitter, at the middle of the flame ring, drives all five flames: they light, flicker and
    go out together with the record's state and phase (identical in the room and in every image,
    which share Prop.noise and flags; the exit puzzle's unlit candle must be all out), and one
    halo instead of five keeps the cluster from blooming into a blob."""
    m = G.Mesh("chandelier", pivot=(0.5, 1.0))
    cx, cz = 0.5, 1.0
    c = (cx, 0.0, cz)
    flame_y = 0.715  # the flames' middle (candles 0.6 + ~0.085, flame centre 0.03 above)
    e = m.emitter((cx, flame_y, cz), G.EMIT_CANDLE, 0.45)
    # Ceiling cup (the canopy hiding the hook).
    G.lathe(m, [(0.0, 0.955), (0.03, 0.958), (0.055, 0.972), (0.068, 0.99), (0.068, 1.0)], 10, "de_brass", centre=c)
    # Chain: five oval links, each at right angles to the last.
    y = 0.955
    for k in range(5):
        h, w = 0.034, 0.012
        cy = y - h / 2
        ang = 0.0 if k % 2 == 0 else math.pi / 2
        ca, sa = math.cos(ang), math.sin(ang)
        loop = [(cx + ca * w * math.sin(t), cy + h / 2 * math.cos(t), cz + sa * w * math.sin(t))
                for t in np.linspace(0, 2 * math.pi, 7)]
        G.tube(m, loop, 0.0035, 3, "de_brass", v_per_block=4.0)
        y -= h * 0.78
    top = y
    # Baluster: a hook ring, a vase, a ball, and the arms' boss with a pointed drop below.
    G.lathe(m, [(0.0, 0.548), (0.012, 0.552), (0.03, 0.575), (0.042, 0.6), (0.032, 0.615), (0.016, 0.64),
                (0.024, 0.68), (0.02, 0.705), (0.009, 0.73), (0.012, top - 0.008), (0.0, top)], 8, "de_brass", centre=c)
    G.lathe(m, [(0.0, 0.5), (0.012, 0.52), (0.022, 0.548), (0.0, 0.552)], 8, "de_brass", centre=c)
    ring_r = 0.19
    r = np.random.default_rng(22880)
    for k in range(5):
        a = k * 2 * math.pi / 5 + 0.3
        ca, sa = math.cos(a), math.sin(a)
        # S-scroll arm: out from the boss, dipping, curling up under the pan.
        prof = [(0.03, 0.585), (0.07, 0.57), (0.11, 0.545), (0.15, 0.53), (0.18, 0.535), (ring_r, 0.55), (ring_r, 0.575)]
        pts = [(cx + ca * d, yy, cz + sa * d) for d, yy in prof]
        G.tube(m, pts, 0.0065, 4, "de_brass", v_per_block=2.0)
        # A small curl hanging under the arm (the scroll's tail).
        G.tube(m, [(cx + ca * 0.12, 0.543, cz + sa * 0.12), (cx + ca * 0.135, 0.52, cz + sa * 0.135),
                   (cx + ca * 0.12, 0.505, cz + sa * 0.12)], 0.004, 3, "de_brass", v_per_block=2.0)
        px, pz = cx + ca * ring_r, cz + sa * ring_r
        pc = (px, 0.0, pz)
        # Drip pan (bobeche) with a socket.
        G.lathe(m, [(0.0, 0.572), (0.022, 0.574), (0.04, 0.585), (0.043, 0.59), (0.024, 0.59), (0.014, 0.6)], 8,
                "de_brass", centre=pc)
        # Candle: slightly uneven height, wax swelling where it ran, a softened top.
        hgt = 0.075 + 0.02 * r.random()
        G.lathe(m, [(0.0136, 0.6), (0.015, 0.6 + hgt * 0.3), (0.0128, 0.6 + hgt * 0.7), (0.0132, 0.6 + hgt),
                    (0.007, 0.6 + hgt + 0.004), (0.0, 0.6 + hgt + 0.004)], 6, "de_candle", centre=pc, v0=1.0, v1=0.0)
        # Wax runs: two drips down the candle, one pooled on the pan.
        for d in range(2):
            b = a + (r.random() - 0.5) * 2.2
            dx, dz = math.cos(b) * 0.015, math.sin(b) * 0.015
            y0 = 0.6 + hgt - 0.004
            y1 = 0.6 + hgt * (0.25 + 0.4 * r.random())
            G.tube(m, [(px + dx, y0, pz + dz), (px + dx * 1.05, (y0 + y1) / 2, pz + dz * 1.05), (px + dx * 1.1, y1, pz + dz * 1.1)],
                   0.003, 3, "de_candle", v_per_block=3.0, radii=[0.0025, 0.003, 0.0042])
        b = a + r.random() * 6.0
        G.lathe(m, [(0.0, 0.589), (0.012, 0.5905), (0.0, 0.592)], 6, "de_candle",
                centre=(px + math.cos(b) * 0.022, 0.0, pz + math.sin(b) * 0.022))
        # The flame bulb, on the shared emitter.
        fy = 0.6 + hgt + 0.03
        prof = [(0.0, -0.036), (0.016, -0.02), (0.018, 0.0), (0.011, 0.024), (0.0, 0.044)]
        G.lathe(m, prof, 6, "de_bulb", centre=(px, fy, pz), layer=G.LAYER_GLOW, emitter=e, v0=1.0, v1=0.0)
    return m


def ticket_stub() -> G.Mesh:
    """The final chamber's torn ticket stub on the floor in front of each mirror (one of the
    details a wrong mirror's image leaves out, so it must be plain to see): a pale pink card about
    0.3 block long, ADMIT ONE, lying a little curled, turned a few degrees off square."""
    m = G.Mesh("ticket_stub", pivot=(0.5, 0.5))
    a = 0.35
    U = np.array([math.cos(a), 0.0, math.sin(a)])
    V = np.array([-math.sin(a), 0.0, math.cos(a)])
    c = np.array([0.5, 0.006, 0.5])
    w, h = 0.32, 0.15
    for k in range(3):
        # Three strips across the length so the torn end can lift off the floor.
        t0, t1 = k / 3, (k + 1) / 3
        lift0, lift1 = (0.0, 0.0) if k < 2 else (0.0, 0.025)
        p0 = c + U * (w * (t0 - 0.5)) - V * h / 2 + np.array([0, lift0, 0])
        p1 = c + U * (w * (t1 - 0.5)) - V * h / 2 + np.array([0, lift1, 0])
        p2 = c + U * (w * (t1 - 0.5)) + V * h / 2 + np.array([0, lift1, 0])
        p3 = c + U * (w * (t0 - 0.5)) + V * h / 2 + np.array([0, lift0, 0])
        m.quad([p0, p1, p2, p3], [(0.25 * t0, 1.0), (0.25 * t1, 1.0), (0.25 * t1, 0.0), (0.25 * t0, 0.0)], "de_litter",
               (0, 1, 0))
    return m


def leaves(v) -> G.Mesh:
    """Blown-in dry leaves: each one a card folded along its midrib (two quads, the edges curled
    up off the floor), a few lying on others, each a different species from the texture."""
    m = G.Mesh(f"leaves_{v}", pivot=(0.5, 0.5))
    r = M.rng(22950 + v)
    for k in range(6 + 3 * v):
        cx, cz = 0.1 + 0.8 * r.random(), 0.1 + 0.8 * r.random()
        a = r.random() * 2 * math.pi
        s = 0.11 + 0.08 * r.random()
        L = np.array([math.cos(a), 0.0, math.sin(a)])     # along the midrib, stem -> tip
        S = np.array([-math.sin(a), 0.0, math.cos(a)])    # across
        y = 0.004 + 0.004 * k
        curl = 0.015 + 0.04 * r.random()
        arch = 0.01 + 0.02 * r.random()                   # the tip and stem lifted a little too
        qx, qy = (k + v) % 2, ((k + v) // 2) % 2
        u0, um, u1 = qx / 2, qx / 2 + 0.25, qx / 2 + 0.5
        vt, vb = qy / 2, qy / 2 + 0.5
        c = np.array([cx, y, cz])
        up = np.array([0.0, 1.0, 0.0])
        st, tp = c - L * s / 2 + up * arch, c + L * s / 2 + up * arch
        for sg, ue in ((-1, u0), (1, u1)):
            e0 = c - L * s / 2 + S * sg * s / 2 + up * (curl + arch)
            e1 = c + L * s / 2 + S * sg * s / 2 + up * (curl + arch)
            m.quad([st, tp, e1, e0], [(um, vb), (um, vt), (ue, vt), (ue, vb)], "de_leaves", None)
    return m


def crate() -> G.Mesh:
    """A crate of spare mirrors: slatted pine box, open, five mirrors standing in it on edge in
    brown paper, straw spilling."""
    m = G.Mesh("crate", pivot=(0.5, 0.5))
    x0, x1, z0, z1, h = 0.08, 0.92, 0.18, 0.82, 0.62
    # Slatted sides: three boards per side.
    for k in range(3):
        y0, y1 = k * h / 3 + 0.005, (k + 1) * h / 3 - 0.005
        for (lo, hi, face) in (((x0, y0, z0), (x1, y1, z0 + 0.02), "z"), ((x0, y0, z1 - 0.02), (x1, y1, z1), "Z"),
                               ((x0, y0, z0), (x0 + 0.02, y1, z1), "x"), ((x1 - 0.02, y0, z0), (x1, y1, z1), "X")):
            G.box(m, lo, hi, "de_crate", rsize=(0.84, 0.62), off=(0.0, (2 - k) / 3), faces="xXzZY")
    # Corner posts.
    for (x, z) in ((x0, z0), (x1 - 0.05, z0), (x0, z1 - 0.05), (x1 - 0.05, z1 - 0.05)):
        G.box(m, (x, 0.0, z), (x + 0.05, h + 0.02, z + 0.05), "de_crate", rsize=(0.84, 0.62), off=(0.9, 0.0), faces="xXzZY")
    # Mirrors standing in it, leaning a little.
    r = M.rng(22960)
    for k in range(5):
        z = z0 + 0.08 + k * 0.11
        tilt = G.rot_y(0) @ np.array([[1, 0, 0], [0, math.cos(0.08 * (k - 2)), -math.sin(0.08 * (k - 2))],
                                      [0, math.sin(0.08 * (k - 2)), math.cos(0.08 * (k - 2))]])
        pane = G.Mesh("p")
        G.box(pane, (-0.36, 0.0, -0.012), (0.36, 0.78 - 0.04 * (k % 2), 0.012), "de_oldmirror", rsize=(0.72, 0.8),
              faces="zZY")
        # Brown paper wrap on the back half.
        G.box(pane, (-0.37, 0.0, 0.012), (0.37, 0.4, 0.016), "de_litter", rsize=(4.0, 1.0), off=(0.0, 0.0), faces="Z")
        m.add(pane, tilt, (0.5 + 0.03 * r.normal(), 0.02, z))
    m.contact_ao(0.0, None, reach=0.25, strength=0.5)
    return m


def mirror_stack() -> G.Mesh:
    """Three broken mirrors in old frames leaning against the wall (+z), a 1 x 2 run along +x."""
    m = G.Mesh("mirror_stack", pivot=(1.0, 0.5))
    for k, (x, w, h, lean) in enumerate(((0.15, 0.8, 1.7, 0.16), (0.85, 0.9, 1.45, 0.2), (1.2, 0.7, 1.15, 0.25))):
        sub = G.Mesh("f")
        G.box(sub, (0.04, 0.04, -0.015), (w - 0.04, h - 0.04, 0.0), "de_oldmirror", rsize=(w, h), faces="z")
        prof_w = 0.06
        for (lo, hi) in (((0, 0, -0.03), (w, prof_w, 0.02)), ((0, h - prof_w, -0.03), (w, h, 0.02)),
                         ((0, prof_w, -0.03), (prof_w, h - prof_w, 0.02)), ((w - prof_w, prof_w, -0.03), (w, h - prof_w, 0.02))):
            G.bevel_box(sub, lo, hi, 0.01, "de_frame_gilt" if k == 1 else "de_paint_red", rsize=(4.0, 0.25) if k == 1 else (2, 2))
        rx = np.array([[1, 0, 0], [0, math.cos(lean), math.sin(lean)], [0, -math.sin(lean), math.cos(lean)]], float)
        base_z = WALL_Z - 0.04 - math.sin(lean) * h - 0.02 * k
        m.add(sub, rx, (x, 0.0, base_z))
    m.add(shards(1), None, (0.4, 0.0, -0.1))
    m.contact_ao(0.0, None)
    return m


def bucket() -> G.Mesh:
    """A galvanised mop bucket with the mop left standing in it, against the wall."""
    m = G.Mesh("bucket", pivot=(0.5, 0.5))
    c = (0.45, 0.0, 0.62)
    G.lathe(m, [(0.0, 0.01), (0.15, 0.0), (0.155, 0.02), (0.19, 0.36), (0.2, 0.37), (0.19, 0.38), (0.18, 0.37),
                (0.145, 0.03), (0.0, 0.04)], 16, "de_galv", centre=c, v0=1.0, v1=0.0)
    # Dirty water.
    G.lathe(m, [(0.0, 0.22), (0.172, 0.22)], 16, "de_mop", centre=c, v0=0.95, v1=0.9)
    # Handle (a bail wire).
    G.tube(m, [(0.45 - 0.2, 0.36, 0.62), (0.45 - 0.12, 0.52, 0.62), (0.45, 0.56, 0.62), (0.45 + 0.12, 0.52, 0.62),
               (0.45 + 0.2, 0.36, 0.62)], 0.005, 4, "de_galv")
    # Mop: strands in the bucket, handle leaning on the wall.
    G.tube(m, [(0.45, 0.3, 0.6), (0.62, 1.55, WALL_Z - 0.03)], 0.014, 6, "de_crate", v_per_block=0.5)
    G.lathe(m, [(0.0, 0.18), (0.1, 0.2), (0.08, 0.32), (0.02, 0.34), (0.0, 0.34)], 10, "de_mop", centre=(0.45, 0.0, 0.6),
            u_turns=1.0)
    m.contact_ao(0.0, None)
    return m


def banner() -> G.Mesh:
    """THANK YOU FOR VISITING: a cloth banner hung on the wall (+z) across three blocks (x 0..3),
    tacked at its corners and sagging between them."""
    m = G.Mesh("banner", pivot=(1.5, 0.5))

    def f(u, v):
        x = 0.1 + u * 2.8
        sag = 0.08 * math.sin(u * math.pi)
        y = 0.95 - v * 0.62 - sag * (1 - v) - 0.03 * math.sin(u * math.pi * 3) * v
        z = WALL_Z - 0.01 - 0.05 * math.sin(u * math.pi) * (0.3 + v)
        return (x, y, z)

    G.surface(m, f, 12, 4, "de_banner", uv_fn=lambda u, v: (1 - u, v))
    return m


def exit_door() -> G.Mesh:
    """The way on, at the end of the exit hallway: a double doorway in the wall (the 2-block
    segment, x 0..2, wall faces at z 0 and 1), moulded architraves on both faces, a transom board
    above, and both painted steel leaves standing open into the vestibule (+z)."""
    m = G.Mesh("exit_door", pivot=(1.0, 0.5))
    top = 2.6
    for z, s in ((0.0, -1.0), (1.0, 1.0)):
        for (lo, hi) in (((-0.14, 0.0, 0.0), (0.0, top + 0.14, 0.05)), ((2.0, 0.0, 0.0), (2.14, top + 0.14, 0.05)),
                         ((-0.14, top, 0.0), (2.14, top + 0.14, 0.05))):
            lo2 = (lo[0], lo[1], z + (s * lo[2] if s > 0 else -hi[2]))
            hi2 = (hi[0], hi[1], z + (s * hi[2] if s > 0 else -lo[2]))
            G.bevel_box(m, lo2, hi2, 0.015, "de_paint_cream", rsize=(2, 2))
    # Jamb linings through the wall, and the transom.
    for x in (0.0, 2.0):
        m.quad([(x, 0.0, 0.0), (x, 0.0, 1.0), (x, top, 1.0), (x, top, 0.0)], [(0, 1), (1, 1), (1, 0), (0, 0)],
               "de_paint_cream", (1 if x == 0 else -1, 0, 0))
    m.quad([(0.0, top, 0.0), (2.0, top, 0.0), (2.0, top, 1.0), (0.0, top, 1.0)], [(0, 1), (1, 1), (1, 0), (0, 0)],
           "de_paint_cream", (0, -1, 0))
    G.box(m, (0.0, top, 0.45), (2.0, CEIL, 0.55), "de_paint_red", rsize=(2.0, 2.0), faces="zZ")
    for k, (hx, sgn) in enumerate(((0.0, 1.0), (2.0, -1.0))):
        leaf = G.Mesh("leaf")
        G.box(leaf, (0.0, 0.0, 0.0), (0.97, top - 0.03, 0.045), "de_door", rsize=(0.97, top - 0.03), faces="xXzY")
        G.card(leaf, (0.97, 0.0, -0.001), (-1, 0, 0), (0, 1, 0), 0.97, top - 0.03, "de_door",
               normal=np.array([0.0, 0.0, -1.0]))
        G.tube(leaf, [(0.1, 1.02, -0.06), (0.85, 1.02, -0.06)], 0.018, 8, "de_galv")
        for x in (0.12, 0.83):
            G.box(leaf, (x - 0.02, 0.98, -0.06), (x + 0.02, 1.06, 0.0), "de_galv", rsize=(0.5, 0.5))
        # Hinged at the vestibule face of the wall; opened 80 degrees into the vestibule.
        mm = G.scale(sgn, 1, 1)
        ang = -sgn * math.radians(80 + 6 * k)
        m.add(leaf, G.rot_y(ang) @ mm, (hx, 0.005, 1.0))
    m.contact_ao(0.0, None, reach=0.3)
    return m


def chair() -> G.Mesh:
    """A bentwood cafe chair (the furniture of the rooms that are not there)."""
    m = G.Mesh("chair", pivot=(0.5, 0.5))
    seat_y = 0.46
    for (x, z) in ((0.32, 0.32), (0.68, 0.32), (0.68, 0.68), (0.32, 0.68)):
        foot = (0.5 + (x - 0.5) * 1.25, 0.0, 0.5 + (z - 0.5) * 1.25)
        G.tube(m, [foot, (x, seat_y - 0.02, z)], 0.017, 6, "cr_wood_v" if False else "de_crate", v_per_block=1.0)
    G.lathe(m, [(0.0, seat_y - 0.02), (0.21, seat_y - 0.02), (0.22, seat_y), (0.21, seat_y + 0.025), (0.0, seat_y + 0.03)],
            16, "de_crate", centre=(0.5, 0.0, 0.5), u_turns=0.4)
    # Leg ring and the bent back hoop.
    G.tube(m, [(0.5 + 0.2 * math.cos(t), 0.2, 0.5 + 0.2 * math.sin(t)) for t in np.linspace(0, 2 * math.pi, 13)], 0.008, 4,
           "de_crate")
    # The hoop runs from the right back leg (x 0.69) up, over (cos t: right to left) and down to the
    # left one: start, arc and end must be in that order or the strand crosses itself.
    hoop = [(0.5 + 0.19 * math.cos(t), seat_y + 0.3 + 0.2 * math.sin(t) ** 0.6, 0.7 + 0.02 * math.sin(t))
            for t in np.linspace(0, math.pi, 13)]
    G.tube(m, [(0.69, seat_y, 0.69)] + hoop + [(0.31, seat_y, 0.69)], 0.014, 6, "de_crate", v_per_block=1.0)
    # The inner loop, bent from the seat's back edge (so it is held by something).
    inner = [(0.5 + 0.11 * math.cos(t), seat_y + 0.2 + 0.1 * math.sin(t), 0.705) for t in np.linspace(0, math.pi, 9)]
    G.tube(m, [(0.61, seat_y, 0.7)] + inner + [(0.39, seat_y, 0.7)], 0.01, 5, "de_crate")
    m.contact_ao(0.0, None, reach=0.25)
    return m


def curtain() -> G.Mesh:
    """A black velvet strip curtain hung in an opening (the pane plane, column to column, under
    the band), strips swaying apart a little in the middle where people push through."""
    m = G.Mesh("curtain", pivot=(1.0, 0.5))
    n = 14
    r = M.rng(22970)
    G.tube(m, [(-0.45, PANE_Y1 - 0.03, PLANE), (2.45, PANE_Y1 - 0.03, PLANE)], 0.015, 6, "de_brass", v_per_block=1.0)
    for k in range(n):
        x = -0.4 + 2.8 * (k + 0.5) / n
        w = 2.8 / n * 0.96
        part = math.exp(-((x - 1.0) / 0.5) ** 2)
        push = (0.12 * part) * (1 if k % 2 else -1)

        def f(u, v, x=x, w=w, push=push, k=k):
            y = PANE_Y1 - 0.05 - v * (PANE_Y1 - 0.08)
            return (x - w / 2 + u * w + push * v * v * 0.5, y, PLANE + 0.02 * math.sin(k * 1.3 + v * 3) + push * v * v)

        G.surface(m, f, 1, 6, "de_curtain", uv_fn=lambda u, v, k=k: ((k % 3 + u) / 3, v))
    return m


def palm() -> G.Mesh:
    """A dead potted palm in a tarnished brass pot: a ring of drooping dried fronds."""
    m = G.Mesh("palm", pivot=(0.5, 0.5))
    c = (0.5, 0.0, 0.5)
    G.lathe(m, [(0.0, 0.0), (0.17, 0.0), (0.18, 0.02), (0.24, 0.32), (0.26, 0.36), (0.25, 0.38), (0.22, 0.36),
                (0.0, 0.34)], 16, "de_pot", centre=c, v0=1.0, v1=0.0)
    r = M.rng(22975)
    for k in range(9):
        a = k * 2 * math.pi / 9 + r.normal() * 0.2
        L = 0.8 + 0.4 * r.random()
        droop = 0.6 + 0.5 * r.random()

        def f(u, v, a=a, L=L, droop=droop):
            s = v * L
            out = s * math.cos(0.9 - droop * v)
            y = 0.36 + 0.5 * math.sin(0.9) * s - droop * 0.5 * s * v
            side = (u - 0.5) * 0.28 * math.sin(v * math.pi) ** 0.6
            return (0.5 + math.cos(a) * out - math.sin(a) * side, y + abs(u - 0.5) * 0.05, 0.5 + math.sin(a) * out + math.cos(a) * side)

        G.surface(m, f, 2, 6, "de_palm", uv_fn=lambda u, v: (u, 1 - v))
    return m


def _gilt_frame(m, x0, x1, y0, y1, z, width=0.16, depth=0.07):
    """A gilt picture-frame moulding round the rectangle (front at +z)."""
    prof = [(0.0, 0.0), (0.0, 0.02), (0.02, 0.04), (0.05, depth * 0.75), (0.09, depth), (0.12, depth * 0.85),
            (0.14, depth * 0.5), (width, depth * 0.35), (width, 0.0)]
    # Offsets measured from the outer edge inward.
    G.sweep(m, [(o, d) for o, d in prof], G.rect_path(x0 - width, y0 - width, x1 + width, y1 + width), (0, 0, z),
            (1, 0, 0), (0, 1, 0), (0, 0, 1), "de_frame_gilt", u_scale=1 / 4.0)


def _reveal(m, x0, x1, y0, y1, depth, region):
    """The lining of a niche from the wall face (z 0) back to the pane plane (z depth)."""
    for x, nx in ((x0, 1.0), (x1, -1.0)):
        m.quad([(x, y0, 0.0), (x, y0, depth), (x, y1, depth), (x, y1, 0.0)], [(0, 1), (1, 1), (1, 0), (0, 0)], region,
               (nx, 0, 0), 0.7)
    m.quad([(x0, y1, 0.0), (x1, y1, 0.0), (x1, y1, depth), (x0, y1, depth)], [(0, 1), (1, 1), (1, 0), (0, 0)], region,
           (0, -1, 0), 0.6)
    m.quad([(x0, y0, depth), (x1, y0, depth), (x1, y0, 0.0), (x0, y0, 0.0)], [(0, 1), (1, 1), (1, 0), (0, 0)], region,
           (0, 1, 0), 0.8)


def chamber_mirror() -> G.Mesh:
    """One of the final chamber's three tall antique mirrors. The pane is the 2-block segment
    (x 0..2) at the wall's centre plane, visible from 0.1 to 3.6; the deep gilt frame sits on the
    wall face toward the viewer (z 0, facing -z) round a dark reveal back to the glass, and the
    carved shell crest stands on top."""
    m = G.Mesh("chamber_mirror", pivot=(1.0, 0.5))
    y0, y1 = 0.1, 3.6
    front = G.Mesh("f")
    _gilt_frame(front, 0.0, 2.0, y0, y1, 0.0, width=0.17, depth=0.09)
    cm = crest_mask(40, 100)
    G.mask_extrude(front, cm, (1.3, 0.3), (0.35, y1 + 0.1, 0.0), (1, 0, 0), (0, 1, 0), (0, 0, 1), 0.06, "de_crest",
                   "de_frame_gilt", edge_rsize=(4.0, 0.25))
    # Frame faces -z: mirror the +z-built frame onto the wall face.
    m.add(front, G.scale(1, 1, -1), (0.0, 0.0, 0.0))
    _reveal(m, 0.0, 2.0, 0.0, y1, PLANE, "de_walnut")
    m.contact_ao(0.0, None, reach=0.3)
    return m


def arrival_mirror() -> G.Mesh:
    """The foyer's tall arrival mirror, the one you step out of (also the checkpoints' and the
    hallway's): a painted carnival frame with dead bulb sockets all round and a gilt bead, on the
    wall face toward the viewer (-z), round a reveal back to the 2 x 3.5 pane."""
    m = G.Mesh("arrival_mirror", pivot=(1.0, 0.5))
    y0, y1 = 0.1, 3.6
    front = G.Mesh("f")
    prof = [(0.0, 0.0), (0.0, 0.05), (0.03, 0.07), (0.17, 0.07), (0.2, 0.05), (0.22, 0.02), (0.22, 0.0)]
    G.sweep(front, prof, G.rect_path(-0.22, y0 - 0.1, 2.22, min(y1 + 0.22, CEIL - 0.02)), (0, 0, 0), (1, 0, 0),
            (0, 1, 0), (0, 0, 1), "de_paint_red")
    _gilt_frame(front, 0.0, 2.0, y0, y1, 0.0, width=0.05, depth=0.05)
    pts = [(-0.11 + t * 2.22, min(y1 + 0.11, CEIL - 0.13)) for t in np.linspace(0, 1, 8)]
    pts += [(-0.11, y0 + t * (y1 - y0)) for t in np.linspace(0.1, 0.9, 8)]
    pts += [(2.11, y0 + t * (y1 - y0)) for t in np.linspace(0.1, 0.9, 8)]
    for (x, y) in pts:
        s = G.Mesh("s")
        G.lathe(s, [(0.0, 0.0), (0.03, 0.0), (0.03, 0.03), (0.02, 0.035), (0.0, 0.035)], 8, "de_brass", centre=(0, 0, 0))
        G.lathe(s, [(0.0, 0.035), (0.018, 0.04), (0.022, 0.06), (0.012, 0.08), (0.0, 0.085)], 8, "de_bulb", centre=(0, 0, 0))
        front.add(s, np.array([[1, 0, 0], [0, 0, -1], [0, 1, 0]], float).T, (x, y, 0.07))
    m.add(front, G.scale(1, 1, -1), (0.0, 0.0, 0.0))
    _reveal(m, 0.0, 2.0, 0.0, y1, PLANE, "de_paint_red")
    return m


def meshes():
    return [bulb(False), bulb(True), sconce(), exit_sign(), facade(), ticket_booth(), turnstile(), stanchion(False),
            stanchion(True), stanchion_rope(), *[poster(i) for i in range(6)], *[note(i) for i in range(3)], bunting(),
            shards(0), shards(1), shards(2), panel_empty_shards(), cobweb(), litter(0), litter(1), litter(2), leaves(0),
            leaves(1), crate(), mirror_stack(), bucket(), banner(), exit_door(), curtain(), palm(), chamber_mirror(),
            arrival_mirror(), chair(), ticket_stub(), bulb_cage(), chandelier()]
