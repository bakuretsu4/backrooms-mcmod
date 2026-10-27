package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.AxisShapeBlock;
import com.rickr.backrooms.block.PicketFenceBlock;
import com.rickr.backrooms.block.SurveyNoteBlock;
import com.rickr.backrooms.block.WallMountBlock;
import com.rickr.backrooms.registry.Level39Blocks;
import com.rickr.backrooms.world.Hash;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * One column of a Level 39 town, from the bedrock up: rock, the flooded tunnels
 * ({@link Level39Tunnels}), soil, the ground (yard grass, dirt paths, gravel, ruts and puddles,
 * the holes), then whatever stands on it: a house ({@link Level39Houses}), a picket fence, a
 * utility pole, a bare or dark tree, bushes and weeds, a lattice telephone tower, a park or an
 * abandoned M.E.G. survey camp. Everything is read from the column's {@link Level39Tile}.
 */
final class Level39Town {
    /** Soil from here up to the ground; grey rock below it. */
    static final int SOIL_FROM_Y = 63;
    /** Poles are this many blocks of wood, with the crossarm block on top. */
    static final int POLE_HEIGHT = 10;
    /** The crossarm block of every utility pole (the wires hang from its insulators). */
    static final int CROSSARM_Y = Level39Layout.GROUND_Y + 1 + POLE_HEIGHT;
    /** The transformer cans hang this high beside a pole. */
    private static final int TRANSFORMER_Y = CROSSARM_Y - 3;

    private Level39Town() {}

    /** Underground layers, seen in the walls of the holes: soil, clay, stratified rock, rock. */
    static final int CLAY_FROM_Y = 59;
    static final int LAYERED_FROM_Y = 52;

