"""Level 490 ("Penance") blocks: textures, block models, blockstates and item models, and the moon.

Penance is stone platforms and bridges hung from giant iron chains over a sea of storm cloud at
night (docs/level-490-penance.md). The look comes from the two reference stills: almost no
colour, pale worn stone, black wrought iron, cold moonlight. Every texture has its own fixed
random seed (49000-49999), so re-running gives identical files.

Run with `python tools/gen_assets.py l490`.
"""
from __future__ import annotations

import json

import numpy as np

import gen_assets as G

S = G.S                 # 128 px block texture size
ASSETS = G.ROOT         # src/main/resources/assets/backrooms

PAVING = (150, 153, 158)
STONE = (118, 121, 128)
SLATE = (56, 60, 68)
IRON = (40, 40, 44)
RUST = (78, 56, 44)


def _blk(name: str):
    return G.TEX / "block" / f"{name}.png"


def _save(img, name):
    G.save_png(np.clip(img, 0, 255), _blk(name))


def _relief(h, k):
    """Brightness change from a tileable height field lit from the upper left."""
    gx = (np.roll(h, -1, 1) - np.roll(h, 1, 1)) * 0.5
    gy = (np.roll(h, -1, 0) - np.roll(h, 1, 0)) * 0.5
    return (gx * 0.6 + gy * 0.8) * k


def _joints(rows, cols, offset_rows=False, width=2.2):
    """Darkness (0..1) of the mortar joints of a grid of `rows` x `cols` stones per block, every
    other row shifted half a stone if offset_rows. Also returns each pixel's stone index."""
    X, Y = G.grid()
    h = S / rows
    w = S / cols
    row = np.floor(Y / h).astype(int)
    xs = X + (w / 2 if offset_rows else 0) * (row % 2)
    col = np.floor(xs / w).astype(int) % cols
    dy = np.minimum(Y % h, h - Y % h)
    dx = np.minimum(xs % w, w - xs % w)
    joint = np.clip(1.0 - np.minimum(dx, dy) / width, 0, 1)
    return joint, row * cols + col


def _stones(rng, base, rows, cols, offset_rows, tint, wear):
    """A wall or floor of stones: each stone its own tone, worn surface, dark joints."""
    joint, idx = _joints(rows, cols, offset_rows)
    tones = rng.normal(0, tint, rows * cols + cols)
    img = G.fill(base, (S, S)).astype(float)
    img = G.add_lum(img, tones[idx])
    h = G.blur(G.fnoise(rng, beta=1.9, kmin=3), 0.8)
    img = G.add_lum(img, _relief(h, wear) + G.fnoise(rng, beta=1.2, kmin=8) * 4 + rng.normal(0, 2.5, (S, S)))
    img = G.add_lum(img, -joint * 42)
    return img, joint


def _paving_img():
    """Pale, worn flagstones (two by two per block): smoothed by feet, cracked, a few pits.
    Returns (image, joint darkness); the rubble is this same paving, broken."""
    rng = G.rng_for(49001)
    img, joint = _stones(rng, PAVING, 2, 2, False, 6.0, 6.0)
    # Hairline cracks wandering across some slabs.
    cracks = G.smoothstep(0.92, 0.99, np.abs(G.fnoise(rng, beta=1.7, kmin=4, aniso=(1.0, 0.4))) / 3.0)
    img = G.add_lum(img, -cracks * 28 * (1 - joint))
    # Pitting and the darker grime gathered in the hollows.
    pits = (rng.random((S, S)) < 0.0015).astype(float)
    img = G.add_lum(img, -G.blur(pits, 0.9) * 70)
    grime = G.smoothstep(0.8, 2.0, G.fnoise(rng, beta=2.2, kmin=2))
    img = G.over(img, np.array([118, 120, 126]), grime * 0.35)
    return img, joint


def tex_l490_paving() -> None:
    """The flagstones of the islands and the bridges."""
    _save(_paving_img()[0], "l490_paving")


def _gothic_img():
    """Dark weathered Gothic ashlar: three staggered courses a block, each stone its own tone, rain
    streaks down it, grime in the joints, a faint cold tint."""
    rng = G.rng_for(49060)
    img, joint = _stones(rng, (66, 65, 72), 3, 2, True, 5.0, 6.0)
    streaks = G.blur(G.fnoise(rng, beta=1.4, kmin=6, aniso=(1.0, 0.08)), 0.6)
    img = G.add_lum(img, -G.smoothstep(0.4, 2.0, streaks) * 14)
    chips = G.smoothstep(1.7, 2.6, G.fnoise(rng, beta=1.3, kmin=8))
    img = G.add_lum(img, chips * 12)
    return img


def tex_l490_gothic() -> None:
    """The spire's dark Gothic masonry."""
    _save(_gothic_img(), "l490_gothic")


def tex_l490_gothic_carved() -> None:
    """Darker carved stone: vertical fluting between a moulding at top and bottom (cornices,
    tracery, pinnacles, the turrets' roofs)."""
    rng = G.rng_for(49061)
    X, Y = G.grid()
    img = G.fill((54, 53, 60), (S, S)).astype(float)
    flutes = np.cos(X / S * np.pi * 2 * 4)
    img = G.add_lum(img, flutes * 9)
    band = (Y < S * 0.12) | (Y > S * 0.88)
    img = np.where(band[..., None], G.add_lum(img, 8 - flutes * 18), img)
    edge = G.smoothstep(2.5, 0.0, np.minimum(np.abs(Y - S * 0.12), np.abs(Y - S * 0.88)))
    img = G.add_lum(img, -edge * 22 + G.fnoise(rng, beta=1.8, kmin=3) * 4 + rng.normal(0, 2.5, (S, S)))
    streaks = G.blur(G.fnoise(rng, beta=1.4, kmin=6, aniso=(1.0, 0.08)), 0.6)
    img = G.add_lum(img, -G.smoothstep(0.5, 2.0, streaks) * 12)
    _save(img, "l490_gothic_carved")


def _window(rng, glass, glow: bool):
    """Leaded glass in a dark stone frame: small panes, each its own tone; lit ones glow warm."""
    X, Y = G.grid()
    img = G.fill((40, 39, 45), (S, S)).astype(float)
    frame = 14
    inside = (X >= frame) & (X < S - frame) & (Y >= frame) & (Y < S - frame)
    pw, ph = (S - 2 * frame) / 3, (S - 2 * frame) / 4
    col = np.floor((X - frame) / pw).astype(int)
    row = np.floor((Y - frame) / ph).astype(int)
    tone = rng.normal(0, 0.12, 64)[np.clip(row * 3 + col, 0, 63)]
    lead = (np.abs(((X - frame) % pw) - pw / 2) > pw / 2 - 2.5) | (np.abs(((Y - frame) % ph) - ph / 2) > ph / 2 - 2.5)
    pane = np.array(glass, float)[None, None, :] * (1 + tone[..., None])
    if glow:
        # Brighter toward the middle: the candle behind it.
        c = 1.0 - 0.45 * np.hypot((X - S / 2) / (S / 2), (Y - S / 2) / (S / 2))
        pane = pane * c[..., None]
    img = np.where((inside & ~lead)[..., None], pane, img)
    img = np.where((inside & lead)[..., None], np.array([22, 21, 24], float), img)
    img = G.add_lum(img, rng.normal(0, 2.0, (S, S)))
    return img


