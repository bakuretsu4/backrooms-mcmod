"""Level 1's halls, corridors and storage lockers: textures and a sound.

Signs on the pillars pointing nowhere (EXIT / SALIDA / SORTIE / AUSGANG, arrow either way),
rusty rebar, roll-up locker shutters (down and rolled up), a stained mattress, the locked white door
at the end of the lockers, the hazard-striped pillar band of the warehouse sector and cracked
concrete for the decrepit one. Sound: something growling behind a locker shutter.

Run only these with:  python tools/gen_assets.py l1_
"""
from __future__ import annotations

import numpy as np
from PIL import Image, ImageDraw, ImageFont

import gen_assets as G

S = G.S
FONT = G.Path(G.__file__).resolve().parent / "fonts" / "Anton-Regular.ttf"
SIGN_WORDS = ("EXIT", "SALIDA", "SORTIE", "AUSGANG")


def _blk(name: str):
    return G.TEX / "block" / f"{name}.png"


def _concrete() -> np.ndarray:
    """The plain concrete wall texture the signs and the door sit on (so they match the pillars)."""
    return np.asarray(Image.open(_blk("concrete_wall")).convert("RGB"), float)


def tex_l1_signs() -> None:
    """Eight signs: a worn green plate with white letters and an arrow, screwed to the concrete."""
    base = _concrete()
    for word_i, word in enumerate(SIGN_WORDS):
        for arrow in (0, 1):                                    # 0 = arrow right, 1 = left
            rng = G.rng_for(7500 + word_i * 2 + arrow)
            im = Image.new("RGBA", (S * 4, S * 4), (0, 0, 0, 0))
            d = ImageDraw.Draw(im)
            k = 4
            d.rounded_rectangle((8 * k, 34 * k, 120 * k, 94 * k), radius=6 * k, fill=(22, 112, 64, 255))
            d.rounded_rectangle((12 * k, 38 * k, 116 * k, 90 * k), radius=4 * k, outline=(220, 236, 226, 255), width=2 * k)
            size = 30 * k
            font = ImageFont.truetype(str(FONT), size)
            while d.textlength(word, font=font) > 66 * k and size > 10:
                size -= 2 * k
                font = ImageFont.truetype(str(FONT), size)
            tw = d.textlength(word, font=font)
            text_x = (20 if arrow else 44) * k if tw < 60 * k else (36 if arrow else 16) * k
            if arrow == 0:
                text_x = 16 * k
                ax0, ax1 = 88 * k, 110 * k
            else:
                text_x = 112 * k - tw
                ax0, ax1 = 40 * k, 18 * k
            d.text((text_x, 64 * k), word, font=font, fill=(240, 246, 242, 255), anchor="lm")
            y = 64 * k
            tip = ax1
            back = ax0
            d.polygon([(tip, y), (tip + (back - tip) * 0.55, y - 11 * k), (tip + (back - tip) * 0.55, y + 11 * k)],
                      fill=(240, 246, 242, 255))
            d.rectangle((min(back, tip + (back - tip) * 0.5), y - 4 * k, max(back, tip + (back - tip) * 0.5), y + 4 * k),
                        fill=(240, 246, 242, 255))
            for sx in (14, 114):                                # screws
                d.ellipse(((sx - 2) * k, 40 * k, (sx + 2) * k, 44 * k), fill=(150, 150, 140, 255))
            plate = np.asarray(im.resize((S, S), Image.LANCZOS), float)
            alpha = plate[..., 3:] / 255.0
            img = base * (1 - alpha) + plate[..., :3] * alpha
            # Worn: grime and scratches over the plate only.
            grime = G.smoothstep(0.3, 1.8, G.fnoise(rng, beta=2.2)) * alpha[..., 0]
            img = G.over(img, np.array([70, 74, 62.0]), grime * 0.35)
            img = G.add_lum(img, -14 * (rng.random((S, S)) < 0.01) * alpha[..., 0])
            G.save_png(img, _blk(f"l1_sign_{word_i * 2 + arrow}"))


