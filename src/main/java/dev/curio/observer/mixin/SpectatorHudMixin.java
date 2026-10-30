package dev.curio.observer.mixin;

import dev.curio.observer.CurioObserverClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.SpectatorHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpectatorHud.class)
public abstract class SpectatorHudMixin {
    @Inject(method = "renderSpectatorMenu(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true)
    private void curio$hideMenu(DrawContext context, CallbackInfo ci) {
        if (CurioObserverClient.replacesSpectatorHud()) ci.cancel();
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void curio$hideSpectatorPrompt(DrawContext context, CallbackInfo ci) {
        if (CurioObserverClient.replacesSpectatorHud()) ci.cancel();
    }
}
