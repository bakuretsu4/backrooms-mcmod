package com.rickr.backrooms.entity;

import com.rickr.backrooms.player.HidingService;
import com.rickr.backrooms.registry.Level227Predators;
import com.rickr.backrooms.world.layout.Level227Tree;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * A squirrel swarm: the plan that dozens of {@link L227Squirrel}s follow together. The wiki says
 * packs of up to a hundred circle a wanderer, then eat them alive, and fighting back is futile.
 *
 * <p>How it plays:
 * <ol>
 *   <li><b>Gather</b> (about 5 s): a rising chatter from every side, glowing shapes pouring out of
 *       the undergrowth 18-26 blocks away and running in.</li>
 *   <li><b>Circle</b> (about 11 s): they run round you in a ring that slowly tightens from 13
 *       blocks to 3.5, some stopping dead to sit up and stare. <b>This is your chance.</b> Break
 *       out through the ring and sprint: they give chase a little slower than a sprint, and they
 *       will not follow you more than {@value #LEASH} blocks from where the ring was.</li>
 *   <li><b>Swarm</b>: they leap at you and hang on, up to {@value #MAX_CLINGING} at a time,
 *       biting. Every squirrel on you does about half a heart a second and slows you down; hitting
 *       one knocks it off, three hits kill it, and another takes its place. A swarm is
 *       {@value #TOTAL} squirrels in all, but only {@value #MAX_LIVE} are ever there at once:
 *       kill one and another comes running out of the trees.</li>
 *   <li><b>Feed</b> / <b>Disperse</b>: once you are dead, or away past the leash, or gone, they
 *       scatter back into the forest and vanish.</li>
 * </ol>
 *
 * <p>Cost: the swarm does the thinking once per tick (one sight check on the target, the ring,
 * the damage) and each squirrel only steers at its spot, so a full swarm of {@value #MAX_LIVE} is
 * about the cost of a few ordinary mobs. Only one swarm may run per world at a time.
 *
 * <p>Server only. Swarms are not saved: squirrels aren't either, and a reload simply ends it.
 */
public final class SquirrelSwarm {
    public enum Phase { GATHER, CIRCLE, SWARM, FEED, DISPERSE }

    /** A swarm, all told (the wiki's "up to 100"). */
    public static final int TOTAL = 100;
    /** Real squirrels alive at once: the performance cap. */
    public static final int MAX_LIVE = 40;
    public static final int MAX_CLINGING = Math.min(10, L227Squirrel.CLING_SPOTS_COUNT);
    /** They never chase you further than this from the ring. */
    public static final int LEASH = 36;

    private static final int GATHER_MAX_TICKS = 110;
    /** Squirrels arrive in pairs this often while gathering, and one at a time as reinforcements. */
    private static final int GATHER_SPAWN_EVERY = 3;
    private static final int REINFORCE_EVERY = 6;
    private static final double SPAWN_MIN = 18.0;
    private static final double SPAWN_MAX = 26.0;
    private static final double RING_START = 13.0;
    private static final double RING_END = 3.5;
    private static final int CIRCLE_TICKS = 220;
    /** The ring turns at about this many blocks per tick along its edge (the runners' speed). */
    private static final float RING_EDGE_SPEED = 0.26f;
    /** Out through the ring by this much and they all come at once. */
    private static final double BREAKOUT_MARGIN = 2.0;
    /** Bites land every half second, this much per clinging squirrel: half a heart a second each. */
    private static final int BITE_EVERY = 10;
    private static final float BITE_DAMAGE = 0.5f;
    private static final int FEED_TICKS = 120;
    private static final int DISPERSE_TICKS = 260;
    /** Hidden in furniture this long and they give up. */
    private static final int HIDDEN_GIVE_UP_TICKS = 300;
    /** With no squirrel left alive for this long (none could be placed), the swarm is over. */
    private static final int EMPTY_GIVE_UP_TICKS = 100;
    /** Nobody can see a squirrel vanish this far from every player. */
    private static final double VANISH_RANGE = 20.0;

    // Speeds (multipliers of the squirrel's MOVEMENT_SPEED 0.3, so 1.0 is about 3.9 blocks/s:
    // a little slower than a sprinting wanderer, faster than a walking one).
    private static final double GATHER_SPEED = 1.35;
    private static final double RING_SPEED = 1.45;
    private static final double SWARM_SPEED = 1.0;
    private static final double DISPERSE_SPEED = 1.3;

    private static final List<SquirrelSwarm> ACTIVE = new ArrayList<>();
    private static int nextId = 1;

    private final int id;
    private final ServerLevel level;
    private UUID targetId;
    @Nullable
    private Player target;
    private Vec3 centre;
    private Vec3 lastKnown;
    private Phase phase = Phase.GATHER;
    private int phaseTicks;
    private float ringAngle;
    private double radius = RING_START;
    /** Squirrels still to come (not yet spawned). */
    private int reserve;
    private final List<L227Squirrel> members = new ArrayList<>();
    private final L227Squirrel[] clinging = new L227Squirrel[MAX_CLINGING];
    private int hiddenTicks;
    private int emptyTicks;
    private int soundIn = 20;
    private final RandomSource random;

    private SquirrelSwarm(ServerLevel level, Player target, int reserve) {
        this.id = nextId++;
        this.level = level;
        this.target = target;
        this.targetId = target.getUUID();
        this.centre = target.position();
        this.lastKnown = target.position();
        this.reserve = reserve;
        this.random = level.getRandom();
    }

    // ------------------------------------------------------------------ registry of live swarms

    /**
     * Starts a swarm on {@code target} with {@code reserve} squirrels to come. Null if this world
     * already has one (only one at a time, to keep the squirrel count bounded).
     *
     * @param announce play the opening chatter (a natural swarm; spawn eggs start silently)
     */
    @Nullable
    public static SquirrelSwarm start(ServerLevel level, Player target, int reserve, boolean announce) {
        if (activeIn(level)) {
            return null;
        }
        SquirrelSwarm swarm = new SquirrelSwarm(level, target, Math.min(reserve, TOTAL));
        ACTIVE.add(swarm);
        if (announce) {
            // The first warning: a wave of chattering from all around, before anything shows.
            Vec3 at = target.position();
            level.playSound(null, at.x, at.y, at.z, Level227Predators.SQUIRREL_SWARM.get(), SoundSource.HOSTILE, 1.6f, 1.0f);
        }
        return swarm;
    }

    @Nullable
    public static SquirrelSwarm get(int id) {
        if (id == 0) {
            return null;
        }
        for (SquirrelSwarm swarm : ACTIVE) {
            if (swarm.id == id) {
                return swarm;
            }
        }
        return null;
    }

    public static boolean activeIn(ServerLevel level) {
        for (SquirrelSwarm swarm : ACTIVE) {
            if (swarm.level == level) {
                return true;
            }
        }
        return false;
    }

    /** The swarm in {@code level} whose ring or target is within {@code range} of {@code at}, if any. */
    @Nullable
    public static SquirrelSwarm nearest(ServerLevel level, Vec3 at, double range) {
        for (SquirrelSwarm swarm : ACTIVE) {
            if (swarm.level == level && swarm.lastKnown.distanceToSqr(at) < range * range) {
                return swarm;
            }
        }
        return null;
    }

    /** True while a swarm hunts {@code player} (the spawner won't start another on them). */
    public static boolean isHunting(Player player) {
        for (SquirrelSwarm swarm : ACTIVE) {
            if (swarm.targetId.equals(player.getUUID())) {
                return true;
            }
        }
        return false;
    }

    /** The server stopped: forget every swarm (they hold on to their world otherwise). */
    public static void clearAll() {
        ACTIVE.clear();
    }

    /** Runs every swarm in {@code level}; called every tick by the Level 227 spawner. */
    public static void tickAll(ServerLevel level) {
        for (Iterator<SquirrelSwarm> it = ACTIVE.iterator(); it.hasNext(); ) {
            SquirrelSwarm swarm = it.next();
            if (swarm.level.getServer() != level.getServer()) {
                it.remove(); // a stale swarm from a world that was closed
                continue;
            }
            if (swarm.level != level) {
                continue;
            }
            if (!swarm.tick()) {
                swarm.dismiss();
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------ what squirrels ask

    public Phase phase() {
        return phase;
    }

    @Nullable
    public Player target() {
        return phase == Phase.DISPERSE || phase == Phase.FEED ? null : target;
    }

    public boolean isHunting() {
        return phase == Phase.CIRCLE || phase == Phase.SWARM || phase == Phase.FEED;
    }

    /** Only in the ring do they stop to sit up and stare. */
    public boolean mayPauseToStare() {
        return phase == Phase.CIRCLE;
    }

    /** Takes a squirrel into this swarm. False if the swarm is full or winding down. */
    public boolean adopt(L227Squirrel squirrel) {
        if (members.size() >= MAX_LIVE || phase == Phase.DISPERSE || phase == Phase.FEED) {
            return false;
        }
        squirrel.swarmId = id;
        members.add(squirrel);
        return true;
    }

    /** Where {@code squirrel} should be heading right now. */
    public Vec3 goalFor(L227Squirrel squirrel) {
        switch (phase) {
            case GATHER, CIRCLE -> {
                // Its own place on the ring. A few run the other way round, so it never looks
                // like a tidy merry-go-round.
                float dir = (squirrel.getId() % 5 == 0) ? -1.0f : 1.0f;
                float angle = ringAngle * dir + squirrel.ringOffset;
                double r = Math.max(1.5, radius + squirrel.ringJitter);
                return new Vec3(centre.x + Mth.cos(angle) * r, centre.y, centre.z + Mth.sin(angle) * r);
            }
            case SWARM -> {
                Player prey = target;
                if (prey != null) {
                    // Straight at you, spread a little so they come from all sides.
                    double r = 0.6;
                    return prey.position().add(Mth.cos(squirrel.ringOffset) * r, 0.0, Mth.sin(squirrel.ringOffset) * r);
                }
                return lastKnown;
            }
            case FEED -> {
                double r = 1.5 + Math.abs(squirrel.ringJitter);
                float angle = squirrel.ringOffset + phaseTicks * 0.02f;
                return lastKnown.add(Mth.cos(angle) * r, 0.0, Mth.sin(angle) * r);
            }
            default -> {
                // Away from where it all happened, into the forest.
                Vec3 away = squirrel.position().subtract(lastKnown).multiply(1.0, 0.0, 1.0);
                if (away.lengthSqr() < 1.0e-4) {
                    away = new Vec3(Mth.cos(squirrel.ringOffset), 0.0, Mth.sin(squirrel.ringOffset));
                }
                return squirrel.position().add(away.normalize().scale(8.0));
            }
        }
    }

    public double speedFor(L227Squirrel squirrel) {
        return switch (phase) {
            case GATHER -> GATHER_SPEED;
            case CIRCLE -> RING_SPEED;
            case SWARM -> SWARM_SPEED;
            case FEED -> 0.6;
            case DISPERSE -> DISPERSE_SPEED;
        };
    }

    /** A free spot on the target's body for {@code squirrel}, or -1 if every spot is taken. */
    public int claimClingSpot(L227Squirrel squirrel) {
        if (phase != Phase.SWARM) {
            return -1;
        }
        for (int i = 0; i < clinging.length; i++) {
            if (clinging[i] == null || !clinging[i].isAlive()) {
                clinging[i] = squirrel;
                return i;
            }
        }
        return -1;
    }

    public void releaseClingSpot(L227Squirrel squirrel) {
        for (int i = 0; i < clinging.length; i++) {
            if (clinging[i] == squirrel) {
                clinging[i] = null;
            }
        }
    }

    /** A hider was dragged out by one of us: back on them at once. */
    public void resume(Player player) {
        target = player;
        targetId = player.getUUID();
        lastKnown = player.position();
        hiddenTicks = 0;
        setPhase(Phase.SWARM);
    }

    // ------------------------------------------------------------------ the plan, once a tick

    /** False when the swarm is over. */
    private boolean tick() {
        phaseTicks++;
        members.removeIf(squirrel -> !squirrel.isAlive() || squirrel.isRemoved());
        refreshTarget();
        switch (phase) {
            case GATHER -> tickGather();
            case CIRCLE -> tickCircle();
            case SWARM -> tickSwarm();
            case FEED -> {
                if (phaseTicks >= FEED_TICKS) {
                    setPhase(Phase.DISPERSE);
                }
            }
            case DISPERSE -> {
                return tickDisperse();
            }
        }
        tickSounds();
        if (members.isEmpty() && reserve <= 0 && phase != Phase.GATHER) {
            return false; // every last one of them is dead
        }
        // None alive and none could be placed for a while (no room on this branch): it's over.
        emptyTicks = members.isEmpty() ? emptyTicks + 1 : 0;
        return emptyTicks < EMPTY_GIVE_UP_TICKS;
    }

    /** Keeps track of the target: dead means feeding, gone or hidden too long means giving up. */
    private void refreshTarget() {
        if (phase == Phase.FEED || phase == Phase.DISPERSE) {
            return;
        }
        Player player = level.getPlayerByUUID(targetId);
        if (player == null || player.level() != level || player.isSpectator() || player.isCreative()) {
            setPhase(Phase.DISPERSE);
            return;
        }
        target = player;
        if (player.isDeadOrDying()) {
            lastKnown = player.position();
            setPhase(Phase.FEED);
            return;
        }
        if (HidingService.isHidden(player)) {
            // They mill about where you vanished (close enough to hear you breathe), then give up.
            if (++hiddenTicks >= HIDDEN_GIVE_UP_TICKS) {
                setPhase(Phase.DISPERSE);
            }
            return;
        }
        hiddenTicks = 0;
        lastKnown = player.position();
    }

    private void tickGather() {
        if (phaseTicks % GATHER_SPAWN_EVERY == 0) {
            spawnSome(2);
        }
        int expected = Math.min(MAX_LIVE, members.size() + reserve);
        if (phaseTicks >= GATHER_MAX_TICKS || members.size() >= expected * 0.85) {
            // The ring closes on where you stand now.
            if (target != null) {
                centre = target.position();
            }
            radius = RING_START;
            setPhase(Phase.CIRCLE);
            screech(3);
        }
    }

    private void tickCircle() {
        double f = Math.min(1.0, phaseTicks / (double) CIRCLE_TICKS);
        radius = Mth.lerp(f * f * (3.0 - 2.0 * f), RING_START, RING_END);
        ringAngle += (float) (RING_EDGE_SPEED / Math.max(radius, 1.0));
        if (target == null || HidingService.isHidden(target)) {
            return;
        }
        double dx = target.getX() - centre.x;
        double dz = target.getZ() - centre.z;
        boolean brokeOut = dx * dx + dz * dz > (radius + BREAKOUT_MARGIN) * (radius + BREAKOUT_MARGIN);
        if (brokeOut || f >= 1.0) {
            setPhase(Phase.SWARM);
            screech(6);
        }
    }

    private void tickSwarm() {
        if (target == null) {
            return;
        }
        if (phaseTicks % REINFORCE_EVERY == 0) {
            spawnSome(1);
        }
        int on = clingingCount();
        double dx = target.getX() - centre.x;
        double dz = target.getZ() - centre.z;
        if (on == 0 && dx * dx + dz * dz > LEASH * LEASH) {
            // Out past their ground with none of them on you: they let you go.
            screech(2);
            setPhase(Phase.DISPERSE);
            return;
        }
        if (on > 0 && phaseTicks % BITE_EVERY == 0 && target.invulnerableTime <= 10) {
            bite(target, on);
        }
    }

    /**
     * Every clinging squirrel bites together: one damage sum, so vanilla's short damage cooldown
     * doesn't swallow most of the bites. No attacker, so you aren't knocked about by every nip.
     * Slowness grows with the weight hanging on you.
     */
    private void bite(Player player, int on) {
        DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(Level227Predators.SQUIRREL_DAMAGE));
        player.hurt(source, BITE_DAMAGE * on);
        if (on >= 3) {
            int amplifier = on >= 9 ? 2 : on >= 6 ? 1 : 0;
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, amplifier, false, false, false));
        }
    }

    /** Running back into the forest; each one vanishes once nobody is near enough to see it go. */
    private boolean tickDisperse() {
        for (L227Squirrel squirrel : members) {
            if (squirrel.state() == L227Squirrel.CLING) {
                squirrel.letGo(false);
            }
            if (phaseTicks > 40 && level.getNearestPlayer(squirrel, VANISH_RANGE) == null) {
                squirrel.discard();
            }
        }
        return phaseTicks < DISPERSE_TICKS && !members.isEmpty();
    }

    /** The swarm is over: anything left of it simply goes. */
    private void dismiss() {
        for (L227Squirrel squirrel : members) {
            if (squirrel.isAlive()) {
                squirrel.discard();
            }
        }
        members.clear();
    }

    private void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
    }

    private int clingingCount() {
        int on = 0;
        for (L227Squirrel squirrel : clinging) {
            if (squirrel != null && squirrel.isAlive() && squirrel.state() == L227Squirrel.CLING) {
                on++;
            }
        }
        return on;
    }

    // ------------------------------------------------------------------ bringing them in

    /** Up to {@code count} more squirrels from the reserve, out of sight among the trees. */
    private void spawnSome(int count) {
        if (target == null) {
            return;
        }
        for (int i = 0; i < count && reserve > 0 && members.size() < MAX_LIVE; i++) {
            BlockPos spot = findSpot(target);
            if (spot == null) {
                return;
            }
            L227Squirrel squirrel = Level227Predators.SQUIRREL.get().spawn(level, spot, MobSpawnType.EVENT);
            if (squirrel != null) {
                reserve--;
                adopt(squirrel);
            }
        }
    }

    /**
     * A spot on the same branch top 18-26 blocks from the target, out of everyone's sight. Squirrels
     * come from all directions, so the angle is random each time.
     */
    @Nullable
    private BlockPos findSpot(Player around) {
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = SPAWN_MIN + random.nextDouble() * (SPAWN_MAX - SPAWN_MIN);
            int x = Mth.floor(around.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(around.getZ() + Math.sin(angle) * distance);
            // On the tree's plan in Level 227; elsewhere (a test swarm) the ordinary ground.
            int feet = L227Senses.inLevel227(level) ? Level227Tree.topSurfaceY(x, z)
                    : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (feet == Integer.MIN_VALUE || Math.abs(feet - around.getY()) > 10.0) {
                continue;
            }
            BlockPos spot = Level39Spawner.standableNear(level, x, feet, z, 3);
            if (spot != null && !Level39Spawner.anyoneCanSee(level, spot)) {
                return spot;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ sound

    /** The swarm's own sound: a mass of chattering and claws, from somewhere in the ring. */
    private void tickSounds() {
        if (--soundIn > 0 || members.isEmpty()) {
            return;
        }
        soundIn = 34 + random.nextInt(20);
        L227Squirrel from = members.get(random.nextInt(members.size()));
        float volume = phase == Phase.SWARM ? 1.4f : 1.1f;
        level.playSound(null, from.getX(), from.getY(), from.getZ(), Level227Predators.SQUIRREL_SWARM.get(),
                SoundSource.HOSTILE, volume, 0.92f + random.nextFloat() * 0.16f);
    }

    /** A few of them shriek at once (the ring closing, the attack starting). */
    private void screech(int voices) {
        for (int i = 0; i < voices && !members.isEmpty(); i++) {
            L227Squirrel from = members.get(random.nextInt(members.size()));
            level.playSound(null, from.getX(), from.getY(), from.getZ(), Level227Predators.SQUIRREL_SCREECH.get(),
                    SoundSource.HOSTILE, 1.0f, 0.85f + random.nextFloat() * 0.4f);
        }
    }
}
