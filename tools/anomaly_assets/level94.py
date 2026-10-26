"""Level 94 "Motion" anomalies and supplies: textures and sounds.

Run only these with:  python tools/gen_assets.py level94_

- the porcelain sink of the furniture piles (and its basin with the 1953 coin glinting in it),
- the water tower's Almond Water tap, the milk van's crate of Almond Milk,
- the blinding hall's light panels and white tiles,
- the Almond Milk and 1953 coin items,
- the hum of the blinding hall's light, the coin rattling in the sink.
"""
from __future__ import annotations

import numpy as np

import gen_assets as G

S = G.S
SR = G.SR


def _blk(name: str):
    return G.TEX / "block" / f"{name}.png"


def _item(name: str):
    return G.TEX / "item" / f"{name}.png"


# =============================================================================
# Textures
# =============================================================================

def _porcelain(rng) -> np.ndarray:
    """Glazed 1950s porcelain: warm off-white, a soft sheen and the faintest crazing."""
    X, Y = G.grid()
    img = G.add_lum(G.fill((236, 232, 220), (S, S)), G.fnoise(rng, beta=3.0) * 2.5 + G.fnoise(rng, beta=1.2, kmin=20) * 0.8)
    sheen = 10 * np.cos((X - 40) / S * 2 * np.pi) * np.cos((Y - 30) / S * 2 * np.pi)
    img = G.add_lum(img, sheen)
    craze = G.smoothstep(0.15, 0.0, np.abs(G.fnoise(rng, beta=2.2, kmin=4)))
    return G.over(img, np.array([200, 196, 184]), craze * 0.25)


def tex_level94_sink() -> None:
    """l94_sink (porcelain), l94_sink_top (the basin seen from above), l94_sink_top_coin (with the coin)."""
    rng = G.rng_for(94701)
    X, Y = G.grid()
    body = _porcelain(rng)
    G.save_png(body, _blk("l94_sink"))

    # The top face of the model is the block's x 2..14, z 4..15: draw the basin inside that part.
    top = body.copy()
    cx, cy = 64.0, 76.0
    d = np.hypot((X - cx) / 40.0, (Y - cy) / 33.0)
    bowl = G.smoothstep(1.02, 0.96, d)
    depth = np.clip(1 - d, 0, 1)
    inside = G.add_lum(G.fill((222, 218, 206), (S, S)), -46 * (1 - depth) ** 0.5 + G.fnoise(rng, beta=3.0) * 2)
    top = G.over(top, inside, bowl)
    rim = G.smoothstep(0.07, 0.0, np.abs(d - 1.0))
    top = G.add_lum(top, -26 * rim)
    # Rust stain running to the plughole, the plughole itself.
    stain = G.smoothstep(14, 0, np.abs(X - cx - 2 * np.sin(Y / 9))) * G.smoothstep(40, 80, Y) * bowl
    top = G.over(top, np.array([176, 128, 82]), stain * 0.35)
    drain_d = np.hypot(X - cx, Y - (cy + 4))
    top = G.over(top, np.array([150, 152, 156]), G.smoothstep(7.5, 6.5, drain_d))
    top = G.over(top, np.array([34, 32, 30]), G.smoothstep(5.0, 4.0, drain_d))
    G.save_png(top, _blk("l94_sink_top"))

    # The coin: a worn brass disc half in the plughole, catching the light.
    coin_d = np.hypot(X - (cx + 9), Y - (cy + 9))
    shade = G.lerp(np.array([226, 188, 92]), np.array([150, 112, 46]), G.smoothstep(-6, 6, (X - (cx + 9)) + (Y - (cy + 9)))[..., None])
    coin = G.over(top, shade, G.smoothstep(7.0, 6.0, coin_d))
    coin = G.add_lum(coin, 40 * G.smoothstep(2.5, 0.0, np.hypot(X - (cx + 6), Y - (cy + 6))))
    G.save_png(coin, _blk("l94_sink_top_coin"))


def tex_level94_tap() -> None:
    """l94_tower_tap: the tower's grey-green painted standpipe; l94_tower_tap_wheel: the red valve wheel."""
    rng = G.rng_for(94702)
    X, Y = G.grid()
    pipe = G.add_lum(G.fill((104, 128, 112), (S, S)), G.fnoise(rng, beta=2.6) * 4 + 12 * np.cos((X - 44) / S * 2 * np.pi))
    rust = G.smoothstep(1.2, 2.4, G.fnoise(rng, beta=2.0, kmin=6))
    pipe = G.over(pipe, np.array([132, 84, 52]), rust * 0.6)
    drip = G.smoothstep(1.4, 0.4, np.abs(G.wrapd(X - 70, S))) * G.smoothstep(0, 128, Y)
    pipe = G.add_lum(pipe, -14 * drip)
    G.save_png(pipe, _blk("l94_tower_tap"))
    wheel = G.add_lum(G.fill((196, 48, 40), (S, S)), G.fnoise(rng, beta=2.4) * 5 + 10 * np.cos((Y - 40) / S * 2 * np.pi))
    chip = G.smoothstep(1.6, 2.6, G.fnoise(rng, beta=1.8, kmin=10))
    G.save_png(G.over(wheel, np.array([90, 92, 96]), chip * 0.7), _blk("l94_tower_tap_wheel"))


