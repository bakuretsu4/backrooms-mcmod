package com.rickr.backrooms.world.layout;

/** THROWAWAY test helper: finds things in Level 94 for the in-game screenshot tour. */
public final class Level94Probe {
    /** {tx, tz, castleX, castleZ, footX, footZ} of region (rx, rz)'s town. */
    public static int[] town(long seed, int rx, int rz) {
        Level94Roads.Town t = Level94Roads.town(seed, rx, rz);
        return new int[] {t.tx, t.tz, t.castleX, t.castleZ, t.footX, t.footZ};
    }

    /** First thing of {@code kind} (Level94Builds.HOUSE/TOWER/...) in the plan square around (x, z): {x, z, ground, facing2D, houseX0, houseZ0, w, d, houseKind}. */
    public static int[] thing(long seed, int x, int z, int kind, boolean hillOnly) {
        Level94Plan plan = Level94Plan.of(seed, x, z);
        for (Level94Builds.Thing t : plan.things) {
            if (t == null || t.kind != kind) continue;
            if (hillOnly && t.ground <= LevelLayout.FLOOR_Y + 3) continue;
            Level94Houses.House h = t.house;
            return new int[] {t.x, t.z, t.ground, t.facing.get2DDataValue(),
                    h == null ? 0 : h.x0, h == null ? 0 : h.z0, h == null ? 0 : h.w, h == null ? 0 : h.d,
                    h == null ? -1 : h.kind, h == null ? 0 : h.front.get2DDataValue(), h == null ? 0 : h.g};
        }
        return null;
    }

    private Level94Probe() {}
    /** Dark house of the exit region containing (x, z): {x0, z0, w, d, g, front2D, doorX, doorZ}. */
    public static int[] darkHouse(long seed, int x, int z) {
        int rx = Math.floorDiv(x, 400), rz = Math.floorDiv(z, 400);
        Level94Houses.House h = Level94Plan.darkHouse(seed, rx, rz).house;
        int[] door = Level94Layout.darkDoor(seed, rx, rz);
        return new int[] {h.x0, h.z0, h.w, h.d, h.g, h.front.get2DDataValue(), door[0], door[1]};
    }

}
