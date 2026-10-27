package com.rickr.backrooms.block;

import com.rickr.backrooms.registry.ModBlockEntities;
import com.rickr.backrooms.registry.ModSounds;
import java.util.function.Supplier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;

/**
 * The three kinds of supply container. They all work the same way ({@link SupplyContainerBlock}
 * + {@link SupplyContainerBlockEntity}); this enum holds what is different about each one:
 * how many rows of slots, which loot table folder, what the screen is called and how it sounds.
 */
public enum SupplyKind {
    /** Wooden Crate: 27 slots, mostly food. */
    CRATE("crate", 3, "container.backrooms.supply_crate", () -> SoundEvents.BARREL_OPEN, () -> SoundEvents.BARREL_CLOSE),
    /** Backpack someone dropped: only 9 slots, but the best loot. */
    BACKPACK("backpack", 1, "container.backrooms.backpack", null, null),
    /** Metal supply locker: 27 slots of useful gear (batteries, bandages, pills). */
    LOCKER("locker", 3, "container.backrooms.supply_locker", ModSounds.LOCKER_OPEN, ModSounds.LOCKER_CLOSE);

    private final String lootName;
    private final int rows;
    private final String titleKey;
    /** Null: opens and closes without a sound (a backpack is just picked up and looked into). */
    @Nullable
    private final Supplier<SoundEvent> openSound;
    @Nullable
    private final Supplier<SoundEvent> closeSound;

    SupplyKind(String lootName, int rows, String titleKey, @Nullable Supplier<SoundEvent> openSound,
               @Nullable Supplier<SoundEvent> closeSound) {
        this.lootName = lootName;
        this.rows = rows;
        this.titleKey = titleKey;
        this.openSound = openSound;
        this.closeSound = closeSound;
    }

    /** Folder under data/backrooms/loot_table/supplies/ that holds this container's loot tables. */
    public String lootName() {
        return lootName;
    }

    /** Rows of 9 slots in the chest screen. */
    public int rows() {
        return rows;
    }

    public int size() {
        return rows * 9;
    }

    /** Lang key of the title shown at the top of the screen. */
    public String titleKey() {
        return titleKey;
    }

    @Nullable
    public SoundEvent openSound() {
        return openSound == null ? null : openSound.get();
    }

    @Nullable
    public SoundEvent closeSound() {
        return closeSound == null ? null : closeSound.get();
    }

    /** The registered block entity type for this kind (see ModBlockEntities). */
    public BlockEntityType<SupplyContainerBlockEntity> blockEntityType() {
        return switch (this) {
            case CRATE -> ModBlockEntities.SUPPLY_CRATE.get();
            case BACKPACK -> ModBlockEntities.BACKPACK.get();
            case LOCKER -> ModBlockEntities.SUPPLY_LOCKER.get();
        };
    }
}