def tex_l1_rebar() -> None:
    """Three or four rusty bars bent out of the floor, on transparent (a cross model)."""
    rng = G.rng_for(7510)
    def draw(d, k):
        for x0, lean, bend in ((34, 8, -20), (58, -6, 14), (80, 10, 22), (100, -4, -10)):
            pts = [(x0 * k, 128 * k), ((x0 + lean) * k, 70 * k), ((x0 + lean + bend) * k, 30 * k)]
            d.line(pts, fill=255, width=int(5 * k), joint="curve")
    m = G.pil_mask(draw)
    rust = G.add_lum(G.fill((112, 64, 38), (S, S)), G.fnoise(rng, beta=1.4, kmin=8) * 14, (1.0, 0.75, 0.5))
    ribs = np.sin(G.grid()[1] * 2 * np.pi / 5) * 10
    rust = G.add_lum(rust, ribs)
    img = np.dstack([rust, m * 255])
    G.save_png(img, _blk("l1_rebar"))


def _ribbed(rng, col, period: float, depth: float) -> np.ndarray:
    X, Y = G.grid()
    img = G.add_lum(G.fill(col, (S, S)), G.fnoise(rng, beta=2.4) * 6)
    rib = np.sin(Y * 2 * np.pi / period)
    return G.add_lum(img, rib * depth - depth * 0.6 * G.smoothstep(0.7, 1.0, -rib))


def tex_l1_shutter() -> None:
    """A roll-up locker shutter: corrugated grey-blue steel, dented, rust running down."""
    rng = G.rng_for(7511)
    img = _ribbed(rng, (104, 112, 118), 8.0, 16.0)
    rust = G.smoothstep(0.8, 2.0, G.fnoise(rng, beta=2.0, aniso=(1, 6)))
    img = G.over(img, np.array([118, 74, 46.0]), rust * 0.5)
    X, Y = G.grid()
    img = G.add_lum(img, -26 * G.box((S, S), 0, 118, S, 128))           # the bottom bar
    img = G.add_lum(img, -40 * G.box((S, S), 54, 106, 74, 112))          # the handle slot
    G.save_png(img, _blk("l1_shutter"))


def tex_l1_shutter_rolled() -> None:
    rng = G.rng_for(7512)
    img = _ribbed(rng, (92, 98, 104), 4.0, 14.0)
    X, Y = G.grid()
    img = G.add_lum(img, -30 * (1 - np.sin(np.clip(Y / S, 0, 1) * np.pi)))  # a round drum
    G.save_png(img, _blk("l1_shutter_rolled"))


def tex_l1_mattress() -> None:
    """A thin, stained mattress: quilted grey-white with brown stains (top), striped edge (side)."""
    rng = G.rng_for(7513)
    X, Y = G.grid()
    img = G.add_lum(G.fill((196, 192, 182), (S, S)), G.fnoise(rng, beta=2.6) * 6)
    quilt = np.minimum(np.abs(G.wrapd(X, 16)), np.abs(G.wrapd(Y, 16)))
    img = G.add_lum(img, -22 * G.smoothstep(1.4, 0.4, quilt) + 6 * G.smoothstep(3, 7, quilt))
    stain = G.smoothstep(0.9, 1.9, G.fnoise(rng, beta=2.6))
    img = G.over(img, np.array([150, 122, 84.0]), stain * 0.55)
    G.save_png(img, _blk("l1_mattress"))
    side = G.add_lum(G.fill((176, 172, 164), (S, S)), np.sin(X * 2 * np.pi / 8) * 8 + G.fnoise(rng, beta=2.2) * 5)
    side = G.over(side, np.array([150, 122, 84.0]), G.smoothstep(1.0, 2.0, G.fnoise(rng, beta=2.4)) * 0.5)
    G.save_png(side, _blk("l1_mattress_side"))


