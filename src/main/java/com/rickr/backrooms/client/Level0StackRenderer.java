package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.layout.Level0Stack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * The floors of Level 0's stack (world/layout/Level0Stack) that aren't there: five are built, and
 * these go on below and above them, the same plan, out of sight. {@value #BELOW} floors down,
 * {@value #ABOVE} up. Each fake floor is the faces you can see of it from the void: below you the
 * carpet tops, the partitions and the outer wall, and the edge of each slab facing the void; above
 * you the ceilings (with their light panels), the partitions, the wall and the slab edges. They
 * darken the further they are from the real floors, into black, and about one in four has its
 * lights out. Built once into a vertex buffer from Level 0's own block textures and drawn as solid
 * blocks are, after them (with the event's own model-view matrix, see Level490ChainTops).
 *
 * <p>The client anomaly effect (SetPieceEffects.Stack) tells this which stack is near every tick.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level0StackRenderer {
    static final int BELOW = 30;
    static final int ABOVE = 24;
    /** Forget the stack when nothing has asked for it for this long (ticks). */
    private static final int FORGET_TICKS = 40;

    @Nullable
    private static AnomalySite site;
    @Nullable
    private static AnomalySite built;
    @Nullable
    private static VertexBuffer buffer;
    private static int sinceAsked;

    private Level0StackRenderer() {}

    /** Called every tick while a stack is near. */
    public static void near(AnomalySite stack) {
        site = stack;
        sinceAsked = 0;
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean onLevel0 = mc.level != null && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_0;
        if (!onLevel0 || site == null || ++sinceAsked > FORGET_TICKS * 3) {
            release();
            return;
        }
        if (built == null || !built.equals(site)) {
            release();
            buffer = build(mc, site);
            built = site;
        }
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f mv = new Matrix4f(event.getModelViewMatrix())
                .translate((float) (built.minX() - cam.x), (float) -cam.y, (float) (built.minZ() - cam.z));
        RenderType solid = RenderType.solid();
        solid.setupRenderState();
        buffer.bind();
        buffer.drawWithShader(mv, event.getProjectionMatrix(), GameRenderer.getRendertypeSolidShader());
        VertexBuffer.unbind();
        solid.clearRenderState();
    }

    private static void release() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
        built = null;
        if (sinceAsked > FORGET_TICKS * 3) {
            site = null;
        }
    }

    // ---- Building ------------------------------------------------------------------------------------

    private record Sprites(TextureAtlasSprite carpet, TextureAtlasSprite paper, TextureAtlasSprite base,
                           TextureAtlasSprite tile, TextureAtlasSprite panel) {}

    private static TextureAtlasSprite sprite(Minecraft mc, Block block) {
        return mc.getBlockRenderer().getBlockModel(block.defaultBlockState()).getParticleIcon();
    }

    private static VertexBuffer build(Minecraft mc, AnomalySite s) {
        Sprites sp = new Sprites(sprite(mc, ModBlocks.LOBBY_CARPET.get()), sprite(mc, ModBlocks.YELLOW_WALLPAPER.get()),
                sprite(mc, ModBlocks.YELLOW_WALLPAPER_BASEBOARD.get()), sprite(mc, ModBlocks.CEILING_TILE.get()),
                sprite(mc, ModBlocks.FLUORESCENT_LIGHT.get()));
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        for (int k = 1; k <= BELOW; k++) {
            floor(buf, sp, s, Level0Stack.LOWEST_REAL - k * Level0Stack.STORY, k, true);
        }
        for (int k = 1; k <= ABOVE; k++) {
            floor(buf, sp, s, Level0Stack.HIGHEST_REAL_CEILING + 1 + (k - 1) * Level0Stack.STORY, k, false);
        }
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        MeshData mesh = buf.build();
        vb.bind();
        if (mesh != null) {
            vb.upload(mesh);
        }
        VertexBuffer.unbind();
        return vb;
    }

    /** Ring offset {m} of column (x, z), -1 outside the box. */
    private static int m(AnomalySite s, int x, int z) {
        int[] l = Level0Stack.local(s, x, z);
        return l == null ? -1 : l[2];
    }

    private static boolean partitionAt(AnomalySite s, int f, int x, int z) {
        int[] l = Level0Stack.local(s, x, z);
        return l != null && l[2] >= Level0Stack.RING_MIN && l[2] <= Level0Stack.RING_MAX
                && Level0Stack.partition(s.salt(), f, l[3], l[4]) && !Level0Stack.partitionDoor(l[2]);
    }

    private static final int[][] SIDES = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

    /**
     * One fake floor whose carpet layer is at y {@code f}, {@code k} floors from the real ones.
     * {@code below}: seen from above (carpet tops), else from below (ceilings at f + 4).
     */
    private static void floor(BufferBuilder buf, Sprites sp, AnomalySite s, int f, int k, boolean below) {
        long h = Hash.mix(s.salt() ^ (f * 0x9E3779B97F4A7C15L));
        boolean dark = Hash.unit(h) < 0.25;
        float fade = (float) Math.exp(-k / (below ? 9.0 : 7.0));
        float bright = fade * (dark ? 0.32f : 1.0f);
        int lit = LightTexture.pack(15, 0);
        int glow = LightTexture.pack(15, 15);
        int x0 = s.minX();
        int z0 = s.minZ();
        for (int x = s.minX(); x <= s.maxX(); x++) {
            for (int z = s.minZ(); z <= s.maxZ(); z++) {
                int m = m(s, x, z);
                if (m < 0) {
                    continue;
                }
                int lx = x - x0;
                int lz = z - z0;
                boolean ring = m >= Level0Stack.RING_MIN && m <= Level0Stack.RING_MAX;
                if (ring) {
                    boolean part = partitionAt(s, f, x, z);
                    if (below && !part) {
                        face(buf, sp.carpet, lit, lx, f, lz, 0, 1, 0, bright);
                    }
                    if (!below) {
                        boolean panel = !dark && Math.floorMod(x + z, 4) == 1 && (m == 9 || m == 12) && Math.floorMod(x - z, 4) == 0;
                        face(buf, panel ? sp.panel : sp.tile, panel ? glow : lit, lx, f + 4, lz, 0, -1, 0,
                                panel ? Math.max(bright, 0.4f) : bright);
                    }
                    for (int[] d : SIDES) {
                        int nm = m(s, x + d[0], z + d[1]);
                        if (nm >= 0 && nm <= Level0Stack.VOID_MAX) {
                            // The slab's edge at the void: its carpet layer and the tile layer of the other side.
                            int other = below ? f - 1 : f + 4;
                            face(buf, sp.carpet, lit, lx, f, lz, d[0], 0, d[1], bright * 0.9f);
                            face(buf, sp.tile, lit, lx, other, lz, d[0], 0, d[1], bright * 0.55f);
                        }
                        if (part && (nm <= Level0Stack.VOID_MAX || !partitionAt(s, f, x + d[0], z + d[1]))) {
                            for (int y = f + 1; y <= f + 3; y++) {
                                face(buf, y == f + 1 ? sp.base : sp.paper, lit, lx, y, lz, d[0], 0, d[1], bright);
                            }
                        }
                    }
                } else if (m >= Level0Stack.WALL_MIN) {
                    for (int[] d : SIDES) {
                        int nm = m(s, x + d[0], z + d[1]);
                        if (nm >= Level0Stack.RING_MIN && nm <= Level0Stack.RING_MAX) {
                            for (int y = f + 1; y <= f + 3; y++) {
                                face(buf, y == f + 1 ? sp.base : sp.paper, lit, lx, y, lz, d[0], 0, d[1], bright);
                            }
                            // Between the stories the wall goes on behind the slab: nothing to see there.
                        }
                    }
                }
            }
        }
    }

    /** One face of the unit block at (x, y, z), facing (nx, ny, nz), shaded as Minecraft shades block faces. */
    private static void face(BufferBuilder buf, TextureAtlasSprite s, int light, int x, int y, int z, int nx, int ny, int nz,
                             float bright) {
        float shade = bright * (ny == 1 ? 1.0f : ny == -1 ? 0.5f : nz != 0 ? 0.8f : 0.6f);
        float[][] c;
        if (ny == 1) {
            c = new float[][] {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
        } else if (ny == -1) {
            c = new float[][] {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}};
        } else if (nz == -1) {
            c = new float[][] {{1, 0, 0}, {0, 0, 0}, {0, 1, 0}, {1, 1, 0}};
        } else if (nz == 1) {
            c = new float[][] {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}};
        } else if (nx == -1) {
            c = new float[][] {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}};
        } else {
            c = new float[][] {{1, 0, 1}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1}};
        }
        float[][] uv = {{s.getU0(), s.getV1()}, {s.getU1(), s.getV1()}, {s.getU1(), s.getV0()}, {s.getU0(), s.getV0()}};
        for (int i = 0; i < 4; i++) {
            buf.addVertex(x + c[i][0], y + c[i][1], z + c[i][2])
                    .setColor(shade, shade, shade, 1.0f)
                    .setUv(uv[i][0], uv[i][1])
                    .setLight(light)
                    .setNormal(nx, ny, nz);
        }
    }
}
