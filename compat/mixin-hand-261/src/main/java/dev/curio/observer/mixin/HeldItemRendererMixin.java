package dev.curio.observer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.curio.observer.CurioObserverClient;
import dev.curio.observer.compat.ClientEnv;
import dev.curio.observer.hud.BotHandRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First-person hand pass for MC 26.1–26.2 (renderItemInHand(CameraRenderState, float, Matrix4fc)). */
@Mixin(GameRenderer.class)
public abstract class HeldItemRendererMixin {
    @Inject(method = "renderItemInHand", at = @At("TAIL"))
    private void curio$renderBotHands(CameraRenderState camera, float tickDelta, Matrix4fc projection, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        var state = CurioObserverClient.snapshot();
        if (!CurioObserverClient.shouldRenderHands(state) || !CurioObserverClient.firstPersonView()
                || ClientEnv.isHudHidden(client) || !(client.getCameraEntity() instanceof AbstractClientPlayer player)
                || player == client.player || player.isSleeping()) return;
        BotHandRenderer.render(new PoseStack(), player, state, tickDelta);
    }
}
