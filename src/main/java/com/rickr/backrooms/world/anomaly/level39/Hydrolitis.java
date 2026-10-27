package com.rickr.backrooms.world.anomaly.level39;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rickr.backrooms.Backrooms;
import com.rickr.backrooms.registry.ModItems;
import com.rickr.backrooms.world.BackroomsLevel;
import com.rickr.backrooms.world.BackroomsSeeds;
import com.rickr.backrooms.world.layout.Level39Layout;
import java.util.Collection;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The Hydrolitis plague (canon: Level 39's stagnant tunnel water carries it). Standing in a
 * stagnant run, and above all swimming in it, builds up an infection; once it has taken hold it
 * keeps getting worse on its own until you drink almond water (or die).
 *
 * <p>Stages (the infection counter runs 0..{@value #MAX}):
 * <ol>
 *   <li><b>Fever</b> from {@value #FEVER}: hot and shivery (the client warms the picture a little and
 *       plays chills); no game effect yet.</li>
 *   <li><b>Weakness</b> from {@value #WEAKNESS}: Slowness and Mining Fatigue.</li>
 *   <li><b>Nausea</b> from {@value #NAUSEA}: now and then you retch and throw up: Nausea, and the
 *       food in your stomach is gone.</li>
 *   <li><b>Delirium</b> from {@value #DELIRIUM}: you hear things that aren't there (client), you are
 *       slower still, and at the very end the fever starts to hurt.</li>
 * </ol>
 * The status effect {@code backrooms:hydrolitis} shows the stage (and tells the client about it).
 * Test with {@code /backrooms hydrolitis [check|clear|set <0-1000>]}.
 */
@EventBusSubscriber(modid = Backrooms.MOD_ID)
public final class Hydrolitis {
    public static final int MAX = 1000;
    public static final int FEVER = 100;
    public static final int WEAKNESS = 300;
    public static final int NAUSEA = 500;
    public static final int DELIRIUM = 700;
    /** Infection gained per second swimming in stagnant water (about 3 s to a fever)... */
    private static final int SWIM_GAIN = 36;
    /** ...and standing on a stagnant run's rim (about 10 s). */
    private static final int STAND_GAIN = 10;
    /** Once it has taken hold it grows by this much a second by itself: delirium ~10 min after a fever. */
    private static final int PROGRESS = 1;
    /** A light exposure that never reached a fever wears off at this rate per second. */
    private static final int RECOVER = 3;
    /** Throwing up happens about this often (seconds, random) in the nausea stage. */
    private static final int RETCH_MIN_S = 40;
    private static final int RETCH_SPREAD_S = 50;
    /** Food points lost when you throw up. */
    private static final int RETCH_FOOD = 4;
    /** At full infection the fever hurts this much every {@value #BURN_EVERY_S} s (never below 3 hearts). */
    private static final float BURN_DAMAGE = 1.0f;
    private static final int BURN_EVERY_S = 10;
    private static final float BURN_FLOOR = 6.0f;

    private Hydrolitis() {}

    public static int infection(ServerPlayer player) {
        return player.getData(Level39AnomalyRegistry.INFECTION);
    }

    /** 0 healthy, 1 fever, 2 weakness, 3 nausea, 4 delirium. */
    public static int stage(int infection) {
        if (infection >= DELIRIUM) return 4;
        if (infection >= NAUSEA) return 3;
        if (infection >= WEAKNESS) return 2;
        if (infection >= FEVER) return 1;
        return 0;
    }

    public static void set(ServerPlayer player, int value) {
        int before = infection(player);
        int now = Math.max(0, Math.min(MAX, value));
        player.setData(Level39AnomalyRegistry.INFECTION, now);
        int s0 = stage(before);
        int s1 = stage(now);
        if (s1 > s0) {
            player.displayClientMessage(Component.translatable("message.backrooms.hydrolitis.stage" + s1)
                    .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC), true);
        }
        applyStatus(player, s1);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || player.tickCount % 20 != 0) {
            return;
        }
        int infection = infection(player);
        boolean inLevel = BackroomsLevel.fromDimension(player.level().dimension()) == BackroomsLevel.LEVEL_39;
        int gain = inLevel && !player.isCreative() ? exposure(player) : 0;
        if (infection == 0 && gain == 0) {
            return;
        }
        int next;
        if (gain > 0) {
            next = infection + gain;
        } else if (infection >= FEVER) {
            next = infection + PROGRESS;
        } else {
            next = infection - RECOVER;
        }
        set(player, next);
        symptoms(player, stage(infection(player)));
    }

    /** How much infection a second here gives: swimming or standing in a stagnant run. */
    private static int exposure(ServerPlayer player) {
        double y = player.getY();
        if (y > Level39Layout.TUNNEL_VAULT_TOP || y < Level39Layout.TUNNEL_FLOOR_Y - 1) {
            return 0;
        }
        long seed = BackroomsSeeds.layoutSeed(player.serverLevel(), BackroomsLevel.LEVEL_39);
        if (!Level39Layout.isStagnant(seed, player.getBlockX(), player.getBlockZ())) {
            return 0;
        }
        return player.isInWater() ? SWIM_GAIN : STAND_GAIN;
    }

    /** Keeps the status effect (icon + stage for the client) and the stage's game effects on. */
    private static void applyStatus(ServerPlayer player, int stage) {
        if (stage == 0) {
            if (player.hasEffect(Level39AnomalyRegistry.HYDROLITIS)) {
                player.removeEffect(Level39AnomalyRegistry.HYDROLITIS);
            }
            return;
        }
        MobEffectInstance now = player.getEffect(Level39AnomalyRegistry.HYDROLITIS);
        if (now == null || now.getAmplifier() != stage - 1 || now.getDuration() < 200) {
            if (now != null) {
                player.removeEffect(Level39AnomalyRegistry.HYDROLITIS);
            }
            player.addEffect(new MobEffectInstance(Level39AnomalyRegistry.HYDROLITIS, 1200, stage - 1, false, false, true));
        }
        if (stage >= 2) {
            // Weak and heavy-limbed: refreshed every second so it lasts exactly as long as the stage.
            int slow = stage >= 4 ? 1 : 0;
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, slow, false, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 60, 0, false, false, false));
            if (stage >= 4) {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false, false));
            }
        }
    }

    /** The once-a-second symptoms that are events rather than lasting effects. */
    private static void symptoms(ServerPlayer player, int stage) {
        if (stage >= 3 && player.getRandom().nextInt(RETCH_MIN_S + RETCH_SPREAD_S) == 0) {
            retch(player);
        }
        if (stage >= 4 && infection(player) >= MAX && player.tickCount % (BURN_EVERY_S * 20) < 20
                && player.getHealth() - BURN_DAMAGE >= BURN_FLOOR) {
            player.hurt(Level39AnomalyRegistry.damage(player.level(), Level39AnomalyRegistry.HYDROLITIS_DAMAGE), BURN_DAMAGE);
        }
    }

    /** Throwing up: everyone near hears it, you get Nausea and lose what you ate. */
    private static void retch(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), Level39AnomalyRegistry.HYDROLITIS_RETCH.get(),
                SoundSource.PLAYERS, 1.0f, 0.95f + player.getRandom().nextFloat() * 0.1f);
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0, false, false, false));
        var food = player.getFoodData();
        food.setFoodLevel(Math.max(0, food.getFoodLevel() - RETCH_FOOD));
        food.setSaturation(0.0f);
        Vec3 mouth = player.getEyePosition().add(player.getLookAngle().multiply(0.5, 0.0, 0.5)).add(0.0, -0.25, 0.0);
        Vec3 look = player.getLookAngle();
        // Sickly green spatter falling in front of you.
        level.sendParticles(ParticleTypes.SNEEZE, mouth.x, mouth.y, mouth.z, 14, look.x * 0.15, 0.05, look.z * 0.15, 0.08);
        level.sendParticles(ParticleTypes.FALLING_WATER, mouth.x, mouth.y, mouth.z, 8, 0.15, 0.05, 0.15, 0.0);
        player.displayClientMessage(Component.translatable("message.backrooms.hydrolitis.retch")
                .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC), true);
    }

    /** Almond water (or almond milk) cures it. */
    @SubscribeEvent
    public static void onDrink(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack used = event.getItem();
        if (!used.is(ModItems.ALMOND_WATER.get()) && !used.is(ModItems.ALMOND_MILK.get())) {
            return;
        }
        if (infection(player) > 0) {
            cure(player);
            player.displayClientMessage(Component.translatable("message.backrooms.hydrolitis.cured")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC), true);
        }
    }

    public static void cure(ServerPlayer player) {
        boolean wasWeak = stage(infection(player)) >= 2;
        player.setData(Level39AnomalyRegistry.INFECTION, 0);
        player.removeEffect(Level39AnomalyRegistry.HYDROLITIS);
        if (wasWeak) {
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            player.removeEffect(MobEffects.DIG_SLOWDOWN);
            player.removeEffect(MobEffects.WEAKNESS);
        }
        player.removeEffect(MobEffects.CONFUSION);
    }

    // ==========================================================================================
    // /backrooms hydrolitis [check|clear|set <value>] [targets]
    // ==========================================================================================

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(command());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("backrooms")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("hydrolitis")
                        .executes(ctx -> check(ctx, List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("check")
                                .executes(ctx -> check(ctx, List.of(ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> check(ctx, EntityArgument.getPlayers(ctx, "targets")))))
                        .then(Commands.literal("clear")
                                .executes(ctx -> setCommand(ctx, List.of(ctx.getSource().getPlayerOrException()), 0))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> setCommand(ctx, EntityArgument.getPlayers(ctx, "targets"), 0))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, MAX))
                                        .executes(ctx -> setCommand(ctx, List.of(ctx.getSource().getPlayerOrException()),
                                                IntegerArgumentType.getInteger(ctx, "value")))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> setCommand(ctx, EntityArgument.getPlayers(ctx, "targets"),
                                                        IntegerArgumentType.getInteger(ctx, "value")))))));
    }

    private static int check(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            int value = infection(player);
            ctx.getSource().sendSuccess(() -> Component.literal(player.getScoreboardName() + ": Hydrolitis " + value + "/" + MAX
                    + " (stage " + stage(value) + ": " + stageName(stage(value)) + ")"), false);
        }
        return players.size();
    }

    private static int setCommand(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, int value)
            throws CommandSyntaxException {
        for (ServerPlayer player : players) {
            if (value == 0) {
                cure(player);
            } else {
                set(player, value);
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Hydrolitis set to " + value + " (" + stageName(stage(value))
                + ") for " + players.size() + " player(s)"), true);
        return players.size();
    }

    private static String stageName(int stage) {
        return switch (stage) {
            case 1 -> "fever";
            case 2 -> "weakness";
            case 3 -> "nausea";
            case 4 -> "delirium";
            default -> "healthy";
        };
    }
}
