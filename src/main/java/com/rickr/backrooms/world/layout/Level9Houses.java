package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.CeilingFurnitureBlock;
import com.rickr.backrooms.block.FamilyPhotoBlock;
import com.rickr.backrooms.block.HouseBedBlock;
import com.rickr.backrooms.block.HouseDoorBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.layout.Level9Layout.House;
import com.rickr.backrooms.world.layout.LevelLayout.Column;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

/**
 * Level 9's house plans and the things that make a house look lived in (or long abandoned).
 * {@link Level9Layout} draws the walls, roofs and the basic rooms; it calls in here at a few
 * points ({@code houseOf}, {@code house()}, {@code interior()}) and this class adds the rest.
 *
 * <p><b>Plans.</b> Every ordinary house picks one plan from its lot's hash (so the same lot is
 * always the same house). Rough shares of all houses:
 * <pre>
 *   classic      ~41%  the original 1-2 storey house (plus every safe, twin, clipped, glitch and clay-door house)
 *   ranch        ~18%  wide single storey: living room, kitchen and a bedroom side by side
 *   attic        ~14%  two storeys and a dusty attic, reached by a ladder through the bedroom ceiling
 *   basement     ~12%  one storey with stairs down to a dark cellar (water heater, washer, boxes)
 *   boarded       ~9%  planks over every window; inside, dust sheets, dust and cobwebs
 *   half_built    ~2%  bare stud walls, rafters, a tarp, sawhorses and lumber; nobody finished it
 *   upside_down ~1.75% (a "wrong" house) the living room's furniture is stuck to the ceiling
 *   repeated    ~1.75% (a "wrong" house) two identical rooms one behind the other, down to the objects
 * </pre>
 * Safe houses, twin and clipped houses, the glitch houses and the clay door to Level 94 all keep
 * the classic plan: their own code relies on its exact shape.
 *
 * <p><b>Lived in.</b> Every house also gets a "decor" hash. From it: the TV left on (soft static,
 * light 4) in ~40%, dinner still on the table in ~45%, unmade beds in ~60%, a kid's corner in
 * ~35% of the houses with a bedroom (small bed, toys, a drawing on the wall), family photos on
 * the walls (~15% with the faces scratched out, half of them in boarded houses), an answering
 * machine on the kitchen counter in ~25% and a missing-person poster on the fridge in ~10%.
 *
 * <p>A third kind of wrong house, the long hall (a hallway that never ends), is an anomaly site
 * ({@code level_9/long_hall}, see Level9AnomalyPlan) because the server has to move you in it.
 *
 * <p>Find each plan in game with {@code /backrooms house9 <plan>} (see {@link #locate}).
 */
public final class Level9Houses {
    public static final int CLASSIC = 0;
    public static final int RANCH = 1;
    public static final int ATTIC = 2;
    public static final int BASEMENT = 3;
    public static final int BOARDED = 4;
    public static final int HALF_BUILT = 5;
    public static final int UPSIDE_DOWN = 6;
    public static final int REPEATED = 7;
    /** Plan names, in plan-number order (used by the test command). */
    public static final List<String> PLANS = List.of("classic", "ranch", "attic", "basement", "boarded", "half_built",
            "upside_down", "repeated");
    /** Everything /backrooms house9 can look for: the plans plus safe houses, clay doors and long halls. */
    public static final List<String> KINDS = List.of("classic", "ranch", "attic", "basement", "boarded", "half_built",
            "upside_down", "repeated", "safe", "clay_door", "long_hall");

    // Cumulative shares of the plan roll (see the class comment for what they add up to).
    private static final double UPSIDE_DOWN_UPTO = 0.0175;
    private static final double REPEATED_UPTO = 0.035;
    private static final double HALF_BUILT_UPTO = 0.055;
    private static final double BOARDED_UPTO = 0.145;
    private static final double BASEMENT_UPTO = 0.265;
    private static final double ATTIC_UPTO = 0.405;
    private static final double RANCH_UPTO = 0.585;

    /** The repeated house is exactly this deep: wall, 4 rows of room, wall, 4 rows of room, wall. */
    private static final int REPEATED_DEPTH = 11;
    private static final int REPEATED_ROOM = 4;

    /** Basement floor block; the cellar's air is y38-40 under the ground floor (y41). */
    private static final int CELLAR_FLOOR_Y = 37;

    private Level9Houses() {}

    // ==========================================================================================
    // Picking a plan (called once per house from Level9Layout.houseOf)
    // ==========================================================================================

