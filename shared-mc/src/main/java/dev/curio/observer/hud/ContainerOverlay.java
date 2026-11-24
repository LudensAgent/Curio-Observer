package dev.curio.observer.hud;

import dev.curio.observer.compat.HudCanvas;
import dev.curio.observer.compat.ItemCodec;
import dev.curio.observer.compat.McCompatImpl;
import dev.curio.observer.config.ObserverConfig;
import dev.curio.observer.config.ObserverText;
import dev.curio.observer.hud.ContainerLayout.Slot;
import dev.curio.observer.model.Protocol;
import dev.curio.observer.state.BotState;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** A display-only container. It never opens a Screen or sends inventory packets. */
public final class ContainerOverlay {
    private static final long SLOT_PULSE_MS = 240;
    private final InventoryPointer pointer = new InventoryPointer();
    private String layoutKind;
    private int layoutSlotCount = -1;
    private ContainerLayout cachedLayout;

    public void render(HudCanvas canvas, BotState state, float tickDelta, double scale, ObserverConfig.PanelConfig panel) {
        BotState.Window window = state.window();
        long now = System.currentTimeMillis();
        if (window == null || !window.visible(now)) {
            reset();
            return;
        }
        if (!window.kind().equals(layoutKind) || window.slots().size() != layoutSlotCount) {
            layoutKind = window.kind();
            layoutSlotCount = window.slots().size();
            cachedLayout = ContainerLayout.forWindow(layoutKind, layoutSlotCount);
        }
        ContainerLayout layout = cachedLayout;
        float fade = window.open() ? 1.0F
                : Mth.clamp(1.0F - (now - window.closedAt()) / (float) BotState.Window.CLOSE_FADE_MS, 0, 1);
        float requestedScale = (float) (Double.isFinite(scale) ? Mth.clamp(scale, 0.5, 3.0) : 1.0);
        int screenWidth = canvas.width();
        int screenHeight = canvas.height();
        int availableWidth = Math.max(1, screenWidth - 16);
        int availableHeight = Math.max(1, screenHeight - 16);
        float factor = Math.min(requestedScale, Math.min(
                availableWidth / (float) layout.width(),
                availableHeight / (float) (layout.height() + 26)));
        float panelWidth = layout.width() * factor;
        float panelHeight = (layout.height() + 26) * factor;
        HudPlacement.Point at = HudPlacement.place(panel, (screenWidth - panelWidth) / 2,
                (screenHeight - panelHeight) / 2, panelWidth, panelHeight, screenWidth, screenHeight);

        canvas.fill(0, 0, canvas.width(), canvas.height(), ((int) (110 * fade) << 24));
        canvas.push();
        canvas.translate(at.x(), at.y(), 150);
        canvas.scale(factor, factor);
        canvas.enableBlend();
        canvas.setAlpha(fade);
        try {
            drawBackground(canvas, layout);
            canvas.drawPlayerPreview(layout, state, tickDelta, fade);
            drawFurnace(canvas, layout, window, now, fade);
            drawBrewing(canvas, layout, window);
            drawEnchanting(canvas, layout, window, state, now, fade);
            drawAnvil(canvas, layout, window, state, fade);
            if (layout.background() == ContainerLayout.Background.GRINDSTONE && window.slots().size() >= 3
                    && (window.slots().get(0) != null || window.slots().get(1) != null) && window.slots().get(2) == null) {
                canvas.drawGrindstoneError(92, 31);
            }
            MerchantOverlay.render(canvas, window, fade);
            drawLabels(canvas, layout, window.title(), fade);
            for (Slot slot : layout.slots()) {
                long changedAt = window.slotChangedAt(slot.index());
                float pulse = changedAt == 0 ? 0 : Mth.clamp(1.0F - (now - changedAt) / (float) SLOT_PULSE_MS, 0, 1);
                if (pulse > 0) {
                    canvas.fill(slot.x() - 1, slot.y() - 1, slot.x() + 17, slot.y() + 17,
                            ((int) (90 * pulse * fade) << 24) | 0xFFFFFF);
                }
                Protocol.ItemData item = slot.index() < window.slots().size()
                        ? window.slots().get(slot.index()) : playerItem(state, slot.playerSlot());
                drawSlot(canvas, item, slot.x(), slot.y(), pulse);
            }
            drawCarriedItem(canvas, layout, window, state.cursor(), now);
            drawTransfer(canvas, layout, window, now, fade);
        } finally {
            canvas.setAlpha(1.0F);
            canvas.disableBlend();
            canvas.pop();
        }
    }

