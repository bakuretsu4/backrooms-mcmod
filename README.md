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
| Jump | Not possible in the Backrooms (except in water, to swim and climb out; on ropes and other climbables; and in Level 490 and Level 227, where the levels need it) |
| Loot | Right-click Wooden Crates, Backpacks and Supply Cabinets (see Supplies below) |
| New battery | Right-click a Camcorder Battery |
| Settings | **SETTINGS** on the main menu, **Backrooms Settings...** in the pause menu, or Config in the Mods list: camera smoothing (default 35%; the original feel was 88%), handheld shake, view tilt, motion blur, wide lens, **footage look** and **4:3 frame** (see The camcorder picture below), stamina line, and **QUALITY** for weaker computers (see Performance below). Changes apply immediately and are saved per player in `config/backrooms-client.toml`. |

### Performance

**QUALITY** (on the SETTINGS screen) turns down the three things that cost the most graphics-card
time. HIGH is the full look; try MEDIUM or LOW if the game stutters.

| | HIGH | MEDIUM | LOW |
|---|---|---|---|
| Dynamic lamp lights (shadowed) | 32, within 28 blocks | 20, within 24 | 12, within 20 |
| Glow around lamps (blur passes) | 6 | 4 | none |
| Pool water reflections | full | shorter | none (plain mirror colour) |
| Sun beams (Level 37, Poolrooms) | 20 | 12 | 6 |
| Opening glows (niche wash + bounce, 2 lights each) | 12 | 6 | 3 |

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

One path runs through every level, in the order of the wiki's level numbers (the unnumbered
Poolrooms sit between 9 and 37):

```
Overworld --noclip--> Level 0 --stairwell--> Level 1 --stairwell--> Level 2 --EXIT door--> Level 4 --stairwell--> Level 7 --sunken door--> Level 9 --underpass--> Poolrooms --the great pool hall--> Level 37 --deep pit--> Level 39 --mill window--> Level 94 --dark doorway--> Level 228 --the one true mirror--> Level 369 --arch of vines--> Level 490 --the leap--> Overworld
```

Every exit looks different, but every move from one level to the next is filmed the same way:
the same datamosh as noclipping in, the old place smearing into the new one. Only escaping back
to reality, with the leap at the end of Level 490, has its own ending (the tape stops).

Rare side ways lead off the line, ahead or back. The rule for them: the level you land in always
has a way back to the level you left (going back needs nothing, you just walk the line forward
again). A sunlit archway in the Poolrooms skips ahead to **Level 39**'s flooded tunnels, and a
few dead ends of those tunnels glow with the same daylight: walk into one and you are back in
the Poolrooms. A moulded-clay front door on a Level 9 house opens onto **Level 94**, and a very
rare chairlift in Level 9's Crimson Field climbs into the clouds and drops you into **Level
39**'s town; both levels have the odd house with a clay front door of their own that leads back
to **Level 9**. Going back: Level 94's flooded houses lead down to **Level 7** and its castle
throne to **Level 1**; Level 37's void and dark openings lead to **Level 0** and the
**Poolrooms**; Level 9.1's foxholes lead to **Level 2** and its gloom to **Level 0**.

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
  passage, the odd cardboard cutout of a person, a heap of old furniture) and whole wings where every light is dead. Here
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
  (mattresses, crates, the odd backpack), where nothing hunts you. Now and then a hallway of
  **storage lockers** opens off the halls: roll-up shutters, a few rolled up with supplies
  inside, something growling behind one or two, and a white door at the end that never opens. Car
  **ramps** lead down to a dimmer lower floor here and there. A stairwell leads down.
  - **Wet floor:** the concrete is damp and the puddles are wet, so the lamps show in the floor:
    a pillar's tube as a long bright streak running from its foot toward you, a hanging fixture
    as a short wide patch, both sparkling where they cross a puddle and faint on dry concrete,
    strongest looking far across the floor and faint at your feet; between the lamps the floor
    stays dark. The reflections are flat strips drawn on the floor with vanilla's glowing render
    type (`client/light/Level1WetFloor`), so they look the same with Veil, without it and under
    Iris shader packs. They go out with their lamp (blackout, flicker, dead zones) and a lamp
    behind a pillar or wall leaves none.
  - **Lights out:** when the power fails (the blackout event, 20-40 s) the garage goes nearly
    pitch black: every tube, its glowing face and its reflection die over two seconds, the air
    turns black and swallows everything a few blocks out, and only the faintest shape of the
    floor at your feet is left. Your flashlight holds the dark back further, NightShot sees
    through most of it. Under an Iris shader pack (which still lights the world by the dead
    tubes' block light) the camcorder presses the picture itself down to black, before your
    flashlight (`client/Level1Atmosphere`). Then the Smilers come.
- **Level 2 (Pipe Dreams):** one endless, perfectly straight maintenance corridor of grey poured
  concrete, one block wide: the walls almost brush your shoulders and the ceiling sits just over
  your head (2 blocks of headroom). Nothing branches off it: no rooms, no doors, no detours, only
  the corridor ahead and behind. Its dressing corkscrews round you without pause, all the way
  along (`world/layout/Level2Twist`, `world/layout/Level2Section`): the pipe bundle (a pale
  insulated main, fat painted pipes, a cable tray on steel channel brackets) winds from one wall
  over the ceiling to the other wall and under the floor grating, about one turn every 40-60
  blocks, slowing, speeding up and reversing every few hundred blocks; recessed square light
  panels every 10 blocks, a painted yellow band, wall fans and spinning amber warning beacons
  (`registry/Level2SpinBlocks`) ride round with it. The whole level seems to turn around you
  (`client/anomaly/level2/Level2Spin`).
  - **Camera:** while you are more than 100 blocks from the nearest exit, the whole room turns
    right round you with the spin (about one turn a minute, speeding up, stalling and drifting
    back), so half the time you are walking past sideways and upside down; within 100 blocks it
    settles back upright, so you reach the bulkhead the right way up. On top of that the view
    drifts 7-11 degrees and leans with the turning, the field of view narrows and breathes, and
    the edges of the picture slide round a still centre.
  - **Light and sound:** with Veil, the lamps' bright side travels round the corridor. Groans,
    rattles and long creaks orbit your head (`client/anomaly/level2/Level2Orbit`).
  - **Comfort:** all of the motion follows the "tilt" comfort setting; at 0 there is none.

  You can only walk forwards or back. Every 24-48 blocks the corridor changes (a valve manifold set
  into the wall, a bulkhead ring, a leak with black ooze underfoot, a short dark stretch). Now and
  then a backpack or a supply crate sits at the foot of a wall (Level 2 leans to bandages), and
  every 250-350 blocks a checkpoint radio is set into the wall. The only way out is an EXIT
  bulkhead at the back of a recess as wide as the lane, one every 1300-1600 blocks; you always
  arrive halfway between two, at least 500 blocks from the nearest. Its bulkhead takes everyone
  within 6 blocks with you. Parallel corridors 48 blocks apart never meet. The concrete is marked
  by water and age; in damp stretches (round leaks, and long patches where the cold pipes sweat)
  the floor is wet, the wall bases carry tide lines and the slab blooms with white salts. Service
  pipes are paint-coded: green water, red steam, grey return, yellow gas. The walls are numbered:
  a stencilled section number (2-A 03 up to 2-D 32, then round again) at the start of every
  stretch, in order as you walk east.
- **Level 4 (the Abandoned Office):** you arrive here through Level 2's EXIT door. As in canon,
  an endless, empty office building, almost completely devoid of furniture: white walls, cheap
  grey carpet, the low hum of air conditioning blowing through countless rooms and a cold draft
  now and then. Most lights don't work; the level is lit by its windows, which are mostly blacked
  out (blinds down, half down, or taped over) yet still glow. No monsters: it is a safe haven, and
  the place where **Almond Water** is found most often.
  - **Empty rooms and floors** (about half of it): huge open-plan floors with square white columns
    and nothing left but the dents of desk rows and their floor boxes; rooms that are truly empty
    except for the pale marks where a whiteboard or a picture hung, the odd cleared-out office
    with its cable trunking and thermostat, and very rarely a supply crate someone left behind.
  - **Conference rooms** (about a fifth): glass fronts, and always the same table, 3 blocks long,
    centred in the room, with 1 to 8 chairs spaced evenly round it (big rooms sometimes have spare
    chairs along the back wall: up to 20), papers, pencils and mugs of Almond Water on the table, a
    projector over it, a whiteboard, an empty TV bracket and a clock stopped at 4:04.
  - **Lobbies and cafeterias** (about a third, often run together): elevator lobbies with
    terrazzo floors, lit coffers, a directory board and fountains; big empty lobby rooms; and
    mostly empty cafeterias with a few of the same tables spread wide under pendant lamps (half the
    chairs gone, some turned away), a serving counter with shuttered
    hatches, vending machines (Almond Water or an Energy Bar until sold out), water coolers and
    drinking fountains (Almond Water, three times each), now and then a bottle of Almond Water left
    on a table (right-click to take it) and, extremely rarely, a double cheeseburger on a tray.
  - **Cubicle clusters** (rare): 54 identical, complete cubicles: a desk with a computer, a chair,
    pencils, notebooks, post-its, a mug of Almond Water and the same little "please keep your
    workspace tidy" sign in every one.

  Since the rooms are empty, the building itself carries the detail: framed doorways with room
  signs beside the doors, light switches and door stops inside them, slot diffusers and a slim
  radiator under every window bay, square diffusers, sprinklers and smoke detectors in the
  ceiling, access hatches, water-damaged patches (stained, lifted and missing tiles, a dangling
  cable), an extinguisher with a fire-alarm call point every 24 blocks or so, first-aid boxes,
  hand sanitisers, evacuation plans, notice boards, water coolers on some columns, and green
  **exit signs** over the doorways and hanging in the big spaces, pointing toward the nearest
  stairwell. Some filing cabinets marked SUPPLIES are lockers. Safe rooms stand as little office
  boxes in the open floors. Behind one locked door with a brass "6", faint music plays. There are
  two ways out, both to Level 7: an **emergency stairwell** (look for the glowing EXIT sign
  over a grey steel fire door in a bright, cleared area; go down the stairs), or, now and then in
  an elevator lobby, an **exit elevator** standing open and lit under a green EXIT sign: step in
  and wait, and it takes you down.
- **Level 7 ("Thalassophobia"):** reached down Level 4's emergency stairwells and exit elevators
  (or, coming back from further on, down the stairs of a flooded house in Level 94, about two exit
  houses in three). You come up in a
  small flooded sitting room (bookcase, coffee
  table, armchair, a radio and a crate on the dry landing) hanging from a concrete ceiling over an
  endless dark sea in a thick pale fog. Open a hatch in the landing and drop; the rope by the
  other hatch climbs back up (hold jump). Around the room is a shallow shelf of pale silt, then a
  sheer drop to a carpeted sea floor about 150 blocks down, with rock stacks, tar, scrap and the
  ribs of giant fish. You can hold your breath about ten times longer than normal here. The way
  out is a wooden door lying in a round stone platform on the sea floor, ringed by pillars and
  huge pipes, 150 blocks west of the room: swim into it and you come out in Level 9's suburbs.
  No monsters that hunt you, but something enormous lives in the water, and the longer you stay
  in (faster the deeper you are) the more interested it gets: over about 5 minutes in deep water
  (12 at the surface; it forgets you a minute after you climb out) its groans come from every
  30-80 s down to every 12-30 s and from closer, and it swims past you every 40-80 s down to
  every 14-30 s (the first time 12-25 s after you swim out over open water). Swimming at the
  surface, a huge shadow glides under you (it follows you until it has passed), the sea heaves
  and bubbles boil up around you. Under water it crosses in front of you 12-27 blocks off, and
  later slides past right under your feet or, deep down, over your head; two pale eyes and a row
  of faint lights along its flanks show where it is even in the black, and at its boldest its
  head turns toward you as it passes. You hear its body the whole way (shoved water and slow tail
  beats, coming from the part of it nearest you), then the surge and the rush of bubbles when it
  is closest, and its wake pushes you and rocks the view (scaled by the view-tilt setting). It
  never touches you. To test: `/backrooms thing` in the water (it passes now, at full dread) or
  `/backrooms thing dread`. Your sanity drains in the water, faster the deeper you go; the room calms you. The
  sea is drawn like the pool water (below), in its own murky grey-green: a rippled surface that
  mirrors the concrete above and lets the pale shallows show through, and underneath a dim teal
  murk that turns black and closes in as you go down. Far off, waves slap against rock, the
  ceiling drips and the giant pipes thump.
- **Level 9 (the Suburbs):** reached through the door on Level 7's sea floor. An endless suburb at midnight in thick fog that drains the colour
  from everything. Wet roads covered in leaves, old sodium streetlamps (most dead, some
  flickering), houses with blacked-out windows, some unlocked with dark rooms inside, and dark
  forest behind the yards. Follow the green EXIT arrow signs (a few lie) to a highway underpass
  and walk into the dark: it lets you out in the Poolrooms.
  - **Houses:** not every house is the same plan any more: wide one-storey ranch houses, houses with
    a ladder up to a dusty attic, houses with stairs down to a dark cellar (water heater, washer,
    boxes), boarded-up houses full of dust sheets and cobwebs, and now and then a half-built house
    of bare studs, rafters, sawhorses and a tarp.
  - **Lived in:** the TV is sometimes still on (soft static), dinner is left on the table, beds are
    unmade, some houses have a kid's room with toys and drawings, family photos hang on the walls
    (some with the faces scratched out), and a few kitchens have an answering machine on the counter.
- **The Poolrooms:** reached through Level 9's highway underpass. Dim white-tiled rooms and long water channels lit by a few tubes, glowing
  cyan pools with underwater lamps and moving light ripples, and now and then a tall atrium full
  of daylight. No monsters. The way out is the **great pool hall** (about one per 1000 x 1000
  blocks): a dark hall 41 blocks across, sunk 35 blocks under the level, its floor one big
  turquoise pool and its ceiling sloping up from all four walls to a small square of open sky
  at the top. A doorway in its outer wall leads down a long dark stairway inside the wall and out
  of a small doorway at the water's edge; across the pool, a straight tiled stair climbs the far
  wall to a landing high under the slope, where a niche is full of daylight. Climb up and step
  into the light: you go on to Level 37. The pool glows from within, a rising line of small step
  lights picks out the stair from anywhere in the hall, lamps glow in the corners, and there are
  ladders and a drifting plank at the edge. `/backrooms locate` gives the niche's position.
  Somewhere (about once every 300 x 300 blocks) a dim, dry room has a **sunlit archway** in its
  north wall: a round arch lined with fine white mosaic, warm daylight pouring out of it from somewhere deep inside. Walk
  into the light: a shortcut that skips ahead to Level 39's flooded tunnels (its way back is a dead
  end of those tunnels that ends in the same daylight).
  Bigger stretches of the level are one strange space (`/backrooms anomaly locate
  poolrooms/...`): **sky courts** (`sky_court`), open-air courtyard pools between plain grey
  concrete walls, heavy beams across an open roof of blue sky, the water bright turquoise, a dark
  covered deck along one side and dark doorways out; **glow halls** (`glow_hall`), long, tall,
  dark halls with a channel of glowing cyan water down the middle, tall tiled piers along both
  sides with a strip light in every other opening, its reflection lying in the water, black aisles
  behind them and dark slots high in the walls; and **tangles** (`tangle`), dark flooded rooms
  crossed by a tangle of low tiled walkways, ledges and blocks, lit only from under the water.
