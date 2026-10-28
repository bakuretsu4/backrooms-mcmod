"""Level 2 "Pipe Dreams": the materials. Concrete (walls, wall base, floor, lane-line floor,
ceiling slab, with their aged versions) and metal (the pipe materials, paint, strut, clamps,
flanges, the I-beams, the floor grating and gutter, the leak stains).

What makes them read as real, in a dim corridor lit by a few amber bulbs:
- Concrete is never one flat colour: formwork panels differ in tone, bugholes and tie holes pock
  it, and water writes on it (dark damp bands, tide lines with a crust of white salts where the
  water dried, rust bleeding from every steel fixing).
- Metal is mostly value contrast: dark mill scale against bright worn edges and scrapes, brushed
  streaks along the pipe, rust that pits instead of just tinting, beads of condensation (a dark
  rim and a bright point) on the cold pipes.
- The aged versions of the surfaces are separate textures (block/Level2SurfaceBlock picks them,
  clustered in damp stretches of the corridor), so the plain textures stay calm and tile well.

Every surface keeps the mean brightness the lighting was tuned for (see _keep_mean), and every
texture has its own fixed seed (2300-2499; new ones here 2470-2499), so re-running gives
identical files.

Run with:  python tools/gen_assets.py level2_materials
(writes the textures, the aged versions and the version blockstates/models of the surfaces;
tools/anomaly_assets/level2_revamp.py builds the pipe and prop models that use them).

Atlas: the corridor surfaces are 128 px (big close-up faces); pipes, metals and the beam are
drawn at 128 and saved at 64; the 16 px pipe caps are flat colours.
"""
from __future__ import annotations

import numpy as np

import gen_assets as G

S = G.S


# =============================================================================
# Helpers (also used by level2_revamp for the props)
# =============================================================================

def _blk(name: str):
    return G.TEX / "block" / f"{name}.png"


def _fill(color) -> np.ndarray:
    return G.fill(color, (S, S))


def _local(cx, cy, sx, sy) -> np.ndarray:
    X, Y = G.grid()
    return np.exp(-(((X - cx) / sx) ** 2 + ((Y - cy) / sy) ** 2) / 2)


def _wear(rng, amount=0.25, scale=1.6) -> np.ndarray:
    """0..1 mask of worn-off paint (1 = paint still there)."""
    return 1 - G.smoothstep(2.0 - amount * 2, 2.6 - amount * 2, G.blur(G.fnoise(rng, beta=scale), 0.6))


def _along(rng, beta=1.4, k=10) -> np.ndarray:
    """Noise stretched along the pipe (u): streaks and grain that run lengthwise."""
    return G.fnoise(rng, beta=beta, aniso=(1, k), kmin=2)


def _across(rng, beta=1.4, k=10) -> np.ndarray:
    """Noise stretched round the pipe (v): drips running down the sides."""
    return G.fnoise(rng, beta=beta, aniso=(k, 1), kmin=2)


def _round(img, spec=1.0) -> np.ndarray:
    """Bake a lit cylinder's shading across v (see level2_revamp._frame_box round_): v = 0 is the
    top of the pipe, 8 its side, 16 its underside. A little darker on top (so the top flat
    doesn't stand out as a box edge), a bright specular line about a third of the way down, then
    falling off to a dark underside. spec = how shiny (0 for paint-dull jacket)."""
    X, Y = G.grid(img.shape[:2])
    phi = Y / img.shape[0] * 180.0
    diffuse = np.clip(np.cos(np.radians(phi - 42)), 0, None) ** 0.9
    hl = np.exp(-((phi - 36) / 8) ** 2)
    b = 0.34 + 0.56 * diffuse + 0.22 * spec * hl
    out = img.copy()
    out[..., :3] = img[..., :3] * b[..., None] + 34 * spec * hl[..., None]
    return out


def _lum(img) -> float:
    return float((img[..., :3] @ np.array([0.2126, 0.7152, 0.0722])).mean())


def _keep_mean(img, target) -> np.ndarray:
    """Scale the colours so the mean luminance is `target`: the Level 2 lighting (fill, lamp
    falloff, grade) was tuned against these albedos, so a richer texture mustn't drift brighter
    or darker overall."""
    out = img.copy()
    out[..., :3] = img[..., :3] * (target / max(_lum(img), 1e-6))
    return out


def _edge_fade(margin=14.0) -> np.ndarray:
    """1 in the middle, 0 at the block edges: for marks that must not be cut off by a
    neighbouring block that is the plain version."""
    X, Y = G.grid()
    d = np.minimum(np.minimum(X, S - X), np.minimum(Y, S - Y))
    return G.smoothstep(1.0, margin, d)


def _side_fade(margin=14.0) -> np.ndarray:
    """1 away from the left/right edges (marks that run off the top or bottom on purpose)."""
    X, _ = G.grid()
    return G.smoothstep(1.0, margin, np.minimum(X, S - X))


def _aggregate(rng, mask, density=0.05, size=(0.7, 2.2)) -> np.ndarray:
    """Exposed aggregate where the cement skin is gone: small stones in grey, buff and
    brown-black, each lit from above (bright top edge, dark under-edge). Returns RGB to blend
    over the surface by `mask`."""
    X, Y = G.grid()
    img = G.add_lum(_fill((112, 108, 100)), 10 * G.blur(rng.normal(0, 1, (S, S)), 0.7))
    n = int(density * S * S / 10)
    tones = np.array([(150, 146, 138), (128, 118, 100), (84, 80, 76), (170, 166, 158), (106, 96, 84)], float)
    for _ in range(n):
        cx, cy = rng.uniform(0, S, 2)
        if mask[int(cy) % S, int(cx) % S] < 0.05:
            continue
        r = rng.uniform(*size)
        d = np.hypot((X - cx) / (r * rng.uniform(0.8, 1.3)), (Y - cy) / r)
        stone = G.smoothstep(1.1, 0.8, d)
        img = G.over(img, tones[rng.integers(len(tones))] + rng.normal(0, 6), stone)
        img = G.add_lum(img, 18 * stone * np.clip(-(Y - cy) / r, 0, 1) - 22 * G.smoothstep(1.4, 0.9, d) * (1 - stone) * (Y > cy))
    return img


def _bugholes(img, rng, n=18, r=(0.6, 1.8)) -> np.ndarray:
    """Bugholes: the trapped air bubbles of cast concrete. A dark pit (in shadow at the top,
    since the light comes from above) with a thin pale lip below."""
    X, Y = G.grid()
    for _ in range(n):
        cx, cy = rng.uniform(0, S, 2)
        rr = rng.uniform(*r)
        dx, dy = G.wrapd(X - cx, S), G.wrapd(Y - cy, S)
        d = np.hypot(dx, dy) / rr
        pit = G.smoothstep(1.15, 0.7, d)
        img = G.add_lum(img, -38 * pit * (0.7 + 0.3 * (dy < 0)))
        img = G.add_lum(img, 9 * G.smoothstep(1.7, 1.15, d) * (1 - pit) * (dy > 0))
    return img


def _crust(img, rng, edge, amount=0.5) -> np.ndarray:
    """Efflorescence: the fluffy white salts left where water dries out of concrete (`edge` is
    the 0..1 band where it forms)."""
    fluff = G.smoothstep(-0.3, 1.2, G.blur(G.fnoise(rng, beta=2.2, kmin=3), 0.6))
    return G.over(img, np.array([206, 204, 196]), np.clip(edge * fluff * amount, 0, 0.8))


def _runs(rng, x0, y0, length, spread=5.0, n=(3, 6), width=(0.9, 2.4)) -> np.ndarray:
    """0..1 mask of liquid runs down a wall from about (x0, y0): a few wandering streaks that
    widen a little and fade out at different lengths."""
    X, Y = G.grid()
    m = np.zeros((S, S))
    for _ in range(int(rng.integers(*n))):
        xs = x0 + rng.normal(0, spread)
        wob = 1.6 * G.fnoise(rng, (S, 1), beta=2.2)[:, 0][np.clip(Y.astype(int), 0, S - 1)]
        w = rng.uniform(*width)
        ln = length * rng.uniform(0.45, 1.0)
        t = np.clip((Y - y0) / ln, 0, 1)
        m = np.maximum(m, np.exp(-((X - xs - wob) / (w * (1 + 0.7 * t))) ** 2) * (Y >= y0) * (1 - t) ** 1.3)
    return m * (0.75 + 0.25 * G.fnoise(rng, beta=1.0))


def _rivets(img, pts, r=2.2, col=None) -> np.ndarray:
    """Round domed rivet / bolt heads: lit from the top left, a shadow to the bottom right."""
    X, Y = G.grid()
    for x, y in pts:
        d = np.hypot(X - x, Y - y)
        img = G.add_lum(img, -26 * G.smoothstep(r + 1.6, r, np.hypot(X - x - 1.0, Y - y - 1.0)) * (d > r * 0.8))
        cover = G.smoothstep(r + 0.5, r - 0.5, d)
        if col is not None:
            img = G.over(img, np.array(col), cover)
        img = G.add_lum(img, cover * (16 * np.clip(-(X - x + Y - y) / (2 * r), -1, 1)))
    return img