    public void reset() {
        pointer.reset();
    }

    private void drawBackground(HudCanvas canvas, ContainerLayout layout) {
        if (layout.storageRows() == 0) {
            int offset = layout.backgroundX();
            if (offset > 0) {
                canvas.fill(0, 0, offset, layout.height(), 0xFF555555);
                canvas.fill(1, 1, offset, layout.height() - 1, 0xFFFFFFFF);
                canvas.fill(3, 3, offset, layout.height() - 3, 0xFFC6C6C6);
            }
            canvas.drawContainerBackground(layout.background(), offset, 0, layout.width() - offset, layout.height());
            return;
        }
        canvas.drawGenericChestSlice(0, 0, 0, 176, 17);
        for (int row = 0; row < layout.storageRows(); row++) {
            canvas.drawGenericChestSlice(0, 17 + row * 18, 17, 176, 18);
        }
        canvas.drawGenericChestSlice(0, 17 + layout.storageRows() * 18, 126, 176, 96);
    }

    private void drawFurnace(HudCanvas canvas, ContainerLayout layout, BotState.Window window, long now, float fade) {
        if (layout.background() != ContainerLayout.Background.FURNACE
                && layout.background() != ContainerLayout.Background.BLAST_FURNACE
                && layout.background() != ContainerLayout.Background.SMOKER) return;
        FurnaceProgress.Frame frame = FurnaceProgress.sample(window, now);
        if (frame.burning() && frame.fuelFraction() > 0) {
            int height = Mth.clamp(Mth.ceil(frame.fuelFraction() * 13), 1, 13);
            float flicker = 0.91F + 0.09F * Mth.sin(now % 10_000 / 95.0F);
            canvas.setAlpha(fade * flicker);
            canvas.drawFurnaceLit(layout.background(), 56, 36, height + 1);
            canvas.setAlpha(fade);
        }
        int width = Mth.clamp(Mth.floor(frame.cookFraction() * 24), 0, 24);
        if (width > 0) canvas.drawFurnaceBurn(layout.background(), 79, 34, width);
        if (frame.cookKnown() && window.furnace() != null && fade > 0.025F) {
            int totalTicks = window.furnace().data().cookTimeTotal();
            String elapsed = String.format(Locale.ROOT, "%.1f", Math.max(0, totalTicks - frame.remainingCookTicks()) / 20.0F);
            String total = String.format(Locale.ROOT, "%.1f", totalTicks / 20.0F);
            Component time = ObserverText.text("curio-observer.furnace.time", elapsed, total);
            var font = Minecraft.getInstance().font;
            canvas.drawString(font, font.plainSubstrByWidth(time.getString(), 89), 79, 57,
                    ((int) (255 * fade) << 24) | 0x404040, false);
        }
    }

    private void drawBrewing(HudCanvas canvas, ContainerLayout layout, BotState.Window window) {
        if (layout.background() != ContainerLayout.Background.BREWING_STAND || window.brewing() == null) return;
        var brewing = window.brewing();
        if (brewing.fuel() != null) {
            int width = Mth.clamp((18 * brewing.fuel() + 19) / 20, 0, 18);
            if (width > 0) canvas.drawBrewingFuel(60, 44, width);
        }
        if (brewing.brewTime() != null && brewing.brewTime() > 0) {
            int time = brewing.brewTime();
            int height = Mth.clamp((int) (28 * (1 - time / 400.0F)), 0, 28);
            if (height > 0) canvas.drawBrewingProgress(97, 16, height);
            int bubbles = new int[] {29, 24, 20, 16, 11, 6, 0}[time / 2 % 7];
            if (bubbles > 0) canvas.drawBrewingBubbles(63, 14, bubbles);
        }
    }

