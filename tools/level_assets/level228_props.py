"""Level 228 v2 ("Hall of Mirrors", an abandoned carnival mirror maze): the prop meshes and their
texture sheets, drawn by client/level228/Level228Props (a static prop renderer outside the block
atlas, which is full). Everything is generated here from scratch: geometry from profiles and
cut-outs, textures from noise and height fields, lettering in OFL fonts from tools/fonts.

Writes:
  textures/level228/props_<sheet>.png   2048 x 2048 sheets (crystal, alhambra, funhouse, decor)
  textures/level228/mirror.png          2048 x 2048 mirror-surface tiles (translucent)
  models/level228/props.bin             every prop mesh (format in level228_parts/sheet.py)
  textures/block/l228_*.png             the plain block textures the maze is built from (same
                                        names and sizes as before: the block atlas is full)

The parts live in level228_parts/: geo (meshes), mat (materials), carve (ornament), one module per
zone (crystal, alhambra, funhouse, decor), mirror (mirror sheet), blocks (block textures), preview.

Run:      python tools/gen_assets.py l228_props
Preview:  python tools/level_assets/level228_props.py --preview <dir>   (contact sheets + rooms)
Real:     python tools/level_assets/level228_props.py --real <layout.json> <dir> [shot filters]
          shots of the real floor plan; dump the layout first with the test-source tool
          client/level228/Level228PropDump (java -cp build/classes/java/main;build/classes/java/test
          com.rickr.backrooms.client.level228.Level228PropDump <layout.json> [seed]).
Sizes:    python tools/level_assets/level228_props.py --sizes   (shipped PNG size per sheet against
          its budget, and the costliest regions; nothing written)
Fixed seeds 22800-22999: identical output every run.
"""
from __future__ import annotations

import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE.parent) not in sys.path:
    sys.path.insert(0, str(_HERE.parent))

from level_assets.level228_parts import sheet as SH  # noqa: E402

ROOT = _HERE.parents[1] / "src" / "main" / "resources" / "assets" / "backrooms"
SHEET_DIR = ROOT / "textures" / "level228"
MESH_FILE = ROOT / "models" / "level228" / "props.bin"


def _zones():
    from level_assets.level228_parts import alhambra, crystal, decor, funhouse, halls
    return [crystal, alhambra, funhouse, decor, halls]


def build(zones=None):
    """Regions (painted) and meshes of the given zone modules (all by default)."""
    zs = zones or _zones()
    S = SH.Sheets()
    meshes = []
    for z in zs:
        z.regions(S)
    for z in zs:
        meshes += z.meshes()
    _anchor_ceilings(meshes)
    S.pack()
    S.paint()
    return S, meshes


def _anchor_ceilings(meshes):
    """Ceilings are modelled standing on the floor (their soffit at y = CEIL, next to the columns
    and arches they meet), but the plan anchors a P_CEILING record on the ceiling block itself
    (Level228Plan.CEILING_Y), so shift them down to be local to that block."""
    from level_assets.level228_parts.dims import CEIL
    for m in meshes:
        if m.name.startswith("ceiling_"):
            for q in m.quads:
                q.p = q.p - (0.0, CEIL, 0.0)
            for e in m.emitters:
                e.pos = (e.pos[0], e.pos[1] - CEIL, e.pos[2])


def tex_l228_props() -> None:
    from level_assets.level228_parts import blocks, mirror
    S, meshes = build()
    for s in S.sheets():
        S.save(s, SHEET_DIR / f"props_{s}.png")
    SH.write_meshes(MESH_FILE, meshes, S)
    mirror.save(SHEET_DIR / "mirror.png")
    blocks.save()


TEXTURES = [tex_l228_props]
SOUNDS = []


def _preview(out: Path, only=None):
    from level_assets.level228_parts import mirror, preview, rooms
    zs = _zones()
    if only:
        zs = [z for z in zs if z.__name__.rsplit(".", 1)[-1] in only]
    S, meshes = build(zs)
    imgs = {s: S.compose(s) for s in S.sheets()}
    mir = mirror.shipped()
    imgs["mirror"] = mir
    out.mkdir(parents=True, exist_ok=True)
    for s, img in imgs.items():
        from PIL import Image
        import numpy as np
        Image.fromarray(np.clip(np.round(img * 255), 0, 255).astype(np.uint8), "RGBA").save(out / f"sheet_{s}.png")
    R = preview.Renderer(imgs, lambda name, u, v: mirror.uv(name, u, v) if name.startswith("mir_") else S.uv(name, u, v))

    def sheet_of(name):
        return "mirror" if name.startswith("mir_") else S.regions[name].sheet

    by_zone = {}
    for m in meshes:
        by_zone.setdefault(m.name.split("_")[-1] if m.name.split("_")[-1] in ("crystal", "alhambra", "funhouse", "glass")
                           else "decor", []).append(m)
    for z, ms in by_zone.items():
        preview.contact_sheet(R, ms, sheet_of, out / f"props_{z}.png")
    rooms.render_all(R, {m.name: m for m in meshes}, sheet_of, out)
    print("preview written to", out)


def _real(layout: Path, out: Path, only=None):
    from level_assets.level228_parts import mirror, preview, real
    S, meshes = build()
    imgs = {s: S.compose(s) for s in S.sheets()}
    imgs["mirror"] = mirror.shipped()
    R = preview.Renderer(imgs, lambda name, u, v: mirror.uv(name, u, v) if name.startswith("mir_") else S.uv(name, u, v))

    def sheet_of(name):
        return "mirror" if name.startswith("mir_") else S.regions[name].sheet

    real.render_all(R, {m.name: m for m in meshes}, sheet_of, layout, out, only)
    print("real-layout shots written to", out)


# Shipped PNG budget per sheet (MB): the textures of this level must stay within 12 MB in all.
BUDGET = {"crystal": 1.9, "alhambra": 2.0, "funhouse": 2.2, "decor": 2.85, "halls": 1.7, "mirror": 1.35}


def _png_mb(img) -> float:
    import io
    import numpy as np
    from PIL import Image
    b = io.BytesIO()
    Image.fromarray(np.clip(np.round(img * 255), 0, 255).astype(np.uint8), "RGBA").save(b, "png", optimize=True)
    return b.tell() / 1e6


def _sizes():
    from level_assets.level228_parts import mirror
    S, _ = build()
    total = 0.0
    for s in S.sheets():
        mb = _png_mb(S.compose(s))
        total += mb
        print(f"{s:10s} {mb:5.2f} MB  (budget {BUDGET.get(s, 0):.1f})  {SH.SHEET} x {S.height[s]}")
    mb = _png_mb(mirror.shipped())
    total += mb
    print(f"{'mirror':10s} {mb:5.2f} MB  (budget {BUDGET['mirror']:.1f})")
    print(f"total      {total:5.2f} MB  (budget {sum(BUDGET.values()):.1f})")
    costs = sorted(((_png_mb(r.img), r.name, r.sheet, r.w, r.h) for r in S.regions.values()), reverse=True)
    for c in costs[:25]:
        print(f"  {c[0]:.3f} MB  {c[1]} ({c[2]}, {c[3]} x {c[4]})")


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "--preview":
        _preview(Path(sys.argv[2]), sys.argv[3:] or None)
    elif len(sys.argv) >= 4 and sys.argv[1] == "--real":
        _real(Path(sys.argv[2]), Path(sys.argv[3]), sys.argv[4:] or None)
    elif len(sys.argv) >= 2 and sys.argv[1] == "--sizes":
        _sizes()
    else:
        tex_l228_props()
