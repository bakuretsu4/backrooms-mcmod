package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.Level94Vehicles;
import com.rickr.backrooms.block.SignKind;
import com.rickr.backrooms.block.StreetSignBlock;
import com.rickr.backrooms.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jetbrains.annotations.Nullable;

/**
 * Everything that stands on the hills and beside the roads of Level 94, apart from the town:
 * houses (drawn by {@link Level94Houses}), lollipop trees, water towers, parked cars, siren poles
 * and road signs. Each is one {@link Thing} with a footprint; the plan decides where they go.
 */
final class Level94Builds {
    static final int HOUSE = 0;
    static final int TREE = 1;
    static final int TOWER = 2;
    static final int CAR = 3;
    static final int SIREN = 4;
    static final int SIGN = 5;

    /** Water tower: legs this tall, then the tank. */
    private static final int LEG_HEIGHT = 12;
    private static final int TANK_HEIGHT = 5;

    private Level94Builds() {}

    /** One placed thing. Never changes once the plan that made it is finished. */
    static final class Thing {
        final int kind;
        /** Lower wins when two things want the same ground (dark houses first, then hilltops, then roadsides). */
        final int priority;
        /** Tie-breaker between equal priorities: a stable hash. */
        final long key;
        /** Anchor column (centre of a tree, tower or vehicle; the pole of a siren or sign). */
        final int x;
        final int z;
        /** Y of the ground block the thing stands on. */
        int ground;
        Direction facing = Direction.NORTH;
        /** Tree: trunk height. Car: vehicle kind. Sign: sign kind. */
        int a;
        /** Tree: crown radius in tenths of a block. */
        int b;
        @Nullable
        Level94Houses.House house;
        /** What the thing draws: {minX, minZ, maxX, maxZ}. */
        int[] box;
        /** The ground it flattens to {@link #padHeight} (above the plain), or null. */
        @Nullable
        int[] pad;
        int padHeight;
        /** True: the pad may raise and lower the land around it. False: only lower (roadside pads at y40). */
        boolean padRaises;
        /** Garden path from the door to the road: {x0, z0, x1, z1, ...}, or null. */
        @Nullable
        float[] path;

        Thing(int kind, int priority, long key, int x, int z) {
            this.kind = kind;
            this.priority = priority;
            this.key = key;
            this.x = x;
            this.z = z;
        }

        /** True if this thing goes before {@code o} when they compete for ground. */
        boolean beats(Thing o) {
            return priority != o.priority ? priority < o.priority : key < o.key;
        }

        /** The rectangle this thing claims: what it draws plus what it flattens. */
        int[] claim() {
            if (pad == null) return box;
            return new int[] {Math.min(box[0], pad[0]), Math.min(box[1], pad[1]), Math.max(box[2], pad[2]), Math.max(box[3], pad[3])};
        }

        boolean overlaps(Thing o, int margin) {
            int[] a1 = claim();
            int[] b1 = o.claim();
            return a1[0] - margin <= b1[2] && b1[0] - margin <= a1[2] && a1[1] - margin <= b1[3] && b1[1] - margin <= a1[3];
        }

        boolean inBox(int px, int pz) {
            return px >= box[0] && px <= box[2] && pz >= box[1] && pz <= box[3];
        }
    }

    static int[] square(int x, int z, int r) {
        return new int[] {x - r, z - r, x + r, z + r};
    }

    // ==========================================================================================
    // Drawing
    // ==========================================================================================

