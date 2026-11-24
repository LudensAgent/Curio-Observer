package dev.curio.observer.compat;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.curio.observer.hud.ContainerLayout;
import dev.curio.observer.state.BotState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** HudCanvas for MC 1.19.4 (PoseStack, GuiComponent, JOML math, ItemRenderer with PoseStack arg). */
public final class PoseStackCanvas implements HudCanvas {
    private static final ResourceLocation ICONS = new ResourceLocation("minecraft", "textures/gui/icons.png");
    private static final ResourceLocation WIDGETS = new ResourceLocation("minecraft", "textures/gui/widgets.png");
    private static final ResourceLocation INVENTORY = new ResourceLocation("minecraft", "textures/gui/container/inventory.png");
    private static final ResourceLocation CRAFTING_TABLE = new ResourceLocation("minecraft", "textures/gui/container/crafting_table.png");
    private static final ResourceLocation FURNACE = new ResourceLocation("minecraft", "textures/gui/container/furnace.png");
    private static final ResourceLocation ENCHANTING_TABLE = new ResourceLocation("minecraft", "textures/gui/container/enchanting_table.png");
    private static final ResourceLocation ANVIL = new ResourceLocation("minecraft", "textures/gui/container/anvil.png");
    private static final ResourceLocation BLAST_FURNACE = new ResourceLocation("minecraft", "textures/gui/container/blast_furnace.png");
    private static final ResourceLocation SMOKER = new ResourceLocation("minecraft", "textures/gui/container/smoker.png");
    private static final ResourceLocation BREWING_STAND = new ResourceLocation("minecraft", "textures/gui/container/brewing_stand.png");
    private static final ResourceLocation HOPPER = new ResourceLocation("minecraft", "textures/gui/container/hopper.png");
    private static final ResourceLocation SMITHING = new ResourceLocation("minecraft", "textures/gui/container/smithing.png");
    private static final ResourceLocation GRINDSTONE = new ResourceLocation("minecraft", "textures/gui/container/grindstone.png");
    private static final ResourceLocation STONECUTTER = new ResourceLocation("minecraft", "textures/gui/container/stonecutter.png");
    private static final ResourceLocation LOOM = new ResourceLocation("minecraft", "textures/gui/container/loom.png");
    private static final ResourceLocation DISPENSER = new ResourceLocation("minecraft", "textures/gui/container/dispenser.png");
    private static final ResourceLocation SHULKER_BOX = new ResourceLocation("minecraft", "textures/gui/container/shulker_box.png");
    private static final ResourceLocation CARTOGRAPHY_TABLE = new ResourceLocation("minecraft", "textures/gui/container/cartography_table.png");
    private static final ResourceLocation BEACON = new ResourceLocation("minecraft", "textures/gui/container/beacon.png");
    private static final ResourceLocation MERCHANT = new ResourceLocation("minecraft", "textures/gui/container/villager2.png");
    private static final ResourceLocation GENERIC_54 = new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");

    private final PoseStack pose;
    private float alpha = 1.0F;

    public PoseStackCanvas(PoseStack pose) { this.pose = pose; }

