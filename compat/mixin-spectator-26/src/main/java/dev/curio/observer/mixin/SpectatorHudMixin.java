package dev.curio.observer.mixin;

import dev.curio.observer.CurioObserverClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.spectator.SpectatorGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides vanilla spectator hotbar and action prompt while observing (MC 26.1–26.3). */
@Mixin(SpectatorGui.class)
public abstract class SpectatorHudMixin {
    @Inject(method = "extractHotbar", at = @At("HEAD"), cancellable = true)
    private void curio$hideMenu(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (CurioObserverClient.replacesSpectatorHud()) ci.cancel();
    }

    @Inject(method = "extractAction", at = @At("HEAD"), cancellable = true)
    private void curio$hideSpectatorPrompt(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (CurioObserverClient.replacesSpectatorHud()) ci.cancel();
    }
}
