package dev.curio.observer.render;

import dev.curio.observer.compat.McCompatImpl;
import net.minecraft.world.entity.Entity;

/** Keeps terrain's section/cache anchor aligned with the entity whose view is being rendered. */
public final class SpectatorTerrainAnchor {
    private SpectatorTerrainAnchor() {}

    public static Entity resolve(Entity observer, Entity camera, boolean spectator) {
        return spectator && camera != null && camera != observer && McCompatImpl.entityLevel(camera) == McCompatImpl.entityLevel(observer)
                ? camera : observer;
    }
}