    private void drawEnchanting(HudCanvas canvas, ContainerLayout layout, BotState.Window window, BotState state,
                                long now, float fade) {
        if (layout.background() != ContainerLayout.Background.ENCHANTING_TABLE || window.enchanting() == null) return;
        var font = Minecraft.getInstance().font;
        int alpha = (int) (255 * fade) << 24;
        boolean text = fade > 0.025F;
        Protocol.ItemData lapisStack = window.slots().size() > 1 ? window.slots().get(1) : null;
        int lapis = lapisStack != null && lapisStack.id().endsWith(":lapis_lazuli") ? lapisStack.count() : 0;
        int level = state.xp().level();
        BotState.Enchanting enchanting = window.enchanting();
        for (int option = 0; option < Protocol.ENCHANT_OPTIONS; option++) {
            Protocol.EnchantOption offer = enchanting.data().options().get(option);
            int x = 60;
            int y = 14 + option * 19;
            if (!offer.offered()) {
                canvas.drawEnchantSlot(x, y, false, false, false);
                continue;
            }
            boolean chosen = enchanting.recentlyChosen(option, now);
            boolean affordable = lapis >= option + 1 && level >= offer.cost();
            canvas.drawEnchantSlot(x, y, true, affordable, chosen);
            canvas.drawEnchantLevel(x + 1, y + 1, option, affordable || chosen);
            if (!text) continue;
            String cost = Integer.toString(offer.cost());
            int hintWidth = 86 - font.width(cost);
            String hint = font.plainSubstrByWidth(enchantmentHint(offer), hintWidth);
            int hintColor = chosen ? 0xFFFF80 : affordable ? 0x685E4A : (0x685E4A & 0xFEFEFE) >> 1;
            canvas.drawString(font, hint, x + 20, y + 2, alpha | hintColor, false);
            canvas.drawString(font, cost, x + 20 + 86 - font.width(cost), y + 2 + 7,
                    alpha | (affordable || chosen ? 0x80FF20 : 0x407F10), true);
        }
        if (!text) return;
        int color = alpha | 0x404040;
        canvas.drawString(font, font.plainSubstrByWidth(ObserverText.text("curio-observer.enchant.level", level).getString(), 48), 9, 15, color, false);
        canvas.drawString(font, font.plainSubstrByWidth(ObserverText.text("curio-observer.enchant.lapis", lapis).getString(), 48), 9, 27, color, false);
    }

    private static String enchantmentHint(Protocol.EnchantOption offer) {
        if (offer.enchant() == null) return "";
        return McCompatImpl.enchantmentName(offer.enchant(), offer.level() == null ? 1 : offer.level());
    }

    private void drawAnvil(HudCanvas canvas, ContainerLayout layout, BotState.Window window, BotState state, float fade) {
        if (layout.background() != ContainerLayout.Background.ANVIL || window.anvil() == null) return;
        var font = Minecraft.getInstance().font;
        int alpha = (int) (255 * fade) << 24;
        Protocol.ItemData input = window.slots().isEmpty() ? null : window.slots().get(0);
        Protocol.ItemData output = window.slots().size() > 2 ? window.slots().get(2) : null;
        canvas.drawAnvilTextField(59, 20, input != null);
        if (input != null && output == null) canvas.drawAnvilError(99, 45);
        if (fade <= 0.025F) return;
        String name = window.anvil().name();
        if (name == null && input != null) name = ItemCodec.stack(input).getHoverName().getString();
        if (name != null && !name.isBlank()) {
            canvas.drawString(font, font.plainSubstrByWidth(name, 103), 62, 24, alpha | 0xE0E0E0, true);
        }
        Integer cost = window.anvil().cost();
        if (cost == null || cost <= 0) return;
        boolean expensive = cost >= 40;
        String label = (expensive ? ObserverText.text("container.repair.expensive") : ObserverText.text("container.repair.cost", cost)).getString();
        int color = expensive || state.xp().level() < cost ? 0xFF6060 : 0x80FF20;
        int x = layout.width() - 8 - font.width(label) - 2;
        canvas.fill(x - 2, 67, layout.width() - 8, 79, ((int) (0x4F * fade) << 24));
        canvas.drawString(font, label, x, 69, alpha | color, true);
    }

