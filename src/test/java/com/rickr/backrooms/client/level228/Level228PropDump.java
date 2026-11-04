package com.rickr.backrooms.client.level228;

import com.rickr.backrooms.world.layout.Level228Plan;
import com.rickr.backrooms.world.layout.Level228Plan.Zone;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dumps the real floor plan's props and mirrors to JSON so the asset generator's previewer
 * ({@code tools/level_assets/level228_props.py --real <json> <dir>}) renders scenes exactly as
 * the game would place them, instead of a hand-made test room.
 *
 * <p>A plain {@code main}, not a JUnit test: {@link Level228Plan} is pure, so this runs straight
 * on the compiled classes without bootstrapping the game (seconds instead of minutes):
 * {@code java -cp build/classes/java/main;build/classes/java/test
 * com.rickr.backrooms.client.level228.Level228PropDump out.json [seed]}.
 */
public final class Level228PropDump {
    /** The plan's grid, generously (it is MIN -64, 256 x 192 today); empty chunks cost nothing. */
    static final int CHUNK_MIN = -8;
    static final int CHUNK_MAX = 16;

    private Level228PropDump() {}

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "l228_layout.json");
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 228L;
        Level228Plan.ShiftState s = Level228Plan.ShiftState.INITIAL;
        Level228Plan.Prop[] props = new Level228Plan.Prop[4096];
        for (int i = 0; i < props.length; i++) {
            props[i] = new Level228Plan.Prop();
        }
        StringBuilder b = new StringBuilder(1 << 20);
        b.append("{\"seed\":").append(seed).append(",\n\"props\":[");
        boolean first = true;
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int cx = CHUNK_MIN; cx < CHUNK_MAX; cx++) {
            for (int cz = CHUNK_MIN; cz < CHUNK_MAX; cz++) {
                int n = Level228Plan.propsInChunk(seed, s, cx, cz, props);
                for (int k = 0; k < n; k++) {
                    Level228Plan.Prop p = props[k];
                    b.append(first ? "\n" : ",\n").append('[').append(p.type).append(',').append(p.variant).append(',')
                            .append(p.x).append(',').append(p.y).append(',').append(p.z).append(',').append(p.rot).append(',')
                            .append(p.flags).append(',').append(p.aux).append(',').append(p.noise).append(']');
                    first = false;
                    minX = Math.min(minX, p.x);
                    minZ = Math.min(minZ, p.z);
                    maxX = Math.max(maxX, p.x);
                    maxZ = Math.max(maxZ, p.z);
                }
            }
        }
        b.append("],\n\"mirrors\":[");
        Level228Plan.Mirror[] mirrors = new Level228Plan.Mirror[8192];
        for (int i = 0; i < mirrors.length; i++) {
            mirrors[i] = new Level228Plan.Mirror();
        }
        int nm = Level228Plan.mirrorsNear(seed, s, (minX + maxX) * 0.5, (minZ + maxZ) * 0.5,
                Math.max(maxX - minX, maxZ - minZ) + 16.0, mirrors);
        for (int k = 0; k < nm; k++) {
            Level228Plan.Mirror m = mirrors[k];
            b.append(k == 0 ? "\n" : ",\n").append('[').append(m.id).append(',').append(m.axis).append(',').append(m.plane)
                    .append(',').append(m.min).append(',').append(m.max).append(',').append(m.y0).append(',').append(m.y1)
                    .append(',').append(m.kind).append(',').append(m.surface).append(',').append(m.zone.ordinal()).append(',')
                    .append(m.flags).append(',').append(m.front).append(',').append(m.noise).append(']');
        }
        b.append("],\n\"starts\":{");
        Zone[] zones = Zone.values();
        boolean firstZone = true;
        for (Zone z : zones) {
            if (z == Zone.NONE) {
                continue;
            }
            double[] st = Level228Plan.zoneStart(seed, z);
            b.append(firstZone ? "" : ",").append("\n\"").append(z.name()).append("\":[").append(st[0]).append(',').append(st[1])
                    .append(',').append(st[2]).append(',').append(st[3]).append(']');
            firstZone = false;
        }
        int[] ch = Level228Plan.chamberCentre(seed);
        int[] door = Level228Plan.exitDoor(seed);
        b.append("},\n\"chamber\":[").append(ch[0]).append(',').append(ch[1]).append("],\"door\":[").append(door[0]).append(',')
                .append(door[1]).append("],\n\"bounds\":[").append(minX).append(',').append(minZ).append(',').append(maxX).append(',')
                .append(maxZ).append("],\n");
        // Zone and walkability rows, one character per block, so the previewer can frame shots.
        b.append("\"zone_rows\":[");
        for (int z = minZ; z <= maxZ; z++) {
            b.append(z == minZ ? "\n\"" : ",\n\"");
            for (int x = minX; x <= maxX; x++) {
                Zone zz = Level228Plan.zoneAt(seed, x, z);
                boolean walk = Level228Plan.isWalkable(seed, s, x, Level228Plan.FEET_Y, z);
                b.append(walk ? (char) ('A' + zz.ordinal()) : zz == Zone.NONE ? '.' : (char) ('a' + zz.ordinal()));
            }
            b.append('"');
        }
        b.append("]}\n");
        try (Writer w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write(b.toString());
        }
        System.out.println("wrote " + out + ": props+mirrors " + nm + " mirrors, bounds " + minX + "," + minZ + ".." + maxX + "," + maxZ);
    }
}
