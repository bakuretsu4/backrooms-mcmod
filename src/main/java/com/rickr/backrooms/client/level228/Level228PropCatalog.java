package com.rickr.backrooms.client.level228;

import com.rickr.backrooms.world.layout.Level228Plan;
import com.rickr.backrooms.world.layout.Level228Plan.Zone;

/**
 * Which meshes draw a prop record of the floor plan ({@link Level228Plan#propsInChunk}): the
 * type picks the object, the variant the zone's style (architecture) or the look (decor), the
 * flags the panel kind. Resolved to mesh indices once per mesh file load, so building a chunk
 * only indexes arrays.
 *
 * <p>Architecture outside the four maze rooms falls back as the plan places it: chamber, foyer,
 * hall and backstage have no columns and no panel frames (their mirrors get their own frames,
 * P_CHAMBER_MIRROR and P_ARRIVAL_MIRROR); walls of the maze rooms borrow the nearest look.
 */
final class Level228PropCatalog {
    private static final Zone[] ZONES = Zone.values();
    /** [zone ordinal] -> mesh index, -1 = nothing. */
    private static int[] column, panel, opening, solid, floor, ceiling;
    /** [zone ordinal][variant 0..3]: 0-1 plain wall faces, 2-3 the rarer ones with a sign or notice. */
    private static int[][] wall;
    private static int bulb, bulbShade, bulbCage, sconce, exitSign, facade, booth, turnstile, stanchion, stanchionFallen, stanchionRope,
            bunting, cobweb, crate, mirrorStack, bucket, banner, chamberMirror, arrivalMirror, curtain, exitDoor, palm, chair,
            emptyShards, ticketStub, chandelier;
    private static int[] posters, notes, shards, litter, leaves;
    private static boolean resolved;

    private Level228PropCatalog() {}

    static void invalidate() {
        resolved = false;
    }

    /** The maze rooms' style name; the other zones have no columns or panel frames. */
    private static String mazeStyle(Zone z) {
        return switch (z) {
            case CRYSTAL -> "crystal";
            case ALHAMBRA -> "alhambra";
            case FUNHOUSE -> "funhouse";
            case GLASS -> "glass";
            default -> null;
        };
    }

    /** Every zone's floor/ceiling look. */
    private static String surfaceStyle(Zone z) {
        return switch (z) {
            case CRYSTAL, NONE -> "crystal";
            case ALHAMBRA -> "alhambra";
            case FUNHOUSE -> "funhouse";
            case GLASS -> "glass";
            case FOYER -> "foyer";
            case CHAMBER -> "chamber";
            case EXIT_HALL -> "exithall";
            case BACKSTAGE -> "backstage";
        };
    }

    /** Walls: the halls have their own; a maze room's wall takes the hall look closest to it. */
    private static String wallStyle(Zone z) {
        return switch (z) {
            case FOYER, FUNHOUSE -> "foyer";
            case CHAMBER, CRYSTAL -> "chamber";
            case EXIT_HALL, ALHAMBRA -> "exithall";
            default -> "backstage";
        };
    }

    private static int idx(String name) {
        return Level228PropMeshes.index(name);
    }