    /** Writes the blocks of {@code t} in column (x, z), which lies inside its box. */
    static void column(Thing t, int x, int z, LevelLayout.Column out) {
        switch (t.kind) {
            case HOUSE -> Level94Houses.column(t.house, x, z, out);
            case TREE -> tree(t, x, z, out);
            case TOWER -> tower(t, x, z, out);
            case CAR -> vehicle(t, x, z, out);
            case SIREN -> Level94Town.siren(t.ground, t.facing, out);
            case SIGN -> {
                out.set(t.ground + 1, ModBlocks.SIGN_POST.get().defaultBlockState());
                out.set(t.ground + 2, ModBlocks.STREET_SIGN.get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, t.facing)
                        .setValue(StreetSignBlock.KIND, SignKind.values()[t.a]));
            }
            default -> {
            }
        }
    }

    /** A cartoon lollipop tree: a thin trunk and a perfectly round ball of bright leaves. */
    private static void tree(Thing t, int x, int z, LevelLayout.Column out) {
        int trunk = t.a;
        double r = t.b / 10.0;
        double cy = t.ground + trunk + r - 0.5;
        double dx = x - t.x;
        double dz = z - t.z;
        double flat = dx * dx + dz * dz;
        if (flat <= r * r) {
            double half = Math.sqrt(r * r - flat);
            BlockState leaves = ModBlocks.L94_LEAVES.get().defaultBlockState();
            for (int y = (int) Math.ceil(cy - half - 0.5); y <= (int) Math.floor(cy + half + 0.5); y++) {
                double dy = y - cy;
                if (flat + dy * dy <= r * r) out.set(y, leaves);
            }
        }
        if (x == t.x && z == t.z) {
            BlockState wood = ModBlocks.L94_TRUNK.get().defaultBlockState();
            for (int y = t.ground + 1; y <= t.ground + trunk; y++) out.set(y, wood);
        }
    }

    /**
     * A water tower: four thin legs with two bands of cross-bracing, a round grey tank and a
     * cone roof with a little spike. Centred on its anchor, 7 x 7 at the tank.
     */
    private static void tower(Thing t, int x, int z, LevelLayout.Column out) {
        int dx = x - t.x;
        int dz = z - t.z;
        int g = t.ground;
        int adx = Math.abs(dx);
        int adz = Math.abs(dz);
        if (adx == 2 && adz == 2) {
            BlockState leg = ModBlocks.L94_TOWER_LEG.get().defaultBlockState();
            for (int y = g + 1; y <= g + LEG_HEIGHT; y++) out.set(y, leg);
        } else if (Math.max(adx, adz) == 2) {
            // Bracing between the legs: thin lattice panes joined along the square.
            boolean alongX = adz == 2;
            BlockState brace = ModBlocks.L94_TOWER_BRACE.get().defaultBlockState()
                    .setValue(IronBarsBlock.EAST, alongX)
                    .setValue(IronBarsBlock.WEST, alongX)
                    .setValue(IronBarsBlock.NORTH, !alongX)
                    .setValue(IronBarsBlock.SOUTH, !alongX);
            out.set(g + 5, brace);
            out.set(g + 9, brace);
        }
        int r2 = dx * dx + dz * dz;
        int tank0 = g + LEG_HEIGHT + 1;
        if (r2 <= 11) {
            BlockState tank = ModBlocks.L94_TANK.get().defaultBlockState();
            for (int y = tank0; y < tank0 + TANK_HEIGHT; y++) out.set(y, tank);
            int roof = tank0 + TANK_HEIGHT;
            BlockState full = ModBlocks.L94_TANK_ROOF.get().defaultBlockState();
            BlockState slab = ModBlocks.L94_TANK_ROOF_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            if (r2 <= 4) {
                out.set(roof, full);
                out.set(roof + 1, r2 <= 1 ? full : slab);
                if (r2 == 0) {
                    out.set(roof + 2, ModBlocks.L94_TOWER_LEG.get().defaultBlockState());
                }
            } else {
                out.set(roof, slab);
            }
        }
    }

    /** One cell layer of a parked car or milk van, from the Props slice's vehicle shapes. */
    private static void vehicle(Thing t, int x, int z, LevelLayout.Column out) {
        Level94Vehicles.Kind kind = Level94Vehicles.Kind.values()[t.a];
        for (int dy = 0; dy <= 2; dy++) {
            BlockState part = Level94Vehicles.cell(kind, t.facing, x - t.x, dy, z - t.z);
            if (part != null) {
                out.set(t.ground + 1 + dy, part);
            }
        }
    }
}
