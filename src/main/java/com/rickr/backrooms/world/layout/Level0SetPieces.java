package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.Level0MannequinBlock;
import com.rickr.backrooms.block.RugBlock;
import com.rickr.backrooms.block.WallDoorDrawingBlock;
import com.rickr.backrooms.registry.Level0Blocks;
import com.rickr.backrooms.registry.Level0LightBlocks;
import com.rickr.backrooms.registry.Level0SetPieceBlocks;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * What Level 0's three big set pieces ({@link Level0Atrium}, {@link Level0ShaftHall},
 * {@link Level0FrontOffice}) have in common.
 *
 * <ul>
 *   <li><b>A turned frame.</b> Each set piece is drawn once, in its own "local" grid (u across, v
 *       down, local north = towards v = 0), and every site turns it a different way (its
 *       {@code facing} = how many quarter turns clockwise), so two atriums never look the same
 *       from the same direction. {@link #u}/{@link #v} turn a world column into local
 *       coordinates, {@link #worldX}/{@link #worldZ} turn them back and {@link #toWorld} turns a
 *       local direction (for doors, lamps, chairs) into the real one.</li>
 *   <li><b>Piece codes.</b> The set pieces don't build blocks straight away. For each column they
 *       first work out a small number per height ({@link #CARPET}, {@link #TILE}, {@link #PENDANT}
 *       ...), and only {@link #state} turns that number into a block. That keeps the shape a pure
 *       piece of maths (a throwaway preview script can draw it without starting Minecraft) and
 *       allocates nothing while the world generates.</li>
 * </ul>
 */
public final class Level0SetPieces {
    /**
     * The roof of the tall spaces (the atrium void, the shafts): the last-but-one block layer of
     * Level 0's world, which is 64 blocks tall (y0..y63). Lights hang one block under it.
     */
    public static final int ROOF_Y = 62;

    // ---- Piece codes (what one block of a set piece is) ------------------------------------------
    static final int AIR = 0;
    static final int CARPET = 1;
    static final int WALLPAPER = 2;
    static final int BASEBOARD = 3;
    static final int TILE = 4;
    /** A lit Level 0 troffer of some age (Level0Tubes), or a dead one. */
    static final int TUBE = 5;
    static final int TUBE_DEAD = 6;
    /** Chest-high partition top: the balcony's parapet. */
    static final int PARAPET = 7;
    static final int LADDER = 8;
    static final int CHAIN = 9;
    static final int PENDANT = 10;
    static final int LAMP = 11;
    /** An emergency lamp on standby: it only comes on when the power fails around it. */
    static final int LAMP_STANDBY = 12;
    static final int BEACON = 13;
    static final int DOOR_LOWER = 14;
    static final int DOOR_UPPER = 15;
    static final int WINDOW = 16;
    static final int RUG = 17;
    static final int WET_CARPET = 18;
    /** A fallen ceiling tile lying on the carpet. */
    static final int DEBRIS = 19;
    static final int CABLE = 20;
    static final int ARROW = 21;
    static final int CRATE = 22;
    static final int CHAIR = 23;
    static final int MANNEQUIN_LOWER = 24;
    static final int MANNEQUIN_UPPER = 25;
    static final int DESK_PC = 26;
    static final int DESK = 27;
    static final int RADIO = 28;
    static final int COOLER = 29;
    static final int CABINET = 30;
    static final int FILING = 31;
    static final int WARM_LAMP = 32;
    static final int EXIT_SIGN = 33;

    private Level0SetPieces() {}

    // ---- The turned frame ----------------------------------------------------------------------

    /**
     * Local u of world column (x, z): 0 at the first column inside the moat. Sites are square, so
     * the turn never changes the size. Columns in the moat give -moat..-1 or box..box+moat-1.
     */
    static int u(AnomalySite site, int moat, int x, int z) {
        int size = site.maxX() - site.minX() + 1;
        int i = x - site.minX();
        int j = z - site.minZ();
        return switch (site.facing() & 3) {
            case 0 -> i;
            case 1 -> j;
            case 2 -> size - 1 - i;
            default -> size - 1 - j;
        } - moat;
    }

    /** Local v of world column (x, z); see {@link #u}. */
    static int v(AnomalySite site, int moat, int x, int z) {
        int size = site.maxX() - site.minX() + 1;
        int i = x - site.minX();
        int j = z - site.minZ();
        return switch (site.facing() & 3) {
            case 0 -> j;
            case 1 -> size - 1 - i;
            case 2 -> size - 1 - j;
            default -> i;
        } - moat;
    }

    /** World x of local column (u, v) (the inverse of {@link #u}/{@link #v}). */
    public static int worldX(AnomalySite site, int moat, int u, int v) {
        int size = site.maxX() - site.minX() + 1;
        int a = u + moat;
        int b = v + moat;
        return site.minX() + switch (site.facing() & 3) {
            case 0 -> a;
            case 1 -> size - 1 - b;
            case 2 -> size - 1 - a;
            default -> b;
        };
    }

    /** World z of local column (u, v). */
    public static int worldZ(AnomalySite site, int moat, int u, int v) {
        int size = site.maxX() - site.minX() + 1;
        int a = u + moat;
        int b = v + moat;
        return site.minZ() + switch (site.facing() & 3) {
            case 0 -> b;
            case 1 -> a;
            case 2 -> size - 1 - b;
            default -> size - 1 - a;
        };
    }

    /** The real direction of a local one: the frame is turned clockwise {@code facing} times. */
    public static Direction toWorld(AnomalySite site, Direction local) {
        Direction d = local;
        for (int i = site.facing() & 3; i > 0; i--) {
            d = d.getClockWise();
        }
        return d;
    }

    /** Local direction as a 2-bit number (0 N, 1 E, 2 S, 3 W) packed into column words. */
    static Direction localDir(int bits) {
        return switch (bits & 3) {
            case 0 -> Direction.NORTH;
            case 1 -> Direction.EAST;
            case 2 -> Direction.SOUTH;
            default -> Direction.WEST;
        };
    }

    /**
     * A spot to stand on, for /backrooms anomaly visit: {x, feet y, z, yaw, pitch} at the middle of
     * local column (u, v), looking along local direction {@code look}.
     */
    static double[] spot(AnomalySite site, int moat, int u, int v, double feetY, Direction look, float pitch) {
        return new double[] {worldX(site, moat, u, v) + 0.5, feetY, worldZ(site, moat, u, v) + 0.5,
                toWorld(site, look).toYRot(), pitch};
    }

    static final int N = 0;
    static final int E = 1;
    static final int S = 2;
    static final int W = 3;

    // ---- Codes to blocks -----------------------------------------------------------------------

    /**
     * The block for a piece code. {@code facing} is already the world direction; {@code extra}
     * carries the rug part (0..8) for {@link #RUG}. {@code seed}, x and z give lit tubes their age.
     * Every block here that turns uses the same "facing" property, so one constant serves them all.
     */
    static BlockState state(int code, Direction facing, int extra, long seed, int x, int z) {
        return switch (code) {
            case CARPET -> ModBlocks.LOBBY_CARPET.get().defaultBlockState();
            case WALLPAPER -> ModBlocks.YELLOW_WALLPAPER.get().defaultBlockState();
            case BASEBOARD -> ModBlocks.YELLOW_WALLPAPER_BASEBOARD.get().defaultBlockState();
            case TILE -> ModBlocks.CEILING_TILE.get().defaultBlockState();
            case TUBE -> Level0Tubes.pick(seed, x, z, false, false);
            case TUBE_DEAD -> Level0Tubes.pick(seed, x, z, true, false);
            case PARAPET -> turned(Level0Blocks.HALF_WALL_CAP.get().defaultBlockState(), facing);
            case LADDER -> turned(Level0Blocks.LADDER.get().defaultBlockState(), facing);
            case CHAIN -> Blocks.CHAIN.defaultBlockState();
            case PENDANT -> Level0LightBlocks.PENDANT_LIGHT.get().defaultBlockState();
            case LAMP, LAMP_STANDBY -> turned(Level0LightBlocks.EMERGENCY_LAMP.get().defaultBlockState(), facing)
                    .setValue(BlockStateProperties.LIT, code == LAMP);
            case BEACON -> turned(Level0LightBlocks.BEACON.get().defaultBlockState(), facing)
                    .setValue(BlockStateProperties.LIT, true);
            case DOOR_LOWER, DOOR_UPPER -> turned(ModBlocks.WALL_DOOR_DRAWING.get().defaultBlockState(), facing)
                    .setValue(WallDoorDrawingBlock.HALF, code == DOOR_LOWER ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
            case WINDOW -> turned(ModBlocks.WALL_WINDOW_DRAWING.get().defaultBlockState(), facing);
            case RUG -> ModBlocks.RUG.get().defaultBlockState().setValue(RugBlock.PART, extra);
            case WET_CARPET -> ModBlocks.CARPET_FLUID.get().defaultBlockState();
            case DEBRIS -> Level0SetPieceBlocks.FALLEN_TILE.get().defaultBlockState();
            case CABLE -> turned(Level0Blocks.DANGLING_CABLE.get().defaultBlockState(), facing);
            case ARROW -> turned(ModBlocks.LOBBY_CARPET_ARROW.get().defaultBlockState(), facing);
            case CRATE -> ModBlocks.SUPPLY_CRATE.get().defaultBlockState();
            case CHAIR -> turned(ModBlocks.OFFICE_CHAIR.get().defaultBlockState(), facing);
            case MANNEQUIN_LOWER, MANNEQUIN_UPPER -> turned(Level0Blocks.MANNEQUIN.get().defaultBlockState(), facing)
                    .setValue(Level0MannequinBlock.HALF, code == MANNEQUIN_LOWER ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
            case DESK_PC -> turned(ModBlocks.OFFICE_DESK_PC.get().defaultBlockState(), facing);
            case DESK -> turned(ModBlocks.OFFICE_DESK.get().defaultBlockState(), facing);
            case RADIO -> turned(ModBlocks.CHECKPOINT_RADIO.get().defaultBlockState(), facing);
            case COOLER -> turned(ModBlocks.WATER_COOLER.get().defaultBlockState(), facing);
            case CABINET -> turned(ModBlocks.SUPPLY_CABINET.get().defaultBlockState(), facing);
            case FILING -> turned(ModBlocks.FILING_CABINET.get().defaultBlockState(), facing);
            case WARM_LAMP -> ModBlocks.SAFE_ROOM_LAMP.get().defaultBlockState();
            case EXIT_SIGN -> turned(Level0Blocks.EXIT_SIGN.get().defaultBlockState(), facing);
            default -> Blocks.AIR.defaultBlockState();
        };
    }

    private static BlockState turned(BlockState state, Direction facing) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }
}