    private static int[] series(String prefix, int n) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = idx(prefix + i);
        }
        return out;
    }

    private static void resolve() {
        int n = ZONES.length;
        column = new int[n];
        panel = new int[n];
        opening = new int[n];
        solid = new int[n];
        floor = new int[n];
        ceiling = new int[n];
        wall = new int[n][4];
        for (Zone z : ZONES) {
            int i = z.ordinal();
            String maze = mazeStyle(z);
            column[i] = maze == null ? -1 : idx("column_" + maze);
            panel[i] = maze == null ? -1 : idx("panel_" + maze);
            opening[i] = maze == null ? -1 : idx("opening_" + maze);
            solid[i] = maze == null ? -1 : idx("solid_" + maze);
            floor[i] = idx("floor_" + surfaceStyle(z));
            ceiling[i] = idx("ceiling_" + surfaceStyle(z));
            for (int v = 0; v < 4; v++) {
                wall[i][v] = idx("wall_" + wallStyle(z) + "_" + v);
            }
            if (wall[i][3] < 0) {
                wall[i][3] = wall[i][2];
            }
        }
        bulb = idx("bulb");
        bulbShade = idx("bulb_shade");
        bulbCage = idx("bulb_cage");
        sconce = idx("sconce");
        exitSign = idx("exit_sign");
        facade = idx("facade");
        booth = idx("ticket_booth");
        turnstile = idx("turnstile");
        stanchion = idx("stanchion");
        stanchionFallen = idx("stanchion_fallen");
        stanchionRope = idx("stanchion_rope");
        bunting = idx("bunting");
        cobweb = idx("cobweb");
        crate = idx("crate");
        mirrorStack = idx("mirror_stack");
        bucket = idx("bucket");
        banner = idx("banner");
        chamberMirror = idx("chamber_mirror");
        arrivalMirror = idx("arrival_mirror");
        curtain = idx("curtain");
        exitDoor = idx("exit_door");
        palm = idx("palm");
        chair = idx("chair");
        ticketStub = idx("ticket_stub");
        chandelier = idx("chandelier");
        emptyShards = idx("panel_empty_shards");
        posters = series("poster_", 6);
        notes = series("note_", 3);
        shards = series("shards_", 3);
        litter = series("litter_", 3);
        leaves = series("leaves_", 2);
        resolved = true;
    }

    private static int zone(int variant) {
        return variant >= 0 && variant < ZONES.length ? variant : Zone.NONE.ordinal();
    }

    private static int pick(int[] series, int variant) {
        return series[Math.floorMod(variant, series.length)];
    }

    /** Kind of a PANEL prop (Level228Plan.PANEL_*). */
    static int panelKind(Level228Plan.Prop p) {
        return (p.flags & Level228Plan.PF_PANEL_MASK) >> Level228Plan.PF_PANEL_SHIFT;
    }

    /**
     * The meshes of one prop into out (at most 3); returns how many. Meshes missing from the file
     * (an older generator run) are skipped.
     */
    static int meshes(Level228Plan.Prop p, int[] out) {
        if (!resolved) {
            resolve();
        }
        int n = 0;
        int z = zone(p.variant);
        switch (p.type) {
            case Level228Plan.P_COLUMN -> n = put(out, n, column[z]);
            case Level228Plan.P_PANEL -> {
                n = put(out, n, panel[z]);
                int kind = panelKind(p);
                if (kind == Level228Plan.PANEL_SOLID) {
                    n = put(out, n, solid[z]);
                } else if (kind == Level228Plan.PANEL_EMPTY_FRAME) {
                    n = put(out, n, emptyShards);
                }
            }
            case Level228Plan.P_OPENING -> n = put(out, n, opening[z]);
            case Level228Plan.P_FLOOR -> n = put(out, n, floor[z]);
            case Level228Plan.P_CEILING -> n = put(out, n, ceiling[z]);
            case Level228Plan.P_WALL -> n = put(out, n, wall[z][wallVariant(p.noise)]);
            case Level228Plan.P_BULB -> n = put(out, n, bulbMesh(p));
            case Level228Plan.P_SCONCE -> n = put(out, n, sconce);
            case Level228Plan.P_EXIT_SIGN -> n = put(out, n, exitSign);
            case Level228Plan.P_FACADE -> n = put(out, n, facade);
            case Level228Plan.P_TICKET_BOOTH -> n = put(out, n, booth);
            case Level228Plan.P_TURNSTILE -> n = put(out, n, turnstile);
            case Level228Plan.P_STANCHION -> {
                n = put(out, n, p.variant == 1 ? stanchionFallen : stanchion);
                // Bit 1: a rope from this post to the next one, two blocks along its +x.
                if (p.variant != 1 && (p.flags & 2) != 0) {
                    n = put(out, n, stanchionRope);
                }
            }
            case Level228Plan.P_POSTER -> n = put(out, n, pick(posters, p.variant));
            case Level228Plan.P_BUNTING -> n = put(out, n, bunting);
            case Level228Plan.P_SHARDS -> n = put(out, n, pick(shards, p.variant));
            case Level228Plan.P_COBWEB -> n = put(out, n, cobweb);
            // Variant 7: the final chamber's single ticket stub (a puzzle detail: one large, plain card).
            case Level228Plan.P_LITTER -> n = put(out, n, p.variant == 7 ? ticketStub : pick(litter, p.variant));
            case Level228Plan.P_NOTE -> n = put(out, n, pick(notes, p.variant));
            case Level228Plan.P_CRATE -> n = put(out, n, crate);
            case Level228Plan.P_MIRROR_STACK -> n = put(out, n, mirrorStack);
            case Level228Plan.P_BUCKET -> n = put(out, n, bucket);
            case Level228Plan.P_BANNER -> n = put(out, n, banner);
            case Level228Plan.P_LEAVES -> n = put(out, n, pick(leaves, p.variant));
            case Level228Plan.P_CHAMBER_MIRROR -> n = put(out, n, chamberMirror);
            case Level228Plan.P_ARRIVAL_MIRROR -> n = put(out, n, arrivalMirror);
            case Level228Plan.P_CURTAIN -> n = put(out, n, curtain);
            case Level228Plan.P_EXIT_DOOR -> n = put(out, n, exitDoor);
            case Level228Plan.P_PALM -> n = put(out, n, palm);
            case Level228Plan.P_CHAIR -> n = put(out, n, chair);
            default -> { }
        }
        return n;
    }

    /**
     * Which of a hall's four wall faces a block shows. Every wall prop is one block wide, so an
     * even pick put a painted sign or notice on two blocks in three and the hallway read as
     * wallpaper of THANK YOU FOR VISITING; plain faces now make up 80 %, each sign 10 %. The
     * noise is hashed because neighbouring blocks' noises differ by small steps. Same pick in
     * tools/level_assets/level228_parts/real.py (wall_variant).
     */
    static int wallVariant(int noise) {
        int h = noise * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA77;
        h ^= h >>> 13;
        int r = (h >>> 8) % 20;
        return r < 8 ? 0 : r < 16 ? 1 : r < 18 ? 2 : 3;
    }

    private static int put(int[] out, int n, int mesh) {
        if (mesh < 0 || n >= out.length) {
            return n;
        }
        out[n] = mesh;
        return n + 1;
    }

    /**
     * The bulb's fitting: a candle-kind bulb is the final chamber's chandelier; otherwise by zone
     * (the variant): the Crystal Maze's bulbs hang in tin shades, the Glass Labyrinth's and the
     * exit hallway's cold lights are caged work lamps wired up after the fact, the rest bare bulbs
     * on flex.
     */
    private static int bulbMesh(Level228Plan.Prop p) {
        int kind = p.flags >> Level228Plan.PF_LIGHT_KIND_SHIFT & 7;
        if (kind == Level228Plan.LIGHT_CANDLE && chandelier >= 0) {
            return chandelier;
        }
        int variant = p.variant;
        if (variant == Zone.CRYSTAL.ordinal()) {
            return bulbShade;
        }
        if ((variant == Zone.GLASS.ordinal() || variant == Zone.EXIT_HALL.ordinal()) && bulbCage >= 0) {
            return bulbCage;
        }
        return bulb;
    }
}
