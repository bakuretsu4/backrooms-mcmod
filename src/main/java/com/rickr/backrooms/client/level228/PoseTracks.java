package com.rickr.backrooms.client.level228;

import java.util.List;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

/**
 * The last couple of seconds of every player's pose in Level 228, one sample per client tick:
 * what a late reflection replays. Used for the exit puzzle's delayed mirror (every client keeps
 * the same history of every player it sees, so all of them see the same delay) and for the "late"
 * lie on your own reflection.
 *
 * <p>Fixed-size arrays indexed by slot ({@value #MAX} players, {@value #RING} ticks each): nothing
 * is allocated per tick or frame.
 */
final class PoseTracks {
    /** Ticks remembered: the longest delay (20 ticks) plus easing, with room to spare. */
    static final int RING = 48;
    /** Players tracked at once; more than that in one maze are simply not delayed. */
    static final int MAX = 16;

    private static final int[] ID = new int[MAX];
    private static final int[] SAMPLES = new int[MAX];
    private static final boolean[] TOUCHED = new boolean[MAX];
    private static final double[] X = new double[MAX * RING];
    private static final double[] Y = new double[MAX * RING];
    private static final double[] Z = new double[MAX * RING];
    private static final float[] BODY = new float[MAX * RING];
    private static final float[] HEAD = new float[MAX * RING];
    private static final float[] PITCH = new float[MAX * RING];
    private static final float[] WALK_POS = new float[MAX * RING];
    private static final float[] WALK_SPEED = new float[MAX * RING];
    private static final float[] ATTACK = new float[MAX * RING];
    private static final boolean[] CROUCH = new boolean[MAX * RING];

    /** The result of {@link #sample}: a past pose in real space. */
    static double sx;
    static double sy;
    static double sz;
    static float sBody;
    static float sHead;
    static float sPitch;
    static float sWalkPos;
    static float sWalkSpeed;
    static float sAttack;
    static boolean sCrouch;

    static {
        clear();
    }

    private PoseTracks() {}

    static void clear() {
        for (int i = 0; i < MAX; i++) {
            ID[i] = Integer.MIN_VALUE;
            SAMPLES[i] = 0;
        }
    }

    /** Once a client tick (after the players moved): append everyone's pose, drop players gone. */
    static void tick(List<AbstractClientPlayer> players) {
        for (int i = 0; i < MAX; i++) {
            TOUCHED[i] = false;
        }
        for (int k = 0; k < players.size(); k++) {
            AbstractClientPlayer p = players.get(k);
            int slot = slotOf(p.getId());
            if (slot < 0) {
                slot = freeSlot();
                if (slot < 0) {
                    continue;
                }
                ID[slot] = p.getId();
                SAMPLES[slot] = 0;
            }
            TOUCHED[slot] = true;
            int i = slot * RING + SAMPLES[slot] % RING;
            X[i] = p.getX();
            Y[i] = p.getY();
            Z[i] = p.getZ();
            BODY[i] = p.yBodyRot;
            HEAD[i] = p.yHeadRot;
            PITCH[i] = p.getXRot();
            WALK_POS[i] = p.walkAnimation.position();
            WALK_SPEED[i] = p.walkAnimation.speed();
            ATTACK[i] = p.attackAnim;
            CROUCH[i] = p.isCrouching();
            SAMPLES[slot]++;
        }
        for (int i = 0; i < MAX; i++) {
            if (!TOUCHED[i]) {
                ID[i] = Integer.MIN_VALUE; // left the level (or out of sight of this client): forget them
                SAMPLES[i] = 0;
            }
        }
    }

    /** Ticks of history for this player (0 if untracked). */
    static int history(int entityId) {
        int slot = slotOf(entityId);
        return slot < 0 ? 0 : Math.min(SAMPLES[slot], RING);
    }

    /**
     * The pose {@code delay} ticks before the one drawn at {@code pt}, into the {@code s*} fields.
     * The drawn pose lies between the last two samples at {@code pt}; the oldest sample bounds it.
     * False if the player has no history yet.
     */
    static boolean sample(int entityId, float delay, float pt) {
        int slot = slotOf(entityId);
        if (slot < 0 || SAMPLES[slot] < 2) {
            return false;
        }
        int n = SAMPLES[slot];
        double when = n - 2 + pt - delay;
        double oldest = Math.max(0, n - RING);
        if (when < oldest) {
            when = oldest;
        }
        int k0 = Mth.floor(when);
        int k1 = Math.min(k0 + 1, n - 1);
        float f = (float) (when - k0);
        int i0 = slot * RING + k0 % RING;
        int i1 = slot * RING + k1 % RING;
        sx = Mth.lerp(f, X[i0], X[i1]);
        sy = Mth.lerp(f, Y[i0], Y[i1]);
        sz = Mth.lerp(f, Z[i0], Z[i1]);
        sBody = Mth.rotLerp(f, BODY[i0], BODY[i1]);
        sHead = Mth.rotLerp(f, HEAD[i0], HEAD[i1]);
        sPitch = Mth.lerp(f, PITCH[i0], PITCH[i1]);
        sWalkPos = Mth.lerp(f, WALK_POS[i0], WALK_POS[i1]);
        sWalkSpeed = Mth.lerp(f, WALK_SPEED[i0], WALK_SPEED[i1]);
        sAttack = Mth.lerp(f, ATTACK[i0], ATTACK[i1]);
        sCrouch = f < 0.5f ? CROUCH[i0] : CROUCH[i1];
        return true;
    }

    private static int slotOf(int id) {
        for (int i = 0; i < MAX; i++) {
            if (ID[i] == id) {
                return i;
            }
        }
        return -1;
    }

    private static int freeSlot() {
        for (int i = 0; i < MAX; i++) {
            if (ID[i] == Integer.MIN_VALUE) {
                return i;
            }
        }
        return -1;
    }
}
