package dev.curio.observer.mixin;

import dev.curio.observer.CurioObserverClient;
import dev.curio.observer.render.BroadcastLighting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reports shader nightVision uniform strength while brightening is on (MC 1.18.2–26.1.2). */
@Mixin(GameRenderer.class)
public abstract class BroadcastNightVisionMixin {
    @Inject(method = "getNightVisionScale", at = @At("HEAD"), cancellable = true)
    private static void curio$shaderBrightness(LivingEntity entity, float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (entity == null || entity.hasEffect(MobEffects.NIGHT_VISION)
                || entity != Minecraft.getInstance().getCameraEntity() || !CurioObserverClient.brightenActive()
                || entity.hasEffect(MobEffects.DARKNESS) || entity.hasEffect(MobEffects.BLINDNESS)) return;
        float strength = BroadcastLighting.shaderNightVision(CurioObserverClient.gamma());
        if (strength > 0) cir.setReturnValue(strength);
    }
}
