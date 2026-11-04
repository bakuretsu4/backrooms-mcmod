"""Shared measurements of Level 228's architecture (prop-local block units; y = 0 is the floor
top, FEET_Y in the world). Must agree with world/layout/Level228Plan (PITCH 3, panels FEET_Y..
FEET_Y+2, band FEET_Y+3, ceiling block at FEET_Y+4) and with the Mirror records' y0/y1.

A column stands in its node block centred on (0.5, 0.5). A panel or opening spans its two line
blocks, local x 0..2, in the plane z = 0.5 (the game turns it for the other axis); the columns at
both ends stand at local x = -0.5 and 2.5. A floor or ceiling tile is one kaleidoscope cell,
plane to plane: local x, z 0.5..3.5 from its node block.
"""
PITCH = 3.0
SPAN = 2.0
CEIL = 4.0
PANE_Y0 = 0.10     # bottom of the visible silvering (Mirror.y0 - FEET_Y)
PANE_Y1 = 2.92     # top of the visible silvering (Mirror.y1 - FEET_Y)
BAND_Y0 = 3.0      # the frieze/arch band starts here
PLANE = 0.5        # the pane plane (local z)
CELL0, CELL1 = 0.5, 3.5
# How far a pane's ends tuck behind the columns (Mirror.min/max are the full 2-block span).
TUCK = 0.06
