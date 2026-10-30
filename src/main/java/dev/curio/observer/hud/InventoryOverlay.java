package dev.curio.observer.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.curio.observer.config.ObserverConfig;
import dev.curio.observer.model.Protocol;
import dev.curio.observer.state.BotState;
import dev.curio.observer.state.ItemCodec;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import java.util.List;

/** The bot's complete inventory (armor, offhand, 27 storage slots and the hotbar) as a read-only panel. */
public final class InventoryOverlay {
    static final int SLOT = 18;
    static final int PADDING = 4;
    static final int WIDTH = PADDING * 2 + SLOT * 9;
    static final int HEIGHT = 14 + SLOT + 4 + SLOT * 3 + 4 + SLOT + PADDING;
    private static final int MARGIN = 8;

    /** Compact: about 60% of the item-slot size so it fits between the status card and the sidebar. */
    public static float scale(double hudScale) {
        return (float) Math.max(0.3, Math.min(1.8, hudScale * 0.6));
    }

    /** Returns the panel's bounds in screen pixels (x, y, width, height), or null when nothing was drawn. */
    public float[] render(DrawContext context, BotState state, double hudScale, ObserverConfig.PanelConfig panel) {
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        float factor = Math.min(scale(hudScale), Math.min((screenWidth - MARGIN * 2) / (float) WIDTH,
                (screenHeight - MARGIN * 2) / (float) HEIGHT));
        if (factor <= 0) return null;
        // Default: top centre, between the status card and the interest sidebar. Livestream platforms may blur
        // the bottom-left corner, so the panel no longer sits there.
        HudPlacement.Point at = HudPlacement.place(panel, (screenWidth - WIDTH * factor) / 2, MARGIN,
                WIDTH * factor, HEIGHT * factor, screenWidth, screenHeight);
        var renderer = MinecraftClient.getInstance().textRenderer;
        context.getMatrices().push();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            context.getMatrices().translate(at.x(), at.y(), 300);
            context.getMatrices().scale(factor, factor, 1);
            context.fill(0, 0, WIDTH, HEIGHT, 0xB0101418);
            context.fill(0, 0, WIDTH, 1, 0xFF8FD6B3);
            int used = 0;
            for (Protocol.ItemData item : state.main()) if (item != null) used++;
            for (Protocol.ItemData item : state.hotbar()) if (item != null) used++;
            String bot = state.hello() == null ? "Curio" : state.hello().bot();
            String title = Text.translatable("curio-observer.inventory.title", bot).getString();
            String count = Text.translatable("curio-observer.inventory.used", used, 36).getString();
            int countWidth = renderer.getWidth(count);
            context.drawText(renderer, HudText.clip(renderer, title, WIDTH - PADDING * 3 - countWidth), PADDING, 4, 0xFFE2EEE6, true);
            context.drawText(renderer, count, WIDTH - PADDING - countWidth, 4, 0xFFA8B9AE, false);

            int y = 14;
            // Armor is sent head..feet; show it left to right, then the offhand after a gap.
            List<Protocol.ItemData> armor = state.armor();
            for (int i = 0; i < 4; i++) slot(context, i < armor.size() ? armor.get(i) : null, PADDING + i * SLOT, y, false);
            slot(context, state.offhand(), PADDING + 5 * SLOT, y, false);
            y += SLOT + 4;
            List<Protocol.ItemData> main = state.main();
            for (int i = 0; i < 27; i++) {
                slot(context, i < main.size() ? main.get(i) : null, PADDING + i % 9 * SLOT, y + i / 9 * SLOT, false);
            }
            y += SLOT * 3 + 4;
            List<Protocol.ItemData> hotbar = state.hotbar();
            for (int i = 0; i < 9; i++) {
                slot(context, i < hotbar.size() ? hotbar.get(i) : null, PADDING + i * SLOT, y, i == state.selected());
            }
        } finally {
            RenderSystem.disableBlend();
            context.getMatrices().pop();
        }
        return new float[] {at.x(), at.y(), WIDTH * factor, HEIGHT * factor};
    }

    private static void slot(DrawContext context, Protocol.ItemData data, int x, int y, boolean selected) {
        context.fill(x, y, x + SLOT, y + SLOT, selected ? 0xFFE8E8E8 : 0xFF373737);
        context.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, selected ? 0xFF6B6B6B : 0xFF8B8B8B);
        context.fill(x + 1, y + 1, x + SLOT - 2, y + SLOT - 2, selected ? 0xFF5A5A5A : 0xFF2A2F33);
        ItemStack stack = ItemCodec.stack(data);
        if (stack.isEmpty()) return;
        context.drawItem(stack, x + 1, y + 1);
        context.drawItemInSlot(MinecraftClient.getInstance().textRenderer, stack, x + 1, y + 1);
    }
}
