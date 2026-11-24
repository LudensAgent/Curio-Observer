package dev.curio.observer.hud;

import static org.junit.jupiter.api.Assertions.*;

import dev.curio.observer.config.ObserverConfig;
import org.junit.jupiter.api.Test;

class HudPlacementTest {
    @Test void unsetAxesKeepTheDefaultPlace() {
        ObserverConfig.PanelConfig panel = new ObserverConfig.PanelConfig();
        assertFalse(HudPlacement.custom(panel));
        assertEquals(new HudPlacement.Point(8, 20), HudPlacement.place(panel, 8, 20, 100, 50, 800, 450));
        panel.x = 0.5;
        assertTrue(HudPlacement.custom(panel));
        assertEquals(new HudPlacement.Point(400, 20), HudPlacement.place(panel, 8, 20, 100, 50, 800, 450));
    }

    @Test void panelsStayEntirelyOnScreen() {
        ObserverConfig.PanelConfig panel = new ObserverConfig.PanelConfig();
        panel.x = 1.0;
        panel.y = 1.0;
        assertEquals(new HudPlacement.Point(700, 400), HudPlacement.place(panel, 0, 0, 100, 50, 800, 450));
        assertEquals(new HudPlacement.Point(0, 0), HudPlacement.place(panel, 0, 0, 900, 500, 800, 450),
                "A panel larger than the screen is pinned to the top-left corner");
    }
}