def tex_l490_window_lit() -> None:
    _save(_window(G.rng_for(49062), (255, 170, 92), True), "l490_window_lit")


def tex_l490_window_dark() -> None:
    _save(_window(G.rng_for(49063), (34, 38, 50), False), "l490_window_dark")


def tex_l490_statue() -> None:
    """A statue's pale worn stone, grime run down into its folds."""
    rng = G.rng_for(49064)
    img = G.fill((150, 146, 138), (S, S)).astype(float)
    img = G.add_lum(img, G.fnoise(rng, beta=2.0, kmin=2) * 6 + rng.normal(0, 2.5, (S, S)))
    streaks = G.blur(G.fnoise(rng, beta=1.4, kmin=6, aniso=(1.0, 0.08)), 0.6)
    img = G.add_lum(img, -G.smoothstep(0.2, 1.8, streaks) * 26)
    _save(img, "l490_statue")


def _stone_img():
    """Big dressed stone blocks (two courses per block, joints staggered), streaked by the rain."""
    rng = G.rng_for(49002)
    img, _ = _stones(rng, STONE, 2, 1, True, 5.0, 5.0)
    streaks = G.blur(G.fnoise(rng, beta=1.4, kmin=6, aniso=(1.0, 0.08)), 0.6)
    return G.add_lum(img, -G.smoothstep(0.5, 2.0, streaks) * 16)


def tex_l490_stone() -> None:
    """Big dressed stone blocks (two courses per block, joints staggered), streaked by the rain."""
    _save(_stone_img(), "l490_stone")


def tex_l490_slate() -> None:
    """Dark roof slates in overlapping rows: each row's lower edge casts a thin shadow."""
    rng = G.rng_for(49003)
    joint, idx = _joints(4, 3, True, 1.6)
    img = G.fill(SLATE, (S, S)).astype(float)
    img = G.add_lum(img, rng.normal(0, 4, 4 * 3 + 3)[idx] + G.fnoise(rng, beta=1.6, kmin=4) * 3)
    X, Y = G.grid()
    edge = G.smoothstep(0.0, 6.0, (Y % (S / 4)))
    img = G.add_lum(img, (edge - 1.0) * 22 - joint * 20)
    _save(img, "l490_slate")


def tex_l490_iron() -> None:
    """Black wrought iron, pitted, with a little rust where the rain sits."""
    rng = G.rng_for(49004)
    img = G.add_lum(G.fill(IRON, (S, S)).astype(float), G.fnoise(rng, beta=2.0) * 3 + rng.normal(0, 2.0, (S, S)))
    rust = G.smoothstep(1.4, 2.4, G.fnoise(rng, beta=1.6, kmin=4))
    img = G.over(img, np.array(RUST), rust * 0.5)
    sheen = G.smoothstep(1.0, 2.5, G.fnoise(rng, beta=2.4, kmin=2))
    img = G.add_lum(img, sheen * 10)
    _save(img, "l490_iron")


def tex_l490_lantern_glass() -> None:
    """The lantern's glass: dark smoky panes when it has gone out; a warm flame glow when it burns."""
    rng = G.rng_for(49005)
    X, Y = G.grid()
    grime = G.smoothstep(0.3, 2.0, G.fnoise(rng, beta=1.8, kmin=3))
    dark = G.add_lum(G.fill((44, 48, 56), (S, S)).astype(float), grime * 10 + G.fnoise(rng, beta=1.2, kmin=6) * 3)
    _save(dark, "l490_lantern_glass")
    r = np.hypot((X - S / 2) / (S * 0.42), (Y - S * 0.62) / (S * 0.55))
    glow = np.clip(1.0 - r, 0, 1) ** 1.4
    lit = np.stack([200 + 55 * glow, 128 + 110 * glow, 60 + 110 * glow], axis=-1)
    lit = G.add_lum(lit, -grime * 10)
    _save(lit, "l490_lantern_glass_lit")


def tex_l490_rock() -> None:
    """Dark weathered rock of the floating islands: layered, cracked, crusted pale where it dried."""
    rng = G.rng_for(49007)
    img = G.fill((70, 72, 78), (S, S)).astype(float)
    strata = G.blur(G.fnoise(rng, beta=1.6, kmin=3, aniso=(1.0, 0.25)), 0.8)
    h = G.blur(G.fnoise(rng, beta=2.0, kmin=3), 0.7)
    img = G.add_lum(img, strata * 7 + _relief(h, 9) + rng.normal(0, 3, (S, S)))
    cracks = G.smoothstep(0.93, 0.995, np.abs(G.fnoise(rng, beta=1.5, kmin=4)) / 3.0)
    img = G.add_lum(img, -cracks * 30)
    crust = G.smoothstep(1.2, 2.2, G.fnoise(rng, beta=1.8, kmin=3))
    img = G.over(img, np.array([118, 118, 116]), crust * 0.35)
    _save(img, "l490_rock")


# The broken flagstones' variants: each rubble block picks one at random (its blockstate).
RUBBLE_VARIANTS = ("blow", "blow", "blow", "blow", "two", "line", "corner")


def _crack_star(dl, rng, cx, cy, n, steps, step, ends):
    """Cracks branching out from a blow at (cx, cy) (on the 4x canvas)."""
    for k in range(n):
        a = (k + rng.uniform(-0.35, 0.35)) / n * 2 * np.pi
        x, y = cx, cy
        pts = [(x, y)]
        for _ in range(int(rng.uniform(*steps))):
            a += rng.normal(0, 0.22)
            x += np.cos(a) * step
            y += np.sin(a) * step
            pts.append((x, y))
            if rng.random() < 0.15:
                ba = a + rng.choice([-1, 1]) * rng.uniform(0.5, 1.0)
                bx, by = x, y
                bpts = [(bx, by)]
                for _ in range(rng.integers(1, 4)):
                    ba += rng.normal(0, 0.3)
                    bx += np.cos(ba) * step
                    by += np.sin(ba) * step
                    bpts.append((bx, by))
                dl.line(bpts, fill=120, width=4)
        dl.line(pts, fill=230, width=4)
        ends.append(pts[min(len(pts) - 1, 4)])