- **Level 37 ("Sublimity"):** reached up the long stair of the Poolrooms' great pool hall. Pristine cream-white mosaic on
  every surface, floors flooded ankle-deep in clear sea-green water, deep pools with no ledge and
  chrome ladders, forests of round pillars standing in the water, wavy walls and rounded corners, round arches
  and circular tunnels fading into darkness, domed rotundas with an oculus and softly lit arched
  alcoves, a spiral stair of floating treads climbing a light well toward the sun. Warm daylight
  comes from nowhere: skylights and tall slit windows are tiled niches lit by a source you never
  see (brightest at the back, fading toward the room), and throw soft-edged stripes and patches of
  sun across the tile and water, with a faint glow around each opening, while the long tunnels stay dark.
  Calm and quiet: gentle lapping, distant drips, no monsters, no events, and your **sanity slowly
  comes back** (+0.6/s) anywhere on the level. Backpacks on the dry pool decks and at the top of
  the spiral stairs hold almond water and calm pills. The way on is down: a round rotunda with a
  **pit of deep, dark water** in its middle (one per ~1000 x 1000 blocks; `/backrooms locate` points to
  it). Walk down the floating treads into the water and dive; a gentle current draws you down
  the shaft and up into Level 39's flooded tunnels.
- **Level 39 ("Megalophobia"):** you come up out of Level 37's pits (or the Poolrooms' sunlit archway) into long, white
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
  the ground drop back into the tunnels. Two ways back for whoever skipped ahead to here: a few
  dead ends of the tunnels (about one in eight) end, behind their round arch, in a wall of warm
  daylight instead of the two locked doors; walk into the arch and you are back in the Poolrooms.
  And about one house in seventy has a moulded-clay front door that opens: step through it and
  you are back in Level 9's suburbs. The way on is through the mill: walk into one of the
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
  out: houses whose doorway is pure black lead on to Level 228, flooded houses (knee-deep water,
  steps going down into murk) lead to Level 7, and the light behind the throne leads to Level 1.
  And about one house in twelve has a moulded-clay front door like Level 9's: step through it
  and you are back in Level 9's suburbs.
