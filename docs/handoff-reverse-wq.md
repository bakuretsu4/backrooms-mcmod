# Handoff: tasks for reverse-wq's Claude

Written 2026-11-02 by bakuretsu4's Claude. **bakuretsu4 has read and confirmed everything in this file**,
and it is how the two of you split the work: these five tasks are yours; the list at the bottom is
what bakuretsu4's side is doing, so stay out of those files where you can (or coordinate first).

Reference screenshots are in `docs/reference/` (not shipped in the jar). Most come from other
Backrooms mods and are **reference only**: match the look, the mood and the scale, never copy
their code, models, textures or sounds (assets must be CC0 or our own, as always).

Before you start: pull. bakuretsu4's side is landing the **exit-distance change** (see the bottom)
just before this file; build on top of it.

---

## 1. Poolrooms: new anomaly areas, and a new main exit (big)

**Anomaly areas.** "This is how I want the anomaly areas in there to look. Add/change them in that
level according to these images." References `docs/reference/poolrooms/anomaly_1..5.jpg`:

- `anomaly_1`: an open-air courtyard pool. Plain grey concrete walls, heavy concrete beams across
  an open roof with bright blue sky and clouds, the water a bright turquoise, a dark doorway out.
- `anomaly_2`, `anomaly_3`: a long dark hall with a channel of glowing cyan water down the middle
  (the tiled floor visible through it), tall dark piers along both sides with a wall light on
  each, the lights reflected in the water. Mostly dark; the water is the brightest thing.
- `anomaly_4`, `anomaly_5`: almost black: a tangle of low tiled walls, ledges and walkways over
  glowing cyan water, seen from above. The water glows; everything else is shadow.

**The new exit.** "That big room with the water in the middle and staircase should be the new
way to exit. But change it up and make it our own, don't copy it exactly. The scale of it should
be the same/similar." References `docs/reference/poolrooms/exit_hall_1..4.jpg`: a huge, very tall,
dark hall whose ceiling slopes up from all four walls to a small bright square skylight at the
top (sky visible); the floor is one big bright turquoise pool; a long straight flight of tiled
steps climbs along one wall towards the top; small lamps in the corners, a small doorway at
water level, ladders and a plank at the pool edge (`exit_hall_4` is the view down from high up).
The way out is up there. Match the scale (estimate it from the shots); design the rest yourself.

- It replaces the drain as the Poolrooms' **main** exit (to the next level of the path, see task
  3). Whether the drains stay as scenery is your call.
- Keep the spacing from the exit-distance change: about one per 1024 x 1024 blocks
  (`PoolroomsLayout` EXITS), and arrivals land at least 400 blocks from one (automatic, through
  `LevelLayout.arrivalExitClearance` and `Travel.arrival`).
- Exit hints (`world/ExitHints`: the sound, NightShot arrows, torn notes) and `/backrooms locate`
  must point at the new hall.

## 2. Level 1 garage: wet reflective floors, and real darkness when the lights go out (big)

"For the garage level, I want reflective wet surfaces like in the images. Also the near pitch
black image is how I want it to look when lights go out." References
`docs/reference/level1-garage/`:

- `wet_1`, `wet_2`: a dark concrete garage whose wet floor reflects the lights: the light strips
  on the pillars and the ceiling tubes show as long bright streaks in the floor, puddles sparkle,
  and between the lights it is very dark. The reflections are what sells it.
- `lights_out`: the lights-out state. Nearly pitch black: only the faintest shape of the floor
  is visible. Whatever Level 1 does when its lights shut off should look like this.

## 3. Level order: follow the wiki's numbers (big)

bakuretsu4 chose this order (it replaces the current one):

```
Overworld -> 0 -> 1 -> 2 -> 4 -> 7 -> 9 -> Poolrooms -> 37 -> 39 -> 94 -> 369 -> 490 -> escape
```

Each level's exit keeps its look; only where it leads changes:

| Exit | Was | Becomes |
|---|---|---|
| Level 0 stairwell (and flickering wall) | 1 | 1 (same) |
| Level 1 stairwell | 2 | 2 (same) |
| Level 2 EXIT bulkhead | 4 | 4 (same) |
| Level 4 stairwell and exit elevator | Poolrooms | **7** |
| Level 7 sunken door | 39 | **9** |
| Level 9 underpass | escape | **Poolrooms** |
| Poolrooms exit (the new hall from task 1) | 37 | 37 (same) |
| Level 37 pit | 369 | **39** |
| Level 39 mill window | 94 | 94 (same) |
| Level 94 dark-door houses | 490 | **369** |
| Level 369 arch of vines | 7 | **490** |
| Level 490 leap | 9 | **escape** (the tape-stops ending moves here from Level 9) |