def _wire_crate(rng, behind: np.ndarray) -> np.ndarray:
    """Galvanised wire crate: horizontal bands and vertical wires over whatever is inside."""
    X, Y = G.grid()
    wire = G._galvanised(rng, (178, 180, 184))
    bands = (G.smoothstep(3.5, 2.0, np.abs(Y - 6)) + G.smoothstep(3.5, 2.0, np.abs(Y - 122))
             + G.smoothstep(2.2, 1.0, np.abs(G.wrapd(Y - 64, 40))))
    verts = G.smoothstep(1.8, 0.8, np.abs(G.wrapd(X, 16)))
    mask = np.clip(bands + verts, 0, 1)
    return G.over(behind, wire, mask)


def tex_level94_milk_crate() -> None:
    """The milk van's crate: sides with and without bottles, its empty compartments, the bottle necks and caps."""
    rng = G.rng_for(94703)
    X, Y = G.grid()
    # Milk bottles behind the wire: white glass bodies, darker gaps between them.
    col = G.wrapd(X - 21, 42.67)
    bottles = G.add_lum(G.fill((244, 242, 234), (S, S)), -30 * G.smoothstep(14, 20, np.abs(col)) + G.fnoise(rng, beta=2.8) * 2)
    bottles = G.add_lum(bottles, 14 * G.smoothstep(6, 0, np.abs(col + 8)))
    G.save_png(_wire_crate(rng, bottles), _blk("l94_milk_crate"))
    dark = G.add_lum(G.fill((40, 40, 44), (S, S)), G.fnoise(rng, beta=2.5) * 3)
    G.save_png(_wire_crate(rng, dark), _blk("l94_milk_crate_empty"))
    # Seen from above: a 3 x 3 grid of square compartments.
    cell = np.maximum(np.abs(G.wrapd(X - 21.3, 42.67)), np.abs(G.wrapd(Y - 21.3, 42.67)))
    inside = G.over(dark, G._galvanised(rng, (170, 172, 176)), G.smoothstep(18.5, 20.5, cell))
    G.save_png(inside, _blk("l94_milk_crate_inside"))
    # Bottle necks (sides of the element sticking out above the crate) and their foil caps from above.
    necks = G.add_lum(G.fill((236, 236, 230), (S, S)), -60 * G.smoothstep(9, 13, np.abs(G.wrapd(X - 21.3, 42.67))))
    necks = G.add_lum(necks, -18 * G.smoothstep(40, 128, Y))
    G.save_png(necks, _blk("l94_milk_bottles"))
    r = np.hypot(G.wrapd(X - 21.3, 42.67), G.wrapd(Y - 21.3, 42.67))
    caps = G.over(dark, np.array([236, 234, 226]), G.smoothstep(15, 13, r))
    foil = G.lerp(np.array([232, 200, 120]), np.array([168, 132, 64]), G.smoothstep(-8, 8, G.wrapd(X - 21.3, 42.67))[..., None])
    caps = G.over(caps, foil, G.smoothstep(11, 9.5, r))
    G.save_png(caps, _blk("l94_milk_caps"))


def tex_level94_glare() -> None:
    """l94_glare_panel: the blinding hall's ceiling of light; l94_white_tile: its glossy white floor."""
    rng = G.rng_for(94704)
    X, Y = G.grid()
    panel = G.add_lum(G.fill((255, 255, 255), (S, S)), -np.abs(G.fnoise(rng, beta=3.0)) * 1.2)
    seam = G.smoothstep(1.6, 0.4, np.minimum(np.abs(G.wrapd(X, 64)), np.abs(G.wrapd(Y, 64))))
    panel = G.over(panel, np.array([236, 240, 246]), seam * 0.6)
    G.save_png(panel, _blk("l94_glare_panel"))
    tile = G.add_lum(G.fill((240, 241, 242), (S, S)), G.fnoise(rng, beta=2.6) * 1.5)
    tile = G.add_lum(tile, 6 * G.smoothstep(20, 2, np.hypot(G.wrapd(X - 10, 32), G.wrapd(Y - 10, 32))))
    grout = G.smoothstep(1.4, 0.5, np.minimum(np.abs(G.wrapd(X, 32)), np.abs(G.wrapd(Y, 32))))
    G.save_png(G.over(tile, np.array([196, 198, 202]), grout), _blk("l94_white_tile"))


