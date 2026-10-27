package com.rickr.backrooms.player;

import com.rickr.backrooms.block.HouseBedBlock;
import com.rickr.backrooms.block.Level39FurnitureBlock;
import com.rickr.backrooms.block.Level39PairFurnitureBlock;
import com.rickr.backrooms.block.SupplyContainerBlock;
import com.rickr.backrooms.block.SupplyKind;
import com.rickr.backrooms.registry.HidingAudio;
import com.rickr.backrooms.registry.Level39HouseBlocks;
import com.rickr.backrooms.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Which pieces of existing furniture you can hide in, and where your view sits inside each one.
 * No new blocks: this only recognises the Supply Locker (and the filing cabinet that is really a
 * locker), the office desks, Level 39's wardrobe, and the Level 9 / Level 39 beds.
 *
 * <p>Every one of them has a FACING property. For lockers, wardrobes and desks it is the side
 * that opens (the door, or the side you sit at); for beds it points from the foot to the head, so
 * you look out of one of the long sides instead.
 */
public final class HidingSpots {
    /** The kinds of hiding spot. They differ in how high your view sits and what they sound like. */
    public enum Kind {
        LOCKER, WARDROBE, DESK, BED;

        /** Getting in: a metal door creaks shut, a wooden one closes, or you shuffle in on the floor. */
        public SoundEvent enterSound() {
            return switch (this) {
                case LOCKER -> HidingAudio.LOCKER_IN.get();
                case WARDROBE -> HidingAudio.WARDROBE_IN.get();
                case DESK, BED -> HidingAudio.SHUFFLE_IN.get();
            };
        }

        /** Getting (or being dragged) out. */
        public SoundEvent leaveSound() {
            return switch (this) {
                case LOCKER -> HidingAudio.LOCKER_OUT.get();
                case WARDROBE -> HidingAudio.WARDROBE_OUT.get();
                case DESK, BED -> HidingAudio.SHUFFLE_OUT.get();
            };
        }
    }

    /**
     * A hiding spot.
     *
     * @param anchor the block that stands for the whole piece (a bed's foot, a wardrobe's lower
     *               half), so two players can never hide in the same bed through different halves
     * @param cell   the block your view is in
     * @param kind   what it is
     * @param open   the side you look out of
     * @param camera where your view sits (inside the furniture)
     */
    public record Spot(BlockPos anchor, BlockPos cell, Kind kind, Direction open, Vec3 camera) {
        /** The way you face when you look straight out. */
        public float yaw() {
            return open.toYRot();
        }
    }

    // Eye heights above the floor. A standing eye height only fits the two-block wardrobe; the
    // locker is a single block tall, so your eyes sit near its top vents. Under a desk you crouch
    // low. Under a bed there is only a hand's breadth, so the view sits inside the bed frame:
    // from in there the frame's own faces are invisible (faces are only drawn from outside),
    // and you look out under the mattress at floor level.
    private static final double LOCKER_EYE = 0.8;
    private static final double WARDROBE_EYE = 1.62;
    private static final double DESK_EYE = 0.6;
    /** The Level 94 drawing desk's top is lower (10 px), so the view goes under it a bit lower. */
    private static final double LOW_DESK_EYE = 0.5;
    /** The Level 9 bed's frame runs from 2 to 4.5 px above the floor; the view sits in it. */
    private static final double BED_EYE = 3.0 / 16.0;
    /** The Level 39 iron bed's frame runs from 3 to 5 px. */
    private static final double IRON_BED_EYE = 4.0 / 16.0;

    // How far in from the open side the view sits. Just behind a locker's door (its door panel
    // has no inside face, so nothing blocks the view), inside the wardrobe's door panels (behind
    // the inside of the doors you would otherwise see a brown wall), and well under a desk or bed.
    private static final double LOCKER_DEPTH = 0.2;
    private static final double WARDROBE_DEPTH = 4.25 / 16.0;
    private static final double DESK_DEPTH = 0.35;
    private static final double BED_DEPTH = 0.2;

