# Level 490 "Penance" (design notes)

Status: **stages 1-3 built** (the islands, bridges, chains and storm; the spire and its climb, the
looping stairs, the leap from the top; the hung ruins far off, the chains' jolts and groans).
Stage 4 (the creature) built after the second play-test.

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

(As built after the second play-test: see "After the second play-test" below.)

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
- **Look:** the ruins, debris and whirlwinds of Crumbling Farum Azula, kept at night.
- **Build order**, playable after each stage:
  1. platforms, bridges and broken spans, railings, lanterns, shelters, shackles; the storm sky
     with moon, fog banks and lightning; falling sends you to your checkpoint; the way in from
     Level 94 and the jump out to Level 9;
  2. the eldritch structure and its climb (inside spiral shaft, outside ledges and ladders);
  3. the distant chains and the structures they hold (one falls now and then), the chain sounds,
     the drone;
  4. the creature.

## Stage 1 as built (redesigned after the first look)

The first version (neat platforms on a grid, hundreds of thin chains) was too clean and the chains
too small. Redesigned after Elden Ring's Crumbling Farum Azula:

- **Floating rock islands** on a loose grid every 44 blocks (some missing), ragged crumbling rims,
  rock tapering 15-30 blocks below. Ruins on top: pillar rings (many broken, some with capitals),
  arcade walls with arches and jagged tops, tower stubs, rubble, dead trees (some red-leaved),
  shackles. Shelters with a checkpoint radio on some islands and always on the arrival island.
- **Bridges** between neighbouring islands, climbing in half steps; main 5 wide, side 3, some
  broken, railing gaps, small chains dangling underneath, lantern posts at the ends.
- **Colossal chains** built of blocks: links 34 long, 18 wide, bars 6 thick, from y 0 to the top
  of the world; through one island in forty (ruins built round them like a collar) and four round
  each site. Beyond render distance the sky draws them (they fade; lightning shows them), and a
  few far bigger sky-only ones hang miles off.
- **Floating debris** (drawn, not blocks) and **whirlwind pillars** far off in the storm.
- **Lightning:** the server picks strikes (mostly far, some inside the clouds, some close, some
  on a chain); every client draws the same branching, flickering bolt, the clouds glow round it,
  and the thunder (CC0 recordings) arrives after distance / 343 blocks per second.
- **Ambience:** howling wind, the chains singing, the whirlwinds' roar, a low rumble, a deep drone.
- Falling below y 150: within 34 blocks of the site's centre it is the leap (Level 9); anywhere
  else back to the last radio used here, or the arrival island.

## Stage 2 as built: the spire

Concept art (not in the repo) was agreed first: a spine-like spire hanging in the chains, and a
climber on an outside ledge under a colossal link. Built per site (world/layout/Level490Spire):

- **Body:** eleven vertebrae from the island (y 200) to the top platform (y 476), each turned,
  shifted and tilted from the one below, lobed, narrow where they meet, with a darker disc at the
  joints; roots flaring over the island, a capital under the platform. Pale bone-like stone
  (l490_spire), darker joint stone (l490_spire_joint), a dressed-stone core the stairs are cut into.
- **Ribs** curl out near the diagonals at four heights; nine **broken chunks** float round it.
- **Iron:** a band round the upper body (y 433) bolted by beams to the four colossal chains, which
  wear collars there and at y 505; four chains run from the top platform up to those. The colossal
  chains go on up into the storm, drawn in the sky above the world's top (client/Level490SkyChains).
- **The climb** (feet heights): the entrance faces the bridge you arrive by, into a round hall;
  stair A 201-251 (windows); ledge 1 (3 wide) a quarter round to a ladder up to 267; stair B-C
  267-357, enclosed and lantern-lit, through the half-way room at 307 (radio, two big windows);
  ledge 2 (2 wide) half way round, ladder to 375; stair D 375-425 (windows); ledge 3 (1 wide, one
  gap) a quarter round to a 50-block ladder up the outside to the top. Stairs are spirals 4 wide,
  10 blocks a turn, in half steps (walked without jumping); doors and ladders sit on the spire's
  axes so ladders face straight out.
- **Looping stairs** (P.T.-like, world/Level490Loops): stair B-C at 267-297 and 317-347 is three
  identical turns; climbing into the third moves you back one, unseen. Each lap something changes,
  shown only to you: the first loop goes dark (lanterns out, an alcove bricked up, tally marks
  counting your laps, shackles on the steps, GO BACK DOWN, at last every stone scratched full of
  tallies); the second leaves lit lanterns and chains in the alcoves while its own lanterns go out.
  Sounds above you each lap (a knock, footsteps, whispers, dragging, a knock behind, a heartbeat).
  Walking back down to where you came in breaks it (a door creaks far above); then the stair goes
  on.
- **The leap:** any fall that starts at the top platform takes you to Level 9 (a spur reaches out
  over open void for it). Falling into the clouds, or 12+ blocks onto something, sends you back to
  your last radio (the half-way room has one), unhurt.
- **Seen from afar:** past 110 blocks, where the fog takes its blocks, a pale outline of it is drawn
  (client/Level490SpireFar), so you can steer by it from the arrival island.
- Inside the spire the wind is muffled; the drone grows as you climb. (Whether its inside is safe
  from the creature is for stage 4.)

## Stage 3 as built: the far chains and what they hold

References: Elden Ring's Crumbling Farum Azula (wiki screenshots: the domed rotunda on its rock
among firs and debris, the fortress torn apart in the storm, a ruin seen only as a silhouette in a
whirlwind, the great bridge) and the reel stills above.

