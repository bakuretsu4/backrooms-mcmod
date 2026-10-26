"""Textures and sounds of whole levels added after gen_assets.py grew too big, one module per
level (level7.py, ...). Same contract as anomaly_assets: each module does ``import gen_assets as G``
and lists its generator functions in TEXTURES and SOUNDS; gen_assets.py runs them with its own.
"""
