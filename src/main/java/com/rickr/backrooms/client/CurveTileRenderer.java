package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.TileCurveBlock;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Draws Level 37's curved tile ({@link TileCurveBlock}: round pillars, rounded corners, arches,
 * tunnel rings, rotundas) into the chunk meshes with lighting that follows the curve. All the work
 * happens when a chunk section is (re)built, never per frame.
 *
 * <p><b>Why not let Minecraft light them like any block?</b> Its block lighting is made for the six
 * sides of a cube, and one curve piece fills a whole block:
 * <ul>
 *   <li>Flat lighting (the curve models had ambient occlusion switched off) gives every face of a
 *       block ONE light value, the light in the block's own cell. A curved ceiling became a
 *       patchwork, one brightness per block; in Level 37's dim tunnels a single light level is a big
 *       step, so the far end of a tunnel showed big dark blocks.</li>
 *   <li>Smooth lighting picks the cube side a face is closest to, samples the four cells around it
 *       and hands the four values to the face's corners by their order in the model. On a tilted
 *       face they land on the wrong corners (dark triangles), and the solid tile behind the curve
 *       counts as a wall right in front of it (dark blocks).</li>
 *   <li>Face shading only knows the six sides, so the curve can't fade from one wall's shade to the
 *       next.</li>
 * </ul>
 *
 * <p><b>What this does instead</b>, for every corner (vertex) of every face of a piece:
 * <ul>
 *   <li><b>Light</b>: the light of the cells half a block in front of the surface (along its
 *       normal), blended by how close they are. Solid cells and cells behind the surface don't
 *       count. On a flat wall this is exactly vanilla smooth lighting's average of four cells, so a
 *       curve agrees with the flat wall it runs into, and two pieces sharing an edge work out the
 *       same value there: no seams, no per-block patches. Light levels between whole numbers are
 *       fine: the lightmap blends between its entries.</li>
 *   <li><b>Ambient occlusion</b>: the same blend of vanilla's "shade brightness" per cell (solid
 *       blocks 0.2, anything else 1.0), so the foot and the top of a curve darken toward the floor
 *       and ceiling exactly like the wall beside it.</li>
 *   <li><b>Face shading</b>: vanilla shades the top 1.0, north/south 0.8, east/west 0.6 and the
 *       bottom 0.5. A curve gets the mix for the way it faces (each side's value weighted by the
 *       normal's share squared): a rounded corner between an east wall and a north wall fades from
 *       0.6 to 0.8, a vault darkens toward its crown. The six values come from the level, so when a
 *       shader pack switches face shading off (Iris) it is off here too and the pack shades by the
 *       normals instead.</li>
 * </ul>
 * With smooth lighting OFF the curves are flat-lit like every other block then: one light per face
 * (the cell the face looks into), no ambient occlusion.
 *
 * <p><b>How it is hooked in</b>: the block has {@code RenderShape.INVISIBLE}, so the normal chunk
 * mesher skips it, and {@link AddSectionGeometryEvent} lets this class add the pieces to the same
 * chunk mesh (solid layer). Sodium runs these renderers too, so it works under Iris; Iris works out
 * its own per-face normals from the positions, which is why the curved faces are thin strips (about
 * 0.1 block). The shapes, texture coordinates and normals still come from the OBJ models written by
 * {@code tools/gen_assets.py}, read from the baked block model.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class CurveTileRenderer {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Predicate<BlockState> IS_PIECE = state -> state.getBlock() instanceof TileCurveBlock;
    /** Weights smaller than this count as "nothing there". */
    private static final float EPSILON = 1.0e-4f;

    private CurveTileRenderer() {}

    /** Main thread, whenever a section is queued for (re)building: add our renderer if it has curve pieces. */
    @SubscribeEvent
    public static void onAddSectionGeometry(AddSectionGeometryEvent event) {
        BlockPos origin = event.getSectionOrigin().immutable();
        if (mayHavePieces(event.getLevel(), origin)) {
            event.addRenderer(context -> new Mesher(context).section(origin));
        }
    }

    /** Cheap check on the section's palette, so sections without curves cost nothing. */
    private static boolean mayHavePieces(Level level, BlockPos origin) {
        int index = level.getSectionIndex(origin.getY());
        if (index < 0 || index >= level.getSectionsCount()) {
            return false;
        }
        LevelChunk chunk = level.getChunkSource().getChunk(SectionPos.blockToSectionCoord(origin.getX()),
                SectionPos.blockToSectionCoord(origin.getZ()), false);
        if (chunk == null) {
            return true; // can't tell from here; the renderer just looks
        }
        LevelChunkSection section = chunk.getSection(index);
        return !section.hasOnlyAir() && section.maybeHas(IS_PIECE);
    }

    /** One section's worth of drawing, on a chunk builder thread. */
    private static final class Mesher {
        private final AddSectionGeometryEvent.SectionRenderingContext context;
        private final BlockAndTintGetter region;
        private final PoseStack.Pose pose;
        private final BlockModelShaper models = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper();
        private final RandomSource random = RandomSource.create();
        private final boolean smooth = Minecraft.useAmbientOcclusion();
        /** The level's face shading per side (Direction.get3DDataValue order); all 1.0 under a shader pack. */
        private final float[] sideShade = new float[6];
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        private final BlockPos.MutableBlockPos cellPos = new BlockPos.MutableBlockPos();
        // The 3 x 3 x 3 cells around the piece being drawn, index (dx + 1) * 9 + (dy + 1) * 3 + (dz + 1).
        private final float[] cellSky = new float[27];
        private final float[] cellBlock = new float[27];
        /** Vanilla's ambient occlusion value of each cell: 0.2 for solid blocks, 1.0 otherwise. */
        private final float[] cellOpen = new float[27];
        /**
         * Cells whose light doesn't count: solid blocks without light of their own (the light engine
         * keeps 0 in them). A glowing block still counts, as in vanilla's smooth lighting.
         */
        private final boolean[] cellNoLight = new boolean[27];
        private final Vector3f position = new Vector3f();
        private final Vector3f normal = new Vector3f();
        private VertexConsumer out;
        // Result of light(): sky and block light (0..15, fractional) and the ambient occlusion.
        private float litSky;
        private float litBlock;
        private float litAo;

        Mesher(AddSectionGeometryEvent.SectionRenderingContext context) {
            this.context = context;
            this.region = context.getRegion();
            this.pose = context.getPoseStack().last();
            for (Direction side : DIRECTIONS) {
                sideShade[side.get3DDataValue()] = region.getShade(side, true);
            }
        }

        void section(BlockPos origin) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                        BlockState state = region.getBlockState(pos);
                        if (state.getBlock() instanceof TileCurveBlock) {
                            piece(state, x, y, z);
                        }
                    }
                }
            }
        }

        /** One piece at {@link #pos}; (x, y, z) is where it sits in the section. */
        private void piece(BlockState state, int x, int y, int z) {
            if (out == null) {
                out = context.getOrCreateChunkBuffer(RenderType.solid());
            }
            readCells();
            boolean tagged = IrisBlockTags.begin(out, state, pos, state.getLightEmission(region, pos));
            BakedModel model = models.getBlockModel(state);
            // The curved skin isn't on the block's boundary, so it is never hidden by a neighbour.
            random.setSeed(42L);
            quads(model.getQuads(state, null, random, ModelData.EMPTY, null), x, y, z);
            // Faces on the boundary (flat sides, the ends of a pillar or tunnel ring) are hidden
            // against neighbours exactly like a normal block's (see TileCurveBlock.skipRendering).
            for (Direction side : DIRECTIONS) {
                random.setSeed(42L);
                List<BakedQuad> list = model.getQuads(state, side, random, ModelData.EMPTY, null);
                if (!list.isEmpty() && Block.shouldRenderFace(state, region, pos, side, cellPos.setWithOffset(pos, side))) {
                    quads(list, x, y, z);
                }
            }
            if (tagged) {
                IrisBlockTags.end(out);
            }
        }

        private void readCells() {
            for (int i = 0; i < 27; i++) {
                cellPos.setWithOffset(pos, i / 9 - 1, i / 3 % 3 - 1, i % 3 - 1);
                BlockState state = region.getBlockState(cellPos);
                int packed = LevelRenderer.getLightColor(region, state, cellPos);
                cellSky[i] = (packed >> 20) & 15;
                cellBlock[i] = (packed >> 4) & 15;
                cellOpen[i] = state.getShadeBrightness(region, cellPos);
                cellNoLight[i] = packed == 0 && state.isSolidRender(region, cellPos);
            }
        }

        private void quads(List<BakedQuad> quads, int x, int y, int z) {
            for (BakedQuad quad : quads) {
                int[] data = quad.getVertices();
                if (!smooth) {
                    flatLight(data);
                }
                for (int k = 0; k < 4; k++) {
                    int o = k * IQuadTransformer.STRIDE;
                    float px = Float.intBitsToFloat(data[o + IQuadTransformer.POSITION]);
                    float py = Float.intBitsToFloat(data[o + IQuadTransformer.POSITION + 1]);
                    float pz = Float.intBitsToFloat(data[o + IQuadTransformer.POSITION + 2]);
                    unpackNormal(data[o + IQuadTransformer.NORMAL], quad.getDirection());
                    float nx = normal.x;
                    float ny = normal.y;
                    float nz = normal.z;
                    float brightness = sideShade(nx, ny, nz);
                    if (smooth) {
                        light(px, py, pz, nx, ny, nz);
                        brightness *= litAo;
                    }
                    int abgr = data[o + IQuadTransformer.COLOR];
                    int color = FastColor.ARGB32.color(abgr >>> 24,
                            (int) ((abgr & 255) * brightness),
                            (int) ((abgr >> 8 & 255) * brightness),
                            (int) ((abgr >> 16 & 255) * brightness));
                    int light = Math.round(litSky * 16.0f) << 16 | Math.round(litBlock * 16.0f);
                    pose.pose().transformPosition(x + px, y + py, z + pz, position);
                    pose.normal().transform(normal.set(nx, ny, nz));
                    out.addVertex(position.x, position.y, position.z, color,
                            Float.intBitsToFloat(data[o + IQuadTransformer.UV0]),
                            Float.intBitsToFloat(data[o + IQuadTransformer.UV0 + 1]),
                            OverlayTexture.NO_OVERLAY, light, normal.x, normal.y, normal.z);
                }
            }
        }

        /** The model's normal of a vertex (unit length) into {@link #normal}; the face's side if it has none. */
        private void unpackNormal(int packed, Direction side) {
            float nx = (byte) packed / 127.0f;
            float ny = (byte) (packed >> 8) / 127.0f;
            float nz = (byte) (packed >> 16) / 127.0f;
            float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
            if (length < 1.0e-3f) {
                normal.set(side.getStepX(), side.getStepY(), side.getStepZ());
            } else {
                normal.set(nx / length, ny / length, nz / length);
            }
        }

        /** Vanilla's six side shades blended by how much the normal faces each way. */
        private float sideShade(float nx, float ny, float nz) {
            return nx * nx * sideShade[nx > 0 ? Direction.EAST.get3DDataValue() : Direction.WEST.get3DDataValue()]
                    + ny * ny * sideShade[ny > 0 ? Direction.UP.get3DDataValue() : Direction.DOWN.get3DDataValue()]
                    + nz * nz * sideShade[nz > 0 ? Direction.SOUTH.get3DDataValue() : Direction.NORTH.get3DDataValue()];
        }

        /**
         * Smooth lighting at one vertex (p = position in the block, n = unit normal): the cells around
         * the point half a block in front of the surface, blended trilinearly by distance. A cell
         * counts in full when its centre is at least a quarter block in front of the surface and not
         * at all from a quarter block behind it (a smooth ramp, so nothing jumps as the curve turns).
         */
        private void light(float px, float py, float pz, float nx, float ny, float nz) {
            // The sample point, in "cell centre" units: 0 = centre of this block, -1 / +1 the neighbours.
            float qx = px + 0.5f * nx - 0.5f;
            float qy = py + 0.5f * ny - 0.5f;
            float qz = pz + 0.5f * nz - 0.5f;
            int kx = Mth.clamp(Mth.floor(qx), -1, 0);
            int ky = Mth.clamp(Mth.floor(qy), -1, 0);
            int kz = Mth.clamp(Mth.floor(qz), -1, 0);
            float fx = Mth.clamp(qx - kx, 0.0f, 1.0f);
            float fy = Mth.clamp(qy - ky, 0.0f, 1.0f);
            float fz = Mth.clamp(qz - kz, 0.0f, 1.0f);
            float total = 0.0f;
            float ao = 0.0f;
            float lit = 0.0f;
            float sky = 0.0f;
            float block = 0.0f;
            for (int c = 0; c < 8; c++) {
                int cx = c & 1;
                int cy = c >> 1 & 1;
                int cz = c >> 2 & 1;
                float w = (cx == 1 ? fx : 1.0f - fx) * (cy == 1 ? fy : 1.0f - fy) * (cz == 1 ? fz : 1.0f - fz);
                if (w <= 0.0f) {
                    continue;
                }
                int dx = kx + cx;
                int dy = ky + cy;
                int dz = kz + cz;
                // How far the cell's centre lies in front of the surface (blocks, along the normal).
                float ahead = (dx + 0.5f - px) * nx + (dy + 0.5f - py) * ny + (dz + 0.5f - pz) * nz;
                w *= Mth.clamp(2.0f * ahead + 0.5f, 0.0f, 1.0f);
                if (w <= 0.0f) {
                    continue;
                }
                int i = (dx + 1) * 9 + (dy + 1) * 3 + (dz + 1);
                total += w;
                ao += w * cellOpen[i];
                if (!cellNoLight[i]) {
                    lit += w;
                    sky += w * cellSky[i];
                    block += w * cellBlock[i];
                }
            }
            litAo = total > EPSILON ? ao / total : 1.0f;
            if (lit > EPSILON) {
                litSky = sky / lit;
                litBlock = block / lit;
            } else {
                litSky = cellSky[13];
                litBlock = cellBlock[13];
            }
        }

        /** Flat lighting (smooth lighting off): the light of the cell the middle of the face looks into. */
        private void flatLight(int[] data) {
            float cx = 0.0f;
            float cy = 0.0f;
            float cz = 0.0f;
            float nx = 0.0f;
            float ny = 0.0f;
            float nz = 0.0f;
            for (int k = 0; k < 4; k++) {
                int o = k * IQuadTransformer.STRIDE;
                cx += Float.intBitsToFloat(data[o + IQuadTransformer.POSITION]);
                cy += Float.intBitsToFloat(data[o + IQuadTransformer.POSITION + 1]);
                cz += Float.intBitsToFloat(data[o + IQuadTransformer.POSITION + 2]);
                int packed = data[o + IQuadTransformer.NORMAL];
                nx += (byte) packed;
                ny += (byte) (packed >> 8);
                nz += (byte) (packed >> 16);
            }
            float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
            float s = length < 1.0e-3f ? 0.0f : 0.5f / length;
            int dx = Mth.clamp(Mth.floor(cx * 0.25f + nx * s), -1, 1);
            int dy = Mth.clamp(Mth.floor(cy * 0.25f + ny * s), -1, 1);
            int dz = Mth.clamp(Mth.floor(cz * 0.25f + nz * s), -1, 1);
            int i = (dx + 1) * 9 + (dy + 1) * 3 + (dz + 1);
            if (cellNoLight[i]) {
                i = 13; // looking into solid tile: the piece's own cell, as vanilla does
            }
            litSky = cellSky[i];
            litBlock = cellBlock[i];
        }
    }

    /**
     * Under Iris (with Sodium) every chunk vertex also carries the shader pack's id for its block and
     * where the block's middle is (packs use them for materials, waving and voxel light). Iris fills
     * them in for the blocks Sodium draws but not for geometry added through
     * {@link AddSectionGeometryEvent}, so the curves would get whatever the last block left behind
     * (id 0, the middle at the section's corner). This sets them the way Iris does for a block.
     * Iris is optional and these are its internals, so it is all looked up by reflection; if
     * anything is missing or fails it is simply skipped.
     */
    private static final class IrisBlockTags {
        @Nullable
        private static final Class<?> BUFFER;
        @Nullable
        private static final MethodHandle BEGIN;
        @Nullable
        private static final MethodHandle END;
        /** WorldRenderingSettings.INSTANCE.getBlockStateIds(), bound: the pack's block ids (null without a pack). */
        @Nullable
        private static final MethodHandle IDS;
        private static volatile boolean failed;

        static {
            Class<?> buffer = null;
            MethodHandle begin = null;
            MethodHandle end = null;
            MethodHandle ids = null;
            try {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                buffer = Class.forName("net.irisshaders.iris.vertices.BlockSensitiveBufferBuilder");
                begin = lookup.findVirtual(buffer, "beginBlock", MethodType.methodType(void.class,
                        int.class, byte.class, byte.class, int.class, int.class, int.class));
                end = lookup.findVirtual(buffer, "endBlock", MethodType.methodType(void.class));
                Class<?> settings = Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings");
                ids = lookup.findVirtual(settings, "getBlockStateIds", MethodType.methodType(Object2IntMap.class))
                        .bindTo(settings.getField("INSTANCE").get(null));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                buffer = null; // no Iris (or a different one): nothing to tag
            }
            BUFFER = buffer;
            BEGIN = buffer == null ? null : begin;
            END = buffer == null ? null : end;
            IDS = buffer == null ? null : ids;
        }

        /** Tags what follows as {@code state} (giving off {@code emission}) at {@code pos}; true if {@link #end} must follow. */
        static boolean begin(VertexConsumer out, BlockState state, BlockPos pos, int emission) {
            if (BUFFER == null || failed || !BUFFER.isInstance(out)) {
                return false;
            }
            try {
                @SuppressWarnings("unchecked")
                Object2IntMap<BlockState> map = (Object2IntMap<BlockState>) IDS.invoke();
                if (map == null) {
                    return false; // no shader pack: Iris leaves the tags alone too
                }
                BEGIN.invoke(out, map.getInt(state), (byte) 0, (byte) emission, pos.getX(), pos.getY(), pos.getZ());
                return true;
            } catch (Throwable e) {
                failed = true;
                return false;
            }
        }

        static void end(VertexConsumer out) {
            try {
                END.invoke(out);
            } catch (Throwable e) {
                failed = true;
            }
        }
    }
}