    static void fillColumn(long seed, Level39Tile tile, int x, int z, LevelLayout.Column out) {
        int i = tile.index(x, z);
        byte surf = tile.surf[i];
        int h = tile.half[i];
        // The top full block of the ground; a half block on top of it when h is odd.
        int top = Level39Layout.GROUND_Y + (h >> 1);

        // ---- Underground ------------------------------------------------------------------------
        out.set(Level39Layout.BEDROCK_Y, Blocks.BEDROCK.defaultBlockState());
        BlockState rock = Level39Blocks.ROCK.get().defaultBlockState();
        for (int y = Level39Layout.BEDROCK_Y + 1; y < LAYERED_FROM_Y; y++) {
            out.set(y, rock);
        }
        BlockState layered = Level39Blocks.ROCK_LAYERED.get().defaultBlockState();
        BlockState clay = Level39Blocks.CLAY.get().defaultBlockState();
        BlockState soil = Level39Blocks.SOIL.get().defaultBlockState();
        BlockState rubble = Level39Blocks.RUBBLE.get().defaultBlockState();
        for (int y = LAYERED_FROM_Y; y < top; y++) {
            BlockState s = y < CLAY_FROM_Y ? layered : y < SOIL_FROM_Y ? clay : soil;
            // Pockets of broken rock in the clay and soil.
            if (y >= CLAY_FROM_Y - 3 && y < Level39Layout.GROUND_Y - 1 && Hash.unit(seed, x, z, 3990 + y) < 0.06) {
                s = rubble;
            }
            out.set(y, s);
        }
        int tunnelTop = Level39Tunnels.fillColumn(tile, x, z, out);
        if (tile.holes.length > 0 && holeShaft(tile, x, z, tunnelTop, out)) {
            return;
        }

        // ---- Ground -----------------------------------------------------------------------------
        int feet = top + 1;
        switch (surf) {
            case Level39Tile.HATCH -> {
                Level39Tunnels.shaftColumn(tile, x, z, out);
                return;
            }
            case Level39Tile.PUDDLE -> {
                // A waterlogged half block of mud: the water stands almost level with the ground.
                out.set(top - 1, Level39Blocks.PATH_MUD.get().defaultBlockState());
                out.set(top, Level39Blocks.MUD_SLAB.get().defaultBlockState().setValue(SlabBlock.WATERLOGGED, true));
            }
            // Dry cart ruts are only churned mud, level with the path: no dips, no slots.
            case Level39Tile.RUT -> out.set(top, Level39Blocks.PATH_MUD.get().defaultBlockState());
            default -> {
                out.set(top, surface(surf));
                if ((h & 1) != 0) {
                    out.set(top + 1, (surf == Level39Tile.GRASS ? Level39Blocks.GRASS_SLAB : Level39Blocks.DIRT_SLAB).get().defaultBlockState());
                    feet = -1; // standing on a half block: nothing is placed on it
                }
            }
        }

        // ---- What stands on it ------------------------------------------------------------------
        int k = tile.thing[i];
        if (k >= 0) {
            Level39Cell.Thing t = tile.cell.things[k];
            switch (t.kind()) {
                case Level39Cell.HOUSE -> {
                    Level39Houses.fillColumn(seed, t.house(), x, z, out);
                    return;
                }
                case Level39Cell.TREE_BARE, Level39Cell.TREE_LEAFY -> tree(t.parts(), t.cx(), t.cz(), x, z, out);
                case Level39Cell.TOWER -> tower(t, x, z, out);
                case Level39Cell.PARK -> {
                    if (park(seed, t, x, z, out)) return;
                }
                case Level39Cell.CAMP -> {
                    if (camp(seed, t, x, z, out)) return;
                }
                default -> {
                }
            }
        }
        if (feet < 0) {
            return;
        }
        byte prop = tile.prop[i];
        if (prop >= Level39Tile.FENCE && prop < Level39Tile.FENCE + 8) {
            int code = prop - Level39Tile.FENCE;
            out.set(feet, Level39Blocks.PICKET_FENCE.get().defaultBlockState()
                    .setValue(AxisShapeBlock.AXIS, code < 4 ? Direction.Axis.X : Direction.Axis.Z)
                    .setValue(PicketFenceBlock.STYLE, code & 3));
            return;
        }
        if (prop >= Level39Tile.YARD) {
            yardThing(prop - Level39Tile.YARD, feet, out);
            return;
        }
        switch (prop) {
            case Level39Tile.FENCE_POST -> {
                out.set(feet, Level39Blocks.FENCE_POST.get().defaultBlockState());
                return;
            }
            case Level39Tile.POLE -> {
                BlockState pole = Level39Blocks.UTILITY_POLE.get().defaultBlockState();
                for (int y = feet; y < CROSSARM_Y; y++) out.set(y, pole);
                Level39Cell.Pole p = tile.cell.poles[tile.poleOf[i]];
                // The crossarm goes across the wires.
                out.set(CROSSARM_Y, Level39Blocks.POLE_CROSSARM.get().defaultBlockState()
                        .setValue(AxisShapeBlock.AXIS, p.wiresAlongX() ? Direction.Axis.Z : Direction.Axis.X));
                return;
            }
            case Level39Tile.BUSH, Level39Tile.BUSH_TALL -> {
                out.set(feet, Level39Blocks.BUSH.get().defaultBlockState());
                if (prop == Level39Tile.BUSH_TALL) out.set(feet + 1, Level39Blocks.BUSH.get().defaultBlockState());
                return;
            }
            case Level39Tile.HEDGE, Level39Tile.HEDGE_TALL -> {
                out.set(feet, Level39Blocks.HEDGE.get().defaultBlockState());
                if (prop == Level39Tile.HEDGE_TALL) out.set(feet + 1, Level39Blocks.HEDGE.get().defaultBlockState());
                return;
            }
            default -> {
                if (prop >= Level39Tile.TRANSFORMER && prop < Level39Tile.TRANSFORMER + 4) {
                    out.set(TRANSFORMER_Y, Level39Blocks.POLE_TRANSFORMER.get().defaultBlockState()
                            .setValue(WallMountBlock.FACING, Direction.from2DDataValue(prop - Level39Tile.TRANSFORMER)));
                }
            }
        }
        // Weeds in the yards, thickest along the fences and walls (never on a tree trunk).
        if ((surf == Level39Tile.GRASS || surf == Level39Tile.YARD_DIRT || surf == Level39Tile.PATH_GRASS)
                && !(k >= 0 && tile.cell.things[k].cx() == x && tile.cell.things[k].cz() == z)) {
            double chance = surf == Level39Tile.YARD_DIRT ? 0.03 : nearProp(tile, x, z) ? 0.35 : 0.1;
            if (Hash.unit(seed, x, z, 3975) < chance) {
                out.set(feet, Level39Blocks.WEEDS.get().defaultBlockState());
            }
        }
    }

