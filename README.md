# The Backrooms: Found Footage

A NeoForge 1.21.1 mod: noclip out of reality into an endless Backrooms lit by real dynamic
lights, seen through a found-footage camera. Built for multiplayer: play it with friends on a
server or LAN.

## Playing

**Everyone (server and every player) needs the same mod jar** in their `mods` folder, with
NeoForge 1.21.1 installed. The jar already contains the Veil rendering library.

Not compatible with Sodium or Embeddium/Rubidium: with Sodium 0.8.13 the Veil lighting breaks
(Level 0, the Poolrooms and Level 37 render 3-4x darker and the pool water disappears). With
Iris installed the mod falls back to simpler lighting. Performance mods tested and fine:
Lithium, FerriteCore, ModernFix, Entity Culling, ImmediatelyFast and Dynamic FPS.

| Action | How |
|---|---|
| Get in | Reality has seams in the dark (see **Getting in** below): keep walking into a wall somewhere dark and enclosed, stay alone in the dark too long, film the dark with a **Camcorder** (craft: iron ingots around a glass pane and redstone; right-click to record), or lie down in a bed. Suffocating inside a block still works too. |
| Flashlight | `R`. Everyone sees your beam. Uses the camcorder battery. |
| NightShot (infrared) | `N` (drains the battery 3x faster) |
| Camcorder REC overlay | `H` (off by default). Switching it on shows REC, battery, clock and exit signal clearly for a few seconds; then they sink to a faint ghost until one of them changes. A low battery (15% or less) pulses soft red at the top right even with the overlay off. |
| Sprint | Limited by energy: a short, thin faint line above your hearts shows it while you run (turns soft red when you're out of breath). Rest to refill it. |
| Jump | Not possible in the Backrooms (except in water, to swim and climb out) |
| Loot | Right-click Wooden Crates, Backpacks and Supply Lockers (see Supplies below) |
| New battery | Right-click a Camcorder Battery |
| Settings | **SETTINGS** on the main menu, **Backrooms Settings...** in the pause menu, or Config in the Mods list: camera smoothing (default 35%; the original feel was 88%), handheld shake, view tilt, motion blur, wide lens, stamina line, and **QUALITY** for weaker computers (see Performance below). Changes apply immediately and are saved per player in `config/backrooms-client.toml`. |

### Performance

**QUALITY** (on the SETTINGS screen) turns down the three things that cost the most graphics-card
time. HIGH is the full look; try MEDIUM or LOW if the game stutters.

| | HIGH | MEDIUM | LOW |
|---|---|---|---|
| Dynamic lamp lights (shadowed) | 32, within 28 blocks | 20, within 24 | 12, within 20 |
| Glow around lamps (blur passes) | 6 | 4 | none |
| Pool water reflections | full | shorter | none (plain mirror colour) |
| Sun beams (Level 37, Poolrooms) | 20 | 12 | 6 |

Measured at 1920x1080 on a fast graphics card, LOW takes about 15% less time per frame than HIGH
in Level 0 and about 40% less in Level 37; slower cards gain more.

### Getting in

Every way in (`world/RealWorldEntries`, `world/NoclipIntro`) counts "dark" as light level 3 or
less at your eyes and "enclosed" as no open sky above you:

- **Noclip through a wall:** keep walking into a wall somewhere dark and enclosed and it gives way,
  usually within a second or two. Never in daylight.
- **Too long alone in the dark:** dark, enclosed, nobody within 24 blocks. Over about 15 seconds
  reality wears thin: Level 0's fluorescent hum fades in from nowhere and the picture takes on a
  faint stale yellow, until you're through. Back in the light or in company it heals in ~5 s.
- **Film it:** the same with a Camcorder recording in your hand (right-click it: the camcorder
  look and REC overlay switch on). It finds the seams in about 5 seconds, the tape breaking up as
  it does.
- **A bed:** lie down in one and, a moment later, the view sinks straight down through it (the
  mattress, the floor, the ground) as the picture breaks up.
- **Suffocating inside a block** (sand or gravel falling on you) still works.

However you go, the picture fails like a video stream that has lost its keyframes
(`client/intro/NoclipTransition`, shader `shaders/core/datamosh`): the real world stutters and
crumbles into low-bitrate blocks, holds while Level 0 loads (no loading screen), then its blocks
smear along with the camera's motion while Level 0 bleeds through, until it snaps clean into the
Backrooms. About three seconds, no cut.

### The way out

One path runs through every level, each exit leading naturally into the next place:

```
Overworld --noclip--> Level 0 --stairwell--> Level 1 --stairwell--> Level 2 --EXIT door--> Level 4 --stairwell--> Poolrooms --drain--> Level 37 --deep pit--> Level 7 --sunken door--> Level 39 --mill window--> Level 94 --dark doorway--> Level 490 --the leap--> Level 9 --underpass--> Overworld
```

Every exit looks different, but every move from one level to the next is filmed the same way:
the same datamosh as noclipping in, the old place smearing into the new one. Only escaping back
to reality has its own ending (the tape stops).

Rare shortcuts stay as extra ways in and out: a sunlit archway in the Poolrooms skips ahead to
**Level 39**'s flooded tunnels; a moulded-clay front door on a Level 9 house opens onto
**Level 94**, whose flooded houses lead down to **Level 7** and whose castle throne leads back to
**Level 1**; a very rare chairlift in Level 9's Crimson Field climbs into the clouds and drops you
into **Level 39**'s town.

And out in the Backrooms' own nature (Level 9's woods behind the backyards, Level 9.1's Crimson
Field, Level 94's open hills) something can take hold of you and drag you into **Level 227**: after
a minute out there, a 1 in 180 chance every 5 seconds (about one pull per quarter of an hour). The
leaves rush toward one point, the ground groans and pulls, and you noclip onto a branch of the
tree (`world/Level227Entrances`). Level 227 has no exit: the tree shakes you loose by itself.

The stairwells (Levels 0, 1 and 4) are part of their level: the level's own walls inside, then a
wide spiral of shallow half-steps around a concrete core, lit for the first half turn and then
going down into the dark, until the picture breaks up and you are in the next level.

- **Level 0 (the Lobby):** Kane Pixels' yellow maze under a low ceiling (three blocks, just over
  your head) and a grid of troffers (the odd one missing, doubled or off the grid): rooms of odd
  sizes behind thick wall blocks joined by doorways that are rarely centred, walls that stop short
  of each other or of the ceiling, 1-wide slits, off-centre pillars and lone pieces of wall
  standing in the middle of a room. Corridors dogleg, run straight for 60 blocks, split into two
  lanes behind a thin wall and join again, or end in a plug with a slit to squeeze through. Most
  views are short, but now and then a row of rooms lines up into a 40-90 block vista of doorways
  ending at a blank wall, its far lights dead. Now and then a hall of square pillars, the film's
  half-fitted rooms (rows of half-walls and cubicles, window slots, a step down into a sunken
  passage, mannequins, a heap of old furniture) and whole wings where every light is dead. Here
  and there furniture has noclipped into the walls, floor and ceiling. About a fifth of the Lobby
  is the room maze: rooms of every size from 6 to 22 blocks behind thick wall masses, joined by
  2-deep doorways 2 to 6 wide that mostly line up, so you look through a tunnel of door frames
  into lit rooms far off; posts where four rooms meet, the odd big pillar, 1-wide slits, tunnel
  rooms, and dead ends where someone left crates, a case, a backpack or a heap of furniture. The
  light comes in districts: brightly lit office stretches, ordinary ones with pools of light and
  dark gaps, dim ones where half the tubes are dead, and abandoned ones where you need your
  flashlight. You always wake up under a working light. Arrows drawn on the walls and painted on
  the carpet point to the exit stairwell, but some of them lie, and in a few neighbourhoods,
  where the arrows crowd together, every one of them points the wrong way. Look for the EXIT sign
  over a wooden door between green and white hazard stripes.
  The building has aged unevenly: faded beige-yellow paper with a small, faint print, a worn,
  slightly damp tan carpet and fissured off-white ceiling tiles. Mostly it looks merely old, but
  here and there a whole stretch shows its years at once: water rings on the tiles, a leak stain
  running down from the ceiling, damp creeping up the wall from the carpet, grimy hand marks, a
  sagging or broken tile.
- **Level 1 (the "Habitable Zone"):** an endless underground parking structure crossed with a
  warehouse, big and high: bevelled 3x3 concrete pillars every 12 blocks under a 7-block ceiling.
  Each pillar has its number painted two blocks tall on a band in its sector's colour, and some
  carry a fire extinguisher cabinet, a convex mirror, a tube light, a garage sign (P1, headroom,
  no parking, 5 km/h, zone) or an EXIT/SALIDA/SORTIE/AUSGANG sign pointing nowhere. The floor is
  marked out like a real car park: stall lines and numbers, wheel stops, yellow lane dashes,
  one-way arrows, SLOW, speed bumps, drains, oil stains and tyre marks. Overhead run downstand
  beams both ways, a red sprinkler main with its heads, conduit, cable trays full of cable, big
  ducts, rusty pipes and fluorescent fixtures hanging on chains. Puddles lie about, some giving off
  a slow drifting steam; rebar juts from the cracked floor (it slows you and pricks). The halls
  change slowly by sector: blue-banded parking, a warmer yellow warehouse sector with
  hazard-striped pillars, crates and aisles of steel pallet racking (pallets, shrink-wrapped
  stacks, loose boxes, the odd collapsed one), and a red-banded decrepit one of cracked concrete,
  puddles and dead lights. Push-bar fire doors under green EXIT
  signs lead into walled-off **corridor blocks**: tight white corridors lined with small rooms
  (mattresses, supply lockers, crates), where nothing hunts you. Now and then a hallway of
  **storage lockers** opens off the halls: roll-up shutters, a few rolled up with supplies
  inside, something growling behind one or two, and a white door at the end that never opens. Car
  **ramps** lead down to a dimmer lower floor here and there. A stairwell leads down.
- **Level 2 (Pipe Dreams):** one long, straight, dim maintenance corridor of grey poured concrete
  that goes on forever, its walls buried in pipe chases (insulated mains, racks of steel, copper
  and painted service pipes, risers) under a low band of pipes and cable trays. Caged lamps hang
  every few blocks, alternating sides, so the line reads as pools of light receding into the
  distance. You can only walk forwards or back: every 24-48 blocks the corridor changes (a valve
  manifold, a pump bay, a low hot squeeze, a grating over a pipe trench, a bulkhead ring, a leak,
  a short dark stretch), and every 60-120 blocks a door or doorway on alternating sides leads into
  a dead-end maintenance room, ventilation room, boiler room, storage closet or safe room. Every
  320-420 blocks an EXIT bay opens at the side; its heavy bulkhead takes everyone within 6 blocks
  with you. Parallel corridors 48 blocks apart never meet. The concrete is marked by water and
  age: rust bleeding from old bracket stumps, spalled patches with rusty rebar, chipped kickers;
  in damp stretches (round leaks, and long patches where the cold pipes sweat) condensation runs
  down from the ceiling, the floor is wet, the wall bases carry tide lines and the slab blooms
  with white salts. Service pipes are paint-coded: green water, red steam, grey return, yellow gas.
  The ceiling reads in layers: grey steel I-beams cross under the slab every 6 blocks, and below
  them on hanger rods hang the cable tray (loose cable sagging out of it here and there) and the
  pipe runs, with gaps where you look up past them at the beams. Pipes never just stop: racks
  bend into the wall or pass into a pier through a bolted collar, risers turn into the wall, runs
  overhead turn up into the slab, and racks sit on strut with U-bolts. A valve manifold's header
  drops a branch into each of its valves (wheels or red-handled ball valves). Now and then a joint
  of an insulated main weeps: a drip hangs under it and a puddle spreads on the floor.
  The walls are numbered: a stencilled section number (2-A 03 up to 2-D 32, then round again) on
  the first pier of every stretch, in order as you walk east, and on the bulkhead rings. About one
  side room in six is a bigger **maintenance chamber** (`world/layout/Level2Chambers`, drawn as
  little text plans you can edit): a pump room (two pump sets under their header, starter panels,
  mercury lamps), a valve gallery (quarter-turn valves round three walls, each with a gauge or a
  label), an electrical room (breaker panels, transformer cabinets, cable trays everywhere), a
  flooded sump (a pit of black water with the dead sump pump standing in it, one grated walkway,
  leaks dripping) and the crew's break room (lockers, a kettle on the counter, the logbook on the
  table, a mattress in the corner, a hi-vis jacket left on its hook). The crews' shift logbooks
  lie open in them: right-click one to read its page. About one stretch in ten is an odd but
  grounded **detour** (`world/layout/Level2Detours`): a dog-leg where the corridor steps sideways
  round a massive concrete column, a duct crossing where the ceiling drops to 2.2 blocks for four
  blocks, a two-level stretch with a grated catwalk and railing over an open trench (a ladder
  down to the walkway at its bottom), or a section sealed by two concrete walls with locked steel
  doors and a narrow, low bypass passage round it behind the wall.
