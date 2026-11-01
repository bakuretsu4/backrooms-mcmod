package com.rickr.backrooms.client;

import com.mojang.logging.LogUtils;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.layout.Level490Layout;
import com.rickr.backrooms.world.layout.Level490Spire;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

/**
 * Development harness (never committed; ~/.local/share/backrooms-dev/harness). Active only when
 * run/CLIENT_SMOKE exists: "play" opens a fresh survival world in Level 490 for a play session;
 * anything else runs the scripted Level 490 test below and closes the game.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class ClientSmoke {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Path FLAG = Path.of("CLIENT_SMOKE");
    private static final Path TAGFILE = Path.of("SEAMTAG");
    private static Boolean enabled;
    private static boolean play;
    private static String tag = "x";
    private static boolean started;
    private static boolean entered;
    private static int wait = -1;
    private static int step;
    private static String pendingShot;
    private static long seed;
    private static int cx, cz;
    private static long fpsAt;
    private static final List<Consumer<MinecraftServer>> STEPS = new ArrayList<>();
    private static final List<Integer> WAITS = new ArrayList<>();
    private static final List<String> SHOTS = new ArrayList<>();

    // The walker: follows the outside stair, sprinting, jumping holes and walls.
    private static boolean walking;
    private static int walkTicks;
    private static int walkMax;
    private static double walkUntil;
    private static double stuckX, stuckY, stuckZ;
    private static int stuckTicks;

    private ClientSmoke() {}

    private static void add(int waitAfter, String shot, Consumer<MinecraftServer> action) {
        STEPS.add(action);
        WAITS.add(waitAfter);
        SHOTS.add(shot);
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private static String where(ServerPlayer p) {
        BlockPos b = p.blockPosition().below();
        String n = p.level().getBlockState(b).getBlock().getDescriptionId();
        return String.format("%.1f %.1f %.1f on %s", p.getX(), p.getY(), p.getZ(), n.substring(n.lastIndexOf('.') + 1));
    }

    private static void tp(MinecraftServer s, double x, double y, double z, float yaw, float pitch) {
        ServerPlayer p = player(s);
        p.teleportTo(p.serverLevel(), x, y, z, yaw, pitch);
    }

    private static void tpAt(MinecraftServer s, double x, double y, double z, double tx, double ty, double tz) {
        double h = Math.hypot(tx - x, tz - z);
        float pitch = (float) -Math.toDegrees(Math.atan2(ty - (y + 1.62), h));
        float yaw = (float) Math.toDegrees(Math.atan2(-(tx - x), tz - z));
        tp(s, x, y, z, yaw, pitch);
    }

    private static void mode(MinecraftServer s, GameType type, boolean fly) {
        ServerPlayer p = player(s);
        p.setGameMode(type);
        p.getAbilities().invulnerable = true;
        p.getAbilities().flying = fly;
        p.onUpdateAbilities();
    }

    /** The creature's head as the client sees it {x, y, z} (reflection: test only). */
    private static double[] head() {
        try {
            java.lang.reflect.Field f = Level490Creature.class.getDeclaredField("HEAD");
            f.setAccessible(true);
            return ((double[]) f.get(null)).clone();
        } catch (ReflectiveOperationException e) {
            return new double[] {Double.NaN, Double.NaN, Double.NaN};
        }
    }

    private static int stage() {
        try {
            java.lang.reflect.Field f = Level490Creature.class.getDeclaredField("stage");
            f.setAccessible(true);
            return f.getInt(null);
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }

    /** The hand coming down, as the client knows it: {land tick}, or null (reflection: test only). */
    private static long[] reachLanding() {
        try {
            java.lang.reflect.Field f = Level490Creature.class.getDeclaredField("reach");
            f.setAccessible(true);
            Object r = f.get(null);
            if (r == null) {
                return null;
            }
            return new long[] {((com.rickr.backrooms.network.Level490ReachPayload) r).land()};
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static void lookAtCreature(MinecraftServer s) {
        ServerPlayer p = player(s);
        double[] h = head();
        LOG.info("CLIENTSMOKE creature stage {} head {} {} {} | player {} | gap {}", stage(), (int) h[0], (int) h[1], (int) h[2], where(p),
                String.format("%.1f", p.getY() - h[1]));
        tpAt(s, p.getX(), p.getY(), p.getZ(), h[0], h[1] - 4, h[2]);
    }

    private static void walk(double until, int max) {
        walking = true;
        walkTicks = 0;
        walkMax = max;
        walkUntil = until;
        stuckTicks = 0;
    }

    private static void buildSteps() {
        // ---- 1. The chains: from the top of the tower, which ones get a solid top, and how far off ----
        add(5, null, s -> mode(s, GameType.CREATIVE, true));
        add(5, null, s -> tp(s, cx + 0.5, Level490Spire.TOP + 2, cz + 0.5, 0, 0));
        add(200, null, s -> {});
        add(5, null, s -> Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            for (int[] c : Level490ClientState.chains(mc.player.getX(), mc.player.getZ())) {
                double d = Math.hypot(c[0] - mc.player.getX(), c[1] - mc.player.getZ());
                if (d < 400) {
                    LOG.info("CLIENTSMOKE chain at {} {} dist {} solid top {} section {}", c[0], c[1], (int) d,
                            Level490ChainTops.blocksShown(mc, c[0], c[1]),
                            mc.levelRenderer.isSectionCompiled(new BlockPos(c[0], 500, c[1])));
                }
            }
        }));
        // Look at the nearest chain past 170 blocks, from the top, as in the play-test screenshot.
        add(60, "10_far_chain", s -> {
            ServerPlayer p = player(s);
            int[] best = null;
            double bd = 0;
            for (int[] c : Level490ClientState.chains(p.getX(), p.getZ())) {
                double d = Math.hypot(c[0] - p.getX(), c[1] - p.getZ());
                if (d > 170 && d < 260 && (best == null || d < bd)) {
                    best = c;
                    bd = d;
                }
            }
            if (best != null) {
                tpAt(s, cx + 0.5, Level490Spire.TOP + 2, cz + 0.5, best[0], 540, best[1]);
            }
        });
        add(20, "11_up", s -> tp(s, cx + 0.5, Level490Spire.TOP + 30, cz + 0.5, player(s).getYRot(), -20));

        // ---- 2. The climb, as a player would: survival, sprinting, jumping the gaps ----
        add(5, null, s -> {
            Level490Spire sp = Level490Layout.spireAt(seed, cx, cz);
            double[] g = sp.flightPoint(0, 20, 0.15);
            tp(s, g[0], Math.floor(g[1]) + 1.05, g[2], 0, 0);
            player(s).resetFallDistance();
            mode(s, GameType.SURVIVAL, false);
        });
        add(40, null, s -> LOG.info("CLIENTSMOKE climb starts at {} ({} survival)", where(player(s)), player(s).isCreative() ? "not" : "in"));
        add(5, null, s -> walk(307 - 0.5, 1600));
        add(10, null, s -> LOG.info("CLIENTSMOKE flight 1 ended at {}", where(player(s))));
        add(30, "20_after_f1", s -> lookAtCreature(s));
        // Up flight 2 as a climber would, about 2.5 blocks a second, in hops along the stair's inner
        // lane (so no jump can fail); now and then a look at the creature.
        final double sweep2 = Level490Spire.flightSweep(1);
        for (int k = 0; k <= 60; k++) {
            final double sdeg = 20 + k * (sweep2 - 40) / 60.0;
            final int kk = k;
            add(k % 12 == 11 ? 2 : 20, k % 12 == 11 ? "30_look_" + (k / 12) : null, s -> {
                Level490Spire sp = Level490Layout.spireAt(seed, cx, cz);
                double[] g = sp.flightPoint(1, sdeg, 0.2);
                ServerPlayer p = player(s);
                if (kk == 0) {
                    mode(s, GameType.SURVIVAL, false);
                }
                p.resetFallDistance();
                tp(s, g[0], g[1] + 1.2, g[2], p.getYRot(), 0);
                if (kk % 12 == 11) {
                    lookAtCreature(s);
                }
            });
        }
        add(20, null, s -> LOG.info("CLIENTSMOKE glided up flight 2 to {}", where(player(s))));
        // Stand still 12 s near the top and watch it close in.
        for (int k = 0; k < 3; k++) {
            add(80, "31_still_" + k, s -> lookAtCreature(s));
        }
        add(10, null, s -> {
            LOG.info("CLIENTSMOKE done");
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
        });
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (enabled == null) {
            enabled = Files.exists(FLAG);
            if (enabled) {
                try {
                    play = Files.readString(FLAG).trim().equals("play");
                    if (Files.exists(TAGFILE)) {
                        tag = Files.readString(TAGFILE).trim();
                    }
                } catch (java.io.IOException e) {
                    play = false;
                }
            }
        }
        if (!enabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!started) {
            if (mc.level == null && mc.screen != null && mc.getOverlay() == null) {
                started = true;
                String name = play ? "penance_play" : "penance_test";
                LevelSettings settings = new LevelSettings(name, GameType.SURVIVAL, false, Difficulty.NORMAL, true, new GameRules(),
                        WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(name, settings, play ? WorldOptions.defaultWithRandomSeed() : new WorldOptions(77L, false, false),
                        WorldPresets::createNormalWorldDimensions, mc.screen);
            }
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || server == null) {
            return;
        }
        if (!entered) {
            entered = true;
            wait = 100;
            server.execute(() -> {
                ServerPlayer p = player(server);
                server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4), "backrooms enter @s level_490");
                LOG.info("CLIENTSMOKE sent the player to Level 490");
            });
            return;
        }
        if (play) {
            long now = System.currentTimeMillis();
            if (now - fpsAt > 10_000) {
                fpsAt = now;
                LOG.info("CLIENTSMOKE fps {} at {} dim {}", mc.getFps(), mc.player.blockPosition(), mc.level.dimension().location());
            }
            return;
        }
        if (walking) {
            steer(mc);
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (STEPS.isEmpty()) {
            // In Level 490 now: find the spire.
            ServerLevel l = server.getPlayerList().getPlayers().get(0).serverLevel();
            if (BackroomsLevel.fromDimension(l.dimension()) != BackroomsLevel.LEVEL_490) {
                wait = 20;
                return;
            }
            seed = BackroomsSeeds.layoutSeed(l, BackroomsLevel.LEVEL_490);
            Level490Spire sp = Level490Layout.spireAt(seed, mc.player.getBlockX(), mc.player.getBlockZ());
            cx = sp.centreX();
            cz = sp.centreZ();
            LOG.info("CLIENTSMOKE arrived at {} spire {} {}", mc.player.blockPosition(), cx, cz);
            buildSteps();
        }
        if (pendingShot != null) {
            LOG.info("CLIENTSMOKE shot {} at {}", pendingShot, mc.player.blockPosition());
            Screenshot.grab(mc.gameDirectory, "p490" + tag + "_" + pendingShot + ".png", mc.getMainRenderTarget(), c -> { });
            pendingShot = null;
        }
        if (step >= STEPS.size()) {
            return;
        }
        Consumer<MinecraftServer> action = STEPS.get(step);
        server.execute(() -> {
            try {
                action.accept(server);
            } catch (RuntimeException e) {
                LOG.error("CLIENTSMOKE step {} failed", step, e);
            }
        });
        wait = WAITS.get(step);
        pendingShot = SHOTS.get(step);
        step++;
    }

    private static void steer(Minecraft mc) {
        walkTicks++;
        var p = mc.player;
        Level490Spire sp = Level490Layout.spireAt(Level490ClientState.seed(), cx, cz);
        double[] g = sp.stairGuide(p.getX(), p.getY(), p.getZ(), 2.5);
        boolean done = p.getY() >= walkUntil || walkTicks >= walkMax || g == null;
        if (walkTicks % 100 == 0 || done) {
            double[] h = head();
            LOG.info("CLIENTSMOKE   walk t {} at {} | creature stage {} head y {} gap {}", walkTicks,
                    String.format("%.1f %.1f %.1f", p.getX(), p.getY(), p.getZ()), stage(), (int) h[1], String.format("%.1f", p.getY() - h[1]));
        }
        if (done && g == null) {
            BlockPos b = p.blockPosition().below();
            String n = mc.level.getBlockState(b).getBlock().getDescriptionId();
            LOG.info("CLIENTSMOKE   lost the stair on {} outside {} inside {}", n.substring(n.lastIndexOf('.') + 1),
                    String.format("%.3f", sp.outsideProgress(p.getX(), p.getY(), p.getZ())), sp.inside(p.getX(), p.getY(), p.getZ()));
        }
        if (done) {
            mc.options.keyUp.setDown(false);
            mc.options.keySprint.setDown(false);
            mc.options.keyJump.setDown(false);
            walking = false;
            LOG.info("CLIENTSMOKE   walker stopped at y {} after {} ticks ({})", String.format("%.1f", p.getY()), walkTicks,
                    g == null ? "lost the stair" : p.getY() >= walkUntil ? "reached" : "timed out");
            wait = 5;
            return;
        }
        // A hand coming down ahead: stop and let it land, as a player would.
        long[] r = reachLanding();
        if (r != null && mc.level.getGameTime() < r[0] + 10) {
            mc.options.keyUp.setDown(false);
            mc.options.keySprint.setDown(false);
            mc.options.keyJump.setDown(false);
            if (walkTicks % 20 == 0) {
                LOG.info("CLIENTSMOKE   waiting for a hand to land");
            }
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-(g[0] - p.getX()), g[2] - p.getZ()));
        p.setYRot(yaw);
        p.setXRot(10);
        mc.options.keyUp.setDown(true);
        boolean hole = false;
        for (double d = 0.5; d <= 0.9; d += 0.4) {
            double ax = p.getX() - Math.sin(Math.toRadians(yaw)) * d;
            double az = p.getZ() + Math.cos(Math.toRadians(yaw)) * d;
            BlockPos ahead = BlockPos.containing(ax, p.getY() - 0.2, az);
            hole |= mc.level.getBlockState(ahead).isAir() && mc.level.getBlockState(ahead.below()).isAir();
        }
        // Run at the gaps, as a player would; walk otherwise.
        boolean gapNear = false;
        for (double d = 0.5; d <= 3.5; d += 0.5) {
            BlockPos b = BlockPos.containing(p.getX() - Math.sin(Math.toRadians(yaw)) * d, p.getY() - 0.2, p.getZ() + Math.cos(Math.toRadians(yaw)) * d);
            gapNear |= mc.level.getBlockState(b).isAir() && mc.level.getBlockState(b.below()).isAir();
        }
        mc.options.keySprint.setDown(gapNear);
        mc.options.keyJump.setDown(p.onGround() && hole);
        if (p.onGround() && hole) {
            StringBuilder sb = new StringBuilder();
            for (double d = 0.5; d <= 4.0; d += 0.5) {
                BlockPos b = BlockPos.containing(p.getX() - Math.sin(Math.toRadians(yaw)) * d, p.getY() - 0.2, p.getZ() + Math.cos(Math.toRadians(yaw)) * d);
                boolean air = mc.level.getBlockState(b).isAir() && mc.level.getBlockState(b.below()).isAir();
                sb.append(air ? '_' : '#');
            }
            LOG.info("CLIENTSMOKE   jump at {} ahead {} sprint {}", String.format("%.1f %.1f %.1f", p.getX(), p.getY(), p.getZ()), sb, p.isSprinting());
        }
        if (Math.abs(p.getY() - stuckY) > 0.3 || Math.hypot(p.getX() - stuckX, p.getZ() - stuckZ) > 1.0) {
            stuckX = p.getX();
            stuckY = p.getY();
            stuckZ = p.getZ();
            stuckTicks = 0;
        } else if (++stuckTicks == 80) {
            LOG.info("CLIENTSMOKE   stuck at {} guide {}", String.format("%.1f %.1f %.1f", p.getX(), p.getY(), p.getZ()),
                    String.format("%.1f %.1f %.1f", g[0], g[1], g[2]));
        }
    }
}
