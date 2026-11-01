package dev.curio.observer.hud;

import dev.curio.observer.config.ObserverText;

import dev.curio.observer.model.Protocol;
import dev.curio.observer.state.BotState;
import dev.curio.observer.state.ItemCodec;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.village.VillagerData;

/** Read-only rendition of the 1.20.1 merchant's seven visible trade offers. */
final class MerchantOverlay {
    private static final Identifier TEXTURE = new Identifier("minecraft", "textures/gui/container/villager2.png");
    private static final int VISIBLE_OFFERS = 7;

    private MerchantOverlay() {}

    static void render(DrawContext context, BotState.Window window, float fade) {
        if (!"merchant".equals(window.kind()) || window.merchant() == null || fade <= 0.025F) return;
        Protocol.MerchantData merchant = window.merchant();
        var offers = merchant.offers();
        int selected = merchant.selected() == null ? -1 : merchant.selected();
        // The bot reports a choice rather than a mouse-wheel position. Keep that
        // choice visible when it falls below the first page of seven offers.
        int start = Math.max(0, Math.min(Math.max(0, offers.size() - VISIBLE_OFFERS), selected - VISIBLE_OFFERS + 1));
        var renderer = MinecraftClient.getInstance().textRenderer;
        context.drawText(renderer, ObserverText.text("merchant.trades"), 5, 6,
                ((int) (255 * fade) << 24) | 0x404040, false);

        int scroll = offers.size() <= VISIBLE_OFFERS ? 0 : (int) Math.round(113.0 * start / (offers.size() - VISIBLE_OFFERS));
        context.drawTexture(TEXTURE, 94, 18 + scroll, 0, offers.size() > VISIBLE_OFFERS ? 0.0F : 6.0F,
                199.0F, 6, 27, 512, 256);
        for (int row = 0; row < VISIBLE_OFFERS && start + row < offers.size(); row++) {
            int index = start + row;
            var offer = offers.get(index);
            int y = 18 + row * 20;
            int textureY = index == selected ? 86 : 66;
            context.drawTexture(HudTextures.WIDGETS, 5, y, 0, textureY, 44, 20);
            context.drawTexture(HudTextures.WIDGETS, 49, y, 156, textureY, 44, 20);
            ItemStack first = ItemCodec.stack(offer.input1());
            ItemStack second = ItemCodec.stack(offer.input2());
            ItemStack output = ItemCodec.stack(offer.output());
            context.drawItemWithoutEntity(first, 10, y + 1);
            context.drawItemInSlot(renderer, first, 10, y + 1);
            if (!second.isEmpty()) {
                context.drawItemWithoutEntity(second, 40, y + 1);
                context.drawItemInSlot(renderer, second, 40, y + 1);
            }
            context.drawTexture(TEXTURE, 60, y + 4, 0, offer.disabled() ? 25.0F : 15.0F,
                    171.0F, 10, 9, 512, 256);
            context.drawItemWithoutEntity(output, 73, y + 1);
            context.drawItemInSlot(renderer, output, 73, y + 1);
        }
        if (selected >= 0 && selected < offers.size() && offers.get(selected).disabled()) {
            context.drawTexture(TEXTURE, 182, 35, 0, 311.0F, 0.0F, 28, 21, 512, 256);
        }
        Integer level = merchant.level();
        Integer experience = merchant.experience();
        if (Boolean.TRUE.equals(merchant.regular()) && level != null && level >= 1 && level < 5) {
            context.drawTexture(TEXTURE, 136, 16, 0, 0.0F, 186.0F, 102, 5, 512, 256);
            if (experience != null) {
                int lower = VillagerData.getLowerLevelExperience(level);
                int upper = VillagerData.getUpperLevelExperience(level);
                int progress = Math.max(0, Math.min(100, (int) (100.0 * (experience - lower) / (upper - lower))));
                context.drawTexture(TEXTURE, 136, 16, 0, 0.0F, 191.0F, progress + 1, 5, 512, 256);
            }
        }
    }
}
