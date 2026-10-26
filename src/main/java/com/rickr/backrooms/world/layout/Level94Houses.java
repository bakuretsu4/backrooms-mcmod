package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.FurnitureBlock;
import com.rickr.backrooms.block.RugBlock;
import com.rickr.backrooms.block.WallDecalBlock;
import com.rickr.backrooms.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The toy houses of Level 94, and the lone fragments of houses on the hills.
 *
 * <p>Every house looks the same from outside, like the flat painted houses of a model railway:
 * narrow, two storeys, the gable end facing the front, cream siding over a band of oversized
 * bricks, white corner boards, a door between two dark windows and one more window up in the gable.
 *
 * <p>A house is drawn in its own "local" grid: u runs along the front (0 .. w-1), v runs from the
 * front wall (0) to the back wall (d-1). {@link House#front} is the way the front looks out.
 *
 * <p>Heights, from the floor block at g: brick band g+1, siding g+2 and g+3, a white band at g+4
 * (the upper floor), siding g+5 to g+7, the wall plate at g+8, then the roof, whose eaves start at
 * g+8 and climb one block per column to the ridge.
 */
final class Level94Houses {
    /** A full house, two storeys, stairs inside. */
    static final int BIG = 0;
    /** A town house: narrower, one room downstairs (the upper floor is only for show). */
    static final int TINY = 1;
    /** An exit: looks like BIG, but behind the door there is nothing but black. */
    static final int DARK = 2;
    /** A lone front wall with a chair and a lamp beside it. */
    static final int FRAGMENT_WALL = 3;
    /** A single room with three walls and no roof. */
    static final int FRAGMENT_ROOM = 4;

    private Level94Houses() {}

    /** One house standing at a fixed spot. Mutable so the town can reuse a single one per thread. */
    static final class House {
        int kind;
        /** World position of local (0, 0): the front-left corner. */
        int x0;
        int z0;
        Direction front;
        Direction uAxis;
        Direction inward;
        int w;
        int d;
        /** Y of the floor block (the ground the house stands on). */
        int g;
        boolean fence;
        boolean redRoof;

        House set(int kind, int x0, int z0, Direction front, int w, int d, int g, boolean fence, boolean redRoof) {
            this.kind = kind;
            this.x0 = x0;
            this.z0 = z0;
            this.front = front;
            this.uAxis = front.getClockWise();
            this.inward = front.getOpposite();
            this.w = w;
            this.d = d;
            this.g = g;
            this.fence = fence;
            this.redRoof = redRoof;
            return this;
        }

        /** Places a w x d house (along / across the front) with its middle at (cx, cz). */
        House centred(int kind, int cx, int cz, Direction front, int w, int d, int g, boolean fence, boolean redRoof) {
            set(kind, 0, 0, front, w, d, g, fence, redRoof);
            int mu = (w - 1) / 2;
            int mv = (d - 1) / 2;
            x0 = cx - uAxis.getStepX() * mu - inward.getStepX() * mv;
            z0 = cz - uAxis.getStepZ() * mu - inward.getStepZ() * mv;
            return this;
        }

        /** Places a house filling the world rectangle [rx0, rx1] x [rz0, rz1] with its front facing {@code front}. */
        House inRect(int kind, int rx0, int rz0, int rx1, int rz1, Direction front, int g, boolean redRoof) {
            Direction ua = front.getClockWise();
            Direction in = front.getOpposite();
            int sx = ua.getStepX() + in.getStepX();
            int sz = ua.getStepZ() + in.getStepZ();
            int width = ua.getAxis() == Direction.Axis.X ? rx1 - rx0 + 1 : rz1 - rz0 + 1;
            int depth = in.getAxis() == Direction.Axis.X ? rx1 - rx0 + 1 : rz1 - rz0 + 1;
            return set(kind, sx > 0 ? rx0 : rx1, sz > 0 ? rz0 : rz1, front, width, depth, g, false, redRoof);
        }

        int u(int x, int z) {
            return (x - x0) * uAxis.getStepX() + (z - z0) * uAxis.getStepZ();
        }

        int v(int x, int z) {
            return (x - x0) * inward.getStepX() + (z - z0) * inward.getStepZ();
        }

        int worldX(int u, int v) {
            return x0 + uAxis.getStepX() * u + inward.getStepX() * v;
        }

        int worldZ(int u, int v) {
            return z0 + uAxis.getStepZ() * u + inward.getStepZ() * v;
        }

        int mid() {
            return (w - 1) / 2;
        }

        boolean isFragment() {
            return kind == FRAGMENT_WALL || kind == FRAGMENT_ROOM;
        }

        /** Local rectangle of everything this house draws (roof overhang, fence) or flattens: {u0, v0, u1, v1}. */
        int[] reach(int extra) {
            if (isFragment()) {
                return new int[] {-extra, -extra, w - 1 + extra, d - 1 + extra};
            }
            return fence ? new int[] {-2 - extra, -3 - extra, w + 1 + extra, d + 1 + extra}
                    : new int[] {-1 - extra, -1 - extra, w + extra, d + extra};
        }

        /** The same rectangle in world coordinates: {minX, minZ, maxX, maxZ}. */
        int[] worldReach(int extra) {
            int[] r = reach(extra);
            int ax = worldX(r[0], r[1]);
            int az = worldZ(r[0], r[1]);
            int bx = worldX(r[2], r[3]);
            int bz = worldZ(r[2], r[3]);
            return new int[] {Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz)};
        }

        /** True inside the walls of an intact house (a safe room). Fragments and dark houses are not. */
        boolean isSafe(int x, int z) {
            if (kind != BIG && kind != TINY) {
                return false;
            }
            int u = u(x, z);
            int v = v(x, z);
            return u >= 0 && u < w && v >= 0 && v < d;
        }

        boolean fenceAt(int u, int v) {
            if (!fence) {
                return false;
            }
            boolean onSide = (u == -2 || u == w + 1) && v >= -3 && v <= d + 1;
            boolean onEnd = (v == -3 || v == d + 1) && u >= -2 && u <= w + 1;
            if (!onSide && !onEnd) {
                return false;
            }
            // A gap in front of the door for the garden path.
            return !(v == -3 && Math.abs(u - mid()) <= 1);
        }
    }

    // ==========================================================================================
    // Blocks
    // ==========================================================================================

    private static BlockState facing(BlockState state, Direction d) {
        return state.setValue(WallDecalBlock.FACING, d);
    }

    private static BlockState wall(Direction out) {
        return facing(ModBlocks.L94_HOUSE_WALL.get().defaultBlockState(), out);
    }

    private static BlockState window(Direction out) {
        return facing(ModBlocks.L94_WINDOW.get().defaultBlockState(), out);
    }

    private static BlockState brickBand(Direction out) {
        return facing(ModBlocks.L94_BRICK_BAND.get().defaultBlockState(), out);
    }

    private static BlockState trim() {
        return ModBlocks.L94_TRIM.get().defaultBlockState();
    }

    private static BlockState furniture(BlockState state, Direction d) {
        return state.setValue(FurnitureBlock.FACING, d);
    }

    // ==========================================================================================
    // Drawing one column
    // ==========================================================================================

    /** Writes the house blocks of column (x, z). The ground (pad) up to y = g is already there. */
    static void column(House h, int x, int z, LevelLayout.Column out) {
        int u = h.u(x, z);
        int v = h.v(x, z);
        if (h.fenceAt(u, v)) {
            out.set(h.g + 1, picket(h, u, v));
        }
        if (h.kind == FRAGMENT_WALL) {
            fragmentWall(h, u, v, out);
            return;
        }
        if (h.kind == FRAGMENT_ROOM) {
            fragmentRoom(h, u, v, out);
            return;
        }
        roof(h, u, v, out);
        if (u < 0 || u >= h.w || v < 0 || v >= h.d) {
            return;
        }
        boolean wallU = u == 0 || u == h.w - 1;
        boolean wallV = v == 0 || v == h.d - 1;
        if (wallU || wallV) {
            houseWall(h, u, v, wallU && wallV, out);
        } else {
            interior(h, u, v, out);
        }
    }

    /** Roof stairs from the eaves up to a slab on the ridge; the roof overhangs the walls by one. */
    private static void roof(House h, int u, int v, LevelLayout.Column out) {
        if (u < -1 || u > h.w || v < -1 || v > h.d) {
            return;
        }
        int layer = Math.min(u + 1, h.w - u);
        int y = h.g + 8 + layer;
        int mid = h.mid();
        if (u == mid) {
            BlockState slab = (h.redRoof ? ModBlocks.L94_ROOF_RED_SLAB : ModBlocks.L94_ROOF_BROWN_SLAB).get().defaultBlockState();
            out.set(y, slab.setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        } else {
            BlockState stairs = (h.redRoof ? ModBlocks.L94_ROOF_RED_STAIRS : ModBlocks.L94_ROOF_BROWN_STAIRS).get().defaultBlockState();
            // Stairs rise toward their FACING side, so each half of the roof faces the ridge.
            out.set(y, stairs.setValue(StairBlock.FACING, u < mid ? h.uAxis : h.uAxis.getOpposite()));
        }
    }

    /** Which way the wall at local (u, v) looks out of the house. */
    private static Direction outward(House h, int u, int v) {
        if (v == 0) return h.front;
        if (v == h.d - 1) return h.inward;
        return u == 0 ? h.uAxis.getOpposite() : h.uAxis;
    }

    /** True where a wall column has windows (front: either side of the door; back and sides: the middle). */
    private static boolean windowColumn(House h, int u, int v) {
        if (v == 0) return u == 1 || u == h.w - 2;
        if (v == h.d - 1) return u == h.mid();
        return v == h.d / 2;
    }

    private static void houseWall(House h, int u, int v, boolean corner, LevelLayout.Column out) {
        int g = h.g;
        if (corner) {
            out.set(g + 1, ModBlocks.L94_BRICK.get().defaultBlockState());
            for (int y = g + 2; y <= g + 8; y++) out.set(y, trim());
        } else {
            Direction dir = outward(h, u, v);
            boolean door = v == 0 && u == h.mid();
            boolean windows = windowColumn(h, u, v);
            if (door) {
                BlockState state = ModBlocks.L94_HOUSE_DOOR.get().defaultBlockState()
                        .setValue(DoorBlock.FACING, h.inward)
                        .setValue(DoorBlock.OPEN, false);
                out.set(g + 1, state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                out.set(g + 2, state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            } else {
                out.set(g + 1, brickBand(dir));
                out.set(g + 2, windows ? window(dir) : wall(dir));
            }
            out.set(g + 3, wall(dir));
            out.set(g + 4, trim());
            out.set(g + 5, wall(dir));
            out.set(g + 6, windows ? window(dir) : wall(dir));
            out.set(g + 7, wall(dir));
            out.set(g + 8, trim());
        }
        // The gable: the triangle of wall under the roof at the front and back.
        if (v == 0 || v == h.d - 1) {
            int layer = Math.min(u + 1, h.w - u);
            BlockState siding = ModBlocks.L94_SIDING.get().defaultBlockState();
            for (int y = g + 9; y < g + 8 + layer; y++) {
                out.set(y, siding);
            }
            if (u == h.mid()) {
                out.set(g + 10, window(v == 0 ? h.front : h.inward));
            }
        }
    }

    private static void interior(House h, int u, int v, LevelLayout.Column out) {
        int g = h.g;
        out.set(g + 8, trim()); // ceiling under the attic
        if (h.kind == DARK) {
            // Nothing inside but the dark. Stepping into it is the way back to Level 9.
            out.set(g, ModBlocks.L94_FLOOR.get().defaultBlockState());
            BlockState dark = ModBlocks.L94_DARK_VOID.get().defaultBlockState();
            for (int y = g + 1; y <= g + 7; y++) out.set(y, dark);
            return;
        }
        boolean big = h.kind == BIG;
        // Rug: 3x3, in front of the armchair.
        int rugU = 2;
        int rugV = big ? 5 : 3;
        if (Math.abs(u - rugU) <= 1 && Math.abs(v - rugV) <= 1) {
            int ox = h.worldX(u, v) - h.worldX(rugU, rugV);
            int oz = h.worldZ(u, v) - h.worldZ(rugU, rugV);
            out.set(g, ModBlocks.L94_RUG.get().defaultBlockState().setValue(RugBlock.PART, RugBlock.part(ox, oz)));
        } else {
            out.set(g, ModBlocks.L94_FLOOR.get().defaultBlockState());
        }

        int back = h.d - 2;
        if (big) {
            // Straight stairs along the right-hand wall: steps at v = 2..5, the last one in the
            // upper floor's own layer (g + 4), with the floor left open above the lower steps.
            int stairU = h.w - 2;
            boolean stairs = u == stairU && v >= 2 && v <= 5;
            if (stairs) {
                out.set(g + v - 1, ModBlocks.L94_STAIRS.get().defaultBlockState().setValue(StairBlock.FACING, h.inward));
            } else {
                out.set(g + 4, ModBlocks.L94_FLOOR.get().defaultBlockState());
            }
        } else {
            out.set(g + 4, trim()); // a town house has one room; the upper storey is only for show
        }
        // Armchair at the back looking at the TV near the front, the odd lamp beside the chair.
        if (u == 2 && v == back) {
            out.set(g + 1, furniture(ModBlocks.L94_ARMCHAIR.get().defaultBlockState(), h.front));
        } else if (u == (big ? 1 : 3) && v == back) {
            out.set(g + 1, furniture(ModBlocks.L94_LAMP.get().defaultBlockState(), h.front));
        } else if (u == 2 && v == (big ? 3 : 2)) {
            out.set(g + 1, furniture(ModBlocks.L94_TV.get().defaultBlockState(), h.inward));
        }
    }

    /** A lone front wall: brick band, cream siding, one window. A chair and a lamp stand on its papered side. */
    private static void fragmentWall(House h, int u, int v, LevelLayout.Column out) {
        int g = h.g;
        if (v == 0 && u >= 0 && u < h.w) {
            if (u == 0 || u == h.w - 1) {
                out.set(g + 1, ModBlocks.L94_BRICK.get().defaultBlockState());
                for (int y = g + 2; y <= g + 8; y++) out.set(y, trim());
                return;
            }
            Direction dir = h.front;
            out.set(g + 1, brickBand(dir));
            out.set(g + 2, u == h.mid() ? window(dir) : wall(dir));
            out.set(g + 3, wall(dir));
            out.set(g + 4, trim());
            for (int y = g + 5; y <= g + 7; y++) out.set(y, wall(dir));
            out.set(g + 8, trim());
        } else if (v == 1 && u == 2) {
            out.set(g + 1, furniture(ModBlocks.L94_ARMCHAIR.get().defaultBlockState(), h.inward));
        } else if (v == 1 && u == 1) {
            out.set(g + 1, furniture(ModBlocks.L94_LAMP.get().defaultBlockState(), h.inward));
        }
    }

    /** A single papered room, open at the front and to the sky, furnished like any house. */
    private static void fragmentRoom(House h, int u, int v, LevelLayout.Column out) {
        if (u < 0 || u >= h.w || v < 0 || v >= h.d) {
            return;
        }
        int g = h.g;
        boolean wallU = u == 0 || u == h.w - 1;
        boolean wallV = v == h.d - 1;
        if (wallU || wallV) {
            if (wallU && wallV) {
                out.set(g + 1, ModBlocks.L94_BRICK.get().defaultBlockState());
                out.set(g + 2, trim());
                out.set(g + 3, trim());
            } else {
                Direction dir = wallV ? h.inward : (u == 0 ? h.uAxis.getOpposite() : h.uAxis);
                boolean win = wallV ? u == h.mid() : v == h.d / 2;
                out.set(g + 1, brickBand(dir));
                out.set(g + 2, win ? window(dir) : wall(dir));
                out.set(g + 3, wall(dir));
            }
            out.set(g + 4, trim());
            return;
        }
        int mid = h.mid();
        if (Math.abs(u - mid) <= 1 && Math.abs(v - 3) <= 1) {
            int ox = h.worldX(u, v) - h.worldX(mid, 3);
            int oz = h.worldZ(u, v) - h.worldZ(mid, 3);
            out.set(g, ModBlocks.L94_RUG.get().defaultBlockState().setValue(RugBlock.PART, RugBlock.part(ox, oz)));
        } else {
            out.set(g, ModBlocks.L94_FLOOR.get().defaultBlockState());
        }
        int back = h.d - 2;
        if (u == mid && v == back) {
            out.set(g + 1, furniture(ModBlocks.L94_ARMCHAIR.get().defaultBlockState(), h.front));
        } else if (u == 1 && v == back) {
            out.set(g + 1, furniture(ModBlocks.L94_LAMP.get().defaultBlockState(), h.front));
        } else if (u == mid && v == 1) {
            out.set(g + 1, furniture(ModBlocks.L94_TV.get().defaultBlockState(), h.inward));
        }
    }

    /** A white picket joined to the fence pieces beside it. */
    private static BlockState picket(House h, int u, int v) {
        BlockState state = ModBlocks.PICKET_FENCE.get().defaultBlockState();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            int nu = u + dir.getStepX() * h.uAxis.getStepX() + dir.getStepZ() * h.uAxis.getStepZ();
            int nv = v + dir.getStepX() * h.inward.getStepX() + dir.getStepZ() * h.inward.getStepZ();
            state = state.setValue(side(dir), h.fenceAt(nu, nv));
        }
        return state;
    }

    private static BooleanProperty side(Direction d) {
        return switch (d) {
            case NORTH -> FenceBlock.NORTH;
            case SOUTH -> FenceBlock.SOUTH;
            case EAST -> FenceBlock.EAST;
            default -> FenceBlock.WEST;
        };
    }
}