    /**
     * Gives the house its decor hash and, if it is an ordinary house, its plan. {@code odd} means
     * the anomaly plan already turned it into a twin or clipped house.
     */
    static void choosePlan(long seed, int r, int side, int idx, House h, boolean odd) {
        int k = r * 2 + side;
        h.decor = Hash.of(seed, idx, k, 4900);
        h.plan = CLASSIC;
        // These depend on the classic shape (see the class comment), so they never change plan.
        if (odd || h.safe || h.clayDoor || h.glitch) return;
        double roll = Hash.unit(seed, idx, k, 4901);
        long g = Hash.of(seed, idx, k, 4902);
        if (roll < UPSIDE_DOWN_UPTO) {
            if (h.livingRoomFits()) upsideDown(h);
        } else if (roll < REPEATED_UPTO) {
            repeated(h);
        } else if (roll < HALF_BUILT_UPTO) {
            halfBuilt(h);
        } else if (roll < BOARDED_UPTO) {
            boarded(h, g);
        } else if (roll < BASEMENT_UPTO) {
            if (h.partitionU != Integer.MIN_VALUE) basement(h);
        } else if (roll < ATTIC_UPTO) {
            attic(h);
        } else if (roll < RANCH_UPTO) {
            ranch(h, g);
        }
    }

    private static void oneStorey(House h) {
        h.storeys = 1;
        h.roofBase = 41 + 5;
    }

    /** Re-centres the porch (same width as before) on a moved front door. */
    private static void porch(House h) {
        int width = h.porchU1 - h.porchU0 + 1;
        h.porchU0 = Math.min(Math.max(h.doorU - width / 2, h.hu0), h.hu1 - width + 1);
        h.porchU1 = h.porchU0 + width - 1;
    }

    private static void ranch(House h, long g) {
        h.plan = RANCH;
        oneStorey(h);
        h.garage = false; // a ranch house takes the whole width of the lot
        h.drivewayBackpack = false;
        h.width = 15;
        h.depth = 8 + Hash.range(Hash.mix(g ^ 1), 2);
        h.hu0 = 1 + Hash.range(Hash.mix(g ^ 2), 2);
        h.hu1 = h.hu0 + h.width - 1;
        h.ridgeAlongU = true; // one long, low roof
        // The front door opens into the living room (the left third).
        h.doorU = h.hu0 + 2 + Hash.range(Hash.mix(g ^ 3), 2);
        porch(h);
        h.partitionU = h.hu0 + 5; // first of the two interior walls (the classic floor rule uses it)
    }

    private static void attic(House h) {
        h.plan = ATTIC;
        h.storeys = 2;
        h.roofBase = 41 + 10;
        // Gables on the side walls: the attic is tall all along the middle, and the ladder can lean on a gable wall.
        h.ridgeAlongU = true;
    }

    private static void basement(House h) {
        h.plan = BASEMENT;
        oneStorey(h);
    }

    private static void boarded(House h, long g) {
        h.plan = BOARDED;
        h.unlocked = Hash.unit(Hash.mix(g ^ 4)) < 0.5; // half of them can still be got into
        h.porchLight = false;
    }

    private static void halfBuilt(House h) {
        h.plan = HALF_BUILT;
        oneStorey(h);
        h.garage = false;
        h.drivewayBackpack = false;
        h.porchU0 = -10; // no porch yet
        h.porchU1 = -10;
        h.porchLight = false;
        h.porchBackpack = false;
        h.unlocked = true; // open framing: walk in anywhere
        h.crate = false;
    }

    private static void upsideDown(House h) {
        h.plan = UPSIDE_DOWN;
        oneStorey(h);
        h.unlocked = true;
        h.porchLight = false;
    }

    private static void repeated(House h) {
        h.plan = REPEATED;
        oneStorey(h);
        h.unlocked = true;
        h.porchLight = false;
        h.crate = false;
        h.depth = REPEATED_DEPTH;
        // The doors (front, middle, back) sit in one line in the middle, so the furniture can
        // stand to either side of the way through and still be the same in both rooms.
        h.doorU = h.hu0 + h.width / 2;
        porch(h);
        h.partitionU = Integer.MIN_VALUE; // no side-by-side rooms
    }

    // ==========================================================================================
    // Hooks from Level9Layout.house() (walls and windows)
    // ==========================================================================================

    /** The window block of this house's walls: boarded houses have planks nailed over them. */
    static BlockState windowBlock(House h) {
        return (h.plan == BOARDED ? ModBlocks.L9_BOARDED_WINDOW : ModBlocks.HOUSE_WINDOW).get().defaultBlockState();
    }

    /**
     * The repeated house's side walls get corner trim where the middle wall meets them too, so
     * the back room's corners look exactly like the front room's.
     */
    static boolean extraCorner(House h, int v) {
        return h.plan == REPEATED && v == h.setback + 1 + REPEATED_ROOM;
    }

    /** Repeated house: one side window per room, in the same place in both rooms. */
    static boolean repeatedSideWindow(House h, int v) {
        int w = v - h.setback;
        return w == 2 || w == 2 + REPEATED_ROOM + 1;
    }

    /** A front (or back) wall window spot, the classic rule: every third column, none beside a door. */
    private static boolean frontWindow(House h, int u) {
        if (Math.abs(u - h.doorU) <= 1) return false;
        int w = u - h.hu0;
        return w % 3 == 2 && w > 0 && w < h.width - 1;
    }

    /** A classic side-wall window at row v. */
    private static boolean sideWindow(House h, int v) {
        int w = v - h.setback;
        return w % 3 == 2 && w > 0 && w < h.depth - 1;
    }

