package com.rickr.backrooms.entity;

import com.rickr.backrooms.player.HidingService;
import com.rickr.backrooms.registry.Level227Grazers;
import com.rickr.backrooms.world.Travel;
import com.rickr.backrooms.world.anomaly.Teleports;
import com.rickr.backrooms.world.layout.Level227Tree;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The does of Level 227: slender, pale, harmless-looking deer with a long black scar-like mark
 * from the nose over the head and down the spine to the tip of the tail.
 *
 * <p>The wiki: they are not aggressive, but get close to one and you suddenly noclip through the
 * tree and fall into the void. So that is exactly what happens:
 * <ul>
 *   <li><b>She notices you first.</b> Within about 12 blocks (and in sight) she stops grazing,
 *       lifts her head and stares, perfectly still, her head following you. The scar opens a
 *       crack. That stare is the warning: back away and she goes back to grazing.</li>
 *   <li><b>Four blocks is too close.</b> Come within {@value #TAKE_RANGE} blocks of a doe (any
 *       doe, staring or not, even from behind) and the branch lets you through: you drop out of
 *       its underside into the open air below it, with the noclip transition, and fall.</li>
 *   <li><b>She doesn't care.</b> Afterwards she stands a moment, the scar wide open, then lowers
 *       her head and grazes on as if nothing happened. Hit her (from range) and she bolts.</li>
 * </ul>
 *
 * <p>Brain ({@link Mode}), run in {@link #customServerAiStep()}:
 * <pre>
 * GRAZE --a while--> WALK --there--> GRAZE      GRAZE/WALK --sees you--> WATCH --you left--> GRAZE
 * any --you are within 4 blocks--> TAKE (you fall) --2 s--> GRAZE      any --hurt--> FLEE --5 s--> GRAZE
 * </pre>
 */
public class L227Doe extends PathfinderMob {
    /** What the doe is doing. The order matters: it is synced as a byte. */
    public enum Mode { GRAZE, WALK, WATCH, TAKE, FLEE }

    private static final Mode[] MODES = Mode.values();
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(L227Doe.class, EntityDataSerializers.BYTE);

    /** She looks up at anyone this close (and in sight)... */
    private static final double NOTICE_RANGE = 12.0;
    /** ...keeps staring until they are this far off or out of sight for a while... */
    private static final double FORGET_RANGE = 16.0;
    private static final int FORGET_TICKS = 60;
    /** ...and anyone this close (horizontally) falls through the branch. */
    public static final double TAKE_RANGE = 4.0;
    private static final int SENSE_INTERVAL = 5;
    private static final int TAKE_TICKS = 40;
    private static final int FLEE_TICKS = 100;
    /** A player who just fell can't be taken again for this long (they are falling anyway). */
    private static final int TAKEN_COOLDOWN_TICKS = 60;

    // --- Speeds (multipliers of MOVEMENT_SPEED 0.25) --------------------------------------------
    private static final double WALK_SPEED = 0.55;
    private static final double FLEE_SPEED = 2.0;

    // --- The drop -----------------------------------------------------------------------------
    /** Feet go this far below the first air block under the branch, so the head is clear of the bark. */
    private static final double BELOW_UNDERSIDE = 2.0;
    /** If the spot is in bark relief after all, look this much further down for open air. */
    private static final int AIR_SEARCH = 12;

    /** When each player (by UUID) was last dropped, in game time. Server-side, shared by all does. */
    private static final Map<UUID, Long> TAKEN_AT = new HashMap<>();

    // Server-side brain state (never saved: the spawner keeps the forest stocked).
    private Mode mode = Mode.GRAZE;
    private int modeTicks;
    private int modeLength = 120;
    @Nullable
    private Player watched;
    private int unseenTicks;

    // Client-only visuals, eased each tick: head down to graze (0..1), alert (staring, 0..1) and
    // how far the scar has opened (0..1).
    private float graze;
    private float grazeO;
    private float alert;
    private float alertO;
    private float scar;
    private float scarO;

    public L227Doe(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 16.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 20.0)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODE, (byte) Mode.GRAZE.ordinal());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    public Mode mode() {
        byte b = entityData.get(MODE);
        return b >= 0 && b < MODES.length ? MODES[b] : Mode.GRAZE;
    }

    // ------------------------------------------------------------------ client visuals

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            Mode current = mode();
            grazeO = graze;
            alertO = alert;
            scarO = scar;
            float grazeTarget = current == Mode.GRAZE ? 1.0f : 0.0f;
            float alertTarget = current == Mode.WATCH || current == Mode.TAKE ? 1.0f : 0.0f;
            float scarTarget = switch (current) {
                case WATCH -> 0.3f;
                case TAKE -> 1.0f;
                default -> 0.0f;
            };
            // The head comes up fast when she notices you, and goes down slowly.
            graze += (grazeTarget - graze) * (grazeTarget < graze ? 0.25f : 0.05f);
            alert += (alertTarget - alert) * 0.2f;
            scar += (scarTarget - scar) * (scarTarget > scar ? 0.3f : 0.04f);
        }
    }

    public float graze(float partialTick) {
        return Mth.lerp(partialTick, grazeO, graze);
    }

    public float alert(float partialTick) {
        return Mth.lerp(partialTick, alertO, alert);
    }

    public float scar(float partialTick) {
        return Mth.lerp(partialTick, scarO, scar);
    }

    // ------------------------------------------------------------------ the brain

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        modeTicks++;
        // The rule that matters is checked every tick: 4 blocks means you fall.
        if (isAlive() && level() instanceof ServerLevel server) {
            for (ServerPlayer player : server.players()) {
                if (canTake(player) && horizontalDistanceSqr(player) < TAKE_RANGE * TAKE_RANGE
                        && Math.abs(player.getY() - getY()) < 3.0 && take(player)) {
                    watched = player;
                    setMode(Mode.TAKE);
                    getNavigation().stop();
                }
            }
        }
        switch (mode) {
            case GRAZE -> tickGraze();
            case WALK -> tickWalk();
            case WATCH -> tickWatch();
            case TAKE -> tickTake();
            case FLEE -> tickFlee();
        }
    }

    private void tickGraze() {
        getNavigation().stop();
        if (noticeSomeone()) {
            return;
        }
        if (modeTicks >= modeLength) {
            Vec3 to = DefaultRandomPos.getPos(this, 10, 3);
            if (to != null && getNavigation().moveTo(to.x, to.y, to.z, WALK_SPEED)) {
                setMode(Mode.WALK);
                modeLength = 200;
            } else {
                modeTicks = 0;
            }
        }
    }

    private void tickWalk() {
        if (noticeSomeone()) {
            return;
        }
        if (getNavigation().isDone() || modeTicks >= modeLength) {
            startGraze();
        }
    }

    private void tickWatch() {
        getNavigation().stop();
        if (watched == null || !canTake(watched) || distanceToSqr(watched) > FORGET_RANGE * FORGET_RANGE) {
            startGraze();
            return;
        }
        if (getSensing().hasLineOfSight(watched)) {
            unseenTicks = 0;
        } else if (++unseenTicks > FORGET_TICKS) {
            startGraze();
            return;
        }
        // Head and then the body turn after you: slowly, like a deer that is not afraid of you.
        getLookControl().setLookAt(watched, 10.0f, 30.0f);
        turnBodyTowards(watched.position(), 3.0f);
    }

    private void tickTake() {
        getNavigation().stop();
        if (watched != null) {
            getLookControl().setLookAt(watched, 10.0f, 40.0f);
        }
        if (modeTicks >= TAKE_TICKS) {
            watched = null;
            startGraze();
        }
    }

    private void tickFlee() {
        if (modeTicks >= FLEE_TICKS) {
            startGraze();
        } else if (getNavigation().isDone()) {
            Vec3 to = DefaultRandomPos.getPos(this, 16, 5);
            if (to != null) {
                getNavigation().moveTo(to.x, to.y, to.z, FLEE_SPEED);
            }
        }
    }

    /** Looks up at the nearest player in sight within {@link #NOTICE_RANGE}. True if she did. */
    private boolean noticeSomeone() {
        if ((tickCount + getId()) % SENSE_INTERVAL != 0) {
            return false;
        }
        Player best = null;
        double bestDistance = NOTICE_RANGE * NOTICE_RANGE;
        for (Player player : level().players()) {
            double distance = distanceToSqr(player);
            if (distance < bestDistance && canTake(player) && getSensing().hasLineOfSight(player)) {
                best = player;
                bestDistance = distance;
            }
        }
        if (best == null) {
            return false;
        }
        watched = best;
        unseenTicks = 0;
        setMode(Mode.WATCH);
        // A soft breath through the nose: the only sound she makes when she sees you.
        playSound(Level227Grazers.DOE_NOTICE.get(), 0.8f, 0.95f + random.nextFloat() * 0.1f);
        return true;
    }

    private void startGraze() {
        setMode(Mode.GRAZE);
        modeLength = 80 + random.nextInt(160);
    }

    private void setMode(Mode next) {
        mode = next;
        modeTicks = 0;
        entityData.set(MODE, (byte) next.ordinal());
    }

    private void turnBodyTowards(Vec3 target, float maxStep) {
        float yaw = (float) (Mth.atan2(target.z - getZ(), target.x - getX()) * Mth.RAD_TO_DEG) - 90.0f;
        setYRot(Mth.approachDegrees(getYRot(), yaw, maxStep));
        yBodyRot = getYRot();
    }

    private double horizontalDistanceSqr(Entity other) {
        double dx = other.getX() - getX();
        double dz = other.getZ() - getZ();
        return dx * dx + dz * dz;
    }

    private static boolean canTake(@Nullable Player player) {
        return player != null && player.isAlive() && !player.isSpectator() && !player.isCreative()
                && !HidingService.isHidden(player);
    }

    // ------------------------------------------------------------------ the drop

    /**
     * Drops the player through the branch they stand on: a teleport to just under its underside,
     * straight below where they stood, with the noclip glitch and sound. From there they fall.
     * False (nothing happens) if there is no branch under them, they were dropped a moment ago,
     * or no open air was found under the bark.
     */
    private boolean take(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Long last = TAKEN_AT.get(player.getUUID());
        if (last != null && now - last < TAKEN_COOLDOWN_TICKS) {
            return false;
        }
        double feet = undersideFeetY(level, player.getX(), player.getY(), player.getZ());
        if (Double.isNaN(feet)) {
            return false;
        }
        TAKEN_AT.put(player.getUUID(), now);
        // The doe's own sound where it happened (others nearby hear the branch open)...
        level.playSound(null, player.getX(), player.getY(), player.getZ(), Level227Grazers.DOE_SLIP.get(),
                SoundSource.HOSTILE, 1.0f, 1.0f);
        // ...and the noclip itself for the one who falls, filmed like every move between places
        // (the picture breaks up, then open air under the bark). The spot is worked out again when
        // the move happens, in case they shifted during the freeze.
        Travel.moshTo(player, p -> {
            double under = undersideFeetY(p.serverLevel(), p.getX(), p.getY(), p.getZ());
            if (Double.isNaN(under)) {
                return;
            }
            p.stopRiding();
            p.setDeltaMovement(Vec3.ZERO);
            Teleports.to(p, p.getX(), under, p.getZ(), p.getYRot(), p.getXRot());
        });
        return true;
    }

    /**
     * Feet height just under the underside of the branch the player stands on at (x, z), or NaN.
     *
     * <p>The underside comes from the tree's plan ({@link Level227Tree#undersideY}), not from a
     * block scan, because the inside of a big branch may be hollow (sealed air) and a scan would
     * stop there. Then the spot is checked against the real blocks, in case the bark relief hangs
     * a little lower than the plan.
     */
    private static double undersideFeetY(ServerLevel level, double x, double feetY, double z) {
        int firstAir = Level227Tree.undersideY(x, z, feetY);
        if (firstAir == Integer.MIN_VALUE) {
            return Double.NaN;
        }
        double feet = firstAir - BELOW_UNDERSIDE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        for (int drop = 0; drop <= AIR_SEARCH; drop++) {
            int fy = Mth.floor(feet) - drop;
            if (fy <= level.getMinBuildHeight() + 1) {
                break;
            }
            if (open(level, pos.set(bx, fy, bz)) && open(level, pos.set(bx, fy + 1, bz)) && open(level, pos.set(bx, fy + 2, bz))) {
                return feet - drop;
            }
        }
        return Double.NaN;
    }

    private static boolean open(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    // ------------------------------------------------------------------ vanilla hooks

    /** Hurt, she bolts. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && isAlive() && mode != Mode.TAKE) {
            setMode(Mode.FLEE);
            Entity attacker = source.getEntity();
            Vec3 to = attacker != null ? DefaultRandomPos.getPosAway(this, 16, 5, attacker.position()) : DefaultRandomPos.getPos(this, 16, 5);
            if (to != null) {
                getNavigation().moveTo(to.x, to.y, to.z, FLEE_SPEED);
            }
        }
        return hurt;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return true;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 300;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return mode == Mode.GRAZE || mode == Mode.WALK ? Level227Grazers.DOE_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return Level227Grazers.DOE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Level227Grazers.DOE_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(Level227Grazers.DOE_STEP.get(), mode == Mode.FLEE ? 0.5f : 0.25f, 0.95f + random.nextFloat() * 0.15f);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }
}
