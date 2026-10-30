package dev.curio.observer.render;

import net.minecraft.entity.Entity;

/** Keeps terrain's section/cache anchor aligned with the entity whose view is being rendered. */
public final class SpectatorTerrainAnchor {
    private SpectatorTerrainAnchor() {}

    public static Entity resolve(Entity observer, Entity camera, boolean spectator) {
        return spectator && camera != null && camera != observer && camera.getWorld() == observer.getWorld()
                ? camera : observer;
    }
}
