package com.rickr.backrooms.pilot;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Dev-only test pilot entry point.
 *
 * Automated play-through tests need to steer a real client without stealing the owner's mouse
 * and keyboard focus. This mod opens a localhost socket and turns JSON requests into ordinary
 * player input (held keys, view direction, clicks) plus telemetry. It never sends custom packets:
 * the server only ever sees what a human player's client would send.
 *
 * It does nothing at all unless a port is configured, so loading it by accident is harmless.
 * It is built from the `pilot` source set, which the release jar never includes.
 */
@Mod(value = PilotMod.MOD_ID, dist = Dist.CLIENT)
public final class PilotMod {
    public static final String MOD_ID = "backrooms_pilot";
    static final Logger LOG = LogUtils.getLogger();

    private final int port;
    private final Pilot pilot = new Pilot();

    public PilotMod(IEventBus modBus) {
        this.port = configuredPort();
        if (port <= 0) {
            return; // inert: no listeners, no socket, no threads
        }
        modBus.addListener(this::onClientSetup);
        NeoForge.EVENT_BUS.addListener(this::onClientTickPre);
    }

    /**
     * The port comes from the JVM property (set by the Gradle run via -PpilotPort) or, failing
     * that, from an environment variable so a harness can enable it without touching build files.
     */
    private static int configuredPort() {
        String raw = System.getProperty("backrooms.pilot.port");
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("BACKROOMS_PILOT_PORT");
        }
        if (raw == null || raw.isBlank()) {
            return -1;
        }
        try {
            int p = Integer.parseInt(raw.trim());
            return p > 0 && p < 65536 ? p : -1;
        } catch (NumberFormatException e) {
            LOG.warn("[pilot] ignoring invalid pilot port '{}'", raw);
            return -1;
        }
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        PilotServer.start(port, pilot);
    }

    private void onClientTickPre(ClientTickEvent.Pre event) {
        pilot.tick();
    }
}
