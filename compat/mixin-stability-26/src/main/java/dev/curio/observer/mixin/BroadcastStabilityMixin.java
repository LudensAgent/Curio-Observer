package dev.curio.observer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.curio.observer.CurioObserverClient;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cancels walk bobbing and hurt tilt while adaptive camera is active (MC 26.1–26.3). */
@Mixin(GameRenderer.class)
public abstract class BroadcastStabilityMixin {
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void curio$steadyWalk(CameraRenderState camera, PoseStack matrices, CallbackInfo ci) {
        if (CurioObserverClient.cameraActive()) ci.cancel();
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void curio$steadyDamage(CameraRenderState camera, PoseStack matrices, CallbackInfo ci) {
        if (CurioObserverClient.cameraActive()) ci.cancel();
    }
}
