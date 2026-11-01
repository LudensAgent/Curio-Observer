package dev.curio.observer.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.curio.observer.state.BotState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

/** Renders the observed player's real skin and equipment without opening or changing any inventory. */
final class ContainerPlayerPreview {
    private ContainerPlayerPreview() {}

    static void render(DrawContext context, ContainerLayout layout, BotState state, float tickDelta, float fade) {
        var preview = layout.playerPreview();
        if (preview == null || fade <= 0.025F) return;
        if (layout.background() == ContainerLayout.Background.CRAFTING_TABLE) {
            int alpha = Math.round(255 * fade) << 24;
            context.fillGradient(preview.x(), preview.y(), preview.x() + preview.width(),
                    preview.y() + preview.height(), alpha | 0x302C29, alpha | 0x171615);
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.getCameraEntity() instanceof AbstractClientPlayerEntity player)
                || player == client.player || state.hello() == null
                || !player.getGameProfile().getName().equals(state.hello().bot())
                || !(client.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerEntityRenderer renderer)) return;

        // Use a private matrix stack: even a renderer with unmatched pushes cannot affect the HUD.
        MatrixStack matrices = new MatrixStack();
        Matrix4f position = context.getMatrices().peek().getPositionMatrix();
        matrices.peek().getPositionMatrix().set(position);
        matrices.peek().getNormalMatrix().set(context.getMatrices().peek().getNormalMatrix());
        Vector3f first = position.transformPosition(new Vector3f(preview.x(), preview.y(), 0));
        Vector3f last = position.transformPosition(new Vector3f(preview.x() + preview.width(),
                preview.y() + preview.height(), 0));
        matrices.translate(preview.centerX(), preview.baseY(), 50);
        matrices.multiplyPositionMatrix(new Matrix4f().scaling(preview.size(), preview.size(), -preview.size()));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180));

        var dispatcher = client.getEntityRenderDispatcher();
        Quaternionf rotation = new Quaternionf(dispatcher.getRotation());
        PlayerAngles angles = PlayerAngles.capture(player);
        RenderState renderState = RenderState.capture();
        boolean scissorEnabled = false;
        try {
            context.draw();
            context.enableScissor(MathHelper.floor(first.x), MathHelper.floor(first.y),
                    MathHelper.ceil(last.x), MathHelper.ceil(last.y));
            scissorEnabled = true;
            player.bodyYaw = player.prevBodyYaw = 160;
            player.setYaw(160);
            player.prevYaw = 160;
            player.headYaw = player.prevHeadYaw = 170;
            player.setPitch(0);
            player.prevPitch = 0;
            dispatcher.setRotation(new Quaternionf());
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            // Calling the player renderer directly avoids changing dispatcher shadow/hitbox flags.
            // Keep the caller's GUI lighting; only the entity's block/sky light is made full bright.
            RenderSystem.runAsFancy(() -> renderer.render(player, 180, MathHelper.clamp(tickDelta, 0, 1),
                    matrices, context.getVertexConsumers(), LightmapTextureManager.MAX_LIGHT_COORDINATE));
        } finally {
            try {
                context.getVertexConsumers().draw();
            } finally {
                angles.restore(player);
                dispatcher.setRotation(rotation);
                if (scissorEnabled) context.disableScissor();
                renderState.restore();
            }
        }
    }

    private record PlayerAngles(float yaw, float previousYaw, float pitch, float previousPitch,
                                float bodyYaw, float previousBodyYaw, float headYaw, float previousHeadYaw) {
        static PlayerAngles capture(AbstractClientPlayerEntity player) {
            return new PlayerAngles(player.getYaw(), player.prevYaw, player.getPitch(), player.prevPitch,
                    player.bodyYaw, player.prevBodyYaw, player.headYaw, player.prevHeadYaw);
        }

        void restore(AbstractClientPlayerEntity player) {
            player.setYaw(yaw);
            player.prevYaw = previousYaw;
            player.setPitch(pitch);
            player.prevPitch = previousPitch;
            player.bodyYaw = bodyYaw;
            player.prevBodyYaw = previousBodyYaw;
            player.headYaw = headYaw;
            player.prevHeadYaw = previousHeadYaw;
        }
    }

    /** Entity render layers restore defaults, which need not be the caller's current GUI state. */
    private record RenderState(boolean depth, boolean depthMask, int depthFunction, boolean cull, boolean blend,
                               int sourceRgb, int destinationRgb, int sourceAlpha, int destinationAlpha,
                               int activeTexture, ShaderProgram shader, float[] color, Matrix4f textureMatrix,
                               int texture, int overlayTexture, int lightTexture) {
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
            RenderSystem.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.setTextureMatrix(textureMatrix);
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShaderTexture(1, overlayTexture);
            RenderSystem.setShaderTexture(2, lightTexture);
            RenderSystem.activeTexture(activeTexture);
        }
    }
}
