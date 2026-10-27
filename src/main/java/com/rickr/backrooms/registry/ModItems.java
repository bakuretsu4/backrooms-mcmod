package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.item.AlmondWaterItem;
import com.rickr.backrooms.item.BandageItem;
import com.rickr.backrooms.item.CalmPillsItem;
import com.rickr.backrooms.item.CamcorderBatteryItem;
import com.rickr.backrooms.item.EnergyBarItem;
import com.rickr.backrooms.item.ModFoods;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Backrooms.MOD_ID);

    public static final DeferredItem<CamcorderBatteryItem> CAMCORDER_BATTERY = ITEMS.registerItem("camcorder_battery",
            CamcorderBatteryItem::new, new Item.Properties().stacksTo(4));
    public static final DeferredItem<AlmondWaterItem> ALMOND_WATER = ITEMS.registerItem("almond_water",
            AlmondWaterItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<DeferredSpawnEggItem> BACTERIA_SPAWN_EGG = ITEMS.registerItem("bacteria_spawn_egg",
            props -> new DeferredSpawnEggItem(ModEntities.BACTERIA, 0x0B0B0B, 0x3C3A33, props), new Item.Properties());
    // Crate food (S3): canned food is reliable, backshrooms are a gamble.
    public static final DeferredItem<Item> CANNED_FOOD = ITEMS.registerSimpleItem("canned_food",
            new Item.Properties().stacksTo(16).food(ModFoods.CANNED_FOOD));
    public static final DeferredItem<Item> BACKSHROOM = ITEMS.registerSimpleItem("backshroom",
            new Item.Properties().food(ModFoods.BACKSHROOM));
    // Supplies (v3): sprint energy, sanity and health in a pinch.
    public static final DeferredItem<EnergyBarItem> ENERGY_BAR = ITEMS.registerItem("energy_bar",
            EnergyBarItem::new, new Item.Properties().stacksTo(16).food(ModFoods.ENERGY_BAR));
    public static final DeferredItem<CalmPillsItem> CALM_PILLS = ITEMS.registerItem("calm_pills",
            CalmPillsItem::new, new Item.Properties().stacksTo(8));
    public static final DeferredItem<BandageItem> BANDAGE = ITEMS.registerItem("bandage",
            BandageItem::new, new Item.Properties().stacksTo(16));
    // Level 94 anomalies and supplies: Almond Milk from the milk vans, and the 1953 coin from a sink in a furniture pile.
    public static final DeferredItem<com.rickr.backrooms.item.AlmondMilkItem> ALMOND_MILK = ITEMS.registerItem("almond_milk",
            com.rickr.backrooms.item.AlmondMilkItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<com.rickr.backrooms.item.Coin1953Item> COIN_1953 = ITEMS.registerItem("coin_1953",
            com.rickr.backrooms.item.Coin1953Item::new, new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
    /** An old camcorder for the real world: film the dark long enough and it lets you in. */
    public static final DeferredItem<com.rickr.backrooms.item.CamcorderItem> CAMCORDER = ITEMS.registerItem("camcorder",
            com.rickr.backrooms.item.CamcorderItem::new, new Item.Properties().stacksTo(1));
    /** A torn note from a supply container: a rough (sometimes wrong) direction to the exit. */
    public static final DeferredItem<com.rickr.backrooms.item.WandererNoteItem> WANDERER_NOTE = ITEMS.registerItem("wanderer_note",
            com.rickr.backrooms.item.WandererNoteItem::new, new Item.Properties().stacksTo(1));

    static {
        // Block items so builders can use every Backrooms block in creative.
        ITEMS.registerSimpleBlockItem(ModBlocks.YELLOW_WALLPAPER);
        ITEMS.registerSimpleBlockItem(ModBlocks.LOBBY_CARPET);
        ITEMS.registerSimpleBlockItem(ModBlocks.CEILING_TILE);
        ITEMS.registerSimpleBlockItem(ModBlocks.LOBBY_CARPET_ARROW);
        ITEMS.registerSimpleBlockItem(ModBlocks.GARAGE_PILLAR);
        ITEMS.registerSimpleBlockItem(ModBlocks.CONCRETE_STAIRS);
        ITEMS.registerSimpleBlockItem(ModBlocks.FLUORESCENT_LIGHT);
        ITEMS.registerSimpleBlockItem(ModBlocks.FLUORESCENT_LIGHT_OFF);
        ITEMS.registerSimpleBlockItem(ModBlocks.CONCRETE_WALL);
        ITEMS.registerSimpleBlockItem(ModBlocks.CONCRETE_FLOOR);
        ITEMS.registerSimpleBlockItem(ModBlocks.WET_CONCRETE_FLOOR);
        ITEMS.registerSimpleBlockItem(ModBlocks.WAREHOUSE_LIGHT);
        ITEMS.registerSimpleBlockItem(ModBlocks.PIPE_WALL);
        ITEMS.registerSimpleBlockItem(ModBlocks.RUSTY_PIPE);
        ITEMS.registerSimpleBlockItem(ModBlocks.PIPE_FLOOR);
        ITEMS.registerSimpleBlockItem(ModBlocks.PIPE_LIGHT);
        ITEMS.registerSimpleBlockItem(ModBlocks.EXIT_SIGN);
        ITEMS.registerSimpleBlockItem(ModBlocks.SUPPLY_CRATE);
        // S4 v2 world blocks.
        ITEMS.registerSimpleBlockItem(ModBlocks.YELLOW_WALLPAPER_BASEBOARD);
        ITEMS.registerSimpleBlockItem(ModBlocks.RUG);
        ITEMS.registerSimpleBlockItem(ModBlocks.WALL_ARROW_LEFT);
        ITEMS.registerSimpleBlockItem(ModBlocks.WALL_ARROW_RIGHT);
        ITEMS.registerSimpleBlockItem(ModBlocks.WALL_HANDPRINT);
        ITEMS.registerSimpleBlockItem(ModBlocks.WALL_WINDOW_DRAWING);
        ITEMS.registerSimpleBlockItem(ModBlocks.WALL_DOOR_DRAWING);
        ITEMS.registerSimpleBlockItem(ModBlocks.RED_ALARM_LAMP);
        // Doors need the two-block-tall item so the top half is placed too.
        ITEMS.registerItem("lobby_door", props -> new DoubleHighBlockItem(ModBlocks.LOBBY_DOOR.get(), props), new Item.Properties());
        ITEMS.registerSimpleBlockItem(ModBlocks.STAIRWELL_WALL);
        ITEMS.registerSimpleBlockItem(ModBlocks.CHAIN_LINK);
        ITEMS.registerSimpleBlockItem(ModBlocks.PILLAR_NUMBER);
        ITEMS.registerSimpleBlockItem(ModBlocks.CEILING_BEAM);
        ITEMS.registerSimpleBlockItem(ModBlocks.CEILING_TUBE);
        ITEMS.registerSimpleBlockItem(ModBlocks.PALE_DUCT);
        ITEMS.registerSimpleBlockItem(ModBlocks.PIPE_CEILING);
        ITEMS.registerSimpleBlockItem(ModBlocks.BULKHEAD_LAMP);
        ITEMS.registerSimpleBlockItem(ModBlocks.PIPE_VALVE);
        ITEMS.registerSimpleBlockItem(ModBlocks.FLOOR_GRATE);
        // The Poolrooms.
        ITEMS.registerSimpleBlockItem(ModBlocks.POOL_TILE);
        ITEMS.registerSimpleBlockItem(ModBlocks.POOL_TILE_DARK);
        ITEMS.registerSimpleBlockItem(ModBlocks.POOL_FLOOR);
        ITEMS.registerSimpleBlockItem(ModBlocks.POOL_SKYLIGHT);
        ITEMS.registerSimpleBlockItem(ModBlocks.POOL_LAMP);
        // Ankle-deep water (Level 37 floods its floors with it); invisible once placed.
        ITEMS.registerSimpleBlockItem(ModBlocks.SHALLOW_WATER);
        // Level 37 "Sublimity" (the curved tile pieces have no item: the layout places them).
        ITEMS.registerSimpleBlockItem(ModBlocks.L37_TILE_WALL);
        ITEMS.registerSimpleBlockItem(ModBlocks.L37_TILE_FLOOR);
        ITEMS.registerSimpleBlockItem(ModBlocks.L37_TILE_CEILING);
        ITEMS.registerSimpleBlockItem(ModBlocks.L37_TILE_DEEP);
        ITEMS.registerSimpleBlockItem(ModBlocks.L37_TILE_SLAB);
        ITEMS.registerSimpleBlockItem(ModBlocks.L37_SUNLIT_VOID);
        ITEMS.registerSimpleBlockItem(ModBlocks.L37_POOL_LADDER);
        ITEMS.registerSimpleBlockItem(ModBlocks.L37_HANDRAIL);
        // Level 9: The Suburbs.
        ITEMS.registerSimpleBlockItem(ModBlocks.ASPHALT);
        ITEMS.registerSimpleBlockItem(ModBlocks.ASPHALT_WET);
        ITEMS.registerSimpleBlockItem(ModBlocks.ASPHALT_MARKING);
        ITEMS.registerSimpleBlockItem(ModBlocks.SIDEWALK_SLAB);
        ITEMS.registerSimpleBlockItem(ModBlocks.SIDEWALK_CURB);
        ITEMS.registerSimpleBlockItem(ModBlocks.STORM_DRAIN);
        ITEMS.registerSimpleBlockItem(ModBlocks.MANHOLE);
        ITEMS.registerSimpleBlockItem(ModBlocks.LAWN_GRASS);
        ITEMS.registerSimpleBlockItem(ModBlocks.LEAF_LITTER);
        ITEMS.registerSimpleBlockItem(ModBlocks.DUST_PILE);
        ITEMS.registerSimpleBlockItem(ModBlocks.SIDING_GREEN);
        ITEMS.registerSimpleBlockItem(ModBlocks.SIDING_GREY);
        ITEMS.registerSimpleBlockItem(ModBlocks.SIDING_BLUE);
        ITEMS.registerSimpleBlockItem(ModBlocks.SIDING_YELLOW);
        ITEMS.registerSimpleBlockItem(ModBlocks.SIDING_WHITE);
        ITEMS.registerSimpleBlockItem(ModBlocks.HOUSE_TRIM);
        ITEMS.registerSimpleBlockItem(ModBlocks.BRICK_FOUNDATION);
        ITEMS.registerSimpleBlockItem(ModBlocks.ROOF_SHINGLES);
        ITEMS.registerSimpleBlockItem(ModBlocks.ROOF_SHINGLES_STAIRS);
        ITEMS.registerSimpleBlockItem(ModBlocks.ROOF_SHINGLES_SLAB);
        ITEMS.registerSimpleBlockItem(ModBlocks.HOUSE_WINDOW);
        ITEMS.registerItem("house_door", props -> new DoubleHighBlockItem(ModBlocks.HOUSE_DOOR.get(), props), new Item.Properties());
        ITEMS.registerSimpleBlockItem(ModBlocks.GARAGE_DOOR);
        ITEMS.registerSimpleBlockItem(ModBlocks.PORCH_PLANKS);
        ITEMS.registerSimpleBlockItem(ModBlocks.PORCH_POST);
        ITEMS.registerSimpleBlockItem(ModBlocks.PORCH_LIGHT);
        ITEMS.registerSimpleBlockItem(ModBlocks.BEIGE_CARPET);
        ITEMS.registerSimpleBlockItem(ModBlocks.DIM_WALLPAPER);
        ITEMS.registerSimpleBlockItem(ModBlocks.WOOD_FLOOR);
        ITEMS.registerSimpleBlockItem(ModBlocks.SOFA);
        ITEMS.registerSimpleBlockItem(ModBlocks.TV);
        ITEMS.registerSimpleBlockItem(ModBlocks.FRIDGE);
        ITEMS.registerSimpleBlockItem(ModBlocks.KITCHEN_COUNTER);
        ITEMS.registerSimpleBlockItem(ModBlocks.DINING_TABLE);
        ITEMS.registerSimpleBlockItem(ModBlocks.BED);
        ITEMS.registerSimpleBlockItem(ModBlocks.PICKET_FENCE);
        ITEMS.registerSimpleBlockItem(ModBlocks.MAILBOX);
        ITEMS.registerSimpleBlockItem(ModBlocks.NIGHT_LOG);
        ITEMS.registerSimpleBlockItem(ModBlocks.NIGHT_LEAVES);
        ITEMS.registerSimpleBlockItem(ModBlocks.DEAD_BRANCH);
        ITEMS.registerSimpleBlockItem(ModBlocks.HEDGE);
        ITEMS.registerSimpleBlockItem(ModBlocks.STREETLAMP_POLE);
        ITEMS.registerSimpleBlockItem(ModBlocks.STREETLAMP_ARM);
        ITEMS.registerSimpleBlockItem(ModBlocks.STREETLAMP_HEAD);
        ITEMS.registerSimpleBlockItem(ModBlocks.STREETLAMP_HEAD_MERCURY);
        ITEMS.registerSimpleBlockItem(ModBlocks.SIGN_POST);
        ITEMS.registerSimpleBlockItem(ModBlocks.STREET_SIGN);
        ITEMS.registerSimpleBlockItem(ModBlocks.TUNNEL_CONCRETE);
        ITEMS.registerSimpleBlockItem(ModBlocks.EXIT_TUNNEL_MARKER);
        ITEMS.registerSimpleBlockItem(ModBlocks.HIGHWAY_SIGN_EXIT);
        // Supply containers (v3).
        ITEMS.registerSimpleBlockItem(ModBlocks.BACKPACK);
        ITEMS.registerSimpleBlockItem(ModBlocks.SUPPLY_LOCKER);
        // Safe rooms (v3).
        ITEMS.registerSimpleBlockItem(ModBlocks.CHECKPOINT_RADIO);
        ITEMS.registerSimpleBlockItem(ModBlocks.SAFE_ROOM_LAMP);
        ITEMS.registerSimpleBlockItem(ModBlocks.SAFE_ROOM_SIGN);
        // Level 4: The Abandoned Office.
        ITEMS.registerSimpleBlockItem(ModBlocks.OFFICE_CARPET);
        ITEMS.registerSimpleBlockItem(ModBlocks.OFFICE_CARPET_BLUE);
        ITEMS.registerSimpleBlockItem(ModBlocks.OFFICE_CARPET_INDENT);
        ITEMS.registerSimpleBlockItem(ModBlocks.OFFICE_CARPET_BLUE_INDENT);
        ITEMS.registerSimpleBlockItem(ModBlocks.OFFICE_WALL);
        ITEMS.registerSimpleBlockItem(ModBlocks.OFFICE_WALL_BASE);
        ITEMS.registerSimpleBlockItem(ModBlocks.OFFICE_WALL_RAIL);
        ITEMS.registerSimpleBlockItem(ModBlocks.OFFICE_COLUMN);
        ITEMS.registerSimpleBlockItem(ModBlocks.OFFICE_COLUMN_BASE);
        ITEMS.registerSimpleBlockItem(ModBlocks.OFFICE_CEILING);
        ITEMS.registerSimpleBlockItem(ModBlocks.OFFICE_CEILING_MISSING);
        ITEMS.registerSimpleBlockItem(ModBlocks.OFFICE_CEILING_HATCH);
        ITEMS.registerSimpleBlockItem(ModBlocks.OFFICE_PANEL);
        ITEMS.registerSimpleBlockItem(ModBlocks.OFFICE_WINDOW);
        ITEMS.registerSimpleBlockItem(ModBlocks.OFFICE_GLASS);
        ITEMS.registerSimpleBlockItem(ModBlocks.OFFICE_GLASS_BLINDS);
        ITEMS.registerSimpleBlockItem(ModBlocks.STAGE_CURTAIN);
        ITEMS.registerItem("office_door", props -> new DoubleHighBlockItem(ModBlocks.OFFICE_DOOR.get(), props), new Item.Properties());
        ITEMS.registerItem("office_door_six", props -> new DoubleHighBlockItem(ModBlocks.OFFICE_DOOR_SIX.get(), props), new Item.Properties());
        ITEMS.registerItem("office_glass_door", props -> new DoubleHighBlockItem(ModBlocks.OFFICE_GLASS_DOOR.get(), props), new Item.Properties());
        ITEMS.registerItem("fire_door", props -> new DoubleHighBlockItem(ModBlocks.FIRE_DOOR.get(), props), new Item.Properties());
        ITEMS.registerSimpleBlockItem(ModBlocks.ELEVATOR_DOOR);
        ITEMS.registerSimpleBlockItem(ModBlocks.CUBICLE_PARTITION);
        ITEMS.registerSimpleBlockItem(ModBlocks.RADIATOR);
        ITEMS.registerSimpleBlockItem(ModBlocks.OFFICE_DESK);
        ITEMS.registerSimpleBlockItem(ModBlocks.OFFICE_DESK_PC);
        ITEMS.registerSimpleBlockItem(ModBlocks.OFFICE_CHAIR);
        ITEMS.registerSimpleBlockItem(ModBlocks.FILING_CABINET);
        ITEMS.registerSimpleBlockItem(ModBlocks.COPIER);
        ITEMS.registerSimpleBlockItem(ModBlocks.DEAD_PLANT);
        ITEMS.registerSimpleBlockItem(ModBlocks.CARDBOARD_BOXES);
        ITEMS.registerSimpleBlockItem(ModBlocks.STEP_LADDER);
        ITEMS.registerSimpleBlockItem(ModBlocks.CONFERENCE_TABLE);
        ITEMS.registerSimpleBlockItem(ModBlocks.SERVER_RACK);
        ITEMS.registerSimpleBlockItem(ModBlocks.MICROWAVE);
        ITEMS.registerSimpleBlockItem(ModBlocks.WHITEBOARD);
        ITEMS.registerSimpleBlockItem(ModBlocks.ELEVATOR_PANEL);
        ITEMS.registerSimpleBlockItem(ModBlocks.CCTV_CAMERA);
        ITEMS.registerSimpleBlockItem(ModBlocks.OFFICE_PLAQUE);
        ITEMS.registerSimpleBlockItem(ModBlocks.VENDING_MACHINE);
        ITEMS.registerSimpleBlockItem(ModBlocks.WATER_COOLER);
        ITEMS.registerSimpleBlockItem(ModBlocks.SUPPLY_CABINET);
        // Level 94 props (the vehicle hull has no item: it is an invisible filler placed with the vehicle).
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_SIREN_POLE);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_SIREN);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_VEHICLE);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_TV);
        // Level 94 world blocks.
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_GRASS_LIGHT);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_GRASS_MID);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_GRASS_DARK);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_GRASS_SLAB_LIGHT);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_GRASS_SLAB_MID);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_GRASS_SLAB_DARK);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_DIRT);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_ASPHALT);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_CURB);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_PATH);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_PATH_SLAB);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_PAVING);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_FOUNTAIN_STONE);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_STATUE);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_LEAVES);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_TRUNK);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_TOWER_LEG);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_TOWER_BRACE);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_TANK);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_TANK_ROOF);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_TANK_ROOF_SLAB);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_SIDING);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_HOUSE_WALL);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_BRICK_BAND);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_BRICK);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_WINDOW);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_TRIM);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_FLOOR);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_STAIRS);
        ITEMS.registerItem("l94_house_door", props -> new DoubleHighBlockItem(ModBlocks.L94_HOUSE_DOOR.get(), props), new Item.Properties());
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_ROOF_RED);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_ROOF_RED_STAIRS);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_ROOF_RED_SLAB);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_ROOF_BROWN);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_ROOF_BROWN_STAIRS);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_ROOF_BROWN_SLAB);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_ARMCHAIR);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_RUG);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_LAMP);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_DARK_VOID);
        ITEMS.registerItem("l94_clay_door", props -> new DoubleHighBlockItem(ModBlocks.L94_CLAY_DOOR.get(), props), new Item.Properties());
        // Level 94 castle: the glass mountain, its ramp, the castle and the funhouse inside.
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_GLASS_MOUNTAIN);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_OPAL_PATH);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_OPAL_PATH_SLAB);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_OPAL_RAIL);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_CASTLE_WALL);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_CASTLE_WINDOW);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_CASTLE_ROOF);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_CASTLE_ROOF_STAIRS);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_CASTLE_STONE);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_CASTLE_STONE_STAIRS);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_CASTLE_LAMP);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_CHECKER_FLOOR);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_STRIPE_PATH);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_STAR_TILE);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_DISC_FLOOR);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_RING_FLOOR);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_PLAY_PILLAR);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_PLAY_PILLAR_PORTHOLE);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_PLAY_PIPE);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_PLAY_WALL);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_PLAY_CEILING);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_PLAY_MAT);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_BALL_PIT);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_CLIMBING_FRAME);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_PLAY_TUBE);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_SLIDE);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_DRAWING_DESK);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_SKETCH_WALL);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_STUDIO_TABLE);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_MINI_HOUSE);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_MINI_TREE);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_MINI_FOUNTAIN);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_RED_CARPET);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_THRONE);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_THRONE_LIGHT);
        // Level 94 anomalies and supplies.
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_SINK);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_TOWER_TAP);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_MILK_CRATE);
        ITEMS.registerItem("l94_wandering_door", props -> new DoubleHighBlockItem(ModBlocks.L94_WANDERING_DOOR.get(), props), new Item.Properties());
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_GLARE_PANEL);
        ITEMS.registerSimpleBlockItem(ModBlocks.L94_WHITE_TILE);
        // Level 1 anomalies: crate-hall racks, the crude paintings, condensation, the Corrupted Corridor.
        ITEMS.registerSimpleBlockItem(ModBlocks.L1_PAINTING);
        ITEMS.registerSimpleBlockItem(ModBlocks.L1_FLOOR_PAINTING);
        ITEMS.registerSimpleBlockItem(ModBlocks.L1_CONDENSATION);
        ITEMS.registerSimpleBlockItem(ModBlocks.L1_CINDERBLOCK);
        ITEMS.registerSimpleBlockItem(ModBlocks.L1_BLUE_TILE);
        ITEMS.registerSimpleBlockItem(ModBlocks.L1_BLUE_TILE_SLAB);
        ITEMS.registerSimpleBlockItem(ModBlocks.STAIRWELL_STEP);
        ITEMS.registerSimpleBlockItem(ModBlocks.L1_NOCLIP_WALL);
        ITEMS.registerSimpleBlockItem(ModBlocks.L1_BRIGHT_PATCH);
        ITEMS.registerSimpleBlockItem(ModBlocks.L1_MONOCHROME_WALL);
        ITEMS.registerSimpleBlockItem(ModBlocks.L1_MONOCHROME_FLOOR);
        ITEMS.registerSimpleBlockItem(ModBlocks.L1_VOID_FLOOR);
        // Level 4 anomalies: looping stairwells, the Window, elevator cars, the Cubicles.
        ITEMS.registerSimpleBlockItem(ModBlocks.L4_STAIRWELL_WALL);
        ITEMS.registerItem("l4_stair_door", props -> new DoubleHighBlockItem(ModBlocks.L4_STAIR_DOOR.get(), props), new Item.Properties());
        ITEMS.registerSimpleBlockItem(ModBlocks.L4_FLOOR_SIGN);
        ITEMS.registerSimpleBlockItem(ModBlocks.L4_CLEAR_WINDOW);
        ITEMS.registerSimpleBlockItem(ModBlocks.L4_COVERED_WINDOW);
        ITEMS.registerSimpleBlockItem(ModBlocks.L4_WHITE_WALL);
        ITEMS.registerSimpleBlockItem(ModBlocks.L4_WHITE_FLOOR);
        ITEMS.registerSimpleBlockItem(ModBlocks.L4_ELEVATOR_WALL);
        ITEMS.registerSimpleBlockItem(ModBlocks.L4_ELEVATOR_FLOOR);
        ITEMS.registerSimpleBlockItem(ModBlocks.L4_OFFICE_FRIDGE);
        // Level 2 anomalies: the brownout's emergency lights, dead lamps and black ooze, the hot
        // zones' machines and steam vents, the void doors and the black of the void.
        ITEMS.registerSimpleBlockItem(ModBlocks.L2_EMERGENCY_LIGHT);
        ITEMS.registerSimpleBlockItem(ModBlocks.L2_DEAD_LAMP);
        ITEMS.registerSimpleBlockItem(ModBlocks.L2_BLACK_OOZE);
        ITEMS.registerSimpleBlockItem(ModBlocks.L2_MACHINE);
        ITEMS.registerSimpleBlockItem(ModBlocks.L2_STEAM_VENT);
        ITEMS.registerItem("l2_void_door", props -> new DoubleHighBlockItem(ModBlocks.L2_VOID_DOOR.get(), props), new Item.Properties());
        ITEMS.registerSimpleBlockItem(ModBlocks.L2_VOID_BLACK);
        // Level 0 anomalies: the Red Rooms and their warning signs, the flickering wall, blackout
        // zones, the Manila Room's walls, furniture and notes, the arch wall of the pillar hall.
        ITEMS.registerSimpleBlockItem(ModBlocks.RED_WALLPAPER);
        ITEMS.registerSimpleBlockItem(ModBlocks.RED_WALLPAPER_MOULD);
        ITEMS.registerSimpleBlockItem(ModBlocks.RED_WALL_TALLY);
        ITEMS.registerSimpleBlockItem(ModBlocks.RED_CARPET_COARSE);
        ITEMS.registerSimpleBlockItem(ModBlocks.RED_ROOM_LIGHT);
        ITEMS.registerSimpleBlockItem(ModBlocks.RED_MUSHROOMS);
        ITEMS.registerSimpleBlockItem(ModBlocks.PEELING_WALLPAPER);
        ITEMS.registerSimpleBlockItem(ModBlocks.STICKY_CARPET);
        ITEMS.registerSimpleBlockItem(ModBlocks.FLICKERING_WALL);
        ITEMS.registerSimpleBlockItem(ModBlocks.BLACKOUT_WALLPAPER);
        ITEMS.registerSimpleBlockItem(ModBlocks.CARPET_FLUID);
        ITEMS.registerSimpleBlockItem(ModBlocks.MANILA_WALLPAPER);
        ITEMS.registerSimpleBlockItem(ModBlocks.MANILA_BROWN_WALL);
        ITEMS.registerSimpleBlockItem(ModBlocks.OCTAGON_TABLE);
        ITEMS.registerSimpleBlockItem(ModBlocks.WOODEN_CHAIR);
        ITEMS.registerSimpleBlockItem(ModBlocks.VOLUNTEER_NOTE);
        ITEMS.registerSimpleBlockItem(ModBlocks.ARCH_WALL);
        ITEMS.registerSimpleBlockItem(ModBlocks.ARCH_WALL_STAIRS);
        // Level 9 anomalies: the Crimson Field's grass, the Mangled fog's crater, foxhole sandbags.
        ITEMS.registerSimpleBlockItem(ModBlocks.L9_FIELD_GRASS);
        ITEMS.registerSimpleBlockItem(ModBlocks.L9_CRATER_ASPHALT);
        ITEMS.registerSimpleBlockItem(ModBlocks.L9_CRATER_ASPHALT_SLAB);
        ITEMS.registerSimpleBlockItem(ModBlocks.L9_CRATER_SOIL);
        ITEMS.registerSimpleBlockItem(ModBlocks.L9_SANDBAGS);
        // Level 1: the halls' signs, rebar and pipes, the corridors' mattresses, the storage lockers.
        for (var block : java.util.List.of(ModBlocks.L1_SIGN, ModBlocks.L1_REBAR, ModBlocks.L1_CEILING_PIPE, ModBlocks.L1_SHUTTER,
                ModBlocks.L1_SHUTTER_GROWL, ModBlocks.L1_SHUTTER_ROLLED, ModBlocks.L1_MATTRESS, ModBlocks.L1_LOCKED_DOOR,
                ModBlocks.L1_LOCKED_DOOR_TOP, ModBlocks.L1_PILLAR_PAINTED, ModBlocks.L1_CRACKED_CONCRETE, ModBlocks.L1_STEAMING_PUDDLE,
                ModBlocks.L1_HANGING_LIGHT, ModBlocks.L1_PILLAR_CORNER, ModBlocks.L1_PILLAR_NUMBER, ModBlocks.L1_PILLAR_SIGN,
                ModBlocks.L1_FLOOR_MARK, ModBlocks.L1_WHEEL_STOP, ModBlocks.L1_SPEED_BUMP, ModBlocks.L1_SPRINKLER_PIPE,
                ModBlocks.L1_SPRINKLER_HEAD, ModBlocks.L1_CONDUIT, ModBlocks.L1_CABLE_TRAY, ModBlocks.L1_DUCT,
                ModBlocks.L1_EXTINGUISHER, ModBlocks.L1_CONVEX_MIRROR, ModBlocks.L1_RACK_UPRIGHT, ModBlocks.L1_RACK_BAY)) {
            ITEMS.registerSimpleBlockItem(block);
        }
        // Level 7: the sea, the flooded entrance room and the exit ring.
        for (var block : java.util.List.of(ModBlocks.L7_CONCRETE, ModBlocks.L7_RUST_METAL, ModBlocks.L7_GRATING,
                ModBlocks.L7_CARPET, ModBlocks.L7_SILT, ModBlocks.L7_ROCK, ModBlocks.L7_BONE, ModBlocks.L7_TAR,
                ModBlocks.L7_SCRAP, ModBlocks.L7_PIPE, ModBlocks.L7_PILLAR, ModBlocks.L7_PLATFORM, ModBlocks.L7_EXIT_DOOR,
                ModBlocks.L7_ROPE, ModBlocks.L7_HATCH, ModBlocks.L7_BOOKCASE, ModBlocks.L7_COFFEE_TABLE,
                ModBlocks.L7_ARMCHAIR, ModBlocks.L94_FLOOD_DARK)) {
            ITEMS.registerSimpleBlockItem(block);
        }
    }

    private ModItems() {}
}