def _hex_nut(img, cx, cy, r=3.2, col=(70, 62, 56)) -> np.ndarray:
    """A hex nut on a stud seen face on: flat faces catching the light differently."""
    X, Y = G.grid()
    ang = np.arctan2(Y - cy, X - cx)
    rad = np.hypot(X - cx, Y - cy)
    hexr = r * np.cos(np.pi / 6) / np.cos(((ang + np.pi / 6) % (np.pi / 3)) - np.pi / 6)
    nut = G.smoothstep(hexr + 0.5, hexr - 0.5, rad)
    img = G.add_lum(img, -30 * G.smoothstep(r + 2.2, r, np.hypot(X - cx - 1.2, Y - cy - 1.4)) * (1 - nut))
    face = np.floor(((ang + np.pi) / (np.pi / 3))) % 6
    shade = np.array([10, 18, 6, -12, -20, -6])[face.astype(int)]
    img = G.over(img, np.array(col), nut)
    img = G.add_lum(img, shade * nut)
    stud = G.smoothstep(r * 0.45 + 0.4, r * 0.45 - 0.4, rad)
    return G.add_lum(img, (-14 + 20 * np.clip(-(X - cx + Y - cy) / r, -1, 1)) * stud)


def _beads(rng, region, density=0.012, r=(0.6, 1.5)) -> tuple:
    """Condensation beads on a cold surface: returns (dark, bright) 0..1 masks: each drop darkens
    a little disc (water on metal reads darker) and has one bright highlight up and left."""
    X, Y = G.grid()
    dark = np.zeros((S, S))
    bright = np.zeros((S, S))
    n = int(density * S * S)
    for _ in range(n):
        cx, cy = rng.uniform(0, S, 2)
        if rng.random() > region[int(cy) % S, int(cx) % S]:
            continue
        rr = rng.uniform(*r)
        d = np.hypot(G.wrapd(X - cx, S), G.wrapd(Y - cy, S))
        dark = np.maximum(dark, G.smoothstep(rr + 0.4, rr - 0.3, d))
        bright = np.maximum(bright, G.smoothstep(rr * 0.5, 0.0, np.hypot(G.wrapd(X - cx + rr * 0.35, S), G.wrapd(Y - cy + rr * 0.35, S))))
    return dark, bright


def _chips(img, rng, amount=0.22, scale=1.3, bare=(54, 50, 48)) -> np.ndarray:
    """Chipped paint: the chips show dark steel, each with a thin bright rim of freshly exposed
    metal on its upper edge, a dark paint edge below it and a halo of rust bleeding round it."""
    chips = 1 - _wear(rng, amount, scale)
    up = np.roll(chips, 1, axis=0)
    img = G.over(img, np.array(bare), chips * 0.92)
    img = G.add_lum(img, 26 * np.clip(chips - up, 0, 1))                         # bright rim at the top
    img = G.add_lum(img, -16 * np.clip(np.roll(chips, -1, axis=0) - chips, 0, 1))  # paint edge shadow
    return G.over(img, np.array([118, 64, 34]), np.clip(G.blur(chips, 1.1) - chips, 0, 1) * 0.55)


# =============================================================================
# Concrete: the corridor surfaces
# =============================================================================

WALL = (150, 145, 136)        # grey-beige concrete (canon grey with a little warmth); ~#968F87
FLOOR = (124, 120, 112)       # the floor is a shade darker and browner (dust, oil, boots)
CEILING_SLAB = (84, 81, 76)   # dark soffit, mostly hidden behind the pipe bundles
RUST = (124, 70, 38)
RUST_DARK = (78, 44, 28)
TIE_HOLES = ((26, 44), (90, 44))
# Mean luminance each surface keeps (measured on the textures the lighting was tuned with).
WALL_L, BASE_L, FLOOR_L, CEILING_L = 143.0, 133.0, 120.0, 78.0


def _concrete_wall(rng) -> np.ndarray:
    """Poured concrete cast against plywood formwork, one panel per block.

    - a faint fin at the panel joints (left and top edges, so the joints line up block to block)
    - each panel a slightly different tone, cloudy laitance and darker pour streaks
    - the plywood's grain and a repair patch printed faintly into the surface
    - two tie holes per block, grout-plugged and recessed, one bleeding a little rust
    - bugholes of several sizes, soft mottling, faint efflorescence
    """
    X, Y = G.grid()
    img = G._smooth_concrete(rng, WALL, mottle=4.2, fine=1.4)
    img = G.add_lum(img, 1.8 * G.fnoise(rng, beta=1.2, aniso=(12, 1), kmin=3))       # plywood grain
    patch = G.smoothstep(0.92, 1.0, 1 - np.maximum(np.abs(X - 70) / 9, np.abs(Y - 92) / 4))
    img = G.add_lum(img, 2.2 * patch)
    tone = G.blur(G.fnoise(rng, beta=2.0, kmin=1, kmax=3), 6.0)
    img = G.add_lum(img, 5.0 * tone, (1.0, 0.98, 0.94))                               # panel-to-panel tone
    cloud = G.blur(G.fnoise(rng, beta=2.6, aniso=(1, 2.5), kmin=2), 2.0)
    img = G.add_lum(img, 4.0 * cloud, (1.0, 1.0, 1.03))                               # laitance clouds
    for d, w in ((np.abs(G.wrapd(X - 0.5, S)), 1.2), (np.abs(G.wrapd(Y - 0.5, S)), 0.8)):
        img = G.add_lum(img, w * (7 * np.exp(-(d / 0.6) ** 2) - 8 * np.exp(-((d - 1.6) / 0.7) ** 2)))
    lift = 70 + 1.2 * G.fnoise(rng, (1, S), beta=2.5)[0]                               # pour (lift) line
    img = G.add_lum(img, -5 * np.exp(-((Y - lift) / 0.8) ** 2) + 2.5 * np.exp(-((Y - lift + 1.6) / 0.8) ** 2))
    # Pour streaks: darker vertical runs, seamless along y so they carry on into the next block.
    streak = np.clip(G.fnoise(rng, beta=1.3, aniso=(1, 16), kmin=2), 0, None)
    img = G.over(img, np.array([100, 95, 88]), np.clip(streak * 0.12, 0, 0.24))
    bloom = G.smoothstep(1.1, 2.2, G.blur(G.fnoise(rng, beta=1.8, kmin=2), 1.0))
    img = _crust(img, rng, bloom, 0.18)                                                # faint old salts
    img = G._pores(img, rng, density=0.007, depth=10)
    img = _bugholes(img, rng, 14)
    for i, (tx, ty) in enumerate(TIE_HOLES):
        r = np.hypot(X - tx, Y - ty)
        img = G.add_lum(img, -22 * G.smoothstep(4.4, 3.4, r) * G.smoothstep(2.2, 3.2, r))
        img = G.over(img, np.array([124, 120, 112]), G.smoothstep(3.2, 2.4, r) * 0.9)
        img = G.add_lum(img, -14 * G.smoothstep(3.6, 1.0, np.hypot(X - tx, Y - ty + 1.6)) * (Y < ty))
        img = G.add_lum(img, 8 * G.smoothstep(1.0, 0.0, np.abs(r - 3.9)) * (Y > ty + 1))
        if i == 1:   # rust bleeding from the snapped steel tie, down the wall
            run = _runs(rng, tx, ty + 3, 34, spread=0.6, n=(1, 3), width=(0.9, 1.4))
            img = G.over(img, np.array([112, 72, 46]), np.clip(run, 0, 1) * 0.42)
    return img


def wall() -> np.ndarray:
    return _keep_mean(_concrete_wall(G.rng_for(2300)), WALL_L)


def wall_base() -> np.ndarray:
    """The lowest row of wall: the same concrete, the kicker joint ~10 cm up, and in the lower
    half a darker band of splashed grime fading upwards, an old faint tide line where water once
    stood, a few boot and cart scuffs."""
    rng = G.rng_for(2301)
    img = _concrete_wall(G.rng_for(2300))
    X, Y = G.grid()
    edge = 70 + 3 * G.blur(G.fnoise(rng, (1, S), beta=2.6), 2.0)[0]
    band = G.smoothstep(-6, 22, Y - edge)
    grime = G._smooth_concrete(rng, (100, 94, 86), mottle=4.0, fine=1.2)
    splash = G.smoothstep(0.0, 1.4, G.blur(G.fnoise(rng, beta=1.8, aniso=(1, 2), kmin=2), 1.0))
    img = G.lerp(img, grime, (band * (0.45 + 0.2 * splash))[..., None])
    old = 96 + 2.5 * G.blur(G.fnoise(rng, (1, S), beta=2.2), 1.5)[0]                  # an old tide line
    img = G.add_lum(img, -7 * np.exp(-((Y - old) / 0.9) ** 2), (1.0, 0.95, 0.85))
    img = _crust(img, rng, np.exp(-((Y - old + 2.2) / 1.4) ** 2), 0.22)
    drops = (rng.random((S, S)) < 0.004) * G.smoothstep(80, 120, Y)
    img = G.add_lum(img, -30 * G.blur(drops.astype(float), 0.7))                      # mud splash dots
    img = G.add_lum(img, -12 * G.smoothstep(S - 18, S, Y))                             # dust at the floor
    kicker = S - 13 + 0.6 * G.blur(G.fnoise(rng, (1, S), beta=2.5), 1.0)[0]
    img = G.add_lum(img, -9 * np.exp(-((Y - kicker) / 0.8) ** 2) + 4 * np.exp(-((Y - kicker + 1.8) / 0.9) ** 2))
    scuffs = np.zeros((S, S))
    for _ in range(4):
        cx, cy = rng.uniform(0, S), rng.uniform(96, S - 18)
        ln, tilt = rng.uniform(6, 16), rng.uniform(-0.08, 0.08)
        dx = G.wrapd(X - cx, S)
        scuffs = np.maximum(scuffs, np.exp(-((dx / ln) ** 4) - ((Y - cy - tilt * dx) / rng.uniform(0.6, 1.1)) ** 2)
                            * (0.6 + 0.4 * G.fnoise(rng, beta=0.8)))
    img = G.over(img, np.array([46, 44, 42]), np.clip(scuffs * 0.45, 0, 0.5))
    return _keep_mean(img, BASE_L)


