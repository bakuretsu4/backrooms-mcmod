package com.rickr.backrooms.client.light;

import com.rickr.backrooms.config.ClientSettings;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.SunlitVoidBlock;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.client.water.WaterStyle;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.registry.Level369Blocks;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.client.Level369Atmosphere;
import com.rickr.backrooms.world.layout.Level369Plan;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.VeilRenderer;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The hidden daylight of Level 37's skylights, slit windows, alcoves, light well and oculus (and
 * of the Poolrooms' sunlit archway).
 *
 * <p>Every opening ends in a group of {@code l37_sunlit_void} blocks, which look like the tile
 * around them: the source is never seen, only its light. Each group gets up to three Veil lights:
 * <ul>
 *   <li><b>The shaft</b> (every opening in range): an area light the size of the opening, just in
 *       front of its back face, shining along a slanted "sun" direction. With Veil's shadows on,
 *       the walls around the opening crop it, so it lands as a bright stripe or patch on the
 *       walls, floor, pillars and water it reaches. Its edge angle gives the patches a soft rim
 *       and it fades out over {@value #DISTANCE} blocks, so stripes die away into dim corners.</li>
 *   <li><b>The wash</b> (the nearest few openings): a small area light inside the niche, facing
 *       its back, so the back of the niche is the brightest tile in it and the reveals fade from
 *       bright at the back to the plain room light at the front, as if the source were just out
 *       of sight inside.</li>
 *   <li><b>The bounce</b> (the same openings): a faint, wide point light just outside the
 *       niche's mouth, so the tile and water around the opening pick up a soft glow.</li>
 * </ul>
 * The niche's depth (how far the wall runs on in front of the back face) is measured once when
 * the opening is found ({@link #recessDepth}), not every frame.
 *
 * <p>No in-air scattering ("god rays") on these lights: Veil's area-light scattering marches a
 * 1-block sphere around the nearest point of the rectangle, and on any surface within a block of
 * it (the back face, the jambs of the opening) it goes negative or explodes. That drew flat grey
 * patches over the archway's light, rings around it and coloured speckle on the walls beside it.
 * The haze comes from the fog ({@code Level37Atmosphere}) instead.
 *
 * <p>The light leaves at irregular angles on purpose ("strict but senseless" architecture, sun
 * from nowhere): skylights shine down tilted around the level's mean sun direction
 * ({@link WaterStyle#SUN_DIR_37}), windows shine in and down, each opening a little differently
 * (fixed per opening, so it is the same every visit).
 *
 * <p>Only active while Veil's lights are ({@link BackroomsLights#isVeilLightingActive()}).
 * Without them the block's vanilla light 14 is the light, falling off block by block.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class SunBeams {
    // How many shafts and glows at once comes from the QUALITY setting: ClientSettings.Quality
    // #maxSunBeams (20 on HIGH) and #maxSunGlows (12 on HIGH; each is two more lights).
    /** Beams whose opening is further than this aren't drawn. */
    private static final double BEAM_RANGE = 44.0;
    /** Rebuild the beam list after walking this far, or when openings appeared or vanished. */
    private static final int REBUILD_MOVE = 6;
    /** Openings are gathered this far around you. */
    private static final int BUILD_RANGE = 56;
    /** Two void blocks this close (or touching) in one wall/ceiling belong to one opening (window grids). */
    private static final int MERGE_GAP = 2;
    /** An opening is never wider than this (keeps one beam from spanning a whole wall). */
    private static final int MAX_SPAN = 16;
    /** Glows only for openings this close (blocks); they are small and soft, so further off they don't show. */
    private static final double GLOW_RANGE = 28.0;
    /** A niche deeper than this is measured as this deep (the archway passage is 5). */
    private static final int MAX_RECESS = 8;

    /** Sun colour: #F1E9BF warm daylight. */
    private static final float SUN_R = 0xF1 / 255.0f;
    private static final float SUN_G = 0xE9 / 255.0f;
    private static final float SUN_B = 0xBF / 255.0f;
    /**
     * Level 37 shafts. Everything is drawn into an 8-bit picture before the camcorder grades it,
     * so a patch plus the room fill under it must stay under 1.0, yet stand well clear of the
     * fill (the camcorder's highlight shoulder squeezes everything above ~0.6). Calibrated in game
     * (2026-11, sun-light brightness tests): Veil's light lands at about a third of
     * the textbook estimate on our tile, so the niche wash needed 3x and the bounce 2x their first
     * values; the shaft follows the same factor. With the long reach below, a patch 4-14 blocks
     * out (floor, pillars, the far wall, the water) gains roughly 0.3-0.5 over ~0.45-0.55 of fill.
     */
    private static final float BRIGHTNESS_37 = 2.6f;
    /** The Poolrooms archway: warm light spilling into a dim room (same calibration). */
    private static final float BRIGHTNESS_ARCHWAY = 2.0f;
    /**
     * Level 369's hallway windows (the user's picture of the white corridor): flush with the wall
     * (no niche), so each shaft lands as bright stripes across the meadow and the far wall. Cool
     * white daylight, #EEF3FF.
     */
    private static final float BRIGHTNESS_369 = 2.4f;
    private static final float WINDOW_R = 0xEE / 255.0f;
    private static final float WINDOW_G = 0xF3 / 255.0f;
    private static final float WINDOW_B = 0xFF / 255.0f;
    /** In Level 369 only this many window shafts at once, within this many blocks (frame rate). */
    private static final int COMELY_BEAMS = 6;
    private static final double COMELY_RANGE = 28.0;
    /**
     * How far a shaft reaches (it fades out smoothly by this distance, as (1 - d/reach)^3). Long, so
     * sunlight crosses a room at nearly full strength (0.8 at 4 blocks, 0.4 at 14) and lands as
     * stripes on the far wall and pillars, not only as a patch at the window's foot (at 24 a patch
     * 12 blocks out kept an eighth of its light).
     */
    private static final float DISTANCE = 56.0f;
    /**
     * How far a shaft's rectangle may lean behind the back face (blocks): see {@link #limitSlant}.
     * The sideways lean gets at most {@link #MAX_LEAN}, the downward lean what is left of
     * {@link #LEAN_TOTAL}; together they stay under the void's one-block thickness.
     */
    private static final float MAX_LEAN = 0.45f;
    private static final float LEAN_TOTAL = 0.9f;
    /**
     * Soft rim of a shaft, radians. Veil's shadows are block-sharp, so the patch keeps the window's
     * outline; this angle adds a soft falloff around it (about 0.2 block per block of distance)
     * instead of the old hard-cut rectangles (0.14). Not wider: with the long reach every 0.1 rad
     * adds ~5.6 blocks to each side of the volume Veil shades.
     */
    private static final float EDGE_ANGLE = 0.2f;
    // (No setInscatteringStrength: see the class comment. Veil's default is 0 = off.)
    /** The rectangle reaches this far beyond the opening on each side; the walls around crop it. */
    private static final float SIZE_MARGIN = 0.2f;
    /** The archway's light rectangle is exactly the size of its opening (see {@link #cluster}). */
    private static final float ARCHWAY_INSET = 0.0f;
    /**
     * The shaft's rectangle sits this far in front of the back face. Exactly on it, the face (now
     * lit tile, no longer full-bright) would be lit from a zero distance: noisy speckle.
     */
    private static final float FACE_GAP = 0.05f;

    /**
     * The wash inside the niche: a rectangle a little smaller than the opening, 0.8 of the way to
     * the niche's mouth (at most 0.8 blocks out), shining back onto the back face. It lights the
     * back evenly and the reveals behind it with a gradient: full near the back, fading to nothing
     * at its own plane over the wide edge angle. Brightness calibrated in game: at 3.3 the backs
     * glow warm and read as lit niches (the reference's alcoves) without going white.
     */
    private static final float COVE_GAP_SHARE = 0.8f;
    private static final float COVE_GAP_MIN = 0.4f;
    private static final float COVE_GAP_MAX = 0.8f;
    private static final float COVE_INSET = 0.1f;
    private static final float COVE_DISTANCE = 2.8f;
    private static final float COVE_ANGLE = 1.25f;
    private static final float COVE_BRIGHTNESS = 3.3f;
    /**
     * The bounce: a point light this far out of the niche's mouth, reaching {@link #BOUNCE_REACH}
     * plus the opening's half size: a soft glow on the tile and water around the opening.
     * Brightness calibrated in game (0.8).
     */
    private static final float BOUNCE_OUT = 0.7f;
    private static final float BOUNCE_REACH = 6.0f;
    private static final float BOUNCE_BRIGHTNESS = 0.8f;

    /**
     * One opening (plain data, no Veil types). (x, y, z) is the middle of its back face; facing
     * points out of it into the room; halfX/halfY are the shaft's rectangle, faceHalfX/faceHalfY
     * the opening itself; depth is how many blocks of niche lie in front of the back face.
     */
    public record Beam(long key, double x, double y, double z, float dx, float dy, float dz, float halfX, float halfY,
                       boolean upZ, boolean archway, Direction facing, float faceHalfX, float faceHalfY, int depth) {
        public double distSq(Vec3 p) {
            double ax = x - p.x;
            double ay = y - p.y;
            double az = z - p.z;
            return ax * ax + ay * ay + az * az;
        }
    }

    /** Positions of all sunlit void blocks in the loaded chunks, per chunk. */
    private static final Long2ObjectOpenHashMap<long[]> BY_CHUNK = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<Direction> FACING = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<LightRenderHandle<AreaLightData>> ACTIVE = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<LightRenderHandle<AreaLightData>> COVES = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<LightRenderHandle<PointLightData>> BOUNCES = new Long2ObjectOpenHashMap<>();
    /** Reused every tick (no allocation): the openings that want a shaft / glows this tick. */
    private static final LongOpenHashSet WANTED = new LongOpenHashSet();
    private static final LongOpenHashSet WANTED_GLOWS = new LongOpenHashSet();
    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();

    @Nullable
    private static ClientLevel boundLevel;
    private static List<Beam> beams = new ArrayList<>();
    private static int revision;
    private static int builtRevision = -1;
    private static int builtX;
    private static int builtZ;
    private static float appliedScale = -1.0f;

    /** The beams around you, nearest first (also used by the archway glow in Level37Atmosphere). */
    public static List<Beam> beams() {
        return beams;
    }

    private static boolean activeIn(@Nullable BackroomsLevel floor) {
        return floor == BackroomsLevel.LEVEL_37 || floor == BackroomsLevel.POOLROOMS || floor == BackroomsLevel.LEVEL_369;
    }

    /** True while the bound level is Level 369 (its windows' cool white light). */
    private static boolean comely() {
        return boundLevel != null && BackroomsLevel.fromDimension(boundLevel.dimension()) == BackroomsLevel.LEVEL_369;
    }

    private static float red() {
        return comely() ? WINDOW_R : SUN_R;
    }

    private static float green() {
        return comely() ? WINDOW_G : SUN_G;
    }

    private static float blue() {
        return comely() ? WINDOW_B : SUN_B;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != boundLevel) {
            bind(level);
        }
        BackroomsLevel floor = level == null ? null : BackroomsLevel.fromDimension(level.dimension());
        if (!activeIn(floor) || mc.player == null) {
            freeLights();
            return;
        }
        BlockPos pos = mc.player.blockPosition();
        if (revision != builtRevision || Math.abs(pos.getX() - builtX) > REBUILD_MOVE || Math.abs(pos.getZ() - builtZ) > REBUILD_MOVE) {
            beams = build(pos.getX(), pos.getZ());
            builtRevision = revision;
            builtX = pos.getX();
            builtZ = pos.getZ();
        }
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        beams.sort(Comparator.comparingDouble(b -> b.distSq(eye)));
        if (BackroomsLights.isVeilLightingActive()) {
            sync(eye);
        } else {
            freeLights();
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide() && event.getChunk() instanceof LevelChunk chunk) {
            if (chunk.getLevel() != boundLevel && chunk.getLevel() instanceof ClientLevel level) {
                bind(level);
            }
            scan(chunk);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            remove(event.getChunk().getPos().toLong());
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        bind(null);
    }

    private static void bind(@Nullable ClientLevel level) {
        boundLevel = level;
        freeLights();
        BY_CHUNK.clear();
        FACING.clear();
        beams = new ArrayList<>();
        revision++;
        Minecraft mc = Minecraft.getInstance();
        if (level != null && mc.player != null && mc.player.level() == level) {
            int cx = SectionPos.blockToSectionCoord(mc.player.getBlockX());
            int cz = SectionPos.blockToSectionCoord(mc.player.getBlockZ());
            int r = BUILD_RANGE / 16 + 1;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                    if (chunk != null) {
                        scan(chunk);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Index of the sunlit void blocks
    // ------------------------------------------------------------------------------------------

    private static boolean isVoid(BlockState state) {
        return state.is(ModBlocks.L37_SUNLIT_VOID.get()) || state.is(Level369Blocks.WINDOW.get());
    }

    /**
     * The way an opening shines: a sunlit void block says so itself; a Level 369 window, flush in
     * its wall, shines toward the side with open air (always in the same chunk: the window stands
     * one block outside the corridor, both well inside a chunk). Null if no side is open.
     */
    @Nullable
    private static Direction facingOf(LevelChunk chunk, BlockState state, int x, int y, int z) {
        if (state.hasProperty(SunlitVoidBlock.FACING)) {
            return state.getValue(SunlitVoidBlock.FACING);
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int nx = x + d.getStepX();
            int nz = z + d.getStepZ();
            if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16
                    && chunk.getBlockState(new BlockPos(chunk.getPos().getMinBlockX() + nx, y, chunk.getPos().getMinBlockZ() + nz)).isAir()) {
                return d;
            }
        }
        return null;
    }

    private static void scan(LevelChunk chunk) {
        if (boundLevel == null || chunk.getLevel() != boundLevel || !activeIn(BackroomsLevel.fromDimension(boundLevel.dimension()))) {
            return;
        }
        LongArrayList found = new LongArrayList();
        LevelChunkSection[] sections = chunk.getSections();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir() || !section.maybeHas(SunBeams::isVoid)) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (isVoid(state)) {
                            Direction facing = facingOf(chunk, state, x, baseY + y, z);
                            if (facing == null) {
                                continue;
                            }
                            long pos = BlockPos.asLong(baseX + x, baseY + y, baseZ + z);
                            found.add(pos);
                            FACING.put(pos, facing);
                        }
                    }
                }
            }
        }
        long key = chunk.getPos().toLong();
        remove(key);
        if (!found.isEmpty()) {
            BY_CHUNK.put(key, found.toLongArray());
            revision++;
        }
    }

    private static void remove(long chunkKey) {
        long[] old = BY_CHUNK.remove(chunkKey);
        if (old != null) {
            for (long pos : old) {
                FACING.remove(pos);
            }
            revision++;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Openings -> beams
    // ------------------------------------------------------------------------------------------

    private static List<Beam> build(int centerX, int centerZ) {
        List<Beam> out = new ArrayList<>();
        LongOpenHashSet visited = new LongOpenHashSet();
        LongArrayList stack = new LongArrayList();
        int minCx = SectionPos.blockToSectionCoord(centerX - BUILD_RANGE);
        int maxCx = SectionPos.blockToSectionCoord(centerX + BUILD_RANGE);
        int minCz = SectionPos.blockToSectionCoord(centerZ - BUILD_RANGE);
        int maxCz = SectionPos.blockToSectionCoord(centerZ + BUILD_RANGE);
        boolean archway = boundLevel != null && BackroomsLevel.fromDimension(boundLevel.dimension()) == BackroomsLevel.POOLROOMS;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                long[] positions = BY_CHUNK.get(ChunkPos.asLong(cx, cz));
                if (positions == null) {
                    continue;
                }
                for (long start : positions) {
                    if (visited.contains(start)) {
                        continue;
                    }
                    Beam beam = cluster(start, visited, stack, archway);
                    if (beam != null) {
                        out.add(beam);
                    }
                }
            }
        }
        return out;
    }

    /**
     * Flood fill over the void blocks of one opening: same facing, in the same wall or ceiling
     * plane, touching or within {@link #MERGE_GAP} blocks (so a grid of small windows lights as
     * one beam, and the wall between them shades it into separate patches).
     */
    @Nullable
    private static Beam cluster(long start, LongOpenHashSet visited, LongArrayList stack, boolean archway) {
        Direction facing = FACING.get(start);
        if (facing == null) {
            return null;
        }
        int sx = BlockPos.getX(start);
        int sy = BlockPos.getY(start);
        int sz = BlockPos.getZ(start);
        int minX = sx;
        int minY = sy;
        int minZ = sz;
        int maxX = sx;
        int maxY = sy;
        int maxZ = sz;
        stack.clear();
        stack.add(start);
        visited.add(start);
        Direction.Axis normal = facing.getAxis();
        while (!stack.isEmpty()) {
            long pos = stack.popLong();
            int x = BlockPos.getX(pos);
            int y = BlockPos.getY(pos);
            int z = BlockPos.getZ(pos);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            for (int a = -MERGE_GAP; a <= MERGE_GAP; a++) {
                for (int b = -MERGE_GAP; b <= MERGE_GAP; b++) {
                    if (a == 0 && b == 0) {
                        continue;
                    }
                    // Step within the plane of the opening (perpendicular to the light's direction).
                    int nx = x + (normal == Direction.Axis.X ? 0 : a);
                    int ny = y + (normal == Direction.Axis.Y ? 0 : normal == Direction.Axis.X ? a : b);
                    int nz = z + (normal == Direction.Axis.Z ? 0 : b);
                    long next = BlockPos.asLong(nx, ny, nz);
                    if (visited.contains(next) || FACING.get(next) != facing
                            || Math.abs(nx - sx) >= MAX_SPAN || Math.abs(ny - sy) >= MAX_SPAN || Math.abs(nz - sz) >= MAX_SPAN) {
                        continue;
                    }
                    visited.add(next);
                    stack.add(next);
                }
            }
        }
        // (cx, cy, cz): the middle of the back face of the opening. The shaft's rectangle sits just
        // in front of it (FACE_GAP). (Half a block back inside the void lit the back evenly, but then
        // the passage walls beside it were lit at a grazing angle and Veil's area-light shadows
        // turned them into flickering speckle.)
        double cx = (minX + maxX + 1) / 2.0;
        double cy = (minY + maxY + 1) / 2.0;
        double cz = (minZ + maxZ + 1) / 2.0;
        double inset = 0.0;
        switch (facing) {
            case DOWN -> cy = minY - inset;
            case UP -> cy = maxY + 1 + inset;
            case NORTH -> cz = minZ - inset;
            case SOUTH -> cz = maxZ + 1 + inset;
            case WEST -> cx = minX - inset;
            case EAST -> cx = maxX + 1 + inset;
        }
        Vector3f dir = direction(facing, start, archway);
        // Rectangle half-sizes: across the opening (local X) and along "up" (local Y).
        boolean vertical = facing.getAxis() == Direction.Axis.Y;
        float halfX;
        float halfY;
        if (vertical) {
            halfX = (maxX - minX + 1) / 2.0f;
            halfY = (maxZ - minZ + 1) / 2.0f;
        } else {
            halfX = (facing.getAxis() == Direction.Axis.X ? maxZ - minZ + 1 : maxX - minX + 1) / 2.0f;
            halfY = (maxY - minY + 1) / 2.0f;
        }
        limitSlant(dir, facing, halfX, halfY);
        // Level 37's openings: the rectangle overhangs the opening and the walls around crop it. The
        // Poolrooms archway's light wall fills a passage whose side walls run along the beam, so an
        // overhang would put the light's edge inside those walls and Veil's area-light shading turns
        // the wall surface into flickering speckle: there the rectangle stays a little inside.
        float margin = archway ? -ARCHWAY_INSET : SIZE_MARGIN;
        int depth = recessDepth(facing, minX, minY, minZ, maxX, maxY, maxZ);
        return new Beam(start, cx, cy, cz, dir.x, dir.y, dir.z, halfX + margin, halfY + margin, vertical, archway,
                facing, halfX, halfY, depth);
    }

    /**
     * Keeps a shaft's slant small enough for Veil's shadow test to work across the whole opening.
     *
     * <p>Veil lights a surface point from the nearest point of the light's rectangle and checks
     * the straight line between the two for blocks. The rectangle stands square to the (slanted)
     * beam, through the middle of the opening, so across a wide or tall opening one end of it
     * leans back behind the back face. A point lit through that end of the opening then tests its
     * line against a spot up to ~1.5-2.6 blocks behind the face: past the one-block-thick void,
     * inside the solid wall, so every such patch came out black (the 15-wide window grid of the
     * pool rooms and the 5-tall slits lost all their light this way). Limiting the in-plane
     * components of the beam keeps that spot inside the void: the lean along the opening's first
     * axis (across a wall, X for a skylight) at most {@value #MAX_LEAN} block, along the second
     * (up a wall) at most what is left of {@value #LEAN_TOTAL}. The test then only asks the real
     * question: is the path from the opening to the point clear. Narrow openings keep their full
     * slant; a wide window grid shines nearly straight out sideways, a 5-tall slit (which leans
     * little sideways) down at up to ~17 degrees: long stripes across the room onto the pillars,
     * the far wall and the water, like the references.
     */
    private static void limitSlant(Vector3f dir, Direction facing, float halfX, float halfY) {
        float nx = facing.getStepX();
        float ny = facing.getStepY();
        float nz = facing.getStepZ();
        // The two directions within the face, with the opening's half-size along each.
        float ax;
        float ay;
        float az;
        float bx;
        float by;
        float bz;
        if (facing.getAxis() == Direction.Axis.Y) {
            ax = 1.0f; ay = 0.0f; az = 0.0f;   // halfX runs along X
            bx = 0.0f; by = 0.0f; bz = 1.0f;   // halfY along Z
        } else {
            Direction across = facing.getClockWise();
            ax = across.getStepX(); ay = 0.0f; az = across.getStepZ();   // halfX across the wall
            bx = 0.0f; by = 1.0f; bz = 0.0f;                             // halfY up
        }
        float ca = dir.x * ax + dir.y * ay + dir.z * az;
        float cb = dir.x * bx + dir.y * by + dir.z * bz;
        ca = Math.copySign(Math.min(Math.abs(ca), MAX_LEAN / Math.max(halfX, MAX_LEAN)), ca);
        float left = LEAN_TOTAL - halfX * Math.abs(ca);
        cb = Math.copySign(Math.min(Math.abs(cb), left / Math.max(halfY, left)), cb);
        float cn = (float) Math.sqrt(Math.max(1.0f - ca * ca - cb * cb, 0.0f));
        dir.set(nx * cn + ax * ca + bx * cb, ny * cn + ay * ca + by * cb, nz * cn + az * ca + bz * cb).normalize();
    }

    /**
     * How many blocks of niche lie in front of an opening's back face: step out of the face one
     * block at a time while the wall still closes in on all four sides just beyond the opening's
     * edges. A slit window gives 1, the rotunda alcoves and the archway passage several, a
     * skylight the height of its shaft. Measured once per opening when the list is rebuilt.
     */
    private static int recessDepth(Direction facing, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        ClientLevel level = boundLevel;
        if (level == null) {
            return 0;
        }
        int[] lo = {minX, minY, minZ};
        int[] hi = {maxX, maxY, maxZ};
        int axis = facing.getAxis().ordinal(); // X 0, Y 1, Z 2
        int a = (axis + 1) % 3;
        int b = (axis + 2) % 3;
        int[] p = new int[3];
        int depth = 0;
        for (int k = 1; k <= MAX_RECESS; k++) {
            p[axis] = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? hi[axis] + k : lo[axis] - k;
            boolean closed = true;
            for (int side = 0; side < 4 && closed; side++) {
                int across = side < 2 ? a : b;
                int along = side < 2 ? b : a;
                p[across] = (side & 1) == 0 ? lo[across] - 1 : hi[across] + 1;
                p[along] = (lo[along] + hi[along]) >> 1;
                CURSOR.set(p[0], p[1], p[2]);
                closed = !level.getBlockState(CURSOR).getCollisionShape(level, CURSOR).isEmpty();
            }
            if (!closed) {
                break;
            }
            depth = k;
        }
        return depth;
    }

    /**
     * Where an opening's light goes: out of its face, slanted down (windows) or off the vertical
     * (skylights) by an amount fixed per opening, so every patch of sun falls a little differently.
     */
    private static Vector3f direction(Direction facing, long key, boolean archway) {
        long h = FixturePower.mix(key ^ 0x5DEECE66DL);
        float u1 = ((h >>> 11) & 0xFFFF) / 65535.0f;
        float u2 = ((h >>> 27) & 0xFFFF) / 65535.0f;
        if (archway) {
            // Out of the Poolrooms archway: nearly level, just dipping toward the floor.
            Vector3f out = new Vector3f(facing.getStepX(), -0.12f, facing.getStepZ());
            return out.normalize();
        }
        if (facing == Direction.DOWN || facing == Direction.UP) {
            Vector3f sun = WaterStyle.SUN_DIR_37;
            double azimuth = Math.atan2(sun.z, sun.x) + (u1 - 0.5) * Math.toRadians(70.0);
            double tilt = Math.toRadians(16.0 + 22.0 * u2); // off the vertical
            float side = (float) Math.sin(tilt);
            float vy = (float) Math.cos(tilt) * (facing == Direction.DOWN ? -1.0f : 1.0f);
            return new Vector3f((float) Math.cos(azimuth) * side, vy, (float) Math.sin(azimuth) * side).normalize();
        }
        double down = Math.toRadians(18.0 + 26.0 * u1);
        double skew = (u2 - 0.5) * Math.toRadians(44.0);
        Direction left = facing.getCounterClockWise();
        float fx = (float) (Math.cos(down) * Math.cos(skew));
        float lx = (float) (Math.cos(down) * Math.sin(skew));
        return new Vector3f(
                facing.getStepX() * fx + left.getStepX() * lx,
                (float) -Math.sin(down),
                facing.getStepZ() * fx + left.getStepZ() * lx).normalize();
    }

    // ------------------------------------------------------------------------------------------
    // Veil lights
    // ------------------------------------------------------------------------------------------

    private static void sync(Vec3 eye) {
        VeilRenderer renderer = VeilRenderSystem.renderer();
        if (renderer == null) {
            return;
        }
        float scale = CameraClientState.battery <= 0.0f ? 0.25f : 1.0f;
        boolean rescale = scale != appliedScale;
        appliedScale = scale;
        WANTED.clear();
        WANTED_GLOWS.clear();
        // Level 369's hallways have a window every few blocks along hundreds of blocks of
        // corridor: lighting every one in range halved the frame rate, even in rooms with no
        // window in sight (each shaft is shadowed). There only the nearest few shine, and only
        // while you are mostly in a hallway (the light outside one stays the room's own).
        boolean comely = comely();
        if (comely && Level369Atmosphere.room(Level369Plan.HALL) < 0.5f) {
            freeLights();
            return;
        }
        double maxSq = comely ? COMELY_RANGE * COMELY_RANGE : BEAM_RANGE * BEAM_RANGE;
        double glowSq = GLOW_RANGE * GLOW_RANGE;
        int maxBeams = comely ? Math.min(COMELY_BEAMS, ClientSettings.quality().maxSunBeams) : ClientSettings.quality().maxSunBeams;
        int maxGlows = comely ? 0 : ClientSettings.quality().maxSunGlows;
        for (Beam beam : beams) {
            double distSq = beam.distSq(eye);
            if (WANTED.size() >= maxBeams || distSq > maxSq) {
                break; // nearest first
            }
            long key = beam.key();
            WANTED.add(key);
            LightRenderHandle<AreaLightData> handle = ACTIVE.get(key);
            if (handle != null && !handle.isValid()) {
                ACTIVE.remove(key); // Veil threw it away (resource reload): make it again
                handle = null;
            }
            if (handle == null) {
                ACTIVE.put(key, renderer.getLightRenderer().addLight(newShaft(beam, scale)));
            } else if (rescale) {
                handle.getLightData().setBrightness(brightnessOf(beam) * scale);
            }
            if (WANTED_GLOWS.size() >= maxGlows || distSq > glowSq) {
                continue;
            }
            WANTED_GLOWS.add(key);
            LightRenderHandle<AreaLightData> cove = COVES.get(key);
            if (cove != null && !cove.isValid()) {
                COVES.remove(key);
                cove = null;
            }
            if (cove == null) {
                COVES.put(key, renderer.getLightRenderer().addLight(newCove(beam, scale)));
            } else if (rescale) {
                cove.getLightData().setBrightness(COVE_BRIGHTNESS * scale);
            }
            LightRenderHandle<PointLightData> bounce = BOUNCES.get(key);
            if (bounce != null && !bounce.isValid()) {
                BOUNCES.remove(key);
                bounce = null;
            }
            if (bounce == null) {
                BOUNCES.put(key, renderer.getLightRenderer().addLight(newBounce(beam, scale)));
            } else if (rescale) {
                bounce.getLightData().setBrightness(BOUNCE_BRIGHTNESS * scale);
            }
        }
        var shafts = ACTIVE.long2ObjectEntrySet().iterator();
        while (shafts.hasNext()) {
            var entry = shafts.next();
            if (!WANTED.contains(entry.getLongKey())) {
                entry.getValue().free();
                shafts.remove();
            }
        }
        var coves = COVES.long2ObjectEntrySet().iterator();
        while (coves.hasNext()) {
            var entry = coves.next();
            if (!WANTED_GLOWS.contains(entry.getLongKey())) {
                entry.getValue().free();
                coves.remove();
            }
        }
        var bounces = BOUNCES.long2ObjectEntrySet().iterator();
        while (bounces.hasNext()) {
            var entry = bounces.next();
            if (!WANTED_GLOWS.contains(entry.getLongKey())) {
                entry.getValue().free();
                bounces.remove();
            }
        }
    }

    private static float brightnessOf(Beam beam) {
        return beam.archway() ? BRIGHTNESS_ARCHWAY : comely() ? BRIGHTNESS_369 : BRIGHTNESS_37;
    }

    /** The shaft of sun: out of the back face along the opening's slanted sun direction. */
    private static AreaLightData newShaft(Beam beam, float scale) {
        AreaLightData light = new AreaLightData()
                .setSize(beam.halfX(), beam.halfY())
                .setAngle(EDGE_ANGLE)
                .setDistance(DISTANCE)
                .setColor(red(), green(), blue())
                .setBrightness(brightnessOf(beam) * scale)
                .setOcclusionEnabled(true);
        Direction out = beam.facing();
        light.getPositionMutable().set(beam.x() + out.getStepX() * FACE_GAP, beam.y() + out.getStepY() * FACE_GAP,
                beam.z() + out.getStepZ() * FACE_GAP);
        // Area lights shine along their local +Z. lookAlong turns the given direction to -Z, so
        // pass the reverse (same trick as the spot lights in VeilFixtures).
        orient(light, beam, -beam.dx(), -beam.dy(), -beam.dz());
        return light;
    }

    /** The wash: inside the niche, a little smaller than the opening, shining back onto its back face. */
    private static AreaLightData newCove(Beam beam, float scale) {
        AreaLightData light = new AreaLightData()
                .setSize(Math.max(beam.faceHalfX() - COVE_INSET, 0.2f), Math.max(beam.faceHalfY() - COVE_INSET, 0.2f))
                .setAngle(COVE_ANGLE)
                .setDistance(COVE_DISTANCE)
                .setColor(red(), green(), blue())
                .setBrightness(COVE_BRIGHTNESS * scale)
                .setOcclusionEnabled(true);
        Direction out = beam.facing();
        float gap = Math.min(Math.max(beam.depth() * COVE_GAP_SHARE, COVE_GAP_MIN), COVE_GAP_MAX);
        light.getPositionMutable().set(beam.x() + out.getStepX() * gap, beam.y() + out.getStepY() * gap,
                beam.z() + out.getStepZ() * gap);
        // It shines back into the niche (along -facing), so lookAlong gets +facing.
        orient(light, beam, out.getStepX(), out.getStepY(), out.getStepZ());
        return light;
    }

    /** The bounce: a faint wide glow just outside the niche's mouth. */
    private static PointLightData newBounce(Beam beam, float scale) {
        Direction out = beam.facing();
        double reach = beam.depth() + BOUNCE_OUT;
        return new PointLightData()
                .setPosition(beam.x() + out.getStepX() * reach, beam.y() + out.getStepY() * reach, beam.z() + out.getStepZ() * reach)
                .setRadius(BOUNCE_REACH + Math.max(beam.faceHalfX(), beam.faceHalfY()))
                .setColor(red(), green(), blue())
                .setBrightness(BOUNCE_BRIGHTNESS * scale)
                .setOcclusionEnabled(true); // stays in the room it lights
    }

    /** Turns an area light to face along -look; "up" lays the rectangle along Z for skylights, upright for windows. */
    private static void orient(AreaLightData light, Beam beam, float lookX, float lookY, float lookZ) {
        float upY = beam.upZ() ? 0.0f : 1.0f;
        float upZ = beam.upZ() ? 1.0f : 0.0f;
        light.getOrientationMutable().identity().lookAlong(lookX, lookY, lookZ, 0.0f, upY, upZ);
    }

    private static void freeLights() {
        if (ACTIVE.isEmpty() && COVES.isEmpty() && BOUNCES.isEmpty()) {
            return;
        }
        for (LightRenderHandle<AreaLightData> handle : ACTIVE.values()) {
            handle.free();
        }
        for (LightRenderHandle<AreaLightData> handle : COVES.values()) {
            handle.free();
        }
        for (LightRenderHandle<PointLightData> handle : BOUNCES.values()) {
            handle.free();
        }
        ACTIVE.clear();
        COVES.clear();
        BOUNCES.clear();
        appliedScale = -1.0f;
    }

    private SunBeams() {}
}
