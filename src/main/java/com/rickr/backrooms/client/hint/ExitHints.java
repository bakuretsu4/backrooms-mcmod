package com.rickr.backrooms.client.hint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraClientState;
import com.rickr.backrooms.client.audio.FadingLoopSound;
import com.rickr.backrooms.network.ExitHintPayload;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Hash;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Subtle hints toward the nearest real exit of the level you're on (the server tells us where it
 * is, see {@code world/ExitHints}). Nothing on screen points at it; you have to notice:
 * <ul>
 *   <li><b>Its sound:</b> every exit gives off a faint loop of its own (a draft up Level 0's
 *       stairwell, Level 2's EXIT door buzzing, the Poolrooms' drain gurgling, highway hum through
 *       Level 9's underpass, bubbles rising from Level 7's door...). Within about 40 blocks it
 *       fades in, quiet, and the wall muffling of {@code client/audio/Acoustics} makes it come
 *       "through the walls" until you find the way round.</li>
 *   <li><b>NightShot marks:</b> earlier wanderers painted the way in something that only shows up
 *       on the camcorder's infrared: it soaks up the IR lamp's light, so with NightShot on dark
 *       arrows appear on a few walls and floors around you, each pointing toward the exit. (Dark,
 *       not glowing: NightShot turns the pale walls of Level 0 nearly white, where a glow would
 *       vanish.) They are always true (unlike the arrows drawn on
 *       Level 0's walls), but sparse: on Level 0 one every dozen blocks or so of wall, and fewer
 *       on every floor after it along the path; the last floors only show a rare one near the
 *       exit (see {@link #guidance}).</li>
 * </ul>
 * The torn notes in supply containers are the third hint (server side, {@code WandererNotes}).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class ExitHints {
    // --- The exit's sound ----------------------------------------------------------------------
    /** Heard within this distance (blocks); sounds.json lets it carry about this far. */
    private static final double HEAR_RANGE = 44.0;
    /** Quiet: it should sound like part of the level until you notice it doesn't move. */
    private static final float SOUND_VOLUME = 0.55f;
    private static final float SOUND_FADE = 0.02f;

    // --- NightShot marks -------------------------------------------------------------------------
    private static final ResourceLocation ARROW = Backrooms.id("textures/misc/ir_arrow.png");
    /** Searched this far around you (blocks), from a little above your head to well below your feet. */
    private static final int SCAN_RADIUS = 16;
    private static final int SCAN_UP = 3;
    private static final int SCAN_DOWN = 12;
    /** On Level 0: share of head-high wall faces, and of standable floor cells, that carry a mark. */
    private static final double WALL_SHARE = 0.035;
    private static final double FLOOR_SHARE = 0.008;
    /** Rescanned this often, or sooner once you have moved this far. */
    private static final int RESCAN_TICKS = 40;
    private static final double RESCAN_MOVE = 3.0;
    private static final float WALL_SIZE = 0.8f;
    private static final float FLOOR_SIZE = 0.9f;

    /**
     * How much the marks help on a floor: {@code share} of Level 0's marks, shown only within
     * {@code reach} blocks of the exit (0: anywhere).
     */
    private record Guidance(double share, int reach) {}

    /**
     * The marks are what make finding an exit easy, so they thin out along the path: the first
     * floors teach you that NightShot shows them, the last ones only confirm you're close.
     */
    private static Guidance guidance(BackroomsLevel floor) {
        return switch (floor) {
            case LEVEL_0 -> new Guidance(1.00, 0);
            case LEVEL_1 -> new Guidance(0.75, 0);
            case LEVEL_2 -> new Guidance(0.55, 0);
            case LEVEL_4 -> new Guidance(0.40, 0);
            case POOLROOMS -> new Guidance(0.30, 0);
            case LEVEL_37 -> new Guidance(0.20, 150);
            case LEVEL_7 -> new Guidance(0.15, 120);
            case LEVEL_39 -> new Guidance(0.10, 100);
            case LEVEL_94 -> new Guidance(0.07, 80);
            case LEVEL_9 -> new Guidance(0.03, 50);
        };
    }

    /** A painted arrow: its centre, the face it's on (pointing out of the block) and the way it points. */
    private record Mark(Vec3 centre, Direction face, Vector3f along) {}

    private static boolean known;
    private static int exitX;
    private static int exitY;
    private static int exitZ;
    @Nullable
    private static FadingLoopSound sound;
    @Nullable
    private static BlockPos soundAt;
    private static final List<Mark> MARKS = new ArrayList<>();
    @Nullable
    private static Vec3 scannedFrom;
    private static int sinceScan;

    private ExitHints() {}

    /** From the network: the nearest exit (or none). */
    public static void receive(ExitHintPayload payload) {
        known = payload.known();
        exitX = payload.x();
        exitY = payload.y();
        exitZ = payload.z();
        scannedFrom = null; // the marks point somewhere new: look again
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        BackroomsLevel floor = mc.level == null || mc.player == null ? null : BackroomsLevel.fromDimension(mc.level.dimension());
        if (floor == null || !known || mc.isPaused()) {
            if (floor == null) {
                MARKS.clear();
                scannedFrom = null;
            }
            return;
        }
        tickSound(mc, floor);
        if (CameraClientState.nightShot) {
            Vec3 eye = mc.player.getEyePosition();
            if (++sinceScan >= RESCAN_TICKS || scannedFrom == null || scannedFrom.distanceToSqr(eye) > RESCAN_MOVE * RESCAN_MOVE) {
                scan(mc.level, mc.player.blockPosition(), guidance(floor));
                scannedFrom = eye;
                sinceScan = 0;
            }
        } else if (!MARKS.isEmpty()) {
            MARKS.clear();
            scannedFrom = null;
        }
    }

    /** Keep the exit's loop fed while you are within earshot; a new exit gets a new loop. */
    private static void tickSound(Minecraft mc, BackroomsLevel floor) {
        BlockPos exit = new BlockPos(exitX, exitY, exitZ);
        if (mc.player.position().distanceToSqr(Vec3.atCenterOf(exit)) > HEAR_RANGE * HEAR_RANGE) {
            return; // not fed: it fades out and stops by itself
        }
        if (sound == null || !exit.equals(soundAt) || !mc.getSoundManager().isActive(sound)) {
            sound = new FadingLoopSound(soundOf(floor), false, exitX + 0.5, exitY + 1.0, exitZ + 0.5, SOUND_FADE);
            soundAt = exit;
            mc.getSoundManager().play(sound);
        }
        sound.feed(SOUND_VOLUME);
    }

    /** Each level's exit sound (what you would hear from that kind of exit). */
    private static SoundEvent soundOf(BackroomsLevel floor) {
        return (switch (floor) {
            case LEVEL_0 -> ModSounds.EXIT_HINT_LEVEL_0;
            case LEVEL_1 -> ModSounds.EXIT_HINT_LEVEL_1;
            case LEVEL_2 -> ModSounds.EXIT_HINT_LEVEL_2;
            case LEVEL_4 -> ModSounds.EXIT_HINT_LEVEL_4;
            case POOLROOMS -> ModSounds.EXIT_HINT_POOLROOMS;
            case LEVEL_37 -> ModSounds.EXIT_HINT_LEVEL_37;
            case LEVEL_9 -> ModSounds.EXIT_HINT_LEVEL_9;
            case LEVEL_94 -> ModSounds.EXIT_HINT_LEVEL_94;
            case LEVEL_7 -> ModSounds.EXIT_HINT_LEVEL_7;
            case LEVEL_39 -> ModSounds.EXIT_HINT_LEVEL_39;
        }).get();
    }

    /**
     * Finds the painted spots around you. Which faces carry a mark depends only on their position
     * (a hash), so the same walls always have them; only the arrow's direction depends on the exit.
     * Walls: faces at head height beside a spot you could stand. Floors: spots you could stand on.
     */
    private static void scan(ClientLevel level, BlockPos feet, Guidance guidance) {
        MARKS.clear();
        double dx = exitX - feet.getX();
        double dz = exitZ - feet.getZ();
        if (guidance.reach() > 0 && dx * dx + dz * dz > (double) guidance.reach() * guidance.reach()) {
            return; // too far from the exit for this floor's marks
        }
        double floorShare = FLOOR_SHARE * guidance.share();
        double wallShare = WALL_SHARE * guidance.share();
        BlockPos.MutableBlockPos a = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos b = new BlockPos.MutableBlockPos();
        for (int x = feet.getX() - SCAN_RADIUS; x <= feet.getX() + SCAN_RADIUS; x++) {
            for (int z = feet.getZ() - SCAN_RADIUS; z <= feet.getZ() + SCAN_RADIUS; z++) {
                for (int y = feet.getY() - SCAN_DOWN; y <= feet.getY() + SCAN_UP; y++) {
                    // y is a floor cell when it's solid on top with two open cells above.
                    BlockState ground = level.getBlockState(a.set(x, y, z));
                    if (!ground.isFaceSturdy(level, a, Direction.UP) || solid(level, a.set(x, y + 1, z))
                            || solid(level, a.set(x, y + 2, z))) {
                        continue;
                    }
                    if (Hash.unit(y, x, z, 7301) < floorShare) {
                        MARKS.add(floorMark(x, y, z));
                    }
                    // The head-high cell above this floor, and the walls beside it.
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        b.set(x + d.getStepX(), y + 2, z + d.getStepZ());
                        if (!level.getBlockState(b).isFaceSturdy(level, b, d.getOpposite())) {
                            continue;
                        }
                        if (Hash.unit(b.getY(), b.getX(), b.getZ(), 7302 + d.get3DDataValue()) < wallShare) {
                            MARKS.add(wallMark(b.getX(), b.getY(), b.getZ(), d.getOpposite()));
                        }
                    }
                }
            }
        }
    }

    private static boolean solid(ClientLevel level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** An arrow on the top of floor block (x, y, z), pointing across the floor toward the exit. */
    private static Mark floorMark(int x, int y, int z) {
        Vec3 centre = new Vec3(x + 0.5, y + 1.0, z + 0.5);
        Vector3f along = new Vector3f((float) (exitX + 0.5 - centre.x), 0.0f, (float) (exitZ + 0.5 - centre.z));
        return new Mark(centre, Direction.UP, safeNormalize(along, new Vector3f(1, 0, 0)));
    }

    /**
     * An arrow on the face {@code face} of wall block (x, y, z). It points the way to the exit as
     * seen on that wall: along it, or down when the exit lies far below (Level 7's sea floor).
     */
    private static Mark wallMark(int x, int y, int z, Direction face) {
        Vec3 centre = new Vec3(x + 0.5 + face.getStepX() * 0.5, y + 0.5, z + 0.5 + face.getStepZ() * 0.5);
        Vector3f toExit = new Vector3f((float) (exitX + 0.5 - centre.x), (float) (exitY + 0.5 - centre.y),
                (float) (exitZ + 0.5 - centre.z));
        Vector3f normal = new Vector3f(face.getStepX(), 0, face.getStepZ());
        // Drop the part going into or out of the wall: what's left lies in the wall's plane.
        toExit.sub(new Vector3f(normal).mul(toExit.dot(normal)));
        Vector3f sideways = new Vector3f(-normal.z, 0, normal.x);
        return new Mark(centre, face, safeNormalize(toExit, sideways));
    }

    private static Vector3f safeNormalize(Vector3f v, Vector3f fallback) {
        return v.lengthSquared() < 1.0e-6f ? fallback : v.normalize();
    }

    /** Draws the marks (dark: the paint swallows the infrared lamp's light), only with NightShot on. */
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || MARKS.isEmpty() || !CameraClientState.nightShot) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucent(ARROW);
        VertexConsumer out = buffers.getBuffer(type);
        for (Mark mark : MARKS) {
            Vector3f n = new Vector3f(mark.face().getStepX(), mark.face().getStepY(), mark.face().getStepZ());
            // The arrow's own axes on the surface: u along the way it points, v across it.
            Vector3f u = new Vector3f(mark.along());
            Vector3f v = new Vector3f(n).cross(u);
            float half = (mark.face() == Direction.UP ? FLOOR_SIZE : WALL_SIZE) / 2.0f;
            // A hair off the surface, so it never flickers into it.
            float cx = (float) (mark.centre().x - cam.x) + n.x * 0.01f;
            float cy = (float) (mark.centre().y - cam.y) + n.y * 0.01f;
            float cz = (float) (mark.centre().z - cam.z) + n.z * 0.01f;
            corner(out, pose, cx, cy, cz, u, v, n, -half, -half, 0.0f, 1.0f);
            corner(out, pose, cx, cy, cz, u, v, n, half, -half, 1.0f, 1.0f);
            corner(out, pose, cx, cy, cz, u, v, n, half, half, 1.0f, 0.0f);
            corner(out, pose, cx, cy, cz, u, v, n, -half, half, 0.0f, 0.0f);
        }
        buffers.endBatch(type);
    }

    private static void corner(VertexConsumer out, PoseStack.Pose pose, float cx, float cy, float cz, Vector3f u,
                               Vector3f v, Vector3f n, float du, float dv, float tu, float tv) {
        out.addVertex(pose, cx + u.x * du + v.x * dv, cy + u.y * du + v.y * dv, cz + u.z * du + v.z * dv)
                .setColor(14, 16, 14, 215)
                .setUv(tu, tv)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, n.x, n.y, n.z);
    }
}