# ---- aged versions of the walls ---------------------------------------------

def _bracket_rust(img, rng) -> np.ndarray:
    """The stump of an old pipe bracket near the top: a rusted base plate on two anchor nuts
    (the bracket was cut off long ago), a brown halo of rust soaked into the concrete round it,
    and rust running down from it in streaks that thin out on the way down."""
    X, Y = G.grid()
    cx, top = rng.uniform(34, 94), rng.uniform(12, 24)
    w, h = rng.uniform(7, 10), rng.uniform(5, 7)
    plate = G.box((S, S), cx - w, top - h, cx + w, top + h, soft=0.6)
    halo = G.blur(plate, 6.0) * (1 - plate)
    img = G.over(img, np.array(RUST_DARK), np.clip(halo * 1.2 * (0.7 + 0.3 * G.fnoise(rng, beta=1.0)), 0, 0.6))
    runs = _runs(rng, cx, top + h - 1, rng.uniform(84, 124), spread=w * 0.5, n=(4, 7), width=(1.2, 3.0))
    near = np.clip(1 - (Y - top) / 50, 0, 1)
    colour = G.lerp(np.array([132, 80, 46]), np.array([112, 58, 30]), near[..., None])
    img = G.lerp(img, colour, (np.clip(runs * 1.3, 0, 1) * 0.78 * _side_fade())[..., None])
    # The plate: rust over dark steel, pitted, its edges lifting in flakes.
    metal = G.add_lum(_fill((86, 56, 38)), 12 * G.fnoise(rng, beta=1.4))
    metal = G.over(metal, np.array([52, 42, 36]), G.smoothstep(0.2, 1.4, G.fnoise(rng, beta=2.0)) * 0.6)
    metal = G.add_lum(metal, -30 * G.blur((rng.random((S, S)) < 0.05).astype(float), 0.5))
    img = G.add_lum(img, -26 * np.clip(G.blur(np.roll(np.roll(plate, 2, 0), 1, 1), 1.0) - plate, 0, 1))
    img = G.lerp(img, metal, plate[..., None])
    img = G.add_lum(img, 14 * np.clip(plate - np.roll(plate, 1, axis=0), 0, 1))       # lit top edge
    for nx in (cx - w * 0.55, cx + w * 0.55):
        img = _hex_nut(img, nx, top, 2.6, (96, 62, 42))
    return img


def _condensation(img, rng, top_strength=1.0) -> np.ndarray:
    """Condensation running down from the ceiling (the cold pipes sweat, the drops run down the
    wall): a darker damp band that starts at the top edge in fingers of different length, wet
    through near the top, a brown tide line and a crust of white salts along its drying edge,
    and specks of black mould where it stays wet."""
    X, Y = G.grid()
    m = np.zeros((S, S))
    for _ in range(int(rng.integers(5, 9))):
        cx = rng.uniform(18, 110)
        w = rng.uniform(3, 9)
        ln = rng.uniform(50, 124)
        wob = 2.5 * G.fnoise(rng, (S, 1), beta=2.4)[:, 0][np.clip(Y.astype(int), 0, S - 1)]
        lat = np.exp(-((X - cx - wob) / w) ** 2)
        m = np.maximum(m, lat * (1 - np.clip(Y / ln, 0, 1)) ** 0.8)
    # A broad soft damp band along the top, where the wall meets the sweating ceiling.
    band_ = np.exp(-Y / 26) * (0.55 + 0.25 * G.blur(G.fnoise(rng, (1, S), beta=2.6), 2.0)[0])
    m = np.clip(G.blur(np.maximum(m, band_), 1.4), 0, 1) * _side_fade(10) * top_strength
    img = img * (1 - 0.34 * m[..., None] * np.array([1.0, 0.99, 0.95]))
    lines = np.clip(G.fnoise(rng, beta=1.8, aniso=(1, 14), kmin=2), 0, None)            # runs inside it
    img = G.add_lum(img, -6 * lines * m)
    edge = G.blur(np.clip(G.smoothstep(0.08, 0.16, m) - G.smoothstep(0.16, 0.3, m), 0, 1), 0.8)
    img = G.add_lum(img, -7 * edge, (1.0, 0.92, 0.78))                                  # tide line
    img = _crust(img, rng, G.blur(np.clip(G.smoothstep(0.02, 0.07, m) - G.smoothstep(0.07, 0.14, m), 0, 1), 0.8), 0.32)
    mould = (rng.random((S, S)) < 0.006) * G.smoothstep(0.65, 0.9, m) * (Y < 34)
    return G.over(img, np.array([34, 38, 32]), G.blur(mould.astype(float), 0.6) * 0.8)


def _spall(img, rng, near_bottom=False) -> np.ndarray:
    """Spalled concrete: a chunk broken out where the rebar inside rusted and swelled. A rough
    recess of exposed aggregate (its upper inside wall in shadow, the lower one catching the
    light), the rusty bar showing across it, rust stain below, hairline cracks running out."""
    X, Y = G.grid()
    cx = rng.uniform(32, 96)
    cy = rng.uniform(88, 104) if near_bottom else rng.uniform(34, 92)
    rx, ry = rng.uniform(16, 26), rng.uniform(10, 18)
    d = np.hypot((X - cx) / rx, (Y - cy) / ry) + 0.34 * G.blur(G.fnoise(rng, beta=1.6, kmin=2), 0.8)
    hole = G.smoothstep(1.0, 0.9, d)
    rough = _aggregate(rng, hole, density=0.09, size=(0.7, 2.0))
    rough = rough * (0.72 + 0.1 * np.clip((Y - cy) / ry, -1, 1))[..., None]
    img = G.lerp(img, rough, hole[..., None])
    rim = np.clip(G.smoothstep(1.12, 0.98, d) - hole, 0, 1)
    img = G.add_lum(img, -22 * rim * (Y < cy) + 12 * rim * (Y > cy))
    img = G.add_lum(img, -26 * hole * G.smoothstep(0.75, 0.98, d) * (Y < cy))         # shadowed upper wall
    bar_y = cy + rng.uniform(-3, 3)
    bar = np.exp(-((Y - bar_y) / 1.6) ** 2) * G.smoothstep(0.95, 0.75, d)
    steel = G.add_lum(_fill((108, 58, 32)), 18 * np.clip(-(Y - bar_y) / 1.6, -1, 1) + 10 * G.fnoise(rng, beta=1.4))
    img = G.lerp(img, steel, np.clip(bar * 1.4, 0, 1)[..., None])
    stain = _runs(rng, cx, cy + ry * 0.6, rng.uniform(26, 48), spread=rx * 0.35, n=(2, 5)) * (1 - hole)
    img = G.over(img, np.array(RUST), np.clip(stain * 0.45 * _edge_fade(), 0, 1))
    img = G.over(img, np.array(RUST_DARK), np.clip(G.blur(hole, 3.0) - hole, 0, 1) * 0.35)
    return G.mark_crack(img, rng, 26, (24, 44), 2)


def wall_marks(i, rng, img):
    """pipe_wall versions: 1 rust from a bracket stump, 2 condensation from the ceiling (the
    top wall row only, see block/Level2SurfaceBlock), 3 spalled concrete with rusty rebar."""
    if i == 1:
        out = _bracket_rust(img, rng)
    elif i == 2:
        out = _condensation(img, rng)
    else:
        out = _spall(img, rng)
    return out   # unscaled: outside the mark it must stay identical to the plain wall it meets


def _tide_lines(img, rng) -> np.ndarray:
    """Standing water was here: the bottom of the wall is wet-dark up to a wavy line, with two
    older tide lines above it (each a brown line with white salts just above), black mould and
    the green-black of algae right at the floor."""
    X, Y = G.grid()
    out = img
    levels = sorted(rng.uniform(54, 76, 3), reverse=True)
    soft = 0.88 + 0.12 * G.blur(G.fnoise(rng, beta=3.0), 2.0)
    for k, lv in enumerate((levels[0], levels[1] - 14, levels[2] - 28)):
        line = lv + 2.2 * G.blur(G.fnoise(rng, (1, S), beta=2.0), 1.2)[0]
        below = G.smoothstep(line - 2, line + 3, Y)
        dark = 0.30 if k == 0 else 0.07
        out = out * (1 - dark * below[..., None] * soft[..., None])
        out = G.add_lum(out, -(14 - 4 * k) * np.exp(-((Y - line) / 0.9) ** 2), (1.0, 0.9, 0.74))
        out = _crust(out, rng, np.exp(-((Y - line + 2.5) / 1.6) ** 2), 0.55 - 0.12 * k)
    # wicking: wet fingers rising above the waterline where the concrete soaks it up
    wick = np.clip(G.blur(G.fnoise(rng, beta=2.4, aniso=(1, 4), kmin=3), 1.0), 0, None) * G.smoothstep(levels[0] - 26, levels[0], Y)
    out = out * (1 - 0.12 * np.clip(wick, 0, 1)[..., None])
    algae = G.smoothstep(S - 22, S - 4, Y) * G.smoothstep(0.0, 1.2, G.blur(G.fnoise(rng, beta=2.6), 1.5))
    out = G.over(out, np.array([46, 54, 40]), algae * 0.45)
    mould = (rng.random((S, S)) < 0.004) * G.smoothstep(levels[0] + 4, levels[0] + 30, Y)
    return G.over(out, np.array([30, 32, 28]), G.blur(mould.astype(float), 0.6) * 0.7)


