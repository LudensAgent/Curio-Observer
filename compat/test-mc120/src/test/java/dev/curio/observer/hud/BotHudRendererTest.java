package dev.curio.observer.hud;

import static org.junit.jupiter.api.Assertions.*;

import dev.curio.observer.compat.HudCanvas;
import dev.curio.observer.state.BotState;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BotHudRendererTest {
    private record Bubble(String kind, int x, int y) {}

    private static HudCanvas recording(List<Bubble> bubbles) {
        return (HudCanvas) Proxy.newProxyInstance(HudCanvas.class.getClassLoader(), new Class<?>[] {HudCanvas.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "drawAirFull" -> { bubbles.add(new Bubble("full", (int) args[0], (int) args[1])); yield null; }
                    case "drawAirBursting" -> { bubbles.add(new Bubble("bursting", (int) args[0], (int) args[1])); yield null; }
                    case "width", "height" -> 0;
                    default -> null;
                });
    }

    @Test void oxygenBubblesUseVanillaPositionsAboveTheHungerBar() {
        List<Bubble> bubbles = new ArrayList<>();
        BotHudRenderer.renderOxygen(recording(bubbles), 270, 300, true, true, 160, 240);
        assertEquals(9, bubbles.size());
        for (int slot = 0; slot < 9; slot++) {
            assertEquals(new Bubble("full", 242 - slot * 8, 191), bubbles.get(slot));
        }
    }

    @Test void bubbleCountsMatchVanillaFormula() {
        List<Bubble> bubbles = new ArrayList<>();
        HudCanvas canvas = recording(bubbles);
        int[][] cases = { {300, 10, 0}, {299, 10, 0}, {285, 10, 0}, {272, 9, 1}, {271, 9, 1}, {150, 5, 0}, {31, 1, 1}, {2, 0, 1}, {0, 0, 0} };
        for (int[] c : cases) {
            bubbles.clear();
            BotHudRenderer.renderOxygen(canvas, c[0], 300, true, true, 160, 240);
            assertEquals(c[1], bubbles.stream().filter(b -> b.kind().equals("full")).count(), "full at air " + c[0]);
            assertEquals(c[2], bubbles.stream().filter(b -> b.kind().equals("bursting")).count(), "popping at air " + c[0]);
        }
    }

    @Test void refillingAirStaysVisibleOnLandLikeVanilla() {
        List<Bubble> bubbles = new ArrayList<>();
        HudCanvas canvas = recording(bubbles);
        BotHudRenderer.renderOxygen(canvas, 120, 300, false, true, 160, 240);
        assertEquals(4, bubbles.size());
        bubbles.clear();
        BotHudRenderer.renderOxygen(canvas, 300, 300, false, true, 160, 240);
        assertTrue(bubbles.isEmpty(), "Full air on land shows nothing");
    }

    @Test void possiblyStaleReadingsNeverRenderOnLand() {
        List<Bubble> bubbles = new ArrayList<>();
        HudCanvas canvas = recording(bubbles);
        for (int air = 0; air <= 300; air += 15) {
            BotHudRenderer.renderOxygen(canvas, air, 300, false, false, 160, 240);
            assertTrue(bubbles.isEmpty(), "Unexpected bubbles on land at air " + air);
        }
    }
}
