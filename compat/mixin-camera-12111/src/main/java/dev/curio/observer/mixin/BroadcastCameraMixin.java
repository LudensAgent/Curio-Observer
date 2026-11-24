package dev.curio.observer.mixin;

import dev.curio.observer.CurioObserverClient;
import dev.curio.observer.compat.McCompatImpl;
import dev.curio.observer.render.BroadcastCameraSpace;
import dev.curio.observer.render.BroadcastCameraState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adaptive broadcast camera for MC 1.21.11 (Level setup + float move/getMaxZoom). */
@Mixin(Camera.class)
public abstract class BroadcastCameraMixin {
    @Shadow private boolean detached;
    @Shadow private float eyeHeight;
    @Shadow private float eyeHeightOld;
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void move(float x, float y, float z);
    @Shadow private float getMaxZoom(float desiredCameraDistance) { throw new AssertionError(); }
    @Unique private final BroadcastCameraState curio$broadcastCamera = new BroadcastCameraState();

    @Inject(method = "reset", at = @At("TAIL"))
    private void curio$resetBroadcast(CallbackInfo ci) {
        curio$broadcastCamera.reset();
    }

    @Inject(method = "setup", at = @At("TAIL"))
    private void curio$frameBroadcast(Level area, Entity target, boolean vanillaThirdPerson,
                                      boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!CurioObserverClient.cameraActive() || target != Minecraft.getInstance().getCameraEntity()
                || (Object) this != Minecraft.getInstance().gameRenderer.getMainCamera()) {
            curio$broadcastCamera.reset();
            return;
        }

        Vec3 eye = new Vec3(Mth.lerp(tickDelta, target.xo, target.getX()),
                Mth.lerp(tickDelta, target.yo, target.getY()) + Mth.lerp(tickDelta, eyeHeightOld, eyeHeight),
                Mth.lerp(tickDelta, target.zo, target.getZ()));
        float yaw = target.getViewYRot(tickDelta);
        float pitch = target.getViewXRot(tickDelta);
        double desired = CurioObserverClient.broadcastDistance();
        setPosition(eye.x, eye.y, eye.z);
        setRotation(yaw, Mth.clamp(pitch, -10, 25));
        double available = curio$space(desired);
        boolean roomy = Math.abs(pitch) < 55 && !McCompatImpl.isSubmergedInWater(target)
                && BroadcastCameraSpace.roomy(area, target, eye);
        long now = System.nanoTime();
        BroadcastCameraState.Frame frame = curio$broadcastCamera.update(target, area, eye.x, eye.y, eye.z, yaw, pitch,
                available, roomy, desired, CurioObserverClient.broadcastSmoothing(), now);
        setRotation(frame.yaw(), frame.pitch());

        double safeDistance = frame.thirdPerson() ? curio$space(frame.distance()) : 0;
        if (frame.thirdPerson() && safeDistance < 1.25) {
            frame = curio$broadcastCamera.update(target, area, eye.x, eye.y, eye.z, yaw, pitch, 0, false,
                    desired, CurioObserverClient.broadcastSmoothing(), now);
            setRotation(frame.yaw(), frame.pitch());
        }
        detached = frame.thirdPerson();
        if (detached) move((float) -Math.min(frame.distance(), safeDistance), 0, 0);
    }

    @Unique private double curio$space(double distance) {
        double clipped = getMaxZoom((float) distance);
        return clipped < distance ? Math.max(0, clipped - .15) : distance;
    }
}
