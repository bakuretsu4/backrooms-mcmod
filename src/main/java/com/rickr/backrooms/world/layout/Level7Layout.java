package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.WaterloggedFurnitureBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.ExitLocator;
import com.rickr.backrooms.world.Hash;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Level 7, "Thalassophobia": an endless dark sea under a concrete ceiling, in a thick fog of bone
 * dust. (Wikidot's version, with the fandom page's fog, shallows and drop-off.)
 *
 * <pre>
 *   y 213        the entrance room's roof, one fluorescent lamp
 *   y 209-212    the room: knee-deep water over carpet, a dry raised landing with the hatch
 *   y 205-207    concrete ceiling over the whole sea; under the hatch a shaft goes through it
 *   y 200-204    five blocks of air
 *   y 199        the sea's surface
 *   y 193        a shallow shelf of pale silt around each room (radius ~40)...
 *   y ~49        ...then a sheer drop to the deep floor: soggy carpet, bones, rusted scrap,
 *                tar mounds, giant ribcages, rock stacks rising to bare islands, and a fallen
 *                giant pipe lying from the foot of the cliff west to the exit
 * </pre>
 *
 * <p>Each 640x640 region has one entrance room (placed by {@link #ROOMS}) and, 150 blocks west of
 * it on the deep floor, the exit: a ring of stone pillars and giant pipes around a round stone
 * platform with a wooden door set into it (swim into the door: Level 9). The room is a safe room
 * with a checkpoint radio. Its hatch opens straight down onto the sea; beside it a rope hangs
 * through a hole in the landing so you can climb back up.
 */
public final class Level7Layout extends LevelLayout {
    // Heights.
    public static final int SURFACE_Y = 199;
    public static final int CEILING_BOTTOM_Y = 205;
    public static final int CEILING_TOP_Y = 207;
    public static final int ROOM_FLOOR_Y = 208;
    public static final int ROOM_ROOF_Y = 213;
    /** Top block of the shallow shelf around a room (six blocks of water over it). */
    private static final int SHELF_TOP_Y = 193;
    /** Average top of the deep floor (150 blocks below the surface). */
    public static final int DEEP_TOP_Y = 49;
    public static final int WORLD_HEIGHT = 256;

    /** One entrance room per region; at least 200 blocks from the region's edges, so its exit fits. */
    public static final int REGION = 640;
    static final ExitLocator ROOMS = new ExitLocator(REGION, 200, 7007);
    /** The exit lies this far west of its room. */
    public static final int EXIT_WEST = 150;

    // The room (relative to its centre R): walls at x = +-4, z = -6..+6; the hatch end is +z.
    private static final int ROOM_HALF_X = 4;
    private static final int ROOM_HALF_Z = 6;
    /** Rows z >= this are the dry raised landing. */
    private static final int LANDING_Z = 3;
    /** The two holes through the floor: x = -1 (the drop, under a hatch) and x = 0 (the rope), at z = +4. */
    private static final int HATCH_Z = 4;
    private static final int DROP_X = -1;
    private static final int ROPE_X = 0;

    private static final int SHELF_RADIUS = 40;

    // The exit ring (relative to its centre E).
    private static final int PLATFORM_RADIUS = 6;
    private static final int PILLAR_RING = 11;
    private static final int PIPE_RING = 17;
    private static final int RING_CLEAR = 24;

    public Level7Layout() {
        super(4, ROOMS);
    }

    @Override
    public int worldHeight() {
        return WORLD_HEIGHT;
    }

    @Override
    public int highestCeilingY() {
        return ROOM_ROOF_Y;
    }

    // ==========================================================================================
    // Where things are
    // ==========================================================================================

    /** Centre {x, z} of the entrance room of the region containing (x, z). */
    public static int[] room(long seed, int x, int z) {
        int rx = Math.floorDiv(x, REGION);
        int rz = Math.floorDiv(z, REGION);
        return new int[] {ROOMS.exitX(seed, rx, rz), ROOMS.exitZ(seed, rx, rz)};
    }

    /** True inside the entrance room's box (walls included): the safe room. */
    private static boolean inRoom(int dx, int dz) {
        return Math.abs(dx) <= ROOM_HALF_X && Math.abs(dz) <= ROOM_HALF_Z;
    }

    @Override
    public boolean isSafeRoom(long seed, int x, int z) {
        int[] r = room(seed, x, z);
        return inRoom(x - r[0], z - r[1]);
    }

    /** Arrivals look for the room nearest to where they were sent (see Travel.findSafeSpot). */
    @Override
    public int[] arrivalHint(long seed, int x, int z) {
        return room(seed, x, z);
    }

    /** You arrive at the bottom of the stairs, in the water at the near end of the room. */
    @Override
    public boolean isSafeArrival(long seed, int x, int z) {
        int[] r = room(seed, x, z);
        int dx = x - r[0];
        int dz = z - r[1];
        return Math.abs(dx) <= 1 && dz >= -4 && dz <= -2;
    }

    @Override
    public boolean isWalkable(long seed, int x, int z) {
        int[] r = room(seed, x, z);
        int dx = x - r[0];
        int dz = z - r[1];
        return Math.abs(dx) < ROOM_HALF_X && Math.abs(dz) < ROOM_HALF_Z;
    }

    @Override
    public int standY(long seed, int x, int z) {
        int[] r = room(seed, x, z);
        int dz = z - r[1];
        return ROOM_FLOOR_Y + (dz >= LANDING_Z ? 2 : 1);
    }

    /** The exit door of the region's ring (/backrooms locate). */
    @Override
    public int[] exitPosition(long seed, int x, int z) {
        int[] r = room(seed, x, z);
        return new int[] {r[0] - EXIT_WEST, r[1]};
    }

    // ==========================================================================================
    // Blocks
    // ==========================================================================================

    @Override
    public void fillColumn(long seed, int x, int z, Column out) {
        int[] r = room(seed, x, z);
        int dx = x - r[0];
        int dz = z - r[1];
        int ex = r[0] - EXIT_WEST;
        int ez = r[1];
        double roomDist = Math.sqrt((double) dx * dx + (double) dz * dz);
        double ringDist = Math.sqrt((double) (x - ex) * (x - ex) + (double) (z - ez) * (z - ez));

        // --- The sea floor and the water over it ---
        int top;
        BlockState topBlock;
        boolean shelf = roomDist < shelfRadius(seed, r, x, z);
        if (shelf) {
            top = SHELF_TOP_Y - (Hash.unit(seed, x >> 2, z >> 2, 7101) < 0.2 ? 1 : 0);
            topBlock = silt();
        } else {
            top = deepTop(seed, x, z, ringDist);
            topBlock = Hash.unit(seed, x >> 3, z >> 3, 7102) < 0.25 ? silt() : block(ModBlocks.L7_CARPET);
        }
        // The fallen pipe: lying on the deep floor from the foot of the shelf's cliff to the exit
        // ring, the one thing to follow down there (the exit is too far to see from the room).
        boolean trail = !shelf && Math.abs(dz) <= 1 && dx < 0 && x > ex + RING_CLEAR - 1;
        // A rock stack rising from the deep to a bare island (never near a room, the exit or the pipe).
        boolean clear = shelf || roomDist < 110 || ringDist < RING_CLEAR + 30 || (Math.abs(dz) <= 8 && dx < 0 && x > ex);
        int stackTop = clear ? Integer.MIN_VALUE : stackTop(seed, x, z);
        BlockState rock = block(ModBlocks.L7_ROCK);
        if (stackTop > top) {
            top = stackTop;
            topBlock = rock;
        }
        for (int y = 0; y < top - 2; y++) out.set(y, rock);
        BlockState under = shelf ? silt() : topBlock == rock ? rock : silt();
        out.set(top - 2, under);
        out.set(top - 1, under);
        out.set(top, topBlock);
        BlockState water = Blocks.WATER.defaultBlockState();
        for (int y = top + 1; y <= SURFACE_Y; y++) out.set(y, water);

        // Things lying on the floor (they replace water, which is fine: water fills around them).
        if (trail) {
            // Bedded on the floor under its middle row, so the three rows stay level with each other.
            int base = deepTop(seed, x, ez, Math.abs(x - ex)) + 1;
            BlockState pipe = ModBlocks.L7_PIPE.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
            for (int y = top + 1; y < base; y++) out.set(y, under);
            for (int y = base; y <= base + 2; y++) out.set(y, pipe);
        } else if (!shelf && stackTop <= DEEP_TOP_Y + 12 && ringDist >= RING_CLEAR) {
            deepClutter(seed, x, z, top, out);
        } else if (shelf && !inRoom(dx, dz)) {
            shelfClutter(seed, x, z, top, out);
        }
        if (ringDist < RING_CLEAR) {
            exitRing(x - ex, z - ez, ringDist, top, out);
        }

        // --- The concrete ceiling over everything, with the shaft under the room's hatches ---
        boolean shaft = dz == HATCH_Z && (dx == DROP_X || dx == ROPE_X);
        BlockState concrete = block(ModBlocks.L7_CONCRETE);
        if (!shaft) {
            for (int y = CEILING_BOTTOM_Y; y <= CEILING_TOP_Y; y++) out.set(y, concrete);
        } else if (dx == ROPE_X) {
            // The rope hangs from the landing down to just above the water.
            BlockState rope = block(ModBlocks.L7_ROPE);
            for (int y = SURFACE_Y + 1; y <= ROOM_FLOOR_Y; y++) out.set(y, rope);
        }

        // --- The entrance room, sitting on top of the ceiling ---
        if (inRoom(dx, dz)) {
            room(seed, dx, dz, shaft, out);
        }
    }

    private static double shelfRadius(long seed, int[] r, int x, int z) {
        double angle = Math.atan2(z - r[1], x - r[0]);
        double wobble = Math.sin(angle * 3 + Hash.unit(seed, r[0], r[1], 7103) * 6.28) * 5.0
                + Math.sin(angle * 7 + 1.3) * 2.0;
        return SHELF_RADIUS + wobble;
    }

    /** The deep floor: gentle swells, flattened around the exit ring, with tar mounds here and there. */
    private static int deepTop(long seed, int x, int z, double ringDist) {
        double swell = Math.sin(x * 0.031 + Hash.unit(seed, 0, 0, 7104) * 6.0) * 2.5
                + Math.sin(z * 0.027 + x * 0.011) * 2.5;
        int top = DEEP_TOP_Y + (int) Math.round(swell);
        if (ringDist < RING_CLEAR + 8) {
            double t = Mth.clamp((ringDist - RING_CLEAR) / 8.0, 0.0, 1.0);
            top = (int) Math.round(Mth.lerp(t, DEEP_TOP_Y, top));
        }
        return top;
    }

    /** Height of a rock stack at (x, z), or MIN_VALUE: one maybe per 96x96 cell, some reaching the surface. */
    private static int stackTop(long seed, int x, int z) {
        int cx = Math.floorDiv(x, 96);
        int cz = Math.floorDiv(z, 96);
        long h = Hash.of(seed, cx, cz, 7105);
        if (Hash.unit(h) > 0.45) {
            return Integer.MIN_VALUE;
        }
        int sx = cx * 96 + 20 + Hash.range(Hash.mix(h), 56);
        int sz = cz * 96 + 20 + Hash.range(Hash.mix(h ^ 1), 56);
        double radius = 4 + Hash.unit(Hash.mix(h ^ 2)) * 6;
        double d = Math.sqrt((double) (x - sx) * (x - sx) + (double) (z - sz) * (z - sz));
        // Ragged edge: the radius wobbles with the angle.
        double angle = Math.atan2(z - sz, x - sx);
        double edge = radius * (1.0 + 0.18 * Math.sin(angle * 5 + Hash.unit(h) * 9));
        if (d > edge) {
            return Integer.MIN_VALUE;
        }
        // Most break the surface as low bare islands; some stop short in the dark.
        boolean island = Hash.unit(Hash.mix(h ^ 3)) < 0.6;
        int peak = island ? SURFACE_Y + 2 : SURFACE_Y - 30 - Hash.range(Hash.mix(h ^ 4), 60);
        return peak - (int) Math.round(Math.max(0.0, d - edge * 0.5) * 0.6);
    }

    /** The deep floor: bones, rusted scrap, tar mounds and now and then a giant ribcage. */
    private static void deepClutter(long seed, int x, int z, int top, Column out) {
        // Tar mounds: soft black cones.
        int cx = Math.floorDiv(x, 40);
        int cz = Math.floorDiv(z, 40);
        long th = Hash.of(seed, cx, cz, 7106);
        if (Hash.unit(th) < 0.35) {
            int tx = cx * 40 + 6 + Hash.range(Hash.mix(th), 28);
            int tz = cz * 40 + 6 + Hash.range(Hash.mix(th ^ 1), 28);
            double radius = 5 + Hash.unit(Hash.mix(th ^ 2)) * 8;
            double d = Math.sqrt((double) (x - tx) * (x - tx) + (double) (z - tz) * (z - tz));
            if (d < radius) {
                int height = (int) Math.round((1.0 - d / radius) * (6 + Hash.unit(Hash.mix(th ^ 3)) * 14));
                BlockState tar = block(ModBlocks.L7_TAR);
                for (int y = top + 1; y <= top + height; y++) out.set(y, tar);
                return;
            }
        }
        // A giant fish skeleton: a spine along x with ribs arching over it.
        int gx = Math.floorDiv(x, 112);
        int gz = Math.floorDiv(z, 112);
        long gh = Hash.of(seed, gx, gz, 7107);
        if (Hash.unit(gh) < 0.5) {
            int sx = gx * 112 + 30 + Hash.range(Hash.mix(gh), 52);
            int sz = gz * 112 + 20 + Hash.range(Hash.mix(gh ^ 1), 72);
            int half = 14 + Hash.range(Hash.mix(gh ^ 2), 10);
            int rx = x - sx;
            int rz = z - sz;
            if (Math.abs(rx) <= half && Math.abs(rz) <= 9) {
                BlockState bone = block(ModBlocks.L7_BONE);
                if (rz == 0) {
                    out.set(top + 1, bone); // the spine
                }
                // Ribs every 3 blocks, tallest in the middle of the body.
                if (Math.floorMod(rx, 3) == 0 && Math.abs(rx) < half - 1) {
                    double ribRadius = 8.0 * Math.sqrt(1.0 - Math.pow((double) rx / half, 2));
                    if (Math.abs(rz) <= ribRadius) {
                        int y = top + 1 + (int) Math.round(Math.sqrt(Math.max(0.0, ribRadius * ribRadius - rz * rz)));
                        out.set(y, bone);
                        out.set(y - 1, bone);
                    }
                }
                if (Math.abs(rz) <= 1 && rx == -half) {
                    for (int y = top + 1; y <= top + 4; y++) out.set(y, bone); // the skull
                }
                return;
            }
        }
        double u = Hash.unit(seed, x, z, 7108);
        if (u < 0.025) {
            out.set(top + 1, block(ModBlocks.L7_BONE));
        } else if (u < 0.035) {
            out.set(top + 1, block(ModBlocks.L7_SCRAP));
        }
    }

    /** The shallows: a few bones and lumps of scrap on the silt. */
    private static void shelfClutter(long seed, int x, int z, int top, Column out) {
        double u = Hash.unit(seed, x, z, 7109);
        if (u < 0.008) {
            out.set(top + 1, block(ModBlocks.L7_SCRAP));
        } else if (u < 0.014) {
            out.set(top + 1, block(ModBlocks.L7_BONE));
        }
    }

    /** The exit: a round stone platform with a door in it, inside a ring of pillars and giant pipes. */
    private static void exitRing(int rx, int rz, double d, int top, Column out) {
        BlockState stone = block(ModBlocks.L7_PILLAR);
        if (d <= PLATFORM_RADIUS) {
            out.set(top + 1, block(ModBlocks.L7_PLATFORM));
            out.set(top + 2, block(ModBlocks.L7_PLATFORM));
            if (rx == 0 && rz == 0) {
                out.set(top + 3, block(ModBlocks.L7_EXIT_DOOR));
            }
            return;
        }
        // Eight pillars, 2x2, standing in a ring.
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            int px = (int) Math.round(Math.cos(a) * PILLAR_RING);
            int pz = (int) Math.round(Math.sin(a) * PILLAR_RING);
            if ((rx == px || rx == px + 1) && (rz == pz || rz == pz + 1)) {
                for (int y = top + 1; y <= top + 30; y++) out.set(y, stone);
                return;
            }
        }
        // Four giant pipes, 3x3, rising into the dark.
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.PI / 4;
            int px = (int) Math.round(Math.cos(a) * PIPE_RING);
            int pz = (int) Math.round(Math.sin(a) * PIPE_RING);
            if (Math.abs(rx - px) <= 1 && Math.abs(rz - pz) <= 1) {
                BlockState pipe = block(ModBlocks.L7_PIPE);
                for (int y = top + 1; y <= top + 60; y++) out.set(y, pipe);
                return;
            }
        }
        // Bones heaped around the platform: something lives here.
        if (d < PILLAR_RING + 3 && ((rx * 31 + rz * 17) & 7) == 0) {
            out.set(top + 1, block(ModBlocks.L7_BONE));
        }
    }

    /** One column of the entrance room (dx, dz from its centre). */
    private static void room(long seed, int dx, int dz, boolean shaft, Column out) {
        BlockState metal = block(ModBlocks.L7_RUST_METAL);
        boolean wall = Math.abs(dx) == ROOM_HALF_X || Math.abs(dz) == ROOM_HALF_Z;
        if (wall) {
            for (int y = ROOM_FLOOR_Y; y <= ROOM_ROOF_Y; y++) out.set(y, metal);
            return;
        }
        // The roof, with the room's one fluorescent lamp.
        out.set(ROOM_ROOF_Y, dx == 0 && dz == -1 ? block(ModBlocks.FLUORESCENT_LIGHT) : metal);
        BlockState water = Blocks.WATER.defaultBlockState();
        if (dz >= LANDING_Z) {
            // The dry landing, one step up, with the hatches in it.
            if (shaft && dx == ROPE_X) {
                // The rope's hole has no hatch: the rope runs right up to the landing's surface, so
                // climbing it (hold jump) brings your feet level with the floor and you step off.
                out.set(ROOM_FLOOR_Y + 1, block(ModBlocks.L7_ROPE));
            } else if (shaft) {
                out.set(ROOM_FLOOR_Y + 1, ModBlocks.L7_HATCH.get().defaultBlockState()
                        .setValue(TrapDoorBlock.FACING, Direction.NORTH).setValue(TrapDoorBlock.HALF, Half.TOP)
                        .setValue(TrapDoorBlock.OPEN, false));
            } else {
                out.set(ROOM_FLOOR_Y, metal);
                out.set(ROOM_FLOOR_Y + 1, block(ModBlocks.L7_GRATING));
                // The radio (a checkpoint) and a supply crate against the end wall.
                if (dz == ROOM_HALF_Z - 1 && dx == 2) {
                    out.set(ROOM_FLOOR_Y + 2, ModBlocks.CHECKPOINT_RADIO.get().defaultBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
                } else if (dz == ROOM_HALF_Z - 1 && dx == -3) {
                    out.set(ROOM_FLOOR_Y + 2, block(ModBlocks.SUPPLY_CRATE));
                }
            }
            return;
        }
        // The flooded part: carpet under knee-deep water.
        out.set(ROOM_FLOOR_Y, block(ModBlocks.L7_CARPET));
        BlockState feet = water;
        if (dz == LANDING_Z - 1 && dx >= -1 && dx <= 0) {
            // A step up to the landing (stairs work under water).
            feet = ModBlocks.CONCRETE_STAIRS.get().defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH).setValue(BlockStateProperties.WATERLOGGED, true);
        } else if (dx == -3 && dz >= -3 && dz <= 1) {
            // The bookcase against the left wall (two high).
            out.set(ROOM_FLOOR_Y + 2, block(ModBlocks.L7_BOOKCASE));
            feet = block(ModBlocks.L7_BOOKCASE);
        } else if (dx == 0 && dz == 0) {
            feet = furniture(ModBlocks.L7_COFFEE_TABLE.get().defaultBlockState(), Direction.NORTH);
        } else if (dx == 1 && dz == 1) {
            feet = furniture(ModBlocks.L7_ARMCHAIR.get().defaultBlockState(), Direction.NORTH);
        } else if (dz == -ROOM_HALF_Z + 1 && dx >= -1 && dx <= 1) {
            // The way you came in: stairs going up into the wall, blocked with rubble.
            feet = ModBlocks.CONCRETE_STAIRS.get().defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.NORTH).setValue(BlockStateProperties.WATERLOGGED, true);
            out.set(ROOM_FLOOR_Y + 2, block(ModBlocks.L7_CONCRETE));
            out.set(ROOM_FLOOR_Y + 3, block(ModBlocks.L7_CONCRETE));
            out.set(ROOM_FLOOR_Y + 4, block(ModBlocks.L7_CONCRETE));
        }
        out.set(ROOM_FLOOR_Y + 1, feet);
    }

    private static BlockState furniture(BlockState state, Direction facing) {
        return state.setValue(WaterloggedFurnitureBlock.FACING, facing).setValue(WaterloggedFurnitureBlock.WATERLOGGED, true);
    }

    private static BlockState silt() {
        return block(ModBlocks.L7_SILT);
    }

    private static BlockState block(net.neoforged.neoforge.registries.DeferredBlock<?> block) {
        return block.get().defaultBlockState();
    }
}
