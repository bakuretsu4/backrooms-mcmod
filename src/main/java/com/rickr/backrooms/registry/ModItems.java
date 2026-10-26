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
    }

    private ModItems() {}
}
