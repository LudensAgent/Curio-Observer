package dev.curio.observer.hud;

import dev.curio.observer.model.Protocol;
import dev.curio.observer.state.BotState;
import dev.curio.observer.state.ItemCodec;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Experimental hand pass using the bot skin and feed items, without changing the local player. */
public final class BotHandRenderer {
    private BotHandRenderer() {}

    public static void render(MatrixStack matrices, AbstractClientPlayerEntity player, BotState state, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        var buffers = client.getBufferBuilders().getEntityVertexConsumers();
        int light = client.getEntityRenderDispatcher().getLight(player, tickDelta);
        long now = System.currentTimeMillis();
        float swing = MathHelper.clamp((now - state.actions().swingAt()) / 350.0F, 0, 1);
        if (swing >= 1) swing = 0;
        Arm mainArm = player.getMainArm();
        client.gameRenderer.getLightmapTextureManager().enable();
        try {
            renderHand(matrices, buffers, light, player, state.mainHand(), mainArm, swing, state, now);
            if (state.offhand() != null) renderHand(matrices, buffers, light, player, state.offhand(), mainArm.getOpposite(), 0, state, now);
            buffers.draw();
        } finally { client.gameRenderer.getLightmapTextureManager().disable(); }
    }

    private static void renderHand(MatrixStack matrices, VertexConsumerProvider buffers, int light,
                                   AbstractClientPlayerEntity player, Protocol.ItemData item, Arm arm,
                                   float swing, BotState state, long now) {
        MinecraftClient client = MinecraftClient.getInstance();
        ItemStack stack = ItemCodec.stack(item);
        int side = arm == Arm.RIGHT ? 1 : -1;
        float rootSwing = MathHelper.sin(MathHelper.sqrt(swing) * (float) Math.PI);
        matrices.push();
        try {
            matrices.translate(side * (0.56F - 0.4F * rootSwing),
                    -0.52F + 0.2F * MathHelper.sin(MathHelper.sqrt(swing) * (float) Math.PI * 2),
                    -0.72F - 0.2F * MathHelper.sin(swing * (float) Math.PI));
            if (state.actions().eating() && arm == player.getMainArm()) {
                float phase = (now - state.actions().changedAt()) / 90.0F;
                matrices.translate(side * -0.25F, 0.25F + Math.abs(MathHelper.cos(phase)) * 0.08F, 0.15F);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-35));
            } else if (now - state.actions().useAt() < 450 && arm == player.getMainArm()) {
                matrices.translate(side * -0.1F, 0.1F, 0.15F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -20));
            }
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (45 - 20 * MathHelper.sin(swing * swing * (float) Math.PI))));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * -20 * rootSwing));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-80 * rootSwing));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * -45));
            if (!stack.isEmpty()) {
                client.gameRenderer.firstPersonRenderer.renderItem(player, stack,
                        arm == Arm.RIGHT ? ModelTransformationMode.FIRST_PERSON_RIGHT_HAND : ModelTransformationMode.FIRST_PERSON_LEFT_HAND,
                        arm == Arm.LEFT, matrices, buffers, light);
            } else if (!player.isInvisible() && client.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerEntityRenderer renderer) {
                matrices.translate(side * 0.08F, -0.08F, 0);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-55));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * 35));
                if (arm == Arm.RIGHT) renderer.renderRightArm(matrices, buffers, light, player);
                else renderer.renderLeftArm(matrices, buffers, light, player);
            }
        } finally { matrices.pop(); }
    }
}
