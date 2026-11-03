# Level 369, "Comely"

An endless indoor garden: rooms of pastel-painted plaster and brick, every floor carpeted wall to
wall in grass and flowers. Based on the Backrooms Wiki's
[Level 369 "Comely"](https://backrooms-wiki.wikidot.com/level-369) (CC BY-SA 3.0; nothing of its
text or images is copied into the mod) and on two pictures the user brought: a white hallway of
tall bright windows with blue flowers in the grass and a light at its end, and a low peach hall
of pillars with red flowers and a winding brick path.

## The user's choices

- **Level 369 with a twist.** It looks like the wiki's gentlest level ("habitable", nothing
  lives here), and on the surface it is: no creature at all. The level itself is the danger.
- **Where:** after Level 37. Level 37 --a deep pit--> Level 369 --the arch of vines--> Level 7.
- **Way in:** dive down one of Level 37's dark pits as before; you fall out of a skylight into
  soft flowers (the landing never hurts).
- **Way out:** a great archway of vines and flowers (from the wiki); walk through it into Level 7.
- **Rooms**, all four looks:
  - white window hallways with blue flowers (picture 1);
  - peach pillar halls with red flowers and a winding brick path (picture 2);
  - skylit rooms of other pastel colours, some with curved walls, bushes and young trees (wiki);
  - dark rooms where vines hang, with glowing plants (the user: "in the dark rooms, add glowing
    foliage too"); dead ends.
- **The twist: it makes you forget.** The longer you stay, the more you lose:
  - the camcorder's readouts (battery, tape counter, BPM, then REC) fade out one by one;
  - the way back: doorways you came through are walled over, or open onto rooms you've never seen;
  - your things: now and then an item slips out of your inventory into the grass nearby (you can
    find it if you look).
  Everything comes back the moment you leave through the arch of vines.
- **The light.** A warm light with no source drifts from room to room like a wave, then the room
  falls back to dim (wiki). The user wants it to do "just overall good things": it is kind (warm,
  beautiful, sanity comes back in it). Its lie is the comfort itself: it makes you want to stay
  while the forgetting goes on.
- **Pace:** a normal visit about 20 minutes; forgetting starts after about 5 and is total by about
  25.

## Stages

1. The level: dimension, blocks, floor plan with the four kinds of room, the way in and the way
   out, arrival text, exit hint, atmosphere, music. Built.
2. The twists: the drifting light, the forgetting, restored on leaving. Built.

Next: the user's first play-test.

## What was built

### The level (world/layout/Level369Plan, Level369Layout; registry/Level369Blocks)

- A grid of 20 x 20 cells, one room each, the 1-thick walls on each cell's west and north edge.
  Rooms come in zones of 3 x 3 cells sharing a kind: hallways (30%), pillar halls (30%), skylit
  rooms (40%); about one cell in 11 is a dark room instead.
- **Hallways:** a 5-wide corridor straight through each cell; a zone's corridors all run the same
  way and join end to end. Tall windows of sky-blue light down one side (full-bright), the Lobby's
  wooden doors (closed; behind them, plaster) down the other, light panels down the middle of the
  ceiling, blue forget-me-nots in the grass. Side passages lead off now and then.
- **Pillar halls:** low (4 high), cream pillars every 8 blocks, salmon acoustic ceiling with light
  panels, red daisies; a zone's cells open into one wide hall. Winding brick paths follow one
  contour line of a smooth noise.
- **Skylit rooms:** 7 high, mint, lavender, cream, rose or white, one big skylight, four small
  ones or a long strip; bushes along the walls, sometimes a young tree under the light; flowers of
  every colour in patches. Neighbours of a zone sometimes open into each other.
- **Dark rooms:** sage, unlit, one doorway, glowing plants in the grass (cyan bells, green
  lanterns, violet fronds, glowing moss) and vines with glowing flowers hanging from the ceiling.
- **Doorways:** 3 wide, 3 high, a brick threshold.
- **The way in:** from a Level 37 pit you appear at y50 in a skylight shaft of the nearest skylit
  room and fall into the flowers; no fall ever hurts in Level 369 (world/Level369Rules).
- **The way out:** one arch of vines per 1024 x 1024 blocks (140 x 140 at first), against the north wall of a skylit
  room: a round-topped opening in overgrown masonry, filled with a curtain of hanging blossom
  (l369_veil, full-bright, walk-through). Walk into it: Level 7.
- Lighting: light panels, skylights and windows are Veil fixtures (client/light/FixtureType); the
  skylit rooms also get invisible light blocks; client/Level369Atmosphere gives the lightmap a warm
  gold fill, and a cool green-cyan where it is dim (the glowing plants), with a warm cream haze.
  Camcorder mood: soft, warm, a little saturated, a generous glow (client/AtmosphereGrade).
- Sound: its own ambience (a breeze in gusts, leaves stirring, a far sprinkler, and in the
  strongest gusts the flowers' faint "singing"), its own exit sound (leaves in the arch, water far
  behind it, a faint warm chord), short soft room acoustics, and its own music, "Comely"
  (tools/anomaly_assets/music_f.py): a music-box waltz in F that loses more of its notes each time
  it comes round. All generated in code.
- Textures are 64 px (surfaces) and 32 px (plants): the block atlas has to stay at 4096 x 4096.

### The light (world/Level369Light)

Bands of warm light 160 blocks apart sweep across the level in one direction (fixed per world),
at a slow walking pace: one passes any spot every 4 minutes and stays about 45 seconds; the front
is bent by a slow noise so it never reads as a line. A pure function of position and time, so
server and client agree without syncing. In it: your sanity comes back (up to +1.2/s) and you heal
(half a heart every 3 seconds); the picture turns golden and brightens; a soft swell of warmth
plays as it arrives. Level 369 never drains sanity anywhere.

### The forgetting (world/Level369Forgetting)

Time spent in the level this visit; nothing for 5 minutes, total at 25.
- **The camcorder's readouts** (client/CamcorderHud) fade in order: battery, then tape counter and
  clock, then heart rate, then REC, each over a quarter of the way. The exit signal stays (the user
  didn't pick "where the exit is").
- **The way back:** the plain doorways you go through are remembered (the last six). When one is
  9+ blocks away, behind you and nobody is near it, it may be walled over (3% a second, up to 15%
  at full forgetting), and a new doorway opens elsewhere along the same wall, where there is room
  to walk on both sides. The same two rooms stay joined, so you are never shut in. Hallways'
  doorways are left alone.
- **Your things:** now and then (on average every 75 seconds at full forgetting) a stack slips out
  of your inventory and lies in the grass 3-7 blocks behind you, in the same room. Never the
  camcorder or what's in your hand. The item never despawns; picking it up is finding it.
- **Leaving** (the arch, or any other way) brings everything back: lost things still lying in the
  grass come back to your inventory (and vanish from the grass, even from unloaded chunks), the
  moved doorways are rebuilt as the level made them, and the next visit starts from nothing.
  Visits are saved with the world (SavedData "backrooms_level369_visits").

## Second pass (after the first play-test)

The user's verdict: "doesn't feel dreamlike or satisfying enough. The foliage is too gritty (or
noisy) and doesn't feel completely like the reference images", and "areas need to be bigger".

Compared with the two pictures:
- **Plants:** the pictures have long soft grass blades and many small flowers in one smooth
  meadow. Now: one 32 px cross sprite per block with hard pixel edges and high-contrast noise, a
  noisy lawn texture, few and oversized flowers.
- **Light:** the pictures have warm low sunset light across the pillar hall, bright beams from the
  hallway windows lying on the grass, and a glowing white corridor end. Now: dim, greenish-grey,
  far walls fading to murk, no beams, no glow.
- **Size:** the pictures are a huge open pillar hall and a corridor that seems endless. Now: 20-block
  cells, pillar halls at most ~57 wide, straight corridor runs of ~60.

The user's choices:
- **Size: mostly big, a few huge.** About twice the size as the norm (pillar halls ~120 across,
  straight corridor runs ~130 before they turn), and now and then a vast pillar hall (200+) or a
  corridor that seems endless (300+).
- **Rooms:** all four kinds stay (peach pillar halls, white window hallways, skylit pastel rooms,
  dark rooms with glowing plants).
- **Plants: a soft, dense meadow**, as close to the pictures as Minecraft allows: several thin
  blade planes per block, small flowers several to a block (red daisies, blue cornflowers),
  smooth colours, a calm lawn under them. FPS in the big halls to be checked.
- **Light, all four:** visible light beams from the hallway windows lying across the grass;
  golden sunset light in the pillar halls; long corridors fading into soft white light far ahead;
  a soft dreamy glow over everything (gentle bloom and haze, lower contrast).
- **Satisfying, all four:** plants sway gently as if a breeze passes; walking through flowers
  rustles (and grass footsteps); petals and pollen drift through the light; a quiet, warm
  ambience with faint wind (no birds).
- Unchanged: the drifting light wave, the forgetting, the way in from Level 37 and the arch of
  vines out to Level 7.

### What was built

- **Bigger** (world/layout/Level369Plan): 32-block cells in zones of 4 x 4, so a pillar hall
  opens about 128 blocks across and a hallway runs about 128 straight; about one 384-block square
  in 16 is a huge area (one pillar hall, or hallways, 384 across). Dark rooms are never inside a
  huge area or next to each other, and a corridor that ends at a dark room always runs on into
  it (the first layout had a few sealed rooms); bushes never grow in front of a doorway; the
  brick paths keep an even width. Checked on a 1024 x 1024 map: no room is shut off.
- **The meadow** (tools/anomaly_assets/level369.py): 64 px grass and flowers drawn from soft
  tapered blades shaded root to tip (no pixel speckle), big open flower heads (cornflowers, red
  daisies, pink cosmos, white daisies, buttercups), every lit lawn block planted with flowers in
  drifts. The plants stand thigh to waist high (16-19 px), flower heads riding near the top. Each
  is two crossed planes of its own texture and two single 3D blades (l369_blade_0/1) at
  different angles, every one cut in two at half height so it can curve; mipmapped cutout, so
  the blocks merge into one field and stay soft in the distance. The lawn under it is a calm,
  even green. (Third pass, after the user's reference of a tall flowing grass field.) Frame rate
  in the huge pillar hall on the test machine: about 360 (450-480 with the old knee-high cards;
  320 with four blades per plant, which looked the same from a few blocks). Up close (fourth
  pass) the meadow is real geometry instead (client/Level369Grass): within 32 blocks every grass
  and flower block grows about 20 thin tapered blades, smooth gradient from shadowed root to
  sunlit tip with no texture and no cutout edges, plus flower heads of petal fans on stems; past
  20 blocks they thin out while the block panels rise, so the far field stays the panels (under a
  shader pack or Sodium the panels stay everywhere). Frame rate in the huge pillar hall on the
  test machine: 330-360 steady (295 at worst), about what the block meadow alone cost.
- **The light, per kind of room** (client/Level369Atmosphere, client/AtmosphereGrade): window
  hallways cool white, their windows throwing shafts of light (client/light/SunBeams: only the
  six nearest windows, only while you are in a hallway); pillar halls a low golden sunset with
  amber air; skylit rooms soft cream daylight. Calibrated against the two pictures by average
  brightness of the frame (the pillar hall picture 0.24, the hallway 0.37): pillar halls now
  0.26, skylit rooms 0.37, hallways about 0.41 (0.47 while the drifting light passes), nothing
  clipped to white (it was 0.40-0.76 with up to 27% pure white, "it looks like heaven"). The
  drifting light now lifts the room you are in by 15% and turns it golden, instead of setting
  one bright exposure everywhere. Highlights roll off softly; the haze is a darker tint of each
  room's light, so the distance softens into colour instead of white.
- **It flows** (assets/minecraft/shaders/core/rendertype_cutout_mipped.vsh): the meadow's grass
  and flowers carry a marker block colour (client/Level369Atmosphere SWAY_MARKER); the cutout
  shader bends only those vertices, by the square of their height above the lawn, so every
  blade curves from an upright root. The whole field leans one way with the wind; waves roll
  across it, bending it further and catching the light on the tips as they pass, with gusts and
  a small flutter. Every other block is untouched. Under Iris or a shader pack the meadow stands
  still (nothing breaks). The near blades (client/Level369Grass, shaders/core/l369_grass) use
  the same wind in world coordinates, so both sway as one field; near the camera the cutout
  shader sinks the block meadow into the lawn (uniform MeadowFade) to hand over without a seam.
- **Sound:** the lawn has grass footsteps (it fell back to concrete before), and wading through
  the flowers rustles (step.meadow, layered over the footstep; player/PlayerGameplay). The
  ambience is quieter and warmer: low breeze, a faint warm room tone, the flowers' faint song in
  the strongest gusts; the far sprinkler is gone.
- **Petals and pollen** (client/Level369Petals, client/particle/PetalParticle): a few petals at a
  time from the room's own flowers ride the breeze and settle; pollen hangs in the lit air,
  faintly glowing. None in the dark rooms.
- Frame rate: unchanged once the area around you has loaded (about 450-480 FPS in the huge
  pillar hall on the test machine, as before the second pass); lower for the first minute after
  a long teleport while the meadow's chunks build.

Existing worlds keep their old Level 369 rooms where they were already generated: delete the
world's dimensions/backrooms/level_369 folder to get the new layout.
