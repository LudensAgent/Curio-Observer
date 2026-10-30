package dev.curio.observer.hud;

import net.minecraft.client.font.TextRenderer;

/** Shared text helpers for HUD cards. */
public final class HudText {
    private HudText() {}

    static String clip(TextRenderer renderer, String value, int width) {
        String line = value.replace('\n', ' ').replace('\r', ' ');
        if (renderer.getWidth(line) <= width) return line;
        int ellipsis = renderer.getWidth("…");
        return width < ellipsis ? "" : renderer.trimToWidth(line, width - ellipsis) + "…";
    }
}
