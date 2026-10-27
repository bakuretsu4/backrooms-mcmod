package com.rickr.backrooms.client;

import com.rickr.backrooms.client.light.FixtureLight;
import com.rickr.backrooms.client.light.FixtureType;
import com.rickr.backrooms.world.layout.Level39Layout;
import com.rickr.backrooms.world.layout.Level39Superstructure;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * Veil lights for Level 39's superstructure facade (client side).
 *
 * <p>The facade has thousands of glowing window blocks; making each one a light fixture would
 * swamp the light index and the budget. Instead, while you are near a facade, we place one cool
 * white light two blocks in front of each window of the lowest storeys near you, worked out from
 * the facade's fixed bay/storey grid ({@link Level39Layout}). They join the normal fixture list
 * ({@code client/light/BackroomsLights}), so the usual budget picks the nearest few: walking along
 * the wall, the street is washed in strong cool light from the windows beside you, with soft
 * shadows of fences and poles, exactly like the glow on the yard in the photo.
 *
 * <p>The tunnel orbs ({@code l39_orb_light}) and the occasional lit house window
 * ({@code l39_sash_window_lit}) are real blocks and are found like every other fixture (see
 * {@link FixtureType#L39_ORB} and {@link FixtureType#L39_LIT_WINDOW}).
 */
public final class Level39Lights {
    /** Facade lights are made when you are within this many blocks of the glass. */
    private static final int REACH = 56;
    /** ... for the windows this far either way along the facade from you ... */
    private static final int ALONG_REACH = 36;
    /** ... on the lowest this-many storeys (the higher ones are too far up to light the street). */
    private static final int STOREYS = 4;
    /** The lights hang this far in front of the pier plane (into the town), so nothing blocks them. */
    private static final int OUT = 2;

    /**
     * Adds the facade window lights around block (x, z) of Level 39 to {@code out}. Called when the
     * fixture list is rebuilt (every few blocks you walk), so it can afford a small loop.
     */
    public static void addFacadeLights(List<FixtureLight> out, int x, int z) {
        if (!Level39Layout.inTown(x, z) || Level39Layout.facadeDistance(x, z) < -REACH) {
            return;
        }
        int lx = Level39Layout.local(x);
        int lz = Level39Layout.local(z);
        int cellX = x - lx;
        int cellZ = z - lz;
        int pier = Level39Layout.TOWN_HALF;
        // +x / -x facades (along = local z), then +z / -z (along = local x).
        if (pier - Math.abs(lx) <= REACH) {
            addRow(out, true, cellX, cellZ, lx >= 0 ? 1 : -1, lz);
        }
        if (pier - Math.abs(lz) <= REACH) {
            addRow(out, false, cellX, cellZ, lz >= 0 ? 1 : -1, lx);
        }
    }

    /** The windows of one facade ({@code side} +1 / -1) near {@code alongCentre}, on the lowest storeys. */
    private static void addRow(List<FixtureLight> out, boolean xFace, int cellX, int cellZ, int side, int alongCentre) {
        int from = Math.max(-(Level39Layout.TOWN_HALF - 1), alongCentre - ALONG_REACH);
        int to = Math.min(Level39Layout.TOWN_HALF - 1, alongCentre + ALONG_REACH);
        // The lights hang OUT blocks in front of the pilasters; the glass is one block behind them.
        int lightPlane = side * (Level39Layout.TOWN_HALF - OUT);
        int glassPlane = side * (Level39Layout.TOWN_HALF + 1);
        boolean seed = Level39ClientState.hasSeed();
        for (int along = from; along <= to; along++) {
            // Window centres: the middle (offset 3) of the 3 window columns (offsets 2, 3, 4) of each bay.
            if (Math.floorMod(along, Level39Layout.BAY) != 3) {
                continue;
            }
            for (int k = 0; k < STOREYS; k++) {
                // Storey k: string course at 73+7k, spandrel above it, glass rows 2-6 above that.
                int glassMid = Level39Layout.GROUND_Y + 1 + k * Level39Layout.STOREY + 4;
                int gx = cellX + (xFace ? glassPlane : along);
                int gz = cellZ + (xFace ? along : glassPlane);
                // No light where there is no lit glass: stair towers, boarded or bricked windows.
                // (Before the seed arrives the boarded ones can't be told apart: lit for now.)
                if (seed ? !Level39Superstructure.facadeWindowAt(Level39ClientState.seed(), gx, glassMid, gz)
                        : Level39Superstructure.inStairTower(gx, gz)) {
                    continue;
                }
                int wx = cellX + (xFace ? lightPlane : along);
                int wz = cellZ + (xFace ? along : lightPlane);
                long key = BlockPos.asLong(wx, glassMid, wz);
                double cx = wx + 0.5;
                double cy = glassMid + 0.5;
                double cz = wz + 0.5;
                out.add(new FixtureLight(key, FixtureType.L39_FACADE_WINDOW, cx, cy, cz, cx, cy, cz, null, false));
            }
        }
    }

    private Level39Lights() {}
}
