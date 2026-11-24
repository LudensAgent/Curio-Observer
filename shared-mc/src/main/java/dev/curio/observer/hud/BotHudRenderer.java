package dev.curio.observer.hud;

import dev.curio.observer.compat.HudCanvas;
import dev.curio.observer.compat.ItemCodec;
import dev.curio.observer.compat.McCompatImpl;
import dev.curio.observer.config.ObserverConfig;
import dev.curio.observer.model.Protocol;
import dev.curio.observer.state.BotState;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Draws a snapshot without changing the spectator's inventory or screen handler. */
public final class BotHudRenderer {
    private static final int BAR_WIDTH = 182;
    private static final int XP_COLOR = 0xFF80FF20;
    static final int BLOCK_WIDTH = 211;
    static final int BLOCK_HEIGHT = 62;

    public void render(HudCanvas canvas, BotState state, float tickDelta, double scale) {
        render(canvas, state, tickDelta, scale, null);
    }

    public void render(HudCanvas canvas, BotState state, float tickDelta, double scale, ObserverConfig.PanelConfig panel) {
        Minecraft client = Minecraft.getInstance();
        float factor = (float) (Double.isFinite(scale) ? Mth.clamp(scale, 0.5, 3.0) : 1.0);
        int width = Mth.floor(canvas.width() / factor);
        int height = Mth.floor(canvas.height() / factor);
        int center = width / 2;
        int ticks = McCompatImpl.guiTicks(client);

        canvas.push();
        float defaultX = (center - 120) * factor;
        float defaultY = (height - BLOCK_HEIGHT) * factor;
        if (HudPlacement.custom(panel)) {
            HudPlacement.Point at = HudPlacement.place(panel, defaultX, defaultY, BLOCK_WIDTH * factor, BLOCK_HEIGHT * factor,
                    canvas.width(), canvas.height());
            canvas.translate(at.x() - defaultX, at.y() - defaultY, 0);
        }
        canvas.scale(factor, factor);
        canvas.enableBlend();
        try {
            renderHotbar(canvas, state, center, height);
            renderStatusBars(canvas, state, center, height, ticks);
            Entity camera = client.getCameraEntity();
            if (camera instanceof Player player && state.hello() != null
                    && McCompatImpl.playerName(player).equals(state.hello().bot())) {
                renderOxygen(canvas, player.getAirSupply(), player.getMaxAirSupply(),
                        McCompatImpl.isSubmergedInWater(player), true, center, height);
            } else {
                renderOxygen(canvas, state.oxygen() * 15, 300,
                        camera != null && McCompatImpl.isSubmergedInWater(camera), false, center, height);
            }
            renderExperience(canvas, state, center, height);
        } finally {
            canvas.disableBlend();
            canvas.pop();
        }
    }

    private void renderHotbar(HudCanvas canvas, BotState state, int center, int height) {
        canvas.drawHotbar(center - 91, height - 22);
        int selected = Mth.clamp(state.selected(), 0, 8);
        canvas.drawHotbarSelection(center - 92 + selected * 20, height - 23);

        List<Protocol.ItemData> hotbar = state.hotbar();
        for (int slot = 0; slot < Math.min(9, hotbar.size()); slot++) {
            drawItem(canvas, hotbar.get(slot), center - 88 + slot * 20, height - 19);
        }

        ItemStack offhand = ItemCodec.stack(state.offhand());
        if (!offhand.isEmpty()) {
            canvas.drawOffhandLeft(center - 120, height - 23);
            drawItem(canvas, offhand, center - 117, height - 19);
        }
    }