    /**
     * The hiding spot at a clicked block, or null if it isn't one. {@code from} is where the
     * player stands: under a bed you crawl in from (and look out of) the side you're on.
     */
    @Nullable
    public static Spot find(Level level, BlockPos pos, BlockState state, Vec3 from) {
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return null;
        }
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        if (state.getBlock() instanceof SupplyContainerBlock container && container.kind() == SupplyKind.LOCKER) {
            return front(pos, pos, Kind.LOCKER, facing, LOCKER_EYE, LOCKER_DEPTH);
        }
        if (state.is(ModBlocks.OFFICE_DESK.get()) || state.is(ModBlocks.OFFICE_DESK_PC.get())) {
            return front(pos, pos, Kind.DESK, facing, DESK_EYE, DESK_DEPTH);
        }
        if (state.is(ModBlocks.L94_DRAWING_DESK.get())) {
            return front(pos, pos, Kind.DESK, facing, LOW_DESK_EYE, DESK_DEPTH);
        }
        if (state.is(Level39HouseBlocks.WARDROBE.get())) {
            // Upside-down furniture (Level 39's inverted houses) has its door hanging from the ceiling.
            if (state.getValue(Level39FurnitureBlock.INVERTED)) {
                return null;
            }
            // The lower half is the anchor; your eyes are up in the top half.
            BlockPos lower = state.getValue(Level39PairFurnitureBlock.SECOND) ? pos.below() : pos;
            return front(lower, lower.above(), Kind.WARDROBE, facing, WARDROBE_EYE, WARDROBE_DEPTH);
        }
        if (state.is(ModBlocks.BED.get())) {
            BlockPos foot = state.getValue(HouseBedBlock.HEAD) ? pos.relative(facing.getOpposite()) : pos;
            return bed(foot, pos, facing, BED_EYE, from);
        }
        if (state.is(Level39HouseBlocks.BED.get())) {
            if (state.getValue(Level39FurnitureBlock.INVERTED)) {
                return null;
            }
            BlockPos foot = state.getValue(Level39PairFurnitureBlock.SECOND) ? pos.relative(facing.getOpposite()) : pos;
            return bed(foot, pos, facing, IRON_BED_EYE, from);
        }
        return null;
    }

    /** A spot you look out of from its front (FACING) side. */
    private static Spot front(BlockPos anchor, BlockPos cell, Kind kind, Direction open, double eye, double depth) {
        Vec3 camera = Vec3.atBottomCenterOf(anchor)
                .add(open.getStepX() * (0.5 - depth), eye, open.getStepZ() * (0.5 - depth));
        return new Spot(anchor, cell, kind, open, camera);
    }

    /** Under a bed: look out of whichever long side the player is on. */
    private static Spot bed(BlockPos foot, BlockPos half, Direction facing, double eye, Vec3 from) {
        Direction left = facing.getCounterClockWise();
        Vec3 centre = Vec3.atBottomCenterOf(half);
        double side = (from.x - centre.x) * left.getStepX() + (from.z - centre.z) * left.getStepZ();
        Direction open = side >= 0.0 ? left : left.getOpposite();
        Vec3 camera = centre.add(open.getStepX() * (0.5 - BED_DEPTH), eye, open.getStepZ() * (0.5 - BED_DEPTH));
        return new Spot(foot, half, Kind.BED, open, camera);
    }

    /** True while the furniture is still there, unchanged (not broken, replaced or turned). */
    public static boolean stillThere(Level level, Spot spot) {
        BlockState state = level.getBlockState(spot.cell());
        Spot again = find(level, spot.cell(), state, spot.camera().add(
                spot.open().getStepX(), 0.0, spot.open().getStepZ()));
        return again != null && again.anchor().equals(spot.anchor()) && again.open() == spot.open();
    }

    /**
     * Where the player's (invisible) body waits while they hide, and where they step out: on the
     * floor right in front of the open side if there is room, else beside it. Keeping the body
     * outside the furniture means it is never stuck inside a block (no suffocation, nothing to
     * push it out on login); only the view goes inside. Null if there is no room anywhere.
     */
    @Nullable
    public static Vec3 standingPlace(Level level, Spot spot, Player player) {
        Direction open = spot.open();
        BlockPos base = spot.kind() == Kind.WARDROBE ? spot.anchor() : spot.cell();
        BlockPos front = base.relative(open);
        BlockPos[] tries = {
                front,
                front.relative(open.getClockWise()),
                front.relative(open.getCounterClockWise()),
                base.relative(open.getClockWise()),
                base.relative(open.getCounterClockWise()),
        };
        for (BlockPos at : tries) {
            // Stand on whatever is there: a full floor block below, or a rug/carpet layer in the cell itself.
            VoxelShape layer = level.getBlockState(at).getCollisionShape(level, at);
            double lift = layer.isEmpty() ? 0.0 : layer.max(Direction.Axis.Y);
            if (lift > 0.5) {
                continue;
            }
            boolean floor = lift > 0.0 || !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty();
            Vec3 feet = Vec3.atBottomCenterOf(at).add(0.0, lift, 0.0);
            AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(feet);
            if (floor && level.noCollision(player, box)) {
                return feet;
            }
        }
        return null;
    }

    private HidingSpots() {}
}