- **Level 228 ("Hall of Mirrors"):** reached through a dark doorway in Level 94. An abandoned
  carnival mirror maze, one per world, shared by everyone: you step out of a tall mirror in the
  ticket hall, under a painted funhouse facade ("HALL OF MIRRORS", its marquee dead but for two
  failing bulbs), past the cracked ticket booth and the sagging queue ropes, through the turnstile
  and a black velvet curtain into the maze. Four mirror halls follow, each its own look, joined by
  narrow backstage passages (plywood, crates of spare mirrors, a broken mirror stack, staff notes):
  the **Crystal Maze** (turned-wood columns, gilt frames, dark boards), the **Alhambra Hall**
  (slender painted columns, horseshoe arches, palm backdrops, a dense forest of columns), the
  **Funhouse** (barber-striped columns, sagging canvas, distorting mirrors on the backdrop) and the
  **Glass Labyrinth** (black steel posts, glass and mirror, almost no light), then the **final
  chamber**. Every hall is a regular grid (a column every 3 blocks, full-height panels between:
  mirror, clean glass, painted panel, an arch or an empty frame) laid out as a kaleidoscope: its
  content is exactly mirror-symmetric across every line that carries a mirror, so what you see in
  one is the real space beyond it, and behind the perimeter and far mirrors a sealed copy of the
  hall runs on 24 blocks into the fog (`world/layout/Level228Plan`, `Level228Maze`). Nothing in the
  reflections is faked by the renderer, so they light, fog and shade right under any shader pack,
  and you can't tell a reflected corridor from a real one until you touch it: the colonnade goes on
  forever, empty frames look exactly like mirrors, perimeter "frames" are clean glass. Now and then
  a hall's shifting panels change (a mirror becomes an empty frame or back, the whole pattern at
  once, only in a hall nobody is within 8 blocks of; the way through always stays). One mirror in
  each of three halls shows a little lit parlour that isn't there; next to one twin mirror per
  hall the reflection shows an opening where the real wall is a panel: push it for a moment and it
  opens, for everyone. In the octagonal final chamber stand three identical antique mirrors, a
  candle above each, nothing else: only one shows a true reflection (fixed for the world); the
  others are subtly wrong, for everyone alike. Walk into the true one and you step out of a mirror
  into the carnival's exit hallway ("THANK YOU FOR VISITING", cold light, leaves blown in); walk
  into a wrong one and you quietly step out of a mirror at the start of the Glass Labyrinth again.
  The hallway is safe (no anomalies, no imitator); go through its door into the vestibule and you
  are ready: when every active player in the level is ready (or you are alone) everyone goes on to
  Level 369 together (the others see "Waiting for N others..."). Leaving, disconnecting or dying
  drops you from the count. No damage, no spawns, no random teleports. `/backrooms locate` points at
  the final chamber (from the hallway, at its door). Testing: `/backrooms maze
  <foyer|crystal|alhambra|funhouse|glass|chamber|exithall|backstage>` jumps to a zone's start,
  `/backrooms maze shift` forces a shift, `/backrooms maze answer` names the true mirror.
  - **Reflections** (`client/level228/Level228Reflections`): every player (you too, first person
    included) is drawn again at every visible mirror image as a stand-in — same skin, layers,
    armour, held items (mirrored hands), crouch/swim/use animations; real bodies standing beyond a
    pane are hidden so only their reflections show; stand-ins never carry name tags (a name tag = a
    real body). Funhouse mirrors stretch/squash/waver your reflection. Rare personal lies on your
    own reflections only (head slowly turns to look at you while you stand still, lags behind, keeps
    walking when you stop, stops while you walk, one reflection missing) — never in the final
    chamber/exit hallway, never on the puzzle mirrors. The two wrong exit mirrors carry a fixed,
    world-seeded flaw for everyone (0.4-0.7 s late, item in the wrong hand, a worn piece missing,
    head nodding out of time, unmirrored, its candle out in the image, or the ticket stub in front
    of it missing from the image). Debug: `/backrooms mirror lie <look|lag|walk|stop|vanish|off>`,
    `/backrooms mirror info`, `/backrooms mirror stage <entities|block_entities|solid_blocks>`.
  - **Props** (`client/level228/Level228Props`, `Level228MirrorSurfaces`; meshes and sheets from
    `tools/level_assets/level228_props.py`): all architecture and decor is a static prop renderer
    outside the full block atlas. Crystal Maze: turned walnut columns, carved gilt friezes,
    raised-and-fielded panelled walls, coffered ceilings with gilt bosses. Alhambra Hall: slender
    marble columns, true horseshoe arches (alternating voussoirs, soffit, alfiz, lattice) over every
    bay, star-tile dados under carved stucco, painted beam ceilings. Funhouse: barber columns with
    bulb sockets, scalloped crests, hand-painted carnival boards (clown, strongman, fortune-teller),
    sagging canvas. Glass Labyrinth: riveted rusted steel posts and trusses, rubber mats, caged cold
    work lamps. Foyer show front with marquee, ticket booth (CLOSED card, price board), stanchions,
    posters; staff notes, crates of spare mirrors, shards, cobwebs, leaves, litter; the exit
    hallway's signs; the chamber's antique frames. One vertex buffer per chunk and sheet (vanilla
    entity render type drawn at AFTER_ENTITIES, so Iris packs and Veil light them), light baked per
    vertex, built a slice per frame, rebuilt only where a shift changes something; bulbs glow per
    frame from `Level228Bulbs.glow`. Mirror panes get an aged, tint-free silvered surface (dust,
    smears, desilvering, cracks, shattered, funhouse chrome, clear glass) with grazing-angle
    darkening, drawn without depth writes so the reflection keeps its depth. About 10 MB of sheets
    (6-bit colour, cropped). Previews from the real floor plan: dump it with the test-source tool
    `Level228PropDump`, then `level228_props.py --real <layout.json> <dir>`; `--sizes` checks the
    texture budget; `Level228PropCoverageTest` checks every planned prop has a mesh.
  - **The imitator** (`world/Level228Imitator`, `client/level228/Level228ImitatorClient`, drawn by
    the reflections' stand-in renderer): rarely (a player is first due 4-7 min after arriving, then
    every 8-15 min per group: everyone within 40 blocks shares the cooldown) something wearing one
    of you (usually your own face, sometimes a teammate's) stands a few steps behind you in the
    mirrors, walking your trail and copying you perfectly; then wrongly, one way per episode (a beat
    late, stopped dead where nobody stands, or its head leaning over); then it stops and watches you,
    with one faint glass tick; then, the next moment you look away, it is gone. Rarely it is suddenly
    right behind you in every reflection instead, until you turn round. It exists only in images:
    the server keeps its point in real space and every client draws it at that point's images, so
    everyone sees the same figure in the same glass. No chase, no damage: seeing it costs a little
    sanity (0.2/s, 0.35/s while it watches). Tells: its image is a rotated copy, not a reflection
    (skin details and the held item on the wrong side), and its eyes catch the light. Never starts
    in view, needs a mirror nearby, never in the exit hallway or the final chamber; it ignores
    creative and spectator players unless forced. Test: `/backrooms imitator <show|off>`.
  - **Light and sound**: warm ~2500 K bare bulbs (Veil point lights + invisible `l228_bulb` light
    blocks for Iris, in the sealed images too so reflections are lit alike), ~40% dead, a few
    failing on a game-time pattern shared by all players and their reflections, red EXIT signs,
    cold exit-hallway tubes; dark warm-grey fog showing 4-6 colonnade repetitions; neutral-warm
    camcorder grade per zone; per-zone reverb and floor footsteps (boards/tile/lino/plywood/
    concrete); near-silent room tone, building creaks, mirror ticks, a far warped band organ now and
    then, wind in the exit hallway, total silence in the final chamber (music stops there).
- **Level 369 ("Comely"):** reached through the door at the end of Level 228's exit hallway: you fall out of a skylight into
  soft flowers (no landing ever hurts here). An endless indoor garden: rooms of pastel-painted
  plaster, every floor a lawn carpeted wall to wall with grass and flowers. Long white hallways
  with tall windows full of pale blue light down one side, closed doors down the other and blue
  cornflowers in the grass; low, wide peach halls of cream pillars under salmon ceiling tiles,
  red daisies and winding brick paths; tall mint, lavender, cream and rose rooms under skylight
  shafts, bushes along the walls and young trees under the light; and, now and then, a dark sage
  room with a single doorway, glowing flowers in the grass and glowing vines hanging from the
  ceiling. The halls are big: pillar halls open a hundred and more blocks across, hallways run as
  far, and now and then one goes on for nearly four hundred. Every floor is one soft meadow, thigh
  to waist high, that leans and rolls in waves of wind (not under shader packs), rustles as you
  wade through it, and sheds petals and
  pollen into the light. Around you it is real grass: thousands of thin, smoothly shaded blades
  and flowers on stems (no textures, no pixel edges), blending into the block meadow further off
  (without a shader pack or Sodium). Each kind of room has its own light: cool white hallways with shafts of
  sun from their windows, pillar halls in a low golden sunset, soft daylight under the skylights.
  Nothing lives here and nothing happens. The way on is a great **arch of vines** filled
  with a curtain of hanging flowers (one per ~1000 x 1000 blocks; `/backrooms locate` points to it): walk
  into the flowers and you come out in Level 490. See `docs/level-369-comely.md`.
- **Level 490 ("Penance"):** through Level 369's arch of vines you come out among crumbling ruins
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
  colossal chain; climb the ladder up it to the collar and step off: the last leap, back to reality (the tape stops). Fall
  anywhere else (into the clouds, or a long fall onto something) and you wake at the last radio you
  used, unhurt. And something hunts you: a colossal shape of drifting smoke like the Mind Flayer,
  long spider legs fraying into tendrils, a crown of spines and a scatter of dim eyes, hard to see
  until the lightning shows it. On the walk you hear it scraping below the edges, then see
  it far off in the clouds when lightning flashes, then its hand comes over the edge for you if you
  stand about in the open (a roof or a lit lantern keeps it off). On the tower it rises slowly
  through the sky from far below the clouds after you, faster when you stop, slamming its hand onto
  the stair ahead and tearing it out, hurling masonry at you, shaking the whole tower, the world
  going silent just before each attack; it reaches for you if it draws level; inside the tower you
  are safe. At the top it holds the tower in its hands and stares at you, then floats in
  the sky watching as you climb the chain. If it catches you, you wake chained on a punishment
  platform behind you: pull at the chains (jump) to break free.
- **Level 227 ("Megaflora"):** reached only by being dragged in out of the Backrooms' own nature
  (see above) or with `/backrooms enter level_227`. A tree the size of a planet hangs upside down
  over a black void, its roots reaching up out of the sky. You arrive in a misty rainforest that
  turns out to grow on top of one of its branches: a trunk about 1800 blocks across, a dozen main
  branches leaving it 560-680 blocks up and running out for thousands of blocks, gently downhill
  (walk outward and you slowly descend), splitting into sub-branches and limbs that end in tapered
  tips or broken stumps showing their growth rings. Here and there two branch systems have grown
  into each other or are joined by a thin bridging limb: the only ways across, so look for them.
  On top: 4-8 blocks of rooted soil, moss and grass with roots snaking over it and forest pools in
  the dips, under a forest of giant trees 25-70 blocks tall (buttress roots, layered crowns, lianas
  you can climb, epiphytes, bracket fungi), shrubs, tall grass, giant ferns, fallen logs and snapped
  stumps. The pools (`world/layout/Level227Ponds`) are real, still water you can jump, fall and dive
  into and swim in: one flat surface each, a dark silty bottom that shelves down a block at a time
  (1 deep at the rim, 2-4 in the middle of the bigger ones), banks at most a block above the water
  so you can always climb out, and open sky over them (no roots, logs or trunks in or over the
  water, nothing solid within 3 blocks above it). Jumping works everywhere in Level 227: you hop
  over roots and into the pools. Pools only appear in newly generated chunks; where you have
  already been, the old one-block puddles stay.
  The water is a tea-coloured forest pool (`client/water/WaterStyle.LEVEL_227`): clear amber-green
  in the shallows, peaty green-black where it is deep, mirroring the canopy, with soft ripples,
  light patterns on the bottom where light reaches and sun glints from the real sun overhead; under
  it a murky green haze. With a shader pack (Iris, e.g. Complementary) the pack draws the water
  instead, tinted the same amber-green by the biome's water colour. Anything falling in splashes
  by how fast it hits (a plop, a splash, a belly-flop from six blocks up): drops, rings, foam,
  bubbles dragged down, and rings rippling across the surface (`client/water/WaterSplashes`);
  wading and swimming leave small rings and soft water sounds. With your head under, a quiet
  muffled hush replaces vanilla's ocean sounds and everything else goes dull and quiet
  (`client/water/UnderwaterAudio`). Test it with `/backrooms l227 pond [height]`.
  At the edges the soil thins to moss-streaked bark curving away into nothing, where purple
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
Every floor is endless (but Level 228, one finite maze), and its way out is far away. On Levels 0, 1, 4, the Poolrooms, 369, 9
and 94 there is about one main exit per 1000 x 1000 blocks, and wherever you arrive from another
level, the nearest one is at least 400 blocks off (`world/Travel.clearOfExits` moves the arrival
point away from it, the same way for everyone coming through the same exit, so friends still land
together). Level 2's corridors have an EXIT bay about every 1000 blocks and Level 37 a pit per
~1000 x 1000 blocks; Level 228's one way out is the final chamber at the far end of its maze. Side ways out (the flickering
wall in Level 0, Level 4's exit elevators, Level 37's openings, Level 94's flooded houses) and
Levels 7, 39, 490 and 227 keep their own spacing.

**Finding the exit** (subtle on purpose, `world/ExitHints`, `client/hint/ExitHints`): nothing on
screen points the way, but every level's ordinary exit can be tracked down three ways. Secret
ways (the sunlit archway, the clay doors, the throne light) are never hinted.
- **Listen.** Every exit gives off a faint sound of its own that fades in within about 40 blocks
  and comes through the walls: a draft up Level 0's stairwell, the hollow drone of Level 1's,
  Level 2's EXIT door buzzing, a cold draft hissing under Level 4's fire door, open air spilling
  down the Poolrooms' great pool hall over its lapping water, water rushing down Level 37's pit,
  highway hum through Level 9's underpass, the dark breathing behind Level 94's dark doors,
  bubbles rising from Level 7's sunken door. Unlike the
  rest of the level's sounds, it never moves.
- **NightShot.** Earlier wanderers painted arrows in something that only the camcorder's
  infrared shows. With NightShot on, a few dark arrows appear on walls and floors around you, each
  pointing toward the exit, or down when it lies far below. Unlike the arrows drawn on Level 0's
  walls, they never lie. They thin out along the path: about one per dozen blocks of wall on
  Level 0, fewer on every level after it, and from Level 7 on they only show near the exit (Level
  490, the last: a rare one within 60 blocks).
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

**Curved tile (Level 37's pillars, rounded corners, arches, tunnels and rotundas; the Poolrooms'
sunlit archway)** is lit by the mod while each chunk is built (`client/CurveTileRenderer`), not by
vanilla's block lighting, which can only light a curve one block at a time (dark blocks and
triangles on curved ceilings). Light and ambient occlusion are sampled per vertex half a block in
front of the surface, so they run smoothly round the curve and match vanilla smooth lighting where
a curve meets a flat wall; the face shading follows the way the surface faces (the same shades
the flat walls get for north/south, east/west, up and down). The flat Level 37 tile walls now use
vanilla's normal face shading. With smooth lighting off the curves are flat-lit like every other
block. It works the same under Sodium/Iris (shader packs do their own face shading from the
normals); it does not need NeoForge's experimental light pipeline.

