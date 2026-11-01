package dev.curio.observer.hud;

import dev.curio.observer.config.ObserverText;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.curio.observer.config.ObserverConfig;
import dev.curio.observer.hud.ContainerLayout.Slot;
import dev.curio.observer.model.Protocol;
import dev.curio.observer.state.BotState;
import dev.curio.observer.state.ItemCodec;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import java.util.Locale;

/** A display-only container. It never opens a Screen or sends inventory packets. */
public final class ContainerOverlay {
    private static final long SLOT_PULSE_MS = 240;
    private final InventoryPointer pointer = new InventoryPointer();
    private String layoutKind;
    private int layoutSlotCount = -1;
    private ContainerLayout cachedLayout;

    public void render(DrawContext context, BotState state, float tickDelta, double scale, ObserverConfig.PanelConfig panel) {
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
                : MathHelper.clamp(1.0F - (now - window.closedAt()) / (float) BotState.Window.CLOSE_FADE_MS, 0, 1);
        float requestedScale = (float) (Double.isFinite(scale) ? MathHelper.clamp(scale, 0.5, 3.0) : 1.0);
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        int availableWidth = Math.max(1, screenWidth - 16);
        int availableHeight = Math.max(1, screenHeight - 16);
        // Keep tall containers inside the visible window, including on small GUI sizes.
        float factor = Math.min(requestedScale, Math.min(
                availableWidth / (float) layout.width(),
                availableHeight / (float) (layout.height() + 26)));
        float panelWidth = layout.width() * factor;
        float panelHeight = (layout.height() + 26) * factor;
        HudPlacement.Point at = HudPlacement.place(panel, (screenWidth - panelWidth) / 2,
                (screenHeight - panelHeight) / 2, panelWidth, panelHeight, screenWidth, screenHeight);

        context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(),
                ((int) (110 * fade) << 24));
        context.getMatrices().push();
        context.getMatrices().translate(at.x(), at.y(), 150);
        context.getMatrices().scale(factor, factor, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        context.setShaderColor(1, 1, 1, fade);
        try {
            drawBackground(context, layout);
            ContainerPlayerPreview.render(context, layout, state, tickDelta, fade);
            drawFurnace(context, layout, window, now, fade);
            drawBrewing(context, layout, window);
            drawEnchanting(context, layout, window, state, now, fade);
            drawAnvil(context, layout, window, state, fade);
            if (layout.background() == ContainerLayout.Background.GRINDSTONE && window.slots().size() >= 3
                    && (window.slots().get(0) != null || window.slots().get(1) != null) && window.slots().get(2) == null) {
                context.drawTexture(HudTextures.GRINDSTONE, 92, 31, 176, 0, 28, 21);
            }
            MerchantOverlay.render(context, window, fade);
            drawLabels(context, layout, window.title(), fade);
            for (Slot slot : layout.slots()) {
                long changedAt = window.slotChangedAt(slot.index());
                float pulse = changedAt == 0 ? 0 : MathHelper.clamp(1.0F - (now - changedAt) / (float) SLOT_PULSE_MS, 0, 1);
                if (pulse > 0) {
                    context.fill(slot.x() - 1, slot.y() - 1, slot.x() + 17, slot.y() + 17,
                            ((int) (90 * pulse * fade) << 24) | 0xFFFFFF);
                }
                Protocol.ItemData item = slot.index() < window.slots().size()
                        ? window.slots().get(slot.index()) : playerItem(state, slot.playerSlot());
                drawSlot(context, item, slot.x(), slot.y(), pulse);
            }
            drawCarriedItem(context, layout, window, state.cursor(), now);
            drawTransfer(context, layout, window, now, fade);
        } finally {
            context.setShaderColor(1, 1, 1, 1);
            RenderSystem.disableBlend();
            context.getMatrices().pop();
        }
    }

    public void reset() {
        pointer.reset();
    }

    private void drawBackground(DrawContext context, ContainerLayout layout) {
        if (layout.storageRows() == 0) {
            int offset = layout.backgroundX();
            if (offset > 0) {
                context.fill(0, 0, offset, layout.height(), 0xFF555555);
                context.fill(1, 1, offset, layout.height() - 1, 0xFFFFFFFF);
                context.fill(3, 3, offset, layout.height() - 3, 0xFFC6C6C6);
            }
            if (layout.background() == ContainerLayout.Background.MERCHANT)
                context.drawTexture(HudTextures.MERCHANT, 0, 0, 0, 0.0F, 0.0F, 276, 166, 512, 256);
            else context.drawTexture(texture(layout), offset, 0, 0, 0, layout.width() - offset, layout.height());
            return;
        }
        // Repeat a storage row for unfamiliar container sizes instead of reading beyond the atlas.
        context.drawTexture(HudTextures.GENERIC_54, 0, 0, 0, 0, 176, 17);
        for (int row = 0; row < layout.storageRows(); row++) {
            context.drawTexture(HudTextures.GENERIC_54, 0, 17 + row * 18, 0, 17, 176, 18);
        }
        context.drawTexture(HudTextures.GENERIC_54, 0, 17 + layout.storageRows() * 18,
                0, 126, 176, 96);
    }

