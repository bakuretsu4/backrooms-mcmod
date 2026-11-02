package com.rickr.backrooms.registry;

import com.rickr.backrooms.block.Level227HangingBlock;
import com.rickr.backrooms.block.Level227PlantBlock;
import java.util.function.Function;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The blocks of Level 369, "Comely" (world/layout/Level369Plan): pastel plaster walls, acoustic
 * ceilings with light panels, frosted skylights, glowing hallway windows, brick paths, a lawn for a
 * floor, flowers, bushes, glowing plants for the dark rooms, and the arch of vines that leads out.
 * The young trees' trunks are Level 227's logs; the hallway doors are the Lobby's. Textures
 * and models come from tools/anomaly_assets/level369.py (64 px surfaces and 64 px plants: the
 * block atlas is nearly full). Also the drifting petals' particle type.
 */
public final class Level369Blocks {
    /** Painted plaster walls, one block per colour (see Level369Plan's wall colours). */
    public static final DeferredBlock<Block> PLASTER_WHITE = structural("l369_plaster_white", MapColor.SNOW, SoundType.CALCITE);
    public static final DeferredBlock<Block> PLASTER_PEACH = structural("l369_plaster_peach", MapColor.TERRACOTTA_WHITE, SoundType.CALCITE);
    public static final DeferredBlock<Block> PLASTER_MINT = structural("l369_plaster_mint", MapColor.COLOR_LIGHT_GREEN, SoundType.CALCITE);
    public static final DeferredBlock<Block> PLASTER_LAVENDER = structural("l369_plaster_lavender", MapColor.COLOR_PURPLE, SoundType.CALCITE);
    public static final DeferredBlock<Block> PLASTER_BUTTER = structural("l369_plaster_butter", MapColor.SAND, SoundType.CALCITE);
    public static final DeferredBlock<Block> PLASTER_ROSE = structural("l369_plaster_rose", MapColor.COLOR_PINK, SoundType.CALCITE);
    /** Deep sage plaster: the dark rooms. */
    public static final DeferredBlock<Block> PLASTER_DUSK = structural("l369_plaster_dusk", MapColor.COLOR_GREEN, SoundType.CALCITE);
    /** White acoustic ceiling tile (hallways, skylit rooms, dark rooms). */
    public static final DeferredBlock<Block> CEILING = structural("l369_ceiling", MapColor.SNOW, SoundType.CALCITE);
    /** Salmon-pink acoustic ceiling tile (the pillar halls). */
    public static final DeferredBlock<Block> CEILING_ROSE = structural("l369_ceiling_rose", MapColor.COLOR_PINK, SoundType.CALCITE);
    /** A recessed light panel set into the ceiling: warm white. */
    public static final DeferredBlock<Block> PANEL = block("l369_panel", Block::new,
            unbreakable(MapColor.SNOW, SoundType.GLASS).lightLevel(state -> 14));
    /** The frosted glass at the top of a skylight shaft, overexposed white with daylight. */
    public static final DeferredBlock<Block> SKYLIGHT = block("l369_skylight", Block::new,
            unbreakable(MapColor.SNOW, SoundType.GLASS).lightLevel(state -> 15));
    /** A tall hallway window full of pale blue-white light (nothing can be seen through it). */
    public static final DeferredBlock<Block> WINDOW = block("l369_window", Block::new,
            unbreakable(MapColor.ICE, SoundType.GLASS).lightLevel(state -> 12));
    /** Terracotta brick pavers: the winding paths of the pillar halls, the doorway thresholds. */
    public static final DeferredBlock<Block> BRICK = structural("l369_brick", MapColor.TERRACOTTA_ORANGE, SoundType.MUD_BRICKS);
    /** The lawn every floor is carpeted with. */
    public static final DeferredBlock<Block> GRASS = structural("l369_grass", MapColor.GRASS, SoundType.GRASS);
    /** The soil under the lawn. */
    public static final DeferredBlock<Block> SOIL = structural("l369_soil", MapColor.DIRT, SoundType.ROOTED_DIRT);
    /** Masonry overgrown with vines and small flowers: the great arch that leads out. */
    public static final DeferredBlock<Block> VINE_WALL = structural("l369_vine_wall", MapColor.PLANT, SoundType.AZALEA_LEAVES);
    /** The leaves of the bushes along the walls and the young trees under the skylights. */
    public static final DeferredBlock<Block> LEAVES = block("l369_leaves", Block::new,
            unbreakable(MapColor.PLANT, SoundType.AZALEA_LEAVES).noOcclusion()
                    .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false));
    /**
     * The curtain of hanging flowers filling the arch. You walk into it (no collision), and through
     * it you come out in Level 7 (world/Level369Rules).
     */
    public static final DeferredBlock<Block> VEIL = block("l369_veil", Block::new,
            unbreakable(MapColor.PLANT, SoundType.AZALEA_LEAVES).noCollission().noOcclusion().lightLevel(state -> 9)
                    .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false));
    /** Flowers, KIND 0 blue forget-me-nots, 1 red daisies, 2 pink cosmos, 3 white daisies. */
    public static final DeferredBlock<Level227PlantBlock> FLOWER = block("l369_flower", Level227PlantBlock::new,
            plant(MapColor.PLANT, SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ));
    /** Grass tufts in the lawn, KIND 0 short, 1 tall, 2 clover, 3 seed heads. */
    public static final DeferredBlock<Level227PlantBlock> TUFT = block("l369_tuft", Level227PlantBlock::new,
            plant(MapColor.PLANT, SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ));
    /**
     * The dark rooms' glowing plants, KIND 0 cyan bells, 1 pale green lanterns, 2 violet fronds,
     * 3 a tuft of glowing moss. A dim light of their own.
     */
    public static final DeferredBlock<Level227PlantBlock> GLOW = block("l369_glow", Level227PlantBlock::new,
            plant(MapColor.COLOR_CYAN, SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ)
                    .lightLevel(state -> state.getValue(Level227PlantBlock.KIND) == 3 ? 5 : 8));
    /** A vine hanging from the dark rooms' ceilings, with small glowing flowers; TIP = the lowest one. */
    public static final DeferredBlock<Level227HangingBlock> GLOW_VINE = block("l369_glow_vine",
            props -> new Level227HangingBlock(props, 3, false), plant(MapColor.PLANT, SoundType.VINE).lightLevel(state -> 6));

    /**
     * A petal or a speck of pollen drifting through the light (client/particle/PetalParticle). The
     * spawn's x speed argument picks the sprite (0-1 red, 2-3 blue, 4-5 pink, 6-7 white, 8-9 pollen).
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PETAL = ModParticles.PARTICLES.register("l369_petal",
            () -> new SimpleParticleType(false));

    static {
        for (DeferredBlock<?> block : new DeferredBlock<?>[] {PLASTER_WHITE, PLASTER_PEACH, PLASTER_MINT, PLASTER_LAVENDER,
                PLASTER_BUTTER, PLASTER_ROSE, PLASTER_DUSK, CEILING, CEILING_ROSE, PANEL, SKYLIGHT, WINDOW, BRICK, GRASS, SOIL,
                VINE_WALL, VEIL, LEAVES, FLOWER, TUFT, GLOW, GLOW_VINE}) {
            ModItems.ITEMS.registerSimpleBlockItem(block);
        }
    }

    /** Called once from the mod constructor so this class (and its registrations) loads in time. */
    public static void bootstrap() {
    }

    // ------------------------------------------------------------------ helpers (as in ModBlocks)

    private static <B extends Block> DeferredBlock<B> block(String name, Function<BlockBehaviour.Properties, B> factory,
                                                        BlockBehaviour.Properties props) {
        return ModBlocks.BLOCKS.registerBlock(name, factory, props);
    }

    private static DeferredBlock<Block> structural(String name, MapColor color, SoundType sound) {
        return ModBlocks.BLOCKS.registerBlock(name, (Function<BlockBehaviour.Properties, Block>) Block::new, unbreakable(color, sound));
    }

    /** Plants: no collision, light passes, nothing spawns on them. */
    private static BlockBehaviour.Properties plant(MapColor color, SoundType sound) {
        return unbreakable(color, sound).noCollission().noOcclusion()
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false)
                .isRedstoneConductor((state, level, pos) -> false);
    }

    private static BlockBehaviour.Properties unbreakable(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(-1.0f, 3_600_000.0f)
                .sound(sound)
                .noLootTable()
                .isValidSpawn((state, level, pos, type) -> false)
                .pushReaction(PushReaction.BLOCK);
    }

    private Level369Blocks() {}
}
