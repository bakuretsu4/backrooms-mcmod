package com.rickr.backrooms.client.level228;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.jetbrains.annotations.Nullable;

/**
 * The two things Level 228's prop renderer needs to know from Iris, through its public API by
 * reflection (Iris is optional): whether a shader pack is on (its entity programs then expect
 * Iris's extended vertex format, so the cached prop buffers are rebuilt when this changes), and
 * whether the shadow pass is being drawn (our draws use the main camera's matrices, so they must
 * not land in the shadow map).
 */
final class Level228Iris {
    @Nullable
    private static final Object API;
    @Nullable
    private static final MethodHandle PACK_IN_USE;
    @Nullable
    private static final MethodHandle SHADOW_PASS;

    static {
        Object api = null;
        MethodHandle pack = null;
        MethodHandle shadow = null;
        try {
            Class<?> cls = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = cls.getMethod("getInstance").invoke(null);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            pack = lookup.findVirtual(cls, "isShaderPackInUse", MethodType.methodType(boolean.class)).bindTo(api);
            shadow = lookup.findVirtual(cls, "isRenderingShadowPass", MethodType.methodType(boolean.class)).bindTo(api);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            api = null; // no Iris: plain Veil / vanilla
        }
        API = api;
        PACK_IN_USE = pack;
        SHADOW_PASS = shadow;
    }

    private Level228Iris() {}

    static boolean packInUse() {
        if (PACK_IN_USE == null) {
            return false;
        }
        try {
            return (boolean) PACK_IN_USE.invokeExact();
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean shadowPass() {
        if (SHADOW_PASS == null) {
            return false;
        }
        try {
            return (boolean) SHADOW_PASS.invokeExact();
        } catch (Throwable t) {
            return false;
        }
    }
}