def _chipped_kicker(img, rng) -> np.ndarray:
    """The bottom edge knocked about by carts and tools: chunks broken off along the kicker,
    aggregate showing, gouges, black rubber scuffs."""
    X, Y = G.grid()
    # How far up the broken corner reaches at each x (0 where the edge is still whole).
    depth = 12 * np.clip(G.blur(G.fnoise(rng, (1, S), beta=1.4, kmin=3), 1.0)[0] - 0.3, 0, None) ** 1.2
    top = S - depth
    chip = G.smoothstep(top - 0.5, top + 0.5, Y) * (depth > 1.0) * _side_fade(8)
    rough = _aggregate(rng, chip, 0.12, (0.6, 1.8)) * 0.8
    img = G.lerp(img, rough, chip[..., None])
    img = G.add_lum(img, -24 * np.exp(-((Y - top) / 0.9) ** 2) * (depth > 1.0) * _side_fade(8))   # broken lip
    for _ in range(3):
        cx, cy = rng.uniform(20, 108), rng.uniform(70, 110)
        ln, a = rng.uniform(5, 14), rng.uniform(-0.3, 0.3)
        u = (X - cx) * np.cos(a) + (Y - cy) * np.sin(a)
        v = -(X - cx) * np.sin(a) + (Y - cy) * np.cos(a)
        g = np.exp(-(u / ln) ** 6 - (v / 0.7) ** 2)
        img = G.add_lum(img, -14 * g + 7 * np.exp(-(u / ln) ** 6 - ((v - 1.2) / 0.6) ** 2))   # gouge
    return G.mark_scuffs(img, rng, (30, 28, 28), 5, (0.6, 0.86), 0.32)


def wall_base_marks(i, rng, img):
    """l2_wall_base versions: 1 rust run-off down to the floor, 2 tide lines (damp stretches),
    3 a chipped kicker."""
    if i == 1:
        X, Y = G.grid()
        cx = rng.uniform(34, 94)
        runs = _runs(rng, cx, 0, 150, spread=4, n=(3, 6))
        out = G.lerp(img, np.array([128, 74, 42]), (np.clip(runs, 0, 1) * 0.5 * _side_fade())[..., None])
        fan = _local(cx, S + 4, 18, 14) * (0.7 + 0.3 * G.fnoise(rng, beta=1.4))
        out = G.over(out, np.array(RUST_DARK), np.clip(fan * 0.6, 0, 0.6) * _side_fade())
    elif i == 2:
        out = _tide_lines(img, rng)
    else:
        out = _chipped_kicker(img, rng)
    return out


# ---- floor -------------------------------------------------------------------

def _floor_base(rng) -> np.ndarray:
    """Worn power-floated concrete: faint trowel swirls, grit and exposed aggregate, dirt packed
    into every pore, fine scratches along the corridor (u) from boots and carts, paler polished
    patches, black scuffs, a hairline crack."""
    X, Y = G.grid()
    img = G._smooth_concrete(rng, FLOOR, mottle=4.6, fine=1.3)
    ang = np.arctan2(Y - 40, X - 50)
    swirl = np.sin(ang * 3 + np.hypot(X - 50, Y - 40) / 9 + 2 * G.fnoise(rng, beta=2.6))
    img = G.add_lum(img, 1.6 * swirl * G.smoothstep(0.2, 1.2, G.fnoise(rng, beta=2.4)))
    grit = (rng.random((S, S)) < 0.035) * rng.normal(0, 1, (S, S))
    img = G.add_lum(img, 14 * G.blur(grit, 0.45), (1.0, 0.97, 0.92))
    dust = G.smoothstep(0.3, 1.6, G.fnoise(rng, beta=2.2))
    img = G.over(img, np.array([146, 140, 128]), dust * 0.16)
    scratches = G.smoothstep(1.4, 2.6, G.fnoise(rng, beta=0.9, aniso=(1, 30), kmin=6))
    img = G.add_lum(img, 7 * scratches - 3 * np.roll(scratches, 1, axis=0))
    polish = G.smoothstep(0.4, 1.6, G.blur(G.fnoise(rng, beta=2.4, aniso=(1, 2.5), kmin=1), 2.0))
    img = G.add_lum(img, 5 * polish)
    img = G.lerp(img, G.blur(img, 0.8), (polish * 0.5)[..., None])                       # smoothed by wear
    scuff = np.zeros((S, S))
    for _ in range(4):
        cx, cy = rng.uniform(0, S, 2)
        a = rng.uniform(-0.5, 0.5) + (np.pi / 2 if rng.random() < 0.25 else 0)
        u = G.wrapd(X - cx, S) * np.cos(a) + G.wrapd(Y - cy, S) * np.sin(a)
        v = -G.wrapd(X - cx, S) * np.sin(a) + G.wrapd(Y - cy, S) * np.cos(a)
        bend = v - 0.01 * u ** 2
        scuff = np.maximum(scuff, np.exp(-(u / rng.uniform(8, 18)) ** 4 - (bend / 1.0) ** 2) * (0.6 + 0.4 * G.fnoise(rng, beta=0.8)))
    img = G.over(img, np.array([44, 42, 40]), np.clip(scuff * 0.34, 0, 0.45))
    crack = G.smoothstep(0.03, 0.0, np.abs(G.fnoise(rng, beta=2.6, kmin=2)))
    crack *= G.smoothstep(0.7, 1.5, G.fnoise(rng, beta=3.0))
    img = G.add_lum(img, -30 * crack)
    img = G._pores(img, rng, density=0.006, depth=10)
    return _bugholes(img, rng, 10, (0.5, 1.2))


def floor() -> np.ndarray:
    return _keep_mean(_floor_base(G.rng_for(2310)), FLOOR_L)


def _oil(img, rng) -> np.ndarray:
    """A dark oil stain: soaked-in brown-black middle, a darker tide line, drips trailing off
    along the corridor where something leaked as it was wheeled through."""
    X, Y = G.grid()
    cx, cy = G._spot(rng, 26, 20)
    m = _local(cx, cy, rng.uniform(14, 22), rng.uniform(11, 16)) * (1 + 0.35 * G.fnoise(rng, beta=2.6))
    m += 0.6 * _local(cx + rng.uniform(-24, 24), cy + rng.uniform(-14, 14), 5, 4)
    for k in range(4):
        m += 0.5 * _local(cx + 22 + 9 * k + rng.normal(0, 2), cy + rng.normal(0, 1.5), 2.2, 1.8)
    inner = G.smoothstep(0.3, 0.7, m)
    rim = np.clip(G.smoothstep(0.22, 0.3, m) - G.smoothstep(0.3, 0.42, m), 0, 1)
    return G.over(img, np.array([42, 36, 30]), (0.6 * inner + 0.35 * rim) * _edge_fade())


def _floor_spall(img, rng) -> np.ndarray:
    """A crack with a chipped-out spall along it (rough, darker, the aggregate showing)."""
    img = G.mark_crack(img, rng, 34, (40, 70), 2)
    X, Y = G.grid()
    cx, cy = G._spot(rng, 12, 10)
    edge = np.hypot((X - cx) / 9, (Y - cy) / 6) + 0.25 * G.fnoise(rng, beta=1.4)
    m = G.smoothstep(1.0, 0.85, edge)
    rough = _aggregate(rng, m, 0.12, (0.6, 1.6)) * 0.85
    img = G.lerp(img, rough, m[..., None])
    return G.add_lum(img, -14 * np.clip(G.smoothstep(1.1, 0.95, edge) - m, 0, 1))


def _wet_patch(img, rng) -> np.ndarray:
    """Where a leak drips: a wet-dark patch of floor (the light makes it shine, see
    client/light/Level2Gloss), a shallow puddle in the low middle with its silt smoothed flat,
    and round it the pale ring of silt and salts left as earlier puddles dried."""
    X, Y = G.grid()
    cx, cy = 64 + rng.uniform(-10, 10), 64 + rng.uniform(-10, 10)
    m = _local(cx, cy, rng.uniform(26, 34), rng.uniform(20, 28)) * (1 + 0.22 * G.blur(G.fnoise(rng, beta=3.0), 1.5))
    m = m * _edge_fade(18)
    wet = G.smoothstep(0.28, 0.55, m)
    out = img * (1 - 0.36 * wet[..., None] * np.array([1.0, 0.99, 0.96]))
    puddle = G.smoothstep(0.7, 0.82, m)
    calm = G.lerp(out, G.blur(out, 1.6), 0.6) * 0.8
    out = G.lerp(out, calm, puddle[..., None])
    ring = np.clip(G.smoothstep(0.15, 0.22, m) - G.smoothstep(0.22, 0.3, m), 0, 1)
    out = G.over(out, np.array([150, 142, 126]), ring * 0.4)                                 # dried silt
    out = G.add_lum(out, -8 * np.clip(G.smoothstep(0.24, 0.28, m) - G.smoothstep(0.28, 0.34, m), 0, 1))
    return G.over(out, np.array([92, 64, 40]), puddle * 0.12)                                # rust in the water


