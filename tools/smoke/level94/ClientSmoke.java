package com.rickr.backrooms.client;

import com.mojang.logging.LogUtils;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.Travel;
import com.rickr.backrooms.world.layout.Level94Probe;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

/** THROWAWAY in-game tour of Level 94 with screenshots. Only runs if run/CLIENT_SMOKE exists. */
@EventBusSubscriber(modid = Backrooms.MOD_ID, value = Dist.CLIENT)
public final class ClientSmoke {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ENABLED = Files.exists(Path.of("CLIENT_SMOKE"));
    private static boolean created;
    private static int step, wait;
    private static final List<Consumer<MinecraftServer>> STEPS = new ArrayList<>();
    private static final List<Integer> WAITS = new ArrayList<>();
    private static final List<String> SHOTS = new ArrayList<>();
    private static UUID id;
    private static long seed;
    private static int[] town, house, tower;
    private static BlockPos throneLight, ballPit, studio, climbing;
    private static int dx, dz; // castle "inward" direction (gate -> throne)
    private static BlockPos darkVoid, clayDoor;

    private static void add(int waitAfter, String shot, Consumer<MinecraftServer> action) {
        STEPS.add(action);
        WAITS.add(waitAfter);
        SHOTS.add(shot);
    }

    static {
        add(200, null, s -> {
            ServerPlayer p = player(s);
            p.setGameMode(GameType.SURVIVAL);
            p.getAbilities().invulnerable = true;
            p.onUpdateAbilities();
            Travel.sendToQuietly(p, BackroomsLevel.LEVEL_94, 0, 0);
        });
        add(120, null, s -> cmd(s, "backrooms time94 day"));
        add(40, "01_arrival_n", s -> look(s, 180, 0));
        add(40, "02_arrival_e", s -> look(s, -90, 0));
        add(40, "03_arrival_s", s -> look(s, 0, 0));
        add(40, "04_arrival_w", s -> look(s, 90, 0));
        add(120, "05_hill_house", s -> {
            ServerPlayer p = player(s);
            seed = BackroomsSeeds.layoutSeed(p.serverLevel(), BackroomsLevel.LEVEL_94);
            town = Level94Probe.town(seed, 0, 0);
            LOG.info("CLIENTSMOKE town {} {} castle {} {} foot {} {}", town[0], town[1], town[2], town[3], town[4], town[5]);
            house = Level94Probe.thing(seed, p.getBlockX(), p.getBlockZ(), 0, true);
            if (house == null) house = Level94Probe.thing(seed, p.getBlockX() + 128, p.getBlockZ(), 0, true);
            tower = Level94Probe.thing(seed, p.getBlockX(), p.getBlockZ(), 2, false);
            if (tower == null) tower = Level94Probe.thing(seed, p.getBlockX() + 128, p.getBlockZ() + 128, 2, false);
            LOG.info("CLIENTSMOKE house {} tower {}", java.util.Arrays.toString(house), java.util.Arrays.toString(tower));
            Direction front = Direction.from2DDataValue(house[9]);
            Direction u = front.getClockWise();
            double doorX = house[4] + 0.5 + u.getStepX() * house[6] / 2.0;
            double doorZ = house[5] + 0.5 + u.getStepZ() * house[6] / 2.0;
            double ex = doorX + front.getStepX() * 16 + u.getStepX() * 6;
            double ez = doorZ + front.getStepZ() * 16 + u.getStepZ() * 6;
            tpLook(s, ex, ez, doorX, house[10] + 4, doorZ);
        });
        add(100, "06_house_inside", s -> {
            Direction front = Direction.from2DDataValue(house[9]);
            Direction u = front.getClockWise();
            Direction in = front.getOpposite();
            double x = house[4] + 0.5 + u.getStepX() * house[6] / 2.0 + in.getStepX() * 1.5;
            double z = house[5] + 0.5 + u.getStepZ() * house[6] / 2.0 + in.getStepZ() * 1.5;
            tp(s, x, house[10] + 1, z, in.toYRot(), 15);
        });
        add(40, "07_house_inside_turn", s -> look(s, Direction.from2DDataValue(house[9]).getOpposite().toYRot() + 60, 10));
        add(120, "08_water_tower", s -> {
            if (tower == null) return;
            tpLook(s, tower[0] + 18.5, tower[1] + 10.5, tower[0], tower[2] + 12, tower[1]);
        });
        add(160, "09_town_plaza", s -> tpLook(s, town[0] + 16.5, town[1] + 10.5, town[0], 44, town[1]));
        add(40, "10_town_street", s -> look(s, yawTo(town[0] + 16.5, town[1] + 10.5, town[0] + 40, town[1] - 10), 0));
        add(160, "11_castle_from_foot", s -> tpLook(s, town[4] + 0.5, town[5] + 0.5, town[2], 170, town[3]));
        add(200, "12_castle_aerial", s -> {
            ServerPlayer p = player(s);
            p.getAbilities().mayfly = true;
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            tpAt(s, town[2] + 150.5, 150, town[3] + 60.5, town[2], 165, town[3]);
        });
        add(120, "13_castle_ramp", s -> tpAt(s, town[2] + 80.5, 110, town[3] + 80.5, town[2], 120, town[3]));
        add(160, null, s -> {
            ServerPlayer p = player(s);
            p.getAbilities().flying = false;
            p.getAbilities().mayfly = false;
            p.onUpdateAbilities();
            tp(s, town[2] + 0.5, 152, town[3] + 0.5, 0, 0);
        });
        add(100, "14_entrance_hall", s -> {
            ServerLevel l = player(s).serverLevel();
            throneLight = find(l, ModBlocks.L94_THRONE_LIGHT.get(), 150, 175);
            ballPit = find(l, ModBlocks.L94_BALL_PIT.get(), 145, 155);
            studio = find(l, ModBlocks.L94_MINI_FOUNTAIN.get(), 150, 175);
            climbing = find(l, ModBlocks.L94_CLIMBING_FRAME.get(), 150, 175);
            LOG.info("CLIENTSMOKE throneLight {} ballPit {} studio {} climbing {}", throneLight, ballPit, studio, climbing);
            int ox = throneLight.getX() - town[2], oz = throneLight.getZ() - town[3];
            if (Math.abs(ox) > Math.abs(oz)) { dx = Integer.signum(ox); dz = 0; } else { dx = 0; dz = Integer.signum(oz); }
            double x = town[2] + 0.5 - dx * 15, z = town[3] + 0.5 - dz * 15;
            tp(s, x, 151, z, yawTo(x, z, town[2] + 0.5, town[3] + 0.5), 5);
        });
        add(100, "15_ball_pit", s -> {
            if (ballPit == null) return;
            double x = ballPit.getX() + 0.5 - dx * 3 + dz * 3, z = ballPit.getZ() + 0.5 - dz * 3 + dx * 3;
            tp(s, x, 151, z, yawTo(x, z, ballPit.getX() + 0.5, ballPit.getZ() + 0.5), 25);
        });
        add(100, "16_playground", s -> {
            if (climbing == null) return;
            double x = climbing.getX() + 0.5 + dx * 6, z = climbing.getZ() + 0.5 + dz * 6;
            tp(s, x, 151, z, yawTo(x, z, climbing.getX() + 0.5, climbing.getZ() + 0.5), -10);
        });
        add(100, "17_studio", s -> {
            if (studio == null) return;
            double x = studio.getX() + 0.5 + dx * 5, z = studio.getZ() + 0.5 + dz * 5;
            tp(s, x, studio.getY(), z, yawTo(x, z, studio.getX() + 0.5, studio.getZ() + 0.5), 30);
        });
        add(100, "18_throne_room", s -> {
            double x = town[2] + 0.5 + dx * 7, z = town[3] + 0.5 + dz * 7;
            tp(s, x, 151, z, yawTo(x, z, throneLight.getX() + 0.5, throneLight.getZ() + 0.5), -5);
        });
        add(300, null, s -> {
            tpLook(s, town[0] + 16.5, town[1] + 10.5, town[0], 44, town[1]);
            cmd(s, "backrooms time94 dusk");
        });
        add(40, "19_dusk_plaza", s -> {});
        add(200, null, s -> {
            cmd(s, "backrooms time94 night");
            tpLook(s, house[4] + 30.5, house[5] + 30.5, town[2], 170, town[3]);
        });
        add(400, "20_night_hills", s -> LOG.info("CLIENTSMOKE smilers near: {}", player(s).serverLevel().getEntities(
                com.rickr.backrooms.registry.ModEntities.SMILER.get(), e -> e.distanceTo(player(s)) < 64).size()));
        add(40, "21_night_smiler", s -> {
            var smilers = player(s).serverLevel().getEntities(com.rickr.backrooms.registry.ModEntities.SMILER.get(), e -> e.distanceTo(player(s)) < 64);
            if (!smilers.isEmpty()) {
                var e = smilers.get(0);
                look(s, yawTo(player(s).getX(), player(s).getZ(), e.getX(), e.getZ()), 0);
            }
        });
        add(60, "22_night_nightshot", s -> s.execute(() -> com.rickr.backrooms.camera.CameraService.toggle(player(s),
                com.rickr.backrooms.network.CameraTogglePayload.NIGHT_SHOT)));
        add(140, "23_dark_door", s -> {
            com.rickr.backrooms.camera.CameraService.toggle(player(s), com.rickr.backrooms.network.CameraTogglePayload.NIGHT_SHOT);
            cmd(s, "backrooms time94 day");
            ServerPlayer p = player(s);
            int[] door = BackroomsLevel.LEVEL_94.layout().exitPosition(seed, p.getBlockX(), p.getBlockZ());
            LOG.info("CLIENTSMOKE dark door at {} {}", door[0], door[1]);
            ServerLevel l = p.serverLevel();
            l.getChunk(door[0] >> 4, door[1] >> 4);
            int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, door[0], door[1]);
            BlockPos dpos = null;
            for (BlockPos q : BlockPos.betweenClosed(door[0] - 3, y - 4, door[1] - 3, door[0] + 3, y + 4, door[1] + 3)) {
                if (l.getBlockState(q).is(ModBlocks.L94_HOUSE_DOOR.get())) { dpos = q.immutable(); break; }
            }
            LOG.info("CLIENTSMOKE dark door block {}", dpos);
            if (dpos != null) {
                BlockState st = l.getBlockState(dpos);
                Direction f = st.getValue(DoorBlock.FACING);
                l.setBlock(dpos, st.setValue(DoorBlock.OPEN, true), 10);
                BlockPos lower = st.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? dpos : dpos.below();
                l.setBlock(lower, l.getBlockState(lower).setValue(DoorBlock.OPEN, true), 10);
                l.setBlock(lower.above(), l.getBlockState(lower.above()).setValue(DoorBlock.OPEN, true), 10);
                double x = lower.getX() + 0.5 + f.getStepX() * 4, z = lower.getZ() + 0.5 + f.getStepZ() * 4;
                tp(s, x, lower.getY(), z, yawTo(x, z, lower.getX() + 0.5, lower.getZ() + 0.5), 5);
                darkVoid = find(l, ModBlocks.L94_DARK_VOID.get(), lower.getY() - 1, lower.getY() + 3, lower.getX(), lower.getZ(), 6);
            }
        });
        add(100, null, s -> {
            LOG.info("CLIENTSMOKE dark void {}", darkVoid);
            if (darkVoid != null) tp(s, darkVoid.getX() + 0.5, darkVoid.getY(), darkVoid.getZ() + 0.5, 0, 0);
        });
        add(200, "24_after_dark_door", s -> LOG.info("CLIENTSMOKE after dark door: dim {}", player(s).level().dimension().location()));
        add(100, null, s -> {
            ServerPlayer p = player(s);
            ServerLevel l = p.serverLevel();
            BlockPos at = p.blockPosition().relative(p.getDirection(), 2);
            BlockState lowerSt = ModBlocks.L94_CLAY_DOOR.get().defaultBlockState().setValue(DoorBlock.OPEN, true)
                    .setValue(DoorBlock.FACING, p.getDirection().getOpposite());
            l.setBlock(at, lowerSt, 3);
            l.setBlock(at.above(), lowerSt.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
            clayDoor = at;
        });
        add(60, "25_clay_door", s -> {});
        add(10, null, s -> tp(s, clayDoor.getX() + 0.5, clayDoor.getY(), clayDoor.getZ() + 0.5, 0, 0));
        add(200, "26_after_clay_door", s -> LOG.info("CLIENTSMOKE after clay door: dim {} at {}", player(s).level().dimension().location(), player(s).blockPosition()));
        add(160, null, s -> {
            ServerPlayer p = player(s);
            if (p.level().dimension() != BackroomsLevel.LEVEL_94.dimension()) Travel.sendToQuietly(p, BackroomsLevel.LEVEL_94, 0, 0);
        });
        add(160, null, s -> tp(s, town[2] + 0.5, 152, town[3] + 0.5, 0, 0));
        add(10, null, s -> tp(s, throneLight.getX() + 0.5, throneLight.getY(), throneLight.getZ() + 0.5, 0, 0));
        add(200, "27_after_throne", s -> LOG.info("CLIENTSMOKE after throne light: dim {}", player(s).level().dimension().location()));
        add(10, null, s -> {
            LOG.info("CLIENTSMOKE done");
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
        });
    }


    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (!created && mc.getOverlay() == null && mc.screen != null) {
                created = true;
                mc.options.pauseOnLostFocus = false;
                LevelSettings settings = new LevelSettings("l94_test", GameType.SURVIVAL, false, Difficulty.NORMAL, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("l94_test", settings, WorldOptions.defaultWithRandomSeed(),
                        WorldPresets::createNormalWorldDimensions, mc.screen);
            }
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || server == null) return;
        if (id == null) {
            id = mc.player.getUUID();
            wait = 100;
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        // Each step: its action runs, then after its wait its screenshot (if any) is taken, then the next step.
        if (pendingShot != null) {
            LOG.info("CLIENTSMOKE shot {} at {} dim {}", pendingShot, mc.player.blockPosition(), mc.level.dimension().location());
            Screenshot.grab(mc.gameDirectory, "l94_" + pendingShot + ".png", mc.getMainRenderTarget(), c -> { });
            pendingShot = null;
        }
        if (step >= STEPS.size()) return;
        Consumer<MinecraftServer> action = STEPS.get(step);
        server.execute(() -> safe(() -> action.accept(server)));
        wait = WAITS.get(step);
        pendingShot = SHOTS.get(step);
        step++;
    }

