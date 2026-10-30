package dev.curio.observer.hud;

import dev.curio.observer.config.ObserverConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** A small card telling viewers which presentation aids are on: adaptive camera and dark-area brightening. */
public final class ViewModeOverlay {
    static final int WIDTH = 132;
    static final int PADDING = 4;
    static final int ROW = 11;
    private static final int MARGIN = 8;

    /** camera: null = off, otherwise whether the shot is currently third person. */
    public record Status(boolean cameraOn, boolean cameraActive, boolean thirdPerson,
                         boolean brightenOn, boolean brightenActive, double gamma, boolean shaders) {
        public boolean anything() { return cameraOn || brightenOn; }
    }

    static List<Line> lines(Status s) {
        List<Line> lines = new ArrayList<>();
        if (s.cameraOn()) {
            String state = !s.cameraActive() ? Text.translatable("curio-observer.view.standby").getString()
                    : Text.translatable(s.thirdPerson() ? "curio-observer.view.third" : "curio-observer.view.first").getString();
            lines.add(new Line(Text.translatable("curio-observer.view.camera", state).getString(), s.cameraActive() ? 0x8FD6B3 : 0xA8B9AE));
        }
        if (s.brightenOn()) {
            String gamma = String.format(Locale.ROOT, "%.1f", s.gamma());
            String text = Text.translatable("curio-observer.view.brighten", gamma).getString();
            if (!s.brightenActive()) text += Text.translatable("curio-observer.view.paused").getString();
            else if (s.shaders()) text += Text.translatable("curio-observer.view.shaders").getString();
            lines.add(new Line(text, s.brightenActive() ? 0xFFE39A : 0xA8B9AE));
        }
        return lines;
    }

    record Line(String text, int color) {}

    /** Default place: right under the interest sidebar (same right edge), or the top-right corner. */
    public void render(DrawContext context, Status status, double scale, ObserverConfig.PanelConfig panel, float[] sidebar) {
        List<Line> lines = lines(status);
        if (lines.isEmpty()) return;
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        float factor = (float) Math.max(0.25, Math.min(3, scale));
        int height = PADDING + lines.size() * ROW + 2;
        float width = WIDTH * factor;
        float defaultX = sidebar != null ? sidebar[0] + sidebar[2] - width : screenWidth - MARGIN - width;
        float defaultY = sidebar != null ? sidebar[1] + sidebar[3] + 4 : MARGIN;
        HudPlacement.Point at = HudPlacement.place(panel, defaultX, defaultY, width, height * factor, screenWidth, screenHeight);
        var renderer = MinecraftClient.getInstance().textRenderer;
        context.getMatrices().push();
        try {
            context.getMatrices().translate(at.x(), at.y(), 350);
            context.getMatrices().scale(factor, factor, 1);
            context.fill(0, 0, WIDTH, height, 0xB0101418);
            context.fill(0, 0, 2, height, 0xFF82B1DB);
            int y = PADDING;
            for (Line line : lines) {
                context.drawText(renderer, HudText.clip(renderer, line.text(), WIDTH - PADDING * 2 - 2), PADDING + 2, y,
                        0xFF000000 | line.color(), false);
                y += ROW;
            }
        } finally {
            context.getMatrices().pop();
        }
    }
}
