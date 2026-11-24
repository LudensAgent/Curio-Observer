package dev.curio.observer.compat;

import dev.curio.observer.hud.ContainerLayout;
import dev.curio.observer.state.BotState;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Version-independent 2D HUD and container drawing surface.
 * Abstracts legacy PoseStack/GuiComponent (1.18.2–1.19.4), GuiGraphics with legacy atlases (1.20–1.20.1),
 * GuiGraphics with GUI sprites (1.20.2–1.21.11), and GuiGraphicsExtractor (26.1+).
 */
public interface HudCanvas {
    int width();
    int height();
    void push();
    void pop();
    void translate(float x, float y, float z);
    void scale(float x, float y);
    void setAlpha(float alpha);
    void enableBlend();
    void disableBlend();
    void fill(int x1, int y1, int x2, int y2, int color);
    void fillGradient(int x1, int y1, int x2, int y2, int topColor, int bottomColor);
    void enableScissor(int x1, int y1, int x2, int y2);
    void disableScissor();
    void drawString(Font font, String text, int x, int y, int color, boolean shadow);
    void drawString(Font font, Component text, int x, int y, int color, boolean shadow);
    void drawCenteredString(Font font, Component text, int centerX, int y, int color);
    void drawItem(ItemStack stack, int x, int y);
    void drawFakeItem(ItemStack stack, int x, int y);
    void drawItemDecorations(Font font, ItemStack stack, int x, int y);
    void drawItemDecorations(Font font, ItemStack stack, int x, int y, String countText);

    void drawCrosshair(int x, int y);
    void drawHotbar(int x, int y);
    void drawHotbarSelection(int x, int y);
    void drawOffhandLeft(int x, int y);
    void drawHeartContainer(int x, int y);
    void drawHeartFull(int x, int y);
    void drawHeartHalf(int x, int y);
    void drawAbsorptionFull(int x, int y);
    void drawAbsorptionHalf(int x, int y);
    void drawArmorEmpty(int x, int y);
    void drawArmorHalf(int x, int y);
    void drawArmorFull(int x, int y);
    void drawFoodEmpty(int x, int y);
    void drawFoodFull(int x, int y);
    void drawFoodHalf(int x, int y);
    void drawAirFull(int x, int y);
    void drawAirBursting(int x, int y);
    void drawXpBackground(int x, int y);
    void drawXpProgress(int x, int y, int width);

    void drawContainerBackground(ContainerLayout.Background bg, int x, int y, int width, int height);
    void drawGenericChestSlice(int x, int y, int v, int width, int height);
    void drawFurnaceLit(ContainerLayout.Background bg, int x, int y, int litHeight);
    void drawFurnaceBurn(ContainerLayout.Background bg, int x, int y, int burnWidth);
    void drawBrewingFuel(int x, int y, int width);
    void drawBrewingProgress(int x, int y, int height);
    void drawBrewingBubbles(int x, int y, int height);
    void drawEnchantSlot(int x, int y, boolean offered, boolean affordable, boolean chosen);
    void drawEnchantLevel(int x, int y, int option, boolean highlighted);
    void drawAnvilTextField(int x, int y, boolean active);
    void drawAnvilError(int x, int y);
    void drawGrindstoneError(int x, int y);
    void drawMerchantScroller(int x, int y, boolean scrollable);
    void drawMerchantButton(int x, int y, boolean selected);
    void drawMerchantArrow(int x, int y, boolean disabled);
    void drawMerchantCross(int x, int y);
    void drawMerchantXpBg(int x, int y);
    void drawMerchantXpFill(int x, int y, int width);

    void drawPlayerPreview(ContainerLayout layout, BotState state, float tickDelta, float fade);
}
