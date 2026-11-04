package com.rickr.backrooms.client.level228;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.config.ClientSettings;
import com.rickr.backrooms.network.Level228HuntPayload;
import com.rickr.backrooms.network.Level228SeenPayload;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Level228Hunt;
import com.rickr.backrooms.world.layout.Level228Plan;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/**
 * Level 228's "Other" on the client (the server side is world/Level228Hunt): a tall, thin, pale
 * figure drawn ONLY inside the mirrors' image volumes, at the image of its real position. In the
 * glass you see it in the reflected corridor, maybe behind your own reflection; turn round to the
 * real corridor and nothing is there, because nothing is ever drawn in real space.
 *
 * <ul>
 *   <li><b>Seen.</b> Every few ticks we check whether any of its images is on screen and not hidden
 *       behind opaque blocks (frames, panels, the mirrored walls; the glass itself doesn't hide
 *       it) and tell the server, which freezes it while the reports keep coming.</li>
 *   <li><b>Sound.</b> Its footsteps are placed at its nearest image, so they come from the mirror,
 *       never from where it really is. Within {@value #TICK_RANGE} blocks the glass around you
 *       ticks, faster as it nears. Just before a catch (only for the one it has) the level holds
 *       its breath: {@link #hush()} ducks the ambience (Level228Atmosphere/Ambience read it), an
 *       intake of breath, then nothing.</li>
 *   <li><b>The catch.</b> Your reflection goes from every mirror (Level228Reflections hides your
 *       puppets), then the glass cracks across the picture and it whites out; the server moves you
 *       under the white, and it fades back somewhere else.</li>
 * </ul>
 *
 * <p><b>Why it reads under shader packs too:</b> the body is plain entity geometry drawn with
 * {@code RenderType.entityCutoutNoCull} after the entities (Iris draws it with the pack's entity
 * program, fog and lighting included), lit by the real block light at its image, which is the
 * mirrored copy of the light at its real position. The crack and white-out are GUI drawing.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level228Other {
    private static final ResourceLocation SKIN = Backrooms.id("textures/entity/l228_other.png");
    private static final ResourceLocation CRACK = Backrooms.id("textures/misc/l228_crack.png");
    private static final int CRACK_SIZE = 512;

    /** Images farther than this from the camera are not drawn or checked (the fog has them anyway). */
    private static final double RANGE = 48.0;
    /** Its steps are heard from images up to this far; the glass ticks within this of its real position. */
    private static final double STEP_RANGE = 28.0;
    private static final double TICK_RANGE = 12.0;
    /** One footstep per this much walking (long, slow strides). */
    private static final double STRIDE = 1.15;
    /** "Seen" is checked (and reported) this often, in ticks; the server's grace is 12. */
    private static final int SEEN_EVERY = 4;
    /** Only the middle of the screen counts as looking at it (fraction of the half-extent). */
    private static final double SCREEN_MARGIN = 0.92;
    /** Take timing on the client: crack at once, full white by this tick, holding, then fading out by the end. */
    private static final float WHITE_IN = 8.0f;
    private static final float WHITE_HOLD = 22.0f;
    private static final float WHITE_END = 60.0f;
    /** The crack's jolt of the view (degrees of roll), scaled by the view-tilt setting. */
    private static final float JOLT_ROLL = 2.5f;

    // ---- the figure (local units: x right, y up, z forward; blocks) ----
    private static final float HEIGHT = 2.62f;
    private static final float HIP_Y = 1.32f;
    private static final float SHOULDER_Y = 2.08f;
    private static final int PALE = 0xFFD9D6CF;
    private static final int SHADE = 0xFFB9B5AD;
    private static final int EYES = 0xFF0B0A0A;

    private static final int MAX_IMAGES = 24;
    private static final Level228Plan.Image[] FOUND = new Level228Plan.Image[MAX_IMAGES];
    // The image volumes near you, copied once a tick so every frame can map the moving figure into them.
    private static final int[] I_MINX = new int[MAX_IMAGES];
    private static final int[] I_MAXX = new int[MAX_IMAGES];
    private static final int[] I_MINY = new int[MAX_IMAGES];
    private static final int[] I_MAXY = new int[MAX_IMAGES];
    private static final int[] I_MINZ = new int[MAX_IMAGES];
    private static final int[] I_MAXZ = new int[MAX_IMAGES];
    private static final int[] I_FX = new int[MAX_IMAGES];
    private static final int[] I_FZ = new int[MAX_IMAGES];
    private static final int[] I_OX = new int[MAX_IMAGES];
    private static final int[] I_OZ = new int[MAX_IMAGES];
    private static final int[] I_AXIS = new int[MAX_IMAGES];
    private static final double[] I_PLANE = new double[MAX_IMAGES];
    private static int imageCount;

    static {
        for (int i = 0; i < MAX_IMAGES; i++) {
            FOUND[i] = new Level228Plan.Image();
        }
    }

    private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
    private static final RandomSource RANDOM = RandomSource.create();

    // ---- state from the server ----
    private static long seed;
    private static int state = Level228Hunt.ABSENT;
    private static boolean hunted;
    private static double goalX;
    private static double goalY;
    private static double goalZ;
    private static float yaw;
    // Where it is drawn (eased toward the server's position each tick), last tick and now.
    private static double prevX;
    private static double prevY;
    private static double prevZ;
    private static double curX;
    private static double curY;
    private static double curZ;
    private static boolean placed;
    /** Walking: distance covered (for the stride and the steps) and whether it moved this tick. */
    private static double walked;
    private static double prevWalked;
    private static double stepAccum;
    private static boolean moving;

    private static long ticks;
    private static long nextGlassTick;
    private static float hush;
    private static boolean selfHidden;
    /** Ticks since the crack (the take began), or negative when none is showing. */
    private static long crackTick = -1;

    private Level228Other() {}

    /** 0..1: how far the level has gone quiet before a catch (ambience volumes scale by 1 - 0.95 * hush). */
    public static float hush() {
        return hush;
    }

    /** Network: where the Other is now (real coordinates) and what it is doing. */
    public static void receive(Level228HuntPayload p) {
        seed = p.seed();
        int was = state;
        state = p.state();
        hunted = p.hunted();
        goalX = p.x();
        goalY = p.y();
        goalZ = p.z();
        yaw = p.yaw();
        if (state == Level228Hunt.ABSENT) {
            placed = false;
        } else if (!placed || state != Level228Hunt.STALK) {
            // Arriving, or holding on to you: no gliding there.
            snap();
        }
        if (state != was) {
            if (hunted) {
                Minecraft mc = Minecraft.getInstance();
                if (state == Level228Hunt.HOLD) {
                    playFlat(mc, ModSounds.LEVEL228_OTHER_BREATH.get(), 0.9f, 1.0f);
                } else if (state == Level228Hunt.TAKE) {
                    crackTick = 0;
                    playFlat(mc, ModSounds.LEVEL228_OTHER_CRACK.get(), 1.0f, 0.95f + RANDOM.nextFloat() * 0.1f);
                }
            }
        }
    }

    private static void snap() {
        curX = prevX = goalX;
        curY = prevY = goalY;
        curZ = prevZ = goalZ;
        placed = true;
        moving = false;
    }

    private static boolean inLevel(Minecraft mc) {
        return mc.level != null && mc.player != null
                && BackroomsLevel.fromDimension(mc.level.dimension()) == BackroomsLevel.LEVEL_228;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!inLevel(mc)) {
            if (state != Level228Hunt.ABSENT || hush > 0.0f || selfHidden || crackTick >= 0) {
                state = Level228Hunt.ABSENT;
                placed = false;
                hush = 0.0f;
                crackTick = -1;
                hideSelf(false);
            }
            imageCount = 0;
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        ticks++;
        if (crackTick >= 0 && ++crackTick > WHITE_END) {
            crackTick = -1;
        }
        boolean holding = hunted && (state == Level228Hunt.HOLD || state == Level228Hunt.TAKE);
        float want = hunted && state == Level228Hunt.HOLD ? 1.0f : 0.0f;
        hush += (want - hush) * (want > hush ? 0.3f : 0.04f);
        if (hush < 0.002f) {
            hush = 0.0f;
        }
        hideSelf(holding);
        if (state == Level228Hunt.ABSENT || !placed) {
            imageCount = 0;
            return;
        }

        // Glide toward the server's position.
        prevX = curX;
        prevY = curY;
        prevZ = curZ;
        prevWalked = walked;
        double dx = goalX - curX;
        double dy = goalY - curY;
        double dz = goalZ - curZ;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > 4.0) {
            snap();
        } else {
            curX += dx * 0.35;
            curY += dy * 0.35;
            curZ += dz * 0.35;
            double moved = d * 0.35;
            moving = moved > 0.005 && state == Level228Hunt.STALK;
            if (moving) {
                walked += moved;
                stepAccum += moved;
            }
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition();
        imageCount = 0;
        int n = Level228Plan.imagesNear(seed, eye.x, eye.z, RANGE, FOUND);
        for (int i = 0; i < n && i < MAX_IMAGES; i++) {
            Level228Plan.Image im = FOUND[i];
            int k = imageCount++;
            I_MINX[k] = im.minX;
            I_MAXX[k] = im.maxX;
            I_MINY[k] = im.minY;
            I_MAXY[k] = im.maxY;
            I_MINZ[k] = im.minZ;
            I_MAXZ[k] = im.maxZ;
            I_FX[k] = im.flipX;
            I_FZ[k] = im.flipZ;
            I_OX[k] = im.offX;
            I_OZ[k] = im.offZ;
            I_AXIS[k] = im.paneAxis;
            I_PLANE[k] = im.panePlane;
        }

        if (stepAccum >= STRIDE) {
            stepAccum -= STRIDE;
            footstep(mc, eye);
        }
        glassTicks(mc, eye);
        if (state == Level228Hunt.STALK && ticks % SEEN_EVERY == 0 && anyImageSeen(mc, camera)) {
            PacketDistributor.sendToServer(Level228SeenPayload.INSTANCE);
        }
    }

    private static void hideSelf(boolean hide) {
        if (hide != selfHidden) {
            selfHidden = hide;
            Level228Reflections.setSelfHidden(hide);
        }
    }

    // ---- images ----

    // Level228Plan's map is per block (real = flip * img + off); for a point inside a block a
    // flipped axis also swaps the block's two faces, hence the +1 (see Level228Plan.Image).
    private static double imageX(int k, double rx) {
        return I_FX[k] > 0 ? rx - I_OX[k] : I_OX[k] + 1 - rx;
    }

    private static double imageZ(int k, double rz) {
        return I_FZ[k] > 0 ? rz - I_OZ[k] : I_OZ[k] + 1 - rz;
    }

    /** Whether the image point (ix, y, iz) lies inside image volume k (it only exists there). */
    private static boolean inside(int k, double ix, double y, double iz) {
        return ix >= I_MINX[k] && ix < I_MAXX[k] + 1 && iz >= I_MINZ[k] && iz < I_MAXZ[k] + 1
                && y >= I_MINY[k] - 0.5 && y < I_MAXY[k] + 1;
    }

    /** True if any of its images is on screen and nothing opaque stands between you and it. */
    private static boolean anyImageSeen(Minecraft mc, Camera camera) {
        Vec3 eye = camera.getPosition();
        Vector3f look = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        double tanV = Math.tan(Math.toRadians(mc.options.fov().get() * 0.5)) * SCREEN_MARGIN;
        double aspect = mc.getWindow().getWidth() / (double) Math.max(1, mc.getWindow().getHeight());
        double tanH = tanV * aspect;
        for (int k = 0; k < imageCount; k++) {
            double ix = imageX(k, curX);
            double iz = imageZ(k, curZ);
            if (!inside(k, ix, curY, iz)) {
                continue;
            }
            // Its head and its middle: either one in plain view counts.
            for (int s = 0; s < 2; s++) {
                double py = curY + (s == 0 ? HEIGHT - 0.25 : HIP_Y);
                double vx = ix - eye.x;
                double vy = py - eye.y;
                double vz = iz - eye.z;
                double dist = Math.sqrt(vx * vx + vy * vy + vz * vz);
                if (dist > RANGE || dist < 0.3) {
                    continue;
                }
                double f = vx * look.x() + vy * look.y() + vz * look.z();
                if (f <= 0.0) {
                    continue;
                }
                double h = Math.abs(vx * left.x() + vy * left.y() + vz * left.z()) / f;
                double v = Math.abs(vx * up.x() + vy * up.y() + vz * up.z()) / f;
                if (h <= tanH && v <= tanV && clearLine(mc.level, eye.x, eye.y, eye.z, ix, py, iz)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Walks the blocks on the line from a to b (voxel traversal) and returns false at the first
     * opaque full block. Glass panes are not opaque, so a mirror never hides what is behind it.
     */
    private static boolean clearLine(ClientLevel level, double ax, double ay, double az, double bx, double by, double bz) {
        int x = Mth.floor(ax);
        int y = Mth.floor(ay);
        int z = Mth.floor(az);
        int ex = Mth.floor(bx);
        int ey = Mth.floor(by);
        int ez = Mth.floor(bz);
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        int sx = dx > 0 ? 1 : -1;
        int sy = dy > 0 ? 1 : -1;
        int sz = dz > 0 ? 1 : -1;
        double tdx = dx == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
        double tdy = dy == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
        double tdz = dz == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
        double tx = dx == 0 ? Double.MAX_VALUE : (sx > 0 ? x + 1 - ax : ax - x) * tdx;
        double ty = dy == 0 ? Double.MAX_VALUE : (sy > 0 ? y + 1 - ay : ay - y) * tdy;
        double tz = dz == 0 ? Double.MAX_VALUE : (sz > 0 ? z + 1 - az : az - z) * tdz;
        for (int guard = 0; guard < 160; guard++) {
            if (x == ex && y == ey && z == ez) {
                return true;
            }
            if (tx < ty && tx < tz) {
                x += sx;
                tx += tdx;
            } else if (ty < tz) {
                y += sy;
                ty += tdy;
            } else {
                z += sz;
                tz += tdz;
            }
            if (x == ex && y == ey && z == ez) {
                return true;
            }
            CURSOR.set(x, y, z);
            if (level.getBlockState(CURSOR).isSolidRender(level, CURSOR)) {
                return false;
            }
        }
        return false;
    }

    // ---- sound ----

    /** One step, placed at its image nearest to you: from the mirror, never from where it really is. */
    private static void footstep(Minecraft mc, Vec3 eye) {
        int best = -1;
        double bestD = STEP_RANGE * STEP_RANGE;
        for (int k = 0; k < imageCount; k++) {
            double ix = imageX(k, curX);
            double iz = imageZ(k, curZ);
            if (!inside(k, ix, curY, iz)) {
                continue;
            }
            double d = (ix - eye.x) * (ix - eye.x) + (iz - eye.z) * (iz - eye.z);
            if (d < bestD) {
                bestD = d;
                best = k;
            }
        }
        if (best < 0) {
            return; // no mirror shows it near you: you don't hear it
        }
        mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.LEVEL228_OTHER_STEP.get(), SoundSource.HOSTILE,
                (0.75f + RANDOM.nextFloat() * 0.2f) * (1.0f - hush), 0.85f + RANDOM.nextFloat() * 0.15f, RANDOM,
                imageX(best, curX), curY + 0.1, imageZ(best, curZ)));
    }

    /**
     * Within {@value #TICK_RANGE} blocks (real distance) the glass ticks, more often the closer it
     * is: on the mirror pane nearest you, at about head height.
     */
    private static void glassTicks(Minecraft mc, Vec3 eye) {
        double d = Math.hypot(curX - eye.x, curZ - eye.z);
        if (d > TICK_RANGE || state == Level228Hunt.TAKE || hush > 0.3f || ticks < nextGlassTick) {
            return;
        }
        double close = 1.0 - d / TICK_RANGE;
        nextGlassTick = ticks + Mth.floor(Mth.lerp(close, 34.0, 6.0)) + RANDOM.nextInt(8);
        double px = eye.x + (RANDOM.nextDouble() - 0.5) * 3.0;
        double pz = eye.z + (RANDOM.nextDouble() - 0.5) * 3.0;
        double bestD = Double.MAX_VALUE;
        for (int k = 0; k < imageCount; k++) {
            double qx;
            double qz;
            if (I_AXIS[k] == 0) {
                qx = I_PLANE[k];
                qz = Mth.clamp(eye.z + (RANDOM.nextDouble() - 0.5) * 2.0, I_MINZ[k], I_MAXZ[k] + 1.0);
            } else {
                qz = I_PLANE[k];
                qx = Mth.clamp(eye.x + (RANDOM.nextDouble() - 0.5) * 2.0, I_MINX[k], I_MAXX[k] + 1.0);
            }
            double dd = (qx - eye.x) * (qx - eye.x) + (qz - eye.z) * (qz - eye.z);
            if (dd < bestD) {
                bestD = dd;
                px = qx;
                pz = qz;
            }
        }
        mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.LEVEL228_OTHER_TICK.get(), SoundSource.HOSTILE,
                (0.35f + 0.5f * (float) close), 0.9f + RANDOM.nextFloat() * 0.3f, RANDOM,
                px, eye.y - 0.8 + RANDOM.nextDouble() * 1.4, pz));
    }

    /** A sound in your head (no position): the breath, the crack. */
    private static void playFlat(Minecraft mc, SoundEvent sound, float volume, float pitch) {
        mc.getSoundManager().play(new SimpleSoundInstance(sound.getLocation(), SoundSource.HOSTILE, volume, pitch, RANDOM,
                false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
    }

    // ---- drawing ----

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || imageCount == 0
                || state == Level228Hunt.ABSENT || !placed) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double rx = Mth.lerp(pt, prevX, curX);
        double ry = Mth.lerp(pt, prevY, curY);
        double rz = Mth.lerp(pt, prevZ, curZ);
        float stride = (float) Mth.lerp(pt, prevWalked, walked);
        Vec3 cam = event.getCamera().getPosition();
        // Facing in real space (Minecraft yaw: 0 = +z, 90 = -x).
        float yawRad = yaw * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yawRad);
        double fz = Mth.cos(yawRad);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityCutoutNoCull(SKIN);
        VertexConsumer out = null;
        PoseStack poses = event.getPoseStack();
        for (int k = 0; k < imageCount; k++) {
            double ix = imageX(k, rx);
            double iz = imageZ(k, rz);
            if (!inside(k, ix, ry, iz)) {
                continue;
            }
            double ddx = ix - cam.x;
            double ddz = iz - cam.z;
            if (ddx * ddx + ddz * ddz > RANGE * RANGE) {
                continue;
            }
            if (out == null) {
                out = buffers.getBuffer(type);
            }
            // Facing in the image: the reflection maps the facing vector like any other.
            double ifx = fx * I_FX[k];
            double ifz = fz * I_FZ[k];
            float imageYaw = (float) Math.atan2(-ifx, ifz);
            boolean mirrored = I_FX[k] * I_FZ[k] < 0;
            CURSOR.set(Mth.floor(ix), Mth.floor(ry + 1.5), Mth.floor(iz));
            int packed = LevelRenderer.getLightColor(level, CURSOR);
            // A faint floor of its own pallor, so a dark mirror still shows a pale shape.
            int light = LightTexture.pack(Math.max(LightTexture.block(packed), 2), LightTexture.sky(packed));
            poses.pushPose();
            poses.translate(ix - cam.x, ry - cam.y, iz - cam.z);
            poses.mulPose(Axis.YP.rotation(-imageYaw));
            if (mirrored) {
                poses.scale(-1.0f, 1.0f, 1.0f); // odd number of reflections: left and right swap
            }
            drawFigure(poses, out, light, stride);
            poses.popPose();
        }
        if (out != null) {
            buffers.endBatch(type);
        }
    }

    /**
     * The figure, from boxes: long legs, a narrow torso stooped a little forward, arms hanging to
     * its knees, a small head tipped to one side (that asymmetry is what shows a mirrored image is
     * mirrored). Walking, the legs and arms swing slowly; frozen, it stands utterly still.
     */
    private static void drawFigure(PoseStack poses, VertexConsumer out, int light, float stride) {
        float swing = moving ? Mth.sin(stride * 2.7f) * 0.38f : 0.0f;
        // Legs (pivot at the hip).
        limb(poses, out, light, -0.085f, HIP_Y, 0.0f, swing, 0.055f, HIP_Y, PALE);
        limb(poses, out, light, 0.085f, HIP_Y, 0.0f, -swing, 0.055f, HIP_Y, PALE);
        // Torso, stooped: leaning forward from the hips.
        poses.pushPose();
        poses.translate(0.0f, HIP_Y, 0.0f);
        poses.mulPose(Axis.XP.rotation(0.12f));
        box(poses.last(), out, -0.17f, 0.0f, -0.07f, 0.17f, SHOULDER_Y - HIP_Y + 0.04f, 0.07f, PALE, light);
        // Arms hang from the shoulders (in the torso's frame, so they stoop with it).
        float sh = SHOULDER_Y - HIP_Y;
        limb(poses, out, light, -0.215f, sh, 0.0f, -swing * 0.5f, 0.038f, 1.36f, SHADE);
        limb(poses, out, light, 0.215f, sh, 0.0f, swing * 0.5f, 0.038f, 1.36f, SHADE);
        // Neck and head, tipped to its left.
        poses.translate(0.0f, sh, 0.02f);
        box(poses.last(), out, -0.035f, 0.0f, -0.035f, 0.035f, 0.16f, 0.035f, SHADE, light);
        poses.translate(0.0f, 0.15f, 0.0f);
        poses.mulPose(Axis.ZP.rotation(0.21f));
        poses.mulPose(Axis.XP.rotation(-0.1f));
        box(poses.last(), out, -0.1f, 0.0f, -0.11f, 0.1f, 0.33f, 0.1f, PALE, light);
        // Eyes: two dark hollows, a hair proud of the face so they never fight it for depth.
        box(poses.last(), out, -0.07f, 0.17f, 0.1f, -0.025f, 0.21f, 0.106f, EYES, light);
        box(poses.last(), out, 0.025f, 0.17f, 0.1f, 0.07f, 0.21f, 0.106f, EYES, light);
        // The mouth: a thin dark line, slightly open.
        box(poses.last(), out, -0.04f, 0.075f, 0.1f, 0.04f, 0.09f, 0.104f, EYES, light);
        poses.popPose();
    }

    /** A hanging limb of half-width hw and length len from the pivot (px, py, pz), swung by angle (radians). */
    private static void limb(PoseStack poses, VertexConsumer out, int light, float px, float py, float pz, float angle,
                             float hw, float len, int colour) {
        poses.pushPose();
        poses.translate(px, py, pz);
        if (angle != 0.0f) {
            poses.mulPose(Axis.XP.rotation(angle));
        }
        box(poses.last(), out, -hw, -len, -hw, hw, 0.0f, hw, colour, light);
        poses.popPose();
    }

    /** An axis-aligned box in the pose's space, each face carrying the whole (tiny) skin texture. */
    private static void box(PoseStack.Pose pose, VertexConsumer out, float x0, float y0, float z0, float x1, float y1, float z1,
                            int colour, int light) {
        face(pose, out, colour, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        face(pose, out, colour, light, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
        face(pose, out, colour, light, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
        face(pose, out, colour, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        face(pose, out, colour, light, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        face(pose, out, colour, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
    }

    private static void face(PoseStack.Pose pose, VertexConsumer out, int colour, int light, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        vertex(pose, out, ax, ay, az, 0.0f, 1.0f, colour, light, nx, ny, nz);
        vertex(pose, out, bx, by, bz, 1.0f, 1.0f, colour, light, nx, ny, nz);
        vertex(pose, out, cx, cy, cz, 1.0f, 0.0f, colour, light, nx, ny, nz);
        vertex(pose, out, dx, dy, dz, 0.0f, 0.0f, colour, light, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer out, float x, float y, float z, float u, float v, int colour,
                               int light, float nx, float ny, float nz) {
        out.addVertex(pose, x, y, z).setColor(colour).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    // ---- the catch on screen ----

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        if (crackTick < 0) {
            return;
        }
        float t = crackTick + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float white;
        if (t < WHITE_IN) {
            white = t / WHITE_IN;
        } else if (t < WHITE_HOLD) {
            white = 1.0f;
        } else {
            white = Math.max(0.0f, 1.0f - (t - WHITE_HOLD) / (WHITE_END - WHITE_HOLD));
        }
        // The crack is there at once and goes with the white as it lifts (you come to elsewhere).
        float crack = t < WHITE_HOLD ? 1.0f : white;
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth();
        int h = g.guiHeight();
        RenderSystem.enableBlend();
        g.setColor(1.0f, 1.0f, 1.0f, crack);
        // Square crack texture scaled to cover the screen (its impact point stays in the middle).
        int side = Math.max(w, h);
        g.blit(CRACK, (w - side) / 2, (h - side) / 2, side, side, 0.0f, 0.0f, CRACK_SIZE, CRACK_SIZE, CRACK_SIZE, CRACK_SIZE);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        int a = Mth.clamp((int) (white * white * 255.0f), 0, 255);
        if (a > 0) {
            g.fill(0, 0, w, h, a << 24 | 0xF4F6F8);
        }
        RenderSystem.disableBlend();
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (crackTick < 0 || crackTick > 10) {
            return;
        }
        // The glass giving way jolts the view once, dying out within half a second.
        float t = crackTick + (float) event.getPartialTick();
        float fade = Math.max(0.0f, 1.0f - t / 10.0f);
        event.setRoll(event.getRoll() + JOLT_ROLL * ClientSettings.tilt() * fade * Mth.sin(t * 2.2f));
    }
}
