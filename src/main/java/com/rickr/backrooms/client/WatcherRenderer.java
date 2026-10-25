package com.rickr.backrooms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rickr.backrooms.client.light.BackroomsLights;
import com.rickr.backrooms.entity.Watcher;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws a Watcher so you can SEE the threat in the pitch-dark street:
 * <ul>
 *   <li>the eyeball ({@link WatcherModel}) at full size (3.8 blocks), drawn full-bright: a pale,
 *       faintly self-lit sclera with dark veins, and a sickly amber glow around the pupil;</li>
 *   <li>its searchlight: three nested additive cones, brightest at the pupil and fading with length,
 *       with soft edges (faces seen edge-on fade out, so it reads like a beam of light in fog rather
 *       than a plastic cone). It stops where the light hits something;</li>
 *   <li>a soft elliptical pool of light where the beam lands (works with or without Veil; with Veil
 *       the real spot light from {@code WatcherSearchlights} lights the street as well);</li>
 *   <li>while it stares the cone narrows and brightens; while it charges, a searing beam to you.</li>
 * </ul>
 */
public class WatcherRenderer extends EntityRenderer<Watcher> {
    /** The model is built 3 blocks across; scale it up to the Watcher's real size. */
    private static final float MODEL_SCALE = Watcher.HALF_SIZE / WatcherModel.RADIUS;
    /** Longest the visible beam gets (it stops earlier where it hits the street or a wall). */
    public static final float CONE_MAX_LENGTH = 32.0f;
    private static final int CONE_SIDES = 32;
    private static final int CONE_SLICES = 8;
    /** Nested cones: relative width and brightness. The narrow inner ones make a denser core. */
    private static final float[] SHELL_WIDTH = {1.0f, 0.62f, 0.3f};
    private static final float[] SHELL_ALPHA = {0.30f, 0.38f, 0.55f};
    /** Searchlight half-angles (degrees): wide while patrolling, narrow while staring, tightest when charging. */
    private static final float CALM_HALF_ANGLE = Watcher.VISION_HALF_ANGLE;
    private static final float STARE_HALF_ANGLE = 14.0f;
    private static final float CHARGE_HALF_ANGLE = 7.0f;
    /** Pupil sizes (angular radius, radians). */
    private static final float PUPIL_RELAXED = 0.30f;
    private static final float PUPIL_STARE = 0.17f;
    private static final float PUPIL_CHARGED = 0.09f;
    /** Cold, pale searchlight colour; heats up to a pinkish white while charging. */
    private static final int LIGHT_R = 215;
    private static final int LIGHT_G = 228;
    private static final int LIGHT_B = 255;

    private final WatcherModel model = new WatcherModel();
    // Scratch objects (render thread only).
    private final Quaternionf frame = new Quaternionf();
    private final Vector3f camLocal = new Vector3f();
    private final Vector3f normal = new Vector3f();
    private final Vector3f toCam = new Vector3f();

