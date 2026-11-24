package dev.curio.observer.mixin;

import dev.curio.observer.CurioObserverClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.spectator.SpectatorGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides vanilla spectator hotbar and prompt while observing (MC 1.20–1.21.5). */
@Mixin(SpectatorGui.class)
public abstract class SpectatorHudMixin {
    @Inject(method = "renderHotbar(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), cancellable = true)
    private void curio$hideMenu(GuiGraphics g, CallbackInfo ci) {
        if (CurioObserverClient.replacesSpectatorHud()) ci.cancel();
    }

    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void curio$hideSpectatorPrompt(GuiGraphics g, CallbackInfo ci) {
        if (CurioObserverClient.replacesSpectatorHud()) ci.cancel();
    }
}