    /** A classic back-wall window at column u (houses without a back door). */
    private static boolean backWindow(House h, int u) {
        int w = u - h.hu0;
        return w % 3 == 2 && w > 0 && w < h.width - 1;
    }

    /**
     * Below the ground floor of the house footprint (walls included): the cellar of a basement
     * house, its concrete floor and brick walls. Only enterable houses get one.
     */
    static void underFloor(House h, int u, int v, boolean wall, Column out) {
        if (h.plan != BASEMENT || !h.unlocked) return;
        out.set(CELLAR_FLOOR_Y, ModBlocks.TUNNEL_CONCRETE.get().defaultBlockState());
        if (wall) {
            for (int y = CELLAR_FLOOR_Y + 1; y <= 40; y++) out.set(y, ModBlocks.BRICK_FOUNDATION.get().defaultBlockState());
        }
    }

    // ==========================================================================================
    // The half-built house (drawn here entirely, instead of Level9Layout.house())
    // ==========================================================================================

    /**
     * Lot column (u, v) of a half-built house: a plywood subfloor, a stud every other block along
     * the walls and the interior wall, a top plate, a ridge board and every third rafter, and the
     * builders' things lying about inside. No door, no windows, no siding: walk in anywhere.
     */
    static void halfBuilt(House h, int u, int v, Column out) {
        int s = h.setback;
        int d = h.depth;
        if (h.inHouse(u, v)) {
            out.set(41, ModBlocks.L9_PLYWOOD.get().defaultBlockState());
            boolean wallU = u == h.hu0 || u == h.hu1;
            boolean wallV = v == s || v == s + d - 1;
            BlockState stud = ModBlocks.L9_STUD.get().defaultBlockState();
            BlockState plate = Level9Layout.slab(ModBlocks.L9_LUMBER_SLAB.get().defaultBlockState());
            if (wallU || wallV) {
                boolean studHere = wallU && wallV
                        || (wallV ? (u - h.hu0) % 2 == 0 && u != h.doorU : (v - s) % 2 == 0);
                if (studHere) {
                    for (int y = 42; y <= 45; y++) out.set(y, stud);
                }
                out.set(46, plate);
            } else if (u == h.partitionU) {
                // The interior wall's framing, with a gap where its doorway will be.
                if ((v - s) % 2 == 0 && v != s + d / 2) {
                    for (int y = 42; y <= 45; y++) out.set(y, stud);
                }
                out.set(46, plate);
            } else {
                halfBuiltInside(h, u, v, out);
            }
        }
        // The roof: a ridge board along the top and every third rafter, open to the sky between.
        BlockState roof = Level9Layout.roofAt(h, u, v);
        if (roof != null) {
            int y = h.roofBase + Level9Layout.roofLayer(h, u, v);
            if (roof.getBlock() instanceof StairBlock) {
                int along = h.ridgeAlongU ? u - h.hu0 : v - s;
                int last = h.ridgeAlongU ? h.width - 1 : d - 1;
                if (along >= 0 && (along % 3 == 0 || along == last)) {
                    out.set(y, ModBlocks.L9_LUMBER_STAIRS.get().defaultBlockState()
                            .setValue(StairBlock.FACING, roof.getValue(StairBlock.FACING)));
                }
            } else if (h.ridgeAlongU ? u >= h.hu0 && u <= h.hu1 : v >= s && v <= s + d - 1) {
                out.set(y, Level9Layout.slab(ModBlocks.L9_LUMBER_SLAB.get().defaultBlockState()));
            }
        }
    }

    private static void halfBuiltInside(House h, int u, int v, Column out) {
        int s = h.setback;
        int back = s + h.depth - 2;
        int mid = h.hu0 + h.width / 2;
        BlockState piece = null;
        if (v == s + 3 && (u == mid - 1 || u == mid + 1)) {
            piece = Level9Layout.facing(ModBlocks.L9_SAWHORSE.get().defaultBlockState(), h.front);
        } else if (v == back && u >= h.hu0 + 1 && u <= h.hu0 + 3) {
            // A stack of lumber against the back wall, two high at the end.
            out.set(42, ModBlocks.L9_LUMBER.get().defaultBlockState());
            if (u <= h.hu0 + 2) out.set(43, Level9Layout.slab(ModBlocks.L9_LUMBER_SLAB.get().defaultBlockState()));
            return;
        } else if (v == back && (u == h.hu1 - 1 || u == h.hu1 - 2)) {
            piece = Level9Layout.facing(ModBlocks.L9_TARP.get().defaultBlockState(), h.front);
        } else if (v == s + 1 && u == h.hu1 - 1) {
            piece = Level9Layout.facing(ModBlocks.STEP_LADDER.get().defaultBlockState(), h.inward);
        } else if (v == s + 1 && u == h.hu1 - 2) {
            piece = Level9Layout.facing(ModBlocks.CARDBOARD_BOXES.get().defaultBlockState(), h.inward);
        }
        if (piece != null) {
            out.set(42, piece);
        } else if (cell(h, u, v, 1) < 0.2) {
            out.set(42, ModBlocks.LEAF_LITTER.get().defaultBlockState()); // it's open to the sky
        }
    }

