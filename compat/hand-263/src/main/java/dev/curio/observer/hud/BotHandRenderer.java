package dev.curio.observer.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.curio.observer.compat.ItemCodec;
import dev.curio.observer.model.Protocol;
import dev.curio.observer.state.BotState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** First-person hand pass for MC 26.3 (ItemModelResolver + PoseStack.rotateDegrees + GameRenderer.featureRenderDispatcher). */
public final class BotHandRenderer {
    private static final ItemStackRenderState SCRATCH = new ItemStackRenderState();

    private BotHandRenderer() {}

    public static void render(PoseStack matrices, SubmitNodeCollector collector, AbstractClientPlayer player, BotState state, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        int light = client.getEntityRenderDispatcher().getPackedLightCoords(player, tickDelta);
        long now = System.currentTimeMillis();
        float swing = Mth.clamp((now - state.actions().swingAt()) / 350.0F, 0, 1);
        if (swing >= 1) swing = 0;
        HumanoidArm mainArm = player.getMainArm();
        renderHand(matrices, collector, light, player, state.mainHand(), mainArm, swing, state, now);
        if (state.offhand() != null) renderHand(matrices, collector, light, player, state.offhand(), mainArm.getOpposite(), 0, state, now);
    }

    private static void renderHand(PoseStack matrices, SubmitNodeCollector collector, int light,
                                   AbstractClientPlayer player, Protocol.ItemData item, HumanoidArm arm,
                                   float swing, BotState state, long now) {
        Minecraft client = Minecraft.getInstance();
        ItemStack stack = ItemCodec.stack(item);
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float rootSwing = Mth.sin(Mth.sqrt(swing) * (float) Math.PI);
        matrices.pushPose();
        try {
            matrices.translate(side * (0.56F - 0.4F * rootSwing),
                    -0.52F + 0.2F * Mth.sin(Mth.sqrt(swing) * (float) Math.PI * 2),
                    -0.72F - 0.2F * Mth.sin(swing * (float) Math.PI));
            if (state.actions().eating() && arm == player.getMainArm()) {
                float phase = (now - state.actions().changedAt()) / 90.0F;
                matrices.translate(side * -0.25F, 0.25F + Math.abs(Mth.cos(phase)) * 0.08F, 0.15F);
                matrices.rotateDegrees(Axis.XP, -35);
            } else if (now - state.actions().useAt() < 450 && arm == player.getMainArm()) {
                matrices.translate(side * -0.1F, 0.1F, 0.15F);
                matrices.rotateDegrees(Axis.YP, side * -20);
            }
            matrices.rotateDegrees(Axis.YP, side * (45 - 20 * Mth.sin(swing * swing * (float) Math.PI)));
            matrices.rotateDegrees(Axis.ZP, side * -20 * rootSwing);
            matrices.rotateDegrees(Axis.XP, -80 * rootSwing);
            matrices.rotateDegrees(Axis.YP, side * -45);
            if (!stack.isEmpty()) {
                SCRATCH.clear();
                client.getItemModelResolver().updateForLiving(SCRATCH, stack,
                        arm == HumanoidArm.RIGHT ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                        player);
                SCRATCH.submit(matrices, collector, light, OverlayTexture.NO_OVERLAY, 0);
            } else if (!player.isInvisible() && client.getEntityRenderDispatcher().getRenderer(player) instanceof AvatarRenderer<?> renderer) {
                matrices.translate(side * 0.08F, -0.08F, 0);
                matrices.rotateDegrees(Axis.XP, -55);
                matrices.rotateDegrees(Axis.ZP, side * 35);
                var skinTex = player.getSkin().body().texturePath();
                if (arm == HumanoidArm.RIGHT) renderer.renderRightHand(matrices, collector, light, skinTex, player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
                else renderer.renderLeftHand(matrices, collector, light, skinTex, player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
            }
        } finally { matrices.popPose(); }
    }
}