    private void drawLabels(HudCanvas canvas, ContainerLayout layout, String title, float fade) {
        var font = Minecraft.getInstance().font;
        int color = ((int) (255 * fade) << 24) | 0x404040;
        if (fade <= 0.025F) return;
        String caption = title.isBlank() ? ObserverText.text(layout.titleKey()).getString()
                : title.startsWith("container.") && !title.contains(" ") ? ObserverText.text(title).getString() : title;
        caption = font.plainSubstrByWidth(caption, layout.width() - layout.titleX() - 8);
        int titleX = (layout.background() == ContainerLayout.Background.FURNACE
                || layout.background() == ContainerLayout.Background.BLAST_FURNACE
                || layout.background() == ContainerLayout.Background.SMOKER)
                ? (layout.width() - font.width(caption)) / 2 : layout.titleX();
        boolean inventory = layout.background() == ContainerLayout.Background.INVENTORY;
        canvas.drawString(font, caption, titleX, inventory ? 8 : 6, color, false);
        if (!inventory) {
            canvas.drawString(font, ObserverText.text("container.inventory"), layout.inventoryX(),
                    layout.inventoryY() - (layout.storageRows() > 0 ? 11 : 12), color, false);
        }
    }

    private void drawSlot(HudCanvas canvas, Protocol.ItemData data, int x, int y, float pulse) {
        ItemStack stack = ItemCodec.stack(data);
        if (stack.isEmpty()) return;
        canvas.push();
        try {
            float zoom = 1 + pulse * 0.12F;
            canvas.translate(x + 8, y + 8, 0);
            canvas.scale(zoom, zoom);
            canvas.drawItem(stack, -8, -8);
            canvas.drawItemDecorations(Minecraft.getInstance().font, stack, -8, -8);
        } finally {
            canvas.pop();
        }
    }

    private void drawCarriedItem(HudCanvas canvas, ContainerLayout layout, BotState.Window window,
                                 Protocol.ItemData current, long now) {
        var frame = pointer.sample(layout, window, current, now);
        if (!frame.visible() || frame.item() == null) return;
        int x = Math.round(frame.x()), y = Math.round(frame.y());
        canvas.push();
        canvas.translate(0, 0, 300);
        try {
            ItemStack stack = ItemCodec.stack(frame.item());
            canvas.drawItem(stack, x + 4, y + 4);
            canvas.drawItemDecorations(Minecraft.getInstance().font, stack, x + 4, y + 4,
                    Integer.toString(frame.item().count()));
        } finally {
            canvas.pop();
        }
    }

    private void drawTransfer(HudCanvas canvas, ContainerLayout layout, BotState.Window window, long now, float fade) {
        var transfer = window.transfer();
        if (transfer == null || now - transfer.receivedAt() >= BotState.Window.TRANSFER_LIFETIME_MS || fade <= 0.025F) return;
        var data = transfer.data();
        var font = Minecraft.getInstance().font;
        ItemStack stack = ItemCodec.stack(data.item());
        String text = ObserverText.text("curio-observer.transfer." + data.kind(), data.item().count(), stack.getHoverName()).getString();
        text = font.plainSubstrByWidth(text, layout.width() - 24);
        canvas.fill(0, layout.height() + 2, layout.width(), layout.height() + 24, ((int) (210 * fade) << 24));
        canvas.drawItem(stack, 3, layout.height() + 5);
        canvas.drawString(font, text, 23, layout.height() + 9, ((int) (255 * fade) << 24) | 0xFFFFFF, true);
        for (var departure : InventoryTransfers.departures(layout, window, now)) {
            ItemStack dropped = ItemCodec.stack(departure.item());
            int x = Math.round(departure.x()), y = Math.round(departure.y());
            canvas.push();
            canvas.translate(0, 0, 300);
            canvas.drawItem(dropped, x, y);
            canvas.drawItemDecorations(font, dropped, x, y, Integer.toString(departure.item().count()));
            canvas.pop();
        }
    }

    private static Protocol.ItemData playerItem(BotState state, int playerSlot) {
        if (playerSlot >= 5 && playerSlot <= 8) return state.armor().get(playerSlot - 5);
        if (playerSlot >= 9 && playerSlot <= 35) return state.main().get(playerSlot - 9);
        if (playerSlot >= 36 && playerSlot <= 44) return state.hotbar().get(playerSlot - 36);
        return playerSlot == 45 ? state.offhand() : null;
    }
}
