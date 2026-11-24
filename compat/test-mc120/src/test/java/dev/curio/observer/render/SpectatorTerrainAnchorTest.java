package dev.curio.observer.render;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SpectatorTerrainAnchorTest {
    @BeforeAll static void bootstrapRegistries() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

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
        observer.setPos(4.5, 65, 8.5);
        observer.setDeltaMovement(.25, .5, -.25);
        Vec3 observerPosition = observer.position();
        var observerBox = observer.getBoundingBox();
        Vec3 observerVelocity = observer.getDeltaMovement();
        Entity camera = new TestEntity();

        assertSame(camera, SpectatorTerrainAnchor.resolve(observer, camera, true));
        camera.setPos(x, y, z);
        Entity anchor = SpectatorTerrainAnchor.resolve(observer, camera, true);

        assertSame(camera, anchor);
        assertEquals(x, anchor.getX());
        assertEquals(y, anchor.getY());
        assertEquals(z, anchor.getZ());
        assertEquals(sectionX, SectionPos.posToSectionCoord(anchor.getX()));
        assertEquals(sectionY, SectionPos.posToSectionCoord(anchor.getY()));
        assertEquals(sectionZ, SectionPos.posToSectionCoord(anchor.getZ()));
        assertSame(observerPosition, observer.position());
        assertSame(observerBox, observer.getBoundingBox());
        assertSame(observerVelocity, observer.getDeltaMovement());
    }

    @Test void normalPlayAndFreeSpectatorCameraKeepObserverAsTerrainAnchor() {
        Entity observer = new TestEntity();
        Entity other = new TestEntity();
        other.setPos(1024, 200, -1024);

        assertSame(observer, SpectatorTerrainAnchor.resolve(observer, other, false));
        assertSame(observer, SpectatorTerrainAnchor.resolve(observer, observer, true));
        assertSame(observer, SpectatorTerrainAnchor.resolve(observer, null, true));
        assertSame(observer, SpectatorTerrainAnchor.resolve(observer, null, false));
    }

    @Test void switchingTargetsAndLeavingSpectateImmediatelySelectsCurrentAnchor() {
        Entity observer = new TestEntity();
        Entity first = new TestEntity();
        Entity second = new TestEntity();
        first.setPos(64, 80, 96);
        second.setPos(-128, 32, -160);

        assertSame(first, SpectatorTerrainAnchor.resolve(observer, first, true));
        assertSame(second, SpectatorTerrainAnchor.resolve(observer, second, true));
        assertSame(observer, SpectatorTerrainAnchor.resolve(observer, observer, true));
        assertSame(observer, SpectatorTerrainAnchor.resolve(observer, second, false));
    }

    private static final class TestEntity extends Entity {
        TestEntity() { super(EntityType.ARMOR_STAND, null); }
        @Override protected void defineSynchedData() {}
        @Override protected void readAdditionalSaveData(CompoundTag nbt) {}
        @Override protected void addAdditionalSaveData(CompoundTag nbt) {}
    }
}