Arrivals that depend on where you come from must follow: Level 369's skylight fall
(`Level369Layout.preferredArrival`, now `from == LEVEL_37`) becomes the arrival from 94; Level
39's tunnel arrival (`from == LEVEL_7 || from == POOLROOMS`) needs a decision for arrivals from 37;
check `Level0/2/94/227Layout.preferredArrival` too. Arrival cards and their "how you got here"
lines (lang `message.backrooms.arrive.*`), the README path diagram and every level's "reached
through..." text must say the new order.

**Side exits** (bakuretsu4's rule, confirmed): side exits may send you off the line, ahead or
back, but **the level you land in must always have a way back to the level you left**. Going
back is fine automatically (walk the line forward again). Going ahead needs a way back. With the
new order these skip ahead and need one (add a way back, or ask bakuretsu4 if one should go):

- Poolrooms sunlit archway -> 39 (skips 37).
- Level 9 gondola -> 39, and Level 9 clay door -> 94 (both now ahead of 9).
- Level 227's random exits (`Level227Noclip.dangerousLevel`, `VoidBubble.MIRRORS`): 227 stays
  **off the line** (you're only dragged in from Level 9's woods and Level 94's hills). Following
  the rule, it may only drop you in levels from which the line still reaches 9 or 94 (anything up
  to 94 in the new order), so 369 and 490 are out.

Back-links that stay valid as they are: 37's void and dark openings, 94's flooded houses (to 7)
and throne (to 1), Level 9's foxhole (to 2) and gloom (to 0).

Check, after the change: every level reachable on the line; every side exit's target has its way
back; the escape tests in `src/test/java/com/rickr/backrooms/escape` updated to the new `next()`.

## 4. Level 37: two structure bugs (small)

"These structures need to be fixed. Entrance is cutting off the arch. Pillar is trying to go
through the rectangle light in the ceiling." References `docs/reference/level37-fixes/`:

- `entrance_cuts_arch`: a round tunnel entrance cuts into a curved arch next to it, so the arch
  is sliced off.
- `pillar_through_skylight`: a pillar runs up into a rectangular ceiling light (skylight) and
  through it.

Fix the plan so these never overlap (all seeds, not just these spots).

## 5. Level 7: make the Thing more noticeable (medium)

"Make the thing in level thalassophobia more noticeable." Today (`client/Level7Client`) you only
feel it: a groan every 30-80 s, the water heaving now and then near the surface, and rarely
(every 60-150 s, only 12-110 blocks deep) a vast dark shape gliding past at the edge of sight.
Most players never notice it. Make it clearly there (seen and heard more, closer, more often)
while keeping it a presence rather than a monster that attacks, unless bakuretsu4 says otherwise.

---

## What bakuretsu4's side is doing (avoid these, or coordinate first)

- **Exit distances** (landing now): about one main exit per 1024 blocks in Levels 0, 1, 2, 4,
  Poolrooms, 37, 369, 9 and 94; arrivals from another level land at least 400 blocks from the
  nearest one (300 in Levels 2 and 37). Files: `world/Travel.java` (`arrival`, `clearOfExits`),
  `LevelLayout.arrivalExitClearance`, each level's exit region. Level 94: exit houses every 576
  blocks, about one in three dark-door (so one per ~1024), the rest flooded. Your escape tests
  are updated to the new arrival rule in the same commit.
- Removing the voice-like and creature-like sounds bakuretsu4 picked by ear, in every level
  including yours: Level 0's distant wall scratch and far call; Level 4's window whisper; Level
  39's hound growls at hydrolitis and holes, house scream and hydrolitis whisper; the Wretch's
  moan, sob and scream; Level 94's night laughter; Level 227's distant animal call; the sanity
  whisper (also used by Level 490's tower). Their code, sounds.json entries, .ogg files and
  generators go.
- Removing the wet carpet and its sound from every level.
- Level 0: lighting glitches around the stack void (the big hole with several floors), and new
  or changed anomaly areas from bakuretsu4's references.
- Level 369: tall, flowing 3D grass.
