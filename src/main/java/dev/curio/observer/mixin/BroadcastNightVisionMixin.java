package dev.curio.observer.mixin;

import dev.curio.observer.CurioObserverClient;
import dev.curio.observer.render.BroadcastLighting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shader packs light the world themselves and ignore the vanilla lightmap, so the gamma redirect has no effect
 * under Iris. Iris passes this method's value to packs as the {@code nightVision} uniform (Complementary adds it to
 * its minimum light), so while brightening is on we report a night-vision strength derived from the gamma.
 * Vanilla only calls this for entities that really have Night Vision; those keep the real value.
 */
@Mixin(GameRenderer.class)
public abstract class BroadcastNightVisionMixin {
    @Inject(method = "getNightVisionStrength", at = @At("HEAD"), cancellable = true)
    private static void curio$shaderBrightness(LivingEntity entity, float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (entity == null || entity.hasStatusEffect(StatusEffects.NIGHT_VISION)
                || entity != MinecraftClient.getInstance().getCameraEntity() || !CurioObserverClient.brightenActive()
                || entity.hasStatusEffect(StatusEffects.DARKNESS) || entity.hasStatusEffect(StatusEffects.BLINDNESS)) return;
        float strength = BroadcastLighting.shaderNightVision(CurioObserverClient.gamma());
        if (strength > 0) cir.setReturnValue(strength);
    }
}
