package dev.curio.observer.compat;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.curio.observer.hud.ContainerLayout;
import dev.curio.observer.state.BotState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

/** HudCanvas implementation for MC 1.20–1.20.1 using GuiGraphics and legacy UV atlases. */
public final class GuiGraphicsCanvas1200 implements HudCanvas {
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

    private final GuiGraphics g;

    public GuiGraphicsCanvas1200(GuiGraphics g) { this.g = g; }

    @Override public int width() { return g.guiWidth(); }
    @Override public int height() { return g.guiHeight(); }
    @Override public void push() { g.pose().pushPose(); }
    @Override public void pop() { g.pose().popPose(); }
    @Override public void translate(float x, float y, float z) { g.pose().translate(x, y, z); }
    @Override public void scale(float x, float y) { g.pose().scale(x, y, 1.0F); }
    @Override public void setAlpha(float alpha) { g.setColor(1, 1, 1, alpha); }
    @Override public void enableBlend() { RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); }
    @Override public void disableBlend() { RenderSystem.disableBlend(); }
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

    @Override
    public void drawCrosshair(int x, int y) {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        g.blit(ICONS, x, y, 0, 0, 15, 15);
        RenderSystem.defaultBlendFunc();
    }

    @Override public void drawHotbar(int x, int y) { g.blit(WIDGETS, x, y, 0, 0, 182, 22); }
    @Override public void drawHotbarSelection(int x, int y) { g.blit(WIDGETS, x, y, 0, 22, 24, 22); }
    @Override public void drawOffhandLeft(int x, int y) { g.blit(WIDGETS, x, y, 24, 22, 29, 24); }
    @Override public void drawHeartContainer(int x, int y) { g.blit(ICONS, x, y, 16, 0, 9, 9); }
    @Override public void drawHeartFull(int x, int y) { g.blit(ICONS, x, y, 52, 0, 9, 9); }
    @Override public void drawHeartHalf(int x, int y) { g.blit(ICONS, x, y, 61, 0, 9, 9); }
    @Override public void drawAbsorptionFull(int x, int y) { g.blit(ICONS, x, y, 160, 0, 9, 9); }
    @Override public void drawAbsorptionHalf(int x, int y) { g.blit(ICONS, x, y, 169, 0, 9, 9); }
    @Override public void drawArmorEmpty(int x, int y) { g.blit(ICONS, x, y, 16, 9, 9, 9); }
    @Override public void drawArmorHalf(int x, int y) { g.blit(ICONS, x, y, 25, 9, 9, 9); }
    @Override public void drawArmorFull(int x, int y) { g.blit(ICONS, x, y, 34, 9, 9, 9); }
    @Override public void drawFoodEmpty(int x, int y) { g.blit(ICONS, x, y, 16, 27, 9, 9); }
    @Override public void drawFoodFull(int x, int y) { g.blit(ICONS, x, y, 52, 27, 9, 9); }
    @Override public void drawFoodHalf(int x, int y) { g.blit(ICONS, x, y, 61, 27, 9, 9); }
    @Override public void drawAirFull(int x, int y) { g.blit(ICONS, x, y, 16, 18, 9, 9); }
    @Override public void drawAirBursting(int x, int y) { g.blit(ICONS, x, y, 25, 18, 9, 9); }
    @Override public void drawXpBackground(int x, int y) { g.blit(ICONS, x, y, 0, 64, 182, 5); }
    @Override public void drawXpProgress(int x, int y, int width) { g.blit(ICONS, x, y, 0, 69, width, 5); }

    @Override
    public void drawContainerBackground(ContainerLayout.Background bg, int x, int y, int width, int height) {
        if (bg == ContainerLayout.Background.MERCHANT) g.blit(MERCHANT, x, y, 0, 0.0F, 0.0F, 276, 166, 512, 256);
        else g.blit(texture(bg), x, y, 0, 0, width, height);
    }

    @Override public void drawGenericChestSlice(int x, int y, int v, int width, int height) { g.blit(GENERIC_54, x, y, 0, v, width, height); }
    @Override public void drawFurnaceLit(ContainerLayout.Background bg, int x, int y, int litHeight) {
        int h = litHeight - 1;
        g.blit(texture(bg), x, y + 13 - h, 176, 13 - h, 14, litHeight);
    }
    @Override public void drawFurnaceBurn(ContainerLayout.Background bg, int x, int y, int burnWidth) { g.blit(texture(bg), x, y, 176, 14, burnWidth, 16); }
    @Override public void drawBrewingFuel(int x, int y, int width) { g.blit(BREWING_STAND, x, y, 176, 29, width, 4); }
    @Override public void drawBrewingProgress(int x, int y, int height) { g.blit(BREWING_STAND, x, y, 176, 0, 9, height); }
    @Override public void drawBrewingBubbles(int x, int y, int height) { g.blit(BREWING_STAND, x, y + 29 - height, 185, 29 - height, 12, height); }
    @Override public void drawEnchantSlot(int x, int y, boolean offered, boolean affordable, boolean chosen) {
        int v = !offered ? 185 : chosen ? 204 : affordable ? 166 : 185;
        g.blit(ENCHANTING_TABLE, x, y, 0, v, 108, 19);
    }
    @Override public void drawEnchantLevel(int x, int y, int option, boolean highlighted) {
        g.blit(ENCHANTING_TABLE, x, y, 16 * option, highlighted ? 223 : 239, 16, 16);
    }
    @Override public void drawAnvilTextField(int x, int y, boolean active) { g.blit(ANVIL, x, y, 0, active ? 166 : 182, 110, 16); }
    @Override public void drawAnvilError(int x, int y) { g.blit(ANVIL, x, y, 176, 0, 28, 21); }
    @Override public void drawGrindstoneError(int x, int y) { g.blit(GRINDSTONE, x, y, 176, 0, 28, 21); }
    @Override public void drawMerchantScroller(int x, int y, boolean scrollable) { g.blit(MERCHANT, x, y, 0, scrollable ? 0.0F : 6.0F, 199.0F, 6, 27, 512, 256); }
    @Override public void drawMerchantButton(int x, int y, boolean selected) {
        int ty = selected ? 86 : 66;
        g.blit(WIDGETS, x, y, 0, ty, 44, 20);
        g.blit(WIDGETS, x + 44, y, 156, ty, 44, 20);
    }
    @Override public void drawMerchantArrow(int x, int y, boolean disabled) { g.blit(MERCHANT, x, y, 0, disabled ? 25.0F : 15.0F, 171.0F, 10, 9, 512, 256); }
    @Override public void drawMerchantCross(int x, int y) { g.blit(MERCHANT, x, y, 0, 311.0F, 0.0F, 28, 21, 512, 256); }
    @Override public void drawMerchantXpBg(int x, int y) { g.blit(MERCHANT, x, y, 0, 0.0F, 186.0F, 102, 5, 512, 256); }
    @Override public void drawMerchantXpFill(int x, int y, int width) { g.blit(MERCHANT, x, y, 0, 0.0F, 191.0F, width, 5, 512, 256); }

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
                || !McCompatImpl.playerName(player).equals(state.hello().bot())
                || !(client.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;

        PoseStack matrices = new PoseStack();
        Matrix4f position = g.pose().last().pose();
        matrices.last().pose().set(position);
        matrices.last().normal().set(g.pose().last().normal());
        Vector3f first = position.transformPosition(new Vector3f(preview.x(), preview.y(), 0));
        Vector3f last = position.transformPosition(new Vector3f(preview.x() + preview.width(), preview.y() + preview.height(), 0));
        matrices.translate(preview.centerX(), preview.baseY(), 50);
        matrices.mulPoseMatrix(new Matrix4f().scaling(preview.size(), preview.size(), -preview.size()));
        matrices.mulPose(Axis.ZP.rotationDegrees(180));

        var dispatcher = client.getEntityRenderDispatcher();
        Quaternionf rotation = new Quaternionf(dispatcher.cameraOrientation());
        float yaw = player.getYRot(), prevYaw = player.yRotO, pitch = player.getXRot(), prevPitch = player.xRotO;
        float bodyYaw = player.yBodyRot, prevBodyYaw = player.yBodyRotO, headYaw = player.yHeadRot, prevHeadYaw = player.yHeadRotO;
        RenderState rs = RenderState.capture();
        boolean scissor = false;
        try {
            g.flush();
            g.enableScissor(Mth.floor(first.x), Mth.floor(first.y), Mth.ceil(last.x), Mth.ceil(last.y));
            scissor = true;
            player.yBodyRot = player.yBodyRotO = 160;
            player.setYRot(160); player.yRotO = 160;
            player.yHeadRot = player.yHeadRotO = 170;
            player.setXRot(0); player.xRotO = 0;
            dispatcher.overrideCameraOrientation(new Quaternionf());
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.runAsFancy(() -> renderer.render(player, 180, Mth.clamp(tickDelta, 0, 1),
                    matrices, g.bufferSource(), LightTexture.FULL_BRIGHT));
        } finally {
            try { g.bufferSource().endBatch(); } finally {
                player.setYRot(yaw); player.yRotO = prevYaw; player.setXRot(pitch); player.xRotO = prevPitch;
                player.yBodyRot = bodyYaw; player.yBodyRotO = prevBodyYaw; player.yHeadRot = headYaw; player.yHeadRotO = prevHeadYaw;
                dispatcher.overrideCameraOrientation(rotation);
                if (scissor) g.disableScissor();
                rs.restore();
            }
        }
    }

    private record RenderState(boolean depth, boolean depthMask, int depthFunction, boolean cull, boolean blend,
                               int srcRgb, int dstRgb, int srcA, int dstA, int activeTex, ShaderInstance shader,
                               float[] color, Matrix4f texMat, int tex0, int tex1, int tex2) {
        static RenderState capture() {
            return new RenderState(GL11.glIsEnabled(GL11.GL_DEPTH_TEST), GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetInteger(GL11.GL_DEPTH_FUNC), GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glIsEnabled(GL11.GL_BLEND), GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA), GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),
                    RenderSystem.getShader(), RenderSystem.getShaderColor().clone(),
                    new Matrix4f(RenderSystem.getTextureMatrix()), RenderSystem.getShaderTexture(0),
                    RenderSystem.getShaderTexture(1), RenderSystem.getShaderTexture(2));
        }
        void restore() {
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            RenderSystem.depthMask(depthMask);
            RenderSystem.depthFunc(depthFunction);
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcA, dstA);
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.setTextureMatrix(texMat);
            RenderSystem.setShaderTexture(0, tex0);
            RenderSystem.setShaderTexture(1, tex1);
            RenderSystem.setShaderTexture(2, tex2);
            RenderSystem.activeTexture(activeTex);
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