    /** True next to a fence, hedge or other prop (weeds grow thick where the mower never reached). */
    private static boolean nearProp(Level39Tile tile, int x, int z) {
        for (int d = 0; d < 4; d++) {
            int nx = x + (d == 1 ? 1 : d == 3 ? -1 : 0);
            int nz = z + (d == 0 ? -1 : d == 2 ? 1 : 0);
            if (tile.inside(nx, nz) && tile.prop[tile.index(nx, nz)] != Level39Tile.NO_PROP) {
                return true;
            }
        }
        return false;
    }

    /** Woodpiles, rain barrels, clotheslines, mailboxes, carts and wheelbarrows. */
    private static void yardThing(int code, int feet, LevelLayout.Column out) {
        int kind = code >> 2;
        int dir = code & 3;
        Direction facing = Direction.from2DDataValue(dir);
        switch (kind) {
            case Level39Cell.YARD_WOODPILE -> out.set(feet, Level39Blocks.WOODPILE.get().defaultBlockState()
                    .setValue(WallMountBlock.FACING, facing.getOpposite()));
            case Level39Cell.YARD_BARREL -> out.set(feet, Level39Blocks.RAIN_BARREL.get().defaultBlockState());
            case Level39Cell.YARD_CLOTHES_POST -> {
                // Two blocks of post; the T crossbar runs the same way as the line.
                Direction.Axis bar = dir == 0 ? Direction.Axis.X : Direction.Axis.Z;
                out.set(feet, Level39Blocks.FENCE_POST.get().defaultBlockState());
                out.set(feet + 1, Level39Blocks.CLOTHESLINE_POST.get().defaultBlockState().setValue(AxisShapeBlock.AXIS, bar));
            }
            case Level39Cell.YARD_CLOTHESLINE -> out.set(feet + 1, Level39Blocks.CLOTHESLINE.get().defaultBlockState()
                    .setValue(AxisShapeBlock.AXIS, dir == 0 ? Direction.Axis.X : Direction.Axis.Z));
            case Level39Cell.YARD_MAILBOX -> out.set(feet, Level39Blocks.MAILBOX.get().defaultBlockState()
                    .setValue(WallMountBlock.FACING, facing));
            case Level39Cell.YARD_HANDCART -> out.set(feet, Level39Blocks.HANDCART.get().defaultBlockState()
                    .setValue(WallMountBlock.FACING, facing));
            default -> out.set(feet, Level39Blocks.WHEELBARROW.get().defaultBlockState()
                    .setValue(WallMountBlock.FACING, facing));
        }
    }

