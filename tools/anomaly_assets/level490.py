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
    for name in ("l490_paving", "l490_stone", "l490_slate"):
        _model(name, {"parent": "minecraft:block/cube_all", "textures": {"all": _ref(name)}})
        _json(f"blockstates/{name}.json", {"variants": {"": {"model": _ref(name)}}})
        _json(f"models/item/{name}.json", {"parent": _ref(name)})

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
            tex_l490_moon, tex_l490_models]
SOUNDS = []
