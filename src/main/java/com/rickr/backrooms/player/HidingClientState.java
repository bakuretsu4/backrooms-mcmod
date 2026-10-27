package com.rickr.backrooms.player;

import com.rickr.backrooms.network.HidingPayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Who is hiding, as last sent by the server ({@link HidingPayload}). Plain fields with no client
 * imports, so the packet handler can write them without loading client classes on a server.
 */
public final class HidingClientState {
    /** Hiders by entity id. Replaced as a whole on every packet, so readers never see a half-update. */
    private static volatile Map<Integer, HidingPayload.Entry> hiders = Map.of();

    /** Called by the packet handler: replace what we knew with the new snapshot. */
    public static void receive(List<HidingPayload.Entry> entries) {
        Map<Integer, HidingPayload.Entry> next = new HashMap<>();
        for (HidingPayload.Entry entry : entries) {
            next.put(entry.entityId(), entry);
        }
        hiders = next;
    }

    /** True if the player with this entity id is hiding. */
    public static boolean isHidden(int entityId) {
        return hiders.containsKey(entityId);
    }

    /** The hider with this entity id, or null. */
    @Nullable
    public static HidingPayload.Entry get(int entityId) {
        return hiders.get(entityId);
    }

    /** True if somebody is hiding in the furniture anchored at this block (packed BlockPos). */
    public static boolean occupied(long anchor) {
        for (HidingPayload.Entry entry : hiders.values()) {
            if (entry.anchor().asLong() == anchor) {
                return true;
            }
        }
        return false;
    }

    /** Forget everything (leaving a world). */
    public static void clear() {
        hiders = Map.of();
    }

    private HidingClientState() {}
}