- **Hung ruins** (client/Level490SkyHung): one in most 1500-block cells, 650-7000 blocks off,
  never over a spire's island. A jagged rock tapering to a point, carrying a tiered rotunda under a
  dome with turrets and dark firs (now and then a red tree), a broken fortress (walls round the
  rim, some gone, a keep, round towers, a wall leaning out), a tall broken tower on a long rock, or
  a span of a great bridge with its piers. Hung from two to four chains going up into the storm;
  debris drifting round them. Only ever sky: dark shapes in the haze, the moon side lit, lit up by
  lightning near them; they fade out as you get within 650 blocks.
- **Falls:** each ruin hangs for 4-10 hours of game time, then its chains snap (they whip up and
  vanish) and it drops into the cloud sea over about half a minute, tilting. You hear the snap, and
  the fall into the clouds, as late as sound takes to come that far. 20-50 minutes later another
  ruin hangs there, slowly coming out of the haze. All worked out from the layout seed and the
  game clock: everyone sees the same fall, nothing is sent, a fallen ruin stays gone.
- **Jolts** (world/Level490Storm, client/Level490ChainEvents): every 40-110 seconds a colossal
  chain 350-2400 blocks away (drawn in the sky there, so it can move) is snatched down eighteen blocks
  as if pulled from below, bounces and settles; its clank reaches you later, rolling like thunder.
- **Groans:** within 110 blocks of a colossal chain you hear it creak under its load every few
  seconds, from the chain itself, more often the nearer you are.
- **Sounds** (tools/level_assets/level490_sounds.py): real CC0 recordings slowed down to sound
  enormous, their highs cut by the distance, with a long dark tail: a heavy chain dropped and an
  anchor chain (the jolts), metal creaks (the groans), a crack and lashing links (the snap), a
  rock slide (the fall).

## After the stage 3 play-test

The play-test verdict: the atmosphere works; the walk to the spire, the climb and the top do not
yet (too long and samey, no pull or tension; plain repetitive stairs, too little outside; the top
an anticlimax), falls should be common, the sky and the cloud sea felt flat. Planned: set pieces on
the walk, a Gothic Farum Azula spire reworked inside and out, a final climb up the chains with
something rising beside you as you leap, sky whales (passive pods and a rare great whale, inspired
by Re:Zero's White Whale but our own design; later the great whale becomes stage 4's creature).
Concept art for those first. Done so far, the parts that need no concept art:

- **Clouds with depth** (client/Level490Clouds): the cloud sea's top a drifting, shaded height
  field (rolling mounds, canyons showing the darker layer below), cloud towers far off as stacks of
  soft puffs, the storm's underside hanging in lumps, anvil clouds on the horizon flickering with
  lightning, faint moon shafts.
- **Falls every few minutes**, and the three sure ones (above).
- **Whale song** (client/Level490WhaleSong): every minute or two a whale sings far off, sometimes
  answered from elsewhere: NOAA's public-domain recordings (a humpback's song, right whale up-calls,
  sei whale downsweeps), slowed and darkened.

## After the concept art (the user's six images, in Downloads, not in the repo)

The user's choices: the spire in dark Gothic stone with pale bone ribs and iron collars ("mix"),
heavy rain, whales paler and closer to Re:Zero's White Whale (our own design, no horn or sigil),
whales first.

- **Whales** (client/Level490Whales): pods of two to four (60-110 blocks long) swim slow loops under
  the storm, diving into the cloud sea now and then (only well away from you); the Great Whale
  (about 400 long) wanders a wider loop. Bone-white, long fins, broad flukes, throat grooves, dark
  eyes, a swimming wave down the body, fog pouring off the skin. At the top of the spire the Great
  Whale comes down out of the storm beside the tower to look at you, then rises back into it
  (Level490WhalePayload). The whale song (client/Level490WhaleSong) comes from them.
- **Rain** (client/Level490Weather): always heavy; Minecraft's own rain (the biome allows it now) and
  the hiss of a real downpour (CC0), muffled inside the spire.
