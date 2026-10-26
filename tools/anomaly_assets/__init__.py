"""Textures and sounds of the levels' anomalous areas, one module per level (level0.py, ...).

Each module does ``import gen_assets as G`` for the shared helpers and paths (G.TEX, G.SND,
G.save_png, G.save_ogg, ...) and lists its generator functions in TEXTURES and SOUNDS.
gen_assets.py runs them together with its own, so `python tools/gen_assets.py <prefix>` works
for them too.
"""
