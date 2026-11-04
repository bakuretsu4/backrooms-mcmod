package com.rickr.backrooms.client.level228;

import com.rickr.backrooms.Backrooms;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Level 228's prop meshes, read once from {@code models/level228/props.bin} (written with the
 * texture sheets by tools/level_assets/level228_props.py; the format is described in
 * tools/level_assets/level228_parts/sheet.py).
 *
 * <p>A mesh is in prop-local block units: the origin is the minimum corner of the prop's anchor
 * block, y 0 the floor it stands on. Its quads are split into parts by texture sheet and layer:
 * {@link #LAYER_CUT} is static (baked into the chunk buffers by {@link Level228Props}),
 * {@link #LAYER_GLOW} is redrawn every frame with the brightness of its bulb, {@link #LAYER_GLASS}
 * is see-through glass drawn every frame sorted with the mirrors. Everything is kept as flat
 * arrays so turning meshes into vertices never allocates.
 */
public final class Level228PropMeshes {
    public static final ResourceLocation FILE = Backrooms.id("models/level228/props.bin");
    private static final Logger LOGGER = LoggerFactory.getLogger("backrooms/l228props");
    public static final int LAYER_CUT = 0;
    public static final int LAYER_GLOW = 1;
    public static final int LAYER_GLASS = 2;
    /** Emitter kinds as written by the generator (geo.EMIT_*); the same order as Level228Plan.LIGHT_*. */
    public static final int EMIT_BULB = 0;
    public static final int EMIT_CANDLE = 1;
    public static final int EMIT_MARQUEE = 2;
    public static final int EMIT_SIGN = 3;
    public static final int EMIT_COLD = 4;

    /** One sheet + layer worth of a mesh's quads (4 vertices each). */
    public static final class Part {
        public final int sheet;
        public final int layer;
        public final int vertices;
        /** x y z per vertex. */
        public final float[] pos;
        /** u v per vertex (sheet coordinates). */
        public final float[] uv;
        /** nx ny nz per vertex, -1..1. */
        public final float[] normal;
        /** Ambient occlusion per vertex, 0..1. */
        public final float[] shade;
        /** Emitter index per vertex (glow parts). */
        public final byte[] emitter;

        Part(int sheet, int layer, int vertices) {
            this.sheet = sheet;
            this.layer = layer;
            this.vertices = vertices;
            this.pos = new float[vertices * 3];
            this.uv = new float[vertices * 2];
            this.normal = new float[vertices * 3];
            this.shade = new float[vertices];
            this.emitter = new byte[vertices];
        }
    }

    public static final class Mesh {
        public final String name;
        public final float pivotX;
        public final float pivotZ;
        public final float minX, minY, minZ, maxX, maxY, maxZ;
        /** Emitters (bulbs, flames, sign faces): x y z per emitter, prop-local. */
        public final float[] emitterPos;
        public final int[] emitterKind;
        public final float[] emitterSize;
        public final Part[] parts;

        Mesh(String name, float pivotX, float pivotZ, float[] bounds, float[] emitterPos, int[] emitterKind, float[] emitterSize,
             Part[] parts) {
            this.name = name;
            this.pivotX = pivotX;
            this.pivotZ = pivotZ;
            this.minX = bounds[0];
            this.minY = bounds[1];
            this.minZ = bounds[2];
            this.maxX = bounds[3];
            this.maxY = bounds[4];
            this.maxZ = bounds[5];
            this.emitterPos = emitterPos;
            this.emitterKind = emitterKind;
            this.emitterSize = emitterSize;
            this.parts = parts;
        }

        public int emitters() {
            return emitterKind.length;
        }
    }

    /** A named rectangle of a sheet that the game draws by itself (the glow halo). */
    public record Region(int sheet, float u0, float v0, float u1, float v1) {}

    private static String[] sheets = new String[0];
    private static Mesh[] meshes = new Mesh[0];
    private static final Object2IntOpenHashMap<String> BY_NAME = new Object2IntOpenHashMap<>();
    private static final java.util.HashMap<String, Region> REGIONS = new java.util.HashMap<>();
    private static boolean loaded;
    private static boolean failed;

    static {
        BY_NAME.defaultReturnValue(-1);
    }

    private Level228PropMeshes() {}

    /** Loads the meshes if they aren't yet; false if the file is missing or broken (logged once). */
    public static boolean ensureLoaded(ResourceManager resources) {
        if (loaded) {
            return true;
        }
        if (failed) {
            return false;
        }
        try {
            Resource res = resources.getResourceOrThrow(FILE);
            byte[] bytes;
            try (InputStream in = res.open()) {
                bytes = in.readAllBytes();
            }
            parse(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN));
            loaded = true;
            return true;
        } catch (IOException | RuntimeException e) {
            failed = true;
            LOGGER.error("Level 228 props: could not read {}", FILE, e);
            return false;
        }
    }

    /**
     * Loads the meshes from a stream (the unit test reads the shipped file straight from the
     * resources folder, with no resource manager). Replaces whatever was loaded.
     */
    static void load(InputStream in) throws IOException {
        invalidate();
        parse(ByteBuffer.wrap(in.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN));
        loaded = true;
    }

    /** Forget everything (resource reload): read again on next use. */
    public static void invalidate() {
        loaded = false;
        failed = false;
        meshes = new Mesh[0];
        sheets = new String[0];
        BY_NAME.clear();
        REGIONS.clear();
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static int count() {
        return meshes.length;
    }

    public static Mesh get(int index) {
        return meshes[index];
    }

    /** Mesh index by name, -1 if there is none. */
    public static int index(String name) {
        return BY_NAME.getInt(name);
    }

    public static String[] sheetNames() {
        return sheets;
    }

    @Nullable
    public static Region region(String name) {
        return REGIONS.get(name);
    }

    private static String string(ByteBuffer b) {
        int n = b.get() & 0xFF;
        byte[] s = new byte[n];
        b.get(s);
        return new String(s, StandardCharsets.US_ASCII);
    }

    private static void parse(ByteBuffer b) throws IOException {
        if (b.get() != 'L' || b.get() != '2' || b.get() != '2' || b.get() != '8') {
            throw new IOException("not a Level 228 prop file");
        }
        int version = b.get() & 0xFF;
        if (version != 1) {
            throw new IOException("prop file version " + version);
        }
        int nSheets = b.getShort() & 0xFFFF;
        String[] sh = new String[nSheets];
        for (int i = 0; i < nSheets; i++) {
            sh[i] = string(b);
        }
        int nMeshes = b.getShort() & 0xFFFF;
        Mesh[] out = new Mesh[nMeshes];
        Object2IntOpenHashMap<String> names = new Object2IntOpenHashMap<>();
        for (int m = 0; m < nMeshes; m++) {
            String name = string(b);
            float px = b.getFloat();
            float pz = b.getFloat();
            float[] bounds = new float[6];
            for (int k = 0; k < 6; k++) {
                bounds[k] = b.getFloat();
            }
            int nEmit = b.get() & 0xFF;
            float[] ePos = new float[nEmit * 3];
            int[] eKind = new int[nEmit];
            float[] eSize = new float[nEmit];
            for (int e = 0; e < nEmit; e++) {
                ePos[e * 3] = b.getFloat();
                ePos[e * 3 + 1] = b.getFloat();
                ePos[e * 3 + 2] = b.getFloat();
                eKind[e] = b.get() & 0xFF;
                eSize[e] = b.getFloat();
            }
            int nParts = b.get() & 0xFF;
            Part[] parts = new Part[nParts];
            for (int p = 0; p < nParts; p++) {
                int sheet = b.get() & 0xFF;
                int layer = b.get() & 0xFF;
                int quads = b.getInt();
                Part part = new Part(sheet, layer, quads * 4);
                for (int v = 0; v < quads * 4; v++) {
                    part.pos[v * 3] = b.getFloat();
                    part.pos[v * 3 + 1] = b.getFloat();
                    part.pos[v * 3 + 2] = b.getFloat();
                    part.uv[v * 2] = (b.getShort() & 0xFFFF) / 65535.0f;
                    part.uv[v * 2 + 1] = (b.getShort() & 0xFFFF) / 65535.0f;
                    part.normal[v * 3] = b.get() / 127.0f;
                    part.normal[v * 3 + 1] = b.get() / 127.0f;
                    part.normal[v * 3 + 2] = b.get() / 127.0f;
                    part.shade[v] = (b.get() & 0xFF) / 255.0f;
                    part.emitter[v] = b.get();
                }
                parts[p] = part;
            }
            out[m] = new Mesh(name, px, pz, bounds, ePos, eKind, eSize, parts);
            names.put(name, m);
        }
        int nRegions = b.getShort() & 0xFFFF;
        java.util.HashMap<String, Region> regions = new java.util.HashMap<>();
        for (int i = 0; i < nRegions; i++) {
            String name = string(b);
            int sheet = b.get() & 0xFF;
            regions.put(name, new Region(sheet, b.getFloat(), b.getFloat(), b.getFloat(), b.getFloat()));
        }
        sheets = sh;
        meshes = out;
        BY_NAME.clear();
        BY_NAME.putAll(names);
        REGIONS.clear();
        REGIONS.putAll(regions);
    }
}