- **Level 4 (the Abandoned Office):** you arrive here through Level 2's EXIT door. Endless empty
  office floors on a stormy night: huge open-plan floors with square white columns, cubicle
  fields, corridors of wooden doors into small offices, glass conference rooms, break rooms,
  copy and server rooms, elevator lobbies whose elevators never come. A drop ceiling with
  fluorescent panels, most of them dead in the big open areas; long walls of dark rainy windows
  with radiators under the sills. Rain drums on the glass everywhere, the pipes rumble now and
  then, and every minute or two lightning flashes through the windows before the thunder rolls
  in. No monsters: it is a safe haven. Break rooms have **vending machines** (right-click: Almond
  Water or an Energy Bar until sold out) and **water coolers** (right-click: Almond Water, three
  times each). Some filing cabinets marked SUPPLIES are lockers. Safe rooms stand as little
  office boxes in the open floors. Behind one locked door with a brass "6", faint music plays.
  The way out is an **emergency stairwell**: look for the glowing EXIT sign over a grey steel fire
  door in a bright, cleared area; go down the stairs to reach the Poolrooms.
- **The Poolrooms:** dim white-tiled rooms and long water channels lit by a few tubes, glowing
  cyan pools with underwater lamps and moving light ripples, and now and then a tall atrium full
  of daylight. No monsters. The way out is the drain: a dark-tiled shaft in the middle of a deep
  pool, lit by lamps around its edge. Swim into it and it pulls you down into Level 37.
  Somewhere (about once every 300 x 300 blocks) a dim, dry room has a **sunlit archway** in its
  north wall: a round arch lined with fine white mosaic, warm daylight pouring out of it. Walk
  into the light: a shortcut that skips ahead to Level 39's flooded tunnels.
- **Level 37 ("Sublimity"):** reached down the Poolrooms' drain. Pristine cream-white mosaic on
  every surface, floors flooded ankle-deep in clear sea-green water, deep pools with no ledge and
  chrome ladders, forests of round pillars standing in the water, wavy walls and rounded corners, round arches
  and circular tunnels fading into darkness, domed rotundas with an oculus and glowing arched
  alcoves, a spiral stair of floating treads climbing a light well toward the sun. Warm daylight
  comes from nowhere: skylights and tall slit windows open onto a dazzling white void and throw
  sharp bright rectangles and stripes across the tile and water, while the long tunnels stay dark.
  Calm and quiet: gentle lapping, distant drips, no monsters, no events, and your **sanity slowly
  comes back** (+0.6/s) anywhere on the level. Backpacks on the dry pool decks and at the top of
  the spiral stairs hold almond water and calm pills. The way on is down: a round rotunda with a
  **pit of deep, dark water** in its middle (one every ~170 blocks; `/backrooms locate` points to
  it). Walk down the floating treads into the water and dive; a gentle current draws you down
  the shaft and out into Level 7.
- **Level 7 ("Thalassophobia"):** reached by diving down Level 37's pit (or, as a shortcut, down
  the stairs of a flooded house in Level 94, about one exit house in three). You come up in a
  small flooded sitting room (bookcase, coffee
  table, armchair, a radio and a crate on the dry landing) hanging from a concrete ceiling over an
  endless dark sea in a thick pale fog. Open a hatch in the landing and drop; the rope by the
  other hatch climbs back up (hold jump). Around the room is a shallow shelf of pale silt, then a
  sheer drop to a carpeted sea floor about 150 blocks down, with rock stacks, tar, scrap and the
  ribs of giant fish. You can hold your breath about ten times longer than normal here. The way
  out is a wooden door lying in a round stone platform on the sea floor, ringed by pillars and
  huge pipes, 150 blocks west of the room: swim into it and you come out in Level 39's flooded tunnels. No monsters,
  but something enormous lives in the water: you hear it groan far below, feel the water heave
  when it passes under you, and once in a while a vast dark shape glides past at the edge of what
  you can see. Your sanity drains in the water, faster the deeper you go; the room calms you. The
  sea is drawn like the pool water (below), in its own murky grey-green: a rippled surface that
  mirrors the concrete above and lets the pale shallows show through, and underneath a dim teal
  murk that turns black and closes in as you go down. Far off, waves slap against rock, the
  ceiling drips and the giant pipes thump.

- **Level 39 ("Megalophobia"):** you come up out of Level 7's sunken door into long, white
  marble tunnels, barrel-vaulted and flooded with still turquoise water, lit by light that comes
  from nowhere (the tunnels swallow sound). Walkways run along both sides of the water. The way
  back up: iron rungs in a pool of light at the foot of the wall, wherever a town path crosses
  over the tunnel, climbing to a hatch in the town above: a
  foggy night town of light-blue clapboard houses from every era (1800s saltboxes, Victorians with
  turrets, farmhouses, bungalows, Cape Cods, 1980s ranches), unpainted picket fences, dirt and rocky
  paths, hedges, bare trees, sagging power lines, the odd lattice telephone tower or abandoned
  park. All around it, colossal, a 1900s mill wall rises into the clouds: rows upon rows of bright
  windows, so big it has its own weather (it rains in the town now and then), and everything you
  do echoes off it for seconds. Loud noises can echo into an earthquake. Food and Almond Water you
  bring vanish when you arrive. The houses hold almond water on pantry shelves, in old fridges and
  in cellars, but the front doors are mostly locked or fake: punch through a rotten wall section
  (it takes a few seconds, and it is loud). Some houses are wrong (see Anomalous areas). Holes in
  the ground drop back into the tunnels. The way on is through the mill: walk into one of the
  glowing ground-floor windows that wakes up, and survive its grab for three seconds against the
  glass, and you noclip through it into Level 94.
