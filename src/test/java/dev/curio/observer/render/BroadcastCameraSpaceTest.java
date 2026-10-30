package dev.curio.observer.render;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.fluid.FluidState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Exercises Minecraft's real voxel raycasts, including tunnel walls, ceilings and fluid shapes. */
class BroadcastCameraSpaceTest {
    private static final Vec3d EYE = new Vec3d(.5, 65.62, .5);

    @BeforeAll static void bootstrapRegistries() { SharedConstants.createGameVersion(); Bootstrap.initialize(); }

    @Test void openGroundHasSpaceForTheCamera() {
        TestWorld world = new TestWorld();
        world.fill(-3, 63, -3, 3, 63, 3, Blocks.STONE.getDefaultState());
        assertTrue(BroadcastCameraSpace.roomy(world, new TestEntity(), EYE));
    }

    @Test void longOneBlockWideTwoBlockHighMineTunnelKeepsFirstPerson() {
        TestWorld world = new TestWorld();
        world.fill(-1, 63, -8, 1, 63, 8, Blocks.STONE.getDefaultState());
        world.fill(-1, 66, -8, 1, 66, 8, Blocks.STONE.getDefaultState());
        world.fill(-1, 64, -8, -1, 65, 8, Blocks.STONE.getDefaultState());
        world.fill(1, 64, -8, 1, 65, 8, Blocks.STONE.getDefaultState());
        assertFalse(BroadcastCameraSpace.roomy(world, new TestEntity(), EYE));
    }

    @Test void lowCeilingBlocksAnOtherwiseWideRoom() {
        TestWorld world = new TestWorld();
        world.fill(-3, 66, -3, 3, 66, 3, Blocks.STONE.getDefaultState());
        assertFalse(BroadcastCameraSpace.roomy(world, new TestEntity(), EYE));
        assertTrue(BroadcastCameraSpace.roomy(world, new TestEntity(), EYE.add(0, -1, 0)),
                "The same ceiling permits a lower eye position with sufficient headroom");
    }

    @Test void nearbySideFrontAndRearWallsAreRejectedIndependentlyOfLookDirection() {
        for (BlockPos wall : new BlockPos[] {
                new BlockPos(1, 65, 0), new BlockPos(-1, 65, 0),
                new BlockPos(0, 65, 1), new BlockPos(0, 65, -1)}) {
            TestWorld world = new TestWorld();
            world.blocks.put(wall, Blocks.STONE.getDefaultState());
            TestEntity target = new TestEntity();
            for (float yaw : new float[] {0, 90, 180, 270}) {
                target.setYaw(yaw);
                assertFalse(BroadcastCameraSpace.roomy(world, target, EYE),
                        "A close wall at " + wall + " must prevent a cramped shot at yaw " + yaw);
            }
        }
    }

    @Test void WaterAndLavaDoNotActAsSolidCameraObstacles() {
        TestWorld world = new TestWorld();
        world.fill(-2, 64, -2, 2, 67, 2, Blocks.WATER.getDefaultState());
        world.blocks.put(new BlockPos(1, 65, 0), Blocks.LAVA.getDefaultState());
        assertTrue(BroadcastCameraSpace.roomy(world, new TestEntity(), EYE));
        world.blocks.put(new BlockPos(1, 65, 0), Blocks.STONE.getDefaultState());
        assertFalse(BroadcastCameraSpace.roomy(world, new TestEntity(), EYE),
                "Solid walls still block the camera when surrounded by fluid");
    }

    private static final class TestWorld implements BlockView {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();

        void fill(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, BlockState state) {
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) blocks.put(new BlockPos(x, y, z), state);
                }
            }
        }

        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public BlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.AIR.getDefaultState()); }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return 384; }
        @Override public int getBottomY() { return -64; }
    }

    private static final class TestEntity extends Entity {
        TestEntity() {
            super(EntityType.ARMOR_STAND, null);
            setPosition(.5, 64, .5);
        }
        @Override protected void initDataTracker() {}
        @Override protected void readCustomDataFromNbt(NbtCompound nbt) {}
        @Override protected void writeCustomDataToNbt(NbtCompound nbt) {}
    }
}
