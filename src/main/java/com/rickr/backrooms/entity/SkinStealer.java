package com.rickr.backrooms.entity;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.Level39Mobs;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.SafeRooms;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Skin-stealers (Entity 10): tall, gaunt, pale-yellow humanoids with sunken white eyes, wearing a
 * loose, sagging suit of stolen human skin. Their blood is thin and see-through.
 *
 * <p>They are not always dangerous. A fed one is docile: it drifts through the tunnels and ignores
 * you (unless you hurt it). A hungry one hunts people who are ALONE:
 * <ul>
 *   <li>It stalks: it follows {@value #KEEP_MIN}-{@value #KEEP_MAX} blocks behind, out of sight.</li>
 *   <li>When you can't see it, it calls to you, mimicking speech (garbled words, sometimes your
 *       own words from chat), to lure you closer.</li>
 *   <li>If you turn and look straight at it, it freezes and stares; keep looking and it slinks
 *       back out of sight. Walk up to it and it charges.</li>
 *   <li>Sooner or later, once you are close and looking away, it charges and tears at you with its
 *       hands. It is slower than your sprint, so running works; once it has fed enough it leaves.</li>
 * </ul>
 * Someone with company (another player within {@value #LONE_RADIUS} blocks) is never stalked.
 *
 * <p>State machine on the server ({@link #customServerAiStep()}), synced so the model can pose:
 * <pre>
 * WANDER --hungry and a lone player near--> STALK --you look at it--> FROZEN --still looking--> (slinks off) STALK
 * STALK --close and you look away / stalked long enough--> CHARGE --fed / lost you--> WANDER / STALK
 * any --badly hurt--> RETREAT (runs, vanishes out of sight)
 * </pre>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public class SkinStealer extends Monster {
    public enum Mode { WANDER, STALK, FROZEN, CHARGE, RETREAT }

    private static final Mode[] MODES = Mode.values();
    private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(SkinStealer.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> HUNGRY = SynchedEntityData.defineId(SkinStealer.class, EntityDataSerializers.BOOLEAN);

    /** Entity event: it is calling out (the model opens the jaw and tilts the head). */
    public static final byte EVENT_MIMIC = 73;

    // --- Who it hunts -----------------------------------------------------------------------
    /** A player with nobody else this close counts as alone. */
    public static final double LONE_RADIUS = 20.0;
    private static final double HUNT_RANGE = 48.0;
    private static final double KEEP_MIN = 10.0;
    private static final double KEEP_MAX = 18.0;
    /** Coming this close to it (or it getting this close to you unseen) starts the charge. */
    private static final double CHARGE_RANGE = 8.0;
    /** "Looking at it": within this angle of your view centre (cosine), and in clear sight. */
    private static final double WATCH_COS = 0.9;
    private static final double WATCH_RANGE = 40.0;

    // --- Timings (ticks) ----------------------------------------------------------------------
    private static final int SENSE_INTERVAL = 5;
    private static final int STALK_MIN_TICKS = 30 * 20;
    private static final int STALK_MAX_TICKS = 55 * 20;
    private static final int MIMIC_MIN_TICKS = 8 * 20;
    private static final int MIMIC_MAX_TICKS = 16 * 20;
    private static final int FROZEN_MAX_TICKS = 3 * 20;
    private static final int ATTACK_COOLDOWN = 24;
    private static final int CHARGE_LOST_TICKS = 100;
    /** After a failed charge it stalks again for at least this long before the next one. */
    private static final int RECHARGE_TICKS = 25 * 20;
    private static final int HUNGER_MIN_TICKS = 2 * 60 * 20;
    private static final int HUNGER_MAX_TICKS = 5 * 60 * 20;
    private static final int LONELY_DESPAWN_TICKS = 90 * 20;
    private static final double LONELY_RANGE = 96.0;

    // --- Fighting -----------------------------------------------------------------------------
    /** Once it has dealt this much damage it is fed, and leaves. */
    private static final float FEED_DAMAGE = 10.0f;
    private static final double CHARGE_GIVE_UP = 32.0;
    private static final float RETREAT_HEALTH = 0.35f;

    // --- Speeds (multipliers of MOVEMENT_SPEED 0.28) -----------------------------------------
    // About 4.3 blocks/s while charging: faster than a walk, slower than your sprint.
    private static final double CHARGE_SPEED = 1.15;
    private static final double STALK_SPEED = 0.8;
    private static final double WANDER_SPEED = 0.5;
    private static final double RETREAT_SPEED = 1.2;

    /** The last thing each player said in chat on Level 39, for the mimicry. Server thread only. */
    private static final Map<UUID, String> LAST_WORDS = new HashMap<>();
    private static final String[] PHRASES = {
            "hello?", "is someone there", "help me", "over here", "wait for me", "i found a way out",
            "come closer", "where are you", "it's safe here", "don't leave me"};

    private Mode mode = Mode.WANDER;
    private int modeTicks;
    @Nullable
    private Player prey;
    private int hungerIn;
    private int stalkBudget;
    private int mimicIn = MIMIC_MIN_TICKS;
    private int attackCooldown;
    private int unseenTicks;
    private int rechargeIn;
    private float fed;
    private int lonelyTicks;

    // Client-only visual.
    private int mimicStart = -1000;
    /** How much each mode's pose shows (0..1), eased every tick so it never snaps between poses. */
    private final float[] poseWeight = new float[MODES.length];
    private final float[] poseWeightO = new float[MODES.length];

    public SkinStealer(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 25;
        // It wades through the flooded tunnels without minding the water.
        setPathfindingMalus(PathType.WATER, 0.0f);
        setPathfindingMalus(PathType.WATER_BORDER, 0.0f);
        this.poseWeight[Mode.WANDER.ordinal()] = 1.0f;
        this.poseWeightO[Mode.WANDER.ordinal()] = 1.0f;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 50.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, HUNT_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MODE, (byte) Mode.WANDER.ordinal());
        builder.define(HUNGRY, false);
    }

    @Override
    protected void registerGoals() {
        // Everything is in customServerAiStep; it is tall enough to wade, so no swimming goal.
    }

    /** Fed or hungry from the start: a bit more than a third of them arrive hungry. */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, type, data);
        boolean hungry = type == MobSpawnType.COMMAND || random.nextFloat() < 0.4f;
        setHungry(hungry);
        return result;
    }

    // ------------------------------------------------------------------ synced getters

    public Mode mode() {
        byte b = entityData.get(MODE);
        return b >= 0 && b < MODES.length ? MODES[b] : Mode.WANDER;
    }

    public boolean isHungry() {
        return entityData.get(HUNGRY);
    }

    /** Tick on which it last started calling out (client side), for the jaw animation. */
    public int mimicStart() {
        return mimicStart;
    }

    /** How far into the given mode's pose the body is this frame (0..1). Client side. */
    public float poseWeight(Mode which, float partialTick) {
        return Mth.lerp(partialTick, poseWeightO[which.ordinal()], poseWeight[which.ordinal()]);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            int now = mode().ordinal();
            for (int i = 0; i < poseWeight.length; i++) {
                poseWeightO[i] = poseWeight[i];
                poseWeight[i] += ((i == now ? 1.0f : 0.0f) - poseWeight[i]) * 0.18f;
            }
        }
    }

    public void setHungry(boolean hungry) {
        entityData.set(HUNGRY, hungry);
        hungerIn = hungry ? 0 : HUNGER_MIN_TICKS + random.nextInt(HUNGER_MAX_TICKS - HUNGER_MIN_TICKS);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_MIMIC) {
            mimicStart = tickCount;
        } else {
            super.handleEntityEvent(id);
        }
    }

    // ------------------------------------------------------------------ the brain

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        modeTicks++;
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (rechargeIn > 0) {
            rechargeIn--;
        }
        if (!isHungry() && --hungerIn <= 0) {
            setHungry(true);
        }
        boolean senseNow = (tickCount + getId()) % SENSE_INTERVAL == 0;
        switch (mode) {
            case WANDER -> tickWander(senseNow);
            case STALK -> tickStalk(senseNow);
            case FROZEN -> tickFrozen(senseNow);
            case CHARGE -> tickCharge();
            case RETREAT -> tickRetreat(senseNow);
        }
    }

    /** Docile: slow strolls, long pauses facing the wall. Hungry ones look for someone alone. */
    private void tickWander(boolean senseNow) {
        if (senseNow) {
            if (isHungry()) {
                Player lone = findLonePlayer();
                if (lone != null) {
                    startStalk(lone);
                    return;
                }
            }
            if (tickLonely()) {
                return;
            }
        }
        if (getNavigation().isDone() && random.nextInt(160) == 0) {
            Vec3 to = DefaultRandomPos.getPos(this, 12, 3);
            if (to != null) {
                getNavigation().moveTo(to.x, to.y, to.z, WANDER_SPEED);
            }
        }
    }

    private void startStalk(Player target) {
        prey = target;
        setTarget(null);
        stalkBudget = STALK_MIN_TICKS + random.nextInt(STALK_MAX_TICKS - STALK_MIN_TICKS);
        mimicIn = MIMIC_MIN_TICKS / 2 + random.nextInt(MIMIC_MIN_TICKS);
        setMode(Mode.STALK);
    }

    /**
     * Following at a distance, out of sight. Keeps {@link #KEEP_MIN}-{@link #KEEP_MAX} blocks
     * back, calls out when unseen, and charges when the moment is right.
     */
    private void tickStalk(boolean senseNow) {
        if (!isHuntable(prey) || !isAlone(prey) || distanceToSqr(prey) > HUNT_RANGE * HUNT_RANGE * 1.5) {
            loseInterest();
            return;
        }
        double distance = distanceTo(prey);
        boolean watched = isWatchedBy(prey);
        // modeTicks < 0 right after it slunk off from a stare: don't freeze again straight away.
        if (senseNow && watched && modeTicks >= 0) {
            if (distance <= CHARGE_RANGE) {
                startCharge(); // you walked right up to it
                return;
            }
            getNavigation().stop();
            setMode(Mode.FROZEN);
            return;
        }
        boolean ready = modeTicks > stalkBudget && rechargeIn <= 0;
        if ((distance < CHARGE_RANGE && !watched && rechargeIn <= 0) || (ready && distance < KEEP_MAX)) {
            startCharge();
            return;
        }
        // Keep its distance: closer if it is too far, hold still if it is in the band.
        if (senseNow) {
            if (distance > KEEP_MAX) {
                getNavigation().moveTo(prey, STALK_SPEED);
            } else if (distance < KEEP_MIN) {
                getNavigation().stop();
            } else if (ready) {
                getNavigation().moveTo(prey, STALK_SPEED); // its patience is up: close in
            } else if (!getNavigation().isDone() && random.nextInt(4) == 0) {
                getNavigation().stop();
            }
        }
        getLookControl().setLookAt(prey, 10.0f, 10.0f);
        if (--mimicIn <= 0) {
            mimicIn = MIMIC_MIN_TICKS + random.nextInt(MIMIC_MAX_TICKS - MIMIC_MIN_TICKS);
            if (!watched && !prey.hasLineOfSight(this)) {
                mimic(prey);
            }
        }
    }

    /**
     * Caught in your gaze, it stops dead and stares back. Keep looking and after a few seconds it
     * slinks off out of sight; walk up to it and it charges.
     */
    private void tickFrozen(boolean senseNow) {
        if (!isHuntable(prey)) {
            loseInterest();
            return;
        }
        getNavigation().stop();
        getLookControl().setLookAt(prey, 30.0f, 30.0f);
        if (distanceTo(prey) < CHARGE_RANGE) {
            startCharge();
            return;
        }
        if (senseNow && !isWatchedBy(prey)) {
            setMode(Mode.STALK);
            return;
        }
        if (modeTicks >= FROZEN_MAX_TICKS) {
            // Slink back out of sight, then keep stalking.
            Vec3 away = DefaultRandomPos.getPosAway(this, 14, 4, prey.position());
            if (away != null) {
                getNavigation().moveTo(away.x, away.y, away.z, STALK_SPEED * 1.2);
            }
            setMode(Mode.STALK);
            modeTicks = -60; // don't freeze again straight away
        }
    }

    private void startCharge() {
        setTarget(prey);
        unseenTicks = 0;
        setMode(Mode.CHARGE);
        playSound(Level39Mobs.SKIN_STEALER_HUNT.get(), 2.0f, 0.9f + random.nextFloat() * 0.15f);
    }

    /** Runs at you and tears at you with its hands. It is slower than your sprint. */
    private void tickCharge() {
        Player victim = prey;
        if (victim != null && victim.isDeadOrDying()) {
            feed(FEED_DAMAGE);
            return;
        }
        if (!isHuntable(victim) || distanceToSqr(victim) > CHARGE_GIVE_UP * CHARGE_GIVE_UP) {
            backToStalking();
            return;
        }
        if (getSensing().hasLineOfSight(victim)) {
            unseenTicks = 0;
        } else if (++unseenTicks > CHARGE_LOST_TICKS) {
            backToStalking();
            return;
        }
        getLookControl().setLookAt(victim, 30.0f, 30.0f);
        if (distanceToSqr(victim) < 2.5 * 2.5) {
            getNavigation().stop();
            getMoveControl().setWantedPosition(victim.getX(), victim.getY(), victim.getZ(), CHARGE_SPEED);
        } else if (modeTicks % 10 == 1 || getNavigation().isDone()) {
            getNavigation().moveTo(victim, CHARGE_SPEED);
        }
        if (attackCooldown <= 0 && isWithinMeleeAttackRange(victim)) {
            attackCooldown = ATTACK_COOLDOWN;
            swing(InteractionHand.MAIN_HAND);
            if (doHurtTarget(victim)) {
                playSound(Level39Mobs.SKIN_STEALER_TEAR.get(), 1.4f, 0.9f + random.nextFloat() * 0.2f);
                feed((float) getAttributeValue(Attributes.ATTACK_DAMAGE));
            }
        }
    }

    /** Counts what it has torn off; once it has had enough it is no longer hungry and wanders off. */
    private void feed(float amount) {
        fed += amount;
        if (fed >= FEED_DAMAGE) {
            fed = 0.0f;
            setHungry(false);
            prey = null;
            setTarget(null);
            getNavigation().stop();
            setMode(Mode.WANDER);
        }
    }

    private void backToStalking() {
        setTarget(null);
        getNavigation().stop();
        rechargeIn = RECHARGE_TICKS;
        if (isHungry() && isHuntable(prey)) {
            stalkBudget = 0; // it has already waited; it only needs the recharge
            setMode(Mode.STALK);
        } else {
            loseInterest();
        }
    }

    private void loseInterest() {
        prey = null;
        setTarget(null);
        getNavigation().stop();
        setMode(Mode.WANDER);
    }

    /** Badly hurt: runs from everyone and vanishes once nobody can see it. */
    private void tickRetreat(boolean senseNow) {
        Player near = level().getNearestPlayer(this, 24.0);
        if (near != null && (getNavigation().isDone() || modeTicks % 40 == 1)) {
            Vec3 away = DefaultRandomPos.getPosAway(this, 16, 4, near.position());
            if (away != null) {
                getNavigation().moveTo(away.x, away.y, away.z, RETREAT_SPEED);
            }
        }
        if (senseNow && modeTicks > 60 && !anyPlayerCanSee()) {
            discard();
        }
    }

    private void setMode(Mode next) {
        mode = next;
        modeTicks = 0;
        entityData.set(MODE, (byte) next.ordinal());
    }

    private boolean tickLonely() {
        Player near = level().getNearestPlayer(getX(), getY(), getZ(), LONELY_RANGE, EntitySelector.NO_SPECTATORS);
        lonelyTicks = near == null ? lonelyTicks + SENSE_INTERVAL : 0;
        if (lonelyTicks >= LONELY_DESPAWN_TICKS) {
            discard();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ the mimicry

    /**
     * Calls out from the dark: one of its synthesized "voice" fragments, heard from far down the
     * tunnel. Half the time the player also reads the words, garbled: their own last words from
     * chat if they said anything here, otherwise something a lost person would say.
     */
    private void mimic(Player target) {
        level().broadcastEntityEvent(this, EVENT_MIMIC);
        playSound(Level39Mobs.SKIN_STEALER_MIMIC.get(), 2.6f, 0.92f + random.nextFloat() * 0.12f);
        if (target instanceof ServerPlayer player && random.nextBoolean()) {
            String words = LAST_WORDS.getOrDefault(player.getUUID(), PHRASES[random.nextInt(PHRASES.length)]);
            player.sendSystemMessage(Component.translatable("chat.backrooms.skin_stealer.echo", garble(words, random))
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    /**
     * Mangles a sentence the way a bad copy of a voice would: words stutter ("h-help"), trail off
     * ("the..."), repeat, or drop out. Never more than seven words.
     */
    static String garble(String text, RandomSource random) {
        String[] words = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z' ?]", "").trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        int used = 0;
        for (String word : words) {
            if (word.isEmpty() || used >= 7) {
                continue;
            }
            if (used > 0 && random.nextInt(6) == 0) {
                continue; // a word lost in the echo
            }
            if (out.length() > 0) {
                out.append(random.nextInt(3) == 0 ? "... " : " ");
            }
            if (word.length() > 2 && random.nextInt(3) == 0) {
                out.append(word, 0, 1).append('-'); // stutter on the first letter
            }
            out.append(word);
            if (random.nextInt(7) == 0) {
                out.append(' ').append(word); // repeated
            }
            used++;
        }
        if (out.length() == 0) {
            out.append("...");
        }
        return out.append("...").toString();
    }

    /** Remembers what players say on Level 39, so a Skin-stealer can throw it back at them. */
    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (BackroomsLevel.fromDimension(player.level().dimension()) == BackroomsLevel.LEVEL_39) {
            String raw = event.getRawText();
            LAST_WORDS.put(player.getUUID(), raw.length() > 60 ? raw.substring(0, 60) : raw);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_WORDS.remove(event.getEntity().getUUID());
    }

    // ------------------------------------------------------------------ senses

    @Nullable
    private Player findLonePlayer() {
        Player best = null;
        double bestDistance = HUNT_RANGE * HUNT_RANGE;
        for (Player player : level().players()) {
            if (!isHuntable(player) || !isAlone(player)) {
                continue;
            }
            double distance = distanceToSqr(player);
            if (distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Nobody else (no other player in survival or adventure) within {@link #LONE_RADIUS} blocks. */
    private boolean isAlone(Player player) {
        for (Player other : level().players()) {
            if (other != player && !other.isSpectator() && other.distanceToSqr(player) < LONE_RADIUS * LONE_RADIUS) {
                return false;
            }
        }
        return true;
    }

    /** True if this player is looking right at it, with nothing in between. */
    public boolean isWatchedBy(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 to = new Vec3(getX(), getY() + getBbHeight() * 0.7, getZ()).subtract(eye);
        double distance = to.length();
        if (distance > WATCH_RANGE || distance < 1.0e-3) {
            return false;
        }
        return player.getViewVector(1.0f).dot(to.scale(1.0 / distance)) > WATCH_COS && player.hasLineOfSight(this);
    }

    private boolean isHuntable(@Nullable Player player) {
        return player != null && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && !player.isCreative() && player.level() == level() && !SafeRooms.isSafe(player);
    }

    private boolean anyPlayerCanSee() {
        for (Player player : level().players()) {
            if (!player.isSpectator() && player.distanceToSqr(this) < 64.0 * 64.0 && player.hasLineOfSight(this)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ vanilla hooks

    /**
     * Hurting it: a docile one turns on whoever did it (hungry or not), and a badly hurt one runs.
     * Its blood is thin and almost clear.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (!hurt || level().isClientSide || !isAlive()) {
            return hurt;
        }
        if (level() instanceof ServerLevel server) {
            server.sendParticles(new DustParticleOptions(new Vector3f(0.86f, 0.88f, 0.80f), 1.1f),
                    getX(), getY() + getBbHeight() * 0.6, getZ(), 10, 0.25, 0.4, 0.25, 0.0);
            server.sendParticles(ParticleTypes.FALLING_WATER, getX(), getY() + getBbHeight() * 0.55, getZ(), 6, 0.2, 0.3, 0.2, 0.0);
        }
        if (getHealth() < getMaxHealth() * RETREAT_HEALTH) {
            prey = null;
            setTarget(null);
            setMode(Mode.RETREAT);
            return true;
        }
        if (source.getEntity() instanceof Player attacker && isHuntable(attacker) && mode != Mode.CHARGE) {
            prey = attacker;
            rechargeIn = 0;
            startCharge();
        }
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 220;
    }

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return mode == Mode.STALK || mode == Mode.FROZEN ? null : Level39Mobs.SKIN_STEALER_BREATH.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return Level39Mobs.SKIN_STEALER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Level39Mobs.SKIN_STEALER_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // Heavy bare footfalls, softer while it stalks.
        playSound(Level39Mobs.SKIN_STEALER_STEP.get(), mode == Mode.STALK ? 0.35f : 0.8f, 0.85f + random.nextFloat() * 0.2f);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }
}
