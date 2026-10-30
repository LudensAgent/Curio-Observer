package dev.curio.observer.render;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SpectatorTerrainAnchorTest {
    @BeforeAll static void bootstrapRegistries() { SharedConstants.createGameVersion(); Bootstrap.initialize(); }

    @ParameterizedTest
    @CsvSource({
            "15.75, 79.75, 15.75, 0, 4, 0",
            "16.25, 80.25, 16.25, 1, 5, 1",
            "257.5, 160.5, 513.5, 16, 10, 32",
            "-0.25, -0.25, -0.25, -1, -1, -1",
            "-16.25, -16.25, -32.25, -2, -2, -3"
    })
    void terrainFollowsMovingCameraAcrossSectionsWithoutMovingObserver(
            double x, double y, double z, int sectionX, int sectionY, int sectionZ) {
        Entity observer = new TestEntity();
        observer.setPosition(4.5, 65, 8.5);
        observer.setVelocity(.25, .5, -.25);
        Vec3d observerPosition = observer.getPos();
        var observerBox = observer.getBoundingBox();
        Vec3d observerVelocity = observer.getVelocity();
        Entity camera = new TestEntity();

        // The spectator's body stays put while the followed player crosses the render section grid.
        assertSame(camera, SpectatorTerrainAnchor.resolve(observer, camera, true));
        camera.setPosition(x, y, z);
        Entity anchor = SpectatorTerrainAnchor.resolve(observer, camera, true);

        assertSame(camera, anchor);
        assertEquals(x, anchor.getX());
        assertEquals(y, anchor.getY());
        assertEquals(z, anchor.getZ());
        assertEquals(sectionX, ChunkSectionPos.getSectionCoord(anchor.getX()));
        assertEquals(sectionY, ChunkSectionPos.getSectionCoord(anchor.getY()));
        assertEquals(sectionZ, ChunkSectionPos.getSectionCoord(anchor.getZ()));
        assertSame(observerPosition, observer.getPos());
        assertSame(observerBox, observer.getBoundingBox());
        assertSame(observerVelocity, observer.getVelocity());
    }

    @Test void normalPlayAndFreeSpectatorCameraKeepObserverAsTerrainAnchor() {
        Entity observer = new TestEntity();
        Entity other = new TestEntity();
        other.setPosition(1024, 200, -1024);

        assertSame(observer, SpectatorTerrainAnchor.resolve(observer, other, false));
        assertSame(observer, SpectatorTerrainAnchor.resolve(observer, observer, true));
        assertSame(observer, SpectatorTerrainAnchor.resolve(observer, null, true));
        assertSame(observer, SpectatorTerrainAnchor.resolve(observer, null, false));
    }

    @Test void switchingTargetsAndLeavingSpectateImmediatelySelectsCurrentAnchor() {
        Entity observer = new TestEntity();
        Entity first = new TestEntity();
        Entity second = new TestEntity();
        first.setPosition(64, 80, 96);
        second.setPosition(-128, 32, -160);

        assertSame(first, SpectatorTerrainAnchor.resolve(observer, first, true));
        assertSame(second, SpectatorTerrainAnchor.resolve(observer, second, true));
        assertSame(observer, SpectatorTerrainAnchor.resolve(observer, observer, true));
        assertSame(observer, SpectatorTerrainAnchor.resolve(observer, second, false));
    }

    /** Real Minecraft entities with no world ticking, network connection or OpenGL context. */
    private static final class TestEntity extends Entity {
        TestEntity() { super(EntityType.ARMOR_STAND, null); }
        @Override protected void initDataTracker() {}
        @Override protected void readCustomDataFromNbt(NbtCompound nbt) {}
        @Override protected void writeCustomDataToNbt(NbtCompound nbt) {}
    }
}
