package com.rickr.backrooms.registry;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.item.AlmondWaterItem;
import com.rickr.backrooms.item.CamcorderBatteryItem;
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
        // The Poolrooms.
        ITEMS.registerSimpleBlockItem(ModBlocks.POOL_TILE);
        ITEMS.registerSimpleBlockItem(ModBlocks.POOL_TILE_DARK);
        ITEMS.registerSimpleBlockItem(ModBlocks.POOL_FLOOR);
        ITEMS.registerSimpleBlockItem(ModBlocks.POOL_SKYLIGHT);
        ITEMS.registerSimpleBlockItem(ModBlocks.POOL_LAMP);
    }

    private ModItems() {}
}
