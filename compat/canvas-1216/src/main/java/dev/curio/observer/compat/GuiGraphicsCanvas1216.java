package dev.curio.observer.compat;

import dev.curio.observer.hud.ContainerLayout;
import dev.curio.observer.state.BotState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

/** HudCanvas for MC 1.21.6–1.21.10 (GuiGraphics + RenderPipelines + Matrix3x2fStack + ResourceLocation). */
public final class GuiGraphicsCanvas1216 implements HudCanvas {
    private static ResourceLocation rl(String path) { return ResourceLocation.withDefaultNamespace(path); }

    private static final ResourceLocation CROSSHAIR = rl("hud/crosshair");
    private static final ResourceLocation HOTBAR = rl("hud/hotbar");
    private static final ResourceLocation HOTBAR_SELECTION = rl("hud/hotbar_selection");
    private static final ResourceLocation HOTBAR_OFFHAND_LEFT = rl("hud/hotbar_offhand_left");
    private static final ResourceLocation HEART_CONTAINER = rl("hud/heart/container");
    private static final ResourceLocation HEART_FULL = rl("hud/heart/full");
    private static final ResourceLocation HEART_HALF = rl("hud/heart/half");
    private static final ResourceLocation ABSORB_FULL = rl("hud/heart/absorbing_full");
    private static final ResourceLocation ABSORB_HALF = rl("hud/heart/absorbing_half");
    private static final ResourceLocation ARMOR_EMPTY = rl("hud/armor_empty");
    private static final ResourceLocation ARMOR_HALF = rl("hud/armor_half");
    private static final ResourceLocation ARMOR_FULL = rl("hud/armor_full");
    private static final ResourceLocation FOOD_EMPTY = rl("hud/food_empty");
    private static final ResourceLocation FOOD_FULL = rl("hud/food_full");
    private static final ResourceLocation FOOD_HALF = rl("hud/food_half");
    private static final ResourceLocation AIR = rl("hud/air");
    private static final ResourceLocation AIR_BURSTING = rl("hud/air_bursting");
    private static final ResourceLocation XP_BG = rl("hud/experience_bar_background");
    private static final ResourceLocation XP_PROGRESS = rl("hud/experience_bar_progress");

    private static final ResourceLocation FURNACE_LIT = rl("container/furnace/lit_progress");
    private static final ResourceLocation FURNACE_BURN = rl("container/furnace/burn_progress");
    private static final ResourceLocation BLAST_LIT = rl("container/blast_furnace/lit_progress");
    private static final ResourceLocation BLAST_BURN = rl("container/blast_furnace/burn_progress");
    private static final ResourceLocation SMOKER_LIT = rl("container/smoker/lit_progress");
    private static final ResourceLocation SMOKER_BURN = rl("container/smoker/burn_progress");
    private static final ResourceLocation BREW_FUEL = rl("container/brewing_stand/fuel_length");
    private static final ResourceLocation BREW_PROGRESS = rl("container/brewing_stand/brew_progress");
    private static final ResourceLocation BREW_BUBBLES = rl("container/brewing_stand/bubbles");
    private static final ResourceLocation ENCH_SLOT = rl("container/enchanting_table/enchantment_slot");
    private static final ResourceLocation ENCH_SLOT_DISABLED = rl("container/enchanting_table/enchantment_slot_disabled");
    private static final ResourceLocation ENCH_SLOT_HIGHLIGHTED = rl("container/enchanting_table/enchantment_slot_highlighted");
    private static final ResourceLocation[] ENCH_LVL = {
            rl("container/enchanting_table/level_1"), rl("container/enchanting_table/level_2"), rl("container/enchanting_table/level_3") };
    private static final ResourceLocation[] ENCH_LVL_DIS = {
            rl("container/enchanting_table/level_1_disabled"), rl("container/enchanting_table/level_2_disabled"), rl("container/enchanting_table/level_3_disabled") };
    private static final ResourceLocation ANVIL_FIELD = rl("container/anvil/text_field");
    private static final ResourceLocation ANVIL_FIELD_DIS = rl("container/anvil/text_field_disabled");
    private static final ResourceLocation ANVIL_ERROR = rl("container/anvil/error");
    private static final ResourceLocation GRINDSTONE_ERROR = rl("container/grindstone/error");
    private static final ResourceLocation VILLAGER_SCROLLER = rl("container/villager/scroller");
    private static final ResourceLocation VILLAGER_SCROLLER_DIS = rl("container/villager/scroller_disabled");
    private static final ResourceLocation BUTTON_SPRITE = rl("widget/button");
    private static final ResourceLocation BUTTON_HIGHLIGHTED = rl("widget/button_highlighted");
    private static final ResourceLocation TRADE_ARROW = rl("container/villager/trade_arrow");
    private static final ResourceLocation TRADE_ARROW_OUT = rl("container/villager/trade_arrow_out_of_stock");
    private static final ResourceLocation TRADE_OUT = rl("container/villager/out_of_stock");
    private static final ResourceLocation VILLAGER_XP_BG = rl("container/villager/experience_bar_background");
    private static final ResourceLocation VILLAGER_XP_CUR = rl("container/villager/experience_bar_current");