def floor_marks(i, rng, img):
    """pipe_floor versions: 1 an oil stain, 2 a spalled crack, 3 a wet patch (damp stretches)."""
    out = _oil(img, rng) if i == 1 else _floor_spall(img, rng) if i == 2 else _wet_patch(img, rng)
    return out


def floor_line() -> np.ndarray:
    """The corridor floor with a worn yellow lane line along its north edge (top of the texture;
    the block turns it to the wall side). Its inner half is the walking line: boots and carts
    have scuffed it darker and polished it smoother along the corridor."""
    rng = G.rng_for(2311)
    img = _floor_base(G.rng_for(2310))
    X, Y = G.grid()
    walk = G.smoothstep(40, 96, Y)
    tread = G.smoothstep(0.6, 2.0, G.fnoise(rng, beta=1.0, aniso=(1, 26), kmin=4))
    img = G.over(img, np.array([58, 54, 50]), np.clip(tread * walk * 0.35, 0, 0.4))
    img = G.lerp(img, G.blur(img, 0.9), (walk * 0.4)[..., None])
    top = 4 + 0.35 * G.fnoise(rng, (1, S), beta=2.6)[0]
    line = G.smoothstep(top - 0.6, top + 0.6, Y) * G.smoothstep(18.6, 17.4, Y)
    paint = G.add_lum(_fill((176, 140, 44)), 4 * G.fnoise(rng, beta=1.6), (1.0, 0.95, 0.7))
    worn = _wear(rng, 0.42, 1.3) * (0.82 + 0.18 * G.fnoise(rng, beta=1.0))
    img = G.lerp(img, paint * (img / np.array(FLOOR)).mean(axis=2, keepdims=True) ** 0.6, (line * np.clip(worn, 0, 1))[..., None])
    grime = G.smoothstep(0.4, 1.6, G.fnoise(rng, beta=2.0, aniso=(4, 1)))
    img = G.over(img, np.array([86, 80, 70]), line * grime * 0.35)
    return _keep_mean(img, 120.0)


# ---- ceiling -----------------------------------------------------------------

def _ceiling_base(rng) -> np.ndarray:
    """The dark underside of the slab: board-formed (the marks of 10 cm planks along x), soot and
    dust, a few pale lime spots, a hairline crack."""
    X, Y = G.grid()
    img = G._smooth_concrete(rng, CEILING_SLAB, mottle=3.4, fine=1.0)
    board = np.floor(Y / 16)
    img = G.add_lum(img, (np.sin(board * 12.9898) * 43758.5453 % 1 - 0.5) * 5)
    img = G.add_lum(img, 2.4 * G.fnoise(rng, beta=1.0, aniso=(14, 1), kmin=4))
    img = G.add_lum(img, -7 * np.exp(-((G.wrapd(Y, 16)) / 0.7) ** 2) + 3 * np.exp(-((G.wrapd(Y - 1.4, 16)) / 0.7) ** 2))
    soot = G.smoothstep(0.0, 1.5, G.fnoise(rng, beta=2.4))
    img = G.over(img, np.array([44, 42, 40]), soot * 0.35)
    lime = G.smoothstep(1.3, 2.3, G.blur(G.fnoise(rng, beta=2.4, aniso=(1, 3)), 1.2))
    img = G.over(img, np.array([132, 128, 120]), lime * 0.3)
    img = G._pores(img, rng, density=0.004, depth=6)
    return G.mark_crack(img, rng, 16, (30, 50), 1)


def ceiling() -> np.ndarray:
    return _keep_mean(_ceiling_base(G.rng_for(2320)), CEILING_L)


def _ceiling_damp(img, rng) -> np.ndarray:
    """Water coming through the slab: a dark damp bloom with tide rings, white salts crusted
    along the rings and little calcite straws (pale dots with a wet dark tip) where it drips."""
    X, Y = G.grid()
    cx, cy = 64 + rng.uniform(-12, 12), 64 + rng.uniform(-12, 12)
    m = _local(cx, cy, rng.uniform(26, 34), rng.uniform(22, 30)) * (1 + 0.35 * G.fnoise(rng, beta=2.2))
    m = m * _edge_fade(16)
    out = img * (1 - 0.35 * G.smoothstep(0.3, 0.7, m)[..., None])
    for lv in (0.22, 0.38):
        ring = np.clip(G.smoothstep(lv - 0.05, lv, m) - G.smoothstep(lv, lv + 0.06, m), 0, 1)
        out = G.add_lum(out, -8 * ring, (1.0, 0.9, 0.75))
        out = _crust(out, rng, G.blur(np.clip(G.smoothstep(lv - 0.1, lv - 0.05, m) - G.smoothstep(lv - 0.05, lv, m), 0, 1), 0.8), 0.4)
    for _ in range(int(rng.integers(5, 10))):
        a, r = rng.uniform(0, 2 * np.pi), rng.uniform(0, 14)
        sx, sy = cx + r * np.cos(a), cy + r * np.sin(a)
        d = np.hypot(X - sx, Y - sy)
        out = G.over(out, np.array([196, 192, 182]), G.smoothstep(2.0, 1.2, d) * 0.85)
        out = G.add_lum(out, -30 * G.smoothstep(0.9, 0.3, d))
    return out


def _ceiling_rust(img, rng) -> np.ndarray:
    """An anchor bolt that once held a pipe hanger: a rusty nut in a halo of rust soaked into the
    slab, rust spread along the board grain, rust drip spots around it."""
    X, Y = G.grid()
    cx, cy = G._spot(rng, 20, 16)
    halo = _local(cx, cy, 13, 10) + 0.7 * _local(cx + rng.uniform(-16, 16), cy, 22, 4)
    halo = halo * (0.75 + 0.25 * G.blur(G.fnoise(rng, beta=2.2), 1.0))
    out = G.over(img, np.array([104, 58, 32]), np.clip(halo * 0.7, 0, 0.65))
    out = _hex_nut(out, cx, cy, 3.6, (90, 56, 36))
    for _ in range(int(rng.integers(4, 8))):
        sx, sy = cx + rng.normal(0, 10), cy + rng.normal(0, 7)
        out = G.over(out, np.array(RUST_DARK), G.smoothstep(1.6, 0.6, np.hypot(X - sx, Y - sy)) * 0.7)
    return out


def ceiling_marks(i, rng, img):
    """pipe_ceiling versions: 1 a damp bloom with salts (damp stretches), 2 a rusty anchor."""
    out = _ceiling_damp(img, rng) if i == 1 else _ceiling_rust(img, rng)
    return out


# =============================================================================
# Metal. u = along the pipe (tiles), v = around it (0 top, 64 side, 128 underside).
# =============================================================================

def _cold_beads(img, rng, density=0.010) -> np.ndarray:
    """Condensation on a cold pipe, thickest on the underside where the drops gather."""
    _, Y = G.grid()
    dark, bright = _beads(rng, G.smoothstep(40, 110, Y) * 0.9 + 0.1, density)
    img = G.add_lum(img, -14 * dark)
    return G.add_lum(img, 70 * bright, (0.95, 0.98, 1.0))


def steel(rng, beads=True) -> np.ndarray:
    """Old dark steel pipe: grey-black mill scale and worn black paint; brushed streaks and
    bright worn bands along it (this value contrast is what makes it read as metal), rust as
    pitted patches and as streaks running round to the underside, a few bright scrapes, and
    beads of condensation on the cold pipe."""
    img = G.add_lum(_fill((80, 79, 77)), 6 * G.fnoise(rng, beta=2.4) + 2 * G.fnoise(rng, beta=1.2, kmin=6))
    scale = G.smoothstep(-0.3, 1.0, G.blur(G.fnoise(rng, beta=2.2, kmin=2), 0.8))
    img = G.over(img, np.array([46, 45, 45]), scale * 0.6)
    brushed = G.fnoise(rng, beta=0.8, aniso=(1, 28), kmin=6)
    img = G.add_lum(img, 5 * brushed)
    worn = G.smoothstep(0.5, 1.6, _along(rng, 1.8, 6))
    img = G.over(img, np.array([126, 124, 120]), worn * 0.35)
    patch = G.smoothstep(0.7, 1.4, G.blur(G.fnoise(rng, beta=2.0, kmin=2), 1.0))
    rust = G.add_lum(_fill((118, 62, 34)), 12 * G.fnoise(rng, beta=1.4), (1.0, 0.8, 0.6))
    img = G.lerp(img, rust, (patch * 0.85)[..., None])
    pits = G.blur((rng.random((S, S)) < 0.03) * rng.uniform(0.5, 1, (S, S)), 0.5) * (0.3 + patch)
    img = G.add_lum(img, -pits * 48)
    streak = np.clip(_across(rng, 1.3, 16), 0, None) * G.smoothstep(0.2, 1.0, G.fnoise(rng, beta=2.4))
    img = G.over(img, np.array([108, 56, 30]), np.clip(streak * 0.45, 0, 0.6))
    scrape = G.smoothstep(1.8, 2.6, _along(rng, 1.4, 10))
    img = G.over(img, np.array([150, 148, 144]), scrape * 0.55)
    if beads:
        img = _cold_beads(img, rng)
    return G.add_lum(img, rng.normal(0, 1.6, (S, S)))


