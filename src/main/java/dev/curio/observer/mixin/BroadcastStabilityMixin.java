package dev.curio.observer.mixin;

import dev.curio.observer.CurioObserverClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class BroadcastStabilityMixin {
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void curio$steadyWalk(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (CurioObserverClient.cameraActive()) ci.cancel();
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void curio$steadyDamage(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (CurioObserverClient.cameraActive()) ci.cancel();
    }
}
