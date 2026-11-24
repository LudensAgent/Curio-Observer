package dev.curio.observer.render;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Exercises Minecraft's real voxel raycasts, including tunnel walls, ceilings and fluid shapes. */
class BroadcastCameraSpaceTest {
    private static final Vec3 EYE = new Vec3(.5, 65.62, .5);

    @BeforeAll static void bootstrapRegistries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void openGroundHasSpaceForTheCamera() {
        TestWorld world = new TestWorld();
        world.fill(-3, 63, -3, 3, 63, 3, Blocks.STONE.defaultBlockState());
        assertTrue(BroadcastCameraSpace.roomy(world, new TestEntity(), EYE));
    }

    @Test void longOneBlockWideTwoBlockHighMineTunnelKeepsFirstPerson() {
        TestWorld world = new TestWorld();
        world.fill(-1, 63, -8, 1, 63, 8, Blocks.STONE.defaultBlockState());
        world.fill(-1, 66, -8, 1, 66, 8, Blocks.STONE.defaultBlockState());
        world.fill(-1, 64, -8, -1, 65, 8, Blocks.STONE.defaultBlockState());
        world.fill(1, 64, -8, 1, 65, 8, Blocks.STONE.defaultBlockState());
        assertFalse(BroadcastCameraSpace.roomy(world, new TestEntity(), EYE));
    }

    @Test void lowCeilingBlocksAnOtherwiseWideRoom() {
        TestWorld world = new TestWorld();
        world.fill(-3, 66, -3, 3, 66, 3, Blocks.STONE.defaultBlockState());
        assertFalse(BroadcastCameraSpace.roomy(world, new TestEntity(), EYE));
        assertTrue(BroadcastCameraSpace.roomy(world, new TestEntity(), EYE.add(0, -1, 0)),
                "The same ceiling permits a lower eye position with sufficient headroom");
    }

    @Test void nearbySideFrontAndRearWallsAreRejectedIndependentlyOfLookDirection() {
        for (BlockPos wall : new BlockPos[] {
                new BlockPos(1, 65, 0), new BlockPos(-1, 65, 0),
                new BlockPos(0, 65, 1), new BlockPos(0, 65, -1)}) {
            TestWorld world = new TestWorld();
            world.blocks.put(wall, Blocks.STONE.defaultBlockState());
            TestEntity target = new TestEntity();
            for (float yaw : new float[] {0, 90, 180, 270}) {
                target.setYRot(yaw);
                assertFalse(BroadcastCameraSpace.roomy(world, target, EYE),
                        "A close wall at " + wall + " must prevent a cramped shot at yaw " + yaw);
            }
        }
    }

    @Test void waterAndLavaDoNotActAsSolidCameraObstacles() {
        TestWorld world = new TestWorld();
        world.fill(-2, 64, -2, 2, 67, 2, Blocks.WATER.defaultBlockState());
        world.blocks.put(new BlockPos(1, 65, 0), Blocks.LAVA.defaultBlockState());
        assertTrue(BroadcastCameraSpace.roomy(world, new TestEntity(), EYE));
        world.blocks.put(new BlockPos(1, 65, 0), Blocks.STONE.defaultBlockState());
        assertFalse(BroadcastCameraSpace.roomy(world, new TestEntity(), EYE),
                "Solid walls still block the camera when surrounded by fluid");
    }

    private static final class TestWorld implements BlockGetter {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();

        void fill(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, BlockState state) {
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) blocks.put(new BlockPos(x, y, z), state);
                }
            }
        }

        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public BlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return 384; }
        @Override public int getMinBuildHeight() { return -64; }
    }

    private static final class TestEntity extends Entity {
        TestEntity() {
            super(EntityType.ARMOR_STAND, null);
            setPos(.5, 64, .5);
        }
        @Override protected void defineSynchedData() {}
        @Override protected void readAdditionalSaveData(CompoundTag nbt) {}
        @Override protected void addAdditionalSaveData(CompoundTag nbt) {}
    }
}
