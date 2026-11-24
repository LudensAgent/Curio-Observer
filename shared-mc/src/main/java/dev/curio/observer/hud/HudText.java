package dev.curio.observer.hud;

import net.minecraft.client.gui.Font;

/** Shared text helpers for HUD cards. */
public final class HudText {
    private HudText() {}

    static String clip(Font font, String value, int width) {
        String line = value.replace('\n', ' ').replace('\r', ' ');
        if (font.width(line) <= width) return line;
        int ellipsis = font.width("…");
        return width < ellipsis ? "" : font.plainSubstrByWidth(line, width - ellipsis) + "…";
    }
}
