package com.rickr.backrooms.entity;

import com.rickr.backrooms.registry.Level227Predators;
import com.rickr.backrooms.player.HidingService;
import com.rickr.backrooms.world.SafeRooms;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The wolves of Level 227: they live only in the higher sections of the tree, hunt in packs and
 * attack on sight. Their mouths open sideways: the long snout splits down the middle into two jaws
 * lined with teeth, like a pair of shears.
 *
 * <p>What a player can learn about them:
 * <ul>
 *   <li><b>The howl is the alarm.</b> The first wolf to see you stops and howls; every wolf of its
 *       pack within earshot comes running.</li>
 *   <li><b>They surround you.</b> The pack spreads out in a ring around you, then darts in one at a
 *       time to bite and falls back. Standing still in the middle is the worst place to be.</li>
 *   <li><b>They never go down.</b> Wolves keep to the high branches. Get below the high sections
 *       (walking outward along a branch goes downhill) and they stop at the line, pacing and
 *       howling, and finally give up.</li>
 *   <li><b>Hurting them works, a little.</b> A hurt wolf breaks off for a few seconds; a badly
 *       hurt one runs. Once half the pack is badly hurt, the rest lose their nerve.</li>
 * </ul>
 *
 * <p>The brain is a small state machine ({@link Mode}) run by the server in
 * {@link #customServerAiStep()}, like the Hound's:
 * <pre>
 * ROAM --sees you--> HOWL --1.5 s--> CIRCLE --its turn--> RUSH --bite / 3 s--> RETREAT --1.2 s--> CIRCLE
 * CIRCLE/RUSH --you run--> CHASE --close again--> CIRCLE        CHASE --out of sight 4 s--> SEARCH --> ROAM
 * any hunt --you go below the high sections--> PACE --5 s--> ROAM       badly hurt --> FLEE
 * </pre>
 * The mode is synced so the model can pose (howling with the jaws spread, slinking low around you,
 * the full-stretch rush, cringing away).
 *
 * <p>Packs are just a shared number ({@link #packId}) handed out by the spawner; a wolf finds its
 * packmates nearby every two seconds. Hiding in furniture works as for every other monster
 * ({@link HidingService}).
 */
public class L227Wolf extends Monster implements HidingService.Seeker {
    /** What the wolf is doing right now. The order matters: it is synced as a byte. */
    public enum Mode { ROAM, HOWL, CIRCLE, RUSH, RETREAT, CHASE, SEARCH, PACE, FLEE }

    private static final Mode[] MODES = Mode.values();
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(L227Wolf.class, EntityDataSerializers.BYTE);

    // --- Senses ------------------------------------------------------------------------------
    /** How far it spots you in the misty forest (crouching cuts this, see L227Senses). */
    private static final double SIGHT = 30.0;
    /** It hears someone sprinting through the undergrowth from this far. */
    private static final double HEAR_SPRINT = 18.0;
    private static final int SENSE_INTERVAL = 5;
    /** A howl reaches packmates this far away. */
    private static final double PACK_RANGE = 56.0;
    private static final int PACK_REFRESH_TICKS = 40;

    // --- The ring ----------------------------------------------------------------------------
    /** How far from you the pack stands while circling. */
    private static final double RING_RADIUS = 7.5;
    /** How fast the ring turns around you (radians per tick): a slow, steady prowl. */
    private static final float RING_SPIN = 0.018f;
    private static final int RING_REPATH_TICKS = 10;
    /** The first rush comes this long after the ring forms, then one wolf about every 1.5-2.5 s. */
    private static final int FIRST_RUSH_TICKS = 45;
    private static final int RUSH_GAP_TICKS = 30;
    private static final int RUSH_GAP_EXTRA = 20;
    private static final int RUSH_MAX_TICKS = 60;
    private static final int RETREAT_TICKS = 24;

    // --- Chasing -----------------------------------------------------------------------------
    /** You are "running" once you are this far from it while it circles. */
    private static final double BREAKOUT_RANGE = 13.0;
    /** A chasing wolf closes the ring again once it is this close and you have slowed down. */
    private static final double RECLOSE_RANGE = 6.0;
    private static final int LOST_SIGHT_TICKS = 80;
    private static final double CHASE_GIVE_UP = 44.0;
    private static final int SEARCH_TICKS = 160;
    private static final int PACE_TICKS = 100;
    private static final int BITE_COOLDOWN_TICKS = 20;
    /** Hurt wolves break off for this long before coming back. */
    private static final int FLINCH_TICKS = 50;
    /** Below this share of its health a wolf gives up and runs. */
    private static final float FLEE_HEALTH = 0.35f;
    private static final int FLEE_TICKS = 200;

    // --- Speeds (multipliers of MOVEMENT_SPEED 0.32: 1.0 is about 4.2 blocks/s) --------------
    // A sprinting wanderer (about 4.5 blocks/s in the Backrooms) slowly pulls away from a chase,
    // but sprint energy runs out long before a wolf tires: running only helps if it gets you
    // downhill out of the high sections, or out of sight.
    private static final double ROAM_SPEED = 0.55;
    private static final double FOLLOW_SPEED = 0.75;
    private static final double CIRCLE_SPEED = 0.9;
    private static final double RUSH_SPEED = 1.4;
    private static final double RETREAT_SPEED = 1.0;
    private static final double CHASE_SPEED = 1.02;
    private static final double SEARCH_SPEED = 0.8;
    private static final double FLEE_SPEED = 1.2;

    /** The pack this wolf belongs to (0 = not yet in one). Handed out by the spawner; not saved. */
    private int packId;
    private static int nextPack = 1;

    // Server-side brain state.
    private Mode mode = Mode.ROAM;
    private int modeTicks;
    @Nullable
    private Player prey;
    private Vec3 lastSeen = Vec3.ZERO;
    private int unseenTicks;
    private int repathIn;
    private int biteCooldown;
    private int rushCooldown;
    private int flinchTicks;
    private boolean bitThisRush;
    /** The ring's angle around the prey; shared through the pack leader so the ring stays spread. */
    private float ringAngle;
    private final List<L227Wolf> pack = new ArrayList<>();
    private int packRefreshIn;

    // Client-only visuals (0..1), eased every tick so the model never snaps.
    private float jaw;
    private float jawO;
    private float crouch;
    private float crouchO;
    private float howl;
    private float howlO;

    public L227Wolf(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 26.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.25)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODE, (byte) Mode.ROAM.ordinal());
    }

    /** Only the always-on basic is a goal; the hunt itself is the state machine below. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    /** Puts this wolf in pack {@code id} (the spawner gives every wolf of one pack the same id). */
    public void setPack(int id) {
        packId = id;
        packRefreshIn = 0;
    }

    /** Current mode; works on both sides (synced). */
    public Mode mode() {
        byte b = entityData.get(MODE);
        return b >= 0 && b < MODES.length ? MODES[b] : Mode.ROAM;
    }

    // ------------------------------------------------------------------ client visuals

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            Mode current = mode();
            float jawTarget = switch (current) {
                case HOWL -> 0.75f;
                case RUSH -> 1.0f;
                case CIRCLE -> 0.18f;
                case CHASE -> 0.35f;
                case PACE -> 0.25f;
                default -> 0.0f;
            };
            float crouchTarget = switch (current) {
                case CIRCLE -> 1.0f;
                case RETREAT, FLEE -> 0.7f;
                case SEARCH -> 0.4f;
                default -> 0.0f;
            };
            jawO = jaw;
            crouchO = crouch;
            howlO = howl;
            jaw += (jawTarget - jaw) * (jawTarget > jaw ? 0.35f : 0.2f);
            crouch += (crouchTarget - crouch) * 0.12f;
            howl += ((current == Mode.HOWL ? 1.0f : 0.0f) - howl) * 0.15f;
        }
    }

    /** How far the two jaws are spread (0..1) this frame. */
    public float jaw(float partialTick) {
        return Mth.lerp(partialTick, jawO, jaw);
    }

    /** How low it slinks (0..1) this frame. */
    public float crouch(float partialTick) {
        return Mth.lerp(partialTick, crouchO, crouch);
    }

    /** How far its head is thrown back to howl (0..1) this frame. */
    public float howl(float partialTick) {
        return Mth.lerp(partialTick, howlO, howl);
    }

    // ------------------------------------------------------------------ the brain

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        modeTicks++;
        if (biteCooldown > 0) {
            biteCooldown--;
        }
        if (rushCooldown > 0) {
            rushCooldown--;
        }
        if (flinchTicks > 0) {
            flinchTicks--;
        }
        if (--packRefreshIn <= 0) {
            refreshPack();
        }
        if (keepOutOfSafeRooms()) {
            return;
        }
        boolean senseNow = (tickCount + getId()) % SENSE_INTERVAL == 0;
        switch (mode) {
            case ROAM -> tickRoam(senseNow);
            case HOWL -> tickHowl();
            case CIRCLE -> tickCircle();
            case RUSH -> tickRush();
            case RETREAT -> tickRetreat();
            case CHASE -> tickChase();
            case SEARCH -> tickSearch(senseNow);
            case PACE -> tickPace();
            case FLEE -> tickFlee();
        }
    }

    /** Safe rooms are off limits, as for every monster. True if it stopped this tick. */
    private boolean keepOutOfSafeRooms() {
        var path = getNavigation().getPath();
        boolean headingIn = path != null && !path.isDone() && SafeRooms.isSafe(level(), path.getNextNodePos());
        boolean inside = SafeRooms.isSafe(this);
        if (!headingIn && !inside) {
            return false;
        }
        getNavigation().stop();
        if (mode != Mode.ROAM) {
            loseInterest();
        }
        return inside;
    }

    private void tickRoam(boolean senseNow) {
        if (senseNow) {
            Player seen = L227Senses.nearestVisible(this, SIGHT);
            if (seen != null && isOnHighGround(seen)) {
                startHowl(seen);
                return;
            }
            Player heard = L227Senses.nearestSprinting(this, HEAR_SPRINT);
            if (heard != null && isOnHighGround(heard)) {
                lastSeen = heard.position();
                startSearch();
                return;
            }
        }
        L227Wolf leader = leader();
        if (leader != this) {
            // Packmates trot after the leader in a loose file, each a few blocks behind.
            if (getNavigation().isDone() || modeTicks % 20 == 0) {
                double gap = 3.0 + slot() * 2.0;
                if (distanceToSqr(leader) > gap * gap) {
                    getNavigation().moveTo(leader, FOLLOW_SPEED);
                }
            }
            return;
        }
        if (!high(getY() - 2.0)) {
            // Drifted too low: head back up towards the trunk (the branches climb as they go in).
            if (getNavigation().isDone()) {
                Vec3 in = new Vec3(-getX(), 0.0, -getZ());
                if (in.lengthSqr() > 1.0) {
                    Vec3 to = position().add(in.normalize().scale(14.0));
                    getNavigation().moveTo(to.x, getY() + 4.0, to.z, ROAM_SPEED);
                }
            }
            return;
        }
        // Long, purposeful trots with pauses to sniff the air.
        if (getNavigation().isDone() && random.nextInt(70) == 0) {
            Vec3 to = DefaultRandomPos.getPos(this, 16, 4);
            if (to != null && high(to.y)) {
                getNavigation().moveTo(to.x, to.y, to.z, ROAM_SPEED);
            }
        }
    }

    private void tickHowl() {
        if (!L227Senses.isHuntable(prey, this)) {
            lostOrHidden();
            return;
        }
        getNavigation().stop();
        getLookControl().setLookAt(prey, 30.0f, 30.0f);
        if (modeTicks >= 30) {
            startCircle();
        }
    }

    private void tickCircle() {
        if (!checkPrey()) {
            return;
        }
        boolean visible = getSensing().hasLineOfSight(prey);
        if (visible) {
            lastSeen = prey.position();
            unseenTicks = 0;
        } else if (++unseenTicks > LOST_SIGHT_TICKS) {
            startSearch();
            return;
        }
        double distance = Math.sqrt(distanceToSqr(prey));
        if (distance > BREAKOUT_RANGE && prey.isSprinting()) {
            startChase();
            return;
        }
        getLookControl().setLookAt(prey, 30.0f, 30.0f);
        // The ring turns slowly around the prey. The leader owns the angle; the others take
        // evenly spaced places around it, so the pack always comes from several sides.
        L227Wolf leader = leader();
        if (leader == this) {
            ringAngle += RING_SPIN;
        } else {
            ringAngle = leader.ringAngle;
        }
        int size = Math.max(1, pack.size() + 1);
        float angle = ringAngle + slot() * Mth.TWO_PI / size;
        if (--repathIn <= 0) {
            repathIn = RING_REPATH_TICKS;
            double x = lastSeen.x + Mth.cos(angle) * RING_RADIUS;
            double z = lastSeen.z + Mth.sin(angle) * RING_RADIUS;
            getNavigation().moveTo(x, lastSeen.y, z, CIRCLE_SPEED);
        }
        if (modeTicks % 40 == 7 && random.nextInt(3) == 0) {
            playSound(Level227Predators.WOLF_GROWL.get(), 1.2f, 0.85f + random.nextFloat() * 0.2f);
        }
        // One rush at a time: only when no packmate is already darting in.
        if (rushCooldown <= 0 && flinchTicks <= 0 && visible && distance < RING_RADIUS + 4.0 && !packmateRushing()) {
            startRush();
        }
    }

    private void tickRush() {
        if (!checkPrey()) {
            return;
        }
        getLookControl().setLookAt(prey, 40.0f, 40.0f);
        double distanceSqr = distanceToSqr(prey);
        if (distanceSqr < 9.0) {
            // Close and committed: straight at you (a path would stop a block short).
            getNavigation().stop();
            getMoveControl().setWantedPosition(prey.getX(), prey.getY(), prey.getZ(), RUSH_SPEED);
        } else if (--repathIn <= 0) {
            repathIn = 5;
            getNavigation().moveTo(prey, RUSH_SPEED);
        }
        if (!bitThisRush && biteCooldown <= 0 && isWithinMeleeAttackRange(prey)) {
            bitThisRush = true;
            bite(prey);
        }
        if (bitThisRush && modeTicks > 4 || modeTicks >= RUSH_MAX_TICKS) {
            startRetreat();
        }
    }

    /** After a bite it springs back out to the ring, still facing you. */
    private void tickRetreat() {
        if (!checkPrey()) {
            return;
        }
        getLookControl().setLookAt(prey, 30.0f, 30.0f);
        if (modeTicks == 1) {
            Vec3 away = position().subtract(prey.position()).multiply(1.0, 0.0, 1.0);
            if (away.lengthSqr() < 1.0e-4) {
                away = new Vec3(random.nextDouble() - 0.5, 0.0, random.nextDouble() - 0.5);
            }
            Vec3 to = prey.position().add(away.normalize().scale(RING_RADIUS));
            getNavigation().moveTo(to.x, to.y, to.z, RETREAT_SPEED);
        }
        if (modeTicks >= RETREAT_TICKS) {
            setMode(Mode.CIRCLE);
            repathIn = 0;
        }
    }

    private void tickChase() {
        if (!checkPrey()) {
            return;
        }
        if (distanceToSqr(prey) > CHASE_GIVE_UP * CHASE_GIVE_UP) {
            startSearch();
            return;
        }
        if (getSensing().hasLineOfSight(prey)) {
            unseenTicks = 0;
            lastSeen = prey.position();
            getLookControl().setLookAt(prey, 30.0f, 30.0f);
            double distanceSqr = distanceToSqr(prey);
            if (distanceSqr < 9.0) {
                getNavigation().stop();
                getMoveControl().setWantedPosition(prey.getX(), prey.getY(), prey.getZ(), CHASE_SPEED);
            } else if (--repathIn <= 0) {
                repathIn = 8;
                getNavigation().moveTo(prey, CHASE_SPEED);
            }
            if (biteCooldown <= 0 && isWithinMeleeAttackRange(prey)) {
                bite(prey);
            }
            // Caught up and the prey has stopped running: close the ring again.
            if (distanceSqr < RECLOSE_RANGE * RECLOSE_RANGE && !prey.isSprinting() && modeTicks > 40) {
                startCircle();
            }
            return;
        }
        if (unseenTicks++ == 0) {
            getNavigation().moveTo(lastSeen.x, lastSeen.y, lastSeen.z, CHASE_SPEED);
        }
        if (unseenTicks >= LOST_SIGHT_TICKS) {
            startSearch();
        }
    }

    private void tickSearch(boolean senseNow) {
        if (senseNow) {
            Player seen = L227Senses.nearestVisible(this, SIGHT);
            if (seen != null && isOnHighGround(seen)) {
                // Found again: no need to howl, the pack is already out.
                prey = seen;
                setTarget(seen);
                lastSeen = seen.position();
                alertPack(seen);
                startCircle();
                return;
            }
        }
        if (getNavigation().isDone() && modeTicks % 30 == 0) {
            Vec3 to = DefaultRandomPos.getPosTowards(this, 8, 3, lastSeen, Mth.HALF_PI);
            if (to != null && high(to.y)) {
                getNavigation().moveTo(to.x, to.y, to.z, SEARCH_SPEED);
            }
        }
        if (modeTicks >= SEARCH_TICKS) {
            loseInterest();
        }
    }

    /** The prey went below the high sections: pace along the line, howl at it, then go home. */
    private void tickPace() {
        if (prey != null && prey.isAlive()) {
            getLookControl().setLookAt(prey, 20.0f, 20.0f);
        }
        if (getNavigation().isDone() && modeTicks % 25 == 0) {
            Vec3 to = DefaultRandomPos.getPos(this, 5, 2);
            if (to != null && high(to.y)) {
                getNavigation().moveTo(to.x, to.y, to.z, ROAM_SPEED);
            }
        }
        if (modeTicks == 40 && random.nextInt(2) == 0) {
            playSound(Level227Predators.WOLF_HOWL.get(), 3.0f, 0.95f + random.nextFloat() * 0.1f);
        }
        if (modeTicks > 20 && prey != null && L227Senses.isHuntable(prey, this) && isOnHighGround(prey)
                && high(getY() + 1.0) && getSensing().hasLineOfSight(prey)) {
            startCircle(); // came back up: straight back to the hunt
            return;
        }
        if (modeTicks >= PACE_TICKS) {
            loseInterest();
        }
    }

    /** Badly hurt: run from the prey for good (it doesn't come back this hunt). */
    private void tickFlee() {
        if (modeTicks % 20 == 1) {
            Vec3 from = prey != null ? prey.position() : lastSeen;
            Vec3 away = position().subtract(from).multiply(1.0, 0.0, 1.0);
            if (away.lengthSqr() > 1.0e-4) {
                Vec3 to = position().add(away.normalize().scale(10.0));
                getNavigation().moveTo(to.x, to.y, to.z, FLEE_SPEED);
            }
        }
        if (modeTicks >= FLEE_TICKS) {
            prey = null;
            setTarget(null);
            setMode(Mode.ROAM);
        }
    }

    // ------------------------------------------------------------------ transitions

    /**
     * Keeps a hunt going: false (and the mode already changed) if the prey died, slipped away, hid,
     * or went below the high sections.
     */
    private boolean checkPrey() {
        if (prey != null && prey.isDeadOrDying()) {
            loseInterest();
            return false;
        }
        if (!L227Senses.isHuntable(prey, this)) {
            lostOrHidden();
            return false;
        }
        if (!isOnHighGround(prey) || !high(getY() + 1.0)) {
            setMode(Mode.PACE);
            getNavigation().stop();
            return false;
        }
        return true;
    }

    private void startHowl(Player player) {
        prey = player;
        setTarget(player);
        lastSeen = player.position();
        setMode(Mode.HOWL);
        getNavigation().stop();
        // Loud on purpose: it carries far through the forest, so you know a pack is coming.
        playSound(Level227Predators.WOLF_HOWL.get(), 4.0f, 0.9f + random.nextFloat() * 0.15f);
        alertPack(player);
    }

    /** Every packmate not already hunting joins in, with a short yip. */
    private void alertPack(Player player) {
        for (L227Wolf mate : pack) {
            if (mate.isAlive() && (mate.mode == Mode.ROAM || mate.mode == Mode.SEARCH || mate.mode == Mode.PACE)) {
                mate.joinHunt(player);
            }
        }
    }

    private void joinHunt(Player player) {
        if (!L227Senses.isHuntable(player, this)) {
            return;
        }
        prey = player;
        setTarget(player);
        lastSeen = player.position();
        startCircle();
        if (random.nextInt(2) == 0) {
            playSound(Level227Predators.WOLF_YIP.get(), 2.0f, 0.9f + random.nextFloat() * 0.25f);
        }
    }

    private void startCircle() {
        setMode(Mode.CIRCLE);
        repathIn = 0;
        unseenTicks = 0;
        if (leader() == this && prey != null) {
            // Start the ring where the leader already is, so nobody has to cross through the prey.
            ringAngle = (float) Mth.atan2(getZ() - prey.getZ(), getX() - prey.getX());
        }
        rushCooldown = Math.max(rushCooldown, FIRST_RUSH_TICKS + slot() * RUSH_GAP_TICKS);
    }

    private void startRush() {
        setMode(Mode.RUSH);
        bitThisRush = false;
        repathIn = 0;
        playSound(Level227Predators.WOLF_GROWL.get(), 1.8f, 1.05f + random.nextFloat() * 0.15f);
    }

    private void startRetreat() {
        setMode(Mode.RETREAT);
        // The next wolf's turn comes before this one's.
        rushCooldown = RUSH_GAP_TICKS * Math.max(1, pack.size()) + random.nextInt(RUSH_GAP_EXTRA);
    }

    private void startChase() {
        setMode(Mode.CHASE);
        repathIn = 0;
        unseenTicks = 0;
    }

    private void startSearch() {
        setMode(Mode.SEARCH);
        getNavigation().moveTo(lastSeen.x, lastSeen.y, lastSeen.z, SEARCH_SPEED);
    }

    private void loseInterest() {
        prey = null;
        setTarget(null);
        getNavigation().stop();
        setMode(Mode.ROAM);
    }

    /** The prey slipped away: if it hid in furniture, search where it was last seen; else forget it. */
    private void lostOrHidden() {
        if (HidingService.isHidden(prey)) {
            startSearch();
        } else {
            loseInterest();
        }
    }

    /** It dragged someone out of hiding: straight into the rush. */
    @Override
    public void onPulledOut(ServerPlayer player) {
        prey = player;
        setTarget(player);
        lastSeen = player.position();
        alertPack(player);
        startRush();
    }

    @Override
    public boolean isSeeking() {
        return mode != Mode.ROAM && mode != Mode.FLEE;
    }

    private void setMode(Mode next) {
        mode = next;
        modeTicks = 0;
        entityData.set(MODE, (byte) next.ordinal());
    }

    private void bite(Player target) {
        biteCooldown = BITE_COOLDOWN_TICKS;
        swing(InteractionHand.MAIN_HAND);
        if (doHurtTarget(target)) {
            playSound(Level227Predators.WOLF_SNAP.get(), 1.5f, 0.9f + random.nextFloat() * 0.2f);
        }
    }

    // ------------------------------------------------------------------ the pack

    /** A fresh pack number (any wolves given the same number hunt together). Server thread only. */
    public static int newPackId() {
        return nextPack++;
    }

    /** Finds packmates nearby again (cheap: once every two seconds, one box query). */
    private void refreshPack() {
        packRefreshIn = PACK_REFRESH_TICKS;
        pack.clear();
        if (packId == 0) {
            // A wolf from a spawn egg or a command: join a pack close by, or found a new one that
            // the next wolf placed nearby will join.
            for (L227Wolf other : level().getEntitiesOfClass(L227Wolf.class, getBoundingBox().inflate(24.0), wolf -> wolf != this)) {
                if (other.packId != 0) {
                    packId = other.packId;
                    break;
                }
            }
            if (packId == 0) {
                packId = newPackId();
            }
        }
        pack.addAll(level().getEntitiesOfClass(L227Wolf.class, getBoundingBox().inflate(PACK_RANGE, 24.0, PACK_RANGE),
                wolf -> wolf != this && wolf.packId == packId && wolf.isAlive()));
        // Badly hurt packmates count against the pack's nerve: half the pack down, everyone runs.
        int hurt = isBadlyHurt() ? 1 : 0;
        for (L227Wolf mate : pack) {
            if (mate.isBadlyHurt()) {
                hurt++;
            }
        }
        if (prey != null && mode != Mode.FLEE && hurt * 2 > pack.size() + 1) {
            setMode(Mode.FLEE);
        }
    }

    /** The pack's leader: the wolf with the lowest entity id (every member agrees without talking). */
    private L227Wolf leader() {
        L227Wolf leader = this;
        for (L227Wolf mate : pack) {
            if (mate.isAlive() && mate.getId() < leader.getId()) {
                leader = mate;
            }
        }
        return leader;
    }

    /** This wolf's place in the pack, counted by entity id (0 = leader). */
    private int slot() {
        int slot = 0;
        for (L227Wolf mate : pack) {
            if (mate.isAlive() && mate.getId() < getId()) {
                slot++;
            }
        }
        return slot;
    }

    private boolean packmateRushing() {
        for (L227Wolf mate : pack) {
            if (mate.mode == Mode.RUSH) {
                return true;
            }
        }
        return false;
    }

    private boolean isBadlyHurt() {
        return getHealth() < getMaxHealth() * FLEE_HEALTH;
    }

    /** Wolves only hunt where they live: a player above the line of the high sections. */
    private boolean isOnHighGround(Player player) {
        return high(player.getY());
    }

    /** True if height {@code y} is in the high sections (always true outside Level 227). */
    private boolean high(double y) {
        return L227Senses.isHighGround(level(), y);
    }

    // ------------------------------------------------------------------ vanilla hooks

    /** A hurt wolf flinches out of the ring for a moment; a badly hurt one runs. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && isAlive()) {
            if (isBadlyHurt()) {
                setMode(Mode.FLEE);
                return true;
            }
            flinchTicks = FLINCH_TICKS;
            if (source.getEntity() instanceof Player player && L227Senses.isHuntable(player, this) && isOnHighGround(player)) {
                if (prey == null || mode == Mode.ROAM || mode == Mode.SEARCH) {
                    startHowl(player);
                } else if (mode == Mode.RUSH || mode == Mode.CIRCLE) {
                    startRetreat();
                }
            }
        }
        return hurt;
    }

    /** Idle wolves may wander off and despawn; hunting ones stay until the hunt is over. */
    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return mode == Mode.ROAM;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return mode == Mode.HOWL ? null : Level227Predators.WOLF_PANT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return Level227Predators.WOLF_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Level227Predators.WOLF_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        boolean running = mode == Mode.RUSH || mode == Mode.CHASE || mode == Mode.FLEE;
        playSound(Level227Predators.WOLF_STEP.get(), running ? 0.6f : 0.25f, 0.9f + random.nextFloat() * 0.2f);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }
}
