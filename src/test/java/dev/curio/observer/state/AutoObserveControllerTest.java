package dev.curio.observer.state;

import static org.junit.jupiter.api.Assertions.*;
import dev.curio.observer.config.ObserverConfig;
import org.junit.jupiter.api.Test;

class AutoObserveControllerTest {
    private ObserverConfig enabled() {
        ObserverConfig config = new ObserverConfig();
        config.autoObserve = true;
        config.defaultTarget = "Alex";
        config.normalize();
        return config;
    }

    @Test void entersSpectatorBeforeAttachingAndLimitsPermissionFailureRetries() {
        var config = enabled();
        var controller = new AutoObserveController();
        var survival = new AutoObserveController.Session(true, false, "Observer", null, true);
        var spectator = new AutoObserveController.Session(true, true, "Observer", null, true);
        assertEquals("gamemode spectator", controller.poll(config, survival, 100));
        assertNull(controller.poll(config, survival, 101));
        assertNull(controller.poll(config, spectator, 5099));
        assertEquals("spectate Alex", controller.poll(config, spectator, 5100));
        assertNull(controller.poll(config, spectator, 5101));
        assertEquals("spectate Alex", controller.poll(config, spectator, 10100));
    }

    @Test void followsConfiguredTargetWithoutSpammingTheCurrentOneAndRecoversAfterDimensionChange() {
        var config = enabled();
        var controller = new AutoObserveController();
        var matching = new AutoObserveController.Session(true, true, "Observer", "alex", true);
        assertNull(controller.poll(config, matching, 100));
        assertNull(controller.poll(config, matching, 10_000));
        var detached = new AutoObserveController.Session(true, true, "Observer", null, true);
        assertNull(controller.poll(config, detached, 10_001));
        assertEquals("spectate Alex", controller.poll(config, detached, 15_000));
        config.defaultTarget = "Steve";
        var otherTarget = new AutoObserveController.Session(true, true, "Observer", "Alex", true);
        assertEquals("spectate Steve", controller.poll(config, otherTarget, 15_001));
    }

    @Test void disabledOfflineMissingAndSelfTargetsSendNoCommands() {
        var config = enabled();
        var controller = new AutoObserveController();
        var connected = new AutoObserveController.Session(true, true, "Observer", null, true);
        assertNull(controller.poll(config, new AutoObserveController.Session(false, false, null, null, false), 0));
        assertNull(controller.poll(config, new AutoObserveController.Session(true, true, "Observer", null, false), 10));
        assertEquals("spectate Alex", controller.poll(config, connected, 20));
        config.autoObserve = false;
        assertNull(controller.poll(config, connected, 6000));
        config.autoObserve = true;
        assertNull(controller.poll(config, new AutoObserveController.Session(true, true, "alex", null, true), 7000));
        config.defaultTarget = "@a";
        config.normalize();
        assertNull(controller.poll(config, connected, 8000));
    }

    @Test void disconnectAndExplicitResetAllowTheNextConnectionToStartImmediately() {
        var config = enabled();
        var controller = new AutoObserveController();
        var connected = new AutoObserveController.Session(true, true, "Observer", null, true);
        assertEquals("spectate Alex", controller.poll(config, connected, 100));
        assertNull(controller.poll(config, new AutoObserveController.Session(false, false, null, null, false), 101));
        assertEquals("spectate Alex", controller.poll(config, connected, 102));
        controller.reset();
        assertEquals("spectate Alex", controller.poll(config, connected, 103));
    }
}