def _rubble_img(kind, seed):
    """One broken flagstone: the very same paving (so it sits among it without a seam, its joints
    lining up), but struck. kind: "blow" (cracks out from one blow, in one of the four flagstones),
    "two" (two smaller blows in two flagstones), "line" (one long crack wandering across the block),
    "corner" (a flagstone's corner broken off and sunk). The cracks mostly stop at the joints, as a
    cracked tile's do; a light lip along each; the pieces tilted a little; a few chips."""
    from PIL import Image, ImageDraw
    img, joint = _paving_img()
    rng = G.rng_for(seed)
    X, Y = G.grid()
    K = 4  # drawn at 4x, then shrunk (smooth thin lines)
    lines = Image.new("L", (S * K, S * K), 0)
    pieces = Image.new("L", (S * K, S * K), 128)
    dl = ImageDraw.Draw(lines)
    dp = ImageDraw.Draw(pieces)
    step = S * K * 0.04
    # The four flagstones' middles; pick which get hit.
    quads = [(0.25, 0.25), (0.75, 0.25), (0.25, 0.75), (0.75, 0.75)]
    order = rng.permutation(4)
    blows = []
    across = True  # whether cracks may run on across the joints
    if kind in ("blow", "two"):
        across = False
        for q in order[: 1 if kind == "blow" else 2]:
            qx, qy = quads[q]
            cx = (qx + rng.uniform(-0.1, 0.1)) * S * K
            cy = (qy + rng.uniform(-0.1, 0.1)) * S * K
            ends = []
            if kind == "blow":
                _crack_star(dl, rng, cx, cy, int(rng.integers(4, 8)), (5, 14), step, ends)
            else:
                _crack_star(dl, rng, cx, cy, int(rng.integers(3, 6)), (3, 8), step, ends)
            for k in range(len(ends)):
                dp.polygon([(cx, cy), ends[k], ends[(k + 1) % len(ends)]], fill=int(rng.uniform(60, 200)))
            blows.append((cx / K, cy / K))
    elif kind == "line":
        # From one side of the block to another, wandering, a few short branches off it.
        side = rng.integers(4)
        t0 = rng.uniform(0.2, 0.8) * S * K
        x, y = [(0, t0), (S * K, t0), (t0, 0), (t0, S * K)][side]
        a = [0.0, np.pi, np.pi / 2, -np.pi / 2][side] + rng.normal(0, 0.3)
        pts = [(x, y)]
        while 0 <= x <= S * K and 0 <= y <= S * K and len(pts) < 60:
            a += rng.normal(0, 0.2)
            x += np.cos(a) * step
            y += np.sin(a) * step
            pts.append((x, y))
            if rng.random() < 0.18:
                ends = []
                _crack_star(dl, rng, x, y, 1, (2, 5), step, ends)
        dl.line(pts, fill=235, width=5)
        # One side of the crack has sunk a little.
        poly = pts + [(S * K * 2 if side != 1 else -S * K, pts[-1][1]), (S * K * 2 if side != 1 else -S * K, pts[0][1])]
        dp.polygon(poly, fill=96)
    else:  # corner
        across = False
        q = order[0]
        qx, qy = quads[q]
        # The flagstone's outer corner (away from the block's middle).
        ox = (qx - 0.25) * 2 * S * K / 2 + (0 if qx < 0.5 else S * K / 2) - ((qx - 0.5) > 0) * 0
        x0 = 0 if qx < 0.5 else S * K / 2
        y0 = 0 if qy < 0.5 else S * K / 2
        x1 = x0 + S * K / 2
        y1 = y0 + S * K / 2
        cxn = x0 if qx < 0.5 else x1
        cyn = y0 if qy < 0.5 else y1
        sx = 1 if qx < 0.5 else -1
        sy = 1 if qy < 0.5 else -1
        la = rng.uniform(0.35, 0.6) * S * K / 2
        lb = rng.uniform(0.35, 0.6) * S * K / 2
        p0 = (cxn + sx * la, cyn)
        p1 = (cxn, cyn + sy * lb)
        mid = ((p0[0] + p1[0]) / 2 + sx * rng.uniform(2, 8), (p0[1] + p1[1]) / 2 + sy * rng.uniform(2, 8))
        dl.line([p0, mid, p1], fill=255, width=5)
        dp.polygon([(cxn, cyn), p0, mid, p1], fill=70)
        ends = []
        _crack_star(dl, rng, mid[0], mid[1], 2, (2, 5), step, ends)
        blows.append((mid[0] / K, mid[1] / K))
    lines = np.asarray(lines.resize((S, S), Image.BILINEAR), float) / 255.0
    pieces = np.asarray(pieces.resize((S, S), Image.BILINEAR), float) / 255.0
    if not across:
        lines *= 1 - 0.85 * np.clip(joint * 1.5, 0, 1)
    img = G.add_lum(img, (pieces - 0.5) * 14)
    img = G.add_lum(img, -lines * 48)
    lip = np.clip(np.roll(np.roll(lines, -1, 0), -1, 1) - lines, 0, 1)
    img = G.add_lum(img, lip * 16)
    # A few chips knocked out near each blow.
    for bx, by in blows:
        r = np.hypot(X - bx, Y - by)
        chips = (rng.random((S, S)) < 0.02) * G.smoothstep(S * 0.16, S * 0.03, r)
        img = G.add_lum(img, -G.blur(chips.astype(float), 0.7) * 40)
    return img


def _rubble_name(i):
    return "l490_rubble" if i == 0 else f"l490_rubble_{i}"


def tex_l490_rubble() -> None:
    """The broken flagstones (after the play-test: the old rubble "is ugly", then "not all the
    same pattern"): one texture per variant, l490_rubble, l490_rubble_1, ..."""
    for i, kind in enumerate(RUBBLE_VARIANTS):
        _save(_rubble_img(kind, 49008 + i * 17), _rubble_name(i))


def tex_l490_dead_grass() -> None:
    """Dead grass flattened by the wind (top), and its side: thin soil over rock."""
    rng = G.rng_for(49009)
    blades = G.blur(G.fnoise(rng, beta=1.2, kmin=10, aniso=(1.0, 0.15)), 0.4)
    top = G.fill((112, 100, 78), (S, S)).astype(float)
    top = G.add_lum(top, blades * 10 + G.fnoise(rng, beta=2.0, kmin=2) * 6, (1.0, 0.95, 0.85))
    soil = G.smoothstep(1.0, 2.0, G.fnoise(rng, beta=1.6, kmin=4))
    top = G.over(top, np.array([70, 62, 54]), soil * 0.5)
    _save(top, "l490_dead_grass")
    side = G.fill((70, 72, 78), (S, S)).astype(float)
    side = G.add_lum(side, G.fnoise(rng, beta=1.8, kmin=3) * 6 + rng.normal(0, 3, (S, S)))
    X, Y = G.grid()
    fringe = Y < (22 + 8 * G.fnoise(rng, (1, S), beta=1.2, kmin=4)[0])[None, :]
    side = np.where(fringe[..., None], G.add_lum(G.fill((104, 92, 72), (S, S)).astype(float), blades * 8), side)
    _save(side, "l490_dead_grass_side")


def tex_l490_chain_iron() -> None:
    """The colossal chains' iron: black, heavily pitted, rust streaked down by the rain."""
    rng = G.rng_for(49010)
    img = G.add_lum(G.fill((60, 58, 62), (S, S)).astype(float), G.fnoise(rng, beta=2.2) * 5 + rng.normal(0, 3, (S, S)))
    pits = G.blur((rng.random((S, S)) < 0.01).astype(float), 1.0)
    img = G.add_lum(img, -pits * 40)
    streaks = G.smoothstep(0.6, 2.2, G.blur(G.fnoise(rng, beta=1.3, kmin=5, aniso=(1.0, 0.1)), 0.6))
    img = G.over(img, np.array([86, 54, 36]), streaks * 0.55)
    flakes = G.smoothstep(1.6, 2.6, G.fnoise(rng, beta=1.5, kmin=6))
    img = G.over(img, np.array([104, 70, 48]), flakes * 0.6)
    _save(img, "l490_chain_iron")


