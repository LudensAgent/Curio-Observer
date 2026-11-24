package dev.curio.observer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.curio.observer.CurioObserverClient;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cancels walk bobbing and hurt tilt while adaptive camera is active (MC 1.18.2–1.21.11). */
@Mixin(GameRenderer.class)
public abstract class BroadcastStabilityMixin {
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void curio$steadyWalk(PoseStack matrices, float tickDelta, CallbackInfo ci) {
        if (CurioObserverClient.cameraActive()) ci.cancel();
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void curio$steadyDamage(PoseStack matrices, float tickDelta, CallbackInfo ci) {
        if (CurioObserverClient.cameraActive()) ci.cancel();
    }
}
