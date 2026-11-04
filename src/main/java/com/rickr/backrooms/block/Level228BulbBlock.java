package com.rickr.backrooms.block;

import com.rickr.backrooms.world.layout.Level228Plan;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

/**
 * The light of one live (or failing) light source in Level 228: a bare hanging bulb, a candle-bulb
 * sconce, a run of marquee bulbs, a red EXIT sign or one of the exit hallway's cold fixtures. The
 * thing you SEE (glass, filament, socket, its glow) is a prop drawn by the prop renderer
 * (client/level228); this block is only the light, invisible and walk-through like air:
 * <ul>
 *   <li>Its block light is what lights the maze under an Iris shader pack, which draws its own
 *       lighting from block light and ignores Veil.</li>
 *   <li>With Veil the client turns each one into a warm point light (client/light: FixtureType
 *       {@code L228_*}, Level228Bulbs), and the block light is only a weak bounce.</li>
 * </ul>
 * KIND and STATE use Level228Plan's {@code LIGHT_*} numbers (dead bulbs get no block at all).
 * PHASE picks the bulb's flicker pattern and age; the layout gives a bulb and every mirror image
 * of it the same PHASE, so a failing bulb and all its reflections stutter together.
 *
 * <p>Block light is static on purpose: a flickering bulb never changes its state (relighting
 * chunks 20 times a second would cost far more than it shows). A failing bulb simply gives less
 * light; the flicker itself is client-side (the Veil light and the bulb's glowing glass).
 */
public class Level228BulbBlock extends InvisibleLightBlock {
    public static final IntegerProperty KIND = IntegerProperty.create("kind", Level228Plan.LIGHT_BULB, Level228Plan.LIGHT_COLD);
    public static final IntegerProperty STATE = IntegerProperty.create("state", Level228Plan.LIGHT_LIT, Level228Plan.LIGHT_FAILING);
    public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, 15);

    /**
     * Block light per KIND (bulb, candle, marquee, sign, cold) when lit / failing. A bare bulb
     * hangs a block under the 5-high ceiling: 12 gives the floor right under it 9 and leaves 4-5
     * halfway to the next live bulb (pools with darkness between, as in the brief). The first
     * round (11 / 8 for bulbs / candles) read 6-16 / 255 under Iris + Complementary in the
     * dark-wood Crystal Maze and 13-14 in the final chamber, too dark to read the puzzle's
     * reflections: candle sconces now give 12, so the three chamber mirrors (and the identical
     * sconces in their sealed images) are lit enough to compare. Marquee runs and signs only light
     * their own corner; the exit hallway's cold fixtures (and the Glass Labyrinth's cold spill)
     * match a bulb, so the hallway reads as a lit, safe place.
     */
    private static final int[][] EMISSION = {{12, 9}, {12, 9}, {8, 6}, {6, 5}, {12, 9}};

    public Level228BulbBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(KIND, Level228Plan.LIGHT_BULB)
                .setValue(STATE, Level228Plan.LIGHT_LIT).setValue(PHASE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND, STATE, PHASE);
    }

    /** The registry's lightLevel. */
    public static int emission(BlockState state) {
        return EMISSION[state.getValue(KIND)][state.getValue(STATE)];
    }

    /**
     * The state for a light source of the layout ({@code kind}, {@code state} and {@code phase} as
     * in Level228Plan's prop flags); null for a dead one (it has no light block). For the world
     * generator.
     */
    @Nullable
    public static BlockState state(int kind, int state, int phase) {
        if (state == Level228Plan.LIGHT_DEAD) {
            return null;
        }
        return com.rickr.backrooms.registry.Level228Blocks.BULB.get().defaultBlockState()
                .setValue(KIND, Math.clamp(kind, Level228Plan.LIGHT_BULB, Level228Plan.LIGHT_COLD))
                .setValue(STATE, state == Level228Plan.LIGHT_FAILING ? Level228Plan.LIGHT_FAILING : Level228Plan.LIGHT_LIT)
                .setValue(PHASE, phase & 15);
    }
}
