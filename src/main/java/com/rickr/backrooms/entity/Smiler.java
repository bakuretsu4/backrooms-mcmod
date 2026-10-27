package com.rickr.backrooms.entity;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraData;
import com.rickr.backrooms.registry.ModAttachments;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.LevelEvents;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.Level94Clock;
import com.rickr.backrooms.world.SafeRooms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Smilers: a glowing grin and two glowing eyes in the dark, with no body you can see (NightShot
 * shows the black, smoky shape underneath). They come out when the lights go out, and in places
 * that are always dark (see {@link SmilerSpawner}).
 *
 * <p>The rules, all decided by the server:
 * <pre>
 * APPEAR  --eyes have opened (2 s)-->                     STALK
 * STALK   --a flashlight beam is on its face-->           FLINCH
 * FLINCH  --no beam for 6 ticks-->                        STALK
 * FLINCH  --lit for 1.5 s in total (3 s if it's bold)-->  DISPEL
 * STALK   --within 4 blocks of its prey, not lit-->       LUNGE  (half a second, fast)
 * LUNGE   --touches its prey-->                           kills it, then DISPEL
 * any     --the blackout is over (Level 94: dawn; a dark area's Smiler: its prey stood in the light
 *           for 5 s) / its prey is in a safe room / nobody left-->                                DISPEL
 * DISPEL  --1 s shrivelling away-->                       removed
 * </pre>
 *
 * <p>Fairness: its face glows, so you see it from far away in the dark, and within a few blocks
 * you can hear it breathing (its only sound) even with your back to it. It can't be hurt, but
 * light always works on it. About one in six is "bold" (a visibly wider grin) and needs twice as
 * much light.
 */
public class Smiler extends Monster {
    /** What it is doing. The order matters: it is synced as a byte. */
    public enum Mode { APPEAR, STALK, FLINCH, LUNGE, DISPEL }

    private static final Mode[] MODES = Mode.values();
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(Smiler.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> BOLD = SynchedEntityData.defineId(Smiler.class, EntityDataSerializers.BOOLEAN);
    /** 0..1: how close the light is to burning it away. Drives the squint. */
    private static final EntityDataAccessor<Float> BURN = SynchedEntityData.defineId(Smiler.class, EntityDataSerializers.FLOAT);

    public static final ResourceKey<DamageType> SMILER_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Backrooms.id("smiler"));

    /** Height of the face above its feet (eye height), blocks. */
    public static final float FACE_HEIGHT = 1.7f;

    // --- Timings (ticks) ---------------------------------------------------------------------
    private static final int APPEAR_TICKS = 40;
    private static final int DISPEL_TICKS = 20;
    /** Light needed to drive it off: 1.5 s, or 3 s for a bold one. Short bursts add up. */
    private static final int BURN_TICKS = 30;
    private static final int BOLD_BURN_TICKS = 60;
    /** Back to stalking once no light has touched it for this long. */
    private static final int FLINCH_RELEASE_TICKS = 6;
    private static final int LUNGE_TICKS = 12;
    private static final int REPATH_TICKS = 10;
    /** Within this many blocks you hear it breathe, every 2.5 s. */
    private static final int BREATH_TICKS = 50;
    /** With no prey for this long it gives up and fades. */
    private static final int NO_PREY_TICKS = 600;
    /** One spawned by command (not by a blackout) fades after 90 s. */
    private static final int COMMAND_LIFETIME = 90 * 20;
    /** One that came out of a dark area fades once its prey has stood in the light this long. */
    private static final int DARK_LIGHT_GRACE = 5 * 20;

    // --- Distances (blocks) -----------------------------------------------------------------
    private static final double PREY_RANGE = 48.0;
    private static final double LUNGE_RANGE = 4.0;
    private static final double BREATH_RANGE = 6.0;
    /** The flashlight: matches the bright centre of the beam (client: Flashlights, 25 blocks). */
    private static final double BEAM_RANGE = 25.0;
    private static final double BEAM_COS = Math.cos(Math.toRadians(14.0));
    /** Standing in a lit spot (lamps on, no blackout) burns it too. */
    private static final int LIT_LEVEL = 7;

    // --- Speeds (multipliers of MOVEMENT_SPEED 0.26: about 3 blocks/s when stalking) ---------
    private static final double STALK_SPEED = 1.0;
    private static final double LUNGE_SPEED = 1.65;
    private static final double BACK_OFF_SPEED = 0.45;

    // Server-side brain.
    private Mode mode = Mode.APPEAR;
    private int modeTicks;
    private int burnTicks;
    private int unlitTicks;
    private int noPreyTicks;
    private int repathIn;
    private Origin origin = Origin.COMMAND;
    /** DARK origin: how long its prey has been standing in the light. */
    private int preyLitTicks;
    @Nullable
    private Player prey;
    @Nullable
    private Player lighter;

    // Client-side: when the synced mode last changed, for the face animation.
    private Mode clientMode = Mode.APPEAR;
    private int clientModeStart;

    public Smiler(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        setPersistenceRequired(); // the spawner decides when it goes, not vanilla despawning
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.FOLLOW_RANGE, PREY_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODE, (byte) Mode.APPEAR.ordinal());
        builder.define(BOLD, false);
        builder.define(BURN, 0.0f);
    }

    /** Where a Smiler came from, which decides when it goes away (see {@link #shouldFade}). */
    public enum Origin {
        /** {@code /backrooms spawn smiler}: fades after 90 s. */
        COMMAND,
        /** A blackout (Level 94: the night): fades when the lights come back (at dawn). */
        BLACKOUT,
        /**
         * A dark area that is always dark (Level 1's dead zones, Level 9's streets between the lamps):
         * fades once its prey has been standing in the light for a few seconds. It can't follow you
         * into the light anyway (the light burns it).
         */
        DARK
    }

    /** Set by the spawner. */
    public void setOrigin(Origin origin) {
        this.origin = origin;
    }

    public void setBold(boolean bold) {
        entityData.set(BOLD, bold);
    }

    public boolean isBold() {
        return entityData.get(BOLD);
    }

    public Mode mode() {
        byte b = entityData.get(MODE);
        return b >= 0 && b < MODES.length ? MODES[b] : Mode.STALK;
    }

    /** 0..1, how far the light has got toward burning it away (client: squint). */
    public float burn() {
        return entityData.get(BURN);
    }

    /** Ticks since the synced mode last changed, on the client (for the face animation). */
    public float clientModeAge(float partialTick) {
        return tickCount - clientModeStart + partialTick;
    }

    /** How strongly it glitches the camcorder, before distance is applied. */
    public float dangerWeight() {
        return switch (mode()) {
            case LUNGE -> 1.0f;
            case STALK, FLINCH -> 0.8f;
            case APPEAR -> 0.5f;
            case DISPEL -> 0.2f;
        };
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            Mode synced = mode();
            if (synced != clientMode) {
                clientMode = synced;
                clientModeStart = tickCount;
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        modeTicks++;
        if (mode == Mode.DISPEL) {
            getNavigation().stop();
            if (modeTicks >= DISPEL_TICKS) {
                discard();
            }
            return;
        }
        if (shouldFade()) {
            dispel();
            return;
        }
        if (tickCount % 20 == 0 || !isValidPrey(prey)) {
            prey = findPrey();
        }
        noPreyTicks = prey == null ? noPreyTicks + 1 : 0;
        if (noPreyTicks > NO_PREY_TICKS) {
            dispel();
            return;
        }

        // Light: checked every other tick (a few raycasts per player in range).
        if (tickCount % 2 == 0) {
            lighter = findLighter();
        }
        boolean lit = lighter != null || inLitSpot();
        if (lit && mode != Mode.APPEAR) {
            burnTicks += 1;
            unlitTicks = 0;
            entityData.set(BURN, Math.min(1.0f, (float) burnTicks / burnNeeded()));
            if (burnTicks >= burnNeeded()) {
                dispel();
                return;
            }
            if (mode != Mode.FLINCH) {
                setMode(Mode.FLINCH);
            }
        } else {
            unlitTicks++;
            // The burn heals slowly once the light is off, so short bursts still add up.
            if (unlitTicks > 20 && burnTicks > 0 && tickCount % 4 == 0) {
                burnTicks--;
                entityData.set(BURN, (float) burnTicks / burnNeeded());
            }
        }

        switch (mode) {
            case APPEAR -> tickAppear();
            case STALK -> tickStalk();
            case FLINCH -> tickFlinch();
            case LUNGE -> tickLunge();
            default -> {}
        }
    }

    private void tickAppear() {
        getNavigation().stop();
        if (prey != null) {
            getLookControl().setLookAt(prey, 30.0f, 30.0f);
        }
        if (modeTicks >= APPEAR_TICKS) {
            setMode(Mode.STALK);
        }
    }

    private void tickStalk() {
        if (prey == null) {
            getNavigation().stop();
            return;
        }
        getLookControl().setLookAt(prey, 30.0f, 30.0f);
        double distance = distanceTo(prey);
        if (distance <= LUNGE_RANGE && hasLineOfSight(prey)) {
            setMode(Mode.LUNGE);
            return;
        }
        if (--repathIn <= 0) {
            repathIn = REPATH_TICKS;
            getNavigation().moveTo(prey, STALK_SPEED);
        }
        if (distance <= BREATH_RANGE && tickCount % BREATH_TICKS == 0) {
            playSound(ModSounds.SMILER_BREATH.get(), 0.45f, 0.9f + random.nextFloat() * 0.15f);
        }
    }

    private void tickFlinch() {
        getNavigation().stop();
        // Back away from whoever is shining on it.
        if (lighter != null) {
            Vec3 away = position().subtract(lighter.position()).multiply(1.0, 0.0, 1.0);
            if (away.lengthSqr() > 1.0e-4) {
                Vec3 to = position().add(away.normalize().scale(1.5));
                getMoveControl().setWantedPosition(to.x, getY(), to.z, BACK_OFF_SPEED);
            }
        }
        if (unlitTicks >= FLINCH_RELEASE_TICKS) {
            setMode(Mode.STALK);
            repathIn = 0;
        }
    }

    private void tickLunge() {
        if (prey == null) {
            setMode(Mode.STALK);
            return;
        }
        getNavigation().stop();
        getMoveControl().setWantedPosition(prey.getX(), prey.getY(), prey.getZ(), LUNGE_SPEED);
        if (getBoundingBox().inflate(0.35).intersects(prey.getBoundingBox())) {
            kill(prey);
            return;
        }
        if (modeTicks >= LUNGE_TICKS) {
            setMode(Mode.STALK);
            repathIn = 0;
        }
    }

    private void kill(Player victim) {
        DamageSource source = new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SMILER_DAMAGE), this);
        victim.hurt(source, Float.MAX_VALUE);
        if (!victim.isAlive()) {
            dispel(); // it got what it came for
        } else {
            setMode(Mode.STALK); // something saved them (a totem): it comes again
        }
    }

    // ------------------------------------------------------------------ transitions

    private void setMode(Mode next) {
        mode = next;
        modeTicks = 0;
        entityData.set(MODE, (byte) next.ordinal());
    }

    /** Fades away: driven off by light, its prey gone, or the lights back on. */
    private void dispel() {
        if (mode == Mode.DISPEL) {
            return;
        }
        setMode(Mode.DISPEL);
        getNavigation().stop();
    }

    private int burnNeeded() {
        return isBold() ? BOLD_BURN_TICKS : BURN_TICKS;
    }

    /**
     * The lights are back on (for blackout Smilers), dawn in Level 94 (for its night Smilers), its
     * prey has stood in the light for a while (for Smilers of the dark areas), or a command one has
     * had its 90 s.
     */
    private boolean shouldFade() {
        BackroomsLevel floor = BackroomsLevel.fromDimension(level().dimension());
        return switch (origin) {
            case BLACKOUT -> floor == BackroomsLevel.LEVEL_94
                    ? Level94Clock.phase(level()) != Level94Clock.Phase.NIGHT
                    : floor == null || !LevelEvents.isBlackout(floor);
            case DARK -> {
                boolean preyInLight = prey != null && floor != null && !LevelEvents.isBlackout(floor)
                        && level().getMaxLocalRawBrightness(BlockPos.containing(prey.getEyePosition())) >= LIT_LEVEL;
                preyLitTicks = preyInLight ? preyLitTicks + 1 : 0;
                yield preyLitTicks > DARK_LIGHT_GRACE;
            }
            case COMMAND -> tickCount > COMMAND_LIFETIME;
        };
    }

    // ------------------------------------------------------------------ senses

    @Nullable
    private Player findPrey() {
        Player best = null;
        double bestDistance = PREY_RANGE * PREY_RANGE;
        for (Player player : level().players()) {
            if (isValidPrey(player)) {
                double distance = distanceToSqr(player);
                if (distance < bestDistance) {
                    best = player;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private boolean isValidPrey(@Nullable Player player) {
        return player != null && player.isAlive() && !player.isRemoved() && !player.isSpectator() && !player.isCreative()
                && player.level() == level() && distanceToSqr(player) < PREY_RANGE * PREY_RANGE && !SafeRooms.isSafe(player)
                && !inLevel94Shelter(player);
    }

    /** Level 94: the castle is as safe as a house (houses are safe rooms already, see above). */
    private boolean inLevel94Shelter(Player player) {
        if (!(level() instanceof ServerLevel serverLevel)
                || BackroomsLevel.fromDimension(serverLevel.dimension()) != BackroomsLevel.LEVEL_94) {
            return false;
        }
        return !SmilerSpawner.isOutsideInLevel94(BackroomsSeeds.layoutSeed(serverLevel, BackroomsLevel.LEVEL_94), player);
    }

    /**
     * The player whose flashlight is on its face right now, or null. "On its face" means: the
     * flashlight is on and the battery isn't dead, the face is within 25 blocks and within 14
     * degrees of where they're looking (the bright centre of the beam), and nothing is in between.
     */
    @Nullable
    private Player findLighter() {
        Vec3 face = facePosition();
        for (Player player : level().players()) {
            if (!(player instanceof ServerPlayer serverPlayer) || player.isSpectator()) {
                continue;
            }
            CameraData camera = serverPlayer.getData(ModAttachments.CAMERA);
            if (!camera.flashlight() || camera.dead()) {
                continue;
            }
            Vec3 eye = player.getEyePosition();
            Vec3 to = face.subtract(eye);
            double distance = to.length();
            if (distance > BEAM_RANGE || distance < 1.0e-3) {
                continue;
            }
            if (player.getLookAngle().dot(to.scale(1.0 / distance)) < BEAM_COS) {
                continue;
            }
            if (level().clip(new ClipContext(eye, face, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) {
                return player;
            }
        }
        return null;
    }

    /**
     * Lamps on (no blackout) and bright where its face is. In Level 94 only lamp light counts: the
     * level's dimension is stuck at noon as far as Minecraft's sky light knows (the real day and
     * night are drawn by {@link Level94Clock}), so sky light would "burn" every Smiler outdoors.
     */
    private boolean inLitSpot() {
        BackroomsLevel floor = BackroomsLevel.fromDimension(level().dimension());
        if (floor != null && LevelEvents.isBlackout(floor)) {
            return false;
        }
        BlockPos face = BlockPos.containing(facePosition());
        if (floor == BackroomsLevel.LEVEL_94) {
            return level().getBrightness(LightLayer.BLOCK, face) >= LIT_LEVEL;
        }
        return level().getMaxLocalRawBrightness(face) >= LIT_LEVEL;
    }

    public Vec3 facePosition() {
        return position().add(0.0, FACE_HEIGHT, 0.0);
    }

    // ------------------------------------------------------------------ vanilla hooks

    /** Weapons pass straight through it. Only /kill (or falling out of the world) removes it. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // It makes no footsteps: the breathing is the only sound when it's close.
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
