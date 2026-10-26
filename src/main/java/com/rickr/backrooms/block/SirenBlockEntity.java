package com.rickr.backrooms.block;

import com.rickr.backrooms.registry.ModBlockEntities;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Level 94 siren's block entity. It stores nothing; it only exists so the siren gets a CLIENT
 * tick, in which it reports "I'm here" to a small list of nearby sirens.
 *
 * <p>Why not just play the song from every siren? A town has many of them, and a dozen copies of
 * the same tune at once would be deafening (and out of step). So the client's siren player
 * ({@code client/SirenSongs}) reads this list every tick and lets only the nearest two sing.
 * This class is common code (the server loads it too), so it must not touch client classes:
 * the list is plain data, and only the client fills and reads it.
 */
public class SirenBlockEntity extends BlockEntity {
    /** Client side: siren position -> the game tick it last ticked. Old entries mean "unloaded". */
    private static final Map<BlockPos, Long> CLIENT_SIRENS = new HashMap<>();

    public SirenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.L94_SIREN.get(), pos, state);
    }

    /** Runs on the client only (see {@link SirenBlock#getTicker}): "this siren is still loaded". */
    public static void clientTick(Level level, BlockPos pos, BlockState state, SirenBlockEntity siren) {
        CLIENT_SIRENS.put(siren.getBlockPos(), level.getGameTime());
    }

    /** The client's sirens and when each last ticked. Only the client thread may use this. */
    public static Map<BlockPos, Long> clientSirens() {
        return CLIENT_SIRENS;
    }
}
