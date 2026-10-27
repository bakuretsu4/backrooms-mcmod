# Level 490 "Penance" (design notes)

Status: **stage 1 built** (platforms, bridges, chains, lanterns, shelters, the storm sky, falling,
the way in and the leap out). Stages 2-4 not built yet.

## The idea

You arrive on a stone platform hung from gigantic chains above an endless sea of storm cloud,
at night. Fenced stone bridges lead out across the void toward one strange, eldritch structure
on the horizon. You have to find it and climb it; the exit is at the top. Something colossal,
made of ash and chains, lives down in the clouds, and the higher you get, the closer it comes.

Theme: **punishment.** "Some chains aren't meant to imprison, but to punish." Whatever was
chained here is climbing out.

## References

Two stills from an Instagram reel (not kept in the repo):

1. A single colossal iron chain hanging straight down out of storm clouds; its top lost in the
   sky. A lone figure, seen from behind, tiny beside it. A row of wrought-iron fence spikes with
   arrowhead tips in the foreground, dark against the sky.
2. A long, narrow stone walkway running off into the distance between tall spiked iron railings,
   nothing visible below but grey haze. The moon breaking through heavy clouds, the only bright
   thing in the picture.

Look taken from them: almost no colour (blue-grey, black iron, pale stone, white moonlight),
heavy storm clouds, dark but readable, lonely, a low-resolution grainy feel (the camcorder
effect already gives this).

## Sky and weather

- Storm clouds, the moon breaking through now and then: the main light.
- Distant lightning that briefly shows the chains rising out of sight.
- Thunder far away (atmosphere only, no physical effect).
- Fog banks that roll over the walkways now and then, hiding the next bridge and the structure.

## The chains

Two kinds:

- **Near chains** hold up the platforms, walkways and structure. You can touch them. They creak
  and sway, and now and then one snaps and a section drops into the clouds.