    private static final ResourceLocation INVENTORY = rl("textures/gui/container/inventory.png");
    private static final ResourceLocation CRAFTING_TABLE = rl("textures/gui/container/crafting_table.png");
    private static final ResourceLocation FURNACE = rl("textures/gui/container/furnace.png");
    private static final ResourceLocation ENCHANTING_TABLE = rl("textures/gui/container/enchanting_table.png");
    private static final ResourceLocation ANVIL = rl("textures/gui/container/anvil.png");
    private static final ResourceLocation BLAST_FURNACE = rl("textures/gui/container/blast_furnace.png");
    private static final ResourceLocation SMOKER = rl("textures/gui/container/smoker.png");
    private static final ResourceLocation BREWING_STAND = rl("textures/gui/container/brewing_stand.png");
    private static final ResourceLocation HOPPER = rl("textures/gui/container/hopper.png");
    private static final ResourceLocation SMITHING = rl("textures/gui/container/smithing.png");
    private static final ResourceLocation GRINDSTONE = rl("textures/gui/container/grindstone.png");
    private static final ResourceLocation STONECUTTER = rl("textures/gui/container/stonecutter.png");
    private static final ResourceLocation LOOM = rl("textures/gui/container/loom.png");
    private static final ResourceLocation DISPENSER = rl("textures/gui/container/dispenser.png");
    private static final ResourceLocation SHULKER_BOX = rl("textures/gui/container/shulker_box.png");
    private static final ResourceLocation CARTOGRAPHY_TABLE = rl("textures/gui/container/cartography_table.png");
    private static final ResourceLocation BEACON = rl("textures/gui/container/beacon.png");
    private static final ResourceLocation MERCHANT = rl("textures/gui/container/villager.png");
    private static final ResourceLocation GENERIC_54 = rl("textures/gui/container/generic_54.png");

    private final GuiGraphics g;
    private int tintColor = 0xFFFFFFFF;

    public GuiGraphicsCanvas1216(GuiGraphics g) { this.g = g; }

    @Override public int width() { return g.guiWidth(); }
    @Override public int height() { return g.guiHeight(); }
    @Override public void push() { g.pose().pushMatrix(); }
    @Override public void pop() { g.pose().popMatrix(); }
    @Override public void translate(float x, float y, float z) { g.pose().translate(x, y); }
    @Override public void scale(float x, float y) { g.pose().scale(x, y); }
    @Override public void setAlpha(float alpha) { tintColor = (Math.round(Mth.clamp(alpha, 0, 1) * 255) << 24) | 0xFFFFFF; }
    @Override public void enableBlend() {}
    @Override public void disableBlend() {}
    @Override public void fill(int x1, int y1, int x2, int y2, int color) { g.fill(x1, y1, x2, y2, color); }
    @Override public void fillGradient(int x1, int y1, int x2, int y2, int topColor, int bottomColor) { g.fillGradient(x1, y1, x2, y2, topColor, bottomColor); }
    @Override public void enableScissor(int x1, int y1, int x2, int y2) { g.enableScissor(x1, y1, x2, y2); }
    @Override public void disableScissor() { g.disableScissor(); }
    @Override public void drawString(Font font, String text, int x, int y, int color, boolean shadow) { g.drawString(font, text, x, y, color, shadow); }
    @Override public void drawString(Font font, Component text, int x, int y, int color, boolean shadow) { g.drawString(font, text, x, y, color, shadow); }
    @Override public void drawCenteredString(Font font, Component text, int centerX, int y, int color) { g.drawCenteredString(font, text, centerX, y, color); }
    @Override public void drawItem(ItemStack stack, int x, int y) { g.renderItem(stack, x, y); }
    @Override public void drawFakeItem(ItemStack stack, int x, int y) { g.renderFakeItem(stack, x, y); }
    @Override public void drawItemDecorations(Font font, ItemStack stack, int x, int y) { g.renderItemDecorations(font, stack, x, y); }
    @Override public void drawItemDecorations(Font font, ItemStack stack, int x, int y, String countText) { g.renderItemDecorations(font, stack, x, y, countText); }

