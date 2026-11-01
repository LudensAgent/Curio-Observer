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

    @Test void feedConfigurationAndContainerVisibilityPersistWithoutRemovingRcon() throws Exception {
        Path path = directory.resolve("feed.json");
        Files.writeString(path, "{\"rconEnabled\":true,\"rconHost\":\"minecraft.local\",\"feedUrl\":\"  https://bot.example/observer  \"}");
        ObserverConfig config = ObserverConfig.load(path);
        assertTrue(config.feedEnabled);
        assertTrue(config.rconEnabled);
        assertEquals("https://bot.example/observer", config.feedUrl);
        assertTrue(config.visible("containers"));
        config.panel("containers").visible = false;
        config.feedEnabled = false;
        config.saveAsync(path);
        ObserverConfig.awaitPendingSaves();
        ObserverConfig loaded = ObserverConfig.load(path);
        assertFalse(loaded.feedEnabled);
        assertFalse(loaded.visible("containers"));
        assertEquals("minecraft.local", loaded.rconHost);
        loaded.feedUrl = " ";
        loaded.normalize();
        assertEquals(ObserverConfig.DEFAULT_FEED_URL, loaded.feedUrl);
    }

    @Test void observationAndLanguageDefaultsStayOptInAndValidateNames() {
        ObserverConfig config = new ObserverConfig();
        config.normalize();
        assertFalse(config.autoObserve);
        assertEquals("", config.defaultTarget);
        assertEquals(5, config.autoObserveSeconds);
        assertEquals("auto", config.language);
        config.defaultTarget = "  Alex  ";
        config.autoObserveSeconds = 999;
        config.language = "zh_cn";
        config.normalize();
        assertEquals("Alex", config.defaultTarget);
        assertEquals(300, config.autoObserveSeconds);
        assertEquals("zh_cn", config.language);
        config.defaultTarget = "Alex @a";
        config.autoObserveSeconds = 0;
        config.language = "unknown";
        config.normalize();
        assertEquals("", config.defaultTarget);
        assertEquals(1, config.autoObserveSeconds);
        assertEquals("auto", config.language);
        config.defaultTarget = null;
        config.language = null;
        config.normalize();
        assertEquals("", config.defaultTarget);
        assertEquals("auto", config.language);
    }

    @Test void savesDefaultTargetObservationIntervalAndExplicitLanguage() {
        Path path = directory.resolve("observe.json");
        ObserverConfig config = new ObserverConfig();
        config.defaultTarget = "Alex";
        config.autoObserve = true;
        config.autoObserveSeconds = 7;
        config.language = "en_us";
        config.saveAsync(path);
        ObserverConfig.awaitPendingSaves();
        ObserverConfig loaded = ObserverConfig.load(path);
        assertEquals("Alex", loaded.defaultTarget);
        assertTrue(loaded.autoObserve);
        assertEquals(7, loaded.autoObserveSeconds);
        assertEquals("en_us", loaded.language);
    }
}