    /**
     * The holes: a ragged, roughly round pit straight down into the tunnel below. Its wall bulges
     * and pinches with depth through the soil, clay and rock layers; turf hangs over the edge and
     * roots dangle into the pit. Returns true if (x, z) is open at the ground (nothing stands on it).
     */
    private static boolean holeShaft(Level39Tile tile, int x, int z, int tunnelTop, LevelLayout.Column out) {
        boolean openAtTop = false;
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int k : tile.holes) {
            Level39Cell.Hole hole = tile.cell.holes[k];
            int ddx = x - hole.x();
            int ddz = z - hole.z();
            if (Math.abs(ddx) > 6 || Math.abs(ddz) > 6) {
                continue;
            }
            float d = (float) Math.sqrt(ddx * ddx + ddz * ddz);
            int bottom = tunnelTop > 0 ? tunnelTop + 1 : Level39Tunnels.RIM_FEET_Y;
            for (int y = bottom; y <= Level39Layout.GROUND_Y; y++) {
                float r = Level39Cell.holeRadius(hole, x, z, y);
                // Near the vault the shaft must stay over the water (inside the tunnel).
                if (y < 54) r = Math.min(r, 2.9f);
                if (d <= r) {
                    out.set(y, air);
                    if (y == Level39Layout.GROUND_Y) openAtTop = true;
                }
            }
            if (openAtTop) {
                // Roots hanging from the turf around the edge, and a lip of turf overhanging it.
                float edge = Level39Cell.holeRadius(hole, x, z, Level39Layout.GROUND_Y);
                if (d > edge - 1.2f) {
                    long rh = Hash.of(tile.seed, x, z, 3986);
                    if (Hash.unit(rh) < 0.55) {
                        Direction toCentre = Math.abs(ddx) >= Math.abs(ddz)
                                ? (ddx > 0 ? Direction.WEST : Direction.EAST) : (ddz > 0 ? Direction.NORTH : Direction.SOUTH);
                        out.set(Level39Layout.GROUND_Y, Level39Blocks.GRASS_OVERHANG.get().defaultBlockState()
                                .setValue(WallMountBlock.FACING, toCentre));
                    }
                    int roots = 1 + Hash.range(Hash.mix(rh), 3);
                    for (int y = Level39Layout.GROUND_Y - 1; y >= Level39Layout.GROUND_Y - roots; y--) {
                        if (Hash.unit(Hash.mix(rh + y)) < 0.6) {
                            out.set(y, Level39Blocks.ROOTS.get().defaultBlockState());
                        }
                    }
                }
                return true;
            }
        }
        return false;
    }

    private static BlockState surface(byte surf) {
        return switch (surf) {
            case Level39Tile.YARD_DIRT -> Level39Blocks.YARD_DIRT.get().defaultBlockState();
            case Level39Tile.PATH_DIRT -> Level39Blocks.PATH_DIRT.get().defaultBlockState();
            case Level39Tile.PATH_GRAVEL, Level39Tile.APRON -> Level39Blocks.PATH_GRAVEL.get().defaultBlockState();
            case Level39Tile.PATH_GRASS -> Level39Blocks.PATH_GRASS.get().defaultBlockState();
            case Level39Tile.PATH_MUD, Level39Tile.HOLE_RIM -> Level39Blocks.PATH_MUD.get().defaultBlockState();
            default -> Level39Blocks.YARD_GRASS.get().defaultBlockState();
        };
    }

    // ==========================================================================================
    // Trees
    // ==========================================================================================

    /**
     * One column of a grown tree (see {@link Level39Trees}): every part of it standing over
     * (x, z), from the trunk foot up. Twigs and leaves first, so the wood always wins.
     */
    static void tree(int[] parts, int cx, int cz, int x, int z, LevelLayout.Column out) {
        int feet = Level39Layout.GROUND_Y + 1;
        int dx = x - cx;
        int dz = z - cz;
        for (int pass = 0; pass < 2; pass++) {
            for (int p : parts) {
                if (Level39Trees.dx(p) != dx || Level39Trees.dz(p) != dz) {
                    continue;
                }
                boolean wood = Level39Trees.type(p) == Level39Trees.PIECE;
                if (wood == (pass == 1)) {
                    out.set(feet + Level39Trees.dy(p), Level39Trees.state(p));
                }
            }
        }
    }

    // ==========================================================================================
    // Lattice telephone towers
    // ==========================================================================================

    /** An abandoned lattice tower: a 5x5 rusty frame narrowing to 3x3 and a single mast, arms at the top. */
    private static void tower(Level39Cell.Thing t, int x, int z, LevelLayout.Column out) {
        int dx = x - t.cx();
        int dz = z - t.cz();
        int feet = Level39Layout.GROUND_Y + 1;
        int h = t.height();
        BlockState lattice = Level39Blocks.LATTICE.get().defaultBlockState();
        int ring = Math.max(Math.abs(dx), Math.abs(dz));
        if (ring == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2) {
            out.set(feet, Level39Blocks.FOUNDATION.get().defaultBlockState()); // concrete footings
        }
        for (int y = feet + 1; y <= feet + h; y++) {
            int up = y - feet;
            int size = up < h * 2 / 5 ? 2 : up < h * 3 / 4 ? 1 : 0;
            boolean frame = ring == size && (size == 0 || Math.abs(dx) == size && Math.abs(dz) == size
                    || up % 6 == 0 || (size == 2 && up < 3));
            // Horizontal rings every 6 blocks and the corner legs; open faces in between.
            boolean arms = (up == h - 3 || up == h) && dz == 0 && Math.abs(dx) <= 3;
            if (frame || arms) {
                out.set(y, lattice);
            }
        }
        if (dx == 0 && dz == 0) {
            out.set(feet + h + 1, Level39Blocks.POLE_CROSSARM.get().defaultBlockState().setValue(AxisShapeBlock.AXIS, Direction.Axis.X));
        }
    }

    // ==========================================================================================
    // Parks
    // ==========================================================================================

    /**
     * A very rare park filling a whole block: gravel walks crossing at a white gazebo, benches,
     * a rusty swing set, a dead fountain and a few bare trees. Returns true if it wrote the column
     * completely (the caller then stops).
     */
    private static boolean park(long seed, Level39Cell.Thing t, int x, int z, LevelLayout.Column out) {
        int dx = x - t.cx();
        int dz = z - t.cz();
        int hw = (t.x1() - t.x0()) / 2;
        int hd = (t.z1() - t.z0()) / 2;
        int feet = Level39Layout.GROUND_Y + 1;
        int ground = Level39Layout.GROUND_Y;
        BlockState gravel = Level39Blocks.PATH_GRAVEL.get().defaultBlockState();
        double r = Math.sqrt(dx * dx + dz * dz);

        // The gazebo in the middle.
        if (r <= 5.5) {
            out.set(ground, Level39Blocks.PORCH_PLANKS.get().defaultBlockState());
            boolean post = (Math.abs(dx) == 4 && dz == 0) || (dx == 0 && Math.abs(dz) == 4) || (Math.abs(dx) == 3 && Math.abs(dz) == 3);
            if (post) {
                for (int y = feet; y <= feet + 2; y++) out.set(y, Level39Blocks.WHITE_POST.get().defaultBlockState());
            }
            if (r > 4.6) {
                out.set(ground, gravel);
            }
            // Roof: rings of shingle stairs stepping in toward the middle.
            int level = r > 4.6 ? 0 : r > 3.3 ? 1 : r > 2.0 ? 2 : r > 0.9 ? 3 : 4;
            if (r <= 5.2) {
                int y = feet + 3 + level;
                if (level >= 4) {
                    out.set(y, Level39Blocks.SHINGLES.get().defaultBlockState());
                    out.set(y + 1, Level39Blocks.WHITE_POST.get().defaultBlockState());
                } else {
                    Direction in = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST)
                            : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
                    out.set(y, Level39Blocks.SHINGLE_STAIRS.get().defaultBlockState().setValue(StairBlock.FACING, in));
                    if (level > 0) {
                        out.set(y - 1, Level39Blocks.WHITE_TRIM.get().defaultBlockState());
                    }
                }
            }
            return true;
        }
        // Gravel walks from the edges to the gazebo.
        boolean walk = Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
        if (walk) {
            out.set(ground, gravel);
        }
        // Benches beside the walks, facing them.
        int bench = Math.max(Math.abs(dx), Math.abs(dz));
        if ((bench == 9 || bench == 16) && (Math.abs(dx) == 2 || Math.abs(dz) == 2) && Math.min(Math.abs(dx), Math.abs(dz)) == 2) {
            Direction face = Math.abs(dx) == 2 ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
            out.set(feet, Level39Blocks.PARK_BENCH.get().defaultBlockState().setValue(WallMountBlock.FACING, face));
            return true;
        }
        // Rusty swing set in the north-west quarter.
        int sx = x - (t.cx() - hw / 2);
        int sz = z - (t.cz() - hd / 2);
        if (sz == 0 && Math.abs(sx) <= 3) {
            if (Math.abs(sx) == 3) {
                for (int y = feet; y <= feet + 2; y++) out.set(y, Level39Blocks.RUSTY_POST.get().defaultBlockState());
            }
            out.set(feet + 3, Level39Blocks.SWING_BAR.get().defaultBlockState().setValue(AxisShapeBlock.AXIS, Direction.Axis.X));
            if (Math.abs(sx) == 1) {
                out.set(feet + 1, Level39Blocks.SWING.get().defaultBlockState().setValue(WallMountBlock.FACING, Direction.SOUTH));
            }
            return true;
        }
        // The dead fountain in the south-east quarter: a dry granite basin full of mud.
        int fx = x - (t.cx() + hw / 2);
        int fz = z - (t.cz() + hd / 2);
        double fr = Math.sqrt(fx * fx + fz * fz);
        if (fr <= 3.6) {
            out.set(ground, fr > 2.6 ? gravel : Level39Blocks.PATH_MUD.get().defaultBlockState());
            BlockState granite = Level39Blocks.GRANITE.get().defaultBlockState();
            if (fr > 2.6) {
                if (fr <= 3.4) out.set(feet, granite); // the basin's low rim
            } else if (fx == 0 && fz == 0) {
                out.set(feet, granite);
                out.set(feet + 1, granite);
                out.set(feet + 2, Level39Blocks.GRANITE_STAIRS.get().defaultBlockState().setValue(StairBlock.HALF, Half.TOP));
            } else if (Hash.unit(seed, x, z, 3977) < 0.35) {
                out.set(feet - 1, Blocks.WATER.defaultBlockState()); // a little dark rainwater left in it
            }
            return true;
        }
        // (The park's trees are planned as ordinary trees: see Level39Cell.)
        return false;
    }

    // ==========================================================================================
    // M.E.G. survey camp
    // ==========================================================================================

    /**
     * An abandoned M.E.G. survey camp: a small ridge tent, a stack of stencilled crates with a
     * clipboard of notes on top and a dead lantern, on trampled dirt. Returns true if it wrote
     * the column completely.
     */
    private static boolean camp(long seed, Level39Cell.Thing t, int x, int z, LevelLayout.Column out) {
        int dx = x - t.cx();
        int dz = z - t.cz();
        int feet = Level39Layout.GROUND_Y + 1;
        if (dx * dx + dz * dz <= 36) {
            out.set(feet - 1, Level39Blocks.PATH_DIRT.get().defaultBlockState());
        }
        // Tent: ridge along x, from dx -3 to 3, dz -4..-2.
        if (Math.abs(dx) <= 3 && dz >= -4 && dz <= -2) {
            if (dz == -3) {
                out.set(feet + 1, Level39Blocks.CANVAS.get().defaultBlockState());
                if (dx == -3) out.set(feet, Level39Blocks.CANVAS.get().defaultBlockState()); // closed back
            } else {
                out.set(feet, Level39Blocks.CANVAS_STAIRS.get().defaultBlockState()
                        .setValue(StairBlock.FACING, dz == -4 ? Direction.SOUTH : Direction.NORTH));
            }
            return true;
        }
        // Crates, one stacked, with the notes on top of another.
        if ((dx == 2 && dz == 1) || (dx == 3 && dz == 1) || (dx == 2 && dz == 2)) {
            BlockState crate = Level39Blocks.MEG_CRATE.get().defaultBlockState();
            out.set(feet, crate);
            if (dx == 3) {
                out.set(feet + 1, crate);
            }
            if (dx == 2 && dz == 2) {
                long h = Hash.of(seed, x, z, 3979);
                out.set(feet + 1, Level39Blocks.SURVEY_NOTE.get().defaultBlockState()
                        .setValue(WallMountBlock.FACING, Direction.from2DDataValue(Hash.range(h, 4)))
                        .setValue(SurveyNoteBlock.NOTE, Hash.range(Hash.mix(h), SurveyNoteBlock.NOTES)));
            }
            return true;
        }
        if (dx == -1 && dz == 2) {
            out.set(feet, Level39Blocks.DEAD_LAMP.get().defaultBlockState().setValue(WallMountBlock.FACING, Direction.SOUTH));
            return true;
        }
        return false;
    }
}