def tex_l1_locked_door() -> None:
    """The white door at the end of the storage lockers, on the concrete wall (bottom and top)."""
    rng = G.rng_for(7514)
    base = _concrete()
    shp = (2 * S, S)
    X, Y = G.grid(shp)
    wall = np.concatenate([base, base], axis=0)
    leaf = G.box(shp, 14, 12, S - 14, 2 * S + 8)
    door = G.add_lum(G.fill((226, 226, 220), shp), G.fnoise(rng, shp, beta=2.6) * 4)
    door = G.add_lum(door, -18 * (G.box(shp, 10, 8, S - 10, 2 * S + 8) - leaf))   # the frame's shadow
    img = wall * (1 - G.box(shp, 10, 8, S - 10, 2 * S + 8)[..., None]) + door * G.box(shp, 10, 8, S - 10, 2 * S + 8)[..., None]
    knob = G.supersample(shp, lambda x, y: np.hypot(x - 98, y - 142) < 5)
    img = G.over(img, np.array([150, 150, 140.0]), knob)
    img = G.over(img, np.array([150, 150, 146.0]), G.box(shp, 90, 136, 106, 156) * 0.5)
    grime = G.smoothstep(0.8, 2.0, G.fnoise(rng, shp, beta=2.2)) * leaf
    img = G.over(img, np.array([150, 146, 132.0]), grime * 0.4)
    G.save_png(img[:S], _blk("l1_locked_door_top"))
    G.save_png(img[S:], _blk("l1_locked_door"))


def tex_l1_pillar_painted() -> None:
    """A pillar's base band painted with yellow and black hazard stripes, chipped."""
    rng = G.rng_for(7515)
    base = _concrete()
    X, Y = G.grid()
    stripe = (np.floor((X + Y) / 22) % 2) == 0
    paint = np.where(stripe[..., None], np.array([214, 172, 36.0]), np.array([30, 28, 26.0]))
    band = G.box((S, S), -1, 16, S + 1, S - 16)
    chipped = G.smoothstep(1.2, 1.8, G.fnoise(rng, beta=1.6, kmin=4))
    a = band * (1 - chipped * 0.9)
    img = base * (1 - a[..., None]) + paint * a[..., None]
    G.save_png(G.add_lum(img, G.fnoise(rng, beta=2.0) * 4), _blk("l1_pillar_painted"))


def tex_l1_cracked_concrete() -> None:
    """Concrete split by dark cracks, with damp seeping from them."""
    rng = G.rng_for(7516)
    img = _concrete()
    crack = np.abs(G.fnoise(rng, beta=2.2))
    lines = G.smoothstep(0.13, 0.02, crack) * G.smoothstep(-0.4, 0.4, G.fnoise(rng, beta=2.0))
    img = G.add_lum(img, -95 * np.clip(lines, 0, 1))
    damp = G.blur(np.clip(lines, 0, 1), 3.0)
    img = G.add_lum(img, -26 * damp, (0.95, 1.0, 1.0))
    G.save_png(img, _blk("l1_cracked_concrete"))


def snd_l1_locker_growl() -> None:
    """Something low in a locker: a throaty growl with a chatter of teeth, close behind steel."""
    for take in (1, 2, 3):
        rng = G.rng_for(7520 + take)
        dur = 1.8 + take * 0.4
        n = int(dur * G.SR)
        t = np.arange(n) / G.SR
        throat = G.fft_filter(rng.normal(size=n), lambda f: G.bp_gain(f, 60, 380, 3))
        flutter = 0.55 + 0.45 * np.sin(2 * np.pi * (24 + 6 * take) * t + rng.uniform(0, 6))
        growl = G.norm(throat) * flutter * G.env_adsr(n, 0.3, 0.6)
        chatter = np.zeros(n)
        start = rng.uniform(0.3, 0.8)
        for k in range(int(rng.integers(6, 12))):
            click = G.ring([rng.uniform(2200, 3600), rng.uniform(4000, 5200)], [0.004, 0.003], [1, 0.5], 0.02)
            G.add_at(chatter, click * rng.uniform(0.4, 1.0), start + k * rng.uniform(0.035, 0.06), circular=False)
        x = growl + 0.35 * G.norm(chatter)
        x = G.fft_filter(x, lambda f: G.lp_gain(f, 2500, 2))          # muffled by the shutter
        G.save_ogg(x, G.SND / "level1" / f"locker_growl{take}.ogg", peak=0.7)


TEXTURES = [tex_l1_signs, tex_l1_rebar, tex_l1_shutter, tex_l1_shutter_rolled, tex_l1_mattress, tex_l1_locked_door,
            tex_l1_pillar_painted, tex_l1_cracked_concrete]
SOUNDS = [snd_l1_locker_growl]