    private static String pendingShot;

    private static void safe(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            LOG.error("CLIENTSMOKE step failed", t);
        }
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayer(id);
    }

    private static void cmd(MinecraftServer s, String command) {
        ServerPlayer p = player(s);
        s.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(4), command);
    }

    private static void look(MinecraftServer s, float yaw, float pitch) {
        ServerPlayer p = player(s);
        p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), yaw, pitch);
    }

    private static void tp(MinecraftServer s, double x, double y, double z, float yaw, float pitch) {
        ServerPlayer p = player(s);
        p.teleportTo(p.serverLevel(), x, y, z, yaw, pitch);
    }

    /** Stand on the ground at (x, z) and look at (tx, ty, tz). */
    private static void tpLook(MinecraftServer s, double x, double z, double tx, double ty, double tz) {
        ServerLevel l = player(s).serverLevel();
        l.getChunk(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4);
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        tpAt(s, x, y, z, tx, ty, tz);
    }

    private static void tpAt(MinecraftServer s, double x, double y, double z, double tx, double ty, double tz) {
        double h = Math.hypot(tx - x, tz - z);
        float pitch = (float) -Math.toDegrees(Math.atan2(ty - (y + 1.6), h));
        tp(s, x, y, z, yawTo(x, z, tx, tz), pitch);
    }

    private static float yawTo(double x, double z, double tx, double tz) {
        return (float) Math.toDegrees(Math.atan2(-(tx - x), tz - z));
    }

    private static BlockPos find(ServerLevel l, Block block, int y0, int y1) {
        return find(l, block, y0, y1, town[2], town[3], 24);
    }

    private static BlockPos find(ServerLevel l, Block block, int y0, int y1, int cx, int cz, int r) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos q : BlockPos.betweenClosed(cx - r, y0, cz - r, cx + r, y1, cz + r)) {
            if (l.getBlockState(q).is(block)) {
                double d = q.distToCenterSqr(cx, q.getY(), cz);
                if (d < bestD) {
                    bestD = d;
                    best = q.immutable();
                }
            }
        }
        return best;
    }

    private ClientSmoke() {}
}
