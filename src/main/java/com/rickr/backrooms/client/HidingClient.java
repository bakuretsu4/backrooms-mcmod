package com.rickr.backrooms.client;

import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.network.HideInputPayload;
import com.rickr.backrooms.network.HidingPayload;
import com.rickr.backrooms.player.HidingClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The client side of hiding in furniture (the rules live in {@code player/HidingService}).
 * <ul>
 *   <li>Your view sits inside the furniture ({@code mixin/client/CameraMixin} asks
 *       {@link #viewPosition}) and can only turn about 70 degrees left and right of straight out,
 *       35 up and down. Faces are only drawn from outside, so from inside you see out through it.</li>
 *   <li>Your body can't move; jump gets you out, holding sneak holds your breath.</li>
 *   <li>Nobody's hiding body is drawn (yours waits invisible in front of the furniture).</li>
 * </ul>
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class HidingClient {
    private static final float YAW_LIMIT = 70.0f;
    private static final float PITCH_LIMIT = 35.0f;

    private static boolean jumpWasDown;
    /** Sneak was let go since you got in (you sneak to get in; that press mustn't hold your breath). */
    private static boolean sneakReleased;
    /** What we last told the server about holding our breath. */
    private static boolean holdingBreath;

    /** Your own hiding entry, or null when you aren't hiding. */
    @Nullable
    private static HidingPayload.Entry mine(Minecraft mc) {
        return mc.player == null ? null : HidingClientState.get(mc.player.getId());
    }

    /** True while you are hiding. */
    public static boolean hiding() {
        return mine(Minecraft.getInstance()) != null;
    }

    /** True while you hide holding your breath (as the server last confirmed). */
    public static boolean holdingBreath() {
        HidingPayload.Entry entry = mine(Minecraft.getInstance());
        return entry != null && entry.breathHeld();
    }

    /** Where the view sits this frame when {@code entity} (the camera's entity) is you hiding; else null. */
    @Nullable
    public static Vec3 viewPosition(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (entity != mc.player) {
            return null;
        }
        HidingPayload.Entry entry = mine(mc);
        return entry == null ? null : entry.camera();
    }

    /** Runs after every other movement handler so nothing switches a key back on. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!HidingClientState.isHidden(event.getEntity().getId())) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0.0f;
        input.leftImpulse = 0.0f;
        input.up = input.down = input.left = input.right = false;
        input.jumping = false;
        // Sneak is read straight from the key below: if vanilla sent it, the body would crouch.
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        HidingPayload.Entry entry = mine(mc);
        if (player == null || entry == null) {
            jumpWasDown = false;
            sneakReleased = false;
            holdingBreath = false;
            return;
        }
        player.setDeltaMovement(Vec3.ZERO);
        boolean free = mc.screen == null; // keys typed into a chat or an inventory don't count
        boolean jump = free && mc.options.keyJump.isDown();
        if (jump && !jumpWasDown) {
            PacketDistributor.sendToServer(new HideInputPayload(HideInputPayload.LEAVE));
        }
        jumpWasDown = jump;

        boolean sneak = free && mc.options.keyShift.isDown();
        if (!sneak) {
            sneakReleased = true;
        }
        boolean hold = sneak && sneakReleased;
        if (hold != holdingBreath) {
            holdingBreath = hold;
            PacketDistributor.sendToServer(new HideInputPayload(hold ? HideInputPayload.HOLD_BREATH : HideInputPayload.BREATHE));
        }
    }

    /** No attacking, using or picking from inside (and no arm swing either). */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (hiding()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** Hiding players are not drawn at all (their armour and held item would show otherwise). */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (HidingClientState.isHidden(event.getEntity().getId())) {
            event.setCanceled(true);
        }
    }

    /** The block outline would frame the inside of the furniture: none while hiding. */
    @SubscribeEvent
    public static void onBlockHighlight(RenderHighlightEvent.Block event) {
        if (hiding()) {
            event.setCanceled(true);
        }
    }

    /**
     * Keeps the view looking out of the open side: the player's own turn is clamped (so the
     * server and the next frame agree), before the handheld-camera sway is added on top.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        HidingPayload.Entry entry = mine(mc);
        if (player == null || entry == null || event.getCamera().getEntity() != player) {
            return;
        }
        float base = entry.yaw();
        player.setYRot(clampAround(player.getYRot(), base, YAW_LIMIT));
        player.yRotO = clampAround(player.yRotO, base, YAW_LIMIT);
        player.setXRot(Mth.clamp(player.getXRot(), -PITCH_LIMIT, PITCH_LIMIT));
        player.xRotO = Mth.clamp(player.xRotO, -PITCH_LIMIT, PITCH_LIMIT);
        float partial = (float) event.getPartialTick();
        event.setYaw(Mth.lerp(partial, player.yRotO, player.getYRot()));
        event.setPitch(Mth.lerp(partial, player.xRotO, player.getXRot()));
    }

    /** {@code angle} limited to within {@code limit} degrees of {@code base}, without a jump of 360. */
    private static float clampAround(float angle, float base, float limit) {
        float off = Mth.wrapDegrees(angle - base);
        return angle - off + Mth.clamp(off, -limit, limit);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        HidingClientState.clear();
    }

    private HidingClient() {}
}
