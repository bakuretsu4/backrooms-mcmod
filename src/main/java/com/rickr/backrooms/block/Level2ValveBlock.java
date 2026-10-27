package com.rickr.backrooms.block;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A gate valve on a short pipe stub out of the wall, with its hand wheel facing the corridor.
 * It sits in the AIR block in front of the wall; FACING points away from the wall (as for every
 * {@link WallMountBlock}). COLOR = the wheel's paint: red (steam, fire) or plain grey.
 */
public class Level2ValveBlock extends WallMountBlock {

    public enum WheelColor implements StringRepresentable {
        RED, GREY;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<WheelColor> COLOR = EnumProperty.create("color", WheelColor.class);

    public Level2ValveBlock(Properties properties) {
        // The wheel is 12 px across and stands 9 px out of the wall.
        super(properties, 2, 2, 7, 14, 14, 16);
        registerDefaultState(defaultBlockState().setValue(COLOR, WheelColor.RED));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COLOR);
    }
}
