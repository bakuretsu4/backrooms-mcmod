package com.rickr.backrooms.client.level228;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.Level228ClientState;
import com.rickr.backrooms.mixin.client.FrustumInvoker;
import com.rickr.backrooms.mixin.client.WalkAnimationStateAccessor;
import com.rickr.backrooms.network.Level228MirrorPayload;
import com.rickr.backrooms.world.layout.Level228Plan;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Vector3f;

/**
 * Level 228's reflections of players: "puppets".
 *
 * <p>A Level 228 mirror is a glass pane with a real, mirrored copy of the maze built behind it
 * (an image volume, world/layout/Level228Plan). Blocks, lights and fog of the image are real, so
 * every renderer gets them right; what the world can't contain is you. So for every player near
 * the camera (you too, in first person as well) and every image volume around (up to depth
 * {@value #MAX_DEPTH}, box on screen), the player is drawn a second time where the mirror map puts
 * them: {@code img = flip * (real - off - 0.5) + 0.5} per axis (the block map
 * {@code realBlock = flip * imgBlock + off} for continuous points; Y unchanged).
 *
 * <p><b>How.</b> The real player entity is drawn by its own renderer (skin, armour, held
 * camcorder, cape, limb swing, crouch, arm swing: everything as the player really is) into a pose
 * stack that holds the mirror map's linear part, {@code scale(flipX, 1, flipZ)}, about the
 * puppet's feet. That one transform mirrors position, body and head yaw, pitch and handedness
 * together. With an odd number of flips the model comes out inside out; {@link PuppetBuffers}
 * winds its quads the other way so culling keeps the outside. Lighting is the image's own (its
 * mirrored lights), read at the puppet's position.
 *
 * <p><b>When.</b> In {@code AFTER_ENTITIES}, like the real entities: opaque geometry is in the
 * depth buffer (image walls hide puppets that are round a corner), and the translucent mirror
 * panes are drawn after, over the puppets, with the fog on both. Plain, Veil and Iris (shader
 * packs such as Complementary draw it with their entity program) all go through this stage. If a
 * pack ever misbehaves there, {@code /backrooms mirror stage <...>} switches the stage at run time
 * for testing.
 *
 * <p>Spectators and invisible players have no reflection; neither have players hidden by the
 * mod's hiding (their render is cancelled the same way). Nothing here allocates per frame:
 * the image array, pose and buffers are preallocated, and the players are walked by index.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level228Reflections {
    /** Image volumes are gathered this far around the camera (blocks). */
    private static final double SEARCH = 24.0;
    /** Deeper images than this are lost in the coloured dark anyway. */
    private static final int MAX_DEPTH = 4;
    /** Puppets farther than this from the camera are not drawn (blocks; the fog has them). */
    private static final double MAX_DISTANCE = 40.0;
    private static final int MAX_IMAGES = 64;

    /** Render stages to draw in: see {@link #setStage}. */
    public static final int STAGE_ENTITIES = 0;
    public static final int STAGE_BLOCK_ENTITIES = 1;
    public static final int STAGE_SOLID_BLOCKS = 2;

    private static final Level228Plan.Image[] IMAGES = new Level228Plan.Image[MAX_IMAGES];
    private static final PuppetBuffers BUFFERS = new PuppetBuffers();
    private static final PuppetPose POSE = new PuppetPose();
    private static final BlockPos.MutableBlockPos LIGHT_POS = new BlockPos.MutableBlockPos();

    static {
        for (int i = 0; i < MAX_IMAGES; i++) {
            IMAGES[i] = new Level228Plan.Image();
        }
    }

    private static int stage = STAGE_ENTITIES;
    private static boolean selfHidden;
    private static int selfDrawn;
    private static boolean wasInLevel;
    /** While a lying puppet is drawn: crouch it like this (-1: as the player is). */
    private static int crouchOverride = -1;

    private Level228Reflections() {}

    // ==========================================================================================
    // API for the other Level 228 parts and the debug command
    // ==========================================================================================

    /**
     * Hides (or shows again) all reflections of the LOCAL player. The thing in the mirrors uses
     * this for its moment (client/level228 hunter code): your reflection is simply not there.
     */
    public static void setSelfHidden(boolean hidden) {
        selfHidden = hidden;
    }

    /** How many reflections of the local player were drawn in the last frame. */
    public static int puppetsDrawn() {
        return selfDrawn;
    }

    /** Testing: which render stage the puppets are drawn in (one of the STAGE_ constants). */
    public static void setStage(int which) {
        stage = Mth.clamp(which, STAGE_ENTITIES, STAGE_SOLID_BLOCKS);
    }

    /** Testing ({@code /backrooms mirror lie ...}): this lie (Level228Lies.LAG etc.) as soon as it can happen. */
    public static void forceLie(int kind) {
        Level228Lies.force(kind);
    }

    /** Client thread: {@code /backrooms mirror ...} (network/Level228MirrorPayload). */
    public static void receive(Level228MirrorPayload payload) {
        switch (payload.kind()) {
            case Level228MirrorPayload.LIE_LAG -> forceLie(Level228Lies.LAG);
            case Level228MirrorPayload.LIE_LOOK -> forceLie(Level228Lies.LOOK);
            case Level228MirrorPayload.LIE_WALK -> forceLie(Level228Lies.WALK);
            case Level228MirrorPayload.LIE_STILL -> forceLie(Level228Lies.STILL);
            case Level228MirrorPayload.STAGE_ENTITIES -> setStage(STAGE_ENTITIES);
            case Level228MirrorPayload.STAGE_BLOCK_ENTITIES -> setStage(STAGE_BLOCK_ENTITIES);
            case Level228MirrorPayload.STAGE_SOLID_BLOCKS -> setStage(STAGE_SOLID_BLOCKS);
            default -> {
                // An unknown kind from a newer server: ignore it.
            }
        }
    }

    // ==========================================================================================
    // Ticks
    // ==========================================================================================

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean in = mc.level != null && mc.player != null && Level228ClientState.inLevel228() && Level228ClientState.hasSeed();
        if (!in) {
            if (wasInLevel) {
                Level228Lies.reset();
                selfDrawn = 0;
                wasInLevel = false;
            }
            return;
        }
        wasInLevel = true;
        if (mc.isPaused()) {
            return;
        }
        Level228Lies.tick(mc.player, Level228ClientState.seed());
    }

    // ==========================================================================================
    // Drawing
    // ==========================================================================================

    private static RenderLevelStageEvent.Stage drawStage() {
        return switch (stage) {
            case STAGE_BLOCK_ENTITIES -> RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES;
            case STAGE_SOLID_BLOCKS -> RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS;
            default -> RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        };
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != drawStage()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || !Level228ClientState.inLevel228() || !Level228ClientState.hasSeed()) {
            selfDrawn = 0;
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        Vector3f look = event.getCamera().getLookVector();
        FrustumInvoker frustum = (FrustumInvoker) event.getFrustum();
        int count = Math.min(Level228Plan.imagesNear(Level228ClientState.seed(), cam.x, cam.z, SEARCH, IMAGES), MAX_IMAGES);
        Level228Lies.beginFrame(cam);
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
        List<AbstractClientPlayer> players = level.players();
        int drawnSelf = 0;
        for (int i = 0; i < count; i++) {
            Level228Plan.Image img = IMAGES[i];
            if (img.depth > MAX_DEPTH || img.depth < 1) {
                continue;
            }
            if (!frustum.backrooms$cubeInFrustum(img.minX, img.minY, img.minZ, img.maxX + 1.0, img.maxY + 1.0, img.maxZ + 1.0)) {
                continue;
            }
            boolean lieImage = Level228Lies.isLieImage(img);
            if (lieImage) {
                Level228Lies.noteSeen();
            }
            for (int k = 0; k < players.size(); k++) {
                AbstractClientPlayer p = players.get(k);
                if (p.isSpectator() || p.isInvisible() || !p.isAlive() || p.isRemoved()) {
                    continue;
                }
                boolean self = p == mc.player;
                if (self && selfHidden) {
                    continue;
                }
                livePose(img, p, pt);
                if (self && lieImage && !Level228Lies.apply(p, pt, cam, look, POSE)) {
                    continue;
                }
                if (!inside(img, POSE)) {
                    continue; // you aren't where this mirror reflects (or the lie walked out of it)
                }
                double dx = POSE.x - cam.x;
                double dy = POSE.y + 1.0 - cam.y;
                double dz = POSE.z - cam.z;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist > MAX_DISTANCE
                        || !frustum.backrooms$cubeInFrustum(POSE.x - 0.5, POSE.y, POSE.z - 0.5, POSE.x + 0.5, POSE.y + 2.0, POSE.z + 0.5)) {
                    continue;
                }
                draw(mc, level, p, img, pt, cam, poseStack, source);
                if (self) {
                    drawnSelf++;
                    if (!lieImage) {
                        Level228Lies.offer(img, p, pt, POSE, dist);
                    }
                }
            }
        }
        BUFFERS.endBatches();
        selfDrawn = drawnSelf;
        Level228Lies.endFrame();
    }

    /** The plain reflection: the player as they are now, mapped into the image. */
    private static void livePose(Level228Plan.Image img, AbstractClientPlayer p, float pt) {
        double rx = Mth.lerp(pt, p.xo, p.getX());
        double ry = Mth.lerp(pt, p.yo, p.getY());
        double rz = Mth.lerp(pt, p.zo, p.getZ());
        POSE.x = img.flipX * (rx - img.offX - 0.5) + 0.5;
        POSE.y = ry;
        POSE.z = img.flipZ * (rz - img.offZ - 0.5) + 0.5;
        POSE.override = false;
        POSE.crouch = p.isCrouching();
        POSE.alpha = 1.0f;
    }

    private static boolean inside(Level228Plan.Image img, PuppetPose pose) {
        return pose.x >= img.minX && pose.x < img.maxX + 1.0
                && pose.z >= img.minZ && pose.z < img.maxZ + 1.0
                && pose.y >= img.minY && pose.y < img.maxY + 1.0;
    }

    private static void draw(Minecraft mc, ClientLevel level, AbstractClientPlayer p, Level228Plan.Image img, float pt, Vec3 cam,
            PoseStack poseStack, MultiBufferSource.BufferSource source) {
        PuppetPose pose = POSE;
        EntityRenderer<? super AbstractClientPlayer> renderer = mc.getEntityRenderDispatcher().getRenderer(p);
        BUFFERS.begin(source, img.flipX * img.flipZ < 0, pose.alpha);
        LIGHT_POS.set(Mth.floor(pose.x), Mth.floor(pose.y + 1.0), Mth.floor(pose.z));
        int light = LevelRenderer.getLightColor(level, LIGHT_POS);

        // What the lie changes on the entity, put back right after the draw.
        float body = p.yBodyRot;
        float bodyO = p.yBodyRotO;
        float head = p.yHeadRot;
        float headO = p.yHeadRotO;
        float pitch = p.getXRot();
        float pitchO = p.xRotO;
        float attack = p.attackAnim;
        float attackO = p.oAttackAnim;
        WalkAnimationStateAccessor walk = (WalkAnimationStateAccessor) p.walkAnimation;
        float walkSpeedOld = walk.backrooms$speedOld();
        float walkSpeed = walk.backrooms$speed();
        float walkPos = walk.backrooms$position();
        if (pose.override) {
            p.yBodyRot = pose.body;
            p.yBodyRotO = pose.body;
            p.yHeadRot = pose.head;
            p.yHeadRotO = pose.head;
            p.setXRot(pose.pitch);
            p.xRotO = pose.pitch;
            p.attackAnim = pose.attack;
            p.oAttackAnim = pose.attack;
            walk.backrooms$setSpeedOld(pose.walkSpeed);
            walk.backrooms$setSpeed(pose.walkSpeed);
            // position(pt) = position - speed * (1 - pt): stored so that it reads walkPos at this pt.
            walk.backrooms$setPosition(pose.walkPos + pose.walkSpeed * (1.0f - pt));
            crouchOverride = pose.crouch ? 1 : 0;
        }
        poseStack.pushPose();
        try {
            poseStack.translate(pose.x - cam.x, pose.y - cam.y, pose.z - cam.z);
            // The mirror map's linear part about the feet: mirrors yaw, pitch, limbs and handedness at once.
            poseStack.scale(img.flipX, 1.0f, img.flipZ);
            if (pose.crouch) {
                poseStack.translate(0.0, p.getScale() * -2.0 / 16.0, 0.0); // the crouch offset PlayerRenderer asks for
            }
            renderer.render(p, 0.0f, pt, poseStack, BUFFERS, light);
        } finally {
            poseStack.popPose();
            crouchOverride = -1;
            if (pose.override) {
                p.yBodyRot = body;
                p.yBodyRotO = bodyO;
                p.yHeadRot = head;
                p.yHeadRotO = headO;
                p.setXRot(pitch);
                p.xRotO = pitchO;
                p.attackAnim = attack;
                p.oAttackAnim = attackO;
                walk.backrooms$setSpeedOld(walkSpeedOld);
                walk.backrooms$setSpeed(walkSpeed);
                walk.backrooms$setPosition(walkPos);
            }
        }
    }

    /**
     * The player renderer sets the model's crouch from the entity just before this event; a lying
     * reflection crouches (or doesn't) as its own pose says instead.
     */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (crouchOverride >= 0) {
            event.getRenderer().getModel().crouching = crouchOverride == 1;
        }
    }
}
