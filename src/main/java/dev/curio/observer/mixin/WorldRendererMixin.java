package dev.curio.observer.mixin;

import dev.curio.observer.render.SpectatorTerrainAnchor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** MC-148993: setupTerrain otherwise re-centers chunk render storage on the spectator's stale body. */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Redirect(method = "setupTerrain(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;ZZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getX()D"), require = 1)
    private double curio$terrainX(ClientPlayerEntity observer, Camera camera, Frustum frustum, boolean forcedFrustum, boolean spectator) {
        return SpectatorTerrainAnchor.resolve(observer, camera.getFocusedEntity(), observer.isSpectator()).getX();
    }

    @Redirect(method = "setupTerrain(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;ZZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getY()D"), require = 1)
    private double curio$terrainY(ClientPlayerEntity observer, Camera camera, Frustum frustum, boolean forcedFrustum, boolean spectator) {
        return SpectatorTerrainAnchor.resolve(observer, camera.getFocusedEntity(), observer.isSpectator()).getY();
    }

    @Redirect(method = "setupTerrain(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;ZZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getZ()D"), require = 1)
    private double curio$terrainZ(ClientPlayerEntity observer, Camera camera, Frustum frustum, boolean forcedFrustum, boolean spectator) {
        return SpectatorTerrainAnchor.resolve(observer, camera.getFocusedEntity(), observer.isSpectator()).getZ();
    }
}
