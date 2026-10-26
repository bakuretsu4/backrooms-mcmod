# The Backrooms: Found Footage

A NeoForge 1.21.1 mod: noclip out of reality into an endless Backrooms lit by real dynamic
lights, seen through a found-footage camera. Built for multiplayer: play it with friends on a
server or LAN.

## Playing

**Everyone (server and every player) needs the same mod jar** in their `mods` folder, with
NeoForge 1.21.1 installed. The jar already contains the Veil rendering library.

Not compatible with Embeddium/Rubidium. Sodium must be 0.8.12 or newer. With Iris installed the
mod falls back to simpler lighting.

| Action | How |
|---|---|
| Get in | Suffocate inside a block in the real world (let sand/gravel fall on you). Your camcorder starts recording, the footage breaks up, and you fall through the dark into Level 0. |
| Flashlight | `R`. Everyone sees your beam. Uses the camcorder battery. |
| NightShot (infrared) | `N` (drains the battery 3x faster) |
| Camcorder REC overlay | `H` (off by default) |
| Sprint | Limited by energy (the faint bolt in the middle of the screen). Rest to refill it. |
| Jump | Not possible in the Backrooms (except in water, to swim and climb out) |
| Loot | Right-click Wooden Crates, Backpacks and Supply Lockers (see Supplies below) |
| New battery | Right-click a Camcorder Battery |

### The way out

```
Overworld --suffocate--> Level 0 --stairwell--> Level 1 --stairwell--> Level 2 --EXIT door--> Level 4 --stairwell--> Poolrooms --drain--> Level 9 --underpass--> Overworld
```

Secret branches: a rare sunlit archway in the Poolrooms leads to **Level 37**, whose deep pits lead
on to Level 9; a rare moulded-clay front door on a Level 9 house opens onto **Level 94**.

- **Level 0 (the Lobby):** thick-walled yellow maze, lit rooms and dark corridors. Arrows drawn on
  the walls point to the exit stairwell, but some of them lie. Look for the EXIT sign over a
  wooden door.
- **Level 1 (the garage):** a grey concrete maze of closed-off rooms, narrow corridors and dead
  ends, opening now and then into wide parking halls with numbered pillars, tube lights,
  chain-link fences and puddles. Another white stairwell leads down.
- **Level 2 (the pipes):** long, narrow pale maintenance corridors with racks of rusty pipes and
  big ducts along one wall, caged lamps far apart and long pitch-dark stretches, junction rooms
  and small dark utility rooms. Find the emergency EXIT door; opening it takes everyone within
  6 blocks with you.
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
  pool, lit by lamps around its edge. Swim into it and it pulls you down.
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
  out: houses whose doorway is pure black lead back to Level 9, and the light behind the throne
  leads to Level 1.

Every floor is endless; exits are spread out a few hundred blocks apart.

**Pool water (Poolrooms and Level 37)** is drawn by the mod itself instead of vanilla's flat blue
water: it takes the colour of the lit room behind it, tinted from clear shallow mint/cyan to deep
green/teal the more water you look through, bends the tile floor with slow ripples, throws moving
caustic light patterns onto the floor and lower walls, mirrors the walls and pillars (Fresnel:
clear looking down, mirror-like at grazing angles) and catches sun glints in Level 37. Under water
the view stays clear and tinted, with caustics and soft light shafts. Level 37's ankle-deep
flooding is the `shallow_water` block: you wade through it (wading steps, a little drag) instead
of swimming. With an Iris shader pack active, vanilla water is kept (the pack draws its own).
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
  sometimes screams and charges at you down the corridor. None of it is real.

### Safe rooms & checkpoints

Every level has small safe rooms, marked by a green **SAFE** plaque over the doorway. Inside there
is a warm lamp, a crate, a cot and an old field radio on a little table.
About six in ten 128x128 areas have one: a panelled office room hidden in the Lobby maze, a
concrete room with a metal door frame in the Habitable Zone, a carpeted utility room off a Level 2
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

