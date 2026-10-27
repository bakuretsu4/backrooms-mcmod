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
| Camcorder REC overlay | `H` (off by default) |
| Sprint | Limited by energy: a thin faint line above your hearts shows it while you run (turns red when you're out of breath). Rest to refill it. |
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

```
Overworld --noclip--> Level 0 --stairwell--> Level 1 --stairwell--> Level 2 --EXIT door--> Level 4 --stairwell--> Poolrooms --drain--> Level 39 --mill window--> Level 9 --underpass--> Overworld
```

Secret branches: a rare sunlit archway in the Poolrooms leads to **Level 37**, whose deep pits lead
on to Level 9; a rare moulded-clay front door on a Level 9 house opens onto **Level 94**, and a
flooded house in Level 94 leads down to **Level 7**, whose sunken door leads on to Level 9; a very
rare chairlift in Level 9's Crimson Field climbs into the clouds and drops you into **Level 39**.

The stairwells (Levels 0, 1 and 4) are part of their level: the level's own walls inside, then a
wide spiral of shallow half-steps around a concrete core, lit for the first half turn and then
going down into the dark. Somewhere down there you are simply in the next level, the picture
fading back in: no glitch, no cut.

- **Level 0 (the Lobby):** Kane Pixels' bright yellow halls of square pillars under a grid of
  troffers, mazes of thick wall blocks, long corridors, the film's big half-fitted rooms (rows of
  half-walls and cubicles, window holes, a ramp into a low passage, mannequins, a heap of old
  furniture) and whole wings where every light is dead. Here and there furniture has noclipped
  into the walls, floor and ceiling. Arrows drawn on the walls point to the exit stairwell, but
  some of them lie. Look for the EXIT sign over a wooden door between green and white hazard stripes.
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
  with you. Parallel corridors 48 blocks apart never meet.
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
  pool, lit by lamps around its edge. Swim into it and it pulls you down into Level 39's flooded
  tunnels.
  Somewhere (about once every 300 x 300 blocks) a dim, dry room has a **sunlit archway** in its
  north wall: a round arch lined with fine white mosaic, warm daylight pouring out of it. Walk
  into the light.
- **Level 37 ("Sublimity"):** reached only through the Poolrooms' sunlit archway (the screen
  whitens and fades in, no tape glitch). Pristine cream-white mosaic on every surface, floors
  flooded ankle-deep in clear sea-green water, deep pools with no ledge and chrome ladders,
  forests of round pillars standing in the water, wavy walls and rounded corners, round arches
  and circular tunnels fading into darkness, domed rotundas with an oculus and glowing arched
  alcoves, a spiral stair of floating treads climbing a light well toward the sun. Warm daylight
  comes from nowhere: skylights and tall slit windows open onto a dazzling white void and throw
  sharp bright rectangles and stripes across the tile and water, while the long tunnels stay dark.
  Calm and quiet: gentle lapping, distant drips, no monsters, no events, and your **sanity slowly
  comes back** (+0.6/s) anywhere on the level. Backpacks on the dry pool decks and at the top of
  the spiral stairs hold almond water and calm pills. The way on is down: a round rotunda with a
  **pit of deep, dark water** in its middle (one every ~170 blocks; `/backrooms locate` points to
  it). Walk down the floating treads into the water and dive; a gentle current draws you down
  the shaft and out into Level 9.
- **Level 9 (the Suburbs):** an endless suburb at midnight in thick fog that drains the colour
  from everything. Wet roads covered in leaves, old sodium streetlamps (most dead, some
  flickering), houses with blacked-out windows, some unlocked with dark rooms inside, and dark
  forest behind the yards. Follow the green EXIT arrow signs (a few lie) to a highway underpass
  and walk into the dark.
- **Level 39 ("Megalophobia"):** you come up out of the Poolrooms' drain into long, white
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
  glass, and you noclip through it into Level 9.
