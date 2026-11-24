package dev.curio.observer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.curio.observer.CurioObserverClient;
import dev.curio.observer.compat.ClientEnv;
import dev.curio.observer.hud.BotHandRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First-person hand pass for MC 26.3 (submits into GameRenderer.handAndScreenSubmitNodeStorage on RETURN). */
@Mixin(GameRenderer.class)
public abstract class HeldItemRendererMixin {
    @Shadow @Final private SubmitNodeStorage handAndScreenSubmitNodeStorage;

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void curio$renderBotHands(CameraRenderState camera, PlayerRenderState playerState, GpuTextureView target, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        var state = CurioObserverClient.snapshot();
        if (!CurioObserverClient.shouldRenderHands(state) || !CurioObserverClient.firstPersonView()
                || ClientEnv.isHudHidden(client) || !(client.getCameraEntity() instanceof AbstractClientPlayer player)
                || player == client.player || player.isSleeping()) return;
        float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        BotHandRenderer.render(new PoseStack(), this.handAndScreenSubmitNodeStorage, player, state, tickDelta);
    }
}
