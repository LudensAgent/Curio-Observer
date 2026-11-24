package dev.curio.observer.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.curio.observer.CurioObserverClient;
import dev.curio.observer.compat.PoseStackCanvas;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps first-person crosshair while spectating (MC 1.18.2–1.19.4). */
@Mixin(Gui.class)
public abstract class InGameHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void curio$keepBotCrosshair(PoseStack pose, CallbackInfo ci) {
        if (!CurioObserverClient.replacesSpectatorHud()) return;
        ci.cancel();
        if (!CurioObserverClient.firstPersonView()) return;
        PoseStackCanvas canvas = new PoseStackCanvas(pose);
        canvas.drawCrosshair((canvas.width() - 15) / 2, (canvas.height() - 15) / 2);
    }
}
