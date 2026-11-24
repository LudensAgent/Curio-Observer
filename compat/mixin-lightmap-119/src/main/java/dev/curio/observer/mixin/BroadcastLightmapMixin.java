package dev.curio.observer.mixin;

import dev.curio.observer.CurioObserverClient;
import dev.curio.observer.render.BroadcastLighting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Raises the lightmap gamma while brightening is active (MC 1.19–1.21.11). */
@Mixin(LightTexture.class)
public abstract class BroadcastLightmapMixin {
    @Shadow @Final private Minecraft minecraft;
    @Unique private boolean curio$brighten;

    @Inject(method = "updateLightTexture(F)V", at = @At("HEAD"))
    private void curio$prepareBroadcastLight(float tickDelta, CallbackInfo ci) {
        curio$brighten = CurioObserverClient.brightenActive()
                && !curio$hasDarkness(minecraft.player) && !curio$hasDarkness(minecraft.getCameraEntity());
    }

    @Redirect(method = "updateLightTexture(F)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"), require = 1)
    private Object curio$broadcastGamma(OptionInstance<?> option) {
        Object value = option.get();
        if (!curio$brighten || option != minecraft.options.gamma() || !(value instanceof Double vanilla)) return value;
        return BroadcastLighting.gamma(vanilla, true, CurioObserverClient.gamma());
    }

    @Unique
    private static boolean curio$hasDarkness(Entity entity) {
        return entity instanceof LivingEntity living && (living.hasEffect(MobEffects.DARKNESS)
                || living.hasEffect(MobEffects.BLINDNESS));
    }
}
