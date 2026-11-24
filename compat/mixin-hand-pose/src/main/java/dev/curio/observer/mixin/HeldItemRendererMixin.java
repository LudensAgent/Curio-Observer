package dev.curio.observer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.curio.observer.CurioObserverClient;
import dev.curio.observer.hud.BotHandRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First-person hand pass for MC 1.18.2–1.20.4 (renderItemInHand(PoseStack, Camera, float)). */
@Mixin(GameRenderer.class)
public abstract class HeldItemRendererMixin {
    @Inject(method = "renderItemInHand", at = @At("TAIL"))
    private void curio$renderBotHands(PoseStack matrices, Camera camera, float tickDelta, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        var state = CurioObserverClient.snapshot();
        if (!CurioObserverClient.shouldRenderHands(state) || !CurioObserverClient.firstPersonView()
                || client.options.hideGui || !(camera.getEntity() instanceof AbstractClientPlayer player)
                || player == client.player || player.isSleeping()) return;
        BotHandRenderer.render(matrices, player, state, tickDelta);
    }
}
