package com.rickr.backrooms.client.water;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.BackroomsLevel;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides whether vanilla's water faces are left out of the chunk meshes (asked by
 * {@code mixin/client/LiquidBlockRendererMixin} for every water block it is about to draw).
 *
 * <p>Yes only when ALL of these hold: we are in a level with a {@link WaterStyle} (the Poolrooms,
 * Level 37), our water shader compiled, and no Iris shader pack is running (a shader pack draws
 * its own water, and our pass would not match its picture). Otherwise vanilla water stays, so
 * there is never a level with no visible water at all.
 *
 * <p>The chunk builder calls this from several worker threads at once, so the answer is cached
 * per level in one immutable object that is swapped atomically (a volatile field).
 */
public final class VanillaWaterHider {
    private static final Logger LOGGER = LoggerFactory.getLogger("backrooms/water");

    /** The cached answer for one level object (a new ClientLevel is created on every dimension change). */
    private record Decision(@Nullable ClientLevel level, boolean hide) {}

    private static volatile Decision decision = new Decision(null, false);
    /** Our renderer can draw the water (shader loaded, no shader pack). Set once per tick by WaterRenderer. */
    private static volatile boolean rendererAvailable;

    // Iris is optional: looked up by reflection so we never need it at compile time.
    private static boolean irisLooked;
    /** Asking Iris failed once: from then on assume a pack is on (keeps vanilla water, the safe choice). */
    private static boolean irisFailed;
    @Nullable
    private static Object irisApi;
    @Nullable
    private static Method irisPackInUse;

    /** Called from the chunk builder threads: is vanilla water hidden in the current level? */
    public static boolean hideVanillaWater() {
        ClientLevel level = Minecraft.getInstance().level;
        Decision current = decision;
        if (current.level() != level) {
            current = new Decision(level, decide(level));
            decision = current;
        }
        return current.hide();
    }

    private static boolean decide(@Nullable ClientLevel level) {
        return level != null && rendererAvailable
                && WaterStyle.forLevel(BackroomsLevel.fromDimension(level.dimension())) != null;
    }

    /**
     * Once per client tick (main thread). When our renderer becomes available or unavailable
     * while you are in a pool level, every chunk is rebuilt so vanilla water appears/disappears.
     */
    static void setRendererAvailable(boolean available) {
        if (available == rendererAvailable) {
            return;
        }
        rendererAvailable = available;
        decision = new Decision(null, false); // forget the cached answer
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && WaterStyle.forLevel(BackroomsLevel.fromDimension(mc.level.dimension())) != null) {
            mc.levelRenderer.allChanged();
        }
    }

    /** True while an Iris shader pack is in use (false when Iris isn't installed). */
    static boolean shaderPackInUse() {
        if (irisFailed) {
            return true;
        }
        if (!irisLooked) {
            irisLooked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance").invoke(null);
                irisPackInUse = api.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError e) {
                irisApi = null; // no Iris: nothing to check
            }
        }
        if (irisApi == null || irisPackInUse == null) {
            return false;
        }
        try {
            return (Boolean) irisPackInUse.invoke(irisApi);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("[{}] Could not ask Iris about shader packs; keeping vanilla water", Backrooms.MOD_ID, e);
            irisFailed = true;
            return true;
        }
    }

    private VanillaWaterHider() {}
}
