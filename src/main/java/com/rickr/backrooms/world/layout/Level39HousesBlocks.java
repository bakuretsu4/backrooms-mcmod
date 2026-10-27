package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.HouseDoorBlock;
import com.rickr.backrooms.block.Level39BulbBlock;
import com.rickr.backrooms.block.Level39FurnitureBlock;
import com.rickr.backrooms.block.Level39HouseWallBlock;
import com.rickr.backrooms.block.Level39KnockWallBlock;
import com.rickr.backrooms.block.Level39LowRoofBlock;
import com.rickr.backrooms.block.Level39RoofBlock;
import com.rickr.backrooms.block.Level39PairFurnitureBlock;
import com.rickr.backrooms.block.Level39SupplyFurnitureBlock;
import com.rickr.backrooms.block.Level39SurfaceBlock;
import com.rickr.backrooms.block.WallDecalBlock;
import com.rickr.backrooms.registry.Level39Blocks;
import com.rickr.backrooms.registry.Level39HouseBlocks;
import com.rickr.backrooms.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * The little "block language" the Level 39 house builder ({@link Level39HousesBuilder}) draws in.
 *
 * <p>Why not draw BlockStates straight away? The houses get turned upside down, cut in half and
 * pushed into each other after they are drawn, and all of that is easier on plain numbers: turning
 * a piece upside down is flipping one bit, "is this a wall?" is comparing one number. Only when a
 * house is finished are its codes turned into real blocks ({@link #resolve}), once per house.
 *
 * <p>A code is an int: bits 0-7 the KIND, 8-9 the facing (Minecraft's 2D direction number),
 * bit 10 INV (upside down / top half), bit 11 SECOND (second or upper half), bit 12 FLAG (lit,
 * locked), bits 13-16 a small PARAM (wall finish, stair shape, bottles left, rail sides), bits
 * 17-18 the paint SHADE and bit 19 HINGE (door hinge on the right).
 */
final class Level39HousesBlocks {
    private Level39HousesBlocks() {}

    // ---- kinds --------------------------------------------------------------------------------
    /** Leave whatever the town put there (yard, sky). */
    static final int EMPTY = 0;
    static final int AIR = 1;
    static final int WATER = 2;
    static final int FLOOR_OAK = 3;
    static final int FLOOR_DARK = 4;
    static final int LINO = 5;
    static final int BATH_TILE = 6;
    static final int POPCORN = 7;
    static final int CONCRETE = 8;
    /** An inside wall; PARAM = the finish (Level39HouseWallBlock.INNERS). */
    static final int INWALL = 9;
    /** An outside wall: clapboard on the facing side, PARAM finish inside, SHADE paint. */
    static final int EXTWALL = 10;
    static final int KNOCK = 11;
    static final int WINDOW = 12;
    static final int WINDOW_SHADE = 13;
    static final int WINDOW_LIT = 14;
    static final int TRIM = 15;
    /** Clapboard on every side (SHADE): dormer cheeks and porch gables. */
    static final int CLAP = 16;
    static final int BRICK = 20;
    static final int FOUNDATION = 21;
    static final int PORCH_PLANKS = 22;
    static final int PORCH_STAIR = 23;
    static final int OAK_STAIR = 24;
    /** PARAM bits: 1 north, 2 east, 4 south, 8 west (which sides the rail runs to). */
    static final int RAIL = 25;
    static final int POST_WHITE = 26;
    static final int POST_CRAFT = 27;
    /** FLAG = locked. */
    static final int FRONT_DOOR = 28;
    static final int INT_DOOR = 29;
    static final int GARAGE_DOOR = 30;
    /** FLAG = lit, INV = standing up out of the floor. */
    static final int BULB = 31;
    static final int BOXES = 32;
    static final int SOIL = 33;
    /** Furniture: FURN + index into {@link #FURNITURE}. PARAM = bottles left (shelf, icebox). */
    static final int FURN = 64;

    /** The cellar hatch: an iron trapdoor lying in the floor (top half), FACING = its hinge side away. */
    static final int HATCH_TOP = 34;
    /** A wooden ladder down into the cellar, FACING = away from the wall it hangs on. */
    static final int LADDER = 35;
    /** Smooth steep roof piece: FACING = up-slope, PARAM = Level39RoofBlock.Shape ordinal, TRIM bits, SHADE. */
    static final int ROOF = 36;
    /** Smooth low roof piece; SECOND = the upper half of its block. */
    static final int ROOF_LOW = 37;
    /** Roof trim (Level39RoofBlock.Trim ordinal) lives in bits 20-21. */
    static final int TRIM_SHIFT = 20;
    static final int ARMCHAIR = FURN;
    static final int SOFA = FURN + 1;
    static final int SIDE_TABLE = FURN + 2;
    static final int RADIO = FURN + 3;
    static final int BOOKSHELF = FURN + 4;
    static final int PIANO = FURN + 5;
    static final int WARDROBE = FURN + 6;
    static final int DRESSER = FURN + 7;
    static final int BED = FURN + 8;
    static final int TUB = FURN + 9;
    static final int PED_SINK = FURN + 10;
    static final int TOILET = FURN + 11;
    static final int ICEBOX = FURN + 12;
    static final int SHELF = FURN + 13;
    static final int STOVE = FURN + 14;
    static final int COUNTER = FURN + 15;
    static final int KITCHEN_SINK = FURN + 16;
    static final int KITCHEN_DRESSER = FURN + 17;
    static final int TABLE = FURN + 18;
    static final int CHAIR = FURN + 19;
    static final int PICTURE = FURN + 20;
    static final int BOILER = FURN + 21;

    /**
     * The furniture blocks by index, in a holder class of their own so that drawing a house (which
     * only uses the numbers above) never needs the block registry; only {@link #resolve} does.
     */
    private static final class Furniture {
        @SuppressWarnings("unchecked")
        static final DeferredBlock<? extends Level39FurnitureBlock>[] BLOCKS = new DeferredBlock[] {
                Level39HouseBlocks.ARMCHAIR, Level39HouseBlocks.SOFA, Level39HouseBlocks.SIDE_TABLE, Level39HouseBlocks.RADIO,
                Level39HouseBlocks.BOOKSHELF, Level39HouseBlocks.PIANO, Level39HouseBlocks.WARDROBE, Level39HouseBlocks.DRESSER,
                Level39HouseBlocks.BED, Level39HouseBlocks.CLAWFOOT_TUB, Level39HouseBlocks.PEDESTAL_SINK, Level39HouseBlocks.TOILET,
                Level39HouseBlocks.ICEBOX, Level39HouseBlocks.ALMOND_SHELF, Level39HouseBlocks.STOVE, Level39HouseBlocks.COUNTER,
                Level39HouseBlocks.KITCHEN_SINK, Level39HouseBlocks.KITCHEN_DRESSER, Level39HouseBlocks.DINING_TABLE,
                Level39HouseBlocks.DINING_CHAIR, Level39HouseBlocks.PICTURE_FRAME, Level39HouseBlocks.BOILER};
    }

    /** Two-block pieces and how their halves sit (null = one block). */
    static Level39PairFurnitureBlock.Layout pairLayout(int kind) {
        return switch (kind) {
            case SOFA, PIANO -> Level39PairFurnitureBlock.Layout.WIDE;
            case WARDROBE, KITCHEN_DRESSER -> Level39PairFurnitureBlock.Layout.TALL;
            case BED, TUB -> Level39PairFurnitureBlock.Layout.LONG;
            default -> null;
        };
    }

    // ---- bit fields ---------------------------------------------------------------------------
    static final int INV = 1 << 10;
    static final int SECOND = 1 << 11;
    static final int FLAG = 1 << 12;
    static final int HINGE = 1 << 19;

    static int kind(int code) {
        return code & 0xFF;
    }

    static Direction facing(int code) {
        return Direction.from2DDataValue((code >> 8) & 3);
    }

    static int param(int code) {
        return (code >> 13) & 15;
    }

    static int shade(int code) {
        return (code >> 17) & 3;
    }

    static int code(int kind) {
        return kind;
    }

    static int code(int kind, Direction facing) {
        return kind | (facing.get2DDataValue() << 8);
    }

    static int withParam(int code, int param) {
        return (code & ~(15 << 13)) | ((param & 15) << 13);
    }

    static int withShade(int code, int shade) {
        return (code & ~(3 << 17)) | ((shade & 3) << 17);
    }

    static int withFacing(int code, Direction facing) {
        return (code & ~(3 << 8)) | (facing.get2DDataValue() << 8);
    }

    static boolean isFurniture(int code) {
        return kind(code) >= FURN;
    }

    /** Things you can't walk through and that hold a room together (used when houses are merged). */
    static boolean isSolid(int code) {
        int k = kind(code);
        return k != EMPTY && k != AIR && k != WATER && k != BULB;
    }

    /** Walls of any kind (for the "walls cut through rooms" look of merged houses and the furniture placer). */
    static boolean isWall(int code) {
        int k = kind(code);
        return k == INWALL || k == EXTWALL || k == KNOCK || k == WINDOW || k == WINDOW_SHADE || k == WINDOW_LIT
                || k == TRIM || k == CLAP || k == BRICK || k == FOUNDATION;
    }

    /** Doors can't stand upside down (their halves fall apart); flipped houses lose them. */
    static boolean isDoor(int code) {
        int k = kind(code);
        return k == FRONT_DOOR || k == INT_DOOR;
    }

    /**
     * The same block turned upside down (for the inverted houses). A two-wide piece also swaps
     * which half is "second": upside down it is mirrored left to right, so its first half is now
     * the one on the other side (see Level39PairFurnitureBlock.secondOffset).
     */
    static int flip(int code) {
        int k = kind(code);
        return switch (k) {
            case FRONT_DOOR, INT_DOOR -> AIR;
            case FLOOR_OAK, FLOOR_DARK, LINO, BATH_TILE, POPCORN, INWALL, EXTWALL,
                 PORCH_STAIR, OAK_STAIR, BULB, ROOF, ROOF_LOW -> code ^ INV;
            default -> k >= FURN ? (pairLayout(k) == Level39PairFurnitureBlock.Layout.WIDE ? code ^ INV ^ SECOND : code ^ INV) : code;
        };
    }

    // ---- codes -> blocks ----------------------------------------------------------------------

    /** The real block for a code. Called once per distinct code per house. */
    static BlockState resolve(int code) {
        int k = kind(code);
        boolean inv = (code & INV) != 0;
        Direction f = facing(code);
        int p = param(code);
        int shade = shade(code);
        if (k >= FURN) {
            int index = k - FURN;
            BlockState s = Furniture.BLOCKS[index].get().defaultBlockState()
                    .setValue(Level39FurnitureBlock.FACING, f)
                    .setValue(Level39FurnitureBlock.INVERTED, inv);
            if (s.hasProperty(Level39PairFurnitureBlock.SECOND)) {
                s = s.setValue(Level39PairFurnitureBlock.SECOND, (code & SECOND) != 0);
            }
            if (s.hasProperty(Level39SupplyFurnitureBlock.LEFT)) {
                s = s.setValue(Level39SupplyFurnitureBlock.LEFT, Math.min(p, Level39SupplyFurnitureBlock.MAX_LEFT));
            }
            return s;
        }
        return switch (k) {
            case AIR -> Blocks.AIR.defaultBlockState();
            case WATER -> Blocks.WATER.defaultBlockState();
            case FLOOR_OAK -> surface(Level39HouseBlocks.FLOORBOARDS.get(), inv);
            case FLOOR_DARK -> surface(Level39HouseBlocks.FLOORBOARDS_DARK.get(), inv);
            case LINO -> surface(Level39HouseBlocks.LINOLEUM.get(), inv);
            case BATH_TILE -> surface(Level39HouseBlocks.BATH_TILE.get(), inv);
            case POPCORN -> surface(Level39HouseBlocks.POPCORN_CEILING.get(), inv);
            case CONCRETE -> ModBlocks.CONCRETE_FLOOR.get().defaultBlockState();
            case SOIL -> Level39Blocks.SOIL.get().defaultBlockState();
            case INWALL -> surface(inner(p), inv);
            case EXTWALL -> Level39HouseBlocks.HOUSE_WALL.get().defaultBlockState()
                    .setValue(WallDecalBlock.FACING, f)
                    .setValue(Level39HouseWallBlock.INNER, Math.min(p, Level39HouseWallBlock.INNERS.length - 1))
                    .setValue(Level39HouseWallBlock.SHADE, Math.min(shade, 2))
                    .setValue(Level39HouseWallBlock.INVERTED, inv);
            case KNOCK -> Level39HouseBlocks.KNOCK_WALL.get().defaultBlockState()
                    .setValue(WallDecalBlock.FACING, f)
                    .setValue(Level39KnockWallBlock.SHADE, Math.min(shade, 2));
            case WINDOW -> Level39Blocks.SASH_WINDOW.get().defaultBlockState().setValue(WallDecalBlock.FACING, f);
            case WINDOW_SHADE -> Level39HouseBlocks.SASH_WINDOW_SHADE.get().defaultBlockState().setValue(WallDecalBlock.FACING, f);
            case WINDOW_LIT -> Level39Blocks.SASH_WINDOW_LIT.get().defaultBlockState().setValue(WallDecalBlock.FACING, f);
            case TRIM -> Level39Blocks.WHITE_TRIM.get().defaultBlockState();
            case CLAP -> (shade == 0 ? Level39Blocks.CLAPBOARD.get() : shade == 1 ? Level39Blocks.CLAPBOARD_FADED.get()
                    : Level39Blocks.CLAPBOARD_WEATHERED.get()).defaultBlockState();
            case BRICK -> Level39Blocks.CHIMNEY_BRICK.get().defaultBlockState();
            case FOUNDATION -> Level39Blocks.FOUNDATION.get().defaultBlockState();
            case PORCH_PLANKS -> Level39Blocks.PORCH_PLANKS.get().defaultBlockState();
            case PORCH_STAIR -> stairs(Level39HouseBlocks.PORCH_STAIRS.get(), f, inv, 0);
            case OAK_STAIR -> stairs(Level39HouseBlocks.OAK_STAIRS.get(), f, inv, p);
            case RAIL -> Level39HouseBlocks.SPINDLE_RAIL.get().defaultBlockState()
                    .setValue(FenceBlock.NORTH, (p & 1) != 0)
                    .setValue(FenceBlock.EAST, (p & 2) != 0)
                    .setValue(FenceBlock.SOUTH, (p & 4) != 0)
                    .setValue(FenceBlock.WEST, (p & 8) != 0);
            case POST_WHITE -> Level39Blocks.WHITE_POST.get().defaultBlockState();
            case POST_CRAFT -> Level39HouseBlocks.CRAFTSMAN_POST.get().defaultBlockState();
            case FRONT_DOOR -> door(Level39Blocks.HOUSE_DOOR.get().defaultBlockState(), code)
                    .setValue(HouseDoorBlock.LOCKED, (code & FLAG) != 0);
            case INT_DOOR -> door(Level39HouseBlocks.INTERIOR_DOOR.get().defaultBlockState(), code)
                    .setValue(HouseDoorBlock.LOCKED, (code & FLAG) != 0);
            case GARAGE_DOOR -> ModBlocks.GARAGE_DOOR.get().defaultBlockState().setValue(WallDecalBlock.FACING, f);
            case BULB -> Level39HouseBlocks.CEILING_BULB.get().defaultBlockState()
                    .setValue(Level39BulbBlock.LIT, (code & FLAG) != 0)
                    .setValue(Level39BulbBlock.HANGING, !inv)
                    .setValue(Level39BulbBlock.FLICKER, (code & SECOND) != 0);
            case BOXES -> ModBlocks.CARDBOARD_BOXES.get().defaultBlockState().setValue(WallDecalBlock.FACING, f);
            case HATCH_TOP -> Level39Blocks.HATCH.get().defaultBlockState()
                    .setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, f)
                    .setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, Half.TOP)
                    .setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, false);
            case LADDER -> Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, f);
            case ROOF, ROOF_LOW -> {
                BlockState s = (k == ROOF ? Level39HouseBlocks.ROOF.get() : Level39HouseBlocks.ROOF_LOW.get()).defaultBlockState()
                        .setValue(Level39RoofBlock.FACING, f)
                        .setValue(Level39RoofBlock.SHAPE, Level39RoofBlock.Shape.values()[Math.min(p, 5)])
                        .setValue(Level39RoofBlock.TRIM, Level39RoofBlock.Trim.values()[Math.min((code >> TRIM_SHIFT) & 3, 2)])
                        .setValue(Level39RoofBlock.SHADE, Math.min(shade, 2))
                        .setValue(Level39RoofBlock.INVERTED, inv);
                yield k == ROOF_LOW ? s.setValue(Level39LowRoofBlock.UPPER, (code & SECOND) != 0) : s;
            }
            default -> Blocks.AIR.defaultBlockState();
        };
    }

    private static BlockState surface(Block block, boolean inverted) {
        return block.defaultBlockState().setValue(Level39SurfaceBlock.INVERTED, inverted);
    }

    /** The surface block for a wall finish (Level39HouseWallBlock.INNERS order). */
    private static Block inner(int index) {
        return switch (index) {
            case 0 -> Level39HouseBlocks.PAPER_DAMASK.get();
            case 1 -> Level39HouseBlocks.PAPER_STRIPE.get();
            case 2 -> Level39HouseBlocks.PAPER_FLORAL.get();
            case 3 -> Level39HouseBlocks.PAPER_ROSE.get();
            case 4 -> Level39HouseBlocks.PAPER_LATTICE.get();
            case 5 -> Level39HouseBlocks.PAPER_DAMASK_BASE.get();
            case 6 -> Level39HouseBlocks.PAPER_STRIPE_BASE.get();
            case 7 -> Level39HouseBlocks.PAPER_FLORAL_BASE.get();
            case 8 -> Level39HouseBlocks.PAPER_ROSE_BASE.get();
            case 9 -> Level39HouseBlocks.PAPER_LATTICE_BASE.get();
            case Level39HouseWallBlock.BEADBOARD -> Level39HouseBlocks.BEADBOARD.get();
            case Level39HouseWallBlock.WAINSCOT -> Level39HouseBlocks.WAINSCOT.get();
            default -> Level39HouseBlocks.PLASTER.get();
        };
    }

    private static BlockState stairs(Block block, Direction facing, boolean top, int shape) {
        return block.defaultBlockState()
                .setValue(StairBlock.FACING, facing)
                .setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM)
                .setValue(StairBlock.SHAPE, StairsShape.values()[Math.min(shape, StairsShape.values().length - 1)]);
    }

    private static BlockState door(BlockState door, int code) {
        return door.setValue(DoorBlock.FACING, facing(code))
                .setValue(DoorBlock.HALF, (code & SECOND) != 0 ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.HINGE, (code & HINGE) != 0 ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT)
                .setValue(DoorBlock.OPEN, false);
    }
}