    private void sprite(ResourceLocation id, int x, int y, int w, int h) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, w, h, tintColor);
    }
    private void spriteSlice(ResourceLocation id, int tw, int th, int u, int v, int x, int y, int w, int h) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, id, tw, th, u, v, x, y, w, h, tintColor);
    }

    @Override public void drawCrosshair(int x, int y) { g.blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR, x, y, 15, 15); }
    @Override public void drawHotbar(int x, int y) { sprite(HOTBAR, x, y, 182, 22); }
    @Override public void drawHotbarSelection(int x, int y) { sprite(HOTBAR_SELECTION, x, y, 24, 23); }
    @Override public void drawOffhandLeft(int x, int y) { sprite(HOTBAR_OFFHAND_LEFT, x, y, 29, 24); }
    @Override public void drawHeartContainer(int x, int y) { sprite(HEART_CONTAINER, x, y, 9, 9); }
    @Override public void drawHeartFull(int x, int y) { sprite(HEART_FULL, x, y, 9, 9); }
    @Override public void drawHeartHalf(int x, int y) { sprite(HEART_HALF, x, y, 9, 9); }
    @Override public void drawAbsorptionFull(int x, int y) { sprite(ABSORB_FULL, x, y, 9, 9); }
    @Override public void drawAbsorptionHalf(int x, int y) { sprite(ABSORB_HALF, x, y, 9, 9); }
    @Override public void drawArmorEmpty(int x, int y) { sprite(ARMOR_EMPTY, x, y, 9, 9); }
    @Override public void drawArmorHalf(int x, int y) { sprite(ARMOR_HALF, x, y, 9, 9); }
    @Override public void drawArmorFull(int x, int y) { sprite(ARMOR_FULL, x, y, 9, 9); }
    @Override public void drawFoodEmpty(int x, int y) { sprite(FOOD_EMPTY, x, y, 9, 9); }
    @Override public void drawFoodFull(int x, int y) { sprite(FOOD_FULL, x, y, 9, 9); }
    @Override public void drawFoodHalf(int x, int y) { sprite(FOOD_HALF, x, y, 9, 9); }
    @Override public void drawAirFull(int x, int y) { sprite(AIR, x, y, 9, 9); }
    @Override public void drawAirBursting(int x, int y) { sprite(AIR_BURSTING, x, y, 9, 9); }
    @Override public void drawXpBackground(int x, int y) { sprite(XP_BG, x, y, 182, 5); }
    @Override public void drawXpProgress(int x, int y, int width) { spriteSlice(XP_PROGRESS, 182, 5, 0, 0, x, y, width, 5); }

    @Override public void drawContainerBackground(ContainerLayout.Background bg, int x, int y, int width, int height) {
        if (bg == ContainerLayout.Background.MERCHANT) g.blit(RenderPipelines.GUI_TEXTURED, MERCHANT, x, y, 0.0F, 0.0F, 276, 166, 512, 256, tintColor);
        else g.blit(RenderPipelines.GUI_TEXTURED, texture(bg), x, y, 0.0F, 0.0F, width, height, 256, 256, tintColor);
    }
    @Override public void drawGenericChestSlice(int x, int y, int v, int width, int height) {
        g.blit(RenderPipelines.GUI_TEXTURED, GENERIC_54, x, y, 0.0F, v, width, height, 256, 256, tintColor);
    }
    @Override public void drawFurnaceLit(ContainerLayout.Background bg, int x, int y, int litHeight) {
        ResourceLocation s = bg == ContainerLayout.Background.BLAST_FURNACE ? BLAST_LIT : bg == ContainerLayout.Background.SMOKER ? SMOKER_LIT : FURNACE_LIT;
        int h = Mth.clamp(litHeight, 1, 14);
        spriteSlice(s, 14, 14, 0, 14 - h, x, y + 14 - h, 14, h);
    }
    @Override public void drawFurnaceBurn(ContainerLayout.Background bg, int x, int y, int burnWidth) {
        ResourceLocation s = bg == ContainerLayout.Background.BLAST_FURNACE ? BLAST_BURN : bg == ContainerLayout.Background.SMOKER ? SMOKER_BURN : FURNACE_BURN;
        spriteSlice(s, 24, 16, 0, 0, x, y, burnWidth, 16);
    }
    @Override public void drawBrewingFuel(int x, int y, int width) { spriteSlice(BREW_FUEL, 18, 4, 0, 0, x, y, width, 4); }
    @Override public void drawBrewingProgress(int x, int y, int height) { spriteSlice(BREW_PROGRESS, 9, 28, 0, 0, x, y, 9, height); }
    @Override public void drawBrewingBubbles(int x, int y, int height) { spriteSlice(BREW_BUBBLES, 12, 29, 0, 29 - height, x, y + 29 - height, 12, height); }
    @Override public void drawEnchantSlot(int x, int y, boolean offered, boolean affordable, boolean chosen) {
        sprite(!offered ? ENCH_SLOT_DISABLED : chosen ? ENCH_SLOT_HIGHLIGHTED : affordable ? ENCH_SLOT : ENCH_SLOT_DISABLED, x, y, 108, 19);
    }
    @Override public void drawEnchantLevel(int x, int y, int option, boolean highlighted) {
        int idx = Mth.clamp(option, 0, 2);
        sprite(highlighted ? ENCH_LVL[idx] : ENCH_LVL_DIS[idx], x, y, 16, 16);
    }
    @Override public void drawAnvilTextField(int x, int y, boolean active) { sprite(active ? ANVIL_FIELD : ANVIL_FIELD_DIS, x, y, 110, 16); }
    @Override public void drawAnvilError(int x, int y) { sprite(ANVIL_ERROR, x, y, 28, 21); }
    @Override public void drawGrindstoneError(int x, int y) { sprite(GRINDSTONE_ERROR, x, y, 28, 21); }
    @Override public void drawMerchantScroller(int x, int y, boolean scrollable) { sprite(scrollable ? VILLAGER_SCROLLER : VILLAGER_SCROLLER_DIS, x, y, 6, 27); }
    @Override public void drawMerchantButton(int x, int y, boolean selected) { sprite(selected ? BUTTON_HIGHLIGHTED : BUTTON_SPRITE, x, y, 88, 20); }
    @Override public void drawMerchantArrow(int x, int y, boolean disabled) { sprite(disabled ? TRADE_ARROW_OUT : TRADE_ARROW, x, y, 10, 9); }
    @Override public void drawMerchantCross(int x, int y) { sprite(TRADE_OUT, x, y, 28, 21); }
    @Override public void drawMerchantXpBg(int x, int y) { sprite(VILLAGER_XP_BG, x, y, 102, 5); }
    @Override public void drawMerchantXpFill(int x, int y, int width) { spriteSlice(VILLAGER_XP_CUR, 102, 5, 0, 0, x, y, width, 5); }

    @Override
    public void drawPlayerPreview(ContainerLayout layout, BotState state, float tickDelta, float fade) {
        var preview = layout.playerPreview();
        if (preview == null || fade <= 0.025F) return;
        if (layout.background() == ContainerLayout.Background.CRAFTING_TABLE) {
            int alpha = Math.round(255 * fade) << 24;
            g.fillGradient(preview.x(), preview.y(), preview.x() + preview.width(),
                    preview.y() + preview.height(), alpha | 0x302C29, alpha | 0x171615);
        }
        Minecraft client = Minecraft.getInstance();
        if (!(client.getCameraEntity() instanceof AbstractClientPlayer player)
                || player == client.player || state.hello() == null
                || !McCompatImpl.playerName(player).equals(state.hello().bot())) return;
        Matrix3x2f mat = new Matrix3x2f(g.pose());
        Vector2f p1 = mat.transformPosition(new Vector2f(preview.x(), preview.y()));
        Vector2f p2 = mat.transformPosition(new Vector2f(preview.x() + preview.width(), preview.y() + preview.height()));
        float scale = Math.max(0.5F, (p2.x - p1.x) / Math.max(1.0F, preview.width()));
        g.pose().pushMatrix();
        g.pose().identity();
        try {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g,
                    Mth.floor(p1.x), Mth.floor(p1.y), Mth.ceil(p2.x), Mth.ceil(p2.y),
                    Math.round(preview.size() * scale), 0.0625F, (p1.x + p2.x) * 0.5F - 20.0F, (p1.y + p2.y) * 0.5F, player);
        } finally {
            g.pose().popMatrix();
        }
    }

    private static ResourceLocation texture(ContainerLayout.Background bg) {
        return switch (bg) {
            case INVENTORY -> INVENTORY;
            case CRAFTING_TABLE -> CRAFTING_TABLE;
            case FURNACE -> FURNACE;
            case ENCHANTING_TABLE -> ENCHANTING_TABLE;
            case ANVIL -> ANVIL;
            case BLAST_FURNACE -> BLAST_FURNACE;
            case SMOKER -> SMOKER;
            case BREWING_STAND -> BREWING_STAND;
            case HOPPER -> HOPPER;
            case SMITHING -> SMITHING;
            case GRINDSTONE -> GRINDSTONE;
            case STONECUTTER -> STONECUTTER;
            case LOOM -> LOOM;
            case DISPENSER -> DISPENSER;
            case SHULKER_BOX -> SHULKER_BOX;
            case CARTOGRAPHY_TABLE -> CARTOGRAPHY_TABLE;
            case BEACON -> BEACON;
            case MERCHANT -> MERCHANT;
            case GENERIC -> GENERIC_54;
        };
    }
}
