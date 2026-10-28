package com.rickr.backrooms.world.anomaly.level9;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModSounds;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.Travel;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import com.rickr.backrooms.world.anomaly.Teleports;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Level 9.1, "The Crimson Field" (http://backrooms-mghc.wikidot.com/level-9-1), on the server:
 * getting there, getting home, and the Gloom.
 *
 * <ul>
 *   <li><b>There:</b> follow a grass walkway to the middle of its clearing and you are in the
 *       field, on the same path, at the same spot (it is the suburb's walkway shifted
 *       {@link Level9Sites#FIELD_DX} east; the cold has already set in on the way).</li>
 *   <li><b>Home:</b> walk back along the path to where it starts (a scrap of sidewalk and a dead
 *       streetlamp) and you step out of the walkway onto the suburb's sidewalk again.</li>
 *   <li><b>The Gloom:</b> a red glow low on the horizon to the east. "Expeditions that try to
 *       reach it are dragged in." Walk more than {@link #GLOOM_REACH} blocks east of where you
 *       arrived and it takes you: you are pulled toward it for a few seconds while the screen
 *       goes red, then black, and you wake up back in Level 0. (Canon says the Gloom kills; here
 *       it throws you back to the start instead, like dying in the Backrooms usually does, so a
 *       mistake costs the whole way down but never the save.) The clients draw the red from the
 *       same rule ({@code client/anomaly/level9/CrimsonFieldClient}).</li>
 *   <li><b>Foxholes:</b> drop through a foxhole's hatch and you fall into Level 2 (canon: "rare
 *       foxhole doors lead to Levels 2 and 3 and work one way only"; this mod has no Level 3).</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class CrimsonField {
    /** How far east of your arrival point (blocks) the Gloom lets you get. */
    public static final double GLOOM_REACH = 200.0;
    /** How long it drags you before you are gone (ticks). */
    private static final int DRAG_TICKS = 70;
    /** Away from the field this long (ticks) and your arrival point is forgotten. */
    private static final long FORGET_AFTER = 200;

    private static final class State {
        double entryX;
        long lastTick;
        /** Game tick the Gloom took hold, or -1. */
        long dragStart = -1;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    /** Standing in a walkway's clearing in the suburb: across to the field. */
    static void cross(ServerPlayer player, AnomalySite walkway) {
        double x = Level9Sites.clearingX(walkway) + Level9Sites.FIELD_DX;
        double z = Level9Sites.clearingZ(walkway);
        teleport(player, x, z);
        State state = state(player);
        state.entryX = x;
        state.lastTick = player.serverLevel().getGameTime();
        state.dragStart = -1;
        player.connection.send(new ClientboundSoundPacket(ModSounds.L9_FROST_ONSET, SoundSource.AMBIENT,
                x, player.getEyeY(), z, 0.8f, 0.85f, player.getRandom().nextLong()));
        player.sendSystemMessage(Component.translatable("message.backrooms.level9.field").withStyle(s -> s.withItalic(true)));
    }

    /** At the start of a field walkway: home to the suburb end of the same walkway. */
    static void goHome(ServerPlayer player, AnomalySite fieldWalkway) {
        AnomalySite suburb = Level9Sites.toSuburb(fieldWalkway);
        // A step in from the sidewalk, on the path, still walking the way you were.
        teleport(player, suburb.anchorX(), Level9Sites.walkwayZAt(suburb, 1.5));
        STATES.remove(player.getUUID());
        player.sendSystemMessage(Component.translatable("message.backrooms.level9.home").withStyle(s -> s.withItalic(true)));
    }

    /** Fell through a foxhole hatch: one way, into Level 2. */
    static void dropThroughFoxhole(ServerPlayer player, AnomalySite foxhole) {
        STATES.remove(player.getUUID());
        int x = Math.floorMod(foxhole.anchorX(), 8192) - 4096;
        int z = Math.floorMod(foxhole.anchorZ(), 8192) - 4096;
        Travel.sendTo(player, BackroomsLevel.LEVEL_2, x, z,
                Component.translatable("message.backrooms.level9.foxhole").withStyle(s -> s.withItalic(true)));
    }

    /**
     * Every tick for a player anywhere in the field (called by all the field's site behaviours):
     * keeps track of where they arrived and lets the Gloom take them if they go too far east.
     */
    static void fieldTick(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        State state = state(player);
        if (now - state.lastTick > FORGET_AFTER) {
            // New visit without a walkway (a command, a respawn...): you "arrived" right here.
            state.entryX = player.getX();
            state.dragStart = -1;
        }
        state.lastTick = now;
        if (state.dragStart < 0) {
            if (player.getX() - state.entryX >= GLOOM_REACH) {
                state.dragStart = now;
                player.connection.send(new ClientboundSoundPacket(ModSounds.L9_GLOOM_DRAG, SoundSource.AMBIENT,
                        player.getX(), player.getEyeY(), player.getZ(), 1.0f, 1.0f, player.getRandom().nextLong()));
            }
            return;
        }
        long held = now - state.dragStart;
        if (held < DRAG_TICKS) {
            // Pulled east, faster and faster, legs useless.
            Vec3 motion = player.getDeltaMovement();
            double pull = 0.15 + 0.25 * held / DRAG_TICKS;
            player.setDeltaMovement(pull, motion.y, motion.z * 0.5);
            player.hurtMarked = true;
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 4, false, false, false));
            return;
        }
        STATES.remove(player.getUUID());
        int x = player.getRandom().nextInt(3001) - 1500;
        int z = player.getRandom().nextInt(3001) - 1500;
        Travel.sendTo(player, BackroomsLevel.LEVEL_0, x, z,
                Component.translatable("message.backrooms.level9.gloom").withStyle(s -> s.withItalic(true)));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
    }

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> {
            State s = new State();
            s.entryX = player.getX();
            s.lastTick = player.serverLevel().getGameTime();
            return s;
        });
    }

    /**
     * Same dimension, far away: a plain teleport keeping where you look. The client freezes you
     * until the new chunks have arrived, so you never fall through unloaded ground. Feet at 42.0:
     * the walkway's concrete on both ends.
     */
    private static void teleport(ServerPlayer player, double x, double z) {
        Teleports.to(player, x, 42.0, z, player.getYRot(), player.getXRot());
    }

    private CrimsonField() {}
}
