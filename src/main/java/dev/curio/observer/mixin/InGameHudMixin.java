package dev.curio.observer.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.curio.observer.CurioObserverClient;
import dev.curio.observer.hud.HudTextures;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void curio$keepBotCrosshair(DrawContext context, CallbackInfo ci) {
        if (!CurioObserverClient.replacesSpectatorHud()) return;
        ci.cancel();
        if (!CurioObserverClient.firstPersonView()) return;
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE_MINUS_DST_COLOR,
                GlStateManager.DstFactor.ONE_MINUS_SRC_COLOR, GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);
        context.drawTexture(HudTextures.ICONS, (context.getScaledWindowWidth() - 15) / 2,
                (context.getScaledWindowHeight() - 15) / 2, 0, 0, 15, 15);
        RenderSystem.defaultBlendFunc();
    }
}
