package com.rickr.backrooms.world.anomaly;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One anomalous area of a level: what kind it is and which columns it covers.
 *
 * <p>Like the rest of the floor plan this is a pure function of (seed, x, z): a level's
 * {@link AnomalyGrid} decides where each site is, the layout builds its blocks from it in
 * {@code fillColumn}, and the server ({@link AnomalyService}) uses the very same site to make it
 * behave strangely while a player is inside. The client never computes sites (it doesn't know the
 * seed); the server sends it the site you are in and the ones nearby.
 *
 * @param kind    "level/what", e.g. {@code level_0/red_rooms}; behaviours are looked up by it
 * @param minX    first column covered (inclusive)
 * @param minZ    first column covered (inclusive)
 * @param maxX    last column covered (inclusive)
 * @param maxZ    last column covered (inclusive)
 * @param anchorX a point that matters to the kind (entrance, centre, door...), chosen by the planner
 * @param anchorZ see anchorX
 * @param facing  a horizontal direction as {@code Direction.get2DDataValue()} (0 S, 1 W, 2 N, 3 E), or 0
 * @param salt    per-site random bits, for details that vary from site to site
 */
public record AnomalySite(String kind, int minX, int minZ, int maxX, int maxZ, int anchorX, int anchorZ, int facing, long salt) {
    public static final StreamCodec<ByteBuf, AnomalySite> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AnomalySite decode(ByteBuf buf) {
            return new AnomalySite(ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf));
        }

        @Override
        public void encode(ByteBuf buf, AnomalySite site) {
            ByteBufCodecs.STRING_UTF8.encode(buf, site.kind);
            ByteBufCodecs.VAR_INT.encode(buf, site.minX);
            ByteBufCodecs.VAR_INT.encode(buf, site.minZ);
            ByteBufCodecs.VAR_INT.encode(buf, site.maxX);
            ByteBufCodecs.VAR_INT.encode(buf, site.maxZ);
            ByteBufCodecs.VAR_INT.encode(buf, site.anchorX);
            ByteBufCodecs.VAR_INT.encode(buf, site.anchorZ);
            ByteBufCodecs.VAR_INT.encode(buf, site.facing);
            ByteBufCodecs.VAR_LONG.encode(buf, site.salt);
        }
    };

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean contains(double x, double z) {
        return contains((int) Math.floor(x), (int) Math.floor(z));
    }

    /** True if the column is within {@code margin} blocks of the site (a square around it). */
    public boolean near(int x, int z, int margin) {
        return x >= minX - margin && x <= maxX + margin && z >= minZ - margin && z <= maxZ + margin;
    }

    public double centerX() {
        return (minX + maxX + 1) * 0.5;
    }

    public double centerZ() {
        return (minZ + maxZ + 1) * 0.5;
    }

    /** Distance (blocks, sideways) from (x, z) to the nearest column of the site; 0 inside. */
    public double distanceTo(double x, double z) {
        double dx = Math.max(Math.max(minX - x, 0), x - (maxX + 1));
        double dz = Math.max(Math.max(minZ - z, 0), z - (maxZ + 1));
        return Math.sqrt(dx * dx + dz * dz);
    }
}
