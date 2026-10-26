package com.rickr.backrooms.client.trailer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.client.camera.HandheldCamera;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A tool for making trailers: flies the camera along a smooth path and records every frame
 * straight into a video file. Players never see it: it does nothing unless the game is started
 * with {@code -Dbackrooms.trailerDir=<folder>} (and {@code -Dbackrooms.ffmpeg=<path to ffmpeg>}).
 *
 * <p>How a shot is recorded: something outside the game (the trailer script) puts the player in
 * spectator mode where the shot happens and writes {@code shot.json} into the folder. Within a
 * tick this class picks it up, hides the HUD, holds the camera on the first keyframe for a moment
 * (so the picture settles), then moves it along the keyframes and feeds each frame to ffmpeg. When
 * the path ends it writes {@code <name>.done}.
 *
 * <p>The clock is the video's, not the real world's: every rendered frame moves the camera exactly
 * 1/fps seconds along the path, so the motion is perfectly smooth even if the game stutters. The
 * frame rate is capped to the video's for the shot, so the world (lights, water, monsters) moves at
 * about its normal speed too.
 *
 * <p>shot.json: {@code name}, {@code out} (video path), {@code fps} (30), {@code pre} (seconds held
 * before recording, 1.5), {@code ease} (true: start and stop gently), {@code fov} (optional,
 * degrees), {@code keys}: [[seconds, x, eyeY, z, yaw, pitch], ...] with yaw unwrapped (keep it
 * continuous, e.g. 170 then 190, not 170 then -170).
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class TrailerRecorder {
    private static final Logger LOGGER = LoggerFactory.getLogger("backrooms/trailer");
    @Nullable
    private static final String DIR = System.getProperty("backrooms.trailerDir");
    private static final String FFMPEG = System.getProperty("backrooms.ffmpeg", "ffmpeg");

    /** One keyframe: time (s), eye position, view angles. */
    private record Key(double t, double x, double y, double z, double yaw, double pitch) {}

    @Nullable
    private static Shot shot;

    private static final class Shot {
        String name;
        Path out;
        int fps;
        int preFrames;
        boolean ease;
        double fov;
        List<Key> keys;
        int frame;
        Process ffmpeg;
        OutputStream video;
        int width;
        int height;
        boolean hideGuiBefore;
        int framerateBefore;
        boolean vsyncBefore;
    }

    private TrailerRecorder() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (DIR == null || shot != null) {
            return;
        }
        Path file = Path.of(DIR, "shot.json");
        if (!Files.exists(file) || Minecraft.getInstance().player == null) {
            return;
        }
        try {
            Path taken = Path.of(DIR, "shot.taken.json");
            Files.move(file, taken, StandardCopyOption.REPLACE_EXISTING);
            start(JsonParser.parseString(Files.readString(taken)).getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Trailer: could not start shot", e);
            shot = null;
        }
    }

    private static void start(JsonObject json) throws IOException {
        Minecraft mc = Minecraft.getInstance();
        Shot s = new Shot();
        s.name = json.get("name").getAsString();
        s.out = Path.of(json.get("out").getAsString());
        s.fps = json.has("fps") ? json.get("fps").getAsInt() : 30;
        s.preFrames = (int) Math.round((json.has("pre") ? json.get("pre").getAsDouble() : 1.5) * s.fps);
        s.ease = !json.has("ease") || json.get("ease").getAsBoolean();
        s.fov = json.has("fov") ? json.get("fov").getAsDouble() : 0.0;
        s.keys = new ArrayList<>();
        JsonArray keys = json.getAsJsonArray("keys");
        for (JsonElement k : keys) {
            JsonArray a = k.getAsJsonArray();
            s.keys.add(new Key(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(),
                    a.get(3).getAsDouble(), a.get(4).getAsDouble(), a.get(5).getAsDouble()));
        }
        s.width = mc.getMainRenderTarget().width;
        s.height = mc.getMainRenderTarget().height;
        Files.createDirectories(s.out.getParent());
        s.ffmpeg = new ProcessBuilder(FFMPEG, "-y", "-loglevel", "error",
                "-f", "rawvideo", "-pix_fmt", "rgba", "-s", s.width + "x" + s.height, "-r", Integer.toString(s.fps),
                "-i", "-", "-c:v", "libx264", "-preset", "medium", "-crf", "14", "-pix_fmt", "yuv420p", s.out.toString())
                .redirectErrorStream(true)
                .redirectOutput(Path.of(DIR, s.name + ".ffmpeg.log").toFile())
                .start();
        s.video = new BufferedOutputStream(s.ffmpeg.getOutputStream(), 1 << 22);
        s.hideGuiBefore = mc.options.hideGui;
        s.framerateBefore = mc.options.framerateLimit().get();
        s.vsyncBefore = mc.options.enableVsync().get();
        mc.options.hideGui = true;
        mc.options.framerateLimit().set(s.fps);
        mc.options.enableVsync().set(false);
        shot = s;
        LOGGER.info("Trailer: recording {} ({}x{} @ {} fps, {} keys)", s.name, s.width, s.height, s.fps, s.keys.size());
    }

    /** Before the frame is drawn: put the camera where the path says it is right now. */
    @SubscribeEvent
    public static void onRenderFramePre(RenderFrameEvent.Pre event) {
        Shot s = shot;
        LocalPlayer player = Minecraft.getInstance().player;
        if (s == null || player == null) {
            return;
        }
        double t = Math.max(0, s.frame - s.preFrames) / (double) s.fps;
        double[] p = sample(s, t);
        double feetY = p[1] - player.getEyeHeight();
        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(p[0], feetY, p[2]);
        player.xo = p[0];
        player.yo = feetY;
        player.zo = p[2];
        player.xOld = p[0];
        player.yOld = feetY;
        player.zOld = p[2];
        player.setYRot((float) p[3]);
        player.yRotO = (float) p[3];
        player.setXRot((float) p[4]);
        player.xRotO = (float) p[4];
        player.setYHeadRot((float) p[3]);
        player.yHeadRotO = (float) p[3];
    }

    /** After the frame is drawn: hand it to ffmpeg, step the clock, stop at the end of the path. */
    @SubscribeEvent
    public static void onRenderFramePost(RenderFrameEvent.Post event) {
        Shot s = shot;
        if (s == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        try {
            if (s.frame >= s.preFrames) {
                if (mc.getMainRenderTarget().width != s.width || mc.getMainRenderTarget().height != s.height) {
                    throw new IOException("window size changed during the shot");
                }
                try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    int[] pixels = image.getPixelsRGBA();
                    ByteBuffer bytes = ByteBuffer.allocate(pixels.length * 4).order(ByteOrder.LITTLE_ENDIAN);
                    bytes.asIntBuffer().put(pixels);
                    s.video.write(bytes.array());
                }
            }
            s.frame++;
            double duration = s.keys.get(s.keys.size() - 1).t();
            if ((s.frame - s.preFrames) / (double) s.fps > duration) {
                finish(s, null);
            }
        } catch (IOException | RuntimeException e) {
            finish(s, e);
        }
    }

    private static void finish(Shot s, @Nullable Exception error) {
        shot = null;
        Minecraft mc = Minecraft.getInstance();
        mc.options.hideGui = s.hideGuiBefore;
        mc.options.framerateLimit().set(s.framerateBefore);
        mc.options.enableVsync().set(s.vsyncBefore);
        int frames = Math.max(0, s.frame - s.preFrames);
        try {
            s.video.close();
            s.ffmpeg.waitFor();
            Files.writeString(Path.of(DIR, s.name + ".done"),
                    (error == null ? "ok " : "error " + error) + " frames=" + frames, StandardCharsets.UTF_8);
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Trailer: could not finish {}", s.name, e);
        }
        if (error != null) {
            LOGGER.error("Trailer: shot {} failed", s.name, error);
        } else {
            LOGGER.info("Trailer: finished {} ({} frames)", s.name, frames);
        }
    }

    /** A cinematic field of view for the shot, if it asks for one (after the camcorder's own widening). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        Shot s = shot;
        if (s != null && s.fov > 0 && event.usedConfiguredFov()) {
            event.setFOV(s.fov);
            HandheldCamera.setFov(s.fov);
        }
    }

    /**
     * Position and angles at time t: a Catmull-Rom spline through the keyframes (it passes through
     * every key and curves smoothly between them), optionally eased so the shot starts and stops
     * gently instead of at full speed.
     */
    private static double[] sample(Shot s, double t) {
        List<Key> k = s.keys;
        double end = k.get(k.size() - 1).t();
        if (s.ease && end > 0) {
            double u = Mth.clamp(t / end, 0, 1);
            t = end * (u * u * (3 - 2 * u)) * 0.5 + t * 0.5; // half eased, half linear: gentle, not sluggish
        }
        t = Mth.clamp(t, k.get(0).t(), end);
        int i = 0;
        while (i < k.size() - 2 && t > k.get(i + 1).t()) {
            i++;
        }
        Key k1 = k.get(i);
        Key k2 = k.get(Math.min(i + 1, k.size() - 1));
        Key k0 = k.get(Math.max(i - 1, 0));
        Key k3 = k.get(Math.min(i + 2, k.size() - 1));
        double span = k2.t() - k1.t();
        double u = span > 0 ? (t - k1.t()) / span : 0;
        return new double[] {
                cr(k0.x(), k1.x(), k2.x(), k3.x(), u), cr(k0.y(), k1.y(), k2.y(), k3.y(), u),
                cr(k0.z(), k1.z(), k2.z(), k3.z(), u), cr(k0.yaw(), k1.yaw(), k2.yaw(), k3.yaw(), u),
                cr(k0.pitch(), k1.pitch(), k2.pitch(), k3.pitch(), u)};
    }

    private static double cr(double p0, double p1, double p2, double p3, double u) {
        double u2 = u * u;
        double u3 = u2 * u;
        return 0.5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3);
    }
}
