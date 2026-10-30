package dev.curio.observer.mixin;

import dev.curio.observer.CurioObserverClient;
import dev.curio.observer.render.BroadcastLighting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raises the gamma the lightmap is built with, beyond the vanilla slider's 1.0 limit. The player's saved
 * option, HUD pixels, entities and server state are untouched; darkness and blindness keep their effect.
 */
@Mixin(LightmapTextureManager.class)
public abstract class BroadcastLightmapMixin {
    @Shadow @Final private MinecraftClient client;
    @Unique private boolean curio$brighten;

    @Inject(method = "update(F)V", at = @At("HEAD"))
    private void curio$prepareBroadcastLight(float tickDelta, CallbackInfo ci) {
        curio$brighten = CurioObserverClient.brightenActive()
                && !curio$hasDarkness(client.player) && !curio$hasDarkness(client.getCameraEntity());
    }

    @Redirect(method = "update(F)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;"), require = 1)
    private Object curio$broadcastGamma(SimpleOption<?> option) {
        Object value = option.getValue();
        if (!curio$brighten || option != client.options.getGamma() || !(value instanceof Double vanilla)) return value;
        return BroadcastLighting.gamma(vanilla, true, CurioObserverClient.gamma());
    }

    @Unique
    private static boolean curio$hasDarkness(Entity entity) {
        return entity instanceof LivingEntity living && (living.hasStatusEffect(StatusEffects.DARKNESS)
                || living.hasStatusEffect(StatusEffects.BLINDNESS));
    }
}