### Supplies

Containers are filled the first time anyone opens them, from a loot table for the floor you are
on (`data/backrooms/loot_table/supplies/<crate|backpack|locker>/<level>.json`, falling back to
`default.json`). Breaking one spills what is left inside.

- **Wooden Crate** (27 slots): mostly food: canned food, backshrooms, bread, almond water, energy bars.
- **Backpack** (9 slots, lying in corridors): few items but the best ones: pills, bandages, energy
  bars, and the best chance of a camcorder battery.
- **Supply Cabinet** (27 slots, a filing cabinet marked SUPPLIES): bandages, calm pills, energy bars,
  some food. (The green metal Supply Locker is saved for a future level and isn't placed anywhere yet.)

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
- **When it's low** (mostly only you notice): below 70 you hear knocks and footsteps
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
concrete room with a metal door frame in the Habitable Zone, a dry tiled room on a walkway in the
Poolrooms, and on Level 9 a house with its porch light on and the sign over the front door.
Level 2 has none (nothing opens off its corridor): its checkpoint radios are set into the wall.

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
- **Shared checkpoints (multiplayer):** saving at a radio also leaves a checkpoint for your
  friends on that level. Everyone else on the level who can use it sees **NAME SAVED A
  CHECKPOINT** on their camcorder.
  - **Who counts as a friend:** players on the same vanilla scoreboard team (`/team add cavers`,
    `/team join cavers Alice Bob`). Players without a team all count as one big "everyone" team,
    so on a small server with no teams set up, everybody shares with everybody.
  - Each level keeps every player's latest save (per team), so saving your own checkpoint never
    wipes out your friend's. When you die, the newest one saved by someone else on your team is
    used. It is stored with the world, so it is still there after a restart or when the person
    who saved it has logged out, and it stays when they leave the level (others may still be
    down there).
  - **If you die on that level without a checkpoint of your own**, you wake up at your friend's
    one instead of in the real world (on the floor in front of the radio, sanity reset, the same
    as your own checkpoint).
  - **If you have your own checkpoint too** and your friend's one is newer or at another radio,
    the death screen shows two extra buttons: **Respawn: my checkpoint** and **Respawn: NAME's
    checkpoint**. The normal Respawn button keeps your own.
  - A shared checkpoint is skipped if its radio is gone or something is blocking the spot in
    front of it.
  - **Turning it off:** `/gamerule backroomsSharedCheckpoints false` (it is on by default). Then
    every checkpoint is private again, exactly as above.

### Hiding

You can hide in furniture that is already there: a **Supply Cabinet** (the filing cabinet marked
SUPPLIES), an **office desk** (Level 4's offices and cubicles, Level 0's furniture heaps,
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
  2 seconds): that one comes over and pulls you out, then attacks. The **Bacteria** takes its
  time: it creeps up to your hiding place and prowls round it for 15 to 25 seconds, holding on
  to the furniture and the walls, changing its grip slowly, standing still with a hand on the
  furniture next to yours, reaching across the front; you hear it sniff and its claws scrape, and
  you see it through the vents. Then it rips the door open, cranes its head in, and drags you
  out. Get out yourself while it prowls and it is after you at once. A monster that didn't see
  you get in searches where it last saw you and gives up as usual. Nothing new spawns for you
  while you hide, Wretches aren't frightened by you and Level 39's windows don't see you. A
  monster within 16 blocks still makes it nerve-racking: your sanity drains a little.
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
  makes both shorter, your flashlight doesn't matter). When it spots you it doesn't freeze: its
  head snaps onto you, it bends right over to look while it swings round, and less than half a
  second later it is running at you at about 5 blocks a second: faster than you walk, a little slower
  than your sprint on average, though its pace surges and slackens, and in the last few blocks it
  bursts forward at twice that. Sprint the moment it sees you and you slowly pull away; hesitate and it has you.
  Breaking line of sight is still the surest escape. Then it runs to where it *last saw* you, searches
  for a few seconds (you hear it sniffing), and when it gives up you hear a slow sigh moving away.
  If it hears you sprint it walks over to the noise and swipes at the spot. It is quiet: wet, slow
  breathing, soft clicks of its joints and the scrape of its claws, never a shriek. With NightShot
  on you can see it even in total darkness.

  How it moves (like the Bacteria of the XP Games add-on): it stands about 2.5 blocks tall, so its
  head clears the ceiling, with its neck pushed forward like a vulture's and its head tilted to one
  side, and its arms are far too long for its body (over 3 blocks). Standing, it holds them like a
  mantis's forelegs, elbows out in front and the claws on the floor before it, and only the claws
  slowly move: down to the floor, forward, back. If it can see you, its head slowly turns onto you
  and stays there, wherever its body goes. It walks like a mantis in long, slow strides on bent, wide-apart legs,
  one arm cocked forward with the claws hanging while the other reaches out and back to the floor,
  swapping with every step. It runs (like Kane Pixels' Bacteria) hunched and never in a steady
  rhythm: no two strides alike, now and then a stumble or a lurch, the torso rolling and twisting,
  the head jerking while it stays on you. Its arms vault it along like huge spider legs, one
  planted two blocks out to the side (that claw stays put on the floor) while the other whips up
  and over, flailing and now and then flung up or forward, dropping little black flakes; in a
  corridor the claws plant ahead instead. It leans into corners and skids when it stops. Sometimes,
  before it has properly seen you, its head slowly comes out from behind a pillar or door frame to
  look at you. Within 3 blocks it brakes to a stop in a wide crouch and stares at you for a moment, then
  leaps with its arms flung straight out and closes them around you. It stops about a block and a
  half away and holds you in a cage of its long arms, the claws planted on the floor behind you
  (its body never ends up in your face); the hits come from the arms, and now and then one sweeps
  up across your face while its head slowly creeps in to look at you. Get more than 2.5 blocks away
  and you have broken free; if its leap misses, it swipes at you with one arm. Hit it and its head
  snaps back for a moment. After a kill it folds over the body for a few seconds. Searching near a
  wall, it feels its way along it with one hand. If it saw you hide in furniture it creeps up and
  prowls round your hiding place for 15 to 25 seconds, holding on to it and the walls (see Hiding),
  before it rips the door open.
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
  walk away and want you to follow... always away from the way out. Get out of the water and dry
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
  Roth syndrome, drink at the water's edge; they flee from light and noise but lash out if you
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
- **Blackout Zones:** Patches of the maze where every light is dead and the walls are rough. Step in and the lights behind you die in a wave rolling away from you; a minute later (or once you're out) they come back from the edge inward. The buzz you hear seems to lead the way out, but it never stays in one place, though the longer you're in, the more honestly it points.
- **The Manila Room:** A very rare brown wall with a wooden door. Behind it is a quiet beige room with a table, two chairs, supplies and notes from volunteers, and a piano playing from nowhere. It calms you faster than anywhere else, the lights never go out in it, and if you come back later someone will have put something in the cupboards you emptied. Sign the guestbook. Read the notes: the way to Level 1 is close.
- **Shifting pillar halls:** Big bright halls full of pillars that are never quite where they were when you look back. You'll hear them settle behind you, and sometimes half the hall is on a different grid from the half you were watching. Mark a pillar with chalk and look away. Only the pale arches in the middle stay put.
- **Pit rooms:** You hear cold air breathing up through the walls before you see it: a big room whose floor is a grid of square shafts with narrow carpeted walkways between them, and a wooden door on the far side. The light dies two blocks down. Don't fall in. You will land, hurt, somewhere very far away, under a hole in the ceiling.
- **The warren:** A patch where the maze closes in to slots: corridors one block wide that twist, branch and dead-end at random, mostly dark, the odd tube burning somewhere down one of them. Here and there one opens into a small room with one thing in it under one small light: supply crates stacked in a corner, a heap of boxes, a filing cabinet, a desk and its chair, a water cooler, or a chair on its own. Every slot leads somewhere, and two openings on each side lead back out into the maze. About one per 256x256 blocks.
- **Hidden rooms:** Some of the big blocks of wall are hollow. Look for a one-block crack with a kink in it, a doorway piled with boxes that has a gap at one end, a missing ceiling tile with a ladder going up into the plenum, or (only by flashlight, on the dark side of a block) a low gap in a corner. Each room has someone's notes, supplies they left for the next person, and something about it that isn't right. The one above the ceiling has a radio that saves your checkpoint.
- **The lights, locally:** Now and then, never more than once every few minutes, the lights do something to you alone: a room's tubes stutter out as you walk in and come back when you leave, the tubes behind you in a corridor die one by one, or a lit district far down a long view goes dark while you watch.
- **The Atrium:** A big, low, dark hall whose ceiling stops in the middle: above it a square shaft 22 blocks across goes straight up as far as Level 0 goes, its yellow walls fading into the dark, with a ring of small square lights round its top edge far overhead. Squat pillars carry the shaft's walls; between them lit bays open into the low hall around it, and corridors lead away from the hall in every direction, two straight out of each side and one from each corner, back into the maze. Under the shaft it is dark, and there is nothing there but cold air moving high up.
- **The Shaft Hall:** A wide, low, almost dark hall whose ceiling is full of holes. Each one is a tile-lined shaft going straight up, and a few have a light burning far up at the top. You hear things up there now and then, and once in a while a ceiling tile comes loose and falls the whole way down. Mind the broken tiles on the carpet.
- **The Front Office:** You'll hear it before you see it: an alarm and a red light turning in a huge dark room, around a block of yellow wallpaper with a door and a window drawn on it, and a row of empty chairs facing the drawing. The PA sometimes asks you to come to the front office. If you go while you're expected (or simply arrive at the right moment), the alarm stops and there is light under the drawn door. Walk into it. Behind it is a small warm reception office that can't possibly fit in that block: supplies, Almond Water, a radio that saves your checkpoint, and another drawn door under an EXIT sign that really is the way out.
- **The street:** A doorway opens into a hall so big it doesn't belong: yellow walls going up and up, rows of light panels twenty blocks overhead, huge ceiling fans slowly turning up there, and down the middle a whole suburban street: a pale road with a double yellow line, sidewalks, garages, gabled houses with porch lights, picket fences and mailboxes, a stop sign at the far end. About one door in three opens: into someone's living room, into more yellow wallpaper and carpet, or into a garage with supplies.
- **The stack:** A square void through Level 0, 22 blocks across, floor after floor of open office space around it, down into the dark and up out of sight. Most of those floors aren't really there: the game draws copies of the real ones (their blocks, their light panels, the chair someone left), so they look the same from above and below. On one side the floor is gone but for a ledge along the wall, and a floating stair climbs from it over the drop into a hole in the ceiling: a sealed room with supplies and a radio. Fall in and you wake somewhere else, hurt.
- **Findability:** One of these five set pieces (the Atrium, the Shaft Hall, the Front Office, the street and the stack) is built in (nearly) every 256x256 square of the map, so together with the pit rooms and pillar halls there is always a big anomalous building close by (measured over four seeds with the first three: median 39 blocks away, 99% of places within 116 blocks, the worst spot 183).
- **Left behind:** Like the film's tapes of the Lobby, the big halls (the Shaft Hall, the Front Office, the stack) have abandoned furniture piled in their open floor: old dressers and shelves, side tables, armchairs, filing cabinets, cardboard boxes, the odd thing upside down on top of another. In the pit rooms, the pillar halls and (more of them) the blackout zones, ceiling tiles are missing here and there, a cable hanging out of the hole, the tile lying broken on the carpet below. In the Red Rooms a chair lies on its back in the first room and tiles have come down in the others (never by the crawlways), the hidden rooms each have an old dresser and a fallen tile, and the Manila Room's volunteers have added a side table and a bookshelf. None of it ever blocks a way through.

**Level 1**
- **Crate halls:** In some of the open garage halls, aisles of steel pallet racking stand in a brighter, better-lit hall. Wooden crates turn up there: you hear something settle behind you, and there is a crate that wasn't there before. When the lights fail, the crates are gone; when the power comes back, new ones are waiting. Not everything inside is worth taking.
- **Painting rooms:** Standing alone in a hall, a small closed room is covered in crude paintings and drawings. Look away, or wait for the lights to go out, and they are not the pictures you remember, if they are there at all.
- **Fog patches:** Here and there a low, sourceless fog lies along the floor, the air turns grey and damp, and puddles of condensation gather and dry up again in places you weren't looking. Listen for the drips.
- **The Corrupted Corridor (Level 1.1):** Very rarely, a doorway in a garage wall opens onto a long white-cinderblock corridor with a blue tiled floor. The further you go the worse it gets: the colours go wrong, the same stretch of hallway seems to repeat, the dark swallows your light, and the walls turn black and white and won't hold still. At the very end, a light beckons.

**Level 2**
- **Series circuits:** The corridor's light panels are wired in series along the line. Now and then a circuit gives out with a sharp pop, and a whole section of the line goes dark at once (a gap opens in the row of lights ahead), sometimes for nearly a minute, before the lamps stutter back to life. Sometimes a fault runs down the line: three sections pop out one after another, toward you or away. A dead section is dark but never blind: the floor edge, the walls and the pipes stay faintly visible in a cold blue-grey.
- **Industrial lighting:** No two bulbs are alike: new ones burn a little whiter, old ones dimmer and browner, and a few failing ones sag now and then. Beside bulkhead rings and the exits, red or amber battery emergency lamps wait on standby and click on when their section's circuit dies or the power fails. Wet floors, puddles, sweating pipes and bare metal catch the lamps in small highlights.
- **Brownouts:** Some sections never recovered from the Blackout. Almost every bulb is burst, the machines are silent, and only a few violet emergency lights still glow. Burst pipes have left pools of thick black sludge that clings to your boots.
- **Oddities:** Rare, quiet things that are simply wrong, if you notice them (`world/layout/Level2Oddities`). A **valve manifold** where every single quarter-turn valve has been turned the same way, all shut, yet water still rushes through the header overhead. A yellow gas **pipe** that rises from the floor and bends into one wall, and straight across the lane comes out of the other wall upside down: high up, bending into the ceiling, its label below it; knock on one and the other answers. A run of corridor where **the section number repeats**: three or four sections in a row stencilled with the same number, each under the same dead panel, with the same chalk cross some earlier visitor drew underneath to check. And a **pressure gauge** set into the wall at eye level whose needle reads your own heart rate (BPM / 10 on its dial) and ticks with every beat.

**Level 4**
- **Looping stairwells:** Grey stair cores stand in some open-plan floors. However far you climb or descend, the sign on the landing always says 4, and somewhere far away you hear footsteps that never arrive. Once in a while the stairs let you out somewhere else.
- **The Window:** Most windows are blacked out and only glow, but very rarely one is clear: a blurry, sunny place that feels like somewhere you've been. If the panes around a window are taped over with cardboard, there's a reason. Don't get close, and don't listen.
- **Vanishing elevators:** In some lobbies one elevator stands open and lit. Look away and it may not be where you left it. Step in and it will take you somewhere that looks exactly like here. (The open elevator under a green EXIT sign is different: that one is a real exit.)
- **Level 4.3, "The Cubicles":** A dim white series of hallways leads to an endless cubicle field in white fog, full of the sounds of people working whom you never see. The longer you stay, the harder it is to leave. Walk back the way you came.
- **The Occupied Floor** (rare, open-plan floors): around one column, four neat pods of desks with a monitor on every desk and every chair pushed in, every light working, not a tile missing, not a dent in the carpet. Nobody is there. It does nothing.
- **The Inside-Out Room** (rare, office corridors): behind an ordinary office door the carpet runs up the walls and across the ceiling, and the floor is ceiling tiles with a light panel glowing up out of it. It does nothing.
- **The Cooler Row** (rare, open-plan floors): a short drywall corridor standing on its own, every light in it on, lined with identical water coolers at perfectly even spacing. All of them are empty, except the last one at the dead end, under an EXIT sign that leads nowhere.

**The Poolrooms (and Level 37's water)**
- **Sound in the pools:** In the Poolrooms and Level 37, footsteps and voices die out a few blocks away and everything sounds muffled; only the water stays clear. Every so often the water grows loud and close, drowning out everything else for half a minute.
- **Dark corners:** Some lit rooms have a corner that no light reaches: a small nook of dark, ankle-deep water behind a tiled screen, with faint chanting drifting out of it. Stay inside too long and the light drains away, and you may find yourself somewhere that looks exactly the same, but red, where the water holds on to you and the chanting never stops. The only way on from there is the stair of an exit hall, its niche at the top dark now but still the way out.
- **Sky courts, glow halls and tangles:** whole blocks of rooms that are one strange space (see the Poolrooms above): an open-air courtyard pool under a painted blue sky, a long dark hall with a glowing channel down its middle, and a black, flooded tangle of low walkways over glowing water.
- **Stair pits:** Some rooms are one deep, dark flooded shaft, with a staircase running straight down into the water. Down there your breath runs out fast and currents drag you around. The stairs are always the way back up.

**Level 37**
- **Void openings:** In a few slit-window walls two slits have merged into one wider, deeper lit niche above a knee-high ledge. The closer you get, the more the light swallows the picture, and the louder and worse it grows. Those who climb in and stand in the white wake somewhere dark in the Lobby, shaken.
- **Dark openings:** Rarely, a dim corner of a pool deck holds a round arch framed in dark tile and filled with absolute darkness, breathing cold air. Walk into it and the world fades to black; you come back out in the Poolrooms.
- **Looping tunnels:** Some of the long dark round tunnels don't end. Keep walking and you keep passing the same stretch, the same drip, while the light fades a little each time. Turn around and you walk straight out; persist and it eventually lets you through.
- **Healing water:** Level 37's warm water soothes. Standing or swimming in it slowly heals you and brings your sanity back, fastest in the deep pools; dry tile only calms you a little.

**Level 369**
- **The drifting light:** Every few minutes a band of warm light drifts through the rooms like a slow wave. While it passes over you the room turns gold, your sanity comes back and you slowly heal. Then it moves on and the room falls back to its usual light. Nothing here ever wears your nerves down.
- **It makes you forget:** After about five minutes the level starts taking things, and by twenty-five it has taken everything it can. The camcorder's readouts fade one by one (battery, then the tape counter and clock, then your heart rate, then REC). Doorways you came through are walled over once they're behind you, and a new doorway opens somewhere else in the same wall. Now and then something slips out of your inventory and lies in the grass a few blocks behind you; you can find it if you look (never the camcorder, or what's in your hand).
- **Leaving brings it back:** The moment you leave, by the arch of vines or any other way, everything comes back: what you lost returns to your inventory, the doorways are where they were, and the next visit starts fresh.

**Level 9**
- **The Mangled's fog:** Sometimes the air turns ice cold before the fog rolls in, and the streetlamps go silent. Somewhere down the street a black pillar rises into the sky, and booms roll out of the fog. When the fog lifts the pillar is gone, and the road where it stood has a crater in it.
- **Clipped houses:** Now and then two houses have grown into each other. Walls run through the middle of rooms, furniture sticks out of walls, and there are two front doors. Some of the walls in there aren't quite solid.
- **Twin houses:** Two identical houses stand side by side. Leave one by its back door and you may find you aren't where you think you are. The front door always leads out.
- **Grass walkways and Level 9.1:** Rarely, a paved path runs between two houses and on into the woods, getting colder as it goes, to a clearing of tall grass. Stand in the middle of the clearing and you are in the Crimson Field: endless grass at night under a red glow that pulses on the horizon. Don't walk toward the glow. To get home, follow the path back to where it started; the rare foxhole hatches in the ground lead elsewhere, one way only.
- **The chairlift (side entrance to Level 39):** Very rarely, somewhere in the Crimson Field, you hear a cable creaking before you see it: a small frozen lift station and steel towers marching off into the fog, each taller than the last, with empty frosted chairs creeping along the line. Sit in the chair waiting at the station and it carries you up into a white-out; you can still hop off in the first second, after that the bar stays locked. Where it lets you go, you fall through the clouds into Level 39. (The way back: the odd moulded-clay front door in Level 39's town opens onto Level 9's suburbs.)
- **Missing posters:** Photocopied MISSING posters are stapled to streetlamp poles, front fences, garage doors and house corners. Right-click one to read it close up. Every so often the name and the face on it are your own.
- **Neighborhood Watch flyers:** The Watch's rules for staying alive on the street (the streetlights, the tall ones with a light for a face, the fog, porch lights, the signs that lie, not answering the door). Found pinned to porch posts (sneak + right-click to take one), in some mailboxes (right-click a mailbox to open it, once), and in Level 9's supply crates, lockers and backpacks. Right-click a flyer to read it; the Watch notice boards in front of empty lots can be read the same way.
- **Answering machines:** Some kitchens have an answering machine with its red light blinking: a message nobody heard. Right-click it to play the tape. The voicemails start out ordinary.
- **Kids' drawings:** Crayon drawings taped up in the children's rooms. Right-click to look closer.
- **Wrong houses:** About one house in thirty is wrong inside. In some, the living room's furniture is stuck to the ceiling. In others you walk through a door into the room you just left, the same down to the photo on the wall. And very rarely a house's front door opens onto a narrow hall with a door every few steps: it goes on much further than the house does.
- **Yards and street:** People left things behind. Some driveways and curbs have parked cars in faded colours. Some backyards have a rusty swing set whose seats sway gently with no wind and creak now and then. Lawn sprinklers still tick and hiss on their timers. You'll find a tipped-over tricycle, a ball or a bike lying in the grass, wheelie bins put out by the sidewalk, and some mailboxes with the red flag up.
- **Streetlights behind you:** Now and then, while you walk down a street, the lamps you've just passed go out one by one behind your back. Turn round and look at them for a few seconds, or walk far enough away, and they quietly come back on.
- **The dogs go quiet:** Every so often a few dogs start barking somewhere down the streets. Then they all stop at once, in the middle of a bark, and for a few seconds even the crickets are silent.
- **The night:** Outdoors you hear crickets, traffic far off that never gets any closer, and now and then a wind chime. It goes muffled when you're inside a house or under the underpass.

**Level 94**
- **Furniture drift:** The closer you get to the castle, the less of the houses is left: whole houses on the far hills, single rooms nearer in, and closest of all only heaps of furniture, which turn towards the castle when nobody is looking. Check the sinks.
- **The wandering door:** Somewhere on the castle's plateau or inside it stands a door with no wall around it. Look away and it may be somewhere else. Everyone who walks through it ends up in the same place.
- **The blinding hall:** Upstairs in the castle, behind the looping hall, is a hall so bright it whites out your eyes. Nothing chasing you can keep track of you in there.
- **Supplies:** The town fountain flows with Almond Water, the water towers have a tap at their base, and every milk van has a crate of Almond Milk at its back doors.

**Level 39**
- **Hostile windows:** Most stretches of the mill wall are infested with Windows, on the ground floor and the storey above. Come within ten blocks of an inhabited window and it wakes: the light swells and pulses, the blinds tremble, a dark long-armed shape stands behind them and coal ash drifts out. Stare and it pulls your eyes in. Linger and it shrieks and throws glass, and up close it breathes paralysing fumes; get within three blocks of a ground-floor window and it grabs you and drags you over the sill (the ones upstairs can only throw glass). Tear free past four and a half blocks, or survive three seconds pressed against the glass and you noclip through it (the way to Level 94). Every infested stretch has at least one inhabited ground-floor window, and `/backrooms locate`, the exit sound and the camcorder lead to the open ground right in front of the nearest one. While you stand there with its Window awake, the Windows upstairs hold their glass and just watch; step away and they come for you again. More than four people together and they stay quiet.
- **Inverted, merged, cut and floating houses:** Some houses stand on their roofs, two houses of different eras have grown into one, some are sliced clean in half like a dollhouse, and some hover a few blocks off the ground with no foundation at all.
- **Odd furniture:** Furniture on the walls, stacked, or hanging upside down from the ceiling.
- **Flooded rooms:** One room of the house is filled to the ceiling with water.
- **Bigger on the inside:** The rare house whose front door is unlocked opens onto an entry hall, and past it an endless run of rooms under a ceiling studded with bare bulbs that the house could never hold. Walk back through the hall to leave.
- **Vanishing doors:** In some houses, a door you walk through is a wall when you turn around. It comes back once nobody is near, or after a while.
- **Sounds from the holes:** If you hear scraping, then breathing, then a growl coming out of a hole in the ground, run: something is climbing up.
- **Hydrolitis:** The rare dark stretches of tunnel, where the light orbs are out and green motes hang over a filmy surface, carry the Hydrolitis plague. Wade there and it builds up: fever and chills, then weakness, nausea and delirium. Almond water cures it.
- **The M.E.G. survey camp:** Somewhere in each town the M.E.G.'s last expedition left a tent, crates and field notes (right-click to read). One of the crates has a false bottom.

### The camcorder picture

Everything you see in the Backrooms is filmed: a gentle wide lens, worn VHS tape (faint line
jitter and grain; colour fringes only while the tape misbehaves: something dangerous close by, low
sanity, glitches, a Smiler, a level change), soft bloom on the lights and a little motion blur
when you turn. On top of that each level has its own mood (`client/AtmosphereGrade`), applied
evenly over the whole picture and blended over about two seconds when you change level:

**The footage look** (on by default; FOOTAGE LOOK in the settings) makes the picture what a cheap
old camera recorded, after the found footage in the Backrooms film: a mix of tape and early
digital. The picture is read at about 480 lines, soft and then over-sharpened (pale halos round
dark edges), its colour bleeds sideways and sits in 8x8 compression blocks, and a faint ghost
echoes each edge. A cheap white balance turns the Lobby buttery yellow and the whites blow out
(except in the Poolrooms and Level 37, where the brightest parts roll off softly so skylights and
sunlit openings keep their shape).
Digital noise grows in the dark as the camera turns its gain up, and its auto-exposure hunts:
step from a dark corridor into a lit room and the picture flares, dips and settles. **4:3 FRAME**
(also on by default) puts it between black bars like the old camera's picture, with the HUD kept
inside the frame; the overlay itself is the camera's (REC and the tape counter top left, the
battery and heart rate top right, the date stamp in amber bottom right, the exit signal bottom
left). Turn the footage look off for a clean picture.


| Level | Mood |
|---|---|
| Level 0 | Kane Pixels' tapes: a soft, faded yellow-beige (never olive). With Veil the troffers are the real light: warm pools under each one, cut block-exact by every wall, post and door jamb (`client/light/Level0LightRenderer`, shadows from a top-down wall map of the 128 x 128 columns around you, `Level0WallGrid`), so jambs facing away go black and posts throw hard wedges; a weak bounce from the lightmap fills in under the pools and dies away into corners and wall feet, which a screen pass darkens from the same wall map (no halos). Lit doorways far down dark runs read as beacons (64 lamps within 42 blocks on HIGH). Without Veil the lightmap alone makes the pools, steeper, with contact shadows for the corners. Tubes differ in age, chosen by the layout (`world/layout/Level0Tubes`, a `glow` block state: full, worn, tired or dying, each with its own light level, so it works with or without Veil); whole stretches of ceiling were re-lamped at different times, so some areas are brighter than others. Each tube also has its own colour temperature (mostly neutral, some yellowed, a few cool). About one tube in twenty is faulty: it stutters in a short burst every 12-45 s, or now and then cuts out for a few seconds and re-strikes with a few flashes, clicking each time; the room under it dims a little with it. A blackout is a dying wave rolling out from a point near you (22 blocks/s, each tube popping), then a partial recovery: emergency lamps and a few tubes on emergency ballasts come on, half the 24-block patches come back after 20-60 s, a quarter when the event ends and a quarter stay dead for 2-4 more minutes (`client/light/Level0Power`). Server code can cut the power locally (`world/FixtureOutages`: a circle or a list of fixtures, cut, stuttering or as a wave; restored on demand). Almost no haze (6% from 20 blocks), dark wings crushed to black. F3 shows the Level 0 lights' GPU time |
| Level 1 | Cold, wet concrete: blue shadows, little colour, a dark blue-grey air; the lamps streak and sparkle in the wet floor, dark between them; nearly pitch black when the lights go out |
| Level 2 | Small pools under the recessed light panels, swallowed by a near-black haze 18-26 blocks ahead; pipe racks sunk in shadow right beside you; the picture's edges darkened and slowly turning round a still centre. With Veil the lamps take block-exact shadows from the same wall map as Level 0 (48 lamps within 39 blocks on HIGH), their bright side travels round the corridor with the spin, and wet floors, puddles, sweating pipes and bare metal get a small highlight of the nearby lamps (`client/light/Level2Gloss` says which blocks are damp, wet or metal). F3 shows the Level 2 lights' GPU time |
| Level 4 | Cold fluorescent blue-grey, quiet |
| Poolrooms | Teal shadows, soft cool highlights, a gentle wet glow; skylights and the tiles under the tubes roll off softly instead of clipping |
| Level 37 | Dreamlike: warm overexposed highlights, warm air and a soft glow, with natural contrast (tunnels fade into real darkness). The camcorder rolls the highlights off along a soft shoulder and the bloom is half as strong, so the lit niches and the shafts of sun land just under white with their grout still showing |
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
  before it settles; step into the dark and it stays too dark for a couple of seconds. In Level 37
  it (and the footage look's auto-exposure) meters the room light rather than the raw light level,
  since its rooms are evenly lit: turning toward a sunlit opening no longer flares the picture.
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
of the levels plays behind it (cinematic shots, level by level, each slowly drifting; soft analog
cuts where a tracking band rolls through the picture; fine grain, faint scanlines and now and then a
tape imperfection) with the REC overlay, the logo, a camcorder-style menu and a taped note, while
the main theme plays (see Music below). Each shot is framed so its focal point sits in the open
space between the menu and the note. Every shot is a real in-game capture (no edits, nothing
built for the camera): 21 shots across all 14 levels, one to three each, mostly with the mod's own
Veil lighting and a few under a shader pack where it shows the level better. The shots are
1920x1080 JPEGs in `textures/gui/menu/shots/`, listed in `shots.json` there; `tools/menu_shots.py`
builds both from the full-size screenshots (edit its `SHOTS` list and run it). **FIELD NOTES** is
a short in-game guide for new players.

### Music

Fifteen original pieces, composed in code (the first three in `tools/gen_assets.py`,
`snd_music_*`; the rest in `tools/anomaly_assets/`: Level 227's in `level227_audio.py`, the
per-level pieces in `music_*.py`; Level 228's with its other sounds in
`tools/level_assets/level228_atmos.py`) and played on the game's music channel, so the Music volume
slider controls them (`client/BackroomsMusic`). Every level has a piece in its own style; now and
then the main theme plays instead, so it runs through the whole soundtrack:

| Piece | Where | What it is |
|---|---|---|
| `liminal` | Title menu (looping); now and then in every level except Levels 94 and 227 | The main theme: slow, washed-out tape chords in D minor with an uneasy E-flat. Single tones fade in, it swells into a wall of sound, then falls apart into wind, a creak, a far boom and an out-of-tune music box |
| `level0` | Level 0 | "Fluorescent": office muzak heard through a wall at half speed: a soft electric piano on lounge chords that never resolve, a ghost of a bossa rhythm, sitting slightly flat against the 60 Hz mains hum |
| `level1` | Level 1 | "Concrete": cold, wide industrial ambient: a sub drone, far metallic pillar strikes, a breathing ventilation pulse, a swelling minor pad and drips |
| `level2` | Level 2 | "Pressure": tuned struck pipes in a slow interlocking pattern, steam-hiss swells, a boiler heartbeat and a dark brassy drone; hot and claustrophobic |
| `level4` | Level 4 | "Night Shift": sparse, lonely jazz piano in an empty office's faint hush, one muted-horn phrase, ending on an unresolved chord |
| `poolrooms` | The Poolrooms | "Chlorine": slowed, chorused dream pads and a glassy arpeggio in F Lydian that dip underwater and come back; a childhood indoor pool at dusk |
| `level37` | Level 37 | "Sublimity": airy synthesized choir and glass-harmonica shimmer, bright and almost bassless; near the end one voice drifts out of tune |
| `level369` | Level 369 | "Comely": a music-box waltz, a lullaby in F over a soft harp and a warm pad, played three times; each time more of its notes are gone, and at the end it starts once more and stops |
| `level228` | Level 228 | "Silvering": a glass harmonica sings a tune in E minor and, half a bar later, a glassy electric piano answers with its mirror image (the same tune upside down), bouncing in a ping-pong echo like two facing mirrors; then everything plays backward; in the last part the tune stops and its reflection goes on alone, and lies |
| `level7` | Level 7 | "Thalassophobia": huge, very low drones swelling like waves, far whale-like glides, sonar pings, and once a low brass cluster rising from the deep |
| `level39` | Level 39, every 10 to 25 minutes | "Megalophobia": a small-town waltz on a music box or piano from impossibly far away in a gigantic echo, interrupted by enormous metallic groans |
| `level490` | Level 490 | "Penance": a slow processional on a synthesized pipe organ, a low male-choir drone, a tolling bell, thunder, chain rattles and a bowed lament |
| `level9` | Level 9 | "Suburbs": warped 80s analog synth pads and a drifting lullaby, a faint drum machine and sodium-lamp buzz; at the end the tape slows down and dies |
| `deep` | Now and then in Level 39 | Drones, wind and far-off noises with long near-silences; a muffled memory of the theme drifts past |
| `drift` | Now and then in the Poolrooms | Softer and dreamier: warm chords that never settle, water drips, a slow music-box line |
| `megaflora` | Level 227 only, every 4 to 10 minutes | Wonder turning into dread: wide, warm chords in E Lydian and a celesta figure climbing like light through leaves; then the bass slides a tritone down under the same chords, the sun's choir sings a long E that bends up to F, the tree groans, and it ends on E and F together, fading into wind |

In the levels a piece plays only every 3 to 8 minutes, like Minecraft's own music; in between
you hear only the level. Level 7's sea is loud and Level 490 is meant to be near-silent, so there
it's every 5 to 12 minutes; in Level 228's quiet glass maze every 4 to 10. Level 94 has
no music because its siren poles play their own tune.
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
  somewhere. Now and then a tube pops in the distance or the halls shift, far off.
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
  every 7-18 seconds the pipes act up somewhere up or down the corridor, always in the pipe bundle
  right beside the lane: a water-hammer knock that runs along the pipes toward you or away, a heavy
  clank, a long creak of hot metal (far more of them while the corridor twists), water rushing
  through the bundle past you, a shudder running along a pipe run with its brackets buzzing, or far
  metal tapping: a few knocks of a tool on a pipe, a pause, the same knocks again a little closer
  along the same pipe. The bundle corkscrews round the lane, so a run of knocks or a rush that
  travels along it also turns round your head as it passes. The wall fans drone as you pass them.
  In a dead section (its circuit popped, or its lamps dead for good) the level's drone dips about
  7 dB. Leaking valves hiss and puff steam, and about once a minute a relief valve near you lifts
  with a clunk and a burst of steam. A drop from a leak plinks in a puddle, ticks on a grating or a
  pipe, taps on bare concrete, plops in black ooze. On top of it all the level turns round you
  (`client/anomaly/level2/Level2Orbit`): the structure's groan rolls through the corridor's
  cross-section at the spin's own pace (about a turn every 52 s), the brackets' rattle circles three
  times as fast, a deep pressure pans right round your head every 12-20 s, and every 8-20 s a long
  creak of steel rolls 180-360 degrees round you.
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
  rooms' radio -31, clock -34 and stillness -37. Level 2: the wall fans' drone -27, a leaking
  valve -30, the pipes' knocks, creaks, rushes and far tapping -24 to -25, a clank -24, a relief
  valve venting -23 (its hiss swells in over a fifth of a second), drips -28 (puddle), -29 (metal)
  and -31 (concrete). Its turning is heard at your ear at about -30 (the groan and the pressure),
  -34 (the rattle) and -27 (the rolling creaks).
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
  Its ponds (`client/water/WaterSplashes`, `UnderwaterAudio`, `tools/level227_water/gen_water.py`):
  splashes in three sizes (-27 / -23 / -20 LUFS), soft strokes -31, the underwater hush -33; with
  your head under, every other sound is muffled through the same filter as the walls.

## Operator commands

```
/backrooms enter [players] [level]      send players in: level_0, level_1, level_2, level_4, level_7, level_9, poolrooms, level_37, level_39, level_94, level_228, level_369, level_490 (the path's order), level_227
/backrooms time94 <day|dusk|night|dawn> jump Level 94's day/night clock (testing)
/backrooms thing [dread]                Level 7, in the water: the Thing passes by you now at full dread (dread: full dread only) (testing)
/backrooms maze <foyer|crystal|alhambra|funhouse|glass|chamber|exithall|backstage|shift|answer>   Level 228: jump to a zone's start, force a shift of the panels, or name the true chamber mirror (testing)
/backrooms imitator <show|off>         Level 228: the imitator starts behind you in the mirrors now (any game mode, no cooldown), or goes away until the next show (testing)
/backrooms escape [players]             send players back to reality
/backrooms battery [players] [percent]  set camcorder battery
/backrooms locate                       where is the nearest exit? (Level 227 has none, and says so)
/backrooms locate saferoom              where is the nearest safe room?
/backrooms wayback [sunlit|clay]        Level 39 / 94: where the side ways back are (Level 39: the nearest sunlit tunnel dead end, back to the Poolrooms, and clay front door, back to Level 9; Level 94: the nearest clay front door); with an argument, takes you there
/backrooms anomaly here                 which anomalous area am I standing in?
/backrooms anomaly locate <kind>        where is the nearest one of a kind (e.g. level_0/red_rooms; tab completes)? On Level 0 it also finds zones and set pieces: level_0/zone/... (e.g. level_0/zone/rooms, the room maze) and level_0/set/... (e.g. frame_tunnel, dead_end_cache, liar_arrows, bright_district, dim_district, abandoned_district). On Level 2 also segments (level_2/seg/..., e.g. valves, bulkhead, leak, dark), level_2/exit, brownouts and the oddities (level_2/wrong_valves, inverted_pipe, repeat_section, heart_gauge)
/backrooms anomaly visit <kind> <point>  Level 0 and Level 2 (ops, for testing): jump to a named spot of the nearest site of that kind (tab completes both). level_0/atrium centre|edge|corridor, level_0/warren centre|room|corridor, level_0/front_office rug|hidden_room, level_0/shaft_hall lit_shaft; the hidden rooms and the older anomalies have their own points. Level 2: level_2/wrong_valves front, heart_gauge front, inverted_pipe between|wall_a|wall_b, repeat_section first|second|last
/backrooms event <blackout|flicker|alarm|intercom|lightning|twist|fog|lampwave|water|rain|quake|end> [seconds]
/backrooms outage <cut|stutter|wave> <radius> [seconds]   Level 0: cut the power in a circle around you (no seconds = until "/backrooms outage end")
/backrooms l0event <room|corridor|district>   Level 0: let that local light event happen to you at the next right moment (walk into a room / walk a corridor / look down a long lit view), skipping the wait
/backrooms drift <player> [sealed|door|lights|end]   separate realities now: that player sees a sealed doorway, a door or dead lights (with the nearest friend, or alone); end = snap back
/backrooms hallucinate [players]        soak players in the Poolrooms: full hallucination + phantom friend
/backrooms sanity [players] <0-100>     set sanity (test the low-sanity effects)
/backrooms spawn bacteria [count]       spawn Bacteria ~12 blocks in front of you (they only hunt survival players)
/backrooms bacteria pose <idle|walk|chase|spotted|lunge|cage|feed|peek|creep|auto> [loop]   film the Bacteria's animations: forces that pose on the nearest living one within 32 blocks and freezes it there (walk and chase run on the spot; its head locks on the nearest player); loop = replay: chase = a swipe every 2 s, spotted = the turn then the run every 2 s, lunge = stop-stare, leap and cage every 2 s, cage = a whip every 2.5 s, feed every 4 s, peek = a normal peek then a long one, creep = the 15 s prowl round whatever stands 1.3 blocks in front of it (hands on it, re-gripping, a long freeze, a reach across, then it opens it and cranes in; face it to a wall or a locker); auto = back to normal. Works with NoAI too
/backrooms spawn smiler [count|bold]    spawn Smilers ~16 blocks in front of you (start a blackout first)
/backrooms spawn <watcher|observer> [players]   Level 9 test spawns
/backrooms house9 <plan>                Level 9: jump to the nearest house of a plan (classic, ranch, attic, basement, boarded, half_built, upside_down, repeated, safe, clay_door, long_hall)
/backrooms event <lampsbehind|dogs>    Level 9: start the lamps going out behind you (walk past a few) / the dog chorus that stops dead, now
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
/backrooms l227 pond [height]           Level 227: dig a test pond 7 blocks ahead (4 deep in the middle); with a height, drop into it from that high
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

### Escape tests and the test pilot

`gradlew test` includes escapability checks (`src/test/java/com/rickr/backrooms/escape`): for every
level they place an arrival the way `Travel` does and search the generated blocks on foot (walk,
half steps, ladders, water, and jumps only where the level allows them) for that level's exit.
Levels 369 and 490 belong to a friend: they are only reported, in
`build/minecraft-junit/build/reports/friend-levels.txt`, and never fail the build.
`SideExitTest` pins the line itself (every level but 227 once, in the wiki's order, ending in
reality) and the rule for side ways: every side way that skips ahead has a way straight back,
Level 227 only drops you where the line leads back, and the ways back (Level 39's sunlit dead ends
and clay doors, Level 94's clay doors) are really generated.
`Level228PlanTest` pins Level 228's mirror trick: around every twin mirror the hall is exactly
symmetric to fog depth and every sealed image is the exact reflection of what it shows; walking
from the foyer (every shift state) never reaches image space, cuts nothing off and reaches all
three chamber mirrors, as does the wrong mirrors' checkpoint; rays traced physically (bouncing off
the panes) and `Level228Plan.imagesOf` agree both ways. `Level228RulesTest` pins the group exit
and when a body counts as pushing a pane.

The `pilot` source set is a dev-only mod (`backrooms_pilot`, never in the release jar) that lets a
script drive a dev client with real key and mouse input over a local socket, for survival
playthroughs without commands. It does nothing unless the client is started with
`-PpilotPort=<port>` (or the `BACKROOMS_PILOT_PORT` environment variable); it listens on
127.0.0.1 only.

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
| `player` | Energy (stamina), movement limits, footsteps (incl. wading, `PoolWaterSounds`), checkpoints and respawning (`Checkpoints`; team-shared ones in `SharedCheckpoints`, game rule in `registry/ModGameRules`, death-screen buttons in `client/CheckpointChoiceButtons`), hiding in furniture (`HidingService`, which furniture and where the view sits: `HidingSpots`), friends hearing a panicking player's heartbeat (`SharedHeartbeat`) |
| `block`, `item` | Decals, lamps, supply containers (crate/backpack/locker share `SupplyContainerBlock`), doors, food, pills, bandages; big surfaces (wallpaper, carpets, ceiling tiles, concrete, pool tile, asphalt) come in a few stained/cracked versions picked per block by the world generator (`SurfaceBlock`; plain inside anomalous areas); Level 0's versions cluster in worn patches and some keep to one wall row (`Level0SurfaceBlock`, art in `tools/anomaly_assets/level0_surfaces.py`); Level 2's wet versions gather in damp stretches (`Level2SurfaceBlock` with `world/layout/Level2Damp`, art in `tools/anomaly_assets/level2_materials.py`); Level 227's flora (`Level227*Block`: tall plants, hanging strands, the Palette, GammaPali, Lestron; registered in `registry/Level227Blocks`, art in `tools/anomaly_assets/level227_flora.py`) |
| `network` | Packets between server and clients |
| `client/particle` | Steam and dust particles, drawn soft by their own shader (`SoftParticles`, `shaders/core/soft_particle`); dust spawning (`DustMotes`); Level 227's splash drops, surface rings and foam, and bubbles (`WaterDropletParticle`, `WaterSurfaceParticle`, `WaterBubbleParticle`) |
| `client/light` | Veil dynamic lights: fixture index, light manager, flicker/blackout, Level 0's faulty tubes (stutter, lazy restart, dying pulse: `FixturePower`; the tube ages and colours themselves are block states, `block/Level0TubeGlow`, picked by `world/layout/Level0Tubes`), Level 0's own light type with wall-map shadows and the power/corner screen pass (`Level0LightRenderer`, `Level0WallGrid`, `Level0PowerMap`, shaders in `pinwheel/shaders/program/light/level0_*`), its blackout wave and local outages (`Level0Power`), flashlights, hum sounds, Level 37's shafts of sun (`SunBeams`: one area light per opening), Level 4's window glow, troffer downlights and lobby/cafeteria feature lights (`Level4Lights`: shadowed area lights), Level 1's lamp reflections in the wet floor (`Level1WetFloor`: glowing strips on the floor, in any renderer) |
| `client/water` | Pool water (also Level 7's sea and Level 227's ponds): surface finder (`WaterSurfaces`), renderer + shaders (`WaterRenderer`, `shaders/core/pool_*`), per-level look (`WaterStyle`), impact rings on the surface (`WaterRipples`), underwater fog and its darkening with depth (`UnderwaterView`); Level 227's splashes (`WaterSplashes`) and underwater sound (`UnderwaterAudio`) |
| `client/audio` | Wall muffling and room reverb through OpenAL EFX (`Acoustics`, `AcousticsEfx`); distant one-off sounds per level (`DistantAmbience`) |
| `mixin/client` | Hides vanilla water faces in the pool levels; tells the water renderer about block changes; lets you sprint through the knee-deep shallows (`LocalPlayerMixin`); reaches the sound engine's channels for `client/audio` (`SoundManagerAccessor`, `SoundEngineAccessor`, `ChannelAccessor`); moves your view inside the furniture while you hide (`CameraMixin`) |
| `mixin` | Keeps gravity on while you run through shallow pool water (`LivingEntityMixin`); lets anomaly teleports update the server's movement check, and keeps players held in the air (Level 39's sky arrival, window grabs, the chairlift) from being kicked for "flying" (`ServerGamePacketListenerAccessor`) |
| `client/camera` | Handheld camera shake, roll, smoothing |
| `client/hud` | Minimal HUD, stamina line, the arrival card (`ArrivalCard`), and the shared camcorder OSD look (`OsdStyle`: font, colours, margins, fades) used by `CamcorderHud`, `SafeRoomOsd`, `HidingOsd` and `ArrivalCard` |
| `client/trailer` | Dev-only trailer camera and frame recorder (`TrailerRecorder`, off unless started with `-Dbackrooms.trailerDir`) |
| `client` | Camcorder post-shader control, per-level picture mood (`AtmosphereGrade`), overlay, Bacteria model/renderer, keys; Level 37's curved tile drawn into the chunk meshes with smooth per-vertex lighting (`CurveTileRenderer`) |
| `assets/backrooms/shaders` | Lens, bloom, motion blur, contact shadows, distance haze, glow and grade post-processing |
| `tools/gen_assets.py`, `tools/anomaly_assets` | Every texture and sound, generated in code; the anomalous areas' assets live in one module per level, loaded by `gen_assets.py` |
