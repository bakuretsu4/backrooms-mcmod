package com.rickr.backrooms.world.layout;

import com.rickr.backrooms.block.Flooding;
import com.rickr.backrooms.block.HandrailBlock;
import com.rickr.backrooms.block.PoolLadderBlock;
import com.rickr.backrooms.block.SunlitVoidBlock;
import com.rickr.backrooms.block.TileCurveBlock;
import com.rickr.backrooms.registry.ModBlocks;
import com.rickr.backrooms.world.anomaly.AnomalySite;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jetbrains.annotations.Nullable;

/**
 * Level 37, "Sublimity": endless pristine tiled pools under warm daylight from nowhere. The
 * floor plan itself lives in {@link Level37Plan} (rooms, corridors, pillar halls, rotundas, the
 * exit pits; all pure functions of (seed, x, z)); this class only turns its codes into blocks.
 *
 * <p>Nothing hunts you here and your sanity slowly comes back. The way on is down: one of the
 * round pits of deep, dark water. Dive (the current takes over, see {@code Level37Rules}) and once
 * you sink below {@link #TRANSFER_BELOW_Y} you come out in Level 7.
 *
 * <p>Anomalous areas (a void opening, a dark opening back to the Poolrooms, looping tunnels) are
 * placed by {@link Level37AnomalyPlan}; their behaviour is in {@code world/anomaly/level37}.
 */
public final class Level37Layout extends LevelLayout {
    /** Sinking below this in a pit's water shaft takes you on to the next level (the pit floor is y14). */
    public static final int TRANSFER_BELOW_Y = 26;

    private static final Level37Plan PLAN = new Level37Plan();
    /** One 64-high code buffer per world-generation thread. */
    private static final ThreadLocal<int[]> BUFFER = ThreadLocal.withInitial(() -> new int[64]);

    public Level37Layout() {
        // 7 = air layers of an ordinary room; the real heights vary per room (see Level37Plan).
        super(7, Level37Plan.EXITS);
    }

    /** The plan, for the Level 37 rules (where the pits are). */
    public static Level37Plan plan() {
        return PLAN;
    }

    @Override
    public void fillColumn(long seed, int x, int z, Column out) {
        int[] codes = BUFFER.get();
        PLAN.column(seed, x, z, anomalyAt(seed, x, z), codes);
        for (int y = 0; y < codes.length; y++) {
            BlockState state = stateOf(codes[y]);
            if (state != null) {
                out.set(y, state);
            }
        }
    }

    @Override
    public boolean isWalkable(long seed, int x, int z) {
        return PLAN.isWalkable(seed, x, z, anomalyAt(seed, x, z));
    }

    // ---- Anomalies (see Level37AnomalyPlan) ----------------------------------------------------

    @Override
    @Nullable
    public AnomalySite anomalyAt(long seed, int x, int z) {
        return Level37AnomalyPlan.at(seed, x, z);
    }

    @Override
    public void anomaliesNear(long seed, int x, int z, int radius, Consumer<AnomalySite> out) {
        Level37AnomalyPlan.near(seed, x, z, radius, out);
    }

    @Override
    @Nullable
    public AnomalySite nearestAnomaly(long seed, int x, int z, String kind) {
        return Level37AnomalyPlan.nearest(seed, x, z, kind);
    }

    @Override
    public List<String> anomalyKinds() {
        return Level37AnomalyPlan.KINDS;
    }

    @Override
    public int highestCeilingY() {
        return Level37Plan.HIGHEST_Y;
    }

    /** /backrooms locate points at the middle of the pit. */
    @Override
    public int[] exitPosition(long seed, int x, int z) {
        return PLAN.pitCentre(seed, x, z);
    }

    /** The block for one plan code, or null for air. */
    private static BlockState stateOf(int code) {
        int data = Level37Plan.data(code);
        return switch (Level37Plan.type(code)) {
            case Level37Plan.SHALLOW -> ModBlocks.SHALLOW_WATER.get().defaultBlockState();
            case Level37Plan.WATER -> Blocks.WATER.defaultBlockState();
            case Level37Plan.WALL -> ModBlocks.L37_TILE_WALL.get().defaultBlockState();
            case Level37Plan.FLOOR -> ModBlocks.L37_TILE_FLOOR.get().defaultBlockState();
            case Level37Plan.CEIL -> ModBlocks.L37_TILE_CEILING.get().defaultBlockState();
            case Level37Plan.DEEP -> ModBlocks.L37_TILE_DEEP.get().defaultBlockState();
            case Level37Plan.PIECE -> ModBlocks.L37_TILE_CURVE.get()
                    .piece(Level37Plan.pieceShape(data), Level37Plan.pieceOrientation(data))
                    .setValue(TileCurveBlock.WATERLOGGED, Level37Plan.pieceWaterlogged(data))
                    .setValue(TileCurveBlock.FLOODED, Level37Plan.pieceFlooded(data));
            case Level37Plan.VOID -> ModBlocks.L37_SUNLIT_VOID.get().defaultBlockState()
                    .setValue(SunlitVoidBlock.FACING, Direction.from3DDataValue(data));
            case Level37Plan.SLAB -> ModBlocks.L37_TILE_SLAB.get().defaultBlockState()
                    .setValue(SlabBlock.TYPE, (data & 1) != 0 ? SlabType.TOP : SlabType.BOTTOM)
                    .setValue(SlabBlock.WATERLOGGED, (data & 2) != 0)
                    .setValue(Flooding.FLOODED, (data & 4) != 0);
            case Level37Plan.LADDER -> ModBlocks.L37_POOL_LADDER.get().defaultBlockState()
                    .setValue(PoolLadderBlock.FACING, Direction.from2DDataValue(data & 3))
                    .setValue(PoolLadderBlock.WATERLOGGED, (data & 4) != 0);
            case Level37Plan.RAIL -> ModBlocks.L37_HANDRAIL.get().defaultBlockState()
                    .setValue(HandrailBlock.FACING, Direction.from2DDataValue(data & 3))
                    .setValue(HandrailBlock.POST, (data & 4) != 0);
            case Level37Plan.LIGHT -> Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, data);
            case Level37Plan.BACKPACK -> ModBlocks.BACKPACK.get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue(data & 3));
            // The walk-in darkness of a dark opening: the same pitch black as Level 94's dark doorways.
            case Level37Plan.DARK -> ModBlocks.L94_DARK_VOID.get().defaultBlockState();
            default -> null;
        };
    }
}
