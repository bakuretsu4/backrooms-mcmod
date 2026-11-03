package com.rickr.backrooms.escape;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.SystemReport;
import net.minecraft.Util;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.LoggerChunkProgressListener;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.debugchart.LocalSampleLogger;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * A throwaway in-process server with the mod's dimensions (every Backrooms level), for tests that
 * need real levels and players. The NeoForge test framework's EphemeralTestServerProvider builds
 * its world from the flat preset alone and never loads any level, so mod dimensions are missing
 * there; this is the same idea, but the world takes its dimensions from the data packs (as a new
 * world in the game does) and the levels are actually created.
 *
 * <p>One server per test JVM, started on first use, deleted when the JVM exits.
 */
final class BackroomsTestServer extends MinecraftServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicReference<MinecraftServer> RUNNING = new AtomicReference<>();
    private static final Services NO_SERVICES = new Services(null, com.mojang.authlib.yggdrasil.ServicesKeySet.EMPTY, null, null);
    private static final WorldOptions OPTIONS = new WorldOptions(0L, false, false);

    private final Path tempDir;
    private final LocalSampleLogger sampleLogger = new LocalSampleLogger(1);

    /** The running test server (starting it the first time). */
    static synchronized MinecraftServer get() {
        if (RUNNING.get() != null) {
            return RUNNING.get();
        }
        try {
            Path tempDir = Files.createTempDirectory("backrooms-test-server-");
            LevelStorageSource.LevelStorageAccess access = LevelStorageSource.createDefault(tempDir.resolve("world")).validateAndCreateAccess("main");
            PackRepository packs = ServerPacksSource.createPackRepository(access);
            MinecraftServer server = MinecraftServer.spin(thread -> create(thread, tempDir, access, packs));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> server.halt(false)));
            // The server marks itself running after its first tick.
            long deadline = System.currentTimeMillis() + 300_000;
            while (RUNNING.get() == null) {
                if (System.currentTimeMillis() > deadline) {
                    throw new IllegalStateException("test server didn't start within 5 minutes");
                }
                Thread.sleep(50);
            }
            return RUNNING.get();
        } catch (Exception e) {
            throw new IllegalStateException("couldn't start the test server", e);
        }
    }

    private static BackroomsTestServer create(Thread thread, Path tempDir, LevelStorageSource.LevelStorageAccess access, PackRepository packs) {
        packs.reload();
        WorldDataConfiguration data = new WorldDataConfiguration(new DataPackConfig(new ArrayList<>(packs.getAvailableIds()), List.of()),
                FeatureFlags.REGISTRY.allFlags());
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null);
        LevelSettings settings = new LevelSettings("Backrooms escape tests", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules, data);
        WorldLoader.InitConfig init = new WorldLoader.InitConfig(new WorldLoader.PackConfig(packs, data, false, true),
                Commands.CommandSelection.DEDICATED, 4);
        try {
            WorldStem stem = Util.<WorldStem>blockUntilDone(executor -> WorldLoader.load(init, context -> {
                // Like a new world in the game: the preset's dimensions plus every dimension the data
                // packs define (the mod's data/backrooms/dimension/*.json).
                Registry<LevelStem> fromPacks = context.datapackDimensions().registryOrThrow(Registries.LEVEL_STEM);
                WorldPreset preset = context.datapackWorldgen().registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value();
                WorldDimensions.Complete dimensions = preset.createWorldDimensions().bake(fromPacks);
                return new WorldLoader.DataLoadOutput<>(new PrimaryLevelData(settings, OPTIONS, dimensions.specialWorldProperty(),
                        dimensions.lifecycle()), dimensions.dimensionsRegistryAccess());
            }, WorldStem::new, Util.backgroundExecutor(), executor)).get();
            return new BackroomsTestServer(thread, access, packs, stem, tempDir);
        } catch (Exception e) {
            LOGGER.error("Couldn't load the test world", e);
            throw new IllegalStateException(e);
        }
    }

    private BackroomsTestServer(Thread thread, LevelStorageSource.LevelStorageAccess access, PackRepository packs, WorldStem stem, Path tempDir) {
        super(thread, access, packs, stem, Proxy.NO_PROXY, fixer(), NO_SERVICES, LoggerChunkProgressListener::createFromGameruleRadius);
        this.tempDir = tempDir;
    }

    private static DataFixer fixer() {
        return DataFixers.getDataFixer();
    }

    @Override
    protected boolean initServer() {
        setPlayerList(new PlayerList(this, registries(), playerDataStorage, 16) {});
        ServerLifecycleHooks.handleServerAboutToStart(this);
        loadLevel();
        ServerLifecycleHooks.handleServerStarting(this);
        LOGGER.info("Backrooms test server started with {} levels", levelKeys().size());
        return true;
    }

    @Override
    public void tickServer(java.util.function.BooleanSupplier hasTimeLeft) {
        super.tickServer(hasTimeLeft);
        RUNNING.compareAndSet(null, this);
    }

    @Override
    public boolean saveEverything(boolean suppressLog, boolean flush, boolean forced) {
        return false; // nothing worth keeping
    }

    @Override
    public void stopServer() {
        getPlayerList().removeAll();
        try {
            storageSource.deleteLevel();
            storageSource.close();
            Files.deleteIfExists(tempDir);
        } catch (IOException e) {
            LOGGER.warn("Couldn't delete the test world", e);
        }
    }

    @Override
    public SystemReport fillServerSystemReport(SystemReport report) {
        report.setDetail("Type", "Backrooms escape test server");
        return report;
    }

    @Override
    public boolean isHardcore() {
        return false;
    }

    @Override
    public int getOperatorUserPermissionLevel() {
        return 4;
    }

    @Override
    public int getFunctionCompilationLevel() {
        return 4;
    }

    @Override
    public boolean shouldRconBroadcast() {
        return false;
    }

    @Override
    public boolean isDedicatedServer() {
        return true;
    }

    @Override
    public int getRateLimitPacketsPerSecond() {
        return 0;
    }

    @Override
    public boolean isEpollEnabled() {
        return false;
    }

    @Override
    public boolean isCommandBlockEnabled() {
        return false;
    }

    @Override
    public boolean isPublished() {
        return false;
    }

    @Override
    public boolean shouldInformAdmins() {
        return false;
    }

    @Override
    public boolean isSingleplayerOwner(GameProfile profile) {
        return false;
    }

    @Override
    protected SampleLogger getTickTimeLogger() {
        return sampleLogger;
    }

    @Override
    public boolean isTickTimeLoggingEnabled() {
        return false;
    }
}