- **Distant chains** are scenery: colossal, on the horizon, hanging out of the clouds into the
  cloud sea. Drawn as part of the sky (like Level 39's far facade), so they can be miles tall.
  - Only a few are visible normally; lightning reveals many more hidden in the clouds.
  - Some hold other platforms or towers far away: you are not the only one hung here.
  - Mostly still; now and then one jerks taut as if pulled from below, with a deep distant clank.
  - Rarely, a distant structure's chain snaps and you watch it drop silently into the clouds,
    then hear the sound much later.

## Layout

- **Endless** walkways and platforms, but **only one** climbable structure, which you have to
  find. Finding it is by sight: it is the one thing that rises above everything, and lightning
  outlines it.
- You start on a large platform; long, straight, fenced bridges (reference 2) branch out from it.
- Some bridges are broken off mid-span: find another way round or risk the jump.
- On the platforms:
  - shackles, cages and iron rings where people were chained;
  - small stone shelters to hide in;
  - old iron lanterns, some lit, some out.

## The structure

- Strange and eldritch, not a normal building.
- **Wrong geometry:** twisting arches, spires leaning impossibly, a spiral stair that takes
  longer to climb than it should (the shaft can really repeat a section).
- **Ribbed and organic:** stone that looks grown, not built; gothic shapes that read as a skeleton.
- **Floating pieces:** chunks of it hang loose in the air between the chains, slowly turning.
- **Silhouette:** something you can't name; it looks different from every side, so the outline
  you steer by keeps changing as you approach.
- **The climb** mixes safe and unsafe:
  - inside: a spiral stair up a shaft, with small windows out to the drop;
  - outside: exposed ledges and ladders, broken railings.

## The creature

- Like the Mind Flayer (Stranger Things) in spirit, but our own design: a colossal shape of
  drifting dark ash with real chains and shackles inside it, dragging and clanking.
- The giant body stays down in the clouds and far off; you only see it whole in lightning.
  Its long limbs reach up over the walkways to grab you.
- It builds up:
  1. scraping and chain rattles from below the edges;
  2. a shape seen in lightning flashes that wasn't there before;
  3. limbs pulling up over the railing.
- **If it catches you:** you wake up shackled on a punishment platform elsewhere in the level,
  break free and have to find your way again.
- **Holding it off:**
  - keep moving: it gains when you stop or backtrack;
  - hide in the stone shelters: it won't go in, but it waits outside;
  - stay near lit lanterns: it avoids the light, so dark stretches are where it reaches you;
  - inside the structure's shaft you are safe; the outside parts of the climb are fair game.

## The top and the exit

- At the top, all the structure's chains meet and carry on up into the clouds: they don't end,
  they go up into something even bigger.
- **The exit is a jump:** you let go and step off the top into the clouds on purpose, the one fall
  that doesn't bring you back. The datamosh transition plays as your "death" and takes you to
  Level 9.
- **The creature watches you leave:** no final chase. In a last lightning flash you see it whole,
  just watching as you go.

## Sound

- Mostly near silence, so every sound stands out.
- Deep creaks and groans from the near chains, especially the ones under your feet.
- Huge far-off metallic clanks when a distant chain jolts, rolling like thunder.
- A barely-there low drone that grows as you climb the structure.
- Distant thunder (see Sky and weather).

## Falling

Falling off sends you back to your last checkpoint **in this level**. Not more punishing than
that: the level is hard enough.

## Place in the path

One straight path through every level, each exit leading naturally into the next:

Level 0 → Level 1 → Level 2 → Level 4 → Poolrooms → Level 37 → Level 7 → Level 39 → Level 94 →
**Level 490** → Level 9 → home.

Built: Level 94's dark doorways lead here and the leap leads to Level 9.

- Level 94's main exit leads here; the exit at the top of the structure leads to Level 9.
- Every exit has its own look, but all use the datamosh transition.
- The old rare exits stay as shortcuts.
- Exits get harder to find each level, mainly by cutting the NightShot (night vision) arrows;
  the exit sounds stay about the same. Arrows compared with now:

  | Level | Arrows | Only within (blocks of the exit) |
  |---|---|---|
  | Level 0 | 100% | anywhere |
  | Level 1 | 75% | anywhere |
  | Level 2 | 55% | anywhere |
  | Level 4 | 40% | anywhere |
  | Poolrooms | 30% | anywhere |
  | Level 37 | 20% | ~60 (its pits are close together) |
  | Level 7 | 15% | ~120 |
  | Level 39 | 10% | ~100 |
  | Level 94 | 7% | ~80 |
  | **Level 490** | 5% | ~60 |
  | Level 9 | 3% | ~50 |

## Build decisions

- **Climb:** about 300 blocks from the bridges to the top of the structure; the level is about
  512 blocks tall. The chains above it are drawn as part of the sky (like Level 39's far facade).
- **Distance:** the structure stands about 300 blocks from where you arrive.
- **Bridges:** main bridges 5 wide, side and broken ones 3 wide.
- **Build order**, playable after each stage:
  1. platforms, bridges and broken spans, railings, lanterns, shelters, shackles; the storm sky
     with moon, fog banks and lightning; falling sends you to your checkpoint; the way in from
     Level 94 and the jump out to Level 9;
  2. the eldritch structure and its climb (inside spiral shaft, outside ledges and ladders);
  3. the distant chains and the structures they hold (one falls now and then), the chain sounds,
     the drone;
  4. the creature.

## Stage 1 as built

- Grid of platforms every 48 blocks (a few missing), decks at y 200, level 512 tall; one site per
  1024-block region, its arrival platform ~300 blocks away, a route of main bridges between them.
- Side bridges: 3 wide, one in five broken (one in three of those a 2-block gap).
- Chains from every platform corner and every bridge's middle up to the top of the world.
- Lanterns on posts along the railings (about half lit); shelters with a radio on some big
  platforms and always on the arrival platform.
- Falling below y 150: within 32 blocks of the site's centre it is the leap (Level 9); anywhere
  else back to the last radio used here, or the arrival platform.
- Sky: storm overhead (two drifting cloud layers silvered toward the moon), the moon now and then
  hidden, a moonlit cloud sea below, lightning every 20-50 s with thunder 1-4 s later.

## Name and number

**Level 490, "Penance".** 490 is "seventy times seven", how many times you must forgive:
penance as something that never ends.

No existing wiki level fitted, so it has its own name and number. Closest existing ones, for the
record:

- Level 108 "World of Chains" (Fandom): mostly an indoor concrete complex full of chains, and a
  safe level with friendly entities. Opposite tone.
- Level 63 "Tranquility" (main wiki): bridges and platforms over a sky void, but calm and safe,
  no chains.
- Level 505 "Sky Bridges" (small fan wikis): endless bridges between clouds, no chains or theme.
- Level 255 "Death Factory" (main wiki): catwalks over an abyss on rusted chains, but a factory.

## Still open

Nothing big; details get decided while building.