    // ==========================================================================================
    // Hooks from Level9Layout.interior()
    // ==========================================================================================

    /**
     * Called first for every inside column of an enterable house. Ranch and repeated houses get
     * their whole interior here (returns true: the classic rooms are skipped). For the others
     * this adds what lies above and below the classic rooms (upstairs extras, the attic, the
     * cellar) and swaps floor and ceiling in the upside-down room, then returns false.
     */
    static boolean interior(House h, int u, int v, Column out) {
        if (h.plan == RANCH) {
            ranch(h, u, v, out);
            return true;
        }
        if (h.plan == REPEATED) {
            repeated(h, u, v, out);
            return true;
        }
        if (h.plan == UPSIDE_DOWN && u < h.partitionU) {
            out.set(41, ModBlocks.HOUSE_TRIM.get().defaultBlockState());        // the "ceiling" underfoot
            out.set(h.roofBase, ModBlocks.BEIGE_CARPET.get().defaultBlockState()); // the carpet overhead
        }
        if (h.storeys == 2) upstairs(h, u, v, out);
        if (h.plan == ATTIC) attic(h, u, v, out);
        if (h.plan == BASEMENT) cellar(h, u, v, out);
        return false;
    }

    /**
     * Puts one piece of the classic rooms' furniture down, the way this house has it: the TV on,
     * dinner on the table, dust sheets in a boarded house, on the ceiling in the upside-down room.
     */
    static void place(House h, int u, int v, @Nullable BlockState piece, Column out) {
        if (piece == null) return;
        piece = dress(h, piece);
        if (h.plan == UPSIDE_DOWN && u < h.partitionU) {
            out.set(45, ceiling(piece));
            return;
        }
        out.set(42, piece);
    }

    /** A bed as this house has it: most were left unmade. */
    static BlockState bed(House h, BlockState bed) {
        return unmade(h) ? bed.setValue(HouseBedBlock.UNMADE, true) : bed;
    }

    /**
     * Called last for every inside column of a classic-style room (not the interior wall or the
     * stairs): chairs round the table, photos, the answering machine, the poster on the fridge,
     * and the dust and cobwebs of a boarded house. {@code taken}: furniture already stands at y42.
     */
    static void decorate(House h, int u, int v, boolean taken, Column out) {
        int s = h.setback;
        int d = h.depth;
        int back = s + d - 2;
        int pu = h.partitionU;
        boolean upsideDown = h.plan == UPSIDE_DOWN && u < pu;
        if (pu != Integer.MIN_VALUE) {
            // Chairs either side of the dining table (Level9Layout.interior puts it at pu + 2).
            int tu = pu + 2;
            int tv = s + d / 2 - 1;
            boolean table = tu < h.hu1 && !(h.storeys == 2 && tu == h.stairU());
            if (table && !taken && v == tv) {
                if (u == pu + 1 && h.plan != BASEMENT) { // (the cellar stairs run down that column)
                    out.set(42, Level9Layout.facing(ModBlocks.WOODEN_CHAIR.get().defaultBlockState(), House.alongU(true)));
                    taken = true;
                } else if (u == pu + 3 && u < h.hu1 && !(h.storeys == 2 && u == h.stairU())) {
                    out.set(42, Level9Layout.facing(ModBlocks.WOODEN_CHAIR.get().defaultBlockState(), House.alongU(false)));
                    taken = true;
                }
            }
            // A photo on the living room's side wall (rows s+3 never have a side window).
            if (u == h.hu0 + 1 && v == s + 3 && u < pu) {
                out.set(upsideDown ? 44 : 43, photo(h, 0, House.alongU(true)));
            }
            // A photo on the kitchen's front wall, where there is no window, door or stairs behind it.
            int ku = kitchenPhotoU(h);
            if (u == ku && v == s + 1) {
                out.set(43, photo(h, 2, h.inward));
            }
            int kitchenEnd = Math.min(pu + 3, h.hu1 - (h.storeys == 2 ? 3 : 1));
            if (machine(h) && u == pu + 1 && v == back && pu + 1 < kitchenEnd) {
                out.set(43, Level9Story.answeringMachine(h.decor, u, v, h.front)); // on the first counter
            }
            if (poster(h) && u == kitchenEnd && kitchenEnd > pu && v == back - 1) {
                out.set(43, Level9Story.missingPoster(h.decor, u, v, h.front)); // stuck on the fridge door
            }
        }
        if (h.plan == BOARDED) {
            if (!taken && cell(h, u, v, 2) < 0.55) out.set(42, ModBlocks.L9_HOUSE_DUST.get().defaultBlockState());
            boolean cornerU = u == h.hu0 + 1 || u == h.hu1 - 1 || pu != Integer.MIN_VALUE && (u == pu - 1 || u == pu + 1);
            if (cornerU && (v == s + 1 || v == back) && cell(h, u, v, 3) < 0.6) {
                out.set(45, Blocks.COBWEB.defaultBlockState());
            }
        }
    }

