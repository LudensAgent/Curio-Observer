package dev.curio.observer.render;

import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;

/** A long tunnel can have room behind the bot but still be too narrow to frame its body. */
public final class BroadcastCameraSpace {
    private BroadcastCameraSpace() {}

    public static boolean roomy(BlockView world, Entity target, Vec3d eye) {
        return clear(world, target, eye, eye.add(.75, 0, 0))
                && clear(world, target, eye, eye.add(-.75, 0, 0))
                && clear(world, target, eye, eye.add(0, 0, .75))
                && clear(world, target, eye, eye.add(0, 0, -.75))
                && clear(world, target, eye, eye.add(0, .7, 0));
    }

    private static boolean clear(BlockView world, Entity target, Vec3d start, Vec3d end) {
        return world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.VISUAL,
                RaycastContext.FluidHandling.NONE, target)).getType() == HitResult.Type.MISS;
    }
}