def tex_l490_dead_wood() -> None:
    """Grey, barkless, weather-split wood."""
    rng = G.rng_for(49011)
    grain = G.blur(G.fnoise(rng, beta=1.3, kmin=8, aniso=(0.12, 1.0)), 0.5)
    img = G.add_lum(G.fill((112, 108, 102), (S, S)).astype(float), grain * 12 + rng.normal(0, 3, (S, S)))
    splits = G.smoothstep(0.9, 0.99, np.abs(G.fnoise(rng, beta=1.2, kmin=6, aniso=(0.1, 1.0))) / 3.0)
    img = G.add_lum(img, -splits * 45)
    _save(img, "l490_dead_wood")


def tex_l490_red_leaves() -> None:
    """Sparse dark red leaves (RGBA, most of the block see-through)."""
    rng = G.rng_for(49012)
    X, Y = G.grid()
    alpha = np.zeros((S, S))
    col = np.zeros((S, S, 3))
    for _ in range(60):
        cx, cy = rng.uniform(0, S, 2)
        ang = rng.uniform(0, np.pi)
        dx = (X - cx + S / 2) % S - S / 2
        dy = (Y - cy + S / 2) % S - S / 2
        u = dx * np.cos(ang) + dy * np.sin(ang)
        v = -dx * np.sin(ang) + dy * np.cos(ang)
        m = (u / 7.0) ** 2 + (v / 3.6) ** 2 < 1.0
        shade = rng.uniform(0.6, 1.05)
        col[m] = np.array([128, 30, 24]) * shade
        alpha[m] = 255
    img = np.concatenate([col, alpha[..., None]], axis=-1)
    G.save_png(img, _blk("l490_red_leaves"))


def tex_l490_spire() -> None:
    """The spire's stone: pale and dense like old bone, grown rather than built. No joints; long
    fibres running up it, soft swellings, small pores, grime gathered in the hollows."""
    rng = G.rng_for(49012)
    img = G.fill((156, 152, 144), (S, S)).astype(float)
    fibres = G.blur(G.fnoise(rng, beta=1.3, kmin=8, aniso=(0.12, 1.0)), 0.5)
    swell = G.blur(G.fnoise(rng, beta=2.2, kmin=2), 1.0)
    img = G.add_lum(img, fibres * 7 + _relief(swell, 10) + rng.normal(0, 2.0, (S, S)))
    pores = G.blur((rng.random((S, S)) < 0.004).astype(float), 0.7)
    img = G.add_lum(img, -pores * 45)
    grime = G.smoothstep(0.9, 2.2, G.fnoise(rng, beta=2.0, kmin=2))
    img = G.over(img, np.array([110, 106, 100]), grime * 0.4)
    _save(img, "l490_spire")


def tex_l490_spire_joint() -> None:
    """Where the spire's vertebrae meet: darker, ridged round like a disc of cartilage turned to stone."""
    rng = G.rng_for(49013)
    X, Y = G.grid()
    img = G.fill((98, 96, 94), (S, S)).astype(float)
    ridges = np.sin(Y / S * np.pi * 2 * 4 + G.blur(G.fnoise(rng, beta=1.6, kmin=2, aniso=(1.0, 0.12)), 0.8) * 0.8)
    img = G.add_lum(img, ridges * 9 + G.fnoise(rng, beta=1.8, kmin=3) * 4 + rng.normal(0, 2.5, (S, S)))
    cracks = G.smoothstep(0.93, 0.995, np.abs(G.fnoise(rng, beta=1.5, kmin=4)) / 3.0)
    img = G.add_lum(img, -cracks * 26)
    _save(img, "l490_spire_joint")


def tex_l490_ladder() -> None:
    """A rusted iron ladder (RGBA): two flat rails, four round rungs, rivets, rust running down."""
    rng = G.rng_for(49014)
    X, Y = G.grid()
    rails = ((X >= 14) & (X < 30)) | ((X >= 98) & (X < 114))
    rungs = np.zeros((S, S), bool)
    for y0 in (10, 42, 74, 106):
        rungs |= (Y >= y0) & (Y < y0 + 9) & (X >= 24) & (X < 104)
    alpha = (rails | rungs).astype(float)
    img = G.add_lum(G.fill((72, 68, 66), (S, S)).astype(float), G.fnoise(rng, beta=2.0) * 4 + rng.normal(0, 2.5, (S, S)))
    rust = G.smoothstep(0.4, 1.9, G.blur(G.fnoise(rng, beta=1.4, kmin=4, aniso=(1.0, 8.0)), 0.6))
    img = G.over(img, np.array([118, 72, 44]), rust * 0.6)
    # Rungs lit from above, shadowed below; rivets where they meet the rails.
    for y0 in (10, 42, 74, 106):
        band = (Y - y0) / 9.0
        inside = rungs & (band >= 0) & (band < 1)
        img = G.add_lum(img, np.where(inside & (band < 0.35), 12, 0) + np.where(inside & (band > 0.7), -10, 0))
        for cx in (22, 106):
            img = G.add_lum(img, -24 * (np.hypot(X - cx, Y - (y0 + 4.5)) < 2.2))
    rgba = np.dstack([np.clip(img, 0, 255), alpha * 255])
    G.save_png(rgba, _blk("l490_ladder"))


def _scratch(mask, img):
    """Scratches cut into stone: pale where the surface is broken, a dark lip on one side."""
    m = G.blur(mask, 0.6)
    shadow = np.roll(np.roll(m, 1, 0), 1, 1)
    img = G.add_lum(img, -np.clip(shadow - m, 0, 1) * 40)
    return G.over(img, np.array([196, 196, 190]), np.clip(m, 0, 1) * 0.75)


def _tally(draw, rng, x, y, size, n):
    """n tally strokes at (x, y): upright strokes, the fifth across them."""
    gap = size * 0.24
    for i in range(min(n, 4)):
        x0 = x + i * gap + rng.uniform(-1.5, 1.5)
        draw.line([(x0 + rng.uniform(-2, 2), y), (x0 + rng.uniform(-2, 2), y + size)], fill=255, width=3 if size > 40 else 2)
    if n >= 5:
        draw.line([(x - gap * 0.6, y + size * 0.8), (x + gap * 3.6, y + size * 0.2)], fill=255, width=3 if size > 40 else 2)


def tex_l490_marks() -> None:
    """The stairwell stone with tally marks scratched in: 1-5 strokes, and a wall covered in them."""
    from PIL import Image, ImageDraw
    base = _gothic_img()
    for n in range(1, 7):
        rng = G.rng_for(49020 + n)
        layer = Image.new("L", (S, S), 0)
        draw = ImageDraw.Draw(layer)
        if n <= 5:
            _tally(draw, rng, 34, 30, 64, n)
        else:
            for row in range(3):
                for col in range(3):
                    _tally(draw, rng, 8 + col * 42 + rng.uniform(-3, 3), 8 + row * 40 + rng.uniform(-3, 3), 28, 5)
        _save(_scratch(np.asarray(layer, float) / 255.0, base.copy()), f"l490_marked_{n}")


