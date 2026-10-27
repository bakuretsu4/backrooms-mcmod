package com.rickr.backrooms.player;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.camera.CameraService;
import com.rickr.backrooms.network.CameraTogglePayload;
import com.rickr.backrooms.network.HideInputPayload;
import com.rickr.backrooms.network.HidingPayload;
import com.rickr.backrooms.registry.HidingAudio;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Hiding in furniture: sneak + right-click a locker, wardrobe, desk or bed ({@link HidingSpots})
 * to get in, jump to get out.
 *
 * <p>The server decides everything. While you hide, your body waits invisible on the floor in
 * front of the furniture (never inside a block, so nothing can get stuck), frozen in place, and
 * only your view moves inside (client/HidingClient). Every client is told who is hiding
 * ({@link HidingPayload}) so nobody draws your body.
 *
 * <p><b>Monsters</b> ask {@link #isHiddenFrom} before they see, hear or chase anyone: one rule for
 * all of them. A hider is invisible to every monster except one that saw them get in (it had them
 * in sight within the last 2 seconds): that one keeps coming, and when it reaches the furniture it
 * drags them out. A monster that walks right past (within 3 blocks) can also hear them breathe,
 * unless they hold their breath. Monsters that take part implement {@link Seeker}.
 *
 * <p>Getting out is always clean: jumping, being pulled out or the furniture being opened by a
 * friend, the block breaking or unloading, logging out, dying, changing dimension or being
 * teleported all simply end the hiding where the body already stands.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class HidingService {
    /**
     * A monster that hunts players and so takes part in hiding: it can see someone get in, hear
     * them breathe, and drag them out.
     */
    public interface Seeker {
        /** False while this one isn't hunting anyone at all (a Faceling that is only a Stranger). */
        default boolean isSeeking() {
            return true;
        }

        /** False for a blind monster (the Clump hunts by ear and can't see anyone get in). */
        default boolean seesHiding() {
            return true;
        }

        /** Called right after this monster dragged {@code player} out of hiding: go for them. */
        void onPulledOut(ServerPlayer player);
    }

    /** After being pulled out you can't get back in for 3 seconds. */
    private static final int NO_REHIDE_TICKS = 60;
    /** A monster that had you in sight this recently when you got in saw you hide. */
    private static final int SAW_YOU_TICKS = 40;
    /** Only monsters this close can see you get in. */
    private static final double SAW_YOU_RANGE = 32.0;
    /** How often (ticks) we note which monsters can see each player who isn't hiding. */
    private static final int SIGHT_CHECK_TICKS = 5;
    /** A monster that saw you hide drags you out once it is this close (blocks from its body). */
    private static final double PULL_REACH = 1.5;
    /** Your breathing can be heard this far from your hiding place... */
    private static final double HEAR_RANGE = 3.0;
    /** ...by each monster that close, with this chance every second. */
    private static final float HEAR_CHANCE = 0.25f;
    /** Held breath runs out after 10 s (your air bar drains with it). */
    private static final int BREATH_TICKS = 200;
    /** Let go after holding it longer than 6 s and you gasp. */
    private static final int GASP_AFTER_TICKS = 120;
    /** A monster this close makes hiding nerve-racking. */
    private static final double DREAD_RANGE = 16.0;
    /** Sanity lost per second while hiding with a monster that close (a little). */
    private static final float DREAD_DRAIN = 0.2f;
    /** Further from the waiting spot than this, the player was teleported away: let them go. */
    private static final double TELEPORTED = 2.0;

    private enum Reason { LEFT, PULLED, GONE }

    /** One hiding player. */
    private static final class Hider {
        final int entityId;
        final ResourceKey<Level> dimension;
        final HidingSpots.Spot spot;
        /** Where the invisible body waits, and where they step out. */
        final Vec3 body;
        /** The monsters that saw them get in. */
        final Set<UUID> witnesses;
        boolean breathHeld;
        int breathTicks;
        /** The air bar while holding breath (a float so it drains smoothly, 1.5 a tick). */
        float air;
        /** Ran out of breath: sneak must be let go once before you can hold it again. */
        boolean mustBreathe;

        Hider(int entityId, ResourceKey<Level> dimension, HidingSpots.Spot spot, Vec3 body, Set<UUID> witnesses) {
            this.entityId = entityId;
            this.dimension = dimension;
            this.spot = spot;
            this.body = body;
            this.witnesses = witnesses;
        }
    }

    // Server-side state only (the server thread). Not saved: logging out ends the hiding anyway.
    private static final Map<UUID, Hider> HIDERS = new HashMap<>();
    /** Pulled-out players: the game time until which they can't hide again. */
    private static final Map<UUID, Long> NO_HIDE_UNTIL = new HashMap<>();
    /** For each player not hiding: which monsters had them in sight, and when (game time). */
    private static final Map<UUID, Map<UUID, Long>> SEEN_BY = new HashMap<>();

    // ------------------------------------------------------------------------------------------
    // The rule every monster uses
    // ------------------------------------------------------------------------------------------

    /** True for a player hiding in furniture; false for anything else. Works on both sides. */
    public static boolean isHidden(@Nullable Entity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        if (player.level().isClientSide()) {
            return HidingClientState.isHidden(player.getId());
        }
        return HIDERS.containsKey(player.getUUID());
    }

    /**
     * True if {@code looker} can't see, hear or target {@code entity} because it is hiding: every
     * monster but the ones that saw it get in. Monsters call this wherever they pick a player.
     */
    public static boolean isHiddenFrom(@Nullable Entity entity, @Nullable Entity looker) {
        if (entity == null || !isHidden(entity)) {
            return false;
        }
        if (looker == null || entity.level().isClientSide()) {
            return true;
        }
        Hider hider = HIDERS.get(entity.getUUID());
        return hider == null || !hider.witnesses.contains(looker.getUUID());
    }

    /** Spectators and creative players can't hide (and the dead can't either). */
    private static boolean mayHide(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative();
    }

    // ------------------------------------------------------------------------------------------
    // Getting in, and opening a spot someone is in
    // ------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (isHidden(player)) {
            deny(event); // nothing can be used from inside
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        HidingSpots.Spot spot = HidingSpots.find(level, pos, level.getBlockState(pos), player.position());
        if (spot == null) {
            return;
        }
        boolean wantsToHide = player.isShiftKeyDown() && mayHide(player);
        if (level.isClientSide()) {
            // The client only guesses: the server decides. Swallowing the click here keeps a
            // locker from opening its loot (or a held item from being used on it) at the same moment.
            if (wantsToHide || HidingClientState.occupied(spot.anchor().asLong())) {
                succeed(event);
            }
            return;
        }
        ServerPlayer opener = (ServerPlayer) player;
        ServerPlayer hider = occupant(opener.serverLevel(), spot.anchor());
        if (hider != null) {
            // Someone's in there: opening it drags them out (one player per spot).
            pullOut(hider, opener);
            succeed(event);
        } else if (wantsToHide) {
            tryHide(opener, spot);
            succeed(event);
        }
    }

    private static void succeed(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static void deny(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
    }

    @Nullable
    private static ServerPlayer occupant(ServerLevel level, BlockPos anchor) {
        for (Map.Entry<UUID, Hider> entry : HIDERS.entrySet()) {
            Hider hider = entry.getValue();
            if (hider.dimension == level.dimension() && hider.spot.anchor().equals(anchor)
                    && level.getPlayerByUUID(entry.getKey()) instanceof ServerPlayer player) {
                return player;
            }
        }
        return null;
    }

    private static void tryHide(ServerPlayer player, HidingSpots.Spot spot) {
        ServerLevel level = player.serverLevel();
        Long until = NO_HIDE_UNTIL.get(player.getUUID());
        if (until != null && level.getGameTime() < until) {
            return; // just dragged out: too shaken to get straight back in
        }
        if (HIDERS.containsKey(player.getUUID()) || player.isPassenger() || player.isSleeping()) {
            return;
        }
        Vec3 body = HidingSpots.standingPlace(level, spot, player);
        if (body == null) {
            player.displayClientMessage(Component.translatable("message.backrooms.hide.no_room"), true);
            return;
        }
        Hider hider = new Hider(player.getId(), level.dimension(), spot, body, whoSawYou(player));
        // A lit flashlight would give the hiding place away (and the beam would hang in the air).
        if (CameraService.get(player).flashlight()) {
            CameraService.toggle(player, CameraTogglePayload.FLASHLIGHT);
        }
        player.stopUsingItem();
        player.setSprinting(false);
        player.connection.teleport(body.x, body.y, body.z, spot.yaw(), 0.0f);
        player.setDeltaMovement(Vec3.ZERO);
        player.setInvisible(true);
        HIDERS.put(player.getUUID(), hider);
        SEEN_BY.remove(player.getUUID());
        playAt(level, spot, spot.kind().enterSound());
        broadcast();
    }

    /** The monsters that had this player in sight within the last 2 seconds (or right now). */
    private static Set<UUID> whoSawYou(ServerPlayer player) {
        Set<UUID> witnesses = new HashSet<>();
        Map<UUID, Long> seen = SEEN_BY.getOrDefault(player.getUUID(), Map.of());
        long now = player.serverLevel().getGameTime();
        for (Mob mob : seekersNear(player.serverLevel(), player.position(), SAW_YOU_RANGE)) {
            if (!((Seeker) mob).seesHiding()) {
                continue;
            }
            Long when = seen.get(mob.getUUID());
            if (when != null && now - when <= SAW_YOU_TICKS || mob.hasLineOfSight(player)) {
                witnesses.add(mob.getUUID());
            }
        }
        return witnesses;
    }

    // ------------------------------------------------------------------------------------------
    // Getting out
    // ------------------------------------------------------------------------------------------

    /** Keys from the hider's client: jump to get out, sneak to hold your breath. */
    public static void onInput(ServerPlayer player, int action) {
        Hider hider = HIDERS.get(player.getUUID());
        if (hider == null) {
            return;
        }
        switch (action) {
            case HideInputPayload.LEAVE -> release(player, Reason.LEFT);
            case HideInputPayload.HOLD_BREATH -> {
                if (!hider.breathHeld && !hider.mustBreathe) {
                    hider.breathHeld = true;
                    hider.breathTicks = 0;
                    hider.air = player.getAirSupply();
                    broadcast();
                }
            }
            case HideInputPayload.BREATHE -> {
                hider.mustBreathe = false;
                if (hider.breathHeld) {
                    letBreathGo(player, hider);
                    broadcast();
                }
            }
            default -> { }
        }
    }

    /**
     * Drags a hider out (a monster reached them, heard them, or a player opened the furniture).
     * They are pushed out in front of it and can't hide again for 3 seconds.
     */
    public static void pullOut(ServerPlayer player, @Nullable Entity by) {
        Hider hider = HIDERS.get(player.getUUID());
        if (hider == null) {
            return;
        }
        release(player, Reason.PULLED);
        Vec3 away = hider.body.subtract(Vec3.atBottomCenterOf(hider.spot.cell())).multiply(1.0, 0.0, 1.0);
        if (away.lengthSqr() > 1.0e-4) {
            player.setDeltaMovement(away.normalize().scale(0.4).add(0.0, 0.15, 0.0));
            player.hurtMarked = true; // sends the push to the player's client
        }
        NO_HIDE_UNTIL.put(player.getUUID(), player.serverLevel().getGameTime() + NO_REHIDE_TICKS);
        if (by instanceof Seeker seeker) {
            seeker.onPulledOut(player); // and now it attacks as it normally would
        }
    }

    private static void release(ServerPlayer player, Reason reason) {
        Hider hider = HIDERS.remove(player.getUUID());
        if (hider == null) {
            return;
        }
        if (hider.breathHeld) {
            letBreathGo(player, hider);
        }
        // Visible again (unless a real Invisibility potion is still running).
        player.setInvisible(player.hasEffect(MobEffects.INVISIBILITY));
        if (reason != Reason.GONE) {
            playAt(player.serverLevel(), hider.spot, hider.spot.kind().leaveSound());
        }
        broadcast();
    }

    // ------------------------------------------------------------------------------------------
    // While hidden
    // ------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Hider hider = HIDERS.get(player.getUUID());
        if (hider != null) {
            tickHider(player, hider);
        } else if ((player.tickCount + player.getId()) % SIGHT_CHECK_TICKS == 0 && mayHide(player)) {
            noteWhoSees(player);
        }
    }

    private static void tickHider(ServerPlayer player, Hider hider) {
        ServerLevel level = player.serverLevel();
        // Anything that makes hiding impossible ends it: the furniture broken, replaced or
        // unloaded, death, a dimension change, a game mode change, getting on something.
        if (!mayHide(player) || level.dimension() != hider.dimension || player.isPassenger() || player.isSleeping()
                || !level.isLoaded(hider.spot.anchor()) || !HidingSpots.stillThere(level, hider.spot)) {
            release(player, Reason.GONE);
            return;
        }
        double drift = player.position().distanceToSqr(hider.body);
        if (drift > TELEPORTED * TELEPORTED) {
            release(player, Reason.GONE); // teleported (/backrooms enter, an exit): let them go there
            return;
        }
        if (drift > 0.01) {
            // Bumped (a mob or a friend walked into the invisible body): put it back.
            player.connection.teleport(hider.body.x, hider.body.y, hider.body.z, player.getYRot(), player.getXRot());
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0f;
        // Vanilla resets this flag whenever potion effects change, so keep it set.
        player.setInvisible(true);

        tickBreath(player, hider);
        if (!HIDERS.containsKey(player.getUUID())) {
            return;
        }
        tickMonsters(player, hider);
    }

    private static void tickBreath(ServerPlayer player, Hider hider) {
        if (!hider.breathHeld) {
            return; // vanilla refills the air bar
        }
        hider.breathTicks++;
        hider.air = Math.max(0.0f, hider.air - player.getMaxAirSupply() / (float) BREATH_TICKS);
        if (!player.isEyeInFluid(FluidTags.WATER)) {
            player.setAirSupply(Math.round(hider.air)); // underwater, vanilla's own air counts down
        }
        if (hider.air <= 0.0f || hider.breathTicks >= BREATH_TICKS) {
            letBreathGo(player, hider);
            hider.mustBreathe = true;
            broadcast();
        }
    }

    /** Breathe again; after holding it long, with a gasp anyone nearby hears. */
    private static void letBreathGo(ServerPlayer player, Hider hider) {
        if (hider.breathTicks > GASP_AFTER_TICKS || hider.air <= 0.0f) {
            Vec3 at = hider.spot.camera();
            player.serverLevel().playSound(null, at.x, at.y, at.z, HidingAudio.GASP.get(), SoundSource.PLAYERS,
                    1.0f, 0.95f + player.getRandom().nextFloat() * 0.1f);
        }
        hider.breathHeld = false;
        hider.breathTicks = 0;
    }

    private static void tickMonsters(ServerPlayer player, Hider hider) {
        ServerLevel level = player.serverLevel();
        Vec3 torso = hider.body.add(0.0, 0.9, 0.0);
        Vec3 inside = hider.spot.camera();
        // A monster that saw you get in comes to the furniture and drags you out.
        for (UUID id : hider.witnesses) {
            if (level.getEntity(id) instanceof Mob mob && mob.isAlive()) {
                AABB reach = mob.getBoundingBox().inflate(PULL_REACH);
                if (reach.contains(torso) || reach.contains(inside)) {
                    pullOut(player, mob);
                    return;
                }
            }
        }
        if ((player.tickCount + player.getId()) % 20 != 0) {
            return;
        }
        List<Mob> near = seekersNear(level, inside, DREAD_RANGE);
        if (!near.isEmpty()) {
            SanityService.drain(player, DREAD_DRAIN);
        }
        if (hider.breathHeld) {
            return; // holding your breath, you make no sound at all
        }
        for (Mob mob : near) {
            if (mob.distanceToSqr(inside) <= HEAR_RANGE * HEAR_RANGE && player.getRandom().nextFloat() < HEAR_CHANCE) {
                pullOut(player, mob); // it heard you breathing
                return;
            }
        }
    }

    /** Remembers which monsters can see this (not hiding) player right now. */
    private static void noteWhoSees(ServerPlayer player) {
        List<Mob> seekers = seekersNear(player.serverLevel(), player.position(), SAW_YOU_RANGE);
        Map<UUID, Long> seen = SEEN_BY.get(player.getUUID());
        if (seekers.isEmpty() && seen == null) {
            return;
        }
        if (seen == null) {
            seen = new HashMap<>();
            SEEN_BY.put(player.getUUID(), seen);
        }
        long now = player.serverLevel().getGameTime();
        for (Mob mob : seekers) {
            if (((Seeker) mob).seesHiding() && mob.hasLineOfSight(player)) {
                seen.put(mob.getUUID(), now);
            }
        }
        // Forget old sightings so the map stays small.
        for (Iterator<Long> it = seen.values().iterator(); it.hasNext(); ) {
            if (now - it.next() > SAW_YOU_TICKS) {
                it.remove();
            }
        }
        if (seen.isEmpty()) {
            SEEN_BY.remove(player.getUUID());
        }
    }

    /** Living monsters that take part in hiding ({@link Seeker}) within {@code range} of a point. */
    private static List<Mob> seekersNear(ServerLevel level, Vec3 at, double range) {
        return level.getEntitiesOfClass(Mob.class, AABB.ofSize(at, range * 2.0, range * 2.0, range * 2.0),
                mob -> mob instanceof Seeker seeker && seeker.isSeeking() && mob.isAlive() && mob.distanceToSqr(at) <= range * range);
    }

    private static void playAt(ServerLevel level, HidingSpots.Spot spot, net.minecraft.sounds.SoundEvent sound) {
        Vec3 at = Vec3.atCenterOf(spot.cell());
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, 1.0f, 0.92f + level.getRandom().nextFloat() * 0.16f);
    }

    // ------------------------------------------------------------------------------------------
    // A hidden player can't do anything, and can't be hit through the furniture
    // ------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isHidden(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isHidden(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        // Neither can a hider hit anything, nor can anyone hit the invisible body waiting outside.
        if (isHidden(event.getEntity()) || isHidden(event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (interactWithHider(event.getEntity(), event.getTarget())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onInteractEntityAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (interactWithHider(event.getEntity(), event.getTarget())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /**
     * A hider can't touch anything. A friend who right-clicks the hider's invisible body (it waits
     * right in front of the furniture, so it is easy to click instead of the furniture) opens the
     * furniture and drags them out, the same as clicking the furniture itself.
     */
    private static boolean interactWithHider(Player player, Entity target) {
        if (isHidden(player)) {
            return true;
        }
        if (!isHidden(target)) {
            return false;
        }
        if (target instanceof ServerPlayer hider) {
            pullOut(hider, player);
        }
        return true;
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isHidden(player)) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof Player) {
            event.setCanceled(true);
        } else if (attacker instanceof Mob) {
            pullOut(player, attacker); // whatever got to you drags you out first
        }
    }

    // ------------------------------------------------------------------------------------------
    // Clean release on logout, death, dimension change; sync
    // ------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, snapshot());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // The body already stands in front of the furniture, so that's where they log back in.
            release(player, Reason.GONE);
            NO_HIDE_UNTIL.remove(player.getUUID());
            SEEN_BY.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            release(player, Reason.GONE);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            release(player, Reason.GONE);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        HIDERS.clear();
        NO_HIDE_UNTIL.clear();
        SEEN_BY.clear();
    }

    private static HidingPayload snapshot() {
        List<HidingPayload.Entry> entries = new ArrayList<>();
        for (Hider hider : HIDERS.values()) {
            entries.add(new HidingPayload.Entry(hider.entityId, hider.spot.anchor(), hider.spot.camera(),
                    hider.spot.yaw(), hider.breathHeld));
        }
        return new HidingPayload(entries);
    }

    /** Tells every client who is hiding now (rare: only when someone gets in, out, or holds their breath). */
    private static void broadcast() {
        PacketDistributor.sendToAllPlayers(snapshot());
    }

    private HidingService() {}
}