- **Clouds as volumes** (client/Level490VolumeClouds, shaders/core/l490_clouds): a full-screen pass
  in the sky follows each pixel's ray through the cloud sea (y 40-150) and the storm (y 600-760)
  in tileable 3D Perlin-Worley noise (textures/environment/l490_cloud_noise, 64 slices packed 8 x 8):
  billowing heads, lumps hanging under the storm, moonlit and darker below, lit from inside by
  lightning. Replaces the flat sheets (kept as the fallback), the cloud towers and the anvils.
- **The spire reworked** (world/layout/Level490Spire): dark Gothic stone (l490_gothic, carved trim
  l490_gothic_carved), the ribs pale; turrets on corbels with pointed roofs, or broken off with a
  red-leaved tree growing out; flying buttresses between neighbouring turrets; pinnacles round the
  joints; rows of pointed windows, some candlelit (l490_window_lit gives light); a rose window over
  the entrance. Nothing is built where the climb comes out (doors, ledges, piers, ladders). Inside:
  statues in niches round the hall and the half-way room (l490_statue), a fallen bell and rubble in
  the hall, candles on the stairs, a statue niche each turn of the lit stairs, two great breaches
  open to the storm.
- **The final climb:** the top has crumbled to a broken floor with a radio (falling from here on
  only costs this last stretch). A narrow broken bridge (one gap to step across) runs out to the
  colossal chain on the diagonal; a rusted ladder is welded up an iron column bolted to it, up to an
  iron plate at the chain's upper collar (y 508). Stepping off from there is the leap.
- **The walk:** about 220 blocks now (was 300). Where the route turns toward the spire stands a
  Gothic gatehouse over the bridge (two crenellated towers, a pointed arch, a statue in a niche on
  each looking back at you, lanterns). The last bridge before the spire's island groans half way
  across and a stretch behind you falls into the void (world/Level490Crumble); it is rebuilt from
  the layout two minutes later, and a gap still due is rebuilt after a restart too.

## After the second play-test (the outside stair, figures, our own rain)

The user's choices: the climb almost all outside on a broken spiral stair ("messy, chaotic"),
inside only at the half-way room; the loop kept there; whales smooth and detailed with a painted
skin, some passing close, the Great Whale at the top much better; silent hooded figures and one
pilgrim who talks; a walk of about 400 blocks with more buildings; rain of our own, lighter; the
Options screen and pause menu restyled.

- **The climb** (world/layout/Level490Spire): flight 1 winds round the outside from beside the
  hall's door (y 201) to a broken doorway into the half-way room (y 307); the inside stair goes up
  from the room (the loop is on it) to a landing at y 357; a doorway leads out onto flight 2, which
  winds on round the outside to the crumbled top (y 476). Everything on it is walked: there is no
  jumping in the Backrooms (client/gameplay/MovementClientEvents), so the inner lane along the wall,
  a block and a half wide, is never broken (where the rest has fallen away it is a ledge, where only
  a plank is left the plank lies along the wall), half steps are always slabs, rubble lies only in
  the middle of the wide stretches, the stair never comes nearer the axis than 11.5 blocks (nearer
  in, the steps would rise more than half a block per block walked) and is carried in to meet the
  body where it is slimmer than that, and the bridge to the chain leaves the stair's last steps be.
  The scripted test climbs both flights without jumping (58 and 66 seconds). Each flight rises 34 blocks a turn
  (under half a block per block walked, so every step is a half step), 3 wide, hugging the body (its inner edge just outside the body at that height, so it
  swings out round the bulges) and cutting through the turrets and ribs it meets. Broken on
  purpose: gaps of a block and a half (one ordinary jump), stretches crumbled to under 2 wide, a
  stretch of rotten planks on posts, a stretch fallen away and crossed on one plank, a crumbled
  outer edge, rubble, stumps of railing posts, candles, carved brackets under it. Stairs A and D,
  the ledges, ladders, piers and breaches are gone. `stairGuide` / `flightPoint` give where the
  stair is (used by the scripted tests).
- **Whales** (client/Level490Whales): a humpback-like body (a broad flattened head with knobs,
  throat pleats as real grooves, a dorsal hump, long pale pectoral fins with a bumpy leading edge,
  broad flukes with a notch) wearing a painted skin (textures/entity/l490_whale, made by
  `tex_l490_whale`: darker back, paler belly, pleats, barnacles on the chin and head, scars,
  mottling), finer the nearer it is. Every minute or two one swims past 35-70 blocks to one side and
  25-55 above you, clear of the chains and the spire. Far-off whales call to each other every 12-30
  seconds (client/Level490WhaleSong).
