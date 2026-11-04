package com.rickr.backrooms.client.level228;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rickr.backrooms.world.layout.Level228Plan;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Every prop the floor plan can emit must have its meshes in the shipped props.bin: the
 * renderer silently skips a missing mesh (so an old generator run never crashes the game), which
 * means a gap would only ever show up as an invisible column or an empty frame in game. This
 * walks the whole finite maze for several seeds and shift states and resolves every record
 * through the same catalog the renderer uses.
 */
class Level228PropCoverageTest {
    private static final long[] SEEDS = {228L, 0x5EEDL, -987654321L};
    private static final String RESOURCE = "/assets/backrooms/models/level228/props.bin";

    @BeforeAll
    static void load() throws IOException {
        InputStream in = Level228PropCoverageTest.class.getResourceAsStream(RESOURCE);
        if (in == null) {
            // Not on the test classpath (an IDE run): read it from the source tree.
            in = Files.newInputStream(Path.of("src/main/resources" + RESOURCE));
        }
        try (InputStream s = in) {
            Level228PropMeshes.load(s);
        }
        Level228PropCatalog.invalidate();
        assertTrue(Level228PropMeshes.count() > 0, "props.bin has no meshes");
    }

    /** How many meshes a record must resolve to (the catalog adds companions for some kinds). */
    private static int expected(Level228Plan.Prop p) {
        return switch (p.type) {
            case Level228Plan.P_PANEL -> {
                int kind = Level228PropCatalog.panelKind(p);
                if (kind == Level228Plan.PANEL_MIRROR && !mazeZone(p.variant)) {
                    // The foyer's arrival mirror, the chamber's three and the hallway's entry mirror
                    // are framed by their own props (P_ARRIVAL_MIRROR, P_CHAMBER_MIRROR); their
                    // P_PANEL records only carry the pane, which Level228MirrorSurfaces draws.
                    yield 0;
                }
                yield kind == Level228Plan.PANEL_SOLID || kind == Level228Plan.PANEL_EMPTY_FRAME ? 2 : 1;
            }
            case Level228Plan.P_STANCHION -> p.variant != 1 && (p.flags & 2) != 0 ? 2 : 1;
            default -> 1;
        };
    }

    private static boolean mazeZone(int variant) {
        return variant >= Level228Plan.Zone.CRYSTAL.ordinal() && variant <= Level228Plan.Zone.GLASS.ordinal();
    }

    private static Level228Plan.ShiftState[] states(long seed) {
        int bits = Level228Plan.bitCount();
        Level228Plan.ShiftState all = Level228Plan.ShiftState.INITIAL;
        for (int b = 0; b < bits; b++) {
            all = all.with(b, true);
        }
        return new Level228Plan.ShiftState[] {Level228Plan.ShiftState.INITIAL, all};
    }

    @Test
    void everyPlannedPropHasItsMeshes() {
        Level228Plan.Prop[] props = new Level228Plan.Prop[4096];
        for (int i = 0; i < props.length; i++) {
            props[i] = new Level228Plan.Prop();
        }
        int[] meshes = new int[4];
        Map<String, Integer> missing = new TreeMap<>();
        Map<Integer, Integer> seen = new TreeMap<>();
        int total = 0;
        int chandeliers = 0;
        for (long seed : SEEDS) {
            for (Level228Plan.ShiftState s : states(seed)) {
                for (int cx = Level228PropDump.CHUNK_MIN; cx < Level228PropDump.CHUNK_MAX; cx++) {
                    for (int cz = Level228PropDump.CHUNK_MIN; cz < Level228PropDump.CHUNK_MAX; cz++) {
                        int n = Level228Plan.propsInChunk(seed, s, cx, cz, props);
                        assertTrue(n < props.length, "chunk " + cx + "," + cz + " filled the whole prop array");
                        for (int k = 0; k < n; k++) {
                            Level228Plan.Prop p = props[k];
                            total++;
                            seen.merge(p.type, 1, Integer::sum);
                            int got = Level228PropCatalog.meshes(p, meshes);
                            if (got < expected(p)) {
                                String key = "type " + p.type + " variant " + p.variant + " flags " + p.flags
                                        + " (resolved " + got + "/" + expected(p) + ")";
                                missing.merge(key, 1, Integer::sum);
                            }
                            for (int m = 0; m < got; m++) {
                                assertNotNull(Level228PropMeshes.get(meshes[m]));
                            }
                            // The chamber's candle pendant must get its chandelier, not the
                            // bare-bulb fallback the catalog uses when a mesh is missing.
                            if (p.type == Level228Plan.P_BULB
                                    && (p.flags >> Level228Plan.PF_LIGHT_KIND_SHIFT & 7) == Level228Plan.LIGHT_CANDLE) {
                                chandeliers++;
                                assertEquals("chandelier", Level228PropMeshes.get(meshes[0]).name);
                            }
                        }
                    }
                }
            }
        }
        assertTrue(total > 1000, "the plan placed suspiciously few props: " + total);
        assertTrue(chandeliers > 0, "no candle pendant found in the final chamber");
        assertEquals(Map.of(), missing, "props without meshes (types seen: " + seen + ")");
    }

    @Test
    void everyCatalogueTypeHasAMesh() {
        // Types the plan may not place in every seed still need a mesh (anomalies, rare decor).
        int[] meshes = new int[4];
        Level228Plan.Prop p = new Level228Plan.Prop();
        for (int type = Level228Plan.P_COLUMN; type <= Level228Plan.P_CHAIR; type++) {
            int zones = type <= Level228Plan.P_CEILING ? 4 : 1;
            for (int z = 0; z < zones; z++) {
                p.type = type;
                p.variant = z + 1; // CRYSTAL..GLASS for the architecture, variant 1 otherwise
                p.flags = 0;
                p.noise = 0;
                assertTrue(Level228PropCatalog.meshes(p, meshes) > 0, "no mesh for type " + type + " variant " + p.variant);
            }
        }
    }
}
