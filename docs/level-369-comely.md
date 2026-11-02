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
- **The way out:** one arch of vines per 140 x 140 blocks, against the north wall of a skylit
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

## Second pass (planned after the first play-test, not built yet)

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

### Plan

1. **Bigger** (Level369Plan): larger cells and zones; most pillar-hall zones open into one hall
   about 120 across with long winding brick paths; hallways run about 130 blocks straight; a rare
   "huge" zone (a 200+ pillar hall, or a 300+ corridor); skylit and dark rooms scaled to match.
   Longer view distance in the level's fog.
2. **Plants** (tools/anomaly_assets/level369.py, registry/Level369Blocks): new grass and flower
   models built from several crossed thin planes per block with 64 px textures (the recent
   texture cleanup freed about 340k texels of block-atlas room; it must stay 4096 x 4096), smooth
   gradients instead of pixel noise; a calm lawn texture; denser, more even planting with small
   flowers in drifts like the pictures.
3. **Light** (client): warm low golden light in pillar halls; window beams in hallways (reusing
   Level 227's client/light/SunBeams); a white glow at the far end of long corridors; a softer
   camcorder mood for the level (more bloom and haze, less contrast).
4. **Satisfying:** plant sway (a vertex wave limited to Level 369's plant blocks; to be checked
   against Veil, with animated textures as the fallback); rustle and grass-footstep sounds when
   wading through flowers; drifting petal and pollen particles in lit areas; the ambience redone
   quieter and warmer.
5. **Test** side by side with the pictures (screenshots), FPS check in the biggest hall, docs,
   ship, play session. Existing worlds need their Level 369 folder deleted to get the new layout.