    public WatcherRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f; // it floats far above the street
    }

    /** Its tendrils and light reach far outside the hitbox, so cull a much bigger box. */
    @Override
    public boolean shouldRender(Watcher entity, Frustum frustum, double camX, double camY, double camZ) {
        if (entity.lockedTargetId() >= 0) {
            return true; // the beam can cross the screen even when the eye itself is off it
        }
        return entity.shouldRender(camX, camY, camZ) && frustum.isVisible(entity.getBoundingBox().inflate(CONE_MAX_LENGTH * 0.7));
    }

    // ---- Shared with WatcherSearchlights -----------------------------------------------------

    /** Searchlight half-angle in radians for this state. */
    public static float halfAngle(Watcher.State state, float charge) {
        float degrees = switch (state) {
            case STARE -> STARE_HALF_ANGLE;
            case CHARGE -> Mth.lerp(charge, STARE_HALF_ANGLE, CHARGE_HALF_ANGLE);
            default -> CALM_HALF_ANGLE;
        };
        return (float) Math.toRadians(degrees);
    }

    /** 1 while patrolling, brighter while staring, brightest at full charge. */
    public static float lightStrength(Watcher.State state, float charge) {
        return switch (state) {
            case STARE -> 1.45f;
            case CHARGE -> 1.45f + 1.2f * charge;
            default -> 1.0f;
        };
    }

    /** Where the pupil is (world space) at this frame. */
    public static Vec3 pupilPosition(Watcher entity, float partialTick) {
        Vec3 look = entity.getViewVector(partialTick);
        return entity.getPosition(partialTick).add(0.0, Watcher.HALF_SIZE, 0.0).add(look.scale(Watcher.HALF_SIZE));
    }

    /** First block the centre of the searchlight hits, or null within {@code maxLength}. */
    @Nullable
    public static BlockHitResult beamHit(Watcher entity, float partialTick, double maxLength) {
        Vec3 from = pupilPosition(entity, partialTick);
        Vec3 to = from.add(entity.getViewVector(partialTick).scale(maxLength));
        BlockHitResult hit = entity.level().clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    // ---- Drawing -----------------------------------------------------------------------------

    @Override
    public void render(Watcher entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        float yaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        float age = entity.tickCount + partialTick;
        Watcher.State state = entity.state();
        float charge = state == Watcher.State.CHARGE ? entity.charge() : 0.0f;
        float pupil = pupilSize(entity, state, charge, age);
        float strength = lightStrength(state, charge);
        float halfAngle = halfAngle(state, charge);
        boolean veil = BackroomsLights.isVeilLightingActive();

        BlockHitResult hit = beamHit(entity, partialTick, CONE_MAX_LENGTH);
        Vec3 pupilPos = pupilPosition(entity, partialTick);
        float coneLength = hit != null ? (float) hit.getLocation().distanceTo(pupilPos) : CONE_MAX_LENGTH;

        poseStack.pushPose();
        poseStack.translate(0.0, Watcher.HALF_SIZE, 0.0);
        // Turn eye space (+Z = pupil) to where it looks: yaw around Y, then pitch (positive = down).
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));

        // The eye itself, scaled up, with a slow heavy roll as it floats. Full-bright: its colours are
        // its brightness, so it reads in the dark (and fades into the fog with distance).
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(4.0f * Mth.sin(age * 0.02f + entity.getId())));
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        model.render(poseStack, buffers.getBuffer(WatcherRenderTypes.SOLID), LightTexture.FULL_BRIGHT, age, pupil, entity.getId() * 1.37f);
        VertexConsumer glow = buffers.getBuffer(WatcherRenderTypes.GLOW);
        model.renderIrisGlow(poseStack, glow, pupil, Math.min(1.0f, 0.55f + 0.3f * (strength - 1.0f)));
        poseStack.popPose();

        // Camera position in the eye's frame, for the soft cone edges.
        Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
        Vec3 eyeCentre = entity.getPosition(partialTick).add(0.0, Watcher.HALF_SIZE, 0.0);
        frame.rotationY((float) Math.toRadians(-yaw)).rotateX((float) Math.toRadians(pitch));
        camLocal.set((float) (cam.x - eyeCentre.x), (float) (cam.y - eyeCentre.y), (float) (cam.z - eyeCentre.z));
        frame.conjugate(new Quaternionf()).transform(camLocal);

        float pupilRadius = Watcher.HALF_SIZE * Mth.sin(pupil);
        drawCone(poseStack.last(), glow, pupilRadius, coneLength, halfAngle, strength * (veil ? 0.8f : 1.0f), charge);
        if (charge > 0.0f) {
            Entity target = entity.lockedTarget();
            float length = target != null ? (float) target.getEyePosition(partialTick).distanceTo(eyeCentre) : coneLength;
            drawBeam(poseStack.last(), glow, length, charge, age);
        }
        poseStack.popPose();

        // The pool of light where the beam lands (positions relative to the entity's origin).
        if (hit != null) {
            Vec3 origin = entity.getPosition(partialTick);
            float poolStrength = strength * (veil ? 0.6f : 1.0f);
            drawPool(poseStack.last(), glow, hit, origin, entity.getViewVector(partialTick), coneLength, halfAngle, poolStrength, charge);
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    /** Wide and slowly "breathing" while calm, narrowing when it stares, a pinpoint while charging. */
    private static float pupilSize(Watcher entity, Watcher.State state, float charge, float age) {
        float target = switch (state) {
            case STARE -> PUPIL_STARE;
            case CHARGE -> Mth.lerp(charge, PUPIL_STARE, PUPIL_CHARGED);
            default -> PUPIL_RELAXED + 0.04f * Mth.sin(age * 0.03f);
        };
        long now = System.nanoTime();
        if (entity.renderPupil < 0.0f) {
            entity.renderPupil = target;
        } else {
            // Frame-rate independent easing: slow to dilate, quick to snap narrow when it spots you.
            float seconds = Math.min((now - entity.renderPupilNanos) / 1.0e9f, 0.25f);
            float rate = target < entity.renderPupil ? 6.0f : 1.0f;
            entity.renderPupil += (target - entity.renderPupil) * (1.0f - (float) Math.exp(-rate * seconds));
        }
        entity.renderPupilNanos = now;
        return entity.renderPupil;
    }

    /**
     * Nested additive cones along +Z from the pupil. Each vertex's brightness = shell brightness x
     * fade along the length x how directly that bit of surface faces the camera (edge-on parts fade
     * out, which gives soft, volumetric-looking edges).
     */
    private void drawCone(PoseStack.Pose pose, VertexConsumer out, float startRadius, float length, float halfAngle,
                          float strength, float charge) {
        float startZ = Watcher.HALF_SIZE * 1.01f;
        float spread = (float) Math.tan(halfAngle);
        int r = (int) Mth.lerp(charge, LIGHT_R, 255);
        int g = (int) Mth.lerp(charge, LIGHT_G, 226);
        int b = (int) Mth.lerp(charge, LIGHT_B, 232);
        for (int shell = 0; shell < SHELL_WIDTH.length; shell++) {
            float width = SHELL_WIDTH[shell];
            float baseAlpha = SHELL_ALPHA[shell] * strength;
            for (int slice = 0; slice < CONE_SLICES; slice++) {
                float t0 = slice / (float) CONE_SLICES;
                float t1 = (slice + 1) / (float) CONE_SLICES;
                float z0 = startZ + t0 * length;
                float z1 = startZ + t1 * length;
                float r0 = (startRadius + t0 * length * spread) * width;
                float r1 = (startRadius + t1 * length * spread) * width;
                // Brightest at the pupil, fading with distance (but still reaching the ground).
                float a0 = baseAlpha * fade(t0);
                float a1 = baseAlpha * fade(t1);
                for (int i = 0; i < CONE_SIDES; i++) {
                    float ang0 = Mth.TWO_PI * i / CONE_SIDES;
                    float ang1 = Mth.TWO_PI * (i + 1) / CONE_SIDES;
                    coneVertex(pose, out, ang0, r0, z0, spread * width, a0, r, g, b);
                    coneVertex(pose, out, ang1, r0, z0, spread * width, a0, r, g, b);
                    coneVertex(pose, out, ang1, r1, z1, spread * width, a1, r, g, b);
                    coneVertex(pose, out, ang0, r1, z1, spread * width, a1, r, g, b);
                }
            }
        }
    }

    private static float fade(float t) {
        return (1.0f - 0.75f * t) * (1.0f - t * t * t); // gentle falloff, gone at the very end
    }

    private void coneVertex(PoseStack.Pose pose, VertexConsumer out, float angle, float radius, float z, float slope,
                            float alpha, int r, int g, int b) {
        float x = Mth.cos(angle) * radius;
        float y = Mth.sin(angle) * radius;
        normal.set(Mth.cos(angle), Mth.sin(angle), -slope).normalize();
        toCam.set(camLocal.x - x, camLocal.y - y, camLocal.z - z).normalize();
        float facing = Math.abs(normal.dot(toCam));
        float soft = facing * (float) Math.sqrt(facing); // facing^1.5: edge-on surfaces fade out
        int a = Mth.clamp((int) (alpha * soft * 255.0f), 0, 255);
        out.addVertex(pose, x, y, z).setColor(r, g, b, a);
    }

    /**
     * The charging beam: three crossed ribbons along +Z (so it looks solid from any side), growing
     * thicker and brighter with the charge and crackling a little.
     */
    private static void drawBeam(PoseStack.Pose pose, VertexConsumer out, float length, float charge, float age) {
        float crackle = 0.85f + 0.15f * Mth.sin(age * 2.7f) * Mth.sin(age * 1.3f);
        float width = (0.06f + 0.28f * charge) * crackle;
        int alpha = (int) (255 * Mth.clamp(charge * 1.2f, 0.0f, 1.0f) * crackle);
        float z0 = Watcher.HALF_SIZE * 1.01f;
        float z1 = z0 + length;
        for (int i = 0; i < 3; i++) {
            float a = Mth.PI * i / 3.0f;
            float dx = Mth.cos(a) * width;
            float dy = Mth.sin(a) * width;
            glowVertex(pose, out, -dx, -dy, z0, 255, 200, 210, alpha);
            glowVertex(pose, out, dx, dy, z0, 255, 200, 210, alpha);
            glowVertex(pose, out, dx, dy, z1, 255, 235, 240, alpha);
            glowVertex(pose, out, -dx, -dy, z1, 255, 235, 240, alpha);
        }
    }

    /**
     * A soft ellipse of light on the surface the beam hits: as wide as the cone is at that distance,
     * stretched along the beam when it comes in at a slant, bright in the middle and fading to nothing
     * at the rim.
     */
    private static void drawPool(PoseStack.Pose pose, VertexConsumer out, BlockHitResult hit, Vec3 origin, Vec3 look,
                                 float distance, float halfAngle, float strength, float charge) {
        Vector3f n = hit.getDirection().step();
        Vec3 normalVec = new Vec3(n.x(), n.y(), n.z());
        // Axis "u": the beam's direction flattened onto the surface; "v" across it.
        Vec3 u = look.subtract(normalVec.scale(look.dot(normalVec)));
        if (u.lengthSqr() < 1.0e-4) {
            u = Math.abs(normalVec.y) < 0.9 ? new Vec3(0, 1, 0).cross(normalVec) : new Vec3(1, 0, 0).cross(normalVec);
        }
        u = u.normalize();
        Vec3 v = normalVec.cross(u).normalize();
        float radius = Mth.clamp(distance * (float) Math.tan(halfAngle), 0.8f, 10.0f);
        float stretch = Mth.clamp(1.0f / (float) Math.max(0.05, Math.abs(look.dot(normalVec))), 1.0f, 3.0f);
        Vec3 centre = hit.getLocation().add(normalVec.scale(0.04)).subtract(origin);

        int r = (int) Mth.lerp(charge, LIGHT_R, 255);
        int g = (int) Mth.lerp(charge, LIGHT_G, 226);
        int b = (int) Mth.lerp(charge, LIGHT_B, 232);
        float[] rings = {0.0f, 0.45f, 1.0f};
        float[] alphas = {0.42f * strength, 0.24f * strength, 0.0f};
        int sides = 28;
        for (int ring = 0; ring < 2; ring++) {
            for (int i = 0; i < sides; i++) {
                float a0 = Mth.TWO_PI * i / sides;
                float a1 = Mth.TWO_PI * (i + 1) / sides;
                poolVertex(pose, out, centre, u, v, a0, rings[ring] * radius, stretch, alphas[ring], r, g, b);
                poolVertex(pose, out, centre, u, v, a0, rings[ring + 1] * radius, stretch, alphas[ring + 1], r, g, b);
                poolVertex(pose, out, centre, u, v, a1, rings[ring + 1] * radius, stretch, alphas[ring + 1], r, g, b);
                poolVertex(pose, out, centre, u, v, a1, rings[ring] * radius, stretch, alphas[ring], r, g, b);
            }
        }
    }

    private static void poolVertex(PoseStack.Pose pose, VertexConsumer out, Vec3 centre, Vec3 u, Vec3 v, float angle,
                                   float radius, float stretch, float alpha, int r, int g, int b) {
        double du = Mth.cos(angle) * radius * stretch;
        double dv = Mth.sin(angle) * radius;
        glowVertex(pose, out, (float) (centre.x + u.x * du + v.x * dv), (float) (centre.y + u.y * du + v.y * dv),
                (float) (centre.z + u.z * du + v.z * dv), r, g, b, Mth.clamp((int) (alpha * 255.0f), 0, 255));
    }

    private static void glowVertex(PoseStack.Pose pose, VertexConsumer out, float x, float y, float z, int r, int g, int b, int a) {
        out.addVertex(pose, x, y, z).setColor(r, g, b, a);
    }

    @Override
    public ResourceLocation getTextureLocation(Watcher entity) {
        return WatcherRenderTypes.WHITE;
    }
}
