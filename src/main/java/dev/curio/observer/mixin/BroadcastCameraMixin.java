package dev.curio.observer.mixin;

import dev.curio.observer.CurioObserverClient;
import dev.curio.observer.render.BroadcastCameraSpace;
import dev.curio.observer.render.BroadcastCameraState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Moves only the render camera; entity positions, rotations and the selected vanilla perspective stay intact. */
@Mixin(Camera.class)
public abstract class BroadcastCameraMixin {
    @Shadow private boolean thirdPerson;
    @Shadow private float cameraY;
    @Shadow private float lastCameraY;
    @Shadow protected abstract void setPos(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void moveBy(double x, double y, double z);
    @Shadow private double clipToSpace(double desiredCameraDistance) { throw new AssertionError(); }
    @Unique private final BroadcastCameraState curio$broadcastCamera = new BroadcastCameraState();

    @Inject(method = "reset", at = @At("TAIL"))
    private void curio$resetBroadcast(CallbackInfo ci) {
        curio$broadcastCamera.reset();
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void curio$frameBroadcast(BlockView area, Entity target, boolean vanillaThirdPerson,
                                      boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!CurioObserverClient.cameraActive() || target != MinecraftClient.getInstance().getCameraEntity()
                || (Object) this != MinecraftClient.getInstance().gameRenderer.getCamera()) {
            curio$broadcastCamera.reset();
            return;
        }

        Vec3d eye = new Vec3d(MathHelper.lerp(tickDelta, target.prevX, target.getX()),
                MathHelper.lerp(tickDelta, target.prevY, target.getY()) + MathHelper.lerp(tickDelta, lastCameraY, cameraY),
                MathHelper.lerp(tickDelta, target.prevZ, target.getZ()));
        float yaw = target.getYaw(tickDelta);
        float pitch = target.getPitch(tickDelta);
        double desired = CurioObserverClient.broadcastDistance();
        setPos(eye.x, eye.y, eye.z);
        setRotation(yaw, MathHelper.clamp(pitch, -10, 25));
        double available = curio$space(desired);
        boolean roomy = Math.abs(pitch) < 55 && !target.isSubmergedIn(FluidTags.WATER)
                && BroadcastCameraSpace.roomy(area, target, eye);
        long now = System.nanoTime();
        BroadcastCameraState.Frame frame = curio$broadcastCamera.update(target, area, eye, yaw, pitch,
                available, roomy, desired, CurioObserverClient.broadcastSmoothing(), now);
        setRotation(frame.yaw(), frame.pitch());

        // Recheck the smoothed angle too: a fast turn can otherwise sweep the camera through a corner.
        double safeDistance = frame.thirdPerson() ? curio$space(frame.distance()) : 0;
        if (frame.thirdPerson() && safeDistance < 1.25) {
            frame = curio$broadcastCamera.update(target, area, eye, yaw, pitch, 0, false,
                    desired, CurioObserverClient.broadcastSmoothing(), now);
            setRotation(frame.yaw(), frame.pitch());
        }
        thirdPerson = frame.thirdPerson();
        if (thirdPerson) moveBy(-Math.min(frame.distance(), safeDistance), 0, 0);
    }

    @Unique private double curio$space(double distance) {
        double clipped = clipToSpace(distance);
        return clipped < distance ? Math.max(0, clipped - .15) : distance;
    }
}