    private void renderStatusBars(HudCanvas canvas, BotState state, int center, int height, int ticks) {
        int left = center - 91;
        int right = center + 91;
        int baseline = height - 39;
        int health = Mth.ceil(Mth.clamp(state.health(), 0.0, 1024.0));
        int maxHealth = Mth.ceil(Mth.clamp(state.maxHealth(), 1.0, 1024.0));
        int absorption = Mth.ceil(Mth.clamp(state.absorption(), 0.0, 1024.0));
        int normalHearts = (maxHealth + 1) / 2;
        int absorptionHearts = (absorption + 1) / 2;
        int totalHearts = normalHearts + absorptionHearts;
        int rows = (totalHearts + 9) / 10;
        int rowSpacing = Math.max(10 - (rows - 2), 3);
        Random jitter = new Random(ticks * 312871L);

        for (int slot = totalHearts - 1; slot >= 0; slot--) {
            int x = left + slot % 10 * 8;
            int y = baseline - slot / 10 * rowSpacing;
            if (health <= 4) y += jitter.nextInt(2);
            canvas.drawHeartContainer(x, y);
            if (slot < normalHearts) {
                int remaining = health - slot * 2;
                if (remaining > 1) canvas.drawHeartFull(x, y);
                else if (remaining == 1) canvas.drawHeartHalf(x, y);
            } else {
                int remaining = absorption - (slot - normalHearts) * 2;
                if (remaining > 1) canvas.drawAbsorptionFull(x, y);
                else if (remaining == 1) canvas.drawAbsorptionHalf(x, y);
            }
        }

        int armor = armorPoints(state.armor());
        if (armor > 0) {
            int armorY = baseline - (rows - 1) * rowSpacing - 10;
            for (int slot = 0; slot < 10; slot++) {
                int remaining = armor - slot * 2;
                int x = left + slot * 8;
                if (remaining > 1) canvas.drawArmorFull(x, armorY);
                else if (remaining == 1) canvas.drawArmorHalf(x, armorY);
                else canvas.drawArmorEmpty(x, armorY);
            }
        }

        int food = Mth.clamp(state.food(), 0, 20);
        boolean hungerJitter = state.saturation() <= 0 && ticks % (food * 3 + 1) == 0;
        for (int slot = 0; slot < 10; slot++) {
            int x = right - slot * 8 - 9;
            int y = baseline + (hungerJitter ? jitter.nextInt(3) - 1 : 0);
            canvas.drawFoodEmpty(x, y);
            int remaining = food - slot * 2;
            if (remaining > 1) canvas.drawFoodFull(x, y);
            else if (remaining == 1) canvas.drawFoodHalf(x, y);
        }
    }

    static void renderOxygen(HudCanvas canvas, int air, int maxAir, boolean submerged, boolean showWhileRecovering,
                             int center, int height) {
        int max = Math.max(1, maxAir);
        int value = Math.min(Math.max(air, 0), max);
        if (!submerged && !(showWhileRecovering && value < max)) return;
        int full = Mth.ceil((value - 2) * 10.0 / max);
        int popping = Mth.ceil(value * 10.0 / max) - full;
        for (int slot = 0; slot < full + popping; slot++) {
            int x = center + 91 - slot * 8 - 9;
            int y = height - 49;
            if (slot < full) canvas.drawAirFull(x, y);
            else canvas.drawAirBursting(x, y);
        }
    }

    private void renderExperience(HudCanvas canvas, BotState state, int center, int height) {
        int x = center - 91;
        int y = height - 32 + 3;
        canvas.drawXpBackground(x, y);
        int progress = (int) (Mth.clamp(state.xp().progress(), 0.0, 1.0) * 183.0);
        if (progress > 0) {
            canvas.drawXpProgress(x, y, Math.min(BAR_WIDTH, progress));
        }

        int level = state.xp().level();
        if (level <= 0) return;
        var font = Minecraft.getInstance().font;
        String text = Integer.toString(level);
        int textX = center - font.width(text) / 2;
        int textY = height - 31 - 4;
        canvas.drawString(font, text, textX + 1, textY, 0xFF000000, false);
        canvas.drawString(font, text, textX - 1, textY, 0xFF000000, false);
        canvas.drawString(font, text, textX, textY + 1, 0xFF000000, false);
        canvas.drawString(font, text, textX, textY - 1, 0xFF000000, false);
        canvas.drawString(font, text, textX, textY, XP_COLOR, false);
    }

    private static int armorPoints(List<Protocol.ItemData> armor) {
        int points = 0;
        for (Protocol.ItemData data : armor) {
            ItemStack stack = ItemCodec.stack(data);
            points += McCompatImpl.armorProtection(stack);
        }
        return Mth.clamp(points, 0, 20);
    }

    private static void drawItem(HudCanvas canvas, Protocol.ItemData data, int x, int y) {
        drawItem(canvas, ItemCodec.stack(data), x, y);
    }

    private static void drawItem(HudCanvas canvas, ItemStack stack, int x, int y) {
        if (!stack.isEmpty()) {
            canvas.drawItem(stack, x, y);
            canvas.drawItemDecorations(Minecraft.getInstance().font, stack, x, y);
        }
    }
}