    /** Column of the kitchen front-wall photo, or MIN_VALUE if no spot fits. */
    private static int kitchenPhotoU(House h) {
        for (int u = h.partitionU + 1; u <= h.partitionU + 2 && u < h.hu1; u++) {
            if (frontWindow(h, u) || Math.abs(u - h.doorU) <= 1) continue;
            if (h.storeys == 2 && u >= h.stairU() - 1) continue; // the stairs and the step onto them
            return u;
        }
        return Integer.MIN_VALUE;
    }

    private static BlockState dress(House h, BlockState piece) {
        Block b = piece.getBlock();
        if (h.plan == BOARDED && (b == ModBlocks.SOFA.get() || b == ModBlocks.TV.get())) {
            return sameFacing(ModBlocks.L9_DUST_SHEET.get().defaultBlockState(), piece);
        }
        if (b == ModBlocks.TV.get() && tvOn(h)) {
            return sameFacing(ModBlocks.L9_TV_ON.get().defaultBlockState(), piece);
        }
        if (b == ModBlocks.DINING_TABLE.get() && dinner(h)) {
            return sameFacing(ModBlocks.L9_DINNER_TABLE.get().defaultBlockState(), piece);
        }
        return piece;
    }

    private static BlockState sameFacing(BlockState state, BlockState like) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING, like.getValue(BlockStateProperties.HORIZONTAL_FACING));
    }

    /** The upside-down copy of a piece (pieces with no upside-down model just hang there as they are). */
    private static BlockState ceiling(BlockState piece) {
        Block b = piece.getBlock();
        int p;
        if (b == ModBlocks.SOFA.get()) p = CeilingFurnitureBlock.SOFA;
        else if (b == ModBlocks.TV.get() || b == ModBlocks.L9_TV_ON.get()) p = CeilingFurnitureBlock.TV;
        else if (b == ModBlocks.DINING_TABLE.get() || b == ModBlocks.L9_DINNER_TABLE.get()) p = CeilingFurnitureBlock.TABLE;
        else if (b == ModBlocks.WOODEN_CHAIR.get()) p = CeilingFurnitureBlock.CHAIR;
        else if (b == ModBlocks.KITCHEN_COUNTER.get()) p = CeilingFurnitureBlock.COUNTER;
        else if (b == ModBlocks.FRIDGE.get()) p = CeilingFurnitureBlock.FRIDGE;
        else if (b == ModBlocks.CARDBOARD_BOXES.get()) p = CeilingFurnitureBlock.BOXES;
        else return piece;
        return sameFacing(ModBlocks.L9_CEILING_FURNITURE.get().defaultBlockState(), piece).setValue(CeilingFurnitureBlock.PIECE, p);
    }

    // ---- Upstairs, attic, cellar -----------------------------------------------------------------

    /**
     * The upper floor (y47) of a two-storey house: a photo over the main bed, a kid's corner on
     * the far side of the stairwell in some houses, and dust in a boarded one. (The main bed
     * itself is placed by Level9Layout.interior.)
     */
    private static void upstairs(House h, int u, int v, Column out) {
        int s = h.setback;
        int back = s + h.depth - 2;
        int b = h.hu1;
        if (h.plan == BOARDED && !(u >= h.stairU() - 1 && v <= s + 5) && cell(h, u, v, 4) < 0.5) {
            out.set(47, ModBlocks.L9_HOUSE_DUST.get().defaultBlockState()); // furniture placed later covers it
        }
        if (u == h.hu0 + 3 && v == back) {
            out.set(48, photo(h, 1, h.front)); // on the back wall over the bed (no window at w = 3)
        }
        if (!kids(h)) return;
        if (u == b - 1 && v == back) {
            out.set(47, Level9Layout.facing(ModBlocks.L9_KIDS_BED.get().defaultBlockState(), h.front)); // headboard to the back wall
        } else if (u == b - 2 && v == back - 1 || u == b - 3 && v == back) {
            out.set(47, Level9Layout.facing(ModBlocks.L9_TOYS.get().defaultBlockState(), cell(h, u, v, 5) < 0.5 ? h.front : House.alongU(false)));
        }
        // Drawings: on the side wall beside the little bed and on the back wall, wherever no window is behind.
        if (u == b - 1 && v == back - 1 && !sideWindow(h, v)) {
            out.set(48, Level9Story.kidsDrawing(h.decor, u, v, House.alongU(false)));
        }
        if (u == b - 2 && v == back && !backWindow(h, u)) {
            out.set(48, Level9Story.kidsDrawing(h.decor, u, v, h.front));
        }
    }

    /**
     * Attic house: a ladder up the gable wall from the bedroom through a hole in its ceiling,
     * and under the roof boxes along the eaves, an old trunk, a sheeted chair, dust and cobwebs.
     */
    private static void attic(House h, int u, int v, Column out) {
        int s = h.setback;
        int back = s + h.depth - 2;
        int va = s + h.depth / 2;
        if (u == h.hu0 + 1 && v == va) {
            BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, House.alongU(true));
            for (int y = 47; y <= h.roofBase; y++) out.set(y, ladder); // the top rung fills the hatch
            return;
        }
        int floor = h.roofBase + 1;
        int headroom = Level9Layout.roofLayer(h, u, v) - 1; // air blocks between attic floor and roof
        if (headroom < 1) return;
        boolean landing = u <= h.hu0 + 2 && Math.abs(v - va) <= 1; // where you step off the ladder
        if (!landing && (v == s + 1 || v == back) && cell(h, u, v, 6) < 0.45) {
            out.set(floor, Level9Layout.facing(ModBlocks.CARDBOARD_BOXES.get().defaultBlockState(), v == s + 1 ? h.inward : h.front));
        } else if (u == h.hu1 - 1 && v == va && chance(h, 8, 0.5)) {
            out.set(floor, ModBlocks.SUPPLY_CRATE.get().defaultBlockState()); // someone's old trunk
        } else if (u == h.hu1 - 1 && v == va + 1) {
            out.set(floor, Level9Layout.facing(ModBlocks.L9_DUST_SHEET.get().defaultBlockState(), House.alongU(false)));
        } else if (!landing && cell(h, u, v, 7) < 0.5) {
            out.set(floor, ModBlocks.L9_HOUSE_DUST.get().defaultBlockState());
        }
        if (headroom >= 3 && cell(h, u, v, 8) < 0.15) {
            out.set(h.roofBase + headroom, Blocks.COBWEB.defaultBlockState()); // up against the roof, over your head
        }
    }

    /**
     * Basement house: stairs down the column beside the interior wall (in the kitchen), into a
     * dark cellar with a water heater, a washer, boxes and a step ladder.
     */
    private static void cellar(House h, int u, int v, Column out) {
        int s = h.setback;
        int back = s + h.depth - 2;
        int bu = h.partitionU + 1;
        if (u == bu && v >= s + 2 && v <= s + 5) {
            // Step k sits k blocks below the floor; the floor above the lower steps is left open.
            int step = v - (s + 2);
            out.set(41 - step, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, h.front));
            if (step > 0) out.set(41, Level9Layout.AIR);
            return;
        }
        if (Math.abs(u - bu) <= 1 && v >= s + 2 && v <= s + 6) return; // keep the stairs' foot clear
        int y = CELLAR_FLOOR_Y + 1;
        BlockState piece = null;
        if (u == h.hu0 + 1 && v == back) {
            piece = Level9Layout.facing(ModBlocks.L9_WATER_HEATER.get().defaultBlockState(), h.front);
        } else if (u == h.hu0 + 2 && v == back) {
            piece = Level9Layout.facing(ModBlocks.L9_WASHER.get().defaultBlockState(), h.front);
        } else if (u == h.hu0 + 1 && v == s + 1) {
            piece = chance(h, 9, 0.4) ? ModBlocks.SUPPLY_CRATE.get().defaultBlockState()
                    : Level9Layout.facing(ModBlocks.CARDBOARD_BOXES.get().defaultBlockState(), h.inward);
        } else if (u == h.hu1 - 1 && (v == s + 1 || v == s + 2)) {
            piece = Level9Layout.facing(ModBlocks.CARDBOARD_BOXES.get().defaultBlockState(), House.alongU(false));
        } else if (u == h.hu1 - 1 && v == back) {
            piece = Level9Layout.facing(ModBlocks.STEP_LADDER.get().defaultBlockState(), h.front);
        }
        if (piece != null) {
            out.set(y, piece);
        } else if (cell(h, u, v, 10) < 0.3) {
            out.set(y, ModBlocks.L9_HOUSE_DUST.get().defaultBlockState());
        }
        boolean corner = (u == h.hu0 + 1 || u == h.hu1 - 1) && (v == s + 1 || v == back);
        if (corner && cell(h, u, v, 11) < 0.7) {
            out.set(40, Blocks.COBWEB.defaultBlockState()); // up under the floorboards, above head height
        }
    }

    // ---- Ranch house ------------------------------------------------------------------------------

    /**
     * Ranch house interior: three rooms side by side, each 4 wide, behind two interior walls:
     * living room (front door, sofa, TV), kitchen (counters, fridge, the dining table and its
     * chairs) and a bedroom (a double bed, or a kid's room).
     */
    private static void ranch(House h, int u, int v, Column out) {
        int a = h.hu0;
        int s = h.setback;
        int back = s + h.depth - 2;
        int mid = s + h.depth / 2;
        int wallA = a + 5;
        int wallB = a + 10;
        if (u == wallA || u == wallB) {
            BlockState paper = ModBlocks.DIM_WALLPAPER.get().defaultBlockState();
            for (int y = 42; y <= 45; y++) {
                if (!(v == mid && y <= 43)) out.set(y, paper); // a doorway in the middle of each wall
            }
            return;
        }
        if (u > wallB) out.set(41, ModBlocks.BEIGE_CARPET.get().defaultBlockState()); // carpeted bedroom
        BlockState piece = null;
        BlockState wall = null; // something on the wall at y43
        if (u < wallA) {
            int tvU = h.doorU == a + 3 ? a + 1 : a + 4; // never right inside the front door
            if (v == back && (u == a + 1 || u == a + 2)) {
                piece = Level9Layout.facing(ModBlocks.SOFA.get().defaultBlockState(), h.front);
            } else if (v == s + 1 && u == tvU) {
                piece = Level9Layout.facing(ModBlocks.TV.get().defaultBlockState(), h.inward);
            } else if (h.crate && v == back && u == a + 4) {
                piece = ModBlocks.SUPPLY_CRATE.get().defaultBlockState();
            }
            if (u == a + 1 && v == s + 3) wall = photo(h, 0, House.alongU(true));
        } else if (u < wallB) {
            if (v == back && u >= a + 6 && u <= a + 8) {
                piece = Level9Layout.facing(ModBlocks.KITCHEN_COUNTER.get().defaultBlockState(), h.front);
                if (u == a + 6 && machine(h)) wall = Level9Story.answeringMachine(h.decor, u, v, h.front);
            } else if (v == back && u == a + 9) {
                piece = Level9Layout.facing(ModBlocks.FRIDGE.get().defaultBlockState(), h.front);
            } else if (v == mid - 1 && u == a + 7) {
                piece = Level9Layout.facing(ModBlocks.DINING_TABLE.get().defaultBlockState(), h.front);
            } else if (v == mid - 1 && (u == a + 6 || u == a + 8)) {
                piece = Level9Layout.facing(ModBlocks.WOODEN_CHAIR.get().defaultBlockState(), House.alongU(u == a + 6));
            }
            if (u == a + 9 && v == back - 1 && poster(h)) wall = Level9Story.missingPoster(h.decor, u, v, h.front);
            if (u == a + 7 && v == s + 1) wall = photo(h, 2, h.inward); // front wall, no window at w = 7
        } else if (kids(h)) {
            if (v == back && u == a + 13) {
                piece = Level9Layout.facing(ModBlocks.L9_KIDS_BED.get().defaultBlockState(), h.front);
            } else if (v == back - 1 && u == a + 12 || v == s + 2 && u == a + 11) {
                piece = Level9Layout.facing(ModBlocks.L9_TOYS.get().defaultBlockState(), cell(h, u, v, 5) < 0.5 ? h.inward : House.alongU(true));
            }
            if (u == a + 13 && v == s + 3) wall = Level9Story.kidsDrawing(h.decor, u, v, House.alongU(false));
            if (u == a + 12 && v == back) wall = Level9Story.kidsDrawing(h.decor, u, v, h.front);
        } else {
            if (u == a + 12 && (v == back - 1 || v == back)) {
                piece = bed(h, Level9Layout.facing(ModBlocks.BED.get().defaultBlockState(), h.inward).setValue(HouseBedBlock.HEAD, v == back));
            }
            if (u == a + 13 && v == back) wall = photo(h, 1, h.front); // back wall, no window at w = 13
        }
        place(h, u, v, piece, out);
        if (wall != null) out.set(43, wall);
    }

    // ---- Repeated house ---------------------------------------------------------------------------

    /**
     * The repeated house: a wall across the middle splits it into a front and a back room that
     * are the same in every detail. The middle wall has a door exactly where the front door is
     * and "windows" (trim, as the windows look from inside) exactly where the front windows are;
     * the back wall has a back door and windows in the same places again. Walk through the
     * middle door and you are in the room you just left; walk on and you are out in the back yard.
     */
    private static void repeated(House h, int u, int v, Column out) {
        int s = h.setback;
        int middle = s + 1 + REPEATED_ROOM;
        if (v == middle) {
            BlockState wall = Level9Layout.siding(h.colour);
            for (int y = 42; y <= 45; y++) out.set(y, wall);
            if (frontWindow(h, u)) {
                BlockState trim = ModBlocks.HOUSE_TRIM.get().defaultBlockState();
                out.set(43, trim);
                out.set(44, trim);
            }
            if (u == h.doorU) {
                BlockState door = ModBlocks.HOUSE_DOOR.get().defaultBlockState()
                        .setValue(DoorBlock.FACING, h.inward)
                        .setValue(HouseDoorBlock.LOCKED, false);
                out.set(42, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                out.set(43, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            }
            return;
        }
        // Row inside its room: 0 just inside the door, 3 against the far wall.
        int row = v < middle ? v - (s + 1) : v - (middle + 1);
        int a = h.hu0;
        int b = h.hu1;
        BlockState piece = null;
        if (row == 3 && (u == a + 1 || u == a + 2)) {
            piece = Level9Layout.facing(ModBlocks.SOFA.get().defaultBlockState(), h.front);
        } else if (row == 0 && u == b - 1) {
            piece = Level9Layout.facing(ModBlocks.TV.get().defaultBlockState(), h.inward);
        } else if (row == 2 && u == b - 2) {
            piece = Level9Layout.facing(ModBlocks.DINING_TABLE.get().defaultBlockState(), h.front);
        } else if (row == 2 && (u == b - 3 || u == b - 1)) {
            piece = Level9Layout.facing(ModBlocks.WOODEN_CHAIR.get().defaultBlockState(), House.alongU(u == b - 3));
        }
        place(h, u, v, piece, out);
        if (row == 2 && u == a + 1) out.set(43, photo(h, 0, House.alongU(true)));
    }

    // ==========================================================================================
    // The house's decor (all from its decor hash, so it never changes)
    // ==========================================================================================

    private static boolean chance(House h, int salt, double p) {
        return Hash.unit(Hash.mix(h.decor ^ (salt * 0x9E3779B97F4A7C15L))) < p;
    }

    /** A per-column roll inside one house. */
    private static double cell(House h, int u, int v, int salt) {
        return Hash.unit(Hash.mix(h.decor + u * 0x632BE59BD9B4E019L + v * 0x85157AF5L + salt * 0x5BD1E995L));
    }

    private static boolean tvOn(House h) {
        return h.plan != BOARDED && chance(h, 1, 0.4);
    }

    private static boolean dinner(House h) {
        return h.plan != BOARDED && chance(h, 2, 0.45);
    }

    private static boolean unmade(House h) {
        return h.plan != BOARDED && chance(h, 3, 0.6);
    }

    private static boolean kids(House h) {
        return chance(h, 4, 0.35);
    }

    private static boolean machine(House h) {
        return h.plan != BOARDED && chance(h, 5, 0.25);
    }

    private static boolean poster(House h) {
        return chance(h, 6, 0.10);
    }

    /** Family photo number {@code slot} of this house; some have the faces scratched out. */
    private static BlockState photo(House h, int slot, Direction awayFromWall) {
        double scratched = h.plan == BOARDED ? 0.5 : 0.15;
        return ModBlocks.FAMILY_PHOTO.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, awayFromWall)
                .setValue(FamilyPhotoBlock.PHOTO, Hash.range(Hash.mix(h.decor ^ (slot + 10)), 3))
                .setValue(FamilyPhotoBlock.SCRATCHED, Hash.unit(Hash.mix(h.decor ^ (slot + 20) * 0x632BE59BD9B4E019L)) < scratched);
    }

    // ==========================================================================================
    // Finding houses (the /backrooms house9 test command)
    // ==========================================================================================

    /** How far /backrooms house9 searches (blocks). */
    public static final int LOCATE_RADIUS = 640;

    /**
     * The nearest house of {@code kind} (a plan name, "safe", "clay_door" or "long_hall") to (x, z): where to
     * stand on its front path, two blocks in front of the porch, and which way to look:
     * {x, z, yawDegrees}; null if none is within {@link #LOCATE_RADIUS}. Plans that can have a
     * locked door only count when the door is open.
     */
    @Nullable
    public static int[] locate(Level9Layout layout, long seed, int x, int z, String kind) {
        int plan = PLANS.indexOf(kind);
        if (plan < 0 && !KINDS.contains(kind)) return null;
        int[] best = null;
        long bestDist = Long.MAX_VALUE;
        int lw = Level9Layout.LW;
        // Main streets are about 78 apart; a row or two extra on each side costs nothing.
        for (int r = Math.floorDiv(z - LOCATE_RADIUS, 78) - 1; r <= Math.floorDiv(z + LOCATE_RADIUS, 78) + 1; r++) {
            for (int side = 0; side <= 1; side++) {
                for (int idx = Math.floorDiv(x - LOCATE_RADIUS, lw) - 1; idx <= Math.floorDiv(x + LOCATE_RADIUS, lw) + 1; idx++) {
                    int x0 = layout.lotX0(seed, r, side, idx);
                    int front = layout.lotFrontZ(seed, r, side, x0);
                    long dx = x0 + lw / 2 - x;
                    long dz = front - z;
                    long dist = dx * dx + dz * dz;
                    if (dist >= bestDist || dist > (long) LOCATE_RADIUS * LOCATE_RADIUS || !layout.lotValid(seed, r, side, idx)) {
                        continue;
                    }
                    House h = layout.houseOf(seed, r, side, idx);
                    boolean match = switch (kind) {
                        case "safe" -> h.safe;
                        case "clay_door" -> h.clayDoor;
                        case "long_hall" -> h.longHall;
                        default -> h.plan == plan && h.clipA == null && !h.twin && !h.longHall && (h.unlocked || plan == HALF_BUILT);
                    };
                    if (!match) continue;
                    int v = h.setback - 3;
                    boolean south = side == Level9Layout.SOUTH_OF_STREET;
                    // Minecraft yaw: 0 looks south (+z), 180 looks north.
                    best = new int[] {x0 + h.doorU, south ? front + v : front - v, south ? 0 : 180};
                    bestDist = dist;
                }
            }
        }
        return best;
    }
}