- **Level 94 ("Motion"):** reached through a window of Level 39's mill (you come out at a
  crossroads), or through a rare clay front door on a Level 9 house (about one unlocked house in
  forty). Endless rolling hills of felt grass under a painted sky with fat
  cartoon clouds, winding roads, little cream houses with red roofs (some enterable, with a CRT TV
  showing static), house fragments on the hills, water towers, parked old cars and milk vans, siren
  poles that play a happy tune by day, and a town with a chess-king fountain. Far away a white
  cartoon castle stands on a see-through glass mountain; walk the spiral ramp up to it. Inside it
  turns into a real (and wrong) play centre: checker floors, a ball pit, a playground with a
  climbing frame, a looping upstairs hall, an animation studio and an empty throne room. Day lasts
  8 minutes (the last 30 s is dusk), night 3: at night the music stops and Smilers come out on the
  hills, so get inside a house (they can't follow you in; your sanity holds steady there). Ways
  out: houses whose doorway is pure black lead on to Level 490, flooded houses (knee-deep water,
  steps going down into murk) lead to Level 7, and the light behind the throne leads to Level 1.
- **Level 490 ("Penance"):** through a dark doorway in Level 94 you come out among crumbling ruins
  on floating islands of rock over a sea of storm cloud, at night, in rain (after Elden
  Ring's Crumbling Farum Azula). On the islands stand rings of pillars, arcade walls with broken
  tops, tower stubs, rubble, dead trees and open shackles; stone bridges with spiked iron railings
  climb in half steps between them, some broken off. Colossal chains, every link 34 blocks long,
  hang out of the storm into the clouds. Rocks and masonry float in the air; whirlwinds stand far
  off; billowing clouds roll below and hang in lumps overhead. Kilometres off hang other ruins on
  their own chains, and every few minutes one breaks loose and falls into the clouds. Pale,
  barnacled whales swim under the storm trailing fog, and sing; now and then one passes close
  overhead. Lightning strikes far off, its thunder as late as it is far; far chains jerk taut with
  a clank; the chains near you groan. The walk to the spire is about 400 blocks, past a ruined
  chapel you walk through, graves, gibbets with hanging cages, a fallen colossal statue, wayside
  shrines and a pilgrim's camp; silent Penitents in tall pointed hoods, chained at the ankles, kneel
  in chains, pray at the shrines and walk toward the spire with candles (some are gone when you come
  close), and the old pilgrim bent under his enormous pack talks, in a speech bubble, if you
  right-click him. Then a Gothic gatehouse and a bridge that gives way behind you, and the
  spire: a tower of dark Gothic stone about 275 blocks tall, stacked like a spine, its ribs pale,
  turrets and flying buttresses clinging to it, candlelit windows and a rose window glowing, hanging
  in four colossal chains. Climb it by a broken stair winding round the outside (gaps to jump,
  crumbled stretches, rotten planks), in through a half-way room with a radio, where the stair
  inside never ends until you go back down (something changes every time round), and out again
  round the outside to the top. From its crumbled top a broken bridge runs out to a
  colossal chain; climb the ladder up it to the collar and step off: the way on, to Level 9. Fall
  anywhere else (into the clouds, or a long fall onto something) and you wake at the last radio you
  used, unhurt. And something hunts you: a colossal shape of drifting smoke like the Mind Flayer,
  long spider legs fraying into tendrils, a crown of spines and a scatter of dim eyes, hard to see
  until the lightning shows it. On the walk you hear it scraping below the edges, then see
  it far off in the clouds when lightning flashes, then its hand comes over the edge for you if you
  stand about in the open (a roof or a lit lantern keeps it off). On the tower it rises slowly
  through the sky from far below the clouds after you, faster when you stop, slamming its hand onto
  the stair ahead and reaching for you if it draws level; inside the tower you are safe. At the top it holds the tower in its hands and stares at you, then floats in
  the sky watching as you climb the chain. If it catches you, you wake chained on a punishment
  platform behind you: pull at the chains (jump) to break free.
- **Level 9 (the Suburbs):** the last level, through the leap in Level 490. An endless suburb at midnight in thick fog that drains the colour
  from everything. Wet roads covered in leaves, old sodium streetlamps (most dead, some
  flickering), houses with blacked-out windows, some unlocked with dark rooms inside, and dark
  forest behind the yards. Follow the green EXIT arrow signs (a few lie) to a highway underpass
  and walk into the dark, back to reality.
- **Level 227 ("Megaflora"):** reached only by being dragged in out of the Backrooms' own nature
  (see above) or with `/backrooms enter level_227`. A tree the size of a planet hangs upside down
  over a black void, its roots reaching up out of the sky. You arrive in a misty rainforest that
  turns out to grow on top of one of its branches: a trunk about 1800 blocks across, a dozen main
  branches leaving it 560-680 blocks up and running out for thousands of blocks, gently downhill
  (walk outward and you slowly descend), splitting into sub-branches and limbs that end in tapered
  tips or broken stumps showing their growth rings. Here and there two branch systems have grown
  into each other or are joined by a thin bridging limb: the only ways across, so look for them.
  On top: 4-8 blocks of rooted soil, moss and grass with roots snaking over it and puddles in the
  dips, under a forest of giant trees 25-70 blocks tall (buttress roots, layered crowns, lianas
  you can climb, epiphytes, bracket fungi), shrubs, tall grass, giant ferns, fallen logs and snapped
  stumps. At the edges the soil thins to moss-streaked bark curving away into nothing, where purple
  Lestron grows; moss curtains, fern fronds and lianas hang off the undersides into the void. Bear
  caves open as mossy holes in the forest floor; hollow knots gape at the branch edges. Rare
  clearings of white ash mark the deadly sunbeams of the Sun over the Void. Mind the glowing
  GammaPali and the bark overhead (a Palette looks just like it). Everyone who comes in lands on a
  different branch, at least 1500 blocks from anyone else already there. Falling off a branch is
  death: below y 24 the void takes you. There is no exit: sooner or later the tree's motion
  noclips you to another level.
  Climb above the canopy or walk to an edge and the rest of the tree looms out of the grey-green
  mist (`client/Level227Far`, `Level227Atmosphere`): the fluted trunk like a wall of cliffs, the
  other branches and their forests fading into silhouettes thousands of blocks away, moss curtains
  under them, the roots fanning up out of the sky into a bright haze, and far above them the Sun
  over the Void, a tiny blinding white star in a wide iridescent glare, with a dozen bubbles racing
  round it on hair-thin beams of light, each mirroring another level. Below, the void goes black,
  with only faint mist near the branches. The Sun and its bubbles wander slowly across the sky:
  that is the tree drifting in the void. The mist is thick in the forest and thins out at the
  edges and in the open air. The far view is built once when you arrive (about 0.6 s in the
  background, ~34 MB of graphics memory) and freed ten seconds after you leave.
Every floor is endless; exits are spread out a few hundred blocks apart.

**Finding the exit** (subtle on purpose, `world/ExitHints`, `client/hint/ExitHints`): nothing on
screen points the way, but every level's ordinary exit can be tracked down three ways. Secret
ways (the sunlit archway, the clay doors, the throne light) are never hinted.
- **Listen.** Every exit gives off a faint sound of its own that fades in within about 40 blocks
  and comes through the walls: a draft up Level 0's stairwell, the hollow drone of Level 1's,
  Level 2's EXIT door buzzing, storm noise through Level 4's fire door, the Poolrooms' drain
  gurgling, water rushing down Level 37's pit, highway hum through Level 9's underpass, the dark
  breathing behind Level 94's dark doors, bubbles rising from Level 7's sunken door. Unlike the
  rest of the level's sounds, it never moves.
- **NightShot.** Earlier wanderers painted arrows in something that only the camcorder's
  infrared shows. With NightShot on, a few dark arrows appear on walls and floors around you, each
  pointing toward the exit, or down when it lies far below. Unlike the arrows drawn on Level 0's
  walls, they never lie. They thin out along the path: about one per dozen blocks of wall on
  Level 0, fewer on every level after it, and from Level 37 on they only show near the exit (Level
  9: a rare one within 50 blocks).
- **Torn notes.** About one backpack in three, and one crate or locker in five, holds a note
  from someone who came through before: a rough direction and distance to the exit from where
  the container stands. One note in five points the wrong way. Some mention the other two hints.

**Pool water (Poolrooms, Level 37 and Level 7's sea)** is drawn by the mod itself instead of vanilla's flat blue
water: it takes the colour of the lit room behind it, tinted from clear shallow mint/cyan to deep
green/teal the more water you look through, bends the tile floor with slow ripples, throws moving
caustic light patterns onto the floor and lower walls, mirrors the walls and pillars (Fresnel:
clear looking down, mirror-like at grazing angles) and catches sun glints in Level 37. Under water
the view stays clear and tinted, with caustics and soft light shafts. Level 37's ankle-deep
flooding is the `shallow_water` block: you wade through it (wading steps, a little drag) instead
of swimming. In both levels you can walk or run through shallow water (in the Poolrooms' knee-deep
shallows too, which vanilla wouldn't allow): your steps become splashy wading sounds made of
bubbles, sloshing and spray, and the water slows you down a little. Only deep pools make you swim.
With an Iris shader pack active, vanilla water is kept (the pack draws its own).
Sodium/Embeddium-style renderers that skip vanilla's fluid renderer are not covered.

### Supplies

Containers are filled the first time anyone opens them, from a loot table for the floor you are
on (`data/backrooms/loot_table/supplies/<crate|backpack|locker>/<level>.json`, falling back to
`default.json`). Breaking one spills what is left inside.

- **Wooden Crate** (27 slots): mostly food: canned food, backshrooms, bread, almond water, energy bars.
- **Backpack** (9 slots, lying in corridors): few items but the best ones: pills, bandages, energy
  bars, and the best chance of a camcorder battery.
- **Supply Locker** (27 slots, metal cabinet): bandages, calm pills, energy bars, some food.

Each floor leans its own way: Level 0 has more almond water, Level 1 more batteries, Level 2 more
bandages, the Poolrooms more almond water and pills, Level 9 ordinary kitchen food (cookies,
apples, bread). Camcorder batteries stay rare everywhere.

Light and route-marking supplies come from their own roll, so they never crowd out the food or
the batteries: chalk is common in Level 0, 1 and 4 crates and backpacks (rare elsewhere),
glowsticks (1-3) turn up in backpacks and lockers on every level, and flares are found in lockers
and crates on Levels 1, 2, 4 and 9 (rare elsewhere).

| Item | What it does |
|---|---|
| Canned Food / Backshroom / Bread | Food. 1 can in 20 has gone bad; backshrooms can make you sick. |
| Almond Water | Heals a little and clears your head (Poolrooms hallucinations). |
| Energy Bar | 3 hunger, eaten fast, and refills your sprint energy at once (even when out of breath). |
| Calm Pills | Hold right-click for 1 s: restores 25 sanity. |
| Bandage | Hold right-click for 1.5 s: heals 2 hearts plus Regeneration I for 5 s. |
| Camcorder Battery | Right-click to swap in a fresh battery. |
| Chalk | 64 marks per stick. Right-click a wall, floor or ceiling to draw the current symbol in white chalk; sneak + right-click (anywhere: air, wall or mark, nothing gets drawn) to switch between arrow, cross and circle (the action bar says which). On a wall the arrow points towards the edge of the block you click nearest to (left, right, up or down); on the floor and ceiling it points the way you face. Drawing on a mark replaces it; one punch wipes it off. Everyone sees the marks and they stay in the world. They look nothing like Level 0's own painted arrows (dark brown and solid), so you can tell yours from the level's lies. |
| Flare | Right-click: strike it and throw it a short way. It burns bright red-orange for 75 s (even underwater), sputters out, and smokes for a while. Smilers won't stay in its light, the Bacteria see you in it like under a lamp, and Level 9's Neighborhood Watch comes to look at it. Can't be picked up. |
| Glowstick | Right-click: crack it and toss it at your feet. A soft green glow for 8 minutes (it fades over the last 30 s), good for marking your way together with chalk. Right-click a lying one to pick it up again; it keeps the time it has left. No effect on monsters. |
| Raw / Cooked Boar Meat | From Level 227's boars (3-6 each). Raw: 3 hunger and often Hunger, sometimes nausea. Cook it in a furnace, smoker or on a campfire: 9 hunger, the biggest meal there is. |
| Lestron | Purple, sage-like herb at the very edges of Level 227's branches (right-click the plant for 1-2 sprigs; it grows back in about 8 minutes). Used to create ████████. |

### Sanity

Your heart rate (top-right in the Backrooms) is your sanity: 70 BPM when calm, up to
160 BPM when you're losing it. It starts at 100, is saved when you log out, and you respawn at 70.
The readout only shows when it matters: for a few seconds after it changes, and faintly all the
time while it's 100 BPM or more (amber below 50 sanity, pulsing red below 30).

- **Drains** slowly all the time in the Backrooms, faster in the dark (with your flashlight off),
  when nobody is within 24 blocks, when you see a Bacteria (much faster if it's chasing you), a
  Watcher, an Observer or a Smiler, and during blackouts.
- **Restores** in a safe room (fast), next to a friend in the light (slowly), out in the real world,
  and with Almond Water (+35) or Calm Pills (+25). Creative/spectator players aren't affected.
- **When it's low** (mostly only you notice): below 70 you hear whispers, knocks and footsteps
  right behind you; below 50 the camcorder picture darkens at the edges in time with your heart,
  loses colour and a black figure stands at the edge of your view until you look at it; below 30
  you hear your heartbeat and the readout blinks red, and the Bacteria find you more often; below
  15 the figure sometimes breathes in and charges at you down the corridor. None of it is real.
- **Shared panic** (`player/SharedHeartbeat`): while you hear your own heartbeat (below 30), other
  players within 12 blocks hear it too, faintly and coming from you, at your heart rate. Walls muffle
  it like any other sound. Spectators don't make or hear it, hiding doesn't stop it, monsters can't
  hear it, and it costs the listener no sanity.

### Safe rooms & checkpoints

Every level has small safe rooms, marked by a green **SAFE** plaque over the doorway. Inside there
is a warm lamp, a crate, a cot and an old field radio on a little table.
About six in ten 128x128 areas have one: a panelled office room hidden in the Lobby maze, a
concrete room with a metal door frame in the Habitable Zone, a carpeted side room off the Level 2
corridor, a dry tiled room on a walkway in the Poolrooms, and on Level 9 a house with its porch
light on and the sign over the front door.

- **Safe:** no monster spawns in a safe room or within 12 blocks of one, the Bacteria never walks
  in (reaching one mid-chase counts as escaping), and Watchers and Observers leave you alone while
  you're inside. The camcorder shows a small **SAFE ROOM** tag at the top left for a few seconds
  when you walk in (and faintly with the `H` overlay on).
- **The lamp is battery-backed:** like the exit signs it stays on in blackouts and never flickers
  (with the Veil lights; without Veil a blackout darkens everything).
- **Checkpoints:** right-click the radio to save your checkpoint (static, a chime and
  **CHECKPOINT SAVED** on the camcorder). If you die on that level you wake up on the floor in front
  of the radio. Dying anywhere else still sends you back to the real world, and leaving the level
  through its exit forgets the checkpoint. Clicking the radio again just saves again.

### Hiding

You can hide in furniture that is already there: a **Supply Locker** (or the filing cabinet that
is really one), an **office desk** (Level 4's offices and cubicles, Level 0's furniture heaps,
Level 94's drawing desks), a Level 39 **wardrobe**, or under a **bed** (Level 9 and Level 39
houses). Upside-down furniture doesn't count.

- **Sneak + right-click** to get in; **jump** to get out (you step out in front). A plain
  right-click still opens a locker. One person per hiding place, and spectators and creative
  players can't hide.
- **Inside** you can't move, hit or use anything, and your flashlight stays off. The view sits in
  the furniture (up near the vents in a locker, at eye height in a wardrobe, low under a desk, at
  floor level under a bed) and looks out of its open side, turning about 70 degrees each way. The
  camcorder shows **HIDING** under where SAFE ROOM goes. Other players see nothing there.
- **Hold your breath:** hold sneak, up to 10 s (your air bar runs down). Let go after more than
  6 s, or run out, and you gasp out loud. While you breathe normally, a monster within 3 blocks
  has about a 1 in 4 chance each second to hear you and drag you out.
- **Monsters** (the Bacteria, Hounds, Clumps, Smilers, Facelings, Watchers, Skin-stealers) can't
  see, hear or chase you while you hide, unless one saw you get in (it had you in sight in the last
  2 seconds): that one comes over and pulls you out, then attacks. Otherwise it searches where it
  last saw you and gives up as usual. Nothing new spawns for you while you hide, Wretches aren't
  frightened by you and Level 39's windows don't see you. A monster within 16 blocks still makes
  it nerve-racking: your sanity drains a little.
- **Pulled out:** opening a hiding place someone is in (a friend right-clicking it) drags them out
  too. You're shoved out in front and can't hide again for 3 seconds.
- Breaking the furniture, logging out, dying, changing level or being teleported just ends the
  hiding; your body was waiting in front of the furniture the whole time, so you never end up
  stuck in a block. Getting in and out creaks a locker or wardrobe door or scuffs along the floor
  (`tools/anomaly_assets/hiding_audio.py`), quietly.

### Things that happen

- **The Bacteria (Level 0 only):** there is only ever one, a tall black wire figure. When it has
  lost track of everyone it doesn't stay lost: it turns up again somewhere out of sight 30 to 50
  blocks from one of you, and if you leave it far behind it catches up after a while. It only
  knows where you are if it sees you or hears you
  sprint. In a lit room it spots you from about 24 blocks; in the dark only from about 8 (crouching
  makes both shorter, your flashlight doesn't matter). When it spots you it freezes for a moment and
  draws in one long, wrong breath, then chases. Break line of sight: it runs to where it *last saw*
  you, searches for a few seconds (you hear it sniffing), and when it gives up you hear a slow sigh
  moving away. It is quiet: wet, slow breathing and soft clicks, never a shriek. With
  NightShot on you can see it even in total darkness.
- **Blackouts:** the lights fade out over two seconds. Only flashlights work, and the Bacteria come
  out three times as often, but they can't use the lamps to spot you either.
- **Smilers (Levels 1 and 2 in blackouts, Level 1's dead zones, Level 9's dark streets, Level 94 at
  night):** two glowing eyes and a huge glowing grin in the dark, wrapped in a faint shroud of
  churning dark smoke you mostly notice against a lit wall or in NightShot. In Levels 1 and 2 one
  comes when the lights go out, sometimes a second from the other side 10 seconds later. Level 1
  has no Bacteria: it belongs to the Smilers, and they also wait in its dead zones where the power
  is out for good; linger in the dark there and one comes out of it (in Level 9's streets between
  the lamps too, more rarely). Step back into the light and it is gone a few seconds later. Level
  0 has none. It
  makes no sound except a faint breath when it's a few blocks away; if it reaches you, you die.
  Hold your flashlight on its face: it squints, backs off and burns away after about 1.5 seconds
  of light in total (bursts add up). About one in six has a wider grin and takes twice as long.
  Weapons go straight through it. When the lights come back on (in Level 94: at dawn), or you reach
  a safe room or a house, they're gone.
- **Flicker**, **alarm** (red rotating emergency lamps), **intercom** announcements that get
  creepier, **lightning** in the garage, the rare **twist** in the pipes (the corridor slowly leans a few degrees), distant
  knocks and creaks.
- **Hallucinations (Poolrooms):** stay in the water too long and the picture starts to swim. Then
  you see your friend (or yourself, if you're alone) waving at you. Only you can see them. They
  walk away and want you to follow... always away from the drain. Get out of the water and dry
  off, or drink Almond Water, to clear your head.
- **The Neighborhood Watch (Level 9):** huge floating veined eyes that drift over the streets,
  sweeping a searchlight. If one sees you, it stares, then charges a beam: stay in sight and you
  are turned to dust. Break line of sight (houses, trees, going indoors). They come to look when
  you sprint nearby or shine your flashlight, and they ruin your camcorder picture and battery
  when close. Keep quiet.
- **Something watching (Level 9):** a tall dark figure that stands where the light doesn't reach.
  Look straight at it and it's gone.
- **Level 9 events:** streetlamps die in waves; the fog rolls in thick while something enormous
  rumbles somewhere out in it.
- **Level 39's town:** rarely, a **Hound** prowls the paths: a starved human on all fours, long
  black hair hiding a mouth full of needle teeth. It growls before it charges (hide when you hear
  it); hold its gaze for two seconds and it backs off for a moment, but it learns. Hit it and it
  turns furious. **Facelings** wander between the houses: blank, mannequin-smooth faces, touching
  where their faces should be. Most ignore you. A few walk in packs and run at you all together
  the moment one of them sees you.
- **Level 39's tunnels:** now and then a lone Hound hunts down here (never a pack; it gives up
  quickly once it loses you). **Clumps**
  hide in the ceiling drains and side alcoves, only a few arms dangling as bait; they hunt by ear
  (sprinting and splashing carry far), knuckles crack just before one strikes with a six-block
  arm that reels you in. Go quiet and still when you hear that. **Wretches**, people far gone with
  Roth syndrome, moan at the water's edge; they flee from light and noise but lash out if you
  corner them. **Skin-stealers** stalk anyone who is alone, calling out in a garbled voice (sometimes
  your own last chat line); look straight at one and it slinks away, walk up to it and it charges.
- **Level 227's big animals** (`entity/L227Bear`, `L227Doe`, `L227Boar`, placed by
  `entity/Level227GrazerSpawner`; nothing appears in your first minute there):
  - **Bears** sleep in the bear caves, one to a cave, lying curled up facing the mouth: a long,
    shaggy, moss-grown body on **six legs** and **fifteen eyes**, all shut. They are not aggressive
    unless provoked, but noise wakes them: walking close, sprinting or jumping nearby, anyone right
    next to one. Crouch past and don't linger. The warning is the eyes: it groans and a few of them
    open and look around; back off and they close again. Wake it fully, or hit it at all, and it
    heaves itself up with every eye open (a faint eyeshine in the dark), roars and comes for you,
    a little slower than a sprint, then rears up and brings four paws down: about 8 hearts on
    Normal, and you are slowed for a moment. Lose it and it lumbers back to its cave and lies
    down. Kill one and its cave stays empty for 10 minutes.
  - **Does** graze in the open forest: pale, slender deer with a long black scar from the nose,
    over the head and down the spine to the tip of the tail. They never attack. When one sees
    you she lifts her head and stares, and the scar opens a crack. Come within 4 blocks of a doe
    (any doe, even from behind) and you noclip straight down through the branch you stand on: the
    tape glitches and you are falling out of its underside into the void. She grazes on.
  - **Boars**, the largest animals, five blocks long, mostly lounge on their backs in the
    undergrowth, legs in the air, snoring; now and then one rolls over, gets up, roots about with
    its tusks and lies down again. They ignore you, but every hit sets off three seconds of
    thrashing: it squeals, spins and kicks, and its four tusks gore anyone within about 3.5 blocks,
    again and again (about 4.5 hearts a hit). Hit, step back, hit again, or use a bow. A boar drops
    3-6 **Raw Boar Meat**; cook it (furnace, smoker or campfire).
- **Level 227's hunters** (`entity/L227Wolf`, `L227Stag`, `L227Squirrel` with `SquirrelSwarm`,
  placed by `entity/Level227PredatorSpawner`; nothing hunts you in your first minute there). They
  attack on sight; crouching in the undergrowth shortens how far they see you.
  - **Wolves** live only in the **higher sections** of the tree (above y 520), in packs of 3 to 5:
    big, gaunt, shaggy, with a long snout that splits down the middle and opens **sideways**, two
    rows of teeth along each half. The first to see you stops and **howls**, and the rest of its
    pack comes running. They spread out in a ring around you, slowly circling, and dart in **one
    at a time** to bite (about 2.5 hearts) and spring back out. Break out and run and they chase,
    a little slower than a sprint but they never tire. **They never go down:** get below the high
    sections (walking outward along a branch goes downhill) and they stop at the line, pacing and
    howling, then give up. A hurt wolf flinches out of the ring; a badly hurt one runs, and once
    half the pack is badly hurt the rest lose their nerve. About one pack every 2-3 minutes while
    you are up high and none is near.
  - **Stags** (the male deer) walk the forest anywhere: bigger than a horse, very long pale horns,
    five thin tails and **red eyes** that glow in the shade. When one sees you it throws its head
    up, then **bellows** and paws the wood with its horns lowered for about a second and a half;
    then it charges at twice your sprinting speed and runs you through (about 5 hearts, thrown
    off the horns and slowed). The charge is nearly a straight line: step aside late and it
    thunders past, then has to turn and set up again. Stand in front of a tree trunk and sidestep:
    if it hits the tree its horns stick in the wood for 2.5 seconds. It never runs off a branch
    edge. One at a time, about one every 3-4 minutes while none is near.
  - **Squirrels** are very rare and the worst thing on the tree: small, fast, fur glowing a
    sickly fluorescent green. They come as a **swarm** (at most once per 20 minutes for a player,
    after at least 4 minutes in the level, about once per 40 minutes on average, and one swarm in
    the world at a time): first a wave of chattering from every side, then glowing shapes pouring
    in out of the undergrowth. They **circle** you in a ring that tightens over about 11 seconds,
    some sitting bolt upright to stare. **That is your chance: break out through the ring and
    sprint.** They chase a little slower than a sprint and never follow you more than 36 blocks
    from where the ring was. Stay, and they leap onto you, up to ten at a time, each biting about
    half a heart a second and weighing you down (slowness). Hit one and it falls off; three hits
    kill one; another always comes. A swarm is a hundred squirrels, but only 40 are ever there at
    once.
- **Level 227's plants that kill** (`registry/Level227Blocks`, `world/Level227Hazards`,
  `entity/VenusFlytrap`, placed by `entity/Level227FloraSpawner`):
  - **Giant Venus flytraps** lie open in patches of tall grass: two rounded, ragged lobes spread
    almost flat on the ground (about 3 by 4.7 blocks, under a block high), dull green-maroon
    under moss, dead leaves and grass tufts, with short thin teeth along the rim. The tall grass
    grows right up to it, so you hardly see it. Step into the mouth and it **snaps shut** round
    you in a quarter of a second (a flash of red inside), a stalk shoots up and lifts you five
    blocks into the air, and it starts to **digest** you, slowly: under a third of a heart a
    second at first, a little more each second, about 20 seconds from full health. **Struggle:**
    press attack or jump. 12 presses break you out at first, rising to 18 by the end (they only
    fade if you stop pressing); you drop out the side. Shift doesn't get you out. A friend
    hitting the stalk's base helps (each hit counts as 4 presses). Once it has let go it sinks
    back down and lies open again ten seconds later.
  - **GammaPali**, a small plant with glowing yellow-green bulbs on the bark of the branches, often
    on their undersides. Its glow is a toxic gas: a faint glowing haze drifts round it, and it hisses
    now and then. Within about 2.5 blocks you are poisoned at once and your air bubbles drain in two
    seconds; when they are gone the gas chokes you (about three seconds more to death). Step back
    out of the haze and you breathe again.
  - **The Palette** looks like the bark it clings to (look very closely: faint overlapping scales),
    on the undersides of branches and overhangs above where people walk. Pass under one (within
    about 3 blocks to the side, up to 10 below) and it **creaks**; 0.8 seconds later it drops,
    aimed at where you are heading. Run when you hear the creak. It crushes harder the further it
    falls: a 4-block drop takes about 9 hearts, from 5 blocks it kills. Touching pieces drop
    together, and a fallen Palette lies where it landed, harmless.
- **Echo quakes (Level 39):** booms from the superstructure echo four times off the walls and can
  build into a quake: the ground shakes, dust and coal ash fall, windows rattle. Slamming doors,
  bells, hard landings and breaking walls add up, and enough noise sets one off.
- **Indoor rain (Level 39):** the superstructure's own clouds rain on the town for a few minutes
  every quarter of an hour or so.
- **Separate realities (Levels 0, 1 and 4, with friends):** rarely, about once in 25 minutes that
  two of you spend 3 to 12 blocks apart (outside safe rooms, not hiding, no blackout or alarm),
  one of you sees the room differently. A doorway you just came through is walled up, a door
  stands in a wall that has none (it never opens), or the lights around you are dead while your
  friend's are on. Walls and doors change while you aren't looking; there is no sound and no
  effect, your friend simply says "what door?". Walk up to each other and it snaps back, with a
  soft thump in your ears. It also ends after a minute or two, or if either of you wanders off,
  dies, hides or leaves. The same two people get it at most once every 10 minutes. Nothing is
  changed in the world: the server only shows that one player different blocks
  (`world/RealityDrift`), so a restart always leaves the real room.
- **Battery:** lasts 40 minutes; flashlight and NightShot drain it faster. It recharges in the
  real world.
- **The sunbeams (Level 227):** the Sun over the Void shines straight down through a few gaps in
  the canopy: blinding white shafts of air with dust motes drifting up through them and the air
  shimmering, the ground under them bleached to ash (`client/Level227Sunbeams`; with Veil the
  forest around a beam is lit by it). Walking toward one, the light dazzles and its hum climbs.
  Step in under open sky (a leaf, a branch or a block over your head protects you) and you get one
  second of warning: the hum rises into a choir and everything goes white. Stay, and over the next
  two seconds you turn to white ash and die; get out in time and you live, badly hurt. Armour,
  shields and potions don't help (`world/Level227Sun`). Creative players only see it.
- **The tree moves (Level 227):** every 4 to 10 minutes the whole tree lurches in the void: one
  vast groan for everyone in the level, leaves raining down, the camera swaying very slightly. And
  after a random 10 to 30 minutes in the level (counted for each player, from arrival), the tree
  shakes you loose: six seconds of a groan building from below and the world shuddering harder and
  harder, then you noclip (the same filmed transition as coming in) to a random level, most often a
  dangerous one (Level 39, then Level 9, then Levels 0, 1 and 7...; `world/Level227Instability`).
  That is the only way out.
- **The sun speaks (Level 227):** a faint choir of whispers in your head, louder near a beam and
  in the higher sections. Very rarely (every 15 to 35 minutes, near a beam or high up), it says one
  short line in the action bar.
- **Fallen bubbles (Level 227):** about once a quarter of an hour, one of the bubbles that circle
  the sun drifts down by a branch edge, a few blocks out over the void at head height: an
  iridescent sphere three blocks across, its surface rushing with the colours of another level (a
  yellow hall, a tiled pool, a night street, green hills...). Jump into it and you noclip to that
  level; miss, and there is only the void. After three minutes it sinks away (`entity/VoidBubble`).
- Dying in the Backrooms: *you can't escape.*

### Anomalous areas

Every level has its own strange places, taken from that level's own lore on the Backrooms wiki
(nothing borrowed from another level). They are built into the map where they belong, rarer than
the ordinary rooms, and most of them only change when nobody is looking. Their positions come from
the world seed (`world/anomaly`: `AnomalyGrid` plans them, each level's layout builds them,
`AnomalyService` runs their behaviour on the server and tells clients which ones are near).

**Level 0**
- **Red Rooms:** Sometimes the wallpaper starts peeling, there's crimson underneath, and the carpet grabs at your shoes. Turn the corner and you're in darkroom-red rooms that hum like a klaxon, and the way you came in is gone. The rooms keep count: every time you come round, the hum drags low for a moment and more of the walls have tally marks on them. Count the loops; Level 0 has its own way of letting people go.
- **Blackout Zones:** Patches of the maze where every light is dead, the walls are rough and the carpet is flooded. Step in and the lights behind you die in a wave rolling away from you; a minute later (or once you're out) they come back from the edge inward. The buzz you hear seems to lead the way out, but it never stays in one place, though the longer you're in, the more honestly it points.
- **The Manila Room:** A very rare brown wall with a wooden door. Behind it is a quiet beige room with a table, two chairs, supplies and notes from volunteers, and a piano playing from nowhere. It calms you faster than anywhere else, the lights never go out in it, and if you come back later someone will have put something in the cupboards you emptied. Sign the guestbook. Read the notes: the way to Level 1 is close.
- **Shifting pillar halls:** Big bright halls full of pillars that are never quite where they were when you look back. You'll hear them settle behind you, and sometimes half the hall is on a different grid from the half you were watching. Mark a pillar with chalk and look away. Only the pale arches in the middle stay put.
- **Pit rooms:** You hear cold air breathing up through the walls before you see it: a big room whose floor is a grid of square shafts with narrow carpeted walkways between them, and a wooden door on the far side. The light dies two blocks down. Don't fall in. You will land, hurt, somewhere very far away, under a hole in the ceiling.
- **Hidden rooms:** Some of the big blocks of wall are hollow. Look for a one-block crack with a kink in it, a doorway piled with boxes that has a gap at one end, a missing ceiling tile with a ladder going up into the plenum, or (only by flashlight, on the dark side of a block) a low gap in a corner. Each room has someone's notes, supplies they left for the next person, and something about it that isn't right. The one above the ceiling has a radio that saves your checkpoint.
- **The lights, locally:** Now and then, never more than once every few minutes, the lights do something to you alone: a room's tubes stutter out as you walk in and come back when you leave, the tubes behind you in a corridor die one by one, or a lit district far down a long view goes dark while you watch.
- **The Atrium:** A big hall whose low ceiling suddenly stops: above the middle, a black shaft rises out of sight, with warm pendant lamps hanging in it on long chains, and a balcony runs all the way round the void up there. You can get up. One alcove has no light, only a dead emergency lamp, and a steel ladder going up into the dark. Up on the balcony someone left supplies and scuffed arrows on the carpet, and they point the true way to the nearest exit. Something stands at the railing looking down. Don't be surprised if the hall below goes dark while you're up there.
- **The Shaft Hall:** A wide, low, almost dark hall whose ceiling is full of holes. Each one is a tile-lined shaft going straight up, and a few have a light burning far up at the top. You hear things up there now and then, and once in a while a ceiling tile comes loose and falls the whole way down. Mind the broken tiles on the carpet.
- **The Front Office:** You'll hear it before you see it: an alarm and a red light turning in a huge dark room, around a block of yellow wallpaper with a door and a window drawn on it, and a row of empty chairs facing the drawing. The PA sometimes asks you to come to the front office. If you go while you're expected (or simply arrive at the right moment), the alarm stops and there is light under the drawn door. Walk into it. Behind it is a small warm reception office that can't possibly fit in that block: supplies, Almond Water, a radio that saves your checkpoint, and another drawn door under an EXIT sign that really is the way out.
- **Findability:** One of these three set pieces is built in (nearly) every 256x256 square of the map, so together with the pit rooms and pillar halls there is always a big anomalous building close by (measured over four seeds: median 39 blocks away, 99% of places within 116 blocks, the worst spot 183).

**Level 1**
- **Crate halls:** In some of the open garage halls, aisles of steel pallet racking stand in a brighter, better-lit hall. Wooden crates turn up there: you hear something settle behind you, and there is a crate that wasn't there before. When the lights fail, the crates are gone; when the power comes back, new ones are waiting. Not everything inside is worth taking.
- **Painting rooms:** Standing alone in a hall, a small closed room is covered in crude paintings and drawings. Look away, or wait for the lights to go out, and they are not the pictures you remember, if they are there at all.
- **Fog patches:** Here and there a low, sourceless fog lies along the floor, the air turns grey and damp, and puddles of condensation gather and dry up again in places you weren't looking. Listen for the drips.
- **The Corrupted Corridor (Level 1.1):** Very rarely, a doorway in a garage wall opens onto a long white-cinderblock corridor with a blue tiled floor. The further you go the worse it gets: the colours go wrong, the same stretch of hallway seems to repeat, the dark swallows your light, and the walls turn black and white and won't hold still. At the very end, a light beckons.

**Level 2**
- **Series circuits:** The caged lamps are wired in series along the corridor. Now and then a circuit gives out with a sharp pop, and a whole section of the line goes dark at once (a gap opens in the row of lights ahead), sometimes for nearly a minute, before the lamps stutter back to life. Sometimes a fault runs down the line: three sections pop out one after another, toward you or away. A dead section is dark but never blind: the floor edge, the walls and the pipes stay faintly visible in a cold blue-grey.
- **Industrial lighting:** No two bulbs are alike: new ones burn a little whiter, old ones dimmer and browner, and a few failing ones sag now and then. Machine areas hang cold mercury lamps. Beside bulkheads, chamber doors and exits, red or amber battery emergency lamps wait on standby and click on when their section's circuit dies or the power fails. Wet floors, puddles, sweating pipes and bare metal catch the lamps in small highlights.
- **Brownouts:** Some sections never recovered from the Blackout. Almost every bulb is burst, the machines are silent, and only a few violet emergency lights still glow. Burst pipes have left pools of thick black sludge that clings to your boots.
- **Hot zones:** Behind a wide opening off the corridor, some boiler rooms run hot: faulty machines whir and steam hisses from the walls on the way in. The heat slows you down, wears you out and makes you hungry fast, and if you stay too long it starts to hurt.
- **Void doors:** At the back of a small hall off the corridor stands a door with a pitch-black window. It opens onto an endless, silent, lightless space where the doorway behind you is the only light. Don't lose sight of it.
- **Looping hallway:** A few office doors, each at the back of a small hall off the corridor, open onto a long, plain, fluorescent-lit hallway. However far you walk, you keep passing the same door you came in by.
- **Oddities:** Rare, quiet things that are simply wrong, if you notice them (`world/layout/Level2Oddities`). A **valve gallery** where every single valve has been turned the same way, all shut, yet water still rushes through the header overhead. A yellow gas **pipe** that rises from the floor and bends into one wall, and straight across the corridor comes out of the other wall upside down: high up, bending into the ceiling, its label below it; knock on one and the other answers. A run of corridor where **the section number repeats**: three or four sections in a row stencilled with the same number, each with the same dead bulb above it and the same chalk cross some earlier visitor drew underneath to check. A steel **door to nowhere** that opens onto bare concrete; the air in front of it is warm and something breathes softly behind it. And a **pressure gauge** at eye level whose needle reads your own heart rate (BPM / 10 on its dial) and ticks with every beat. The shift logbooks mention most of them.

**Level 4**
- **Looping stairwells:** Grey stair cores stand in some open-plan floors. However far you climb or descend, the sign on the landing always says 4, and somewhere far away you hear footsteps that never arrive. Once in a while the stairs let you out somewhere else.
- **The Window:** Most windows show the storm, but very rarely one is clear: a blurry, sunny place that feels like somewhere you've been. If the panes around a window are taped over with cardboard, there's a reason. Don't get close, and don't listen.
- **Vanishing elevators:** In some lobbies one elevator stands open and lit. Look away and it may not be where you left it. Step in and it will take you somewhere that looks exactly like here.
- **Level 4.3, "The Cubicles":** A dim white series of hallways leads to an endless cubicle field in white fog, full of the sounds of people working whom you never see. The longer you stay, the harder it is to leave. Walk back the way you came.

**The Poolrooms (and Level 37's water)**
- **Sound in the pools:** In the Poolrooms and Level 37, footsteps and voices die out a few blocks away and everything sounds muffled; only the water stays clear. Every so often the water grows loud and close, drowning out everything else for half a minute.
- **Dark corners:** Some lit rooms have a corner that no light reaches: a small nook of dark, ankle-deep water behind a tiled screen, with faint chanting drifting out of it. Stay inside too long and the light drains away, and you may find yourself somewhere that looks exactly the same, but red, where the water holds on to you and the chanting never stops. The only way on from there is down a drain.
- **Stair pits:** Some rooms are one deep, dark flooded shaft, with a staircase running straight down into the water. Down there your breath runs out fast and currents drag you around. The stairs are always the way back up.

**Level 37**
- **Void openings:** In a few slit-window walls two slits have merged into one wider gap above a knee-high ledge, with dazzling white right behind it. The closer you get, the brighter and louder it grows, and the worse you feel. Those who climb in and stand in the white wake somewhere dark in the Lobby, shaken.
- **Dark openings:** Rarely, a dim corner of a pool deck holds a round arch framed in dark tile and filled with absolute darkness, breathing cold air. Walk into it and the world fades to black; you come back out in the Poolrooms.
- **Looping tunnels:** Some of the long dark round tunnels don't end. Keep walking and you keep passing the same stretch, the same drip, while the light fades a little each time. Turn around and you walk straight out; persist and it eventually lets you through.
- **Healing water:** Level 37's warm water soothes. Standing or swimming in it slowly heals you and brings your sanity back, fastest in the deep pools; dry tile only calms you a little.

**Level 9**
- **The Mangled's fog:** Sometimes the air turns ice cold before the fog rolls in, and the streetlamps go silent. Somewhere down the street a black pillar rises into the sky, and booms roll out of the fog. When the fog lifts the pillar is gone, and the road where it stood has a crater in it.
- **Clipped houses:** Now and then two houses have grown into each other. Walls run through the middle of rooms, furniture sticks out of walls, and there are two front doors. Some of the walls in there aren't quite solid.
- **Twin houses:** Two identical houses stand side by side. Leave one by its back door and you may find you aren't where you think you are. The front door always leads out.
- **Grass walkways and Level 9.1:** Rarely, a paved path runs between two houses and on into the woods, getting colder as it goes, to a clearing of tall grass. Stand in the middle of the clearing and you are in the Crimson Field: endless grass at night under a red glow that pulses on the horizon. Don't walk toward the glow. To get home, follow the path back to where it started; the rare foxhole hatches in the ground lead elsewhere, one way only.
- **The chairlift (side entrance to Level 39):** Very rarely, somewhere in the Crimson Field, you hear a cable creaking before you see it: a small frozen lift station and steel towers marching off into the fog, each taller than the last, with empty frosted chairs creeping along the line. Sit in the chair waiting at the station and it carries you up into a white-out; you can still hop off in the first second, after that the bar stays locked. Where it lets you go, you fall through the clouds into Level 39.

**Level 94**
- **Furniture drift:** The closer you get to the castle, the less of the houses is left: whole houses on the far hills, single rooms nearer in, and closest of all only heaps of furniture, which turn towards the castle when nobody is looking. Check the sinks.
- **The wandering door:** Somewhere on the castle's plateau or inside it stands a door with no wall around it. Look away and it may be somewhere else. Everyone who walks through it ends up in the same place.
- **The blinding hall:** Upstairs in the castle, behind the looping hall, is a hall so bright it whites out your eyes. Nothing chasing you can keep track of you in there.
- **Supplies:** The town fountain flows with Almond Water, the water towers have a tap at their base, and every milk van has a crate of Almond Milk at its back doors.

**Level 39**
- **Hostile windows:** Most stretches of the mill wall are infested with Windows. Come within ten blocks of an inhabited ground-floor window and it wakes: the light swells and pulses, the blinds tremble, a dark long-armed shape stands behind them and coal ash drifts out. Stare and it pulls your eyes in. Linger and it shrieks and throws glass, and up close it breathes paralysing fumes; get within three blocks of the glass and it grabs you and drags you over the sill. Tear free past four and a half blocks, or survive three seconds pressed against the glass and you noclip through it (the way to Level 9). More than four people together and they stay quiet.
- **Inverted, merged, cut and floating houses:** Some houses stand on their roofs, two houses of different eras have grown into one, some are sliced clean in half like a dollhouse, and some hover a few blocks off the ground with no foundation at all.
- **Odd furniture:** Furniture on the walls, stacked, or hanging upside down from the ceiling.
- **Flooded rooms:** One room of the house is filled to the ceiling with water.
- **Bigger on the inside:** The rare house whose front door is unlocked opens onto an entry hall, and past it an endless run of rooms under a ceiling studded with bare bulbs that the house could never hold. Walk back through the hall to leave.
- **Vanishing doors:** In some houses, a door you walk through is a wall when you turn around. It comes back once nobody is near, or after a while.
- **Screams from the houses:** Now and then a scream comes from a house window and the colour drains from the picture. Only you hear it. It isn't real.
- **Sounds from the holes:** If you hear scraping, then breathing, then a growl coming out of a hole in the ground, run: something is climbing up.
- **Hydrolitis:** The rare dark stretches of tunnel, where the light orbs are out and green motes hang over a filmy surface, carry the Hydrolitis plague. Wade there and it builds up: fever and chills, then weakness, nausea and delirium. Almond water cures it.
- **The M.E.G. survey camp:** Somewhere in each town the M.E.G.'s last expedition left a tent, crates and field notes (right-click to read). One of the crates has a false bottom.

### The camcorder picture

Everything you see in the Backrooms is filmed: a gentle wide lens, worn VHS tape (faint line
jitter and grain; colour fringes only while the tape misbehaves: something dangerous close by, low
sanity, glitches, a Smiler, a level change), soft bloom on the lights and a little motion blur
when you turn. On top of that each level has its own mood (`client/AtmosphereGrade`), applied
evenly over the whole picture and blended over about two seconds when you change level:

| Level | Mood |
|---|---|
| Level 0 | Kane Pixels' tapes: a soft, faded yellow-beige (never olive). With Veil the troffers are the real light: warm pools under each one, cut block-exact by every wall, post and door jamb (`client/light/Level0LightRenderer`, shadows from a top-down wall map of the 128 x 128 columns around you, `Level0WallGrid`), so jambs facing away go black and posts throw hard wedges; a weak bounce from the lightmap fills in under the pools and dies away into corners and wall feet, which a screen pass darkens from the same wall map (no halos). Lit doorways far down dark runs read as beacons (64 lamps within 42 blocks on HIGH). Without Veil the lightmap alone makes the pools, steeper, with contact shadows for the corners. Tubes differ in age, chosen by the layout (`world/layout/Level0Tubes`, a `glow` block state: full, worn, tired or dying, each with its own light level, so it works with or without Veil); whole stretches of ceiling were re-lamped at different times, so some areas are brighter than others. Each tube also has its own colour temperature (mostly neutral, some yellowed, a few cool). About one tube in twenty is faulty: it stutters in a short burst every 12-45 s, or now and then cuts out for a few seconds and re-strikes with a few flashes, clicking each time; the room under it dims a little with it. A blackout is a dying wave rolling out from a point near you (22 blocks/s, each tube popping), then a partial recovery: emergency lamps and a few tubes on emergency ballasts come on, half the 24-block patches come back after 20-60 s, a quarter when the event ends and a quarter stay dead for 2-4 more minutes (`client/light/Level0Power`). Server code can cut the power locally (`world/FixtureOutages`: a circle or a list of fixtures, cut, stuttering or as a wave; restored on demand). Almost no haze (6% from 20 blocks), dark wings crushed to black. F3 shows the Level 0 lights' GPU time |
| Level 1 | Cold, damp concrete: blue shadows, little colour, grey-blue air |
| Level 2 | Warm amber pools from the caged lamps receding down the line, cold blue-grey darkness between and beyond them, pipe chases sunk in shadow, a thin cold haze far off. With Veil the lamps take block-exact shadows from the same wall map as Level 0 (48 lamps within 39 blocks on HIGH) and wet floors, puddles, sweating pipes and bare metal get a small highlight of the nearby lamps (`client/light/Level2Gloss` says which blocks are damp, wet or metal). F3 shows the Level 2 lights' GPU time |
| Level 4 | Cold fluorescent blue-grey, quiet |
| Poolrooms | Teal shadows, soft cool highlights, a gentle wet glow |
| Level 37 | Dreamlike: low contrast, milky shadows, warm overexposed highlights, the strongest glow |
| Level 9 | Deep blue shadows, sodium-orange highlights, cold dark air |
| Level 94 | A touch over-saturated and soft by day, pale blue distance; colour drains away at night |
| Level 7 | Colour almost gone: pale grey bone-dust fog above the water, murky dark teal under it, near-black in the deep |
| Level 227 | Misty rainforest light (`client/Level227Lighting`): pale grey-green daylight under open sky, greener and dimmer under every layer of leaves, a dark mossy green deep under a branch but never black; only a trace of camcorder haze (the distance mist is the far view's own, so the void stays black); colour draining to black as you fall into the void; and a blinding white-out inside a sunbeam |

Each mood combines a colour grade (split-toned shadows and highlights, lifted blacks), coloured
haze that thickens with real distance, soft contact shadows in corners and where walls meet floors,
a faint glow around bright areas, and a very slow exposure drift, as if the camera's auto-iris
can't quite settle. All of it is kept subtle so you can always see where you're going.

The camera also reacts like a real one:

- **Eyes adjusting** (`client/EyeAdaptation`): the auto-exposure follows how bright it is where you
  look, slowly. Step from the dark into a lit hall and the picture is blown out for a moment
  before it settles; step into the dark and it stays too dark for a couple of seconds.
- **Dust in the light** (`client/particle/DustMotes`): fine specks drift in the air near the lamps
  and in your flashlight beam (only where there is light to see them by). In Level 0 they are
  rare, small and faint: the odd speck in your beam or right under a tube.
- **Nothing but the footage:** no hand or held item, no crosshair or block outline, no XP bar, no
  tutorial hints or advancement pop-ups. Hearts, hunger and air appear only for a few seconds when
  they change (and stay faintly in view while your health is low); the hotbar slides away when
  you're not switching items (`client/hud/BackroomsHudEvents`).
- **No name tags** (`client/hud/BackroomsHudEvents`): while you are in any Backrooms level nobody
  has a name floating over their head, players or anything pretending to be one, so you only
  know who's who by finding them. Chat, the player list and the overworld are unchanged.
- **Arrival card** (`client/hud/ArrivalCard`): arriving on a level (from reality, through an exit,
  an anomaly or Level 227's tree) no longer prints lines in chat. Once the picture is back from the
  transition, a camcorder-style title comes up at the lower left, above the hearts and hotbar: how
  you got there if it matters ("YOU CLIPPED THROUGH REALITY." in amber), the level's name, and its
  one-line description. It fades in row by row, holds, and is gone after about six seconds. Each
  letter has a thin dark edge, so it reads on Level 37's white tiles and in Level 2's dark alike.
- **Your breathing** (`client/Breathing`): heavy breathing after a sprint that eases off as you get
  your breath back, and quick, shaky breaths when something is near.

### Main menu

The vanilla title screen is replaced by a "found footage" menu (`client/menu`): camcorder footage
of the levels plays behind it (a slow pan over screenshots, VHS glitch cuts, flickering lights,
grain and scanlines) with the REC overlay, the logo, a camcorder-style menu and a taped note, while
the main theme plays (see Music below). **FIELD NOTES** is a short in-game guide for new players.

### Music

Four original pieces, composed in code in `tools/gen_assets.py` (`snd_music_*`; Level 227's in
`tools/anomaly_assets/level227_audio.py`) and played on the game's music channel, so the Music
volume slider controls them (`client/BackroomsMusic`):

| Piece | Where | What it is |
|---|---|---|
| `liminal` | Title menu (looping); now and then in every level except Level 94 | The main theme: slow, washed-out tape chords in D minor with an uneasy E-flat. Single tones fade in, it swells into a wall of sound, then falls apart into wind, a creak, a far boom and an out-of-tune music box |
| `deep` | Now and then in Levels 0, 1, 2, 4 and 9 | Drones, wind and far-off noises with long near-silences; a muffled memory of the theme drifts past |
| `drift` | Now and then in the Poolrooms and Level 37 | Softer and dreamier: warm chords that never settle, water drips, a slow music-box line |
| `megaflora` | Level 227 only, every 4 to 10 minutes | Wonder turning into dread: wide, warm chords in E Lydian and a celesta figure climbing like light through leaves; then the bass slides a tritone down under the same chords, the sun's choir sings a long E that bends up to F, the tree groans, and it ends on E and F together, fading into wind |

In the levels a piece plays only every 3 to 8 minutes, like Minecraft's own music; in between
you hear only the level. Level 94 has no music because its siren poles play their own tune, and
Level 7 none because the sea is louder.
Everything goes through a worn-tape sound: a slow pitch wobble shared by every note, soft
saturation, hiss and a big, empty room.

### Sound

- **Walls muffle, rooms echo** (`client/audio/Acoustics`, using OpenAL's EFX effects). Every sound
  placed in the world is muffled by the solid blocks between you and it (a lamp in the next
  corridor hums dull and low and opens up as you round the corner), and it rings in a reverb that
  follows the space you are standing in: short and close in a small room, long in a big one, hardly
  at all under open sky. Each level adds its materials: Level 0's carpet and ceiling tiles make it
  sound dead and dry up close, like a real office, with only a soft, dark, slightly late tail
  hinting at how far the halls go (but in its tall Atrium and shafts the echo turns harder and
  longer and comes back from far above); Level 4's offices soak up the highs, Level 1's bare
  concrete garage rings, Level 2's long pipe corridor is hard and metallic, with a late echo
  coming back from far down the line. The
  Poolrooms and Level 37 get no reverb, since in canon nothing reverberates there. Music, the
  camcorder's beeps and the overworld are left alone. Without EFX support it simply switches off.
- **Distant sounds** (`client/audio/DistantAmbience`): every 20 seconds to a minute and a half
  something happens somewhere out of sight, picked from five or more per level, each with three
  takes: in Level 0 mostly the building itself (see below), pipes clanging in Level 1 and 2, a
  phone or copier in Level 4, water and drains in the pool levels, wind chimes and a dog that
  stops barking in Level 9, a car horn or rats in Level 94 depending on the time of day.
- **Level 0's quiet** (`client/light/FixtureSounds`, `tools/anomaly_assets/level0_audio.py`): the
  room tone is a 93-second loop built from a real recording of a fluorescent-lit room: the 60 Hz
  mains hum and its harmonics slowly swelling and drifting against each other, the room's air,
  very faint distant air conditioning and a thin ballast whine, so it never audibly repeats. Each
  tube buzzes on its own, with one of four ballast sounds, its own pitch and loudness (worn tubes a
  little louder); you hear the one you stand under, the next ones less, and stretches with no lit
  tube go nearly silent. Faulty tubes buzz rough and unsteady; one dropping out ticks (its
  starter), striking again it clicks and catches, and a dying tube's starter ticks now and then.
  About once a minute, faint and far away, the building makes a sound: it settles with a creak,
  ducting ticks above the ceiling as it cools, a relay clacks in a far panel, a door clicks
  somewhere. Now and then a tube pops in the distance or water drips onto the carpet; rarely,
  something scratches behind a wall or a voice calls, far off.
- **Level 0's dark is silent** (`client/audio/Level0Soundscape`): the room tone belongs to a lit
  building. Where there is no lit tube near you (a dark district, the dark wing, a room whose
  lights just died) it sinks about 18 dB, and in a blackout, with the mains off, about 30 dB, so
  the buzz of the tubes that still burn is all there is and the dark is quiet. It fades smoothly
  in decibels, down in about a second and back up over two.
- **Level 0's events** (`client/light/FixtureSounds`, `client/audio/Level0Soundscape`): every
  tube near you that goes out in a blackout's dying wave or a local outage pops and its buzz sags
  away, one after another as the dark rolls past, and each that comes back ticks and catches; an
  emergency lamp's relay clunks as it switches on. The blackout itself is a far breaker and the
  whole floor's hum sagging while ballasts pop further and further away; the power's return is far
  contactors and the mains swelling back into the walls (the tubes then come back on their own,
  some never). A district going dark far away is heard as a muffled contactor and a cascade of
  ballasts, from its direction.
- **Level 0's set pieces**: the Front Office's failing fire horn (three blasts and a pause, one
  blast chattering, the last one sagging) carries about 80 blocks through the halls, with the red
  beacon's motor whirring close by; the Atrium has cold air moving high up in its void, and the
  Shaft Hall's shafts send down a scrape, a knock, a creak or trickling grit every half minute or
  so; a loose ceiling tile cracks up a shaft and lands on the carpet. Hidden rooms have their own
  tones: an old radio playing a waltz through static (and a recorded message), a wall clock that
  skips a beat, the stillness of a sealed room, a chair creaking as if someone just stood up, and
  behind the Front Office's drawn door a clock and a PA microphone left on. Under the Atrium and
  the shafts the reverb turns harder and longer, coming back from far above.
- **Level 2's pipes** (`client/anomaly/level2/Level2Emitters`, `tools/anomaly_assets/level2_audio.py`):
  every 7-18 seconds the pipes act up somewhere up or down the corridor: a water-hammer knock
  that runs along the pipes toward you or away, a heavy clank, a long creak of hot metal (far more
  of them while the corridor twists), water rushing through the overhead bundle and over your
  head, a shudder running along a pipe run past you with its brackets buzzing, or far metal
  tapping: a few knocks of a tool on a pipe, a pause, the same knocks again a little closer
  along the same pipe. The machinery is heard where it stands: pumps hum and thump, fan housings
  and machine cabinets drone, breaker panels buzz, and every ~48 blocks a plant room behind the
  wall swells a deep hum as you walk past it. When a section's circuit fails (or in a stretch
  whose lamps died for good) its machines coast down and the level's drone dips about 7 dB;
  they spin back up when the power returns. Leaking valves hiss and puff steam, and about once a
  minute a relief valve near you lifts with a clunk and a burst of steam. A drop from a leak
  plinks in a puddle or the gutter, ticks on a grating or a pipe, taps on bare concrete, plops
  in black ooze.
- **Real recordings** (`tools/level_assets/recorded.py`, all CC0, see CREDITS.md): the fluorescent
  hum of every tube in Levels 0-2 is a real ballast, the flicker clicks are real tubes and starters
  catching and dropping out, a blackout is a big breaker clunking and the floor's hum sagging and
  dying in the concrete, and when the power comes back the starters tick before the hum swells
  in. Level 1's garage air (a ventilation fan rattling far off in an empty car park) and Level 0's
  room tone are field recordings too, and so is your breathing. The Bacteria's sounds are the same
  breathing recordings slowed down, some played backwards, with soft wet clicks
  (`tools/level_assets/bacteria.py`).
- **Mix:** quiet on purpose. Every level's background loop is heard at the same loudness (about
  -30 LUFS, set by its `volume` in `sounds.json`); hums, buzzes and drones stay around -27 and
  nothing else (creatures, alarms, the camcorder's beeps) goes above about -18, so a scare stands
  out without blasting. Backpacks open and close silently. Many lamps humming
  together are turned down so their drone never buries everything else, and the music sits a
  little above the level instead of over it. Level 0 is a touch quieter still, so its silences
  feel like silence: room tone about -33 (and 18 dB under that in the dark, 30 in a blackout),
  each tube's buzz -27 (-28 for a faulty one) and faster to fade with distance, starter ticks -35,
  and its far building sounds -29 to -32 before distance and walls take their share. Its events
  and set pieces (`tools/anomaly_assets/level0_spaces_audio.py`): the Front Office horn's blasts
  -18 (at the ceiling, so it carries 80 blocks through the walls), the PA -18 like every PA line, a falling
  ceiling tile -24, a far district going dark -23 before distance, the blackout's wave -25, a
  tube popping -28 or striking again -30, the Atrium's air and the pit draught -30, the hidden
  rooms' radio -31, clock -34 and stillness -37. Level 2: the plant hum -27, pumps -28, a
  leaking valve -30, a breaker panel -31, the pipes' knocks, creaks, rushes and far tapping -24
  to -25, a clank -24, a relief valve venting -23 (its hiss swells in over a fifth of a second),
  drips -28 (puddle), -29 (metal) and -31 (concrete); the hot zone's machinery sits at -24.
- **Level 39 "Megalophobia"** (`client/Level39Audio`, `client/Level39QuakeEffects`): the town
  rings like one colossal hall (the superstructure encloses it, so its echo is long and loud even
  in the open), while the marble tunnels below swallow sound. Its own loops follow you between
  town and tunnels; far off you hear the superstructure groan, metal doors slam with long echoes
  and telephones ring in empty houses. Radios only garble here. Loud sounds echo and can build into a quake
  (camera shake, rumble, dust, rattling windows); the superstructure's own climate rains on the
  town now and then.
- **Level 227 "Megaflora"** (`client/Level227Audio`, `tools/anomaly_assets/level227_audio.py`):
  wind through an ocean of leaves, insects, birdsong that isn't quite right. Near a branch edge a
  hollow wind rises out of the void from the exact spot where the ground ends, so you can hear
  which way the drop is; falling, it is all around you. Sunbeams hum, the sun's choir whispers,
  bubbles shimmer. Far off, branches groan as the tree moves, limbs crack in the canopy, something
  big calls and the void howls from below. The forest's echo is soft and diffuse (thousands of
  trunks scatter it). Loops at -30 LUFS (the void wind and the beam hum -27), the lurch -20.

## Operator commands

```
/backrooms enter [players] [level]      send players in: level_0, level_1, level_2, level_4, poolrooms, level_37, level_9, level_94, level_7, level_39, level_490, level_227
/backrooms time94 <day|dusk|night|dawn> jump Level 94's day/night clock (testing)
/backrooms escape [players]             send players back to reality
/backrooms battery [players] [percent]  set camcorder battery
/backrooms locate                       where is the nearest exit? (Level 227 has none, and says so)
/backrooms locate saferoom              where is the nearest safe room?
/backrooms anomaly here                 which anomalous area am I standing in?
/backrooms anomaly locate <kind>        where is the nearest one of a kind (e.g. level_0/red_rooms; tab completes)? On Level 0 it also finds zones and set pieces: level_0/zone/... (e.g. level_0/zone/rooms, the room maze) and level_0/set/... (e.g. frame_tunnel, dead_end_cache, liar_arrows, bright_district, dim_district, abandoned_district). On Level 2 also segments (level_2/seg/..., e.g. dogleg, duct_crossing, catwalk, bypass), rooms and chambers (level_2/room/..., e.g. pump_room, valve_gallery, electrical, sump, break_room), level_2/exit and the oddities (level_2/wrong_valves, inverted_pipe, repeat_section, warm_door, heart_gauge)
/backrooms anomaly visit <kind> <point>  Level 0 and Level 2 (ops, for testing): jump to a named spot of the nearest site of that kind (tab completes both). level_0/atrium ladder|balcony, level_0/front_office rug|hidden_room, level_0/shaft_hall lit_shaft; the hidden rooms and the older anomalies have their own points. Level 2: level_2/room/<chamber> door|story, level_2/seg/dogleg approach|around, duct_crossing approach|under, catwalk approach|catwalk|lower, bypass approach|seal|passage, level_2/wrong_valves door|inside, warm_door front, heart_gauge front, inverted_pipe between|wall_a|wall_b, repeat_section first|second|last
/backrooms event <blackout|flicker|alarm|intercom|lightning|twist|fog|lampwave|storm|water|rain|quake|end> [seconds]
/backrooms outage <cut|stutter|wave> <radius> [seconds]   Level 0: cut the power in a circle around you (no seconds = until "/backrooms outage end")
/backrooms l0event <room|corridor|district>   Level 0: let that local light event happen to you at the next right moment (walk into a room / walk a corridor / look down a long lit view), skipping the wait
/backrooms drift <player> [sealed|door|lights|end]   separate realities now: that player sees a sealed doorway, a door or dead lights (with the nearest friend, or alone); end = snap back
/backrooms hallucinate [players]        soak players in the Poolrooms: full hallucination + phantom friend
/backrooms sanity [players] <0-100>     set sanity (test the low-sanity effects)
/backrooms spawn bacteria [count]       spawn Bacteria ~12 blocks in front of you (they only hunt survival players)
/backrooms spawn smiler [count|bold]    spawn Smilers ~16 blocks in front of you (start a blackout first)
/backrooms spawn <watcher|observer> [players]   Level 9 test spawns
/backrooms spawn hound [count]          Level 39: Hounds ~14 blocks in front of you (they only hunt survival players)
/backrooms spawn faceling [hostile] [count]   Level 39: Stranger Facelings, or a hostile pack (default 5)
/backrooms spawn clump [ceiling|alcove] Level 39: a Clump in the nearest drain or alcove
/backrooms spawn skin_stealer [hungry|fed]   Level 39: a Skin-stealer
/backrooms spawn wretch [count]         Level 39: Wretches
/backrooms window wake                  Level 39: wake the nearest lit facade window
/backrooms hole stir                    Level 39: make the nearest hole start its sounds (a Hound follows)
/backrooms hydrolitis [check|clear|set <0-1000>] [players]   Level 39: Hydrolitis infection
/backrooms gondola ride                 Level 9: board the nearest chairlift (testing)
/backrooms l227 lurch                   Level 227: the whole tree lurches now
/backrooms l227 unstable [seconds]      Level 227: the tree shakes you loose in 10 s (or the given time; the warning is the last 6 s)
/backrooms l227 drag                    in another level: get dragged into Level 227 now
/backrooms l227 bubble                  Level 227: a fallen bubble drifts by the nearest branch edge
/backrooms l227 beam                    Level 227: stand on the forest floor 6 blocks outside the nearest sunbeam, facing it
/backrooms spawn l227_bear [awake]      Level 227: a bear ~10 blocks ahead, asleep facing you (or waking)
/backrooms spawn l227_doe               Level 227: a doe ~12 blocks ahead (walk within 4 blocks and you fall)
/backrooms spawn l227_boar [standing]   Level 227: a boar ~10 blocks ahead, lounging on its back (or up)
/backrooms spawn l227_wolf [count]      a wolf pack (default 4) ~20 blocks ahead; they only hunt in the high sections of Level 227
/backrooms spawn l227_stag              a stag ~24 blocks ahead
/backrooms spawn l227_squirrels [count] a squirrel swarm (default 100) comes for you, anywhere (survival only)
```

## Building

Requires a Java 21 JDK (`org.gradle.java.home` in `gradle.properties` points at the one bundled
with CurseForge on this machine; change it if you build elsewhere).

```
gradlew build            -> build/libs/backrooms-1.0.0.jar (give this to your friends)
gradlew runClient        -> test in a dev client
gradlew runServer        -> test on a dev dedicated server (run/)
```

Textures and sounds are generated by `tools/gen_assets.py` (Python 3 with numpy, Pillow,
soundfile; Windows speech synthesis for the intercom voices).

### Recording trailer footage

`client/trailer/TrailerRecorder` flies the camera along a smooth keyframed path and records every
frame straight into a video with ffmpeg. It does nothing unless the client is started with
`-Dbackrooms.trailerDir=<folder>` and `-Dbackrooms.ffmpeg=<path to ffmpeg.exe>`. Put the player in
spectator mode where the shot starts, then write `shot.json` into that folder:

```json
{"name": "l0_dolly", "out": "C:/trailer/l0_dolly.mp4", "fps": 30, "pre": 1.5,
 "keys": [[0, 443.0, 42.62, -94.0, -90, 3], [6, 449.0, 42.62, -94.0, -86, 2]]}
```

Keys are `[seconds, x, eye height, z, yaw, pitch]`. The HUD is hidden while recording, and
`<name>.done` appears when the shot is finished. The sneak-peek trailer
(`dist/Backrooms-Found-Footage-Sneak-Peek.mp4`) was shot this way and cut to the `liminal` theme,
and so was the trailer (`dist/Backrooms-Found-Footage-Trailer.mp4`): 24 shots, one per scene and
never reused, across every level, letterboxed at 24 fps, with no creatures in it, cut to `deep`.

## Code map

| Package | What lives there |
|---|---|
| `world` | Level definitions, the infinite chunk generator, exits, travel, floor events, safe rooms (`SafeRooms`) |
| `world/layout` | One floor plan per level (pure functions of seed + x/z); Level 0's maze is a skeleton of wall bands and gaps (`Bands`) shaped by its zones (`Level0Zones`) in `Level0Layout` (rooms, awkward walls, vistas, corridors, and where each troffer goes and whether it is dead), with the builders' rooms (`Level0Builders`) and the room maze (`Level0RoomMaze`) on their own grids, the light and liar districts in `Level0Lights` and each lit tube's age picked by `Level0Tubes`; Level 37's plan is integer codes (`Level37Plan`) turned into blocks by `Level37Layout`; Level 39 is split into the town cell plan (`Level39Cell`, cached 128-block tiles in `Level39Tile`), houses (`Level39Houses*`, one cached voxel plan per house), the mill (`Level39Superstructure`) and the tunnels (`Level39Tunnels`, `Level39Vault`); Level 227's one canonical tree (`Level227Tree`, also read by the far renderer) is turned into blocks by `Level227Layout`, with the forest, caves and flytrap patches of each 32-block tile cached in `Level227Tile`; each level's anomalous areas are planned in `<Level>AnomalyPlan` |
| `world/anomaly` | Anomalous areas: site planner (`AnomalyGrid`), behaviour registry (`Anomalies`), server driver and client sync (`AnomalyService`), seamless teleports (`Teleports`), "is anyone looking?" (`Observation`); one sub-package per level |
| `client/anomaly` | Client side of the anomalous areas: which sites are near (`AnomalyClientState`), per-level picture moods, sounds and overlays; one sub-package per level |
| `entity` | The Bacteria and its spawner; Level 39's mobs (`Hound`, `Faceling`, `Clump`, `SkinStealer`, `Wretch`) and their spawning (`Level39Spawner`, `TunnelDwellers`); Level 227's grazers (`L227Bear`, `L227Doe`, `L227Boar`) and their spawning (`Level227GrazerSpawner`); Level 227's hunters (`L227Wolf`, `L227Stag`, `L227Squirrel` and the swarm brain `SquirrelSwarm`) and their spawning (`Level227PredatorSpawner`); Level 227's giant Venus flytrap (`VenusFlytrap`, planted by `Level227FloraSpawner`) |
| `camera` | Camcorder battery/flashlight/NightShot server rules, flashlight sync |
| `player` | Energy (stamina), movement limits, footsteps (incl. wading, `PoolWaterSounds`), checkpoints and respawning (`Checkpoints`), hiding in furniture (`HidingService`, which furniture and where the view sits: `HidingSpots`), friends hearing a panicking player's heartbeat (`SharedHeartbeat`) |
| `block`, `item` | Decals, lamps, supply containers (crate/backpack/locker share `SupplyContainerBlock`), doors, food, pills, bandages; big surfaces (wallpaper, carpets, ceiling tiles, concrete, pool tile, asphalt) come in a few stained/cracked versions picked per block by the world generator (`SurfaceBlock`; plain inside anomalous areas); Level 0's versions cluster in worn patches and some keep to one wall row (`Level0SurfaceBlock`, art in `tools/anomaly_assets/level0_surfaces.py`); Level 2's wet versions gather in damp stretches (`Level2SurfaceBlock` with `world/layout/Level2Damp`, art in `tools/anomaly_assets/level2_materials.py`); Level 227's flora (`Level227*Block`: tall plants, hanging strands, the Palette, GammaPali, Lestron; registered in `registry/Level227Blocks`, art in `tools/anomaly_assets/level227_flora.py`) |
| `network` | Packets between server and clients |
| `client/particle` | Steam and dust particles, drawn soft by their own shader (`SoftParticles`, `shaders/core/soft_particle`); dust spawning (`DustMotes`) |
| `client/light` | Veil dynamic lights: fixture index, light manager, flicker/blackout, Level 0's faulty tubes (stutter, lazy restart, dying pulse: `FixturePower`; the tube ages and colours themselves are block states, `block/Level0TubeGlow`, picked by `world/layout/Level0Tubes`), Level 0's own light type with wall-map shadows and the power/corner screen pass (`Level0LightRenderer`, `Level0WallGrid`, `Level0PowerMap`, shaders in `pinwheel/shaders/program/light/level0_*`), its blackout wave and local outages (`Level0Power`), flashlights, hum sounds, Level 37's shafts of sun (`SunBeams`: one area light per opening) |
| `client/water` | Pool water (also Level 7's sea): surface finder (`WaterSurfaces`), renderer + shaders (`WaterRenderer`, `shaders/core/pool_*`), per-level look (`WaterStyle`), underwater fog and its darkening with depth (`UnderwaterView`) |
| `client/audio` | Wall muffling and room reverb through OpenAL EFX (`Acoustics`, `AcousticsEfx`); distant one-off sounds per level (`DistantAmbience`) |
| `mixin/client` | Hides vanilla water faces in the pool levels; tells the water renderer about block changes; lets you sprint through the knee-deep shallows (`LocalPlayerMixin`); reaches the sound engine's channels for `client/audio` (`SoundManagerAccessor`, `SoundEngineAccessor`, `ChannelAccessor`); moves your view inside the furniture while you hide (`CameraMixin`) |
| `mixin` | Keeps gravity on while you run through shallow pool water (`LivingEntityMixin`); lets anomaly teleports update the server's movement check, and keeps players held in the air (Level 39's sky arrival, window grabs, the chairlift) from being kicked for "flying" (`ServerGamePacketListenerAccessor`) |
| `client/camera` | Handheld camera shake, roll, smoothing |
| `client/hud` | Minimal HUD, stamina line, the arrival card (`ArrivalCard`), and the shared camcorder OSD look (`OsdStyle`: font, colours, margins, fades) used by `CamcorderHud`, `SafeRoomOsd`, `HidingOsd` and `ArrivalCard` |
| `client/trailer` | Dev-only trailer camera and frame recorder (`TrailerRecorder`, off unless started with `-Dbackrooms.trailerDir`) |
| `client` | Camcorder post-shader control, per-level picture mood (`AtmosphereGrade`), overlay, Bacteria model/renderer, keys |
| `assets/backrooms/shaders` | Lens, bloom, motion blur, contact shadows, distance haze, glow and grade post-processing |
| `tools/gen_assets.py`, `tools/anomaly_assets` | Every texture and sound, generated in code; the anomalous areas' assets live in one module per level, loaded by `gen_assets.py` |