def tex_l490_words() -> None:
    """The stairwell stone with GO BACK / DOWN scratched into it, unevenly, gone over more than once
    (Pillow's built-in font, Aileron, is CC0)."""
    from PIL import Image, ImageDraw, ImageFont
    rng = G.rng_for(49030)
    font = ImageFont.load_default(size=24)
    layer = Image.new("L", (S, S), 0)
    draw = ImageDraw.Draw(layer)
    for line, (x, y) in (("GO BACK", (12, 30)), ("DOWN", (30, 68))):
        for _ in range(3):
            draw.text((x + rng.uniform(-1.5, 1.5), y + rng.uniform(-1.5, 1.5)), line, fill=255, font=font)
    mask = np.asarray(layer, float) / 255.0
    mask *= G.smoothstep(-1.2, 0.2, G.fnoise(rng, beta=1.2, kmin=10))  # broken where the blade skipped
    _save(_scratch(mask, _gothic_img()), "l490_words")


def tex_l490_puff() -> None:
    """A soft puff of cloud (RGBA) for the fog pouring off the whales: a lumpy round body, denser in the middle,
    its edge broken into wisps; lighter at the top, where the moon catches it."""
    rng = G.rng_for(49040)
    X, Y = G.grid()
    c = S / 2
    r = np.hypot(X - c, Y - c) / (S / 2)
    lumps = G.blur(G.fnoise(rng, beta=1.6, kmin=2), 1.5) * 0.18 + G.fnoise(rng, beta=1.2, kmin=8) * 0.05
    body = np.clip(1.0 - np.clip(r + lumps, 0, None) ** 1.6, 0, 1)
    alpha = G.smoothstep(0.0, 0.55, body)
    shade = 0.78 + 0.22 * (1 - Y / S) + G.fnoise(rng, beta=1.8, kmin=3) * 0.05
    img = np.dstack([np.clip(shade * 255, 0, 255)] * 3 + [alpha * 255])
    G.save_png(img, G.TEX / "environment" / "l490_puff.png")


def _worley3(rng, n_cells: int, size: int = 64) -> np.ndarray:
    """Tileable 3D Worley noise (size^3): distance to the nearest of one random point per cell,
    0 at a point, about 1 at the farthest."""
    pts = rng.random((n_cells, n_cells, n_cells, 3))
    c = (np.arange(size) + 0.5) * n_cells / size
    X, Y, Z = np.meshgrid(c, c, c, indexing="ij")
    ix, iy, iz = np.floor(X).astype(int), np.floor(Y).astype(int), np.floor(Z).astype(int)
    best = np.full(X.shape, 9.0)
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                cx, cy, cz = ix + dx, iy + dy, iz + dz
                p = pts[cx % n_cells, cy % n_cells, cz % n_cells]
                d = (X - (cx + p[..., 0])) ** 2 + (Y - (cy + p[..., 1])) ** 2 + (Z - (cz + p[..., 2])) ** 2
                best = np.minimum(best, d)
    return np.clip(np.sqrt(best) / 0.9, 0, 1)


def _fbm3(rng, size: int = 64, beta: float = 3.0, kmin: float = 2.0) -> np.ndarray:
    """Tileable 3D fractal noise (power spectrum ~ 1/k^beta), normalised to 0..1."""
    k = np.fft.fftfreq(size) * size
    KX, KY, KZ = np.meshgrid(k, k, k, indexing="ij")
    kk = np.sqrt(KX ** 2 + KY ** 2 + KZ ** 2)
    amp = np.where(kk >= kmin, np.maximum(kk, 1e-6) ** (-beta / 2), 0.0)
    spec = (rng.normal(size=kk.shape) + 1j * rng.normal(size=kk.shape)) * amp
    a = np.real(np.fft.ifftn(spec))
    return (a - a.min()) / (a.max() - a.min())


def tex_l490_cloud_noise() -> None:
    """Tileable 3D noise for Level 490's cloud shader (shaders/core/l490_clouds.fsh): 64 slices of
    64 x 64, packed 8 x 8 into one 512 x 512 image (slice z at column z % 8, row z // 8).
      R  the clouds' shapes: smooth fractal noise made billowy by inverted Worley noise (Perlin-Worley)
      G  detail that eats the edges: finer inverted Worley noise in three octaves
      B  plain smooth fractal noise (coverage, variety)"""
    from PIL import Image
    rng = np.random.default_rng(49050)
    size = 64
    w4, w8, w16, w32 = (_worley3(rng, n, size) for n in (4, 8, 16, 32))
    billow = 1.0 - (0.625 * w4 + 0.25 * w8 + 0.125 * w16)
    smooth = _fbm3(rng, size, beta=3.2, kmin=1.5)
    # Perlin-Worley: the smooth noise remapped by the billows, so it clumps into round heads.
    pw = np.clip((smooth - (1.0 - billow)) / (1.0 - (1.0 - billow) + 1e-6), 0, 1)
    pw = 0.5 * pw + 0.5 * billow
    detail = 1.0 - (0.625 * w8 + 0.25 * w16 + 0.125 * w32)
    plain = _fbm3(rng, size, beta=2.4, kmin=2.0)

    def norm(a):
        return (a - a.min()) / (a.max() - a.min())

    channels = [norm(pw), norm(detail), norm(plain)]
    atlas = np.zeros((512, 512, 4), np.uint8)
    atlas[..., 3] = 255
    for z in range(size):
        col, row = z % 8, z // 8
        for c in range(3):
            # Rows are y, columns x within a slice.
            atlas[row * 64:(row + 1) * 64, col * 64:(col + 1) * 64, c] = np.round(channels[c][:, :, z].T * 255)
    path = G.TEX / "environment" / "l490_cloud_noise.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(atlas, "RGBA").save(path)