- **Rain** (client/Level490Rain): Minecraft's rain is off in the level; ours is thin translucent
  streaks round you slanted by a wind that turns and gusts, stopping on the ground under them (none
  under roofs) with small splashes, and pale sheets of rain drifting far off; lit by the lightning.
  About half as heavy as before (client/Level490Weather).
- **The walk** (world/layout/Level490Layout): the arrival is 9-10 island spacings (396-440 blocks'
  walk) from the spire. Each island on the route has a set piece beside or across the way: a ruined
  chapel you walk through (lit windows, pews, an altar with candles and a statue), a cemetery, two
  gibbets with hanging cages, a colossal fallen statue, a wayside shrine; the third island has the
  pilgrim's camp (tent, fire, log, barrel, lantern). `Level490Layout.spots` says where figures go.
- **Figures** (entity/Penitent, entity/Pilgrim, world/Level490Figures): Penitents (after Holy
  Week's nazarenos and the Maltese penitents: a tall pointed oxblood hood with eye holes, a dark robe
  and rope belt, bare feet chained at the ankles with a length trailing, a tall lit candle or a chain
  across the shoulders) kneel in chains by the colossal chains and at the graves, gibbets and fallen
  statue, pray at the shrines and the chapel altars, walk slowly along the bridges toward the spire,
  or stand watching you; about half vanish when you come within 6 blocks. The pilgrim (a burdened
  traveller: bent double under a pack frame taller than he is, piled with a crate, bundles, sacks
  and a blanket, pots and a bell dangling, a lantern on a pole over the top) keeps his fire, greets
  you, and says one of eleven lines each time you right-click him, in a speech bubble over his head
  (lang `entity.backrooms.pilgrim.line.N`). Code-built models (client/PenitentRenderer,
  PilgrimRenderer).
- **The creature** (world/Level490Hunt, client/Level490Creature; the main enemy, replacing the
  Great Whale at the top). After the user's reference, Stranger Things' Mind Flayer: a tall
  spindle with four very long legs out of its middle rising to a sharp knee and down, two arms. Then
  (play-test: "way too noticeable, the eyes goofy, more smoky, more eldritch") it is made of
  smoke, thousands of dark ash particles flowing along its body and limbs with wisps rising off;
  only a faint smudge in the fog until lightning shows it whole; fading when you look straight at it.
  No face: a scatter of small dim cold eyes blinking out of step, a crown of long curved spines,
  limbs fraying into tendrils (its hands are bunches of them), chains hanging inside the smoke
  glimpsed in the flashes. Each player is hunted by their own:
  - *On the walk* it builds up: scraping and chains rattling below the edges (from 12% of the way),
    its shape far off in the clouds seen when lightning flashes behind it (from 30%), then (from
    half way) an arm rising out of the clouds over the edge and slamming down where you stand.
    Standing about in the open brings it sooner; a roof over you or a lit lantern within 6 blocks
    keeps it off.
  - *On the tower* (play-test: "move through the sky, not climb; start from the very bottom") it
    rises from far below the clouds (y 40) as you start up the outside stair, hanging in the sky 75
    blocks off the tower's axis and drifting round it after you, rising 1.2 blocks a second (a
    little slower than you climb) plus 0.4 for every second you stand still. If it rises level with
    you a hand reaches across to grab you: keep walking and it closes on nothing. Every 20-35 seconds
    a hand slams onto the stair about 11 blocks ahead: hurry past or wait (and let it gain). Inside
    the tower it can't reach you; it waits.
  - *At the top* it holds the top's edge in both hands, fingers over the rim, its head over the edge
    staring at you, and does nothing more. As you cross to the chain and climb it, it lets go and
    floats in the sky off the tower, following, staring, lightning flickering behind it.
  - *If it catches you* the picture breaks up and you wake chained to the pillar of a punishment
    platform (an island beside the route, joined to it by a bridge that is always there; behind
    where you were caught, or the last one before the gate if caught on the tower). Press jump to
    pull at the chains; twelve pulls and they give. Then it leaves you be for a minute.
- **Chains above the world's top** (client/Level490ChainTops): the near colossal chains go on up
  from y 512 to the storm's floor as solid chain-iron (they looked see-through drawn in the sky).
- **Broken flagstones** (`tex_l490_rubble`): the rubble block is now a cracked paving stone (the
  paving itself, struck), seven patterns picked at random per block; half the paving slabs are
  cracked the same way. The posts on the outside stair stand only on full steps.
- **Menus** (client/menu/VanillaRestyle): Minecraft's Options screen and pause menu drawn in our
  menus' camcorder style: the game dimmed behind scanlines with a blinking PAUSE (the footage
  background from the title screen), buttons as menu rows with the yellow play-arrow highlight,
  sliders as thin tracks. The buttons themselves are vanilla's, so they work as before.

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

Nothing planned beyond the play-test notes.