def copper(rng) -> np.ndarray:
    """Aged copper: orange-brown, brushed lengthwise, darker tarnish clouds and hand-darkened
    bands, a few bright scuffs (the green patina is only at the joints)."""
    img = _fill((186, 108, 62))
    img = G.add_lum(img, 6 * G.fnoise(rng, beta=2.6), (1.0, 0.75, 0.55))
    img = G.add_lum(img, 6.0 * _along(rng, 1.0, 20), (1.0, 0.8, 0.6))
    tarnish = G.smoothstep(-0.4, 1.2, G.blur(G.fnoise(rng, beta=2.4, kmin=2), 1.0))
    img = G.over(img, np.array([112, 62, 38]), tarnish * 0.45)
    bright = G.smoothstep(1.4, 2.4, _along(rng, 1.6, 6))
    img = G.over(img, np.array([226, 152, 100]), bright * 0.5)
    spots = G.smoothstep(1.8, 2.6, G.fnoise(rng, beta=1.2, kmin=6))
    img = G.over(img, np.array([70, 100, 84]), spots * 0.35)                               # first verdigris
    return G.add_lum(img, rng.normal(0, 1.2, (S, S)))


def patina(rng) -> np.ndarray:
    """Heavy verdigris at a soldered joint: crusty green-blue over dark copper, white-green bloom."""
    img = copper(rng)
    crust = G.smoothstep(-0.5, 0.6, G.blur(G.fnoise(rng, beta=2.4, kmin=2), 1.2))
    green = G.add_lum(_fill((82, 134, 106)), 9 * G.blur(G.fnoise(rng, beta=1.4), 0.8), (0.8, 1.0, 0.95))
    img = G.lerp(img, green, (crust * 0.88)[..., None])
    img = G.add_lum(img, -14 * np.clip(G.smoothstep(0.0, 0.25, crust) - G.smoothstep(0.25, 0.6, crust), 0, 1))
    bloom = G.smoothstep(1.0, 2.0, G.blur(G.fnoise(rng, beta=1.6, kmin=4), 1.0)) * crust
    img = G.over(img, np.array([168, 194, 174]), bloom * 0.55)
    return G.add_lum(img, rng.normal(0, 1.6, (S, S)))


def cladding(rng) -> np.ndarray:
    """Off-white insulation jacket (painted canvas over lagging): fine weave, a lap seam along
    the pipe, dirt, brown water tide marks where condensation soaked it, soft dents."""
    X, Y = G.grid()
    img = _fill((192, 186, 170))
    weave = np.sin(X * 2.4) * np.sin(Y * 2.4)
    img = G.add_lum(img, 2.0 * weave + 2.2 * G.fnoise(rng, beta=2.6))
    img = G.add_lum(img, 5 * G.blur(G.fnoise(rng, beta=3.0), 3.0))
    seam = np.abs(Y - 96.5)
    img = G.add_lum(img, -16 * np.exp(-(seam / 0.8) ** 2) + 7 * np.exp(-((Y - 94.5) / 1.2) ** 2))
    lap = np.abs(G.wrapd(X - 1.0, S))
    img = G.add_lum(img, -20 * np.exp(-(lap / 0.9) ** 2) + 8 * np.exp(-((G.wrapd(X - 3.2, S)) / 1.2) ** 2))
    dirt = G.smoothstep(0.0, 1.6, G.fnoise(rng, beta=2.2))
    img = G.over(img, np.array([142, 132, 112]), dirt * 0.32)
    tide = G.fnoise(rng, beta=2.8)
    ring = np.clip(G.smoothstep(0.6, 0.75, tide) - G.smoothstep(0.75, 1.1, tide), 0, 1)
    img = G.over(img, np.array([150, 118, 70]), ring * 0.45 + G.smoothstep(0.75, 1.4, tide) * 0.14)
    sag = G.smoothstep(80, 128, Y) * G.smoothstep(0.2, 1.4, G.fnoise(rng, beta=2.0, aniso=(1, 3)))
    img = G.over(img, np.array([132, 112, 80]), sag * 0.3)                                # wet underside
    return G.add_lum(img, rng.normal(0, 1.4, (S, S)))


def band(rng) -> np.ndarray:
    """Aluminium strap band: dull grey metal, a crimp seal, a little dirt."""
    X, Y = G.grid()
    img = G._galvanised(rng, (164, 166, 166))
    img = G.add_lum(img, 18 * np.exp(-((G.wrapd(Y - 20, 64)) / 3) ** 2) - 8 * np.exp(-((G.wrapd(Y - 26, 64)) / 2) ** 2))
    return img


def torn(rng) -> np.ndarray:
    """A torn patch of the jacket: ragged canvas edge, the yellow fibreglass lagging beneath,
    a flap of foil."""
    X, Y = G.grid()
    img = cladding(rng)
    hole = G.smoothstep(0.95, 0.8, np.hypot((X - 60) / 34, (Y - 64) / 26) + 0.18 * G.blur(G.fnoise(rng, beta=1.4, kmin=3), 0.8))
    fibre = G.add_lum(_fill((206, 172, 92)), 9 * G.blur(G.fnoise(rng, beta=1.0, aniso=(1, 5), kmin=3), 0.7), (1.0, 0.9, 0.6))
    fibre = G.add_lum(fibre, -30 * G.smoothstep(0.2, 1.6, G.blur(G.fnoise(rng, beta=2.4), 1.5)), (1.0, 0.95, 0.9))
    fibre = G.add_lum(fibre, -40 * G.smoothstep(0.8, 0.95, np.hypot((X - 60) / 34, (Y - 64) / 26)))
    img = G.lerp(img, fibre, hole[..., None])
    edge = np.clip(G.smoothstep(0.0, 0.25, hole) - G.smoothstep(0.25, 0.6, hole), 0, 1)
    img = G.add_lum(img, 22 * edge - 18 * G.blur(edge, 1.5) * (1 - hole))
    foil = G.smoothstep(0.92, 0.8, np.hypot((X - 92) / 10, (Y - 46) / 14) + 0.3 * G.fnoise(rng, beta=1.0))
    return G.over(img, np.array([186, 186, 190]) + 20 * G.fnoise(rng, beta=0.5)[..., None], foil * 0.9)


# Paint coding of the service pipes (the owner's brief): green water, red steam/fire, grey return,
# yellow gas. The block's enum keeps the old names (PAINTED_BLUE is the grey return line) so
# saved worlds keep their pipes.
PAINT = {
    "red": (138, 40, 34),
    "blue": (96, 100, 104),     # slate grey: the return line
    "yellow": (176, 142, 42),
    "green": (50, 98, 62),
}
LABEL = {   # (word, letter colour) on the pipe's colour, ASME style
    "red": ("STEAM", (236, 232, 222)),
    "blue": ("RETURN", (236, 232, 222)),
    "yellow": ("GAS", (24, 22, 20)),
    "green": ("H2O", (236, 232, 222)),
}
COLD = {"blue", "green"}       # cold lines: they sweat


def painted(rng, colour) -> np.ndarray:
    """Enamel-painted service pipe: a soft orange-peel gloss, chips to dark steel with a bright
    rim and a rust halo, grime collected on the underside, paint sags, and beads of condensation
    on the cold lines."""
    img = G.add_lum(_fill(PAINT[colour]), 4 * G.fnoise(rng, beta=2.4))
    img = G.add_lum(img, 2.0 * _along(rng, 1.6, 12) + 1.4 * G.fnoise(rng, beta=0.4, kmin=20))
    img = _chips(img, rng, 0.3, 1.2, (58, 46, 40))
    _, Y = G.grid()
    grime = G.smoothstep(-0.2, 1.4, G.fnoise(rng, beta=2.4)) * (0.5 + 0.5 * G.smoothstep(40, 120, Y))
    img = G.over(img, np.array([58, 52, 46]), grime * 0.26)
    sags = np.clip(_across(rng, 1.4, 12), 0, None)
    img = G.add_lum(img, 6 * G.smoothstep(1.2, 2.0, sags))
    if colour in COLD:
        img = _cold_beads(img, rng, 0.008)
    return G.add_lum(img, rng.normal(0, 1.4, (S, S)))


def _stencil_text(text, cx, cy, px, maxw=118.0, gap=0.9, font_px=None) -> np.ndarray:
    """Spray-stencil lettering: a condensed bold face (Anton) with the thin uncut "bridges" a
    stencil leaves (vertical gaps through the bowls of the letters), centred at (cx, cy)."""
    from PIL import ImageFont
    f_path = str(G.Path(__file__).resolve().parents[1] / "fonts" / "Anton-Regular.ttf")
    probe = G.ImageDraw.Draw(G.Image.new("L", (1, 1)))
    while px > 6 and probe.textlength(text, font=ImageFont.truetype(f_path, px)) > maxw:
        px -= 1
    m = G.pil_mask(lambda d, k: d.text((cx * k, cy * k), text, fill=255,
                                       font=ImageFont.truetype(f_path, px * k), anchor="mm"))
    X, Y = G.grid()
    cols = m.sum(axis=0)
    cut = np.zeros(S)
    inside = cols > 0.5
    start = None
    for x in range(S + 1):
        on = x < S and inside[x]
        if on and start is None:
            start = x
        elif not on and start is not None:
            if x - start > px * 0.34:
                mid = (start + x) / 2
                cut += np.exp(-((np.arange(S) + 0.5 - mid) / gap) ** 2)
            start = None
    bridge = np.clip(cut, 0, 1)[None, :] * G.smoothstep(px * 0.18, px * 0.05, np.abs(Y - cy))
    return np.clip(m * (1 - bridge), 0, 1)