def tex_l490_whale() -> None:
    """The sky whales' skin (client/Level490Whales), 1024 x 512. The body wraps the top 384 rows:
    u along it (0 the nose, 1 the tail), v round it (0 and 1 the top of the back, 0.5 the belly).
    The bottom 128 rows are the fins and flukes (u along the fin, v across it).
      - pale grey, darker along the back, paler and pinker-grey on the belly
      - the throat pleats: dark grooves on the underside from the chin to half way
      - knobbly bumps on the head, barnacle clusters on the chin, the head and the fins
      - old scars: pale scratches, darker mottling"""
    rng = G.rng_for(49070)
    W, H = 1024, 512
    BH = 384
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    u = xx / W
    v = yy / BH
    img = np.zeros((H, W, 3))
    # Round the body: 0 back .. 0.5 belly .. 1 back.
    belly = 1.0 - np.abs(np.cos(np.pi * v))  # 0 on the back, 1 on the belly
    back = np.array([118, 121, 126], float)
    under = np.array([196, 192, 190], float)
    t = np.clip(belly * 1.3 - 0.15, 0, 1)
    body = back[None, None] * (1 - t[..., None]) + under[None, None] * t[..., None]
    def noise(shape, beta, kmin):
        from numpy.fft import fft2, ifft2, fftfreq
        h, w = shape
        ky, kx = np.meshgrid(fftfreq(h) * h, fftfreq(w) * w, indexing="ij")
        k = np.hypot(kx, ky * (w / h) * 0.5)
        amp = np.where(k >= kmin, np.maximum(k, 1e-6) ** (-beta / 2), 0)
        a = np.real(ifft2((rng.normal(size=k.shape) + 1j * rng.normal(size=k.shape)) * amp))
        return a / (a.std() + 1e-9)
    mott = noise((H, W), 2.2, 3)
    fine = noise((H, W), 1.0, 40)
    body = body + (mott * 7 + fine * 3)[..., None]
    # The throat pleats: grooves round the underside, from the chin to half way along.
    pleat_zone = (belly > 0.62) & (u > 0.03) & (u < 0.5)
    groove = np.abs(np.sin(v * np.pi * 90)) ** 3
    fade = np.clip((0.5 - u) / 0.12, 0, 1) * np.clip((belly - 0.62) / 0.12, 0, 1)
    body = body - (groove * fade * 30)[..., None] * pleat_zone[..., None]
    # Knobs on the head (the top, the first 15 %).
    knobs = np.zeros((H, W))
    for _ in range(140):
        cu = rng.uniform(0.01, 0.15)
        cv = rng.choice([rng.uniform(0.0, 0.12), rng.uniform(0.88, 1.0)])
        r = rng.uniform(3, 6)
        d = np.hypot((xx - cu * W), (yy - cv * BH))
        knobs = np.maximum(knobs, np.clip(1 - d / r, 0, 1))
    body = body + (knobs * 34)[..., None] - (np.roll(knobs, 3, 0) * 18)[..., None]
    # Barnacles: clusters of pale rings on the chin and head.
    def barnacles(region_u, region_v, count, size):
        out = np.zeros((H, W))
        for _ in range(count):
            cu = rng.uniform(*region_u) * W
            cv = rng.uniform(*region_v) * (BH if region_v[1] <= 1 else 1)
            for _ in range(rng.integers(4, 14)):
                px = cu + rng.normal(0, size * 2.2)
                py = cv + rng.normal(0, size * 2.2)
                rr = rng.uniform(size * 0.5, size)
                d = np.hypot(xx - px, yy - py)
                ring = np.clip(1 - np.abs(d - rr * 0.7) / (rr * 0.35), 0, 1) * (d < rr * 1.2)
                out = np.maximum(out, ring)
        return out
    bar = barnacles((0.0, 0.1), (0.35, 0.65), 16, 4.5) + barnacles((0.02, 0.16), (0.0, 0.1), 10, 3.5)
    body = body * (1 - np.clip(bar, 0, 1)[..., None] * 0.5) + np.clip(bar, 0, 1)[..., None] * np.array([214, 210, 200])[None, None] * 0.5
    # Scars: long pale scratches.
    scars = np.zeros((H, W))
    for _ in range(11):
        x0, y0 = rng.uniform(0.1, 0.9) * W, rng.uniform(0.05, 0.95) * BH
        ang = rng.uniform(-0.5, 0.5)
        ln = rng.uniform(30, 120)
        for k in range(rng.integers(1, 4)):
            off = k * 4
            t = np.clip(((xx - x0) * np.cos(ang) + (yy - y0) * np.sin(ang)) / ln, 0, 1)
            px = x0 + t * ln * np.cos(ang) - off * np.sin(ang)
            py = y0 + t * ln * np.sin(ang) + off * np.cos(ang)
            d = np.hypot(xx - px, yy - py)
            scars = np.maximum(scars, np.clip(1 - d / 1.2, 0, 1) * (t > 0) * (t < 1) * (0.5 + 0.5 * np.sin(t * 9 + k)))
    body = body + (scars * 24)[..., None]
    img[:BH] = body[:BH]
    # The fins (bottom quarter): pale, a darker trailing edge, bumps along the leading edge, barnacles.
    fv = (yy[BH:] - BH) / (H - BH)
    fu = xx[BH:] / W
    fin = np.array([206, 204, 200], float)[None, None] + (noise((H - BH, W), 2.0, 3) * 6 + noise((H - BH, W), 1.0, 30) * 3)[..., None]
    edge = np.clip((fv - 0.75) / 0.25, 0, 1)
    fin = fin - (edge * 50)[..., None]
    lead = np.clip(1 - np.abs(fv - 0.08) / 0.08, 0, 1) * (0.5 + 0.5 * np.sin(fu * np.pi * 60)) ** 4
    fin = fin + (lead * 22)[..., None]
    img[BH:] = fin
    img = np.clip(img, 0, 255).astype(np.uint8)
    from PIL import Image
    path = G.TEX / "entity" / "l490_whale.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(img, "RGB").save(path)


def tex_l490_rain() -> None:
    """Level 490's own rain (client/Level490Rain).
      - l490_rain_streak (16 x 64): one drop's streak, a soft line brightest at its head (v = 1),
        fading up its tail; the bottom rows double as a splash ring.
      - l490_rain_sheet (128 x 256, tiles up and down): many fine streaks for the far sheets."""
    from PIL import Image
    rng = G.rng_for(49080)
    W, H = 16, 64
    yy, xx = np.mgrid[0:H, 0:W].astype(float)
    across = np.exp(-((xx - 7.5) / 2.6) ** 2)
    head = (yy / (H - 1)) ** 1.6
    a = across * head
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = 235
    img[..., 3] = np.clip(a * 255, 0, 255).astype(np.uint8)
    # Rows 32-38 (v 0.5-0.6): a ring for the splashes, drawn flat on the ground.
    ring_rows = slice(32, 39)
    rx = (xx[ring_rows] - 7.5) / 7.5
    img[ring_rows, :, 3] = np.clip((1 - np.abs(np.abs(rx) - 0.75) / 0.25) * 200, 0, 255).astype(np.uint8)
    Image.fromarray(img, "RGBA").save(G.TEX / "environment" / "l490_rain_streak.png")
    W, H = 128, 256
    sheet = np.zeros((H, W))
    for _ in range(260):
        x = rng.uniform(0, W)
        y0 = rng.uniform(0, H)
        ln = rng.uniform(20, 70)
        for yv in range(int(ln)):
            yi = int(y0 + yv) % H
            for dx in (-1, 0, 1):
                xi = int(x + dx) % W
                sheet[yi, xi] = max(sheet[yi, xi], (yv / ln) * (0.6 if dx else 1.0))
    sheet = sheet * 0.7 + 0.3 * np.clip(rng.normal(0.5, 0.15, (H, W)), 0, 1) * 0.4
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = 225
    img[..., 3] = np.clip(sheet * 255, 0, 255).astype(np.uint8)
    Image.fromarray(img, "RGBA").save(G.TEX / "environment" / "l490_rain_sheet.png")


