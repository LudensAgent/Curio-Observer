package dev.curio.observer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.curio.observer.CurioObserverClient;
import dev.curio.observer.hud.BotHandRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First-person hand pass for MC 1.21.6–1.21.11 (renderItemInHand(float, boolean, Matrix4f)). */
@Mixin(GameRenderer.class)
public abstract class HeldItemRendererMixin {
    @Inject(method = "renderItemInHand", at = @At("TAIL"))
    private void curio$renderBotHands(float tickDelta, boolean sleeping, Matrix4f projection, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        var state = CurioObserverClient.snapshot();
        if (sleeping || !CurioObserverClient.shouldRenderHands(state) || !CurioObserverClient.firstPersonView()
                || client.options.hideGui || !(client.getCameraEntity() instanceof AbstractClientPlayer player)
                || player == client.player || player.isSleeping()) return;
        BotHandRenderer.render(new PoseStack(), player, state, tickDelta);
    }
}