    private void drawFurnace(DrawContext context, ContainerLayout layout, BotState.Window window,
                             long now, float fade) {
        if (layout.background() != ContainerLayout.Background.FURNACE
                && layout.background() != ContainerLayout.Background.BLAST_FURNACE
                && layout.background() != ContainerLayout.Background.SMOKER) return;
        FurnaceProgress.Frame frame = FurnaceProgress.sample(window, now);
        if (frame.burning() && frame.fuelFraction() > 0) {
            int height = MathHelper.clamp(MathHelper.ceil(frame.fuelFraction() * 13), 1, 13);
            // Preserve vanilla's remaining-fuel silhouette, with a quiet flicker
            // so even a long-burning fuel visibly stays alive between updates.
            float flicker = 0.91F + 0.09F * MathHelper.sin(now % 10_000 / 95.0F);
            context.setShaderColor(1, 1, 1, fade * flicker);
            context.drawTexture(texture(layout), 56, 49 - height,
                    176, 13 - height, 14, height + 1);
            context.setShaderColor(1, 1, 1, fade);
        }
        int width = MathHelper.clamp(MathHelper.floor(frame.cookFraction() * 24), 0, 24);
        if (width > 0) context.drawTexture(texture(layout), 79, 34, 176, 14, width, 16);
        if (frame.cookKnown() && window.furnace() != null && fade > 0.025F) {
            int totalTicks = window.furnace().data().cookTimeTotal();
            String elapsed = String.format(Locale.ROOT, "%.1f", Math.max(0, totalTicks - frame.remainingCookTicks()) / 20.0F);
            String total = String.format(Locale.ROOT, "%.1f", totalTicks / 20.0F);
            Text time = ObserverText.text("curio-observer.furnace.time", elapsed, total);
            var renderer = MinecraftClient.getInstance().textRenderer;
            context.drawText(renderer, renderer.trimToWidth(time.getString(), 89), 79, 57,
                    ((int) (255 * fade) << 24) | 0x404040, false);
        }
    }

    private void drawBrewing(DrawContext context, ContainerLayout layout, BotState.Window window) {
        if (layout.background() != ContainerLayout.Background.BREWING_STAND || window.brewing() == null) return;
        var brewing = window.brewing();
        if (brewing.fuel() != null) {
            int width = MathHelper.clamp((18 * brewing.fuel() + 19) / 20, 0, 18);
            if (width > 0) context.drawTexture(HudTextures.BREWING_STAND, 60, 44, 176, 29, width, 4);
        }
        if (brewing.brewTime() != null && brewing.brewTime() > 0) {
            int time = brewing.brewTime();
            int height = MathHelper.clamp((int) (28 * (1 - time / 400.0F)), 0, 28);
            if (height > 0) context.drawTexture(HudTextures.BREWING_STAND, 97, 16, 176, 0, 9, height);
            int bubbles = new int[] {29, 24, 20, 16, 11, 6, 0}[time / 2 % 7];
            if (bubbles > 0) context.drawTexture(HudTextures.BREWING_STAND, 63, 43 - bubbles, 185, 29 - bubbles, 12, bubbles);
        }
    }