def tex_l490_moon() -> None:
    """The moon for the sky (RGBA): a pale disk with darker seas, in a soft glow that fades out."""
    rng = G.rng_for(49006)
    X, Y = G.grid()
    r = np.hypot(X - S / 2, Y - S / 2) / (S / 2)
    disk = G.smoothstep(0.36, 0.33, r)
    seas = G.smoothstep(0.2, 1.4, G.blur(G.fnoise(rng, beta=2.2, kmin=2), 1.5))
    tone = 235 - seas * 55 + G.fnoise(rng, beta=1.4, kmin=6) * 4
    halo = np.clip(1.0 - r, 0, 1) ** 2.6 * 0.55
    alpha = np.clip(disk + halo * (1 - disk), 0, 1) * 255
    rgb = np.stack([tone, tone + 3, tone + 10], axis=-1) * disk[..., None] + np.array([200, 210, 228]) * (1 - disk[..., None])
    img = np.concatenate([np.clip(rgb, 0, 255), alpha[..., None]], axis=-1)
    G.save_png(img, G.TEX / "environment" / "l490_moon.png")


# =============================================================================
# Models
# =============================================================================

def _json(rel: str, obj) -> None:
    path = ASSETS / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", newline="\n")


def _ref(name: str) -> str:
    return f"backrooms:block/{name}"


def _model(name: str, obj) -> None:
    _json(f"models/block/{name}.json", obj)


def _el(frm, to, tex="#iron"):
    """A model element with all six faces. Iron faces all take one small patch of the texture:
    worked out from the position, the parts reaching above the block (the spear tips) would
    sample outside it on the atlas and show up white."""
    face = {"texture": tex} if tex != "#iron" else {"texture": tex, "uv": [5, 5, 9, 9]}
    return {"from": list(frm), "to": list(to),
            "faces": {f: dict(face) for f in ("north", "south", "east", "west", "up", "down")}}


def _spear(cx, cz, y):
    """A spear tip on a 1-wide bar ending at height y: a collar, a broad head, a point."""
    return [_el((cx - 1.0, y, cz - 1.0), (cx + 1.0, y + 1.0, cz + 1.0)),
            _el((cx - 0.75, y + 1.0, cz - 0.75), (cx + 0.75, y + 2.5, cz + 0.75)),
            _el((cx - 0.35, y + 2.5, cz - 0.35), (cx + 0.35, y + 4.0, cz + 0.35))]


IRON_TEX = {"iron": _ref("l490_iron"), "particle": _ref("l490_iron")}


