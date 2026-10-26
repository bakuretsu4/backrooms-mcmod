package com.rickr.backrooms.client.light;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.block.SunlitVoidBlock;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.client.water.WaterStyle;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.BackroomsLevel;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.VeilRenderer;
import foundry.veil.api.client.render.light.data.AreaLightData;
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
 * Shafts of daylight through Level 37's skylights, slit windows and alcoves (and out of the
 * Poolrooms' sunlit archway).
 *
 * <p>Every opening ends in a group of {@code l37_sunlit_void} blocks. Each group becomes one Veil
 * <b>area light</b>: a glowing rectangle the size of the opening that shines along a slanted
 * "sun" direction, with a narrow beam angle. With Veil's shadows on, the solid wall or ceiling
 * around the opening crops the beam, so it lands as a sharp bright rectangle (or a stripe per
 * slit) on the walls, floors and pillars it reaches, like sun through a window; a little in-air
 * scattering gives the dusty "god ray" look. The sunlit void block doesn't block light, so it never
 * shadows its own opening.
 *
 * <p>The light leaves at irregular angles on purpose ("strict but senseless" architecture, sun
 * from nowhere): skylights shine down tilted around the level's mean sun direction
 * ({@link WaterStyle#SUN_DIR_37}), windows shine in and down, each opening a little differently
 * (fixed per opening, so it is the same every visit).
 *
 * <p>Only active while Veil's lights are ({@link BackroomsLights#isVeilLightingActive()}).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class SunBeams {
    /** Most beams at once (each is a shadowed area light). */
    private static final int MAX_BEAMS = 20;
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

    /** Sun colour: #F1E9BF warm daylight. */
    private static final float SUN_R = 0xF1 / 255.0f;
    private static final float SUN_G = 0xE9 / 255.0f;
    private static final float SUN_B = 0xBF / 255.0f;
    /**
     * Level 37: a lit patch of white tile lands around the sun colour (#f1e9bf) on top of the
     * fill, bright but not clipped to white (2.6 blew pillars and walls out).
     */
    private static final float BRIGHTNESS_37 = 1.25f;
    /** The Poolrooms archway: warm light spilling into a dim room. */
    private static final float BRIGHTNESS_ARCHWAY = 1.2f;
    /** How far a beam reaches (it fades out smoothly by this distance). */
    private static final float DISTANCE = 30.0f;
    /** Soft edge of the beam, radians: small = crisp rectangles of light. */
    private static final float EDGE_ANGLE = 0.14f;
    /** Visible shaft in the air (the flashlight uses 0.3). */
    private static final float INSCATTER_37 = 0.1f;
    private static final float INSCATTER_ARCHWAY = 0.22f;
    /** The rectangle reaches this far beyond the opening on each side; the walls around crop it. */
    private static final float SIZE_MARGIN = 0.35f;

    /** One opening's beam (plain data, no Veil types). */
    public record Beam(long key, double x, double y, double z, float dx, float dy, float dz, float halfX, float halfY,
                       boolean upZ, boolean archway) {
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
        return floor == BackroomsLevel.LEVEL_37 || floor == BackroomsLevel.POOLROOMS;
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
        return state.is(ModBlocks.L37_SUNLIT_VOID.get());
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
                            long pos = BlockPos.asLong(baseX + x, baseY + y, baseZ + z);
                            found.add(pos);
                            FACING.put(pos, state.getValue(SunlitVoidBlock.FACING));
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
        // The rectangle sits on the face the light comes out of, centred on the opening.
        double cx = (minX + maxX + 1) / 2.0;
        double cy = (minY + maxY + 1) / 2.0;
        double cz = (minZ + maxZ + 1) / 2.0;
        double inset = 0.02;
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
        return new Beam(start, cx, cy, cz, dir.x, dir.y, dir.z, halfX + SIZE_MARGIN, halfY + SIZE_MARGIN, vertical, archway);
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
        LongOpenHashSet wanted = new LongOpenHashSet();
        double maxSq = BEAM_RANGE * BEAM_RANGE;
        for (Beam beam : beams) {
            if (wanted.size() >= MAX_BEAMS || beam.distSq(eye) > maxSq) {
                break; // nearest first
            }
            wanted.add(beam.key());
            LightRenderHandle<AreaLightData> handle = ACTIVE.get(beam.key());
            if (handle != null && !handle.isValid()) {
                ACTIVE.remove(beam.key()); // Veil threw it away (resource reload): make it again
                handle = null;
            }
            if (handle == null) {
                ACTIVE.put(beam.key(), renderer.getLightRenderer().addLight(newLight(beam, scale)));
            } else if (rescale) {
                handle.getLightData().setBrightness(brightnessOf(beam) * scale);
            }
        }
        var it = ACTIVE.long2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (!wanted.contains(entry.getLongKey())) {
                entry.getValue().free();
                it.remove();
            }
        }
    }

    private static float brightnessOf(Beam beam) {
        return beam.archway() ? BRIGHTNESS_ARCHWAY : BRIGHTNESS_37;
    }

    private static AreaLightData newLight(Beam beam, float scale) {
        AreaLightData light = new AreaLightData()
                .setSize(beam.halfX(), beam.halfY())
                .setAngle(EDGE_ANGLE)
                .setDistance(DISTANCE)
                .setColor(SUN_R, SUN_G, SUN_B)
                .setBrightness(brightnessOf(beam) * scale)
                .setOcclusionEnabled(true)
                .setInscatteringStrength(beam.archway() ? INSCATTER_ARCHWAY : INSCATTER_37);
        light.getPositionMutable().set(beam.x(), beam.y(), beam.z());
        // Area lights shine along their local +Z. lookAlong turns the given direction to -Z, so
        // pass the reverse (same trick as the spot lights in VeilFixtures). "Up" picks how the
        // rectangle lies: along Z for skylights, upright for windows.
        float upX = 0.0f;
        float upY = beam.upZ() ? 0.0f : 1.0f;
        float upZ = beam.upZ() ? 1.0f : 0.0f;
        light.getOrientationMutable().identity().lookAlong(-beam.dx(), -beam.dy(), -beam.dz(), upX, upY, upZ);
        return light;
    }

    private static void freeLights() {
        if (ACTIVE.isEmpty()) {
            return;
        }
        for (LightRenderHandle<AreaLightData> handle : ACTIVE.values()) {
            handle.free();
        }
        ACTIVE.clear();
        appliedScale = -1.0f;
    }

    private SunBeams() {}
}
