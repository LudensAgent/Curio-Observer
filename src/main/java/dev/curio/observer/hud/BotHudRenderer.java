package dev.curio.observer.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.curio.observer.config.ObserverConfig;
import dev.curio.observer.model.Protocol;
import dev.curio.observer.state.BotState;
import dev.curio.observer.state.ItemCodec;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.Random;

/** Draws a snapshot without changing the spectator's inventory or screen handler. */
public final class BotHudRenderer {
    private static final int BAR_WIDTH = 182;
    private static final int XP_COLOR = 0x80FF20;
    /** Bounds of the vanilla-style block (offhand slot to hunger bar, hotbar to armor row) in logical pixels. */
    static final int BLOCK_WIDTH = 211;
    static final int BLOCK_HEIGHT = 62;

    public void render(DrawContext context, BotState state, float tickDelta, double scale) {
        render(context, state, tickDelta, scale, null);
    }

    /** A positioned panel moves the whole block; by default it sits where vanilla draws the player's own HUD. */
    public void render(DrawContext context, BotState state, float tickDelta, double scale, ObserverConfig.PanelConfig panel) {
        MinecraftClient client = MinecraftClient.getInstance();
        float factor = (float) (Double.isFinite(scale) ? MathHelper.clamp(scale, 0.5, 3.0) : 1.0);
        int width = MathHelper.floor(context.getScaledWindowWidth() / factor);
        int height = MathHelper.floor(context.getScaledWindowHeight() / factor);
        int center = width / 2;
        int ticks = client.inGameHud.getTicks();

        context.getMatrices().push();
        float defaultX = (center - 120) * factor;
        float defaultY = (height - BLOCK_HEIGHT) * factor;
        if (HudPlacement.custom(panel)) {
            HudPlacement.Point at = HudPlacement.place(panel, defaultX, defaultY, BLOCK_WIDTH * factor, BLOCK_HEIGHT * factor,
                    context.getScaledWindowWidth(), context.getScaledWindowHeight());
            context.getMatrices().translate(at.x() - defaultX, at.y() - defaultY, 0);
        }
        context.getMatrices().scale(factor, factor, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            renderHotbar(context, state, center, height);
            renderStatusBars(context, state, center, height, ticks);
            Entity camera = client.getCameraEntity();
            if (camera instanceof PlayerEntity player && state.hello() != null
                    && player.getGameProfile().getName().equals(state.hello().bot())) {
                // The spectated player's air is synced entity data on this client: exact and without feed latency.
                renderOxygen(context, player.getAir(), player.getMaxAir(), player.isSubmergedIn(FluidTags.WATER), true, center, height);
            } else {
                // Preview without the bot's entity: the feed's 0..20 reading, shown only underwater because it may be stale.
                renderOxygen(context, state.oxygen() * 15, 300, camera != null && camera.isSubmergedIn(FluidTags.WATER), false, center, height);
            }
            renderExperience(context, state, center, height);
        } finally {
            RenderSystem.disableBlend();
            context.getMatrices().pop();
        }
    }

    private void renderHotbar(DrawContext context, BotState state, int center, int height) {
        context.drawTexture(HudTextures.WIDGETS, center - 91, height - 22, 0, 0, BAR_WIDTH, 22);
        int selected = MathHelper.clamp(state.selected(), 0, 8);
        context.drawTexture(HudTextures.WIDGETS, center - 92 + selected * 20, height - 23,
                0, 22, 24, 22);

        List<Protocol.ItemData> hotbar = state.hotbar();
        for (int slot = 0; slot < Math.min(9, hotbar.size()); slot++) {
            drawItem(context, hotbar.get(slot), center - 88 + slot * 20, height - 19);
        }

        ItemStack offhand = ItemCodec.stack(state.offhand());
        if (!offhand.isEmpty()) {
            context.drawTexture(HudTextures.WIDGETS, center - 120, height - 23,
                    24, 22, 29, 24);
            drawItem(context, offhand, center - 117, height - 19);
        }
    }

