package dev.curio.observer.mixin;

import dev.curio.observer.CurioObserverClient;
import dev.curio.observer.compat.GuiGraphicsCanvas1202;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps first-person crosshair while spectating (MC 1.20.2–1.20.4). */
@Mixin(Gui.class)
public abstract class InGameHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void curio$keepBotCrosshair(GuiGraphics g, CallbackInfo ci) {
        if (!CurioObserverClient.replacesSpectatorHud()) return;
        ci.cancel();
        if (!CurioObserverClient.firstPersonView()) return;
        new GuiGraphicsCanvas1202(g).drawCrosshair((g.guiWidth() - 15) / 2, (g.guiHeight() - 15) / 2);
    }
}