def _spray(rng, mask, overspray=1.0) -> np.ndarray:
    """Stencil paint coverage: slightly patchy, with a soft halo of overspray round the edges."""
    patchy = np.clip(0.88 + 0.12 * G.fnoise(rng, beta=1.0), 0, 1)
    halo = G.blur(mask, 1.6) * 0.22 * overspray
    return np.clip(np.maximum(mask * patchy, halo), 0, 1)


def painted_label(rng, colour) -> np.ndarray:
    """The label band of a painted pipe (u along the pipe): the pipe colour with white edge
    bands, the stencilled word and a flow arrow pointing to +u, grubby and chipped."""
    X, Y = G.grid()
    img = painted(rng, colour)
    word, ink = LABEL[colour]
    ink = np.array(ink, float)
    bands = G.smoothstep(1.0, 0.0, np.abs(X - 10) - 4) + G.smoothstep(1.0, 0.0, np.abs(X - 118) - 4)
    img = G.over(img, np.array([226, 222, 210]), np.clip(bands, 0, 1) * 0.95)
    txt = _stencil_text(word, 56, 64, 52, maxw=74)
    img = G.over(img, ink, _spray(rng, txt) * 0.95)
    arrow = G.supersample((S, S), lambda x, y: ((np.abs(y - 64) < 5) & (x > 88) & (x < 100))
                          | ((x >= 100) & (x < 110) & (np.abs(y - 64) < (110 - x) * 1.1)))
    img = G.over(img, ink, _spray(rng, arrow) * 0.95)
    img = G.over(img, np.array([60, 54, 48]), G.smoothstep(0.0, 1.6, G.fnoise(rng, beta=2.4)) * 0.18)
    return img


def conduit(rng) -> np.ndarray:
    """Galvanised EMT conduit: bright zinc with spangle, a lengthwise sheen, grime and white rust."""
    img = G._galvanised(rng, (150, 152, 152))
    img = G.add_lum(img, 5 * _along(rng, 1.0, 16))
    white = G.smoothstep(1.4, 2.2, G.fnoise(rng, beta=1.4, kmin=4))
    img = G.over(img, np.array([196, 196, 190]), white * 0.4)
    return G.over(img, np.array([70, 66, 60]), G.smoothstep(0.4, 1.8, G.fnoise(rng, beta=2.2)) * 0.25)


def strut(rng) -> np.ndarray:
    """Galvanised slotted strut channel (unistrut) for the pipe racks: slots every 16 px along u,
    rust bleeding from the slot edges and running down from them."""
    X, Y = G.grid()
    img = G._galvanised(rng, (128, 130, 130))
    img = G.add_lum(img, 4 * _along(rng, 1.0, 14))
    slot = G.smoothstep(4.5, 3.5, np.abs(G.wrapd(X - 8, 16))) * G.smoothstep(2.2, 1.4, np.abs(G.wrapd(Y - 8, 16)))
    img = G.over(img, np.array([24, 22, 20]), slot)
    img = G.add_lum(img, 12 * np.clip(np.roll(slot, -1, axis=0) - slot, 0, 1))          # lit lower lip
    img = G.over(img, np.array(RUST), G.blur(slot, 1.6) * (1 - slot) * 0.6)
    drip = G.blur(np.roll(slot, 4, axis=0), 1.0) * (1 - slot)
    img = G.over(img, np.array(RUST), np.clip(drip * 0.5, 0, 0.5))
    return G.over(img, np.array(RUST), G.smoothstep(1.2, 2.2, G.fnoise(rng, beta=1.6)) * 0.4)


def bolt_steel(rng) -> np.ndarray:
    """Dark oily steel for bolts, nuts, gauge cases, hubs: bright worn edges, rust in the gaps."""
    img = G._galvanised(rng, (58, 58, 60))
    img = G.add_lum(img, 10 * G.smoothstep(1.0, 2.2, _along(rng, 1.2, 4)))
    return G.over(img, np.array(RUST), G.smoothstep(0.8, 1.8, G.fnoise(rng, beta=1.8)) * 0.45)


def clamp(rng) -> np.ndarray:
    """Plain galvanised pipe clamp / hanger band: light zinc, white rust, a little red rust where
    it bites the pipe."""
    img = G._galvanised(rng, (138, 140, 140))
    img = G.add_lum(img, 6 * _along(rng, 1.0, 10))
    img = G.over(img, np.array([184, 184, 178]), G.smoothstep(1.4, 2.2, G.fnoise(rng, beta=1.4, kmin=4)) * 0.35)
    return G.over(img, np.array(RUST), G.smoothstep(1.0, 2.0, G.fnoise(rng, beta=1.8)) * 0.4)


def flange(rng) -> np.ndarray:
    """A cast steel flange: black paint worn to bright metal on the rim, rust weeping out of the
    joint and running round to the underside (v toward 128), oily grime."""
    _, Y = G.grid()
    img = G.add_lum(_fill((50, 46, 44)), 4 * G.fnoise(rng, beta=2.0))
    rust = G.smoothstep(0.4, 1.6, G.blur(G.fnoise(rng, beta=1.8, kmin=2), 0.7))
    img = G.over(img, np.array([116, 62, 34]), rust * 0.7)
    weep = np.clip(_across(rng, 1.2, 10), 0, None) * G.smoothstep(30, 110, Y)
    img = G.over(img, np.array([126, 66, 32]), np.clip(weep * 0.5, 0, 0.6))
    worn = G.smoothstep(1.2, 2.0, _along(rng, 1.2, 6))
    img = G.over(img, np.array([118, 116, 112]), worn * 0.55)
    return G.add_lum(img, rng.normal(0, 2.0, (S, S)))


def beam(rng) -> np.ndarray:
    """A rolled steel I-beam (u along the beam): grey zinc primer, lighter than the slab and
    flatter than the pipes so the structure reads as its own layer; mill scale showing through
    worn primer, rust along the flange edges (the top and bottom of v) with drips running down
    the web from the top edge, a row of bolt heads near each edge, chalk-white survey marks."""
    X, Y = G.grid()
    img = G._smooth_concrete(rng, (128, 128, 124), mottle=3.0, fine=0.8)
    img = G.add_lum(img, 4 * _along(rng, 1.0, 18))
    scale = 1 - _wear(rng, 0.22, 1.4)
    img = G.over(img, np.array([70, 70, 70]), scale * 0.7)
    edge = np.minimum(Y, S - Y)
    rim = G.smoothstep(14, 2, edge) * (0.6 + 0.4 * G.fnoise(rng, beta=1.4))
    img = G.over(img, np.array(RUST), np.clip(rim * 0.7, 0, 0.75))
    img = G.add_lum(img, 14 * np.exp(-((Y - 1.5) / 1.0) ** 2) - 18 * np.exp(-((Y - S + 1.5) / 1.0) ** 2))
    drips = np.zeros((S, S))
    for _ in range(int(rng.integers(3, 6))):
        drips = np.maximum(drips, _runs(rng, rng.uniform(8, 120), 6, rng.uniform(30, 80), spread=3, n=(1, 3)))
    img = G.over(img, np.array([118, 66, 36]), np.clip(drips * 0.55, 0, 0.6))
    img = _rivets(img, [(x, y) for x in (16, 48, 80, 112) for y in (10, S - 10)], 2.6, (96, 88, 80))
    for x, y in [(x, y) for x in (16, 48, 80, 112) for y in (10, S - 10)]:
        run = np.exp(-((X - x) / 1.2) ** 2) * (Y > y + 2) * np.clip(1 - (Y - y) / 20, 0, 1)
        img = G.over(img, np.array(RUST), run * 0.35)
    chalk = G.box((S, S), 60, 54, 63, 74, soft=0.7) * (0.7 + 0.3 * G.fnoise(rng, beta=0.6))
    return G.over(img, np.array([210, 208, 200]), chalk * 0.6)


def _painted_metal(rng, colour, wear=0.22, grime=0.25, scratches=4) -> np.ndarray:
    """Old enamel on steel: soft mottling, chips to dark steel (a bright rim, a rust halo), worn
    paler edges, grime collecting low down, scratches with a dark groove and a bright edge."""
    X, Y = G.grid()
    img = G.add_lum(_fill(colour), 3.5 * G.fnoise(rng, beta=2.4) + 1.2 * G.fnoise(rng, beta=1.0, kmin=8))
    img = _chips(img, rng, wear, 1.3)
    dirt = G.smoothstep(-0.2, 1.6, G.fnoise(rng, beta=2.2)) * (0.6 + 0.4 * Y / S)
    img = G.over(img, np.array([58, 52, 46]), dirt * grime)
    for _ in range(scratches):
        x0, y0 = rng.uniform(10, S - 10, 2)
        a, ln = rng.uniform(0, np.pi), rng.uniform(6, 18)
        dist = (X - x0) * np.sin(a) - (Y - y0) * np.cos(a)
        along = np.abs((X - x0) * np.cos(a) + (Y - y0) * np.sin(a))
        img = G.add_lum(img, (18 * np.exp(-(dist / 0.45) ** 2) - 8 * np.exp(-((dist - 0.9) / 0.45) ** 2)) * (along < ln / 2))
    return img