    private void renderStatusBars(DrawContext context, BotState state, int center, int height, int ticks) {
        int left = center - 91;
        int right = center + 91;
        int baseline = height - 39;
        int health = MathHelper.ceil(MathHelper.clamp(state.health(), 0.0, 1024.0));
        int maxHealth = MathHelper.ceil(MathHelper.clamp(state.maxHealth(), 1.0, 1024.0));
        int absorption = MathHelper.ceil(MathHelper.clamp(state.absorption(), 0.0, 1024.0));
        int normalHearts = (maxHealth + 1) / 2;
        int absorptionHearts = (absorption + 1) / 2;
        int totalHearts = normalHearts + absorptionHearts;
        int rows = (totalHearts + 9) / 10;
        int rowSpacing = Math.max(10 - (rows - 2), 3);
        Random jitter = new Random(ticks * 312871L);

        // Drawing from the top row down matches vanilla's overlapping rows at high maximum health.
        for (int slot = totalHearts - 1; slot >= 0; slot--) {
            int x = left + slot % 10 * 8;
            int y = baseline - slot / 10 * rowSpacing;
            if (health <= 4) {
                y += jitter.nextInt(2);
            }
            icon(context, x, y, 16, 0);
            if (slot < normalHearts) {
                int remaining = health - slot * 2;
                if (remaining > 0) {
                    icon(context, x, y, remaining > 1 ? 52 : 61, 0);
                }
            } else {
                int remaining = absorption - (slot - normalHearts) * 2;
                if (remaining > 0) {
                    icon(context, x, y, remaining > 1 ? 160 : 169, 0);
                }
            }
        }

        int armor = armorPoints(state.armor());
        if (armor > 0) {
            int armorY = baseline - (rows - 1) * rowSpacing - 10;
            for (int slot = 0; slot < 10; slot++) {
                int remaining = armor - slot * 2;
                icon(context, left + slot * 8, armorY,
                        remaining > 1 ? 34 : remaining == 1 ? 25 : 16, 9);
            }
        }

        int food = MathHelper.clamp(state.food(), 0, 20);
        boolean hungerJitter = state.saturation() <= 0 && ticks % (food * 3 + 1) == 0;
        for (int slot = 0; slot < 10; slot++) {
            int x = right - slot * 8 - 9;
            int y = baseline + (hungerJitter ? jitter.nextInt(3) - 1 : 0);
            icon(context, x, y, 16, 27);
            int remaining = food - slot * 2;
            if (remaining > 0) {
                icon(context, x, y, remaining > 1 ? 52 : 61, 27);
            }
        }
    }

    /**
     * Vanilla 1.20.1 bubbles: shown while the eyes are underwater or air is still refilling, with
     * full = ceil((air - 2) * 10 / max) and one popping bubble up to ceil(air * 10 / max).
     * showWhileRecovering is false for readings that may be stale.
     */
    static void renderOxygen(DrawContext context, int air, int maxAir, boolean submerged, boolean showWhileRecovering,
                             int center, int height) {
        int max = Math.max(1, maxAir);
        int value = Math.min(Math.max(air, 0), max);
        if (!submerged && !(showWhileRecovering && value < max)) return;
        int full = MathHelper.ceil((value - 2) * 10.0 / max);
        int popping = MathHelper.ceil(value * 10.0 / max) - full;
        for (int slot = 0; slot < full + popping; slot++) {
            icon(context, center + 91 - slot * 8 - 9, height - 49, slot < full ? 16 : 25, 18);
        }
    }

    private void renderExperience(DrawContext context, BotState state, int center, int height) {
        int x = center - 91;
        int y = height - 32 + 3;
        context.drawTexture(HudTextures.ICONS, x, y, 0, 64, BAR_WIDTH, 5);
        int progress = (int) (MathHelper.clamp(state.xp().progress(), 0.0, 1.0) * 183.0);
        if (progress > 0) {
            context.drawTexture(HudTextures.ICONS, x, y, 0, 69, Math.min(BAR_WIDTH, progress), 5);
        }

        int level = state.xp().level();
        if (level <= 0) {
            return;
        }
        var textRenderer = MinecraftClient.getInstance().textRenderer;
        String text = Integer.toString(level);
        int textX = center - textRenderer.getWidth(text) / 2;
        int textY = height - 31 - 4;
        context.drawText(textRenderer, text, textX + 1, textY, 0, false);
        context.drawText(textRenderer, text, textX - 1, textY, 0, false);
        context.drawText(textRenderer, text, textX, textY + 1, 0, false);
        context.drawText(textRenderer, text, textX, textY - 1, 0, false);
        context.drawText(textRenderer, text, textX, textY, XP_COLOR, false);
    }

    private static int armorPoints(List<Protocol.ItemData> armor) {
        int points = 0;
        for (Protocol.ItemData data : armor) {
            ItemStack stack = ItemCodec.stack(data);
            if (stack.getItem() instanceof ArmorItem armorItem) {
                points += armorItem.getProtection();
            }
        }
        return MathHelper.clamp(points, 0, 20);
    }

    private static void icon(DrawContext context, int x, int y, int u, int v) {
        context.drawTexture(HudTextures.ICONS, x, y, u, v, 9, 9);
    }

    private static void drawItem(DrawContext context, Protocol.ItemData data, int x, int y) {
        drawItem(context, ItemCodec.stack(data), x, y);
    }

    private static void drawItem(DrawContext context, ItemStack stack, int x, int y) {
        if (!stack.isEmpty()) {
            context.drawItem(stack, x, y);
            context.drawItemInSlot(MinecraftClient.getInstance().textRenderer, stack, x, y);
        }
    }
}
