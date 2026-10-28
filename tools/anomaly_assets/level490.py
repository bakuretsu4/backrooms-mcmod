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


def tex_l490_paving() -> None:
    """Pale, worn flagstones (two by two per block): smoothed by feet, cracked, a few pits."""
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
    _save(img, "l490_paving")


def tex_l490_stone() -> None:
    """Big dressed stone blocks (two courses per block, joints staggered), streaked by the rain."""
    rng = G.rng_for(49002)
    img, _ = _stones(rng, STONE, 2, 1, True, 5.0, 5.0)
    streaks = G.blur(G.fnoise(rng, beta=1.4, kmin=6, aniso=(1.0, 0.08)), 0.6)
    img = G.add_lum(img, -G.smoothstep(0.5, 2.0, streaks) * 16)
    _save(img, "l490_stone")


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


def tex_l490_rubble() -> None:
    """Broken masonry and grit: chips of pale stone in dark grit."""
    rng = G.rng_for(49008)
    img = G.fill((84, 86, 90), (S, S)).astype(float)
    img = G.add_lum(img, G.fnoise(rng, beta=1.4, kmin=6) * 6 + rng.normal(0, 4, (S, S)))
    X, Y = G.grid()
    chips = np.zeros((S, S))
    tone = np.zeros((S, S))
    for _ in range(46):
        cx, cy = rng.uniform(0, S, 2)
        rx, ry = rng.uniform(4, 12, 2)
        dx = (X - cx + S / 2) % S - S / 2
        dy = (Y - cy + S / 2) % S - S / 2
        m = G.smoothstep(1.05, 0.85, np.hypot(dx / rx, dy / ry) + 0.25 * G.fnoise(rng, beta=1.0, kmin=8) / 3)
        tone = np.where(m > chips, rng.normal(0, 10), tone)
        chips = np.maximum(chips, m)
    stone = G.add_lum(G.fill((136, 138, 142), (S, S)).astype(float), tone + _relief(chips * 6, 6))
    img = G.over(img, stone, chips)
    _save(img, "l490_rubble")


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
    for name in ("l490_paving", "l490_stone", "l490_slate", "l490_rock", "l490_rubble", "l490_chain_iron", "l490_dead_wood"):
        _model(name, {"parent": "minecraft:block/cube_all", "textures": {"all": _ref(name)}})
        _json(f"blockstates/{name}.json", {"variants": {"": {"model": _ref(name)}}})
        _json(f"models/item/{name}.json", {"parent": _ref(name)})

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
    _json("blockstates/l490_paving_slab.json", {"variants": {
        "type=bottom": {"model": _ref("l490_paving_slab")}, "type=top": {"model": _ref("l490_paving_slab_top")},
        "type=double": {"model": _ref("l490_paving")}}})
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


TEXTURES = [tex_l490_paving, tex_l490_stone, tex_l490_slate, tex_l490_iron, tex_l490_lantern_glass,
            tex_l490_rock, tex_l490_rubble, tex_l490_dead_grass, tex_l490_chain_iron, tex_l490_dead_wood,
            tex_l490_red_leaves, tex_l490_moon, tex_l490_models]
SOUNDS = []
