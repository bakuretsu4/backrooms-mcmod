# Level 94: verification record (finished)

The resume list from the WIP checkpoint was worked through in a real dev server + client
(Windows, RTX 4070, Veil lighting on). Level 94 is now documented as a playable level in the
README and the in-game Field Notes, and has a title-menu footage shot (`scene_7`).

## Verified in game

1. **Outside:** arrival on a road; felt-grass hills now have green sides (the `tex_l94_grass` change);
   a full house exterior from 18 blocks out looks right.
2. **Vehicles:** `L94_VEHICLE` block entities exist server side. They were drawn as a tiny speck
   because `VehicleRenderer` scaled by 1/16 on top of `ModelPart`'s own pixel-to-block
   conversion (the double-scaling hypothesis was right). Fixed: `scale(-1, -1, 1)`. Cars and the
   milk van now draw full size in the town.
3. **Siren:** `L94_SIREN` has its block entity and the horn model renders on the pole. No sound
   errors in the client log (the music itself was not listened to).
4. **Ball pit / playground:** both rooms render correctly (ball pit at castle-local
   x -13..-7, z -5..3; playground with the climbing frame, stairs and checker floor).
5. **Night:** `/backrooms time94 night` outside: a Smiler appears after 15-25 s and kills you if
   ignored ("saw something smile in the dark"). Flashlight on its face at ~15 blocks: it flinches
   and burns away in ~2.7 s, player unharmed. Inside an intact house (with a TV) for 60 s of night:
   no Smilers, sanity held steady (by design 0 at night, +0.6/s by day). `time94 dawn` dispels
   Smilers within a second. Outside at night sanity drains as designed.
6. **Dark-house exit:** the house next to the default arrival was a DARK house (kind 2): its
   interior is `L94_DARK_VOID`. Stepping in sends you to `backrooms:level_9`. (The old lookup failure
   was a test-search problem, not generation.)
7. **Clay door:** found at a Level 9 house (875, 42, -13 in the test world), opened it and stepped into
   the doorway from Level 9: arrived in `backrooms:level_94`.
8. **Castle ramp:** a breadth-first walk over `Level94Castle.standY` from the ramp foot (y41), with
   steps of at most 0.6 blocks, reaches the plateau (feet y151) in front of the gate. Glass
   mountain, spiral ramp and castle exterior render (screenshots).
9. **Smilers in blackouts (Levels 0-2):** a Smiler spawns ~15 blocks away right after
   `/backrooms event blackout`.

Not verified: listening to sounds (only structural checks: every OGG decodes, mono, no clipping,
nothing silent), Veil-off fallback lighting.

## Test helpers

`ClientSmoke.java` and `Level94Probe.java` here are throwaway helpers kept out of the jar. To use
the probe again, copy it to `src/main/java/com/rickr/backrooms/world/layout/` temporarily and remove
it before building a release. `CONTRACT.md` is the original design brief.
