package dev.curio.observer.mixin;

import dev.curio.observer.CurioObserverClient;
import dev.curio.observer.compat.GuiGraphicsExtractorCanvas;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps first-person crosshair while spectating (MC 26.2–26.3). */
@Mixin(Hud.class)
public abstract class InGameHudMixin {
    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void curio$keepBotCrosshair(GuiGraphicsExtractor g, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!CurioObserverClient.replacesSpectatorHud()) return;
        ci.cancel();
        if (!CurioObserverClient.firstPersonView()) return;
        new GuiGraphicsExtractorCanvas(g).drawCrosshair((g.guiWidth() - 15) / 2, (g.guiHeight() - 15) / 2);
    }
}
