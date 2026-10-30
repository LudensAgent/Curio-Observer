package dev.curio.observer.hud;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

class BotHudRendererTest {
    @Test void oxygenBubblesUseVanillaTexturesAboveTheHungerBar() {
        RecordingContext context = new RecordingContext();
        BotHudRenderer.renderOxygen(context, 270, 300, true, true, 160, 240);
        assertEquals(9, context.icons.size());
        for (int slot = 0; slot < 9; slot++) {
            assertEquals(new Icon(HudTextures.ICONS, 242 - slot * 8, 191, 16, 18, 9, 9), context.icons.get(slot));
        }
    }

    @Test void bubbleCountsMatchVanillaFormula() {
        RecordingContext context = new RecordingContext();
        // air ticks -> {full, popping}, from InGameHud: ceil((air-2)*10/300) and ceil(air*10/300) - full
        int[][] cases = { {300, 10, 0}, {299, 10, 0}, {285, 10, 0}, {272, 9, 1}, {271, 9, 1}, {150, 5, 0}, {31, 1, 1}, {2, 0, 1}, {0, 0, 0} };
        for (int[] c : cases) {
            context.icons.clear();
            BotHudRenderer.renderOxygen(context, c[0], 300, true, true, 160, 240);
            assertEquals(c[1], context.icons.stream().filter(icon -> icon.u() == 16).count(), "full at air " + c[0]);
            assertEquals(c[2], context.icons.stream().filter(icon -> icon.u() == 25).count(), "popping at air " + c[0]);
        }
    }

    @Test void refillingAirStaysVisibleOnLandLikeVanilla() {
        RecordingContext context = new RecordingContext();
        BotHudRenderer.renderOxygen(context, 120, 300, false, true, 160, 240);
        assertEquals(4, context.icons.size());
        context.icons.clear();
        BotHudRenderer.renderOxygen(context, 300, 300, false, true, 160, 240);
        assertTrue(context.icons.isEmpty(), "Full air on land shows nothing");
    }

    @Test void possiblyStaleReadingsNeverRenderOnLand() {
        RecordingContext context = new RecordingContext();
        for (int air = 0; air <= 300; air += 15) {
            BotHudRenderer.renderOxygen(context, air, 300, false, false, 160, 240);
            assertTrue(context.icons.isEmpty(), "Unexpected bubbles on land at air " + air);
        }
    }

    private record Icon(Identifier texture, int x, int y, int u, int v, int width, int height) {}

    /** Capture draw calls without a game window or an OpenGL context. */
    private static final class RecordingContext extends DrawContext {
        private final List<Icon> icons = new ArrayList<>();

        private RecordingContext() { super(null, null); }

        @Override
        public void drawTexture(Identifier texture, int x, int y, int u, int v, int width, int height) {
            icons.add(new Icon(texture, x, y, u, v, width, height));
        }
    }
}
