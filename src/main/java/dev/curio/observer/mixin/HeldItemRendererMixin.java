package dev.curio.observer.mixin;

import dev.curio.observer.CurioObserverClient;
import dev.curio.observer.hud.BotHandRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The vanilla spectator path skips hands inside this method; its tail still has the hand projection. */
@Mixin(GameRenderer.class)
public abstract class HeldItemRendererMixin {
    @Inject(method = "renderHand", at = @At("TAIL"))
    private void curio$renderBotHands(MatrixStack matrices, Camera camera, float tickDelta, CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        var state = CurioObserverClient.snapshot();
        if (!CurioObserverClient.shouldRenderHands(state) || !CurioObserverClient.firstPersonView()
                || client.options.hudHidden || !(camera.getFocusedEntity() instanceof AbstractClientPlayerEntity player)
                || player == client.player || player.isSleeping()) return;
        BotHandRenderer.render(matrices, player, state, tickDelta);
    }
}