def tex_l490_models() -> None:
    """Block models, blockstates and item models of every Level 490 block."""
    for name in ("l490_paving", "l490_stone", "l490_slate", "l490_rock", "l490_chain_iron", "l490_dead_wood",
                 "l490_spire", "l490_spire_joint", "l490_words", "l490_gothic", "l490_gothic_carved",
                 "l490_window_lit", "l490_window_dark"):
        _model(name, {"parent": "minecraft:block/cube_all", "textures": {"all": _ref(name)}})
        _json(f"blockstates/{name}.json", {"variants": {"": {"model": _ref(name)}}})
        _json(f"models/item/{name}.json", {"parent": _ref(name)})

    # The broken flagstones: each block picks one of the variants at random.
    for i in range(len(RUBBLE_VARIANTS)):
        _model(_rubble_name(i), {"parent": "minecraft:block/cube_all", "textures": {"all": _ref(_rubble_name(i))}})
    _json("blockstates/l490_rubble.json",
          {"variants": {"": [{"model": _ref(_rubble_name(i))} for i in range(len(RUBBLE_VARIANTS))]}})
    _json("models/item/l490_rubble.json", {"parent": _ref("l490_rubble")})

    _model("l490_dead_grass", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": _ref("l490_dead_grass"), "side": _ref("l490_dead_grass_side"), "bottom": _ref("l490_rock")}})
    _json("blockstates/l490_dead_grass.json", {"variants": {"": {"model": _ref("l490_dead_grass")}}})
    _json("models/item/l490_dead_grass.json", {"parent": _ref("l490_dead_grass")})
    _model("l490_red_leaves", {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout",
                               "textures": {"all": _ref("l490_red_leaves")}})
    _json("blockstates/l490_red_leaves.json", {"variants": {"": {"model": _ref("l490_red_leaves")}}})
    _json("models/item/l490_red_leaves.json", {"parent": _ref("l490_red_leaves")})
    pav = {"bottom": _ref("l490_paving"), "top": _ref("l490_paving"), "side": _ref("l490_paving")}
    _model("l490_paving_slab", {"parent": "minecraft:block/slab", "textures": pav})
    _model("l490_paving_slab_top", {"parent": "minecraft:block/slab_top", "textures": pav})
    # Half the slabs are cracked like the broken flagstones (after the play-test: "the slabs need the
    # crack textures too"): each picks plain (weight 7) or one of the cracked variants (1 each).
    bottoms = [{"model": _ref("l490_paving_slab"), "weight": len(RUBBLE_VARIANTS)}]
    tops = [{"model": _ref("l490_paving_slab_top"), "weight": len(RUBBLE_VARIANTS)}]
    for i in range(len(RUBBLE_VARIANTS)):
        cracked = {"bottom": _ref(_rubble_name(i)), "top": _ref(_rubble_name(i)), "side": _ref("l490_paving")}
        _model(f"l490_paving_slab_cracked_{i}", {"parent": "minecraft:block/slab", "textures": cracked})
        _model(f"l490_paving_slab_top_cracked_{i}", {"parent": "minecraft:block/slab_top", "textures": cracked})
        bottoms.append({"model": _ref(f"l490_paving_slab_cracked_{i}")})
        tops.append({"model": _ref(f"l490_paving_slab_top_cracked_{i}")})
    _json("blockstates/l490_paving_slab.json", {"variants": {
        "type=bottom": bottoms, "type=top": tops, "type=double": {"model": _ref("l490_paving")}}})
    _json("models/item/l490_paving_slab.json", {"parent": _ref("l490_paving_slab")})

    # The railing: a picket post with a spear tip, and toward each neighbour two rails with a
    # picket between them (a fence's multipart; the side model points north).
    _model("l490_railing_post", {"textures": IRON_TEX,
                                 "elements": [_el((7.5, 0, 7.5), (8.5, 21, 8.5))] + _spear(8, 8, 21)})
    _model("l490_railing_side", {"textures": IRON_TEX, "elements": [
        _el((7.6, 2, 0), (8.4, 3, 8)),
        _el((7.4, 17, 0), (8.6, 18.5, 8)),
        _el((7.6, 0, 3.6), (8.4, 21, 4.4)),
    ] + _spear(8, 4, 21)})
    _model("l490_post", {"textures": IRON_TEX, "elements": [
        _el((6.5, 0, 6.5), (9.5, 15, 9.5)),
        _el((5.5, 15, 5.5), (10.5, 16, 10.5)),
    ]})
    for name, post in (("l490_railing", "l490_railing_post"), ("l490_post", "l490_post")):
        parts = [{"apply": {"model": _ref(post)}}]
        for side, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            apply = {"model": _ref("l490_railing_side"), "uvlock": True}
            if y:
                apply["y"] = y
            parts.append({"when": {side: "true"}, "apply": apply})
        _json(f"blockstates/{name}.json", {"multipart": parts})
    _model("l490_railing_item", {"parent": "minecraft:block/block", "textures": IRON_TEX, "elements": [
        _el((7.5, 0, 0), (8.5, 21, 1)), _el((7.5, 0, 15), (8.5, 21, 16)),
        _el((7.6, 2, 0), (8.4, 3, 16)), _el((7.4, 17, 0), (8.6, 18.5, 16)),
        _el((7.6, 0, 7.6), (8.4, 21, 8.4))] + _spear(8, 8, 21)})
    _json("models/item/l490_railing.json", {"parent": _ref("l490_railing_item")})
    _json("models/item/l490_post.json", {"parent": _ref("l490_post")})

    # The lantern: an iron base and cap, glass all round, a ring on top.
    for lit in (False, True):
        glass = _ref("l490_lantern_glass_lit" if lit else "l490_lantern_glass")
        name = "l490_lantern_lit" if lit else "l490_lantern"
        _model(name, {"textures": {"iron": _ref("l490_iron"), "glass": glass, "particle": _ref("l490_iron")}, "elements": [
            _el((4.5, 0, 4.5), (11.5, 1.5, 11.5)),
            _el((5, 1.5, 5), (11, 8.5, 11), "#glass"),
            _el((4.6, 1.5, 4.6), (5.4, 8.5, 5.4)), _el((10.6, 1.5, 4.6), (11.4, 8.5, 5.4)),
            _el((4.6, 1.5, 10.6), (5.4, 8.5, 11.4)), _el((10.6, 1.5, 10.6), (11.4, 8.5, 11.4)),
            _el((4.5, 8.5, 4.5), (11.5, 9.5, 11.5)),
            _el((6, 9.5, 6), (10, 10.5, 10)),
            _el((7.5, 10.5, 7), (8.5, 12, 9)),
        ]})
    _json("blockstates/l490_lantern.json", {"variants": {
        "lit=false": {"model": _ref("l490_lantern")}, "lit=true": {"model": _ref("l490_lantern_lit")}}})
    _json("models/item/l490_lantern.json", {"parent": _ref("l490_lantern_lit")})

    # A chain link: a thick oval ring standing in the x-y plane; every other link turned a
    # quarter (twist) so they hang interlocked.
    _model("l490_chain", {"textures": IRON_TEX, "elements": [
        _el((3, 2, 6.5), (6, 14, 9.5)), _el((10, 2, 6.5), (13, 14, 9.5)),
        _el((5, 0, 6.5), (11, 3, 9.5)), _el((5, 13, 6.5), (11, 16, 9.5)),
        _el((4, 1, 6.5), (6, 3, 9.5)), _el((10, 1, 6.5), (12, 3, 9.5)),
        _el((4, 13, 6.5), (6, 15, 9.5)), _el((10, 13, 6.5), (12, 15, 9.5)),
    ]})
    _json("blockstates/l490_chain.json", {"variants": {
        "twist=false": {"model": _ref("l490_chain")}, "twist=true": {"model": _ref("l490_chain"), "y": 90}}})
    _json("models/item/l490_chain.json", {"parent": _ref("l490_chain")})

    # The shackle: a ring bolted flat to the paving, a few links, an open cuff.
    _model("l490_shackle", {"textures": IRON_TEX, "elements": [
        _el((5, 0, 2), (11, 1, 3.2)), _el((5, 0, 6.8), (11, 1, 8)),
        _el((5, 0, 3.2), (6.2, 1, 6.8)), _el((9.8, 0, 3.2), (11, 1, 6.8)),
        _el((7.4, 0, 8), (8.6, 0.8, 10)), _el((7, 0, 10), (9, 1, 11.2)),
        _el((7.4, 0, 11.2), (8.6, 0.8, 12.4)),
        _el((4.5, 0, 12.4), (11.5, 2.2, 13.6)),
        _el((4.5, 0, 13.6), (5.7, 2.2, 15.5)), _el((10.3, 0, 13.6), (11.5, 2.2, 15.5)),
    ]})
    _json("blockstates/l490_shackle.json", {"variants": {
        f"facing={f}": ({"model": _ref("l490_shackle"), "y": y} if y else {"model": _ref("l490_shackle")})
        for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
    _json("models/item/l490_shackle.json", {"parent": _ref("l490_shackle")})

    # The ladder: vanilla's flat ladder model with our rusted iron; facing is where it faces out.
    _model("l490_ladder", {"parent": "minecraft:block/ladder", "render_type": "minecraft:cutout",
                           "textures": {"texture": _ref("l490_ladder"), "particle": _ref("l490_ladder")}})
    _json("blockstates/l490_ladder.json", {"variants": {
        f"facing={f}": ({"model": _ref("l490_ladder"), "y": y} if y else {"model": _ref("l490_ladder")})
        for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
    _json("models/item/l490_ladder.json", {"parent": "minecraft:item/generated", "textures": {"layer0": _ref("l490_ladder")}})

    # The statue: a robed figure in two blocks (the head worn to a stump), facing south unturned.
    st = {"stone": _ref("l490_statue"), "particle": _ref("l490_statue")}
    _model("l490_statue_lower", {"textures": st, "elements": [
        _el((3.5, 0, 3.5), (12.5, 2, 12.5), "#stone"),
        _el((4.5, 2, 5), (11.5, 16, 11.5), "#stone"),
        _el((4, 2, 4.5), (12, 6, 12), "#stone"),
    ]})
    _model("l490_statue_upper", {"textures": st, "elements": [
        _el((5, 0, 5.5), (11, 9, 11), "#stone"),
        _el((3.5, 6, 6), (5, 9, 10), "#stone"), _el((11, 6, 6), (12.5, 9, 10), "#stone"),
        _el((6.5, 9, 7), (9.5, 11, 10), "#stone"),
        _el((6, 3, 10.5), (10, 6, 12), "#stone"),
    ]})
    _json("blockstates/l490_statue.json", {"variants": {
        f"facing={f},half={h}": ({"model": _ref(f"l490_statue_{h}"), "y": y} if y else {"model": _ref(f"l490_statue_{h}")})
        for f, y in (("south", 0), ("west", 90), ("north", 180), ("east", 270)) for h in ("lower", "upper")}})
    _json("models/item/l490_statue.json", {"parent": _ref("l490_statue_lower")})

    # The scratched stairwell stone, one model per number of marks.
    for n in range(1, 7):
        _model(f"l490_marked_stone_{n}", {"parent": "minecraft:block/cube_all", "textures": {"all": _ref(f"l490_marked_{n}")}})
    _json("blockstates/l490_marked_stone.json", {"variants": {
        f"marks={n}": {"model": _ref(f"l490_marked_stone_{n}")} for n in range(1, 7)}})
    _json("models/item/l490_marked_stone.json", {"parent": _ref("l490_marked_stone_5")})


TEXTURES = [tex_l490_paving, tex_l490_stone, tex_l490_slate, tex_l490_iron, tex_l490_lantern_glass,
            tex_l490_rock, tex_l490_rubble, tex_l490_dead_grass, tex_l490_chain_iron, tex_l490_dead_wood,
            tex_l490_red_leaves, tex_l490_spire, tex_l490_spire_joint, tex_l490_gothic, tex_l490_gothic_carved,
            tex_l490_window_lit, tex_l490_window_dark, tex_l490_statue, tex_l490_ladder, tex_l490_marks,
            tex_l490_words, tex_l490_puff, tex_l490_cloud_noise, tex_l490_whale, tex_l490_rain, tex_l490_moon, tex_l490_models]
SOUNDS = []
