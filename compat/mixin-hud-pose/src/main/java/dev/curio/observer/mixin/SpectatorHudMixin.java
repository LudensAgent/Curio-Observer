package dev.curio.observer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.curio.observer.CurioObserverClient;
import net.minecraft.client.gui.components.spectator.SpectatorGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides vanilla spectator hotbar and prompt while observing (MC 1.18.2–1.19.4). */
@Mixin(SpectatorGui.class)
public abstract class SpectatorHudMixin {
    @Inject(method = "renderHotbar(Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("HEAD"), cancellable = true)
    private void curio$hideMenu(PoseStack pose, CallbackInfo ci) {
        if (CurioObserverClient.replacesSpectatorHud()) ci.cancel();
    }

    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void curio$hideSpectatorPrompt(PoseStack pose, CallbackInfo ci) {
        if (CurioObserverClient.replacesSpectatorHud()) ci.cancel();
    }
}
