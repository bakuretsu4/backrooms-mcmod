package com.rickr.backrooms.client.level228;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.Level228ClientState;
import com.rickr.backrooms.network.Level228ImitatorPayload;
import com.rickr.backrooms.world.Level228Imitator;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Level 228's imitator on the client: turns what the server says (world/Level228Imitator: phase,
 * whom it watches, whose face it wears, where it stands in real space) into a figure the
 * reflections draw at every image of that point, and only there ({@link Level228Reflections}'s
 * figure source; never at the point itself, so turning round shows an empty corridor).
 *
 * <p><b>Its body</b> is a player stand-in wearing the wearer's skin and equipment (the watcher's
 * own when the wearer is out of range), moved by the WATCHER's pose:
 * <ul>
 *   <li>copy: the watcher's live pose (body, head, limbs, swing, crouch) frame for frame;</li>
 *   <li>wrong, late: the same pose {@value #LATE_TICKS} ticks behind, from a ring of recorded poses;</li>
 *   <li>wrong, still: it freezes; its limbs settle and it stands while you walk on;</li>
 *   <li>wrong, tilt: the live copy, with the head slowly leaning over to {@value #TILT_ROLL} degrees;</li>
 *   <li>watch: it stops; the body turns (server) and the head follows the watcher's eyes, eased, the
 *       head a little tilted. From right behind you it looks down at the back of your head.</li>
 * </ul>
 * <b>Tells</b> (so a close look never mistakes it for a teammate): it is drawn rotated, not
 * mirrored ({@code unmirroredSkin}), so skin details, writing and the held item sit on the wrong
 * side for a reflection; its eyes always catch a little light and stare bright while it watches;
 * like every stand-in it has no name tag and no shadow. Everyone sees the same figure in the same
 * glass; only the head's easing is per client.
 *
 * <p>Nothing here allocates per frame or per tick: slots, pose rings and the output are fixed.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class Level228ImitatorClient {
    /** How far behind the watcher the late copy runs (ticks; 0.6 s). */
    private static final int LATE_TICKS = 12;
    private static final int RING = 32;
    private static final float TILT_ROLL = 24.0f;
    /** While watching, every kind of it holds its head a little off level. */
    private static final float WATCH_ROLL = 9.0f;
    /** Degrees per tick its head turns toward you while watching (slow: no snap, no jumpscare). */
    private static final float HEAD_TURN = 3.0f;
    /** The head never turns farther than this from the body. */
    private static final float HEAD_LIMIT = 75.0f;
    /** Eye glint: always a little (a close look catches it), full while it watches you. */
    private static final float GLINT_BASE = 0.35f;
    private static final float GLINT_WATCH = 1.0f;
    /** Its limbs settle this much per tick once it stops copying. */
    private static final float SETTLE = 0.8f;

    private static final class Slot {
        int phase = Level228Imitator.NONE;
        int kind;
        boolean behind;
        UUID watcher;
        UUID wearer;
        // Position: received target, and the two ticks rendered between.
        double tx;
        double ty;
        double tz;
        float tYaw;
        double x0;
        double y0;
        double z0;
        double x1;
        double y1;
        double z1;
        float yaw0;
        float yaw1;
        // Held pose (STILL, WATCH): previous and current tick, rendered between.
        float body0;
        float body1;
        float head0;
        float head1;
        float pitch0;
        float pitch1;
        float limbPos;
        float limbAmt0;
        float limbAmt1;
        float roll0;
        float roll1;
        float glint0;
        float glint1;
        // The watcher's recorded poses, one per client tick, for the late copy and the freeze.
        final float[] rBody = new float[RING];
        final float[] rHead = new float[RING];
        final float[] rPitch = new float[RING];
        final float[] rLimbPos = new float[RING];
        final float[] rLimbAmt = new float[RING];
        final float[] rAttack = new float[RING];
        final boolean[] rCrouch = new boolean[RING];
        int recorded;
    }

    private static final Slot[] SLOT = new Slot[Level228Imitator.SLOTS];
    /** The figure source is handed to the reflections on the first client tick, once the game is up. */
    private static boolean registered;

    static {
        for (int i = 0; i < SLOT.length; i++) {
            SLOT[i] = new Slot();
        }
    }

    private Level228ImitatorClient() {}

    public static void receive(Level228ImitatorPayload p) {
        if (p.slot() < 0 || p.slot() >= SLOT.length) {
            return;
        }
        Slot s = SLOT[p.slot()];
        if (p.phase() == Level228Imitator.NONE) {
            // Gone: no fade. It is simply not there the next time you look.
            s.phase = Level228Imitator.NONE;
            return;
        }
        boolean fresh = s.phase == Level228Imitator.NONE || !p.watcher().equals(s.watcher);
        boolean jumped = p.behind() && !s.behind;
        s.phase = p.phase();
        s.kind = p.kind();
        s.behind = p.behind();
        s.watcher = p.watcher();
        s.wearer = p.wearer();
        s.tx = p.x();
        s.ty = p.y();
        s.tz = p.z();
        s.tYaw = p.yaw();
        if (fresh) {
            // A late joiner can meet it mid-watch: the held pose starts level, facing the way it
            // stands (set below).
            s.recorded = 0;
            s.pitch0 = s.pitch1 = 0.0f;
            s.limbAmt0 = s.limbAmt1 = 0.0f;
            s.roll0 = s.roll1 = 0.0f;
            s.glint0 = s.glint1 = GLINT_BASE;
        }
        if (fresh || jumped) {
            // A new figure, or the move to right behind you (made while you saw none of it): placed
            // at once, already facing its way, never slid or turned there.
            s.x0 = s.x1 = s.tx;
            s.y0 = s.y1 = s.ty;
            s.z0 = s.z1 = s.tz;
            s.yaw0 = s.yaw1 = s.tYaw;
            s.body0 = s.body1 = s.head0 = s.head1 = s.tYaw;
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!registered) {
            registered = true;
            Level228Reflections.setFigureSource(Level228ImitatorClient::collect);
        }
        if (mc.level == null || !Level228ClientState.inLevel228()) {
            clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        for (int i = 0; i < SLOT.length; i++) {
            Slot s = SLOT[i];
            if (s.phase != Level228Imitator.NONE) {
                tick(mc.level, s);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void clear() {
        for (int i = 0; i < SLOT.length; i++) {
            SLOT[i].phase = Level228Imitator.NONE;
        }
    }

    private static void tick(ClientLevel level, Slot s) {
        s.x0 = s.x1;
        s.y0 = s.y1;
        s.z0 = s.z1;
        s.yaw0 = s.yaw1;
        s.x1 = s.tx;
        s.y1 = s.ty;
        s.z1 = s.tz;
        s.yaw1 = s.tYaw;
        Player w = level.getPlayerByUUID(s.watcher);
        if (w == null) {
            return;
        }
        record(s, w);
        s.body0 = s.body1;
        s.head0 = s.head1;
        s.pitch0 = s.pitch1;
        s.limbAmt0 = s.limbAmt1;
        s.roll0 = s.roll1;
        s.glint0 = s.glint1;
        boolean copying = s.phase == Level228Imitator.COPY
                || s.phase == Level228Imitator.WRONG && s.kind != Level228Imitator.STILL;
        if (copying) {
            // Keep the held pose on the copy, so a freeze or the watch starts from exactly where
            // the copy was.
            int n = s.phase == Level228Imitator.WRONG && s.kind == Level228Imitator.LATE ? LATE_TICKS : 0;
            int k = ringIndex(s, n);
            s.body1 = s.rBody[k];
            s.head1 = s.rHead[k];
            s.pitch1 = s.rPitch[k];
            s.limbPos = s.rLimbPos[k];
            s.limbAmt1 = s.rLimbAmt[k];
            float roll = s.phase == Level228Imitator.WRONG && s.kind == Level228Imitator.TILT ? TILT_ROLL : 0.0f;
            s.roll1 = approach(s.roll1, roll, 0.4f);
            s.glint1 = approach(s.glint1, GLINT_BASE, 0.05f);
            return;
        }
        // Frozen, or watching: the limbs settle, nothing else moves unless it is watching.
        s.limbAmt1 = Math.max(0.0f, s.limbAmt1 * SETTLE - 0.01f);
        if (s.phase != Level228Imitator.WATCH) {
            return;
        }
        // The body turns as the server turns it; the head finds your eyes, slowly.
        s.body1 = s.body1 + Mth.wrapDegrees(s.yaw1 - s.body1) * 0.35f;
        double fx = s.x1;
        double fz = s.z1;
        double eyeY = s.y1 + 1.62;
        double dx = w.getX() - fx;
        double dz = w.getZ() - fz;
        double dy = w.getEyeY() - eyeY;
        float wantYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        float rel = Mth.clamp(Mth.wrapDegrees(wantYaw - s.body1), -HEAD_LIMIT, HEAD_LIMIT);
        float wantHead = s.body1 + rel;
        float wantPitch = (float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        if (s.behind) {
            // Over your shoulder, at the back of your head.
            wantPitch = Math.max(wantPitch, 18.0f);
        }
        s.head1 = s.head1 + Mth.clamp(Mth.wrapDegrees(wantHead - s.head1), -HEAD_TURN, HEAD_TURN);
        s.pitch1 = s.pitch1 + Mth.clamp(wantPitch - s.pitch1, -HEAD_TURN, HEAD_TURN);
        float roll = s.kind == Level228Imitator.TILT ? TILT_ROLL : WATCH_ROLL;
        s.roll1 = approach(s.roll1, roll, 0.25f);
        s.glint1 = approach(s.glint1, GLINT_WATCH, 0.04f);
    }

    /** Records the watcher's pose this tick into the ring. */
    private static void record(Slot s, Player w) {
        int i = s.recorded % RING;
        s.rBody[i] = w.yBodyRot;
        s.rHead[i] = w.yHeadRot;
        s.rPitch[i] = w.getXRot();
        s.rLimbPos[i] = w.walkAnimation.position();
        s.rLimbAmt[i] = w.walkAnimation.speed();
        s.rAttack[i] = w.getAttackAnim(1.0f);
        s.rCrouch[i] = w.isCrouching();
        s.recorded++;
    }

    /** Ring slot of the pose {@code back} ticks before the newest (clamped to what is recorded). */
    private static int ringIndex(Slot s, int back) {
        int newest = s.recorded - 1;
        int n = Math.max(0, newest - Math.min(back, Math.min(RING - 2, newest)));
        return n % RING;
    }

    private static float approach(float v, float target, float step) {
        return v < target ? Math.min(target, v + step) : Math.max(target, v - step);
    }

    /**
     * The reflections' figure source: fills {@code out} with the figures to draw this frame (each at
     * its images only). Render thread; allocation-free.
     */
    public static int collect(Level228Reflections.Figure[] out, float pt) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < SLOT.length && n < out.length; i++) {
            Slot s = SLOT[i];
            if (s.phase == Level228Imitator.NONE || s.recorded < 2) {
                continue;
            }
            Player w = level.getPlayerByUUID(s.watcher);
            if (!(w instanceof AbstractClientPlayer watcher)) {
                continue; // too far to copy anyone: too far to see it, too
            }
            AbstractClientPlayer source = watcher;
            if (!s.wearer.equals(s.watcher) && level.getPlayerByUUID(s.wearer) instanceof AbstractClientPlayer face) {
                source = face;
            }
            Level228Reflections.Figure f = out[n++];
            f.source = source;
            f.x = Mth.lerp(pt, s.x0, s.x1);
            f.y = Mth.lerp(pt, s.y0, s.y1);
            f.z = Mth.lerp(pt, s.z0, s.z1);
            f.unmirroredSkin = true;
            f.alpha = 1.0f;
            f.headRoll = Mth.lerp(pt, s.roll0, s.roll1);
            f.glint = Mth.lerp(pt, s.glint0, s.glint1);
            boolean live = s.phase == Level228Imitator.COPY
                    || s.phase == Level228Imitator.WRONG && s.kind == Level228Imitator.TILT;
            if (live) {
                // The perfect copy: exactly what the watcher's own model shows this frame.
                f.bodyYaw = Mth.rotLerp(pt, watcher.yBodyRotO, watcher.yBodyRot);
                f.headYaw = Mth.rotLerp(pt, watcher.yHeadRotO, watcher.yHeadRot);
                f.headPitch = Mth.lerp(pt, watcher.xRotO, watcher.getXRot());
                f.limbSwing = watcher.walkAnimation.position(pt);
                f.limbSwingAmount = watcher.walkAnimation.speed(pt);
                f.attackAnim = watcher.getAttackAnim(pt);
                f.crouching = watcher.isCrouching();
            } else if (s.phase == Level228Imitator.WRONG && s.kind == Level228Imitator.LATE) {
                int a = ringIndex(s, LATE_TICKS + 1);
                int b = ringIndex(s, LATE_TICKS);
                f.bodyYaw = Mth.rotLerp(pt, s.rBody[a], s.rBody[b]);
                f.headYaw = Mth.rotLerp(pt, s.rHead[a], s.rHead[b]);
                f.headPitch = Mth.lerp(pt, s.rPitch[a], s.rPitch[b]);
                f.limbSwing = Mth.lerp(pt, s.rLimbPos[a], s.rLimbPos[b]);
                f.limbSwingAmount = Mth.lerp(pt, s.rLimbAmt[a], s.rLimbAmt[b]);
                f.attackAnim = Mth.lerp(pt, s.rAttack[a], s.rAttack[b]);
                f.crouching = s.rCrouch[b];
            } else {
                f.bodyYaw = Mth.rotLerp(pt, s.body0, s.body1);
                f.headYaw = Mth.rotLerp(pt, s.head0, s.head1);
                f.headPitch = Mth.lerp(pt, s.pitch0, s.pitch1);
                f.limbSwing = s.limbPos;
                f.limbSwingAmount = Mth.lerp(pt, s.limbAmt0, s.limbAmt1);
                f.attackAnim = 0.0f;
                f.crouching = false;
            }
        }
        return n;
    }
}
