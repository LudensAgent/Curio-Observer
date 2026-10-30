package dev.curio.observer.config;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ObserverConfigTest {
    @TempDir Path directory;

    @Test void clampsScaleDistanceSmoothingAndGamma() {
        ObserverConfig config = new ObserverConfig();
        config.hudScale = 4;
        config.infoScale = 5;
        config.gamma = -1;
        config.broadcastDistance = 10;
        config.broadcastSmoothing = -0.1;
        config.normalize();
        assertEquals(3, config.hudScale);
        assertEquals(1.5, config.infoScale);
        assertEquals(0, config.gamma);
        assertEquals(6, config.broadcastDistance);
        assertEquals(0, config.broadcastSmoothing);
        config.gamma = 99;
        config.broadcastDistance = 0;
        config.broadcastSmoothing = 1;
        config.normalize();
        assertEquals(15, config.gamma);
        assertEquals(1.5, config.broadcastDistance);
        assertEquals(0.8, config.broadcastSmoothing);
        config.gamma = Double.NaN;
        config.hudScale = Double.NaN;
        config.infoScale = Double.NaN;
        config.normalize();
        assertEquals(3, config.gamma);
        assertEquals(1, config.hudScale);
        assertEquals(.7, config.infoScale);
    }

    @Test void persistsTogglesAndRetainsDefaultsForMissingFields() throws Exception {
        Path path = directory.resolve("curio-observer.json");
        ObserverConfig config = ObserverConfig.load(path);
        config.panel("inventory").visible = false;
        config.saveAsync(path);
        config.hudEnabled = false;
        config.saveAsync(path);
        ObserverConfig.awaitPendingSaves();
        ObserverConfig loaded = ObserverConfig.load(path);
        assertFalse(loaded.visible("inventory"));
        assertFalse(loaded.hudEnabled);
        assertTrue(loaded.visible("vitals"));
        assertTrue(loaded.visible("hands"));
        Files.writeString(path, "{\"hudScale\":0.5,\"infoScale\":0.8}");
        assertEquals(.4, ObserverConfig.load(path).infoHudScale());
    }

    @Test void malformedConfigurationFallsBackWithoutOverwritingTheOriginal() throws Exception {
        Path path = directory.resolve("curio-observer.json");
        Files.writeString(path, "{broken");
        ObserverConfig config = ObserverConfig.load(path);
        assertTrue(config.hudEnabled);
        assertEquals("{broken", Files.readString(path));
    }

    @Test void legacyBroadcastAndHandsSwitchesMigrate() throws Exception {
        Path path = directory.resolve("broadcast.json");
        Files.writeString(path, "{\"broadcastEnabled\":true,\"showHands\":false}");
        ObserverConfig migrated = ObserverConfig.load(path);
        assertTrue(migrated.autoCamera);
        assertTrue(migrated.brighten);
        assertFalse(migrated.visible("hands"));
        migrated.autoCamera = false;
        migrated.saveAsync(path);
        ObserverConfig.awaitPendingSaves();
        ObserverConfig loaded = ObserverConfig.load(path);
        assertFalse(loaded.autoCamera);
        assertTrue(loaded.brighten, "The two switches persist separately");
    }
}
