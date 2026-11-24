package dev.curio.observer.hud;

import dev.curio.observer.config.ObserverConfig;

/** Resolves a dashboard-positioned panel's top-left corner in scaled screen pixels, kept fully on screen. */
public final class HudPlacement {
    public record Point(float x, float y) {}

    private HudPlacement() {}

    public static boolean custom(ObserverConfig.PanelConfig panel) {
        return panel != null && (panel.x != null || panel.y != null);
    }

    /** width/height are the panel's on-screen size; the default place is used for any axis left unset. */
    public static Point place(ObserverConfig.PanelConfig panel, float defaultX, float defaultY, float width, float height,
                              int screenWidth, int screenHeight) {
        float x = panel != null && panel.x != null ? (float) (panel.x * screenWidth) : defaultX;
        float y = panel != null && panel.y != null ? (float) (panel.y * screenHeight) : defaultY;
        return new Point(clamp(x, screenWidth - width), clamp(y, screenHeight - height));
    }

    private static float clamp(float value, float maximum) {
        return Math.max(0, Math.min(Math.max(0, maximum), value));
    }
}
