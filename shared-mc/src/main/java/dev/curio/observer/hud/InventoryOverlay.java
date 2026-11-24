package dev.curio.observer.hud;

import dev.curio.observer.compat.HudCanvas;
import dev.curio.observer.compat.ItemCodec;
import dev.curio.observer.config.ObserverConfig;
import dev.curio.observer.config.ObserverText;
import dev.curio.observer.model.Protocol;
import dev.curio.observer.state.BotState;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** The bot's complete inventory (armor, offhand, 27 storage slots and the hotbar) as a read-only panel. */
public final class InventoryOverlay {
    static final int SLOT = 18;
    static final int PADDING = 4;
    static final int WIDTH = PADDING * 2 + SLOT * 9;
    static final int HEIGHT = 14 + SLOT + 4 + SLOT * 3 + 4 + SLOT + PADDING;
    private static final int MARGIN = 8;

    public static float scale(double hudScale) {
        return (float) Math.max(0.3, Math.min(1.8, hudScale * 0.6));
    }

    public float[] render(HudCanvas canvas, BotState state, double hudScale, ObserverConfig.PanelConfig panel) {
        int screenWidth = canvas.width();
        int screenHeight = canvas.height();
        float factor = Math.min(scale(hudScale), Math.min((screenWidth - MARGIN * 2) / (float) WIDTH,
                (screenHeight - MARGIN * 2) / (float) HEIGHT));
        if (factor <= 0) return null;
        HudPlacement.Point at = HudPlacement.place(panel, (screenWidth - WIDTH * factor) / 2, MARGIN,
                WIDTH * factor, HEIGHT * factor, screenWidth, screenHeight);
        var font = Minecraft.getInstance().font;
        canvas.push();
        canvas.enableBlend();
        try {
            canvas.translate(at.x(), at.y(), 300);
            canvas.scale(factor, factor);
            canvas.fill(0, 0, WIDTH, HEIGHT, 0xB0101418);
            canvas.fill(0, 0, WIDTH, 1, 0xFF8FD6B3);
            int used = 0;
            for (Protocol.ItemData item : state.main()) if (item != null) used++;
            for (Protocol.ItemData item : state.hotbar()) if (item != null) used++;
            String bot = state.hello() == null ? "Curio" : state.hello().bot();
            String title = ObserverText.text("curio-observer.inventory.title", bot).getString();
            String count = ObserverText.text("curio-observer.inventory.used", used, 36).getString();
            int countWidth = font.width(count);
            canvas.drawString(font, HudText.clip(font, title, WIDTH - PADDING * 3 - countWidth), PADDING, 4, 0xFFE2EEE6, true);
            canvas.drawString(font, count, WIDTH - PADDING - countWidth, 4, 0xFFA8B9AE, false);

            int y = 14;
            List<Protocol.ItemData> armor = state.armor();
            for (int i = 0; i < 4; i++) slot(canvas, i < armor.size() ? armor.get(i) : null, PADDING + i * SLOT, y, false);
            slot(canvas, state.offhand(), PADDING + 5 * SLOT, y, false);
            y += SLOT + 4;
            List<Protocol.ItemData> main = state.main();
            for (int i = 0; i < 27; i++) {
                slot(canvas, i < main.size() ? main.get(i) : null, PADDING + i % 9 * SLOT, y + i / 9 * SLOT, false);
            }
            y += SLOT * 3 + 4;
            List<Protocol.ItemData> hotbar = state.hotbar();
            for (int i = 0; i < 9; i++) {
                slot(canvas, i < hotbar.size() ? hotbar.get(i) : null, PADDING + i * SLOT, y, i == state.selected());
            }
        } finally {
            canvas.disableBlend();
            canvas.pop();
        }
        return new float[] {at.x(), at.y(), WIDTH * factor, HEIGHT * factor};
    }

    private static void slot(HudCanvas canvas, Protocol.ItemData data, int x, int y, boolean selected) {
        canvas.fill(x, y, x + SLOT, y + SLOT, selected ? 0xFFE8E8E8 : 0xFF373737);
        canvas.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, selected ? 0xFF6B6B6B : 0xFF8B8B8B);
        canvas.fill(x + 1, y + 1, x + SLOT - 2, y + SLOT - 2, selected ? 0xFF5A5A5A : 0xFF2A2F33);
        ItemStack stack = ItemCodec.stack(data);
        if (stack.isEmpty()) return;
        canvas.drawItem(stack, x + 1, y + 1);
        canvas.drawItemDecorations(Minecraft.getInstance().font, stack, x + 1, y + 1);
    }
}