def grating(rng) -> np.ndarray:
    """Steel bar grating seen from above (cutout): bearing bars along u every 8 px, cross rods
    every 32 px, the bar tops worn bright where boots go, rust in the joints, and dirt: grime
    packed against the cross rods and a few openings clogged solid with sludge and grit."""
    X, Y = G.grid()
    bars = np.abs(G.wrapd(Y - 4, 8)) < 1.3
    rods = np.abs(G.wrapd(X - 16, 32)) < 1.0
    frame = (X < 3) | (X > S - 3)
    img = G.add_lum(_fill((80, 78, 74)), 10 * G.fnoise(rng, beta=1.4))
    worn = G.smoothstep(-0.2, 1.2, G.blur(G.fnoise(rng, beta=2.2, aniso=(1, 3)), 2.0))
    img = G.add_lum(img, (14 + 18 * worn) * np.exp(-((G.wrapd(Y - 4, 8)) / 0.6) ** 2) * bars)
    img = G.over(img, np.array([118, 64, 34]), G.smoothstep(0.6, 1.8, G.fnoise(rng, beta=1.8)) * 0.5 + 0.35 * (bars & rods))
    # Cells between bars: some clogged with sludge (opaque), the rest open.
    cell_x = np.floor((X - 16) / 32) % 4
    cell_y = np.floor((Y - 4) / 8) % 16
    pick = rng.random((16, 4))
    clog = (pick[cell_y.astype(int), cell_x.astype(int)] < 0.12) & ~bars & ~rods & ~frame
    sludge = G.add_lum(_fill((58, 50, 40)), 10 * G.blur(rng.normal(0, 1, (S, S)), 0.6))
    sludge = G.over(sludge, np.array([96, 84, 66]), G.smoothstep(0.4, 1.6, G.fnoise(rng, beta=1.4)) * 0.5)
    img = np.where(clog[..., None], sludge, img)
    near_rod = (np.abs(G.wrapd(X - 16, 32)) < 3.5) & ~bars & ~rods & (rng.random((S, S)) < 0.55)
    img = np.where(near_rod[..., None], sludge * 0.85, img)
    img = G.over(img, np.array([50, 44, 36]), (bars & ~rods) * G.smoothstep(0.6, 1.8, G.fnoise(rng, beta=2.0)) * 0.4)
    solid = bars | rods | frame | clog | near_rod
    out = np.zeros((S, S, 4))
    out[..., :3] = img
    out[..., 3] = np.where(solid, 255, 0)
    return out


def gutter_channel(rng) -> np.ndarray:
    """The bottom of the drainage channel: wet black-brown sludge, a thin trickle of water with
    highlights running along u, rust stains, pale silt banks at its edges."""
    X, Y = G.grid()
    img = G.add_lum(_fill((44, 40, 34)), 8 * G.fnoise(rng, beta=2.2))
    img = G.over(img, np.array([96, 62, 36]), G.smoothstep(0.4, 1.6, G.fnoise(rng, beta=2.0)) * 0.5)
    centre = 64 + 6 * G.fnoise(rng, (1, S), beta=2.2)[0]
    wet = np.exp(-((Y - centre) / 14) ** 2)
    img = G.add_lum(img, 26 * wet * G.smoothstep(0.6, 1.6, _along(rng, 1.6, 8)), (0.9, 0.95, 1.0))
    bank = np.exp(-((np.abs(Y - centre) - 22) / 3.0) ** 2)
    return G.over(img, np.array([112, 104, 90]), bank * 0.35 * G.smoothstep(0.0, 1.2, G.fnoise(rng, beta=1.6)))


def drip_stain(rng) -> np.ndarray:
    """A wet stain round a leak (cutout decal, used on the ceiling and as the leak puddle): wet
    dark middle, soft brown tide rings with white salts on the outer one, a little rust."""
    X, Y = G.grid()
    m = _local(64, 64, 34, 30) * (1 + 0.25 * G.blur(G.fnoise(rng, beta=3.0), 1.0))
    img = G.lerp(_fill((74, 60, 46)), _fill((30, 28, 26)), G.smoothstep(0.4, 0.9, m)[..., None])
    img = G.add_lum(img, 6 * G.fnoise(rng, beta=1.8))
    for lv, a in ((0.33, 0.5), (0.52, 0.35)):
        ring = np.clip(G.smoothstep(lv - 0.05, lv, m) - G.smoothstep(lv, lv + 0.07, m), 0, 1)
        img = G.over(img, np.array([96, 64, 40]), ring * a)
    salt = np.clip(G.smoothstep(0.27, 0.3, m) - G.smoothstep(0.3, 0.34, m), 0, 1)
    img = _crust(img, rng, salt, 0.7)
    img = G.add_lum(img, 26 * G.smoothstep(0.9, 1.0, m) * G.smoothstep(1.0, 2.0, G.fnoise(rng, beta=1.0)), (0.9, 0.95, 1.0))
    out = np.zeros((S, S, 4))
    out[..., :3] = img
    out[..., 3] = np.where(m > 0.27, 255, 0)
    return out


def water(rng) -> np.ndarray:
    """A water drop: dark clear with a bright highlight."""
    X, Y = G.grid()
    return G.add_lum(_fill((96, 106, 112)), 60 * G.smoothstep(30, 0, np.hypot(X - 46, Y - 40)), (1.0, 1.0, 1.05))


MATERIALS = {
    "steel": steel, "copper": copper, "insulated": cladding, "conduit": conduit,
    **{f"painted_{c}": (lambda rng, c=c: painted(rng, c)) for c in PAINT},
}

# How shiny each pipe surface is (the baked highlight of _round).
SHINE = {"steel": 0.9, "copper": 1.3, "insulated": 0.25, "conduit": 1.0,
         **{f"painted_{c}": 0.6 for c in ("red", "blue", "yellow", "green")}}


def _cap_colour(kind):
    """The flat colour of a pipe's capped end: a little darker than the pipe."""
    base = {"steel": (60, 56, 52), "copper": (128, 76, 46), "insulated": (150, 148, 142), "conduit": (104, 106, 106)}
    if kind in base:
        return base[kind]
    return tuple(int(c * 0.7) for c in PAINT[kind.split("_")[1]])


# =============================================================================
# Writers
# =============================================================================

# Texture budget: every PNG in textures/block is stitched into the one block atlas, which must
# stay at 4096 x 4096. Only big, close-up faces keep 128 px; pipes, fittings and props are drawn
# at 128 and saved at 64.
SMALL = 64


def _save(img, name, size=SMALL) -> None:
    """Save a texture, box-filtered down to size x size (a cutout keeps a strictly 0/255 alpha)."""
    k = img.shape[0] // size
    if k > 1:
        h, w = img.shape[0] // k, img.shape[1] // k
        img = img.reshape(h, k, w, k, img.shape[2]).mean(axis=(1, 3))
        if img.shape[2] == 4:
            img[..., 3] = np.where(img[..., 3] >= 128, 255, 0)
    G.save_png(img, _blk(name))


# The surfaces with aged versions (gen_assets SURFACE_VARIANTS has their counts; block weights in
# ModBlocks / registry/Level2Blocks, see block/Level2SurfaceBlock).
SURFACES = ("pipe_wall", "l2_wall_base", "pipe_floor", "pipe_ceiling")


def tex_level2_materials_surfaces() -> None:
    """The corridor concrete: plain textures, aged versions, and the version blockstates and
    models (copies of each plain model, see gen_assets.write_variant_states)."""
    _save(wall(), "pipe_wall", S)
    _save(wall_base(), "l2_wall_base", S)
    _save(floor(), "pipe_floor", S)
    _save(ceiling(), "pipe_ceiling", S)
    _save(floor_line(), "l2_floor_line", S)
    for name in SURFACES:
        G.save_surface_variants(name)
        G.write_variant_states(name, G.SURFACE_VARIANTS[name][2])


def tex_level2_materials_metals() -> None:
    """Pipe materials (with the cylinder shading baked in), caps, fittings, the I-beam, the floor
    grating, the gutter and the leak stain."""
    for i, (kind, fn) in enumerate(MATERIALS.items()):
        _save(_round(fn(G.rng_for(2330 + i)), SHINE[kind]), f"l2_pipe_{kind}")
        _save(_fill(_cap_colour(kind))[:16, :16], f"l2_pipe_cap_{kind}", 16)
    _save(_round(patina(G.rng_for(2340)), 0.6), "l2_pipe_copper_patina")
    _save(_round(band(G.rng_for(2341)), 0.9), "l2_pipe_band")
    _save(_round(torn(G.rng_for(2342)), 0.25), "l2_pipe_torn")
    for i, c in enumerate(PAINT):
        _save(_round(painted_label(G.rng_for(2343 + i), c), 0.5), f"l2_pipe_label_{c}")
    _save(strut(G.rng_for(2350)), "l2_strut")
    _save(bolt_steel(G.rng_for(2351)), "l2_bolt_steel")
    _save(clamp(G.rng_for(2352)), "l2_clamp")
    _save(_round(flange(G.rng_for(2353)), 0.7), "l2_pipe_flange")
    _save(beam(G.rng_for(2470)), "l2_beam")
    _save(grating(G.rng_for(2429)), "l2_grating", S)
    _save(gutter_channel(G.rng_for(2430)), "l2_gutter")
    _save(drip_stain(G.rng_for(2431)), "l2_drip_stain")
    _save(water(G.rng_for(2432)), "l2_water")


TEXTURES = [tex_level2_materials_surfaces, tex_level2_materials_metals]
