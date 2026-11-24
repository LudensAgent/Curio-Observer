package dev.curio.observer.render;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A long tunnel can have room behind the bot but still be too narrow to frame its body. */
public final class BroadcastCameraSpace {
    private BroadcastCameraSpace() {}

    public static boolean roomy(BlockGetter world, Entity target, Vec3 eye) {
        return clear(world, target, eye, eye.add(.75, 0, 0))
                && clear(world, target, eye, eye.add(-.75, 0, 0))
                && clear(world, target, eye, eye.add(0, 0, .75))
                && clear(world, target, eye, eye.add(0, 0, -.75))
                && clear(world, target, eye, eye.add(0, .7, 0));
    }

    private static boolean clear(BlockGetter world, Entity target, Vec3 start, Vec3 end) {
        return world.clip(new ClipContext(start, end, ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE, target)).getType() == HitResult.Type.MISS;
    }
}
