package com.rickr.backrooms.client.level228;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.Level228ClientState;
import com.rickr.backrooms.mixin.client.FrustumInvoker;
import com.rickr.backrooms.mixin.client.WalkAnimationStateAccessor;
import com.rickr.backrooms.network.Level228MirrorPayload;
import com.rickr.backrooms.world.Hash;
import com.rickr.backrooms.world.layout.Level228Plan;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Level 228's reflections of people: "stand-ins".
 *
 * <p>Every mirror in the maze shows real geometry: the twin half of a kaleidoscope room, or a
 * sealed mirrored copy built behind the pane (world/layout/Level228Plan). Blocks, props, lights
 * and fog of a reflection are therefore real and right under any renderer. What the world can't
 * contain is people. So:
 * <ul>
 *   <li><b>Stand-ins.</b> For every player near the camera (you too, first person included) the
 *       player is drawn again at every image {@link Level228Plan#imagesOf} finds visible from the
 *       camera, by its own renderer (skin and layers, armour, held items, camcorder, crouch,
 *       swimming, using an item, limb swing, arm swing), into a pose stack that holds the mirror
 *       map's linear part, {@code scale(flipX, 1, flipZ)}, about the image's feet. That one
 *       transform mirrors position, yaw, pitch and handedness together (the item in your right
 *       hand is in the reflection's left). An odd number of flips turns the model inside out;
 *       {@link PuppetBuffers} winds its quads the other way so culling keeps the outside.</li>
 *   <li><b>Hiding the real body.</b> A kaleidoscope room's twin space is real and walkable, so a
 *       teammate standing there would show through the pane where only their reflection can be.
 *       Any entity whose centre is behind a mirror as seen from the camera
 *       ({@link Level228Plan#behindMirror}) is not drawn (mixin/client/EntityRenderDispatcherMixin);
 *       their stand-ins are, where the mirrors put them.</li>
 *   <li><b>Name tags.</b> Stand-ins never carry one: a name tag means a real body. Together with
 *       the hiding, a person you see with a name above them is a person.</li>
 *   <li><b>The exit puzzle</b> (final chamber): the three mirrors are sealed images; images through
 *       the two wrong ones carry a fixed flaw ({@link Level228Plan#exitFlaw}, a function of the
 *       world seed): late by 0.4-0.7 s, the item in the wrong hand, a piece of what you wear
 *       missing, a head that nods out of time, or a rotated (unmirrored) body. Deterministic for
 *       every player and every viewer (same seed, same per-player pose history, same game time),
 *       so teammates see the same flaws on everyone. The flaws of the image's geometry (an unlit
 *       candle, a missing detail) are built into the sealed image itself.</li>
 *   <li><b>Funhouse mirrors</b> (distorting variant): the stand-in is stretched tall and thin,
 *       squashed short and wide, or slowly wavering, by {@link #distortion}.</li>
 *   <li><b>Lies</b> ({@link Level228Lies}): rare personal hallucinations on your own reflections
 *       only, never through a puzzle mirror, never in the final chamber or exit hallway.</li>
 *   <li><b>The imitator</b> (client/level228 imitator code) registers a {@link FigureSource}; its
 *       figures are drawn only at their images, through the same path.</li>
 * </ul>
 *
 * <p><b>When.</b> In {@code AFTER_ENTITIES}, like the real entities: opaque geometry is in the
 * depth buffer (image walls and columns hide stand-ins round a corner), and the translucent
 * mirror surfaces are drawn after, over the stand-ins. Plain, Veil and Iris (shader packs draw it
 * with their entity program) all go through this stage; {@code /backrooms mirror stage <...>}
 * moves it at run time should a pack misbehave.
 *
 * <p>At most {@value #MAX_STANDINS} stand-ins a frame (figures first, then you, then the others).
 * Nothing here allocates per frame: images, mirrors, pose, matrices and buffers are preallocated,
 * players are walked by index.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level228Reflections {
    /** Images are asked for this far around the camera (blocks): about where the fog has them. */
    private static final double RADIUS = 28.0;
    /** Stand-ins drawn a frame at most. */
    public static final int MAX_STANDINS = 48;
    private static final int MAX_IMAGES = 48;
    private static final int MAX_MIRRORS = 256;
    /** Real bodies farther than this are not tested for hiding (the fog has them anyway). */
    private static final double HIDE_RANGE = 48.0;
    /** The imitator's figures a frame at most. */
    public static final int MAX_FIGURES = 4;
    /** How far beyond a body's centre it is tested for standing behind a mirror (see {@link #hiddenBehindMirror}). */
    private static final double STRADDLE = 0.35;
    /**
     * Sideways probes (blocks) besides the centre when looking for a body's images: a reflection
     * whose centre is hidden behind a pier or column can still show a shoulder past its edge.
     * The depth buffer clips what the geometry hides, so drawing the whole stand-in is honest.
     */
    private static final double PROBE = 0.3;

    /** Render stages to draw in: see {@link #setStage}. */
    public static final int STAGE_ENTITIES = 0;
    public static final int STAGE_BLOCK_ENTITIES = 1;
    public static final int STAGE_SOLID_BLOCKS = 2;

    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    private static final Level228Plan.Image[] IMAGES = new Level228Plan.Image[MAX_IMAGES];
    /** This body's distinct images (centre positions), gathered over the probes. */
    private static final Level228Plan.Image[] FOUND = new Level228Plan.Image[MAX_IMAGES];
    private static final Level228Plan.Mirror[] MIRRORS = new Level228Plan.Mirror[MAX_MIRRORS];
    private static final PuppetBuffers BUFFERS = new PuppetBuffers();
    private static final PuppetPose POSE = new PuppetPose();
    private static final BlockPos.MutableBlockPos LIGHT_POS = new BlockPos.MutableBlockPos();
    private static final Quaternionf ROT = new Quaternionf();
    private static final Vector3f V = new Vector3f();
    private static final Vector3f N = new Vector3f();
    private static final Figure[] FIGURES = new Figure[MAX_FIGURES];

    static {
        for (int i = 0; i < MAX_IMAGES; i++) {
            IMAGES[i] = new Level228Plan.Image();
            FOUND[i] = new Level228Plan.Image();
        }
        for (int i = 0; i < MAX_MIRRORS; i++) {
            MIRRORS[i] = new Level228Plan.Mirror();
        }
        for (int i = 0; i < MAX_FIGURES; i++) {
            FIGURES[i] = new Figure();
        }
    }

    private static int stage = STAGE_ENTITIES;
    private static boolean localHidden;
    private static boolean wasInLevel;
    private static int mirrorCount;
    private static final Level228Plan.Mirror SCRATCH_MIRROR = new Level228Plan.Mirror();
    private static long frameSeed;
    private static Level228Plan.ShiftState frameShift = Level228Plan.ShiftState.INITIAL;
    @Nullable
    private static FigureSource figureSource;

    // Statistics for /backrooms mirror info.
    private static int drawnLastFrame;
    private static int drawnSelfLastFrame;
    private static int hiddenCount;
    private static int hiddenLastFrame;

    // The stand-in being drawn right now (read by the render hooks below and the mixins).
    private static boolean drawing;
    private static int crouchOverride = -1;
    private static boolean hideHat;
    private static float drawRoll;
    private static float drawGlint;
    private static float rollApplied;

    private Level228Reflections() {}

    // ==========================================================================================
    // API: the imitator (client/level228 imitator code)
    // ==========================================================================================

    /**
     * A figure that exists only in mirrors: drawn at every visible image of (x, y, z), never at
     * (x, y, z) itself. Skin, layers and equipment are {@link #source}'s; the pose is its own.
     * Angles are in the REAL frame (the yaw the unreflected body would have); the mirror map is
     * applied on top, like for any stand-in.
     */
    public static final class Figure {
        @Nullable
        public AbstractClientPlayer source;
        public double x;
        public double y;
        public double z;
        public float bodyYaw;
        public float headYaw;
        public float headPitch;
        /** Sideways head tilt (degrees). */
        public float headRoll;
        /** WalkAnimationState position and speed at the partial tick. */
        public float limbSwing;
        public float limbSwingAmount;
        public float attackAnim;
        public boolean crouching;
        /** Drawn as a rotated copy, not a reflection: text and asymmetric skin read the wrong way. */
        public boolean unmirroredSkin;
        /** 0..1: its eyes catch the light. */
        public float glint;
        public float alpha = 1.0f;
    }

    /** Supplies the figures of a frame. */
    public interface FigureSource {
        /** Fill {@code out[0..n)} and return n (render thread, every frame: allocation-free). */
        int collect(Figure[] out, float partialTick);
    }

    public static void setFigureSource(@Nullable FigureSource source) {
        figureSource = source;
    }

    /** Hides (or shows again) every reflection of the LOCAL player. */
    public static void setLocalHidden(boolean hidden) {
        localHidden = hidden;
    }

    /** How many stand-ins of the local player were drawn in the last frame. */
    public static int localStandIns() {
        return drawnSelfLastFrame;
    }

    // ==========================================================================================
    // API: testing
    // ==========================================================================================

    /** Testing: which render stage the stand-ins are drawn in (one of the STAGE_ constants). */
    public static void setStage(int which) {
        stage = Mth.clamp(which, STAGE_ENTITIES, STAGE_SOLID_BLOCKS);
    }

    /** Client thread: {@code /backrooms mirror ...} (network/Level228MirrorPayload). */
    public static void receive(Level228MirrorPayload payload) {
        switch (payload.kind()) {
            case Level228MirrorPayload.LIE_LOOK -> Level228Lies.force(Level228Lies.LOOK);
            case Level228MirrorPayload.LIE_LAG -> Level228Lies.force(Level228Lies.LAG);
            case Level228MirrorPayload.LIE_WALK -> Level228Lies.force(Level228Lies.WALK_ON);
            case Level228MirrorPayload.LIE_STOP -> Level228Lies.force(Level228Lies.STOP);
            case Level228MirrorPayload.LIE_VANISH -> Level228Lies.force(Level228Lies.VANISH);
            case Level228MirrorPayload.LIE_OFF -> Level228Lies.force(Level228Lies.NONE);
            case Level228MirrorPayload.STAGE_ENTITIES -> setStage(STAGE_ENTITIES);
            case Level228MirrorPayload.STAGE_BLOCK_ENTITIES -> setStage(STAGE_BLOCK_ENTITIES);
            case Level228MirrorPayload.STAGE_SOLID_BLOCKS -> setStage(STAGE_SOLID_BLOCKS);
            case Level228MirrorPayload.INFO -> info();
            default -> {
                // An unknown kind from a newer server: ignore it.
            }
        }
    }

    private static void info() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !Level228ClientState.hasSeed()) {
            return;
        }
        long seed = Level228ClientState.seed();
        StringBuilder exits = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            if (i > 0) {
                exits.append(", ");
            }
            exits.append((char) ('A' + i)).append(": ").append(flawName(Level228Plan.exitFlaw(seed, i)));
        }
        mc.player.displayClientMessage(Component.literal("Stand-ins last frame: " + drawnLastFrame + " (yours " + drawnSelfLastFrame
                + "), real bodies hidden behind mirrors: " + hiddenLastFrame + ", " + Level228Lies.describe()), false);
        mc.player.displayClientMessage(Component.literal("Exit mirrors: " + exits), false);
    }

    private static String flawName(int flaw) {
        return switch (flaw) {
            case -1 -> "accurate";
            case Level228Plan.FLAW_DELAY -> "late " + delayTicks(Level228ClientState.seed()) * 50 + " ms";
            case Level228Plan.FLAW_WRONG_HAND -> "item in the wrong hand";
            case Level228Plan.FLAW_NO_ARMOUR -> "a worn piece missing";
            case Level228Plan.FLAW_NOD -> "head nods out of time";
            case Level228Plan.FLAW_UNMIRRORED -> "not mirrored";
            case Level228Plan.FLAW_CANDLE_UNLIT -> "candle unlit (geometry)";
            case Level228Plan.FLAW_MISSING_DETAIL -> "room detail missing (geometry)";
            default -> "flaw " + flaw;
        };
    }

    // ==========================================================================================
    // Hiding real bodies behind mirrors (mixin/client/EntityRenderDispatcherMixin)
    // ==========================================================================================

    /** True if {@code entity} stands beyond a mirror pane as seen from the camera: draw only its reflections. */
    public static boolean hiddenBehindMirror(Entity entity) {
        if (!wasInLevel || !Level228ClientState.hasSeed()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        if (entity == camera.getEntity() && !camera.isDetached()) {
            return false; // you, in first person: not drawn anyway
        }
        Vec3 cam = camera.getPosition();
        double x = entity.getX();
        double y = entity.getY() + entity.getBbHeight() * 0.5;
        double z = entity.getZ();
        double dx = x - cam.x;
        double dy = y - cam.y;
        double dz = z - cam.z;
        if (dx * dx + dy * dy + dz * dz > HIDE_RANGE * HIDE_RANGE) {
            return false;
        }
        // Tested a little beyond the centre (away from the camera): a body whose centre is on or just
        // short of a pane's plane (only possible when placed there, e.g. teleported into the glass) is
        // half behind the mirror, and is drawn as its reflections rather than poking through the
        // glass. A body that really stands in front keeps at least half its width plus half the
        // pane (0.36 blocks) from the plane, more than the push.
        double h = Math.sqrt(dx * dx + dz * dz);
        if (h > 1e-6) {
            x += dx / h * STRADDLE;
            z += dz / h * STRADDLE;
        }
        if (Level228Plan.behindMirror(Level228ClientState.seed(), Level228ClientState.shift(), cam.x, cam.y, cam.z, x, y, z)) {
            hiddenCount++;
            return true;
        }
        return false;
    }

    // ==========================================================================================
    // Ticks
    // ==========================================================================================

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        boolean in = level != null && mc.player != null && Level228ClientState.inLevel228() && Level228ClientState.hasSeed();
        if (!in) {
            if (wasInLevel) {
                Level228Lies.reset();
                PoseTracks.clear();
                drawnLastFrame = 0;
                drawnSelfLastFrame = 0;
                wasInLevel = false;
            }
            return;
        }
        wasInLevel = true;
        if (mc.isPaused()) {
            return;
        }
        PoseTracks.tick(level.players());
        Level228Plan.Zone zone = Level228Plan.zoneAt(Level228ClientState.seed(), mc.player.getBlockX(), mc.player.getBlockZ());
        // The final chamber and the exit hallway are honest: the puzzle and the safe room.
        boolean allowed = zone != Level228Plan.Zone.CHAMBER && zone != Level228Plan.Zone.EXIT_HALL;
        Level228Lies.tick(mc.player, level, allowed);
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
        // Hidden counts accumulate between our frames (the dispatcher asks during entity culling).
        hiddenLastFrame = hiddenCount;
        hiddenCount = 0;
        if (level == null || mc.player == null || !wasInLevel || !Level228ClientState.hasSeed()) {
            drawnLastFrame = 0;
            drawnSelfLastFrame = 0;
            return;
        }
        long seed = Level228ClientState.seed();
        Level228Plan.ShiftState shift = Level228ClientState.shift();
        frameSeed = seed;
        frameShift = shift;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        Vector3f look = event.getCamera().getLookVector();
        FrustumInvoker frustum = (FrustumInvoker) event.getFrustum();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
        mirrorCount = Math.min(Level228Plan.mirrorsNear(seed, shift, cam.x, cam.z, RADIUS + 4.0, MIRRORS), MAX_MIRRORS);
        Level228Lies.beginFrame();
        int budget = MAX_STANDINS;
        int self = 0;

        FigureSource figures = figureSource;
        if (figures != null) {
            int n = Math.min(figures.collect(FIGURES, pt), MAX_FIGURES);
            for (int i = 0; i < n && budget > 0; i++) {
                budget = drawFigure(mc, level, FIGURES[i], seed, shift, pt, cam, frustum, poseStack, source, budget);
            }
        }
        if (!localHidden) {
            int before = budget;
            budget = drawPlayer(mc, level, mc.player, true, seed, shift, pt, cam, look, frustum, poseStack, source, budget);
            self = before - budget;
        }
        List<AbstractClientPlayer> players = level.players();
        for (int k = 0; k < players.size() && budget > 0; k++) {
            AbstractClientPlayer p = players.get(k);
            if (p != mc.player) {
                budget = drawPlayer(mc, level, p, false, seed, shift, pt, cam, look, frustum, poseStack, source, budget);
            }
        }
        BUFFERS.endBatches();
        Level228Lies.endFrame();
        drawnLastFrame = MAX_STANDINS - budget;
        drawnSelfLastFrame = self;
    }

    /**
     * The distinct images of a body standing at (px, py, pz) into {@link #FOUND} (centre
     * positions); returns the count. The centre and four sideways probes are asked for: an image
     * of a probe point {@code p + d} through the same mirrors is the centre's image moved by
     * {@code F d}, so it is moved back, and images through the same mirror map (same flips and
     * translation) are kept once. That also folds the duplicates the plan can return for a point
     * lying exactly on a symmetry line.
     */
    private static int gather(long seed, Level228Plan.ShiftState shift, Vec3 cam, double px, double py, double pz) {
        int found = 0;
        for (int probe = 0; probe < 5; probe++) {
            double ox = probe == 1 ? PROBE : probe == 2 ? -PROBE : 0.0;
            double oz = probe == 3 ? PROBE : probe == 4 ? -PROBE : 0.0;
            int n = Math.min(Level228Plan.imagesOf(seed, shift, cam.x, cam.y, cam.z, px + ox, py, pz + oz, RADIUS, IMAGES), MAX_IMAGES);
            for (int i = 0; i < n && found < MAX_IMAGES; i++) {
                Level228Plan.Image img = IMAGES[i];
                if (img.depth < 1) {
                    continue;
                }
                double cx = img.x - img.flipX * ox;
                double cz = img.z - img.flipZ * oz;
                boolean seen = false;
                for (int k = 0; k < found && !seen; k++) {
                    Level228Plan.Image f = FOUND[k];
                    seen = f.flipX == img.flipX && f.flipZ == img.flipZ && Math.abs(f.x - cx) < 1e-3 && Math.abs(f.z - cz) < 1e-3;
                }
                if (seen) {
                    continue;
                }
                Level228Plan.Image f = FOUND[found++];
                f.x = cx;
                f.y = img.y;
                f.z = cz;
                f.flipX = img.flipX;
                f.flipZ = img.flipZ;
                f.depth = img.depth;
                f.lastMirror = img.lastMirror;
            }
        }
        return found;
    }

    /** Every image of one player; returns the budget left. */
    private static int drawPlayer(Minecraft mc, ClientLevel level, AbstractClientPlayer p, boolean you, long seed,
            Level228Plan.ShiftState shift, float pt, Vec3 cam, Vector3f look, FrustumInvoker frustum, PoseStack poseStack,
            MultiBufferSource.BufferSource source, int budget) {
        if (p.isSpectator() || p.isInvisible() || !p.isAlive() || p.isRemoved()) {
            return budget; // spectators and the invisible have no reflection
        }
        double rx = Mth.lerp(pt, p.xo, p.getX());
        double ry = Mth.lerp(pt, p.yo, p.getY());
        double rz = Mth.lerp(pt, p.zo, p.getZ());
        double cdx = rx - cam.x;
        double cdz = rz - cam.z;
        if (cdx * cdx + cdz * cdz > 4.0 * RADIUS * RADIUS) {
            return budget; // too far for any of its images to be near you
        }
        int n = gather(seed, shift, cam, rx, ry, rz);
        for (int i = 0; i < n && budget > 0; i++) {
            Level228Plan.Image img = FOUND[i];
            if (img.depth < 1) {
                continue;
            }
            Level228Plan.Mirror mirror = mirror(img.lastMirror);
            double tx = img.x - img.flipX * rx;
            double tz = img.z - img.flipZ * rz;
            POSE.reset(img.x, img.y, img.z, img.flipX, img.flipZ);
            boolean liveOnScreen = onScreen(frustum, POSE);
            int exit = mirror == null ? -1 : exitIndex(mirror);
            if (exit >= 0) {
                applyFlaw(level, p, Level228Plan.exitFlaw(seed, exit), seed, tx, tz, pt);
            } else if (you) {
                boolean special = mirror != null && isSpecial(mirror);
                Level228Lies.consider(p, pt, img.flipX, img.flipZ, tx, tz, POSE, cam, look, liveOnScreen, special);
                if (Level228Lies.isLieImage(img.flipX, img.flipZ, tx, tz)) {
                    boolean draws = Level228Lies.apply(p, pt, cam, POSE);
                    if (draws ? onScreen(frustum, POSE) : liveOnScreen) {
                        Level228Lies.noteSeen();
                    }
                    if (!draws) {
                        continue;
                    }
                }
            }
            if (mirror != null) {
                distortion(mirror, rx, rz, level.getGameTime() + pt, POSE);
            }
            if (!onScreen(frustum, POSE)) {
                continue;
            }
            draw(mc, level, p, pt, cam, poseStack, source);
            budget--;
        }
        return budget;
    }

    /** Every image of one imitator figure; returns the budget left. */
    private static int drawFigure(Minecraft mc, ClientLevel level, Figure f, long seed, Level228Plan.ShiftState shift, float pt,
            Vec3 cam, FrustumInvoker frustum, PoseStack poseStack, MultiBufferSource.BufferSource source, int budget) {
        AbstractClientPlayer skin = f.source;
        if (skin == null || skin.isRemoved() || f.alpha <= 0.01f) {
            return budget;
        }
        int n = gather(seed, shift, cam, f.x, f.y, f.z);
        for (int i = 0; i < n && budget > 0; i++) {
            Level228Plan.Image img = FOUND[i];
            if (img.depth < 1) {
                continue; // only ever in a mirror
            }
            POSE.reset(img.x, img.y, img.z, img.flipX, img.flipZ);
            POSE.override = true;
            POSE.body = f.bodyYaw;
            POSE.head = f.headYaw;
            POSE.pitch = f.headPitch;
            POSE.walkPos = f.limbSwing;
            POSE.walkSpeed = f.limbSwingAmount;
            POSE.attack = f.attackAnim;
            POSE.crouch = f.crouching;
            POSE.headRoll = f.headRoll;
            POSE.glint = f.glint;
            POSE.alpha = Math.min(1.0f, f.alpha);
            POSE.unmirrored = f.unmirroredSkin;
            Level228Plan.Mirror mirror = mirror(img.lastMirror);
            if (mirror != null) {
                distortion(mirror, f.x, f.z, level.getGameTime() + pt, POSE);
            }
            if (!onScreen(frustum, POSE)) {
                continue;
            }
            draw(mc, level, skin, pt, cam, poseStack, source);
            budget--;
        }
        return budget;
    }

    private static boolean onScreen(FrustumInvoker frustum, PuppetPose pose) {
        double hw = 0.6 * pose.scaleXZ;
        double h = 2.0 * pose.scaleY;
        return frustum.backrooms$cubeInFrustum(pose.x - hw, pose.y, pose.z - hw, pose.x + hw, pose.y + h, pose.z + hw);
    }

    /** The mirror with this id: from the frame's table of mirrors near the camera, else asked of the plan. */
    @Nullable
    private static Level228Plan.Mirror mirror(int id) {
        for (int i = 0; i < mirrorCount; i++) {
            if (MIRRORS[i].id == id) {
                return MIRRORS[i];
            }
        }
        return Level228Plan.mirror(frameSeed, frameShift, id, SCRATCH_MIRROR) ? SCRATCH_MIRROR : null;
    }

    // ==========================================================================================
    // The exit puzzle's flaws
    // ==========================================================================================

    /** 0, 1, 2 for the final chamber's mirrors A, B, C; -1 for any other mirror. */
    private static int exitIndex(Level228Plan.Mirror m) {
        return switch (m.kind) {
            case Level228Plan.K_EXIT_A -> 0;
            case Level228Plan.K_EXIT_B -> 1;
            case Level228Plan.K_EXIT_C -> 2;
            default -> -1;
        };
    }

    /** A "nonexistent room" or "hidden passage" mirror: the lies like these. */
    private static boolean isSpecial(Level228Plan.Mirror m) {
        return (m.flags & (Level228Plan.MF_PHANTOM | Level228Plan.MF_HIDDEN)) != 0;
    }

    /** How late the late puzzle mirror is: 8..14 ticks (0.4..0.7 s), fixed by the world. */
    static int delayTicks(long seed) {
        return 8 + Hash.range(Hash.of(seed, 228, 5, 0x1A7E), 7);
    }

    /**
     * A wrong exit mirror's flaw on {@code POSE} (the plain image of {@code p}). Every input is the
     * same on every client (the seed, the player's pose history, their equipment, the game time),
     * so everyone sees the same flaw on everyone.
     */
    private static void applyFlaw(ClientLevel level, AbstractClientPlayer p, int flaw, long seed, double tx, double tz, float pt) {
        switch (flaw) {
            case Level228Plan.FLAW_DELAY -> {
                if (PoseTracks.sample(p.getId(), delayTicks(seed), pt)) {
                    POSE.x = tx + POSE.flipX * PoseTracks.sx;
                    POSE.y = PoseTracks.sy;
                    POSE.z = tz + POSE.flipZ * PoseTracks.sz;
                    POSE.override = true;
                    POSE.body = PoseTracks.sBody;
                    POSE.head = PoseTracks.sHead;
                    POSE.pitch = PoseTracks.sPitch;
                    POSE.walkPos = PoseTracks.sWalkPos;
                    POSE.walkSpeed = PoseTracks.sWalkSpeed;
                    POSE.attack = PoseTracks.sAttack;
                    POSE.crouch = PoseTracks.sCrouch;
                }
            }
            case Level228Plan.FLAW_WRONG_HAND -> {
                // With nothing in either hand there is nothing to swap: the empty main arm swings instead.
                POSE.wrongHand = true;
            }
            case Level228Plan.FLAW_NO_ARMOUR -> missingPiece(p, seed);
            case Level228Plan.FLAW_NOD -> {
                // A slow, small nod every few seconds that the real head never makes (game time: the
                // same moment for every viewer).
                int period = 70 + Hash.range(Hash.of(seed, 228, 6, 0x40D), 50);
                int phase = Hash.range(Hash.of(seed, 228, 7, 0x40D), period);
                double t = (level.getGameTime() + phase + pt) % period;
                if (t < 14.0) {
                    POSE.nod = 14.0f * Mth.sin((float) (Math.PI * t / 14.0));
                }
            }
            case Level228Plan.FLAW_UNMIRRORED -> POSE.unmirrored = true;
            default -> {
                // Accurate, or a flaw of the image's geometry (unlit candle, missing detail).
            }
        }
    }

    /**
     * "Something you wear is missing": the first piece worn in a fixed order (helmet, chestplate,
     * leggings, boots rotated by the seed), else the main-hand item, else the hat skin layer. Fixed
     * by the seed and by what the player wears, so it is the same for everyone.
     */
    private static void missingPiece(AbstractClientPlayer p, long seed) {
        Inventory inv = p.getInventory();
        int start = Hash.range(Hash.of(seed, 228, 8, 0xA4), 4);
        for (int k = 0; k < 4; k++) {
            int slot = 3 - (start + k) % 4;
            if (!inv.armor.get(slot).isEmpty()) {
                POSE.dropArmour = slot;
                return;
            }
        }
        if (!p.getMainHandItem().isEmpty()) {
            POSE.dropHeld = true;
            return;
        }
        POSE.dropHat = true;
    }

    // ==========================================================================================
    // Funhouse mirrors
    // ==========================================================================================

    /**
     * Stretches or squashes {@code pose} about its feet if {@code m} is a funhouse mirror (its
     * surface says how: concave tall and thin, convex short and wide, wave slowly wavering; the
     * pane art, client/level228 props, matches). Stronger the closer the real body (rx, rz) is to
     * the glass, like a curved mirror's pull.
     */
    private static void distortion(Level228Plan.Mirror m, double rx, double rz, double time, PuppetPose pose) {
        int surface = m.surface;
        if (surface != Level228Plan.S_DISTORT_CONCAVE && surface != Level228Plan.S_DISTORT_CONVEX
                && surface != Level228Plan.S_DISTORT_WAVE) {
            return;
        }
        double d = Math.abs((m.axis == 0 ? rx : rz) - m.plane);
        float near = (float) Mth.clamp(1.0 - (d - 0.5) / 5.0, 0.35, 1.0);
        switch (surface) {
            case Level228Plan.S_DISTORT_CONCAVE -> {
                pose.scaleY = 1.0f + 0.38f * near;
                pose.scaleXZ = 1.0f - 0.30f * near;
            }
            case Level228Plan.S_DISTORT_CONVEX -> {
                pose.scaleY = 1.0f - 0.34f * near;
                pose.scaleXZ = 1.0f + 0.42f * near;
            }
            default -> {
                float w = Mth.sin((float) (time * 0.07 + m.id * 1.7));
                pose.scaleY = 1.0f + 0.22f * near * w;
                pose.scaleXZ = 1.0f - 0.16f * near * w;
            }
        }
    }

    // ==========================================================================================
    // One stand-in
    // ==========================================================================================

    private static void draw(Minecraft mc, ClientLevel level, AbstractClientPlayer p, float pt, Vec3 cam, PoseStack poseStack,
            MultiBufferSource.BufferSource source) {
        PuppetPose pose = POSE;
        EntityRenderer<? super AbstractClientPlayer> renderer = mc.getEntityRenderDispatcher().getRenderer(p);
        boolean odd = (pose.flipX * pose.flipZ < 0) != pose.unmirrored;
        BUFFERS.begin(source, odd, pose.alpha);
        LIGHT_POS.set(Mth.floor(pose.x), Mth.floor(pose.y + 1.0), Mth.floor(pose.z));
        int light = LevelRenderer.getLightColor(level, LIGHT_POS);

        // What the stand-in changes on the entity, put back right after the draw.
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
        HumanoidArm mainArm = p.getMainArm();
        Inventory inv = p.getInventory();
        ItemStack armour = pose.dropArmour >= 0 ? inv.armor.get(pose.dropArmour) : ItemStack.EMPTY;
        int heldSlot = inv.selected;
        ItemStack held = pose.dropHeld && Inventory.isHotbarSlot(heldSlot) ? inv.items.get(heldSlot) : ItemStack.EMPTY;
        boolean crouch = pose.override ? pose.crouch : p.isCrouching();
        float drawnBody = pose.override ? pose.body : Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        poseStack.pushPose();
        try {
            if (pose.override) {
                p.yBodyRot = pose.body;
                p.yBodyRotO = pose.body;
                p.yHeadRot = pose.head;
                p.yHeadRotO = pose.head;
                p.setXRot(pose.pitch + pose.nod);
                p.xRotO = pose.pitch + pose.nod;
                p.attackAnim = pose.attack;
                p.oAttackAnim = pose.attack;
                walk.backrooms$setSpeedOld(pose.walkSpeed);
                walk.backrooms$setSpeed(pose.walkSpeed);
                // position(pt) = position - speed * (1 - pt): stored so that it reads walkPos at this pt.
                walk.backrooms$setPosition(pose.walkPos + pose.walkSpeed * (1.0f - pt));
                crouchOverride = pose.crouch ? 1 : 0;
            } else if (pose.nod != 0.0f) {
                p.setXRot(pitch + pose.nod);
                p.xRotO = pitchO + pose.nod;
            }
            if (pose.wrongHand) {
                p.setMainArm(mainArm.getOpposite());
            }
            if (!armour.isEmpty()) {
                inv.armor.set(pose.dropArmour, ItemStack.EMPTY);
            }
            if (!held.isEmpty()) {
                inv.items.set(heldSlot, ItemStack.EMPTY);
            }
            hideHat = pose.dropHat;
            drawRoll = pose.headRoll;
            drawGlint = pose.glint * pose.alpha;
            drawing = true;

            poseStack.translate(pose.x - cam.x, pose.y - cam.y, pose.z - cam.z);
            // The mirror map's linear part about the feet: mirrors yaw, pitch, limbs and handedness at once.
            poseStack.scale(pose.flipX, 1.0f, pose.flipZ);
            if (pose.unmirrored) {
                // Reflect the body across its own front-back plane first: the same figure in the same
                // place, but rotated rather than mirrored, so its skin and hands read the wrong way.
                float yaw = (180.0f - drawnBody) * Mth.DEG_TO_RAD;
                poseStack.mulPose(ROT.rotationY(yaw));
                poseStack.scale(-1.0f, 1.0f, 1.0f);
                poseStack.mulPose(ROT.rotationY(-yaw));
            }
            if (pose.scaleY != 1.0f || pose.scaleXZ != 1.0f) {
                poseStack.scale(pose.scaleXZ, pose.scaleY, pose.scaleXZ);
            }
            if (crouch) {
                poseStack.translate(0.0, p.getScale() * -2.0 / 16.0, 0.0); // the crouch offset PlayerRenderer asks for
            }
            renderer.render(p, 0.0f, pt, poseStack, BUFFERS, light);
        } finally {
            poseStack.popPose();
            drawing = false;
            crouchOverride = -1;
            hideHat = false;
            drawRoll = 0.0f;
            drawGlint = 0.0f;
            if (!held.isEmpty()) {
                inv.items.set(heldSlot, held);
            }
            if (!armour.isEmpty()) {
                inv.armor.set(pose.dropArmour, armour);
            }
            if (pose.wrongHand) {
                p.setMainArm(mainArm);
            }
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

    // ==========================================================================================
    // Render hooks while a stand-in is drawn
    // ==========================================================================================

    /**
     * The player renderer sets the model's crouch and layers from the entity just before this
     * event; a stand-in crouches (or doesn't) as its own pose says, and may lack its hat layer.
     */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (!drawing) {
            return;
        }
        if (crouchOverride >= 0) {
            event.getRenderer().getModel().crouching = crouchOverride == 1;
        }
        if (hideHat) {
            event.getRenderer().getModel().hat.visible = false;
        }
    }

    /** No stand-in carries a name tag: a name tag means a real body. */
    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (drawing) {
            event.setCanRender(TriState.FALSE);
        }
    }

    /** Mixin hook (LivingEntityRenderer.render, right after the model is posed): the head's tilt. */
    public static void afterSetupAnim(EntityModel<?> model) {
        if (!drawing || drawRoll == 0.0f || !(model instanceof HumanoidModel<?> humanoid)) {
            return;
        }
        rollApplied = drawRoll * Mth.DEG_TO_RAD;
        humanoid.head.zRot += rollApplied;
        humanoid.hat.zRot += rollApplied;
    }

    /**
     * Mixin hook (LivingEntityRenderer.render, before it leaves model space): the eye glint, and the
     * head's tilt taken off again (vanilla's setupAnim never resets zRot of the head; the model is
     * shared by every player drawn after this one).
     */
    public static void beforeModelPop(EntityModel<?> model, PoseStack poseStack, MultiBufferSource buffer) {
        if (!drawing || !(model instanceof HumanoidModel<?> humanoid)) {
            return;
        }
        if (drawGlint > 0.004f) {
            poseStack.pushPose();
            humanoid.head.translateAndRotate(poseStack);
            VertexConsumer vc = buffer.getBuffer(RenderType.eyes(WHITE));
            PoseStack.Pose last = poseStack.last();
            int r = (int) (255.0f * drawGlint);
            int g = (int) (240.0f * drawGlint);
            int b = (int) (212.0f * drawGlint);
            eye(vc, last, -2.0f, -1.0f, r, g, b);
            eye(vc, last, 1.0f, 2.0f, r, g, b);
            poseStack.popPose();
        }
        if (rollApplied != 0.0f) {
            humanoid.head.zRot -= rollApplied;
            humanoid.hat.zRot -= rollApplied;
            rollApplied = 0.0f;
        }
    }

    /** One eye: a 1-pixel quad just in front of the face, both windings (whichever survives culling). */
    private static void eye(VertexConsumer vc, PoseStack.Pose pose, float x0, float x1, int r, int g, int b) {
        float y0 = -4.0f / 16.0f;
        float y1 = -3.0f / 16.0f;
        float z = -4.02f / 16.0f;
        float a = x0 / 16.0f;
        float c = x1 / 16.0f;
        Matrix4f m = pose.pose();
        Matrix3f nm = pose.normal();
        N.set(0.0f, 0.0f, -1.0f).mul(nm);
        vertex(vc, m, a, y0, z, r, g, b, 0.0f, 0.0f);
        vertex(vc, m, a, y1, z, r, g, b, 0.0f, 1.0f);
        vertex(vc, m, c, y1, z, r, g, b, 1.0f, 1.0f);
        vertex(vc, m, c, y0, z, r, g, b, 1.0f, 0.0f);
        N.negate();
        vertex(vc, m, c, y0, z, r, g, b, 1.0f, 0.0f);
        vertex(vc, m, c, y1, z, r, g, b, 1.0f, 1.0f);
        vertex(vc, m, a, y1, z, r, g, b, 0.0f, 1.0f);
        vertex(vc, m, a, y0, z, r, g, b, 0.0f, 0.0f);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, int r, int g, int b, float u, float v) {
        m.transformPosition(x, y, z, V);
        vc.addVertex(V.x, V.y, V.z)
                .setColor(r, g, b, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(N.x, N.y, N.z);
    }
}