    @Override public int width() { return Minecraft.getInstance().getWindow().getGuiScaledWidth(); }
    @Override public int height() { return Minecraft.getInstance().getWindow().getGuiScaledHeight(); }
    @Override public void push() { pose.pushPose(); }
    @Override public void pop() { pose.popPose(); }
    @Override public void translate(float x, float y, float z) { pose.translate(x, y, z); }
    @Override public void scale(float x, float y) { pose.scale(x, y, 1.0F); }
    @Override public void setAlpha(float alpha) { this.alpha = alpha; RenderSystem.setShaderColor(1, 1, 1, alpha); }
    @Override public void enableBlend() { RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); }
    @Override public void disableBlend() { RenderSystem.disableBlend(); }
    @Override public void fill(int x1, int y1, int x2, int y2, int color) { GuiComponent.fill(pose, x1, y1, x2, y2, color); }
    @Override public void fillGradient(int x1, int y1, int x2, int y2, int topColor, int bottomColor) { GuiComponent.fill(pose, x1, y1, x2, y2, topColor); }
    @Override public void enableScissor(int x1, int y1, int x2, int y2) {
        var win = Minecraft.getInstance().getWindow();
        double scale = win.getGuiScale();
        RenderSystem.enableScissor((int) (x1 * scale), (int) (win.getHeight() - y2 * scale),
                Math.max(0, (int) ((x2 - x1) * scale)), Math.max(0, (int) ((y2 - y1) * scale)));
    }
    @Override public void disableScissor() { RenderSystem.disableScissor(); }

    @Override public void drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        if (shadow) font.drawShadow(pose, text, x, y, color); else font.draw(pose, text, x, y, color);
    }
    @Override public void drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        if (shadow) font.drawShadow(pose, text, x, y, color); else font.draw(pose, text, x, y, color);
    }
    @Override public void drawCenteredString(Font font, Component text, int centerX, int y, int color) {
        GuiComponent.drawCenteredString(pose, font, text, centerX, y, color);
    }

    @Override public void drawItem(ItemStack stack, int x, int y) {
        Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(pose, stack, x, y);
        RenderSystem.setShaderColor(1, 1, 1, alpha);
    }
    @Override public void drawFakeItem(ItemStack stack, int x, int y) {
        Minecraft.getInstance().getItemRenderer().renderAndDecorateFakeItem(pose, stack, x, y);
        RenderSystem.setShaderColor(1, 1, 1, alpha);
    }
    @Override public void drawItemDecorations(Font font, ItemStack stack, int x, int y) {
        Minecraft.getInstance().getItemRenderer().renderGuiItemDecorations(pose, font, stack, x, y);
        RenderSystem.setShaderColor(1, 1, 1, alpha);
    }
    @Override public void drawItemDecorations(Font font, ItemStack stack, int x, int y, String countText) {
        Minecraft.getInstance().getItemRenderer().renderGuiItemDecorations(pose, font, stack, x, y, countText);
        RenderSystem.setShaderColor(1, 1, 1, alpha);
    }

    private void blitTex(ResourceLocation tex, int x, int y, float u, float v, int w, int h, int tw, int th) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1, 1, 1, alpha);
        RenderSystem.setShaderTexture(0, tex);
        GuiComponent.blit(pose, x, y, w, h, u, v, w, h, tw, th);
    }
    private void blitTex(ResourceLocation tex, int x, int y, int u, int v, int w, int h) {
        blitTex(tex, x, y, u, v, w, h, 256, 256);
    }

    @Override public void drawCrosshair(int x, int y) {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        blitTex(ICONS, x, y, 0, 0, 15, 15);
        RenderSystem.defaultBlendFunc();
    }

    @Override public void drawHotbar(int x, int y) { blitTex(WIDGETS, x, y, 0, 0, 182, 22); }
    @Override public void drawHotbarSelection(int x, int y) { blitTex(WIDGETS, x, y, 0, 22, 24, 22); }
    @Override public void drawOffhandLeft(int x, int y) { blitTex(WIDGETS, x, y, 24, 22, 29, 24); }
    @Override public void drawHeartContainer(int x, int y) { blitTex(ICONS, x, y, 16, 0, 9, 9); }
    @Override public void drawHeartFull(int x, int y) { blitTex(ICONS, x, y, 52, 0, 9, 9); }
    @Override public void drawHeartHalf(int x, int y) { blitTex(ICONS, x, y, 61, 0, 9, 9); }
    @Override public void drawAbsorptionFull(int x, int y) { blitTex(ICONS, x, y, 160, 0, 9, 9); }
    @Override public void drawAbsorptionHalf(int x, int y) { blitTex(ICONS, x, y, 169, 0, 9, 9); }
    @Override public void drawArmorEmpty(int x, int y) { blitTex(ICONS, x, y, 16, 9, 9, 9); }
    @Override public void drawArmorHalf(int x, int y) { blitTex(ICONS, x, y, 25, 9, 9, 9); }
    @Override public void drawArmorFull(int x, int y) { blitTex(ICONS, x, y, 34, 9, 9, 9); }
    @Override public void drawFoodEmpty(int x, int y) { blitTex(ICONS, x, y, 16, 27, 9, 9); }
    @Override public void drawFoodFull(int x, int y) { blitTex(ICONS, x, y, 52, 27, 9, 9); }
    @Override public void drawFoodHalf(int x, int y) { blitTex(ICONS, x, y, 61, 27, 9, 9); }
    @Override public void drawAirFull(int x, int y) { blitTex(ICONS, x, y, 16, 18, 9, 9); }
    @Override public void drawAirBursting(int x, int y) { blitTex(ICONS, x, y, 25, 18, 9, 9); }
    @Override public void drawXpBackground(int x, int y) { blitTex(ICONS, x, y, 0, 64, 182, 5); }
    @Override public void drawXpProgress(int x, int y, int width) { blitTex(ICONS, x, y, 0, 69, width, 5); }

    @Override public void drawContainerBackground(ContainerLayout.Background bg, int x, int y, int width, int height) {
        if (bg == ContainerLayout.Background.MERCHANT) blitTex(MERCHANT, x, y, 0, 0, 276, 166, 512, 256);
        else blitTex(texture(bg), x, y, 0, 0, width, height);
    }
    @Override public void drawGenericChestSlice(int x, int y, int v, int width, int height) { blitTex(GENERIC_54, x, y, 0, v, width, height); }
    @Override public void drawFurnaceLit(ContainerLayout.Background bg, int x, int y, int litHeight) {
        int h = litHeight - 1;
        blitTex(texture(bg), x, y + 13 - h, 176, 13 - h, 14, litHeight);
    }
    @Override public void drawFurnaceBurn(ContainerLayout.Background bg, int x, int y, int burnWidth) { blitTex(texture(bg), x, y, 176, 14, burnWidth, 16); }
    @Override public void drawBrewingFuel(int x, int y, int width) { blitTex(BREWING_STAND, x, y, 176, 29, width, 4); }
    @Override public void drawBrewingProgress(int x, int y, int height) { blitTex(BREWING_STAND, x, y, 176, 0, 9, height); }
    @Override public void drawBrewingBubbles(int x, int y, int height) { blitTex(BREWING_STAND, x, y + 29 - height, 185, 29 - height, 12, height); }
    @Override public void drawEnchantSlot(int x, int y, boolean offered, boolean affordable, boolean chosen) {
        int v = !offered ? 185 : chosen ? 204 : affordable ? 166 : 185;
        blitTex(ENCHANTING_TABLE, x, y, 0, v, 108, 19);
    }
    @Override public void drawEnchantLevel(int x, int y, int option, boolean highlighted) {
        blitTex(ENCHANTING_TABLE, x, y, 16 * option, highlighted ? 223 : 239, 16, 16);
    }
    @Override public void drawAnvilTextField(int x, int y, boolean active) { blitTex(ANVIL, x, y, 0, active ? 166 : 182, 110, 16); }
    @Override public void drawAnvilError(int x, int y) { blitTex(ANVIL, x, y, 176, 0, 28, 21); }
    @Override public void drawGrindstoneError(int x, int y) { blitTex(GRINDSTONE, x, y, 176, 0, 28, 21); }
    @Override public void drawMerchantScroller(int x, int y, boolean scrollable) { blitTex(MERCHANT, x, y, scrollable ? 0 : 6, 199, 6, 27, 512, 256); }
    @Override public void drawMerchantButton(int x, int y, boolean selected) {
        int ty = selected ? 86 : 66;
        blitTex(WIDGETS, x, y, 0, ty, 44, 20);
        blitTex(WIDGETS, x + 44, y, 156, ty, 44, 20);
    }
    @Override public void drawMerchantArrow(int x, int y, boolean disabled) { blitTex(MERCHANT, x, y, disabled ? 25 : 15, 171, 10, 9, 512, 256); }
    @Override public void drawMerchantCross(int x, int y) { blitTex(MERCHANT, x, y, 311, 0, 28, 21, 512, 256); }
    @Override public void drawMerchantXpBg(int x, int y) { blitTex(MERCHANT, x, y, 0, 186, 102, 5, 512, 256); }
    @Override public void drawMerchantXpFill(int x, int y, int width) { blitTex(MERCHANT, x, y, 0, 191, width, 5, 512, 256); }

    @Override
    public void drawPlayerPreview(ContainerLayout layout, BotState state, float tickDelta, float fade) {
        var preview = layout.playerPreview();
        if (preview == null || fade <= 0.025F) return;
        if (layout.background() == ContainerLayout.Background.CRAFTING_TABLE) {
            int a = Math.round(255 * fade) << 24;
            fill(preview.x(), preview.y(), preview.x() + preview.width(), preview.y() + preview.height(), a | 0x24211F);
        }
        Minecraft client = Minecraft.getInstance();
        if (!(client.getCameraEntity() instanceof AbstractClientPlayer player)
                || player == client.player || state.hello() == null
                || !McCompatImpl.playerName(player).equals(state.hello().bot())
                || !(client.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;

        PoseStack matrices = new PoseStack();
        matrices.last().pose().set(pose.last().pose());
        matrices.last().normal().set(pose.last().normal());
        matrices.translate(preview.centerX(), preview.baseY(), 50);
        matrices.mulPoseMatrix(new Matrix4f().scaling(preview.size(), preview.size(), -preview.size()));
        matrices.mulPose(Axis.ZP.rotationDegrees(180));

        var dispatcher = client.getEntityRenderDispatcher();
        Quaternionf oldRot = new Quaternionf(dispatcher.cameraOrientation());
        float yaw = player.getYRot(), prevYaw = player.yRotO, pitch = player.getXRot(), prevPitch = player.xRotO;
        float bodyYaw = player.yBodyRot, prevBodyYaw = player.yBodyRotO, headYaw = player.yHeadRot, prevHeadYaw = player.yHeadRotO;
        var buffers = client.renderBuffers().bufferSource();
        try {
            player.yBodyRot = player.yBodyRotO = 160;
            player.setYRot(160); player.yRotO = 160;
            player.yHeadRot = player.yHeadRotO = 170;
            player.setXRot(0); player.xRotO = 0;
            dispatcher.overrideCameraOrientation(new Quaternionf());
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.runAsFancy(() -> renderer.render(player, 180, Mth.clamp(tickDelta, 0, 1),
                    matrices, buffers, LightTexture.FULL_BRIGHT));
            buffers.endBatch();
        } finally {
            player.setYRot(yaw); player.yRotO = prevYaw; player.setXRot(pitch); player.xRotO = prevPitch;
            player.yBodyRot = bodyYaw; player.yBodyRotO = prevBodyYaw; player.yHeadRot = headYaw; player.yHeadRotO = prevHeadYaw;
            dispatcher.overrideCameraOrientation(oldRot);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1, 1, 1, alpha);
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
