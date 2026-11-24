package dev.curio.observer.mixin;

import dev.curio.observer.render.SpectatorTerrainAnchor;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** MC-148993: setupRender otherwise re-centers chunk render storage on the spectator's stale body (MC 1.18.2–1.21.8). */
@Mixin(LevelRenderer.class)
public abstract class WorldRendererMixin {
    @Redirect(method = "setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getX()D"), require = 1)
    private double curio$terrainX(LocalPlayer observer, Camera camera, Frustum frustum, boolean forcedFrustum, boolean spectator) {
        return SpectatorTerrainAnchor.resolve(observer, camera.getEntity(), observer.isSpectator()).getX();
    }

    @Redirect(method = "setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getY()D"), require = 1)
    private double curio$terrainY(LocalPlayer observer, Camera camera, Frustum frustum, boolean forcedFrustum, boolean spectator) {
        return SpectatorTerrainAnchor.resolve(observer, camera.getEntity(), observer.isSpectator()).getY();
    }

    @Redirect(method = "setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getZ()D"), require = 1)
    private double curio$terrainZ(LocalPlayer observer, Camera camera, Frustum frustum, boolean forcedFrustum, boolean spectator) {
        return SpectatorTerrainAnchor.resolve(observer, camera.getEntity(), observer.isSpectator()).getZ();
    }
}