- **Bacteria:** tall black wire figure. It only knows where you are if it sees you or hears you
  sprint. In a lit room it spots you from about 24 blocks; in the dark only from about 8 (crouching
  makes both shorter, your flashlight doesn't matter). When it spots you it freezes for a moment and
  screams, then chases. Break line of sight: it runs to where it *last saw* you, searches for a few
  seconds (you hear its wires ticking), and when it gives up you hear a long falling creak. With
  NightShot on you can see it even in total darkness.
- **Blackouts:** the lights fade out over two seconds. Only flashlights work, and the Bacteria come
  out three times as often, but they can't use the lamps to spot you either.
- **Smilers (blackouts in Levels 0-2, and Level 94 at night):** two glowing eyes and a huge glowing grin in the dark,
  wrapped in a faint shroud of churning dark smoke you mostly notice against a lit wall or in NightShot.
  One comes when the lights go out, sometimes a second from the other side 10 seconds later. It
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
- **Battery:** lasts 40 minutes; flashlight and NightShot drain it faster. It recharges in the
  real world.
- Dying in the Backrooms: *you can't escape.*

### The camcorder picture

Everything you see in the Backrooms is filmed: a gentle wide lens, worn VHS tape (faint line
jitter and colour fringes), soft bloom on the lights and a little motion blur when you turn. On top
of that each level has its own mood (`client/AtmosphereGrade`), applied evenly over the whole
picture and blended over about two seconds when you change level:

| Level | Mood |
|---|---|
| Level 0 | Stale yellow light, sickly greenish shadows, thick yellow-grey air down the halls |
| Level 1 | Cold, damp concrete: blue shadows, little colour, grey-blue air |
| Level 2 | Rusty-brown shadows under cold bulbs, deep corner shadows, dark oily air |
| Level 4 | Cold fluorescent blue-grey, quiet |
| Poolrooms | Teal shadows, soft cool highlights, a gentle wet glow |
| Level 37 | Dreamlike: low contrast, milky shadows, warm overexposed highlights, the strongest glow |
| Level 9 | Deep blue shadows, sodium-orange highlights, cold dark air |
| Level 94 | A touch over-saturated and soft by day, pale blue distance; colour drains away at night |

Each mood combines a colour grade (split-toned shadows and highlights, lifted blacks), coloured
haze that thickens with real distance, soft contact shadows in corners and where walls meet floors,
a faint glow around bright areas, and a very slow exposure drift, as if the camera's auto-iris
can't quite settle. All of it is kept subtle so you can always see where you're going.

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
you hear only the level. Level 94 has no music because its siren poles play their own tune.
Everything goes through a worn-tape sound: a slow pitch wobble shared by every note, soft
saturation, hiss and a big, empty room.

## Operator commands

```
/backrooms enter [players] [level]      send players in: level_0, level_1, level_2, level_4, poolrooms, level_37, level_9, level_94
/backrooms time94 <day|dusk|night|dawn> jump Level 94's day/night clock (testing)
/backrooms escape [players]             send players back to reality
/backrooms battery [players] [percent]  set camcorder battery
/backrooms locate                       where is the nearest exit?
/backrooms locate saferoom              where is the nearest safe room?
/backrooms event <blackout|flicker|alarm|intercom|lightning|twist|fog|lampwave|end> [seconds]
/backrooms hallucinate [players]        soak players in the Poolrooms: full hallucination + phantom friend
/backrooms sanity [players] <0-100>     set sanity (test the low-sanity effects)
/backrooms spawn bacteria [count]       spawn Bacteria ~12 blocks in front of you (they only hunt survival players)
/backrooms spawn smiler [count|bold]    spawn Smilers ~16 blocks in front of you (start a blackout first)
/backrooms spawn <watcher|observer> [players]   Level 9 test spawns
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

## Code map

| Package | What lives there |
|---|---|
| `world` | Level definitions, the infinite chunk generator, exits, travel, floor events, safe rooms (`SafeRooms`) |
| `world/layout` | One floor plan per level (pure functions of seed + x/z); Level 37's plan is integer codes (`Level37Plan`) turned into blocks by `Level37Layout` |
| `entity` | The Bacteria and its spawner |
| `camera` | Camcorder battery/flashlight/NightShot server rules, flashlight sync |
| `player` | Energy (stamina), movement limits, footsteps (incl. wading, `PoolWaterSounds`), checkpoints and respawning (`Checkpoints`) |
| `block`, `item` | Decals, lamps, supply containers (crate/backpack/locker share `SupplyContainerBlock`), doors, food, pills, bandages |
| `network` | Packets between server and clients |
| `client/light` | Veil dynamic lights: fixture index, light manager, flicker/blackout, flashlights, hum sounds, Level 37's shafts of sun (`SunBeams`: one area light per opening) |
| `client/water` | Pool water: surface finder (`WaterSurfaces`), renderer + shaders (`WaterRenderer`, `shaders/core/pool_*`), per-level look (`WaterStyle`), underwater fog (`UnderwaterView`) |
| `mixin/client` | Hides vanilla water faces in the pool levels; tells the water renderer about block changes |
| `client/camera` | Handheld camera shake, roll, smoothing |
| `client/hud` | Minimal HUD, energy bolt |
| `client` | Camcorder post-shader control, per-level picture mood (`AtmosphereGrade`), overlay, Bacteria model/renderer, keys |
| `assets/backrooms/shaders` | Lens, bloom, motion blur, contact shadows, distance haze, glow and grade post-processing |