def tex_level94_items() -> None:
    """item/almond_milk (a 1950s milk bottle with a tan foil cap), item/coin_1953 (a worn brass coin)."""
    img = np.zeros((16, 16, 4))
    outline = (110, 112, 116, 255)
    rows = {2: (6, 9), 3: (6, 9), 4: (6, 9), 5: (5, 10), 6: (4, 11)}
    for y in range(7, 15):
        rows[y] = (4, 11)
    rows[15] = (5, 10)
    for y, (xl, xr) in rows.items():
        for x in range(xl, xr + 1):
            img[y, x] = (238, 232, 214, 255) if y >= 5 else (220, 232, 238, 140)
        img[y, xl] = outline
        img[y, xr] = outline
    img[15, 5:11] = outline
    img[0, 6:10] = (196, 150, 84, 255)          # foil cap
    img[1, 5:11] = (172, 124, 62, 255)
    img[1, 6] = (232, 196, 132, 255)
    img[9:12, 5:11] = (204, 64, 54, 255)        # red printed band
    img[10, 6:10] = (246, 238, 226, 255)
    for y in range(6, 14):
        if not 9 <= y <= 11:
            img[y, 5] = (255, 255, 255, 255)
    img[14, 6:10] = (214, 206, 186, 255)
    G.save_png(img, _item("almond_milk"))

    coin = np.zeros((16, 16, 4))
    yy, xx = np.mgrid[0:16, 0:16]
    d = np.hypot(xx - 7.5, yy - 7.5)
    body = d <= 6.2
    rim = (d > 5.0) & body
    light = np.clip(1.0 - ((xx - 4) + (yy - 4)) / 16.0, 0, 1)
    base = np.array([196, 150, 64]) * 0.75 + np.array([236, 204, 120]) * 0.25
    for y in range(16):
        for x in range(16):
            if body[y, x]:
                c = base * (0.75 + 0.45 * light[y, x])
                if rim[y, x]:
                    c = c * 0.8
                coin[y, x] = (*np.clip(c, 0, 255), 255)
    # "53" stamped in the middle, and a worn spot.
    for x, y in ((5, 6), (6, 6), (5, 7), (6, 8), (5, 9), (6, 9), (9, 6), (10, 6), (10, 7), (9, 8), (10, 9), (9, 9)):
        coin[y, x, :3] *= 0.62
    coin[4, 5, :3] = (252, 236, 180)
    coin[5, 4, :3] = (246, 222, 160)
    G.save_png(coin, _item("coin_1953"))


# =============================================================================
# Sounds
# =============================================================================

def snd_level94_blinding_hum() -> None:
    """The blinding hall's light: a thin, glassy, slowly beating high hum over a faint electric bed. A seamless 6 s loop."""
    rng = G.rng_for(94711)
    dur = 6.0
    n = int(dur * SR)
    t = np.arange(n) / SR
    # Every frequency is a whole number of cycles over the loop, so the seam is invisible.
    x = np.zeros(n)
    for f, a in ((2793.0, 0.30), (2794.5, 0.24), (4190.0, 0.10), (5587.0, 0.05), (120.0, 0.08), (240.0, 0.05)):
        x += a * np.sin(2 * np.pi * f * t + rng.uniform(0, 2 * np.pi))
    air = G.periodic_noise(n, rng, lambda f: G.bp_gain(f, 2500, 7000, 2))
    x += 0.035 * air
    x *= G.drift(n, rng, amount=0.12, lo=1 / dur, hi=0.6)
    G.save_ogg(x, G.SND / "level94" / "blinding_hum.ogg", peak=0.5, loop=True)


def snd_level94_coin() -> None:
    """A coin knocked loose in a porcelain sink: a bright clink, a few bounces, a short spin and rattle."""
    rng = G.rng_for(94712)
    dur = 1.2
    n = int(dur * SR)
    x = np.zeros(n)
    at = 0.02
    gap = 0.16
    amp = 1.0
    for _ in range(6):
        f = rng.uniform(4300, 4700)
        clink = G.ring([f, f * 1.47, f * 2.21, 1900], [0.09, 0.05, 0.03, 0.02], [1.0, 0.5, 0.25, 0.3], 0.3)
        clink[:30] += rng.normal(size=30) * 0.5
        G.add_at(x, amp * clink, at, circular=False)
        at += gap
        gap *= 0.62
        amp *= 0.7
    # The settling spin: quick, fading little ticks.
    spin_t = at + np.cumsum(np.geomspace(0.03, 0.008, 18))
    x += 0.25 * G.clicks(rng, spin_t[spin_t < dur - 0.1], dur, 3800, 5200)
    x = x + 0.2 * G.conv(x, G.reverb_ir(rng, 0.4, 0.07, lp=7000))[:n]
    G.save_ogg(x, G.SND / "level94" / "coin.ogg", peak=0.7, fade_out=0.1)


TEXTURES = [tex_level94_sink, tex_level94_tap, tex_level94_milk_crate, tex_level94_glare, tex_level94_items]
SOUNDS = [snd_level94_blinding_hum, snd_level94_coin]
