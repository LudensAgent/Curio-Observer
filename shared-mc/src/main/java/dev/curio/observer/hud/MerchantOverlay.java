package dev.curio.observer.hud;

import dev.curio.observer.compat.HudCanvas;
import dev.curio.observer.compat.ItemCodec;
import dev.curio.observer.config.ObserverText;
import dev.curio.observer.model.Protocol;
import dev.curio.observer.state.BotState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** Read-only rendition of the merchant's seven visible trade offers. */
final class MerchantOverlay {
    private static final int VISIBLE_OFFERS = 7;
    private static final int[] NEXT_LEVEL_XP_THRESHOLDS = {0, 10, 70, 150, 250};

    private MerchantOverlay() {}

    static void render(HudCanvas canvas, BotState.Window window, float fade) {
        if (!"merchant".equals(window.kind()) || window.merchant() == null || fade <= 0.025F) return;
        Protocol.MerchantData merchant = window.merchant();
        var offers = merchant.offers();
        int selected = merchant.selected() == null ? -1 : merchant.selected();
        int start = Math.max(0, Math.min(Math.max(0, offers.size() - VISIBLE_OFFERS), selected - VISIBLE_OFFERS + 1));
        var font = Minecraft.getInstance().font;
        canvas.drawString(font, ObserverText.text("merchant.trades"), 5, 6,
                ((int) (255 * fade) << 24) | 0x404040, false);

        int scroll = offers.size() <= VISIBLE_OFFERS ? 0 : (int) Math.round(113.0 * start / (offers.size() - VISIBLE_OFFERS));
        canvas.drawMerchantScroller(94, 18 + scroll, offers.size() > VISIBLE_OFFERS);
        for (int row = 0; row < VISIBLE_OFFERS && start + row < offers.size(); row++) {
            int index = start + row;
            var offer = offers.get(index);
            int y = 18 + row * 20;
            canvas.drawMerchantButton(5, y, index == selected);
            ItemStack first = ItemCodec.stack(offer.input1());
            ItemStack second = ItemCodec.stack(offer.input2());
            ItemStack output = ItemCodec.stack(offer.output());
            canvas.drawFakeItem(first, 10, y + 1);
            canvas.drawItemDecorations(font, first, 10, y + 1);
            if (!second.isEmpty()) {
                canvas.drawFakeItem(second, 40, y + 1);
                canvas.drawItemDecorations(font, second, 40, y + 1);
            }
            canvas.drawMerchantArrow(60, y + 4, offer.disabled());
            canvas.drawFakeItem(output, 73, y + 1);
            canvas.drawItemDecorations(font, output, 73, y + 1);
        }
        if (selected >= 0 && selected < offers.size() && offers.get(selected).disabled()) {
            canvas.drawMerchantCross(182, 35);
        }
        Integer level = merchant.level();
        Integer experience = merchant.experience();
        if (Boolean.TRUE.equals(merchant.regular()) && level != null && level >= 1 && level < 5) {
            canvas.drawMerchantXpBg(136, 16);
            if (experience != null) {
                int lower = NEXT_LEVEL_XP_THRESHOLDS[level - 1];
                int upper = NEXT_LEVEL_XP_THRESHOLDS[level];
                int progress = Math.max(0, Math.min(100, (int) (100.0 * (experience - lower) / (upper - lower))));
                canvas.drawMerchantXpFill(136, 16, progress + 1);
            }
        }
    }
}