    /** Vanilla 1.20.1 enchanting buttons, with the hinted enchantment written out instead of glyphs so viewers can read it. */
    private void drawEnchanting(DrawContext context, ContainerLayout layout, BotState.Window window, BotState state,
                                long now, float fade) {
        if (layout.background() != ContainerLayout.Background.ENCHANTING_TABLE || window.enchanting() == null) return;
        var renderer = MinecraftClient.getInstance().textRenderer;
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
                context.drawTexture(HudTextures.ENCHANTING_TABLE, x, y, 0, 185, 108, 19);
                continue;
            }
            boolean chosen = enchanting.recentlyChosen(option, now);
            boolean affordable = lapis >= option + 1 && level >= offer.cost();
            context.drawTexture(HudTextures.ENCHANTING_TABLE, x, y, 0, chosen ? 204 : affordable ? 166 : 185, 108, 19);
            context.drawTexture(HudTextures.ENCHANTING_TABLE, x + 1, y + 1, 16 * option, affordable || chosen ? 223 : 239, 16, 16);
            if (!text) continue;
            String cost = Integer.toString(offer.cost());
            int hintWidth = 86 - renderer.getWidth(cost);
            String hint = renderer.trimToWidth(enchantmentHint(offer), hintWidth);
            int hintColor = chosen ? 0xFFFF80 : affordable ? 0x685E4A : (0x685E4A & 0xFEFEFE) >> 1;
            context.drawText(renderer, hint, x + 20, y + 2, alpha | hintColor, false);
            context.drawText(renderer, cost, x + 20 + 86 - renderer.getWidth(cost), y + 2 + 7,
                    alpha | (affordable || chosen ? 0x80FF20 : 0x407F10), true);
        }
        if (!text) return;
        // The book's corner shows what the choice depends on: the bot's level and its lapis.
        int color = alpha | 0x404040;
        context.drawText(renderer, renderer.trimToWidth(ObserverText.text("curio-observer.enchant.level", level).getString(), 48), 9, 15, color, false);
        context.drawText(renderer, renderer.trimToWidth(ObserverText.text("curio-observer.enchant.lapis", lapis).getString(), 48), 9, 27, color, false);
    }

    private static String enchantmentHint(Protocol.EnchantOption offer) {
        if (offer.enchant() == null) return "";
        Enchantment enchantment = Registries.ENCHANTMENT.get(offer.enchant());
        if (enchantment == null) return "";
        return enchantment.getName(offer.level() == null ? 1 : offer.level()).getString();
    }

    /** Vanilla 1.20.1 anvil: the name field, the blocked arrow and the level cost the bot will pay. */
    private void drawAnvil(DrawContext context, ContainerLayout layout, BotState.Window window, BotState state, float fade) {
        if (layout.background() != ContainerLayout.Background.ANVIL || window.anvil() == null) return;
        var renderer = MinecraftClient.getInstance().textRenderer;
        int alpha = (int) (255 * fade) << 24;
        Protocol.ItemData input = window.slots().isEmpty() ? null : window.slots().get(0);
        Protocol.ItemData output = window.slots().size() > 2 ? window.slots().get(2) : null;
        context.drawTexture(HudTextures.ANVIL, 59, 20, 0, input != null ? 166 : 182, 110, 16);
        if (input != null && output == null) context.drawTexture(HudTextures.ANVIL, 99, 45, 176, 0, 28, 21);
        if (fade <= 0.025F) return;
        String name = window.anvil().name();
        if (name == null && input != null) name = ItemCodec.stack(input).getName().getString();
        if (name != null && !name.isBlank()) {
            context.drawText(renderer, renderer.trimToWidth(name, 103), 62, 24, alpha | 0xE0E0E0, true);
        }
        Integer cost = window.anvil().cost();
        if (cost == null || cost <= 0) return;
        boolean expensive = cost >= 40;
        String label = (expensive ? ObserverText.text("container.repair.expensive") : ObserverText.text("container.repair.cost", cost)).getString();
        int color = expensive || state.xp().level() < cost ? 0xFF6060 : 0x80FF20;
        int x = layout.width() - 8 - renderer.getWidth(label) - 2;
        context.fill(x - 2, 67, layout.width() - 8, 79, ((int) (0x4F * fade) << 24));
        context.drawText(renderer, label, x, 69, alpha | color, true);
    }

    private void drawLabels(DrawContext context, ContainerLayout layout, String title, float fade) {
        var renderer = MinecraftClient.getInstance().textRenderer;
        int color = ((int) (255 * fade) << 24) | 0x404040;
        // TextRenderer supplies opaque alpha for small alpha values; stop drawing text at the end of a fade.
        if (fade <= 0.025F) return;
        String caption = title.isBlank() ? ObserverText.text(layout.titleKey()).getString()
                : title.startsWith("container.") && !title.contains(" ") ? ObserverText.text(title).getString() : title;
        caption = renderer.trimToWidth(caption, layout.width() - layout.titleX() - 8);
        int titleX = (layout.background() == ContainerLayout.Background.FURNACE
                || layout.background() == ContainerLayout.Background.BLAST_FURNACE
                || layout.background() == ContainerLayout.Background.SMOKER)
                ? (layout.width() - renderer.getWidth(caption)) / 2 : layout.titleX();
        boolean inventory = layout.background() == ContainerLayout.Background.INVENTORY;
        context.drawText(renderer, caption, titleX, inventory ? 8 : 6, color, false);
        if (!inventory) {
            context.drawText(renderer, ObserverText.text("container.inventory"), layout.inventoryX(),
                    layout.inventoryY() - (layout.storageRows() > 0 ? 11 : 12), color, false);
        }
    }

    private void drawSlot(DrawContext context, Protocol.ItemData data, int x, int y, float pulse) {
        ItemStack stack = ItemCodec.stack(data);
        if (stack.isEmpty()) return;
        context.getMatrices().push();
        try {
            float zoom = 1 + pulse * 0.12F;
            context.getMatrices().translate(x + 8, y + 8, 0);
            context.getMatrices().scale(zoom, zoom, 1);
            context.drawItem(stack, -8, -8);
            context.drawItemInSlot(MinecraftClient.getInstance().textRenderer, stack, -8, -8);
        } finally {
            context.getMatrices().pop();
        }
    }

    private void drawCarriedItem(DrawContext context, ContainerLayout layout, BotState.Window window,
                                 Protocol.ItemData current, long now) {
        var frame = pointer.sample(layout, window, current, now);
        if (!frame.visible() || frame.item() == null) return;
        int x = Math.round(frame.x()), y = Math.round(frame.y());
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 300);
        try {
            ItemStack stack = ItemCodec.stack(frame.item());
            context.drawItem(stack, x + 4, y + 4);
            context.drawItemInSlot(MinecraftClient.getInstance().textRenderer, stack, x + 4, y + 4,
                    Integer.toString(frame.item().count()));
        } finally {
            context.getMatrices().pop();
        }
    }

    private void drawTransfer(DrawContext context, ContainerLayout layout, BotState.Window window, long now, float fade) {
        var transfer = window.transfer();
        if (transfer == null || now - transfer.receivedAt() >= BotState.Window.TRANSFER_LIFETIME_MS || fade <= 0.025F) return;
        var data = transfer.data();
        var renderer = MinecraftClient.getInstance().textRenderer;
        ItemStack stack = ItemCodec.stack(data.item());
        String text = ObserverText.text("curio-observer.transfer." + data.kind(), data.item().count(), stack.getName()).getString();
        text = renderer.trimToWidth(text, layout.width() - 24);
        context.fill(0, layout.height() + 2, layout.width(), layout.height() + 24, ((int) (210 * fade) << 24));
        context.drawItem(stack, 3, layout.height() + 5);
        context.drawText(renderer, text, 23, layout.height() + 9, ((int) (255 * fade) << 24) | 0xFFFFFF, true);
        for (var departure : InventoryTransfers.departures(layout, window, now)) {
            // Exactly one departing stack per actual operation, even when several events arrive in one frame.
            // Its count is never expanded into simulated single-item drops.
            ItemStack dropped = ItemCodec.stack(departure.item());
            int x = Math.round(departure.x()), y = Math.round(departure.y());
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 300);
            context.drawItem(dropped, x, y);
            context.drawItemInSlot(renderer, dropped, x, y, Integer.toString(departure.item().count()));
            context.getMatrices().pop();
        }
    }

    private static Protocol.ItemData playerItem(BotState state, int playerSlot) {
        if (playerSlot >= 5 && playerSlot <= 8) return state.armor().get(playerSlot - 5);
        if (playerSlot >= 9 && playerSlot <= 35) return state.main().get(playerSlot - 9);
        if (playerSlot >= 36 && playerSlot <= 44) return state.hotbar().get(playerSlot - 36);
        return playerSlot == 45 ? state.offhand() : null;
    }

    private static Identifier texture(ContainerLayout layout) {
        return switch (layout.background()) {
            case INVENTORY -> HudTextures.INVENTORY;
            case CRAFTING_TABLE -> HudTextures.CRAFTING_TABLE;
            case FURNACE -> HudTextures.FURNACE;
            case ENCHANTING_TABLE -> HudTextures.ENCHANTING_TABLE;
            case ANVIL -> HudTextures.ANVIL;
            case BLAST_FURNACE -> HudTextures.BLAST_FURNACE;
            case SMOKER -> HudTextures.SMOKER;
            case BREWING_STAND -> HudTextures.BREWING_STAND;
            case HOPPER -> HudTextures.HOPPER;
            case SMITHING -> HudTextures.SMITHING;
            case GRINDSTONE -> HudTextures.GRINDSTONE;
            case STONECUTTER -> HudTextures.STONECUTTER;
            case LOOM -> HudTextures.LOOM;
            case DISPENSER -> HudTextures.DISPENSER;
            case SHULKER_BOX -> HudTextures.SHULKER_BOX;
            case CARTOGRAPHY_TABLE -> HudTextures.CARTOGRAPHY_TABLE;
            case BEACON -> HudTextures.BEACON;
            case MERCHANT -> HudTextures.MERCHANT;
            case GENERIC -> HudTextures.GENERIC_54;
        };
    }
}
