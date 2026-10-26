package com.rickr.backrooms.client.anomaly;

import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/** What the server told us about anomalies: the site we're in and the ones nearby. Client side. */
public final class AnomalyClientState {
    @Nullable
    private static AnomalySite current;
    private static long enteredMs;
    private static List<AnomalySite> nearby = List.of();

    public static void update(@Nullable AnomalySite site, List<AnomalySite> near) {
        if (!Objects.equals(site, current)) {
            current = site;
            enteredMs = System.currentTimeMillis();
        }
        nearby = List.copyOf(near);
    }

    /** The site the local player is in, or null. */
    @Nullable
    public static AnomalySite current() {
        return current;
    }

    /** True if we're inside a site whose kind is exactly {@code kind}. */
    public static boolean in(String kind) {
        return current != null && current.kind().equals(kind);
    }

    /** Seconds since we entered the current site (0 when outside). */
    public static float secondsInside() {
        return current == null ? 0.0f : (System.currentTimeMillis() - enteredMs) / 1000.0f;
    }

    /** Sites within ~48 blocks (the current one included). */
    public static List<AnomalySite> nearby() {
        return nearby;
    }

    /** Forget everything (logout, leaving the Backrooms). */
    public static void clear() {
        current = null;
        nearby = List.of();
    }

    private AnomalyClientState() {}
}