- **Level 94 ("Motion"):** reached only through a rare clay front door on a Level 9 house (about
  one unlocked house in forty). Endless rolling hills of felt grass under a painted sky with fat
  cartoon clouds, winding roads, little cream houses with red roofs (some enterable, with a CRT TV
  showing static), house fragments on the hills, water towers, parked old cars and milk vans, siren
  poles that play a happy tune by day, and a town with a chess-king fountain. Far away a white
  cartoon castle stands on a see-through glass mountain; walk the spiral ramp up to it. Inside it
  turns into a real (and wrong) play centre: checker floors, a ball pit, a playground with a
  climbing frame, a looping upstairs hall, an animation studio and an empty throne room. Day lasts
  8 minutes (the last 30 s is dusk), night 3: at night the music stops and Smilers come out on the
  hills, so get inside a house (they can't follow you in; your sanity holds steady there). Ways
  out: houses whose doorway is pure black lead back to Level 9, flooded houses (knee-deep water,
  steps going down into murk) lead to Level 7, and the light behind the throne leads to Level 1.
- **Level 7 ("Thalassophobia"):** reached only down the stairs of a flooded house in Level 94
  (about one exit house in three). You come up in a small flooded sitting room (bookcase, coffee
  table, armchair, a radio and a crate on the dry landing) hanging from a concrete ceiling over an
  endless dark sea in a thick pale fog. Open a hatch in the landing and drop; the rope by the
  other hatch climbs back up (hold jump). Around the room is a shallow shelf of pale silt, then a
  sheer drop to a carpeted sea floor about 150 blocks down, with rock stacks, tar, scrap and the
  ribs of giant fish. You can hold your breath about ten times longer than normal here. The way
  out is a wooden door lying in a round stone platform on the sea floor, ringed by pillars and
  huge pipes, 150 blocks west of the room: swim into it and you come out in Level 9. No monsters,
  but something enormous lives in the water: you hear it groan far below, feel the water heave
  when it passes under you, and once in a while a vast dark shape glides past at the edge of what
  you can see. Your sanity drains in the water, faster the deeper you go; the room calms you. The
  sea is drawn like the pool water (below), in its own murky grey-green: a rippled surface that
  mirrors the concrete above and lets the pale shallows show through, and underneath a dim teal
  murk that turns black and closes in as you go down. Far off, waves slap against rock, the
  ceiling drips and the giant pipes thump.

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
  infrared shows. With NightShot on, a few dark arrows appear on walls and floors around you
  (about one per dozen blocks of wall), each pointing toward the exit, or down when it lies far
  below. Unlike the arrows drawn on Level 0's walls, they never lie.
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

| Item | What it does |
|---|---|
| Canned Food / Backshroom / Bread | Food. 1 can in 20 has gone bad; backshrooms can make you sick. |
| Almond Water | Heals a little and clears your head (Poolrooms hallucinations). |
| Energy Bar | 3 hunger, eaten fast, and refills your sprint energy at once (even when out of breath). |
| Calm Pills | Hold right-click for 1 s: restores 25 sanity. |
| Bandage | Hold right-click for 1.5 s: heals 2 hearts plus Regeneration I for 5 s. |
| Camcorder Battery | Right-click to swap in a fresh battery. |

### Sanity

Your heart rate (top-right, always shown in the Backrooms) is your sanity: 70 BPM when calm, up to
160 BPM when you're losing it. It starts at 100, is saved when you log out, and you respawn at 70.

- **Drains** slowly all the time in the Backrooms, faster in the dark (with your flashlight off),
  when nobody is within 24 blocks, when you see a Bacteria (much faster if it's chasing you), a
  Watcher, an Observer or a Smiler, and during blackouts.
- **Restores** in a safe room (fast), next to a friend in the light (slowly), out in the real world,
  and with Almond Water (+35) or Calm Pills (+25). Creative/spectator players aren't affected.
- **When it's low** (only you notice): below 70 you hear whispers, knocks and footsteps right behind
  you; below 50 the camcorder picture darkens at the edges in time with your heart, loses colour
  and a black figure stands at the edge of your view until you look at it; below 30 you hear your
  heartbeat and the readout blinks red, and the Bacteria find you more often; below 15 the figure
  sometimes breathes in and charges at you down the corridor. None of it is real.

### Safe rooms & checkpoints

Every level has small safe rooms, marked by a green **SAFE** plaque over the doorway. Inside there
is a warm lamp, a crate, a cot and an old field radio on a little table.
About six in ten 128x128 areas have one: a panelled office room hidden in the Lobby maze, a
concrete room with a metal door frame in the Habitable Zone, a carpeted side room off the Level 2
corridor, a dry tiled room on a walkway in the Poolrooms, and on Level 9 a house with its porch
light on and the sign over the front door.

- **Safe:** no monster spawns in a safe room or within 12 blocks of one, the Bacteria never walks
  in (reaching one mid-chase counts as escaping), and Watchers and Observers leave you alone while
  you're inside. The camcorder shows **SAFE ROOM** at the top left while you're in one.
- **The lamp is battery-backed:** like the exit signs it stays on in blackouts and never flickers
  (with the Veil lights; without Veil a blackout darkens everything).
- **Checkpoints:** right-click the radio to save your checkpoint (static, a chime and
  **CHECKPOINT SAVED** on the camcorder). If you die on that level you wake up on the floor in front
  of the radio. Dying anywhere else still sends you back to the real world, and leaving the level
  through its exit forgets the checkpoint. Clicking the radio again just saves again.

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
  creepier, **lightning** in the garage, the **twist** in the pipes (the corridor tilts), distant
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
- **Echo quakes (Level 39):** booms from the superstructure echo four times off the walls and can
  build into a quake: the ground shakes, dust and coal ash fall, windows rattle. Slamming doors,
  bells, hard landings and breaking walls add up, and enough noise sets one off.
- **Indoor rain (Level 39):** the superstructure's own clouds rain on the town for a few minutes
  every quarter of an hour or so.
- **Battery:** lasts 40 minutes; flashlight and NightShot drain it faster. It recharges in the
  real world.
- Dying in the Backrooms: *you can't escape.*

### Anomalous areas

Every level has its own strange places, taken from that level's own lore on the Backrooms wiki
(nothing borrowed from another level). They are built into the map where they belong, rarer than
the ordinary rooms, and most of them only change when nobody is looking. Their positions come from
the world seed (`world/anomaly`: `AnomalyGrid` plans them, each level's layout builds them,
`AnomalyService` runs their behaviour on the server and tells clients which ones are near).

**Level 0**
- **Red Rooms:** Sometimes the wallpaper starts peeling, there's crimson underneath, and the carpet grabs at your shoes. Turn the corner and you're in darkroom-red rooms that hum like a klaxon, and the way you came in is gone. Count the loops; Level 0 has its own way of letting people go.
- **Blackout Zones:** Patches of the maze where every light is dead, the walls are rough and the carpet is flooded. The buzz you hear seems to lead the way out, but it never stays in one place.
- **The Manila Room:** A very rare brown wall with a wooden door. Behind it is a quiet beige room with a table, two chairs, supplies and notes from volunteers, and a piano playing from nowhere. Read the notes: the way to Level 1 is close.
- **Shifting pillar halls:** Big bright halls full of pillars that are never quite where they were when you look back. Only the pale arches in the middle stay put.
- **Pit rooms:** A big room whose floor is a grid of carpeted square shafts with narrow walkways between them, and a wooden door on the far side. Don't fall in. You will land, hurt, somewhere very far away.

**Level 1**
- **Crate halls:** In some of the open garage halls, aisles of steel pallet racking stand in a brighter, better-lit hall. Wooden crates turn up there: you hear something settle behind you, and there is a crate that wasn't there before. When the lights fail, the crates are gone; when the power comes back, new ones are waiting. Not everything inside is worth taking.
- **Painting rooms:** Standing alone in a hall, a small closed room is covered in crude paintings and drawings. Look away, or wait for the lights to go out, and they are not the pictures you remember, if they are there at all.
- **Fog patches:** Here and there a low, sourceless fog lies along the floor, the air turns grey and damp, and puddles of condensation gather and dry up again in places you weren't looking. Listen for the drips.
- **The Corrupted Corridor (Level 1.1):** Very rarely, a doorway in a garage wall opens onto a long white-cinderblock corridor with a blue tiled floor. The further you go the worse it gets: the colours go wrong, the same stretch of hallway seems to repeat, the dark swallows your light, and the walls turn black and white and won't hold still. At the very end, a light beckons.

**Level 2**
- **Series circuits:** The caged lamps are wired in series along the corridor. Now and then a circuit gives out with a sharp pop, and a whole section of the line goes dark at once (a gap opens in the row of lights ahead), sometimes for nearly a minute, before the lamps stutter back to life. Sometimes a fault runs down the line: three sections pop out one after another, toward you or away.
- **Brownouts:** Some sections never recovered from the Blackout. Almost every bulb is burst, the machines are silent, and only a few violet emergency lights still glow. Burst pipes have left pools of thick black sludge that clings to your boots.
- **Hot zones:** Behind a wide opening off the corridor, some boiler rooms run hot: faulty machines whir and steam hisses from the walls on the way in. The heat slows you down, wears you out and makes you hungry fast, and if you stay too long it starts to hurt.
- **Void doors:** At the back of a small hall off the corridor stands a door with a pitch-black window. It opens onto an endless, silent, lightless space where the doorway behind you is the only light. Don't lose sight of it.
- **Looping hallway:** A few office doors, each at the back of a small hall off the corridor, open onto a long, plain, fluorescent-lit hallway. However far you walk, you keep passing the same door you came in by.

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
jitter and colour fringes), soft bloom on the lights and a little motion blur when you turn. On top
of that each level has its own mood (`client/AtmosphereGrade`), applied evenly over the whole
picture and blended over about two seconds when you change level:

| Level | Mood |
|---|---|
| Level 0 | Kane Pixels' tapes: bright, flat, even light, yellow-green with the blue cut, troffers clipping to white, no haze; dark wings crushed to black |
| Level 1 | Cold, damp concrete: blue shadows, little colour, grey-blue air |
| Level 2 | Warm amber pools from the caged lamps receding down the line, rust-brown shadows, pipe chases sunk in darkness, a light warm heat haze |
| Level 4 | Cold fluorescent blue-grey, quiet |
| Poolrooms | Teal shadows, soft cool highlights, a gentle wet glow |
| Level 37 | Dreamlike: low contrast, milky shadows, warm overexposed highlights, the strongest glow |
| Level 9 | Deep blue shadows, sodium-orange highlights, cold dark air |
| Level 94 | A touch over-saturated and soft by day, pale blue distance; colour drains away at night |
| Level 7 | Colour almost gone: pale grey bone-dust fog above the water, murky dark teal under it, near-black in the deep |

Each mood combines a colour grade (split-toned shadows and highlights, lifted blacks), coloured
haze that thickens with real distance, soft contact shadows in corners and where walls meet floors,
a faint glow around bright areas, and a very slow exposure drift, as if the camera's auto-iris
can't quite settle. All of it is kept subtle so you can always see where you're going.

The camera also reacts like a real one:

- **Eyes adjusting** (`client/EyeAdaptation`): the auto-exposure follows how bright it is where you
  look, slowly. Step from the dark into a lit hall and the picture is blown out for a moment
  before it settles; step into the dark and it stays too dark for a couple of seconds.
- **Dust in the light** (`client/particle/DustMotes`): fine specks drift in the air near the lamps
  and in your flashlight beam (only where there is light to see them by).
- **Nothing but the footage:** no hand or held item, no crosshair or block outline, no XP bar, no
  tutorial hints or advancement pop-ups. Hearts, hunger and air appear only for a few seconds when
  they change (and stay faintly in view while your health is low); the hotbar slides away when
  you're not switching items (`client/hud/BackroomsHudEvents`).
- **Your breathing** (`client/Breathing`): heavy breathing after a sprint that eases off as you get
  your breath back, and quick, shaky breaths when something is near.

### Main menu

The vanilla title screen is replaced by a "found footage" menu (`client/menu`): camcorder footage
of the levels plays behind it (a slow pan over screenshots, VHS glitch cuts, flickering lights,
grain and scanlines) with the REC overlay, the logo, a camcorder-style menu and a taped note, while
the main theme plays (see Music below). **FIELD NOTES** is a short in-game guide for new players.

### Music

Three original pieces, composed in code in `tools/gen_assets.py` (`snd_music_*`) and played on the
game's music channel, so the Music volume slider controls them (`client/BackroomsMusic`):

| Piece | Where | What it is |
|---|---|---|
| `liminal` | Title menu (looping); now and then in every level except Level 94 | The main theme: slow, washed-out tape chords in D minor with an uneasy E-flat. Single tones fade in, it swells into a wall of sound, then falls apart into wind, a creak, a far boom and an out-of-tune music box |
| `deep` | Now and then in Levels 0, 1, 2, 4 and 9 | Drones, wind and far-off noises with long near-silences; a muffled memory of the theme drifts past |
| `drift` | Now and then in the Poolrooms and Level 37 | Softer and dreamier: warm chords that never settle, water drips, a slow music-box line |

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
  at all under open sky. Each level adds its materials: Level 0's carpet and Level 4's offices soak
  up the highs, Level 1's bare concrete garage rings, Level 2's long pipe corridor is hard and
  metallic, with a late echo coming back from far down the line. The
  Poolrooms and Level 37 get no reverb, since in canon nothing reverberates there. Music, the
  camcorder's beeps and the overworld are left alone. Without EFX support it simply switches off.
- **Distant sounds** (`client/audio/DistantAmbience`): every 20 seconds to a minute something
  happens somewhere out of sight, picked from five per level, each with three takes: a ballast
  popping or scratching behind the walls in Level 0, pipes clanging in Level 1 and 2, a phone or
  copier in Level 4, water and drains in the pool levels, wind chimes and a dog that stops barking
  in Level 9, a car horn or rats in Level 94 depending on the time of day.
- **Level 2's pipes** (`client/anomaly/level2/Level2Emitters`, `tools/anomaly_assets/level2_audio.py`):
  every 7-18 seconds the pipes act up somewhere up or down the corridor: a water-hammer knock
  that runs along the pipes toward you or away, a heavy clank, a long creak of hot metal (far more
  of them while the corridor twists), or water rushing through the overhead bundle and over your
  head. Pumps in pump bays hum and thump, leaking valves hiss and puff steam, and drips plink
  into the gutter.
- **Real recordings** (`tools/level_assets/recorded.py`, all CC0, see CREDITS.md): the fluorescent
  hum of every tube in Levels 0-2 is a real ballast, the flicker clicks are real tubes and starters
  catching and dropping out, a blackout is a big breaker clunking and the floor's hum sagging and
  dying in the concrete, and when the power comes back the starters tick before the hum swells
  in. Level 0's room tone and Level 1's garage air (a ventilation fan rattling far off in an empty
  car park) are field recordings too, and so is your breathing. The Bacteria's sounds are the same
  breathing recordings slowed down, some played backwards, with soft wet clicks
  (`tools/level_assets/bacteria.py`).
- **Mix:** quiet on purpose. Every level's background loop is heard at the same loudness (about
  -30 LUFS, set by its `volume` in `sounds.json`); hums, buzzes and drones stay around -27 and
  nothing else (creatures, alarms, the camcorder's beeps) goes above about -18, so a scare stands
  out without blasting. Backpacks open and close silently. Many lamps humming
  together are turned down so their drone never buries everything else, and the music sits a
  little above the level instead of over it.
- **Level 39 "Megalophobia"** (`client/Level39Audio`, `client/Level39QuakeEffects`): the town
  rings like one colossal hall (the superstructure encloses it, so its echo is long and loud even
  in the open), while the marble tunnels below swallow sound. Its own loops follow you between
  town and tunnels; far off you hear the superstructure groan, metal doors slam with long echoes
  and telephones ring in empty houses. Radios only garble here. Loud sounds echo and can build into a quake
  (camera shake, rumble, dust, rattling windows); the superstructure's own climate rains on the
  town now and then.

## Operator commands

```
/backrooms enter [players] [level]      send players in: level_0, level_1, level_2, level_4, poolrooms, level_37, level_9, level_94, level_7, level_39
/backrooms time94 <day|dusk|night|dawn> jump Level 94's day/night clock (testing)
/backrooms escape [players]             send players back to reality
/backrooms battery [players] [percent]  set camcorder battery
/backrooms locate                       where is the nearest exit?
/backrooms locate saferoom              where is the nearest safe room?
/backrooms anomaly here                 which anomalous area am I standing in?
/backrooms anomaly locate <kind>        where is the nearest one of a kind (e.g. level_0/red_rooms; tab completes)? On Level 0 it also finds zones and set pieces: level_0/zone/... and level_0/set/...
/backrooms event <blackout|flicker|alarm|intercom|lightning|twist|fog|lampwave|storm|water|rain|quake|end> [seconds]
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
(`dist/Backrooms-Found-Footage-Sneak-Peek.mp4`) was shot this way and cut to the `liminal` theme.

## Code map

| Package | What lives there |
|---|---|
| `world` | Level definitions, the infinite chunk generator, exits, travel, floor events, safe rooms (`SafeRooms`) |
| `world/layout` | One floor plan per level (pure functions of seed + x/z); Level 37's plan is integer codes (`Level37Plan`) turned into blocks by `Level37Layout`; Level 39 is split into the town cell plan (`Level39Cell`, cached 128-block tiles in `Level39Tile`), houses (`Level39Houses*`, one cached voxel plan per house), the mill (`Level39Superstructure`) and the tunnels (`Level39Tunnels`, `Level39Vault`); each level's anomalous areas are planned in `<Level>AnomalyPlan` |
| `world/anomaly` | Anomalous areas: site planner (`AnomalyGrid`), behaviour registry (`Anomalies`), server driver and client sync (`AnomalyService`), seamless teleports (`Teleports`), "is anyone looking?" (`Observation`); one sub-package per level |
| `client/anomaly` | Client side of the anomalous areas: which sites are near (`AnomalyClientState`), per-level picture moods, sounds and overlays; one sub-package per level |
| `entity` | The Bacteria and its spawner; Level 39's mobs (`Hound`, `Faceling`, `Clump`, `SkinStealer`, `Wretch`) and their spawning (`Level39Spawner`, `TunnelDwellers`) |
| `camera` | Camcorder battery/flashlight/NightShot server rules, flashlight sync |
| `player` | Energy (stamina), movement limits, footsteps (incl. wading, `PoolWaterSounds`), checkpoints and respawning (`Checkpoints`) |
| `block`, `item` | Decals, lamps, supply containers (crate/backpack/locker share `SupplyContainerBlock`), doors, food, pills, bandages; big surfaces (wallpaper, carpets, ceiling tiles, concrete, pool tile, asphalt) come in a few stained/cracked versions picked per block by the world generator (`SurfaceBlock`; plain inside anomalous areas) |
| `network` | Packets between server and clients |
| `client/particle` | Steam and dust particles, drawn soft by their own shader (`SoftParticles`, `shaders/core/soft_particle`); dust spawning (`DustMotes`) |
| `client/light` | Veil dynamic lights: fixture index, light manager, flicker/blackout, flashlights, hum sounds, Level 37's shafts of sun (`SunBeams`: one area light per opening) |
| `client/water` | Pool water (also Level 7's sea): surface finder (`WaterSurfaces`), renderer + shaders (`WaterRenderer`, `shaders/core/pool_*`), per-level look (`WaterStyle`), underwater fog and its darkening with depth (`UnderwaterView`) |
| `client/audio` | Wall muffling and room reverb through OpenAL EFX (`Acoustics`, `AcousticsEfx`); distant one-off sounds per level (`DistantAmbience`) |
| `mixin/client` | Hides vanilla water faces in the pool levels; tells the water renderer about block changes; lets you sprint through the knee-deep shallows (`LocalPlayerMixin`); reaches the sound engine's channels for `client/audio` (`SoundManagerAccessor`, `SoundEngineAccessor`, `ChannelAccessor`) |
| `mixin` | Keeps gravity on while you run through shallow pool water (`LivingEntityMixin`); lets anomaly teleports update the server's movement check, and keeps players held in the air (Level 39's sky arrival, window grabs, the chairlift) from being kicked for "flying" (`ServerGamePacketListenerAccessor`) |
| `client/camera` | Handheld camera shake, roll, smoothing |
| `client/hud` | Minimal HUD, stamina line |
| `client/trailer` | Dev-only trailer camera and frame recorder (`TrailerRecorder`, off unless started with `-Dbackrooms.trailerDir`) |
| `client` | Camcorder post-shader control, per-level picture mood (`AtmosphereGrade`), overlay, Bacteria model/renderer, keys |
| `assets/backrooms/shaders` | Lens, bloom, motion blur, contact shadows, distance haze, glow and grade post-processing |
| `tools/gen_assets.py`, `tools/anomaly_assets` | Every texture and sound, generated in code; the anomalous areas' assets live in one module per level, loaded by `gen_assets.py` |
