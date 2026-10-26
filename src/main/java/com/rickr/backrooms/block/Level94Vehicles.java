package com.rickr.backrooms.block;

import com.rickr.backrooms.registry.ModBlocks;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The parked cars and milk vans of Level 94: which blocks make up one vehicle.
 *
 * <p>A vehicle is one {@link VehicleBlock} "anchor" (its renderer draws the whole vehicle) plus
 * invisible hull blocks around it, so you bump into the car body instead of walking through it.
 * The world layout asks {@link #cell} for every block near the anchor and places what it gets.
 *
 * <p>Footprints, counted from the anchor in the vehicle's own directions ("along" = towards its
 * front, "side" = towards its right-hand side when you sit in it):
 * <ul>
 *   <li>Car: 2 wide (side 0..1), 5 long (along -2..2), 2 tall. The body is drawn 4.5 long.</li>
 *   <li>Milk van: 3 wide (side -1..1), 5 long (along -2..2), 3 tall. The body is drawn 2.5 wide.
 *       A crate of Almond Milk stands on the ground at its back doors (along -3, side 0): the
 *       Level 94 wiki says the vans carry Almond Milk, and the invisible hull can't be clicked.</li>
 * </ul>
 */
public final class Level94Vehicles {
    /** Which vehicle an anchor draws. Also a block-state property, so it needs a lower-case name. */
    public enum Kind implements StringRepresentable {
        CAR_RED, CAR_BLACK, CAR_CREAM, MILK_VAN;

        private final String serializedName = name().toLowerCase(Locale.ROOT);

        public boolean isVan() {
            return this == MILK_VAN;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    /** No footprint reaches further than this from the anchor, in x or z. */
    public static final int REACH = 3;

    private Level94Vehicles() {}

    /**
     * The block for the cell at (dx, dy, dz) from a parked vehicle's anchor (dy = 0 is the ground
     * layer it stands on, i.e. feet level), or null if that cell is outside the vehicle.
     *
     * @param facing the way the vehicle's front points
     */
    @Nullable
    public static BlockState cell(Kind kind, Direction facing, int dx, int dy, int dz) {
        if (dx == 0 && dy == 0 && dz == 0) {
            return ModBlocks.L94_VEHICLE.get().defaultBlockState()
                    .setValue(VehicleBlock.FACING, facing)
                    .setValue(VehicleBlock.KIND, kind);
        }
        // Turn the world offset into the vehicle's own directions. Both are unit steps along x or z,
        // so a dot product with each gives how far along / to the side the cell is.
        Direction right = facing.getClockWise();
        int along = dx * facing.getStepX() + dz * facing.getStepZ();
        int side = dx * right.getStepX() + dz * right.getStepZ();
        boolean inside = kind.isVan()
                ? dy >= 0 && dy <= 2 && side >= -1 && side <= 1 && along >= -2 && along <= 2
                : dy >= 0 && dy <= 1 && side >= 0 && side <= 1 && along >= -2 && along <= 2;
        if (!inside && kind.isVan() && dy == 0 && side == 0 && along == -3) {
            return ModBlocks.L94_MILK_CRATE.get().defaultBlockState().setValue(WallMountBlock.FACING, facing.getOpposite());
        }
        return inside ? ModBlocks.L94_VEHICLE_HULL.get().defaultBlockState() : null;
    }

    /**
     * How far (in blocks) the drawn body's centre sits to the right of the anchor block's centre.
     * Cars are two blocks wide, so their middle is on the line between the anchor and the cell to
     * its right; the three-wide van is centred on the anchor. Used by the renderer.
     */
    public static float sideOffset(Kind kind) {
        return kind.isVan() ? 0.0f : 0.5f;
    }
}
